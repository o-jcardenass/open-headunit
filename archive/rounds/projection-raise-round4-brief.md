# projection-raise, round 4 brief: finish the rename counter, grade the audio queue in the right units, and one hand on D-HP

## What this round is

Round 3 settled the two runs that had been UNTESTABLE for two rounds running, and it is the first
round in this thread where the escalated wake fired on hardware at all. What is left is small: one
FAIL carried, two audio runs where round 3's own measurement turned out to be in the wrong units,
one run that needs a human finger for ten seconds, and one repeat on a second phone.

Round 3's own words are the reason for every change below.

- **B3r FAILed again**, and its root cause is right: two more branches of the same policy were
  zeroing the count. Round 3's reading of the *consequence* is not right and B3r2 says so, so the
  run is not graded as a brick risk that it never was.
- **A1 PASSed on a number that is wrong on an AAC sink.** The queue was measured in encoded bytes
  divided by the width of a PCM frame, so every duration on that line was scaled by the compression
  ratio. Fixed on the candidate. A1r grades the corrected figure and **records the codec**.
- **A2 is A1's one-shot warning**, which round 3 lost to a host-side capture that died, not to the
  app. It is a separate run this time with a capture that cannot miss it.
- **L4r could not reach D-HP's Settings screen by any scripted route.** Four were tried and all are
  closed. L4r2 asks for two physical taps and nothing else.
- **W3r is new.** The wake verdict is a per-unit measurement and it has been taken on one unit.

**None of this is hardware-measured.** Every claim below is from source and from round 3's results.

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `952a0ffc` | 5 on `main` | 2153 tests, 0 failures |
| Baseline | none this round | | | |

```bash
git fetch fork
git checkout -B projection-raise-r4 fork/fix/audio-sink-and-wireless-bring-up
./gradlew :app:testGithubDebugUnitTest   # 2153, 0 failures
```

**The branch was rebuilt again, not appended to.** All five SHAs are new and round 3's `c2effbca` no
longer exists, so reset any local copy rather than pulling it. A gate reading 2149 is round 3's tip.
Round 3 found this host's default `java` is a full JDK, so `JAVA_HOME` is no longer prescribed here;
set it only if the gate complains.

One APK, installed on whichever unit each stage names.

## 2. The rig and the preconditions

Unchanged from round 3. Five devices, four USB ports, the head unit under test is always
USB-attached. Round 3 left all five plugged in throughout and nothing below depends on isolation
between stages, so do whichever is easier and say which in Setup notes.

Round 3 checked `shared_prefs/` ownership on all three head units and found every one correctly
app-owned, so the hazard that made round 2's B3 ambiguous is not a standing state on this rig. Keep
checking it before any run that grades a value the app writes itself, which here is B3r2 only:

```bash
adb -s $SER shell run-as $PKG stat -c '%U %G %a' shared_prefs
```

Three per-unit values round 3 had to discover the hard way, now preconditions:

```xml
<int name="native-aa-wake-damage-verdict" value="0" />  <!-- clear before W3r, and after it -->
<int name="wifi-direct-group-name-changes" value="0" /> <!-- D-SAM, before B3r2's first bring-up -->
<int name="audio-queue-capacity" value="0" />           <!-- A1r and A2 only, restored after -->
```

`native-aa-wake-damage-verdict` latches by design once a wake has measured the unit, and a latched
verdict stops every later poke. Round 3 hit that and had to clear it mid-round to continue. Clear it
before W3r and again before anything after W3r that needs a real session.

---

## Stage A - D-HU and D-MOTO, with D-POCO present

### W3r. The wake verdict, on a second unit

**Why.** W1r2 measured the escalated wake end to end for the first time, on D-HU: armed at
`21:37:14`, five refusal passes 15 s apart that each cost zero budget, escalation at `21:38:46` and
**91.4 s** after arming, HSP-AG poke accepted, zero pokes in the 30 s give-back, and the verdict
written at `21:39:16` exactly 30.001 s later. W2r2 then proved the verdict survives a re-arm and a
real reboot, verbatim.

That is one unit. The verdict `NativeAaWakeDamagePolicy` holds is a claim about *this* unit's
Bluetooth stack, and the rig has a second phone that has never been the wake target.

**Run.** Repeat W1r2 exactly, with **D-MOTO** as the phone instead of D-POCO. Gearhead disabled on
D-MOTO for the run, `last-connected-native-mac` naming it, `native-aa-wake-damage-verdict` cleared
first, and the HFP link to D-HU confirmed `Connected` in `dumpsys bluetooth_manager` before arming.
Round 3's own method for building that precondition works and is worth reusing: set
`auto-start-bt-macs` to the phone's MAC to make the loop probe it, bounce D-HU's own Bluetooth radio
with `svc bluetooth disable` (it self-reverts in about 14 s on this unit), let the phone's stack
reconnect its own profile, then clear `auto-start-bt-macs` before the graded capture.

