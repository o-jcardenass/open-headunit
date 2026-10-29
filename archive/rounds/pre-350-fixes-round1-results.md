# pre-350-fixes, round 1 results

- Candidate (C): `1fc5181411e38f36942515b852757b621b30b475` (`fork/review/pre-350-fixes`), reported `commit` `1fc5181411e3-dirty`
- Baseline (A): `4ee1d58f6cf7f39f7dc0a6ef93509553401ec6aa` (`origin/main`), reported `commit` `4ee1d58f6cf7-dirty`
- APK md5: C `f5d253021581ab8edbeb3407e5092456`, A `ff2e8ab24ea7e3cb6db75bdc8cc8edfd`
- Units: D-HU (UNISOC MT50, Android 14), D-POCO (phone and Self Mode unit, Gearhead 18.0.164204-release.daily), D-SAM (not used, see R1b)
- Date: 2026-10-13. Evidence: release `rig-evidence-pre-350-fixes`, asset `pre-350-fixes-round1-captures.zip`, sha256 `33fa03bbdc2c052370b58bc2a423b86ff47ab8cd106b6df16c1cef5bbb52169f` (36580969 bytes; APKs left out, md5s above)

| Run | Verdict |
|---|---|
| R0 | PASS |
| R3 | INCONCLUSIVE (the point of the round; condition 1 not reproducible; night-key leg PASS in supplementary R3N) |
| R1a, R1b | UNTESTABLE as written (wrong surface) |
| R1s | INCONCLUSIVE (supplementary, on a `DialogUtils` dialog; Enter presses OK, keyboard still covers the dialog) |
| R3N | PASS (supplementary night-key leg, physical key 189) |
| R4 | PASS (run twice) |
| R5 | PASS |
| R2b | PASS |
| R2a | PASS |
| R2c | PASS (control) |

## Setup notes

**Pre-flight.** First attempt FAILED: D-HU not in `adb devices`. After the operator reconnected it: D-HU wifi 1, bt 1, awake; D-POCO wifi 1, bt 1, awake; D-SAM wifi 1, bt 1, asleep; `PREFLIGHT OK`. Quirk files read: `rig-quirks/topics/tooling.md` only.

**Helpers.** `hur-wifi-test-scripts/` does not exist on this host (the tool folder is `rig-toolkit/`), and `projection-teardown-and-relays-round3/` and `pr-1042-disabled-home-buttons-round1/` are gone. Used instead:
- `ohu_lib.sh`, `ptr_lib.sh`, `ohu_setkeys.py` from `rig-data/rounds/usb-reattach-round4/` (a later `ptr_lib.sh` than the one the brief names).
- `p1042_lib.sh` and `flags.py` extracted verbatim from the pr-1042 round 1 brief on this branch.
- `p350_lib.sh` verbatim from this brief, plus `env.sh` (sourcing order, key strings, `bringup`, `endsession`, `validity`).
- The empty-set fix in `ohu_setkeys.py` was already present; `set:native-poke-bt-macs=` gives `<set name="native-poke-bt-macs" />`.

**Brief defects worked around (each one changes how a number below reads):**
1. `pwin` matched `OHURIG: <label>`, but the phone log prints `I/OHURIG  (pid): <label>`. Every phone window read 0 until `pwin` was overridden in `env.sh`. The first R3A attempt's `byebye=0` and `critical=0` were therefore meaningless; they were re-read.
2. The `key-codes` grep `name="key-codes".*</set>` cannot match the multi-line XML. Replaced by a function that joins the lines.
3. `MediaSession: State updated to ` is `AppLog.d` in the source (`AapService.kt:436`), not INFO. At `log-level=2` it never prints. R2b was re-run at `log-level=1` (decisive-string setup fix).
4. `input keyevent 4` in the projection opens the "Exit Android Auto" dialog (type=2 window, seen in `R3C.focus-after-back` and `R4.dialog.png`). R3's "BACK to leave the search field" therefore left the dialog up and it consumed the following keys (R3C run 1: no TX Key for 131 or 42). R3C was re-run with a second BACK to dismiss the dialog.
5. R1 targets the vehicle info screen; its dialogs do not use `DialogUtils` (see R1a).
6. Maps was already in front, so HS5's icon tap was not performed. The search bar tap alone opens "Search all destinations" but does not focus its field; a second tap at the same spot did (Android Auto's own on-car keyboard then appeared).

