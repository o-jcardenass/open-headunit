# ultrawide-touch-alignment: round 6 brief

Hosts, and the labels used throughout:

| Label | Unit | Panel | Role |
|---|---|---|---|
| **D-HU** | UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14 | 1440x720, aspect 2.0 | Native AA, projecting phone is D-POCO, view-mode GLES |
| **D-POCO** | POCO X3 NFC (`M2007J20CG`), Android 15 | 2400x1080 landscape | Self Mode |
| **D-MOTO** | Moto edge 30 neo | ~2400x1080 | Self Mode, **R7 only, and only if it can be attached** |

Read `TESTING-TEMPLATE.md` before planning any step; §7a applies in full. This file is append-only;
corrections arrive as new commits and a `git pull` fast-forwards.

Rounds 4 and 5 are the parents. This round does **not** reuse their APK; there is a real code change
and a real A/B. Read `ultrawide-touch-alignment-round4-results.md` and
`ultrawide-touch-alignment-round5-results.md` plus `-round5-addendum.md` first; several expectations
below are "identical to a round 5 number" and R3 is a re-run of round 4's R5, which **failed**.

---

## 0. What changed since round 5, and what this round settles

Round 5 was a buildless probe. It answered its question: **Android Auto honours
`pixel_aspect_ratio_e4` on these phones, strongly and linearly**, and the direction shipped in the
app is right (see the round 5 addendum, not the results file's "inverted" conclusion, which was
fitted from D-POCO alone). Round 5 R0 was the margin-free design already working end to end on
D-HU's 2.0 panel: `Margins 0x0`, both scales `1.0`, `PixelAspectRatioE4 11250`, circles measuring
60x60.

Round 5 left **one thing open**: R0/R1 measured the ratio on a panel the AUTO ladder *already*
hands a margin-free 720p canvas to. A panel that would otherwise be given a margin, a stored 1080p
on a 720-row panel, the whole `#809` "a third of the frame is squashed" class, was never on the rig.

The candidate closes that. `fix/video-fit-and-ultrawide-touch` @ `e4be4e0e3`:

- **`MarginStrategyPolicy`** — in FILL, a panel wider than its buffer now describes itself with the
  pixel ratio **at any resolution**, not just at the rung the ladder already made margin-free. A
  stored 1080p on D-HU used to negotiate `1920x1080` / `Margins 480x360` and squash; it now
  negotiates `1920x1080` / `Margins 0x0` / `PAR 11250` / scales `1.0`.
- **`SystemOptimizer.hardCeiling`** split from `panelCeiling` — the runtime cap no longer silently
  overrides a resolution the settings "Use anyway" dialog let the user pick. Round 4 R4 measured the
  old cap forcing manual 1080p down to 720p on D-HU; that no longer happens.
- **Video fit is FILL / CONTAIN / COVER**, replacing the `stretch_to_fill` boolean and the
  `optimize-ultrawide` toggle. `Settings.videoFitMode` migrates old installs (`VideoFitPolicy`);
  `SettingsBackupManager` keeps the old key so old backups still restore.
- **Touch maps from the buffer top-left** (round 4 R5's finding baked in; the round-4 centring
  change is gone). The two touch code paths in `AapProjectionActivity` are collapsed into one
  `TouchCoordinateMapper.map(... fitMode ...)` call.
- **Margin re-announce** — `HeadUnitScreenConfig.onMarginsDiverged` fires when the live margins
  leave what `ServiceDiscoveryResponse` put on the wire, and re-announces once (round 3 R9's path,
  rebuilt; the old `[UI_DEBUG_FIX] ... sendUpdateUiConfigRequest` line is kept).
- **Floating button defaults off** (`enable-floating-button` false) so a fresh install is not sent
  to the system overlay-permission screen for a feature nobody asked for.

**New log line, every connect:** `[ServiceDiscovery] PixelAspectRatioE4 is: N (10000 = square)` —
first seen in round 5, still the newest line in the app.

---

## 1. R0 — build and unit-test gate

Standard A/B build via `hur-wifi-test-scripts/build_hur.sh` under
`thermal_guarded.sh` (the PC still thermal-throttles; rounds 3-5 all needed it).

