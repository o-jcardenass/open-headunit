# native-bt-broker-esp32, round 1 results

**Candidate:** PR head `792989746b8f71b03562e44e067ee4dd47594c4f` (tree `6918b60f1739e57c57762dc9eed6eac877560ea5`, 3 commits on `main` `f9a72d480d51105870f62df6f0a40a1276e98a34`)   **Baseline:** none (C1 is the control)
**APK md5:** `C.apk` 5f5f8e4c24b08734dda392cb10cf1b1d (DEX holds `NativeBTBrokerCarrier`, 30 string hits). D-HU prior APK edac10621db7e3a3dcd37f4b256207db. D-SAM prior APK: not saved (see Setup notes).
**Firmware:** arm 1 `v1.0.0` release bins (4 SHA256 matched the brief). Arm 2 `apply_hfp` `21e0eb48f1ba8afb3f2f37795b2f8fa9c80488ec`, built here: bootloader f7ec7b7d9026e12dd3616bf79cee0e78974a2605afbc2863fcfb0ac9aeb159d4, partitions d90b0f480968f1cde746d7d8746261b38ec098b63c7f45b1aa23698cca4a2236, boot_app0 f94c5d786a7a8fab06ac5d10e33bf37711a6697636dc037559ea19cc410a17f0, firmware 211b4052a969bc4465abd29c0ab0a330cbecb8019789742929d7bbb4fb90dc63. `BOOT_APP0_SAME`, `PARTITIONS_SAME`, bootloader differs (platform espressif32 8.0.0, framework-arduinoespressif32 4.30312.261024, unpinned in `platformio.ini`).
**Unit:** D-HU (MT50, Android 14, rooted shell), D-POCO (phone, Gearhead 18.1.164344-release.daily), board E-1 (ESP32, MAC 78:1c:3c:2b:ac:ec), D-SAM (API 19). D-MOTO radios off throughout.
**Date:** 2026-10-25 and 2026-10-26. BROKER_LE = BROKER_BT = 78:1C:3C:2B:AC:EE. Evidence: release `rig-evidence-native-bt-broker-esp32`, asset `native-bt-broker-esp32-round1-captures.zip` (24724626 bytes, sha256 dec230cdadbf379519cffc23d4387b1dd1019c353dc4f83c5f5f143c5ed4da0b).

## Verdict table

| Run | Verdict |
|---|---|
| C1 control, stock route | **PASS** |
| P pairing (v1.0.0, first pairing) | **PASS** |
| B broker route on `v1.0.0` | **FAIL** (predicted) |
| P repeat on `apply_hfp` (Prepare-2 step 7) | **FAIL** |
| B2 broker route on `apply_hfp` | **UNTESTABLE** (pairing); pre-repair cycles recorded, none formed a session |
| C2 | **UNTESTABLE** (pairing) |
| M-1, M-2 | **UNTESTABLE** (operator: no callee number, Stage M not run) |
| S D-SAM Scan | **FAIL** (predicted) |

## Setup notes

Scripts used: `rig_preflight.sh`, `rig_cleanup.sh`, `wt-new.sh`, `rig_thermal.sh`, `build_hur_cool.sh`, `set_hu_settings_host.py`, `set_prefs_runas_host.py`, `rig_devices.sh`; round scripts extracted from the brief into `rig-data/rounds/native-bt-broker-esp32-round1/scripts/`. Quirk files read: topics tooling, bt, gearhead; units D-HU, D-POCO, D-MOTO, D-SAM, D-SAM-and-D-HP. Template read without 7b and 8.

Opening pre-flight (read-only, PREFLIGHT OK): D_HU wifi 1 bt 1; D_POCO wifi 1 bt 1, HFP and A2DP up to the HU radio; D_MOTO wifi 1 bt 1; D_SAM wifi 1 bt 1. Prepare turned D-MOTO bt and wifi off (read back OFF and disabled). The operator confirmed injected taps (up to 3 per run), no Stage M, and D-MOTO off at closing.

