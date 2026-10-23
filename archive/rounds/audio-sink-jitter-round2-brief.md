# audio-sink-jitter, round 2 brief: the dial that now moves something, on the pairing you can hear

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-jitter-and-instrumentation` | `d292ec52` | 4 on `main` | 1914 tests, 0 failures |
| Baseline | `main` | `8418ed02` | | |

```bash
git fetch fork
git checkout -B audio-sink fork/fix/audio-sink-jitter-and-instrumentation   # d292ec52
git log --oneline -4    # d292ec52 Transport, 4c4360c9 Audio, 0bd7782d Transport, 443b9a64 Audio
```

**History was not rewritten.** Round 1's two commits are untouched underneath; this round adds two on
top, so a round 1 checkout fast-forwards. All four compile alone, checked.

Baseline APK is needed for P2, H2 and Part G. Everything else is candidate only.

## 2. Why this round exists

Round 1 graded ten runs and answered the two that mattered: the sink recovers from a gap instead of
running shallow for the rest of the session (B2), and an ordinary pause parks and resumes the track
instead of rebuilding it (C1). It also FAILed two, and the capture carried three findings nobody
graded. All five are fixed on the two new commits.

**The dial did nothing a listener could hear, on either path.** A2 caught half of it: the mixer's
shared output track is built by whichever Media Sink Setup arrives first, which is a prompt channel
capped at 4, so the media channel's setting never reached it. The capture carried the other half. The
cushion itself read **200 ms in all 192 instrumented windows**, at every setting, on both paths,
including windows carrying 24 underruns. The setting sized a buffer and nothing filled it. The
cushion is now derived from the setting, the shared mixer track is sized from the user's uncapped
value, and the picker has a deeper entry than it had.

**Two policies had no caller at all.** The effective-size growth was written, unit tested and never
wired; it is deleted, because running below the granted size trades robustness for latency and a
network fed sink wants the opposite. The queue floor and its drop-oldest rule were also written and
never wired, so a queue set to 20 chunks stayed at 20 and shed the **newest** audio. Both are now
either wired or gone, and the sink line reports three separate real numbers where it used to print
the bank target under the name of the capacity.

**The head-of-line hazard is real and the demux is built.** E1 forced the video feed to hold and
measured the read thread blocking 132 to 145 times a window for up to 233 ms, with the audio sink
underrunning and shedding in the same windows. The capture also carries **91 `Audio queue is full`
lines, every one of them inside those four minutes**, with the queue pinned at its ceiling: a parked
read thread does not read audio slowly, it does not read it at all and then takes four hundred
milliseconds of it in one burst. No queue depth fixes that. Video now runs on its own thread.

**The re-bank banked the opening depth, which is shorter than the disturbance.** 200 ms re-banked
against a 203 to 233 ms park underran again inside the same window, two to four times over. A re-bank
is deeper than an opening bank now.

## 3. What is different about this round

**Phone-to-phone leads, because it is the pairing the owner can hear the stutter on.** Round 1 ran
entirely on D-HU and graded logs. Part P runs D-POCO as head unit and D-MOTO as phone, which is the
configuration of the drive that caught the sink discarding audio.

**Part P needs no artificial lever.** D-POCO is joined to no network while it hosts the group, and a
disconnected station scans: the drive measured one scan every 21 to 40 s, each taking the single
radio off the group's channel, with six `Audio queue is full` in about 126 s of music beside them.
That is the disturbance. Do not stand the station down, do not join D-POCO to anything, and do not
change the audio settings.

**Leave D-POCO's audio settings exactly as they are**: AAC on, latency multiplier 2, queue capacity
20. They are deliberate and they are why this reproduces in minutes rather than hours. They are not a
misconfiguration and nothing in this round should reset them.

**The ear is an instrument this thread has never used.** Every earlier round graded only what the log
said. Fire a marker on every audible break:

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
adb -s <hu> shell am broadcast -f 0x00000020 -n $RX \
  -a com.andrerinas.openheadunit.ACTION_LOG_MARKER --es text stutter
```

`-f 0x00000020` is not optional. It prints `AutomationMarker: stutter` at WARN. The number that
matters at the end is how many markers landed in a window whose `audio sink` line showed nothing at
all: that is the size of what our instruments still cannot see.

**Part G is unrunnable until the latch is cleared.** Round 1 read `mode=AUTO ... learned` and `mode=
NEVER ... learned` on the same build and concluded the AUTO heuristic was opting out. It was not:
`playback-focus-self-defeating` was already `true` on that unit from an earlier session. It persists
across every connection and only re-picking the focus mode in the settings UI clears it. Write it
`false` with the app stopped and read it back before the first Part G run, and report its value after
each one. The log wording is also fixed: only AUTO says `learned` now.

