# mic-uplink, round 2 brief: the same seven commits, a phone that will open the microphone

**Baseline (A):** `origin/main` @ `ea7aa7e0` (3.3.0-beta1, `andreknieriem/open-headunit`).
**Candidate (C):** `fork/fix/mic-uplink` @ `42302130` (7 commits on top of `fix/audio-start-and-teardown`).

```bash
git fetch origin && git fetch fork
git checkout -B main-a     origin/main         # must print ea7aa7e0...
git checkout -B mic-uplink fork/fix/mic-uplink # must print 42302130...
git rev-parse HEAD
```

Same two arms as round 1. **No code changed between the rounds.** If `origin/main` has advanced, check
`ea7aa7e0` out explicitly by SHA rather than taking the branch tip, exactly as round 1 did.

---

## 0. What changed since round 1, and what is not being re-run

Round 1 gated clean and then stopped. Every run that needed the phone to ask for the microphone was
UNTESTABLE, because the paired POCO X3 NFC never asked: its Android Auto invokes Gemini, Gemini could
not reach its own backend on any attempt, and no `MicrophoneRequest` ever arrived. The three trigger
routes all worked; the phone was the block.

**So the phone changes. This round runs on a Motorola with mobile data on, and nothing else changes.**
The transport stays Native AA over WiFi Direct. That is deliberate: it keeps one new variable in the
round, and it is the route the rest of this branch was validated on. Native plus hotspot is queued as
the fallback if this phone also never opens the microphone.

**Not re-run this round, and why:**

| Run | Round 1 | Why it is not here |
|---|---|---|
| M0 gate | PASS, 765 / 833, all eight suites exact | Repeated below as a build check only, not a measurement |
| M6a | **FAIL 5/5** | Answered, and it blocks the branch on its own. A new phone cannot change it: the throw happens in `onCreate` before any phone is involved |
| M6b | session half PASS | The half that mattered needs the microphone, so it moves into M5's orbit; the session half is settled |
| M7 clean control | PASS, 11m53s, six zeroes, `dropped=0` in 142/142 | Nothing in a phone swap bears on it |

**M6a still blocks this branch.** This round can produce a complete set of microphone results and the
branch will still not ship until that is fixed. Run it anyway: the results are what tell us whether
the seven commits are right, and they are what the fix would otherwise be written blind against.

---

## 1. What this round has to produce

Four numbers and one comparison that no round has ever produced:

1. Whether the microphone message carries its own 2-byte type (`dataOffset: 6` against `dataOffset: 2`).
2. Whether the announced rate and the captured rate are the same number when the picker is not 16000.
3. Whether messages are whole 2048-frame chunks.
4. `anc_enabled`, `ec_enabled`, `max_unacked` and `acks=` from the phone's own request, which **no
   reporter log has ever contained**.

Everything below is in service of those.

---

## 2. Setting up a phone this rig has never seen

The rig's documented shortcuts all assume the POCO. None of them apply. Do these in order, before any
capture, and report each as done.

1. **Pair over Bluetooth**, head unit to phone, and confirm from both sides. Record the phone's MAC:
   several runs and the manual poke need it.
2. **Confirm mobile data is on and validated**, and report it:
   ```bash
   adb -s <phone> shell dumpsys connectivity | grep -A3 "Active default network"
   ```
   Report the transport of the default network and whether it says `IS_VALIDATED`. Round 1's phone
   reported a validated LTE connection while its assistant said it had no network, so this reading is
   evidence, not a formality.
3. **Install and first-run Android Auto**, accept every consent, and let it complete one full
   projected session before any measured run. A first-run session is not a measurement.
4. **Report the assistant this phone actually invokes.** Open it once by voice or by the on-screen
   control and say which answers: Gemini, or classic Google Assistant. **This is a reported field of
   the round**, because it is the single thing that decided round 1.
5. **Raise the phone-side tags, per §5b, before the first launch.** Not during. Round 1 lost a capture
   to this: force-stopping Gearhead mid-session tore the live session down and re-formed it, and the
   run was discarded for a second `createGroup SUCCESS` that the tester had caused.
6. **Report the Gearhead version** (`adb -s <phone> shell dumpsys package
   com.google.android.projection.gearhead | grep versionName`). Round 1 ran `17.5.663204-release`; a
   different build changes which phone-side strings exist.

---

## 3. Settings keys

Unchanged from round 1.