**`Critical error` count.** Every session run on this rig reports 3 to 5 `Critical error` lines in the phone capture, all inside 1.2 s after my `ACTION_DISCONNECT` (e.g. R3A run 2: 14:42:55.295 disconnect, errors 14:42:55.757 to 14:42:56.423; R2a: `R2a-end` 15:08:11, errors 15:08:13.0 to 15:08:13.07). Before the disconnect marker the count is 0 in every run. The baseline A shows the same pattern (3). I graded condition `Critical error is 0` on the window before the disconnect and report the full-window count next to it. A strict reading of the brief's window would FAIL every session run, A included.

**Discard rule.** `ssl_in_window=2` on R5, R2b run 2: one handshake logged twice (`AapSslContext` at INFO and `AapTransport` "Handshake: SSL handshake complete" at DEBUG, visible at `log-level=1`). Not a second handshake; no discard. `MATCH! Starting AapService` was 0 in every session run.

**Prechecks (`precheck.txt`).** Gearhead `18.0.164204-release.daily`. Bonded: POCO X3 NFC `DC:B7:2E:5E:4E:59` (`$MAC`), motorola edge 30 neo, Magnetic Speaker. D-POCO `groupFormed: false isGroupOwner: false`. `ls -ld shared_prefs` on D-HU: `drwxrwx--x 2 u0_a176 u0_a176` (uid:gid 10176:10176, correct, no `chown` needed). IME present on D-HU (LatinIME) and D-SAM (SamsungKeypad). `cmd uimode night` read `yes`. Spotify and VLC on D-POCO. `:149D` count 0 (so HS9 was needed). D-HU `wm size` 720x1440 (projection screenshots are 1440x720, landscape).

**Settings.** Backups of each unit's `settings.xml` first (`backup-HU.xml` 9690 bytes, `backup-POCO.xml` 2887, `backup-SAM.xml` 7816). Audio keys read, never written: `use-aac-audio=false`, `audio-latency-multiplier=8`, `audio-queue-capacity=20`, `enable-audio-sink=false`. Keys written per the brief's strings (`KEYS_R1`, `KEYS_R3`, `KEYS_R4`, `KEYS_R5`, `KEYS_R2B`) with `hu_put`, read back. Deltas: R2b at `log-level=1` (above); D-POCO `auto-start-self-mode=false`, `log-level=1`, `onboarding-version=2`. Restored at the end: D-HU diff against backup empty, D-POCO diff empty, D-SAM untouched (diff empty), D-HU night mode back to `yes`, D-POCO back in airplane mode, C left installed on D-POCO (md5 `f5d25302...` verified).

**Build.** System JDK 21 has no `JAVA_COMPILER`; built with `JAVA_HOME=/opt/android-studio/jbr` as `build_hur.sh` does. Worktrees created with `wt-new.sh` (`pre-350-fixes__candidate__1fc51814__20261013`, `pre-350-fixes__baseline__4ee1d58f__20261013`). The `-dirty` suffix on both `commit` values comes from the untracked `local.properties` copied in.

