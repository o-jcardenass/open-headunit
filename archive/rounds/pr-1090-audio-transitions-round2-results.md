# pr-1090-audio-transitions round 2 results

**Candidate (P):** `fork/review/pr-1090` @ `f81fd51aede743ff5da9185f8cdc960a497764a1`   **Control (C1):** `fork/review/pr-1090~2` @ `528a174125fad9b5d936aca2ce724420e0a630fb`   **Boot fix (F):** `fork/fix/boot-service-refusal` @ `bb5ff8d5a3f44da0feda0d322fda893bbd58e2cf` (parent `ec9d9c33`)
**APK md5:** `candidate.apk` 1c1a9e55f18bf70eb66b87fda452cfb4 (reused from round 1, md5 as the brief required) / `c1.apk` 1352299257febbbabddb740db7a396dc / `boot.apk` 0f6afadb44a76590372643da706f8647
**Unit:** D-POCO (POCO X3 NFC, Android 15, 2400x1080) as USB-host head unit, D-MOTO (Android 14) as phone on D-POCO's OTG port, both on wireless adb
**Date:** 2026-10-10

## Setup notes

**Quirk files read:** `rig-quirks/topics/tooling.md`, `topics/audio.md`, `topics/lifecycle.md` (first 120 lines), `units/D-POCO.md`, `units/D-MOTO.md`.

**Pre-flight** (`rig_preflight.sh D_POCO:wifi D_MOTO:wifi`):

| Role | adb | WiFi | BT | Screen | BT profiles |
|---|---|---|---|---|---|
| D_POCO | ok | 1 | 1 | Awake | HFP to D-HU, A2DP up |
| D_MOTO | ok | 1 | 1 | Awake | none |

Batteries at start: D-POCO 62% (63% at R0), D-MOTO 100%. D-POCO's output was `bt_a2dp`; `svc bluetooth disable` made `dumpsys bluetooth_manager` read `enabled: false` and `dumpsys audio` read `speaker(2)`. Restored with `svc bluetooth enable` at the end (`enabled: true`).

**Scripts folder.** The skill names `hur-wifi-test-scripts/`, which is now `rig-toolkit/`. Used: `rig_devices.sh`, `rig_preflight.sh`, `rig_thermal.sh`, `wt-new.sh`, `build_hur_cool.sh`, `run_unit_tests.sh`, `apk_identity.sh`, `set_prefs_runas_host.py`, `restore_settings.sh`. Round scripts in the evidence folder: `lib.sh`, `keys_usb.sh`, `run_usb.sh`, `analyze.py` (copied from round 1, paths changed) and a new `run_r3.sh`. No script was added to `rig-toolkit/`.

**Builds.** Worktrees for `528a1741` and `bb5ff8d5` via `wt-new.sh`, `local.properties` copied in, built one after the other under the thermal gate, each APK copied out at once. Unit tests on the `boot.apk` worktree: 3111 tests, 0 failures (summed from the JUnit XML; the run exited 0).

