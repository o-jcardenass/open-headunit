# mic-uplink, round 1 brief: the microphone message has never carried its own type

**Baseline (A):** `origin/main` @ `ea7aa7e0` (3.3.0-beta1, `andreknieriem/open-headunit`).
**Candidate (C):** `fork/fix/mic-uplink` @ `42302130` (7 commits on top of `fix/audio-start-and-teardown`).

**Both branches were rewritten on 2026-08-25 and the old SHAs are gone.** The WiFi Direct group
bring-up guard was dropped from `fix/audio-start-and-teardown`, so neither branch touches WiFi Direct
any more and `WifiDirectManager.kt` is identical to `main` on both. Nothing else changed: every
remaining commit differs from its old counterpart by exactly that one revert. Fetch fresh and reset;
an old checkout is orphaned. The pre-rewrite tips are tagged `audio-round1-validated` (`3d3fe465`,
the APK round 1 measured) and `audio-and-mic-pre-group-guard-drop` (`3cbb6dba`).

```bash
git fetch origin && git fetch fork
git checkout -B main-a     origin/main            # must print ea7aa7e0...
git checkout -B mic-uplink fork/fix/mic-uplink    # must print 42302130...
git rev-parse HEAD
```

Two APKs for this round.

| Commit | What it changes |
|---|---|
| `b3e07af8` | `MicUplinkFrame`, `MicUplinkMonitor`, the transport's send path |
| `bac0c057` | `MicCaptureFormat`, the rate picker, `MicCaptureRatePolicy`, `MicPcmDecimator` |
| `21294e65` | `MicrophoneResponse`, the stop arm on channel 7, `Mic request` at INFO |
| `0be7cadd` | `MicChunkAccumulator` |
| `2489eb6f` | `MicrophonePolicy` and the `use-head-unit-microphone` setting |
| `71e2c13a` | `ForegroundServiceTypePolicy`, the manifest's microphone service type |
| `42302130` | `BluetoothAddressSeedPolicy` and the address fill at service start |

This is a **new thread**. Nothing else on this branch needs reading.

---

## 0. Run order, and the arm that is no longer part of this visit

`fix/mic-uplink` is stacked on `fix/audio-start-and-teardown` (`455e76e0`), **whose round is now
done** — see `audio-start-and-teardown-round1-results.md`. Nothing needs re-running there, so this
visit is two APKs, not three.

| Arm | SHA | Used by |
|---|---|---|
| A | `ea7aa7e0` | this round |
| C | `42302130` | this round |

Arm B was the audio round's own candidate and is referenced below only where a line is described as
having appeared on it. It measured `3d3fe465`, now tag `audio-round1-validated`; the branch has since
dropped its WiFi Direct commit and is `455e76e0`. Build neither for this round.

---

## 1. Why this round exists

Seven reporter threads land on audio input: the microphone dead, the assistant hearing nothing after
the wake word, the far end hearing the reporter break up, and two riders whose Bluetooth helmet
intercom loses its microphone the moment the head unit connects. The code review found the input path
wrong in four independent ways, and found that no reporter log can say whether a single microphone
byte ever left the head unit.

**The message is missing its type.** `AapMessageFraming` states this repo's own rule: flag bit 0 set
means a 2-byte message type at payload offset 0. The receive side obeys it twice. The microphone
send path never has. `AapTransport.onMicDataAvailable` on `main` writes `[timestamp:8][PCM]` where
the phone expects `[type:2][timestamp:8][PCM]`, and passes `type = -1, dataOffset = 2` to a message
class whose own `toString` then prints `Unknown (-1)`. It parses at all only because
`elapsedRealtime()` in milliseconds has two leading zero bytes, which the phone reads as type 0. The
phone then takes six real timestamp bytes plus the first PCM sample as its timestamp, and starts the
audio one sample late on every message of every session. Two commits put this where it is:
`42fcd389` placed the timestamp at byte 2 where the length field overwrote it, and `dc039351`
(v.3.2.1, still in `main`) moved it to byte 4 and left the type unwritten. The clock unit is wrong
too: milliseconds here, microseconds in aasdk, openauto and every other AAP media producer.

