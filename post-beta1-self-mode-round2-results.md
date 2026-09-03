# post-beta1-self-mode, round 2 results

**Candidate:** `fork/fix/post-beta1-self-mode-and-log-probe` @ `82df5d33` (rebased onto current `main`)
**Baseline:** `origin/main` @ `78689a96`
**APK md5:** `452dc20f7e8ac5a14d03d8203efff181` (candidate) / `825b36fd5d91663c60c3e03512a067b9` (baseline)
**Units:**
- **D-HU**, UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14 / API 34, Gearhead `17.3.662864-release`, `READ_LOGS` granted=true
- **D-POCO**, POCO X3 (`M2007J20CG`), Android 15 / API 35, Gearhead `17.5.663204-release`, `READ_LOGS` granted=false
- **D-MOTO**, Motorola edge 30 neo, Android 14 / API 34, Gearhead `17.3.662854-release`, `READ_LOGS` granted=false
**Date:** 2026-08-27

## Summary

| Run | Verdict | One line |
|---|---|---|
| R0 | **PASS** | candidate 770/0, baseline 765/0, exact match; delta +5 = `SelfLaunchTimeoutPolicyTest` |
| R1 | **PASS** | candidate spawns zero app-owned `logcat` on all three, `MainActivity` resumes ~1 s; baseline reproduces the `LogAccessDialog` on D-HU (held the foreground the full poll, unattended, `MainActivity` never resumed) |
| R2 | **PASS** | candidate `HUR_Log` capture still works (81 KB file, `LogExporter: session \|` present); session formed while the consent dialog was still up, so `onCreate` was not blocked |
| R3 | **PASS** (D-HU + D-MOTO) | legacy Self Mode forms in 1352 ms / 2033 ms, no teardown, no timeout, no toast, no defect-3 line |
| R4 | **INCONCLUSIVE** for the defect-2 mechanism | baseline connects on both devices (1244 ms / 1443 ms), always beating the old 2500 ms watchdog, so the disconnect-on-timeout never fires here |
| R5 | **PASS** (candidate) / **INCONCLUSIVE** (baseline) | dummy VPN up (`excludeSelf=false`), tun held the whole 90 s on both arms with no `releasing the dummy VPN`; baseline never released it for the same reason as R4 |
| R6 | **PASS** both arms | `CarErrorDisplay` = 0 on D-MOTO on candidate and baseline |
| R7 | **PASS** | `SelfMode: nothing connected within 30000ms of the launch` at +30.09 s, `permissions are in order`, no teardown |
| R8 | **PASS** | D-POCO 17.4+ route forms and holds 90 s, no teardown/timeout; one transient first-attempt failure (`Headunit Server ... is NOT running`) that self-recovers via `NetworkDiscovery` ~9 s later |

**Shipping question:** defect 1 (log probe) and defect 3 (inverted permission-trampoline test) are **fixed and confirmed on hardware**, same as round 1 and now on the rebased tip. Defect 2 (the 2500 ms watchdog that disconnects) **still could not be exercised on this rig**: loopback Self Mode connects in 1.2 to 2.0 s on all three devices, so the baseline never trips its own 2.5 s bug. That coverage rests on `SelfLaunchTimeoutPolicyTest` (the +5 JVM tests in R0) and on the candidate's observable behaviour (R3: no disconnect; R7: 30 s deadline reported, no disconnect-on-timeout).

## Setup notes

**The candidate is the rebased tip.** Round 1 followed the brief's pinned `8bc6fcce`. Since then `fix/post-beta1-self-mode-and-log-probe` was rebased onto the current `main` (`78689a96`, which carries the "android 4 errors" fixes) and is now two commits: `1a2976a3` (logging: stop asking the ROM about logcat on every app launch) and `82df5d33` (Self Mode: a launch that is still in flight is not a launch that failed). `git diff 78689a96..82df5d33` is 8 files, +203 / -68, and matches round 1's fix content: `SelfLaunchTimeoutPolicy.kt` (new), `SelfLaunchTimeoutPolicyTest.kt` (new, 5 tests), `CommManager.kt`, `AAPermissionTrampolineActivity.kt`, `SelfLaunchResolveHelper.kt`, `SelfLauncherManager.kt`, `LogExporter.kt`, `Settings.kt`. Baseline moved to `78689a96` to match.

