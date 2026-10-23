# native-join-scan - round 2 brief

**Queue:** units D-HU (head unit) and D-POCO (phone). D-MOTO stays plugged in with its Bluetooth **off**. D-SAM and D-HP are not used. Two builds: the installed `main` APK (R1, R2) and the candidate `feat/native-join-scan` (R3).
**Time:** 3 runs of 6 cycles (one seed and five graded). Round 1 cycles started about 65 s apart, with the same 30 s hold, so 18 cycles take about 20 min. Add about 15 min for the build, 15 min for preparation and finish, and up to 10 min for reruns and thermal waits. About 60 min in total. One hand step at the start and one at the end (P7, F1).

## 1. Build and baseline

- **Baseline:** `22392a15` (`main`), the build of round 1. Run `$RIG_TOOLKIT/apk_identity.sh` against D-HU's installed APK. Its md5 must be `fb608e0ee92fbfb7d1d1d7542bc89b2b`. If it does not match, build `22392a15` the way round 1's §1 says and record the md5.
- **Save the baseline APK before anything else** (P0), so that F3 can put it back: `P=$(adb -s 27870808938846 shell pm path com.andrerinas.headunitrevived | head -1 | sed 's/^package://' | tr -d '\r')`, then `adb -s 27870808938846 pull "$P" $EV/base-22392a15.apk`. Its md5 must be the one above.
- **Candidate:** branch `feat/native-join-scan` on `fork`, SHA `ebc9c0f5`, two commits on `22392a15`. History was not rewritten. Build it during preparation (P9): `git -C <wt> fetch fork feat/native-join-scan && git -C <wt> checkout --detach ebc9c0f5 && git -C <wt> rev-parse --short HEAD` must print `ebc9c0f5`. Then `$RIG_TOOLKIT/rig_thermal.sh wait 70` and `HUR_DIR=<wt> MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh`. Record the APK's md5. Do not install it until §7 says so.
- **What the candidate changes:** `661b268f` removes the fixed `delay(1000)` before Type 3. `ebc9c0f5` rewords a KDoc only.
- **JVM suite on the dev host at `ebc9c0f5`:** 3309 tests, 0 failures, 0 errors (run 2026-10-17). The rig runs no unit tests.

## 2. What this is and why

Round 1 (`native-join-scan-round1-results.md`) found three things on this pair:

- **The phone stays joined to a kept group when its Bluetooth goes off.** It was still on the group 52 s and 61 s after a force-stop. So round 1's kept-group arm never ran.
- **The join scan for a new group is 2.6 s** with the phone joined to no network, in 16 of 17 cycles.
- **Gearhead turns the phone's screen on 0.4 to 0.5 s after it connects.** So a screen check after SSL reads `Awake` in every cycle. This round does not use one.

This round asks two questions:

1. **Arm KS (kept, stayed).** The group survives the app's stop, and the phone stays on it. Does the next bring-up then skip the join scan? This is a dropped session or a short engine stop with the head unit still powered. The phone has no new group to look for, so it can skip the scan and the association. `main` already has this path: a force-stop leaves the group up, and `startNativeAaQuietHost` adopts it.
2. **The 1 s Type 3 delay.** In round 1's R2, the head unit wrote Type 3 1.01 s after the phone's Type 2 in every cycle. The phone answered with Type 7 2 ms after the write. The candidate sends Type 3 with no pause. The question is whether bring-up gets 1 s shorter and whether every join still succeeds.

## 3. What is different this round

- **The phone's screen stays on for the whole round.** Screen off is deferred.
- **R1 needs the phone ON our group at the launch.** Round 1 needed it off. The gate in `njs2_cycle.sh` changes by arm (§7).
- **The seed cycle `c0` has no WiFi gate**, except exits 6 and 9. Round 1's seed refused to start with the phone on a kept group, and the rig had to reset by hand between runs. The seed sets the state, so it does not need a clean start.
- **One install between runs.** R3 runs on the candidate. F3 puts the baseline back.
- **All the deciding spans are on the head unit's clock** (§6, derived). The phone's numbers are reported but do not decide a verdict.
- **P7 needs a manual disconnect.** In round 1, turning off auto-connect did not make the phone leave `Pegue Cdesta`. The operator also had to disconnect it by hand.
- **`phone_screen on` checks for `Awake`.** The function keeps round 1's `Dozing` fix for `off`, for reuse only. This round never uses `off`.

