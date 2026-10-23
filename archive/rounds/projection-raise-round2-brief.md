# projection-raise, round 2 brief: reach what round 1 could not, and two tablets that have not run in months

## What this round is

Round 1 graded nine things and settled six. This round carries the three it could not reach, adds
one fix it found on its own, and opens a second front: **D-SAM and D-HP, the two old tablets**, which
between them cover an API range nothing on this branch has ever run on.

Round 1's own words are the reason for each change. W1 and W2 came back UNTESTABLE because D-POCO
answered the ordinary way in 0.4 to 4 s on 6 of 6 arms, so the 90 s stand-down never matured and
nothing escalated. N4 came back INCONCLUSIVE after three attempts to wedge a real head unit server,
and the analysis in the results is right: the app's graceful paths exist to prevent that state, so
nothing that goes through them can reproduce it. N5's WiFi-button sub-check could not reach the
button. **Each of those gets a lever this time rather than a repeat.**

**None of this is hardware-measured.** Every claim below is from source and from round 1's results.

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `bed8ad85` | 5 on `main` | 2133 tests, 0 failures |
| Baseline | none this round | | | |

```bash
git fetch fork
git checkout -B projection-raise-r2 fork/fix/audio-sink-and-wireless-bring-up
./gradlew :app:testGithubDebugUnitTest      # 2133, 0 failures
```

**The tip was amended again**, so reset any local copy rather than pulling it. A gate reading 2127
is round 1's tip: E1 below is the only run that grades anything new in the code, but report the gate
anyway. Everything else grades code round 1 already carried.

One APK, installed on whichever unit each stage names.

## 2. The rig has five devices and the PC has four USB ports

This round cannot be run with everything plugged in, and projection takes the head unit onto a P2P
group or a shared LAN, so **wireless adb is not available for the unit under test**. Three
consequences, and they set the order:

- **The head unit under test is always USB-attached.** Stage A uses D-HU, stage B D-SAM, stage C D-HP.
- **A phone may use wireless adb only in Headunit Server mode**, where it stays on the house LAN.
  In Native AA it joins the P2P group and is unreachable, so it needs the cable.
- **Run the stages in order and unplug between them.** Stage A needs three devices, B and C two
  each. Nothing in B or C depends on A having passed.

Say in Setup notes what was plugged in for each stage. If a stage had to run with a different set,
that is a finding, not an error.

## 3. Preconditions

Written to `shared_prefs/settings.xml` with the app stopped, per house rule 3:

```xml
<int name="log-level" value="0" />                                    <!-- Verbose, every run -->
<set name="connection-modes"><string>wifi</string><string>usb</string></set>
```

Per stage: `wifi-connection-mode` is `3` (Native AA) for stage A, both `3` and `1` for stage B, and
`1` (Headunit Server) for stage C, which is the only mode D-HP can run.

**Check `shared_prefs/` ownership before stage A, and fix it if it is wrong.** W2r depends on a
setting the *app itself* writes, and on D-HU that directory has been owned `root:root` while the app
runs as its own uid, so `apply()` updates the in-memory copy and never reaches disk. It is silent in
both directions: an in-process read passes, a read of `settings.xml` after a force-stop fails, and
neither is the truth.

```bash
adb -s 27870808938846 shell su -c 'stat -c "%U:%G %a" /data/data/com.andrerinas.headunitrevived/shared_prefs'
# not <app-uid>:<app-gid>?  fix it, and say in Setup notes what it read first:
adb -s 27870808938846 shell su -c 'chown -R $(stat -c %u /data/data/com.andrerinas.headunitrevived):$(stat -c %g /data/data/com.andrerinas.headunitrevived) /data/data/com.andrerinas.headunitrevived/shared_prefs'
```

**If it could not be fixed, W2r is INCONCLUSIVE, not FAIL**, and W1r still runs: W1r's verdict is
read from the log, not from disk.

Three standing points carried from round 1, each of which cost it time:

- **`adb logcat -G 16M` before every capture**, clearing the buffer in the same command as the action.
- **`ACTION_NATIVE_AA_POKE` needs `--es extra_mac <MAC>`.** A bare broadcast answers
  `{"error":"extra_mac is required","ok":false}`. This cost round 1 its first attempt at N1 and is
  not written down anywhere else.
