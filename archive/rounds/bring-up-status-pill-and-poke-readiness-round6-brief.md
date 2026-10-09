# bring-up-status-pill-and-poke-readiness: round 6 brief

**Candidate:** `fork/testing/status-pill-aac-zbt` @ `d8198612`
**Baseline:** none needed. See §1.
**Gate:** 1813 tests / 0 failures
**Read `TESTING-TEMPLATE.md` first.** This brief only says what is different about this round.

---

## 0. What this is and why it exists

Round 5 found three FAILs and two defects it was not looking for. All three are fixed, and the
branches have also gained work that has never been on hardware. This round grades the fixes, that
new work, the external Bluetooth module route, and what is left of the AAC branch. Six parts, all
independent: a failure in one says nothing about the others.

**The auto-start offer could not run in the case it exists for.** Round 5's C1 read as "the RESET
branch did not fire" and left the cause open. The cause is in `HomeFragment.onResume`, which called
the offer only when the driver check had put nothing on screen. Two paired phones is exactly what
raises the driver selector, and two paired phones is the whole of RESET's precondition, so RESET was
unreachable on every unit it applied to. The C1 capture proves the selector was up:
`ACTION_NATIVE_AA_PROMPT_SHOWN` at 07:42:11.200 and `..._DISMISSED` at 07:42:14.230, three seconds
later, when the Bluetooth auto-start launch paused the fragment. The same gate blocked the question
in the single-phone case, which is what C2's first attempt met. RESET no longer waits for anything,
because it only rewrites a setting; the question still gives way, and a later resume carries it.

**The offer's dialog was destroyed before anybody could answer it, permanently.** Round 5's C2.
`showAutoStartOffer` never recorded its dialog, unlike every other dialog in that fragment, so
`onPause` could not take it down and the stored-orientation relaunch leaked its window. Worse, the
answer was marked when the question was *put*, so the relaunched fragment read the phone as already
asked and the offer was suppressed for good, unanswered. The dialog is tracked now, and the mark is
written when the user answers or closes it: an app-side `dismiss()` does not fire a cancel listener,
so a relaunch asks again while a back press still does not.

**The Android Auto listener never came back from a radio bounce.** Round 5's A1 found this while
doing something else, and it is the most serious thing in that round. The RFCOMM accept loop ended
on a socket error having recorded nothing, so the manager still read as running: `start()`
early-returned, the rearm answered *"the Android Auto listeners are still open, so the phone can
come straight back"* when they were not, and the wake poke believed it could help. Nothing in the
app watched for the radio returning. The group recovery watchdog then spent all four recreations on
a group that was never the problem and gave up until the next start. A failed loop exit is recorded
now, the three answers above tell the truth, and the radio coming back reopens the listeners.

**A vetoed Bluetooth auto-start left no line at all.** Round 5's A1 condition 3 asked for a line the
run could never produce, because a different and independently correct veto fired first and printed
nothing. The decision now says which of the four vetoes it was.

**The narrow-band cap read the radio, not the link.** The cap lowers video to 720p30 and announces
AAC, and it fired only when the radio had no 5 GHz band. A unit that has one and is hosting a
2.4 GHz group is on the same narrow link, and the app already tells that user to expect 720p when it
offers the 2.4 GHz band as a remedy. The cap now asks what band the session is actually on. This is
also what makes the cap gradable on this rig at all, from one settings write.

**Three commits the pill branch gained after round 5.** The station stand-down mode, which answers a
reporter whose unit scanned every ten seconds with the unconditional stand-down in force; the
WPP-over-TCP re-dial hold-open; and the decoder-stop guard on a fast reconnect. None has been on
hardware.

**The external Bluetooth module route, on a rig that has no module.** Everything about that route
hangs off one detection, and on plain hardware it answers no, so the settings rows are absent and
nothing is logged. §7a now records the lever that puts the app on its real detection path, and Part
E uses it to grade what a user who does have such a unit actually sees.

## 1. Build and baseline

```bash
git fetch fork
git checkout testing/status-pill-aac-zbt   # d8198612
```