**Build version bumped to `3.3.0-beta2 (101)`** (round 1 saw `beta1 (100)`), seen in the `LogExporter: session |` banner.

**Every device was offline for the entire round.** `dumpsys connectivity` read `Active default network: none` on all three from the first check. D-POCO and D-MOTO each expose one LTE `NetworkAgentInfo` but it is an IMS-only PDN (`Capabilities: IMS&...`, no `INTERNET`), so it carries no app traffic; D-HU had no `NetworkAgentInfo` at all. Consequence: every Self Mode run took the dummy-VPN path (`HomeFragment.startSelfMode` guards on `activeNetwork == null`), so R3 and R8 exercised the VPN too, not only R5. Same as round 1. The rig's network state was not changed by this session (no authorization to).

**PC thermal.** The build machine hard-power-cut twice on the pre-round-1 attempt (confirmed thermal). This round: CPU turbo disabled (`/sys/devices/system/cpu/intel_pstate/no_turbo = 1`), all gradle invocations run `taskset -c 0,2,4,6 nice -n 10 ... --max-workers=4` with a temperature watchdog (abort at 90 C sustained). Peak package temperature 88 to 89 C across the four builds/test runs, zero `package_throttle_count` events, no power-cut. A corrupt Gradle transforms cache (`~/.gradle/caches/8.13/transforms/701327bb.../metadata.bin`, left by an earlier crash-during-build) blocked the first candidate build with "Could not read workspace metadata"; clearing the `transforms` directory and stopping the daemon fixed it.

**`settings.xml` diff vs a fresh backup at round start: zero on all three for every tracked key.** `wifi-connection-mode` unchanged (D-HU=3, D-POCO=1, D-MOTO=2), checked after R3 and R4. Round 1's "normalised to 1 during failed Self Mode attempts" did not recur. D-HU's `auto-start-bt-macs` set went from populated to empty over the round (not a tracked key, app housekeeping of a stale auto-start MAC). D-POCO drifted only `last-loc-*`.

**The `AapService: dummy VPN requested (owner=SELF_MODE)` line from brief section 5 does not exist on this branch** (same as round 1). The SELF_MODE bring-up logs as `HomeFragment.startSelfMode | Device is offline. Preparing Dummy VPN for Self Mode.` then `VpnControl.startVpn | VpnControl: Starting DummyVpnService (GitHub Build, excludeSelf=false)` then `DummyVpnService.startVpn | DummyVpnService: tun established (excludeSelf=false)`.

**R2 needs a specific tap pattern on this ROM (`TESTING-TEMPLATE.md` section 7a candidate).** `log-capture-enabled=true` genuinely spawns `logcat`, which raises the system LogAccess consent dialog; that is expected and unchanged by the fix. On this UNISOC ROM "Allow one-time access" does **not** persist even for the lifetime of one `logcat` exec: the dialog re-fires roughly once a second and each re-fire kills the capture process. A first R2 attempt that kept tapping Allow for the whole session produced a 32 KB file missing the `LogExporter: session |` banner (the banner had rotated out of the ring buffer by the time a grant held). Tapping Allow only for the first ~8 s and then leaving `logcat` undisturbed produced the valid 81 KB file with the banner.

**The one non-scriptable step (`TESTING-TEMPLATE.md` section 0):** tapping the system LogAccess dialog on D-HU (R1 baseline, R2, R4 baseline). Bounds on this ROM: "Allow one-time access" `[354,603][950,675]`, "Don't allow" just below.

**Scripts.** Reused `r1_launch_probe.sh`, `selfmode_session_probe.sh`, `set_prefs_runas.sh`, and the `build_hur.sh` / `run_unit_tests.sh` gradle tasks (run through a thermal-cap wrapper for R0). No new scripts added.

