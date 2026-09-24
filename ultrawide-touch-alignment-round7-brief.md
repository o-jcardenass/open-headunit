# ultrawide-touch-alignment: round 7 brief

Hosts, and the labels used throughout:

| Label | Unit | Panel | Role |
|---|---|---|---|
| **D-HU** | UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14 | 1440x720, aspect 2.0, **OEM side bar visible in Screen mode Normal** | Native AA, projecting phone is D-POCO, view-mode GLES |
| **D-POCO** | POCO X3 NFC (`M2007J20CG`), Android 15 | 2400x1080 landscape | the projecting phone for D-HU; Self Mode only if a run says so |
| **D-MOTO** | Moto edge 30 neo | ~2400x1080 | Self Mode, R7 only |

Read `TESTING-TEMPLATE.md` before planning any step; §7a applies in full. This file is append-only;
corrections arrive as new commits and a `git pull` fast-forwards.

Round 6 is the parent. Read `ultrawide-touch-alignment-round6-results.md`,
`-round6-addendum-dmoto.md` and `-round6-review.md` first: this round answers the three asks the
review file queued, and several expectations below are "identical to a round 6 number".

**D-HU is the unit this round exists for.** Its OEM side bar is on screen in Screen mode Normal and
gone in Immersive, which is exactly the difference a reporter measured on an 8:3 unit where the
branch fixed the picture in Immersive and broke it in Normal. Every other host is supporting.

---

## 0. What changed since round 6, and what this round settles

Round 6 closed the squash and the round 4 centring regression. Its D-MOTO addendum and the review
notes left one gap standing: **the pixel shape is derived from whatever the screen config believed
the canvas was at `makeProto` time, it goes out once, and it can never be re-sent.** The review
predicted, from code reading alone, that the cache meant to carry a settled canvas into the *next*
connect could not survive a process restart, and asked for one grep to settle it.

That reading is confirmed, and the cause turned out to be larger than the cache. Reporter logs from
an 8:3 unit (1920x720 panel) show the app announcing a canvas nothing had measured:

```
Immersive  Touch map: raw=1099,383 -> video=733,383  view=1920x720 video=1280x720 margin=0x0 fit=FILL
Normal     Touch map: raw=52,689   -> video=35,720   view=1920x642 video=1280x720 margin=0x0 fit=FILL
Normal     HeadUnitScreenConfig: Honest Init | Mode: NONE | Anchor: 1920x642
```

`1920x642` is neither the panel nor the window: raw touch Y reaches 691 in the same capture, and his
photo shows the projected canvas starting to the right of a ROM side bar. Outside an immersive mode
the anchor came from `display.getSize()`, a display-level API that on that ROM reports a decoration
on the wrong axis and knows nothing about the side bar. **Immersive worked by coincidence**, its
anchor being the raw panel and the panel being the window.

Two further defects sat under it, both visible in the same capture:

- `init()`'s early return compared the anchor against the **raw display**, which outside an
  immersive mode can never match, so every `ProjectionViewScaler.updateScale` re-ran the whole
  init and discarded any correction a surface had made. The reporter's log carries two
  `Honest Init | Mode: NONE | Anchor: 1920x642` lines 40 ms apart, each followed by `Normal Scale`.
- `computeSettingsHash` folded the mutable anchor, so the surface cache could never match after a
  cold start. This is the review's prediction, exactly.

Touch had its own independent hole: the measured overlay was used only with
`use_measured_touch_surface` on **and** the mode exactly IMMERSIVE, and that setting defaults to
false, so every mode divided by a config figure rather than by the surface the coordinates came from.

### The candidate

Two commits on top of round 6's `e4be4e0e3`:

- **`fd4819b0` Screen config: announce the canvas the video is drawn into.** New pure
  `utils/ProjectionCanvasPolicy` ranks the readings: **projection surface > live activity window >
  cached surface > display metrics**, each tagged with the settings hash it was taken under.
  `SystemUI.apply` now reports the window's content area on layout, so the *first* session in a
  screen mode announces the shape it will actually draw into rather than learning it from a surface
  that arrives after the answer went out. The init guard compares the anchor it would choose, so a
  measurement survives. `computeSettingsHash` folds the physical panel instead of the anchor, so the
  cache can match after a cold start. Readings taken in picture-in-picture or while the soft
  keyboard is up are refused.
- **`409056f4` Touch: the mapper measures the surface the events came from.** `viewW/viewH` are the
  overlay's own size in every screen mode, with the config only as a pre-layout fallback.
  `use_measured_touch_surface` is deleted: property, backup key, settings row and strings in all 21
  locales. A leftover `use_measured_touch_surface` key in an existing `settings.xml` is now ignored;
  do not delete it, it is a useful marker that the file was not rewritten.

### Log lines that changed, and this is the one thing that will waste time if missed

- **Gone on the candidate:** `[UI_DEBUG_FIX] ... Using cached surface dimensions` and
  `[UI_DEBUG_FIX] ... Cache invalidated (hash mismatch`. Do not grep for them on the candidate; they
  are what R8 greps for on the **baseline**.
- **Changed:** the `Honest Init` line now carries the source in parentheses:

  ```
  HeadUnitScreenConfig: Honest Init | Mode: NONE | Anchor: 1290x720 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
  # 1290x720 is the shape of the line, not a prediction for D-HU
  ```

  The four sources are `SURFACE`, `WINDOW`, `CACHE`, `DISPLAY`. **`DISPLAY` outside Immersive is the
  pre-fix state**; anything else means a real measurement is driving the announcement.
- **New:** `HeadUnitScreenConfig: anchor WxH -> WxH, measured on the activity window` (or
  `on the projection surface`), once per move.
- **New:** `CarScreen: canvas moved from the announced WxH to WxH; the pixel shape cannot be
  re-sent`. Instrument only. It is the answer to "does a screen-mode change restart the session",
  which is unmeasured; see R6.

All three are `AppLog.i`, so INFO carries them.

---

## 1. R0 build and unit-test gate

