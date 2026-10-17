# ultrawide-touch-alignment — round 4 results

**Candidate:** `fix/video-fit-and-ultrawide-touch` @ `2a8ce83fd2299f2f49081c7f6724d47a91164e9d` (2 commits on `main`)
**Baseline:** `main` @ `12706e26f`
**APK md5:** candidate `3c57e5300385248206202b0d03295645` / baseline `1d7f36d71b24ded4d40b123860f43f35` (different)
**Live `commit` fingerprint:** `ACTION_QUERY_STATE` stdout still truncates at the first inner quote
(`data="{"`, same as round 3); identity carried by the **live APK md5**, re-read on every device
before every run, plus the `portraitScaled:` arm tell (present on every candidate run, absent on
R2's baseline run).
**Units:**
- **D-HU** — UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, panel 1440x720 (aspect 2.0),
  view-mode GLES, Native AA (`wifi-connection-mode=3`), POCO X3 NFC as the projecting phone —
  R1/R2/R3/R4/R9
- **D-POCO** — POCO X3 NFC (`M2007J20CG`), Android 15, 2400x1080 landscape, Self Mode — R5
- **D-MOTO** — Moto edge 30 neo (`motorola_edge_30_neo`), Android 14, 2400x1080 landscape,
  Self Mode — R8
**Date:** 2026-09-07

## Verdict summary

| Run | Verdict | One line |
|---|---|---|
| R0 | **PASS** | candidate 1458/0, baseline 1414/0, md5s differ |
| R1 | **PASS** | D-HU AUTO candidate: `_1280x720`, `0x0`, `PAR 11250`, `1.0/1.0`, 5 taps exact to round 3 |
| R2 | measurement | D-HU AUTO baseline: `_1920x1080`, `480x360`, `1.3333334/1.5`, no PAR line; picture full-bleed and undistorted to the eye |
| R3 | **PASS** | D-HU manual 720p candidate: byte-identical to round 3's R1 and to this round's R1 |
| R4 | **INCONCLUSIVE** | candidate's new panel ceiling caps manual 1080p → 720p on a 720-row panel, so no margin is ever produced on D-HU; the margin-present touch-centring path is unreachable here |
| R5 | **FAIL** | D-POCO A/B: baseline tap hits the intended control; candidate tap lands one rail slot lower (AssistantScrim, not Spotify) — displaced down by exactly half the announced margin. AA anchors its canvas at the buffer's top-left; the old mapper was right |
| R6, R7 | not run | brief: stop the D-POCO runs on an R5 FAIL |
| R8 | **INCONCLUSIVE** (tap A/B) | geometry cross-checked (matches D-POCO / round 3); the tap A/B could not run — the Moto's digitizer sprayed continuous phantom touches, operator-confirmed |
| R9 | matches brief | D-HU resolution picker now reads **"720p (Recommended)"** where `main` says 1080p; DPI screen over-panel warning cites 720p |

**The round does not clear the candidate.** R5 is a real regression in the touch mapper, present
on every panel that gets a margin (every 2400x1080 panel, and the reporter's 1920x720). R1/R3 pass
only because the MT50's margin is `0x0`, where the centring change is a no-op. R9 confirms the
ladder's visible half works; the video-fit / PAR half is intact everywhere it was observed. The
defect is isolated to `TouchCoordinateMapper` / `AapProjectionActivity`'s canvas-centring change in
commit `2a8ce83f`.

---

## Setup notes

### Build gate and thermal

The PC is still thermal-throttling (flagged since round 3). All four gradle invocations
(candidate assemble + test, baseline assemble + test) were run under
`hur-wifi-test-scripts/thermal_guarded.sh HOT=88 COOL=68`. It `SIGSTOP`d the gradle tree at
88–90 °C several times per build and `SIGCONT`d at ≤68 °C; every build returned `BUILD SUCCESSFUL`,
no truncated logs. Idle package temp started the session at 57 °C (better than round 3's day).

Unit counts (from `app/build/test-results/testGithubDebugUnitTest/*.xml`, 137 classes):
- candidate `testGithubDebugUnitTest`: **1458 / 0** (failures 0, errors 0, skipped 0) — exactly the brief
- baseline `testGithubDebugUnitTest`: **1414 / 0** — exactly the brief

### `hur-wifi-test-scripts/` inventory and use

Used existing, nothing added or changed:

- `build_hur.sh`, `run_unit_tests.sh` — both arms (checkout, build, stash APK, checkout other, build)
- `thermal_guarded.sh` — wrapped every gradle call
- `set_hu_prefs.sh` — D-HU multi-key settings (rooted, one force-stop, no relaunch)
- `set_prefs_runas.sh` — D-POCO / D-MOTO multi-key settings (non-rooted, pushed inner script)
- `ultrawide_touch_r3.sh` — round 3's one-connect driver, carried unchanged with
  `OUTDIR=round-ultrawide-touch-r4`; `MODE=native` for D-HU (phone-BT off→on connect),
  `MODE=selfmode` for the phones, `TAGFILTER="OPENHU:V *:S"` on D-MOTO
- `restore_settings.sh` — settings restore on the two non-rooted phones

Full captures and full-res `screencap` PNGs on the rig under
`hur-wifi-test-scripts/round-ultrawide-touch-r4/`. Photos (downscaled) in
`ultrawide-touch-alignment-round4-photos/`.

### Per-run commit / PAR read-back / resolution landing

| Run | live APK md5 | PAR read-back | resolutionId → chosen / capped |
|---|---|---|---|
| R1 | `3c57e530` (cand) | `10000` | `0` → `_1280x720` / `_1280x720` ✓ |
| R2 | `1d7f36d7` (base) | `10000` | `0` → `_1920x1080` / `_1920x1080` ✓ |
| R3 | `3c57e530` (cand) | `10000` | `2` → `_1280x720` / `_1280x720` ✓ |
| R4 | `3c57e530` (cand) | `10000` | `3` → `_1920x1080` / **`_1280x720`** (`changed=true`) — **did not land where the brief asked**; see R4 |
| R5 base | `1d7f36d7` (base) | `10000` | `2` → `_1280x720` / `_1280x720` ✓ |
| R5 cand | `3c57e530` (cand) | `10000` | `2` → `_1280x720` / `_1280x720` ✓ |
| R8 | `3c57e530` (cand) | `10000` | `2` → `_1280x720` / `_1280x720` ✓ |

`skipped` list from settings imports: none — every `set_hu_prefs.sh` / `set_prefs_runas.sh` write
verified by reading the keys back before the run.

### Deviations from the brief and the protocol

- **R4 resolution did not land as briefed** (capped 1080p → 720p). Full analysis in the R4 section;
  this is the round's second finding.
- **R6, R7 not run.** The brief's R5 instructions: *"Baseline activates it and the candidate does
  not. This is a FAIL … Do not try to work around it; report it, with both screencaps, and stop
  the D-POCO runs there."*
- **R8 tap A/B not run** — the Moto's touch digitizer sprayed continuous unsolicited
  `sendTouchEvent` lines from the moment of connect (`raw=92,937`, `raw=1258,70`, `raw=917,92`, …,
  dozens, with no harness input). Operator confirmed *"MOTO touch points were off"* and, separately,
  that the POCO's physical touch was also miscalibrated (*"POCO touch too"*, *"both were higher than
  the actual icon"*). **This does not affect R5's verdict:** R5 used `adb shell input tap`, which
  injects synthetic events at exact logical coordinates and bypasses the digitizer entirely — and
  the decisive numbers are read from Android Auto's own
  `CAR.PROJECTION.PRES: GhFacetBar injectMotionEvent(... x[0]=N, y[0]=N)` lines, i.e. the coordinate
  AA actually received, not a physical touch. R8's connect still produced valid geometry
  (margins / scale / PAR), recorded below.
- **R9 driven partly through the settings UI.** The in-app "Search settings" field
  (`id/settingsSearch`, TESTING-TEMPLATE §2's sanctioned no-scroll route) to reach the Resolution
  row and the DPI row; one downward swipe on the **DPI sub-screen** (not the settings list) to bring
  the over-panel warning text into view. No settings-list scrolling.
- **D-HU settings restore** — the first restore attempt's `chown $(stat …)` subshell hit a
  `/system/bin/sh: syntax error: unexpected '('`. The `cp` had already succeeded; re-ran
  `chown u0_a174:u0_a174` + `chmod 660` explicitly. **Final `settings.xml` is md5-identical to the
  pre-round backup on all three devices** (D-HU `b3769c5a…`, D-POCO `a0d036a8…`, D-MOTO `e7270fbc…`).
- **`5GHz createGroup SUCCESS!` once per Self-Mode connect on D-POCO** — round 3 already noted this
  is not a discard-rule hit (the rule is a *second* group in one run).
- Candidate APK left installed on all three devices (an upgrade from round 3's `0b6497b4` build).

---

## R0 — build gate

**PASS**

- Candidate `assembleGithubDebug` + `testGithubDebugUnitTest`: `BUILD SUCCESSFUL`, **1458 / 0**.
- Baseline the same: `BUILD SUCCESSFUL`, **1414 / 0**.
- APK md5s recorded and different: candidate `3c57e5300385248206202b0d03295645`,
  baseline `1d7f36d71b24ded4d40b123860f43f35`.

---

## R1 — D-HU on AUTO, candidate

**PASS** — every required line met.

- Settings: `resolutionId=0`, `video-fit-mode` absent, `pixel-aspect-ratio-e4=10000`, `log-level=0`.
- Radio: POCO Bluetooth off at launch (`svc bluetooth disable`), on after `createGroup SUCCESS`;
  connect via `AutoStartReceiver` `MATCH!` (the intended path on this rig — TESTING-TEMPLATE §7a,
  round 3 setup notes).
- Discard-rule: clean — 1 `createGroup SUCCESS`, 1 `SSL handshake complete`, 1 `MATCH!` (expected),
  0 `Magic Garbage`, `p2p-wlan0-1` (single).
- Decisive lines:
  ```
  21:04:29.469 [RES_CAP] resolutionId=0 realScreen=1440x720 ... chosen=_1280x720 capped=_1280x720
  21:04:29.485 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  21:04:29.492 [ServiceDiscovery] Margins are: 0x0
  21:04:29.493 [ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
  21:04:30.470 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
  21:04:29.471 [UI_DEBUG] CarScreen isSmallScreen: true, scaleFactor: 1.0, portraitScaled: false, margins: w=0, h=0
  ```
- Taps (from `[UI_DEBUG] Touch map:`, all `margin=0x0 fit=FILL`):

  | tap (raw) | video (measured) | video (brief) |
  |---|---|---|
  | 40,40 | 36,40 | 36,40 |
  | 1400,40 | 1244,40 | 1244,40 |
  | 40,680 | 36,680 | 36,680 |
  | 1400,680 | 1244,680 | 1244,680 |
  | 720,360 | 640,360 | 640,360 |

- **Photo `R1.jpg`:** full-bleed edge-to-edge, no bars, split media/map dashboard, every circular
  UI element (left-rail app icons, `+`/`−`, km/h dial, settings gear, compass) reads as a circle —
  undistorted. Indistinguishable from round 3's R1.

Every number and every tap identical to round 3's R1 / this round's R3. This is the configuration
the whole change exists to produce, and it is correct on this panel.

---

## R2 — D-HU on AUTO, baseline

**Measurement (not PASS/FAIL). The reference R1 is read against.**

- Baseline APK (`1d7f36d7`). Settings: `resolutionId=0`, `video-fit-mode` absent, `PAR 10000`,
  `log-level=0`. Radio/connect as R1.
- Discard-rule: clean — 1 `createGroup SUCCESS`, 1 SSL, 1 `MATCH!` (expected), 0 `Magic Garbage`,
  `p2p-wlan0-0`.
- Decisive lines:
  ```
  21:02:45.861 [RES_CAP] resolutionId=0 realScreen=1440x720 ... chosen=_1920x1080 capped=_1920x1080
  21:02:45.877 [ServiceDiscovery] NegotiatedResolution is: 1920x1080
  21:02:45.884 [ServiceDiscovery] Margins are: 480x360
  21:02:46.864 [UI_DEBUG] Normal Scale. scaleX: 1.3333334, scaleY: 1.5
  21:02:46.862 [UI_DEBUG] CarScreen isSmallScreen: true, scaleFactor: 1.0, margins: w=480, h=360
  ```
  `CarScreen` line has **no `portraitScaled:` field** (baseline tell). **No `PixelAspectRatioE4 is:`
  line** — baseline does not emit one. `chosen=_1920x1080`, `Margins are: 480x360`,
  `scaleX: 1.3333334, scaleY: 1.5` — exactly the brief's expectation.
- Transient: first `[RES_CAP]` of the run reads `realScreen=1304x720` (margins w=616) before
  settling to `1440x720` ~230 ms later; the decisive `makeProto` fired after the settle. Same mild
  metrics wobble the MT50 showed in round 3.

**Photo `R2.jpg`, read plainly per the brief:** the picture is **full-bleed, no visible bars, and
undistorted to the eye** on this panel. The AA left-rail circular icons, the `+`/`−` buttons and the
km/h dial all read as circles. Despite the announced `480x360` margin and the non-uniform
`1.3333334 / 1.5` scale, the rendered result on this 1440x720 (PAR ≈ 1.125, close to square) panel
is not visibly worse than R1's. **On this rig, the AUTO baseline picture is as good as the
candidate's.** The candidate's real gain here is that it negotiates a `1280x720` buffer the panel
uses fully, where the baseline asks the phone for `1920x1080` and margins off `480x360` of it. The
correction that matters — a panel far from square, like the reporter's 1920x720 (PAR 1.5) — cannot
be shown on this rig.

---

## R3 — D-HU at manual 720p, candidate

**PASS** — the regression anchor.

- Settings: `resolutionId=2`, `video-fit-mode` absent, `PAR 10000`, `log-level=0`. Radio/connect as R1.
- Discard-rule: clean — 1 / 1 / 1 (`MATCH!` expected) / 0, `p2p-wlan0-2`.
- Decisive lines:
  ```
  21:05:54.440 [RES_CAP] resolutionId=2 realScreen=1440x720 ... chosen=_1280x720 capped=_1280x720
  21:05:54.457 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  21:05:54.464 [ServiceDiscovery] Margins are: 0x0
  21:05:54.465 [ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
  21:05:55.354 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
  ```
- Taps (all `margin=0x0 fit=FILL`): `36,40` / `1244,40` / `36,680` / `1244,680` / `640,360` —
  **byte-identical to round 3's R1 and to this round's R1.** The margin is zero, the touch-centring
  change is a no-op here, and nothing moved. No regression against a measured result.

---

## R4 — D-HU at manual 1080p, candidate

**INCONCLUSIVE** — the run executed, but this candidate cannot produce the signal the run needs
(a non-zero margin) on a 720-row panel.

- Settings: `resolutionId=3`, `video-fit-mode` absent, `PAR 10000`, `log-level=0`. Radio/connect as R1.
- Discard-rule: clean — 1 / 1 / 1 (`MATCH!` expected) / 0, `p2p-wlan0-4`.
- Decisive lines:
  ```
  21:07:05.022 [RES_CAP] resolutionId=3 realScreen=1440x720 ... chosen=_1920x1080 capped=_1280x720 changed=true
  21:07:05.038 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  21:07:05.046 [ServiceDiscovery] Margins are: 0x0
  21:07:05.048 [ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
  21:07:05.938 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
  ```
- Taps (all `margin=0x0 fit=FILL`): `36,40` / `1244,40` / `36,680` / `1244,680` / `640,360` —
  identical to R1 / R3, **not** the brief's `280,220 / 1640,220 / 280,860 / 1640,860 / 960,540`.

**Why the brief's R4 table cannot be produced.** The candidate applies a **panel ceiling to every
resolution choice, manual selections included** —
`HeadUnitScreenConfig.recalculate()` (≈ lines 352–357):

```kotlin
val panelCeiling = autoResolutionForPanel(realScreenWidthPx, realScreenHeightPx, …)
if (pixelsOf(negotiatedResolutionType) > pixelsOf(panelCeiling)) {
    negotiatedResolutionType = panelCeiling
}
```

`SystemOptimizer.panelCeiling(1440, 720, …)` returns `_1280x720` (short side 720, not `> 720`), so a
manual `resolutionId=3` is silently capped to 720p, `NegotiatedResolution` is `1280x720`, the height
margin is `0`, and `scaleX/scaleY` stay `1.0`. Round 3's candidate `0b6497b4` did **not** cap manual
selections (its R2 logged `resolutionId=3 → capped=_1920x1080`). The cap is new in `2a8ce83f` and
deliberate — the code comment cites issue #767 and the goal of the runtime cap agreeing with the
wizard and the DPI screen (see R9).

**Consequence:** no resolution setting produces a non-zero margin on D-HU with this candidate, so
"the touch centring change with a margin present, on the head unit" is not testable here. That
coverage now lives entirely in **R5** (D-POCO, real margins) and the JVM
`TouchCoordinateMapperTest` / `ProjectionGeometryPolicyTest`.

- **Photo `R4.jpg`:** full-bleed, undistorted, navigation active (the centre tap started a route).
  Geometry identical to R1 / R3.
- Noted: 8+ stray `sendTouchEvent` lines after the 5 briefed taps — a short downward drift
  (`raw=1210,437` … `raw=472,1018 → video=420,720` clamped). Most likely the centre tap landing on
  the map and the panel registering momentum, or rig touch noise. All 5 briefed taps logged cleanly
  first. Not a discard condition.

---

## R5 — D-POCO alignment A/B: the decisive run

**FAIL.** *"Baseline activates it and the candidate does not"* — and the candidate activates a
different control, the one directly below the target, displaced by exactly half the announced margin.

### Geometry (both arms), `resolutionId=2`, FILL, Self Mode

```
[RES_CAP] resolutionId=2 realScreen=2400x1080 ... chosen=_1280x720 capped=_1280x720
[ServiceDiscovery] NegotiatedResolution is: 1280x720
[ServiceDiscovery] Margins are: 0x144
[UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.25
```

Candidate additionally: `PixelAspectRatioE4 is: 10000`, `CarScreen … scaleFactor: 1.875,
portraitScaled: false, margins: w=0, h=144`. Baseline: no PAR line, `CarScreen … scaleFactor: 1.875,
margins: w=0, h=144` (no `portraitScaled:` — arm tell). Geometry is exactly round 3's R5. Both
`Margins are: 0x144`, `scaleY: 1.25`, `scaleX: 1.0`.

Discard-rule, each connect: 1 `createGroup SUCCESS`, 1 SSL, 0 `MATCH!`, 0 `Magic Garbage`.

### The A/B

**Target:** the Spotify launcher icon in Android Auto's left rail (`GhFacetBar`), drawn on the panel
at ≈ `(95, 620)`. About 66 px tall, with ≈ 160 px of empty rail directly above it (between it and the
Maps icon at ≈ y440) and the Assistant "star" button below it at ≈ y760. A miss up **or** down hits
empty rail or a different, identifiable control.

Every coordinate below is synthetic (`adb shell input tap`) and cross-checked against Android Auto's
own `CAR.PROJECTION.PRES: GhFacetBar injectMotionEvent(… x[0]=N, y[0]=N)` line — the coordinate AA
received — and against what AA then did (`GH.IntentProcessor` for a media facet, `AssistantScrim
setup` for the Assistant). None of it depends on the phone's physical digitizer.

| arm | `input tap` (panel) | OHU `Touch map` → | AA `injectMotionEvent` received | AA response | screencap |
|---|---|---|---|---|---|
| **baseline** | `95, 625` | *(baseline emits no Touch map line)* | `x=50.0, y=333.0` | `GH.IntentProcessor: Media intent … com.spotify.music/…AndroidAutoService` | **Spotify opened** — `R5baseTap.jpg` |
| **candidate** | `95, 625` | `raw=95,625 -> video=51,405  margin=0x144 fit=FILL` | `x=51.0, y=405.0` | `[…/AssistantScrim] setup … createVirtualDisplay 1280x576` | **Assistant activated** (target NOT hit) — `R5candTap.jpg` (map unchanged; scrim transient) |
| **candidate (confirm, +135 px up)** | `95, 490` | `raw=95,490 -> video=51,333  margin=0x144 fit=FILL` | `x=51.0, y=333.0` | `GH.IntentProcessor: Media intent … com.spotify.music/…AndroidAutoService` | **Spotify opened** — `R5candConfirm.jpg` |

### What this shows

- Android Auto draws its UI **anchored to the top of the negotiated buffer** — content in buffer
  rows `0..576`, the announced `144`-row margin at the **bottom**. Spotify's facet sits at buffer
  `y ≈ 333`. The head-unit renderer scales that 576-row canvas ×1.875 to the 1080-row panel, so
  Spotify appears at panel `y ≈ 333 × 1.875 ≈ 624` — which is exactly where the screencap shows it.
- The **baseline** touch mapper measures from the buffer's top-left: panel `625` → buffer `333`.
  Correct: it hit Spotify, confirmed by AA's `Media intent` line.
- The **candidate** touch mapper assumes the canvas is **centred** in the buffer (72 rows top,
  72 bottom): panel `625` → buffer `72 + 333 = 405`. That `+72` offset is phantom — AA never used
  it — so the tap lands 72 buffer rows / 135 panel px **too low**, on the Assistant button one rail
  slot below Spotify (`AssistantScrim setup` in the log).
- Same-arm control: the candidate needs the tap moved **135 px up** (`panel 490 → buffer 333`) to hit
  the intended target. The offset is exactly **half the announced `144`-row height margin**.

The brief's model ("the renderer scales about the view centre; the mapper measured from the buffer's
top-left") is **inverted** for this Android Auto build. AA anchors top-left. The **old (baseline)
mapper was right**, and the candidate's canvas-centring change in commit `2a8ce83f`
(`input/TouchCoordinateMapper.kt`, `aap/AapProjectionActivity.kt`) is a pure regression: it
displaces every touch on a margin-present panel downward by half the margin. Per the brief: *"the
fix belongs in the renderer instead"* — or the touch-centring change is dropped and the mapper left
measuring from the buffer top-left.

**The follow-up design work in §4 of the brief ("whether announcing a margin is honoured at all")
dies with this:** the answer is that AA honours the margin by keeping the bottom `144` rows clear,
not by centring — so anything hung off "the mapper should match a centred renderer" is built on a
false premise.

### Salvaged geometry (incidental, needs no separate run)

The R5 candidate connects also exercised what R7 would have measured — candidate at 720p FILL on a
2400x1080 panel: `Margins are: 0x144`, `scaleX 1.0`, `scaleY 1.25`, `PAR 10000`, `portraitScaled:
false`. Matches round 3's R5/R7 exactly. The ladder + video-fit + PAR half of the change is intact;
only the touch mapper regressed.

**Photos:** `R5base.jpg` (baseline, pre-tap), `R5baseTap.jpg` (baseline, Spotify open),
`R5cand.jpg` (candidate, pre-tap — rail identical to baseline), `R5candTap.jpg` (candidate,
`95,625`, target missed), `R5candConfirm.jpg` (candidate, `95,490`, Spotify open).

---

## R6, R7 — not run

Per the brief's R5 instruction on a FAIL: *"report it, with both screencaps, and stop the D-POCO
runs there."*

---

## R8 — D-MOTO, abbreviated

**INCONCLUSIVE for the tap A/B** (rig limitation, not a code problem). Geometry cross-checked and
consistent.

- Candidate APK (`3c57e530`), `resolutionId=2`, FILL, `PAR 10000`, Self Mode, `log-level` verbose,
  `TAGFILTER="OPENHU:V *:S"`.
- Connect geometry:
  ```
  [RES_CAP] resolutionId=2 realScreen=2400x1080 ... chosen=_1280x720 capped=_1280x720
  [ServiceDiscovery] Margins are: 0x144
  [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
  [UI_DEBUG] CarScreen isSmallScreen: false, scaleFactor: 1.875, portraitScaled: false, margins: w=0, h=144
  [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.25
  ```
  Identical to D-POCO and to round 3's R8.
- **Connect-time inset wobble reproduced** (round 3 saw `0x102` → `0x144`): first `[RES_CAP]` reads
  `realScreen=2237x1080 … margins: w=0, h=102`, settling to `2400x1080 … h=144` within ~150 ms;
  the decisive `makeProto` and every `Touch map` line used the settled `margin=0x144`.
- **The tap A/B could not be run.** From the instant the session came up — with no `input tap` from
  the harness — the Moto's touch digitizer sprayed continuous unsolicited events
  (`raw=92,937 → video=49,572`, `raw=1258,70`, `raw=917,92 → video=489,122`, `raw=787,143`, … dozens
  over ~40 s). Operator confirmed *"MOTO touch points were off"*. A controlled single-point A/B is
  impossible on this unit in this state.
- Discard-rule: 1 SSL, 0 `MATCH!`, 0 `Magic Garbage` (the `OPENHU:V *:S` filter drops the
  wpa_supplicant `createGroup` / `p2p-wlan0` lines by design).
- **Photo `R8cand.jpg`:** full-bleed, undistorted (a phantom touch opened the search dropdown).

R5's controlled A/B already established the mechanism, and it is a property of the Android Auto
protocol (canvas anchoring), not of one phone. A cross-device confirmation adds nothing the R5
`injectMotionEvent` logs did not already prove.

---

## R9 — D-HU, the recommended resolution the wizard offers

**Matches the brief.** 720p where `main` says 1080p — the visible half of the ladder change.

Driven through the in-app "Search settings" field (no list scrolling; one downward swipe on the DPI
sub-screen to reveal the warning). Candidate installed, `resolutionId=3` still set from R4.

- **Resolution picker** (`showResolutionDialog`), title **"Change resolution"**:
  ```
  Auto
  480p
  720p (Recommended)
  1080p
  1440p (2K) · high bandwidth
  2160p (4K) · high bandwidth
  ```
  Screencap `R9-res.jpg`. Exact user-facing string for the PR: **`720p (Recommended)`**
  (`R.string.resolution_recommended_format` = `%1$s (Recommended)`).

- **DPI screen**, over-panel warning (shown because `resolutionId=3` is still selected and the
  ceiling is 720p):
  > *"Your chosen resolution (1080p) is higher than your panel. It is limited to 720p, and the DPI
  > is calculated for that. If it looks off, use the recommended resolution (720p)."*

  Screencap `R9-dpi2.jpg`.

- **Why it changed.** `main`'s `SystemOptimizer.panelCeiling(1440, 720, …)` hits
  `longSide > 1280 || shortSide > 720 -> _1920x1080` (1440 > 1280) → recommends **1080p**. The
  candidate drops the long-side clause and keys on the short side only
  (`shortSide > 720 -> _1920x1080` else `_1280x720`; 720 is not `> 720`) → recommends **720p**.
  `recommendedResolution()` is now a thin wrapper over `panelCeiling()`, the same function the
  runtime cap (R4) and the DPI screen use — all three agree.

---

## Anything the brief did not ask about

- **The candidate's picture (renderer) is correct on every margin-present run.** `R5base.jpg` vs
  `R5cand.jpg` are the same full-bleed, undistorted frame; `R8cand.jpg` likewise. The regression is
  the touch mapper alone — the renderer already draws the canvas where AA puts it (top-anchored).
  A renderer-side "fix" as the brief suggests would *move* a correct picture to match a broken
  mapper; the cleaner path is to drop the mapper's centring offset.

- **The panel ceiling now caps *manual* resolution choices, not just AUTO.** A user who deliberately
  picks 1080p on a 720-row panel silently gets 720p, with the DPI-screen warning (R9) as the only
  signal — there is no dialog at pick time on the runtime path, only in the settings UI
  (`showResolutionTooHighDialog`). Worth a line in the PR: it is the right behaviour, but it is a
  visible change from "you are given what you asked for".

- **R2's finding stands on its own:** on a ~2.0-aspect, near-square-pixel panel the AUTO *baseline*
  picture is not visibly degraded. The ladder change's value on this class of panel is bandwidth
  (negotiating `1280x720` instead of `1920x1080` + `480x360` margin), not visible quality. The
  visible win is on panels far from square, which this rig has none of.

- **Both phones' touch digitizers are miscalibrated** (operator: *"POCO touch too"*, *"both were
  higher than the actual icon"*, *"MOTO touch points were off"*). This is a rig-hardware note for
  future rounds that rely on **physical** touch. It did not affect R5, which used synthetic
  injection and read Android Auto's received coordinates from the log. A round that needs a real
  finger on these phones should treat the digitizer as unreliable until re-checked.

- **MT50 connect-time metrics wobble** again present (R2: `realScreen=1304x720` w=616 → `1440x720`
  w=480; R4 similar). Milder than the phones; the decisive `makeProto` always fired after the
  settle.