**Expect these to be INCONCLUSIVE and say so rather than failing them.** Nothing on this rig triggers
guidance or system-sound traffic through the session, so any run needing `AUDIO1` or `AUDIO2` is
blocked. Try one scripted notification on the phone during a live session and report whether any
`RECV: AUDIO2` appeared. If none does, say so once and we stop briefing those runs.

**One premise to verify, not assert.** Confirm `AapProjectionActivity` stays resumed on D-POCO and
the picture actually renders before starting Part P. The drive measured 46 to 47 fps there, so this
is a check rather than a doubt. The unit that cannot hold the projection is D-MOTO in portrait, which
is why D-MOTO is the phone here and never the head unit.

**The phone-side audio source has to be rebuilt on D-MOTO.** Round 1 spent hours on this, so carry it
across rather than rediscovering it: VLC needs `appops set org.videolan.vlc MANAGE_EXTERNAL_STORAGE
allow` and not `pm grant`; a stale playback item makes it reopen the old path until `pm clear`, which
then costs a first-run scan of about 15 s; and the activity to target is `org.videolan.vlc/.StartActivity`
and not the player activity. D-MOTO has a PIN lock and is not rooted.

## 4. Settings keys this round needs

```xml
<int name="log-level" value="0" />                    <!-- VERBOSE, required for every run -->
<boolean name="enable-audio-sink" value="true" />     <!-- read it back before the first run -->
<boolean name="static-audio-focus" value="false" />   <!-- false is the default path, true the mixer -->
<int name="playback-focus-mode" value="0" />          <!-- 0 AUTO, 1 ALWAYS, 2 NEVER -->
<boolean name="playback-focus-self-defeating" value="false" />
<int name="audio-latency-multiplier" value="8" />     <!-- 1, 2, 4, 8 or 16; D-POCO stays at 2 -->
<int name="audio-queue-capacity" value="50" />        <!-- D-POCO stays at 20 -->
<boolean name="use-aac-audio" value="false" />        <!-- D-POCO stays true -->
<int name="debug-video-feed-hold-ms" value="0" />     <!-- E2 only, max 250 -->
```

**16 is a new value** and is the deep end of the picker. A stored value the picker does not offer is
still honoured, so writing it directly is fine.

## 5. The lines that decide every run

New on the candidate, absent on the baseline, all at INFO unless marked. Copied from the source, not
from memory.

```
audio sink AUDIO over 30000ms: underruns=0, silentCycles=0, rebanks=0, dropped=0, droppedFrames=0, shed=0, depth=203ms (min 158ms), queued=0, capacity=30784 frames (641ms), effective=30784 frames (641ms), target=9600 frames (200ms), drift=0ms
transport dispatch over 30000ms: video=0ms, audio=2ms, other=1ms, unread=0%, blocks=0, longest=3ms on AUDIO, videoQueue=2
AudioTrackWrapper: AUDIO can hold N frames (Nms) of the N bytes asked for, keeps Nms filled, banking Nms before playing and Nms after an underrun
AudioTrackWrapper: AUDIO can hold Nms, which cannot cover a Nms arrival and a cushion together - this sink will break up whatever the latency is set to
AudioMixer: Started (asked N bytes = Nms, minBuffer=N, capacity=N frames (Nms), effective=N frames (Nms), latencyMultiplier=N)
AudioMixer: Registered channel N (sampleRate=48000, channels=2, latencyMultiplier=N, bank Nms before playing)
AudioDecoder: Created and started shared AudioMixer on channel N at latencyMultiplier=N
Audio queue is full at N chunks, shedding the oldest to keep the sink current - see dropped= on this channel's sink line for how many   (WARN, first time only)
AudioTrackWrapper: the AAC sink cannot keep up, shedding decoded frames - see shed= on this channel's sink line for how many            (WARN, first time only)
AutomationMarker: stutter                                                                                                              (WARN)
```

Carried over from round 1, unchanged:

