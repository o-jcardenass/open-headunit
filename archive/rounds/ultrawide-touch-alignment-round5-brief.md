# ultrawide-touch-alignment: round 5 brief

Hosts, and the labels used throughout this brief:

| Label | Unit | Panel | Role |
|---|---|---|---|
| **D-HU** | UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14 | 1440x720, aspect 2.0 | Native AA, projecting phone is D-POCO |
| **D-POCO** | POCO X3 NFC (`M2007J20CG`), Android 15 | 2400x1080 landscape | Self Mode |

Read `TESTING-TEMPLATE.md` before planning any step; §7a applies in full.

This file is append-only. Corrections arrive as new commits and a `git pull` fast-forwards.

**Run round 4 first.** This round uses round 4's APK, unchanged, and several of its expectations are
"identical to a round 4 number". If round 4 has not run, run it, then come back here.

---

## 0. What this round is, and why it has no build

There is **no code change in this round.** Nothing to compile, nothing to install beyond what round 4
already put on the devices. Every arm below is a preference write and a connect.

The app describes a non-16:9 panel to the phone in two different ways, and it has only ever been
able to use one of them at a time:

- a **margin**, meaning "encode a 16:9 frame but leave these rows blank, I cannot show them", and
- a **pixel aspect ratio**, meaning "these pixels are not square, author your layout accordingly".

Wherever a margin is announced, the derived ratio cancels to exactly 10000 by construction, because
the margin has already shaped the canvas to the panel and there is nothing left for the ratio to say.
So the two are alternatives, never layers. Which one carries the correction is decided entirely by
whether the chosen buffer is taller than the panel.

**The margin has been observed working. The pixel aspect ratio never has.** Round 3 R7 photographed
two phones honouring a margin of 20% of the frame: full bleed, correct proportions, no bar, no crop.
Against that, no capture anywhere in the project contains a `PixelAspectRatioE4 is:` line at all;
that log line is newer than every log on disk. The one field report of a correct picture at a
non-square ratio comes from a panel where every scale is also 1.0, so it proves the scales and not
the ratio.

That gap matters because the obvious next simplification rests entirely on it. If the phone honours
the ratio, the app can stop announcing margins altogether, every scale becomes exactly 1.0 on every
panel, buffer and touch space coincide, and the whole class of "where inside the buffer does the
visible canvas sit" bugs stops existing. If the phone ignores the ratio, that design would make the
picture worse on every panel that has a margin today, and the margin has to stay.

**This round answers which.** It also tests the one hypothesis that would explain three separate
reporter complaints at once.

---

## 1. Precondition and identity

No build gate. Confirm the APK on both devices is round 4's candidate before starting:

```bash
adb -s <serial> shell am broadcast -a com.andrerinas.headunitrevived.ACTION_QUERY_STATE
```

The `commit` field must read the candidate SHA from round 4. If either device carries something
else, reinstall round 4's candidate APK and record the md5 in Setup notes. **Do not build.**

Standing settings for every run below, written to `shared_prefs/settings.xml` with the app stopped:

```
log-level = 0          (verbose, as round 3 and round 4 used it)
video-fit-mode = 0     (FILL; the ratio is only announced in FILL)
```

Restore every device to its pre-round backup at the end, as always.

---

## 2. The lever, and why it needs no code

`pixel-aspect-ratio-e4` is a normal preference. A stored value goes on the wire **unclamped** and
beats the value the app would derive, with one exception: exactly `10000` doubles as "unset" and
lets the derived value through.

**So `10001` is how you force square pixels**, and any other number is how you force a shape. That
single quirk is what makes this whole round buildless.

---

## 3. R0: the control, D-HU on AUTO

**Setup.** D-HU. `resolutionId = 0` (AUTO), `pixel-aspect-ratio-e4 = 10000`. Connect once via the
round 3 method (D-POCO Bluetooth off at launch, on after the group forms). Photograph the projection
with `screencap` after the picture is up. Leave Android Auto on a screen with a **circular** element
visible: the Google logo on the assistant screen or the speed dial on the map. The circle is the
whole instrument.

**Expected**, all four lines, dumped from the policy objects rather than computed by hand:

```
[ServiceDiscovery] NegotiatedResolution is: 1280x720
[ServiceDiscovery] Margins are: 0x0
[ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
[UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
```

This is round 4's R1 repeated, so if it does not reproduce, stop and say so: nothing after it means
anything.

## 4. R1: the decisive run. Same panel, square pixels forced

**Setup.** Identical to R0 in every respect except `pixel-aspect-ratio-e4 = 10001`. Connect,
photograph the same Android Auto screen.

D-HU is the right unit for this and the only one on the rig that is, because at 1440x720 the
candidate already produces margins `0x0` and scales `1.0/1.0`. Nothing else in the geometry can move.
The **only** difference between R0 and R1 on the wire is one integer.

**Expected**, if the phone honours the ratio: the app tells it the pixels are square, so it authors a
1280-wide layout for what it believes is a 1280-wide square-pixel screen. Those 1280 columns are then
shown across 1440 panel columns. The picture is **12.5% too wide**, and the reference circle is
visibly an ellipse next to R0's.

**Verdicts.**

- R0 and R1 photographs clearly different, R1 stretched horizontally: **PASS**, and it means the
  ratio is honoured. This is the result that unlocks the margin-free design.
- R0 and R1 photographs indistinguishable: **PASS as a run, and a negative result.** The phone
  ignores the field. Say so plainly. It is not a defect in the build and not a rig fault; it is the
  answer, and it is the more consequential of the two.
- Anything else, including a session that will not stay up on one arm: INCONCLUSIVE, re-run.

**Measure, do not eyeball.** Both photographs are `screencap` of the same panel, so the circle can be
compared in pixels. Report its width and height in each, from the image. A 12.5% difference is easy
to see and easy to argue about; a number is neither.

## 5. R2: the same question with an unmissable signal

If R1 comes back "indistinguishable", the obvious objection is that 12.5% is too small. R2 removes
that objection.

**Setup.** D-POCO, Self Mode, `resolutionId = 3` (1080p), which round 3 R4 and round 4 measured as a
correct, full-bleed, undistorted picture. Three sub-runs, one connect each, photographing the same
screen every time:

| sub-run | `pixel-aspect-ratio-e4` | on the wire | expected if honoured |
|---|---|---|---|
| R2a | `10000` | 10000, derived, square | correct picture, the control |
| R2b | `15000` | 15000 | layout authored 1.5x narrow, then stretched: grossly too wide |
| R2c | `6667` | 6667 | grossly too tall |

Every sub-run keeps `Margins are: 0x216` and `Normal Scale. scaleX: 1.0, scaleY: 1.25`, identical to
round 3 R4. Only the ratio line changes. Confirm that in the log before reading the photographs; if a
margin or a scale moved, something else changed and the sub-run is INCONCLUSIVE.

**Verdict.** Three visibly different pictures means honoured, and it also calibrates *how much* the
phone applies. Three identical pictures at ratios of 0.667, 1.0 and 1.5 is as strong a negative as
this rig can produce, and it retires the field.

## 6. R3: how large a margin will a phone actually honour

Independent of R1 and R2, and worth running whatever they say.

Three separate reporters describe the same symptom, a vertically squashed picture, on three
different panels. All three panels have one number in common: the app asks the phone to leave
**33.3%** of the encoded frame blank.

| panel | buffer asked for | margin announced | % of frame height |
|---|---|---|---|
| 1920x720, before the ladder fix | 1920x1080 | 0x360 | **33.3** |
| 2400x900 twelve-inch unit | 1920x1080 | 0x360 | **33.3** |
| D-POCO at 720p, round 3 R7 | 1280x720 | 0x144 | 20.0, **and it worked** |

The hypothesis: a phone honours a small margin and gives up on a large one, drawing across the whole
frame instead. Everything the app does downstream then squashes that full-frame layout into the rows
the panel has, which is exactly what all three reporters describe.

**Setup.** D-POCO, Self Mode, `resolutionId = 0` (AUTO), `pixel-aspect-ratio-e4 = 10000`. Vary only
`fullscreen-mode`, which changes the usable area the app measures and therefore the margin it asks
for, with the projection view laid out in that same window so the picture stays consistent:

