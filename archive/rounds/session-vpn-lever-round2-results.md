# session-vpn-lever — round 2 results

**Candidate:** `session-vpn-lever` @ `82814ec0` (== `fork/fix/session-lifecycle-and-diagnostics`)
**Baseline:** none — no A/B for this round
**APK md5:** `750d399053b74005c1835eb7a4312e04`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, phone POCO X3 NFC (serial `4f4027e9`)
**Date:** 2026-08-20

## Setup notes

- Inventory of `hur-wifi-test-scripts/`: used `build_hur.sh`, `run_unit_tests.sh`,
  `install_and_launch.sh` (`SKIP_BUILD=1`), and `set_hu_prefs.sh` (multi-key, single relaunch) — all
  fit, nothing new was added.
- Settings delta against the fresh pre-round backup: only `keep-dummy-vpn-during-session` needed
  writing. The other four keys the brief lists (`log-level=2`, `wifi-connection-mode=3`,
  `view-mode=1`, `enable-audio-sink=true`, `debug-video-fault-injection=0`) were already at the
  required values from the prior session on this rig. No round-1 settings backup was found on this
  machine to diff against directly; the pre-round backup taken fresh at the start of this round
  (`round-session-vpn-lever/settings-backup-r2.xml`) served as the baseline instead and was restored
  at the end.
- `adb shell cmd connectivity airplane-mode enable` did **not** bring the phone's Bluetooth/WiFi
  down on this POCO X3 (both still reported enabled via `dumpsys` afterward) — matches the
  template's existing note about airplane mode being unreliable on this unit. Used
  `svc bluetooth disable` / `svc wifi disable` directly instead, verified with `dumpsys` per the
  corrected instruction in §"Radios and state". Restored with `airplane-mode disable` at the end,
  which did bring both back up cleanly this time.
- Capture: single `stdbuf -oL adb logcat -v time` spanning the whole round (`r1_capture.txt`,
  R1 through R3), stopped by pid at the end; last capture line (18:29:51.710) is within seconds of
  the kill wall-clock (18:29:55), confirming the flush.
- Two benign `MATCH! Starting AapService` lines appeared during R1's phone-radio-on step and one
  more during R3's Bluetooth re-arm (3 total for the round) — each is the phone's own BT reconnect,
  not a poke, and none was followed by a second `createGroup SUCCESS` in the same session. This
  matches the signature the `native-aa-5288` thread already characterized as benign; §4's literal
  discard rule was not applied here because the round's own discard criterion (§6 R1, R3: "more
  than 1 `createGroup SUCCESS` per genuine user exit") is the one the brief actually asks to track,
  and that stayed clean throughout (exactly 2 for the round: 1 per session, 2 sessions).
- APK build and unit-test runs went through the rig's scripts with no deviation.

## R0 — build and unit tests. Gate.

**PASS**