- **A run that denies `SYSTEM_ALERT_WINDOW` gives it back inside the same run.**

**Projection is proved by four lines and `TransportStarted` is not one of them:**

```bash
grep -a "Service Discovery Response"                   log.txt
grep -a "Channel Open Response"                        log.txt
grep -a "Media Sink Setup Request: . on channel VIDEO" log.txt
grep -a "Throughput over"                              log.txt   # carries rendered=
```

---

## Stage A - D-HU, D-POCO, D-MOTO plugged. D-SAM and D-HP unplugged.

### W1r. The probe, with something to keep the phone away

Round 1 set the precondition correctly every time and the phone simply answered. The escalation only
fires after **90 s** of a stand-down, and D-POCO reconnected in 0.4 to 4 s on all six arms, so
nothing the run did could have reached it. The missing piece is a phone that holds its hands-free
link and cannot dial back.

**Disable Android Auto on D-POCO for the duration of the run.**

```bash
adb -s 4f4027e9 shell pm disable-user --user 0 com.google.android.projection.gearhead
# ... run ...
adb -s 4f4027e9 shell pm enable com.google.android.projection.gearhead      # at the end, always
```

The hands-free link belongs to the phone's Bluetooth stack, not to Android Auto, so it stays up
while Gearhead is down. That is exactly the shape round 1 of the wake thread measured naturally: a
phone holding the link, raising no Bluetooth event, never coming back.

**This is legitimate for what W1r measures and not for what it does not.** The verdict is "does this
unit's own hands-free client re-establish its link after the poke displaced it", which is a
Bluetooth-stack question and is unaffected by which apps are enabled on the phone. What this run
**cannot** measure is whether the escalated wake *achieves* a connection, because with Gearhead
disabled nothing can answer. That half already has a result: C1 in the wake round's round 2 PASSed
2/2. Do not report W1r as evidence either way about it.

**Two preconditions that are easy to miss**, both from `HandsFreeWakeEscalationPolicy`:

1. **D-POCO must be the phone that last completed a Native AA handshake.** The escalation refuses a
   pairing that has not run Android Auto on this unit, and after a process restart the durable half
   of that check is `last-connected-native-mac`. Round 1 interleaved D-MOTO runs, which would move
   it. Run one ordinary D-POCO session to completion first, `headunit://exit`, then read the key back.
2. **No session in flight, and the stand-down must be continuous.** Confirm
   `mCurrentState: Connected` for D-HU's MAC in D-POCO's own `HeadsetStateMachine` before arming,
   exactly as round 1's N6 did.

Then arm (`MainActivity`), capture **6 minutes**, and report:

1. The arming line, verbatim: `waking a phone over a hands-free link it holds is ...`. On a unit that
   has never probed it reads **not yet measured on this unit**. Round 1 saw this every time.
2. The wake: `it has not started Android Auto in 90s`, with its timestamp.
3. **Every `NativeAA: Calling socket.connect()` in the 30 s after that wake. There must be none.**
   `grep -c` between the wake and the verdict must read **0**. Round 1 of the wake thread read nine
   in the equivalent window, and that is the bug this window exists to fix.
4. The `leaving ... unpoked for another Ns` lines, which is the loop honouring the window.
5. The verdict line, verbatim, one of `the hands-free link came back within 30s of the wake ...` or
   `the hands-free link is still down 30s after the wake ...`.
6. **Independently of our log**, `dumpsys bluetooth_manager` on D-HU sampled at the wake, +30 s,
   +2 min and +5 min, reporting D-POCO's state each time. This is the reading that matters most: it
   is the only one that is not our own code marking its own homework.

**PASS:** zero pokes inside the window, and a verdict line that matches what `dumpsys` says.
**FAIL:** any poke inside the window, or a verdict that disagrees with `dumpsys`.

Which way the verdict falls is **not** a pass condition. Safe and destructive are both results.

**If the escalation still does not fire with Gearhead disabled, that is a finding and the run is
UNTESTABLE again** - report the arming line, how long the stand-down ran, and what
`BluetoothWakePolicy` said each pass, because then something other than the phone answering is
holding it down.

### W2r. The verdict holds across an arming

Straight after W1r, without clearing app data. Re-arm (toggle the wireless mode off and on, or
restart the service), then reboot D-HU and arm once more.

