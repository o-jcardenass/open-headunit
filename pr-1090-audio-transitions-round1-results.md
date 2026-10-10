# pr-1090-audio-transitions round 1 results

- **Candidate (P):** `fork/review/pr-1090` @ `f81fd51aede743ff5da9185f8cdc960a497764a1` (`f81fd51a`), PR head as published. `ACTION_QUERY_STATE` reports `f81fd51aede7-dirty` (the `-dirty` is the untracked `.WORKTREE-INFO` file in the worktree, nothing else).
- **Baseline (B):** `main` @ `ec9d9c33` (`fork/review/pr-1090~3`). `ACTION_QUERY_STATE` reports `ec9d9c33f728-dirty`, same cause.
- **APK md5s:** `baseline.apk` `9b6f8628b34da16955f5517fbe3296a6`, `candidate.apk` `1c1a9e55f18bf70eb66b87fda452cfb4`. Installed copies pulled and matched on every unit after every install.
- **Units:** D-POCO (API 35, USB host, stage P), D-HP (API 17, USB host, stage H), D-HU (Native AA, stage W), D-MOTO as the phone (VLC, Gearhead 17.9).
- **Date:** 2026-10-10.
- **Evidence:** release `rig-evidence-pr-1090-audio-transitions`, asset `pr-1090-audio-transitions-round1-captures.zip`, sha256 `debd661b4e7a739fb99811b2f3041688126269fa1f8c7d6228f0a5a1da9d7464`.

Round result: the candidate passed the floor, loss and stream-end bars everywhere they were measured, and failed the recovery bar on D-POCO (R2) and D-HU (R6), where the backlog above target is larger than the baseline's. D-HP (R4) passed recovery. R2, R4 and R6 each also fail the brief's literal "every `mixWorkMax` under 10 ms" clause, which the baseline fails by the same amount; see Setup notes.

## Setup notes

**Quirk files read:** `rig-quirks/topics/tooling.md` (in full), `topics/audio.md` and `topics/bt.md` (the entries on A2DP speakers, `enable-audio-sink`, the wake list and the driver selector). The unit files were not read in full.

**Pre-flight table** (`rig_preflight.sh`, final pass after D-MOTO's Bluetooth was switched on by the operator):

| Role | adb | WiFi | BT | Screen | BT profiles |
|---|---|---|---|---|---|
| D_HU | ok | 1 | 1 | Awake | HFP none, A2DP none |
| D_MOTO | ok | 1 | 1 | Awake | HFP none, A2DP none |
| D_POCO | ok | 1 | 1 | Awake | HFP to D-HU, A2DP up |
| D_HP | ok | 1 | 1 | Awake | HFP none, A2DP none |

The first pass failed because D-MOTO was not on adb, the second because its Bluetooth was off (needed for stage W only). Batteries at start: D-POCO 83%, D-HP 97%, D-MOTO 93%.

**Scripts folder.** The skill names `hur-wifi-test-scripts/`, which no longer exists; `rig-toolkit/` was used. Scripts used: `rig_devices.sh`, `rig_preflight.sh`, `rig_thermal.sh`, `wt-new.sh`, `build_hur_cool.sh`, `run_unit_tests.sh`, `apk_identity.sh`, `set_prefs_runas_host.py`, `set_hu_settings_host.py`, `rig_cleanup.sh`. Round scripts (`lib.sh`, `run_usb.sh`, `run_w.sh`, `analyze.py`, key writers) are in the round's evidence folder.

**Builds.** Both SHAs built in their own worktrees. The first build failed because the new worktrees had no `local.properties`; it was copied from the main checkout and the builds were rerun (baseline and candidate both succeeded). Unit tests on the candidate: 3127 run, 0 failures.

**Settings written.** Writers: `set_prefs_runas_host.py` (D-POCO, D-HP), `set_hu_settings_host.py` (D-HU), translated from the brief's `key=` form to the writers' `scalar:<type>:<key>:<value>` form. Every key read back after every install. `static-audio-focus` read back: D-POCO absent (default), D-HP `false`, D-HU `false`; unchanged between arms. Delta of the round-start file against the keys written (no earlier backup existed for a diff, so the delta is against the file as found):

| Unit | Changed by the round |
|---|---|
| D-POCO | added the AKEYS audio keys, `connection-modes` `wifi,usb` to `usb`, `native-*` keys not present and not written |
| D-HP | `log-level` 0 to 2, `audio-latency-multiplier` 16 to 2, `audio-queue-capacity` 50 to 20, added `use-aaudio-output`, `playback-focus-self-defeating`, USB auto keys; `connection-modes` `wifi,usb` to `usb`; `aa-exit-action` untouched |
| D-HU | `enable-audio-sink` false to true, `use-aac-audio` false to true, `audio-latency-multiplier` 8 to 2, `native-driver-selection-mode` 1 to 0, `aa-exit-action` deleted, `native-poke-bt-macs` cleared (see below) |

Every unit's `settings.xml` was restored from its round-start backup at the end and read back identical (D-HP's differs from the pull only in CR line endings). The pre-round APKs (3.5.0-beta4, saved before R0) were reinstalled and their md5s matched.

