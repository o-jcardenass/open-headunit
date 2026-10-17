# usb-reattach, round 3 results

**Candidate:** `fix/usb-reattach` @ `bcf3b1a3bff1672578ca3750bf60aadef7faf503` (tree `d817c5a28247bb6d2d0f6afa78f245e06d8f5b13`, on `main` `71375a68`). Export build E = C + local commit `ea01d91d1e49` (one manifest attribute), never pushed.
**Baseline:** none built; control numbers from rounds 1 and 2 and `pr-1047-session-reconnect-round3`.
**APK md5:** C `3e6bbe7b61bf2e3460bf05ae89894ce6` / E `c6ab904bedb27b45c1dfa168185ab031` (each from a real `adb pull` of the installed APK, equal to the local `md5sum` of the built file; they differ). sha256 C `ddbd30005f44...`, E `417100dd9215...` (first 12 hex).
**Unit:** D-POCO (Xiaomi POCO X3 NFC `M2007J20CG`, Android 15, API 35, not rooted) as head unit on wireless adb; D-MOTO (motorola edge 30 neo, Gearhead 17.9.664004-release) as phone; dongle `carplay_box_F96B` for Stage D.
**Date:** 2026-10-09
**Evidence:** release `rig-evidence-usb-reattach`, asset `usb-reattach-round3-captures.zip`, 9857400 bytes, sha256 `ced6a2109157ebf0153264793b27f20cf2bfe719478706dda722fed7b829c7c6`.

## Setup notes

