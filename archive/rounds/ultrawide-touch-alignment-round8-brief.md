# ultrawide-touch-alignment: round 8 brief

Hosts, and the labels used throughout:

| Label | Unit | Panel | Role |
|---|---|---|---|
| **D-HU** | UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14 | 1440x720, **OEM side bar on the right, 136 px, visible in Screen mode Normal and STATUS_ONLY** | Native AA, projecting phone is D-POCO, view-mode GLES |
| **D-POCO** | POCO X3 NFC (`M2007J20CG`), Android 15 | 2400x1080 landscape | the projecting phone for D-HU |
| **D-MOTO** | Moto edge 30 neo, Gearhead `17.5.663234` | ~2400x1080 | Self Mode, R5 and R6 only |

> **Correction, 2026-09-10:** the two candidate SHAs in the first push of this brief were superseded
> minutes later by an amend. The commits are **`94efd6df`** (compacted) and **`89093db4`** (tip), and
> they are what this file now says throughout. The trees are otherwise unchanged.

Read `TESTING-TEMPLATE.md` before planning any step; §7a applies in full. This file is append-only;
corrections arrive as new commits and a `git pull` fast-forwards.

Round 7 is the parent. Read `ultrawide-touch-alignment-round7-results.md` first: every expectation
below is either "identical to a round 7 number" or "the thing round 7 found and this build fixes".

---

## 0. What changed since round 7, and what this round settles

Round 7 PASSed every run and left two defects standing. Both are fixed here, and reading the fixes
turned up a third instance of the first one that no round has ever been in a position to see.

**The invariant this whole thread rests on** is that the anchor is the canvas plus whatever insets
are in force, and the usable area is the anchor minus them. Round 7 measured three places where the
two halves were taken from different moments:

1. **STATUS_ONLY, measured.** The window was read while the insets still said zero, the anchor was
   left at the bar-reduced `1304`, and then the 136 px bar was taken off it again:
   `realScreen=1304x720 usable=1168x720`, `shape=MARGIN, margins: w=112`, for 140 ms. The projection
   surface corrected it before `makeProto` on that unit, so nothing wrong went out. On a unit whose
   surface arrives *after* service discovery, which is the reporter unit this thread exists for, that
   is the number that gets announced.
2. **The fresh-install case, never exercised.** Outside immersive the last-resort reading is not the
   panel, it is the **window metrics**, which are already bar-reduced. `init()` used to skip the
   inset add-back for any display-sourced reading, so the very first session on a unit that has
   measured nothing yet double-shrank the same way. **R4 is the only run that has ever tested this.**
3. **The Moto's hash wobble.** Its second `Honest Init` read `(from DISPLAY)` at `2300x1017` after a
   mid-connect settings-hash change of `+1890`. Only the panel terms can produce a delta that small,
   and solving it puts the first reading at `2237x1080`: the same 63 px decoration counted on a
   different axis. So the panel reading invalidated the good window measurement and then supplied the
   fallback itself. It cost a spurious init, a resolution unlock, and two `UpdateUiConfigRequest`
   frames that undid each other.

Plus the live window reading had **no orientation normalisation at all** where the cache has one,
which is why the Moto adopted the portrait `1080x2400` for 61 ms with `chosen=_1080x1920`.

### The candidate

`fix/video-fit-and-ultrawide-touch` is now **five commits** on `main` `12706e26`, tip `89093db4`.
Round 7's `fd4819b0` and `409056f4` were compacted into `94efd6df` with the fixes folded in, so
**those two SHAs no longer resolve on the branch**; cite the new ones. The last commit is separate on
purpose so R6 can A/B it alone:

- **`94efd6df` Screen config: announce the canvas the video is drawn into, and follow it.** The
  anchor now remembers the canvas it came from and is re-derived when the insets move under it.
  `SystemUI.apply` reads the window's content area **before** it re-seeds the manual insets, so the
  content size and the insets in force describe the same layout. Outside immersive the display
  fallback carries the insets like any other canvas. New `utils/ScreenOrientationPolicy` is the one
  copy of the portrait/landscape swap, used by all five readings. `SettingsActivity` no longer
  describes a canvas at all.
- **`89093db4` Screen config: a wobbling display reading no longer drops a measurement.** New
  `utils/ScreenSettingsHash` folds the effective orientation where the panel metrics used to be, and
  a stored canvas is bounded by the panel proportionally rather than exactly.

