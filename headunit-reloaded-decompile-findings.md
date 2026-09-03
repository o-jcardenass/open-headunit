# Headunit Reloaded decompile — findings

Not a hardware round; PC-only, no brief (an ad-hoc request made directly in the coding session,
not queued from this branch). Target: a competing Android Auto head-unit app called **Headunit
Reloaded**, found installed on the user's own phone.

## Setup notes

- Package `gb.xxy.hr`, versionName `Headunit Reloaded V8.2 .0`, versionCode `820`, minSdk 21,
  targetSdk 36, first-installed `2026-08-18 09:17:47` on the phone it was found on.
- Pulled via `adb pull` from `/data/app/~~.../gb.xxy.hr-.../base.apk` (single base APK, no splits).
  `sha256`: `7ddcb31d76477efa7d35ff66a4cd175e6d7cf15630498aff7e0661c9179e35d2`, 6,656,605 bytes.
- Decompiled with **jadx 1.5.6** (downloaded fresh from the GitHub release, no system package
  available) — `jadx -d src_out --show-bad-code headunit_reloaded.apk`. 4,191 classes, 79 finished
  with errors (normal jadx noise on an R8-obfuscated APK, not a blocker).
- The app is heavily R8/ProGuard-obfuscated: almost everything lives under single/double-letter
  packages (`a`, `a0`, `b`, `z7`, `y7`, ...). Only the app's own top-level package, `gb.xxy.hr`,
  keeps real class names (`TransporterService`, `DispatcherActivity`, `MainActivity`, `Player`,
  `UsbNative`, `proto/*`).
- Full decompiled source tree and the raw/pulled APK were kept in the session's scratchpad only,
  not committed here (multi-thousand-file jadx output, not something anyone needs wholesale). What
  *is* committed: the video-decoder-and-rendering-pipeline files named below, in
  `evidence/headunit-reloaded-decompile/`, since that was asked for explicitly. Everything else
  below is quoted/paraphrased in this file rather than attached in full.
