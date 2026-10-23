# projection-raise, round 3 brief: settle B3, reach the escalation at last, and read the phone while a tablet pokes it

## What this round is

Round 2 graded fourteen things across three head units and settled eight. This round carries the
one FAIL, the four UNTESTABLEs, and one INCONCLUSIVE, and adds three reads on the **phone** that
round 2 did not take and that decide a piece of design work waiting on them.

Round 2's own words are the reason for every change below.

- **B3 FAILed**, and the code that has to notice a rename was changed because of it. Round 2's
  reading of why is not the whole story and the brief says so under B3r.
- **W1r and W2r came back UNTESTABLE for the second round running**, and this time the cause was
  found in our own source: a chosen driver's wake budget ran out about 60 s before the escalation
  it was waiting for could mature. That is fixed on the candidate.
- **L2 is dropped, not retried.** It is structurally unreachable on D-HP and the brief says why once.
- **L3's lever did not exist**: neither `svc wifi disable` nor `settings put global wifi_on 0` takes
  the radio down on Android 4.2.2. It gets a different lever and a different unit.
- **L4 could not reach the Share button**, only the automation export, which does not go through the
  path the fix repairs.

**None of this is hardware-measured.** Every claim below is from source and from round 2's results.

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `c2effbca` | 5 on `main` | 2149 tests, 0 failures |
| Baseline | none this round | | | |

```bash
git fetch fork
git checkout -B projection-raise-r3 fork/fix/audio-sink-and-wireless-bring-up
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testGithubDebugUnitTest   # 2149, 0 failures
```

**The branch was rebuilt, not appended to.** All five SHAs are new and round 2's `bed8ad85` no
longer exists, so reset any local copy rather than pulling it. A gate reading 2133 is round 2's
tip. `JAVA_HOME` is carried here from round 2's own Setup notes, which found the system default is
a JRE with no `javac`.

One APK, installed on whichever unit each stage names.

## 2. The rig, the ports, and the one precondition that has invalidated a run before

Unchanged from round 2: five devices, four USB ports, the head unit under test is always
USB-attached, and a phone may use wireless adb only in Headunit Server mode. Run the stages in
order and say in Setup notes what was plugged in for each.

**Before any stage, on every unit that stage uses, `stat` `shared_prefs/` and report what it read.**

```bash
adb -s $SER shell run-as $PKG stat -c '%U %G %a' shared_prefs
```

If it is not the app's own uid:gid, `chown` it and say so. This is `TESTING-TEMPLATE.md`'s own
standing rule and round 2 did not report it. It is not a formality here: **B3r and W2r both grade a
value the app writes itself**, and a root-owned directory makes `SharedPreferences.apply()` fail
silently, so the in-process value is right and the file never changes. That failure mode reads
exactly like round 2's B3 result, which is why B3r below is written to tell the two apart.

## 3. Preconditions

Round 2's preconditions stand unchanged except where a run says otherwise. Two additions:

```xml
<int name="native-driver-selection-mode" value="1" />   <!-- AUTO, the default; W1r needs it -->
<int name="audio-queue-capacity" value="0" />           <!-- A1 on D-HP only, deliberately wrong -->
```

`audio-queue-capacity=0` is set **only for A1, only on D-HP**, and restored to `50` after it. Round
2 found it carried over from an earlier session and it caused a real defect; here it is the run.

---

## Stage A - D-HU, D-POCO, D-MOTO plugged. D-SAM and D-HP unplugged.

### W1r2. The escalated wake, on a budget that can now reach it

Round 2's W1r established every precondition correctly and still could not make the escalation fire.
The arming line printed verbatim at `16:53:19.069`, D-POCO's HFP link to D-HU held `Connected`
throughout, Gearhead was disabled, and `BluetoothWakePolicy` refused each pass with
`reason=TARGET_CONNECTED`, which is correct. What ran out was the budget: D-POCO was the single
native-poke candidate, so `AUTO` resolved it and routed the wake through the chosen-driver path,
whose three rounds 15 s apart are over in about 30 s when every round is refused, against the
escalation's 90 s. It could not have fired.

