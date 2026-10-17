# Helmet intercom + Open Headunit native mode: findings and code hand-off

Not a hardware round. A context transfer combining a web-research pass (device identity + how
motorcycle riders route projected audio), a read of the local prior research
(`headunit-modding/`, `code-researchs/`), and a source read of this repo's audio path.

The user's goal: on the motorcycle, **all** Android Auto audio (music, navigation prompts, calls)
in the **helmet intercom**, with Open Headunit in **native AA wireless mode**
(`wifiConnectionMode == 3`). Head unit is a map/touch screen only. Decisions locked with the user:
intercom pairs to the **phone**, not the head unit.

**Revised 2026-08-28.** The first version of this document proposed gating `ID_AU2` and `ID_MIC` out
of `ServiceDiscoveryResponse` behind a new mode, with three design options and two questions to
measure first. Both premises turned out to be wrong, and the work has since been written a different
way. §3 and §5 below are the corrections; §7 is what exists now.

---

## 1. The head unit is a Linkifun MT23 / EKIY MTC23

Board string `MT50_YT610E4GFPSL_U` = UNISOC T610, Android 14, 4 GB RAM. Standalone portable
motorcycle head unit + dual-1080p dash cam (6" IP67, 4G-SIM data only, TPMS, GPS). One Shenzhen ODM
board resold by **Linkifun as "MT23"** and **EKIY as "MTC23" / "M23"**. "MT50" is the ODM's
internal platform code. Projection middleware is **ZLink** (Beijing Zjinnova).

- XDA owners' thread: `xdaforums.com/t/linkifun-mt23-ekiy-mtc23-motorcycle-head-unit.4752522/`
- Linkifun single-vs-dual Bluetooth explainer:
  `linkifun.com/blogs/news/motorcycle-display-single-bluetooth-vs-dual-bluetooth-what-s-the-difference-in-real-use`

**Single Bluetooth radio, one connection at a time.** Confirmed by Linkifun's own docs and by this
project's native-AA handshake testing on this exact unit (zero secondary-radio listener activity;
`NATIVE_AA_BLUETOOTH_HANDSHAKE_CONTEXT.md`). The OEM ZLink app monopolises that one radio during a
wireless session, which is why "ZLink wouldn't let me connect a headset" (Dasaita / ugode.co.uk /
aahacks all document the same limitation). The unit is an A2DP **source** only for its **own local
Android audio**; it is not marketed or documented as bridging CarPlay/AA audio to an intercom (that
is a dual-BT-model feature, e.g. Carpuride "BT Trans Mode", Chigee multi-source).

Prior local research only ever called it "UNISOC MT50" and never linked the retail names.

## 2. How riders normally solve this, and why projection breaks the normal path

Standard motorcycle topology with a standalone screen or a wireless AA/CP adapter: **intercom
paired to the phone**, phone keeps the audio and streams it phone→intercom over its own A2DP/HFP,
the screen is fed video only. Chigee, Linkifun (single-BT), Fodsports and independent guides all
recommend exactly this.

The known limitation: **wireless Android Auto routes essentially all audio over the projection link
to the head unit**, so a phone-paired headset goes silent for AA audio. The community workaround is
AAWireless's **"Disable media sink"**, which tells the AA receiver not to take those audio streams
so they stay on the phone.

Open Headunit in native mode is an AA receiver and needs the same kind of opt-out. It has one.

## 3. What `enableAudioSink` actually covers, and what it does not

`Settings.enableAudioSink` (default `true`, `utils/Settings.kt`; UI string `enable_audio_sink`,
"If disabled, the headunit will not receive audio. Sound will play from the phone.").

In `aap/protocol/messages/ServiceDiscoveryResponse.kt`:

| Channel | Const / id | Declared as | Advertised when `enableAudioSink == false`? |
|---|---|---|---|
| Media | `ID_AUD` / 6 | `AudioStreamType.MEDIA` | **No** |
| Speech | `ID_AU1` / 4 | `AudioStreamType.SPEECH` | **No** |
| System | `ID_AU2` / 5 | `AudioStreamType.SYSTEM` | Yes, always |
| Mic source | `ID_MIC` / 7 | `MediaSourceService`, PCM | Yes, always |

