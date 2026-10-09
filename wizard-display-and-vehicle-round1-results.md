# wizard-display-and-vehicle, round 1 results

- **Candidate:** `3e4f7597e6a30f3bc492493f39d934111a15cbaf` (`fix/wizard-display-and-vehicle` on `fork`), tree `4a1eb38f4b3b85fa2226b4389ff67d356fe18076` (verified in the scratch worktree).
- **Baseline:** none, by design (sentinel DPI 300 per run).
- **APK md5:** candidate `0d519ac2790ed17d810c8611bd7e78f5` (built file, pulled installed APK: identical).
- **Unit:** D-HU (UNISOC MT50, 1440x720 panel, `wm density` 240), no phone.
- **Date:** 2026-10-09.
- **Evidence:** release `rig-evidence-wizard-display-and-vehicle`, asset `wizard-display-and-vehicle-round1-captures.zip`, sha256 `6040294b3d502d547991dde010389fc120be6b00ab1865abd4914be8c6a6fc10`.

## Setup notes

- **Pre-flight** (`rig_preflight.sh D_HU:wifi`): `D_HU ok, WIFI 1, BT 1, Awake, HFP:none A2DP:none`, PREFLIGHT OK. Host 65C at start.
- **Quirk files read:** `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`.
- **Build:** `build_hur.sh` with `GRADLE_OPTS=-Dorg.gradle.workers.max=2` (the script has no `--max-workers` flag; this is the equivalent). Host started the build at 65C; peaked at 92C during the build, `throttle_pkg=0` throughout; Gradle daemon stopped afterwards. Unit tests not run, as the brief says (3033 passed on its side).
- **Identity gate:** `OnboardingDisplayPolicy` count 10, `display-size-preset` count 2; `ACTION_QUERY_STATE` returned `"commit":"3e4f7597e6a3-dirty"`. The `-dirty` suffix is from the scratch worktree (an untracked `local.properties` copied in); the tree hash matched, so I take it as the candidate.
- **Paths:** `hur-wifi-test-scripts/` is gone (now `rig-toolkit/`); one-round scripts are in `rig-data/rounds/wizard-display-and-vehicle-round1/scripts/` (`libwizard1.sh` extracted verbatim from the brief, then edited as below). `ohu_setkeys.py` and `ptr_lib.sh` copied from `usb-reattach-round3/stageD/` (the `if v else []` fix present, count 1).
- **Lib edits:** D-HU's `adb shell` is already uid 0 and `su` does not exist (`su: inaccessible or not found`), so the three `su -c '...'` wrappers in `libwizard1.sh` were removed. `set -u` was dropped from the run wrappers (`ptr_lib.sh` reads unset variables). `shared_prefs` was `u0_a176` mode 771, `settings.xml` `u0_a176` 660 (not root-owned).
- **Lock leak:** `th_gate` leaves its `th_watch` subshell holding the rig lock after a run, so R2 blocked about 6 min on `flock` until I killed the R1 watcher by pid. R3, R4 and the close script ended with a `kill $THPID`. No run measurement was affected.
- **Baseline diff:** no earlier `settings-backup-HU.xml` exists to diff against, so the delta is not stated. The backup was taken after the install; `allow-external-configuration` was already `true`, `wifi-connection-mode` 3, `onboarding-version` 2, `vehicle-type` 3, `dpi-pixel-density` 218.
- **Panel:** `wm size` 720x1440 (the app runs it rotated: wizard bounds 1304x720 plus a 136 px nav bar), `wm density` 240, `xDpi=268.941`, `yDpi=240.631`. The wizard's own line: "Detected panel: 1440 x 720 px, 240 dpi, hardware H.265."
- **Injected taps** (all from `taps.log`, each on a dumped target, one match): R1 `onb_next` x3 (1214,666), `onb_size_small` (652,346), `onb_next`; R2 `onb_next` x3, `onb_size_large` (652,483), `onb_next`; R3 `onb_next` x4; R4 `onb_next` x8, then `NO_TARGET onb_vehicle_type_truck` (no tap), `onb_next` once more (tap 9 of 10).
- **Hand steps (operator approved):** the first pass (R1, R2, R4) could not read the DPI-step number box, the "Recommended" line or the car-step vehicle row, because they sit below the fold of a `ScrollView`. The runs were repeated as R1h, R2h and R4h with the same setup and keys (`run_H.sh`); at three cues (`operator.log`: 16:18:07, 16:18:50, 16:19:42) the operator scrolled the step by hand and the script read the dump and tapped by id. The first-pass runs R1, R2 and R4 are kept as evidence below the hand runs' verdicts. R3 was not repeated.
- **Decisive nodes not in the dump (first pass):** on the DPI step `dpi_input` and `onb_dpi_recommended` are absent from `uiautomator dump` (`uinode` returned `matches=0`); on the car step `onb_vehicle_type_group`, `_car`, `_truck` and `_forced` are absent. Both steps are a `ScrollView` (`activity_onboarding.xml`, Step 4 and Step 9) and these views sit below the fold of the 552 px page, after the preview and slider (DPI step) and after the dashboard, RHD switch, name input and chips (car step). The brief bans scrolling, so none was done. Consequence for the first pass: every "picker" and "recommended" read in R1 to R3 and every UI read in R4 is unmeasured. I graded those runs on the saved `settings.xml` values, which the brief also lists, and say so per run.
- **Estimate:** the wizard's own guess was `phone` in R1 and R2 (the first-launch guess from `xdpi`).
- **Hand-run taps** (all from `taps.log`): R1h `onb_next` x3, `onb_size_small`, `onb_next`; R2h `onb_next` x3, `onb_size_large`, `onb_next`; R4h `onb_next` x8, `onb_vehicle_type_truck`, `onb_next` (10 of 10). Scrolling was by hand, not injected.
- **Windows:** captures were cleared with `logcat -c` per run. `SystemOptimizer: SoC=` count 3 (R1, R2, R4) and 2 (R3); `FATAL EXCEPTION` 0 in all four.
- **Thermal:** per-run package max R1 71C, R2 64C, R3 62C, R4 65C, R1h 65C, R2h 59C, R4h 60C; `throttle_pkg=0` throughout.
- **Closing:** `settings-backup-HU.xml` restored as root and read back; the diff is only XML serialization of empty strings (`<string name="x"></string>` vs `<string name="x" />`) and the XML header, no value differs. `settings.xml` is `u0_a176` 660. `pgrep -fc "^adb .*logcat"` printed 0.

