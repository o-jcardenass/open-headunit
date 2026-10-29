# pr-train-20261029, round 1 brief: nine small and medium upstream PRs on one build

Published name: `pr-train-20261029-round1-brief.md`. Results go in `pr-train-20261029-round1-results.md`.
Evidence: create the release `rig-evidence-pr-train-20261029` and add `pr-train-20261029-round1-captures.zip`.

Units: D-HU (head unit, Native wireless) with D-POCO (phone); D-POCO (head unit, API 35, Native wireless) with
D-MOTO (phone); D-SAM (head unit, API 19, Native wireless) with D-POCO.

Queue line: `pr-train-20261029-round1-brief.md` (T `e2b1264c` on `fork/test/pr-train-20261029`, B `a4f80ff1` =
`main`; two builds in one worktree; units D-HU, D-POCO, D-MOTO, D-SAM;
X1 and W1 are the point).

Pairing: none, because D-POCO plays both roles and the round uses D-HU, D-SAM and D-MOTO too.
Hand steps: one D-POCO unpair only if stage W needs it. Injected taps: up to 10 per run (the operator raised the
cap for this round), each on a `uiautomator dump` target by resource id. Only P1 is expected to use any.

**Time budget.** Two builds 15 min, pre-flight 10 min, 8 runs of about 6 min, installs 10 min, thermal waits about
8 min. **About 1 h 30 min.**

| Run | Unit | Arm | PRs it covers | What it proves | Min |
|---|---|---|---|---|---|
| XB | D-HU + D-POCO | B | control | a second Exit during the teardown wait is safe on `main` | 6 |
| **X1** | D-HU + D-POCO | T | #1212, #1182, #1191, #1224 | **the point: the same double Exit, and a default session no worse** | 6 |
| PB | D-POCO as HU + D-MOTO | B, `fullscreen-mode=0` | control | the anchor with bars on API 35 | 6 |
| P1 | D-POCO as HU + D-MOTO | T, `fullscreen-mode=0` | #1211 | the anchor now leaves the bars out | 6 |
| P2 | D-POCO as HU + D-MOTO | T, `fullscreen-mode=1` | #1211 | immersive is unchanged | 6 |
| WB | D-SAM + D-POCO | B | control | a pending rotation is skipped on `main` (positive control) | 8 |
| **W1** | D-SAM + D-POCO | T | #1209 | **the point: a pending rotation purges and re-creates below API 29** | 8 |
| W2 | D-SAM + D-POCO | T | #1209 | an ordinary relaunch still reads the group it left | 6 |

PRs with no rig run, and why: #1210 and #1208 fix API 16 to 18 calls that no rig unit can reach the right way
(D-HP is API 17 but runs no Self-ADB and no GPS resend); #1167 changes only the external module route, and no rig unit
has the 3152 daemon. The dev host checked these three by code review.

## 1. Build and baseline

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| T (candidate) | `fork/test/pr-train-20261029` | `e2b1264c5265ac32ad2667f889838a24664c467c` | `13e22f6e1ddf16234344ab318c6ba88a23b96077` |
| B (control) | `main` | `a4f80ff1b6aa6726c17e7385192cf23abe0d0e9f` | `f36d4598ff4bcd75182661f91f87841b0f08483f` |

T is `main` plus nine upstream PRs, each as one merge commit, in this order: #1209 `5a010a39`, #1211 `129e49fd`,
#1212 `9225d416`, #1167 `14cdf10e`, #1182 `6a377580`, #1191 `acda7ac6`, #1224 `f4faa828`, #1210 `d77e1fcc`,
#1208 `aca4a096`. All nine merge with no conflict. Build T with `wt-new.sh`, the thermal gate and `MAX_WORKERS=2`.
Build B in the same worktree with `git checkout`. The dev host ran the JVM suite at T: 4399 tests, 1 failure (`WppTcpTlsTest`, which passes alone and on B, so it depends on test order). No rig unit tests.

Identity after each install: `send ACTION_QUERY_STATE` and match `commit`; and
`unzip -p <apk> 'classes*.dex' | strings | grep -acF NightOverlayState` is 1 or more for T and 0 for B.

## 2. What this is and why

Nine upstream PRs wait for review. They are small enough to share one build. The dev-host review found:

- **#1212 (microphone foreground type):** `onStartCommand` now calls `startForegroundWithCurrentTypes()`, which
  returns without calling `startForeground` once `isDestroying` is set. A Stop sets it and then waits up to 5 s
  for the wireless teardown. A second `startForegroundService` in that window (a second Exit, an auto-start, the
  `onTaskRemoved` restart) never promotes the service, and Android may then kill the app when `stopSelf()` runs
  ("did not then call Service.startForeground"). X1 sends the second Exit on purpose. XB is the control.
- **#1182 (night overlay, two-finger gestures):** a gesture sends `ACTION_CANCEL` to the phone and then swallows
  the `ACTION_UP`. A rig cannot inject a two-finger swipe, so this is not graded here; the review comment carries
  it. X1 checks only that a default session with no gesture is no worse.
- **#1211 (system insets, API 35+):** the canvas and anchor now leave the system bars out in Normal screen mode
  (`fullscreen-mode=0`). D-POCO is the only API 35 unit.
- **#1209 (WiFi Direct below API 29):** a pending identity rotation now removes a surviving group and purges its
  saved profile before it re-creates it. On `main` the bring-up reads the surviving group instead, which skips the
  rotation. WB is a positive control: it should show the skip.
- **#1191 (ILL+ calibration)** and **#1224 (Fake Speed removal)** ride along in X1.

## 3. What is different about this round

- **X1 can end in a crash on purpose.** That is the finding under test, not a rig fault. Capture the crash block.
- **D-POCO as head unit carries no video** (`rig-facts`). P runs grade geometry lines from the session start only.
- **W runs need a group that survives a force-stop.** D-SAM deletes an empty P2P group (`dsam-platform-removes-
  empty-p2p-group`), so the relaunch comes within 3 s of the force-stop, while D-POCO is still a client. If the
  group is gone anyway, the run is INCONCLUSIVE (reachability below), not FAIL.
- **D-POCO may be bound to D-HU.** Stage W turns D-HU's Bluetooth off first.
- **Taps:** the cap is 10 per run this round. P1 and P2 may tap D-POCO's "Welcome to Android Auto" `Continue` on
  D-MOTO if it shows; resolve it by text from a dump and log each tap.
- Read `rig-quirks/topics/tooling.md`, `topics/lifecycle.md`, `topics/wifi.md`, `topics/projection.md`,
  `units/D-HU.md`, `units/D-POCO.md`, `units/D-MOTO.md`, `units/D-SAM.md`, `units/D-SAM-and-D-HP.md`.

## 4. Pre-flight (one batched ask to the operator, before XB)

1. D-SAM 50% or more on a separate charger; D-POCO and D-MOTO 50% or more.
2. D-MOTO unlocked with no PIN prompt; `adb -s ZY22GC3BM4 shell svc power stayon true`.
3. A Spotify playlist on D-POCO and on D-MOTO.
4. Record `dumpsys bluetooth_manager | grep -a -A8 'Bonded'` on D-POCO and D-MOTO. D-MOTO must list D-POCO.
5. `stat -c '%U:%G'` of D-HU's `shared_prefs`; report it in Setup notes.

## 5. Settings keys

Write with the app stopped, read back after every install. Back up each unit's `settings.xml` first.

