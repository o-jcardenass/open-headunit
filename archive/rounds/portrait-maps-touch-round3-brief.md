# portrait-maps-touch round 3 brief

A candidate round on D-SAM, with D-POCO as the phone. It asks one question: does the candidate keep a portrait canvas at 730 dp or more on the phone, so that Google Maps takes touch with a DPI that failed before?

Rounds 1 and 2 found the cause. In portrait, Android Auto 18.0 uses a narrow layout when the phone's canvas is less than 730 dp wide, and Maps takes no touch in that layout. The phone's width includes the pixel aspect ratio stretch. The candidate clamps the portrait DPI to that width.

Publish as `portrait-maps-touch-round3-brief.md`. Results go in `portrait-maps-touch-round3-results.md`.

## 1. Build and baseline

| | |
|---|---|
| Candidate | `fork/fix/portrait-density-floor` @ `ec6cb51655fe9b210dfaa707d7ff3c8b1fb9ed39` (`ec6cb516`, "Display: keep a portrait canvas out of Android Auto's narrow layout"), one commit on `main` `d2d0a6bf` |
| Baseline | none. Rounds 1 and 2 on beta5 are the "before" (198 DPI portrait FAIL) |
| History | new branch, not rewritten |

```bash
git fetch fork fix/portrait-density-floor
git rev-parse fork/fix/portrait-density-floor     # must print ec6cb51655fe...
```

Build it in a detached worktree (`wt-new.sh`) with `build_hur_cool.sh`, after the thermal gate (rig-quirks `topics/tooling.md`, first entry). Copy the APK at once to `rig-data/apks/portrait-maps-touch-round3/candidate.apk` and record its md5. No unit tests on the rig: the branch passes 3247 JVM tests.

Install with `adb -s 30041c35642d2200 install -r`. Do not uninstall. D-SAM has no `md5sum`, so check the APK identity as round 2 did: pull the installed APK and hash it on the host. It must equal `candidate.apk`'s md5.

## 2. What this is and why