That branch is `main` with `feat/bring-up-status-pill-and-poke-readiness`,
`feat/external-bt-zbt-probe` (which carries the pill branch under it) and
`fix/aac-default-on-narrow-band` merged. It carries no commits of its own and is rebuilt from `main`
whenever a part of it moves, so do not expect its SHA to match any earlier round's.

**No baseline APK.** Every run below is either a new line that only the candidate can print, or a
positive control reached by a settings change on the candidate itself.

## 2. What is different about this round

**The rig's standing state has changed, and two runs depend on it.** D-MOTO is now bonded to D-HU,
left there by round 5's C1. D-HU therefore reports **two** paired phones on every run, and driver
selection acts before anything else on its home screen. That is what A1 needs. It is also why A2 and
A3 run on D-POCO, which has one.

**Verify, do not assume, three pieces of rig state before starting.** Each has cost a round before:

1. `adb -s <hu> shell dumpsys bluetooth_manager | grep -A5 "Bonded devices"` on both D-HU and
   D-POCO, and report what it says. A1 needs two phones on D-HU; A2 needs one on D-POCO.
2. `dumpsys wifip2p | grep isGroupOwner` on **every** phone before the round starts. A phone left
   hosting its own group from an earlier round stalls a connection at `PHONE_JOINING` forever, which
   cost round 5 about five minutes on its first AAC attempt. §7a has the lever.
3. `native-preferred-device-mac` and `last-connected-native-mac` on D-POCO. A2 needs the second to
   name D-MOTO; if it is empty the offer's own precondition is not met and A2 is INCONCLUSIVE rather
   than FAIL.

**AAC3 needs D-MOTO's Android Auto unbound from D-HU first.** Round 5 lost that run to it: D-MOTO
answered six wake pokes from D-POCO and never opened the Android Auto channel, because its Gearhead
was bound to D-HU as its current wireless car. The app's own diagnostic names it. Clear D-MOTO's
saved car, or F2 will repeat the same INCONCLUSIVE.

**Declared UNTESTABLE up front, not a failure:**

- **The cap's radio arm.** `WifiBandCapability.supports5Ghz` reads `WifiManager.is5GHzBandSupported`
  and nothing overrides it. Both rig units answer true. F1 reaches the cap by the other arm, the
  band the group came up on, which is a real measurement and not a simulation.
- **`AacDecoderRecoveryPolicy`'s rebuild budget.** Round 5 measured zero rebuilds across every run,
  which is the right outcome; nothing on this rig produces the decoder error the budget bounds. It
  stays with its JVM tests.
- **The stand-down's "no 5 GHz band" skip**, for the same reason as the cap's radio arm.
- **Everything past the module daemon's handshake in Part E.** There is no module. Part E grades the
  detection, the settings surface and the refusal, which is all a unit without the hardware can
  honestly answer for.
- **USB host on D-HU** (`host_connected=false`), unchanged from every earlier round.

**A2's soft-AP wait from round 5 is not re-run.** This unit's own access point tears down in well
under 500 ms, so `createWhenRadioIsFree()`'s wait branch cannot be reached from adb; two attempts
already closed the timing gap as far as scripting allows.

## 3. Settings keys this round needs

Written in `shared_prefs/settings.xml` with the app stopped, never in the UI. D-HU is rooted;
D-POCO goes through `run-as`.

### D-HU

| Key | Type | Value | Used by |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | all |
| `native-driver-selection-mode` | int | `1` (AUTO, the standing value) | A1 |
| `auto-start-bt-macs` | set of string | `DC:B7:2E:5E:4E:59` | A1 |
| `auto-start-offer-answered-macs` | set | **delete the element** | A1 |
| `stand-down-station-mode` | int | `0` AUTO, `1` ALWAYS, `2` NEVER | C1 to C5 |
| `wifi-direct-band` | int | `0` automatic, `2` force 2.4 GHz | F1 |
| `use-aac-audio` | bool | delete for F1, `true` for F2 | F1, F2 |
| `narrow-band-profile-cap` | bool | `true` (the default; confirm it is not written false) | F1 |
| `log-level` | int | `0` only where a run says so | F3 |

### D-POCO, as head unit

