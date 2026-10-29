# usb-dialog: round 6 results

**Candidate:** `fork/fix/usb-permission-dialog` @ `ceca2126c8a6992b39330bc569b054476a7bd4c8` (tree `a6f5a409fe57d4daf9d615d35db4452a2fbee7dd`)       **Baseline:** none (one arm, as briefed)
**APK md5:** `18a1c2d15bbaa2c3d125dcfc5be82f25` (candidate; the live APK on D-POCO and on D-HP read the same md5, `commit_ceca2126=1` on both)
**Unit:** D-POCO (POCO X3 NFC, API 35) and D-HP (HP Slate 7 Plus, API 17) as USB hosts; D-MOTO (edge 30 neo, API 34) the wired phone; P-1 the list device
**Date:** 2026-10-28
**Evidence:** `usb-dialog-round6-captures.zip` on release `rig-evidence-usb-dialog`, sha256 `9ad1fbdff6455ed3702b8c52baa33b153cc7dcd5aca26300d8e058b8289c1015`

## Round verdicts

| Run | Verdict |
|---|---|
| S1-P-N | UNTESTABLE |
| TD-P | INCONCLUSIVE |
| L-P | INCONCLUSIVE |
| DR-H | PASS (re-run; first attempt INCONCLUSIVE) |
| TD-H | PASS (one measured deviation, see its section) |
| R-H | PASS |
| L-H | PASS |

## Setup notes

- Brief resolved from commit `1c22780c`. Transfer branch fast-forwarded from `a1ca1e5bb`; `rig_sync` reported in sync; no restart needed. Quirk files read: `topics/tooling.md`, `units/D-HP.md`, `units/D-MOTO.md`. The template was read in part only (head); the brief carried the run detail.
- Opening pre-flight, all OK, restored at the close:

  | ROLE | ADB | WIFI | BT | SCREEN | BT-PROFILES |
  |---|---|---|---|---|---|
  | D_HP | ok | 1 | 1 | Asleep | HFP:none A2DP:none |
  | D_POCO | ok | 1 | 1 | Awake | HFP:none A2DP:none |
  | D_MOTO | ok | 1 | 1 | Awake | HFP:none A2DP:none |

