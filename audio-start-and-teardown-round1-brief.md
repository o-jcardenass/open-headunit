# audio-start-and-teardown, round 1 brief: an AudioTrack that plays before it has anything to play

**Baseline (A):** `origin/main` @ `ea7aa7e0` (3.3.0-beta1, `andreknieriem/open-headunit`).
**Candidate (B):** `fork/fix/audio-start-and-teardown` @ `3d3fe465` (3 commits on top of `ea7aa7e0`).

> **Round 1 is done and this brief is the record of what was run. Do not re-run it from the branch.**
> `3d3fe465` is no longer the branch tip: the WiFi Direct group bring-up commit was dropped on
> 2026-08-25 and the branch is now `455e76e0`, two commits, with `WifiDirectManager.kt` identical to
> `main`. **R5 and R6 tested that commit and no longer apply to anything on the branch.** The SHA
> above stays reachable as tag `audio-round1-validated`, which is the APK
> `audio-start-and-teardown-round1-results.md` measured.

```bash
git fetch origin && git fetch fork
git checkout -B main-a         origin/main                      # must print ea7aa7e0...
git checkout -B audio-teardown fork/fix/audio-start-and-teardown # must print 3d3fe465...
git rev-parse HEAD
```

**History was rewritten since this branch was first pushed.** It was rebased from `e33fd65f` onto
`ea7aa7e0` so the two arms differ by these three commits and nothing else. Fetch and reset; do not
pull.

| Commit | What it changes |
|---|---|
| `8d619287` | `AudioTrackWrapper` pre-roll and drain, `MicRecorder` capture summary |
| `fec59ade` | `WifiDirectManager` one Native AA group per bring-up request |
| `3d3fe465` | `LinkGapMonitor` / `AapTransport` audio-series gate |

This is a **new thread**. Nothing else on this branch needs reading.

Two APKs. Every run names its arm.

---

## 1. Why this round exists

A reporter on a Spreadtrum `sp7731e_1h10` (Android 8.1, API 27) says music lags "right after playing
and pausing". His 16-minute capture explains it exactly, and the mechanism is arithmetic rather than
hardware.

`AudioTrackWrapper` called `track.play()` inside `init`, before the run loop had written a byte. At
his `audio-latency-multiplier` of 8 the media buffer is 134144 bytes, which is 33536 frames, which is
**699 ms** at 48 kHz stereo. Playback was already running while that filled from empty. All five
48 kHz media starts in his capture underran, at 657, 693, 634, 960 and 588 ms after their
`Media Start Request AUDIO`. That is 100 percent, and the media track is rebuilt on every
`Media Start Request`, so it fires on every pause/resume and on both sides of every Assistant
session.

Behind it, `cleanup()` spun up to 2500 ms waiting for `playbackHeadPosition >= framesWritten`, and
both of its escapes were unreachable because the loop read the position **after** `track.stop()` had
zeroed it. Measured on his capture: the full budget every clean teardown, 2525 / 2603 / 2522 ms. When
Android Auto restarted the channel inside that window the sleep was interrupted, `release()` ran
immediately and truncated the tail, and the app logged the normal restart at `E` with a Java stack
trace.

Two more defects came out of the same capture and are in this round because they are cheap to measure
alongside:

- **Group churn from a racing manual poke.** `startNativeAaQuietHost()` had no in-flight guard.
  His session called it twice 175 ms apart, from the service's own bring-up and from
  `manualPoke -> triggerWifiDirectRefresh`, produced two `createGroup` calls 4 ms apart (one SUCCESS,
  one BUSY), and the retry ladder built a **second group 4.15 s later** while the phone was mid
  handshake on the first. `p2p-wlan0-0 -> -1 -> -2` in one session, and 11 credential deliveries
  instead of the usual 4. It survived only because both groups happened to get the same SSID and
  BSSID.
- **The audio gap monitor counted an Assistant session as an outage.** His one
  `inbound audio quiet 3 times in 30220ms: dead=10937ms (36%)` covers a voice session during which
  Android Auto legitimately sent `Media Sink Stop Request: AUDIO`. That series is the instrument the
  whole "is this a link fault or a decoder fault" reading rests on, so it has to mean what it says.

**None of this is a regression.** The audio path is byte-identical from v.3.2.4 through 3.3.0-beta1.
The defect has always been there; his multiplier of 8 makes its window widest.