| Key | Type | Value | Used by |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | A2, A3, F2 |
| `native-driver-selection-mode` | int | `1` (AUTO) | A2, A3 |
| `auto-start-bt-macs` | set | **delete the element** (nothing configured) | A2, A3 |
| `auto-start-offer-answered-macs` | set | **delete the element** between every attempt | A2, A3 |
| `screen-orientation` | int | `2` (landscape) | A3 |
| `use-aac-audio` | bool | `true` | F2 |

Read every one of them back before launching, and report the read-back. Round 5's C2 spent an
attempt on a settings table that did not mention `native-driver-selection-mode` at all.

## 4. The lines that decide every run

All verified with `git grep -F` against the candidate, and quoted as the fragment that appears in
the source rather than the whole rendered line, because several are assembled from several string
literals. A few are assembled at runtime and so are not greppable in the source at all; those are
marked. Where a line carries an em dash, grep the fragment after it.

**The auto-start offer**

```
HomeFragment: more than one phone is paired, so the Bluetooth auto-start device is cleared.
HomeFragment: Bluetooth auto-start turned on for
AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received
HomeFragment: Unambiguous driver
BluetoothHelper: driver candidates:
```

`driver candidates:` prints at INFO **only when its composition changes** and at DEBUG otherwise, so
a second identical call in the same launch is invisible at INFO. Do not read one line as one call.

**The Android Auto listener**

```
NativeAA: ACTIVELY LISTENING on Android Auto UUID
NativeAA: AA Server socket error        (assembled at runtime; "AA Server socket" is the label)
NativeAA: the Android Auto listener is down, so nothing can answer the phone.
NativeAA: reopening the Android Auto listener
NativeAA: the Android Auto listeners are still open, so the phone can come straight back.
the Android Auto listener was lost when this unit's Bluetooth went away
WifiDirectManager: Native AA recovery
giving up until the next start.
AapService: Bluetooth auto-start: nothing to do,
```

**The station stand-down**

```
StationStandDown: asked this unit to leave
StationStandDown: this unit has left its WiFi network.
StationStandDown: this unit is still joined to its WiFi network
StationStandDown: this unit's WiFi network is enabled again and should rejoin
the setting keeps this unit joined to its own WiFi network
5 GHz station is the state measured to run clean
will only let the app drop its own WiFi connection while the app
```

The first line carries `mode=`, the station frequency and `5GHz=` inside it, so it is the one line
that separates AUTO-stood-down from AUTO-left-joined. Quote it whole.

**The re-dial and the decoders**

```
WppTcpServer: projection already up; holding the re-dialled control channel open without a handshake
AapService: a session is already connected, so its decoders are left running
```

**The external Bluetooth module route**

```
NativeAA: [ZBT] nothing is listening on 127.0.0.1:3152
ZbtProbe: starting
ZbtProbe: finished
bt=internal                             (assembled at runtime from "bt=" and the route)
bt=module:                              (assembled at runtime)
```

**AAC and the band**

```
WifiDirectManager: onGroupInfoAvailable:
[ServiceDiscovery] AAC audio announced by the 2.4 GHz cap (Use AAC Audio is off)
This session's network is on 2.4 GHz    (logged with a "[ServiceDiscovery] " prefix)
Media Sink Setup Request
AudioDecoder.start:
AAC Decoder started for
```

## 5. Runs

### R0 - build, gate, install, identity

Build with `build_hur.sh`, run `run_unit_tests.sh`. **PASS requires:**

1. `BUILD SUCCESSFUL`, and the test XMLs sum to **1813 tests, 0 failures, 0 errors**.
2. Installed on D-HU and D-POCO, with the live `md5sum` of each `pm path` base.apk matching the
   built APK and each other.
3. Identity: `ACTION_QUERY_STATE` (with `-p com.andrerinas.headunitrevived`, see §7a) reports the
   candidate's commit. DEX grep confirms `AaListenerRecoveryPolicy`, which no earlier build carries.

### R1 - the pill, as a regression guard

