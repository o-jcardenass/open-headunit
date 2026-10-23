# emotionbug-batch, round 2 results

**Candidate:** `fork/test/emotionbug-batch-4pr` @ `4c5960c7856e49c620a17b96c76fe6623d8c1cea` (tree `22c747c98f5568ee4d9233dfb99c49cf7c2d5859`)   **Baseline:** none (one arm, as briefed)
**APK md5:** `756437245576de3c7f308ec21c2ff54a` on D-HU and D-POCO (pulled from each unit and hashed locally); `ACTION_QUERY_STATE` commit `4c5960c7856e` on both
**Unit:** D-HU (UNISOC MT50, Android 14, wireless mode 3, head unit), D-POCO (POCO X3 NFC, Android 15; phone in Stage A, USB head unit in Stage B), D-MOTO (motorola edge 30 neo, Android 14; USB phone in Stage B, Bluetooth off all round)
**Date:** 2026-10-21

## Verdicts

| Label | Verdict | Basis |
|---|---|---|
| FRAMING | **FAIL** (on the letter) | All 6 handshakes (W1, W2, U1 x2, U3, U2) read `the phone selected 1.7 ... STATUS_SUCCESS`; 0 rejected, 0 cleartext, 0 channel-0 errors. The drain ran: `Read 32968 stale USB bytes` in U3. FAIL because U3 logged 5 `Handshake: Version request send failed (ret=-7)` lines, which the brief's table counts. |
| LIBUSB | **FAIL** (on the letter) | U1: one `read failed: -4` 1654 ms after the unplug (brief bound 1 s). U3: 5 `write failed: -7` (2000 ms timeouts). U2 (standard USB) PASS, so per the brief this points at libusb. |
| PRESENT | **PASS** | W1 titles 5/5 matched, 5 distinct; nav ACTIVE and notification posted; `nav=0` after exit; 0 `AA presentation update failed`. |
| BRIDGE | **PASS** | `bt=internal`; 0 `[HCT]`, 0 bridge or module lines; HCT, marker and prop pre-flight clean. |
| SCAN | **PASS** | 0 `FYT Shizuku: `, 0 `Wi-Fi scan control: ` across Stage A and Stage B; prefs file absent. |

| Run | Verdict |
|---|---|
| W1 | **PASS** |
| W2 | **PASS** |
| U1 | **FAIL** |
| U2 | **PASS** |
| U3 | **FAIL** |

## Setup notes

