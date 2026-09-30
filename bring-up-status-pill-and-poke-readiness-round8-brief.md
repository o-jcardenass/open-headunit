# bring-up-status-pill-and-poke-readiness: round 8 brief

**Candidate:** `fork/feat/native-aa-enhancements-extbt` @ `e466171a`
**Baseline:** none needed. See §1.
**Gate:** 1829 tests / 0 failures
**Read `TESTING-TEMPLATE.md` first.** This brief only says what is different about this round.

---

## 0. What this is and why it exists

Round 7 was the best round this thread has had. R0, R1, A1, A3, B2 and all five of Part C passed,
E5 confirmed the reworded module dialogs on hardware, and the auto-start offer's ASK arm reached the
screen for the first time in seven rounds, twice. Nothing in it graded FAIL at the round level.

It also found three defects, all of which reproduce from the source rather than resting on the
capture, and all three are fixed in this candidate.

**The give-up watchdog could never fire, and there were two reasons.** A2 condition 2 watched for
7 min 39 s across 19 wake cycles and never saw the line. `startAutoConnectWatchdog()` has exactly
one call site, and it sits below `beginAutoConnect`'s `if (autoConnectInProgress) return`. That flag
is on the companion and outlives the activity; the watchdog `Job` is on the activity's own scope and
dies with it. So an activity recreation mid-attempt killed the bound for good: the rebuilt activity
called `beginAutoConnect`, returned at the guard, and never reached the arm. Separately, a coroutine
`delay` on the main thread is a `postDelayed` on the uptime clock, which stops while a unit sleeps,
so a 150 s bound on a backgrounded head unit can outlast far more than 150 s of wall clock. The
bound is now a deadline on elapsed realtime, re-armed by whichever activity is alive, re-read after
every sleep, and checked again on each resume.

**One correction to round 7's own reasoning, because this round needs the right evidence.** A2
condition 2 ruled an activity recreation out from an unchanged PID. A PID cannot see one: Android
destroys and rebuilds an activity inside the same process, which is exactly what the stored
orientation relaunch this thread has now measured three times does (A3's own 0.326 s, round 6's
0.406 s). The observation was right, the inference from it was not. A2 below grades the arm line's
**count** instead, which is the thing that actually distinguishes the two mechanisms.

**`ACTION_START_WIRELESS` really was refused, and D2's INCONCLUSIVE was honest.**
`wifiLauncherManager.stop()` keeps the stopped launcher in `active` at every sequence but the last,
so `setActive` answered "same start-configuration is already initialized" on behalf of something
already torn down, and it answered at DEBUG where no reporter capture would carry it. The guard now
asks whether the launcher is still running, and the refusal is at INFO. `TESTING-TEMPLATE.md` §7a
carried this brief's wrong claim about that lever since round 6 and is corrected.

**S1 measured a policy gap and this candidate closes it.** Same unit, same hotspot, same
`wifi-direct-band=2`: under AUTO the session sat at `PHONE_JOINING` for 4 min 35 s despite three
successful RFCOMM pokes and never completed, and under ALWAYS the station left, SSL landed in about
58 s and the session held 34 to 41 fps for 98 s with no degradation. AUTO's own skip line was still
telling that user "a group beside a 5 GHz station is the state measured to run clean", which is true
only when the group is on 5 GHz too. AUTO now reads the band the group is about to ask for and
stands down for a forced 2.4 GHz group beside a 5 GHz station; the skip line says which pairing it
means. What the fix still cannot cover is a group that **falls back** to 2.4 GHz after a refused
5 GHz create, because the stand-down runs before `createGroup`.

**F1b cleared the narrow-band cap.** With the cap off, a forced 2.4 GHz group beside a 5 GHz station
either never associated or bled down to 4 to 7 fps over three minutes. The cap changes the shape of
the failure, not whether there is one, so round 6's suspicion of it is dropped and the cross-band
split is the cause. That matters for F1 here: its configuration is now C6's, so the station leaves
before the group forms, and F1's session may finally live long enough to reach condition 3.

---

## 1. Build and baseline

No baseline build. Every run below either grades a line that does not exist on any earlier build or
re-runs a part whose previous result is already written down.

Build, gate and install per `TESTING-TEMPLATE.md` §2. The gate is **1829 / 0**.

## 2. What is different about this round

- **A2 is split into three, because two different mechanisms could have swallowed the watchdog** and
  the round 7 capture cannot tell them apart. A2 is the plain case, A2b forces an activity
  recreation, A2c leaves the screen off for the whole window.
