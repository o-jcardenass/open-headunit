# ultrawide-touch-alignment: round 6 review notes

Read after `ultrawide-touch-alignment-round6-results.md` and
`ultrawide-touch-alignment-round6-addendum-dmoto.md`. Neither file is edited; this one corrects
three readings in them and queues the round 7 asks. Every number below was recomputed from the
lines the two files quote, and the code paths the addendum reasons about were re-read on the
candidate (`e4be4e0e3`).

## What stands

- **R2 is the run the round existed for, and it holds.** A stored 1080p on the 1440x720 panel
  negotiates `1920x1080 / Margins 0x0 / PAR 11250 / 1.0,1.0` twice, where the baseline gives
  `480x360 / 1.333,1.5`. The two D-HU touch lines are exactly `raw/720*1080` from the top-left.
- **R3 closes round 4 R5.** No centring term (`625/1080*720 = 417`), and `x=51 -> AA x=64` is the
  phone multiplying by `PAR/10000`, which is the round 5 law composing correctly. R4's
  `panel x=95 -> AA x=95` is the cleanest number in the round.
- **R2-base versus round 4 R2.** Round 4 called the same build at the same geometry "undistorted to
  the eye" from a full frame; round 6's montage (`shape_compare.jpg`) shows the `0 km/h` disc
  taller than wide. The montage is the better instrument. Do not cite the round 4 line again.
- R0, R1, R5, R6, R8: as reported.

## Three corrections

### 1. R4's "finding" is the design, not a discovery

The brief the round ran on was written from the branch diff. The brief the change was designed
against (never pushed, now dropped) expected exactly what R4 and R3 measured on the 2400x1080 phone:
`0x216 / 1.25` becoming `0x0 / PAR 12500 / 1.0,1.0` at 1080p, and `0x144` becoming `0x0` at 720p
with the same tap landing at `video=50,417`. `MarginStrategyPolicy` gates on "FILL and the panel is
wider than the buffer", which is every 2.2 panel at every rung. The 5000-20000 clamp and the 3%
dead band were chosen for that reach. Nothing in this round argues for moving them; the old
1024x600 class stays on margins because 720p there derives 9600, below square.

### 2. The D-MOTO arithmetic

At the settled `2300x1017` the derived ratio is **12721**, not "~12200". It is the same number as
the CONTAIN run's `scaleY 1.2720848`. Against the announced 11651 the residual is **9.2%** too wide
at `2300x1017` and **7.3%** at `2400x1080`, not "3-8%". The direction in the addendum is right:
announced below derived means the phone under-compensates and circles render wider than tall.

### 3. "Extend the drift re-announce to the PAR" is not a fix, and R7 proves less than PASS

Two independent reasons, both already on record:

- `UiConfig` carries `margins`, `content_insets`, `stable_content_insets` and `ui_theme`. There is
  no pixel-aspect field in `UpdateUiConfigRequest`; `pixel_aspect_ratio_e4` exists only inside
  service discovery (`control.proto`). The ratio cannot be re-sent mid-session at all.
- `UpdateUiConfigRequest` (`0x8009`) is **measured inert**: 16 sends, every one acknowledged, no
  change on the phone (`aap-video-protocol-reference.md` line 55 in the handoff set; analysis A8.3).

So the addendum's R7 PASS is a count: the head unit sent the corrected margins exactly once. That
was the thing the dedupe was built to prove and it is proven. It does not show the phone moved its
canvas, and the earlier measurement says it does not. The sentence "the margin path self-corrects
here" is the one claim in the addendum that is wrong. Both strategies bake the connect-time reading
into the session.

**Which makes the D-MOTO CONTAIN run weaker than PASS.** The phone laid out for `0x153` (927 canvas
rows). After the settle the head unit scales the view and maps touch for `0x231` (849 rows). With
the re-announce inert the phone's canvas is `927/849 = 1.092` taller than the view assumes: a ~9%
vertical zoom about the centre, and a touch error growing to ~4% of panel height at the rail ends.
`RMOTO-cand-contain.jpg` next to `RMOTO-candsettled.jpg` looks like that (rail icons larger, the
search card flush with the top edge) but nothing measured it. The addendum's CONTAIN touch table
checks the mapper's formula only; the D-MOTO Gearhead build does not log `injectMotionEvent`, so
there is no control-hit evidence for D-MOTO in either mode.

## The gap, stated correctly

On a panel whose insets settle after `makeProto`, the first reading is baked in on both paths. This
is round 2 finding 2, unchanged. The only lever that exists is the anchor at the *next* connect:
`AapProjectionActivity` caches the settled usable size (`cached-surface-width/height/settings-hash`)
and `HeadUnitScreenConfig.init` reuses it when the hash matches.

As read, that cache cannot survive a process restart. `computeSettingsHash` folds the anchor
(`realScreenWidthPx/HeightPx`) in; `init` computes the hash *before* assigning the anchor, so on a
fresh process it hashes `0x0`, while the cache was written with the settled anchor. The hashes
differ, the cache is discarded, and the log says `Cache invalidated (hash mismatch`. On D-MOTO that
means the pre-settle ratio goes out on every connect after a force-stop, which is what the addendum
saw. This is a code reading, not a measurement. One grep settles it.

## Round 7 asks (D-MOTO only, candidate APK already installed)

1. **Grep the existing D-MOTO captures** for `Using cached surface dimensions` and
   `Cache invalidated (hash mismatch`. Report which appears, per connect.
2. **Two more FILL connects at `resolutionId=3`:** one in the same process (disconnect and
   reconnect without a force-stop), one after `am force-stop`. Report `PixelAspectRatioE4 is:` and
   the `[RES_CAP] realScreen=` line for each. Expected if the cache works within a process:
   `12721` on the in-process reconnect and `11651` again after the force-stop. Expected if it never
   works: `11651` every time.
3. **CONTAIN control-hit check:** `video-fit-mode=1`, connect, wait for the
   `margins drifted from the announced` line, then one synthetic tap on the visible centre of the
   *bottom* rail icon and one on the *top*. Verdict from which app opened (screencap), not from the
   `Touch map` line. Expected if `0x8009` is inert: the bottom tap misses or lands on the neighbour.

No code change is queued until ask 1 answers. If the cache is being discarded across restarts, the
fix is in `HeadUnitScreenConfig.init` (compare the cache against a hash without the anchor; rotation
and unfold are already caught by the raw-size comparison just above it), and it gets its own A/B
because it moves every panel.

## Housekeeping

- The three round 6 commits carry a Claude co-author trailer and multi-line prose bodies. This
  branch is append-only once pushed, so they stay; future commits are subject plus one line, the
  author's name only.
- The brief this change was designed against was never pushed and has been dropped locally; its
  content lives in the analysis document in the handoff set.