- `assembleGithubDebug` compiled clean via `build_hur.sh`.
- `testGithubDebugUnitTest` green via `run_unit_tests.sh`.
- **612/612** `@Test` total (matches the brief's expected count, unchanged from round 1).
- `DummyVpnPolicyTest`: **7/7**.

## R1 — consent, and the tun comes up. Gate for R2 and R3.

**PASS**

- Settings written: `log-level=2`, `wifi-connection-mode=3`, `view-mode=1`,
  `enable-audio-sink=true`, `debug-video-fault-injection=0`, `keep-dummy-vpn-during-session=true`.
- Radio state: phone BT/WiFi forced off via `svc ... disable`, verified via `dumpsys`; brought back
  on via `svc ... enable` after the app settled ~18s.
- `appops get ... ACTIVATE_VPN` → `allow`. No `prepared VPN app` WARN in the capture — consent was
  never the blocker.
- Discard-rule check: 1 `createGroup SUCCESS` in this run, clean.
- Decisive log lines:
  - `18:19:26.571 AapService: dummy VPN requested (owner=SESSION). While it is up, other apps on
    this unit have no IPv4.`
  - `18:19:26.624 DummyVpnService: tun established (excludeSelf=true)`
  - `18:19:26.513 WirelessServer: Incoming connection detected from /192.168.49.24`
  - `18:19:26.804 SSL handshake complete.`
- `ip link show tun0`: `<POINTOPOINT,UP,LOWER_UP>`.
- `pidof $PKG` = **28321**. Recorded as `PID` and unchanged for the rest of the round.

## R2 — the teardown actually takes the tun down. **The point of the round.**

**PASS**, all four conditions:

- Settings: unchanged from R1 (live session).
- Held the session 3 minutes, then `headunit://disconnect`.
- Before: `ip link show tun0` UP, `pidof` = 28321.
- After (10s later): `ip link show tun0` → `Device "tun0" does not exist.` **This is the whole
  round.** `pidof` = **28321**, unchanged — the process stayed alive across the teardown, so
  condition 1 actually proves something.
- Exactly **1** `Dummy VPN stopped` in the capture at this point
  (`18:24:39.812 DummyVpnService.stopVpn | Dummy VPN stopped`) — round 1 had zero here.
- `dumpsys activity services $PKG | grep -B2 -A8 DummyVpnService` returned **no output** — no
  `ServiceRecord` left for `DummyVpnService`.
- Corroboration: `DummyVpnService was not running` = 0 (expected). `releasing the dummy VPN
  (owner=SESSION, reason=SESSION_ENDED)` = exactly 1
  (`18:24:39.735`), immediately followed by `VpnControl: Stopping DummyVpnService (GitHub Build)`
  at `18:24:39.736` and the stop completing 76ms later.

Round 1's defect (`tun0` surviving an apparently-successful teardown with the pid unchanged) does
not reproduce. `82814ec0`'s direct-descriptor-close fix works.

## R3 — it comes back, and it goes down again.

**PASS**, all four conditions:

- Re-armed with a phone-side Bluetooth off/on cycle (`svc bluetooth disable`, 5s, `svc bluetooth
  enable`), not `headunit://connect` — per the corrected instruction.
- `18:25:21.837 MATCH! Starting AapService via Bluetooth Auto-start...` — the phone came back.
- `18:25:22.537 WifiDirectManager: 5GHz createGroup SUCCESS!` — a **second**, genuine group (new
  P2P interface index, 83 vs R1's 81).
- `18:25:34.149 AapService: dummy VPN requested (owner=SESSION)` — a **second** request.
- `18:25:34.187 DummyVpnService: tun established (excludeSelf=true)` — a **second** establish. This
  is the condition round 1 could not prove: a genuine re-establish, not `startVpn()`'s idempotence
  guard silently absorbing the call over a tun that never actually went down. `ip link show tun0`
  confirmed UP again.
- Held 3 minutes, then a second scripted `headunit://disconnect`.
- After (10s later): `ip link show tun0` → `Device "tun0" does not exist.` again. `pidof` =
  **28321**, still unchanged from R1's `PID` across both teardowns and the re-arm in between.
- `Dummy VPN stopped` count for the whole capture: exactly **2**
  (`18:24:39.812` and `18:29:14.523`) — one per teardown, not four and not zero.
- `createGroup SUCCESS` across the whole round: **2** total (`18:18:51.656`, `18:25:22.537`) — one
  per genuine user exit/re-arm, matching the brief's expectation exactly. No churn without a
  preceding teardown.
- `consent was revoked`: 0, as expected (`onRevoke()` is out of scope for this round, per §3).

Second session behaved identically to the first in every measured respect; nothing degraded across
the cycle.

## Anything the brief did not ask about

Nothing new. The `MATCH! Starting AapService` occurrences (3 total across the round) are worth a
one-line mention above because the brief's own §4 discard rule would flag them naively, but the
round's actual criterion (unexplained `createGroup SUCCESS` churn) stayed clean — consistent with
the `native-aa-5288` thread's existing finding that a `MATCH!` from the phone's own BT reconnect,
with no accompanying group churn, is benign.

## Bottom line, per the brief's §8

1. **R0**: compiles. **PASS.**
2. **R2.1 paired with R2.2**: `tun0` gone, pid unchanged (28321 before and after). **PASS** — the
   process was alive when the interface went down, so this actually proves the fix.
3. **R3.2**: a second `tun established` line, proving a genuine re-establish rather than the
   idempotence guard hiding a tun that never came down. **PASS.**

`Dummy VPN stopped` counted exactly 2 at the end, corroborating both teardowns.

**Recommendation: ships.** All three deciding conditions passed, `onRevoke()` remains explicitly
out of scope for hardware testing (routed to code review per the brief), and the toggle-off control
was correctly not re-run (round 1's clean result there stands, untouched by this commit).