| Key | Type | Value | Runs |
|---|---|---|---|
| `log-level` | int | `0` (VERBOSE) | G1, M1, M2, M3 |
| `log-level` | int | `1` (DEBUG) | M4, M5 |
| `log-source` | int | `0` (LOGCAT) | all |
| `mic-sample-rate` | int | `16000` | all except M2 |
| `mic-sample-rate` | int | `48000` | M2 only |
| `use-head-unit-microphone` | bool | `true` | all except M5 |
| `use-head-unit-microphone` | bool | `false` | M5 only |
| `mic-input-source` | int | `0` (DEFAULT) | all |
| `mic-noise-suppressor` | bool | `false` | all |
| `mic-auto-gain-control` | bool | `false` | all |
| `mic-echo-canceler` | bool | `false` | all |
| `enable-audio-sink` | bool | `true` | all |
| `wifi-connection-mode` | int | `3` (Native AA) | all |
| `native-ap-transport` | int | `0` (WiFi Direct) or absent | all |
| `key-codes` | (string set) | **absent** | all |

Ready to paste, for the VERBOSE runs:

```
<int name="log-level" value="0" />
<int name="log-source" value="0" />
<int name="mic-sample-rate" value="16000" />
<boolean name="use-head-unit-microphone" value="true" />
<int name="mic-input-source" value="0" />
<boolean name="mic-noise-suppressor" value="false" />
<boolean name="mic-auto-gain-control" value="false" />
<boolean name="mic-echo-canceler" value="false" />
<boolean name="enable-audio-sink" value="true" />
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
```

`use-head-unit-microphone` does not exist on arm A, and writing it there is harmless. The three effect
toggles are pinned off because the integration guide requires AGC and single-microphone noise
reduction disabled, and because a rig with one of them on would make M1's peak numbers incomparable to
any reporter's.

`native-ap-transport` is pinned to `0` explicitly this round, even though that is the default, because
the hotspot route is the queued fallback and a stray write would silently change the transport
underneath the whole round.

`bt-address` was blank before round 1 and blank after every one of its launches, and neither the fill
line nor the warning ever fired: `BluetoothHelper.getBluetoothMacAddress()` returns an empty string on
this head unit, not the masked `02:00:00:00:00:00`. That is the intended leave-it-alone behaviour and
is settled. **Read it once before the round and once after the first arm-C launch anyway** and report
both, because a new phone changes the pairing and this is free to collect.

---

## 4. The lines that decide the round

Candidate only, INFO:

```
AapTransport: mic uplink started (channel MIC, type 0, timestamps in microseconds, 4096B messages)
AapTransport: mic uplink | <n> frames, <n> B in <n>ms (<p>% of expected), peak=<n>/32767, largest=<n>B, smallest=<n>B, acks=<n>, discarded=<n>B
AapTransport: not taking the microphone (setting useHeadUnitMicrophone=false, available=true)
Mic request: the head unit microphone is off in Settings. Declining and sending nothing, so the phone can use its own microphone and any Bluetooth headset keeps this one
```

Candidate only, WARNING. The fallback path; none is expected, and all being absent is the result:

```
Mic request: capture did not start (code <n>); telling the phone so rather than leaving it waiting on a stream that will never arrive
Mic request: this device has no usable microphone capture; declining
MicRecorder: 16000 Hz capture is unavailable; capturing at <n> Hz and converting <k>:1 so the phone still receives the rate it was told about
MicRecorder: this device will not open 16000 Hz mono capture, which is the only rate Android Auto accepts, and no whole multiple of it either. The microphone is unavailable
```

Candidate only, VERBOSE, and the line the frame runs rest on:

```
MIC Media Data type: 0 flags: 11 size: 4110 dataOffset: 6
MIC Mic Response type: 32774 flags: 11 size: <n> dataOffset: 6
```

Baseline only, VERBOSE, the same message:

```
MIC Unknown (-1) type: -1 flags: 11 size: <n> dataOffset: 2
```

Both arms, INFO:

```
Mic request: open: true
Media Sink Stop Request: MIC
MicRecorder: Initializing AudioRecord with source: DEFAULT (0), SampleRate: <hz>, BufferSize: <n>
MicRecorder: Stopping. Active: true
MicRecorder: capture summary | source=DEFAULT (0) rate=<hz> elapsed=<n>ms bytes=<n> (<p>% of expected) emptyReads=<n> peak=<n>/32767
Voice Session Notification: START
Voice Session Notification: STOP
RemoteControlReceiver received: com.android.music.musicservicecommand
RemoteControlReceiver: Transport not started, skipping command execution
createGroup SUCCESS
```

