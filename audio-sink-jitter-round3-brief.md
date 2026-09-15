# audio-sink-jitter, round 3 brief: the sink keeps what it has, and we look below the AudioTrack

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `02ce29d0` | 4 on `main` | 1947 tests, 0 failures |
| Baseline | `main` | `8418ed02` | | |

```bash
git fetch fork
git checkout -B audio-sink fork/fix/audio-sink-and-wireless-bring-up   # 02ce29d0
git log --oneline -4
# 02ce29d0 Connection, 61143283 Native AA, 762d195c Transport, 9e451cac Audio
```

**The branch was renamed and consolidated after this brief was first written.** Everything unlanded
of ours is now one branch of four commits, one per component. `fix/audio-sink-jitter-and-instrumentation`,
`fix/native-aa-double-group-create` and `fix/wireless-only-when-selected` are all deleted, locally and
on `fork`, so nothing resolves under those names and a round 2 checkout does **not** fast-forward:
check the branch out fresh. All four compile alone, checked.

The audio and transport work rounds 1 and 2 graded is unchanged in content: the two-commit pair's
tree equals the six-commit tip's exactly, checked. What is new to this candidate is the two wireless
commits, and section 3 says what they can do to a run.

Baseline APK is needed for P7 only. Everything else is candidate only.

## 2. Why this round exists

Round 2 was the first round with a person listening, and it settled the dial: `target=` and
`capacity=` now rise at every step on both paths and both rigs, and at 16 the owner heard no audible
break at all in eight minutes against three at 2. The deeper re-bank held against a real 3.16 s gap.
The demux took `blocks` from 132 to 145 a window down to 2 once, and `Audio queue is full` from 91 to
zero.

It also found three defects, all of them in code round 2 was grading rather than in the rig.

**A repeated Media Sink Setup was destroying a live, healthy sink.** At 11:42:00 in P1 the phone
re-sent setup for every channel at once, because the mock drive put Maps into a guidance state. We
tore the playing media track down and rebuilt it, and the framework handed the replacement a
different output: `capacity=` fell from **698 ms to 400 ms** and stayed there. Every underrun window
and every `shed` window in that 23 minute capture is after that moment and none is before it, which
is the whole of P4's FAIL. A live sink whose codec has not changed is kept now.

**The demux had given the phone an unbounded window.** E2 measured `videoQueue` climbing 918, 1803,
2732, 3671, 4666, 5602 over six windows and never coming back. Acking video on receipt removed the
only bound the backlog ever had, and each queued message holds at least 64 KiB, so that run was
carrying hundreds of megabytes of copies. The ack goes out from the video thread after the decode
now, so the phone's own `max_unacked` window bounds it at about 12 messages, with a transport ceiling
behind that as a backstop.

**The mixer was counting silence a channel never had.** H2b's `silentCycles=269` in its first window
was a channel registered at setup with nothing sent to it yet, scoring one per mix cycle. Those are
the opening bank, reported as their own line now, and `silentCycles` counts only gaps after a channel
has played.

**Two thirds of what the owner heard was invisible, and that is this round's question.** 6 of 9
markers landed in windows the sink reported perfectly healthy, and it reproduced across two sessions.
`underruns` comes from the framework's own `AudioTrack.getUnderrunCount()`, so those windows mean our
buffer genuinely never ran dry. Whatever was heard was not this sink emptying.

## 3. What is different about this round

**The falsifier is the point of the round.** This thread has carried one since round 1: if the sink
line holds its target and `Audio queue is full` stays at zero while a listener still hears breaking
up, the fault is below us and no amount of app-side buffering fixes it. Round 2 produced exactly that
condition six times. So this round captures what is below us at every marker, and the answer decides
whether this thread continues at all.

**The default cushion changed.** `audio-latency-multiplier` now defaults to 16 rather than 8, which
is 400 ms of bank and the ceiling. Nothing else writes the key, so a unit with a stored value keeps
it: a run that wants the new default has to have the key **absent**, not set to 16. Both are worth
running and P6 is the one that proves the default reaches a fresh install.

**A sink can now deepen its own cushion.** Three re-banks in a minute and it banks 100 ms deeper, up
to the same 400 ms ceiling. At the new default it is already on the ceiling and will never fire,
which is correct: it exists for a user who picked shallower. A2d is where it is graded, at 2.

**Leave D-POCO's other audio settings as they are**: AAC on, queue 20. They are deliberate and they
are why this reproduces in minutes. Only `audio-latency-multiplier` moves, and only where a run says
so.

