# ultrawide-touch-alignment — round 7 results

**Candidate:** `fix/video-fit-and-ultrawide-touch` @ `409056f4c` (round 6's `e4be4e0e3` + 2 commits)
**Baseline:** `fix/video-fit-and-ultrawide-touch` @ `e4be4e0e3` (round 6's candidate, rebuilt this round)
**APK md5:** candidate `32b52a15b8dd0188932512905e10e647` / baseline `6abf549a9fdd5e896a69d19a89b1ee37`
**Units:**
- **D-HU** — UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, panel 1440x720, OEM side bar on the
  **right** 136 px in Screen mode Normal, view-mode GLES, Native AA (`wifi-connection-mode=3`) —
  R1, R2, R3, R4, R5, R6, R8
- **D-POCO** — POCO X3 NFC (`M2007J20CG`), Android 15, Gearhead `17.5.663214` — the projecting phone
- **D-MOTO** — Moto edge 30 neo, Gearhead `17.5.663234` — R7
**Date:** 2026-09-09 / 2026-09-10

## One-line answer to the question the round exists for

**The candidate announces a measured canvas in every screen mode on D-HU, and the announcement
equals the area the app actually draws into.** In Screen mode Normal it reads
`Anchor: 1304x720 (from WINDOW)` — the window's content area, the panel minus the OEM side bar —
on one `init` per session, with no cache round-trip and no re-init, and it does so identically on a
first connect, an in-process reconnect and a connect after a force-stop.

## Verdict summary

| Run | Verdict | One line |
|---|---|---|
| R0 | **PASS** | candidate `assembleGithubDebug` clean, **1482 tests / 0 failures**; `ProjectionCanvasPolicyTest` 8/8, `TouchCoordinateMapperTest` 7/7 including `the denominator is the surface the events came from`. Identity symbols present on the candidate only. |
| R1 | measurement | D-HU Normal baseline: `Honest Init … Anchor: 1440x720` first, then a **second** init 413 ms later at `Anchor: 1304x720` off `Using cached surface dimensions`; `makeProto` fires 18 ms after that, so the wire got the 1304-derived `PAR 10000`. Two inits, one `Cache invalidated (hash mismatch`. |
| R2 | **PASS (decisive)** | D-HU Normal candidate: **one** `Honest Init | Mode: NONE | Anchor: 1304x720 (from WINDOW)`, equal to the window content area on both axes (0 px error), `PAR 10000` by the dead band from `usable=1304x720`, `Normal Scale 1.0/1.0`, `margins w=0,h=0`, `shape=PAR`. Picture proportions identical to R4's Immersive picture. |
| R3 | **PASS** | D-HU Normal touch A/B: every `Touch map` line reads `view=1304x720` on **both** arms (the content area, not the 1440 display); no tap clamps (bottom tap `video=44,671`, not 720); bottom rail tap opens the split-screen control, top rail tap opens Maps. Gearhead's `injectMotionEvent` receives exactly the mapped coordinate. Candidate byte-identical to baseline. |
| R4 | **PASS** | D-HU Immersive candidate bit-identical to round 6 R1: `1280x720` / `0x0` / `PAR 11250` / `1.0,1.0` / `shape=PAR`, `Anchor: 1440x720 (from WINDOW)`. First attempt discarded on the discard rule (2 `createGroup SUCCESS`), re-run clean. |
| R5a | **PASS + finding** | D-HU STATUS_ONLY: final `Anchor 1440x720` == window frame `[0,0][1440,720]`, `usable=1304x720` == anchor minus the 136 px bar, `PAR 10000`, picture fills the area inside the bar. **Finding:** a 140 ms transient before the surface reading has the canvas shrunk twice (`realScreen=1304x720 usable=1168x720`, `shape=MARGIN, margins: w=112`); it never reached the wire. |
| R5b | **PASS** | D-HU IMMERSIVE_WITH_NOTCH: `Anchor: 1440x720 (from WINDOW)` == frame `[0,0][1440,720]`, `usable=1440x720`, `PAR 11250`, `Normal Scale 1.0/1.0`, full-bleed 1440-wide picture, no letterbox. Never measured on this rig before. |
| R6 | **PASS** | All three connects announce `PixelAspectRatioE4 10000`. Connect 3 (after `am force-stop`) reads `(from WINDOW)`, never `DISPLAY`. Connect 2 (in-process) re-inits at all — `init()` early-returns, so the measured anchor simply carries. Live screen-mode change: see the observation below. |
| R7 | **PASS + finding** | D-MOTO Self Mode CONTAIN: **both rail taps hit the control they were aimed at** — the bottom one opens the app launcher, the top one opens Maps — which is *not* what the review expected. The reason is in the log: the window reading pulls the anchor back to the true `2400x1080`, so the live margin returns to the announced `0x216` and the inert `UpdateUiConfigRequest` no longer matters. Its first `Honest Init` reads `(from DISPLAY)` at `2300x1017`. |
| R8 | **PASS (answered)** | Both cache lines appear exactly once per baseline connect (`Cache invalidated (hash mismatch` then `Using cached surface dimensions`) and **zero times in all seven candidate captures**. The review's ask-1 prediction is confirmed on hardware: the stored hash never matches on a fresh process. |

**Discard rule:** every reported run clean — exactly `1 createGroup SUCCESS` per connect,
`0 Magic Garbage`, one P2P interface index per session. R4's first attempt had 2 `createGroup
SUCCESS` (23:47:35 and 23:48:36) and was discarded; the capture is kept as
`R4-discarded.logcat.txt`. R1's two `p2p-wlan0-N` indices are a stale group torn down *before* the
first `createGroup SUCCESS` (§7a), not churn. `MATCH! Starting AapService` appears 1-2 times per
D-HU run with no group churn attached, the phone's own Bluetooth reconnect, per §7a.

---

## Setup notes

### The baseline was not on the rig, contrary to the router row

All three devices carried `7b5a8bb1389cc0689fef5b9f23305eea`, a build left by another thread, not
round 6's `959467e2`. The brief's instruction to verify per device rather than assume is what caught
it. `e4be4e0e3` was rebuilt this round; its md5 is `6abf549a…`, not round 6's `959467e2…`, because
`build_hur.sh` produces a fresh signature each time. Identity was therefore carried by symbol, not
by matching a historical md5:

| symbol | baseline `6abf549a` | candidate `32b52a15` |
|---|---|---|
| `ProjectionCanvasPolicy` | 0 | 9 |
| `noteWindowContent` | 0 | 1 |
| `use_measured_touch_surface` | 3 | 0 |

The third row is the reverse check the brief did not ask for: the deleted setting is absent from the
candidate's DEX, so an arm cannot be misread in either direction.

### Scripts used, and one added

- `build_hur.sh`, `run_unit_tests.sh`, both under `thermal_guarded.sh HOT=88 COOL=68`. The guard
  fired twice (89 °C at 23:21, 88 °C at 23:27), each time resuming at 68 °C; both builds completed.
- `r6_set_hu_prefs.sh` — every D-HU settings write (rooted, multi-key, one force-stop, no relaunch).
- `set_prefs_runas.sh` — D-MOTO settings writes.
- **Added: `hur-wifi-test-scripts/ultrawide_touch_r7.sh`** — a three-phase driver
  (`PHASE=connect|taps|end`). `ultrawide_touch_r3.sh` is one-shot: `TAPS` must be decided before the
  session exists. This round's touch A/B scores "the *visible* centre of the bottom-most rail icon",
  which can only be read off a screencap of the live arm, so connect and tap had to be separable.
  The `end` phase also captures the framework window frame while the projection is still up, which
  §2 of the brief requires and no existing script does. Left in place.

### Erratum in the existing driver's session-detect pattern (and in the round 6 results' counts)

`ultrawide_touch_r3.sh` waits for `Handshake: SSL handshake complete`. On these builds that exact
string is `AapTransport.handshake`'s line, which is `AppLog.d` — **absent at `log-level=2`**. The
INFO-level line is `AapSslContext.performHandshake | SSL handshake complete.` R1 therefore reported
`session: NOT SEEN in 100s` while the session had in fact formed at ~34 s. The new driver waits for
`SSL handshake complete` without the `Handshake:` prefix, which matches both. Consequence for
reading round 6: its "2 per session" SSL count is a VERBOSE-only artifact (both lines present), and
its INFO runs would have counted 1 for the same healthy session.

### Deviations from the brief

- **Tap 5 is 20 px inside the right edge of the *projection*, not of the panel** (`1284,360`, not
  `1420,360`). On D-HU the panel's right 136 px is the OEM bar, and `1420,360` lands on that bar's
  Home key, which would background the app and end the session. `1284` is the same test against the
  canvas the brief actually cares about. Recorded on both arms, so the A/B is unaffected.
- **The photos are framebuffer screencaps, not camera photographs.** `adb shell screencap` on this
  unit captures the projected video *and* the OEM side bar (visible on the right of every Normal and
  STATUS_ONLY image), so it is a strictly better instrument than a photo here, and scriptable per §0.
  Folder `ultrawide-touch-alignment-round7-photos/`.
- **R8's D-MOTO half could not be run as written.** Round 6's raw D-MOTO captures no longer exist:
  they are not on this branch (only the photos and the results file are) and the rig's
  `round-ultrawide-touch-r3/` working folder is gone. The same question is answered decisively by
  this round's own baseline captures, which are the same build; see R8.
- **R7 needed one operator tap and was the only escalation of the round.** Self Mode on D-MOTO
  cannot start on Gearhead 17.4+ without Android Auto's developer "Start head unit server"
  (`127.0.0.1:5277`), and there is no adb route to that toggle. The run was held, the operator
  tapped it, and the run then completed scripted. Worth folding into §7a: any Self Mode run on a
  phone carrying Gearhead 17.4 or newer has this precondition, and it should be verified with
  `adb shell cat /proc/net/tcp | grep -i :149D` before the round starts rather than discovered by a
  failed launch.
- The brief's §2 ground-truth commands were extended. `dumpsys window windows | grep
  AapProjectionActivity -A 12` stops 6 lines short of the `Frames:` line on this ROM; the driver
  greps the package name with `-A 22` instead.

### The three framework figures, and which one is the canvas

`dumpsys` reports three different rectangles for the projection activity on this unit, and only one
of them is the canvas:

| figure | Normal | STATUS_ONLY | IMMERSIVE / _WITH_NOTCH |
|---|---|---|---|
| `mBounds` (display) | 1440x720 | 1440x720 | 1440x720 |
| `Frames: frame=` (window layout) | `[0,0][1440,720]` | `[0,0][1440,720]` | `[0,0][1440,720]` |
| `mAppBounds` (app configuration) | **1304x720** | 1304x720 | 1304x720 |

The window's layout frame is the whole display in every mode, because the OEM bar is an inset on the
content view rather than a smaller window. The area the app draws into is the content view:
1304x720 in Normal (matching `mAppBounds`), and 1440x720 in the immersive modes, where the bar is
hidden and the anchor moves to 1440 with zero insets. The panel images corroborate it directly — in
Normal and STATUS_ONLY the picture ends at x=1304 with the bar to its right; in IMMERSIVE_WITH_NOTCH
it runs to x=1440. So R2's and R5's "Anchor equals the framework frame" is scored against the drawn
content area, with all three figures quoted rather than one.

### Rig state and settings

`settings.xml` backed up and restored **md5-identical** on D-HU (`06fb1e9478f045a01e59f5c4c1da9987`)
and D-MOTO (`6de146448d39c1d7ad4fbb3c753bec36`). The `use_measured_touch_surface` key was left in
place on both, per the brief, as the marker that the file was not rewritten. Delta found at the
start of the round versus the brief's standing settings: D-HU carried round 6's `resolutionId=3` and
`fullscreen-mode=1`; both were written per run. D-POCO's own settings were not touched (it is only
the projecting phone). Phone-side Bluetooth was the connect lever throughout (§7a: airplane mode is
not toggleable from adb here); the bond was verified present on D-HU before the first run.

**APK left installed:** candidate `32b52a15` on D-HU and D-MOTO; D-POCO still carries the unrelated
`7b5a8bb1` from another thread, which is irrelevant to its role as the projecting phone.

---

## R0 — build and unit-test gate

**PASS**

- Candidate `409056f4c`: `assembleGithubDebug` clean; `testGithubDebugUnitTest` **1482 tests, 0
  failures, 0 errors, 0 skipped** — the gate's exact number.
- `ProjectionCanvasPolicyTest` 8/8, including the round's two decisive assertions:
  `nothing measured yet reads the window metrics outside immersive`,
  `a measured surface outranks the display metrics`,
  `a measurement taken under other settings is ignored`.
- `TouchCoordinateMapperTest` 7/7 (was 6), with the added case
  `the denominator is the surface the events came from`.
- Baseline `e4be4e0e3` built for the A/B; its unit tests were not re-run (the brief asks for the
  candidate only).

---

## R1 — D-HU, Screen mode Normal, baseline (measurement)

- APK `6abf549a`; settings `resolutionId=2`, `video-fit-mode=0`, `log-level=2`, `fullscreen-mode=0`
- Radio: D-POCO Bluetooth off at launch, on 18 s later; connect via `AutoStartReceiver MATCH!`
- Discard-rule check: clean (1 `createGroup SUCCESS`, 0 `Magic Garbage`; the `p2p-wlan0-4` → `-5`
  bump precedes the first `createGroup SUCCESS`)

```
23:33:56.228  HeadUnitScreenConfig: Cache invalidated (hash mismatch: stored=-1588719331, current=-1084159378)
23:33:56.228  HeadUnitScreenConfig: Honest Init | Mode: NONE | Anchor: 1440x720 | Seeded Insets: L0 T0 R0 B0
23:33:56.387  [RES_CAP] resolutionId=2 realScreen=1440x720 usable=1440x720 ... chosen=_1280x720 capped=_1280x720
23:33:56.391  CarScreen isSmallScreen: true, scaleFactor: 1.0, portraitScaled: false, shape=PAR, margins: w=0, h=0
23:33:56.584  [RES_CAP] resolutionId=2 realScreen=1304x720 usable=1304x720 ... locked=true
23:33:56.639  HeadUnitScreenConfig: Settings changed (-1084159378 -> -1084118234). Unlocking resolution.
23:33:56.641  HeadUnitScreenConfig: Using cached surface dimensions: 1304x720 (raw: 1304x720, anchor: 1304x720)
23:33:56.642  HeadUnitScreenConfig: Honest Init | Mode: NONE | Anchor: 1304x720 | Seeded Insets: L0 T0 R0 B0
23:33:56.643  [RES_CAP] resolutionId=2 realScreen=1304x720 usable=1304x720 ... chosen=_1280x720 capped=_1280x720
23:33:56.659  [ServiceDiscovery] NegotiatedResolution is: 1280x720
23:33:56.665  [ServiceDiscovery] Margins are: 0x0
23:33:56.667  [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
23:33:57.582  [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
```

Framework: `mBounds=Rect(0,0-1440,720)`, `mAppBounds=Rect(0,0-1304,720)`, `cur=1440x720 app=1304x720`.

**What the numbers say.** The baseline's *first* answer is the raw display, 1440x720, which is 136 px
wider than the content area on the x axis and exact on y. It is corrected 414 ms later, and the
correction lands 18 ms before `makeProto`, so on this unit the wire got the right number anyway —
by a margin of 18 ms. The correction arrives through the surface cache, which had to be re-written
inside this same session first: the stored entry (`-1588719331`) did not match the live hash
(`-1084159378`), so it was discarded on the fresh process exactly as the round 6 review predicted.

The reporter's 8:3 failure (`Anchor: 1920x642`, a figure that is neither panel nor window) therefore
does **not** reproduce on D-HU's baseline. What D-HU shows instead is the same mechanism arriving in
time: two inits, a discarded cache, and an 18 ms margin between the correction and the announcement.
Photo `R1-base-normal.jpg`.

---

## R2 — D-HU, Screen mode Normal, candidate (decisive)

**PASS**

- APK `32b52a15`; settings as R1
- Discard-rule check: clean (1 `createGroup SUCCESS`, 1 SSL handshake, 1 `MATCH!`, `p2p-wlan0-7`)

```
23:41:12.653  HeadUnitScreenConfig: Honest Init | Mode: NONE | Anchor: 1304x720 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
23:41:12.812  [RES_CAP] resolutionId=2 realScreen=1304x720 usable=1304x720 portrait=false locked=false chosen=_1280x720 capped=_1280x720 changed=false
23:41:12.816  CarScreen isSmallScreen: true, scaleFactor: 1.0, portraitScaled: false, shape=PAR, margins: w=0, h=0
23:41:13.066  [ServiceDiscovery] NegotiatedResolution is: 1280x720
23:41:13.072  [ServiceDiscovery] Margins are: 0x0
23:41:13.074  [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
23:41:13.993  [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
```

Framework, read live with the projection up: `mBounds=Rect(0,0-1440,720)`,
`mAppBounds=Rect(0,0-1304,720)`, `Frames: parent=[0,0][1440,720] frame=[0,0][1440,720]`.

Against the brief's four conditions:

1. `(from WINDOW)` — not `DISPLAY`. **Met.**
2. `Anchor 1304x720` versus the drawn content area `1304x720`: **0 px on both axes** (the display and
   the window layout frame are 1440x720; see the three-figures table in Setup notes).
3. `round(usableW · 720 / (usableH · 1280) · 10000)` = `round(1304 · 720 / (720 · 1280) · 10000)`
   = `round(1304 / 1280 · 10000)` = `round(10187.5)` = **10188**. `|10188 − 10000| / 10000 = 1.88 %`,
   inside the 3 % dead band, so the expected announcement is exactly `10000` — and
   `PixelAspectRatioE4 is: 10000`. **Met.**
4. `Normal Scale. scaleX: 1.0, scaleY: 1.0`, `margins: w=0, h=0`, `shape=PAR`. **Met.** The expected
   fork (canvas below 1280 px wide → `MARGIN`) did not occur: 1304 > 1280.

**One init, not two.** The candidate emits a single `Honest Init` for the whole session where the
baseline emits two, and no `Cache invalidated` / `Using cached surface dimensions` pair at all. That
is the init-guard fix and the hash fix visible in one line count.

**Picture.** `R2-cand-normal.jpg` next to `R4-cand-immersive.jpg`: the rail icon discs and the
`0 km/h` speed disc read as circles in both, at the same proportions; the Normal image is the same
picture with the right 136 px given over to the OEM bar. Nothing is squashed or stretched in either.

---

## R3 — D-HU, Screen mode Normal, touch A/B

**PASS** (candidate); baseline arm reported as a measurement

- Both arms: `fullscreen-mode=0`, `log-level=0`, `resolutionId=2`, `video-fit-mode=0`
- Verdict evidence: Gearhead's own `injectMotionEvent` on D-POCO, plus a `screencap` per tap
- Discard-rule check: clean on both arms

Tap targets were read off each arm's own live screencap; the rail geometry was identical on the two
arms, so the same five points served both.

**Candidate arm** (`Touch map` on D-HU / `injectMotionEvent` on D-POCO / what opened):

| # | target | panel tap | `Touch map` → | `view=` | phone received | result |
|---|---|---|---|---|---|---|
| 1 | bottom-most rail icon | `45,671` | `video=44,671` | `1304x720` | `GhFacetBar x=44.0 y=671.0` | split-screen pane opened |
| 2 | top-most rail icon | `45,249` | `video=44,249` | `1304x720` | `GhFacetBar x=44.0 y=249.0` | full-screen Maps restored |
| 3 | panel centre | `720,360` | `video=707,360` | `1304x720` | *(no inject line)* | map, no control |
| 4 | 20 px inside left edge | `20,360` | `video=20,360` | `1304x720` | `GhFacetBar x=20.0 y=360.0` | map rail edge |
| 5 | 20 px inside right edge of the canvas | `1284,360` | `video=1260,360` | `1304x720` | `gearhead/meb x=1172.0 y=360.0` | map |

**Baseline arm:** byte-identical on all five rows — same `video=`, same `view=1304x720`, same phone
coordinates, same controls opened. The candidate changes nothing here, which is the correct outcome
on a unit whose baseline already reached the right denominator (R1).

Against the brief's three conditions, on the candidate:

1. `view=1304x720` in every line, equal to the drawn content area and **not** the 1440x720 display
   metrics. **Met.**
2. No clamp. The bottom-row tap reads `video=…,671`, 49 rows short of the 720 buffer edge; the
   reporter's signature (a `video` Y equal to the buffer height from a tap that was not at the
   bottom edge) does not occur. **Met.**
3. Both rail taps opened the control they were aimed at (`R3-cand-tap-bottom-rail.jpg`,
   `R3-cand-tap-top-rail.jpg`). **Met.**

Two observations the table does not carry. The **bottom-most rail icon on this Gearhead build is the
split-screen toggle**, not an app launcher, so "which app opened" is "which control acted" for that
row. And tap 5 reaches Gearhead in the **map presentation's own coordinate space**: `1260 → 1172`, a
constant 88 px offset that is the width of Android Auto's own left rail inside the video. Both arms
show it, so it is Gearhead-side composition, not our mapping.

---

## R4 — D-HU, Immersive, regression guard

**PASS** — bit-identical to round 6 R1.

- Candidate only, `fullscreen-mode=1`, `log-level=2`
- Discard-rule check: **first attempt discarded** (2 `createGroup SUCCESS`, 23:47:35.766 and
  23:48:36.484, session formed only after the second); re-run clean (1 `createGroup`, 1 SSL,
  `p2p-wlan0-13`). The discarded capture is kept as `R4-discarded.logcat.txt` and its geometry lines
  were identical to the accepted run's.

```
23:50:11.529  HeadUnitScreenConfig: Honest Init | Mode: IMMERSIVE | Anchor: 1440x720 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
23:50:11.700  [RES_CAP] resolutionId=2 realScreen=1440x720 usable=1440x720 ... chosen=_1280x720 capped=_1280x720
23:50:11.704  CarScreen isSmallScreen: true, scaleFactor: 1.0, portraitScaled: false, shape=PAR, margins: w=0, h=0
23:50:11.981  [ServiceDiscovery] NegotiatedResolution is: 1280x720
23:50:11.988  [ServiceDiscovery] Margins are: 0x0
23:50:11.990  [ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
23:50:12.905  [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
```

Every figure the brief listed matches round 6 R1 exactly: `1280x720`, `0x0`, `11250`, `1.0/1.0`,
`shape=PAR`, `Anchor 1440x720`. Arithmetic: `round(1440 / 1280 · 10000) = 11250`, 12.5 % from square
and so outside the dead band, which is why this mode carries a real ratio where Normal carries
`10000`. The change did not move the panel it had no business moving.

---

## R5 — D-HU, the other two screen modes

### R5a — STATUS_ONLY (`fullscreen-mode=2`)

**PASS + finding**

```
23:52:00.836  Honest Init | Mode: STATUS_ONLY | Anchor: 1304x720 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
23:52:00.996  [RES_CAP] realScreen=1304x720 usable=1304x720 ... locked=false
23:52:01.000  CarScreen isSmallScreen: true, ... shape=PAR, margins: w=0, h=0
23:52:01.069  [RES_CAP] realScreen=1304x720 usable=1168x720 ... locked=true          <- shrunk twice
23:52:01.071  CarScreen isSmallScreen: true, ... shape=MARGIN, margins: w=112, h=0    <- shrunk twice
23:52:01.206  HeadUnitScreenConfig: anchor 1304x720 -> 1440x720, measured on the projection surface
23:52:01.209  [RES_CAP] realScreen=1440x720 usable=1304x720 ... locked=true
23:52:01.211  CarScreen isSmallScreen: true, ... shape=PAR, margins: w=0, h=0
23:52:01.273  [ServiceDiscovery] NegotiatedResolution is: 1280x720
23:52:01.280  [ServiceDiscovery] Margins are: 0x0
23:52:01.281  [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
23:52:01.335  [RES_CAP] realScreen=1440x720 usable=1440x720 ... locked=true
23:52:01.339  CarScreen: canvas moved from the announced 1304x720 to 1440x720; the pixel shape cannot be re-sent
23:52:01.353  [RES_CAP] realScreen=1440x720 usable=1304x720 ... locked=true          <- steady state
23:52:02.212  [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
```

Framework, live: `Frames: parent=[0,0][1440,720] display=[0,0][1440,720] frame=[0,0][1440,720]`,
`mAppBounds=Rect(0,0-1304,720)`.

1. Final `Anchor 1440x720` equals the window frame on both axes, 0 px. **Met.**
2. Steady-state `usable=1304x720` = anchor `1440` minus the 136 px bar, once. The announcement went
   out at `usable=1304x720`, and the picture inside the bar is 1304 px wide. **Met at the wire and
   in the steady state** — but see the finding.
3. Picture fills the area inside the bar, no letterbox the mode did not create
   (`R5a-cand-statusonly.jpg`; the OEM bar is on the right and the map runs up to it). **Met.**

**Finding — the canvas is briefly shrunk twice, on the candidate.** For 140 ms (23:52:01.069 →
23:52:01.206) the anchor is the bar-reduced 1304 *and* the 136 px inset is subtracted from it again,
giving `usable=1168x720` and flipping the strategy to `shape=MARGIN, margins: w=112`. That is
exactly the double-subtraction the brief's condition 2 describes. It is corrected by the projection
surface's own reading and never reaches `makeProto` on this rig, so nothing is announced wrong here
— but the window seeding is what is supposed to prevent it, and in STATUS_ONLY the window reading
(1304, the content area with the status bar inset already applied) and the live inset (136) describe
the same bar twice. On a unit whose surface arrives *after* service discovery, this is the state that
would go out on the wire.

**Second observation — the new instrument fires on a transient.** `CarScreen: canvas moved from the
announced 1304x720 to 1440x720` printed at 23:52:01.339 during a 18 ms inset flap, and the canvas
settled back to `usable=1304x720` 14 ms later, equal to what was announced. The line is `loggedCanvasDrift`-latched,
so it prints once and cannot un-say itself; read it as "the canvas moved at least once", not as "the
session is running mismatched".

### R5b — IMMERSIVE_WITH_NOTCH (`fullscreen-mode=3`)

**PASS**

```
23:53:58.272  Honest Init | Mode: IMMERSIVE_WITH_NOTCH | Anchor: 1440x720 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
23:53:58.443  [RES_CAP] resolutionId=2 realScreen=1440x720 usable=1440x720 ... chosen=_1280x720 capped=_1280x720
23:53:58.447  CarScreen isSmallScreen: true, ... shape=PAR, margins: w=0, h=0
23:53:58.750  [ServiceDiscovery] NegotiatedResolution is: 1280x720
23:53:58.757  [ServiceDiscovery] Margins are: 0x0
23:53:58.759  [ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
23:53:59.629  [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
```

Framework, live: `Frames: … frame=[0,0][1440,720]`. Anchor equals it, 0 px. `usable=1440x720` =
anchor minus zero insets, and there is no second subtraction anywhere in the capture. The picture is
full-bleed to both edges with the OEM bar gone (`R5b-cand-immersive-notch.jpg`). One init, one
`RES_CAP`, no drift line: the cleanest of the four modes.

---

## R6 — the canvas across a reconnect and a force-stop

**PASS**

Candidate, `fullscreen-mode=0`, `log-level=2`, no settings change between the three connects.

| connect | how | `Honest Init` | `[RES_CAP] realScreen=` | `PixelAspectRatioE4` |
|---|---|---|---|---|
| 1 | first after `am force-stop` (R2's capture, pid 29727) | `Anchor: 1304x720 (from WINDOW)` | `1304x720` | **10000** |
| 2 | `headunit://disconnect`, 10 s, in-process reconnect (same pid 29727) | *none — `init()` early-returned* | *none* | **10000** |
| 3 | `am force-stop`, relaunch (new pid 32391) | `Anchor: 1304x720 (from WINDOW)` | `1304x720` | **10000** |

```
connect 2:  23:42:25.559 AutomationMarker: R6c2disconnect
            23:42:35.733 AutomationMarker: R6c2btcycle        (head-unit-side svc bluetooth disable, §7a)
            23:42:55.083 MATCH! Starting AapService via Bluetooth Auto-start...
            23:43:04.101 SSL handshake complete
            23:43:04.463 [ServiceDiscovery] PixelAspectRatioE4 is: 10000
connect 3:  23:43:38.262 AutomationMarker: R6c3forcestop
            23:43:51.011 SSL handshake complete
            23:43:51.234 Honest Init | Mode: NONE | Anchor: 1304x720 (from WINDOW)
            23:43:51.408 [RES_CAP] resolutionId=2 realScreen=1304x720 usable=1304x720
            23:43:51.664 [ServiceDiscovery] PixelAspectRatioE4 is: 10000
```

All three announce the same ratio, and connect 3's source is `WINDOW`, never `DISPLAY`. The
pre-fix expectation the review wrote down — the settled ratio in-process and the pre-settle ratio
again after the force-stop — does not occur, because the fix no longer needs the cache to survive
anything: the window seeds the answer before the first session in a mode. Connect 2 is the other
half of the same result: nothing re-initialises at all, so there is no opportunity to lose the
measurement.

Deviation: the reconnect lever was the **head unit's** own Bluetooth adapter, not the phone's. §7a
records that cycling the phone's radio raises no `ACL_CONNECTED` on this rig while the head unit's
does (it self-reverts in ~14 s); the brief's §3 pointer to the phone would not have produced a
second session.

### The live screen-mode change (observation, no PASS condition)

With the connect-3 session live and projecting, `fullscreen-mode` was rewritten `0 → 1` in
`settings.xml` (root `sed`, app **not** stopped, ownership restored) and `MainActivity` started from
adb.

- adb answered `Warning: Activity not started, intent has been delivered to currently running
  top-most instance.`
- **The session survived**: no teardown, no Android Auto loading screen, video uninterrupted, picture
  identical (`R6-live-mode-change.jpg`, taken 27 s after the write; the OEM bar is still on screen,
  so the app is still in Screen mode Normal).
- **`CarScreen: canvas moved from the announced …` did not print** — 0 occurrences in the whole R6
  capture.
- The file on disk read `fullscreen-mode=1` throughout, while the process kept 0.

So the honest answer to what the instrument was added for is that **this route does not change the
screen mode at all**: `SharedPreferences` are cached in the running process, and an `am start` to a
live task neither re-reads them nor re-creates the activity. Applying a screen-mode change on this
rig requires a `force-stop`, and that restarts the session by definition. A genuine mid-session mode
change is reachable only through the settings UI, which §0 bans, so the "does a screen-mode change
restart the session" question is **INCONCLUSIVE on hardware** and belongs on the JVM side. What is
now measured is the weaker but real fact that a *settings-file* change under a live session is inert
and harmless.

---

## R7 — D-MOTO, CONTAIN control hit

**PASS + finding**

- Candidate `32b52a15` on D-MOTO, Self Mode, `video-fit-mode=1`, `log-level=0`, `resolutionId=3`
  (the round 6 addendum's CONTAIN settings, so the two are comparable)
- Discard-rule check: clean (1 `createGroup SUCCESS`, 0 `Magic Garbage`)
- **Setup deviation, and it needed the operator.** Self Mode could not start at all on the first
  attempt: Gearhead `17.5.663234` takes the 17.4+ route, which needs Android Auto's developer
  "Start head unit server" on `127.0.0.1:5277`, and the app logged
  `SelfMode: Headunit Server (127.0.0.1:5277) is NOT running.` → `SelfMode: All launchers failed`.
  That toggle is UI-only and has no scriptable equivalent, so the run was held until the operator
  tapped it. Everything after that was scripted.

```
00:07:08.241  Honest Init | Mode: IMMERSIVE | Anchor: 2400x1080 (from WINDOW) | Seeded Insets: L0 T0 R0 B0
00:07:08.359  HeadUnitScreenConfig: anchor 2400x1080 -> 1080x2400, measured on the activity window
00:07:08.420  HeadUnitScreenConfig: anchor 1080x2400 -> 2400x1080, measured on the projection surface
00:07:08.639  [ServiceDiscovery] NegotiatedResolution is: 1920x1080
00:07:08.641  [ServiceDiscovery] Margins are: 0x216
00:07:08.642  [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
00:07:09.715  HeadUnitScreenConfig: Settings changed (-1320121677 -> -1320119787). Unlocking resolution.
00:07:09.716  Honest Init | Mode: IMMERSIVE | Anchor: 2300x1017 (from DISPLAY) | Seeded Insets: L0 T0 R0 B0
00:07:09.718  CarScreen: margins drifted from the announced 0x216 to 0x231
00:07:09.720  [UI_DEBUG_FIX] TX UpdateUiConfigRequest: L=0 T=115 R=0 B=116
00:07:09.723  [UI_DEBUG] Normal Scale. scaleX: 0.9999693, scaleY: 1.2720848
00:07:09.733  HeadUnitScreenConfig: anchor 2300x1017 -> 2400x1080, measured on the activity window
00:07:09.735  CarScreen: margins drifted from the announced 0x231 to 0x216
00:07:09.736  [UI_DEBUG_FIX] TX UpdateUiConfigRequest: L=0 T=108 R=0 B=108
00:07:09.738  [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.25
```

Taps, targeted on each icon's visible centre read off the live screencap:

| # | target | panel tap | `Touch map` → | what opened |
|---|---|---|---|---|
| 1 | bottom-most rail icon | `78,1002` | `video=62,802 view=2400x1080 video=1920x1080 margin=0x216 fit=CONTAIN` | **the app launcher grid** (`R7-moto-tap-bottom-rail.jpg`) |
| 2 | top-most rail icon | `78,365` | `video=62,292` same view/margin | **full-screen Maps** (`R7-moto-tap-top-rail.jpg`) |

**The review's expectation does not hold, and the log says why.** The prediction was that with
`UpdateUiConfigRequest` inert, the bottom tap would miss or land on the neighbour, because the phone
would have laid out for the announced `0x216` (927 canvas rows) while the head unit scaled and
mapped for the settled `0x231` (849 rows). On the candidate the settled margin **returns to
`0x216`**: 10 ms after the drift, `anchor 2300x1017 -> 2400x1080, measured on the activity window`
puts the anchor on the real panel, `scaleY` goes back to `1.25` from `1.2720848`, and the second
drift line reads `from the announced 0x231 to 0x216` — back to what went out on the wire. The view
and the phone's canvas therefore agree, and whether `0x8009` moves the phone or not stops mattering.
Round 6's residual (`12721` derived against `11651` announced, 9.2 % too wide) is the same defect
seen from the FILL side, and the same window reading closes it.

**Finding — `(from DISPLAY)` appears here, inside Immersive.** The second `Honest Init` picks the
display metrics and gets `2300x1017`, the settled-but-wrong reading the round 6 addendum measured;
`ProjectionCanvasPolicy`'s rule that Immersive reads the panel returns the display's own idea of the
panel, which on this ROM is not the panel. It is corrected 17 ms later by the window, so nothing
downstream is wrong for more than one frame, but the ranking did fall through to its last resort in
a mode where the brief expects it not to. It is also the second re-init in one session
(`Settings changed (-1320121677 -> -1320119787)`), from a hash change *during* the connect, which is
what re-opened the door to the display reading in the first place.

**Also worth recording:** D-MOTO's first window reading is the *portrait* `1080x2400`
(00:07:08.359), adopted for 61 ms with `chosen=_1080x1920`, before the projection surface corrects
it to landscape. The orientation normalisation in `cachedMeasurement` covers the cache; the live
window reading has no equivalent guard.

---

## R8 — the ask-1 grep

**PASS (answered)**

| capture | build | `Using cached surface dimensions` | `Cache invalidated (hash mismatch` |
|---|---|---|---|
| R1 (Normal, 1 connect) | baseline | 1 | 1 |
| R3base (Normal, 1 connect) | baseline | 1 | 1 |
| R2, R3cand, R4, R5a, R5b, R2frame | candidate | 0 | 0 |
| R6 (2 connects) | candidate | 0 | 0 |

```
R1      23:33:56.228  Cache invalidated (hash mismatch: stored=-1588719331, current=-1084159378)
        23:33:56.641  Using cached surface dimensions: 1304x720 (raw: 1304x720, anchor: 1304x720)
R3base  23:37:39.705  Cache invalidated (hash mismatch: stored=-1084118234, current=-1084159378)
        23:37:40.157  Using cached surface dimensions: 1304x720 (raw: 1304x720, anchor: 1304x720)
```

Exactly one of each per baseline connect, in that order, and the ordering is the mechanism: on the
fresh process the stored hash never matches, the cache is thrown away, and the entry that is used
420 ms later is the one this same session has just re-written from its own surface. R3base's stored
hash (`-1084118234`) is the value R1's session wrote, and it still failed to match on the next
process — the review's code reading, confirmed on hardware, with the anchor being the only thing
that differs.

The candidate's zero counts are expected (the lines no longer exist in that build) and are reported
so an absence is on the record as an absence rather than a missing grep.

---

## Anything the brief did not ask about

- **The reporter's fault does not reproduce on D-HU's baseline, and the round is still decisive.**
  D-HU's baseline announces the correct 1304x720 because its projection surface is measured 75 ms
  before `makeProto`. What the candidate changes here is that the correct number is the *first*
  number, computed from the window before any surface exists, with one `init` instead of two and no
  cache round-trip. On a unit where the surface arrives after service discovery — which is what the
  8:3 capture shows — that timing difference is the whole bug. D-HU can prove the new path produces
  the right answer; it cannot prove the old path is wrong, because on this unit it wins its race.
- **STATUS_ONLY is the mode most worth a second look** (R5a's 140 ms double-shrink). It is the only
  mode of the four where the window reading and the live insets describe the same OEM bar twice.
- **`resolutionId=2` on this panel sits 24 px inside the dead band.** The Normal canvas derives
  `10188`, which rounds to a `10000` announcement; a panel 40 px narrower would derive `9875` and
  still announce `10000`, while one 40 px wider would derive `10500` and announce that. The dead band
  is doing real work on this unit and the round never exercises its edge.
- **The round's most useful result is arguably R7's, and it was queued as a control check.** The
  window reading closes the D-MOTO gap the review had concluded could not be closed on the wire: the
  fix does not re-announce anything, it stops the anchor being wrong in the first place, so the
  announced margin and the live margin end up equal. That is a stronger claim than "the re-announce
  is sent once", and it is measured, not reasoned.
- The AA rail's own 88 px inside the video is a fixed offset in the coordinates Gearhead delivers to
  the map presentation (R3 tap 5). Worth knowing before anyone reads a `meb injectMotionEvent`
  coordinate as a mapping error.