**The announced rate and the captured rate are different numbers.** `ServiceDiscoveryResponse` on
`main` hardcodes 16000 / 16 / 1 in the microphone service. `MicRecorder` opens `AudioRecord` at
`settings.micSampleRate`, which the settings screen offered as 8000, 16000, 24000, 32000, 44100 or
48000, and there is no resampler on the input path. At 48000 the phone is handed 48 kHz PCM under a
16 kHz declaration. Three reporters found this independently and reported the same remedy.

Android Auto 17.5's own `AudioConfiguration` validator carries the literals `48000, 16000, 16, 2, 1`
beside `wrong sampling rate `, `wrong number of bits ` and `wrong number of channels `, so four of
the six offered values were rates the phone rejects outright and could never have been announced.
Google's Head Unit Integration Guide v1.3 is narrower still: the microphone MUST be PCM, 16 kHz,
16-bit, mono, in **2048-frame** buffers, with AGC and single-microphone noise reduction disabled.
`MicRecorder` on `main` reads into a buffer of exactly `getMinBufferSize()` and forwards whatever
`read()` returned; reporter logs show 640 and 768 frames. It also silently drops any read of 64 bytes
or fewer.

**The phone's request is half-answered.** `MicrophoneResponse` (0x8006) exists in `media.proto` and
in Gearhead's own message enum, and `main` replies with nothing. `anc_enabled`, `ec_enabled` and
`max_unacked` are parsed and thrown away. A `MediaStopRequest` on channel 7 leaves the recorder
running, because that handler branches on `Channel.isAudio()`, which deliberately excludes MIC.

**Two of the seven are not app defects.** In-call voice audio is routed over Bluetooth HFP/SCO, not
over AAP. Gearhead 17.5 has `AUDIO_STREAM_TELEPHONY` as a name with no producer, no config selection
and no stream mapping anywhere in the APK. One thing there is actionable, and is the last commit:
`main` announces the `BluetoothService` only when `settings.bluetoothAddress` is non-empty, and that
is a hand-typed field, so a head unit with a real Bluetooth car kit that leaves it blank never tells
the phone where to connect.

---

## 2. What the candidate does

**The frame.** `MicUplinkFrame` writes `[0]` channel 7, `[1]` flags `0x0b`, `[2..3]` zero for
`sendEncryptedMessage` to fill, `[4..5]` `MEDIA_MESSAGE_DATA` (0) big-endian, `[6..13]` the timestamp
in **microseconds** big-endian, `[14..]` PCM. The `> 64` drop becomes `<= 0`. `SystemClock.elapsedRealtimeNanos()`
is API 17 and the `github` flavor's minSdk is 16, so it is guarded and falls back to
`elapsedRealtime() * 1000`.

**The rate.** `MicCaptureFormat` owns 16000 / 16 / 1 / 2048 frames, and the announcement, the
recorder, the chunker and the monitor's percentage all read it, so they cannot drift. The picker
drops to `listOf(16000, 48000)` with the migration inline in the getter, so a stored 8000, 24000,
32000 or 44100 reads back as 16000. `settings.micSampleRate` is now only a **fallback preference**:
`MicCaptureRatePolicy` tries 16000 first and reaches for anything else only if
`getMinBufferSize(16000, ...)` fails, in which case `MicPcmDecimator` converts 3:1 to 16 kHz before
the listener sees a byte.

**The chunk.** `MicChunkAccumulator` emits whole 4096-byte messages, stamped with the capture time of
their **first** byte rather than the emit time, and **discards** the tail at stop rather than padding
it, because padding injects synthetic silence into the recogniser's stream and a short frame breaks
the multiple-of-2048 rule. The discarded count is reported. The cost is 128 ms of uplink latency.

**The answer.** `MicrophoneResponse` goes out on open and on close with a real status, so
`MicRecorder.start()` now returns named error codes instead of 0-on-failure. A `MediaStopRequest` on
channel 7 gets its own arm. `Mic request` is promoted from DEBUG to INFO, which puts `anc_enabled`,
`ec_enabled` and `max_unacked` in every future reporter log for one character.

