# automation-screen-disconnect, round 1 brief: end the session when external power is lost, and two screen verbs

Publish name on `transfer/rig-rounds`: `automation-screen-disconnect-round1-brief.md`. Results go in
`automation-screen-disconnect-round1-results.md`. Evidence goes in a new release
`rig-evidence-automation-screen-disconnect` as the asset
`automation-screen-disconnect-round1-captures.zip`.

One candidate build, no baseline build. Three units: D-HU as the head unit, D-POCO as the phone,
D-SAM for the screen-on route below API 33 and for one real cable pull. About 2.5 hours plus one
build.

| Run | What it proves | Unit |
|---|---|---|
| R0 | build, unit tests, APK identity | host |
| C0 | control: does D-HU's own ROM light the panel when power changes | D-HU |
| V1 | the screen verbs before the grant: the gate, the refusal, screen-on open to any caller | D-HU |
| R3 | power lost with no session: no timer, the poke loop keeps running; power back does nothing new | D-HU |
| R4 | switch off: no line, no timer, the session stays | D-HU + D-POCO |
| **R1** | **the point of the round: the session ends like Exit after the delay, the stack stays down, power back re-arms and a session forms** | D-HU + D-POCO |
| R2 | power back inside the delay cancels; a lost, back, lost flap keeps one timer | D-HU + D-POCO |
| R6 | screen row on, no grant: the warning, the panel stays lit, the session still ends | D-HU + D-POCO |
| R10 | the service stops inside the delay: no timer survives it | D-HU + D-POCO |
| T1 | the timer keeps its deadline while the panel is off and the SoC may suspend | D-HU + D-POCO |
| V3 | the screen verbs with the grant, no session | D-HU |
| V4 | the screen verbs during a session: screen-on brings back the projection, not the home screen | D-HU + D-POCO |
| R5, R7 | screen row on, grant held, delay at the minimum: the screen goes dark after the end, and lights again on power back | D-HU + D-POCO |
| S1 | screen-on below API 33: the wake lock alone lights the panel | D-SAM |
| S2 | a real cable pull gives the same event and the same line as the `dumpsys battery` lever | D-SAM |
| X | clean-up: remove the device admin grant, restore battery state and settings | D-HU |

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` |
|---|---|---|---|
| **C** (candidate) | `fork/feat/automation-screen-disconnect` | `5236a1b44855613124109dad3398322c2f522b1e` | `5236a1b44855` (a `-dirty` suffix from the copied `local.properties` is expected) |

C is `main` at `73b52b64` plus two commits: `95246f11` (Auto-disconnect: end the session when
external power is lost) and `5236a1b4` (Automation: ACTION_SCREEN_OFF and ACTION_SCREEN_ON). This is
the thread's first round, so no history was rewritten since a previous round. No baseline is
needed: the feature does not exist on `main`, and C0 is the control.

```bash
git fetch fork feat/automation-screen-disconnect
git rev-parse fork/feat/automation-screen-disconnect     # must print 5236a1b44855613124109dad3398322c2f522b1e
git rev-parse fork/feat/automation-screen-disconnect^{tree}   # must print 2d8df4fa69e8d2c7191a6336d8d4e801198aee78
```

If either line differs, stop the round.

### R0. Build and identity gate

Make `rig-data/rounds/automation-screen-disconnect-round1/` and set `OUT` to it. Make a worktree of C
the way the last round did (`wt-new.sh`), copy `local.properties` in, and wait for the host to cool
(`th_wait 70`). The contract:

```bash
cd <C worktree> && ./gradlew :app:assembleGithubDebug --max-workers=2 && mkdir -p $OUT/apk && cp app/build/outputs/apk/github/debug/*.apk $OUT/apk/C.apk
cd <C worktree> && ./gradlew :app:testGithubDebugUnitTest --max-workers=2
md5sum $OUT/apk/C.apk
for s in ExternalPowerDisconnectPolicy ScreenOffAdminReceiver ScreenOnRaisePolicy 'external-power disconnect'; do
  printf '%s %s\n' "$s" "$(unzip -p $OUT/apk/C.apk 'classes*.dex' | strings | grep -cF "$s")"; done
adb -s 27870808938846 install -r $OUT/apk/C.apk
adb -s 30041c35642d2200 install -r $OUT/apk/C.apk
```

**PASS when all hold:** the build succeeds; the unit tests report 3235 tests, 0 failures and 0
errors; each of the four symbols counts 1 or more; after each install `state` (section 5) shows
`commit` starting `5236a1b44855`. **A failure in R0 stops the round.**

## 2. What this is and why

A tablet that serves as a head unit has its own battery. When the car is switched off, the charger
loses power and nothing happens: the screen stays lit, and the Android Auto session stays up for as
long as the phone is in range. Two users asked for the same thing from two sides. One wants the
session to end after a delay, because a full battery can drop and regain charger power by itself.
The other wants the screen to go dark when the car stops and to light again, with a new connection,
when the car starts.

The candidate adds three settings rows and two automation verbs.

- **`external-power-disconnect`** (switch) and **`external-power-disconnect-delay-seconds`**. On
  `POWER_DISCONNECTED` with a session up, a timer starts. Power back before the timer ends cancels
  it. When the timer ends, the app reads the switch and the plug state again. If the switch is still
  on and the plug is still out, it ends the session with the Exit button's path (`isUserExit = true`,
  ByeBye sent) and keeps the wireless bring-up down. The delay is clamped to 5 to 3600 s. The 5 s
  floor keeps our own screen-off out of the existing "power lost beside a screen-off" inference,
  which uses a 5 s window.
- **`external-power-screen-off`** (switch). With the device admin grant (`force-lock` only), the
  stand-down calls `DevicePolicyManager.lockNow()` after the session has ended. Without the grant it
  logs a warning and the panel stays lit.
- **Power back after this feature ended a session**: the screen comes on (only if this feature
  turned it off), then the stack re-arms by the same path as `ACTION_START_WIRELESS`. The screen-on
  is a two-second bright wake lock. From API 33 the service also raises a screen with the
  turn-screen-on flag: the projection if a session is connected, the home screen if not.
- **`ACTION_SCREEN_OFF`** needs "Allow external configuration" and the grant. **`ACTION_SCREEN_ON`**
  is open to any caller, like `ACTION_DISCONNECT`.

No JVM test can show any of this: the trigger is a system broadcast, and the screen calls are
framework calls whose result depends on the API level and the lock type. 3233 JVM tests cover the
pure policies (`ExternalPowerDisconnectPolicy`, `ScreenOnRaisePolicy`, `AutomationCommandPolicy`).

## 3. What is different about this round

- **The lever is `dumpsys battery`.** `unplug` sends `POWER_DISCONNECTED` and sets the sticky plug
  state to 0. `set ac 1` sends `POWER_CONNECTED` and sets it to AC. Each lever is valid only when its
  `WakeDetect:` line appears (section 6). Every D-HU run starts and ends in the fake plugged state.
  X restores the real state with `dumpsys battery reset`. S2 checks that a real pull gives the same
  event.
- **The device admin grant cannot be removed from the shell.** `dpm remove-active-admin` refuses an
  admin that is not test-only, and Android blocks an uninstall while the grant is held. So the runs
  that need no grant come first, and the grant is set once with `dpm set-active-admin` before V3.
  In X the operator removes it on the system's device admin screen (HS2). The app's own removal row
  has no verb and sits below the first screen. **Do not skip X**: a later round that needs a fresh
  install fails while the grant is held. D-SAM gets no grant.
- **D-HU's own ROM can light the panel on a plug change** (`config_unplugTurnsOnScreen`). C0
  measures it first. If C0 reads `ROM_WAKES_ON_PLUG=yes`, R7's panel reading cannot be credited to
  the app. R7 then grades on the app's own lines, and V1 and V3 carry the screen-on proof for D-HU.
- **No API 29 to 31 unit is on the rig.** The plan asked for one with no lock screen. D-SAM (API 19)
  stands in for the route below API 33, where only the wake lock acts. Android 10 itself is not
  measured.
- **The USB re-arm branch is not run.** D-HU cannot host USB (`host_connected=false`), so
  `power is back; checking USB again.` stays unmeasured this round.
- **The grant row in the settings screen is not graded.** No verb opens it and it sits below the
  first screen. The grant comes from `dpm`, which gives the same end state.
- **D-HU quirks that apply:** `shared_prefs/` can be root-owned (stat it once, `chown` if wrong,
  report what it read); write settings with `hu_put` as root; never run adb calls in parallel
  against one unit; the host thermal gate (`th_gate` before every run).
- **The rig's audio settings are a deliberate worst case.** Never write `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and
  record them.
- **`Critical error` lines on the phone follow every session end we cause** (pre-350-fixes round 1
  setup notes). Do not grade them.
- **Expected INCONCLUSIVE:** R7 conditions 4 and 5 when C0 reads `ROM_WAKES_ON_PLUG=yes`; R3's
  cadence half when no poke prints before the cut; S2 when D-SAM does not read `USB powered: true`.

### Hand steps

| Id | Run | Action | Why there is no verb |
|---|---|---|---|
| HS1 | S2 | The operator pulls D-SAM's USB cable, waits 30 s, and plugs it back | a real power loss is hardware |
| HS2 | X | On D-HU, the operator opens this app's entry on the system's device admin screen and deactivates it, confirming if the system asks | `dpm` refuses to remove an admin that is not test-only; the screen is a system dialog |
| HS3 | grant fallback | Only if `dpm set-active-admin` fails: the operator accepts the system's add-admin screen for this app | the screen is a system dialog |

The script injects no input for either step. It announces each with `cue` and then checks the
result with `admin`. The operator does nothing else on the unit.

## 4. Settings keys

Write with the app stopped. Take a fresh backup of D-HU and D-SAM first (`$OUT/backup-HU.xml`,
`$OUT/backup-SAM.xml`) and state the delta against it in Setup notes. On D-HU write with `hu_put`
from `ohu_lib.sh` in the key syntax below, and read every key back with `rd` before the launch.

**Before the round, fix the empty-set bug in your copy of `ohu_setkeys.py`** if it is not fixed
(tooling quirk): `for item in (v.split(',') if v else []):`.

`$MAC` is D-POCO's Bluetooth address from D-HU's `dumpsys bluetooth_manager` "Bonded devices" list.

```bash
BASE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MAC str:last-connected-native-mac=$MAC str:native-preferred-device-mac=$MAC set:auto-start-bt-macs= bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:auto-start-on-usb=false bool:kill-on-disconnect=false int:onboarding-version=2 int:log-level=2 del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"
A0="bool:allow-external-configuration=false"
A1="bool:allow-external-configuration=true"
EP_OFF="bool:external-power-disconnect=false int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=false"
EP_ON="bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=false"
EP_ON_SCR="bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=true"
EP_MIN_SCR="bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=2 bool:external-power-screen-off=true"
EP_SLEEP="bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=120 bool:external-power-screen-off=false"
```

| Run | Keys |
|---|---|
| C0 | `$BASE $A0 $EP_OFF` |
| V1a | `$BASE $A0 $EP_OFF` |
| V1b | `$BASE $A1 $EP_OFF` |
| R3 | `$BASE $A0 $EP_ON` |
| R4 | `$BASE $A0 $EP_OFF` |
| R1, R2, R10 | `$BASE $A0 $EP_ON` |
| R6 | `$BASE $A0 $EP_ON_SCR` |
| T1 | `$BASE $A0 $EP_SLEEP` |
| V3, V4 | `$BASE $A1 $EP_OFF` |
| R5/R7 | `$BASE $A0 $EP_MIN_SCR` (the 2 is below the floor on purpose; the log must show 5000ms) |

Why the less obvious keys: `auto-start-bt-macs` empty and `auto-start-on-*` false keep every other
re-arm path out of the windows, so a re-arm can only be the feature's own. `auto-start-on-boot`
false keeps the hibernate wake from launching the UI. `log-level` 2 (INFO) carries every line in
section 6. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on C, so markers work
with `$A0`.

D-SAM keys (S1, S2), written with `sx.py` (section 5):
`int:wifi-connection-mode=0 int:onboarding-version=2 int:log-level=2 bool:auto-connect-last-session=false bool:auto-start-on-usb=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=false`.
Mode 0 is MANUAL: nothing is armed, so no session can form.

## 5. Helpers and verbs

Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into `$OUT` from the newest round folder that
has them (pre-350-fixes round 1 used `rig-data/rounds/usb-reattach-round4/`). Use only `hu_put`,
`th_wait`, `th_gate` and `th_report` from them. Source them, then save and source
`$OUT/ascd_lib.sh` last, so its functions win:

```bash
# ascd_lib.sh : source after ohu_lib.sh and ptr_lib.sh. Needs OUT and RUN. U is the unit under test.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
ADMIN=$PKG/com.andrerinas.openheadunit.app.ScreenOffAdminReceiver
DHU=27870808938846; POCO=4f4027e9; SAM=30041c35642d2200; U=${U:-$DHU}
send() { local a=$1; shift; adb -s $U shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; if [ -n "$PHCAP" ]; then adb -s $POCO shell log -t OHURIG "$1"; sleep 0.3; fi; }
HF() { echo "$OUT/$RUN.hu.txt"; }
PF() { echo "$OUT/$RUN.ph.txt"; }
cap_start() {
  adb -s $U logcat -G 16M; adb -s $U logcat -c
  stdbuf -oL adb -s $U logcat -v time > "$(HF)" & echo $! > "$OUT/$RUN.hu.pid"
  if [ -n "$PHCAP" ]; then
    for t in CAR.GAL CAR.GAL.GAL.LITE; do adb -s $POCO shell setprop log.tag.$t VERBOSE; sleep 0.3; done
    adb -s $POCO logcat -G 16M; adb -s $POCO logcat -c
    stdbuf -oL adb -s $POCO logcat -v time > "$(PF)" & echo $! > "$OUT/$RUN.ph.pid"; fi
  sleep 1; }
cap_stop() { sleep 1; for f in "$OUT/$RUN.hu.pid" "$OUT/$RUN.ph.pid"; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null; rm -f "$f"; done; }
# hwin A B : head unit lines from marker A to marker B. pwin A B : the same on the phone capture.
hwin() { awk -v a="AutomationMarker: $1" -v b="AutomationMarker: $2" '{sub(/\r$/,"")} function at(s){i=index($0,s); return i>0 && substr($0,i+length(s))~/^[[:space:]]*$/} at(a){on=1} on{print} on&&at(b){exit}' "$(HF)"; }
pwin() { awk -v a="$1" -v b="$2" '{sub(/\r$/,"")} /OHURIG/ && $NF==a {on=1} on{print} on && /OHURIG/ && $NF==b {exit}' "$(PF)"; }
c() { grep -acF -- "$1"; }
# ep FRAGMENT : count lines that hold FRAGMENT and also "external-power disconnect"
ep() { grep -aF -- "$1" | grep -acF 'external-power disconnect'; }
rend() { grep -acE 'Throughput over [0-9]+ms: rendered=[1-9]'; }
lines() { wc -l < "$(HF)"; }
waitline() { local s=$1 str=$2 f=${3:-$(HF)} from=${4:-0} i
  for i in $(seq 1 "$s"); do tail -n +"$((from+1))" "$f" | grep -aqF -- "$str" && return 0; sleep 1; done; return 1; }
# tms STR : time of day in ms of the first stdin line that holds STR
tms() { grep -aF -m1 -- "$1" | awk '{split($2,t,":"); printf "%d\n", (t[1]*3600+t[2]*60+t[3])*1000}'; }
# gap WINDOW A B : ms from the first A to the first B in WINDOW, or NA
gap() { local a b; a=$(printf '%s\n' "$1" | tms "$2"); b=$(printf '%s\n' "$1" | tms "$3")
  if [ -n "$a" ] && [ -n "$b" ]; then echo $((b-a)); else echo NA; fi; }
wake() { adb -s $U shell dumpsys power | grep -a -m1 -oE 'mWakefulness=[A-Za-z]+|mScreenOn=[a-z]+'; sleep 0.3; }
kg() { adb -s $U shell dumpsys window | grep -a -m2 -oE '(isKeyguardShowing|mShowingLockscreen)=[a-z]+' | tr '\n' ' '; echo; sleep 0.3; }
admin() { adb -s $U shell dumpsys device_policy | grep -ac ScreenOffAdminReceiver; sleep 0.3; }
unplug() { adb -s $U shell dumpsys battery unplug; sleep 0.3; }
plug() { adb -s $U shell dumpsys battery set ac 1; sleep 0.3; }
focus() { adb -s $U shell dumpsys window | grep -a -m1 mCurrentFocus; sleep 0.3; }
state() { send ACTION_QUERY_STATE | grep -a 'data='; }
rd() { adb -s $U shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE "($1)[^/]*"; sleep 0.3; }
cue() { echo "$(date +%T) OPERATOR_STEP $1"; printf '\a'; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
clean_hu() { adb -s $U shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 3; adb -s $U shell am force-stop $PKG; }
phone_off() { adb -s $POCO shell cmd connectivity airplane-mode enable; sleep 3; adb -s $POCO shell svc wifi disable; sleep 0.3
  adb -s $POCO shell svc bluetooth disable; sleep 5; adb -s $POCO shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'; }
# bringup : session from D-HU to D-POCO with the keys in K. Sets ssl=0 on success.
bringup() {
  adb -s $POCO shell cmd connectivity airplane-mode disable; sleep 5
  adb -s $POCO shell svc wifi enable; sleep 0.3; adb -s $POCO shell svc bluetooth enable; sleep 5
  adb -s $POCO shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'
  adb -s $POCO shell input keyevent KEYCODE_HOME; sleep 1
  adb -s $DHU shell am force-stop $PKG; plug; hu_put "$BASEXML" $K
  rd 'external-power|allow-external|wifi-connection-mode|log-level' | tee "$OUT/$RUN.keys"
  th_gate; cap_start; mark $RUN-start
  adb -s $DHU shell am start -n $MAIN; sleep 5
  mark $RUN-arm; local L; L=$(lines); send ACTION_START_WIRELESS_SCAN
  waitline 120 'SSL handshake complete' "$(HF)" $L; ssl=$?; echo "ssl=$ssl" | tee -a "$OUT/$RUN.summary"
  sleep 15; focus | tee "$OUT/$RUN.focus-session"; }
# endrun : user exit (also the reference ByeBye line), then a clean stop
endrun() { mark $RUN-x-go; send ACTION_DISCONNECT > "$OUT/$RUN-x.reply"; sleep 8; mark $RUN-x-done
  mark $RUN-end; plug; clean_hu; cap_stop; th_report $RUN; PHCAP=; }
```

For D-SAM set `U=$SAM` before S1 and `U=$DHU` after S2. D-SAM's `am` rejects `-p`; the helper uses
`-n` only. `logcat -G` may print a usage error on API 19; that is harmless.

D-SAM has no `sed`. Write its settings with this host script, `$OUT/sx.py`, then push and copy:

```python
# sx.py FILE type:key=value ... ; type is int, bool, str or del. Edits FILE in place.
import re, sys
f = sys.argv[1]; s = open(f).read()
for kv in sys.argv[2:]:
    t, rest = kv.split(':', 1); k, v = (rest.split('=', 1) + [''])[:2]
    s = re.sub(r'<\w+ name="%s"[^>]*/>' % re.escape(k), '', s)
    s = re.sub(r'<string name="%s">[^<]*</string>' % re.escape(k), '', s)
    el = {'int': '<int name="%s" value="%s" />', 'bool': '<boolean name="%s" value="%s" />',
          'str': '<string name="%s">%s</string>'}.get(t)
    if el: s = s.replace('</map>', el % (k, v) + '</map>')
