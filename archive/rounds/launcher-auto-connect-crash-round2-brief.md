# launcher-auto-connect-crash, round 2 brief: the dark recreate is held, and the wake replay asks before it rebuilds

On the transfer branch this file is `launcher-auto-connect-crash-round2-brief.md`.

## 0. Pre-flight and time budget

The round runs unattended. It has no hand step. Only D-HU and D-POCO take part. D-SAM, D-HP and D-MOTO are not used. D-HU is **not** the HOME app in this round.

Run the pre-flight and read these values. Put each one in Setup notes.

```bash
HU=27870808938846; PH=4f4027e9; PKG=com.andrerinas.headunitrevived
rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt                                               # must print PREFLIGHT OK
adb -s $PH shell settings get secure bluetooth_address                                    # expect DC:B7:2E:5E:4E:59
adb -s $HU shell dumpsys bluetooth_manager | grep -a -iA20 "Bonded devices" | grep -a -c "DC:B7:2E:5E:4E:59"   # expect 1 or more
adb -s $HU shell stat -c %U:%G /data/data/$PKG/shared_prefs /data/user_de/0/$PKG/shared_prefs   # expect u0_a176:u0_a176 twice
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb devices | grep -c ZY22GC3BM4                                                          # D-MOTO plugged in: 1
```

- If the phone's address is not `DC:B7:2E:5E:4E:59`, use the address it printed everywhere this brief writes that one.
- If D-HU has no bond to D-POCO, stop the round. Every run needs it, and pairing is a hand step.
- If a `stat` line is `root:root`, run `adb -s $HU shell chown u0_a176:u0_a176 <that directory>` and read it again.
- If D-MOTO is plugged in, put it in airplane mode for the whole round and read it back. Round 1 did not, and could not say that D-MOTO did not interfere:

```bash
adb -s ZY22GC3BM4 shell cmd connectivity airplane-mode enable; sleep 8
adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'             # expect state: OFF
```

| Step | Wall clock |
|---|---|
| R0: two builds, unit tests, identity | 50 min |
| Backups, settings, scripts | 10 min |
| R7-PC on the control APK (5 cycles) | 30 min |
| Install the candidate, settings again | 5 min |
| R6B (1 cycle) | 6 min |
| R6 (3 cycles) | 15 min |
| R7 (5 cycles) | 30 min |
| Restore, zip, upload | 10 min |
| **Total** | **about 2 h 40 min** |

## 1. Build and baseline

| | Where | SHA | Gate |
|---|---|---|---|
| Candidate | fork `o-jcardenass/open-headunit`, branch `fix/launcher-auto-connect-crash` | `e5f818b8849904d5f26cfa76b498eca85594cbda` | 2775 JVM tests, 0 failures |
| Control | the candidate with one patch (below) that removes the replay veto | `e5f818b88499-dirty` | not tested; the APK is the control |
| Base | `main` | `145a0c762f0a87386ca63b54518ea5e861b290be` | not built |

**History was rewritten since round 1.** Round 1 ran `0b8e7d59`. The branch was autosquashed and force-pushed, so `0b8e7d59` is no longer on it. The candidate is still two commits on the base.

```bash
git fetch fork fix/launcher-auto-connect-crash
git checkout e5f818b8849904d5f26cfa76b498eca85594cbda
git log --oneline 145a0c762f0a87386ca63b54518ea5e861b290be..HEAD | wc -l      # 2
git status --short | wc -l                                                     # 0
```

If the scratch worktree has no `local.properties`, copy it from the main checkout, as round 1 did.

**Candidate build.** Run `th_wait 70` (section 5) first. Build with `build_hur_cool.sh` (or `build_hur.sh` under `--max-workers=2`). Copy the APK out of `apks/` at once, to `lacc2-candidate.apk`. Run `run_unit_tests.sh`.

**Control build.** Save the patch below as `control.patch` in the round folder. Then, in the same worktree:

```bash
git apply --check control.patch && git apply control.patch
git status --short                       # exactly: M app/src/main/java/com/andrerinas/openheadunit/aap/AapService.kt
th_wait 70; build_hur_cool.sh            # copy the APK out of apks/ at once, to lacc2-control.apk
git checkout -- app/src/main/java/com/andrerinas/openheadunit/aap/AapService.kt
git status --short | wc -l               # 0
```

`control.patch` removes the replay veto and nothing else:

```diff
diff --git a/app/src/main/java/com/andrerinas/openheadunit/aap/AapService.kt b/app/src/main/java/com/andrerinas/openheadunit/aap/AapService.kt
index 30e200b4..8d047009 100644
--- a/app/src/main/java/com/andrerinas/openheadunit/aap/AapService.kt
+++ b/app/src/main/java/com/andrerinas/openheadunit/aap/AapService.kt
@@ -2388,15 +2388,6 @@ class AapService : Service() {
                 AppLog.i("WakeDetect: a session came up during the settle, so the held wireless bring-up is dropped")
                 return@launch
             }
-            val launcher = wifiLauncherManager.active as? WifiLauncherNative
-            WirelessSleepHold.replayVeto(
-                force,
-                networkComingUp = launcher?.networkComingUp(),
-                attemptInFlight = launcher?.handshakeManager?.isAttemptInFlight(),
-            )?.let { reason ->
-                AppLog.i("WakeDetect: the held wireless bring-up is dropped, because $reason")
-                return@launch
-            }
             wifiLauncherManager.setActiveFromSettings(force = force)
         }
     }
```

**R0 PASS when all hold:**

- The candidate gives 2775 tests and 0 failures, counted from the JUnit XML, with `WirelessSleepHoldTest` among them. A build or test failure stops the round.
- `git apply --check` succeeds. If it fails, the control does not exist: mark R7-PC UNTESTABLE, run the rest, and say so.
- `md5sum lacc2-candidate.apk lacc2-control.apk` prints two different values.
- The DEX string check below prints 1 or more for the candidate and 0 for the control:

```bash
for a in lacc2-candidate.apk lacc2-control.apk; do printf '%s ' $a; unzip -p $a 'classes*.dex' | strings | grep -cF 'the held wireless bring-up is dropped, because'; done
```

Install and identity, each time an APK goes on D-HU (template section 5: `adb install -r`, never uninstall):

```bash
adb -s $HU install -r <apk>
adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') live.apk; md5sum <apk> live.apk   # the two md5s match
send ACTION_QUERY_STATE        # the reply's commit is e5f818b88499 (candidate) or e5f818b88499-dirty (control)
```

## 2. What this is and why it exists

A reporter runs Open Headunit as the HOME app on a car head unit, in Native AA mode. When the ignition goes off, the unit sleeps. When the ignition comes back after 10 to 20 s, the unit does a full reboot instead of a fast resume.

The reporter's logs show this order. The unit starts to sleep, our process dies, and Android starts HOME again. The new process starts `AapService`, which arms the Native stack within about 2 s with no screen check. The arm creates a P2P group, opens the RFCOMM listeners, pokes the phone and can switch WiFi back on. The next boot is a cold boot. The best-ranked cause is that this bring-up during the sleep stops the fast resume. The rig cannot prove that cause, because no rig unit has a fast-resume sleep. A separate reporter round tests the reboot.

The candidate holds every automatic bring-up while the screen is off and replays it on the first `SCREEN_ON`. Round 1 measured that on `0b8e7d59`: R5 and R6 passed. Round 1 also found one gap (R6B FAIL): the armed stack's 60 s join watchdog recreated the group 45 s into the dark.

This round tests the three changes since round 1:

1. **The recreate hold.** `WifiDirectManager.recoverNativeGroup` now holds its recreate while the screen is off. It logs one line and latches a forced replay for the wake.
2. **The watchdog re-arm.** The held branch arms the join watchdog again. So if the wake does not rebuild the group, the watchdog recreates it later, with the screen on.
3. **The replay veto.** A forced replay at the wake now asks the same two questions as the Bluetooth auto-start: is the network still coming up, and is a handshake attempt in flight. If either is true, the replay drops itself and logs why. Without this, a phone arriving with the screen can get two forced rebuilds 1.5 s apart, and the second one tears down the group while the phone joins.

## 3. What is different about this round

- **D-HU is not the HOME app.** R5 does not run again, so the HOME role is not needed. `enable-car-launcher` stays `false`.
- **A control APK replaces a second branch.** The pre-veto commit is not on the fork. The control is the candidate with the veto removed by `control.patch`, so the two APKs differ in that one block only.
- **R7-PC can come back INCONCLUSIVE.** The double trigger needs the Bluetooth auto-start to force a rebuild in the first 1.5 s after `SCREEN_ON`. In round 1, every Bluetooth auto-start against an armed stack was vetoed (`nothing to do, a handshake attempt is already in flight`). If that happens again, no cycle reaches the double trigger. That is a result, not a failure. Section 7 says how R7 is graded then.
- **No `su` on D-HU.** adbd runs as uid 0, so every root step is a plain `adb shell`. The helpers in section 5 already do this.
- **The phone's radios are set explicitly and read back with `dumpsys`.** Airplane mode does not turn D-POCO's Bluetooth off if it was on (round 1, Setup note 5). D-POCO's WiFi can come back on after one `svc wifi disable` (`rig-quirks/units/D-POCO.md`). The helpers retry and read back.
- **Markers are shell markers** (`smark`), as in round 1. A broadcast marker would start a stopped package.
- **The discard rules of template section 4 do not apply as written.** `MATCH! Starting AapService` is the trigger in R6 and R7, and our own poke raises it in R6B and R7. A second group is part of what R6B, R7 and R7-PC grade. Report every `MATCH!` count instead.
- **Log level is INFO (`log-level=2`).** The plan asked for VERBOSE. Every line in section 6 is `AppLog.i`, `AppLog.w` or `AppLog.e`, and VERBOSE adds only per-message lines that no run grades. The capture streams to a file, so the ring buffer does not matter.
- **Host thermal gate:** `th_gate` before each run, with the `th_pkg` fix from round 1 (section 5). Re-run a run once if a timing condition failed and its throttle delta was above 0 or `thermal_max` was 90C or more.

## 4. Settings keys

Write the keys with the app stopped, through `hu_put` (section 5). Read every key back before the launch. Write them again after each `adb install -r`, because an install can wipe `settings.xml` (`rig-quirks/topics/tooling.md`). At the start, save `settings-backup-HU.xml` and diff it against `launcher-auto-connect-crash-round1/settings-backup-HU.xml`. Put the delta in Setup notes. Do not touch the audio keys: the rig's audio settings are a deliberate worst case.

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `log-level` | int | `2` | INFO, section 3 |
| `native-driver-selection-mode` | int | `0` | no selector countdown |
| `onboarding-version` | int | `2` | no wizard over the launch |
| `native-poke-all-paired` | boolean | `false` | poke D-POCO only |
| `native-poke-bt-macs` | string set | `DC:B7:2E:5E:4E:59` | the wake list |
| `auto-start-bt-macs` | string set | `DC:B7:2E:5E:4E:59` | the Bluetooth auto-start trigger in R6 and R7 |
| `last-connected-native-mac` | string | `DC:B7:2E:5E:4E:59` | repoint a stale target |
| `native-preferred-device-mac` | string | `DC:B7:2E:5E:4E:59` | the same |
| `native-aa-wake-damage-verdict` | int | delete | a latched verdict stands every poke down |
| `video-profile-starvation-cap` | boolean | delete | a latch that caps the session |
| `enable-car-launcher` | boolean | `false` | D-HU is not HOME in this round |

`ACTION_LOG_MARKER` is not used. `allow-external-configuration` is not needed.

```bash
MAC=DC:B7:2E:5E:4E:59
KEYS="int:wifi-connection-mode=3 int:log-level=2 int:native-driver-selection-mode=0 int:onboarding-version=2 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MAC set:auto-start-bt-macs=$MAC str:last-connected-native-mac=$MAC str:native-preferred-device-mac=$MAC del:native-aa-wake-damage-verdict del:video-profile-starvation-cap bool:enable-car-launcher=false"
```