---

## 2. What the candidate does, and the numbers that follow from it

**Pre-roll.** `track.play()` is out of `init`. `AudioPrerollPolicy` decides when to start:

| Constant | Value | Meaning |
|---|---|---|
| `TARGET_MS` | 200 | how much audio to bank before playing |
| `MAX_FILL_NUMERATOR` / `DENOMINATOR` | 3/4 | the target never exceeds three quarters of the track buffer, so a `write()` cannot block the thread that would have called `play()` |
| `MAX_WAIT_MS` | 300 | a stream that only ever gets a trickle still sounds |

The target is in **frames**, so it scales with the multiplier and covers the 16 kHz channels on the
same rule. Capacity is read from `getBufferSizeInFrames()` where the API has it, not from the
requested byte count, because `setBufferSizeInBytes` is a request the framework may clamp.

**Drain.** `cleanup()` samples `playbackHeadPosition` **before** `stop()` zeroes it, converts the
unplayed frames to milliseconds, and sleeps exactly that long, capped at `DRAIN_CAP_MS = 1000`. The
interrupted case is now one INFO line naming the cause instead of an `E` with a stack trace.

**Group bring-up.** `NativeGroupBringUpPolicy.COALESCE_WINDOW_MS = 40000`. A second call inside a
live bring-up is folded into it rather than starting a competing one, and runs once when the first
finishes. Superseded framework callbacks are ignored by generation, and `stop()` clears the guard.

**Audio gap series.** `LinkGapMonitor.skipExpectedGap()` shifts the window past a silence Android
Auto asked for. It shifts rather than resets, so the window keeps what it already measured; a reset
would have restarted the 30 s window on every track skip and silenced the series for anyone using
the transport controls.

**Not testable, no run:** the third commit also adds a paragraph to `VideoFeedThrottlePolicy`'s KDoc
recording that the paced read thread carries audio too. No behaviour change.

---

## 3. What is different about this round

- **This is an audio round, so the phone has to be playing something for most of it.** Use the media
  transport keys (`TESTING-TEMPLATE.md` §3) rather than the phone's screen. Note §7a: media keys
  alone do not open a fresh audio channel, so a run that needs a **new** track build has to
  force-stop and relaunch the media app on the phone.
- **`log-level` is `1` (DEBUG), not VERBOSE.** No `RECV:` line decides anything here. One candidate
  line prints at DEBUG (`ignoring a superseded bring-up outcome`); everything else is INFO or a
  system line.
- **The underruns are system lines from AudioFlinger, not ours.** They survive any app log level, and
  they are what makes an A/B possible at all. They are also the run's main risk: if this rig's
  AudioFlinger does not underrun on an empty-buffer start, arm A produces no signal. **R1 has a
  pre-registered fallback for exactly that (§5), and it is not a FAIL.**
- **R5 suspends the standard discard rule.** `TESTING-TEMPLATE.md` §7a says a second
  `createGroup SUCCESS` in one run means discard. In R5 that count **is** the measurement. Do not
  discard R5 for it; report it.