Read the arming line first: it now names what W1r measured.

- **destructive**: zero escalated wakes, and the ordinary `Not poking` stand-down instead.
- **safe**: the escalation runs as it did in W1r, and the window is honoured again.

The reboot arm is what grades persistence, and it is the one the `shared_prefs` check above protects.
Report `native-aa-wake-damage-verdict` from `settings.xml` after the reboot as well as the log line.

### N4r. A failed handshake takes the pill down with it, with a peer that is genuinely deaf

Round 1 could not manufacture a deaf head unit server and the reason is structural. **Do not try
again.** What N4 actually asks is whether the pill comes down with a handshake that fails because
the peer accepts and never speaks. A bare TCP listener is exactly that shape, and it is deterministic.

On the **host PC**, on the same LAN as D-HU, with no real head unit server running:

```bash
python3 -c "
import socket
s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(('0.0.0.0', 5277)); s.listen(8)
print('listening, accepting and never answering')
held = []
while True:
    c, a = s.accept(); print('accepted', a); held.append(c)
"
```

D-HU in Headunit Server mode (`wifi-connection-mode=1`), both on the house LAN. Round 1's N3 showed
discovery finds a server by sweeping the subnet, so a plain listener is found the same way.

Report the `Scanning subnet`, the `Found Headunit Server on <host>:5277` line, the session state the
app reaches, and what the pill does. **PASS:** the app reaches a `peer_silent` style failure and the
pill comes down with it rather than sitting at a stage that no longer describes anything.
**FAIL:** the pill stays up, or the app hangs with no verdict.

**This grades our detection and the pill, not the real-world trigger.** A real deaf server is a
phone-side state we cannot create; say so in the results and do not let a PASS here retire the
`HEADUNIT_SERVER_NOT_ANSWERING` question.

### N5r. The WiFi button inside the quiet window

Round 1 found the mechanism and could not reach it: the real button needs `HomeFragment` on screen
and the DIRECT raise reclaims the foreground in under a second after "Finish later".

Suppress the raise for this run so the home screen stays up. The automation surface has
`EXTRA_NO_UI`, which asks for the session without the screen:

```bash
adb -s 27870808938846 shell am broadcast -a com.andrerinas.openheadunit.ACTION_START_WIRELESS \
  -p com.andrerinas.headunitrevived --ez no_ui true
```

Then end the session from the phone ("Exit" inside the projection, then "Finish later", which round 1
established as the clean `ByeByeRequest` lever), and with `HomeFragment` on screen **tap the WiFi
button inside the ~4.5 s quiet window** round 1 measured.

Report what the pill does and whether `phoneLeftQuiet` suppression was bypassed. Round 1's reading of
the code is that `connectToNativeDevice()` bypasses it unconditionally; this run says whether that is
what a user sees. **Either answer is a result**; if the bypass is real, say whether it is wanted.

### E1. An exit gives the network back before the service stops

**This is the only run grading new code.** Round 1 found, outside its brief, that
`ACTION_STOP_SERVICE` called `stopSelf()` without waiting: the teardown that removes the P2P group
runs on a scope `onDestroy` cancels, and suspends on the disconnect before it gets there. Their one
direct test won the race. The candidate now holds the service open until the teardown returns,
bounded at 5 s.

**Ten cycles.** Each one: a live Native AA session on D-HU with D-POCO, then

```bash
adb -s 27870808938846 shell am start -a android.intent.action.VIEW -d 'headunit://exit'
sleep 3
adb -s 27870808938846 shell dumpsys wifip2p | grep -i groupFormed
```

**PASS:** `groupFormed: false` on all ten, and no
`the wireless teardown did not finish in 5000ms` line anywhere. **FAIL:** any cycle leaving a group
formed. Report the count either way, and report any timeout line with what preceded it: a teardown
that needs longer than 5 s is a different finding from one that is cancelled.

Also report `CommManager teardown complete. Stopping WiFi Direct group.` per cycle. Ten sessions,
ten of those, is the clean shape.

### D1. Driver selection against a phone that is already coming back

Round 1 observed this and did not grade it. With D-MOTO holding the hands-free link and the P2P group
and no AAP session running, an explicit poke to D-POCO won the race and took the group; D-MOTO's own
reconnect lost, and its hands-free link was untouched throughout.

