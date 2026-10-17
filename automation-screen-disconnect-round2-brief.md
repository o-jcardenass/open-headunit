# automation-screen-disconnect, round 2 brief: the power-loss runs on D-POCO and D-SAM

> **Revised 2026-10-17: the candidate was rebased onto `main` `28af450d`.** C moved from `5236a1b4` to
> `8db52ceb`. The two commits are the same patches; only the base moved. Round 1's APK is not this
> candidate, so R0 now builds C and installs it on D-POCO **and** D-SAM. Re-read section 1 (R0) and
> section 8. No other run, string or verdict rule changed. A copy that still names `5236a1b4` is stale.

Publish name on `transfer/rig-rounds`: `automation-screen-disconnect-round2-brief.md`. Results go in
`automation-screen-disconnect-round2-results.md`. Evidence goes in the existing release
`rig-evidence-automation-screen-disconnect` as the asset
`automation-screen-disconnect-round2-captures.zip`.

Round 1 (`archive/rounds/automation-screen-disconnect-round1-results.md`) stopped after C0. On D-HU the
`dumpsys battery` lever sends no `POWER_CONNECTED` or `POWER_DISCONNECTED` broadcast, so no
power-loss run could start there. This round moves every power-loss run to D-POCO and D-SAM, and
first measures which lever works on each unit. D-HU runs nothing.

One candidate, the same patches as round 1 on a new base, so one build (about 25 min). Four units, all on PC cables. About 90
minutes of runs, plus thermal-gate waits (the estimate is the run time plus those waits). The operator must be present for Stage A: at least 2 cable pulls (T1), up to 14 if the fake
lever fails on D-POCO, and one device admin removal.

| Run | What it proves | Head unit | Phone |
|---|---|---|---|
| R0 | build, APK identity on D-POCO and D-SAM | | |
| P | precheck and the bonds the stages need | all | |
| L-MOTO | does the fake lever send the power broadcasts on D-MOTO (record only) | D-MOTO | |
| L-POCO | which lever works on D-POCO, and does its ROM light the panel on a plug change | D-POCO | |
| V1 | the screen verbs before the grant: the gate, the refusal, screen-on open to any caller (API 35) | D-POCO | |
| R3 | power lost with no session: no timer, the poke loop keeps running | D-POCO | |
| R4 | switch off: no line, no timer, the session stays (also the control that a pull alone ends nothing) | D-POCO | D-MOTO |
| **R1** | **the point of the round: the session ends like Exit after the delay, the stack stays down, power back re-arms and a session forms** | D-POCO | D-MOTO |
| R2 | power back inside the delay cancels; a lost, back, lost flap keeps one timer (flap: fake lever only) | D-POCO | D-MOTO |
| R6 | screen row on, no grant: the warning, the panel stays lit, the session still ends | D-POCO | D-MOTO |
| R10 | the service stops inside the delay: no timer survives it (fake lever only) | D-POCO | D-MOTO |
| T1 | a real cable pull with the panel off: the timer keeps its deadline while the SoC may suspend | D-POCO | D-MOTO |
| V3 | the screen verbs with the grant, no session | D-POCO | |
| V4 | the screen verbs during a session: screen-on brings back the projection | D-POCO | D-MOTO |
| R5, R7 | screen row on, grant held, delay at the minimum: dark after the end, lit again on power back | D-POCO | D-MOTO |
| L-SAM | which lever works on D-SAM (API 19) | D-SAM | |
| S1 | screen-on below API 33: the wake lock alone lights the panel | D-SAM | |
| R1s | R1 again on API 19 (fake lever only) | D-SAM | D-POCO |
| S2 | a real cable pull with no session gives the same lines as the fake lever | D-SAM | |
| X | clean-up: remove the grant, restore battery state, timeouts, settings and radios | all | |

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` |
|---|---|---|---|
| **C** (candidate) | `fork/feat/automation-screen-disconnect` | `8db52cebe6bfc103f645c656a201d56bd3a3a963` | `8db52cebe6bf` (a `-dirty` suffix is expected) |

The branch was rebased on 2026-10-17 from round 1's `5236a1b4`. Both commits are the same patches, so
round 1's APK is not this candidate. C is `main` at `28af450d` plus
`8712543d` (Auto-disconnect: end the session when external power is lost) and `8db52ceb`
(Automation: ACTION_SCREEN_OFF and ACTION_SCREEN_ON). No baseline: the feature does not exist on
`main`, and each L run is the control for its unit.

```bash
git fetch fork feat/automation-screen-disconnect
git rev-parse fork/feat/automation-screen-disconnect          # must print 8db52cebe6bfc103f645c656a201d56bd3a3a963
git rev-parse fork/feat/automation-screen-disconnect^{tree}   # must print c5c46cdaaf8c96979bbd78e745aa113d93ffad3d
```

If either line differs, stop the round.

### R0. APK identity

Make `rig-data/rounds/automation-screen-disconnect-round2/` and set `OUT` to it. Build C once with
`rig-toolkit/build_hur.sh` (`MAX_WORKERS=2`, host below 70C) from the checked-out SHA, and copy the APK
out of `apks/` at once to `$OUT/apk/C.apk`. Do not run the unit tests: C's 3336 JVM tests passed on the
dev host at this SHA.

```bash
APK=$OUT/apk/C.apk
md5sum $APK                                                   # record it in Setup notes
for s in ExternalPowerDisconnectPolicy ScreenOffAdminReceiver ScreenOnRaisePolicy 'external-power disconnect'; do
  printf '%s %s\n' "$s" "$(unzip -p $APK 'classes*.dex' | strings | grep -cF "$s")"; done   # each 1 or more