## 4. Settings keys

The same 13 keys as round 1, written by `njs_keys.sh` at the start of every cycle and read back. D-HU has no `su`. `set_hu_settings_host.py` and `run-as` work. The app adds D-POCO back into `native-poke-bt-macs` after each session, so a write once per run is not enough.

| Element | Why |
|---|---|
| `<int name="wifi-connection-mode" value="3" />` | Native AA |
| `<int name="native-ap-transport" value="0" />` | WiFi Direct |
| `<int name="log-level" value="2" />` | INFO |
| `<int name="native-driver-selection-mode" value="0" />` | no driver selector |
| `<string name="native-preferred-device-mac">POCO</string>` | the target |
| `<string name="last-connected-native-mac">POCO</string>` | as last round |
| `<int name="native-aa-wake-damage-verdict" value="0" />` | no latched verdict |
| `<int name="native-aa-wake-armings-without-session" value="0" />` | |
| `<set name="auto-start-bt-macs" />` | no `AutoStartReceiver` match |
| `<int name="onboarding-version" value="2" />` | no wizard |
| `<set name="native-poke-bt-macs" />` | poke off |
| `<boolean name="native-poke-all-paired" value="false" />` | poke off |
| `<boolean name="wifi-direct-stable-identity" value="true" />` | the gate of the adopt path |

Before the first run, diff `settings.xml` against a fresh backup and state the delta in Setup notes.

## 5. Shared commands

**Source the toolkit's `rig_devices.sh` before `njs_env.sh`**, as round 1 did (its Setup notes), so `$RIG_TOOLKIT` is set.

**njs_env.sh** is round 1's file with two changes: `EV` names round 2, and `phone_screen off` accepts `Dozing` (kept for reuse only).

```bash
export PKG=com.andrerinas.headunitrevived
export HU=27870808938846 PH=4f4027e9 MOTO=ZY22GC3BM4
export EV=$HOME/Coding/StudioProjects/rig-data/rounds/native-join-scan-round2
export RIG_ROUND=$EV/scripts
export POCO=DC:B7:2E:5E:4E:59
export HU_MAC=11:46:03:10:33:59
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { adb -s $HU shell log -t RIGMARK "$1"; adb -s $PH shell log -t RIGMARK "$1"; echo "$(date +%T.%3N) $1" >> $EV/marks.txt; }
waitline() { for i in $(seq 1 "$3"); do grep -aqF "$2" "$1" && return 0; sleep 1; done; return 1; }  # file, fixed string, cap s
phone_bt() {  # on|off; returns 0 when dumpsys agrees, after up to 5 tries 3 s apart
  for i in 1 2 3 4 5; do
    if [ "$1" = off ]; then adb -s $PH shell svc bluetooth disable; else adb -s $PH shell svc bluetooth enable; fi
    sleep 3
    s=$(adb -s $PH shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:' | tr -d '\r')
    if [ "$1" = off ] && echo "$s" | grep -q OFF; then return 0; fi
    if [ "$1" = on ] && echo "$s" | grep -q ' ON'; then return 0; fi
  done; return 1; }
phone_screen() {  # on|off; returns 0 when dumpsys power agrees
  if [ "$1" = on ]; then adb -s $PH shell svc power stayon usb; adb -s $PH shell input keyevent KEYCODE_WAKEUP
  else adb -s $PH shell svc power stayon false; adb -s $PH shell input keyevent KEYCODE_SLEEP; fi
  sleep 2
  w=$(adb -s $PH shell dumpsys power | grep -a -m1 'mWakefulness=' | tr -d '\r')
  if [ "$1" = on ]; then echo "$w" | grep -q Awake; else echo "$w" | grep -qE 'Asleep|Dozing'; fi; }
phone_unjoined() {  # 0 not associated, 1 on another network, 2 on our group or a hidden SSID, 3 unreadable
  adb -s $PH shell dumpsys wifi | grep -a -m1 'mWifiInfo' | tr -d '\r' > "$1"
  [ -s "$1" ] || return 3
  grep -aq 'Supplicant state: COMPLETED' "$1" || return 0
  grep -aqF -e 'DIRECT-' -e '<unknown ssid>' "$1" && return 2; return 1; }
```

