# usb-dialog, round 8 results: Samsung S24+ on D-POCO, USB modes, dialogs, Charging only, link-loss recovery

Candidate: `fork/fix/usb-permission-dialog` `15a13bff0d1b6a7fe4e32664afa6b53d9fa320ae`, tree `deff5f62c97720f738c26f674619a11f7dcc5862` (confirmed with `rev-parse`)
Baseline: none (one arm)
APK: `apk-C.apk` md5 `b6c21a48c587703c595649fbfaf03326`; live APK on D-POCO identical, `commit_15a13bff=1`
Unit: D-POCO (POCO X3 NFC, API 35) as USB host; D-S24P (SM-S926B, Android 16 sdk 36, One UI 8.5 `80500`, `BP4A.251205.006.S926BXXSHDZI1`, Android Auto 17.9.664044-release)
Date: 2026-10-29, stage 12:19 to 13:01
Evidence: release asset `rig-evidence-usb-dialog` / `usb-dialog-round8-captures.zip`, sha256 `ec2938eb210ae9cfe59eeb3e1d49fd33e5e9be16342c03b8205264abb88309e7`

## Round summary

| Run | Verdict |
|---|---|
| Q1 | INCONCLUSIVE |
| Q2 | INCONCLUSIVE |
| R1 | FAIL |
| R1-on | FAIL |
| R2 | PASS |
| R3 | INCONCLUSIVE |
| R4 | PASS |
| R5 | UNTESTABLE |
| Q3 | PASS |
| Q4 (ptp) | PASS |
| Q4 (midi) | UNTESTABLE |
| Q5 | UNTESTABLE |

## Setup notes

- Pre-flight `rig_preflight.sh D_POCO:wifi`: `D_POCO ADB ok, WIFI 1, BT 1, Awake, HFP:none A2DP:none`, PREFLIGHT OK. Quirk files read: `topics/tooling.md`, `units/D-POCO.md`. Template read without 7b and 8 (output persisted to file and skimmed, not read twice).
- Build: worktree `usb-dialog__candidate__a4f80ff1__20261029`, `15a13bff`, tree matches. Thermal pauses at 95C three times, resumed at 83 to 85C. Dex string gates: all seven strings 1 or more, `Android starts this app for a USB attach` 0.
- **Brief bug:** the brief and its helpers use `$POCO`; `rig_devices.sh` defines `$D_POCO` (`4f4027e9`). Used `HU=$D_POCO`. One attempt with an empty `HU` failed ("more than one device") and wrote nothing.
- **Deviation, operator approved:** D-POCO was at 52% battery, the brief needs 80%. `BSTART` lowered 80 to 50 in `lib8.sh`; `BSTOP` stays 30. Battery reached 28% at `Q4-midi` (`Q4-midi BATTERY_LOW 28`), so the stage ended there.
- **Brief bug:** `stage8.sh` ignores `qv`'s return code 2, so after the battery gate failed it went on and issued Q5's DEFUSB cue. The Default USB configuration was already Charge only, so no setting changed. `Q4-midi` and `Q5` are UNTESTABLE (battery).
- Prepare 9 reads (spoken): Default USB configuration exists (Spanish UI, under Redes above the "mostrar dispositivos bluetooth sin nombre" toggle), options Transferir archivos, anclaje a red con USB, MIDI, transferir imagenes, solo cargar el telefono; selected before the round: Charge only. Add new cars on. Auto Blocker off. `Q5=1` was therefore used.
- Prepare 7: D-S24P was already on adb when first checked (`192.168.1.23:35641`). `adb mdns services` listed nothing, before or during the round, so mDNS did not work. **Wireless debugging dropped after every plug and its port changed each time**; the operator read out a new `ip:port` 15 times, written to `s24.addr`. The brief's fallback worked each time. Prepare 8: `adb_enabled=0`, `adb_wifi_enabled=1`, `stay_on_while_plugged_in=0`, `sys.usb.config=sec_charging`, `screen_unlocked_functions=0x40000`.
- **The phone capture could not see the plug.** Every plug had `pm_unreach=1` (the watcher goes unreachable at the plug) and `pm_acc=0` for the whole round, although sessions formed (`ssl` 2, `ms` 2). So the phone-side items (`pm_acc` 1 or more, `phone config=` per rep, `GH.` lines) are unobtainable on this phone. `phone_lines` was above 0 for every plug, so this is not the brief's "no capture" case. Where `pm_acc` was a PASS item, it is not counted and the verdict says so.
- **The phone's mode was not what the operator selected.** `pm_first` read `config=[ptp]` at the start of every plug from `Q1-s` on, and the Q2 plugs enumerate as `04E8:6865` (single `06/01/01` interface, the same PID as the PTP mode in Q1), not `04E8:6860` (MTP plus CDC ACM, the Q1 File transfer row). The operator reported "File transfer" at every Q2 COUNT. `Q3-p1-start` was PTP too.
- **Q1 and the app's own decision:** `dec_A`, `app_mtp` and `app_filter` read `-` in every Q1 window. In Q1 and the Connect-when-plugged-in-off plugs the app prints no `UsbLauncher: permission for` line, so Q1 items 3 and 4 cannot be read.
- Windowed counts: `lcount` windows run `<K>-fault` to `<K>-end` (about 40 s for R1) and so include the whole recovery. Units refusing `logcat -c` were not involved (D-POCO and D-S24P both took a fresh capture).
- Checked one count against the capture: `carries flags 0x` 0 and `WRONG FLAG` 0 over the whole `P-hu.txt` (18 MB), `FATAL EXCEPTION`/`ANR in com.andrerinas`/`NoClassDefFoundError` 0. Matches the per-rep numbers.
- `Q1-w1` `readid` and others where `names_src=logcat` leave the interface names `null` or `?`; the triples are from the app's `UsbDiagnostics:` lines.
- Executors: none used. The host ran the stage and Close foreground and in the background with monitors. One monitor was re-armed with a narrower filter and fired one empty event from my own baseline count (no signal). Close ran before the operator's address arrived (`S24 LOST` at 13:05:17); the D-S24P restore reads were taken by hand at 13:08 with `192.168.1.23:42349`.

