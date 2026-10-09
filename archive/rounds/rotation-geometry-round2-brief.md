# Round 2 brief, rotation geometry: the probe, wired so it can fire

Read `TESTING-TEMPLATE.md` first. Read `rotation-geometry-round1-results.md` too, all of it,
because this round repeats almost nothing from round 1 and every omission below is deliberate.

Round 1 settled Part A and could not start Part B. Part A passed on hardware and is unchanged
except for one log line. Part B's five levers were never put on the wire at all, so nothing is known
about what Android Auto does with any of them. This round exists to ask the question round 1 could
not reach.

---

## 1. Build

**Part A candidate:** `fix/rotation-keeps-negotiated-geometry` @ `65e91b56` on `fork`, two commits
on `origin/main` @ `80a81099` (`v.3.4.0`).
**Part B candidate:** `probe/geometry-renegotiation` @ `13d29c61`, two commits on top of the above.

Both SHAs moved since round 1. Round 1's `a03aeb3d` and `24a12300` are gone; do not build them.

### R0, build gate

`run_unit_tests.sh`, then `build_hur.sh`, for each candidate.

- Part A: full suite **2176** tests, green. `ProjectionOrientationPolicyTest` **8**,
  `SessionGeometryLockPolicyTest` **4**. Same counts as round 1, because the only change is a log
  line.
- Part B: full suite **2193**, green. `GeometryProbePolicyTest` now **12** (it was 6),
  `MaxUnackedPolicyTest` **4**, `ProjectionOrientationPolicyTest` **9**.

Record each APK md5 and confirm which is live per §5. **If R0 fails, stop and report.**

---

## 2. What changed since round 1, and why

Round 1's headline was that `grep -c GEOMETRY_PROBE` was 0 in all five runs. That reading was right
and the diagnosis in the results file was close: the probe was gated behind
`HeadUnitScreenConfig.updateSurfaceDimensions()` returning true, which is an adopt of a new canvas,
and the app is deliberately built never to adopt one mid-session. Several paths race to satisfy that
gate on a rotation and every one that loses returned false without printing a word, so the round
could not say which link was dark.

Three things are different now:

1. **A configuration change is the trigger.** With a probe armed, `onConfigurationChanged` refreshes
   the normalisation, adopts the rotated canvas itself, and then fires. It no longer waits for a
   verdict that nothing produces.
2. **Every gate names itself.** A probe that does not fire prints
   `[GEOMETRY_PROBE] mode=N not fired: MODE_OFF|NO_TRANSPORT|ALREADY_FIRED` on the line where it
   stopped, and `updateSurfaceDimensions` now gives a reason on the exits that used to be silent.
   If this round reports "the probe did not fire", the line above it says why.
3. **Mode 1 asks a real question.** Its `ServiceDiscoveryUpdate` is built from the geometry in force
   at the moment it fires, and with the adopt now unconditional that geometry is the rotated one.
   In round 1 it would have re-announced the unchanged landscape shape.

What has **not** changed: `liftsOrientationPin` is still true for every armed mode, so the panel
still turns under a probe and the picture still starts out as the round 1 squeeze. That is the
probe's precondition, not a defect. See §6 for what the photograph is actually grading now.

---

## 3. Preconditions

**Unit:** D-POCO, Self Mode, for every run in this round. There is no two-device arm. D-HU cannot be
rotated on the operator's instruction and the probe needs a genuine canvas flip, so a two-device
repeat is untestable on this rig rather than skipped. Do not attempt one.

**Self Mode needs Android Auto's "Start head unit server" developer toggle on**, and it is UI only:

```bash
adb -s <serial> shell cat /proc/net/tcp | grep -i :149D     # 0x149D == 5277
```

Round 1 found a second way this wedges: Gearhead raising its own permission dialog steals foreground
and leaves the dev server accepting a TCP connection and then dropping it, which shows up as
`java.net.SocketException: Broken pipe` right after the version request. Dismissing the dialog is not
enough; recycle the developer toggle. See §7a.

**Set VERBOSE**, and set the orientation setting explicitly per run. Every write with the app
stopped, per §1.

| Key | Type | Value |
|---|---|---|
| `log-level` | int | `0` (VERBOSE) |
| `log-source` | int | `1` (APPLOG_FILE) |
| `log-capture-enabled` | boolean | `false`, and use the external `adb logcat` capture per §2 |
| `screen-orientation` | int | `0` System Default, except A2r which says so itself |
| `view-mode` | int | `1` TextureView (the default) |
| `geometry-probe-mode` | int | the run's mode |
| `geometry-probe-real-density` | boolean | `false` except the B5 arms |
| `dpi-pixel-density` | int | `0` (Auto) except the B5 arms |

Round 1 left `log-capture-enabled` at `false` on purpose, because setting it true raises SystemUI's
`LogAccessDialogActivity` on every launch on this Android 15 unit and needs a manual tap each time.
That was the right call and this brief adopts it: the external capture carries every line below.

