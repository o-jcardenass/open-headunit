# pr-1046-video-retirement, round 1 results

**Candidate:** pr/split-video-retirement @ e7c2949c1bd43df22c5d1b94dbacf7e4d3ebf385       **Baseline:** main @ 7e9d813d66493f1cc978c0f6a259bdb613874bd1
**APK md5:** 5dd43efac5a5a0c0f6adb843b685b666 (candidate) / 9516b641ac68b2e89033dca1a947c9cd (baseline)
**Unit:** D-HU (UNISOC MT50, Android 14) with D-POCO; D-SAM (API 19, Android 4.4.2); D-HP (API 17, Android 4.2.2); D-MOTO (V1M, not usable)
**Date:** 2026-10-05

## Round verdict

**FAIL.** B1 reproduced on GLES (H.264 and H.265) on the screen-off return route; it did not reproduce on the Home or launcher routes, which tear the GLES surface down on this unit for both builds. TEXTURE fails the return rule too, but its baseline is also broken on screen-off returns. SURFACE, codec pin, S1 cadence, singleTask, density recreate, overload cadence, fault injection and both old tablets pass. Captures: release `rig-evidence-pr-1046-video-retirement`, asset `pr-1046-video-retirement-round1-captures.zip`, sha256 b128dfc894b156b868d4e556c2f55b5466ee4eb103535352706f7caffbcaad37.

Headline numbers, picture time T in seconds after `AapProjectionActivity: onResume` (999 = none in the 90 s window):

| Run | Return | Route | T baseline | T candidate | Candidate cycle line | Candidate nudges |
|---|---|---|---|---|---|---|
| V1G GLES H.264 | 4 | screen off | 1.75 | 999 | none | 49 |
| V1G | 5 | screen off | 1.83 | 999 | none | 49 |
| V1H GLES H.265 | 4 | screen off | 1.79 | 86.83 | none | 41 |
| V1H | 5 | screen off | 1.71 | 99.03 | none | 47 |
| V1H | 6 | screen off | 1.95 | 7.13 | none | 2 |

## Setup notes