**Hand steps** (all injected `input tap`, from `hand-steps.log`; coordinates from screenshots during video and from `uiautomator dump` otherwise):
```
14:40:31 R3A HS5: search field 400,75 (Maps already foreground, no icon tap)
14:42:34 R3A run2 HS5a: search bar 400,75      14:42:44 HS5b: search field 400,75
14:44:41 R3C HS5a 400,75                       14:44:51 HS5b 400,75
14:47:08 R3C run2 HS5a 400,75                  14:47:18 HS5b 400,75
14:49:46 R1a HS1 row "Display name" 700,315    14:50:46 HS2 row "Make" 700,415
14:51:02 R1a HS4 dialog title area 650,240
14:52:31 R4 HS6 Picture-in-Picture 407,327     14:52:52 HS7 To Android Auto 198,334
15:20:21 R1s search settings field 720,236     15:20:41 row Static BSSID 700,415
15:20:54 R1s dialog message 650,325            15:21:00 R1s field 650,397
15:02:09 R4 repeat HS6 407,327                 15:02:30 R4 repeat HS7 198,334
```
HS3 and HS8 were not needed. HS9 (Gearhead "Start head unit server" on D-POCO) was done by the operator; `:149D` read 1 afterwards and 2 before R2c.

**Run counts.** R3A and R3C ran twice, R2b twice (setup failures, listed above). Supplementary runs at the operator's request after the main round: `R3P` (probe), `R3N` (run twice, F2 then physical 189) and `R1s`. R4 ran twice at the operator's request, with identical readings; first run kept in `R4-run1/`. R1b was not run.

## R0

**PASS**

- C builds (`BUILD SUCCESSFUL`), unit tests: 3208 tests, 0 failures, 0 errors, 0 skipped. A builds.
- md5 differ (above).
- Symbol counts in `classes*.dex` strings, C / A: `TextDialogKeyPolicy` 3/0, `MediaSessionActivationPolicy` 3/0, `migrateLegacyNightKey` 2/0, `applyButtonVisibility` 2/0, `NightModeManager: SYSTEM uiMode=` 1/0.
- `aapt2 dump resources`: `resource 0x7f09033b id/vehicleInfoFragment`, so `VEH_ID=0x7f09033b`.
- `state` after each install matched the table (`1fc5181411e3`, `4ee1d58f6cf7`).

## R3

**INCONCLUSIVE**

R3A (baseline, run 2, valid: render=7, disconnected=0, byebye=1, critical=0 before `R3A-x-go`, 3 after) and R3C (candidate, run 2, valid: render=11, disconnected=0, byebye=1, critical=0 before `R3C-x-go`, 5 after):

| Condition | Reading |
|---|---|
| 1. phone keyboard | **INCONCLUSIVE.** `R3A.phone-ime`: `mInputShown=false`, `mCurrentFocus=null`, in both A attempts. The defect does not reproduce on this rig/Gearhead. `R3C.phone-ime`: `mInputShown=false`, same focus. Android Auto showed its own on-car keyboard (a dark keyboard in the projected video) on both builds. The phone's launcher was in front in `R3A.search.phone.png`. |
| 2. letters | PASS. `TX Key -> AA=29`, `AA=30`, `AA=31`: 2 each (down and up). Example `14:47:26.578 CommManager: TX Key -> AA=29 (isPress=true) src=projection`. |
| 3. night key | **Not obtainable with F1; PASS with physical 189, see R3N.** `TX Key -> AA=65539` 0, `Received request to resend night mode state` 0, `AA=42` 0. D-HU's firmware consumes F1 (131) before the app: `14:47:40.366 W/ContextImpl: ... PhoneWindowManager.sendKeyEvent_F1 ... UnisocPhoneWindowManager.interceptKeyBeforeDispatching`. No app-side log of any kind for the key, after the dialog from note 4 was dismissed (`R3C.focus-after-back2`: projection, type=1). The stored mapping `65539-131` can never fire on this unit through physical key 131. |
| 4. bare N | PASS. `TX Key -> AA=42` 2, `resend night mode state` 0 (window `R3C-n` to `R3C-n-done`). |
| 5. migration | PASS. `R3A.keycodes`: `42-131`; install of C preserved it (`R3C.keycodes-before`: `42-131`); `R3C.keycodes`: `65539-131`, no `42-131`. |
| 6. phone `Critical error` | 0 before the disconnect marker, 5 after (teardown, see Setup notes). |

The phone keyboard question the round exists to answer: on this rig the baseline already keeps the phone's keyboard closed, so the letter-keycode list cannot be shown to be the cause of any improvement. Whether the list moves Android Auto on a unit that reproduces the defect is not answered here. Condition 3 needs a physical key the firmware does not take (not F1); not run, as it would change the brief.