- **The launch** is `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`. It arms mode 3 by itself. **Do not send `ACTION_START_WIRELESS_SCAN` after it** (template §4 step 4).
- **The phone** leaves the Bluetooth link only by `svc`, never by airplane mode. **Never run `svc wifi` on the phone in this round.**

**hfp_state.sh** is round 1's file, unchanged:

```bash
#!/bin/bash
# usage: hfp_state.sh <out-file>; the section is "Profile: HeadsetClientService", and MACs show only their last two octets
adb -s "$HU" shell dumpsys bluetooth_manager > /tmp/njs-bt.txt
awk '/^Profile: HeadsetClientService/{f=1;print;next} f&&/^Profile:/{f=0} f' /tmp/njs-bt.txt > "$1"
TAIL=$(echo "$POCO" | awk -F: '{print $(NF-1)":"$NF}')
if grep -aiF "$TAIL" "$1" | grep -aq 'state=Connected'; then echo CONNECTED; else echo NOT; fi
```

**njs_keys.sh** is round 1's file, unchanged:

```bash
#!/bin/bash
# usage: njs_keys.sh <out-file>
source "$(dirname "$0")/njs_env.sh"
adb -s $HU shell am force-stop $PKG
python3 -I $RIG_TOOLKIT/set_hu_settings_host.py $HU scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:int:log-level:2 scalar:int:native-driver-selection-mode:0 scalar:string:native-preferred-device-mac:$POCO scalar:string:last-connected-native-mac:$POCO scalar:int:native-aa-wake-damage-verdict:0 scalar:int:native-aa-wake-armings-without-session:0 setclear:auto-start-bt-macs scalar:int:onboarding-version:2 setclear:native-poke-bt-macs scalar:boolean:native-poke-all-paired:false scalar:boolean:wifi-direct-stable-identity:true >/dev/null
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml | grep -aE 'name="(wifi-connection-mode|native-ap-transport|log-level|native-driver-selection-mode|native-preferred-device-mac|last-connected-native-mac|native-aa-wake-damage-verdict|native-aa-wake-armings-without-session|auto-start-bt-macs|onboarding-version|native-poke-bt-macs|native-poke-all-paired|wifi-direct-stable-identity)"' -A2 | grep -av '^--' > "$1"
for k in '<int name="wifi-connection-mode" value="3" />' '<set name="native-poke-bt-macs" />' '<boolean name="native-poke-all-paired" value="false" />' '<boolean name="wifi-direct-stable-identity" value="true" />' '<set name="auto-start-bt-macs" />'; do
  grep -aqF "$k" "$1" || exit 8; done
exit 0
```

## 6. The deciding lines

Every head-unit string below was checked with `git grep -F` at both `22392a15` and `ebc9c0f5`, with the same count on each. HD is the one exception, on purpose. The lines marked "runtime" build their text from a variable. They were read verbatim in round 1's D-HU captures (`R2-c1-hu.txt`). Grep every capture with `-a`.

Head unit capture, `$EV/<run>-c<n>-hu.txt`:

| Id | Fixed string | Source |
|---|---|---|
| H1 | `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | `NativeAaHandshakeManager.kt` |
| H2 | `createGroup SUCCESS!` | `WifiDirectManager.kt`, a new group |
| HA | `before this bring-up; reading it instead of tearing it down.` | `WifiDirectManager.kt`, the adopt (second half of a two-literal line). The line starts `a group named <ssid> is already up from` |
| HB | `removeGroup before recreate failed` | `WifiDirectManager.kt`. With `reason=BUSY` it means no group existed |
| HI | `WifiDirectManager: group identity ssid=` | read the value after `bssid=` |
| H3 | `NativeAA: Connection accepted from` | the phone's Bluetooth dial accepted |
| T2 | `NativeAA: [RX] Received Type 2 ` | runtime. The phone is ready for credentials |
| HC | `NativeAA: [TX] Sending WifiInfoResponse (Type 3) with full credentials` | both builds, once per Type 3 send |
| HD | `with full credentials in 1000ms` | the baseline only. **HD = HC on `22392a15`, HD = 0 on `ebc9c0f5`** |
| T3 | `NativeAA: [TX] Wrote TYPE 3 ` | runtime. Type 3 written to the socket |
| INC | `WirelessServer: Incoming connection detected` | the phone's TCP connection on our group |
| H4 | `SSL handshake complete` | `AapSslContext.kt` |
| HL | `the phone's session landed while Type 3 was pending` | `NativeAaHandshakeManager.kt`, credentials skipped |
| H9 | `NativeAA: No wake poke phone selected, so nothing is poked.` | proof the poke is off |
| H6 | `NativeAA: Calling socket.connect()` | a real poke dial, must be 0 |
| H10 | `WppTcpServer: [TX] WifiVersionRequest` | a TCP-route contact |
| H12 | `MATCH! Starting AapService` | discard rule |
| E | `AutomationMarker: <run>-c<n>-end` | the cycle end |

