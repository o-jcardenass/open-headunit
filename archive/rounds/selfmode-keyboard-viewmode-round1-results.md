# selfmode-keyboard-viewmode — round 1 results

**Candidate:** `fix/883-self-mode-call-raise` @ tip `85eca1e4` (tested as installed, see Setup notes)   **Baseline:** none
**APK md5:** `6d61776d2cb4afc63a3a1c2080b91c17` (`com.andrerinas.headunitrevived` 3.3.0-beta2, versionCode 101)
**Unit:** D-POCO — Redmi M2007J20CG / POCO X3 NFC, `surya`, Android 15 / SDK 35, Snapdragon 732G (`sm6150`), decoder `c2.qti.avc.decoder`. Android Auto `17.5.663204-release`, Self Mode 17.4+ loopback route (`127.0.0.1:5277`). Negotiated video 1920x1080, H.264, ~60 s GOP, fps-limit 60.
**Date:** 2026-08-28
**Type:** logs only — no fix designed.

## Setup notes

- **Tested as installed**, per direction. The installed APK md5 does not match any recorded build
  on the `883-call-raise` D-POCO notes (`0a329ea6` was `6325d7...`); it is presumed branch tip
  `85eca1e4` because `settings.xml` carried `raise-projection-over-keyboard` written explicitly,
  which only makes sense against that commit's default-off. Not sha-verified. No rebuild.
- **Operator-in-the-loop, by design.** The keyboard triggers (field focus, phone-keyboard-key tap)
  are drawn inside the projected video and are not reachable by `uiautomator` / `input tap` at
  known coordinates, and the live view-mode switch (R4) is a Quick Settings dialog with no
  scriptable path (`ACTION_SETTINGS_CHANGED` is a `LocalBroadcastManager` in-process broadcast).
  The operator performed those taps on cue each run and reported what was on screen; every
  decisive line below is from the device log, timestamps included. This is a deviation from
  "run the whole round unattended" (§0) with no scriptable alternative.
- Settings written only via `hur-wifi-test-scripts/set_pref.sh` (`DEVICE=4f4027e9`), app
  force-stopped. Keys touched across the round: `log-level` 2→0, `auto-start-self-mode` false→true
  (single clean `MainActivity` launch route), `view-mode` per run, `raise-projection-over-keyboard`
  per run. **Restored byte-identical** to the pre-round backup at the end (`diff` clean:
  `view-mode=1`, `log-level=2`, `auto-start-self-mode=false`, `raise-projection-over-keyboard=true`,
  `raise-projection-during-call=true`).
