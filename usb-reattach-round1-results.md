# usb-reattach: round 1 results (Stage U; Stage D follows in an addendum)

- Candidate: `fix/usb-reattach` `37bbe71e` (`37bbe71effa84fd5a09a72b0f0f4e226d9db4f09`), APK md5 `4647bf675bf59119b34c5ee7dde72fc6`
- Baseline: `main` `7102b428` (`7102b4283666ffcc7802e49402735e3958cd51a0`), APK md5 `dd8653ee2c5397c691645eae323d28cb`
- Unit: D-POCO as head unit (Android 15), D-MOTO as the phone on D-POCO's OTG port (Gearhead `17.9.664004-release`), both on wireless adb. D-HU took no part
- Date: 2026-10-08
- Stage D (the dongle: R4p, R4w) is not in this file. It follows as an addendum.

## Setup notes

1. **Operator's manual step on D-MOTO, not scripted and not in the brief.** The operator told me, during R1, that after every plug and unplug they set "USB controlled by" to "This device" on D-MOTO by hand, and that this is the only way D-MOTO connects to D-POCO. So every `HAND_SSL` in R1 (cycles 0, 2, 3, 5) includes that change on top of the cable replug, and the brief counts a USB-mode change as a different lever from a cable action. I asked the operator to message "flip" each time they did it during R2, R3 and R5u; none came, but I cannot say none was done. D-MOTO read `current_functions=ACCESSORY`, `kernel_state=CONFIGURED`, `power_role=sink` under a live session.
2. **Operator's timing observation.** Automatic recovery is slower than a hand replug: "auto SSL takes longer than a manual plug unplug, maybe there is something to optimize". Measured (see the timing table in the section after R2b): a hand replug reaches `SSL handshake complete` about 0.6 s after the attach; the candidate's recovery by retry takes about 17.9 s from `Found device already in accessory mode`, and by USB reset 10.6 s.
3. **Tooling.** `lib1008r3.sh` and `ohu_setkeys.py` copied from `bluetooth-audio-disabled-usb-connect-round3/`, `libreattach1.sh` cut from section 6 of the brief; all three used unchanged. `urun2` and `nfail` worked as written. Every stage ran under `flock /tmp/ohu-rig.lock`.
4. **Wireless adb.** D-POCO `192.168.1.8:5555`, D-MOTO `192.168.1.5:5555` (HU and PH in the scripts). D-MOTO's USB serial is not reachable while it is on D-POCO's OTG port; its state was read through the wireless address.
5. **Prepare.** `settings_backup_poco.xml` (20 elements, `wifi-connection-mode` 1, `use-libusb` false) taken with the app stopped; restored byte-identical at the end of Stage U. Kernel log: `dmesg` is not readable on D-POCO (`klogctl: Permission denied`), so no `dmesg` lines for the reset. USB grants: `dumpsys usb` lists only the app's `UsbAttachedActivity` intent filters, no per-device grant lines; `dumpsys-usb-grants-after.txt` equals the before copy, so no new grant. D-MOTO: Gearhead `17.9.664004-release`, no PIN, stay-awake on. Bonds on D-MOTO include `carplay_box_F96B` (the dongle, for Stage D).
6. **Installs.** `adb install -r -d` for both builds, `settings.xml` unchanged after each (diffed). `commit` read `7102b4283666` on the baseline.
7. **Rig audio keys** were not touched (they live on D-HU). D-POCO's own audio keys were not changed.
8. **Hand steps taken** (`hand-steps.log`): R1 only, four H1 replug cues at 18:05:00, 18:08:02, 18:10:05 and 18:12:57 (cycles 0, 2, 3, 5). R3 had five PULL cues at 18:21:00, 18:21:56, 18:22:42, 18:23:25 and 18:24:05. No H2 dialog was cued in the whole stage (`dialogs.log` only holds the focus lines of the hand-recovery steps).
9. **Host** 61C to 73C, throttle delta 0 in every run. No logcat was left running after any run.

## R0

**PASS**

Candidate and baseline built; md5s differ. Unit tests on the candidate: 2856 run, 0 failures, 0 errors (gate 2856). `StaleAccessoryRecoveryPolicy` in dex: candidate 10, baseline 0.

## R1: baseline reproduction, form A, `main` `7102b428`

**FAIL**

One stated condition failed: cycle 1 recovered by itself. In substance the fault and the dead report reproduced.

