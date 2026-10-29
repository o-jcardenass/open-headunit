# portrait-maps-touch round 2 brief

A measurement round on D-SAM, with D-POCO as the phone. It asks one question: in a portrait projection, which width, measured by the phone, makes Google Maps stop taking touch?

Round 1 found the edge between 175 DPI (works) and 190 DPI (fails). It also found that the phone measures the canvas 800 px wide, not 720. This round puts the edge on one dp value and tests the width cause directly, with the pixel aspect ratio as the second variable.

No fix is under test. Publish as `portrait-maps-touch-round2-brief.md`. Results go in `portrait-maps-touch-round2-results.md`.

## 1. Build and baseline

| | |
|---|---|
| Build | `fork/release/3.5.0-beta5` @ `9beef1c9012bc1f7dd1305515a1c6732c602e402`, the same build as round 1 |
| APK | reuse `rig-data/apks/portrait-maps-touch-round1/beta5.apk`, md5 `6757744bf96c3c86425d99e8d240d42d`. Do not rebuild. |
| Baseline | none |
| History | not rewritten |

If the APK is gone, rebuild it as round 1 section 1 says, and record the new md5.

APK identity after the install:

```bash
HU=30041c35642d2200
PKG=com.andrerinas.headunitrevived
adb -s $HU shell md5sum $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r')   # must equal 6757744bf96c3c86425d99e8d240d42d
```

If D-SAM still has this APK from round 1 and the md5 matches, do not reinstall.

## 2. What this is and why

Round 1 results (`portrait-maps-touch-round1-results.md`): Maps takes touch in portrait at 175 DPI and not at 190, 192, 193 or 198. The layout under the map changes at the same edge: weather and media cards at 175, one bottom bar from 190 up. The 600 dp hypothesis is refuted.

The phone's `contentArea:` line reads `Rect(0, 0 - 800, 1280)` in every portrait run, so its width in dp is 800 x 160 / DPI. 175 DPI gives `w731dp`. The likely reason for 800 is the pixel aspect ratio we announce: D-SAM's panel is 800 px wide and the codec 720 px, so the phone widens its canvas by 800/720.

Two Android Auto layout thresholds fall between 175 and 190 DPI:

| Flag (build default) | Value | Fails at |
|---|---|---|
| `SystemUi__narrow_portrait_width_threshold_dp` | 730 | 176 DPI and up |
| `SystemUi__full_maps_and_dashboard_width_threshold_dp` | 710 | 181 DPI and up |

These are build defaults. The phone can carry other values, and its flags cannot be dumped.

**Two predictions this round tests:**

1. **The edge is a phone width.** The ladder P2 to P4 puts it on 730 or 710.
2. **The width comes from the pixel aspect ratio.** P5 keeps 175 DPI and forces square pixels (`pixel-aspect-ratio-e4` = 10001). The phone then measures 720 px, so `w` falls to 658 and Maps must fail at the DPI that worked. P6 lowers the DPI to 157 with square pixels, which brings `w` back to 734, so Maps must work again.

If P5 fails and P6 passes, the fix is a portrait DPI cap computed from the width the phone measures.

## 3. What is different this round

- **No thread B.** The wizard did not reopen in round 1 (B1 and B2 PASS). It is not re-run until the operator gives the exact path that showed it.
- **No uninstall.** Keep the installed app and its settings.
- **The pixel aspect ratio is a run variable.** `pixel-aspect-ratio-e4` is the manual override. 10000 means "unset", so the app derives the value. 10001 forces square pixels. With square pixels the picture looks stretched sideways on D-SAM's 800 px panel. That is expected and does not change the verdict. Every run writes it, and section 5 gives the value.
- **Round 1 lessons that apply here:**
  - D-POCO runs Android Auto 18.0. `Will use layout:` is absent. Grade the phone side from `contentArea:`.
  - The pixel-change count is degenerate (idle is often 0.000). Grade touch on the screenshots alone (section 7). Do not compute the counts.
  - The projection gate is **two consecutive `Throughput over` lines at 25 fps or more**, as in round 1's `run_a.sh`. One line is not enough: the link dropped 5 s after one in round 1.
  - The link drops on some sessions with no relation to the DPI. A run whose session drops before step 11 is void. Re-run it once. If the second try also drops, mark the run INCONCLUSIVE.