`MicRecorder: capture summary` exists on arm C and not on arm A. It is the capture half of the pair;
`mic uplink |` is the send half. Read them together, always.

Grep everything with `-a` (`TESTING-TEMPLATE.md` §7a), no exceptions.

### 4a. Triggering the assistant from adb

**Round 1 answered this and the three-route apparatus is gone.** All three routes reached the app and
produced a genuine `Voice Session Notification: START` closing with `STOP` 3.1 to 3.5 s later. Use
route 1; it is one broadcast and it is the only one that says why it did nothing.

```bash
PKG=com.andrerinas.headunitrevived
RC=$PKG/com.andrerinas.openheadunit.app.RemoteControlReceiver

adb shell am broadcast -n $RC -a com.android.music.musicservicecommand --es command voice
```

It sends `KEYCODE_SEARCH` down and up together. If nothing happens, `RemoteControlReceiver: Transport
not started, skipping command execution` at INFO separates "we are not projecting" from "the broadcast
never landed", without reading anything else.

Two preconditions, both of which have caused a silent no-op before:

- `AapProjectionActivity` must be **resumed**.
- `key-codes` must be absent from `settings.xml` (§3), or the code arrives remapped.

If route 1 turns out not to work on this phone, the Microntek pair against
`…connection.carkey.CarKeyBroadcastReceiver` and the same pair reversed are both known to reach the
app; round 1's brief has them. Report which route was used.

### 4b. Raise the phone's microphone tags, before the first launch

```bash
# Android Auto's developer settings must be on, by hand on the phone:
#   Android Auto settings -> Version -> tap ~10 times -> Developer settings -> enable

for t in CAR.GAL.MIC CAR.GAL CAR.AUDIO GH.CarMicRecorder GH.PhoneMicRecorder GH.Audio CAR.SERVICE; do
  adb -s <phone> shell setprop log.tag.$t VERBOSE
done
adb -s <phone> shell am force-stop com.google.android.projection.gearhead
```

**The force-stop goes here, before the head unit is launched, and nowhere else.** Verify it took by
pid before spending a run on it.

The phone-side strings, all confirmed present in `17.5.663204`:

```
Using phone microphone
Not using phone mic
microphone timed out; no data received for %d
Received message with invalid type header: %d
Audio config received has wrong number of bits %d
```

**`Received message with invalid type header` is not expected on either arm, and is not the A/B.**
Arm A's bytes 4 and 5 are the top two bytes of an 8-byte millisecond timestamp, which are zero, so the
phone reads type 0 and accepts the message. Grep it anyway: on arm C it must stay at zero, and a
non-zero count there is a FAIL saying the new frame layout is wrong in some other way.

---

## 5. Runs

### G0. Build gate

Build and unit-test both arms (`build_hur.sh`, `run_unit_tests.sh`). Copy each APK out of `apks/` as
soon as it is built (§7a: the script deletes the previous one). Report both md5s.

Expected, unchanged from round 1: **arm A 765, arm C 833**, and the eight suites at 9 / 10 / 10 / 4 /
5 / 3 / 7 / 7. A different number here means the wrong SHA is checked out; stop and say so.

### G1. The gate that decides the round: does this phone open the microphone?

**Run this first, and stop here if it fails.** Arm C only, `log-level=0`.

1. Bring one Native AA session up, projection in the foreground, settled 30 s.
2. One route-1 trigger. Leave it open about five seconds, speaking a sentence out loud.
3. Keep capturing 30 s.

```bash
grep -ac "Voice Session Notification: START" g1.txt
grep -ac "Mic request: open: true"           g1.txt
grep -ac "mic uplink started"                g1.txt
grep -ac "MIC Media Data"                    g1.txt
```

- **PROCEED:** `Mic request: open: true` at least once **and** `MIC Media Data` non-zero. The phone is
  asking for the microphone and bytes are going out. Run M1 through M5.
- **STOP:** `Voice Session Notification: START` fires but `Mic request: open: true` is zero, which is
  exactly round 1's signature on a different phone. Do not run M1 to M5; they will all be UNTESTABLE
  for the same reason and will cost the visit for nothing.

  If it stops here, collect these three things and nothing else, then finish the round:
  - the phone-side reason, from its own capture. Round 1's was `Cannot connect to Gemini` with
    `Assistant.Controller` at `LIVE_INACTIVE`. Report whatever this one says, verbatim.
  - the default-network reading from §2 step 2, taken **during** the live session this time, not
    before it.
  - whether Gearhead has bound its process to the projection network:
    ```bash
    adb -s <phone> shell dumpsys connectivity | grep -i -B2 -A6 "bound\|per-app\|gearhead"
    ```
    That last one separates "this phone has no route" from "Gearhead is sending its assistant traffic
    down our link, which has no upstream". Nobody has ever checked it, and it decides whether the
    queued hotspot round is worth running at all.