Phone capture, `$EV/<run>-c<n>-ph.txt` (Android's own lines, read in round 1's D-POCO captures):

| Id | Fixed string | Meaning |
|---|---|---|
| J-req | `/WifiNetworkFactory(`, on a line that also holds `got request NetworkRequest` and `LITERAL: DIRECT-` | the phone asks for our group |
| J-conn | `User initiated connect to network: "DIRECT-` | the phone connects. **A KS cycle may not print it**, because the phone is already on the network |
| S-start | `scan start scan id` | the WiFi driver starts a scan |
| P-dial | `Creating rfcomm socket for device: <HU_MAC>` | Android Auto dials our listener |

## 7. Runs

### Preparation (once, before R1)

- **P0.** Save the baseline APK (§1). Record its md5.
- **P1.** Run `adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $EV/settings.bak`. Diff it against round 1's backup and record the delta.
- **P2.** On D-MOTO, run `adb -s $MOTO shell svc bluetooth disable`. Read it back as off.
- **P3.** Run round 1's `njs_bonded.sh` (below). Confirm `POCO` and `HU_MAC`. If D-POCO's list holds another rig head unit's radio, stop and ask the operator to forget it on D-POCO.

  **njs_bonded.sh**

  ```bash
  #!/bin/bash
  adb -s 27870808938846 shell settings get secure bluetooth_name
  adb -s 27870808938846 shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices'
  adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices'
  ```
- **P4.** Run `adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a versionName` and record it.
- **P5.** Run `adb -s $PH shell dumpsys window | grep mCurrentFocus`. If the focus is not the launcher, run `adb -s $PH shell input keyevent KEYCODE_HOME`.
- **P6.** Run `adb -s $PH shell settings get global stay_on_while_plugged_in` and record the value for F2.
- **P7. Hand step (operator).** On D-POCO, open Settings, then Wi-Fi. For `Pegue Cdesta`, and for every other saved network in range, turn **Auto-connect** off. Then select the joined network, then select **Disconnect**. Do not forget any network. Leave WiFi on. Wait 45 s, then run `phone_unjoined $EV/p7-wifi.txt` twice, 45 s apart. Both must return 0. If either returns anything else, send the saved line to the host and stop.
- **P8.** Run `adb -s $HU shell dumpsys wifi | grep -iE "mWifiInfo" | head -2` and record D-HU's own station state.
- **P9.** Build the candidate (§1) and record its md5. Do not install it yet.

### The cycle

**njs2_cycle.sh** takes the run id, the cycle number, the arm (`ks` or `r`) and the hold in seconds. Arm `ks` ends with a force-stop, so the group stays up. Arm `r` ends with `headunit://exit` and then a force-stop, so the group is removed. The screen is always on. Exit codes:

- 0: graded.
- 3: no HFP.
- 4: a radio or the screen would not switch.
- 5: thermal.
- 6: the phone joined another network.
- 7: arm `r`, and the phone was on our group at the launch.
- 8: a settings key read back wrong.
- 9: `dumpsys wifi` printed no `mWifiInfo` line.
- 10: arm `ks`, and the phone was not on our group at the launch (a **KS-miss**).

