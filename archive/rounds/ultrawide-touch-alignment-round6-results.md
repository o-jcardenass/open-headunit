# ultrawide-touch-alignment — round 6 results

**Candidate:** `fix/video-fit-and-ultrawide-touch` @ `e4be4e0e3e2d25c966ead8cb82029a0cd2f22a55`
(`main` `12706e26f` + 3 commits; no correction commit was pushed, `e4be4e0e3` is the tip)
**Baseline:** `origin/main` @ `12706e26f`
**APK md5:** candidate `959467e2989744c5db9356923949a433` / baseline `aac9797f09d1f63d9bdca1415a90e733`
(both versionCode 106, so `adb install -r -d` is a clean reinstall, settings preserved — verified by
`settings.xml` md5 before/after every install)
**Units:**
- **D-HU** — UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, panel 1440x720 (aspect 2.0), view-mode
  GLES, Native AA (`wifi-connection-mode=3`), D-POCO as the projecting phone — R1, R2, R5 (D-HU legs), R6, R8
- **D-POCO** — POCO X3 NFC (`M2007J20CG`), Android 15, 2400x1080 landscape (aspect 2.222), Self Mode,
  Gearhead 17.5.663214 — R3, R4, R5 (D-POCO legs)
- **D-MOTO** — Moto edge 30 neo — **not connected to the rig this round**, so R7 could not run
**Date:** 2026-09-08

## One-line answer to the question the round exists for

**Yes.** A stored 1080p on D-HU's wide panel now negotiates a margin-free `1920x1080` canvas
(`Margins 0x0`) with the aspect carried on `PixelAspectRatioE4 11250` and both head-unit scales
`1.0`, where `main` produces `Margins 480x360` + `scaleY 1.5` and a visibly vertically-stretched
picture. The round 4 R5 touch regression is gone: a tap on a rendered control's visible centre hits
that control on the candidate.

## Verdict summary

| Run | Verdict | One line |
|---|---|---|
| R0 | **PASS** | candidate `assembleGithubDebug` clean, **1473 tests / 0 failures**; baseline clean, **1414 / 0**. All 6 new policy test classes green; the 4 decisive assertions hold. |
| R1 | **PASS** | D-HU FILL AUTO: `1280x720` / `0x0` / `PAR 11250` / `1.0,1.0` — bit-identical to round 5 R0. |
| R2 | **PASS (decisive)** | D-HU FILL forced 1080p: candidate `1920x1080` / **`0x0`** / **`PAR 11250`** / **`1.0,1.0`**; baseline `1920x1080` / `480x360` / `scaleX 1.333 scaleY 1.5`, rail icons and speed-dial visibly vertical ovals. The #809 squash is closed on the 11250 panel class. |
| R3 | **PASS** | D-POCO touch A/B: candidate `Touch map` uses the top-left-anchor formula (`video_y = raw_y/panelH·uiH`), **no phantom centring offset**; a tap on the visible centre of Maps / Spotify / Phone opens each correctly; AA receives the composed coordinate (`x=95` panel → AA `x=95`). Round 4 R5's `+72`-row regression does not occur. |
| R4 | **PASS + finding** | D-POCO Self Mode 1080p FILL: candidate announces **`PAR 12500` / `Margins 0x0` / `1.0,1.0`**, not round 3-5's `Margins 0x216` / `scaleY 1.25`. Picture correct, touch 1:1 aligned. The 2.222-aspect panel is now described by pixel shape at every resolution, so round 3-5's margin baseline for this panel no longer exists (see Findings). |
| R5 | **PASS** | CONTAIN and COVER plumbed through end to end. D-HU 720p CONTAIN: `scaleX 0.8889` pillarbox, taps in the side bars clamp to the picture edge (`raw=20 -> video=0`, `raw=1420 -> video=1280`). D-HU 720p COVER: `scaleY 1.125`, fills width, crops 45 px top/bottom, touch compensates (`raw y=20 -> video y=58`). Both keep `PAR 10000` / `shape=MARGIN`; neither distorts. |
| R6 | **PASS** | `stretch_to_fill` → `videoFitMode` migration, all four cases: `true`→FILL, `false`→CONTAIN, neither→FILL, `false`+`forced_scale`+`view-mode=SURFACE`→FILL (inverted). Read from the live `fit=` on the touch line and the wire geometry. |
| R7 | **INCONCLUSIVE** | Re-announce-on-drift needs a post-`makeProto` margin change; D-MOTO (the only unit that produces one) is not on the rig, and neither D-HU nor D-POCO drifts its margin after the wire announce. JVM-covered: `MarginAnnouncementPolicyTest` 3/3 in R0; the `onMarginsDiverged` → `reannounceMargins` wiring is present in `AapProjectionActivity`. |
| R8 | **PASS** | `enable-floating-button` absent → getter default `false`; launching `MainActivity` stays on `MainActivity`, no `MANAGE_OVERLAY_PERMISSION` navigation, no button drawn. |

