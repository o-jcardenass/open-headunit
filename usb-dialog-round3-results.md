# usb-dialog, round 3 results

**Candidate:** fork/fix/usb-permission-dialog @ 9f90e121e962ffe2fd70142ccd41ae68485cc8fd (tree dcb105332230c7e644e8df9a7e18be7421ba5e2e), on main 2018d8f09. **Baseline:** none (rounds 1 and 2 cited for "before").
**APK md5:** 8f3889d4b75a4606074d8d4d7282cdef (candidate; the new string "not raised over Android's USB dialog" counted 1 in the dex). Live APK on D-HP and D-POCO matched after install; ACTION_QUERY_STATE read commit 9f90e121e962 on both.
**Unit:** D-HP (HP Slate 7 Plus, API 17) stage H, D-POCO (POCO X3 NFC, API 35) stage P, both as USB host over wireless adb; D-MOTO (API 34) the wired phone, USB debugging on, Bluetooth off.
**Date:** 2026-10-27
**Evidence:** release `rig-evidence-usb-dialog`, asset `usb-dialog-round3-captures.zip`, sha256 568dc70d6f258b46cb831d1346d69932252838abb19e543aee22ad896003ccb7

## Setup notes

- Quirk files read: `topics/tooling.md`, `units/D-HP.md`, `D-SAM-and-D-HP.md`, `D-POCO.md`, `D-MOTO.md` (tail only), template without 8, and 7b. Scripts from the brief extracted with `extract_brief_blocks.py` into the round folder; no toolkit script added.
- Opening pre-flight (`D_HP:wifi D_POCO:wifi D_MOTO:wifi`): PREFLIGHT OK. D_HP wifi 1 bt 1; D_POCO wifi 1 bt 1; D_MOTO wifi 1 bt 0; BT profiles none on all.
- Build: worktree `usb-dialog__scratch__9f90e121__20261027`, HEAD tree matched the brief, `rig_thermal.sh wait 70` read 65C, `build_hur_cool.sh` exit 0. Unit tests not run (brief).
- Prepare: live md5 before install 3d1e486d... on both hosts. D-POCO `SYSTEM_ALERT_WINDOW=allow`, `serial_outside=0`, `defaults=0` (no lasting grant). D-MOTO `stay_on_while_plugged_in` was 15, Gearhead 18.1.164344-release.daily, `sys.usb.config=adb`. Clocks within about 1 s. Prepare step 8 ran in a shell with `RIG_TOOLKIT` unset (lib.sh line 1 failed); the values it uses resolved anyway and nothing was affected.
- **Stage H was run twice.** The first start (13:49) lost R5c-p1 to `NO_PLUG`: the operator did not see the PLUG cue (I had given the cue terminal command with a variable, not the full path). The operator then paused at 13:55:40 during p2; I killed the stage by pid. The restart at 17:20:46 is the one graded. A first-attempt row (p1 `NO_PLUG`, p1 `NO_UNPLUG_LINE`) remains in the capture and in `marks.txt`; no cycle there is graded.
- **Battery deviation.** D-HP read 72% after Prepare and 49% at the restart (brief gate: 60%). The operator authorised starting anyway. I used `scripts/stage3-lowbat.sh`, which differs from `stage3.sh` in one line: the start gate `-ge 60` became `-ge 40`. The 30% stop inside `cycle()` was not changed and not reached (D-HP read 40% after stage H). D-HP was not charged to 100% (brief Prepare 1).
- `stage3.sh` overwrites `backup-H.xml` on a rerun, so the original was copied first to `backup-H-orig.xml`; D-HP settings were restored from that copy and the diff against it is empty. D-POCO restored from `backup-P.xml`, diff empty. `stay_on_while_plugged_in` on D-HP read 7 before and after.
- **D-MOTO's wireless adb dropped after R5c-p1 on stage H.** `moto_dump` printed `UNREACHABLE` for every later H cycle, so `phone_acc` is blank for all stage H runs and the phone-side items (R5c item 6, F4 item 8) are not graded there. A late pull also failed (connection refused); `adb tcpip 5555` over D-MOTO's PC cable restored it before stage P. On stage P every cycle dumped (`phone_acc=1`).
- Operator first words versus the log. F4a, F4b, R4a: "Open", matching `confirm_*`. **R5c on D-HP:** the operator said "Open", but the log has `perm_A=1 perm_B=1 confirm_A=0 confirm_B=0`, two `UsbPermissionActivity` starts, whose text starts "Allow". On D-POCO the operator said "Allow" for the same run, matching the log. I read the D-HP answer as a misreading; the verdict rests on the log.
- Seed: H `recorded=0` (API 17 prints no route line), P `recorded=1`. Both seeds OK at seed1.

