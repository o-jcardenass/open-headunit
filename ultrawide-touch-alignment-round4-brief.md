# ultrawide-touch-alignment: round 4 brief

Hosts, and the labels used throughout this brief:

| Label | Unit | Panel | Role |
|---|---|---|---|
| **D-HU** | UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14 | 1440x720, aspect 2.0 | Native AA, projecting phone is D-POCO |
| **D-POCO** | POCO X3 NFC (`M2007J20CG`), Android 15 | 2400x1080 landscape | Self Mode |
| **D-MOTO** | Moto edge 30 neo (`motorola_edge_30_neo`), Android 14 | 2400x1080 landscape | Self Mode |

Read `TESTING-TEMPLATE.md` before planning any step; §7a applies in full.

This file is append-only. Corrections arrive as new commits and a `git pull` fast-forwards.

**Read `ultrawide-touch-alignment-round3-results.md` first.** Every expectation below is either
"identical to a round 3 number" or "different from a round 3 number by an amount this brief states",
so that file is the reference, not background.

---

## 0. What changed since round 3

Round 3 passed all ten runs on `0b6497b4`. Two things happened after it.

**One: the reporter's own fix was merged to `main`.** It is toggle-gated and defaults off, so it
changes nothing by itself, but the candidate is rebased onto it and then generalises it. `main` now
also carries a floating launcher button that arrived in the same merge. **If a floating button
appears over the projection on any run, it is not a defect and not part of this round.**

**Two: the reason the reporter's 1920x720 panel was still squeezed was found, and it is upstream of
everything round 3 measured.** Round 3 only ever ran *manual* resolutions, ids 2 and 3. On AUTO the
ladder asks a 720-row panel for 1080 rows and then describes the 360 rows it cannot show as a height
margin. The derived pixel aspect ratio is measured against the canvas that margin leaves, and that
canvas has the panel's aspect by construction, so the ratio cancels to exactly 10000 every time.
**The pixel-shape correction could never fire on the path a user is actually on.**

The ladder now picks the rung whose rows match the panel's short side, so no height margin is
created and the correction survives. On D-HU that means AUTO stops asking for 1080p and lands
exactly where round 3's manual-720p R1 landed. On D-POCO and D-MOTO, whose panels have 1080 rows,
AUTO is unchanged.

**Three, and this is the run that matters most:** with a margin present, the picture and the touch
mapping disagreed about where the canvas sits inside the buffer. The renderer scales about the view
centre; the mapper measured from the buffer's top-left. On D-POCO at 720p that is 72 buffer rows,
which is about 135 panel pixels. The mapper is now centred, matching the renderer. Round 3 could not
have caught this: its tap tables were checked against predictions from the same model that produced
the mapper, never against a control that either lights up or does not. **R5 exists to check it
against a control.**

---

## 1. Build and baseline

```bash
git fetch fork
git checkout fix/video-fit-and-ultrawide-touch    # 2a8ce83f
```

| Input | SHA | Role |
|---|---|---|
| `main` | `12706e26` | **the baseline arm.** Build this too |
| `fix/video-fit-and-ultrawide-touch` | `2a8ce83f` | **the candidate.** 2 commits on `main` |

Unit gate: candidate **1458 / 0**, baseline **1414 / 0** (`testGithubDebugUnitTest`). Any other
count means the wrong tree was built; stop and say so.

Round 3's candidate `0b6497b4` no longer resolves on the fork: the branch was rebased onto the new
`main` and then compacted. Do not try to build it. Its measured numbers are quoted below wherever
they are the expectation.

The PC thermal-throttle guard `hur-wifi-test-scripts/thermal_guarded.sh` from round 3 is there if
the day needs it.

## 2. Drive it with the automation surface

Unchanged from round 3, including the quoting lesson and the `allow-external-configuration=true`
bootstrap after every install on every device. `ultrawide_touch_r3.sh` should carry this round with
its `MODE`/`RUN` arguments; the only new thing it needs is an AUTO setting, which is
`resolutionId=0`.

### Telling the arms apart

The one-line arm check from round 3 still works and still does not need the md5:

```
candidate   [UI_DEBUG] CarScreen ... scaleFactor: N, portraitScaled: B, margins: w=N, h=N
baseline    [UI_DEBUG] CarScreen ... scaleFactor: N, margins: w=N, h=N
```

Candidate only, and both are load-bearing this round:

```
[ServiceDiscovery] PixelAspectRatioE4 is: N (10000 = square)     INFO
[UI_DEBUG] Touch map: raw=X,Y -> video=X,Y ... margin=WxH fit=M   VERBOSE
```

### Settings

| Key | Value | Meaning |
|---|---|---|
| `resolutionId` | **`0` AUTO**, `2` 720p, `3` 1080p | AUTO is new this round and is most of it |
| `video-fit-mode` | `0` FILL, `1` CONTAIN, `2` COVER | absent means FILL |
| `pixel-aspect-ratio-e4` | `10000` | anything else suppresses the derivation |