```
AudioTrackWrapper: AUDIO underran, re-banking Nms (re-bank N)
AudioTrackWrapper: AUDIO parked with Nms buffered
AudioTrackWrapper: AUDIO resumed with Nms banked after Nms
AudioTrackWrapper: AUDIO has underrun through 6 re-banks in a minute - the link is short of audio, so it is left to run shallow
AudioTrackWrapper: playback started with N frames banked (target N) after Nms
AudioDecoder.start: channel=6, ..., isAac=false, source=setup, latencyMultiplier=8, queueCapacity=50, ...
StationScanMonitor: station scans: N in Nms, every Ns (shortest Ns, longest Ns). Each one takes the radio off the group's channel.
AapAudio: AA audio started (AUDIO) - acquiring transient system audio focus (mode=AUTO)
AapAudio: AA audio started (AUDIO) - leaving system audio focus alone (...)
```

**Three fields changed meaning and two are new.** `granted=` is gone: it printed the effective size
under the name of the capacity. `capacity=` is what the track can hold, `effective=` is what the
framework will keep filled, and `target=` is the cushion we bank, which is the one the dial moves.
`shed=` counts decoded AAC frames the sink could not take, separated from `droppedFrames=` which
counts frames dropped before decode. `videoQueue=` is the deepest the video thread's backlog got,
which is where the park moved to.

**A `videoQueue` above zero beside `blocks=0` is the result the demux is meant to produce**, not a
fault. The park still happens; it just no longer happens on the thread that reads the socket.

## 6. Runs

### R0. Gate

Both units, both APKs.

```bash
adb shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_QUERY_STATE
./gradlew :app:testGithubDebugUnitTest
```

**PASS:** `commit` reads `d292ec52` on the candidate and `8418ed02` on the baseline, and the gate is
1914 tests with no failures. Report both APK md5s.

---

## Part P: phone-to-phone, D-POCO as head unit and D-MOTO as phone

This is the point of the round. Settings on D-POCO stay at AAC on, multiplier 2, queue 20 for P1 and
P2. Music from VLC on D-MOTO, at least 8 minutes a run so several scans fall inside it.

### P1. The candidate, on the configuration the owner hears

**Candidate only.** Listen to the whole run and fire a marker on every audible break.

Report, per `audio sink AUDIO` window: `underruns`, `rebanks`, `dropped`, `droppedFrames`, `shed`,
`depth` and its `min`, `capacity`, `effective`, `target`, `queued`. Beside each window report the
`station scans` line that falls in it, and every `AutomationMarker` timestamp.

**PASS:** no window has `dropped` above zero, and every underrun is followed by a `re-banking` line
and a `resumed with` line inside the same window. **FAIL:** any `Audio queue is full` at all, or an
underrun with no re-bank after it.

**If the run is clean and you still heard breaks,** that is the most valuable result in the round.
Report the marker count and which windows they fell in.

### P2. The baseline, same everything

**Baseline only**, same settings, same length, same music, listen and mark the same way.

This is the comparison that answers the shipping question. Report the same fields the baseline has
(it prints no `audio sink` line at all, which is itself the finding) plus `Audio queue is full`
counts, `disabled due to previous underrun` counts and the `station scans` lines.

**Report the two numbers side by side:** `Audio queue is full` per minute on each arm, and markers per
minute on each arm. The drive on this build read six drops in about 126 s of music.

### P3. The dial, where it can be heard

**Candidate only.** Three sessions, app stopped between each, `audio-latency-multiplier` at **2**,
then **8**, then **16**. Everything else unchanged, including AAC on and queue 20. About 5 minutes
each, listening and marking.

**PASS:** `target=` rises at each step and `capacity=` rises with it, and the deepest setting has
fewer markers and fewer underruns than the shallowest. **FAIL:** `target=` identical at 2 and 16,
which would mean the dial still reaches nothing.

**Say which way the latency went too.** A deeper cushion is meant to cost lag: report whether the
audio noticeably trails the picture at 16, because that is the reason the ceiling is where it is.

### P4. AAC on the owner's own configuration

Read out of P1 rather than run separately. Report `AudioDecoder.start`'s `isAac` and `source`, and
`shed=` in every window.

**PASS:** `shed=0` in every window after the first. **FAIL:** `shed` above zero in more than one
window. The once-per-lifetime warning line is **not** a FAIL by itself: it fires once ever and cannot
say whether the problem recurred, which is why round 1's bar was the wrong one.

---

## Part H: the same work on D-HU

The MT50 renders reliably, scans only about once every 2.75 minutes, and is the unit round 1
measured, so it carries the controlled levers and the default-settings arm a reporter would run.

### H1 / H2. The default configuration, both arms

`static-audio-focus=false`, `use-aac-audio=false`, multiplier 8, queue 50, five minutes each arm.

**PASS:** the candidate's underruns per minute are at or below the baseline's, and no window on the
candidate has `dropped` above zero. **Pair the count with the measurement that proves it was
reachable:** report the `station scans` line and the throughput windows, so a clean run on a quiet
link is not read as the fix working.

