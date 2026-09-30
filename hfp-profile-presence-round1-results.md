# hfp-profile-presence — round 1 results

**Candidate:** `fork/fix/hfp-profile-presence` @ `c5609e16`
**Baseline:** `fork/fix/bssid-from-interface-address` @ `e6b19c3a`
**APK md5:** candidate `2f905faa867a3667ff04afd9465fe93f` / baseline `499b414b601d32d298325290a104cc05`
**Devices:**
- **D-MOTO** `ZY22GC3BM4` — motorola edge 30 neo, Android (head unit under test, wireless mode 3). BT MAC `A0:46:5A:97:E4:95`.
- **D-POCO** `4f4027e9` — POCO X3 NFC (`surya`), Android 12, Gearhead `17.5.663204-release` (projecting phone). BT MAC `DC:B7:2E:5E:4E:59`.
- **D-HU** `27870808938846` — UNISOC MT50 `MT50_YT610E4GFPSL_U`, Android 14, Gearhead `17.3.662864-release` (regression head unit). BT MAC `11:46:03:10:33:59` (`Navegadortz2`).
**Date:** 2026-09-01

## Setup notes

### Deviations from the brief

- **R1's poke command in the brief is wrong.** The brief's R1 text fires the poke with
  `am broadcast -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es extra_mac ...`.
  `ACTION_NATIVE_AA_POKE` is handled in `AapService.onStartCommand` (`AapService.kt:2071`), not in
  any `BroadcastReceiver`; the manual-poke UI (`HomeFragment.kt:677-681`) sends it with
  `ContextCompat.startForegroundService`. A broadcast with that action is dropped. All poke runs
  used the `TESTING-TEMPLATE.md` §7a form instead:
  `am start-foreground-service -n <pkg>/com.andrerinas.openheadunit.aap.AapService -a ...ACTION_NATIVE_AA_POKE --es extra_mac <D-POCO MAC>`.

- **D-MOTO (head unit under test) logcat is source-filtered, not unfiltered.** Memory
  `moto edge 30 neo logcat needs source filter`: an unfiltered `adb logcat` on this phone buries
  every `OPENHU` line in ROM spam. D-MOTO was captured `OPENHU:V '*:S'`. This keeps every
  `NativeAA:` line (all `AppLog`, tag `OPENHU`) but drops the Android BT-stack framing around them.
  The phone-side profile state that the round turns on is read from **D-POCO** (`dumpsys
  bluetooth_manager`), not from D-MOTO's log, so the filter costs nothing the round needs. D-POCO
  was captured unfiltered per brief §5.

- **Clean-run airplane-mode dance skipped for the poke runs (R1/R3/R4).** §7a: airplane mode
  cannot be toggled from adb on D-POCO, and every poke run needs D-POCO reachable over Bluetooth
  for the poke to connect at all. D-POCO's radios were left up throughout those runs; discard-rule
  checks (second `createGroup SUCCESS`, second SSL handshake, `p2p-wlan0-N` bump) still applied.

### Instrument the round depends on

The brief (§2) flags the phone-side profile-state read as unverified. It resolves cleanly on
D-POCO: `dumpsys bluetooth_manager` → `Profile: HeadsetService` carries a per-device
`==== StateMachine for <D-MOTO MAC> ====` block with `mCurrentState` / `curState` and a timestamped
`StateMachineLog` of transitions (`Disconnected → Connecting → Connected → Disconnecting →
Disconnected`). D-POCO is the AG (phone) and D-MOTO's HFP poke lands as an incoming connection
tracked in this state machine. `HeadsetClientService` is not instantiated on D-POCO (a phone is not
an HF client), so there is no client-side view; the AG-side `HeadsetStateMachine` is the read.

The backup-state dump already showed one such episode for D-MOTO's MAC at `06:06:17` today
(`Disconnected → Connecting → Connected → Disconnecting → Disconnected` in ~240 ms) from the
pre-round beta4 build — so the mechanism is present on this rig.

