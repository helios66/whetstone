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

echo "=== Mundus trace scenarios on $DEVICE (mundus $(grep -m1 '^mundus' gradle/libs.versions.toml | cut -d'"' -f2)) ==="
adb get-state >/dev/null 2>&1 || { echo "ERROR: device $DEVICE not available"; exit 2; }

if [ "$WHICH" = "debug" ] || [ "$WHICH" = "both" ]; then
  echo "--- DEBUG scenario ---"
  T="$(run_variant debug 'sample/build/outputs/apk/debug/*.apk')"
  CE="$(q "$T" "SELECT COUNT(*) FROM slice WHERE name GLOB '*androidx.compose*'")"
  assert_ge "[debug] heavy compose tracing active" "$CE" "$COMPOSE_HEAVY_MIN"
  validate_common "$T" debug
  assert_ge "[debug] debug.todo rows (@TraceArg suspend)" "$(q "$T" "SELECT COUNT(*) FROM args WHERE flat_key='debug.todo'")" 1
fi

if [ "$WHICH" = "release" ] || [ "$WHICH" = "both" ]; then
  echo "--- RELEASE scenario (R8 full mode, composeTracing OFF) ---"
  T="$(run_variant release 'sample/build/outputs/apk/release/*.apk' -Pmundus.composeTracing=false)"
  CE="$(q "$T" "SELECT COUNT(*) FROM slice WHERE name GLOB '*androidx.compose*'")"
  assert_eq "[release] heavy compose tracing dropped" "$CE" "0"
  validate_common "$T" release
  if grep -q "message!" "$OUT/scenario-release.logcat.txt" 2>/dev/null; then
    pass "[release] no DI crash (App log emitted under R8 full mode)"
  else
    fail "[release] App DI log missing — possible R8/DI breakage"
  fi
fi

echo "==="
if [ "$FAILS" -eq 0 ]; then echo "ALL SCENARIOS PASSED"; else echo "$FAILS ASSERTION(S) FAILED"; fi
exit "$FAILS"
