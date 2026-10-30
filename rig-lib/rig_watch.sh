#!/usr/bin/env bash
# rig_watch.sh [--keep] [ROUNDS_DIR]: a live view of the rig's progress for the operator, in its own terminal.
# Prints the last 20 lines of ROUNDS_DIR/PROGRESS.log, then every new line as it is written, and stops by
# itself when the session it follows writes DONE. An ESCALATE line stays on screen: the host reruns the session
# after the fix, and the view goes on. --keep follows every later session too.
# Default ROUNDS_DIR: $RIG_DATA/rounds, else ~/Coding/StudioProjects/rig-data/rounds. Ctrl+C also stops it.
set -u
keep=0; [ "${1:-}" = --keep ] && { keep=1; shift; }
dir=${1:-${RIG_DATA:+$RIG_DATA/rounds}}; dir=${dir:-$HOME/Coding/StudioProjects/rig-data/rounds}
log="$dir/PROGRESS.log"
mkdir -p "$dir"; touch "$log"
echo "== rig progress: $log (stops at the session's DONE$([ "$keep" = 1 ] && echo '; --keep: never'))"
tail -n 20 "$log" | tr -d '\r'; echo
start=$(stat -c %s "$log")
tail -c +$((start + 1)) -F "$log" 2>/dev/null | tr -d '\r' &
TP=$!
trap 'kill "$TP" 2>/dev/null' EXIT INT TERM
# Watch the bytes written after the start; the first session line names the session we follow.
sess=""
while kill -0 "$TP" 2>/dev/null; do
  sleep 2
  new=$(tail -c +$((start + 1)) "$log" 2>/dev/null)
  [ -n "$sess" ] || sess=$(printf '%s\n' "$new" | sed -n 's/^[0-9:]* session \([^ ]*\) .*/\1/p' | head -1)
  if [ "$keep" = 0 ] && [ -n "$sess" ] && printf '%s\n' "$new" | grep -qE "^[0-9:]+ session $sess DONE"; then
    sleep 1
    echo "== session $sess finished; its summary: $dir/$sess/summary.md"
    exit 0
  fi
done
