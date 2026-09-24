# hotspot-endpoint-poison — round 1 results

**Candidate:** `fix/hotspot-endpoint-poison` @ `018d5af67a35`       **Baseline:** `main` @ `dd454eed1`
**APK md5:** `4e2c6a6c4dce5e678e7fbcb98b4161da` (candidate) / `2a8f47b33ab6d16aae621374810540b7` (baseline)
**APK sha256:** `582204b1513d5cbe31c99eb6369a2f11a89833952bdb4aa0b71c33bc505929b3` (candidate) / `b93d9613cf900daf1c991f1bca54a1fd37886ae324f9d864ab7bb782d01281c9` (baseline)
**Unit:** UNISOC MT50 (D-HU, rooted), Android 14, D-MOTO (Motorola edge 30 neo) as phone, both cabled
**Date:** 2026-09-23

## Setup notes

- Inventoried `hur-wifi-test-scripts/`; used `build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh`
  as-is. No new script needed.
- **D-HU's `wlan2` AP consistently comes up degraded the first time after every `adb reboot`
  observed this round** (R1 point 3, P1's own reboot, R3's reboot): `cmd wifi start-softap` prints
  a trailing `Soft AP failed to start. Please check config parameters` even though the interface
  reports a valid IP, and joins fail against it. One manual `stop-softap`/`start-softap` cycle
  clears it every time, address unchanged. Not previously in `TESTING-TEMPLATE.md` §7a — worth
  adding for the next round that reboots this unit.
- **The same underlying flakiness appears to reach the app's own programmatic hotspot-enable path.**
  In R5 and R8 step 2, `HotspotManager.setHotspotEnabled`'s own attempts (both attempt 1 and
  attempt 2) failed to bring up a usable AP, logging "Every start path was tried on 5 GHz and no
  access point came up within 6s each... either the radio was busy or this unit refuses it" — even
  though our manual `cmd wifi start-softap` reliably succeeds on the same hardware seconds later
  (via a stop/start cycle). Whether this is a gap in the candidate's retry logic (it doesn't cycle
  the radio the way our manual recovery does) or a rig-specific radio-settle quirk outside the
  app's control is unresolved from this round alone.
- D-HU's station WiFi did not automatically rejoin its home network ("Pegue Cdesta") after the
  R1/P1 reboot despite `svc wifi disable`/`enable` nudges and several minutes of waiting. Not
  required for any run's grading (only the `wlan2` AP interface mattered), so not chased further;
  flagged as a possible new rig quirk.
- **Background `adb logcat` capture processes died silently and repeatedly** when backgrounded
  across tool-call boundaries during R5 and part of R8. Evidence for the affected stretches is the
  quoted decisive lines below, pulled from `logcat -d` ring-buffer dumps (buffer raised to 16M
  first via `logcat -G 16M`) rather than a persisted capture file. P1, R2 and R3 have full capture
  files.