**Correction 1: `ID_AU2` is not the navigation channel.** It is declared `SYSTEM`
(`media.proto`, `AudioStreamType`: `SPEECH = 1, SYSTEM = 2, MEDIA = 3`). Only this app's own volume
setting calls it "navigation" (`AapAudio.kt`, `navigationVolumeOffset` / `navGain`). Turn-by-turn
TTS is Android Auto's SPEECH stream, which is `ID_AU1`, which is **already** inside the
`enableAudioSink` gate. The first version of this document claimed a rider would still hear nav
prompts from the head unit speaker with the sink off. There is no evidence of that: issue #818's
reporter runs exactly this configuration on a bike and says the AA audio "correctly stays on the
main phone and plays through the helmet", with no complaint about prompts.

**Correction 2: AU2's "always" is load-bearing, and gating it is not a free experiment.** Commit
`036913d8` deliberately *moved* AU2 out of the `enableAudioSink` block, with the comment "Always add
Audio2 (System Sounds) to keep connection alive". That records a change someone made because the
gated version misbehaved. Leave it advertised.

**Native mode changes none of this.** `wifiConnectionMode` only governs how the AAP session is
established; once `CommManager` has a socket the audio path is transport-agnostic. There is no
output-device selection anywhere in the app.

## 4. Why "pair the intercom to the head unit" is the wrong path here

`connection/wifi/modes/nativeaa/BluetoothWakePolicy.shouldPoke()` **suppresses the native-AA wake
poke whenever the head unit already holds a hands-free (HFP/HSP) link**, because a poke would take
the phone's single hands-free slot. An intercom paired to the head unit registers as
`HEADSET`/`HEADSET_CLIENT`, trips that check, and can stop native mode starting or recovering after
a dropout (tunnels, range). Combined with the one-connection-at-a-time radio, routing through the
head unit's own Bluetooth is fragile on this hardware. The user chose intercom-on-phone, so no
`BluetoothWakePolicy` change is needed (the head unit never holds a hands-free link).

## 5. The mic side: what moves Android Auto's recorder, and what does not

Requirement: Assistant and call voice input must be captured from the **intercom mic**, never the
head unit's built-in mic.

**Settled, on hardware and in the bytecode: withholding `ID_MIC` moves the recorder to the phone, and
it works only together with a motorcycle claim.** headunit-info round 2 measured the move
(`GH.Assistant.Recorder: Using phone microphone`, `GH.PhoneMicRecorder`, zero `GH.CarMicRecorder`, a
spoken navigation query transcribed and answered). The bytecode then explained why neither half works
alone. Read out of Gearhead `17.5.663204`:

- `Lwxq;->a()V` picks the recorder **once**, at projection start, before any `MicrophoneRequest`
  exists, and stores it in `GhMicrophoneContentProvider.d`:

  ```
  usePhoneMic = (CarInfo.r /* vehicleType */ == 3)
                && CarAudioManager != null
                && ICarAudio.o() /* int[] */ .length == 0
  ```

  It is a **conjunction and the vehicle type is evaluated first**: the fall-through sets the result
  false and skips the microphone query entirely, so on anything but a motorcycle withholding the
  service is invisible to this code. The comparison is a strict `== 3`, so a truck fails it too.

- **Withholding the service is legal only under a motorcycle.** `Ljon;->run()` aborts connection
  setup with `No audio/mic` when `Liwz;->c` is null, and motorcycle is the exemption. `Liwz;->c` and
  `Liwz;->h` are written in one straight-line block from the audio-source submessage of our own
  ServiceDiscoveryResponse, so both are null exactly when we omit the service, and the abort fires
  **before** projection start. **This corrects the reading that produced the change.** The earlier
  analysis read `Lqso;->c()V` in the newer audio stack, which genuinely never looks at the
  microphone, but that stack also throws `CarNotSupported` from `openMicrophone` unconditionally, and
  round 1 moved 327,680 bytes through a working `GH.CarMicRecorder`. So the older stack is live here
  and it does check.

- **`Not using phone mic` is only an exception handler**, and it logs under `GH.GHLifetimeManager`,
  not `GH.Assistant.Recorder`. The ordinary false outcome is two instructions with no logging, so
  filtering on the recorder tag and seeing nothing proves nothing.

- **A constraint found alongside it, written down nowhere else:** the system sink is accepted only if
  it offers a **16 kHz mono** config, which `AudioConfigs` happens to give `ID_AU2`. With the sink off
  that is the only sink left, so changing it would empty all three sink slots and genuinely would end
  the session. That is what `036913d8`'s "keep connection alive" comment was recording.