- Build: worktree `usb-dialog__scratch__ceca2126__20261028`, `HEAD` `ceca2126`, tree `a6f5a409` as briefed. `strings` checks on the dex: `ohu-usb-ask` 1, `arrived while permission is held` 1, `UsbSysfsNames` 10. Batteries at start: D-HP 99 (brief wants 100; went on at 99, read 100 before stage H), D-POCO 100, D-MOTO 78.
- Prepare: D-MOTO `sys.usb.state=adb`, `sys.usb.config=adb`, `stay_on_while_plugged_in=15`, `screen_off_timeout=1800000` at start. `ifaces.sh 22b8`: `dev 3-2.3 22b8:2e81 cfg=1`, `if 3-2.3:1.0 ff/42/01 name=ADB Interface`. The operator was not asked for, and did not give, the "Default USB configuration" row's original value. It is unrecorded. Both hosts `usbparse` `defaults=0 serial_outside=0`; D-POCO `SYSTEM_ALERT_WINDOW: allow`; neither host had `usb-blacklist` or `allow-devices` keys.
- **Brief script bug (lib6.sh):** `usbup` and `usbgone` return 1 whenever their poll loop iterated at least once, which is exactly when the link came up or went away. First S1-P-N attempt (16:12 to 16:14): after the operator's DEFUSB and REPC the script logged `P-N REPC_NOT_SEEN`, `P-N NO_PC_LINK`, `P-N UNTESTABLE lever` within 18 s of the cue. Run voided, stage P stopped by pid, `return 0` added to both helpers, stage P restarted at 16:14 from S1-P-N. The voided lines stay in `marks.txt` above the `STAGE P RESTART` marker.
- **False `BATTERY_LOW` (stage P):** the script exited 2 at 16:26:55 with `P-TD-p3 BATTERY_LOW ` and an empty reading (`cycle.sh: line 13: [: : integer expression expected`, `adb: device offline`): D-POCO's wireless adb blinked offline and `bat` returned nothing. D-POCO read 98 seconds later. Resumed with `scripts/stageP6b.sh` (the rest of the TD-P arm from P-TD-p3, then L-P and the stage close, same commands as `stageP6.sh`). The capture process had died with the script; `alive` restarted it appending to `P-hu.txt`, so there is a gap from 16:26:55 to the restart, with no plug in it (p1 was unplugged at 16:23).
- **DR-H first attempt contaminated:** the operator pressed Cancel on all 3 dialogs including both Allow ones (rule: Allow, touch nothing). Graded INCONCLUSIVE and re-run at the operator's request as `H-DR2` (`scripts/stageH6c.sh`, the same `tdcycle ... rot` after a CLEAR and `setkeys td`). The re-run is the verdict.
- Prepare 10 (rotation lever): the brief's `rot` helper reads `mRotation=` in `dumpsys window`, and D-HP's API 17 build prints none (`rot` is empty throughout). The fallback `dumpsys display` viewport `orientation=1` did not move for `user_rotation` 1 or 2. I recorded `rot_lever=no`. Yet the lever worked in DR-H: with `rot_before=none rot_want=0 rot_after=none`, `rotate_by=settings` held in both attempts and `H-DR2-p1` logged the false denial 2 s after the request with nobody touching a dialog. So `rot` is a broken probe on this build and the settings write itself is enough.
- D-HP install and identity pull (26 MB) ran over wireless adb while D-HP sat on its cable, about 6 minutes. The operator flagged it; the result is valid (`commit_ceca2126=1`, md5 matches) and large D-HP/D-SAM transfers now go over USB (`rig-quirks/units/D-HP.md` and `D-SAM.md` updated).
- Operator requests: (1) a blank line after every cue in `cues.log`: `lib.sh` `cue()` now appends an empty line, in force for scripts started after 16:26 (stage H and the continuations). (2) "USB controlled by: This device" on D-MOTO: not scriptable, `adb shell cmd usb` answers `No shell command implementation.` on D-MOTO (API 34), so it stays a manual step.
- Unplug detection misfired once: `P-TD-p3` cued "UNPLUG again" at 16:37:04 although `USB_DEVICE_DETACHED` is in the capture at 16:36:08 (end marker 16:35:58). Cause not proven (suspect capture lag over wireless adb after the capture restart). `P-TD-p1` and `P-L3` detected normally. No effect on grading.
- `P-TD-p3` printed `NO_PLUG` although the plug and a session happened: the plug went straight through `UsbAttachedActivity`, so there was no `Normal USB device attached:` line the script greps. Capture: attach 16:35:07, SSL 16:35:15, Media Sink 16:35:16.
- `restart_refused` read 0 in TD-P although the restart WAS refused: the refusal came back in the broadcast result text (`startForegroundService() not allowed due to mAllowStartForeground false`), not as the app log line the brief greps.
- Strays: thermal watcher pid 79755 on `usb-dialog-round5/thermal-I.log` (from round 5, left alone); this round's own orphan watcher from the aborted first stage P run was killed by pid. Host peaked at 75C (stage P), 0 throttle events.
- Scripts used: `get_apk.sh` (steps by hand), `wt-new.sh`, `build_hur_cool.sh`, `rig_thermal.sh`, `rig_preflight.sh`, `extract_brief_blocks.py`, the copied round 5 helpers, and this round's `lib6.sh`, `s6.sh`, `list6.sh`, `stageP6.sh`, `stageH6.sh`, plus `stageP6b.sh` and `stageH6c.sh` (continuations written this round, in `rig-data/rounds/usb-dialog-round6/scripts/`).
- Closing pre-flight (all OK, matches the opening table; D-HP screen Awake now, it was Asleep):

  | ROLE | ADB | WIFI | BT | SCREEN | BT-PROFILES |
  |---|---|---|---|---|---|
  | D_HP | ok | 1 | 1 | Awake | HFP:none A2DP:none |
  | D_POCO | ok | 1 | 1 | Awake | HFP:none A2DP:none |
  | D_MOTO | ok | 1 | 1 | Awake | HFP:none A2DP:none |

  `rig_cleanup.sh` found a leftover `tail` of this round's cue monitor (killed by pid) and an idle Gradle daemon (killed); then no strays, lock free.

