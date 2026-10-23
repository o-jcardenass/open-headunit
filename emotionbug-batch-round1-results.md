# emotionbug-batch, round 1 results

**Candidate:** `fork/test/emotionbug-batch-4pr` @ `14ad25b8eda4c2f6d4dad7a90ea6041166c0fa76` (tree `63d2a55f6d69968e1d512be51e385ee4c94bcef7`)   **Baseline:** none (one arm, as briefed)
**APK md5:** C `0256704bd40d3784b9840d63dce65ded` (`apk_identity.sh` MATCH on D-HU and D-POCO; `HandshakeFrameReader` 3, `PresentationQueue` 28, `ZbtIdentityProbe` 8 in both live APKs)
**Unit:** D-HU (UNISOC MT50, Android 14) head unit in Stage A; D-POCO (POCO X3 NFC, Android 15) phone in Stage A and USB head unit in Stage B; D-MOTO (Android 14) USB phone in Stage B. Host thermal peaks: A 65C, W3 61C, B 57C, `throttle_pkg=0` throughout.
**Date:** 2026-10-17 (Stage B ended 2026-10-18 00:03)
**Evidence:** release `rig-evidence-emotionbug-batch`, asset `emotionbug-batch-round1-captures.zip` sha256 `8f61dff113f7f2bae1287456d1e51ebcfb7397302d165d8dd0023059a1afe5ff`.

## Verdicts

| Label | Verdict | Basis |
|---|---|---|
| FRAMING | **PASS** | 6 sessions (W1, W2, W3, U1 x2, U2), every one `Version response 1.7, requested 1.2, status STATUS_SUCCESS`; 0 `rejected VERSION_RESPONSE`, 0 `Invalid cleartext handshake header`, 0 `expected channel 0 SSL_HANDSHAKE`. |
| PRESENT | **PASS** (with brief errata) | Titles equal 5 of 5 (W1) and on the W2 skip; largest `longest=` 6 ms on `MUSIC_PLAYBACK`; nav notification gone 8 s after exit (W3); 0 `AA presentation update failed`. |
| BRIDGE | **PASS** | Pre-flight markers all absent, props empty, export `bt=internal`, 0 candidate/module lines, Native handshake completed. |
| LIBUSB | **PASS** (no-regression only) | 2 of 2 libusb sessions formed. The timeout partial-read path was never exercised (no `-7`). Operator may veto how the unplug read errors were treated, see U1. |

| Run | Verdict |
|---|---|
| W1 | **PASS** |
| W2 | **PASS** |
| W3 (addendum, not in brief) | **PASS** |
| U1 | **PASS** |
| U2 | **PASS** |

## Setup notes