**The instrument.** `MicUplinkMonitor` reports, per microphone session rather than per rolling
window, what actually went on the wire. Paired with `MicRecorder`'s capture summary from arm B, it
separates "the microphone is routed nowhere" from "we heard it and never sent it", which is the
distinction no reporter log has ever been able to make.

**The setting.** `use-head-unit-microphone`, default true. Off, the service is still announced, the
listener is never wired, `AudioRecord` is never constructed, and the request is declined with a
non-zero status. Omitting the service is not an option: `startRequiredServices` in Gearhead is a hard
check list carrying `No audio/mic`, and the teardown enum carries `NO_AUDIO_MIC`.

**The service type.** `FOREGROUND_SERVICE_MICROPHONE` and the `microphone` type are in the manifest,
and `ForegroundServiceTypePolicy` adds the type to the mask only when `RECORD_AUDIO` is held and the
setting is on, because since Android 14 claiming it without the permission throws.

**The address.** `AapService.onCreate` fills `bt-address` from `BluetoothHelper.getBluetoothMacAddress()`
when it is blank, and `BluetoothAddressSeedPolicy` never overwrites a value the user typed.

---

## 3. What is different about this round

- **The assistant can be scripted, and this is the first round to do it.** `KeyCode.convert` maps
  `KEYCODE_VOICE_ASSIST` to `KEYCODE_SEARCH` (84), which is in the AAP whitelist, so the Microntek
  broadcast pair reaches `CommManager.sendKey`. §5a has the recipe, **corrected** after the
  `audio-start-and-teardown` round 1 pass tried the earlier version and never reached the app.
  **Whether Android Auto opens the microphone in response is still unproven on this rig**, and if it
  does not, most of this round is UNTESTABLE. Establish that in M1's first two minutes before
  spending the round.
- **`log-level` is `0` (VERBOSE) for M1, M2 and M3, and `1` (DEBUG) everywhere else.** The send line
  the frame runs depend on prints only at VERBOSE. Both `AapDump` hex-dump call sites are commented
  out on both arms, so VERBOSE costs the `RECV:` lines and nothing worse, but it is still the largest
  capture this channel produces. Keep those three runs short.
- **One step in this round cannot be unattended, and only one.** Every count, size and percentage
  below comes off the log with the room silent. Only the transcription check in M1 and M2 needs a
  person to speak, and it is deliberately the last thing in each.
- **The phone's log is half the round.** M5 cannot be answered from the head unit at all. §5a raises
  the Gearhead tags.
- **The rate fallback cannot be exercised here.** Nothing is known to refuse 16 kHz mono, and there is
  no lever to make `getMinBufferSize` fail. `MicCaptureRatePolicyTest` and `MicPcmDecimatorTest` carry
  it. Do not spend time on it; it is not a FAIL.
- **`key-codes` must be absent from `settings.xml`** or an injected keycode arrives remapped and the
  assistant never opens. Check before M1, not after.
- **M6a is the risk this round is underwriting**, and it is not about the microphone at all. Read it
  before scheduling, because a FAIL there blocks the whole branch.

---

## 4. Settings keys

