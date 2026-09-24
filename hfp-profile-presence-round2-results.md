# hfp-profile-presence — round 2 results

**Candidate:** `fork/fix/hfp-profile-presence` @ `ee49a623` (4 commits on `e6b19c3a`)
**Baseline:** none built — R1 re-uses round 1's explicitly-written-`true` result as the comparison,
R2/R3 are candidate-only controls.
**APK md5:** `e78b1a39b2cb23ff874f3e364001f0cb` (candidate, `3.3.0-beta4` githubDebug)
**Units:**
- D-MOTO — motorola edge 30 neo, Android 14, BT `A0:46:5A:97:E4:95` — head unit under test (R1, R2)
- D-POCO — POCO X3 NFC, **Android 15** (brief says 12), Gearhead `17.5.663204`, BT `DC:B7:2E:5E:4E:59` — projecting phone
- D-HU — UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, BT `11:46:03:10:33:59`, Gearhead on the box `17.3.662864` (irrelevant, it is the head unit) — regression arm (R3)
**Date:** 2026-09-01

## The three numbers that matter

1. **R1's four conditions, at the default with nothing written — all met, 3/3 runs.**
   Per run: SLC established = 1, D-POCO `HeadsetStateMachine` for D-MOTO reaches `Connected`,
   `NO_HFP_FROM_HU_PRESENCE` = 0, `Incoming connection detected` = 1 into a full SSL session.
   The default reaches the gate. **Not falsified.**
2. **R2's SLC count = 0**, `HFP responder active` = 0, and the failure came back
   (`NO_HFP_FROM_HU_PRESENCE` = 12, no session). The opt-out still beats the new default.
3. **R3 on D-HU: `already advertises Hands-Free` = 1, `hands-free service level connection
   established` = 0.** The new path is inert on a real head unit even with the setting on by
   default; one clean session, video 46–51 fps `dropped=0`.

Plus R4's greps over the surviving round 1 captures: `audio sink decoy accepted` = **0** (decoy
removal is provably free); `could not read radio` = 0 but **not decisive** (see Setup notes) — R1's
own live capture answers that question instead: D-MOTO's reflective read succeeds.

---

## Setup notes

**Scripts used**
- `build_hur.sh` — candidate build (R0). `run_unit_tests.sh` — R0 test gate.
- `set_autostart_btmac.sh` — `auto-start-bt-macs` = D-POCO on D-MOTO.
- `set_prefs_runas.sh` — the scalar keys + `DEL native-aa-complete-hfp-slc`.
- **New script added:** `hfp_slc_default_run.sh` — one Native AA run against the HU-under-test with
  the round's harvest. Based on round 1's `hfp_poke_probe.sh`; adds the round-2 log-line greps
  (`gets the stand-in HFP record`, `already advertises Hands-Free`, `a real hands-free link is up`),
  a `BT_CYCLE` env (default **0** = leave the phone's radios alone, round 1's recipe) and the
  phone-side `HeadsetStateMachine` transition dump. Left in `hur-wifi-test-scripts/`.

**Deviations and corrections**

