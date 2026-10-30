#!/usr/bin/env bash
# Tests for rig_lib.sh, rig_run.sh, rig_requires.py, brief_check.py and rig_session.py.
# Run: bash rig-lib/tests/test_rig_lib.sh. Needs bash, python3, git, procps and util-linux (setsid, flock).
# No device: adb is tests/fake_adb.sh.
set -u
T=$(cd "$(dirname "$0")" && pwd); L=$(dirname "$T")
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
mkdir -p "$W/bin" "$W/adb" "$W/rounds/t-round1"
ln -s "$T/fake_adb.sh" "$W/bin/adb"
export PATH="$W/bin:$PATH" FAKE_ADB_DIR="$W/adb" EV="$W/rounds/t-round1" RUN=T RIG_LOCK="$W/lock"
cat > "$W/devices.sh" <<'EOF'
D_HU=HU1; D_POCO=PH1; D_MOTO=MO1; D_HP=HP1; D_SAM=SA1; D_T230=SA1
EOF
export RIG_DEVICES="$W/devices.sh"
pass=0; fail=0
ok()   { pass=$((pass+1)); echo "ok   $1"; }
bad()  { fail=$((fail+1)); echo "FAIL $1"; }
check() { if eval "$2"; then ok "$1"; else bad "$1"; fi; }
lib() { bash -c "source '$L/rig_lib.sh'; rl_init; $1" 2>&1; }
F='```'

# --- rig_lib.sh ---
printf 'boot\nNativeAA: ACTIVELY LISTENING\n' > "$W/cap.txt"
out=$(lib "rl_wait_line '$W/cap.txt' 'ACTIVELY LISTENING' 2")
check "a line printed before the wait counts (B4)" '[[ "$out" == ok* ]]'
out=$(lib "rl_wait_line '$W/cap.txt' 'ACTIVELY LISTENING' 1 \$(rl_offset '$W/cap.txt')")
check "FROM_BYTE skips older lines" '[[ "$out" == TIMEOUT* ]]'
s=$(date +%s); lib "rl_wait_line '$W/cap.txt' 'never printed' 2" >/dev/null; e=$(( $(date +%s) - s ))
check "the cap is wall-clock seconds, not poll passes (B3)" '[ "$e" -ge 2 ] && [ "$e" -le 4 ]'

out=$(lib "rl_radio PH1 bt on")
check "rl_radio reads back on" '[[ "$out" == *"RADIO PH1 bt=on"* ]]'
out=$(FAKE_ADB_STUCK=1 lib "rl_radio PH1 wifi on; echo rc=\$?")
check "rl_radio reports a radio that did not move (A2)" '[[ "$out" == *RADIO_MISMATCH*"rc=1"* ]]'

echo off > "$W/adb/MO1.bt"
out=$(lib "rl_bonds MO1; echo rc=\$?")
check "rl_bonds refuses with Bluetooth off (B5)" '[[ "$out" == *BONDS_RADIO_OFF*"rc=2"* ]]'
echo on > "$W/adb/PH1.bt"; echo on > "$W/adb/HU1.bt"; echo 'Navegadortz3' > "$W/adb/HU1.name"
echo '  AA:BB [ BR/EDR ] Navegadortz3' > "$W/adb/PH1.bonds"
out=$(lib "rl_bonded PH1 HU1")
check "rl_bonded finds the unit by name" '[[ "$out" == BONDED* ]]'

s=$(date +%s)
out=$(timeout 20 bash -c "source '$L/rig_lib.sh'; rl_init; rl_cap_start c1 HU1; rl_hold_off PH1 wifi 1; rl_release PH1 wifi; rl_radio PH1 wifi on; rl_cap_stop c1; echo done" 2>&1)
e=$(( $(date +%s) - s ))
check "hold, release and capture stop return; no bare wait hang (B2)" '[[ "$out" == *done ]] && [ "$e" -lt 15 ]'
sleep 0.5
check "no capture or hold loop survives" '! pgrep -f "fake_adb.sh -s (HU1|PH1)" >/dev/null && ! pgrep -f "svc wifi disable" >/dev/null'
timeout 20 bash -c "source '$L/rig_lib.sh'; rl_init; rl_cap_start c2 HU1; rl_hold_off PH1 bt 1; exit 0" >/dev/null 2>&1
sleep 0.5
check "the EXIT trap stops tracked jobs a script forgot" '! pgrep -f "fake_adb.sh -s HU1 logcat" >/dev/null'