- **Gate (R0):** 2668 tests, 0 failures, 0 errors, 0 skipped, counted from the JUnit XML, including `VideoOutputRetirementTest` and `VideoSurfaceRetirementTest`. Candidate built in a scratch worktree of `e7c2949c` (4 commits on the baseline). Baseline APK is the one built from `7e9d813d` in the previous round (same commit). Identity: `detachSurfaceIfCurrent` 3 and `outputPublicationLock` 1 in the candidate dex, 0 and 0 in the baseline.
- **Identity per run (deviation):** I did not read `commit` from `ACTION_QUERY_STATE`. Before every run the driver pulled the installed APK off the unit and compared its md5 with the expected build, and aborts on a mismatch. Every run below passed that check.
- **Host thermal (new rule this round):** the host PC (i7-3632QM) throttled during the first gradle test and build (peak 94C, package throttle count 6, all during that build). Every run was started below 75C, a thermal log was kept per run (`out/<RUN>.thermal`), and the throttle count stayed at 6 through every run. Hottest run: V3G-B at 80C. The test and build were run once more after cooling; gate numbers are from that run.
- **Starting state:** D-HU `shared_prefs` owner `u0_a176:771`; `SYSTEM_ALERT_WINDOW` default; `user_rotation` read `1` (the unit is not rotated by the operator); `wm density` Physical 240, no override, read back as 240 after V5D and at the end; host and D-HU clocks agree to the second. D-POCO started behind a keyguard and was dismissed with `wm dismiss-keyguard`, then HOME. The settings backup of D-HU is byte-identical to the one taken at the end of the previous round, so the round started with no delta. After each run the writer re-wrote the file from that backup; the final restore differs from the backup only in XML formatting (14 diff lines, the declaration and whitespace).
- **Audio keys as found, not touched:** `use-aac-audio` false (the brief expects it on; deviation, left as found), `audio-latency-multiplier` 8, `audio-queue-capacity` 20, `enable-audio-sink` false, `fps-limit` 60, `screen-orientation` 2. D-HU ran GLES, TEXTURE or SURFACE per run, codec `H.264` (`H.265` in V1H), `resolutionId` 3; the resolution line read `chosen=_1920x1080`, and the phone asked for H.264 (`Media Sink Setup Request: 3`; `7` only in V1H).
- **Tablets:** D-SAM backup: `view-mode` 2 (GLES), codec H.264, `resolutionId` 0, `fps-limit` 60, `screen-orientation` 3. D-HP backup: `view-mode` 0 (SURFACE), codec H.264, `resolutionId` 1, `fps-limit` 30, `screen-orientation` 2. D-SAM battery 99% charging, clock equal to host; D-HP battery 91%.
- **Scripts (in `hur-wifi-test-scripts/pr-1046-round1/`):** the brief's `ohu_lib.sh`, `ohu_setkeys.py`, `ohu_returns.py`, `ohu_pair.py`, with these edits: `su -c` calls removed because D-HU's adb shell is already root and has no `su` binary; `phone_air_on` now switches Wi-Fi and Bluetooth off with `svc` and verifies with `dumpsys`; `session_up` takes the capture line from before the phone's radios come on; `ohu_setkeys.py` gained `set:key=a,b` for string-set keys. Added: `ohu_runs.sh` (the brief's run functions), `ohu_phase.sh` (D-HU driver with per-run md5, thermal wait and a stop on a failed session), `ohu_tab.sh` (D-SAM and D-HP runs with a restarting tag-filtered capture), `grade_all.py` (the tables below), `build.log`, `unittest-cand.txt`.
- **Harness fixes, with the voided attempts kept in `out/failed1`, `out/failed2`, `out/void`:**
  1. The brief's `cmd connectivity airplane-mode enable` does not switch D-POCO's radios off (settings flag 1, Wi-Fi and Bluetooth still ON after 20 s). First V1G-B attempt: the phone connected at once. Replaced with `svc`.
  2. `session_up` started watching after the phone had already connected; V2T-B first attempt failed on it (a session formed with SSL at 15:23:00, before the watch started). Fixed; V1G-B was valid because it won that race.
  3. D-HP (Headunit Server mode) auto-connects in under 3 s; the harness `ACTION_CONNECT` landed on top, the phone's server wedged (accepts, sends nothing) and the operator had to restart it. Script now waits 45 s for the auto-connect and sends the verb only as a fallback. First V7P-B attempt voided.
  4. D-SAM needs `connection-modes` to contain `wifi`; its backup had `{usb}`. The brief's key list does not set it, so `ohu_tab.sh` writes `set:connection-modes=wifi` and checks it before launch.
  5. D-HP's serial is `0123456789ABCDEF`, not the brief's.
- **Voided runs (discard rule):** V1H-B first run: the app process died with `Fatal signal 11 (SIGSEGV)` in thread `VideoDecoder-Fe` at 15:50:17 (return 2, GLES, H.265, baseline build) and a new process formed a second SSL. V1H-C first run: the capture stopped 40 s into the run (last line 17:50:29, no markers); cause not found, it was the first run after the USB hub was plugged in. Both re-run once and clean (V1H-B 19:47 to 19:57, V1H-C 19:41 to 19:47). A baseline SIGSEGV on H.265 is a finding in its own right.
- **Mid-round pause:** the operator plugged a USB hub in after V2S-C; the phase was held between runs for it and resumed at V1H-C.
- **V1M:** D-MOTO is bonded to the head unit's radio (`Navegadortz2`, 11:46:03:10:33:59) but its Gearhead never started a wireless connection (last `GH.WIRELESS` line 13:59); the head unit poked D-POCO instead and the session failed after 150 s. Its last rfcomm attempts named `84:1D:E8:E0:F9:6B`, not the head unit's address. Per the brief the only fix is a Gearhead data clear, which needs approval and was not asked for. V1M is UNTESTABLE for both builds.
- **Premise that did not hold:** the brief says Home never tears down a GLES surface on D-HU. On this unit and both builds, Home (returns 1 to 3) and launcher (7 to 9) returns on GLES logged `Decoder stopped: surfaceDestroyed` and `New surface`; only screen-off returns kept the Surface (`New surface` 0). B1 is therefore reachable here only by screen off.
- **Window deviations:** V6B's start marker is written after the 30 s settle, by which time the injector had spent its budget, so `FAULT INJECTED (#` is 0 inside `win` for both builds and 40 over the whole capture; V6B is graded on the whole capture. V3's `Media Sink Setup Request` lines are logged before the start marker, so the 3 and 7 counts are over the whole capture.
- **Other:** the brief's `shot` helper fails for the same reason on every run (`adb pull -q` is not an option of this adb), so no screenshots were taken; the `ohu_returns.py` `picture_rel_resume_s` reads 0.0 when the picture line precedes `onResume` (Home returns on GLES, picture already up); it is printed as 0.0 below.