Report the same six things W1r2 reported: the arming line, every refusal pass with its timestamp,
the escalation line and its offset from arming, the poke result, the count of
`Calling socket.connect()` inside the give-back window, and the verdict line. Take independent
`dumpsys bluetooth_manager` samples at +30 s, +2 min and +5 min.

PASS is: the escalation fires at about 90 s, refusals cost no budget, the give-back is honoured, and
a verdict is written. **The verdict itself is a measurement, not a grade.** If D-MOTO's link comes
back on its own the line will read "measured safe on this unit" and that is a PASS just as much as
D-HU's "it will not be used again" was. Do not read either wording as a failure.

Clear `native-aa-wake-damage-verdict` again afterwards.

---

## Stage B - D-SAM as head unit, D-POCO

### B3r2. The rename counter, third time, and what it actually costs

**What round 3 found, and it is right.** `GroupIdentityStabilityPolicy.assess()`'s `STABLE` and
`CHANGED` branches were building their verdict without carrying `nameChanges`, so it fell to the
data class default of `0` and `WifiDirectManager` persisted that zero. D-SAM assesses a bring-up
twice, the first callback re-reading the group the previous run left up, and that repeat is what
wrote the zero. The measured `1/3` four bring-ups running is exactly that oscillation.

**What round 3 got wrong, and it matters for how this run is read.** Both round 2 and round 3 say
the consequence is that the WPP-over-TCP endpoint "was never withheld on a unit that plainly needs it
withheld". It was withheld, on every bring-up. `WppEndpointPolicy.decide` withholds on **any**
identity that is not `STABLE`, and D-SAM's verdict was `UNPROVEN` throughout, so no endpoint ever
went out and no phone was ever at risk. What the bug costs is the reason the unit is given: it is
told "the next bring-up decides" forever, on a unit where no bring-up ever will. This run grades a
diagnosability fix. Nothing here can brick a phone and nothing here ever could.

**The fix on the candidate.** Below API 29 the count is no longer reset by a name that repeated, and
only there: on Android 10 and above, where the app asks for the name and gets it, a repeat still
clears the count because that is the app's own lever working. Replayed against round 3's own table
this reaches the measured verdict on the third bring-up's settled callback.

**Run.** Reset `wifi-direct-group-name-changes` to `0` with the app stopped, confirm `shared_prefs`
ownership, then four full bring-ups on D-SAM with a force-stop and relaunch between each, exactly as
round 3 ran them. After every bring-up capture both:

- every `WifiDirectManager: group identity ssid=` read-back line, including the ephemeral one, with
  its `nameChanges=N/3` and its `stable=` label;
- `wifi-direct-group-name-changes` out of `settings.xml`.

Round 3's table is the format to reproduce, one row per callback rather than per bring-up.

PASS is: by the third bring-up's settled callback the count reads `3/3`, the label reads
`stable=no (the platform names it)`, and the reason names the platform rather than promising that
the next bring-up decides. The ordinary Bluetooth WPP path must still connect throughout; that is
the path this unit is meant to use and withholding the endpoint is the correct outcome, not a
degradation.

### B2r2

Not run. Round 3's B2r answered it completely and nothing further is owed. Its result is recorded in
the app's own orientation file: a tablet with no hands-free profile of its own **does** win a real
hands-free pair, confirmed from the phone's side twice over, and the gate that then refuses is a
missing wireless-projection configuration entry rather than anything about Bluetooth.

### B1r and B4r

Not repeated; both PASSed in round 2.

---

## Stage C - D-HP as head unit, D-POCO, Headunit Server only

### A1r. The audio queue, in the right units this time

**Why it is being run again after a PASS.** A1's PASS condition was that the queue's backlog is
readable as a delay from the log line alone, and it is. But the arithmetic behind that delay was
wrong on an AAC sink, and round 3 did not record which codec the session used.

`AudioTrackWrapper` queues the bytes it receives and derived a chunk's playing time by dividing them
by the width of one PCM frame. On the PCM path that is correct. On the AAC path the queue holds
**encoded** AAC, so the division produced a frame count that is too small by the compression ratio,
and three things were scaled by it: the `(Xms)` figure on the sink line, the queue's own capacity
floor, and the cushion the sink banks before it plays. Round 3's own numbers show it: the plateau at
`142-143 (994-1001ms)` puts a chunk at 7 ms, and a 7 ms chunk on a 48 kHz sink is not something a
phone sends.

The candidate now measures an AAC chunk at the decoder, in real PCM frames, and leaves the PCM path
untouched.

**Run.** As A1, on D-HP, and with two additions.

- **Record the codec.** `grep "Media Sink Setup Request" ` and the `audio-force-aac` setting as it
  reads on the unit, in Setup notes, before the session. Whether this sink is AAC decides whether
  the fix is what is being graded at all. If the session is PCM, say so and the run grades the line
  format only.
- The sink line now carries the ceiling in force as well as the depth:
  `queued=N/capacity (Xms)`, and `queued=N/unbounded (Xms)` when the capacity is 0.