- **Candidate:** `fork/fix/video-fit-and-ultrawide-touch` @ `e4be4e0e3` (take the exact SHA from
  this thread's `README.md` router row when you start; the branch may gain a correction commit).
- **Baseline:** `main` @ `12706e26f` (the candidate is this + 3 commits).

Both must build `github` debug clean. Run `run_unit_tests.sh` on **both**. The candidate adds
`MarginStrategyPolicyTest`, `NegotiatedResolutionPolicyTest`, `ProjectionGeometryPolicyTest`,
`SystemOptimizerCeilingTest`, `VideoFitPolicyTest`, `MarginAnnouncementPolicyTest` and extends
`TouchCoordinateMapperTest`. Gate: **candidate green, baseline green**. Record both counts.

Decisive assertions, worth eyeballing in the candidate's test output because they are this round's
whole thesis in JVM form:

- `1440x720` panel at `_1920x1080` FILL → strategy `PAR`, `scaleX == scaleY == 1.0`, `parE4 == 11250`
- `1920x720` panel at `_1920x1080` FILL → strategy `PAR`, `parE4 == 15000`
- `hardCeiling(1440,720)` == `_1920x1080` while `panelCeiling(1440,720)` == `_1280x720`
- a `1024x600` panel and a panel taller than its buffer keep `MARGIN` (the ratio has no
  below-square field evidence yet)

If either build or either unit run fails, **stop and report** — nothing after this means anything.

Identity: `ACTION_QUERY_STATE`'s `commit` field still truncates at `data="{"` (rounds 3-5), so carry
identity by the **live APK md5, re-read per device before every run**. Record both APK md5s.

Standing settings for every run unless a run says otherwise, written to `shared_prefs/settings.xml`
with the app force-stopped (pushed `set_hu_pref.sh` on D-HU which is rooted, `set_prefs_runas.sh` on
D-POCO which is not; never the UI, never scroll the list):

```
log-level = 0          (verbose)
video-fit-mode = 0     (FILL)
```

Back up each device's `settings.xml` first and restore it md5-identical at the end (§7a: D-HU's
shared_prefs is root-owned; D-POCO's differs between rounds, restore to the state you found).

---

## 2. R1 — D-HU, FILL, AUTO: the round 5 R0 regression check

**Setup.** D-HU. `resolutionId = 0` (AUTO), `pixel-aspect-ratio-e4 = 10000`, `video-fit-mode = 0`.
Candidate APK. Connect once by the round 3-5 method (D-POCO Bluetooth off at launch, on after the
group forms; connect via `AutoStartReceiver` `MATCH!`). `screencap` with a circular AA element
visible (map speed dial or the assistant logo).

**Expected — bit-identical to round 5 R0:**

```
[ServiceDiscovery] NegotiatedResolution is: 1280x720
[ServiceDiscovery] Margins are: 0x0
[ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
[UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
```

Circle measurement: ~60x60 px, W/H 1.00 (round 5 R0 numbers). Picture full-bleed, undistorted.

**Verdict.** Matches round 5 R0 within quantisation → **PASS**. Any geometry line moved → the fit
refactor regressed the path that already worked; **FAIL**, keep the full capture.

---

## 3. R2 — D-HU, FILL, forced 1080p: the run round 5 could not do

This is the decisive new run. A stored resolution above the panel's rows on a wide panel: exactly
the `#809` regime, on the rig for the first time.

**Setup.** D-HU. `resolutionId = 3` (1080p, manual), `pixel-aspect-ratio-e4 = 10000`,
`video-fit-mode = 0`. Two arms, one connect each, same AA screen photographed both times:

| arm | APK | 
|---|---|
| R2-base | **baseline** `main` `12706e26f` |
| R2-cand | **candidate** `e4be4e0e3` |

**Expected, R2-cand:**

```
[ServiceDiscovery] NegotiatedResolution is: 1920x1080
[ServiceDiscovery] Margins are: 0x0
[ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
[UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
[RES_CAP] resolutionId=3 ... chosen=_1920x1080 capped=_1920x1080 changed=false
```

Same `PAR`, same scales, same zero margin as R1 — only the negotiated buffer differs (1920x1080 vs
1280x720). The picture must look the **same as R1**: full-bleed, circles round, no vertical squash.

**Expected, R2-base:** capture whatever `main` does and record every line. It will differ, in one
of two ways depending on `main`'s ceiling logic: either `capped=_1280x720 changed=true` (1080p
forced down, round 4 R4's finding) or `NegotiatedResolution 1920x1080` / `Margins 480x360` with a
`scaleY` above 1.0 and a visibly squashed or letterboxed picture. Either way it is the pre-fix
behaviour the reporters describe.

**Verdicts.**

- R2-cand shows `0x0` / `PAR 11250` / `1.0/1.0` and a picture indistinguishable in proportion from
  R1, **and** R2-base shows a margin or a cap and a worse picture: **PASS**. This is the round 5
  open item closed on the rig for the 11250 (`1440x720`-class) panel.
- R2-cand still produces a non-zero margin or a scale off 1.0: **FAIL**, `MarginStrategyPolicy` is
  not reaching this path. Keep the capture.
- R2-cand and R2-base identical: **INCONCLUSIVE** — either `main` already does this (unlikely) or
  the candidate did not take; re-check md5 and re-run.

**Measure, do not eyeball.** Both R1 and R2-cand are `screencap` of the same panel; report the
reference circle's W and H in pixels from each image. They should match.

---

## 4. R3 — D-HU touch alignment A/B: the round 4 R5 re-run

Round 4's R5 **failed**: the centring change put every tap half the announced margin low, one rail
slot down, verified against Android Auto's own `injectMotionEvent` coordinates. That change was
reverted; `e4be4e0e3` maps from the buffer top-left. This run confirms the revert landed and the
touch-path collapse in `AapProjectionActivity` did not regress it.

**Setup.** Method exactly as round 4 R5: synthetic `input tap` (not physical touch, the rig's
digitizer is miscalibrated), verified against `GhFacetBar injectMotionEvent` / the app's own touch
line. On the candidate the instrument is now also `[UI_DEBUG] Touch map: raw=X,Y -> video=X,Y ...`,
verbose, one line per pointer.

Two arms on D-HU, `video-fit-mode = 0` (FILL), `resolutionId = 3` (1080p, so a margin *would* exist
under the old strategy and the mapper is genuinely exercised):

| arm | APK | tap a known AA target (a left-rail app icon) |
|---|---|---|
| R3-base | `main` `12706e26f` | record the tap, the coordinate AA logs receiving, and which control fired |
| R3-cand | `e4be4e0e3` | same tap, same three readings |

Tap near the **top** of the rail and near the **bottom** — round 4 showed the error grows with
distance from the anchor.

**Expected.** Both arms hit the **same** control for the same tap, and the `video=` coordinate the
mapper emits matches what AA logs receiving to within a few px. On the candidate, `Margins are: 0x0`
(R2's strategy), so `video_y == raw_y` scaled straight; on baseline a margin may be present and the
top-left anchor still applies.

**Verdicts.**

- Candidate hits the target control at both top and bottom, coordinates agree with AA's own:
  **PASS**.
- Candidate lands on a neighbouring control, or `video=` disagrees with AA's received coordinate by
  more than ~1 rail slot: **FAIL** — the path collapse regressed the mapping; report the raw tap,
  the emitted `video=`, AA's received coordinate, and the offset in px. This is a shipping gate.
- Session will not stay up on one arm: INCONCLUSIVE, re-run.

---

## 5. R4 — D-POCO Self Mode 1080p, FILL: the margin-panel regression check

D-POCO at 1080p has a real `0x216` margin (round 3 R4, round 5 R2a2). The fit refactor and the
touch-path collapse must not move it.

**Setup.** D-POCO, Self Mode, `resolutionId = 3` (1080p), `video-fit-mode = 0`,
`pixel-aspect-ratio-e4 = 10000`. Candidate APK. Connect, `screencap` the app-dock rail.

**Expected — identical to round 5 R2a2 / round 3 R4:**

```
[ServiceDiscovery] NegotiatedResolution is: 1920x1080
[ServiceDiscovery] Margins are: 0x216
[ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)     (or "unset/absent" on read-back)
[UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.25
```

Rail icon discs W/H 1.00 (round 5: 82x81). Then a touch A/B on this device: one `input tap` on a
rail icon, candidate vs baseline, same control must fire, `video=` must land inside the `0..1080`
buffer with the announced `216` at the bottom left clear (top-left anchor).

**Verdict.** Geometry lines identical to round 5 R2a2 and the tap hits the same control on both
APKs: **PASS**. Any margin/scale drift, or a tap that misses on the candidate but not baseline:
**FAIL**.

---

## 6. R5 — CONTAIN and COVER

Round 1 R5/R6 covered these on the old boolean; round 2 finding 1 flagged that CONTAIN/COVER touch
mapping ignores the real letterbox/crop when the margin is non-zero. The modes are now first-class
(`VideoFitMode.CONTAIN` / `COVER`) and the mapper has an explicit branch per mode.

**Setup.** Candidate APK. Run the pair on **D-HU** (AUTO, so `_1280x720`, a 2.0 panel where CONTAIN
genuinely pillarboxes) and on **D-POCO** (Self Mode, 1080p). For each device and each mode:

| sub-run | `video-fit-mode` | 
|---|---|
| R5a | `1` (CONTAIN) |
| R5b | `2` (COVER) |

Connect, `screencap`, then `input tap` two points: one on a visible AA control, one deliberately in
the letterbox bar (CONTAIN) or in the cropped-away region (COVER).

**Expected.**

- **CONTAIN:** `PixelAspectRatioE4 is: 10000` on the wire (`MarginStrategyPolicy` returns `MARGIN`
  for every non-FILL mode, so no ratio correction). Picture letterboxed, aspect correct, no
  distortion. A tap in the bar clamps to the picture edge (`video` x or y at 0 or max), a tap on a
  control hits it.
- **COVER:** `PixelAspectRatioE4 is: 10000`. Picture fills the panel by cropping, aspect correct,
  no distortion. A tap on a control hits it; the crop offset is applied to the mapping (round 1 R6
  behaviour).
- On D-HU with `forcedScale` **not** set (GLES view-mode) the scaling is the modern
  `ProjectionViewScaler` path; do not set `forced_scale`.

**Verdict.** Both modes on both devices: aspect visibly correct (no stretch), `PAR 10000` on the
wire, and taps land where they look like they should including the clamp case: **PASS**. A stretched
picture, a non-10000 PAR, or a tap that misses by more than the letterbox/crop it should have
accounted for: **FAIL**, name the mode and device.

---

## 7. R6 — the `stretch_to_fill` → `videoFitMode` migration

A shipping-default change: every existing install has `stretch_to_fill` and no `video-fit-mode`.
Buildless — write the old keys, force-stop, connect, read back the resolved mode.

**Setup.** D-HU (rooted, easiest to script). For each row: write only the keys in the "stored"
column into `settings.xml` with the app stopped, **remove `video-fit-mode`**, connect once, and read
the effective mode from the wire behaviour (`Margins` / `PAR` / `scaleY`) and from
`ACTION_GET_SETTINGS`.

| sub-run | stored | expected `videoFitMode` | wire tell (D-HU AUTO 720p) |
|---|---|---|---|
| R6a | `stretch_to_fill=true`, no `video-fit-mode` | FILL | `Margins 0x0`, `PAR 11250` |
| R6b | `stretch_to_fill=false`, no `video-fit-mode` | CONTAIN | `PAR 10000`, pillarboxed |
| R6c | neither key present | FILL | `Margins 0x0`, `PAR 11250` |
| R6d | `stretch_to_fill=false` + `forced_scale=true` + `view-mode=0` (SURFACE) | FILL | forcedScale FILL path, `[UI_DEBUG] FORCED FILL` |

R6d exercises the inverted legacy reading (`forcedScale` active flips the boolean's meaning). If the
SURFACE view-mode will not hold on D-HU, note it and run R6d on D-POCO or mark INCONCLUSIVE.

**Optional sub-arm, R6e (needs the export/import dialog — a house-rule exception, keep it to the
minimum taps and say so):** on D-POCO, hand-craft or reuse an old settings-backup JSON containing
`"stretch_to_fill": false` and **no** `video-fit-mode`, import it, confirm the app resolves CONTAIN
on the next connect rather than losing the setting.

**Verdict.** Every row resolves the expected mode: **PASS**. Any row landing on the wrong mode
(especially R6c defaulting to anything but FILL, or R6d not inverting): **FAIL** — this regresses
the picture for real users on upgrade.

---

## 8. R7 — margin re-announce (D-MOTO if it can be attached)

`onMarginsDiverged` should fire once when the live margins leave the announced ones, log
`[UI_DEBUG] CarScreen: margins drifted from the announced WxH to WxH`, and re-announce via
`sendUpdateUiConfigRequest` exactly once (the "surface change reaches here twice, one send" dedupe).

D-MOTO's connect-time inset settle is the known producer (round 3 R9). **D-MOTO is not currently on
the rig.** If the operator can attach it: candidate APK, Self Mode, `video-fit-mode=0`, connect and
capture. Expect the `margins drifted` line **followed by** exactly one `[UI_DEBUG_FIX] AA is already
running, send corrected via sendUpdateUiConfigRequest`, and no second send for the same drift.

If D-MOTO cannot be attached: try the same on D-POCO (round 5 saw its wobble but it never reached
the wire, so this may be INCONCLUSIVE) and say so. Do not fake it.

**Verdict.** Drift line + exactly one re-announce: **PASS**. Drift line + two or more sends, or a
drift with no re-announce: **FAIL**. No drift producible on any attached device: **INCONCLUSIVE**,
name what was tried.

---

## 9. R8 — floating button defaults off

**Setup.** Any device. Clear the app's prefs (or a genuinely fresh install), launch the app, do not
touch any setting.

**Expected.** `ACTION_GET_SETTINGS` reports `enable-floating-button` false (or absent). The app does
**not** navigate to the system "Display over other apps" permission screen on first resume. No
floating button drawn.

**Verdict.** Button off and no overlay-permission redirect: **PASS**. Button on by default, or the
overlay screen appears unprompted: **FAIL**.

---

## 10. The one thing the rig still cannot answer — a Sesam17 build

Round 5's addendum: **R2b put `15000` on the wire and the phone honoured it exactly**, and `15000`
is precisely what `MarginStrategyPolicy` derives for a `1920x720` panel against a `1280x720` buffer,
the whole 8:3 / 33.3% class. So the *ratio* is covered. What the rig cannot produce is:

1. an actual 8:3 panel negotiating `PAR 15000` **from its own geometry** (not a forced pref), with
   a real Gearhead session on it, and
2. whether `fullscreen-mode` or a stored 1080p on that panel ever announces a margin past ~20%
   (round 5 R3 topped out at 20.0% on D-POCO; the reporters sit at 25-33%).

Sesam17 (issue #809, PR #943) has the only 8:3 unit in the loop and has been a careful reporter.
**The brief for whoever lands this should ask him to test a `fix/video-fit-and-ultrawide-touch`
build:** default settings, FILL, his normal 2.4 GHz / 720p path, and report whether the picture is
no longer "squished" and whether the touch points line up with the icons without touching high.
Give him the `PixelAspectRatioE4 is:` and `Margins are:` lines to capture (Settings → Advanced →
log level → Verbose first). Do **not** manufacture a rig substitute for his result.

---

## 11. What to report

Standard results format (`TESTING-TEMPLATE.md` §7), plus:

1. **Both unit-test counts** (candidate, baseline) and the four decisive assertions from R0.
2. **The `PixelAspectRatioE4 is:` and `Margins are:` line from every single connect**, per run.
3. **R1 vs R2-cand reference-circle pixel measurements**, side by side, and R2-base's full geometry.
4. **R3 / R4 touch A/B**: the raw tap, the `video=` the mapper emitted, the coordinate AA logged
   receiving, and which control fired, for every arm. Numbers, not "aligned".
5. **A one-line answer:** does a stored 1080p on D-HU's wide panel now negotiate a margin-free
   canvas with the correction on the ratio, yes or no.

Settings restored md5-identical on every device touched. Kill each `adb logcat` capture by pid
before the next; confirm none survive at the end.