printf 'x\nAutomationMarker: R9-start\nSSL handshake complete\nSSL handshake complete\nAutomationMarker: R9-end\nSSL handshake complete\n' > "$W/win.txt"
out=$(lib "rl_cond 1 ge \$(rl_cnt '$W/win.txt' R9-start R9-end 'SSL handshake complete') 2 ssl; rl_cond 2 eq '' 0 empty")
check "rl_cnt counts inside the marker window only, and rl_cond grades it" '[[ "$out" == *"COND 1 PASS ssl: 2 ge 2"* && "$out" == *"COND 2 FAIL empty: none eq 0"* ]]'

# --- rig_run.sh ---
cat > "$W/orphan.sh" <<'EOF'
rl_step leaving a child behind
sleep 300 &
echo body ran
EOF
cat > "$W/hang.sh" <<'EOF'
rl_step hang
sleep 300
EOF
out=$("$L/rig_run.sh" R0 30 "$W/orphan.sh" 2>&1); rc=$?
check "rig_run.sh refuses without requirements.ok (A1)" '[ "$rc" -eq 78 ]'
echo ok > "$EV/requirements.ok"
out=$("$L/rig_run.sh" R1 30 "$W/orphan.sh" 2>&1); rc=$?
st=$(cat "$EV"/R1-*.status)
check "rig_run.sh kills an orphan the script left (A4, B6)" '[ "$rc" -eq 0 ] && [[ "$st" == *"ORPHANS killed=1"* ]] && ! pgrep -f "^sleep 300" >/dev/null'
check "the log ends in script exit=" '[[ "$(tail -1 "$EV"/R1-*.log)" == "script exit=0" ]]'
check "PROGRESS.log has the run (A8)" 'grep -q "R1 DONE exit=0" "$W/rounds/PROGRESS.log"'
s=$(date +%s); out=$("$L/rig_run.sh" R2 3 "$W/hang.sh" 2>&1); rc=$?; e=$(( $(date +%s) - s ))
check "rig_run.sh ends a run at its cap (A9)" '[ "$rc" -eq 124 ] && [ "$e" -lt 10 ] && ! pgrep -f "^sleep 300" >/dev/null'
check "the timeout names the last step" 'grep -q "TIMEOUT after 3s, last STEP hang" "$EV"/R2-*.status'
exec 8>"$RIG_LOCK"; flock -n 8
out=$("$L/rig_run.sh" R3 30 "$W/orphan.sh" 2>&1); rc=$?
exec 8>&-
check "a held lock is refused and its holder named" '[ "$rc" -eq 75 ] && [[ "$out" == *"pid "* ]]'
RL_STALL_S=1 RL_HB_S=1 "$L/rig_run.sh" R5 4 "$W/hang.sh" >/dev/null 2>&1
check "a step that stops moving is reported as STALL with what it waits on" 'grep -q "STALL step unchanged.*sleep 300" "$EV"/R5-*.status'
RL_STALL_KILL=1 RL_STALL_S=1 RL_HB_S=1 "$L/rig_run.sh" R6 20 "$W/hang.sh" >/dev/null 2>&1; rc=$?
check "RL_STALL_KILL ends a stalled run with exit 125" '[ "$rc" -eq 125 ]'