- **Brief commit and skill.** The round was resolved from the transfer-branch commit `057d8abf` whose subject is `usb-reattach: publish round 3 brief`; thread and round were taken from the file it adds. The `rig-round` skill's SHA step was changed to do that.
- **Quirk files read:** `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-POCO.md`, `rig-quirks/units/D-MOTO.md`.
- **Pre-flight** (`rig_preflight.sh D_POCO:wifi,bt D_MOTO:wifi,bt`): first run FAILED, D-POCO Bluetooth off (`bluetooth_on=0`); the operator turned it on, second run `PREFLIGHT OK` (D-POCO wifi 1, bt 1, Awake; D-MOTO wifi 1, bt 1, Awake). Later pre-flights used D-MOTO only through the script and D-POCO by hand (wifi 1, bt 1, `mWakefulness`), because `rig_devices.sh` lists D-POCO's USB serial `4f4027e9` and re-exports it, so the script cannot see the wireless serial `192.168.1.8:5555`.
- **Battery.** D-POCO read 74% at Prepare against the brief's 80%; the operator waived the charge gate. Levels: 75% at R2A, 70% R2K, 63% before Stage U close, 46% after Stage D. `bat_gate` (25%) never fired.
- **Builds.** `build_hur.sh` has no worker option, so `GRADLE_OPTS=-Dorg.gradle.workers.max=2` was passed. Host below 70C before each build (62C, 69C); package max 83C during the C build, `throttle_pkg=0`. The worktree's Gradle daemon was stopped with `./gradlew --stop`. `git ls-remote fork fix/usb-reattach` printed `bcf3b1a3...`; worktree tree matched.
- **Identity gate (1a).** On C and E: `PROJECTION_UNRAISED` 1, `StaleAccessoryRecoveryPolicy` 10, the not-reconnecting string 1. `exported=true` on `main.SettingsActivity`: C 0, E 1. `ACTION_QUERY_STATE` commit `bcf3b1a3bff1` on C and `ea01d91d1e49` on E. `settings.xml` was identical before and after every install; the Prepare backup has 0 key/value differences from round 2's (only element order).
- **`apks/` disappeared mid-round.** The whole `hur-wifi-test-scripts/apks/` directory and the pulled `installed-C.apk` vanished at about 13:08 (a host reorganisation by another session, found out later). C was re-pulled from the device and E copied from the worktree build output; both md5s equal the originals. The round was stopped once at the operator's request for that reorganisation and resumed on the operator's `go`; paths in the round scripts were rewritten by that session (`rig-data/rounds/usb-reattach-round3/`).
- **D-MOTO WiFi.** D-MOTO was joined to D-HU's leftover WiFi Direct group (`DIRECT-QS-...`, `192.168.49.28`), unreachable from the host; `adb connect` hung and was killed by pid. The operator rejoined it to the home network by hand (`192.168.1.5`). No WiFi lever was used by the tester.
- **Libraries.** Stage U: `libreattach3.sh` cut verbatim from the brief (149 lines, `bash -n` clean); `ohu_setkeys.py` check `grep -c 'if v else \[\]'` printed 1. Stage D: `pgrep -fc "^adb .*logcat"` replaced `ps aux | grep -c "[l]ogcat"` in `ohu_lib.sh` and `ptr_lib.sh`. Three defects in the Stage D libraries, all worked around in `stageD/env.sh` or by hand: (1) `th_pkg` needs `../rig_thermal.sh` from the run directory (symlinked); (2) the library `pocoput` returns grep's status of an immediate read-back and races the write, so `r_open` returned failure with the keys actually written (overridden with a retrying version); (3) `th_gate` starts a background `th_watch` that is never stopped, outlives the script and holds `/tmp/ohu-rig.lock`, which blocked two later launches (killed by pid four times). The first Stage D launch also printed nothing for 9 minutes: that was cause (3) holding the lock, not the app.
- **D-POCO USB bus empty twice.** After the cable change D-POCO listed no USB device (`/sys/bus/usb/devices` empty, port `current_mode=none`); one reseat cue (the brief allows one) produced `18d1:2d00`. Before the resume it read empty again (the dongle was no longer seated, after the pause) and one more reseat was asked for.
- **Taps (all injected, 4 in U1, none elsewhere).** `taps.log`: U1-E tap1 toggle (2248,713) 15:32:59; tap2 save (2085,87) 15:33:04; tap3 toggle (2248,713) 15:34:07; tap4 save (2085,87) 15:34:11. Coordinates come from `uiautomator dump` targets (U0 dump gave the save target at x=2269; the injected one used the cycle's own dump). The earlier aborted U1 attempt made no tap.
- **Cues:** H1 "plug" (D-MOTO to D-POCO's OTG port, "USB controlled by: This device"), H1b (swap to the dongle), a reseat cue during U0 (twice, see above), H1c (unplug the dongle). No `REC=` line in any run, no system USB dialog, no `HAND_SSL`.
- **Crash dialog (see Anything the brief did not ask about).** The first U1 attempt and U0 each ended with an "Application Error" dialog on D-POCO. The first U1 attempt (`U1-E.attempt1.logcat`) was killed by pid before any tap and its dialog dismissed with a HOME key event; U1 was then run again from its start (`u1only.sh`). That attempt made no tap and is not graded.
- **Logcat readers:** `readers` printed 0 before and after every Stage U run; Stage D checked with `pgrep -fc`.

## R2A (Stage U, C, form A)

**PASS**

- Settings written (`KEYS`): `wifi-connection-mode=0`, `log-level=2`, `onboarding-version=2`, `kill-on-disconnect=false`, `auto-connect-last-session=false`, `auto-connect-single-usb=false`, `auto-start-on-usb=false`, `reopen-on-reconnection=false`, `use-libusb=false`, `connection-modes={usb,wifi}`, deleted: starvation cap, banner records, legacy mode keys.
- Discard rule: `match=0` in all 6 cycles; `readers_after=0`.
- `nfail R2A` = 5 (c1 to c5). Every cycle ended `AUTO_SSL`; no `REC=`, no `STOPPED at`.
- Per cycle: c0 `fails=2 kinds=SSL,SSL step1=1 step2=1 re1=0 nc1=1 checks=1 checks_ran=1 replay=1 sfail=2 dt_ms=9203 ph.enter_auto=1 ph.gh=378`; c1 to c5 each `fails=1 kinds=TRANSPORT_ERROR step1=1 re1=0 nc1=1 step2=0 checks=1 checks_ran=1 replay=0 busy=0 arb=0 sfail=1 ssl_auto=1 ph.enter_auto=0`, `dt_ms` 10274, 10227, 10249, 10291, 10272, `ph.gh` 405, 385, 411, 402, 405. `miss=0 extra=0` in all.
- Decisive lines, c1 (13:13:42 to 13:13:52): `13:13:42.225 UsbLauncher: stale accessory motorola motorola edge 30 neo (VID: 18D1 PID: 2D01): handshake failed (TRANSPORT_ERROR), step 1 of 2: RESWITCH`; `13:13:47.800 ... no re-enumeration within 5054ms of RESWITCH; trying the handshake once more`; `13:13:52.476 SSL handshake complete`.
- Item 0 (slot freed): c0 took both steps, `13:12:58.094 USB_RESET re-enumerated the phone in 742ms`, then `13:12:58.118 UsbLauncher: replaying the USB scan queued during the attempt (accessoryOnly=false)`; the next cycles' check verbs each ran (`checks_ran` equals `checks` over c0 to c5, 6 of 6).
- My own recount over c0 and c3: checks 1, `Found device already in accessory mode` 2 and 1, terminal failure lines 2 and 1, replay 1 and 0, equal to the script's counts.

## R2K (Stage U, C, form K)

**PASS**

- Same keys as R2A. `nfail R2K` = 6 (c0 to c5), every cycle `AUTO_SSL`, `match=0`, `miss=0`, `extra=0`, `checks_ran` equals `checks`.
- Per cycle (identical shape): `fails=2 kinds=SSL,SSL step1=1 step2=1 re1=0 nc1=1 re2=1 nc2=0 left=0 rstok=1 rstno=0 replay=1 sfail=2 unative=2 avc=0 ssl_auto=1`; `dt_ms` 9052, 9231, 8980, 9031, 8982, 8885; `ph.enter_auto=1` and `ph.gh` 371, 370, 435, 364, 366, 370.
- Item 4 (reset steps): `rstok + rstno` = 1 = `step2`; `re1+nc1+re2+nc2+left` = 2 = `step1+step2`. Per cycle the `USB_RESET` step line, the `USB reset issued to` line and the observation (`re-enumerated the phone in`): c0 13:18:17.271, 13:18:18.208, 315 ms; c1 13:18:55.543, 13:18:56.271, 714 ms; c2 13:19:34.226, 13:19:35.163, 309 ms; c3 13:20:13.105, 13:20:14.038, 307 ms; c4 13:20:51.244, 13:20:52.177, 306 ms; c5 13:21:29.431, 13:21:30.159, 307 ms. Issue delay 0.7 to 0.9 s after the step line; exactly one observation line per step.
- Reset part: `re2` 6 of `step2` 6 across the run, with the phone's own `entering USB accessory mode` seen in all 6 (`ph.enter_auto=1`); `re1` 0 of 6 `step1` (the re-switch never re-enumerated, as in rounds 1 and 2).

## R2T (graded over R2A and R2K)

**PASS**

- `miss=0` and `extra=0` in all 12 cycles; `misses.log` is empty (0 lines).
- Counted failures: R2A 7 (2+1+1+1+1+1), R2K 12 (2 per cycle); sum of `fails - exempt` = 19 (exempt 0), threshold 3. `sfail` equals `fails` in every cycle (7 and 12), so no finding there.
- Kinds: R2A c0 `SSL,SSL`, c1 to c5 `TRANSPORT_ERROR`; R2K every cycle `SSL,SSL`.

## P1 (Stage U, C, raise deadline)

**PASS**

- Settings: `P1KEYS` (`auto-connect-single-usb=true`, rest as `KEYS`). `sdk=35`; overlay app-op `default` before, `deny` during (`overlay_now=SYSTEM_ALERT_WINDOW: deny`), restored to `default`; `screen_off_timeout` 1800000 before and after. Setup checks held: `fg_ours=0`, `wake=wake_end=mWakefulness=Awake`.
- `P1 ends=2 recovered_ends=1 held_ends=1 notrec=1 notrec_within_2s=1 recovered_end_rescheduled=1 raise_notif=4 raise_direct=0 raise_overlay=0 throughput=0 ssl_before_hold=2 ssl_after_hold=0 found_after_hold=0 sched_after_hold=0 single_after_hold=0 hold_at_ms=71354 quiet_span_ms=61221 attach_after_hold=0 ssl_after_attach=0`.
- Decisive lines (13:22:57 to 13:24:08): `13:23:15.472 AapService: raising the projection by NOTIFICATION (overlay=false, foreground=false)`; `13:23:23.494 ... NOTIFICATION (overlay=false, foreground=false)`; `13:23:31.508 AapService: the projection screen never came up, so this session can carry nothing. Ending it so the phone can start a new one.`; `13:23:31.543 AapService: USB disconnect. Scheduling reconnect check in 3000ms...`; second session `13:23:52.362 SSL handshake complete`, two more NOTIFICATION raises at 13:23:52.369 and 13:24:00.387; `13:24:08.404 AapService: the projection screen never came up again, so this session ends and automatic reconnection waits until something asks for it.`; `13:24:08.439 AapService: not reconnecting by itself, the projection screen could not be raised.`
- Phone: `ph.gh_before_hold=116`, `ph.gh_after_hold=7`, `ph.BYEBYE=4`, `ph.ByeBye=0`, `ph.gal_deliberate=0`, `ph.enter_after_hold=0`, `ph.crit_after_hold=0`. What Android Auto did in the 60 s after the second ByeBye (`P1.phone_after_hold`, 10 lines): at 13:24:12.935 `USB_ISSUE_MENDEL_EVENT_TIMEOUT_OCCURRED` and `USB_ACCESSORY_TO_PROJECTION_TIMEOUT`, at 13:24:14 it disconnected its car client, unbound and destroyed the car service, then logged nothing until the end marker at 13:25:10: nothing, no reconnect attempt and no error screen.
- Hold at 71.4 s from the trigger; no scan, SSL, reconnect check or single-USB connect for 61.2 s afterwards (no attach on the bus either).

## U0 (Stage D setup and gate, E)

**PASS**

- Wireless adb answered (`M2007J20CG`); no 5277 listener (0 matches on `:149D ... 0A`); D-MOTO `enabled: true`, `state: ON`; the dongle was at `18d1:2d00` (vendor_id 6353, product_id 11520) on D-POCO's bus after the reseat cue.
- 1a gate on E passed (see Setup notes). Screen check: `dest_id` 2131296365, `open_audio` returned 0 by route `am`, `ui_dump` plus `target` printed two pairs (settingSwitch 2248 713; save_button_widget 2269 87), `close_settings` ok, no tap made.
- Keys written: `U1KEYS` (`wifi-connection-mode=3`, `auto-connect-last-session=true`, `auto-start-on-usb=true`, `reopen-on-reconnection=true`, `kill-on-disconnect=false`, `use-libusb=false`, `native-aa-wake-damage-verdict=0`, `log-level=2`); read back from the device.
- Session: `U0 usb_live ok`; `U0 quiesce=1 native=0` (`stopping the wireless stack for the duration of it` followed the first session). A crash dialog appeared in this window; see below.

## U1 (Stage D, E, USB Save with the settings screen open 35 s)

**FAIL**

The brief's FAIL clause reads "`held` 1 or more"; `held` is 1 in both cycles. The measured effect on the thing the item is about is far better than the control, and the single hold has a visible, separate cause; both are reported below, but the verdict follows the stated condition.

- Keys: `U1KEYS`. 2 completed cycles, 4 taps (listed in Setup notes), `phone_fatal=0`, `fatal=0`, `crit=5` per cycle (report only).
- U1-1 and U1-2 (identical shape): `retry=1 detach=4 attach=12 reattach=1 held=1 tries_before_ssl=3 ssl_before_close=1 quiesce=2 arm_in_window=0 pokes_in_window=0 fallback_run=0 fallback_other=1 raise_refused=1 settings_restart=1 usb_retry_line=2 accstart=1 stale_steps=0 replay=1`; `t_ssl2` 9912 ms and 9802 ms. Pre-flight `native=0 quiesce=1`.
- The pass items that held: `retry` 1, `ssl_before_close` 1 or more with `t_ssl2` far below 35000 ms, `tries_before_ssl` 3, `quiesce` 2, `arm_in_window` 0, `pokes_in_window` 0, `fallback_run` 0, `raise_refused` 1, `settings_restart` 1, `fatal` 0, `phone_fatal` 0. The one that did not: `held` 0.
- Decisive lines, cycle 1: `15:33:02.765 CommManager: audio settings changed; reconnecting the projection session` (T0); `15:33:02.831 SettingsRestart: route=USB retry=run`; `15:33:04.633 Handshake: Version request/response failed after 1 attempt(s)` (stale data, 89694 bytes drained first); `15:33:08.073 UsbLauncher: USB auto-connect held while the settings screen is open; checking again when it closes.` (the attach screen's own scan, after the dongle's USB_DEVICE_ATTACHED at 15:33:08.010); `15:33:10.078 Sending acc start`; `15:33:10.585 UsbLauncher: replaying the USB scan queued during the attempt (accessoryOnly=true)`; `15:33:12.677 SSL handshake complete` (9.912 s after T0); `15:33:12.682 stopping the wireless stack ...`; `15:33:12.683 AapService: Not raising the projection, the settings screen is open`. Cycle 2: T0 `15:34:10.094`; hold `15:34:16.238`; `Sending acc start` `15:34:18.246`; `SSL handshake complete` `15:34:19.896` (9.802 s).
- Compared with the control (`pr-1047-session-reconnect-round3`: `held` 5 per cycle, `ssl_before_close` 0, SSL at 46 to 51 s), this build forms the session behind the open screen after 9.8 to 9.9 s with one hold, released by the replay, not by the screen closing. `accstart` is 1 per cycle (no double switch), `stale_steps` 0 (no ladder line on the dongle), `replay` 1 per cycle.
- Report: `detach` 4, `attach` 12, `reattach` 1, `fallback_other` 1, `usb_retry_line` 2 per cycle. A later hold line at 15:33:42.188 and 15:34:49.221 (`...the settings screen closed with a USB auto-connect held behind it, checking USB now`) falls outside the 35 s windows.

## Anything the brief did not ask about

- **App crash at every restart when `auto-start-on-usb=true` on API 35.** After `am force-stop`, Android 15 re-delivers `LOCKED_BOOT_COMPLETED` to the unstopped package (`PACKAGE_UNSTOPPED`) and `BootCompleteReceiver` calls `startForegroundService`, which throws. Lines: `15:31:27.039 BootCompleteReceiver.onReceive | Boot auto-start: received action=android.intent.action.LOCKED_BOOT_COMPLETED`; `15:31:27.043 ... USB auto-start enabled, starting AapService to check USB`; `15:31:27.047 startForegroundService() not allowed due to mAllowStartForeground false: service .../aap.AapService`; `15:31:27.062 FATAL EXCEPTION: main ... Unable to start receiver ...BootCompleteReceiver: android.app.ForegroundServiceStartNotAllowedException`. It put an "Application Error" dialog on D-POCO (reported by the operator as "OHU stopped"). The same redelivery appears in every Stage U capture (`BootCompleteReceiver.onReceive` 4, 24 and 4 times in R2A, R2K, P1) but was harmless there because `auto-start-on-usb=false` made it log `disabled, skipping`. `BootCompleteReceiver.kt` has 0 diff lines between `main` and the candidate, so this is not from this branch. The session still formed in U0 and U1 after the crash, and U1's windows count `fatal=0` because the crash precedes each run's launch. Whether the receiver crashes on a real boot, not only on this force-stop artefact, was not tested.
- **Stage U needed no hand step.** No `REC=` line, no `HAND_SSL`, no dialog. R2A c0 and every R2K cycle took the SSL form at c0/c0 to c5 (form K) and recovered through `RESWITCH` then `USB_RESET` with a replay.
- **Items 3 and 4** have no cheap rig trigger (brief section 9): not measured.
- **D-POCO is at 46% after the round and 51% at the final check; it was never charged during Stage D.**
- Final state restored: candidate C reinstalled (md5 `3e6bbe7b...`), `settings.xml` diff against the Prepare backup empty, overlay app-op `default`, `screen_off_timeout` 1800000, USB grants unchanged (no "Always", diff empty), D-MOTO `stayon false`, no logcat reader (`pgrep -fc` 0), leftover `th_watch` processes killed by pid.

## Cleanup process and data locations (for the coding agent)

- **Layout since the 2026-10-09 host reorganisation.** Scripts and docs are in `rig-toolkit/` (`TOOLS.md` is the inventory); captures, JSON and screenshots in `rig-data/rounds/<topic>-round<N>/`; APKs in `rig-data/apks/<topic>-round<N>/`; extra worktrees only under `worktrees/` (made with `rig-toolkit/wt-new.sh`). This round's data is in `rig-data/rounds/usb-reattach-round3/` (`stageD/` holds Stage D) and its APKs in `rig-data/apks/usb-reattach-round3/`. A hook refuses captures or APKs inside the toolkit, new top-level folders, and toolkit scripts without a `TOOLS.md` entry.
- **What this round did at its end:** restored D-POCO to the Prepare backup (diff empty) with candidate C reinstalled; restored the overlay app-op and screen timeout; stopped D-MOTO's stay-awake; killed every logcat reader and leftover `th_watch` loop by pid (never `pkill -f`); ran `rig_cleanup.sh list` and `studio-clean.sh status`. The capture zip was made only to upload and is deleted afterwards; the data stays in `rig-data/rounds/` until the operator moves it.
- **Still open for the operator:** the export worktree (`arm-E-r3`, local commit `ea01d91d1e49`, never push) and the transfer worktree under `worktrees/` can be removed once this results file is accepted.
- **Rig-script defect to fix before the next Stage D round (lib1047 family):** `th_gate` starts `th_watch` in the background and nothing stops it, so it holds `/tmp/ohu-rig.lock` after the script ends and the next launch waits silently. Stop it with `kill $THPID` at the end of every run script. The library `pocoput` also needs a retrying read-back (see Setup notes).