- Gearhead **never force-stopped** (D-POCO's `:5277` dies permanently if it is). Session formed
  clean on the first launch of every run (`SelfMode: AA 17.4+ detected` → `Head unit connected` →
  `SSL handshake complete` → `First frame rendered`, ~3–5 s each). No `LogAccessDialog`.
- Scripts: `set_pref.sh`, `restore_settings.sh` pattern. No new script added — the capture was an
  inline `nohup stdbuf -oL adb -s 4f4027e9 logcat -v time > R<n>.txt &` (same idiom as
  `selfmode_session_probe.sh`, minus its Gearhead force-stop).
- Full captures kept at `hur-wifi-test-scripts/round-882-keyboard-viewmode/R1..R5.txt` (~62 MB
  total) + `settings-backup-D-POCO.xml`. Not committed here.
- **Baseline decoder fps by backend, at rest on this device:** SurfaceView ~52–54, TextureView
  ~49–50 decoded but **~34–36 displayed**, GLES ~50 decoded / ~46–52 displayed. SurfaceView is the
  fastest and the only one where decoded == displayed.

---

## R1 — SURFACE, raise arm off: characterise the trigger + capture the bounce — PASS (signal recorded)

- Settings written: `view-mode=0`, `raise-projection-over-keyboard=false`.
- Two operator cycles.

### Cycle A — field focus only (no phone-key tap)

Operator: "keyboard didn't bounce back." Log: **no `PhoneKeyboardActivity`, no `onPause`.** The
only keyboard is AA's in-projection `TouchInputMethod` window (`CAR.WM ... :TouchInputMethod ...
window detached` at 07:25:58, 07:26:10, 07:26:50 as the operator opened/closed it). The projection
is never covered, the decoder is never touched.

**On AA 17.5 / this device, focusing a text field opens AA's in-projection keyboard and works
fine.** The involuntary `PhoneKeyboardActivity` the #882 reporter describes on AA 17.3 did not
occur from field focus here.

### Cycle B — phone-keyboard-key tap (the bounce)

Operator: keyboard appeared then bounced back, unusable. Decisive lines:

```
07:26:54.414 ActivityTaskManager  START u0 {cmp=.../PhoneKeyboardActivity} LAUNCH_SINGLE_TASK from uid 10193 (BAL_ALLOW_PERMISSION)
07:26:54.419 OPENHU  AapProjectionActivity: onPause
07:26:54.424 OPENHU  AapProjectionActivity: covered 65ms after a touch, but the keyboard raise is turned off
07:26:54.817 ATM     Displayed .../PhoneKeyboardActivity for user 0: +359ms
07:26:55.106 OPENHU  ProjectionView.surfaceDestroyed | holder android.view.SurfaceView$1@2c215dc
07:26:55.192 OPENHU  VideoDecoder.stop | Decoder stopped: surfaceDestroyed
07:26:55.333 OPENHU  SurfaceCallback: onSurfaceDestroyed. Surface: ...@0x3d565ba
07:26:55.334 OPENHU  AapTransport.send | VIDEO Video Focus Notification        (gain=false)
07:26:55.361 GH.PhoneKeyboard  Asked by projected IME to detach
07:26:55.456 OPENHU  AapProjectionActivity: onResume
07:26:55.505 OPENHU  ProjectionView.surfaceCreated
07:26:56.083 GH.KeyboardBinderWrappr  setExternalKeyboardCallback called after being told to detach.
07:26:56.183 OPENHU  VideoDecoder: keyframe reached the codec (8200 bytes)
07:26:56.262 OPENHU  First frame rendered (hardware decode)
```

**Mechanism.** The phone-key tap launches `PhoneKeyboardActivity` full-screen. Our activity pauses.
~690 ms later SurfaceFlinger destroys the SurfaceView surface; we stop the decoder and send
`VideoFocusEvent(gain=false)`. **28 ms after that focus release, Gearhead tears its own keyboard
down** (`Asked by projected IME to detach`), the keyboard task closes, our activity resumes, the
surface is recreated, a keyframe is pulled, picture back. `setExternalKeyboardCallback called after
being told to detach` is Gearhead's keyboard binder getting a late callback after it already gave
up.

- Blackout: `onPause`→`onResume` = **1037 ms**; `onPause`→`First frame rendered` = **1843 ms**.
- Decoder rebuilds: 1. Keyframe requests: 1. `dropped=0` in every throughput window
  (`07:26:42` .. `07:27:06`, steady 50–53 fps); the window spanning the cover shows `skipped=6`.
- Raise arm: would have fired (`covered 65ms after a touch`) — disabled this run.

---

## R2 — SURFACE, raise arm on: does the arm fix the bounce? — PASS (signal recorded): no, it does not

- Settings written: `view-mode=0`, `raise-projection-over-keyboard=true`.
- Two operator cycles. Gearhead relaunched `PhoneKeyboardActivity` **5 times** total (3 in the
  first cluster 07:28:53 / 07:29:03 / 07:29:13, 2 in the second 07:29:42 / 07:29:46).

Representative (first launch):

```
07:28:53.042 ATM     START u0 {cmp=.../PhoneKeyboardActivity} LAUNCH_SINGLE_TASK
07:28:53.046 OPENHU  AapProjectionActivity: onPause
07:28:53.050 OPENHU  covered 318ms after a touch with no call, will raise the projection once
07:28:53.477 OPENHU  tickRaise | keyboard raise waiting - waiting to see whether the cover is opaque
07:28:53.667 OPENHU  ProjectionView.surfaceDestroyed
07:28:53.717 OPENHU  Decoder stopped: surfaceDestroyed
07:28:53.825 GH.PhoneKeyboard  Asked by projected IME to detach
07:28:53.960 OPENHU  tickRaise | raising the projection - covered right after a touch, which is what the phone keyboard does
07:28:53.973 OPENHU  closeRaiseEpisode | keyboard raise finished - the projection is back in front
07:28:53.974 OPENHU  AapProjectionActivity: onResume
07:28:54.136 OPENHU  AapProjectionActivity: onPause          (raise's REORDER_TO_FRONT re-races the keyboard task)
07:28:54.140 OPENHU  covered again within 30000ms of the last keyboard raise, leaving it alone
07:28:54.141 OPENHU  AapProjectionActivity: onResume
07:28:54.663 OPENHU  First frame rendered
```

**The arm does not change the outcome.** The SurfaceView surface is still destroyed as part of
the cover (07:28:53.667), we still send `VideoFocusEvent(gain=false)`, Gearhead still detaches its
keyboard on the focus loss (07:28:53.825) — this happens *before* the raise is even sent
(07:28:53.960). The raise then just wins the same race back to the front. Net: keyboard bounced on
all 5 launches, identical to R1.

- Raise episodes opened: 3 (`will raise the projection once`). Raises actually sent: 2. Suppressed
  by the 30 s quiet window: 4 (`covered again within 30000ms ... leaving it alone`).
- Extra churn from the arm: each raise produces a second `onPause`/`onResume` ~150 ms later
  (07:28:54.136, 07:29:47.097) as `REORDER_TO_FRONT` re-races the keyboard task.
- Blackout `onPause`→`First frame rendered` per launch: **1617 / 1502 / 1553 / 1565 / 1523 ms**.
- Decoder: 10× `Decoder stopped: surfaceDestroyed` (2 per cover), 0 `sync_stall`. Throughput held
  54 fps, `dropped=0` in every window.

---

## R3 — TEXTURE, raise arm off: keyboard behaviour + decoder while covered — PASS (signal recorded)

- Settings written: `view-mode=1`, `raise-projection-over-keyboard=false`.
- Two operator cycles; operator held the keyboard open deliberately.

Operator: "keyboard stays on textureview." Log, cycle 1:

```
07:33:33.089 ATM     START u0 {cmp=.../PhoneKeyboardActivity} LAUNCH_SINGLE_TASK
07:33:33.092 OPENHU  AapProjectionActivity: onPause
07:33:33.098 OPENHU  covered 388ms after a touch, but the keyboard raise is turned off
        ... keyboard stays up for ~29.8 s, operator dismisses ...
07:34:02.868 OPENHU  AapProjectionActivity: onResume
```

- **Zero** `onSurfaceTextureDestroyed`, **zero** `Decoder stopped`, **zero** `sync_stall`, zero
  view rebuild, over the whole ~30 s (cycle 1) and ~8 s (cycle 2) the keyboard was up.
- Decoder throughput never wavered: `rendered=250 (49–50fps), fed=250, dropped=0, concealed=0` in
  every window including those fully inside the cover.
- **But the composited picture froze:** `TextureProjectionView: displayed 99 frames in 31518ms
  (3fps)` across the covered span, then `displayed 188 frames in 12198ms (15fps)` recovering, then
  back to ~35 fps. So while covered the decoder kept decoding ~50 fps into a `SurfaceTexture` that
  hwui was not sampling; those frames were discarded at the BufferQueue. It took **~12 s** after
  dismiss for the displayed rate to climb back to baseline. No black screen, no keyframe needed
  for cycle 1 (cycle 2 pulled one at 07:34:20.751).

**TextureView keeps the keyboard because the surface is never destroyed, so no
`VideoFocusEvent(gain=false)` ever tells Gearhead the projection is gone.** The cost on this
device: ~50 fps of wasted decode while covered and a ~12 s visual recovery. The `sync_stall`
cascade that the `SurfaceTexture` BufferQueue backpressure could in principle trigger **did not
fire** on this Qualcomm / Android 15 decoder in a 30 s window — `c2.qti.avc.decoder` kept
returning output buffers throughout. It may still fire on other chipsets (the issue reporter's,
the UNISOC rig).

---

## R4 — live Quick Settings view-mode switch (SURFACE start): Problem 2 — FAIL reproduced

- Settings written: `view-mode=0` (start), `raise-projection-over-keyboard=false`.
- Operator opened OHU Quick Settings and switched View Mode three times: SurfaceView→TextureView
  (07:36:16), TextureView→GLES (07:36:48), GLES→SurfaceView (07:37:04).
- Operator, all three: **"Video went white/gray-ish ... all recover on its own but at the [next]
  60 s GOP."**

Decisive lines (switch 1):

```
07:36:16.194 OPENHU  AapProjectionActivity.recreateProjectionView | Recreating projection view due to settings change...
07:36:16.210 OPENHU  VideoDecoder.stop | Decoder stopped: projectionViewRecreate
07:36:16.211 OPENHU  ProjectionView.surfaceDestroyed
07:36:16.212 OPENHU  VideoDecoder.stop | Decoder stopped: surfaceDestroyed
07:36:16.212 OPENHU  VideoDecoder.stop | Decoder stopped: onDetachedFromWindow
07:36:16.212 OPENHU  setupProjectionView | Projection backend: viewMode=TEXTURE
07:36:16.225 OPENHU  TextureProjectionView: Surface available: 2400x1080
07:36:16.227 OPENHU  VideoDecoder.setSurface | New surface set: Surface(name=android.graphics.SurfaceTexture@19496c5 ...)
07:36:16.227 OPENHU  VideoDecoder.stop | Decoder stopped: New surface
07:36:16.228 OPENHU  AapTransport.send | VIDEO Video Focus Notification
07:36:16.260 OPENHU  VideoDecoder.start | Configuring decoder: c2.qti.avc.decoder for 1920x1080 ...
07:36:16.367 OPENHU  First frame rendered (hardware decode)          <-- 173 ms after the switch
   ... picture is GRAY: decoder is running on P-frames with no anchor keyframe ...
07:36:59.620 OPENHU  VideoDecoder: keyframe reached the codec (159069 bytes)   <-- first real IDR
```

**Mechanism.** `recreateProjectionView()` tears the view and the decoder down
(`projectionViewRecreate`, then `surfaceDestroyed` + `onDetachedFromWindow` from the discarded
view), builds the new view, `setSurface()`s it, and the decoder reconfigures on the next packet —
reporting `First frame rendered` within ~150–200 ms. But that packet stream is **inter frames with
no keyframe**, so what is on screen is gray/white garbage. Nothing forces a keyframe:
`VideoDecoder.start` is even called **twice** per switch (once from `setSurface`+next packet, once
from `onSurfaceChanged` re-applying dimensions), and neither path pulls an IDR that Gearhead
honours. The projection watchdog does not rescue it either — `lastFrameRenderedMs != 0` because
the gray P-frames "rendered", so `ProjectionWatchdogPolicy.shouldNudgeForFirstFrame` returns false
and no `Requesting Keyframe` fires.

The picture only repairs on the phone's **next natural keyframe**. Measured gaps from switch to
the next `keyframe reached the codec`:

| Switch | at | next keyframe | gray for |
|---|---|---|---|
| SURFACE→TEXTURE | 07:36:16.194 | (operator switched away at 07:36:48 before it recovered) | ≥ 32.4 s |
| TEXTURE→GLES | 07:36:48.579 | 07:36:59.620 | **11.0 s** |
| GLES→SURFACE | 07:37:04.560 | 07:38:11.054 | **66.5 s** (full GOP) |

- 3× `Recreating projection view`, 0 `sync_stall`, 0 `decoderPermanentlyFailed`, no fallback. The
  decoder is healthy the whole time — throughput reads 49–54 fps `dropped=0` — it is just decoding
  against a reference it does not have.
- Affects **every** transition, not only SurfaceView→TextureView.

---

## R5 — GLES, raise arm off: keyboard + fps on the third backend — PASS (signal recorded)

- Settings written: `view-mode=2`, `raise-projection-over-keyboard=false`.
- One operator cycle, keyboard held ~17 s.

Operator: "keyboard stays. Video looks good ... keyboard regains focus too."

```
07:39:20.374 ATM     START u0 {cmp=.../PhoneKeyboardActivity} LAUNCH_SINGLE_TASK
07:39:20.375 OPENHU  AapProjectionActivity: onPause
07:39:20.378 OPENHU  covered 51ms after a touch, but the keyboard raise is turned off
        ... keyboard up ~17.4 s, usable, regains focus ...
07:39:37.771 OPENHU  AapProjectionActivity: onResume
07:39:37.830 OPENHU  GlProjectionView: onSurfaceChanged: 2400x1080
```

- **Zero** decoder stop (only startup `New surface`), **zero** `sync_stall`, zero surface destroy.
- GLES displayed fps: ~46–52 normally → `displayed 106 frames in 18904ms (5fps)` while covered →
  **straight back to 48 fps in the very next window** after dismiss. Recovery is markedly faster
  than TextureView's ~12 s — `preserveEGLContextOnPause = true` and the GL thread resumes
  `updateTexImage` immediately.
- GLES keeps the keyboard for the same reason as TextureView (app-owned `SurfaceTexture`, released
  only on view detach), but with better fps and a clean recovery.

---

## Summary table

| Backend | Field-focus keyboard | Phone-key keyboard | Decoder while covered | Live view-mode switch |
|---|---|---|---|---|
| **SurfaceView** | in-projection kbd, fine | **bounces** (~1.8 s blackout, 1 rebuild) — arm does not fix it | stopped cleanly on `surfaceDestroyed`, keyframe on return | n/a as source; as *target* → gray until next GOP |
| **TextureView** | in-projection kbd, fine | **stays** | keeps decoding ~50 fps into an unsampled surface; displayed→3 fps; ~12 s visual recovery | gray until next GOP (11 s measured) |
| **GLES** | in-projection kbd, fine | **stays**, regains focus | displayed→5 fps; **instant** recovery | gray until next GOP (66.5 s measured) |

## Anything the brief did not ask about

- **The keyboard-raise arm adds a second pause/resume per episode** on SurfaceView (the
  `REORDER_TO_FRONT` re-races the keyboard task). Harmless here but it is extra churn on a path
  that is already bouncing.
- **Gearhead retries the phone keyboard several times** after a bounce (5× in R2 across 2 operator
  cycles, 2× in R1's single cycle-B) — each retry is another full ~1.5 s blackout. A user holding
  the field focused would see the projection strobe.
- **`c2.qti.avc.decoder` tolerated the unsampled-`SurfaceTexture` backpressure** for 30 s with no
  `sync_stall` — the cascade hypothesised from the code did not reproduce on this chipset. Worth
  re-checking on UNISOC / the reporter's device before assuming the `sync_stall` path is dead
  code on this scenario.
- **The `Watchdog: No video received yet. Requesting Keyframe` path only ever fires at session
  start** (`lastFrameRenderedMs == 0`). Once any frame — including a gray P-frame — has rendered,
  every "picture is wrong but frames are flowing" situation (R4's whole premise) is invisible to
  it.
- **TextureView's decoded-vs-displayed gap is permanent, not just during a cover:** at rest it
  decodes ~50 fps but only composites ~35. SurfaceView composites everything it decodes. If the
  fix moves the default backend to TextureView to solve the keyboard, that ~30 % displayed-fps
  loss ships with it on this class of device.
