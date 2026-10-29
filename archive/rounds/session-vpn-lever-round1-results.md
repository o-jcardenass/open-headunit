# session-vpn-lever — round 1 results

**Candidate:** `fix/session-lifecycle-and-diagnostics` @ `6bd336ed`       **Baseline:** none (brief did not ask for an A/B)
**APK md5:** `a617311477e1a78328cb4aead55e6259`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14. Phone: POCO X3 NFC (`M2007J20CG`).
**Date:** 2026-08-20

## Setup notes

Scripts used: `build_hur.sh`, `run_unit_tests.sh` (R0), `set_hu_prefs.sh` (all settings writes),
`banner_watch.sh` (background, all live-session runs). No new script needed.

`settings.xml` diff against a fresh backup: only `keep-dummy-vpn-during-session` needed writing
(absent by default). `wifi-connection-mode=3`, `view-mode=1`, `enable-audio-sink=true`,
`debug-video-fault-injection=0`, `log-level=2` were already in place from a prior round's state —
zero-delta on those five, confirmed by reading the file before writing anything.

**Deviation 1 — `settings get global bluetooth_on`/`wifi_on` on the phone are not reliable.** R1's
first attempt launched with these reading "2" / "3" (on), but `dumpsys bluetooth_manager` /
`dumpsys wifi` showed both radios actually disabled. The head unit's P2P group formed and then
retried three times waiting for a phone that was never coming (3× `createGroup SUCCESS`, a genuine
discard-rule hit). Discarded, radios fixed via `dumpsys`-verified `svc bluetooth enable` /
`svc wifi enable`, redone clean. Capture kept as
`r1_capture_DISCARDED_no_phone_radios.txt` for the record; not used for any verdict below. Always
check the `dumpsys` state directly, not `settings get global`, on this phone.

**Deviation 2 — `headunit://connect` is a no-op for Native AA.** With no `ip` query param it maps to
`ACTION_CHECK_USB` (`AutomationActivity.kt:53-57`), which only scans for USB accessory devices —
irrelevant on this wireless-only rig and irrelevant to mode 3 generally. R2's prescribed
"disconnect, wait 30s, connect" second-connection step did nothing on `connect` for ~4 minutes.
Source-traced instead: a user-exit disconnect on mode 3 calls `nativeAaHandshakeManager?.stop()`
**and** `wifiDirectManager?.stop()` (`AapService.kt:1218-1246`), fully tearing down the P2P group;
the only path that re-arms it is `ACTION_BT_AUTO_START`, fired by `AutoStartReceiver` on the
phone's Bluetooth `ACL_CONNECTED` (`AapService.kt:2157-2181`) — the same mechanism that formed the
*first* session in R1. Substituted a phone-side Bluetooth off/on cycle for the brief's `connect`
deep link; it produced the re-arm and a full second session within 5 seconds. The brief's method is
wrong for this rig/mode and should be corrected for any future round on this thread.

**Deviation 3 — a real defect found investigating R2 condition 4, not something the brief
anticipated: `VpnControl.stopVpn()`'s `context.stopService()` does not actually destroy an
established `DummyVpnService`.** See "Anything the brief did not ask about" below; it is the reason
R2's 4th condition reads FAIL rather than PASS.

**Deviation 4 — R3's forced re-init cascaded further than the brief's own runbook implied**, leaving
the rig mid-reconnect when R4 needed to start. Force-stop + relaunch (needed anyway for R4's clean
state) cleared it; no extra time lost. See R3 below and the "Anything the brief did not ask about"
section.

## R0 — build and unit tests. Gate.

**PASS**

- `./gradlew assembleGithubDebug`: clean compile, `BUILD SUCCESSFUL`. The `onConnected()` merge
  conflict the brief flagged as unbuilt-on-paper compiles without error.