The round 4 and round 5 P1: a Native AA cold bring-up on D-HU with D-POCO, graded on the ordered
`status pill step:` list. **PASS requires:** at least six steps, first `ARMED`, `WAITING_FOR_PHONE`
before `WAKING_PHONE`, last `STARTING_PROJECTION`, and any rank decrease being a
`WAKING_PHONE` to `WAITING_FOR_PHONE` retreat or the one that follows a create request. Report the
wall clock from the first step to `STARTING_PROJECTION`.

This is here to catch a regression from the three commits, not to re-prove the pill. A failure here
is worth more than any other run in this brief.

---

### Part A - the auto-start offer, and the point of this round

**A1, the stored trigger is cleared even while the selector is up.** This is round 5's C1 in its own
configuration, unchanged: D-HU, two paired phones, `native-driver-selection-mode` left at `1`. Set
`auto-start-bt-macs` to D-POCO's MAC, delete `auto-start-offer-answered-macs`, read both back, launch.

**PASS requires:**

1. `HomeFragment: more than one phone is paired, so the Bluetooth auto-start device is cleared.`
   appears.
2. `auto-start-bt-macs` reads back **empty** afterwards, in both `shared_prefs/settings.xml` and the
   device-protected mirror at `/data/user_de/0/com.andrerinas.headunitrevived/shared_prefs/`.
   Both, because the mirror is written separately and resyncs from the host file.
3. The driver selector **was** on screen while that happened: `ACTION_NATIVE_AA_PROMPT_SHOWN` is
   present. This is the whole point of the run. If it is absent the run did not reach the state that
   failed in round 5, and the result is INCONCLUSIVE rather than PASS.
4. `driver candidates: 2 phone` confirms the two-phone pool.

If condition 3 holds and condition 1 does not, that is a clean FAIL and the fix did not work.

**A2, the question is asked on a later resume.** D-POCO as head unit, one paired phone (D-MOTO),
`auto-start-bt-macs` deleted, `auto-start-offer-answered-macs` deleted, `last-connected-native-mac`
confirmed to name D-MOTO, D-MOTO's Bluetooth **off** so the auto-connect it triggers goes nowhere.
Launch, wait for the auto-connect to give up, then press Home and reopen the app.

**PASS requires:**

1. The first launch logs `HomeFragment: Unambiguous driver` and no offer dialog appears. The offer
   giving way to a connect is correct, not a failure.
2. After the reopen, the offer dialog is on screen. Screenshot it.
3. `auto-start-offer-answered-macs` is still **absent** while the dialog is up and unanswered.
4. Answering Yes writes the MAC and logs
   `HomeFragment: Bluetooth auto-start turned on for`, and the question does not return on the next
   launch.

If the dialog never appears and `HomeFragment: Unambiguous driver` also never appears, read
`last-connected-native-mac` again: the run's own precondition was not met and this is INCONCLUSIVE.

**A3, the dialog survives the relaunch, and a back press is remembered.** Same as A2 with
`screen-orientation` set to `2` on D-POCO, which forces the Activity relaunch about half a second to
a second after the first resume. Delete `auto-start-offer-answered-macs` before each attempt.

**PASS requires:**

1. Two `HomeFragment.onResume` lines about half a second apart, confirming the relaunch actually
   happened. Without a second one the run proves nothing and is INCONCLUSIVE.
2. No `WindowLeaked` anywhere in the capture, and no `FATAL EXCEPTION`; the PID is unchanged.
3. A dialog is on screen and answerable at +5 s. Screenshot it.
4. Then dismiss it with `input keyevent KEYCODE_BACK`, relaunch, and confirm the question does
   **not** come back: a back press is the user's own answer. Report
   `auto-start-offer-answered-macs` after each step.

---

### Part B - the Android Auto listener, and the veto line

**B1, the listener comes back after the radio bounces.** D-HU, WiFi **on** throughout, Native mode,
one phone. Launch and wait for `NativeAA: ACTIVELY LISTENING on Android Auto UUID`. Only then run
`svc bluetooth disable` on D-HU, which self-reverts in about 14 s on this unit. Leave it 3 minutes.

The order matters and §7a already says why: `NativeAaHandshakeManager.start()` needs the adapter
enabled when it runs, so toggling before the launch prevents the manager coming up at all and tests
nothing. Let the listener open first.

