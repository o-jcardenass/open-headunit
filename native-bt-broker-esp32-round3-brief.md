# native-bt-broker-esp32, round 3 brief: K with the D-HU bond in place, then S, then the closing

Published name: `native-bt-broker-esp32-round3-brief.md`. Results go in `native-bt-broker-esp32-round3-results.md`.
Evidence: add `native-bt-broker-esp32-round3-captures.zip` to the release `rig-evidence-native-bt-broker-esp32`.

**The point of the round is K.** Round 2 graded C1, P0, P, B and C2 PASS (B 5 of 5 once D-POCO had forgotten D-HU).
K was INCONCLUSIVE: the operator forgot D-HU on D-POCO during K, so K could not show a fallback to the stock route.
S was not run, and neither was the closing. This round runs K again with the D-POCO to D-HU bond in place, then S,
then the closing as a cleanup run that always runs.

Units: D-HU (head unit), D-POCO (phone), board E-1 on the host's serial port, D-SAM (S only). D-MOTO radios off.
Time: K 6 min, S 4 min, cleanup 4 min, plus thermal waits. **About 15 min.**

```rig-requires
lib 19f862828
unit D-HU role=headunit wifi=on bt=on
unit D-POCO role=phone wifi=on bt=on aa=D-HU battery=30
unit D-SAM role=headunit
unit D-MOTO wifi=off bt=off
unit D-POCO@K bond=D-HU
candidate C f1bed4ee
hardware board E-1 (ESP32, MAC 78:1c:3c:2b:ac:ec) is on the host's serial port with release firmware v1.0.0; board E-2 is not touched
confirm round 2's folder rig-data/rounds/native-bt-broker-esp32-round2 is still on the rig, with ids.env, venv/ and settings-backup-hu.xml
run K cap=600 script=k.sh grade=grade-k.sh point=yes
run S cap=420 script=s.sh grade=grade-s.sh
run cleanup cap=420 script=cleanup.sh always=yes
stop K UNTESTABLE skip=all
```

`bond=D-HU` is scoped to K and read on both sides. Round 2 left D-POCO without it, so the session asks the operator
to pair D-POCO and D-HU before K and waits for the read-back. Nothing after K needs the bond, and it stays: a D-HU
stock run needs it.

| Run | What it proves | Min |
|---|---|---|
| **K** | **the point: switch on, board held in reset, D-POCO bonded to D-HU: does Native mode fall back to the stock route, or say why it waits?** | 6 |
| S | D-SAM (API 19): the relay screen leaves at once with no crash (contributor fix 3, the API 23 guard) | 4 |
| cleanup | D-HU relay file gone and settings back; D-POCO forgets the board; D-SAM APK and settings back | 4 |

## 1. Build and baseline

| | |
|---|---|
| Candidate | `fork/test/pr-1187-relay` `f1bed4ee42528a21a2ce03caebac4f214afdaf87`, tree `c90b77d66271c3cb0f41e6d615a8db609f415f75` |
| What it is | `main` `a4f80ff1b6aa6726c17e7385192cf23abe0d0e9f` plus PR #1187 head `adb0ae6b` as one merge commit |
| Built APK | round 2: md5 `e4915024f9b13ec04c694872b16a66c5`; build again into `$EV/apk/C.apk` or copy round 2's `C.apk` |
| Firmware | `kendrickkim/aa-native-bt-broker-for-openheadunit` release `v1.0.0`, `firmware.bin` sha256 `2a6059dc...`, already on E-1. No flash this round |

No history was rewritten. The dev host ran the JVM suite; no rig unit tests.

## 2. What this is and why

PR #1187 moves the Bluetooth half of Native AA onto an ESP32 board. With the relay switch on (`enabled` in
`NativeBTBroker.xml`), the head unit opens no RFCOMM listener of its own and sends no wake poke; the board does both.
The change request asks what the user sees when the board is gone. K answers it: the board is held in reset, the
switch is on, and D-POCO is bonded to D-HU, so D-POCO's Android Auto dials D-HU's own address. If the app falls back,
its listeners open (`ACTIVELY LISTENING on Android Auto UUID`) and a session forms. If it waits, the `Broker:` lines
must say why (`Broker: link lost, retrying for `, `Broker: still down after `).

S checks fix 3: below API 23 the relay screen leaves at once (`BrokerSettingsFragment.onViewCreated` calls `leave()`
when `NativeBTBrokerSession.isSupported()` is false), where round 1 crashed at Scan with `NoSuchMethodError`.

