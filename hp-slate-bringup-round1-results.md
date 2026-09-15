# hp-slate-bringup — round 1 results

**Candidate:** main @ 8418ed025       **Baseline:** none (bring-up round, no fix under test)
**APK md5:** 7e52b44befaa24af6ffa323503c1bb91
**Unit:** 1851 tests, 0 failures, 0 errors
**Date:** 2026-09-15

## Setup notes

- Ran directly (devices are adb-attached to the same machine as the planning session on this
  round; not a separate physical rig). `hur-wifi-test-scripts/build_hur.sh` and
  `run_unit_tests.sh` used for R0, `set_hu_settings_runas.py` for all settings writes, matching
  the branch's own conventions.
- **`adb exec-out screencap -p` returns an empty/closed stream on this device.** Use
  `adb shell screencap -p /sdcard/x.png` + `adb pull` instead. Add this to the device's known
  quirks.
- **`adb shell screencap` also does not apply the app's per-app landscape orientation override.**
  It returns the raw 800x1280 physical (portrait) framebuffer even while the app runs rotated
  (`mOverrideDisplayInfo` rotation=1, 1280x736), so a pulled screenshot looks sideways and its
  pixel coordinates do not obviously map to `input tap` targets. This cost one failed blind tap
  attempt (landed on the system nav bar instead of the in-app Settings gear). **The physical
  display itself rotates correctly** — the two operator photos in `hp-slate-bringup-round1-photos/`
  confirm the AA session renders right-side-up on the actual hardware. For any future round needing
  a tap on this device, ask the operator to do it rather than trusting a screencap-derived
  coordinate.
- **`SettingsActivity` is not reachable via `am start -n` from the shell uid on this device**
  (confirmed again this round — `mCurrentFocus` stayed on `MainActivity` after the attempt).
  Reaching it in-app (tap the Settings gear on `MainActivity`) works fine and was used for both R1
  passes.
- **This device's external storage does not use the `/storage/emulated/0/...` path every other rig
  device uses.** `$EXTERNAL_STORAGE` here is `/storage/emulated/legacy`, and
  `context.getExternalFilesDir(null)` resolves under `/storage/sdcard0/Android/data/
  com.andrerinas.headunitrevived/files/`. This is almost certainly the actual answer to "I can't
  export logs" — the export mechanism works correctly (R1), but looking for the result under the
  modern-convention path (as habit from the other three devices) finds nothing. Brief the next round
  on this path explicitly rather than relying on memory.
- **Single USB port, confirmed for real this round.** The operator plugged the POCO into D-HP for
  R2/R3, then unplugged it and reconnected D-HP to the PC afterward — the two are genuinely
  mutually exclusive on this hardware, exactly as the brief assumed. (An adb session appearing live
  mid-write-up was this session catching the state *after* the operator had already reconnected,
  not simultaneous access — worth being precise about since it looked contradictory in the
  transcript.)
- R2/R3 evidence (photos, pulled captures) came from the operator's phone camera (D-MOTO, still
  adb-attached throughout since only D-HP's port was involved in the swap) and from re-pulling
  D-HP's on-device capture segment after the reconnect.
