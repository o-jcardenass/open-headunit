# post-beta1-self-mode — round 1 results

**Candidate:** `fork/fix/post-beta1-self-mode-and-log-probe` @ `8bc6fcce`   **Baseline:** `origin/main` @ `71930d54`
**APK md5:** `16688ed590b827e2ae6e30621cc8ae54` (candidate) / `028ebb088df5a734e0b0680fcb780b1e` (baseline)
**Units:**
- **D-HU** — UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14 / API 34, Gearhead `17.3.662864-release`, `READ_LOGS` granted=true
- **D-POCO** — POCO X3 (`M2007J20CG`), Android 15 / API 35, Gearhead `17.5.663204-release`, `READ_LOGS` granted=false
- **D-MOTO** — Motorola edge 30 neo (`motorola edge 30 neo`), Android 14 / API 34, Gearhead `17.3.662854-release`, `READ_LOGS` granted=false
**Date:** 2026-08-27

## Summary

| Run | Verdict | One line |
|---|---|---|
| R0 | **PASS** | candidate 770/0, baseline 765/0, exact match to brief |
| R1 | **PASS** | candidate never spawns `logcat` on any device, MainActivity resumes ~1 s; baseline reproduces the `LogAccessDialog` on D-HU (and hangs the app indefinitely unattended) |
| R2 | **PASS** | candidate's `HUR_Log` capture still works — 6.9 MB file, `LogExporter: session \|` present |
| R3 | **PASS** (D-HU + D-MOTO) | legacy Self Mode forms in 1.25 s / 1.44 s, no teardown, no timeout, no toast, no defect-3 |
| R4 | **INCONCLUSIVE** for the defect-2 mechanism | baseline connects on **both** devices (1.51 s / 0.79 s), always beating the old 2500 ms watchdog, so the teardown never fires here |
| R5 | **PASS** (candidate) / **INCONCLUSIVE** (baseline) | dummy VPN up (`excludeSelf=false`), tun held for the whole session on the candidate; baseline never released it either (same reason as R4) |
| R6 | **PASS** both arms | `CarErrorDisplayActivityImpl` did not appear on D-MOTO on either arm |
| R7 | **PASS** | `SelfMode: nothing connected within 30000ms of the launch` at +30.2 s (not 2.5 s), `permissions are in order`, no teardown |
| R8 | **PASS** | D-POCO 17.4+ route forms and holds 65 s, no teardown, no timeout-abort (one transient first-attempt failure that auto-recovered) |

**Shipping question:** defect 1 (log probe) and defect 3 (inverted permission-trampoline test) are **fixed and confirmed on hardware**. Defect 2 (the 2500 ms watchdog that disconnects) **could not be exercised on this rig** — loopback Self Mode connects in 0.8–1.6 s on all three devices, so the baseline never trips its own 2.5 s bug. That coverage rests on `SelfLaunchTimeoutPolicyTest` (the +5 JVM tests counted in R0) and on the candidate's observable behaviour (R3: no disconnect; R7: 30 s deadline, no disconnect-on-timeout), both consistent with the fix.

## Setup notes

**Every device was offline for the entire round.** `dumpsys connectivity` on all three read `Active default network: none` from the first check onward; D-HU's WiFi was `DISCONNECTED` / `<unknown ssid>`. `TESTING-TEMPLATE.md` §7a states D-HU is "permanently joined to `Pegue Cdesta`, 5500 MHz" — it was not during this round. Consequence: **every device brought up the dummy VPN for Self Mode** (`HomeFragment.startSelfMode` guards on `activeNetwork == null`), so R3 and R8 exercised the VPN path too, not just R5. This is more VPN coverage than the brief anticipated, not less. The rig's network state was not changed by this session (no authorization to).

**R0 ran on the PC under a thermal cap.** The PC (an old Sony VAIO) hard-power-cut twice during the earlier attempt at this brief — confirmed thermal: the 08:27 boot's `kern.log` starts with `ACPI: thermal: Thermal Zone [THRM] (74 C)`, i.e. it restarted while hot; idle package temp is ~60 °C on a cold boot. Both APKs were already built (Aug 26, at the exact SHAs the brief pins) so only `testGithubDebugUnitTest` was re-run, throttled to 2 physical cores (`taskset -c 0,2`, `--max-workers=2`) with a temperature watchdog. Peak package temp 85 °C, no thermal event. Candidate 770 tests / 0 failures, baseline 765 / 0 — exact match to the brief. The unit needs its heatsink cleaned/repasted before it runs full-core builds again.

