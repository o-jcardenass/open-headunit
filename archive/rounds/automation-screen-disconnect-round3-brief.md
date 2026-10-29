# automation-screen-disconnect, round 3 brief: the screen-on raise, the keyguard, and the USB stand-down

Publish name on `transfer/rig-rounds`: `automation-screen-disconnect-round3-brief.md`. Results go in
`automation-screen-disconnect-round3-results.md`. Evidence goes in the existing release
`rig-evidence-automation-screen-disconnect` as the asset
`automation-screen-disconnect-round3-captures.zip`.

Round 2 (`automation-screen-disconnect-round2-results.md`) passed the point of the round (R1) and 12
other runs. Three runs failed: V3, V4 and R7. The cause of V3 was in the build, and C now fixes it.
V4 and R7 failed because the screen-on raise stayed behind the keyguard that `lockNow()` puts up. C
now asks the system to dismiss that keyguard. A review after round 2 also found a fault on the USB
path, which no run had reached. C fixes that too. This round grades the three fixes and runs the
USB scenario (R11) for the first time.

One candidate, one build (about 25 min). Three units: D-POCO and D-MOTO, plus D-HU for its
Bluetooth only. D-SAM and D-HP are free. About 65 minutes of runs, plus the build and the
thermal-gate waits. The operator must be present for HS2 (one device admin removal) and for all of
Stage U (cable moves and USB dialogs).

| Run | What it proves | Head unit | Phone |
|---|---|---|---|
| R0 | build, APK identity on D-POCO | | |
| P | precheck | all | |
| V3 | **the point of the round, part 1:** screen-on with the grant and no session now raises the home screen | D-POCO | |
| V4n | screen-on during a session now raises the projection (no keyguard) | D-POCO | D-MOTO |
| R5n, R7n | screen-off after the end, then power back: a new session renders (no keyguard) | D-POCO | D-MOTO |
| **V4k** | **the point of the round, part 3:** screen-on during a session dismisses the swipe keyguard and the projection renders | D-POCO | D-MOTO |
| **R5k, R7k** | **part 3:** the same return as R7n, with the swipe keyguard up | D-POCO | D-MOTO |
| **R11** | **the point of the round, part 2:** a USB session ended by power loss keeps wireless down until power back, then re-arms it | D-POCO (USB host) | D-MOTO (on OTG) |
| X | clean-up | all | |

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` |
|---|---|---|---|
| **C** (candidate) | `fork/feat/automation-screen-disconnect` | `b5bd8145780ad6dae31f09b7f8075a1831398a62` | `b5bd8145780a` (a `-dirty` suffix is expected) |

History was rewritten since round 2. Round 2's `8db52ceb` carried the same two commits without the
three fixes. The fixes were folded into the commits, and the branch was force-pushed. C is `main` at
`e9377ff9` plus `5283f9b5` (Auto-disconnect: end the session when external power is lost) and
`b5bd8145` (Automation: ACTION_SCREEN_OFF and ACTION_SCREEN_ON). No baseline arm: round 2's
captures are the "before" for V3, V4 and R7.

```bash
git fetch fork feat/automation-screen-disconnect
git rev-parse fork/feat/automation-screen-disconnect          # must print b5bd8145780ad6dae31f09b7f8075a1831398a62
git rev-parse fork/feat/automation-screen-disconnect^{tree}   # must print 1d56d2a739398fa809ef3236b0df4f52e54de1a0
```

If either line differs, stop the round.

### R0. Build and APK identity

Make `rig-data/rounds/automation-screen-disconnect-round3/` and set `OUT` to it. In one worktree,
`git checkout --detach b5bd8145780ad6dae31f09b7f8075a1831398a62` and check the tree hash. Run
`$RIG_TOOLKIT/rig_thermal.sh wait 70`, then build with
`HUR_DIR=<wt> MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh`. Copy the APK out of `apks/` at once to
`$OUT/apk/C.apk`. Do not run the unit tests: 3521 JVM tests passed on the dev host on this tree, with
0 failures.

**r0_identity.sh**
```bash
APK=$OUT/apk/C.apk
md5sum $APK                                                   # record it in Setup notes
for s in WiredRearm 'the stand-down decides the re-arm of wireless mode' 'screen-on asks the system to dismiss the keyguard' ScreenOnRaisePolicy 'external-power disconnect'; do
  printf '%s %s\n' "$s" "$(unzip -p $APK 'classes*.dex' | strings | grep -cF "$s")"; done   # each 1 or more