# --- rig_requires.py ---
B="$W/brief.md"
cat > "$B" <<EOF
# t, round 1
## R1. One
${F}rig-requires
lib 0123456
unit D-HU role=headunit wifi=on bt=on
unit D-POCO role=phone wifi=on bt=on bond=D-HU aa=D-HU
confirm a playlist
run R1 cap=60 script=r1.sh grade=grade-r1.sh
$F
EOF
check "a good block lints" 'python3 -I "$L/rig_requires.py" lint "$B" >/dev/null'
sed 's/unit D-HU role=headunit wifi=on bt=on/unit D-HU wifi=on bt=off/' "$B" > "$W/b2.md"
out=$(python3 -I "$L/rig_requires.py" lint "$W/b2.md")
check "a bond with Bluetooth off is a lint error (B5)" '[[ "$out" == *"needs bt=on"* ]]'
for f in HU1.wifi HU1.bt PH1.wifi PH1.bt; do echo on > "$W/adb/$f"; done
echo 'POCO X3 NFC' > "$W/adb/PH1.name"; echo '  CC:DD [ BR/EDR ] POCO X3 NFC' > "$W/adb/HU1.bonds"
rm -f "$EV/requirements.ok"
out=$(python3 -I "$L/rig_requires.py" check --ev "$EV" "$B"); rc=$?
check "check fails until the operator says yes (B10)" '[ "$rc" -eq 1 ] && [[ "$out" == *"has not said yes"* ]]'
python3 -I "$L/rig_requires.py" yes --ev "$EV" "$B" "$W/b2.md" >/dev/null
out=$(python3 -I "$L/rig_requires.py" check --ev "$EV" "$B"); rc=$?
check "one yes covers the session; check passes and writes requirements.ok" '[ "$rc" -eq 0 ] && [ -f "$EV/requirements.ok" ]'
echo off > "$W/adb/PH1.bt"
out=$(python3 -I "$L/rig_requires.py" check --ev "$EV" "$B"); rc=$?
check "without --fix a radio the brief needs on fails check (A1)" '[ "$rc" -eq 1 ] && [ ! -f "$EV/requirements.ok" ]'
out=$(python3 -I "$L/rig_requires.py" check --ev "$EV" "$B" --fix); rc=$?
check "--fix switches a phone radio on, reads it back and passes" '[ "$rc" -eq 0 ] && [[ "$out" == *"D-POCO bt switched to on"* ]]'
echo off > "$W/adb/HU1.wifi"
out=$(python3 -I "$L/rig_requires.py" check --ev "$EV" "$B" --fix); rc=$?
check "--fix never switches D-HU WiFi on; it names the operator" '[ "$rc" -eq 1 ] && [[ "$out" == *"OPERATOR turn D-HU wifi on"* ]]'
echo on > "$W/adb/HU1.wifi"
sed 's/role=phone/role=headunit/' "$B" > "$W/b4.md"; python3 -I "$L/rig_requires.py" yes --ev "$EV" "$W/b4.md" >/dev/null
out=$(python3 -I "$L/rig_requires.py" check --ev "$EV" "$W/b4.md" --fix)
check "a role swap is done by check --fix and recorded" '[[ "$out" == *"D-POCO role phone -> headunit"* ]] && grep -q "D-POCO headunit" "$W/rounds/unit-roles.txt"'
sed 's/unit D-POCO.*/unit D-POCO wifi=on bt=off/; /bond/d' "$B" > "$W/b3.md"
out=$(python3 -I "$L/rig_requires.py" order "$W/b3.md" "$B")
check "order names each transition and who does it" '[[ "$out" == *"auto     D-POCO bt"* ]]'
printf '## R1\n%srig-requires\nlib 0123456\nunit D-HU wifi=off\nrun R1 cap=9 script=a.sh grade=b.sh\n%s\n' "$F" "$F" > "$W/hu-off.md"
printf '## R1\n%srig-requires\nlib 0123456\nunit D-HU wifi=on\nrun R1 cap=9 script=a.sh grade=b.sh\n%s\n' "$F" "$F" > "$W/hu-on.md"
out=$(python3 -I "$L/rig_requires.py" order "$W/hu-off.md" "$W/hu-on.md")
check "order puts the brief that needs D-HU WiFi off last, so no operator re-join" '[ "$(echo "$out" | grep -n hu-off | cut -d: -f1)" -gt "$(echo "$out" | grep -n hu-on | cut -d: -f1)" ] && [[ "$out" != *"OPERATOR D-"* ]]'

