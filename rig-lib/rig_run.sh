#!/usr/bin/env bash
# rig_run.sh: run one brief script under the rig lock, with a hard time cap, in its own session.
# Usage: rig_run.sh RUN CAP_S SCRIPT [--source LIB.sh]...    (EV must be set)
#        rig_run.sh reap                                     kill every session a run in $EV left behind
# A script calls a rig-lib tool as python3 -I "$RL_HOME/rig_forget.py" (or rig_pair.py).
# The script runs with rig_lib.sh and each --source file loaded, stdin closed, and the lock fd closed,
# so no child can hold the lock. On exit, timeout or a signal, the whole session is killed before the
# lock is freed. Writes $EV/RUN-HHMMSS.log (last line "script exit=N") and $EV/RUN-HHMMSS.status
# (STEP, HB, STALL, TIMEOUT, ORPHANS, DONE lines). Refuses to start without a fresh $EV/requirements.ok.
# RL_STALL_KILL=1 (rig_session.py sets it) ends a run on its first STALL instead of only reporting it.
# Env: RIG_LOCK (/tmp/ohu-rig.lock), RL_STALL_S (180: a step older than this is reported), RL_HB_S (30).
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
: "${EV:?set EV to the evidence dir of the round}"
LOCK=${RIG_LOCK:-/tmp/ohu-rig.lock}
STALL_S=${RL_STALL_S:-180}; HB_S=${RL_HB_S:-30}; FRESH_S=${RL_REQUIRES_FRESH_S:-43200}

