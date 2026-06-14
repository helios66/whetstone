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
#   RELEASE (-Pmundus.compose.tracing=false, R8 full mode):
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
#     - multi-dependency         a SECOND DI dependency (StatsAuditor.audit) traced, not just one
#     - external-lib callees     traceCalleePackages wraps calls into okio + gson; the readable
#                                callee name survives R8 (compile-time literal, pre-obfuscation)
#     - Part B (0.11.1)          inline fns NOT traced (Mundus warns instead — build check);
#                                structured-concurrency child spans (parallel.async#N nested);
#                                exception metadata (error_type + error_message args on .exception);
#                                cancellation: a cancelled suspend fn's span still CLOSES (no leak);
#                                traceFlowOperators + traceLambdas OFF by default (no map#/block# spans)
#   GRANULAR scenario (in `both`): rebuilds with -Pmundus.traceFlowOperators=true -Pmundus.traceLambdas
#     =true and asserts the opt-in spans DO appear (flowConsumer.map#N, <fn>.block#N).
#
# Regression guards (also run in `both`):
#   - metadata VALUE correctness (debug): debug.filter == 'all', debug.todoCount a sane int —
#     catches typed put() writing garbage or to the wrong Perfetto column.
#   - composeTracing causality (release-default): a release built WITHOUT the flag must STILL be
#     heavy. Paired with the lean flagged release, this proves the flag (not R8 over-stripping)
#     causes the lean result — guarding against a false-lean and the 0.6.0 auto-install regression.
#
# Negative control (also run in `both`): a build with -Pmundus.enabled=false -Pmundus.compose.tracing
#   =false. EVERY compiler-injected slice must vanish (proving the positive scenarios' slices are
#   genuinely Mundus-authored), while the hand-written beginTokenWith span survives and the app still
#   runs. Requires the enabled=false fix (Mundus >= 0.10.1); it was a no-op in 0.9.0.
#
# Usage:   scripts/mundus-trace-scenarios.sh [MODE]   (default: both)
#   both      — full gauntlet (debug + release + regression guard + disabled + granular) = CI gate
#   debug | release | disabled | granular | regression — a single gauntlet scenario
#   e2e       — one realistic release journey -> a reviewable trace (trace-e2e-release.perfetto-trace)
# Env:     DEVICE (default emulator-5556), JAVA_HOME, ANDROID_HOME
#
set -euo pipefail

# --- config ---------------------------------------------------------------
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
DEVICE="${DEVICE:-emulator-5556}"
PKG="com.deliveryhero.whetstone.sample"
# Mundus 0.13.0 moved its trace output from the app-private external files dir
# (Android/data/$PKG/files/mundus-traces) to the shared media dir below. The runtime logs the
# active path at startup: "traces in /storage/emulated/0/Android/media/$PKG/mundus-traces".
TDIR="/storage/emulated/0/Android/media/$PKG/mundus-traces"
TP="$ROOT/trace_processor"
OUT="$HOME/AndroidStudioProjects/mundus-traces"
COMPOSE_HEAVY_MIN="${COMPOSE_HEAVY_MIN:-500}"   # debug must exceed this many compose events
WHICH="${1:-both}"

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$HOME/.maestro/bin"
export MAESTRO_CLI_NO_ANALYTICS=1

mkdir -p "$OUT"
# shared adb()/pass()/fail()/assert_ge()/assert_eq()/q() + FAILS (needs DEVICE + TP, set above)
source "$ROOT/scripts/_trace-lib.sh"