- **R0's decoy grep in the brief is over-broad.** `unzip -p <apk> classes*.dex | strings | grep -c
  "AudioSinkDecoy\|Audio Sink"` returns **2**, not 0 — both hits are the unrelated, long-standing
  `enable-audio-sink` feature (`"Audio Sink disabled - skipping … audio focus request"`). The decoy
  (commit `c5609e16`) is genuinely gone: every decoy-specific symbol is absent from the DEX —
  `launchAudioSinkDecoy` 0, `AUDIO_SINK_UUID` 0, `0000110b-0000-1000-8000-00805f9b34fb` 0,
  `audio sink decoy accepted` 0, `NativeAa-AudioSinkDecoy` 0, `already advertises an audio sink` 0 —
  and `git grep` finds none of them in the `ee49a623` source.

- **R1's first attempt was discarded — my harness error, not a candidate fault.** The first
  `hfp_slc_default_run.sh` build cut D-POCO's Bluetooth before launch (`BT_CYCLE=1`). Brief §2 says
  to leave D-POCO's radios up; cutting them makes the phone miss the first P2P group, and D-MOTO's
  group then self-churns every ~60 s (`recoverNativeGroup`): 4× `createGroup SUCCESS`, 3 different
  SSIDs, the phone never caught a stable one, no session. The mechanism under test *did* fire in
  that run (SLC established ×6, phone `WIRELESS_SETUP_SHARED_HFP_CONNECTING` ×7) but the discard-rule
  hit (2nd `createGroup SUCCESS`) voids it. All three reported R1 runs use `BT_CYCLE=0`, matching
  round 1's `hfp_poke_probe.sh` exactly, and each has one `createGroup SUCCESS`.

- **`could not read radio` (R4 grep 1) is not decisive.** The string was added by `74a70d12` *this
  round*; at round 1's tip `c5609e16` the register branch logged nothing at all (the brief itself
  says so). So the grep over round 1 captures is always 0 regardless of what the branch did then.
  R1's own fresh capture answers it directly: all three D-MOTO runs printed
  `radio [motorola edge 30 neo] gets the stand-in HFP record, because it advertises no Hands-Free` —
  i.e. **D-MOTO's reflective UUID read succeeds**; the "records could not be read" branch is not
  exercised on this rig.

- **No video in any R1 run — a D-MOTO-as-head-unit artifact, unrelated to this change.** On D-MOTO
  `AapProjectionActivity` goes `onResume` → `onPause` within ~20–40 ms of the session forming (a
  phone in portrait cannot hold the projection activity foregrounded; the capture also logs a
  surface orientation mismatch, expected `2237x1080`, actual `1080x2400`). Gearhead then holds
  video focus and only the audio channels run. Confirmed independent of screen state — R1c forced
  the screen awake with `svc power stayon true` and the pattern was identical. The AAP session
  itself is healthy in every R1 run: SSL completes, media sinks set up, Spotify audio streams
  continuously for the full ~3 min capture with `state=PLAYING` status packets. `CAR.SERVICE:
  InitialVideoFocus: Disabled by setting.` appears on the phone in both the no-video D-MOTO runs
  **and** the working-video D-HU run (R3), so that Gearhead line is a red herring; the discriminator
  is whether `AapProjectionActivity` stays resumed, which it does on the real head unit (R3) and
  does not on the phone-as-head-unit (R1). **R1's video coverage therefore rests on R3** — same
  candidate, same phone, 33 throughput windows all `dropped=0` at 46–51 fps.

- **Settings screen not opened** (house rules — UI not driven). Row text verified from
  `values/strings.xml` @ `ee49a623` instead:
  - `native_aa_complete_hfp_slc` → **"Finish the Bluetooth hands-free connection"**, description
    *"Some phones will not start wireless Android Auto until Bluetooth is fully connected, not just
    paired. On by default. Turn it off if you take calls on this device, because they will stay on
    the phone while it is on."* — reads sensibly.
  - `native_wifi_version_exchange` renamed to **"Factory-style handshake"** in `values/` (the
    non-English locales still carry the old "Modern handshake (version exchange)" and will fall
    back until translated).

- **`settings.xml` restored byte-identical on D-MOTO and D-HU** (both `diff` clean vs the round's
  opening backup). `svc power stayon` returned to `false` on D-MOTO. Candidate APK left installed on
  D-MOTO and D-HU. D-POCO left on its own build, radios up. Bonds intact throughout
  (D-POCO ↔ D-MOTO and D-POCO ↔ D-HU; D-POCO's addresses are masked, matched by device name).

- **Rig quirk hit:** the monitoring wrapper around the R3 run was killed twice by the harness while
  the run script itself kept executing unaffected; R3's capture is complete and decisive. No effect
  on results.

---

## R0 — build gate

**PASS**

- Build: `assembleGithubDebug` clean, APK `com.andrerinas.headunitrevived_3.3.0-beta4_debug.apk`,
  md5 `e78b1a39b2cb23ff874f3e364001f0cb`.
- Unit tests: **1071 / 0** (measured, `testGithubDebugUnitTest`, parsed from the JUnit XML). Exact
  match to the brief's expected 1071 / 0.
- `HfpServiceRecordPolicyTest` = **11** (brief expected 11). `HfpSlcInitiatorTest` 15,
  `BluetoothWakePolicyTest` 16, `Eui64BssidPolicyTest` 15.
- Decoy gone from the built APK — see Setup notes (all decoy-specific DEX symbols = 0; the brief's
  own grep pattern gives a false 2 on the unrelated `enable-audio-sink` strings).

---

## R1 — the default, with nothing written  ·  **the point of the round**

**PASS — 3/3**

- Settings written (D-MOTO): `wifi-connection-mode=3`, `native-ap-transport=0`, `static-bssid=0`
  (string), `log-level=1`, `native-wifi-version-exchange=false`,
  `insecure-aa-rfcomm-listener=false`, `auto-start-bt-macs={DC:B7:2E:5E:4E:59}`.
  **`native-aa-complete-hfp-slc` absent** — verified `grep -c` = 0 in `settings.xml` before every
  launch.
- Radio state: all radios left up on all three devices (brief §2). Phone not touched during any run.
- Discard-rule check: **clean** each run — 1× `createGroup SUCCESS`, one DIRECT SSID, 0 Magic
  Garbage. The lone `MATCH! Starting AapService` per run is the phone's own Bluetooth reconnect with
  no group churn attached (§7a) — not contamination.

| run | SLC established | D-POCO HeadsetStateMachine → Connected | `NO_HFP_FROM_HU_PRESENCE` | `Incoming connection detected` | SSL | launch→SSL |
|---|---|---|---|---|---|---|
| r1a | **1** (10:30:17.8) | yes (10:30:18.9) | **0** | **1** (10:30:24.9) | 10:30:25.2 | ~14 s |
| r1b | **1** (10:34:50.4) | yes (10:34:51.2) | **0** | **1** (10:34:54.1) | 10:34:54.5 | ~11 s |
| r1c | **1** (10:38:57.4) | yes (10:38:58.4) | **0** | **1** (10:39:01.3) | 10:39:01.5 | ~11 s |

Decisive lines (r1a, representative):

```
10:30:13.385  NativeAaHandshakeManager.shouldRegisterDummyHfp | NativeAA: radio [motorola edge 30 neo] gets the stand-in HFP record, because it advertises no Hands-Free.
10:30:17.534  NativeAaHandshakeManager.pokeDevice | NativeAA: Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms...
10:30:17.540  NativeAA: HFP responder active for HFP-AG poke to DC:B7:2E:5E:4E:59
10:30:17.799  NativeAA: hands-free service level connection established (HFP-AG poke to DC:B7:2E:5E:4E:59). The phone now treats this head unit as its hands-free device...
10:30:18.155  NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) on local radio [motorola edge 30 neo]
10:30:24.895  WirelessServer: Incoming connection detected from /192.168.49.171
10:30:25.237  AapSslContext.performHandshake | SSL handshake complete. Session id: 4xb0e+S4X3g4SdtLG5Cad6l/CeBU2uMWi8pBbiiFTtM=
```

Phone side (r1a): `NO_HFP_FROM_HU_PRESENCE` = 0, `WIRELESS_SETUP_SHARED_HFP_CONNECTING` = 7,
`WIRELESS_SETUP_FAILED` = 0. D-POCO `HeadsetStateMachine` for D-MOTO:
`Disconnected → … → Connected` at 10:30:18.9 (then cycles as each 15 s poke hold releases — the
same transient shape round 1's R3 showed).

**Stand-in variant (the new instrument's first hardware outing):** all three D-MOTO runs printed
`gets the stand-in HFP record, because it advertises no Hands-Free`. D-MOTO's reflective UUID read
succeeds; the "records could not be read" branch is not exercised here.

Video: none in any R1 run — see Setup notes (a D-MOTO-as-head-unit projection-activity artifact,
not this change). The AAP session forms and holds regardless: SSL complete, all four media sinks
set up, Spotify audio streaming `state=PLAYING` continuously to the end of each ~3 min capture, no
`ByeBye` / teardown.

**Not falsified.** R1 succeeds at the default exactly where round 1 succeeded with `true` written
by hand. The default reaches the gate.

---

## R2 — the opt-out still beats the new default

**PASS**

- Settings written (D-MOTO): R1's set, but **`native-aa-complete-hfp-slc=false`** — read back
  verbatim (`native-aa-complete-hfp-slc" value="false"`).
