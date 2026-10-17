# portrait-maps-touch round 1 brief

A measurement round on D-SAM, with D-POCO as the phone. It asks two questions:

- **A.** In a portrait projection, at which announced DPI does Google Maps stop taking touch? What does Android Auto log about its layout on each side of that DPI?
- **B.** After a fresh install, does a change of Screen orientation to portrait open the setup wizard again?

No fix is under test. Both threads run on the same build.

Publish as `portrait-maps-touch-round1-brief.md`. Results go in `portrait-maps-touch-round1-results.md`.

## 1. Build and baseline

| | |
|---|---|
| Build | `fork/release/3.5.0-beta5` @ `9beef1c9012bc1f7dd1305515a1c6732c602e402` (`9beef1c9`, "Release: changelog and version for 3.5.0-beta5") |
| Contains | the announced-density commits `205be230`, `e8fd5848`, `70271831` (the `Density is:` log line and the DPI row's dp canvas) |
| Baseline | none |
| History | not rewritten |

```bash
git fetch fork release/3.5.0-beta5
git rev-parse fork/release/3.5.0-beta5     # must print 9beef1c9012b...
```

Build it in a detached worktree (`wt-new.sh`) with `build_hur_cool.sh`, after the thermal gate (rig-quirks `topics/tooling.md`, first entry). Copy the APK out of `apks/` at once into `portrait-maps-touch-round1/beta5.apk`, because `build_hur.sh` deletes the previous APK. Record its md5. No unit tests this round: nothing in the code changes.

APK identity after every install:

```bash
HU=30041c35642d2200
PKG=com.andrerinas.headunitrevived
adb -s $HU shell md5sum $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r')   # must equal beta5.apk's md5
```

## 2. What this is and why

**Thread A.** A user reports that Google Maps on Android Auto takes no touch when the projection runs in portrait (720x1280). The search bar does nothing, the map does not pan, and it stays in follow mode. Everything Android Auto draws itself (the app rail, a media app's lists, the dashboard) takes touch. Landscape works. On D-SAM in portrait the operator then found that 198 DPI fails and 175 DPI works.

On a 720 px wide portrait canvas:

| DPI | dp canvas (w x h) |
|---|---|
| 198 | 581 x 1034 |
| 196 | 587 x 1044 |
| 193 | 596 x 1061 |
| 192 | 600 x 1066 |
| 190 | 606 x 1077 |
| 175 | 658 x 1170 |

The Android Auto 17.8 decompile shows that the configuration it hands to apps sets the smallest width to the canvas **width**, not to the shorter side. So only a portrait canvas can report a small width. None of Android Auto's own layout thresholds falls between 581 and 658 dp. Android's standard 600 dp width is the leading hypothesis, and it sits inside Maps, which is not decompiled. The DPI ladder above crosses 600 dp between 192 and 193. If the change happens there, the hypothesis holds. If it happens elsewhere, the phone's `contentArea:` line gives the real width.

The earlier `dsam-widget-layout-dpi` round 1 (archived) found the same two DPIs splitting the weather widget in **landscape** 1280x720. That is the same dp pair turned 90 degrees. This round also records Maps touch in landscape at 198 as the control.

**Thread B.** The operator changed Screen orientation to portrait after a fresh install and the setup wizard opened again. The wizard opens from `MainActivity.onResume` (`checkSetupFlow`) whenever `onboarding-version` is below 2, and `MainActivity.onResume` then applies the stored orientation, which can recreate the activity. Thread B counts how many times the wizard is started and reads `onboarding-version` at each step, to tell a second wizard start from a lost version.

## 3. What is different this round

- **First round of this thread.** Open a `README.md` row for `portrait-maps-touch`.
- **Thread B uninstalls the app, at the operator's request.** This is the one exception to §5's `install -r` rule. Back up `settings.xml` first (section 5), and restore it before thread A. Bluetooth bonds are system state and survive the uninstall.
- **Run thread B first**, because the restore at its end gives thread A the operator's own configuration.
- **D-SAM is API 19**: no `sed` on the unit (use `set_prefs_runas_host.py`), `am` rejects `-p`, and `SettingsActivity` is not exported (start it with `run-as`). See rig-quirks `units/D-SAM.md` and `units/D-SAM-and-D-HP.md`.
- **D-SAM's natural orientation is portrait**, 800x1280. In a portrait session the screen buffer and the `input` coordinates match, so a screenshot pixel is a tap coordinate.
- **Taps into the projected video are the standing exception** (TESTING-TEMPLATE §3), and thread A uses them to touch Maps. The wizard and the orientation dialog in thread B have no verb, so they are **hand steps** (section 7): `uiautomator dump`, then `input tap` at the dump's bounds. Never tap a screenshot's apparent position for an app control.
- **Thread A's touch verdict is read from screenshots by the grading host.** No log line on either side says that Maps took a touch. The scripted half of each verdict is the phone's layout lines and a pixel-change count (section 7). Per TESTING-TEMPLATE §0 the results say which part is visual.
- **Phone capture is mandatory in thread A** (D-POCO logcat beside D-SAM's).
- **The Android Auto strings come from the 17.8 decompile.** D-POCO may run a later version. If `Will use layout:` is absent, grep `contentArea:` and `displayDimensions`. If all three are absent, record the phone's Android Auto version and mark that condition INCONCLUSIVE, not FAIL.

## 4. Pre-flight

1. Thermal gate, then build (section 1).
2. Clocks: `adb -s 30041c35642d2200 shell date`, `adb -s 4f4027e9 shell date`, and the host's `date`. Record all three.
3. Android Auto version on D-POCO: `adb -s 4f4027e9 shell dumpsys package com.google.android.projection.gearhead | grep -a versionName`. Record it.
4. D-POCO must not be bonded to D-HU's radio (rig-quirks `units/D-POCO.md`: D-SAM cannot wake D-POCO while it is). Check D-POCO's bonded list and record it.
5. D-SAM charged from a separate supply (its USB port does not charge it).
6. On D-POCO, Google Maps is the Android Auto navigation app, and **Fake Speed and any mock location are off**, so the map does not move by itself.
7. Back up D-SAM's settings, then diff it against the last round's backup and record the delta:

```bash
HU=30041c35642d2200; PKG=com.andrerinas.headunitrevived
mkdir -p portrait-maps-touch-round1
adb -s $HU shell am force-stop $PKG
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > portrait-maps-touch-round1/settings-entry.xml
```

## 5. Settings keys

Write with the app stopped, with `set_prefs_runas_host.py`. Read every key back before each launch.

| Key | Element | Where |
|---|---|---|
| `screen-orientation` | `<int name="screen-orientation" value="4" />` (PORTRAIT) | thread A runs A1 to A6 |
| `screen-orientation` | `<int name="screen-orientation" value="2" />` (LANDSCAPE) | thread A run A7 |
| `dpi-pixel-density` | `<int name="dpi-pixel-density" value="N" />`, N per run | thread A |
| `log-level` | `<int name="log-level" value="2" />` (INFO) | thread A; every line graded here prints at INFO |

Thread A keeps every other key from `settings-entry.xml`, including the rig's worst-case audio settings. Do not reset them. Thread B writes no keys: it grades what a fresh install and the wizard write.

## 6. Deciding lines

Head unit lines, each checked with `git grep -F` on `9beef1c9` (`app/src/main`):

| Line (verbatim fragment) | Level | Grep it in |
|---|---|---|
| `[ServiceDiscovery] Density is: ` | INFO | `hu-<run>.txt` |
| `NegotiatedResolution is: ` | INFO | `hu-<run>.txt` |
| `Locking resolution at` | INFO | `hu-<run>.txt` |
| `Margins are: ` | INFO | `hu-<run>.txt` |
| `Sticky Orientation: Session active, forcing orientation to` | INFO | `hu-<run>.txt` |
| `Service Discovery Response` | INFO | `hu-<run>.txt` |
| `Throughput over` | INFO | `hu-<run>.txt` |
| `AutomationReceiver: ` | INFO | `hu-<run>.txt` and `hu-B<n>.txt` |

Phone lines, from the Android Auto 17.8 decompile (`nus.java`), not from our tree:

| Line (verbatim fragment) | Grep it in |
|---|---|
| `Will use layout: ` | `phone-<run>.txt` |
| `contentArea: ` | `phone-<run>.txt` |
| `displayDimensions ` | `phone-<run>.txt` |

Framework lines for thread B (Android 4.4 `ActivityManager`, not in our tree):

| Line (fragment) | Grep it in |
|---|---|
| `START u0` together with `OnboardingActivity` | `hu-B<n>.txt` |
| `Displayed com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.OnboardingActivity` | `hu-B<n>.txt` |

The marker window for every grep is from the run's `-start` marker to its `-end` marker.

## 7. Runs

Shell helpers for every run:

```bash
HU=30041c35642d2200; PH=4f4027e9; PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
shot() { adb -s $HU shell screencap -p /sdcard/s.png; sleep 0.3; adb -s $HU pull /sdcard/s.png "portrait-maps-touch-round1/$1.png"; }
win() { awk "/AutomationMarker: $1-start/,/AutomationMarker: $1-end/" "$2"; }   # marker window of a capture
```

### Thread B (run first)

#### B1: fresh install, wizard in landscape, then orientation to portrait in Settings (the point of thread B)

This is the operator's own path.

1. D-POCO in airplane mode, so no session can start: `adb -s $PH shell cmd connectivity airplane-mode enable`. Check with `adb -s $PH shell dumpsys bluetooth_manager | grep -a "enabled:"` (must read false).
2. Uninstall and install: `adb -s $HU uninstall $PKG`, then `adb -s $HU install portrait-maps-touch-round1/beta5.apk`. Check the md5 (section 1).
3. Start the capture before the launch: `adb -s $HU logcat -c; stdbuf -oL adb -s $HU logcat -v time > portrait-maps-touch-round1/hu-B1.txt &`
4. `send ACTION_LOG_MARKER --es text B1-start`. On a fresh install the package is stopped, and `-f 0x00000020` delivers it anyway. If no `AutomationReceiver:` line follows, record that and go on: the framework lines carry thread B.
5. Launch: `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`. `sleep 8`.
6. **Checkpoint C1.** Record:
   - `grep -a "START u0" portrait-maps-touch-round1/hu-B1.txt | grep -c OnboardingActivity` (expected 1);
   - `adb -s $HU shell dumpsys activity activities | grep -a "Hist #" | grep -c OnboardingActivity` (wizard records in the stack);
   - `adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml | grep -o 'onboarding-version[^/]*'` (expected absent).
   - `shot B1-C1`.
7. **Hand step HS-B1: walk the wizard.** No verb exists for the wizard's controls. Repeat until the wizard closes, at most 15 times:
   1. `adb -s $HU shell uiautomator dump /sdcard/u.xml; adb -s $HU pull /sdcard/u.xml portrait-maps-touch-round1/u.xml`
   2. If the dump has a node whose `resource-id` ends in `:id/onb_safety_accept` and whose `checked="false"`, tap its bounds' centre.
   3. If the dump has `:id/onb_orient_land`, tap its centre (this run picks **Landscape**), then dump again and check `checked="true"` on it.
   4. Tap the centre of `:id/onb_next`. `sleep 2`.
   5. If the dump has no `:id/onb_next`, `shot B1-wizard-stuck` and stop the walk.

   Record each step's title text and the number of Next taps. The last Next reads `Finish`.
8. `sleep 5`. **Checkpoint C2.** Same three counts as C1, plus `grep -o 'screen-orientation[^/]*'` from `settings.xml`. `shot B1-C2`. Expected: 1 start, 0 records, `onboarding-version` 2.
9. **Hand step HS-B2: orientation to portrait in Settings.** No verb sets a setting live, and this run must use the same path as the operator.
   1. `adb -s $HU shell run-as $PKG am start --user 0 -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --es extra_search_query orientation`. `sleep 3`.
   2. Dump. Tap the centre of the node whose text is `Screen orientation`.
   3. Dump. Tap the centre of the node whose text is `Portrait (90°)`. `sleep 4`.
   4. `adb -s $HU shell input keyevent 4` (BACK to the home screen). `sleep 6`.
   5. If step 2 or 3 finds no such node, `shot B1-settings-stuck` and record it.
10. **Checkpoint C3.** Same counts as C2. `shot B1-C3`.
11. `send ACTION_LOG_MARKER --es text B1-end`. Stop the capture.

**B1 PASS:** the `START u0 ... OnboardingActivity` count is 1 at C3, and `onboarding-version` reads 2 at C2 and C3. **FAIL:** the count is 2 or more at any checkpoint, or the wizard is on screen in `B1-C3.png`. For a FAIL, record at which checkpoint the count rose, and whether `onboarding-version` was still 2 at that point. Those two facts separate a second start from a lost version.

#### B2: fresh install, Portrait picked inside the wizard

The same as B1 with three differences:

- the capture is `hu-B2.txt` and the markers are `B2-start` and `B2-end`;
- HS-B1 step 3 taps `:id/onb_orient_port` instead;
- HS-B2 (step 9) is skipped. After step 8, `adb -s $HU shell am force-stop $PKG`, then launch `MainActivity` again, `sleep 8`, then take checkpoint C3.

**B2 PASS / FAIL:** as B1.

#### B-restore (not a run)

```bash
adb -s $HU shell am force-stop $PKG
adb -s $HU push portrait-maps-touch-round1/settings-entry.xml /data/local/tmp/s.xml
adb -s $HU shell run-as $PKG cp /data/local/tmp/s.xml shared_prefs/settings.xml
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml | diff - portrait-maps-touch-round1/settings-entry.xml   # must print nothing
```

If `run-as cp` cannot read `/data/local/tmp`, write every key of `settings-entry.xml` with `set_prefs_runas_host.py` instead, and say so in Setup notes.

### Thread A

Each run is one Native AA session. TESTING-TEMPLATE §4 applies.

| Run | `screen-orientation` | `dpi-pixel-density` | width dp | Role |
|---|---|---|---|---|
| A1 | 4 | 175 | 658 | known good (operator) |
| A2 | 4 | 190 | 606 | |
| A3 | 4 | 192 | 600 | **the point: just at 600 dp** |
| A4 | 4 | 193 | 596 | **the point: just below 600 dp** |
| A5 | 4 | 196 | 587 | |
| A6 | 4 | 198 | 581 | known bad (operator) |
| A7 | 2 | 198 | 1034 (height 582) | positive control: landscape must take touch |

Per run, with `<run>` = A1 ... A7:

1. `send ACTION_EXIT`; `sleep 3`; `adb -s $HU shell am force-stop $PKG`.
2. D-POCO airplane mode on (B1 step 1, with the same check).
3. Write the run's two keys plus `log-level` (section 5). Read them back.
4. Captures, both before the launch:
   `adb -s $HU logcat -c; stdbuf -oL adb -s $HU logcat -v time > portrait-maps-touch-round1/hu-<run>.txt &`
   `adb -s $PH logcat -c; stdbuf -oL adb -s $PH logcat -v time > portrait-maps-touch-round1/phone-<run>.txt &`
5. `send ACTION_LOG_MARKER --es text <run>-start`. On D-POCO, stamp the same marker: `adb -s $PH shell log -t AutomationMarker "<run>-start"`.
6. `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`. `sleep 20`.
7. D-POCO airplane mode off, then `adb -s $PH shell svc wifi enable` and `adb -s $PH shell svc bluetooth enable`. Check both with `dumpsys`.
8. Wait for projection: poll `grep -c "Throughput over" portrait-maps-touch-round1/hu-<run>.txt` every 10 s for up to 120 s. If it stays 0, the run is void. Re-run it once. If the second try also stays 0, mark the run INCONCLUSIVE.
9. `sleep 10`, so that the map settles. `shot <run>-s0`. `sleep 3`. `shot <run>-s0b`. This is the no-touch control.
10. **Open Maps.** Tap the middle of the map area once: `adb -s $HU shell input tap 400 350` in portrait, or `adb -s $HU shell input tap 640 300` in A7 (the coordinates are logical; A7's activity is landscape). `sleep 3`. `shot <run>-s1`.
11. **Drag the map:** portrait `adb -s $HU shell input swipe 400 300 400 600 400`, A7 `adb -s $HU shell input swipe 500 300 800 300 400`. `sleep 3`. `shot <run>-s2`.
12. `send ACTION_LOG_MARKER --es text <run>-end`; `adb -s $PH shell log -t AutomationMarker "<run>-end"`. Stop both captures.
13. Pixel-change counts, on the host:

```bash
python3 - portrait-maps-touch-round1/<run>-s0.png portrait-maps-touch-round1/<run>-s0b.png portrait-maps-touch-round1/<run>-s1.png portrait-maps-touch-round1/<run>-s2.png <<'EOF'
import sys
from PIL import Image, ImageChops
a, b, c, d = (Image.open(p).convert("RGB") for p in sys.argv[1:])
w, h = a.size
box = (0, int(h * 0.10), w, int(h * 0.55))   # the map band, below the status area
def diff(x, y):
    px = ImageChops.difference(x.crop(box), y.crop(box)).convert("L").point(lambda v: 255 if v > 24 else 0)
    return sum(1 for v in px.getdata() if v) / ((box[2] - box[0]) * (box[3] - box[1]))
print(f"idle={diff(a, b):.3f} drag={diff(c, d):.3f}")
EOF
```

If PIL is missing, say so and leave the counts out. The grading host then reads the screenshots alone.

Record per run:

- the head unit's `Density is:`, `NegotiatedResolution is:`, `Margins are:` and `Sticky Orientation` lines, quoted with timestamps;
- the phone's `Will use layout:` and `contentArea:` lines inside the marker window, quoted;
- `idle=` and `drag=`.

**Verdicts. Thread A grades Maps, not the app**, so PASS means "Maps took touch":

- **PASS:** `<run>-s2.png` shows the map moved off follow mode: a re-centre control is on screen, or the map content is offset from `s1`. And `drag=` is at least 3 times `idle=`.
- **FAIL:** `s2` matches `s1` in the map area (no re-centre control, same content), and `drag=` is under 3 times `idle=`.
- **Visual part:** the re-centre reading is the grading host's, from the PNGs. State it as visual in the results.
- **Check that the touch reached Maps:** `s0` must show the projection, and the tap point in step 10 must land on the map, not on a card or the rail. If it does not, the run is INCONCLUSIVE, not FAIL. Give the screen region that was tapped.

**Stop rule for thread A:** run A1, A7, A6, A3, A4 in that order, then A2 and A5. If A1 or A7 FAILs, stop thread A after A6: the controls are broken and the ladder means nothing. If both A3 and A4 PASS, or both FAIL, still run A2 and A5.

## 8. Do not re-run

- `dsam-widget-layout-dpi` round 1 (archived): the landscape weather widget appears at 175 DPI and not at 198. Its numbers stand. This round reads Maps touch only, and a landscape run at 175 is not needed.

## 9. Report back

1. **The DPI where Maps stops taking touch in portrait**: the last PASS and the first FAIL on the A1 to A6 ladder, with each one's width dp from `Density is:` and from the phone's `contentArea:`.
2. **Whether the phone's layout changes across that edge**: `Will use layout:` on each side, verbatim.
3. **Thread B**: the `OnboardingActivity` start count and `onboarding-version` at each checkpoint of B1 and B2.