# --- brief_check.py ---
G="$W/repo"; mkdir -p "$G/rig-lib"; cp "$L"/*.sh "$L"/*.py "$L"/*.conf "$G/rig-lib/"
git -C "$G" init -q; git -C "$G" add -A; git -C "$G" -c user.name=t -c user.email=t@t commit -qm "Rig lib: test"
PIN=$(git -C "$G" rev-parse --short HEAD)
cat > "$G/good-round1-brief.md" <<EOF
# good, round 1
## 5. Helpers
**lib.sh**
${F}bash
HU=\$D_HU; PH=\$D_POCO
$F
## 7. Runs
### R1. A run
**r1.sh**
${F}bash
rl_thermal_gate
rl_cap_start R1-hu "\$HU"
rl_hold_off "\$PH" wifi
rl_wait_line "\$EV/R1-hu.txt" 'SSL handshake complete' 120 || echo no-session
rl_release "\$PH" wifi
rl_radio "\$PH" wifi on
rl_cap_stop R1-hu
$F
**grade-r1.sh**
${F}bash
rl_cond 1 ge "\$(grep -acF 'SSL handshake complete' "\$EV/R1-hu.txt")" 1 'SSL handshake complete'
$F
${F}rig-requires
lib $PIN
unit D-HU wifi=on bt=on
unit D-POCO wifi=on bt=on
run R1 cap=300 script=r1.sh grade=grade-r1.sh
$F
EOF
out=$(python3 -I "$L/brief_check.py" check "$G/good-round1-brief.md" 2>&1); rc=$?
check "a brief built on rig-lib passes lint and smoke and is stamped" '[ "$rc" -eq 0 ] && python3 -I "$L/brief_check.py" stamped "$G/good-round1-brief.md"'
[ "$rc" -eq 0 ] || echo "$out" | sed 's/^/     /'
check "check prints what the round costs the operator" '[[ "$out" == *"cost: 0 operator confirmations, 0 hand steps"* ]]'
cat > "$G/bad-round1-brief.md" <<EOF
# bad, round 1
## 7. Runs
### R1. A run
**lib.sh**
${F}bash
cap_on() { adb -s X logcat > \$EV/c.txt & HUPID=\$!; }
ph_on() { kill \$PHLOOP 2>/dev/null; wait \$PHLOOP 2>/dev/null; PHLOOP=; }
$F
**r1.sh**
${F}bash
cap_on
ph_on
MAC=<MOTO_MAC from P>
$F
${F}rig-requires
lib $PIN
unit D-HU wifi=on bt=on
run R1 cap=300 script=r1.sh grade=grade-r1.sh
$F
EOF
out=$(python3 -I "$L/brief_check.py" lint "$G/bad-round1-brief.md" 2>&1)
check "lint names the redefined helpers" '[[ "$out" == *"defines cap_on()"* && "$out" == *"defines ph_on()"* ]]'
check "lint names the unquoted wait (B2)" '[[ "$out" == *"wait\` without a quoted pid"* ]]'
check "lint names the untracked background job" '[[ "$out" == *"untracked background job"* ]]'
check "lint names the placeholder" '[[ "$out" == *"placeholder <MOTO_MAC from P>"* ]]'
check "lint names a grade that is not a named block" '[[ "$out" == *"grade=grade-r1.sh is not a named block"* ]]'
out=$(python3 -I "$L/brief_check.py" smoke "$G/bad-round1-brief.md" 2>&1)
check "smoke catches the hang the bare wait causes (B2)" '[[ "$out" == *"HANG"* ]]'
check "smoke does not stamp a failed brief" '! python3 -I "$L/brief_check.py" stamped "$G/bad-round1-brief.md"'
sed 's/SSL handshake complete/SSL handshake done/; s/\(lib \)[0-9a-f]*/\1abcdef0/' "$G/good-round1-brief.md" > "$W/edited.md"
check "same-body flags a changed condition" '! python3 -I "$L/brief_check.py" same-body "$G/good-round1-brief.md" "$W/edited.md" >/dev/null'
sed 's/\(lib \)[0-9a-f]*/\1abcdef0/' "$G/good-round1-brief.md" > "$W/bumped.md"
check "same-body accepts a SHA bump" 'python3 -I "$L/brief_check.py" same-body "$G/good-round1-brief.md" "$W/bumped.md" >/dev/null'