**The Bluetooth auto-start reads a second file.** `AutoStartReceiver` reads `auto-start-bt-macs` from `/data/user_de/0/com.andrerinas.headunitrevived/shared_prefs/settings_device_protected.xml`. `App.onCreate` copies it there at each launch. After the first launch on each APK, run `mirror_check`. If it does not print the MAC, mark R6, R7 and R7-PC UNTESTABLE.

## 5. Helpers and verbs

Every action on the app is a verb or a named OS-level step.

| Step | Command | Note |
|---|---|---|
| identity | `send ACTION_QUERY_STATE` | record the reply's `commit` |
| stop the service | `send ACTION_EXIT` | finishes the activities, stops `AapService` |
| launch | `adb -s $HU shell am start -n $MAIN` | template section 3, launch only. It arms the stack as an automatic bring-up, not a user one |
| marker | `smark <label>` | shell marker on both units |

OS-level steps, allowed: `input keyevent 223` (SLEEP), `224` (WAKEUP), `wm dismiss-keyguard`, `dumpsys power`, `svc bluetooth`, `svc wifi`, `cmd connectivity airplane-mode`, `log`.

**Do not arm with `ACTION_START_WIRELESS_SCAN`.** It is a user request, and a stack the user started is exempt from the recreate hold. Every run arms with the plain launch above.

Make the folder `hur-wifi-test-scripts/launcher-auto-connect-crash-round2/`. Copy `ohu_setkeys.py` into it from `launcher-auto-connect-crash-round1/`. Save `control.patch` (section 1) and the two files below beside it. List all of them in Setup notes. If a script does not match the real line format, fix it, say so in Setup notes, and carry on.

**`lacc2_lib.sh`**:

```bash
# lacc2_lib.sh : source it after setting HU, PH, OUT, MAC and cd $OUT.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
send() { a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
# smark <label> : a shell marker on D-HU, and on D-POCO while a phone capture runs. It never touches the app.
smark() { adb -s "$HU" shell log -p w -t RIGMARK "$1"; [ -n "$PCAPPID" ] && adb -s "$PH" shell log -p w -t RIGMARK "$1"; sleep 0.3; }
nl() { echo $(( $(wc -l < "$CAP") + 1 )); }
# waitfor <secs> <fixed-string> <from-line> : 0 when the string appears at or after <from-line>
waitfor() { local end=$((SECONDS+$1)); while [ $SECONDS -lt $end ]; do
  tail -n +"$3" "$CAP" | grep -aqF -- "$2" && return 0; sleep 0.5; done; return 1; }
awake() { adb -s "$HU" shell dumpsys power | grep -a -m1 -o 'mWakefulness=[A-Za-z]*'; }
# screen_off : SLEEP, then prove it. Prints ASLEEP, or NOT_ASLEEP after one retry.
screen_off() { adb -s "$HU" shell input keyevent 223; sleep 3
  awake | grep -q Awake && { adb -s "$HU" shell input keyevent 223; sleep 3; }
  awake | grep -q Awake && echo NOT_ASLEEP || echo ASLEEP; }
screen_on() { adb -s "$HU" shell input keyevent 224; sleep 1; adb -s "$HU" shell wm dismiss-keyguard; }
svc_up() { adb -s "$HU" shell dumpsys activity services $PKG | grep -ac 'ServiceRecord.*AapService'; }
mirror_check() { adb -s "$HU" shell cat /data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml | grep -aoF "$MAC"; }
# hu_put <base.xml> <spec...> : host-side edit, root copy, read back. The app must be stopped. adbd is root: no su.
hu_put() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  OWN=$(adb -s "$HU" shell stat -c %u:%g /data/data/$PKG | tr -d '\r')
  printf 'cp /data/local/tmp/new.xml /data/data/%s/shared_prefs/settings.xml\nchown %s /data/data/%s/shared_prefs/settings.xml\nchmod 660 /data/data/%s/shared_prefs/settings.xml\n' "$PKG" "$OWN" "$PKG" "$PKG" > hu_put.sh
  adb -s "$HU" push new.xml /data/local/tmp/new.xml >/dev/null; adb -s "$HU" push hu_put.sh /data/local/tmp/hu_put.sh >/dev/null
  adb -s "$HU" shell sh /data/local/tmp/hu_put.sh
  adb -s "$HU" shell cat /data/data/$PKG/shared_prefs/settings.xml | grep -aoE '(wifi-connection-mode|log-level|native-driver-selection-mode|onboarding-version|native-poke-all-paired|native-poke-bt-macs|auto-start-bt-macs|last-connected-native-mac|native-preferred-device-mac|native-aa-wake-damage-verdict|video-profile-starvation-cap|enable-car-launcher)[^/]*'; }
# Phone radios. Verify with dumpsys, never with settings get global. Each prints one status word.
ph_bt_state() { adb -s "$PH" shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:' | awk '{print $2}' | tr -d '\r'; }
ph_wifi_state() { adb -s "$PH" shell dumpsys wifi | grep -a -m1 'Wi-Fi is' | awk '{print $3}' | tr -d '\r'; }
# phone_bt_set on|off : up to 3 tries. Prints PHONE_BT_ON/OFF, or PHONE_BT_NOT_<want> and returns 1.
phone_bt_set() { local i st; for i in 1 2 3; do
  if [ "$1" = on ]; then adb -s "$PH" shell svc bluetooth enable; else adb -s "$PH" shell svc bluetooth disable; fi; sleep 4
  st=$(ph_bt_state)
  if [ "$1" = on ] && [ "$st" = ON ]; then echo "PHONE_BT_ON try=$i"; return 0; fi
  if [ "$1" = off ] && [ "$st" = OFF ]; then echo "PHONE_BT_OFF try=$i"; return 0; fi
  done; echo "PHONE_BT_NOT_$1 state=$st"; return 1; }
# phone_wifi_off : airplane mode on, WiFi off, read back. Up to 3 tries. Bluetooth is set after it, never before.
phone_wifi_off() { local i; for i in 1 2 3; do
  adb -s "$PH" shell cmd connectivity airplane-mode disable; sleep 3
  adb -s "$PH" shell cmd connectivity airplane-mode enable; sleep 8
  adb -s "$PH" shell svc wifi disable; sleep 3
  [ "$(ph_wifi_state)" = disabled ] && { echo "PHONE_WIFI_OFF try=$i"; return 0; }; done
  echo PHONE_WIFI_NOT_OFF; return 1; }
# trigger : phone WiFi and Bluetooth on, then the head unit's screen on at once. Prints the gap in seconds.
trigger() { adb -s "$PH" shell 'svc wifi enable; svc bluetooth enable'; local t1=$(date +%s.%N)
  adb -s "$HU" shell input keyevent 224; local t2=$(date +%s.%N)
  echo "bt_to_screen_s=$(awk -v a="$t1" -v b="$t2" 'BEGIN{printf "%.3f", b-a}')"; sleep 1; adb -s "$HU" shell wm dismiss-keyguard; }
phone_restore() { adb -s "$PH" shell cmd connectivity airplane-mode disable; sleep 2
  adb -s "$PH" shell svc wifi enable; adb -s "$PH" shell svc bluetooth enable; sleep 4
  echo "phone wifi=$(ph_wifi_state) bt=$(ph_bt_state)"; }
# cap_start <RUN> : D-HU capture and the D-POCO capture. Every run in this round captures the phone.
cap_start() { CAP=$OUT/$1.hu.logcat; PCAP=$OUT/$1.phone.logcat
  adb -s "$HU" logcat -G 16M; adb -s "$HU" logcat -c; stdbuf -oL adb -s "$HU" logcat -v time > "$CAP" & CAPPID=$!
  adb -s "$PH" logcat -G 16M; adb -s "$PH" logcat -c; stdbuf -oL adb -s "$PH" logcat -v time > "$PCAP" & PCAPPID=$!
  sleep 1; }
cap_stop() { kill $CAPPID $PCAPPID 2>/dev/null; wait $CAPPID $PCAPPID 2>/dev/null; PCAPPID=
  pgrep -x adb -a | grep -c ' logcat '; }          # must print 0, else kill the leftover by its pid
# Host thermal. th_pkg prints one number: the CPU package temperature in C.
th_pkg() { if [ -x ../rig_thermal.sh ]; then ../rig_thermal.sh status | grep -oE '(^|[^_a-z])pkg=[0-9]+' | head -1 | grep -oE '[0-9]+$'; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
th_gate() { th_wait 75 || { echo "$(date +%T) HOST_TOO_HOT ${RUN:-run}" | tee -a "$OUT/thermal.log"; return 3; }
  [ -n "$THPID" ] && kill $THPID 2>/dev/null; th_watch "$OUT/${RUN:-run}.thermal" & THPID=$!
  echo "$(date +%T) ${RUN:-run} start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"; }
th_report() { awk -F'[ =C]+' '{p=$3; t=$5; if(p>m)m=p; if(NR==1)t0=t} END{print "thermal_max=" m "C throttle_delta=" t-t0}' "$OUT/$1.thermal"; }
```