- Radio state: as R1.
- Discard-rule check: 3× `createGroup SUCCESS` / 3 SSIDs / 6× `MATCH! Starting AapService` —
  **expected and not a distortion**: no session forms by design, so the poke loop keeps retrying
  every ~30 s and the self-wake loop churns the group. R2's PASS condition *is* "no session", which
  this is consistent with.
- Decisive counts:
  - `hands-free service level connection established` = **0**
  - `HFP responder active for` = **0**
  - `Successfully poked` = 6 (poke loop still runs; `via HFP-AG` ×12), but each poke's HFP exchange
    is gated off — no responder, no SLC.
  - Phone: `NO_HFP_FROM_HU_PRESENCE` = **12**, `WIRELESS_SETUP_FAILED` = **12**,
    `Incoming connection detected` = 0, `SSL handshake complete` = 0 — the failure is back.
- D-POCO `HeadsetStateMachine` for D-MOTO: reaches `Connecting` then `Disconnecting`/`Disconnected`
  every poke cycle (5.0–5.1 s), **never `Connected`** — the baseline shape from round 1's R1.

Round 1's R4 result reproduced through the new single gate rather than the old half-switch.

---

## R3 — real head unit regression, D-HU

**PASS**

- Settings written (D-HU): `wifi-connection-mode=3`, `native-ap-transport=0`, `static-bssid=0`,
  `log-level=1`, `native-wifi-version-exchange=false`, `insecure-aa-rfcomm-listener=false`.
  **`native-aa-complete-hfp-slc` absent** (`grep -c` = 0) → runs at the new default. Pre-existing
  `auto-start-bt-macs={DC:B7:2E:5E:4E:59}` left in place (brief: "not needed", not "must be absent").