## 3. What is different about this round

- **Round 2's defects are fixed here.** K is a named block. No script takes an argument. The phone `Critical error`
  count leaves out the 2 s after the Exit inside the grade (`crit_count.py`). There is no `Pair phone` tap, and the
  one system dialog button the round might meet is matched in any case (`PAIR`).
- **No P (re-pair) this round.** K uses the board that round 2 saved in `NativeBTBroker.xml` (`78:1C:3C:2B:AC:EE`),
  and holds it in reset, so K needs no scan and no new bond. S needs no board. So nothing needs a new pairing.
- **The board stops advertising after a force-stop** until its serial port is opened with a reset. K's own release
  step opens the port with `--reset` and waits for `advertising OpenHU-BT-Relay-Setup`, so the board ends the round
  advertising. Any later run that needs a scan resets it first in the same way.
- **K holds EN low** by opening the serial port with RTS high (`serial_cap.py --hold`). The same process reads the
  port, so a boot line inside K's window proves the hold failed.
- **Markers** are `adb shell log -t AutomationMarker <label>` on each unit, plus the same line stamped into
  `$EV/serial-E1.txt`. No app process is needed.
- **Known state first.** K installs C on D-HU and writes its keys and its relay file; S backs D-SAM up and installs C.
  `rl_thermal_gate` calls `$RIG_TOOLKIT/rig_thermal.sh`. Settings go through `$RIG_TOOLKIT/set_hu_settings_host.py`
  (D-HU, as `python3`) and `$RIG_TOOLKIT/set_prefs_runas_host.py` (D-SAM).
- **Rig state round 2 left:** D-HU runs C with `NativeBTBroker.xml` enabled for the board; D-POCO is bonded to
  `OpenHU-BT-Relay` and not to D-HU; E-1 is on the release firmware and advertising; D-MOTO radios are off.
- Read `rig-quirks/topics/tooling.md`, `topics/bt.md`, `topics/gearhead.md`, `units/D-HU.md`, `units/D-POCO.md`,
  `units/D-SAM.md`, `units/D-SAM-and-D-HP.md`, and `native-bt-broker-esp32-hardware-findings.md`.

## 4. Settings keys

