# ultrawide-touch-alignment — round 6 addendum: D-MOTO Self Mode

Run after the round 6 report, on request: the reporter of the round saw touch misalignment on
**D-MOTO** (Moto edge 30 neo, Android 14), which round 6 could not test because the device was off
the rig. It is connected now. This addendum covers D-MOTO Self Mode only; R0-R8 in
`ultrawide-touch-alignment-round6-results.md` stand.

**Candidate:** `fix/video-fit-and-ultrawide-touch` @ `e4be4e0e3`, APK md5 `959467e2` (== round 6)
**Also captured:** the build D-MOTO was already carrying, APK md5
`3c57e5300385248206202b0d03295645` (round 4's tip, "RMOTO-preexisting" below) — this is what the
reporter was using.
**Unit:** D-MOTO, Self Mode, Gearhead 17.5.663234, panel 1080x2400 physical / ~2400x1080 landscape
usable, with a **connect-time inset settle** (`realScreen` reads `2237x1080` → `2400x1080` → settles
`2300x1017`, the round 3 R9 behaviour).
**Date:** 2026-09-08

## One-line answer

**The misalignment is the round 4 R5 touch-centring regression, still live on the build D-MOTO was
carrying (`3c57e530`). The round 6 candidate fixes it** — verified in both FILL and CONTAIN, every
tap maps from the buffer top-left with no `+margin/2` offset. Two D-MOTO-specific notes below: R7's
margin re-announce **works** here (first hardware confirmation), and the FILL/PAR path has a small
gap — the pixel ratio is computed from the pre-settle panel reading and never re-announced.

## Verdict summary

| Run | Verdict | One line |
|---|---|---|
| RMOTO-preexisting | **reproduces the fault** | `3c57e530` maps FILL touch as `video_y = margin/2 + (raw_y/viewH)·uiH` — the round 4 R5 centring bug. On its `0x231` margin every vertical tap lands ~115 buffer-rows low, worst near the top. |
| RMOTO-cand (FILL) | **PASS (touch) + finding (PAR)** | Candidate maps `video_y = raw_y/viewH·videoH`, **no offset** (`raw=1200,60 → video y=64` vs `166` on `3c57e530`). Picture correct to the eye. But `PAR 11651` is derived from the transient `2237x1080` reading and **never re-announced** when the panel settles to `2300x1017` — a ~3-8% aspect error the margin path would have self-corrected. |
| RMOTO-cand (CONTAIN) | **PASS** | `shape=MARGIN`, touch maps top-left with no offset (`raw=1200,60 → video y=50`). **And the round 6 `onMarginsDiverged` re-announce fires correctly**: `margins drifted from the announced 0x153 to 0x231` → **one** `TX UpdateUiConfigRequest: L=0 T=115 R=0 B=116` → `reannounceMargins`. First hardware confirmation of round 6's R7. |

## RMOTO-preexisting — the fault, on the build the reporter had

**Reproduces the misalignment.** Settings `resolutionId=3`, `video-fit-mode=0` (FILL), `log-level=0`.

Wire geometry (after the inset settle + the build's own margin re-announce):
```
[ServiceDiscovery] NegotiatedResolution is: 1920x1080
[ServiceDiscovery] Margins are: 0x153        (then drifts to 0x231, re-announced)
[ServiceDiscovery] PixelAspectRatioE4 is: 10000
[UI_DEBUG] CarScreen: margins drifted from the announced 0x153 to 0x231
[UI_DEBUG_FIX] AA is already running, send corrected via sendUpdateUiConfigRequest
[UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.2720848
```

Touch, at settled geometry `view=2300x1017 video=1920x1080 margin=0x231 fit=FILL`:

| `input tap` (panel) | `Touch map` → `video=` | `margin/2 + (raw_y/1017)·849` |
|---|---|---|
| `1200,540` | `1002,566` | `115.5 + 450.8 = 566.3` |
| `95,300` | `79,366` | `115.5 + 250.4 = 365.9` |
| `95,780` | `79,767` | `115.5 + 651.1 = 766.6` |
| `1200,1030` | `1002,975` | `115.5 + 859.9 = 975.4` |

Every `video_y` is `half the announced margin (115 rows) + the top-left-anchored proportional value`.
That is exactly the `2a8ce83f` canvas-centring change round 4 R5 measured as wrong: Android Auto
anchors its UI at the **buffer top-left** with the margin at the bottom, so adding `margin/2` pushes
every tap down. Near the top of the panel the tap lands ~115 panel-px below where the user aimed;
lower down it is worse because the proportional term is also inflated. This is the misalignment.

`3c57e530` predates round 6 — no `shape=` in its `CarScreen` line — so it has the round-4 centring
regression and none of round 6's fixes.

## RMOTO-cand (FILL) — the fix, and a PAR gap

**Touch: PASS.** Settings as above, candidate APK.

Wire geometry:
```
[RES_CAP] resolutionId=3 realScreen=2237x1080 ... chosen=_1920x1080 capped=_1920x1080
[UI_DEBUG] CarScreen ... shape=PAR, margins: w=0, h=0
[ServiceDiscovery] NegotiatedResolution is: 1920x1080
[ServiceDiscovery] Margins are: 0x0
[ServiceDiscovery] PixelAspectRatioE4 is: 11651 (10000 = square)
[UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
```

Touch, `view=2300x1017 video=1920x1080 margin=0x0 fit=FILL`:

| `input tap` | candidate `video=` | `3c57e530` `video=` | `raw_y/1017·1080` |
|---|---|---|---|
| `1200,60` | `1002,**64**` | `1002,166` | `63.7` |
| `1200,540` | `1002,**573**` | `1002,566` | `573.3` |
| `1200,1030` | `1002,**1080**` (clamped) | `1002,975` | `1093.8` |
| `66,398` | `55,423` | — | `422.6` |
| `66,492` | `55,522` | — | `522.4` |
| `66,588` | `55,624` | — | `624.4` |

The candidate's `video_y` is `raw_y / viewH · videoH` with **no `+margin/2` term** (margin is 0 on
the PAR path anyway). Near the top the correction over `3c57e530` is `166 → 64`, ~100 panel-px. The
settled picture (`RMOTOcandsettled.jpg`) is full-screen, correctly proportioned, rail discs round.

**Finding — the PAR is stale on a settling panel.** `MarginStrategyPolicy` flips D-MOTO's wide panel
to the PAR path (as it did D-POCO in R4). The announced `PAR 11651` is
`(2237·1080)/(1080·1920)·10000` — computed from the **first** `realScreen` reading, before the inset
settles. The panel then settles to `2300x1017` (true derived ratio ~`12200`) or, when a pane is
maximised, `2400x1080` (~`12500`), and **the PAR is never re-announced**: the round 6 drift check
(`onMarginsDiverged` / `MarginAnnouncementPolicy.shouldReannounce`) only compares the **margin**, and
on the PAR path the margin is always `0`, so nothing ever "drifts". The margin path self-corrects
here (see CONTAIN below); the PAR path does not. Visible impact on this rig is small — the rendered
picture is ~3-8% wider than ideal, not enough to catch by eye against the rail discs — but a panel
with a larger connect-time inset delta, or a slower settle, would show it. **The re-announce logic
needs to cover the PAR the same way it covers the margin.**

## RMOTO-cand (CONTAIN) — margin path, and R7 confirmed

**PASS.** Settings `video-fit-mode=1`, else as above.

```
[UI_DEBUG] CarScreen ... shape=MARGIN, margins: w=0, h=153
[ServiceDiscovery] NegotiatedResolution is: 1920x1080
[ServiceDiscovery] Margins are: 0x153
[ServiceDiscovery] PixelAspectRatioE4 is: 10000
[UI_DEBUG] CarScreen: margins drifted from the announced 0x153 to 0x231
[UI_DEBUG_FIX] TX UpdateUiConfigRequest: L=0 T=115 R=0 B=116
[UI_DEBUG_FIX] AA is already running, send corrected via sendUpdateUiConfigRequest
[UI_DEBUG] Normal Scale. scaleX: 0.9999693, scaleY: 1.2720848
```

**Round 6 R7 → PASS on D-MOTO.** The inset settle moves the margin `0x153 → 0x231` *after*
`makeProto` announced `0x153`; `onMarginsDiverged` fires, `reannounceMargins()` sends the corrected
per-side margins (`T=115 B=116`, the 231 split) via `sendUpdateUiConfigRequest`, and it sends
**exactly once** — the `marginsMatchAnnounced()` dedup holds even though a surface change re-enters
`reannounceMargins` twice. This is the path round 6 could only cover in JVM
(`MarginAnnouncementPolicyTest`); D-MOTO exercises it for real.

Touch, `view=2300x1017 video=1920x1080 margin=0x231 fit=CONTAIN` (panel aspect == margin-reduced
canvas aspect, so no visible letterbox — same as D-POCO):

| `input tap` | candidate `video=` | `(raw_y/1017)·849` (top-left, uiH=849) |
|---|---|---|
| `1200,60` | `1002,50` | `50.1` |
| `1200,540` | `1002,451` | `450.8` |
| `1200,1000` | `1002,835` | `834.8` |
| `66,400` | `55,334` | `333.9` |
| `66,500` | `55,417` | `417.4` |

No `+margin/2` offset — compare `3c57e530`'s `raw=1200,60 → 166`. Picture
(`RMOTO-cand-contain.jpg`) full-screen, correctly proportioned, rail discs round.

## Setup notes

- **D-MOTO Self Mode is flaky on this rig.** The first candidate/baseline driver run hit
  `Handshake: ... peer accepted the connection and then sent nothing at all ... session state failed
  (peer_silent)` — Gearhead's `:5277` dev server had gone silent (it recovered on its own ~2 min
  later, no UI tap needed). A real incoming phone call interrupted one baseline session
  (`Suspected spam caller`); `cmd notification set_dnd priority` for the rest of the round, restored
  to `off` after.
