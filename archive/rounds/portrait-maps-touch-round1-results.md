# portrait-maps-touch - round 1 results

**Candidate:** fork/release/3.5.0-beta5 @ 9beef1c9012bc1f7dd1305515a1c6732c602e402   **Baseline:** none
**APK md5:** 6757744bf96c3c86425d99e8d240d42d (beta5.apk, matched on D-SAM after both installs by `apk_identity.sh`)
**Unit:** D-SAM (SM-T230, API 19, 800x1280 portrait native) as head unit; D-POCO (Android Auto 18.0.164204-release.daily) as phone
**Date:** 2026-10-14
**Evidence:** release `rig-evidence-portrait-maps-touch`, asset `portrait-maps-touch-round1-captures.zip`, sha256 `88d527bbcf54ccd4765a9192fcf94f4fe5aa51edbcee233b4ee65980be0385c3`

## Setup notes

- Read: TESTING-TEMPLATE (without 7b and 8), `rig-quirks/topics/tooling.md`, `units/D-SAM.md`, `units/D-SAM-and-D-HP.md`, `units/D-POCO.md`, and the rotation lines of `topics/projection.md`.
- Pre-flight (`rig_preflight.sh D_SAM:wifi,bt D_POCO:wifi,bt`): PREFLIGHT OK; both radios 1, both awake. Host 58C, throttle_pkg=0. Host peaked at 84C during the build only (`build_hur_cool.sh`), `throttle_pkg=0` in all 48 samples of the build log; runs started below 75C and `throttle_pkg` stayed 0.
- Build: detached worktree via `wt-new.sh`, `build_hur_cool.sh`, APK copied to `rig-data/apks/portrait-maps-touch-round1/beta5.apk` (not `portrait-maps-touch-round1/`, per the rig layout). No unit tests, as the brief says. Every deciding fragment of section 6 was found by `git grep -F` on 9beef1c9 (`Density is: `, `NegotiatedResolution is: `, `Locking resolution at`, `Margins are: `, `Sticky Orientation: ...`, `Throughput over`, `AutomationReceiver: `). `ACTION_LOG_MARKER` is not in `CONFIGURING`, so markers worked on a fresh install without any settings write.
- Clocks at start: D-SAM 09:19:49, D-POCO 09:20:07, host 09:20:07. D-SAM was about 18 s behind. All windows below are by marker, not by clock.
- Android Auto on D-POCO is 18.0, not 17.8. `Will use layout:` is **absent** in every run. `contentArea:` and `displayDimensions` are present, so those carry the phone side.
- D-POCO was bonded to D-HU's radio (Navegadortz2) beside D-SAM's (Navegadortz3) at the start. The operator unpaired it (bond list read after: Navegadortz3 only) before thread A. Thread B ran with the bond present; it needs no phone.
- No earlier D-SAM settings backup exists to diff against (delta: unknown). `fake_speed=true` is in `settings-entry.xml` (head unit key); left as is per the brief ("keep every other key"). D-POCO mock location: `mock_location` secure setting 0.
- Scripts: `rig_preflight.sh`, `rig_thermal.sh`, `wt-new.sh`, `build_hur_cool.sh`, `apk_identity.sh`, `set_prefs_runas_host.py`. New one-round scripts in `rig-data/rounds/portrait-maps-touch-round1/scripts/`: `wiz_walk.py` (HS-B1 walk by uiautomator dump + tap), `tap_text.py` (HS-B2 taps), `run_a.sh` (thread A run), `chain.sh`, `extract.sh`.
- **API 19 prints no `START u0` line** (known quirk). The brief's decisive count for thread B could not be read. Substituted: records of `OnboardingActivity` in `dumpsys activity activities` (`Hist #`), `mResumedActivity`, and a screenshot at each checkpoint. No `OnboardingActivity` lines other than BufferQueue and dalvikvm noise were logged after 09:27 in B1.
- **HS-B2 deviation:** `SettingsActivity --es extra_search_query orientation` did not filter the list on this build (`B1-settings-stuck.png`). I tapped the in-app "Search settings" field, typed `orientation` with `input text`, then tapped "Screen orientation" and "Portrait (90°)". Four taps plus one text entry.
- **HS-B1 deviation:** the wizard step "How do you connect?" keeps Next disabled until a connection is picked. In B1 the walk got through that step on its own (cause not found); in B2 it stalled for the full 15 Next taps, so I tapped "WiFi" once and walked on with a fresh budget (total 15 + 10 Next taps in B2). `settings.xml` then carries WiFi as connection mode, which is also the operator's setting.
- B2: `onb_orient_port` read `checked=false` right after its tap (the brief asks to confirm `true`), but `screen-orientation` read 4 in `settings.xml` at C2 and C3. Portrait may also be the default, so the pick itself is not proven by the file.
- B1 and B2 both uninstalled the app (the brief's exception); D-POCO was in airplane mode for thread B. After B2's relaunch the wizard's WiFi choice armed wireless and woke D-POCO ("POCO X3 NFC is disconnected, waking it...", B2-C3.png); harmless in airplane mode.
- Restore: `settings-entry.xml` came back from `adb shell` with CRLF line endings. I stripped them and restored with `run-as cp` from `/data/local/tmp` (worked). On-device file matches `settings-entry.xml` byte for byte (md5 compared with CR stripped), checked after B2 and again at the end of the round.
- Thread A gate: the first attempt of A1 passed the brief's "one Throughput line" gate but the link broke 5 s later (09:41:42, rendered=14 then 0fps, `Connection lost` in `A1-s2.png`), before the first screenshot. I tightened the gate in `run_a.sh` to two consecutive Throughput lines at 25 fps or more. First attempts of A1, A4 (never reached projection: 0 Throughput lines in 120 s) and A5 are in `void/`. A1 and A4 passed on the second try. A5 failed twice.
- Pixel counts: `idle=` was 0.000 on most runs because the idle pair is two identical frames, which makes the "3 times idle" rule degenerate. A6 read drag=0.307 against idle=0.000 although the map did not leave follow mode (see R6). Verdicts below are visual for the Re-centre control and map offset, as the brief says; the numbers are given beside them. The A7 crop band is taken from a physical portrait buffer, so its pixel numbers are not meaningful (the screenshot shows the landscape content rotated).
- Each clean session printed one `createGroup SUCCESS`, one `SSL handshake complete`, 0 `MATCH! Starting AapService` in its marker window.
- `Sticky Orientation: ...` was never printed in any run (0 in every window).

## B1 - fresh install, wizard in landscape, then orientation to portrait in Settings

**PASS** (start count substituted, see Setup notes)

- Settings written: none by hand. The wizard wrote `onboarding-version`=2 and `screen-orientation`=2; HS-B2 changed it to 4.
- Radio state: D-POCO airplane mode on, `dumpsys bluetooth_manager` `enabled: false`.
- Discard-rule check: clean.
- Checkpoints: C1 `OnboardingActivity` records in stack 1, `onboarding-version` absent. C2 records 0, version 2, orientation 2. C3 records 0, version 2, orientation 4, `mResumedActivity` = `MainActivity`, `B1-C3.png` shows the home screen (Self Mode / USB / WiFi / Settings), no wizard.
- Markers: `09:26:04.935 W/OPENHU ... AutomationMarker: B1-start` and `09:29:33.749 ... B1-end`. Wizard walk: 15 Next taps, last label `Finish`.

The wizard did not reopen after the orientation change on this build.

## B2 - fresh install, Portrait picked inside the wizard

**PASS** (start count substituted, see Setup notes)

- Settings written: none by hand. Wizard wrote `onboarding-version`=2 and `screen-orientation`=4.
- Checkpoints: C1 records 1, version absent. C2 records 0, version 2, orientation 4, `MainActivity` resumed. After force-stop and relaunch, C3 records 0, version 2, orientation 4, `MainActivity` resumed, `B2-C3.png` shows the home screen.
- Markers: `B2-start` at 09:30:28.552, `B2-end` at 09:35:24.371.

## A1 - portrait, 175 DPI (known good)

**PASS** (visual)

- Settings written: `screen-orientation`=4, `dpi-pixel-density`=175, `log-level`=2 (read back, plus `connection-modes` containing `wifi`, `wifi-connection-mode`=3).
- HU: `09:43:34.729 [ServiceDiscovery] NegotiatedResolution is: 720x1280`, `Margins are: 0x0`, `09:43:34.739 Density is: 175 (dp canvas 658x1170, requested 175, portrait, not clamped)`.
- Phone: `09:43:52.765 GH.DisplayLayout contentArea: Rect(0, 0 - 800, 1280) ... sw731dp w731dp h1170dp 175dpi ... port` and `displayDimensions Point(800, 1280), dpi 175`. No `Will use layout:`.
- Pixels: idle=0.000 drag=0.392. `A1-s2.png`: a Re-centre control is on screen and the map content is offset from `A1-s1.png`. Tap at (400,350) landed on the map. Under the map the layout has a weather card and a media card (`A1-s0.png`).

## A7 - landscape, 198 DPI (positive control)

**PASS** (visual)

- Settings written: `screen-orientation`=2, `dpi-pixel-density`=198, `log-level`=2.
- HU: `09:46:00.061 NegotiatedResolution is: 1280x720`, `09:46:00.071 Density is: 198 (dp canvas 1034x581, requested 198, within the HUIG floor)`.
- Phone: `09:46:18.348 contentArea: Rect(0, 0 - 1280, 800) ... sw646dp w1034dp h646dp 198dpi ... land`.
- `A7-s2.png`: Re-centre control on screen, map offset from `A7-s1.png`. Pixel counts (idle=0.000 drag=0.312) are from a crop of a rotated buffer and are not graded.

## A6 - portrait, 198 DPI (known bad)

**FAIL** (visual)

- Settings written: `screen-orientation`=4, `dpi-pixel-density`=198, `log-level`=2.
- HU: `09:48:18.757 Density is: 198 (dp canvas 581x1034, requested 198, portrait, not clamped)`.
- Phone: `09:48:36.698 contentArea: Rect(0, 0 - 800, 1280) ... sw646dp w646dp h1034dp 198dpi ... port`.
- Pixels: idle=0.000 drag=0.307. `A6-s2.png` has no Re-centre control and the map is within a few pixels of `A6-s1.png` (follow mode kept; the speed read-out changes from `--` to `0`). The drag count is fine-line subpixel movement, so the "3 times idle" test is degenerate here; graded on the visual. Tap at (400,350) landed on the map.
- The layout differs from A1: no weather or media cards under the map, a single bottom bar instead (`A6-s0.png`).

## A3 - portrait, 192 DPI (the point: at 600 dp on the HU's canvas)

**FAIL** (visual)

- HU: `Density is: 192 (dp canvas 600x1066 ...)`. Phone: `contentArea: Rect(0, 0 - 800, 1280) ... sw667dp w667dp h1067dp 192dpi`.
- Pixels: idle=0.324 drag=0.078 (idle exceeds drag; the map was animating between the idle pair). `A3-s2.png` matches `A3-s1.png`, no Re-centre. Bottom-bar layout as in A6.

## A4 - portrait, 193 DPI (the point: just below 600 dp on the HU's canvas)

**FAIL** (visual)

- HU: `10:00:40.700 Density is: 193 (dp canvas 596x1061, requested 193, portrait, not clamped)`. Phone: `sw663dp w663dp h1061dp 193dpi`.
- Pixels: idle=0.000 drag=0.000. `A4-s2.png` shows no Re-centre, bottom-bar layout. Second try (first try void: 0 Throughput lines in 120 s).

## A2 - portrait, 190 DPI

**FAIL** (visual)

- HU: `Density is: 190 (dp canvas 606x1077 ...)`. Phone: `sw674dp w674dp h1078dp 190dpi`.
- Pixels: idle=0.000 drag=0.000. `A2-s2.png`: no Re-centre, bottom-bar layout.

## A5 - portrait, 196 DPI

**INCONCLUSIVE**

- HU: `Density is: 196 (dp canvas 587x1044 ...)`. Phone: `sw653dp w653dp h1045dp 196dpi`.
- The link dropped on both tries before the touch: `A5-s0.png` shows Frame 6377ms and 5 FPS (try 1), and `A5-s2.png` of try 2 shows `Connection lost / Retrying...` with the toast "Android Auto disconnected: If using wireless AA, disable 'Switch between networks' ..." in `A5-s0b.png`. idle=0.967 drag=0.001 describes a dead session. The brief's one re-run was used.

## Report back (brief section 9)

1. **Edge:** last PASS 175 DPI (HU canvas 658 dp wide, phone `w731dp`), first FAIL 190 DPI (HU 606 dp, phone `w674dp`). Every measured DPI from 190 to 198 fails (190, 192, 193, 198; 196 inconclusive). The ladder was not run between 175 and 190.
2. **Phone layout across the edge:** no `Will use layout:` line (Android Auto 18.0). What changes is the picture: at 175 DPI the screen shows a weather card and a media card under the map; from 190 DPI up a single bottom bar replaces them. `contentArea:` is 800x1280 in every portrait run, so the phone's `w` is 800 x 160 / DPI (731 at 175, 674 at 190, 646 at 198), not the HU's 720-based figure. None of the portrait runs, including 192 and 193, is near 600 dp on the phone's own `w`, so the 600 dp hypothesis does not fit the numbers: 192 and 193 behave the same as 190 and 198, and 190 (606 dp on the HU's canvas) already fails.
3. **Thread B:** B1 records 1 / 0 / 0, version absent / 2 / 2; B2 records 1 / 0 / 0, version absent / 2 / 2. The `START u0` count was not readable on API 19.

## Anything the brief did not ask about

- The phone's `w` is computed from an 800 px wide canvas while the HU announces dp for 720 px (`NegotiatedResolution is: 720x1280`, `contentArea ... 800, 1280`). A 720 px canvas would give 659 dp at 175 DPI, but the phone reports 731. Worth a look for whoever owns the density log line.
- The link dropped after projection started in three sessions (A1 try 1, A5 try 1, A5 try 2); A4 try 1 never reached projection. In the A1 void, `Tethering interfaceLinkStateChanged wlan0, true` and a `dhcpcd` start on D-SAM's `wlan0` appear at 09:41:38, 4 s before the video stalled. Not shown to be the cause.
- A GLES renderer prompt ("Do you see the Android Auto screen? Yes / Switch renderer") appeared at the bottom of the first A1 screens (`A1-s1.png`) with `view-mode`=2 and corrupted tiles around the bottom cards. Not tapped.
- `uiautomator` on D-SAM returns logical coordinates matching screenshots in portrait; in the landscape wizard the bounds were 1259 px wide.
