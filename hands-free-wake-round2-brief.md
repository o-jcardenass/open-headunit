# hands-free-wake, round 2 brief: the wake on a second phone, and three things the owner asked for

## Revised 2026-09-17, after this brief was first pushed

**Re-read sections 1, 2 and the new Part O.** Everything else is unchanged, and the wake runs keep
their numbering, so a results file still pairs run for run.

- **The candidate moved twice.** The branch was compacted again and the head unit server work was
  folded into it, so the tip section 1 first named is gone. Section 1 has the SHA and count that
  count; a build reading 2076 or 2063 is the wrong APK.
- **The round is no longer the wake alone.** Part O grades the three changes the repo owner asked
  for after testing the branch, one reporter's issue, and the head unit server's close and redial. They are
  independent of the wake and of each other, and Part O can be run before or after the wake blocks.
- **One Part O precondition changes a wake run's assumptions.** O1 deliberately leaves the wireless
  stack stopped. Press the WiFi button, or relaunch the app, before any H run that follows it.

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `18663348` | 5 on `main` | 2102 tests, 0 failures |
| Baseline | same APK, the wake setting off, which is now its default | | | |

```bash
git fetch fork
git checkout -B hands-free-wake-r2 fork/fix/audio-sink-and-wireless-bring-up
./gradlew :app:testGithubDebugUnitTest      # 2102, 0 failures
```

**The branch was compacted a seventh time and then grew a folded commit, so what is on `fork` is a
rewrite.** Reset any older local copy rather than pulling it; if the gate reads 2076 or lower you are
on an earlier tip, so stop and rebuild. What Part O grades rides inside two of the five commits: the
status pill's X, its automation action and the settings-close re-arm in `c23d8340` (Native AA); the
auto-start device and the head unit server's close and redial in `cc4128f1` (Misc).

One APK for the whole round.

## 2. What this round is, and what round 1 already settled

Round 1 (`hands-free-wake-and-proto-schema-round1-results.md`) graded the whole of the proto part and
the serve path and they are **DONE**: P1/P1'/P1'' PASS and retire field 3 as a lead, P2/P3 PASS,
W1/W2 PASS. S1 is rig limited, not build limited, and is not retried here. **Only the wake is open**,
and round 1 left it in a specific state:

- **H2 FAIL on D-POCO.** The escalated wake fired at exactly 90.0 s, `socket.connect()` succeeded and
  took the phone's hands-free slot, and Android Auto never opened its Bluetooth channel afterwards.
  That is the `filter_profile_connection_by_acl` shape the round 1 brief pre-registered.
- **H3 FAIL.** The hands-free link stayed `Disconnected` for over five minutes and did not recover
  unaided.

Two things changed on the branch in answer, and this round grades both:

- The setting **ships off**, which is round 1's own pre-registered consequence of an H3 FAIL.
- An escalated wake that goes unanswered for `POKES_AFTER_ESCALATION` pokes now stands that device
  down for the rest of the arming. Round 1 measured the mechanism this closes: once the wake takes
  the slot the guard has no link left to defer to, so the ordinary loop poked nine more times, holding
  the slot 15 s of every 30, and the phone never had an uninterrupted window to restore its link.

**The round is no longer the wake alone.** Part O, at the end, grades four changes that have
nothing to do with the wake: the status pill's X, the settings screen no longer restarting the
wireless bring-up when it closes, the auto-start Bluetooth device no longer being cleared behind the
user, and the head unit server being closed on ACC-off and redialled when WiFi returns. The first
two are what the repo owner asked for after testing the branch; the third is a reporter's issue
with a root cause found by reading. Only O6 needs a phone that projects, so the rest can run before
or after the wake blocks and on whichever unit is free.

**The one open question in the wake itself is whether it works on any phone at all.** A reporter measured a
poke answered in 66 s on their own Note20 Ultra, and D-POCO never answered one at all. Those are not
in conflict: the reporter's poke went out while the link read free, and the escalation's case is a
link that is held. **D-MOTO has never been asked**, and that is what this round is for.

## 3. The rig, and the one change from round 1

