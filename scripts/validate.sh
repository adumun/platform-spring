#!/usr/bin/env bash
set -u
set -o pipefail
shopt -s lastpipe

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"

source scripts/progress.sh

MVNW=${MVNW:-./mvnw}
VALIDATION_DIR=${VALIDATION_DIR:-.validation}
LOG_FILE=${VALIDATION_LOG:-$VALIDATION_DIR/validate.log}
VERBOSE=${VERBOSE:-0}

mkdir -p "$VALIDATION_DIR"
: >"$LOG_FILE"

started_at=$(date +%s)

if [[ "$VERBOSE" == "1" ]]; then
  echo "[validate] Verbose mode — streaming Maven output"
  "$MVNW" -B clean verify 2>&1 | tee "$LOG_FILE"
  status=${PIPESTATUS[0]}
  finished_at=$(date +%s)
  elapsed=$((finished_at - started_at))
  if (( status == 0 )); then
    echo "[validate] PASS — $(progress_duration "$elapsed")"
  else
    echo "[validate] FAIL — $(progress_duration "$elapsed")"
  fi
  exit "$status"
fi

# Reactor steps are derived from the Maven reactor itself. The root project is
# one step plus every declared root module. No synthetic percentage-by-time is used.
module_count=$(grep -c '<module>' pom.xml 2>/dev/null || true)
total=$((module_count + 1))
current=0
last_label='Starting Maven reactor'

progress_render 0 "$total" "$last_label" RUNNING

set +e
"$MVNW" -B clean verify 2>&1 | while IFS= read -r line; do
  printf '%s\n' "$line" >>"$LOG_FILE"

  if [[ "$line" =~ \[INFO\][[:space:]]+Building[[:space:]]+(.+)[[:space:]]+\[([0-9]+)/([0-9]+)\] ]]; then
    label=${BASH_REMATCH[1]}
    current=${BASH_REMATCH[2]}
    reported_total=${BASH_REMATCH[3]}
    if [[ -n "$reported_total" && "$reported_total" -gt 0 ]]; then
      total=$reported_total
    fi
    last_label=$label
    progress_render "$current" "$total" "$label" RUNNING
  fi
done
status=${PIPESTATUS[0]}
set -e

progress_finish_line
finished_at=$(date +%s)
elapsed=$((finished_at - started_at))
warning_count=$(grep -cE '^\[WARNING\]| WARN ' "$LOG_FILE" 2>/dev/null || true)

if (( status == 0 )); then
  progress_render "$total" "$total" "Maven reactor" PASS
  progress_finish_line
  if (( warning_count > 0 )); then
    echo "[validate] WARN — ${warning_count} warning line(s); inspect $LOG_FILE"
  fi
  echo "[validate] PASS — $(progress_duration "$elapsed")"
  echo "[validate] Full log: $LOG_FILE"
  exit 0
fi

progress_render "$current" "$total" "$last_label" FAIL
progress_finish_line
echo "[validate] FAIL — $(progress_duration "$elapsed")"
echo "[validate] Diagnostic:"
grep -E "APPLICATION FAILED TO START|Description:|Action:|Caused by:|\[ERROR\]|Tests run:|BUILD FAILURE|COMPILATION ERROR" "$LOG_FILE" | tail -40 || true
echo "[validate] Full log: $LOG_FILE"
echo "[validate] Re-run with full output: make validate VERBOSE=1"
exit "$status"