open(f, 'w').write(s)
```

```bash
adb -s $SAM shell am force-stop $PKG
adb -s $SAM shell run-as $PKG cat shared_prefs/settings.xml > $OUT/sam.xml; cp $OUT/sam.xml $OUT/backup-SAM.xml
python3 -I $OUT/sx.py $OUT/sam.xml <the D-SAM keys from section 4>
adb -s $SAM push $OUT/sam.xml /data/local/tmp/sam.xml; adb -s $SAM shell run-as $PKG cp /data/local/tmp/sam.xml shared_prefs/settings.xml
U=$SAM rd 'external-power|wifi-connection-mode|log-level'
```

**Verbs and commands used, and nothing else acts on the app:**

| Action | Command |
|---|---|
| arm Native AA, as the WiFi button | `send ACTION_START_WIRELESS_SCAN` |
| user exit from the projection | `send ACTION_DISCONNECT` |
| stop the service, as Exit on the home screen | `send ACTION_EXIT` |
| screen off, the verb under test | `send ACTION_SCREEN_OFF` |
| screen on, the verb under test | `send ACTION_SCREEN_ON` |
| marker | `mark <label>` (labels have no spaces) |
| build identity | `state` |
| launch | `adb -s $U shell am start -n $MAIN` |
| external power lost, fake | `unplug` |
| external power back, fake | `plug` |
| panel off by the OS, not the app | `adb -s $DHU shell input keyevent 223` (D-HU); `adb -s $SAM shell input keyevent 26` only when `wake` reads awake (D-SAM, API 19 has no 223) |
| panel on by the OS | `adb -s $DHU shell input keyevent 224` |
| grant the device admin | `adb -s $DHU shell dpm set-active-admin --user 0 $ADMIN` |

Every verb must print `AutomationReceiver: ` in the head unit capture. A step with no such line
never landed: that step is void, not a FAIL. Save each verb's `am` output to a `.reply` file; the
reply is on its `data=` line. `clean_hu` only for a running app.

### Precheck, once

Apps stopped. Record every output in Setup notes.

```bash
adb -s $DHU shell ls -ld /data/data/$PKG/shared_prefs                    # u0_a176 owner, else chown
adb -s $DHU shell dumpsys battery | grep -a -E 'AC powered|USB powered|Wireless powered|present|level'
adb -s $DHU shell cmd lock_settings get-disabled                          # true = no lock screen
adb -s $DHU shell settings get system screen_off_timeout                  # record as TIMEOUT0
adb -s $DHU shell settings get global stay_on_while_plugged_in
adb -s $DHU shell appops get $PKG SYSTEM_ALERT_WINDOW
adb -s $DHU shell appops get $PKG TURN_SCREEN_ON
U=$DHU admin                                                               # must be 0
adb -s $DHU shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'  # D-POCO listed; take $MAC
adb -s $POCO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb -s $POCO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'   # isGroupOwner false
adb -s $POCO shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'
adb -s $SAM shell dumpsys battery | grep -a -E 'AC powered|USB powered|present|level'
adb -s $SAM shell date; date
adb -s $DHU shell settings put system screen_off_timeout 1800000          # X restores TIMEOUT0
adb -s $DHU shell dumpsys battery set ac 1                                 # fake plugged from here
```

`screen_off_timeout` is raised because `stay_on_while_plugged_in` does not hold the panel while the
fake state reads unplugged. A panel that times out by itself in a window would look like the
feature's screen-off.

## 6. The deciding lines

Every app string below matched `grep -F -r` in `app/src/main` at `5236a1b4`. All are INFO or WARN, so
`log-level` 2 carries them.

| Line (grep -F) | Source | Used for |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | a verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` | markers |
| `WakeDetect: POWER_DISCONNECTED` | `AapService` | the unplug lever landed |
| `WakeDetect: POWER_CONNECTED` | `AapService` | the plug lever landed |
| `WakeDetect: SCREEN_OFF` | `AapService` | the panel went off |
| `WakeDetect: SCREEN_ON (screen was off for ` | `AapService` | the panel came on |
| `WakeDetect: power lost beside a screen-off; treating it as the car being switched off` | `AapService` | must stay 0 in R5 |
| `WakeDetect: re-arming the wireless bring-up held while the unit was asleep` | `AapService` | record in R7 |
| `WakeDetect: hibernate wake detected` | `AapService` | record in R7 |
| `AapService: external-power disconnect: power lost; ending the session in ` | `AapService` | A1; the delay follows, ending `ms unless it comes back.` |
| `ms unless it comes back.` | `AapService` | A1 tail |
| `AapService: external-power disconnect: power lost, but nothing is projecting; leaving the connection stack alone.` | `AapService` | A3 |
| `AapService: external-power disconnect: power is back; the pending disconnect is cancelled.` | `AapService` | A2 |
| `AapService: external-power disconnect: power stayed away; ending the session the way the Exit button does.` | `AapService` | A6 |
| `AapService: external-power disconnect: not ending the session (enabled=` | `AapService` | A7; must stay 0 where named |
| `AapService: external-power disconnect: screen not turned off, no device admin grant` | `AapService` | A8 without the grant |
| `AapService: external-power disconnect: power is back; re-arming the wireless bring-up.` | `AapService` | A9 re-arm |
| `AapService: external-power disconnect: power is back; checking USB again.` | `AapService` | must stay 0 (wireless sessions only) |
| `AapService: external-power disconnect: power is back while the session is still ending; the return runs once it has ended.` | `AapService` | record |
| `external-power disconnect` | `AapService` | the co-filter of `ep`, and the "no line at all" counts |
| `keeping the wireless bring-up down until something re-arms it.` | `AapService` (composed after the label) | the stand-down; count with `ep` |
| `: screen off ` | `AapService` (composed) | the stand-down screen-off; count with `ep`; the line ends `done.` or `refused by the system.` |
| `done.` | `AapService` | the end of the screen-off line when it worked |
| `refused by the system.` | `AapService` | the end of the screen-off line when it did not |
| `: power came back while the session was ending; not standing down.` | `AapService` (composed) | record |
| `: power came back and went again while the session was ending; standing down.` | `AapService` (composed) | record |
| `AapService: external-power disconnect: the session end never reached the stand-down in ` | `AapService` | must stay 0 in every run |
| `AapService: external-power disconnect: could not hold the CPU awake for the timer: ` | `AapService` | must stay 0 in T1 |
| `AapService: turning the screen on (` | `AapService` | screen-on ran; the source follows: `automation` or `external power back` |
| `) by wake lock and ` | `AapService` | screen-on raised a screen as well; then `the home screen` or `the projection`, then ` by <DIRECT/OVERLAY/NOTIFICATION>` |
| `the home screen` | `AapService` | which screen screen-on raised |
| `the projection` | `AapService` | which screen screen-on raised |
| `AapService: screen-on launch failed: ` | `AapService` | must stay 0 |
| `AapTransport stopping and sending byebye (` | `AapTransport` | our side sent the ByeBye; the reason follows |
| `Disconnect action received.` | `AapService` | the reference exit in `endrun` |
| `AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected` | `AapService` | a session ended (printed on every session end) |
| `AapService destroying...` | `AapService` | R10 validity: the service stopped |
| `SSL handshake complete` | `AapSslContext` | a session formed |
| `Throughput over ` | `VideoDecoder` | picture; `rend` regex `Throughput over [0-9]+ms: rendered=[1-9]` |
| `createGroup SUCCESS` | `WifiDirectManager` | must stay 0 in R1's quiet window |
| `NativeAA: Attempting active poke to device` | `NativeAaHandshakeManager` | the poke loop is running (R3), or must stay 0 (R1 quiet) |
| `MATCH! Starting AapService` | `AutoStartReceiver` | discard check, and must stay 0 in R1's quiet window |
| `external configuration is off; turn on` | `AutomationCommandPolicy` reply | V1a refusal |
| `no device admin grant; allow it under Settings > Auto-start` | `AutomationCommandPolicy` reply | V1b refusal |
| `"screenOff"` | `AutomationEffectRunner` reply key | V3 and V4: `"screenOff":true` |

