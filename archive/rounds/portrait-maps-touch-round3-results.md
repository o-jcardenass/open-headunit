# portrait-maps-touch round 3 results

| | |
|---|---|
| Candidate | `fork/fix/portrait-density-floor` @ `ec6cb51655fe9b210dfaa707d7ff3c8b1fb9ed39` ("Display: keep a portrait canvas out of Android Auto's narrow layout"), one commit on `main` `d2d0a6bf` |
| Baseline | none (rounds 1 and 2 on beta5 are the "before") |
| APK md5 | candidate.apk `9e7ba902713336cee1850167795cf314` (3.5.0-beta4 debug); installed APK pulled from D-SAM and hashed on the host: `9e7ba902713336cee1850167795cf314`, match |
| Unit | D-SAM (SM-T230, API 19) as head unit, D-POCO as phone (Android Auto `18.0.164204-release.daily`) |
| Date | 2026-10-14 |
| Host thermal | package max 84 C (build 83 C, runs below 62 C), `throttle_pkg=0` for the whole round |

## Round verdict: PASS

Q1, Q2, Q3 and Q4 each PASS. The candidate keeps a portrait canvas at 730 dp or more on the phone, and Maps takes touch at 198 DPI, which failed on beta5.

## Setup notes

- **Pre-flight.** `rig_preflight.sh D_SAM:wifi,bt D_POCO:wifi,bt` printed `PREFLIGHT OK` (both units WiFi 1, BT 1, screen Awake, no BT profiles connected). Host at 60 C. The old `hur-wifi-test-scripts` path no longer exists, so everything was sourced from `rig-toolkit/`.
- **Quirk files read:** `rig-quirks/topics/tooling.md`, `units/D-SAM.md`, `units/D-POCO.md`.
- **Clocks.** D-SAM `11:30:56 COT`, D-POCO `11:31:14`, host `11:31:13`. D-SAM read about 18 s behind (the `date` calls were sequential, so a few seconds of that is call spacing). Not corrected; every grep is windowed by marker.
- **Android Auto on D-POCO:** `versionName=18.0.164204-release.daily`, same as rounds 1 and 2.
- **D-POCO bonded list:** motorola edge 30 neo, Magnetic Speaker, FX Plus, DR-82645, Navegadortz3. D-HU's radio (`Navegadortz2`) is not bonded. `mock_location` read 0 (printed twice by a duplicated command in my pre-flight line).
- **D-SAM supply:** `AC powered: true`, `USB powered: false`, level 100.
- **Settings backup.** `settings-entry.xml` diffed against round 2's: empty delta. `display-size-preset` = `STANDARD_9_10`.
- **Build.** Fresh detached worktree via `wt-new.sh` (`portrait-maps-touch__candidate__ec6cb516__20261014`), `build_hur_cool.sh`; thermal guard stopped and resumed Gradle 4 times (86 C to 68 C). Unit tests not run, as the brief says. APK copied at once to `rig-data/apks/portrait-maps-touch-round3/candidate.apk`.
- **Deviation: install needed `-d`.** `adb install -r` failed with `INSTALL_FAILED_VERSION_DOWNGRADE`: D-SAM held 3.5.0-beta5 (versionCode 118) and the candidate branch is 3.5.0-beta4. I reinstalled with `adb install -r -d` (the documented route in `tooling.md`, no uninstall). `settings.xml` read back identical to `settings-entry.xml` after the install.
- **Executor hiccup.** The first executor stopped at the thermal-watch step because it may not background anything; the host started the watch itself and resumed it. The executor then stopped at the install failure above; the host did the `-d` install, the identity check and the settings read-back itself.
- **Scripts.** Round 2's `run_p.sh` and `grep_run.sh` copied to `run_q.sh` and `grep_run.sh` with the directory changed and `run_q.sh` taking orientation and pixel aspect as arguments. The script writes `pixel-aspect-ratio-e4`, reads every key back and refuses to start unless `connection-modes` contains wifi. Every run read back `wifi-connection-mode=3`, the orientation, DPI, `log-level=2` and the pixel aspect it was given. Q1 ran alone first (stop rule), then Q4, Q3, Q2 in one chain.
- **Taps and swipe into the projected video** are the standing exception: portrait `input tap 400 350` and `input swipe 400 300 400 600 400`; landscape (Q4) `input tap 640 300` and `input swipe 500 300 800 300 400`. No other tap or hand step.
- **Q3 `s2` shows an Android status bar and a volume panel** drawn over the top of the projection on D-SAM. The Re-centre control and the moved map are still visible below it, so the touch verdict stands. Cause not chased.
- **Q4 screenshots are rotated** relative to the physical portrait mount (the unit is held portrait with `screen-orientation=2`); the rail sits on the left of the image.
- **Marker window.** All greps are from `<run>-start` to `<run>-end`; the phone marker is `AutomationMarker.*: <run>-start`. `contentArea:` count was 1 in every run.
- **Settings restore** is done at close: `settings-entry.xml` pushed back and the final diff must print nothing (see the last section).

