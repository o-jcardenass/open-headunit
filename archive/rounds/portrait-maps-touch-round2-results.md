# portrait-maps-touch - round 2 results

**Candidate:** fork/release/3.5.0-beta5 @ 9beef1c9012bc1f7dd1305515a1c6732c602e402   **Baseline:** none
**APK md5:** 6757744bf96c3c86425d99e8d240d42d (the round 1 APK, not reinstalled; the installed `/data/app/...-1.apk` was pulled and hashed on the host)
**Unit:** D-SAM (SM-T230, API 19, 800x1280 portrait native) as head unit; D-POCO (Android Auto 18.0.164204-release.daily) as phone
**Date:** 2026-10-14
**Evidence:** release `rig-evidence-portrait-maps-touch`, asset `portrait-maps-touch-round2-captures.zip`, sha256 `3a664e5e6c705e1d9c65115908f7d76fddde944ab9f55cdbc91fcf01c05becf0`

## Setup notes

- Read: TESTING-TEMPLATE (without 7b and 8), `rig-quirks/topics/tooling.md`, `units/D-SAM.md`, `units/D-POCO.md`.
- **Pre-flight failed once, then passed.** `rig_preflight.sh D_SAM:wifi,bt D_POCO:wifi,bt` first reported `FAIL stray processes` (a Gradle daemon, pid 16842, from Android Studio's JBR). The operator said to kill it; `rig_cleanup.sh kill` reported `rig clean: no strays, lock free` and the second pre-flight read PREFLIGHT OK. Both units: WiFi 1, BT 1, awake, HFP:none A2DP:none. Host 63C, `throttle_pkg=0` at the gate.
- Clocks at start: host 10:43:50 COT, D-SAM 10:44:07 (the `date` reads were a few seconds apart; D-SAM and D-POCO agree to the second, host about 17 s behind). All windows are by marker.
- Android Auto on D-POCO: 18.0.164204-release.daily (same as round 1). Google Maps 26.40.00.989295350. D-POCO bonded list: motorola edge 30 neo, Magnetic Speaker, FX Plus, DR-82645, Navegadortz3 (D-SAM). **No Navegadortz2**, so no D-HU bond. D-POCO `mock_location` = 0. Fake Speed on D-POCO was not checked beyond that.
- D-SAM: AC powered true, level 100 (separate supply, as the brief asks). `md5sum` does not exist on D-SAM; the APK was pulled and hashed on the host: 6757744bf96c3c86425d99e8d240d42d, equal to the brief. No reinstall.
- Settings delta against round 1's `settings-entry.xml`: **none** (`diff` printed nothing). `pixel-aspect-ratio-e4` read 10000 at entry. Restored at the end with the brief's commands; the final `diff` printed nothing (RESTORE_OK).
- Script: `rig-data/rounds/portrait-maps-touch-round2/scripts/run_p.sh`, copied from round 1's `run_a.sh` with these changes: orientation fixed at 4, a third argument writes `pixel-aspect-ratio-e4` (also read back before launch), `KEYCODE_WAKEUP` sent to D-SAM at run start (wake only, no display option changed), and the pixel-diff block dropped as the brief says. `scripts/chain.sh` ran P2, P6, P3, P4 in that order, with a thermal wait to 75C before each. `scripts/grep_run.sh` does the windowed greps. Other tools: `rig_preflight.sh`, `rig_thermal.sh`, `rig_cleanup.sh`, `set_prefs_runas_host.py`.
- Run order was P1, P5, P2, P6, P3, P4, as the stop rule says. P1 PASSed, so no stop.
- **Grading is visual from the PNGs** (s1 against s2), as the brief says. Pixel-change counts were not computed. Every run's `Throughput over` gate passed with two consecutive lines at 29 to 30 fps.
- **Injected input:** per run, 1 tap at `400 350` (step 10) and 1 swipe `400 300 400 600 400` (step 11), by `adb input`. In every run Maps was already on screen at s0, so the step 10 tap landed on the map and did not open anything. No other taps. Round 1 used the same method.
- **P4 try 1 was void and re-run once.** The first try formed no session: 0 `Throughput over` lines, no `contentArea:` line on the phone, `SocketProjectionConnection.handleBrokenTransport | Socket broken (server disconnected ungracefully?)` at 10:57:18, script `throughput_lines=0 ok=0`, exit 3. Try 1 files are kept as `try1-P4-script.log`, `try1-hu-P4.txt`, `try1-phone-P4.txt`. The second try passed the gate and is the one graded. Host thermal for the round: max 63C, `throttle_pkg=0` in every sample.
- Deviation seen only in P4 s1 and s2: the Android status bar of D-SAM is drawn over the top of the projection (icons, 100%, 11:00 AM). P1 to P3, P5 and P6 do not show it. It does not touch the map area that the verdict compares.
- Grep marker pattern: the phone's marker line is `I/AutomationMarker( pid): <run>-start`, not `AutomationMarker: <run>-start`. The window awk for the phone capture used `AutomationMarker.*: <run>-start`. The head unit line matches the brief's form.

## R1 - P1 (175 DPI, aspect derived)

**PASS**

- Settings written: `screen-orientation` 4, `dpi-pixel-density` 175, `pixel-aspect-ratio-e4` 10000, `log-level` 2. Read back before launch.
- HU, 10:45:24.709: `[ServiceDiscovery] Density is: 175 (dp canvas 658x1170, requested 175, portrait, not clamped)`; `PixelAspectRatioE4 is: 11111 (10000 = square)`; `NegotiatedResolution is: 720x1280` (10:45:24.689); `Margins are: 0x0`.
- Phone, 10:45:42.707: `contentArea: Rect(0, 0 - 800, 1280) ... sw731dp w731dp h1170dp 175dpi`. Predicted w 731, measured 731.
- Layout under the map in s0: **cards** (weather and media cards).
- s2 shows a Re-centre control and the map content offset from s1.

## R2 - P5 (175 DPI, square pixels)

**FAIL**

- Settings written: `dpi-pixel-density` 175, `pixel-aspect-ratio-e4` 10001, others as above.
- HU, 10:48:00.731: `Density is: 175 (dp canvas 658x1170 ...)`; `PixelAspectRatioE4 is: 10001 (10000 = square)`; `NegotiatedResolution is: 720x1280`; `Margins are: 0x0`.
- Phone, 10:48:21.789: `contentArea: Rect(0, 0 - 720, 1280) ... sw658dp w658dp h1170dp 175dpi`. Predicted w 658, measured 658. The width is 720, so the override reached the phone (P5 condition met).
- Layout in s0: **bar** (one bottom bar).
- s2 matches s1: no Re-centre, same map content.

## R3 - P2 (176 DPI, aspect derived)

**FAIL**

- Settings written: `dpi-pixel-density` 176, `pixel-aspect-ratio-e4` 10000.
- HU, 10:50:26.824: `Density is: 176 (dp canvas 654x1163 ...)`; `PixelAspectRatioE4 is: 11111`; `NegotiatedResolution is: 720x1280`; `Margins are: 0x0`.
- Phone, 10:50:44.788: `contentArea: Rect(0, 0 - 800, 1280) ... sw727dp w727dp h1164dp 176dpi`. Predicted 727, measured 727.
- Layout in s0: **bar**.
- s2 matches s1: no Re-centre, same map content.

## R4 - P6 (157 DPI, square pixels)

**PASS**

- Settings written: `dpi-pixel-density` 157, `pixel-aspect-ratio-e4` 10001.
- HU, 10:52:31.746: `Density is: 157 (dp canvas 733x1304 ...)`; `PixelAspectRatioE4 is: 10001`; `NegotiatedResolution is: 720x1280`; `Margins are: 0x0`.
- Phone, 10:52:49.758: `contentArea: Rect(0, 0 - 720, 1280) ... sw734dp w734dp h1304dp 157dpi`. Predicted 734, measured 734.
- Layout in s0: **cards**.
- s2 shows a Re-centre control and the map content offset from s1.

## R5 - P3 (180 DPI, aspect derived)

**FAIL**

- Settings written: `dpi-pixel-density` 180, `pixel-aspect-ratio-e4` 10000.
- HU, 10:54:36.677: `Density is: 180 (dp canvas 640x1137 ...)`; `PixelAspectRatioE4 is: 11111`; `NegotiatedResolution is: 720x1280`; `Margins are: 0x0`.
- Phone, 10:54:54.665: `contentArea: Rect(0, 0 - 800, 1280) ... sw711dp w711dp h1138dp 180dpi`. Predicted 711, measured 711.
- Layout in s0: **bar**.
- s2 matches s1: no Re-centre, same map content.

## R6 - P4 (181 DPI, aspect derived)

**FAIL**

- Settings written: `dpi-pixel-density` 181, `pixel-aspect-ratio-e4` 10000. Second try; try 1 was void (Setup notes).
- HU, 10:59:48.632: `Density is: 181 (dp canvas 636x1131 ...)`; `PixelAspectRatioE4 is: 11111`; `NegotiatedResolution is: 720x1280`; `Margins are: 0x0`.
- Phone, 11:00:06.665: `contentArea: Rect(0, 0 - 800, 1280) ... sw707dp w707dp h1131dp 181dpi`. Predicted 707, measured 707.
- Layout in s0: **bar**.
- s2 matches s1: no Re-centre, same map content.

## Report back (brief section 9)

1. **The edge in the phone's `w`:** last PASS is P1 at w731 (`contentArea` width 800); first FAIL is P2 at w727 (`contentArea` width 800). The edge lies between 727 and 731, so it is the 730 flag (`narrow_portrait_width_threshold_dp`), not 710: P3 at w711 and P4 at w707 fail, and w711 is above 710. That the threshold is 730 is inferred from where the edge falls, not read from the phone, whose flags cannot be dumped.
2. **P5 and P6:** P5 FAIL with `PixelAspectRatioE4 is: 10001` and `contentArea` 720 wide, w658. P6 PASS with `PixelAspectRatioE4 is: 10001` and `contentArea` 720 wide, w734. P5 FAIL with P6 PASS: the width the phone measures decides touch, not the DPI alone.
3. **Layout:** cards at P1 (w731) and P6 (w734); bar at P2 (727), P3 (711), P4 (707) and P5 (658). The layout changes at the same edge as touch, in all six runs.

## Anything the brief did not ask about

- With the derived aspect ratio, the phone's `w` equals the head unit's logged `dp canvas` width times 800/720, to within 1 dp (658 to 731, 654 to 727, 640 to 711, 636 to 707). With square pixels the two are equal (658 and 658 in P5; 733 and 734 in P6). So the head unit's own `dp canvas` line predicts the phone's width: Maps takes touch here when the derived-aspect canvas is 658 dp or more (equivalently, when the phone's `w` is 730 or more).
- The round does not test a fix. Whether a portrait DPI cap computed from the phone's width (800/720 times the canvas when the aspect is derived) is enough was not run.
- Only one phone and one Android Auto build (18.0.164204) were used, so the 730 edge is for that build.
- Step 10 of the brief ("Open Maps") has nothing to open: Maps is already the foreground app at s0 in every run. The tap lands on the map. The verdict does not depend on it.