Phone focus lines: A `mCurrentFocus=null`, C `mCurrentFocus=null`. Screenshots: `R3C.search.png`, `R3C.typed.png` (the letters `abc` do NOT appear in the projected search box; the on-car keyboard is up and the field reads empty, "1" highlighted), `R3A.search.png`.

First attempts (kept in `a1/`, `R3C-a1/`): R3A run 1 tapped only the search bar (field not focused, `mInputShown=false`); R3C run 1 sent the letters (condition 2) but no 131 or 42 key at all, because the exit dialog was up.

## R1a

**UNTESTABLE**

As written the run cannot test the change. The vehicle info screen builds its dialogs locally (`VehicleInfoFragment.kt:343` and `:372`, `MaterialAlertDialogBuilder` plus a bare `EditText`); `TextDialogKeyPolicy` is applied only in `DialogUtils.fitKeyboard`, which that screen does not call. The readings, for the record (C installed, `state` `1fc5181411e3-dirty`):

- `R1a.open1`: `edittext=1`, `R1a.ime-open`: `mInputShown=true` (condition 1 shape holds).
- After Enter (`R1a.after1`): `edittext=1`, IME still up, the field text read `Open HeadunitR1VALUE&#10;` (a newline was inserted); OK not pressed. Condition 2 does not hold.
- "Make" dialog: `R1a.ime-open2` `mInputShown=true`; after the body tap `R1a.ime-body` `mInputShown=true`, `edittext=1`, OK present (`bounds [957,448][1089,520]`). Condition 3 does not hold.
- No `FATAL EXCEPTION`.
- IME was in fullscreen extract mode on this panel (`mInFullscreenMode=true`), `wm size` 720x1440.

These readings describe the unchanged vehicle info dialogs, not the change. A run on a `DialogUtils` dialog (for example the manual hotspot name) was not done: it needs the settings search field and more than the 5 taps the brief allows.

## R1b

**UNTESTABLE**

Not run: D-SAM would hit the same surface as R1a.

## R3N

**PASS**

Supplementary (operator asked to try R3 again), on C, for the night-key leg only. F1 is taken by D-HU's firmware, so a probe run (`R3P`, one session, unmapped keys sent 1.5 s apart) found which physical keys reach the app: F2 to F12 (132 to 142) and BUTTON_1 (188) 0 each; BUTTON_2 (189), BUTTON_3 (190), MENU (82) and SEARCH (84) 2 each (down and up). A first attempt with F2 (132) mapped read 0 as well (`R3N-f2/`). Final run: `set:key-codes=42-189` (the legacy night entry on physical 189), C installed. Session valid: render=4, disconnected=0, byebye=1, critical=0 before `R3N-x-go` (5 after).

- Migration: `R3N.keycodes-in-session` `42-189`, after the session `R3N.keycodes` `65539-189`, no `42-189`.
- `R3N-night` to `R3N-night-done` (key 189): `TX Key -> AA=65539` 2 (`15:18:30.579` down, `.591` up), `Received request to resend night mode state` 2, `NightMode update: false` 2, `TX Key -> AA=42` 0, `TX Key -> AA=189` 0.
- `R3N-n` to `R3N-n-done` (bare key 42): `TX Key -> AA=42` 2, resend 0.

This closes conditions 3, 4 and 5 of R3 for the night key on hardware. Condition 1 (the phone keyboard) is unchanged.

## R1s

**INCONCLUSIVE**

Supplementary (operator asked to do R1): the brief's target screen is unaffected by the change (see R1a), so this run uses a dialog that does go through `DialogUtils.fitKeyboard`: the "Static BSSID (MAC Address)" row, reached through the app's own "Search settings" field on D-HU (wifi-connection-mode 3 shows the row). C installed, `KEYS_R1`. Taps: search field (720,236), the result row (700,415), the dialog message (650,325), the field (650,397); four, plus typed text. Nothing was saved (the Save button was not pressed) and `settings.xml` was restored and diffed identical to the backup.

