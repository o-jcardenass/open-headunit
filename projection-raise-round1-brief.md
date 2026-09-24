# projection-raise, round 1 brief: a handshake that becomes a picture, and four things beside it

## Revised 2026-09-17: the branch moved and three wake runs were added

If you fetched this brief before 2026-09-17 15:00, re-read **section 1** and the new **section 3,
runs W1 to W3**. Nothing in N1 to N6 changed.

What moved: the hands-free wake audit landed on the same branch, so the SHAs and the gate in
section 1 are different and the setting "Wake the phone even when Bluetooth is connected" **no
longer exists**. It was deleted, not defaulted on: the wake now measures once per unit what it
costs and stands itself down on a unit that does not recover. W1 to W3 grade that. Drop any
`native-aa-wake-over-hands-free-link` line from a precondition file you already wrote; it is
ignored now.

## What this round is

The `hands-free-wake` round 2 results carry an addendum that re-reads its own captures. The short
version: a session that reaches `SSL handshake complete` does not read a single byte until
`AapProjectionActivity` has a surface, and on that round the projection screen often never came up,
so seven sessions sat on a live socket with nothing on it. Four other sessions in the same round
projected perfectly, which is how the cause was found.

This round grades the fix for that and four smaller ones found alongside it. **None of it is
hardware-measured yet.** Every claim below is from source and from round 2's captures.

The wake runs are not repeated here. Round 2 showed that no phone on this rig still reproduces the
negative the escalation exists for: D-POCO answered the escalated wake 2/2, and D-MOTO self-heals
through the ordinary path in 30 to 90 s. Run N6 to settle whether that thread can be closed.

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `59229efa` | 5 on `main` | 2127 tests, 0 failures |
| Baseline | the same branch at `18663348`, only where a run names it | | 5 | 2102 tests |

```bash
git fetch fork
git checkout -B projection-raise-r1 fork/fix/audio-sink-and-wireless-bring-up
./gradlew :app:testGithubDebugUnitTest      # 2127, 0 failures
```

**The branch is a rewrite again**: the two commits that carry this work were amended in place, so
reset any older local copy rather than pulling it. A gate reading 2102 or 2119 is an older tip and
every run below will read as a FAIL against it. What this round grades rides in two of the five
commits: the raise, the pill, the banner and the quiet window in `59229efa` (AAP), the discovery
sweep in `f545c78d` (Misc); the wake runs W1 to W3 ride in `eb3aed89` (Native AA).

One APK for the whole round.

## 2. Preconditions, and the one that broke the last round

Written to `shared_prefs/settings.xml` with the app stopped, per house rule 3:

```xml
<int name="log-level" value="0" />                                    <!-- Verbose, all runs -->
<int name="wifi-connection-mode" value="3" />                         <!-- Native AA; N2 changes it -->
<set name="connection-modes"><string>wifi</string><string>usb</string></set>
```

Three standing points, each of which cost round 2 time:

- **`adb logcat -G 16M` before every capture**, and clear the buffer in the same command as the
  action that triggers what you are capturing. GPS fixes print about once a second and evicted a
  whole handshake from the default buffer last round.
- **The wireless automation actions are receiver-only.** `am start -a` on one fails with
  `unable to resolve Intent`. Use
  `am broadcast -a <ACTION> -p com.andrerinas.headunitrevived`, package flag included or the
  broadcast is dropped at enqueue and still prints `result=0`.
- **N1 denies the permission to draw over other apps and gives it back inside the run.** Do not
  leave the round with it denied; it is not in `settings.xml`, so a settings restore will not undo
  it, and every later session will hang at the handshake.

**Projection is proved by four lines and `TransportStarted` is not one of them** (it is a state
name the app never logs). Throughout this brief, "projected" means all four of:

```bash
grep -a "Service Discovery Response"                 log.txt
grep -a "Channel Open Response"                      log.txt
grep -a "Media Sink Setup Request: . on channel VIDEO" log.txt
grep -a "Throughput over"                            log.txt   # carries rendered=
```

## 3. Runs

### N1. A session the phone starts by itself comes up without the overlay permission

The defect: `ActivityLaunchPolicy` chose its route from the API level and the overlay permission
alone. On API 29+ with the permission off it posted a full-screen-intent notification even when the
app was visibly on screen, and Android 14 demotes that. The fix adds a third input: an app with a
started activity starts the activity directly.

**Unit:** D-HU. **Phone:** D-POCO or D-MOTO, Native AA.

1. `appops set com.andrerinas.headunitrevived SYSTEM_ALERT_WINDOW deny`, and read it back.
2. Launch the app and leave it on the home screen. Let the phone connect on its own, or
   `am broadcast -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE -p com.andrerinas.headunitrevived`.
3. Capture through to a picture or to a 60 s timeout.

**PASS:** the log carries `raising the projection by DIRECT (overlay=false, foreground=true)`,
`AapProjectionActivity.onCreate` within about a second of `Handshake successful`, and all four
projection lines. **FAIL:** `by NOTIFICATION` with the app on screen, or no `onCreate`.