**Brief errors and script deviations (each cost time; fixes are in the round scripts, toolkit untouched):**
1. The brief's `E1` serial path (`usb-0:1.4:1.0`) does not exist. The board with MAC 78:1c:3c:2b:ac:ec sat on `usb-0:1.3:1.0-port0`, the path the brief names as E-2; `1.4:1.1` is D-SAM. The operator confirmed it was E-1 and had removed the other board. Only the `1.3` port was opened.
2. `hu_su` uses `su -c`; D-HU's `adb shell` is uid 0 with no `su` binary. Redefined to run the command directly. A first backup attempt wrote a 0-byte file and was redone.
3. `hu_hfp` and `ph_hfp` read the "Enabled Profile Services" list, wanted the unmasked MAC and `state: CONNECTED`. This HU masks MACs and prints `state=Connected` under `Profile: HeadsetClientService`. Rewritten to match the last two octets. A first read of NOT before the fix was a helper fault; the link was up.
4. `serial_cap.py` started inside the flock-ed `flash.sh` inherited the rig lock descriptor, so the next stage's `flock` hung (C1 launch blocked, no device touched). Restarted with `3>&-` and `setsid nohup`; `flash2.sh` patched the same way. Opening the serial port also resets the board (boot at 19:36:43 and 08:12:04, 08:14:34, all outside cycle windows).
5. `set_hu_settings_host.py` is not executable (first C1 attempt voided both cycles on `keys`, no device launched); called through `python3`.
6. `grade_cycle.py` takes the first occurrence of a RIGMARK label. `serial-E1.txt` is append-only and held `C1-c1`/`C1-c2` labels from a killed first attempt, so C1 cycle 2 was falsely voided `board_reset`. Both cycles regraded from `serial-E1.cur.txt` (lines from the 08:14:34 capture open). The stale row is kept in `aborted-c1/`.
7. `extract_brief_blocks.py` matched nothing (brief headings are `**name.sh**` without backticks); extracted inline with a looser pattern.
8. `$APKS` is not defined in `bb_env.sh`, so D-SAM's `adb pull ... prior-sam.apk` wrote to `/prior-sam.apk` and was denied. **No prior D-SAM APK was saved and the candidate was installed over it** (firstInstall 2026-10-14, versionCode 120 on every build, prior build unrecoverable). `set_prefs_runas_host.py` rejected `setclear` (`unknown edit kind`), so D-SAM settings did not match the brief's table. D-SAM `settings.xml` was restored from its backup (CR-stripped diff identical); the app stays at the candidate until the operator names the build to put back.
9. The pairing step is hard to follow as written: the first re-pairing needed the old phone bond removed by hand first, and the brief does not say so. The `Pair phone` tap was needed in both repeat attempts.
10. `pm grant` of BLUETOOTH_SCAN, BLUETOOTH_CONNECT and ACCESS_FINE_LOCATION failed with "not a changeable permission type"; `dumpsys package` showed `granted=true` for all three.

**Prepare facts:** D-HU `shared_prefs` was `u0_a176:u0_a176` mode 771, `/data/data` owner `u0_a176`, no broker file. `settings.xml` delta against the last round's backup: `video-profile-starvation-cap` false, `wifi-direct-ip-repeated` true, `home-widget-host-id` 7262, `resolutionId` 2 to 3, empty-string spellings. `wifi-direct-stable-identity` true, `auto-start-bt-macs` empty set. Install `-r -d` md5 equal to `C.apk`, `commit` `792989746b8f`, install changed no setting. D-POCO rebooted before Prepare (uptime 0 min confirmed). MACs: POCO DC:B7:2E:5E:4E:59, MOTO A0:46:5A:97:E4:95, HU adapter 11:46:03:10:33:59. Clock offsets host minus device at start: D-HU +1.70 s, D-POCO +0.78 s, D-MOTO -1.08 s, D-SAM about +24 s (its `date` has no `%N`). The host was shut down by the operator after C1 cycle 1 of a first attempt (killed mid-cycle, captures moved to `aborted-c1/`) and the round resumed the next morning. During the shutdown the operator hand-paired the board to test it (D-POCO bond and a saved broker on D-HU at 2026-10-25 19:46); this was cleared at the operator's request (broker file deleted, board NVS erased with `esptool erase-region 0x9000 0x5000`) and D-POCO and D-HU bonds were forgotten by hand before C1 was re-run.

**Evidence asset omissions:** every `settings*.xml` and `keys.log` are left out of the zip because they hold the rig's WiFi Direct and hotspot passphrases; the keys written are listed in this file.

**Operator intervention in B2.** The operator disconnected D-HU's radio from D-POCO by hand "to help the testing" during B2. The phone log shows Settings-app (uid 1000, pid 2435) `HeadsetService` disconnects at 08:46:07.909 (B2 c1, D-HU), 08:47:29.201 (B2 c2, D-HU), 08:47:36.999 (B2 c2, an unrelated headset) and 08:50:09.974 (B2 c3, D-HU); none in C1, B, B2 c4 or B2r. B2 c1 to c3 are void for this reason and B2-g was not graded from them. D-HU's hands-free client reconnects on its own.