| Condition | Reading |
|---|---|
| 1. dialog up with keyboard | PASS. `R1s.open`: `edittext=1`, `R1s.ime-open`: `mInputShown=true`. The dialog has a message ("Set the WiFi Direct group's BSSID by hand...") above the field, buttons Reset, Cancel, OK at `[957,437][1089,509]`. |
| 2. Enter presses OK | PASS. After typing `02:00:00:00:00:01` and key 66, the dialog was gone and the row read `02:00:00:00:00:01` (`R1s.after.png`, `value-shown=1`). `button1=ABSENT`. The brief's `edittext=0` cannot be used here: the settings screen's own search box is an `EditText` and keeps the count at 1. |
| 3. body tap hides the keyboard | **Not testable.** `R1s.ime-body`: `mInputShown=true`. The IME was in fullscreen extract mode (`dumpsys input_method`: `mInFullscreenMode=true`) the whole time: `R1s.open.png` and `R1s.body.png` show only the keyboard's extract field and a DONE button, with the dialog entirely behind it. The tap at the dialog message landed on the keyboard, not on the dialog body. |
| 4. OK on screen with the keyboard down | Not reached (condition 3). With the keyboard up, OK is in the hierarchy at `[957,437][1089,509]` but is not visible: the keyboard covers it. |
| 5. no `FATAL EXCEPTION` | PASS (0). |