- **Pre-flight (opening):** first attempt FAILed (D-HU not on adb, D-POCO Bluetooth off); the operator fixed both. Second table: D_HU ok wifi 1 bt 1; D_POCO ok wifi 1 bt 1 (HFP `XX:XX:XX:XX:33:59`, A2DP up); D_MOTO ok wifi 1 bt 0. `PREFLIGHT OK`, no strays, lock free.
- **Quirk files read:** `tooling`, `bt`, `wifi`, `audio` (lines 9 to 30 only) (topics); `D-HU`, `D-POCO`, `D-MOTO` (units). Template read without 7b and 8.
- **Build:** worktree `emotionbug-batch__scratch__4c5960c7__20261021`, `HEAD` and tree matched the brief, `build_hur_cool.sh`, host 65 C before the build. No unit tests run (brief says so).
- **D-POCO reinstall:** `adb install -r -d` failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (different debug key). The operator approved the uninstall and had their own backup; `settings.xml` was also saved first (`settings-backup-poco.xml`). The fresh install lost its settings and runtime permissions: onboarding was seeded with `onboarding-version=2` and the app's runtime permissions (location x2, Bluetooth connect and scan, nearby Wi-Fi, notifications, phone state, record audio) were granted with `pm grant`. Neither is in the brief. D-POCO's old backup had `video-profile-starvation-cap=false`; the fresh install removed it. D-HU never had it.
- **Identity:** `HandshakeFrameReader`=3, `PresentationQueue`=28, `HctServiceObserver`=54, `BluetoothPeerDiscovery`=12 in the live dex on both units.
- **Section 7 pre-flight:** four vendor markers absent; `rw.zlink.bt.type` and `rw.zj.bt.type` empty; no `android.microntek.mtcser`; `MTC.BT.matchList` = `null`; no Shizuku or `com.syu.ms` on either unit; `wifi-scan-option.xml` absent on both. D-HU `shared_prefs` owner `u0_a176:u0_a176`, mode 771. The brief's `su -c` form fails on D-HU (adbd is already uid 0), so the files were read directly. D-POCO bonded list held D-MOTO, `Magnetic Speaker`, `DR-82645`, `BC8-Android` and `Navegadortz2` (D-HU); D-SAM not bonded. D-POCO battery 100.
- **Settings:** D-HU Stage A keys written with `set_hu_settings_host.py` and read back (10 of 10). D-POCO Stage B keys written with `set_prefs_runas_host.py` on its wireless serial and read back; `use-libusb` `true` for U1 and U3, `false` for U2 (read back). D-HU also carried `native-driver-selection-mode`=1 and `last-connected-native-mac`=`DC:B7:2E:5E:4E:59`, left as found.
- **Scripts:** `extract_brief_blocks.py` wrote nothing for this brief, so the blocks were cut from the brief by line range into `scripts/` (`lib.sh` with `OUT` made absolute and `rig_devices.sh` sourced). `runA.sh` is the brief's `stageA.sh`, the W1 steps and `W2.sh` as one script; `runB.sh` is `stageB.sh`, U1, U3 and U2. Both ran from the host, not through an executor.
- **Deviations in `runA.sh`:** it waits up to 30 s for `ACTIVELY LISTENING` before switching the phone's radios on. That line prints before `W1-start` (as the brief warns), so the wait always ran out; harmless, 30 s. Its first launch refused to start because my own Spotify check looked at too few lines (nothing started, no capture). Its log export step took the newest file by mtime; D-HU's clock reads 2026-06-17, so the fresh export `HUR_Log_20260617_031302_135.txt` sorted older than a 2026-10-17 file and the wrong file was pulled first. The right file was pulled by hand and the `bt=` read comes from it; the wrong one is kept as `W1-export-STALE-wrongfile.txt`.
- **Deviations in `runB.sh`:** `ACTION_CHECK_USB` is sent 15 s after the plug is seen instead of straight away, to leave the operator time for the permission dialog. The operator answered one dialog (OK, not "always") at the first plug of U1; no later plug raised one that they reported.
- **Clocks:** D-HU logcat reads 2026-06-17 (anchored on `AutomationMarker` lines); D-POCO is within about 0.1 s of the host; D-MOTO runs about 2 s ahead of the host (teardown at 14:35:23.2 after `ACTION_DISCONNECT` at 14:35:21.2), so phone windows were shifted by +2 s.
- **Music source on D-POCO:** Spotify, a playlist the operator started; the media session read by `ph_title` was Spotify.
- **Thermal:** host 65 C before the build; Stage A max 69 C, Stage B max 76 C, `throttle_pkg=0` in both logs.
- **Settings deltas, backup against end of round:** D-HU: the brief's keys (`external-bt-blink-transport`=false added, `show-navigation-notifications` false to true, `sync-media-session-aa-metadata` false to true, `log-level` 2 to 1) plus app-written Wi-Fi Direct bookkeeping (`wifi-direct-ip-repeated`, `wifi-direct-last-ip`, a digest, new `last-group-bssid` and `readback-bssid`) and empty strings re-serialised. D-POCO: not meaningful, its app was reinstalled and its settings rebuilt from scratch (82 differing lines). Both baselines were restored with the apps stopped and read back **byte-identical** to the backups (`cmp`); D-HU `settings.xml` owner `u0_a176:u0_a176`.
- **Restore:** radios read back as in the opening table (D-HU wifi 1 bt 1, D-POCO wifi 1 bt 1, D-MOTO wifi 1 bt 0). D-POCO and D-MOTO were returned to USB adb mode with `adb usb` (they come back on a PC cable). No logcat left running. Build worktree removed; an idle Gradle daemon from the build was stopped with `rig_cleanup.sh kill`.
- **Closing pre-flight:** `PREFLIGHT OK`. D_HU ok wifi 1 bt 1 Awake; D_POCO ok wifi 1 bt 1 Awake (HFP `XX:XX:XX:XX:33:59`, A2DP up); D_MOTO ok wifi 1 bt 0 Awake; `rig clean: no strays, lock free`.
- **Evidence:** release `rig-evidence-emotionbug-batch`, asset `emotionbug-batch-round2-captures.zip` (17188194 bytes), sha256 `78eb35e73ee51494cf852e2be1f9a7f363b2ab28ef225aee46184a9f54a216f0`. It holds both stage captures, the four D-MOTO dumps, the exports, marks, grades, notes and scripts; the APK is not in it.

## W1

**PASS**

