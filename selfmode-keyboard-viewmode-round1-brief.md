# selfmode-keyboard-viewmode — round 1 brief

**Type: logs only.** This round designs no fix. It hands a follow-up coding session ("the builder")
a hardware capture of two related Self Mode problems so the fix can be designed against measured
behaviour, not a guess.

## The two problems (GitHub issue #882)

**Problem 1 — the keyboard bounce.** In Self Mode, when Android Auto's own on-screen keyboard
(`com.google.android.apps.auto.components.externalkeyboard.phone.PhoneKeyboardActivity`) opens
over the projection, it flashes up and immediately vanishes, leaving the user back on the AA
screen with no keyboard. Users on the issue report that switching the projection backend from
**SurfaceView to TextureView** makes the keyboard stay. Confirmed here.

**Problem 2 — the view-mode switch breaks the picture.** The maintainer's follow-up comment: after
switching SurfaceView→TextureView "somehow broke the decoding process." This round pins that down:
it is the *live* switch through OHU Quick Settings during an active session, and it affects **every**
transition (→TextureView, →GLES, →SurfaceView), not just SurfaceView→TextureView.

## What the code already says (context, not the answer)

- `AapProjectionActivity.onPause()` does nothing to the decoder or the view. What tears the decoder
  down on a cover is the surface callback the OS delivers for the active backend.
- **SurfaceView** (`view/ProjectionView.kt`): a separate window layer. `PhoneKeyboardActivity`
  covering it → SurfaceFlinger destroys the surface → `ProjectionView.surfaceDestroyed()` →
  `videoDecoder.stopIfCurrentSurface(REASON_SURFACE_DESTROYED)` and
  `AapProjectionActivity.onSurfaceDestroyed` → `commManager.send(VideoFocusEvent(gain=false))`.
- **TextureView** (`view/TextureProjectionView.kt`): the `SurfaceTexture` is destroyed only on view
  detach, not on a cover. `onSurfaceTextureDestroyed` does not fire → no decoder stop → no
  `VideoFocusEvent(gain=false)`.
- **GLES** (`view/GlProjectionView.kt`): the decoder surface is an app-owned `SurfaceTexture`
  released only on view detach — same as TextureView for this purpose.
- **Keyboard-raise arm** (`connection/self/SelfModeCoverRaisePolicy.kt`,
  `AapProjectionActivity.maybeOpenRaiseEpisode/tickRaise`, setting `raise-projection-over-keyboard`,
  default off on branch tip `85eca1e4`): opens a raise episode when a cover lands within
  `TOUCH_WINDOW_MS = 3000` of an `ACTION_UP` we forwarded, and reorders the projection back to
  front via `ACTION_RAISE_PROJECTION` → `AapService.launchAapProjectionActivity`
  (`NEW_TASK | REORDER_TO_FRONT`).
- **The live view-mode switch**: OHU Quick Settings `showViewModeDialog()` sets `settings.viewMode`
  and fires the `ACTION_SETTINGS_CHANGED` LocalBroadcast; `AapProjectionActivity.settingsReceiver`
  calls `recreateProjectionView()` → `videoDecoder.stop(REASON_PROJECTION_VIEW_RECREATE)` → new
  view → `setSurface()`.

## Round shape

Candidate: `fix/883-self-mode-call-raise`, tested **as installed** (3.3.0-beta2, APK md5
`6d61776d2cb4afc63a3a1c2080b91c17`, presumed branch tip `85eca1e4`). No baseline. Unit: D-POCO
(Redmi M2007J20CG / POCO X3 NFC, Android 15, Snapdragon 732G, AA 17.5.663204, Self Mode 17.4+
loopback route). Full method deviation: the keyboard trigger is inside the projected video and not
scriptable, so the operator performed the field-focus / phone-keyboard-key taps on cue — see the
results file's Setup notes.

| Run | view-mode | raise-over-keyboard | What it captures |
|---|---|---|---|
| R1 | SURFACE | off | The involuntary trigger + the #882 bounce, unmodified |
| R2 | SURFACE | on | Whether the keyboard-raise arm fixes the bounce |
| R3 | TEXTURE | off | Keyboard behaviour on TextureView + decoder state while covered |
| R4 | SURFACE→live switch | off | Problem 2: live Quick Settings view-mode switch, all three transitions |
| R5 | GLES | off | Keyboard behaviour + fps on GLES (third backend) |

## For the builder

The results file has the measured answer to: which trigger bounces (phone-key vs field-focus),
what the raise arm does and does not fix, why TextureView/GLES keep the keyboard, what the decoder
does on each backend while covered, and exactly what breaks on the live view-mode switch and for
how long. No fix is proposed here — the two problems interact (SurfaceView is the only backend
that bounces the keyboard *and* the only one that does not have the view-switch gray-screen /
covered-decode-waste issue), so the design trade-off is real and is the builder's call.
