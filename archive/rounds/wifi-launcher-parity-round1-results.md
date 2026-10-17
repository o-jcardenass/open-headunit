# wifi-launcher-parity — round 1 results

**Arm A:** `origin/3.3.0-alpha` @ `e8fe4611afeaeb1afe68861f52c458ef96b2a43f`
**Arm B:** `origin/main` @ `048f4eaf663cbb9d9589ebd9c4233d4a262759a3`
**Arm C:** `fork/fix/wifi-launcher-parity` @ `63a3f699cda5b89349df9aa05563821c2f388440`
**APK md5:** A `7174a3788c27f180a262badf48a0a8ca` / B `08074010a57d1cab1c210f7bda39d361` / C `5a1121ee8821a13f664e7cc514b304a9`
**Unit:** UNISOC MT50_YT610E4GFPSL_U, Android 14, 720x1440@240dpi. Phone: Redmi/POCO X3 NFC (`surya_eea`), Gearhead `17.5.663204-release`.
**Date:** 2026-08-24

## Report back (per §8)

Per-run triple `A / B / C`:

| Run | A | B | C |
|---|---|---|---|
| R0 (build) | production build PASS, **unit-test compile FAIL** | PASS, 738/738 | production build PASS, **unit-test compile FAIL** (same cause as A) |
| R1 | **FAIL** | PASS | PASS |
| R2 | INCONCLUSIVE | INCONCLUSIVE | INCONCLUSIVE |
| R3 | UNTESTABLE | UNTESTABLE | UNTESTABLE |
| R4 | **FAIL** | PASS | PASS |
| R5 | UNTESTABLE (pre-registered) | UNTESTABLE | UNTESTABLE |
| R6 | UNTESTABLE | UNTESTABLE | UNTESTABLE |
| R7 | INCONCLUSIVE-by-construction | PASS | PASS |
| R8 | UNTESTABLE | UNTESTABLE | UNTESTABLE |

1. **Runs that are `A=FAIL, B=PASS`: 2** (R1, R4).
2. **Of those, `C=PASS`: 2 of 2** (R1, R4). Both confirmed regressions are fixed on the candidate.
3. **Runs that are `B=FAIL`: 0.** Nothing subtracts from the count above on rig grounds.

R2 and R7(on arm A) could not produce a triple because of rig-side reachability, not because the defects are disproven — see their sections below. R3/R5/R6/R8 are a phone-side constant across all three arms (see §"Helper mode blocked" below), not a per-arm finding.