- **A4 moves to D-POCO.** Round 7 confirmed directly that this rig's MT50 has no USB host mode
  (`dumpsys usb`: `host_connected=false`), so A4 can never run on D-HU.
- **D2 uses `ACTION_START_WIRELESS`**, which is the lever the fix is about.
- **Part C is re-run in full as the regression on the stand-down change**, and gains C6, the arm S1
  measured.
- **F3 is dropped**, with its reason, in §6.
- **The candidate's branches were re-cut after this brief was first written.**
  `feat/bring-up-status-pill-and-poke-readiness` is now
  `feat/native-aa-bring-up-and-profile`, four commits grouped by component with the AAC work
  folded in, and `feat/external-bt-zbt-probe` is now `feat/native-aa-enhancements-extbt`, one
  commit stacked on it. **There is no testing branch any more.** The stack collapsed to a single
  branch that carries everything, so `testing/status-pill-aac-zbt` and
  `feat/native-aa-bring-up-and-profile` were both deleted as contained in it, and the candidate to
  clone and build is `feat/native-aa-enhancements-extbt`. Nothing in the tree moved: the candidate's
  tree is byte-identical to the `37dd41e3` this brief first named, and the gate is the same
  1829 / 0. Only the ref to build changed.

## 3. Settings keys this round needs

Written in `shared_prefs/settings.xml` with the app stopped, never in the UI. D-HU is rooted;
D-POCO goes through `run-as`. **Read every one of them back before launching and report the
read-back.**

### D-HU

| Key | Type | Value | Used by |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | all |
| `stand-down-station-mode` | int | `0` AUTO, `1` ALWAYS, `2` NEVER | C1 to C6 |
| `wifi-direct-band` | int | `0` automatic, `2` force 2.4 GHz | C6, F1, F1b |
| `enable-audio-sink` | bool | **`true`** | F1, F1b |
| `use-aac-audio` | bool | **delete the element** | F1, F1b |
| `narrow-band-profile-cap` | bool | `true` for F1, **`false`** for F1b | F1, F1b |
| `last-connected-native-mac` | string | non-empty, naming a paired phone | D2 |

### D-POCO, as head unit

| Key | Type | Value | Used by |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | A2, A2b, A2c, A4 |
| `native-driver-selection-mode` | int | `1` (AUTO) | A2, A2b, A2c, A4 |
| `auto-start-bt-macs` | set | **delete** | A2, A2b, A2c, A4 |
| `auto-start-offer-answered-macs` | set | **delete the element** between every attempt | A2, A2b, A2c |
| `screen-orientation` | int | `0` for A2 and A2c, flipped mid-attempt for A2b | A2, A2b, A2c |

**D-POCO carries exactly one paired phone (D-MOTO), and D-MOTO's Bluetooth stays off** for the whole
of Part A. That is the state the bound exists for: an attempt aimed at a phone that never answers.

## 3a. Re-arming the stack without waiting for a phone

Corrected from round 7, which is what D2 grades. Addressed at the service component the way
`TESTING-TEMPLATE.md` §3 documents for a poke:

```bash
am start-foreground-service -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService \
  -a com.andrerinas.openheadunit.ACTION_START_WIRELESS
```

On any build before this candidate that is refused after a user disconnect and arms nothing, and the
refusal was invisible at INFO. Here it should arm, and if it does not the refusal line is now
visible and is the evidence.

## 4. The lines that decide every run

All checked with `git grep -F` against `e466171a`, except where marked **assembled**, which means
the line is built at runtime from several literals and only its fragments can be grepped.

| Line | Where it decides |
|---|---|
| `Auto-connect: begin (` | A2, A2b, A2c, A4 |
| `Auto-connect: this attempt gives up in ` | A2, A2b, A2c. **New this build** |
| `Auto-connect: nothing answered this attempt (mode=` | A2, A2b, A2c |
| `HomeFragment: Unambiguous driver` | A2, A2b, A2c |
| `HomeFragment: Bluetooth auto-start turned on for` | A2 condition 5 |
| `with same start-configuration is already initialized.` | D2. **At INFO from this build**, DEBUG before it |
| `WifiLauncher: Initializing WiFi Mode: ` | D2, the arm that follows |
| `AapService: a session is already connected, so its decoders are left running` | D2 |
| `StationStandDown: asked this unit to leave` | C1, C4, C6 |
| `group asking for ` | C1, C4, C6. **New field on that line** |
| `StationStandDown: this unit has left its WiFi network.` | C1, C4, C6 |
| `which is the state measured to run clean` | C2. **assembled**, and reworded this build |
| `the setting keeps this unit joined to its own WiFi network` | C3 |
| `will only let the app drop its own WiFi connection while the app` | C5 |
| `[ServiceDiscovery] AAC audio announced by the 2.4 GHz cap (Use AAC Audio is off)` | F1 |
| `[ServiceDiscovery] This session's network is on 2.4 GHz` | **assembled**, F1 |
| `[ServiceDiscovery] NegotiatedResolution is: ` | F1 |
| `Media Sink Setup Request: ` | F1 condition 3 |
| `AudioDecoder.start:` | F1 condition 3 |

