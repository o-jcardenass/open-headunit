# headunit-info — round 3 results

**Candidate:** `fork/fix/mic-and-vehicle-type` @ `283659f4` (16 commits `main..HEAD`)   **Baseline:** none (round 2 measured `40390cf7`, two commits back)
**APK md5:** `1d146fe76fe667a9befe03811bbbe520` (`com.andrerinas.headunitrevived_3.3.0-beta3_debug.apk`), confirmed on-device before R1
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14) head unit, adb-root; phone **Motorola edge 30 neo `ZY22GC3BM4`**, Android Auto **17.5.663204-release**, assistant = Gemini. Intercom **"KY Pro"** (`D0:D9:4F:A0:88:AF`), paired to the phone (R5 only).
**Date:** 2026-08-28

---

## Headline

- **R0 PASS** — `assembleGithubDebug` clean (first compile of both commits), **906 tests / 0 failures / 0 errors** (90 classes), exactly the brief's prediction. `VehicleTypePolicyTest` 3→**5**, `VehicleIdentityPolicyTest` **new, 4**.
- **R1 PASS** — the ordinary car-with-mic config. Session up 45–47 fps, `CarInfoInternal` `dbId=1 / VEHICLE_TYPE_CAR`, phone built `GH.CarMicRecorder`, head unit recorded (`capture summary bytes=294912 (97%) peak=2272`).
- **R2 INCONCLUSIVE on the first attempt, PASS on the retry (R2b).** First attempt: the phone's Android Auto **never opened the Native AA Bluetooth handshake channel back to the head unit** (`Connection accepted from` = 0, 5/9 pokes failed with `read failed … read ret: -1`, one transient `No paired Bluetooth devices found to poke`), so nothing under test ran. This is the documented poke/OEM-radio flakiness on this rig (§7a), not the candidate. **R2b, after a clean phone-Bluetooth cycle: PASS and it is the answer.** A **new** record was inserted — `dbId=2, vehicleId=796b48db361c202d, vehicleType=VEHICLE_TYPE_MOTORCYCLE` — the session formed with **no `No audio/mic`**, the microphone service was withheld, and the recorder moved to the phone (`GH.Assistant.Recorder: Using phone microphone`, `GH.PhoneMicRecorder` ×2, **zero** `GH.CarMicRecorder`, **zero** `Mic request:` at the head unit). The phone's companion app now lists **two** entries for this head unit (dbId 1 = car, dbId 2 = motorcycle) — the designed outcome.
- **R3 FAIL, by the brief's own criterion.** With the mic turned back on, the session resolved to **`dbId=2` (MOTORCYCLE)** — R2's record — not R1's `dbId=1` CAR. The brief: *"If R3 lands on R2's record instead of R1's, the identity split is not doing its job and that is a FAIL."* Mitigating: the projected UI was the full car rail, Maps drew a car puck, and the head-unit microphone worked normally (`GH.CarMicRecorder` ×4, `capture summary bytes=286720 peak=3616`).
- **R4** — a **third** record was created (`dbId=3, vehicleId=544224da75ec15f3, vehicleType=VEHICLE_TYPE_TRUCK`), so all three types now have their own entry. But reconnect routing stayed broken: R4a (announcing TRUCK) resolved to `dbId=1 CAR`; R4b (announcing CAR) resolved to `dbId=3 TRUCK`. The microphone worked in both. "Return to Car" did **not** land on R1's record.
- **R5 PASS** — the riding configuration on the fixed build. Music in the helmet (A2DP → intercom, `mIsPlaying:true`), assistant on the intercom's SCO mic (`startVoiceRecognition device=D0:D9:4F:A0:88:AF` → `Using phone microphone` → transcription + `StartNavigationExecutor`), head unit silent (VIDEO + AUDIO2 only), music ducked (`onAudioFocusChange(-2)`) and returned (`onAudioFocusChange(1)`). **Disconnect → reconnect — the sub-question round 2 left open — now answered YES:** after `headunit://disconnect` and a Bluetooth nudge the session re-formed (2nd incoming, 2nd SSL handshake), taking ~3.4 min through the `wifi-direct-band=0` fallback ladder.

