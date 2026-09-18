# rotation geometry — round 1 results

**Candidate:** Part A `fix/rotation-keeps-negotiated-geometry` @ `a03aeb3d`, Part B
`probe/geometry-renegotiation` @ `24a12300` (one commit on top of Part A) &nbsp;&nbsp;**Baseline:**
`origin/main` @ `80a81099`
**APK md5:** Part A `e669731c4bfcc64eb61d378e10d1feb4` / Part B `58152b2d3f41cb7c2dde8191ff0761c2` /
baseline `993c7ccf7219773988348d722269cb00` — all three distinct
**Unit:** D-POCO (Self Mode, all of Part A's A0-A4 and all of Part B), D-HU (two-device A5 only)
**Date:** 2026-09-18

## Setup notes

**R0 build gate: PASS for both candidates.** Part A: 2176 tests green, `ProjectionOrientationPolicyTest`
= 8, `SessionGeometryLockPolicyTest` = 4. Part B: 2187 tests green, `GeometryProbePolicyTest` = 6,
`MaxUnackedPolicyTest` = 4, `ProjectionOrientationPolicyTest` = 9. All match the brief exactly.
`build_hur.sh` and `run_unit_tests.sh` were used as-is; no new script was needed this round.

**D-POCO needed an uninstall.** It carried a non-debug (release, versionCode 109) build at round
start. `adb install -r` of a debug candidate would have failed with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Confirmed with the operator before uninstalling (this is the
one sanctioned exception, not a routine step): app data, settings and onboarding were wiped and
reseeded (`onboarding-version=2`) from scratch. D-HU already carried a debuggable build and never
needed this.

**`log-capture-enabled` was left `false` all round, against the brief's own table (which asks for
`true`).** Setting it `true` spawns the app's own `logcat` and triggers SystemUI's
`LogAccessDialogActivity` on every single launch on this Android 15 unit — a known, already-recorded
blocker (`project_log_capture_triggers_logaccessdialog`) that would have needed a manual tap on
every one of this round's ~20 launches. The round's actual evidence source throughout was the
external `stdbuf -oL adb logcat` capture per §2, which is independent of this setting — every
`AppLog` line the brief's own table asks for (`Sticky Orientation`, `DROPPING LOCK`,
`GEOMETRY_PROBE`, etc.) is gated by `log-level`, not by the in-app exporter. Nothing in this round's
evidence depends on the in-app exporter.

**Rotation via `adb shell settings put system user_rotation` is real but has two traps, both cost a
discarded run before being understood:**
- It only takes effect reliably when set with `accelerometer_rotation=0` *and* several seconds
  *before* the app is force-stopped and relaunched — set mid-session-launch-race and the app can
  negotiate the wrong starting orientation (this round's A1, A3 and B3's first attempts were each
  discarded to it: `a1-DISCARDED-bad-start-orientation.txt`,
  `a3-DISCARDED-wrong-start-orientation.txt`, `b3-selfmode-connectcheck.txt`,
  `b1/b6-selfmode-discarded-wrongorient.txt`). The reliable recipe used from A2 onward: write
  `accelerometer_rotation=0` and the target `user_rotation`, wait, *then* force-stop and relaunch.
- Once applied, the actual system rotation lands with a several-second lag (up to ~15 s observed) —
  `dumpsys display`'s `rotation` field is not a trustworthy live check on this unit (it reported `0`
  throughout multiple genuine rotations); trust `HeadUnitScreenConfig`'s own `Raw size:` log line or
  a screenshot instead.
- **The home/launcher screen is portrait-locked regardless of any of these settings** — an early
  diagnostic session against the home screen (not the app) wrongly concluded rotation was broken
  entirely; it is not, the launcher just never rotates. Don't use the home screen as a rotation
  control on this unit.