## Q1, the descriptor of each Samsung mode

**INCONCLUSIVE**

Item 1 (Wireless debugging check): holds. `Q1-w1` (on) and `Q1-w2` (off) both `04E8:6860`, `devclass 00/00/00`, `ifs=[06/01/01:MTP; 02/02/01:CDC ACM; 0A/00/00:CDC ACM Data]`, `adb_if=0`. Wireless debugging does not change the descriptor, so nothing is marked "not representative" for that reason.

| Mode | vidpid | n_if | interfaces | filter_calc | aosp_mtp | verdict (app) | dialogs | mtpn |
|---|---|---|---|---|---|---|---|---|
| ft | 04E8:6860 | 3 | 06/01/01 MTP; 02/02/01; 0A/00/00 | match | yes | accepted: PTP | 0 | 0 |
| tether | 04E8:6863 | 2 | E0/01/03 RNDIS; 0A/00/00 | match | no | accepted: RNDIS | 1 (`confirm_A=1`) | 0 |
| midi | 04E8:686C | 2 | 01/01/00 MIDI; 01/03/00 | none | no | rejected: no Android interface | 0 | 0 |
| ptp | 04E8:6865 | 1 | 06/01/01 | match | yes | accepted: PTP | 0 | 0 |
| chg | 04E8:6860 | 3 | 06/01/01 MTP; 02/02/01; 0A/00/00 | match | yes | accepted: PTP | 0 | 0 |

Items 3 and 4 cannot be read (no decision line), so Q1 is INCONCLUSIVE, not PASS. What does line up: tethering (`filter match`, not MTP) raised the one Android dialog (`confirm_A=1`); MIDI (`filter none`) raised none. **Finding:** the `chg` row (Charging phone only, selected) has the same descriptor as `ft`; the last `UsbDiagnostics` line in its window may be the earlier one, `pm` cannot say (phone unreadable at the plug). Spoken: `Q1-w1` 0 dialogs, File transfer, Android Auto did not start; `Q1-w2` not asked beyond the cue; `Q1-s` "1 dialog", last mode tethering, no session. `fatal=0`.

## Q2, File transfer, three plugs after a CLEAR

**INCONCLUSIVE**

Host items hold; the identity is not the one the brief expects.