lock_holders() {
  local p fd
  for p in /proc/[0-9]*; do
    for fd in "$p"/fd/*; do
      [ "$(readlink "$fd" 2>/dev/null)" = "$LOCK" ] && { echo "  pid ${p#/proc/}: $(tr '\0' ' ' < "$p/cmdline")"; break; }
    done
  done 2>/dev/null
}

session_pids() { ps -e -o pid=,sid= 2>/dev/null | awk -v s="$1" '$2==s {print $1}'; }

kill_session() {  # TERM then KILL every process in session $1; prints how many were alive
  local sid=$1 n left
  n=$(session_pids "$sid" | wc -l)
  [ "$n" -gt 0 ] || { echo 0; return; }
  pkill -TERM -s "$sid" 2>/dev/null
  for _ in 1 2 3 4 5 6 7 8 9 10; do left=$(session_pids "$sid" | wc -l); [ "$left" -eq 0 ] && break; sleep 0.3; done
  pkill -KILL -s "$sid" 2>/dev/null
  echo "$n"
}

if [ "${1:-}" = reap ]; then
  for f in "$EV"/*.sid; do
    [ -f "$f" ] || continue
    sid=$(cat "$f"); n=$(kill_session "$sid")
    [ "$n" -gt 0 ] && echo "REAPED $(basename "$f" .sid) session $sid: $n processes"
    rm -f "$f"
  done
  echo "reap done"; exit 0
fi

RUN=${1:?RUN}; CAP=${2:?CAP_S}; SCRIPT=${3:?SCRIPT}; shift 3
SOURCES=()
while [ $# -gt 0 ]; do
  case "$1" in --source) SOURCES+=("$(readlink -f "$2")"); shift 2 ;; *) echo "rig_run.sh: unknown argument $1" >&2; exit 64 ;; esac
done
SCRIPT=$(readlink -f "$SCRIPT")
mkdir -p "$EV"

# Gate 1: the session's requirements were read back and the operator said yes (rig_requires.py check).
REQ="$EV/requirements.ok"
if [ ! -f "$REQ" ] || [ $(( $(date +%s) - $(stat -c %Y "$REQ") )) -gt "$FRESH_S" ]; then
  echo "REFUSED: $REQ is missing or older than ${FRESH_S}s. Run rig_requires.py check first." >&2; exit 78
fi
# Attempts are counted, not gated: rig_session.py retries a run once and then grades it INCONCLUSIVE.
ATT="$EV/attempts-$RUN"; n=$(( $(cat "$ATT" 2>/dev/null || echo 0) + 1 )); echo "$n" > "$ATT"

exec 9>"$LOCK"
if ! flock -n 9; then
  echo "LOCKED: $LOCK is held by:" >&2; lock_holders >&2; exit 75
fi

TS=$(date +%H%M%S)
LOG="$EV/$RUN-$TS.log"; STATUS="$EV/$RUN-$TS.status"
RUNNER="$EV/.runner-$RUN-$TS.sh"
{
  echo "source '$HERE/rig_lib.sh'"
  echo "rl_init"
  for s in "${SOURCES[@]}"; do echo "source '$s'"; done
  echo "source '$SCRIPT'"
} > "$RUNNER"
echo "$(date +%T) $RUN START cap=${CAP}s attempt=$n script=$SCRIPT" > "$STATUS"
echo "$(date +%T) $RUN START cap=${CAP}s attempt=$n" >> "$(dirname "$EV")/PROGRESS.log"
echo "RUN $RUN log=$LOG status=$STATUS"

export EV RUN RL_EV="$EV" RL_STATUS="$STATUS" RL_HOME="$HERE"   # RL_HOME: this rig-lib, for its .py tools
setsid bash "$RUNNER" 9>&- </dev/null >"$LOG" 2>&1 &
CPID=$!
sleep 0.2
SID=$(ps -o sid= -p "$CPID" 2>/dev/null | tr -d ' '); SID=${SID:-$CPID}
echo "$SID" > "$EV/$RUN-$TS.sid"

finish() {
  local code=$1 why=$2 n
  n=$(kill_session "$SID")
  [ "$n" -gt 0 ] && echo "$(date +%T) $RUN ORPHANS killed=$n ($why)" >> "$STATUS"
  rm -f "$EV/$RUN-$TS.sid" "$RUNNER"
  echo "script exit=$code" >> "$LOG"
  echo "$(date +%T) $RUN DONE exit=$code" >> "$STATUS"
  echo "$(date +%T) $RUN DONE exit=$code" >> "$(dirname "$EV")/PROGRESS.log"
  echo "RUN $RUN exit=$code"
  exit "$code"
}
trap 'finish 143 "wrapper signalled"' TERM INT HUP

start=$(date +%s); last_hb=$start; stalled=""
while kill -0 "$CPID" 2>/dev/null; do
  sleep 1
  now=$(date +%s); el=$((now - start))
  if [ "$el" -ge "$CAP" ]; then
    echo "$(date +%T) $RUN TIMEOUT after ${CAP}s, last $(grep -a ' STEP ' "$STATUS" | tail -1 | cut -d' ' -f3-)" >> "$STATUS"
    finish 124 "timeout"
  fi
  if [ $((now - last_hb)) -ge "$HB_S" ]; then
    last_hb=$now
    step=$(grep -a ' STEP ' "$STATUS" | tail -1)
    if [ -n "$step" ]; then age=$(( now - $(date -d "${step%% *}" +%s 2>/dev/null || echo "$start") )); else age=$el; fi
    [ "$age" -lt 0 ] && age=$el
    kids=$(ps -o args= --ppid "$CPID" 2>/dev/null | cut -c1-60 | tr '\n' ';')
    echo "$(date +%T) $RUN HB ${el}s step_age=${age}s children=${kids:-none}" >> "$STATUS"
    wcap=$(echo "$step" | sed -n 's/.* STEP WAIT cap=\([0-9]*\)s.*/\1/p')
    lim=$STALL_S; [ -n "$wcap" ] && [ "$wcap" -gt "$lim" ] && lim=$((wcap + 30))
    if [ "$age" -gt "$lim" ] && [ -z "$stalled" ]; then
      stalled=1
      echo "$(date +%T) $RUN STALL step unchanged ${age}s: ${step#* STEP } | waiting on: ${kids:-nothing}" >> "$STATUS"
      [ "${RL_STALL_KILL:-}" = 1 ] && finish 125 "stall"
    fi
  fi
done
wait "$CPID"; finish $? "script exited"