- **Where the work ran.** `transfer/rig-rounds` was already checked out in a clean scratch worktree (`rig-sync__scratch__8228cb07__20261015`); it was fast-forwarded there (`f9abbfcc` to `6ffbdc1f`) and the main checkout stayed on `main`. `rig_sync.py sync`: in sync. `CLAUDE.md` unchanged. Quirk files read: `topics/tooling`, `topics/audio`, `topics/gearhead`, `units/D-HU`, `units/D-POCO`, `units/D-MOTO`.
- **Opening pre-flight** (`rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`): PREFLIGHT OK. D_HU wifi 1 bt 1 Awake HFP:none A2DP:none; D_POCO wifi 1 bt 1 Awake HFP:XX:XX:XX:XX:33:59 A2DP:up. D-POCO battery 100, AC powered. D-MOTO was not on adb (operator plugged it in before Stage B; 74 %, unlocked, Bluetooth OFF). D-SAM Bluetooth refcount 0 (off).
- **Build.** Worktree `emotionbug-batch__scratch__14ad25b8__20261017` via `wt-new.sh`; tree matched the brief. `rig_thermal.sh wait 70` read 64C; `build_hur_cool.sh` exit 0; APK copied out at once. Unit tests not run (brief says the dev host did).
- **Install.** `adb install -r -d` Success on D-HU and D-POCO. The executor issued its first two prep steps as parallel tool calls (brief says sequential adb); no ill effect seen.
- **Pre-flight markers (BRIDGE).** `/system/bin/fscsdkbw123`, `/system/bin/gocsdk_zj`, `/dev/rf_serial`, `/dev/zj_bt_serial` all absent; `rw.zlink.bt.type` and `rw.zj.bt.type` empty. BRIDGE not made INCONCLUSIVE.
- **Settings.** HU `shared_prefs` owner `u0_a176:u0_a176` (app uid, correct). Stage A keys written with `set_hu_settings_host.py` and read back; `connection-modes` = {wifi, self}. `enable-audio-sink` was `false` on D-HU and left alone as briefed; no audio decoder was exercised and no grade depends on one. `video-profile-starvation-cap=false` was present on D-POCO and deleted as briefed. Stage B keys written with `set_prefs_runas_host.py`. Full deltas are at the end of this section.
- **Markers** are not gated on this build (`ACTION_LOG_MARKER` not in `CONFIGURING`); `allow-external-configuration=true` was set for `ACTION_EXPORT_LOG`.
- **Scripts.** `rig-toolkit`: `rig_preflight.sh`, `rig_cleanup.sh`, `rig_thermal.sh`, `wt-new.sh`, `build_hur_cool.sh`, `apk_identity.sh`, `set_hu_settings_host.py`, `set_prefs_runas_host.py`. One-round scripts in `rig-data/rounds/emotionbug-batch-round1/scripts/`: `lib.sh`, `stageA.sh`, `stageW3.sh`, `stageB.sh`, copied from the brief with the changes listed below.
- **Windowing.** The launch precedes the `W1-start` marker by about 2 s, so `ACTIVELY LISTENING` (23:18:39.961) falls just before the marker and is counted over the whole capture. The phone capture clock runs about 0.4 s ahead of the HU clock.
- **Brief defects found (Stage A).**
  1. `hu_notes` greps `headunit_navigation` in `dumpsys notification`, which matches the NotificationChannel definition (`mId='headunit_navigation'`) whether or not a notification is posted. `nav=1` before and after the exit measures nothing.
  2. `Nav: NavigationState steps=` is logged only for `INSTRUMENT_CLUSTER_NAVIGATION_STATE` messages. This phone build sent none (0 lines in 450k), so that bullet cannot be met on this phone. Turn data did flow: `Nav: Navigation status=ACTIVE` and 24 `NextTurnDetail` events.
  3. `ph_nav` targets Bogota. D-POCO has no mobile data (operator) and its offline maps cover Medellin, Marinilla and Rionegro. The Bogota route still reached `status=ACTIVE` but only 2 min 49 s after the request. The operator asked for the cached area; W3 does that.
