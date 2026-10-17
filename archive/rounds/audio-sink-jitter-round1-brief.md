# audio-sink-jitter, round 1 brief: the sink that starves itself, and the focus it hands back

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-jitter-and-instrumentation` | `0bd7782d` | 2 on `main` | 1905 tests, 0 failures |
| Baseline | `main` | `8418ed02` | | |

```bash
git fetch fork
git checkout -B audio-sink fork/fix/audio-sink-jitter-and-instrumentation   # 0bd7782d
git log --oneline -2      # 0bd7782d Transport, 443b9a64 Audio
```

**History was rewritten today.** The branch was pushed once at `c644ac6b` and force-updated about an
hour later to fold a parked-sink fix into the Audio commit. A checkout taken from the first push has
to be reset, not pulled: `git fetch fork && git reset --hard fork/fix/audio-sink-jitter-and-instrumentation`.
Both commits compile alone, checked.

Baseline APK is needed for A1, B1, C1 and every run in Part G. Everything else is candidate only.

## 2. Why this round exists

Audio stuttering is reported on high-end and low-end units alike, and the capture that forced the
audit is the one where every other explanation is already dead: the link delivered 187 kB/s of the
192 kB/s that 48 kHz stereo PCM needs, all 64 throughput windows read `dropped=0 skipped=0
concealed=0`, `enqueueWait=0ms` in every one of them, zero `Feed queue full`, zero `Audio queue is
full`, one 2.7 s quiet gap in a whole session. The bytes arrive, the picture is perfect, nothing is
dropped anywhere we count, and the user hears stuttering. The fault is inside our own sink.

The mechanism is arithmetic rather than a race. `AudioMixer` banked 60 ms before playing a channel
and declared it underrun below 20 ms, while one AAP PCM message is 8192 bytes, which is 2048 frames,
which is **42.7 ms**. Walk it forward on a perfectly jitter-free link and the level after each 20 ms
read runs 3840, 1920, 0, 2176, 256 ... 3712, 1792, at which point 1792 is below the 1920 floor, the
channel is declared starved and it plays **silence** until it has re-banked 60 ms, which takes two
more messages. That is a dropout roughly every third of a second, each about 85 ms long, forever,
with no network jitter at all. It is invisible in every log we hold because the mixer pads the
starved channel with zeros, so its `AudioTrack` never underruns and every framework counter reads
zero.

The candidate restates every buffering threshold in milliseconds of audio and sizes each one against
the chunk that actually arrives, adds the per-window instruments that would have answered this on day
one, re-banks a sink that has drained instead of leaving it shallow for the rest of the session,
parks a sink across a media stop instead of rebuilding it, and passes the audio latency setting to
the mixer, which had been ignoring it and using a fixed 160 ms on every device whatever the user set.
That last one is why the reporter found no combination of settings that helped.

**The second half of this round is a field report with no log.** On a phone-to-phone Native session,
a Google Maps spoken instruction paused the music, and when the instruction ended the **head unit's
own local media app** started playing instead of the projected one. Source reading gives a candidate
mechanism and Part G is written to confirm or kill it: in dynamic focus mode we hold one transient
system audio focus for as long as **any** AA audio channel is live, and we abandon it the instant the
last one stops (`AapAudio.onAudioPlaybackStopped`). A spoken instruction empties that set at least
once, because Android Auto stops the media sink for every assistant session and every pause, so there
is a window in which we hold no focus, and a local player that we paused with `AUDIOFOCUS_LOSS_TRANSIENT`
reads the abandon as its cue to resume. **The candidate does not change any of that.** Part G is a
measurement, not a fix, and it is run on both arms precisely so the answer cannot be confounded with
the sink work.

## 3. What is different about this round

- **This rig has no speaker and no 3.5 mm output, so nothing is graded by ear.** Every run below names
  its proxy. For the sink work the proxies are the new `audio sink` line and the framework underrun
  counters. For Part G the proxies are `dumpsys audio`'s focus stack on the head unit, the head unit's
  own `dumpsys media_session` playback state, and our focus log lines. "Both players are producing
  audio at once" is a readable state even with nothing to listen to, and it is the fault.
- **Part G needs a local media app on the head unit**, playing before the AA session starts, that
  holds a `MediaSession` and resumes after a transient focus loss. Name the package and version in the
  results. If nothing suitable is installed, say so and mark Part G UNTESTABLE rather than improvising
  with a player that does not auto-resume: a player that does not resume cannot show this fault.
- **A phone standing in as the head unit carries no video** (the projection activity cannot stay
  resumed in portrait, so Gearhead withholds video focus). That is expected and does not affect any
  run here: every run in this brief grades audio. Do not read `rendered=0` as a failure.
- **The nav prompt is the one trigger that may not be scriptable.** Starting navigation by intent
  normally speaks the first instruction within a few seconds of the route forming, without the unit
  moving. Mock locations are retired for this: `cmd location providers set-test-provider-location`
  drives Maps on the phone but never reaches Android Auto's own nav rendering, which consumes Play
  Services' fused location. G2 therefore has a pre-registered INCONCLUSIVE, and **G1 is the cheap
  path to the same window** because a media pause empties the channel set exactly as a prompt does.
- **There is no `PERFORMANCE_MODE` arm.** The plan called for an A/B of `NONE` against `POWER_SAVING`
  and the candidate ships no lever for it, so it cannot be run this round. Do not improvise one.
- Settings go in `shared_prefs/settings.xml` with the app stopped, per house rule 3, and
  **`enable-audio-sink` must be read back `true` before any run here**: a stale `false` from an earlier
  round leaves only the always-on system-sounds channel and no `AudioDecoder.start:` ever appears for
  music, which reads as a broken build.

## 4. Settings keys this round needs

```xml
<int name="log-level" value="0" />                    <!-- VERBOSE, required for every run -->
<boolean name="enable-audio-sink" value="true" />     <!-- read it back, see above -->
<boolean name="static-audio-focus" value="false" />   <!-- false is the default path, true the mixer -->
<int name="playback-focus-mode" value="0" />          <!-- 0 AUTO, 1 ALWAYS, 2 NEVER -->
<boolean name="playback-focus-self-defeating" value="false" />
<int name="audio-latency-multiplier" value="8" />
<int name="audio-queue-capacity" value="50" />
<boolean name="use-aac-audio" value="false" />
<boolean name="separate-audio-streams" value="false" />
<int name="debug-video-feed-hold-ms" value="0" />     <!-- E1 only, max 250 -->
```

`playback-focus-self-defeating` is a **latch written by the app**, not a preference: two media channels
closing within 5 s of a focus grab set it, and once set the app stops taking focus on this and every
later session. Read it back after every Part G run and report its value. A run where it latched
mid-round explains anything that follows, and clearing it means writing `false` back with the app
stopped.

## 5. The lines that decide every run

New on the candidate, absent on the baseline, all at INFO:

```
audio sink AUDIO over 5000ms: underruns=0, silentCycles=0, rebanks=0, dropped=0, droppedFrames=0, depth=203ms (min 158ms), queued=0, granted=15376 frames (320ms), effective=15376 frames (320ms), drift=0ms
AudioTrackWrapper: AUDIO granted N frames (Nms) of the N bytes asked for, banking Nms before playing
AudioTrackWrapper: AUDIO was granted only Nms, so it can bank Nms of the 200ms it wants - this sink will be fragile
AudioTrackWrapper: AUDIO underran, re-banking 200ms (re-bank N)
AudioTrackWrapper: AUDIO parked with Nms buffered
AudioTrackWrapper: AUDIO resumed with Nms banked after Nms
AudioTrackWrapper: AUDIO has underrun through 6 re-banks in a minute - the link is short of audio, so it is left to run shallow
AudioMixer: Started (bufferSize=N bytes = Nms, minBuffer=N, granted=N frames (Nms), latencyMultiplier=N)
AudioMixer: Registered channel N (sampleRate=48000, channels=2, bank Nms before playing)
transport dispatch over 5000ms: video=Nms, audio=Nms, other=Nms, unread=N%, blocks=N, longest=Nms on VIDEO
```

On both arms:

```
AudioTrackWrapper: playback started with N frames banked (target N) after Nms
Audio stream: 3 buffer size: N (min: N) sampleRateInHz: 48000 channelCount: 2
AudioDecoder.start: channel=6, stream=3, gain=..., isAac=false, source=setup, latencyMultiplier=8, queueCapacity=50, ...
Media Sink Setup Request: 1 on channel AUDIO
Audio Stop: AUDIO
Audio queue is full, dropping audio frame to prevent stalling
AudioTrack: ... disabled due to previous underrun, restarting
AapAudio: AA audio started (AUDIO) - acquiring transient system audio focus (mode=AUTO)
AapAudio: AA audio started (AUDIO) - leaving system audio focus alone (...)
AapAudio: last AA audio channel stopped - releasing transient system audio focus
AapAudio: Releasing playback transient audio focus
AapAudio: Playback transient focus request result: GRANTED
AapAudio: playback audio focus changed: N
AapAudio: media stopped Nms after taking audio focus (N/2)
```

On the **mixer path** (`static-audio-focus=true`) this wrapper owns no `AudioTrack`, so the `audio
sink` line's `underruns`, `granted`, `effective` and `drift` fields are all zero by construction and
carry no information. `silentCycles`, `rebanks`, `depth` and `queued` are the live ones there, and
`silentCycles` is the only place a listener's gap shows up at all, because the mixer pads a starved
channel with zeros. On the default path it is the other way round: `silentCycles` stays zero and
`underruns` is the real counter.

`playback audio focus changed:` prints the raw framework constant: **1** is GAIN, **-1** LOSS, **-2**
LOSS_TRANSIENT, **-3** LOSS_TRANSIENT_CAN_DUCK. Quote the number, not a word for it.

Channel names in these lines: `AUDIO` is media, `AUDIO1` is guidance and speech, `AUDIO2` is system
sounds. The media channel is the one Part G is about. The setup type on an audio channel is the codec
the phone asked for: **1** is PCM, **2** and **4** are AAC LC, and anything else leaves the sink on
whatever `use-aac-audio` says, which `AudioDecoder.start` reports as `source=setting`.

## 6. Runs

### R0. Gate

Both arms. Build both APKs, record both md5s, and check the candidate APK actually carries this work
rather than an older build with the same name:

```bash
unzip -p app-github-debug.apk classes*.dex | strings | grep -c "re-banking"      # candidate: >= 1
unzip -p app-github-debug.apk classes*.dex | strings | grep -c "transport dispatch over"   # candidate: >= 1
./gradlew :app:testGithubDebugUnitTest                                          # 1905, 0 failures
```

**PASS:** both greps non-zero on the candidate and zero on the baseline, and the test gate clean.
A failed gate is the one thing that stops the round (house rule 4).

### A1. The point of the round: the mixer stops starving itself

`static-audio-focus=true`, `audio-latency-multiplier=8`, media playing from the phone for **five
unbroken minutes**, both arms. This is the configuration the reporter was in, and the fault is
supposed to be continuous, so five minutes is many hundreds of dropouts if it is still there.

Baseline carries no instrument at all here, so grade it by what it prints and by the absence:
`AudioMixer: Started (bufferSize=30720, minBuffer=...)` with **no** `latencyMultiplier=` field and no
`audio sink` line anywhere. That absence is itself the finding worth reporting, since it is why four
investigations in a row measured the link.

Candidate, per 5 s window on channel 6:

| Check | Condition |
|---|---|
| the starvation is gone | `silentCycles=0` in **every** window after the first |
| the cushion holds | `depth=` at or above 120 ms in every window, `min` never 0 ms |
| the setting reaches the mixer | `AudioMixer: Started` carries `latencyMultiplier=8` and a `granted=` in frames and ms |
| the bank is sized to the chunk | `Registered channel 6` says `bank` 150 ms or more, not 60 ms |

The 120 ms floor is deliberately below the 200 ms target: one arrival chunk is 42.7 ms, so a healthy
channel oscillates in a band about that wide under its target and a trough near 157 ms is correct
behaviour, not a near miss.

**PASS:** all four. **FAIL:** any window after the first with `silentCycles` above zero, or a `min`
depth of 0 ms. Report the highest `silentCycles` seen and the lowest `min` depth as numbers.

### A2. The latency setting means something on the mixer path

Candidate only, `static-audio-focus=true`. Two sessions, app stopped between them, one with
`audio-latency-multiplier=2` and one with `8`. Read `AudioMixer: Started`.

**PASS:** the `granted=` figure at 8 is larger than at 2, and both print their multiplier. **FAIL:**
both print the same buffer, which would mean the multiplier is still not reaching the mixer. Report
both lines verbatim.

### B1. The default path does not run permanently shallow

`static-audio-focus=false`, five minutes of media, both arms. The default path had no re-bank at all:
one underrun drained the track and it stayed drained for the session. Count on each arm:

```bash
grep -c "disabled due to previous underrun" log.txt
grep -oE "underrunframes=[0-9]+" log.txt          # framework teardown counter, divide by 48000
grep "audio sink AUDIO over" log.txt | tail -20   # candidate only
```

**PASS:** candidate's underrun count per minute is at or below the baseline's, **and** the candidate's
`depth=` recovers to 120 ms or more within 5 s of every `re-banking` line. **FAIL:** the candidate
underruns more often than the baseline, or a `re-banking` line is never followed by a recovered depth.
Report both arms' underruns per minute, computed over the log's own span.

### B2. A gap in the link is survived rather than made permanent

Candidate only. Media playing and healthy, then interrupt the link for about 3 s and let it come back.
Use whichever lever this rig has that costs the session no teardown; if the only available lever kills
the session outright, say so and mark this INCONCLUSIVE rather than grading a reconnect.

**PASS:** a `re-banking` line, then `resumed with Nms banked`, then `depth=` back at target in the next
window or the one after, and audio keeps flowing for the rest of the session. **FAIL:** `depth=` stays
below 100 ms for the remainder of the session, which is the old permanent-shallow behaviour.
Pre-registered INCONCLUSIVE if the gap tears the session down.

### C1. A pause no longer rebuilds the sink

`static-audio-focus=false`, both arms. Media playing, then pause from the head unit and resume after
about 10 s. **Media keys do not open or resume an AAP audio channel on this rig; the tap on the
projected media widget does** (`input tap 272 657` on the 1440x720 layout), so drive it with the tap
and take timings after the first play tap of a cold session has stopped flapping.

Baseline: expect `Audio Stop: AUDIO`, then a fresh `AudioDecoder.start: channel=6` and a fresh
`playback started ... after Nms` on resume, which is the rebuild plus a fresh pre-roll.

Candidate: expect `Audio Stop: AUDIO`, then `AUDIO parked with Nms buffered`, then on resume
`AUDIO resumed with Nms banked after Nms` and **no** second `AudioDecoder.start: channel=6` and no
second `playback started` line for that channel in the same session.

**PASS:** the candidate prints park and resume with no rebuild, and `resumed ... after` is under
400 ms. **FAIL:** a second `AudioDecoder.start: channel=6` on the candidate, or no `resumed` line
within 3 s of audio returning, which would be a sink parked and never restarted. That second failure
mode is the one to look hardest for: it is silence, and this rig cannot hear it.

### C2. A sink parked, then fed something shorter than the cushion

Candidate only, `static-audio-focus=false`: the speech channels are only parked on the default path,
the mixer path keeps them and just restores the media volume. The reason this run exists is that the
resume waits for 200 ms of banked audio
before it plays, so a prompt shorter than that would sit in the buffer until some later stream filled
it. The deadline that prevents it is new in this build and untested on hardware.

Park the **guidance** channel by letting one spoken instruction or system sound finish, then trigger a
second short one. Any `AUDIO1` or `AUDIO2` traffic does: a notification, a Maps chime, an assistant
acknowledgement.

**PASS:** every `AUDIO1 parked` is followed by an `AUDIO1 resumed with Nms banked after Nms` whose
`after` is at or below 300 ms, and the banked figure may legitimately be below 200 ms, that is the
deadline working. **FAIL:** a park with no matching resume inside 3 s of the next audio on that
channel. Pre-registered INCONCLUSIVE if no `AUDIO1` or `AUDIO2` traffic occurs at all.

### D1. AAC, which has never run on hardware

Candidate only, `use-aac-audio=true`, one session of at least three minutes with music and, if it
happens naturally, one spoken instruction. This path is on by default for any 2.4 GHz link on the
released builds and has never been exercised on a rig.

**PASS:** `AudioDecoder.start` shows `isAac=true`, the `audio sink` line shows `droppedFrames=0` and
a `depth=` that holds, and neither `the AAC sink cannot keep up` nor `AAC Input Buffer timeout` appears.
**FAIL:** either of those two lines, or `droppedFrames` above zero in more than one window. Report the
codec source field (`source=setup` or `source=setting`) as well, since the phone's own setup message
overrides the setting.

### E1. Head of line, and the gate on whether the demux gets built

Candidate only. `debug-video-feed-hold-ms=200`, one session with media playing, three minutes.

This forces the video feed queue to back up. The question is whether a video stall reaches through to
audio: `AapMessageHandlerType` dispatches every channel synchronously on the one read thread, and the
video feed can park that thread for up to a second. It has never been observed doing so, which is why
the transport demux was **not** built on this branch, and `transport dispatch over` is the instrument
that decides whether it should be.

Report, from every window: `blocks=`, `longest=` with its channel, and the audio numbers beside it.

**PASS is not the question here**, this run is a measurement. What decides the follow-up work: if
`blocks` is above zero with `longest` on VIDEO **and** the `audio sink` line degrades in the same
window, the demux is justified and it gets built. If `blocks=0` throughout even with the hold forced,
the hazard stays theoretical and the demux stays unbuilt. Say which of the two happened.

### G1. The focus window, driven by a pause

**Both arms.** The cheap, fully scriptable version of the field report, and it must run even if G2
cannot. Setup, in this order:

1. Start the local media app on the **head unit** and let it play. Record its package.
2. Bring up the AA session and start media on the phone. Our grab should pause the local app.
3. Confirm the state before touching anything: our focus line says acquired, and the local app's
   `PlaybackState` is paused.
4. Pause the projected media from the head unit, wait about 5 s, resume it.

Sample throughout, once a second, into a file:

```bash
for i in $(seq 1 90); do
  echo "== $(date +%H:%M:%S.%N)"
  adb -s <hu> shell dumpsys media_session | grep -iE "package=|PlaybackState \{"
  adb -s <hu> shell dumpsys audio | grep -A 12 -i "focus stack"
  sleep 1
