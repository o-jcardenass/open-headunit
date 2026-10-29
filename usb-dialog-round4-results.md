# usb-dialog, round 4 results

**Candidate:** fork/fix/usb-permission-dialog @ 3dcbf76a7256f25a9a4015e834db1f960d920773 (tree fb4922ddd741b878d96c57d5a626667ba011628b), on main 2018d8f09. **Baseline:** none (rounds 1 to 3 cited for "before").
**APK md5:** 7c942997196b5391dd23f6d38f83ca2b (candidate; both new strings counted 1 in the dex, 0 in round 3's APK, md5 8f3889d4b75a4606074d8d4d7282cdef). Live APK on D-HP and D-POCO matched after install; ACTION_QUERY_STATE read commit 3dcbf76a on both.
**Unit:** D-HP (HP Slate 7 Plus, API 17) stage H, D-POCO (POCO X3 NFC, API 35) stage P, both USB hosts over wireless adb; D-MOTO (API 34) the wired phone, USB debugging on, Bluetooth off.
**Date:** 2026-10-27
**Evidence:** release `rig-evidence-usb-dialog`, asset `usb-dialog-round4-captures.zip`, sha256 9d7c300ec0962872d52226b68230324881d0d77e65f1eec18b3035cf37f581ae

## Setup notes

- Quirk files read: `topics/tooling.md`, `topics/lifecycle.md`, `units/D-HP.md`, `D-SAM-and-D-HP.md`, `D-POCO.md`, `D-MOTO.md`; template without 8 (7b read as an excerpt). Scripts extracted with `extract_brief_blocks.py` into the round folder; no toolkit script added.
- Opening pre-flight (`D_HP:wifi D_POCO:wifi D_MOTO:wifi`): PREFLIGHT OK. D_HP wifi 1 bt 1 asleep; D_POCO wifi 1 bt 1 awake; D_MOTO wifi 1 bt 0 awake; BT profiles none on all.
- Build: worktree `usb-dialog__candidate__620af558__...` checked out at 3dcbf76a, tree matched, `rig_thermal.sh wait 70` read 64C, `build_hur_cool.sh` exit 0. Unit tests not run (brief).
- Prepare: D-MOTO `stay_on_while_plugged_in` was 15, Gearhead 18.1.164344-release.daily, `sys.usb.config=adb`. Clocks within 1 s. D-POCO `boot_count` 76; `usb-attach-route-boot`, `usb-blacklist` and `enable-car-launcher` absent on both hosts. `serial_outside=0` and `defaults=0` on D-POCO (no lasting grant), `sections=0` in the parser (not a problem: no defaults stored). My first `usbdef` call for the grant ran with `HU` unset (shell mistake) and was redone; `defaults.tsv` was reset.
- Prepare 8: both hosts `form=upgrade` (round 3's APK wrote the keys, then the candidate installed over it).
- **Deviation, L1b on D-HP.** The rig notes say `adb reboot` freezes D-HP hard. I ran `scripts/l1b-H.sh`, a copy of `l1b.sh` whose `A reboot` line is the REBOOT hand cue, with the wait for the device to drop raised from 30 to 240 s and the wait for its return from 240 to 400 s. The operator restarted D-HP from its power menu. D-POCO used `l1b.sh` as written. The trailing exit code 1 of the D-HP run is the last line (`[ $ST = P ] && ...`) being false, not a failure.
- Stage H: no retries, no restarts. Stage P: B2 needed the seed (`route-boot=none`, `boot_count=76`): seed1 and seed2 ran; B2's first graded plug followed. B7 ran (`WITHB7=1`) and the optional block ran (stage P was under 45 min).
- Every phone dump came back over WiFi (20 of 20). No cycle changed the app pid.
- Operator counts versus the log: H-B1 the operator said 4 per plug, the log has 4, 4, 1 (plug 3 had Android's one dialog for A only; the session still formed). P-B1 and P-B2 were answered in one sentence ("4 on each, the last only 2; ticking always on Open sometimes was not possible"): the log has B1 4, 4, 4 and B2 2, 0, 0. The remark about the tick box is unexplained; a stored default would explain a missing box. Findings, not verdicts. Every other count matched the log.
- Window greps: every count above is windowed by `timeline.sh` between the run's markers; H B3 openhome re-checked with `cnt`.

## R-H-B1 (D-HP, upgrade, key absent)

**PASS**

- `H-B1-before keys ... connect=none auto-start-on-usb=false`, `H-B1-after keys ... connect=none`.
- p1, p2, p3: `nosw=0 openhome=0 sw_lines=2 attach_B=1 ssl=1 ms=4 phone_acc=1 fatal=0`. `dlg_all` 4, 4, 1; `dec_A` ask, ask, wait.

## R-P-B1 (D-POCO, upgrade, key absent)

**PASS**

- `P-B1-before` and `P-B1-after keys ... connect=none`. p1 to p3: `nosw=0 openhome=0 sw_lines=2 attach_B=1 ssl=1 ms=2 phone_acc=1 fatal=0`. `dlg_all=4` each with `stacked=1`, `rs=no,no` (no route seen yet in this boot, as round 3 saw before the seed; not graded by B1).

## R-P-B2 (D-POCO, key and auto-start absent, defaults cleared)

**PASS**

- Keys `connect=none auto-start-on-usb=none listen=true`, and `connect=none` after.
- p1: `dlg_all=2 confirm_A=1 confirm_B=1 perm_A=0 perm_B=0 resolver=0 stacked=0 req_A=0 req_B=0`, `dec_A=wait dec_B=wait`, `rs=yes,yes`, `after=` lists `8888/11905` and `6353/11521`. p2, p3: `dlg_all=0 req=0`.
- All three: `ssl=1 ms=2 sw_lines>=1 nosw=0 openhome=0 phone_acc=1 fatal=0`.

## R-H-B3 and B4 (D-HP, key off, no defaults)

**PASS** (both)

- B3, `pre`: `attach_A=1 confirm_A=1 openhome=1 main_start=1 sw_lines=0 attach_B=0 ssl=0 phone_pre=0 perm_A=0 req_A=0 resolver=0 raise_A=0 autostart=0 nosw_conn=1 nosw_det=0 fatal=0`. Lines: `Normal USB device attached`, then `UsbLauncher: not switching VID: 22B8 PID: 2E81 on a plug-in: Connect when plugged in is off` and `UsbAttachedActivity: Connect when plugged in is off; opening the app, not switching` at plug 22:20:52.
- B4, `post`: `rx_check=1 confirm_A=0 perm_A=0 req_A=0 sw_lines=2 attach_B=1 confirm_B=1 perm_B=0 req_B=0 dec_B=wait ssl=1 ms=4`, phone `phone_acc=1`. Operator: 2 dialogs, both Android.

## R-P-B3 and B4 (D-POCO)

**PASS** (both)

- Mirror: `usb-connect-on-plug-in value="false"`. `comp=enabled`.
- B3 `pre`: `confirm_A=1 openhome=1 main_start=1 sw_lines=0 attach_B=0 ssl=0 phone_pre=0 perm_A=0 req_A=0 nosw_conn=1 nosw_det=0 fatal=0`. B4 `post`: `rx_check=1 confirm_A=0 perm_A=0 req_A=0 sw_lines=2 attach_B=1 confirm_B=1 dec_B=wait ssl=1 ms=2`. Operator: 2 dialogs, both Android.

## R-H-B5 (D-HP, key off, both defaults stored)

**PASS**

- Plug 22:23:42, press 22:24:14 (32 s). `pre`: `dlg_all=0 openhome=1 main_start=1 sw_lines=0 attach_B=0 ssl=0 phone_pre=0 nosw_conn=1`. `post`: `rx_check=1 dlg_all=0 req_A=0 req_B=0 sw_lines=2 attach_B=1 ssl=1 ms=4`. `phone_acc=1`, `fatal=0`. Operator: 0 dialogs.

## R-P-B5 (D-POCO)

**PASS**

- Plug 22:43:46, press 22:44:18 (32 s). `pre`: `dlg_all=0 openhome=1 main_start=1 sw_lines=0 attach_B=0 ssl=0 phone_pre=0 nosw_conn=1`. `post`: `rx_check=1 dlg_all=0 req=0 sw_lines=2 attach_B=1 ssl=1 ms=2`. `fatal=0`. Operator: 0 dialogs.

## R-P-B7 (D-POCO, replug inside the glitch window)

**PASS**

- `b7_unplug=22:44:39.089`, `b7_plug=22:44:48.114` (9 s). `P-B5-p1/b7`: `openhome=0 nosw=0 sw_lines=1 attach_B=1 ssl=1 ms=2 dlg_all=0 fatal=0`.

## R-P-B6-off (D-POCO, key off, Settings open)

**PASS**

- `before-plug`: `topResumedActivity ... SettingsActivity`. Whole cycle: `sw_lines=0 attach_B=0 ssl=0 dlg_all=0 phone_acc=0 fatal=0`. `handoff=1 nosw=2 held=0 openhome=0`; `replay=0 replay_trig=-`. Operator: 0 dialogs.

## R-P-B6-on (D-POCO, key on, Settings open; the control)

**PASS**

- `pre`: `held=2 handoff=1 sw_lines=0`. Close, then `22:49:05.205 AapService: the settings screen closed with a USB auto-connect held behind it, checking USB now (trigger=PLUG_IN).`; `post`: `replay=1 replay_trig=PLUG_IN sw_lines=2 attach_B=1 ssl=1 ms=2`. `dlg_all=0 phone_acc=1 fatal=0`. Operator: 0 dialogs.

## R-P-control dump

**PASS**

- `P-ctl-gen`: `Detect plugged-in phones/AA dongles=1:true:true`. `P-ctl-as`: `Connect when plugged in=1:true:true`, `Open the app when plugged in=1:true:true`, `Reopen on reconnection=1:true:true`, `Start on screen on=1:false:true`, `need_any=0`, `Listen for USB Devices=0`, `Auto-start on USB=0`. `detect_after_libusb=-`: the "Use Native USB Driver" row was not in the General dump, so the order is not graded. `need_in_row1=no`.

## R-P-B9 dump

**PASS**

- `P-b9-gen`: `Detect plugged-in phones/AA dongles=1:false:true`, `Listen for USB Devices=0`. `P-b9-as`: `Connect when plugged in=1:true:false`, `need_in_row1=yes`, `Open the app when plugged in=0`, `Reopen on reconnection=0`, `Auto-start on USB=0`, `Start on screen on=1`. The control dump reached rows 2 and 3, so the "hidden" items are grounded. No `NOT_REACHED` marks.

## R-H-B9 (D-HP, detection off)

**PASS**

- `nosw_det=1 nosw_conn=0 rx_check=2 dec_A=ask,ask req_A=1 perm_A=1 refuse=1 req_B=1 perm_B=1 dec_B=ask src_B=automatic lsn=off,off,off confirm_A=0 confirm_B=0 resolver=0 unfinished=0 ssl=1 ms=4 phone_acc=1 fatal=0`. Operator: 2 dialogs, both Allow.

## R-P-B9 (D-POCO, detection off)

**PASS**

- Same readings, `ms=2`. Decisive: `UsbLauncher: not switching ... on a plug-in: Detect plugged-in phones/AA dongles is off` at 22:54:36; the two `ACTION_CHECK_USB` broadcasts at 22:54:43; `UsbLauncher: a dialog for /dev/bus/usb/001/002 is already open; not opening a second`. Operator: 2 dialogs, both Allow.

## R-P-B10 (D-POCO, wake check, key on)

**PASS**

- `wd_off=1 wd_on=1 wd_on_s=75 wd_hib=1 wd_usb=1 wd_skip=0 fatal=0`.

## R-P-B10n (D-POCO, wake check, key off)

**PASS**

- `wd_on_s=76 wd_hib=1 wd_usb=0 wd_skip=0 fatal=0`. B10 is the control (`wd_usb=1`).

## R-P-B8 (D-POCO, key off, "Auto-connect USB device" on)

**INCONCLUSIVE**

- `homeauto=0`: `HomeFragment: Requesting single-USB auto-connect via AapService` never printed. `openhome=1`, the gate refused the plug-in (`23:00:04.542 UsbLauncher: not switching ... Connect when plugged in is off`), no switch (`sw_lines=0 ssl=0`, `NO_SESSION`), `dlg_all=0`. The only `auto-connect` lines in the slice: `Normal USB device attached ... Will check auto-connect in 2000ms...`, then `HomeFragment: onResume. isConnected=false` at 23:00:02.602. Operator: 0 dialogs. How the two settings meet is not shown; the home auto-connect did not fire on this plug.

## R-H-L1b (D-HP, boot start with detection off)

**PASS**

- `stopped=false` before; keys `connect=true auto-start-on-usb=true listen=false`; `boot_count=null` (API 17). `received=1 usb_branch=1 boot_branch=0 screen_branch=0 creating_after_usb=1 dlg=0 req=0 main_start=0 anr=0 fatal=0`, `t_received=22:30:46.030`, `fgs_exc=0`, `locked=0`, `refused=0`. Operator reboot (see Setup notes).

## R-P-L1b (D-POCO)

**PASS**

- `stopped=false` before; `boot_count` 76 to 77. Mirror: `auto-start-on-usb` true, `usb-connect-on-plug-in` true, `auto-start-on-boot` false, `auto-start-on-wifi` false, no screen-on key. `received=2 usb_branch=2 boot_branch=0 screen_branch=0 creating=1 creating_after_usb=1 dlg=0 req=0 main_start=0 anr=0 fatal=0`, `fgs_exc=0`, `refused=0`, `locked=0`, `t_received=23:05:40.085`, `t_usb=23:05:40.090`.

## Round verdict: PASS on B3 and B5 (the point of the round) on both hosts; B8 INCONCLUSIVE; no FAIL

## Anything the brief did not ask about

- The mirror lines on D-POCO followed `settings.xml` at every `setkeys`; the `l1b` mirror had screen-on absent, so `mirrorfix.sh` did not run.
- `received=2` on D-POCO's L1b (two boot broadcasts reached the receiver) with one service creation: the second delivery did not create a second service.
- The B1 plugs on D-POCO ran with no route seen (`rs=no`), so Android's dialog and ours stacked (`stacked=1`); this is the known first-boot state that B2 seeds away, and not a regression of the candidate.
- The two operator-count mismatches (H-B1 plug 3, P-B2 plugs 2 and 3) and the unexplained missing "always" box.
- `serial_outside=0` on D-POCO before the round and after every cycle; D-MOTO `mtp=no`.