**PASS requires:**

1. `NativeAA: AA Server socket error` appears when the radio goes, as it did in round 5.
2. `NativeAA: the Android Auto listener is down, so nothing can answer the phone.` appears. This
   line is new and is the whole of the fix's first half.
3. `NativeAA: reopening the Android Auto listener` appears after the radio returns, followed by a
   second `ACTIVELY LISTENING on Android Auto UUID`. Report the gap between the radio coming back
   and that second line.
4. The phone then gets a session **without the app being relaunched**: one
   `SSL handshake complete`. This is the run's real question.
5. `giving up until the next start.` is **absent**. Round 5 reached it after four recreations spent
   on a group that was never at fault.

A pre-fix build fails 2, 3 and 4. If the radio does not self-revert within a minute, re-enable it by
hand and say so: the fix is about the listener, not the toggle.

**B2, a vetoed auto-start says which veto fired.** Any Bluetooth auto-start arrival in this round
that does nothing must print `AapService: Bluetooth auto-start: nothing to do,` followed by the
reason. B1's capture almost certainly carries one; if not, raise one by cycling D-HU's adapter again
while a handshake attempt is in flight.

**PASS requires:** at least one such line, quoted whole, naming one of: a session is already up, a
handshake attempt is already in flight, the network has been asked for and has not answered yet, or
a handshake is running on a group that is still up. **This run replaces round 5's A1 condition 3**,
which asked for a line a different veto prevented.

---

### Part C - the station stand-down mode

This part needs D-HU **joined to a WiFi network**, which the stand-down is about leaving. Use D-MOTO
as a 2.4 GHz hotspot for C1 and a 5 GHz one for C2 and C4, and keep D-POCO as the session's phone.
Confirm the band D-HU actually joined on with `dumpsys wifi | grep -i freq` before each run; the
policy reads the station's own frequency, not the hotspot's setting.

The settings row is shown on every Android below 15, but the feature needs the "display over other
apps" permission on Android 10 to 14, and D-HU is 14. Grant it with
`appops set com.andrerinas.headunitrevived SYSTEM_ALERT_WINDOW allow` for C1 to C4 and revoke it
with `deny` for C5.

**C1, AUTO stands down from a 2.4 GHz station.** `stand-down-station-mode` = `0`, overlay granted,
D-HU joined to a 2.4 GHz network. **PASS requires:** `StationStandDown: asked this unit to leave`,
quoted whole so `mode=`, the station frequency and `5GHz=` can be read; then
`this unit has left its WiFi network.`; then, after the session ends,
`this unit's WiFi network is enabled again and should rejoin`

**C2, AUTO stays joined on a 5 GHz station.** Same, with D-HU joined to a 5 GHz network. **PASS
requires:** no `asked this unit to leave`, and the line carrying
`5 GHz station is the state measured to run clean`.

**C3, NEVER stays joined.** `stand-down-station-mode` = `2`, D-HU on the 2.4 GHz network, overlay
granted. **PASS requires:** no `asked this unit to leave`, and the line carrying
`the setting keeps this unit joined to its own WiFi network`.

**C4, ALWAYS stands down regardless of band.** `stand-down-station-mode` = `1`, D-HU on the **5 GHz**
network. **PASS requires:** `asked this unit to leave` with `mode=ALWAYS` in it. This is the
positive control for C2: the same station state, the opposite outcome, from one settings change.

**C5, the permission gate.** `stand-down-station-mode` = `1`, overlay **revoked**. **PASS requires:**
no stand-down, and the line carrying
`will only let the app drop its own WiFi connection while the app`. Re-grant the permission
afterwards and say so, because later parts assume the rig's standing state.

If D-HU cannot be joined to a network at all, every run in this part reports the line
`there is nothing to stand down before creating the group`, and the part is INCONCLUSIVE rather than
failed. Say which network each run was joined to.

---

### Part D - the re-dial hold-open and the decoder guard

**D1, a re-dial while projecting sends nothing.** A live Native AA session on D-HU with D-POCO. Once
the picture is up, make the phone re-dial the control channel: toggle D-POCO's WiFi off and on
briefly, or restart Android Auto on it, without ending the session.