**Discard rule:** every run clean — exactly `1 createGroup SUCCESS`, `0 Magic Garbage`, one P2P
interface index per D-HU run (`p2p-wlan0-0` … `-6`, +1 per session, no second group). The `MATCH!
Starting AapService` on every D-HU Native AA run is the intended `AutoStartReceiver` path on this rig
(§7a). `SSL handshake complete` counts 2 per session on every run (a stable per-session logging
artifact, not a second session — a second session would double `createGroup` too); R5b-cover-dhu
read 1 because the MT50 ring buffer wrapped past it before the final grep (§7a), session was fine.

---

## Setup notes

### Build gate

`hur-wifi-test-scripts/build_hur.sh` + `run_unit_tests.sh`, each under
`thermal_guarded.sh HOT=88 COOL=68`. PC idled at 65 °C; the candidate build touched ~73 °C, no
`STOP`/`CONT` fired. Candidate build 3 m 9 s, baseline 1 m 51 s.

Decisive assertions checked in the candidate's `test-results` XML:

- `MarginStrategyPolicyTest` (7/7): `1440x720 @ _1920x1080` FILL → `PAR`, scales `1.0`, `parE4 11250`;
  `1920x720 @ _1920x1080` FILL → `PAR`, `parE4 15000`; taller-than-buffer and `1024x600` keep `MARGIN`.
- `SystemOptimizerCeilingTest` (6/6): `hardCeiling(1440,720) == _1920x1080` while
  `panelCeiling(1440,720) == _1280x720`; hard ceiling never below the recommendation.
- `ProjectionGeometryPolicyTest` 23/23, `NegotiatedResolutionPolicyTest` 9/9,
  `VideoFitPolicyTest` 8/8, `MarginAnnouncementPolicyTest` 3/3, `TouchCoordinateMapperTest` 6/6.

### `hur-wifi-test-scripts/` inventory and use

- `build_hur.sh`, `run_unit_tests.sh`, `thermal_guarded.sh` — the two builds + two test runs
- `ultrawide_touch_r3.sh` — the connect / decisive-grep / tap / screencap driver, reused unchanged
  for every device run (`MODE=native` for D-HU, `MODE=selfmode` for D-POCO). Its decisive-line grep
  already covers this round's new lines (`PixelAspectRatioE4 is:`, `shape=`, `FORCED (FILL|CONTAIN|COVER)`,
  `Touch map:`, `CarScreen: margins drifted`).
- `set_prefs_runas.sh` — D-POCO pref writes (multi-key, one force-stop, no relaunch)
- `restore_settings.sh` — D-POCO settings restore
- **Added: `r6_set_hu_prefs.sh`** — multi-key pref writer for the *rooted* D-HU, one pass, no
  relaunch. `set_hu_pref.sh` does one key per call and relaunches via `install_and_launch.sh` after
  each, which reinstalls the APK — wrong build mid-A/B and a wasted connect per key. `set_prefs_runas.sh`
  can't be used on D-HU because its `run-as` route needs the app uid and D-HU's `settings.xml` is
  written as root; the new script `sed`s it as root and restores `10174:10174` ownership. Left in place.

### Identity

