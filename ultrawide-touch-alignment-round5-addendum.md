# ultrawide-touch-alignment, round 5 addendum: the direction is not inverted

Written after reading `ultrawide-touch-alignment-round5-results.md` against the code that produced
the wire values. The measurements in that file are all reproduced here unchanged. One conclusion
drawn from them is wrong, and it is the one a follow-up would act on, so it is corrected here rather
than by editing the results.

## The claim being corrected

> "the applied direction is inverted from the brief's R2 model ... The relationship is
> `rendered_aspect ≈ 10000 / PixelAspectRatioE4` ... Anyone building the margin-free design on this
> result must get that sign right"

That law is fitted from R2 alone. On D-POCO the value the app derives for the announced geometry
*is* 10000, so `10000/announced` and `derived/announced` are the same number there and R2 cannot
tell them apart. R0 and R1 can, because D-HU's derived value is 11250.

## All five points, against both laws

| run | panel | canvas | derived | announced | measured | `derived/announced` | `10000/announced` |
|---|---|---|---|---|---|---|---|
| R0 | 1440x720 | 1280x720 | 11250 | 11250 | 1.000 | **1.000** | 0.889 |
| R1 | 1440x720 | 1280x720 | 11250 | 10001 | 1.117 | **1.125** | 1.000 |
| R2a2 | 2400x1080 | 1920x864 | 10000 | 10000 | 1.000 | **1.000** | 1.000 |
| R2b | 2400x1080 | 1920x864 | 10000 | 15000 | 0.667 | **0.667** | 0.667 |
| R2c | 2400x1080 | 1920x864 | 10000 | 6667 | 1.490 | **1.500** | 1.500 |

`derived` is `panelW*canvasH / (panelH*canvasW) * 10000`, the app's own formula, and it reproduces
the 11250 and 10000 the round logged on the wire.

`10000/announced` misses R0 by 12.5% and R1 by 12%. Those are the only two runs on a panel whose
pixels are not square, which is the case the whole feature exists for.

## The mechanism both halves share

The phone lays its UI out pre-compensated by `10000/announced`. The head unit then stretches the
buffer to the panel, by `derived/10000` on this axis. What is left on the glass is the product,
`derived/announced`, and it is 1.0 exactly when the app announces what it derived.

The brief's R2 model was wrong about what the *phone* draws, and the round is right to correct it.
The derivation in the app was never the same claim, and it is confirmed by R0 and R1 rather than
refuted.

## Why this matters before the next round

R0 is not a control that happened to look right. It is the margin-free design already working end to
end on a 2.0-aspect panel: zero margins, both head-unit scales 1.0, and Android Auto's circles
measuring 60x60 because the announced 11250 cancelled the 1.125 stretch. R1 is the counterfactual
that proves the ratio was doing the work.

Inverting the sign as the results file recommends would announce 8889 on that same panel, leave
`11250/8889 = 1.27` on the glass, and make the picture 27% too wide, which is worse than announcing nothing.
No sign change is needed and none should be made.

## What the round does settle, that the results file understates

R3 could not push an announced *margin* past 20%, so "does a phone give up on a large margin" is
still open as posed. But the question it exists to serve is answered from the other side: **R2b put
15000 on the wire and the phone honoured it exactly**, drawing its UI 0.667 as wide. 15000 is
precisely what the derivation produces for a 1920x720 panel against a 1280x720 buffer, which is the
whole class of reports that ask for 33.3% of the frame. The phone only ever receives an integer, so
its response does not depend on the panel that integer came from.

The remaining gap for a margin-free redesign is therefore not the ratio. It is that R0 and R1
measured the ratio on a panel the resolution ladder already hands a margin-free canvas to, so a
panel that would otherwise get a margin still needs one field build.