`nfail R1` = 4 (cycles 1, 2, 3, 5). Cycle 4 had no failure.

| Cycle | fails | tls | found | ssl_auto | miss | verdicts | olddead | outcome |
|---|---|---|---|---|---|---|---|---|
| c0 | 2 | 2 | 3 | 0 | 2 | 0 | 0 | HAND_SSL |
| c1 | 1 | 1 | 2 | 1 | 1 | 0 | 0 | AUTO_SSL (about 25 s, `ph.enter_auto=1`) |
| c2 | 1 | 0 | 2 | 0 | 1 | 0 | 0 | HAND_SSL |
| c3 | 1 | 0 | 2 | 0 | 1 | 0 | 0 | HAND_SSL |
| c4 | 0 | 0 | 1 | 1 | 0 | 0 | 0 | AUTO_SSL |
| c5 | 1 | 0 | 2 | 0 | 1 | 0 | 0 | HAND_SSL |

In c2, c3 and c5 the brief's PASS conditions hold (`ssl_auto=0`, `verdicts=0`, `tear=0`, `olddead=0`, `miss` equals the failure count, `ph.enter_auto=0`). In c1 the failure was followed by an automatic session after about 25 s (`ssl_auto=1`, `ph.enter_auto=1`), which the wording does not allow. `key_at_end` was `absent` in every cycle.

## R2T: the trigger

**PASS**

`miss=0` and `extra=0` in every counted cycle of R2, R3 and R5u (18 cycles). Counted failures (`fails - uefails - exempt`): R2 7 (c0 2, c1 to c5 1 each), R3 2 (c0), R5u 4 (c0 2, c1 1, c2 1), total 13. `tear` equals the counted failures in every cycle (each report came through the guarded path). Kinds: `SSL` x2 in each c0, `TRANSPORT_ERROR` in every other failing cycle.

## R2R: the recovery

**FAIL**

One stated condition did not hold (`ph.enter_auto` below 1 in cycles 1 to 5). No cycle needed the hand ladder: every failing cycle ended `AUTO_SSL` with `key_at_end=absent`, and nothing was cued.

`nfail R2` = 5 (c1 to c5), plus c0.

| Cycle | kinds | step1 | re1 | nc1 | step2 | re2 | accstart | rstok | ssl_auto | dt_ms | ph.enter_auto |
|---|---|---|---|---|---|---|---|---|---|---|---|
| c0 | SSL, SSL | 1 | 0 | 1 | 1 | 1 | 2 | 1 | 1 | 9160 | 1 |
| c1 | TRANSPORT_ERROR | 1 | 0 | 1 | 0 | 0 | 1 | 0 | 1 | 10188 | 0 |
| c2 | TRANSPORT_ERROR | 1 | 0 | 1 | 0 | 0 | 1 | 0 | 1 | 10212 | 0 |
| c3 | TRANSPORT_ERROR | 1 | 0 | 1 | 0 | 0 | 1 | 0 | 1 | 10198 | 0 |
| c4 | TRANSPORT_ERROR | 1 | 0 | 1 | 0 | 0 | 1 | 0 | 1 | 10217 | 0 |
| c5 | TRANSPORT_ERROR | 1 | 0 | 1 | 0 | 0 | 1 | 0 | 1 | 10172 | 0 |

The two numbers that matter:

- **AOA re-switch: 0 re-enumerations out of 6 `step1`.** On this phone the re-switch (`Acc start sent`) does not make D-MOTO leave the bus: in every cycle the log reads `no re-enumeration within 5019ms to 5046ms of RESWITCH; trying the handshake once more`. In c1 to c5 the one extra handshake that follows then succeeds (4.6 s later), with the phone never having left accessory mode. This round cannot say whether the re-switch itself helped that retry or whether any retry after a pause would have worked.
- **USB reset: 1 re-enumeration out of 1 `step2`.** In R2c0 the reset went out at 18:14:41.182, the phone re-enumerated in 313 ms, and the session formed at 18:14:42.918. It worked on an unrooted D-POCO with `avc=0` and no native error.

## R2b: the second step, the give-up and the banner

**PASS** for the part that ran; the give-up and banner path was not measured