`StationStandDown: $why` is assembled from `StationStandDownPolicy.describeSkipped`, so C2, C3 and
C5 grade on the **fragment** named above, never on a whole line. The C2 fragment is not the one
round 7 used: the old wording claimed too much and was replaced, so do not carry
`5 GHz station is the state measured to run clean` forward as a literal.

## 5. Runs

### R0 - build, gate, install, identity

Unchanged. The gate is **1829 / 0**. DEX grep the installed base APK for `WifiLauncherRestartPolicy`,
which is new this commit and absent from every earlier build.

---

### Part A - the attempt's own bound

D-POCO as head unit, D-MOTO paired and its **Bluetooth off throughout**. `auto-start-bt-macs` and
`auto-start-offer-answered-macs` deleted and read back before each attempt.

All three start the same way: launch the app cold and confirm
`HomeFragment: Unambiguous driver (motorola edge 30 neo) - auto-connecting directly without prompt`
and `Auto-connect: begin (`, which is the attempt this part bounds.

#### A2 - the attempt gives up on its own

The plain case, with the screen kept awake (`svc power stayon true`) and nothing touched.

**PASS requires:**

1. `Auto-connect: this attempt gives up in ` appears **once**, and the seconds it names are 150 or
   within a second or two of it.
2. `Auto-connect: nothing answered this attempt (mode=PILL_THEN_OVERLAY), ending it` appears, and
   the wall clock from `Auto-connect: begin (` to it is **150 s ± 15 s**. Round 7 saw nothing in
   459 s, so anything under about three minutes is the fix working; report the number either way.
3. The pill is still up afterwards and its step is whatever the stack is on. The bound ends the
   *attempt*, not the bring-up, and a pill that rewinds to `ARMED` here is a FAIL.
4. No `FATAL EXCEPTION`, no `WindowLeaked`.

#### A2b - a recreated activity re-arms the bound

The recreation half, and the one round 7 could not rule in or out. Start as above, then **about 30
to 60 s into the attempt** force a configuration change by flipping the stored orientation:

```bash
run-as com.andrerinas.headunitrevived sh -c \
  'sed -i "s/screen-orientation\" value=\"0\"/screen-orientation\" value=\"2\"/" shared_prefs/settings.xml'
am broadcast -p com.andrerinas.headunitrevived -a com.andrerinas.openheadunit.ACTION_RECREATE_MAIN
```

If that broadcast does not recreate the activity on this unit, rotate it by hand or with
`settings put system user_rotation`, and say in the results which lever was used.

**PASS requires:**

1. `MainActivity: Received recreate request. Recreating.` or equivalent evidence of the rebuild.
2. `Auto-connect: this attempt gives up in ` appears **twice**, and the second one names **less**
   than the first. That pair is the whole point of the run: the rebuilt activity re-armed for the
   time that was left rather than starting the bound again or losing it.
3. `Auto-connect: nothing answered this attempt (mode=` appears, and the wall clock from the
   **first** `Auto-connect: begin (` to it is still about 150 s. A give-up at roughly 180 to 210 s
   means the bound restarted rather than continued, which is a FAIL with a useful number.
4. The PID may or may not change; report it, but grade on the arm-line count, never the PID.

#### A2c - a sleeping unit still gives up

The suspend half. Start as above, then let the screen go off (`svc power stayon false` and do not
touch the device) for the whole window, and read the log back afterwards over adb.

**PASS requires:** `Auto-connect: nothing answered this attempt (mode=` appears, and the wall clock
from `Auto-connect: begin (` to it is **at most about 240 s**. Some slack is expected because a
suspended unit only notices on its next wake; what is being graded is that it gives up at all,
which on the uptime clock it could not. Report the measured figure.

