# settings-defaults: round 1 results

- Candidate: `fix/settings-defaults` `9e32cf47` (APK md5 `285526785255eb362bd61968e89ba847`)
- Baseline: `main` `f749b84c` (APK md5 `17715974a26b7b6c5651c82eab02b383`)
- Unit: D-HU (API 34) for C0, R1 to R4, R6, E1 to E3, S0 to S2; D-SAM (API 19) for R5; D-POCO as the phone in R6 and E1 to E3
- Date: 2026-10-08
- Taps: every tap after R3 was injected with `input tap` at the operator's request (see Setup notes)

## Setup notes

1. **Stock Settings cannot carry this test on D-HU.** On Android 14 the Settings app sets `HIDE_NON_SYSTEM_OVERLAY_WINDOWS`, so the floating button is force-hidden over it (`mForceHideNonSystemOverlayWindow=true` on our window) and no tap can reach or pass through the button. The files app has the same flag. The OEM settings app (`com.zqc.zqcsettings`, Network page, "Wi-Fi" row) does not set it, and a tap on its Wi-Fi row logs `START u0 {act=android.settings.WIFI_SETTINGS cmp=com.android.settings/.Settings$WifiSettingsActivity}`, which matches the brief's `settings_start` pattern unchanged. It stood in for Settings in C0 and R1 to R4 and R6. `bring_up` in `sd_lib.sh` was changed to launch it.
2. **Calibration.** `floating-button-x-percent` 50, `floating-button-y-percent` 23. The overlay frame is (648,132)(144x144), so the button is centred on the Wi-Fi row. Tap point x=720, y=204. The head unit's own OEM assist ball (top centre, near x=560, y=60) is not ours: it stays on screen with our button disabled.
3. **C0b PASS.** One real touch, `settings_start=1`, with our button disabled.
4. **`alpha=` is graded from the `mAttrs` line only.** The `alpha=0.0` token on the window header line is the surface alpha and reads 0.0 on D-HU even for windows that are visibly shown (the foreground Files activity read `shown=false alpha=0.0` too). The new helper `lpalpha` reads the LayoutParams alpha.
5. **Phones must be off.** The first R1 attempt was void: both phones were not in airplane mode, a phone reconnected on its own, the projection came to the front and the service removed the button (`R1-void1.hu.txt`). After that D-POCO and D-MOTO were put in airplane mode and their Bluetooth and WiFi switched off explicitly, because airplane mode alone left D-POCO's Bluetooth and D-MOTO's WiFi on. Verified with `dumpsys`.
6. **Instruction slip.** I told the operator to tap "when the blue button appears". At 0% it is invisible, and at the control setting it appears about 15 s before the recorder arms, so two R4 attempts ended with the app opened early (`R4-void1`, `R4-void2`) and the third recorded no touch (`R4-void3`). The operator then asked me to do the remaining taps.
7. **Injected taps.** From R4 on every tap is `adb shell input tap`, which does not reach `/dev/input`, so `getevent` shows 0 touches and `touch_downs` is not available for those runs. Grading rests on the log counts and, on D-SAM, on the resumed activity. R1 to R3 and C0b used real hand taps, and the helper's two patterns both match one touch there (`touch_downs=2` is one touch; `BTN_TOUCH DOWN` alone reads 1).
8. **D-SAM.** API 19 logs no activity `START` line, so R5 is graded from `mResumedActivity` after the tap: Settings `SearchMain` (the search icon under the button) means the tap passed through, Open Headunit `MainActivity` means it hit the button. The button was placed over the search icon (x-percent 95, y-percent 0, frame (1094,0)(128x128)). Window flags decoded from hex.
9. **Phone-side pattern.** The brief's ` GH.` / ` CAR.` pattern needs a leading space, but a `-v time` capture prints `I/GH.ConnLoggerV2(`. Counted with `[/ ]` instead.
10. **Head unit Bluetooth toggle.** In E1 the session did not form in 45 s and a toggle of the head unit's Bluetooth (`svc bluetooth disable`, 4 s, `enable`) formed it 9 s later. Used once in E1 (toggle=1); E2 and E3 needed none.
11. **Exit taps.** Three injected taps per E run: (60,665) dashboard, (60,665) launcher grid, (240,110) Exit tile. S1 and S2 used two each: the "Show Toast Messages" switch (1356,432) and Save (1369,47).
12. **`shared_prefs` ownership on D-HU** read `u0_a176:u0_a176` (writable by the app). Settings delta before the round: backed up as `round-start.settings.xml` (165 elements), restored byte-identical at the end on D-HU and D-SAM. `block_untrusted_touches` was null on D-HU (blocking on). `SYSTEM_ALERT_WINDOW` was `default` and set to `allow` for the round, then back to `default`.
13. **Host.** 63C to 74C during the builds, throttle counter 0.
14. **A is never run on D-SAM**, as the brief says; R5 ran B only.
15. **Operator's standing instruction on taps (given during this round, applies to later rounds unless a brief or the operator says otherwise):** when a round needs a tap the tester cannot script and the operator is not able to hand-tap, the tester may do it with `adb shell input tap`, with at most 5 injected taps per run and as few as possible (the operator saw the tester struggle once a run needed more than 3). Find coordinates from a `uiautomator dump` or a screenshot first, and say every injected tap in Setup notes. Injected taps do not reach `/dev/input`, so `getevent` cannot confirm them; grade from log counts instead. Do not ask the operator to tap something that is invisible (0% opacity) or that appears long before the recorder arms.
16. Not run: nothing. R1's follow-up (A with opacity above 80) is suggested below and was not run.