- Settings restored: N/A — this is a bring-up round and the pre-round `settings.xml` was itself the
  standing state from prior probing (see `reference_test_device_serials_and_roles` /
  `project_cnu350bgbj_hp_slate_findings` in the planning session's memory); `log-level`,
  `log-capture-enabled` were the only two keys this round changed and both are left at `0`/`true`,
  a reasonable standing state for this device given the findings below. `show-fps-counter` and
  `view-mode` were already at `true`/`1` before this round touched anything.

## R0 — build + unit-test gate

**PASS**

`assembleGithubDebug` clean. `testGithubDebugUnitTest`: **1851 tests, 0 failures, 0 errors**
(parsed from `app/build/test-results/testGithubDebugUnitTest/*.xml`). APK md5
`7e52b44befaa24af6ffa323503c1bb91`.

## R1 — Export Logs button

**PASS**

Tapped twice, by two different routes, both succeeded:

1. This session navigated `MainActivity` → Settings gear → Advanced → tapped **Export Logs**
   directly (the in-app "Search settings" field was not needed this time — the row was already
   visible under Advanced). Result confirmed by the operator: a dialog titled "Logs Exported" with
   **Share** and **Close** buttons appeared, matching the `saveLogToPublicFile` success path
   exactly (`SettingsFragment.kt:2857-2874`).
2. Later, with the POCO connected over USB (R2/R3's window, no adb available), the operator tapped
   Export Logs again on-device. The pulled capture segment shows `appendBanner`'s raw (non-logcat,
   file-only) banner write landing **3 times** in quick succession
   (`HUR_Log_20260915_143300_723.txt` lines ~18478/19201/19203), meaning the export genuinely
   completed 3 separate times, not one dialog rendered thrice. The operator had to tap Close 3
   times to clear it. Read as a UX gap (no immediate feedback between tap and dialog appearing,
   inviting a repeat tap) rather than a defect — no error, no stale/empty file, no crash.

Both exported files are actually the *live, still-growing* capture segment, reused directly rather
than a separate joined file — expected per source (`LogExporter.kt:333-339`): with a capture
running and no prior roll, `saveLogToPublicFile` appends a banner to the existing `captureFile` and
returns it rather than creating a new one.

`HUR_Log_20260915_142710_600.txt` (the R1 segment, 124818 bytes at first pull) is attached in
`evidence/hp-slate-bringup-round1/`.

**Root cause of "can't export logs":** almost certainly the retrieval path, not the export itself
— see Setup notes' `$EXTERNAL_STORAGE` finding.

## R2 — on-device capture across an untethered USB session

**PASS**

With adb disconnected and the POCO attached via OTG, `log-capture-enabled`'s background pipe kept
writing with no PC involved, confirming it as a usable evidence path on this device independent of
Export Logs or a tethered `adb logcat`. After the operator reconnected D-HP to the PC,
`HUR_Log_20260915_143300_723.txt` (2,526,010 bytes, single unrolled segment, well under the 8 MB
`SEGMENT_BYTES` roll point) was pulled directly from
`/storage/sdcard0/Android/data/com.andrerinas.headunitrevived/files/` and covers the entire window:
session banner at 14:33:00.740 through the last `WirelessServer: port 5288 released` line at
14:37:18.210.

The USB connection itself, as a fact (not part of the verdict): fully formed. AOA switch at
14:34:39.790 (`Switching USB device to accessory mode VID: 18D1 PID: 4E11`), accessory-mode
re-attach with permission at 14:34:40.960, `AapTransport` version handshake at 14:34:41.900, SSL
handshake complete at 14:34:41.990, first frame rendered (hardware decode) at 14:34:46.060. Codec
was H.264 hardware (session banner: `video=codec:H.264 ... swDecoder:BUNDLED_FFMPEG` — H.264 uses
the hardware path; the `swDecoder` field only matters for H.265). Three further `onCreate`/AOA
re-attach cycles happened over the next ~50s (14:35:16, 14:35:31 plus the one already covered),
each re-forming a session cleanly — not investigated further, out of scope for this round, but
worth noting as a repeat pattern if a future round needs a *stable* multi-minute session on this
device.

Evidence: `evidence/hp-slate-bringup-round1/HUR_Log_20260915_143300_723_r2r3.txt`.

## R3 — FPS overlay presence

**FAIL, with root cause identified**

Two operator photos (`hp-slate-bringup-round1-photos/r3_starting.jpg`,
`r3_maps_session.jpg`) taken during the live R2 session — one mid-handshake, one with a fully
formed Google Maps AA session — show no FPS HUD anywhere on screen, including the top-left corner
where it is positioned (`gravity = TOP or START`, `setMargins(20, 20, 0, 0)`,
`AapProjectionActivity.kt:2317-2323`). `show-fps-counter=true` was confirmed in `settings.xml`
throughout.

**This is not a Dalvik/API-17 problem, and not specific to this device's age.** The logcat does
carry a `dalvikvm: Could not find method android.widget.TextView.setElevation` line at
`setupFpsCounter`'s call site, but that is routine Dalvik per-method verification noise — the same
class logs a dozen equivalent lines for every API 21+/24+/26+ call anywhere in
`AapProjectionActivity` (`getDisplay`, `PictureInPictureParams.Builder`,
`AudioManager.OnModeChangedListener`, `UserManager.isUserUnlocked`, none of which are broken
either), and each is correctly guarded by a runtime `Build.VERSION.SDK_INT` check that simply
never executes on this API level. No `FATAL EXCEPTION`, `NoSuchMethodError`, or `VerifyError`
appears anywhere near `setupFpsCounter` or `AapProjectionActivity` in the whole capture.

**The real mechanism, found in source (`AapProjectionActivity.kt`):**

- `onCreate` calls `setupFpsCounter()` at line 963, then `setupProjectionView()` at line 1054 —
  the FPS counter's `container.addView(fpsTextView, params)` runs *before* the video view's
  `container.addView(view)` (line 2291/2301, unindexed in both cases — a plain `FrameLayout`
  append always goes to the top of the z-order).
- For **SURFACE** and **GLES** view-modes, add order does not matter: `ProjectionView` and
  `GlProjectionView` are SurfaceFlinger hole-punch surfaces composited on a separate layer, and
  nothing in the codebase ever calls `setZOrderOnTop`/`setZOrderMediaOverlay` — so that layer
  defaults to *behind* the app's normal window, and the FPS `TextView` (a real child of the normal
  view hierarchy) always draws in front regardless of when it was added.
- For **TEXTURE** view-mode, `TextureProjectionView` is a genuine composited `View` with no
  separate hardware layer, so ordinary FrameLayout z-order applies — and since it is added *after*
  the FPS TextView, it fully covers it.
- D-HP's standing `view-mode` is `1` (TEXTURE). Cross-checked against the rest of this branch:
  every round that actually confirmed the overlay on screen (`ultrawide-touch-alignment` round 5's
  "`Temp: 91-93 C`" reading, the `aap-reorg` rounds) was running **GLES**
  (`view-mode=2`), never TEXTURE. No round on this branch has confirmed the overlay under TEXTURE.

This should reproduce on **any** device running `view-mode=1`, not just this one — it is a genuine
ordering bug, not a device-specific limitation. Not fixed this round per scope discipline (a code
change beyond a log line/build fix belongs to a decision by the planning session); flagging it here
as a concrete, source-verified lead. A minimal fix is either reordering the two calls, or having
`setupFpsCounter()` call `container.addView(fpsTextView, params)` and then `fpsTextView?.bringToFront()`
each time `setupProjectionView()`/`recreateProjectionView()` runs afterward.

## Anything the brief did not ask about

- The optional `view-mode` sweep (0/1/2, if the overlay were absent) was **not** run — the root
  cause was found from a single capture and source reading instead, which answers the same question
  more precisely than a sweep would have (a sweep would have shown *that* TEXTURE hides it, not
  *why*, and would have cost 2-3 more cable-swap cycles on a device with a confirmed reboot-freeze
  risk).
- Battery held at 84% → 86% across the whole round (net gain, likely brief PC-charging time between
  the two cable states outweighing the untethered OTG window) — no charging concern on this device
  for a round of this length.
- No `adb reboot` was used at any point.
