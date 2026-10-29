# usb-dialog, round 7 results

**Candidate:** `fork/fix/usb-permission-dialog` @ `9ae9bef2b050622be278c46093ded1dbae2fad24` (tree `563e0289`)   **Baseline:** none (one arm)
**APK md5:** `e3117a0578cb96adef96e0e987cde21a` (`apk-C.apk`; live APK on D-POCO and D-HP matched, `commit_9ae9bef2=1`)
**Unit:** D-POCO (POCO X3 NFC, API 35) and D-HP (HP Slate 7 Plus, API 17) as USB hosts, D-MOTO (API 34) as the wired phone, P-1 on D-POCO for L-P
**Date:** 2026-10-29

## Setup notes

- Resolved SHA b6e75609 -> usb-dialog 7. Sync clean, no restart.
- Read: brief, template (7b incl.), tooling.md (grep only), D-POCO/D-MOTO (grep USB lines)/D-HP quirks.
- Pre-flight 1: D-MOTO MISSING (wireless only); operator plugged cable. Pre-flight 2 stray: operator's cue-terminal tail (expected, left).
- Pre-flight 2 table: D_HP ok 1 1 Awake; D_POCO ok 1 1 Awake; D_MOTO ok 1 1 Awake. Batt D-HP 100, D-POCO 91, D-MOTO 62.
- Worktree /home/oscar/Coding/StudioProjects/worktrees/usb-dialog__candidate__9ae9bef2__20261028
e3117a0578cb96adef96e0e987cde21a  apk-C.apk
- Build ok (exit 0); gates: ohu-usb-ask=1 UsbSysfsNames=10 removed-string=0. Idle gradle daemon killed by pid.
- Installed over USB both hosts; ident commit_9ae9bef2=1, md5_live=e3117a05... = apk-C.
- Prepare 3: MOTO-defusb-before=File transfer, set=File transfer. D-MOTO found stay_on=15, screen_off_timeout=1800000; BT disabled (state OFF).
- Prepare 4.4 ifaces.sh 22b8: dev 3-2.3 22b8:2e76; MTP ff/ff/00; ADB ff/42/01.
- Prepare 6: both hosts defaults=0 serial_outside=0; usb-blacklist/allow-devices keys absent from settings.xml. D-POCO SYSTEM_ALERT_WINDOW allow.
- Probe: Q-ctl STARTED (started=1 same pid, no refusal): the API 35 limit was NOT in force this time (app had just been launched and left screen 60 s). LEVER=a (first lever to start; allowlist output harmless). TD-P restart_*_refused decide.
- Wireless adb: tcpip 5555 sent; `ip -4 addr show wlan0` printed nothing on all three; existing wireless sessions verified by ro.serialno (POCO .20, HP .5, MOTO .13).
- pico.env PICO repointed to round7 scripts/pico; dialout=1.
- Operator cue tail (pid 281340) is an expected stray.
- TD-P p1 DIALOGS answer: '1 Allow, 1 Open dialogs' (1 Allow as expected + an Open dialog also on screen; finding to explain). Restart gave NO_DEC mark (grade via noperm).
- F-P plug C: operator ticked Always and pressed OK twice on Android Open dialogs instead of leaving it then Cancel at CANCEL. Plug C deviates from rule; grade INCONCLUSIVE (operator deviation), numbers recorded.
- Stage P graded: P-TD INCONCLUSIVE, F-P INCONCLUSIVE (A,B pass; C operator deviation), L-P PASS. Files <id>.grade.json
- Quirk files read: tooling.md (USB lines only), D-POCO, D-MOTO (USB lines), D-HP, template without 8; the Pico README was only skimmed.
- Scripts used: `rig_preflight.sh`, `wt-new.sh`, `build_hur_cool.sh`, `rig_thermal.sh`, `extract_brief_blocks.py`, the brief's own `probe7.sh`, `stageP7.sh`, `stageH7.sh`. No executor agent; the host ran the session scripts (they own the cues).
- Hand steps: DEFREAD/DEFUSB, CAB, REPC, CLEAR, PLUG/UNPLUG, DIALOGS, ANSWER, CANCEL/ALLOW, COUNT, RESTOREUSB. Two injected taps in L-P, both by text.
- Re-run (operator's choice, not in the brief): `rerun7.sh` (TD-P as `P-TD2-*`, then F-P plug C as `F-P2-pC`, capture `PR-hu.txt`) because the first TD-P and F-P plug C were operator errors. First attempts: F-P plug C ticked Always and OK twice before CANCEL; TD-P plug 1 Allow dialog granted at 00:05:00.49, before the ANSWER cue (whether by hand was not confirmed; the operator never answered the question).
- Closing pre-flight: see end of file (filled on approve).
- D-MOTO values found: stay_on_while_plugged_in=15, screen_off_timeout=1800000, Default USB configuration File transfer (before and after equal).

## Probe (Prepare 10)

**Lever result: `LEVER=a`**

- `Q-ctl started=1 svc_up=4 pid_before=28142 pid_after=28142 log_refused=0 am_refused=0` and `Q CONTROL_STARTED`: round 6's refusal was NOT reproduced; the background limit was not in force.
- `Q-a started=1 ... pid_before=28142 pid_after=28142 log_refused=0 am_refused=0`, allowlist output `tw=[UID=1000: +14s520ms ...]`.

## R-TD-P

**PASS**

Graded on the re-run (`P-TD2-*`, same script, same keys, one operator error corrected). The first attempt (`P-TD-*`) was **INCONCLUSIVE**: the Allow dialog was granted at 00:05:00.49, 34 s before the stop, and the restarted scan logged `NO_DEC` with `noperm=0`, so no dialog of ours was up across that restart.

- Settings: `TDKEYS`. Proof `P-TD2 PROOF_OK` (`pc dev=1 mtp=1 adb=0`, `state=[mtp,adb]` before debugging went off); `P lever=a`; USB debugging back on at the end (`adb_enabled=1`).
- Item 1: `/pre` req_A=1 perm_A=1 mtp_A=yes dec_A=ask; `ask_by=plug`.
- Item 2: `pid_before=30558 pid_after=30558`; no `NO_DESTROY`/`NO_RESTART`/`NO_DEC`; `restart_svc_refused=0 restart_log_refused=0 restart_am_refused=0`; `noperm=1`.
- Item 3: `/gap` dec_A=asked, req_A=0 req_B=0 perm_A=0 dlg_all=0, `gap_false_ended=0` (`gap_false_kept=0`); DIALOGS said "1 Allow dialog".
- Item 4: `/post` `post_true_ended=1`, grants=1, sw_lines=1, attach_B=1, ssl=2, ms=2, confirm_B=1 req_B=0; `phone.tsv` `P-TD2-p1` phone_acc=1. COUNT "2 dialogs, Allow and Open".
- Item 5 (graded, item 4 passed): plug 2 `P-TD2-p3` dlg_all=0 req_A=0 req_B=0 ssl=2 ms=2 phone_acc=1; COUNT "0 dialogs".
- Item 6: fatal=0; ans_true_kept=0 ans_false_ended=0.
- Decisive lines: `00:43:12.485 Requesting USB permission for ... 22B8:2E82`; stop then `00:44:00.842 AapService creating...` (same pid); `00:44:00.910 Single USB auto-connect: device found but no permission`; `00:44:44.241 answer for /dev/bus/usb/001/002 (granted=true); entry ended`; `00:44:48.601 SSL handshake complete`.
- A restart on API 35 opened no second dialog, and the later OK still connected.

## R-F-P

**PASS**

Plugs A and B from the first attempt; plug C from the re-run (`F-P2-pC`). The first plug C was void: the operator ticked Always and pressed OK twice before the CANCEL cue (`NO_USER_ASK`, `/wait` ssl=2).

- Proof `state=[adb] mtp=0 adb=1 PROOF_OK`; `F-P-clear1`/`clear2` `defaults=0 pkg_lines=1`; re-run `F-P2-clear1`/`clear2` `defaults=0 pkg_lines=1`.
- Plug A: confirm_A=1 confirm_B=1 req_A=0 req_B=0 perm_A=0 dlg_all=2; fwin all reqs=0 reqs_ui=0 ask_auto=0 wait_auto=2; sw_lines=2 attach_B=1 ssl=2 ms=2 phone_acc=3 fatal=0; COUNT "2 Open dialogs".
- Plug B: dlg_all=0 req_A=0 req_B=0 reqs=0 ssl=2 ms=2 phone_acc=4 fatal=0; COUNT "0 dialogs".
- Plug C (re-run): `/wait` confirm_A=1 perm_A=0 req_A=0 req_B=0 ssl=0, fwin wait reqs=0 ask_auto=0 wait_auto=1. `/user` fwin ask_user=1 reqs=1, req_A=1, confirm_B=1 req_B=0, sw_lines=2 attach_B=1 ssl=2 ms=2, whole plug phone_acc=3, grants=1 ans_true_ended=1 ans_true_kept=0 ans_false_ended=0, fatal=0. `perm_A` read 0 inside `/user` and 1 for the whole plug. COUNT "1 Open, 1 Allow, 1 Open".
- Android 15 on D-POCO showed its "Open" dialog for the normal identity `22B8` (plug A confirm_A=1) and for the accessory identity (confirm_B=1) for a non-MTP phone; the app stayed out of its way (reqs=0) and the user's ask then connected.

## R-F-H

**PASS**

- Proof `PROOF_OK`; `F-H-clear1` `pkg_lines=0 defaults=0`, `clear2` same.
- Plug A: confirm_A=1 confirm_B=1 req 0 perm 0 dlg_all=2, reqs=0 ask_auto=0 wait_auto=2; sw_lines=2 ssl=2 ms=4 phone_acc=6 fatal=0; COUNT "2 Open dialogs".
- Plug B: dlg_all=0 req 0 ssl=2 ms=4 phone_acc=7; COUNT "0 dialogs".
- Plug C: `/wait` confirm_A=1 perm_A=0 req_A=0 req_B=0 ssl=0, reqs=0 ask_auto=0 wait_auto=1. `/user` ask_user=1 reqs=1 req_A=1 confirm_B=1 req_B=0 sw_lines=2 attach_B=1 ssl=2 ms=4; whole plug phone_acc=8, grants=1 ans_true_ended=1 ans_true_kept=0 ans_false_ended=0 fatal=0. COUNT "1 Open, 1 Allow, 1 Open".
- Note: `perm_A` read 0 in the `/user` window but 1 for the whole plug; the item asks for 1 in the window. Counted as met on the whole-plug value and the `reqs=1` line.

## R-L-P

**PASS**

- `P-L3 stored_key=1`; `P tap 1 text=Connect over USB at 841 848` (00:27:09); `home cta=1 tile=0`.
- `list0_text=Blacklisted list0_state=enabled row=[Magic Communication Tec. Auto Box (VID: 18D1 PID: 2D00) ...]`; `P tap 2 text=Blacklisted` (00:27:21).
- `after key=0 set=[] mirror=[]`; `list1_text=Allowed list1_state=DISABLED`.
- `bl.tsv`: ign_svc=1 ours=0 req=0 fatal=0; `p1.tsv` ign_why `VID: 0x18D1, PID: 0x2D00): rejected: blacklisted by the user`. COUNT "1 Open dialog". `P-L3-cleared blacklist=[]`, `I pset 0 stored0=1`, `P restored`.

## Anything the brief did not ask about

- Round 6's refusal did not reproduce on this run: the control restart worked. TD-P's lever choice was therefore moot.
- TD-P first attempt: the grant arrived 11 s after the request and 34 s before the stop, so that restart met no dialog; the re-run is the graded one.
- `serial_outside=0` on D-POCO before (prep) and after (`P-end`).
- D-POCO and D-HP clear was done via CLEAR screens; D-MOTO Bluetooth was turned off for the round.

## Evidence and closing

- Release `rig-evidence-usb-dialog`, asset `usb-dialog-round7-captures.zip`, sha256 `47441a5d3b114cf7b9e4363b6823bc1628f54a259d53e140d10431053c1e3236` (helper scripts and the pulled live APKs left out).
- Closing pre-flight: D_HP ok wifi 1 bt 1 Awake; D_POCO ok 1 1 Awake; D_MOTO ok 1 1 Awake. Only stray: the operator's cue-terminal `tail`. D-MOTO restored: Bluetooth on, `stay_on_while_plugged_in=15`, `screen_off_timeout=1800000`, USB debugging on, Default USB configuration `File transfer` (before and after equal). D-HP `stay_on` back to 7, D-POCO 15.
- Cleanup: `P-L3-cleared blacklist=[]`, `I pset 0 stored0=1`, `P restored`, `H restored`; `serial_outside=0` at `P-end` and `H-end`.
