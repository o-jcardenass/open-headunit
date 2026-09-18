# ultrawide-touch-alignment — round 2 results

**Candidate:** `fix/video-fit-and-ultrawide-touch` @ `371477b61dbd1fafb42b6c14a0a938bfcdb83293`
**Baseline:** `main` @ `6cd703995f68e04740ef5c2bff1bab05dc9e0c3a`
**APK md5:** candidate `55e5469502a2c68dce3a9f7348cad012` / baseline `ba0037d902039adece0931868841a70d`
(same APKs round 1 built and validated; R0 skipped by request, no rebuild needed)
**Units:** POCO X3 NFC (`M2007J20CG`, Android 15) and Moto edge 30 neo (`motorola_edge_30_neo`,
Android 14), both physically 1080x2400, both used as their own head unit via **Self Mode**
(loopback), both driven over adb, not the MT50.
**Date:** 2026-09-07

## Setup notes

**R0 skipped by request.** Reused round 1's already-built and unit-tested APKs; no rebuild.

**This round tests real phone panels instead of the MT50.** In landscape both phones present as
2400x1080 (aspect 2.222) once their display insets settle — wider than the MT50's 1440x720 (2.0),
and wide enough to cross a threshold in the geometry code neither phone nor the MT50 exercised
together before (see findings below). Both phones run Gearhead 17.5.66xxxx (>17.4), so Self Mode
routes through `SelfLauncherV17_4`, which connects to the phone's own Android Auto dev "Headunit
Server" on `127.0.0.1:5277`. That server has no scriptable start trigger (Android Auto's own UI
only: Developer settings → tap Version 10x → ⋮ → "Start headunit server"), so per house rule #2 the
user started it by hand once on each phone before this round began; confirmed first via the
`SelfMode: Headunit Server ... is NOT running` log line before the tap, then `Launch of 'v17.4+' had
no issues` after.

Both phones are non-rooted: settings written via `set_prefs_runas.sh` (the run-as-based multi-key
writer), sessions driven with the same `AutomationReceiver` broadcasts as round 1. `headunit://exit`
used between runs instead of `force-stop` where a live session needed a clean teardown first (Self
Mode's `SelfLauncherManager` is a long-lived singleton; exiting cleanly avoids leaving its VPN/server
state stuck, per its own watchdog comment).

**Settings restore hit the documented `run-as sh -c 'cp ...'` quirk** (§7a): `cp: Needs 1 argument`
on both phones on the first attempt. Fixed by pushing a one-line script and running it on-device
instead, per the template's own instruction. Both phones' `settings.xml` diff clean against their
pre-round backups afterward.