### The poke could not be triggered on demand on D-MOTO

`ACTION_NATIVE_AA_POKE` reaches `pokeDevice()` only through `AapService` (`onStartCommand`), which
is `exported="false"`, and `HomeFragment` starts it with `ContextCompat.startForegroundService`
from the app's own process. From `adb shell` (uid 2000) every route is refused:
`am start-foreground-service` / `am startservice` → `Error: Requires permission not exported from
uid 10491`; `am broadcast` → `result=0` but no receiver. D-MOTO is not rooted. The manual-poke UI
dialog would work but lists 8 bonded devices and may need scrolling, which the house rules forbid
scripting.

**Workaround:** set the "Auto Start BT Device" (`auto-start-bt-macs`) to D-POCO's MAC only, so
`NativeAaHandshakeManager`'s auto-poke loop pokes just D-POCO every ~15–30 s. That is the same
`pokeDevice()` code path a manual poke uses and is a plain user setting. R1/R2 ran before this was
in place and relied on the "poke all paired devices" fallback loop reaching D-POCO on its own
(it did in R1 and R2a, did not in R2b — the loop walks all 8 bonded devices at ~10 s each and
`recoverNativeGroup` restarts the walk every 60 s).

**`settings.xml.bak` gotcha (cost one R1 attempt).** A first version of `set_autostart_btmac.sh`
did `cp settings.xml settings.xml.bak` before editing. Android SharedPreferences treats an
existing `.bak` as "the last write aborted" and, on the next load, deletes `settings.xml` and
renames `.bak` over it — silently discarding the edit. The app then re-ran its `auto-start-bt-macs`
migration and wrote back an empty `<set/>`. Fixed by never creating `.bak` and `rm -f`-ing any
stray one before each write. `set_prefs_runas.sh` (plain key writes) was never affected.

### D-POCO's Gearhead was mid-retry against D-HU for R1/R2

Through R1 and R2, D-POCO's Gearhead was in a `WIRELESS_SETUP_WPP_RESTART_WITH_DELAY` /
`THROTTLE_LIMIT_EXCEEDED` loop, dialling **D-HU**'s AA RFCOMM UUID (`11:46:03:10:33:59`,
`Navegadortz2`) every ~5 s — D-HU was powered but not listening in mode 3. That is why R1/R2's
`NO_HFP_FROM_HU_PRESENCE` lines are D-HU-directed and no D-MOTO-directed one appears. It did **not**
block R3: the moment the candidate completed the HFP SLC with D-POCO's HFP-AG (08:44:39), Gearhead
switched to `startWirelessSetup for <D-MOTO MAC>` and formed the session. So D-POCO does hold a
stored config for D-MOTO (`CarInfoInternal … model=Desktop Head Unit, bluetoothaddress=A0:46:5A:
97:E4:95, known=true`); no "forget car" was needed.

### Scripts

- Added `hur-wifi-test-scripts/hfp_poke_probe.sh` — brings the head-unit-under-test up in mode 3
  and samples the projecting phone's `HeadsetService` per-device state machine every N s while
  OHU's auto-poke loop pokes it; harvests the HFP and session landmarks from both sides. Used for
  R1–R5.
- Added `hur-wifi-test-scripts/set_autostart_btmac.sh` — writes/clears the `auto-start-bt-macs`
  StringSet (which `set_prefs_runas.sh` cannot do), with the `.bak` fix above.
- Used `set_prefs_runas.sh` (multi-key, `DEVICE=<serial>`) for every scalar settings write.
- Used `build_hur.sh` / `run_unit_tests.sh` for R0.

### Devices restored

D-MOTO `settings.xml` and D-HU `settings.xml` both `diff`-clean against their pre-round backups.
Candidate `2f905faa…` left installed on D-MOTO and D-HU (D-POCO untouched, still on `3.3.0-beta3`
`b09df77f…`). All three BT radios enabled. Mutual bond D-MOTO↔D-POCO intact.

## R0 — build gate

**PASS**