**PASS requires:** `WppTcpServer: projection already up; holding the re-dialled control channel open
without a handshake` appears, **and** the projection survives it: no new `SSL handshake complete`,
no gap in the `Throughput over ...ms: rendered=` lines. If the session dies instead, quote what came out and FAIL.

If the phone never re-dials within a few attempts, D1 is INCONCLUSIVE. Do not force it by ending the
session, which is a different path.

**D2, a disconnect keeps a live session's decoders.** Immediately after a session ends, start another
one fast enough that the new session is up before the old one's disconnect is handled. Scripted:
`headunit://disconnect` followed at once by a fresh connect.

**PASS requires:** `AapService: a session is already connected, so its decoders are left running`
appears **and** the new session shows a picture. This is a race, so if the line never appears after
three attempts the run is **INCONCLUSIVE, not FAIL** - the guard is for a window that may simply not
open on this hardware. Report how many attempts were made and what the gap was.

---

### Part E - the external Bluetooth module route, on a unit with no module

**Run this part last.** With the property set, Native AA is refused, so nothing else in the round can
run while it stands. §7a has the full lever and the clean-up.

Before anything: export a log and confirm the footer reads `bt=internal`. That is the baseline.

Then, with the app force-stopped:

```bash
adb -s <hu> shell setprop rw.zlink.bt.type extra
adb -s <hu> shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity
```

**E1, the settings surface appears.** Open Settings and find the Wireless Connection section.
**PASS requires:** three rows that were absent before, screenshotted: "Ignore the Bluetooth
compatibility check" (only while Native is the selected mode), "Connect through the head unit's
Bluetooth module", and "Test the head unit's Bluetooth module". Screenshot each row with its
description text readable. This is the run that answers "what does this look like to somebody who
has one of these units", so the screenshots are the deliverable.

**E2, choosing Native AA is refused honestly.** Select Native AA as the wireless mode. **PASS
requires:** a dialog titled "Native Wireless cannot work on this head unit", and its body naming
that this unit was asked and did not answer. Screenshot it.

**E3, the probe.** Tap "Test the head unit's Bluetooth module" and take the "Watch only" button.
**PASS requires:** `ZbtProbe: starting` in the log, then the row's own text reporting that nothing is
listening on port 3152, and `NativeAA: [ZBT] nothing is listening on 127.0.0.1:3152` in the log.
Then run it again with "Wake and watch" and confirm the same outcome.

The budgets are long and a working probe looks like a hang: a failed reachability dial takes about
7 s (a 4 s connect plus a 3 s wait for the daemon to say something) and the probe itself watches for
up to 90 s. Wait it out before calling it stuck.

**E4, the export footer.** Export a log with the transport setting off, then turn
"Connect through the head unit's Bluetooth module" on and export again. **PASS requires:**
`bt=module:BLOCKED` in the first and `bt=module:ZBT` in the second, against the `bt=internal` from
before the property was set.

**Then clean up, and prove it:** `setprop rw.zlink.bt.type ""`, force-stop, relaunch, confirm the
three rows are gone and a fresh export reads `bt=internal` again. A round that leaves the property
set leaves the rig unable to run Native AA.

---

### Part F - what is left of the AAC branch

**F1, the cap fires on the band the session is on.** This is the run the cap arm has never had. On
D-HU: `wifi-direct-band` = `2` (force 2.4 GHz), `use-aac-audio` **deleted**, `narrow-band-profile-cap`
left at its default true. Connect D-POCO and play music through the tap into the projected video.

**PASS requires:**

1. The group actually came up on 2.4 GHz: `WifiDirectManager: onGroupInfoAvailable:` reporting a
   `Freq:` below 4000 MHz. If it came up on 5 GHz the run never reached the state under test and is
   INCONCLUSIVE, not a FAIL.
2. `[ServiceDiscovery] AAC audio announced by the 2.4 GHz cap (Use AAC Audio is off)` appears, with
   `use-aac-audio` confirmed absent from the settings read-back.
3. `Media Sink Setup Request: 2` on the media audio channel, and `AudioDecoder.start:` reporting
   `isAac=true, source=setup`.