**The candidate branch was force-pushed since the brief was written** — `8bc6fcce` → `82df5d33` (15 files, +105/−70). This round followed the brief's pinned SHA `8bc6fcce` for both the APK and the unit-test run. The moved tip was not evaluated.

**Scripts.**
- Used existing: `r1_launch_probe.sh` (R1, already present from the earlier attempt), `set_prefs_runas.sh` (settings writes), `restore_settings.sh` (settings restore), `build_hur.sh`/`run_unit_tests.sh` equivalents for R0 (run directly with the throttle wrapper).
- **Added `hur-wifi-test-scripts/selfmode_session_probe.sh`** — the R3/R4/R5/R7/R8 session probe: clean-starts via a plain `MainActivity` launch (the `auto-start-self-mode` route that reaches `HomeFragment.startSelfMode` → `VpnControl.startVpn`), captures full logcat with `stdbuf -oL`, holds for `HOLD` seconds, prints the decisive lines. Options: `KILLGH=<seconds>` (force-stop Gearhead N s after launch, for R7) and `DISMISS_LOGDIALOG=1` (poll for the system `LogAccessDialog` and tap "Don't allow", for baseline D-HU).

**`headunit://exit` is required to flush the `HUR_Log` file; `am force-stop` truncates it to 0 bytes.** The first two R2 attempts produced 0-byte `HUR_Log` files because the probe script ends each run with `am force-stop`, which kills the logcat-to-file pipe before it flushes. Re-running with a clean `headunit://exit` before stopping the capture produced a complete 6.9 MB file. Not a candidate defect — worth a `TESTING-TEMPLATE.md` §7a line.

**The system `LogAccessDialog` on baseline D-HU hangs the app indefinitely when unattended.** The brief describes defect 1 as the probe "blocking in `waitFor()`". On D-HU baseline that is exactly what happens and it does not time out: R4's first attempt sat with `com.android.systemui/.logcat.LogAccessDialogActivity` holding the foreground for **4+ minutes**, with `Activity pause timeout` and `top resumed state loss timeout` logged against `MainActivity` — the app's main thread wedged in `onCreate` before `AppLog` even initialised (zero `OPENHU` lines in 90 s of capture). Only a manual "Don't allow" tap cleared it. In R1 (also baseline D-HU) the same dialog happened to dismiss after ~6 s; behaviour is inconsistent. Every baseline D-HU run in this round used `DISMISS_LOGDIALOG=1` to tap "Don't allow" a few seconds after launch (the one non-scriptable step, per `TESTING-TEMPLATE.md` §0 — a real user dismisses it too). Noted per run.

**The `AapService: dummy VPN requested (owner=SELF_MODE)` line from brief §5 does not appear on this branch.** The SELF_MODE VPN bring-up is logged as `HomeFragment.startSelfMode | Device is offline. Preparing Dummy VPN for Self Mode.` → `VpnControl.startVpn | VpnControl: Starting DummyVpnService (GitHub Build, excludeSelf=false)` → `DummyVpnService.startVpn | DummyVpnService: tun established (excludeSelf=false)`. The `owner=` string is either stale or from a different (AapService-managed) path. The substantive R5 precondition — VPN up, `excludeSelf=false`, tun established, `Network` handed to Gearhead as `PARAM_SERVICE_WIFI_NETWORK` — was met on every offline run.

**`settings.xml` diff vs. a fresh backup at round start: zero on D-HU and D-MOTO.** D-POCO drifted only `last-loc-latitude`/`last-loc-longitude` (the app persisting its last GPS fix). `log-source` stayed `0` and **`wifi-connection-mode` did not move** on any device (D-HU=3, D-POCO=1, D-MOTO=2) — round 1's "normalised to 1 during failed Self Mode attempts" did not recur, checked after R3 and R4.