| Key | Type | Value | Runs |
|---|---|---|---|
| `log-level` | int | `0` (VERBOSE) | M1, M2, M3 |
| `log-level` | int | `1` (DEBUG) | M4, M5, M6, M7 |
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
```

`use-head-unit-microphone` does not exist on arm A, and writing it there is harmless. The three
effect toggles are pinned off because the integration guide requires AGC and single-microphone noise
reduction disabled, and because a rig with one of them on would make M1's peak numbers
incomparable to any reporter's.

`bt-address` is not pinned. **Read it and report it before the round starts, and again after the
first arm-C launch** (M6b uses both readings). If it is already set by hand from an earlier round,
say so, because that is the branch of `BluetoothAddressSeedPolicy` that must leave it alone.

---

## 5. The lines that decide the round

Candidate only, INFO:

```
AapTransport: mic uplink started (channel MIC, type 0, timestamps in microseconds, 4096B messages)
AapTransport: mic uplink | <n> frames, <n> B in <n>ms (<p>% of expected), peak=<n>/32767, largest=<n>B, smallest=<n>B, acks=<n>, discarded=<n>B
AapTransport: not taking the microphone (setting useHeadUnitMicrophone=false, available=true)
AapService: filled in this device's Bluetooth address (<mac>) so the Bluetooth service can be announced; phone calls need it
Mic request: the head unit microphone is off in Settings. Declining and sending nothing, so the phone can use its own microphone and any Bluetooth headset keeps this one
```

Candidate only, WARNING:

```
Mic request: capture did not start (code <n>); telling the phone so rather than leaving it waiting on a stream that will never arrive
Mic request: this device has no usable microphone capture; declining
MicRecorder: 16000 Hz capture is unavailable; capturing at <n> Hz and converting <k>:1 so the phone still receives the rate it was told about
MicRecorder: this device will not open 16000 Hz mono capture, which is the only rate Android Auto accepts, and no whole multiple of it either. The microphone is unavailable
```

The last two are the fallback path. Neither is expected to appear; both being absent is the result.

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
MicRecorder: No RECORD_AUDIO permission
MicRecorder: RECORD_AUDIO is granted but this ROM has revoked the
Voice Session Notification: START
Voice Session Notification: STOP
BT MAC Address is empty, so no Bluetooth service is announced.
CarKeyReceiver: Handling intent action:
RemoteControlReceiver received: com.android.music.musicservicecommand
RemoteControlReceiver: Transport not started, skipping command execution
MATCH! Starting AapService
createGroup SUCCESS
```

`MicRecorder: capture summary` exists on arm C and on arm B, and **not** on arm A. It is the capture
half of the pair; `mic uplink |` is the send half. Read them together, always.

Grep everything with `-a` (`TESTING-TEMPLATE.md` §7a), no exceptions.

### 5a. Triggering the assistant from adb

The app's key path is reachable through two exported, manifest-registered, permissionless receivers.
**This recipe was corrected after the `audio-start-and-teardown` round 1 pass, where the earlier
version never reached the app on three attempts.** The component it named,
`com.andrerinas.openheadunit.connection.CarKeyReceiver`, has no `<receiver>` entry in the manifest and
is referenced by nothing in the app; `b05a4b89` split it and left the class orphaned. An
`am broadcast -n` naming a component with no manifest entry is enqueued by `ActivityManager` and then
dropped, which is exactly what that round saw. Two routes are given below because which one Android
Auto answers has never been measured here.

**Route 1, the simplest. Try this first.**

```bash
PKG=com.andrerinas.headunitrevived
RC=$PKG/com.andrerinas.openheadunit.app.RemoteControlReceiver

adb shell am broadcast -n $RC -a com.android.music.musicservicecommand --es command voice
```

One broadcast sends `KEYCODE_SEARCH` down and up together (`RemoteControlReceiver.kt:91-93`). It is
the only route that says out loud why it did nothing: it checks the transport first and logs
`RemoteControlReceiver: Transport not started, skipping command execution` at INFO, which separates
"we are not projecting" from "the broadcast never landed" without reading anything else.

**Route 2, the Microntek pair.** Separate DOWN and UP, which is the shape a real head unit's voice
button has, and worth having if route 1 turns out not to open the assistant.

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.connection.carkey.CarKeyBroadcastReceiver