## S1-P-N: "No data transfer" proved, the app is offered once and the tick sticks

**UNTESTABLE**

- Settings written: `S1KEYS` via `setkeys s1N`; readback `P-s1N keys listen=true connect=true auto-start-on-usb=false reopen=false`.
- Proof: the operator did DEFUSB at ~16:15 and REPC at ~16:16. `P-N proof1 state=[adb] config=[adb] dumpsys=[]`, phone side passed. After debugging off at 16:16:34 the test PC read `P-N pc dev=1 mtp=1 adb=0 ifaces=[ff/ff/00 name=MTP;]`, the brief's gate printed `P-N PROOF_FAIL pc`, `P-N UNTESTABLE lever`. No plug was run.
- The reading: with debugging off and Default USB configuration set to "No data transfer", D-MOTO still offers an MTP interface to a host. This is the brief's own "lever not proven" case, not a branch result.

## TD-P: a service restart while our dialog is up, API 35, MTP phone

**INCONCLUSIVE**

- Settings: `TDKEYS`; proof `P-TD proof1 state=[mtp,adb]`, `P-TD pc dev=1 mtp=1 adb=0`, `P-TD PROOF_OK` (16:17:57).
- Item 1 holds: plug `16:18:45.789`, `ask_by=plug`, `/pre` `dec_A=ask mtp_A=yes req_A=1 perm_A=1`.
- Item 2 fails (the reason for INCONCLUSIVE): `ACTION_REFRESH_SENSORS` at 16:19:32 returned `result=1 ... startForegroundService() not allowed due to mAllowStartForeground false`. Marks: `NO_RESTART`, `NO_DEC`; `pid_before=21431 pid_after=21431`. That is the API 35 background limit the brief anticipated, with the overlay permission allowed.
- Items 3 and 4 cannot be tested without a restart: `/gap` is empty (`dec_A=-`), DIALOGS answered 1 Allow dialog. After the operator's OK the record logged `16:21:05.998 UsbPermissionAsks: answer for /dev/bus/usb/001/002 (granted=true); entry ended`, but the stopped service never acted (`grants=0`, no switch, no session, the operator confirmed no session formed).
- Plug 3 (`P-TD-p3`): `ssl=2 ms=2 req_A=0 req_B=0`, but `dlg_all=1` (`confirm_x=1`, an Android Open dialog; operator COUNT 1 Open) and `phone_acc` blank (phone dump deferred, `phone=NO_TIMES`). `defaults` rose 1 to 2 (`8888/11906` = 22B8:2E82, then `6353/11520` = 18D1:2D00): the Open dialog at plug 3 was for the accessory identity, which the plug 1 tick did not cover.
- S1-P-F's fields from plug 1: `diag=... (VID: 22B8 PID: 2E82) [class 00/00/00, no permission] accepted: MTP`, `mtp_A=yes`, `route_A=0`, `mtpn=0`, `dec_A=ask`, `ssl=0`.

## L-P: a blacklisted accessory-mode device in the USB list (P-1 identity 3)

**INCONCLUSIVE**

- Settings: `set:usb-blacklist=vidpid:18d1:2d00` via `xprefs`; `P-L3 stored_key=1`. Plug `16:42:57.405`.
- Item 1 holds: `ign_svc=1`, `ign_why=VID: 0x18D1, PID: 0x2D00): rejected: blacklisted by the user`, `ours=0 req=0 fatal=0`, `diag=Magic Communication Tec. Auto Box (VID: 18D1 PID: 2D00) ... rejected: blacklisted by the user`.
- The tap was void: `P TAP_VOID rid=:id/usb_button text= (MATCHES=0)`. D-POCO's dump carries empty resource-ids (text `Connect over USB` is there), so `list0_text=MATCHES=0` and items 2 to 4 were not reached. The key stayed: `P-L3 after key=1 set=[vidpid:18d1:2d00] mirror=[vidpid:18d1:2d00]`. Operator COUNT: 1 Open. Cleared afterwards: `P-L3-cleared blacklist=[]`.