## R0 - build and unit-test gate

**PASS**

- `assembleGithubDebug`: both APKs built clean, `BUILD SUCCESSFUL`. md5s recorded above and different.
- `testGithubDebugUnitTest`, parsed from `app/build/test-results/testGithubDebugUnitTest/*.xml`:
  - **candidate `82df5d33`: 770 tests, 0 failures, 0 errors** (73 result files)
  - **baseline `78689a96`: 765 tests, 0 failures, 0 errors** (72 result files)
- Delta is +5, all in `SelfLaunchTimeoutPolicyTest` (5 `@Test`). Exact match to the brief and to round 1.

## R1 - the log dialog, all three devices (POINT OF THE ROUND, half 1)

**PASS**

- Settings: all three already matched brief section 4 R1 (`auto-start-self-mode=true`, `auto-connect-*` off, `auto-connect-delay-seconds=0`, `log-level=2`, `log-source=0`, `log-capture-enabled` absent). Confirmed against a fresh backup, no writes.

### Candidate - PASS on all three

| Device | launch to `MainActivity` resumed | app-owned `logcat` (poll frames + `logdr: UID=<app>`) | `LogAccessDialog` | after resume |
|---|---|---|---|---|
| D-HU | 10:10:45.2 to 10:10:46.0 (**~0.8 s**) | **0** | **none** | to `AapProjectionActivity` to Maps `GhostActivity` (full session) |
| D-POCO | 10:11:14.0 to 10:11:15.0 (**~1.0 s**) | **0** | **none** | to `AapProjectionActivity` to Maps `GhostActivity` (full session) |
| D-MOTO | 10:11:40.3 (**<1 s**) | **0** | **none** | to `AapProjectionActivity` to Maps `GhostActivity` (full session) |

- `isLogcatSupported` / `LogExporter` probe lines: **0** on all three candidate captures. The only `logcat` process in every poll frame is the shell-owned external capture.
- All three formed a full Self Mode session this round (round 1, D-POCO only reached Gearhead first-run).

### Baseline - dialog reproduced on D-HU; pre-registered report on D-POCO / D-MOTO

| Device | `LogAccessDialog` | app spawned `logcat` | launch to `MainActivity` resumed | notes |
|---|---|---|---|---|
| **D-HU** | **yes**, `com.android.systemui/.logcat.LogAccessDialogActivity` held the foreground 10:14:04.5 through 10:14:25.3 (entire 30-frame poll, did **not** auto-dismiss unattended) | **yes**, `u0_a168 21095` `logcat` process, poll frames 2 to 30 | never during the poll | `READ_LOGS` granted=true and the dialog still fires. Later cleared on its own and the session formed. |
| D-POCO | no | yes, `logdr: UID=10268` (app uid) once at 10:13:04.578, silent, no consent UI | ~1.0 s, full session | API 35; the exec is allowed with neither the permission nor a dialog. Pre-registered not-a-fail. |
| D-MOTO | no | no evidence (0 app-uid `logdr`, 0 poll frames) | <1 s, full session | API 34, `READ_LOGS` off; the exec is denied silently. Pre-registered not-a-fail. |

- Both non-reproducing devices are API >= 33, so "below API 33" does not apply; reported per the brief as consent/ROM behaviour, not a candidate pass. The candidate arm stands on its own: **0 app-owned `logcat` on all three**.

## R2 - capture still works on the candidate

**PASS**