**Anything on arm C that A and B both did fine:** R1 on arm C surfaced a genuinely new guard not present in either A or B — `NativeAaHandshakeManager` now checks for an already-active Bluetooth hands-free link before poking and skips the poke if one exists (`NativeAA: Not poking ... this head unit already holds a Bluetooth hands-free link, which a poke would take over and leave disconnected.`). This did not regress anything observed in this round (the handshake still completed normally via the phone's own reconnect), but it's a real behavior change beyond restoring parity and worth a second look in review.

## Setup notes

- **Rig inventory**: used `hur-wifi-test-scripts/build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh` for every settings write (multi-key, single relaunch). `install_and_launch.sh` was not used — installed manually with `adb install -r -d` since arm B (versionCode 98) is a downgrade from arm A/C (versionCode 100), and `install_and_launch.sh` only does `-r`.
- **APK preservation**: copied each arm's APK out of `apks/` into `round-wifi-launcher-parity/arm{A,B,C}/` immediately after building, since `build_hur.sh` deletes the previous APK (§5).
- **Bluetooth was unbonded at round start.** Head unit's bonded-device list was empty and the phone's list didn't include the head unit (same failure mode as `TESTING-TEMPLATE.md`'s round-9 note). Re-paired by hand (the only way — needs a UI consent tap) before any run could proceed. Confirmed bonded on both sides afterward.
- **Head unit lost power mid-round** (low battery) during R2 on arm A. Recovered after charging; reappeared in `adb devices`, still bonded, app/settings state intact. R2 on arm A was already past its useful signal at that point (see below) so nothing was lost, but noting it since a mid-round power loss is exactly the kind of thing a future round should watch for on this rig.
- **R0's unit-test compile failure (arms A and C) is a real, pre-existing defect**, not a local artifact: `UserExitHotspotPolicyTest.kt:154` calls `WifiModePolicy.usesWifiDirect(WifiLauncherMode.NATIVE, 0, NativeStrategy.WIFI_DIRECT)`, mixing a `WifiLauncherMode` first argument with a raw `Int` second argument against a test-only `WifiModePolicy` object (`app/src/test/.../WifiModePolicy.kt`) that only overloads `(Int,Int,NativeStrategy)` and `(WifiLauncherMode,HelperStrategy,NativeStrategy)`. Confirmed present verbatim at `e8fe4611` itself (`git log` shows it landed in "fixing typos and some small refactorings", not something this session's checkout introduced) and unchanged by any of arm C's four commits (`git diff e8fe4611 63a3f699 -- .../UserExitHotspotPolicyTest.kt` only shows new code appended after it). **Effect: no unit-test counts are available for arm A or arm C** — including the four named suites the brief asked for (`WifiLauncherCapabilityTest`, `UserExitHotspotPolicyTest`, `UsbSessionQuiescePolicyTest`, `LinkLossTeardownPolicyTest`) — because the whole test source set fails to compile before any test runs. The production `assembleGithubDebug` build is unaffected on both arms (it doesn't compile the test source set), so hardware testing was not blocked. This one-line test bug should be fixed upstream before arm C's real coverage number can be reported.
- **R1's brief-specified fourth PASS line (`Attempting active poke to device`) did not fire on arm B**, and R1 is still recorded PASS. Every other signal (credentials delivered end-to-end, `WirelessServer: Incoming connection detected`, SSL handshake complete) was present; the poke line is gated behind the retry loop's first check finding `handshake=false`, and on arm B the phone's own Bluetooth reconnect landed fast enough that the loop's very first check already saw `handshake=true` and self-stopped (`Stopping poke retry loop (settling=false, handshake=true, session=false)`) before ever attempting a poke — exactly the rig behavior `TESTING-TEMPLATE.md` §7a documents ("the phone's own reconnect beats our poke, so the poke is normally never exercised"). Treating this as a FAIL would have been a false negative from a rig timing artifact, not the defect under test.
- **R1's positive control** (native-ap-transport=0 after a PASS) was run once, on arm B, and passed cleanly (group formed, credentials updated, SSL handshake complete). Not re-run on arm C because arm C's own R2/R4 setups already used `native-ap-transport=0` and formed clean sessions both times — the WiFi Direct transport is independently proven working on arm C without a dedicated control run.
- **R2 was INCONCLUSIVE on all three arms for the same reason**: after `headunit://disconnect`, the phone's Bluetooth link never actually cycled (no `ACL_DISCONNECTED`/`ACL_CONNECTED` pair) within the observation window on any arm (60-90s+ each), so `MATCH! Starting AapService` never fired a second time and the re-arm code path was never reached. This is a genuine, repeated rig characteristic — not chased further per the no-poke rule — but it means **R2's actual defect (`WifiLauncherManager.stop()` nulling `active` before the re-arm reads it) remains formally untested on hardware on every arm.** Worth routing to a JVM test given the reachability wall is consistent, not a fluke of one run.
- **R4 used `ACTION_START_WIRELESS_SCAN` instead of the brief's `ACTION_START_WIRELESS`.** Confirmed in source (`AapService.kt:1879-1884` on arm A) that `ACTION_START_WIRELESS` calls `wifiLauncherManager.setActiveFromSettings()` **without** `force`, which no-ops with the log line `WifiLauncher: WiFi Mode NATIVE.mode with same start-configuration is already initialized` when nothing in settings actually changed since the session started — exactly our setup, since we can't change settings without stopping the app (which would tear down the very session under test). `ACTION_START_WIRELESS_SCAN` calls the same method with `force = true` (`AapService.kt:1895`), which is the only scriptable way to reach the real stop/restart cycle `stopWirelessServer()` lives in. This substitution reached the intended code path on all three arms (confirmed by the VPN start/stop pairs actually firing) and is recorded as a deviation, not a silent swap.
- **Helper mode (R3/R6/R8) is UNTESTABLE on this rig/phone combination for a documented, external reason**, not a rig limitation discovered this round: Android Auto/Gearhead 17.4.663004+ disabled the `WirelessStartupReceiver` component that Wireless Helper's connect trigger depends on (`[[project_gearhead_17_4_broadcast_disabled]]`), and this phone runs `17.5.663204-release`. Verified directly this round: the Wireless Helper companion app (`com.andrerinas.wirelesshelper`) is installed and was launched, the head unit's `WirelessServer` bound and listened on 5288 correctly, the phone was joined to the head unit's hotspot (`OHU-TEST`, confirmed via `dumpsys wifi`), and `NetworkDiscovery`'s gateway scan correctly targeted `192.168.125.1:5289` — every piece worked except the one broadcast Gearhead now silently ignores. This is a phone-side constant, independent of which HUR build is installed, so it wasn't re-attempted per-arm; UNTESTABLE is recorded identically on all three. **This also means R8 (the round's own control, whose brief text says a FAIL there voids everything else) never got to run its actual FAIL/PASS check** — but since the blocking cause here is independently documented, external, and unrelated to session-forming code in general (R1/R2/R4/R7 all *did* form real Native AA sessions on this same rig in this same round), the Native AA findings above are not treated as voided by it.
- **R7 on arm A is recorded INCONCLUSIVE-by-construction per the brief's own instruction** (R1 FAILed on arm A). One thing worth flagging beyond that instruction: `WirelessServer: binding port 5288...` did not merely arrive late on arm A — it **never appeared at all**, anywhere in the R1/R7 capture. The server was never started on the hotspot transport, not started-late. Consistent with the round's own root-cause framing (the 5288 server has no wiring on the hotspot arm of the pre-refactor equivalent), but worth naming precisely since "never" and "late" are different failure shapes and only "late" was in the brief's literal FAIL wording.
- Log level: DEBUG (`log-level=1`) throughout, as specified.
- `hotspot-teardown-proven-unsafe` was left `true` in settings from an earlier thread's round and not cleared — confirmed harmless for this round since it has no caller on arm A (§1 of the brief) and R3 never reached the code that would read it on any arm.

