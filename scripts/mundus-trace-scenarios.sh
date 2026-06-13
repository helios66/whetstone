#!/usr/bin/env bash
#
# mundus-trace-scenarios.sh — codified Mundus tracing acceptance test.
#
# Builds the sample in two variants, drives the same UI flow on an emulator,
# extracts the Perfetto trace each produces, and validates the slice/metadata
# invariants we care about. Exits non-zero on the first failed assertion.
#
# Scenarios (what each asserts):
#   DEBUG   (default build, composeTracing ON):
#     - heavy Compose CompositionTracer is active        (androidx.compose events > threshold)
#     - Whetstone DI ran                                  (getMessage slices present)
#     - suspend-fn tracing across a dispatcher hop        (scoreFor slices present)
#     - manual span via beginTokenWith                    (statsBatch slices present)
#     - @TraceArg captured arg values                     (debug.title / debug.todo metadata)
#     - beginTokenWith typed put() columns                (debug.todoCount int, debug.filter string)
#   RELEASE (-Pmundus.composeTracing=false, R8 full mode):
#     - heavy tracer is GONE                              (androidx.compose events == 0)
#     - everything else still survives R8 full mode       (DI + suspend + manual span + metadata)
#     - no DI crash                                       ("App: ... message!" in logcat)
#
#   Both scenarios also run (so each is verified in debug AND under R8):
#     - per-preset coverage      lifecycle (Activity.onCreate), startupPhases (Application.onCreate),
#                                viewModel (TodoViewModel.*), workers (MainWorker.doWork)
#     - thread attribution       scoreFor runs on a DefaultDispatcher thread, NOT main
#     - span balance             manual statsBatch spans all closed (dur>=0); <2% unclosed overall
#     - multi-module reach       slices from both :sample (app) and :sample-library (library)
#
# Regression guards (also run in `both`):
#   - metadata VALUE correctness (debug): debug.filter == 'all', debug.todoCount a sane int —
#     catches typed put() writing garbage or to the wrong Perfetto column.
#   - composeTracing causality (release-default): a release built WITHOUT the flag must STILL be
#     heavy. Paired with the lean flagged release, this proves the flag (not R8 over-stripping)
#     causes the lean result — guarding against a false-lean and the 0.6.0 auto-install regression.
#
# Note: `mundus { enabled = false }` is NOT used as a negative control — verified (0.9.0) to be a
# no-op (the compiler instruments regardless), so it can't gate a control build. Reported upstream.
#
# Usage:   scripts/mundus-trace-scenarios.sh [debug|release|both]   (default: both)
# Env:     DEVICE (default emulator-5556), JAVA_HOME, ANDROID_HOME
#
set -euo pipefail

# --- config ---------------------------------------------------------------
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
DEVICE="${DEVICE:-emulator-5556}"
PKG="com.deliveryhero.whetstone.sample"
TDIR="/storage/emulated/0/Android/data/$PKG/files/mundus-traces"
TP="$ROOT/trace_processor"
OUT="$HOME/AndroidStudioProjects/mundus-traces"
COMPOSE_HEAVY_MIN="${COMPOSE_HEAVY_MIN:-500}"   # debug must exceed this many compose events
WHICH="${1:-both}"

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$PATH:$ANDROID_HOME/platform-tools"

mkdir -p "$OUT"
FAILS=0
adb() { command adb -s "$DEVICE" "$@"; }

# --- assertion helpers ----------------------------------------------------
pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; FAILS=$((FAILS+1)); }
assert_ge() { # name value min
  if [ "${2:-0}" -ge "$3" ] 2>/dev/null; then pass "$1 ($2 >= $3)"; else fail "$1 (got '${2:-?}', need >= $3)"; fi
}
assert_eq() { # name value expected
  if [ "${2:-x}" = "$3" ]; then pass "$1 (== $3)"; else fail "$1 (got '${2:-?}', expected $3)"; fi
}