**A benign single `WifiDirectManager: 5GHz createGroup SUCCESS!` appears in most D-HU Self Mode captures.** D-HU's `wifi-connection-mode=3` (Native AA), so the mode-3 machinery arms alongside Self Mode and creates one P2P group. It never reaches a second `createGroup SUCCESS`, no `MATCH! Starting AapService`, exactly one SSL handshake — not a discard-rule hit (`TESTING-TEMPLATE.md` §7a: the discard is a *second* `createGroup SUCCESS`). D-MOTO (`mode=2`) and D-POCO (`mode=1`) captures show `createGroup SUCCESS: 0`.

## R0 — build and unit-test gate

**PASS**

- `assembleGithubDebug`: both APKs pre-built Aug 26 at the pinned SHAs, `BUILD SUCCESSFUL` in the stored gradle log; md5s recorded above and different.
- `testGithubDebugUnitTest` (re-run this session, throttled): **candidate `8bc6fcce` → 770 tests, 0 failures, 0 errors**; **baseline `71930d54` → 765 tests, 0 failures, 0 errors**. Parsed from `app/build/test-results/testGithubDebugUnitTest/*.xml` (73 files candidate, matching set baseline). The +5 are `SelfLaunchTimeoutPolicyTest`.

## R1 — the log dialog, all three devices (POINT OF THE ROUND, half 1)

**PASS**

- Settings written: none — all three devices already matched brief §4 R1 (`auto-start-self-mode=true`, `auto-connect-last-session=false`, `auto-connect-single-usb=false`, `auto-connect-delay-seconds=0`, `log-level=2`, `log-source=0`, `log-capture-enabled` absent). Confirmed against a fresh backup.
- Radio state: as-is (all devices offline — see Setup notes).
- Discard-rule check: clean on all runs (`createGroup SUCCESS` ≤ 1, no `MATCH!`).

### Candidate — PASS on all three

| Device | launch → `MainActivity` resumed | app-owned `logcat` (`logdr: UID=<app>`) | `LogAccessDialog` | after resume |
|---|---|---|---|---|
| D-HU | 08:49:31.4 → 08:49:32.2 (**~0.8 s**) | **0** | **none** | → `AapProjectionActivity` → Maps `GhostActivity` (full session) |
| D-POCO | 08:50:40.5 → 08:50:41.5 (**~1.0 s**) | **0** | **none** | Gearhead first-run `Material3SettingsActivity`, then back to `MainActivity` (no session — pre-registered not-a-fail) |
| D-MOTO | 08:51:05.0 → 08:51:06.2 (**~1.2 s**) | **0** | **none** | → `AapProjectionActivity` → Maps `GhostActivity` (full session) |

- Corroboration: `grep -a "logdr: UID=<app-uid>"` = 0 on all three candidate captures; live `ps -A | grep logcat` during each run showed only the shell-owned capture process, never an app-owned one; 0 `isLogcatSupported` / `LogExporter` lines.
- **Relaunch check, D-HU candidate, 3× (`log-capture-enabled` absent):** `START ... LogAccessDialogActivity` = 0, app `logdr` = 0, `isLogcatSupported` = 0, `MainActivity` resumed 3/3. "No relaunch produces one" — confirmed.

### Baseline — dialog reproduced on D-HU; not on D-POCO / D-MOTO (pre-registered report)

| Device | `LogAccessDialog` | app spawned `logcat` | launch → `MainActivity` resumed | notes |
|---|---|---|---|---|
| **D-HU** | **yes** — `com.android.systemui/.logcat.LogAccessDialogActivity` held the foreground 08:53:06.685 → 08:53:12.829 (poll frames 3–11), operator visually confirmed | **yes** — `u0_a168 … logcat` process present, poll frames 2–10 | 08:53:04 → **08:53:13.6 (~8.5 s)** | READ_LOGS granted=true and the dialog **still fires** — exactly the brief's point. Unattended (R4 first attempt) it does not dismiss: 4+ min, `Activity pause timeout` on `MainActivity`. |
| D-POCO | no | yes — `logdr: UID=10268` ×1 (silent success, no dialog, no runtime permission) | ~1.0 s | API 35; probe spawns `logcat` and it is allowed without consent UI |
| D-MOTO | no | no evidence in capture (0 `logdr: UID=10491`, 0 poll frames) | <2 s | API 34, READ_LOGS granted=false; the exec appears to be denied instantly with no reader registered |

