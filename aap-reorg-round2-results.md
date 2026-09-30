# aap package reorganisation — round 2 results

**Candidate:** `fix/head-unit-server-silence-and-log-attribution` @ `6479a374`
(`6479a3744bebbbf5b3ba4df51b3af3e35e73cb28`)
**Baseline:** none (brief §1 — no A/B; pre-move count is on record from `58802778`)
**APK md5:** `c365fd12a088983e1f40b6a8b3038d43` (github debug, `3.3.0-beta2`)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14, 1440×720) + POCO X3 (`M2007J20CG`,
Android 15, Gearhead `17.5.663204-release`) as the Native AA phone. Build host: `/opt/android-studio/jbr`.
**Date:** 2026-08-27

## Verdict in one line

**All four runs PASS.** The keyword-rename fix works: `6479a374` compiles clean and runs 780/0,
exactly the on-record pre-move count, so round 1's kapt failure is gone (**R0**). Every `Java_`
symbol in both native libraries decodes to a relocated Kotlin class that exists at `6479a374`, with
no stale `connection_UsbNative` / `decoder_FfmpegHevcDecoder` prefix (**R1**). After the operator
re-paired the phone to the head unit mid-round, a full Native AA session formed and held ~3 min at
~52 fps hardware HEVC, one group, one SSL handshake, projected-widget tap → `PLAYING`, zero
`UnsatisfiedLinkError` (**R2**). With `force-software-decoding=true` the bundled FFmpeg HEVC decoder
engaged on its own JNI symbols — `Configuring bundled FFmpeg HEVC decoder for 1920x1080`,
`FFmpeg HEVC decoder thread count: 6`, `Bundled FFmpeg HEVC decoder initialized`, frames rendered in
every one of 45 throughput windows — with zero `UnsatisfiedLinkError` and no `Configuring decoder:`
fallback (**R3**). The `_00024Companion_` symbol path the round exists to check is confirmed at
runtime, not just by string match. One finding for the coding session, unrelated to the relocation:
on the software-decode path `AapProjectionActivity` logged `Recreating projection view due to
settings change` 4× in the first 30 s of the session, restarting the decoder each time before
settling — see "Anything the brief did not ask about".

## Setup notes

- Scripts used: `hur-wifi-test-scripts/build_hur.sh` (R0 build), `run_unit_tests.sh` (R0 tests),
  `set_prefs_runas.sh` (R3 settings write), `restore_settings.sh` (settings restore). No new scripts
  added. R1's symbol audit was run inline per the brief (scripted, no device).
- **The round ran in two sittings.** R0/R1 and a first R2 attempt were done while the phones were
  unpaired from the head unit (`dumpsys bluetooth_manager` → HU `Bonded devices:` empty,
  `NativeAA: No paired Bluetooth devices found to poke` every retry, no session in ~4 min). The
  operator then re-paired the POCO X3 to the head unit by hand (a UI step adb cannot do), after
  which the HU shows `DC:B7:2E:5E:4E:59 [DUAL] POCO X3 NFC` bonded and `bredr_authenticated:T` /
  `bredr_encrypted:T`. R2 and R3 below are from after the re-pair. The pre-re-pair R2 attempt
  capture is kept as `r2.txt`; the scored R2 is `r2b.txt`.
