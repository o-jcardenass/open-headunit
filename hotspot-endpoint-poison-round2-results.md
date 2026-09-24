# hotspot-endpoint-poison — round 2 results

**Candidate:** `fix/hotspot-endpoint-poison` @ `30a1f6d8da21`       **Baseline:** none (per brief, P1 already reproduced the bug in round 1)
**APK md5:** `cb786a5875815122a8755dda99422f47`
**APK sha256:** `aeb9966b0c40a7bed36e343f4aa4b304c7feee4c7c2670118980aa65eec7eaa0`
**Unit:** UNISOC MT50 (D-HU, rooted), Android 14, D-MOTO (Motorola edge 30 neo) as phone, both cabled
**Date:** 2026-09-23

## Setup notes

- Inventoried `hur-wifi-test-scripts/`; used `build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh`
  as-is. No new script needed for the ordinary bring-ups; `forget_car_gearhead.sh` (written by an
  earlier thread) was reused to clear D-MOTO's Android Auto vehicle list, see below.
- **D-HU's reboot for R3 had to be done by the operator by hand.** The harness's own permission
  classifier refused an `adb reboot` issued from this session ("Interfere With Workloads"), even
  though the brief and `TESTING-TEMPLATE.md` both treat `adb reboot` on this rig as routine. Waited
  on `adb wait-for-disconnect` / `wait-for-device` around the operator's manual reboot; `boot_count`
  went 219→220 confirming a genuine fresh boot.