## R5c on D-HP (stage H, cycle H-R5c-p3)

**PASS**

- Settings: `listen-for-usb-devices=false`, `auto-start-on-usb=false` plus the brief's base block, `comp=disabled` read back.
- `rx_check=2`, gap 520 ms: `17:21:49.102` and `17:21:49.622` (`AutomationReceiver: ... ACTION_CHECK_USB`).
- A: `req_A=1` (`17:21:49.152 Requesting USB permission for VID: 22B8 PID: 2E81`), `perm_A=1` (start `17:21:49.232`, finish `17:21:57.002`).
- Refusal: `17:21:49.652 UsbLauncher: a dialog for /dev/bus/usb/002/002 is already open; not opening a second` (`refuse=1`). `dec_A=hold,ask,ask`, `src_A=automatic,user,user`.
- B: `req_B=1`, `perm_B=1`, `dec_B=ask`, `src_B=automatic` (decision `17:21:59.242`, `attach=1066ms`, request `17:21:59.252`, dialog finished `17:22:00.622`).
- `confirm_A=0 confirm_B=0 unfinished=0`, `ssl=1 ms=4`, `fatal=0`. Phone `phone_acc` not read (see Setup notes).

## R5c on D-POCO (stage P, cycle P-R5c-p1)

**PASS**

- `rx_check=2`, gap 70 ms: `17:43:13.743` and `17:43:13.813`.
- `req_A=1` (`17:43:13.766`), `perm_A=1` (`17:43:13.808` to `17:43:16.335`), `refuse=1`: `17:43:13.851 UsbLauncher: a dialog for /dev/bus/usb/001/002 is already open; not opening a second`. `dec_A=hold,ask,ask`.
- `req_B=1` (`17:43:18.191`), `perm_B=1`, `dec_B=ask`, `src_B=automatic`. `confirm_A=0 confirm_B=0 unfinished=0`, `ssl=1 ms=2`, `phone_acc=1`, `fatal=0`. Operator: 2 dialogs, both "Allow".

Round 2's reading (`req_A=2 perm_A=2 refuse=0 unfinished=1`) did not repeat on either host: one dialog for A in both cases.

## F4a on D-HP (H-F4a-p1)

**PASS**

- Defaults cleared before the plug (`H-F4a-p1-clear-post defaults=0`), app sent to the launcher, seed OK, `routeSeen=yes,yes`, pid 10143 unchanged.
- `17:27:08.712 CONFIRM_START A`, `17:27:08.792 Reopen on reconnection: not raised over Android's USB dialog (USB normal attach (VID: 22B8 PID: 2E81))` (`hold_A=1 raise_A=0`). B: `17:27:56.522 ... not raised over Android's USB dialog (USB accessory attach)` (`hold_B=1 raise_B=0`). `covered=0`.
- `dec_A=wait dec_B=wait`, `req_A=req_B=0 perm=0 resolver=0`, `confirm_A=1 confirm_B=1` (operator: 2, both "Open").
- LOOK `17:27:24`: operator saw a dialog starting "Open", no Recents press; `focus.txt` read `mCurrentFocus=...UsbConfirmActivity`, and after the session `AapProjectionActivity`. The two reads agree with the `START` order.
- Answer to app: `CONFIRM_FINISH A 17:27:55.602`, `17:27:55.642 USB auto-start: launching app for VID: 22B8 PID: 2E81` (`a_fin_to_auto_ms=40`). `ssl=1 ms=4 open_at_ms=0`, `b_answer_ms=22910`. `after=` pairs `6353/11521 8888/11905`. `fatal=0`. Phone item not read.

## F4a on D-POCO (P-F4a-p1)

**PASS**

