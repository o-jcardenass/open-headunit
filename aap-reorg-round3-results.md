# aap package reorganisation — round 3 results

**What this round is:** a re-run of the round 2 brief (`aap-reorg-round2-brief.md`, all four runs
R0–R3) against the current branch tip, which is one commit past round 2's candidate. No new brief;
the round 2 brief applies verbatim except the candidate SHA.

**Candidate:** `fix/head-unit-server-silence-and-log-attribution` @ `e4d4bf70`
(`e4d4bf7081338ecc16f05af95610c4fa21b8f8fd`) — round 2 tested `6479a374`; `e4d4bf70` adds exactly
one commit on top: **"Video: a directly uploaded YUV frame now counts as a drawn frame"**
(`GlProjectionView.kt`, +5 lines).
**Baseline:** none (brief §1 — no A/B; pre-move count on record from `58802778` = 780/0).
**APK md5:** `edfc19475083814825a0402ab03a6199` (github debug, `3.3.0-beta2`, versionCode 101) —
differs from round 2's `c365fd12…` only by the one-file `GlProjectionView.kt` change.
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14, 1440×720) + POCO X3 (`M2007J20CG`,
Android 15, Gearhead `17.5.663204-release`) as the Native AA phone. Build host `/opt/android-studio/jbr`.
**Date:** 2026-08-27

## Verdict in one line

**All four runs PASS, and the round 2 finding is fixed.** R0 780/0 delta 0 (`e4d4bf70` adds no
tests). R1 symbol audit byte-for-byte identical to round 2 — `e4d4bf70` touches no native code. R2
(hardware HEVC path, unaffected by the commit) a full Native AA session, ~48 fps
`c2.unisoc.hevc.decoder`, one group, one handshake, projected-widget tap toggles Spotify
PLAYING↔PAUSED, zero `UnsatisfiedLinkError`. **R3 is the run that changes:** the bundled FFmpeg HEVC
decoder engages exactly as in round 2, but the projection-view rebuild loop round 2 flagged
("Anything the brief did not ask about") is **gone** — round 2 logged `Configuring bundled FFmpeg
HEVC decoder for` 9× (initial + 4 stall-driven view rebuilds) in the first 30 s; round 3 logs it
**once**, zero `Rebuilding projection view` / `Recreating projection view` / `Display stall`, a
steady ~47 fps from engagement onward, zero `UnsatisfiedLinkError`, zero MediaCodec fallback.

## Setup notes

- Scripts used: `hur-wifi-test-scripts/build_hur.sh` (R0 build), `run_unit_tests.sh` (R0 tests),
  `install_and_launch.sh` (`SKIP_BUILD=1`, R2/R3 install). R1's symbol audit and the R3 settings
  edit (single `sed` on the rooted HU, app force-stopped) were run inline. No new scripts added.
- **View mode is GLES, and that is what makes `e4d4bf70` testable here.** `settings.xml` carries
  `view-mode=2`. Round 2's results doc labelled that "TEXTURE" — it is wrong; `Settings.ViewMode`
  is `SURFACE(0) / TEXTURE(1) / GLES(2)`, so `2` = **GLES**. The commit under test patches
  `GlProjectionView`'s direct YUV upload path, which `VideoDecoder.startBundledHevc` only wires up
  when `settings.viewMode == GLES` (otherwise the bundled decoder renders straight to the Surface
  and the sink is never used). The rig's standing GLES config is exactly the case the fix addresses;
  a TEXTURE rig would not have shown round 2's finding and would not exercise this commit.
- Standing config unchanged from round 2: `wifi-connection-mode=3`, `video-codec=H.265`,
  `software-video-decoder=1`, `debug-video-low-latency=true`, `enable-audio-sink=false`,
  `log-level=2` (INFO — all brief §5 lines are `AppLog.i`), `view-mode=2` (GLES),
  `show-fps-counter=true`, `head-unit-make=Google`.
- **The head unit's Bluetooth bond to the POCO X3 held from round 2's manual re-pair** —
  `dumpsys bluetooth_manager` on the HU showed `DC:B7:2E:5E:4E:59 [DUAL] POCO X3 NFC` bonded at the
  start of this round, no re-pair needed. Both R2 and R3 formed a session on the phone's own
  Bluetooth reconnect (~22 s and ~18 s after the radios came back), no poke required.