### Log lines that changed, and this is the one thing that will waste time if missed

- **Changed:** the `Honest Init` source `DISPLAY` outside immersive now means the **window metrics**,
  not the panel, and its anchor carries the insets. The word in the log line is unchanged.
- **Changed:** `anchor WxH -> WxH, measured on ...` gains a re-derivation form,
  `measured on the activity window, against the insets now in force`. It means the insets moved, not
  that anything was re-measured. It is expected, and one per inset change is normal.
- **New:** `CarScreen: canvas returned to the announced WxH`. The `canvas moved from the announced`
  line used to latch and could never retract, and round 7 fired it on a 140 ms transient. The pair
  now reads honestly, and after four lines it stops reporting and says so.
- **Unchanged and still absent on the candidate:** `Using cached surface dimensions` and
  `Cache invalidated (hash mismatch`. R8 of round 7 settled those; do not re-grep them.

---

## 1. R0 build and unit-test gate

- **Candidate:** `fork/fix/video-fit-and-ultrawide-touch` @ `89093db4` (take the exact SHA from this
  thread's `README.md` router row when you start).
- **Baseline:** round 7's candidate, `409056f4`. It is **no longer on the branch** and is reachable
  only from the fork ref round 7 pushed or by SHA; if neither resolves, say so and run the
  candidate-only runs, which is every run except R1's baseline arm.

Build the candidate with `hur-wifi-test-scripts/build_hur.sh` under `thermal_guarded.sh`. Run
`run_unit_tests.sh` on the candidate only.

Gate: **candidate green, 1499 tests / 0 failures** (round 7 was 1482/0). Two new classes,
`ScreenOrientationPolicyTest` (6) and `ScreenSettingsHashTest` (5), plus `ProjectionCanvasPolicyTest`
at 14 (was 8) and `TouchCoordinateMapperTest` unchanged at 7.

**Identity.** Version name and versionCode do not move between these builds, so carry identity by
live APK md5 re-read per device **and** by symbols the baseline cannot contain:

```bash
PKG=com.andrerinas.headunitrevived
adb shell pm path $PKG                      # pull that apk, then
unzip -p <apk> 'classes*.dex' | strings | grep -F 'ScreenOrientationPolicy'   # candidate only
unzip -p <apk> 'classes*.dex' | strings | grep -F 'ScreenSettingsHash'        # candidate only
unzip -p <apk> 'classes*.dex' | strings | grep -F 'ProjectionCanvasPolicy'    # both
```

Record both md5s. If the build or the unit run fails, **stop and report**.

---

## 2. Standing settings and the ground truth

Written to `shared_prefs/settings.xml` with the app force-stopped, never through the UI. D-HU is
rooted; use `r6_set_hu_prefs.sh` for multi-key writes, `set_prefs_runas.sh` for D-MOTO. Back up
`settings.xml` per device and restore it at the end; leave any `use_measured_touch_surface` key in
place, it is the marker that the file was not rewritten.

| Key | Value | Why |
|---|---|---|
| `resolutionId` | `2` (720p) on D-HU, `3` on D-MOTO | the rungs rounds 6 and 7 measured |
| `video-fit-mode` | `0` (FILL) | the only mode that carries a pixel shape |
| `log-level` | `2` (INFO), **`0` (VERBOSE) for R7** | `Touch map` is `AppLog.v`; everything else here is `AppLog.i` |
| `fullscreen-mode` | per run: **0 NONE, 1 IMMERSIVE, 2 STATUS_ONLY** | the round's independent variable |

`hur-wifi-test-scripts/ultrawide_touch_r7.sh` is the driver added last round
(`PHASE=connect|taps|end`); it is what lets a tap target be read off the live arm and the framework
frame be captured while the projection is up. Reuse it.

**Two errata from round 7 that apply to every run here.** `ultrawide_touch_r3.sh` waits for
`Handshake: SSL handshake complete`, which is a **DEBUG** line and absent at INFO; the INFO line is
`AapSslContext.performHandshake | SSL handshake complete.` Match on `SSL handshake complete` without
the prefix. And **tap 5 on D-HU is `1284,360`, not `1420,360`**: the panel's right 136 px is the OEM
bar and `1420` lands on its Home key, which backgrounds the app and ends the session.

### Which framework figure is the canvas

`dumpsys` reports three rectangles for the projection activity and only one is the drawn area. Quote
all three in the results, as round 7 did:

| figure | what it is |
|---|---|
| `mBounds` | the display |
| `Frames: frame=` | the window layout, which is the **whole panel** because the OEM bar is an inset on the content view rather than a smaller window |
| `mAppBounds` | the content area, so **this is what an anchor is scored against** |

```bash
adb shell dumpsys window windows | grep -i "com.andrerinas.headunitrevived" -A 22
adb shell wm size ; adb shell wm density
```

A verdict of PASS below means the app's `Anchor:` agrees with the drawn content area, not that the
app is self-consistent.

---

## 3. R1: D-HU, STATUS_ONLY, the decisive A/B

`fullscreen-mode=2`, INFO. Baseline arm first if the baseline APK resolves, then the candidate.
Standard clean-run protocol. Let the session form, leave it 60 s, exit. Screencap the panel with the
projection up (`adb shell screencap`, which on this unit captures the OEM bar too and is strictly
better than a photo).

Record, verbatim with timestamps, **every** occurrence of each on both arms:

- `Honest Init | Mode: STATUS_ONLY | Anchor: ...`
- every `[RES_CAP] ... realScreen=... usable=...`
- every `CarScreen isSmallScreen: ... shape=... margins: w=..., h=...`
- every `anchor WxH -> WxH, measured on ...`
- `[ServiceDiscovery] NegotiatedResolution is:` and `PixelAspectRatioE4 is:`
- `Normal Scale. scaleX: ..., scaleY: ...`
- the three framework figures from §2

**Candidate PASS** requires all of:

1. **`usable=1168x720` appears zero times in the whole capture**, and `shape=MARGIN` appears zero
   times. These are the round 7 signature and their absence is the point of the run.
2. The final `Anchor` equals the window layout frame on both axes, and the steady-state `usable`
   equals the drawn content area, each within 4 px.
3. `PixelAspectRatioE4 is: 10000`, and the picture fills the area inside the bar with no letterbox
   the mode did not create.
4. At most **one** `Honest Init`.

An `anchor ... measured on the activity window, against the insets now in force` line **is expected**
here: it is the fix doing its job when the bar arrives. Count them and quote them; more than three is
worth reporting.

**Baseline arm has no PASS condition.** It is the measurement that shows the transient this run
exists to remove. Report whether `usable=1168x720` and `shape=MARGIN, w=112` appear, and for how long.

---

## 4. R2: D-HU, Screen mode Normal, regression guard

`fullscreen-mode=0`, INFO, candidate only. **PASS** is bit-identical to round 7 R2:
`Anchor: 1304x720 (from WINDOW)`, one `Honest Init`, `[RES_CAP] realScreen=1304x720 usable=1304x720`,
`NegotiatedResolution 1280x720`, `Margins 0x0`, `PixelAspectRatioE4 10000`, `Normal Scale 1.0/1.0`,
`shape=PAR`. Any difference from those seven figures is the finding.

## 5. R3: D-HU, Immersive, regression guard

`fullscreen-mode=1`, INFO, candidate only. **PASS** is bit-identical to round 7 R4:
`Anchor: 1440x720 (from WINDOW)`, `1280x720`, `0x0`, `PAR 11250`, `1.0/1.0`, `shape=PAR`.

---

## 6. R4: D-HU, Screen mode Normal, from nothing measured

**The run no round has done, and the one behaviour change here with no hardware history.** It tests
the fresh-install path, where the anchor comes from the window metrics because nothing else exists.

Setup, in this order, candidate only, `fullscreen-mode=0`, INFO:

1. `adb shell pm clear com.andrerinas.headunitrevived`, which wipes the surface cache **and every
   setting**, which is the state being tested.
2. Write the standing settings from §2 into the now-empty `settings.xml` with the app stopped.
3. Clean-run protocol as usual.

**PASS** requires:

1. The **first** `Honest Init` of the process announces an `Anchor` equal to the window layout frame
   within 4 px, whatever source it names.
2. The **first** `[RES_CAP]` line's `usable=` equals the drawn content area within 4 px. It must not
   be 136 px short of it, which is the double-shrink this run is for.
3. `PixelAspectRatioE4` is derived from that `usable`, per the arithmetic in R2.

If the first `Honest Init` says `(from WINDOW)` because a MainActivity layout beat it, the run is
still valid, but say so: the fresh-install path was then not the one exercised, and note whether a
`(from DISPLAY)` line appears anywhere in the capture.

**A note on the wizard.** A cleared install re-runs first-time setup, which writes a resolution of
its own. Read the settings back after step 2 and after the run, and report both; a wizard write that
lands between them changes the hash and is a legitimate second `Honest Init`, not a failure.

---

## 7. R5: D-MOTO, Self Mode, Immersive

`fullscreen-mode=1`, `video-fit-mode=0`, `resolutionId=3`, INFO, candidate.

**Precondition, and it needs the operator once.** Gearhead 17.4 and newer will not start Self Mode
without Android Auto's developer **"Start head unit server"** on `127.0.0.1:5277`, and there is no
adb route to that toggle. Verify it **before** the run rather than discovering it from a failed
launch:

```bash
adb -s <D-MOTO> shell cat /proc/net/tcp | grep -i :149D     # 0x149D == 5277
```

If nothing is listening, hold the run, have the operator tap it, then continue scripted.

**PASS** requires all of:

1. Exactly **one** `Honest Init` for the session.
2. Its source is `(from WINDOW)` or `(from SURFACE)`. **`(from DISPLAY)` anywhere in the capture is
   the finding**, and if it appears, quote the `Settings changed (A -> B)` line that precedes it with
   both hashes.
3. **No portrait reading is ever adopted.** `anchor ... -> 1080x2400` must appear zero times and no
   `chosen=_1080x1920` may appear.
4. At most one `margins drifted from the announced` line and at most one
   `[UI_DEBUG_FIX] TX UpdateUiConfigRequest`. Round 7 had two of each, the second undoing the first.

Also record the settled `Margins are:` and `Normal Scale` and compare with round 7's `0x216` and
`scaleY 1.25`; they should be the same, reached without the excursion through `0x231` / `1.2720848`.

Then two taps, targeted on each icon's visible centre read off a live screencap: the bottom-most rail
icon and the top-most. Both must open the control they were aimed at, as they did in round 7. Verdict
from which app opened, from a screencap, not from the `Touch map` line.

## 8. R6: the A/B the last commit earns

Same as R5, on a build of **`94efd6df`** (the candidate with `89093db4` reverted). This is what shows
the `(from DISPLAY)` fall-through is that commit's to remove rather than something else's.

Expected if the reading is right: this arm reproduces round 7's second `Honest Init` at
`(from DISPLAY)` with a `Settings changed` line before it, and R5's arm does not. If **both** arms are
clean the commit is unproven on this rig rather than wrong; say so and quote both captures.

Skip this run and say why if R5 failed for any reason other than condition 2.

---

## 9. R7: D-HU touch in STATUS_ONLY

`fullscreen-mode=2`, **`log-level=0`**, candidate only. Round 7 ran the touch A/B in Normal and never
in this mode, where the canvas and the panel differ *and* an inset is live.

Five synthetic taps via `input tap`, targets read off the live screencap: the bottom-most rail icon,
the top-most rail icon, the panel centre, 20 px inside the left edge, and `1284,360` (see §2).

**PASS** requires:

1. Every `Touch map` line reads `view=WxH` equal to the drawn content area, not the 1440 display.
2. No tap's mapped `video=` Y equals the buffer height unless the tap really was on the bottom edge.
3. Both rail taps open the control they were aimed at.

Note for reading the phone side: a `gearhead/meb injectMotionEvent` coordinate can sit about 88 px
left of the mapped one, because Android Auto's own rail is inside the video. That is composition, not
a mapping error, and round 7 saw it on both arms. Score on `GhFacetBar` lines and on which control
opened.

---

## 10. What to report

The fixed format from §7 of the template. Beyond it, three things this round should say plainly:

- whether `usable=1168x720` occurred at all, on either arm of R1;
- whether the fresh-install path in R4 was genuinely exercised, or whether a window layout beat it;
- whether R6 separated the two arms, or whether the hash commit is unproven on this rig.

Every deviation goes in Setup notes. Restore `settings.xml` md5-identically per device and say which
APK each device is left carrying.