**The candidate changes that.** A refused wake opens nothing, so it no longer spends a round; the
loop is bounded by the 120 s exclusivity ceiling instead, which leaves room for exactly one
escalated wake at about 90 s.

Set up exactly as round 2's W1r, including disabling Gearhead on D-POCO for the window
(`pm disable-user --user 0 com.google.android.projection.gearhead`, re-enabled at the end), and
`last-connected-native-mac` naming D-POCO.

Grade:

1. The arming line, verbatim, with its timestamp.
2. **Every pass of the chosen-driver loop, with its timestamp.** Round 2 logged two passes where
   three were due and then 118 s of silence before the UI's own 150 s watchdog gave up. That is
   still unexplained and this run is the capture that explains it. Report how many passes ran, how
   far apart, and what ended the loop.
3. The escalation line (`waking ... despite the hands-free link`) and its timestamp. Expect it
   about 90 s after the first refusal.
4. **Zero** `Calling socket.connect()` in the 30 s after it (`grep -c`), and the
   `leaving ... unpoked for another Ns` lines that hold that window. Round 2 could not reach these.
5. The verdict line, and `dumpsys bluetooth_manager` on D-POCO sampled independently at the wake,
   +30 s, +2 min and +5 min.

If the escalation still does not fire, report items 1, 2 and 5 and how long the stand-down ran. A
loop that now outlasts 90 s and still does not escalate is a different finding from round 2's and
is worth as much.

### W2r2. The verdict holds across an arming

Unchanged from round 2's W2r, straight after W1r2: re-arm, then reboot, and read the arming line and
`native-aa-wake-damage-verdict` in `settings.xml` after each. Round 2 found the key absent because
nothing had ever escalated. **Report the `stat` from section 2 beside this run's result**, because
an absent key means one of two different things and only that read separates them.

### N5r2. The WiFi button inside the quiet window

Round 2 found two things and settled neither. Both carry forward.

- **Use the direct form, not the broadcast.** Round 2 proved
  `am broadcast -a ...ACTION_START_WIRELESS ... --ez no_ui true` drops the extra, because that action
  was a plain relay in `AutomationCommandPolicy`. **That is fixed on this candidate**, so run it
  both ways and report each: the broadcast should now honour `no_ui`, and the direct
  `am start-service -n .../AapService -a ...ACTION_START_WIRELESS --ez no_ui true` should behave as
  it did in round 2. The line either way is
  `AapService: Not raising the projection, a no_ui command asked for the session only`.
- **The input-routing overlap is the real obstacle.** Round 2 measured a tap being forwarded to the
  phone as a video touch event 87 ms **after** `HomeFragment.onResume` had fired, so a resumed
  fragment does not yet own touch input. A second attempt 1.5 s later produced no `Touch map` line
  and also no `connectToNativeDevice`. Rather than more blind timing, take a `uiautomator dump`
  immediately before the tap and report what window owns the coordinates, then tap. If the tile is
  not where the dump says it is, that is the answer.

### A2. The unbounded audio queue, on the unit it was found on

Moved to stage C as A1; see there.

---

## Stage B - D-SAM as head unit, D-POCO. D-HU and D-HP unplugged.

### B3r. The rename counter, and which of two causes round 2 actually hit

Round 2 saw D-SAM produce four different group names across three bring-ups while
`wifi-direct-group-name-changes` never rose past 1, so the `RENAMED` verdict was never reached and
the WPP-over-TCP endpoint was never withheld on a unit that plainly needs it withheld.

**Round 2's stated mechanism does not hold up in source**, so do not assume it. The credential
refresh re-reads a live group rather than re-assessing it, and the per-group guard blocks a second
assessment. Two causes remain and this run separates them:

- **The count never reached disk**, because `shared_prefs/` was root-owned. Section 2's `stat` is
  the whole test for this one.
- **A branch that observed nothing about the name zeroed it.** The candidate fixes that, and adds
  the count to the read-back line so it can be read from the log instead of from `settings.xml`.

