# pr-1046-video-retirement, round 2 results

**Candidate:** PR head b749525d merged onto main 7102b428, tree b0efa0bd, merge commit 7e34e9667       **Baseline:** main @ 7102b4283666ffcc7802e49402735e3958cd51a0
**APK md5:** candidate d7c48f24fc56ff67fb615164cfa45a39 / baseline 20838f1a13f02572253b0de1e52ab745
**Unit:** D-HU (UNISOC MT50, Android 14) with D-POCO (Gearhead 17.9.664004-release)
**Date:** 2026-10-07

## Setup notes

- **Quirk files read:** `rig-quirks/topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`.
- **Pre-flight:** `rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_MOTO D_SAM` printed PREFLIGHT OK (D-HU, D-POCO, D-MOTO, D-SAM on adb, screens awake). An earlier pre-flight failed because D-MOTO was not on adb; the operator reconnected it and the round started after the second, passing run.
- **Bonds:** D-POCO bonded list holds `Navegadortz2` (D-HU, count 1) and `Navegadortz3` (D-SAM, count 1). No pairing request was needed. `OTHER` was set to D-MOTO, so `phone_air_on` switched its radios off.
- **Builds:** both arms built from detached worktrees under `build_hur_cool.sh` (thermal guarded), via `pr_r2_build_all.sh`. The baseline APK from the earlier smoke round was not on disk, so baseline was rebuilt: md5 20838f1a... (the earlier build was 62ccbb85...; debug builds are not byte-reproducible, tree e96aa72a... matches). Trees checked: baseline e96aa72a2ff5, candidate b0efa0bdacfc, both as the brief states. Merge had no conflict.
- **R0 test count:** the brief's R0 text says 2752 candidate tests; its section 1 table says 2836. The candidate ran 2836 tests, 0 failures, 0 errors (baseline 2820, 0, 0). Graded against the table.
- **Identity:** `r0-identity.txt` (DEX greps). Candidate: detachSurfaceIfCurrent=3, SurfaceRecoveryState=3, surfaceStopGeneration=1, outputPublicationLock=1, detachCurrentSurface=0. Baseline: 0, 0, 0, 0, detachCurrentSurface=2. `ACTION_QUERY_STATE` commit: baseline `7102b4283666`, candidate `7e34e96674e5`.
- **Starting state:** shared_prefs owner `u0_a176:771`; `use-aac-audio` false, `audio-latency-multiplier` 8, `audio-queue-capacity` 20, `enable-audio-sink` false, `kill-on-disconnect` false (as found); `SYSTEM_ALERT_WINDOW: default`; `wm density` Physical 240, no override; clocks agree to 1 s.
- **Scripts:** `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh`, `ptr_crash.py`, `ohu_pair.py` copied as the brief says; `v2_lib.sh`, `v2_returns.py`, `v2_reconnect.py`, `v2_phone.py` extracted from the brief with the new `extract_brief_blocks.py`; `pr1046_arm.sh` (session script, added) runs one arm; `pr_r2_build_all.sh` (added) built all arms. Folder `pr-1046-round2/`.
- **Session script deviation:** the brief runs each run as separate calls; `pr1046_arm.sh` ran each arm as one background script under the rig lock. A leftover thermal watcher subshell from `th_gate` kept the lock after arm B and had to be killed by pid before arm C started (harmless to the data; arm C's first launch attempt exited at once on the held lock and ran no commands).
- **Re-runs:** none. Every run executed once, no `RETRY`, no `SETUP_FAIL`.
- **Thermal:** every run `throttle_delta=0`, `thermal_max` at most 69C.
- **V9 metric note (brief bug):** the brief grades `off_cycles_with_activity_stop` of at least 8 from `Decoder stopped: activity_stopped`. On the candidate that line does not exist for SURFACE; the candidate logs `Decoder stop (activity_stopped) skipped: activity has no owned surface` (20 of 20 cycles) and the decoder is stopped by `surfaceDestroyed` (20) and `screen_off_sleep` (10). `extractor` therefore reads 0 on C and 10 on B. Teardown still reached 20 of 20 cycles on C (`cycles_with_teardown` 20). V9-C is graded on the teardown evidence and flagged here for the operator's veto.
- **Final step:** settings restored from `settings-backup-HU.xml` with `video-profile-starvation-cap` deleted (count 0); the restored file differs from the backup only in empty-element serialisation (`<string name="x"></string>` vs `<string name="x" />`). `wm density` reads Physical 240, no override. No logcat left (`ps` count 0).

- **Evidence:** release `rig-evidence-pr-1046-video-retirement`, asset `pr-1046-video-retirement-round2-captures.zip`, sha256 `194f0af5dc8671c9c289386f6aa419dff58dc3d5f27a58ae54eea6fcab9a13c5` (captures, phone captures, returns/reconnect/phone/crash JSON, pair JSON; APKs left out).

## R0 - gate

**PASS**

- Trees and merge-base match; candidate 2836 tests, 0 failures; two md5s differ; identity greps as above. (`SurfaceRecoveryStateTest`, `VideoOutputEventsTest`, `VideoOutputRetirementTest`, `VideoSurfaceRetirementTest` presence was not separately listed; the JUnit totals match the brief's table.)

## V1G-B

**PASS**

- Executed cleanly (baseline arm, no verdict on the branch). Thermal: thermal_max=67C throttle_delta=0

## V1G-C

**PASS**

- Thermal: thermal_max=65C throttle_delta=0
- Settings written: COMMON plus `view-mode=2`, `video-codec=H.264`, log-level 2
- Discard-rule check: clean (0 `MATCH! Starting AapService`, 0 `createGroup SUCCESS`, 0 `Magic Garbage`, 0 second SSL, both arms)
- RET (`V1G.pair.json`): FAIL_a, FAIL_c, FAIL_d empty, FAIL_b_fires false, void empty. Crash counts 0. PH: `critical_error` 0 in all 8 windows, both arms.
- All 8 returns are B1 returns (kept surface, decoder stopped before resume), so the round 1 path is reached on every return. Repair is the focus cycle line at 0.94 to 1.09 s after `onResume` (`rearm_lines` 0, `cycle_lines` 1), T 1.70 to 2.01 s on screen-off returns.

| ret | route | T B (s) | T C (s) | kept (C) | stopped before resume (C) | cycle line rel resume B/C (s) | re-arm lines B/C | nudges B/C |
|---|---|---|---|---|---|---|---|---|
| 1 | S | 1.91 | 1.93 | True | 2 | 1.06 / 1.05 | 0 / 0 | 0 / 0 |
| 2 | S | 1.81 | 1.71 | True | 2 | 1.01 / 1.01 | 0 / 0 | 0 / 0 |
| 3 | S | 1.85 | 1.81 | True | 2 | 1.04 / 1.02 | 0 / 0 | 0 / 0 |
| 4 | S | 1.83 | 1.87 | True | 2 | 0.99 / 1.07 | 0 / 0 | 0 / 0 |
| 5 | S | 1.88 | 1.81 | True | 2 | 1.03 / 1.04 | 0 / 0 | 0 / 0 |
| 6 | S | 1.87 | 2.01 | True | 2 | 1.01 / 1.09 | 0 / 0 | 0 / 0 |
| 7 | H | 1.75 | 1.89 | True | 1 | 0.98 / 0.96 | 0 / 0 | 0 / 0 |
| 8 | H | 1.78 | 1.7 | True | 1 | 0.96 / 0.94 | 0 / 0 | 0 / 0 |


## V1H-B

**PASS**

- Executed cleanly (baseline arm, no verdict on the branch). Thermal: thermal_max=68C throttle_delta=0

## V1H-C

**PASS**

- Thermal: thermal_max=65C throttle_delta=0
- Settings written: COMMON plus `view-mode=2`, `video-codec=H.265`
- Discard-rule check: clean, both arms. RET empty, crash 0, PH 0 in all 8 windows both arms.
- All 8 returns are B1 returns; cycle line 0.93 to 1.04 s after `onResume`, T 1.67 to 1.85 s.

| ret | route | T B (s) | T C (s) | kept (C) | stopped before resume (C) | cycle line rel resume B/C (s) | re-arm lines B/C | nudges B/C |
|---|---|---|---|---|---|---|---|---|
| 1 | S | 1.84 | 1.77 | True | 2 | 1.06 / 0.98 | 0 / 0 | 0 / 0 |
| 2 | S | 1.86 | 1.84 | True | 2 | 0.99 / 1.01 | 0 / 0 | 0 / 0 |
| 3 | S | 1.86 | 1.77 | True | 2 | 1.04 / 1.0 | 0 / 0 | 0 / 0 |
| 4 | S | 1.83 | 1.85 | True | 2 | 1.02 / 1.02 | 0 / 0 | 0 / 0 |
| 5 | S | 1.92 | 1.82 | True | 2 | 1.06 / 1.0 | 0 / 0 | 0 / 0 |
| 6 | S | 1.84 | 1.79 | True | 2 | 1.06 / 1.04 | 0 / 0 | 0 / 0 |
| 7 | H | 1.82 | 1.71 | True | 1 | 0.95 / 0.93 | 0 / 0 | 0 / 0 |
| 8 | H | 1.77 | 1.67 | True | 1 | 0.95 / 0.94 | 0 / 0 | 0 / 0 |


## V2T-B

**PASS**

- Executed cleanly (baseline arm, no verdict on the branch). Thermal: thermal_max=68C throttle_delta=0

## V2T-C

**PASS**

- Thermal: thermal_max=58C throttle_delta=0
- Settings written: COMMON plus `view-mode=1`, `video-codec=H.264`
- Discard-rule check: clean. RET empty, crash 0, PH 0 in all 8 windows.
- Every return is a kept-surface return with a stopped decoder. Each has `rearm_lines` 1 at 0.30 to 0.31 s after `onResume` (inside 0.2 to 1.5 s) and `cycle_lines` 1. `cb_after_rearm` is 0 on all 6 screen-off returns on both arms: no same-surface callback arrived after a re-arm, so this run could not have shown a second grant either way. Unsolicited gains logged: 0 (`nudges` 0).

| ret | route | T B (s) | T C (s) | kept (C) | stopped before resume (C) | cycle line rel resume B/C (s) | re-arm lines B/C | nudges B/C |
|---|---|---|---|---|---|---|---|---|
| 1 | S | 2.06 | 1.95 | True | 2 | 1.16 / 1.15 | 1 / 1 | 0 / 0 |
| 2 | S | 2.05 | 2.01 | True | 2 | 1.16 / 1.16 | 1 / 1 | 0 / 0 |
| 3 | S | 1.93 | 1.98 | True | 2 | 1.15 / 1.15 | 1 / 1 | 0 / 0 |
| 4 | S | 2.1 | 1.95 | True | 2 | 1.15 / 1.15 | 1 / 1 | 0 / 0 |
| 5 | S | 2.01 | 1.97 | True | 2 | 1.16 / 1.15 | 1 / 1 | 0 / 0 |
| 6 | S | 1.97 | 2.01 | True | 2 | 1.15 / 1.15 | 1 / 1 | 0 / 0 |
| 7 | H | 1.99 | 1.93 | True | 1 | 1.15 / 1.15 | 1 / 1 | 0 / 0 |
| 8 | H | 1.9 | 2.02 | True | 1 | 1.15 / 1.15 | 1 / 1 | 0 / 0 |


## V2S-B

**PASS**

- Executed cleanly (baseline arm, no verdict on the branch). Thermal: thermal_max=63C throttle_delta=0

## V2S-C

**PASS**

- Thermal: thermal_max=57C throttle_delta=0
- Settings written: COMMON plus `view-mode=0`, `video-codec=H.264`
- Discard-rule check: clean. RET empty, crash 0, PH 0 in all 5 windows. `double_arm` false on every return; `cycle_lines` 0 on every return on both arms.
- Reached SURFACE teardown on every return (`kept_surface` false, `surfaceDestroyed` then `New surface`), T 0.37 to 0.50 s on C, 0.42 to 0.55 s on B. `lifecycle_stops_after_last_new_surface` 0.

| ret | route | T B (s) | T C (s) | kept (C) | stopped before resume (C) | cycle line rel resume B/C (s) | re-arm lines B/C | nudges B/C |
|---|---|---|---|---|---|---|---|---|
| 1 | S | 0.43 | 0.47 | False | 1 | None / None | 0 / 0 | 0 / 0 |
| 2 | S | 0.55 | 0.5 | False | 1 | None / None | 0 / 0 | 0 / 0 |
| 3 | S | 0.43 | 0.42 | False | 1 | None / None | 0 / 0 | 0 / 0 |
| 4 | H | 0.42 | 0.47 | False | 0 | None / None | 0 / 0 | 0 / 0 |
| 5 | H | 0.47 | 0.37 | False | 0 | None / None | 0 / 0 | 0 / 0 |


## V5-B

**PASS**

- Executed cleanly (baseline arm, no verdict on the branch). Thermal: thermal_max=69C throttle_delta=0

## V5-C

**PASS**

- Thermal: thermal_max=65C throttle_delta=0
- Settings written: COMMON plus `view-mode=2`, `video-codec=H.264`
- Discard-rule check: clean, `second_ssl_or_garbage` 0 on all 8 returns both arms. Crash 0, PH 0 in all 8 windows.
- F returns (1 to 3): `decoder_stopped` empty, `codec_init` 0, `cycle_lines` 0, `throughput_max_gap_s` 5.0 (limit 7.5) on both arms. H returns (4 to 6): RET empty. D returns (7, 8): `onResume` present, T 1.90 and 1.91 s (B 2.12, 2.13), `lifecycle_stops_after_last_new_surface` 0. `wm density` read back after the run: `Physical density: 240`, no override, same as the starting state.

| ret | route | T B (s) | T C (s) | kept (C) | stopped before resume (C) | cycle line rel resume B/C (s) | re-arm lines B/C | nudges B/C |
|---|---|---|---|---|---|---|---|---|
| 1 | F | None | None | True | 0 | None / None | 0 / 0 | 0 / 0 |
| 2 | F | None | None | True | 0 | None / None | 0 / 0 | 0 / 0 |
| 3 | F | None | None | True | 0 | None / None | 0 / 0 | 0 / 0 |
| 4 | H | 1.87 | 1.82 | True | 1 | 0.96 / 0.95 | 0 / 0 | 0 / 0 |
| 5 | H | 1.82 | 1.75 | True | 1 | 0.97 / 0.96 | 0 / 0 | 0 / 0 |
| 6 | H | 1.79 | 1.78 | True | 1 | 0.96 / 0.95 | 0 / 0 | 0 / 0 |
| 7 | D | 2.12 | 1.9 | False | 0 | 1.28 / 1.08 | 0 / 0 | 0 / 0 |
| 8 | D | 2.13 | 1.91 | False | 0 | 1.26 / 1.07 | 0 / 0 | 0 / 0 |


## V8-B

**PASS**

- Executed cleanly (baseline arm). Thermal: thermal_max=67C throttle_delta=0. The baseline shows the defect signature on all 3 cycles: it reconnects (SSL 3.6 to 4.9 s after the verb) but has no `Codec initialized:`, no picture and 0 frame windows in the 60 s after SSL, and 0 first-frame nudges.

## V8-C

**PASS**

- Thermal: thermal_max=69C throttle_delta=0. Settings: COMMON plus `view-mode=2`, H.264.
- All 3 cycles reach the mechanism (verb 1, received 1, overlay 1, finishing 0, destroyed 0, `spent_before` 2) and reconnect. Per cycle: `codec_init_after_ssl_s` 3.41 to 3.43 (limit 15), `picture_after_ssl_s` 3.45 to 3.47 (limit 15), 11 frame windows in 60 s (limit 3), phone `car_state` 6 in each cycle window. Each cycle logged one first-frame nudge at 3.05 s after SSL, 0.4 s before the codec initialised; the baseline logged none and never streamed.
- The returns inside V8 are report only. Phone `critical_error` per window is the same on both arms (1 in each r-S window at 2, 4, 6; 2 in each cycle window), reported, not graded for V8.

| arm | cycle | verb/received/overlay/finishing/destroyed | spent_before | ssl_after_go (s) | codec_init_after_ssl (s) | picture_after_ssl (s) | frame windows in 60 s | first-frame nudges (after ssl s) | phone car_state |
|---|---|---|---|---|---|---|---|---|---|
| B | 1 | 1/1/1/0/0 | 2 | 3.63 | None | None | 0 | 0 (None) | 6 |
| B | 2 | 1/1/1/0/0 | 1 | 4.03 | None | None | 0 | 0 (None) | 6 |
| B | 3 | 1/1/1/0/0 | 1 | 4.87 | None | None | 0 | 0 (None) | 6 |
| C | 1 | 1/1/1/0/0 | 2 | 3.61 | 3.42 | 3.46 | 11 | 1 (3.05) | 6 |
| C | 2 | 1/1/1/0/0 | 2 | 4.5 | 3.41 | 3.45 | 11 | 1 (3.05) | 6 |
| C | 3 | 1/1/1/0/0 | 2 | 3.54 | 3.43 | 3.47 | 11 | 1 (3.06) | 6 |

## V9-B

**PASS**

- Executed cleanly (regression guard). 20 cycles, 1 segment, 0 crash segments. `fatal_count` 0, `aborting_count` 0, `died_count` 0, `race_count` 0, `early_init_cycles` 0, `home_cycles_with_detach_stop` 10, `off_cycles_with_activity_stop` 10. PH: `critical_error` 0 in all 20 windows. Thermal: thermal_max=65C throttle_delta=0.

## V9-C

**PASS**

- 20 cycles, 1 segment, 0 crash segments. `fatal_count` 0, `aborting_count` 0, `died_count` 0, `fatal_exception_ours` 0, `race_count` 0, `early_init_cycles` 0, `cycles_with_teardown` 20, `home_cycles_with_detach_stop` 10. PH: `critical_error` 0 in all 20 windows. Thermal: thermal_max=68C throttle_delta=0.
- **`off_cycles_with_activity_stop` reads 0, not 8 or more.** See the Setup note: the candidate prints `AapProjectionActivity.onStop | Decoder stop (activity_stopped) skipped: activity has no owned surface` 20 times (for example 21:02:56.071) in place of the old stop line, and the 10 screen-off cycles still stop the decoder with `screen_off_sleep`. By the letter of the brief this condition is not met; on the evidence the cycles reached teardown 20 of 20 and nothing crashed. Verdict given on the evidence, for the operator to confirm.

## OL

**PASS**

- Prefix: `throughput_prefix.prefixed` equals `total` in every candidate capture (V1G 23/23, V1H 23/23, V2T 23/23, V2S 11/11, V5 25/25, V8 53/53). The baseline carries the same prefix in every capture too (from the stack walk), so the sample is the same on both arms.
- Cadence: observed over expected windows 158/158 = 1.000 on C, 121/121 = 1.000 on B.
- Report only: `Codec exception in output thread` 4 on V5-B, 0 everywhere else on both arms; `Decoder stall detected` 0 and `Forcing restart (` 0 on every run, both arms.

## Anything the brief did not ask about

- The baseline (`main` at 7102b428) reproduces the V8 stall on this rig: after a session end with a spent latch, the phone reconnects and sets up video but nothing streams, 0 of 3 cycles. The candidate restores the stream in 3 of 3 cycles at 3.4 s after SSL with one nudge. So the defect round 1 found on the old candidate exists on main, and this candidate fixes it.
- The arms ran in about 26 minutes each, not the brief's 65.
- `th_gate` starts a thermal watcher on every call and never stops the last one; it kept the rig lock after the session script exited. Worth fixing in `ptr_lib.sh`.