Not in our tree:

| Line | Where | Used for |
|---|---|---|
| `received ByeByeRequest` | D-POCO, tag `CAR.GAL.GAL.LITE` (set VERBOSE by `cap_start`) | the phone got our ByeBye |
| `OHURIG` | D-POCO, our own `log -t` markers | phone windows |
| `device wake-up` | head unit, `PowerManagerService` (API 34: `Allowing device wake-up from app` or `Not allowing device wake-up for `) | record which, for every screen-on on D-HU |

Framework readings, not log lines: `mWakefulness=` from `dumpsys power` (`Awake` is a lit panel,
`Asleep` a dark one; D-SAM may print `mScreenOn=` instead), the keyguard state from `kg`, the
focused window from `focus`, and the admin count from `admin`.

## 7. Runs

Every run: `th_gate` first (inside `bringup` for session runs), then the steps in order. Report
`th_report $RUN` in each section. Order: R0, precheck, C0, V1, R3, R4, **R1**, R2, R6, R10, grant,
V3, V4, R5/R7, S1, S2, X. That order puts every run that needs no grant before the grant.

**Session runs** (R1, R2, R4, R6, R10, V4, R5/R7) set `PHCAP=1` and `K` before `bringup`. If `ssl=1`,
run `endrun` and repeat the run once. A second `ssl=1` makes that run UNTESTABLE. **Every session run
is valid only when** `hwin $RUN-arm $RUN-cut | rend` (or the window the run names) is 1 or more and
`MATCH! Starting AapService` is 0 in `hwin $RUN-start $RUN-end`. Report both numbers.

