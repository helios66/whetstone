#!/usr/bin/env bash
# _trace-lib.sh — shared helpers for the Mundus Perfetto-trace scripts
# (mundus-trace-scenarios.sh + maestro-trace-e2e.sh).
#
# Source AFTER setting two variables:
#   DEVICE  — the adb serial (e.g. emulator-5556)
#   TP      — path to the trace_processor binary
# FAILS is initialized here; the assertion helpers increment it.

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

# scalar query against a trace; returns the last non-header line, unquoted
q() {
  local tf; tf="$(mktemp)"; printf '%s\n' "$2" > "$tf"
  local r; r="$("$TP" "$1" -q "$tf" 2>/dev/null | tail -1 | tr -d '"' || true)"
  rm -f "$tf"; printf '%s' "$r"
}