adb -s 4f4027e9 shell am force-stop com.andrerinas.headunitrevived; sleep 0.3
adb -s 4f4027e9 shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/pre-install-D-POCO.xml
adb -s 4f4027e9 install -r -d $APK
adb -s 4f4027e9 shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/post-install-D-POCO.xml
diff $OUT/pre-install-D-POCO.xml $OUT/post-install-D-POCO.xml   # record the delta; reseed from pre-install if it differs
```

`WiredRearm`, `the stand-down decides the re-arm of wireless mode` and `screen-on asks the system to
dismiss the keyguard` are new in C. Round 2's APK
cannot hold them, so a count of 0 means the wrong APK. If the build fails or a symbol counts 0, stop
the round. If the install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and tell the
operator. Do not uninstall. D-SAM is not used and keeps round 2's APK.

**PASS when:** in P, `U=$POCO state` shows `commit` starting `b5bd8145780a`. **A failure in R0 stops
the round.**

## 2. What this is and why

A tablet that serves as a head unit has its own battery. When the car is switched off, the charger
loses power, and the screen and the Android Auto session stay up. The candidate ends the session
after a delay (`external-power-disconnect`), can turn the screen off after the end
(`external-power-screen-off`, with a device admin grant), and undoes both when power comes back. It
also adds the verbs `ACTION_SCREEN_OFF` and `ACTION_SCREEN_ON`. Round 2's brief, section 2, has the
full description.

Two faults are fixed in C:

- **The screen-on raise never fired (V3, V4, R7 in round 2).** From API 33, `turnScreenOn` raises a
  screen with the turn-screen-on flag: the projection during a session, the home screen without
  one. `ScreenOnRaisePolicy.decide` raises nothing when the panel is already lit. In round 2,
  `turnScreenOn` took the wake lock first and read the panel after it, so the panel always read lit,
  and every line read `... by wake lock` with no screen. C reads the panel first, then takes the
  wake lock. Expected now: `AapService: turning the screen on (automation) by wake lock and the home
  screen by <strategy>` in V3, and `... and the projection by <strategy>` in V4n.
- **The wired re-arm defeated the stand-down (no run reached it).** A USB session stops the wireless
  stack while it runs, and its end re-arms the stack 1.5 s later. When power loss ended a USB
  session, that re-arm ran after the stand-down had stopped the stack. So the stack came back up
  with the car off. C lets the stand-down decide: it logs `AapService: wired session ended by an
  auto-disconnect; the stand-down decides the re-arm of wireless mode <mode>`, holds the re-arm, and
  runs it after `power is back; checking USB again.` on power back.
- **The screen-on raise stayed behind the keyguard (V4 and R7 in round 2).** `lockNow()` puts the
  keyguard up, and an activity raised under it gets no surface. C reads the keyguard with the panel
  (`KeyguardManager.isKeyguardLocked`). When a keyguard is up, from API 26, it raises a screen even
  on a lit panel, and that screen calls `KeyguardManager.requestDismissKeyguard`. A keyguard with no
  PIN, pattern or password goes away. A secure one shows its own unlock screen, so the verb still
  cannot unlock a unit. The screen-on line now ends `(keyguard=true)`, `(keyguard=false)` or
  `(keyguard=null)`, and the raised screen logs `MainActivity: screen-on asks the system to dismiss
  the keyguard` (or `AapProjectionActivity: ...`), then `...: the keyguard was dismissed`.

## 3. What is different about this round

- **Only D-POCO runs the app.** Its fake lever works (round 2 L-POCO: `LEVER_POCO=fake`), so every
  run uses the fake lever. No cable pull. D-SAM runs nothing.
- **D-POCO's ROM lights the panel on every plug change** (`rig-quirks/units/D-POCO.md`). So in R7n
  the panel reads lit before `turnScreenOn` runs, and with no keyguard the line reads `... by wake
  lock` with no screen. That is correct by the policy. R7n conditions 4 and 5 stay INCONCLUSIVE, as
  in round 2. In R7k the keyguard is up, so C raises the home screen on the lit panel.
- **Each session case runs twice: with the keyguard off, then with it up.** V4n and R5n/R7n run with
  D-POCO's keyguard off (`locksettings set-disable true`). They grade the raise and the return on
  their own. V4k and R5k/R7k run after "Keyguard back", with the swipe keyguard that `lockNow()`
  puts up. They grade the dismiss. P proves D-POCO has no lock credential, so the keyguard is never
  a secure one in this round. X restores the keyguard setting that P reads.
- **V3 runs with the keyguard as found** (round 2 measured a swipe keyguard after `lockNow()`). So
  V3 also grades the dismiss from the home screen.
- **D-POCO dozes between runs** and `am start` does not wake it. `ready` (section 5) lights the
  panel and dismisses the keyguard before each run and after each bring-up. This was a hand step in
  round 2.
- **Stage U moves D-POCO and D-MOTO to wireless adb.** D-POCO hosts USB through its OTG adapter, so
  its one port is not free for the PC, and D-MOTO plugs into that adapter. This is the layout of the
  `bluetooth-audio-disabled-usb-connect` rounds (D-POCO `192.168.1.8:5555`, D-MOTO
  `192.168.1.5:5555` there). Read the addresses again; do not assume them.
- **R11 runs in `wifi-connection-mode` 1 (Headunit Server), not 3.** The fix needs a non-manual
  wireless mode. Mode 3 can stand D-POCO's station down, which drops wireless adb. Mode 1 leaves the
  station alone.
- **Hold D-HU's Bluetooth off before every Stage A session** (`bringup` does it). D-MOTO is bonded
  to D-HU's radio, and Gearhead can dial it instead of D-POCO. X turns it back on.
- **Do not write the rig's audio settings.** `put` starts from the unit's own backup, so they keep
  their values.
- **`Critical error` lines on the phone follow every session end we cause.** Do not grade them.
- **Expected INCONCLUSIVE:** R7n conditions 4 and 5, R7k condition 4; R11 when the USB session never stops the
  wireless stack (condition 1c); R11 condition 10 under the re-attach fault (rule in R11).

### Hand steps

| Id | Run | Action | Why there is no verb |
|---|---|---|---|
| HS0 | P | Only if D-MOTO shows a PIN screen: the operator unlocks D-MOTO once | adb cannot enter a PIN |
| HS2 | after R7n | On D-POCO, the operator deactivates this app on the system's device admin screen | `dpm` refuses to remove an admin that is not test-only; the screen is a system dialog |
| HS3 | grant fallback | Only if `dpm set-active-admin` fails: the operator accepts the add-admin screen | a system dialog |
| CABLES | Stage U | The operator unplugs D-POCO and D-MOTO from the PC and puts the OTG adapter in D-POCO | hardware |
| OTG | R11 | The operator plugs D-MOTO's cable into the OTG adapter | hardware |
| USBOK | R11 | The operator presses OK on a USB dialog on D-POCO, and does not tick "Always" | `UsbConfirmActivity` and `UsbPermissionActivity` are system dialogs |
| H3 | R11, only if cued | On D-MOTO, the operator chooses "USB controlled by: This device" | Android's USB preferences screen has no adb lever |
| RECOVER | R11, at most once | The operator unplugs D-MOTO from the OTG adapter, waits 5 s, plugs it back in | the first USB bring-up after a cable event can fail (`Found device already in accessory mode`) |
| OTG-OUT, CABLES-BACK | X | The operator unplugs D-MOTO from the OTG adapter, removes the adapter, and plugs both phones back into the PC | hardware |

`cue` prints and beeps each step at the moment of the action and writes it to
`$OUT/hand-steps.log`. The script injects no input on any unit. Before Stage U, tell the operator in
one message: "Cues arrive with a beep. Answer a USB dialog on D-POCO only at a USBOK cue. Do not tick
Always. Do not touch either phone without a cue."

## 4. Settings keys

Write with the app stopped, with `put`. `put` starts every write from the unit's own backup. Read
the keys back with `rd` before the launch. A key that does not read back voids the run before
launch: write it again.

`MAC` is D-MOTO's Bluetooth address. Set it before `mkbase`.

| Name | Keys |
|---|---|
| `BASE` | `mkbase` below, as in round 2: mode 3, poke list `$MAC` only, every auto-start off, `log-level` 2, starvation cap and wake verdict deleted |
| `A0`, `A1` | `bool:allow-external-configuration=false`, `=true` |
| `EP_OFF` | `bool:external-power-disconnect=false int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=false` |
| `EP_ON` | `bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=false` |
| `EP_MIN_SCR` | `bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=2 bool:external-power-screen-off=true` |
| `UKEYS` | see below |

**ukeys.sh**
```bash
mkbase() { BASE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MAC str:last-connected-native-mac=$MAC str:native-preferred-device-mac=$MAC set:auto-start-bt-macs= bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:auto-start-on-usb=false bool:kill-on-disconnect=false int:onboarding-version=2 int:log-level=2 del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"; }
A0="bool:allow-external-configuration=false"
A1="bool:allow-external-configuration=true"
EP_OFF="bool:external-power-disconnect=false int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=false"
EP_ON="bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=false"
EP_MIN_SCR="bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=2 bool:external-power-screen-off=true"
UKEYS="int:wifi-connection-mode=1 set:connection-modes=usb,wifi bool:auto-start-on-usb=false bool:auto-connect-single-usb=false bool:use-libusb=false set:auto-start-bt-macs= bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:kill-on-disconnect=false int:onboarding-version=2 int:log-level=2 del:video-profile-starvation-cap $A0 $EP_ON"
```

| Run | Keys |
|---|---|
| V3, V4n | `$BASE $A1 $EP_OFF` |
| R5n/R7n | `$BASE $A0 $EP_MIN_SCR` (the 2 is below the floor on purpose; the log must show 5000ms) |
| R11 | `$UKEYS` |

Why the less obvious keys: every auto-start key is off, so a re-arm can only be the feature's own.
`auto-start-on-usb` false stops a plug from starting a session before the capture runs.
`connection-modes` holds `usb`, so the USB path is on. `log-level` 2 (INFO) carries every app line
in section 6. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on C, so markers
work with `$A0`.

## 5. Helpers and verbs

Copy these from `rig-data/rounds/automation-screen-disconnect-round2/` into `$OUT`: `ptr_lib.sh`,
`ascd2_lib.sh`, `sx.py` and `scripts/runlocked.sh`. Source `ptr_lib.sh`, then `ascd2_lib.sh`, then
`ukeys.sh` and `ascd3_lib.sh` (below), so the new functions win. Run each run's script through
`runlocked.sh`: round 2 found that `th_gate` leaves a watcher that holds the rig lock, and that
script kills it. Use only `th_wait`, `th_gate` and `th_report` from `ptr_lib.sh`.

**ascd3_lib.sh**
```bash
# ascd3_lib.sh : source after ptr_lib.sh, ascd2_lib.sh and ukeys.sh. Needs OUT, U, UN, PH, RUN.
# phone_off : svc only, no airplane mode (TESTING-TEMPLATE.md section 4 step 1)
phone_off() { adb -s $1 shell svc wifi disable; sleep 0.3; adb -s $1 shell svc bluetooth disable; sleep 5
  adb -s $1 shell dumpsys wifi | grep -a -m1 'Wi-Fi is'; sleep 0.3
  adb -s $1 shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'; sleep 0.3; }
# ready TAG : light the panel and dismiss a keyguard; write the readings to $OUT/$RUN.w-TAG and kg-TAG
ready() { adb -s $U shell input keyevent 224; sleep 0.5; adb -s $U shell wm dismiss-keyguard; sleep 1
  wake | tee "$OUT/$RUN.w-$1"; kg | tee "$OUT/$RUN.kg-$1"; }
# kgset true|false : D-POCO's "no lock screen" setting, then read it back
kgset() { adb -s $U shell locksettings set-disable $1; sleep 0.5; adb -s $U shell locksettings get-disabled; sleep 0.3; }
# usbwait S : wait up to S s for a new SSL line; cue USBOK while a USB system dialog has focus. 0 = SSL seen.
usbwait() { local s=$1 b i f; b=$(wc -l < "$(SF)")
  for i in $(seq 1 $((s/3))); do
    tail -n +"$((b+1))" "$(SF)" | grep -aqF 'SSL handshake complete' && return 0
    f=$(focus)
    case "$f" in *UsbConfirmActivity*|*UsbPermissionActivity*|*UsbResolverActivity*)
      cue "USBOK: press OK on the USB dialog on D-POCO; do not tick Always"
      echo "$(date +%T) $RUN dialog: $f" >> "$OUT/hand-steps.log"; sleep 8;; esac
    sleep 2; done; return 1; }
# wr WINDOW : count the wired-session re-arm lines in WINDOW (the source line holds a dash we do not grep)
wr() { printf '%s\n' "$1" | grep -aF 'AapService: wired session ended' | grep -acF 're-arming wireless mode'; }
```

**Verbs and commands used, and nothing else acts on the app:**

| Action | Command |
|---|---|
| arm Native AA (a launch arms it; the verb only if not) | `launch_arm <marker>` |
| user exit from the projection | `send ACTION_DISCONNECT` (inside `endrun`) |
| USB button | `send ACTION_CHECK_USB` |
| screen off, the verb under test | `send ACTION_SCREEN_OFF` |
| screen on, the verb under test | `send ACTION_SCREEN_ON` |
| marker | `mark <label>` (labels have no spaces) |
| build identity | `state` |
| launch | `adb -s $U shell am start -n $MAIN` |
| external power lost, back | `pw_lost`, `pw_back` (fake lever: `dumpsys battery unplug`, `dumpsys battery set ac 1`) |
| grant the device admin | `adb -s $U shell dpm set-active-admin --user 0 $ADMIN` |

Every verb must print `AutomationReceiver: ` in the head unit capture. A step with no such line never
landed: that step is void, not a FAIL. Save each verb's `am` output to a `.reply` file. Never run two
adb calls against one unit at the same time.

## 6. The deciding lines

Every app string below matched `grep -F -r` in `app/src/main` at `b5bd8145`. All are INFO or WARN,
so `log-level` 2 carries them.

| Line (grep -F) | Source | Used for |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | a verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` | markers |
| `WakeDetect: POWER_DISCONNECTED` | `AapService` | the power-lost lever landed |
| `WakeDetect: POWER_CONNECTED` | `AapService` | the power-back lever landed |
| `WakeDetect: SCREEN_OFF` | `AapService` | the panel went off |
| `WakeDetect: SCREEN_ON (screen was off for ` | `AapService` | the panel came on |
| `WakeDetect: power lost beside a screen-off; treating it as the car being switched off` | `AapService` | must stay 0 |
| `WakeDetect: re-arming the wireless bring-up held while the unit was asleep` | `AapService` | record in R7n |
| `WakeDetect: hibernate wake detected` | `AapService` | record in R7n |
| `AapService: external-power disconnect: power lost; ending the session in ` | `AapService` | A1; the delay follows, then `ms unless it comes back.` |
| `ms unless it comes back.` | `AapService` | A1 tail (`delay` reads it) |
| `AapService: external-power disconnect: power stayed away; ending the session the way the Exit button does.` | `AapService` | A6 |
| `AapService: external-power disconnect: not ending the session (enabled=` | `AapService` | must stay 0 |
| `AapService: external-power disconnect: power is back; re-arming the wireless bring-up.` | `AapService` | wireless return (R7n); must stay 0 in R11 |
| `AapService: external-power disconnect: power is back; checking USB again.` | `AapService` | USB return (R11); must stay 0 in R7n |
| `AapService: external-power disconnect: power is back while the session is still ending; the return runs once it has ended.` | `AapService` | record |
| `external-power disconnect` | `AapService` | the co-filter of `ep` |
| `keeping the wireless bring-up down until something re-arms it.` | `AapService` (after the label) | the stand-down; count with `ep` |
| `: screen off ` | `AapService` (after the label) | the stand-down screen-off; count with `ep`; ends `done.` or `refused by the system.` |
| `done.` | `AapService` | the screen-off worked |
| `refused by the system.` | `AapService` | the screen-off did not work |
| `: power came back while the session was ending; not standing down.` | `AapService` (after the label) | record |
| `AapService: external-power disconnect: the session end never reached the stand-down in ` | `AapService` | must stay 0 |
| `AapService: wired session ended by an auto-disconnect; the stand-down decides the re-arm of wireless mode` | `AapService` | R11: the hold (new in C) |
| `the wired session's wireless re-arm is dropped with the stand-down.` | `AapService` (after the label) | R11: must stay 0 |
| `AapService: wired session ended` | `AapService` | co-filter of `wr` |
| `re-arming wireless mode` | `AapService` | the wired re-arm, counted with `wr` |
| `USB session established while wireless mode` | `AapService` | R11 validity: the USB session stopped the wireless stack |
| `WifiLauncher: Initializing WiFi Mode: ` | `WifiLauncherManager` | the wireless stack started |
| `Requesting USB permission for` | `UsbLauncherManager` | record the USB identities |
| `Found device already in accessory mode` | `UsbLauncherManager` | the re-attach fault (R11 condition 10) |
| `Version request send failed` | `AapTransport` | the re-attach fault (R11 condition 10) |
| `AapService: turning the screen on (` | `AapService` | screen-on ran; then `automation` or `external power back` |
| `) by wake lock and ` | `AapService` | screen-on also raised a screen; then `the home screen` or `the projection`, then ` by <DIRECT/OVERLAY/NOTIFICATION>`, then ` (keyguard=<true/false/null>)` |
| `(keyguard=` | `AapService` | the keyguard reading at the raise; the value follows, then `)` |
| `: screen-on asks the system to dismiss the keyguard` | `MainActivity` or `AapProjectionActivity` (before the colon) | the raised screen asked for the dismiss |
| `: the keyguard was dismissed` | the same | the dismiss worked |
| `: the keyguard stays up (the user did not unlock)` | the same | must stay 0 (no secure keyguard here) |
| `: the system refused to dismiss the keyguard` | the same | must stay 0 |
| `: keyguard dismiss request failed: ` | the same | must stay 0 |
| `the home screen` | `AapService` | which screen the raise named |
| `the projection` | `AapService` | which screen the raise named |
| `AapService: screen-on launch failed: ` | `AapService` | must stay 0 |
| `AapTransport stopping and sending byebye (` | `AapTransport` | our side sent the ByeBye |
| `Disconnect action received.` | `AapService` | the reference exit in `endrun` |
| `AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected` | `AapService` | a session ended |
| `SSL handshake complete` | `AapSslContext` | a session formed |
| `Throughput over ` | `VideoDecoder` | picture; `rend` regex `Throughput over [0-9]+ms: rendered=[1-9]` |
| `MATCH! Starting AapService` | `AutoStartReceiver` | discard check |
| `"screenOff"` | `AutomationEffectRunner` reply key | V3 and V4n: `"screenOff":true` |

Not in our tree:

| Line | Where | Used for |
|---|---|---|
| `received ByeByeRequest` | the phone, tag `CAR.GAL.GAL.LITE` (set VERBOSE by `cap_start`); present on Gearhead 18.0 in round 2 | the phone got our ByeBye |
| `OHURIG` | the phone, our own `log -t` markers | phone windows |
| `device wake-up` | head unit, `PowerManagerService` | record for every screen-on |
| `Lock credential verified successfully` | D-POCO, `cmd lock_settings verify` | P: no lock credential |

Framework readings, not log lines: `mWakefulness=` or `mScreenOn=` from `wake` (`dark`: `Asleep`,
`Dozing` or `mScreenOn=false`; `lit`: `Awake` or `mScreenOn=true`), the keyguard from `kg`, the
focused window from `focus`, the admin count from `admin`.

## 7. Runs

Every run: `th_gate` first (inside `bringup` for session runs; `$RIG_TOOLKIT/rig_thermal.sh wait 75`,
and stop the round after a second failed wait, per `rig-quirks/topics/tooling.md`). Report
`th_report $RUN`. Write the results file after every run. Order: R0, P, Stage A (grant, V3, keyguard
off, V4n, R5n/R7n, keyguard back, V4k, R5k/R7k, HS2), Stage U (R11), X.

**Session runs** (V4n, R5n/R7n, V4k, R5k/R7k) set `PHCAP=1` and `K` before `bringup`. If `ssl=1`, run `endrun` and
repeat the run once. A second `ssl=1` makes that run UNTESTABLE. **Every session run is valid only
when** `hwin $RUN-arm $RUN-cut | rend` is 1 or more, `MATCH! Starting AapService` is 0 in
`hwin $RUN-start $RUN-end`, and `$RUN.summary` reads `wrapped=no`. Report all three. A run with
`wrapped=yes` is void: run it once more.

`gap` and `aftms` use the time of day. Do not let a run cross midnight.

**Stop rule:** if V4n and R5n/R7n both end UNTESTABLE, run "Keyguard back", skip V4k and R5k/R7k,
run HS2, then go to Stage U. The same holds if V4k ends UNTESTABLE for a session fault. In Stage U, R11 gets one RECOVER at most; no session after it makes R11 UNTESTABLE.

### P. Precheck, once

Apps stopped on every unit. Record every output in Setup notes.

**p_precheck.sh**
```bash
POCO=4f4027e9; MOTO=ZY22GC3BM4; DHU=27870808938846; PKG=com.andrerinas.headunitrevived
adb -s $POCO shell am force-stop $PKG; sleep 0.3
adb -s $POCO shell run-as $PKG cat shared_prefs/settings.xml > $OUT/backup-D-POCO.xml
diff <(tr -d '\r' < rig-data/rounds/automation-screen-disconnect-round2/backup-D-POCO.xml) <(tr -d '\r' < $OUT/backup-D-POCO.xml)   # record the delta, even if zero
adb -s $POCO shell dumpsys battery | grep -a -E 'AC powered|USB powered|level'      # level 60 or more before Stage U
adb -s $MOTO shell dumpsys battery | grep -a -E 'level'
adb -s $POCO shell cmd lock_settings verify                 # must print "Lock credential verified successfully"
adb -s $POCO shell locksettings get-disabled                # record as KG_POCO (round 2 implies false)
adb -s $POCO shell settings get system screen_off_timeout   # record as TIMEOUT_POCO
U=$POCO admin                                               # must be 0
adb -s $POCO shell dumpsys bluetooth_manager | grep -a -A14 'Bonded devices' | grep -ac 'motorola edge 30 neo'   # 1 or more
adb -s $MOTO shell settings get secure bluetooth_address    # MOTO_MAC (round 2: A0:46:5A:97:E4:95)
adb -s $MOTO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb -s $MOTO shell dumpsys window | grep -a -m2 -oE '(isKeyguardShowing|mShowingLockscreen)=[a-z]+'
adb -s $MOTO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'   # isGroupOwner false
adb -s $POCO shell settings put system screen_off_timeout 1800000                 # X restores TIMEOUT_POCO
```

Then:

1. If `cmd lock_settings verify` does not print `Lock credential verified successfully`, stop and
   tell the operator: `lockNow()` would lock D-POCO behind a credential adb cannot enter.
2. If D-MOTO's keyguard reads `true` and its screen asks for a PIN, cue HS0 once.
3. If the D-MOTO bond count is 0, the Stage A session runs are UNTESTABLE. Stage U does not need it.
4. If D-POCO's battery reads below 60, charge it before Stage U and record the level.
5. Run R0's check: `U=$POCO state`.

### Stage A. D-POCO as head unit, D-MOTO as phone, both on PC cables

**stage_a.sh**
```bash
U=$POCO; UN=D-POCO; PH=$MOTO; LEVER=fake; MAC=<MOTO_MAC from P>; mkbase
phone_off $MOTO
```

#### Grant

```bash
adb -s $U shell dpm set-active-admin --user 0 $ADMIN; sleep 1; admin     # must read 1
```

If `dpm` fails, use HS3:

```bash
adb -s $U shell am start -a android.app.action.ADD_DEVICE_ADMIN --ecn android.app.extra.DEVICE_ADMIN $ADMIN; sleep 3
cue "HS3: accept the device admin screen for Open Headunit on D-POCO"
sleep 30; admin                                                          # must read 1
```

If `admin` still reads 0, V3, V4n, R5n/R7n, V4k and R5k/R7k are UNTESTABLE; go to Stage U.

#### V3. The point of the round, part 1: screen-on with the grant, no session

**v3.sh**
```bash
RUN=V3; PHCAP=; CABLE=; adb -s $U shell am force-stop $PKG; sleep 0.3; fakeplug
K="$BASE $A1 $EP_OFF"; put $K; rd 'allow-external|external-power' | tee $OUT/$RUN.keys
th_gate; cap_start; mark V3-start; adb -s $U shell am start -n $MAIN; sleep 10
ready pre
mark V3-off; send ACTION_SCREEN_OFF > $OUT/V3-off.reply; sleep 3; wake | tee $OUT/V3.w-off; kg | tee $OUT/V3.kg-off
sleep 10; mark V3-on; send ACTION_SCREEN_ON > $OUT/V3-on.reply; sleep 5; wake | tee $OUT/V3.w-on; focus | tee $OUT/V3.focus; kg | tee $OUT/V3.kg-on
mark V3-end; clean_hu; cap_stop; th_report V3
```

`V3.w-pre` must be lit. If it is dark, the run is void: run it once more.

**PASS when all hold:**
1. `V3-off.reply` holds `"screenOff":true` and `"ok":true`; `V3.w-off` is dark; `hwin V3-off V3-on` holds 1 `WakeDetect: SCREEN_OFF`.
2. `hwin V3-off V3-on`: `WakeDetect: power lost beside a screen-off` is 0.
3. `hwin V3-on V3-end`: 1 `AapService: turning the screen on (` line, and that line holds `automation`, `) by wake lock and ` and `the home screen`. `V3.w-on` is lit. `gap "$(hwin V3-on V3-end)" 'AapService: turning the screen on (' 'WakeDetect: SCREEN_ON (screen was off for '` is 0 to 5000.
4. `AapService: screen-on launch failed: ` is 0 in the capture.
5. If `V3.kg-off` reads `isKeyguardShowing=true`: the screen-on line ends `(keyguard=true)`; `hwin V3-on V3-end` holds 1 `: screen-on asks the system to dismiss the keyguard` and 1 `: the keyguard was dismissed`, both on `MainActivity` lines; `V3.kg-on` reads `isKeyguardShowing=false`; `V3.focus` names `MainActivity`. If `V3.kg-off` reads `false`, condition 5 is INCONCLUSIVE (no keyguard to dismiss).

If the change did nothing, condition 3 would read `... by wake lock` alone, as in round 2. Record
the whole screen-on line (the strategy), the `device wake-up` lines in `hwin V3-on V3-end`, both `kg`
readings and `V3.focus`.

#### Keyguard off

```bash
kgset true | tee $OUT/kg-off.txt          # must print true
```

If it does not print `true`, V4n and R5n/R7n are UNTESTABLE (no keyguard-free state); go to HS2.

#### V4n. Screen-on during a session, no keyguard

**v4n.sh**
```bash
RUN=V4n; PHCAP=1; CABLE=; K="$BASE $A1 $EP_OFF"; ready pre; bringup; ready live
sleep 20; mark V4n-cut; mark V4n-off; send ACTION_SCREEN_OFF > $OUT/V4n-off.reply; sleep 10; wake | tee $OUT/V4n.w-off; kg | tee $OUT/V4n.kg-off
mark V4n-on; send ACTION_SCREEN_ON > $OUT/V4n-on.reply; sleep 8; wake | tee $OUT/V4n.w-on; focus | tee $OUT/V4n.focus
sleep 15; mark V4n-after; endrun
```

Validity, beside the session-run rule: `V4n.kg-off` reads `isKeyguardShowing=false`. If it reads
`true`, the keyguard setting did not hold: V4n is UNTESTABLE.

**PASS when all hold:**
1. `V4n-off.reply` holds `"screenOff":true`; `V4n.w-off` is dark; `V4n.w-on` is lit.
2. `hwin V4n-off V4n-after`: `WakeDetect: power lost beside a screen-off` is 0.
3. If `hwin V4n-off V4n-on | c 'since connection is disconnected'` is 0 (the session survived): `hwin V4n-on V4n-after` holds 1 `AapService: turning the screen on (` line, and it holds `automation`, `) by wake lock and ` and `the projection`; `V4n.focus` names `AapProjectionActivity`; `hwin V4n-on V4n-after | rend` is 1 or more.
4. If that count is 1 or more (the sleep path ended the session): the screen-on line holds `the home screen`. Report it as a finding about the sleep path, not a FAIL of this change.
5. Phone: `pwin V4n-off V4n-after | c 'received ByeByeRequest'` is 0 when condition 3 applies.

With no keyguard, the projection comes back and renders even without a raise. So the screen-on line
is the evidence for the raise, and the focus and `rend` show that the raise did no harm. Record
which of 3 or 4 applied and the strategy.

#### R5n and R7n. Screen-off after the end, then power back, no keyguard

Precondition: `admin` reads 1. The panel stays dark for about 40 s, under the 60 s hibernate
threshold.

**r5n_r7n.sh**
```bash
RUN=R5n; PHCAP=1; CABLE=; K="$BASE $A0 $EP_MIN_SCR"; ready pre; bringup; ready live
sleep 20; mark R5n-cut; pw_lost; sleep 20
wake | tee $OUT/R5n.wake; kg | tee $OUT/R5n.kg
sleep 20; pw_back; mark R7n-back; sleep 5; wake | tee $OUT/R7n.wake; kg | tee $OUT/R7n.kg
waitfor 120 'SSL handshake complete'; echo "ssl2=$?" | tee -a $OUT/R5n.summary
sleep 15; focus | tee $OUT/R7n.focus; endrun
```

`ALL=$(hwin R5n-cut R5n-x-go)`, `W=$(printf '%s\n' "$ALL" | upto 'WakeDetect: POWER_CONNECTED')`,
`V=$(printf '%s\n' "$ALL" | from 'WakeDetect: POWER_CONNECTED')`.

**R5n PASS when all hold** (on `W`; a regression check, R5 passed in round 2):
1. 1 A1 line, and `delay "$W"` is 5000 (the 2 was clamped).
2. A6 1; `gap "$W" 'power lost; ending the session in ' 'power stayed away;'` is 4500 to 8000.
3. `printf '%s\n' "$W" | ep 'keeping the wireless bring-up down'` is 1; `printf '%s\n' "$W" | grep -aF ': screen off ' | grep -acF 'done.'` is 1; `refused by the system.` is 0.
4. `WakeDetect: SCREEN_OFF` 1, and `gap "$W" 'WakeDetect: POWER_DISCONNECTED' 'WakeDetect: SCREEN_OFF'` is over 5000.
5. `WakeDetect: power lost beside a screen-off` is 0.
6. `R5n.wake` is dark, and `R5n.kg` reads `isKeyguardShowing=false` (else R7n is UNTESTABLE: the keyguard setting did not hold).
7. Phone: `pwin R5n-cut R7n-back | c 'received ByeByeRequest'` is 1 or more.

**R7n PASS when all hold** (on `V`):
1. `WakeDetect: POWER_CONNECTED` 1.
2. 1 `AapService: turning the screen on (` line holding `external power back`; `gap "$V" 'WakeDetect: POWER_CONNECTED' 'AapService: turning the screen on ('` is 0 to 1000.
3. `power is back; re-arming the wireless bring-up.` 1; `gap "$V" 'AapService: turning the screen on (' 'power is back; re-arming'` is 0 or more; `power is back; checking USB again.` is 0.
4. INCONCLUSIVE by rule (the ROM lights the panel on plug): report `gap "$V" 'AapService: turning the screen on (' 'WakeDetect: SCREEN_ON (screen was off for '`.
5. INCONCLUSIVE by rule: report `R7n.wake`.
6. `ssl2=0`; `printf '%s\n' "$V" | rend` is 1 or more; `R7n.focus` names `AapProjectionActivity`.
7. `AapService: screen-on launch failed: ` is 0.

Record the whole screen-on line (expected: `... by wake lock` alone, because the ROM lit the panel
first), the `device wake-up` lines, `R7n.kg`, and the counts of `WakeDetect: re-arming the wireless
bring-up held while the unit was asleep` and `WakeDetect: hibernate wake detected` in `V`.

#### Keyguard back

```bash
kgset <KG_POCO from P> | tee $OUT/kg-back.txt     # must print KG_POCO
```

If KG_POCO is `true`, D-POCO has no keyguard to dismiss: V4k and R5k/R7k are UNTESTABLE; go to HS2.

#### V4k. Screen-on during a session, with the swipe keyguard

**v4k.sh**
```bash
RUN=V4k; PHCAP=1; CABLE=; K="$BASE $A1 $EP_OFF"; ready pre; bringup; ready live
sleep 20; mark V4k-cut; mark V4k-off; send ACTION_SCREEN_OFF > $OUT/V4k-off.reply; sleep 10; wake | tee $OUT/V4k.w-off; kg | tee $OUT/V4k.kg-off
mark V4k-on; send ACTION_SCREEN_ON > $OUT/V4k-on.reply; sleep 8; wake | tee $OUT/V4k.w-on; kg | tee $OUT/V4k.kg-on; focus | tee $OUT/V4k.focus
sleep 15; mark V4k-after; endrun
```

Validity, beside the session-run rule: `V4k.kg-off` reads `isKeyguardShowing=true`. If it reads
`false`, `lockNow()` put up no keyguard: V4k is UNTESTABLE.

**PASS when all hold:**
1. `V4k-off.reply` holds `"screenOff":true`; `V4k.w-off` is dark; `V4k.w-on` is lit.
2. `hwin V4k-off V4k-after`: `WakeDetect: power lost beside a screen-off` is 0.
3. `hwin V4k-on V4k-after` holds 1 `AapService: turning the screen on (` line, and it holds `automation`, `) by wake lock and `, `the projection` and ends `(keyguard=true)`. If `hwin V4k-off V4k-on | c 'since connection is disconnected'` is 1 or more, it holds `the home screen` instead: report that as a finding about the sleep path, and grade conditions 4 to 6 on `MainActivity` in place of `AapProjectionActivity`.
4. `hwin V4k-on V4k-after` holds 1 `: screen-on asks the system to dismiss the keyguard` and 1 `: the keyguard was dismissed`, both on `AapProjectionActivity` lines. `: the keyguard stays up (the user did not unlock)`, `: the system refused to dismiss the keyguard` and `: keyguard dismiss request failed: ` are 0.
5. `V4k.kg-on` reads `isKeyguardShowing=false`; `V4k.focus` names `AapProjectionActivity`.
6. `hwin V4k-on V4k-after | rend` is 1 or more; `AapService: screen-on launch failed: ` is 0.
7. Phone: `pwin V4k-off V4k-after | c 'received ByeByeRequest'` is 0 when the session survived.

Round 2's V4 is the "before": focus `NotificationShade` and `rend` 0 after the screen-on. Record the
whole screen-on line, the strategy, and the gap from the screen-on line to `: the keyguard was
dismissed`.

#### R5k and R7k. Screen-off after the end, then power back, with the swipe keyguard

Precondition: `admin` reads 1. Same timing as R5n/R7n.

**r5k_r7k.sh**
```bash
RUN=R5k; PHCAP=1; CABLE=; K="$BASE $A0 $EP_MIN_SCR"; ready pre; bringup; ready live
sleep 20; mark R5k-cut; pw_lost; sleep 20
wake | tee $OUT/R5k.wake; kg | tee $OUT/R5k.kg
sleep 20; pw_back; mark R7k-back; sleep 5; wake | tee $OUT/R7k.wake; kg | tee $OUT/R7k.kg
waitfor 120 'SSL handshake complete'; echo "ssl2=$?" | tee -a $OUT/R5k.summary
sleep 15; kg | tee $OUT/R7k.kg-after; focus | tee $OUT/R7k.focus; endrun
```

`ALL=$(hwin R5k-cut R5k-x-go)`, `W=$(printf '%s\n' "$ALL" | upto 'WakeDetect: POWER_CONNECTED')`,
`V=$(printf '%s\n' "$ALL" | from 'WakeDetect: POWER_CONNECTED')`.

**R5k PASS when** R5n conditions 1 to 5 and 7 hold on this `W`, with `R5k` and `R7k` in place of
`R5n` and `R7n`, and `R5k.wake` is dark. `R5k.kg` must read `isKeyguardShowing=true`; if it reads
`false`, `lockNow()` put up no keyguard and R7k is UNTESTABLE.

**R7k PASS when all hold** (on `V`):
1. `WakeDetect: POWER_CONNECTED` 1.
2. 1 `AapService: turning the screen on (` line holding `external power back`, `) by wake lock and `, `the home screen` and ending `(keyguard=true)`; `gap "$V" 'WakeDetect: POWER_CONNECTED' 'AapService: turning the screen on ('` is 0 to 1000.
3. 1 `: screen-on asks the system to dismiss the keyguard` and 1 `: the keyguard was dismissed`, both on `MainActivity` lines. `: the keyguard stays up (the user did not unlock)`, `: the system refused to dismiss the keyguard` and `: keyguard dismiss request failed: ` are 0.
4. INCONCLUSIVE by rule (the ROM lights the panel on plug): report `R7k.wake`.
5. `power is back; re-arming the wireless bring-up.` 1; `power is back; checking USB again.` is 0.
6. `ssl2=0`; `printf '%s\n' "$V" | rend` is 1 or more; `R7k.kg-after` reads `isKeyguardShowing=false`; `R7k.focus` names `AapProjectionActivity`.
7. `AapService: screen-on launch failed: ` is 0.

Round 2's R7 is the "before": the new session's projection stayed behind the keyguard, rendered
nothing, and `ProjectionRaiseDeadlinePolicy` ended it at 16 s. Record the whole screen-on line, `R7k.kg`,
and the gap from `: the keyguard was dismissed` to `SSL handshake complete`.

#### HS2

```bash
adb -s $U shell am force-stop $PKG; sleep 0.3
adb -s $U shell am start -n 'com.android.settings/.Settings\$DeviceAdminSettingsActivity'; sleep 3
focus                                                                     # must name DeviceAdminSettingsActivity
cue "HS2: deactivate Open Headunit on the device admin screen of D-POCO"
sleep 30; admin                                                           # must read 0
adb -s $U shell input keyevent 3
```

The `\$` is needed: the device shell otherwise eats the `$` and opens plain Settings (round 2). If
`admin` does not read 0 after 120 s, **stop and tell the operator**: the grant blocks an uninstall.

### Stage U. D-POCO as USB host, D-MOTO on its OTG port, both on wireless adb

D-HU stays on its cable and idle. Read both addresses while the cables are still in.

**stage_u_setup.sh**
```bash
adb -s $MOTO shell svc wifi enable; sleep 0.3; adb -s $POCO shell svc wifi enable; sleep 10
POCO_IP=$(adb -s $POCO shell ip -4 -o addr show wlan0 | awk '{print $4}' | cut -d/ -f1); sleep 0.3
MOTO_IP=$(adb -s $MOTO shell ip -4 -o addr show wlan0 | awk '{print $4}' | cut -d/ -f1); sleep 0.3
echo "POCO_IP=$POCO_IP MOTO_IP=$MOTO_IP" | tee $OUT/ips.env          # both must be non-empty
adb -s $POCO tcpip 5555; sleep 3; adb -s $MOTO tcpip 5555; sleep 3
adb connect $POCO_IP:5555; sleep 1; adb connect $MOTO_IP:5555; sleep 1; adb devices | tee $OUT/adb-devices.txt
POCO_W=$POCO_IP:5555; MOTO_W=$MOTO_IP:5555
adb -s $POCO_W shell getprop ro.product.device; sleep 0.3          # surya
adb -s $MOTO_W shell getprop ro.product.device; sleep 0.3          # miami
cue "CABLES: unplug D-POCO and D-MOTO from the PC. Put the OTG adapter in D-POCO. Do not plug D-MOTO in yet"
while adb -s $POCO get-state >/dev/null 2>&1; do sleep 1; done
adb -s $POCO_W get-state; adb -s $MOTO_W get-state                  # both "device"
adb -s $MOTO_W shell svc bluetooth disable; sleep 0.3
U=$POCO_W; UN=D-POCO; PH=$MOTO_W; LEVER=fake
```

If either address is empty after 30 s, or either `get-state` does not read `device`, R11 is
UNTESTABLE. Go to X.

#### R11. The point of the round, part 2: a USB session ended by power loss

**r11.sh**
```bash
RUN=R11; PHCAP=1; CABLE=; adb -s $U shell am force-stop $PKG; sleep 0.3; fakeplug
K="$UKEYS"; put $K; rd 'external-power|wifi-connection-mode|connection-modes|auto-start-on-usb|log-level' | tee $OUT/$RUN.keys
th_gate; cap_start; mark R11-start
adb -s $U shell am start -n $MAIN; waitfor 15 'WifiLauncher: Initializing WiFi Mode: '; echo "armed=$?" | tee -a $OUT/R11.summary
sleep 10; ready pre; mark R11-plug
cue "OTG: plug D-MOTO's USB cable into the OTG adapter on D-POCO now"
usbwait 60; u=$?
if [ $u -ne 0 ]; then
  adb -s $PH shell dumpsys window | grep -a -m1 mCurrentFocus | tee $OUT/R11.moto-focus; sleep 0.3
  grep -aq 'Usb' $OUT/R11.moto-focus && cue "H3: on D-MOTO choose USB controlled by: This device"
  send ACTION_CHECK_USB > $OUT/R11-check1.reply; usbwait 60; u=$?; fi
if [ $u -ne 0 ]; then cue "RECOVER: unplug D-MOTO from the OTG adapter, wait 5 s, plug it back in"; sleep 10; usbwait 60; u=$?; fi
echo "ssl=$u" | tee -a $OUT/R11.summary
sleep 20; ready live; mark R11-cut; pw_lost; sleep 90
focus | tee $OUT/R11.focus-quiet
pw_back; mark R11-back
usbwait 90; echo "ssl2=$?" | tee -a $OUT/R11.summary
sleep 15; endrun
cue "OTG-OUT: unplug D-MOTO from the OTG adapter"
```

If `ssl=1`, R11 is UNTESTABLE: run `endrun`, then go to X. Do not answer a USB dialog that appears
between `R11-cut` and `R11-back`: a new session there would mix a second cause into the quiet
window. The script cues USBOK only outside that window.

`ALL=$(hwin R11-cut R11-x-go)`, `W1=$(printf '%s\n' "$ALL" | upto 'WakeDetect: POWER_CONNECTED')`,
`Wq=$(printf '%s\n' "$W1" | from 'power stayed away;')`,
`W3=$(printf '%s\n' "$ALL" | from 'WakeDetect: POWER_CONNECTED')`, `P1=$(pwin R11-cut R11-back)`.

**PASS when all hold:**
1. Validity:
   a. `hwin R11-start R11-plug | c 'SSL handshake complete'` is 0 (no wireless session formed before the plug), and `ssl=0`.
   b. `hwin R11-plug R11-cut | rend` is 1 or more, and `MATCH! Starting AapService` is 0 in `hwin R11-start R11-end`, and `wrapped=no`.
   c. `hwin R11-plug R11-cut | c 'USB session established while wireless mode'` is 1. If it is 0, the USB session never stopped the wireless stack, the path under test was not reached, and R11 is INCONCLUSIVE.
   d. `W1` holds 1 `WakeDetect: POWER_DISCONNECTED`; `gap "$ALL" 'power stayed away;' 'WakeDetect: POWER_CONNECTED'` is 60000 or more.
2. A1: `W1` holds 1 line with `AapService: external-power disconnect: power lost; ending the session in `; `delay "$W1"` is 20000; `gap "$W1" 'WakeDetect: POWER_DISCONNECTED' 'power lost; ending the session in '` is 0 to 1000.
3. A6: `W1` holds 1 A6 line; `gap "$W1" 'power lost; ending the session in ' 'power stayed away;'` is 19500 to 23000.
4. Exit flavour: `gap "$W1" 'power stayed away;' 'AapTransport stopping and sending byebye ('` is 0 to 2000.
5. Phone: `printf '%s\n' "$P1" | c 'received ByeByeRequest'` is 1 or more.
6. The hold: `W1` holds 1 `AapService: wired session ended by an auto-disconnect; the stand-down decides the re-arm of wireless mode` line; `gap "$W1" 'power stayed away;' 'the stand-down decides the re-arm'` is 0 to 5000; `printf '%s\n' "$W1" | ep 'keeping the wireless bring-up down until something re-arms it.'` is 1; `the wired session's wireless re-arm is dropped with the stand-down.` is 0.
7. Quiet: in `Wq` (A6 to power back), `wr "$Wq"` is 0, `WifiLauncher: Initializing WiFi Mode: ` is 0 and `SSL handshake complete` is 0.
8. Return: `W3` holds 1 `WakeDetect: POWER_CONNECTED` and 1 `power is back; checking USB again.`, with `gap "$W3" 'WakeDetect: POWER_CONNECTED' 'checking USB again.'` 0 to 1000; `wr "$W3"` is 1, with `gap "$W3" 'checking USB again.' 're-arming wireless mode'` 0 to 1000; `WifiLauncher: Initializing WiFi Mode: ` is 1 or more in `W3`, with `gap "$W3" 're-arming wireless mode' 'WifiLauncher: Initializing WiFi Mode: '` 1400 to 5000; `power is back; re-arming the wireless bring-up.` is 0 in `W3`.
9. `the session end never reached the stand-down in ` is 0 in `ALL`.
10. New USB session: `ssl2=0`, and `printf '%s\n' "$W3" | rend` is 1 or more. If `ssl2=1` and `W3` holds `Found device already in accessory mode` or `Version request send failed`, condition 10 is INCONCLUSIVE: that re-attach fault belongs to another thread. Otherwise `ssl2=1` is a FAIL.

If the fix did nothing, condition 6's hold line would be absent, and condition 7 would count 1 wired
re-arm and 1 `WifiLauncher: Initializing WiFi Mode: ` within about 2 s of A6. Condition 1c rules out
a PASS from a USB session that never stopped the stack.

Report: every count in conditions 6 to 8, the A6 to hold-line gap, the quiet window in ms, the
`checking USB again.` to `Initializing` gap, the POWER_CONNECTED to SSL gap in `W3`, every
`Requesting USB permission for` line in `W3` (the identity after `for`), `R11.focus-quiet`, and each
hand step with its time from `hand-steps.log`.

### X. Clean-up

**x_cleanup.sh**
```bash
U=$POCO_W; UN=D-POCO; put; adb -s $U shell run-as $PKG cat shared_prefs/settings.xml > $OUT/restored-D-POCO.xml
diff <(tr -d '\r' < $OUT/backup-D-POCO.xml) <(tr -d '\r' < $OUT/restored-D-POCO.xml)   # must be empty
adb -s $POCO_W usb; sleep 3; adb -s $MOTO_W usb; sleep 3
cue "CABLES-BACK: remove the OTG adapter from D-POCO. Plug D-POCO and D-MOTO back into the PC"
adb -s $POCO wait-for-device; adb -s $MOTO wait-for-device; adb disconnect $POCO_W; adb disconnect $MOTO_W
for s in $POCO $MOTO; do adb -s $s shell dumpsys battery reset; sleep 0.3
  adb -s $s shell dumpsys battery | grep -a -E 'AC powered|USB powered'; sleep 0.3; done      # a real source true
adb -s $POCO shell settings put system screen_off_timeout <TIMEOUT_POCO>
adb -s $POCO shell locksettings get-disabled                                 # must print KG_POCO
U=$POCO admin                                                                # must read 0
adb -s $DHU shell svc bluetooth enable; sleep 3; adb -s $DHU shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'
```

If Stage U did not run, use `U=$POCO` for the `put` and skip the wireless lines. Put D-MOTO's radios
back to the state P found. If `admin` does not read 0 or the keyguard setting differs from
`KG_POCO`, stop and tell the operator.

## 8. Do not re-run

- **R0's unit tests.** 3521 JVM tests, 0 failures, on this tree on the dev host.
- **L-MOTO, L-POCO, L-SAM.** Settled in round 2: `MOTO_FAKE=yes`, `LEVER_POCO=fake` with the ROM
  lighting the panel on both plug changes, `LEVER_SAM=cable`.
- **V1, R3, R4, R1, R2, R10, R6, S1, S2.** PASS in round 2. C changes no line on their paths except
  the screen-on order and the keyguard reading, which V3, V4n and V4k grade.
- **T1.** PASS in round 2. The brief's condition 6 check counted wake-lock history; the rig graded it
  on the live wake-lock list, and that grade stands.
- **R5.** PASS in round 2. It runs again only as the first half of R5n/R7n and R5k/R7k.
- **R1s.** UNTESTABLE on D-SAM's cable lever, and that will not change. R1 on D-POCO and S1/S2 on
  D-SAM cover it.
- **A secure keyguard (PIN, pattern or password).** D-POCO must have no lock credential (P), and
  C shows nothing over a secure keyguard by design: the system asks for the credential.
- **C0 on D-HU.** The `dumpsys battery` lever sends no `POWER_*` broadcast there.

## 9. Report back

The numbers that decide shipping:

1. **V3 and V4n:** the whole screen-on line of each (it must name `the home screen` and `the
   projection`), the panel reading after the request, and `V4n.focus` with the `rend` count.
2. **R7n:** `ssl2`, the `rend` count after power back, `R7n.focus`, and the whole screen-on line.
3. **V4k and R7k:** the whole screen-on line of each, the dismiss lines and which activity logged
   them, the `kg` readings before and after, the focus, and the `rend` count.
4. **R11:** the hold line count and its gap after A6, the quiet window counts of the wired re-arm and
   `WifiLauncher: Initializing WiFi Mode: `, and the return gaps (`checking USB again.` to the re-arm,
   the re-arm to `Initializing`).

Give each verdict in the fixed format of `TESTING-TEMPLATE.md` §7, and put every deviation in Setup
notes. Add to `rig-quirks/units/D-POCO.md` whether `locksettings set-disable true` removed the
keyguard after `lockNow()`, whether `requestDismissKeyguard` lifted the swipe keyguard, and how many USB dialogs one OTG plug and one power return raised.

These strings are also grepped, but they are not in our tree: the first is in the phone's Android
Auto log, the second is our own phone marker tag, the third is in the head unit's
`PowerManagerService` log, and the last is shell output on D-POCO.

```external-strings
received ByeByeRequest
OHURIG
device wake-up
Lock credential verified successfully
```

The block below lists every app string the runs grep. Each one is in `app/src/main` at `b5bd8145`.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
WakeDetect: POWER_DISCONNECTED
WakeDetect: POWER_CONNECTED
WakeDetect: SCREEN_OFF
WakeDetect: SCREEN_ON (screen was off for 
WakeDetect: power lost beside a screen-off; treating it as the car being switched off
WakeDetect: re-arming the wireless bring-up held while the unit was asleep
WakeDetect: hibernate wake detected
AapService: external-power disconnect: power lost; ending the session in 
ms unless it comes back.
AapService: external-power disconnect: power stayed away; ending the session the way the Exit button does.
AapService: external-power disconnect: not ending the session (enabled=
AapService: external-power disconnect: power is back; re-arming the wireless bring-up.
AapService: external-power disconnect: power is back; checking USB again.
AapService: external-power disconnect: power is back while the session is still ending; the return runs once it has ended.
external-power disconnect
keeping the wireless bring-up down until something re-arms it.
: screen off 
done.
refused by the system.
: power came back while the session was ending; not standing down.
AapService: external-power disconnect: the session end never reached the stand-down in 
AapService: wired session ended by an auto-disconnect; the stand-down decides the re-arm of wireless mode
the stand-down decides the re-arm
the wired session's wireless re-arm is dropped with the stand-down.
AapService: wired session ended
re-arming wireless mode
USB session established while wireless mode
WifiLauncher: Initializing WiFi Mode: 
Requesting USB permission for
Found device already in accessory mode
Version request send failed
AapService: turning the screen on (
) by wake lock and 
the home screen
the projection
AapService: screen-on launch failed: 
(keyguard=
: screen-on asks the system to dismiss the keyguard
: the keyguard was dismissed
: the keyguard stays up (the user did not unlock)
: the system refused to dismiss the keyguard
: keyguard dismiss request failed: 
MainActivity
AapProjectionActivity
AapTransport stopping and sending byebye (
Disconnect action received.
AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected
SSL handshake complete
Throughput over 
MATCH! Starting AapService
"screenOff"
power lost; ending the session in 
power stayed away;
power is back; re-arming
checking USB again.
keeping the wireless bring-up down
since connection is disconnected
automation
external power back
```
