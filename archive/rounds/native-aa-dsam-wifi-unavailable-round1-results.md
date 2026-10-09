# native-aa-dsam-wifi-unavailable — round 1 results

**Candidate:** app installed on D-SAM, versionName `3.5.0-alpha` versionCode `113`, `lastUpdateTime`
2026-09-24 10:51:25 (installed same day as capture; exact build SHA not confirmed, presumed at or
near `main` @ `d9e51925` — `7cd816b7e`, the commit adding `NativeHandoffPolicy.isHandoffSettling()`,
is an ancestor of that tip).
**Baseline:** none, not a candidate-vs-baseline round.
**Unit:** D-SAM (Samsung SM-T230, Android 4.4.2 / API 19, 2.4GHz-only WiFi Direct band preference)
as head unit; D-POCO (POCO X3 NFC, Android 15, Gearhead `17.5.663204`) as phone.
**Date:** 2026-09-24

## Setup notes

**Not a scripted round.** This is an unscripted, coding-session-driven live capture, not a run
against a queued brief: the user reported D-SAM taking a long time (and sometimes failing outright)
to connect over Native AA, cycling through new WiFi Direct sessions before finally landing. Two
back-to-back attempts were captured live via `adb logcat` while the user reproduced the connect on
the rig; no settings were changed, no brief was followed, and there are no PASS/FAIL verdicts to
assign — this file exists to hand the capture and its reading to the coding agent.

Capture commands, D-SAM: `stdbuf -oL adb logcat -v threadtime "OPENHU:V" "WifiP2pService:V"
"WifiP2pManager:V" "wpa_supplicant:V" "WifiP2pStateMachine:V" "*:S"`.
Capture commands, D-POCO round 2 only (round 1's D-POCO capture came back empty — its WiFi-Direct
system tags didn't match the filter used): one stream `adb logcat -v threadtime --uid=10193`
(Gearhead's uid on this device), one stream `adb logcat -v threadtime "WifiP2pService:V"
"WifiP2pManager:V" "wpa_supplicant:V" "SupplicantP2pIfaceHalV1:V" "ConnectivityService:V"
"IpClient:V" "NetworkMonitor:V" "*:S"`.

**Evidence:** `rig-evidence-native-aa-dsam-wifi-unavailable`, asset
`native-aa-dsam-wifi-unavailable-round1-captures.zip`,
sha256 `afb6e9e13d6144e9a280ff1548dda42168031f34847787f39cbe5c5df1b422d4`. Contains, unfiltered:
- `dsam_20260924_135540.logcat` — D-SAM, run 1 (4 group-create cycles, connected on the 4th)
- `dpoco_20260924_135540.logcat` — D-POCO, run 1 (empty, filter miss, kept for reference)
- `dsam_20260924_140729.logcat` — D-SAM, run 2 (2 group-create cycles, connected on the 2nd)
- `dpoco_gearhead_20260924_140729.logcat` — D-POCO, run 2, Gearhead app only (uid 10193)
- `dpoco_wifisystem_20260924_140729.logcat` — D-POCO, run 2, system WiFi/P2P/connectivity stack

## Finding — the reconnect loop is the phone reporting a genuine join failure, not an app-side race

Both runs show the identical pattern: D-SAM completes group creation, poke, BT accept and the full
Type1→Type2→Type3 handshake cleanly on **every** cycle (status pill walks
`WAKING_PHONE → PHONE_ANSWERED → SENDING_CREDENTIALS → PHONE_JOINING` each time, no skipped step).
The retry is triggered **after** that, by the phone's own Type 6 `WifiConnectStatus` reply:

Run 1 (`dsam_20260924_135540.logcat`):
```
13:55:53.260  NativeAA: [RX] Received Type 6 (Payload size: 11)
13:55:53.270  WifiConnectStatus status=WIFI_NETWORK_UNAVAILABLE(-11)
13:55:53.270  Handshake failed — phone reported join failure (type 6, status=WIFI_NETWORK_UNAVAILABLE(-11))
13:55:53.280  Handshake stage SETTLING -> FAILED
13:55:53.280  the phone has refused this network 1 times in a row, so the next wake waits 14996ms.
```
Repeats at `13:56:58.774` (2nd failure, run 1). Only the 4th group's handshake gets
`Type 6 status=SUCCESS(0)` at `13:57:21.406`, immediately followed by
`WirelessServer: Incoming connection detected from /192.168.49.153` at `13:57:21.316`.

Run 2 (`dsam_20260924_140729.logcat`) is the same shape, one failure then success:
```
14:07:57.597  Received Type 6 (Payload size: 11)
14:07:57.597  WifiConnectStatus status=WIFI_NETWORK_UNAVAILABLE(-11)
14:08:20.199  Received Type 6 (Payload size: 2)
14:08:20.209  WifiConnectStatus status=SUCCESS(0)
14:08:22.491  WirelessServer: Incoming connection detected from /192.168.49.222
```

Every group-create is a legitimate reaction (`refresh: no group is up, so one is created`) to the
phone's own reported failure, gated by the app's per-refusal backoff (`next wake waits ~15s`), not a
teardown race. The `isHandoffSettling()` 45s guard (`NativeHandoffPolicy`, landed `7cd816b7e`) is
present and was never even challenged — nothing here recreates the group inside that window.

`WIFI_NETWORK_UNAVAILABLE` is a phone-side (POCO X3 / Android 15) connectivity-stack failure code,
returned when the phone's own network request against the P2P group times out or is abandoned. It is
not a credentials, protocol or app-side-teardown problem on D-SAM. D-SAM is 2.4GHz-only by user
setting (`Band preference is 2.4 GHz only`, channel 6 / 2437 MHz) and is an old (API 19) unit.

**This matches the `link-stall-periodic-scan` thread's already-caught mechanism**: that thread's row
notes a background scan deauthing the phone off a live WiFi Direct group on D-POCO
(`audio-sink-jitter-round4-results.md`, `locally_generated=1 reason=3`). This capture did not itself
catch a deauth reason code on the phone side (D-POCO's own WiFi/P2P system log for run 1 is empty —
filter miss — and run 2's phone-side system log, `dpoco_wifisystem_20260924_140729.logcat`, is only
185 lines and does not show a deauth around either `-11` timestamp; it may not have been capturing
early enough or the phone's WiFi stack tags differ from what was guessed). It is offered as
corroborating field evidence, not as new proof of the mechanism, and it is left for the coding agent
to correlate against `link-stall-periodic-scan-round4-brief.md`'s 2.4GHz-plus-constrained-memory
conditions, which this unit happens to match.

## Anything the brief did not ask about

- The connect time itself (`ARMED` to a live session) was ~105s in run 1 (4 cycles) and ~72s in
  run 2 (2 cycles) — both well above a first-try connect, which the same rig has shown land in
  single digits of seconds elsewhere in this branch's history.
- D-POCO's Gearhead uid (10193) log for run 2 was captured in full
  (`dpoco_gearhead_20260924_140729.logcat`, 3036 lines) but not read closely here; it may carry
  Gearhead's own account of the join failure and is left for the coding agent.