# --- rig_session.py: a whole round with no host ---
S="$G/sess-round1-brief.md"
cat > "$S" <<EOF
# sess, round 1
**lib.sh**
${F}bash
HU=\$D_HU
$F
### R1. Passes
**r1.sh**
${F}bash
rl_cap_start R1-hu "\$HU"
rl_wait_line "\$EV/R1-hu.txt" 'fake: line 3' 10
rl_cap_stop R1-hu
$F
**grade-r1.sh**
${F}bash
rl_cond 1 ge "\$(grep -acF 'fake: line' "\$EV/R1-hu.txt")" 1 'capture has lines'
$F
### R2. Hangs
**r2.sh**
${F}bash
rl_step hang on purpose
sleep 300
$F
**grade-r2.sh**
${F}bash
rl_cond 1 eq 1 1 never reached
$F
### R3. Skipped by the stop rule
**r3.sh**
${F}bash
echo r3
$F
### cleanup
**cleanup.sh**
${F}bash
echo cleaned > "\$EV/cleaned.txt"
$F
${F}rig-requires
lib PINSHA
unit D-HU role=headunit wifi=on bt=on
unit D-POCO role=phone bt=on
run R1 cap=30 script=r1.sh grade=grade-r1.sh point=yes
run R2 cap=3 script=r2.sh grade=grade-r2.sh
run R3 cap=30 script=r3.sh grade=grade-r1.sh
run cleanup cap=30 script=cleanup.sh always=yes
stop R2 INCONCLUSIVE skip=all
$F
EOF
sed -i "s/PINSHA/$PIN/" "$S"
for f in HU1.wifi HU1.bt; do echo on > "$W/adb/$f"; done; echo off > "$W/adb/PH1.bt"
SE="$W/rounds/sess-round1"
s=$(date +%s); out=$(timeout 120 python3 -I "$L/rig_session.py" run --ev "$SE" "$S" 2>&1); rc=$?; e=$(( $(date +%s) - s ))
st=$(cat "$SE/session.status" 2>/dev/null)
check "the session runs a round to DONE with no host and no operator" '[ "$rc" -eq 0 ] && [[ "$st" == *"DONE"*"interventions=0"* ]]'
[ "$rc" -eq 0 ] || { echo "$out" | tail -5 | sed 's/^/     /'; echo "$st" | tail -8 | sed 's/^/     /'; }
check "it fixed D-POCO Bluetooth itself before the first run" 'grep -q "D-POCO bt switched to on" "$SE/requirements.txt"'
check "R1 graded PASS from its grade script" '[[ "$st" == *"GRADE R1 PASS"* ]]'
check "a hung run is retried once, then INCONCLUSIVE, with no operator step" '[[ "$st" == *"RETRY R2"* && "$st" == *"GRADE R2 INCONCLUSIVE"* ]]'
check "the stop rule skips R3 and cleanup still runs" '[[ "$st" == *"GRADE R3 NOT_RUN"* ]] && [ -f "$SE/cleaned.txt" ]'
check "summary.md has one row per run" '[ "$(grep -c "^| R[0-9]\|^| cleanup" "$SE/summary.md")" -eq 4 ]'
check "the host sees a few lines per run, not the run" '[ "$(wc -l < "$SE/session.status")" -lt 20 ]'
out=$(python3 -I "$L/rig_session.py" view "$S")
check "view prints the round in one screen" '[[ "$out" == *"run R2 cap=3s"* && "$out" == *"stop after R2 on INCONCLUSIVE"* ]] && [ "$(echo "$out" | wc -l)" -lt 15 ]'

