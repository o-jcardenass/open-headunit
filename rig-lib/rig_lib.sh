# rig_lib.sh: shared helpers for brief scripts. rig_run.sh sources it before every run script.
# A brief calls these and never defines its own waits, captures, radio helpers or background loops:
# brief_check.py refuses a brief that does. Tests: rig-lib/tests/test_rig_lib.sh (fake adb).
# Env: EV (evidence dir) and RUN (run id), both set by rig_run.sh. RL_SMOKE=ok|timeout is the
# brief_check.py smoke run: device reads short-circuit and waits return at once.

RL_VERSION=1
RL_LIB_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
RL_EV="${RL_EV:-${EV:-}}"
RL_PIDS="${RL_EV:+$RL_EV/pids-${RUN:-run}.txt}"
RL_PROGRESS="${RL_PROGRESS:-${RL_EV:+$(dirname "$RL_EV")/PROGRESS.log}}"
export LC_ALL=C

rl_log() {   # rl_log MESSAGE: one line to the run's status file and the shared PROGRESS.log
  local line; line="$(date +%T) ${RUN:-?} $(printf '%s' "$*" | tr '\r\n' '  ')"   # one entry per line in PROGRESS.log
  [ -n "${RL_STATUS:-}" ] && echo "$line" >> "$RL_STATUS"
  [ -n "$RL_PROGRESS" ] && echo "$line" >> "$RL_PROGRESS"
  return 0
}

rl_step() {  # rl_step NAME: mark the start of a step; rig_run.sh reports a step that stops moving
  RL_STEP="$*"; rl_log "STEP $*"
}

rl_bg() {    # rl_bg TAG CMD...: start a tracked background job. The pid is in RL_PID.
  local tag=$1; shift
  "$@" & RL_PID=$!
  [ -n "$RL_PIDS" ] && echo "$RL_PID $tag" >> "$RL_PIDS"
  rl_log "BG $tag pid=$RL_PID"
}

rl_kill_tree() {  # rl_kill_tree PID: TERM the pid and every descendant, then KILL what is left
  local root=$1 all="" queue="$1" p kids
  [ -n "$root" ] || return 0
  while [ -n "$queue" ]; do
    set -- $queue; p=$1; shift; queue="$*"
    all="$all $p"
    kids=$(ps -o pid= --ppid "$p" 2>/dev/null | tr -s ' \n' ' ')
    queue="$queue $kids"; queue=$(echo $queue)
  done
  kill -TERM $all 2>/dev/null
  for _ in 1 2 3 4 5; do kill -0 $all 2>/dev/null || break; sleep 0.2; done
  kill -KILL $all 2>/dev/null
  return 0
}

rl_stop() {  # rl_stop TAG: stop every tracked job with that tag (a prefix match, so "cap:" stops all captures)
  [ -n "$RL_PIDS" ] && [ -f "$RL_PIDS" ] || return 0
  local pid tag keep=""
  while read -r pid tag; do
    case "$tag" in "$1"*) rl_kill_tree "$pid"; wait "$pid" 2>/dev/null; rl_log "STOP $tag pid=$pid" ;;
                   *) keep="$keep$pid $tag"$'\n' ;; esac
  done < "$RL_PIDS"
  printf '%s' "$keep" > "$RL_PIDS"
}

rl_stop_all() { rl_stop ""; }

rl_cap_start() {  # rl_cap_start TAG SERIAL [logcat args]: capture to $EV/TAG.txt (threadtime), restarted if it dies
  local tag=$1 serial=$2; shift 2
  if [ -f "$RL_LIB_DIR/rl_capture.sh" ]; then
    rl_bg "cap:$tag" bash "$RL_LIB_DIR/rl_capture.sh" "$serial" "$RL_EV/$tag" "$@"
  else   # the brief_check smoke copies rig_lib.sh alone
    rl_bg "cap:$tag" stdbuf -oL adb -s "$serial" logcat -v threadtime "$@" > "$RL_EV/$tag.txt" 2>&1
  fi
  sleep 2
}

rl_cap_stop() {   # rl_cap_stop TAG (no TAG stops every capture); a restarted capture is named in the status log
  sleep 2; rl_stop "cap:$1"
  [ -n "$1" ] && [ -s "$RL_EV/$1.restarts" ] && rl_log "CAPTURE $1 restarted $(grep -c . "$RL_EV/$1.restarts") times"
  return 0
}

rl_connect() {  # rl_connect ADDR [CAP_S]: adb connect until the unit answers a shell command (default 30 s)
  local addr=$1 cap=${2:-30} start
  rl_step "CONNECT $addr cap=${cap}s"
  [ -n "${RL_SMOKE:-}" ] && { echo "CONNECTED $addr (smoke)"; return 0; }
  start=$(date +%s)
  while :; do   # "offline" and "authorizing" read as not yet: keep asking until the cap
    adb connect "$addr" >/dev/null 2>&1
    if [ "$(adb -s "$addr" get-state 2>/dev/null | tr -d '\r')" = device ] &&
       [ "$(adb -s "$addr" shell echo rl-ok 2>/dev/null | tr -d '\r')" = rl-ok ]; then
      echo "CONNECTED $addr after $(( $(date +%s) - start ))s"; return 0
    fi
    [ $(( $(date +%s) - start )) -ge "$cap" ] && break
    sleep 1
  done
  echo "CONNECT_TIMEOUT $addr ${cap}s"; rl_log "CONNECT_TIMEOUT $addr"; return 1
}

