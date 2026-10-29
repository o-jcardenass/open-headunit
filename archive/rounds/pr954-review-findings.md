# PR #954 (Sesam17): exit action, flow-control window, floating-button service

**Not a hardware round.** Coding-session review, written the same way as
`ultrawide-touch-and-usb-pr943-findings.md`: an evidence file so nobody has to re-open the diff.
Nothing here has been posted to GitHub. The reply text lives in `pr954-review-reply-draft.md`.

## Where this came from

[PR #954](https://github.com/andreknieriem/open-headunit/pull/954), "more added features and some
fixes", opened 2026-09-09 by **Sesam17**, the reporter of #809 and the author of #943. This is his
first attempt at the workflow our #943 reply asked for: small branches off current `main`, one
subject each. That is worth saying out loud when we answer him.

- Head `Sesam17:main` @ `88f78f4f3490b798640a2268e3674c97909c3d11`, base `andreknieriem:main` @
  `12706e26f`. 3 commits, 14 files, +983/-0 by the API's count.
- Base already contains his merged [#949](https://github.com/andreknieriem/open-headunit/pull/949)
  (Ultrawide screen optimization setting, Floating Launcher Overlay Button), which is where
  `HeadUnitScreenConfig.isUltrawideEnabled()` comes from.
- CI on the head: Build (github debug) PASS, Unit tests (github debug) PASS, `review` workflow FAIL
  with an empty output body (looks like an infrastructure failure, not a finding).
- Compared against `testing/recovery-plus-video-plus-usb` @ `097325df7`, which contains upstream
  `main` plus our `fix/video-fit-and-ultrawide-touch` (PR #948), `fix/usb-aoa-failure-diagnostics`
  and `fix/native-aa-recovery-identity-and-speed`.

| Commit | Subject | Verdict |
|---|---|---|
| `e0e192f` | Configurable AA Exit Action (OEM Launcher / App Home / Disconnect) | good idea, this implementation does not work here |
| `925d55d` | Flow-control window x4 video, x2 audio when Ultrawide is on | reject |
| `88f78f4` | Floating button moved into a `specialUse` foreground service | out of our scope, two notes |

---

## 1. `925d55d`: the AAP flow-control window, x4 video and x2 audio

`AapControlMedia.maxUnackedFor` (`aap/AapControl.kt:120-142`) gains an ultrawide multiplier:

```kotlin
val base = if (aapTransport.isWireless) 12 else 16
return if (HeadUnitScreenConfig.isUltrawideEnabled()) base * 4 else base
...
val baseAudio = if (aapTransport.isWireless) 30 else 16
return if (HeadUnitScreenConfig.isUltrawideEnabled()) baseAudio * 2 else baseAudio
```

So on an ultrawide install: video 12 -> 48 wireless, 16 -> 64 USB; audio 30 -> 60 wireless, 16 -> 32
USB. The value is handed to the phone as `Media.Config.max_unacked` from `mediaSinkSetupRequest`
(`AapControl.kt:92-101`; proto `app/src/main/proto/media.proto:113-124`). The commit message claims
it "prevents phone transport stalls during large 1080p/720p keyframe bursts on 2.4GHz Wi-Fi". No
measurement is attached.

### 1.1 It does not compile on our branch

`9e1ce321b` ("Screen config: an ultra-wide panel announces its pixel shape, not a margin", the second
commit of PR #948) deleted `HeadUnitScreenConfig.isUltrawideEnabled()`, the `Settings.optimizeUltrawide`
property, the `optimize-ultrawide` pref key, its settings entry, its strings and its backup key. The
toggle was replaced by an automatic model: the AUTO ladder picks on the panel's short side and the
width is carried by `pixelAspectRatioE4` (`NegotiatedResolutionPolicy`, `ProjectionGeometryPolicy`,
`SystemOptimizer.panelCeiling`). A repo-wide grep for `isUltrawideEnabled` on our branch returns
nothing.

This is a merge-order problem, not a merge conflict in the textual sense. Whichever of #948 and #954
lands second has to resolve it, and #948 has hardware rounds behind it (ultrawide-touch-alignment
rounds 1-6) while this hunk has none.

### 1.2 We already rejected this exact change once

`ultrawide-touch-and-usb-pr943-findings.md` on this branch recorded the `base * 3` version of the
same hunk under "Global default changes that would regress everyone else... Reject every one",
citing two reasons: it deletes the comment that records where the window came from, and it calls
`isUltrawideEnabled()` on the transport thread where `currentSettings` is `lateinit` and may not be
assigned yet. #954 raises the video multiplier to 4x and adds a new audio 2x on top.

### 1.3 The audio 2x breaks an invariant this repo writes down

`Settings.audioQueueCapacity` (`utils/Settings.kt:967-975`), default 50 chunks, carries this comment:

> The bound has to clear the window we hand the phone, or we drop sound the protocol told it to
> send: AapControl advertises max_unacked 30 for wireless audio, so a burst that size is legal and
> must fit.

The capacity is read once per session (`aap/AapAudio.kt:36`) and handed to the decoder
(`AapAudio.kt:324`). At a 60-message window a burst the protocol authorises is 60 chunks against a
50-chunk queue, so the drop-on-overflow path fires on traffic we told the phone it could send. That
is the dropout and lip-sync class of report (#449, #543, #598) re-opened from the other end. If the
window ever moves up, `audioQueueCapacity`'s default has to move with it, and that is a global
change, not an ultrawide one.

### 1.4 The video 4x dilutes the backpressure mechanism it sits next to

`4e7f3b740` ("Video: pace the transport thread instead of shedding reference frames") deliberately
uses the small window as the throttle:

> When the queue is full, `decode()` now waits for a slot in 50ms slices up to 1s
> (`VideoFeedThrottlePolicy`) instead of shedding: the paced read thread stops acking, the phone's
> 16-message unacked window closes, and the phone slows to the codec's real drain rate.

That was measured on a 1920x720 panel (issue #875), which is the same hardware class this commit
targets. 16 -> 64 on USB quadruples the in-flight video the pacing path has to absorb before the
phone throttles at all. `video-feed-backpressure-round1-brief.md` on this branch states the same
coupling: "the pacing mechanism is transport-agnostic and the window size is not".

The standing comment at `AapControl.kt:131-136` also argues in the other direction from the one the
commit assumes: the window is left wide *for hardware decode*, and it is narrowed to 6/8 for the
software HEVC path (`AapControl.kt:126-130`) precisely because a large wireless window turns into
visible input lag. Multiplying it further is untested in either direction.

### 1.5 The gate is the wrong axis, and it is race-dependent

`isUltrawideEnabled()` as merged in #949 was:

```kotlin
fun isUltrawideEnabled(): Boolean =
    if (this::currentSettings.isInitialized) currentSettings.optimizeUltrawide else false
```

Three problems, in order of importance:

1. It is a **user toggle**, not a panel measurement. Its callers in #949 pair it with an inline
   `screenWidthPx >= 1700` test; this one does not, so the window changes for anyone who ticked the
   box on any panel.
2. `HeadUnitScreenConfig` is a mutable, unsynchronised `object` whose state is set up from the
   activity path, and `maxUnackedFor` runs on the `AapTransport:Handler::Poll` HandlerThread (chain:
   `AapTransport.pollHandlerCallback` -> `AapRead*` -> `AapMessageHandlerType.handle` ->
   `AapControlGateway.execute`). Media sink setup can arrive before `HeadUnitScreenConfig.init()`,
   in which case `isInitialized` is false and the window silently stays at base. The same build then
   behaves differently run to run, which is the worst possible property for a link parameter.
3. Panel aspect has nothing to do with the quantity the constants were chosen against, which is
   messages per keyframe. Under #948's geometry an ultra-wide panel negotiates the *same* buffer as a
   16:9 panel of the same height, so the stated premise does not hold on our branch at all.

### 1.6 There is no evidence on either side, and no test to land it behind

- Six ultrawide rounds on this branch (`ultrawide-touch-alignment-round1..6`) measured geometry and
  touch only. No throughput, stall, dropped-frame or keyframe-burst finding appears in any of them.
- We have never varied this window on hardware. `video-latency-round1-brief.md`: "it was changed and
  then changed back within this branch. It is 12 on wireless, same as `main`.
  `Config response: ... (maxUnacked=12)` is the expected value, not a finding."
- There is no `AapControlMedia` unit test anywhere under `app/src/test/java/`, and the new dependency
  on a global `object` would make one awkward to add.

### 1.7 What is real underneath it

His constraint is genuine and is already in our #809 write-up: his unit only hosts on UNII-3
(channel 149), restricted in Egypt, so his only workable path is 2.4 GHz, which caps him at 720p. If
a wider window really helps a congested 2.4 GHz link, that is a **wireless** question and it deserves
a measurement, not an ultrawide gate. What to ask him for: one verbose capture per value, with
`Config response: ... (maxUnacked=N)` and the `dropped=` counters, on the same drive.

---

## 2. `e0e192f`: configurable Android Auto exit action

Replaces the `VIDEO_FOCUS_NATIVE` branch (`aap/AapControl.kt:49-59`) with a three-way setting
(`OEM_LAUNCHER` = 0 and default, `APP_HOME` = 1, `DISCONNECT` = 2), widens `AapTransport.context`
from `private val` to `val` so the protocol thread can reach a Context, and adds `minimizeToHome` /
`returnToAppHome` to `AapProjectionActivity`'s companion object.

The instinct is right. Today the phone's Exit button always kills the session, and users have asked
for the other behaviours. Four things block this implementation.

### 2.1 `APP_HOME` is an immediate loop

`returnToAppHome` starts `MainActivity`. `MainActivity.onResume` (`main/MainActivity.kt:926-929`) and
`onCreate` (`:140-142`) both run:

```kotlin
if (App.provide(this).commManager.isConnected && !App.isPiPActive && !AapProjectionActivity.isForeground) {
    AppLog.i("MainActivity: Active session detected, bringing projection to front")
    bringProjectionToFront()
}
```

On this path the session is deliberately still connected, and the projection activity has just been
paused, so `isForeground` is false. Both conditions hold, and the user is thrown straight back into
the screen they exited. The branch cannot work as written.

### 2.2 The default flip is against a rule this file states

`utils/Settings.kt:917-919`, on `mediaKeyRouting`:

> ALWAYS is the stored zero value so an unset preference keeps the behaviour every existing install
> has.

`DISCONNECT` is today's behaviour, so it is the one that belongs at value 0. As written, every
existing install silently changes behaviour on upgrade with no migration. `FullscreenMode`
(`Settings.kt:408-419`) is the house pattern when a default genuinely has to move.

### 2.3 `OEM_LAUNCHER` duplicates a feature we already ship, and inherits a slow path

`AapProjectionActivity.moveToBackground()` (`:1519-1524`) is the same HOME intent, and it already
leaves the transport alive:

```kotlin
private fun moveToBackground() {
    val startMain = Intent(Intent.ACTION_MAIN)
    startMain.addCategory(Intent.CATEGORY_HOME)
    startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    startActivity(startMain)
}
```

It is wired to *Move to Background* in `showExitDialog` (`:1401-1455`), alongside Stop Connection,
Picture-in-Picture, Quick Settings and Switch Driver, raised by the back press (`:836-840`) and the
edge gesture (`:1703`). So "minimize to the launcher while staying connected" is not a new capability;
what is new is triggering it from the phone's Exit button.

The latent cost is specific. The Exit tap is a touch we forwarded, and `ACTION_UP` stamps
`lastProjectionTouchMs`. When the launcher then covers us, on the SURFACE backend the framework
destroys the surface, and `VideoFocusReleasePolicy.shouldReleaseOnSurfaceLost` sees
`coverFollowsTouch = true` (`TOUCH_WINDOW_MS = 3_000`), `sessionConnected = true`,
`activityEnding = false`, so it returns false and **withholds the focus release**
(`aap/VideoFocusReleasePolicy.kt:49-59`). The withheld release is the keyboard fix from
`selfmode-keyboard-viewmode`; here it means the phone never re-runs sink setup. The comment at
`AapProjectionActivity.kt:255-266` has the numbers: with the release the picture is back in 42-96 ms,
without it the wait for a decodable picture was 6.3-115.9 s across ten returns. Our shipped 1.9-2.1 s
(round 8, PR #826) comes from the warm-relaunch focus cycle, which is a recovery path, not this one.

On TEXTURE and GLES, `video-black-after-background` round 5 measured that the surface is never torn
down by a cover at all (9 cover events, zero `onSurfaceDestroyed`), so nothing is sent and the
decoder keeps running behind the launcher.

Either way the phone is left in NATIVE mode with `ConnectionState` still `TransportStarted`,
`commManager.isConnected` still true, and the foreground notification still deep-linking into the
projection.

### 2.4 Nothing is ever sent back to the phone

The phone requested NATIVE focus. On both new paths we neither answer nor release; the handler still
ends in a bare `return 0`. `VideoFocusEvent(gain = false, unsolicited = false)` is exactly that
answer and already exists (`aap/protocol/messages/VideoFocusEvent.kt`). Until `onPause` removes them,
the activity's own watchdogs can also work against the user's choice: `maybeRequestVideoFocus`
(`AapProjectionActivity.kt:407-416`) sends an unsolicited PROJECTED gain as soon as frames stop,
which asks the phone to undo the exit it just performed.

### 2.5 Smaller things, all fixable

- **`App.provide(ctx).settings` is redundant and does disk I/O on the media read thread.**
  `AapControlMedia` already reads `aapTransport.settings` (`AapControl.kt:118-125`), a field since
  `AapTransport.kt:72`. Using it removes the need to widen `AapTransport.context` at all. That thread
  reads every inbound video and audio message; `startActivity` on a cold launcher is not free there.
  The house pattern for "tell the activity to do something" is a broadcast, as
  `ACTION_FINISH_ACTIVITIES` is used at `connection/CommManager.kt:886-890`.
- **`startActivity` from a non-activity context ignores `ActivityLaunchPolicy`**
  (`app/ActivityLaunchPolicy.kt`, tested in `app/src/test/.../ActivityLaunchStrategyTest.kt`): on API
  29+ a direct start from the background is silently dropped unless the overlay trampoline is used.
  The HOME-category start is exempt so `OEM_LAUNCHER` survives; `APP_HOME` is the one at risk, and
  its failure mode is that nothing at all happens, which is worse than today's disconnect.
- **`reason` is still ignored.** `media.proto:88-95` defines `UNKNOWN`, `PHONE_SCREEN_OFF` and
  `LAUNCH_NATIVE`. Only the last one is "the user tapped Exit". With this default, a phone screen
  blanking would bounce the head unit to its OEM launcher mid-drive. The new behaviour should be
  gated on `reason == LAUNCH_NATIVE`. `VIDEO_FOCUS_NATIVE_TRANSIENT` remains unhandled, as before.
- **Dead debounce state sits in the block being rewritten**: `lastNativeFocusRequestTime` and
  `nativeFocusRequestCount` (`AapControl.kt:34-35`), `NATIVE_FOCUS_DEBOUNCE_MS` and
  `MAX_NATIVE_FOCUS_RETRIES` (`:218-221`) have zero reads anywhere. Use or delete.
- **Style**: `Settings.ExitAction.fromInt` is non-nullable but is called as `fromInt(which)?.let`;
  the house style for a three-option enum is `SettingItem.SegmentedButtonSettingEntry` plus an
  `InfoBanner` hint (`main/SettingsFragment.kt:2163-2193`), not a single-choice dialog; the backup
  key is registered as `ValueType.INT`, which is right, but wants the one-line "read through
  `fromInt`, total fallback" comment its neighbours carry (`utils/SettingsBackupManager.kt:129-134`);
  the new strings are `translatable="false"`.

### 2.6 The version that would work here

Keep the idea, drop the plumbing:

- `ExitAction` with `DISCONNECT(0)` as the default (today's behaviour), then `BACKGROUND(1)`,
  `EXIT_DIALOG(2)`, optionally `PIP(3)`. Every one of those is already implemented in
  `showExitDialog`.
- `AapControlMedia` reads `aapTransport.settings`, checks `reason == LAUNCH_NATIVE`, and for anything
  other than DISCONNECT sends `VideoFocusEvent(gain = false, unsolicited = false)` and then
  broadcasts a request the projection activity handles on the main thread, so `moveToBackground()`,
  `enterPiP()` and `showExitDialog()` stay the single implementations.
- The decision itself is a pure function of (mode, reason, setting), so it extracts into a policy
  object with unit tests, the way `VideoFocusReleasePolicy` and `ProjectionKeyPolicy` did.
- Anything that leaves the session alive needs a round before it ships: return-to-picture time on
  SURFACE (where the release is withheld) and on TEXTURE (where the surface is not torn down),
  which is the shape of `video-black-after-background` rounds 5-8.

---

## 3. `88f78f4`: floating button as a `specialUse` foreground service

Out of our scope (it is his own #949 feature), two notes if it is looked at upstream:

- The manifest is well formed: `FOREGROUND_SERVICE_SPECIAL_USE`, `android:exported="false"`,
  `foregroundServiceType="specialUse"` and a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property. The
  Android 12+ restriction on starting a foreground service from the background is survivable here
  only because an app holding `SYSTEM_ALERT_WINDOW` is exempt, and this feature requires that
  permission anyway. `specialUse` needs a Play Console justification, and there is a `playstore`
  flavour.
- The commit adds a stray top-level directory `UWfix And Features/` containing a duplicate copy of
  both Kotlin files, a drawable, and a `floating_button_feature.patch` describing changes to
  `App.kt`, `AapService.kt` and `MainActivity.kt` that are already upstream from #949. Spaces in the
  path and none of it is source. It should be dropped from the branch.
- Our branch defaults `enableFloatingButton` to **false** (`9e1ce321b`), because
  `MainActivity.checkOverlayPermission()` otherwise sends a fresh install to the system overlay
  settings screen on first resume for a feature nobody asked for. Any adoption keeps that default.

---

## 4. Merge-order collision with our own PRs (recorded, no action taken)

Not raised with anyone yet; noted here so it is not rediscovered.

- #949 (his) is merged upstream and introduced `Settings.optimizeUltrawide` /
  `HeadUnitScreenConfig.isUltrawideEnabled()`.
- #948 (ours, open) deletes both, replacing the toggle with the short-side AUTO ladder plus
  `pixelAspectRatioE4`. It is hardware-clear through ultrawide-touch-alignment round 6 (`e4be4e0e3`).
- #954 (his, open) adds a second consumer of the accessor #948 deletes, in the protocol layer.

So #948 already has to resolve against #949's merged code; if #954 lands first it has to resolve
against a second call site as well. Nothing else in #954 touches files #948 changes.