## R0

**PASS**

B: 2836 tests, 0 failures, 0 errors; `FloatingButtonOpacityPolicyTest` 9, `FloatingButtonPolicyTest` 5, `AaExitActionPolicyTest` 4, `BluetoothAddressSeedPolicyTest` 4. APK md5s differ. `windowAlpha` in dex: A 0, B 1. `40:EF:4C:A3:CB:A5` in dex: A 0, B 1.

## R1: the defect on the merged build (A, D-HU)

**INCONCLUSIVE** (setup condition not met by the letter; tap passed through)

Setup valid is defined as `NOT_TOUCHABLE` with no `alpha=`. A's overlay window read `fl=NOT_FOCUSABLE NOT_TOUCHABLE LAYOUT_IN_SCREEN` and `alpha=0.8` in `mAttrs`. A real hand tap then gave `settings_start=1`, `ohu_start=0`, `untrusted=0`, one `BTN_TOUCH DOWN`. So the merged build did not block the tap in this configuration, which refutes the brief's prediction here.

Why, as a hypothesis not tested: A puts the window alpha at the "Opacity" setting (80%), which equals the trust limit (at most 0.8). The defect may only show above 80. A run on A with `floating-button-opacity-percent` above 80 would test it.

## R2: the fix at 0% (B, D-HU)

**PASS**

`mAttrs` `alpha=0.0`, `NOT_TOUCHABLE`. One real touch, `settings_start=1`, `untrusted=0`, `ohu_start=0`.

## R3: the fix at 5% (B, D-HU)

**PASS**

`alpha=0.05`, `NOT_TOUCHABLE`. One real touch, `settings_start=1`, `untrusted=0`, `ohu_start=0`. The faint button on screen is unconfirmed (not scripted).

## R4: positive control (B, D-HU)

**PASS**

Window `NOT_TOUCHABLE` absent, no alpha. Injected tap: `ohu_start=1`, `settings_start=0`, `untrusted=0`, `Removed floating button overlay` 1 after `R4-tap-ready`. Open Headunit `MainActivity` was in front afterwards. `touch_downs` not available (injected).

## R5: the fix below API 31 (B, D-SAM)

**PASS**

| Label | Mode | % | fl bit 0x10 | alpha | Resumed after tap |
|---|---|---|---|---|---|
| t0 | true | 0 | set (`#118`) | 0.0 | Settings `SearchMain` |
| t5 | true | 5 | set (`#118`) | 0.05 | Settings `SearchMain` |
| t80 | false | any | clear (`#108`) | absent | Open Headunit `MainActivity`, overlay removed |

One injected tap each; `touch_downs` not available.