# --- build + drive one variant; echoes the pulled trace path --------------
run_variant() { # $1=label  $2=apk-glob  $3...=extra gradle args
  local label="$1" apkglob="$2"; shift 2
  # release* variants -> assembleRelease; everything else (debug, disabled) -> assembleDebug
  local gradle_task; case "$label" in release*) gradle_task=":sample:assembleRelease";; *) gradle_task=":sample:assembleDebug";; esac
  echo ">>> [$label] building ($gradle_task $*)" >&2
  # FAIL LOUDLY on a build error — never fall through to a stale APK (bogus, silently-wrong results).
  # Always clean the APK first: variants that share a task+output path but differ only by -P flags
  # (debug vs disabled, release vs release-default) must not reuse each other's APK.
  rm -f $apkglob 2>/dev/null || true
  if ! ./gradlew "$gradle_task" "$@" --console=plain -q >&2; then
    echo "FATAL [$label] build failed — aborting (no stale-APK fallback)" >&2; exit 3
  fi

  local apk; apk="$(ls -t $apkglob 2>/dev/null | head -1)"
  [ -n "$apk" ] && [ -f "$apk" ] || { echo "FATAL [$label] no APK produced at $apkglob" >&2; exit 3; }
  echo ">>> [$label] installing $apk" >&2
  adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adb shell pm uninstall "$PKG" >/dev/null 2>&1 || true
  adb install -r "$apk" >&2
  adb shell rm -rf "$TDIR" >/dev/null 2>&1 || true
  adb logcat -c >/dev/null 2>&1 || true

  # Drive the flow + capture, with an integrity-checked retry: the streaming-protobuf trace can be
  # truncated mid-flush, producing a corrupt/tiny file. Rather than assert on garbage (a confusing
  # 'got ?'), re-run the flow up to 3x until the pulled trace parses and carries a real slice count.
  # Per-scenario integrity floor: a disabled build legitimately produces only the surviving manual
  # span(s), so it must just PARSE and carry >=1 slice; normal builds must clear a real floor (a
  # truncated trace fails to parse and q returns empty, which is rejected either way).
  local min; case "$label" in disabled) min=1;; *) min=100;; esac
  local dest="$OUT/scenario-$label.perfetto-trace" attempt slices i f
  for attempt in 1 2 3; do
    # Drop any host-side trace from a prior attempt: if this attempt's device-side pull finds nothing
    # (f empty), the pull is skipped and `q "$dest"` must NOT silently re-validate the stale file.
    rm -f "$dest" 2>/dev/null || true
    adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
    adb shell rm -rf "$TDIR" >/dev/null 2>&1 || true
    adb logcat -c >/dev/null 2>&1 || true
    echo ">>> [$label] driving via Maestro (attempt $attempt)" >&2
    # Deterministic UI journey (.maestro/todo-e2e.yaml): launches, opens Stats (fires refreshStats
    # -> computeStatsScore: scoreFor + manual span + all @AutoTrace/@NoTrace fixtures + probe +
    # cancellation), opens a Detail screen, then backgrounds the app (Home) so Mundus flushes.
    maestro --device "$DEVICE" test "$ROOT/.maestro/todo-e2e.yaml" >&2 || true
    adb logcat -d -s App:D > "$OUT/scenario-$label.logcat.txt" 2>/dev/null || true
    # Clean capture: the flow already backgrounded the app (flush); settle past the 500ms interval,
    # force-stop, let the OS finish writing the file, then pull.
    sleep 2
    adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
    sleep 3
    f="$(adb shell ls -t "$TDIR" 2>/dev/null | grep -i '\.perfetto' | head -1 | tr -d '\r')"
    [ -n "$f" ] && adb pull "$TDIR/$f" "$dest" >/dev/null 2>&1
    slices="$(q "$dest" "SELECT COUNT(*) FROM slice")"
    if [ -n "$slices" ] && [ "$slices" -ge "$min" ] 2>/dev/null; then
      echo ">>> [$label] capture OK ($slices slices, attempt $attempt)" >&2
      echo "$dest"; return 0
    fi
    echo ">>> [$label] bad/corrupt trace (slices='${slices:-none}') — retrying" >&2
  done
  echo "FATAL [$label] no valid trace after 3 attempts" >&2; exit 4
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
  # Function-level permutations: @AutoTrace on one method of an UN-annotated class (only that
  # method traces, the sibling stays untraced), and @NoTrace on a method of an IN-includePackages
  # class (stays untraced even though a sibling like getMessage is traced).
  assert_ge "[$l] fn-level @AutoTrace (PartlyTracedDemo.tracedOne)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*PartlyTracedDemo.tracedOne*'")" 1
  assert_eq "[$l] fn-level @AutoTrace sibling NOT traced (PartlyTracedDemo.plainTwo)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*PartlyTracedDemo.plainTwo*'")" 0
  assert_eq "[$l] fn-level @NoTrace in included pkg (MainDependency.silentHelper NOT traced)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*MainDependency.silentHelper*'")" 0
  # Precedence (observed in 0.9.0): a class-level @NoTrace overrides a function-level @AutoTrace —
  # the @AutoTrace method stays untraced. This pins the contract; if Mundus flips it (fn opt-in
  # wins), 'contested' will start tracing and this assertion goes red, flagging the semantic change.
  assert_eq "[$l] precedence: @NoTrace class beats @AutoTrace fn (ConflictDemo.contested untraced)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*ConflictDemo.contested*'")" 0
}