## R1
**PASS**

Graded on R1h (hand step). The first pass, R1, was INCONCLUSIVE for the unreadable picker and agrees on every value it did read.

| What | R1h | R1 (first pass) |
|---|---|---|
| estimate (wizard's guess) | `phone` | `phone` |
| tapped size | `small`, `checked_after` = `small` | same |
| picker (`dpi_input`) | **215** | not in dump |
| recommended (`onb_dpi_recommended`) | **215** ("Recommended for your screen: 215 dpi") | not in dump |
| `dpi-pixel-density` after stop | **215** (sentinel was 300) | 215 |
| `display-size-preset` after stop | **`SMALL_7_8`** | `SMALL_7_8` |
| taps / fatal | 5 / 0 | 5 / 0 |

Decisive lines: `R1h picker=215 recommended=215 taps=5`; `R1h-prefs-after.txt`: `display-size-preset">SMALL_7_8<`, `dpi-pixel-density" value="215"`.

## R2
**PASS**

Graded on R2h. Picker equals recommended, is not 300, the saved DPI equals it, and it differs from R1's.

| What | R2h | R2 (first pass) |
|---|---|---|
| estimate / target | `phone` / `large` | same |
| picker / recommended | **129 / 129** | not in dump |
| `dpi-pixel-density` after stop | **129** | 129 |
| `display-size-preset` after stop | **`LARGE_11_PLUS`** | `LARGE_11_PLUS` |
| R1 vs R2 recommended | 215 vs 129, different | |
| taps / fatal | 5 / 0 | 5 / 0 |

Decisive line: `R2h estimate=phone target=large picker=129 recommended=129 taps=5`.

## R3
**PASS**

R3 read what the first-pass R2 saved, with no keys written (R3 was not repeated after R2h; R2h saved the same 129 and `LARGE_11_PLUS`). The picker was graded through the saved value after R3's Next, which is the slider's value.

| What | Value |
|---|---|
| `checked` on relaunch | **`large`** (R2's target; the wizard's estimate would be `phone`) |
| `display-size-preset` after stop | `LARGE_11_PLUS`, unchanged |
| `dpi-pixel-density` after Next | **129**, equal to R2's saved value |
| taps / fatal | 4 / 0 |

Decisive line: `R3 checked=large`; `R3-prefs-after.txt`: `dpi-pixel-density" value="129"`. This proxy is weaker than reading the slider by eye.

## R4
**PASS**

Graded on R4h (hand step). The car step was reached with 8 `Next` taps and the vehicle row was scrolled into view by the operator.

| What | R4h | R4 (first pass) |
|---|---|---|
| car step reached with 8 Next taps | yes (`onb_vehicle_dashboard` present) | yes |
| `onb_vehicle_type_group` | present after scroll | not in dump |
| `car_checked` before the tap | **true** (no stored type reads as Car) | unreadable |
| microphone note (`onb_vehicle_type_forced`) | **shown**: "The head unit microphone is off, so Android Auto is told this is a motorcycle whatever is ..." | unreadable |
| `truck_checked` after the tap | **true** | no tap |
| `vehicle-type` after stop | **2** | absent |
| taps / fatal | 10 of 10 / 0 | 9 / 0 |

Decisive lines: `R4h car_checked=true note=shown`; `R4h truck_checked=true taps=9`; `R4h-prefs-after.txt`: `vehicle-type" value="2"`. Screenshots `R4h-scrolled.png` and `R4h-after-truck.png` are in the evidence.

## Anything the brief did not ask about

- The brief's DPI step and car step cannot be graded from a `uiautomator dump` on D-HU's 720 px screen without scrolling: the number box, the "Recommended" line and the vehicle row are below the fold. A future brief for these steps should plan a hand scroll, or a second dump after one.
- In R4 and R4h the wizard saved `display-size-preset` = `PHONE_4_6` and left `dpi-pixel-density` at 300 with no size tapped. The brief's sentinel is "no size gives 300", so an untouched Display step keeps (and saves) the stored value. R3 shows the same keep-the-stored-DPI rule. Not graded.
- The Display step's dump carries `checked="true"` on the chosen size, so `one_size` worked in every run.
- `th_gate` in `ptr_lib.sh` leaves its `th_watch` subshell holding the rig lock after a run, which blocked R2 for about 6 min until it was killed by pid (see Setup notes).