## Q1

**PASS**

Role: the point (198 DPI portrait failed on beta5). Stored DPI 198, pixel aspect 10000 (derived), orientation 4.

- Wire: `11:38:11.739 [ServiceDiscovery] Density is: 175 (dp canvas 731x1170, requested 198, clamped to the 730 dp portrait width floor)`. Matches the table verbatim.
- `PixelAspectRatioE4 is: 11111 (10000 = square)`, `NegotiatedResolution is: 720x1280`, `Margins are: 0x0` (11:38:11.739 / .719 / .729).
- Phone: `11:38:29.865 GH.DisplayLayout: contentArea: Rect(0, 0 - 800, 1280), DisplayDimensions: Point(800, 1280), VD Config: {... sw731dp w731dp h1170dp 175dpi ... port ...}`. `w` = 731, 1 dp above the 730 edge, table expects 731.
- Projection gate: `Throughput over` 11:38:22 rendered=121 (24fps), 11:38:27 rendered=151 (30fps), and later windows at 30 fps (grep count in window: 12).
- Touch (visual): `Q1-s1.png` shows Maps in follow mode with no Re-centre control; `Q1-s2.png` shows a Re-centre control and the map content offset (Calle 24A / Carrera 58 area moved). Layout under the map in `s0`: **cards** (weather and media).

## Q4

**PASS**

Role: landscape control. Stored DPI 198, pixel aspect 10000, orientation 2.

- Wire: `11:40:29.063 [ServiceDiscovery] Density is: 198 (dp canvas 1034x646, requested 198, within the HUIG floor)`. Matches the table verbatim.
- `PixelAspectRatioE4 is: 9000 (10000 = square)`, `NegotiatedResolution is: 1280x720`, `Margins are: 0x0` (11:40:29.063 / .053 / .053).
- Phone: `11:40:47.252 contentArea: Rect(0, 0 - 1280, 800), DisplayDimensions: Point(1280, 800), VD Config: {... sw646dp w1034dp h646dp 198dpi ... land ...}`. `w` 1034 and `h` 646 equal the `Density is:` dp canvas exactly (0 dp difference).
- Projection gate: 11:40:39 rendered=151 (30fps), 11:40:44 rendered=150 (30fps).
- Touch (visual): `Q4-s1.png` has no Re-centre control; `Q4-s2.png` shows a Re-centre control and the map offset. Layout in `s0`: **bar** (a single media rail on the left, no cards).

## Q3

**PASS**

Role: the cap follows the pixel aspect ratio. Stored DPI 198, pixel aspect 10001 (square), orientation 4.