- **D-SAM is API 19**: no `sed` on the unit (use `set_prefs_runas_host.py`), and `am` rejects `-p`. See rig-quirks `units/D-SAM.md`.
- **Taps into the projected video are the standing exception** (TESTING-TEMPLATE §3). This round has no other hand step.
- **Phone capture is mandatory in every run** (D-POCO logcat beside D-SAM's).

## 4. Pre-flight

1. Thermal gate (rig-quirks `topics/tooling.md`, first entry).
2. Clocks: `adb -s 30041c35642d2200 shell date`, `adb -s 4f4027e9 shell date`, and the host's `date`. Record all three.
3. Android Auto version on D-POCO: `adb -s 4f4027e9 shell dumpsys package com.google.android.projection.gearhead | grep -a versionName`. Record it. Round 1 read 18.0.164204-release.daily.
4. D-POCO must not be bonded to D-HU's radio (rig-quirks `units/D-POCO.md`). Check D-POCO's bonded list and record it.
5. D-SAM charged from a separate supply.
6. On D-POCO, Google Maps is the Android Auto navigation app, and Fake Speed and any mock location are off.
7. Back up D-SAM's settings and diff them against round 1's `settings-entry.xml`. Record the delta.

```bash
HU=30041c35642d2200; PKG=com.andrerinas.headunitrevived
mkdir -p portrait-maps-touch-round2
adb -s $HU shell am force-stop $PKG
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > portrait-maps-touch-round2/settings-entry.xml
diff portrait-maps-touch-round1/settings-entry.xml portrait-maps-touch-round2/settings-entry.xml
```

8. Record the value of `pixel-aspect-ratio-e4` in `settings-entry.xml`, or "absent".

## 5. Settings keys

Write with the app stopped, with `set_prefs_runas_host.py`. Read every key back before each launch.

| Key | Element | Where |
|---|---|---|
| `screen-orientation` | `<int name="screen-orientation" value="4" />` (PORTRAIT) | every run |
| `dpi-pixel-density` | `<int name="dpi-pixel-density" value="N" />`, N per run | every run |
| `pixel-aspect-ratio-e4` | `<int name="pixel-aspect-ratio-e4" value="10000" />` (derived) | P1 to P4 |
| `pixel-aspect-ratio-e4` | `<int name="pixel-aspect-ratio-e4" value="10001" />` (square) | P5, P6 |
| `log-level` | `<int name="log-level" value="2" />` (INFO) | every run |

Keep every other key from `settings-entry.xml`. At the end of the round, restore it:

```bash
adb -s $HU shell am force-stop $PKG
adb -s $HU push portrait-maps-touch-round2/settings-entry.xml /data/local/tmp/s.xml
adb -s $HU shell run-as $PKG cp /data/local/tmp/s.xml shared_prefs/settings.xml
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' | diff - portrait-maps-touch-round2/settings-entry.xml   # must print nothing
```

## 6. Deciding lines

Head unit lines, each checked with `git grep -F` on `9beef1c9` (`app/src/main`):

| Line (verbatim fragment) | Level | Grep it in |
|---|---|---|
| `[ServiceDiscovery] Density is: ` | INFO | `hu-<run>.txt` |
| `[ServiceDiscovery] PixelAspectRatioE4 is: ` | INFO | `hu-<run>.txt` |
| `NegotiatedResolution is: ` | INFO | `hu-<run>.txt` |
| `Margins are: ` | INFO | `hu-<run>.txt` |
| `Throughput over` | INFO | `hu-<run>.txt` |
| `AutomationReceiver: ` | INFO | `hu-<run>.txt` |

Phone line (Android Auto, not our tree; present in every round 1 run):

| Line (verbatim fragment) | Grep it in |
|---|---|
| `contentArea: ` | `phone-<run>.txt` |

From the `contentArea:` line, record the `Rect(...)` and the `swNNNdp wNNNdp hNNNNdp NNNdpi` part.

The marker window for every grep is from the run's `-start` marker to its `-end` marker.

## 7. Runs

Shell helpers for every run:

```bash
HU=30041c35642d2200; PH=4f4027e9; PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
shot() { adb -s $HU shell screencap -p /sdcard/s.png; sleep 0.3; adb -s $HU pull /sdcard/s.png "portrait-maps-touch-round2/$1.png"; }
win() { awk "/AutomationMarker: $1-start/,/AutomationMarker: $1-end/" "$2"; }
```

| Run | DPI | `pixel-aspect-ratio-e4` | Predicted phone `w` | If the edge is 730 | If the edge is 710 | Role |
|---|---|---|---|---|---|---|
| P1 | 175 | 10000 | 731 | PASS | PASS | control: repeats round 1's PASS |
| P2 | 176 | 10000 | 727 | FAIL | PASS | **the point: splits 730** |
| P3 | 180 | 10000 | 711 | FAIL | PASS | splits 710 |
| P4 | 181 | 10000 | 707 | FAIL | FAIL | |
| P5 | 175 | 10001 | 658 | FAIL | FAIL | **the point: same DPI as P1, square pixels** |
| P6 | 157 | 10001 | 734 | PASS | PASS | positive control for P5 |

Per run, with `<run>` = P1 ... P6:

1. `send ACTION_EXIT`; `sleep 3`; `adb -s $HU shell am force-stop $PKG`.
2. D-POCO airplane mode on: `adb -s $PH shell cmd connectivity airplane-mode enable`. Check with `adb -s $PH shell dumpsys bluetooth_manager | grep -a "enabled:"` (must read false).
3. Write the run's four keys (section 5). Read them back.
4. Captures, both before the launch:
   `adb -s $HU logcat -c; stdbuf -oL adb -s $HU logcat -v time > portrait-maps-touch-round2/hu-<run>.txt &`
   `adb -s $PH logcat -c; stdbuf -oL adb -s $PH logcat -v time > portrait-maps-touch-round2/phone-<run>.txt &`
5. `send ACTION_LOG_MARKER --es text <run>-start`. On D-POCO: `adb -s $PH shell log -t AutomationMarker "<run>-start"`.
6. `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`. `sleep 20`.
7. D-POCO airplane mode off: `adb -s $PH shell cmd connectivity airplane-mode disable`, then `adb -s $PH shell svc wifi enable` and `adb -s $PH shell svc bluetooth enable`. Check both with `dumpsys`.
8. Wait for projection: poll `hu-<run>.txt` every 10 s for up to 120 s, until two consecutive `Throughput over` lines each show 25 fps or more. If that does not happen, the run is void (section 3).
9. `sleep 10`. `shot <run>-s0`.
10. **Open Maps:** `adb -s $HU shell input tap 400 350`. `sleep 3`. `shot <run>-s1`.
11. **Drag the map:** `adb -s $HU shell input swipe 400 300 400 600 400`. `sleep 3`. `shot <run>-s2`.
12. `send ACTION_LOG_MARKER --es text <run>-end`; `adb -s $PH shell log -t AutomationMarker "<run>-end"`. Stop both captures.

Round 1's `rig-data/rounds/portrait-maps-touch-round1/scripts/run_a.sh` may be copied for these steps if it also writes `pixel-aspect-ratio-e4`. Say so in Setup notes if you use it.

Record per run:

- the head unit's `Density is:`, `PixelAspectRatioE4 is:`, `NegotiatedResolution is:` and `Margins are:` lines, quoted with timestamps;
- the phone's `contentArea:` line, quoted;
- the layout under the map in `s0`: "cards" (weather and media cards, as round 1 A1) or "bar" (one bottom bar, as round 1 A6).

**Verdicts. PASS means "Maps took touch".** All visual, from the PNGs. State that in the results.

- **PASS:** `<run>-s2.png` shows the map moved off follow mode: a Re-centre control is on screen, or the map content is offset from `s1`.
- **FAIL:** `s2` matches `s1` in the map area: no Re-centre control and the same content.
- **INCONCLUSIVE:** `s0` does not show the projection, the tap in step 10 lands on a card or the rail and not on the map, or the session dropped before step 11 on both tries. Give the reason.
- **Width check, per run:** the phone's `w` must be within 2 dp of the table's prediction. If it is not, record both numbers. The run still gets its touch verdict.
- **P5 condition:** `PixelAspectRatioE4 is:` must read about 11111 in P1 to P4 (800 x 1280 / (1280 x 720), derived in FILL fit mode) and 10001 in P5 and P6, and P5's `contentArea:` width must be 720, not 800. If P5's line still reads 800, the override did not reach the phone. Mark P5 and P6 INCONCLUSIVE and say so.

**Stop rule:** run P1, P5, P2, P6, P3, P4 in that order. If P1 FAILs, stop the round after P5: the control is broken.

## 8. Do not re-run

- Round 1 thread A: 175 PASS; 190, 192, 193, 198 FAIL; landscape 198 PASS. Those numbers stand. This round needs no landscape run.
- Round 1 thread B: the wizard did not reopen. Not re-run.

## 9. Report back

1. **The edge in the phone's `w`:** the last PASS and the first FAIL of P1 to P4, each with its `contentArea:` width.
2. **P5 and P6:** the touch verdict of each, with its `PixelAspectRatioE4 is:` line and its `contentArea:` line. P5 FAIL with P6 PASS confirms that the phone's width, not the DPI alone, decides.
3. **The layout:** "cards" or "bar" for each run, and whether it changes at the same edge as touch.