- Candidate `assembleGithubDebug` clean; `testGithubDebugUnitTest` **1069 tests, 0 failures**.
- Baseline `assembleGithubDebug` clean; `testGithubDebugUnitTest` **1049 tests, 0 failures**.
- Delta **+20**, exactly as the brief predicted (measured, not computed).
- New class present in candidate only: `HfpSlcInitiatorTest` **15** (absent from baseline).
- `BluetoothWakePolicyTest` **15 → 16**. `HfpServiceRecordPolicyTest` **5 → 9**. Both as predicted.
- APK md5s recorded above and differ.

## R1 — the premise, on the baseline

**PASS (premise holds)**

- APK: baseline `499b414b…` (verified live md5 on D-MOTO).
- Settings written: `wifi-connection-mode=3`, `native-ap-transport=0`, `static-bssid=0`,
  `log-level=1`, `native-wifi-version-exchange=false`, `insecure-aa-rfcomm-listener=false`;
  `native-aa-complete-hfp-slc` absent (not in baseline).
- Radio state: D-POCO WiFi+BT left up throughout (needed for the poke to connect; airplane dance
  not usable, §7a). Mutual bonding D-MOTO↔D-POCO confirmed before the run.
- Discard-rule check: `createGroup SUCCESS` fired 3× — the `recoverNativeGroup` 60 s "no phone
  joined" recreate loop, inherent to a run where no phone ever joins (this is a phone-as-head-unit
  bring-up with the projecting phone's Gearhead engaged elsewhere), not session contamination.
  `Handshake: SSL handshake complete` 0×.

**The poke could not be fired manually** (see Setup notes). Instead OHU's own auto-poke loop
(`No 'Auto Start BT Device' selected … Poking all paired devices as fallback`) was left to reach
D-POCO. It walks all 8 bonded devices (~10 s each), so it reached D-POCO once inside the capture
and twice more in the app's continued runtime between runs. Every one of those is a baseline poke
by the same `pokeDevice()` path.

Decisive lines:

```
08:27:17.719  NativeAA: Calling socket.connect() for POCO X3 NFC via HFP-AG …
08:27:18.985  NativeAA: Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms...
```

D-POCO `dumpsys bluetooth_manager` → `HeadsetService` → `StateMachine for <D-MOTO MAC>`, across
the three baseline pokes:

| poke connect | Connecting entered | left Connecting | time in Connecting | reached Connected? |
|---|---|---|---|---|
| 08:18:07.991 | rec[9] `dest=Connecting` | 08:18:13.095 `→Disconnected` | **5.10 s** | **no** (valInt never 3) |
| 08:20:04.152 | rec[13] `dest=Connecting` | 08:20:09.267 `→Disconnected` | **5.11 s** | **no** |
| 08:27:18.241 | rec[16/17] `dest=Connecting` | 08:27:23.310 `→Disconnected` | **5.07 s** | **no** |

Sample `S+240s` (08:27:22, mid-hold) caught it live: `mCurrentState: Connecting`. Sample `S+260s`
(08:27:42): back to `Disconnected`.

Baseline HU log for the whole run: `HFP responder active` **0**, `HFP TX (` **0**, `HFP RX (`
**0**, `hands-free service level connection established` **0**. The baseline holds the poke socket
silently — the phone opens its hands-free profile to `Connecting`, waits to be spoken to, and
times out to `Disconnected` after ~5.1 s without ever reaching `Connected`. That is exactly the
mechanism the round is built on.

`NO_HFP_FROM_HU_PRESENCE` for D-MOTO: **not observed** — D-POCO's Gearhead was in an RFCOMM-retry
loop toward **D-HU** (`11:46:03:10:33:59`, `WIRELESS_SETUP_WPP_RESTART_WITH_DELAY` /
`THROTTLE_LIMIT_EXCEEDED`, ~1 attempt / 5 s) the entire round and never targeted D-MOTO. Per brief
§2 the per-device profile state is the primary read and the Gearhead counter is the fallback
"if nothing resembling per-device state appears"; the per-device state is unambiguous, so this is
a PASS on the primary condition. The Gearhead wedge is a separate rig-state problem (see Setup
notes) that also caps R2/R3's session half.

