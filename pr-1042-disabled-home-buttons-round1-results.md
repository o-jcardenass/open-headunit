# pr-1042-disabled-home-buttons: round 1 results

- Candidate: P `3404e4e41e1ea6af91eb58ab80fcbf7fbef74288` as pushed (`commit` reads `3404e4e41e1e`), APK md5 `dd500f754108bc97a98f64fb807a78c2`, versionName 3.5.0-beta2, versionCode 115
- Baseline: none (R5 is the control, as the brief says)
- Unit: D-HU (API 34), D-POCO as the phone (Gearhead `17.9.664004-release`) in R5 and R4 only
- Date: 2026-10-08

## Setup notes

1. **P installs only with `-d`.** `adb install -r` failed silently (the live md5 stayed the previous build's) because P is versionCode 115 and the build on the unit was 117. `adb install -r -d` worked, and a diff of `settings.xml` before and after the install was empty.
2. **`ohu_setkeys.py` wrote the empty set wrongly, and I fixed it.** For `set:connection-modes=` it wrote `<set name="connection-modes"><string /></set>`, a set holding one empty string. The brief asks for `<set name="connection-modes" />`. I changed the loop to `for item in (v.split(',') if v else [])` in the round's copy, confirmed the output is now `<set name="connection-modes" />` (and the same for `native-poke-bt-macs`), and re-ran R1-D. The first R1-D (void) also showed all buttons enabled, so the app reads both shapes as empty. R1-A, B, C and E ran before the fix and also carry `native-poke-bt-macs` as a one-empty-string set; no phone was involved, so it cannot matter there.
3. **`dumpsys activity top` timed out on the first R1 pass.** Leftover Files and Clock tasks from the previous thread's probing failed the dump (`Failure while dumping the activity: Timeout`), so B to E read `ABSENT` for every view (R1-A had worked). The screenshots showed the right states. I force-stopped those apps (`documentsui`, `deskclock`, the OEM settings app, stock Settings, Chrome), confirmed the dump has no timeout, moved the B to E files to `void/` and re-ran them. Every verdict below is from the re-run.
4. **HS1 and HS2 were injected taps, not hand presses.** The operator authorised injected taps for this round, at most 5 per run: HS1 is `input tap 407 327` (the "Picture-in-Picture" row of the exit dialog, found from a screenshot of the dialog taken in a recon session with no tap) and HS2 is `input tap 198 334` (the Self Mode button, from R1's screenshot). Two taps in R5 and two in R4. `hand-steps.log` records the times: R5 17:44:13 and 17:44:24, R4 17:46:04 and 17:46:15.
5. **Precheck.** Gearhead `17.9.664004-release`. D-POCO's Bluetooth address `DC:B7:2E:5E:4E:59`, listed under D-HU's bonded devices (also bonded: the other phone, off for the round, and a speaker). `isGroupOwner` false on both units, no group formed. `ro.build.version.sdk` 34. `shared_prefs` on D-HU read `drwxrwx--x u0_a176 u0_a176`, not root-owned.
6. **Settings.** Backup (165 elements) is the restored state of the previous thread; after the round D-HU's `settings.xml` is byte-identical to it. Audio keys read back and not written: `audio-latency-multiplier` 8, `enable-audio-sink` false, `use-aac-audio` false, `audio-queue-capacity` 20. D-POCO was in airplane mode with Bluetooth off for R1 to R3 and put back after R4.
7. **Session formed first time** in R5 and R4 (`SSL handshake complete` once each, no head unit Bluetooth toggle needed). No discard.
8. **Screenshot observations on dimming (unconfirmed, from R1-B's screenshot):** the disabled Self Mode and WiFi buttons render dark grey, and their labels are visibly dimmer than the enabled USB label. Labels stay visible in every case (`vis=visible`). I looked at one screenshot only; the other four are in the evidence.
9. The host stayed at 62C to 69C, throttle delta 0 in every run.

## R0

**PASS**

Build succeeded; unit tests 2446 run, 0 failures, 0 errors; `applyButtonVisibility` in dex: 1; `id/customizationFragment` is `0x7f0900dc` (`CUST_ID`). `state` after install: `commit` `3404e4e41e1e`.

## R1: home screen button states, five selections, no phone

**PASS**

All five cases matched the rule, `clickable` equals `enabled`, the three labels read `vis=visible` everywhere, no `ABSENT`, and each run had `HomeFragment: onResume. isConnected=false` once, `MainActivity` in focus and one `AutomationReceiver: ` line.

| Case | `connection-modes` | self | usb | wifi | settings |
|---|---|---|---|---|---|
| A | `wifi` | no | no | yes | yes |
| B | `usb` | no | yes | no | yes |
| C | `self` | yes | no | no | yes |
| D | empty set | yes | yes | yes | yes |
| E | `usb,wifi,self` | yes | yes | yes | yes |

## R2: legacy single choice, no `connection-modes` key (H2)

**PASS**

Before the run the file had `primary-connection` 2 and no `connection-modes`. The flags equal R1-A (self no, usb no, wifi yes, settings yes), and `R2.written` shows `<set name="connection-modes"><string>wifi</string></set>`. `shared_prefs` was not root-owned. **Finding for the PR owner, not a failure:** a user who once chose WiFi now has Self Mode and USB greyed on the first launch without having unticked anything.

## R3: Customization screen, WiFi only

**PASS**

`SettingsActivity` in focus. `preview_btn_self_mode` and `preview_btn_usb` read `enabled=no`, `preview_btn_wifi` and `preview_btn_settings` read `enabled=yes`, all four `vis=visible`. `row_color_self_mode` and `row_color_usb` read `vis=gone`; `row_color_wifi` and `row_color_settings` read `vis=visible`. The two disabled previews still read `clickable=yes`.

## R5: positive control, Self Mode ticked, session live, projection pinned

**PASS**

State reached: `ssl=0`; `Throughput over ... rendered=[1-9]` 2 between `R5-arm` and `R5-pip`; focus `AapProjectionActivity` before the dialog and `MainActivity` after HS1; `pinned` count 8; `HomeFragment: onResume. isConnected=true` 2 and `MainActivity: Active session detected, bringing projection to front` 0 between `R5-pip` and `R5-home`; `AapService: Disconnected.` 0 before `R5-x-go`; one `SSL handshake complete`.

Verdict conditions: `self_mode_button` `enabled=yes clickable=yes`; `usb_button` `enabled=no`; `wifi_button` yes, `settings_button` yes; focus after HS2 `AapProjectionActivity`; phone `received ByeByeRequest` 1 in `R5-x-go` to `R5-x-done`.

## R4: the point of the round, only WiFi ticked, session live, projection pinned

**FAIL** (the pre-registered result: H1 confirmed on hardware)

The same state as R5 was reached on R4's files: `ssl=0`, rendered 2, focus `AapProjectionActivity` then `MainActivity`, `pinned` 8, `isConnected=true` 2, auto-raise 0, `AapService: Disconnected.` 0, one `SSL handshake complete`, phone `received ByeByeRequest` 1, so the result stands.

- `self_mode_button` reads `vis=visible enabled=no clickable=no`. With a live session in picture-in-picture, the "To Android Auto" button is dimmed and does nothing.
- HS2 left `MainActivity` in front (`R4.focus-after`), so the press did nothing. In R5 the same press raised `AapProjectionActivity`.
- `usb_button` `enabled=no`, `wifi_button` `enabled=yes`, `settings_button` `enabled=yes`.

## Anything the brief did not ask about

- The brief's `ohu_setkeys.py` empty-set bug (setup note 2) will bite any later brief that writes `set:key=`.
- `dumpsys activity top` is fragile on this unit when old tasks are left on the list; force-stop probing apps before using it.
- Evidence: release `rig-evidence-pr-1042-disabled-home-buttons`, asset `pr-1042-disabled-home-buttons-round1-captures.zip` (9578684 bytes), sha256 `4e05845118803a4aed3ebc51004fde8adb7daf6868c6e80af5351a4cf3fa9055`. The APK is not in the zip; its md5 is in the header.