rl_broadcast() {  # rl_broadcast SERIAL ACTION [am extras]: an ordered broadcast to the app's AutomationReceiver
  # Returns 0 when the app answered "ok":true, 1 when it answered false (the data says why), 2 when nothing
  # answered (a build older than the reply, or a broadcast that was never delivered). Prints am's output.
  local s=$1 a=$2 out; shift 2
  [ -n "${RL_SMOKE:-}" ] && { echo "BROADCAST $a (smoke)"; return 0; }
  out=$(adb -s "$s" shell am broadcast -f 0x00000020 \
    -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.automation.AutomationReceiver \
    -a "com.andrerinas.openheadunit.$a" "$@" 2>&1 | tr -d '\r')
  echo "$out"
  case "$out" in
    *'"ok":true'*) return 0 ;;
    *'"ok":false'*) rl_log "BROADCAST_REFUSED $s $a"; return 1 ;;
    *) rl_log "BROADCAST_UNANSWERED $s $a"; return 2 ;;
  esac
}

rl_offset() { stat -c %s "$1" 2>/dev/null || echo 0; }   # rl_offset FILE: its size in bytes now

rl_wait_count() {  # rl_wait_count FILE N STRING CAP_S [FROM_BYTE]: wait until STRING has printed N times
  # Counts from FROM_BYTE (default 0, the start of the capture), so a line printed before the call counts.
  # Pass FROM_BYTE=$(rl_offset FILE) to count only new lines. Prints "ok <s>s" or "TIMEOUT <s>s count=<k>".
  local f=$1 n=$2 s=$3 cap=$4 from=${5:-0} start now c=0
  rl_step "WAIT cap=${cap}s n=$n '$s'"
  case "${RL_SMOKE:-}" in ok) echo "ok 0s"; return 0 ;; timeout) echo "TIMEOUT 0s count=0"; return 1 ;; esac
  start=$(date +%s)
  while :; do
    c=$(tail -c +$((from + 1)) "$f" 2>/dev/null | grep -acF -- "$s")
    [ "${c:-0}" -ge "$n" ] && { echo "ok $(( $(date +%s) - start ))s"; return 0; }
    now=$(date +%s); [ $((now - start)) -ge "$cap" ] && break
    sleep 0.5
  done
  echo "TIMEOUT ${cap}s count=${c:-0}"; return 1
}

rl_wait_line() { rl_wait_count "$1" 1 "$2" "$3" "${4:-0}"; }   # rl_wait_line FILE STRING CAP_S [FROM_BYTE]

rl_sleep() { rl_step "WAIT cap=${1}s SLEEP"; [ -n "${RL_SMOKE:-}" ] || sleep "$1"; }   # a timed wait; rig_run.sh allows it plus 30 s

rl_radio_state() {  # rl_radio_state SERIAL wifi|bt: prints on, off or unknown
  local out st
  if [ "$2" = bt ]; then
    out=$(adb -s "$1" shell dumpsys bluetooth_manager 2>/dev/null | grep -a -m1 -E '^ *(state|enabled):' | tr -d '\r')
    case "$out" in *state:*BLE*) st=off ;; *ON*|*true*) st=on ;; *OFF*|*false*) st=off ;; *) st=unknown ;; esac
  else
    out=$(adb -s "$1" shell dumpsys wifi 2>/dev/null | grep -a -m1 'Wi-Fi is' | tr -d '\r')
    case "$out" in *enabled*) st=on ;; *disabled*) st=off ;; *) st=unknown ;; esac
  fi
  if [ "$st" = unknown ]; then   # API 17/19 dumps print no state line; the global setting is there from 4.2
    out=$(adb -s "$1" shell settings get global "$([ "$2" = bt ] && echo bluetooth_on || echo wifi_on)" 2>/dev/null | tr -d '\r ')
    case "$out" in 1|2) st=on ;; 0|3) st=off ;; esac
  fi
  echo "$st"
}

rl_radio() {  # rl_radio SERIAL wifi|bt on|off: switch, then read back for RL_RADIO_WAIT_S (35; D-HP takes 30). 1 on a mismatch.
  local serial=$1 r=$2 want=$3 got i svcname=$2
  [ "$r" = bt ] && svcname=bluetooth
  rl_step "RADIO $serial $r=$want"
  [ -n "${RL_SMOKE:-}" ] && { echo "RADIO $serial $r=$want (smoke)"; return 0; }
  adb -s "$serial" shell svc "$svcname" "$([ "$want" = on ] && echo enable || echo disable)"
  for i in $(seq 1 $(( ${RL_RADIO_WAIT_S:-35} * 2 ))); do
    got=$(rl_radio_state "$serial" "$r"); [ "$got" = "$want" ] && break; sleep 0.5
  done
  if [ "$got" = "$want" ]; then echo "RADIO $serial $r=$got"; return 0; fi
  echo "RADIO_MISMATCH $serial $r want=$want got=$got"; rl_log "RADIO_MISMATCH $serial $r want=$want got=$got"; return 1
}