## R2 — candidate, setting off

**PASS** (both runs; a→below, b→below)

- APK: candidate `2f905faa…` (verified live). Settings: as R1 plus
  `native-aa-complete-hfp-slc=false` (read back verbatim from `settings.xml`).
- Discard check: `createGroup SUCCESS` 4× (R2a) — same `recoverNativeGroup` loop as R1, no phone
  ever joins. `SSL handshake complete` / `Incoming connection detected` 0×.

**R2a:** `Successfully poked POCO X3 NFC via HFP-AG` ×2 (08:34:42, 08:36:36). With the setting
off: `HFP responder active` **0**, `HFP TX (` **0**, `HFP RX (` **0**,
`hands-free service level connection established` **0** — the poke socket does **not** open the SLC
(`shouldInitiateSlc()` = `nativeAaCompleteHfpSlc && standingInForHfp`, first term false). D-POCO's
E4:95 profile went to `Connecting` on the 08:36:36 poke and timed out, same as baseline. No
crash, no `E/OPENHU`, no `AndroidRuntime`, no new error-level line. Group forms every time.
No worse than R1.

Observations (not verdicts):
- **Record decisions on D-MOTO:** `already advertises Hands-Free` **absent** → the stand-in HFP
  record was published and `standingInForHfp = true`. `already advertises an audio sink`
  **absent** → the audio-sink decoy was published. `audio sink decoy accepted` **0** and
  `NativeAA: HFP connection accepted from` **0** — nothing dialed either record (the phone's
  Gearhead never targeted D-MOTO). The decoy's own question stays at 0, as in the five prior
  rounds; not a failure of this run.
- Gearhead `NO_HFP_FROM_HU_PRESENCE` ×2, but D-HU-directed (`rfcomm socket for device:
  11:46:03:10:33:59` ×9), not D-MOTO.

**R2b:** No crash, no error line, group forms (`createGroup SUCCESS` 6× — recovery loop), no
`SSL handshake complete`. **The auto-poke fallback loop never reached D-POCO this run** — with
`auto-start-bt-macs` empty it walks all 8 bonded devices at ~10 s each, and `recoverNativeGroup`
restarts the walk every 60 s, so it only ever cycled the first ~6 (`WH-1000XM4` 6×, `Redmi Watch`
5×, `FX Plus` 5×, `KY Pro` 5×, `Mi True Wireless` 4×, `Magnetic Speaker` 1×; `POCO X3 NFC` **0×**).
So R2b is a clean **regression PASS** but the poke→phone path itself was not exercised. R2a
already exercised it (setting-off poke correctly silent). **R2 overall: PASS.**

For R3/R4 the "Auto Start BT Device" (`auto-start-bt-macs`) was set to D-POCO's MAC only, so the
loop pokes just D-POCO every ~15–30 s. That is a plain user setting and a settings-file write with
the app stopped (see Setup notes for the `.bak` gotcha that made the first attempt silently
revert).

## R3 — candidate, setting on (the point of the round)

**PASS — 3 / 3 attempts, full session every time**

- APK candidate `2f905faa…`. Settings: R1 set + `native-aa-complete-hfp-slc=true` (read back
  verbatim) + `auto-start-bt-macs = {DC:B7:2E:5E:4E:59}`.
- Discard check: `createGroup SUCCESS` **1×** on all three attempts (no churn); one
  `SSL handshake complete` per session (the log counts 2 — version-request + SSL, same handshake,
  6 ms apart).

