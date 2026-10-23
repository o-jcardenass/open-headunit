# wizard-dpi-layout, round 1 results

**Candidate:** fix/wizard-dpi-layout @ 71e3854dd5f70fd956ee6b84b8cac9e1a52023d0 (C)   **Baseline:** origin/main @ 73b52b64cbf88beb411f064b014cd4d7ae5b19db (A)
**APK md5:** C 10e32fe334a62a588a039a9796732207 / A 1f047a5eea4a08849ac1d9c0c19ebe6f
**Unit:** D-HU (UNISOC MT50, Android 14, `wm size` 720x1440, `wm density` 240) as head unit; D-POCO (POCO X3 NFC) as phone, Android Auto (Gearhead) `18.0.164204-release.daily`
**Date:** 2026-10-13
**Evidence:** release `rig-evidence-wizard-dpi-layout`, asset `wizard-dpi-layout-round1-captures.zip`, sha256 b5171ecfdb0b26312dc72c6e9d8943966880ebd6f265910ebb38726627191760

## Round verdict: PASS

18 sessions, one run each, no re-runs, all 18 valid on the seven conditions. The reporter's reduced layout reproduces at a stored 171 on `main`, and C's clamp and migration put the full layout back.

## Setup notes

- **Preflight FAILED, operator overrode.** `rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt` printed `D_POCO needs WiFi on (wifi_on=3)` and `needs Bluetooth on (bluetooth_on=2)`: D-POCO was in airplane mode (`airplane_mode_on=1`, `Wi-Fi is disabled`, Bluetooth `state: OFF`) left from an earlier round. D-HU: wifi 1, bt 1. The operator chose to continue, since the brief's `sess` puts the phone in airplane mode at every session start and switches both radios on after the arm. No radio was switched by hand. D-POCO's bonded list could not be read with Bluetooth off at precheck; D-HU's bonded list is Magnetic Speaker, the D-MOTO phone and D-POCO (`DC:B7:2E:5E:4E:59`, taken as `$MAC`). No other head unit's radio appeared, and no session showed a connect-back problem, so the operator-forget step was not needed.
- **Quirk files read:** `topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `topics/projection.md`, `topics/wifi.md`, `topics/gearhead.md`.
- **Toolkit path.** The skill and brief name `hur-wifi-test-scripts/`; the folder is now `rig-toolkit/` and the old name does not exist as a symlink. Helpers named by the brief (`ohu_lib.sh`, `ptr_lib.sh`, `p1042_lib.sh`, `flags.py`, `ohu_setkeys.py`, which already carries the empty-`set:` fix) were copied from `rig-data/rounds/pre-350-fixes-round1/`, because the old round folders were archived on 2026-10-09. `OUT` is `rig-data/rounds/wizard-dpi-layout-round1/`. Scripts used: `rig_preflight.sh`, `rig_thermal.sh`, `wt-new.sh`, `build_hur_cool.sh`, `run_unit_tests.sh`, `rig_cleanup.sh`. Round-only scripts: `scripts/r0_build.sh`, `scripts/run_sessions.sh`, `wdl_lib.sh`, `env.sh`.
- **Brief bug, fixed in the round copy: `pwin` never matched.** The brief's phone-window helper looks for `OHURIG: <label>`, but the phone's logcat prints `OHURIG  ( 9729): <label>`. The first readout therefore gave `ph_codec=0`, `ph_bye=0` although the phone lines were in the capture. `wdl_lib.sh` carries a corrected `pwin` (regex on the pid form) and every readout was regenerated or produced with it. Counts quoted here are from the corrected version.
- **Unit tests:** 3233 tests, 0 failures, 0 errors (the brief says the review host ran 3209; the extra 24 are not explained).
- **Identity:** `apk_check` md5 matched the build before each arm (19:14:51 A, 19:25:10 C). `ACTION_QUERY_STATE` `commit` read `73b52b64cbf8-dirty` on A and `71e3854dd5f7-dirty`. The `-dirty` suffix is on a clean worktree whose only extra file is the copied `local.properties`.
- **R0 symbol counts** (`classes*.dex`, `strings | grep -cF`): AnnouncedDensityPolicy C=11 A=0; LegacyWizardDpiPolicy C=4 A=0; `Density is: ` C=1 A=0; dpi-legacy-wizard-reset C=1 A=0.
- **`shared_prefs` ownership:** `drwxrwx--x u0_a176 u0_a176` (correct, no `chown` needed). R2a's on-disk values read as the app wrote them (below).
- **Audio keys read back, never written:** `use-aac-audio=false`, `audio-latency-multiplier=8`, `audio-queue-capacity=20`, `enable-audio-sink=false`.
- **Settings delta against the fresh backup** (round keys in `BASE`): the backup had `wifi-direct-band=1`, `pixel-aspect-ratio-e4=10000`, `narrow-band-profile-cap=true`, `dpi-pixel-density=218`, `resolutionId=2`, `video-fit-mode=0`, `log-level=2`; every session rewrote the file from the backup plus its own keys (`hu_put`). At the end the backup was copied back as root (`chown u0_a176`, `chmod 660`) and `diff` against it was empty. C stays installed. D-POCO is back in airplane mode (`airplane_mode_on=1`).
- **Layout grading method.** The host opened every session's `layout2.png` and graded from it. `layout1.png` (10 s earlier) was compared with `layout2.png` by mean brightness of the left strip and the bottom strip on a 96x48 thumbnail; all 18 pairs matched within 1 level, so the two shots show the same layout in every session. Only `layout2` was read by eye.
- **Class table gap.** The brief's `DASH` is "map plus cards beside it". At 800x480 and 150, 160 or 170 dpi, and at 1280x720/200, the phone draws a **bottom rail** with the map filling the area above and the media control (album art, previous, pause, next) inside the bar. At 171 dpi it draws a **left rail**, the map fills the rest and no media control is shown. At 1920x1080/240 it draws a left rail, a large media card and the map. I graded the bottom-rail-with-media-bar form `DASH` (a media card is present and the rail changes shape), and the left-rail-no-media form `MAP`, matching the brief's reporter description. The binary is clear in every session; the label for the middle form is a host judgement.
- **Host thermal:** `rig_thermal.sh wait 70/75` before every build and session; per-session maximum 56 to 69 C, `throttle_delta=0` in all 18.
- No injected taps, no operator hand step. Two screenshots of D-POCO and D-HU were taken outside the sessions at the operator's request (one single `screencap` each, none during a D-HU session).

## R0. Build and identity gate

**PASS**

Both builds succeeded, md5s differ (above), 3233 tests with 0 failures and 0 errors, the four symbols count 1 or more in C and 0 in A.

## R1. Does 171 give the reduced layout, and where does it change?

**PASS**

| Session | Build | Stored dpi | Wire dpi | Class | Rail |
|---|---|---|---|---|---|
| R1-171-1 | A | 171 | 171 | MAP | LEFT |
| R1-171-2 | A | 171 | 171 | MAP | LEFT |
| R1-171-3 | A | 171 | 171 | MAP | LEFT |
| R1-150A | A | 150 | 150 | DASH | BOTTOM |
| R2a-1, -2, -3 | C | 171 | 150 | DASH | BOTTOM |

All three R1-171 are `MAP`, R1-150A is `DASH`, all three R2a are `DASH`. Decisive phone line, R1-171-1: `10-13 19:15:40.710 I/GH.DisplayLayout(10424): contentArea: Rect(0, 0 - 800, 480), DisplayDimensions: Point(800, 480), VD Config: {1.0 732mcc123mnc ... ldltr sw449dp w749dp h449dp 171`, and `19:15:40.363 ... MediaSinkService { codec type=3 { codecResolution=1 widthMargin=0 heightMargin=0 density=171}}`. So the phone's own canvas at 171 is h449 dp, below the 450 dp breakpoint, and it draws the left rail with no media control.

**Where the layout changes (not graded).** Highest density that gave the bottom-rail layout: **170** (R2b, h451 dp). Lowest density that gave the left-rail `MAP`: **171** (h449 dp). R1-160: `DASH`/`BOTTOM` in all three. The brief's prediction (160 `DASH`, 170 `MAP`, from a 710 dp map-width rule) was half wrong: 160 matched, 170 did not. The boundary on this phone and Gearhead is the 450 dp height, which is exactly the floor C clamps to.

Quoted phone lines for the record (`displayDimensions`, first of each class): R1-171-1 `19:15:40.695 ... displayDimensions Point(800, 480), dpi 171`; R2b-1 `19:30:31.576 ... Point(800, 480), dpi 170`; R1-160-1 `19:35:09.523 ... Point(800, 480), dpi 160`. `Will use layout: ` did not print on Gearhead 18.0 in any session.

## R2. The fix on 800x480

**PASS**

**R1-160 (C), 3 of 3 exact:** `[ServiceDiscovery] Density is: 160 (dp canvas 800x480, requested 160, within the HUIG floor)` (R1-160-1 19:35:0x, -2, -3). Layout `DASH`/`BOTTOM` x3.

**R2a (C, stored 171, flag absent), 3 of 3 exact:** `10-13 19:25:44.033 ... [ServiceDiscovery] Density is: 150 (dp canvas 853x512, requested automatic, codec-based)`. The migration ran every time (the clamp-only text `170 (... requested 171 ...)` never appeared). Layout `DASH`/`BOTTOM` x3. `keys-after` (record only): `dpi-pixel-density` 0 and `dpi-legacy-wizard-reset` true on disk after the app ran, in all three, so the app's own write reached disk.

**R2b (C, typed 175, flag true), 3 of 3 exact:** `Density is: 170 (dp canvas 752x451, requested 175, clamped to the 450 dp height floor)`. Layout `DASH`/`BOTTOM` x3. `rendered_windows` 7, 7, 8.

## R3. No change on larger codecs

**PASS**

| Pair | A class/rail | C class/rail | C `Density is:` (exact) |
|---|---|---|---|
| 1280x720, 200 | DASH / BOTTOM | DASH / BOTTOM | `200 (dp canvas 1024x576, requested 200, within the HUIG floor)` |
| 1920x1080, 240 | DASH / LEFT (media card beside the map) | DASH / LEFT | `240 (dp canvas 1280x720, requested 240, within the HUIG floor)` |

`Freq:` R3A-720 5765, R3A-1080 5240, R3C-720 5805, R3C-1080 5765 MHz. No `the phone chose the ` line in R3A-1080 or R3C-1080 (the phone took 1080p; `displayDimensions Point(1920, 1080), dpi 240` on both), so condition 3 is graded on the primary configuration. The fallback offer differs by design: A `Offering a _1280x720 fallback, margins 0x0`, C `Offering a _1280x720 fallback, margins 0x0, density 160`.

## R4. The fallback keeps the dp canvas

**PASS**

- `Freq: 2462 MHz` (forced 2.4 GHz group).
- `[ServiceDiscovery] Density is: 240 (dp canvas 1280x720, requested 240, within the HUIG floor)`.
- `[ServiceDiscovery] Offering a _1280x720 fallback, margins 0x0, density 160` (1280 * 160 / 160 = 1280 dp, equal to the primary's 1280 dp).
- `HeadUnitScreenConfig: Video: the phone chose the _1280x720 fallback (index 1), margins 0x0`.
- Phone: `10-13 19:44:19.153 I/GH.CAR.VIDEO( 5385): VideoCodecResolutionType 3 (3) is not allowed due wireless frequency.` (`ph_refused_freq=1`), then `19:44:19.181 ... displayDimensions Point(1280, 720), dpi 160`.
- Layout `DASH`/`LEFT` (media card beside the map). `rendered_windows=6`, `disconnected_in_session=0`.

## Validity, all 18 sessions

Conditions 1 to 7 from the brief, read from each readout (`ssl` was 1 and `self_wake`/`disconnected_in_session` 0 everywhere; `mode` `Initializing WiFi Mode: NATIVE`; condition 5 = run codec in `capped=`, `linkCapped=none`, `NegotiatedResolution`, `Margins are: 0x0`, `PixelAspectRatioE4 is: 10001`; condition 6 = `keys-before` dpi and resolutionId; `ph_codec` 1 and `ph_critical` 0 everywhere; `ph_bye` 1 everywhere).

| Run | rendered/sink | Freq (MHz) |
|---|---|---|
| R1-171-1 | 8/1 | 5805 |
| R1-171-2 | 8/1 | 5745 |
| R1-171-3 | 8/1 | 5765 |
| R1-150A | 8/1 | 5745 |
| R3A-720 | 8/1 | 5765 |
| R3A-1080 | 8/1 | 5240 |
| R2a-1 | 8/1 | 5240 |
| R2a-2 | 8/1 | 5785 |
| R2a-3 | 8/1 | 5785 |
| R2b-1 | 7/1 | 5180 |
| R2b-2 | 7/1 | 5745 |
| R2b-3 | 8/1 | 5805 |
| R1-160-1 | 8/1 | 5240 |
| R1-160-2 | 8/1 | 5765 |
| R1-160-3 | 8/1 | 5220 |
| R3C-720 | 8/1 | 5805 |
| R3C-1080 | 8/1 | 5765 |
| R4 | 6/1 | 2462 |

## Anything the brief did not ask about

- **The brief's JVM-only claim holds on hardware.** Every `Density is:` text the brief pinned matched exactly, on the first and only attempt, in all 12 C sessions.
- **R4's projection showed a prompt.** `R4.layout2.png` (7:44) has a bottom bar reading "Do you see the Android Auto screen?" with YES and SWITCH RENDERER, over the left rail and media card; the on-screen stats read `FPS: 51 ... Frame: 709ms`. No other session's `layout2` showed it. This is the app's renderer-verification prompt on the 2.4 GHz session with a late first frame; it did not affect any condition. Whether it is the visual fault the operator mentioned seeing on D-HU is not established (that fault was reported while a different session was running, and was not visible in the screenshots taken afterwards).
- **The 171 left-rail layout is a real, reproducible degradation on this phone:** no media control and no bottom bar, from a canvas 1 dp under the floor. C removes it for the wizard's own 171 and 186 values at 800x480.
- **`dpi-legacy-wizard-reset` is written by C on first read even when nothing needed resetting** (R2b and R1-160 seeded it already true; R2a's was absent and was written true).
- **Brief errata:** `pwin` marker format (above); the layout class table has no entry for the bottom-rail media bar; the 170 dpi `MAP` prediction is wrong.
- **Not covered: D-SAM and D-HP.** The brief names D-HU and D-POCO only, so neither tablet was run. D-HP runs Headunit Server mode only (no WiFi Direct), so these Native AA sessions cannot run on it. D-SAM's own density-against-layout measurement at 1280x720 is in `dsam-widget-layout-dpi-round1-results.md`; the 800x480 and clamp behaviour has not been measured on it. A round for either tablet needs its own brief.