```bash
#!/bin/bash
set -u
source "$(dirname "$0")/njs_env.sh"
: "${EV:?}" "${RIG_ROUND:?}" "${POCO:?}" "${HU_MAC:?}"
RUN=$1; C=$2; ARM=$3; HOLD=$4
T=$EV/$RUN-c$C
$RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || exit 5
$RIG_TOOLKIT/rig_thermal.sh watch $EV/thermal-$RUN-c$C.log & echo $! > $T-th.pid

finish() { # stop captures and the thermal watch, then end the way this arm ends
  kill "$(cat $T-hu.pid)" "$(cat $T-ph.pid)" "$(cat $T-th.pid)" 2>/dev/null
  if [ "$ARM" = r ] && adb -s $HU shell pidof $PKG >/dev/null; then
    adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit"; sleep 3; fi
  adb -s $HU shell am force-stop $PKG; }

# 1. phone off the Bluetooth link; its WiFi stays on
phone_bt off || { echo NO_BT_OFF > $T-gate.txt; kill "$(cat $T-th.pid)"; exit 4; }
phone_screen on || { echo NO_SCREEN > $T-gate.txt; kill "$(cat $T-th.pid)"; exit 4; }

# 2. keys written with the app stopped (a force-stop leaves a group up), captures on
$RIG_ROUND/njs_keys.sh $T-keys.txt || { echo KEYS_WRONG > $T-gate.txt; kill "$(cat $T-th.pid)"; exit 8; }
adb -s $HU logcat -G 16M; adb -s $PH logcat -G 16M
adb -s $HU logcat -c; adb -s $PH logcat -c
stdbuf -oL adb -s $HU logcat -v time > $T-hu.txt & echo $! > $T-hu.pid
stdbuf -oL adb -s $PH logcat -v time > $T-ph.txt & echo $! > $T-ph.pid

# 3. phone Bluetooth on while our app is stopped
mark "$RUN-c$C-phone-on"
phone_bt on || { echo NO_BT_ON > $T-gate.txt; finish; exit 4; }
ok=0; for i in $(seq 1 30); do [ "$($RIG_ROUND/hfp_state.sh $T-hfc.txt)" = CONNECTED ] && ok=1 && break; sleep 2; done
if [ $ok = 0 ]; then mark "$RUN-c$C-no-hfp"; echo NO_HFP > $T-gate.txt; finish; exit 3; fi
mark "$RUN-c$C-hfp-up"
sleep "$HOLD"

# 4. state gates just before the launch; the seed c0 checks only 6 and 9
phone_unjoined $T-phwifi.txt; j=$?
[ $j = 1 ] && { echo PHONE_JOINED > $T-gate.txt; finish; exit 6; }
[ $j = 3 ] && { echo NO_WIFIINFO > $T-gate.txt; finish; exit 9; }
if [[ $C != 0* ]]; then
  if [ "$ARM" = r ] && [ $j = 2 ]; then echo PHONE_ON_GROUP > $T-gate.txt; finish; exit 7; fi
  if [ "$ARM" = ks ] && ! { [ $j = 2 ] && grep -aqF 'DIRECT-' $T-phwifi.txt; }; then echo KS_MISS > $T-gate.txt; finish; exit 10; fi
fi
adb -s $PH shell dumpsys power | grep -a -m1 'mWakefulness=' > $T-screen.txt
mark "$RUN-c$C-launch"
adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity

# 5. wait for the session, then end
waitline $T-hu.txt 'SSL handshake complete' 90 || mark "$RUN-c$C-no-ssl"
sleep 5
send ACTION_LOG_MARKER --es text "$RUN-c$C-end"
sleep 1
finish
exit 0
```

Put `njs_env.sh`, `hfp_state.sh`, `njs_keys.sh`, `njs_bonded.sh` and `njs2_cycle.sh` in `$RIG_ROUND` and make them executable.

### Extract for every cycle (Haiku returns these numbers, and Sonnet grades)

Head unit capture, all after the `-launch` RIGMARK:
- the first timestamp of H1, H3, T2, T3, INC and H4;
- the counts of H2, HA, HC, HD, H3, H4, HL, H6, H9, H10 and H12, and of `AapRead: Magic Garbage detected in header`;
- the first HB line in full;
- the value after `bssid=` in the first HI line, or "none" when no HI line printed;
- the BSSID in `$T-phwifi.txt` (after `BSSID: `);
- the timestamp of every `RIGMARK` line and of E.