| | R3a | R3b | R3c |
|---|---|---|---|
| `Successfully poked POCO … via HFP-AG` | 1 | 1 | 1 |
| `HFP responder active` + full `AT+BRSF/CIND/CMER` exchange | ✓ | ✓ | ✓ |
| `hands-free service level connection established` | **1** | **1** | **1** |
| D-POCO `HeadsetStateMachine` for D-MOTO reached **Connected** (valInt=3) | ✓ 08:44:38.228 | ✓ 08:50:21.827 | ✓ |
| Gearhead line | `WIRELESS_SETUP_SHARED_HFP_CONNECTING` (proceeds) | same | same |
| `NO_HFP_FROM_HU_PRESENCE` | **0** | **0** | **0** |
| `Connection accepted from POCO X3 NFC` | **1** | **1** | **1** |
| `WirelessServer: Incoming connection detected` | **1** (from /192.168.49.218) | **1** | **1** |
| `SSL handshake complete` → session | ✓ | ✓ | ✓ |
| video after warm-up | 48–51 fps, `dropped=0` | 49–51, `dropped=0` | 49–50, `dropped=0` |

Timeline of R3a (the exemplar), all on `08:44:3x`:

```
38.799  Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms...
38.801  HFP responder active for HFP-AG poke to DC:B7:2E:5E:4E:59
38.802→39.014  AT+BRSF=0 / +BRSF: 879 / AT+CIND=? / AT+CIND? / AT+CMER=3,0,0,1 / OK
39.015  W  hands-free service level connection established (…). The phone now treats this head unit as its hands-free device…
39.260  Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) on local radio [motorola edge 30 neo]
39.58   [TX] Sending WifiStartRequest (Type 1)
39.682  [RX] Received Type 2
40.695  [TX] Wrote TYPE 3 (WifiInfoResponse, 66 bytes)
40.707  [RX] WifiStartResponse status=SUCCESS(0)
42.748  [RX] WifiConnectStatus status=SUCCESS(0)  (phone is on our network)
42.771  WirelessServer: Incoming connection detected from /192.168.49.218
42.935  SSL handshake complete
44.273  VideoDecoder: Configuring decoder c2.qti.avc.decoder for 1920x1080
```

Gearhead's own view (R3a): `startWirelessSetup for <D-MOTO MAC>. Current state: IDLE` →
`getCarInfoInternal … model=Desktop Head Unit, bluetoothaddress=A0:46:5A:97:E4:95, known=true` →
`WIRELESS_SETUP_SHARED_HFP_CONNECTING` and it proceeds. So D-POCO **does** have a stored config
for D-MOTO; the only thing that changed between R2/R4 (no session) and R3 (session) is that the
candidate completed the HFP SLC, which cleared Gearhead's HFP-from-HU-presence gate.

**This is the first two-phone Native AA session ever formed on this rig** (three prior threads,
`bssid`/`headunit-info`/`intercom`, all recorded it as blocked). It formed on all three R3
attempts and did not form on either R2 run or R4. The brief's mechanism is confirmed end to end.

## R4 — the positive control

**PASS — the failure comes back**

Immediately after R3c, `native-aa-complete-hfp-slc` flipped to `false`, everything else identical
(candidate APK, `auto-start-bt-macs` still D-POCO, same session state). One run.

- `Successfully poked POCO X3 NFC via HFP-AG` **7×** — the poke still connects fine.
- `HFP responder active` **0**, `HFP TX (` **0**, `hands-free service level connection
  established` **0** — the poke socket no longer opens the SLC.
- D-POCO `HeadsetStateMachine` for D-MOTO: **`Disconnected` at every sample**, never Connected.
- **`WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE` ×14** — the exact failure the round
  targets, restored.
- `Connection accepted from` **0**, `Incoming connection detected` **0**, `SSL handshake complete`
  **0** — **no session**. `createGroup SUCCESS` 4× (recovery loop, no phone joins).

The two-phone link stops working the moment the setting goes off. R3's pass belongs to this
branch and to `native-aa-complete-hfp-slc`, not to anything else that changed on the rig.

## R5 — real head unit regression (D-HU)

**PASS**

- APK candidate `2f905faa…` on D-HU (verified live). Settings: `wifi-connection-mode=3`,
  `native-ap-transport=0`, `static-bssid=0`, `log-level=1`, `native-wifi-version-exchange=false`,
  `insecure-aa-rfcomm-listener=false`, `native-aa-complete-hfp-slc=false`. Phone: D-POCO.