adb -s 4f4027e9 shell am force-stop com.andrerinas.headunitrevived
adb -s 4f4027e9 shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/pre-install-D-POCO.xml
adb -s 4f4027e9 install -r $APK
adb -s 4f4027e9 shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/post-install-D-POCO.xml
diff $OUT/pre-install-D-POCO.xml $OUT/post-install-D-POCO.xml   # record the delta; reseed from pre-install if it differs
```

If the build fails, or a symbol counts 0, stop the round. If the install
fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and tell the operator. Do not uninstall.

Install the same APK on D-SAM the same way (`adb -s 30041c35642d2200`): back up `settings.xml` with
`run-as` before and after, `install -r`, and diff the two. D-SAM still carries round 1's build.

**PASS when all hold:** in P, `U=$POCO state` and `U=$SAM state` each show `commit` starting
`8db52cebe6bf`. **A failure in R0 stops the round.**

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
  ByeBye sent) and keeps the wireless bring-up down. The delay is clamped to 5 to 3600 s. The timer
  holds a partial wake lock and checks an `elapsedRealtime` deadline, so a sleeping SoC does not
  delay it.
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
framework calls whose result depends on the API level and the lock type. 3235 JVM tests cover the
pure policies (`ExternalPowerDisconnectPolicy`, `ScreenOnRaisePolicy`, `AutomationCommandPolicy`).

## 3. What is different about this round

- **D-HU runs nothing.** Round 1 measured on D-HU (Android 14, UNISOC): `dumpsys battery unplug`,
  `set ac`, `set usb` and `reset` each send `BATTERY_CHANGED` and never a `POWER_*` broadcast. Its
  ROM lights the panel about 760 ms after each plug change, and `input keyevent 223` gives `Dozing`.
  D-HU stays on its cable only so its Bluetooth can be held off (next point).
- **Hold D-HU's Bluetooth off before every session.** D-MOTO and D-POCO are both bonded to D-HU's
  radio (`Navegadortz2`), and Gearhead can dial a cached head unit instead of the unit under test
  (`rig-quirks/units/D-POCO.md`). `bringup` turns it off and reads it back. It self-reverts, so this
  repeats every run. X turns it back on.
- **Two levers, chosen per unit by its L run.** The fake lever is `dumpsys battery unplug` and
  `dumpsys battery set ac 1`. The cable lever is the operator pulling and replugging the unit's USB
  cable to the PC. The helpers take the lever from `LEVER` (`fake` or `cable`) and the run's `CABLE`
  flag. With the cable out, adb to that unit is gone. So a cable run grades on the unit's own log
  buffer, dumped after the replug (`cap_stop`), and its windows end at app lines, not markers.
- **Runs that need adb while the power is out are fake-lever only.** R10 sends `ACTION_EXIT` inside
  the delay, and R2's flap needs four changes in 12 s. On a unit whose L run reads `cable`, R10 is
  UNTESTABLE and R2 runs its cancel half only. R1s on D-SAM is fake-lever only too: API 19 has no
  `logcat -G`, and its small buffer cannot hold a session for a later dump.
- **T1 always uses the cable**, on both levers. A fake unplug leaves the PC's USB connection in
  place, which keeps the SoC awake, so only a real pull can show the deadline across a suspend.
- **The grant runs need a unit with no lock credential.** `lockNow()` on a unit with a PIN leaves it
  locked behind a PIN that adb cannot enter. P reads D-POCO's credential state. If D-POCO has one, V3,
  V4 and R5/R7 are UNTESTABLE. D-SAM gets no grant: API 19 has no `dpm` command.
- **The device admin grant cannot be removed from the shell.** `dpm remove-active-admin` refuses an
  admin that is not test-only, and Android blocks an uninstall while the grant is held. The operator
  removes it on the system's device admin screen (HS2). **Do not skip it.**
- **"Dark" means `Asleep` or `Dozing`.** Round 1 found that a forced sleep can read `Dozing`. Every
  panel condition below reads dark as `mWakefulness=Asleep`, `mWakefulness=Dozing` or
  `mScreenOn=false`, and lit as `mWakefulness=Awake` or `mScreenOn=true` (`dark` and `lit` in
  section 5).
- **The ROM can light the panel on a plug change.** L-POCO measures it on the fake lever. If it reads
  `ROM_WAKES_ON_PLUG=yes` or `unknown`, R7's panel conditions cannot be credited to the app (rule in
  R7).
- **The USB re-arm branch (plan R11) is not run.** It needs a USB session on D-POCO as host, wireless
  adb for both phones and an OTG hand step. If L-POCO reads `fake`, a later round can carry it.
- **Do not write the rig's audio settings.** `use-aac-audio`, `audio-latency-multiplier`,
  `audio-queue-capacity` and `enable-audio-sink` are a deliberate worst case. Every write starts from
  the unit's own backup, so they keep their values. Record them once in P.
- **`Critical error` lines on the phone follow every session end we cause.** Do not grade them.
- **Expected INCONCLUSIVE:** R3's cadence half when no poke prints before the cut; R7 conditions 4
  and 5 unless L-POCO read `ROM_WAKES_ON_PLUG=no`; T1 condition 5 when the session ended before the
  deadline (the rule is in T1); S2 when D-SAM does not read `USB powered: true`.

### Hand steps

| Id | Run | Action | Why there is no verb |
|---|---|---|---|
| HS0 | P | Only if D-MOTO shows a PIN screen: the operator unlocks D-MOTO once | adb cannot enter a PIN |
| HS1 | P | Only if a bond is missing: the operator pairs the named devices once (text in P) | pairing is a system dialog |
| PULL | cable runs, T1, S2 | The operator pulls the named unit's USB cable out | a real power loss is hardware |
| PLUG | cable runs, T1, S2 | The operator plugs the same cable back in | as above |
| HS2 | X | On D-POCO, the operator deactivates this app on the system's device admin screen | `dpm` refuses to remove an admin that is not test-only; the screen is a system dialog |
| HS3 | grant fallback | Only if `dpm set-active-admin` fails: the operator accepts the system's add-admin screen | a system dialog |

The script injects no input for any step. `cue` prints and beeps each one at the moment of the
action, and writes it to `$OUT/hand-steps.log`. A PULL ends when adb loses the unit. A PLUG ends when
adb finds it again. The operator does nothing else on any unit.

## 4. Settings keys

Write with the app stopped, with `put` (section 5). `put` starts every write from the unit's own
backup, so each run carries exactly the backup plus its keys. Read every key back with `rd` before the
launch. A key that does not read back with its value voids the run before launch: write it again. If
`put` cannot write on a unit, use `set_prefs_runas_host.py` from `rig-toolkit/` with the same keys,
and say so in Setup notes.

`MAC` is the phone's Bluetooth address. Each stage sets it before `BASE`, because `BASE` expands it at
assignment.

```bash
mkbase() { BASE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MAC str:last-connected-native-mac=$MAC str:native-preferred-device-mac=$MAC set:auto-start-bt-macs= bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:auto-start-on-usb=false bool:kill-on-disconnect=false int:onboarding-version=2 int:log-level=2 del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"; }
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
| L-POCO, L-SAM, V1a | `$BASE $A0 $EP_OFF` |
| V1b | `$BASE $A1 $EP_OFF` |
| R3, R1, R2, R10, R1s | `$BASE $A0 $EP_ON` |
| R4 | `$BASE $A0 $EP_OFF` |
| R6 | `$BASE $A0 $EP_ON_SCR` |
| T1 | `$BASE $A0 $EP_SLEEP` |
| V3, V4 | `$BASE $A1 $EP_OFF` |
| R5/R7 | `$BASE $A0 $EP_MIN_SCR` (the 2 is below the floor on purpose; the log must show 5000ms) |
| S1, S2 | `int:wifi-connection-mode=0 int:onboarding-version=2 int:log-level=2 bool:auto-connect-last-session=false bool:auto-start-on-usb=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:external-power-disconnect=true int:external-power-disconnect-delay-seconds=20 bool:external-power-screen-off=false` |

Why the less obvious keys: `auto-start-bt-macs` empty and `auto-start-on-*` false keep every other
re-arm path out of the windows, so a re-arm can only be the feature's own. `log-level` 2 (INFO)
carries every app line in section 6. `ACTION_LOG_MARKER` is not in
`AutomationCommandPolicy.CONFIGURING` on C, so markers work with `$A0`. Mode 0 in S1 and S2 is MANUAL:
nothing is armed, so no session can form.

## 5. Helpers and verbs

Copy `ptr_lib.sh` from `rig-data/rounds/automation-screen-disconnect-round1/` into `$OUT`. Use only
`th_wait`, `th_gate` and `th_report` from it. Source it, then save and source `$OUT/ascd2_lib.sh`
last, so its functions win. Do not source round 1's `ascd_lib.sh`.

```bash
# ascd2_lib.sh : source after ptr_lib.sh. Needs OUT.
# Each stage sets U (head unit serial), UN (its name), PH (phone serial), MAC and LEVER (fake or cable).
# Each run sets RUN, PHCAP (1 = capture the phone) and CABLE (1 = this run pulls the real cable).
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
ADMIN=$PKG/com.andrerinas.openheadunit.app.ScreenOffAdminReceiver
DHU=27870808938846; POCO=4f4027e9; MOTO=ZY22GC3BM4; SAM=30041c35642d2200
send() { local a=$1; shift; adb -s $U shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null
  if [ -n "$PHCAP" ]; then adb -s $PH shell log -t OHURIG "$1"; sleep 0.3; fi; }
SF() { echo "$OUT/$RUN.hu.stream.txt"; }   # live stream; it stops at a cable pull
HF() { echo "$OUT/$RUN.hu.txt"; }          # the graded head unit file
PF() { echo "$OUT/$RUN.ph.txt"; }          # the phone capture
cab() { CABLE=; [ "$LEVER" = cable ] && CABLE=1; }
cap_start() { PULLED=
  adb -s $U logcat -G 16M; sleep 0.3; adb -s $U logcat -c; sleep 0.3
  stdbuf -oL adb -s $U logcat -v time > "$(SF)" & echo $! > "$OUT/$RUN.hu.pid"
  if [ -n "$PHCAP" ]; then
    for t in CAR.GAL CAR.GAL.GAL.LITE; do adb -s $PH shell setprop log.tag.$t VERBOSE; sleep 0.3; done
    adb -s $PH logcat -G 16M; sleep 0.3; adb -s $PH logcat -c; sleep 0.3
    stdbuf -oL adb -s $PH logcat -v time > "$(PF)" & echo $! > "$OUT/$RUN.ph.pid"; fi
  sleep 1; }
# cap_stop: a cable run grades on the unit's own buffer, dumped now; any other run grades on the stream
cap_stop() { sleep 1
  if [ -n "$CABLE" ]; then adb -s $U logcat -d -v time > "$(HF)"; sleep 0.3; fi
  for f in "$OUT/$RUN.hu.pid" "$OUT/$RUN.ph.pid"; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null; rm -f "$f"; done
  if [ -z "$CABLE" ]; then cp "$(SF)" "$(HF)"; fi
  if grep -aqF "AutomationMarker: $RUN-start" "$(HF)"; then echo "wrapped=no" >> "$OUT/$RUN.summary"
  else echo "wrapped=yes" | tee -a "$OUT/$RUN.summary"; fi; }
# hwin A B : head unit lines from marker A to marker B. pwin A B : the same on the phone capture.
hwin() { awk -v a="AutomationMarker: $1" -v b="AutomationMarker: $2" '{sub(/\r$/,"")} function at(s){i=index($0,s); return i>0 && substr($0,i+length(s))~/^[[:space:]]*$/} at(a){on=1} on{print} on&&at(b){exit}' "$(HF)"; }
pwin() { awk -v a="$1" -v b="$2" '{sub(/\r$/,"")} /OHURIG/ && $NF==a {on=1} on{print} on && /OHURIG/ && $NF==b {exit}' "$(PF)"; }
c() { grep -acF -- "$1"; }
ep() { grep -aF -- "$1" | grep -acF 'external-power disconnect'; }
rend() { grep -acE 'Throughput over [0-9]+ms: rendered=[1-9]'; }
# from S : stdin from the first line holding S to the end. upto S : stdin up to, not including, that line.
from() { awk -v s="$1" '{sub(/\r$/,"")} !on && index($0,s){on=1} on'; }
upto() { awk -v s="$1" '{sub(/\r$/,"")} index($0,s){exit} {print}'; }
# aftms MS S : stdin lines stamped MS or more after the first line holding S
aftms() { awk -v ms="$1" -v s="$2" '{sub(/\r$/,""); split($2,t,":"); x=(t[1]*3600+t[2]*60+t[3])*1000} !st && index($0,s){st=x+ms} st && x>=st'; }
tms() { grep -aF -m1 -- "$1" | awk '{split($2,t,":"); printf "%d\n", (t[1]*3600+t[2]*60+t[3])*1000}'; }
# gap WINDOW A B : ms from the first A to the first B in WINDOW, or NA
gap() { local a b; a=$(printf '%s\n' "$1" | tms "$2"); b=$(printf '%s\n' "$1" | tms "$3")
  if [ -n "$a" ] && [ -n "$b" ]; then echo $((b-a)); else echo NA; fi; }
# lastgap WINDOW A B : ms from the last A to the first B after it, or NA
lastgap() { local a b; a=$(printf '%s\n' "$1" | grep -aF -- "$2" | tail -1 | awk '{split($2,t,":"); printf "%d\n", (t[1]*3600+t[2]*60+t[3])*1000}')
  b=$(printf '%s\n' "$1" | tms "$3"); if [ -n "$a" ] && [ -n "$b" ]; then echo $((b-a)); else echo NA; fi; }
# delay WINDOW : the number on the first A1 line, before "ms unless it comes back."
delay() { printf '%s\n' "$1" | grep -aF -m1 'power lost; ending the session in ' | grep -aoE '[0-9]+ms unless it comes back' | grep -aoE '^[0-9]+'; }
wake() { adb -s $U shell dumpsys power | grep -a -m1 -oE 'mWakefulness=[A-Za-z]+|mScreenOn=[a-z]+'; sleep 0.3; }
dark() { grep -aqE 'mWakefulness=(Asleep|Dozing)|mScreenOn=false' "$1"; }
lit() { grep -aqE 'mWakefulness=Awake|mScreenOn=true' "$1"; }
panel_off() { if [ "$U" = "$SAM" ]; then wake | grep -aqE 'Awake|mScreenOn=true' && adb -s $U shell input keyevent 26; else adb -s $U shell input keyevent 223; fi; sleep 0.3; }
panel_on() { if [ "$U" = "$SAM" ]; then wake | grep -aqE 'Asleep|Dozing|mScreenOn=false' && adb -s $U shell input keyevent 26; else adb -s $U shell input keyevent 224; fi; sleep 0.3; }
kg() { adb -s $U shell dumpsys window | grep -a -m2 -oE '(isKeyguardShowing|mShowingLockscreen)=[a-z]+' | tr '\n' ' '; echo; sleep 0.3; }
admin() { adb -s $U shell dumpsys device_policy | grep -ac ScreenOffAdminReceiver; sleep 0.3; }
focus() { adb -s $U shell dumpsys window | grep -a -m1 mCurrentFocus; sleep 0.3; }
state() { send ACTION_QUERY_STATE | grep -a 'data='; }
rd() { adb -s $U shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE "($1)[^/]*"; sleep 0.3; }
cue() { echo "$(date +%T) OPERATOR_STEP $1"; printf '\a'; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
put() { adb -s $U shell am force-stop $PKG; sleep 0.3
  cp "$OUT/backup-$UN.xml" "$OUT/w-$UN.xml"; python3 -I "$OUT/sx.py" "$OUT/w-$UN.xml" "$@"
  adb -s $U push "$OUT/w-$UN.xml" /data/local/tmp/ohu-w.xml >/dev/null; sleep 0.3
  adb -s $U shell run-as $PKG cp /data/local/tmp/ohu-w.xml shared_prefs/settings.xml; sleep 0.3; }
fakeplug() { adb -s $U shell dumpsys battery set ac 1; sleep 0.3; }
pw_lost() { if [ -z "$CABLE" ]; then adb -s $U shell dumpsys battery unplug; sleep 0.3
  else cue "PULL: pull the USB cable of $UN out now"; while adb -s $U get-state >/dev/null 2>&1; do sleep 0.5; done
    PULLED=1; echo "$(date +%T) $RUN cable out" >> "$OUT/hand-steps.log"; fi; }
pw_back() { if [ -z "$CABLE" ]; then fakeplug
  else cue "PLUG: plug the USB cable of $UN back in now"; adb -s $U wait-for-device; sleep 5
    echo "$(date +%T) $RUN cable in" >> "$OUT/hand-steps.log"; fi; }
# waitfor S STR : wait up to S seconds for a new head unit line holding STR. 0 = seen.
waitfor() { local s=$1 str=$2 b i
  if [ -z "$PULLED" ]; then b=$(wc -l < "$(SF)")
    for i in $(seq 1 "$s"); do tail -n +"$((b+1))" "$(SF)" | grep -aqF -- "$str" && return 0; sleep 1; done; return 1; fi
  b=$(adb -s $U logcat -d -v time | grep -acF -- "$str"); sleep 0.3
  for i in $(seq 1 $((s/3))); do sleep 3; [ "$(adb -s $U logcat -d -v time | grep -acF -- "$str")" -gt "$b" ] && return 0; done; return 1; }
# launch_arm M : launch, and send the arm verb only if the launch did not arm the stack within 10 s.
# On mode 3 a launch arms by itself, and a second arm tears the group down and makes another
# (rig-quirks/topics/wifi.md; station-scan round 1 lost three D-SAM attempts to it). Marks M either way.
armcount() { if [ -z "$PULLED" ]; then grep -acF 'WifiLauncher: Initializing WiFi Mode: ' "$(SF)"
  else adb -s $U logcat -d -v time | grep -acF 'WifiLauncher: Initializing WiFi Mode: '; fi; }
launch_arm() { local b i; b=$(armcount); adb -s $U shell am start -n $MAIN
  for i in $(seq 1 20); do sleep 0.5; if [ "$(armcount)" -gt "$b" ]; then mark $1; echo "$1 arm=launch" | tee -a "$OUT/$RUN.summary"; return; fi; done
  mark $1; send ACTION_START_WIRELESS_SCAN > "$OUT/$1.reply"; echo "$1 arm=verb" | tee -a "$OUT/$RUN.summary"; }
clean_hu() { adb -s $U shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 3; adb -s $U shell am force-stop $PKG; sleep 0.3; }
dhu_bt_off() { adb -s $DHU shell svc bluetooth disable; sleep 3; adb -s $DHU shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'; }
phone_off() { adb -s $1 shell cmd connectivity airplane-mode enable; sleep 3; adb -s $1 shell svc wifi disable; sleep 0.3
  adb -s $1 shell svc bluetooth disable; sleep 5; adb -s $1 shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'; }
phone_on() { adb -s $PH shell cmd connectivity airplane-mode disable; sleep 5
  adb -s $PH shell svc wifi enable; sleep 0.3; adb -s $PH shell svc bluetooth enable; sleep 5
  adb -s $PH shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'
  adb -s $PH shell input keyevent KEYCODE_HOME; sleep 1; }
# bringup : a session from U to PH with the keys in K. Sets ssl=0 when SSL completed.
bringup() {
  dhu_bt_off | tee "$OUT/$RUN.dhu-bt"; phone_on | tee "$OUT/$RUN.ph-bt"
  adb -s $U shell am force-stop $PKG; sleep 0.3
  if [ "$LEVER" = fake ] && [ -z "$CABLE" ]; then fakeplug; fi
  put $K; rd 'external-power|allow-external|wifi-connection-mode|log-level|native-poke' | tee "$OUT/$RUN.keys"
  th_gate; cap_start; mark $RUN-start
  launch_arm $RUN-arm
  waitfor 120 'SSL handshake complete'; ssl=$?; echo "ssl=$ssl" | tee -a "$OUT/$RUN.summary"
  sleep 15; focus | tee "$OUT/$RUN.focus-session"; }
# endrun : user exit (the reference ByeBye), then a clean stop
endrun() { mark $RUN-x-go; send ACTION_DISCONNECT > "$OUT/$RUN-x.reply"; sleep 8; mark $RUN-x-done
  mark $RUN-end; clean_hu; cap_stop; th_report $RUN; PHCAP=; CABLE=; }
```

Save this as `$OUT/sx.py`. It edits a local copy of `settings.xml`; `put` pushes it.

```python
# sx.py FILE type:key=value ... ; type is int, bool, str, set (comma list) or del. Edits FILE in place.
import re, sys
f = sys.argv[1]; s = open(f).read()
for kv in sys.argv[2:]:
    t, rest = kv.split(':', 1); k, v = (rest.split('=', 1) + [''])[:2]
    s = re.sub(r'<\w+ name="%s"[^>]*/>' % re.escape(k), '', s)
    s = re.sub(r'<string name="%s">[^<]*</string>' % re.escape(k), '', s)
    s = re.sub(r'<set name="%s">.*?</set>' % re.escape(k), '', s, flags=re.S)
    if t == 'set':
        items = ''.join('<string>%s</string>' % i for i in (v.split(',') if v else []))
        el = '<set name="%s">%s</set>' % (k, items) if items else '<set name="%s" />' % k
    elif t in ('int', 'bool', 'str'):
        el = {'int': '<int name="%s" value="%s" />', 'bool': '<boolean name="%s" value="%s" />',
              'str': '<string name="%s">%s</string>'}[t] % (k, v)
    else:
        el = ''
    s = s.replace('</map>', el + '</map>')
open(f, 'w').write(s)
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
| external power lost | `pw_lost` (fake: `dumpsys battery unplug`; cable: PULL) |
| external power back | `pw_back` (fake: `dumpsys battery set ac 1`; cable: PLUG) |
| panel off or on by the OS, not the app | `panel_off`, `panel_on` (223 and 224; D-SAM toggles with 26) |
| grant the device admin | `adb -s $POCO shell dpm set-active-admin --user 0 $ADMIN` |

Every verb must print `AutomationReceiver: ` in the head unit capture. A step with no such line
never landed: that step is void, not a FAIL. Save each verb's `am` output to a `.reply` file; the
reply is on its `data=` line. Never run two adb calls against one unit at the same time.

## 6. The deciding lines

Every app string below matched `grep -F -r` in `app/src/main` at `8db52ceb`. All are INFO or WARN, so
`log-level` 2 carries them.

| Line (grep -F) | Source | Used for |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | a verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` | markers |
| `WakeDetect: POWER_DISCONNECTED` | `AapService` | the power-lost lever landed |
| `WakeDetect: POWER_CONNECTED` | `AapService` | the power-back lever landed |
| `WakeDetect: SCREEN_OFF` | `AapService` | the panel went off |
| `WakeDetect: SCREEN_ON (screen was off for ` | `AapService` | the panel came on |
| `WakeDetect: power lost beside a screen-off; treating it as the car being switched off` | `AapService` | must stay 0 in R5 and T1 |
| `WakeDetect: re-arming the wireless bring-up held while the unit was asleep` | `AapService` | record in R7 |
| `WakeDetect: hibernate wake detected` | `AapService` | record in R7 |
| `AapService: external-power disconnect: power lost; ending the session in ` | `AapService` | A1; the delay follows, then `ms unless it comes back.` |
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
| `keeping the wireless bring-up down until something re-arms it.` | `AapService` (after the label) | the stand-down; count with `ep` |
| `: screen off ` | `AapService` (after the label) | the stand-down screen-off; count with `ep`; the line ends `done.` or `refused by the system.` |
| `done.` | `AapService` | the screen-off line when it worked |
| `refused by the system.` | `AapService` | the screen-off line when it did not |
| `: power came back while the session was ending; not standing down.` | `AapService` (after the label) | record |
| `: power came back and went again while the session was ending; standing down.` | `AapService` (after the label) | record |
| `AapService: external-power disconnect: the session end never reached the stand-down in ` | `AapService` | must stay 0 in every run |
| `AapService: external-power disconnect: could not hold the CPU awake for the timer: ` | `AapService` | must stay 0 in T1 |
| `AapService: turning the screen on (` | `AapService` | screen-on ran; the source follows: `automation` or `external power back` |
| `) by wake lock and ` | `AapService` | screen-on also raised a screen; then `the home screen` or `the projection`, then ` by <DIRECT/OVERLAY/NOTIFICATION>` |
| `the home screen` | `AapService` | which screen screen-on raised |
| `the projection` | `AapService` | which screen screen-on raised |
| `AapService: screen-on launch failed: ` | `AapService` | must stay 0 |
| `AapTransport stopping and sending byebye (` | `AapTransport` | our side sent the ByeBye; the reason follows |
| `Disconnect action received.` | `AapService` | the reference exit in `endrun` |
| `AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected` | `AapService` | a session ended |
| `AapService destroying...` | `AapService` | R10 validity: the service stopped |
| `SSL handshake complete` | `AapSslContext` | a session formed |
| `Throughput over ` | `VideoDecoder` | picture; `rend` regex `Throughput over [0-9]+ms: rendered=[1-9]` |
| `createGroup SUCCESS` | `WifiDirectManager` | must stay 0 in R1's quiet window |
| `NativeAA: Attempting active poke to device` | `NativeAaHandshakeManager` | the poke loop runs (R3), or must stay 0 (R1 quiet) |
| `MATCH! Starting AapService` | `AutoStartReceiver` | discard check |
| `external configuration is off; turn on` | `AutomationCommandPolicy` reply | V1a refusal |
| `no device admin grant; allow it under Settings > Auto-start` | `AutomationCommandPolicy` reply | V1b refusal |
| `"screenOff"` | `AutomationEffectRunner` reply key | V3 and V4: `"screenOff":true` |

Not in our tree:

| Line | Where | Used for |
|---|---|---|
| `received ByeByeRequest` | the phone, tag `CAR.GAL.GAL.LITE` (set VERBOSE by `cap_start`); present in Gearhead 17.5 and 17.8 | the phone got our ByeBye |
| `OHURIG` | the phone, our own `log -t` markers | phone windows |
| `device wake-up` | head unit, `PowerManagerService` (API 34 and later: `Allowing device wake-up from app` or `Not allowing device wake-up for `) | record for every screen-on on D-POCO |
| `act=android.intent.action.ACTION_POWER_DISCONNECTED`, `act=android.intent.action.ACTION_POWER_CONNECTED` | D-MOTO, `dumpsys activity broadcasts history` | L-MOTO |
| `Lock credential verified successfully` | D-POCO, `cmd lock_settings verify` | P: no lock credential |

Framework readings, not log lines: `mWakefulness=` or `mScreenOn=` from `wake`, the keyguard state
from `kg`, the focused window from `focus`, and the admin count from `admin`.

## 7. Runs

Every run: `th_gate` first (inside `bringup` for session runs), then the steps in order. Report
`th_report $RUN` in each section. Write the results file after every run. Order: R0, P, L-MOTO,
Stage A (L-POCO, V1, R3, R4, **R1 with R2 and R10 as one chain on the fake lever**, R6, T1, grant, V3, V4, R5/R7, HS2), Stage B (L-SAM,
S1, R1s, S2), X.

**Session runs** (R4, R1, R2, R6, R10, T1, V4, R5/R7, R1s) set `PHCAP=1` and `K` before `bringup`. If
`ssl=1`, run `endrun` and repeat the run once. A second `ssl=1` makes that run UNTESTABLE. **Every
session run is valid only when** `hwin $RUN-arm $RUN-cut | rend` is 1 or more, `MATCH! Starting
AapService` is 0 in `hwin $RUN-start $RUN-end`, and `$RUN.summary` reads `wrapped=no`. Report all
three. A run with `wrapped=yes` is void: run it once more.

`aftms`, `gap` and `lastgap` use the time of day. Do not let a run cross midnight.

**Stop rule:** if two session runs in a row in one stage end UNTESTABLE, skip the rest of that
stage's session runs, run its other runs, and say so in Setup notes.

### P. Precheck, once

Apps stopped on every unit. Record every output in Setup notes. First save `sx.py` and
`ascd2_lib.sh` into `$OUT`, copy `ptr_lib.sh` in, and source `ptr_lib.sh`, then `ascd2_lib.sh`.

```bash
for s in $POCO $SAM; do adb -s $s shell am force-stop $PKG; sleep 0.3; done
adb -s $POCO shell run-as $PKG cat shared_prefs/settings.xml > $OUT/backup-D-POCO.xml
adb -s $SAM shell run-as $PKG cat shared_prefs/settings.xml > $OUT/backup-D-SAM.xml
grep -aoE '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)[^/]*' $OUT/backup-D-*.xml
adb -s $DHU shell dumpsys battery | grep -a -E 'AC powered|USB powered|status|level'   # round 1 left it all false; record
for s in $POCO $SAM; do adb -s $s shell dumpsys battery | grep -a -E 'AC powered|USB powered|present|level'; sleep 0.3; done
adb -s $POCO shell cmd lock_settings verify                          # SECURE_POCO=no only if it prints "Lock credential verified successfully"
adb -s $POCO shell settings get system screen_off_timeout            # record as TIMEOUT_POCO
adb -s $SAM shell settings get system screen_off_timeout             # record as TIMEOUT_SAM
adb -s $POCO shell settings get global stay_on_while_plugged_in
adb -s $POCO shell appops get $PKG SYSTEM_ALERT_WINDOW
adb -s $POCO shell appops get $PKG TURN_SCREEN_ON
U=$POCO admin; U=$SAM admin                                          # both must be 0
adb -s $POCO shell dumpsys bluetooth_manager | grep -a -A14 'Bonded devices' > $OUT/poco-bonds.txt
grep -ac 'motorola edge 30 neo' $OUT/poco-bonds.txt                  # 1 or more: Stage A can pair
grep -ac 'Navegadortz3' $OUT/poco-bonds.txt                          # 1 or more: Stage B can pair
grep -a 'motorola edge 30 neo' $OUT/poco-bonds.txt                   # take MOTO_MAC (expected A0:46:5A:97:E4:95)
adb -s $POCO shell settings get secure bluetooth_address             # take POCO_MAC (expected DC:B7:2E:5E:4E:59)
adb -s $MOTO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb -s $POCO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb -s $MOTO shell dumpsys window | grep -a -m2 -oE '(isKeyguardShowing|mShowingLockscreen)=[a-z]+'
adb -s $MOTO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'   # isGroupOwner false
adb -s $POCO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'   # isGroupOwner false
adb -s $SAM shell date; date
adb -s $POCO shell settings put system screen_off_timeout 1800000    # X restores TIMEOUT_POCO
adb -s $SAM shell settings put system screen_off_timeout 1800000     # X restores TIMEOUT_SAM
```

Then do these in order:

1. If either `grep -ac` above counts 0, cue HS1 once: "On D-POCO, pair with `<the missing device>`."
   Read the bonds again into `poco-bonds-after.txt`. If the D-MOTO bond is still missing, every Stage
   A session run is UNTESTABLE. If `Navegadortz3` is still missing, R1s is UNTESTABLE. Do not ask
   again.
2. If D-MOTO's keyguard reads `true` and its screen asks for a PIN, cue HS0 once.
3. If `cmd lock_settings verify` does not print `Lock credential verified successfully`, set
   `SECURE_POCO=yes`: V3, V4 and R5/R7 are UNTESTABLE, and the grant step is skipped.
4. If D-POCO's battery reads below 50, charge it before Stage A and record the level.
5. Run R0's `state` checks: `U=$POCO state`, then `U=$SAM state`.

`screen_off_timeout` is raised because `stay_on_while_plugged_in` does not hold the panel while the
unit reads unplugged. A panel that times out by itself would look like the feature's screen-off.

### L-MOTO. The fake lever on D-MOTO (record only)

D-MOTO does not have this app. This reads the system's own broadcast history.

```bash
h() { adb -s $MOTO shell dumpsys activity broadcasts history | grep -acF "act=android.intent.action.ACTION_POWER_$1"; sleep 0.3; }
d0=$(h DISCONNECTED); c0=$(h CONNECTED)
adb -s $MOTO shell dumpsys battery set ac 1; sleep 3; adb -s $MOTO shell dumpsys battery unplug; sleep 3; d1=$(h DISCONNECTED)
adb -s $MOTO shell dumpsys battery set ac 1; sleep 3; c1=$(h CONNECTED)
adb -s $MOTO shell dumpsys battery reset; sleep 1; adb -s $MOTO shell dumpsys battery | grep -a -E 'AC powered|USB powered'
echo "L-MOTO d0=$d0 d1=$d1 c0=$c0 c1=$c1" | tee $OUT/L-MOTO.txt
```

Report `MOTO_FAKE=yes` when `d1` is above `d0` and `c1` is above `c0`, else `MOTO_FAKE=no`. No
verdict. The battery lines after the reset must match P's reading style (a real source true).

### Stage A. D-POCO as head unit, D-MOTO as phone

```bash
U=$POCO; UN=D-POCO; PH=$MOTO; MAC=<MOTO_MAC from P>; mkbase
phone_off $MOTO                                       # D-SAM stays idle with its app stopped
```

#### L-POCO. Which lever works on D-POCO, and does its ROM light the panel

```bash
RUN=L-POCO; PHCAP=; CABLE=; adb -s $U shell am force-stop $PKG; sleep 0.3; fakeplug
K="$BASE $A0 $EP_OFF"; put $K; rd 'external-power|wifi-connection-mode|log-level' | tee $OUT/$RUN.keys
th_gate; cap_start; mark $RUN-start; adb -s $U shell am start -n $MAIN; sleep 10
mark $RUN-cut; adb -s $U shell dumpsys battery unplug; sleep 5
mark $RUN-back; fakeplug; sleep 5
mark $RUN-panel; panel_off; sleep 8; wake | tee $OUT/$RUN.w0
adb -s $U shell dumpsys battery unplug; sleep 3; wake | tee $OUT/$RUN.w-unplug
lit $OUT/$RUN.w-unplug && { panel_off; sleep 8; }
wake | tee $OUT/$RUN.w1
fakeplug; sleep 3; wake | tee $OUT/$RUN.w-plug
panel_on; sleep 2; mark $RUN-end; clean_hu; cap_stop; th_report $RUN
```

`FAKE_OK` holds when `hwin $RUN-cut $RUN-back | c 'WakeDetect: POWER_DISCONNECTED'` is 1 and
`hwin $RUN-back $RUN-panel | c 'WakeDetect: POWER_CONNECTED'` is 1.

- `FAKE_OK`: set `LEVER_POCO=fake`. Report `ROM_WAKES_ON_UNPLUG` (`yes` when `w0` is dark and
  `w-unplug` is lit, `no` when both are dark, else `unknown`) and `ROM_WAKES_ON_PLUG` (`yes` when
  `w1` is dark and `w-plug` is lit, `no` when both are dark, else `unknown`).
- Not `FAKE_OK`: set `LEVER_POCO=cable`, run `adb -s $U shell dumpsys battery reset`, read
  `dumpsys battery` back (a real source must read true), and report both ROM readings as `unknown`.

Then `LEVER=$LEVER_POCO`. Write `LEVER_POCO`, `ROM_WAKES_ON_UNPLUG` and `ROM_WAKES_ON_PLUG` to
`$OUT/lever.env`. L-POCO has no PASS or FAIL. Its `external-power disconnect` count must be 0
(the switch is off). Report it.

#### V1. The screen verbs before the grant

```bash
RUN=V1a; PHCAP=; CABLE=; adb -s $U shell am force-stop $PKG; sleep 0.3; [ "$LEVER" = fake ] && fakeplug
K="$BASE $A0 $EP_OFF"; put $K; rd 'allow-external|external-power' | tee $OUT/$RUN.keys
th_gate; cap_start; mark V1a-start; adb -s $U shell am start -n $MAIN; sleep 10
mark V1a-off; send ACTION_SCREEN_OFF > $OUT/V1a-off.reply; sleep 3; wake | tee $OUT/V1a.w-off
panel_off; sleep 5; wake | tee $OUT/V1a.w-dark
mark V1a-on; send ACTION_SCREEN_ON > $OUT/V1a-on.reply; sleep 5; wake | tee $OUT/V1a.w-on; focus | tee $OUT/V1a.focus; kg | tee $OUT/V1a.kg
mark V1a-end; clean_hu; cap_stop; th_report V1a
RUN=V1b; K="$BASE $A1 $EP_OFF"; put $K; rd 'allow-external' | tee $OUT/$RUN.keys
th_gate; cap_start; mark V1b-start; adb -s $U shell am start -n $MAIN; sleep 10
mark V1b-off; send ACTION_SCREEN_OFF > $OUT/V1b-off.reply; sleep 3; wake | tee $OUT/V1b.w-off
mark V1b-end; clean_hu; cap_stop; th_report V1b
```

Set `RUN` to each run's name before its `hwin` calls: `hwin` reads `$OUT/$RUN.hu.txt`.

**PASS when all hold:**
1. `V1a-off.reply` holds `external configuration is off; turn on` and `"ok":false`; `V1a.w-off` is lit.
2. `V1a.w-dark` is dark (the precondition for the screen-on half; if it is lit, that half is void).
3. `hwin V1a-on V1a-end`: 1 line with `AapService: turning the screen on (` that holds `automation`; `V1a.w-on` is lit; `gap "$(hwin V1a-on V1a-end)" 'AapService: turning the screen on (' 'WakeDetect: SCREEN_ON (screen was off for '` is 0 to 5000.
4. `V1b-off.reply` holds `no device admin grant; allow it under Settings > Auto-start` and `"ok":false`; `V1b.w-off` is lit.
5. `AapService: screen-on launch failed: ` is 0 in both captures.

Record the whole screen-on line (which screen, which strategy), the `device wake-up` lines in
`hwin V1a-on V1a-end`, `V1a.focus` and `V1a.kg`.

#### R3. Power lost with no session

D-MOTO stays in airplane mode, so the stack is armed and pokes, and no session forms.

```bash
RUN=R3; PHCAP=; cab; adb -s $U shell am force-stop $PKG; sleep 0.3; [ -z "$CABLE" ] && fakeplug
K="$BASE $A0 $EP_ON"; put $K; rd 'external-power|native-poke' | tee $OUT/$RUN.keys
th_gate; cap_start; mark R3-start; launch_arm R3-arm; sleep 60
mark R3-cut; pw_lost; sleep 60
pw_back; mark R3-back; sleep 30
mark R3-end; clean_hu; cap_stop; th_report R3
```

`ALL=$(hwin R3-cut R3-end)`, `W1=$(printf '%s\n' "$ALL" | upto 'WakeDetect: POWER_CONNECTED')`,
`W2=$(printf '%s\n' "$ALL" | from 'WakeDetect: POWER_CONNECTED')`.

**PASS when all hold:**
1. `W1`: `WakeDetect: POWER_DISCONNECTED` is 1, and `W2` holds 1 or more `WakeDetect: POWER_CONNECTED` (else void).
2. `W1`: the A3 line (`... power lost, but nothing is projecting; leaving the connection stack alone.`) is 1, and `gap "$W1" 'WakeDetect: POWER_DISCONNECTED' 'nothing is projecting'` is 0 to 1000.
3. `W1`: `AapService: external-power disconnect: power lost; ending the session in ` is 0, the A6 line is 0, and `printf '%s\n' "$W1" | ep 'keeping the wireless bring-up down'` is 0.
4. `W2`: `external-power disconnect` is 0 and `AapService: turning the screen on (` is 0 (nothing owed, so power back does nothing new).
5. Cadence: if `hwin R3-arm R3-cut | c 'NativeAA: Attempting active poke to device'` is 1 or more, `W1` holds 1 or more of it too. If the first count is 0, the cadence half is INCONCLUSIVE and conditions 1 to 4 decide.

Report both poke counts, and the lever used.

#### R4. Switch off

```bash
RUN=R4; PHCAP=1; cab; K="$BASE $A0 $EP_OFF"; bringup
sleep 20; mark R4-cut; pw_lost; sleep 35
pw_back; mark R4-back; sleep 10; endrun
```

`W=$(hwin R4-cut R4-x-go)`.

**PASS when all hold:**
1. `W`: `WakeDetect: POWER_DISCONNECTED` is 1 and `WakeDetect: POWER_CONNECTED` is 1 (else void).
2. `W`: `external-power disconnect` is 0.
3. `W`: `since connection is disconnected` is 0, and `hwin R4-cut R4-back | rend` is 1 or more.
4. Phone: `pwin R4-cut R4-x-go | c 'received ByeByeRequest'` is 0.

This PASS would look the same if the feature did nothing. R1 shows that the same lever does produce
lines with the switch on, so R4 counts only beside a passing R1. With the cable lever, R4 also shows
that a pull alone does not end the session. If condition 3 fails on the cable lever, stop Stage A's
session runs: every later window would mix the two causes. Say so in Setup notes.

#### R1, R2 and R10 as one chain (fake lever only)

R1's return session (`ssl2`, started under `$EP_ON`) is the starting session that R2 and R10 need. R2
needs a session, power in, keys `$BASE $A0 $EP_ON`. R10 needs the same. R1 ends in that state, so on
the fake lever run the three as one chain in one capture, with one `bringup` and one `th_gate`. R1 runs
first, so its windows stay clean. On the cable lever, run R1 and R2 as written under their own
headings and R10 stays UNTESTABLE.

Keep `RUN=R1` for the whole chain: `hwin` and `pwin` read `$OUT/R1.hu.txt` and `$OUT/R1.ph.txt`. Each
run keeps its own markers and its own PASS conditions, and each window ends at its own marker. R1's
closing exit (REF) moves to the end of the chain. Two names change:

| Run | Start marker | End marker (in place of `-x-go` or `-end`) | Notes |
|---|---|---|---|
| R1 | `R1-start` | `R1-done` | `ALL=$(hwin R1-cut R1-done)`; the validity window is `hwin R1-start R1-done` |
| R2 | `R2-start` | `R2-done` | `Wc=$(hwin R2-flap-end R2-done)`; the validity window is `hwin R2-start R2-done` |
| R10 | `R10-start` | `R10-done` | `hwin R10-exit R10-done` in condition 3; the validity window is `hwin R10-start R10-done` |
| REF | `R1-x-go` | `R1-x-done` | from the chain's closing `endrun`; R1 condition 4 reads `REF=$(hwin R1-x-go R1-x-done)` |

R2 and R10 take `$RUN-arm` as the marker pair `-start` then `-arm`, so `hwin R2-arm R2-cut | rend` and
`hwin R10-arm R10-cut | rend` read the 20 s after R1's session, not a new bring-up. R2 and R10 do not
run `bringup`, so they read no `ssl=` of their own. R1's `ssl2=0` stands in for it. If R1's `ssl2` is 1,
the chain stops after `endrun`, and R2 and R10 run as written under their own headings, each with its
own `bringup`. The same holds for R2's `ssl2` before R10. `dhu_bt_off` repeats before each segment,
because D-HU's Bluetooth self-reverts.

```bash
RUN=R1; PHCAP=1; cab; K="$BASE $A0 $EP_ON"; bringup
# R1
sleep 20; mark R1-cut; pw_lost; sleep 160
pw_back; mark R1-back; wake | tee $OUT/R1.wake
waitfor 120 'SSL handshake complete'; ssl2=$?; echo "ssl2=$ssl2" | tee -a $OUT/R1.summary
sleep 20; mark R1-done
if [ "$ssl2" -ne 0 ]; then endrun; else
# R2 (the flap runs: fake lever)
dhu_bt_off | tee $OUT/R2.dhu-bt
mark R2-start; mark R2-arm; sleep 20
mark R2-cut; pw_lost; sleep 8
pw_back; mark R2-back; sleep 30; mark R2-hold
mark R2-flap; pw_lost; sleep 5; pw_back; sleep 5; pw_lost; sleep 30; mark R2-flap-end
pw_back; waitfor 120 'SSL handshake complete'; ssl2=$?; echo "ssl2=$ssl2" | tee -a $OUT/R2.summary
sleep 15; mark R2-done
if [ "$ssl2" -ne 0 ]; then endrun; else
# R10
dhu_bt_off | tee $OUT/R10.dhu-bt
mark R10-start; mark R10-arm; sleep 20
mark R10-cut; pw_lost; sleep 8
mark R10-exit; send ACTION_EXIT > $OUT/R10-exit.reply; sleep 5
launch_arm R10-arm2
waitfor 120 'SSL handshake complete'; echo "ssl2=$?" | tee -a $OUT/R10.summary
sleep 45; mark R10-hold; pw_back; sleep 10; mark R10-done
endrun
fi; fi
```

Grade each run from its own section, with the marker names above. If R2's `ssl2` or R10's session fails
in the chain, run that one again as written under its own heading.

#### R1. The point of the round: the session ends after the delay, and power back brings it back

On the fake lever the chain above runs this. Use this script alone only on the cable lever.

```bash
RUN=R1; PHCAP=1; cab; K="$BASE $A0 $EP_ON"; bringup
sleep 20; mark R1-cut; pw_lost; sleep 160
pw_back; mark R1-back; wake | tee $OUT/R1.wake
waitfor 120 'SSL handshake complete'; echo "ssl2=$?" | tee -a $OUT/R1.summary
sleep 20; endrun
```

`ALL=$(hwin R1-cut R1-x-go)`, `W1=$(printf '%s\n' "$ALL" | upto 'WakeDetect: POWER_CONNECTED')`,
`W2=$(printf '%s\n' "$W1" | aftms 15000 'power stayed away;')`,
`W3=$(printf '%s\n' "$ALL" | from 'WakeDetect: POWER_CONNECTED')`, `P1=$(pwin R1-cut R1-back)`,
`REF=$(hwin R1-x-go R1-x-done)`.

**PASS when all hold:**
1. Validity: `hwin R1-arm R1-cut | rend` is 1 or more; `W1` holds 1 `WakeDetect: POWER_DISCONNECTED`; `gap "$ALL" 'power stayed away;' 'WakeDetect: POWER_CONNECTED'` is 115000 or more (the quiet window is long enough).
2. A1: `W1` holds 1 line with `AapService: external-power disconnect: power lost; ending the session in `; `delay "$W1"` is 20000; `gap "$W1" 'WakeDetect: POWER_DISCONNECTED' 'power lost; ending the session in '` is 0 to 1000.
3. A6: `W1` holds 1 A6 line; `gap "$W1" 'power lost; ending the session in ' 'power stayed away;'` is 19500 to 23000.
4. Exit flavour: `gap "$W1" 'power stayed away;' 'AapTransport stopping and sending byebye ('` is 0 to 2000, and the reason in brackets on that line equals the reason on the same line in `REF`.
5. The session ended after A6 and not before: `printf '%s\n' "$W1" | upto 'power stayed away;' | c 'since connection is disconnected'` is 0, and `printf '%s\n' "$W1" | from 'power stayed away;' | c 'since connection is disconnected'` is 1 or more.
6. Stand-down: `printf '%s\n' "$W1" | ep 'keeping the wireless bring-up down until something re-arms it.'` is 1, and `: power came back while the session was ending` is 0.
7. Phone: `printf '%s\n' "$P1" | c 'received ByeByeRequest'` is 1 or more.
8. Quiet: in `W2`, `createGroup SUCCESS`, `NativeAA: Attempting active poke to device`, `MATCH! Starting AapService` and `SSL handshake complete` are all 0.
9. Screen untouched: `printf '%s\n' "$W1" | ep ': screen off '` is 0, and `screen not turned off` is 0. With the fake lever, `R1.wake` is lit too.
10. Return: `W3` holds 1 `WakeDetect: POWER_CONNECTED` and 1 `power is back; re-arming the wireless bring-up.`; `gap "$W3" 'WakeDetect: POWER_CONNECTED' 'power is back; re-arming'` is 0 to 1000; `AapService: turning the screen on (`, `the pending disconnect is cancelled` and `power is back; checking USB again.` are 0 in `W3`.
11. New session: `ssl2=0`, and `printf '%s\n' "$W3" | rend` is 1 or more.
12. `the session end never reached the stand-down in ` is 0 in `ALL`.

Report: the lever, the A1 to A6 gap in ms, the A6 to ByeBye gap, the quiet window's length in ms
(condition 1), the POWER_CONNECTED to SSL gap in `W3`, and every count in condition 8.

#### R2. Power back inside the delay, and a flap

On the fake lever the chain above runs this. Use this script alone on the cable lever, or when the
chain stopped before R2.

```bash
RUN=R2; PHCAP=1; cab; K="$BASE $A0 $EP_ON"; bringup
sleep 20; mark R2-cut; pw_lost; sleep 8
pw_back; mark R2-back; sleep 30; mark R2-hold
if [ "$LEVER" = fake ]; then
  mark R2-flap; pw_lost; sleep 5; pw_back; sleep 5; pw_lost; sleep 30; mark R2-flap-end
  pw_back; waitfor 120 'SSL handshake complete'; echo "ssl2=$?" | tee -a $OUT/R2.summary; sleep 15
fi
endrun
```

`Wa=$(hwin R2-cut R2-hold)`, `Wb=$(hwin R2-flap R2-flap-end)`, `Wc=$(hwin R2-flap-end R2-x-go)`.

**PASS when all hold** (conditions 4 and 5 only on the fake lever; on the cable lever report the flap
as not run):
1. Cancel (`Wa`): `WakeDetect: POWER_DISCONNECTED` 1, A1 1, `WakeDetect: POWER_CONNECTED` 1, A2 (`the pending disconnect is cancelled.`) 1, and `gap "$Wa" 'WakeDetect: POWER_CONNECTED' 'the pending disconnect is cancelled.'` is 0 to 1000. Validity: `gap "$Wa" 'WakeDetect: POWER_DISCONNECTED' 'WakeDetect: POWER_CONNECTED'` is 18000 or less, else the run is void (the plug came too late).
2. Nothing fired (`Wa`): the A6 line 0, `not ending the session (enabled=` 0, `since connection is disconnected` 0; `hwin R2-back R2-hold | rend` is 1 or more.
3. Phone: `pwin R2-cut R2-hold | c 'received ByeByeRequest'` is 0.
4. Flap (`Wb`): A1 is 2, A2 is 1, A6 is 1, `not ending the session (enabled=` is 0, and `lastgap "$Wb" 'power lost; ending the session in ' 'power stayed away;'` is 19500 to 23000. A timer left over from the first loss would end the session about 10 s earlier and print a second `not ending` line.
5. Return (`Wc`): `power is back; re-arming the wireless bring-up.` 1, `ssl2=0`, `printf '%s\n' "$Wc" | rend` 1 or more.

#### R6. Screen row on, no grant

Precondition: `admin` reads 0.

```bash
RUN=R6; PHCAP=1; cab; K="$BASE $A0 $EP_ON_SCR"; bringup
sleep 20; mark R6-cut; pw_lost; sleep 35
[ -z "$CABLE" ] && wake | tee $OUT/R6.wake
pw_back; mark R6-back
waitfor 120 'SSL handshake complete'; echo "ssl2=$?" | tee -a $OUT/R6.summary
sleep 15; endrun
```

`ALL=$(hwin R6-cut R6-x-go)`, `W1=$(printf '%s\n' "$ALL" | upto 'WakeDetect: POWER_CONNECTED')`,
`W2=$(printf '%s\n' "$ALL" | from 'WakeDetect: POWER_CONNECTED')`.

**PASS when all hold:**
1. `W1`: A1 1; `screen not turned off, no device admin grant` 1; A6 1; `gap "$W1" 'screen not turned off' 'power stayed away;'` is 0 or more.
2. `W1`: `ep 'keeping the wireless bring-up down'` 1; `ep ': screen off '` 0; `WakeDetect: SCREEN_OFF` 0.
3. Fake lever only: `R6.wake` is lit.
4. Phone: `pwin R6-cut R6-back | c 'received ByeByeRequest'` 1 or more.
5. `W2`: `power is back; re-arming the wireless bring-up.` 1; `AapService: turning the screen on (` 0; `ssl2=0`; `rend` 1 or more.

#### R10. The service stops inside the delay (fake lever only)

If `LEVER=cable`, R10 is UNTESTABLE: `ACTION_EXIT` needs adb inside the delay. Go to T1. On the fake
lever the chain above runs R10. Use this script alone only when the chain stopped before R10.

```bash
RUN=R10; PHCAP=1; CABLE=; K="$BASE $A0 $EP_ON"; bringup
sleep 20; mark R10-cut; pw_lost; sleep 8
mark R10-exit; send ACTION_EXIT > $OUT/R10-exit.reply; sleep 5
launch_arm R10-arm2
waitfor 120 'SSL handshake complete'; echo "ssl2=$?" | tee -a $OUT/R10.summary
sleep 45; mark R10-hold; pw_back; sleep 10; endrun
```

**PASS when all hold:**
1. `hwin R10-cut R10-exit`: A1 is 1.
2. Validity: `hwin R10-exit R10-arm2 | c 'AapService destroying...'` is 1 or more. If it is 0, the service never stopped and R10 is void.
3. `hwin R10-exit R10-x-go`: the A6 line 0, `not ending the session (enabled=` 0, and `external-power disconnect` 0 in total.
4. `ssl2=0`; in `hwin R10-arm2 R10-hold`, `rend` is 1 or more, and `gap "$(hwin R10-arm2 R10-hold)" 'SSL handshake complete' 'since connection is disconnected'` is NA or negative.

Report the time from `R10-cut` to `R10-hold` in seconds; it must be over 30, so the old deadline fell
inside the window.

#### T1. The timer while the unit sleeps (always the cable)

The point: the timer used to stop while the SoC slept. It now holds a partial wake lock and checks an
`elapsedRealtime` deadline. This run pulls the real cable, so nothing keeps the SoC awake, and lets the
panel time out inside a 120 s delay. adb to D-POCO is gone for the whole wait.

```bash
RUN=T1; PHCAP=1; CABLE=1; adb -s $U shell dumpsys battery reset; sleep 0.3
adb -s $U shell dumpsys battery | grep -a -E 'AC powered|USB powered' | tee $OUT/T1.battery0   # a real source must read true
K="$BASE $A0 $EP_SLEEP"; bringup
sleep 20; adb -s $U shell settings put system screen_off_timeout 15000; sleep 0.3
adb -s $U shell cat /sys/power/suspend_stats/success > $OUT/T1.susp0 2>&1; sleep 0.3
mark T1-cut; adb -s $U shell input keyevent 3; sleep 0.3; pw_lost
sleep 150
pw_back; wake | tee $OUT/T1.wake
adb -s $U shell cat /sys/power/suspend_stats/success > $OUT/T1.susp1 2>&1; sleep 0.3
adb -s $U shell dumpsys power | grep -a -c 'ExternalPowerDisconnect' > $OUT/T1.lock; sleep 0.3
panel_on; sleep 2; mark T1-back
adb -s $U shell settings put system screen_off_timeout 1800000; sleep 10; endrun
[ "$LEVER" = fake ] && fakeplug
```

If `T1.battery0` reads no real source, T1 is UNTESTABLE.

`W=$(hwin T1-cut T1-x-go | upto 'WakeDetect: POWER_CONNECTED')`, `P=$(pwin T1-cut T1-back)`.
`G=$(gap "$W" 'power lost; ending the session in ' 'power stayed away;')`; if `G` is NA,
`G=$(gap "$W" 'power lost; ending the session in ' 'not ending the session (enabled=')`.

**PASS when all hold:**
1. Validity: `W` holds 1 `WakeDetect: POWER_DISCONNECTED` and 1 `WakeDetect: SCREEN_OFF`; `gap "$W" 'WakeDetect: POWER_DISCONNECTED' 'WakeDetect: SCREEN_OFF'` is 10000 to 25000, so the panel went off inside the delay.
2. A1: `delay "$W"` is 120000.
3. The deadline held: `G` is 119500 to 126000.
4. `could not hold the CPU awake for the timer: ` is 0, `the session end never reached the stand-down in ` is 0, and `WakeDetect: power lost beside a screen-off` is 0.
5. If `W` holds the A6 line: `printf '%s\n' "$P" | c 'received ByeByeRequest'` is 1 or more. If `W` holds `not ending the session (enabled=` instead, the session ended before the deadline (the existing screen-off path, not this change): condition 5 is INCONCLUSIVE, and report the time of `since connection is disconnected` against `WakeDetect: SCREEN_OFF`.
6. `T1.lock` reads 0: the lock was released after the end.

Record `T1.susp0`, `T1.susp1` and `T1.wake`. Add `suspended=yes` when the count rose, `suspended=no`
when it did not, or `suspended=unknown` when either file holds an error, to the verdict line.

#### Grant

Skip to Stage B when `SECURE_POCO=yes`.

```bash
adb -s $U shell dpm set-active-admin --user 0 $ADMIN; sleep 1; admin     # must read 1
```

If `dpm` fails, use HS3:

```bash
adb -s $U shell am start -a android.app.action.ADD_DEVICE_ADMIN --ecn android.app.extra.DEVICE_ADMIN $ADMIN; sleep 3
cue "HS3: accept the device admin screen for Open Headunit on D-POCO"
sleep 30; admin                                                          # must read 1
```

If `admin` still reads 0, V3, V4 and R5/R7 are UNTESTABLE; go to Stage B.

#### V3. The screen verbs with the grant, no session

```bash
RUN=V3; PHCAP=; CABLE=; phone_off $MOTO; adb -s $U shell am force-stop $PKG; sleep 0.3; [ "$LEVER" = fake ] && fakeplug
K="$BASE $A1 $EP_OFF"; put $K; rd 'allow-external|external-power' | tee $OUT/$RUN.keys
th_gate; cap_start; mark V3-start; adb -s $U shell am start -n $MAIN; sleep 10
mark V3-off; send ACTION_SCREEN_OFF > $OUT/V3-off.reply; sleep 3; wake | tee $OUT/V3.w-off; kg | tee $OUT/V3.kg-off
sleep 10; mark V3-on; send ACTION_SCREEN_ON > $OUT/V3-on.reply; sleep 5; wake | tee $OUT/V3.w-on; focus | tee $OUT/V3.focus; kg | tee $OUT/V3.kg-on
mark V3-end; clean_hu; cap_stop; th_report V3
```

**PASS when all hold:**
1. `V3-off.reply` holds `"screenOff":true` and `"ok":true`; `V3.w-off` is dark; `hwin V3-off V3-on` holds 1 `WakeDetect: SCREEN_OFF`.
2. `hwin V3-off V3-on`: `WakeDetect: power lost beside a screen-off` is 0.
3. `hwin V3-on V3-end`: 1 `AapService: turning the screen on (` line holding `automation` and `the home screen`; `V3.w-on` is lit; the `gap` from that line to `WakeDetect: SCREEN_ON (screen was off for ` is 0 to 5000.
4. `AapService: screen-on launch failed: ` is 0.

Record the strategy, the `device wake-up` lines, both `kg` readings and `V3.focus`.

#### V4. The screen verbs during a session

```bash
RUN=V4; PHCAP=1; CABLE=; K="$BASE $A1 $EP_OFF"; bringup
sleep 20; mark V4-cut; mark V4-off; send ACTION_SCREEN_OFF > $OUT/V4-off.reply; sleep 10; wake | tee $OUT/V4.w-off
mark V4-on; send ACTION_SCREEN_ON > $OUT/V4-on.reply; sleep 8; wake | tee $OUT/V4.w-on; focus | tee $OUT/V4.focus
sleep 15; mark V4-after; endrun
```

**PASS when all hold:**
1. `V4-off.reply` holds `"screenOff":true`; `V4.w-off` is dark; `V4.w-on` is lit.
2. `hwin V4-off V4-after`: `WakeDetect: power lost beside a screen-off` is 0.
3. If `hwin V4-off V4-on | c 'since connection is disconnected'` is 0 (the session survived the dark panel): the screen-on line in `hwin V4-on V4-after` holds `the projection`; `V4.focus` names `AapProjectionActivity`; `hwin V4-on V4-after | rend` is 1 or more.
4. If that count is 1 or more (the existing sleep path ended the session): the screen-on line holds `the home screen`. Report it as a finding about the sleep path, not a FAIL of this change.

Record which of 3 or 4 applied, `pwin V4-off V4-after | c 'received ByeByeRequest'`, and the strategy.

#### R5 and R7. Screen-off after the end, at the minimum delay, then power back

Precondition: `admin` reads 1. The panel stays dark for about 40 s, under the 60 s hibernate
threshold.

```bash
RUN=R5; PHCAP=1; cab; K="$BASE $A0 $EP_MIN_SCR"; bringup
sleep 20; mark R5-cut; pw_lost; sleep 20
if [ -z "$CABLE" ]; then wake | tee $OUT/R5.wake; kg | tee $OUT/R5.kg; fi
sleep 20; pw_back; mark R7-back; sleep 5; wake | tee $OUT/R7.wake; focus | tee $OUT/R7.focus; kg | tee $OUT/R7.kg
waitfor 120 'SSL handshake complete'; echo "ssl2=$?" | tee -a $OUT/R5.summary
sleep 15; endrun
```

`ALL=$(hwin R5-cut R5-x-go)`, `W=$(printf '%s\n' "$ALL" | upto 'WakeDetect: POWER_CONNECTED')`,
`V=$(printf '%s\n' "$ALL" | from 'WakeDetect: POWER_CONNECTED')`.

**R5 PASS when all hold** (on `W`):
1. A4 clamp: 1 A1 line, and `delay "$W"` is 5000 (the 2 was clamped).
2. A6 1; `gap "$W" 'power lost; ending the session in ' 'power stayed away;'` is 4500 to 8000.
3. `ep 'keeping the wireless bring-up down'` 1; `printf '%s\n' "$W" | grep -aF ': screen off ' | grep -acF 'done.'` 1; `refused by the system.` 0.
4. `WakeDetect: SCREEN_OFF` 1, and `gap "$W" 'WakeDetect: POWER_DISCONNECTED' 'WakeDetect: SCREEN_OFF'` is over 5000.
5. `WakeDetect: power lost beside a screen-off` is 0.
6. Fake lever only: `R5.wake` is dark.
7. Phone: `pwin R5-cut R7-back | c 'received ByeByeRequest'` 1 or more.

**R7 PASS when all hold** (on `V`):
1. `WakeDetect: POWER_CONNECTED` 1.
2. 1 `AapService: turning the screen on (` line holding `external power back`; `gap "$V" 'WakeDetect: POWER_CONNECTED' 'AapService: turning the screen on ('` is 0 to 1000.
3. `power is back; re-arming the wireless bring-up.` 1, and `gap "$V" 'AapService: turning the screen on (' 'power is back; re-arming'` is 0 or more.
4. `gap "$V" 'AapService: turning the screen on (' 'WakeDetect: SCREEN_ON (screen was off for '` is 0 to 5000.
5. `R7.wake` is lit.
6. `ssl2=0`, and `printf '%s\n' "$V" | rend` is 1 or more.
7. `AapService: screen-on launch failed: ` is 0.

Conditions 4 and 5 count only when `lever.env` reads `ROM_WAKES_ON_PLUG=no`. Otherwise they are
INCONCLUSIVE: report the gap from `WakeDetect: POWER_CONNECTED` to the `SCREEN_ON` line instead.

Record the whole screen-on line, the `device wake-up` lines, `R5.kg`, `R7.kg`, `R7.focus`, and the
counts of `WakeDetect: re-arming the wireless bring-up held while the unit was asleep` and
`WakeDetect: hibernate wake detected` in `V`.

#### HS2. Remove the grant on D-POCO

Only if the grant step set it.

```bash
adb -s $U shell am force-stop $PKG; sleep 0.3
adb -s $U shell am start -n 'com.android.settings/.Settings$DeviceAdminSettingsActivity'; sleep 3
cue "HS2: deactivate Open Headunit on the device admin screen of D-POCO"
sleep 30; admin                                                          # must read 0
adb -s $U shell input keyevent 3
```

**If `admin` does not read 0, stop and tell the operator**: the grant blocks an uninstall on this
unit.

### Stage B. D-SAM as head unit, D-POCO as phone

```bash
U=$SAM; UN=D-SAM; PH=$POCO; MAC=<POCO_MAC from P>; mkbase
phone_off $MOTO
adb -s $POCO shell am force-stop $PKG; sleep 0.3      # D-POCO is only the phone now
adb -s $POCO shell dumpsys battery reset; sleep 0.3   # undo Stage A's fake plug state
```

D-SAM facts (`rig-quirks/units/D-SAM.md`): no `su` and no `sed` (`put` edits on the host), `am`
rejects `-p` (the helpers use `-n`), no `keyevent 223` (`panel_off` toggles with 26), and `logcat -G`
prints a usage error, which is harmless.

#### L-SAM. Which lever works on D-SAM

Run L-POCO's block with `RUN=L-SAM`. The same `FAKE_OK` rule sets `LEVER_SAM`. Record the ROM
readings. Then `LEVER=$LEVER_SAM`.

#### S1. Screen-on below API 33

```bash
RUN=S1; PHCAP=; CABLE=; adb -s $U shell am force-stop $PKG; sleep 0.3
put <the S1 keys from section 4>; rd 'external-power|wifi-connection-mode|log-level' | tee $OUT/$RUN.keys
th_gate; cap_start; mark S1-start; adb -s $U shell am start -n $MAIN; sleep 10; state
panel_off; sleep 8; wake | tee $OUT/S1.w-dark
mark S1-on; send ACTION_SCREEN_ON > $OUT/S1-on.reply; sleep 3; wake | tee $OUT/S1.w-on
mark S1-end; clean_hu; cap_stop; th_report S1
```

**PASS when all hold:**
1. `S1.w-dark` is dark: 8 s passed with no wake, so the panel does not light by itself. If it is lit, S1 is void.
2. `hwin S1-on S1-end` holds 1 `AapService: turning the screen on (` line holding `automation`, and that line does not hold `) by wake lock and ` (below API 33 the wake lock acts alone).
3. `S1.w-on` is lit.
4. `AapService: screen-on launch failed: ` is 0.

#### R1s. R1 on API 19 (fake lever only)

If `LEVER_SAM=cable`, R1s is UNTESTABLE (API 19 cannot hold the session in its buffer for a dump). If
the D-POCO bond to `Navegadortz3` is missing, R1s is UNTESTABLE.

Run R1's block with `RUN=R1s` and every `R1-` label as `R1s-`. Grade R1's conditions 1 to 12 on its
windows. D-SAM settles with two `createGroup SUCCESS` before SSL (`rig-quirks/units/D-SAM.md`); that
is outside the quiet window and does not void the run.

#### S2. A real cable pull with no session

Precondition: P read `USB powered: true` or `AC powered: true` on D-SAM. If not, S2 is UNTESTABLE.

```bash
RUN=S2; PHCAP=; CABLE=1; adb -s $U shell dumpsys battery reset; sleep 0.3
put <the S2 keys from section 4>; rd 'external-power|wifi-connection-mode' | tee $OUT/$RUN.keys
th_gate; cap_start; mark S2-start; adb -s $U shell am start -n $MAIN; sleep 5
mark S2-cut; pw_lost; sleep 30; pw_back; sleep 5; mark S2-end
cap_stop; th_report S2
adb -s $U shell dumpsys battery | grep -a -E 'AC powered|USB powered' | tee $OUT/S2.battery
clean_hu
```

`W=$(hwin S2-cut S2-end)`.

**PASS when all hold:**
1. `W`: `WakeDetect: POWER_DISCONNECTED` 1 and `WakeDetect: POWER_CONNECTED` 1; report the gap between them.
2. `W`: the A3 line 1; `gap "$W" 'WakeDetect: POWER_DISCONNECTED' 'nothing is projecting'` 0 to 1000.
3. `W`: `external-power disconnect` is exactly 1 (the A3 line, and nothing on the return).
4. `S2.battery` reads the same real source as P.

`wrapped=yes` makes S2 void, as for every run.

### X. Clean-up

```bash
for s in $POCO $SAM $MOTO; do adb -s $s shell dumpsys battery reset; sleep 0.3
  adb -s $s shell dumpsys battery | grep -a -E 'AC powered|USB powered'; sleep 0.3; done     # as in P
adb -s $POCO shell settings put system screen_off_timeout <TIMEOUT_POCO>
adb -s $SAM shell settings put system screen_off_timeout <TIMEOUT_SAM>
U=$POCO admin; U=$SAM admin                                               # both must read 0
adb -s $DHU shell svc bluetooth enable; sleep 3; adb -s $DHU shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'
```

Restore each unit's `settings.xml` from `backup-D-POCO.xml` and `backup-D-SAM.xml` with `put` and
no keys (it copies the backup unchanged), read each back, and diff it against the backup. Put D-MOTO
and D-POCO back in airplane mode. If either `admin` does not read 0, stop and tell the operator.

## 8. Do not re-run

- **R0's unit tests.** The dev host ran 3336 tests, 0 failures, at `8db52ceb`. The rig builds the
  APK and checks identity only.
- **C0 on D-HU.** Settled for D-HU: the `dumpsys battery` lever sends no `POWER_*` broadcast there,
  and the ROM lights the panel on both plug changes. No D-HU power-loss run is possible with that
  lever.

## 9. Report back

The numbers that decide shipping:

1. **Levers:** `LEVER_POCO`, `LEVER_SAM` and `MOTO_FAKE`, with the L counts behind each, and the two
   ROM readings per unit.
2. **R1 (and R1s):** the A1 to A6 gap in ms, the counts of `createGroup SUCCESS`,
   `Attempting active poke` and `SSL handshake complete` in the quiet window, the phone's
   `received ByeByeRequest` count, and the POWER_CONNECTED to SSL gap of the new session.
3. **T1:** the A1 to A6 (or `not ending`) gap in ms with the cable out and the panel off, and
   `suspended=`.
4. **R5, R7 and V3:** the whole screen-on line, the panel reading 5 s after the request, and the
   `device wake-up` line on API 35, beside L-POCO's ROM readings.

Give each verdict in the fixed format of `TESTING-TEMPLATE.md` §7, say which lever each run used, and
put every deviation in Setup notes. Add each unit's lever result to its `rig-quirks/units/` file.

The block below lists every app string the runs grep. Each one is in `app/src/main` at `8db52ceb`.


These strings are also grepped, but they are not in our tree, so they are not decisive strings. The
first is in the phone's Android Auto log, the second is our own phone marker tag, the third is in the
head unit's `PowerManagerService` log, the next two are in D-MOTO's broadcast history, and the last is
shell output on D-POCO.

```external-strings
received ByeByeRequest
OHURIG
device wake-up
act=android.intent.action.ACTION_POWER_DISCONNECTED
act=android.intent.action.ACTION_POWER_CONNECTED
Lock credential verified successfully
```