**Taps:** injected, operator-approved. Stage P: `p-scan` 08:22:59.895, `p-pick` 08:23:02.558. P repeat attempt 1: `p-scan` 09:32:48.543 (the first dump had 0 matches), `p-pick` 09:32:51.142, `p-pair` 09:33:03.755. Attempt 2: `p-scan` 09:36:58.233, `p-pick` 09:37:00.849, `p-pair` 09:37:13.490. S: `s-scan` 09:42:03.846 (the first S attempt at 09:40:58 was unresolved: dump had 0 texts, screenshot black; after `KEYCODE_WAKEUP` the dump listed the broker screen). Hand steps: H4 by the operator on cue at 08:23:07.726 (paired 23 s later at 08:23:30.444).

## C1, control: stock route, no broker saved

**PASS**

- Settings: section 5 keys, board unpaired and NVS empty, no `NativeBTBroker.xml`, D-HU HFP to D-POCO CONNECTED before and after each cycle (relink 0).
- Cycle 1: `ssl=1`, `t_ssl=11.6`, `thr=23`, `listening=1`, `broker_on=0`, `s_ble_conn=0`, `pdial_macs=11:46:03:10:33:59` (= HU_MAC), `match=0`, `poke=1`, `groups=1`.
- Cycle 2: `ssl=1`, `t_ssl=14.3`, `thr=23`, same flags, `match=0`, `poke=1`, `groups=1`.
- Decisive lines: `08:15:01.259 AapSslContext.performHandshake | SSL handshake complete` and `08:17:36.011 ... SSL handshake complete`; phone `GH.WIRELESS.BT: Creating rfcomm socket for device: 11:46:03:10:33:59` at 08:14:54.217 and 08:17:24.638. Host re-count of `SSL handshake complete` in each cycle window: 1.

A first C1 attempt (2026-10-25, board unpaired) was killed mid-cycle 2 by the operator's shutdown; its cycle 1 row (`ssl=1`, `t_ssl=13.5`) is in `aborted-c1/C1-stale.tsv` and agrees.

## P, pair the board (first pairing, v1.0.0)

**PASS**

- `paired=0`; `phone paired: POCO X3 NFC` 08:23:30.444; `BLE bond ok` 08:23:07.294; `cleared phone bonds for new pairing` and `pairing mode for 120 s` 08:23:07.420; broker saved `78:1C:3C:2B:AC:EE`; D-POCO bonded list names `OpenHU-NativeBTBroker` (..:AC:EE); `Broker: opening new-phone pairing window on` x1.

## B, broker route on v1.0.0

**FAIL** (the predicted result: B-a, B-b, B-c and B-d fail in every cycle)

Three cycles ran; the brief's stop rule (3 in a row without SSL) ended the stage.

| Cycle | ssl | thr | wake | terr7 / terr | s_hfp_on_unknown | s_err | rf_open | pdial_macs | hu_hfp before/after | flood lines |
|---|---|---|---|---|---|---|---|---|---|---|
| B-c1 | 0 | 0 | 5 | 13 / 13 | 13 | 13 | 0 | HU_MAC | CONNECTED / CONNECTED | 644 |
| B-c2 | 0 | 0 | 4 | 12 / 12 | 12 | 12 | 0 | HU_MAC | CONNECTED / CONNECTED | 649 |
| B-c3 | 0 | 0 | 4 | 12 / 12 | 12 | 12 | 0 | HU_MAC | CONNECTED / CONNECTED | 649 |

- B-a, B-b: fail 3 of 3 (`ssl=0`, `thr=0`). B-c: `broker_on=1`, `listening=0`, `poke=0`, but `rf_open=0` and `pdial_macs` is HU_MAC only: the phone dialled D-HU's own radio 26 times per cycle and never `BROKER_BT`. B-d: fails 3 of 3 (no `PROJECTION_UP`, no `HFP HF state` line at all). B-e pass (`match=0`). B-f not reached (no hold). B-g pass (CONNECTED before and after). B-h pass (`proj_fail=0`). B-i matches the prediction: `s_err` = `s_hfp_on_unknown` + `s_hfp_off_unknown` = `terr7` in every cycle.
- Decisive lines, cycle 1: unit `08:24:02.312 NativeBTBrokerCarrier.onTransportError | NativeAA: [Broker] transport error (broker EVT_ERROR code=7) (dash) ending the handshake stream.` Serial `08:24:03.765 rx frame type=08 UNKNOWN` then `08:24:04.387 tx frame type=26 ERROR`. Unit and serial clocks differ by about 1.7 s. No `the phone opened Android Auto on the broker.` line in any cycle, so no error came after a phone channel open; every bring-up ended at the first `HFP_ON`.
- About 645 `Broker: previous start is still on the way to the phone` lines per cycle (about 5 per second at peak).
- Phone Audio Gateway view, cycle 1 before and after: `33:59` (D-HU) connected and `97:64` (an unrelated headset) connected; the board never appears.