**Settings.** D-POCO `settings.xml` backed up before R0; the diff against round 1's round-start backup is empty (0 changes). The brief's keys were written with the app force-stopped and read back after the `c1.apk`, `candidate.apk` and `boot.apk` installs (the readback after `candidate.apk` and `boot.apk` was a grep of the keys; all 15 keys were present with the brief's values after `boot.apk`). `static-audio-focus` and `auto-start-on-boot` are absent in every run. Final file restored from the backup and read back byte-identical. The pre-round APK (3.5.0-beta4, md5 `9a103bdc05d45d05cab1610c93f23f42`) was reinstalled and its md5 matched.

**Deviations from the brief, each of which cost or changed something:**

1. **Cables moved before wireless adb existed.** I did not run `adb tcpip 5555` before the operator moved the cables, so both units dropped off adb. The operator re-plugged them, I ran `tcpip 5555` on both (D-POCO 192.168.1.8, D-MOTO 192.168.1.5), `logcat -G 16M` on both, and the cables were moved again. About 10 minutes lost, no effect on any run.
2. **R1 attempt 1 voided (setup).** The app auto-started when the cable was plugged (`auto-start-on-usb`), so the session formed before the capture began and the capture had no `SSL handshake complete` (the L-SSL preflight check could not pass). I stopped it after about 80 s, ended the run per section 6, and re-ran once. Kept as `R1-attempt1-presession/`. The verdict rests on attempt 2. R2 and R3 formed their sessions after the capture started.
3. **`D-MOTO svc power stayon true` was not set.** D-MOTO was awake and unlocked at every run start (focus on a launcher, no PIN prompt); the brief's pre-flight item was skipped, so there is nothing to undo at the end.
4. **Launching `MainActivity` before the first marker** (brief section 3) held in R1 and R2. Before each, the script pressed HOME on the phone; the first focus check failed three times in R1 attempt 1 only because the projection was already in front.
5. **Edge phase** took about 52 s (R1 10:29:11 to 10:30:05), as the brief expected. The script waits 15 s after the "start heard" cue before `R-edges`.
6. **`ACTION_QUERY_STATE` commits** read `528a174125fa-dirty`, `f81fd51aede7-dirty` and `bb5ff8d5a3f4-dirty`; the `-dirty` suffix was also there in round 1 and does not change the SHA prefix.
7. **Grep windows.** Captures were read with `grep -a` after removing duplicate lines (`awk '!seen[$0]++'`, inside `analyze.py`). No `alive` restart note exists in any run, so no capture died.
8. **R1 is graded PASS as a control.** It has no bar. Its preflight checks all passed (below); I used PASS rather than inventing a fifth verdict.

**Thermal.** `rig_thermal.sh wait 75` before each build, test run and run launch. Peak package temperature in the run logs: 64 C; `throttle_pkg` stayed 0 in every run.

## R0

**PASS**

- Unit tests (`boot.apk` worktree): 3111 run, 0 failures.
- md5s all differ (above). `c1.apk` and `boot.apk` installed md5 matched the built file (`apk_identity.sh` MATCH).
- `strings | grep -c -F`: `AudioTrackBufferSizing` `c1.apk` 7, `candidate.apk` 7, `boot.apk` 0. `PcmOverlap` `c1.apk` 0, `candidate.apk` 3, `boot.apk` 0. `Android refused to start AapService` `boot.apk` 1, `c1.apk` 0, `candidate.apk` 0.
- `ACTION_QUERY_STATE` commit on `c1.apk`: `528a174125fa-dirty`.

## R1. D-POCO, USB, C1 (control)

**PASS** (control, no bar; preflight checks all met)

- Settings: brief section 5 keys, read back.
- Preflight: L-OUT `10:28:35.405 ... AudioMixer: id=3 output AudioTrack requested=4320 effective=4320 staging=0 burst=480 minimum=3844 stableFloor=4320 maximum=5280 xruns=0`. L-DEC `codec=AAC_LC, ... latencyMultiplier=2, queueCapacity=20`. L-ACC 1, L-SSL 1 (10:28:33), L-WIFI 0, L-CH6 lines in the hold window 31 (27 or more needed).
- L-MIX: 45 lines, every `effective=` 4320. `mixWorkMax=` hold window largest 3 ms; rest of the capture largest 38 ms (run start and edges), values 37, 38, 37 ms.
- Hold-window deltas: `concealedFrames` 0, `staleFrames` 0, `compressedFrames` 1664, `xruns` 0.
- Excess (`depth` minus `target`): median 49 ms, maximum 73 ms (31 lines).
- L-OVF 0, L-ERR 0, S-UND 0, L-TP median `rendered=` 150.0 (90 lines), L-AR 15.
- Edge cycles:

| Cycle | L-STOP after pause | Stop-to-release gap | Resume | L-MSR | CH6 `depth=` after L-MSR |
|---|---|---|---|---|---|
| 1 | 3783 ms after pause (10:29:15.467) | 1033 ms | key | yes | 190 ms |
| 2 | 3784 ms after pause (10:29:32.544) | 1054 ms | key | yes | 157 ms |
| 3 | 3801 ms after pause (10:29:49.651) | 1040 ms | key | yes | 42 ms |

  Median stop-to-release gap 1040 ms. No `relaunch` marker (all three resumed by key).
- After `vlcreset; tone`: no `Media Start Request AUDIO:` came (script: `10:30:40 MSR not seen after tone`), as in round 1. The hold measured the tone anyway (CH6 lines every 10 s).
- Phone: P-STATE 8, P-FATAL 0.
- Heard stamps (4): `click-1` 10:29:18.342 unmatched (375 ms after cycle 1's window closed), `click-2` 10:29:30.961 matched cycle 2, `click-3` 10:29:47.896 matched cycle 3, `click-4` 10:30:04.891 unmatched. Matched per cycle: 0, 1, 1. Unmatched: 2.

**Attribution check (no bar):** R1 median excess 49 ms against round 1 R1's -18.5 ms (`ec9d9c33` at 960 frames): +67.5 ms. That is near the 70 ms the larger buffer explains.

## R2. D-POCO, USB, candidate (the point)

**PASS**

- Settings: as R1, read back after the install. `ACTION_QUERY_STATE` commit `f81fd51aede7-dirty`; installed md5 `1c1a9e55f18bf70eb66b87fda452cfb4` MATCH.
- Preflight: L-OUT `output AudioTrack requested=4320 effective=4320 ... minimum=3844 stableFloor=4320 maximum=5280`; L-DEC `AAC_LC`, `latencyMultiplier=2`, `queueCapacity=20`; L-ACC 1, L-SSL 1 (10:37:40), L-WIFI 0, L-CH6 hold-window lines 30 (27 or more).
- Bars:

| Bar | R1 (C1) | R2 (P) | Result |
|---|---|---|---|
| Same buffer: L-OUT `effective=` | 4320 | 4320 | met |
| Floor: every L-MIX/L-OUT `effective=` at or above `minimum=` | 4320 against 3844 | 4320 against 3844 | met |
| Excess median (limit R1 + 20) | 49 ms | 55.5 ms (+6.5) | met |
| Excess maximum (limit R1 + 40) | 73 ms | 92 ms (+19) | met |
| `concealedFrames` / `staleFrames` delta | 0 / 0 | 0 / 0 | met |
| `compressedFrames` delta (reachability: R1 not 0) | 1664 | 2064 | reached |
| Hold-window largest `mixWorkMax=` (under 10 ms and at most R1 + 2) | 3 ms | 3 ms | met |
| L-STOP lines in the edge window | 3 | 3 | met |
| CH6 `depth=` 15 s after L-MSR (above 0 ms) | 190 / 157 / 42 | 201 / 42 / 171 | met |
| Median stop-to-release gap (limit R1 + 100) | 1040 ms | 1023 ms (gaps 1004, 1058, 1023) | met |
| L-OVF / L-ERR | 0 / 0 | 0 / 0 | met |
| Phone P-STATE / P-FATAL | | 8 / 0 | met |

- Whole-capture `mixWorkMax=` reaches 39 ms in R2 (21, 37, 39, 34 at run start and edges) and 38 ms in R1; both arms spike alike, outside the hold window.
- L-OUT count: 1 per run. S-UND 0. L-TP median 150.0 (88 lines). After `vlcreset; tone` no `Media Start Request AUDIO:` came (`10:39:46 MSR not seen after tone`), the hold measured the tone.
- Edge cycles: stops 3781, 3909, 3809 ms after the pause; L-REL gaps 1004, 1058, 1023 ms; all three resumed by key with an L-MSR.
- Heard stamps (3): `click-1` 10:38:49.694 unmatched, `click-2` 10:38:54.035 matched cycle 3, `click-3` 10:39:11.034 unmatched. Matched per cycle: 0, 0, 1. Unmatched: 2.

Matched recovery repays about as well as the old recovery at the same buffer: the 70 ms rise round 1 saw came from the buffer, not from commit 2 (the attribution check above gives +67.5 ms for the buffer alone). Sensory result, not part of the verdict: R1 (silence at stream end) 4 stamps, R2 (5 ms ramp) 3 stamps; each run has one stamp inside a cycle window, so the ramp is not audibly different on this evidence.

## R3. D-POCO, boot fix, broadcast to a stopped app

**PASS**

- Installed `boot.apk` md5 `0f6afadb44a76590372643da706f8647` MATCH; `ACTION_QUERY_STATE` commit `bb5ff8d5a3f4-dirty`; 15 keys read back.
- L-BOOT 2 (the trigger happened): `10:46:18.450 BootCompleteReceiver.onReceive | Boot auto-start: received action=android.intent.action.LOCKED_BOOT_COMPLETED` and `10:46:18.463 ... received action=android.intent.action.BOOT_COMPLETED`. R3-start was sent at 10:46:20 by the script log; the broadcast landed after the capture started.
- L-REFUSED 2: `10:46:18.458 W/OPENHU BootCompleteReceiver.startService | Boot auto-start: Android refused to start AapService (trigger=android.intent.action.LOCKED_BOOT_COMPLETED): startForegroundService() not allow...` and the same for `BOOT_COMPLETED` at 10:46:18.469. The system side: `10:46:18.456 ActivityManager: startForegroundService() not allowed due to mAllowStartForeground false: service com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService`.
- S-RCV 0, S-FATAL 0, `FATAL EXCEPTION` 0 in the whole capture.
- Step 5 focus line (10:46:30): `mCurrentFocus=Window{e5fbe05 u0 com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher}`, not an Application Error dialog.
- First `AapService` line after R3-start before step 6: none from the app; the only `AapService` lines are the refusal above.
- L-SSL 1 (10:46:50), P-STATE 8 on the phone.

## Anything the brief did not ask about

- **The R1 pre-session trap.** With `auto-start-on-usb` true and D-MOTO already on the OTG port, plugging the cable starts the app and the session before any capture exists. A brief for this pairing should say to start the capture first, or to force-stop the app after the cable move.
- **The ramp costs nothing the instruments see.** R2's `compressedFrames` delta is 400 frames higher than R1's (2064 against 1664) and its median excess 6.5 ms higher; both are well inside the bars and the run to run scatter between R1's own cycles (depth 42 to 190 ms) is larger.
- **The boot refusal is real on this unit and the fix absorbs it.** Both boot actions arrived within 13 ms of each other and both were refused by Android; the receiver logged and carried on, and the later `MainActivity` launch formed a session at once.
- Captures: `pr-1090-audio-transitions-round2-captures.zip` in the release `rig-evidence-pr-1090-audio-transitions` (sha256 8513868c4ada2160028c07ee4b7176e4a2dffd9c351558a87016d298cf0df4b7).