Phone capture, after the `-launch` RIGMARK:
- the count of J-req lines, the first J-req timestamp, and the BSSID after `BSSID Match pattern=Pair{` in it;
- the first J-conn timestamp after that J-req, or "none";
- the count of S-start lines from 10 ms before the J-req to the J-conn, or to J-req + 10 s when J-conn is "none". When the J-req count is 0, count S-start lines from the `-launch` mark to the end of the capture instead;
- the count of P-dial lines between `-hfp-up` and `-launch`, and the MAC in each, compared with `HU_MAC` (`-aiF`).

Also give the exit code, `$T-gate.txt` if it exists, and the contents of `$T-screen.txt`, `$T-phwifi.txt` and `$T-keys.txt`.

Derived, on the head unit clock:
- **TD** (Type 3 delay) = T3 minus T2. Round 1 measured 1.01 s on the baseline.
- **JH** (join, head unit view) = INC minus T3. Round 1 measured 3.0 to 3.2 s for a new group.
- **AS** (accept to SSL) = H4 minus H3. Round 1 measured 4.9 to 5.1 s.
- **BT** (bring-up time) = H4 minus H1.
- **JS** (phone clock) = J-conn minus J-req, when J-conn printed. Reported only.

A cycle **skips the join** when JH ≤ 1.5 s and the S-start count is 0. When T3 is absent and INC printed (an HL cycle), JH is "n/a", and the cycle skips the join if the S-start count is 0.

Medians of TD are over counted cycles that have both T2 and T3. Medians of JH skip "n/a".

A cycle has a **failed join** when INC never prints within 30 s after T3, or T3 and INC are both absent, or H3 > 1, or H4 = 0, or the phone's J-req count is more than 1.

### Which arm a cycle really ran (every graded cycle)

"The last HI BSSID" means the `bssid=` value of the last HI line printed in an earlier cycle of the same run, seed included. A KS adopt may print no HI line, so it can be several cycles back. Compare BSSIDs without regard to case.

- **KS:** HA = 1, H2 = 0, and the BSSID in `$T-phwifi.txt` equals the last HI BSSID. That proves the phone stayed on the same group.
- **R:** HA = 0, H2 = 1, the HB line holds `reason=BUSY`, and this cycle's HI BSSID differs from the last HI BSSID.

A cycle in a KS run that exited 0 and does not meet KS is a **K-miss**. Record it and do not grade it. K-misses do not count in R1's total. A cycle in an R run that does not meet R is void: run it again once. If the rerun is also void, do not count it.

### Counted cycles and discard rules

A graded cycle (`c1` to `c5`) counts only if:
- it exited 0;
- it meets its arm (above);
- `$T-screen.txt` shows `Awake`;
- P-dial ≥ 1, and every P-dial names `HU_MAC`;
- H9 ≥ 1 and H6 = 0;
- HD is right for the build: HD = HC on R1 and R2 (baseline), HD = 0 on R3 (candidate). A wrong HD means the wrong APK is installed: stop the round and send `apk_identity.sh` output to the host.

Discard a cycle and run it again once if it has an H12, more than one H4, an H10 or a `Magic Garbage` line. A cycle with HL ≥ 1 counts, but report it, because the phone's session came up before credentials went out. An exit 6 is also run again once. If a second exit 6 comes in the same run, stop the round and send `$T-phwifi.txt` to the host. An exit 9 stops the round at once. A run with fewer than 4 counted cycles after its re-runs is INCONCLUSIVE.

**Before any rerun, keep the first try's files.** Run `for f in $T-*; do mv "$f" "${f/$T-/$T-try1-}"; done`, with `T=$EV/<run>-c<n>`. A rerun writes to the same names and would truncate them.

### R1: kept group, phone stays on it, baseline (**the point of the round**)

`c0` seed, then `c1` to `c5`: `$RIG_ROUND/njs2_cycle.sh R1 <n> ks 30`. The seed has the phone off any group. It creates the group and ends with a force-stop, so the group and the phone's association stay up.

### R2: removed group, baseline (the control for R1 and for R3)

`c0` seed, then `c1` to `c5`: `$RIG_ROUND/njs2_cycle.sh R2 <n> r 30`. The seed starts on R1's kept group and removes it.

### Install the candidate (between R2 and R3)

1. Run `adb -s $HU shell am force-stop $PKG`.
2. Run `adb -s $HU install -r -d <candidate apk>`.
3. Run `$RIG_TOOLKIT/apk_identity.sh` against D-HU. The md5 must be the one from P9. Record it.