**Stop rule:** if three session runs in a row end UNTESTABLE, skip the rest of the D-HU session
runs, run S1, S2 and X, and say so in Setup notes.

### C0. Control: does D-HU's ROM light the panel on a plug change?

```bash
RUN=C0; phone_off; adb -s $DHU shell am force-stop $PKG; plug; K="$BASE $A0 $EP_OFF"; hu_put "$BASEXML" $K
th_gate; cap_start; mark C0-start; adb -s $DHU shell am start -n $MAIN; sleep 10
adb -s $DHU shell input keyevent 223; sleep 10; wake | tee $OUT/C0.w0          # must read Asleep, else C0 is void
mark C0-cut; unplug; sleep 3; wake | tee $OUT/C0.w-unplug
sleep 10; [ "$(cat $OUT/C0.w-unplug)" = "mWakefulness=Awake" ] && { adb -s $DHU shell input keyevent 223; sleep 10; }
wake | tee $OUT/C0.w1                                                           # must read Asleep before the plug
mark C0-back; plug; sleep 3; wake | tee $OUT/C0.w-plug
adb -s $DHU shell input keyevent 224; sleep 2; mark C0-end; clean_hu; cap_stop; th_report C0
```

Report `ROM_WAKES_ON_UNPLUG` (`C0.w-unplug` reads `Awake`) and `ROM_WAKES_ON_PLUG` (`C0.w-plug`
reads `Awake`). C0 has no PASS or FAIL. It is **valid when** `hwin C0-cut C0-end` holds 1
`WakeDetect: POWER_DISCONNECTED` and 1 `WakeDetect: POWER_CONNECTED`, and its
`external-power disconnect` count is 0. Report `hwin C0-start C0-end | c 'AapService: turning the screen on ('` too (must be 0).