## DR-H: a rebuilt dialog's false denial keeps the entry, API 17

**PASS**

- Settings: `TDKEYS` after a CLEAR. Run as `H-DR2-p1` (re-run); the first attempt `H-DR-p1` was contaminated and is INCONCLUSIVE (operator Cancel on all dialogs; its numbers: `rot_false_kept=2`, `ans_false_ended=0`, pid 7649 both sides, `/gap` `dec_A=asked req_A=0`).
- Re-run, the operator touched nothing before the ANSWER cue. Plug `17:37:48.434`, `ask_by=plug`. Item 2: `17:37:52.444 UsbPermissionAsks: answer for /dev/bus/usb/002/005 (granted=false); entry kept`, `rotate_by=settings`, `rot_false_kept=1 rot_false_ended=0 rot_denied=1`, `/rot req_A=0`.
- Item 3: stop 17:38:34, restart 17:38:39, `pid_before=10172 pid_after=10172`, no `NO_*`, `noperm=1`.
- Item 4: `/gap` `dec_A=asked req_A=0 req_B=0 perm_A=0 dlg_all=0`; DIALOGS: 1 Allow on top, 1 Open under it.
- Item 5: `17:39:42.383 UsbPermissionAsks: answer for /dev/bus/usb/002/005 (granted=true); entry ended`; `17:39:48.114 SSL handshake complete`; `grants=2 post_true_ended=2 sw=1 attach_B=1 ssl=2 ms=4 phone_acc=1`.
- Item 6: `fatal=0`, `ans_false_ended=0`. Operator COUNT: 2 Allow, 2 Open. `confirm_A` in `/pre` was 1.

## TD-H: a service restart while our dialog is up, API 17

**PASS**

- Settings: `TDKEYS` after a CLEAR. `H-TD-p1` was `NO_PLUG` (no attach event 17:11:51 to 17:13:54), so `H-TD-p2` is the graded plug. The operator reported our Allow dialog covered Android's Open dialog, so Open was left uncancelled.
- Item 1: plug `17:14:35.244`, `ask_by=plug`, `req_A=1 perm_A=1`. Item 2: stop 17:15:21, restart 17:15:26, `pid_before=8060 pid_after=8060`, `noperm=1`.
- Item 3: `/gap` `dec_A=asked req_A=0 req_B=0 perm_A=0 dlg_all=0`; DIALOGS 1 Allow. **Measured deviation: `gap_ans=1`, where the brief says 0**: `17:15:26.564 UsbPermissionAsks: answer for /dev/bus/usb/002/003 (granted=false); entry kept`, 1 s after the restart, with no second dialog and no second request. I read the brief's FAIL clause as "a second dialog or request after the restart, or `action=ask` there", none of which happened, and graded PASS. A strict reading of the `gap_ans=0` number would fail item 3, so the operator should decide.
- Item 4: `17:16:12.254 ... (granted=true); entry ended`, `17:16:18.284 SSL handshake complete`, `grants=2 post_true_ended=2 sw=1 attach_B=1 ssl=2 ms=4 phone_acc=1`. Item 5: `fatal=0`, `ans_true_kept=0`. Operator COUNT: 2 Allow, 2 Open.

## R-H: a first connect and a ticked plug, API 17

**PASS**

