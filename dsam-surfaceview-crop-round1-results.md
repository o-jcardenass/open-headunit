# dsam-surfaceview-crop round 1 results

- **Candidate:** M = `main` `6ff42d9a06a3a0a00d2f50c67bc7d9b73c885572` (tree `6b65d9c6`); one arm, no baseline
- **APK md5:** M `f8df2705808d74509baddcb8247d1895` (md5 of the pulled APK on D-SAM matched in setup, TX and S1)
- **Unit:** D-SAM as head unit (SM-T230, API 19, 800x1280), D-POCO as phone; D-HU Bluetooth off, D-MOTO radios off
- **Date:** 2026-10-30
- **Operator interventions:** 1 (the opening yes, with the B2 forget of D-HU on D-POCO)
- **Verdicts:** setup PASS, TX PASS (host override), **S1 FAIL (host override)**, cleanup PASS

## Setup notes

- APK M was not built: it was copied from `pr-train-20261030` round 1 arm B (same SHA, md5 `f8df2705...` equal to that round's build record). No worktree was made.
- Quirk files read: `tooling.md`, `D-SAM.md`. Pre-flight OK (all four units adb ok, no strays, lock free); both batteries read 100%, AC powered.
- Clocks: D-HU +750 ms, D-MOTO -505 ms, D-POCO -336 ms, D-SAM -26759 ms and left uncorrected (no root shell). The session windows use each unit's own markers, so the offset affects no grade. D-SAM's logcat buffer is not cleared by `logcat -c`, so every D-SAM count here is windowed by marker.
- **TX script verdict was FAIL, graded PASS by the host.** The only failing condition was COND 7, "phone: Critical error, 2 s after k2 and x3 left out", counted 4. Those 4 phone lines are one Android Auto teardown after the clean exit at `TX-x3` (phone 18:29:13.599): `Critical error 4 detail: 34 msg: reason:1` at 18:29:15.665, two `Critical error 3 ... io error` at 18:29:15.709 and `Critical error 18 detail: 54` at 18:29:15.729, that is 2.07 to 2.13 s after the marker against a 2.0 s allowance. The same cascade after `TX-k2` (phone 18:28:26.343, lines from 18:28:27.2 to 18:28:27.987) fell inside the window and was excluded. The control measurement itself is clean: TextureView built (`Using TextureView`), no fallback, 3 of 3 sessions lit 957, 958 and 957 permille. Brief defect for the coding side: the 2 s window is too tight for the x3 cascade.
- **S1 script verdict was INCONCLUSIVE ("TX did not pass"), graded FAIL by the host.** It followed only from the TX script FAIL above. With TX valid, the brief's rule (S1 box below 750 permille of TX's median) applies: 413.
- Settings written by the session: `view-mode` 1 for TX and 0 for S1, `wifi-connection-mode` 3, `resolutionId` 3, `video-fit-mode` 0, `screen-orientation` 2, `dpi-pixel-density` 175. Baseline restored in cleanup (diff 0 lines).
- Device files: 0 copied, 0 left.
- Closing pre-flight: PREFLIGHT OK (all four units adb ok, wifi and bt on, no strays, lock free); radios restored to the fixed baseline, settings baseline restored in cleanup (diff 0 lines).

## R setup

**PASS**

4 of 4 conditions: md5 match, commit `6ff42d9a0` reported by `ACTION_QUERY_STATE`, `wifi-connection-mode` 3 read back, backup taken.

## R TX

**PASS** (host override of the script's FAIL; see Setup notes)

| Session | Lit box (permille of screen) | Rows | Cols |
|---|---|---|---|
| s1 fresh launch | 957 | 9-1268 | 12-788 |
| s2 relaunch after force-stop | 958 | 9-1269 | 12-788 |
| s3 relaunch after clean exit | 957 | 9-1268 | 12-788 |

Median 957 (800x1280 screenshot). `Using TextureView` built, `Falling back to SurfaceView` 0, FATAL EXCEPTION 0. TX-s2.png shows the projection filling the screen apart from the left-hand rail.

## R S1

**FAIL**

SurfaceView lights about 40% of the screen on `main` in every session, including the fresh launch. Brief rule: FAIL when any S1 box is below 750 permille of TX's median.

| Session | Lit box (permille) | Rows | Cols | Ratio to TX median 957 |
|---|---|---|---|---|
| s1 fresh launch | 396 | 0-739 | 252-798 | 413 |
| s2 relaunch after force-stop | 396 | 0-739 | 252-798 | 413 |
| s3 relaunch after clean exit | 396 | 0-739 | 252-798 | 413 |

Decisive lines (D-SAM clock, as logged), all three sessions:
- `18:32:05.539 AapProjectionActivity.setupProjectionView | Using SurfaceView` (s1 window; the same line at 18:33:52.874 and 18:35:08.458 for s2 and s3)
- `18:32:05.689 AapProjectionActivity.onSurfaceChanged | Actual surface dimensions: width=1280, heig...` (and 18:33:52.994, 18:35:08.568). The activity reports a 1280 wide surface while the screenshot lights only a 546x740 region.
- `Surface mismatch!` lines in the session windows: 0. `Honest Init | Mode: IMMERSIVE | Anchor: 1280x800 (from WINDOW)` in each session.
- S1-s2.png: the projection occupies the top-right region (cols 252-798, rows 0-739), the rest of the screen black. Every session is identical to the pixel, so the crop is not intermittent here and does not depend on relaunch order.
- Phone Critical error (windowed): 0. FATAL EXCEPTION for com.andrerinas on D-SAM: 0. md5 match 1.

The brief expected the crop mostly after a force-stop relaunch (s2); here the fresh launch (s1) already shows it, which the brief did not predict.

## R cleanup

**PASS**

`WiFi network is enabled again and should rejoin` seen 1 time; D-SAM settings diff against the backup 0 lines. M stays installed on D-SAM (it is `main`).

## Anything the brief did not ask about

- The `L-SURF` line reports the activity's surface as 1280 wide in all three S1 sessions, equal to the TextureView case's anchor, yet the compositor output is a 546x740 region. The app-side size and the screen content disagree, so the cause is below the activity (or the measure of a rotated screenshot misreads it; the PNG supports the former). Not investigated further: unexplained.
- Evidence: PNGs and lit-box files per session, captures `TX-hu/ph.txt`, `S1-hu/ph.txt`; release asset `dsam-surfaceview-crop-round1-captures.zip` of `rig-evidence-dsam-surfaceview-crop`, sha256 `53fda367c1ab6347daaa7eb455455cb6db3e51956e40fa83d3da33f17fe188cb`.