### V1. The screen verbs before the grant

```bash
RUN=V1a; PHCAP=; phone_off; adb -s $DHU shell am force-stop $PKG; plug; K="$BASE $A0 $EP_OFF"; hu_put "$BASEXML" $K
th_gate; cap_start; mark V1a-start; adb -s $DHU shell am start -n $MAIN; sleep 10
mark V1a-off; send ACTION_SCREEN_OFF > $OUT/V1a-off.reply; sleep 3; wake | tee $OUT/V1a.w-off
adb -s $DHU shell input keyevent 223; sleep 5; wake | tee $OUT/V1a.w-dark
mark V1a-on; send ACTION_SCREEN_ON > $OUT/V1a-on.reply; sleep 5; wake | tee $OUT/V1a.w-on; focus | tee $OUT/V1a.focus; kg | tee $OUT/V1a.kg
mark V1a-end; clean_hu; cap_stop; th_report V1a
RUN=V1b; adb -s $DHU shell am force-stop $PKG; K="$BASE $A1 $EP_OFF"; hu_put "$BASEXML" $K
th_gate; cap_start; mark V1b-start; adb -s $DHU shell am start -n $MAIN; sleep 10
mark V1b-off; send ACTION_SCREEN_OFF > $OUT/V1b-off.reply; sleep 3; wake | tee $OUT/V1b.w-off
mark V1b-end; clean_hu; cap_stop; th_report V1b
```

V1a and V1b each have their own capture (`V1a.hu.txt`, `V1b.hu.txt`). Run `hwin` for each with `RUN`
set to its name.

