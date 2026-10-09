# Round 1 brief, rotation geometry and whether Android Auto re-negotiates

Read `TESTING-TEMPLATE.md` first. Three things in it decide this round: §1's "app stopped" rule for
every setting write, §7a's D-POCO entries, and the rule that a verdict on a picture needs a
photograph beside a baseline photograph, not a log line.

This round has two halves that must not be mixed. **Part A** grades a fix that stands on its own and
changes default behaviour. **Part B** fires four levers at the phone that have never been fired, and
its job is to produce an answer either way. A Part B run that ends "the phone acknowledged and
nothing moved" is a **result**, not a failure.

---

## 1. Build

**Part A candidate:** `fix/rotation-keeps-negotiated-geometry` @ `a03aeb3d` on `fork`, one commit on
`origin/main` @ `80a81099` (`v.3.4.0`).
**Part B candidate:** `probe/geometry-renegotiation` @ `24a12300`, one commit on top of the above.
**Baseline (A1 only):** `origin/main` @ `80a81099`.

### R0, build gate

`run_unit_tests.sh`, then `build_hur.sh`, for each of the two candidates.

- Part A: full suite **2176** tests, green. `ProjectionOrientationPolicyTest` must exist and report
  **8**; `SessionGeometryLockPolicyTest` **4**. If either class is missing, the wrong commit is out.
- Part B: full suite **2187**, green. `GeometryProbePolicyTest` **6**, `MaxUnackedPolicyTest` **4**,
  `ProjectionOrientationPolicyTest` now **9**.

Record each APK md5 and confirm which is live per §5. **If R0 fails, stop and report.**

---

## 2. What this is

Geometry is negotiated once, in the service discovery response, and frozen: the app calls
`HeadUnitScreenConfig.lockResolution()` the moment the handshake completes, precisely so a rotation
cannot re-negotiate. Rotate the panel under a live session and the buffer keeps the shape the phone
was told about while the view scales it into the new window, which is the squeezed picture reporters
describe. The full read is `ohu-fixes-handoff/996-rotation-analysis.md`.

Three things made that worse and Part A fixes them:

1. The orientation pin covered only the **Auto** setting. The default is **System Default**, so a
   default install was the one configuration that could rotate mid-session.
2. On a mismatch the app dropped the lock and re-derived a resolution, scales and margins for the
   new orientation that nothing can put on the wire, so the local model and the phone disagreed.
   That is the best candidate for "rotating back does not fully recover it".
3. The TextureView backend, which is the default, never reported that the canvas had moved, so its
   margins were never corrected.

Part B exists because Google's own schema comments say two levers should work and neither has been
measured. `UiConfig` says a resolution change belongs to **service rediscovery**. `Config` says the
final one received supersedes all previous ones and that a READY arriving mid-stream stops the source
and restarts it on the new configuration. Against that, nothing in the phone's own code appears to
resize a projected display once it is built. The round settles which of those two readings wins.

---

## 3. Preconditions

**Unit:** D-POCO for the Self Mode runs, where it is both phone and head unit. The two-device runs
are an ordinary Native AA session, D-HU as head unit with D-POCO as the phone.

**Self Mode needs Android Auto's "Start head unit server" developer toggle on**, and it is UI only:

```bash
adb -s <serial> shell cat /proc/net/tcp | grep -i :149D     # 0x149D == 5277
```

See `rig-dpoco-headunit-server-down` in §7a.

**Set VERBOSE**, and set the orientation setting explicitly per run. Every write with the app
stopped, per §1.

| Key | Type | Value |
|---|---|---|
| `log-level` | int | `0` (VERBOSE) |
| `log-source` | int | `1` (APPLOG_FILE) |
| `log-capture-enabled` | boolean | `true` |
| `screen-orientation` | int | `0` System Default, `1` Auto, `2` Landscape |
| `view-mode` | int | `1` TextureView (the default) except where a run says otherwise |
| `geometry-probe-mode` | int | `0` in Part A; the run's mode in Part B |
| `geometry-probe-real-density` | boolean | `false` except B5 |

