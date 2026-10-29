# bluetooth-audio-disabled-usb-connect, round 1 results

**Candidate:** `fork/fix/bluetooth-audio-disabled-usb-connect` @ `fff96699480d878bb36bd04078d77260231b19dc`       **Baseline:** none (measurement round, every comparison is a settings change on the candidate)
**APK md5:** candidate `234caaadff2d4731e006591a0c69bb2a` (identical on D-POCO and D-HU, `apk_identity.sh` MATCH on both; every run re-checked it, see `apk-check.log`)
**Unit:** Stage U: D-POCO (POCO X3 NFC, Android 15) as head unit and USB host, D-MOTO (motorola edge 30 neo, Android 14, Android Auto 17.9.664004-release) as phone on D-POCO's OTG port. Stage N: D-HU (UNISOC MT50, Android 14) over Native AA, D-MOTO as phone.
**Date:** 2026-10-07
**Evidence:** release `rig-evidence-bluetooth-audio-disabled-usb-connect`, asset `bluetooth-audio-disabled-usb-connect-round1-captures.zip`, sha256 `d0267cb69eaedf9bfb563cfa867701ae59bf9854397d9f9dd67b03c68e291009`. Everything a verdict rests on is quoted below.

## Setup notes

**Round-level reading, in one place**

- **The brief's policy instrument does not work on this phone, and the operator chose to go on with log-line-only grading.** `pol` reads `A2DP=NA` in every row of `policy.tsv` (44 rows, P0 to P3 for every run). Details below. Every DISABLED / NOT DISABLED reading in this file therefore rests on Android Auto's own phone-side log line `disabling A2dp via profile disabler`, and every restore reading in Stage N rests on the operator reading D-MOTO's "Media audio" toggle for D-HU by eye (H7). No stored-policy number was read for anything.
- **The answer to the point of the round:** on this phone (Android Auto 17.9.664004) the A2DP disabler fires for the default identity `Google` / `Desktop Head Unit` over USB, three times out of three (U1). The brief's code read (17.8) predicted no change for that identity on compiled defaults. This phone evidently carries a pushed value. The flag dump that would show it could not be taken (section "Android Auto flags").
- With the list identity `Aptiv` / `Volvo [AAR]`: real address disables (U4, N2a, N2b), `blank` and `skip` do not (U2, U3). The disabler fires 0.7 to 1.0 s after our `SSL handshake complete`, before any media start.
- Over Native AA the toggle is disabled in session and comes back on after a clean end (N1, N2a, N2b), and stays off after an Android Auto force-stop (N3a, N3b).
- U5 not run: D-POCO's starting policy could not be read (see U5).

**Build gate (P1)**

- Worktree `../ohu-wt-bad-fff96699` at `fff96699480d878bb36bd04078d77260231b19dc` (`git rev-parse HEAD` matches). First build failed: `SDK location not found`, the new worktree has no `local.properties`. Copied it from the main checkout and rebuilt (`build.log`: `build exit=0`).
- Unit tests: 2747 tests, 0 skipped, 0 failures, 0 errors (counted from the JUnit XML); `unit exit=0`.
- DEX: `BluetoothAnnouncePolicy` present (10 occurrences in `classes*.dex`). `ACTION_QUERY_STATE` on both units: `"commit":"fff96699480d"`.
- Host waited below 75C before the builds and before every run (`th_gate`); `thermal.log` has 18 run starts, no HOST_TOO_HOT, throttle delta 0 on every run, max 65C.

**Pre-flight** (`rig_preflight.sh`, first attempt failed on D-MOTO `bluetooth_on=0`; the operator switched it on; second attempt):

| ROLE | ADB | WIFI | BT | SCREEN | BT-PROFILES |
|---|---|---|---|---|---|
| D_POCO | ok | 1 | 1 | Awake | HFP:XX:XX:XX:XX:33:59 A2DP:up |
| D_HU | ok | 1 | 1 | Awake | HFP:none A2DP:none |
| D_MOTO | ok | 1 | 1 | Awake | HFP:none A2DP:none |

PREFLIGHT OK. Later preflights failed twice on `rig lock is held by another run`: a leaked `th_watch` subshell (`sleep 20` loop) from my own session script kept the lock fd open after the script exited. Killed by pid, see the last section.

**Quirk files read:** `rig-quirks/topics/tooling.md` (not reread in full, scripts-first), `rig-quirks/units/D-POCO.md`, `rig-quirks/units/D-MOTO.md`.