**D-HU's own rotation could not be exercised, on the operator's explicit instruction** ("D-HU can
brake, it has a forced ROM behaviour" — only D-POCO is safe to physically rotate). This turned out to
match the code exactly: `GeometryProbePolicy.liftsOrientationPin` (and therefore
`maybeFireGeometryProbe()`) requires a genuine canvas-orientation flip to do anything, so every Part B
run's "same run on two devices" repeat — where D-HU is the panel — is **UNTESTABLE** on this rig, not
merely skipped. Only A5 (Part A's two-device regression guard, which specifically does not rotate) is
reportable in two-device mode this round.

**Two operator interventions were needed on D-POCO mid-round**, both UI-only with no adb path:
1. Android Auto's developer "Start head unit server" toggle was off at round start (port `:5277` not
   listening) — one operator cycle before A0 could run at all.
2. Mid-round (before B1), Gearhead raised its own `RequestManifestPermissionsActivity` ("Permissions
   needed — Location") unprompted, stealing foreground. Self Mode then failed repeatedly with a
   `java.net.SocketException: Broken pipe` immediately after the version request — Gearhead's dev
   server accepted the TCP connection but dropped it before answering. Dismissing the dialog (via a
   `uiautomator`-bounds tap on EXIT) did not by itself fix it; a second operator cycle of the same
   developer toggle did. Discarded: `b1-selfmode-DISCARDED-failed-launch.txt`,
   `b1-isolate-DISCARDED-permission-dialog.txt`, `b1-selfmode-DISCARDED-broken-pipe.txt`.

**Photographs are `adb exec-out screencap`, not camera photographs, on the operator's explicit
instruction** ("do the adb screencap on D-POCO, but safely"), overriding the brief's "photograph"
requirement. Run sequentially with no parallel/looped screencap calls, per the standing
no-parallel-screencap rig rule. Named `<run>-before.png` / `<run>-after.png` per run rather than
`.jpg`.

**Process hygiene gap, found and fixed mid-round but affecting the raw files:** `kill %1` does not
carry a job reference across separate tool invocations in this session, so several per-run
`stdbuf -oL adb logcat` background captures were not actually killed when a run ended — up to 14
were found still running at once and were cleaned up by PID partway through the round (evidence:
`ps aux` output, not reproduced here). **Every count and log line quoted in this report was read live,
during that run, before the next run started** — so nothing below is contaminated. But the raw
`.txt` files for `a0`, `a1`(pre-redo), `a2`–`a5` and the Part B Self Mode runs contain appended
output from later runs past each one's own teardown line, because the orphaned process kept writing
to the same path. Anyone re-deriving a count from these files by grepping the whole file (rather than
up to each run's own `headunit://exit`) will get an inflated number. The final cleanup command list is
in the raw session log if needed.

**Raw logcat captures (~670 MB total) are kept locally, not committed here** —
`hur-wifi-test-scripts/round-rotation-geometry-r1/*.txt` on the rig PC. Every screenshot is committed
to `evidence/rotation-geometry-round1/`; every quoted log line and count in this report was read live
from those local files during the run it describes.

**No two-phone/rig hazards hit this round**: D-HU/D-POCO stayed bonded throughout, A2DP/HFP was not
needed. D-POCO's Bluetooth was found off partway through the round (residual from earlier,
unconnected diagnostic work) and was re-enabled before A5 — noted since it produced one fully
discarded A5 attempt (`a5-DISCARDED-bt-off.txt`) with repeated poke failures that had nothing to do
with the candidate.

## R0 — build gate

**PASS.** See above.

## A0 — a session that never rotates is untouched

**PASS.** Self Mode, `screen-orientation=2`, D-POCO held landscape throughout, ~2 min. 25 throughput
windows, `rendered` tracking `fed` at 28-30 fps the whole time, zero `dropped`/`skipped`/`concealed`.
**Sticky Orientation: 1** (the launch-time pin, naming orientation `0`/landscape). **DROPPING LOCK: 0.
is already announced: 0.** Screenshot: clean two-pane landscape layout (map + trip cards + Spotify
widget), FPS 30, no distortion.

## A1 — the defect on this rig, baseline

**No verdict (per brief) — the rig reproduces the fault, so A2 gets a real one.**
`origin/main` @ `80a81099`, Self Mode, `screen-orientation=0`, auto-rotate on, landscape start.
- (a) **The panel turned.** Confirmed via `HeadUnitScreenConfig`'s `Raw size:` line flipping
  `2400x1080` → `1080x2400` and a screenshot.
- (b) **The picture was visibly distorted after the turn**: the landscape two-pane layout (Search bar,
  home icon, left icon dock) was squeezed/cropped into the portrait frame rather than the map
  reflowing to a portrait layout — icons on the left edge partially cut off, map extending well past
  where a clean portrait render would end. `a1-after.png`.
- (c) **It recovered cleanly on turning back** — `a1-recover.png` shows the same clean two-pane
  landscape layout as the pre-rotation shot, throughput back to 29 fps.
- (d) **DROPPING LOCK: 0** (the line doesn't exist on baseline; not applicable, not evidence of
  anything).
- (e) **`config_index` in every `Media Start Request VIDEO`: `0`**, once, at connect — no
  re-negotiation was ever attempted on either turn.

## A2 — the pin holds System Default

**PASS.** Part A candidate, same sequence as A1. The projection **never turned**
(`HeadUnitScreenConfig`'s `Raw size:` stayed `1920x1080` throughout both rotation attempts, screenshots
identical landscape before/after/recovered). **One Sticky Orientation line naming the negotiated
orientation** (`orientation setting applied, forcing orientation to 0` at launch — see the note below
on the brief's exact quoted string). **DROPPING LOCK: 0** across the whole session. Throughput held
steady 29-30 fps with zero dropped frames through both rotation attempts.

**Line-text correction for the brief:** `[UI_DEBUG] Sticky Orientation: session active, forcing
orientation to` never appears verbatim in either candidate. The single call site
(`AapProjectionActivity.kt:2194`) is parameterised by a `why` string; `"session active"` is passed
only from `applyStickyOrientation()`, and that function's own log line is gated by
`if (requestedOrientation == target) return` — it only prints when the pin has to **change** a value,
which on this rig's runs never happened once the launch-time pin held. The line that actually appears
is `"orientation setting applied, forcing orientation to N"` from `applyOrientationSettings()`
(`AapProjectionActivity.kt:2293`), fired once at launch. Read "one Sticky Orientation line naming the
negotiated orientation" as satisfied by that line; a brief expecting `"session active"` verbatim will
never see it on a run where the pin never needs re-asserting, which was every A2/A3/A4 run here.

## A3 — the same under Auto

**PASS.** Part A candidate, `screen-orientation=1` (Auto), landscape start, **physically** rotated by
the operator by hand (Auto maps to `SCREEN_ORIENTATION_SENSOR` before the pin resolves, then to a
fixed constant after — `adb`'s `user_rotation` cannot drive a live `SCREEN_ORIENTATION_SENSOR`
window the way it can drive `SCREEN_ORIENTATION_USER`, so this run needed hands. First attempt
mis-timed the physical hold before relaunch and negotiated portrait by accident — discarded and
redone holding landscape *before* launching).

Held physically in portrait for the duration of the check: **the panel stayed locked at landscape**,
zero visible movement, zero distortion, FPS steady at 29, **DROPPING LOCK: 0**. Confirms the Auto
setting (already pinned pre-fix) did not regress.

## A4 — the TextureView backend reports its canvas

**INCONCLUSIVE.** Part A candidate, `screen-orientation=2`, `view-mode=1`. Three methods tried to
move the canvas without rotating, none produced `onSurfaceResized`:
1. `adb shell cmd statusbar expand-notifications` — the shade draws as a transparent overlay on top
   of the content; the underlying canvas never resizes.
2. An edge swipe intended to reveal the system bars temporarily — same result, no resize.
3. The brief's own offered fallback, `fullscreen-mode` changed with the app stopped
   (`IMMERSIVE`→`NONE`) between two launches — the status bar then draws as a persistent overlay
   (confirmed by screenshot), again without resizing the content area underneath it.

`onSurfaceResized: 0`, `TX UpdateUiConfigRequest: 0` across the whole session. No distortion either
way. This is a genuine "this unit gives no way to move the canvas without rotating" — as the brief
anticipated — not a candidate defect. (For context: B1-B6 later confirmed `onSurfaceResized` *does*
fire correctly on this unit, from a genuine rotation — see Part B below — so the TextureView callback
itself works; A4's three levers just never produced the qualifying kind of resize.)

## A5 — two devices, no regression

**PASS.** Part A candidate on D-HU, ordinary Native AA session with D-POCO as the phone,
`screen-orientation=2`, 5 minutes, no rotation. One `createGroup SUCCESS`, phone's own reconnect beat
the poke (SSL handshake ~9 s after listeners opened — normal for this rig). 71 throughput windows over
the full 5 minutes, `rendered` tracking `fed` the entire time, **zero dropped frames in any window**,
**DROPPING LOCK: 0**. One earlier attempt was fully discarded (`a5-DISCARDED-bt-off.txt`) — D-POCO's
Bluetooth was off from unrelated earlier work, producing repeated poke failures unrelated to the
candidate; re-enabled and rerun clean.

---

## Part B

Every run below is **Self Mode only** — see Setup notes for why the two-device repeat is UNTESTABLE
on this rig. Per the brief, **there is no PASS/FAIL in Part B**; every outcome below is a result.

### The headline finding, true for every one of B1, B2, B3, B4 and B6

**`[GEOMETRY_PROBE]` never fired, not once, in any of the five runs** (`grep -c GEOMETRY_PROBE` = 0 in
every capture, checked live during each run). This is not the levers being acknowledged and doing
nothing — the lever was never sent at all. Traced to the code: `maybeFireGeometryProbe()` is only
reached from `onSurfaceChanged`'s `anchorMoved` branch or from `onSurfaceResized`, and both of those
require `HeadUnitScreenConfig.updateSurfaceDimensions()` to return `true`. On every rotation this round
produced, `onSurfaceResized` **did** fire with the real new dimensions (confirmed:
`onSurfaceResized: 1080x2400` after every landscape→portrait turn), but `updateSurfaceDimensions()`
returned `false` every time — no `"[UI_DEBUG_FIX] Surface mismatch detected!"` line ever appeared,
meaning the function's own normalisation/tolerance step treated a plain landscape↔portrait swap as
not a real mismatch and returned early before `maybeFireGeometryProbe()` was ever called. Only the
*ordinary* margin-reannouncement path fired instead (`reannounceMargins()` →
`sendUpdateUiConfigRequest`, acknowledged by AA every time). **As wired, none of the five levers can
be exercised by a plain device rotation on this unit** — a genuinely different-shape canvas (not a
rotation of the same one) would be needed to reach the gate, which this round's method cannot produce.
This is a wiring gap in the probe, not evidence about what Android Auto does with any of these five
messages — the round could not reach the point of asking the question.

**A second, separate finding: arming any probe mode reintroduces Part A's fixed defect.**
`GeometryProbePolicy.liftsOrientationPin(mode) = isActive(mode)` — true for every mode 1-5 — so with
any probe armed the orientation pin behaves exactly like baseline (A1): the panel **does** rotate, and
the same squeeze/distortion A1 showed reappears (confirmed by screenshot on B1, `b1-selfmode-after.png`
— the landscape two-pane layout squeezed into the portrait frame, identical in kind to `a1-after.png`).
This is intentional per the code comment ("a probe has to let the panel rotate") and is not itself a
new defect, but it does mean every Part B run's photograph shows the pre-Part-A-fix squeeze, and that
squeeze is present in the round's evidence for a completely different reason than the mechanism under
test.

### B1 — mode 1, `ServiceDiscoveryUpdate`

1. **`[GEOMETRY_PROBE] TX` line:** none — never fired (see above).
2. **Phone's answer within 10 s:** none from the probe path. The ordinary margin path did fire and was
   acknowledged: `[UI_DEBUG_FIX] TX UpdateUiConfigRequest: L=108 T=0 R=108 B=0` →
   `UpdateUiConfig reply received. AA acknowledged new margins.`
3. **`Received video dimensions`:** nothing new (still `1920x1080` from connect).
4. **Photograph:** changed — squeezed/distorted, per the pin-lifted finding above.
5. **Session survived:** yes — `rendered` non-zero in every throughput window before and after, no
   stall.

Connect-time `config_index`: not applicable (B1 announces one config, not two).

### B2 — mode 2, `Media.Config` STATUS_READY selecting index 1

1. **TX line:** none.
2. **Answer:** same ordinary margin-correction path as B1, acknowledged.
3. **Video dimensions:** unchanged.
4. **Photograph:** changed (squeeze, pin lifted).
5. **Session survived:** yes, clean recovery to 29-30 fps.

**Connect-time `config_index`: `0`** — the phone picked the panel's own (index 0) configuration
unprompted, not the second announced one. Not the `1` that would have stopped the round.

### B3 — mode 3, `Media.Config` STATUS_WAIT then STATUS_READY

1. **TX line:** none.
2. **Answer:** same ordinary margin path, acknowledged.
3. **Video dimensions:** unchanged.
4. **Photograph:** squeeze present (not separately re-captured beyond the throughput/stall evidence
   below, which is the more significant finding for this run).
5. **Session did *not* recover the way B1/B2/B4/B6 did.** Throughput collapsed to nearly zero
   (`rendered=5→0→0→5→0…`, 0-1 fps) starting at the rotation and stayed there for the full ~95 s this
   run was observed, never returning to a healthy rate. No app-level crash, ANR or exception in the
   capture — the process stayed alive and the last frame stayed on screen, frozen (`Frame: 12441ms` in
   the on-screen overlay). Correlated in the log with Gearhead's own virtual-display churn on its side
   (`DisplayDeviceRepository`: `GhFacetBar` and the Maps virtual display both removed and re-added
   around the same timestamp) — this looks like a Gearhead-side re-layout stall rather than anything
   in our own decoder/watchdog chain, but that is an inference, not confirmed from our own log alone.

**Connect-time `config_index`: `0`.**

### B4 — mode 4, `Media.Config` index 1 then a video focus cycle

1. **TX line:** none.
2. **Answer:** ordinary margin path, acknowledged.
3. **Video dimensions:** unchanged.
4. **Photograph:** squeeze present (pin lifted), same as B1/B2.
5. **Session survived cleanly** — no stall, throughput held 29-30 fps straight through the rotation,
   unlike B3.

**Connect-time `config_index`: `0`.**

### B6 — mode 5, `UpdateUiConfigRequest` carrying `ui_theme`

1. **TX line:** none — mode 5 doesn't touch video config at all
   (`GeometryProbePolicy.renegotiates(UI_THEME) = false`), so this is expected regardless of the
   wiring gap above.
2. **Answer:** ordinary margin path, acknowledged.
3. **Video dimensions:** unchanged.
4. **Photograph:** squeeze present (pin lifted — this applies even to the mode that never renegotiates,
   confirming the squeeze is purely a function of `liftsOrientationPin`, independent of what the probe
   itself does).
5. **Session did *not* recover** — the same near-zero-throughput stall as B3
   (`rendered=0→5→0…`, sustained for the ~50 s this run was observed). Given mode 5 sends no video-
   config message at all, this stall cannot be attributed to the probe's own payload; it is most
   likely the same Gearhead-side re-layout behaviour seen in B3, triggered by the rotation itself
   rather than by anything mode-specific.

Not applicable: B6 doesn't announce a second config, so no connect-time `config_index` to report.

**If the whole of Part B comes back "acknowledged, nothing moved," the brief said that was the more
likely outcome — this round didn't even reach "acknowledged, nothing moved."** It reached "never sent"
for four of five levers, and "sent, acknowledged by the ordinary margin path, nothing moved" is
correct only for describing what *did* happen alongside the probe's silence. Retiring these two
Google-schema readings as tested is **not** achieved by this round; what is achieved is finding that
the lever built to test them cannot fire from a plain rotation as this round could produce one.

### B5 — real_density

`geometry-probe-mode=0`, `screen-orientation=4` (Portrait, fixed), `dpi-pixel-density=0` (Auto). No
rotation in this run.

**`real_density=true`:** `[ServiceDiscovery] real_density is: 440`. The companion `density` field
(`ServiceDiscoveryResponse.kt:113`, `setDensity(HeadUnitScreenConfig.getDensityDpi())`) is set but
**never logged anywhere** — there is no `"density is:"` line to read it back from, so "beside the
density in the same block" as the brief asks cannot be read from the log as written. Inferred from
`dumpsys display` on D-POCO instead: `440` — the same value, since `dpi-pixel-density=0` means the UI
layout density already equals the panel's own.

Touch tested by a swipe-pan on the map in Google Maps (self mode, portrait): the view visibly
recentred/re-zoomed in response, confirming touch coordinates land correctly. **Repeated with
`geometry-probe-real-density=false` as the control**: identical result, touch pan worked the same way.

**Not completed: the Waze comparison.** Several scripted taps at the on-screen app-switcher icon's
estimated position did not open the Android Auto app switcher (the projected content isn't part of
the accessible view hierarchy, so `uiautomator dump` cannot give exact bounds the way it can for a
native screen). Rather than keep guessing coordinates, this was left incomplete — Maps touch is
confirmed working in both arms; Waze is not tested this round.

Since `dpi-pixel-density=0` in both arms, this run did not exercise "works without setting pixel
density below 200" — that only matters once `dpi-pixel-density` is pinned to something low, which the
brief's own B5 spec does not ask for. Both arms measured here ran at the panel's native 440.

## Anything the brief did not ask about

- **`onSurfaceResized` does fire correctly on this unit from a genuine rotation** — B1 through B6 each
  logged it once per rotation, with the correct new dimensions, confirming the TextureView backend's
  own resize callback works. A4's INCONCLUSIVE is specifically about *non-rotation* canvas moves, not
  a general defect in the callback.
- **The margin-correction path (`reannounceMargins`/`sendUpdateUiConfigRequest`) is doing real,
  independent work in every Part B run** even though the geometry probe itself never fires — it is
  what actually kept the session alive and acknowledged through five back-to-back rotations with no
  probe ever reaching the wire. Worth being clear this is a *different* mechanism than
  `GeometryProbePolicy` in the round's own write-up, since both react to the same rotation event.
- **The B3/B6 stall (not B1/B2/B4) suggests it is not simply "any probe mode plus rotation."** Modes 1,
  2 and 4 recovered in the ~5-10 s range typical of A1/A2; modes 3 and 5 did not recover at all within
  the observation window. Mode 5 sends no video message whatsoever, which weakens any theory that pins
  the stall on a specific probe payload — timing/session-state at the moment of rotation looks like a
  better candidate than the probe mode itself, but this round did not isolate which.
- **The rig's Gearhead dev head-unit-server (`:5277`) broke mid-round for a second time**, this time
  correlated with its own unprompted permission dialog stealing foreground — a new trigger not seen in
  prior rounds' notes on this failure mode (`project_dpoco_selfmode_gearhead_server`,
  `project_selfmode_needs_gearhead_hu_server_tap`). Worth adding to that memory: a permission dialog
  raised by Gearhead itself, not just a force-stop, can also wedge the server and need the same
  developer-toggle recycle to recover.