- Discard check: `createGroup SUCCESS` **1×**, one SSL handshake, one `p2p-wlan0` interface.

**Both record decisions took the skip branch, as the brief expected:**

```
09:03:05.885  NativeAA: radio [Navegadortz2] already advertises Hands-Free, so the stand-in HFP record is not registered - the real stack answers calls, this app cannot.
09:03:05.914  NativeAA: radio [Navegadortz2] already advertises an audio sink, so no decoy is published.
```

So on D-HU `standingInForHfp = false`, no decoy socket is opened, and the candidate's entire new
HFP path is inert (`shouldInitiateSlc()` = `nativeAaCompleteHfpSlc && standingInForHfp`, both
false). `hands-free service level connection established` **0**, `HFP responder active` **0** —
correct.

Session: `ACTIVELY LISTENING` 1 → `Connection accepted from` 1 → `WirelessServer: Incoming
connection detected` 1 → `SSL handshake complete` → `Configuring decoder c2.unisoc… avc.decoder`
(pinned once, no restart). Video **46–56 fps, `dropped=0` / `skipped=0` / `concealed=0`** for the
sustained ~3 min (`09:03:20`–`09:06:05`); three natural-GOP `keyframe decoded - the picture is
repaired` about 70 s apart; no `sync_stall`, no `ByeBye`, no disconnect. The last ~15 s of the
capture drop to 39→15→13 fps as the capture is torn down — the rig's known parked-nav phone-side
adaptive-rate collapse (`project_test_headunit_gearhead_and_nav_fps`), `dropped` still 0, not a
regression.

One error-level line, pre-existing, not from this branch:
`E/OPENHU BluetoothHelper.adapterForService(…IBluetoothAudioProviderFactory/default) failed:
NoSuchMethodException`, from `getAllBluetoothAdapterHandles` in `NativeAaHandshakeManager.start()`
— that call is at line 383 in the baseline and 397 in the candidate and `BluetoothHelper.kt` is
byte-identical between the two (`git diff e6b19c3a c5609e16` empty). It is the six-deep
secondary-radio reflection probe missing on this single-BT-radio unit, caught; the session forms
immediately after.

## R6 — harvest

Per-capture counts (`grep -acF`, D-MOTO/D-HU log = HU side, D-POCO log = phone side):

| capture | `Successfully poked POCO` | `HFP responder active` | `…SLC established` | `Connection accepted from` | `Incoming connection detected` | `SSL handshake complete` | phone `NO_HFP_FROM_HU_PRESENCE` |
|---|---|---|---|---|---|---|---|
| R1 baseline | 1 | 0 | 0 | 0 | 0 | 0 | 0 (D-HU-directed only, not D-MOTO) |
| R2a cand/off | 2 | 0 | 0 | 0 | 0 | 0 | 0 (D-HU-directed) |
| R2b cand/off | 0 (loop never reached POCO) | 0 | 0 | 0 | 0 | 0 | 0 |
| **R3a cand/on** | 1 | 1 | **1** | **1** | **1** | 2 | **0** |
| **R3b cand/on** | 1 | 1 | **1** | **1** | **1** | 2 | **0** |
| **R3c cand/on** | 1 | 1 | **1** | **1** | **1** | 2 | **0** |
| **R4 control/off** | 7 | 0 | 0 | 0 | 0 | 0 | **14** |
| R5 D-HU regr. | 1 | 0 (standingInForHfp=false) | 0 | 1 | 1 | 2 | 0 |

Full `HFP TX` / `HFP RX` exchange, verbatim and in order, from R3a (first time observed on
hardware):