## R0

**PASS**

- 2668 tests, 0 failures, md5s differ, both identity greps pass (Setup notes).

## V1G-B

**PASS**

Reference run (not graded alone): 12 returns, one session, 0 extra SSL, 0 `Fatal signal`. T: returns 1 to 3 (Home) 0.0; 4 to 6 (screen off) 1.75, 1.83, 1.83; 7 to 9 (launcher) 1.62, 1.74, 1.73. Cycle line 1.03 to 1.07 s after `onResume` on every H, S and L return. Returns 10 to 12 (night toggles): no stops, 0 codec inits, max throughput gap 5.0 s. Cadence 92 of 98 (0.939). Counts: 6 `Decoder stall detected`, 3 `Codec exception in output thread`.

## V1G-C

**FAIL**

- Settings written: `COMMON` plus `view-mode` 2, codec `H.264`, `night-mode` 1. Radios: both phones switched off with `svc`, D-POCO back on after the app launch. Discard check: clean (0 `MATCH!`, 0 `createGroup`, 0 `Magic Garbage`, 1 SSL).
- Rule (a) fires on returns 4 and 5 (candidate 999 s with the baseline at 1.75 and 1.83); rule (b) fires on the same two (delta 997 s). (c) none, (d) none.
- B1 path reached on returns 4, 5 and 6 (`onSurfaceChanged` after `onResume`, no `New surface`, preceded by `screen_off_sleep`). The candidate logged no cycle line on 4 and 5, 49 `nudges` each (the unsolicited gain), 4 `restart: sync_stall` stops per window, and no picture in 90 s. Return 6 had a picture at `onResume` (0.0).
- Returns 1 to 3 and 7 to 9 match the baseline (Home and launcher recreate the surface on both builds: `surfaceDestroyed`, `New surface`). Returns 10 to 12: F-route checks pass on both builds (no stops, 0 codec inits, no cycle lines, max gap 5.0 s).
- No screenshots exist: the brief's `shot` helper uses `adb pull -q`, which this adb rejects (`unrecognized option '-q'`), so the 1 s, 4 s and 12 s images were never taken; they were evidence for a human only. Counts: 20 `Decoder stall detected`, 0 `Codec exception in output thread`, cadence 122 of 135 (0.904).

## V2T-B

**PASS**

Reference run: 9 returns, one session. T: Home 0.0, 0.0, 0.0; screen off 999, 0.0, 98.46; launcher 0.0, 1.67, 1.57. The baseline already has no picture in 90 s on screen-off return 4 and 98 s on return 6 (47 to 49 nudges).

## V2T-C

**FAIL**

- Rule (b) fires on returns 5 (3.89 vs 0.0) and 6 (999 vs 98.46). Return 4 is 999 on both. (a) none, (c) none, (d) none.
- B1 path not reached on TEXTURE (0 `onSurfaceChanged` after `onResume` on both builds on screen-off returns, so TextureView is not re-reporting its surface). TEXTURE's screen-off returns are broken on both builds, differently: baseline 2 of 3 no picture in 90 s, candidate 2 of 3. Home and launcher returns match the baseline.

## V2S-B

**PASS**

Reference run: 12 returns, T 0.34 to 0.57 s on every return 1 to 9, F returns clean.

## V2S-C

**PASS**

- Return rule: no (a) to (d). T 0.27 to 0.43 s on returns 1 to 9 (baseline 0.34 to 0.57 s). Rule (e): `cycle_lines` 0 on both builds on every return, so the escalation stayed silent on SURFACE. F returns clean. Cadence 32 of 32 on both builds.
- S2: the candidate logged 22 `Decoder stopped:` lines against 43 on the baseline and 10 codec inits against 14 over the whole run (screen-off returns on the candidate show `New surface` where the baseline did not). Reported only.

## V1H-B

**PASS**

Reference run (the re-run; the first run is voided, Setup notes): 9 returns, one session, 0 `Fatal signal`. T: 0.0, 0.0, 0.0; 1.79, 1.71, 1.95; 1.82, 1.76, 1.73. 6 `Decoder stall detected`, 3 `Codec exception in output thread`, cadence 85 of 91 (0.934).

## V1H-C

**FAIL**

