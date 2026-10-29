# usb-reattach, round 4 results

- **Candidate:** `fix/usb-reattach` `b48da5287ec0694d55e63b66803572412bc3c379`, tree `67051a2be3aac997e134fbb8797544cbad87e0d3` (matches the brief).
- **Export build E:** the candidate plus `android:exported="true"` on `.main.SettingsActivity`, local commit `7778c45fb5b7`, never pushed. `ACTION_QUERY_STATE` reports `7778c45fb5b7-dirty`.
- **Baseline:** none built. The control is round 3's U1 on `bcf3b1a3`: `held=1` per cycle, `t_ssl2` 9912 ms and 9802 ms.
- **APK md5s:** E `1cc484c3765295ee7e009e7146deb4b8` (local md5sum of the built file equals the md5 of the pull from D-POCO). Prepare APK restored at the Closing `fdda065cedc4af01bcaad029147901dd`.
- **Unit:** D-POCO (M2007J20CG, API 35, wireless adb) as head unit with the dongle `carplay_box_F96B` on its OTG port; D-MOTO (Gearhead 17.9.664004-release) as phone.
- **Date:** 2026-10-09.
- **Evidence:** `usb-reattach-round4-captures.zip` on the release `rig-evidence-usb-reattach` (1813774 bytes, sha256 `5687c78a3b4c87d574a50f437e896aa619aa39dc50c674549c2a97215907cbf7`).

## Setup notes

- **Manifest edit:** the first attempt to apply the E manifest edit was refused by the permission classifier. The operator then allowed it and the same commands ran. Gates read back: tree `67051a2b`, 1 file changed 1 insertion, `HEAD^` = `b48da528`.
- **Build:** cooled to 57C first, `build_hur.sh` with `GRADLE_OPTS=-Dorg.gradle.workers.max=2`, APK copied at once to `export-b48da528.apk`, daemon stopped. `local.properties` copied into the worktree, which is the likely cause of the `-dirty` suffix.
- **Preflight (rig_preflight.sh D_POCO:wifi,bt D_MOTO:wifi,bt):** D_POCO adb ok, wifi 1, bt 1, Awake; D_MOTO adb ok, wifi 1, bt 1, Awake; PREFLIGHT OK. D-POCO battery 65% (66% at U0), API 35. D-MOTO unlocked by the operator; bonded to the dongle.
- **Prior APK differs from round 3's note:** the APK on D-POCO at Prepare was `fdda065c...`, not round 3's `3e6bbe7b...`. It was backed up and reinstalled at the Closing (md5 equal).
- **Settings delta at Prepare vs round 3's backup:** `wifi-connection-mode` 1 to 3, plus `advanced-settings-active`, `native-poke-bt-macs`, and the `wifi-direct-*` bookkeeping keys. All overwritten by the U1 key set and restored at the Closing; the restore diff differs only in XML serialization (`<string name="last-connection-ip"></string>` vs the self-closed form, and the XML header).
- **Settings after the E install:** diff before/after install was empty.
- **Quirk files read:** `rig-quirks/topics/tooling.md`, `units/D-POCO.md`, `units/D-MOTO.md`.
- **Scripts:** copied `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh`, `lib1047.sh`, `lib1047r3.sh` from round 3 Stage D (`grep -c 'if v else \[\]' ohu_setkeys.py` = 1). `env.sh` was NOT copied: it sets `OUT` to round 3's folder and would have redirected this round's output. `libreattach4.sh` saved as in the brief. `bat_gate` is not defined in any library, so `libreattach4.sh` carries a one-line version (level above 25, battery log). U0 and U1 ran as one driver script (`rounddrv.sh`) under `flock /tmp/ohu-rig.lock`; the closing ran as `closing.sh`. Both are in the zip.
- **U0 order:** capture and the screen check were started before the keys were written, as the brief orders them. `send` markers before the first launch land nowhere (the app was force-stopped), which affects only `U0-closed`.
- **Hand steps:** one batched request (H0 unlock D-MOTO, H7 cue terminal, H1 dongle to OTG) and the Closing cue H1c. No H1r, no H2, no system dialog.
- **Injected taps (`taps.log`):** 18:21:56 toggle 2248 713; 18:22:00 save 2085 87; 18:23:03 toggle 2248 713; 18:23:08 save 2085 87. 4 of the 5 budget. U0 injected none.
- **Crash dialog:** `dialogs.log` does not exist: no "Application Error" dialog held focus at any `clear_crash`. The expected `BootCompleteReceiver` crash was logged once (18:21:27.757, `ForegroundServiceStartNotAllowedException`), before the first Save; `pre_crash=1`, `boot=1`.
- **Thermal:** host 53 to 61C, `throttle_pkg=0` throughout U0 and U1 (`U1-E.thermal` max 60C).
- **Windowing:** the unit's `logcat -c` worked; all counts are windowed by the Save line (`SAVE_LN`, `T0`) as the brief's block does, and the grading greps below were re-run by the host over the whole capture and agree.
- **Preflight per run:** `readers` 0 at start and end; `keys_ok ok` twice in `keys.log`; `commit` = E's 12 characters; `quiesce` 1 after the first session; `MATCH! Starting AapService` 0.

## U0

**PASS**

