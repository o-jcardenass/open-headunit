# pr-merge-train, round 3 brief: settings screen, logs and projection touch (stage C)

Published name: `pr-merge-train-round3-brief.md`. Results go in `pr-merge-train-round3-results.md`.
Evidence: add `pr-merge-train-round3-captures.zip` to the release `rig-evidence-pr-merge-train`.

Units: D-HU (head unit), D-POCO (phone), D-SAM (settings screen only, no session). D-MOTO and D-HP take no part.

Queue line: `pr-merge-train-round3-brief.md` (T `927edb5f` on `fork/test/pr-merge-train`, one build, no baseline;
units D-HU, D-POCO and D-SAM; S1 and P1 are the point).

Pairing: any round that does not use D-HU, D-POCO or D-SAM (§3b). Hand steps: none. Injected taps: one in P1, into
the projected video (the standing exception, §3).

**Time budget.** Build 25 min, runs 15 min, thermal waits about 4 min. **About 45 min.** Build T in the same batch as
rounds 1 and 2.

| Run | Unit | What it proves | Minutes |
|---|---|---|---|
| **S1** | D-HU, then D-SAM | **the redesigned settings screen opens and draws on Android 14 and on Android 4.4** | 5 |
| **P1** | D-HU | **a session projects, a tap in the video still reaches the phone, and an export is named `OHU_Log_`** | 8 |

## 1. Build and baseline

Stage C is round 2's stage B plus four PRs, merged in this order: the Material 3 settings screen, the log file
rename, the ILL+ calibration and the night overlay with gestures. One conflict was resolved in
`app/src/main/res/values/colors.xml`: `main`'s onboarding colors are kept beside the PR's `brand_primary`.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| T (candidate) | `fork/test/pr-merge-train` | `927edb5f191f37e3b976ba23ace1e1750ac2cac8` | `e395dca82ba0dcb7abeddd18029c187903f302ec` |

The dev host ran the JVM suite at T: 4280 tests, 0 failures, 0 skipped. Do not run the unit tests on the rig. Build T
as round 1's `get_apk.sh` does, into `rig-data/rounds/pr-merge-train-round3/apk-T.apk`. Install it on D-HU and D-SAM.

Identity: `send ACTION_QUERY_STATE` on D-HU carries `927edb5f`, and
`unzip -p apk-T.apk 'classes*.dex' | strings | grep -acE 'SettingsLayoutPolicy|NightProjectionOverlay'` is 2 or more.

## 2. What this is and why

- **The settings redesign** replaces the whole settings screen (142 files). Its author tested it on emulators only,
  from Android 4.1 to 16. S1 opens it on real hardware at both ends of that range.
- **The gestures PR** rewrites how the projection handles two-finger touches. A single tap goes through the same
  code first, so P1 sends one tap into the video and checks that an input event goes to the phone. The night overlay
  and every new gesture are off by default, so P1 also checks nothing new appears on its own.
- **The log rename** names new captures `OHU_Log_<timestamp>.txt`. P1 exports one.
- **The ILL+ calibration** acts only on a car signal the rig does not have. P1 checks it stays silent.

## 3. What is different about this round

- **P1 runs at `log-level` 0 (VERBOSE)**, because the input event line is `AppLog.v`. D-HU's ring buffer wraps
  fast at VERBOSE; the streamed capture keeps it. P1 is short for that reason.
- **D-SAM: `SettingsActivity` is not exported.** Open it with `run-as` (`rig-quirks/units/D-SAM.md`), and drop `-p`
  from `am` on that unit. Write D-SAM's settings with `set_prefs_runas_host.py`.
- Read `rig-quirks/units/D-SAM.md` and `rig-quirks/units/D-SAM-and-D-HP.md` beside round 1's list.

## 4. Settings keys

D-HU: round 1's R0 keys, with `log-level` = 0 for P1. D-SAM: `onboarding-version` (int) = 2 only; record its
current `log-level`. Back up and diff both units' `settings.xml`.

## 5. Helpers

**lib3.sh**

```bash
OUT=rig-data/rounds/pr-merge-train-round3; . ./lib.sh      # lib.sh from round 1
SAM=30041c35642d2200
shot() { adb -s $1 shell screencap -p /sdcard/s.png; sleep 0.3; adb -s $1 pull /sdcard/s.png $OUT/$2.png >/dev/null; }
focus() { adb -s $1 shell dumpsys activity activities | grep -a -m1 mResumedActivity | tee -a $OUT/$2-focus.txt; }
```