D-HU, written by `keys_hu` with the app stopped (round 2's keys, `TGT` = D-POCO's MAC from round 2's `ids.env`):
`wifi-connection-mode` 3, `native-ap-transport` 0, `log-level` 2, `onboarding-version` 2,
`native-driver-selection-mode` 0, `native-preferred-device-mac` and `last-connected-native-mac` = TGT,
`native-poke-bt-macs` = {TGT}, `native-poke-all-paired` false, `auto-start-bt-macs` = {TGT},
`native-aa-wake-damage-verdict` 0, `native-aa-wake-armings-without-session` 0, delete `video-profile-starvation-cap`.
`NativeBTBroker.xml` in K: `enabled` true, `address` `78:1C:3C:2B:AC:EE`, `name` `OpenHU-BT-Relay-Setup`.

D-SAM (S): `wifi-connection-mode` 3, `onboarding-version` 2, `log-level` 2, `native-driver-selection-mode` 0,
`native-poke-all-paired` false, `native-poke-bt-macs` cleared.

## 5. The deciding lines

App lines checked with `git grep -F` under `app/src/main` at `f1bed4ee`. Head-unit lines are in `$EV/K-hu.txt`,
phone lines in `$EV/K-ph.txt`, board lines in `$EV/serial-E1.txt` (host clock), D-SAM lines in `$EV/S-sam.txt`.
A firmware line is tagged `# system` in the scripts, because it is not in `app/src`.

| Line | Where | Meaning |
|---|---|---|
| `NativeAA: Bluetooth relay transport is on` | D-HU | the relay route was chosen |
| `ACTIVELY LISTENING on Android Auto UUID` | D-HU | the stock listeners opened (a fallback) |
| `SSL handshake complete` | D-HU | a session formed |
| `Broker: link lost, retrying for `, `Broker: still down after ` | D-HU | the app says why it waits |
| `Broker: intentional AA wake (HFP) to ` | D-HU | the app asked the board for a wake |
| `MATCH! Starting AapService` | D-HU | a self wake-up |
| `Creating rfcomm socket for device: ` | D-POCO | the phone dialled; the MAC follows |
| `Critical error` | D-POCO | counted with the 2 s after the Exit left out |
| `Classic name OpenHU-BT-Relay, BLE name` | board | a boot: inside K it means the hold failed |
| `advertising OpenHU-BT-Relay-Setup` | board | BLE is up again after the release |
| `Process: com.andrerinas.headunitrevived` | D-SAM | system line of a `FATAL EXCEPTION` block |

## 6. Runs

### K. Switch on, board held in reset, D-POCO bonded to D-HU (the point)

**k.sh**
```bash
rm -f "$EV/K-thermal.txt" "$EV/K-prereq.txt"
if [ -z "$E1" ] || [ ! -x "$VENV/bin/python" ]; then
  echo "no E1 path ($E1) or no venv at $VENV: round 2's ids.env or venv is missing" > "$EV/K-prereq.txt"; exit 3
fi
rl_thermal_gate || { echo HOST_HOT > "$EV/K-thermal.txt"; exit 2; }
rl_step "D-HU: install C, keys, relay file"
adb -s "$HU" shell am force-stop "$PKG"
install_apk "$HU" "$APK" K
send_to "$HU" ACTION_QUERY_STATE > "$EV/K-query.txt" 2>&1
adb -s "$HU" shell am force-stop "$PKG"
adb -s "$HU" shell stat -c '%U:%G' "$SP" > "$EV/K-sp-owner.txt"
keys_hu > "$EV/K-keys.txt" 2>&1
broker_write true "$BROKER_LE" OpenHU-BT-Relay-Setup > "$EV/K-broker.txt"
rl_step "hold board E-1 in reset"
rl_bg ser "$VENV/bin/python" -I -u "$SCR/serial_cap.py" "$E1" "$SER" --hold
sleep 2
adb -s "$PH" shell input keyevent KEYCODE_HOME
rl_cap_start K-hu "$HU"; rl_cap_start K-ph "$PH"
mark K-go; from=$(rl_offset "$EV/K-hu.txt")
adb -s "$HU" shell am start -n "$MAIN" >/dev/null
if rl_wait_line "$EV/K-hu.txt" 'SSL handshake complete' 150 "$from"; then mark K-ssl; rl_sleep 30; fi
mark K-exit
send_to "$HU" ACTION_DISCONNECT > "$EV/K-disc.txt" 2>&1
rl_sleep 15
mark K-post
rl_cap_stop K-hu; rl_cap_stop K-ph
adb -s "$HU" shell am force-stop "$PKG"
rl_win "$EV/K-hu.txt" K-go K-post | grep -aF 'Broker:' > "$EV/K-broker-lines.txt"
rl_step "release the board, reset it, wait for BLE"
rl_stop ser
from=$(rl_offset "$SER")
rl_bg ser "$VENV/bin/python" -I -u "$SCR/serial_cap.py" "$E1" "$SER" --reset
rl_wait_line "$SER" 'advertising OpenHU-BT-Relay-Setup' 25 "$from" > "$EV/K-back.txt"   # system
```

**grade-k.sh**
```bash
HUC=$EV/K-hu.txt; PHC=$EV/K-ph.txt
[ -s "$EV/K-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
[ -s "$EV/K-prereq.txt" ] && { rl_verdict UNTESTABLE "$(cat "$EV/K-prereq.txt")"; exit 0; }
relay=$(rl_cnt "$HUC" K-go K-post 'NativeAA: Bluetooth relay transport is on')
boots=$(rl_cnt "$SER" K-go K-post 'Classic name OpenHU-BT-Relay, BLE name')   # system
held=$(grep -acF "SERIAL_CAP open $E1 hold" "$SER" 2>/dev/null)   # not-literal
dials=$(rl_win "$PHC" K-go K-post | grep -aciF "Creating rfcomm socket for device: $HU_MAC")   # phone
ssl=$(rl_cnt "$HUC" K-go K-post 'SSL handshake complete')
listening=$(rl_cnt "$HUC" K-go K-post 'ACTIVELY LISTENING on Android Auto UUID')
lost=$(rl_cnt "$HUC" K-go K-post 'Broker: link lost, retrying for ')
down=$(rl_cnt "$HUC" K-go K-post 'Broker: still down after ')
fallback=0; [ "${ssl:-0}" -ge 1 ] && [ "${listening:-0}" -ge 1 ] && fallback=1
explained=$(( ${lost:-0} + ${down:-0} ))
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/K-identity.txt")" 1 'C on D-HU (md5)'   # not-literal
rl_cond 2 ge "$(grep -c "$C_SHA" "$EV/K-query.txt")" 1 "commit $C_SHA"
rl_cond 3 ge "$(grep -c 'enabled=true' "$EV/K-broker.txt")" 1 'relay file enabled read back'   # not-literal
rl_cond 4 ge "$relay" 1 'the relay route was chosen'
rl_cond 5 eq "$boots" 0 'no board boot inside K (the hold kept it in reset)'
rl_cond 6 ge "$dials" 1 'phone: D-POCO dialled D-HU (the bond is in force)'
rl_cond 7 ge "$fallback" 0 'answer: fell back to the stock route (1 = SSL and listeners)'
rl_cond 8 ge "$explained" 0 'answer: Broker link-lost and still-down lines'
rl_cond 9 ge "$listening" 0 'report: ACTIVELY LISTENING'
rl_cond 10 ge "$ssl" 0 'report: SSL handshake complete'
rl_cond 11 ge "$(rl_cnt "$HUC" K-go K-post 'Broker: intentional AA wake (HFP) to ')" 0 'report: intentional wakes'
rl_cond 12 ge "$(rl_cnt "$HUC" K-go K-post 'MATCH! Starting AapService')" 0 'report: self wake-ups'
rl_cond 13 ge "$(python3 -I "$SCR/crit_count.py" "$PHC" K-go K-post 'Critical error' K-exit)" 0 'report: phone Critical error, 2 s after the Exit left out'   # phone
rl_cond 14 ge "$(grep -c '^ok' "$EV/K-back.txt")" 0 'report: board advertising again after the reset (1 = yes)'   # not-literal
if [ "${held:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "the hold never opened the serial port"
elif [ "${boots:-0}" -gt 0 ]; then rl_verdict INCONCLUSIVE "the board booted during K: the hold did not keep it in reset"
elif [ "${relay:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "the relay route was not chosen, so K did not ask its question"
elif [ "${dials:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "D-POCO never dialled D-HU, so the bond did not take effect"
fi
```

- **K is a report run.** Its verdict says whether K asked its question, not whether the PR is right. The answer is
  conditions 7 and 8: `fallback` 1 means Native mode fell back to the stock route; `explained` 1 or more means the app
  said why it waits. Both 0 means the user sees nothing: that goes into the change request.
- **PASS** when conditions 1 to 6 hold (C installed, the relay file on, the relay route chosen, the board held, D-POCO
  dialling D-HU). Conditions 7 to 14 are numbers for the report and always pass.
- **INCONCLUSIVE** when the hold failed or never opened the port, the relay route was not chosen, or D-POCO never
  dialled D-HU. **UNTESTABLE** on host thermal or missing round 2 files; the stop rule then skips S.
- **Report:** every line in `$EV/K-broker-lines.txt` with its timestamp, and the two answer numbers.

### S. D-SAM (API 19): the relay screen with the API 23 guard

**s.sh**
```bash
MARK_ON=$SAM
rm -f "$EV/S-thermal.txt" "$EV/S-nodest.txt"
rl_thermal_gate || { echo HOST_HOT > "$EV/S-thermal.txt"; exit 2; }
if [ -z "$DEST" ]; then
  A2=$(find "$HOME/Android" "$HOME/.android" /opt -name aapt2 -type f -perm -u+x 2>/dev/null | sort | tail -1)
  H=""; [ -n "$A2" ] && H=$("$A2" dump resources "$APK" 2>/dev/null | grep -a -m1 -E 'resource 0x[0-9a-f]+ id/brokerSettingsFragment' | grep -o '0x[0-9a-f]*')   # not-literal
  [ -n "$H" ] && DEST=$((H))
fi
[ -n "$DEST" ] || { echo "no nav id for brokerSettingsFragment" > "$EV/S-nodest.txt"; exit 3; }
rl_step "D-SAM: back up, install C, keys"
mkdir -p "$EV/apk"
adb -s "$SAM" shell am force-stop "$PKG"
[ -s "$EV/settings-backup-sam.xml" ] || adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-backup-sam.xml"
if [ ! -s "$EV/apk/prior-sam.apk" ]; then
  dev=$(adb -s "$SAM" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -1)
  [ -n "$dev" ] && adb -s "$SAM" pull "$dev" "$EV/apk/prior-sam.apk" >/dev/null 2>&1
fi
install_apk "$SAM" "$APK" S
"$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" scalar:int:wifi-connection-mode:3 scalar:int:onboarding-version:2 scalar:int:log-level:2 scalar:int:native-driver-selection-mode:0 scalar:boolean:native-poke-all-paired:false setclear:native-poke-bt-macs > "$EV/S-keys.txt" 2>&1
adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/S-settings.xml"
send_to "$SAM" ACTION_QUERY_STATE > "$EV/S-query.txt" 2>&1
adb -s "$SAM" shell pm list features | grep -a bluetooth > "$EV/S-features.txt"
rl_step "D-SAM: open the relay screen"
adb -s "$SAM" shell input keyevent 224; adb -s "$SAM" shell input keyevent 82
rl_cap_start S-sam "$SAM"
mark S-go
adb -s "$SAM" shell run-as "$PKG" am start --user 0 -n "$SETT" --ei extra_destination "$DEST" > "$EV/S-start.txt" 2>&1
rl_sleep 8
adb -s "$SAM" shell dumpsys activity activities | grep -a -m2 -E 'mResumedActivity|mFocusedActivity' > "$EV/S-activity.txt"
rl_sleep 7
mark S-end
rl_cap_stop S-sam
adb -s "$SAM" shell am force-stop "$PKG"
rl_win "$EV/S-sam.txt" S-go S-end | grep -a -A20 'FATAL EXCEPTION' | head -20 > "$EV/S-fatal.txt"   # system
```

**grade-s.sh**
```bash
[ -s "$EV/S-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
[ -s "$EV/S-nodest.txt" ] && { rl_verdict UNTESTABLE "no nav id for brokerSettingsFragment (round 2's DEST, aapt2)"; exit 0; }
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/S-identity.txt")" 1 'C on D-SAM (md5)'   # not-literal
rl_cond 2 ge "$(grep -c "$C_SHA" "$EV/S-query.txt")" 1 "commit $C_SHA"
rl_cond 3 ge "$(grep -ac 'name="wifi-connection-mode" value="3"' "$EV/S-settings.xml")" 1 'wifi-connection-mode 3 read back'   # not-literal
rl_cond 4 eq "$(rl_cnt "$EV/S-sam.txt" S-go S-end 'Process: com.andrerinas.headunitrevived')" 0 'no FATAL EXCEPTION for com.andrerinas in S-go..S-end'   # system
rl_cond 5 ge "$(grep -ac 'SettingsActivity' "$EV/S-activity.txt")" 1 'the resumed activity is SettingsActivity'
if [ "$(grep -aci 'error' "$EV/S-start.txt")" -gt 0 ]; then rl_verdict INCONCLUSIVE "the deep link did not start (S-start.txt)"; fi
```

- **PASS (fix 3)** when all five hold: no crash within 15 s of the screen start, and the resumed activity is
  `SettingsActivity` (the relay screen left at once to the settings list, which is the guard).
- **FAIL** on a `FATAL EXCEPTION`: quote `$EV/S-fatal.txt`. **INCONCLUSIVE** when `run-as ... am start` printed an
  error, so the screen never opened.

### cleanup. The closing (always runs)

**cleanup.sh**
```bash
fail=0
rl_step "D-HU: exit, remove the relay file, restore settings"
if [ -n "$(adb -s "$HU" shell pidof "$PKG" | tr -d '\r')" ]; then
  adb -s "$HU" shell am start -a android.intent.action.VIEW -d headunit://exit >/dev/null; sleep 5
fi
adb -s "$HU" shell am force-stop "$PKG"
adb -s "$HU" shell rm -f "$SP/NativeBTBroker.xml"
if [ -s "$R2/settings-backup-hu.xml" ]; then
  owner=$(adb -s "$HU" shell stat -c %U "/data/data/$PKG" | tr -d '\r')
  adb -s "$HU" push "$R2/settings-backup-hu.xml" /data/local/tmp/settings-restore.xml >/dev/null
  adb -s "$HU" shell "cp /data/local/tmp/settings-restore.xml $SP/settings.xml && chown $owner:$owner $SP/settings.xml && chmod 660 $SP/settings.xml"
  python3 "$RIG_TOOLKIT/set_hu_settings_host.py" "$HU" del:night-mode del:car-signal-invert del:fake_speed > "$EV/cleanup-hu-keys.txt" 2>&1
  adb -s "$HU" shell cat "$SP/settings.xml" > "$EV/cleanup-hu-settings.xml"
else
  echo "no round 2 backup: D-HU keeps this round's keys" | tee "$EV/cleanup-hu.txt"; fail=1
fi
rl_step "D-POCO: forget OpenHU-BT-Relay"
forget_board
rl_step "D-SAM: the APK and settings S found"
if [ -s "$EV/apk/prior-sam.apk" ]; then
  adb -s "$SAM" shell am force-stop "$PKG"
  adb -s "$SAM" install -r -d "$EV/apk/prior-sam.apk" > "$EV/cleanup-sam-install.txt" 2>&1 || fail=1
fi
if [ -s "$EV/settings-backup-sam.xml" ]; then
  adb -s "$SAM" push "$EV/settings-backup-sam.xml" /data/local/tmp/settings-restore.xml >/dev/null
  adb -s "$SAM" shell run-as "$PKG" cp /data/local/tmp/settings-restore.xml shared_prefs/settings.xml
  adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml | tr -d '\r' | grep -v '^$' > "$EV/cleanup-sam-settings.xml"
  tr -d '\r' < "$EV/settings-backup-sam.xml" | grep -v '^$' | diff - "$EV/cleanup-sam-settings.xml" > "$EV/cleanup-sam-diff.txt" || fail=1
fi
cat "$EV/cleanup-board.txt"
exit "$fail"
```

- D-HU keeps C, with no relay file, so it runs the stock route. Its settings go back to round 2's backup, less the
  three keys `pr-train-20261029` round 1 left in it (`night-mode`, `car-signal-invert`, `fake_speed`).
- D-HU keeps its LE bond to `OpenHU-BT-Relay-Setup`, and D-POCO keeps its bond to D-HU. List both in the results.
- E-1 keeps the release firmware and ends the round advertising (K's release step). The host's
  `rig_requires.py baseline` at `approve` puts every radio back.

## 7. Scripts

**lib.sh**
```bash
# Serials, paths and verbs for native-bt-broker-esp32 round 3. Sourced before every script and grade.
source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh
RIG_DATA=${RIG_DATA:-/home/oscar/Coding/StudioProjects/rig-data}
PKG=com.andrerinas.headunitrevived
HU=$D_HU; PH=$D_POCO; SAM=$D_SAM
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SETT=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
SP=/data/data/$PKG/shared_prefs
SCR=$EV/scripts; SER=$EV/serial-E1.txt; APK=$EV/apk/C.apk; C_SHA=f1bed4ee4
R2=$RIG_DATA/rounds/native-bt-broker-esp32-round2; VENV=$R2/venv
E1=""; POCO=""; HU_MAC=""; BROKER_LE=""; DEST=""
[ -f "$R2/ids.env" ] && . "$R2/ids.env"
POCO=${POCO:-DC:B7:2E:5E:4E:59}; HU_MAC=${HU_MAC:-11:46:03:10:33:59}; BROKER_LE=${BROKER_LE:-78:1C:3C:2B:AC:EE}
MARK_ON="$HU $PH"

send_to() { local s=$1 a=$2; shift 2; adb -s "$s" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
mark() {   # the same marker on every unit in MARK_ON and in the serial capture; no app process needed
  local s
  for s in $MARK_ON; do adb -s "$s" shell log -t AutomationMarker "$1"; done
  echo "$(date '+%m-%d %H:%M:%S.%3N') AutomationMarker: $1" >> "$SER"
  echo "$1 $(date +%T)" >> "$EV/marks.txt"
}
apk_check() {   # apk_check SERIAL APK TAG: md5 of the installed APK against the built one, to $EV/TAG-identity.txt
  local s=$1 apk=$2 tag=$3 dev a b
  dev=$(adb -s "$s" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -1)
  rm -f "$EV/$tag-pulled.apk"
  [ -n "$dev" ] && adb -s "$s" pull "$dev" "$EV/$tag-pulled.apk" >/dev/null 2>&1
  a=$(md5sum < "$apk" 2>/dev/null | cut -c1-32); b=$(md5sum < "$EV/$tag-pulled.apk" 2>/dev/null | cut -c1-32)
  if [ -n "$a" ] && [ "$a" = "$b" ]; then echo "md5match=1 $a"; else echo "md5match=0 built=$a installed=$b"; fi > "$EV/$tag-identity.txt"
  rm -f "$EV/$tag-pulled.apk"
}
install_apk() { adb -s "$1" install -r -d "$2" > "$EV/$3-install.txt" 2>&1; apk_check "$1" "$2" "$3"; }
keys_hu() {
  python3 "$RIG_TOOLKIT/set_hu_settings_host.py" "$HU" scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 \
    scalar:int:log-level:2 scalar:int:onboarding-version:2 scalar:int:native-driver-selection-mode:0 \
    "scalar:string:native-preferred-device-mac:$POCO" "scalar:string:last-connected-native-mac:$POCO" \
    "set:native-poke-bt-macs:$POCO" scalar:boolean:native-poke-all-paired:false "set:auto-start-bt-macs:$POCO" \
    scalar:int:native-aa-wake-damage-verdict:0 scalar:int:native-aa-wake-armings-without-session:0 del:video-profile-starvation-cap
}
broker_write() {   # broker_write true|false ADDRESS NAME: the whole relay file as root, app stopped, then read back
  local owner
  owner=$(adb -s "$HU" shell stat -c %U "/data/data/$PKG" | tr -d '\r')
  { echo "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"; echo "<map>"
    echo "    <boolean name=\"enabled\" value=\"$1\" />"
    echo "    <string name=\"address\">$2</string>"; echo "    <string name=\"name\">$3</string>"
    echo "</map>"; } > "$EV/NativeBTBroker-w.xml"
  adb -s "$HU" push "$EV/NativeBTBroker-w.xml" /data/local/tmp/NativeBTBroker.xml >/dev/null
  adb -s "$HU" shell "cp /data/local/tmp/NativeBTBroker.xml $SP/NativeBTBroker.xml && chown $owner:$owner $SP/NativeBTBroker.xml && chmod 660 $SP/NativeBTBroker.xml"
  echo "enabled=$(adb -s "$HU" shell cat "$SP/NativeBTBroker.xml" | grep -ao 'name="enabled" value="[a-z]*"' | grep -o 'true\|false')"   # not-literal
}
tap_ui() {   # tap_ui SERIAL LABEL TEXT: tap the one node whose text is TEXT (any case); 2 when it does not resolve
  local s=$1 label=$2 text=$3 xy="" i
  for i in 1 2 3; do
    adb -s "$s" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb -s "$s" pull /sdcard/ui.xml "$EV/ui-$label.xml" >/dev/null 2>&1
    xy=$(python3 -I "$SCR/tap_text.py" "$EV/ui-$label.xml" "$text" 2>/dev/null) && break
    xy=""; sleep 2
  done
  [ -n "$xy" ] || { echo "$(date +%T) TAP_UNRESOLVED $s $label" >> "$EV/taps.log"; return 2; }
  echo "$(date +%T) tap $s $label $xy" >> "$EV/taps.log"
  adb -s "$s" shell input tap $xy
}
forget_board() {   # remove the OpenHU-BT-Relay bond on D-POCO (3 taps at most); result in $EV/$RUN-board.txt
  local xy n i
  adb -s "$PH" shell am start -a android.settings.BLUETOOTH_SETTINGS >/dev/null; sleep 3
  adb -s "$PH" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb -s "$PH" pull /sdcard/ui.xml "$EV/ui-$RUN-btlist.xml" >/dev/null 2>&1
  if xy=$(python3 -I "$SCR/tap_gear.py" "$EV/ui-$RUN-btlist.xml" OpenHU-BT-Relay OpenHU-NativeBTBroker 2>/dev/null); then
    echo "$(date +%T) tap $PH board-gear $xy" >> "$EV/taps.log"; adb -s "$PH" shell input tap $xy; sleep 2
    tap_ui "$PH" board-forget 'Forget' && sleep 2 && tap_ui "$PH" board-confirm 'Forget device'; sleep 2
  fi
  adb -s "$PH" shell input keyevent KEYCODE_HOME
  n=$(rl_bonds "$PH" | grep -aci 'OpenHU-BT-Relay')   # system
  if [ "${n:-0}" -gt 0 ]; then
    echo "OPERATOR NOW: on D-POCO, forget OpenHU-BT-Relay in Bluetooth settings (the scripted forget did not take)"
    for i in $(seq 1 40); do sleep 15; n=$(rl_bonds "$PH" | grep -aci 'OpenHU-BT-Relay'); [ "${n:-0}" -eq 0 ] && break; done   # system
  fi
  echo "board_bond=${n:-0}" > "$EV/$RUN-board.txt"
}
```

**serial_cap.py**
```python
# Read the ESP32 serial port and stamp each line with the host clock.
# --reset pulses EN for a normal boot. --hold keeps EN low (RTS high), so the board stays in reset, and still reads.
import sys, time, datetime, serial
port, out = sys.argv[1], sys.argv[2]
hold = "--hold" in sys.argv
s = serial.Serial(); s.port = port; s.baudrate = 115200; s.timeout = 1; s.dtr = False; s.rts = hold
s.open()
if "--reset" in sys.argv and not hold:
    s.rts = True; time.sleep(0.1); s.rts = False
def stamp():
    return datetime.datetime.now().strftime("%m-%d %H:%M:%S.%f")[:-3]
buf = b""
with open(out, "a", buffering=1, errors="replace") as f:
    f.write(f"{stamp()} SERIAL_CAP open {port} {'hold' if hold else 'run'}\n")
    while True:
        b = s.read(256)
        if not b:
            continue
        buf += b
        while b"\n" in buf:
            line, buf = buf.split(b"\n", 1)
            f.write(stamp() + " " + line.decode("ascii", "replace").rstrip("\r") + "\n")
```

**crit_count.py**
```python
# Count lines holding NEEDLE between markers FROM and TO, leaving out the 2 s after each EXCL marker.
# usage: crit_count.py CAPTURE FROM TO NEEDLE [EXCL ...]. Prints nothing (exit 1) when the window is missing.
import re, sys
from datetime import datetime, timedelta
cap, a, b, needle, excl = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5:]
def ts(line):
    m = re.match(r"(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d{3})", line)
    return datetime.strptime("2026-" + m.group(1) + " " + m.group(2), "%Y-%m-%d %H:%M:%S.%f") if m else None
try:
    lines = open(cap, errors="replace").read().splitlines()
except OSError:
    sys.exit(1)
win, on = [], False
for l in lines:
    if not on and ("AutomationMarker: " + a) in l:
        on = True
    if on:
        win.append(l)
        if ("AutomationMarker: " + b) in l:
            break
if not win:
    sys.exit(1)
cuts = [t for t in (ts(l) for l in win if any(("AutomationMarker: " + e) in l for e in excl)) if t]
n = 0
for l in win:
    if needle in l:
        t = ts(l)
        if t and any(c <= t <= c + timedelta(seconds=2) for c in cuts):
            continue
        n += 1
print(n)
```

**tap_text.py**
```python
# Print the centre of the one node whose text is argv[2] (any case) in a uiautomator dump; exit 2 unless exactly one.
import re, sys, xml.etree.ElementTree as E
try:
    root = E.parse(sys.argv[1]).getroot()
except Exception as e:
    print(f"tap_text: unreadable dump: {e}", file=sys.stderr); sys.exit(2)
want = sys.argv[2].casefold()
hits = [n for n in root.iter("node") if (n.get("text") or "").casefold() == want]
if len(hits) != 1:
    print(f"tap_text: {len(hits)} matches for {sys.argv[2]!r}", file=sys.stderr); sys.exit(2)
x1, y1, x2, y2 = map(int, re.findall(r"\d+", hits[0].get("bounds")))
print((x1 + x2) // 2, (y1 + y2) // 2)
```

**tap_gear.py**
```python
# Print the centre of the gear (resource id ending settings_button) in the row holding a node whose text is one of argv[2:].
import re, sys, xml.etree.ElementTree as E
try:
    root = E.parse(sys.argv[1]).getroot()
except Exception as e:
    print(f"tap_gear: unreadable dump: {e}", file=sys.stderr); sys.exit(2)
parent = {c: p for p in root.iter() for c in p}
hits = [n for n in root.iter("node") if n.get("text") in sys.argv[2:]]
if len(hits) != 1:
    print(f"tap_gear: {len(hits)} rows match", file=sys.stderr); sys.exit(2)
row = hits[0]
for _ in range(4):
    row = parent.get(row)
    if row is None:
        break
    gears = [n for n in row.iter("node") if (n.get("resource-id") or "").endswith("settings_button")]
    if len(gears) == 1:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", gears[0].get("bounds"))); print((x1 + x2) // 2, (y1 + y2) // 2); sys.exit(0)
print("tap_gear: no single gear in the row", file=sys.stderr); sys.exit(2)
```

## 8. Do not re-run

- C1, P0, P, B and C2: graded PASS in round 2. B passed 5 of 5 with D-POCO not bonded to D-HU.
- The `apply_hfp` branch build, Stage M (calls), and the stock route's held RFCOMM: as in round 2.

## 9. Report back

1. K: `fallback` and `explained`, and every `Broker:` line from `K-go` to `K-post` with its timestamp.
2. S: the resumed activity line, and any `FATAL EXCEPTION` block.
3. Cleanup: D-POCO's `board_bond`, the D-SAM diff, and the bonds left in place (D-HU's LE bond, D-POCO to D-HU).