- **D-MOTO carried a stale WPP-over-TCP record from well before this round started**, pointing at
  `192.168.49.1:5299` — an address neither this round's hotspot nor any WiFi Direct group in this
  session ever used. It blocked R2's second bring-up: Gearhead retried that dead address on a
  throttled backoff and never attempted a fresh Bluetooth handshake with the head unit. Neither
  Android Auto's own "Forget" (Vehicles list, via `forget_car_gearhead.sh`) nor forgetting the saved
  `OHU-HOTSPOT` WiFi network (`cmd wifi forget-network`) cleared it — both were tried, in that order,
  and the phone kept retrying the same dead address afterward with a session id that survived both.
  Only `adb shell pm clear com.google.android.projection.gearhead` (operator-approved, since it
  wipes Android Auto's app data on the phone) cleared it; a fresh device-lifetime id appeared on the
  next bring-up and the phone connected normally. This predates the candidate under test — round 1's
  own P1/R2 handoff already carried a similar risk (its own text: "if a connection will not start,
  forget this head unit on the phone") — but the operational finding that the in-app "Forget" control
  did **not** fully clear it, while `pm clear` did, is new and worth carrying into the next thread
  that uses D-MOTO.
- **D-MOTO's Bluetooth profile link had to be forced back up between bring-ups.** After a session
  ends, D-MOTO's BT ACL to D-HU drops and does not reconnect on its own (matches
  `project_mt50_hfp_link_needs_hu_bt_toggle` from an earlier thread); `svc bluetooth disable` /
  `enable` on the **head unit** (not the phone) restored the A2DP/AVRCP link within ~15s each time it
  was needed.
- **An operator settings change during troubleshooting caused a genuine, if cosmetic, mix-up in
  R3.** While helping unlock D-MOTO, the operator set the app's own `hotspot-ssid`/`hotspot-password`
  (Settings screen, not `settings.xml`) to `Navegadortz2`/`12345678` — the head unit's persisted
  system default, which the operator also confirmed changing via Android's own Tethering settings.
  This left the app quoting `ssid=Navegadortz2` in its identity log for a bring-up that was actually
  running against the shell-started `OHU-HOTSPOT` AP. The **stability grading itself was unaffected**
  (it keys off IP/BSSID/password digest, not the printed ssid text, and correctly read "address moved
  from 192.168.41.88 to 192.168.170.242" throughout), but the round redid R3's bring-ups after
  restoring `hotspot-ssid`/`hotspot-password` to `OHU-HOTSPOT`/`ohutest12345` to keep the reported
  log lines legible. One intermediate bring-up (not counted as one of R3's two) showed `stable=no`
  for "its password changed" rather than "its address moved" — an artifact of this correction
  sequence, not a candidate defect.
- **R8 was run before R5 in this session** (out of the brief's own listed order), and R8's step 2
  initially showed **zero** `(attempt` lines within 60s of `xy.android.acc.on` — tracked down to
  `AapService.onHibernateWake()` (`aap/AapService.kt:711-737`) requiring `settings.autoStartOnBoot`
  to do anything at all; with it `false` the wake is logged and nothing else happens. Round 1's own
  R8 inherited `auto-start-on-boot=true` from its own R5, which ran first in round 1's order; this
  round did not. Fixed by setting `auto-start-on-boot=true` (matching what R5 sets) and rerunning R8
  cleanly; R5 itself was then run after, using the manual re-arm the brief explicitly permits
  (`ACTION_STOP_SERVICE` then `ACTION_START_WIRELESS --ez no_ui true`) rather than a second physical
  reboot.
- Verified `log-level=2` (INFO) is sufficient for every decisive line this round needed — none of
  `SoftApCredentialsProvider.kt` / `WifiLauncherNative.kt`'s relevant lines are behind a
  `LOG_VERBOSE` guard (checked by grep before relying on it).
- Captures: `hotspot-endpoint-poison-round2-captures.zip`, sha256
  `5aa076d2c2cc69772da8a151dd149b3bd76ea979338d61a6f44b021954e6289f`, uploaded to the existing
  `rig-evidence-hotspot-endpoint-poison` release (round 1's asset stays alongside it).

## R0 — Build gate

**PASS**

- SHA `30a1f6d8da21` (4 commits on `main` `dd454eed1`, one more than round 1's `018d5af6`), matches
  brief.
- JVM gate: 2339 tests, 0 failures, 0 errors.
- DEX carries `SoftApEndpointStabilityPolicy` (11 string refs) and `SoftApAutoEnablePolicy` (3
  string refs) — present in round 1's APK too, so per the brief's own note this doesn't distinguish
  the two builds; `ACTION_QUERY_STATE` does: `"commit":"30a1f6d8da21"`.
- `adb install -r`, md5-verified.

## R2 — Same boot: no endpoint on an address not yet seen across a restart

**PASS**

- Bring-up 1: `stable=unproven`, `"first reading of the access point's address at 192.168.41.88..."`
  at `17:21:19.716`; `not advertising WPP over TCP` at `17:21:34.878`; `SSL handshake complete` at
  `17:21:40.726`.
- Bring-up 2 (after the D-MOTO stale-endpoint recovery above, same boot): `stable=unproven`,
  `"same address 192.168.41.88, but not yet seen across a restart..."` at `17:47:49.026`;
  `not advertising WPP over TCP` at the same identity check; `WirelessServer: Incoming connection
  detected from /192.168.41.63` at `17:49:25.093`; `SSL handshake complete` at `17:49:25.361`.
- Phone (D-MOTO, post-`pm clear`): `No WPP on TCP configuration found in storage for the head unit`
  logged repeatedly (e.g. `17:51:08.376` onward).
- No `advertising WPP over TCP` line appeared on either bring-up.

## R3 — Across a reboot (the point of the round)

**PASS** (took the "moved" branch)

- `adb reboot` (operator-performed, `boot_count` 219→220). D-HU's `wlan2` AP again came up degraded
  on the first `start-softap` after reboot (same pattern round 1's Setup notes recorded); one
  `stop-softap`/`start-softap` cycle cleared it, address `192.168.170.242` (BSSID unchanged,
  `00:27:15:43:06:6A`).
- Bring-up 1 (clean, after the hotspot-ssid/password correction above): `stable=no`,
  `"the access point came back but its address moved from 192.168.41.88 to 192.168.170.242, and the
  phone would keep the old one"` at `17:59:40.575`; `not advertising WPP over TCP` (reason: "this
  unit gives its access point a new address every time it comes up...") at `17:59:41.219`;
  `SSL handshake complete` at `17:59:42.394` — no phone forget needed (nothing was ever advertised on
  this candidate to get stuck on).
- Bring-up 2 (no reboot): `stable=unproven`, `"same address 192.168.170.242, but not yet seen across
  a restart"` at `18:00:09.857`; `not advertising WPP over TCP` at `18:00:14.936`;
  `SSL handshake complete` at `18:00:16.139`.
- No `advertising WPP over TCP` on any bring-up whose `ip=` differed from the one before it.

## R4 — A moved password raises the banner (made testable)

**FAIL**

- Setup: AP up from the shell (`OHU-HOTSPOT`/`ohutest12345`), one bring-up
  (`ssid=OHU-HOTSPOT bssid=00:27:15:43:06:6A ip=192.168.170.242`, `18:00:55.281`). Force-stopped and
  seeded `soft-ap-advertised-ssid=OHU-HOTSPOT`, `soft-ap-advertised-bssid=00:27:15:43:06:6A`,
  `soft-ap-advertised-ip=192.168.170.242`, `soft-ap-advertised-psk-digest=`(64 zeros),
  `connection-issue-stale-endpoint=0`; all five read back correctly before the next bring-up.
- **PASS conditions met:**
  - `NativeAA: the WPP endpoint advertised on the access point at 192.168.170.242 no longer matches
    (password); a phone holding it needs this head unit forgotten in Android Auto.` — exactly once,
    at `18:01:18.581`, with only `password` in the brackets.
  - The four `soft-ap-advertised-*` keys were gone from `settings.xml` after a force-stop.
  - The second bring-up printed no further `no longer matches` line.
- **PASS condition failed:** `connection-issue-stale-endpoint` read back as `0` (unchanged) after the
  force-stop, not non-zero.
- **Root cause, traced in the candidate's own code (not something the test setup can route around):**
  `WifiLauncherNative.noteAdvertisedEndpointMoved()` (`connection/wifi/modes/WifiLauncherNative.kt:270`)
  calls `ConnectionIssues.raiseOnce(service, ConnectionIssue.PHONE_HOLDS_STALE_ENDPOINT)`, which does
  write `connection-issue-stale-endpoint` to a non-zero epoch — but
  `NativeAaHandshakeManager.retireStaleEndpointRecord()`
  (`connection/wifi/modes/nativeaa/NativeAaHandshakeManager.kt:2601-2605`) clears the same flag on
  **every** successful Bluetooth handshake landing (`WppAction.CompleteSuccess`,
  `NativeAaHandshakeManager.kt:2878`), gated only by
  `StaleEndpointRecordPolicy.retiredByHandshake(dialRefusedSinceLastLanding)`, which returns `true`
  whenever `dialRefusedSinceLastLanding` is `false` — i.e. whenever no phone dial was ever *refused*
  by our own `WppTcpServer` since the last landing. In the exact "address moved" scenario this whole
  branch targets, a phone dialling the old dead address never reaches our server to be refused at
  all (the connection times out on the phone's own network layer, as observed directly against
  D-MOTO's real GH.WPP.TCP logs earlier in this same session — `SocketTimeoutException`, not a
  refusal). The very same bring-up that raises the banner also lands a normal Bluetooth handshake
  with the *same* phone (this is what R2/R3/R4 all just demonstrated repeatedly), so
  `retireStaleEndpointRecord()` fires immediately afterward and clears the counter before a force-stop
  ever gets to observe it — and, by the same mechanism, before a real user would ever see the banner
  persist long enough to act on it. The code comment at `NativeAaHandshakeManager.kt:2596-2599`
  states the assumption plainly ("a phone holding the endpoint never comes over Bluetooth, so one
  that did holds none") — today's own captures throughout R2 and R3 show a phone can do both at once
  (D-MOTO simultaneously retried a stale WPP-TCP address in a loop while completing a fresh Bluetooth
  handshake with this same head unit), so that assumption does not hold on this rig.
- The phone was not expected to be stuck for this run (it holds no real endpoint of its own here —
  the advertised record is fabricated purely on the head unit's own settings, per the brief), so
  nothing further to observe there.

## R5 — The app switches the hotspot on after a boot

**PASS**

- Ran via the brief's permitted manual re-arm (`ACTION_STOP_SERVICE` then
  `ACTION_START_WIRELESS --ez no_ui true`) rather than a second physical reboot, run *after*
  R8 was corrected (see Setup notes on the run-order dependency).
- Keys: `hotspot-ssid=Navegadortz2`, `hotspot-password=12345678` (the persisted system default, also
  what the operator had just set via Android's own Tethering settings), `auto-enable-hotspot=true`,
  `auto-start-on-boot=true`.
- `SoftApCredentials: No access point after 5s — trying to switch this device's hotspot on (attempt 1
  of 2).` at `18:16:05.077`, `HotspotManager: Setting hotspot enabled=true` at `18:16:05.091`.
- `HotspotManager: Every start path was tried on 5 GHz...` (give-up) at `18:16:25.264`;
  `the hotspot did not come up on attempt 1.` at `18:16:25.267`.
- `(attempt 2 of 2)` at `18:16:35.957` — **10.69s** after the give-up line (≥10s required), with its
  own `Setting hotspot enabled=true` at `18:16:35.959`.
- Give-up for attempt 2 at `18:16:56.069`, `did not come up on attempt 2.` at `18:16:56.072`.
- **Zero** `A hotspot start is already running` lines. Exactly **2** `Setting hotspot enabled=true`
  lines (one per attempt), **0** `Re-enabling the hotspot we started` lines (the AP never came up, so
  there was nothing to re-enable).
- `ACTION_EXPORT_LOG`'s `LogExporter: session` header at `18:18:53.597` carried `autoHotspot:on`.
- **Recorded, not graded:** the AP never came up on either attempt (`wlan2` absent afterward,
  `HotspotManager`'s own "either the radio was busy or this unit refuses it" line) — the same
  rig-side radio-settle limitation round 1 hit in its own R5/R8, per `TESTING-TEMPLATE.md` §7a.

## R7 — WiFi Direct, no regression

**PASS**

- Reverted per round 1 §4 (`native-ap-transport=0`, hotspot keys cleared).
- `WifiDirectManager: group identity ssid=DIRECT-QS-MT50YT610E4GFPSLU persistent=yes ... bssid=
  EE:05:96:91:DF:50 ... stable=no (same name but the BSSID moved from 8E:3F:C8:FA:DB:BD to
  EE:05:96:91:DF:50; this unit re-addresses the group on every create)` at `18:05:13.686`.
- Zero `access point identity` lines in the capture.
- `SSL handshake complete` at `18:05:18.446`.

## R8 — The car switched off and on (XYAuto broadcasts)

**PASS** (after the run-order correction in Setup notes; the first attempt, run before R5, is
discarded and not counted — see Setup notes)

- Setup: hotspot route re-armed (`OHU-HOTSPOT`/`ohutest12345`), `auto-enable-hotspot=true`,
  `auto-start-on-boot=true` (matching R5), AP up from the shell, one bring-up to a live session
  (`SSL handshake complete` at `18:11:08.341`).

**Step 1** (`xy.android.acc.off`, then `cmd wifi stop-softap` within 2s, watched 60s):
- `WakeDetect: ACC off (xy.android.acc.off)` at `18:11:14.575`.
- **Zero** `(attempt` lines in the 60s window.
- `connection-issue-hotspot-off` unchanged at `0` before and after the step.

**Step 2** (`xy.android.acc.on`, watched 60s):
- `WakeDetect: xy.android.acc.on` at `18:12:32.043`, `AapService.onHibernateWake: WakeDetect:
  launching UI (trigger=xy.android.acc.on)` at `18:12:32.045`.
- **No** `(attempt` line in the first 15s (next one at +15.5s).
- `SoftApCredentials: No access point after 113s — trying to switch this device's hotspot on
  (attempt 1 of 2).` at `18:12:47.544`.

**Step 3** (repeat step 1, then `xy.android.acc.on` and within 5s `cmd wifi start-softap
OHU-HOTSPOT wpa2 ohutest12345 -b 5`, watched 60s):
- `xy.android.acc.on` at `18:14:05.391`; `start-softap` issued at `18:14:08.341` (**2.6s** after ACC
  on, within the 5s window).
- **Zero** `(attempt` lines in the 60s window.
- `SoftApCredentials: SUCCESS - Providing credentials from wlan2: SSID=OHU-HOTSPOT,
  IP=192.168.170.242, BSSID=00:27:15:43:06:6A` at `18:14:08.933`.

If the change had done nothing, step 2 would have printed `(attempt 1 of 2)` within about 2s of the
ACC-on line (as round 1 measured), and step 3 would have asked for the AP over the one the shell just
started — neither happened.

## Anything the brief did not ask about

- **The in-app "Forget" control for a vehicle in Android Auto's Vehicles list does not reliably clear
  a phone's cached WPP-over-TCP endpoint on this Gearhead build.** Forgetting the head unit via that
  UI, and separately forgetting the underlying saved WiFi network, both left D-MOTO retrying the same
  dead `192.168.49.1:5299` address; only a full `pm clear` on Gearhead's app data cleared it. The
  candidate's own log line (`"...if a connection will not start, forget this head unit on the
  phone"`) may be advising a remedy that does not reliably work on at least this phone/build — worth
  a closer look independent of this branch, since it's the app's own suggested user recovery path for
  exactly the failure mode this branch targets.
- **R4's finding above (the stale-endpoint banner counter self-clears on the same bring-up that raises
  it)** looks like it would reproduce on real hardware too, not just this synthetic test setup — see
  the root-cause analysis under R4. Flagging it as the most consequential single finding from this
  round, since it affects the observability of the exact condition #1010's original reporter hit.