**Both phones' usable-screen reading is not immediately stable.** Several `[RES_CAP]` lines fire
in quick succession per connection with different `realScreen`/`usable` values (`2237x1080`,
`2300x1017`, `2400x1080`) before settling — a device/ROM-side window-inset animation, not app code
(neither phone is rooted so this can't be a signing/build artifact). Most connections settled at
`2400x1080` before the decisive lines fired; Moto's R4 connection did not (see R4 below). This is a
timing race, not deterministic — same device, same settings, different outcome between attempts.

**Two real findings came out of this round**, both new because neither the MT50 (round 1) nor a
"small screen" panel exercises them. Full detail in "Anything the brief did not ask about".

## POCO X3 NFC

### R1 — baseline at 720p

**PASS** (reproduces, more severely than the MT50)

- Settings: `resolutionId=2`, `log-level=0`, baseline APK, Self Mode.
- Decisive lines:
  ```
  11:50:39.078 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  11:50:39.081 [ServiceDiscovery] Margins are: 0x144
  11:50:42.138 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.25
  ```
- `scaleY: 1.25` — a worse stretch than the MT50's 1.125, exactly matching the hand-derived
  prediction `(2400/1080)/(1280/720) = 1.25` for this panel.

### R2 — candidate at 720p

**PASS**

- Settings: `resolutionId=2`, `video-fit-mode`/`pixel-aspect-ratio-e4` both absent, candidate APK.
- Decisive lines:
  ```
  11:51:49.710 [ServiceDiscovery] NegotiatedResolution is: 1280x720
  11:51:49.713 [ServiceDiscovery] Margins are: 0x144
  11:51:49.713 [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
  11:51:52.786 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
  ```
- **PAR stays 10000 here, unlike the MT50's 11250** — not a bug. This panel (2400 wide) exceeds
  `ProjectionGeometryPolicy.isSmallScreen`'s 1920x1080 threshold, so `fit()` takes the
  scale-to-panel-width branch instead of the MT50's scaleFactor=1.0 branch; that branch's margin
  math always produces a margin-reduced canvas whose aspect exactly equals the panel's, so the PAR
  correction is mathematically a no-op (proven algebraically and confirmed by measurement — see
  findings). Two independent, equally correct mechanisms fix the same underlying bug depending on
  panel size.

### R3 — touch alignment (the point of the round)

**PASS**

Five proportional taps on this panel's 2400x1080 space, FILL, candidate:

| tap (raw) | video (measured) | video (predicted, `(raw−margin)/surface × ui`) |
|---|---|---|
| 40,40 | 21,21 | 21,21 |
| 2360,40 | 1259,21 | 1259,21 |
| 40,1040 | 21,555 | 21,555 |
| 2360,1040 | 1259,555 | 1259,555 |
| 1200,540 | 640,288 | 640,288 |

Every point matches exactly. This is the first time the mapper has been tested with a **non-zero
margin in FILL mode** (round 1's MT50 always had margin 0x0 at 720p) — confirms
`TouchCoordinateMapper` correctly uses the margin-reduced UI height (576, not the raw negotiated
720) for the FILL-mode scale, i.e. `videoY = (rawY/1080) × 576`, not `× 720`.

### R4 — regression guard at 1080p

**PASS**

- Settings: `resolutionId=3`, candidate.
- Decisive lines:
  ```
  11:53:08.256 [ServiceDiscovery] NegotiatedResolution is: 1920x1080
  11:53:08.264 [ServiceDiscovery] Margins are: 0x216
  11:53:08.264 [ServiceDiscovery] PixelAspectRatioE4 is: 10000 (10000 = square)
  11:53:11.298 [UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
  ```
- Clean settle at 2400x1080 this time; matches the standalone pre-round probe reading exactly.

### R5 — CONTAIN

**PASS on the scale numbers; touch mapping does not track the visible letterbox — see findings**

- `Normal Scale. scaleX: 0.8000001, scaleY: 1.0` — matches predicted
  `containScaleFactor(2400,1080,1280,720) × (1280/2400) = 1.5 × 0.5333 = 0.8` exactly.
- All five touch taps returned **identical coordinates to R3 (FILL)** — `21,21`, `1259,21`, `21,555`,
  `1259,555`, `640,288` — despite the view genuinely being pillarboxed 240px each side on screen.
  See "Anything the brief did not ask about" for why, and why this counts as a real defect rather
  than an observation.

### R6 — COVER

**PASS on the scale numbers; same touch-tracking gap as R5**

- `Normal Scale. scaleX: 1.0, scaleY: 1.25` — matches predicted `coverScaleFactor × (720/1080) =
  1.875 × 0.6667 = 1.25` exactly.
- All five taps again identical to FILL/CONTAIN (`21,21` etc.) despite the view genuinely being
  cropped 25% top/bottom.

## Moto edge 30 neo

Same six runs, abbreviated to 1-2 confirmation taps on R5/R6 once the mechanism was established on
the POCO.

### R1 — baseline at 720p

**PASS**

- `[UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.25` — same as POCO (same physical panel).
- `Margins are: 0x102` was logged against a **transient** `realScreen=2237x1080` reading mid-settle,
  not the final `2400x1080` the scale line used — see findings, "stale announced margin".

### R2 — candidate at 720p

**PASS**

- `PixelAspectRatioE4 is: 10000`, `Normal Scale. scaleX: 1.0, scaleY: 1.0` — matches POCO.
- Margins again announced as `0x102` from the same transient reading.

### R3 — touch alignment

**PASS**

Same five taps, same panel, identical results to POCO's R3 (`21,21` / `1259,21` / `21,555` /
`1259,555` / `640,288`), all tagged `margin=0x144` in the touch log — confirming the touch math
uses the **live**, settled margin (144) at the moment of the tap, not the **102** that was actually
announced to the phone at connection time. Two different margin values in play for the same
session: one sent over the wire, a different one used locally. See findings.

### R4 — regression guard at 1080p

**INCONCLUSIVE — device timing race, not a candidate defect**

- `Normal Scale. scaleX: 1.0, scaleY: 1.0619469` — does **not** match the 1080p prediction of
  `1.0`/`1.0`. Back-solving `scaleY = negotiatedHeight/panelHeight` for `1.0619469` gives
  `panelHeight ≈ 1017`, exactly the transient `2300x1017` reading seen fluctuating during connect.
  This specific connection's scale computation fired while the device was still mid-settle, landing
  in the `videoH > panelH` branch instead of the intended `FILL → 1.0` branch. Not comparable to
  POCO's clean R4 or to this round's own R1/R2, which happened to fire after settling. This is the
  device's own inset-animation timing, observed to vary run-to-run on identical settings — the round
  cannot force a clean read here without a lever this branch doesn't have (e.g. waiting for a
  specific settle-confirmed log line before evaluating, which does not currently exist).

### R5 — CONTAIN (abbreviated)

**PASS on the scale number; same touch-tracking gap as POCO**

- `Normal Scale. scaleX: 0.8000001, scaleY: 1.0` — matches POCO exactly.
- One tap (`40,40`) returned `21,21`, identical to FILL, despite the real 240px pillarbox — same gap
  as POCO, confirmed cross-device.

### R6 — COVER (abbreviated)

**PASS on the scale number; same touch-tracking gap as POCO**

- `Normal Scale. scaleX: 1.0, scaleY: 1.25` — matches POCO exactly.
- One tap (`40,40`) returned `21,21`, identical to FILL, confirming the same gap on a second device
  and a second (once-settled) panel reading.