| sub-run | `fullscreen-mode` | usable height | expected margin | expected `scaleY` |
|---|---|---|---|---|
| R3a | `1` (immersive) | 1080 | `0x216`, 20.0% | 1.25 |
| R3b | `2` (status bar only) | read it from the log | see the table below | see below |
| R3c | `0` (bars shown) | read it from the log | see the table below | see below |

The usable height on this device is not known in advance, so read `[RES_CAP] ... usable=2400xNNN`
from each sub-run's log and take the row that matches:

| usable | margin | % of frame | `scaleY` |
|---|---|---|---|
| 1080 | `0x216` | 20.0 | 1.25 |
| 1010 | `0x272` | 25.2 | 1.3366337 |
| 990 | `0x288` | 26.7 | 1.3636364 |
| 970 | `0x304` | 28.1 | 1.3917526 |
| 950 | `0x320` | 29.6 | 1.4210526 |
| 930 | `0x336` | 31.1 | 1.451613 |
| 900 | `0x360` | 33.3 | 1.5 |

All of them keep `PixelAspectRatioE4 is: 10000`, because a margin is present in every one.

Photograph each sub-run on the same Android Auto screen.

**Verdict.**

- Proportions correct at 20% and visibly squashed vertically at the largest margin reached:
  **hypothesis confirmed**, and it explains all three reporters at once. Note the percentage at which
  it breaks, which is the number the fix would be built around.
- Proportions correct at every margin this device can reach: the hypothesis is **not confirmed on
  this phone**. Say which percentages were actually reached. If bars cost this device only a few
  percent, the run cannot get near 33.3 and the honest verdict is INCONCLUSIVE with the range
  covered, not a refutation.
- Do not read a *horizontal* change as evidence either way; `fullscreen-mode` moves the usable height
  on this device, and the width should stay 2400 throughout. If the width moves, record it.

---

## 7. What to report

The standard results format, plus these three things, which are the round:

1. **The photographs, side by side per run**, and for R0/R1 the measured pixel width and height of
   the reference circle in each. This round's verdicts are visual by nature; §0 of the template says
   to say so rather than pretend otherwise, and that is what this note is.
2. **The exact `PixelAspectRatioE4 is:` line from every single connect.** It is the newest log line
   in the app and no captured log has ever contained it. These will be the first.
3. **A one-line answer to the question the round exists for:** does Android Auto react to
   `pixel_aspect_ratio_e4` on this phone, yes or no. Everything else is supporting detail.

If R1 and R2 both come back negative, that is a complete and useful round. It closes a design
direction that algebra alone made look obvious, and it is worth more than a confirmation would be.

---

## Correction, appended after this brief was pushed

**Round 4 has run, and its R5 failed.** Read `ultrawide-touch-alignment-round4-results.md` before
this brief. Three consequences for round 5.

**1. The identity gate in §1 is stale.** It says the `commit` field must match "the candidate SHA
from round 4", which was `2a8ce83f`. That is no longer the candidate. The branch has gained a
floating-button default flip and will gain at least one more commit to answer round 4's R5, so
**take the candidate SHA from this thread's router row in `README.md` at the moment you start**, and
record the live APK md5 per device as round 4 did. Round 4 found the `ACTION_QUERY_STATE` reply
still truncates at the first inner quote, so md5 is the identity that works.

**2. Round 4 answered one question this brief left open, and it is not the one this round asks.**
Round 4's R5 measured that Android Auto anchors its canvas at the **top-left** of the negotiated
buffer, with the announced margin at the bottom, proven by the coordinate Android Auto itself logged
receiving. That settles the anchoring convention. It says nothing about whether the phone honours
`pixel_aspect_ratio_e4`, which is what this round exists to find out, so every run below stands as
written.

**3. R3 gets more valuable, not less.** Round 4 showed a phone honouring a `0x144` margin exactly,
drawing its content in buffer rows `0..576` and leaving the announced margin blank. That is the 20%
row of R3's table, now confirmed from Android Auto's own side rather than from a photograph. R3 asks
what happens as that fraction grows toward 33.3%, and the round 4 evidence is the anchor point the
comparison is measured against.

Nothing else in this brief changes. The probe is still buildless, and `10001` still forces square.