## R6: the fade with a real session (B, D-HU + D-POCO)

**PASS**

- `Throughput over` 2 between `R6-start` and `R6-bg`; phone `GH.`/`CAR.` lines 1248 in the same window.
- Connected window: `fl=NOT_FOCUSABLE LAYOUT_IN_SCREEN HARDWARE_ACCELERATED`, no alpha token.
- After the phone went into airplane mode the window reached `alpha=0.0` with `NOT_TOUCHABLE` in 16 s (last `Throughput over` at 17:10:37, drop at 17:10:22).
- `Failed to add overlay view` 0, `Failed to update overlay layout` 0.
- Injected tap: `settings_start=1`, `untrusted=0`, `ohu_start=0`. The 300 ms fade on screen is unconfirmed.

## E1: untouched setting on the merged build (A)

**PASS** (defect reproduced)

`native_focus=1`, `minimize=1`, `disconnect=0`, `user_exit=0`, `throughput_before=2`, `throughput_after=4`, `phone_aa_before=1462`, `phone_byebye=0`, front after: OEM launcher. `aa-exit-action` still absent afterwards.

## E2: untouched setting on the candidate (B)

**FAIL**

The Exit disconnected the session, but the brief's other conditions were not met.

| Condition | Required | Measured |
|---|---|---|
| `native_focus` | 1 or more | 1 |
| `disconnect` | 1 or more | 1 |
| `minimize` | 0 | 0 |
| `user_exit` (in the exit window) | 1 or more | 0 |
| `throughput_after` | 0 | 4 |
| `aa-exit-action` still absent | yes | yes |

What the log shows at 17:17:23: `ExitAction: Disconnecting projection session`, `AapTransport stopping and sending byebye (USER_SELECTION)`, `session state disconnected (link_lost)`, and then 1 s later `NativeAA: Connection accepted from POCO X3 NFC`. So the session ended and the phone reconnected within about 1 s; the throughput lines after are the new session. `phone_byebye=4`. The `Native AA user exit. Stopping active launcher.` line appears once in the capture, at 17:17:46, after `E2-exit-done`, which is my own end-of-run `ACTION_EXIT`. The session-end handler took the "keeping the WIFI_DIRECT network up for the phone's return" branch, not the user-exit branch. Read the code at `AapService.kt:1587` and `AapControl.kt:66` for why `isUserExit` is not set on this path; not checked here.

## E3: stored choice kept (B)

**PASS** for the exit action, **INCONCLUSIVE** for the address

`minimize=1`, `disconnect=0`, `user_exit=0`, `aa-exit-action` still `value="0"` afterwards. Address: no `filled in this device's Bluetooth address` line, and the app logged `BluetoothHelper: no source named this unit's Bluetooth address`, so this unit's address could not be read. `bt-address` on disk stayed `40:EF:4C:A3:CB:A5`. Per the brief that row is INCONCLUSIVE, not a FAIL.

## S0: opening the screen writes nothing (B)

**PASS**

`frozen=0`; 161 keys before and after.

## S1: one Save on the merged build (A)

**PASS** (defect reproduced)

`show-toast-messages` `value="false"`, `frozen=5` (`hud_mirroring`, `hide-clock`, `hide-battery-level`, `fps-limit` 60, `aa-exit-action` 0), keys 161 to 169.

## S2: one Save on the candidate (B)

**PASS**

`show-toast-messages` `value="false"`, `frozen=0`, keys 163 to 164.

## Anything the brief did not ask about

- R1's A window already carries a 0.8 alpha, so the prediction in the brief ("window stayed at alpha 1.0") does not match A at the 80% setting.
- The head unit's Bluetooth toggle was needed once to form a session (E1).
- A stale `adb logcat` accumulation: 6 processes were left from earlier shells and were killed before S2. The helper `cap_stop` can hang when a script's output is piped, because `th_watch` inherits stdout.
- Evidence: release `rig-evidence-settings-defaults`, asset `settings-defaults-round1-captures.zip` (25907117 bytes), sha256 `b6b45095d59832a0365cf328a67e613e63a2a5b1d83118ca7fd55ae76e167f41`. APKs are not in the zip; their md5s are in the header.