**The device's own auto-rotate must be on** for any run that rotates, or nothing turns and every
verdict below is vacuous. Confirm it and say so in Setup notes:

```bash
adb shell settings get system accelerometer_rotation    # 1 == on
adb shell settings put system user_rotation 0           # 0 portrait, 1 landscape
```

`user_rotation` is the injector for every rotation in this round. Use it rather than turning the
device by hand: it is timestamped and repeatable.

**A photograph is required evidence** for A1, A2, A3 and every Part B run that reports a change.
Frame the whole panel, same distance and angle as the baseline shot, with a map or a list on screen
so proportions are readable. Name the files `rN-before.jpg` and `rN-after.jpg`.

---

## 4. The lines that decide every run

| Meaning | Level | Line |
|---|---|---|
| the pin held the panel | I | `[UI_DEBUG] Sticky Orientation: session active, forcing orientation to` |
| the lock was kept on a mismatch | I | `[UI_DEBUG] CarScreen: Orientation mismatch detected (...), but the canvas ... is already announced` |
| the lock was dropped | I | `[UI_DEBUG] CarScreen: Orientation mismatch detected (...). DROPPING LOCK.` |
| the canvas moved under a live surface | I | `[UI_DEBUG] [AapProjectionActivity] onSurfaceResized:` |
| what went on the wire at connect | I | `[ServiceDiscovery] NegotiatedResolution is:` |
| a probe fired | I | `[GEOMETRY_PROBE] mode=N firing for a ... canvas` |
| what the probe sent | I | `[GEOMETRY_PROBE] TX ...` |
| the phone re-picked a configuration | I | `Media Start Request VIDEO: session=N, config_index=N` |
| the phone stopped its sink | I | `Media Sink Stop Request: VIDEO` |
| the decoder saw new dimensions | I | `[AapProjectionActivity] Received video dimensions:` |

Standing counts:

```bash
grep -c 'Sticky Orientation'                rN.txt
grep -c 'DROPPING LOCK'                     rN.txt
grep -c 'is already announced'              rN.txt
grep -n  'Media Start Request VIDEO'        rN.txt
grep -c  'Media Sink Stop Request: VIDEO'   rN.txt
grep -n  'GEOMETRY_PROBE'                   rN.txt
grep -n  'Received video dimensions'        rN.txt
grep -n  'Throughput over'                  rN.txt | tail -5
```

---

## 5. Part A runs

Run **A0 first**. It is the regression guard.

### A0, a session that never rotates is untouched

Self Mode, `screen-orientation` = `2` (Landscape), device held landscape, projection up for two
minutes with a map moving.

**PASS:** a normal `Throughput over` series with `rendered` tracking `fed`, **zero** `DROPPING LOCK`,
**zero** `is already announced`, and the picture correct in a photograph. **FAIL:** any of those
counts moves, or the picture is not what the baseline shows.

### A1, the defect on this rig, baseline

`origin/main` @ `80a81099`, Self Mode, `screen-orientation` = `0` (System Default), auto-rotate on.
Start landscape, let the projection settle, photograph, then:

```bash
adb shell settings put system user_rotation 0
```

Wait 10 s, photograph again, then rotate back and photograph a third time.

Report: (a) whether the panel turned at all; (b) whether the picture is distorted after the turn;
(c) whether it recovers on turning back; (d) the `DROPPING LOCK` count; (e) the `config_index` in
every `Media Start Request VIDEO`.

**This run has no verdict on the branch.** If the picture is not distorted here, the rig does not
reproduce the fault: say so and mark A2 **INCONCLUSIVE**.

### A2, the pin holds System Default

Part A candidate, same settings as A1, same sequence.

**PASS:** the projection **does not turn**, one `Sticky Orientation` line naming the negotiated
orientation, **zero** `DROPPING LOCK`, the picture in all three photographs identical to A0's, and
the session still rendering afterwards. **FAIL:** the panel turns, or the picture changes.

### A3, the same under Auto

Part A candidate, `screen-orientation` = `1` (Auto), otherwise as A2. Same PASS condition. This
confirms the setting that was already pinned did not regress.