If the unit cannot be made to suspend (a charger, a held wakelock), say so and report A2c
UNTESTABLE rather than passing it on a device that never slept.

#### A4 - a stuck wireless attempt no longer blocks a USB connection

Moved to D-POCO. **First confirm D-POCO can host USB at all**: `dumpsys usb | grep host_connected`.
If it reads `false`, report A4 UNTESTABLE and stop; do not substitute another trigger.

With the Part A attempt in flight and unanswered, plug D-MOTO into D-POCO by hand.

**PASS requires:** `Auto-connect: begin (USB auto-start, mode=OVERLAY)` appears, so the second
attempt was not swallowed by the first. Before round 6's fix this was a silent no-op and no round
has ever graded it.

---

### Part C - the station stand-down mode

Re-run in full: the AUTO arm changed this build and C1 to C5 are its regression. The setup is
round 7's exactly, and it worked, so repeat it: D-MOTO's hand-operated hotspot, 2.4 GHz for C1, C3
and C5, D-HU's own 5 GHz home network for C2 and C4, `connect_hotspot.sh` for the join, overlay
permission granted for C1 to C4 and C6 and revoked for C5. Confirm the band D-HU actually joined
with `dumpsys wifi | grep -i freq` before each run.

**`wifi-direct-band` must be `0` for C1 to C5.** It is the new input to AUTO's decision, so a leftover
`2` from an F run would change C2's answer legitimately and look like a regression.

- **C1, AUTO stands down from a 2.4 GHz station.** `stand-down-station-mode` = `0`, D-HU on the
  2.4 GHz hotspot. **PASS:** `asked this unit to leave` quoted whole, so `mode=`, the station
  frequency, `5GHz=` and the new `group asking for` field can all be read; then
  `StationStandDown: this unit has left its WiFi network.`
- **C2, AUTO stays joined on a 5 GHz station with a 5 GHz group.** Same with D-HU on the 5 GHz
  network. **PASS:** no `asked this unit to leave`, and a line carrying
  `which is the state measured to run clean`. Quote the whole line: its wording changed this build
  and it should now name the group's band as well as the station's.
- **C3, NEVER stays joined.** `stand-down-station-mode` = `2`, D-HU on 2.4 GHz. **PASS:** no
  `asked this unit to leave`, and a line carrying
  `the setting keeps this unit joined to its own WiFi network`.
- **C4, ALWAYS stands down regardless of band.** `stand-down-station-mode` = `1`, D-HU on the 5 GHz
  network. **PASS:** `asked this unit to leave` with `mode=ALWAYS` in it.
- **C5, the permission gate.** `stand-down-station-mode` = `1`, overlay **revoked**. **PASS:** no
  stand-down, and a line carrying `will only let the app drop its own WiFi connection while the app`.
- **C6, AUTO leaves a 5 GHz station for a 2.4 GHz group.** New, and the arm S1 measured.
  `stand-down-station-mode` = `0`, `wifi-direct-band` = **`2`**, D-HU joined to a **5 GHz** network,
  overlay granted. This is exactly S1's setup with only the build changed.
  **PASS requires:**
  1. `asked this unit to leave` with `mode=AUTO` and `group asking for FORCE_2_4GHZ` in it. Under
     round 7's build this run produced the opposite line, so its absence is the regression.
  2. `StationStandDown: this unit has left its WiFi network.`
  3. The session completes: the pill reaches `STARTING_PROJECTION` rather than sitting at
     `PHONE_JOINING`, which is where S1's AUTO run stalled for 4 min 35 s.
  4. Two minutes of playback with no disconnect, which is what S1's ALWAYS run gave.

  Condition 1 alone is the code fix. Conditions 3 and 4 are whether the fix buys the session S1 said
  it should; if 1 and 2 pass and 3 or 4 does not, that is a PASS on the change with a finding, not a
  FAIL.

Overlay permission re-granted and D-HU's temporary networks forgotten after the part, as in round 7.

---

### Part D - the re-arm after a user disconnect

#### D2 - `ACTION_START_WIRELESS` re-arms the stack

D-HU with D-POCO. Bring a live Native AA session up, then `headunit://disconnect`, then immediately
the `ACTION_START_WIRELESS` from §3a.

**PASS requires:**

1. `WifiLauncher: Initializing WiFi Mode: NATIVE` after the disconnect, so the stack was armed.
2. `with same start-configuration is already initialized.` does **not** appear after the disconnect.
   It is at INFO now, so its absence is a real observation rather than a log level.