**PASS when all hold:**
1. `V1a-off.reply` holds `external configuration is off; turn on` and `"ok":false`; `V1a.w-off` reads `Awake`.
2. `V1a.w-dark` reads `Asleep` (the precondition for the screen-on half; if it reads `Awake`, V1a's screen-on half is void).
3. In `hwin V1a-on V1a-end`: 1 line with `AapService: turning the screen on (` that holds `automation`; `V1a.w-on` reads `Awake`; `gap` from that line to `WakeDetect: SCREEN_ON (screen was off for ` is 0 to 5000.
4. `V1b-off.reply` holds `no device admin grant; allow it under Settings > Auto-start` and `"ok":false`; `V1b.w-off` reads `Awake`.
5. `AapService: screen-on launch failed: ` is 0 in both captures.

Record the whole screen-on line (which screen, which strategy), the `device wake-up` lines in
`hwin V1a-on V1a-end`, `V1a.focus` and `V1a.kg`.

### R3. Power lost with no session

The phone stays in airplane mode, so the stack is armed and pokes, and no session forms.

```bash
RUN=R3; PHCAP=; phone_off; adb -s $DHU shell am force-stop $PKG; plug; K="$BASE $A0 $EP_ON"; hu_put "$BASEXML" $K
th_gate; cap_start; mark R3-start; adb -s $DHU shell am start -n $MAIN; sleep 5
mark R3-arm; send ACTION_START_WIRELESS_SCAN; sleep 60
mark R3-cut; unplug; sleep 60
mark R3-back; plug; sleep 30
mark R3-end; clean_hu; cap_stop; th_report R3
```

**PASS when all hold** (`W1=$(hwin R3-cut R3-back)`, `W2=$(hwin R3-back R3-end)`):
1. `W1`: `WakeDetect: POWER_DISCONNECTED` is 1 (else the run is void).
2. `W1`: the A3 line (`... power lost, but nothing is projecting; leaving the connection stack alone.`) is 1, and `gap "$W1" 'WakeDetect: POWER_DISCONNECTED' 'nothing is projecting'` is 0 to 1000.
3. `W1`: `AapService: external-power disconnect: power lost; ending the session in ` is 0, the A6 line is 0, and `ep 'keeping the wireless bring-up down'` is 0.
4. `W2`: `WakeDetect: POWER_CONNECTED` is 1, and `external-power disconnect` is 0 (nothing owed, so power back does nothing new). `AapService: turning the screen on (` is 0.
5. Cadence: if `hwin R3-arm R3-cut | c 'NativeAA: Attempting active poke to device'` is 1 or more, then `W1` holds 1 or more of it too. If the first count is 0, the cadence half is INCONCLUSIVE and conditions 1 to 4 still decide.

Report both poke counts.

### R4. Switch off

```bash
RUN=R4; PHCAP=1; K="$BASE $A0 $EP_OFF"; bringup
sleep 20; mark R4-cut; unplug; sleep 35
mark R4-back; plug; sleep 10; endrun
```

**PASS when all hold** (`W=$(hwin R4-cut R4-x-go)`):
1. `W`: `WakeDetect: POWER_DISCONNECTED` is 1 and `WakeDetect: POWER_CONNECTED` is 1 (else void).
2. `W`: `external-power disconnect` is 0.
3. `W`: `since connection is disconnected` is 0, and `hwin R4-cut R4-back | rend` is 1 or more.
4. Phone: `pwin R4-cut R4-x-go | c 'received ByeByeRequest'` is 0.

This PASS would look the same if the feature did nothing. R1 is the run that shows the same lever
does produce lines when the switch is on; R4 is valid only beside a passing R1.

### R1. The point of the round: the session ends after the delay, and power back brings it back

```bash
RUN=R1; PHCAP=1; K="$BASE $A0 $EP_ON"; bringup
sleep 20; mark R1-cut; unplug
waitline 40 'power stayed away; ending the session the way the Exit button does.' "$(HF)"; sleep 15
mark R1-quiet; sleep 120; mark R1-quiet-end; wake | tee $OUT/R1.wake
mark R1-back; L=$(lines); plug
waitline 120 'SSL handshake complete' "$(HF)" $L; echo "ssl2=$?" | tee -a $OUT/R1.summary
sleep 20; endrun
```

`W1=$(hwin R1-cut R1-quiet)`, `W2=$(hwin R1-quiet R1-quiet-end)`, `W3=$(hwin R1-back R1-x-go)`,
`P1=$(pwin R1-cut R1-quiet)`, `REF=$(hwin R1-x-go R1-x-done)`.

**PASS when all hold:**
1. Validity: `hwin R1-arm R1-cut | rend` is 1 or more; `W1` holds 1 `WakeDetect: POWER_DISCONNECTED`.
2. A1: `W1` holds 1 line with `AapService: external-power disconnect: power lost; ending the session in `, and the number on that line before `ms unless it comes back.` is 20000; `gap "$W1" 'WakeDetect: POWER_DISCONNECTED' 'power lost; ending the session in '` is 0 to 1000.
3. A6: `W1` holds 1 A6 line; `gap "$W1" 'power lost; ending the session in ' 'power stayed away;'` is 19500 to 23000.
4. Exit flavour: `gap "$W1" 'power stayed away;' 'AapTransport stopping and sending byebye ('` is 0 to 2000, and the reason in brackets on that line equals the reason on the same line in `REF`.
5. The session ended after A6 and not before: `gap "$W1" 'power stayed away;' 'since connection is disconnected'` is 0 or more.
6. Stand-down: `echo "$W1" | ep 'keeping the wireless bring-up down until something re-arms it.'` is 1, and `: power came back while the session was ending` is 0.
7. Phone: `echo "$P1" | c 'received ByeByeRequest'` is 1 or more.
8. Quiet: in `W2`, `createGroup SUCCESS`, `NativeAA: Attempting active poke to device`, `MATCH! Starting AapService` and `SSL handshake complete` are all 0.
9. Screen untouched: `R1.wake` reads `Awake`; `echo "$W1$W2" | ep ': screen off '` is 0; `screen not turned off` is 0.
10. Return: `W3` holds 1 `WakeDetect: POWER_CONNECTED` and 1 `power is back; re-arming the wireless bring-up.`; `gap "$W3" 'WakeDetect: POWER_CONNECTED' 'power is back; re-arming'` is 0 to 1000; `AapService: turning the screen on (`, `the pending disconnect is cancelled` and `power is back; checking USB again.` are 0.
11. New session: `ssl2=0`, and `echo "$W3" | rend` is 1 or more.

Report: the A1 to A6 gap in ms, the A6 to ByeBye gap, the POWER_CONNECTED to SSL gap in `W3`, and
every count in condition 8.

### R2. Power back inside the delay, and a flap

```bash
RUN=R2; PHCAP=1; K="$BASE $A0 $EP_ON"; bringup
sleep 20; mark R2-cut; unplug; sleep 10
mark R2-back; plug; sleep 30; mark R2-hold
mark R2-flap; unplug; sleep 5; plug; sleep 5; unplug; sleep 30; mark R2-flap-end
L=$(lines); plug
waitline 120 'SSL handshake complete' "$(HF)" $L; echo "ssl2=$?" | tee -a $OUT/R2.summary
sleep 15; endrun
```

`Wa=$(hwin R2-cut R2-hold)`, `Wb=$(hwin R2-flap R2-flap-end)`, `Wc=$(hwin R2-flap-end R2-x-go)`.

**PASS when all hold:**
1. Cancel (`Wa`): `WakeDetect: POWER_DISCONNECTED` 1, A1 1, `WakeDetect: POWER_CONNECTED` 1, A2 (`the pending disconnect is cancelled.`) 1, and `gap "$Wa" 'WakeDetect: POWER_CONNECTED' 'the pending disconnect is cancelled.'` is 0 to 1000.
2. Nothing fired (`Wa`): the A6 line 0, `not ending the session (enabled=` 0, `since connection is disconnected` 0; `hwin R2-back R2-hold | rend` is 1 or more.
3. Phone: `pwin R2-cut R2-hold | c 'received ByeByeRequest'` is 0.
4. Flap (`Wb`): A1 is 2, A2 is 1, A6 is 1, `not ending the session (enabled=` is 0, and `gap "$Wb" 'power lost; ending the session in ' 'power stayed away;'` is 29000 to 33500. That gap runs from the first loss, so a timer left over from it would show near 20000.
5. Return (`Wc`): `power is back; re-arming the wireless bring-up.` 1, `ssl2=0`, `echo "$Wc" | rend` 1 or more.

### R6. Screen row on, no grant

Precondition: `U=$DHU admin` reads 0.

```bash
RUN=R6; PHCAP=1; K="$BASE $A0 $EP_ON_SCR"; bringup
sleep 20; mark R6-cut; unplug; sleep 35; wake | tee $OUT/R6.wake
mark R6-back; L=$(lines); plug
waitline 120 'SSL handshake complete' "$(HF)" $L; echo "ssl2=$?" | tee -a $OUT/R6.summary
sleep 15; endrun
```

**PASS when all hold** (`W1=$(hwin R6-cut R6-back)`, `W2=$(hwin R6-back R6-x-go)`):
1. `W1`: A1 1; `screen not turned off, no device admin grant` 1; A6 1; `gap "$W1" 'screen not turned off' 'power stayed away;'` is 0 or more.
2. `W1`: `ep 'keeping the wireless bring-up down'` 1; `ep ': screen off '` 0; `WakeDetect: SCREEN_OFF` 0.
3. `R6.wake` reads `Awake`.
4. Phone: `pwin R6-cut R6-back | c 'received ByeByeRequest'` 1 or more.
5. `W2`: `power is back; re-arming the wireless bring-up.` 1; `AapService: turning the screen on (` 0; `ssl2=0`; `rend` 1 or more.

### R10. The service stops inside the delay

```bash
RUN=R10; PHCAP=1; K="$BASE $A0 $EP_ON"; bringup
sleep 20; mark R10-cut; unplug; sleep 8
mark R10-exit; send ACTION_EXIT > $OUT/R10-exit.reply; sleep 5
adb -s $DHU shell am start -n $MAIN; sleep 5
mark R10-arm2; L=$(lines); send ACTION_START_WIRELESS_SCAN
waitline 120 'SSL handshake complete' "$(HF)" $L; echo "ssl2=$?" | tee -a $OUT/R10.summary
sleep 45; mark R10-hold; plug; sleep 10; endrun
```

**PASS when all hold:**
1. `hwin R10-cut R10-exit`: A1 is 1.
2. Validity: `hwin R10-exit R10-arm2 | c 'AapService destroying...'` is 1 or more. If it is 0, the service never stopped and R10 is void.
3. `hwin R10-exit R10-x-go`: the A6 line 0, `not ending the session (enabled=` 0, and `external-power disconnect` 0 in total (no timer survived, none started without a new event, and power back owes nothing).
4. `ssl2=0`; in `hwin R10-arm2 R10-hold`, `rend` is 1 or more, and `since connection is disconnected` after the SSL line is 0 (check with `gap` from `SSL handshake complete` to it: NA or negative passes).

Report the time from `R10-cut` to `R10-hold` in seconds; it must be over 30, so the old deadline fell
inside the window.

### T1. The timer while the unit sleeps

The point: the timer used to stop while the SoC slept. It now holds a partial wake lock and checks
an `elapsedRealtime` deadline. This run lets the panel time out inside a 120 s delay. No adb call
runs inside the 150 s wait, because adb traffic can keep the unit awake.

```bash
RUN=T1; PHCAP=1; K="$BASE $A0 $EP_SLEEP"; bringup
sleep 20; adb -s $DHU shell svc power stayon false
adb -s $DHU shell settings put system screen_off_timeout 15000
adb -s $DHU shell su -c 'cat /sys/power/suspend_stats/success 2>/dev/null; grep -a "^success" /sys/kernel/debug/suspend_stats 2>/dev/null' | tee $OUT/T1.susp0
mark T1-cut; adb -s $DHU shell input keyevent 3; unplug
sleep 150
wake | tee $OUT/T1.wake
adb -s $DHU shell su -c 'cat /sys/power/suspend_stats/success 2>/dev/null; grep -a "^success" /sys/kernel/debug/suspend_stats 2>/dev/null' | tee $OUT/T1.susp1
adb -s $DHU shell dumpsys power | grep -a -c 'ExternalPowerDisconnect' | tee $OUT/T1.lock
adb -s $DHU shell input keyevent 224; sleep 2; mark T1-end
adb -s $DHU shell settings put system screen_off_timeout 1800000
plug; sleep 10; endrun
```

If `su -c` is not the form `hu_put` uses on D-HU, use that form. Report what you used.

`W=$(hwin T1-cut T1-end)`, `P=$(pwin T1-cut T1-end)`.

**PASS when all hold:**
1. Validity: `W` holds 1 `WakeDetect: POWER_DISCONNECTED` and 1 `WakeDetect: SCREEN_OFF`; `gap "$W" 'WakeDetect: POWER_DISCONNECTED' 'WakeDetect: SCREEN_OFF'` is 10000 to 25000, so the panel went off inside the delay.
2. A1: the number on the A1 line before `ms unless it comes back.` is 120000.
3. The deadline held: `gap "$W" 'power lost; ending the session in ' 'power stayed away;'` is 119500 to 126000.
4. `could not hold the CPU awake for the timer: ` is 0, and `the session end never reached the stand-down in ` is 0.
5. The session ended on time on the phone: `echo "$P" | c 'received ByeByeRequest'` is 1 or more.
6. `T1.lock` reads 0: the lock was released after the end.

Record: `T1.susp0`, `T1.susp1` and `T1.wake`. If the suspend count did not rise, or neither file
could be read, say so: then the unit did not suspend, and condition 3 proves the deadline only with
the panel off, not across a suspend. Grade T1 PASS on conditions 1 to 6 in both cases, and add
`suspended=yes`, `suspended=no` or `suspended=unknown` to the verdict line.

### Grant

```bash
adb -s $DHU shell dpm set-active-admin --user 0 $ADMIN; sleep 1; U=$DHU admin     # must read 1
```

If `dpm` fails, use HS3. Open the system screen, then announce the hand step:

```bash
adb -s $DHU shell am start -a android.app.action.ADD_DEVICE_ADMIN --ecn android.app.extra.DEVICE_ADMIN $ADMIN; sleep 3
cue "HS3: accept the device admin screen for Open Headunit on D-HU"
sleep 30; U=$DHU admin                                                         # must read 1
```

If `admin` still reads 0, V3, V4 and R5/R7 are UNTESTABLE; go to S1.

### V3. The screen verbs with the grant, no session

```bash
RUN=V3; phone_off; adb -s $DHU shell am force-stop $PKG; plug; K="$BASE $A1 $EP_OFF"; hu_put "$BASEXML" $K
th_gate; cap_start; mark V3-start; adb -s $DHU shell am start -n $MAIN; sleep 10
mark V3-off; send ACTION_SCREEN_OFF > $OUT/V3-off.reply; sleep 3; wake | tee $OUT/V3.w-off; kg | tee $OUT/V3.kg-off
sleep 10; mark V3-on; send ACTION_SCREEN_ON > $OUT/V3-on.reply; sleep 5; wake | tee $OUT/V3.w-on; focus | tee $OUT/V3.focus; kg | tee $OUT/V3.kg-on
mark V3-end; clean_hu; cap_stop; th_report V3
```

**PASS when all hold:**
1. `V3-off.reply` holds `"screenOff":true` and `"ok":true`; `V3.w-off` reads `Asleep`; `hwin V3-off V3-on` holds 1 `WakeDetect: SCREEN_OFF`.
2. `hwin V3-off V3-on`: `WakeDetect: power lost beside a screen-off` is 0.
3. `hwin V3-on V3-end`: 1 `AapService: turning the screen on (` line holding `automation` and `the home screen`; `V3.w-on` reads `Awake`; the `gap` from that line to `WakeDetect: SCREEN_ON (screen was off for ` is 0 to 5000.
4. `AapService: screen-on launch failed: ` is 0.

Record the strategy, the `device wake-up` lines, both `kg` readings and `V3.focus`.

### V4. The screen verbs during a session

```bash
RUN=V4; PHCAP=1; K="$BASE $A1 $EP_OFF"; bringup
sleep 20; mark V4-cut; mark V4-off; send ACTION_SCREEN_OFF > $OUT/V4-off.reply; sleep 10; wake | tee $OUT/V4.w-off
mark V4-on; send ACTION_SCREEN_ON > $OUT/V4-on.reply; sleep 8; wake | tee $OUT/V4.w-on; focus | tee $OUT/V4.focus
sleep 15; mark V4-after; endrun
```

**PASS when all hold:**
1. `V4-off.reply` holds `"screenOff":true`; `V4.w-off` reads `Asleep`; `V4.w-on` reads `Awake`.
2. `hwin V4-off V4-after`: `WakeDetect: power lost beside a screen-off` is 0.
3. If `hwin V4-off V4-on | c 'since connection is disconnected'` is 0 (the session survived the dark panel): the screen-on line in `hwin V4-on V4-after` holds `the projection`; `V4.focus` names `AapProjectionActivity`; `hwin V4-on V4-after | rend` is 1 or more.
4. If that count is 1 or more (the existing sleep path ended the session): the screen-on line holds `the home screen`. Report it as a finding about the sleep path, not a FAIL of this change.

Record which of 3 or 4 applied, the phone's `received ByeByeRequest` count in `pwin V4-off V4-after`, and the strategy.

### R5 and R7. Screen-off after the end, at the minimum delay, then power back

Precondition: `U=$DHU admin` reads 1. The panel stays dark for about 40 s, under the 60 s hibernate
threshold.

```bash
RUN=R5; PHCAP=1; K="$BASE $A0 $EP_MIN_SCR"; bringup
sleep 20; mark R5-cut; unplug; sleep 20; wake | tee $OUT/R5.wake; kg | tee $OUT/R5.kg; mark R5-dark
sleep 20; mark R7-back; L=$(lines); plug; sleep 5; wake | tee $OUT/R7.wake; focus | tee $OUT/R7.focus; kg | tee $OUT/R7.kg
waitline 120 'SSL handshake complete' "$(HF)" $L; echo "ssl2=$?" | tee -a $OUT/R5.summary
sleep 15; endrun
```

**R5 PASS when all hold** (`W=$(hwin R5-cut R5-dark)`):
1. A4 clamp: `W` holds 1 A1 line, and the number on it before `ms unless it comes back.` is 5000 (the 2 was clamped)
2. `W`: A6 1; `gap "$W" 'power lost; ending the session in ' 'power stayed away;'` is 4500 to 8000.
3. `W`: `ep 'keeping the wireless bring-up down'` 1; `echo "$W" | grep -aF ': screen off ' | grep -acF 'done.'` 1; `refused by the system.` 0.
4. `W`: `WakeDetect: SCREEN_OFF` 1, and `gap "$W" 'WakeDetect: POWER_DISCONNECTED' 'WakeDetect: SCREEN_OFF'` is over 5000.
5. `W`: `WakeDetect: power lost beside a screen-off` is 0.
6. `R5.wake` reads `Asleep`.
7. Phone: `pwin R5-cut R5-dark | c 'received ByeByeRequest'` 1 or more.

**R7 PASS when all hold** (`W=$(hwin R7-back R5-x-go)`):
1. `W`: `WakeDetect: POWER_CONNECTED` 1.
2. `W`: 1 `AapService: turning the screen on (` line holding `external power back`; `gap "$W" 'WakeDetect: POWER_CONNECTED' 'AapService: turning the screen on ('` is 0 to 1000.
3. `W`: `power is back; re-arming the wireless bring-up.` 1, and `gap "$W" 'AapService: turning the screen on (' 'power is back; re-arming'` is 0 or more.
4. `W`: `gap "$W" 'AapService: turning the screen on (' 'WakeDetect: SCREEN_ON (screen was off for '` is 0 to 5000. (INCONCLUSIVE when C0 reads `ROM_WAKES_ON_PLUG=yes`; then report the gap from POWER_CONNECTED instead.)
5. `R7.wake` reads `Awake`. (Same INCONCLUSIVE rule as 4.)
6. `ssl2=0`, and `echo "$W" | rend` is 1 or more.
7. `AapService: screen-on launch failed: ` is 0.

Record: the whole screen-on line (wake lock alone, or which screen and which strategy), the
`device wake-up` lines, `R5.kg`, `R7.kg`, `R7.focus`, and the counts of
`WakeDetect: re-arming the wireless bring-up held while the unit was asleep` and
`WakeDetect: hibernate wake detected` in `W`. A screen-on line that ends `by wake lock` alone means
the panel was already lit when the app asked; say whether C0 explains that.

### S1. Screen-on below API 33 (D-SAM)

```bash
U=$SAM; RUN=S1; PHCAP=; <write the D-SAM keys, section 5>
cap_start; mark S1-start; adb -s $SAM shell am start -n $MAIN; sleep 10; state
wake | tee $OUT/S1.w0; [ "$(cat $OUT/S1.w0)" != "mWakefulness=Asleep" ] && adb -s $SAM shell input keyevent 26
sleep 8; wake | tee $OUT/S1.w-dark
mark S1-on; send ACTION_SCREEN_ON > $OUT/S1-on.reply; sleep 3; wake | tee $OUT/S1.w-on
mark S1-end; cap_stop; th_report S1
```

**PASS when all hold:**
1. `S1.w-dark` reads `Asleep` (or `mScreenOn=false`); 8 s passed with no wake, so the panel does not light by itself. If it reads awake, S1 is void.
2. `hwin S1-on S1-end` holds 1 `AapService: turning the screen on (` line holding `automation`, and that line does not hold `) by wake lock and ` (below API 33 the wake lock acts alone).
3. `S1.w-on` reads `Awake` (or `mScreenOn=true`).
4. `AapService: screen-on launch failed: ` is 0.

### S2. A real cable pull (D-SAM)

Precondition: the precheck read `USB powered: true` on D-SAM. If not, S2 is UNTESTABLE.

```bash
U=$SAM; RUN=S2; adb -s $SAM shell am start -n $MAIN; sleep 5
adb -s $SAM logcat -c; mark S2-cut
cue "HS1: pull D-SAM's USB cable now, wait 30 s, plug it back"
adb -s $SAM wait-for-device; sleep 10; mark S2-end
adb -s $SAM logcat -d -v time > "$(HF)"
adb -s $SAM shell dumpsys battery | grep -a 'USB powered' | tee $OUT/S2.battery
adb -s $SAM shell am force-stop $PKG; U=$DHU
```

**PASS when all hold** (`W=$(hwin S2-cut S2-end)`):
1. `W`: `WakeDetect: POWER_DISCONNECTED` 1 and `WakeDetect: POWER_CONNECTED` 1, about 30 s apart (report the gap).
2. `W`: the A3 line 1; `gap "$W" 'WakeDetect: POWER_DISCONNECTED' 'nothing is projecting'` 0 to 1000.
3. `W`: `external-power disconnect` is exactly 1 (the A3 line, and nothing on the return).
4. `S2.battery` reads `USB powered: true` again.

Restore D-SAM: push `backup-SAM.xml` back the same way as the keys, and read it back.

### X. Clean-up (D-HU)

```bash
U=$DHU; adb -s $DHU shell am force-stop $PKG
adb -s $DHU shell am start -n 'com.android.settings/.Settings$DeviceAdminSettingsActivity'; sleep 3
cue "HS2: deactivate Open Headunit on the device admin screen of D-HU"
sleep 30
admin                                                    # must read 0
adb -s $DHU shell input keyevent 3
adb -s $DHU shell dumpsys battery reset
adb -s $DHU shell dumpsys battery | grep -a -E 'AC powered|USB powered|Wireless powered'   # as in the precheck
adb -s $DHU shell settings put system screen_off_timeout <TIMEOUT0>
```

Restore D-HU's `settings.xml` from `backup-HU.xml` as root (`chown u0_a176:u0_a176`, `chmod 660`),
read it back, and diff it against the backup. Put D-POCO back in airplane mode. **If `admin` does
not read 0, stop and tell the operator**: the grant blocks an uninstall on this unit.

## 8. Do not re-run

Nothing. This is the thread's first round.

## 9. Report back

The numbers that decide shipping:

1. **R1:** the A1 to A6 gap in ms, and the counts of `createGroup SUCCESS`, `Attempting active poke`
   and `SSL handshake complete` in the 120 s quiet window. Then whether a new session formed after
   power came back, and how long it took.
2. **R7 and V3:** the whole screen-on line, `mWakefulness` 5 s after the request, and the
   `device wake-up` line, beside C0's two readings. This says whether the panel lights on API 34
   with this app's grant set.
3. **T1:** the A1 to A6 gap in ms with the panel off, and `suspended=`.
4. **R5:** the ms from `POWER_DISCONNECTED` to `SCREEN_OFF` at the 5 s minimum, and the count of
   `power lost beside a screen-off` (must be 0).

Give each verdict in the fixed format of `TESTING-TEMPLATE.md` §7, and put every deviation in Setup
notes.