# --- per-run needs: nobond, a mid-round operator step, a leftover group owner ---
N="$G/nb-round1-brief.md"
cat > "$N" <<EOF
# nb, round 1
### C2. Needs the bond
**c2.sh**
${F}bash
echo c2
$F
### B. Needs the bond gone
**b.sh**
${F}bash
echo b
$F
**grade-ok.sh**
${F}bash
rl_cond 1 eq 1 1 ran
$F
${F}rig-requires
lib PINSHA
unit D-HU role=headunit wifi=on bt=on
unit D-POCO role=phone wifi=on bt=on
unit D-POCO@C2 bond=D-HU
unit D-POCO@B nobond=D-HU
run C2 cap=30 script=c2.sh grade=grade-ok.sh
run B cap=30 script=b.sh grade=grade-ok.sh point=yes
$F
EOF
sed -i "s/PINSHA/$PIN/" "$N"
check "scoped unit lines and nobond lint" 'python3 -I "$L/rig_requires.py" lint "$N" >/dev/null'
sed 's/unit D-POCO@B nobond=D-HU/unit D-POCO@X nobond=D-HU/' "$N" > "$W/nb2.md"
out=$(python3 -I "$L/rig_requires.py" lint "$W/nb2.md")
check "a scoped line that names no run is a lint error" '[[ "$out" == *"X has no run line"* ]]'
out=$(python3 -I "$L/rig_requires.py" order "$N")
check "order names the mid-round forget before run B" '[[ "$out" == *"OPERATOR D-POCO forget D-HU  (before run B)"* ]]'
for f in HU1.wifi HU1.bt PH1.wifi PH1.bt; do echo on > "$W/adb/$f"; done
echo MT50 > "$W/adb/HU1.name"; echo 'POCO X3 NFC' > "$W/adb/PH1.name"
echo '  AA:BB [ BR/EDR ] MT50' > "$W/adb/PH1.bonds"; echo '  CC:DD [ BR/EDR ] POCO X3 NFC' > "$W/adb/HU1.bonds"
NE="$W/rounds/nb-round1"; mkdir -p "$NE"
out=$(python3 -I "$L/rig_requires.py" check --ev "$NE" "$N" --run B); rc=$?
check "check --run B fails on the bond and names the operator's forget" '[ "$rc" -eq 1 ] && [[ "$out" == *"OPERATOR on D-POCO, forget D-HU (MT50)"* ]]'
out=$(python3 -I "$L/rig_requires.py" check --ev "$NE" "$N" --run C2); rc=$?
check "check --run C2 passes with the bond on both sides" '[ "$rc" -eq 0 ]'
echo true > "$W/adb/PH1.go"
out=$(python3 -I "$L/rig_requires.py" check --ev "$NE" "$N" --run C2 --fix); rc=$?
check "a phone left as a group owner is cleared by a WiFi cycle" '[ "$rc" -eq 0 ] && [[ "$out" == *"isGroupOwner=false"* ]] && [ ! -f "$W/adb/PH1.go" ]'
( sleep 3; : > "$W/adb/PH1.bonds" ) &
out=$(RL_OPERATOR_POLL_S=1 timeout 90 python3 -I "$L/rig_session.py" run --ev "$NE" "$N" 2>&1); rc=$?
st=$(cat "$NE/session.status" 2>/dev/null)
check "the session waits for the operator's forget before B, then goes on to DONE" '[ "$rc" -eq 0 ] && [[ "$st" == *"OPERATOR B now: on D-POCO, forget D-HU"* && "$st" == *"READ BACK B"* && "$st" == *"GRADE B PASS"* && "$st" == *"interventions=1"* ]]'
[ "$rc" -eq 0 ] || echo "$st" | tail -8 | sed 's/^/     /'
check "the session tells the operator which units to leave alone" '[[ "$st" == *"hands off D-HU, D-POCO until DONE"* ]]'
cat > "$W/lint3.md" <<EOF
# l3
### R1
**r1.sh**
${F}bash
stage() { echo "\$1"; }
stage B
echo "\${1:?usage: r1.sh STAGE}"
$F
${F}bash
adb -s \$HU shell am force-stop x
$F
${F}rig-requires
lib $PIN
run R1 cap=30 script=r1.sh grade=r1.sh
$F
EOF
out=$(python3 -I "$L/brief_check.py" lint "$W/lint3.md" --repo "$G")
check "lint: a run script that needs an argument is an error (D11)" '[[ "$out" == *"r1.sh (line 3):7: reads a positional"* && "$out" != *":5: reads"* ]]'
check "lint: an unnamed block that drives the rig is an error (D12)" '[[ "$out" == *"line 9: an unnamed bash block"* ]]'
[[ "$out" == *"reads a positional"* ]] || echo "$out" | sed 's/^/     /'