- **R5 needs the manual-poke intent, not the picker** (§7a's third poke recipe). It reaches
  `pokeDevice()` directly, and its `triggerWifiDirectRefresh` is one of the two racers.
- **The rig is permanently associated to a WiFi network.** Nothing here depends on that either way.
  Report the association in Setup notes as usual.

---

## 4. Settings keys

| Key | Type | Value | Runs |
|---|---|---|---|
| `log-level` | int | `1` (DEBUG) | all |
| `log-source` | int | `0` (LOGCAT) | all |
| `audio-latency-multiplier` | int | `8` | all except R2b |
| `audio-latency-multiplier` | int | `1` | R2b only |
| `audio-queue-capacity` | int | `50` | all |
| `static-audio-focus` | bool | `false` | all |
| `wifi-connection-mode` | int | `3` (Native AA) | all |

Ready to paste:

```
<int name="log-level" value="1" />
<int name="log-source" value="0" />
<int name="audio-latency-multiplier" value="8" />
<int name="audio-queue-capacity" value="50" />
<boolean name="static-audio-focus" value="false" />
<int name="wifi-connection-mode" value="3" />
```

`static-audio-focus=false` is written explicitly. With it on, `AudioMixer` is in the path and it
already pre-rolls 60 ms of its own, which would mask the defect entirely. The reporter's capture has
no `AudioMixer` line in it; his path is the mixer-less default, and so is this round's.

Confirm the buffer arithmetic actually reached the track. This prints at INFO on both arms:

```
AudioDecoder.start: channel=6, ... latencyMultiplier=8, queueCapacity=50, ...
Audio stream: 3 buffer size: <bytes> (min: <bytes>) sampleRateInHz: 48000 channelCount: 2
```

Report both numbers in Setup notes. Every expected value below is derived from them, and
`getMinBufferSize` is device-specific, so a rig whose media buffer is nothing like 134144 bytes at
multiplier 8 changes what R1 and R2 should show.

---

## 5. The lines that decide the round

Candidate only:

```
AudioTrackWrapper: playback started with <N> frames banked (target <M>) after <X>ms
AudioTrackWrapper: <N>ms drain cut short by a restart
MicRecorder: capture summary | source=<name> (<n>) rate=<hz> elapsed=<ms>ms bytes=<n> (<p>% of expected) emptyReads=<n> peak=<n>/32767
WifiDirectManager: a group refresh was folded into the last bring-up (<outcome>) - running it now.
WifiDirectManager: ignoring a superseded bring-up outcome (<outcome>)
```

Baseline only:

```
Error during audio track cleanup
```

Both arms:

```
Media Start Request AUDIO: session=<n>, config_index=<n>
Media Sink Stop Request: AUDIO
AudioTrackWrapper thread finished.
MicRecorder: Stopping. Active: true
inbound audio quiet <n> times in <ms>ms: dead=<ms>ms (<p>%), longest=<ms>ms
createGroup SUCCESS
```

System line, both arms, and the one the A/B rests on:

```
disabled due to previous underrun
```

Grep everything with `-a` (§7a), no exceptions.

---

## 6. Runs

### R0. Gate

Build and unit-test **both** arms (`build_hur.sh`, `run_unit_tests.sh`). Record suite counts and,
separately, these three:

| Suite | Expected on B |
|---|---|
| `AudioPrerollPolicyTest` | 12 |
| `NativeGroupBringUpPolicyTest` | 4 |
| `LinkGapMonitorTest` | 18 (17 on A) |

`AudioPrerollPolicyTest` is the first unit test in this repo to touch the audio path at all, so a
missing-suite result here means the build did not pick up the new source set, not that the tests
passed.

**PASS:** both arms compile, both suites clean, all three named suites present at those counts. A
failure on either arm stops the round.

### R1. The point of the round: does a media start still underrun?

Arm A, then arm B, identical procedure. One session each, Native AA, music playing on the phone.

1. Session up, video on screen, music playing, settled for 30 s.
2. Ten `KEYCODE_MEDIA_PLAY_PAUSE` cycles, roughly 8 s apart, alternating pause and resume. Script it.
3. Keep capturing 30 s after the last one.

For every `Media Start Request AUDIO`, look for a `disabled due to previous underrun` within the
next **1500 ms**. Report the count of starts, the count of starts with an underrun attached, and each
delta in milliseconds.

- **PASS:** arm A shows underruns attached to media starts (the reporter's rate is 5 of 5) and arm B
  shows **zero** attached to a start. Underruns elsewhere in either arm are not this defect; report
  them with their timestamps and say what else was happening.
- **FAIL:** arm B still attaches an underrun to a start. Report `frames banked`, `target` and the
  `after Xms` from the `playback started` line for that start, which says whether the pre-roll ran
  and was too small or never ran at all.
- **Pre-registered INCONCLUSIVE, not a FAIL:** arm A attaches **zero** underruns to its ten starts.
  Then this rig's AudioFlinger does not report the fault and the efficacy question is unanswerable
  here. Say so, and score R2 instead, which needs only arm B.

Both arms, either way: report the **count of `Media Start Request AUDIO`**. Ten cycles that produced
two track builds means media keys were resumed on the same channel rather than a new one, and the
run measured almost nothing. If the count is below eight, add the phone-side media-app force-stop
from §7a between cycles and re-run.

### R2. The pre-roll scales with the buffer, and never exceeds it

Arm B only. This is the positive control and it needs no baseline APK.

- **R2a:** `audio-latency-multiplier=8`, one session, one media start.
- **R2b:** `audio-latency-multiplier=1`, app stopped between the two, one session, one media start.

From the `playback started` line and the `Audio stream:` line, for each:

| Check | Condition |
|---|---|
| target below capacity | `target` <= 3/4 of (`buffer size` / 4) frames, in both |
| target scales | R2b's `target` is **lower** than R2a's, or both are 9600 with R2b's capped by 3/4 |
| pre-roll happened | `frames banked` >= `target` |
| cost is bounded | `after Xms` <= 300 in both |

At 48 kHz stereo, 200 ms is 9600 frames. R2a's target should be 9600 unless three quarters of the
track capacity is less than that; R2b's capacity is one eighth of R2a's, so its target should be the
3/4 cap and visibly smaller. **If both runs print the same target, the cap is not engaging and that
is a FAIL**, because it means the anti-deadlock margin is not being applied on the small buffer.

Also confirm the 16 kHz channels: any `playback started` line on AUDIO1 or AUDIO2 must show
`target` <= 3/4 of that channel's own capacity. If no voice or navigation audio occurs naturally in
these runs, say so and route that coverage to R4.

**PASS:** all four checks in both runs, and no `Audio queue is full` line in either.

### R3. Teardown ends when the audio does

Both arms. Free from R1's captures if the pause/resume cycles produced clean teardowns; otherwise
one extra session per arm ending in a scripted `headunit://disconnect`.

For each `Media Sink Stop Request: AUDIO`, measure to the next `AudioTrackWrapper thread finished.`

- **PASS:** arm A sits at roughly 2500 ms on every clean teardown (the reporter's were 2525, 2603,
  2522). Arm B is **under 1100 ms** on every one, and typically far under, since it sleeps only for
  the frames still unplayed and caps at 1000 ms.
- Arm B must have **zero** `Error during audio track cleanup`. That string does not exist in arm B's
  source; if it appears, the wrong APK is installed.
- If a teardown is interrupted by a restart on arm B, it prints
  `<N>ms drain cut short by a restart` at INFO. Report how many, with their `<N>`. Zero is fine.

Report the mean and the maximum for each arm, not an adjective.

### R4. The mic says what it captured

Arm B only. Two Assistant sessions: press-and-hold or the voice button, say something audible for
about five seconds, let it finish. Then one more with the microphone physically covered or the room
silent.

Every `MicRecorder: Stopping. Active: true` must now be followed by a
`MicRecorder: capture summary` line.

- **PASS:** the summary is present on all three, `bytes` is non-zero, `% of expected` is above 90,
  `emptyReads` is 0 or in the low single digits, and `peak` is **high on the two spoken sessions and
  low on the silent one**. That contrast is the whole point of the instrument: it is what separates
  "the mic opened and captured nothing" from "the mic opened and worked".
- **FAIL:** the summary is missing, or `% of expected` is far below 90 on a spoken session.
- If the Assistant cannot be triggered on this rig, report **UNTESTABLE** and say what was tried.
  Do not substitute another audio source; the summary only prints from `MicRecorder`.

Report `source=` verbatim. The reporter runs `AudioSource.DEFAULT` (0), and if the rig runs something
else his numbers and these are not comparable.

### R5. One group per bring-up request

Both arms. This is the run that reproduces the reporter's group churn, and it needs the two calls to
race.

Per arm, five attempts. For each:

```bash
adb shell am force-stop com.andrerinas.headunitrevived
adb shell logcat -c
stdbuf -oL adb shell logcat -v threadtime > r5-<arm>-<n>.txt &
# launch the app, then fire the manual poke immediately, no sleep between them
adb shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity
adb shell am start-foreground-service -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService \
  -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es extra_mac "<phone MAC>"
# let it run 90 s, then stop the capture
```

The adb round trip is roughly the 175 ms that produced the reporter's race. Report the wall-clock gap
between the two commands for each attempt; if it is over a second on this rig, the race is not being
set up and R5 is INCONCLUSIVE rather than PASS.

Per attempt, count:

```bash
grep -ac "createGroup SUCCESS"  r5-<arm>-<n>.txt
grep -ac "BUSY"                 r5-<arm>-<n>.txt
grep -ao "p2p-wlan0-[0-9]*"     r5-<arm>-<n>.txt | sort -u
grep -ac "SUCCESS - Providing credentials" r5-<arm>-<n>.txt
```

- **PASS:** arm B shows **exactly one** `createGroup SUCCESS` per attempt, at most one `p2p-wlan0-N`
  index, and credential deliveries in the usual 3 to 4 range rather than the reporter's 11. Arm A is
  expected to show two of at least one of those on some attempts; if it never does, the race did not
  set up here and both arms are INCONCLUSIVE together.
- **FAIL:** arm B shows two groups on any attempt.
- Report every `a group refresh was folded into the last bring-up` and
  `ignoring a superseded bring-up outcome` line with its `(<outcome>)`. A fold with a session still
  forming afterwards is the fix working. A fold with **no** session forming afterwards is the failure
  mode this guard could introduce, and matters more than the counts.

**Do not discard an R5 attempt for a second `createGroup SUCCESS`.** That is the measurement.

### R6. The guard does not latch

Arm B only, and it is the risk this round is really underwriting. This repo has a standing pattern of
flags set in one direction only, and a bring-up guard that is never cleared makes Native AA dead
until the process restarts.

Five consecutive connect / disconnect cycles in **one process**, no force-stop between them:

```bash
# per cycle: bring a session up, confirm video, then
adb shell am start -a android.intent.action.VIEW -d "headunit://disconnect"
# wait for the session to close, then reconnect
```

- **PASS:** all five cycles form a full session, each with its own `createGroup SUCCESS`, and cycle
  five is no slower to first frame than cycle one. Report first-frame time for each.
- **FAIL:** any cycle after the first fails to bring a group up, or the app forms a session only
  after a force-stop. Attach the whole capture.

### R7. A paused sink is not an outage

Arm B only. One session, 12 minutes, video on screen with the map moving throughout, and audio going
quiet three ways:

1. Pause music for 3 minutes with everything else running.
2. Resume, then one Assistant session.
3. Resume, then skip four tracks in quick succession with `KEYCODE_MEDIA_NEXT`.

- **PASS:** **zero** `inbound audio quiet` lines across the whole session.
- **Pair the count with the proof it was reachable.** Report the count of `inbound video quiet` and
  `inbound link quiet` lines and the throughput windows for the same period. Zero audio-quiet lines
  on a session where video was also silent proves nothing; the run only counts if video and link
  series were live and reporting normally while audio was legitimately stopped. Report the video
  fps alongside.
- Also confirm the window did not stall: after the 3-minute pause, the next `inbound audio quiet`
  **would** be expected if a real audio outage happened later. There is no way to force one here, so
  this half is INCONCLUSIVE by construction and is covered by `LinkGapMonitorTest`. Say so; do not
  invent a substitute.

### R8. Clean control

Arm B, one uninterrupted 10-minute session, music playing throughout, nothing touched.

Every one of these must be zero:

```bash
grep -ac "disabled due to previous underrun"      r8.txt
grep -ac "Audio queue is full"                    r8.txt
grep -ac "inbound audio quiet"                    r8.txt
grep -ac "drain cut short by a restart"           r8.txt
grep -ac "ignoring a superseded bring-up outcome" r8.txt
```

and `createGroup SUCCESS` is 1.

Pair it with the numbers that prove the session was real: throughput fps per window, the count of
`Media Start Request AUDIO`, and that audio was audible for the full ten minutes. A silent session
scores five zeroes and means nothing.

**PASS:** all five zero, one group, audio continuous, video steady.

---

## 7. Do not re-run

Nothing. This is the thread's first round.

---

## 8. Report back

`audio-start-and-teardown-round1-results.md`, in `TESTING-TEMPLATE.md` §7's format.

The three numbers that decide whether this ships:

1. **R1:** starts with an underrun attached, arm A versus arm B. The reporter's arm-A rate is 5 of 5.
2. **R3:** mean teardown time, arm A versus arm B. The reporter's arm-A figure is about 2500 ms.
3. **R5:** `createGroup SUCCESS` per attempt, arm A versus arm B.

Plus the one that decides whether it is safe: **R6**, five cycles in one process, all forming a
session.

State in Setup notes the `Audio stream:` buffer size and `AudioDecoder.start` multiplier that every
expected value here is derived from, and the `settings.xml` delta at the start of the round even if
it is zero.