Read `pixel-aspect-ratio-e4` back with `ACTION_GET_SETTINGS` before every run, as in round 3.
Log level verbose via `ACTION_SET_LOG_LEVEL --es level verbose`; the touch line does not print below it.

---

## 3. The runs

Marker, run, marker. Ten runs: four on D-HU, four on D-POCO, two on D-MOTO.

### R0, build gate

Candidate **1458 / 0**, baseline **1414 / 0**. No device. A failure ends the round.

### R1, D-HU on AUTO, candidate

**The headline run.** AUTO has never been measured on this rig.

Settings: `resolutionId=0`, `video-fit-mode` deleted, `pixel-aspect-ratio-e4` at `10000`.

**Expect**, and every line is required for PASS:

```
[RES_CAP] resolutionId=0 ... chosen=_1280x720 capped=_1280x720
[ServiceDiscovery] NegotiatedResolution is: 1280x720
[ServiceDiscovery] Margins are: 0x0
[ServiceDiscovery] PixelAspectRatioE4 is: 11250 (10000 = square)
[UI_DEBUG] Normal Scale. scaleX: 1.0, scaleY: 1.0
```

Then the five taps of round 3's R1, which must land on the same table:

| tap (raw) | video |
|---|---|
| 40,40 | 36,40 |
| 1400,40 | 1244,40 |
| 40,680 | 36,680 |
| 1400,680 | 1244,680 |
| 720,360 | 640,360 |

**And a photograph.** This is the configuration the whole change exists to produce, and it should be
indistinguishable from round 3's R1 photo: full-bleed, no bars, undistorted.

### R2, D-HU on AUTO, **baseline**

A measurement, not a PASS/FAIL. It records what a user on AUTO gets today and is the reference R1 is
read against.

Baseline APK, `resolutionId=0`.

**Expect** `chosen=_1920x1080`, `Margins are: 480x360`, `scaleX: 1.3333334, scaleY: 1.5`, and **no**
`PixelAspectRatioE4 is:` line at all, because the baseline does not emit one. Photograph it.

Report the numbers and the photo whatever they are. If this run's picture looks as good as R1's, say
so plainly: that is a real result and it bears on whether the ladder change is worth its cost.

### R3, D-HU at manual 720p, candidate

The regression anchor. Round 3's R1 with nothing allowed to move.

**Expect** exactly round 3's R1: `Margins are: 0x0`, `PixelAspectRatioE4 is: 11250`,
`scaleX: 1.0, scaleY: 1.0`, and the same five taps as R1 above. The margin is zero here, so the
touch centring change is a no-op and the tap table must be **byte-identical to round 3's**. A
difference here is a regression against a measured result.

### R4, D-HU at manual 1080p, candidate

The touch centring change with a margin present, on the head unit.

**Expect** the geometry to be bit-identical to round 3's R2: `Margins are: 480x360`,
`scaleX: 1.3333334, scaleY: 1.5`, `PixelAspectRatioE4 is: 10000`.

**Expect the taps to have moved**, by half the margin in each axis. Round 3 recorded no taps here,
so these are stated from the code:

| tap (raw) | video (candidate) | video (baseline mapping, for contrast) |
|---|---|---|
| 40,40 | **280,220** | 40,40 |
| 1400,40 | **1640,220** | 1400,40 |
| 40,680 | **280,860** | 40,680 |
| 1400,680 | **1640,860** | 1400,680 |
| 720,360 | **960,540** | 720,360 |

The panel centre mapping to the buffer centre (`960,540`) rather than to `720,360` is the whole
change in one number. PASS needs the candidate column.

### R5, D-POCO, the alignment A/B: **the decisive run**

Everything above checks the mapper against a model. This checks it against Android Auto.

Both arms, `resolutionId=2`, FILL, Self Mode, one connect each.

1. Connect on the **baseline**. Take a `screencap`. Pick a **small, unambiguous Android Auto control
   with visible feedback** and read its centre in panel pixels off the capture. Good candidates: an
   icon in the left rail, or a media transport button. It must be **shorter than about 135 panel
   pixels**, and there must be either empty space or a clearly different control about 135 pixels
   **above** it, so a miss is visible rather than ambiguous.
2. `input tap` that exact point. Screencap again. Record whether the control activated, and if
   something else did, which.
3. Repeat, same point, on the **candidate**.

**Why 135:** the baseline maps from the buffer's top-left, the candidate from its centre, and at
`Margins are: 0x144` on this panel that is 72 buffer rows, which is 135 panel pixels. The baseline
should report a tap 72 rows high and therefore act on whatever sits above the target.

Outcomes, and all three are useful:

- **Candidate activates the control, baseline does not (or activates something above it).** The
  centring is correct and the change is proven against the phone rather than against the model.
  PASS.