Measured in rounds 1 and 2 on beta5 (phone `w` from the phone's `contentArea:` line):

| DPI | Pixel aspect | Phone `w` | Maps touch |
|---|---|---|---|
| 175 | derived (11111) | 731 | works |
| 176 | derived | 727 | dead |
| 198 | derived | 646 | dead |
| 175 | square (10001) | 658 | dead |
| 157 | square | 734 | works |

The candidate changes `AnnouncedDensityPolicy`. In portrait, the DPI on the wire is at most `phone width x 160 / 730`. The phone width is the codec width times the pixel aspect ratio when that ratio is above 1. On D-SAM with the derived ratio the cap is 175. With square pixels it is 157. A stored DPI above the cap is sent as the cap, and so is an automatic DPI. The `Density is:` line now prints the dp canvas as the phone computes it.

## 3. What is different this round

- **A candidate build, not beta5.** Build it fresh (section 1).
- **The `Density is:` text changed.** Portrait reads `portrait, within the 730 dp width floor` or `clamped to the 730 dp portrait width floor`. `portrait, not clamped` is gone. The dp canvas now includes the pixel aspect ratio, so in landscape it reads 1034x646 at 198 DPI, not 1034x581.
- **Round 1 and 2 lessons apply:**
  - D-POCO runs Android Auto 18.0. `Will use layout:` is absent. Grade the phone side from `contentArea:`.
  - Grade touch on the screenshots alone. Do not compute pixel counts.
  - The projection gate is two consecutive `Throughput over` lines at 25 fps or more. Round 2's `run_p.sh` does this and may be copied.
  - A session that drops before step 11 is void. Re-run it once. If the second try also drops, mark the run INCONCLUSIVE.
  - The phone's marker line is `I/AutomationMarker( pid): <run>-start`. Window it with `AutomationMarker.*: <run>-start`.
- **D-SAM is API 19**: no `sed` and no `md5sum` on the unit. Use `set_prefs_runas_host.py`.
- **Taps into the projected video are the standing exception** (TESTING-TEMPLATE §3). No other hand step.
- **Phone capture is mandatory in every run.**

## 4. Pre-flight

1. `rig_preflight.sh D_SAM:wifi,bt D_POCO:wifi,bt` must print PREFLIGHT OK. Thermal gate.
2. Clocks: `adb -s 30041c35642d2200 shell date`, `adb -s 4f4027e9 shell date`, and the host's `date`. Record all three.
3. Android Auto version on D-POCO: `adb -s 4f4027e9 shell dumpsys package com.google.android.projection.gearhead | grep -a versionName`. Record it. Rounds 1 and 2 read 18.0.164204-release.daily. If it differs, record it and go on.
4. D-POCO must not be bonded to D-HU's radio (rig-quirks `units/D-POCO.md`). Record the bonded list.
5. D-SAM on a separate supply. D-POCO: Google Maps is the navigation app, `mock_location` 0.
6. Back up D-SAM's settings and diff against round 2's backup. Record the delta and the value of `display-size-preset` (or "absent").

```bash
HU=30041c35642d2200; PKG=com.andrerinas.headunitrevived
mkdir -p portrait-maps-touch-round3
adb -s $HU shell am force-stop $PKG
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > portrait-maps-touch-round3/settings-entry.xml
diff portrait-maps-touch-round2/settings-entry.xml portrait-maps-touch-round3/settings-entry.xml
```

7. Install the candidate (section 1). Check its identity.

## 5. Settings keys

Write with the app stopped, with `set_prefs_runas_host.py`. Read every key back before each launch.

| Key | Element | Where |
|---|---|---|
| `screen-orientation` | `<int name="screen-orientation" value="4" />` (PORTRAIT) | Q1, Q2, Q3 |
| `screen-orientation` | `<int name="screen-orientation" value="2" />` (LANDSCAPE) | Q4 |
| `dpi-pixel-density` | `<int name="dpi-pixel-density" value="198" />` | Q1, Q3, Q4 |
| `dpi-pixel-density` | `<int name="dpi-pixel-density" value="0" />` (Automatic) | Q2 |
| `pixel-aspect-ratio-e4` | `<int name="pixel-aspect-ratio-e4" value="10000" />` (derived) | Q1, Q2, Q4 |
| `pixel-aspect-ratio-e4` | `<int name="pixel-aspect-ratio-e4" value="10001" />` (square) | Q3 |
| `log-level` | `<int name="log-level" value="2" />` (INFO) | every run |

Keep every other key from `settings-entry.xml`. At the end of the round, restore it with round 2's commands (section 5 of that brief), into `portrait-maps-touch-round3/`. The final `diff` must print nothing.

## 6. Deciding lines

Head unit lines, each checked with `git grep -F` on `ec6cb516` (`app/src/main`):

| Line (verbatim fragment) | Level | Grep it in |
|---|---|---|
| `[ServiceDiscovery] Density is: ` | INFO | `hu-<run>.txt` |
| `[ServiceDiscovery] PixelAspectRatioE4 is: ` | INFO | `hu-<run>.txt` |
| `NegotiatedResolution is: ` | INFO | `hu-<run>.txt` |
| `Margins are: ` | INFO | `hu-<run>.txt` |
| `Throughput over` | INFO | `hu-<run>.txt` |
| `AutomationReceiver: ` | INFO | `hu-<run>.txt` |

The reason text inside `Density is:` is built from a constant, so `git grep -F` finds `portrait width floor` and `within the HUIG floor` but not the full phrase with `730`. A unit test pins the full text: `175 (dp canvas 731x1170, requested 198, clamped to the 730 dp portrait width floor)`.

Phone line (Android Auto, not our tree; present in every run of rounds 1 and 2):

| Line (verbatim fragment) | Grep it in |
|---|---|
| `contentArea: ` | `phone-<run>.txt` |

The marker window for every grep is from the run's `-start` marker to its `-end` marker.

## 7. Runs

Shell helpers: as round 2 section 7, with `portrait-maps-touch-round3/` in place of `portrait-maps-touch-round2/`.

| Run | Orientation | DPI stored | Pixel aspect | Expected `Density is:` | Expected phone `w` | Role |
|---|---|---|---|---|---|---|
| Q1 | 4 | 198 | 10000 | `175 (dp canvas 731x1170, requested 198, clamped to the 730 dp portrait width floor)` | 731 | **the point: 198 failed on beta5** |
| Q2 | 4 | 0 | 10000 | announced 175 or lower, `requested automatic, portrait, within the 730 dp width floor` | 730 or more | Automatic stays under the cap |
| Q3 | 4 | 198 | 10001 | `157 (dp canvas 733x1304, requested 198, clamped to the 730 dp portrait width floor)` | 734 | the cap follows the pixel aspect ratio |
| Q4 | 2 | 198 | 10000 | `198 (dp canvas 1034x646, requested 198, within the HUIG floor)` | 1034 | landscape is unchanged (control) |

Per run, with `<run>` = Q1 ... Q4, the steps of round 2 section 7, steps 1 to 12, with this run's keys from section 5. In Q4 use the landscape tap and swipe of round 1: `input tap 640 300`, then `input swipe 500 300 800 300 400`.

Record per run:

- the head unit's `Density is:`, `PixelAspectRatioE4 is:`, `NegotiatedResolution is:` and `Margins are:` lines, quoted with timestamps;
- the phone's `contentArea:` line, quoted (the `Rect(...)` and the `swNNNdp wNNNdp hNNNNdp NNNdpi` part);
- the layout under the map in `s0`: "cards" or "bar".

**Verdicts.** Each run needs all three conditions:

1. **Wire:** the `Density is:` line matches the table. For Q2, the announced value is 175 or lower and the reason reads `portrait, within the 730 dp width floor`.
2. **Phone:** the phone's `w` is 730 or more in Q1 to Q3, and within 2 dp of the table. In Q4, the phone's `w` and `h` are each within 1 dp of the `Density is:` dp canvas.
3. **Touch (visual):** `<run>-s2.png` shows a Re-centre control or map content offset from `s1`.

- **PASS:** all three hold.
- **FAIL:** any condition does not hold. Say which.
- **INCONCLUSIVE:** `s0` does not show the projection, the tap lands on a card or the rail, or the session dropped before step 11 on both tries.

**Stop rule:** run Q1, Q4, Q3, Q2 in that order. If Q1 FAILs on the wire condition, stop: the candidate is not doing what it should.

## 8. Do not re-run

- Rounds 1 and 2 on beta5: the edge at phone `w` 730, and P5 FAIL with P6 PASS. Those numbers stand.
- The wizard check (round 1 thread B): not re-run until the operator gives the path that showed it.

## 9. Report back

1. **Q1:** the `Density is:` line, the phone's `w`, and the touch verdict. PASS means the candidate fixes 198 DPI portrait on D-SAM.
2. **Q3:** whether the cap moved to 157 with square pixels, and the phone's `w`.
3. **Q4:** whether the landscape dp canvas in `Density is:` now equals the phone's `contentArea:` dp.