# scalar query against a trace; returns last non-header line, unquoted
q() {
  local tf; tf="$(mktemp)"; printf '%s\n' "$2" > "$tf"
  local r; r="$("$TP" "$1" -q "$tf" 2>/dev/null | tail -1 | tr -d '"' || true)"
  rm -f "$tf"; printf '%s' "$r"
}

# --- build + drive one variant; echoes the pulled trace path --------------
run_variant() { # $1=label  $2=apk-glob  $3...=extra gradle args
  local label="$1" apkglob="$2"; shift 2
  local gradle_task; [ "$label" = "debug" ] && gradle_task=":sample:assembleDebug" || gradle_task=":sample:assembleRelease"
  echo ">>> [$label] building ($gradle_task $*)" >&2
  ./gradlew "$gradle_task" "$@" --console=plain -q >&2

  local apk; apk="$(ls -t $apkglob | head -1)"
  echo ">>> [$label] installing $apk" >&2
  adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adb shell pm uninstall "$PKG" >/dev/null 2>&1 || true
  adb install -r "$apk" >&2
  adb shell rm -rf "$TDIR" >/dev/null 2>&1 || true
  adb logcat -c >/dev/null 2>&1 || true

  echo ">>> [$label] driving UI flow" >&2
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 4
  adb shell input tap 540 1600 >/dev/null 2>&1 || true
  adb shell input text "Scenario" >/dev/null 2>&1 || true
  local i; for i in 1 2 3 4 5; do
    adb shell input tap 540 1000 >/dev/null 2>&1 || true
    adb shell input swipe 540 1400 540 700 250 >/dev/null 2>&1 || true
  done
  adb shell monkey -p "$PKG" --throttle 90 --pct-touch 80 --pct-syskeys 0 -v 140 >/dev/null 2>&1 || true
  sleep 2

  # capture the DI log line first
  adb logcat -d -s App:D > "$OUT/scenario-$label.logcat.txt" 2>/dev/null || true
  # Clean trace capture: Mundus flushes every 500ms to a streaming protobuf. A bare
  # force-stop SIGKILLs mid-flush and truncates the final packet (corrupt trace). So:
  # background (triggers a flush) -> settle past the flush interval -> force-stop ->
  # let the OS finish writing the file to disk -> only then pull.
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
  sleep 2
  adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 3
  local f; f="$(adb shell ls -t "$TDIR" 2>/dev/null | grep -i '\.perfetto' | head -1 | tr -d '\r')"
  local dest="$OUT/scenario-$label.perfetto-trace"
  adb pull "$TDIR/$f" "$dest" >&2
  echo "$dest"
}

# --- validations ----------------------------------------------------------
validate_common() { # $1=trace  $2=label
  local t="$1" l="$2"
  assert_ge "[$l] getMessage slices (Whetstone DI)"      "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*getMessage*'")"   1
  assert_ge "[$l] scoreFor slices (suspend tracing)"     "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*scoreFor*'")"     1
  assert_ge "[$l] statsBatch spans (beginTokenWith)"     "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*statsBatch*'")"   1
  assert_ge "[$l] debug.title rows (@TraceArg)"          "$(q "$t" "SELECT COUNT(*) FROM args WHERE flat_key='debug.title'")"      1
  assert_ge "[$l] debug.todoCount int (typed put)"       "$(q "$t" "SELECT COUNT(*) FROM args WHERE flat_key='debug.todoCount' AND int_value IS NOT NULL")" 1
  assert_ge "[$l] debug.filter string (typed put)"       "$(q "$t" "SELECT COUNT(*) FROM args WHERE flat_key='debug.filter' AND string_value IS NOT NULL")" 1
}

