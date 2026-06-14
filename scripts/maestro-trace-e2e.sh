#!/usr/bin/env bash
#
# maestro-trace-e2e.sh — drive the RELEASE sample app with a Maestro e2e flow and capture +
# validate the Mundus Perfetto trace it produces.
#
# Unlike mundus-trace-scenarios.sh (monkey-driven, many scenarios), this is a single deterministic
# end-to-end user journey driven by Maestro (.maestro/todo-e2e.yaml) against a real release build.
#
# Pipeline: build release APK -> install -> run Maestro flow -> background+flush+force-stop+pull
#           -> validate the trace via trace_processor -> save it for review.
#
# Requires: Maestro on PATH (https://maestro.mobile.dev), a running emulator/device, JDK 21, SDK.
# Usage:    scripts/maestro-trace-e2e.sh
# Env:      DEVICE (default emulator-5556)
#
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$ROOT"
DEVICE="${DEVICE:-emulator-5556}"
PKG="com.deliveryhero.whetstone.sample"
TDIR="/storage/emulated/0/Android/data/$PKG/files/mundus-traces"
TP="$ROOT/trace_processor"
OUT="$HOME/AndroidStudioProjects/mundus-traces"
DEST="$OUT/trace-maestro-e2e-release.perfetto-trace"
FLOW="$ROOT/.maestro/todo-e2e.yaml"
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$HOME/.maestro/bin"
mkdir -p "$OUT"
# shared adb()/pass()/fail()/assert_ge()/assert_eq()/q() + FAILS (needs DEVICE + TP, set above)
source "$ROOT/scripts/_trace-lib.sh"

command -v maestro >/dev/null 2>&1 || { echo "ERROR: maestro not on PATH — install from https://maestro.mobile.dev"; exit 2; }
adb get-state >/dev/null 2>&1 || { echo "ERROR: device $DEVICE not available"; exit 2; }

echo ">>> building RELEASE apk (R8 full mode; lean compose + flow/lambda tracing on)"
./gradlew :sample:clean :sample:assembleRelease \
  -Pmundus.composeTracing=false -Pmundus.traceFlowOperators=true -Pmundus.traceLambdas=true \
  --console=plain -q
APK="$(ls -t sample/build/outputs/apk/release/*.apk | head -1)"
[ -f "$APK" ] || { echo "FATAL: no release APK"; exit 3; }

echo ">>> installing $APK"
adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb shell pm uninstall "$PKG" >/dev/null 2>&1 || true
adb install -r "$APK" >&2
adb shell rm -rf "$TDIR" >/dev/null 2>&1 || true

echo ">>> running Maestro flow: $FLOW"
maestro --device "$DEVICE" test "$FLOW"

echo ">>> capturing trace (settle -> force-stop -> pull)"
sleep 2
adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
sleep 3
F="$(adb shell ls -t "$TDIR" 2>/dev/null | grep -i '\.perfetto' | head -1 | tr -d '\r')"
[ -n "$F" ] || { echo "FATAL: no trace produced — did the flow run?"; exit 4; }
adb pull "$TDIR/$F" "$DEST" >&2

echo "=== validating the Maestro-driven release trace ==="
SL="$(q "$DEST" "SELECT COUNT(*) FROM slice")"
[ -n "$SL" ] && [ "$SL" -ge 100 ] 2>/dev/null || { echo "FATAL: trace did not parse / too few slices ('$SL')"; exit 4; }
pass "trace parses ($SL slices)"
assert_ge "Whetstone DI ran (getMessage)"            "$(q "$DEST" "SELECT COUNT(*) FROM slice WHERE name GLOB '*getMessage*'")"                1
assert_ge "background suspend work (scoreFor)"       "$(q "$DEST" "SELECT COUNT(*) FROM slice WHERE name GLOB '*scoreFor*'")"                  1
assert_ge "manual span (statsBatch)"                 "$(q "$DEST" "SELECT COUNT(*) FROM slice WHERE name GLOB '*statsBatch*'")"                1
assert_ge "client Compose bodies (Screen/Row)"       "$(q "$DEST" "SELECT COUNT(*) FROM slice WHERE name GLOB '*whetstone*Screen*' OR name GLOB '*whetstone*Row*'")" 1
assert_ge "ViewModel methods (preset)"               "$(q "$DEST" "SELECT COUNT(*) FROM slice WHERE name GLOB '*library.TodoViewModel.*'")"    1
assert_ge "@TraceArg metadata (debug.title)"         "$(q "$DEST" "SELECT COUNT(*) FROM args WHERE flat_key='debug.title'")"                   1
echo "  androidx.compose events (lean, expect ~0): $(q "$DEST" "SELECT COUNT(*) FROM slice WHERE name GLOB '*androidx.compose*'")"

echo "==="
if [ "$FAILS" -eq 0 ]; then echo "E2E TRACE OK — $DEST"; else echo "$FAILS CHECK(S) FAILED"; fi
exit "$FAILS"
