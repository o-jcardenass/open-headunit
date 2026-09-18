# ultrawide-touch-alignment: round 3 brief

Hosts: the **MT50** (panel 1440x720) as the regression guard, and the **POCO X3 NFC** and **Moto
edge 30 neo** in Self Mode (2400x1080 landscape) as the point of the round. Read
`TESTING-TEMPLATE.md` before planning any step; §7a applies in full.

This file is append-only. Corrections arrive as new commits and a `git pull` fast-forwards.

**Read `ultrawide-touch-alignment-round2-results.md` first.** This round exists because of it, and
because reading its two findings back against the source changed the verdict on one of them.

---

## 0. What changed, and why round 2 could not see it

Round 2's own numbers were right and its predictions matched them, but both came from the same
model, and the model was wrong for a panel wider than 1920.

On the POCO, the code's `isSmallScreen` test is false, so the fit step scales the buffer up to the
panel width (2400/1280 = 1.875) and the leftover height becomes the `0x144` margin round 2
measured. That leaves a visible canvas of 1280x576, whose aspect is exactly the panel's. The
baseline's `scaleY: 1.25` is the same number as 720/576: it was never a stretch there, it was the
crop that makes the announced 144-row margin true, and with it the picture renders uniformly at
1.875 on both axes. The candidate's `1.0` deleted that crop, so the picture was squashed about a
fifth vertically and the rows we told the phone were invisible were shown. **Round 2 scored that
PASS.**

On the MT50 and on the reporter's 1920x720 the margin at 720p is `0x0`, so the same old formula
*was* a pure stretch. Both statements hold: the fix is right where the margin is zero and wrong
where it is not. Every scale is now the buffer over the visible canvas, which is 1.0 when there is
no margin and the crop when there is.

Round 2's Finding 1 was real and named the wrong module. The touch mapper was the half that was
right; the scaler measured the raw buffer where the mapper measured the canvas. Both measure the
canvas now, so on the phones **CONTAIN and COVER are expected to equal FILL** and the taps are
expected to be identical to FILL's. That is the fix, not the bug. What must also be true is that
there is no visible bar or crop on screen to disagree with them.

Round 2's Finding 2 and Moto's INCONCLUSIVE R4 were one bug: the scale update re-reads the display
metrics on every pass, so a device whose insets are still settling silently changes the margins
under a session that already announced its own. There was a re-announce path but nothing reached
it. It is reached now.

---

## 1. Build and baseline

```bash
git fetch fork
git checkout fix/video-fit-and-ultrawide-touch    # 0b6497b4
```

| Input | SHA | Role |
|---|---|---|
| `main` | `6cd70399` | **the baseline arm.** Build this too |
| `fix/video-fit-and-ultrawide-touch` | `0b6497b4` | **the candidate.** 2 commits on `main` |

Unit gate: candidate **1453 / 0**, baseline **1414 / 0** (`testGithubDebugUnitTest`). Any other
count means the wrong tree was built; stop and say so.

The previous tip `371477b6` is tagged `ultrawide-rounds-1-2-validated` on the fork, so rounds 1 and
2 still resolve. Do not build it.

## 2. Drive it with the automation surface

Same as round 1. The command surface is on `main`, so both arms have it.

```bash
PKG=com.andrerinas.headunitrevived
RCV=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
A=com.andrerinas.openheadunit
```

**Quote the whole remote command**, which is round 1's own lesson: `adb shell "am broadcast -a
$A.ACTION_LOG_MARKER --es text 'R2 start' -n $RCV"`. Passing it as separate adb arguments loses the
quoting on-device and `am` reads the second word as a package name, matching zero receivers.

Bootstrap once per install, app stopped, per §1 of the template: write
`allow-external-configuration=true` into `shared_prefs/settings.xml`. A test-APK install resets it,
so redo it after every install and on every device. A `Refuse` reply naming external configuration
is what a forgotten bootstrap looks like.

`ACTION_QUERY_STATE`'s `commit` field is the APK fingerprint. Record it for every run.

### Telling the arms apart

Candidate only:

```
[ServiceDiscovery] PixelAspectRatioE4 is: N (10000 = square)     INFO
[UI_DEBUG] Touch map: raw=X,Y -> video=X,Y view=WxH ...          VERBOSE
[UI_DEBUG] CarScreen ... portraitScaled: B, margins: w=N, h=N    INFO   (new field this round)
[UI_DEBUG] CarScreen: margins drifted from the announced WxH ... INFO   (new this round)
```

Both arms:

```
[UI_DEBUG] Normal Scale. scaleX: N, scaleY: N
[ServiceDiscovery] NegotiatedResolution is: WxH
[ServiceDiscovery] Margins are: WxH
[RES_CAP] resolutionId=N ... chosen=X capped=Y
[UI_DEBUG_FIX] AA is already running, send corrected via sendUpdateUiConfigRequest
```

### Settings

| Key | Value | Meaning |
|---|---|---|
| `resolutionId` | `2` 720p, `3` 1080p | |
| `video-fit-mode` | `0` FILL, `1` CONTAIN, `2` COVER | absent means FILL |
| `pixel-aspect-ratio-e4` | `10000` | anything else suppresses the derivation |

**Read `pixel-aspect-ratio-e4` back with `ACTION_GET_SETTINGS` before every run.** A leftover
non-10000 value makes the PAR runs measure nothing and look like a plain failure. Log level goes
through `ACTION_SET_LOG_LEVEL --es level verbose`; the touch line does not print below Verbose.

---

## 3. The runs

Marker, run, marker. Nine runs, three of them on the MT50.

### R0, build gate

Both trees build and test: candidate **1453 / 0**, baseline **1414 / 0**. No device. A failure ends
the round.