# Negative control — built with -Pmundus.enabled=false -Pmundus.compose.tracing=false. EVERY
# compiler-injected slice must vanish (this is what proves the positive scenarios' slices are
# genuinely Mundus-authored, not ambient), while the hand-written runtime API (beginTokenWith)
# survives — it doesn't go through the compiler. Requires the enabled=false fix (Mundus >= 0.10.1).
validate_disabled() { # $1=trace $2=label
  local t="$1" l="$2"
  local nm
  for nm in 'getMessage' 'scoreFor' 'library.TodoViewModel.' 'MainActivity.onCreate' 'MainWorker.doWork' \
            'AutoTracedDemo.weigh' 'PartlyTracedDemo.tracedOne' 'androidx.compose' \
            'StatsAuditor.audit' 'okio.ByteString' 'com.google.gson.Gson'; do
    assert_eq "[$l] disabled: no '$nm' slices (compiler off)" "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*$nm*'")" 0
  done
  assert_eq "[$l] disabled: no @TraceArg metadata (debug.title)" "$(q "$t" "SELECT COUNT(*) FROM args WHERE flat_key='debug.title'")" 0
  assert_ge "[$l] disabled: manual beginTokenWith span SURVIVES (statsBatch)" "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*statsBatch*'")" 1
}

# Multi-dependency + external-library coverage. Asserts (1) Mundus traces a SECOND distinct Whetstone
# DI dependency (StatsAuditor, alongside MainDependency) — not just one; and (2) traceCalleePackages
# wraps call sites into pre-compiled 3rd-party libs (okio + gson), with the human-readable callee name
# surviving R8 (baked at compile time before obfuscation). Run in BOTH debug and release.
validate_deps() { # $1=trace $2=label
  local t="$1" l="$2"
  assert_ge "[$l] 2nd DI dependency traced (StatsAuditor.audit)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*StatsAuditor.audit'")" 1
  assert_ge "[$l] external-lib call traced: okio (traceCalleePackages)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*okio.ByteString.Companion.encodeUtf8*'")" 1
  assert_ge "[$l] external-lib call traced: gson (traceCalleePackages)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*com.google.gson.Gson.toJson*'")" 1
}

# Part B coverage (shipped in 0.11.1) — driven by the TracingProbe fixture (outside includePackages,
# @AutoTrace). Covers: inline fns are NOT traced (Mundus warns instead — see check_inline_warning);
# structured-concurrency child spans (each async{} nested under its parent); and exception metadata
# (a thrown error yields an .exception slice carrying the exception type + message as args).
validate_partb() { # $1=trace $2=label
  local t="$1" l="$2"
  assert_eq "[$l] inline fn NOT traced (TracingProbe.inlined)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*TracingProbe.inlined*'")" 0
  assert_ge "[$l] structured-concurrency child spans (parallel.async#N >= 2)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*TracingProbe.parallel.async*'")" 2
  assert_ge "[$l] exception slice (throwingTraced.exception)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*throwingTraced.exception*'")" 1
  assert_ge "[$l] exception metadata error_type == IllegalStateException" \
    "$(q "$t" "SELECT COUNT(*) FROM args WHERE flat_key='debug.error_type' AND string_value GLOB '*IllegalStateException'")" 1
  assert_ge "[$l] exception metadata error_message == 'probe-boom'" \
    "$(q "$t" "SELECT COUNT(*) FROM args WHERE flat_key='debug.error_message' AND string_value='probe-boom'")" 1
  # T3#5 cancellation: a suspend fn cancelled mid-flight must still CLOSE its span — a leaked/open
  # span shows as negative duration. The span ran, and NONE of its slices may be unclosed.
  assert_ge "[$l] cancellation: cancellable span ran (TracingProbe.cancellable)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*TracingProbe.cancellable*'")" 1
  assert_eq "[$l] cancellation: span CLOSES cleanly on cancel (no unclosed cancellable slice)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*TracingProbe.cancellable*' AND dur<0")" 0
  # traceFlowOperators + traceLambdas are opt-in (default OFF) — assert they produce NOTHING here
  # (the enabled state is verified in the GRANULAR scenario with both flags on).
  assert_eq "[$l] traceFlowOperators OFF by default (no flowConsumer.map# spans)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*flowConsumer.map#*'")" 0
  assert_eq "[$l] traceLambdas OFF by default (no .block# spans)" \
    "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*.block#*'")" 0
}