**Scripts used** (all in `hur-wifi-test-scripts/`): `build_hur.sh`, `run_unit_tests.sh`, `apk_identity.sh`, `rig_devices.sh`, `rig_preflight.sh`, `rig_thermal.sh`. **Added this round, left in place:** `bluetooth-audio-disabled-usb-connect-round1/` with `lib1008.sh` (the brief's helper), `ohu_setkeys.py` (copied from `projection-teardown-and-relays-round3/`), `stageU.sh` and `stageN.sh` (drivers, one case per run).

**Fixes to the brief's `lib1008.sh`, all kept in the copy**

1. `su -c` does not exist on D-HU (`su: inaccessible or not found`); `adb shell` there is already uid 0. Removed the `su -c` wrappers from `hu_put` (stat, run, read-back). Backup and restore of D-HU's settings were done without `su`.
2. `usb_up` sent `ACTION_CHECK_USB` to a stopped app and the verb failed: `startForegroundService() not allowed due to mAllowStartForeground false` (first U1a attempt, void, `void/`). Added `am start -n .../main.MainActivity` and a 3 s sleep before the verb. After that the verb returned `ok:true`.
3. Policy instrument (`pol`): see below. Left as is, it prints `NA`.

**The policy instrument (section 7 fallback)**

- `dumpsys bluetooth_manager` on D-MOTO has no `BluetoothDatabase` section. `grep -a -n -i -E "BluetoothDatabase|A2DP=|connection_policy|a2dp" P.btdump` finds only `A2dpOffloadEnabled: true`, `Profile: A2dpService`, `A2DP State:`. The address's last octets appear only in the bonded-device list (`DC:B7:2E:5E:4E:59 [ DUAL ] POCO X3 NFC`, `11:46:03:10:33:59 [BR/EDR] Navegadortz2`). `dumpsys activity service com.android.bluetooth/.a2dp.A2dpService` prints 3 lines, no policy. `/data/misc/bluetooth` is `Permission denied` for the shell user; D-MOTO is not rooted.
- So there is no A2DP field to name. The operator was told, and chose option (a): go on with log-line-only grading. Where the brief says P0/P1/P2/P3, this file reports `NA` and the line counts instead.

**Android Auto flags**

- P6: `dumpsys activity service com.google.android.projection.gearhead` returns `No services match: com.google.android.projection.gearhead` (`gh-flags-prepare.txt`, 75 bytes). The three flag lines (`BluetoothPairing__disable_a2dp`, `UsbBabysitter__a2dp_fix_car_list`, `UsbBabysitter__enable_a2dp_at_projection_end`) were therefore **not obtained**. The live dump in U1a was not taken either (same command, same refusal expected).
- Android Auto is 17.9.664004-release, not the 17.8.163744 the brief's strings come from. Drift seen: `CarBluetoothService. car address=` printed **0 times in all 11 valid runs**, and none of the restore strings (`Found A2DP entry...`, `Did not find...`, `a2dp cannot be re-enabled...`) printed. The disabler line did print, under tag `CAR.BT.SVC.LITE`, which is how it was found. `ph[Critical error]` read 2 in U1a/U1b/U1c/U2/U3/U4/N1, 3 in N2a/N2b, 0 in N3a/N3b: not tied to the arm.
- `CAR.BT.*` tags were raised to VERBOSE in Prepare as written.

**Calls (H4).** U1a: the call went through (`U1a-ringing` 10:56:14.795, `U1a-answered` 10:56:20.087, `U1a-hangup` 10:56:35.438). The operator could not place calls from U2 on, so U2, U3 and N1 recorded no ring (the script waited 60 s, then sent KEYCODE_CALL / ENDCALL to an idle phone). `ph[No high priority audio routes available]` is 0 in every run, including U1a. The call is ungraded in all of them.

**Hand steps, as actually done.** The log is `hand-steps.log`.

- **H1 was not a physical replug.** The operator did it by changing D-MOTO's USB mode from file transfer to USB tethering in its settings, which re-enumerates the link. That is why `Settings$UsbDetailsActivity` was on screen at several H3 cues (dialogs.log). Every H3 cue was a timeout of the script's 60 s wait; there was never an Android Auto or system screen blocking the session (operator: "There is no AA or system screen on d-moto"). The D-MOTO Dialer app was in front once (after the U1a call); I sent HOME.
- **H2:** D-POCO raised `systemui/.usb.UsbPermissionActivity` (U1a, U1b) and `systemui/.usb.UsbConfirmActivity` (U1c onwards). Allowed every time. "Always" was never ticked (the operator did not look for it).
- **H4:** see Calls. **H5:** not needed, D-MOTO and D-POCO were already bonded. **H6:** after N3a and after the first N3b (toggle left off), the operator turned Media audio back on for D-HU. **H7:** read by eye, sequences in the Stage N runs below.
- D-POCO is bonded to both `Navegadortz2` (D-HU) and `Navegadortz3` (D-SAM); `rig-quirks/units/D-POCO.md` warns about that for wireless bring-up. Stage U was USB only and Stage N did not use D-POCO (Bluetooth off and airplane mode on during Stage N), so it did not matter here.
- The `Settings` app on D-MOTO crashed twice while the operator had Bluetooth settings open (N3a 11:48:20, void N3b 11:52:35); see N3a.

**Adb transport.** Stage U ran on wireless adb (D-POCO 192.168.1.8, D-MOTO 192.168.1.5). `dumpsys usb` before and after: `dumpsys-usb-before.txt` (777 lines), `dumpsys-usb-after.txt` (899 lines). One run (the first U2 attempt) stopped after 4 s with an empty `U2.keys`: `pocoput` returned non-zero. It never opened a capture and was re-run (below).

**Runs voided and re-run (each at most once, as the brief says)**

- U1a first attempt: `ACTION_CHECK_USB` refused (item 2 above). Evidence in `void/`.
- U1c first attempt: no session. Two attempts in the window both opened the accessory endpoints, then `Handshake: Version request send failed (ret=-1)` three times, `Handshake failed` (11:01:43 and 11:03:53); the replug retry also failed; `VOID no session`. Re-run: valid. Evidence in `void-U1c-1/`.
- U2/U3 first attempt: the driver moved on after the U2 `pocoput` non-zero (above); U3 had started its bring-up and was killed by me at its H3 cue. Re-run both, valid. Evidence in `void-U1c-1/` (U2/U3 files).
- U4 first attempt: two sessions in one window (SSL at 11:23:30.095 dropped `link_lost` at 11:23:31.096, second SSL at 11:23:58.346). Brief needs `hu.ssl` = 1: invalid. The disabler fired in both (11:23:30.779, 11:23:58.986). Re-run: valid (1 session). Evidence in `void-U4-1/`.
- N3b first attempt: two sessions (SSL 11:52:29.278 `link_lost` 11:52:50.500, second SSL 11:53:00.228) and `ph[FATAL EXCEPTION]` = 1 (`com.android.settings`, 11:52:35). Invalid. Re-run: valid. Evidence in `void-N3b-1/`.
- N3a is **kept as counted**, with a deviation: `ph[FATAL EXCEPTION]` = 1 (`com.android.settings`, pid 19300, 11:48:20.032, `Failed to call observer method`) while the operator had Bluetooth settings open, 6 s after the run start. It is not Android Auto and not our app (`hu.fatal` = 0). The brief's validity rule says FATAL = 0 in both captures; the operator may regrade. N3a was not re-run (the stop rule allows one re-run per run and N3b already agrees).

## R U0

**FAIL**

The gate item P8 failed: no A2DP value could be read for either address (`A2DP=NA`, see the policy instrument note). Every other item held: md5 `234caaadff2d4731e006591a0c69bb2a`, 2747/0, DEX symbol, `commit` `fff96699480d` on both units, `POCO_BT` = `DC:B7:2E:5E:4E:59` (`settings get secure bluetooth_address`), D-MOTO bonded to D-POCO and D-HU, both wireless adb links answering, `allow-external-configuration` `true` after the first `pocoput` (U1a `.keys`). The brief says a gate FAIL stops Stage U; Stage U was run on the operator's instruction (log-line-only grading) and every Stage U verdict below rests on that.

## R U1

**PASS**

Three valid repetitions, same reading. Default identity (`getk`: `head-unit-make` `Google`, `head-unit-model` `Desktop Head Unit`, `bt-announce` `real`). Reading: **DISABLED**.

| Run | SSL | announce (real, `carAddress=DC:B7:2E:5E:4E:59`) | `disabling A2dp via profile disabler` | window count | `Set A2DP policy for car to` | P0 / P1 / P2 / P3 |
|---|---|---|---|---|---|---|
| U1a | 10:55:25.894 | 10:55:26.186 | 10:55:26.910 | 1 | 0 | NA x4 |
| U1b | 10:59:40.605 | 10:59:40.919 | 10:59:41.620 | 1 | 0 | NA x4 |
| U1c | 11:10:13.620 | 11:10:13.681 | 11:10:14.320 | 1 | 0 | NA x4 |

Each: `hu.ssl` 1, `hu.media_start` 1, `hu.fatal` 0, `ph[FATAL EXCEPTION]` 0, one announce line. Decisive line, U1a phone capture: `10-07 10:55:26.910 I/CAR.BT.SVC.LITE(27862): disabling A2dp via profile disabler`. `ph[CarBluetoothService. car address=]` is 0 in all three (string not present in 17.9, see Setup notes), so the "H4 signal" cannot be evaluated. Live flag lines: not obtained.

## R U2

**PASS**

List identity (`Aptiv` / `Volvo [AAR]` read back), `bt-announce` `blank`. Valid: one `No Bluetooth service announced: bt-announce=blank` (`hu.announce_blank` 1, `hu.announce` empty), SSL 11:14:35.551, `hu.media_start` 1, fatal 0. 0 `disabling A2dp via profile disabler`, 0 `Set A2DP policy for car to`. Policy half not readable (`NA`). Reachability lines: `ph[Car does not support Bluetooth service; skipping disableA2dp.]` 0 and `ph[disabling A2dp route while in projection]` 0, so the PASS rests on U4 showing a disable with the same identity (it did). Call: not placed (see Calls).

## R U3

**PASS**

List identity, `bt-announce` `skip`. Valid: one `Bluetooth service announced with carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)` at 11:19:14.886, SSL 11:19:14.571, `hu.media_start` 1, fatal 0. 0 disabler lines, 0 `Set A2DP policy for car to`. `ph[Special car Bluetooth address that should be skipped]` is **0** (the code read expected 1 or more; string not present in 17.9). Both reachability lines 0, so the PASS rests on U4. Call: not placed.

## R U4

**PASS**

Positive control. List identity, `bt-announce` `real`. Valid on the re-run: one session (SSL 11:35:10.254), one announce (`carAddress=DC:B7:2E:5E:4E:59 (bt-announce=real)` 11:35:10.315), `hu.media_start` 1, fatal 0. Decisive line: `10-07 11:35:10.949 I/CAR.BT.SVC.LITE(27862): disabling A2dp via profile disabler` (1 in window). `Set A2DP policy for car to`: 0. P2 and P3: `NA`; no restore line printed (`Found/Did not find A2DP entry...` 0, `a2dp cannot be re-enabled...` 0). The first U4 attempt (two sessions) also fired the disabler twice, in `void-U4-1/`.

## R U5

**UNTESTABLE**

Not run: D-POCO's starting policy could not be read (P8 `NA`), so a restore cannot be seen on D-POCO. Stage N covers the unclean end (N3a, N3b).

## R N0

**INCONCLUSIVE**

The brief's gate, `pol N0` reading `100` for D-HU, cannot be read (`NA`). The operator read D-MOTO's Media audio toggle for D-HU (`Navegadortz2`) by eye at 11:39 and said **on**. The runs went on from that.

## R N1

**PASS**

D-HU's own identity (`Google` / `Desktop Head Unit`, key `bt-announce` absent), clean end. Valid: one announce `carAddress=11:46:03:10:33:59 (bt-announce=real)` (11:40:26.051), SSL 11:40:25.101, `hu.media_start` 1, fatal 0, `ph[FATAL EXCEPTION]` 0, no createGroup/`MATCH! Starting AapService` outside the single bring-up. Reading: **DISABLED**: `10-07 11:40:25.870 I/CAR.BT.SVC.LITE: disabling A2dp via profile disabler`, 1 in window. Policy P0 to P3: `NA`. H7 (operator, D-MOTO "Media audio" for D-HU): at the cue 11:41:49 "on"; corrected at 11:42:23: first reading at the cue was on, **later mid-session off**, **after the session ended on**. The disabler fired about 55 s before the cue, so the first "on" is likely a stale screen. Call: not placed. `ph[No high priority audio routes available]` 0.

## R N2a

**PASS**

List identity, real address, clean end. Valid: SSL 11:43:38.418, announce `carAddress=11:46:03:10:33:59 (bt-announce=real)` 11:43:39.362, media start 1, fatal 0. **DISABLED**: disabler 11:43:39.259 (1). H7 (operator): before the session on, in session **off**, after the session ended **on**. Policy `NA`, so the brief's "P2 or P3 = 100" is replaced by the by-eye reading (on). No restore log line printed.

## R N2b

**PASS**

As N2a. SSL 11:46:04.637, announce 11:46:05.617, disabler 11:46:05.448 (1), media start 1, fatal 0. H7: before on, in session **off**, after the session ended **on**.

## R N3

**PASS**

Two counted repetitions, same reading: **NO RESTORE** after an Android Auto force-stop (`am force-stop com.google.android.projection.gearhead`, then `ACTION_EXIT` 2 s later). Both DISABLED in session.

| Run | SSL | disabler | H7 (operator) |
|---|---|---|---|
| N3a | 11:48:50.223 | 11:48:51.016 (1) | before on, in session off, after the session ended **off** (no restore) |
| N3b (re-run) | 11:55:44.947 | 11:55:45.722 (1) | before on, in session off, after the session ended **off** |

N3a carries the Settings `FATAL EXCEPTION` deviation (Setup notes). After each run the toggle was off and the operator turned it back on (H6) before the next run. The voided first N3b (two sessions) is informative: the operator saw the toggle go **on** after the first session dropped `link_lost`, off again in the reconnection, off at the end. Policy `NA` throughout.

## Anything the brief did not ask about

1. **USB re-attach to a phone left in accessory mode fails the handshake until D-MOTO re-enumerates.** In U1c (twice), U2 (first attempt), U3 (first attempt) and U4 (first attempt) the head unit logged `Found device already in accessory mode: motorola motorola edge 30 neo (VID: 18D1 PID: 2D01)`, opened the endpoints (`Connected have EPs`), then three times `Handshake: Version request send failed (ret=-1)` and `Handshake failed`, e.g. 11:01:35.633 to 11:01:43.412 and 11:03:46.071 to 11:03:53.753 in U1c. D-MOTO was on its plain launcher at several of those cues, so it is not the Settings screen. The session formed only after the operator changed D-MOTO's USB mode (re-enumeration) and allowed the D-POCO dialog; U2 and U3 formed on the script's own retry. U1a and U1b formed on their first attempt. Not graded and not investigated further: this round measures Android Auto, not USB re-attach, and no code was changed. Whether this is an app bug or a phone-side accessory state is not established.
2. **The disabler fires on SSL, not on media start.** In all 9 DISABLED runs (U1a, U1b, U1c, U4, N1, N2a, N2b, N3a, N3b) the line appears 0.7 to 1.0 s after `SSL handshake complete` and before `Media Start Request AUDIO:` (e.g. U4: SSL 11:35:10.254, disabler 11:35:10.949, media start 11:35:21.106; N1: SSL 11:40:25.101, disabler 11:40:25.870, announce 11:40:26.051). The brief's "it runs when Android Auto starts to send media audio" does not hold on 17.9.
3. **This phone disables for the default identity.** That contradicts the brief's prediction from compiled defaults; the pushed-value flags (`UsbBabysitter__a2dp_fix_car_list`) could not be read here.
4. **`CarBluetoothService. car address=` and every restore string are absent from 17.9.664004**, so any later round should not use them as validity gates.
5. **Harness leaks found.** `th_gate` in `lib1008.sh` starts `th_watch` in a subshell and never kills it when the script ends, so the process kept `/tmp/ohu-rig.lock` and the next `flock` call and `rig_preflight.sh` blocked on it. Fixed in `stageN.sh` (kill `$THPID` after each run); killed by pid for the earlier ones. A stale `rig_thermal.sh watch` from before this session (pid 78277, about 1.5 h old) is still running and was left alone.
6. **`su` is absent on D-HU** (`su: inaccessible or not found`); `adb shell` is root. Worth stating in `rig-quirks/units/D-HU.md`.
7. **Closing state.** D-POCO: settings restored (24 keys, identical to `settings_backup_poco.xml`; the first read-back diff printed an empty stdin, a timing race, the file was 1391 bytes at 11:37 and compared equal key by key), Bluetooth on, airplane off. D-HU: settings restored byte-identical to `settings_backup_dhu.xml`, `head-unit-make` `Google`, `head-unit-model` `Desktop Head Unit`. D-MOTO: `stayon` off, Media audio for D-HU **on** (operator confirmed at 11:58:11), policy not readable by adb so only by eye. `ps aux | grep logcat`: 0.

## Policy table

`policy.tsv` has 44 rows (11 valid runs x P0 to P3), all `A2DP=NA`. The file is in the release asset.