**Shipping read.** The two answer-commits do what they claim on the *insert* side: each vehicle type gets its own phone record, and a user who was already paired as a car can now turn the microphone off and get a working motorcycle session (R2b) without forgetting the car. **But the identity split does not survive a reconnect on this phone.** Android Auto 17.5 looks its stored record up by the head unit's Bluetooth MAC and returns the newest one, ignoring the announced `vehicleId` entirely (the announced id also does not hash stably — "VEHID4C" produced three different `vehicleId` hashes across R1/round-2/this round). So once the `-moto` entry exists, later car-mode and truck-mode connects land on whatever record is newest, and the motorcycle UI (stripped rail) is only delivered on the one session that happens to resolve to the motorcycle record. The feature is safe to ship for the **on-bike use case as a one-way switch** (set mic off, leave it off — R2b + R5 both work and reconnect), but the round-trip the brief tested (toggle off, then back on, and land on your car again) does not work here.

---

## Setup notes

### Scripts

- `hur-wifi-test-scripts/` inventoried at round start. Used: `build_hur.sh` (R0 build — APK copied to `round-headunit-info-r3/cand-283659f4.apk` immediately, per §7a); `run_unit_tests.sh` (R0 — Gradle prints no totals without `--info`, so counts parsed from `app/build/test-results/testGithubDebugUnitTest/*.xml`: 90 classes, tests=906, failures=0, errors=0, skipped=0); `set_hu_prefs.sh` (every settings write, one relaunch-free pass). APK installed by name with `adb install -r`, md5 verified on-device.
- **New script:** `round-headunit-info-r3/r3_run.sh` — descendant of round 2's `session_up.sh`. Brings up one Native AA session with dual capture (`stdbuf -oL`), polls up to N s for the SSL handshake while the operator nudges the phone's Bluetooth, dumps `CarInfoInternal` before and after an optional assistant trigger (route 1, the app's own `RemoteControlReceiver`), and screenshots. Does **not** `pm clear` or forget anything (brief §3). Left in the round folder.

### `settings.xml` at round start

Fresh backup `round-headunit-info-r3/settings-backup-preround.xml`, md5 `743e75bb81cfdce443ace80ec8d61c9c` — **same keys and values** as round 2's restored state (only SharedPreferences line-ordering differs). Round-start deltas vs. the brief's target: `use-head-unit-microphone` false→(R1)true, `enable-audio-sink` false→(R1)true, `wifi-direct-band` **1→0**, `log-level` 2→1, `vehicle-make`/`head-unit-make` `Google`→`MAKE1`/`HUMAKE5`, and `vehicle-model`/`vehicle-year`/`vehicle-id`/`head-unit-model` absent→`MODEL2`/`YEAR3`/`VEHID4C`/`HUMODEL6`. `vehicle-type` kept absent for R1–R3, set to `2` for R4, `1` for R4b/R5. `wifi-connection-mode=3`, `native-ap-transport=0`, `key-codes` absent — verified unchanged. **Restored byte-identical at round end** (md5 `743e75bb…`, `diff` clean).

### `wifi-direct-band` — what `0` bought and cost

The brief mandated `0` because round 2's band=1 (5 GHz-only) blocked the phone's rejoin. `0` worked: **every** run eventually connected. The cost is time — the app tries 5 GHz twice (`5GHz createGroup SUCCESS!` → `P2P-GROUP-REMOVED … reason=REQUESTED` after ~60 s each) before falling back to `Standard createGroup SUCCESS!`, which the phone joins. So connects took 60–280 s and 1–4 `createGroup SUCCESS` lines each. **This is the app's own retry ladder, not contamination** — every run has exactly **1** `WirelessServer: Incoming connection detected` and **1** real SSL handshake (`AapSslContext.performHandshake`; R5 has 2, the deliberate disconnect/reconnect). `Magic Garbage` 0 everywhere.

### Operator actions (minimum, all logged)

1. **The car was never forgotten.** No `pm clear` of Gearhead, no "forget car" in Android Auto, no fresh `vehicle-id`, at any point in R1–R5. The phone→head-unit Bluetooth bond was checked present on both sides before R1 (HU side `A0:46:5A:97:E4:95`, phone side keys the HU as `Navegadortz2 / 11:46:03:10:33:59` — asymmetric masked addresses, normal here).
2. **Nudged the phone's Bluetooth on each connect** (off ~10 s, on). Native AA on this rig needs this after a force-stop; §7a.
3. **Spoke the assistant queries** (R1, R2b, R3, R4, R5). No scriptable TTS-into-mic on this rig, so the *recognition* half rests on the operator plus the phone's `GH.TranscriptionCtrl` / `StartNavigationExecutor` lines; the *recorder-choice* half (`GH.CarMicRecorder` vs `GH.PhoneMicRecorder`) is fully scriptable and is the load-bearing evidence.
4. **Connected "KY Pro" to the phone for R5** — A2DP `Connected` (`mActiveDevice D0:D9:4F:A0:88:AF`, SBC), HFP `Connected`, AVRCP `Connected`. Started Spotify and confirmed music audible in the helmet.
5. **R2b consent** — *(operator to confirm: whether the phone showed a "new car"/consent dialog on the R2b or R4 connect and whether it was tapped. `com.google.android.gms.onboardingconsent.api.ConsentManagerApiService` bound around each new-record connect; the new `dbId=2` / `dbId=3` records came out `known=true, projectionAllowed=true`.)*

### Deviations

1. **R2 first attempt INCONCLUSIVE**, re-run as **R2b** after a clean phone-Bluetooth cycle. R2's failure was entirely at the Bluetooth poke layer (`Connection accepted from` = 0), documented rig flakiness.
2. **The `dumpsys activity service gearhead` one-liner in `r3_run.sh` is not reliable** for "which record did this session use" — it returned a different `dbId` than the session actually negotiated in R2b (script said dbId=1, capture said dbId=2) and R3. The load-bearing line is **`GH.WIRELESS.SETUP: headUnitCarInfoInternal:`** in the phone capture, which resolves the session's announced identity to a stored record. All verdicts below use that line, not the script's dump.
3. **`Not showing dashboard during projection start on motorcycles` — absent from every capture** (R2, R2b, R3, R4, R4b, R5). Either not this Gearhead's string (drift, §7a) or the path isn't hit. The dashboard question is answered visually instead (below).
4. R3 assistant fired twice ~1 s apart (a shell-variable slip); §7a says a quick second trigger can cancel the first. It did not affect R3's recorder-choice evidence (`GH.CarMicRecorder` ×4, capture summary present).
5. R4b run with `noassist` (no assistant trigger) — it only needed to show which record "return to Car" lands on.

### Rig / environment

- adb shell on the head unit is **root**. Settings pre-written with the app force-stopped; the `shared_prefs` root-owned quirk did not bite.
- The head unit's own bundled `com.google.android.projection.gearhead:car` memory-cycles ~every 60 s throughout. Harmless noise.
- Captures (gzipped) + screenshots + `settings-backup-preround.xml` + `settings-after-restore.xml` in `hur-wifi-test-scripts/round-headunit-info-r3/`. Not committed here (channel convention). Candidate APK left installed; settings restored.

---

## R0 — gate — PASS

- `git checkout -B hui-r3 fork/fix/mic-and-vehicle-type` → HEAD `283659f4`, `git rev-list --count main..HEAD` = **16**. Tip = *"Settings: pick the vehicle type, and correct what the microphone switch claims"*.
- `assembleGithubDebug` **clean** — first compile of `1a320aae` + `283659f4`.
- `testGithubDebugUnitTest`: **tests=906, failures=0, errors=0, skipped=0** (90 classes). Delta vs round 2 (900) = **+6**, exact: `VehicleTypePolicyTest` 3→**5**, `VehicleIdentityPolicyTest` **new with 4**.
- md5 `1d146fe76fe667a9befe03811bbbe520`, confirmed installed before R1.

## R1 — establish the car record — PASS

Settings: `use-head-unit-microphone=true`, `enable-audio-sink=true`, `vehicle-type` absent, identity `MAKE1/MODEL2/YEAR3/VEHID4C/HUMAKE5/HUMODEL6`, `wifi-direct-band=0`, `log-level=1`. **Car not forgotten.** Connect 21:46:29 (after the band-0 ladder: `5GHz createGroup SUCCESS` ×2 torn down `reason=REQUESTED`, then `Standard createGroup SUCCESS` = `p2p-wlan0-6`, which the phone joined; 1 incoming, 1 SSL handshake).

- **Session forms, video renders:** `SSL handshake complete` 21:46:29.359; `Throughput … rendered=226 (45fps), fed=226, dropped=0, decodeLatency=10ms, codec=c2.unisoc.hevc.decoder`, sustained 45–47 fps.
- **`CarInfoInternal`, quoted (session read, phone):**
  > `CarInfoInternal[dbId=1,manufacturer=MAKE1,model=MODEL2,headUnitProtocolVersion=1.2,modelYear=YEAR3,vehicleId=215ba32b8c3c14cf,vehicleType=VEHICLE_TYPE_CAR,…,headUnitMake=HUMAKE5,headUnitModel=HUMODEL6,headUnitSoftwareBuild=1,headUnitSoftwareVersion=0.1.0,bluetoothaddress=11:46:03:10:33:59,known=true,projectionAllowed=true,…]`
  - **`dbId=1`, `vehicleId` hash `215ba32b8c3c14cf`, `vehicleType=VEHICLE_TYPE_CAR`.** (Round 2 left `dbId=1` as MOTORCYCLE under this identity — R1 connecting as a car flipped that record's type to CAR; the `dbId` stayed 1.)
- **Assistant trigger → the phone builds the head-unit recorder:** `GH.CarMicRecorder` ×4, `GH.PhoneMicRecorder` 0, `GH.Assistant.Recorder: Using phone microphone` 0. Head unit: `Voice Session Notification: START` 21:47:02.770 → `Mic request: open: true` 21:47:03.357 → `MicRecorder: Initializing AudioRecord … SampleRate: 16000` → `AapTransport: mic uplink started (channel MIC …)` → `MicRecorder: capture summary | elapsed=9448ms bytes=294912 (97% of expected) emptyReads=0 peak=2272/32767` → `Voice Session Notification: STOP` 21:47:14.
- `Skipping the microphone service` 0 (mic **is** announced). Channels: VIDEO, AUDIO2, AUDIO1, AUDIO.
- Discard check: createGroup 3 (band-0 ladder, all pre-join), incoming **1**, real handshakes **1**, `Magic Garbage` 0, `ByeBye` 0. Clean.
- **Screenshot `r1.png`:** full Coolwalk app rail (Maps / Spotify / Phone / Settings / mic / launcher).

## R2 — turn the microphone off without touching the phone

### First attempt — INCONCLUSIVE

Settings: `use-head-unit-microphone=false`, everything else unchanged. Car not forgotten. 270 s, **no session**.

- `WirelessServer: Incoming connection detected` **0**, `SSL handshake complete` **0**, `NativeAA: Connection accepted from` **0**.
- The head unit's side ran: `Providing credentials` (SSID `DIRECT-JK-Navegadortz2` etc.), 4 pokes succeeded (`Successfully poked … via HSP-AG/HFP-AG`), then 5 failed (`Poke via HFP-AG … failed: read failed, socket might closed or timeout, read ret: -1`). One transient `NativeAA: Dropping Auto Start BT MAC(s) no longer paired: [A0:46:5A:97:E4:95]` / `No paired Bluetooth devices found to poke` at 21:53:04, self-recovered (HU-side bond intact on `dumpsys` throughout and after). Two manual pokes at 21:54–21:55 succeeded but still no connect-back.
- **The phone's Android Auto never connected back over Bluetooth RFCOMM**, so the `No audio/mic` / vehicle-type logic the round tests was never reached. This is the OEM-radio (`FX Plus`, `D0:D9:4F:C2:C7:1E` in the phone's bond list) / poke flakiness §7a documents at length — R1 itself needed 3 poke cycles and ~2 min and logged the *"phone is most likely bound to a different Bluetooth device"* warning 3× before recovering. Not a candidate fault; re-run.

### R2b (retry, clean phone-Bluetooth cycle) — PASS — the point of the round

Same settings. Connect 21:58:53 (single `createGroup SUCCESS` = `p2p-wlan0-11`, 1 incoming, 1 SSL handshake, `ByeBye` 0).

- **The session forms. `No audio/mic` = 0.** Video 42–45 fps, `dropped=0`.
- **`Head unit microphone is off in Settings. Skipping the microphone service …`** present (21:59:03.088), the new string with the Android-10 caveat. The announcement carries **no MIC channel** (`Media Sink Setup Request` on VIDEO, AUDIO2, AUDIO1, AUDIO only).
- **New record, quoted (both `GH.WIRELESS.SETUP: getCarInfoInternal for BT device` and `headUnitCarInfoInternal`):**
  > `CarInfoInternal[dbId=2,manufacturer=MAKE1,model=MODEL2,headUnitProtocolVersion=1.2,modelYear=YEAR3,vehicleId=796b48db361c202d,vehicleType=VEHICLE_TYPE_MOTORCYCLE,…,bluetoothaddress=11:46:03:10:33:59,known=true,projectionAllowed=true,…]`
  - **Different `dbId` (2), different `vehicleId` hash (`796b48db361c202d`), `vehicleType=VEHICLE_TYPE_MOTORCYCLE`** — exactly as the brief predicts. Both records now persist in `dumpsys` (`dbId=1` CAR + `dbId=2` MOTORCYCLE). **Two entries in the companion app — the designed outcome.**
- **Recorder moved to the phone:** `GH.Assistant.Recorder: Using phone microphone` 21:59:03.137 (projection start); on the trigger `GH.PhoneMicRecorder` ×2, **`GH.CarMicRecorder` 0**. Head unit: `Mic request:` 0, `Initializing AudioRecord` 0, `mic uplink started` 0, `capture summary` 0. Assistant answered (`GH.TranscriptionCtrl: shouldDisplayTranscription() true`, multiple).
- `GH.PhoneMicRecorder … IOException: EPIPE` on teardown — known Gearhead-side, non-blocking (brief §4).
- **Screenshot `r2b.png`:** the app rail is **gone** — only mic / compass / warning icons on the left edge, no Maps/Spotify/Phone/Settings, no launcher. The motorcycle record visibly changes the projected chrome vs. R1.

## R3 — turn it back on — FAIL (brief's criterion)

Settings: `use-head-unit-microphone=true`, everything else unchanged. Car not forgotten. Connect 22:03:20 (single group `p2p-wlan0-12`, 1 incoming, 1 handshake, `ByeBye` 0).

- **Session resolved to R2's record, not R1's** — `GH.WIRELESS.SETUP: getCarInfoInternal for BT device` and `headUnitCarInfoInternal` both:
  > `CarInfoInternal[dbId=2,…,vehicleId=796b48db361c202d,vehicleType=VEHICLE_TYPE_MOTORCYCLE,…]`
  - The lookup is `for BT device` (`11:46:03:10:33:59`), issued during the RFCOMM stage **before** the head unit announces service discovery, and it returns `dbId=2`. Brief: this is a **FAIL** ("the identity split is not doing its job").
- **What still works:** `Skipping the microphone service` 0 (mic announced); `GH.CarMicRecorder` ×4, `GH.PhoneMicRecorder` 0, `Using phone microphone` 0; head unit `Mic request:` ×2, `capture summary | bytes=286720 (97%) peak=3616`. Video 45–46 fps `dropped=0`. **Screenshot `r3.png`:** full app rail, Maps draws a car puck. So the stale "motorcycle" stored record does not break the head-unit mic or the car UI when the mic service is announced — it is the stored record and companion-app label that are wrong.

## R4 — the picker

### R4a — Truck (`vehicle-type=2`, mic on)

Connect 22:09:57 (band-0 ladder, 2 createGroup, 1 incoming, 1 handshake).

- **Resolved to `dbId=1 CAR`** (`headUnitCarInfoInternal: … dbId=1, VEHICLE_TYPE_CAR`). No truck record visible *in this capture*.
- **Microphone works under the truck announcement:** `GH.CarMicRecorder` ×8, `GH.PhoneMicRecorder` 0, head unit `Mic request:` ×4, `capture summary` ×2 (`bytes=245760 peak=1365`, `bytes=286720 peak=3113`). Video settled 45 fps `dropped=0`.
- **Screenshot `r4.png`:** full app rail (truck ≈ car UI).

### R4b — back to Car (`vehicle-type=1`, mic on)

Connect 22:22:57 (band-0 ladder, 4 createGroup, 1 incoming, 1 handshake).

- **Resolved to `dbId=3 TRUCK`** — `getCarInfoInternal for BT device` and `headUnitCarInfoInternal` both:
  > `CarInfoInternal[dbId=3,…,vehicleId=544224da75ec15f3,vehicleType=VEHICLE_TYPE_TRUCK,…]`
  - So the `-truck` INSERT from R4a **did** land (it just wasn't visible until it had committed) — `dumpsys` now shows all three: `dbId=1` CAR / `dbId=2` MOTORCYCLE / `dbId=3` TRUCK, one per type. But R4b, announcing **CAR**, resolved to the newest (`dbId=3` TRUCK). **"Return to Car" did not land on R1's record.**
- Video 45 fps `dropped=0`. **Screenshot `r4b.png`:** full app rail.

### The picker mechanism, as measured

`VehicleIdentityPolicy` gives each type its own announced id (`VEHID4C` / `VEHID4C-truck` / `VEHID4C-moto`), and the phone **does** insert a distinct record for each (dbId 1/2/3). But Android Auto 17.5's wireless-reconnect lookup is **keyed on the head unit's Bluetooth MAC**, returns the **newest** matching record, and pays no attention to the announced `vehicleId`. Corroborating: the announced `vehicleId` does not even hash stably — plain `VEHID4C` produced `a6e50b83…` (round 1), `6cd8d9af…` (round 2), `215ba32b…` (this round). A per-`vehicleId` routing scheme cannot work against a phone that re-hashes the id each session and matches reconnects by BT device.

## R5 — the riding configuration again, on the fixed build — PASS

Settings: `use-head-unit-microphone=false` **and** `enable-audio-sink=false`, `wifi-direct-band=0`. Intercom "KY Pro" connected to the phone (A2DP + HFP + AVRCP `Connected`). Connect 22:32:25 (single group `p2p-wlan0-19`, 1 incoming, 1 handshake). Session resolved to `dbId=3 TRUCK` (newest record).

- **`Audio sink is off in Settings. Skipping the media and speech audio channels …`** present; **`Skipping the microphone service …`** present. Channels: **VIDEO + AUDIO2 only** — the sink-off shape.
- **Music in the helmet:** `A2dpStateMachine … mIsPlaying: true`, `mActiveDevice: D0:D9:4F:A0:88:AF`, Spotify `PlaybackState … PLAYING(3)`. (Operator confirmed audible.)
- **Assistant on the intercom's SCO mic:** `HeadsetService: startVoiceRecognition: device=D0:D9:4F:A0:88:AF` → `HeadsetStateMachine: VOICE_RECOGNITION_START: A2DP is playing, return and establish SCO after A2DP suspended` → `Sco connected for CS call` → `GH.Assistant.Recorder: Using phone microphone` → `GH.TranscriptionCtrl: shouldDisplayTranscription() true` → `StartNavigationExecutor: … about to launch intent`.
- **Head unit silent:** no media/speech channel ever set up (VIDEO + AUDIO2 only).
- **Music ducks and returns:** `dispatching onAudioFocusChange(-2)` 22:41:11.018 & 22:41:12.515 (duck), then `abandonAudioFocus()` 22:41:26–27 → `dispatching onAudioFocusChange(1)` 22:41:26.718 & 22:41:27.715 (regain).
- **Disconnect → reconnect — YES.** `headunit://disconnect` 22:41:54 → `AapProjectionActivity: Finishing because state isUserExit=true` → `Byebye Response received` → `AapService: Native AA user exit. Stopping active launcher.` → `P2P-GROUP-REMOVED p2p-wlan0-19 GO reason=REQUESTED`. Operator cycled the phone's Bluetooth; the session **re-formed** — `WirelessServer: Incoming connection detected from /192.168.49.151` 22:45:19.111, `SSL handshake complete` 22:45:19.253, sink+mic still withheld (VIDEO + AUDIO2), video ramping 27→37→45 fps `dropped=0`. Elapsed ~3.4 min through the band-0 fallback ladder (`createGroup` 1→4). One session, one `ByeBye` (the deliberate one), `Magic Garbage` 0.
- **Screenshot `r5.png`:** full app rail (session resolved to the TRUCK record, so the motorcycle chrome-stripping seen in R2b is **not** applied here — the rail state now depends on which stored record the phone picks, not on what the head unit announces).

---

## Report-back (brief §7)

1. **R1 / R2b / R3 `CarInfoInternal`, quoted:**
   - R1: `dbId=1, vehicleId=215ba32b8c3c14cf, vehicleType=VEHICLE_TYPE_CAR`
   - R2b: `dbId=2, vehicleId=796b48db361c202d, vehicleType=VEHICLE_TYPE_MOTORCYCLE`  *(new record — different dbId and hash, as predicted)*
   - R3: `dbId=2, vehicleId=796b48db361c202d, vehicleType=VEHICLE_TYPE_MOTORCYCLE`  *(landed on R2's record, not R1's → FAIL)*
   The three `dbId`s the round exists to measure: **1 (R1), 2 (R2b), 2 (R3 — should have been 1).**
2. **Did R2b form a session without the car being forgotten?** **Yes.** The car was never forgotten in R1–R5; R2b formed a full Native AA session and inserted a second record. (R2 *first attempt* failed at the Bluetooth poke layer, not the record logic; R2b is the run.)
3. **Consent / entry count:** `com.google.android.gms.onboardingconsent…ConsentManagerApiService` bound around each new-record connect. New records came out `known=true, projectionAllowed=true`. The companion app lists **3** entries at round end (dbId 1 CAR / 2 MOTORCYCLE / 3 TRUCK). *(Operator to confirm whether a consent dialog was shown and tapped.)*
4. **Did R3 return to R1's record?** **No.** R3 resolved to `dbId=2` (R2's motorcycle record). R4b, also announcing CAR, resolved to `dbId=3` (truck). The identity split creates the records but does not route reconnects.
5. **R4:** a third record `dbId=3, vehicleId=544224da75ec15f3, vehicleType=VEHICLE_TYPE_TRUCK` was created. The microphone worked under the truck announcement (`GH.CarMicRecorder` ×8, capture summaries present). Setting `vehicle-type=1` and reconnecting (R4b) landed on `dbId=3 TRUCK`, **not** R1's record.
6. **Dashboard:** `Not showing dashboard during projection start on motorcycles` — **absent in R2 and everywhere else** (string drift or path not hit). Visually: the app rail is **stripped in R2b** (the one session on the motorcycle record) and **present in R1, R3, R4a, R4b, R5**. So the dashboard/rail follows the *resolved stored record's* vehicle type, and since reconnects don't route to the motorcycle record, the stripped-rail motorcycle UI is delivered inconsistently.
7. **R5 disconnect/reconnect:** **succeeds** — session re-formed after `headunit://disconnect` + a Bluetooth nudge, ~3.4 min via the fallback ladder. **`wifi-direct-band` ran at `0`** the whole round (the brief's value; never changed).
8. **R0 count:** **906 / 0**, exactly the expectation. `VehicleTypePolicyTest` 3→5, `VehicleIdentityPolicyTest` new (4).

## Anything the brief did not ask about

- **The `vehicleId` the head unit announces does not hash to a stable value on this phone.** `VEHID4C` → `a6e50b8340b97c53` (round 1) / `6cd8d9af6961e561` (round 2) / `215ba32b8c3c14cf` (round 3). Whatever the phone folds in (masked BT address, a per-session nonce), any scheme that expects the phone to match a reconnect against a previously-announced `vehicleId` is built on sand here. This is the core reason the identity split doesn't route.
- **`getCarInfoInternal for BT device` fires during the RFCOMM stage, before service discovery**, and its result is what `headUnitCarInfoInternal` ends up being. The head unit's live announcement does not override it for the stored-record purpose — only for the projected layout (which follows the announcement: R3 announced CAR and got the car rail despite resolving to the motorcycle record).
- **`dumpsys activity service com.google.android.projection.gearhead | grep CarInfoInternal` is not a reliable "current session record" probe** — it lagged/disagreed with the capture in R2b and R3. Future briefs should ask for `GH.WIRELESS.SETUP: headUnitCarInfoInternal` from the phone capture instead.
- **The `-moto` INSERT (R2b) needed a full fresh wireless setup to happen; the quick reconnects (R3) reused an existing record.** R4a's `-truck` INSERT only became visible by R4b. Record creation on this phone appears to require the slower `CONNECTING_RFCOMM` → WPP path, not a warm reconnect.
- Video was healthy every run (45–47 fps, `dropped=0`, `c2.unisoc.hevc.decoder`, `decodeLatency` 9–11 ms) once settled; early windows dipped to 10–27 fps during nav-view render load, as in round 2.