| Key | Type | All | D-HU | D-POCO as HU | D-SAM |
|---|---|---|---|---|---|
| `log-level` | int | 1 (DEBUG) | | | |
| `onboarding-version` | int | 2 | | | |
| `allow-external-configuration` | boolean | true | | | |
| `enable-audio-sink` | boolean | true | | | |
| `use-aac-audio` | boolean | false | | | |
| `narrow-band-profile-cap` | boolean | false | | | |
| `kill-on-disconnect` | boolean | false | | | |
| `enable-floating-button` | boolean | false | | | |
| `video-profile-starvation-cap` | | delete | | | |
| `connection-modes` | set | `wifi` | | | |
| `wifi-connection-mode` | int | 3 | | | |
| `native-ap-transport` | int | 0 | | | |
| `native-poke-all-paired` | boolean | true | | | |
| `external-bt-zbt-transport` | boolean | false | | | |
| `night-mode` | int | | 6 (CAR_SIGNAL) | | |
| `car-signal-invert` | boolean | | true (T only; B ignores it) | | |
| `fake_speed` | boolean | | true (an old key #1224 no longer reads) | | |
| `fullscreen-mode` | int | | | 0 in PB and P1, 1 in P2 | |
| `wifi-direct-stable-identity` | boolean | | | | true |
| `wifi-direct-rotation-pending` | boolean | | | | per run, below |

Reset the poke and driver keys for the phone in use on each head unit (`native-poke-bt-macs`, `native-mac`,
`last-connected-native-mac`, `native-preferred-device-mac`).

## 6. Helpers

**`lib-train.sh`**
```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
OUT=rig-data/rounds/pr-train-20261029-round1; mkdir -p $OUT
export LC_ALL=C
DHU=27870808938846; SAM=30041c35642d2200; POCO=4f4027e9; MOTO=ZY22GC3BM4
A() { flock "/tmp/rig-adb-${HU//[:.]/_}.lock" adb -s "$HU" "$@"; }
send() { a=$1; shift; A shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; adb -s "$PH" shell log -t RIGMARK "$1"
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
cap() { A logcat -c; stdbuf -oL adb -s "$HU" logcat -v time >> $OUT/$1-hu.txt & echo $! > $OUT/$1-hu.pid
  adb -s "$PH" logcat -c; stdbuf -oL adb -s "$PH" logcat -v time >> $OUT/$1-phone.txt & echo $! > $OUT/$1-phone.pid; }
alive() { for s in hu phone; do kill -0 "$(cat $OUT/$1-$s.pid)" 2>/dev/null && continue
  d=$HU; [ $s = phone ] && d=$PH; echo "$(date +%T) $1-$s capture restarted" >> $OUT/marks.txt
  stdbuf -oL adb -s "$d" logcat -v time >> $OUT/$1-$s.txt & echo $! > $OUT/$1-$s.pid; done; }
uncap() { kill $(cat $OUT/$1-hu.pid) $(cat $OUT/$1-phone.pid) 2>/dev/null; }
after() { sed -n "/AutomationMarker: $1/,\$p" $OUT/$2-hu.txt | grep -acF -- "$3"; }
waitn() { local m=$1 r=$2 s=$3 cap=$4 t=0
  while [ $t -lt $cap ]; do [ "$(after $m $r "$s")" -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }
launch() { A shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; }
appexit() { send ACTION_DISCONNECT; sleep 5; A shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null
  sleep 5; A shell am force-stop $PKG; sleep 1; }
hold() { local end=$((SECONDS+$2)); while [ $SECONDS -lt $end ]; do sleep 30; alive $1; done; }
```

(D-HU with D-POCO):

**`stage-x.sh`**
```bash
. ./lib-train.sh; HU=$DHU; PH=$POCO
play() { adb -s $PH shell input keyevent 126; sleep 8; }
ph_radios() { adb -s $PH shell svc wifi $1; sleep 0.3; adb -s $PH shell svc bluetooth $1; sleep 4; }
run_x() { local R=$1
  th_gate $R || return 1; A shell settings put system car_mode 0; ph_radios disable; A shell am force-stop $PKG; cap $R
  launch; sleep 2; mark $R-start; ph_radios enable
  waitn $R-start $R "SSL handshake complete" 150 ||
    { echo "$R NO_SESSION" >> $OUT/marks.txt; mark $R-end; th_end; appexit; uncap $R; return 1; }
  waitn $R-start $R "Throughput over" 30; play; hold $R 60
  mark $R-carmode; A shell settings put system car_mode 1; sleep 10
  A shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0 >/dev/null
  sleep 5; A shell screencap -p /sdcard/$R-settings.png; adb -s $HU pull /sdcard/$R-settings.png $OUT/ >/dev/null
  A shell input keyevent 4; sleep 2; send ACTION_RAISE_PROJECTION; sleep 5
  mark $R-exit; send ACTION_EXIT; sleep 0.5; send ACTION_EXIT; sleep 20; mark $R-end
  A shell settings put system car_mode 0; th_end; A shell am force-stop $PKG; uncap $R; }
# run_x XB; install T; run_x X1
```

`settings put system car_mode` runs as root on D-HU. Record whether it returns an error; if it does, the car_mode
part of X1 is UNTESTABLE and the rest of X1 still grades.

(D-POCO as head unit, D-MOTO as phone; install B on D-POCO first):

**`stage-p.sh`**
```bash
. ./lib-train.sh; HU=$POCO; PH=$MOTO
ph_radios() { adb -s $PH shell svc wifi $1; sleep 0.3; adb -s $PH shell svc bluetooth $1; sleep 4; }
run_p() { local R=$1
  th_gate $R || return 1; A shell input keyevent KEYCODE_HOME; ph_radios disable; A shell am force-stop $PKG; cap $R
  launch; sleep 2; mark $R-start; ph_radios enable
  waitn $R-start $R "SSL handshake complete" 150 ||
    { echo "$R NO_SESSION" >> $OUT/marks.txt; mark $R-end; th_end; appexit; uncap $R; return 1; }
  sleep 20; A shell screencap -p /sdcard/$R.png; adb -s $HU pull /sdcard/$R.png $OUT/ >/dev/null
  A shell dumpsys window | grep -a -E 'mAppBounds|mCurrentFocus' > $OUT/$R-window.txt
  hold $R 60; mark $R-end; th_end; appexit; uncap $R; }
# Write fullscreen-mode=0: run_p PB; install T: run_p P1; write fullscreen-mode=1: run_p P2
```

(D-SAM with D-POCO; first `adb -s $DHU shell svc bluetooth disable`):

**`stage-w.sh`**
```bash
. ./lib-train.sh; HU=$SAM; PH=$POCO
ph_radios() { adb -s $PH shell svc wifi $1; sleep 0.3; adb -s $PH shell svc bluetooth $1; sleep 4; }
run_w() { local R=$1 rot=$2
  th_gate $R || return 1; ph_radios disable; A shell am force-stop $PKG; cap $R
  launch; sleep 2; mark $R-start; ph_radios enable
  waitn $R-start $R "SSL handshake complete" 150 ||
    { echo "$R NO_SESSION" >> $OUT/marks.txt; mark $R-end; th_end; appexit; uncap $R; return 1; }
  hold $R 30; mark $R-kill; KILLT=$(date +%s); A shell am force-stop $PKG
  write_rot $rot                  # wifi-direct-rotation-pending=$rot with $RIG_TOOLKIT/set_prefs_runas_host.py
  launch; mark $R-relaunch; echo "$R kill-to-relaunch $(( $(date +%s) - KILLT ))s" >> $OUT/marks.txt
  waitn $R-relaunch $R "SSL handshake complete" 150 || echo "$R NO_SECOND_SESSION" >> $OUT/marks.txt
  hold $R 30; mark $R-end; th_end; appexit; uncap $R; }
# run_w WB true; install T; run_w W1 true; run_w W2 false; then adb -s $DHU shell svc bluetooth enable
```

Define `write_rot` once from `TOOLS.md`'s call form for `set_prefs_runas_host.py` (one key, boolean, app stopped).
Aim for a kill-to-relaunch gap of 5 s or less, and report the gap from `marks.txt`. A longer gap that leaves no
surviving group shows as L-ADOPT 0 on WB, which the reachability rule below already covers.

## 7. The deciding lines and the runs

All app strings verified with `git grep -F` in `app/src/main` at `e2b1264c` and at `a4f80ff1`. Grep with `-a` in
`<run>-hu.txt` between the named markers.

| Id | Line | Window | Read |
|---|---|---|---|
| L-SSL | `SSL handshake complete` | start to end | a session formed |
| L-TP | `Throughput over ` | start to `<run>-carmode` | median `rendered=` |
| L-CAR | `NightModeManager: CAR_SIGNAL car_mode=` | whole | X runs: `car_mode=`, `invert=` (T only), `isNight=` |
| L-STOP | `Stop action received.` | exit to end | count; 2 expected |
| L-TDW | `the wireless teardown did not finish in` | exit to end | count |
| L-DES | `AapService destroying` | exit to end | count |
| L-FGS | `caught in onStartCommand` | whole | count |
| S-FGS | `did not then call Service.startForeground` | exit to end | system line, count |
| L-ANC | `HeadUnitScreenConfig: anchor ` | whole | P runs: the `WxH -> WxH` pair and `measured on` |
| L-INS | `, against the insets now in force` | whole | P runs: record the line |
| L-SMM | `Surface mismatch detected!` | whole | count |
| L-ADOPT | `is already up from ` | relaunch to end | W runs: the surviving group was read |
| L-PURGE | `WifiDirectManager: persistent profile purge: ` | relaunch to end | W runs: the verdict text |
| L-ID | `WifiDirectManager: group identity ssid=` | whole | W runs: the SSID before and after the kill |

FAIL lines, whole capture: `FATAL EXCEPTION` in a block naming `com.andrerinas` (except where X1 below says
otherwise), `UnsatisfiedLinkError`. Phone, between RIGMARK lines: `Critical error` 0 and Gearhead `FATAL EXCEPTION`
0, excluding 2 s after each Exit.

**Runs, in the order of the table.** Write the results file after every run.

- **Session gate, every run:** L-SSL 1 within 150 s. Retry once, then UNTESTABLE.
- **XB (control):** report L-STOP, L-TDW, L-DES, S-FGS and any FATAL EXCEPTION. Expected: no crash.
- **X1, the double Exit (#1212).** FAIL when S-FGS is 1 or more, or a `FATAL EXCEPTION` naming `com.andrerinas`
  appears between `X1-exit` and `X1-end`, and XB shows neither. PASS when neither appears and L-DES is 1.
  Reachability: L-STOP must be 2 and the first Exit must have found a session (L-SSL before `X1-exit`); if L-STOP is
  1, the second Exit never landed and the run is INCONCLUSIVE.
- **X1, the default session (#1182, #1212, #1211):** PASS when median `rendered=` is at least 0.8 x XB's and 0 FAIL
  lines before `X1-exit`.
- **X1, ILL+ (#1191):** PASS when, after `X1-carmode`, L-CAR reads `car_mode=1 invert=true isNight=false`, and
  before it `car_mode=0 invert=true isNight=true`. On XB the same lines read `isNight=false` then `true`: that is the
  control. If no L-CAR line prints on either arm, INCONCLUSIVE.
- **X1, settings screen (#1224):** PASS when the screenshot shows the settings screen and no FATAL EXCEPTION
  follows the `SettingsActivity` start. The `fake_speed` key in `settings.xml` must not break the load.
- **PB, P1, P2 (#1211).** Report L-ANC, L-INS, L-SMM and the `mAppBounds` line for each. P1 PASS when its anchor is
  smaller than PB's by the status and navigation bars (or equal to `mAppBounds`) and L-SMM is 0. P2 PASS when its
  anchor equals the full display and L-SMM is 0. If PB's anchor already equals `mAppBounds`, the fix has nothing to
  change on this unit: P1 is INCONCLUSIVE, and P2 still grades.
- **WB (positive control, #1209).** Expected: L-ADOPT 1 after `WB-relaunch` and the same SSID in L-ID before and
  after. If L-ADOPT is 0 on WB, the group did not survive the kill: W1 is INCONCLUSIVE (reachability).
- **W1 (#1209), the point.** PASS when, after `W1-relaunch`: L-ADOPT 0; L-PURGE 1; L-ID shows a new SSID; and a
  second session forms (no `NO_SECOND_SESSION`). FAIL when L-ADOPT is 1 (the rotation was skipped, as on `main`), or
  no second session forms while WB's did.
- **W2 (#1209), no change at default.** PASS when L-ADOPT is 1, L-PURGE is 0 and a second session forms.

Stop rule: host hot after the second thermal wait: stop and mark the rest UNTESTABLE.

**Closing.** Restore all three head units' `settings.xml` and read them back. `settings put system car_mode 0` on
D-HU. D-HU Bluetooth on. D-MOTO `svc power stayon false`. Leave B installed on every head unit.

## 8. Do not re-run

Nothing from earlier threads. Two-finger gestures and the night overlay are out of scope (no injection route).

## 9. Report back

1. X1 against XB: S-FGS, any FATAL EXCEPTION with its first five lines, L-STOP, L-DES.
2. W1: L-ADOPT, L-PURGE, both L-ID SSIDs, and whether the second session formed; WB's reachability.
3. P1 and P2 anchors against PB and `mAppBounds`; the L-CAR pairs from X1 and XB.