- Settings written: as V1G with codec `H.265` and `night-mode` 0. Discard check: clean (re-run once; the first capture died). 1 SSL, 0 `Fatal signal`.
- Rule (a) fires on returns 4 (86.83 vs 1.79) and 5 (99.03 vs 1.71); rule (b) on 4, 5 and 6 (7.13 vs 1.95). B1 path reached on 4, 5 and 6. Candidate: no cycle line on those returns, 41, 47 and 2 nudges, 6, 7 and 1 `Decoder stall detected`, 4 `restart: sync_stall` stops in returns 4 and 5.
- Whole run: 20 `Decoder stall detected`, 34 codec inits, cadence 113 of 128 (0.883). The hardware H.265 decoder also logged `H265DecDecode_NALU` errors during these windows. Home and launcher returns match the baseline. The baseline's own first (voided) run crashed on this path, so the H.265 hardware decoder on D-HU is fragile under cycling on both builds; the screen-off difference above is from the clean runs.

## V3S-B

**PASS**

Reference run: 20 returns, T 0.33 to 0.64 s. 39 `findBestCodec: ` calls, 0 selecting an HEVC decoder, `Enforcing H.265` 0, `Media Sink Setup Request: 3` once and `7` zero, 40 `surfaceDestroyed` stops (2 per cycle) plus 20 `activity_stopped`.

## V3S-C

**PASS**

20 `findBestCodec: ` calls (at least 20), 0 HEVC-selected, `Enforcing H.265` 0, `Setup Request: 3` once, `7` zero, 20 `Decoder stopped: surfaceDestroyed` (at least 20). T 0.31 to 0.38 s on all 20 returns. S2: the candidate logged 20 surface-destroyed stops where the baseline logged 40, and 0 `activity_stopped` where the baseline logged 20; the pin held on both.

## V3G-B

**PASS**

Reference run: 87 `findBestCodec: ` calls, 0 HEVC-selected, `Enforcing H.265` 0, `Setup Request: 3` once and `7` zero, 20 `activity_stopped` stops, 0 `surfaceDestroyed`. T 0.0 or 1.58 to 1.77 s.

## V3G-C

**PASS**

85 `findBestCodec: ` calls, 0 HEVC-selected, `Enforcing H.265` 0, `Setup Request: 3` once and `7` zero, 20 `Decoder stopped: activity_stopped` (at least 20). Same T pattern as the baseline. The candidate also logged 20 `surfaceDestroyed` stops that the baseline did not log in this window; reported.

## V5R-B

**PASS**

Reference run: returns 1 to 3 (already in front) no stops, 0 codec inits, no cycle line, max gap 5.0 s; returns 4 to 6 T 0.0, 1.8, 0.0; codec inits 2, 5, 2.

## V5R-C

**PASS**

Part 1: no stops, 0 codec inits, 0 cycle lines, max gap 5.0 s on both builds. Part 2: no (a) to (d); T 0.0, 1.74, 0.0 against 0.0, 1.80, 0.0; codec inits 2, 5, 2 on both builds. The candidate is not worse, and not improved either: the two builds produce the same numbers on this rig. Codec inits per window exceed 2 on return 5 on both builds (5), so the brief's "at most 2" does not hold for either build and is reported, not graded against the candidate.

## V5D-B

**PASS**

Reference run: both windows had an `onResume` (the activity was recreated), T 2.01 and 1.98 s, cycle line 1.28 and 1.21 s after `onResume`. Density restored to 240.

## V5D-C

**PASS**

T 1.82 and 1.84 s (baseline 2.01 and 1.98), 0 extra SSL, `onResume` present in both windows (the recreate happened, so the `onDestroy` retirement step ran). Density read back as 240, no override.

## V6A-B

**PASS**

Reference run: per-minute throughput 12, 11, 12, 12; cadence 61 of 61 (1.0); 0 `Decoder stall detected`, 0 `Forcing restart (`; Home returns T 0.64, 0.47, 0.63.

## V6A-C

**PASS**

Minutes 0 to 3: 12, 12, 12, 12 (each at least 8 and at least the baseline minus 2); cadence 1.0 against 1.0; forbidden lines 0; `Codec exception in output thread` 0 against 0; the return rule passes (T 0.63, 0.46, 0.60). **The strike-threshold half is INCONCLUSIVE:** the baseline never reached a stall or a forced restart under this load, and neither did the candidate.

## V6B-B

**PASS**