Reproduce it deliberately, three times, and report: which phone gets a `192.168.49.x` address, which
completes the AAP session, whether the group was recreated (a different BSSID), and whether D-MOTO's
`state=Connected` holds. **Not pass/fail.** The question the results should answer is whether an
explicit poke *should* win: the user named a phone, which argues yes, and a driver mid-reconnect
losing its session argues no. Say which, and why.

---

## Stage B - D-SAM as head unit, one phone, D-HU and D-HP unplugged

D-SAM is a Samsung SM-T230 on **Android 4.4.2, API 19**. Read its section in `TESTING-TEMPLATE.md`
§7a before starting: no root and no `sed`/`awk`/`busybox`, `am` rejects `-p` on 4.4.2 so drop the
package flag everywhere, `SettingsActivity` needs `run-as $PKG am start --user 0 -n ...`, never chain
`svc wifi disable` and `enable` in one call, and it cannot host an access point at all. Build and
install with `install_and_launch.sh` and `HU=30041c35642d2200`.

### B1. Headunit Server mode, end to end

`wifi-connection-mode=1`, D-SAM and the phone on the house LAN. Report discovery, the dial, SSL, all
four projection lines and the `Throughput over` figures. **PASS:** a picture. **FAIL:** anything
short of it, with the last line reached.

### B2. The stand-in hands-free record, on the class of device it was written for

`native-aa-complete-hfp-slc` exists for units that cannot make a hands-free profile of their own. A
unit whose own Bluetooth stack already advertises Hands-Free never gets a stand-in record registered
at all, so the setting is inert there whatever it is set to. **D-SAM is a tablet and is the class the
setting is for. It has never been measured on one.** The app's own KDoc records that the stand-in has
never once been accepted across five rig rounds and every reporter log.

Set `native-aa-complete-hfp-slc=true`, `wifi-connection-mode=3`, and report which of these three the
log carries:

```bash
grep -a "gets the stand-in HFP record, because"          log.txt   # registered, with the reason
grep -a "already advertises Hands-Free, so the stand-in" log.txt   # inert on this unit
grep -a "a real hands-free link is up, so the stand-in"  log.txt   # published but stood down
```

Then whether the phone ever attaches to it, and whether it goes on to start wireless setup.
**Not pass/fail**: report which branch this unit takes and whether the accept fires. An accept would
be the first ever recorded.

### B3. WiFi Direct with no way to name a group

There is no API that names a P2P group before Android 10, so `wifi-direct-stable-identity` has no
code path on this unit and the group's name is random every session. The app is supposed to notice
that and say so rather than pretend: `GroupIdentityStabilityPolicy` should reach `RENAMED` by the
third create, and `WppEndpointPolicy` should then withhold the WPP-over-TCP endpoint with its own
reason.

Three or more Native AA bring-ups. Report `WifiDirectManager: group identity ssid=` for each,
whichever `RENAMED` line appears, and the endpoint policy's refusal.

**PASS:** the app names the instability honestly **and still completes the handshake over the
ordinary Bluetooth WPP path**, reaching a picture. **FAIL:** it withholds the endpoint and also fails
to connect, or it forces the endpoint out on a unit that re-addresses every group.

### B4. The branch's bring-up refusals on a 2014 UI

The candidate refuses a wireless bring-up in three new places and puts an X on the status pill. None
of it has been seen on an old Android.

1. The pill renders, and its label is readable.
2. Its X holds the stack down: after tapping it, nothing re-arms except the WiFi button.
3. Leaving Settings with nothing saved re-arms nothing: **zero** `ACTION_START_WIRELESS` in the 5 s
   after closing it. Round 1 graded this on D-HU as W3 item 2 and it passed there.

**PASS:** all three. **FAIL:** any one, with a screenshot.

---

## Stage C - D-HP as head unit, one phone, Headunit Server only

D-HP is an HP Slate 7 Plus on **Android 4.2.2, API 17**, serial `CNU350BGBJ`, back on the rig after
being listed as retired. It has **no WiFi Direct and no hotspot**, so Headunit Server
(`wifi-connection-mode=1`) is the only mode it can run: do not attempt Native AA or any transport
downstream of an access point on it.