- Settings: D-HU Stage A keys (section 4). Radios: D-POCO `svc wifi`/`bluetooth` off then on by `ph_off`/`ph_on`, read back. Discard rules: `MATCH! Starting AapService`=0, `createGroup SUCCESS`=1 in the window, `Magic Garbage`=0.
- `ACTIVELY LISTENING on Android Auto UUID`=2 over the capture (one per arm), `Sending WifiStartRequest (Type 1)`=1, `Incoming connection detected`=1, `SSL handshake complete` 1 session (the INFO and DEBUG lines of the same handshake, 03:10:37.952 and .954).
- `06-17 03:10:37.880 ... Handshake: the phone selected 1.7 (we asked for 1.2), status STATUS_SUCCESS`; exactly one.
- `Media Sink Setup Request`=2; last `Throughput over` before `W1-end`: `rendered=149 (29fps)` at 03:13:04.937.
- Titles (5 skips, 20 s apart): `hu=` equals `ph=` on 5/5 (`Joseph`, `Don't Wait Up`, `The Death & Resurrection Show`, `Live by the Sword`, `Dark Side`), 5 distinct.
- After `W1-nav`: `Nav: Navigation status=ACTIVE`=1 (03:12:41.469), `NextTurnDetail`=17, `nav=1` in `W1-notes.txt`: a `NotificationRecord` for the app, id 2, `channel=headunit_navigation`.
- `transport dispatch over`: only the `VIDEO` channel logged (`longest=86ms`, 6, 6, 5); no line at all for `MUSIC_PLAYBACK` or `NAVIGATION_DIRECTIONS`, so none reached 50 ms.
- FAIL table over the whole capture up to `W1-end`: 16 rows, all 0. Export: `bt=internal` (from `HUR_Log_20260617_031302_135.txt`, which carries `commit=4c5960c7856e`).
- `BluetoothHelper: driver candidates: ` printed 3 times, identical: `2 phone, 0 unknown, 1 not a phone - hidden: Magnetic Speaker (hands free unit); discovery: ANDROID=READABLE, entries=3, rejected=0; HCT_PAIR_CACHE=ABSENT, entries=0, rejected=0; manufacturer-only=0`.
- Phone side (Gearhead, `W1-start` to `W1-end`): `ByeBye`=0. `Critical error`: 5 lines at 14:26:26.44 to 14:26:27.16 on the phone clock. They are `Protocol error 4 (34): reason:1`, two `3 ... io error` and `18 ... Failed to read message`: the brief's own description of a head-unit exit. They sit in the same wall-clock second as `W1-end` and `W2-start`, where the script sends `ACTION_DISCONNECT`. Nothing earlier in the 2 min 50 s session. Graded as the exit, not a fault in the session.

## W2

**PASS**

- `after-exit nav=0`; `after-exit media=description=Android Auto` (information).
- After `W2-rearm`: one `Handshake: the phone selected 1.7 (we asked for 1.2), status STATUS_SUCCESS` at 03:13:28.188; `SSL handshake complete` 03:13:28.239; `Media Sink Setup Request`=2; `rendered=118` then `rendered=150`.
- Skip: `hu=[description=Self Aware]`, `ph=[description=Self Aware]`; last W1 title was `Dark Side`.
- FAIL table, `W2-start` to `W2-end`: 14 rows, all 0. Phone `Critical error` from `W2-rearm` to `W2-end`: 0.
- Information: the second `createGroup SUCCESS` (03:13:18.974) is the re-arm after the exit, one per window. The `NAVIGATION_UPDATE` broadcast history came back empty before and after (`W2-navbc-*.count` = 0), so: history not available, the STOP broadcast could not be observed.

## U1

**FAIL**

