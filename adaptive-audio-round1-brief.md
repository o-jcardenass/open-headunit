# adaptive-audio, round 1 brief: an outside audio rewrite, stressed on every unit

## 1. Build and baseline

| | Where | SHA | Gate |
|---|---|---|---|
| Candidate | `emotionbug/open-headunit`, branch `pr/audio-low-latency-stability` | `a22579ee` | 2501 JVM tests, 0 failures (author's count) |
| Baseline | `main` | `7db89757` | |

The candidate is an outside contributor's branch, not ours, so it is not on the fork. Fetch it by URL:

```bash
git fetch https://github.com/emotionbug/open-headunit.git pr/audio-low-latency-stability
git checkout a22579ee57da84cc2f1b0aeb6f5bbce98a077dec
git log --oneline 7db89757..HEAD | wc -l        # 12
```

**The baseline is needed** for A1, A4 and S1 only. The candidate sits directly on `7db89757`, so the
baseline is exactly "the same tree without the branch".

**Build both with `build_hur.sh`, and record both md5s from a real `adb pull`.** Then check the
candidate's identity by symbol, since versionName does not move:

```bash
unzip -p cand.apk 'classes*.dex' | strings | grep -cF AdaptivePcmBuffer   # >= 1 on the candidate, 0 on main
unzip -l cand.apk | grep -c 'lib/.*/libhur_aaudio.so'                      # >= 1
```

## 2. What this is and why it exists

The branch rewrites the media audio path:

- **Adaptive jitter bank.** `AudioMixer` renders 48 kHz stereo PCM16 in 10 ms blocks from a
  per-channel adaptive jitter bank (`AdaptivePcmBuffer`). The network budget is 60-150 ms and can
  grow to 400 ms when late batches are seen.
- **Concealment.** At most 30 ms is concealed before the bank re-primes.
- **Startup floor.** The first 10 s of music get a 120 ms floor.
- **Direct ingress.** Plain 48 kHz stereo PCM goes straight into the bank at ingress, so it no
  longer passes through the per-sink queue.
- **Opt-in AAudio.** A native AAudio backend (`use-aaudio-output`, off by default, API 26+, media
  stream only) falls back to AudioTrack if it fails.
- **AAC config.** Codec configuration is now separated from timestamped DATA, and **only DATA is
  acknowledged**.

The owner ran it on a Kindle Fire HD 8 and asked for weak hardware. This round runs it on all five
units and aims at the four places a code review found risk:

1. **Codec-config ACKs (A4).** Audio stopped acknowledging codec-config messages while video still
   does. If the phone counts a config against its 30-permit window, every AAC resume leaks one, and
   the AAC sink goes silent after about 30 resumes. Nobody has measured it either way.
2. **Default latency (A2, A3).** An unsaved `audio-latency-multiplier` now reads 2 instead of 16.
   Every user who never touched the setting moves from a fixed 400 ms bank to the adaptive 60-150 ms
   one.
3. **Write errors (A6).** One negative write ends the mixer thread for the session. Base survived
   it. Base cannot recover from a dead audioserver either, so this is a characterisation run, not a
   regression test.
4. **Old Android (S1, H1).** The branch adds a native library and new AudioTrack calls. Every call
   is SDK-gated by reading, but nothing below API 26 has run it.

## 3. What is different about this round

- **D-HU has no speaker and no 3.5 mm out.** Every verdict is taken from logs. "Music is playing" is
  proven by two things together:
  - the phone's `dumpsys media_session` reads `state=PLAYING`;
  - the head unit's `inbound rate over` line reads a non-zero `audio=` rate.
- **Media keys do not reopen a closed audio channel on this rig** (audio-start-and-teardown round 1).
  Play and pause go through **the Android Auto media widget in the projected picture**. That is the
  standing exception to the no-tap rule, because the tap goes to the phone over the touch channel.
  On D-HU at 1440x720 the widget's play/pause is `input tap 272 657`. If the layout differs, take one
  screenshot and use the widget's position, then say so in Setup notes.
- **The rig's saved `audio-latency-multiplier` is 2**, which is the candidate's new default, so a
  plain run cannot tell the two apart. A2 deletes the key and A3 writes 16. Restore 2 afterwards.
- **Several old lines no longer print for PCM16 on the candidate:**
  - `playback started with N frames banked`;
  - `started playing after banking`;
  - `AudioMixer: Started (asked`;
  - `Registered channel`.

  In addition, `queued=` and `underruns=` on the sink health line read 0 on direct PCM. **None of
  this is a FAIL.** Grade the candidate on the new lines in §5.
- **`first PCM channel=` prints once per channel lifetime, not once per media start.** Per-start
  grading uses the system underrun line against `Media Start Request AUDIO`, as the audio-start
  round did.
- **Pre-registered INCONCLUSIVE outcomes:**
  - A4, if the phone sends no `type: 1` on the media channel after the first start: the leak cannot
    happen, and the count says so.
  - H1, if no phone's head unit server can be brought up for Headunit Server mode. D-POCO's is known
    to be down.

## 4. Settings keys this round needs

Write them with the app stopped (§1) and read them back before every launch.

```xml
<int name="log-level" value="2" />                        <!-- INFO; A4 only: 0 (VERBOSE) -->
<int name="wifi-connection-mode" value="3" />             <!-- Native AA; H1 only: 1 -->
<boolean name="use-aac-audio" value="false" />            <!-- A1, A2, A3, A5, A6, S1, H1, P1 -->
<boolean name="use-aac-audio" value="true" />             <!-- A4 and A7 only (rig default) -->
<int name="audio-latency-multiplier" value="2" />         <!-- rig value; A2 deletes it, A3 writes 16 -->
<int name="audio-queue-capacity" value="20" />            <!-- rig value, leave it -->
<boolean name="use-aaudio-output" value="false" />        <!-- A5, S1, P1 set true where named -->
<boolean name="static-audio-focus" value="true" />        <!-- A6 names both values -->
<boolean name="attach_hw_dsp_equalizer" value="false" />  <!-- must be false or AAudio is never chosen -->
```

**Read back after every run:**
- `video-profile-starvation-cap` and `playback-focus-self-defeating`, which are still latches;
- `audio-latency-multiplier` after A2 and A3.

## 5. Lines that decide the runs

Each line below was checked with `grep -F` against the candidate source at `a22579ee`. The first
seven are INFO unless marked.

```
AudioMixer: id=<n> opened <AudioTrack|AAudio callback>, stream=3, capacity=..., effective=... frames
AudioMixer: id=<n> first PCM channel=6 requestToPcm=<ms>ms pcmToRender=<ms>ms target=... depth=...
AudioMixer: id=<n> channel=6 target=<ms>ms depth=<ms>ms arrivalGapMax=<ms>ms concealedFrames=<N> staleFrames=<N> compressedFrames=<N>   (every 10 s)
AudioMixer: id=<n> <backend> effective=... frames, staging=..., xruns=<N>, producerUnderruns=<N>, ...   (every 10 s)
AudioMixer: id=<n> <backend> write made no progress for 500ms; reopening output      (WARN)
AudioMixer: output stopped                                                            (ERROR, the mixer thread died)
AAudio -> AudioTrack (<reason>)            AAudio unavailable; using AudioTrack       AAudio could not open; using AudioTrack
AAC output rejected:                       ignoring invalid or incompatible AAC configuration
Audio transport read channel=<ch> readerGap=<ms>ms header=<ms>ms body=<ms>ms decrypt=<ms>ms dispatch=<ms>ms   (WARN)
PCM timing: packets=
AudioTrackWrapper: AUDIO PCM goes directly to the mixer bank
inbound rate over <ms>ms: video=...kB/s (...), audio=<N>kB/s (<N> msgs), other=...
Media Start Request AUDIO
RECV: AUDIO <name> type: <0|1> flags: ...                                            (VERBOSE only)
disabled due to previous underrun                                                     (AudioFlinger, any level)
```

The capture and the ring buffer are set up the same way for every run:

```bash
adb logcat -G 16M
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mediatap() { adb shell input tap 272 657; }       # projected AA media widget, D-HU layout
```

**Every run brackets its window with markers**, `send ACTION_LOG_MARKER --es text <RUN>-start` and
`<RUN>-end`. Every grep below runs on the lines between them.

## 6. Runs

Run order: **R0, A1, A2, A3, A7, A4, A5, A6, S1, H1, P1.**
- A6 goes late because it kills audioserver.
- S1, H1 and P1 need recabling.

Between runs, follow the §3a reset.

---

### R0. Gate

**PASS:**
- `./gradlew :app:testGithubDebugUnitTest` reads **2501** tests with 0 failures on the candidate.
- The two APK md5s differ.
- Both identity greps in §1 pass.

**A build or test failure stops the round.**

---

### A1. Media PCM under load, candidate against baseline (**the point of the round**)

**Setup:**
- D-HU is the head unit, D-POCO the phone, Native AA.
- `use-aac-audio=false`.
- Settings as in §4.

**The same procedure on each arm**, baseline first and then candidate:

1. Bring up the session and start music with `mediatap`. Confirm `state=PLAYING` on the phone.
   Settle for 30 s.
2. Send `A1-start`. Leave it for 5 minutes with nothing touched.
3. Do 20 pause/resume cycles:
   ```bash
   for i in $(seq 1 20); do mediatap; sleep 6; mediatap; sleep 10; done
   ```
4. Skip 10 tracks with `adb shell input keyevent 87`, with `sleep 12` between skips. The phone is
   already playing, so the key only has to skip, not reopen the channel. If it does not skip on this
   rig, leave the step out and say so.
5. Leave it for another 5 minutes, then send `A1-end`.

**Report per arm:**

```bash
grep -c "Media Start Request AUDIO" a1.txt
grep -c "disabled due to previous underrun" a1.txt
# starts with an underrun within 1500 ms after them: script it, list each pair's timestamps
grep "channel=6 target=" a1.txt | tail -1          # candidate only: final concealedFrames / staleFrames / compressedFrames
grep "xruns=" a1.txt | tail -1                     # candidate only
grep -c "AudioMixer: output stopped" a1.txt
grep "first PCM channel=6" a1.txt                  # candidate: requestToPcm / pcmToRender
```

**PASS (all of these):**
- The candidate's underrun count is at most the baseline's.
- The candidate has fewer starts with an attached underrun than the baseline, or both are zero.
- `concealedFrames` at `A1-end` is below 48000, which is 1 s of concealment over about 15 minutes.
- `AudioMixer: output stopped` never appears.
- The audio `inbound rate` stays non-zero throughout.

**FAIL:**
- the candidate has more underruns or more starts with an underrun attached;
- or `output stopped` appears;
- or the audio `inbound rate` drops to 0 while the phone reads PLAYING.

**If the change did nothing:** the baseline and the candidate would give the same counts. So report
both arms' counts even on a PASS, and the difference between them is the finding.

---

### A2. The new default for users who never saved one

**Setup:**
- Candidate only.
- `use-aac-audio=false`.
- **Delete `audio-latency-multiplier`** with both removal forms (§1), and confirm it is absent.

**Procedure:** start music, then send `A2-start`. Wait 60 s, then run 10 pause/resume cycles as in
A1. Send `A2-end`.

**PASS:**
- The `AudioDecoder.start:` line shows `latencyMultiplier=2`.
- In the first 10 s after music starts, the `channel=6 target=` line reads **≥120 ms**.
- The last `target=` in the window reads **60 to 150 ms**, or up to 400 ms with a non-zero
  `arrivalGapMax` above 150 ms beside it.

**Report:** the underrun count and the final `concealedFrames`, next to A1's candidate numbers.

**FAIL:** `latencyMultiplier` reads anything but 2, or the target lies outside those bounds with no
late-batch evidence.

**Afterwards:** restore `audio-latency-multiplier=2`.

---

### A3. A saved 16 is honoured

**Setup:**
- Candidate only.
- `audio-latency-multiplier=16`.

**Procedure:** start music, then run `A3-start`, 3 minutes, then `A3-end`.

**PASS:**
- The `AudioDecoder.start:` line shows `latencyMultiplier=16`.
- **Every** `channel=6 target=` line in the window reads `target=400ms`.

**FAIL:** any other target.

**Afterwards:** restore 2.

---

### A7. What the new diagnostics cost

**Setup:**
- Candidate only.
- `use-aac-audio=true` (the rig default).
- INFO.

**Procedure:** a steady 5-minute music session between `A7-start` and `A7-end`, with nothing touched.

**Report these as numbers.** There is no pass or fail:

```bash
grep -c "Audio transport read channel=" a7.txt                            # per 5 min
grep "Audio transport read channel=" a7.txt | head -20                    # quote them
```

- The share of those lines with `header=` ≥50 ms while `body`, `decrypt` and `dispatch` are each
  <5 ms.
- The share with `readerGap=` ≥50 ms.

The review expects `header` to carry almost all of it, meaning the line is measuring the phone's
idle time rather than our own work.

---

### A4. AAC resumes past the 30-permit window

**Setup:**
- Candidate first, then the baseline as the positive control.
- `use-aac-audio=true`.
- **`log-level=0` (VERBOSE)**, which is required for `RECV:`.

**Procedure:**
1. Start music and confirm PLAYING. Send `A4-start`.
2. Run 40 pause/resume cycles, each marked:
   ```bash
   for i in $(seq 1 40); do
     mediatap; sleep 5
     send ACTION_LOG_MARKER --es text A4-resume-$i
     mediatap; sleep 12
   done
   ```
3. Leave it for 60 s more, then send `A4-end`.

**Report:**

```bash
grep -cE "RECV: AUDIO .* type: 1 " a4.txt          # codec configs the phone sent on the media channel
grep -c  "Media Start Request AUDIO" a4.txt
grep -c  "AAC output rejected" a4.txt              # expect 0
grep -c  "ignoring invalid or incompatible AAC configuration" a4.txt
```

- For each `A4-resume-N` marker, record whether a `RECV: AUDIO .* type: 0 ` line follows within
  3 s. That line means data resumed. Give the last N that has one.

**PASS:**
- Audio DATA resumes after **all 40** markers.
- The phone reads PLAYING at `A4-end`.
- The `audio=` inbound rate is non-zero in the last window.

**FAIL:** DATA stops arriving after some resume N while the phone still says PLAYING. **Quote N and
the `type: 1` count.** An N near `30 - (configs before the first resume)` is the permit leak.

**INCONCLUSIVE:** the `type: 1` count is 0 or 1 in the whole window. In that case the phone sends a
config once per session, so the leak cannot accumulate on this phone. Say so; it is still a useful
answer.

**Baseline arm:** the same 40 cycles. It is expected to resume on all 40, since it acknowledges
type 1.

---

### A5. AAudio, opt-in, under stress

**Setup:**
- Candidate only.
- `use-aac-audio=false`, `use-aaudio-output=true`, `attach_hw_dsp_equalizer=false`.

**Procedure:**
1. Start music and send `A5-start`. Leave it for 10 minutes.
2. Run 20 pause/resume cycles as in A1.
3. Run **5 session cycles**, each one:
   ```bash
   send ACTION_DISCONNECT; sleep 8; send ACTION_START_WIRELESS_SCAN
   ```
   Wait for `SSL handshake complete`, then `mediatap`, then 30 s of music.
4. Send `A5-end`.

**Report:**

```bash
grep -c "opened AAudio callback" a5.txt
grep -c "opened AudioTrack" a5.txt
grep -E  "AAudio -> AudioTrack|AAudio unavailable|AAudio could not open" a5.txt   # quote every one
grep -cE "Fatal signal|SIGSEGV|tombstone" a5.txt
grep "xruns=" a5.txt | tail -1
adb shell ls -t /data/tombstones | head -3          # rooted D-HU, before and after
```

**PASS:**
- At least one `opened AAudio callback` per session.
- **Zero** native crashes.
- No `AudioMixer: output stopped`.
- Every fallback line is quoted along with its reason.

**FAIL:** any native crash or tombstone that names `hur_aaudio` or `libaaudio`, or `output stopped`.

**A native crash stops A5 only.** Pull the tombstone and carry on with A6.

---

### A6. A dead audioserver, candidate against baseline (characterisation)

**Setup:**
- D-HU is rooted.
- `use-aac-audio=false`.
- Run it twice on the candidate, once with `static-audio-focus=true` and once with `false`, then once
  on the baseline with `true`.

**Procedure:**
1. Start music and send `A6-start`. Leave it for 30 s.
2. Kill the audio server:
   ```bash
   adb shell su -c 'killall audioserver'
   ```
3. Wait 20 s.
4. Restart audio and wait again:
   ```bash
   send ACTION_RESTART_AUDIO
   ```
   Then wait 20 s, `mediatap` twice, and wait 20 s more.
5. Send `A6-end`.

**Report per arm:**
- whether any new `opened` line, or a `channel=6 target=` line with growing depth, appears after the
  kill;
- the count of `AudioMixer: output stopped`;
- the `write failed` or `write made no progress` lines, quoted;
- whether audio output resumed by the end: yes or no.

There is no pass or fail. The code says neither build recovers before the next session, and this run
checks that.

---

### S1. API 19 (D-SAM), candidate against baseline

**Setup:**
- D-SAM is the head unit, D-POCO the phone, Native AA on WiFi Direct (2.4 GHz).
- Install with `install_and_launch.sh`, `HU=30041c35642d2200`.
- `use-aac-audio=false`.
- **`use-aaudio-output=true` on purpose**: the gate must keep it off below API 26.

**Procedure:** 10 minutes of music, then 10 pause/resume cycles, between `S1-start` and `S1-end`.
Then the same on the baseline.

**PASS (candidate):**
- **Zero** of `VerifyError`, `UnsatisfiedLinkError`, `NoSuchMethodError`, `FATAL EXCEPTION`.
- **Zero** lines containing `AAudio`.
- `opened AudioTrack` present.
- The audio inbound rate is non-zero throughout.

**Report:**
- the final `concealedFrames` and `staleFrames`;
- the `disabled due to previous underrun` count for both arms, if this ROM prints it. If it does
  not, say so.

**FAIL:** any of those exceptions, or the mixer's `output stopped`.

---

### H1. API 17 (D-HP), Headunit Server mode

**Setup:**
- D-HP is the head unit, `wifi-connection-mode=1`, candidate only.
- The phone must be a unit whose Android Auto head unit server is on:
  - check it with a TCP connect to `:5277` from the head unit before launching;
  - D-POCO's is known down, so try D-MOTO first.

**Procedure:** 5 minutes of music plus 5 pause/resume cycles, between `H1-start` and `H1-end`.

**PASS:** the same as S1's candidate conditions.

**UNTESTABLE:** no phone's head unit server can be reached. Say which were tried.

---

### P1. A modern Snapdragon head unit, AAudio on and off

**Setup:**
- D-POCO is the head unit and D-MOTO the phone, Native AA.
- Candidate only.
- `use-aac-audio=false`.

**Procedure:** two 10-minute sessions with 10 pause/resume cycles each:
- `use-aaudio-output=false`;
- then `true`.

**Report:**
- the A1 and A5 numbers for each arm;
- the `effective=` and `burst=` values from each `opened` line.

**PASS:**
- No `output stopped`.
- No native crash.
- The AAudio arm shows `opened AAudio callback`.

## 7. Do not re-run

Nothing yet. This is round 1.

## 8. Report back

These are the numbers that decide whether we ask for changes before merge:

1. **A1:** the candidate against the baseline, as underruns / starts with an underrun / final
   `concealedFrames`.
2. **A4:** the last resume N that still got DATA, and the `type: 1` count. This settles the ACK
   question.
3. **A5:** native crashes, fallback lines, and `xruns`.
4. **S1 and H1:** crash-free, yes or no.
5. **A7:** the share of slow-read lines that are only `header=` time.

Results go in `adaptive-audio-round1-results.md`. Captures go to the release
`rig-evidence-adaptive-audio`, one asset for the round.