- Both non-reproducing devices are API ≥ 33, so "below API 33" does not apply; reported per the brief as consent/ROM behaviour, not a candidate pass. The candidate arm stands on its own: **0 app-owned `logcat` on all three.**

## R2 — capture still works on the candidate

**PASS**

- Device: D-HU. Settings written: `log-capture-enabled=true` (via `set_prefs_runas.sh`); `log-level=2`, `log-source=0` unchanged.
- Radio state: as-is (offline).
- Procedure: launch `MainActivity`, tap "Allow one-time access" on the `LogAccessDialog`, let Self Mode run 60 s, `headunit://exit`, then read the app's `HUR_Log`.
- Decisive: `HUR_Log_20260827_091154_453.txt` = **6,905,375 bytes**, contains `LogExporter.startCapture | LogExporter: session | build=3.3.0-beta1 (100) github/debug | device=UNISOC MT50 … | logLevel=INFO` (count 1).
- The `LogAccessDialog` still appears on the candidate when `log-capture-enabled=true` — that feature genuinely spawns `logcat` (unchanged by this branch; the fix was to the `log-source` getter's probe, not to log capture). The session formed (`WirelessServer: Incoming connection detected` 09:08:34.959) **before** the dialog was answered, i.e. `onCreate` was not blocked — the probe is gone.
- First two attempts produced 0-byte `HUR_Log` files because the probe script ends with `am force-stop`; see Setup notes.

## R3 — legacy Self Mode on the candidate, D-HU + D-MOTO (POINT OF THE ROUND, half 2)

**PASS** on both devices.

- Settings written: none (brief §4, already in place). Radio: offline → dummy VPN path.
- Discard-rule check: D-HU `createGroup SUCCESS`=1 (mode-3 artifact, see Setup notes), `MATCH!`=0, SSL=1; D-MOTO `createGroup SUCCESS`=0, `MATCH!`=0, SSL=1. Clean, no re-run.

| Condition | D-HU | D-MOTO |
|---|---|---|
| 1. `SelfMode: AA < 17.4 detected.` | ✓ 09:04:02.047 | ✓ 09:03:59.822 |
| 2. `WirelessServer: Incoming connection detected from /127.0.0.1` + `Throughput over` following | ✓ 09:04:03.307, throughput every 5 s for 90 s, `dropped=0` (`c2.unisoc.hevc.decoder`, ~9–20 fps) | ✓ 09:04:01.467, throughput for 90 s, `dropped=0` (`c2.qti.avc.decoder`, ~29–32 fps) |
| 3. no `releasing the dummy VPN (owner=` / `Self Mode disconnected. Not restarting.` in first 30 s | ✓ absent from whole capture | ✓ absent from whole capture |
| 4. no `SelfMode: nothing connected within` at all | ✓ | ✓ |
| 5. no "Failed to start Android Auto" toast, no `AA's permission activity could not be started.` | ✓ 0 / 0 | ✓ 0 / 0 |
| **elapsed `Launching AA Wireless Startup` → `Incoming connection detected`** | **1252 ms** (09:04:02.055 → 09:04:03.307) | **1439 ms** (09:04:00.028 → 09:04:01.467) |

- Dummy VPN: `tun established (excludeSelf=false)` (D-HU 09:04:01.932, D-MOTO 09:03:59.785), `PARAM_SERVICE_WIFI_NETWORK` handed to Gearhead's `CAR.SETUP.SERVICE.LITE`, **never released** on either device across the 90 s hold.
- The legacy launcher's Activity route logs `SelfMode: Launch of 'v17.3 and older' had caused an error` on both, then `SelfMode: Broadcast fallback 1 (WirelessStartupReceiver) sent.` → `SelfMode: Launch of 'Fallback: Broadcast' had no issues` — the broadcast fallback carries it. No `PermissionTrampolineActivity` line on this path (defect 3 does not manifest when the Activity route fails early).
- `settings.xml` after R3: `wifi-connection-mode` unchanged (D-HU=3, D-MOTO=2).

## R4 — the same on the baseline, the A/B

**INCONCLUSIVE** for the defect-2 mechanism (both devices connect faster than the old deadline).

- Settings: brief §4, restored from backup before the run. Radio: offline. Baseline md5 `028ebb08…` verified live before each capture.
- D-HU used `DISMISS_LOGDIALOG=1` (tapped "Don't allow" at +3.6 s, 09:01:26.369).

| Device | `Launching AA Wireless Startup` → `Incoming connection detected` | teardown lines | session |
|---|---|---|---|
| D-HU | 09:01:28.092 → 09:01:29.602 = **1510 ms** | **none** — no `All launchers failed (timeout)`, no `Self Mode disconnected`, no `releasing the dummy VPN` | held 68 s, `dropped=0` |
| D-MOTO | 08:56:37.538 → 08:56:38.327 = **789 ms** | **none** | held 90 s, `dropped=0` |

- **Both baseline devices beat the 2500 ms watchdog** (`SelfLauncherLegacy` / `CommManager.emitError` path), so the deadline never expires and the teardown that defines defect 2 never fires on this rig. This is the pre-registered "baseline connects too → deadline met by luck" outcome — except it is *both* devices, not just one. `SelfMode: nothing connected within` cannot appear on `71930d54` (string absent). The mechanism's fix rests on `SelfLaunchTimeoutPolicyTest` (R0) and slower real-world setups.
- Dummy VPN came up on both (offline) and was **never released** (no teardown → no `onDisconnected` → no VPN release).
- Defect 3 on the baseline: `SelfMode: Permission activity closed immediately or failed.` did **not** appear on either device — same as R3, the Activity route fails before reaching the trampoline, so the inverted test is never hit on this path.
- **D-HU baseline, first attempt (no dialog dismissal):** the `LogAccessDialog` held the foreground 4+ minutes unattended, `MainActivity` wedged (`Activity pause timeout`, `top resumed state loss timeout`), zero `OPENHU` lines in 90 s — the app never got past `onCreate`. This is defect 1 in its worst form and is the strongest single piece of evidence in the round for why it must be fixed.

## R5 — with the dummy VPN actually up

**PASS** (candidate) / **INCONCLUSIVE** (baseline).

- Device: D-HU, already offline (`Active default network: none`) — the R5 precondition was satisfied without disabling any radio.
- Precondition: `AapService: dummy VPN requested (owner=SELF_MODE)` not present on this branch (see Setup notes); the equivalent `VpnControl: Starting DummyVpnService (GitHub Build, excludeSelf=false)` + `DummyVpnService: tun established (excludeSelf=false)` both appeared on every run, and the `Network` was handed to Gearhead as `PARAM_SERVICE_WIFI_NETWORK`.
- **Candidate** (= R3 D-HU capture): tun established 09:04:01.932, session formed via loopback, **tun never released across the full 90 s** — no `releasing the dummy VPN`, no `Dummy VPN stopped`. PASS.
- **Baseline** (= R4 D-HU capture): tun established 09:01:27.916, **never released across 68 s**. The brief's expected "release line lands about 2.5 s in" did not happen — the baseline connected in 1.51 s and beat its own watchdog, so the teardown never fired. Same INCONCLUSIVE reason as R4.

## R6 — D-MOTO's Gearhead error screen

**PASS** on both arms.

- Watched: `adb shell dumpsys activity activities | grep -i "CarErrorDisplay"` across the R3 (candidate) and R4 (baseline) D-MOTO captures.
- `CarErrorDisplayActivityImpl` / `CarErrorDisplay` count = **0 on the candidate, 0 on the baseline.** Self Mode formed and held a 90-s session on D-MOTO on both arms.
- Round 1's two readings: neither is confirmed. The error screen did not occur at all this round. Because the baseline arm connected in 0.79 s and never tripped its own 2.5 s watchdog, this run cannot definitively rule out the "network vanishing under Gearhead at 2.5 s" reading — but the practical result stands: **D-MOTO Self Mode is testable and healthy on the candidate.** Route the residual question to a `session-vpn-lever` follow-up if it matters.

## R7 — the timeout still reports when a launch genuinely fails (positive control)

**PASS** — matches the brief's spec exactly.

- Device: D-HU, candidate. R3 setup, then `am force-stop com.google.android.projection.gearhead` at +3 s (logged 09:06:45). Hold 48 s. Radio: offline.
- Discard-rule check: `createGroup SUCCESS`=1 (mode-3 artifact), `MATCH!`=0, SSL=0 (no session — expected).

| Condition | Evidence |
|---|---|
| 1. `SelfMode: nothing connected within 30000ms of the launch` at ~30 s, not 2.5 s | ✓ 09:07:14.734, i.e. **30.16 s** after `Launching AA Wireless Startup` (09:06:44.573) |
| 2. `SelfMode: Failed, timed out!` follows | ✓ 09:07:14.740 |
| 3. `AA permissions request took <N> ms` → `AA's permissions are in order; the launch timed out for another reason.` and **not** `could not be started` | ✓ `PermissionTrampolineActivity.onActivityResult \| SelfMode: AA permissions request took 236 ms` (09:07:15.082) → `SelfMode: AA's permissions are in order; the launch timed out for another reason.` (09:07:15.083); `could not be started` absent |
| 4. still no `releasing the dummy VPN`, no `Self Mode disconnected. Not restarting.` | ✓ both absent — the report happens, the teardown does not |

- This is the run that proves the fix kept the upstream author's reporting while removing the disconnect. Defect 3's fix is visible here: a sub-second permission-activity return (`236 ms`) is read as success, not failure.

## R8 — the 17.4+ route is unaffected

**PASS**

- Device: D-POCO, candidate. Same settings/procedure as R3. Radio: offline. Did **not** touch `127.0.0.1:5277` by hand.
- Discard-rule check: `createGroup SUCCESS`=0, `MATCH!`=0, SSL=1. Clean.
- `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...` ✓ 09:04:01.158.
- First attempt failed: `SelfMode: Launch of 'v17.4+' failed` (09:04:04.065) → `SelfMode: All launchers failed` (09:04:04.066). **Auto-retried** ~7 s later: `AA 17.4+ detected` (09:04:10.916) → `SelfMode: Launch of 'v17.4+' had no issues` (09:04:12.484).
- Session formed: throughput from 09:04:21.605, ~29–46 fps, `dropped=0`, held 65 s.
- **No** teardown lines, **no** `nothing connected within`, **no** `Failed, timed out!`.
- Deviation from the brief: a dummy VPN *did* come up (`tun established (excludeSelf=false)` 09:04:00.602) because D-POCO was offline; the brief expected "no VPN is involved on this route" for an online device. Not a fail — the 17.4+ route still formed and held cleanly with the VPN present.

## Anything the brief did not ask about

- **All three devices ran the entire round offline** — see Setup notes. If the rig's WiFi association is supposed to be stable, something dropped it; if a future brief depends on an *online* device it must verify, not assume.
- **`am force-stop` corrupts the app's `HUR_Log` to 0 bytes.** The logcat-to-file pipe is not flushed on SIGKILL. Any round that reads a `HUR_Log` must end the session with `headunit://exit`. Candidate for `TESTING-TEMPLATE.md` §7a.
- **The `LogAccessDialog` on this UNISOC ROM has no reliable timeout.** Seen dismissing in ~6 s (R1) and not at all in 4+ min (R4) in the same session, unattended. `pm grant READ_LOGS` does not suppress it (D-HU has it granted; the dialog fires anyway). D-POCO (Android 15) never shows it and lets the `logcat` exec through with neither the permission nor a consent UI — a third behaviour.
- **D-POCO's 17.4+ Self Mode first attempt reliably fails once and self-recovers on retry** (`Launch of 'v17.4+' failed` → `All launchers failed` → retry succeeds ~7 s later). Consistent with Gearhead's Headunit Server not being ready at first contact after a cold Gearhead start. Worth confirming it is the retry and not luck if the 17.4+ path gets its own round.
- **Legacy Self Mode's Activity launcher fails on both AA-17.3 devices** (`SelfMode: Launch of 'v17.3 and older' had caused an error`), every time, and the broadcast fallback carries it. This means defect 3's code path (`PermissionTrampolineActivity`) is only reached on the 30-s-timeout branch (R7), never on a normal successful connection here — so the inverted-test bug on `main` would only have bitten users whose Activity route *succeeds*.