- This repo is AGPLv3 (`LICENSE`). No attribution, credits screen, or license text referencing this
  project, `mikereidis/headunit`, or `andrerinas` was found anywhere in `gb.xxy.hr`'s strings or
  resources (checked every locale's `strings.xml` plus a whole-tree grep).

## 1. Video decoding — not copied, and behind what this project already has

Files: `evidence/headunit-reloaded-decompile/Player.java` (the projection Activity) and the `y7`
package (`y7-b-ScaleResult.java` through `y7-i-VideoDecoderCore.java` — scale/margin math, the
`SurfaceView`/`TextureView` hosts, the touch listener, and the actual `MediaCodec` wrapper).

Their decoder (`y7-i-VideoDecoderCore.java`, class `i`) is a plain `MediaCodec.Callback`-based
implementation:

- **H.264/AVC only.** Hardcoded `MediaFormat.createVideoFormat("video/avc", ...)`. No H.265/HEVC
  path anywhere, and no bundled FFmpeg or any other software decoder — confirmed by listing
  `lib/*/` in the raw APK: only `libandroidx.graphics.path.so`, `libhur.so` (their USB helper, see
  §2), `libunrooted_android.so`, and `libusb1.0.so`. Nothing decoder-related.
- Falls back to `MediaCodec.createByCodecName("OMX.google.h264.decoder")` (an old-style hardcoded
  codec name) when a `h264_accel` preference is off, instead of codec-type auto-detection.
- `max-input-size` is a flat hardcoded `13107200` regardless of resolution — this project's
  `VideoDecoder.kt:1002-1018` computes it dynamically from resolution and API level, which matters
  given the input-buffer-overflow bug fixed for #749.
- Error recovery is `activity.recreate()` on any `MediaCodec.CodecException` (`y7-h-...java`,
  `onError`) — no distinction between transient and permanent failures, versus this project's
  restart counter + stall watchdog + one-time codec-type fallback.
- Buffer indices flow through an `ArrayBlockingQueue(1024)` fed by the callback, consumed by a
  producer thread elsewhere in the app (not captured here, outside the video-decoder scope this
  round covered).
- `y7-c-ScreenCalc.java` (their scale/margin calculator) references the wireless state via
  `p.f13312n`/`p.f13313o` (their `z7.p` — the Bluetooth/wireless-bootstrap class, see §3) to cap
  projected resolution to 720p on a non-5GHz wireless session — a real, sensible constraint, and
  independent of anything in this project.

**Assessment (unchanged from the in-chat discussion this file is capturing):** this part looks
independently written or inherited from the old mikereidis-era codebase, not lifted from the
current source. It is architecturally simpler and strictly less capable than what's already in
`VideoDecoder.kt` (no HEVC, no dynamic buffer sizing, cruder error recovery). The one thing worth a
second look, not a copy: the async `MediaCodec.Callback` API shape (buffer availability delivered
by callback rather than polled `dequeueInputBuffer`/`dequeueOutputBuffer`), and the fact that they
send a generic protobuf `Ack` (message id `32772`) back on the video channel immediately after
`releaseOutputBuffer` per frame (`y7-g-OutputBufferRelease.java`) — this project has `MediaAck` for
the audio/media channel but nothing analogous for video, and no `32772`/`AckIndication` handling
anywhere in the AAP layer. Whether Android Auto actually expects/uses that on the video channel
(versus Gearhead just ignoring it if absent) is unconfirmed from the decompile alone; would need a
real AA traffic capture or the protocol docs before treating it as a gap.

## 2. `UsbNative` — same shape, different implementation (shared lineage, not a direct copy)

Their `gb.xxy.hr.usbhelper.UsbNative` (not committed here — not part of "the video decoder and
related components" this round's request scoped to) matches this project's own
`connection/UsbNative.kt` in class name, package name (`usbhelper`), and the two-step
`System.loadLibrary("usb1.0")` then a second custom lib (`"hur"` on their side, `"usbhelper"` on
ours) load order. Their compiled native lib ships as `lib/*/libhur.so`.

But the actual JNI method signatures differ (`nativeRead(long,int,int)` returning `byte[]` on
theirs vs. filling a `ByteBuffer` on ours), and `libhur.so`'s own log tag is `HUR-NATIVE` with
mostly different error strings from `app/src/main/cpp/usbhelper.c` (`LOG_TAG "UsbNative"`). Reads
as shared ancestry — both projects ultimately descend from `mikereidis/headunit` — rather than a
literal copy of the current C source.

## 3. Native AA wireless handshake — this is where the copying looks real

Not video-decoder scope, so not attached in full here, but recorded because it came out of the
same investigation and materially changes how "independent implementation" should be read for §1
and §2 above. Two separate, specific matches against this project's current (not mikereidis-era)
source:

**a) Fake-HFP AT-command responder.** Their `z7.p` (`e()` method) answers `AT+BRSF` with the
literal string `"+BRSF: 20"` and `AT+CIND=?`/`AT+CIND?` with `"service"` then `"call"` as the first
two fields, in that order, before extending further. This matches
`NativeAaHandshakeManager.kt:handleHfp()` value-for-value — same arbitrary `BRSF` bitmap, same
field order. That handler is a recent, still-unverified addition to this project (per its own
code comment: never observed to fire across 5 rig rounds), not something present in the original
2018-era mikereidis project — so this points at the current source specifically.

**b) Native Hotspot / P2P group creation and BSSID resolution.** Their `z7.j` mirrors
`WifiDirectManager.kt` feature-for-feature: `setGroupOperatingBand(2)` (`GROUP_OWNER_BAND_5GHZ`)
with a catch-and-fallback to plain `createGroup`; the same hardcoded `"192.168.49.1"` GO-IP
assumption; the same four-deep BSSID fallback chain in the same order (`NetworkInterface` scan →
`/sys/class/net/$iface/address` → `ip link show $iface` + a `link/ether` regex → P2P
`requestDeviceInfo`); and the same two masked-MAC literals (`"00:00:00:00:00:00"`,
`"02:00:00:00:00:00"`) rejected at every step. This four-technique chain exists only because of the
masked-BSSID gotcha this project's own CLAUDE.md documents — independently converging on the same
four techniques, in the same order, with the same two magic-constant MAC literals, is not something
clean-room protocol reverse engineering produces.

Log tags throughout their wireless code follow a `HUR-*` pattern (`HUR-WirelessBT`, `HUR-Hotspot`,
`HUR-DummyA2dp`, `HUR-NATIVE`) — plausibly just their own "Headunit Reloaded" abbreviation and not
on its own evidence of anything, but consistent with everything else above.

## Where this leaves it

- **Video decoder (§1):** nothing to flag as copied; nothing worth adopting either, beyond the two
  "worth a second look, not a clear win" notes above (async callback API, missing video-channel
  ack) — neither is a recommended change without further justification.
- **`UsbNative` (§2):** shared lineage via the common mikereidis ancestor, not a live concern.
- **Native AA wireless (§3):** the HFP-responder value match and the P2P/BSSID fallback-chain match
  are both against this project's *current* source, not the shared original. Worth the user's own
  judgment on whether/how to raise it — this file records the technical evidence, not a legal
  conclusion.