Then, the background half:

4. `input keyevent KEYCODE_HOME`, wait for `HomeFragment` to stop, end the session
   (`headunit://exit`) and let it come back.
5. **Expect** `raising the projection by NOTIFICATION` **and** the new sentence naming the missing
   permission. This arm is allowed to leave the projection down; what it grades is N2 below.
6. `appops set com.andrerinas.headunitrevived SYSTEM_ALERT_WINDOW allow`, read it back, and say so
   in the results.

**Report the `raising the projection by` line verbatim from every capture in this round**, whatever
the run. It is one line and it dates every other verdict.

### N2. A projection that never comes up ends the session instead of hanging

From N1 step 5's state, with the app in the background and the permission still denied, do not tap
the notification.

**PASS:** at about 8 s, `the handshake finished 8000ms ago and the projection screen has not come up`
and a second raise; at about 16 s, `the projection screen never came up, so this session can carry
nothing` and a disconnect. Total under 20 s. **FAIL:** the socket is still open at 60 s, which is the
old behaviour (measured at 6 m 10 s last round).

Take `netstat -tn | grep 5288` at 5 s, 20 s and 60 s. A socket in `CLOSE_WAIT` or `ESTABLISHED` at
60 s is the FAIL.

### N3. Discovery sweeps the network that is carrying traffic

The defect: both the subnet and the local address came from the first interface
`NetworkInterface.getNetworkInterfaces()` listed, which on this unit is an LTE-style stub, so every
sweep last round walked `100.118.187.*` while the phone sat on `wlan0`.

**This run needs a shared WiFi LAN**, which this rig does not have by default. D-MOTO's own hotspot
worked last round and does not isolate clients; the house network does isolate them. Confirm with a
ping from D-HU to the phone before starting, and record the result either way.

1. Both devices on the same network. `wifi-connection-mode=1` (Headunit Server), app restarted.
2. Start the head unit server on the phone (Settings, Android Auto, App info, Additional settings,
   tap Version ten times, overflow, Developer settings, Start head unit server). Confirm it is
   listening: `/proc/net/tcp6` carries `149D`.
3. Press the WiFi button, or
   `am broadcast -a com.andrerinas.openheadunit.ACTION_START_WIRELESS_SCAN -p com.andrerinas.headunitrevived`.

**PASS:** `NetworkDiscovery: Scanning subnet: <the wlan0 /24>.* (from the joined WiFi network)`, the
phone is found, and the session projects. **FAIL:** any other subnet, or the `from the first
interface that is up` source while `wlan0` holds a validated address.

**This is also the run that answers "I could not start a session via Headunit Server."** If it
passes, that route works scripted for the first time on this rig.

### N4. A failed handshake takes the pill down with it

Measured last round: after `session state failed (peer_silent)` the pill stayed on SECURING for
3 m 39 s.

Reproduce the deaf server: from N3's state, kill the phone's Gearhead mid-session
(`am force-stop com.google.android.projection.gearhead` on the phone) and reconnect, or reuse a
server left deaf by an abandoned connection.

**PASS on two things, graded separately:**
- `status pill step: hidden` within a second of `session state failed (peer_silent)`.
- the connection banner naming the head unit server on the **first** silent failure, not the third.
  Screenshot it.

**FAIL:** the pill keeps any step after the failure line, or the banner needs three failures.

### N5. A clean phone-side exit is not a reconnect

Round 2 found the repro and it is reliable: end the session from the phone's own Android Auto UI
(Exit, then Finish later). The head unit gets `RECEIVED BYEBYE REQUEST FROM PHONE ... USER_SELECTION`
and last round put `CREATING_NETWORK` on the pill 119 ms later, with the phone redialling 501 ms
after that.

**PASS:** no pill for about 4 s after the bye-bye. The log says so in its own words:
`status pill step: CREATING_NETWORK (not shown, the phone ended the session)`. The stack is still
running, so `ACTIVELY LISTENING` must still appear, and a phone that comes back must still project
(N1's four lines). **FAIL:** a pill inside the window, or a phone that can no longer get back in.

Run it twice. Then press the WiFi button inside the window and confirm the pill appears immediately:
a bring-up somebody asked for is not what this hold is about.

### N6. Does this rig still have a phone that needs the escalated wake

Not a pass or fail on the candidate. Round 2 could not grade the escalation because both phones
reconnect on their own, and this thread cannot stay open on a negative nothing reproduces.

For each of D-POCO and D-MOTO, three times: establish the precondition from the wake round's
section 5, then watch 4 minutes and record which of these happened first, with its timestamp:
an ordinary reconnect, an escalated wake (`it has not started Android Auto in 90s`), or nothing.

**Report the nine outcomes as a table.** If no phone reaches the escalation, say so plainly; that is
the finding, and the wake thread closes on it.

### W1. The wake gives the hands-free link back, and says what it measured

The setting is gone. The wake now probes: the first escalated wake a unit ever makes is followed by
a **30 s window in which that phone is not poked at all**, then this unit's own hands-free link is
read once and the answer is stored for good. Round 1 of the wake thread measured five minutes of
dead link where one wake's worth was owed, because the ordinary loop re-took the slot nine times
while the link was trying to come back. This grades that.

D-POCO only. D-MOTO reconnects on its own inside 90 s and never reaches the escalation, and D-SAM is
API 19 where the guard cannot run at all.

Set the precondition from the wake round's section 5 (D-HU holding a hands-free link to D-POCO, no
session, the pairing having run Android Auto here before). Then capture for 6 minutes and report:

1. The arming line, verbatim: `waking a phone over a hands-free link it holds is ...`. On a unit
   that has never probed it must read **not yet measured**.
2. The wake itself: `it has not started Android Auto in 90s`, with its timestamp.
3. **Every `NativeAA: Calling socket.connect()` in the 30 s after that wake. There must be none.**
   This is the run's core: `grep -c "Calling socket.connect()"` between the wake and the verdict
   line must be **0**. Round 1 read nine in the equivalent window.
4. The `leaving ... unpoked for another Ns` lines, which is the loop honouring the window.
5. The verdict line, verbatim, one of:
   `the hands-free link came back within 30s of the wake ...` or
   `the hands-free link is still down 30s after the wake ...`.
6. Independently of our log, `dumpsys bluetooth_manager` sampled at the wake, at +30 s, +2 min and
   +5 min, reporting D-POCO's state each time. **This is the measurement that matters most**: it is
   the only reading that is not our own code marking its own homework.

**PASS:** zero pokes inside the window, a verdict line that matches what `dumpsys` says, and the
link either back inside 30 s or honestly recorded as not back.
**FAIL:** any poke inside the window, or a verdict that disagrees with `dumpsys`.

Whether the verdict comes out safe or destructive is **not** a pass condition. Either answer is a
result; the run grades whether the mechanism works, not which way this unit falls.

### W2. The verdict holds across an arming

Straight after W1, without clearing app data, re-arm (toggle the wireless mode off and on, or
restart the app) and watch 4 minutes with the same precondition.

Read the arming line first. It now names what W1 measured.

- If W1 read **destructive**: expect **zero** escalated wakes, and the ordinary
  `Not poking ... already holds a Bluetooth hands-free link` stand-down instead.
  **FAIL:** any `it has not started Android Auto in 90s` line.
- If W1 read **safe**: expect the escalation to run as it did in W1, and the window to be honoured
  again. **FAIL:** no wake after 90 s of stand-down.

Then reboot the unit and read the arming line once more. The verdict is stored, so it must survive.
**FAIL:** it reads "not yet measured" again after a reboot.

### W3. The setting is gone and nothing else moved

1. Open Settings and search for `wake`, then `hands-free`, then `handsfree`. **No row may offer to
   wake the phone over a Bluetooth link.** The neighbouring row "Complete the Bluetooth connection"
   must still be there and still on.
2. Open and leave the settings screen with nothing changed. Per round 2's O3 this must still re-arm
   nothing: zero `ACTION_START_WIRELESS` in the 5 s after the close.
3. A cold first connect, no prior session, phone away: watch 3 minutes and confirm **zero**
   escalated wakes. This is round 1's H4 and it must still hold: a healthy bring-up is never
   disturbed by any of this.

**FAIL:** the row is still present, or a cold connect escalates.

## 4. What this round cannot settle

- **Nothing here measures the owner's own low-end tablet**, which is where the frame-rate report
  came from. A PASS on D-HU says the raise works on Android 14, not that it works on Android 4.
- **N2's deadline is a compromise, not a measurement.** 8 s twice was chosen to sit under the ~35 s
  the phone waited in round 2's Attempt C. If a slow unit ever needs longer to inflate the
  projection activity, this run will not show it; a unit that fails N2 by disconnecting a session
  that was about to come up is a finding, so report the timings even on a PASS.
- **W1 cannot prove the wake is harmless in general, only what it does on D-HU.** The displacement
  itself is not in doubt and is not being tested: the phone's Audio Gateway closes the link it holds
  when it accepts a second connection from the same address, which is the Bluetooth stack working as
  written. What W1 measures is whether **this** unit's own hands-free client re-establishes once it
  is left alone. A different head unit can answer differently, which is exactly why the app now
  stores the answer per unit instead of offering a setting.
- **W1 and W2 are one phone, and that phone has given both answers.** D-POCO FAILed this twice in
  the wake round's round 1 and PASSed it twice in round 2, on the same rig, unexplained. Two more
  runs will not settle which is typical. Report the outcomes; do not average them.
- **N4's banner arm changes behaviour that existed for a reason.** The log explanation still waits
  for three failures. If the banner turns out to appear on transient failures that then succeed,
  say so: the record clears itself on the next good handshake, but a banner that flickers is worse
  than one that waits.