- Settings: `use-libusb=true`, `connection-modes={usb}`, mode 0, `log-level=2`. Hand steps: plug 14:33:07 (UsbHostManager: VID 0x22B8, PID 0x2E81, `motorola edge 30 neo`; operator pressed OK on the permission dialog, "always" not ticked), unplug (kernel line 14:34:27.214), replug 14:34:35.
- Window `U1-start` to `U1-end`: `Successfully connected via JNI Libusb`=2 (14:33:11.781, 14:34:38.314); `SSL handshake complete`=2 (14:33:12.673, 14:34:39.018); two `phone selected 1.7 ... STATUS_SUCCESS` (14:33:12.611, 14:34:38.989); `Media Sink Setup Request`=8 (4 per session); `Throughput over` `rendered=148` to `151` after each handshake. All met.
- Record per session: `Read N stale USB bytes`: none in either session; `Discarding unframed USB data`: none; `read timeout: preserved=`: 0.
- FAIL-table lines: FRAMING rows 0. LIBUSB: `14:34:28.868 E/UsbNative libusb_bulk_transfer read failed: -4`. The unplug exception does not cover it as written: it follows the kernel line `14:34:27.214 usb 1-1: USB disconnect, device number 3` by **1654 ms** (bound 1 s), and precedes the `UsbHostManager ... Removed device` line (14:34:28.874) by 6 ms. This is the single failing condition. `-4` is `LIBUSB_ERROR_NO_DEVICE`, and the second hand unplug (outside any graded window) shows the same shape with a 1658 ms gap, so the brief's bound looks shorter than this rig's removal latency; I did not stretch it.
- Phone dumps `B-phone-U1a.txt` and `B-phone-U1b.txt`: `Critical error` inside both session windows (clock +2 s): 0. Three appear at 14:34:32 on the phone clock, between the unplug and the replug, which is outside the sessions.

## U2

**PASS**

- Settings: `use-libusb=false` (read back `name="use-libusb" value="false"`).
- `StandardUsbProjectionConnection ... Established connection:` 14:36:46.728 (=1); `Successfully connected via JNI Libusb`=0; `SSL handshake complete` 14:36:47.530 (=1); one `phone selected 1.7 ... STATUS_SUCCESS` (14:36:47.487); `Media Sink Setup Request`=4; `rendered=121` then 150 to 151. FAIL table 0; phone `Critical error` in the session 0.

## U3

**FAIL**

- Same capture, D-MOTO stayed plugged in, `use-libusb=true`. `ACTION_DISCONNECT` 14:35:21.2, `ACTION_CHECK_USB` 14:35:26.635. It **reconnected in place**: no replug was needed and the `NO_INPLACE` fallback did not run.
- Success: `SSL handshake complete` 14:35:44.064 (=1), one `phone selected 1.7 ... STATUS_SUCCESS` (14:35:44.022), `rendered=104` then 150. Phone `Critical error` in the session window: 0. That is 17.4 s after `ACTION_CHECK_USB`.
- Failing condition (0 FAIL-table lines for FRAMING and LIBUSB): 5 `Handshake: Version request send failed (ret=-7), attempt N` and 5 `libusb_bulk_transfer write failed: -7, transferred=0/10, endpoint=0x01, timeout=2000ms`.
  - First connection 14:35:26.655: `14:35:27.296 Handshake: Read 32968 stale USB bytes before version request`, then write failures at 14:35:29.299 (attempt 1), 31.503 (2), 33.707 (3), then `session state disconnected (link_lost)` at 14:35:33.721.
  - Second connection 14:35:38.795: write failures at 14:35:41.451 (attempt 1) and 43.654 (2), then the version response is received and the handshake completes.
  - D-MOTO's own capture shows its previous session already torn down at 14:35:23 (`PROJECTION_ENDED_USB`), with no `Critical error` while the writes timed out.
- Record: stale bytes 32968 on the first connection, none on the second; no `Discarding unframed USB data`; `preserved=`: 0.
- No baseline arm exists, so this cannot say whether `main` behaves the same on this case.

## Anything the brief did not ask about

- **The 1 s unplug bound.** Both hand unplugs: kernel disconnect, then `read failed: -4` 1654 ms and 1658 ms later, then the framework removal line 2 to 6 ms after that. U1 FAILs on it by the letter. The second occurrence fell outside every window, so it did not change any verdict.
- **The `Version request send failed` row.** The build's resend loop logs it at WARN on each failed attempt, so a write that is later resent successfully still counts as a FAIL-table line. U3's second connection recovered on a resend (attempt 3, not logged); the first connection used all 3 attempts and was dropped.
- **The drain.** U3 is the only session that found stale bytes (32968 in one read), and it did not eat the phone's reply: the handshake that followed was accepted. U1 and U2 found none.
- **Wrong export by mtime** (D-HU clock 2026-06-17): any script that takes the newest `HUR_Log_*` by `ls -t` on D-HU picks a stale file.
- **`extract_brief_blocks.py`** found no blocks in this brief. The headings in the brief put a blank line between the bold file name and the code fence, which is probably why.
- **The brief's `su -c` pre-flight lines** do not work on D-HU, where adbd already runs as root.
- The fresh install on D-POCO (forced by the signing key) is a variable round 1 did not have; D-POCO's app state was otherwise clean.