**This candidate carries two wireless changes rounds 1 and 2 did not, and they can move Part P.**
The Connection commit makes `Settings.connectionModes` an actual switch: a unit whose selection does
not include WiFi will now refuse to arm the wireless stack at all, where before it armed regardless.
It also stops the stack while the settings screen is open. The Native AA commit changes when a P2P
group is created. Part P's entire disturbance is D-POCO hosting a group while joined to nothing, so
**confirm wireless arms on both units and the group comes up before grading anything**, and report
the `connectionModes` value for each unit in the setup notes. A Part P run that is unexpectedly quiet
is a bring-up difference until proven otherwise, not the audio fix working.

**Everything round 2 established about the rig still holds** and is now in `TESTING-TEMPLATE.md`
section 7a: the A2DP speaker that hijacks `STREAM_MUSIC`, the poke target pointing at the wrong
phone, the P2P group that survives a force-stop, and the reinstall that wiped `settings.xml`. Read
those before wiring anything up.

## 4. Settings keys this round needs

```xml
<int name="log-level" value="0" />                    <!-- VERBOSE, required for every run -->
<boolean name="enable-audio-sink" value="true" />     <!-- read it back before the first run -->
<boolean name="static-audio-focus" value="false" />   <!-- false is the default path, true the mixer -->
<int name="audio-latency-multiplier" value="16" />    <!-- 1, 2, 4, 8 or 16; ABSENT means 16 now -->
<int name="audio-queue-capacity" value="50" />        <!-- D-POCO stays at 20 -->
<boolean name="use-aac-audio" value="false" />        <!-- D-POCO stays true -->
<int name="debug-video-feed-hold-ms" value="0" />     <!-- E3 only, max 250 -->
```

**Absent and 16 are different states and P6 depends on the difference.** Removing the key is what
tests the shipped default; writing 16 tests the same depth with the key present.

## 5. The lines that decide every run

New on this candidate, copied from the source rather than from memory:

```
AapAudio: AUDIO is already set up, keeping the sink it has
AudioTrackWrapper: AUDIO keeps underrunning at Nms, banking Nms from here
AudioMixer: channel N started playing after banking for Nms - silentCycles counts gaps from here
AapTransport: the video thread is 256 messages behind, shedding - see videoShed= on the transport dispatch line for how many   (WARN, first time only)
transport dispatch over 30000ms: video=0ms, audio=2ms, other=1ms, unread=0%, blocks=0, longest=3ms on AUDIO, videoQueue=2, videoShed=0
```

Changed since round 2:

```
audio sink AUDIO over 30000ms: underruns=0, silentCycles=0, rebanks=0, dropped=0, droppedFrames=0, shed=0, depth=203ms (min 158ms), queued=0, capacity=30784 frames (641ms), effective=30784 frames (641ms), target=9600 frames (200ms)
```

**`drift=` is gone.** It printed in every window of rounds 1 and 2 and was computed nowhere, so it
read `0ms` for that reason and not because the clocks agreed. Do not look for it.

Carried over unchanged, and all still the lines that matter:

```
AudioTrackWrapper: AUDIO underran, re-banking Nms (re-bank N)
AudioTrackWrapper: AUDIO resumed with Nms banked after Nms
AudioTrackWrapper: AUDIO parked with Nms buffered
AudioTrackWrapper: playback started with N frames banked (target N) after Nms
AudioTrackWrapper: AUDIO can hold N frames (Nms) of the N bytes asked for, keeps Nms filled, banking Nms before playing and Nms after an underrun
AudioDecoder.start: channel=6, ..., isAac=true, source=setup, latencyMultiplier=N, queueCapacity=N, ...
AapControlMedia.mediaSinkSetupRequest | Media Sink Setup Request: 2 on channel AUDIO
StationScanMonitor: station scans: N in Nms, every Ns (shortest Ns, longest Ns).
AutomationMarker: stutter                                                                      (WARN)
```

**The pair that decides P5** is `Media Sink Setup Request: 2 on channel AUDIO` and whatever follows
it. On the candidate a mid-session one should be followed by `keeping the sink it has` and by
**no** `AudioDecoder.start: channel=6`, and `capacity=` on the next sink line should be the same
number as on the previous one.

## 6. The instrument this round adds: what is below the AudioTrack

Fire the marker and take the dump in one go, so the two are stamped within a second of each other.
Put this on the host as `mark.sh` and run it every time you hear a break:

```bash
#!/bin/sh
# mark.sh <serial>   fires a marker and captures what AudioFlinger thinks at the same moment
HU=$1
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
N=$(date +%H%M%S)
adb -s $HU shell am broadcast -f 0x00000020 -n $RX \
  -a com.andrerinas.openheadunit.ACTION_LOG_MARKER --es text stutter
adb -s $HU shell dumpsys media.audio_flinger > flinger_$N.txt
adb -s $HU shell dumpsys media.metrics       > metrics_$N.txt
echo "marked $N"
```

