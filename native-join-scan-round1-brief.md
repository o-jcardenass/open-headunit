# native-join-scan - round 1 brief

**Queue:** units D-HU (head unit) and D-POCO (phone). D-MOTO stays plugged in with its Bluetooth **off**. D-SAM and D-HP are not used. No candidate branch: the same `origin/main` APK as `late-listener-poke` round 1.
**Time:** 4 runs of 6 cycles (one seed and five graded), about 3.5 min a cycle. About 95 min with preparation. One hand step at the start and one at the end (P7, F1).

## 1. Build and baseline

- **SHA:** `22392a15`, the tip of `origin/main` and the build of `late-listener-poke` round 1. History was not rewritten.
- **Reuse first.** Run `$RIG_TOOLKIT/apk_identity.sh` against D-HU's installed APK. If its md5 is `fb608e0ee92fbfb7d1d1d7542bc89b2b` (4.0.0-alpha debug), do not build. Record MATCH in Setup notes.
- **Only if it does not match:** `git -C <wt> fetch origin && git -C <wt> checkout --detach 22392a15 && git -C <wt> rev-parse --short HEAD` must print `22392a15`. Then `$RIG_TOOLKIT/rig_thermal.sh wait 70`, `HUR_DIR=<wt> MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh`, and `adb -s 27870808938846 install -r -d <apk>`. Record the md5.
- **JVM suite on the dev host at `22392a15`:** 3309 tests in 362 classes, 0 failures, 0 skipped (run 2026-10-17). The rig runs no unit tests.

## 2. What this is and why

`late-listener-poke` round 1 timed a late bring-up on this pair. From launch to SSL took 13.6 s, and the largest part was the phone's WiFi scan: 5.2 to 6.0 s from the phone's network request (`WifiNetworkFactory: got request`) to its connect (`User initiated connect to network`). The facts behind it:

- **D-HU gives each new group a new BSSID and a new channel.** Its identity reads `stable=no`, so we send DYNAMIC. The phone logs `WIRELESS_WIFI_CREDENTIALS_CACHE_NOT_FOUND` on every cycle. The phone's request pins the BSSID and carries `channels=[]`.
- **Android's `WifiNetworkFactory` reads cached scan results before it scans**, and accepts only results less than 30 s old (`CACHED_SCAN_RESULTS_MAX_AGE_IN_MILLIS`, AOSP 14, 15 and main). Without a match, it scans every 10 s.
- **Each launch built a group 2 to 3 s before the phone's request**, so no phone scan could hold it yet.

The design question: should a non-user stop (ACC-off, a service destroy) keep the group up for the next start to adopt? A kept group has the same BSSID and channel. So the phone's own background scans can see it before the request comes. The gain depends on when the phone last scanned, so this round measures it before any code is written.

**`main` already has the kept-group path.** A force-stop runs no teardown, so the group survives. `startNativeAaQuietHost` then adopts a live group with the same name (gated on `wifi-direct-stable-identity`). A `headunit://exit` removes the group. So this round compares the two kinds of end, with no code change.

## 3. What is different this round

- **The phone's WiFi stays on for the whole round, joined to no network.** Only its Bluetooth goes off and on. In `late-listener-poke` round 1 the script switched the phone's WiFi on together with its Bluetooth. That made the phone scan 2.5 s before the request, and gave one run a fast join for a reason the field does not have. This round must not toggle the phone's WiFi.
- **The phone must not join `Pegue Cdesta` or any other saved network.** No adb command turns off auto-connect for a saved network without its password. The operator turns it off by hand (P7) and back on (F1). Do not forget any network.
- **The phone's screen state is a variable.** Runs R1 and R2 hold the screen on, and R3 and R4 hold it off. `WifiNetworkFactory` stops its periodic scans while the screen is off, and a phone joined to nothing scans less often with the screen off.
- **How a cycle ends sets the arm of the next cycle.** A cycle that ends with `am force-stop` leaves the group up (arm K). A cycle that ends with `headunit://exit` removes it (arm R). So each run starts with a seed cycle `c0` that is not graded: it puts the group into the run's state.
- **Before the launch, markers come from `log`, not from the app** (`rig-quirks/topics/lifecycle.md`). The two devices' clocks differ, so compute every delta on one device's clock.
- **Expected INCONCLUSIVE:** a K cycle in which D-HU's platform removed the client-less group by itself. That is a finding about D-HU, not a fault in the cycle (§7, stop rule).