- Settings: `S1KEYS` via `setkeys s1R` after a CLEAR. `H-R graded=2 retries=1`: `H-R-p2` was `NO_PLUG`, retried as `H-R-p3`.
- Plug 1: `dlg_all=4 confirm_A=1 confirm_B=1 perm_A=1 perm_B=1 req_A=1 req_B=1`, `grants=1 ans_true_ended=1`, `sw=1 attach_B=1 ssl=2 ms=4 phone_acc=1 fatal=0`. Plug 3: `dlg_all=0 req_A=0 req_B=0 sw=1 attach_B=1 ssl=2 ms=4 phone_acc=1 fatal=0`. `ans_true_kept=0 ans_false_ended=0` on both.
- The operator's COUNT was "none", given once for all plugs, which disagrees with plug 1's `dlg_all=4` (a finding, not a verdict).

## L-H: a named key below API 21 from the USB list, one key for every P-1 identity

**PASS**

- Settings: `vidpid:1314:1521` via `xprefs` (`H-L4 stored_key=1`). Plug `17:25:48.294`.
- Item 1: `H-L4 sys_p1=1 sys_line=[/sys/bus/usb/devices/2-1 bus=2 dev=2 id=1314:1521 [Magic Communication Tec.] [Auto Box]]`. Item 2: `H-L4/blk ign_svc=1 ours=0 req=0 sw_lines=0`. Item 3: `list0 blacklisted_nodes=1`.
- Item 4: `after_1 old=0 name=0 vidpid_keys=0 allowed=0`; `after_2 old=0 name=0 vidpid_keys=0 allowed=1`; `after_3 old=0 name=1 vidpid_keys=0 allowed=0`; no `POLL_TIMEOUT`. Item 5: `H-L4-final blacklist=[name:magic communication tec. auto box]`, `list3 blacklisted_nodes=1`.
- Item 6 (identity 3): `ign_why=VID: 0x18D1, PID: 0x2D00): rejected: blacklisted by the user`, `acc=0 found_acc=0 ours=0 req=0 sw_lines=0`, `sys_p1=1 sys_line=[... bus=2 dev=3 id=18d1:2d00 ...]`. Item 7 (identity 1): `ign_why=VID: 0x1314, PID: 0x1520): rejected: blacklisted by the user`, `ours=0 req=0 sw_lines=0`, `sys_p1=1 ... id=1314:1520`. Item 8: `fatal=0` in every row. Operator COUNT: identity 4 and 3, 1 Open each; identity 1, 0.

## Clean-up

`P-L3-cleared blacklist=[]`, `H-L4-cleared blacklist=[]`, `I pset 0 stored0=1` (P-1 back on its console, 17:34:07), `P restored`, `H restored`, `H user_rotation_end=0 accel_end=1` equal to the before values (and `end2` after the re-run), `P-end` and `H-end` `serial_outside=0`. D-MOTO restored at approval: Bluetooth back on (`bluetooth_on=1`), `stay_on_while_plugged_in=15`, `screen_off_timeout=1800000` (unchanged), USB debugging on as found. Its original Default USB configuration was never recorded, so it was not restored and now reads whatever the operator set at DEFUSB. D-HP `stay_on_while_plugged_in=7` and D-POCO `15` match their recorded before values.

## Anything the brief did not ask about

- Setting D-MOTO's default to "No data transfer" does not remove its MTP interface once USB debugging is off (the PC saw `ff/ff/00 name=MTP`). Whatever advice rests on that setting is untested on this phone.
- On API 35 a service restart from the background is refused (`mAllowStartForeground false`) even with the overlay permission, so a "restart while our dialog is up" cannot be driven by `ACTION_REFRESH_SENSORS` on D-POCO. A user grant after that restart finds no service and connects nothing.
- On D-HP the restart worked every time, and kept denials (`granted=false; entry kept`) arrived around the stop and restart (17:15:21.9 and 17:15:26.5 in TD-H, 17:38:34.8 and 17:38:39.6 in DR-H) without opening a second dialog.
- D-HP's USB connector missed three plugs (`H-TD-p1`, `H-R-p2`, and the late attach noted above), each cured by a retry. D-HP's wireless adb is far too slow for 26 MB.
- Our Allow dialog covers Android's Open dialog on API 17, so the Open one cannot be cancelled while ours is on top.
- Three operator counts disagreed with the log or were not split per plug (DR-H first attempt, R-H, TD-P plug 3).