- **Brief defects found (Stage B).**
  1. `log-level=2` hides the DEBUG line `Handshake: SSL handshake complete`. Only `AapSslContext.performHandshake | SSL handshake complete` is logged. My `wait_ssl` matched the DEBUG text, printed `NO_SESSION` three times and ran to its 120 s cap each time; every session was live. Graded on the INFO line.
  2. My plug detection (`dumpsys usb` pattern `/dev/bus/usb/`) read 4 with nothing plugged, so each `wait_usb absent` ran its 300 s cap. The unplug gap was about 4.5 min, not 10 s, and the `U1-replug` and `U2` plug cues are marked at cue time, not at plug time. Real attach times in the capture: 23:36:12 (U1 first), 23:46:04 (U1 second), 23:54:44 (U2).
  3. `B-phone.txt` ends 23:36:10, 2 s before the first plug-in (D-MOTO's wireless adb dropped at the USB mode change; `kill: (254323) - No such process`). There is no phone-side Stage B capture, so the "0 `Critical error`" bullet is unmeasured. Stage A's phone capture was complete.
  4. The FAIL-table line `libusb_bulk_transfer read failed` is produced by the unplug the brief itself mandates (HS2). See U1.
- **Hand steps** (operator): plug D-MOTO x3, unplug x3 (HS1, HS2, HS3, HS2, HS1, HS2 per the brief). The operator reported no VID:PID dialog; `dumpsys usb` shows no stored permission after the round, and the app logged D-MOTO as VID 18D1 PID 2D01 (accessory mode). No injected taps.
- **Settings delta, start to end.** D-HU: `show-navigation-notifications` false to true, `sync-media-session-aa-metadata` false to true, `log-level` 2 to 1, `external-bt-blink-transport` added (false); app-written `wifi-direct-*` and `last-loc-*` keys changed. D-POCO: `auto-connect-last-session`, `auto-start-on-usb`, `reopen-on-reconnection` true to false, `wifi-connection-mode` 3 to 0, `allow-external-configuration` added (true), `auto-connect-single-usb` added (false), `video-profile-starvation-cap` deleted, `connection-modes` = {usb}, `use-libusb` ends false (start false), `last-connection-*` now usb. Both restored from backup at close.
- **Restore and closing pre-flight.** Both `settings.xml` files restored from backup (D-HU as root, `u0_a176:u0_a176` 660; D-POCO via `restore_settings.sh`), `cmp` identical to the backups. Radios back on for D-HU and D-POCO and read back. D-MOTO Bluetooth left OFF as found. Both phones returned to USB adb mode. One idle Gradle daemon was killed with `rig_cleanup.sh kill`. Closing pre-flight: PREFLIGHT OK.

  ```
  ROLE     ADB      WIFI   BT     SCREEN    BT-PROFILES
  D_HU     ok       1      1      Awake     HFP:none A2DP:none
  D_POCO   ok       1      1      Awake     HFP:none A2DP:none
  ```

  D-POCO read `HFP:XX:XX:XX:XX:33:59 A2DP:up` at the opening pre-flight and `HFP:none A2DP:none` at the close, about 40 s after its Bluetooth was re-enabled; the link had not re-formed.

## W1 (the point of the round)

**PASS**

- Settings: Stage A keys (D-HU) as in the brief. `log-level=1`. Radios: D-POCO `svc wifi/bluetooth disable` read back OFF / "Wi-Fi is disabled", then enabled and read back ON / "Wi-Fi is enabled".
- Discard check: clean. 0 `MATCH! Starting AapService`, 0 `Magic Garbage`; 2 `createGroup SUCCESS` (one per session, W1 and the W2 re-arm), interface `p2p-wlan0-0` then `-1`.
- Session: `ACTIVELY LISTENING` 23:18:39.961; one `Sending WifiStartRequest (Type 1)`, one `Incoming connection detected`. `23:18:54.562 Handshake: Version response 1.7, requested 1.2, status STATUS_SUCCESS`; `23:18:54.653 SSL handshake complete`. `Media Sink Setup Request` x2. Last throughput before `W1-end`: `23:23:48.502 Throughput over 5004ms: rendered=56 (11fps) ... presented=56`.
- Titles (`W1-titles.txt`), `hu=` equals `ph=` on 5 of 5, 5 distinct: Años, Luciérnaga Artificial, Sarita, Tu Rostro, Cafés Clandestinos.
- Dispatch: largest `longest=` 104 ms on VIDEO (first 30 s window), 6 ms on MUSIC_PLAYBACK and CONTROL, no line naming NAVIGATION_DIRECTIONS.
- FAIL table (FRAMING, PRESENT, BRIDGE): 0. Export `HUR_Log_20261017_232347_018.txt` header: `bt=internal`.
- Phone Gearhead, W1 window by wall clock: 0 `Critical error`, 0 `ByeBye`. Three `Critical error` lines occur at 23:23:52.610-53.397, the W2 `ACTION_DISCONNECT` (HU 23:23:52.994, clock skew about 0.4 s).
- Not measurable, brief defects: `nav=` (probe matches the channel definition) and `Nav: NavigationState steps=` (never sent). W3 covers navigation and the notification instead.

## W2

**PASS**

- Re-arm at 23:24:01 after `ACTION_DISCONNECT`; session at 23:24:12. `23:24:12.337 Version response 1.7, requested 1.2, status STATUS_SUCCESS` (one), `23:24:12.409 SSL handshake complete`. Handshake wait 10 s.
- Skip: `hu=[Navegando en Tinta] ph=[Navegando en Tinta]`, different from the last W1 title (Cafés Clandestinos).
- 0 FAIL-table lines between `W2-start` and `W2-end`.
- `after-exit nav=1` is the notification channel definition, not a posted notification (defect 1). `after-exit media=description=Android Auto`, recorded as found.

## W3 (addendum, navigation inside the cached map area)

**PASS**

Not in the brief. Added at the operator's instruction (no mobile data on D-POCO; cached maps cover the Medellin / Marinilla / Rionegro area). Destination `Parque Principal Marinilla`, probe = `NotificationRecord` lines for the app package.

- Session `23:27:18.807 Version response 1.7 ... STATUS_SUCCESS`. `Nav: Navigation status=ACTIVE` 11 s after the request (23:27:38.747), 55 `NextTurnDetail` lines, road `Cra. 65`.
- Notifications live (`W3-notif-live.txt`): id 2 `channel=headunit_navigation`, id 1 `headunit_service_v2`, `ranker_group` (`media_v2`). 8 s after `ACTION_DISCONNECT` (`W3-notif-after.txt`): id 1 and `ranker_group` only. The nav notification is gone.
- Dispatch: largest `longest=` 84 ms on VIDEO; none on NAVIGATION_DIRECTIONS. 0 FAIL-table lines.
- Caveat: the count taken at `W3-session`, before the route request, was already 3, so a nav record existed before the request; that list was not saved. The clear after the exit is what was observed.

## U1 (the second point of the round)

**PASS**

Operator may veto the reading of the unplug read errors below.

- Settings (D-POCO, `log-level=2`): `wifi-connection-mode=0`, `connection-modes={usb}`, `use-libusb=true`, auto-start keys false, read back from `settings.xml` before launch.
- `U1-start` 23:34:45 to `U1-end` 23:48:58. `23:36:12.115 LibusbAccessoryConnection: Successfully connected via JNI Libusb`; `23:36:12.880 Version response 1.7, requested 1.2, status STATUS_SUCCESS`; `23:36:12.951 SSL handshake complete`. Second plug: `23:46:04.880 Successfully connected via JNI Libusb`, `23:46:05.526 Version response 1.7 ... STATUS_SUCCESS`, `23:46:05.553 SSL handshake complete`.
- Counts in the window: JNI Libusb 2, SSL complete 2, Version response 2 sessions (both `STATUS_SUCCESS`), `Media Sink Setup Request` 8, `Throughput over` rendered 149-151 (30 fps) after each handshake. D-MOTO's version reply: 1.7.
- FRAMING lines: 0. LIBUSB FAIL-table line `libusb_bulk_transfer read failed`: **1 in the window**, `23:40:51.610 E/UsbNative libusb_bulk_transfer read failed: -1`, 22 ms after kernel `23:40:51.588 usb 1-1: USB disconnect, device number 3`, i.e. at the HS2 unplug the brief mandates. A second one (`-4`, `NO_DEVICE`) at 23:49:43.619 follows the next unplug, 10 ms after the kernel disconnect line, outside the window. Neither is `-7` (`LIBUSB_ERROR_TIMEOUT`). Read literally, the FAIL-table line is in the window; I treated it as caused by the brief's own unplug step and not as a read failure of a live link.
- Phone `Critical error` bullet: unmeasured, see Stage B defect 3.
- The partial-read-on-timeout behaviour LIBUSB changes was never exercised: this run is a no-regression result on libusb.

## U2

**PASS**

- `use-libusb=false` written and read back (`B-uselibusb-U2.txt`).
- `U2-start` 23:54:26 to `U2-end` 23:58:04. `23:54:44.539 StandardUsbProjectionConnection.usbOpen | Established connection: ...`; `23:54:45.292 Version response 1.7, requested 1.2, status STATUS_SUCCESS`; `23:54:45.320 SSL handshake complete`.
- Counts: `Established connection:` 1, JNI Libusb 0, SSL complete 1, Version response 1 (`STATUS_SUCCESS`), `Media Sink Setup Request` 4, rendered 149-152. FRAMING FAIL lines: 0. No `libusb_bulk_transfer read failed` in the window.

## Report back (brief section 10)

1. **Version status per phone:** D-POCO wireless: 1.7 `STATUS_SUCCESS` (3 sessions). D-MOTO over USB: 1.7 `STATUS_SUCCESS` (3 sessions, 2 libusb and 1 standard). No handshake was refused. FRAMING's "any nonzero status is fatal" rule never fired on these phones.
2. **PRESENT:** titles 5 of 5 (plus the W2 skip). Nav notification: gone after exit in W3. Largest `longest=`: 6 ms on `MUSIC_PLAYBACK`; no `NAVIGATION_DIRECTIONS` line in W1 or W3.
3. **LIBUSB:** both libusb sessions formed.
4. **BRIDGE:** `bt=internal`; all four marker files absent, both props empty.

## Anything the brief did not ask about

- D-POCO navigation took 2 min 49 s to reach `status=ACTIVE` on a Bogota destination with no data, and 11 s on the cached Marinilla route.
- D-HU's `enable-audio-sink` was `false` for the whole round, so no media audio channel was set up; nothing graded depended on audio.
- Stage B's D-MOTO wireless adb dropped the moment D-MOTO went into accessory mode. A phone-side capture for a USB round needs a cabled phone or a different logging route.
- D-POCO still bonded to `Navegadortz2` and others; no effect seen.