4. The video half of the same cap: the announced configuration is at most 720p and 30 fps. Quote the
   line that carries it.
5. `[ServiceDiscovery] This session's network is on 2.4 GHz` appears, naming the network rather than
   the radio. That wording is the whole point: this unit **has** a 5 GHz radio.

Then set `wifi-direct-band` back to `0` and confirm the same build announces PCM and the user's own
video settings again. That is the positive control and it costs one settings write.

**F2, AAC on a second decoder vendor.** Round 5's AAC3, redone. D-POCO as head unit, D-MOTO as
phone, `use-aac-audio` = `true` on D-POCO. **D-MOTO's Android Auto must be unbound from D-HU first**,
or this repeats round 5 exactly.

**PASS requires:** a session at all, then `Media Sink Setup Request: 2`, `AudioDecoder.start:` with
`isAac=true, source=setup`, `AAC Decoder started for 48000 Hz, 2 channels`, and none of
`rebuilding the AAC decoder`, `no working AAC decoder`, `AAC Codec Error`,
`Failed to init AAC decoder`. Report the `inbound rate over` audio figure for comparison with round
5's 62 to 63 kB/s.

If D-MOTO still never opens the Android Auto channel, quote the app's own diagnostic and report
INCONCLUSIVE again, but say what was tried to unbind it.

**F3, the 16 kHz guidance channel, with a real maneuver.** D-HU with D-POCO, `use-aac-audio` = `true`,
`log-level` = `0`. Start turn-by-turn navigation on D-POCO with the phone already unlocked and Maps
already granted its permissions, and let it speak at least one route maneuver.

**PASS requires:** `AAC Decoder started for 16000 Hz, 1 channels` **twice** in one session: once at
connection setup and once for the maneuver. Round 5 got only the first, which is why this run exists.
If no maneuver can be made to speak within a few attempts, report how many and mark it INCONCLUSIVE;
do not spend more than about fifteen minutes on it.

**F4, the two changed descriptions.** Screenshot the "Use AAC Audio" row and the "Lower video on a
2.4 GHz link" row in Video settings, with their description text readable. Both were reworded and
nobody has seen them on a screen. No verdict, evidence only.

## 6. Do not re-run

Settled, and nothing in this candidate touches them:

- Round 5's P2 to P6, W2, WB1, WB1b and its D1 to D3, all settled on `7f439d4e` in round 4.
- Round 5's A1 conditions 1, 2 and 4. The WiFi-off bring-up recovery passed and is not re-tested;
  only its condition 3, the missing veto line, comes back, as B2.
- Round 5's A2, the soft-AP wait. Unreachable on this unit, twice.
- Round 5's AAC1, AAC2 and AAC4. The media path is settled: every announced sink `source=setup`,
  zero failures, 62 to 63 kB/s against PCM's 187 kB/s on the same track.
- `AutoStartOfferPolicy`, `AaListenerRecoveryPolicy`, `BtAutoStartRearmPolicy`,
  `NarrowBandProfilePolicy`, `StationStandDownPolicy`, `AudioSinkCodecPolicy` and
  `AacDecoderRecoveryPolicy` are all pure and JVM-tested. The runs above grade the wiring around
  them, never the decisions themselves.

## 7. Report back

The numbers that decide the shipping question:

1. **A1 condition 3 and condition 1 together.** Was the selector on screen, and did the trigger get
   cleared anyway. That pair is the round.
2. **B1 condition 4.** Did the phone get a session after the radio bounced, without the app being
   relaunched.
3. **A3 conditions 2 and 3.** No leaked window, and a dialog somebody could actually answer.
4. **F1 conditions 1, 2 and 5.** The band the group came up on, the cap firing on it, and the
   wording naming the network rather than the radio.
5. **C1 against C2 and C4.** One line, quoted whole, from each.
6. **Part E's screenshots**, which are the answer to what these settings look like on a unit that
   has the hardware.

And the standing question the AAC branch is waiting on: with F1, F2 and F3 in hand, does
"(Experimental)" come off the "Use AAC Audio" description, and does the cap's AAC default ship.