- **Candidate:** `fork/fix/video-fit-and-ultrawide-touch` @ `409056f4` (take the exact SHA from this
  thread's `README.md` router row when you start; the branch may gain a correction commit).
- **Baseline:** round 6's candidate, `e4be4e0e3`, md5 `959467e2...`. `main` is **not** the baseline
  for this round: the question is what these two commits changed, not what the branch changed.
  Round 6 left that APK on D-HU and D-POCO, but a later round on another thread reinstalled D-POCO,
  so **verify the md5 per device rather than assuming**. Only **D-HU** needs the baseline arm (R1
  and R3); if its md5 does not match, rebuild `e4be4e0e3`. D-POCO is only the projecting phone here
  and whatever it carries is irrelevant to D-HU's geometry. D-MOTO needs the **candidate** for R7.

Build the candidate with `hur-wifi-test-scripts/build_hur.sh` under `thermal_guarded.sh`. Run
`run_unit_tests.sh` on the candidate only.

Gate: **candidate green, 1482 tests / 0 failures** (round 6's baseline was 1473/0). New class
`ProjectionCanvasPolicyTest` (8 tests) and one added case in `TouchCoordinateMapperTest`,
`the denominator is the surface the events came from`.

Its two decisive assertions are this round's thesis in JVM form:

- with nothing measured and not immersive, the choice is the window metrics and the source is
  `DISPLAY`; with a surface of `1748x720` recorded, the choice is `1748x720` and the source is
  `SURFACE`, even though the display metrics still say `1920x642`
- a measurement tagged with another settings hash is ignored, so a screen-mode change drops every
  reading that described the old window

**Identity.** `ACTION_QUERY_STATE`'s `commit` field still truncates (rounds 3 to 6), and version name
and versionCode do not move between these two builds, so carry identity by live APK md5 re-read per
device before every run **and** by a symbol the baseline cannot contain:

```bash
PKG=com.andrerinas.headunitrevived
adb shell pm path $PKG                      # pull that apk, then
unzip -p <apk> 'classes*.dex' | strings | grep -F 'ProjectionCanvasPolicy'
unzip -p <apk> 'classes*.dex' | strings | grep -F 'noteWindowContent'
```

Both are present only on the candidate. Record both md5s.

If the build or the unit run fails, **stop and report**.

---

## 2. Standing settings

Written to `shared_prefs/settings.xml` with the app force-stopped, never through the UI, never by
scrolling the list. D-HU is rooted; use `r6_set_hu_prefs.sh` from round 6 for multi-key writes.
Back up `settings.xml` per device first and restore it at the end.

| Key | Type | Value | Why |
|---|---|---|---|
| `resolutionId` | int | `2` (720p) | the rung round 6 R1 measured; keeps the A/B on one variable |
| `video-fit-mode` | int | `0` (FILL) | the only mode that carries a pixel shape |
| `log-level` | int | `2` (INFO) for R1, R2, R4, R5, R6; **`0` (VERBOSE) for R3 and R7** | `Touch map` is an `AppLog.v` behind `LOG_VERBOSE`; everything else this round is `AppLog.i` |
| `fullscreen-mode` | int | per run: **0 NONE, 1 IMMERSIVE, 2 STATUS_ONLY, 3 IMMERSIVE_WITH_NOTCH** | the round's independent variable |

`fullscreen-mode` is part of the settings hash, so changing it deliberately invalidates every canvas
measured under the previous mode. That is the design. **No extra launch is needed to compensate**:
the window is recorded when the first activity lays out in the new mode, and §7a already requires an
explicit `am start` of `MainActivity` after any settings write, which is that layout. Follow the
clean-run protocol unchanged.

If a run's `Honest Init` nevertheless reads `(from DISPLAY)` while the framework frame disagrees
with the display metrics, do not add a launch to make it pass. Record it: that is the finding.

### The ground truth this round is scored against

Do not take the app's word for the canvas. Read the window frame from the framework, with the
projection on screen, and quote it in the results:

```bash
adb shell dumpsys window displays | sed -n '1,40p'
adb shell dumpsys window windows | grep -iE "AapProjectionActivity" -A 12 | grep -iE "mFrame|mBounds|Frame:"
adb shell wm size ; adb shell wm density
```

A verdict of PASS below means the app's `Anchor:` agrees with that frame, not that the app is
self-consistent.

---

## 3. R1: D-HU, Screen mode Normal, baseline

The measurement the round is an A/B against. Baseline APK (`e4be4e0e3`), `fullscreen-mode=0`.

Standard clean-run protocol (§4). Capture at INFO. Let the session form, leave it 60 s, exit.

Record, verbatim with timestamps:

- `HeadUnitScreenConfig: Raw size: ... usable: ...` (both figures, they are the two display APIs)
- `Honest Init | Mode: NONE | Anchor: ...` (the baseline has no `(from ...)` field)
- `[RES_CAP] resolutionId=2 realScreen=... usable=...`
- `CarScreen isSmallScreen: ... shape=... margins: w=..., h=...`
- `[ServiceDiscovery] NegotiatedResolution is:` and `PixelAspectRatioE4 is:`
- `Normal Scale. scaleX: ..., scaleY: ...`
- the framework window frame from §2
- one photo of the panel with the projection up, side bar included

**This run has no PASS condition.** It is a measurement. What matters is whether `Anchor:` equals
the framework frame; say which, and by how much on each axis.

---

## 4. R2: D-HU, Screen mode Normal, candidate (decisive)

Same setup, candidate APK. Same recordings.

**PASS** requires all of:

1. `Honest Init | Mode: NONE | Anchor: WxH (from WINDOW)` or `(from SURFACE)` or `(from CACHE)`.
   **`(from DISPLAY)` is a FAIL** unless the framework frame in §2 genuinely equals the display
   metrics on this unit, in which case say so and the run is INCONCLUSIVE rather than a pass.
2. That `Anchor` equals the framework window frame on both axes, within 4 px.
3. `PixelAspectRatioE4 is:` equals `round(usableW * 720 / (usableH * 1280) * 10000)`, or exactly
   `10000` when that value is within 3% of 10000, which is the deliberate dead band. Take `usableW`
   and `usableH` from the `[RES_CAP] ... usable=WxH` line of the same connect, not from `Anchor`:
   the two differ by whatever insets are in force, and only in Screen mode Normal are they equal.
   Show the arithmetic.
4. `Normal Scale. scaleX: 1.0, scaleY: 1.0` with `margins: w=0, h=0` and `shape=PAR`.

**One expected fork, not a failure.** If the side bar takes D-HU's canvas below 1280 px wide, the
derived ratio falls under 10000, `MarginStrategyPolicy` returns `MARGIN` and margins come back with
`shape=MARGIN` and a non-unity scale. That is correct behaviour for a canvas narrower than its
buffer. Record it, note that condition 4 does not apply, and score conditions 1 to 3 only.

Also record the photo, and say plainly whether the picture's proportions match the Immersive photo
from R4. A visibly squashed or stretched picture with all four conditions met is a finding worth
more than the run.

---

## 5. R3: D-HU, Screen mode Normal, touch A/B

`log-level=0`, both APKs, `fullscreen-mode=0`, one arm each. The projecting phone is D-POCO, whose
Gearhead logs `GhFacetBar injectMotionEvent`, so score by what Android Auto received and by which
app opened, never by our own `Touch map` line alone (round 4 R5 is why).

For each arm, with the AA home screen up, five synthetic taps via `input tap`:

- the visible centre of the **bottom-most** rail icon
- the visible centre of the **top-most** rail icon
- the exact centre of the panel
- 20 px inside the **left** edge, vertically centred
- 20 px inside the **right** edge, vertically centred

Record per tap: the `input tap` coordinates, the `Touch map: raw=... -> video=... view=WxH ...`
line, the `injectMotionEvent` coordinates from the phone, and a `screencap` naming which app or
control opened where one did.

**PASS** on the candidate requires:

1. `view=WxH` in every `Touch map` line equals the framework window frame, not the display metrics.
2. No tap clamps to the buffer edge unless the tap was itself on the panel edge. On the reporter's
   unit the bottom-row taps read `video=...,720`, the clamp, because the denominator was 78 rows
   short. A `video` Y equal to the buffer height from a tap that was not at the bottom edge is the
   signature of this bug and is a **FAIL**.
3. The bottom rail tap and the top rail tap open the control they were aimed at.

The baseline arm is a measurement, not a verdict: report what it did.

---

## 6. R4: D-HU, Immersive, regression guard

`fullscreen-mode=1`, candidate only, INFO. This is the mode that already worked, and the whole risk
of the change is here.

**PASS** requires the announcement to be bit-identical to round 6 R1:
`NegotiatedResolution 1280x720`, `Margins 0x0`, `PixelAspectRatioE4 11250`, `Normal Scale.
scaleX: 1.0, scaleY: 1.0`, `shape=PAR`. The `Anchor` should read `1440x720`; the source may be any
of `WINDOW`, `SURFACE` or `CACHE` and that is not a condition, since on this unit all three equal
the panel.

Any deviation from round 6 R1 in Immersive is a **FAIL** even if it looks more correct, and should
stop the round: it means the change moved a panel it had no business moving.

---

## 7. R5: D-HU, the other two screen modes

Candidate only, INFO, one launch each: `fullscreen-mode=2` (STATUS_ONLY) and `fullscreen-mode=3`
(IMMERSIVE_WITH_NOTCH). Two short runs; a formed session and a picture is enough, no touch table.

For each, record the same six lines as R1 plus the framework frame.

**PASS** requires, per mode:

1. `Anchor` equals the framework window frame within 4 px, whatever the source says.
2. `[RES_CAP] usable=` equals `Anchor` minus the insets on the `Seeded Insets` field, and the
   canvas does not shrink twice. On the pre-fix build a bar could be subtracted from the anchor and
   again from the canvas; a `usable=` noticeably smaller than the visible picture area is that bug.
3. The picture fills the area inside the bars with no letterbox that the mode did not create.

These two modes have never been measured on this rig. If either cannot form a session on D-HU for a
reason unrelated to geometry, mark it INCONCLUSIVE and move on; do not spend the round on it.

---

## 8. R6: the canvas across a reconnect and a force-stop (review ask 2)

Candidate on D-HU, `fullscreen-mode=0`, INFO. Three connects in one sequence, no settings change
between them:

1. the first connect after a force-stop (this is R2's capture; reuse it)
2. `headunit://disconnect`, wait 10 s, reconnect **in the same process** (§3: on Native AA, cycle
   the phone's Bluetooth)
3. `am force-stop`, relaunch, connect again

Record `Honest Init | ... (from ...)`, `PixelAspectRatioE4 is:` and `[RES_CAP] realScreen=` for each
of the three.

**PASS:** all three announce the same `PixelAspectRatioE4`, and connect 3's source is `CACHE`,
`WINDOW` or `SURFACE`, never `DISPLAY`. That is the review's ask 2 with the fix in place: the
pre-fix expectation was the settled ratio in-process and the pre-settle ratio again after the
force-stop.

Then, and only if the rig allows it without a reboot, one extra observation the code cannot answer
on its own: **with a session live, change `fullscreen-mode` from 0 to 1 in `settings.xml` and
relaunch the app from adb.** Report whether the session survives, whether the Android Auto loading
screen reappears, and whether `CarScreen: canvas moved from the announced WxH to WxH; the pixel
shape cannot be re-sent` prints. Whichever way it goes is the answer this instrument was added for;
there is no PASS condition, only the observation.

---

## 9. R7: D-MOTO, CONTAIN control hit (review ask 3, unchanged)

Candidate on D-MOTO, Self Mode, `video-fit-mode=1`, `log-level=0`. Connect, wait for
`margins drifted from the announced`, then one synthetic tap on the visible centre of the
**bottom** rail icon and one on the **top**.

**Verdict from which app opened, read from a `screencap`, not from the `Touch map` line.** The
review's expectation, if `UpdateUiConfigRequest` is inert as measured: the bottom tap misses or
lands on the neighbour. If the bottom tap now lands correctly, that is a result worth more than the
run and needs the `Anchor` source line quoted next to it.

---

## 10. R8: the ask-1 grep, on the baseline captures

No new run. Grep the **existing** round 6 D-MOTO captures, and the R1 baseline capture from this
round, for:

```bash
grep -n "Using cached surface dimensions" *.txt
grep -n "Cache invalidated (hash mismatch" *.txt
```

Report which appears, per connect. Both lines exist only on the baseline; the candidate replaced
them with the `(from ...)` field, so a candidate capture showing neither is expected and is not a
finding.

---

## 11. Reporting

`ultrawide-touch-alignment-round7-results.md` in the template's §7 format, plus a
`ultrawide-touch-alignment-round7-photos/` folder. Photographs are load-bearing this round: R2 and
R4 must each carry one of the same screen so the proportions can be compared side by side, and R2's
should show the OEM side bar.

Numbers, not adjectives: every `Anchor`, every `PixelAspectRatioE4`, every framework frame, and the
arithmetic connecting them.

Restore `settings.xml` on every device and say which APK each is left carrying.