echo '  AA:BB [ BR/EDR ] MT50' > "$W/adb/PH1.bonds"
out=$(python3 -I "$L/rig_requires.py" order --live "$N")
check "order --live counts a bond the rig already holds as done" '[[ "$out" != *"pair D-HU"* && "$out" == *"forget D-HU  (before run B)"* ]]'
out=$(lib "export RL_STATUS='$W/sl.status'; rl_sleep 1; cat '$W/sl.status'")
check "rl_sleep marks a wait with its own cap, so a long sleep is not a stall" '[[ "$out" == *"WAIT cap=1s SLEEP"* ]]'

A="$G/ab-round1-brief.md"
sed -e 's/^unit D-POCO@B nobond=D-HU$/unit D-POCO@B nobond=D-HU\nunit D-MOTO bt=off/' "$N" > "$A"
AE="$W/rounds/ab-round1"; mkdir -p "$AE"; : > "$W/adb/PH1.bonds"; touch "$W/adb/MO1.absent"
out=$(python3 -I "$L/rig_requires.py" check --ev "$AE" "$A" --run B); rc=$?
check "an absent unit that needs only radios off is a note, not a failure" '[ "$rc" -eq 0 ] && [[ "$out" == *"D-MOTO is not on adb: skipped"* ]]'
rm -f "$W/adb/MO1.absent"
for k in wifi bt name bonds; do cp "$W/adb/PH1.$k" "$W/adb/10.0.0.2:5555.$k" 2>/dev/null; done; touch "$W/adb/PH1.absent"
out=$(python3 -I "$L/rig_requires.py" check --ev "$AE" "$A" --run B); rc=$?
check "a phone off its cable fails without a recorded wireless serial" '[ "$rc" -eq 1 ] && [[ "$out" == *"OPERATOR reconnect D-POCO"* ]]'
echo "D-POCO 10.0.0.2:5555" >> "$W/rounds/unit-serials.txt"
out=$(python3 -I "$L/rig_requires.py" check --ev "$AE" "$A" --run B); rc=$?
check "a phone on wireless adb is read at the serial a brief recorded" '[ "$rc" -eq 0 ] && [[ "$out" == *"D-POCO read over wireless adb at 10.0.0.2:5555"* ]]'
rm -f "$W/adb/PH1.absent" "$W/rounds/unit-serials.txt"