| Block | Head unit | Phone | Why |
|---|---|---|---|
| 1 | D-HU (`27870808938846`) | **D-MOTO** (`ZY22GC3BM4`) | Android 14 stock Gearhead, never graded for the wake |
| 2 | D-HU | D-POCO (`4f4027e9`) | the known negative, kept only to grade the new bound |

**D-SAM is struck from every wake run, permanently.** `BluetoothHelper.handsFreeLinkState` reads
`BluetoothProfile.HEADSET_CLIENT`, added in API 24; D-SAM is API 19, so
`getProfileConnectionState(16)` can never report connected there, `decision.poke` is always true and
the `Not poking` branch this whole thread grades is unreachable. Round 1 confirmed this live, with
`dumpsys bluetooth_manager` reading the link `Connected` while the app poked on its ordinary cadence.
Do not brief D-SAM for hands-free work again, and do not record it as UNTESTABLE a second time.

Run order: **R0, H2r, H3r, H5, then block 2's C1.** H2r first because everything else reads its
capture or depends on its verdict.

## 4. Settings, and three preconditions round 1 lost time to

Written the way section 4 of the round 1 brief describes; D-HU is rooted, so `set_hu_pref.sh` or
`set_hu_settings_host.py`. Read every one of them back before the first run.

```xml
<int name="log-level" value="0" />                                    <!-- VERBOSE, every run -->
<int name="wifi-connection-mode" value="3" />                         <!-- Native AA -->
<boolean name="native-aa-wake-over-hands-free-link" value="true" />   <!-- CANDIDATE arm only -->
<int name="native-driver-selection-mode" value="0" />                 <!-- all H runs, restore after -->
```

- **`connection-modes` must contain `wifi`.** A leftover `{usb}` makes `WirelessSelectionPolicy`
  refuse every wireless bring-up and say so only at `WifiLauncher: wireless bring-up requested, but
  WiFi is not one of the chosen connection modes`. Round 1 hit it twice, the second time because the
  settings backup it restored carries the stale value. Set `{usb,wifi}` and read it back.
- **`native-driver-selection-mode=0` for the duration.** At its `AUTO` default a cold launch with one
  known phone auto-pokes through `selectDriver()`, which honours the guard correctly but stops
  retrying after about three cycles, so a plain relaunch can never reach 90 s. Restore it to `1`
  at the end of the round.
- **`native-aa-wake-over-hands-free-link` now defaults to `false`.** Read it back before the
  candidate arms rather than assuming the old default; a run that silently graded the baseline twice
  is the easiest way to waste this round.

## 5. The precondition, and a phone that can latch itself out of the round

The wake runs need the phone holding a live hands-free link to the head unit **with no session
running**. Use section 5 of the round 1 brief unchanged: cycle **the head unit's** adapter, wait up to
15 s, and verify on the phone with `dumpsys bluetooth_manager` immediately before the step that needs
it. A run that starts without a confirmed `Connected` is UNTESTABLE, not FAIL.

End the live session by disabling Gearhead on the phone (`pm disable-user`), not by a head-unit user
exit. Round 1 established why: `headunit://disconnect` removes the P2P group and the app refuses to
re-arm after a real user exit, so the retry loop never restarts and neither outcome can occur. A
`link_lost` disconnect leaves the loop running indefinitely, which is the state being graded. A
Gearhead that is merely force-stopped relaunches and reconnects in under 3 s.

**If Gearhead starts cancelling every attempt, it has latched.** Round 1 hit
`WIRELESS_SETUP_CANCELLED_ALREADY_STARTED` on two different phones, visible only in the phone's own
`GH.ConnLoggerV2` log. It survived a Gearhead `force-stop` and, on D-POCO, a **full phone reboot**.
The only thing that cleared it was `pm clear com.google.android.projection.gearhead`, which also
forgets that phone's accepted vehicles. Check for it before blaming the build.

## 6. Runs

### R0. Gate

`./gradlew :app:testGithubDebugUnitTest` reads **2102, 0 failures**. Record the installed APK's
identity by its symbol check before anything else.

**PASS** when the gate matches and the installed APK is the one just built.

### H2r. Does the wake work on a phone that is not D-POCO

**Candidate arm on D-MOTO. This is the run the round exists for.** Land one ordinary session, end it
with `pm disable-user` on Gearhead, confirm the hands-free link reads `Connected` with no session
running, then capture for **6 minutes** from the first `Not poking` line.