## R0 — build gate, every arm

**Arm A: PASS** (production) / **FAIL** (unit tests). `assembleGithubDebug` clean. `testGithubDebugUnitTest` fails to compile: `UserExitHotspotPolicyTest.kt:154:35`, type mismatch described above. No test counts available.

**Arm B: PASS.** Clean compile, 738/738 tests, 0 failures.

**Arm C: PASS** (production, first-ever compile) / **FAIL** (unit tests, first-ever compile). Same `UserExitHotspotPolicyTest.kt:154` failure as arm A, confirmed unchanged by the candidate's own diff. No test counts available — including the four named suites the brief asked for.

## R1 — Native AA over the head unit hotspot never gets its credentials

**Arm A: FAIL.**
- Settings: `wifi-connection-mode=3`, `native-ap-transport=1`, `auto-enable-hotspot=true`, `hotspot-ssid=OHU-TEST`, `hotspot-password=testtest1234` (manual override, matched to a manually-started system hotspot — this rig can't read its own hotspot config back, per `[[project_test_headunit_hotspot_config_unreadable]]`).
- Radio state: head unit hotspot up first (5 GHz, confirmed via `dumpsys wifi`), then app launched, phone paired and in range throughout.
- Discard-rule check: clean (0 `createGroup SUCCESS`, 0 `MATCH!`, no p2p index).
- Decisive lines: `SoftApCredentials: SUCCESS - Providing credentials from` ×12 over the 3-minute window, each followed by `the access point resolved before anything was listening for it; holding the credentials until it is.` `Received WiFi credentials from manager`, `NativeAA: Credentials updated.`, and the automatic `Attempting active poke to device` **never appeared**. Two full handshake attempts both timed out: `NativeAA: Handshake failed - No WiFi credentials available after 60s wait.`
- One manual poke was triggered from the on-device UI during the window (not scripted by this session); it didn't change the outcome — the credentials were still never delivered to the handshake manager.

**Arm B: PASS.**
- Same settings/radio setup.
- `SUCCESS - Providing credentials from` → `Received WiFi credentials from manager` → `NativeAA: Credentials updated. SSID=OHU-TEST` → phone connected before the automatic poke fired (loop self-stopped, see Setup notes) → `WirelessServer: Incoming connection detected from /192.168.47.199` → SSL handshake complete. Full handshake in **5.27s** (listeners-open to SSL-complete: `00:58:29.603` → `00:58:34.847`).
- Discard-rule check: clean.
- Positive control (`native-ap-transport=0`, WiFi Direct): PASS — group formed, credentials updated, SSL handshake complete in ~17s.

**Arm C: PASS.**
- Same settings/radio setup.
- All four brief lines present, in order, plus the new poke-skip guard (see Setup notes). `WirelessServer: Incoming connection detected from /192.168.47.199` at 01:11:40.885, SSL handshake complete at 01:11:41.159. Full handshake **8.49s** (listeners-open to SSL-complete: `01:11:32.672` → `01:11:41.159`).
- Discard-rule check: clean.

## R2 — after a Native AA user exit, Bluetooth cannot re-arm it

**All three arms: INCONCLUSIVE** (reachability — see Setup notes).

- Arm A: `AapService: Native AA user exit. Stopping active launcher.` fired correctly on `headunit://disconnect`. Observed ~2 min afterward (curtailed by the head unit losing power). `MATCH! Starting AapService`: 0.
- Arm B: `AapService: Native AA user exit. Stopping handshake manager.` (confirmed brief's predicted wording difference from A/C). Observed 150s afterward. `MATCH!`: 0.
- Arm C: `AapService: Native AA user exit. Stopping active launcher.` (matches A's wording, as predicted). Observed 90s afterward. `MATCH!`: 0.
- No arm produced a second Bluetooth `ACL_CONNECTED` after the exit within the observed window, so the re-arm code path itself was never exercised on any arm. Ordering half (gap between disconnect deep link and the exit line) was not meaningfully measurable given no re-arm ever followed to compare against.

## R3 — a user exit leaves the head unit hotspot up

**All three arms: UNTESTABLE.** Blocked by the Gearhead 17.4+ Wireless Helper regression (see Setup notes) — a session in Helper/head-unit-hotspot strategy cannot form on this phone. Confirmed the hotspot itself comes up fine and the phone joins it; the phone-side trigger that would complete the session is what's missing, for reasons unrelated to this branch.

## R4 — a mode change takes the user's VPN down

**Arm A: FAIL.**
- Settings: `wifi-connection-mode=3`, `native-ap-transport=0`, `keep-dummy-vpn-during-session=true`. VPN consent already granted (`appops ACTIVATE_VPN: allow`).
- Session formed, `tun0` confirmed up (`10.0.0.2/24`). Fired `ACTION_START_WIRELESS_SCAN` (see Setup notes for why, not the brief's literal `ACTION_START_WIRELESS`).
- Decisive sequence: `AapService: releasing the dummy VPN (owner=SESSION, reason=SESSION_ENDED)` → `VpnControl: Stopping DummyVpnService (GitHub Build)` (owned, expected) → **a second, separate `VpnControl: Stopping DummyVpnService (GitHub Build)` call, on a different coroutine (`[53]` vs `[2]`), with no `releasing the dummy VPN` line before it.** This is exactly the brief's FAIL signal for an unowned teardown call. (The VPN was still restarted a few seconds later as the session re-formed, so `tun0` didn't stay down for this run — the signal is the unowned call itself, not a permanently-lost VPN.)

**Arm B: PASS.**
- Same settings/setup. Exactly one `Stopping DummyVpnService` call, correctly preceded by `releasing the dummy VPN (owner=SESSION, reason=SESSION_ENDED)`. No second, unowned call.

**Arm C: PASS.**
- Same settings/setup. Same clean single-owned-call sequence as arm B.

## R5 — Self Mode arms the wrong thing (pre-registered UNTESTABLE)

**All three arms: UNTESTABLE**, as pre-registered. Phone's Gearhead: `17.5.663204-release` (checked directly on the phone this round — my first check hit the head unit's own bundled Gearhead install by mistake, corrected). 17.5 ≥ 17.4, so the `AA 17.4+ detected. Connecting directly to Headunit Server` branch is taken and the launcher code path R5 targets is unreachable, exactly as §7a predicted.

## R6 — tapping a discovered Nearby device cannot connect to it

**All three arms: UNTESTABLE.** Same Gearhead 17.4+ regression as R3/R8 — Wireless Helper (which owns the Nearby strategy too) cannot trigger a session on this phone's AA version. Not re-attempted per-arm since the blocker is phone-side and constant.

## R7 — the hotspot transport binds 5288 late

**Arm A: INCONCLUSIVE-by-construction** (R1 FAILed on this arm, per the brief's own instruction). Additional observation: `WirelessServer: binding port 5288...` did not appear anywhere in the capture — the server was never started, not started late. See Setup notes.

**Arm B: PASS.** `binding port 5288...` at `00:58:29.539`, before `NativeAA: Starting Bluetooth Handshake Servers` (`00:58:29.577`) and well before any poke. `found port 5288 unbound` never appeared.

**Arm C: PASS.** `binding port 5288...` at `01:11:32.600`, before `NativeAA: Starting Bluetooth Handshake Servers` (`01:11:32.650`) and before the poke attempt (`01:11:34.705`). `found port 5288 unbound` never appeared.

## R8 — the control, and the run that makes the round readable

**All three arms: UNTESTABLE.** Same Gearhead 17.4+ regression. See Setup notes for why this isn't treated as voiding R1/R2/R4/R7's results.

## Anything the brief did not ask about

- The candidate's new hands-free-link-aware poke skip (R1, arm C) — see "Anything on arm C" above.
- This round's own R0 finding (`UserExitHotspotPolicyTest.kt:154`) blocks getting a real unit-test coverage number on the fix branch itself. Worth fixing before this goes upstream, independent of the WifiLauncher parity question — it's a one-line fix (change the literal `0` to `HelperStrategy.COMMON_WIFI` or the matching enum value to hit the `(WifiLauncherMode, HelperStrategy, NativeStrategy)` overload).
- Head unit lost power mid-round from low battery — first time this specific failure mode has been logged on this rig in this channel's history. Worth keeping an eye on for future rounds; charge before starting.