- Clean-run protocol both live runs: POCO Wi-Fi+Bluetooth off via `svc` (verified `wifi_on=0` /
  `bluetooth_on=0` / BT `state: OFF`), HU app launched, 16 s settle, POCO radios restored (verified
  `=1` / `=1` / `state: ON`).
- **R3 settings written** (rooted HU, app force-stopped, single `sed`): `force-software-decoding`
  `false`→`true`. `software-video-decoder` already `1` and `video-codec` already `H.265`, left as-is
  (brief §4 says write anyway; both were already the target value, so idempotent). `settings.xml`
  restored from a pre-round backup at the end and verified **md5-identical**
  (`475b37a98c2c774d4fa452a5c99b8b48`), `force-software-decoding` back to `false`.
- Discard rule: **clean on both runs.** R2 `createGroup SUCCESS` = 1, single `p2p-wlan0-0`. R3
  `createGroup SUCCESS` = 1, single `p2p-wlan0-1` (index 1 because launching R3 first tore down
  R2's leftover `p2p-wlan0-0` group at 21:15:37 `P2P-GROUP-REMOVED … reason=REQUESTED`, then created
  one fresh group `P2P-GROUP-STARTED p2p-wlan0-1` at 21:15:38 — no churn within either run). One
  `MATCH! Starting AapService` per run, the phone's own BT reconnect, no group churn attached.
- `fed=0` in every R3 throughput window is the software-path counter artifact round 2 already
  documented — the YUV frame sink renders via callback and does not increment `fed`; `rendered` is
  the load-bearing number.
