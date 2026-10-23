# native-join-scan - round 3 brief

**Queue:** units D-HU (head unit) and D-POCO (phone). D-MOTO stays plugged in with its Bluetooth **off**. D-SAM and D-HP are not used. One build: the candidate `feat/native-join-scan` at `afc0b507`.
**Time:** 4 runs, 16 cycles in all. Round 2 cycles started about 65 s apart. A D1 or U1 cycle also waits up to 60 s for the app to exit, and a D1 cycle waits up to 30 s more for the phone's WiFi. About 30 min of cycles, plus 15 min for the build and 15 min for preparation and finish. About 60 min in total.
**Run names:** K1, D1, U1 and B1 are this round's runs. Round 2's runs are always written "round 2's R1" and so on.

## 1. Build and baseline

- **Candidate:** branch `feat/native-join-scan` on `fork`, SHA `afc0b507`, three commits on `22392a15` (`main`). History was not rewritten since round 2, which ran `ebc9c0f5`, its parent.
- **Build:** `git -C <wt> fetch fork feat/native-join-scan && git -C <wt> checkout --detach afc0b507 && git -C <wt> rev-parse --short HEAD` must print `afc0b507`. Then `$RIG_TOOLKIT/rig_thermal.sh wait 70` and `HUR_DIR=<wt> MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh`.
- **Save the installed APK first** (P0), so F3 can put it back: `P=$(adb -s 27870808938846 shell pm path com.andrerinas.headunitrevived | head -1 | sed 's/^package://' | tr -d '\r')`, then `adb -s 27870808938846 pull "$P" $EV/base-installed.apk`. Record its md5. Round 2 left `fb608e0ee92fbfb7d1d1d7542bc89b2b` installed.
- **Install** (P9): `adb -s 27870808938846 shell am force-stop com.andrerinas.headunitrevived`, then `adb -s 27870808938846 install -r -d <apk>`. Run `$RIG_TOOLKIT/apk_identity.sh` and record the md5.
- **What the candidate changes:**
  - `661b268f` removes the fixed 1 s pause before Type 3.
  - `ebc9c0f5` rewords a KDoc only.
  - `afc0b507` keeps the WiFi Direct group up through a service destroy that the user did not ask for. The next start then reads the group. A start that finds a group on a band or channel the setting no longer allows replaces it.
- **JVM suite on the dev host at `afc0b507`:** 3317 tests, 0 failures, 0 errors (run 2026-10-17). The rig runs no unit tests.

## 2. What this is and why

Round 2 (`native-join-scan-round2-results.md`) measured two things on `main` and on `ebc9c0f5`:

- **A kept group with the phone left on it saves 4.1 s of bring-up.** On `main` the phone's TCP connection came in 0.48 s after its Type 2, inside the 1 s pause. So the head unit never wrote Type 3 (`HL` = 1 in 5 of 5).
- **The candidate writes Type 3 at once.** On a kept group it now goes out about 0.5 s before the phone's connection, while the phone is already on the network. Credentials sent to a phone on a live session make it re-associate. Round 2 did not run this pairing.

This round asks three questions:

1. **K1: does the no-pause build keep the kept-group gain of round 2's R1?** The point of the round. The phone must not re-associate, and accept to SSL must stay near round 2's 1.5 s.
2. **D1 and U1: does the new destroy keep work, and does a user exit still remove the group?** `kill-on-disconnect` stops the service with `stopService` after a session end. After a link loss, the group must stay up for the next start (D1). After the user's own disconnect, it must go (U1).

The runs go in the order K1, D1, U1, B1. U1 ends with no group, so B1's seed makes a fresh 5 GHz group, and B1's 2.4 GHz group comes last.
3. **B1: does a start replace a kept group that the band setting no longer allows?**

## 3. What is different this round