# --- no test file stays on a device, and nothing else is deleted ---
DE="$W/rounds/df-round1"; mkdir -p "$DE" "$W/adb/HU1.fs/data/local/tmp/old" "$W/adb/HU1.fs/sdcard/Download"
DB="$W/df-brief.md"
cat > "$DB" <<EOF
**r1.sh**
${F}bash
adb -s \$HU push r1.sh /data/local/tmp/r1.sh
adb -s \$HU shell screencap -p /sdcard/ohu-shots/\$RUN.png
adb -s \$HU push song.mp3 /sdcard/Music/ohu-long.mp3
$F
EOF
python3 -I "$L/rig_requires.py" devfiles snapshot --ev "$DE" "$DB" >/dev/null
echo x > "$W/adb/HU1.fs/data/local/tmp/r1.sh"; mkdir -p "$W/adb/HU1.fs/sdcard/ohu-shots"; echo p > "$W/adb/HU1.fs/sdcard/ohu-shots/R1.png"
echo song > "$W/adb/HU1.fs/sdcard/Download/user-song.mp3"
mkdir -p "$W/adb/HU1.fs/sdcard/Music"; echo m > "$W/adb/HU1.fs/sdcard/Music/ohu-long.mp3"
out=$(python3 -I "$L/rig_requires.py" devfiles sweep --ev "$DE" "$DB"); rc=$?
check "sweep copies brief-named new files to the PC, then deletes them from the unit" '[ -f "$DE/device-files/D-HU/r1.sh" ] && [ -f "$DE/device-files/D-HU/ohu-shots/R1.png" ] && [ ! -e "$W/adb/HU1.fs/data/local/tmp/r1.sh" ] && [ ! -e "$W/adb/HU1.fs/sdcard/ohu-shots" ]'
check "a new file the brief never named is left in place and listed" '[ "$rc" -eq 1 ] && [ -f "$W/adb/HU1.fs/sdcard/Download/user-song.mp3" ] && [[ "$out" == *"user-song.mp3 (not named by the brief"* ]]'
check "a brief path outside the fixed folders is watched and swept too" '[ ! -e "$W/adb/HU1.fs/sdcard/Music/ohu-long.mp3" ] && [ -f "$DE/device-files/D-HU/ohu-long.mp3" ]'
check "files that were there before the session stay" '[ -d "$W/adb/HU1.fs/data/local/tmp/old" ]'
DE2="$W/rounds/df-round2"; mkdir -p "$DE2"; mv "$W/adb/HU1.fs/data/local/tmp" "$W/adb/HU1.fs/data/local/tmp.hidden"
python3 -I "$L/rig_requires.py" devfiles snapshot --ev "$DE2" "$DB" >/dev/null
mv "$W/adb/HU1.fs/data/local/tmp.hidden" "$W/adb/HU1.fs/data/local/tmp"; echo x > "$W/adb/HU1.fs/data/local/tmp/r1.sh"
out=$(python3 -I "$L/rig_requires.py" devfiles sweep --ev "$DE2" "$DB")
check "a folder missing from the snapshot is compared, and only the named file goes" '[ ! -e "$W/adb/HU1.fs/data/local/tmp/r1.sh" ] && [ -d "$W/adb/HU1.fs/data/local/tmp/old" ]'
rm -rf "$DE/device-files-before.json"
out=$(python3 -I "$L/rig_requires.py" devfiles sweep --ev "$DE" "$DB"); rc=$?
check "with no snapshot, sweep deletes nothing" '[ "$rc" -eq 1 ] && [[ "$out" == *"no snapshot"* ]] && [ -f "$W/adb/HU1.fs/sdcard/Download/user-song.mp3" ]'
check "the session summary reports the sweep" 'grep -q "^Device files:" "$NE/summary.md"'

# --- rig_resolve.py ---
git -C "$G" checkout -q -b transfer/t
cp "$G/good-round1-brief.md" "$G/aa-round1-brief.md"; cp "$G/good-round1-brief.md" "$G/bb-round2-brief.md"
git -C "$G" add -A; git -C "$G" -c user.name=t -c user.email=t@t commit -qm "Briefs: queue two"; Q=$(git -C "$G" rev-parse --short HEAD)
echo x >> "$G/aa-round1-brief.md"; git -C "$G" -c user.name=t -c user.email=t@t commit -qam "Briefs: revise aa"; M=$(git -C "$G" rev-parse --short HEAD)
echo r > "$G/bb-round2-results.md"; git -C "$G" add -A; git -C "$G" -c user.name=t -c user.email=t@t commit -qm "bb: results"
out=$(cd "$G" && python3 -I "$L/rig_resolve.py" "$Q" --branch transfer/t)
check "a commit that queues two briefs resolves to the one without results" '[[ "$out" == *"RUN aa 1"* && "$out" != *"RUN bb"* && "$out" == *"bb round 2 already has a results file"* ]]'
out=$(cd "$G" && python3 -I "$L/rig_resolve.py" "$M" --branch transfer/t)
check "a commit that only modifies a brief still resolves" '[[ "$out" == *"RUN aa 1"* ]]'
git -C "$G" checkout -q --orphan rerooted; git -C "$G" -c user.name=t -c user.email=t@t commit -qm "Transfer channel: re-root"
out=$(cd "$G" && python3 -I "$L/rig_resolve.py" "$Q" --branch rerooted); rc=$?
check "a SHA a re-root removed resolves by file name, with a note" '[ "$rc" -eq 0 ] && [[ "$out" == *"RUN aa 1"* && "$out" == *"not an ancestor"* ]]'

echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