- Wireless adb answered. No `:149D` listener on D-POCO. Install over the Prepare APK left `settings.xml` unchanged.
- 1a gate: `checkOnRequest` 1, `PROJECTION_UNRAISED` 1, `StaleAccessoryRecoveryPolicy` 10, `exported(...)=true` count 1; installed md5 = built md5 `1cc484c3765295ee7e009e7146deb4b8`.
- Screen check: `audioStreamSettingsFragment=2131296365`, `open_audio` rc 0 route `am`, targets `settingSwitch` 2248 713 and `save_button_widget` 2269 87, `close_settings` done. No tap in U0.
- `usb_live` rc 0; `U0 quiesce=1`. `ACTION_QUERY_STATE`: `"commit":"7778c45fb5b7-dirty"`, `"wifiMode":"NATIVE"`, `"connected":true`.

## U1

**PASS**

Two completed cycles, one run (`U1-E`); `U1b-E` not needed. Preflight line: `native=0 quiesce=1 pre_crash=1 boot=1`.

| Count | U1-1 | U1-2 | PASS rule |
|---|---|---|---|
| retry | 1 | 1 | 1 |
| detach / attach / reattach | 4 / 12 / 1 | 4 / 12 / 1 | report |
| **handoff** | **2** | **2** | 1 or more in one cycle |
| **handoff_next** | `single;replay;` | `single;replay;` | no `held` |
| **held** | **0** | **0** | 0 |
| tries_before_ssl | 3 | 3 | report |
| **t_ssl2** | **9865 ms** | **9869 ms** | 35000 or less (round 3: 9912, 9802) |
| ssl_before_close | 1 | 1 | 1 or more |
| quiesce | 2 | 2 | 1 or more |
| arm_in_window / pokes_in_window | 0 / 0 | 0 / 0 | 0 / 0 |
| fallback_run / fallback_other | 0 / 1 | 0 / 1 | fallback_run 0 |
| raise_refused | 1 | 1 | 1 or more |
| settings_restart | 1 | 1 | 1 |
| usb_retry_line | 2 | 2 | report |
| **accstart** | **1** | **1** | 1 or less |
| replay | 1 | 1 | report |
| stale_steps / close_release | 0 / 0 | 0 / 0 | report |
| crit (phone) | 5 | 5 | report only |
| fatal / phone_fatal | 0 / 0 | 0 / 0 | 0 / 0 |

Whole-capture host re-check: `held` 0, hand-off lines 4, `FATAL EXCEPTION` 1 (the pre-Save boot crash), `MATCH! Starting AapService` 0.

Hand-off line and the lines after it (U1-1; Save at 18:21:59.340):

```
18:22:03.638 UsbAttachedActivity.onCreate | UsbAttachedActivity: settings on show or the status pill's X holding; handing /dev/bus/usb/001/008 to the service
18:22:03.656 UsbLauncherManager.performSingleConnect | Single USB auto-connect: connecting to Google Pixel 4 (VID: 18D1 PID: 4EE1)
18:22:03.661 UsbAccessoryMode.switch | Sending acc start
18:22:04.138 UsbAttachedActivity.onCreate | ... handing /dev/bus/usb/001/009 to the service
18:22:04.164 UsbLauncher: replaying the USB scan queued during the attempt (accessoryOnly=true)
18:22:04.168 Found device already in accessory mode: Google Pixel 4 (VID: 18D1 PID: 2D00)
18:22:09.190 SSL handshake complete
18:22:09.195 Not raising the projection, the settings screen is open
```

U1-2 (Save at 18:23:07.0): hand-off 18:23:11.582, `Single USB auto-connect` 18:23:11.599, `Sending acc start` 18:23:11.602, second hand-off 18:23:12.077, SSL 18:23:16.759, `Not raising the projection, the settings screen is open` 18:23:16.765.

The fix works as designed: the hand-off scan is no longer held, it switches the dongle itself once (`single`), the 2 s attach fallback queues behind the busy slot and replays accessory-only (`replay`), and no second switch goes out for that return.

## Anything the brief did not ask about

- **The session behind the screen did not survive the close, in both cycles.** At the screen close the projection is raised (18:22:36.301, 18:23:43.969), the transport read loop starts only then, the first write fails (`send incomplete (ret=-1 of 506)` 18:22:36.330) and the session ends `link_lost` (18:22:36.374, 18:23:44.028). The dongle re-enumerates, `Sending acc start` goes out again 18:22:39.586 and 18:23:47.523 (about 39 s after each Save, outside the 35 s window the brief grades), and a new session reaches SSL at 18:22:44.617 and 18:23:52.201 and projects. I have no pre-fix capture of the close, so this is not shown to be new or old. It is not a FAIL condition of the brief.
- `crit` on the phone is Gearhead's `GH.WirelessStartup: Critical error encountered` (and `CAR.SERVICE: Critical error 4`), 5 per cycle, report only.
- `BootCompleteReceiver` crash on API 35 occurred once as the brief expected; not chased.
- Closing: `headunit://exit`, force-stop, Prepare APK reinstalled (md5 `fdda065c...` equal), settings restored (diff serialization only), USB grants diff before/after empty, D-MOTO `stayon false`, `adb usb` run after cue H1c, D-POCO answers on its PC cable. No logcat readers; no thermal watcher.
