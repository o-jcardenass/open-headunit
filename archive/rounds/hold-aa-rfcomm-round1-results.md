# hold-aa-rfcomm — round 1 results

**Candidate:** fork `fix/hold-aa-rfcomm` @ `c3c5a2d8`       **Baseline:** origin/main @ `7db89757`
**APK md5:** `52b188106e9c495e9dbf0b29f16b20d6` (candidate) / `0817837c2cfb41e1e8705491d2cd39ee` (baseline)
**Unit:** D-HU = UNISOC MT50 (head unit); D-MOTO = Motorola edge 30 neo, Gearhead `17.8.663814-release` (phone). 5 GHz WiFi Direct link at 5220 MHz. As found: `native-ap-transport=0`, `wifi-direct-band=1`, `stand-down-station-mode=0`.
**Date:** 2026-09-25

## Setup notes

- D-MOTO was not attached at the start of the round (only D-T230 and D-POCO were); the operator connected it before anything else could run.
- The precondition (a live HeadsetClient/hands-free link between D-HU and D-MOTO) was not present at the start: D-HU's single HFP slot was held by D-POCO instead, and D-MOTO's own Bluetooth radio was off. The operator connected D-MOTO's hands-free link from its own Bluetooth settings (system settings, not the app) before R1, and had to repeat this by hand before R4 (after a spontaneous unbond, see below), before R5 (deliberately disconnected, per that run), and before R6 (reconnected after R5). None of this is scriptable on this phone without root; each was a manual, on-device step per this repo's house rules.
- **D-HU and D-MOTO spontaneously unbonded mid-round**, during R4's setup (`BOND_BONDED => BOND_NONE` at 22:28:46.475), then failed two automatic re-bond attempts (`BOND_BONDING => BOND_NONE` at 22:28:48.201 and 22:29:19.830) before giving up. Neither device listed the other as bonded afterward. The operator re-paired them from D-MOTO's Bluetooth settings (scan, select "Navegadortz2", confirm the passkey prompt). Cause not established; see closing section.
- **The app auto-arms Native AA on `MainActivity` launch** whenever `wifi-connection-mode=3` is already set — it does not need an explicit `ACTION_START_WIRELESS_SCAN` after a fresh launch. Twice this round (R1's first attempt, R5's first attempt) an `ACTION_QUERY_STATE` check read `"state":"Disconnected"` moments after launch, which was actually a snapshot mid-auto-arm rather than confirmation that arming was needed. Sending the explicit arm broadcast in that window raced the in-flight auto-arm and forced a second `createGroup`/SSL cycle, contaminating the capture (discard rule: "a second `createGroup SUCCESS`", "a second `SSL handshake complete`"). Both were discarded and redone by checking the logcat directly for `ACTIVELY LISTENING` / `createGroup SUCCESS` / `SSL handshake complete` before deciding whether an explicit arm was actually needed.
- Brief §6's phone-side line `Attempting to connect Bluetooth RFCOMM` does not appear verbatim anywhere in this Gearhead build's logcat. Its functional equivalent, used throughout this report in its place, is `GH.WIRELESS.BT: Creating rfcomm socket for device: <addr> and uuid: <uuid>`.
- This session's own reference memory for D-MOTO's Gearhead version was stale (recorded `17.5.663204` nine days earlier). Verified via `adb shell dumpsys package com.google.android.projection.gearhead | grep versionName` to be `17.8.663814-release` — the exact version this brief's §2 describes as pinging over the held channel. Memory has been corrected.
- R4's `adb -s <D-MOTO> shell cmd bluetooth_manager disable` was **not refused** — it reported `Success` and produced a real, app-visible `ACL_DISCONNECTED`/channel-closed event — but the radio self-reverted after roughly 8 seconds rather than staying down for the intended 30-second window. The scripted `enable` at +30s was still sent per the brief, redundantly, since the radio was already back on.
- R5 needs no active HFP profile. The standard clean-run protocol's phone airplane-mode cycle was **skipped for R5 only**: cycling airplane mode reliably auto-reconnected HFP within seconds at every other run's transition this round, which would have undone the exact state R5 is testing. D-MOTO's WiFi/Bluetooth radios were left as already-on instead, and the "no profile" state was re-verified by reading `dumpsys bluetooth_manager` directly rather than via any setting or toggle.
- Writing/restoring `native-aa-hold-bluetooth-channel` used a pushed single-key `sed` script (`/data/local/tmp/set_pref.sh`), not an inline `adb shell run-as $PKG sh -c '...'`, which failed here with `sh: -c: requires an argument` — consistent with the standing quoting warning for that inline form.
- `hur-wifi-test-scripts/build_hur.sh` was used for both APKs (candidate and baseline), run sequentially with the output copied out and renamed between builds, since the script always overwrites the same output filename.
- **Round executed directly in this session via Bash/adb, not delegated to a subagent for any device-touching step.** This harness's `Agent` tool launches asynchronously with no way to force the call to block until the subagent finishes, so it cannot satisfy "foreground, host waits, no background agent ever drives a device." Only the non-device-touching build step (building both APKs) was delegated to a Haiku agent, since building never touches the rig.