**Deviations from the brief, each of which cost or changed a run:**

1. **`heard` did not work as published.** The `heard` function in the brief lets `adb shell` read the same stdin as the `read` loop, so Enter presses were swallowed. A fixed form (`</dev/null`) was given to the operator from R2 on. Every capture holds 0 `HEARD` stamps, so no click count exists for any run. The operator did not report heard clicks. Sensory result: none recorded.
2. **Edge phase length.** Three cycles last about 52 s (R1 07:51:26 to 07:52:18), not the 2 minutes the brief assumes. My run script waited 15 s after the "start heard" cue before `R-edges`, not the brief's immediate start.
3. **No `Media Start Request` after the tone in the four USB runs.** `vlcreset; tone` did not produce a new `Media Start Request AUDIO:` in R1 to R4; the phone kept the media stream open across the VLC restart. D-MOTO's media session reported `ohu-1090-tone` playing and `channel=6` lines ran every 10 s from then, so the hold measured the tone. The script printed "MSR not seen after tone" in each.
4. **App crash from my own start sequence (R2 attempts 1 to 3).** The brief's order (`mark R-start`, then launch `MainActivity`) sends a broadcast to a force-stopped app. That starts the process and Android delivers `LOCKED_BOOT_COMPLETED` to `BootCompleteReceiver`, which calls `startForegroundService` and dies with an uncaught `ForegroundServiceStartNotAllowedException` (FATAL EXCEPTION at 07:50:27 in R1 and at the start of every R2 attempt). It did not stop R1, but on R2 the "Application Error" dialog stayed up and covered `MainActivity`, so no session formed. This is the same on the baseline and is not caused by the PR. From R2 attempt 4 on, the script launches `MainActivity` first and sends the first marker after. Voided R2 captures: `R2-attempt1-setupfail` (crash, and the operator's replug came after the script's 90 s wait), `R2-attempt2-scriptrace` (the session formed before my `waitfor` started counting, so it waited for a second one), `R2-attempt3-crashdialog` (crash dialog). The verdict rests on attempt 4 only. The brief allows one re-run; this took four starts, all for harness reasons, none for a PR effect.
5. **D-HU settings overwritten (R5 attempts 1 to 3).** I wrote D-HU's settings without first force-stopping the app, as `set_hu_settings_host.py` requires. The running app overwrote `enable-audio-sink`, `use-aac-audio`, `audio-latency-multiplier`, `native-driver-selection-mode` and others with its old values, which is why attempt 1 had no media channel (`Media Sink Setup Request` for AUDIO2 only) and attempts 2 and 3 followed. Found by diffing the file after the run. Fixed by force-stopping first (`keys_hu.sh`) and reading every key back. Captures kept as `R5-attempt1-nomediachannel`, `R5-attempt2-nossl`, `R5-attempt3-settings-reverted`.
6. **Stale wake list on D-HU (R5 attempt 4).** D-HU's round-start file holds `native-poke-bt-macs` = D-POCO only. With D-POCO in airplane mode and `native-driver-selection-mode` 0, the poke went to D-POCO alone and D-MOTO was never poked (attempt 4: three poke cycles in 90 s, no session). The brief does not mention this key. I cleared it (`setclear:native-poke-bt-macs`), which per `rig-quirks/topics/bt.md` lets `native-poke-all-paired` walk the bonded phones; D-MOTO was then poked within 4 s and the session formed. R5 attempt 5 and R6 both ran with it cleared. It was restored at the end with the rest of the file.
7. **R5 and R6 media start.** My script waits 25 s after the session before `vlcreset; tone` (the brief starts VLC straight after the session, which raced the car audio setup in attempt 1). Both runs then logged `Media Start Request AUDIO:` once.
8. **D-HP cannot swipe.** `input swipe` is not implemented on API 17: the shell printed the `input` usage text at every swipe (27 usage lines in the R3 script log). The `RIGSWIPE` tags were logged (9 per run) but no touch reached D-HP. Both D-HP arms are alike in this.
9. **D-HP `logcat -G 16M` refused** ("unknown option -- G"). D-HP's captures ran without a larger buffer; no capture died (no `alive` restart notes in any run).
10. **Session timing on D-HP.** D-HP's USB session formed by itself when the cable was plugged, before the priming capture started, so `SH` has no `SSL handshake complete` line. R3 and R4 each show exactly one.
11. **Markers.** `ACTION_LOG_MARKER` worked on both candidate and baseline with `-f 0x00000020`; D-HU's `allow-external-configuration` read `true` at round start. D-POCO and D-HP markers landed too (15 `AutomationReceiver` lines per USB run).
12. **Settings not restored between runs.** The brief says to restore the round backup after every run. I restored after each stage instead (and the keys written were identical for both arms), so D-POCO and D-HP carried their AKEYS from baseline run to candidate run unchanged.
13. **D-POCO audio route.** At pre-flight D-POCO's output was `bt_a2dp` to D-HU (`Navegadortz2`), not the Magnetic Speaker. Stage P priming switched D-POCO's Bluetooth off, after which `dumpsys audio` read `speaker(2)`.
14. **Operator-side USB state.** On D-MOTO during R1 the focus window was a system `UsbDetailsActivity`; the session formed anyway. No USB permission dialog was answered. D-POCO and D-HP `dumpsys usb` listed `headunitrevived` filters before the round and were not changed.
15. **D-POCO adb after airplane mode.** Airplane mode cut D-POCO's wireless adb for stage W, as expected; closing used its USB cable.