### R3: removed group, candidate (the 1 s delay)

`c0` seed, then `c1` to `c5`: `$RIG_ROUND/njs2_cycle.sh R3 <n> r 30`.

### Verdicts

**KS (R1 against R2):**
- **PASS:** 3 or more of R1's counted cycles skip the join (3 of 4, or 3 or more of 5), and R2 skips it in at most 1.
- **FAIL:** 2 or fewer of R1's counted cycles skip the join, and R2 skips it in at most 1.
- **INCONCLUSIVE:** R2 skips the join in 2 or more cycles, or either run is INCONCLUSIVE.

**Delay (R3 against R2):**
- **PASS:** R3's median TD ≤ 0.1 s, R3's median AS ≤ R2's median AS minus 0.7 s, and no R3 cycle has a failed join.
- **FAIL:** an R3 cycle has a failed join while no R2 cycle has one. Report every line from T3 to the end of that cycle's head unit capture that holds `NativeAA:` or `WirelessServer:`.
- **FAIL (still delayed):** R3's median TD is above 0.5 s. Run `apk_identity.sh` against D-HU and report it.
- **INCONCLUSIVE:** an R2 cycle has a failed join, or either run is INCONCLUSIVE, or the medians miss PASS with no failed join and R3's median TD is 0.5 s or less.

**Report per cycle:** TD, JH, AS, BT, JS, the S-start count, the J-req count, HL, and the BSSID.

### Stop rule

- After one exit 3 or 4, run the cycle again once. When two cycles in a row of one run exit 3 or 4, mark that run INCONCLUSIVE and go to the next run.
- Two exit 5s in a row stop the round.
- An exit 8 or 9 stops the round at once. Send `$T-keys.txt` or the `dumpsys wifi` head to the host.
- An exit 7 in R2 or R3: run the cycle again once. Two exit 7s in a row make that run INCONCLUSIVE (the phone stays on a removed group). Go on to the next run.
- An exit 10 in R1 leaves the phone off the group, so a plain rerun exits 10 again. Record it, then run a new seed and repeat the cycle: `njs2_cycle.sh R1 0b ks 30` (then `0c`), then `njs2_cycle.sh R1 <n> ks 30`. Count each graded slot once, after its one seed and repeat. When 2 of R1's first 3 graded slots still end in exit 10, mark R1 INCONCLUSIVE ("the phone leaves a kept group within the hold") and go to R2.
- When 2 of R1's first 3 graded cycles are K-misses, mark R1 INCONCLUSIVE. Report their HA, H2 and HB lines, and go to R2.
- When 2 R3 cycles have a failed join, stop R3 and go to Finish.

### Finish (once, after the last run)

- **F3.** Put the baseline back: run `adb -s $HU shell am force-stop $PKG`, then `adb -s $HU install -r -d $EV/base-22392a15.apk`. Run `apk_identity.sh`. The md5 must be `fb608e0ee92fbfb7d1d1d7542bc89b2b`.
- **F1. Hand step (operator).** On D-POCO, turn Auto-connect back on for every network P7 changed. Confirm with `phone_unjoined` returning 1 within 60 s.
- **F2.** Run `adb -s $PH shell settings put global stay_on_while_plugged_in <P6 value>`. With the app stopped, restore `settings.xml` from `$EV/settings.bak` the way round 1 did, and check it reads back byte-identical.

## 8. Do not re-run

Settled on this pair: the phone redials a late listener every 5.2 to 5.8 s while HFP is held (`late-listener-poke` round 1). A removed group costs one 2.6 s join scan with the phone on no network, screen on or off. A phone with Bluetooth off stays on a kept group. Gearhead wakes the phone's screen 0.4 to 0.5 s after it connects (`native-join-scan` round 1). Out of scope: the screen-off kept group, a phone joined to the rig's access point, and boot or ACC start time.

## 9. Report back

1. KS: how many of R1's counted cycles skip the join, against R2. The medians of JH and BT for each.
2. Delay: the medians of TD and AS for R3 against R2, and the count of failed joins in each.
3. Any HL line. For each KS cycle, the S-start count and the J-req count. Both are expected to be 0 on a real skip.