### A4, the TextureView backend now reports its canvas

Part A candidate, `screen-orientation` = `2`, `view-mode` = `1`. Bring the session up, then make the
canvas move without rotating: toggle the navigation bar or the status bar, whichever this unit
allows, or change `fullscreen-mode` between runs with the app stopped.

**PASS:** at least one `onSurfaceResized` line, followed by a `TX UpdateUiConfigRequest`, and the
picture still correct. **FAIL:** the canvas visibly moves with no `onSurfaceResized` line.
**INCONCLUSIVE** is a fair answer if this unit gives no way to move the canvas without rotating; say
which you tried.

### A5, two devices, no regression

Part A candidate on D-HU, ordinary Native AA session with D-POCO as the phone, `screen-orientation`
= `2`, five minutes with a map moving.

**PASS:** SSL, projection, and a `Throughput over` series no worse than A0's, with zero
`DROPPING LOCK`. This is the run that says the change stayed out of the ordinary path.

---

## 6. Part B runs

Part B candidate. Every run: Self Mode first, then the same run on two devices. **Report both.** In
Self Mode the head unit server is Android Auto on the same device, so rotating turns both ends at
once and anything that changes there could be the phone reacting to its own rotation. A change seen
only in Self Mode proves nothing.

Each run is the same sequence: session up in landscape, photograph, `user_rotation 0`, wait 20 s,
photograph, capture, rotate back.

| Run | `geometry-probe-mode` | What is being fired |
|---|---|---|
| B1 | 1 | `ServiceDiscoveryUpdate`, control message 26 |
| B2 | 2 | `Media.Config` STATUS_READY selecting index 1 |
| B3 | 3 | `Media.Config` STATUS_WAIT, then STATUS_READY index 1 |
| B4 | 4 | `Media.Config` index 1, then a video focus cycle |
| B6 | 5 | `UpdateUiConfigRequest` carrying `ui_theme` |

**There is no PASS or FAIL in Part B.** For each run report, in this order:

1. The `[GEOMETRY_PROBE] TX` line, verbatim, proving the lever went out.
2. Whether the phone answered anything at all within 10 s: a `Media Sink Stop Request: VIDEO`, a new
   `Media Start Request VIDEO` and its `session=` and `config_index=`, or a status of `-8`
   (`STATUS_MEDIA_CONFIG_MISMATCH`) anywhere in the capture.
3. Whether `Received video dimensions` reports anything new.
4. Whether the photograph changed.
5. Whether the session survived: a `Throughput over` line after the probe with `rendered` non-zero.

**B2 to B4 also answer a question at connect**, before any rotation: the app announces two video
configurations in those modes, and the phone's first `Media Start Request VIDEO: session=0,
config_index=` says which one it chose unprompted. Record it for each of the three. If it is ever
`1`, stop the round and report immediately: the phone would be picking a configuration the panel is
not in, and that is a finding that changes the branch.

**If any B run ends the session or blacks the picture**, that is also a result. Record the last three
`Throughput over` lines and whether a reconnect recovers it.

### B5, real_density

Part B candidate, `geometry-probe-mode` = `0`, `geometry-probe-real-density` = `true`,
`screen-orientation` = `4` (Portrait), `dpi-pixel-density` = `0` (Auto). No rotation in this run.

Bring a Self Mode session up in portrait, open Google Maps inside Android Auto, and try to pan and
tap. Then repeat with Waze.

Report: the `[ServiceDiscovery] real_density is:` value beside the `density` in the same block;
whether touch works in each app; and whether it works without setting pixel density below 200. Then
repeat the whole run with `geometry-probe-real-density` = `false` as the control. Touch working is a
sensory observation, so describe what you did and what happened.

---

## 7. What to leave behind

Per §7, the fixed results format, plus:

- every photograph, named as above;
- the full capture for each run;
- for each Part B run, the verbatim `[GEOMETRY_PROBE]` block and the ten lines after it.

If the whole of Part B comes back "acknowledged, nothing moved", say so plainly. That retires two
levers the protocol reference has carried as untested for a month, and it is the more likely of the
two outcomes.