done > g1-focus.txt
```

What is being graded is the window, not an opinion about it:

| Check | What to record |
|---|---|
| we let go | timestamp of `last AA audio channel stopped - releasing transient system audio focus` |
| the local app woke | timestamp of the local package's `PlaybackState` going to playing, if it does |
| the gap | milliseconds between those two |
| who is on top | the focus stack owner at each sample through the window |
| we took it back | timestamp of the next `acquiring transient system audio focus` |
| both at once | any sample where the local app is playing **and** the `audio sink AUDIO` line for that window shows a live depth |

**FAIL, meaning the field report reproduces:** the local app goes to playing within about 2 s of our
release. **PASS:** the local app stays paused across the whole window. Expect the same verdict on both
arms; if the arms differ, that is a finding in its own right and the candidate is the one that has to
explain itself.

### G2. The field report verbatim: a spoken instruction

**Both arms.** Same setup as G1, but instead of a pause, trigger a Maps spoken instruction on the
projecting phone:

```bash
adb -s <phone> shell am start -a android.intent.action.VIEW -d "google.navigation:q=<lat>,<lon>&mode=d"
```

Route start normally speaks one instruction within a few seconds without the unit moving. For a second
prompt, issue the intent again with a different destination rather than trying to make the first route
speak twice.

Confirm the prompt actually reached us before grading anything: `AudioDecoder.start: channel=4` or any
`AUDIO1` line in the window. **Pre-registered INCONCLUSIVE:** no `AUDIO1` traffic at all, which means
the prompt never got as far as our sink and this run measured nothing.

Then the same table as G1, plus one row: **did the projected media come back?** Record whether an
`audio sink AUDIO over` window after the prompt shows a live depth again, and whether `Audio Stop:
AUDIO` was followed by media traffic on channel 6. The field report's second half is that it did not,
and that the local app played instead, so this row is the one that separates "the local app stole the
sound" from "the phone never resumed its own media".

**FAIL:** the local app is playing after the prompt ends. Report how many of the prompts in the run
ended that way, as a count out of the total.

### G3. Is it our release at all? Turn the grab off

Candidate only, `playback-focus-mode=2` (NEVER). Repeat G1 exactly.

With the grab suppressed there is no transient loss, so the local app is never paused at all: expect
it playing for the whole session, alongside the projected audio, and **no** `acquiring transient
system audio focus` and no `releasing transient system audio focus` line anywhere. The line that
should appear instead is `leaving system audio focus alone (mode=NEVER)`.

**The point is the negative.** If the local app is paused at some point in this run anyway, then
something other than our grab is pausing it and Part G's hypothesis is dead, which is the most
valuable outcome this round can produce. Report which it was, and quote the line that pauses it if
one appears.

### G4. Does holding focus for the whole session avoid it?

Candidate only, `static-audio-focus=true`, `playback-focus-mode=1` (ALWAYS). Repeat G1.

ALWAYS rather than AUTO on purpose: on the static path AUTO refuses the permanent grab whenever a
Bluetooth media link is connected, which on this rig is any paired phone, so AUTO here would measure
the refusal rather than the behaviour.

Static mode takes one permanent focus at connect and holds it for the session, so there is no
per-channel release and no window. Expect zero `releasing transient system audio focus` lines and the
local app paused throughout.

**PASS:** the local app never resumes. This run is what tells us whether a hold-off on the dynamic
path would fix it, because static mode is that behaviour taken to its limit. **FAIL:** the local app
resumes anyway, which would mean the release is not the trigger and something else wakes it.

### G5. The control: no local player at all

Candidate only. Force-stop the local media app before the session, then repeat G1's pause.

This separates our sink failing to resume from the local app taking the sound. **PASS:** the projected
media resumes normally after the pause, `audio sink AUDIO` shows a live depth again within 3 s, and
the `resumed with Nms banked` line is there. **FAIL:** the projected media does not come back even
with nothing competing for it, which would move the whole investigation off focus and onto the sink.

### G6. How often, and does it persist

Candidate only, one session, at least **ten** pause/resume cycles as in G1, spaced about 30 s apart.
Report as a count out of ten: how many cycles ended with the local app playing, and whether the fault
is every cycle or intermittent. Also read `playback-focus-self-defeating` back at the end: if the
latch fired during this run, say at which cycle, because every cycle after it is a different
configuration from every cycle before it.

### G7. Where the media keys go afterwards

Candidate only, run immediately after a G6 cycle that ended with the local app playing. Send
`adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE` at the head unit and record which player answers,
from `dumpsys media_session`'s active session.

The app holds a `MediaSession` and relays these keys to the phone over AAP. If the local app has
become the active session, the keys stop reaching the projected phone, which would make the fault
sticky rather than a momentary wrong sound. This is a consequence check, not a pass/fail: report what
answered.

## 7. Do not re-run

- **`PERFORMANCE_MODE`.** No lever ships in this build. See section 3.
- **The transport demux.** Not on this branch. E1 is the measurement that decides whether it is worth
  building, so do not look for behaviour changes in video dispatch.
- **Anything about the link.** Band, station scans, throughput and the periodic-scan mechanism are
  settled in their own threads, and this round grades the sink on a link that is healthy. If the link
  is short during a run, note it and re-run rather than grading it.
- **Mock locations for nav prompts.** Retired: it drives Maps on the phone and never reaches Android
  Auto's nav rendering.

## 8. Report back

Five numbers and two sentences:

1. A1's highest `silentCycles` and lowest `min` depth on the candidate, and whether the baseline
   printed any instrument at all.
2. B1's underruns per minute on each arm, over the log's own span.
3. C1's `resumed ... after Nms`, and whether any `AudioDecoder.start: channel=6` appeared after a
   resume.
4. E1's `blocks=` and `longest=` with its channel, which decides whether the demux gets built.
5. G1 and G2's gap in milliseconds between our release and the local app resuming, and the count of
   cycles that ended with the local app playing.

Then: one sentence on whether the field report reproduced on this rig, and one on whether the two arms
differed anywhere in Part G. If Part G could not run for want of a local media app, say that instead
and the sink runs still stand on their own.