## Anything the brief did not ask about

**Finding 1 — CONTAIN/COVER touch mapping ignores the real letterbox/crop whenever the margin is
non-zero. This is a real, previously-undiscovered touch-misalignment bug, reproduced identically on
both phones.**

Two independent modules each recompute "is this view letterboxed, and by how much" using **different
inputs**, and they can disagree:

- `ProjectionViewScaler`'s visible scale comes from `HeadUnitScreenConfig.getScaleX()/getScaleY()`
  → `ProjectionGeometryPolicy.scaleX/scaleY`, which take the **raw negotiated** width/height
  (`app/src/main/java/com/andrerinas/openheadunit/utils/HeadUnitScreenConfig.kt:408-415`, passing
  `getNegotiatedWidth()/getNegotiatedHeight()` — 1280x720, uncorrected for margin).
- `TouchCoordinateMapper.map()`'s own CONTAIN/COVER letterbox decision uses `uiRatio = (negotiatedWidth
  - marginWidth) / (negotiatedHeight - marginHeight)` — the **margin-reduced** canvas (1280x576 on
  this panel) — compared against `viewRatio = inputSurfaceWidth/inputSurfaceHeight`
  (`app/src/main/java/com/andrerinas/openheadunit/input/TouchCoordinateMapper.kt:39-49`).

On any panel where the margin computation already reshapes the canvas to exactly match the panel's
aspect ratio (which it always does whenever `ProjectionGeometryPolicy.isSmallScreen()` is false — see
Finding 2's proof), `uiRatio` **always** equals `viewRatio` exactly, so the mapper's own internal
check concludes no letterbox/crop correction is needed and returns the raw-proportional coordinate —
while the view is, in fact, genuinely pillarboxed (CONTAIN, confirmed `scaleX: 0.8000001` — a real
240px bar on each side of this panel) or cropped (COVER, confirmed `scaleY: 1.25` — a real 25%
top/bottom crop). A tap anywhere in the actual pillarbox (e.g. raw x=40, squarely inside the real
0-240px black bar under CONTAIN) is reported by the app as landing on real content at video x=21,
when nothing is actually drawn there. Measured identically on both phones, in both CONTAIN and
COVER, at the panel's settled 2400x1080 reading.

**Round 1 could not have caught this**: the MT50 is `isSmallScreen()` (1440x720 ≤ 1920x1080), so its
`fit()` always uses `scaleFactor=1.0` and every CONTAIN/COVER run there measured margin 0x0 — under
zero margin, `uiRatio` and the raw negotiated aspect are the same thing by construction, so the two
modules' disagreement has no numbers to disagree about. It only shows up on a panel wide enough to
leave the "small screen" bucket, which is exactly what a real ultra-wide car head unit (rather than
a phone-in-landscape stand-in) is likely to be. Worth root-causing before this branch ships CONTAIN/
COVER as user-facing options: the fix is presumably to have `TouchCoordinateMapper`'s CONTAIN/COVER
branch key off the **same** raw-negotiated-vs-panel aspect comparison `ProjectionGeometryPolicy`
uses for the visual scale, not a margin-reduced one — or to feed it the margin-reduced dimensions
consistently on both sides. Not fixed here since it wasn't in the round's brief; flagging for
before this branch lands its CONTAIN/COVER options.

**Finding 2 — the margin announced to the phone at connect time can differ from the margin the head
unit itself later uses for touch, when the device's screen-metrics reading is still settling.**

Seen on the Moto (not the POCO, which happened to settle before its decisive lines fired): the
`ServiceDiscoveryResponse`'s `Margins are:` line (`ServiceDiscoveryResponse.kt:114`, computed once
from `HeadUnitScreenConfig.getWidthMargin()/getHeightMargin()` at the moment the capability message
is built) logged `0x102` from a transient `2237x1080` reading, while `AapProjectionActivity.
sendTouchEvent()` (`AapProjectionActivity.kt:1902-1904`) recomputes `getWidthMargin()/getHeightMargin()`
**fresh on every tap**, and by the time taps were driven the screen had settled to `2400x1080`,
giving `144`. The phone was told one number for laying out its safe-area content and the head unit
silently started using a different one for touch a few seconds later, with no re-announcement. This
is a race against the device's own window-inset settling, not something either phone-specific script
in this round controls, and it did not reproduce on the POCO in this session (whose readings happened
to settle before the decisive log lines fired both times it was tried) — so it is intermittent, not
deterministic, and worth a JVM regression test asserting `getWidthMargin()/getHeightMargin()` are
stable once read rather than a hardware round chasing a timing window this branch has no lever over.

**Neither finding blocks round 1's MT50-focused verdict** — the core scale/PAR fix (the thing PR #943
and issue #809 are actually about) is confirmed correct on two more real, more-extreme panels than
the rig alone could offer. Both findings are additional, previously-unknown gaps this round happened
to be positioned to catch because it used real ultra-wide-in-landscape phone panels instead of a
head unit whose screen metrics are static and comfortably inside the "small screen" bucket.