- 612 `@Test` annotations (matches the brief's stated count exactly).
- Named classes, all green, 0 failures/errors: `DummyVpnPolicyTest` **7/7**,
  `UsbSessionQuiescePolicyTest` **8/8**, `LinkGapMonitorTest` **17/17**,
  `StationCoexistencePolicyTest` **11/11**. Verified against the JUnit XML reports, not just the
  console summary.
- `./gradlew testGithubDebugUnitTest`: `BUILD SUCCESSFUL`.

## R1 — consent, and does the tun come up at all. Gate for R2 and R3.

**PASS** (after discarding one contaminated attempt — see Deviation 1)

- Settings written: `keep-dummy-vpn-during-session=true` (only key; rest already correct — see
  Setup notes). `appops set com.andrerinas.headunitrevived ACTIVATE_VPN allow`, verified with
  `appops get` → `allow`.
- Discard-rule check on the kept run: clean. 1× `createGroup SUCCESS` (5GHz);
  `p2p-wlan0-7`→`p2p-wlan0-8` bump is the stale-group-torn-down-at-launch pattern (the `-7` teardown
  precedes the `createGroup SUCCESS` line — benign per the refined rule), 1× SSL handshake, 0
  `Magic Garbage`.
- Decisive log lines (all 2026-08-20):
  ```
  17:17:01.334  WifiDirectManager: 5GHz createGroup SUCCESS!
  17:17:04.251  MATCH! Starting AapService via Bluetooth Auto-start...   (benign — phone's own BT
                                                                            reconnect, zero group churn attached)
  17:17:08.508  WirelessServer: Incoming connection detected from /192.168.49.28
  17:17:08.568  AapService: dummy VPN requested (owner=SESSION). While it is up, other apps on this unit have no IPv4.
  17:17:08.645  DummyVpnService: tun established (excludeSelf=true)
  17:17:08.925  SSL handshake complete. Session id: BnQlMv0d0hmpuQBYkNNYLbZgxcRMdLOHzxzzZ5/yhYU=
  ```
- No `prepared VPN app` WARN at any point — the appops route alone satisfied `VpnService.prepare()`;
  the UI-tap fallback in the brief's contingency was never needed.

## R2 — the VPN survives a whole session, and comes back on the second one.

**Mixed: conditions 1-3 PASS, condition 4 FAIL.**

- Settings: unchanged from R1.
- 10-minute hold, then disconnect, 30s wait, reconnect (via the Deviation-2 substitute), 3-minute
  hold on the second session.

1. **Zero** `VpnControl: Stopping DummyVpnService` between first `tun established` (17:17:08.645)
   and the disconnect (17:28:29.396): **PASS**, literal count 0.
   - **Reachability pairing**: `AapService: Initializing WiFi Mode: ` count *strictly inside* the
     10-minute window is **0** — the only occurrence in the whole R1 capture up to the disconnect
     (17:17:00.582) is the initial launch call, which precedes `tun established` by 8 seconds and is
     outside the window being held. Per the brief's own instruction: **this run's zero-stops proves
     nothing by itself** — R3 is what actually carries this half of the round (see below).
2. Exactly one `releasing the dummy VPN (owner=SESSION, reason=SESSION_ENDED)` at the disconnect:
   **PASS**. Fired once, 17:28:29.396, immediately paired with `VpnControl: Stopping
   DummyVpnService` on the next line.
3. `dummy VPN requested (owner=SESSION)` again on the second connection: **PASS**. Fired at
   17:35:03.261 (after the Deviation-2 re-arm), followed by `SSL handshake complete` at
   17:35:03.370.
4. `Dummy VPN stopped` count equals the number of teardowns, not twice it: **FAIL**. Actual count
   over the entire round (R1 through R4): **0**. One teardown had already been attempted by this
   point (the disconnect above); a second followed in R3. Root cause verified independently, not
   just inferred from the missing log line — see "Anything the brief did not ask about."
- `AapService: Initializing WiFi Mode: ` count inside the 10-minute window: **0** (see condition 1).
- `createGroup SUCCESS` count across R2: **2** (R1's original group, plus one from the Deviation-2
  re-arm for the second connection) — both are legitimate new groups following a genuine
  user-exit teardown, not the discard-rule churn pattern.
- Session survived the full 10 minutes: **yes**.

## R3 — force `initWifiMode` under a live VPN. The direct regression probe.

**PASS** on the brief's literal question, with an unscored side effect worth flagging.

- Fired `ACTION_START_WIRELESS_SCAN` against the live second session (tun up since 17:35:03) at
  17:38:29 (host clock) / logged 17:38:27.688 (device clock — this round's adb round-trip
  consistently landed device timestamps ~0.5-1.3s before the corresponding host echo throughout).
- `AapService: Initializing WiFi Mode: 3` appeared. It immediately (17:38:27.690) force-released
  port 5288 with a `SocketException` — a real side effect of the rebind, and what actually ended the
  live AAP session. `releasing the dummy VPN (owner=SESSION, reason=SESSION_ENDED)` and
  `VpnControl: Stopping DummyVpnService` both followed 116ms later (17:38:27.804), but strictly *as
  a consequence of* that genuine disconnect — not a direct, unconditional call from `initWifiMode()`
  itself. This matches the brief's own carve-out exactly: "a drop produces a legitimate `releasing
  the dummy VPN (… reason=SESSION_ENDED)`. Only a stop line *before* any disconnect is a FAIL." No
  stop line preceded a disconnect line anywhere in the capture.
- **Not asked for, observed regardless**: `AapService`'s own non-user-exit reconnect logic then
  fired a *second* `initWifiMode(force=true)` 1.5s later (its documented delay), producing two more
  `createGroup SUCCESS` events and leaving the rig completing three separate NativeAA Bluetooth-side
  handshakes (17:38:32, 17:39:15, 17:39:58) without ever finishing a full AAP/SSL reconnection by
  the time R4 needed to start (~2.5 minutes later, still no `AapService.onConnected`). Not scored —
  R3 only asked whether a stop line preceded a disconnect line, and it didn't — but worth a note for
  whoever next touches Native AA's forced-rescan path: `ACTION_START_WIRELESS_SCAN` mid-session is
  not obviously safe/idempotent on this rig beyond the VPN-specific question this round tested.

## R4 — control, toggle off.

**PASS**, all conditions.

- Settings: `keep-dummy-vpn-during-session=false`. App force-stopped and relaunched fresh (force-stop
  was also what finally cleared the still-live `tun0` from earlier in the round — see next section)
  before a clean 10-minute Native AA session, 17:41:31 (`createGroup SUCCESS`) through 17:52:12
  (hold end).
- **Zero** of every VPN line in §5: confirmed (0 across `dummy VPN requested` / `tun established` /
  `prepared VPN app` / `releasing the dummy VPN` / `Stopping DummyVpnService` / `DummyVpnService was
  not running` / `Dummy VPN stopped`).
- `WifiLock acquired (HIGH_PERF)`: present **exactly once**.
- `stopping the wireless stack for the duration of it`: **absent**.
- `createGroup SUCCESS`: **1**.
- Free riders: `connected to another WiFi network on` = **1**; `Audio sink is off in Settings` =
  **0**.
- Session survived the full 10 minutes: **yes**.

Free riders for R1's session (multi-group, per Deviations 1-4): `connected to another WiFi network
on` = **5** (across the 4 groups formed in that capture — roughly one per group, as expected);
`Audio sink is off in Settings` = **0**.

## Anything the brief did not ask about

**A real, verified defect: `VpnControl.stopVpn()` does not tear down the tun while the app process
stays alive, on this Android 14 device.** `startVpn()`/`stopVpn()` in
`VpnControl.kt` (`app/src/github/.../utils/VpnControl.kt:24-54`) use plain `context.startService()`
/ `context.stopService()` against `DummyVpnService`, on the documented assumption that
"`onDestroy()` closes the descriptor, so stopping the service is enough." On this rig that
assumption is false: once `Builder().establish()` succeeds, `system_server` binds to the
`VpnService` (confirmed via `dumpsys activity services` — `Client AppBindRecord{... 1090:system/1000}`,
`hasBound=true`), and a plain `stopService()` clears only the *started* flag; `onDestroy()` does not
run while that bind persists.

Measured: `tun0` (`ip link show` on the head unit) stayed `<POINTOPOINT,UP,LOWER_UP>` continuously
from R1's first `establish()` at 17:17:08 through at least 17:40 — across **two** app-level
teardown attempts (the R2 disconnect and the R3 forced-reinit disconnect) that each correctly
printed `releasing the dummy VPN (owner=SESSION, reason=SESSION_ENDED)` and `VpnControl: Stopping
DummyVpnService`, and correctly reset the app's own `dummyVpnOwner` bookkeeping (confirmed
indirectly: the third session's own `startDummyVpn()` never fired a *third* `dummy VPN requested` —
consistent with `dummyVpnOwner` having gone back to null, i.e. the app-level state machine is
correct). `DummyVpnService`'s `createTime` in `dumpsys activity services` never advanced past R1's
original value across either teardown, confirming it is the same unstopped service instance
throughout, not a stopped-then-restarted one that happened to skip logging. `DummyVpnService.startVpn()`'s
own idempotent guard (`if (vpnInterface != null && establishedExcludingSelf == excludeSelf) return`,
`DummyVpnService.kt:36`) is what silently absorbed the second `startVpn()` call in R2's step 3
without re-establishing or re-logging `tun established`.

Only killing the whole process — `force-stop`, used going into R4 — actually removed `tun0`.

Practical effect: the log lines this round was told to trust (`releasing the dummy VPN`, `Stopping
DummyVpnService`) are honest about the app's internal intent and bookkeeping, but do **not**
describe reality once the tun has ever come up in this app process's lifetime. The dummy VPN's own
stated cost — "other apps on this unit have no IPv4" — appears to be paid indefinitely after the
first session that brings it up, not just while a session is nominally active, until the app process
is killed outright. This is independent of the `keep-dummy-vpn-during-session` toggle this round
was testing: it applies equally to Self Mode's existing VPN teardown, since both paths share the
same `VpnControl.stopVpn()` call (`AapService.kt:1865`, the only caller). Self Mode's path itself
remains untestable on this rig per the brief's §3, but the shared code path means this defect is not
scoped to the new feature alone.

**This is not pre-existing — this branch's own commit introduced it, in good faith, fixing a
different real bug.** `git show ef0a383e -- .../utils/VpnControl.kt` (this branch's second VPN
commit) replaced `main`'s `context.startService(Intent(...).apply { action =
DummyVpnService.ACTION_STOP_VPN })` with the current `context.stopService(...)`. The commit message
explains why: the old `startService(ACTION_STOP_VPN)` call "threw on O+ during teardown and left the
tun up" — a genuine problem, since starting a non-foreground service from the background throws on
Android 8+, and a teardown often runs exactly as the app loses foreground. The replacement fixes
*that* failure mode but introduces this one: `stopService()` doesn't throw, but on this Android 14
device it also doesn't destroy the service while `system_server` stays bound to it, which is
unconditional for any `VpnService` that ever called `establish()`. Both the old and new code have a
real failure mode; neither reliably tears the tun down on this hardware. Worth fixing at the root —
e.g. sending `ACTION_STOP_VPN` via `startForegroundService`/a bound call rather than a fire-and-hope
`startService`, or having the service call its own `stopVpn()` from inside `onStartCommand` before
`stopService()` is even needed — rather than reverting to the pre-`ef0a383e` behavior.