## 4. Settings keys

D-HU has no `su` (`late-listener-poke` round 1, Setup notes), and `set_hu_settings_host.py` and `run-as` work. `njs_cycle.sh` writes these keys at the start of every cycle, with the app force-stopped, and reads them back. The app adds D-POCO back into `native-poke-bt-macs` after each session (`late-listener-poke` round 1, errata 2), so a write once per run is not enough. An empty set must read `<set name="KEY" />` (`rig-quirks/topics/tooling.md`, the `ohu_setkeys.py` entry). `POCO` is D-POCO's Bluetooth MAC from P3.

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
| `<set name="native-poke-bt-macs" />` | poke off, so the poke cannot move the timing |
| `<boolean name="native-poke-all-paired" value="false" />` | poke off |
| `<boolean name="wifi-direct-stable-identity" value="true" />` | the gate of the adopt path. Last round found it `true`; write it so it cannot drift |

Before the first run, diff `settings.xml` against a fresh backup and state the delta in Setup notes.

## 5. Shared commands

`POCO` and `HU_MAC` are last round's values. P3 confirms them. Write `HU_MAC` in upper case.

**njs_env.sh**

```bash
export PKG=com.andrerinas.headunitrevived
export HU=27870808938846 PH=4f4027e9 MOTO=ZY22GC3BM4
export EV=$HOME/Coding/StudioProjects/rig-data/rounds/native-join-scan-round1
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
  if [ "$1" = on ]; then echo "$w" | grep -q Awake; else echo "$w" | grep -q Asleep; fi; }
phone_unjoined() {  # 0 not associated, 1 on another network, 2 on our group or a hidden SSID, 3 unreadable
  adb -s $PH shell dumpsys wifi | grep -a -m1 'mWifiInfo' | tr -d '\r' > "$1"
  [ -s "$1" ] || return 3
  grep -aq 'Supplicant state: COMPLETED' "$1" || return 0
  grep -aqF -e 'DIRECT-' -e '<unknown ssid>' "$1" && return 2; return 1; }
```

- **The launch** is `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`. It arms mode 3 by itself. **Do not send `ACTION_START_WIRELESS_SCAN` after it** (template §4 step 4).
- **The phone** leaves the Bluetooth link only by `svc`, never by airplane mode. **Never run `svc wifi` on the phone in this round.**

**hfp_state.sh** is last round's corrected script. It prints `CONNECTED` or `NOT` for D-HU's own hands-free client link to `$POCO`:

```bash
#!/bin/bash
# usage: hfp_state.sh <out-file>; the section is "Profile: HeadsetClientService", and MACs show only their last two octets
adb -s "$HU" shell dumpsys bluetooth_manager > /tmp/njs-bt.txt
awk '/^Profile: HeadsetClientService/{f=1;print;next} f&&/^Profile:/{f=0} f' /tmp/njs-bt.txt > "$1"
TAIL=$(echo "$POCO" | awk -F: '{print $(NF-1)":"$NF}')
if grep -aiF "$TAIL" "$1" | grep -aq 'state=Connected'; then echo CONNECTED; else echo NOT; fi
```

**njs_keys.sh** writes §4's keys with the app stopped, and saves the read-back:

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

Every head-unit string was checked with `git grep -F` at `22392a15`. Grep every capture with `-a`.

Head unit capture, `$EV/<run>-c<n>-hu.txt`:

| Id | Fixed string | Source |
|---|---|---|
| H1 | `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | `NativeAaHandshakeManager.kt` |
| H2 | `createGroup SUCCESS!` | `WifiDirectManager.kt`, a new group |
| HA | `before this bring-up; reading it instead of tearing it down.` | `WifiDirectManager.kt`, the adopt. The runtime line joins two literals, so grep this second half |
| HB | `removeGroup before recreate failed` | `WifiDirectManager.kt`. With `reason=BUSY` it means no group existed |
| HI | `WifiDirectManager: group identity ssid=` | the group's BSSID and `stable=` verdict |
| H3 | `NativeAA: Connection accepted from` | `NativeAaHandshakeManager.kt` |
| H4 | `SSL handshake complete` | `AapSslContext.kt` |
| H9 | `NativeAA: No wake poke phone selected, so nothing is poked.` | proof the poke is off |
| H6 | `NativeAA: Calling socket.connect()` | a real poke dial, must be 0 |
| H10 | `WppTcpServer: [TX] WifiVersionRequest` | a TCP-route contact |
| H12 | `MATCH! Starting AapService` | discard rule |
| R | `RIGMARK` | our marks |
| E | `AutomationMarker: <run>-c<n>-end` | the cycle end |

Phone capture, `$EV/<run>-c<n>-ph.txt`. The `WifiNetworkFactory` lines and the scan lines are Android's own and were read in last round's D-POCO captures. The `GH.` lines come from Android Auto 18.0, so record its version (P4):

| Id | Fixed string | Meaning |
|---|---|---|
| J-req | `/WifiNetworkFactory(`, on a line that also holds `got request NetworkRequest` and `LITERAL: DIRECT-`. The fixed string skips the Untrusted, OemPaid and MultiInternet copies printed 1 to 4 ms later | the phone asks for our group. Read the BSSID after `BSSID Match pattern=Pair{` |
| J-conn | `User initiated connect to network: "DIRECT-` | the phone found the group and connects |
| S-start | `scan start scan id` | the WiFi driver starts a scan |
| S-done | `type COMPLETED` | that scan finished, and its results enter the cache |
| P-dial | `Creating rfcomm socket for device: <HU_MAC>` | Android Auto dials our listener |
| P-cache | `WIRELESS_WIFI_CREDENTIALS_CACHE_NOT_FOUND` | the phone has no stored record of our group |

## 7. Runs

### Preparation (once, before R1)

- **P1.** Run `adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $EV/settings.bak`. Diff it against last round's backup and record the delta.
- **P1a.** Run `adb -s $HU shell run-as $PKG stat -c '%U:%G' shared_prefs` and record the result in Setup notes. The adopt path needs the group name and passphrase that the app writes (`rig-quirks/units/D-HU.md`). If the owner is not the app's own uid, stop and send the result to the host.
- **P2.** On D-MOTO, run `adb -s $MOTO shell svc bluetooth disable`. Read it back as off with `adb -s $MOTO shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'`.
- **P3.** Read both bonded lists. Confirm `POCO` and `HU_MAC` in `njs_env.sh`. If D-POCO's list holds another rig head unit's radio, stop and ask the operator to forget it on D-POCO (`rig-quirks/units/D-POCO.md`).

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
- **P7. Hand step (operator).** On D-POCO, open Settings, then Wi-Fi. For `Pegue Cdesta`, and for every other saved network the list shows as in range, turn **Auto-connect** (or Auto-reconnect) off. Do not forget any network. Leave WiFi on. Then wait 90 s and run `phone_unjoined $EV/p7-wifi.txt` twice, 45 s apart. Both must return 0. If either returns anything else, send the saved line to the host and stop.
- **P8.** Run `adb -s $HU shell dumpsys wifi | grep -iE "mWifiInfo" | head -2` and record D-HU's own station state. D-HU stays joined to its network, as on every round (`rig-quirks/topics/wifi.md`).

### The cycle

**njs_cycle.sh** takes the run id, the cycle number, the end (`keep` or `remove`), the phone screen (`on` or `off`) and the hold in seconds. Exit codes:

- 0: graded.
- 3: no HFP.
- 4: a radio or the screen would not switch.
- 5: thermal.
- 6: the phone joined another network.
- 7: the phone was on our group, or on a network whose SSID it hides, at the launch.
- 8: a settings key read back wrong.
- 9: `dumpsys wifi` printed no `mWifiInfo` line.

```bash
#!/bin/bash
set -u
source "$(dirname "$0")/njs_env.sh"
: "${EV:?}" "${RIG_ROUND:?}" "${POCO:?}" "${HU_MAC:?}"
RUN=$1; C=$2; END=$3; SCREEN=$4; HOLD=$5
T=$EV/$RUN-c$C
$RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || exit 5
$RIG_TOOLKIT/rig_thermal.sh watch $EV/thermal-$RUN-c$C.log & echo $! > $T-th.pid

finish() { # stop captures and the thermal watch, then end the way this run ends
  kill "$(cat $T-hu.pid)" "$(cat $T-ph.pid)" "$(cat $T-th.pid)" 2>/dev/null
  if [ "$END" = remove ] && adb -s $HU shell pidof $PKG >/dev/null; then
    adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit"; sleep 3; fi
  adb -s $HU shell am force-stop $PKG; }

# 1. phone off the Bluetooth link; its WiFi stays on
phone_bt off || { echo NO_BT_OFF > $T-gate.txt; kill "$(cat $T-th.pid)"; exit 4; }
phone_screen "$SCREEN" || { echo NO_SCREEN > $T-gate.txt; kill "$(cat $T-th.pid)"; exit 4; }

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

# 4. state gates just before the launch
phone_unjoined $T-phwifi.txt; j=$?
[ $j = 1 ] && { echo PHONE_JOINED > $T-gate.txt; finish; exit 6; }
[ $j = 2 ] && { echo PHONE_ON_GROUP > $T-gate.txt; finish; exit 7; }
[ $j = 3 ] && { echo NO_WIFIINFO > $T-gate.txt; finish; exit 9; }
adb -s $PH shell dumpsys power | grep -a -m1 'mWakefulness=' > $T-screen.txt
mark "$RUN-c$C-launch"
adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity

# 5. wait for the session, then end
waitline $T-hu.txt 'SSL handshake complete' 90 || mark "$RUN-c$C-no-ssl"
adb -s $PH shell dumpsys power | grep -a -m1 'mWakefulness=' > $T-screen2.txt
sleep 5
send ACTION_LOG_MARKER --es text "$RUN-c$C-end"
sleep 1
finish
exit 0
```

Put `njs_env.sh`, `hfp_state.sh`, `njs_keys.sh` and `njs_cycle.sh` in `$RIG_ROUND` and make them executable.

### Extract for every cycle (Haiku returns these numbers, and Sonnet grades)

Head unit capture:
- the first timestamp of H1, H3, H4 and H10;
- the counts of H2, HA, H6, H9 and H12, and of `AapRead: Magic Garbage detected in header`;
- the first HB line in full;
- the BSSID and the `stable=` value in the first HI line that holds `stable=`, or "none" when no such line printed;
- the timestamp of every `RIGMARK` line and of E.

Phone capture:
- the first J-req timestamp after `-launch`, and the BSSID in that line;
- the first J-conn timestamp after that J-req;
- the count of S-start lines from J-req to J-conn, including one stamped up to 10 ms after J-req (the request starts its own scan at once);
- the timestamp of the last S-done line before J-req;
- the count of P-dial lines between `-hfp-up` and `-launch`, and the MAC in each, compared with `HU_MAC` (`-aiF`);
- the count of P-cache lines.

Also give the exit code, `$T-gate.txt` if it exists, and the contents of `$T-screen.txt`, `$T-screen2.txt`, `$T-phwifi.txt` and `$T-keys.txt`.

Derived, each on one device's clock:
- **JS** (join scan) = J-conn minus J-req, on the phone clock. **The round's number.** A cycle **skips the scan** when JS ≤ 1.5 s. Last round measured 5.2 to 6.0 s with a scan and 0.2 s without one.
- **AGE** = J-req minus the last S-done before it, on the phone clock. The cache rule predicts a skip when AGE < 30 s and that scan came after the group was up.
- **BT** (bring-up time) = H4 minus H1, on the head unit clock.

### Which arm a cycle really ran (every graded cycle)

"The previous cycle" means the last cycle of the same run that launched and printed a J-req, seed included.

- **K (kept):** HA = 1, H2 = 0, and the J-req BSSID equals the previous cycle's J-req BSSID.
- **R (removed):** HA = 0, H2 = 1, the HB line holds `reason=BUSY`, and the J-req BSSID differs from the previous cycle's.

A cycle in a K run that does not meet K is a **K-miss**. Record it and do not grade it. A cycle in an R run that does not meet R is void: run it again once.

### Counted cycles and discard rules

A graded cycle (`c1` to `c5`) counts only if:
- it exited 0;
- it meets its arm (above);
- `$T-screen.txt` (before the launch) and `$T-screen2.txt` (after SSL) both show the run's screen state (`Awake` for on, `Asleep` for off);
- the phone dialled at least once between `-hfp-up` and `-launch` (P-dial ≥ 1);
- H9 printed and H6 = 0.

Discard a cycle and run it again once if it has an H12, more than one H4, an H10, a `Magic Garbage` line, or a P-dial that names a MAC other than `HU_MAC`. An exit 6 is also run again once. If a second exit 6 comes in the same run, stop the round and send `$T-phwifi.txt` to the host, because P7 did not hold. An exit 9 stops the round at once: send the raw `dumpsys wifi` head to the host. A run with fewer than 4 counted cycles after its re-runs is INCONCLUSIVE.

### R1: kept group, phone screen on (**the point of the round**)

`c0` seed, then `c1` to `c5`: `$RIG_ROUND/njs_cycle.sh R1 <n> keep on 30`.

### R2: removed group, phone screen on (R1's control)

`c0` seed, then `c1` to `c5`: `$RIG_ROUND/njs_cycle.sh R2 <n> remove on 30`.

### R3: kept group, phone screen off

`c0` seed, then `c1` to `c5`: `$RIG_ROUND/njs_cycle.sh R3 <n> keep off 30`.

### R4: removed group, phone screen off (R3's control)

`c0` seed, then `c1` to `c5`: `$RIG_ROUND/njs_cycle.sh R4 <n> remove off 30`.

### Verdicts, per pair (R1 with R2, R3 with R4)

- **PASS:** the K run skips the scan in 3 or more counted cycles. Its R run skips it in at most 1.
- **FAIL:** the K run skips the scan in 2 or fewer counted cycles. Its R run skips it in at most 1.
- **INCONCLUSIVE:** the R run skips the scan in 2 or more cycles (the arms do not separate), or either run is INCONCLUSIVE.
- **Report per cycle:** JS, AGE, BT, the S-start count and the P-cache count.

### Stop rule

- When two cycles in a row of one run exit 3 or 4, mark that run INCONCLUSIVE and go to the next run.
- An exit 8 stops the round at once. Send `$T-keys.txt` to the host.
- An exit 7 is not a fault. The phone kept its association to a kept group with its Bluetooth off. Record it and run the cycle again once.
- Two exit 7s in a row in a K run make that run INCONCLUSIVE (the phone stays on the group). Go on to the next run.
- When 2 of R1's first 3 graded cycles are K-misses, stop the round. D-HU's platform does not keep a group without its app, so no K arm is possible here. Report the K-miss cycles' HA, H2 and HB lines, run F1 and F2, and end.

### Finish (once, after the last run)

- **F1. Hand step (operator).** On D-POCO, turn Auto-connect back on for every network P7 changed. Confirm with `phone_unjoined` returning 1 (joined to a network) within 60 s.
- **F2.** Run `adb -s $PH shell settings put global stay_on_while_plugged_in <P6 value>`. With the app stopped, restore `settings.xml` from `$EV/settings.bak` the way `late-listener-poke` round 1 did, and check it reads back byte-identical. If that fails, use the root path in `rig-quirks/units/D-HU.md`.

## 8. Do not re-run

`late-listener-poke` round 1 settled these on this pair: the phone redials a late listener every 5.2 to 5.8 s while HFP is held; the poke stands down while HFP is held; the 90 s escalation is not reached. Out of scope here: the 1 s Type 3 delay (the next round, with code), boot and ACC start time, and the phone joined to the rig's access point.

## 9. Report back

1. Skips out of counted cycles for R1 against R2: does a kept group save the join scan with the phone's screen on?
2. The same for R3 against R4, with the screen off.
3. JS and BT medians for each run, and the AGE of every cycle that skipped the scan.