`-f 0x00000020` is not optional on the broadcast. `dumpsys` needs no root: the shell user holds the
DUMP permission on both units.

At the **end of every session**, after the app is stopped, capture the framework's own teardown
figure, which is the one instrument that survives every ROM and every log level:

```bash
adb -s $HU logcat -d | grep -i "underrunframes"
```

Divide it by the sample rate to get seconds of real underrun, and report it per session beside the
sum of `underruns` across that session's sink lines. **If the two disagree, the framework's number
wins**, and the difference is the size of what our counter cannot see.

In each `flinger_*.txt`, the fields worth reading are the output thread's own underrun counters and
the FastMixer block. Different ROMs spell them differently, so report what the file actually says
rather than searching for one string:

```bash
grep -inE "underrun|overrun|FastMixer|Thread [0-9]|standby|sample rate|frame count" flinger_*.txt
```

**What the answers mean, stated in advance so the round cannot be read either way afterwards:**

- **AudioFlinger shows its own underruns in a window our sink called clean.** The remaining stutter
  is below us. This thread stops adding app-side buffering and the next question is CPU contention
  and the output HAL, not the jitter buffer.
- **Neither instrument shows anything at a marker.** The sink delivered continuous audio and the
  framework played it, so the break is in what arrived, in the picture, or in the listening
  conditions. The next suspect is the phone, not the head unit.
- **Our sink shows it and AudioFlinger does not.** The instrument works and round 2's six markers
  were a sampling problem after all, which would be worth knowing and is the least likely of the
  three.

## 7. Runs

### R0. Gate

Both units, both APKs.

```bash
adb shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_QUERY_STATE
./gradlew :app:testGithubDebugUnitTest
```

**PASS:** `commit` reads `02ce29d0` on the candidate and `8418ed02` on the baseline, and the gate is
1947 tests with no failures. Report both APK md5s.

---

## Part P: phone-to-phone, D-POCO as head unit and D-MOTO as phone

The pairing the owner can hear. Music from VLC on D-MOTO, the mock drive running so Maps can enter a
guidance state, at least 15 minutes for P5 because that is what it took for the setup to repeat.

### P5. The re-setup fix, which is the run this round exists for

**Candidate only**, D-POCO settings as round 2 left them apart from `audio-latency-multiplier`, which
stays at **2** so the comparison with P1 is like for like. Listen and mark throughout.

Report every `Media Sink Setup Request` line in the session with its channel and timestamp, and for
each one on channel AUDIO report the three lines around it: whether `keeping the sink it has`
appeared, whether an `AudioDecoder.start: channel=6` appeared, and the `capacity=` on the sink lines
immediately before and after.

**PASS:** at least one mid-session `Media Sink Setup Request: 2 on channel AUDIO`, followed by
`keeping the sink it has`, no `AudioDecoder.start: channel=6`, and `capacity=` the same number before
and after it. Plus `shed=0` in every window after the first.

**FAIL:** `capacity=` drops across a mid-session setup, or `shed` above zero in more than one window.

**If no mid-session setup happens in the whole run, say so and grade the run INCONCLUSIVE** rather
than passing it. The trigger in round 2 was Maps entering guidance under the fed route, so drive the
route and give it time; a run with no re-setup measures nothing about this fix.

### P6. The shipped default, out of the box

**Candidate only.** Reseed D-POCO's settings with `audio-latency-multiplier` **absent**, leaving AAC
on and queue 20. Confirm before listening that the sink line reads `target=19200 frames (400ms)`.
About 10 minutes, listening and marking.

**PASS:** `target=` is 400 ms with the key absent, and markers per minute are at or below P5's.
**FAIL:** `target=` reads anything else with the key absent, which would mean the default did not
reach a fresh install.

**Report whether the audio trails the picture.** This is a default now rather than an opt-in, so one
listener saying it is fine at 16 is no longer enough: say plainly whether lip sync or a tap sound
lands late, and on what content.

### P7. The baseline, for the marker rate only

**Baseline only**, same settings D-POCO had for P5, same music, same length as P6, listening and
marking. The baseline prints no `audio sink` line at all, so the only comparable number is markers
per minute and `disabled due to previous underrun` counts.

Run `mark.sh` on this arm too. **A baseline marker with AudioFlinger underruns beside it is the
strongest possible answer to the round's question**, because it would show the same fault present
before any of this work.

---

## Part H: the controlled levers on D-HU

