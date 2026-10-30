#!/usr/bin/env bash
# rl_capture.sh SERIAL OUTBASE [logcat args]: logcat -v threadtime to OUTBASE.txt, restarted when it dies.
# rl_cap_start runs it as a tracked job. A capture died silently on D-HP in long runs; this one comes back.
# After a restart the device dumps its buffer again, so lines stamped at or before the last line written are
# dropped: a wait that counts new bytes never sees an old line twice. Each restart adds a line to OUTBASE.restarts.
s=$1 base=$2; shift 2
first=1
while :; do
  if [ "$first" = 0 ]; then echo "##RL_RESTART"; date +%T >> "$base.restarts"; fi
  first=0
  stdbuf -oL adb -s "$s" logcat -v threadtime "$@" 2>&1
  sleep 2
done | awk '
  $0 == "##RL_RESTART" { skip = 1; next }
  /^(error: |- waiting for device -|adb: )/ { next }
  match($0, /^[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9]\.[0-9]+/) {
    ts = substr($0, 1, RLENGTH)
    if (skip && ts <= last) next
    skip = 0; last = ts
  }
  { print; fflush() }' > "$base.txt"