```
08:44:38.801  HFP responder active for HFP-AG poke to DC:B7:2E:5E:4E:59
08:44:38.802  HFP TX: AT+BRSF=0
08:44:38.854  HFP RX: +BRSF: 879
08:44:38.854  HFP RX: OK
08:44:38.856  HFP TX: AT+CIND=?
08:44:38.908  HFP RX: +CIND: ("call",(0,1)),("callsetup",(0-3)),("service",(0-1)),("signal",(0-5)),("roam",(0,1)),("battchg",(0-5)),("callheld",(0-2))
08:44:38.908  HFP RX: OK
08:44:38.909  HFP TX: AT+CIND?
08:44:38.961  HFP RX: +CIND: 0,0,1,5,0,4,0
08:44:38.961  HFP RX: OK
08:44:38.962  HFP TX: AT+CMER=3,0,0,1
08:44:39.014  HFP RX: OK
08:44:39.015  hands-free service level connection established (HFP-AG poke to DC:B7:2E:5E:4E:59). …
08:44:39.016  HFP TX: AT+CIND?            (keepalive, every 2 s)
08:44:39.217  HFP RX: +BSIR: 0
08:44:39.218  HFP RX: +BSIR: 1
08:44:39.218  HFP RX: +CIND: 0,0,1,5,0,4,0
08:44:39.218  HFP RX: OK
```

The phone's reply to `AT+BRSF` is **`+BRSF: 879`** (0x36F) on all three R3 runs — D-POCO's
AG feature bitmap (three-way, EC/NR, voice-recognition, in-band ring, reject-call, enhanced call
status, extended error, codec negotiation).

## Report back — the three numbers

1. **R1's phone-side profile state on the baseline:** the hands-free profile enters `Connecting`
   and times out. Three baseline pokes, D-POCO's `HeadsetStateMachine` for D-MOTO went
   `Disconnected → Connecting → Disconnected` in **5.10 / 5.11 / 5.07 s**, valInt never reached 3
   (Connected). Premise holds.

2. **R3's SLC-established count, profile state, and `Connection accepted from` — the three
   together:** on all 3 attempts, `hands-free service level connection established` = **1**, the
   phone's hands-free profile for D-MOTO **reached Connected**, `Connection accepted from` = **1**,
   `NO_HFP_FROM_HU_PRESENCE` = **0**, and `WirelessServer: Incoming connection detected` followed
   each time into a full SSL session at ~50 fps `dropped=0`. **A complete two-phone Native AA
   session formed on all three R3 attempts — the first time that has happened on this rig.**

3. **R5's session on D-HU:** formed. Clean single session, `Incoming connection detected`, SSL,
   ~50 fps `dropped=0`. Both record decisions took the **skip** branch (`already advertises
   Hands-Free`, `already advertises an audio sink`) — no decoy published beside the real stack,
   the new HFP-SLC path inert.

**R4 (control):** yes, the failure comes back. Setting → `false`, everything else identical: no
`established` line, profile never Connected, `NO_HFP_FROM_HU_PRESENCE` ×14, no session.

**Shipping read:** the fix does what the brief says. Completing the HFP SLC (candidate commits
`c53d0d7e` for the accepted socket, `a7de6339` for the poke socket behind `native-aa-complete-
hfp-slc`) makes the projecting phone count the head unit's Bluetooth as connected-with-profile and
proceed with wireless setup, where the silent hold left it stuck in `Connecting` until timeout.
R3 3/3 with a full session, R4 restores the exact failure, R5 shows nothing regresses on a real
head unit. The audio-sink decoy (`c5609e16`) was published on D-MOTO and dialed by nobody
(`HFP connection accepted from` = 0, as in five prior rounds) and correctly skipped on D-HU;
it neither helped nor hurt here.

## Anything the brief did not ask about

- `serveHfpSocket(..., initiate = true, ...)` on the **accepted** HFP socket path
  (`NativeAaHandshakeManager.kt:375` and `:466`) is not behind the `native-aa-complete-hfp-slc`
  setting — it runs whenever `standingInForHfp` is true (commit `c53d0d7e`). Only the **poke's own
  socket** (`holdPoke` → `shouldInitiateSlc()`, `:843`) is gated by the setting (commit
  `a7de6339`). So R2 (setting off) still opens the SLC on any HFP connection the phone dials to us;
  R2 vs R3 isolates only the poke-initiated exchange. In practice the accepted path was never
  reached here: `NativeAA: HFP connection accepted from` = 0 in every run — D-POCO never dialed
  D-MOTO's stand-in Hands-Free record; the SLC that mattered every time was the poke's **outbound**
  one to D-POCO's HFP-AG.