**Rotation recipe.** Round 1 lost three runs to getting this wrong, so use it exactly:

```bash
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0          # 0 portrait, 1 landscape
# wait several seconds, THEN force-stop and relaunch the app
```

Three traps round 1 measured and this round should not re-discover: `dumpsys display`'s `rotation`
field reported `0` through genuine rotations on this unit and is not a live check, so trust
`HeadUnitScreenConfig`'s `Raw size:` line or a screenshot; the launcher is portrait-locked whatever
these settings say, so it is not a rotation control; and `adb` cannot drive a window that is in
`SCREEN_ORIENTATION_SENSOR`, which is what the Auto setting resolves to before the pin lands, so an
Auto run needs the device turned by hand.

**Screenshots, not camera photographs**, per the operator's standing instruction:
`adb exec-out screencap`, run sequentially, never in parallel or in a loop. Name them
`<run>-before.png` and `<run>-after.png`.

---

## 4. The lines that decide every run

| Meaning | Level | Line |
|---|---|---|
| the pin held the panel | I | `[UI_DEBUG] Sticky Orientation: ..., already at orientation N` |
| the pin moved the panel | I | `[UI_DEBUG] Sticky Orientation: ..., forcing orientation to N` |
| the pin declined | I | `[UI_DEBUG] Sticky Orientation: not pinned under` |
| the probe read the rotation | I | `[GEOMETRY_PROBE] normalisation` |
| the probe took the new canvas | I | `[GEOMETRY_PROBE] adopting canvas WxH -> WxH` |
| a probe fired | I | `[GEOMETRY_PROBE] mode=N firing for a ... canvas` |
| a probe refused, with its reason | I | `[GEOMETRY_PROBE] mode=N not fired:` |
| what the probe sent | I | `[GEOMETRY_PROBE] TX ...` |
| a surface change was declined, with its reason | I | `[UI_DEBUG_FIX] Surface ... nothing to correct.` |
| the lock was dropped for the probe | I | `[UI_DEBUG] CarScreen: Orientation mismatch detected (...). DROPPING LOCK.` |
| the lock was kept | I | `[UI_DEBUG] CarScreen: Orientation mismatch detected (...), but the canvas ... is already announced` |
| what went on the wire | I | `[ServiceDiscovery] NegotiatedResolution is:` |
| the phone re-picked a configuration | I | `Media Start Request VIDEO: session=N, config_index=N` |
| the phone stopped its sink | I | `Media Sink Stop Request: VIDEO` |
| the decoder saw new dimensions | I | `[AapProjectionActivity] Received video dimensions:` |

Standing counts:

```bash
grep -n  'GEOMETRY_PROBE'                   rN.txt
grep -c  'Sticky Orientation'               rN.txt
grep -c  'DROPPING LOCK'                    rN.txt
grep -n  'ServiceDiscovery] NegotiatedResolution is' rN.txt
grep -n  'Media Start Request VIDEO'        rN.txt
grep -c  'Media Sink Stop Request: VIDEO'   rN.txt
grep -n  'Received video dimensions'        rN.txt
grep -n  'Throughput over'                  rN.txt | tail -5
```

**A status of `-8` anywhere in a capture is the most valuable single line this round can produce.**
It is `STATUS_MEDIA_CONFIG_MISMATCH` in Google's own table, and it separates "the phone never read
our message" from "the phone read it and refused". Grep for it explicitly in every Part B run.

---

## 5. Part A: one run

Round 1's A0, A3 and A5 carry forward. The only change on this branch since they passed is a log
line, so they are not repeated.

### A2r, the pin holds System Default and says so

Part A candidate, Self Mode, `screen-orientation` = `0` (System Default), auto-rotate on, session up
in landscape, screenshot, rotate with the §3 recipe, wait 20 s, screenshot, rotate back.

**PASS** requires all four:

1. The projection never turned. `Raw size:` does not flip and the two screenshots are the same
   landscape layout.
2. `Sticky Orientation` appears at least once, naming the negotiated orientation. Round 1 could not
   grade this because the line only printed when the pin had to change a value; that is what the
   Part A commit fixes, so a run with no `Sticky Orientation` line at all is now a **FAIL** and not
   an artefact.
3. `DROPPING LOCK: 0`.
4. Throughput holds with no dropped frames through both rotation attempts.

---

## 6. Part B: the five levers

Part B candidate, Self Mode, D-POCO only. Every run is the same sequence: session up in landscape,
screenshot, rotate to portrait with the §3 recipe, wait 20 s, screenshot, capture, rotate back,
capture again.