### R1, MT50 at 720p, candidate

The round 1 result that must not have moved.

**Expect** `scaleX: 1.0, scaleY: 1.0`, `Margins are: 0x0`, `PixelAspectRatioE4 is: 11250`, and the
five taps of round 1's R3 landing on the same table (`40,40 -> 36,40`, `1400,40 -> 1244,40`,
`40,680 -> 36,680`, `1400,680 -> 1244,680`, `720,360 -> 640,360`).

PASS needs all of it. Anything different here is a regression against a measured result.

### R2, MT50 at 1080p, candidate

**Expect** `scaleX: 1.3333334, scaleY: 1.5`, `Margins are: 480x360`, `PixelAspectRatioE4 is: 10000`.
Bit-identical to round 1's R4.

### R3, MT50 at 720p in CONTAIN, candidate

The one MT50 number this round deliberately changes.

**Expect** `scaleX: 0.8888889, scaleY: 1.0` unchanged from round 1's R5, and
`PixelAspectRatioE4 is: **10000**`, where round 1 would have announced 11250.

PASS needs the PAR to be 10000. Announcing a 1.125x pre-stretch and then pillarboxing with square
pixels squeezes the phone's UI by the correction, which is why only FILL may claim non-square
pixels now. Also take a photo: round 1's "no distortion" was an unaided eye, and a 12% horizontal
squeeze is exactly what an unaided eye misses.

### R4, POCO on the **baseline** at 1080p

**The run round 2 never did, and the one every later verdict is measured against.** Nothing yet
records what `main` does on a 2400x1080 panel at 1080p.

Baseline APK, `resolutionId=3`.

**Expect** `Margins are: 0x216` and `scaleY: 1.25`. Photograph the picture. This is the reference
image for R5 and R6.

Not a PASS/FAIL run: it is a measurement. Report the numbers and the photo whatever they are.

### R5, POCO on the candidate at 720p, FILL

**Expect** `Margins are: 0x144`, `scaleX: 1.0`, **`scaleY: 1.25`**, `PixelAspectRatioE4 is: 10000`.

That `1.25` is the whole point: round 2 measured `1.0` here and called it PASS. PASS needs 1.25.

Then the same five proportional taps round 2's R3 used (`40,40`, `2360,40`, `40,1040`, `2360,1040`,
`1200,540`) and their `video=` values recorded.

**And a photograph, next to R4's.** A log cannot tell a correct picture from one squashed a fifth
vertically, which is how round 2 passed this. If the two pictures do not have the same proportions,
say so and treat the numbers as secondary.

### R6, POCO on the candidate at 1080p, FILL

**Expect** `Margins are: 0x216`, `scaleX: 1.0`, **`scaleY: 1.25`**, PAR `10000`, and a picture that
matches R4's baseline photo.

### R7, POCO on the candidate, CONTAIN then COVER at 720p

One connect each.

**Expect both to be identical to R5**: `scaleX: 1.0, scaleY: 1.25`, the same five taps, PAR 10000,
and **no bars and no crop on screen**.

This is the inversion of round 2's Finding 1, so read the verdict carefully. Round 2 failed this
because the taps matched FILL while the screen showed a 240 px pillarbox. Now the taps matching
FILL is correct, because a canvas the margin has already shaped to the panel has nothing to
letterbox. **PASS needs the screen to agree**: identical taps *and* no visible bar. Identical taps
with a bar still on screen is the original defect and a FAIL.

### R8, Moto, abbreviated

R5 and R7 only, one confirmation tap each, on the second device. Round 2 showed both phones behave
identically, so this is a cross-device check rather than a fresh measurement.

### R9, the margin that moves after it is announced

Round 2's Finding 2, now instrumented. Candidate, either phone.

Provoke a mid-session metrics change: connect, then change something that alters the usable area
while the session is live (rotate if the orientation setting allows it, or toggle the system bars
via the fullscreen mode, whichever this device actually honours mid-session).

**Expect**, in order:

```
[UI_DEBUG] CarScreen: margins drifted from the announced WxH to WxH
[UI_DEBUG_FIX] AA is already running, send corrected via sendUpdateUiConfigRequest
```

and the `Margins are:` value from connect time to differ from the drifted one.

**Intermittent by nature, so a miss is not a FAIL.** Round 2 saw it on the Moto and not the POCO on
identical settings. If it does not reproduce, say "not reproduced" and move on; do not spend the
round chasing a timing window. What *would* be a FAIL is the drift line appearing with no
`sendUpdateUiConfigRequest` after it while a session is live.

---

## 4. What this round cannot answer

Whether Android Auto's layout changes at a derived pixel aspect ratio, as opposed to the picture
merely being correctly shaped. The MT50 derives 11250 and the reporter's unit 15000, and only the
second is extreme enough for a layout change to be obvious. If R1 passes on the numbers and the
picture still looks wrong in a way the scale line does not explain, that is a question for the
reporter's hardware.

Also out of scope and recorded rather than tested: the touch configuration announces the full
negotiated size while the mapper emits margin-reduced coordinates; a mid-session metrics change
also drops the resolution lock; and the legacy forced-scale SurfaceView path still measures the
buffer rather than the canvas.

## 5. Reporting

Fixed format, per §7 of the template. In Setup notes: the `commit` from `ACTION_QUERY_STATE` for
every run, the `pixel-aspect-ratio-e4` read-back before each run, the `skipped` list from any
settings import that did not apply cleanly, and any run where the resolution did not land where the
brief asked.

Photographs are part of the result for R3, R4, R5, R6 and R7, not an extra. Round 2's whole miss
was a verdict that rested on numbers alone.