```bash
grep -ac "ACTIVELY LISTENING on Android Auto UUID" log.txt   # armings in this capture
grep -a  "Not poking"                              log.txt
grep -a  "despite the hands-free"                  log.txt
grep -ac "Connection accepted from"                log.txt
```

**Read the accept count first.** Any `Connection accepted from` between the first `Not poking` and the
first `despite the hands-free` means the 90 s never matured and the run is **UNTESTABLE**, not a FAIL.

**PASS:** an escalated wake goes out, the first not before 90 s after the first `Not poking`, and
`Connection accepted from` follows within about 90 s of it, going on to `Incoming connection detected`
and `SSL handshake complete`. Report the wake-to-accept interval as its own figure. The reporter's
66 s is the precedent, not a bound.

**FAIL:** wakes go out, `socket.connect()` succeeds, and no accept follows. That reproduces round 1's
D-POCO result on a second phone and retires the escalation as a lever, whatever H3r says.

**No prior session in this process is needed.** The escalation now also accepts a phone stored in
`last-connected-native-mac`, which H5 grades directly.

### H3r. What the wake costs now, and whether the bound holds

**Runs on H2r's capture and the phone state after it.** On the phone, before the wake and then every
15 s for 5 minutes after it:

```bash
adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -A 5 "HeadsetService"
```

And on the head unit's log, the line the bound prints:

```bash
grep -a "alone for now" log.txt      # this unit giving the link back
grep -ac "Calling socket.connect()" log.txt   # pokes after the escalated wake
```

**PASS:** the app stops poking after the escalated wake goes unanswered, the `alone for now` line
appears, and the hands-free link is back within 3 minutes of that line without anyone touching the
phone. Report how long it stayed down and how many pokes went out after the wake.

**FAIL:** the link is still down 3 minutes after the app stopped poking, or the app never stopped.
The first would mean giving the slot back is not enough on this phone and the wake has no acceptable
form; the second is a defect in the bound and the poke count says so.

**Not gradable from the head unit's log alone.** The link state is a phone-side read, so a run without
it grades nothing.

### H5. The wake still escalates on a unit that has only been rebooted

**Candidate arm, unchanged from the round 1 brief, and still ungradable on any build before this one.**

1. Run one ordinary Native AA session so the phone's address is stored, then end it.
2. Read `last-connected-native-mac` back and record it. An empty value voids the run.
3. **Reboot the head unit.** Let the radio auto-connect hands-free on its own. Do not connect.
4. Establish the section 5 precondition and confirm the link reads `Connected` with no session.
5. Capture for **6 minutes** from the first `Not poking` line.

**PASS:** an escalated wake appears, the first not before 90 s, in a capture whose first
`ACTIVELY LISTENING` line is the first of this process.

**FAIL:** refusals run past 90 s and no wake goes out. Read `last-connected-native-mac` once more
before grading: an empty value makes it UNTESTABLE, not FAIL.

## Block 2: D-POCO, the known negative

### C1. The bound holds where the wake does not work

Round 1 measured D-POCO losing hands-free for over five minutes for nothing. Repeat H2r's sequence on
D-POCO and grade **only H3r's conditions**: the wake is expected to buy no accept on this phone, and
that is not a FAIL here.

**PASS:** the `alone for now` line appears, no more than `POKES_AFTER_ESCALATION` pokes follow the
escalated wake, and the hands-free link is back within 3 minutes of the app standing down. That is the
five minutes bounded, which is the whole claim.

**FAIL:** the link is still down 3 minutes after the stand-down, unaided. Then the wake costs more
than it can ever return on this class of phone and the next lever is removing it, not tuning it.

## Part O: the four the owner asked for, and one reporter's issue