- Device D-HU. `log-capture-enabled=true` (via `set_prefs_runas.sh`), `log-level=2`, `log-source=0`.
- Procedure: launch `MainActivity`, tap "Allow one-time access" for the first ~8 s only, let the session run ~60 s, `headunit://exit`, read the app's `HUR_Log`.
- **`HUR_Log_20260827_102244_778.txt` = 81,323 bytes**, contains `LogExporter.startCapture | LogExporter: session | build=3.3.0-beta2 (101) github/debug ...` (count 1) and 11 session lines (`WirelessServer: Incoming connection detected`, `Throughput over`).
- **`onCreate` was not blocked:** the external capture shows `SelfMode: AA < 17.4 detected.` at 10:20:27.269 while the LogAccess dialog was still up (it started at 10:20:26.999), then `WirelessServer: Incoming connection detected from /127.0.0.1` 10:20:28.757 and `SSL handshake complete` 10:20:29.123. The probe is gone.
- No `Logcat capture produced 0 bytes`, no `log source is APPLOG_FILE`.
- First attempt notes: see Setup notes (the ROM re-fires the consent dialog roughly every second).

## R3 - legacy Self Mode on the candidate, D-HU + D-MOTO (POINT OF THE ROUND, half 2)

**PASS** on both.

- Settings per section 4, already in place. Radio offline, dummy-VPN path.
- Discard-rule: D-HU `createGroup SUCCESS`=1 (mode-3 artifact, `wifi-connection-mode=3`), `MATCH!`=0, SSL=1; D-MOTO `createGroup SUCCESS`=0, `MATCH!`=0, SSL=1. Clean.

| Condition | D-HU | D-MOTO |
|---|---|---|
| 1. `SelfMode: AA < 17.4 detected.` | 10:24:08.568 | 10:25:38.055 |
| 2. `WirelessServer: Incoming connection detected from /127.0.0.1` + `Throughput over` following | 10:24:09.937, throughput for 90 s, `dropped=0` (`c2.unisoc.hevc.decoder`, 9 to 18 fps) | 10:25:40.312, throughput for 90 s, `dropped=0` (`c2.qti.avc.decoder`, 29 to 30 fps) |
| 3. no `releasing the dummy VPN (owner=` / `Self Mode disconnected. Not restarting.` in first 30 s | absent from whole capture | absent from whole capture |
| 4. no `SelfMode: nothing connected within` at all | none | none |
| 5. no "Failed to start Android Auto" toast, no `AA's permission activity could not be started.` | 0 / 0 | 0 / 0 |
| **elapsed `Launching AA Wireless Startup` to `Incoming connection detected`** | **1352 ms** (10:24:08.585 to 10:24:09.937) | **2033 ms** (10:25:38.279 to 10:25:40.312) |