- Radio state: all radios up, phone not touched.
- Discard-rule check: **clean** — 1× `createGroup SUCCESS`, one SSID (`DIRECT-01-HeadUnit`).
- Decisive lines:

```
10:46:48.529  NativeAaHandshakeManager.shouldRegisterDummyHfp | NativeAA: radio [Navegadortz2] already advertises Hands-Free, so the stand-in HFP record is not registered - the real stack answers calls, this app cannot.
10:46:48.607  D/OPENHU  BluetoothHelper.adapterForService | BluetoothHelper: adapterForService(android.hardware.bluetooth.audio.IBluetoothAudioProviderFactory/default) failed: ...
10:46:52.168  NativeAA: Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms...
10:46:52.361  NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) on local radio [Navegadortz2]
10:46:55.897  WirelessServer: Incoming connection detected from /192.168.49.147
10:46:56.115  AapSslContext.performHandshake | SSL handshake complete.
10:46:56.134  NativeAA: AA Server socket closed after successful handoff.
```

- `already advertises Hands-Free` = **1** → `gets the stand-in HFP record` = **0**. Skip branch, as
  in round 1's R5.
- `hands-free service level connection established` = **0**, `HFP responder active for` = **0** —
  the whole HFP path stays inert on the real head unit with the setting on by default.
- One clean session: `Connection accepted from` → `Incoming connection detected` → SSL, launch→SSL
  ~10 s. **33 throughput windows, every one `dropped=0`, 46–51 fps** (`Media Start Request VIDEO` =
  1). Capture runs to 10:49:47 (~3 min), no `ByeBye` / teardown.
- **`adapterForService(...) failed` prints at `D` (DEBUG), not `E`** — round 1's R5 caught it as an
  error; `74a70d12` demotes it. Single occurrence, same call as baseline on a single-radio unit.
- Phone: `NO_HFP_FROM_HU_PRESENCE` = 12 over the window — the first two precede the session (the
  phone's own initial attempts), the rest are the poke loop's later re-attempts against an
  already-live session (D-HU never registers the stand-in, so shared-HFP wireless setup keeps
  failing) — cosmetic; the session that formed at 10:46:56 via the phone's own reconnect ran video
  at 50 fps `dropped=0` straight through all of them.

The reordered gate and the demoted log regress nothing on a real head unit.

---

## R4 — harvest, and two greps that need no hardware

Per-capture counts (`hfp_slc_default_run.sh` harvest):

| capture | Successfully poked | HFP responder active | SLC established | Connection accepted from | HFP connection accepted from | Incoming connection detected | SSL handshake complete | phone `NO_HFP` |
|---|---|---|---|---|---|---|---|---|
| r1a | 1 | 1 | 1 | 1 | 0 | 1 | 1 | 0 |
| r1b | 1 | 1 | 1 | 1 | 0 | 1 | 1 | 0 |
| r1c | 1 | 1 | 1 | 1 | 0 | 1 | 1 | 0 |
| r2  | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 12 |
| r3  | 1 | 0 | 0 | 1 | 0 | 1 | 1 | 12 |

(`HFP connection accepted from` = 0 everywhere — that line is for the *accepted* RFCOMM socket path,
which has still never fired on this rig.)

Two greps over the surviving round 1 D-MOTO captures (all 8 `hu_*.txt`):

```
grep -ac "could not read radio"       → 0   (NOT DECISIVE — string postdates c5609e16; see Setup notes)
grep -ac "audio sink decoy accepted"  → 0   (nothing ever dialled the decoy — its removal is provably free)
```

The decoy-removal check is clean: **0** — the PR can state the removal is behaviour-neutral against
the validated build. The unreadable-adapter question is answered by R1's live capture instead:
D-MOTO's reflective read succeeds, so that branch is unexercised on this rig (neither a FAIL).

---

## Anything the brief did not ask about

- **`AapProjectionActivity` will not stay foregrounded on a phone acting as head unit.** On D-MOTO
  it pauses 17–40 ms after resuming, every run, with a portrait/landscape surface mismatch logged
  (`expected 2237x1080, actual 1080x2400`). Gearhead then withholds the video stream and only audio
  runs. Harmless for this round (R1 needs no video), but any future round that needs *video* from
  D-MOTO-as-head-unit will hit this — use D-HU, or a landscape tablet, for video coverage.
- **`CAR.SERVICE: InitialVideoFocus: Disabled by setting.`** appears on D-POCO in *every* session
  this round, including R3 where video ran fine at 50 fps. It is not the cause of R1's missing
  video — do not chase it if it turns up in a future no-video report.
- **The English `native_wifi_version_exchange` string is renamed but the ~18 translated locales
  still say "Modern handshake (version exchange)"** and will show that until re-translated. Cosmetic.
- **D-POCO is Android 15, not Android 12** as the round-2 brief's device table states. Gearhead
  version (`17.5.663204`) matches.