The MT50 renders reliably and scans about once every 2.75 minutes, so it carries the levers Part P
cannot fire on demand.

### H3. The re-setup fix, forced if a lever exists

**Candidate only**, default settings with the multiplier key absent. Grade P5's condition on whatever
the session produces. If nothing re-sends setup within ten minutes, say so and move on: P5 is where
this is really graded, and this run exists to show the fix costs nothing on a unit that never
re-setups.

### H2c. The mixer, replacing H2b

**Candidate only**, `static-audio-focus=true`, multiplier 8, five minutes, media playing from the
first minute.

**PASS:** `silentCycles=0` in **every** window including the first, one
`channel N started playing after banking for Nms` line per channel that ever played, and `min` depth
at or above 120 ms in every window.

**FAIL:** any `silentCycles` above zero after a channel has started playing. Round 2 read 269 in the
first window, which was the opening bank being counted as gaps.

Report the opening-bank figure for each channel. Round 2's first window was about 5.4 s of it.

### A2d. The sink deepening its own cushion

**Candidate only**, `audio-latency-multiplier=2`, default path, media playing. Fire the link-gap
lever **four times, about 10 seconds apart**, so four underruns fall inside one minute:

```bash
IF=$(adb -s <hu> shell ip -o link | grep -o 'p2p-wlan0-[0-9]*' | head -1)
for i in 1 2 3 4; do
  adb -s <hu> shell "iptables -I INPUT -i $IF -j DROP; iptables -I OUTPUT -o $IF -j DROP; sleep 2; \
    iptables -D INPUT -i $IF -j DROP; iptables -D OUTPUT -o $IF -j DROP"
  sleep 10
done
```

Read the interface name live. Confirm real gaps in `RECV: AUDIO Media Data` arrival timestamps before
grading, because a stale interface name drops nothing.

**PASS:** a `keeps underrunning at Nms, banking Nms from here` line appears after the third re-bank,
the new figure is 100 ms above the old one, `target=` on the next sink line carries it, and it never
reads above 400 ms however many gaps are fired.

**FAIL:** no such line after four underruns in a minute, or a `target=` above 400 ms.

Report `target=` from every window of the run, so the step is visible as a series rather than as one
line.

### E3. The demux, with the backlog bounded

**Candidate only**, `debug-video-feed-hold-ms=200`, media playing, four minutes. Round 2's E2 rerun.

**PASS:** `videoQueue` settles rather than climbing, at a figure in the low tens rather than the
thousands, `videoShed=0` in every window, `blocks=0` after the first window, the `audio sink` line
clean in the same windows, and `Audio queue is full` still zero.

**FAIL:** `videoQueue` climbing window after window as it did in round 2, or `videoShed` above zero,
or the audio line degrading.

**Report the peak `videoQueue` against round 2's 5602**, and report the throughput line beside it so a
`videoQueue` of zero because the hold never took effect can be told apart from one bounded by the
fix. **Say what the picture looked like**: the ack now waits for the decode, so the phone throttles
its video, and a slower but whole picture is the intended outcome. If the picture instead breaks up
or washes out, that matters more than the queue depth and should be reported first.

---

## 8. Do not run

- **B2, B2b, C1, D1b, G1, G3, G4, G5, G6** all passed and nothing in these two commits touches their
  mechanism.
- **A2b, A2c, P3** graded the dial and it passed on both paths and both rigs.
- **G2, C2** are retired as scripted levers. Round 2 settled it: the only thing that opened the
  guidance channel was Maps entering a driving state under a real route, and `cmd notification post`
  does not reach it. Do not brief them again.
- **G7** stays unrun.
- **The guidance channels registering at `latencyMultiplier=4`** is not a finding. Every channel but
  media is capped at 4 on purpose, so a spoken prompt stays prompt. Round 2 reported it twice; it
  needs no further measurement.

## 9. Report back

1. **P5:** whether a mid-session Media Sink Setup happened at all, and if so whether `capacity=` held
   across it. This is the round's headline.
2. **Every marker, with its `flinger_*.txt` beside it.** For each one: the `audio sink` window it
   falls in, what that window said, and what AudioFlinger's own counters said at the same moment.
   Then the one-line verdict from section 6.
3. **The `underrunframes=` teardown figure per session**, beside the sum of `underruns` across that
   session's sink lines.
4. **P6:** `target=` with the key absent, markers per minute, and a plain answer on whether the audio
   trails the picture at the new default.
5. **E3:** peak `videoQueue` and `videoShed`, against round 2's 5602 and nothing, plus what the
   picture looked like.
6. **A2d:** the `target=` series across the run, and the deepen line if it appeared.