- **One build, the candidate.** No baseline APK runs; round 2 measured `main`.
- **D1 switches the phone's WiFi off and on.** It is the only way here to end a session without a user exit. Rounds 1 and 2 forbade it. Only D1's end step does it. **No earlier round proves that a phone WiFi-off ends a Native session on this pair**, so D1's seed `c0` is also that proof (exit 11 rule in D1). Do not force-stop Android Auto on the phone as a fallback: it can latch a wireless-setup state that only `pm clear` releases.
- **D1 and U1 run with `kill-on-disconnect` on.** The app exits about 0.5 s after each session ends. The next cycle's launch starts a new process. The other runs write it off.
- **B1 writes `wifi-direct-band` = 2 (2.4 GHz only) for one cycle**, then ends with an exit. It is the last run, and F2 restores the settings.
- **A cycle's end decides the next cycle's arm**, as in round 2. The seed `c0` sets the state and is not graded.
- **All deciding spans are on the head unit's clock.** The phone's association lines decide K1's re-association check.
- **The host writes the extractor** (`njs3_extract.py`), as round 2's host wrote `njs2_extract.py`. It greps §6's fixed strings over the windows in "Extract" below.
- **Exit 7 and exit 10 can occur only in K1**, the one run with `GATE=on`. No run uses `GATE=off`, so exit 7 cannot occur at all.

## 4. Settings keys

Round 2's 13 keys, plus two, written by `njs3_keys.sh` at the start of every cycle and read back. `BAND` and `KILL` come from the cycle's environment (§7).

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
| `<boolean name="wifi-direct-stable-identity" value="true" />` | the gate of the keep and the adopt |
| `<int name="wifi-direct-band" value="BAND" />` | 0, except B1 c1 (2) |
| `<boolean name="kill-on-disconnect" value="KILL" />` | `true` in D1 and U1, `false` otherwise |