`ACTION_QUERY_STATE`'s `commit` field still truncates at `data="{"` (rounds 3-5), so identity is the
live APK md5, re-read by `ultrawide_touch_r3.sh` on the drive device before every run — `959467e2`
(candidate) or `aac9797f` (baseline) on all 20 device runs.

### Settings

Backed up and restored **md5-identical** on both devices: D-HU `b3769c5ae5b52ea6e0f1e27d265f0dfb`
(== round 4/5's backup), D-POCO `fcdd883d4e76fbf38963a05fb4229bcc` (== the state found at the start
of this round; round 5 noted the same value). Both devices carry the round-6 candidate APK
(`959467e2`) after this round; D-MOTO untouched.

Both devices started this round on round 5's `82a748de` and already had a `video-fit-mode=0` key
(round 5's candidate `f280892e` carried the setting). R6's migration cases therefore deleted
`video-fit-mode` explicitly and set the legacy `stretch_to_fill` before each connect.

### Deviations from the brief

- **R3 was run on D-POCO Self Mode, not D-HU.** The `injectMotionEvent` instrument the brief names
  is Gearhead's, and on Native AA Gearhead runs on the phone, not the head unit; round 4 R5
  established the D-POCO Self Mode method and this run mirrors it (`resolutionId=2`, FILL, target the
  Spotify launcher icon). A D-HU candidate touch check was folded into R2 (`Touch map` lines below).
- **R3/R4 A/B is not the geometry A/B the brief expected.** The candidate changes D-POCO's Self Mode
  geometry (margin → PAR, see Findings), so R3-base and R3-cand ran on different wire geometry. The
  touch verdict still holds: both hit the correct control for a tap on the rendered icon. R2 on D-HU
  is the clean geometry A/B.
- **R2's reference-circle measurement is visual, not a clean pixel count.** The AA left rail draws a
  translucent background strip that defeats automated disc segmentation; the montages
  (`rail_compare.jpg`, `shape_compare.jpg`) are the evidence, read directly. The decisive form is the
  scale line (`scaleY 1.5` baseline vs `1.0` candidate) as §0 of the template allows.
- **R7 could not run** (no D-MOTO). Reported INCONCLUSIVE with the JVM coverage noted, per the brief's
  own contingency.
- **R8 did not `pm clear`** (that triggers the onboarding wizard and rewrites resolution/DPI/codec,
  §7a). The `enable-floating-button` key was already absent on D-HU, which is the same code path a
  fresh install hits for this setting.

---

## R0 — build and unit-test gate

**PASS.**

```
candidate  e4be4e0e3   assembleGithubDebug: BUILD SUCCESSFUL   testGithubDebugUnitTest: 1473 tests, 0 failures, 0 errors
baseline   12706e26f   assembleGithubDebug: BUILD SUCCESSFUL   testGithubDebugUnitTest: 1414 tests, 0 failures, 0 errors
```

Delta +59 tests, all in the new policy classes. md5 candidate `959467e2` / baseline `aac9797f`.

## R1 — D-HU, FILL, AUTO

**PASS** — bit-identical to round 5 R0.

- Settings: `resolutionId=0`, `video-fit-mode=0`, `pixel-aspect-ratio-e4=10000`, `log-level=0`
- Radio: D-POCO Bluetooth off at launch, on after `createGroup SUCCESS`; connect via `AutoStartReceiver MATCH!`
- Decisive lines:
  ```
  16:53:52.164  [RES_CAP] resolutionId=0 realScreen=1440x720 usable=1440x720 ... chosen=_1280x720 capped=_1280x720
  16:53:52.470  [ServiceDiscovery] NegotiatedResolution is: 1280x720
  16:53:52.477  [ServiceDiscovery] Margins are: 0x0
  16:53:52.478  [ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
  16:53:52.388  CarScreen isSmallScreen: true, scaleFactor: 1.0, portraitScaled: false, shape=PAR, margins: w=0, h=0
  16:53:53.341  [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
  ```
  (one transient `realScreen=1304x720` ~250 ms before the settle — the MT50 connect-time metrics
  wobble, §7a; the decisive `makeProto` fired after it.)
- Picture (`R1.jpg`): full-bleed, rail icon discs and the speed-dial circle read as circles.

## R2 — D-HU, FILL, forced 1080p: the decisive run

**PASS.** Candidate produces the margin-free canvas; baseline produces the squash.

- Settings both arms: `resolutionId=3`, `video-fit-mode=0`, `pixel-aspect-ratio-e4=10000`, `log-level=0`

**R2-cand** (`959467e2`), reproduced twice (`R2-cand`, `R2-cand2`):
```
16:52:59.101  CarScreen isSmallScreen: true, scaleFactor: 1.0, portraitScaled: false, shape=PAR, margins: w=0, h=0
16:52:59.422  [ServiceDiscovery] NegotiatedResolution is: 1920x1080
16:52:59.429  [ServiceDiscovery] Margins are: 0x0
16:52:59.430  [ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
16:53:00.297  [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
[RES_CAP] resolutionId=3 ... chosen=_1920x1080 capped=_1920x1080 changed=false   (hardCeiling did NOT cap)
```
D-HU touch on the candidate at this geometry (from the `R2-cand` taps):
```
[UI_DEBUG] Touch map: raw=40,90  -> video=53,135  view=1440x720 video=1920x1080 margin=0x0 fit=FILL
[UI_DEBUG] Touch map: raw=40,620 -> video=53,930  view=1440x720 video=1920x1080 margin=0x0 fit=FILL
```
`video_x = 40/1440·1920 = 53`, `video_y = 90/720·1080 = 135`, `620/720·1080 = 930` — pure top-left
proportional scale, no offset.

**R2-base** (`aac9797f`):
```
15:42:24.306  [ServiceDiscovery] NegotiatedResolution is: 1920x1080
15:42:24.313  [ServiceDiscovery] Margins are: 480x360
              (no PixelAspectRatioE4 line — main predates it)
15:42:25.381  [UI_DEBUG] Normal Scale. scaleX: 1.3333334, scaleY: 1.5
15:42:24.292  CarScreen isSmallScreen: true, scaleFactor: 1.0, margins: w=480, h=360
```

**Picture.** `rail_compare.jpg` (R2-base | R2-cand2 | R1) and `shape_compare.jpg`: R2-base's left-rail
icon discs and the speed-dial "0 km/h" circle are visibly **taller than wide** (`scaleY 1.5` >
`scaleX 1.333`, net vertical stretch ×1.125). R2-cand2 and R1 render the same elements as circles.
R2-base's map streets read as vertically compressed against R2-cand2's.

## R3 — D-POCO touch A/B (round 4 R5 re-run)

**PASS** — the candidate's touch mapper is aligned with what it renders; the round 4 R5 regression
is fixed.

- Settings both arms: `resolutionId=2`, `video-fit-mode=0`, `log-level=0`, `pixel-aspect-ratio-e4=10000`, Self Mode

**Geometry** (they differ, because the candidate flips this panel to PAR — see Findings):

| arm | NegotiatedResolution | Margins | PAR | scaleY | GhFacetBar VD |
|---|---|---|---|---|---|
| R3-base `main` | `1280x720` | `0x144` | (none) | `1.25` | `109 x 576` |
| R3-cand | `1280x720` | **`0x0`** | **`12500`** | `1.0` | `109 x 720` |

**A/B** — synthetic `input tap`, cross-checked against Gearhead's
`CAR.PROJECTION.PRES: GhFacetBar injectMotionEvent(... x[0]=N, y[0]=N)` and the facet it opened:

| arm | `input tap` (panel) | OHU `Touch map` → | AA `injectMotionEvent` | AA opened |
|---|---|---|---|---|
| **baseline**, tap Spotify | `95, 625` | *(no line)* | `x=50.0, y=333.0` | media `TemplateService` (Spotify) — `R3-base.jpg` |
| **baseline**, tap Maps | `95, 440` | *(no line)* | `x=50.0, y=234.0` | Maps |
| **candidate**, hardcoded `95,625` | `95, 625` | `raw=95,625 -> video=51,417` | `x=64.0, y=417.0` | `TelecomService` (Phone) — see note |
| **candidate**, visible Spotify centre | `95, 540` | `raw=95,540 -> video=51,360` | `x=64.0, y=360.0` | media `TemplateService` (Spotify) — `R3-cand-v2_spotify.jpg` |
| **candidate**, visible Maps centre | `95, 402` | `raw=95,402 -> video=51,268` | `x=64.0, y=268.0` | Maps |
| **candidate**, visible Phone centre | `95, 672` | `raw=95,672 -> video=51,448` | `x=64.0, y=448.0` | `TelecomService` (Phone) — `R3-cand-v2_phone.jpg` |

**What this shows.**

- The candidate's `Touch map` is `video_y = raw_y / panelH · uiH` with `uiH = negotiatedH - marginH`
  and **no centring term**: `625/1080·720 = 417`, `540/1080·720 = 360`. Round 4 R5's regression added
  `+72` here (would have given `489` / `432`); it is gone (the `2a8ce83f` centring change was reverted
  and is not in `e4be4e0e3`).
- The hardcoded `95,625` opened Phone on the candidate **only because the candidate draws a taller
  rail** (720-row canvas, no margin) with Spotify at panel ≈`540` and Phone at ≈`672`; `625` sits
  between them. It is a stale coordinate, not a misalignment: tapping the **visible centre** of each
  icon opens that icon (rows 4-6 above, screencaps attached).
- `x=64` for a `video_x=51` is AA applying `PAR/10000` (`51 × 1.25 = 63.75`) to the received touch to
  get its layout coordinate. It composes correctly: OHU's `raw_x → buffer_x` (`×1280/2400`) and AA's
  `× 1.25` give `raw_x × 1280·1.25/2400 = raw_x × 0.667` into a `1600`-wide effective layout, which
  is the right proportion. R4 confirms it directly (panel `x=95` → AA receives `x=95`).

## R4 — D-POCO Self Mode 1080p, FILL

**PASS + finding.**

- Settings: `resolutionId=3`, `video-fit-mode=0`, `log-level=0`, `pixel-aspect-ratio-e4=10000`, Self Mode
- Decisive lines:
  ```
  15:57:05.174  [RES_CAP] resolutionId=3 realScreen=2400x1080 usable=2400x1080 ... chosen=_1920x1080 capped=_1920x1080
  15:57:05.175  CarScreen isSmallScreen: false, scaleFactor: 1.25, portraitScaled: false, shape=PAR, margins: w=0, h=0
  15:57:05.370  [ServiceDiscovery] NegotiatedResolution is: 1920x1080
  15:57:05.373  [ServiceDiscovery] Margins are: 0x0
  15:57:05.374  [ServiceDiscovery] PixelAspectRatioE4 is: 12500 (10000 = square)
  15:57:08.420  [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
  ```
- Touch (1:1 both axes here — buffer height == panel height):
  ```
  [UI_DEBUG] Touch map: raw=95,540 -> video=76,540   (AA injectMotionEvent x=95.0, y=540.0  → TelecomService)
  [UI_DEBUG] Touch map: raw=95,672 -> video=76,672   (AA injectMotionEvent x=95.0, y=672.0  → TemplateService)
  ```
  `video_x = 95/2400·1920 = 76`; AA expands by PAR to `76 × 1.25 = 95` — **AA receives exactly the
  panel tap coordinate**. `video_y = panel_y` 1:1.
- Picture (`R4-cand.jpg`): dual-pane (Settings + Maps), correctly proportioned, rail discs circular,
  no vertical squash.

**Finding.** The brief expected `Margins 0x216` / `scaleY 1.25` unchanged from round 3-5. The
candidate instead announces `PAR 12500` / `Margins 0x0` on this 2.222-aspect panel, at both 720p (R3)
and 1080p (R4). `MarginStrategyPolicy.select(FILL, 2400, 1080, 1920, 1080)` derives
`(2400·1080)/(1080·1920)·10000 = 12500 > 10000` → `PAR`. This is arithmetically correct (`1920 × 1.25
= 2400`), the picture renders right (`rendered_aspect = derived/announced = 12500/12500 = 1.0`, the
round 5 addendum law), touch is 1:1, and it uses the full `1920×1080` buffer instead of blanking
216 rows. **Not a regression — arguably an improvement — but round 3-5's `Margins 0x216` baseline for
D-POCO's panel no longer exists**, and any future run that expects it on this rig needs updating.

## R5 — CONTAIN and COVER

**PASS.**

### D-POCO 1080p (aspect already matches the margin-reduced canvas, so both collapse onto FILL — round 3 R7)

| sub-run | `video-fit-mode` | shape | Margins | PAR | scaleY | `Touch map` centre |
|---|---|---|---|---|---|---|
| R5a CONTAIN | `1` | `MARGIN` | `0x216` | `10000` | `1.25` | `raw=1200,540 -> video=960,432` |
| R5b COVER | `2` | `MARGIN` | `0x216` | `10000` | `1.25` | `raw=1200,540 -> video=960,432` |

`fit=CONTAIN` / `fit=COVER` present on the touch line. Left/right-edge taps: `raw=95 -> video=76`,
`raw=2350 -> video=1880` (both inside `[0,1920]`, no letterbox on this panel). Picture full-bleed,
undistorted (`R5a-contain-poco.jpg`).

### D-HU 720p (real letterbox: 1.778 buffer in a 2.0 panel)

| sub-run | `video-fit-mode` | NegRes | Margins | PAR | Normal Scale | behaviour |
|---|---|---|---|---|---|---|
| R5a CONTAIN | `1` | `1280x720` | `0x0` | `10000` | `scaleX 0.8889, scaleY 1.0` | pillarbox, ~150 px bars total (`R5a-contain-dhu2.jpg`, black bar visible right edge) |
| R5b COVER | `2` | `1280x720` | `0x0` | `10000` | `scaleX 1.0, scaleY 1.125` | fills width, crops 45 px top + 45 px bottom (`R5b-cover-dhu.jpg`, top controls clipped) |

CONTAIN touch — the picture occupies panel x `80..1360`, taps outside clamp to the picture edge:
```
raw=720,360  -> video=640,360     (centre)
raw=20,360   -> video=0,360       (left bar → clamped to x=0)
raw=1420,360 -> video=1280,360    (right bar → clamped to x=1280)
raw=720,50   -> video=640,50      (no vertical letterbox here)
```
COVER touch — the 45 px cropped-away rows are accounted for:
```
raw=720,360 -> video=640,360
raw=720,20  -> video=640,58       (panel top → 58 rows into the buffer's visible area)
raw=720,700 -> video=640,662
```

Both keep `shape=MARGIN` / `PAR 10000` (`MarginStrategyPolicy` returns `MARGIN` for every non-FILL
mode). No distortion in either.

Incidental: **AUTO + CONTAIN/COVER on D-HU negotiates `1920x1080`, not `1280x720`** —
`NegotiatedResolutionPolicy.autoLadder` counts the panel's long side for non-FILL modes
(`wide = longSide`), so `1440 > 1280` promotes the rung; then `Margins 480x360`, `scaleX 1.333
scaleY 1.5`, `shape=MARGIN`. The margin makes the effective canvas `1440x720` = panel aspect, so
there is still no visible letterbox at AUTO — the real pillarbox needs a manually-pinned 720p.

## R6 — the `stretch_to_fill` → `videoFitMode` migration

**PASS.** D-HU, AUTO, `video-fit-mode` deleted before each connect; effective mode read from the live
`fit=` on the touch line plus the wire geometry.

| sub-run | stored | resolved `fit=` | tell |
|---|---|---|---|
| R6a | `stretch_to_fill=true` | **FILL** | `shape=PAR`, `Margins 0x0`, `PAR 11250` |
| R6b | `stretch_to_fill=false` | **CONTAIN** | `shape=MARGIN`, `NegRes 1920x1080`, `Margins 480x360`, `PAR 10000` |
| R6c | neither key present | **FILL** | `shape=PAR`, `Margins 0x0`, `PAR 11250` |
| R6d | `stretch_to_fill=false` + `forced_scale=true` + `view-mode=0` (SURFACE) | **FILL** | `[UI_DEBUG] FORCED FILL: Resized view to match screen exactly: 1440x720`, `fit=FILL` |

R6d confirms the inverted legacy reading (`forcedScaleActive && !legacyStretch`). All four land on the
mode `VideoFitPolicy.resolve` specifies; nothing defaults wrong.

## R7 — margin re-announce on drift

**INCONCLUSIVE (rig).** The `[UI_DEBUG] CarScreen: margins drifted` / `onMarginsDiverged` path fires
only when the live margins change *after* `ServiceDiscoveryResponse.makeProto` has put them on the
wire. On this rig:

- D-MOTO — the unit that reliably produces a connect-time inset settle (round 3 R9) — was not
  connected this round.
- D-HU's connect-time metrics wobble (`realScreen=1304x720` → `1440x720`, seen in R2/R5/R6) settles
  ~250 ms *before* `makeProto`, so the announced margin is already the settled one; no drift line in
  any of the 8 D-HU sessions with a margin present.
- D-POCO in FILL has no margin (`shape=PAR`); in CONTAIN/COVER the `0x216` margin is stable from the
  first `recalculate`.

JVM coverage stands: `MarginAnnouncementPolicyTest` 3/3 in R0, and the diff wires
`HeadUnitScreenConfig.onMarginsDiverged = ::onMarginsDiverged` (set on lock, cleared on destroy) to
`reannounceMargins()`, which no-ops when `marginsMatchAnnounced()`. Needs D-MOTO for a hardware check.

## R8 — floating button defaults off

**PASS.**

- `enable-floating-button` absent from D-HU `settings.xml` → getter returns the new default `false`.
- `am start MainActivity` → `topResumedActivity` stays `.../main.MainActivity`; no
  `MANAGE_OVERLAY_PERMISSION` / overlay-settings activity in a 6 s capture; `ACTION_GET_SETTINGS`
  does not report the key.

---

## Anything the brief did not ask about

- **`MarginStrategyPolicy` reaches further than "a stored 1080p on a 720-row panel".** It flips
  *any* FILL panel wider than 16:9 to PAR, including D-POCO's 2400x1080 at its normal `resolutionId`.
  On this rig that removed the `Margins 0x144` (720p) and `Margins 0x216` (1080p) baselines that
  rounds 3-5 measured against on D-POCO. The results are all correct (picture, touch, full buffer
  use), but the PAR path is now the common case on both rig panels, not an edge case — worth a
  deliberate look before merge that the `MIN/MAX_PIXEL_ASPECT_E4` clamp (5000-20000) and the 3 %
  dead-band are where you want them for the panels in the field.

- **PAR composes with AA's touch handling exactly right.** AA multiplies the received touch X by
  `announcedPAR/10000` to reach its layout space. OHU sends buffer-space X. The product is the
  correct panel-proportional coordinate — measured cleanly in R4 (panel `x=95` → AA `x=95`) and R3.
  No OHU-side change is needed for horizontal touch on a PAR panel, and none is present.

- **The new log lines all work:** `[ServiceDiscovery] PixelAspectRatioE4 is: N (10000 = square)`,
  `shape=PAR|MARGIN` on the `CarScreen` line, `[UI_DEBUG] Touch map: raw=… -> video=… … fit=…`
  (verbose, one per pointer), `[UI_DEBUG] FORCED FILL|CONTAIN|COVER`. Useful for the next reporter
  log — ask for Verbose.

- **D-POCO SoC thermals** — the app's own overlay read `Temp: 82 C`, `Frame: 41-1174 ms` during the
  Self-Mode runs (worst on the Phone-app screencap). Static screencaps and logged coordinates are
  unaffected; no bearing on the verdicts.

- **`hardCeiling` behaves.** D-HU forced 1080p (`resolutionId=3`) negotiated `_1920x1080`
  `capped=_1920x1080 changed=false` every time — round 4 R4's "manual 1080p capped to 720p" does not
  happen on `e4be4e0e3`.

- **The 8:3 panel is still not on the rig.** R3-cand's `PAR 12500` and the R0 assertion `parE4 15000`
  for a `1920x720` panel are the closest evidence; a real 8:3 unit negotiating `15000` from its own
  geometry with a live Gearhead session still needs Sesam17's build (brief §10).