Run at least four full bring-ups, relaunching the app between each, and after **every one** capture:

```bash
adb -s $SER shell run-as $PKG cat shared_prefs/settings.xml | grep -o 'wifi-direct-group-name-changes[^/]*'
grep "WifiDirectManager: group identity ssid=" <capture>
```

The read-back line now carries `nameChanges=N/3` below Android 10. Report the SSID and that count
for every bring-up, from the log and from `settings.xml` both, and say where they disagree.

PASS is: by the third changed name the log reads `nameChanges=3/3` and
`stable=no (the platform names it)`, the endpoint is withheld with its own reason, **and the
ordinary Bluetooth WPP path still connects**. If the log counts up and `settings.xml` does not,
that is the ownership cause and it is a PASS for the code and a finding for the rig.

### B2r. Read the phone while the tablet pokes it

Round 2 recorded the **first accept the stand-in hands-free record has ever had**, across five rig
rounds and every reporter log: D-POCO answered `AT+CIND?` with `+CIND: 0,0,0,0,0,5,0` and `OK`
cleanly for about 95 s. It did not go on to open the Android Auto channel or start wireless setup.

Reading that against the source, the repeating `AT+CIND?` is our own keepalive, sent every 2 s once
the exchange completes, so **the service level connection formed**. What is unknown is whether the
phone's own stack recorded it as a hands-free connection, and whether anything downstream of that
was ever going to start. Three reads on **D-POCO**, taken while D-SAM is poking, answer it. This run
is cheap and it decides a piece of design work waiting on it.

1. **The AG-side state machine.** `adb -s <D-POCO> shell dumpsys bluetooth_manager`, and report the
   `Profile: HeadsetService` block, specifically
   `==== StateMachine for <D-SAM's MAC> ====` with `mCurrentState` and its transition log. Connected
   or only Connecting is the whole question. `HeadsetClientService` is not instantiated on a phone,
   so the AG-side machine is the only read there is.
2. **Gearhead's own verdict**, from D-POCO's logcat across the same window. Report which of these
   appears, verbatim, with timestamps:
   `WIRELESS_SETUP_SHARED_HFP_CONNECTING`, `WIRELESS_SETUP_SHARED_HFP_CONNECTED`,
   `WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE`,
   `Wireless projection experiment disabled`,
   `No WPP on TCP configuration found in storage for the head unit`,
   `WiFi Projection Protocol cannot start as HU is not present.`
3. **Whether D-SAM is a car the phone knows at all.** Report whether D-SAM's Bluetooth MAC appears
   in Android Auto's stored cars. If it does not, say so plainly: that is a likelier explanation
   than anything about the profile, and it costs the round nothing to rule in or out.

Not pass/fail. The report is the result.

### B1r and B4r

Both PASSed in round 2 and are not repeated. If either regresses incidentally during B3r or B2r,
report it.

---

## Stage C - D-HP as head unit, D-POCO. Headunit Server only.

### A1. The unbounded audio queue, reported as a delay

Round 2 found `audio-queue-capacity=0` carried over on D-HP and it caused a real, user-audible
defect: audio delay growing to an estimated five minutes over one session, with `AudioTrackWrapper`'s
unbounded queue climbing 1060, 1409, 1381, 1385, 1920 while the ring buffer's own `depth` stayed
healthy and even reset to 0 through a rebank. The backlog was never in the buffer the telemetry
watched. Resetting to `50` dropped `queued` to single digits immediately.

**The candidate now reports that queue as a duration and says once when an unbounded one falls
behind.** Grade it on the unit that found it.

With `audio-queue-capacity=0` set on D-HP, run a live Headunit Server session for at least ten
minutes with audio playing, and report:

- The `audio sink` lines across the session, specifically `queued=N (Xms)`. The duration must appear
  and must grow with the count.
- The one-shot warning, verbatim, and its timestamp:
  `Audio queue capacity is 0, so nothing sheds and this sink is Nms behind on N queued chunks.`