## Prepare-2 and flash 2

Source checked out at `21e0eb48f1ba8afb3f2f37795b2f8fa9c80488ec`, CSV `nvs 0x9000, otadata 0xe000, app0 0x10000 size 0x300000, spiffs 0x310000`, `INI_SAME`, PlatformIO build SUCCESS in 550.79 s with `-j 2`, `strings ... | grep -c 'HFP off ('` = 1. E-1 MAC read before flashing. Flash 2: `Hash of data verified` x4, self-check failures 0, `reconnecting last phone` 0. The existing pairing survived in NVS on the phone and HU side (D-POCO bonded list and the saved broker unchanged), but the board-side bond check failed (next sections).

## B2, broker route on apply_hfp

**UNTESTABLE** (pairing). No cycle formed a session; the cycles that ran are recorded.

First pass (run `B2`): c1 void (board reset, see Anything below), c2 and c3 void (operator disconnects), c4 clean re-run of c1.

| Cycle | ssl | s_hfp_on_line | s_err / terr7 | HFP HF state lines | notes |
|---|---|---|---|---|---|
| B2-c1 | 0 | 9 | 0 / 0 | 0>0>0>0 | VOID: 3 firmware panics, `match=1` |
| B2-c2 | 0 | 9 | 0 / 0 | none | VOID: operator disconnects; hu_hfp after NOT |
| B2-c3 | 0 | 9 | 0 / 0 | none | VOID: operator disconnect; hu_hfp after NOT |
| B2-c4 | 0 | 9 | 0 / 0, `terr=1` (`transport error (write rejected)` 08:54:11.338) | none | clean; hu_hfp CONNECTED / CONNECTED |

Clean re-run (run `B2r`, 09:10 to 09:2x, 3 cycles, stop rule): each cycle `ssl=0`, 12 `rx frame type=08 HFP_ON`, 12 `HFP on (CMD_HFP_ON)`, 12 `AA_ENABLE: RFCOMM relay only (HFP_ON/OFF owns HFP)`, 0 `tx frame type=26 ERROR`, 0 `HFP HF state` lines, `pdial_macs` HU_MAC only, hu_hfp CONNECTED before and after, relink 0, 0 panics, 0 Settings-app disconnects. The phone logged `btif_dm_get_connection_state: Acl is not connected to peer:xx:xx:xx:xx:ac:ee`: the board never made an ACL link to the phone.

This met Prepare-2 step 7 (HFP on seen, no `HFP HF state 3`, phone never dials `BROKER_BT`), so the bond was treated as not surviving flash 2. Stage P was repeated twice on `apply_hfp`:

- **Attempt 1** (taps 09:32:48 to 09:33:03, pairing window 09:33:04.125 for 120 s): the board panicked at 09:33:29.573 (`Guru Meditation Error: Core 0 panic'ed (InstrFetchProhibited)`, PC 0x0001000a, backtrace `0x401f952b 0x401f9af8 0x40094c85`), rebooted at 09:33:29.662 and lost the window. Phone bonding started 09:33:40 and 09:34:07 and failed with `HCI_ERR_CONNECTION_TOUT` at 09:34:01.791 and 09:34:30.549. `paired=1`.
- **Attempt 2** (window 09:37:14.125): no panic. Phone bonding started 09:38:22.066 and 09:38:48.386, both `HCI_ERR_CONNECTION_TOUT` (09:38:43.628, 09:39:08.616). The board did not answer the phone's page. Serial in the window: `HFP armed for pairing (inbound only)`, `no classic bond for HFP`. `paired=1`.

On `v1.0.0` the same pairing took 23 s. Two failed repeat pairings stop B2 under the brief's stop rule. **Not done, offered:** a control reflash of `v1.0.0` and a re-pair, to tell a board or rig fault from an `apply_hfp` fault as the cause of the page timeouts. B2-d (HFP drop after SSL) was never reached.

## C2

**UNTESTABLE** (pairing; the board holds no phone bond, so the forget has nothing to return from)

## M-1, M-2

**UNTESTABLE** (operator: no callee number; Stage M not run)

## S, D-SAM (API 19) Scan

**FAIL** (predicted)

