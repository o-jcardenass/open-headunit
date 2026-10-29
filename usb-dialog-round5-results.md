# usb-dialog, round 5 results

**Candidate:** fork/fix/usb-permission-dialog @ 4a603930ecbfdeb224907a1d57d8434b1259163c (tree ae3050ba), on main 47162d01529aa315773fef59123993630cb03606. **Baseline:** none (earlier rounds cited for "before").
**APK md5:** a290cbb5edf41d3870817dadd4675cb6 (candidate; live on D-HP and D-POCO, equal to the built APK).
**Unit:** D-HP (HP Slate 7 Plus, API 17) and D-POCO (POCO X3, API 35) as USB hosts over wireless adb; D-MOTO (API 34) the phone; D-DONGLE the wireless dongle; P-1 (RP2040 USB stand-in) as a USB device.
**Date:** 2026-10-28
**Evidence:** release `rig-evidence-usb-dialog`, asset `usb-dialog-round5-captures.zip`, sha256 ff1464749c4c439cc6fa577488924aa0efb19f12970bc1476b56df88b65aa439

## Setup notes

- Quirk files read: tooling, lifecycle, D-HP, D-POCO, D-MOTO, D-DONGLE; template 0-5 + 7b; pico README. Scripts extracted with extract_brief_blocks.py, round 4 helpers copied unchanged; bash -n and py_compile clean.
- Opening pre-flight (D_HP:wifi D_POCO:wifi D_MOTO:wifi): PREFLIGHT OK. D_HP wifi 1 bt 1 awake; D_POCO wifi 1 bt 1 awake; D_MOTO wifi 1 bt 1 awake; BT profiles none on all. Preflight roles use underscores (D_HP), not the brief's D-HP form for the CLI.
- Build: get_apk.sh run from StudioProjects (it has a relative `cp rig-data/apks/*.apk`). Thermal wait 70 read 65C.
- Prepare 3 (D-MOTO): bt disabled (enabled: false); stay_on_while_plugged_in was 15, screen_off_timeout was already 1800000; Gearhead 18.1.164344-release.daily; sys.usb.config=adb; setScreenUnlockedFunctions in `svc usb` = 1; `dumpsys usb` has no screen-unlocked-functions field (default mode read empty, restore `none`); logcat -G 16M ok; sdk 34; clock = host.
- Prepare 4: wireless adb HP 192.168.1.5, POCO 192.168.1.20, MOTO 192.168.1.13 (:5555) in scripts/wireless.env.
- Prepare 5/6 via scripts/prep56.sh (one-round): D-POCO sdk 35 boot_count 78 bat 98, defaults=0 serial_outside=0; D-HP sdk 17 bat 100 (clock zone COT, same instant), defaults=0 sections=1 serial_outside=0. usb-blacklist, enable-car-launcher, allow-devices, usb-attach-route-boot all absent on both. Running a script in a fresh shell needs `source rig_devices.sh` first (RIG_TOOLKIT unexported: lib.sh line 1 error), it worked anyway.
- Candidate tree check printed ae3050ba (matches).
- Prepare 7: backups backup-P.xml (2729 bytes) and backup-H.xml (5890 bytes); apk-C md5 a290cbb5edf41d3870817dadd4675cb6, both strings counted 1 in the dex; install ok on both; ident P-new and H-new read commit_4a603930=1 and md5_live equal to apk-C.
- Prepare 8: `H xprefs int:auto-connect-delay-seconds=0 onboarding=1`.
- Prepare 10: pico-usb-stand-in-round1-results.md R0-R4 all PASS (R1, R3 PASS, R2 PASS), dialout=1, PICO_OK=1. PICO points at scripts/pico (a copy of console.py, since tools/ vanishes when the worktree returns to main). Port detection is ttyof (2e8a:000a), so ttyACM1 trap is covered.
- Adapters (operator): P-1 reaches D-HP through the OTG adapter with a USB-A to USB-C cable, the same way phones do (not the micro-USB OTG to USB-C adapter). D-POCO takes D-MOTO on a USB-C to USB-C cable, no OTG adapter, so stage P's CAB cue "put the OTG adapter in D-POCO's port" is read as "have the USB-C cable ready on D-POCO".
- Stage P arm N: `svc usb setScreenUnlockedFunctions mtp` was accepted and read back `screen_unlocked_functions=MTP`; for `none` the read-back field is empty (rc=2 in usbmode), so the script cued DEFUSB and the operator set "No data transfer" by hand; the first DEFUSB read cannot say whether the shell route had worked. The arm's identity is graded from x1.tsv.
- D-MOTO wireless adb is lost whenever USB debugging is toggled; each arm's dbgon fell back to PCMOTO (cable, 11:10:33 to 11:11:33) and the script restored tcpip itself. Expect one PCMOTO per arm.
- P-F-p1 operator remark: the "Open" dialog had a box but projection started before it could be ticked.
- Stage P graded (11:00 to 11:18, script exit=0, no retries, cycles all pid 10999). Grades in S1-P-N/S1-P-F/ORD .grade.json. S1-P-F brief gap: PASS needs mtpn>=1, INCONCLUSIVE names "mtpn=0 with mtp_A not true"; here mtpn=0 with mtp_A=yes (the brief wrote `true`, the field prints `yes`), so neither literally applies; graded INCONCLUSIVE.
- Stage P D-POCO lasting grants: defaults=2 pairs 6353/11520 8888/11906 (cleared by hand at each CLEAR); after the round P-end defaults=0 serial_outside=0 (no D-MOTO serial grant outside the defaults block). P restored line present.
- DEBUG-ON path: arm F needed a PCMOTO cable pull (wireless adb lost on debug toggle); arm N reached D-MOTO over wifi (`P-N-arm phone ok wifi`).
- Stage H graded (11:22 to 11:39, script exit=0, no retries). Grades in S1-H-N/S1-H-F/S2-M-bl/S2-P1-M3/S2-M-re/B8 .grade.json. Operator COUNT vs log: H-N-p1 and H-F-p1 operator said 3 (2 Allow, Open), log dlg_all=4 (confirm_A 1, confirm_B 1, perm_A 1, perm_B 1): a finding, not a verdict.
- **Brief bug, stage H, D-HP (API 17): every injected tap is TAP_VOID by construction.** API 17's `uiautomator dump` has no `resource-id` attribute (added in API 18); the home dump (H-tap-113309.xml) shows text nodes "Connect over USB", "Cable", "Over USB cable", "Plug in your phone", "Settings", "Exit" and no id. `tapnode ':id/usb_button'` and `android:id/button1` match 0. So S2-M-bl items 4 (tap part) and 5, and S2-P1-M3's list_text/list_state and `diag0_by` USB button, are INCONCLUSIVE as the brief's own TAP_VOID rule allows. Not patched mid-round (scope). Stage D (S2-D-bl) and stage I (I4bl, I3bl) will hit the same on D-HP.
- D-MOTO wireless adb loss on debug toggle did not recur in stage H (both phone dumps over wifi).
- Stage D graded (11:43 to 11:59, WITHBOOT=1, script exit=0, no retries). Grades S2-D-ctl, S2-D-bl, BOOT, S2-D-re.
- **Dongle identity differs from the brief's expectation:** S2-D-ctl diag reads `accepted: PTP | if0 06/01/01` (vp 18D1:4EE1, a camera-class interface), not `accepted: AOAP (vendor interface named nothing)`. So the dongle is not a vendor-class stand-in for the report's box; the blacklist key still works (vidpid:18d1:4ee1). The Gallery/Open Headunit window the operator described is `UsbResolverActivity` (resolver=1); log dlg_all 4 = operator's 3 plus that chooser.
- **S2-D-bl phone item:** `gh_connsession` is 11 in D-S2D and 11 in D-ctl-p1: it counts phone/dongle WiFi activity that does not depend on the head unit, so it cannot show "no session". Recorded, not graded against.
- **S2-D-re void:** the cue printed "plug D-MOTO's cable" (round 4 cycle.sh text reused by stageD.sh), the operator plugged D-MOTO, and the row shows vp 22B8:2E76. A brief bug (cue text), and my earlier chat message said "dongle". Not re-run (label cannot repeat; scope).
- D-end-clear-pre read defaults=4 (6353/11521, 8888/11894, 6353/11520, 6353/20193); cleared by the operator, D-end defaults=0. `D restored` read.
- Every D-BOOT count: received 1 at 11:50:14.924, operator saw 0 dialogs during the boot; D-HP reboot by hand (down after 34 s).
stageI start: P-1 was already on the test PC console; pset 1 read bcd=1.01 at 12:03:37
- **Stage I deviation (second pass).** First pass: I1 and I2 graded. `I pset 4 NO_CONSOLE (was 1314:1520)` at the 60 s HOLD wait: the operator's first hold was a short press that stepped P-1 to identity 3, the redo came after the wait, so the brief's rule made I4, I4bl, I4rp UNTESTABLE (pset) and the script went on to pset 5 (bcd 1.05). I stopped `stageI.sh` by pid at the UNPICO 5 wait (no run in flight), kept the logcat capture, and ran `scripts/stageI2.sh` (stageI.sh minus the setup, I1 and I2) from identity 4: I4, I4bl, I4rp, I5, I3, I3bl, clean-up. Original `stageI.sh.orig` kept. The labels I-c4, I-S2P4, I-rp4 were never used before. The HOLD cue text did not say "count to 4 and keep it down"; the second pass shows P-1 already on its console when it started (pset 4 passed at once, bcd=1.04).
- Stage I graded. Clean-up: `I-before blacklist=[absent]`, `I-mid blacklist=[]`, `I-after blacklist=[]`, `I-restored blacklist=[absent]` (equals I-before); `I pset 0 stored0=1` at 12:35:17 (P-1 back on its console); `I restored` read.
- **I4rp NO_PLUG x2** (12:19:30 and 12:22:48 cues, windows I-rp4-p1 and I-rp4-p2): 0 lines mentioning USB in either window (grep -aic usb = 0), versus the I-S2P4 plug 3 minutes earlier which logged the attach at 12:16:58.464. P-1 did not register on D-HP. D-HP's connector is a known flaky one; the OTG adapter was reseated at the RESEAT cue (12:26). The script's plug detector (`USB Intent: ... USB_DEVICE_ATTACHED`) would also miss a blacklisted device that Android never offers, so a brief weakness exists but the window had no USB line of any kind, so this is the plug and not the detector.
- Hold trap, stage I: the HOLD cue says "hold KEY for 4 s" and the first attempt for identity 4 was a short press that stepped P-1 to identity 3 (lsusb 18d1:2d00 at 12:10:52); the 60 s wait then expired. Operator note, not a brief error, but the cue should say "count to four".
- Operator note on the method: asked whether KEY could be pressed with P-1 already on D-HP. Answered no (steps forward only, would pass through identity 3 on D-HP's bus, no bcdDevice check there). Not done.
- Operator counts stage I: I1 0, I2 0, I4 3 (Allow, Open, Allow) + failure notification, I4bl 0, I4rp not counted (no plug registered), I5 1 (Open), I3 1 (Open), I3bl pending.

- Closing: D-MOTO restored (`setScreenUnlockedFunctions none` sent, stay_on_while_plugged_in 15, Bluetooth on, screen_off_timeout 1800000). D-HP and D-POCO `settings.xml` read back equal to their backups (`P restored`, `D restored`, `I restored`, `I3 restored`), blacklist `[absent]`, D-HP stay_on 7. Closing pre-flight: the CLI cannot see D_HP and D_MOTO (wireless adb only, not in `adb devices` by serial), so it printed FAIL for those two and for an Android Studio gradle daemon it does not own (left running). Read directly over wireless adb: D-HP wifi 1 bt 1 awake; D-MOTO wifi 1 bt 1 awake; D-POCO wifi 1 bt 1 awake (CLI). Same as the opening table. No logcat left running.
- `stay_on_while_plugged_in` differs per phone (D-HP 7, D-MOTO 15); restored to each one's own value.

## S1-P-N: Stage 1, D-POCO, arm N: first offer of a phone in file transfer (point of the round)

**FAIL**

item 4: plug 1 diag reads accepted: MTP (same vp 22B8:2E82 and diag as the F arm); items 1,2,3,5 hold: dlg_all 2,0,0, req 0 on p2/p3, ssl 2 ms 2 phone_acc 1, COUNT 2,0,0, mtpn 0 all plugs

## S1-P-F: Stage 1, D-POCO, arm F: file transfer control

**INCONCLUSIVE**

MTP verdict reached (diag accepted: MTP, mtp_A yes, route_A 0) but mtpn=0 on every plug so the notification half is not shown; no FAIL condition (dec_A ask, ssl 2); brief gap between PASS and INCONCLUSIVE clauses

## ORD: Settings order: Detect row after Use Native USB Driver

**PASS**

Detect=1:true:true directly after Use Native USB Driver=1 in P-ord-gen, detect_after_libusb=yes, screenshot shows no row between them

## S1-H-N: Stage 1, D-HP, arm N

**PASS**

items 1,2,3,5: dlg_all 4,0,0 (operator 3,0,0), req 0 on p2/p3, sw_lines>=1 attach_B 1 ssl 2 ms 4 fatal 0 phone_acc 1 each plug

## S1-H-F: Stage 1, D-HP, arm F

**PASS**

same items as S1-H-N, identity identical to N arm (vp 22B8:2E82, diag accepted: AOAP vendor interface named nothing); control for S2-M

## S2-M-bl: Blacklist on API 17, D-MOTO key

**PASS**

key vidpid:22b8:2e82 stored; plug ign_svc 1 attach_A/B 0; open diag_bl 1 diag0 1; auto homeauto 1 home0 1; btn1 0 rx_check 1; all ours/req/sw/ssl/ms/acc_any/fatal 0, phone_acc 0, COUNT 0. Items 4 tap and 5 INCONCLUSIVE: TAP_VOID, no resource-id in API 17 uiautomator dump

## S2-P1-M3: Cost of the accessory key (18D1:2D00) on D-MOTO

**PASS**

cost confirmed: sw_lines 1 accstart 1 phone_acc 1; ign_why VID 0x18D1 PID 0x2D00 rejected blacklisted by the user; acc 0 found_acc 0 attach_B 0 ssl 0 ms 0 fatal 0 btn1 0; list_text/list_state and diag0_by USB button INCONCLUSIVE (TAP_VOID)

## S2-M-re: D-MOTO reconnects after the key is removed

**PASS**

ign_svc 0 ign_act 0 attach_A 1 sw_lines 1 ssl 2 ms 4 fatal 0 phone_acc 1; D-MOTO connects after the 18d1:2d00 key is removed

## B8: Round 4 leftover B8

**PASS**

pre openhome 1 sw_lines 0; post homeauto 1 sw_lines 1 ssl 2 ms 4; phone_acc 1; fatal 0

## S2-D-ctl: Dongle control

**PASS**

dongle 18D1:4EE1 attach_A 2 sw_lines 3 ssl 2 ms 4 fatal 0 gh_connsession 11; diag accepted: PTP if0 06/01/01 (not the expected AOAP vendor form); dlg_all 4 = operator 3 + one UsbResolver chooser (Gallery or Open Headunit)

## S2-D-bl: Dongle blacklisted

**PASS**

head-unit side: plug ign_svc 2 attach 0; open diag_bl 3; auto homeauto 1 home0 1; btn1 0 rx_check 1; all ours/req/sw/ssl/ms/acc_any/fatal 0, COUNT 0. Not strict: gh_connsession=11 in D-S2D (brief wants 0) but control also 11 so the phone item does not discriminate; list items INCONCLUSIVE (TAP_VOID)

## BOOT: Boot with a blacklisted device present

**PASS**

received 1 usb_branch 1 creating_after_usb 1 boot_branch 0 screen_branch 0; ign_svc 9 ign_act 10 diag_bl 3 ours 0 req 0 sw 0 ssl 0 acc_any 0 fatal 0; stopped=false before; COUNT 0

## S2-D-re: Dongle after its key is removed

**INCONCLUSIVE**

wrong device plugged: vp is 22B8:2E76 (D-MOTO), not the dongle; cue text said D-MOTO; nothing measured about the dongle once its key is removed

## S2-P1-I1: P-1 identity 1, stock CCPA

**PASS**

svc_attach 2, ign_why VID 0x1314 PID 0x1520 rejected: no Android interface, diag rejected: no Android interface, refused_all 2; android 0 act_attach 0 nonand 0 ours 0 req 0 norm 0 sw_lines 0 fatal 0; COUNT 0

## S2-P1-I2: P-1 identity 2, NCM mode

**PASS**

same counts as I1 for identity 2 (bcd 1.02); diag if0 02/0D/00, if1 0A/00/01, if2 0A/00/01 rejected: no Android interface; COUNT 0

## S2-P1-I4: P-1 identity 4, nameless vendor device

**PASS**

norm 1 sw_lines 2 swfail 2 accstart 0 acc 0 ssl 0 fatal 0; vp VID 1314 PID 1521; diag accepted: AOAP vendor interface named nothing; android 1 act_attach 1 ours 1 req 1; COUNT 3 (Allow, Open, Allow) plus a failure notification

Decisive lines: 12:15:13.334 `accepted: AOAP (vendor interface named nothing)`; 12:15:17.964 `UsbAccessoryMode.switch | AOA switch failed: the device did not complete the Android Auto handshake`.

## S2-P1-I4bl: P-1 identity 4 blacklisted

**PASS**

key vidpid:1314:1521 stored; plug ign_svc 1 attach 0; open diag_bl 1 diag0 1; auto homeauto 1 home0 1; btn1 0 rx_check 1; all ours/req/sw/ssl/ms/acc_any/fatal 0; COUNT 0. Items 4 tap and 5 INCONCLUSIVE (TAP_VOID)

## S2-P1-I4rp: P-1 identity 4 replugged with the stored key

**PASS**

Re-run rq4 (stored vidpid:1314:1521): ign_why VID 0x1314 PID 0x1521 rejected: blacklisted by the user (13:37:49.334), ours=0, accstart=0, swfail=0, norm=0, no session; android=1; operator saw 1 Open dialog. First two attempts (rp4) were NO_PLUG.

Attempts rp4 (12:19:30, 12:22:48) were NO_PLUG with 0 USB lines. Re-run `rq4` by `scripts/stageI3.sh` after the adapter was reseated. Stored key `vidpid:1314:1521` was written into `settings.xml` directly (`I-rq4 stored_key=1`), not through the app's list. Decisive line, 13:37:49.334: `UsbLauncherListener.onUsbAttach | Ignoring USB device attached in service (VID: 0x1314, PID: 0x1521): rejected: blacklisted by the user`. Operator count: 1 (Open).

## S2-P1-I5: P-1 identity 5, AOA switcher

**PASS**

norm 1 accstart 1 acc 1 accid VID 18D1 PID 2D00 found_acc 3 ssl 0 fatal 0 vto 0; android 1 act_attach 2 ours 0 req 0; COUNT 1 (Open)

Decisive lines: 12:28:30.234 `Switching USB device to accessory mode VID: 1314 PID: 1521`; 12:28:30.774 `Result: true`; 12:28:30.814 the device returns as `VID: 18D1 PID: 2D00`.

## S2-P1-I3: P-1 identity 3, AOA accessory

**PASS**

acc 1 norm 0 ssl 0 fatal 0; diag accepted: already in accessory mode; found_acc 2 vto 0 android 1 act_attach 1 ours 0 req 0; COUNT 1 (Open)

Decisive line, 12:30:37.944: `Found device already in accessory mode: VID: 18D1 PID: 2D00`.

## S2-P1-I3bl: P-1 identity 3 blacklisted

**PASS**

ign_svc 1 ign_why VID 0x18D1 PID 0x2D00 rejected: blacklisted by the user; acc 0 found_acc 0 vto 0 attach_B 0 ssl 0 ours 0 req 0 sw_lines 0 fatal 0 btn1 0; android 1 ign_act 1; list item INCONCLUSIVE (TAP_VOID); COUNT not yet given

Decisive line, 12:32:43.534: `Ignoring USB device attached in service (VID: 0x18D1, PID: 0x2D00): rejected: blacklisted by the user`. The operator's dialog count for this plug was not given, so the count item is not graded.

## Anything the brief did not ask about

- Operator counts versus log: stage H plug 1 operator 3, log 4 (confirm and permission, both arms); stage D control operator 3 plus a Gallery or Open Headunit chooser, which is the log's fourth.
- Brief bugs: no `resource-id` on API 17, so every `tapnode` is TAP_VOID and the tap and list items are INCONCLUSIVE; the stage D plug cue named D-MOTO, which voided S2-D-re; the dongle reads `accepted: PTP` (if0 06/01/01), not the vendor-class form; the HOLD cue should say to count to four, because a short press steps P-1 to the next identity.
- The plug detector waits for `USB_DEVICE_ATTACHED`, so a blacklisted device Android never offers can read as NO_PLUG.
- I4rp was re-run once; the first two attempts had no USB line at all, which points at the adapter contact on D-HP, not the app.
