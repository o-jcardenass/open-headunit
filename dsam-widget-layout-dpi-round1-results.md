# dsam-widget-layout-dpi - round 1 results

**Candidate:** none, `main` @ `28d73e2f` behavior (app already installed on the unit, not rebuilt)       **Baseline:** none
**APK md5:** not recorded (no build this round)
**Unit:** D-SAM (SM-T230), Android 4.4.2 / API 19, panel 800x1280 at 213 dpi, natural orientation portrait; Native AA (`wifi-connection-mode=3`), `view-mode=2` (GLES), phone is the usual Native AA phone
**Date:** 2026-10-08

## Setup notes

- **There was no brief.** This was an operator-driven investigation, run read-only first and then with
  settings edits the operator approved. The pass conditions below are the operator's report: Android
  Auto on D-SAM shows the portrait-style layout (a weather widget panel, which only appears in the
  portrait layout) whichever way the tablet is held, while D-HP shows the landscape layout without it.
- Settings were changed only in `shared_prefs/settings.xml` with the app force-stopped. D-SAM has no
  `sed`, so the file was pulled with `run-as cat`, edited on the host with `python3`, pushed to
  `/data/local/tmp` and copied in with `run-as cp` using absolute paths. A backup of the original was
  kept on the host and restored between runs.
- Capture: `stdbuf -oL adb logcat OPENHU:V '*:S'`, killed by pid afterwards (`ps aux | grep logcat`
  empty at the end). **The capture was taken without `-v time`, so the quoted lines below carry no
  timestamps.** Order within a run is the file order.
- Screenshots are `adb shell screencap -p`. On this unit `screencap` returns the raw 800x1280 panel
  buffer, so a landscape session appears turned 90 degrees in the PNG. The widget panel is still
  clearly visible in it.
- Rotation was physical: the operator held the tablet landscape or portrait before each launch.
  `dumpsys window displays` confirmed `cur=1280x800` or `cur=800x1280` before each run.
- Settings on entry: `screen-orientation=2`, `dpi-pixel-density=175`, `resolutionId=0`,
  `fps-limit=60`, `view-mode=2`, `video-fit-mode=0`. R1 to R3 ran with `screen-orientation=1` (Auto)
  so that the orientation policy was exercised.
- **Final state differs from entry on one key, at the operator's request:** `dpi-pixel-density=198`
  (was 175). `screen-orientation` is back to 2.
- **Captures are not uploaded.** The screenshots show a map of the operator's neighborhood and the
  track playing, so no release asset was created. Local archive: scratchpad of the session
  (`cap_auto.txt`, `cap_dpi198.txt`, `sc198.png`, `sc175.png`). Everything a verdict rests on is
  quoted here.
- D-HP's `dpi-pixel-density=132` and `resolutionId=1` were read with `run-as grep`. Its 800x480
  stream is from the earlier hp-slate record, **not re-measured this round**, so the D-HP dp size
  below is derived, not observed.

## R1 - Auto orientation, session started with the tablet held portrait, dpi 175

**FAIL** (condition: a landscape canvas is negotiated; a portrait one was)

- Settings written: `screen-orientation=1`, `dpi-pixel-density=175`
- Display before launch: `cur=800x1280`
- Decisive log lines (no timestamps, file order):
  - `HeadUnitScreenConfig.init | Raw size: 800x1280, usable: 800x1280, orientation setting: AUTO, configOrientation: 1`
  - `HeadUnitScreenConfig.recalculate | [RES_CAP] ... realScreen=800x1280 ... portrait=true locked=false chosen=_1080x1920 capped=_720x1280 ... linkCapped=_720x1280`
  - `HeadUnitScreenConfig.lockResolution | Locking resolution at _720x1280`
  - `AapProjectionActivity.applyStickyOrientation | Sticky Orientation: Session active, forcing orientation to 1`
  - `ServiceDiscovery | NegotiatedResolution is: 720x1280`, `PixelAspectRatioE4 is: 11111`
- Measurements: negotiated 720x1280, sticky orientation target 1 (`SCREEN_ORIENTATION_PORTRAIT`).

With Auto, the configuration orientation read at service discovery decides the canvas, and the
sticky lock then pins the activity to it for the session. Rotating afterwards cannot change it. This
is real but is not the widget problem (see R3). Not tested: rotating mid-session.

## R2 - Auto orientation, held landscape, dpi 198

**PASS**

- Settings written: `screen-orientation=1`, `dpi-pixel-density=198`
- Display before launch: `cur=1280x800`
- Decisive log lines:
  - `HeadUnitScreenConfig.init | Raw size: 1280x800 ... configOrientation: 2`
  - `RES_CAP ... portrait=false ... capped=_1280x720 ... linkCapped=_1280x720`
  - `ServiceDiscovery | Margins are: 0x0`
- Screenshot `sc198.png` (raw panel buffer, turned 90 degrees): map plus media card, **no weather
  widget panel**.
- Measurements: stream 1280x720 announced at 198 dpi, which is about 1034x582 dp.

## R3 - Auto orientation, held landscape, dpi 175 (control for R2)

**FAIL** (condition: no weather widget panel; it is present)

- Settings written: `screen-orientation=1`, `dpi-pixel-density=175`, everything else identical to R2
- Display before launch: `cur=1280x800`
- Decisive log lines (an earlier run of the same configuration, before the screenshot):
  - `HeadUnitScreenConfig.init | Raw size: 1280x800 ... orientation setting: AUTO, configOrientation: 2`
  - `RES_CAP ... portrait=false ... capped=_1280x720`
  - `HeadUnitScreenConfig.lockResolution | Locking resolution at _1280x720`
  - `applyStickyOrientation | Sticky Orientation: Session active, forcing orientation to 0`
  - `ServiceDiscovery | PixelAspectRatioE4 is: 9000`
- Screenshot `sc175.png`: a grey card showing `--` and a degree sign (the weather widget) beside the
  map and media card.
- Measurements: stream 1280x720 announced at 175 dpi, which is about 1170x658 dp. The canvas is
  landscape and identical to R2 except for the density.

The only variable between R2 and R3 is `dpi-pixel-density`. Everything the app announced about the
canvas is landscape in both. The widget panel follows the density.

## Why the density is 175

The announced density is the stored setting: `HeadUnitScreenConfig.getDensityDpi()` returns
`dpi-pixel-density` when it is not 0 and the display density otherwise. Default is 0. The value 175 is
consistent with the onboarding wizard's `SystemOptimizer.calculateOptimalSettings`, which derives dpi
from the diagonal of the panel-capped resolution divided by the chosen physical size, but **this was
not traced for D-SAM and it is not known whether 175 came from the wizard or was typed in**.
`NarrowBandProfilePolicy` forces 720p at connect time on this unit (API 19 cannot report the band),
which the wizard may not know about when it computes the dpi.

## Anything the brief did not ask about

- D-SAM now has `dpi-pixel-density=198` left in place. 213 (the panel's native value) was not
  tested, and the exact dp size where Android Auto switches to the widget layout is not bisected.
  D-HP at 132 dpi with 800x480 is about 970x582 dp (derived), D-SAM at 175 dpi is about 1170x658 dp.
- The Auto sticky lock is a separate defect on portrait-native hardware (R1). The earlier
  `t230-native-aa-bringup` finding that LANDSCAPE only looks right with the tablet held portrait was
  not re-examined here and may be unrelated to both.
- Open question for the code side: whether the recommended dpi should be computed from the resolution
  actually projected after the narrow-band cap, or clamped to a dp ceiling.