# Per-preset coverage — each Mundus preset must emit its characteristic slice. Names are
# FQN-anchored (Mundus bakes them as compile-time string literals, so they survive R8) to avoid
# false-matching DI-graph slices (e.g. a bare *Application* also hits GeneratedApplicationComponent).
# Tolerant >=1 thresholds absorb monkey-flow variance.
validate_presets() { # $1=trace $2=label
  local t="$1" l="$2"
  assert_ge "[$l] preset:lifecycle (Activity.onCreate)"        "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*sample.MainActivity.onCreate*'")"   1
  assert_ge "[$l] preset:startupPhases (Application.onCreate)" "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*sample.MainApplication.onCreate*'")" 1
  assert_ge "[$l] preset:viewModel (TodoViewModel methods)"    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*sample.library.TodoViewModel.*'")"   1
  assert_ge "[$l] preset:workers (MainWorker.doWork)"          "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*sample.MainWorker.doWork*'")"        1
}

# Structural correctness — thread attribution, manual-span closure, multi-module reach.
validate_structure() { # $1=trace $2=label
  local t="$1" l="$2"
  local jt="JOIN thread_track tt ON s.track_id=tt.id JOIN thread t ON tt.utid=t.utid"
  # Thread attribution: the suspend scoreFor work must run OFF the main thread — proves Mundus
  # follows the coroutine across the Dispatchers.Default hop (a regression would mis-attribute it).
  assert_ge "[$l] scoreFor on a background dispatcher thread" \
    "$(q "$t" "SELECT COUNT(*) FROM slice s $jt WHERE s.name GLOB '*scoreFor*' AND t.name GLOB 'DefaultDispatcher*'")" 1
  assert_eq "[$l] scoreFor NOT on the main thread" \
    "$(q "$t" "SELECT COUNT(*) FROM slice s $jt WHERE s.name GLOB '*scoreFor*' AND t.name='main'")" 0
  # Span balance: every manual beginTokenWith span must be closed by endToken. A closed slice has
  # dur >= 0; an unclosed/leaked one is dur < 0. Directly guards an endToken-imbalance regression.
  assert_eq "[$l] manual statsBatch spans all closed (endToken fired)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*statsBatch*' AND dur < 0")" 0
  # Overall leak guard: unclosed slices at trace end should be a tiny fraction (only the boundary
  # artifact from the last open frame), not a systemic begin/end imbalance.
  local neg tot; neg="$(q "$t" "SELECT COUNT(*) FROM slice WHERE dur < 0")"; tot="$(q "$t" "SELECT COUNT(*) FROM slice")"
  if [ "${tot:-0}" -gt 0 ] && [ "$(( ${neg:-0} * 100 ))" -lt "$(( tot * 2 ))" ]; then
    pass "[$l] unclosed slices < 2% of total ($neg/$tot)"
  else
    fail "[$l] unclosed slices >= 2% ($neg/$tot) — possible begin/end leak"
  fi
  # Multi-module reach: slices from BOTH the app module (:sample) and the library module
  # (:sample-library) must be present — guards a regression that breaks cross-module instrumentation.
  assert_ge "[$l] app-module slices (:sample MainActivity)" "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*sample.MainActivity*'")" 1
  assert_ge "[$l] library-module slices (:sample-library)"  "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*sample.library.*'")"      1
}

# Annotation-driven opt-in/opt-out — AutoTracedDemo lives OUTSIDE includePackages, so a slice for
# it can only come from @AutoTrace. Covers @AutoTrace on a non-coroutine AND a coroutine method,
# plus @NoTrace (a method inside the @AutoTrace class that must stay untraced — opt-out precedence).
validate_autotrace() { # $1=trace $2=label
  local t="$1" l="$2"
  assert_ge "[$l] @AutoTrace non-coroutine (AutoTracedDemo.weigh)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*AutoTracedDemo.weigh*' AND name NOT GLOB '*weighAsync*'")" 1
  assert_ge "[$l] @AutoTrace coroutine (AutoTracedDemo.weighAsync)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*AutoTracedDemo.weighAsync*'")" 1
  assert_eq "[$l] @NoTrace opt-out (AutoTracedDemo.untraced NOT traced)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*AutoTracedDemo.untraced*'")" 0
}

