# aap package reorganisation, round 1 brief

Thread: `aap-reorg`. First round of this thread.

## 1. Build and baseline

Candidate: branch `fix/head-unit-server-silence-and-log-attribution` at `7395d21b`.

```bash
git fetch fork fix/head-unit-server-silence-and-log-attribution
git checkout 7395d21b
```

No history rewrite: this thread has no prior round. There is no baseline APK and no second
checkout. The pre-move unit-test count is already on record: the selfmode-playstore-route round 1
R0 ran `testGithubDebugUnitTest` at `58802778`, the commit immediately before the relocation, and
got 780/0. The two commits under test add and remove no tests, so the expected count at `7395d21b`
is exactly 780/0.

## 2. What this is and why it exists

`65212776` relocates 118 files: everything in `aap/` that is not AAP protocol moved to
`decoder/video/`, `decoder/audio/`, `input/`, `connection/*`, `app/` and `utils/`, and every test
moved into its subject's package. The commit is relocation only, verified by review. `7395d21b`
follows it and repairs the ten JNI symbols in `usbhelper.c`, which an earlier upstream move had
left encoding the old `connection.UsbNative` package.

The reason this needs a hardware round at all: JNI symbol names encode the full Kotlin package and
are resolved at first call, not at build or load time. A wrong symbol builds green, installs,
loads, and then throws `UnsatisfiedLinkError` the first time the native function is called. The
same class of break applies to nothing else in the commit, so this round is a symbol audit plus a
behaviour smoke, not an investigation.

## 3. What is different about this round

- This rig has no USB accessory path (template 7a), so the `usbhelper.c` fix cannot be exercised
  live. Its coverage is R1's static symbol audit of the built APK, which proves the binding the
  same way the runtime linker does, by string equality. Say so in the results rather than marking
  anything INCONCLUSIVE.
- R3 has a silent-fallback trap. If the bundled FFmpeg decoder fails to engage, `VideoDecoder`
  falls back to MediaCodec and the picture still plays. A run that only checks for video is a PASS
  that proves nothing. R3's pass condition is the engagement line itself, and its absence with a
  `Configuring decoder:` line present is the FAIL signature.
- This round runs on the UNISOC MT50 Native AA rig, not the Self Mode phones the last three
  threads used. A full AAP session with H.265 needs the real head unit, and the H.265 path is known
  good there (the video-feed-backpressure round ran it at 52 fps).
- R0's expected count is exactly 780, matching the on-record `58802778` run. Any delta means a
  moved test dropped out of discovery.

## 4. Settings keys this round needs

Only R3 changes settings. Back up `settings.xml` first, restore after the round. All other runs use
the rig's standing configuration (`wifi-connection-mode` stays 3).

| Key | Type | Value |
|---|---|---|
| `force-software-decoding` | boolean | `true` |
| `software-video-decoder` | int | `1` |
| `video-codec` | string | `H.265` |

`software-video-decoder` 1 is BUNDLED_FFMPEG and is also the default, so it may already be absent
from the file; write it anyway. `video-codec` must be the literal string `H.265`: on this panel the
`Auto` branch never announces H.265, so without it the phone sends H.264 and R3 never reaches the
code under test.

## 5. The lines that decide every run

Verified with `grep -F` against `7395d21b`.

```
Media Sink Setup Request: %d on channel %s          AapControl.kt:82        (prints as: Media Sink Setup Request: 7 on channel VIDEO)
Configuring bundled FFmpeg HEVC decoder for         VideoDecoder.kt:1229    R3 engagement, the line that matters
FFmpeg HEVC decoder thread count:                   FfmpegHevcDecoder.kt:36 R3 corroboration, native call about to happen
Configuring decoder:                                VideoDecoder.kt:1627    R3 FAIL signature when present without the bundled line
Failed to load hur_soft_hevc                        FfmpegHevcDecoder.kt:84 R3 FAIL, library did not load
FFmpeg HEVC decoder native library is not loaded    FfmpegHevcDecoder.kt:23 R3 FAIL
WirelessServer: Incoming connection detected from   WirelessServer.kt:130   session landmark
SSL handshake complete                              AapSslContext.kt:98     session landmark
Throughput over                                     VideoDecoder.kt:1994    rendered/fed counts
```