### M1. The point of the round: the message carries its type, and the phone understands it

Arms A and C, `log-level=0`, `mic-sample-rate=16000`, identical procedure. One session each, Native
AA, projection in the foreground and settled for 30 s.

1. Four assistant sessions, roughly 20 s apart, each left open about five seconds. **Room silent for
   the first three.**
2. On the fourth, say a fixed sentence out loud, close enough to the head unit to be heard. Use the
   same sentence on both arms, with unambiguous words: "navigate to the nearest petrol station".
3. Keep capturing 30 s after the last one.

```bash
grep -ac "Mic request: open: true"                    m1-<arm>.txt
grep -ac "mic uplink started"                         m1-c.txt
grep -ac "mic uplink |"                               m1-c.txt
grep -ac "MIC Media Data type: 0 .* dataOffset: 6"    m1-c.txt
grep -ac "MIC Unknown (-1) type: -1 .* dataOffset: 2" m1-a.txt
grep -ac "Received message with invalid type header"  <phone capture>
```

- **PASS on the frame:** arm C prints `dataOffset: 6` on every microphone message and `dataOffset: 2`
  on none; arm A the reverse. Report the counts, and one full `mic uplink |` line per session with its
  `% of expected`.
- **PASS on the phone:** the fourth session transcribes the sentence correctly on arm C. Report what
  each arm transcribed, verbatim, including a wrong answer, because a *wrong* transcription and *no*
  transcription mean different things: the first says the audio arrived misaligned, the second says it
  did not arrive.
- **FAIL:** arm C's summary is absent after a `Mic request: open: true`, or its `% of expected` is
  below 80 on a session that ran its full five seconds. Report `emptyReads` and `bytes` from the paired
  capture summary, which says whether the loss was upstream of the uplink.
- **Pre-registered INCONCLUSIVE, not a FAIL:** arm A transcribes correctly too. The defect degrades
  rather than fails, so one sample of misalignment may be inaudible to the recogniser. Then the
  verdict rests on the frame counts and on M2, and say so.

Also report, from arm C: `peak=` on the three silent sessions against `peak=` on the spoken one. A peak
that does not move between them means the input is routed nowhere and `mic-input-source` is the next
thing to change, regardless of everything else in this run.

### M2. Positive control: the rate picker no longer reaches the wire

Arms A and C, `mic-sample-rate=48000`, app stopped for the write. One session each, one spoken
assistant session using the same sentence as M1.

| Check | Arm A | Arm C |
|---|---|---|
| `Initializing AudioRecord ... SampleRate:` | **48000** | **16000** |
| the announced microphone rate | 16000 | 16000 |
| `capture summary ... rate=` | 48000 | 16000 |
| transcription | the run's subject | must match M1's |

- **PASS:** arm C behaves exactly as it did at 16000, and arm A opens 48 kHz capture under a 16 kHz
  announcement. That mismatch is the defect three reporters found, and this is the run that puts it on
  the record.
- **FAIL:** arm C prints `SampleRate: 48000`.
- **Also report:** whether arm A's transcription differs from its own M1 transcription. If it does, the
  mechanism is demonstrated end to end and that is the strongest single result this round can produce.
  If it does not, report both verbatim and leave it there; do not argue the point from the counts.

No fallback line (§4) should appear on arm C in either run. Report the count as zero explicitly.

### M3. Whole 2048-frame messages

Arm C only, free from M1's and M2's captures if a session ran the full five seconds. Otherwise one
extra session with a long utterance: dictate a full sentence, because a short wake phrase can complete
inside one 128 ms chunk and prove nothing.

From every `mic uplink |` line:

| Check | Condition |
|---|---|
| every message is whole | `largest=4096B` and `smallest=4096B` |
| the tail is dropped, not padded | `discarded=` is between 0 and 4095 |
| nothing accumulates across sessions | `discarded=` never exceeds 4095 on any line |

and from the VERBOSE capture, `size: 4110` on every `MIC Media Data` line, with no other size present.

- **PASS:** all four, on every session in the round.
- **FAIL:** `largest` and `smallest` differ, or any `size:` other than 4110 appears on the microphone
  channel.
