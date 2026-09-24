# PR #943 / issue #809: ultra-wide touch misalignment, and whether the USB change is safe

**Not a hardware round.** This is a coding-session handoff: an evidence file so whoever does the work
starts from the measured root cause instead of re-deriving it from a 116-file fork dump. A round
brief follows (see "What to run on the rig"); the fault reproduces on the MT50.

## Where this came from

Issue [#809](https://github.com/andreknieriem/open-headunit/issues/809), opened 2026-08-09 by
**Sesam17** (2025 GAC Emzoom, Android 8/9 head unit). Two complaints, tracked together:

1. Native AA would not connect. Resolved down to a radio problem: his unit only hosts on UNII-3
   (channel 149), which is restricted in Egypt, so every phone with an Egyptian SIM refuses to see it.
   His only workable path is **2.4 GHz, which caps him at 720p**.
2. At 720p, *"the touch points have issues, the button is not aligned with the icons, I have to touch
   higher than the icon to execute it"*, and *"Android Auto looks squished, and when I disable stretch
   to fit it zooms in and UI elements are cut out of the screen"*. The existing
   **"Experimental touch alignment fix"** setting *"did not do anything"*.
3. Separately, USB has always shown *"requesting permission"* forever, with the phone listed as
   "not allowed".

On 2026-09-06 he opened [PR #943](https://github.com/andreknieriem/open-headunit/pull/943) with his
whole private fork, saying he had fixed the touch misalignment with AI assistance and asking whether
we wanted it. He is a graphic design studio manager, not a developer, and said so. Andre asked him to
open the PR so it could be looked at.

- PR head: `Sesam17:main` @ `59412f8f28ee478127e6d5d631d62b0a7a5453aa`, base `andreknieriem:main` @
  `702626ffc`. 116 files, +1491/-3674, 17 commits.
- Our comparison branches: `main` @ `702626ffc`, and the parked `feature/video-fit-mode` @
  `3cde148ce` (on `fork`, 3 commits, 2026-08-01).

**Scope of this document:** the touch/resolution/scaling claim, and whether the USB permission change
is harmful. Everything else in the PR is his fork's branding and a stale-base replay; it is inventoried
at the end so nobody has to open the diff again.

---

## 1. The root cause is ours, and it is one sentence

**The view-level scale applied by `setVideoScale()` is never part of the touch coordinate transform.**

His own log (`HUR_Log_20260904_183908_621.txt`, attached to #809, OHU 3.3.1) carries the decisive
lines:

```
[UI_DEBUG] HeadUnitScreenConfig: Honest Init | Mode: IMMERSIVE | Anchor: 1920x720
[UI_DEBUG] HeadUnitScreenConfig: Honest Init | Mode: NONE      | Anchor: 1780x720
[UI_DEBUG] Normal Scale. scaleX: 1.0,        scaleY: 1.5
[UI_DEBUG] Normal Scale. scaleX: 1.0786517,  scaleY: 1.5
```

His panel is **1920x720**, aspect 2.667. The chain, at `_1280x720` negotiated:

1. `HeadUnitScreenConfig.getScaleY()` (`utils/HeadUnitScreenConfig.kt:453-476`) falls through both of
   its guards. Negotiated height 720 is **not** greater than panel height 720, and `isPortraitScaled`
   is false. It reaches the final line and returns
   `divideOrOne(screenWidthPx/screenHeightPx, getAspectRatio())` = `(1920/720)/(1280/720)` = **1.5**.
   `getScaleX()` returns `1.0`, or `1920/1780 = 1.0786517` when the sidebar is showing.
2. `ProjectionViewScaler.updateScale()` (`view/ProjectionViewScaler.kt:88-121`) lays the view out
   `MATCH_PARENT`, sets `translationX/Y = 0f`, and calls `setVideoScale(1.0f, 1.5f)`. Both backends
   scale about the **view centre**: `TextureProjectionView.kt:61-66` sets `View.scaleX/scaleY`,
   `GlProjectionView.kt:64-65, :668-670` applies `Matrix.scaleM` to an identity MVP and clips at the
   viewport edge. The picture is blown up 1.5x vertically and the overflow is thrown away. That is
   exactly his *"zooms in and UI elements are cut out"*.
3. `AapProjectionActivity.kt:1890-1935` then feeds `TouchCoordinateMapper.map()` the **unscaled** view
   size (`getUsableWidth()/getUsableHeight()`, or the measured overlay) plus the margins. Nothing
   anywhere tells the mapper that the rendered frame was scaled by 1.5.

On a 16:9 panel `getScaleY()` returns 1.0, so this has been invisible for years. On any panel wider
than 16:9 it is a real multiplicative error plus a centre-crop offset.

It also explains why **"Experimental touch alignment fix"** did nothing for him. That setting
(`use_measured_touch_surface`, `Settings.kt:103`, string at `res/values/strings.xml:497`) only chooses
between the measured overlay size and `HeadUnitScreenConfig`'s usable size
(`AapProjectionActivity.kt:1890-1903`). On his unit both are 1920x720, and neither branch knows about
the video scale either. It was never going to help.

### The MT50 reproduces it. This is not an Emzoom special case.

Our rig is **1440x720**, aspect 2.0 (recorded in a dozen rounds' results files on this branch). Same
arithmetic at 720p:

| | Emzoom 1920x720 | **MT50 1440x720** |
|---|---|---|
| panel aspect | 2.667 | 2.0 |
| `getScaleY()` at `_1280x720` | `2.667 / 1.778` = **1.5** | `2.0 / 1.778` = **1.125** |
| `getScaleX()` at `_1280x720` | 1.0 (1280 < 1920) | 1.0 (1280 < 1440) |
| auto-derived `parE4` (see §3) | 15000 | **11250** |

The parked `feature/video-fit-mode` branch carries a comment naming *"720p on a 1440x720 panel"* as
the failing case. That is our rig. The branch was almost certainly written after seeing this on our
own hardware and then parked before it landed.

### What is NOT broken, and why that matters

At `_1920x1080` the existing margin mechanism already produces the correct canvas on both panels:

```
[ServiceDiscovery] NegotiatedResolution is: 1920x1080
[ServiceDiscovery] Margins are: 0x360          <- his unit, 1920x720 usable
```

MT50 sends `480x360` for the same reason. The margin is the right lever and it works.

The breakage is specific to the case where the **negotiated buffer is narrower than the panel**.
Margins can only shrink a canvas inside a bigger buffer; they cannot widen one. At 1280x720 on a
1920-wide panel there is no margin to send, so nothing corrects the shape, and `getScaleY()`'s
fall-through then over-scales on top of that. That corner is only reachable when something forces
720p, which for him is 2.4 GHz. It is why this took a year to surface.

---

## 2. `feature/video-fit-mode` already contains the correct fix

`fork/feature/video-fit-mode` @ `3cde148ce`, branched from `ce5d566b1` ("releasing 3.2.0",
2026-08-01). `main` is 365 commits ahead and `TouchCoordinateMapper` has since moved from `aap/` to
`input/`, but git follows the rename:

```
$ git merge-tree --write-tree --name-only main feature/video-fit-mode
CONFLICT (content): app/src/main/java/com/andrerinas/openheadunit/main/SettingsFragment.kt
```

**One conflict, and a trivial one.** Main changed `isChecked = pendingStretchToFill!!` to
`isChecked = pendingStretchToFill ?: settings.stretchToFill` (`SettingsFragment.kt:1824`) inside a
block the branch deletes wholesale. Main's 133-line delta to `HeadUnitScreenConfig` since the
merge-base is orthogonal (orientation inference, cached-dims normalization, `panelCeiling` for
#650/#767, `NarrowBandProfilePolicy` link ceiling) and sits in regions the branch does not touch.

What it does:

- Replaces the `stretchToFill` boolean with `Settings.VideoFitMode` (**FILL / CONTAIN / COVER**, CSS
  `object-fit` vocabulary), key `video-fit-mode`, default FILL. The migration reads the legacy boolean
  **and** `forced_scale` + `view-mode`, because the legacy SurfaceView path used the boolean inverted:

  ```kotlin
  val legacyForcedScaleActive = prefs.getBoolean("forced_scale", false) &&
                                prefs.getInt("view-mode", 1) == ViewMode.SURFACE.value
  val effectiveStretch = if (legacyForcedScaleActive) !legacyStretch else legacyStretch
  ```

- Adds uniform `containScaleFactor()` / `coverScaleFactor()` and makes `getScaleX()`/`getScaleY()`
  return a uniform per-mode factor instead of two independently-computed axes. **Its `getScaleY()`
  fall-through returns `1.0f` in FILL**, with the comment quoted above. That single change is the fix
  for §1.
- Threads `fitMode` into `TouchCoordinateMapper.map()` in place of the boolean, with COVER inverting
  the letterbox branch selection, and adds 2 `TouchCoordinateMapperTest` cases (both checked by hand,
  both correct).
- Collapses the three separate copies of the `forcedScale` "inverted `stretchToFill`" hack
  (`ProjectionViewScaler.kt:30-32`, `ProjectionView.kt:76`, `AapProjectionActivity.kt:1910-1917`) and
  deletes the hand-inlined duplicate of the touch math at `AapProjectionActivity.kt:1939-1974`.

That last point is worth stating plainly: **main has two touch implementations**, and they differ
(`.toInt()` + `coerceIn` inline vs `.roundToInt()` in the mapper). The mapper is only reached when
`useMeasuredTouchSurface` is on **and** the mode is IMMERSIVE. So today `TouchCoordinateMapperTest`
tests a path most users never execute.

The branch deliberately does not touch resolution negotiation, DPI, `pixelAspectRatioE4`, the
advertised margins, `ServiceDiscoveryResponse`, or the GL/Texture backends. It treats the mismatch as
a local presentation problem only.

### Gaps neither the branch nor main closes

- **Centre-pivot vs edge-margin.** `getTopMargin()/getBottomMargin()/getLeftMargin()/getRightMargin()`
  (`HeadUnitScreenConfig.kt:497-500`) are computed and read by **no renderer at all**. Every backend
  scales about the view centre, while the margin AA honours is an edge crop. Whenever `scaleY != 1.0`
  with a non-zero `marginHeight`, the visible UI sits about `margin/2` from where the touch math
  assumes it is. The branch makes `scaleY != 1.0` rarer; it does not fix the disagreement.
- **`TouchConfig` advertises the full negotiated size** (`ServiceDiscoveryResponse.kt:137-138`) while
  the mapper emits coordinates in the margin-reduced UI space and clamps with
  `coerceIn(0, negotiatedWidth/Height)`. Nothing reconciles the two coordinate spaces.
- **No non-16:9 codec resolution exists.** The AAP proto enum has five fixed 16:9/9:16 values, so
  margins and pixel aspect ratio are the only levers for a wide panel. The AUTO buckets
  (`HeadUnitScreenConfig.kt:313-328`) choose on min/max pixel thresholds and never on aspect ratio.
  `SystemOptimizer.kt:113` computes `isWidescreen = aspectRatio > 1.7f` and nothing in the scaling
  path consumes it.
- **No unit tests** exist for `HeadUnitScreenConfig`, `ProjectionViewScaler` or
  `SystemOptimizer.panelCeiling`.

---

## 3. What PR #943 actually changed, and the two things worth taking

### The headline claim is not in the touch code

Read the diff before believing the description. "Optimized the code to scale the touch layer":

- **`input/TouchCoordinateMapper.kt` is +4 / -0 and all four lines are a `//` comment.** The math is
  byte-identical to upstream. All three existing tests pass because nothing executable changed.
- **`aap/AapProjectionActivity.kt`'s touch block (`:1885-1978`) is not in the diff at all.** Its
  +33/-63 is a revert of the API-31 `OnModeChangedListener` call-raise work plus two new
  `minimizeToHome`/`returnToAppHome` helpers for his exit-button feature. Nothing in the stall
  watchdog, `maybeRecoverFromDisplayStall()`, the per-backend `onSurfaceChanged` recovery or
  `WarmRelaunchKeyframePolicy` is touched. The Self-Mode call-raise loss is real but is stale-base
  replay, not scaling collateral.
- **`view/ProjectionViewScaler.kt`'s +58** is a new `touchView: View? = null` parameter with three
  `touchView?.let { }` blocks, and the rest is trailing-whitespace stripping. **All ten call sites
  still pass three arguments**, so the blocks never execute. Wired, they would be wrong:
  `MotionEvent.getX()` on a view carrying a `View.scaleX` returns *unscaled* local coordinates, so
  scaling the overlay moves its hit-test region without moving the coordinate space. It would
  introduce misalignment, not remove it.
- **`ServiceDiscoveryResponse.kt` +2/-14 is entirely audio** (reverts `AudioSinkAnnouncementPolicy` to
  a plain `if (settings.enableAudioSink)`). `VideoConfiguration` and `TouchConfig` are untouched.

All the real change is in `HeadUnitScreenConfig.kt`, behind a new `isUltrawideEnabled()` gate (an
`optimizeUltrawide` pref defaulting to a `BuildConfig.OPTIMIZE_ULTRAWIDE` flavour constant), on the
magic numbers **1700 / 1280 / 720 / 1920**:

| Change | Verdict |
|---|---|
| AUTO: ultrawide and width >= 1700 forces `_1280x720` + `stretchToFill = true` | reject. Fires on every 1920x1080 unit too |
| `screenHeightPx = 720; scaleFactor = 1.0f` | reject. `720` is his panel, and the `scaleFactor` half is **dead**, overwritten at `:370` |
| `getScaleX()`/`getScaleY()` return `1.0f` | reject. Sits *before* the `forcedScale` check and short-circuits the whole scaler |
| `getWidthMargin()`/`getHeightMargin()` return `0` at width >= 1920 | reject. A third threshold, and a no-op in his own case |
| `getPixelAspectRatioE4()` returns `(screenWidthPx / 1280f) * 10000` | **the one real idea**, see below |
| `computeSettingsHash` drops physical dims when ultrawide | reject. Deliberately breaks rotation/unfold cache invalidation |
| `protoForResolution` fallback `_800x480` to `_1280x720` | symptom-patch for the lock bug below |
| Restructure `if (!isResolutionLocked) { if AUTO … else if (selected != null) … }` | **take, as an independent bug fix** |

Two structural problems with the gate as written, worth knowing before anyone is tempted:

- Three different thresholds for one condition (`>=1700 || realW>=1700`, `>=1700` alone, `>=1920`).
  There is a reachable state, large insets, where 720p and stretch are forced but PAR stays 10000: a
  horizontally squashed picture with no correction at all.
- The ultrawide branch writes the **private** `stretchToFill` field inside `recalculate()`, while
  `init()` unconditionally reassigns it from settings at `:57` before its early return, and the touch
  path reads `settings.stretchToFill`. Video and touch can therefore disagree about whether the frame
  is stretched.

### Take 1: the locked-AUTO renegotiation bug (nothing to do with ultra-wide)

Upstream `HeadUnitScreenConfig.kt:308-321` reads:

```kotlin
if (!isResolutionLocked && selectedResolution == Settings.Resolution.AUTO) {
    ... AUTO buckets ...
} else {
    negotiatedResolutionType = protoForResolution(selectedResolution ?: _800x480, isPortraitDisplay)
}
```

When the resolution is **locked and the user is on AUTO**, control falls into the `else` branch and
renegotiates via `protoForResolution(AUTO)`. `Settings.Resolution.AUTO.codec` is `null`
(`Settings.kt:1218`), so it takes the `?:` fallback and lands on **`_800x480`**. A locked AUTO session
silently downgrades itself to 480p.

The PR's restructure fixes it:

```kotlin
if (!isResolutionLocked) {
    if (selectedResolution == AUTO) { ... } else if (selectedResolution != null) { ... }
}
```

Small, unit-testable, no rig needed. Land it as its own commit with credit to Sesam17.

### Take 2: auto-derived `pixelAspectRatioE4`

This is the genuinely new idea in the PR, and it is the correct AAP-native answer to a non-16:9 panel.
Telling the phone the pixels are non-square makes Android Auto **lay its UI out** for the real panel
shape while still encoding a 1280x720 buffer, instead of authoring 16:9 and being stretched to fit.

The field is already sent (`ServiceDiscoveryResponse.kt:123`) and already settable, but only as a
**manual** number defaulting to `10000` (`HeadUnitScreenConfig.kt:485-491`). Nobody sets it.

His version hardcodes the 1280 buffer and an implicit 720 height, and because the ultrawide branch is
checked first it **silently overrides a user's manual PAR**. The general form needs no threshold and
no flag:

```kotlin
parE4 = ((screenWidthPx.toFloat() * getNegotiatedHeight()) /
         (screenHeightPx.toFloat() * getNegotiatedWidth()) * 10000f).roundToInt()
```

Apply it only when the user has not set a manual PAR, and only when the result deviates from 10000 by
more than a few percent. Values: MT50 1440x720 at 720p gives **11250**; Emzoom 1780x720 gives
**13906** and 1920x720 gives **15000**; any 16:9 panel gives 10000 and nothing changes.

Optional companion, one derived clause in the AUTO landscape branch: when the usable aspect ratio
exceeds about 2.0, pick the resolution whose *height* matches the panel rather than whose width does.
For 1920x720 that yields 1280x720 naturally, with no literal `720` anywhere.

### The likely real reason his build "worked"

`app/build.gradle.kts` drops **`targetSdk` 36 to 28** (and `compileSdk` 36 to 34). targetSdk 28 opts
the app out of Android 15 forced edge-to-edge and out of modern cutout/inset semantics, which changes
what `init()` measures as the anchor. That is plausibly how his geometry got "fixed", and it is not
adoptable: it is below Play's minimum target API level, and it is also the load-bearing enabler for
his USB change (§4).

### Global default changes that would regress everyone else

All unconditional and flavour-independent, all in `Settings.kt`. Reject every one:

- **`dpiPixelDensity` 0 (Auto) to 200** (`:307`). Flows straight into `setDensity()`. Upstream derives
  it from the panel-capped diagonal (`SystemOptimizer.kt:60-69`). This is wrong-sized AA UI on every
  unit that is not his.
- `viewMode` TEXTURE to SURFACE (`:281`). SurfaceView reports no drawn frames, so this quietly
  disables the stall watchdog (`SystemOptimizer.kt:77-84`).
- `fullscreenMode` IMMERSIVE to NONE (`:408`). Changes the anchor for everyone, and disables the
  `useMeasuredTouchSurface` gate, so on a fresh install of his build `TouchCoordinateMapper` is never
  called at all and only the inline duplicate runs.
- `fpsLimit` 60 to 30; `enableAudioSink` true to false; `useGpsForNavigation` true to false;
  `wifiDirectBand` and `hotspotBand` to FORCE_2_4GHZ; `uiScale*` 100 to 125 (which `HomeUiHelper.kt:19`
  then discards as outside its 60..120 range, so it is a no-op even for him).
- `AapControl.maxUnackedFor` becomes `base * 3` when ultrawide, deleting the comment that recorded
  where the window came from, and it calls `isUltrawideEnabled()` on the transport thread where
  `currentSettings` is `lateinit` and may not be assigned yet.
- The onboarding rewrite deletes `updateDpiOverPanelWarning()` and the
  `SystemOptimizer.recommendedResolution` "resolution above your panel" warning. That is the issue
  #767 guardrail against stranding upgraders on a black screen.

---

## 4. Is the USB change harmful? Yes, but not where the commit message says

Commit `9e1b252c2`, *"Fix USB permission dialog suppression by bringing UI to foreground before
requestPermission"*. It is two edits in `connection/usb/UsbLauncherManager.kt`, and the commit message
only describes one of them.

### (a) `service.startActivity(MainActivity)` before `requestPermission()`

```kotlin
val launchIntent = Intent(service, MainActivity::class.java).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
service.startActivity(launchIntent)
```

**Give him credit for what this is not.** It does not bypass or auto-grant the consent prompt, it
still calls `UsbManager.requestPermission()`, and it does not use accessibility, reflection or
signature APIs to click the dialog. This is "ask at a different moment", not "defeat the dialog". That
distinction matters and should be said out loud when replying to him.

But it should not be merged:

- **It is a silent no-op at `targetSdk = 36`.** `startActivity` from a plain foreground Service is
  blocked on Android 12+ for apps targeting S or higher. It works in his build only because he dropped
  targetSdk to 28. The fix is inseparable from that regression.
- **It tears down live projection.** `MainActivity` is `singleTask` and shares the default task
  affinity with `AapProjectionActivity` (`AndroidManifest.xml:100-107`, `:127-133`), so raising it
  brings that task forward and clears everything above it. `requestPermission()` is reachable
  mid-session: `onUsbAccessoryDetach()` disconnects and re-checks 1500 ms later
  (`UsbLauncherListener.kt:79-87`) into `checkAlreadyConnected` and then `requestPermission`. Upstream's
  comment there calls out the transient "100% battery" accessory-detach case, which is exactly when
  this would blank the screen.
- **It can loop.** No debounce, no `hasRequested` flag, three call sites. Raising `MainActivity`
  recreates `HomeFragment`, whose auto-connect fires `ACTION_CHECK_USB`
  (`HomeFragment.kt:186`, `:312-324`) into `checkAlreadyConnected(force=true)` and back into
  `requestPermission()`. The only brake is a fragment-instance flag that resets whenever the ROM
  destroys the activity, on exactly the ROMs this is aimed at.
- **Upstream already does this, correctly.** `AapService.launchMainActivityIfNeeded()`
  (`AapService.kt:2648`) is settings-gated on `autoStartOnUsb && reopenOnReconnection` and delegates to
  `launchMainActivityOnBoot()` (`:2656`), which is background-activity-launch aware: direct
  `startActivity` below API 29, otherwise an overlay-window trampoline when `SYSTEM_ALERT_WINDOW` is
  granted, otherwise a full-screen-intent notification. `UsbLauncherListener.onUsbAttach()` already
  calls it (`:36`, `:52`). The PR does not touch that file, so his fork now has two competing
  UI-raise paths.

**The underlying diagnosis is not worthless.** On MTK/FYT/Allwinner ROMs the permission dialog is a
normal activity in the current task stack and routinely renders behind the OEM launcher or behind
`AapProjectionActivity`, or is killed by the ROM's task management, so the user never sees it and the
app waits on a callback that never arrives. That matches #809's symptom. If we act on it, the shape is:
call the **existing** BAL-aware ladder from the permission path, behind a per-device debounce, and
explicitly skip it while `CommManager.isConnected` or while `AapProjectionActivity` is foreground.
None of his code needs to be copied to do that.

### (b) The undisclosed one-word change: `RECEIVER_NOT_EXPORTED` to `RECEIVER_EXPORTED`

`UsbLauncherManager.kt:53`. **This is a real vulnerability and must not be merged under any
circumstances.**

The receiver's filter (`UsbReceiver.createFilter()`) includes the app-private action
`com.andrerinas.openheadunit.ACTION_USB_DEVICE_PERMISSION` (`UsbReceiver.kt:60`), which is **not** a
protected broadcast. Exported, any installed app can send it with `EXTRA_PERMISSION_GRANTED=true` and
an arbitrary `EXTRA_DEVICE` (USB devices are enumerable without any permission, so a real `UsbDevice`
can be re-parcelled). It lands in `UsbLauncherListener.onUsbPermission(granted = true, …)`
(`UsbLauncherListener.kt:89`), which fires `connectWithRetry()` or
`UsbAccessoryMode.connectAndSwitch()` on an attacker-chosen device. That is a spoofable permission
grant and a trivial denial of service against the projection session from any unprivileged app on the
head unit.

It is also **completely unnecessary**. The permission broadcast arrives via
`PendingIntent.getBroadcast(context, …)` (`UsbReceiver.kt:64-73`), sent under the app's own UID, so
`NOT_EXPORTED` delivers it fine, which is why upstream works today. The attach/detach actions are
protected system broadcasts and reach `NOT_EXPORTED` receivers regardless.

Worth telling him to revert in his own build, independently of the PR.

### (c) The `USB_ACCESSORY_ATTACHED` manifest addition

Cargo-cult and wrong twice over. `USB_ACCESSORY_ATTACHED` fires when *this* device is the USB
peripheral attached to a host, the opposite of a head unit acting as host for a phone. And its
meta-data resource must contain `<usb-accessory>` elements, while
`app/src/main/res/xml/usb_device_filter.xml` has only `<usb-device>` entries, so the filter matches
nothing.

### (d) The accessibility service: reject outright

`res/xml/carbitlink_accessibility_service.xml` (new) + `service/AppRedirectService.kt` (new, 171
lines) + a new `KILL_BACKGROUND_PROCESSES` permission.

The XML declares `accessibilityEventTypes="typeWindowStateChanged"` with **no `packageNames` filter**,
so it receives a foreground-app stream for **every app on the device**, and
`canRequestFilterKeyEvents="true"` so it can consume key events before anyone else. To his credit
`canRetrieveWindowContent="false"`, so it cannot read screen text, and it does **not** touch the USB
dialog. What the code does:

- Hardcodes two package hit-lists: CarBit/EasyConnect launchers (`net.easyconn`, `com.gpl.carbit`,
  `com.syu.carbit`, …) and the **OEM Bluetooth dialer apps** (`com.yftech.btphone`, `com.syu.bt`,
  `com.fyt.bt`, `com.microntek.bluetooth`, `com.autochips.bluetooth`, …).
- `killAndForceStopApp()` calls `ActivityManager.killBackgroundProcesses()`, then via the app's
  root/Shizuku executor runs `appops set <pkg> SYSTEM_ALERT_WINDOW ignore`, then `input keyevent 4`,
  then `am force-stop <pkg>`. **The appops call permanently revokes another app's overlay permission
  at system level, and there is no undo path anywhere in the PR.**
- Intercepts `KEYCODE_HOME` / `KEYCODE_MENU` and consumes a double tap within 450 ms, so the user's
  own Home press stops working.
- Reads credential-protected prefs, so it is Direct-Boot unsafe (an accessibility service can be bound
  before user unlock).

Accessibility APIs used for app redirection rather than for accessibility are a Play Policy violation
that gets listings pulled, and the appops/force-stop behaviour is user-hostile system tampering.
**Not adoptable in any form.** The same "fight the OEM dialer" goal reappears in
`connection/carkey/fyt/CarFYTReceiver.kt` as five speculative **`persist.*`** `setprop` writes that
survive reboot and are never restored. Also reject.

`main/FloatingButtonManager.kt` (new, 177 lines) is a different matter and conceptually fine: a
`TYPE_APPLICATION_OVERLAY` shortcut, shown only when the app is backgrounded, gated on
`Settings.canDrawOverlays` with a real permission-request path. If we ever want it, it needs a rewrite:
default **off** (his stored default is `true` while the toggle displays `false`), and without the
`BootCompleteReceiver` branch that starts `AapService` at boot purely to draw an overlay.

---

## 5. Everything else in the PR, so nobody has to open it again

**One genuine isolated fix worth cherry-picking on its own:**
`connection/projection/SocketProjectionConnection.kt`, `socket.inetAddress.hostAddress` becomes
`socket.inetAddress?.hostAddress`. A real NPE, one character.

**Security and hygiene, reject:**

- Hardcoded `storePassword = "737266"` / `keyPassword = "737266"` in `build.gradle.kts`, with every
  keystore fallback deleted (the `headunit-release-key.jks` discovery block, the `secrets.properties`
  path, the env-var path), so upstream CI hard-fails on a missing `Sesam.jks`. Debug builds are also
  signed with the release key.
- Committed build outputs: `app/emzoom/release/output-metadata.json` and two binary `.dm` baseline
  profiles.
- Two Gradle `Copy` tasks hardcoding `/Users/<his-username>/Desktop` as a destination.
- `sync_upstream.sh` (new, 75 lines), his private rebase tool, referencing paths that do not exist in
  the PR and ending in `git reset --hard`.
- `HIDE_OVERLAY_WINDOWS` declared with `tools:ignore="ProtectedPermissions"` and **zero call sites**.
  It is `signature|privileged` and can never be granted to a normal app.
- `import android.R` in `main/AutomationActivity.kt`, which shadows the app's own `R` for the whole
  file.

**Contract-breaking, reject:**

- `applicationId` changed in **`defaultConfig`**, deleting the comment that explains why it must not
  change, which moves the Play Store listing identity for the `playstore` flavour too.
- The custom permission becomes `${applicationId}.permission.NAVIGATION_UPDATE`, so it no longer
  matches `HeadUnit.packageName + ".permission.NAVIGATION_UPDATE"` in the published contract.
- `:contract` is gutted: `HeadUnitIntent.kt` is moved into `:app` with the package constant hardcoded
  to his fork, while `settings.gradle.kts` still includes `:contract`, leaving an empty library module.
  Every third-party nav and automation integration breaks silently.
- The `github` flavour is renamed to `emzoom`, orphaning 21 localized `app/src/github/res/values-*/`
  files and deleting `app/src/github/AndroidManifest.xml` without replacement, so `DummyVpnService`
  compiles with no `<service>` declaration and no `BIND_VPN_SERVICE` and can never start.

**Stale-base replay, not deliberate changes.** The `AapService.kt`, `MainActivity.kt`,
`SettingsFragment.kt`, `OnboardingActivity.kt` and `connection/self/**` diffs delete Self Mode
wholesale (10 source files plus 3 unit-test files), and also revert `StationScanMonitor`,
`StationStandDown.restore()`, `BtAutoDisconnectArm`/`BtAutoDisconnectPolicy`, `SessionEndGroupPolicy`
and auto-resume-playback. Those are upstream features his fork never had. **Merging this branch would
silently revert several releases of work.** Any cherry-pick must be hunk by hunk.

---

## 6. What to do

### Coding task: rebase `feature/video-fit-mode`, then extend it

1. `git rebase main feature/video-fit-mode`. One conflict, `SettingsFragment.kt`, trivial.
2. Re-check the branch's `AapProjectionActivity` hunk **by hand**. It was written before main added
   the `measuredTouchSurfaceEnabled` overlay sizing above it. Git auto-merges it; the semantics need
   eyes.
3. Localize the 5 new strings (`video_fit_mode`, `change_video_fit_mode`, `video_fit_mode_fill`,
   `..._contain`, `..._cover`) into the ~20 locale files. Drop the now-unused
   `pref_stretch_screen_title`/`_summary`. Add a `SettingsBackupManager` compat entry so an old
   backup's `stretch_to_fill` still restores.
4. Add the auto-derived `pixelAspectRatioE4` (§3, Take 2) in `HeadUnitScreenConfig`. No thresholds, no
   flag, no user-facing toggle.
5. Add the locked-AUTO renegotiation fix (§3, Take 1) as its own commit, credited to Sesam17.
6. Extract the resolution-bucket / margin / scale-factor decisions into a pure policy object with
   JUnit tests, per the repo's `WifiModePolicy` convention. There are currently **no** tests for
   `HeadUnitScreenConfig` at all. Use both real panels as fixtures: **1440x720** (MT50) and
   **1920x720** / **1780x720** (Emzoom). Minimum coverage:
   - 1920x720 at `_1280x720`: `scaleY == 1.0` in FILL, `parE4 == 15000`
   - 1440x720 at `_1280x720`: `scaleY == 1.0` in FILL, `parE4 == 11250`
   - 1780x720 at `_1280x720`: `parE4 == 13906`
   - 1920x1080 panel, unchanged: `parE4 == 10000`, `marginHeight == 0`
   - 1440x720 at `_1920x1080`: margins still `480x360`, `parE4 == 10000`
   - locked + AUTO does not fall back to `_800x480`

Not queued, recorded for later: the centre-pivot vs edge-margin disagreement, and `TouchConfig`
advertising the full negotiated size while the mapper emits margin-reduced coordinates.

### What to run on the rig

The MT50's 1440x720 panel reproduces the fault, so this gets a normal round brief
(`ultrawide-touch-alignment-round1-brief.md`, to be written). Sketch:

- **R0** build gate, unit tests including the new `HeadUnitScreenConfig` cases.
- **R1 baseline**, current `main` build, `resolutionId` forced to 720p in `shared_prefs/settings.xml`
  with the app force-stopped. Capture `Normal Scale. scaleX: … scaleY: …` and confirm **`scaleY:
  1.125`**, plus the `[ServiceDiscovery] NegotiatedResolution` and `Margins are:` lines.
- **R2 candidate**, same settings. Expect **`scaleY: 1.0`** in FILL and `pixel_aspect_ratio_e4`
  **11250**.
- **R3 touch alignment**, the point of the round. Tap a known AA target near the top and the bottom of
  the screen and compare the coordinates the app sends against where the tap landed. `input tap` plus
  the app's own touch log line is the instrument; do not eyeball it.
- **R4 regression guard**. Repeat at `_1920x1080`, the rig's normal mode, and confirm `scaleY`, margins
  `480x360` and `parE4 == 10000` are all unchanged.
- **R5 / R6** one run each for CONTAIN and COVER: the fit modes behave and touch stays aligned.

House rules apply. Settings via a pushed `set_pref.sh` with the app force-stopped, never the UI and
never scrolling the settings list; `stdbuf -oL adb logcat` to a file; kill the previous capture's pid
before starting the next.

**The one thing the rig cannot answer** is whether Gearhead's layout actually changes at an extreme PAR
(15000, his value) rather than merely at 11250. If the 11250 result is ambiguous, ask Sesam17 to test
a build. He has the only 8:3 unit in the loop, and he has been a patient and careful reporter. Do not
manufacture a substitute result.

### Reply to the PR

Draft is in `pr943-review-reply-draft.md` on this branch.