The runtime failure this round exists to rule out prints as an uncaught
`java.lang.UnsatisfiedLinkError` naming a `Java_com_andrerinas_openheadunit_` symbol, in logcat
rather than in the app's own log. Grep every capture for `UnsatisfiedLinkError` with `-a`.

## 6. Runs

### R0. Unit test gate and count parity

Build `assembleGithubDebug` at `7395d21b` and run `run_unit_tests.sh`. Record executed and failed
counts. PASS: green with exactly 780 executed, the same count the selfmode-playstore-route round 1
R0 recorded at `58802778`. Any other executed count is a FAIL with the class list from the HTML
report attached, because the relocation adds and removes no tests, so a delta can only be a moved
test dropping out of discovery.

### R1. APK native symbol audit (point of the round, with R3)

Scripted, no device needed. On the APK built in R0:

```bash
unzip -o app.apk 'lib/*' -d apk-libs
for so in apk-libs/lib/*/libusbhelper.so apk-libs/lib/*/libhur_soft_hevc.so; do
  echo "== $so"
  nm -D --defined-only "$so" 2>/dev/null | grep -a 'Java_' || strings -a "$so" | grep -a '^Java_'
done
```

Every `Java_` symbol must decode to a class that exists in the source tree at `7395d21b`
(`_` to `.`, `_00024` to `$`). The two expected sets:

- `libusbhelper.so`: ten symbols, all prefixed
  `Java_com_andrerinas_openheadunit_connection_usb_UsbNative_`
- `libhur_soft_hevc.so`: four symbols, all prefixed
  `Java_com_andrerinas_openheadunit_decoder_video_FfmpegHevcDecoder_00024Companion_`

PASS: every symbol matches its Kotlin class and package, no symbol carries
`connection_UsbNative` (the pre-fix name) or `decoder_FfmpegHevcDecoder` (the pre-move name).
Self-check the audit before trusting it: the pre-fix prefix must come back absent from the new APK
and the script must report that as a detected difference, not as an empty pass. FAIL: any symbol
whose decoded class or package does not exist in the tree.

### R2. Native AA baseline smoke

Standing configuration, no settings changes. Clean-run protocol, one capture. Launch, let the
session form, start music with the projected-widget tap (template 7a, `input tap 272 657` on this
layout), let it run 3 minutes.

PASS, all of:
- session landmarks present, one `createGroup SUCCESS`, one SSL handshake
- `Throughput over` windows show a live picture (rendered above 0 in every steady window)
- the phone's `dumpsys media_session` goes to `PLAYING` after the tap, which also proves the touch
  channel end to end, since the widget is drawn inside the projected video
- zero `UnsatisfiedLinkError` in the full logcat capture

The tap doubles as the input-path check: `KeyCode` and `TouchCoordinateMapper` moved in
`65212776`, and the tap traverses the touch mapper to the phone. No separate input run is needed.

### R3. Bundled FFmpeg HEVC engagement (point of the round, with R1)

Apply the section 4 keys with the app stopped, relaunch, form a session, tap play, run 3 minutes.

PASS, all of:
- `Media Sink Setup Request: 7 on channel VIDEO` (the phone is actually sending H.265; if this
  says 3, the run never reached the code under test and is INCONCLUSIVE, not FAIL)
- `Configuring bundled FFmpeg HEVC decoder for` present
- `FFmpeg HEVC decoder thread count:` present
- `Throughput over` shows rendered above 0 in every steady window after engagement
- zero `UnsatisfiedLinkError` in the full logcat capture

FAIL: `UnsatisfiedLinkError` naming any `FfmpegHevcDecoder` symbol, or `Configuring decoder:`
appearing for the session's video with no bundled-decoder line, or either explicit FFmpeg failure
line from section 5.

Restore the section 4 keys from the backup afterwards.

## 7. Do not re-run

Nothing. This is the thread's first round. The relocation's compile-time correctness is already
proven by review and by R0's build, so do not spend time diffing Kotlin sources on the rig.

## 8. Report back

1. R0's two executed-test counts and their delta (expected 0).
2. R1's symbol list per library, or the one mismatching symbol if any.
3. R3's verdict with the engagement line quoted and the steady-state rendered fps alongside it.