Start the D-HU and D-POCO captures as round 1's `capture.sh` does. For S1 on D-SAM also run
`adb -s $SAM logcat -c; stdbuf -oL adb -s $SAM logcat -v time > $OUT/sam.txt &`.

## 6. The deciding lines

Verified with `grep -F -r` in `app/src/main` at `927edb5f`. Grep with `-a`.

| Id | Line | Read |
|---|---|---|
| L-SSL | `SSL handshake complete` | a session formed |
| L-TP | `Throughput over` | `rendered=N` |
| L-IN | `INPUT Input Event` | VERBOSE: one touch message sent to the phone |
| L-NIGHT | `ACTION_SET_NIGHT_MODE` after `AutomationReceiver: ` | the verb landed |

FAIL lines in any window, on every unit: `FATAL EXCEPTION` in a block naming `com.andrerinas`, and
`ANR in com.andrerinas`. Phone, between P1's RIGMARK lines: `Critical error` MUST be 0.

## 7. Runs

**S1 (D-HU, then D-SAM).** On each unit, with the app stopped and its keys written:

**s1.sh**

```bash
. ./lib3.sh
D=$HU; mark S1-start
adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0
sleep 4; focus $HU S1-hu; shot $HU S1-hu-portrait-or-landscape
adb -s $HU shell input keyevent 4; sleep 2; mark S1-end; adb -s $HU shell am force-stop $PKG
adb -s $SAM shell run-as $PKG am start --user 0 -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0
sleep 6; focus $SAM S1-sam; shot $SAM S1-sam
adb -s $SAM shell input keyevent 4; sleep 2; adb -s $SAM shell am force-stop $PKG
```

S1 PASS on each unit: the focus line names `SettingsActivity`, the screenshot shows the settings screen (a header row
and a list, not a blank or black screen), and 0 FAIL lines in that unit's capture from the launch to the force-stop.
On D-SAM the screenshot is the evidence the screen draws on API 19; attach both images.

**P1 (D-HU).** Write `log-level` 0, then:

**p1.sh**

```bash
. ./lib3.sh
th_gate P1 || exit 1; ph_off; adb -s $D shell am force-stop $PKG
launch; sleep 2; mark P1-start; ph_on
waitn P1-start "SSL handshake complete" 1 120 || { echo "P1 NO_SESSION" >> $OUT/marks.txt; }
waitn P1-start "Throughput over" 1 30; sleep 10
WH=$(adb -s $D shell wm size | grep -a -o '[0-9]*x[0-9]*' | tail -1); W=${WH%x*}; H=${WH#*x}
shot $HU P1-before; mark P1-tap; adb -s $D shell input tap $((W/2)) $((H/2)); sleep 3; mark P1-tapped; shot $HU P1-after
send ACTION_SET_NIGHT_MODE --es state night; sleep 5; shot $HU P1-night
send ACTION_SET_NIGHT_MODE --es state day; sleep 5
send ACTION_EXPORT_LOG; sleep 8
adb -s $D shell su -c "ls -t /sdcard/Android/data/$PKG/files/Download/" | head -3 | tee $OUT/P1-exports.txt
mark P1-end; send ACTION_DISCONNECT; sleep 5; th_end; appexit
```

P1 PASS: L-SSL ≥ 1 and the last L-TP has `rendered=` > 0; between `P1-tap` and `P1-tapped`, L-IN ≥ 1; L-NIGHT = 2;
the newest file in `P1-exports.txt` starts `OHU_Log_`, and no file newer than the round's start starts `HUR_Log_`;
the three screenshots show the projection with no overlay (no clock or maneuver card drawn by the app over the
video); 0 FAIL lines; phone `Critical error` = 0. If the export folder is empty, the path differs on this build: run
`adb -s $D shell su -c "find /sdcard /data/data/$PKG -name 'OHU_Log_*'"` and grade on the newest hit.

Then restore both units' `settings.xml` and stop the captures.

## 8. Do not re-run

Rounds 1 and 2. A two-finger gesture cannot be injected from adb; the JVM tests cover the gesture tracker.

## 9. Report back

1. S1: the focus line and the screenshot on each unit.
2. P1: L-IN in the tap window, the newest export's name, and whether any screenshot shows an overlay.