- First attempt 09:40:58: `TAP_UNRESOLVED` (0 matches); the screenshot was black. Second attempt after `KEYCODE_WAKEUP`: tap at 09:42:03.846 (host clock), `tap=0`, `fatal=0`.
- Decisive lines (D-SAM clock, about 24 s behind the host): `09:41:39.897 E/AndroidRuntime ( 7809): FATAL EXCEPTION: main`, `Process: com.andrerinas.headunitrevived, PID: 7809`, `java.lang.NoSuchMethodError: android.bluetooth.BluetoothAdapter.getBluetoothLeScanner`, `at com.andrerinas.openheadunit.connection.broker.NativeBTBrokerClient.scan(NativeBTBrokerClient.kt:102)`, `at com.andrerinas.openheadunit.main.BrokerSettingsFragment.startScan(BrokerSettingsFragment.kt:156)`. The screen opened without a crash; the crash came at the Scan tap.
- `pm list features` lists `android.hardware.bluetooth` and `android.hardware.bluetooth_le`. The adapter state line was not captured (the `dumpsys` call printed only `btif_core` lines).

## Three numbers, both arms

| Number | B (`v1.0.0`) | B2 (`apply_hfp`) |
|---|---|---|
| 1. Sessions | 0 of 3 cycles run; no `t_ssl` | 0 of 7 valid-or-void cycles (4 first pass, 3 re-run); none formed SSL |
| 2. HFP after SSL | not reached; `s_err` / `terr7`: 13/13, 12/12, 12/12 | not reached; `s_err` / `terr7` 0/0 in all; the board logged `HFP on` 9 or 12 times per cycle and opened no HFP link |
| 3. Phone re-dials | `pdial_hold` n/a (no hold); g-condition: CONNECTED / CONNECTED x3 | `pdial_hold` n/a; g: B2r CONNECTED / CONNECTED x3, B2 c4 same, c1 to c3 void |

## Anything the brief did not ask about

- **`apply_hfp` crashes deterministically.** The same fault (PC 0x0001000a, same three backtrace frames, ELF SHA256 prefix 77daf41aa) hit 3 times in B2 cycle 1 (08:44:05, 08:44:20, 08:44:36, each about 1 s after an `HFP_ON`) and once during the repeat pairing (09:33:29), and not in B2 c2 to c4 or B2r. Cause not established.
- **`v1.0.0` boot behaviour:** after the board was paired and rebooted it printed `reconnecting last phone DC:B7:2E:5E:4E:59` every 5 s for about 60 s and then `HFP HF state 0`, as the brief predicted for a paired board.
- **The `v1.0.0` HFP dial in `AA_ENABLE` the brief predicted did not appear**: no `HFP HF state` line in any B window.
- **The PR's retry loop is noisy:** about 645 `previous start is still on the way to the phone` lines per 2.5 to 3 minute cycle.
- **Operator hand-pairing and the manual disconnects** both changed rig state mid-round; the first needed a state clear and a restart of C1, the second voided three B2 cycles.
- **D-SAM left on the candidate APK** (no prior copy). D-HU, D-POCO, D-MOTO and the board are restored at closing; the closing pre-flight is pasted below once it runs.

## Closing pre-flight and restore

D-HU: `headunit://exit`, force-stop, broker file deleted, prior APK reinstalled (md5 edac10621db7e3a3dcd37f4b256207db, equal to the pulled copy), `settings.xml` restored as uid 0 with owner `u0_a176` and mode 660, diff against the backup empty, no broker file. D-MOTO: Bluetooth and WiFi disabled (read back OFF and disabled; the opening table had them on, the operator asked for off). D-POCO: Bluetooth enabled and read back ON, no bond to the board. D-SAM: settings restored, app still on the candidate build. Board E-1 left on `apply_hfp` (it ran a blink sketch before); serial capture stopped by pid; E-2 never touched. Thermal watchers and captures stopped by pid, `rig_cleanup.sh list`: no strays, lock free. D-HU still holds an LE bond to `OpenHU-NativeBTBroker-Setter`: forget it by hand (no adb verb). End clock offsets in `clocks-end.txt` (host minus device: D-HU +1.53 s, D-POCO +0.73 s, D-MOTO -1.10 s, D-SAM +25.59 s).

Closing pre-flight (D_HU and D_POCO need wifi and bt):

```
ROLE     ADB      WIFI   BT     SCREEN    BT-PROFILES
D_HU     ok       1      1      Awake     HFP:none A2DP:none
D_POCO   ok       1      1      Awake     HFP:XX:XX:XX:XX:33:59 A2DP:up
D_MOTO   ok       0      0      Awake     HFP:none A2DP:none
D_SAM    ok       1      1      Awake     HFP:none A2DP:none
PREFLIGHT OK
```