Before the first run, check `th_pkg` prints exactly one number: `th_pkg | wc -w` must print 1.

**`lacc2_grade.py`** prints one JSON object per cycle. The host grades from it. Run it as `python3 lacc2_grade.py <hu.logcat> <cycle-prefix> <phone.logcat> > <cycle-prefix>.json`.

```python
#!/usr/bin/env python3
# lacc2_grade.py <hu-capture> <prefix> [phone-capture] : counts, times and checks per marker window, as JSON.
import sys, re, json
from collections import Counter
from datetime import datetime

def has(*parts): return lambda l: all(p in l for p in parts)
S = {
    'create': has('AapService creating...'),
    'hold': has('WifiLauncher: wireless bring-up held while the screen is off. '),
    'held_rec': has('WifiDirectManager: Native AA recovery (', ') held while the screen is off. '),
    'recreate': has('WifiDirectManager: Native AA recovery (', '): recreate attempt '),
    'recovery_any': has('WifiDirectManager: Native AA recovery ('),
    'defer': has('join watchdog fired but a Bluetooth handshake or handoff is in flight'),
    'never_dialled': has('has not opened the Android Auto Bluetooth channel since this attempt started'),
    'radio_hold': has('WifiDirectManager: WiFi is off and the screen is off, so it is not switched on.'),
    'rearm': has('WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force='),
    'rearm_true': has('WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=', 'force=true'),
    'dropped': has('WakeDetect: the held wireless bring-up is dropped, because '),
    'session_drop': lambda l: ('WakeDetect: a session is up, so the wireless bring-up held while the unit was asleep is dropped' in l
                               or 'WakeDetect: a session came up during the settle, so the held wireless bring-up is dropped' in l),
    'screen_off': has('WakeDetect: SCREEN_OFF'),
    'screen_on': has('WakeDetect: SCREEN_ON (screen was off for '),
    'init': has('WifiLauncher: Initializing WiFi Mode: '),
    'init_native': lambda l: 'WifiLauncher: Initializing WiFi Mode: ' in l and l.rstrip().endswith('NATIVE'),
    'quiet_host': has('WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any...'),
    'create_attempt': has('WifiDirectManager: Attempting createGroup for Native AA (Attempt '),
    'group_ok': has('createGroup SUCCESS!'),
    'group_formed': has('WifiDirectManager: Group formed. Owner: '),
    'busy': lambda l: ('createGroup failed (' in l and 'BUSY (System is busy' in l) or 'WifiDirectManager: Chip is BUSY, retrying in 2s...' in l,
    'listen': has('NativeAA: ACTIVELY LISTENING on Android Auto UUID'),
    'poke': has('NativeAA: Attempting active poke to device: '),
    'accept': has('NativeAA: Connection accepted from '),
    'wifi_enable': has('Attempting to enable (attempt '),
    'match': has('MATCH! Starting AapService via Bluetooth Auto-start...'),
    'bt_auto': has('AapService: Bluetooth auto-start: '),
    'bt_force': has('AapService: Bluetooth auto-start: ', 'forceRearmWireless=true'),
    'fgs_fail': has('Failed to start AapService from background'),
    'ssl': has('SSL handshake complete'),
    'throughput': has('Throughput over '),
    'fatal': has('FATAL EXCEPTION'),
    'our_proc': has('Process: com.andrerinas.headunitrevived'),
}
TS = re.compile(r'^(\d\d-\d\d \d\d:\d\d:\d\d\.\d{3})')
CAR = re.compile(r'[VDIWEF]/CAR\.(SERVICE|SETUP|PROJECTION)\b')
WPP = re.compile(r'[VDIWEF]/GH\.WPP')
SETUP = re.compile(r'WIRELESS_SETUP_[A-Z_]+')
KEYS = ('start', 'off', 'held', 'bt-on', 'wake', 'short', 'end')

def load(p): return open(p, errors='replace').read().splitlines()
def t(line):
    m = TS.match(line)
    return datetime.strptime('2026-' + m.group(1), '%Y-%m-%d %H:%M:%S.%f') if m else None
def msg(line): return line.split('): ', 1)[-1].rstrip()
def mark(lines, label):
    for i, l in enumerate(lines):
        if 'RIGMARK' in l and l.rstrip().endswith(': ' + label): return i
    return None
def first(lines, pred, a, b):
    if a is None: return None
    for i in range(a, b):
        if pred(lines[i]): return i
    return None
def secs(lines, i, j):
    if i is None or j is None: return None
    x, y = t(lines[i]), t(lines[j])
    return round((y - x).total_seconds(), 3) if x and y else None
def windows(lines, p):
    m = {k: mark(lines, p + '-' + k) for k in KEYS}
    end = m['end'] if m['end'] is not None else len(lines)
    W = {}
    if m['start'] is not None: W['all'] = (m['start'], m['short'] if m['short'] is not None else end)
    d0 = m['off'] if m['off'] is not None else m['bt-on']
    if d0 is not None and m['wake'] is not None: W['dark'] = (d0, m['wake'])
    if m['wake'] is not None:
        W['short'] = (m['wake'], m['short'] if m['short'] is not None else end)
        W['after'] = (m['wake'], end)
    return m, W

hu, p = load(sys.argv[1]), sys.argv[2]
m, W = windows(hu, p)
out = {'prefix': p, 'markers_found': [k for k in KEYS if m[k] is not None]}
for w, (a, b) in W.items():
    out[w] = {k: sum(1 for i in range(a, b) if f(hu[i])) for k, f in S.items()}
    out[w]['bt_auto_lines'] = [msg(hu[i]) for i in range(a, b) if S['bt_auto'](hu[i])][:8]
if 'dark' in W:
    a, b = W['dark']
    h = first(hu, S['hold'], a, b)
    out['hold_before_any_arm'] = h is not None and all(first(hu, S[k], a, h) is None for k in ('init', 'create_attempt', 'group_ok', 'poke', 'listen'))
if 'after' in W:
    a, b = W['after']; sa, sb = W['short']
    son = first(hu, S['screen_on'], a, b)
    out['first_screen_on'] = msg(hu[son]) if son is not None else None
    rr = first(hu, S['rearm'], a, b)
    out['rearm_line'] = msg(hu[rr]) if rr is not None else None
    out['screen_on_to_rearm_s'] = secs(hu, son, rr)
    dec = first(hu, lambda l: S['init_native'](l) or S['dropped'](l), rr, b)
    out['decision'] = None if dec is None else ('dropped' if S['dropped'](hu[dec]) else 'init_native')
    out['decision_line'] = msg(hu[dec]) if dec is not None else None
    out['screen_on_to_decision_s'] = secs(hu, son, dec)
    ssl = first(hu, S['ssl'], a, b)
    out['wake_to_ssl_s'] = secs(hu, a, ssl)
    out['throughput_after_ssl'] = ssl is not None and first(hu, S['throughput'], ssl, b) is not None
    forces = [i for i in range(a, b) if S['bt_force'](hu[i])]
    out['bt_force_offsets_s'] = [secs(hu, son, i) for i in forces] if son is not None else []
    early = next((i for i in forces if son is not None and secs(hu, son, i) is not None and 0 <= secs(hu, son, i) < 1.5), None)
    out['double_reached'] = early is not None
    out['drop_follows_force'] = (first(hu, S['dropped'], early, b) is not None) if early is not None else None
    qh = [i for i in range(sa, sb) if S['quiet_host'](hu[i])]
    gaps = [g for g in (secs(hu, qh[k], qh[k + 1]) for k in range(len(qh) - 1)) if g is not None]
    out['quiet_host_min_gap_s'] = min(gaps) if gaps else None
    out['screen_on_to_recovery_s'] = secs(hu, son, first(hu, S['recovery_any'], son, b))
if len(sys.argv) > 3:
    ph = load(sys.argv[3])
    pm, PW = windows(ph, p)
    out['phone'] = {w: {
        'car': sum(1 for i in range(a, b) if CAR.search(ph[i])),
        'gh_wpp': sum(1 for i in range(a, b) if WPP.search(ph[i])),
        'setup': dict(Counter(x for i in range(a, b) for x in SETUP.findall(ph[i])))} for w, (a, b) in PW.items()}
print(json.dumps(out, indent=1))
```