### H2b. A1 again, on the mixer

`static-audio-focus=true`, multiplier 8, candidate only, five minutes. Round 1 read `silentCycles=0`
in all 11 windows with three dipping below 120 ms of `min` depth.

**PASS:** `silentCycles=0` in every window and `min` depth at or above 120 ms in every window.

### A2b. The dial on the mixer path

**Candidate only**, `static-audio-focus=true`, two sessions with the app stopped between, multiplier
**2** then **16**.

Read `AudioMixer: Started`, `AudioDecoder: Created and started shared AudioMixer on channel N`, and
the `Registered channel 6` line.

**PASS:** `capacity=` at 16 is larger than at 2, and channel 6's own `bank Nms` is larger at 16 than
at 2. **FAIL:** either is identical at both. Report which channel built the mixer; round 1 found it
was always a prompt channel, which is exactly why the setting never arrived.

### A2c. The dial on the direct path

Same two sessions with `static-audio-focus=false`. Read `capacity=`, `effective=` and `target=` off
the `audio sink AUDIO` line.

**PASS:** `target=` differs between 2 and 16. Round 1 read 200 ms at every setting.

### B2b. The deeper re-bank

**Candidate only**, default settings. Same lever as round 1, and read the interface name live rather
than from an earlier line:

```bash
IF=$(adb -s <hu> shell ip -o link | grep -o 'p2p-wlan0-[0-9]*' | head -1)
adb -s <hu> shell "iptables -I INPUT -i $IF -j DROP; iptables -I OUTPUT -o $IF -j DROP; sleep 3; \
  iptables -D INPUT -i $IF -j DROP; iptables -D OUTPUT -o $IF -j DROP"
```

Confirm a real gap in `RECV: AUDIO Media Data` arrival timestamps before grading; round 1's first
attempt hit a stale interface name and dropped nothing.

**PASS:** one `re-banking` line naming a depth **greater than** the `target=` on the sink line, then
a `resumed with` line, then no second underrun inside the same window. **FAIL:** a second underrun in
the same window, which is what round 1 measured under a shorter disturbance.

### D1b. AAC forced

**Candidate only**, `use-aac-audio=true`, at least 5 minutes. Same grading as P4: `shed=` per window,
not the warning line.

### E2. The demux

**Candidate only**, `debug-video-feed-hold-ms=200`, media playing, three minutes. This is round 1's
E1 rerun against the fix.

**PASS:** `blocks=0` on the `transport dispatch` line with `videoQueue` above zero, the `audio sink`
line clean in the same windows (`underruns=0`, `dropped=0`), and **zero** `Audio queue is full`
against round 1's 91. **FAIL:** `blocks` above zero, or the audio line degrading as it did in round 1.

**What a PASS would look like if the change did nothing:** `blocks=0` with `videoQueue=0` and no
video load at all, which means the hold never took effect. Report the throughput line alongside so
the two can be told apart.

### Part G. The focus window, with the latch cleared first

Write `playback-focus-self-defeating=false` with the app stopped, launch, and confirm the first
`AA audio started` line does **not** say `learned`. Then run round 1's G1 and G6 as written. Report
the latch's value after every run.

**If it latches mid-round, stop Part G and say when.** Everything after that point is measuring a
different build state.

### The guidance lever

One attempt, any run. Post a notification with sound on the phone during a live session and report
whether any `RECV: AUDIO1` or `RECV: AUDIO2` line appears. If nothing does, say so plainly: those
runs come off the brief for good.

## 7. Do not re-run

- **B2, C1, G3, G4, G5** passed in round 1 and nothing in these two commits touches their mechanism.
  B2b replaces B2 because the re-bank depth changed.
- **A1** is replaced by H2b, which is the same run with the `min` depth graded.
- **G2 and C2** stay INCONCLUSIVE unless the guidance lever above works.
- **G7** stays unrun.

## 8. Report back

1. **P1 against P2:** `Audio queue is full` per minute and markers per minute on each arm, with the
   `station scans` cadence beside them.
2. **How many markers landed in a window whose `audio sink` line showed nothing.** This is the
   number that says what our instruments still cannot see.
3. **P3 and A2c:** the `target=` figure at multiplier 2, 8 and 16. Round 1 read 200 ms at all of them.
4. **E2:** `blocks`, `videoQueue` and the `Audio queue is full` count, against round 1's 132 to 145,
   nothing, and 91.
5. **The latch's value** before and after each Part G run.