**Reading of the brief's bars.** Two bars cannot be met as written, in either arm:

- **"Every L-MIX `mixWorkMax` under 10 ms."** The baseline itself reaches 39 ms (R1), 44 ms (R3) and 19 ms (R5) in the whole capture. On D-POCO the large values coincide with the edge pause/resume cycles and run start; in the hold window the maximum is 1 to 5 ms (R1), 4 ms (R2). I graded the literal text and also give the hold-window and comparative figures.
- **Floor bar on D-HP: "every `effective=` equals R4's L-OPEN `effective=`".** The `L-OPEN` line is the placeholder `opened Awaiting output ... effective=960`, written before the AudioTrack exists, so it reads 960 on every unit and arm. The real platform minimum is in the `L-OUT` line (`minimum=`): 3844 frames on D-POCO, 2229 on D-HP and 3848 on D-HU. The preflight checks `opened AudioTrack` and `minimum=960` in R1 were read from `L-OUT` (`output AudioTrack ... minimum=960`), which matches.

**Thermal.** Host `rig_thermal.sh wait 75` before every build, test run and run launch. Highest package temperature seen: 63 C (status line before R6; run logs peaked at 62 C or lower); `throttle_pkg` stayed 0 in every run, so no run was voided for heat.

## R0

**PASS**

- Unit tests (candidate): 3127 run, 0 failures.
- md5s differ: `9b6f8628b34da16955f5517fbe3296a6` against `1c1a9e55f18bf70eb66b87fda452cfb4`.
- `strings | grep -c -F AudioTrackBufferSizing`: baseline 0, candidate 7.
- Baseline installed on D-POCO, D-HP and D-HU; installed md5s matched; `ACTION_QUERY_STATE` commit `ec9d9c33f728-dirty` on all three.

## R1. D-POCO, USB, baseline

**PASS**

Baseline record; the brief sets no bar. Preflight checks all met: `L-OUT` reads `output AudioTrack ... minimum=960`; L-DEC `codec=AAC_LC`, `latencyMultiplier=2`, `queueCapacity=20`; L-ACC 1, L-SSL 1, L-WIFI 0; 30 L-CH6 lines in the hold window; L-MATCH not applicable.