## 6. The lines that decide every run

Every app string below was checked with `grep -F` against `app/src/main` at `e5f818b8`. Each one is `AppLog.i`, `AppLog.w` or `AppLog.e`, so `log-level=2` carries all of them. `lacc2_grade.py` matches each one in the D-HU capture, inside the marker windows below. Where the script needs two parts of one line, both parts are listed; the part between them is filled in at runtime.

| Line (fixed string, or parts of one line) | What it means |
|---|---|
| `WifiDirectManager: Native AA recovery (` + `) held while the screen is off. ` | **fix 1**: the 60 s recreate is held in the dark. Logged once per hold. The runtime line reads `WifiDirectManager: Native AA recovery (no phone joined within 60s) held while the screen is off. The screen coming on re-arms the group.` |
| `WifiDirectManager: Native AA recovery (` + `): recreate attempt ` | a recreate that went ahead. Round 1's R6B FAIL line |
| `join watchdog fired but a Bluetooth handshake or handoff is in flight` | the watchdog put itself off for 60 s |
| `has not opened the Android Auto Bluetooth channel since this attempt started` | the watchdog stood down because the phone never dialled |
| `WifiLauncher: wireless bring-up held while the screen is off. ` | a bring-up held in the dark |
| `WakeDetect: re-arming the wireless bring-up held while the unit was asleep (force=` | the replay starts. The line ends `force=true` or `force=false`, then the trigger |
| `WakeDetect: the held wireless bring-up is dropped, because ` | **fix 3**: the veto dropped the replay. The reason follows |
| `WakeDetect: a session is up, so the wireless bring-up held while the unit was asleep is dropped` and `WakeDetect: a session came up during the settle, so the held wireless bring-up is dropped` | a session dropped the replay |
| `WakeDetect: SCREEN_OFF`, `WakeDetect: SCREEN_ON (screen was off for ` | the screen state, as the service saw it |
| `WifiLauncher: Initializing WiFi Mode: ` | a bring-up. The script counts the lines that end in `NATIVE` |
| `WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any...` | a group bring-up starts |
| `WifiDirectManager: Attempting createGroup for Native AA (Attempt `, `createGroup SUCCESS!` | a group is asked for, a group is up |
| `WifiDirectManager: Group formed. Owner: ` | a group is up, created or read from before. The arm signal |
| `createGroup failed (` + `BUSY (System is busy`, and `WifiDirectManager: Chip is BUSY, retrying in 2s...` | two creates fought over the chip |
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID`, `NativeAA: Attempting active poke to device: `, `NativeAA: Connection accepted from ` | listeners open, a wake poke, the phone dialled |
| `Attempting to enable (attempt ` | the app switched the head unit's WiFi on |
| `MATCH! Starting AapService via Bluetooth Auto-start...` | the Bluetooth auto-start trigger |
| `AapService: Bluetooth auto-start: ` | what the service did with it. A forced rebuild reads `BtAutoStartActions(clearUserExit=..., forceRearmWireless=true, ...)`; a veto reads `nothing to do, <reason>.` |
| `Failed to start AapService from background` | Android refused the service start |
| `SSL handshake complete`, `Throughput over ` | a session and a picture. At INFO only the `AapSslContext` line prints, once per session |

System lines (not in `app/src`): `FATAL EXCEPTION` with `Process: com.andrerinas.headunitrevived` is a crash of ours.

Phone lines, D-POCO capture, by tag, because Gearhead's strings drift: `CAR.SERVICE`, `CAR.SETUP` or `CAR.PROJECTION` (the phone's car service ran a projection), and `GH.WPP` (its wireless handshake). The script also counts every `WIRELESS_SETUP_<NAME>` token. Those are reported, not graded, because no `WIRELESS_SETUP_` name is verified on Gearhead 17.9.

**Windows**, all by `RIGMARK` shell markers, with `<c>` the cycle prefix:

| Window | From | To |
|---|---|---|
| `all` | `<c>-start` | `<c>-short` (or `<c>-end` if there is no `short`) |
| `dark` | `<c>-off` | `<c>-wake` |
| `short` | `<c>-wake` | `<c>-short` |
| `after` | `<c>-wake` | `<c>-end` |

## 7. Runs

Start each run with `RUN=<id>; th_gate`. Write the results file after each run. Order: R7-PC on the control, then the candidate install, then R6B, R6, R7.

```bash
HU=27870808938846; PH=4f4027e9; MAC=DC:B7:2E:5E:4E:59
OUT=~/hur-wifi-test-scripts/launcher-auto-connect-crash-round2; mkdir -p $OUT; cd $OUT; source ./lacc2_lib.sh   # adjust to where hur-wifi-test-scripts/ lives
adb -s $HU shell am force-stop $PKG
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > settings-backup-HU.xml
```

**Install the control** (section 1), then:

```bash
adb -s $HU shell am force-stop $PKG; hu_put settings-backup-HU.xml $KEYS
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 15; mirror_check      # must print the MAC
send ACTION_QUERY_STATE                                                     # commit e5f818b88499-dirty
send ACTION_EXIT; sleep 5
```

### The R7 cycle (used by R7-PC and R7)

The point: a stack armed before the sleep latches a forced replay in the dark. Then the phone arrives and the screen comes on together. Without the veto, the Bluetooth auto-start rebuilds at once and the replay rebuilds again 1.5 s later.

For run `<R>` (`R7-PC` or `R7`) and cycle `n`:

```bash
RUN=<R>; th_gate
phone_wifi_off || echo UNTESTABLE_PHONE_WIFI
phone_bt_set on || echo UNTESTABLE_PHONE_BT
cap_start <R>-c$n; smark <R>-c$n-start; L=$(nl)
adb -s $HU shell am start -n $MAIN >/dev/null
waitfor 40 'WifiDirectManager: Group formed. Owner: ' $L || echo NO_ARM
sleep 15; smark <R>-c$n-off; screen_off                                       # must print ASLEEP
D=$(nl); waitfor 150 'held while the screen is off. ' $D || echo NO_DARK_HOLD
smark <R>-c$n-held; phone_bt_set off || echo UNTESTABLE_PHONE_BT
sleep 20
smark <R>-c$n-wake; W=$(nl); trigger                                           # prints bt_to_screen_s
sleep 30; smark <R>-c$n-short
waitfor 120 'SSL handshake complete' $W || echo NO_SSL
waitfor 60 'Throughput over ' $W || echo NO_PICTURE
sleep 10; smark <R>-c$n-end; cap_stop
python3 lacc2_grade.py $CAP <R>-c$n $PCAP > <R>-c$n.json
send ACTION_EXIT; sleep 5
```

The phone's WiFi stays off from the arm to the trigger, so the phone cannot join and the 60 s watchdog fires in the dark. `held while the screen is off. ` matches both hold lines, so `NO_DARK_HOLD` means that nothing latched.

**Void cycles.** A cycle is UNTESTABLE when `screen_off` printed `NOT_ASLEEP` or a phone radio read-back failed. A cycle is INCONCLUSIVE when `NO_ARM` or `NO_DARK_HOLD` printed, or when `dark.ssl` is 1 or more (a session formed in the dark). Run one extra cycle for each void cycle, at most 3 extra. **Stop rule:** stop after 5 cycles that are not void, or after 8 cycles in all.

**Per cycle, report:** `bt_to_screen_s`, `bt_force_offsets_s`, `double_reached`, `short.init_native`, `decision`, `decision_line`, `short.busy`, `quiet_host_min_gap_s`, `after.ssl`, `wake_to_ssl_s`, `dark.held_rec`, `dark.hold`, `dark.defer`, every `bt_auto_lines` entry of `short`, and `phone.after.setup`. In this cycle `decision` names the first rebuild or drop after the replay line. That rebuild can be the Bluetooth auto-start's, so R7 does not grade `decision`.

### R7-PC: positive control, the double trigger on the control APK. Five cycles

Run the R7 cycle with `<R>` = `R7-PC`, `n` = 1 to 5.

**R7-PC PASS:** in at least 1 non-void cycle, `short.init_native` is 2 or more. That shows the double rebuild exists without the veto.

**R7-PC INCONCLUSIVE:** no non-void cycle has `short.init_native` of 2 or more. Then say that the double trigger was not reached, and give `double_reached` and the `short` `bt_auto_lines` for every cycle.

The control has no veto, so `short.dropped` is 0 by construction. A value above 0 means the wrong APK is installed: stop and check the identity.

**Install the candidate** (section 1), then:

```bash
adb -s $HU shell am force-stop $PKG; hu_put settings-backup-HU.xml $KEYS
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 15; mirror_check      # must print the MAC
send ACTION_QUERY_STATE                                                     # commit e5f818b88499
send ACTION_EXIT; sleep 5
```

### R6B: guard. The armed stack's 60 s recreate in the dark. One cycle

Round 1's R6B failed here. The condition is the same as round 1's, now with the candidate that holds the recreate. The phone's Bluetooth is on and its WiFi is off, so the poke loop runs and no phone can join.

```bash
RUN=R6B; th_gate
phone_wifi_off || echo UNTESTABLE_PHONE_WIFI
phone_bt_set on || echo UNTESTABLE_PHONE_BT
cap_start R6B-c1; smark R6B-c1-start; L=$(nl)
adb -s $HU shell am start -n $MAIN >/dev/null
waitfor 40 'WifiDirectManager: Group formed. Owner: ' $L || echo NO_ARM
sleep 15; smark R6B-c1-off; screen_off                                         # must print ASLEEP
sleep 130                                                                      # the dark soak: room for one deferred watchdog
smark R6B-c1-wake; screen_on
sleep 55; smark R6B-c1-short
sleep 25; smark R6B-c1-end; cap_stop
python3 lacc2_grade.py $CAP R6B-c1 $PCAP > R6B-c1.json
send ACTION_EXIT; sleep 5
```

`NO_ARM` or `NOT_ASLEEP` makes the run UNTESTABLE.

**PASS when all hold:**

- Dark window: `screen_off` is 1 or more.
- Dark window: `group_ok` is 0, `create_attempt` is 0, `recreate` is 0 and `init` is 0.
- Dark window: `held_rec` is exactly 1.
- After window: `rearm` is exactly 1, and `rearm_line` contains `force=true`.
- After window: `decision` is `init_native` or `dropped`.
- Short window: `init_native` is 1 or less.
- All window: `group_ok` is 2 or less.
- If `decision` is `dropped`: `screen_on_to_recovery_s` is 75 or less. That line shows the re-armed watchdog.
- Every window: `our_proc` is 0.
- Phone: `phone.all.car` is 0.

**INCONCLUSIVE, then repeat once:** dark `held_rec` is 0 and dark `recreate` is 0, with dark `defer` or dark `never_dialled` 1 or more. The watchdog never reached the recreate. If the repeat is the same, the run is INCONCLUSIVE.

Report as numbers: dark `match`, `poke`, `accept`, `defer`, `radio_hold`, `screen_on_to_decision_s`, `decision_line`, and every `bt_auto_lines` entry of `dark` and `short`.

What a PASS would look like if the change did nothing: it cannot. Without fix 1, `recreate` and `group_ok` are 1 in the dark (round 1), and a watchdog that never fires gives `held_rec` 0, which is INCONCLUSIVE.

### R6: a Bluetooth auto-start into a stopped service while the screen is off. Three cycles

The same as round 1's R6, with the phone's Bluetooth set off and read back before the trigger. Here the service starts in the dark with no launcher, so the veto has nothing to ask and the replay must rebuild. This run guards that the veto does not drop a replay that has nothing under way.

Per cycle `n` = 1, 2, 3:

```bash
RUN=R6; th_gate
adb -s $HU shell am start -n $MAIN >/dev/null; sleep 10
phone_wifi_off || echo UNTESTABLE_PHONE_WIFI
phone_bt_set off || echo UNTESTABLE_PHONE_BT
send ACTION_EXIT; sleep 3; adb -s $HU shell input keyevent 3; sleep 2; svc_up    # svc_up must print 0
cap_start R6-c$n; smark R6-c$n-start
smark R6-c$n-off; screen_off                                                   # must print ASLEEP
sleep 5; smark R6-c$n-bt-on; L=$(nl); phone_bt_set on || echo UNTESTABLE_PHONE_BT
waitfor 60 'MATCH! Starting AapService' $L || echo NO_ARRIVAL
sleep 30                                                                       # the dark soak
smark R6-c$n-wake; W=$(nl); screen_on
waitfor 10 'WifiLauncher: Initializing WiFi Mode: ' $W || echo NO_REARM
phone_restore
waitfor 120 'SSL handshake complete' $W || echo NO_SSL
waitfor 60 'Throughput over ' $W || echo NO_PICTURE
sleep 10; smark R6-c$n-end; cap_stop
python3 lacc2_grade.py $CAP R6-c$n $PCAP > R6-c$n.json
send ACTION_EXIT; sleep 5
```

If `svc_up` prints more than 0, send `ACTION_EXIT` again and wait 5 s. If it still prints more than 0, the cycle is UNTESTABLE. If `screen_off` prints `NOT_ASLEEP`, or a phone read-back failed, the cycle is UNTESTABLE. If dark `match` is 0, the cycle is INCONCLUSIVE (`NO_ARRIVAL`). Run one extra cycle for each void cycle, at most 2 extra. **Stop rule:** stop after 3 cycles with an arrival, or after 5 cycles in all.

**PASS for a cycle when all hold:**

- Dark window: `match` 1 or more, `fgs_fail` 0, `create` 1 or more.
- Dark window: `hold` is exactly 1, and `hold_before_any_arm` is true.
- Dark window: `init`, `create_attempt`, `group_ok`, `listen`, `poke`, `accept` and `wifi_enable` are each 0.
- After window: `first_screen_on` reads `WakeDetect: SCREEN_ON (screen was off for -1s)`.
- After window: `rearm` is exactly 1, `rearm_line` contains `force=true`, and `screen_on_to_rearm_s` is 2.0 or less.
- After window: `decision` is `init_native` and `screen_on_to_decision_s` is 5.0 or less. `decision` `dropped` is also a PASS on this clause, if `wake_to_ssl_s` holds below.
- After window: `wake_to_ssl_s` is 120 or less, and `throughput_after_ssl` is true.
- Every window: `our_proc` is 0.
- Phone: `phone.dark.car` is 0 and `phone.after.car` is 1 or more.

**R6 PASS:** every cycle with an arrival passes, and there are at least 2 of them.

Report every `bt_auto_lines` entry of `after`, and `after.init_native`.

### R7: the double trigger on the candidate. Five cycles. **This is the point of the round**

Run the R7 cycle with `<R>` = `R7`, `n` = 1 to 5. Same void rules and stop rule as above.

**PASS for a non-void cycle when all hold:**

- Dark window: `group_ok` is 0 and `recreate` is 0.
- After window: `rearm` is exactly 1, and `rearm_line` contains `force=true`.
- Short window: `init_native` is 1 or less.
- If `double_reached` is true: `drop_follows_force` is true.
- Short window: `busy` is 0.
- `quiet_host_min_gap_s` is null or 1.0 or more.
- After window: `ssl` is exactly 1, `wake_to_ssl_s` is 120 or less, and `throughput_after_ssl` is true.
- Every window: `our_proc` is 0.
- Phone: `phone.dark.car` is 0 and `phone.after.car` is 1 or more.

**R7 verdict:**

- **FAIL** when any non-void cycle fails a condition.
- **PASS** when every non-void cycle passes, R7-PC is PASS, and at least 1 R7 cycle has `double_reached` true.
- **INCONCLUSIVE** when every non-void cycle passes but R7-PC is not PASS or no R7 cycle has `double_reached` true. Say which of the two. The conditions above still guard against a second rebuild, but the veto itself was not exercised.

What a PASS would look like if the change did nothing: on the control, a cycle with `double_reached` true gives `short.init_native` 2 and no `dropped` line. That is why R7 PASS needs R7-PC to show the double rebuild first.

### End of the round

```bash
send ACTION_EXIT; sleep 3; adb -s $HU shell am force-stop $PKG
hu_put settings-backup-HU.xml                     # the round's starting settings, no keys changed
# if ohu_setkeys.py refuses an empty key list, push settings-backup-HU.xml as root the same way hu_put does
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml | cmp - settings-backup-HU.xml && echo RESTORED
phone_restore                                      # expect phone wifi=enabled bt=ON
adb -s ZY22GC3BM4 shell cmd connectivity airplane-mode disable     # only if section 0 turned it on
```

Leave the candidate APK installed and say so in Setup notes. Leave D-HU's screen on.

## 8. Do not re-run

- **R5 and R5-PC** (a process death in the dark, then a wake). PASS 5 of 5 and 2 of 2 in round 1. Their replay was `force=false`, and the veto does not touch an unforced replay.
- **R6-PC** (a Bluetooth auto-start with the screen on). PASS in round 1. Nothing on that path changed.
- **R0 of round 1.** It identified `0b8e7d59`, which is gone.
- Out of scope, by decision: the reboot itself (no rig unit has a fast-resume sleep; the reporter round grades it), and the auto-connect delay (in Native mode it gates no radio action).

## 9. Report back

1. **R6B:** dark `group_ok`, dark `recreate` and dark `held_rec`, and the `decision` after the wake.
2. **R7:** the number of non-void cycles that passed, out of the non-void cycles. The number of cycles with `double_reached` true, and of those, how many have `drop_follows_force` true. The largest `short.init_native` and the total `short.busy`.
3. **R7-PC:** the number of non-void cycles with `short.init_native` of 2 or more.

Also give R6's cycles that reached `SSL handshake complete`, out of the arrival cycles.

Zip the captures, the JSON files and `control.patch` to `launcher-auto-connect-crash-round2-captures.zip`. Upload it to the existing release with `gh release upload rig-evidence-launcher-auto-connect-crash --repo o-jcardenass/open-headunit launcher-auto-connect-crash-round2-captures.zip`, and quote the sha256 in `launcher-auto-connect-crash-round2-results.md`.