- **D-MOTO's Gearhead 17.5.663234 does not log `CAR.PROJECTION.PRES` (`injectMotionEvent`)** — unlike
  D-POCO's 17.5.663214. So the "what coordinate did AA receive / which control fired" cross-check
  from R3/R4 is unavailable on D-MOTO. Verdicts here rest on the app's own `[UI_DEBUG] Touch map:`
  line (present on the candidate and on `3c57e530`, absent on `main`) plus the settled screencaps.
- **Baseline `main` (`12706e26f`) could not be measured on D-MOTO.** It has no `Touch map` log line
  and `injectMotionEvent` is unavailable, so there is nothing to read. The A/B here is
  `3c57e530` (the reporter's build, buggy) vs `e4be4e0e3` (candidate, fixed); `main`'s correctness is
  already established on D-HU (R2) and D-POCO (R3).
- **D-MOTO's digitizer sprays phantom touches** (round 4 R8). Synthetic `input tap` bypasses it and
  the deliberate taps are identifiable by their exact raw coordinates in the `Touch map` lines, so
  the phantom stream is noise, not a blocker, for this method.
- Scripts: `set_prefs_runas.sh` (DEVICE=ZY22GC3BM4), `restore_settings.sh`. No script added.
  `ultrawide_touch_r3.sh`'s 130 s session wait timed out against D-MOTO's flaky connect, so the
  candidate runs were driven inline instead.
- **Settings restored md5-identical**: D-MOTO `fe4f747b1a51796f5e3b58e562ad2859` (the state found).
  D-MOTO now carries the candidate APK `959467e2` (was `3c57e530`) — left installed on purpose, it
  is the build that fixes what the reporter saw.

## What to do

- The touch fix lands the reporter's complaint on D-MOTO too — no further hardware needed there.
- **Before merge, extend the drift re-announce to the PAR.** Right now `HeadUnitScreenConfig`'s
  divergence check only watches `getWidthMargin()` / `getHeightMargin()`; on the PAR path those are
  always 0, so a panel that finishes settling its window insets after `ServiceDiscoveryResponse` has
  gone out keeps a pixel ratio derived from the pre-settle metrics. Either have
  `MarginAnnouncementPolicy` / `onMarginsDiverged` also compare `getPixelAspectRatioE4()` against a
  recorded announced value, or defer the first announce until `[RES_CAP]` reports a stable reading
  (`locked=true` with two matching `realScreen` values).