- **R3 took two attempts.** Attempt 1: the phone's own AA reconnect did not fire, the run fell
  through to the poke, the poke connected 3× (HFP-AG / HSP-AG) but the phone answered without
  opening the AA channel — `NativeAA: The phone has answered 3 wake pokes but has never opened the
  Android Auto channel … most likely bound to a different Bluetooth device that also advertises the
  Android Auto service`. The POCO's paired list holds both `Navegadortz2` (this HU) and `FX Plus`
  (plausibly the rig's OEM factory Bluetooth module). This is the rig's known session-to-session
  poke/reconnect variance (`TESTING-TEMPLATE.md` §7a), not a relocation effect — R2 five minutes
  earlier formed a session on the phone's own reconnect with no poke needed. Attempt 2, a clean
  restart, formed a session in ~18 s. Attempt 1 capture kept as `r3-attempt1.txt`; the scored R3 is
  `r3.txt`.
- **`settings.xml` delta vs a fresh backup:** the scalar-key set is identical (sorted semantic diff
  empty). Standing non-defaults carried from earlier threads, none load-bearing for this round:
  `wifi-connection-mode=3`, `video-codec=H.265`, `debug-video-low-latency=true`,
  `software-video-decoder=1`, `enable-audio-sink=false`, `log-level=2` (INFO), `view-mode=2`
  (TEXTURE), `show-fps-counter=true`, `fake_speed=true`, `head-unit-make=Google`. `settings.xml`
  restored byte-identical to the backup at the end of R2 and again at the end of R3 (verified both
  times).
- **Audio sink was off for both live runs** (`enable-audio-sink=false`, the rig's standing value —
  no test write). Audio therefore played from the phone's own speakers; this rig has no speaker
  output anyway (§7a), and no run's verdict depends on head-unit audio.
- **R3 settings written** (app stopped, via `set_prefs_runas.sh`): `force-software-decoding=true`,
  `software-video-decoder=1` (already the default, written anyway per brief §4), `video-codec=H.265`
  (already set, idempotent).
- **Log level kept at INFO** (`log-level=2`). All brief §5 lines verified at source as `AppLog.i`
  (`decoder/video/VideoDecoder.kt:1229` / `:1627` / `:1994`,
  `decoder/video/FfmpegHevcDecoder.kt:36` — note the relocation moved `VideoDecoder.kt` and
  `FfmpegHevcDecoder.kt` into `decoder/video/`, brief §5's bare filenames still resolve).
- The 2× / 1× `MATCH! Starting AapService` in the R2 / R3 captures are the phone's own Bluetooth
  reconnect with zero group churn attached (no `P2P-GROUP-STARTED` / `-REMOVED` near them, single
  `createGroup SUCCESS`, single `p2p-wlan0` index per run) — the phone coming back, which is the
  intended path, not contamination per §7a's refined discard rule.
- **The stale bond, now resolved:** before the re-pair, `dumpsys bluetooth_manager` on the HU
  showed an empty `Bonded devices:` list and the POCO held only a one-sided `BOND_TYPE_PERSISTENT`
  record (`bredr_linkkey_known:T` but `bredr_encrypted:F` / `bredr_authenticated:F`, no active
  link); a phone-side Bluetooth off/on cycle did not re-establish it. Round 1's read that the empty
  HU list was "a ROM display quirk" did not hold up — the bond was genuinely gone on the HU side and
  needed a manual re-pair. Worth checking `Bonded devices:` on the HU at the start of any future
  Native AA round.
- **The app dropped two stale `auto-start-bt-macs` on the first (pre-re-pair) launch**
  (`A0:46:5A:97:E4:95`, `DC:B7:2E:5E:4E:59` — "Dropping Auto Start BT MAC(s) no longer paired").
  `DC:B7:2E:5E:4E:59` is in fact the POCO's own MAC and came back as a live bond after the re-pair.
  `settings.xml` was restored byte-identical to the backup at each round end regardless.
- No `git` history surprises: `git checkout 6479a374` → `HEAD` = `6479a3744beb…`, parent `f824721c`
  ("Chore clean aap"), grandparent `58802778`. The `connection/wifi/modes/native/` directory is
  absent; `connection/wifi/modes/nativeaa/` holds all 15 files; `grep -rn 'modes\.native[.;]'
  app/src/` is empty.

## R0 — Unit test gate and count parity

**PASS**

- Build: `./gradlew assembleGithubDebug` at `6479a374` — `BUILD SUCCESSFUL`, APK produced
  (`com.andrerinas.headunitrevived_3.3.0-beta2_debug.apk`, md5 `c365fd12a088983e1f40b6a8b3038d43`).
  Round 1's `:app:kaptGithubDebugKotlin` failure on `SettingsFragment.java` (the
  `modes.native.CredentialField` stub) does **not** recur — kapt stub generation and `javac` both
  pass.
- Tests: `./gradlew testGithubDebugUnitTest` — green. Aggregated from
  `app/build/test-results/testGithubDebugUnitTest/*.xml`:

  ```
  tests=780  failures=0  errors=0  skipped=0
  ```

- **Executed count: 780. Expected: 780 (the `58802778` on-record count). Delta: 0.** No moved test
  dropped out of discovery; the rename added and removed none.

## R1 — APK native symbol audit (point of the round, with R3)

**PASS**

Ran on `lib/*` from the R0 APK (`c365fd12…`), all four ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`,
`x86_64`) — identical symbol sets on each.

**`libusbhelper.so` — 10 symbols, all present, all correct:**

```
Java_com_andrerinas_openheadunit_connection_usb_UsbNative_accModeSwitch
Java_com_andrerinas_openheadunit_connection_usb_UsbNative_claimInterface
Java_com_andrerinas_openheadunit_connection_usb_UsbNative_closeDevice
Java_com_andrerinas_openheadunit_connection_usb_UsbNative_detachKernel
Java_com_andrerinas_openheadunit_connection_usb_UsbNative_exitContext
Java_com_andrerinas_openheadunit_connection_usb_UsbNative_initContext
Java_com_andrerinas_openheadunit_connection_usb_UsbNative_nativeRead
Java_com_andrerinas_openheadunit_connection_usb_UsbNative_nativeResetDevice
Java_com_andrerinas_openheadunit_connection_usb_UsbNative_nativeWrite
Java_com_andrerinas_openheadunit_connection_usb_UsbNative_wrapDevice
```

Decoded package/class `com.andrerinas.openheadunit.connection.usb.UsbNative` — the Kotlin file
`app/src/main/java/com/andrerinas/openheadunit/connection/usb/UsbNative.kt` declares exactly these
10 `private external fun`s (`initContext`, `wrapDevice`, `detachKernel`, `claimInterface`,
`nativeWrite`, `nativeRead`, `nativeResetDevice`, `closeDevice`, `exitContext`, `accModeSwitch`).
No symbol carries the pre-fix `connection_UsbNative` name.

**`libhur_soft_hevc.so` — 4 symbols, all present, all correct:**

```
Java_com_andrerinas_openheadunit_decoder_video_FfmpegHevcDecoder_00024Companion_nativeCreate
Java_com_andrerinas_openheadunit_decoder_video_FfmpegHevcDecoder_00024Companion_nativeDecode
Java_com_andrerinas_openheadunit_decoder_video_FfmpegHevcDecoder_00024Companion_nativeIsAvailable
Java_com_andrerinas_openheadunit_decoder_video_FfmpegHevcDecoder_00024Companion_nativeRelease
```

`_00024` → `$`, so `com.andrerinas.openheadunit.decoder.video.FfmpegHevcDecoder$Companion` — the
Kotlin file `decoder/video/FfmpegHevcDecoder.kt` declares exactly these 4 `private external fun`s in
its `companion object` (`nativeIsAvailable`, `nativeCreate`, `nativeDecode`, `nativeRelease`). No
symbol carries the pre-move `decoder_FfmpegHevcDecoder` (unqualified) or `aap_FfmpegHevcDecoder`
name.

**Self-check (brief §"R1"):** explicit search of every `.so` in the APK for the pre-fix / pre-move
strings `connection_UsbNative`, `aap_UsbNative`, `decoder_FfmpegHevcDecoder`, `aap_FfmpegHevcDecoder`,
`modes_native` → **0 files each**. The old names come back absent, and the audit reports that as a
detected difference, not an empty pass. Total distinct `Java_` symbols in the APK: exactly 14
(10 + 4), no others.

## R2 — Native AA baseline smoke

**PASS** (after the operator re-paired the phone; see Setup notes)

- Settings written: none (standing config, `wifi-connection-mode=3`). Capture `r2b.txt`.
- Radio state: POCO Bluetooth+WiFi cycled off via `svc` (verified `wifi_on=0` / `bluetooth_on=0` /
  BT `state: OFF`), HU app launched, ~16 s settle, POCO radios restored (verified `wifi_on=1` /
  `bluetooth_on=1` / BT `state: ON`).
- Discard-rule check: **clean** — `createGroup SUCCESS` = 1, single `p2p-wlan0-0` interface for the
  run, one `SSL handshake complete`, no `Magic Garbage detected`. The 2× `MATCH! Starting
  AapService` are the phone's own BT reconnect with no group churn attached (Setup notes).
- Decisive log lines (timestamps):

  ```
  20:11:15.167  NativeAA: ACTIVELY LISTENING on Android Auto UUID (4de17a00-…) on radio [Navegadortz2]
  20:11:15.866  WifiDirectManager: 5GHz createGroup SUCCESS!
  20:12:14.574  NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) on local radio [Navegadortz2]
  20:12:15.019  NativeAA: [TX] Sending WifiStartRequest (Type 1)
  20:12:15.105  NativeAA: [RX] Received Type 2 (Payload size: 0)
  20:12:18.294  WirelessServer: Incoming connection detected from /192.168.49.78
  20:12:18.503  AapSslContext.performHandshake | SSL handshake complete. Session id: VyqZ1J2…
  20:12:19.477  AapControlMedia.mediaSinkSetupRequest | Media Sink Setup Request: 7 on channel VIDEO
  ```

- Measurements: `createGroup SUCCESS` = 1, `SSL handshake complete` = 1, `Incoming connection
  detected` = 1, `UnsatisfiedLinkError` = 0 (full 490,109-line capture, `grep -a`). 38 `Throughput
  over` windows, **rendered > 0 in every one** (`rendered` 227→301, ~52 fps steady, 45–60 fps
  range), `dropped` = 0 and `concealed` = 0 in every window, 0 `rendered=0` windows, codec
  `c2.unisoc.hevc.decoder` (hardware HEVC) throughout.
- Projected-widget tap: `adb -s <hu> shell input tap 272 657` (×2) after relaunching Spotify on the
  phone → the phone's `dumpsys media_session` reports `state=PlaybackState {state=PLAYING(3)}` for
  `com.spotify.music`. Since the play control is drawn *into* the projected video, this also proves
  the touch channel end to end — `TouchCoordinateMapper` / `KeyCode` moved in `f824721c`.

Session held ~3 min 15 s (SSL 20:12:18 → capture stop 20:15:33). Full session, one group, one
handshake, live picture, working touch, no native link error. PASS on every brief condition.

## R3 — Bundled FFmpeg HEVC engagement (point of the round, with R1)

**PASS** (attempt 2; attempt 1 was rig poke-variance, see Setup notes)

- Settings written (app stopped): `force-software-decoding=true`, `software-video-decoder=1`,
  `video-codec=H.265`. Verified by reading `settings.xml` back before launch. Capture `r3.txt`.
- Radio state: same clean-run sequence as R2 (POCO radios off → launch HU → ~16 s → POCO radios on).
- Discard-rule check: **clean** — `createGroup SUCCESS` = 1, single `p2p-wlan0-5` interface for the
  run (index 5 = lifetime P2P count on the rig, not churn within the run), one `SSL handshake
  complete`, no `Magic Garbage detected`, 1× `MATCH! Starting AapService` (phone BT reconnect, no
  group churn).
- Decisive log lines (timestamps):

  ```
  20:20:31.277  NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59)
  20:20:33.345  WirelessServer: Incoming connection detected from /192.168.49.120
  20:20:33.560  AapSslContext.performHandshake | SSL handshake complete. Session id: CBGT7wD…
  20:20:34.504  AapControlMedia.mediaSinkSetupRequest | Media Sink Setup Request: 7 on channel VIDEO
  20:20:35.272  VideoDecoder.applyStreamDimensions | H.265 SPS parsed: 1920x1080 (negotiated 1920x1080)
  20:20:35.275  VideoDecoder.startBundledHevc | Configuring bundled FFmpeg HEVC decoder for 1920x1080
  20:20:35.275  FfmpegHevcDecoder.start | FFmpeg HEVC decoder thread count: 6
  20:20:35.278  VideoDecoder.startBundledHevc | Bundled FFmpeg HEVC decoder initialized
  20:20:35.312  VideoDecoder.onSoftwareFramesRendered | First bundled software HEVC frame rendered
  ```

- Measurements against the brief's PASS list:
  - `Media Sink Setup Request: 7 on channel VIDEO` — present (H.265; phone is sending HEVC, not 3).
  - `Configuring bundled FFmpeg HEVC decoder for` — present (9× total: initial + 4 view-recreate
    cycles in the first 30 s, then stable; see the finding below).
  - `FFmpeg HEVC decoder thread count:` — present (`thread count: 6`).
  - `Throughput over` rendered > 0 in every steady window — **45 windows, 0 with `rendered=0`**,
    `rendered` 126→247 (25–49 fps), `dropped` = 0 / `concealed` = 0 in every window, codec
    `ffmpeg-hevc` throughout. (`fed=0` in every window is the software path's known counter
    artifact — the YUV frame sink renders via callback and does not increment `fed`; `rendered` is
    the load-bearing number and it is healthy.)
  - `UnsatisfiedLinkError` — **0** in the full 260,530-line capture.
  - `Failed to load hur_soft_hevc` / `FFmpeg HEVC decoder native library is not loaded` — **0** each.
  - `Configuring decoder:` (the MediaCodec fallback) — **0**. The bundled path took the session; no
    silent fallback.

The `_00024Companion_` JNI symbols in `libhur_soft_hevc.so` resolve and the native HEVC decoder
runs end to end. PASS on every brief condition; the FAIL signatures (ULE on an `FfmpegHevcDecoder`
symbol, `Configuring decoder:` with no bundled line, an explicit FFmpeg failure line) are all
absent. Session held ~4 min (SSL 20:20:33 → capture stop 20:24:43).

## Report-back answers (brief §8)

1. **R0 executed-test counts and delta:** 780 executed / 0 failed at `6479a374`; expected 780;
   **delta 0**. Build and kapt both green — round 1's failure is fixed.
2. **R1 symbol list per library:** `libusbhelper.so` — 10 symbols, all
   `Java_com_andrerinas_openheadunit_connection_usb_UsbNative_*`, matching `UsbNative.kt`'s 10
   `external fun`s. `libhur_soft_hevc.so` — 4 symbols, all
   `Java_com_andrerinas_openheadunit_decoder_video_FfmpegHevcDecoder_00024Companion_*`, matching the
   `FfmpegHevcDecoder` companion's 4. No stale prefix anywhere in the APK. **No mismatching symbol.**
3. **R3 verdict with engagement line and fps:** **PASS.** `Configuring bundled FFmpeg HEVC decoder
   for 1920x1080` + `FFmpeg HEVC decoder thread count: 6` + `Bundled FFmpeg HEVC decoder
   initialized`, steady-state **~48 fps rendered** (`rendered` 234–247 per 5 s window) on
   `codec=ffmpeg-hevc`, zero `UnsatisfiedLinkError`, zero MediaCodec fallback.

## Anything the brief did not ask about

- **Software-decode path only: `AapProjectionActivity` recreates the projection view on a loop at
  session start.** On R3 (`force-software-decoding=true`), the log carried
  `AapProjectionActivity.recreateProjectionView$lambda$6 | Recreating projection view due to
  settings change...` at 20:20:45, :55, 20:21:05, :15 — four times, ~10 s apart — each one calling
  `VideoDecoder.stop | Decoder stopped: projectionViewRecreate` and re-running `startBundledHevc` +
  a fresh surface. It **stopped on its own after 30 s** and the session then ran clean at ~48 fps
  for 3+ more minutes, so it is a startup transient, not a permanent loop, and R3 still passes. But
  it does not happen on the hardware path — R2's 490k-line capture and R3-attempt-1's capture both
  have **0** `Recreating projection view`. Something on the bundled-FFmpeg bring-up is signalling a
  "settings change" to the activity (nothing in `settings.xml` was being written by the test). Worth
  a look on the coding side: `AapProjectionActivity.recreateProjectionView` and whatever preference
  observer feeds it, against the `startBundledHevc` / `SoftwareYuvFrameSink` path. Captures:
  `r3.txt` lines ~32180, ~41950, ~51650.
- **The head unit had lost its Bluetooth bond to the phone** and needed a manual re-pair before any
  Native AA session could form (see Setup notes). Round 1's "ROM display quirk" read of the empty
  HU `Bonded devices:` list was wrong — check that list at the start of every Native AA round.
- Round 1's suggested fix was `connection.wifi.modes.nativeaa`, and that is exactly what
  `f824721c` now uses. The rename touched the six moved files plus the nine already in that package
  plus referencers; the tree delta from the old orphaned tips is the rename only.
- During the pre-re-pair R2 attempt the relocated classes
  `connection.wifi.modes.nativeaa.NativeAaHandshakeManager`, `connection.wifi.WifiLauncherManager`,
  `WifiDirectManager`, `aap.AapService`, `utils.BluetoothHelper` all loaded and ran (visible by
  name in a logged stack trace) with no `ClassNotFoundException` / `NoClassDefFoundError` — an
  early confirmation the relocation's Kotlin side is sound, later made moot by the full R2/R3
  sessions. The one `E/OPENHU` exception then (`BluetoothHelper.adapterForService` →
  `NoSuchMethodException: android.bluetooth.BluetoothAdapter.<init>`) is a pre-existing reflection
  fallback probe, unrelated, and names the new package path correctly.