adb shell am broadcast -n $RX -a com.microntek.irkeyDown --ei keyCode 84
sleep 0.2
adb shell am broadcast -n $RX -a com.microntek.irkeyUp   --ei keyCode 84
```

84 is `KEYCODE_SEARCH`. 231 (`KEYCODE_VOICE_ASSIST`) also works and is converted to 84 on the way
out; send 84 so the log shows what was sent.

**Fallback, route 2 with the pair reversed.** `CommManager.sendToggleVoiceAssistant()`
(`CommManager.kt:724-725`) opens the assistant by sending UP first and then DOWN, with the comment
"up/down must be reversed". Nothing in the app calls that function, so its order is not proof of what
Android Auto wants, but it is the only statement in the source about it. If both routes above reach
the app and neither opens the assistant, send `irkeyUp` first, then `irkeyDown`, before concluding
anything.

```bash
adb shell am broadcast -n $RX -a com.microntek.irkeyUp   --ei keyCode 84
sleep 0.2
adb shell am broadcast -n $RX -a com.microntek.irkeyDown --ei keyCode 84
```

**Report which route opened the assistant**, in Setup notes, whichever it was. That answer is worth
more than this run: every future brief in this channel that needs the assistant will name one route
instead of three.

Three preconditions, all of which have caused a silent no-op before:

- `AapProjectionActivity` must be **resumed**. Its receiver is registered in `onResume`, so a
  backgrounded projection swallows the second delivery.
- `key-codes` must be absent from `settings.xml` (§4) or the code arrives remapped.
- `sendKey` returns silently unless the transport is started, so `CarKeyReceiver: Handling intent
  action:` at INFO is what separates "the broadcast never landed" from "we are not projecting". That
  line is unchanged by the component correction: `CarKeyBroadcastReceiver.onReceive` logs the same
  string. Route 1 logs `RemoteControlReceiver received:` instead.

One note on delivery counts. `CarKeyBroadcastReceiver` is registered both in the manifest and at
runtime by `CarKeysManager`, but an explicit `-n` broadcast can only reach the manifest instance, so
it is delivered **once**. A real OEM key arrives implicitly and is delivered twice. If a run ever
needs the double delivery, drop `-n` and send the action implicitly.

**Confirm the trigger works before running anything on it.** One session, then each route in turn,
looking for `Voice Session Notification: START` followed by `Mic request: open: true`. If none of the
three produces it, report the whole round from M1 onward as UNTESTABLE with the capture attached, say
which routes reached the app and which did not, and run M6a and M7 anyway; both are independent of
the microphone opening.

### 5b. Raise the phone's microphone tags before M5

Same method as `usb-session-teardown-round1-brief.md` §5a, different tags.

```bash
# Android Auto's developer settings must be on, by hand on the phone:
#   Android Auto settings -> Version -> tap ~10 times -> Developer settings -> enable

for t in CAR.GAL.MIC CAR.GAL CAR.AUDIO GH.CarMicRecorder GH.PhoneMicRecorder GH.Audio CAR.SERVICE; do
  adb -s <phone> shell setprop log.tag.$t VERBOSE