3. `NativeAA: ACTIVELY LISTENING on Android Auto UUID` follows, so the arm reached the listeners.

Round 7's attempt 1 got none of the three and no session at all, which is the defect.

**The decoder-guard race D2 originally chased is not what this run grades.** Round 7 established
that a disconnect tears the reopened listeners down about 1.3 s later and that no scripted lever on
this rig is fast enough to overlap a live session with a fresh one. If
`AapService: a session is already connected, so its decoders are left running` does appear, say so,
but do not spend attempts chasing it.

---

### Part F - the AAC branch's remainder

**F1, the cap fires on the band the session is on.** Round 7 passed conditions 1, 2, 4 and 5 and lost
condition 3 when the session died 10.2 s after `SSL handshake complete`, reproducing round 6.

Same configuration as round 7: D-HU, `wifi-direct-band` = `2`, `use-aac-audio` deleted,
`narrow-band-profile-cap` = `true`, `enable-audio-sink` read back `true`. **With one change that is
not a settings change:** `stand-down-station-mode` = `0` and D-HU joined to a 5 GHz network, which is
C6's configuration, so on this build the station leaves before the group forms. That is the whole
reason to retry condition 3 now.

Connect D-POCO and play music through the tap into the projected video.

**PASS requires:** round 7's five conditions unchanged, and condition 3 is the one that matters:
`Media Sink Setup Request: 2` on the media audio channel and `AudioDecoder.start:` reporting
`isAac=true, source=setup`. Report how long the session survived either way, and whether
`asked this unit to leave` appeared first.

**F1b, the cap with the station gone.** Identical to F1 except `narrow-band-profile-cap` = `false`.
**Report, no PASS or FAIL:** how many of three sessions survived two minutes of playback and what
ended the ones that did not, with the frame rate at the start and the end of each. Round 7 measured
1 of 3 never establishing and 2 of 3 bleeding from 40-49 fps to 4-13 fps with the station joined; the
question here is whether standing it down removes that bleed.

Quote the `WifiDirectManager` coexistence line and the station's frequency in both runs.

## 6. Do not re-run

- Everything round 7 passed that nothing here touches: R1, A1, A3, B2, D1, E5, and the whole of the
  module route's settings surface. R1 and A3 in particular are settled; A3's dialog-survives-relaunch
  behaviour is not affected by the watchdog change, since the offer's gate does not read the bound.
- **F3 is dropped.** Round 7 established that `cmd location providers set-test-provider-location`
  against the raw `gps` provider drives Google Maps on the phone but never reaches Android Auto's own
  nav rendering, which consumes Play Services' fused location: the projected session reported
  `0 km/h` and never advanced past the first maneuver across ten minutes of fixes. That is a rig
  limitation, not a candidate defect, and F3 stays off every brief until there is a fused-location
  mock or a real drive. It is now in `TESTING-TEMPLATE.md` §7a.
- **A4 on D-HU.** The MT50 has no USB host mode; §7b already said so and round 7 confirmed it
  directly.
- `AutoConnectAttemptPolicy`, `WifiLauncherRestartPolicy`, `StationStandDownPolicy`,
  `AutoStartOfferPolicy`, `AaListenerRecoveryPolicy`, `BtAutoStartRearmPolicy`,
  `NarrowBandProfilePolicy`, `AudioSinkCodecPolicy` and `AacDecoderRecoveryPolicy` are pure and
  JVM-tested. Every run above grades the wiring around them, never the decisions themselves.

## 7. Report back

1. **A2, A2b and A2c together.** Did the attempt give up, how long did it take in each, and in A2b
   did the arm line appear twice with the second naming less than the first. Those three are one
   question asked three ways, and the answer decides whether the bound is fixed or only looks fixed
   on a device that never slept and never rotated.
2. **A4.** Whether D-POCO can host USB at all, and if so whether the USB overlay came up over a
   stuck wireless attempt.
3. **C6, conditions 1 and 3.** Whether AUTO stood down for a 2.4 GHz group, and whether the session
   then completed where S1's did not.
4. **C1 to C5.** Any change from round 7 at all, since they are this build's regression.
5. **D2.** Whether `ACTION_START_WIRELESS` armed the stack, and whether the refusal line appeared.
6. **F1 condition 3.** Whether the AAC decoder starts, now that the station leaves first.

And the standing question, one item shorter than round 7's: F1 condition 3 is the last thing between
the AAC work and losing its "(Experimental)" label, F3 having been retired as unmeasurable here.