Independent of the wake and of each other. **Run O1 to O3 in order** (O2 assumes O1's stack state),
then O4, O5 and O6. Every run is graded on the head unit's log at VERBOSE plus, where it says so, a
read-back of `settings.xml` or one screenshot.

**Everything here is scripted except O4, which cannot be.** O4 grades the Save button on a settings
screen, so it is the one place in this round where the house rule "settings are changed in
`settings.xml`, never in the UI" is deliberately suspended, and it says so again in its own run.

**O1 to O3 use a new automation action rather than the pill's X.** The X is what the user taps and
`ACTION_CANCEL_WIRELESS` is what it sends, so driving the action grades the same path without a tap
at a guessed coordinate. One screenshot in O1 covers the button itself.

```bash
adb -s <unit> shell am start -a com.andrerinas.openheadunit.ACTION_CANCEL_WIRELESS
```

### O1. The bring-up can be stopped, and it stays stopped

**Native AA, no phone connected.** Relaunch the app, let the stack arm, and confirm from the log
that it did before cancelling anything.

1. Confirm the stack is up: `ACTIVELY LISTENING` and `createGroup SUCCESS` both present.
2. **Screenshot the home screen** while the pill is up, so the X is graded as a control:
   `adb -s <unit> shell screencap -p /sdcard/pill.png && adb -s <unit> pull /sdcard/pill.png`.
3. Send `ACTION_CANCEL_WIRELESS`. Capture for 3 minutes without touching anything.
4. Then bounce the **head unit's** Bluetooth adapter off and on, and wait 60 s.

```bash
grep -a  "the status pill's X stopped"           log.txt
grep -ac "ACTIVELY LISTENING"                    log.txt   # before vs after the cancel
grep -ac "createGroup SUCCESS"                   log.txt
grep -a  "stopped it from the status pill"       log.txt
grep -ac "Calling socket.connect()"              log.txt
```

**PASS:** the `status pill's X stopped` line appears; after it there is no further
`ACTIVELY LISTENING`, no `createGroup SUCCESS` and no `socket.connect()`; and the Bluetooth bounce
prints `stopped it from the status pill` instead of re-arming. The screenshot shows a round X at the
right-hand end of the pill.

**Report the counts from before the cancel as well as after.** A zero afterwards means nothing
unless the same greps were non-zero before it: an unarmed stack would produce the same silence, and
that is the state a stray `connection-modes` value leaves behind. If the before-counts are zero the
run is **UNTESTABLE**, not a PASS and not a FAIL.

**FAIL:** anything re-arms inside the 3 minutes or after the Bluetooth bounce. Name the first log
line that follows the cancel; it identifies the entry point the gate missed.

### O2. Asking for wireless by hand is the way back up

**Runs straight on from O1, with the stack still down.**

```bash
adb -s <unit> shell am start -a com.andrerinas.openheadunit.ACTION_START_WIRELESS_SCAN
```

```bash
grep -a "the stop from the status pill is lifted" log.txt
grep -a "Initializing WiFi Mode"                  log.txt
```

**PASS:** the lift line appears and the mode initialises. A session is not required.

**FAIL:** nothing happens, or O1's refusal line prints again. That would leave a user who cancelled
with no way back, which is worse than what this replaces, so grade it as blocking and say so.

### O3. Leaving the settings screen does not restart the wireless bring-up

**This is the repo owner's own report, and step 3 is its positive control.** Start from a running
stack (O2's state is fine). Open the settings screen with its deep link rather than by navigating,
and leave it with Back:

```bash
adb -s <unit> shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0
adb -s <unit> shell input keyevent KEYCODE_BACK
```

1. Open Settings. Confirm `the settings screen is open, so the wireless stack stops`.
2. Change something that is **not** wireless, in `settings.xml` with the screen closed if that is
   easier; what matters is that no wireless key moves. Return to the main screen. Wait 60 s.
3. Now the control: open Settings, change `wifi-connection-mode` (3 to 2 or back) **through the UI
   so the Save runs**, and go back.

```bash
grep -a  "settings screen closed with nothing wireless changed" log.txt
grep -a  "saved while the settings screen is open"              log.txt
grep -a  "wireless setting saved behind it"                     log.txt
grep -ac "Initializing WiFi Mode"                               log.txt
```

**PASS:** step 2 prints `settings screen closed with nothing wireless changed` and **no**
`Initializing WiFi Mode` follows it; step 3 prints `saved while the settings screen is open`, then
`wireless setting saved behind it` on the close, then **exactly one** `Initializing WiFi Mode`.

**FAIL, and they are different faults, so name which.** A bring-up after step 2 means the removal did
not take. **No** bring-up after step 3 is the worse one: a wireless setting the user saved never
reached the stack, and this brief pre-registers that as blocking. Two after step 3 is also a FAIL.

**Step 3 is the run that proves the mechanism still works**, so a round that reports step 2 alone
has graded half of it. Do not skip it because step 2 passed.

### O4. A chosen auto-start Bluetooth device survives

**D-HU or D-POCO, not D-SAM:** it needs `appops`, and its API 19 grants the overlay permission
outright. **This run drives the UI on purpose.** The defect is in the Save path of a settings
screen, so the picker and the Save button are the thing under test and `settings.xml` is the
instrument, not the lever. It is the only run in this round that touches a control.

Preconditions, read back before starting:

```xml
<int name="log-level" value="0" />
<!-- wifi-connection-mode absent entirely, so it defaults to NATIVE: the reporter's own state -->
```

`connection-modes` holding **Self only**. Bond **two** devices that are not ruled out as phones (a
laptop advertising hands-free plus a second phone is the honest stand-in for the reporter's paired
computer), and **one** hands-free device that is then powered off, standing in for the out-of-range
vehicle. Deny the overlay and read it back:

```bash
adb -s <unit> shell appops set com.andrerinas.headunitrevived SYSTEM_ALERT_WINDOW deny
adb -s <unit> shell appops get com.andrerinas.headunitrevived SYSTEM_ALERT_WINDOW
```

Then in Settings -> Automation -> Auto-start settings set **Auto-start on Bluetooth** and
**Auto-disconnect on Bluetooth** to the powered-off device, press **Save**, and leave the screen.

**Read `auto-start-bt-macs` and `auto-disconnect-bt-macs` out of `settings.xml`, app stopped, after
each of six steps:** the Save, arriving on the home screen, a session run and ended, the app exited,
a relaunch, and reopening the Automation screen.

```bash
grep -a  "AutoStartFragment: saved auto-start"   log.txt
grep -a  "driver candidates:"                    log.txt
grep -ac "more than one phone is paired"         log.txt   # build identity, see below
grep -ac "Overlay permission not granted"        log.txt   # build identity, see below
```

**PASS:** both keys hold the chosen address at **all six** reads, both rows still read as set when
the screen is reopened, and the screen shows an overlay notice row rather than having cleared
anything. `AutoStartFragment: saved auto-start 1 device(s), auto-disconnect 1, wake 0` on the Save
is what proves the Save ran at all.

**FAIL:** either key is empty at any read. **Report which step emptied it.** That is the whole value
of six reads, and it is the only way to catch the half of this report that has no known mechanism.
`auto-disconnect-bt-macs` emptying is what this run is specifically hunting: nothing was found that
writes that key except Save, so a reproduction here is new information.

**Those last two greps are build-identity checks, not conditions.** Both lines were deleted in the
candidate, so zero is the only answer a correct build can give and it proves nothing on its own. A
**non-zero** count means the installed APK is not this branch; stop and re-install.

**Report the `driver candidates:` roll-up verbatim** whether it passes or fails. It says how this
unit classified the three bonded devices, and the reporter's case turns on a computer and an
out-of-range vehicle landing in the same tier.

### O5. The wake rows are gone on a Self-only unit

**Runs on O4's unit, `connection-modes` still Self only.** One screenshot of Auto-start settings,
then set `connection-modes` back to include `wifi`, relaunch, and take a second.

**PASS:** the first screenshot has no "Device to wake for wireless Android Auto" row and no
poke-all-paired toggle, while the auto-start and auto-disconnect rows are still there; the second
has the wake row back.

**FAIL:** the wake row is present with Self only, or the auto-start rows vanished with it. The two
screenshots are the whole report; do not scroll to find a row, and if it is not on the first screen
say so.

### O6. The head unit server is closed on ACC-off, and redialled when WiFi returns

**Headunit Server mode, `wifi-connection-mode=1`, and the one run in Part O that needs a phone.**
The fault this guards against is the phone's: Android Auto's own server hands each accepted socket
to its car service and waits with no timeout, so a peer that vanishes without a FIN leaves it deaf
to everyone after, until the user restarts it by hand. This unit's job is to close cleanly.

**First, establish which ACC path this unit has; do not assume.** With the app running, watch for
the line while cutting ACC if the unit has it:

```bash
grep -a "WakeDetect: ACC off"            log.txt
grep -a "power lost beside a screen-off" log.txt
```

The intents listened for are `com.fyt.boot.ACCOFF`, `com.glsx.boot.ACCOFF`,
`com.cayboy.action.ACC_OFF`, `com.carboy.action.ACC_OFF` and
`android.intent.action.ACTION_MT_COMMAND_SLEEP_IN`. **If the unit names none of them**, use the
corroborated path instead: let the screen go off with power lost within 5 s of it, which prints the
second line. Either line is a valid entry to this run; **say which one your unit produced**, because
it decides what the result generalises to.

Land one ordinary Headunit Server session, then:

1. Trigger ACC-off by whichever path step 0 established. Wait 30 s.
2. Restore power and let the unit reconnect on its own. **Do not touch the phone.**
3. With the session live again, turn the head unit's WiFi off and back on. Wait 60 s.

```bash
grep -a  "network is back after a link-loss teardown" log.txt
grep -ac "SSL handshake complete"                     log.txt
```

**PASS:** one of the two ACC lines appears and the session is torn down rather than left hanging;
step 2 reconnects with nobody restarting the server on the phone; step 3 prints
`network is back after a link-loss teardown; discovery resumes` and reconnects. **Report the time
from power restored to `SSL handshake complete` for steps 2 and 3 separately.**

Note `SSL handshake complete` appears **twice per session in a VERBOSE capture**, once at DEBUG with
a `Handshake:` prefix and once at INFO from `AapSslContext.performHandshake`. Halve the count, or
match the INFO one only.

**FAIL:** the phone's server is deaf afterwards, which reads as this unit connecting, the phone
accepting, and nothing following. That is exactly the state the change exists to prevent, so a FAIL
means the close did not run.

**The banner is the other half, and it is worth a line either way.** If the server does go deaf, the
main screen should say so in the user's words and tell them to stop and start the head unit server
on the phone. Screenshot it if it appears. It is now shown on **any** connection mode, a Self-only
unit included, because it is keyed on the endpoint dialled rather than on the mode.

**Step 3 is the whole point below API 21**, where nothing kicked discovery when WiFi returned and a
broadcast receiver does now. D-SAM is API 19 and is the only unit that can grade that, so run step 3
there if it is free. **This is the one Part O run D-SAM is good for**; section 3 strikes it from the
wake runs only, and for a different reason.

## 7. Phone-side capture, worth more than anything else here

If `adb logcat` can run on the phone during H2r, grep for:

```
WIRELESS_SETUP_SHARED_HFP_CONNECTING
WIRELESS_SETUP_SHARED_ACTION_ACL_CONNECTED
WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE
WIRELESS_SETUP_CANCELLED_HU_NOT_CONNECTED
WIRELESS_SETUP_CANCELLED_ALREADY_STARTED
```

`..._HFP_CONNECTING` right after our wake settles the mechanism outright and makes H2r causal rather
than correlational. On a FAIL it is even more valuable: it separates "Gearhead never saw the event"
from "Gearhead saw it and declined", and only the second is the ACL filter. `..._ALREADY_STARTED`
means the phone has latched and the run is void until `pm clear`.

## 8. What this round cannot settle

- **Whether the reporter's exact failure exists on this rig.** Both phones raise the Bluetooth event
  by accident when a session ends, which is the thing that unit's radio never does. Section 5
  manufactures the state; it does not make the rig into that unit.
- **Whether `filter_profile_connection_by_acl` is what D-POCO is doing.** The flag is not readable
  from either side. The phone-side tokens above are as close as this rig gets.
- **Anything about the proto settings or the serve path.** Round 1 graded them; both proto settings
  stay off throughout, and confirming that once on H2r's first capture is enough.
- **The other half of O4's report.** The reporter said the auto-disconnect row lost its selection
  too, and no code path was found that writes that key except Save. O4's six read-backs can catch it
  happening; they cannot explain it. If every read holds, the honest verdict is that this rig did
  not reproduce that half, not that it does not exist.
