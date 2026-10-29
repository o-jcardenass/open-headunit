# wizard-dpi-layout, round 2 results: the widescreen line and the picker floor

**Candidate:** `fork/fix/wizard-dpi-layout` @ `70271831dd4569c9124051f75e1b88ddcf2179d2`   **Baseline (P1-A only):** `origin/main` @ `73b52b64cbf88beb411f064b014cd4d7ae5b19db` (round 1's `A.apk`, not rebuilt)
**APK md5:** C `da19b5868add614cec39bfc7cc37f00e` / A `1f047a5eea4a08849ac1d9c0c19ebe6f`
**Unit:** D-HU (UNISOC MT50, Android 14, 1440x720, rooted) with D-POCO (POCO X3 NFC, Gearhead `18.0.164204-release.daily`), Native AA over WiFi Direct
**Date:** 2026-10-13
**Evidence:** release `rig-evidence-wizard-dpi-layout`, asset `wizard-dpi-layout-round2-captures.zip`, sha256 `f34d4f87dc0d18f30a267a0af8700489aaf5fc64eaf8c6846f68cbf52be80bf0` (APKs excluded)

## Round verdict: PASS

W1 and W2 both draw the widescreen layout at the DPI the page's line names (103 at 800x480, 165 at 1280x720). C1, A1, P1 and P1-A pass. R0 passes. 10 Native AA sessions of the 18 allowed; no re-runs.

| Run | Verdict | Layout (layout1 / layout2, rail) | `density=` | Phone `w<N>dp h<N>dp` |
|---|---|---|---|---|
| R0 | PASS | | | |
| W1-1, W1-2, W1-3 | PASS | WIDE / WIDE, LEFT (x3) | 103 (dp canvas 1242x745, requested 103, within the HUIG floor) | w1243dp h746dp (x3) |
| W2-1, W2-2, W2-3 | PASS | WIDE / WIDE, LEFT (x3) | 165 (dp canvas 1241x698, requested 165, within the HUIG floor) | w1241dp h698dp (x3) |
| W1p | record | BAR / BAR, BOTTOM | 104 (dp canvas 1230x738, requested 104, within the HUIG floor) | w1231dp h738dp |
| W2p | record | BAR / BAR, BOTTOM | 166 (dp canvas 1233x693, requested 166, within the HUIG floor) | w1234dp h694dp |
| C1 | PASS | BAR / BAR, BOTTOM | 170 (dp canvas 752x451, requested 171, clamped to the 450 dp height floor) | w753dp h452dp |
| A1 | PASS | BAR / BAR, BOTTOM | 150 (dp canvas 853x512, requested automatic, codec-based) | w853dp h512dp |
| P1 | PASS | | | |
| P1-A | PASS | | | |

## Setup notes

**Pre-flight (`rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`) FAILED, and the operator chose to proceed.**

```
FAIL stray processes: gradle 152993 /opt/android-studio/jbr/bin/java ... (an IDE Gradle daemon, up 2 h 10 min)
ROLE     ADB  WIFI BT SCREEN BT-PROFILES
D_HU     ok   1    1  Awake  HFP:none A2DP:none
D_POCO   ok   3    2  Awake  HFP:none A2DP:none
FAIL D_POCO needs WiFi on (wifi_on=3)
FAIL D_POCO needs Bluetooth on (bluetooth_on=2)
```

- D-POCO read `airplane_mode_on=1`, `Wi-Fi is disabled` and Bluetooth `state: OFF` by `dumpsys`. That is the start state the brief's `sess` protocol expects (phone in airplane mode while D-HU starts; `sess` brings the radios back after the arm). The operator confirmed "proceed as designed". The Gradle daemon was left alone and the build ran next to it.
- Later in the precheck `airplane_mode_on` read 0 on D-POCO with no command from this session. `sess` forces airplane mode on at its start, so every session started from the intended state.
- `mCurrentFocus` on D-POCO was `NotificationShade`; sent `KEYCODE_HOME` as the brief allows, focus then read the launcher.

**Quirk files read:** `rig-quirks/topics/tooling.md`, `projection.md`, `gearhead.md`, `rig-quirks/units/D-HU.md`, `D-POCO.md`, and the airplane-mode entries of `topics/wifi.md`.

**Scripts and deviations from the brief**

- Worktree: `rig-toolkit/wt-new.sh wizard-dpi-layout candidate 70271831...` (folder `worktrees/wizard-dpi-layout__candidate__70271831__20261013`) instead of `git worktree add ../wdl-C2`. It copies `local.properties`.
- Build and tests: a one-round script `rig-data/rounds/wizard-dpi-layout-round2/r0_build.sh` ran the brief's two `./gradlew ... --max-workers=2` commands verbatim, with `JAVA_HOME=/opt/android-studio/jbr`. `build_hur_cool.sh` and `run_unit_tests.sh` were not used: neither passes `--max-workers=2`. `rig_thermal.sh wait 70` ran before the build (62C) and before the tests, and `rig_thermal.sh watch` logged the whole round. The build peaked at 88C at 21:18:58 with `throttle_pkg=0` before and after; no run was voided. Per-session `*.thermal` maxima were 60C to 72C.
- No `rig-executor` agent was used. The host ran Prepare and every run in the foreground, one at a time.
- Helpers were copied from round 1's folder, plus `wdl2_lib.sh` saved exactly as the brief gives it. `env.sh` was rewritten for this round (paths and the key sets). W1-1's first readout printed an `awk` multibyte warning (head unit capture line 24097) for every line; later sessions ran with `LC_ALL=C`. The counts in W1-1's readout agree with the other sessions.
- `ANDROID_HOME` was unset on the host, so `dest_id` returned 0 on its first call. Set `ANDROID_HOME=/home/oscar/Android/Sdk` (from `local.properties`) and `dest_id` then returned `2131296513` for both C and A.
- **Brief error, P1 condition 2 and `pageout`'s `resumed`:** `page` greps `mResumedActivity`, which Android 14 on D-HU does not print (the line is `ResumedActivity:`), so `resumed=0` in both P1 and P1-A. Condition 2 was graded instead from: the `uiautomator dump` line (`UI hierchary dumped to: /sdcard/P1.xml`), 35 nodes all in package `com.andrerinas.headunitrevived`, the `P1.png` screenshot showing the "Pixel density (DPI)" page, and `ActivityTaskManager: START u0 {... cmp=com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.SettingsActivity ...}` at `21:38:43.348` (P1) and `21:39:28.484` (P1-A). Fix for the next brief: grep `ResumedActivity` without the `m`.
- `ACTION_QUERY_STATE` returned `"commit":"70271831dd45-dirty"` for C and `"commit":"73b52b64cbf8-dirty"` for A. Both start with the expected prefix. The suffix was not chased; the candidate worktree's `git status` shows only the untracked `.WORKTREE-INFO` it created.
- **Settings written** (app stopped, `hu_put` as root, `chown` and `chmod 660`): the brief's `BASE` plus `K480`/`K720` plus one `dpi-pixel-density` value per run. Delta against the fresh backup is those keys only. The backup holds no `dpi-legacy-wizard-reset` (`grep -ac` = 0), and none was written. At the end `settings.xml` was restored from the backup as root and `diff` against the backup was empty.
- Audio settings read once and never written: `audio-latency-multiplier=8`, `enable-audio-sink=false`, `use-aac-audio=false`, `audio-queue-capacity=20`.
- `$MAC` re-read from D-HU's bonded list: `DC:B7:2E:5E:4E:59` (POCO X3 NFC). The list also holds `2E:F0:03:99:D6:FB` (Magnetic Speaker) and `A0:46:5A:97:E4:95` (motorola edge 30 neo, a phone, not a head unit radio), so the brief's operator ask did not apply.
- `shared_prefs`: `drwxrwx--x 2 u0_a176 u0_a176 3452 2026-10-13 19:45`, owned by the app uid. D-HU locale `en-US`. `animator_duration_scale` read 1.0 before and after both page runs and at the end of the round.
- `wifi-direct-band` 0 at 800x480 still formed 5 GHz groups (see per-session `Freq:`). No `the phone chose the ` line in any session.
- Every session logged `AutomationReceiver:` 9 times in its capture, and the phone capture carried the `OHURIG` markers (`ph_lines` 37923 to 42015 per session, never 0).
- End of round: C installed (md5 verified), D-POCO back in airplane mode (`airplane_mode_on=1`), no `adb logcat` left running, thermal watch killed by pid.

## R0. Build and identity gate

**PASS**

- Build: `assemble exit=0`, `BUILD SUCCESSFUL in 1m 24s`. Unit tests: `testGithubDebugUnitTest` `BUILD SUCCESSFUL`, **3241 tests, 0 failures, 0 errors** (summed from the 356 result XML files; the review host also ran 3241).
- Symbol counts in C's dex: `Density is: ` 1, `widescreenHintDpi` 1, `dpiToSave` 1, `LegacyWizardDpiPolicy` 0, `dpi-legacy-wizard-reset` 0. The migration is gone.
- C md5 `da19b5868add614cec39bfc7cc37f00e` differs from round 1's C (`10e32fe334a62a588a039a9796732207`). A md5 `1f047a5eea4a08849ac1d9c0c19ebe6f` as in round 1.
- `git rev-parse fork/fix/wizard-dpi-layout` = `70271831dd4569c9124051f75e1b88ddcf2179d2`.

## W1. Does 103 give the widescreen layout at 800x480?

**PASS**

All three sessions valid (see the validity table) and all four conditions held in each.

| Session | `density=` | `ph_dims=` | `chose_fallback` | layout1 / layout2, rail | `Freq:` |
|---|---|---|---|---|---|
| W1-1 | 103 (dp canvas 1242x745, requested 103, within the HUIG floor) | `displayDimensions Point(800, 480), dpi 103` | 0 | WIDE / WIDE, LEFT | 5765 MHz |
| W1-2 | same | same | 0 | WIDE / WIDE, LEFT | 5200 MHz |
| W1-3 | same | same | 0 | WIDE / WIDE, LEFT | 5745 MHz |

Decisive lines:

```
21:22:20.914  OPENHU  [ServiceDiscovery] Density is: 103 (dp canvas 1242x745, requested 103, within the HUIG floor)     (W1-1)
21:22:21.414  GH.DisplayLayout  displayDimensions Point(800, 480), dpi 103, contentInsets: Rect(0, 0 - 0, 0), displayInsets: Rect(0, 0 - 0, 0)
21:22:21.428  GH.DisplayLayout  contentArea: Rect(0, 0 - 800, 480), DisplayDimensions: Point(800, 480), VD Config: {1.0 732mcc123mnc ... ldltr sw746dp w1243dp h746dp 103dpi xlrg long hdr widecg land night ...
```

Each session's `ph:` lines show the same `contentArea:` with `w1243dp h746dp`. The picture in all six screenshots: a left rail of four app icons, a media card (album art, track name, pause and skip controls) in the left third, and the map on the right. `Will use layout: ` did not print (as in round 1).

W1's canvas is 1242x745 dp, ratio 1.667. It drew `WIDE`, so the 1.67 aspect-ratio breakpoint does not stop the widescreen rule at this height, as the brief's smali reading predicted.

## W2. Does 165 give the widescreen layout at 1280x720?

**PASS**

All three sessions valid, 5 GHz group in all (`Freq:` below), `chose_fallback` 0 in all.

| Session | `density=` | `ph_dims=` | `chose_fallback` | layout1 / layout2, rail | `Freq:` |
|---|---|---|---|---|---|
| W2-1 | 165 (dp canvas 1241x698, requested 165, within the HUIG floor) | `displayDimensions Point(1280, 720), dpi 165` | 0 | WIDE / WIDE, LEFT | 5240 MHz |
| W2-2 | same | same | 0 | WIDE / WIDE, LEFT | 5765 MHz |
| W2-3 | same | same | 0 | WIDE / WIDE, LEFT | 5220 MHz |

Decisive line (W2-1): `21:32:26.797 OPENHU [ServiceDiscovery] Density is: 165 (dp canvas 1241x698, requested 165, within the HUIG floor)`. The phone's `contentArea:` line in every W2 session carries `w1241dp h698dp`.

## W1p and W2p. One DPI above the line (record only)

**W1p: record only, BAR / BOTTOM at 104.** `density=104 (dp canvas 1230x738, requested 104, within the HUIG floor)`, `ph_dims=displayDimensions Point(800, 480), dpi 104`, phone `w1231dp h738dp`, `Freq: 5805 MHz`. layout1 and layout2 both `BAR`, rail `BOTTOM` (media control inside the bottom bar, map above it).

**W2p: record only, BAR / BOTTOM at 166.** `density=166 (dp canvas 1233x693, requested 166, within the HUIG floor)`, `ph_dims=displayDimensions Point(1280, 720), dpi 166`, phone `w1234dp h694dp`, `Freq: 5220 MHz`. layout1 and layout2 both `BAR`, rail `BOTTOM`.

Highest DPI giving `WIDE`, lowest giving `BAR`: 800x480: 103 / 104. 1280x720: 165 / 166. So N is exact on both codecs: the line "N DPI or lower" is true and is also the exact boundary. In dp, as the phone reports it: `WIDE` at w1241dp and w1243dp, `BAR` at w1231dp and w1234dp. That puts the phone's breakpoint between 1234 and 1241 dp, consistent with the 1240 dp `SystemUi__widescreen_breakpoint_dp`. (The phone's `w` is 1 dp above the head unit's announced canvas in all six W/Wp cases, e.g. 1243 vs 1242, 1231 vs 1230.)

## C1. A stored 171 stays stored

**PASS**

- `density=170 (dp canvas 752x451, requested 171, clamped to the 450 dp height floor)`, equal to the C1 row exactly (the reset text `150 (dp canvas 853x512, requested automatic, codec-based)` did not appear).
- `ph_dims=displayDimensions Point(800, 480), dpi 170`, phone `w753dp h452dp`.
- `C1.keys-after`: `name="dpi-pixel-density" value="171"`.
- layout1 and layout2 both `BAR`, rail `BOTTOM`. `Freq: 5805 MHz`.
- Decisive line: `21:29:03.047 OPENHU [ServiceDiscovery] Density is: 170 (dp canvas 752x451, requested 171, clamped to the 450 dp height floor)`.

## A1. Automatic, the value a fresh install keeps

**PASS**

- `density=150 (dp canvas 853x512, requested automatic, codec-based)`, equal to the A1 row exactly. `ph_dims=displayDimensions Point(800, 480), dpi 150`, phone `w853dp h512dp`. `A1.keys-after`: `dpi-pixel-density` `value="0"`.
- layout1 and layout2 both `BAR`, rail `BOTTOM`. `Freq: 5765 MHz`.
- Decisive line: `21:30:36.502 OPENHU [ServiceDiscovery] Density is: 150 (dp canvas 853x512, requested automatic, codec-based)`.

## Session validity (all ten sessions)

| Condition | Result in all ten |
|---|---|
| 1 `rendered_windows` 1+, `video_sink` 1+ | `rendered_windows=8`, `video_sink=1` |
| 2 `ssl` is 1 | 1 |
| 3 `self_wake` 0, `disconnected_in_session` 0 | 0, 0 |
| 4 `mode` | `Initializing WiFi Mode: NATIVE` |
| 5 `rescap`, `NegotiatedResolution`, `Margins`, `PixelAspectRatioE4` | `capped=_800x480` (W1, W1p, C1, A1) or `_1280x720` (W2, W2p), `linkCapped=none`; `NegotiatedResolution is: 800x480` or `1280x720` to match; `Margins are: 0x0`; `PixelAspectRatioE4 is: 10001 (10000 = square)` |
| 6 `keys-before` | each session's `dpi-pixel-density`, `resolutionId` (1 or 2) and `wifi-direct-band` (0 or 1) read back as written |
| 7 phone `ph_codec` 1+, `ph_critical` 0 | `ph_codec=1`, `ph_critical=0`, `ph_bye=1` |

## P1. The DPI page below the old floor

**PASS**

- `P1.dest`: `DEST_C=2131296513`. `fatal=0`, `ise=0`. `resumed=0` is a tooling error, see Setup notes; condition 2 graded from the dump, screenshot and the `START ... SettingsActivity` line at `21:38:43.348`.
- `input_text= text="103"`. `auto_checked= checked="false"`. `clamped_nodes=0`.
- `hint_nodes=1`, `hint_text= text="103 DPI or lower: Android Auto shows the map with a media card beside it; text gets smaller."` (English locale, full text as the brief expects).
- `auto_desc= text="Uses 150 DPI for 480p, a 853x512 dp canvas"`. `anim_before=1.0`, `anim_after=1.0`.
- `P1.png` shows the page: Automatic off, the hint line under it, the slider near its low end and the DPI field at 103.

## P1-A. Control: the same page on `main`

**PASS**

- A's md5 `1f047a5eea4a08849ac1d9c0c19ebe6f` and `commit` `73b52b64cbf8-dirty` verified before the run. `DEST_A=2131296513`.
- `fatal=0`, `ise=0`, `input_text= text="110"`, `hint_nodes=0`, `hint_text=` empty, `clamped_nodes=0`, `auto_checked= checked="false"`, `auto_desc= text="Uses the device density (240 DPI)"`. `anim_before=1.0`, `anim_after=1.0`.
- A shows 110 where C shows 103, and no line, so the control separates the builds and P1's condition 3 is a real change.
- C was reinstalled afterwards (`install -r`, md5 `da19b586...` verified, `commit` `70271831dd45-dirty`).

## Anything the brief did not ask about

- **Both layout classes the page's line cares about came out as predicted, on both codecs.** The widescreen breakpoint on this phone sits between 1234 and 1241 dp, which matches the decompiled 1240. The brief's `codecWidth * 160 / 1240` gives 103.2 and 165.2, and the floor of both is exactly the last DPI that draws `WIDE`.
- **`wifi-direct-band` 0 (automatic) at 800x480 formed 5 GHz groups in all six sessions** (5765, 5200, 5745, 5805, 5805, 5765 MHz), and the 5 GHz request at 1280x720 held in all four. No W2 or W2p session needed the INCONCLUSIVE rule.
- `ACTION_QUERY_STATE` reports `-dirty` on both the candidate and the baseline; worth knowing before a brief makes the suffix part of an identity check.