- **A two-phone Native AA session forming at all is new for this rig.** `two_phone_native_aa_
  blocked_waitforheadunit` in memory (and the `bssid` round-1 addendum) both say mode-3 never
  forms between two phones here because "OHU's 15 s poke can't hold a stable BT link" for
  Gearhead's wireless SLC. With the candidate that is no longer true — the poke now *is* the HFP
  SLC, it reaches Connected in ~0.8 s, and Gearhead proceeds. Worth updating that memory:
  D-MOTO(candidate)+D-POCO is now a working two-phone Native AA pair for regression, at least while
  the poke connects.

- **The poke self-wakes the head unit.** On the successful R3/R4 pokes,
  `AutoStartReceiver.onReceive | BT Device connected: POCO X3 NFC` fires from the poke's own
  `socket.connect()` (the feedback loop CLAUDE.md documents), landing an `ACTION_BT_AUTO_START`.
  On R4 (setting off) that contributed to the 4 `createGroup SUCCESS` recreations; on R3 the
  session formed before it could re-init the group.

- **R3 return-to-video is slow but clean.** After `Configuring decoder` the first ~5 s render at
  ~13 fps, then a ~25 s window of `rendered=0` (phone-side projection warm-up, `dropped`/
  `concealed` stay 0), then a steady ~50 fps for the rest of the capture. Not a defect, but a
  ~30 s "connected but no picture yet" gap a user would see on this pair.

- **`+BRSF: 879`** — noted here in case the AT responder's feature negotiation is ever revisited:
  the responder sends `AT+BRSF=0` (no HF features) and does not parse the AG's `879` back, which
  is fine for a decoy SLC but means it advertises itself as a featureless HF.

- **The WiFi Direct group is left running after OHU is closed by `force-stop` (or a crash / OS
  kill).** `WifiDirectManager` only calls `removeGroup()` on an explicit `stop()` path
  (`headunit://exit`, mode change, user exit) and on the *next* launch
  (`startNativeAaQuietHost … Removing old group if any`). A process kill runs neither, so the P2P
  group is orphaned: after this whole round (every run ended with the probe's `am force-stop`, no
  `headunit://exit`), both **D-MOTO** (`p2p0`, `192.168.49.1`, `mWifiP2pInfo groupFormed: true
  isGroupOwner: true`) and **D-HU** (`p2p-wlan0-0`, `192.168.49.1`) still had a live 5 GHz P2P
  group up ~1 h later with OHU not running (`pidof` empty on both). Consequences: it holds the
  P2P interface and the `192.168.49.1` bind, and the next launch's `removeGroup` can come back
  `BUSY` (seen in R5: `Native AA removeGroup before recreate failed (reason=BUSY)` — CLAUDE.md
  reads that line as "expected when no group existed", but here a group *did* exist and was busy).
  Same on baseline (R1's `startNativeAaQuietHost … Removing old group if any` is the launch-time
  cleanup, not a stop-time one), so this is pre-existing, not from this branch.

  Cleanup at round end: a launch → `headunit://exit` → `force-stop` cycle cleared the group on
  **D-HU** (`groupFormed: false`, no `p2p-wlan0-0`) but **not on D-MOTO** — its group
  `DIRECT-Cz-moto edge30 neo_oThE` survived the exit cycle and only came down after an
  `svc wifi disable/enable` (STA then reconnected to `Pegue Cdesta` / `192.168.1.5` normally). So
  on the moto the orphaned group is stickier than a single OHU exit can undo. Final rig state: no
  P2P groups on any device, all three WiFi + BT enabled.