With `audio-queue-capacity=0`, a live Headunit Server session with audio playing for at least ten
minutes. Report the `audio sink` lines across it, and whether an operator listening can hear the
offset the line claims and roughly when.

Then `audio-queue-capacity=50`, restart, and report the same lines for several minutes.

**Round 3's brief was wrong about what that second half should show and this one corrects it.** It
said the queue settles to single digits. It does not and never did: the capacity floor raises a
setting of 50 to whatever holds one second of audio, which on a short chunk is well over a hundred
chunks. What `queued=N/capacity` now makes visible is whether the queue is *at* that ceiling. Round
3 measured it sitting there continuously and shedding, which is the sink genuinely unable to keep up
on that unit. That is a real limit of D-HP and not a defect of the setting; report what it does now
that the chunk size is measured properly, without an expectation either way.

PASS is: the duration on the line is consistent with the codec in use, and the ceiling is printed
beside the depth so a queue at its ceiling reads as one.

### A2. The one-shot unbounded-queue warning

Round 3 could not confirm this line. The condition for it to fire was met several times over, but
the host-side `logcat` process died silently twice during the run and the device's own ring buffer
had rotated past it by the time anyone looked. That is a capture failure, not an app one, and the
line is cheap to catch on its own.

**Run.** Any sink on any unit, `audio-queue-capacity=0`, with the capture armed **before** the
session starts and written straight to a file:

```bash
adb -s $SER logcat -c
adb -s $SER logcat -v threadtime > round4-a2.txt &
```

Then a session with audio until `queued=` passes a couple of seconds' worth. Report the line
verbatim with its timestamp:

`Audio queue capacity is 0, so nothing sheds and this sink is Nms behind on N queued chunks.`

PASS is: it appears exactly once, and the figure in it agrees with the `queued=` line beside it.
Check the capture process is still alive at the end (`jobs`, and the pid) and say so.

### L4r2. The Share button below Android 10, by hand

**Every scripted route is now closed and this brief will not ask for another.** Round 3 tried four:
`run-as $PKG am start` segfaults D-HP's `am` binary with and without `--user 0`; plain `am start` is
refused because the activity is not exported; `monkey` plus `input tap` at the bounds a
`uiautomator dump` gives for the Settings button produces no transition; and neither does the same
mirrored for the `rotation="1"` against `mRotation=3` disagreement the dump shows. Touch delivery
itself is proven working on that unit and in that coordinate space, because the identical method on
the neighbouring WiFi button fires `MainActivity.beginAutoConnect`.

What is already settled without a run: the share path is wrapped end to end, so a failure there logs
and toasts instead of killing the app, and the file provider declares the external storage root that
covers D-HP's own export path of `/storage/emulated/0`. What no round has ever done is press the
button.

**Run.** Two physical taps on D-HP, with a logcat capture running: **Settings**, then the log
**Share** button. Nothing else. Report:

- whether a chooser appeared, and what was in it;
- whether the app was still running afterwards;
- `grep "LogExporter: could not share"` over the capture, which is a failure-only line.

PASS is: a chooser, the app alive, and no `could not share` line. If no hand is available, report
that and the run closes; it will not be carried a third time.

### L1r, L2, L3r and L5r

None are run. L1 and L5 PASSed in round 2. L2 stays dropped as structurally unreachable on D-HP.
**L3 is closed.** Round 3 confirmed on D-SAM that `svc wifi disable` does not take the radio down on
API 19 either, and that the `"try again in 1second"` loop plus the multi-megabyte `dumpstate` spill
happens when `disable` is issued alone rather than only when chained with `enable`. Both tablets are
out of levers and the below-Lollipop WiFi rejoin stays unmeasured.

---

## 3. What this round cannot settle

- **The below-Lollipop WiFi rejoin.** Closed above. It needs a unit whose radio can be taken down by
  something the rig can drive, and neither tablet is one.
- **Whether the escalated wake achieves a connect.** W3r runs with Gearhead disabled on the target,
  so the phone cannot dial back by design.
- **Whether a tablet can be provisioned as a wireless car.** Round 3 named the gate precisely and the
  only route to it is a one-tap QR flow that coheres with a hotspot rather than with a WiFi Direct
  group that is renamed on every create. That is design work, not a run.

## 4. Anything round 3 raised that needs no run

- D-HU pokes every bonded phone whatever `auto-start-bt-macs` holds. That is correct and expected:
  the wake list is `native-poke-bt-macs`, `auto-start-bt-macs` gates only which device auto-launches
  the app on a Bluetooth connect, and an empty wake list plus `native-poke-all-paired-devices`,
  which defaults to on, is what walks every paired device. The two lists were deliberately split. It
  is now in `TESTING-TEMPLATE.md`.
- D-SAM's `wifi-direct-group-name-changes` reading `4` before the round touched it, and D-SAM's clock
  offset being gone, are both in `TESTING-TEMPLATE.md` now.