| Run | `geometry-probe-mode` | What is being fired |
|---|---|---|
| B1r | 1 | `ServiceDiscoveryUpdate`, control message 26 |
| B2r | 2 | `Media.Config` STATUS_READY selecting index 1 |
| B3r | 3 | `Media.Config` STATUS_WAIT, then STATUS_READY index 1 |
| B4r | 4 | `Media.Config` index 1, then a video focus cycle |
| B6r | 5 | `UpdateUiConfigRequest` carrying `ui_theme` |

**There is no PASS or FAIL in Part B.** Every outcome is a result, including "the phone acknowledged
and nothing moved", which remains the likelier one. For each run report, in this order:

1. **The `[GEOMETRY_PROBE]` block, verbatim**: the `normalisation` line, the `adopting canvas` line,
   and then either `firing for a ... canvas` or `not fired: <reason>`. If it did not fire, that
   reason is the finding and the rest of the run is context.
2. For B1r only: the `[ServiceDiscovery] NegotiatedResolution is:` line that the update carried, and
   whether it is the portrait shape or still the landscape one.
3. Whether the phone answered anything within 10 s: a `Media Sink Stop Request: VIDEO`, a new
   `Media Start Request VIDEO` with its `session=` and `config_index=`, or a `-8` anywhere.
4. Whether `Received video dimensions` reports anything new.
5. **What the after-screenshot shows, in three named outcomes**, and this is the question round 1
   asked wrong. The panel turns in every armed mode because the probe lifts the orientation pin, so
   "did the picture change" is answered yes by the pin alone and measures nothing. The three
   outcomes are: **a correct portrait layout**, which is the phone re-laying-out and is the only
   positive result this round can produce; **the same squeeze** as `evidence/rotation-geometry-round1/a1-after.png`,
   which is the landscape buffer stretched into a portrait window and means the phone did not act;
   or **a frozen or black picture**. Say which of the three, and say it against that reference image.
6. Whether the session survived: a `Throughput over` line after the probe with `rendered` non-zero.
   Round 1's B3 and B6 stalled to near-zero throughput and never recovered inside the observation
   window, while B1, B2 and B4 recovered normally. **No lever was sent in any of those runs**, so
   that split cannot have been caused by a probe payload and is not a prior for this round. Record
   the stall per run again. Only now, with the levers actually going out, does a stall that follows
   the mode mean anything.

**Connect-time `config_index`.** B2r, B3r and B4r announce two video configurations, so the phone's
first `Media Start Request VIDEO: session=0, config_index=` says which one it chose unprompted.
Round 1 read `0` for all three. Record it again. If it is ever `1`, stop the round and report: the
phone would be picking a configuration the panel is not in.

**Known limitation, carried from round 1 and unchanged.** Touch bounds are announced once at the
negotiated video size and cannot be re-announced, because our `InputSourceService.touchscreen` is
singular where Google's is `repeated TouchScreen`. If a lever does change the picture, taps will map
to the old size. That is expected; note it rather than reporting it as a defect.

---

## 7. B5r, real_density and the reporter's density workaround

Round 1 ran both B5 arms at `dpi-pixel-density` = `0`, so both measured the panel's native 440 and
the reporter's claim that Maps and Waze lose touch in portrait unless pixel density is set below 200
went untested. This round runs the 2x2 that tests it.

`geometry-probe-mode` = `0`, `screen-orientation` = `4` (Portrait, fixed), no rotation in any arm.

| Arm | `geometry-probe-real-density` | `dpi-pixel-density` |
|---|---|---|
| B5r-a | `false` | `0` (Auto, the panel's 440) |
| B5r-b | `true` | `0` |
| B5r-c | `false` | `180` |
| B5r-d | `true` | `180` |

For each arm: the `[ServiceDiscovery] real_density is:` line when present, and whether a swipe-pan on
the map in Google Maps recentres or re-zooms the way it should. A screenshot per arm.

The question the 2x2 answers is whether announcing the panel's true DPI separately from the layout
DPI removes the need for the sub-200 workaround. Arm B5r-c is the reporter's own configuration and
arm B5r-d is the candidate fix for it.

**Waze stays out of this round.** Round 1 could not drive the Android Auto app switcher because the
projected content is not in the accessible view hierarchy, so `uiautomator dump` gives no bounds and
coordinates are guesses. Maps in both orientations is the read-out.

---

## 8. What this round settles

If a lever fires and the phone answers with a sink stop and a new `Media Start Request VIDEO`
carrying `config_index=1` or a new resolution, mid-session re-negotiation is real and the whole issue
has a proper fix. If a lever fires and the phone answers `-8`, the message is read and the
configuration refused, which is also an answer and names which one. If every lever fires and the
phone acknowledges nothing at all, then service rediscovery and a second `Media.Config` are retired
as levers for this Gearhead build, and the analysis in
`ohu-fixes-handoff/996-rotation-analysis.md` says so permanently rather than saying "untested".

Any of those three is a complete round. The one outcome that is not is another capture with no
`[GEOMETRY_PROBE]` lines in it, and if that happens the `not fired:` reason is the whole report.