So no `MicrophoneResponse` status can hand the microphone back, and neither can silence: a head unit
that declines leaves the phone waiting on a car mic that never sends, until
`microphone timed out; no data received for %d`. That is measured, twice, on this project's own rig.

### The cost of claiming a motorcycle, now enumerated

It suppresses the projected dashboard at projection start (`Lxff;->p`, event 10214), drops
`GearSnacksService` from the car-app roster, skips the assistant education tooltip, swaps two icons,
and can install a synthetic touchpad on D-pad-only units. Nothing about resolution, driving
restrictions, app allowlisting, telephony, keyboard or notifications is keyed on it, and it is never
sent to a Google backend.

### The stored record, which is why this needs more than the two protocol changes

The phone records a vehicle type against **manufacturer, model, model year and hashed vehicle id**,
not the Bluetooth MAC as round 1 concluded. Only an INSERT writes the column; the reconnect UPDATE
omits it and `Ljoh;->run()` stamps the stored value back over whatever we declare. So a rider who has
ever connected with the microphone on keeps a `CAR` record, and turning the microphone off then fails
to connect outright. Rounds 1 and 2 both cleared that record before starting, which is why neither
saw it.

The branch answers this by announcing **one vehicle id per vehicle type**, so each type is inserted
once under its own entry and a car keeps the id the user set. headunit-info round 3 is the first round
forbidden from clearing the record, and that is what it measures.

**One limit with no in-app fix:** Android 10 and below never write the column at all
(`Ljlo;->x()` is `SDK_INT >= 30`, and the read hard-codes 0), so the declared type survives the first
session and is discarded on every later one. The phone's version is not visible to us. It is stated in
the settings copy and in the head unit's own log line instead.

`decoder/audio/MicRecorder.kt` does support `micInputSource == 4` (Bluetooth Headset SCO), but that
needs the intercom paired **to the head unit**, which is the topology §4 rules out.

## 6. On-bike config recipe

**Pairing:** intercom (Cardo/Sena/etc.) paired to the **phone**. Remove any intercom to head unit
pairing. Head unit connects to the phone only for native-AA projection.

**Open Headunit settings:**
1. Connection mode: Native AA wireless (`wifi-connection-mode = 3`), unchanged.
2. **Audio Sink: OFF** (`enable-audio-sink = false`). Static setting, not a per-ride toggle.
3. **Head unit microphone: OFF** (`use-head-unit-microphone = false`), once §7 ships.

**Phone side:** ensure media + navigation + call audio are all allowed to the intercom (some
intercoms split "phone" vs "GPS/A2DP" channels; use one that carries media, or dual-channel pair the
phone on both).

**What round 2 measured, on this exact configuration:** music plays in the helmet, the assistant
records from the intercom's SCO mic and its spoken reply is heard in the helmet, the head unit stays
silent, and music ducks and returns. A spoken navigation query was transcribed and answered on the
projected screen. The head unit never opens `AudioRecord`, so its physical microphone stays free.
This supersedes the earlier note that the assistant does not work with the setting off, which was
true only while the microphone service was still being announced.

**Two things a rider has to be told:**

- **It needs Android 11 or newer on the phone.** Older versions do not keep the vehicle type the head
  unit declares, and the configuration stops connecting after the first session. There is no way to
  detect that from the head unit, so it is stated in the settings copy instead.
- **Android Auto will list a second entry for this head unit.** Each vehicle type is recorded under
  its own id by design, which is what lets the microphone switch work without forgetting the car. The
  first connection under a new type asks for consent once.

**The disconnect and reconnect half is still open.** Round 2 could not complete it because the rig's
5 GHz-only Wi-Fi Direct setting blocked the phone's rejoin. Round 3 carries it.

## 7. The code, which exists

Branch **`fix/mic-and-vehicle-type`** (16 commits, rebased onto `main` 2026-08-28; supersedes
`fork/fix/mic-uplink` @ `4e86805c` and `fork/fix/headunit-info-and-vehicle-type` @ `6c6b3b54`, which
were 41 commits behind and did not compile against current `main`). Fourteen are hardware-validated;
the two added after round 2 are what round 3 measures.

The commits that answer this thread:

- **`Mic: a setting that leaves the microphone to the phone`** adds `Settings.useHeadUnitMicrophone`
  (default on). Off never wires the recorder, so `AudioRecord` is never constructed.
  `decoder/audio/MicrophonePolicy` holds the decision and names the reason, so a microphone the user
  handed to the phone reads differently in the log from a device with no usable capture.