- Dummy VPN `tun established (excludeSelf=false)`: D-HU 10:24:08.432, D-MOTO 10:25:38.003. **Never released** on either device across the 90 s hold.
- Both: the legacy Activity launcher logs `SelfMode: Launch of 'v17.3 and older' had caused an error`, then `SelfMode: Launch of 'Fallback: Broadcast' had no issues` carries it. No `PermissionTrampolineActivity` line on this successful path (defect 3 is only reachable on R7's timeout branch here).
- `settings.xml` after R3: `wifi-connection-mode` unchanged (D-HU=3, D-MOTO=2).

## R4 - the same on the baseline, the A/B

**INCONCLUSIVE** for the defect-2 mechanism (both devices connect faster than the old deadline).

- Settings per section 4, restored from backup before each run. Radio offline. Baseline md5 `825b36fd...` verified live before each capture.
- D-HU used the dialog-dismiss step ("Don't allow" tapped at 10:36:05.948, +3.7 s).

| Device | `Launching AA Wireless Startup` to `Incoming connection detected` | teardown lines | session |
|---|---|---|---|
| D-MOTO | 10:34:19.640 to 10:34:21.083 = **1443 ms** | **none** (no `All launchers failed (timeout)`, no `Self Mode disconnected`, no `releasing the dummy VPN`) | held 90 s, `dropped=0` |
| D-HU | 10:36:07.657 to 10:36:08.901 = **1244 ms** | **none** | connection + SSL held 90 s; video `fed=0` after ~16 s |

- Both baseline devices beat the 2500 ms watchdog, so the deadline never expires and the teardown that defines defect 2 never fires on this rig. This is the pre-registered "baseline connects too, deadline met by luck" outcome, on both devices. `SelfMode: nothing connected within` cannot appear on `78689a96` (string absent). The mechanism's fix rests on `SelfLaunchTimeoutPolicyTest` (R0) and the candidate's R3/R7 behaviour.
- Dummy VPN came up on both (offline) and was **never released** (no teardown to trigger `onDisconnected`).
- Defect 3 on the baseline: `SelfMode: Permission activity closed immediately or failed.` did not appear on either device (the Activity route fails before reaching the trampoline on the success path, same as R3).
- **D-HU video `fed=0` after ~16 s:** the AA connection and SSL held the full 90 s with zero disconnect. Gearhead simply stopped feeding video, and the phone-side log shows `GH.PreflightNotifListnr: Permission polling timed out waiting for permission to be accepted` (twice), i.e. Gearhead was waiting for a permission notification to be accepted, which never happened because the run is unattended. Not a Self Mode teardown, and not defect 2.

## R5 - with the dummy VPN actually up

**PASS** (candidate) / **INCONCLUSIVE** (baseline).

- Device D-HU, already offline (`Active default network: none`), so the R5 precondition held without disabling any radio.
- Precondition met on every run: `VpnControl: Starting DummyVpnService (GitHub Build, excludeSelf=false)` + `DummyVpnService: tun established (excludeSelf=false)`, and the `Network` handed to Gearhead's `CAR.SETUP.SERVICE.LITE` as `PARAM_SERVICE_WIFI_NETWORK`.
- **Candidate** (R3 D-HU capture): tun established 10:24:08.432, **0** `releasing the dummy VPN` / `Dummy VPN stopped` across the full 90 s. PASS.
- **Baseline** (R4 D-HU capture): tun established 10:36:07.496, **0** release lines across 90 s. The brief's expected "release line lands about 2.5 s in" did not happen: the baseline connected in 1.24 s and beat its own watchdog, so the teardown never fired. Same INCONCLUSIVE reason as R4.

## R6 - D-MOTO's Gearhead error screen

**PASS** on both arms.

- Watched `dumpsys activity activities | grep -i "CarErrorDisplay"` across the R3 (candidate) and R4 (baseline) D-MOTO captures.
- `CarErrorDisplay` / `CarErrorDisplayActivityImpl` count = **0 on the candidate, 0 on the baseline.** D-MOTO Self Mode formed and held a 90 s session on both arms.
- Neither of round 1's two readings is confirmed: the error screen did not occur at all. Because the baseline connected in 1.44 s and never tripped its own 2.5 s watchdog, this run cannot definitively rule out the "network vanishing under Gearhead" reading, but the practical result stands: **D-MOTO Self Mode is testable and healthy on the candidate.** Residual question routed to a `session-vpn-lever` follow-up if it matters.

## R7 - the timeout still reports when a launch genuinely fails (positive control)

**PASS**, matches the brief's spec exactly.

- Device D-HU, candidate. Radio offline.
- **Method (4 attempts, see below).** Legacy Self Mode connects in ~1 s on this build, so a single Gearhead force-stop lets it reconnect before the 30 s deadline. The working method: `pm disable-user --user 0 com.google.android.projection.gearhead` at +3 s, then `pm enable` at +27 s, so Gearhead is available (enabled, not connected) when the permission trampoline fires at +30 s. Restored to enabled after the run.
- Discard-rule: `createGroup SUCCESS`=1 (mode-3 artifact), `MATCH!`=0, SSL=0 (no session, expected).

| Condition | Evidence |
|---|---|
| 1. `SelfMode: nothing connected within 30000ms of the launch` at ~30 s, not 2.5 s | 10:31:54.185, i.e. **+30.09 s** after `Launching AA Wireless Startup` (10:31:24.100) |
| 2. `SelfMode: Failed, timed out!` follows | 10:31:54.191 |
| 3. `AA permissions request took <N> ms` then `AA's permissions are in order; the launch timed out for another reason.` and **not** `could not be started` | `SelfMode: AA permissions request took 316 ms` then `SelfMode: AA's permissions are in order; the launch timed out for another reason.` (10:31:54.606); `could not be started` absent |
| 4. still no `releasing the dummy VPN`, no `Self Mode disconnected. Not restarting.` | both absent from the whole capture |

- Defect 3's fix is visible here: a sub-second permission-activity return (`316 ms`) is read as success, not failure.
- **Attempts 1 to 3 (documented, not the result):**
  - Attempt 1, force-stop Gearhead once at +3 s: Gearhead reconnected ~1 s later and the session formed and held 48 s. INCONCLUSIVE per the brief's own R7 fallback.
  - Attempt 2, `pm disable-user` Gearhead for the whole wait: its permission activity then does not resolve, producing `could not start AA's permission activity: Unable to find explicit activity class {...RequestManifestPermissionsActivity}` then `AA's permission activity could not be started.` This is the wrong sub-case, an artifact of disabling the package.
  - Attempt 3, force-stop Gearhead every 2 s: a kill landed on a just-connected session, giving `releasing the dummy VPN (owner=SELF_MODE, reason=SESSION_ENDED)` then `Self Mode disconnected. Not restarting.` at +1.4 s. This is a legitimate peer-gone teardown (`reason=SESSION_ENDED`), not defect 2's disconnect-on-timeout.

## R8 - the 17.4+ route is unaffected

**PASS**

- Device D-POCO, candidate. Same settings/procedure as R3. Radio offline. Did **not** touch `127.0.0.1:5277` by hand.
- Discard-rule: `createGroup SUCCESS`=0, `MATCH!`=0, SSL=1. Clean.
- `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...` 10:32:26.945.
- First attempt failed: `SelfLauncherV17_4.run | SelfMode: Headunit Server (127.0.0.1:5277) is NOT running.` then `SelfMode: Launch of 'v17.4+' failed` then `SelfMode: All launchers failed` (10:32:29.840). **Recovered ~9 s later** via `NetworkDiscovery: Found Headunit Server on 0.0.0.0:5277` then `Auto-connecting to Headunit Server at 0.0.0.0:5277 (reusing socket)`, then `SSL handshake complete` 10:32:39.602.
- Session formed: throughput from 10:32:48, ~29 to 45 fps, `dropped=0`, held 90 s.
- **No** teardown lines, **no** `nothing connected within`, **no** `Failed, timed out!`.
- Same transient-first-failure-then-recovery as round 1. Dummy VPN came up (D-POCO offline), a deviation from the brief's "no VPN on the 17.4+ route" which assumes an online device; not a fail.

## Report-back answers

1. **R1**, seconds to `MainActivity` resumed / app-owned `logcat` with capture off:
   - candidate: D-HU 0.8 s / no, D-POCO 1.0 s / no, D-MOTO <1 s / no
   - baseline: D-HU never during the poll / **yes** (pid 21095, dialog held), D-POCO 1.0 s / **yes** (silent, `logdr: UID=10268`), D-MOTO <1 s / no
2. **R3 against R4**, elapsed `Launching AA Wireless Startup` to `Incoming connection detected`:
   - D-HU: candidate **1352 ms**, baseline **1244 ms**
   - D-MOTO: candidate **2033 ms**, baseline **1443 ms**
   - baseline reaches `Incoming connection detected` on both, always under 2.5 s
3. **R5**: tun established on both arms; held the full 90 s on candidate and on baseline, no `releasing the dummy VPN` on either
4. **R6**: D-MOTO gave neither reading, `CarErrorDisplay` = 0 on both arms
5. **R7**: `nothing connected within 30000ms` at 10:31:54.185 (+30.09 s); neither teardown line appeared anywhere in the capture
6. **R2**: PASS. **R8**: PASS.

Also: `wifi-connection-mode` did not move on any device during any Self Mode attempt (D-HU=3, D-POCO=1, D-MOTO=2). The log-access consent state did not change under us mid-round (D-HU stayed granted; the dialog fires regardless).