# Build-time check: Part B T1 (inline) ships as a WARNING, not a trace. Assert Mundus emits it.
check_inline_warning() {
  local out; out="$(./gradlew :sample-library:clean :sample-library:compileDebugKotlin --console=plain 2>&1 || true)"
  if echo "$out" | grep -qiE "inline fun .* cannot be instrumented"; then
    pass "[build] Mundus warns on an untraced inline fn (T1 trace-or-warn acceptance)"
  else
    fail "[build] expected an inline-fn warning from Mundus, none found"
  fi
}

# Focused validation for the `e2e` mode (a single realistic release journey with ALL opt-in tracing
# on). Asserts the trace is review-worthy: DI + suspend + manual span + client Compose + ViewModel +
# @TraceArg are present, the opt-in flow/lambda spans appear (flags on), and it's lean (no androidx).
validate_e2e() { # $1=trace
  local t="$1"
  assert_ge "[e2e] Whetstone DI ran (getMessage)"          "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*getMessage*'")"                1
  assert_ge "[e2e] background suspend work (scoreFor)"      "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*scoreFor*'")"                  1
  assert_ge "[e2e] manual span (statsBatch)"               "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*statsBatch*'")"                1
  assert_ge "[e2e] client Compose bodies (Screen/Row)"     "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*whetstone*Screen*' OR name GLOB '*whetstone*Row*'")" 1
  assert_ge "[e2e] ViewModel methods (preset)"             "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*library.TodoViewModel.*'")"    1
  assert_ge "[e2e] @TraceArg metadata (debug.title)"       "$(q "$t" "SELECT COUNT(*) FROM args WHERE flat_key='debug.title'")"                   1
  assert_ge "[e2e] Flow operator spans (flags on)"         "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*flowConsumer.map#*'")"         1
  assert_ge "[e2e] lambda-body spans (flags on)"           "$(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*.block#*'")"                   1
  echo "  [e2e] androidx.compose events (lean): $(q "$t" "SELECT COUNT(*) FROM slice WHERE name GLOB '*androidx.compose*'")"
}

echo "=== Mundus trace scenarios on $DEVICE (mundus $(grep -m1 '^mundus' gradle/libs.versions.toml | cut -d'"' -f2)) ==="
adb get-state >/dev/null 2>&1 || { echo "ERROR: device $DEVICE not available"; exit 2; }
command -v maestro >/dev/null 2>&1 || { echo "ERROR: maestro not on PATH — install from https://maestro.mobile.dev"; exit 2; }

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
  validate_partb "$T" debug
  validate_deps "$T" debug
  check_inline_warning
fi

if [ "$WHICH" = "release" ] || [ "$WHICH" = "both" ]; then
  echo "--- RELEASE scenario (R8 full mode, composeTracing OFF) ---"
  T="$(run_variant release 'sample/build/outputs/apk/release/*.apk' -Pmundus.compose.tracing=false)"
  CE="$(q "$T" "SELECT COUNT(*) FROM slice WHERE name GLOB '*androidx.compose*'")"
  assert_eq "[release] heavy compose tracing dropped" "$CE" "0"
  validate_common "$T" release
  # presets + structure must also survive R8 full mode (names are compile-time literals)
  validate_presets "$T" release
  validate_structure "$T" release
  validate_autotrace "$T" release
  validate_partb "$T" release
  validate_deps "$T" release
  if grep -q "message!" "$OUT/scenario-release.logcat.txt" 2>/dev/null; then
    pass "[release] no DI crash (App log emitted under R8 full mode)"
  else
    fail "[release] App DI log missing — possible R8/DI breakage"
  fi