echo "=== Mundus trace scenarios on $DEVICE (mundus $(grep -m1 '^mundus' gradle/libs.versions.toml | cut -d'"' -f2)) ==="
adb get-state >/dev/null 2>&1 || { echo "ERROR: device $DEVICE not available"; exit 2; }

if [ "$WHICH" = "debug" ] || [ "$WHICH" = "both" ]; then
  echo "--- DEBUG scenario ---"
  T="$(run_variant debug 'sample/build/outputs/apk/debug/*.apk')"
  CE="$(q "$T" "SELECT COUNT(*) FROM slice WHERE name GLOB '*androidx.compose*'")"
  assert_ge "[debug] heavy compose tracing active" "$CE" "$COMPOSE_HEAVY_MIN"
  validate_common "$T" debug
  assert_ge "[debug] debug.todo rows (@TraceArg suspend)" "$(q "$T" "SELECT COUNT(*) FROM args WHERE flat_key='debug.todo'")" 1
  # R2 — metadata VALUE correctness (not just presence): guards a regression where typed
  # put() writes garbage or to the wrong column. Unfiltered flow => filter == 'all'; the
  # todo count must be a sane non-negative int, not a corrupted/garbage long.
  assert_ge "[debug] debug.filter value=='all' (string put correct)" "$(q "$T" "SELECT COUNT(*) FROM args WHERE flat_key='debug.filter' AND string_value='all'")" 1
  assert_ge "[debug] debug.todoCount sane int [0,1000] (long put correct)" "$(q "$T" "SELECT COUNT(*) FROM args WHERE flat_key='debug.todoCount' AND int_value>=0 AND int_value<=1000")" 1
  validate_presets "$T" debug
  validate_structure "$T" debug
  validate_autotrace "$T" debug
fi

if [ "$WHICH" = "release" ] || [ "$WHICH" = "both" ]; then
  echo "--- RELEASE scenario (R8 full mode, composeTracing OFF) ---"
  T="$(run_variant release 'sample/build/outputs/apk/release/*.apk' -Pmundus.composeTracing=false)"
  CE="$(q "$T" "SELECT COUNT(*) FROM slice WHERE name GLOB '*androidx.compose*'")"
  assert_eq "[release] heavy compose tracing dropped" "$CE" "0"
  validate_common "$T" release
  # presets + structure must also survive R8 full mode (names are compile-time literals)
  validate_presets "$T" release
  validate_structure "$T" release
  validate_autotrace "$T" release
  if grep -q "message!" "$OUT/scenario-release.logcat.txt" 2>/dev/null; then
    pass "[release] no DI crash (App log emitted under R8 full mode)"
  else
    fail "[release] App DI log missing — possible R8/DI breakage"
  fi
fi

if [ "$WHICH" = "both" ] || [ "$WHICH" = "regression" ]; then
  echo "--- REGRESSION GUARD: composeTracing causality (release WITHOUT the flag must be heavy) ---"
  # Builds release with R8 full mode but does NOT pass -Pmundus.composeTracing=false, so the
  # compose-tracing dep stays and the heavy CompositionTracer should be present. Paired with the
  # main RELEASE scenario (flag on -> 0), this proves the lean release is CAUSED by the flag and
  # not by R8 silently stripping tracing (a false-lean that would make the lean test pass for the
  # wrong reason). Also catches the 0.6.0-class regression where composeTracing can't be turned off.
  T="$(run_variant release-default 'sample/build/outputs/apk/release/*.apk')"
  CE="$(q "$T" "SELECT COUNT(*) FROM slice WHERE name GLOB '*androidx.compose*'")"
  assert_ge "[release-default] compose tracing present without the flag (flag is causal, not R8)" "$CE" "$COMPOSE_HEAVY_MIN"
fi

echo "==="
if [ "$FAILS" -eq 0 ]; then echo "ALL SCENARIOS PASSED"; else echo "$FAILS ASSERTION(S) FAILED"; fi
exit "$FAILS"