- Whether an operator listening can hear the offset the line claims, and roughly when.

Then set `audio-queue-capacity=50`, restart, and confirm `queued` stays in single digits and the
warning does not appear. Leave it at `50`.

PASS is: the delay is readable from the log alone, without anyone computing it from a chunk count.

### L3r. WiFi comes back and discovery does not wait it out

Round 2 could not create the lever: both `svc wifi disable` and `settings put global wifi_on 0`
left `dumpsys wifi` reporting `"Wi-Fi is enabled"` on Android 4.2.2, so the disconnect this run
needs never happened.

**Run it on D-SAM instead** (API 19, in stage B, or plugged for this run alone). `WifiJoinKickPolicy`'s
`NETWORK_STATE_CHANGED_ACTION` arm covers everything below API 21, so D-SAM reaches the same code
D-HP would have. Try the same two commands there first and report whether either works; if neither
does, the remaining lever is to take the access point down, or move the unit out of range, and the
brief accepts that as a manual step.

With a discovery sweep waiting, drop WiFi, bring it back, and grade that the sweep restarts in about
500 ms rather than waiting out the 10 s to 5 min timer. If no lever on either tablet takes the radio
down, report that as the result and this run is closed as unreachable rather than carried a third time.

### L4r. The Share button below Android 10

Round 2 exercised `ACTION_EXPORT_LOG`, which completed cleanly and confirmed the export path is the
modern `/storage/emulated/0`, not the legacy `/storage/sdcard0`. That is useful and is recorded. It
is **not** the run: the crash this branch fixes is in the Share **button**, which goes through
`FileProvider` and a path `provider_paths.xml` did not declare, and the automation export does not
touch it.

Reaching Settings on D-HP is the obstacle. Round 2 found `run-as $PKG am start --user 0 -n
.../SettingsActivity` segfaults that unit's `am` binary every time, and scripted taps did not
register with either coordinate mapping. Two things that may help:

- **`input tap` on both tablets takes the UIAutomator logical (rotated) coordinate space**, not the
  `screencap` buffer's physical space. The physical buffer comes back portrait 800x1280 while the
  app forces landscape content into it. Tap the `uiautomator dump` bounds, not the screenshot pixel.
- Try `am start` without `run-as` and without `--user 0`, and try `monkey -p $PKG 1` to get the app
  to the foreground first.

**This one run may be done by hand**, by exception, and the brief says so: it is a crash-or-not on a
single button press. Tap Share, report whether the app survives, and attach the exception if not.
If Settings still cannot be reached at all, report that and the run closes as unreachable.

### L1r, L2 and L5r

L1 and L5 PASSed in round 2 and are not repeated.

**L2 is dropped and will not be asked for again.** The stand-in hands-free record lives entirely
inside `NativeAaHandshakeManager`, which is the Native AA and WiFi Direct path; D-HP has no WiFi
Direct and no hotspot and can run only Headunit Server, so the branch is unreachable there whatever
`native-aa-complete-hfp-slc` is set to. Round 2 established this and it is now in
`TESTING-TEMPLATE.md`. The class of device the setting was written for is covered by D-SAM in B2r.

Round 2's two other stage C observations are recorded and are **not** runs: D-HP's recurring decoder
stalls under sustained H.264 load, which the existing stall-recovery and view-mode-fallback logic
handled correctly both times, and the one USB link drop that recovered on a replug with the app
process and session intact.

---

## 4. What this round cannot settle

- **A real deaf head unit server.** N4r PASSed against a bare TCP listener, which is the right test
  of our detection and the pill. The real-world trigger is a phone-side state this rig cannot
  manufacture, and round 2's own reading of why is correct. Not carried.
- **Whether the escalated wake achieves a connect.** W1r2 runs with Gearhead disabled, so the phone
  cannot dial back by design. `hands-free-wake` round 2's C1 already PASSed that 2/2.
- **Whether a held stand-in hands-free link would start wireless setup on a tablet.** B2r reads the
  phone; it does not change what the tablet does. Any change there is a separate branch and its own
  round.