- `cmd wifi get-softap-config` (brief's §4 R5/R6 instruction) is not a valid subcommand on this
  build. The persisted default hotspot config was read instead from
  `/data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml` as root: SSID `Navegadortz2`,
  password `12345678`.
- **R5's first attempt produced no usable evidence**: the app was left in Android's "stopped" state
  (`am force-stop` was run right before the reboot to write settings), which suppresses
  `BOOT_COMPLETED` delivery entirely — confirmed via `dumpsys package ... stopped=true`, and via
  `dumpsys activity broadcasts history` showing no dispatch record for `BootCompleteReceiver` at
  all. This is Android OS policy, not a candidate defect. Redone by launching the app once and
  stopping it with `ACTION_STOP_SERVICE` (not `force-stop`) before the reboot, confirmed
  `stopped=false` immediately before rebooting; `BootCompleteReceiver` then fired correctly both
  times it was tried this way.
- R6, and the WiFi-Direct/hotspot re-arms around R7/R8, used a manual force-stop-then-relaunch
  cycle rather than a fresh reboot. R5's own investigation established that
  `SoftApCredentialsProvider`'s auto-enable logic behaves identically whether triggered by
  `BootCompleteReceiver` or a manual re-arm — confirmed by matching attempt-timing in both
  (`attempt 1` at ~5s, `attempt 2` at ≥10s later in every observed case).
- **Off-brief, operator-directed observation** (not part of this brief's own scope): the
  `connection_issue_banner_hands_free_held` string (`strings.xml:1096`) is long (~350 characters,
  three sentences) and was seen on-device as visually oversized. Its underlying check
  (`BluetoothWakePolicy.foreignHandsFreeLink`) reads `TargetLink.of(connected)` for the *specific*
  poke target's own `BluetoothDevice` state, and clears rather than raises when the target itself
  is the connected device — so the single-poke case is not confusing "any device" with "the polled
  phone." The operator separately observed the banner appearing to persist once the actually
  relevant phone (D-MOTO) was connected, in a wake cycle where a *different* bonded phone (D-POCO)
  had just failed its own poke (`native-poke-all-paired=true` walks every bonded phone in turn).
  This is a plausible but **unverified** mechanism — an earlier poke target's raised state not
  being re-evaluated once a later target's poke succeeds — and was not confirmed against this
  round's own logs; flagging it rather than asserting it.

## R0 — Build gate

**PASS**

- SHA `018d5af67a35` (3 commits on `main` `dd454eed1`), matches brief.
- JVM gate: 2336 tests, 0 failures, 0 errors (`app/build/test-results/testGithubDebugUnitTest/*.xml`).
- DEX/source carries `SoftApEndpointStabilityPolicy` (grepped, and reachable from
  `WifiLauncherNative`, `WppEndpointPolicy`, `NativeAaHandshakeManager`).
- `adb install -r`, md5-verified against the built APK.
- `ACTION_QUERY_STATE`: `"commit":"018d5af67a35"`, `"ok":true`.

## R1 — Does D-HU's tethering address move? (measurement)

No PASS/FAIL, per brief.

| Point | `boot_count` | AP address | BSSID |
|---|---|---|---|
| 1. `start-softap` (cold) | 212 | `192.168.230.83` | `00:27:15:43:06:6A` |
| 2. `stop-softap`, wait 5s, `start-softap` (same boot) | 212 | `192.168.230.83` (unchanged) | `00:27:15:43:06:6A` (unchanged) |
| 3. `adb reboot`, `start-softap` | 213 | `192.168.67.11` (moved) | `00:27:15:43:06:6A` (unchanged) |
| 4. `stop-softap`, `start-softap` (same boot) | 213 | `192.168.67.11` (unchanged) | `00:27:15:43:06:6A` (unchanged) |

D-HU's tethering address is stable within one boot and changes on every reboot; the BSSID never
moves. Confirmed again on two later reboots this round (P1's own reboot: `192.168.67.11` →
`192.168.48.57`; R3's reboot: `192.168.48.57` → `192.168.127.139`) — 3-for-3 address changes across
3 reboots this session. R3 and P1 graded on the "moved" branch as a result.

## P1 — Positive control on the baseline

**Reproduced.**

- Bring-up 1 (baseline, first sighting at `192.168.67.11`): `stable=unproven`, not advertised
  (baseline's own wording: "not yet seen to repeat").
- Bring-up 2 (same boot): `stable=yes`, `NativeAA: advertising WPP over TCP at 192.168.67.11:5299`
  at `15:20:39.224`, `SSL handshake complete` at `15:20:40.301`.
- `adb reboot` (`boot_count` 213→214), new AP address `192.168.48.57`, one bring-up, watched 3+ min:
  the phone repeated `GH.WPP.TCP: Trying to start WPP on TCP with configuration: ...
  ipAddress=192.168.67.11, port=5299` (the stale address) from `15:20:40` onward, cycling
  `WIRELESS_WIFI_CONNECTED_TO_WRONG_SSID` / `WIRELESS_WIFI_SCAN_RESULTS_NETWORK_NOT_FOUND` and
  `Triggering WPP restart. Reason=NETWORK_UNAVAILABLE_NETWORK_NOT_FOUND` roughly every 20-40s. No
  `SSL handshake complete` anywhere in the capture from `15:22:25` (the reboot bring-up) through
  `15:25:33` (188s later). Hand-forget on D-MOTO then performed before installing the candidate.

## R2 — Same boot: no endpoint on an address not yet seen across a restart

**PASS**

- Bring-up 1: `stable=unproven`, `"first reading of the access point's address at 192.168.48.57;
  it has to come back after a restart"` at `15:36:33.296`; `not advertising WPP over TCP` at
  `15:36:34.606`; `WirelessServer: Incoming connection detected from /192.168.48.125` at
  `15:36:35.665`; `SSL handshake complete` at `15:36:35.866`.
- Bring-up 2: `stable=unproven`, `"same address 192.168.48.57, but not yet seen across a restart,
  which is when tethering picks a new one"` at `15:38:58.495`; `not advertising WPP over TCP` at
  `15:39:03.826`; `SSL handshake complete` at `15:39:05.081`.
- Phone: `No WPP on TCP configuration found in storage for the head unit` logged repeatedly across
  both bring-ups (confirms the P1 hand-forget held).
- Setup note: D-HU's `wlan2` AP was still carrying the post-reboot degraded state from P1's own
  reboot when R2's first bring-up attempt was made; that attempt recorded `stable=unproven` locally
  but never reached `SSL handshake complete` in 3+ minutes. AP cycled once (address unchanged,
  `192.168.48.57`), `soft-ap-*` keys re-cleared, and R2 rerun cleanly from scratch — the PASS above
  is from that clean rerun.

## R3 — Across a reboot (the point of the round)

**PASS** (took the "moved" branch)

- `adb reboot` (214→215), one AP cycle needed again (same degraded-first-start pattern, address
  unchanged at `192.168.127.139` before/after the cycle).
- Bring-up 1: `stable=no`, `"the access point came back but its address moved from 192.168.48.57 to
  192.168.127.139, and the phone would keep the old one"` at `15:40:46.537`; `not advertising WPP
  over TCP` at `15:40:47.598`; phone joined fresh via the ordinary per-session credential exchange
  (`WirelessServer: Incoming connection detected from /192.168.127.125` at `15:40:57.402`);
  `SSL handshake complete` at `15:40:57.646` — no hand-forget needed (nothing was ever advertised
  on this candidate run to get stuck on).
- Bring-up 2 (no reboot): `stable=unproven`, `"same address 192.168.127.139, but not yet seen
  across a restart"` at `15:41:26.914`; `not advertising WPP over TCP` at `15:41:29.799`;
  `SSL handshake complete` at `15:41:31.124`.
- No `advertising WPP over TCP` on any bring-up whose `ip=` differed from the one before it
  (discard condition never hit).

## R4 — A moved password raises the banner

**UNTESTABLE**, per the brief's own §3 fallback: R3 took the "moved" branch, so nothing was ever
advertised on the candidate and there is no advertised endpoint for a password change to poison.
Covered by `SoftApEndpointStabilityPolicyTest` per the brief.

## R5 — The app switches the hotspot on after a boot

**FAIL**

- Keys: `auto-enable-hotspot=true`, `auto-start-on-boot=true` (device-protected mirror confirmed
  `true` after one launch+stop cycle), `hotspot-ssid=Navegadortz2` / `hotspot-password=12345678`
  (the unit's persisted default tethering config, read from
  `WifiConfigStoreSoftAp.xml` — see Setup notes).
- Boot-launch mechanics: confirmed correct (see Setup notes on the "stopped" state pitfall).
- Attempt timing, from an isolated non-reboot retrigger of the identical
  `SoftApCredentialsProvider`/`SoftApAutoEnablePolicy` code path (used after repeated capture
  failures on the boot-triggered runs — see Setup notes):
  - `SoftApCredentials: No access point after 5s — trying to switch this device's hotspot on
    (attempt 1 of 2).` at `15:54:00.541`
  - `SoftApCredentials: The hotspot went down — the credentials the phone was given are no longer
    valid.` at `15:54:09.172`
  - `SoftApCredentials: No access point after 21s — trying to switch this device's hotspot on
    (attempt 2 of 2).` at `15:54:17.098` (16.6s after attempt 1, satisfying the ≥10s spacing)
  - `SoftApCredentials: the hotspot did not come up on attempt 2.` at `15:54:17.105`
- On the clean boot-triggered redo: `connection-issue-hotspot-off` was freshly raised this boot
  (`1790197052775`, was `0` beforehand), and `mCurrentSoftApInfoMap {}` / `wlan2` absent afterward.
- Attempt-count and timing are correct in both the boot-triggered and manually-retriggered case.
  **The AP itself never reached a usable state in either case** — the failure is downstream of the
  candidate's own retry logic, in the actual `HotspotManager.setHotspotEnabled` → system tethering
  call not producing a working AP on this rig (see Setup notes on the suspected radio-settle
  confound).

## R6 — Setting off: the log says so

**PASS**

- `auto-enable-hotspot=false`, relaunched (see Setup notes on why a reboot wasn't repeated here).
- No `(attempt` line anywhere in the capture.
- `SoftApCredentials: No usable access point after 30s. Turn this device's hotspot on before
  connecting — 5 GHz is strongly recommended, Android Auto video is poor over 2.4 GHz — or switch
  the Android Auto network transport back to WiFi Direct. 'Auto-Enable Hotspot' is off, so the app
  did not try to switch it on.` at `15:59:44.169` (30s after the resolve loop started).
- `ACTION_EXPORT_LOG`'s `LogExporter: session` header at `16:00:08.680` carried `autoHotspot:off`.

## R7 — WiFi Direct, no regression

**PASS**

- Reverted per §4: `native-ap-transport=0`, `hotspot-ssid`/`hotspot-password`/`hotspot-interface`
  cleared to `""`, `static-bssid` set to `"0"`; all four read back.
- `WifiDirectManager: group identity ssid=DIRECT-QS-MT50YT610E4GFPSLU persistent=yes (netId 0)
  asked=persistent matchesRequest=yes bssid=5A:BA:01:29:59:53 address=generated stable=no (same
  name but the BSSID moved from 8E:3F:C8:FA:DB:BD to 5A:BA:01:29:59:53; this unit re-addresses the
  group on every create) source=IPv6 link-local` at `16:04:42.669`.
- No `access point identity` line anywhere in the capture.
- `SSL handshake complete` at `16:04:51.818`.

## R8 — The car switched off and on (XYAuto broadcasts)

**FAIL** (step 2)

Setup: hotspot route re-armed (`OHU-HOTSPOT`/`ohutest12345`, `auto-enable-hotspot=true`), AP up
from the shell, one bring-up to a live session (`SSL handshake complete` at `16:00:53.372`).

**Step 1** (`xy.android.acc.off`, then `cmd wifi stop-softap` within 2s, watched 60s):
- `WakeDetect: ACC off (xy.android.acc.off)` at `16:01:11.356`.
- `AapService: ACC_POWER_LOST with a live session — closing it now, while the link still works.`
  (the "closing it now" teardown variant, not graded per brief) at `16:01:11.357`.
- Zero `(attempt` lines in the 60s window.
- `connection-issue-hotspot-off` unchanged at `0` before and after the step.
- The specific `SoftApCredentials: the car is off (xy.android.acc.off), not switching the hotspot
  on.` corroborating line did **not** appear — plausibly because no `SoftApCredentialsProvider`
  resolve loop was actively polling at that moment (credentials were already published for the
  live session before ACC-off), so the `accOff` check inside `beginResolve()`'s `NO_AP_YET` branch
  was never reached. The negative-control evidence itself (zero attempt lines, unchanged counter)
  stands regardless.

**Step 2** (`xy.android.acc.on`, watched 60s):
- `WakeDetect: xy.android.acc.on` at `16:02:45.395`.
- `SoftApCredentials: No access point after 123s — trying to switch this device's hotspot on
  (attempt 1 of 2).` at `16:02:46.338` (<2s after the ACC-on broadcast).
- `SoftApCredentials: No access point after 136s — trying to switch this device's hotspot on
  (attempt 2 of 2).` at `16:02:58.845`.
- `SoftApCredentials: the hotspot did not come up on attempt 2.` at `16:02:58.850`.
- **The AP never came up** (`wlan2` absent afterward) — same failure pattern as R5.
  `HotspotManager`'s own log: `"Every start path was tried on 5 GHz and no access point came up
  within 6s each. This app is allowed to ask, so either the radio was busy or this unit refuses
  it."` at `16:03:06.471`.

If the change had done nothing, step 1 would have printed an `(attempt` line straight after the AP
went down — it did not, satisfying that half of R8. Step 2's attempt-trigger timing is also
correct. The failure is specifically that no attempt this round — in R5 or R8 — ever produced a
usable AP.

## Anything the brief did not ask about

- The `connection_issue_banner_hands_free_held` string is long and was seen as visually oversized
  on-device; see Setup notes for the (partly verified, partly unconfirmed) detail on its
  per-target-device check and the suspected multi-phone-wake-cycle staleness issue.
- The same wlan2-AP-degraded-after-radio-change pattern that confounds R5/R8's auto-enable path
  also affected every manual `start-softap` immediately after an `adb reboot` this round (R1 point
  3, P1's reboot, R3's reboot) — a one-cycle recovery that is not documented in
  `TESTING-TEMPLATE.md` §7a yet.
- D-HU's station WiFi did not rejoin its home network after two reboots this round despite
  `svc wifi` nudges; unconfirmed whether this is new or was simply never exercised by a prior
  round's reboot.