| Plug | dlg_all | confirm_A | confirm_B | perm_A | req_A | req_B | stacked | sw_lines | attach_B | ssl | ms | fatal | spoken |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | 2 | 0 | 1 | 1 | 1 | 0 | 0 | 2 | 1 | 2 | 2 | 0 | 1 Open, 1 Allow; session started; File transfer |
| B | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 | 2 | 2 | 0 | 0 dialogs, session, File transfer |
| C | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 | 2 | 2 | 0 | 0 dialogs, session, File transfer |

Plug A: `aosp_mtp=yes`, `app_mtp=yes`, `dec_A=ask` (`src_A=automatic`), `ans_true_ended=1`. Expected shape of round 7's TD-P: met (`Allow` then `Open`). The answer to the user's question: **no dialog on the replugs, a session each time.** The `after=` pairs were `6353/11520 1256/26725`, not `1256/26720` (item 2), and every Q2 plug is `04E8:6865` with `pm_first=config=[ptp]` although the operator reported File transfer. `pm_acc` is 0 on all three (phone unreadable at the plug). Not a FAIL: no stacked, no double request, no request on B or C, no grant without a session, `fatal=0`.

## R1, link loss at defaults (`auto-connect-*` off)

**FAIL**

The fault fired and the new recheck recovered the session, but the desync line the PASS needs never appeared in any rep.

| Rep | fault | desync | wrongflag | lost_attached | sched | ssl | ms | detach | t_ssl after t_fault | usb_notice | wifi_notice |
|---|---|---|---|---|---|---|---|---|---|---|---|
| R1-1 | 1 | 0 | 0 | 1 | 0 | 2 | 2 | 4 | 35.5 s | 1 | 0 |
| R1-2 | 1 | 0 | 0 | 1 | 0 | 2 | 2 | 4 | 12.9 s | 1 | 0 |
| R1-3 | 1 | 0 | 0 | 1 | 0 | 2 | 2 | 4 | 12.9 s | 1 | 0 |
| R1-4 | 1 | 0 | 0 | 1 | 0 | 2 | 2 | 4 | 13.0 s | 1 | 0 |
| R1-5 | 1 | 0 | 0 | 1 | 0 | 2 | 2 | 4 | 13.0 s | 1 | 0 |

`imported=1` in all five. Decisive lines, R1-1: `12:38:42.581 StandardUsbProjectionConnection: debug fault, discarding a 519 byte read as failed.`, then `12:38:42.637 AapService: USB link lost with the phone still attached. Reconnecting in 3000ms.` (56 ms later), `12:38:43.624 showing the USB_LOST notice for a session lost on USB`, `12:39:18.053 SSL handshake complete`. `carries flags 0x` is 0 in the whole capture. So the fault ends the session as a failed read at once, which exercises the recheck commit (`9371f46d`) and not the frame-header check (`ab530358`): the brief's `desync=1` cannot be produced this way. The host cannot tell from the capture whether the fault or the candidate is at fault; the condition as written failed 5 of 5. Other findings: `detach=4` in every rep, but the 12:38:53 to 12:39:17 lines show the phone re-enumerating on its own around the accessory switch with no replug, so `detach=0` cannot hold; R1-1's recovery needed our permission ask (`ohu-usb-ask`, `Allow`, 12:39:16) which the operator answered, hence 35.5 s; the other four reps recovered in 12.9 to 13.0 s with no dialog. `fatal=0`.

## R1-on, link loss with `auto-connect-single-usb=true`

**FAIL**

| Rep | fault | desync | lost_attached | sched | ssl | ms | t_ssl after t_fault | usb_notice | wifi_notice |
|---|---|---|---|---|---|---|---|---|---|
| R1on-1 | 1 | 0 | 0 | 3 | 2 | 2 | 12.96 s | 1 | 0 |
| R1on-2 | 1 | 0 | 0 | 3 | 2 | 2 | 12.9 s | 1 | 0 |
| R1on-3 | 1 | 0 | 0 | 3 | 2 | 2 | 12.9 s | 1 | 0 |

The old path (`sched=3`, `lost_attached=0`) ran as expected; `desync=0` in 3 of 3, same cause as R1.

## R2, one honest notice per lost session

**PASS**

8 of 8 reps (R1 five, R1-on three): `usb_notice=1`, `wifi_notice=0`, `any_notice=1`. Spoken, the second column: the three R1-on reps each showed "Android Auto disconnected: the USB ...". The five R1 reps' TOAST answers were not given separately. Caveat: the brief limits R2 to reps with a desync; none had one, so this grades the notice on the link-loss reps that did occur.