done
adb -s <phone> shell am force-stop com.google.android.projection.gearhead
```

Verify it took before spending a run on it, by pid, exactly as that brief describes.

The phone-side strings, all confirmed present in `17.5.663204`:

```
Using phone microphone
Not using phone mic
microphone timed out; no data received for %d
Received message with invalid type header: %d
Audio config received has wrong number of bits %d
```

**`Received message with invalid type header` is not expected on either arm, and is not the A/B.**
Arm A's bytes 4 and 5 are the top two bytes of an 8-byte millisecond timestamp, which are zero, so
the phone reads type 0 and accepts the message. Grep it anyway: on arm C it must stay at zero, and a
non-zero count there is a FAIL that says the new frame layout is wrong in some other way.

---

## 6. Runs

### M0. Gate

Build and unit-test **both** arms (`build_hur.sh`, `run_unit_tests.sh`). Copy each APK out of `apks/`
as soon as it is built (§7a: the script deletes the previous one). Record suite totals and,
separately, these eight:

| Suite | Expected on C |
|---|---|
| `MicUplinkFrameTest` | 9 |
| `MicUplinkMonitorTest` | 10 |
| `MicChunkAccumulatorTest` | 10 |
| `MicrophonePolicyTest` | 4 |
| `ForegroundServiceTypePolicyTest` | 5 |
| `BluetoothAddressSeedPolicyTest` | 3 |
| `MicCaptureRatePolicyTest` | 7 |
| `MicPcmDecimatorTest` | 7 |

Arm C should total **833**: 765 on arm A, plus 13 from the audio branch underneath
(`AudioPrerollPolicyTest` 12, and `LinkGapMonitorTest` 17 to 18), plus the 55 in those eight suites.
Round 1 measured 765 and 782; 782 became 778 when the group bring-up guard was dropped, which took
`NativeGroupBringUpPolicyTest`'s 4 with it. **This is the first compile of `42302130` anywhere**, so a
missing suite here means the build did not pick up the new source set rather than that the tests
passed.

**PASS:** both arms compile, both suites clean, all eight present at those counts. A failure on
either arm stops the round.

### M1. The point of the round: the message carries its type, and the phone understands it

Arms A and C, `log-level=0`, `mic-sample-rate=16000`, identical procedure. One session each, Native
AA, projection in the foreground and settled for 30 s.

1. Confirm the trigger per §5a.
2. Four assistant sessions, roughly 20 s apart, each opened with the key pair and left open about
   five seconds. **Room silent for the first three.**
3. On the fourth, say a fixed sentence out loud, close enough to the head unit to be heard. Use the
   same sentence on both arms. Something with unambiguous words: "navigate to the nearest petrol
   station".
4. Keep capturing 30 s after the last one.

The counts, per arm:

```bash
grep -ac "Mic request: open: true"              m1-<arm>.txt
grep -ac "mic uplink started"                   m1-<arm>.txt          # C only
grep -ac "mic uplink |"                         m1-<arm>.txt          # C only
grep -ac "MIC Media Data type: 0 .* dataOffset: 6"  m1-c.txt
grep -ac "MIC Unknown (-1) type: -1 .* dataOffset: 2" m1-a.txt
grep -ac "Received message with invalid type header"  <phone capture>
```

- **PASS on the frame:** arm C prints `dataOffset: 6` on every microphone message and `dataOffset: 2`
  on none; arm A the reverse. Report the counts, and one full `mic uplink |` line per session with
  its `% of expected`.
- **PASS on the phone:** the fourth session transcribes the sentence correctly on arm C. Report what
  each arm transcribed, verbatim, including a wrong answer, because a *wrong* transcription and *no*
  transcription mean different things: the first says the audio arrived misaligned, the second says
  it did not arrive.
- **FAIL:** arm C's summary is absent after a `Mic request: open: true`, or its `% of expected` is
  below 80 on a session that ran its full five seconds. Report `emptyReads` and `bytes` from the
  paired capture summary, which says whether the loss was upstream of the uplink.
- **Pre-registered INCONCLUSIVE, not a FAIL:** arm A transcribes correctly too. The defect degrades
  rather than fails, so it is entirely possible that one sample of misalignment is inaudible to the
  recogniser. Then the round's verdict rests on the frame counts and on M2, and say so.

Also report, from arm C: `peak=` on the three silent sessions against `peak=` on the spoken one. A
peak that does not move between them means the input is routed nowhere and `mic-input-source` is the
next thing to change, regardless of everything else in this run.

### M2. Positive control: the rate picker no longer reaches the wire

Arms A and C, `mic-sample-rate=48000`, app stopped for the write. One session each, one spoken
assistant session using the same sentence as M1.

| Check | Arm A | Arm C |
|---|---|---|
| `Initializing AudioRecord ... SampleRate:` | **48000** | **16000** |
| the announced microphone rate | 16000 | 16000 |
| `capture summary ... rate=` | 48000 | 16000 |
| transcription | the run's subject | must match M1's |

- **PASS:** arm C is byte-for-byte the same behaviour as it had at 16000, and arm A opens 48 kHz
  capture under a 16 kHz announcement. That mismatch is the defect three reporters found, and this is
  the run that puts it on the record.
- **FAIL:** arm C prints `SampleRate: 48000`. The rate is still reaching the recorder.
- **Also report:** whether arm A's transcription differs from its own M1 transcription. If it does,
  the mechanism is demonstrated end to end and that is the strongest single result this round can
  produce. If it does not, report both verbatim and leave it there; do not argue the point from the
  counts.

No fallback line (§5) should appear on arm C in either run. Report the count as zero explicitly.

### M3. Whole 2048-frame messages

Arm C only, free from M1's and M2's captures if a session ran the full five seconds. Otherwise one
extra session with a long utterance: dictate a full sentence, because a short wake phrase can
complete inside one 128 ms chunk and prove nothing.

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

Arm C only, `log-level=1`. Two assistant sessions: let the first run to its natural end, and
**cancel the second** part-way through with a `KEYCODE_BACK` or a second press pair.

Report, verbatim and in full, the first `Mic request:` line of the session. Its generated `toString`
carries `anc_enabled`, `ec_enabled` and `max_unacked`, and **no reporter log has ever contained
them**. Those three values are a deliverable of this round in their own right, independent of any
PASS or FAIL, because they decide whether honouring the flags and implementing a real uplink window
are worth writing at all.

Then:

- **PASS:** exactly two `MIC Mic Response type: 32774` per microphone session, one at open and one at
  close; the `mic uplink |` summary appears at the **close of each session**, not only at the
  disconnect at the end of the capture; and the cancelled session produces its summary at the moment
  of the cancel.
- **FAIL:** a `Mic request: open: false` or a `Media Sink Stop Request: MIC` with no summary after it,
  which means the recorder is still running.
- **Measure, no PASS condition:** `acks=` in each summary. Zero means Gearhead does not acknowledge
  the microphone channel at all, which retires the whole flow-control question. Non-zero means it does,
  and the ratio of `acks` to `frames` is the first real evidence of what `max_unacked` is worth.

### M5. The head unit microphone off, and what the phone does about it

Arm C only, `use-head-unit-microphone=false`, app stopped for the write, phone tags raised per §5b.
Capture both devices.

1. Bring a session up.
2. Confirm the session **establishes and stays up**, with video on screen. This is the risk: the
   microphone service is still announced, so the phone's required-service check should pass, but that
   is the assumption the run exists to test.
3. Two assistant sessions, spoken, per §5a.

Head unit side:

- **PASS:** `AapTransport: not taking the microphone (setting useHeadUnitMicrophone=false,
  available=true)` once at session start; `Mic request: the head unit microphone is off in Settings`
  on each request; **zero** `Initializing AudioRecord`, **zero** `mic uplink started`, **zero**
  `capture summary`; and no `Byebye Request` attributable to the microphone.
- **FAIL:** the session tears down, or `AudioRecord` is constructed anyway.

Phone side, and this is the question #784 and #818 actually ask:

- Report the count of `Using phone microphone` and `Not using phone mic`, with their timestamps
  relative to the decline.
- Report whether `microphone timed out; no data received for` fires, and after how long.
- Report whether the assistant answered at all, and whether it heard the phone's own microphone.

**There is no PASS condition on the phone side.** Either answer is a result. If the phone takes over,
both reporters get exactly what they asked for. If it times out instead, the setting still stops the
head unit taking the microphone, which is the trade they described wanting, and the answer to give
them is different. Do not score it; report it.

### M6. The service still starts

Two parts, arm C only. **Read M6a before scheduling the round.**

**M6a. A background start that claims the microphone type.** Since Android 14,
`FOREGROUND_SERVICE_TYPE_MICROPHONE` is a while-in-use type, and this service is started in the
background by `AutoStartReceiver` on the phone's Bluetooth `ACL_CONNECTED`. `ForegroundServiceTypePolicy`
guards the missing-permission case and not this one. This rig is Android 14, so it can settle it.

`RECORD_AUDIO` granted, `use-head-unit-microphone=true`, so the mask carries the microphone type.
Per §7a a force-stopped app's receivers do not fire, so the sequence is: launch the app explicitly,
let a session form, then

```bash
adb shell am start -a android.intent.action.VIEW -d "headunit://exit"
# wait for the service to be gone
adb shell dumpsys activity services | grep -i AapService
# then cycle the phone's Bluetooth to fire ACL_CONNECTED
adb -s <phone> shell svc bluetooth disable && adb -s <phone> shell svc bluetooth enable
```

Five cycles.

- **PASS:** every cycle produces `MATCH! Starting AapService` followed by a service that reaches the
  foreground and forms a session. Report first-frame time for each.
- **FAIL:** any `ForegroundServiceStartNotAllowedException`, `SecurityException` naming
  `foregroundServiceType`, or a service that dies before its first frame. **A FAIL here blocks the
  branch**, not just this commit: it would mean the app cannot autostart on Android 14 for anyone.
  Attach the whole capture and the exception in full.
- Per §7a's own note, the phone's Bluetooth self-reverts sometimes and does not always come back.
  Verify with `dumpsys` each cycle rather than assuming the command took.

**M6b. The permission revoked.**

```bash
adb shell pm revoke com.andrerinas.headunitrevived android.permission.RECORD_AUDIO
```

One session, one assistant trigger, then restore with `pm grant` and say in Setup notes that it was
restored.

- **PASS:** the service starts and a session forms; the microphone service is still announced;
  `MicRecorder: No RECORD_AUDIO permission` or the app-op variant appears; `Mic request: capture did
  not start (code -3)` follows the request; and a `MIC Mic Response` still goes out. Arm A sends
  nothing at all in this situation and leaves the phone waiting, which is what this replaces.
- **FAIL:** the service does not start, or the request is answered with a success status.
- Report **which** of the two permission lines appeared. They distinguish a denied permission from a
  ROM-revoked app-op, and one reporter chased a granted permission for weeks because the old message
  named only the first.

Also in M6b, from the same capture: report `bt-address` read from `settings.xml` before the first
arm-C launch and after it, plus the count of `AapService: filled in this device's Bluetooth address`.