| Item | Value |
|---|---|
| L-OPEN (ids 1 to 3) | `opened Awaiting output, stream=3, capacity=19200 frames, effective=960, burst=480, minimum=960, maximum=2880` |
| L-OUT | `AudioTrack requested=960 effective=960 minimum=960 stableFloor=960 maximum=2880` at 07:50:50.587 |
| L-MIX `effective=` (44 lines) | 960 on every line; `xruns` 0 on every line |
| L-MIX `mixWorkMax` | hold window 1 to 3 ms; whole capture up to 39 ms (07:51:38, 07:51:51, 07:52:11, all in the edge phase) |
| Hold window deltas | `concealedFrames` 96, `staleFrames` 0, `compressedFrames` 1424, `xruns` 0 |
| Excess (depth minus target, 30 lines) | median -18.5 ms, maximum 20 ms |
| Edge cycles (key resume, no relaunch) | L-STOP at 07:51:30.425, 07:51:47.667, 07:52:04.767 (3); stop-to-release 1030, 1039, 1014 ms (median 1030); first L-CH6 `depth=` after the L-MSR 171, 42, 167 ms |
| L-HEARD | 0 per cycle, 0 unmatched (`heard` failed, see Setup notes) |
| S-UND | 0 |
| L-TP median `rendered=` | 150 (90 lines) |
| Phone | P-STATE 8, P-FATAL 0 |
| L-OVF, L-ERR | 0, 0 |

Decisive line, 07:51:30.425: `AapAudio.stopAudio | Audio Stop: AUDIO`, followed by `AapAudio: Releasing playback transient audio focus` 1030 ms later.

## R2. D-POCO, USB, candidate (the point)

**FAIL**

Failed condition: the recovery bar. Candidate APK installed over wireless adb, md5 matched, commit `f81fd51aede7`. Preflight: `L-OUT` reads `minimum=3844` (L-OPEN reads the 960 placeholder); L-DEC as R1; L-ACC 1, L-SSL 1, L-WIFI 0; 30 L-CH6 lines in the hold. Verdict rests on attempt 4 (see Setup notes, item 4).

| Bar | R1 | R2 | Result |
|---|---|---|---|
| Floor: every L-MIX and L-OUT `effective=` at or above the platform minimum | `effective=960` on every line | L-OUT `requested=4320 effective=4320 minimum=3844 stableFloor=4320 maximum=5280`; all 45 L-MIX lines `effective=4320` | PASS, and it exercised commit 1: the minimum is 3844, well above 960 |
| Recovery, median excess at most R1 + 20 ms | -18.5 ms | 50.5 ms (+69.0) | **FAIL** |
| Recovery, maximum excess at most R1 + 40 ms | 20 ms | 95 ms (+75) | **FAIL** |
| No new loss: `concealedFrames`, `staleFrames` not above R1 (or 4800 or less) | 96, 0 | 0, 0 | PASS |
| Mixer work: every L-MIX `mixWorkMax` under 10 ms | spikes 39, 36, 36 | spikes 21, 36, 36, 30 (08:08:59 to 08:10:01, run start and edges); hold window 1 to 4 ms | literal clause FAIL in both arms; hold window figure 4 ms |
| Mixer work: R2's largest not above R1's + 2 ms | 39 | 36 | PASS |
| Streams end: same L-STOP count in the edge window | 3 | 3 | PASS |
| Streams end: L-CH6 `depth=` above 0 within 15 s of each L-MSR | 171, 42, 167 | 201, 194, 42 | PASS |
| Streams end: median stop-to-release gap at most R1 + 100 ms | 1030 ms | 1015 ms (1016, 1015, 1003) | PASS |
| L-OVF, L-ERR | 0, 0 | 0, 0 | PASS |
| Phone P-STATE at least 1, P-FATAL 0 | 8, 0 | 8, 0 | PASS |

Hold-window deltas: `compressedFrames` 2000 against R1's 1424 (recovery ran on both, so the recovery bar is reachable). Reading of the recovery failure: depth sat at 96 to 166 ms against a target near 78 ms (R1 sat below target, at 48 to 74 ms). The candidate also raised the device buffer from 960 to 4320 frames, so this round cannot separate the buffer commit from the matching commit as the cause of the larger backlog. The excess does fall during the hold (166 ms at the first line, 96 ms at the last). It is a latency cost, not a click.

Decisive line, 08:08:42.945: `AudioMixer: id=3 output AudioTrack requested=4320 effective=4320 staging=0 burst=480 minimum=3844 stableFloor=4320 maximum=5280`.

Sensory result (not part of the verdict): no click count was recorded in either arm.