- Captures (in `hur-wifi-test-scripts/round-aap-reorg/`): `r2-round3.txt` (497,597 lines),
  `r3-round3.txt` (204,350 lines). Round 2's `r2b.txt` / `r3-attempt1.txt` are untouched; round 2's
  own `r2.txt` / `r3.txt` were overwritten by this round before being renamed and are not needed
  (round 2's results doc already extracted them).

## R0 — Unit test gate and count parity

**PASS**

- `./gradlew assembleGithubDebug` at `e4d4bf70` — `BUILD SUCCESSFUL`, APK
  `com.andrerinas.headunitrevived_3.3.0-beta2_debug.apk`, md5 `edfc19475083814825a0402ab03a6199`.
- `./gradlew testGithubDebugUnitTest` — green. Aggregated from
  `app/build/test-results/testGithubDebugUnitTest/*.xml`:

  ```
  tests=780  failures=0  errors=0  skipped=0
  ```

- **Executed 780. Expected 780 (`58802778` on-record count, and round 2's `6479a374` count).
  Delta 0.** `e4d4bf70` adds and removes no tests, confirmed.

## R1 — APK native symbol audit

**PASS** — identical to round 2.

Ran on `lib/*` from the R0 APK, all four ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`),
identical symbol sets on each.

**`libusbhelper.so` — 10 symbols:**

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

Decoded `com.andrerinas.openheadunit.connection.usb.UsbNative` — matches `UsbNative.kt`'s 10
`private external fun`s. No pre-fix `connection_UsbNative`.

**`libhur_soft_hevc.so` — 4 symbols:**

```
Java_com_andrerinas_openheadunit_decoder_video_FfmpegHevcDecoder_00024Companion_nativeCreate
Java_com_andrerinas_openheadunit_decoder_video_FfmpegHevcDecoder_00024Companion_nativeDecode
Java_com_andrerinas_openheadunit_decoder_video_FfmpegHevcDecoder_00024Companion_nativeIsAvailable
Java_com_andrerinas_openheadunit_decoder_video_FfmpegHevcDecoder_00024Companion_nativeRelease
```

`_00024` → `$`, so `com.andrerinas.openheadunit.decoder.video.FfmpegHevcDecoder$Companion` —
matches the companion's 4 `private external fun`s. No pre-move `decoder_FfmpegHevcDecoder` /
`aap_FfmpegHevcDecoder`.

**Self-check:** every `.so` in the APK searched for `connection_UsbNative`, `aap_UsbNative`,
`decoder_FfmpegHevcDecoder`, `aap_FfmpegHevcDecoder`, `modes_native` → **0 files each**. Total
distinct `Java_` symbols in the APK: exactly 14 (10 + 4), no others.

## R2 — Native AA baseline smoke

**PASS**

- Settings written: none (standing config). Capture `r2-round3.txt`.
- Decisive log lines:

  ```
  21:11:11.314  NativeAA: ACTIVELY LISTENING on Android Auto UUID (4de17a00-…) on radio [Navegadortz2]
  21:11:12.003  WifiDirectManager: 5GHz createGroup SUCCESS!
  21:11:35.979  NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59)
  21:11:36.453  NativeAA: [TX] Sending WifiStartRequest (Type 1)
  21:11:36.538  NativeAA: [RX] Received Type 2 (Payload size: 0)
  21:11:38.042  WirelessServer: Incoming connection detected from /192.168.49.123
  21:11:38.260  AapSslContext.performHandshake | SSL handshake complete. Session id: Dsu30Mh9…
  21:11:39.202  AapControlMedia.mediaSinkSetupRequest | Media Sink Setup Request: 7 on channel VIDEO
  ```

- Measurements: `createGroup SUCCESS` = 1, `SSL handshake complete` = 1, `Incoming connection
  detected` = 1, single `p2p-wlan0-0`, `UnsatisfiedLinkError` = 0 (full 497,597-line capture,
  `grep -a`). 40 `Throughput over` windows, **rendered > 0 in every one** (rendered 197–249, ~48 fps
  steady, 39 fps in the first partial window only), `dropped` = 0 and `concealed` = 0 in every
  window, 0 `rendered=0` windows, codec `c2.unisoc.hevc.decoder` (hardware HEVC) throughout.
- `Rebuilding projection view` / `Recreating projection view` / `Display stall` = **0** (hardware
  path, as expected — `TextureProjectionView` / OES branch stamps `lastFrameDrawnMs`).
- `Configuring decoder:` = 1 — the normal MediaCodec configure for the hardware HEVC path, not a
  fallback.
- **Projected-widget tap:** `input tap 272 657` on the HU → the POCO's `dumpsys media_session`
  reported `state=PLAYING(3)` for `com.spotify.music`, and a follow-up single tap toggled it to
  `PAUSED(2)`, and another back to `PLAYING(3)` — deterministic, so the play control drawn into the
  projected video round-trips through the touch channel end to end (`TouchCoordinateMapper` /
  `KeyCode` moved in `f824721c`).

Session held ~3 min 23 s (SSL 21:11:38 → capture stop 21:15:01).

## R3 — Bundled FFmpeg HEVC engagement (point of the round, with R1)

**PASS — and the round 2 finding is fixed.**

- Settings written (app stopped): `force-software-decoding=true` (`software-video-decoder=1` and
  `video-codec=H.265` already set). Capture `r3-round3.txt`.
- Decisive log lines:

  ```
  21:15:54.148  NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59)
  21:15:56.134  WirelessServer: Incoming connection detected from /192.168.49.198
  21:15:56.340  AapSslContext.performHandshake | SSL handshake complete. Session id: z9AMOmEQ…
  21:15:56.620  AapProjectionActivity.setupProjectionView | Using GlProjectionView
  21:15:57.279  AapControlMedia.mediaSinkSetupRequest | Media Sink Setup Request: 7 on channel VIDEO
  21:15:57.950  VideoDecoder.applyStreamDimensions | H.265 SPS parsed: 1920x1080 (negotiated 1920x1080)
  21:15:57.953  VideoDecoder.startBundledHevc | Configuring bundled FFmpeg HEVC decoder for 1920x1080
  21:15:57.954  FfmpegHevcDecoder.start | FFmpeg HEVC decoder thread count: 6
  21:15:57.956  VideoDecoder.startBundledHevc | Bundled FFmpeg HEVC decoder initialized
  21:15:57.988  VideoRenderer.uploadDirectYuvFrame | GlProjectionView: first direct YUV420 upload 1920x1080 strides=1920/960/960
  21:15:57.990  VideoDecoder.onSoftwareFramesRendered | First bundled software HEVC frame rendered
  ```

- Against the brief's PASS list:
  - `Media Sink Setup Request: 7 on channel VIDEO` — **present** (7, so the phone is sending H.265;
    not INCONCLUSIVE).
  - `Configuring bundled FFmpeg HEVC decoder for` — **present, exactly 1** (round 2: 9).
  - `FFmpeg HEVC decoder thread count:` — **present** (`thread count: 6`).
  - `Throughput over` rendered > 0 in every steady window — **36 windows, 0 with `rendered=0`**,
    rendered 140→237, fps 27 (first window only) then 40–48 with a ~43/47 fps mode, `dropped` = 0
    and `concealed` = 0 in every window, codec `ffmpeg-hevc` throughout.
  - `UnsatisfiedLinkError` — **0** in the full 204,350-line capture (`grep -a`).
  - `Failed to load hur_soft_hevc` / `FFmpeg HEVC decoder native library is not loaded` /
    `Bundled FFmpeg HEVC decoder is unavailable` — **0** each.
  - `Configuring decoder:` (MediaCodec fallback) — **0**. No silent fallback.
- **The round 2 finding — fixed.** `Rebuilding projection view` / `Recreating projection view due
  to settings change` / `Display stall` / `projectionViewRecreate` = **0** across the whole run
  (round 2: four rebuilds at ~10 s spacing in the first 30 s, each stopping and restarting the
  decoder). The `GlProjectionView: first direct YUV420 upload` line at 21:15:57.988 confirms the
  exact code path `e4d4bf70` patches is the one in use, and its added `markFrameDrawn()` call now
  keeps `lastFrameDrawnMs` current, so `AapProjectionActivity`'s display-stall watchdog no longer
  reads a frozen `0` off a picture rendering at ~47 fps.
- **Projected-widget tap:** `input tap 272 657` toggled Spotify `PLAYING(3)` ↔ `PAUSED(2)`
  deterministically over three taps — touch channel end to end on the software-decode path too.

The `_00024Companion_` JNI symbols in `libhur_soft_hevc.so` resolve and the native HEVC decoder
runs end to end. Session held ~3 min 7 s (SSL 21:15:56 → capture stop 21:19:03).

## Report-back answers (brief §8)

1. **R0 counts and delta:** 780 executed / 0 failed at `e4d4bf70`; expected 780; **delta 0**.
2. **R1 symbol list:** `libusbhelper.so` — 10 × `Java_com_andrerinas_openheadunit_connection_usb_UsbNative_*`.
   `libhur_soft_hevc.so` — 4 × `Java_com_andrerinas_openheadunit_decoder_video_FfmpegHevcDecoder_00024Companion_*`.
   No stale prefix anywhere in the APK. **No mismatching symbol.** Identical to round 2.
3. **R3 verdict, engagement line, fps:** **PASS.** `Configuring bundled FFmpeg HEVC decoder for
   1920x1080` + `FFmpeg HEVC decoder thread count: 6` + `Bundled FFmpeg HEVC decoder initialized`,
   steady **~47 fps rendered** (`rendered` 220–237 per 5 s window after warm-up) on
   `codec=ffmpeg-hevc`, zero `UnsatisfiedLinkError`, zero MediaCodec fallback — and, new this round,
   **zero projection-view rebuilds** (round 2 had four). `e4d4bf70` resolves the finding round 2
   raised.

## Anything the brief did not ask about

- **Round 2's projection-view-rebuild finding is closed by `e4d4bf70`** — see R3. Nothing else on
  the software path recreated the view this round.
- The head unit kept its Bluetooth bond to the POCO from round 2's manual re-pair; no re-pair was
  needed. Worth still checking `Bonded devices:` at the start of any future Native AA round (the
  bond was genuinely lost once).
- Round 2's results doc mislabels `view-mode=2` as TEXTURE; it is GLES. Corrected in Setup notes
  above — it matters because the GLES sink is precisely the path `e4d4bf70` fixes.
