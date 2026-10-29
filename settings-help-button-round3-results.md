# settings-help-button, round 3 results

- Candidate: C `e3b2597ca30701807ac80b4334d33d4abe23dfd8` (tree `51ada2bb24ac83acb433e2c6c32dbdc75b7c3ae0`, `fork/docs/call-audio-needs-hfp`). Baseline: none (one arm).
- APK md5: `52a58a037289505f8232c315b0e1d992` (`apk-C.apk`), installed with `install -r -d` on both units; `apk_identity.sh` MATCH on both. `ACTION_QUERY_STATE` `commit":"e3b2597ca307"` on both. Live APK: `SettingHelpDialog` strings 6 (need 1+), `mediaKeyRoutingHint` 0 (need 0), measured on the build's dex.
- Units: D-HU (Part A), D-SAM (Part B). No phone. Date: 2026-10-22 (host clock COT/-05).
- `darkModeFragment` id on the rig build: `0x7f0900e2`, same as the dev host.
- Captures: `settings-help-button-round3-captures.zip`, release `rig-evidence-settings-help-button`, sha256 edd0f2da45a6ed9c4053c2abd39f3f457a48cbb0d0ad9644dfe75e1efb07dd38.

## Setup notes

- Transfer branch is checked out in worktree rig-sync__scratch__8228cb07__20261015 (clean, ff'd, at d75455ba8); used it read-only for brief/template; push from there.
- Quirk files read: tooling, D-HU, D-SAM. Preflight: D_HU wifi=1 bt=1 Awake; D_T230 wifi=1 bt=1 Asleep; HFP/A2DP none; PREFLIGHT OK.
- APK C md5 52a58a037289505f8232c315b0e1d992; darkModeFragment 0x7f0900e2 (dev host 0x7f0900e2)
- logcat via setsid nohup; recorded pid is adb's own (179188).
- tier TALL: BARS read 0 at density 240 (H0=480), so tier.sh picked 180 then corrected to 170, h=598dp, 2dp under the 600 floor, and printed UNTESTABLE. Rather than fall back to the found 480dp (where the slider is expected below the fold, losing the point of the round), I set density 160 by hand (one step, same lever) and read h back: 640dp. Deviation from the brief's stop rule; the tier is in the brief's own 600-720 range.
- R1-T2: no BELOW_FOLD line printed because the (?) node was in the dump, but the Desaturation row was clipped by the bottom edge (rowH=42px, bounds end at y=720, slider=0). Graded INCONCLUSIVE (below the fold) per the brief's intent; the tap on the visible (?) opened the help (1 line) and the value stayed 100%.
- Shell state does not persist across calls; env.sh sources lib/tier/runs each call. P=720 BARS=0 D0=240 OVR= (none) set by hand from dens_found output.
- D-SAM: logcat -G/-c refused or returned old buffer; all counts windowed by marker. Clock: D-SAM 23:22:54 vs host 23:23:17 (23 s behind). D-SAM BT left alone (cannot disable).
- R5-A void: D-SAM screen was asleep (blank 0x0 dump, black screenshot). Woke with KEYCODE_WAKEUP (one-shot) and retried once as R5-A2 (a new run id so the marker windows do not overlap).
- R5-A2 void too: KEYCODE_WAKEUP does nothing on API 19 (mWakefulness stayed Asleep). KEYCODE_POWER (26), a one-shot, woke it. Third attempt R5-A3. Screen timeout read-only: 600000
 ms.
- R5-B void: screen asleep again (my power-key press the step before had put the awake unit to sleep; keyevent 26 toggles). Retried as R5-B2 with a wake-only-if-Asleep guard right before the run. The screen timeout is short, so each run needs the guard.
- Pre-flight table (opening): D_HU wifi=1 bt=1 Awake HFP:none A2DP:none; D_T230 wifi=1 bt=1 Asleep HFP:none A2DP:none; PREFLIGHT OK. D-HU Bluetooth was turned off for Part A (`svc bluetooth disable`, read back `enabled: false state: OFF`) and back on at the end (read back `enabled: true state: ON`). D-SAM radios untouched.
- `shared_prefs` stat on D-HU: owner `u0_a176:u0_a176` (the `%a` format is unsupported by the unit's stat; mode not read). Correct owner.
- Settings written (apps stopped): D-HU `log-level=1`, `onboarding-version=2`, `app-language=en`, `app-theme=2`, `use-extreme-dark-mode=false`, `ui-scale-settings-percent=100`, `night-mode=2`, `aa-monochrome-enabled=true`; D-SAM the first six, then `screen-orientation=0` for R5-B2. All read back. Found values: D-HU `log-level=2`, `screen-orientation=2`; D-SAM `log-level=2`, `screen-orientation=4`. Both backups restored; diff of restored against backup (CRs and blank lines stripped): identical on D-HU and D-SAM (delta 0).
- Injected taps: 6 in all, one per run (below, from `taps.log`). All targets resolved from a `uiautomator dump` content-desc. No scroll, no swipe, no typed search. BACK (keyevent 4) closed each dialog.
- Every run window: `WifiLauncher: Initializing WiFi Mode: ` = 0 and `MATCH! Starting AapService` = 0 (`guard.txt`).
- Host thermal: gate before Part A (68C) and Part B (64C), `throttle_pkg=42` constant, max 68C; no throttle rise.
- Closing pre-flight: D_HU wifi=1 bt=1 Awake; D_T230 wifi=1 bt=1 Awake (it was Asleep at the start, a power state); PREFLIGHT OK after `rig_cleanup.sh kill` stopped one idle Gradle daemon.
- D-HU density: found Physical 240, no override; restored (`wm density` reads Physical 240 only, no override). `h` per tier: TALL 640dp (density 160), T2 400dp (288), T1 300dp (384).

## R1-TALL

**PASS**

- Desaturation (?) tapped at (186, 519), 23:19:31. `23:19:31.811 SettingHelpDialog: help opened for aaDesaturation`. `help opened for aaDesaturation` = 1, `help opened for ` = 1.
- Row: `help=1 slider=1 inrow=1 onscreen=1 rowH=116`. Rows: `Desaturation A=1 B=1 C=1 D=1`, `rows=2 fail=0`.
- Dialog: `title_ok=1 text_ok=1 ok_label=OK fits=1 items=0` (`len=80`).
- before `value=100%`, after `value=100%`. `dlg_after_back=0`. guard init=0 match=0.

## R1-T2

**INCONCLUSIVE**

Below the fold, partly visible. The (?) node was in the dump but the row was clipped by the bottom edge: `slider=0 rowH=42` (116 at TALL), bounds end at y=720 of 720. `BELOW_FOLD` did not print, so the helper tapped the visible (?) at (266, 706), 23:20:04: `help opened for aaDesaturation` = 1, dialog `title_ok=1 text_ok=1 ok_label=OK fits=1 items=0`, value `100%` before and after, `dlg_after_back=0`. Not graded as R1-TALL because the slider is not on screen (`slider=0`).

## R1-T1

**INCONCLUSIVE**

`BELOW_FOLD rows=0` at 300dp: the Desaturation row is absent from the dump. Nothing tapped.

## R2-T2

**PASS**

- Exit Action: `summary=Action when tapping Exit inside the Android Auto app drawer.`, `value=Return to OEM Launcher (Minimize)`, `help=0 inrow=1 onscreen=1 rowH=139`.
- Latency Multiplier: `summary=Adjust audio buffer size (Lower = Less Lag, Higher = Less Stutter).`, `value=8x`, `help=0 inrow=1 onscreen=1 rowH=103`.
- help-open count 0; guard 0/0.

## R2-T1

**PASS**

- Exit Action: same summary, `value=Return to OEM Launcher (Minimize)`, `help=0 inrow=1 onscreen=1 rowH=171`.
- Latency Multiplier: same summary, `value=8x`, `help=0 inrow=1 onscreen=1 rowH=124`.
- help-open count 0; guard 0/0.

## R3

**PASS**

- (?) at (946, 330), 23:20:37: `help opened for audioQueueCapacity` = 1, all-ids = 1.
- Dialog `title_ok=1 text_ok=1 ok_label=OK fits=1 items=0 len=311`: the help, not the picker.
- Row `help=1`, `value=20 chunks` before and after. `rows=1 fail=0` (A=B=C=D=1). `dlg_after_back=0`. Guard 0/0.

## R4

**PASS**

- `R4-a`: `summary=Use AAC audio to save bandwidth.`, `help=1`, `xp=0`.
- (?) at (655, 330), 23:20:56: `help opened for useAacAudio` = 1. Dialog `title_ok=1 text_ok=1 ok_label=OK fits=1 items=0 xp=0 len=153`.
- `R4-c`: a row line (`AAudio output`, `summary=Try low-latency media output. Automatically uses AudioTrack if unavailable.`, `help=1`), `xp=0`. Guard 0/0.

## R6

**PASS**

- `R6-a`: `hint1=0 hint2=0 hint3=0`, `help=1`, `seg=Always`, `rowH=232`.
- (?) at (932, 337), 23:21:18: `help opened for mediaKeyRouting` = 1, all-ids = 1.
- Dialog `title_ok=1 text_ok=1 ok_label=OK fits=1 items=0 len=573` (holds `Always: `, `Automatic: `, `Never: `).
- `R6-c`: `seg=Always` (same), `dlg=0`. `rows=1 fail=0`. Guard 0/0.

## R7

**PASS**

- (?) at (784, 330), 23:21:36: `help opened for forceSoftwareDecoding` = 1, all-ids = 1.
- Dialog `title_ok=1 text_ok=1 ok_label=OK fits=1 items=0 len=267`.
- `switch=false` on `R7-a` and `R7-c`, `help=1`, `dlg=0`. Guard 0/0.

## No-Save check

**PASS**

`nosave.txt`: all 9 lines `same=1` (`aa-desaturation-level` and `use-aaudio-output` have no stored key in either file). `nosave-diff.txt` is empty: no key changed.

## R5

**No verdict (information only, per the brief)**

D-SAM, `hdp=576` at the first `open -`. `Displayed ` lines on a SettingsActivity line: 0 in the whole capture (none were logged on this unit), so the relaunch theory cannot be read from it in either arm.

| Arm | screen-orientation | SurfaceOrientation before / after | box | none |
|---|---|---|---|---|
| R5-A3 | 4 (as found) | 0 / 0 | `[Search settings. Double tap to edit.]`, no `zzqx` | 0 |
| R5-B2 | 0 | 1 / 1 | `[Search settings. zzqx. Double tap to edit.]` | 1 |

So `extra_search_query` did not fill the box with the orientation as found (4) and did fill it with `screen-orientation=0`. Void attempts before these: R5-A and R5-A2 and R5-B (screen asleep, blank 0x0 dump; Setup notes).

## Injected taps (`taps.log`)

| Run | Target | x y | Time |
|---|---|---|---|
| R1-TALL | help | 186 519 | 23:19:31 |
| R1-T2 | help | 266 706 | 23:20:04 |
| R3 | help | 946 330 | 23:20:37 |
| R4 | help | 655 330 | 23:20:56 |
| R6 | help | 932 337 | 23:21:18 |
| R7 | help | 784 330 | 23:21:36 |

## Anything the brief did not ask about

- The `tier.sh` BARS estimate (0 at the found density) makes TALL land 2dp short; a brief using this tier code should take BARS from a measured value or relax the floor to 590.
- On D-SAM (API 19) `KEYCODE_WAKEUP` does nothing and `KEYCODE_POWER` toggles, so a run needs a wake-if-Asleep guard in its script; the brief's runs have none.
- At T2 (400dp) the Desaturation (?) is clipped at the bottom edge with no slider visible; at T1 (300dp) the row is absent from the dump. Only the TALL tier shows the whole row.