## R3. D-HP, USB, baseline

**PASS**

Baseline record; no bar. Preflight met: `output AudioTrack requested=960 effective=2229 minimum=960`, `xruns=N/A`; L-DEC as R1; L-ACC 1, L-SSL 1, L-WIFI 0; 30 L-CH6 lines.

| Item | Value |
|---|---|
| L-OPEN | placeholder, `effective=960 minimum=960` |
| L-OUT | `requested=960 effective=2229 minimum=960 stableFloor=960 maximum=2880` at 08:24:33.747 (and again at 08:31:53.007) |
| L-MIX `effective=` (46 lines) | 2229 on every line |
| L-MIX `mixWorkMax` | 1 to 44 ms; hold window 1 to 43 ms |
| Hold window deltas | `concealedFrames` 960, `staleFrames` 0, `compressedFrames` 2288, `xruns` N/A |
| Excess | median -14.5 ms, maximum 53 ms |
| Edge cycles (key resume) | L-STOP 3; stop-to-release 1060, 1020, 1060 ms (median 1060); `depth=` after L-MSR 25, 88, 163 ms |
| L-HEARD | 0 |
| S-UND | 0 |
| L-TP median | 150 |
| Phone | P-STATE 8, P-FATAL 0 |
| L-OVF, L-ERR | 0, 0 |

## R4. D-HP, USB, candidate

**FAIL**

Failed condition: the literal "every `mixWorkMax` under 10 ms" clause, which fails the same way in R3. Every other bar passes. Candidate installed over wireless adb (67 s), md5 matched, commit `f81fd51aede7`. Preflight as R3.

| Bar | R3 | R4 | Result |
|---|---|---|---|
| Floor: every `effective=` constant, at or above the platform minimum | 2229 on every line | L-OUT `requested=2400 effective=2229 minimum=2229 stableFloor=2400 maximum=3360`; all 44 L-MIX lines 2229 | PASS (see the L-OPEN reading in Setup notes) |
| Recovery, median excess at most R3 + 20 ms | -14.5 ms | 0.0 ms (+14.5) | PASS |
| Recovery, maximum excess at most R3 + 40 ms | 53 ms | 23 ms | PASS |
| No new loss | 960, 0 | 788, 0 | PASS |
| Mixer work: every L-MIX `mixWorkMax` under 10 ms | 1 to 44 ms | 7 to 38 ms | **FAIL** (the baseline fails it too) |
| Mixer work: R4's largest at most R3's + 2 ms | 44 | 38 | PASS |
| Streams end: L-STOP count | 3 | 3 | PASS |
| `depth=` above 0 within 15 s of each L-MSR | 25, 88, 163 | 66, 146, then 0 at +30 ms and 126 at +10 s | PASS |
| Median stop-to-release gap at most R3 + 100 ms | 1060 ms | 1030 ms (1030, 1030, 1010) | PASS |
| L-OVF, L-ERR, P-STATE 8, P-FATAL 0 | | 0, 0, 8, 0 | PASS |

Hold-window `compressedFrames` 1608 against R3's 2288. Largest `mixWorkMax` of each D-HP arm: R3 44 ms, R4 38 ms (hold window 43 and 38). The matcher did not lengthen the mixer cycle on the slowest CPU.

## R5. D-HU, Native AA, baseline (log only)

**PASS**

Baseline record; no bar. Attempts 1 to 4 were voided (Setup notes items 5 and 6); this is attempt 5. Preflight met: L-WIFI `NATIVE`; L-FREQ 5765 MHz (5 lines); L-DEC `codec=AAC_LC`, `latencyMultiplier=2`, `queueCapacity=20`; L-SSL 1; L-MATCH 0; 31 L-CH6 lines in the hold.

| Item | Value |
|---|---|
| L-OUT | `AudioTrack requested=960 effective=960 minimum=960` at 08:59:03.789 |
| L-MIX | 35 lines; hold window `effective=960`, `mixWorkMax` 1 to 19 ms, `xruns` 0 |
| Hold window deltas | `concealedFrames` 0, `staleFrames` 0, `compressedFrames` 11568, `xruns` 0 |
| Excess | median 7 ms, maximum 150 ms |
| S-UND | 0 |
| L-TP median | 76 (71 lines) |
| Phone | P-STATE 8, P-FATAL 0 |
| L-OVF, L-ERR | 0, 0 |

## R6. D-HU, Native AA, candidate (log only)