- **Both activate it.** The control was too tall for the offset to matter, or the offset does not
  exist. Retry once with a smaller target before concluding; if both still hit, report
  INCONCLUSIVE and say what the target's height was.
- **Baseline activates it and the candidate does not. This is a FAIL and it is the important one.**
  It means Android Auto anchors its canvas at the buffer's top-left, the old mapper was right, and
  the fix belongs in the renderer instead. Do not try to work around it; report it, with both
  screencaps, and stop the D-POCO runs there.

Record the `[UI_DEBUG] Touch map:` line for the candidate tap. The baseline emits no such line, so
its behaviour is read from the screen alone, which is the point.

### R6, D-POCO on AUTO, candidate

The regression check for a panel the ladder change must **not** touch.

**Expect** `chosen=_1920x1080`, `Margins are: 0x216`, `scaleX: 1.0, scaleY: 1.25`,
`PixelAspectRatioE4 is: 10000`, identical to round 3's R6, which was itself identical to the R4
baseline. AUTO and manual 1080p must agree on this panel.

Taps: `40,40 -> 32,140`, `2360,40 -> 1888,140`, `40,1040 -> 32,940`, `2360,1040 -> 1888,940`,
`1200,540 -> 960,540`.

### R7, D-POCO at manual 720p, candidate, FILL

Round 3's R5 with the taps moved.

**Expect** the geometry unchanged from round 3's R5: `Margins are: 0x144`, `scaleX: 1.0`,
`scaleY: 1.25`, `PixelAspectRatioE4 is: 10000`.

**Expect the taps to be round 3's plus 72 rows:**

| tap (raw) | video (candidate) | round 3 measured |
|---|---|---|
| 40,40 | **21,93** | 21,21 |
| 2360,40 | **1259,93** | 1259,21 |
| 40,1040 | **21,627** | 21,555 |
| 2360,1040 | **1259,627** | 1259,555 |
| 1200,540 | **640,360** | 640,288 |

Photograph it and compare with round 3's R5 photo: the picture must not have changed at all. Only
the touch mapping moved.

### R8, D-MOTO, abbreviated

R6 and R7 only, one confirmation tap each, on the second phone. Round 3 showed the two phones behave
identically, so this is a cross-device check rather than a fresh measurement. Use
`TAGFILTER="OPENHU:V *:S"` as round 3 did.

Expect `1200,540 -> 960,540` on AUTO and `1200,540 -> 640,360` at 720p.

If D-MOTO's connect-time inset wobble shows again (round 3 saw `0x102` settling to `0x144`), record
it and read the taps against the **settled** margin printed on the `Touch map:` line, exactly as
round 3 did.

### R9, D-HU, the recommended resolution the wizard offers

Not a projection run. The ladder is shared with the setup wizard's recommendation and the DPI
screen, on purpose, so all three agree.

With the candidate installed, open Settings and read what the resolution row says is recommended for
this panel, and the same on the DPI screen.

**Expect 720p where it used to say 1080p.** That is intended and is the visible half of the ladder
change. Note the exact wording; it is user-facing text that will need saying in the PR.

---

## 4. What this round cannot answer

**The reporter's own panel.** His is 1920x720, deriving a PAR of 15000; D-HU is 1440x720 deriving
11250. A 1.5x correction is far enough from square that Android Auto may lay its UI out differently
rather than merely render it correctly shaped, and no unit on this rig can show that. If every run
here passes and his unit is still wrong, that is the next question and it needs his log, not more
rig time.

**Whether announcing a margin is honoured at all.** R5 answers where Android Auto puts its canvas
when we announce one, which is the same question from the other side, but only on a 2400x1080 panel
in Self Mode. If R5 comes back FAIL, that answer changes the design and the follow-up below dies
with it.

Recorded rather than tested: the touch configuration still announces the full negotiated size while
the mapper emits canvas coordinates; a mid-session metrics change still drops the resolution lock;
and the legacy forced-scale SurfaceView path still measures the buffer rather than the canvas, so on
a panel wider than its buffer it clips where it should letterbox.

Not retested, because round 3 measured it and nothing in the candidate touches that path: the
margin-drift re-announce (round 3's R9). If a `margins drifted` line appears with no
`sendUpdateUiConfigRequest` after it while a session is live, report it anyway.

## 5. Reporting

Fixed format, per §7 of the template. In Setup notes: the `commit` from `ACTION_QUERY_STATE` for
every run, the `pixel-aspect-ratio-e4` read-back before each run, the `skipped` list from any
settings import that did not apply cleanly, and any run where the resolution did not land where the
brief asked.

Photographs are part of the result for R1, R2, R5 and R7. R5's two screencaps are the result.

One standing instruction from round 3 that earned its place: **a number agreeing with a prediction
is not a pass if the screen disagrees with both.** Round 2 passed a squashed picture that way, and
round 3 passed a tap table that had never been checked against a control. If R5's screencaps and
R1's photograph disagree with any table in this brief, the tables are what is wrong.
