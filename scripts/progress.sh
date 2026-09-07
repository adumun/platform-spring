#!/usr/bin/env bash

# Shared terminal progress primitives for repository commands.
# Presentation only: callers remain responsible for real stage transitions.

progress_is_interactive() {
  [[ -t 1 && "${CI:-}" != "true" ]]
}

progress_duration() {
  local seconds=${1:-0}
  if (( seconds >= 60 )); then
    printf '%dm%02ds' $((seconds / 60)) $((seconds % 60))
  else
    printf '%ds' "$seconds"
  fi
}

progress_render() {
  local current=$1
  local total=$2
  local label=$3
  local state=${4:-RUNNING}
  local width=24
  local filled=0
  local empty=0
  local bar_fill=''
  local bar_empty=''
  local prefix=${PROGRESS_PREFIX:-validate}

  if (( total > 0 )); then
    filled=$((current * width / total))
  fi
  (( filled > width )) && filled=$width
  empty=$((width - filled))

  printf -v bar_fill '%*s' "$filled" ''
  printf -v bar_empty '%*s' "$empty" ''
  bar_fill=${bar_fill// /#}
  bar_empty=${bar_empty// /-}

  if progress_is_interactive; then
    printf '\r[%s] [%s%s] %d/%d %-36s %s' "$prefix" "$bar_fill" "$bar_empty" "$current" "$total" "$label" "$state"
  else
    printf '[%s] [%d/%d] %s — %s\n' "$prefix" "$current" "$total" "$label" "$state"
  fi
}

progress_finish_line() {
  if progress_is_interactive; then
    printf '\n'
  fi
}