`enable-car-launcher` must read `false` (round 2's backup has it). A car launcher turns `kill-on-disconnect` off.

Before the first run, diff `settings.xml` against a fresh backup and state the delta in Setup notes.

## 5. Shared commands

Source the toolkit's `rig_devices.sh` before `njs_env.sh`, so `$RIG_TOOLKIT` is set.

**njs_env.sh** is round 2's file with one change: `EV` names round 3.

```bash
export PKG=com.andrerinas.headunitrevived
export HU=27870808938846 PH=4f4027e9 MOTO=ZY22GC3BM4
export EV=$HOME/Coding/StudioProjects/rig-data/rounds/native-join-scan-round3
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
hu_gone() {  # 0 when the app process has exited within $1 s
  for i in $(seq 1 "$1"); do adb -s $HU shell pidof $PKG >/dev/null || return 0; sleep 1; done; return 1; }
```

- **The launch** is `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`. **Do not send `ACTION_START_WIRELESS_SCAN` after it** (template §4 step 4).
- **The phone** leaves the Bluetooth link only by `svc`, never by airplane mode. **Run `svc wifi` on the phone only in D1's end step.**

**hfp_state.sh** and **njs_bonded.sh** are round 2's files, unchanged:

```bash
#!/bin/bash
# usage: hfp_state.sh <out-file>; the section is "Profile: HeadsetClientService", and MACs show only their last two octets
adb -s "$HU" shell dumpsys bluetooth_manager > /tmp/njs-bt.txt
awk '/^Profile: HeadsetClientService/{f=1;print;next} f&&/^Profile:/{f=0} f' /tmp/njs-bt.txt > "$1"
TAIL=$(echo "$POCO" | awk -F: '{print $(NF-1)":"$NF}')
if grep -aiF "$TAIL" "$1" | grep -aq 'state=Connected'; then echo CONNECTED; else echo NOT; fi
```

```bash
#!/bin/bash
adb -s 27870808938846 shell settings get secure bluetooth_name
adb -s 27870808938846 shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices'
adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices'
```

**njs3_keys.sh** is round 2's `njs_keys.sh` with the two new keys:

```bash
#!/bin/bash
# usage: BAND=<0|2> KILL=<true|false> njs3_keys.sh <out-file>
source "$(dirname "$0")/njs_env.sh"
: "${BAND:?}" "${KILL:?}"
adb -s $HU shell am force-stop $PKG
python3 -I $RIG_TOOLKIT/set_hu_settings_host.py $HU scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:int:log-level:2 scalar:int:native-driver-selection-mode:0 scalar:string:native-preferred-device-mac:$POCO scalar:string:last-connected-native-mac:$POCO scalar:int:native-aa-wake-damage-verdict:0 scalar:int:native-aa-wake-armings-without-session:0 setclear:auto-start-bt-macs scalar:int:onboarding-version:2 setclear:native-poke-bt-macs scalar:boolean:native-poke-all-paired:false scalar:boolean:wifi-direct-stable-identity:true scalar:int:wifi-direct-band:$BAND scalar:boolean:kill-on-disconnect:$KILL >/dev/null
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml | grep -aE 'name="(wifi-connection-mode|native-ap-transport|log-level|native-driver-selection-mode|native-preferred-device-mac|last-connected-native-mac|native-aa-wake-damage-verdict|native-aa-wake-armings-without-session|auto-start-bt-macs|onboarding-version|native-poke-bt-macs|native-poke-all-paired|wifi-direct-stable-identity|wifi-direct-band|kill-on-disconnect|enable-car-launcher)"' -A2 | grep -av '^--' > "$1"
for k in '<int name="wifi-connection-mode" value="3" />' '<set name="native-poke-bt-macs" />' '<boolean name="native-poke-all-paired" value="false" />' '<boolean name="wifi-direct-stable-identity" value="true" />' '<set name="auto-start-bt-macs" />' "<int name=\"wifi-direct-band\" value=\"$BAND\" />" "<boolean name=\"kill-on-disconnect\" value=\"$KILL\" />"; do
  grep -aqF "$k" "$1" || exit 8; done
grep -aqF '<boolean name="enable-car-launcher" value="true" />' "$1" && exit 8
exit 0
```

## 6. The deciding lines

Every head-unit string below was checked with `git grep -F` at `afc0b507`. The lines marked "runtime" build their text from a variable and were read verbatim in round 2's D-HU captures. Grep every capture with `-a`.

Head unit capture, `$EV/<run>-c<n>-hu.txt`:

| Id | Fixed string | Source |
|---|---|---|
| H1 | `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | `NativeAaHandshakeManager.kt` |
| H2 | `createGroup SUCCESS!` | `WifiDirectManager.kt`, a new group |
| HA | `before this bring-up; reading it instead of tearing it down.` | `WifiDirectManager.kt`, the adopt |
| HB | `removeGroup before recreate failed` | with `reason=BUSY` it means no group existed |
| HI | `WifiDirectManager: group identity ssid=` | read the value after `bssid=` |
| HM | `the band or channel setting no longer allows, so it is replaced.` | `WifiDirectManager.kt`, the band check (new) |
| HK | `keeping the group up through this stop, for the next start to read.` | `WifiDirectManager.kt`, the keep (new) |
| HX | `killProcessOnDestroy is true. Triggering System.exit(0).` | `AapService.kt`, the kill-on-disconnect exit |
| HE | `Disconnect action received.` | `AapService.kt`, the user's disconnect |
| HP | `(the platform took it down)` | runtime, inside `WifiDirectManager: the group is being removed (...)`. The platform removed the group by itself |
| HQ | `AapTransport quitting` | `AapTransport.kt`, the session's transport ends |
| H3 | `NativeAA: Connection accepted from` | the phone's Bluetooth dial accepted |
| T2 | `NativeAA: [RX] Received Type 2 ` | runtime |
| HC | `NativeAA: [TX] Sending WifiInfoResponse (Type 3) with full credentials` | once per Type 3 send |
| T3 | `NativeAA: [TX] Wrote TYPE 3 ` | runtime. Type 3 written to the socket |
| INC | `WirelessServer: Incoming connection detected` | the phone's TCP connection |
| H4 | `SSL handshake complete` | `AapSslContext.kt` |
| HL | `the phone's session landed while Type 3 was pending` | credentials skipped |
| H9 | `NativeAA: No wake poke phone selected, so nothing is poked.` | proof the poke is off |
| H6 | `NativeAA: Calling socket.connect()` | a real poke dial, must be 0 |
| H10 | `WppTcpServer: [TX] WifiVersionRequest` | a TCP-route contact |
| H12 | `MATCH! Starting AapService` | discard rule |
| E | `AutomationMarker: <run>-c<n>-end` | the end of the session part of the cycle |

`with full credentials in 1000ms` has 0 matches at `afc0b507`. A capture that holds it ran the wrong APK: stop the round and run `apk_identity.sh`.

Phone capture, `$EV/<run>-c<n>-ph.txt`. These are Android's own lines. All four were read in round 2's D-POCO captures: `apChannel:` holds MHz (5180 to 5785 there), and `CTRL-EVENT-CONNECTED` printed once on each fresh join:

| Id | Fixed string | Meaning |
|---|---|---|
| J-req | `/WifiNetworkFactory(`, on a line that also holds `got request NetworkRequest` and `LITERAL: DIRECT-` | the phone asks for our group |
| J-conn | `User initiated connect to network: "DIRECT-` | the phone connects. Read `apChannel:` on it |
| PC | `CTRL-EVENT-CONNECTED` | the phone's WiFi completes an association |
| PD | `CTRL-EVENT-DISCONNECTED` | the phone's WiFi loses an association |
| S-start | `scan start scan id` | the WiFi driver starts a scan |
| P-dial | `Creating rfcomm socket for device: <HU_MAC>` | Android Auto dials our listener |

## 7. Runs

### Preparation (once, before K1)

- **P0.** Save the installed APK (§1). Record its md5.
- **P1.** Run `adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $EV/settings.bak`. Diff it against round 2's backup and record the delta.
- **P2.** On D-MOTO, run `adb -s $MOTO shell svc bluetooth disable`. Read it back as off.
- **P3.** Run `njs_bonded.sh`. Confirm `POCO` and `HU_MAC`. If D-POCO's list holds another rig head unit's radio, stop and ask the operator to forget it on D-POCO.
- **P4.** Run `adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a versionName` and record it.
- **P5.** Run `adb -s $PH shell dumpsys window | grep mCurrentFocus`. If the focus is not the launcher, run `adb -s $PH shell input keyevent KEYCODE_HOME`.
- **P6.** Run `adb -s $PH shell settings get global stay_on_while_plugged_in` and record the value for F2.
- **P7. Hand step (operator).** On D-POCO, open Settings, then Wi-Fi. For `Pegue Cdesta`, and for every other saved network in range, turn **Auto-connect** off. Then select the joined network, then select **Disconnect**. Do not forget any network. Leave WiFi on. Wait 45 s, then run `phone_unjoined $EV/p7-wifi.txt` twice, 45 s apart. Both must return 0. If either returns anything else, send the saved line to the host and stop.
- **P8.** Run `adb -s $HU shell dumpsys wifi | grep -iE "mWifiInfo" | head -2` and record D-HU's own station state.
- **P9.** Build and install the candidate (§1). Record its md5.

### The cycle

**njs3_cycle.sh** takes the run id, the cycle number, the end and the hold in seconds. `BAND` and `KILL` come from the environment. The ends:

- `ks`: force-stop. The group stays up and the phone stays on it.
- `r`: `headunit://exit`, then force-stop. The group is removed.
- `kd`: the phone's WiFi goes off, the session ends as a link loss, and `kill-on-disconnect` ends the app. Then the phone's WiFi comes back on.
- `ku`: `ACTION_DISCONNECT` (the user's disconnect), and `kill-on-disconnect` ends the app.

Exit codes:

- 0: graded.
- 3: no HFP.
- 4: a radio or the screen would not switch, including the phone's WiFi after a `kd` end.
- 5: thermal.
- 6: the phone joined another network.
- 7: the gate needs the phone off our group, and it was on it.
- 8: a settings key read back wrong.
- 9: `dumpsys wifi` printed no `mWifiInfo` line.
- 10: the gate needs the phone on our group, and it was not (a **KS-miss**).
- 11: `kd` or `ku`, and the app did not exit within 60 s.

The gate for the graded cycles comes from `GATE`: `on` (phone on our group), `off` (phone off it) or `any`. Seeds (`c0`, `c0b`) check only exits 6 and 9.

```bash
#!/bin/bash
set -u
source "$(dirname "$0")/njs_env.sh"
: "${RIG_TOOLKIT:?}" "${EV:?}" "${RIG_ROUND:?}" "${POCO:?}" "${HU_MAC:?}" "${BAND:?}" "${KILL:?}" "${GATE:?}"
RUN=$1; C=$2; END=$3; HOLD=$4
T=$EV/$RUN-c$C
$RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || exit 5
$RIG_TOOLKIT/rig_thermal.sh watch $EV/thermal-$RUN-c$C.log & echo $! > $T-th.pid

stopcap() { kill "$(cat $T-hu.pid)" "$(cat $T-ph.pid)" "$(cat $T-th.pid)" 2>/dev/null; }
finish() { # end the way this cycle ends; captures stop after the app has gone
  rc=0
  case "$END" in
    ks) adb -s $HU shell am force-stop $PKG ;;
    r)  if adb -s $HU shell pidof $PKG >/dev/null; then
          adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit"; sleep 3; fi
        adb -s $HU shell am force-stop $PKG ;;
    kd) mark "$RUN-c$C-phone-wifi-off"; adb -s $PH shell svc wifi disable
        hu_gone 60 || rc=11
        adb -s $PH shell svc wifi enable; w=0
        for i in $(seq 1 30); do [ "$(adb -s $PH shell settings get global wifi_on | tr -d '\r')" = 1 ] && w=1 && break; sleep 1; done
        [ $w = 0 ] && { adb -s $PH shell svc wifi enable; sleep 10; [ "$(adb -s $PH shell settings get global wifi_on | tr -d '\r')" = 1 ] || rc=4; }
        sleep 10; mark "$RUN-c$C-phone-wifi-on"
        adb -s $HU shell am force-stop $PKG ;;
    ku) send ACTION_DISCONNECT
        hu_gone 60 || rc=11
        adb -s $HU shell am force-stop $PKG ;;
  esac
  sleep 2; stopcap; return $rc; }
ends() { finish; r=$?; [ $r = 4 ] && { echo NO_PHONE_WIFI > $T-gate.txt; exit 4; }; [ $r = 11 ] && { echo NO_EXIT > $T-gate.txt; exit 11; }; exit 0; }

# 1. phone off the Bluetooth link; its WiFi stays on
phone_bt off || { echo NO_BT_OFF > $T-gate.txt; kill "$(cat $T-th.pid)"; exit 4; }
phone_screen on || { echo NO_SCREEN > $T-gate.txt; kill "$(cat $T-th.pid)"; exit 4; }

# 2. keys written with the app stopped, captures on
$RIG_ROUND/njs3_keys.sh $T-keys.txt || { echo KEYS_WRONG > $T-gate.txt; kill "$(cat $T-th.pid)"; exit 8; }
adb -s $HU logcat -G 16M; adb -s $PH logcat -G 16M
adb -s $HU logcat -c; adb -s $PH logcat -c
stdbuf -oL adb -s $HU logcat -v time > $T-hu.txt & echo $! > $T-hu.pid
stdbuf -oL adb -s $PH logcat -v time > $T-ph.txt & echo $! > $T-ph.pid

# 3. phone Bluetooth on while our app is stopped
mark "$RUN-c$C-phone-on"
phone_bt on || { echo NO_BT_ON > $T-gate.txt; stopcap; exit 4; }
ok=0; for i in $(seq 1 30); do [ "$($RIG_ROUND/hfp_state.sh $T-hfc.txt)" = CONNECTED ] && ok=1 && break; sleep 2; done
if [ $ok = 0 ]; then mark "$RUN-c$C-no-hfp"; echo NO_HFP > $T-gate.txt; stopcap; exit 3; fi
mark "$RUN-c$C-hfp-up"
sleep "$HOLD"

# 4. state gates just before the launch; seeds check only 6 and 9
phone_unjoined $T-phwifi.txt; j=$?
[ $j = 1 ] && { echo PHONE_JOINED > $T-gate.txt; stopcap; exit 6; }
[ $j = 3 ] && { echo NO_WIFIINFO > $T-gate.txt; stopcap; exit 9; }
if [[ $C != 0* ]]; then
  if [ "$GATE" = off ] && [ $j = 2 ]; then echo PHONE_ON_GROUP > $T-gate.txt; stopcap; exit 7; fi
  if [ "$GATE" = on ] && ! { [ $j = 2 ] && grep -aqF 'DIRECT-' $T-phwifi.txt; }; then echo KS_MISS > $T-gate.txt; stopcap; exit 10; fi
fi
adb -s $PH shell dumpsys power | grep -a -m1 'mWakefulness=' > $T-screen.txt
mark "$RUN-c$C-launch"
adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity

# 5. wait for the session, then end
waitline $T-hu.txt 'SSL handshake complete' 90 || mark "$RUN-c$C-no-ssl"
sleep 5
phone_unjoined $T-phwifi2.txt
send ACTION_LOG_MARKER --es text "$RUN-c$C-end"
sleep 1
ends
```

Put `njs_env.sh`, `hfp_state.sh`, `njs_bonded.sh`, `njs3_keys.sh` and `njs3_cycle.sh` in `$RIG_ROUND` and make them executable.

### Extract for every cycle (Haiku returns these numbers, and Sonnet grades)

Head unit capture:
- after the `-launch` RIGMARK: the first timestamp of H1, H3, T2, T3, INC and H4;
- after the `-launch` RIGMARK and before E: the counts of H2, HA, HB, HM, HC, H3, H4, HL, H6, H9, H10 and H12, and of `AapRead: Magic Garbage detected in header`;
- after E: the counts of HK, HX, HE, HP and HQ, and the timestamps of the first HQ and HX;
- the first HB line in full, and the value after `bssid=` in the first HI line (or "none");
- the timestamp of every `RIGMARK` line and of E.

Phone capture:
- after the `-launch` RIGMARK and before E: the counts of J-req, PC, PD and S-start; the first J-conn line's timestamp and its `apChannel:` value (or "none");
- the count of P-dial lines between `-hfp-up` and `-launch`, and the MAC in each, compared with `HU_MAC` (`-aiF`).

Also give the exit code, `$T-gate.txt` if it exists, and the contents of `$T-screen.txt`, `$T-phwifi.txt`, `$T-phwifi2.txt` and `$T-keys.txt`.

Derived, on the head unit clock:
- **AS** (accept to SSL) = H4 minus H3. Round 2's kept-group median was 1.55 s, its removed-group median on this build 3.91 s.
- **BT** (bring-up time) = H4 minus H1.
- **TI** (Type 3 to the phone's connection) = INC minus T3, or "n/a" when T3 is absent.

A cycle **re-associates** when PC ≥ 1 or PD ≥ 1 between the `-launch` RIGMARK and E, or when the BSSID in `$T-phwifi2.txt` differs from the one in `$T-phwifi.txt`.

"The last HI BSSID" means the `bssid=` value of the last HI line printed in an earlier cycle of the same run, seed included. Compare BSSIDs without regard to case.

### Counted cycles and discard rules

A graded cycle counts only if:
- it exited 0;
- `$T-screen.txt` shows `Awake`;
- P-dial ≥ 1, and every P-dial names `HU_MAC`;
- H9 ≥ 1 and H6 = 0;
- H4 = 1.

Discard a cycle and run it again once if it has an H12, more than one H4, an H10 or a `Magic Garbage` line. **Before any rerun, for any reason (a discard, or an exit 3, 4, 6, 10 or 11), keep the first try's files:** run `for f in $T-*; do mv "$f" "${f/$T-/$T-try1-}"; done`, with `T=$EV/<run>-c<n>`. If the rerun is also discarded, do not count it. A run with fewer counted cycles than its "needs" line below is INCONCLUSIVE.

**"The end before `cN`"** (D1 and U1) is the end step of the latest capture set, try1 files included, whose end ran before `cN`'s `-launch` RIGMARK.

### K1: kept group, phone stays on it, no Type 3 pause (**the point of the round**)

`BAND=0 KILL=false GATE=on`. Seed `c0` with end `ks`, then `c1` to `c5` with end `ks`: `$RIG_ROUND/njs3_cycle.sh K1 <n> ks 30`. Needs 4 counted cycles.

Arm check per graded cycle: HA = 1, H2 = 0, and the BSSID in `$T-phwifi.txt` equals the last HI BSSID. A cycle that fails it is a K-miss: do not count it.

A counted cycle is **good** when AS ≤ 2.5 s and it does not re-associate. It is **bad** otherwise.

- **Instrument check first:** the seed `c0` makes a fresh join, so its phone capture must have PC ≥ 1. If it has PC = 0, mark K1 INCONCLUSIVE ("association lines absent") and go to D1.
- **PASS:** 3 or more counted cycles are good.
- **FAIL:** 2 or more counted cycles are bad. Report every phone line from `-launch` to E that holds `wpa_supplicant` or `WifiNetworkFactory`, for those cycles.
- **Report per cycle:** AS, BT, TI, HC, T3 present or not, HL, PC, PD, J-req, S-start.

An exit 10 leaves the phone off the group, so a plain rerun exits 10 again. Record it, then run a new seed and repeat the same cycle number: `njs3_cycle.sh K1 0b ks 30`, then `njs3_cycle.sh K1 <n> ks 30`. For a second exit 10 the seed is `0c`. After two exit 10s in K1, counted after their seed and repeat, mark K1 INCONCLUSIVE and go to D1.

### D1: service destroy after a link loss keeps the group

`BAND=0 KILL=true GATE=any`. Seed `c0` with end `kd`, then `c1` to `c3` with end `kd`: `$RIG_ROUND/njs3_cycle.sh D1 <n> kd 30`. Needs 3 counted cycles.

Per graded cycle `cN`, read two captures: cycle `cN-1` (the end) and cycle `cN` (the start that follows):
- **Kept:** `cN-1` has HK = 1 and HX = 1, and `cN` has HA = 1, H2 = 0, and its HI BSSID equals the last HI BSSID before it.
- **Lost:** `cN-1` has HX = 1 and HK = 0, or `cN` has H2 ≥ 1.
- **Platform removal:** `cN-1` has HP ≥ 1 after E. The platform took the group down before the destroy, so HK = 0 says nothing about the code. Mark that cycle INCONCLUSIVE, not Lost.

- **PASS:** every counted cycle is Kept.
- **FAIL:** any counted cycle is Lost.
- **Report per cycle:** HK, HX, HQ and HP of the end before it; HA, H2, the BSSID, AS and BT; the time from the `-phone-wifi-off` RIGMARK to HQ and to HX.

An exit 11 means the session did not end, or `kill-on-disconnect` did not fire, within 60 s of the phone's WiFi going off. Report whether HQ printed. Run the cycle again once. If the seed `c0` exits 11, run it again as `c0b`. Two exit 11s in a row make D1 INCONCLUSIVE ("a phone WiFi-off does not end the session"); go to U1.

### U1: the user's disconnect with kill-on-disconnect still removes the group

`BAND=0 KILL=true GATE=any`. Seed `c0` with end `ku`, then `c1` to `c3` with end `ku`: `$RIG_ROUND/njs3_cycle.sh U1 <n> ku 30`. Needs 3 counted cycles.

Per graded cycle `cN`, read `cN-1` and `cN` as in D1:
- **Removed:** `cN-1` has HE = 1, HX = 1 and HK = 0, and `cN` has HA = 0, H2 ≥ 1, and the HB line holds `reason=BUSY`.
- **Kept by mistake:** `cN-1` has HK ≥ 1, or `cN` has HA = 1.

- **PASS:** every counted cycle is Removed.
- **FAIL:** any counted cycle is Kept by mistake.

The seed `c0` starts on D1's kept group, if D1 kept one. Its own HA is not graded.

Exit 11 here follows D1's rule.

### B1: a kept group on a band the setting no longer allows (last run)

Seed `c0`: `BAND=0 KILL=false GATE=any $RIG_ROUND/njs3_cycle.sh B1 0 ks 30`. U1 ended with no group, so the seed makes a fresh group, and the phone joins it and stays on it. Then `c1`: `BAND=2 KILL=false GATE=any $RIG_ROUND/njs3_cycle.sh B1 1 r 30`. Needs 1 counted cycle.

- **PASS:** c1 has HM = 1, HA = 0, H2 ≥ 1, and its J-conn `apChannel:` value is below 3000 (a 2.4 GHz group).
- **FAIL:** c1 has HA = 1 (the 5 GHz group was read), or HM = 0 while the seed's J-conn `apChannel:` was above 4900.
- **INCONCLUSIVE:** the seed's J-conn `apChannel:` is "none" or below 3000 (nothing mismatched), or c1 has HM = 1 with a J-conn `apChannel:` above 4900 (the platform did not give a 2.4 GHz group), or any other outcome.
- **After c1:** run `adb -s $HU shell dumpsys wifi | grep -a -m1 mWifiInfo` and compare D-HU's station with P8. Report it. A 2.4 GHz group beside a 5 GHz station can stand the station down.

### Stop rule

- After one exit 3 or 4, run the cycle again once. When two cycles in a row of one run exit 3 or 4, mark that run INCONCLUSIVE and go to the next run.
- Two exit 5s in a row stop the round.
- An exit 8 or 9 stops the round at once. Send `$T-keys.txt` or the `dumpsys wifi` head to the host.
- An exit 6: run the cycle again once. A second exit 6 in the same run stops the round; send `$T-phwifi.txt` to the host.
- An exit 7 cannot occur. If it does, stop and send `$T-gate.txt` to the host.
- An exit 4 from a `kd` end means the phone's WiFi did not come back. Send `adb -s $PH shell settings get global wifi_on` to the host before the rerun.

### Finish (once, after the last run)

- **F3.** Put back the APK P0 saved: `adb -s $HU shell am force-stop $PKG`, then `adb -s $HU install -r -d $EV/base-installed.apk`. Run `apk_identity.sh`. The md5 must be P0's.
- **F1. Hand step (operator).** On D-POCO, turn Auto-connect back on for every network P7 changed. Confirm with `phone_unjoined` returning 1 within 60 s.
- **F2.** Run `adb -s $PH shell settings put global stay_on_while_plugged_in <P6 value>`. With the app stopped, restore `settings.xml` from `$EV/settings.bak` the way round 2 did, and check it reads back byte-identical.

## 8. Do not re-run

Settled on this pair: a kept group with the phone left on it saves 4.1 s of bring-up on `main`. Removing the Type 3 pause cuts accept to SSL from 5.2 s to 3.9 s on a removed group. About 1 in 7 removed-group joins misses the first scan and takes 9 to 10 s, on both builds (`native-join-scan` round 2). Out of scope: the screen-off phone, a phone joined to another access point, and boot or ACC start time.

## 9. Report back

1. K1: how many counted cycles re-associate, and the AS and BT medians, against round 2's 1.55 s and 3.73 s.
2. D1 and U1: Kept and Removed counts, with the HK, HX, HE and HP counts behind them, and whether the phone WiFi-off ended the session (HQ).
3. B1: HM, HA and the 2.4 GHz `apChannel:` value.