- **Ask explicitly:** did the assistant feel slower to respond than on arm A? The chunker costs 128 ms
  of uplink latency by construction. A yes is not a FAIL, but it is the number that decides whether
  1024 frames is worth an experiment later.

### M4. The phone's request is answered, and its stop is honoured

Arm C only, `log-level=1`. Two assistant sessions: let the first run to its natural end, and **cancel
the second** part-way through with a `KEYCODE_BACK` or a second trigger.

Report, verbatim and in full, the first `Mic request:` line of the session and every line that follows
it. It is a protobuf `toString`, one field per line: `open` is field 1 and always present, and
`anc_enabled`, `ec_enabled` and `max_unacked` are fields 2 to 4 and **optional**. **No reporter log has
ever contained them.** Those three are a deliverable of this round in their own right, independent of
any PASS or FAIL, because they decide whether honouring the flags and implementing a real uplink window
are worth writing at all. **If they are absent, that is the answer, not a missed capture**: it means
this Gearhead does not send them, and report it that way rather than looking for them elsewhere.

Then:

- **PASS:** exactly two `MIC Mic Response type: 32774` per microphone session, one at open and one at
  close; the `mic uplink |` summary appears at the **close of each session**, not only at the
  disconnect at the end of the capture; and the cancelled session produces its summary at the moment of
  the cancel.
- **FAIL:** a `Mic request: open: false` or a `Media Sink Stop Request: MIC` with no summary after it,
  which means the recorder is still running.
- **Measure, no PASS condition:** `acks=` in each summary. Zero means Gearhead does not acknowledge the
  microphone channel at all, which retires the whole flow-control question. Non-zero means it does, and
  the ratio of `acks` to `frames` is the first real evidence of what `max_unacked` is worth.

### M5. The head unit microphone off, and what the phone does about it

Arm C only, `use-head-unit-microphone=false`, app stopped for the write, phone tags already raised per
§4b. Capture both devices.

1. Bring a session up.
2. Confirm the session **establishes and stays up**, with video on screen. This is the risk: the
   microphone service is still announced, so the phone's required-service check should pass, but that
   is the assumption the run exists to test.
3. Two assistant sessions, spoken.

Head unit side:

- **PASS:** `AapTransport: not taking the microphone (setting useHeadUnitMicrophone=false,
  available=true)` once at session start; `Mic request: the head unit microphone is off in Settings` on
  each request; **zero** `Initializing AudioRecord`, **zero** `mic uplink started`, **zero** `capture
  summary`; and no `Byebye Request` attributable to the microphone.
- **FAIL:** the session tears down, or `AudioRecord` is constructed anyway.

Phone side, and this is the question two reporter threads actually ask:

- Report the count of `Using phone microphone` and `Not using phone mic`, with their timestamps
  relative to the decline.
- Report whether `microphone timed out; no data received for` fires, and after how long.
- Report whether the assistant answered at all, and whether it heard the phone's own microphone.

**There is no PASS condition on the phone side.** Either answer is a result. If the phone takes over,
both reporters get what they asked for. If it times out instead, the setting still stops the head unit
taking the microphone, which is the trade they described wanting, and the answer to give them is
different. Do not score it; report it.

---

## 6. Do not re-run

- **M6a.** Answered, FAIL, blocks the branch. `AapService.onCreate` throws before any phone is
  involved, so a new phone cannot move it.
- **M6b's session half, and M7.** Both passed and neither depends on the phone.
- **The three-route trigger comparison.** Answered in round 1. Use route 1.
- **Anything on the hotspot transport.** `native-ap-transport` stays `0` for this whole round. If the
  microphone still never opens, that is the *next* round, not this one.

---

## 7. Report back: the five things that decide whether this ships

1. **The assistant this phone invokes**, and whether G1 passed. If G1 stopped the round, its three
   collected items are the whole report and that is a complete result, not a failed visit.
2. **M1:** the `dataOffset: 6` against `dataOffset: 2` counts, both arms, and both transcriptions
   verbatim.
3. **M2:** `SampleRate:` on both arms, and whether arm A's transcription moved.
4. **M4:** `anc_enabled`, `ec_enabled`, `max_unacked` and `acks=`, verbatim. These are worth having
   even if every other run is INCONCLUSIVE.
5. **M5's phone side:** whether the phone takes the microphone back, or times out.

Setup notes should carry, as always: the scripts used, the `settings.xml` delta at round start, any
discard-rule hits with what caused them, and anything substituted for a step in this brief.