rl_hold_off() {  # rl_hold_off SERIAL wifi|bt [PERIOD_S]: switch off and keep it off (some phones turn WiFi back on)
  local serial=$1 r=$2 p=${3:-3} svcname=$2
  [ "$r" = bt ] && svcname=bluetooth
  rl_radio "$serial" "$r" off || return 1
  rl_bg "hold:$serial:$r" bash -c "while :; do adb -s '$serial' shell svc $svcname disable >/dev/null 2>&1; sleep $p; done"
}

rl_release() { rl_stop "hold:$1:$2"; }   # rl_release SERIAL wifi|bt: stop the hold loop; does not switch the radio on

rl_bonds() {  # rl_bonds SERIAL: print the bonded list. Refuses (exit 2) while Bluetooth is off: the list reads empty then.
  local st; st=$(rl_radio_state "$1" bt)
  [ -n "${RL_SMOKE:-}" ] && return 0
  [ "$st" = on ] || { echo "BONDS_RADIO_OFF $1 bt=$st"; return 2; }
  adb -s "$1" shell dumpsys bluetooth_manager 2>/dev/null | grep -a -A30 'Bonded devices' | tr -d '\r'
}

rl_bonded() {  # rl_bonded SERIAL OTHER_SERIAL: is OTHER in SERIAL's bonded list (by name or address)?
  local name addr list
  [ -n "${RL_SMOKE:-}" ] && { echo "BONDED $1 -> $2 (smoke)"; return 0; }
  name=$(adb -s "$2" shell settings get secure bluetooth_name 2>/dev/null | tr -d '\r')
  addr=$(adb -s "$2" shell settings get secure bluetooth_address 2>/dev/null | tr -d '\r')
  list=$(rl_bonds "$1") || { echo "$list"; return 2; }
  if { [ -n "$name" ] && [ "$name" != null ] && echo "$list" | grep -aqiF -- "$name"; } ||
     { [ -n "$addr" ] && [ "$addr" != null ] && echo "$list" | grep -aqiF -- "$addr"; }; then
    echo "BONDED $1 -> $2 ($name)"; return 0
  fi
  echo "NOT_BONDED $1 -> $2 (name=$name addr=$addr)"; return 1
}

rl_thermal_gate() {  # rl_thermal_gate [LIMIT_C]: wait for the host to cool, then log it for the run (limits: rig-quirks/topics/tooling.md)
  local lim=${1:-75}
  rl_step "WAIT cap=${RL_THERMAL_CAP_S:-1800}s thermal gate ${lim}C"   # a long cool-down is a wait, not a stall
  "$RIG_TOOLKIT/rig_thermal.sh" wait "$lim" || "$RIG_TOOLKIT/rig_thermal.sh" wait "$lim" || { echo HOST_HOT; return 1; }
  rl_bg thermal "$RIG_TOOLKIT/rig_thermal.sh" watch "$RL_EV/thermal-${RUN:-run}.log"
}

# --- grading: a brief's grade-<run>.sh prints COND and VERDICT lines; rig_session.py reads only those ---

rl_win() {  # rl_win FILE FROM TO: the lines from marker FROM to marker TO (AutomationMarker: <tag>)
  awk -v a="AutomationMarker: $2" -v b="AutomationMarker: $3" 'index($0,a){on=1} on{print} on&&index($0,b){exit}' "$1" 2>/dev/null
}

rl_cnt() { rl_win "$1" "$2" "$3" | grep -acF -- "$4"; }   # rl_cnt FILE FROM TO STRING: count inside the window

rl_cond() {  # rl_cond ID OP ACTUAL EXPECTED LABEL: OP is eq|ge|le|gt|lt. Prints "COND ID PASS|FAIL LABEL: ACTUAL OP EXPECTED"
  local id=$1 op=$2 got=${3:-} want=$4 label=$5 r=FAIL
  [[ "$got" =~ ^-?[0-9]+$ ]] && [ "$got" "-$op" "$want" ] && r=PASS
  echo "COND $id $r $label: ${got:-none} $op $want"
}

rl_verdict() { echo "VERDICT $1 $2"; }   # rl_verdict PASS|FAIL|INCONCLUSIVE|UNTESTABLE REASON: overrides the COND roll-up

rl_init() {  # called by rig_run.sh: every tracked job stops when the script exits, however it exits
  [ -n "$RL_EV" ] && mkdir -p "$RL_EV"
  [ -n "$RL_PIDS" ] && : > "$RL_PIDS"
  trap 'rl_stop_all' EXIT
}