Only R2c0 had a second step. Item 1 holds: the `USB_RESET` step line is at 18:14:40.241, `UsbAccessoryMode: USB reset issued to` follows 0.94 s later, and exactly one observation line (`USB_RESET re-enumerated the phone in 313ms`) follows 1.25 s after the step; `rstok + rstno` equals `step2` (1) and `re2 + nc2 + left` equals `step2` (1). No cycle in the round reached a give-up (`giveup=0` everywhere), so items 2 to 4 (the bound on the same plug-in, the `STALE_USB_ACCESSORY` banner, its clearing on SSL) are unmeasured, not passed. `key_at_end` was `absent` in every cycle.

## Timing: automatic recovery against a hand replug

The operator's observation, with numbers from the captures.

| Path | From the phone being visible to `SSL handshake complete` |
|---|---|
| Hand replug (R1 c2, c3, c5): `USB_DEVICE_ATTACHED` to SSL | 0.625 s, 0.614 s, 0.623 s |
| Candidate, recovery by retry (R2 c1 to c5), from `Found device already in accessory mode` | 17.8 s to 17.9 s |
| Candidate, recovery by USB reset (R2 c0) | 10.6 s |

Where the 17.9 s goes in R2 c1 to c5: `found` to `Handshake failed` 7.6 s to 7.7 s (the version exchange retries inside `AapTransport`), then `failed` to `no re-enumeration` 5.6 s (the 5 s wait for a re-enumeration that did not happen in 0 of 6), then `no re-enumeration` to SSL 4.6 s to 4.7 s. In R2 c0: 1.4 s, 5.5 s, 3.6 s. The 5 s wait paid off 0 times out of 6 on this phone; the reset took 313 ms to re-enumerate it.

## R3: the cable form and the TLS failure, form P

**PASS**

Five pulls of D-MOTO's cable, five plug-backs. Every counted cycle meets R2T's per-cycle condition (`miss=0`, `extra=0`). Cycle 0 had the TLS form twice (`tls=2`, `tlsok=2`, `tlsbad=0`, `drained=2`) and recovered through step 1 and step 2 (`re2=1`, `dt_ms` 9193). In cycles 1 to 5 there was no failed handshake after a pull (`fails=0`), the phone re-entered accessory mode by itself each time (`ph.enter_auto=1`), and every cycle ended `AUTO_SSL`. No cycle needed `G_FORCE_STOP`. The older thread reproduced the fault after every pull on this rig; this round did not in 5 of 5 pulls on the candidate, so that comparison is only against a different build and a different day.

## R5u: a user exit during a stale handshake

**PASS**

Two cycles with `ue_hit` (c1 and c2), `uefails=1` in each. In both, `uerep=0` (no `after its own teardown` line within 2 s of the failure the exit caused) and `uesteps=0` (no step line within 25 s of it). In c1 the user exit was sent at 18:25:59 and the exit-caused failure followed at 18:26:01; the next failure, from the cycle's own `ACTION_CHECK_USB`, was a normal stale failure (`TRANSPORT_ERROR`, verdict, step 1) and the cycle settled `AUTO_SSL`. In c2: exit 18:27:27, exit-caused failure 18:27:30, next failure 18:28:05 handled the same way. The log line `session ended while stale accessory recovery runs; the recovery keeps its attempt` appeared in c2. `key_at_end` `absent`.

## R5: wireless beside a failing USB accessory

**UNTESTABLE**

Pre-registered by the brief; not run.

## Anything the brief did not ask about

- **R1, R2R and E-style letter grades.** R1 and R2R are FAIL by the letter of the PASS wording (one condition each) while the substance is clear in the tables; judge from the numbers.
- **The re-switch is a no-op on this phone, the reset is not.** 0 of 6 against 1 of 1. The brief's planning rule ("drop any step that this round shows is dead") applies to the re-switch only if the retry that follows works without it; this round cannot separate the two.
- **Recovery time.** About 17.9 s in the common path (a failed version exchange of about 7.7 s, a 5.6 s wait, a 4.6 s retry), against 0.6 s for a hand replug. See the timing table.
- **Give-up and banner:** never reached, so the `STALE_USB_ACCESSORY` banner, its stamp and the bound on the same plug-in are untested on hardware.
- **Operator's manual "USB controlled by" step** (setup note 1) is a precondition of every hand recovery on this rig and was not in any brief.
- Evidence: release `rig-evidence-usb-reattach`, asset `usb-reattach-round1-captures.zip` (29538728 bytes), sha256 `263ac55e36e3d1e2f09dadb0f023cab00ec7608ad36d3cdc6b9f3102bf32478a`. It holds both stages. The APKs are not in the zip; their md5s are in the header.