API 17 is the `github` flavor's `minSdk` floor. Nothing on this branch has run there. Treat the whole
stage as exploratory: **a run that cannot reach its subject at all is a result**, reported as
UNTESTABLE with the reason, not as a FAIL. Expect D-SAM's 4.4.2 quirks to apply here and worse; in
particular try `am` without `-p` first.

### L1. It installs, launches and projects

Install, launch, and take one Headunit Server session to a picture. Report the four projection lines
and the throughput. **PASS:** a picture. **FAIL:** anything short, with the last line reached and
whether it was an install, a launch or a session failure. If the APK will not install at all, report
the exact error: that is a build-configuration finding worth more than the rest of this stage.

### L2. The stand-in hands-free record at API 17

Same as B2, same three greps. Report which branch it takes and whether the record can even be
registered on this Android. One line of answer is enough if L1 failed.

### L3. WiFi comes back and discovery does not wait it out

**Only D-HP and D-SAM can grade this.** Below Android 5 the app has no `NetworkCallback`, so a WiFi
join is read from the `NETWORK_STATE_CHANGED_ACTION` broadcast instead, and that arm is new on this
branch. Before it, nothing kicked discovery when WiFi returned and the loop waited out its own 10 s
to 5 min timer.

With the app armed in Headunit Server mode and a sweep running, drop WiFi and bring it back. **Issue
the two as separate calls with a pause**, per the D-SAM rule, which is the safer assumption here too:

```bash
adb -s CNU350BGBJ shell svc wifi disable
sleep 5
adb -s CNU350BGBJ shell svc wifi enable
```

**PASS:** a `NetworkMonitor:` line naming the broadcast, and a new `NetworkDiscovery: Scanning
subnet` within about a second of the join, not after a ten-second wait. **FAIL:** no kick, or a
sweep that only appears on the old timer. Run it twice; the second one also grades the debounce, so
report both timestamps.

### L4. Sharing a log below Android 10

The Share button crashed on this unit, and the fix for it is on this branch. **D-HP is the only rig
that can grade it**, because that is where the crash was measured.

Export a log from inside the app and share it. **PASS:** the share sheet opens and the file is
delivered with no crash. **FAIL:** a crash, with the stack trace. Report where the file actually
landed: on this Android it may be under `/storage/sdcard0` rather than `/storage/emulated/0`, which
has never been confirmed on D-SAM and matters for both units.

### L5. How the projection gets raised at API 17

Below Android 6 the permission to draw over other apps is granted at install and cannot be revoked,
so `ActivityLaunchPolicy` should always choose DIRECT and round 1's N1 arm 2, the notification route,
may be unreachable on this unit.

Report the `raising the projection by ...` line for a session started by the phone, and whether
anything can make it choose NOTIFICATION. **That the notification route is unreachable here is the
expected result, not a FAIL.** Say which route it took and why.

---

## 4. What this round cannot settle

- **W1r measures one head unit, not the wake in general.** The displacement is not in doubt and is
  not being tested: the phone's Audio Gateway closes the link it holds when it accepts a second
  connection from the same address, which is the Bluetooth stack working as written. W1r measures
  whether **D-HU's** own hands-free client re-establishes once it is left alone. Another head unit
  can answer differently, which is exactly why the app stores the answer per unit.
- **W1r says nothing about whether the escalated wake works**, by construction: Gearhead is disabled.
  Round 1 of this thread also closed the other half of that question in the opposite direction, with
  N6 reading 6/6 ordinary reconnects on both phones. Neither rig phone reproduces the negative the
  escalation exists for any more, so its benefit cannot be re-measured here at all.
- **N4r's deaf peer is ours, not Android Auto's.** It grades the app's reaction to a peer that
  accepts and never speaks. A real head unit server going deaf is a phone-side state the rig cannot
  create, and round 1 spent three attempts proving that.
- **E1 is a race, and ten clean cycles do not prove it cannot lose.** The fix removes the cancelled
  suspension point rather than widening a window, so ten is enough to catch a regression and not
  enough to call the race retired. Report the count, not a verdict about the race.
- **Stage C is a compatibility probe.** A PASS says the branch runs on API 17 on one tablet. It says
  nothing about the owner's own low-end unit, which is where the frame-rate report came from and
  which no rig device stands in for.
- **D-SAM's battery is an uncontrolled variable** unless it is charged from a separate supply: its
  USB port is data-only. Any radio timing from stage B carries that caveat.