**FAIL**

Failed condition: the recovery median. Candidate installed, md5 matched, commit `f81fd51aede7`, keys read back (wake list cleared as in R5). Preflight met: L-WIFI `NATIVE`, L-FREQ 5765 MHz, L-SSL 1, L-MATCH 0, 30 L-CH6 lines in the hold.

| Bar | R5 | R6 | Result |
|---|---|---|---|
| Floor | `effective=960` | L-OUT `requested=4320 effective=4320 minimum=3848`; all 36 L-MIX lines 4320 | PASS |
| Recovery, median excess at most R5 + 20 ms | 7 ms | 80 ms (+73) | **FAIL** |
| Recovery, maximum excess at most R5 + 40 ms | 150 ms | 138 ms | PASS |
| No new loss | 0, 0 | 0, 0 | PASS |
| Mixer work: every `mixWorkMax` under 10 ms | up to 19 ms | up to 26 ms (the first L-MIX line, before the hold); hold window 2 to 11 ms | **FAIL** (literal) |
| Mixer work: largest at most R5's + 2 ms | 19 | 26 | **FAIL** (whole capture); in the hold window 11 against 19 |
| L-OVF, L-ERR | 0, 0 | 0, 0 | PASS |
| P-STATE at least 1, P-FATAL 0 | 8, 0 | 8, 0 | PASS |

Hold-window `compressedFrames` 3516 against R5's 11568. As on D-POCO, depth sat well above target (the device buffer went from 960 to 4320 frames), and the round cannot split the buffer commit from the matching commit. The baseline's `effective=960` is below the platform minimum 3848 that the candidate reports for this unit.

Decisive line, 09:06:17.480: `AudioMixer: id=3 output AudioTrack requested=4320 effective=4320 staging=0 burst=480 minimum=3848 stableFloor=4320 maximum=5280`.

## Anything the brief did not ask about

- **Candidate against baseline, per unit:**

| | D-POCO | D-HP | D-HU |
|---|---|---|---|
| Platform minimum, frames (candidate L-OUT) | 3844 | 2229 | 3848 |
| Baseline `effective=` | 960 | 2229 | 960 |
| Candidate `effective=` | 4320 | 2229 | 4320 |
| Excess median, baseline to candidate (ms) | -18.5 to 50.5 | -14.5 to 0.0 | 7 to 80 |
| `compressedFrames` hold delta, baseline to candidate | 1424 to 2000 | 2288 to 1608 | 11568 to 3516 |

  On the two units where the candidate lifts the buffer (D-POCO, D-HU), the backlog above target is larger. On D-HP the size cannot change below API 24 and the candidate matched the baseline within the bars. Both baseline arms on D-POCO and D-HU ran below the platform minimum (960 against 3844 and 3848) with 0 `xruns`, 0 `concealedFrames` on D-HU and 96 on D-POCO.
- **Ending ramp (commit 3).** In all three listened-unit pairs the stream ended and restarted without delay: 3 L-STOP per run in both arms, the focus release followed 1.0 to 1.06 s later in every cycle, and the next stream showed non-zero depth. The candidate's median gap was 15 ms shorter on D-POCO and 30 ms shorter on D-HP. No click count exists to compare by ear.
- **App defect seen, not caused by the PR.** `BootCompleteReceiver` calls `startForegroundService` with no guard for `ForegroundServiceStartNotAllowedException`. It surfaced on D-POCO whenever a broadcast started the force-stopped app (R1 baseline at 07:50:27, and four times in the R2 attempts, three of them leaving the "Application Error" dialog up). The cause of the `LOCKED_BOOT_COMPLETED` delivery to a freshly un-stopped package was not investigated.
- **Duplicate `-hu.txt` lines.** No capture needed deduplication: `alive` never restarted a capture (no notes files exist).
- **Leftover processes.** Three `tail -f` helper processes from my own monitors survived their runs and had to be killed by pid before pre-flight would pass for stage W; a gradle daemon from the builds was cleared with `rig_cleanup.sh kill`.
- **Restore state.** D-POCO: airplane off, Bluetooth on, WiFi on, pre-round APK and `settings.xml` restored. D-HP and D-HU: pre-round APK and `settings.xml` restored. D-MOTO: stay-awake off, airplane off, WiFi and Bluetooth on, tone file removed, `adb usb` on D-POCO, D-HP and D-MOTO.