Reference run: 40 `FAULT INJECTED (#` over the capture, `keyframe decoded - the picture is repaired` 4, `picture restored` 0, `holding the picture after` 0, `dropped a reference frame` 0; tail 18 `Throughput over` lines, 0 `Decoder stall detected` in the tail.

## V6B-C

**PASS**

Graded on the whole capture (Setup notes): 40 injections; repaired lines 5 against 4 (at least half of the baseline), the other three counts 0 on both builds (the baseline never reached 4, so no threshold applies); tail 18 lines (at least 15), 0 stalls in the tail. No lines lost.

## V7S-B

**PASS**

Reference run on D-SAM (view mode 2, GLES): 3 returns, one SSL in the window, `Fatal signal` 0. T 3.83, 3.73, 3.73; throughput after resume 7.31, 7.19, 7.21 s.

## V7S-C

**PASS**

Return rule passes (T 0.0, 2.8, 2.7 against 3.83, 3.73, 3.73). Throughput after `onResume` 6.16, 6.20, 6.19 s (at most 15). `Fatal signal` 0 against 0. `createGroup SUCCESS` before SSL is D-SAM settling (the brief says two is normal; 0 inside the window). Bluetooth on D-SAM was on (`bluetooth_on` 1).

## V7P-B

**PASS**

Reference run on D-HP (SURFACE, mode 1 against D-POCO, listener on `:149D` verified): T 0.41, 0.43 on returns 1 and 2, throughput after resume 5.35 s; return 3 had no picture because the baseline app crashed with `Fatal signal 11` in thread `AapTransport:Ha` right after the Home press, and the process restarted. `Fatal signal` 1 for the baseline. The two SSLs both precede the start marker.

## V7P-C

**PASS**

T 0.36, 0.43, 0.34 s (no rule fires; the baseline return 3 crashed). Throughput after resume 5.31, 5.33, 5.29 s. `Fatal signal` 0 against 1 on the baseline. One session, 0 extra SSL.

## V1M-B

**UNTESTABLE**

D-MOTO was unlocked and its Bluetooth bonded to the head unit's radio, but its Gearhead never started a wireless connection and the head unit poked D-POCO only; no SSL in 150 s (Setup notes). The fix is a Gearhead data clear and needs approval. No data.

## V1M-C

**UNTESTABLE**

Same reason as V1M-B. Not attempted on the candidate.

## S1 and S2

- **S1 cadence:** over V1G, V1H, V2T, V2S, V5R, V6A and V6B, baseline 498 of 526 windows (0.947), candidate 557 of 600 (0.928): difference 0.019 (limit 0.05), and above 0.80. **PASS.** Per run baseline/candidate: V1G 0.939/0.904, V1H 0.934/0.883, V2T 0.893/0.901, V2S 1.0/1.0, V5R 0.935/0.935, V6A 1.0/1.0, V6B 1.0/1.0.
- **S1 prefix samples:** baseline `VideoDecoder.logThroughput | `, candidate `1.invoke | ` (the queued lambda's frame).
- **S1 lines possibly lost:** `Codec exception in output thread` appears on the baseline in V1G (3), V1H (3), V2T (4), V5R (2) and V5D (2) and never on the candidate (0 in every run). Either the candidate raises fewer or the line is dropped when the worker retires; these captures cannot tell which.
- **S2 silent skips:** `skipped: surface is no longer current` 0 on both builds in every run. The candidate logs fewer `Decoder stopped:` lines than the baseline on SURFACE (V2S 22 against 43; V3S 20 surface-destroyed against 40 and 0 `activity_stopped` against 20).

## Anything the brief did not ask about

- B1 is real on this unit but only on the screen-off route, where the Surface survives. The candidate's lost warm-relaunch escalation leaves the picture gray or absent until the phone's own keyframe or a later recovery: 87 to 99 s on H.265 and no picture within 90 s on H.264 for two of three returns.
- The H.265 hardware decoder on D-HU crashed once on the baseline (SIGSEGV in `VideoDecoder-Fe`) and the candidate wedged it in the screen-off windows; the H.265 path is fragile on both builds.
- D-HP's baseline crashed once in `AapTransport` on a Home press (SIGSEGV); the candidate did not in its single run.
- Host thermal: see Setup notes. A throttled host did not touch any run.
- D-POCO still has our debug build from the previous round and D-MOTO still carries our debug build; both radios are in whatever state the last run left (D-MOTO Bluetooth off).