- **`Mic: do not announce a microphone this head unit will not use`** withholds the microphone
  service when the setting is off. Round 2 measured that this is what moves the recorder to the
  phone, and §5 explains why it is legal only alongside the next one.
- **`Protocol: the vehicle type is the user's choice, and each type gets its own identity`** makes
  the vehicle type a Car / Truck / Motorcycle setting, overridden to motorcycle while the microphone
  is off because any other type aborts connection setup once the service is withheld. It also
  announces one vehicle id per type, so the phone inserts a record per type instead of stamping a
  stored one back over what we declare. That is the defect rounds 1 and 2 hid by clearing the record.
- **`Settings: pick the vehicle type, and correct what the microphone switch claims`** adds the
  picker to Vehicle Information and rewrites the two microphone strings, both of which told the user
  things round 2 disproved.

An earlier commit in the same stack corrects all eight existing `HeadUnitInfo` field numbers, which
were wrong, so every value had been arriving under the wrong name. Supporting work: a
`MicrophoneResponse` message, the uplink message type and microsecond clock, one sample rate reaching
both the announcement and the capture, whole 2048-frame messages, and the foreground-service
microphone type claimed only while capture is open.

**What is left is one hardware round, not a design choice.** Rounds 1 and 2 are reported in
`headunit-info-round1-results.md` and `headunit-info-round2-results.md`.
`headunit-info-round3-brief.md` runs the sequence a user actually performs, with the phone's stored
record left alone, which no round has done.

## 8. Related reports

Three reports, one mechanism, and they disagree only on where the intercom is paired.

- **#818** "Use phone/Bluetooth mic when Audio Sink is disabled". Phone-as-head-unit on a bike,
  intercom on the **phone**. Explicit: with the sink off, audio correctly stays on the phone, "however
  microphone input still comes from the headunit device". Needs both halves.
- **#818's comments**: two car users with an aftermarket Android interface whose microphone is worse
  than the phone's, wanting head unit speakers *and* phone mic. This is why the microphone switch is
  independent of `enableAudioSink` rather than folded into it.
- **#784** "Option to disable mic capture during handshake". CFMoto motorcycle dashboard, Android 11.
  Its stated mechanism is wrong: nothing in this app touches the microphone at handshake time, and
  `AudioRecord` is constructed only from `AapControl.micRequest()` on the phone's request. Its
  topology is unstated, and "cfmoto mtx1000" names a bike rather than any head unit product, so
  whether the intercom is on the phone or on the head unit is unknown. If it is on the head unit, the
  microphone switch alone is the whole fix for them and the motorcycle claim is irrelevant. That
  question is in the reply draft.

## Sources

- Device: `xdaforums.com/t/...4752522/`; `linkifun.com/products/linkifun-mt23-...`;
  `linkifun.com/blogs/news/motorcycle-display-single-bluetooth-vs-dual-bluetooth-...`;
  `aliexpress.com/s/wiki-ssr/article/ekiy-m23`; T610: `cpubenchmark.net/cpu.php?cpu=T610-Unisoc`.
- ZLink / BT contention: `blog.carplayhacks.com/carplay-zlink/`;
  `xdaforums.com/t/issues-with-bluetooth-calling-via-zlink-android-auto.4589173/`.
- Rider topologies: `chigee.com/blogs/articles/guide-to-using-bluetooth-and-audio-features-...`;
  `carpuride.com/blogs/guide/carpuride-connection-guide-common-issues-and-solutions`;
  `aoocci.com/blogs/motorcycle/bluetooth-carplay-routing-c9-pro-max`;
  `roadglide.org/threads/wireless-android-auto-with-headset-function-and-without-whim-it-works.392695/`;
  AAWireless disable-sink: `xdaforums.com/t/is-it-possble-to-disable-audio-sink-when-using-aa-with-a-rooted-phone.4646403/`.
- Local prior research: `code-researchs/NATIVE_AA_BLUETOOTH_HANDSHAKE_CONTEXT.md`,
  `code-researchs/zlink6-qianfeng-native-aa-findings.md`,
  `code-researchs/WIRELESS_TESTING_FINDINGS.md`, `headunit-modding/docs/SELF_MODE_CDM_INVESTIGATION.md`.