- `17:46:04.655 CONFIRM_START A`, `17:46:04.675 not raised over Android's USB dialog (USB normal attach (motorola ...))`, `17:46:31.380 ... (USB accessory attach)`. `hold_A=hold_B=1 raise=0 covered=0`.
- `dec_A=wait dec_B=wait req=0 perm=0 resolver=0 confirm 1+1`; operator saw "Open" at LOOK `17:46:22` with no Recents press (`focus.txt`: `UsbConfirmActivity`, then `AapProjectionActivity`).
- `CONFIRM_FINISH A 17:46:30.594`, `17:46:30.618 USB auto-start: launching app for ...` (`a_fin_to_auto_ms=24`). `ssl=1 ms=2 open_at_ms=0`, both defaults stored, `phone_acc=1`, `fatal=0`. Operator: 2 dialogs, both "Open".

## F4b on D-HP (H-F4b-p1)

**PASS**

- Same reading as F4a: `hold_A=1 hold_B=1 raise=0 covered=0`, `confirm_A=1 confirm_B=1 perm=0 req=0`, `autostart=1 a_fin_to_auto_ms=60`, `ssl=1 ms=4`, `b_answer_ms=8560`, `fatal=0`; LOOK `17:31:17` "Open" visible (focus `UsbConfirmActivity`), no Recents. Operator: 2 dialogs, both "Open". Phone item not read.

## F4b on D-POCO (P-F4b-p1)

**PASS**

- `hold_A=1 hold_B=1 raise=0 covered=0`, `confirm 1+1 perm=0 req=0`, `a_fin_to_auto_ms=21`, `ssl=1 ms=2`, `phone_acc=1`, `fatal=0`; LOOK `17:48:45` "Open" visible, no Recents. Operator: 2 dialogs, both "Open".

## F4c on D-HP (H-F4c-p1)

**PASS**

- 0 dialog starts (`confirm perm resolver` all 0), `req_A=req_B=0`, `main_start=3`, `raise_A=1 raise_B=1 hold_A=0 hold_B=0 autostart=1`, `ssl=1 ms=4`, `fatal=0`. Operator: 0 dialogs. The raise brought the app up, with `UsbAttachedActivity` auto-start alongside. No `hold_A` here.

## F4c on D-POCO (P-F4c-p1)

**PASS**

- Same: 0 dialogs, `main_start=3`, `17:50:44.791 Reopen on reconnection: launching MainActivity (USB normal attach (...))` (`raise_A=1 raise_B=1 hold=0`), `17:50:44.826 USB auto-start` (`autostart=1`), `ssl=1 ms=2`, `phone_acc=1`, `fatal=0`.

## R4a on D-POCO (P-R4a-p1)

**PASS**

- `mtp_A=no`: `stacked=0`, `req_A=0`, `req_B=0`, `dec_A=wait dec_B=wait`, `perm_A=0 perm_B=0`, `confirm_A=1 confirm_B=1`, `open_at_ms=0`, `ssl=1 ms=2`, `phone_acc=1`, `after=` both pairs, `fatal=0`. Operator: 2 dialogs, both "Open". Matches round 2's six cycles.

## Report-back items (brief section 10)

1. **F4a/F4b:** on both hosts `hold_A=hold_B=1`, `raise=0`, `covered=0`; the operator saw and answered Android's "Open" dialog with no Recents; a session followed on all four cycles (`ssl=1`).
2. **R5c:** one dialog for A on both hosts, `refuse=1` with the `UsbLauncher: a dialog for ... is already open; not opening a second` row, `unfinished=0`.
3. **F4c:** the raise path (`raise_A=1 raise_B=1`) with `autostart=1`; `hold_A=0` on both hosts.
4. Other: R4a row above; no voids; no pid changes in any graded cycle (H 10143, P 14482, 15259 after the `setkeys listen-on` relaunch); `serial_outside=0` on D-POCO; D-MOTO `mtp=no` on both hosts; no `perm_*` above 0 outside R5c and the seed cycles.

## Anything the brief did not ask about

- The first `R5c` attempt was lost to the cue terminal, not the code; give the cue-tail command as a literal path in the brief's section 5.
- D-MOTO's wireless adb drops after its first accessory switch on D-HP's stage and did not come back; the brief's `moto_dump` retry (60 s) is not enough there.
- D-HP drained from 72% to 40% in about 80 minutes of OTG feeding.
- D-POCO had 27% at Prepare; it charged to 54% by stage P and ended the stage without trouble.
- Seed cycles: H-seed1 and P-seed1 each ended with both defaults stored (`6353/11521`, `8888/11905`).