## R3, idle windows

**INCONCLUSIVE**

Five windows: `desync=0`, `ssl=0`, `detach=0`, `fatal=0`, and the session lived on into R1-1 (`fault=1`). But `fed=` read 206/150, 151/150, 150/150, 150/150, 151/150 per 5 s, all above 100: the phone was not idle, so the empty reads that matter were not tested.

## R4, cable pull mid-session

**PASS**

3 of 3: `detach=2`, `ssl=0` after the pull, `any_notice=0`, `usb_notice=0`, `wifi_notice=0`, `lost_attached=0`. Spoken: no toasts in any pull. COUNT: 0 dialogs, session started, File transfer.

## R5, WiFi tip on a wireless loss

**UNTESTABLE**

As the brief: no wireless route on this rig today without D-HU, D-MOTO and Bluetooth. `DisconnectNoticePolicyTest` covers the rule.

## Q3, Charging only and the USB button

**PASS**

`Q3-p1/pre`: `sw_lines=0`, `ssl=0`, `nosw=2` (the plug opened the app, did not switch). `desc.tsv` `Q3-p1` row `04E8:6860`, 3 interfaces; `Q3-p1-m-chg reattach=12:53:14.751` present. `Q3-p1/user`: `rx_check=1`, `sw_lines=2`, `attach_B=1`, `ssl=2`, `ms=2`, `fatal=0`. `NO_USER_DEC` was logged (information). `Q3-p2` (45 s later): row `04E8:6865` (the PTP identity, equal to `Q3-p1-start`, not to `Q3-p1`), `pm_first=config=[ptp]`, `sw_lines=0`, 0 dialogs, no session. `pm_acc` for `/user` is not readable (see Setup notes); the switch and session are proved by `attach_B`, `ssl` and `ms`. Spoken: `Q3-p1` started in Image transfer, chose Charging, the phone switched to File transfer at the USB button press, session started, 0 dialogs. `Q3-p2` came back in Image transfer, the plug opened the app with no dialog, no session, and selecting Image transfer again did nothing.

## Q4, PTP and MIDI with the USB button

**PASS** (ptp). **UNTESTABLE** (midi: `Q4-midi BATTERY_LOW 28`).

`Q4-ptp/user`: `rx_check=1`, `sw_lines=2`, `attach_B=1`, `ssl=2`, `ms=2`, `req_A=0`, `stacked=0`, `fatal=0`; `/pre` `sw_lines=0`. The `desc.tsv` row has no `UsbDiagnostics` line (`diag_lines=0`, `vidpid=none`); the interface (`06/01/01`) comes from `dumpsys usb`. Spoken: 0 dialogs, session started after the USB button.

## Q5, Default USB configuration set to Charging only

**UNTESTABLE**

Battery gate failed at 28% before any Q5 plug. The setting already read Charge only, so nothing changed. `S24-defusb-after` was not taken.

## Close

`P restored` (D-POCO `settings.xml` identical to `backup-P.xml`). `P-end defaults=0 pairs=none` (the Close cleared all stored defaults). `S24 end stay_on=0` (equals `stay_on_before=0`), `adb_enabled=0`, `usb_config=[sec_charging]` (no `rndis` or `ncm`). RESTORE answer (`S24-restore`): the operator said all four steps were done (forget the car, tethering off, forget the test PC and Wireless debugging off, Default USB configuration check, Developer options off); the car name and each value were not itemised. Closing pre-flight: `D_POCO ADB ok, WIFI 1, BT 1, Awake`, PREFLIGHT OK (after `rig_cleanup.sh kill` removed an idle Gradle daemon and the operator's cue-terminal `tail`; D-POCO was moved back from wireless adb to USB and re-cabled by the operator).

## Anything the brief did not ask about

- Every plug in the round after Q1 started from `config=[ptp]` on D-S24P and the Q2 and Q3 replugs came back in PTP, whatever the operator selected before. If the reporter's phone behaves the same, "File transfer / Android Auto" may not be the mode their plugs are in.
- The one dialog on `Q1-s` was the tethering plug (`confirm_A=1`), the first word was not given.
- A permission ask (`Allow`) appears after a link loss when the phone re-enumerates (R1-1), once; with the box ticked it did not come back.
- The phone's wireless adb link drops at every plug; a dedicated way to hold it (or a second wireless route) would save about 15 operator readings per round.