- If it was blank before: the line fires **once**, the value is a valid MAC, and
  `BT MAC Address is empty` disappears from service discovery.
- If it was already set by hand: the line fires **zero** times and the value is unchanged. That is
  the branch of `BluetoothAddressSeedPolicy` that matters, since overwriting a hand-typed address
  would break the workaround that makes calls work on units whose own address reads masked.

### M7. Clean control

Arm C, `log-level=1`, one uninterrupted 10-minute session, music playing throughout, **no assistant
trigger at all**, nothing touched.

Every one of these must be zero:

```bash
grep -ac "mic uplink started"                m7.txt
grep -ac "Initializing AudioRecord"          m7.txt
grep -ac "capture summary"                   m7.txt
grep -ac "Mic request"                       m7.txt
grep -ac "Audio queue is full"               m7.txt
grep -ac "disabled due to previous underrun" m7.txt
```

and `createGroup SUCCESS` is 1.

Pair it with the numbers that prove the session was real: throughput fps per window, the count of
`Media Start Request AUDIO`, and that audio was audible for the full ten minutes. A silent session
scores six zeroes and means nothing.

**PASS:** all six zero, one group, audio continuous, video steady. The point is that nothing in seven
commits of microphone work touches a session that never opens the microphone.

---

## 7. Do not re-run

Nothing from this thread. This is its first round.

From the neighbouring thread: **do not re-run any part of `audio-start-and-teardown-round1-brief.md`
on arm C.** Its arms are A and B, and arm C's `MicRecorder` differs.

---

## 8. Report back

`mic-uplink-round1-results.md`, in `TESTING-TEMPLATE.md` §7's format.

The four things that decide whether this ships:

1. **M1:** `dataOffset: 6` against `dataOffset: 2`, and what each arm transcribed.
2. **M2:** `SampleRate:` on each arm at `mic-sample-rate=48000`.
3. **M4:** `anc_enabled`, `ec_enabled` and `max_unacked`, verbatim, plus `acks=`. These are a
   deliverable whatever the verdicts are.
4. **M6a:** five background starts, all forming a session. A FAIL blocks the branch.

Plus the one that answers two reporters: **M5**, what the phone did when the head unit declined.

State in Setup notes the `bt-address` reading before the round, the `settings.xml` delta at the start
even if it is zero, whether `key-codes` was absent, whether the §5a trigger worked and on which
attempt, and whether `RECORD_AUDIO` was restored at the end.