fi

if [ "$WHICH" = "both" ] || [ "$WHICH" = "regression" ]; then
  echo "--- REGRESSION GUARD: composeTracing causality (release WITHOUT the flag must be heavy) ---"
  # Builds release with R8 full mode but does NOT pass -Pmundus.compose.tracing=false, so the
  # compose-tracing dep stays and the heavy CompositionTracer should be present. Paired with the
  # main RELEASE scenario (flag on -> 0), this proves the lean release is CAUSED by the flag and
  # not by R8 silently stripping tracing (a false-lean that would make the lean test pass for the
  # wrong reason). Also catches the 0.6.0-class regression where composeTracing can't be turned off.
  T="$(run_variant release-default 'sample/build/outputs/apk/release/*.apk')"
  CE="$(q "$T" "SELECT COUNT(*) FROM slice WHERE name GLOB '*androidx.compose*'")"
  assert_ge "[release-default] compose tracing present without the flag (flag is causal, not R8)" "$CE" "$COMPOSE_HEAVY_MIN"
fi

if [ "$WHICH" = "both" ] || [ "$WHICH" = "disabled" ]; then
  echo "--- NEGATIVE CONTROL: Mundus disabled (-Pmundus.enabled=false) ---"
  # The whole point: prove the slices our positive scenarios count are genuinely Mundus-compiler-
  # authored (not ambient). With the compiler off, every compiler-injected slice must vanish, while
  # the hand-written runtime API (beginTokenWith) still fires. Requires the enabled=false fix
  # (Mundus >= 0.10.1); on 0.9.0 this scenario fails by design (the flag was a no-op).
  T="$(run_variant disabled 'sample/build/outputs/apk/debug/*.apk' -Pmundus.enabled=false -Pmundus.compose.tracing=false)"
  validate_disabled "$T" disabled
  if grep -q "message!" "$OUT/scenario-disabled.logcat.txt" 2>/dev/null; then
    pass "[disabled] app still runs with tracing off (App DI log emitted)"
  else
    fail "[disabled] App DI log missing — app broke with Mundus disabled"
  fi
fi

if [ "$WHICH" = "both" ] || [ "$WHICH" = "granular" ]; then
  echo "--- GRANULAR: traceFlowOperators + traceLambdas ENABLED (opt-in flags) ---"
  # The opt-in counterpart to the OFF-by-default checks in validate_partb. With both flags on,
  # Flow operators get nested spans (flowConsumer.map#N) and lambda bodies get nested spans
  # (<fn>.block#N). Proves the flags actually do something (not no-ops).
  T="$(run_variant granular 'sample/build/outputs/apk/debug/*.apk' -Pmundus.traceFlowOperators=true -Pmundus.traceLambdas=true)"
  assert_ge "[granular] traceFlowOperators ON -> Flow operator spans (flowConsumer.map#)" \
    "$(q "$T" "SELECT COUNT(*) FROM slice WHERE name GLOB '*flowConsumer.map#*'")" 1
  assert_ge "[granular] traceLambdas ON -> lambda-body spans (.block#)" \
    "$(q "$T" "SELECT COUNT(*) FROM slice WHERE name GLOB '*.block#*'")" 1
fi

if [ "$WHICH" = "e2e" ]; then
  echo "--- E2E: realistic release journey -> reviewable trace (all opt-in tracing on) ---"
  # A single deterministic user journey on a real release build, with the heavy androidx
  # CompositionTracer off but flow/lambda tracing on — the cleanest trace to actually read.
  T="$(run_variant release-e2e 'sample/build/outputs/apk/release/*.apk' \
        -Pmundus.compose.tracing=false -Pmundus.traceFlowOperators=true -Pmundus.traceLambdas=true)"
  validate_e2e "$T"
  REVIEW="$OUT/trace-e2e-release.perfetto-trace"; cp "$T" "$REVIEW"
  echo "  [e2e] review trace -> $REVIEW (open in https://ui.perfetto.dev)"
fi

echo "==="
if [ "$FAILS" -eq 0 ]; then echo "ALL SCENARIOS PASSED"; else echo "$FAILS ASSERTION(S) FAILED"; fi
exit "$FAILS"