The finding: on this panel (`wm size` 720x1440, shown landscape 1440x720) the keyboard goes fullscreen and covers the whole dialog, OK included, which is the symptom the change describes. `fitKeyboard` sets `SOFT_INPUT_ADJUST_RESIZE` but no `IME_FLAG_NO_EXTRACT_UI`, and the window resize cannot help while the IME is fullscreen. What does work on C is Enter on the last field: it pressed OK and saved the value (A's behaviour on a `DialogUtils` dialog was not measured). D-SAM was not run.

## R4

**PASS**

Run twice with identical readings (second run at the operator's request). Session valid: render=9, disconnected=0, byebye=1, critical=0 before `R4-x-go` (5 after), ssl_in_window=1, autostart=0.

- State reached: `R4.pinned` 8, `R4.focus-home` `MainActivity`, `HomeFragment: onResume. isConnected=true` 2, `MainActivity: Active session detected, bringing projection to front` 0.
- 1. `R4.home.flags`: `self_mode_button vis=visible enabled=yes clickable=yes`. The button's label is "To Android Auto" while the session runs (`R4.home.png`).
- 2. `R4.focus-after`: `AapProjectionActivity` (HS7 raised the projection).
- 3. `R4.exit.flags`: `self_mode_button vis=visible enabled=no clickable=no`; the label is "Self Mode" and the button is dimmed (`R4.exit.png`).
- Recorded: `usb_button enabled=no clickable=no` in both files, `wifi_button enabled=yes clickable=yes` in both.

## R5

**PASS**

Session valid: render=8, disconnected=0, byebye=1, critical=0 before `R5-x-go`. The unit was in night mode `yes`; it was set to `no` first so the `yes` step is a real change (deviation). Restored to `yes` and read back.

1. `R5-dark`: `NightModeManager: SYSTEM uiMode=32 isNight=true` 11, `NightMode update: true` 1.
2. `R5-light`: `NightModeManager: SYSTEM uiMode=16 isNight=false` 10, `NightMode update: false` 1.
3. `[ServiceDiscovery] Announcing NIGHT sensor support. Strategy: SYSTEM` 1 (`14:54:17.030`).

Visual (unconfirmed by scripts): `R5.dark.png` shows the projected map in dark theme, `R5.light.png` in light.

## R2b

**PASS**

Run 2 at `log-level=1`. Session valid: render=5, disconnected=0, byebye=1, critical=0 before `R2b-x-go`.

1. `R2b.hu-ms`: `package=com.andrerinas.headunitrevived active=true`.
2. `MediaSession: State updated to ` 2 in `R2b-arm` to `R2b-keys-done`.
3. `TX Key -> AA=126` 2 and `AA=85` 2.
4. Spotify `state=PLAYING(3)` after key 126, `state=PAUSED(2)` after key 85.

Run 1 (`log-level=2`) read the same on 1, 3 and 4 and 0 on 2 (DEBUG line); the system's `MediaSessionStack: onPlaybackStateChanged - Pushing session to top` for our package appeared at 14:56:05.523.

## R2a

**PASS**

C on D-POCO, Self Mode. `ssl=0`; `SelfMode: AA 17.4+ detected` 1; Gearhead `:149D` count 1 before the run.

1. `R2a.ms-session`: `package=com.andrerinas.headunitrevived active=false`.
2. `Media button session is com.spotify.music/spotify-media-session/13`, not our package.
3. Spotify `PLAYING(3)` in `R2a.ms-session`, `PAUSED(2)` in `R2a.ms-k1`, `PLAYING(3)` in `R2a.ms-k2`.
4. `TX Key -> AA=85` 0; `Runtime MEDIA_BUTTON` 0.

Phone-side `Critical error` 4, all after `R2a-end` (15:08:11); `received ByeByeRequest` 1.

## R2c

**PASS**

The control: A on D-POCO. `ssl=0`, `SelfMode: AA 17.4+ detected` 1, `:149D` count 2. `R2c.ms-session`: `package=com.andrerinas.headunitrevived active=true`, against `active=false` in R2a, so R2a measured the change. Spotify still held the media button session on A and went PLAYING, PAUSED, PLAYING over the two dispatches; `TX Key -> AA=85` 0.

## Anything the brief did not ask about

- **D-HU swallows F1.** `UnisocPhoneWindowManager.interceptKeyBeforeDispatching` runs `sendKeyEvent_F1` for keycode 131 before the app. Any keymap row using physical F1 on this unit is dead. The brief's `42-131` fixture cannot exercise the night key here; a different physical key would.
- **BACK in the projection is the exit dialog.** Any script that presses BACK to leave an Android Auto screen leaves the dialog up and eats the next keys until a second BACK.
- **Gearhead 18.0 on this phone does not open the phone keyboard on a head unit text field** with either build; it shows its own on-car keyboard. R3's premise (a reporter's `mInputShown=true` with `GhostActivity` in front) is another Gearhead version or setup.
- **Self Mode button label** changes with session state ("To Android Auto" during a session, "Self Mode" when no session), alongside the enabled flag. Read from screenshots; the mechanism was not read in source.
- **Possible visual bug, reported by the operator, not reproduced in the captures.** The operator saw the Self Mode button with an extra rectangle outside its icon, clearly visible on the unit. In the four screenshots taken (`R4.home.png` and `R4.exit.png`, both runs, enlarged 2x on the Self Mode and USB tiles) the Self Mode tile is a single rounded square, the same shape as the USB tile, in both states: bright blue with a white icon during the session, dark grey with a dim icon after exit. The only other rounded rectangle near the top of every screenshot is the OS floating assistive button (circle on a square plate at x 500 to 620, y 0 to 120), which is not the app. Which state and moment showed the extra rectangle (just after the press, with focus on the button, or while dimmed) is not recorded; a screenshot taken at that moment is needed to grade it.
- **Phone-side `Critical error` burst on teardown** (4 lines: `reason:1`, two `io error`, `Failed to read message`) follows every `ACTION_DISCONNECT` within about 1.2 s, on A and C alike. It is probably the normal close of the link, but a brief that greps `Critical error` over the whole run will always fail on it.
- **Host thermal.** Peak 90C during the C and A builds (`thermal-build.log`, `throttle_pkg` 0); every run started at 58C to 66C, per-run maximum 62C to 74C, `throttle_delta=0` throughout.
- **Subshell leak.** `th_gate` starts `th_watch` as a background subshell that outlives the run script; I killed those by pid after each script. A future `ptr_lib.sh` should stop it in `th_report`.