## R0 — Identity
**PASS**
`ACTION_QUERY_STATE`'s `commit` read `7db89757b6b3` before R1 and `c3c5a2d8bbbc` before every run after, matching the brief in both cases.

## R1 — Control, `main`, hands-free held (the retry we expect to remove)
**PASS** (confirms the mechanism this round measures)

- Precondition: HeadsetClient connected to D-MOTO before arm and at the 130s checkpoint.
- SSL handshake complete `22:01:41.481` (single `createGroup`/SSL cycle on the counted attempt; a first attempt was discarded, see Setup notes).
- App landing line (control build): `WiFi session landed. Handshake session ending, releasing Bluetooth connection.` at `22:01:41.322`, immediately followed by `BT Handshake link closed.`
- Held 5 minutes; `R1-end` marker at `22:06:55.609`, disconnect sent `22:06:56.923`.
- Phone-side counts, window SSL → `R1-end` marker (`22:01:41.481`–`22:06:55.609`, ~314s): `Triggering WPP restart` **61**, `Rfcomm socket connection failed` **59**, `Creating rfcomm socket for device` **60**, `CONNECTION_CLOSED_BY_PEER` / `has not received the ping response` / `timed out on pings` / `PING_STATE_HEALTHY` all **0**. `grep -ciE "rfcomm|wpp"` over the window: **1183**.
- Retry cadence ≈ once every 5.1s, matching the brief's "retries every 5s".
- HU throughput steady at 29fps; `inbound link quiet` **0** within the window (one instance was logged later, after the window closed, during the phone's post-disconnect retry storm).
- Discard-rule check: clean on the counted attempt.

## R2 — Candidate, hands-free held (the point of the round)
**PASS**

- Precondition connected at arm, at the 71s checkpoint, and at run end.
- SSL handshake complete `22:10:42.770` (single cycle).
- App landing line (candidate, holding on): `WiFi session landed. Holding the Bluetooth channel for the session and answering the phone's pings, as a head unit does.` — printed once.
- `[HOLD]` summary lines printed every ~60s for the full hold, e.g. `[HOLD] Bluetooth channel held 661s, 0 pings answered.`
- Phone-side counts, window SSL → `R2-end` (`22:10:42.770`–`22:21:58.449`, ~677s): `Triggering WPP restart`, `Creating rfcomm socket for device`, `Rfcomm socket connection failed`, `has not received the ping response` all **0**. `grep -ciE "rfcomm|wpp"` over the window: **35**, all from the pre-landing handshake exchange (`22:10:4x`) plus unrelated FastPair/NearbyDiscovery noise — no RFCOMM activity at all after landing.
- Held 677s (>10 min); release line: `the session ended; releasing the held Bluetooth channel after 677s and 0 pings.`
- HU throughput steady at 29–30fps; `inbound link quiet` **0**.
- Discard-rule check: clean, single `createGroup`/SSL cycle.

**One bullet under R2's checklist could not be confirmed as stated: the answered-ping count never left 0** across the full 677s hold, despite the channel visibly staying open and the retry loop being fully suppressed. Source inspection (`WppHandshakeSession.kt:211,241–245`, `NativeAaHandshakeManager.kt:2889–2890`) shows the counting logic is stage-gated correctly: when a real `PING_REQUEST` arrives while `stage == WppStage.HOLDING`, `onHolding()` returns `SendPingResponse` and the counter at `NativeAaHandshakeManager.kt:2889` should increment. The more likely explanation is that D-MOTO's Gearhead build (confirmed `17.8.663814-release`, the exact version §2 names) simply never sent a WPP `PING_REQUEST` over the held channel in this session — not a counting bug — but this cannot be fully confirmed at the brief's fixed `log-level=2`: the app's fallback path logs the one confirming line at `AppLog.d` (`NativeAaHandshakeManager.kt:2890`), which INFO suppresses, and no independent byte-level trace was captured this round. The round's actual, stated question — whether the phone stops retrying once the channel is held, and whether holding breaks anything — is answered unambiguously: yes, completely, and nothing broke.

## R3 — Candidate, exit and reconnect, 3 cycles
**PASS**

- Live-session baseline: SSL `22:23:47.166` (single cycle).
- Cycle 1: disconnect `22:23:59.304` (release line printed), re-armed `22:24:15.032`, SSL `22:24:24.200` → **9.2s**.
- Cycle 2: disconnect `22:25:10.650` (release line, held 46s), re-armed `22:25:26.208`, SSL `22:25:35.788` → **9.6s**.
- Cycle 3: disconnect `22:26:24.284` (release line, held 48s), re-armed `22:26:39.781`, SSL `22:26:48.608` → **8.8s**.
- All 3 cycles reached `SSL handshake complete` well inside the 90s budget; every exit printed its release line.

## R4 — Candidate, the phone's Bluetooth drops mid-session
**This is a measurement, not a grade**, per the brief.

- Live-session SSL `22:30:17.594` (single cycle; setup hit the spontaneous D-HU/D-MOTO unbond described in Setup notes, resolved before arming — no contamination of the counted cycle, just a slower group-to-SSL time than other runs, ~2m11s).
- 60s into the session: `adb -s <D-MOTO> shell cmd bluetooth_manager disable` → `Success`.
- **The app's own detection**: `NativeAA: the phone closed the held Bluetooth channel after 75s and 0 pings; the Android Auto listeners reopen when this session ends.` at `22:31:32.630`, matching `ACL_DISCONNECTED` at `22:31:32.824`.
- **The radio self-reverted after ~8s** (`ACL_CONNECTED` at `22:31:40.837`) rather than staying down for the scripted 30s window; see Setup notes.
- **The AAP/WiFi session survived the drop**: HU video throughput continuous at 29–30fps straight through `22:31:2x`–`22:33:5x`, with **0** `inbound link quiet` events in that window.
- **The phone did retry RFCOMM once Bluetooth reconnected**: 37 `Triggering WPP restart` / `Creating rfcomm socket for device` / `Rfcomm socket connection failed` lines over the following ~2 minutes (about once every 5s) — every attempt failed, confirming the app's listeners stayed closed and no second connection was accepted.

## R5 — Candidate, no profile link
**PASS**

- D-MOTO's HFP profile to D-HU disconnected (not unpaired) by hand; confirmed via `dumpsys bluetooth_manager` on D-HU (still bonded, `mCurrentDevice: null`, no `HeadsetClientStateMachine` entry).
- First attempt discarded: a redundant explicit arm raced an in-flight auto-arm (see Setup notes); the profile had also silently reconnected on its own during that attempt (see closing section) and needed a second manual disconnect.
- Redo: SSL handshake complete `22:40:45.993` with no HFP profile link present at all — confirms the brief's expectation that the handshake itself still runs over Bluetooth (pairing-level) independent of any profile.
- Held 192s (≥3 min); release line: `... after 192s and 0 pings.`
- Discard-rule check: clean on the counted attempt.

## R6 — Candidate with the switch off (the old behaviour, on the new build)
**PASS**

- `native-aa-hold-bluetooth-channel` written `false` with the app stopped, read back and confirmed. HFP profile reconnected by hand first (had been disconnected for R5).
- SSL handshake complete `22:47:47.830` (single cycle).
- Switch-off landing line: `WiFi session landed. Releasing the Bluetooth channel, because holding it is turned off in Settings.` at `22:47:47.837`, `BT Handshake link closed.` at `22:47:47.841` — **4ms** later, well inside "about a second".
- Phone-side counts, window SSL → `R6-end` (~311s): `Triggering WPP restart` **58**, `Creating rfcomm socket for device` **57**, `Rfcomm socket connection failed` **57** — closely matches R1's shape (61 / 59–60 over ~314s).
- Setting restored to `true` afterward, read back and confirmed.

Across all runs: HU video throughput held steady at 29–30fps and `inbound link quiet` stayed at 0 in every counted window, including R1's and R4's retry-storm windows — this rig's 5 GHz link did not show any stutter, as the brief anticipated.

## Anything the brief did not ask about

- **D-MOTO's Gearhead build is `17.8.663814-release`**, not the `17.5.663204` this session's own reference memory had recorded 9 days earlier (it auto-updated in the interim). Corrected in memory; noted here in case it matters to a future round on this rig.
- **`ACTION_QUERY_STATE`'s snapshot can read `"state":"Disconnected"` while an auto-arm is already in flight.** Checking the logcat directly for `ACTIVELY LISTENING` / `createGroup SUCCESS` before sending an explicit arm is more reliable than trusting one query, and would have avoided both discarded attempts this round.
- **With no HFP profile link to D-MOTO (R5), the app's poke mechanism tried the wrong phone.** `NativeAaHandshakeManager.pokeDevice` logged `Poke via HFP-AG to POCO X3 NFC (DC:B7:2E:5E:4E:59) failed: read failed, socket might closed or timeout, read ret: -1` twice — POCO is the *other* bonded phone, not the one under test. Harmless here (the handshake still landed over the direct RFCOMM listener), but suggests poke-target selection falls back to whichever other bonded device offers an HFP-AG record when the intended target has none connected. Worth a look if a future round cares about poke-target selection with more than one bonded phone present.
- **D-HU and D-MOTO spontaneously unbonded once, mid-round**, with two automatic re-bond attempts that both failed before giving up (see Setup notes). Cause not established — possibly related to the repeated airplane-mode radio cycling across R1–R4, or coincidental with the general BT churn this brief's protocol produces. Flagging in case it recurs on a future round.
- **`cmd bluetooth_manager disable` on D-MOTO reports `Success` and produces a real, app-visible ACL drop, but the radio self-reverts within roughly 8 seconds.** That's faster than the ~45s self-revert `TESTING-TEMPLATE.md` §7a already notes for `svc bluetooth disable`. Anything that needs D-MOTO's Bluetooth to actually stay down for tens of seconds should budget for this, or re-issue the disable partway through.
- **The candidate's hands-free profile auto-reconnected on its own** during R5's discarded first attempt, without any operator action — possibly a side effect of the app's own poke/handshake activity toward that device. Not chased further this round.

## Appendix: first 20 phone-side `rfcomm|wpp` lines, verbatim

### R1 (control), from SSL (`22:01:41.481`)

```
09-25 22:01:41.008 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 603, detail OptionalInt.empty, WIRELESS_WIFI_PROJECTION_PROTOCOL_RFCOMM_START_RESPONSE_SENT, at 920558
09-25 22:01:41.211 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 612, detail OptionalInt.empty, WIRELESS_WIFI_PROJECTION_PROTOCOL_RFCOMM_INFO_RESPONSE_RECEIVED, at 921606
09-25 22:01:41.229 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 613, detail OptionalInt.empty, WIRELESS_RFCOMM_INFO_RESPONSE_RECEIVED, at 921611
09-25 22:01:41.825 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: selectEventStream: use RFCOMM, %s
09-25 22:01:41.858 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: shouldConnectRfcomm: isInAllowedList=%s, isRingingSupported=%s, hasFastPairItem=%s, isWearOsDevice=%s, uuidExist=%s, sdk=%s
09-25 22:01:41.869 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: connectEventStreamForRfcomm: skip connection, RFCOMM unsupported. %s
09-25 22:01:41.945 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: selectEventStream: use RFCOMM, %s
09-25 22:01:41.954 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: shouldConnectRfcomm: isInAllowedList=%s, isRingingSupported=%s, hasFastPairItem=%s, isWearOsDevice=%s, uuidExist=%s, sdk=%s
09-25 22:01:41.954 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: connectEventStreamForRfcomm: skip connection, RFCOMM unsupported. %s
09-25 22:01:41.967 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: selectEventStream: use RFCOMM, %s
09-25 22:01:41.970 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: shouldConnectRfcomm: isInAllowedList=%s, isRingingSupported=%s, hasFastPairItem=%s, isWearOsDevice=%s, uuidExist=%s, sdk=%s
09-25 22:01:41.971 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: connectEventStreamForRfcomm: skip connection, RFCOMM unsupported. %s
09-25 22:01:42.329 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 642, detail OptionalInt.empty, WIRELESS_WIFI_PROJECTION_PROTOCOL_RFCOMM_CONNECT_STATUS_SENT, at 922913
09-25 22:01:42.336 I/bt_snoop(30275): RFCOMM port is closed: handle=1(0x1), lcid=66(0x42), dlci=23(0x17), uuid=4353(0x1101)
09-25 22:01:42.336 W/bt_rfcomm(30275): port_rfc_closed: RFCOMM connection closed, index=21, state=2 reason=Closed[19], UUID=1101, bd_addr=11:46:03:10:33:59, is_server=0
09-25 22:01:42.336 E/bt_rfcomm(30275): RFCOMM_RemoveConnection handle:21, port state 0
09-25 22:01:42.336 E/bt_btif_sock_rfcomm(30275): find_rfc_slot_by_id unable to find RFCOMM slot id: 5
09-25 22:01:42.337 E/GH.WPP.IO(22134): Failed to read 4 bytes at offset 0
09-25 22:01:42.337 E/GH.WPP.IO(22134): java.io.IOException: bt socket closed, read return: -1
09-25 22:01:42.337 E/GH.WPP.IO(22134): 	at android.bluetooth.BluetoothSocket.read(BluetoothSocket.java:678)
```

### R2 (candidate), from SSL (`22:10:42.770`)

```
09-25 22:10:42.142 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 1696, detail OptionalInt.empty, WIRELESS_WIFI_PROJECTION_PROTOCOL_RFCOMM_SOCKET_CONNECTED, at 1460314
09-25 22:10:42.190 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 1697, detail OptionalInt.empty, WIRELESS_CONNECTED_RFCOMM, at 1460362
09-25 22:10:42.345 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 1702, detail OptionalInt.empty, WIRELESS_WIFI_PROJECTION_PROTOCOL_RFCOMM_VERSION_REQUEST_RECEIVED, at 1460722
09-25 22:10:42.345 I/GH.StartupMeasure(26295): logEvent GEARHEAD_WIRELESS_VERSION_REQUEST_RECEIVED_RFCOMM=PT24M20.722S
09-25 22:10:42.381 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 1703, detail OptionalInt.empty, WIRELESS_WPP_VERSION_RESPONSE_NO_COMPATIBLE_CHANNELS, at 1460781
09-25 22:10:42.433 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 1704, detail OptionalInt.empty, WIRELESS_RFCOMM_VERSION_CHECK_COMPLETE, at 1460798
09-25 22:10:42.463 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 1705, detail OptionalInt.empty, WIRELESS_WIFI_PROJECTION_PROTOCOL_RFCOMM_START_REQUEST_RECEIVED, at 1460892
09-25 22:10:42.464 I/GH.StartupMeasure(26295): logEvent GEARHEAD_WIRELESS_START_REQUEST_RECEIVED_RFCOMM=PT24M20.892S
09-25 22:10:42.527 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 1707, detail OptionalInt.empty, WIRELESS_WIFI_PROJECTION_PROTOCOL_RFCOMM_INFO_REQUEST_SENT, at 1460929
09-25 22:10:42.543 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 1708, detail OptionalInt.empty, WIRELESS_WIFI_PROJECTION_PROTOCOL_RFCOMM_START_RESPONSE_SENT, at 1460931
09-25 22:10:42.562 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: selectEventStream: use RFCOMM, %s
09-25 22:10:42.705 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: shouldConnectRfcomm: isInAllowedList=%s, isRingingSupported=%s, hasFastPairItem=%s, isWearOsDevice=%s, uuidExist=%s, sdk=%s
09-25 22:10:42.705 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: connectEventStreamForRfcomm: skip connection, RFCOMM unsupported. %s
09-25 22:10:42.805 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: selectEventStream: use RFCOMM, %s
09-25 22:10:42.842 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: shouldConnectRfcomm: isInAllowedList=%s, isRingingSupported=%s, hasFastPairItem=%s, isWearOsDevice=%s, uuidExist=%s, sdk=%s
09-25 22:10:42.842 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: connectEventStreamForRfcomm: skip connection, RFCOMM unsupported. %s
09-25 22:10:43.067 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 1718, detail OptionalInt.empty, WIRELESS_WIFI_PROJECTION_PROTOCOL_RFCOMM_INFO_RESPONSE_RECEIVED, at 1462028
09-25 22:10:43.078 I/GH.ConnLoggerV2(26295): Session f4101494-54b6-4965-8c94-5a2094b7e653, event 1719, detail OptionalInt.empty, WIRELESS_RFCOMM_INFO_RESPONSE_RECEIVED, at 1462031
09-25 22:10:44.338 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: selectEventStream: use RFCOMM, %s
09-25 22:10:44.384 I/NearbyDiscovery( 2960): (REDACTED) FastPairController: shouldConnectRfcomm: isInAllowedList=%s, isRingingSupported=%s, hasFastPairItem=%s, isWearOsDevice=%s, uuidExist=%s, sdk=%s
```

Captures: `hold-aa-rfcomm-round1-captures.zip` (includes a `discarded/` subdirectory with the two contaminated attempts) on release `rig-evidence-hold-aa-rfcomm`, sha256 `6261b2f852ad75c4586d432c2f8dc87aa4fa1078b4b7e7c65d1ce77d13b398a8`.