- Wire: `11:42:32.583 [ServiceDiscovery] Density is: 157 (dp canvas 733x1304, requested 198, clamped to the 730 dp portrait width floor)`. Matches the table verbatim: the cap moved from 175 to 157 with square pixels.
- `PixelAspectRatioE4 is: 10001 (10000 = square)`, `NegotiatedResolution is: 720x1280`, `Margins are: 0x0` (11:42:32.583 / .573 / .573).
- Phone: `11:42:50.602 contentArea: Rect(0, 0 - 720, 1280), DisplayDimensions: Point(720, 1280), VD Config: {... sw734dp w734dp h1304dp 157dpi ... port ...}`. `w` = 734, as the table expects; the canvas is 720 wide, so the square-pixel override reached the phone.
- Projection gate: 11:42:43 rendered=150 (29fps), 11:42:48 rendered=150 (30fps).
- Touch (visual): `Q3-s1.png` has no Re-centre control; `Q3-s2.png` shows a Re-centre control and the map offset (status bar and volume panel also visible, see Setup notes). Layout in `s0`: **cards**.

## Q2

**PASS**

Role: Automatic stays under the cap. Stored DPI 0 (Automatic), pixel aspect 10000, orientation 4.

- Wire: `11:44:37.996 [ServiceDiscovery] Density is: 161 (dp canvas 795x1272, requested automatic, portrait, within the 730 dp width floor)`. Announced 161, at or below 175, and the reason text is the one the brief names.
- `PixelAspectRatioE4 is: 11111 (10000 = square)`, `NegotiatedResolution is: 720x1280`, `Margins are: 0x0` (11:44:37.996 / .976 / .986).
- Phone: `11:44:56.070 contentArea: Rect(0, 0 - 800, 1280), DisplayDimensions: Point(800, 1280), VD Config: {... sw795dp w795dp h1272dp 161dpi ... port ...}`. `w` = 795, 730 or more; the wire dp canvas (795x1272) equals the phone's.
- Projection gate: 11:44:48 rendered=150 (29fps), 11:44:53 rendered=151 (30fps).
- Touch (visual): `Q2-s1.png` has no Re-centre control; `Q2-s2.png` shows a Re-centre control and the map offset. Layout in `s0`: **cards**.

## Answers to the brief's section 9

1. **Q1:** `Density is: 175 (dp canvas 731x1170, requested 198, clamped to the 730 dp portrait width floor)`, phone `w731`, Maps took touch. PASS means the candidate fixes 198 DPI portrait on D-SAM: it does.
2. **Q3:** the cap moved to 157 with square pixels, phone `w734`, Maps took touch.
3. **Q4:** the landscape dp canvas in `Density is:` (1034x646) equals the phone's `contentArea:` dp (`w1034dp h646dp`) exactly.

## Anything the brief did not ask about

- Q2's automatic DPI is 161 on D-SAM, which is not a value any round 1 or 2 run measured. It is under the 175 cap and the phone gave Maps touch, so no cap was needed there, but it shows the cap is not what picks automatic DPI.
- The wire dp canvas now matches the phone's in all four runs (Q1 731x1170, Q3 733x1304 and 734x1304 within 1 dp, Q2 795x1272, Q4 1034x646). In Q3 the wire says 733 and the phone says 734, inside the brief's 2 dp tolerance.
- The candidate is a downgrade against the beta5 that was on D-SAM (3.5.0-beta4 base). A future round on a newer base needs no `-d`.
- Nothing in this round exercised a device other than D-SAM and D-POCO, or any other Android Auto version than 18.0.164204.

## Evidence

Captures, screenshots and scripts: release asset `portrait-maps-touch-round3-captures.zip` of `rig-evidence-portrait-maps-touch`, sha256 `9ad8ea3e110b9825b2848ac160ab4127fc27fb5e117725d0d876df472c065bec` (the zip holds the draft of this file without the hash). Q4 final settings: D-SAM `settings.xml` restored from `settings-entry.xml`, final diff printed nothing.
