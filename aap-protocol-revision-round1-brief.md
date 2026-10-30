# aap-protocol-revision, round 1 brief

> **Revised 2026-10-26: all four arms moved.** Upstream `main` moved from `e9377ff9` to `947b3ae9`. B is that
> commit. T was rebased from `c30da573` to `a9988ed8`, and U from `d811b612` to `97d1ee06`, with the same
> patches. PR #1138 (the update APK button, arm A) is now in `main` and its branch is gone from `fork`. So A\*
> is built from `main` `947b3ae9` with the same one-line `versionName` edit, and B now also has the update
> buttons. Re-read section 1 and rebuild all four arms. No run, string or verdict rule changed. A copy that
> still names `e9377ff9`, `c30da573`, `d811b612` or `f809e6dd` is stale.

Published name: `aap-protocol-revision-round1-brief.md`. Results go in
`aap-protocol-revision-round1-results.md`. Evidence: create the release `rig-evidence-aap-protocol-revision`
and upload `aap-protocol-revision-round1-captures.zip` to it (§7 of the template).

Units: D-HU (head unit), D-POCO (phone) and D-SAM (tablet, R6 only). D-MOTO takes no part: switch its
Bluetooth off for the whole round. D-HP takes no part.

Queue line: `aap-protocol-revision-round1-brief.md` (T `a9988ed8` on `fork/fix/touch-cancel-release`, U
`97d1ee06` on `fork/chore/aap-unread-fields`, B `947b3ae9` = `main`, A\* = `main` with one `versionName` line
changed; four builds in one worktree; units D-HU, D-POCO and D-SAM, D-MOTO Bluetooth off; R1 is the point).

Estimated time: 8 min pre-flight, 20 min for four builds, 7 min S-B, 6 min S-T, 6 min S-U1, 4 min S-U2,
5 min R6-HU, 8 min R6-SAM, 5 min end of round, 10 min thermal waits. About 1 h 20 min, of which 36 min is runs.

**R1 is the point of the round.** It shows a held press on B and no held press on T, after an injected
touch CANCEL over a control in the projected video.

Hand steps: none. Injected input: touches into the projected video only (the standing exception in §3), and
at most 4 dumped-target taps on the app per R6 run.

## 0. What the session needs

`rig_session.py` runs the round from this block. Each run is one script, and the session order is the
order below. The runs are the sessions of the old layout: R1 to R5 are graded inside them (section 7).

```rig-requires
lib f2d7a951f
candidate B 947b3ae9fae9834aa8e4145ee91751ed743c2ae0
candidate A 947b3ae9fae9834aa8e4145ee91751ed743c2ae0
candidate T a9988ed85aaeb47cf7bca97e93df82a61c0c8b75
candidate U 97d1ee0656a35000b55f446085dad8fc9ee9e1b7
unit D-HU   role=headunit wifi=on bt=on
unit D-POCO role=phone wifi=on bt=on bond=D-HU aa=D-HU
unit D-MOTO bt=off
unit D-SAM@R6-SAM role=headunit wifi=on
confirm D-HU's WiFi station is joined to a network with internet access (R6-HU asks api.github.com)
confirm D-SAM's WiFi reaches the internet (R6-SAM)
run pre     cap=180  script=pre.sh     grade=grade-pre.sh
run S-B     cap=900  script=s-b.sh     grade=grade-sb.sh
run S-T     cap=840  script=s-t.sh     grade=grade-st.sh point=yes
run S-U1    cap=840  script=s-u1.sh    grade=grade-su1.sh
run S-U2    cap=600  script=s-u2.sh    grade=grade-su2.sh
run R6-HU   cap=720  script=r6.sh      grade=grade-r6.sh
run R6-SAM  cap=960  script=r6.sh      grade=grade-r6.sh
run cleanup cap=420  script=cleanup.sh grade=grade-cleanup.sh always=yes
stop pre FAIL,UNTESTABLE skip=all
```

D-POCO keeps its bond to D-HU for the whole round: with that bond, Android Auto on D-POCO dials D-HU and
nothing else. Every cap includes up to 10 min of the thermal gate.

## 1. Build and baseline

Two candidates, one commit each on `main` `947b3ae9`, and `main` itself as the baseline. PR #1138 (the update
button) is in `main`, so A\* is `main` with one line changed. None of them was tested on the rig before. The
rebase of 2026-10-26 rewrote the history of T and U once.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| B (baseline) | `main` | `947b3ae9fae9834aa8e4145ee91751ed743c2ae0` | `e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165` |
| T (touch) | `fork/fix/touch-cancel-release` | `a9988ed85aaeb47cf7bca97e93df82a61c0c8b75` | `320d394de5a0bff50d1a128b7f34b0b8f953dbec` |
| U (unread fields) | `fork/chore/aap-unread-fields` | `97d1ee0656a35000b55f446085dad8fc9ee9e1b7` | `9c2ff5daf8cb9377662983792fc757a2e1f5fcd8` |
| A* (update button, test build) | `main` | `947b3ae9fae9834aa8e4145ee91751ed743c2ae0` | `e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165` |

The dev host ran the JVM suite at each candidate, with 0 failures: T 4251 tests, U 4245. B and A\* are `main` itself. Do not run
the unit tests on the rig.

**A\* is B with one line changed, on the rig only.** `main` carries `versionName = "4.0.0-alpha"`, which
sorts above the newest release (`3.5.0`), so its update check answers "Up to Date" and never shows the update
dialog. A\* sets `versionName = "3.4.0"` and changes nothing else. B and A\* share one commit. Their APKs
differ by `versionName`, and A\*'s `commit` reads `947b3ae9fae9-dirty`. Commit nothing from the worktree.

**Prepare (the host, before the session).** Build all four in one worktree, in this order, B and A\* back to
back. All four share the base `947b3ae9`, so the cache stays warm. Copy each APK to `$EV/apk/<arm>.apk` at once
(the next build deletes it). Prepare installs nothing: each run installs its own arm. Then copy the
worktree's `app/src/main/res` to `$EV/res`, because R6 reads the locale strings from it.

```text
WT=$($RIG_TOOLKIT/wt-new.sh aap-protocol-revision scratch 947b3ae9fae9834aa8e4145ee91751ed743c2ae0 | tail -1)
git fetch fork fix/touch-cancel-release chore/aap-unread-fields && git fetch origin main
for each arm: git -C $WT checkout --detach <commit>; git -C $WT rev-parse 'HEAD^{tree}' MUST equal the table
$RIG_TOOLKIT/rig_thermal.sh wait 70; HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
B:  947b3ae9, build, copy to $EV/apk/B.apk
A*: sed -i 's/versionName = "4.0.0-alpha"/versionName = "3.4.0"/' $WT/app/build.gradle.kts
    (grep -c 'versionName = "3.4.0"' MUST print 1), build, copy to $EV/apk/A.apk,
    git -C $WT checkout -- app/build.gradle.kts, git -C $WT status --porcelain MUST print nothing
T:  a9988ed8, build, copy to $EV/apk/T.apk
U:  97d1ee06, build, copy to $EV/apk/U.apk
last: checkout 947b3ae9 again; cp -r $WT/app/src/main/res $EV/res; md5sum $EV/apk/*.apk (all four MUST differ)
```

Identity, after every install (`inst` and `apk_ident` in section 5 do it, and each grade checks it):

| Arm | `ACTION_QUERY_STATE` `commit` | `versionName` | DEX `TouchReleasePolicy` | DEX `UpdateLinkPolicy$Choice` |
|---|---|---|---|---|
| B | `947b3ae9fae9` | `4.0.0-alpha` | 0 | at least 1 |
| T | `a9988ed85aae` | `4.0.0-alpha` | at least 1 | at least 1 |
| U | `97d1ee0656a3` | `4.0.0-alpha` | 0 | at least 1 |
| A\* | `947b3ae9fae9-dirty` | `3.4.0` | 0 | at least 1 |

The `UpdateLinkPolicy$Choice` column no longer tells the arms apart. `commit` and `versionName` do, and B and
A\* also differ in md5. A build that does not match its row makes that run UNTESTABLE (wrong build).

## 2. What this is and why

The source is `aap-protocol-revision-20261013.md` in the handoff folder, steps 1 and 4. It read Android
Auto 17.5 and 17.8 against what we send.

**T, step 1.** `TouchEvent.motionEventToAction` sent `ACTION_CANCEL` as 3 and `ACTION_OUTSIDE` as 4. The
phone accepts only 0, 1, 2, 5 and 6, and reads anything else as `ACTION_DOWN`. So a system gesture that
cancels a touch over the projection left a press held on the phone, with no release. `CommManager.loseFocus`
had the same shape: a DOWN at (0, 0) with no UP. T adds `input/TouchReleasePolicy`. On a CANCEL, T moves
every pointer to (8192, 8192), past every canvas, and lifts it there. `loseFocus` now sends a press and a
release at that point. One question is open, and only the phone can answer it: does the phone ignore or
clamp an off-canvas pointer? If it ignores the MOVE and the UP, the press stays held on T too.

**U, step 4.** U stops four fields that 17.8 never reads: `ServiceDiscoveryResponse` field 12
(`hide_projected_clock`), the video sink's `audio_type = NONE` and `available_while_in_call`, and the empty
`KeyEvent` in `ScrollWheelEvent`. The clock stays hidden through `session_configuration` bit 0x01. The rig
checks that the clock stays hidden, that a rotary step still moves, and that a session still forms.

**A, PR #1138 (now in `main`).** Some head unit browsers cannot list a release page's files. The update dialog now offers
"Download APK" as its positive button and "Open GitHub Releases" as its neutral button, on every Android
version. Before, only a unit that needs Conscrypt for TLS 1.2 got the APK link.

## 3. What is different about this round

- **The phone cannot show a held touch in a log or in `dumpsys input`.** Gearhead 18.0 injects each touch
  into its own window (`rpm.f().injectInputEvent`, a `Presentation`'s `Window`), which bypasses the input
  dispatcher. 17.9 and later print no `injectMotionEvent` line (`aa-178-protocol-levers` round 1). So R1
  grades the held press from D-HU screencaps of the projection. The host reads the images. Each cycle has
  its own control shot: shot `b` is taken while the press is held on purpose, so it shows what a held press
  looks like on this layout.
- **So the grade scripts grade the log half only.** A run whose log conditions pass and that has shots to
  read prints `INCONCLUSIVE HOST READS SHOTS: <shots>`. The host then reads those shots and writes the run's
  verdict by the rules in section 7. A log condition that fails on a candidate arm is a FAIL with no shot read.
- **The head unit shows that the CANCEL reached the app.** `[UI_DEBUG] Touch map:` is `AppLog.v` inside
  `if (AppLog.LOG_VERBOSE)`, so S-B and S-T run at `log-level` 0. B prints it for the CANCEL. T returns
  before it. That count is the reachability measure for R1.
- **The R1 target is fixed at (60, 660).** `ultrawide-touch-alignment` round 8 measured D-HU's view at
  1304x720 with Android Auto's bar at the bottom (y 660). Shot `e` is the positive control: a plain tap on
  the same point MUST open something. If it does not, the point was not a control and the cycle is
  INCONCLUSIVE.
- **Not run, and why.** A pinch then a cancel needs two pointers, and `input` injects one. The
  multi-pointer release is covered by `TouchReleasePolicyTest` on the dev host. The assistant close
  (`loseFocus`) has one caller, `CarFYTReceiver`, and D-HU is not an FYT unit. `TouchReleasePolicyTest` covers
  `tapOffCanvas`.
- **R2, the real edge-back gesture, needs gesture navigation.** `pre` reads `settings get secure
  navigation_mode`. If it is not `2`, S-T skips R2 and R2 is UNTESTABLE (no gesture navigation). Do not change
  the navigation mode.
- **The clock and the rotary focus are graded from screencaps too.** The host reads them.
- **`Service Discovery Response` prints only at VERBOSE** (`AapTransport` logs each message with `AppLog.v`).
  S-U1 and S-U2 run at INFO, so the session lines are `Service Discovery Request: `,
  `Media Sink Setup Request: ` and `Throughput over`.
- **A VERBOSE capture carries two `SSL handshake complete` lines per session** (the DEBUG form joins the INFO
  form). Count sessions by `Service Discovery Request: `, never by the SSL line.
- **R6 needs the internet.** `UpdateChecker` asks `api.github.com`. If a unit has no route, the app logs
  `Update check failed: ` and R6 on that unit is INCONCLUSIVE (no update server).
- **`SettingsActivity` is not exported.** Start it with `run-as` on both D-HU and D-SAM.
- **D-POCO's WiFi turns itself back on** a few seconds after one `svc wifi disable` (`D-POCO.md`). So the
  phone leaves the link with `rl_hold_off` on WiFi, which repeats the disable every 3 s until `link_on`.
- **The thermal gate is `rl_thermal_gate`** (`$RIG_TOOLKIT/rig_thermal.sh`, limits in
  `rig-quirks/topics/tooling.md`). If its second wait fails, the run writes `$EV/HOST_HOT` and every later
  run grades UNTESTABLE (host thermal) without touching a unit.
- Read `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-POCO.md`,
  `rig-quirks/units/D-SAM.md`, `rig-quirks/units/D-SAM-and-D-HP.md` and `rig-quirks/topics/projection.md`
  first.

## 4. Settings keys

`pre` backs up D-HU's `settings.xml` to `$EV/settings-backup-hu.xml`. R6-SAM backs up D-SAM's to
`$EV/settings-backup-sam.xml` before its install. `cleanup` saves each end state, restores each backup and
diffs it. State the delta in Setup notes. Leave the audio keys as they are: they are a deliberate worst case.
No run grades audio.

Record, do not change: `key-codes`, `enable-rotary`, `video-profile-starvation-cap`, `fullscreen-mode`,
`resolutionId`, `native-driver-selection-mode`, `native-poke-bt-macs`, `native-poke-all-paired`
(`pre` writes them to `$EV/pre-keys.txt` and `$EV/pre-keycodes.txt`). If `key-codes` holds any entry, the host
reads it and says whether keycode 1, 2 or 4 is remapped. A remapped key makes R4 INCONCLUSIVE.

**D-HU, per session** (`base LOG CLOCK` in section 5 writes them with `set_hu_settings_host.py` and reads
them back to `$EV/settings-<run>-keys.xml`):

| Session | `wifi-connection-mode` | `log-level` | `onboarding-version` | `hide-clock` |
|---|---|---|---|---|
| S-B, S-T | `<int ... value="3" />` | `<int ... value="0" />` | `<int ... value="2" />` | `<boolean ... value="true" />` |
| S-U1 | 3 | 2 | 2 | true |
| S-U2 | 3 | 2 | 2 | **false** |
| R6-HU | 3 | 2 | 2 | true |

**D-SAM** (R6-SAM): `<int name="log-level" value="2" />` and `<int name="onboarding-version" value="2" />`.

## 5. Helpers

Every adb call runs in sequence, never two at once against one unit. `lib.sh` is sourced before every
script and grade. `ui.py` is saved beside it in `$EV/scripts/`.

**`lib.sh`**
```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
CK=$PKG/com.andrerinas.openheadunit.connection.carkey.CarKeyBroadcastReceiver
SA=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
HU=27870808938846; PH=4f4027e9; SAM=30041c35642d2200
UI="$EV/scripts/ui.py"; RES="$EV/res"
B_COMMIT=947b3ae9fae9; A_COMMIT=947b3ae9fae9-dirty; T_COMMIT=a9988ed85aae; U_COMMIT=97d1ee0656a3
send()  { local a=$1; shift; adb -s "$HU"  shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
sends() { local a=$1; shift; adb -s "$SAM" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
# one marker in both captures: AutomationMarker on D-HU, the same text under tag RIG on D-POCO
step() { rl_step "$1"; send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s "$PH" shell log -t RIG "AutomationMarker: $1"; sleep 0.3; }
launch() { adb -s "$HU" shell am start -n "$MAIN" >/dev/null; }
hustop() { adb -s "$HU" shell am force-stop "$PKG"; sleep 1; }
huexit() { adb -s "$HU" shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5; hustop; }

# host thermal: a second failed wait marks the rest of the round UNTESTABLE
cool() { [ -f "$EV/HOST_HOT" ] && { echo HOST_HOT; exit 3; }
  rl_step "WAIT cap=600s host thermal gate"; rl_thermal_gate 75 || { touch "$EV/HOST_HOT"; exit 3; }
  touch "$EV/$RUN-ran"; }

# D-POCO off and on the link with svc; its WiFi turns itself back on, so the disable is held
link_off() { rl_hold_off "$PH" wifi 3; rl_radio "$PH" bt off; }
link_on()  { rl_release "$PH" wifi; rl_radio "$PH" wifi on; rl_radio "$PH" bt on; }

# input into the projected video, and keys through the car-key receiver
mev()  { adb -s "$HU" shell input touchscreen motionevent "$1" "$2" "$3"; sleep 0.3; }
tap()  { adb -s "$HU" shell input tap "$1" "$2"; sleep 0.3; }
key()  { adb -s "$HU" shell am broadcast -f 0x00000020 -n "$CK" -a com.microntek.irkeyDown --ei keyCode "$1" >/dev/null; sleep 0.3
         adb -s "$HU" shell am broadcast -f 0x00000020 -n "$CK" -a com.microntek.irkeyUp --ei keyCode "$1" >/dev/null; sleep 0.3; }
shot() { local s=${2:-$HU}; adb -s "$s" shell screencap -p /sdcard/ar1.png; sleep 0.3
         adb -s "$s" pull /sdcard/ar1.png "$EV/$1.png" >/dev/null; sleep 0.3; }
dump() { local s=${2:-$HU}; adb -s "$s" shell uiautomator dump /sdcard/ar1.xml >/dev/null; sleep 0.3
         adb -s "$s" pull /sdcard/ar1.xml "$EV/$1.xml" >/dev/null; sleep 0.3; }

# settings on D-HU, app stopped (adbd is root on D-HU)
hu_keys() { python3 -I "$RIG_TOOLKIT/set_hu_settings_host.py" "$HU" "$@" >> "$EV/keys.log" 2>&1; }
hu_read() { adb -s "$HU" shell cat "/data/data/$PKG/shared_prefs/settings.xml" > "$EV/settings-$1.xml"
  grep -aoE '(wifi-connection-mode|log-level|onboarding-version|hide-clock|enable-rotary)"[^/]*' "$EV/settings-$1.xml"; }
base() { hustop; hu_keys scalar:int:wifi-connection-mode:3 "scalar:int:log-level:$1" scalar:int:onboarding-version:2 \
  "scalar:boolean:hide-clock:$2"; hu_read "$RUN-keys"; }

# apk_ident ARM SERIAL: is the live APK $EV/apk/ARM.apk? Writes $EV/ident-$RUN.txt; 1 on a mismatch
apk_ident() { local p w l
  p=$(adb -s "$2" shell pm path "$PKG" | cut -d: -f2 | tr -d '\r')
  adb -s "$2" pull "$p" "$EV/live.apk" >/dev/null 2>&1
  w=$(md5sum < "$EV/apk/$1.apk" | cut -d' ' -f1); l=$(md5sum < "$EV/live.apk" | cut -d' ' -f1)
  { echo "want=$w live=$l"
    for c in TouchReleasePolicy 'UpdateLinkPolicy$Choice'; do
      echo "dex $c=$(unzip -p "$EV/live.apk" 'classes*.dex' | strings | grep -cF "$c")"; done; } > "$EV/ident-$RUN.txt" 2>&1
  [ -n "$w" ] && [ "$w" = "$l" ]; }
# inst ARM: install on D-HU (the phone off the link first), check it, read QUERY_STATE
inst() { hustop; adb -s "$HU" install -r -d "$EV/apk/$1.apk" > "$EV/install-$RUN.txt" 2>&1
  apk_ident "$1" "$HU" || return 1
  launch; sleep 5; send ACTION_QUERY_STATE > "$EV/qs-$RUN.txt"; huexit; }

# one bring-up: phone off, keys, captures, launch, phone on. 0 when the phone asked for discovery
sess_try() {
  link_off; base "$1" "$2"
  adb -s "$HU" logcat -c; adb -s "$HU" logcat -G 16M
  rl_cap_start "$RUN-hu" "$HU"
  rl_cap_start "$RUN-phone" "$PH" -T 1 -b main,system,crash,events
  launch; sleep 1; step "$RUN-start"
  rl_wait_line "$EV/$RUN-hu.txt" 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 30
  rl_sleep 10
  link_on; step "$RUN-phone"
  rl_wait_line "$EV/$RUN-hu.txt" 'Service Discovery Request: ' 120 || return 1
  rl_wait_line "$EV/$RUN-hu.txt" 'Throughput over' 40; return 0; }
# sess_up LOG CLOCK: a session, with one more bring-up on a miss; the projection in front; the clock shot
sess_up() { local f=miss i
  if ! sess_try "$1" "$2"; then
    step "$RUN-miss"; rl_cap_stop; huexit
    mv "$EV/$RUN-hu.txt" "$EV/$RUN-hu-miss.txt"; mv "$EV/$RUN-phone.txt" "$EV/$RUN-phone-miss.txt"
    sess_try "$1" "$2" || { echo "session=none focus=none" > "$EV/$RUN-session.txt"; return 1; }
  fi
  for i in 1 2; do
    send ACTION_RAISE_PROJECTION >/dev/null; sleep 3
    adb -s "$HU" shell dumpsys window | grep -a mCurrentFocus > "$EV/$RUN-focus.txt"
    grep -q AapProjectionActivity "$EV/$RUN-focus.txt" && { f=ok; break; }
  done
  echo "session=ok focus=$f" > "$EV/$RUN-session.txt"
  step "$RUN-up"; sleep 5; shot "$RUN-clock"; }
# hold the session 60 s past its start, so R5 has 60 s of Throughput lines; then end it
sess_end() { local t
  t=$(( 60 - ( $(date +%s) - $(stat -c %Y "$EV/$RUN-session.txt" 2>/dev/null || date +%s) ) ))
  [ "$t" -gt 0 ] && rl_sleep "$t"
  step "$RUN-end"; rl_cap_stop; huexit; link_off; }
sess_none() { step "$RUN-end"; rl_cap_stop; huexit; link_off; }

# R1: one cycle C, idle, held press, CANCEL, a tap elsewhere, the positive control, BACK
r1_cycle() { local c=$1
  step "$c-a";      shot "$c-a"
  step "$c-down";   mev DOWN 60 660;   sleep 1.5; shot "$c-b"
  step "$c-cancel"; mev CANCEL 60 660; sleep 2;   shot "$c-c"
  step "$c-else";   tap 640 300;       sleep 2;   shot "$c-d"
  step "$c-btn";    tap 60 660;        sleep 3;   shot "$c-e"
  step "$c-back";   key 4;             sleep 3;   shot "$c-f"
  step "$c-done"; }
# r1_void FILE C: true for a void cycle (fewer than 7 markers, both ends counted; a new session; no DOWN logged)
r1_void() { local m s d
  m=$(rl_cnt "$1" "$2-a" "$2-done" 'AutomationMarker: ')
  s=$(rl_cnt "$1" "$2-a" "$2-done" 'Service Discovery Request: ')
  d=$(rl_cnt "$1" "$2-down" "$2-cancel" '[UI_DEBUG] Touch map: raw=')
  [ "${m:-0}" -lt 7 ] || [ "${s:-0}" -ge 1 ] || [ "${d:-0}" -eq 0 ]; }
# r1_arm ARM: two cycles, and one more if either was void
r1_arm() { local v=0
  r1_cycle "R1-$1-c1"; r1_void "$EV/$RUN-hu.txt" "R1-$1-c1" && v=1
  r1_cycle "R1-$1-c2"; r1_void "$EV/$RUN-hu.txt" "R1-$1-c2" && v=1
  [ "$v" = 1 ] && r1_cycle "R1-$1-c3"; return 0; }
# R4: three rotary steps forward, three back
r4_scroll() { local c=$1
  step "$c-a"; shot "$c-s0"
  key 2; sleep 1.5; shot "$c-s1"
  key 2; sleep 1.5; shot "$c-s2"
  key 2; sleep 1.5; shot "$c-s3"
  key 1; key 1; key 1; sleep 1.5; shot "$c-s4"
  step "$c-done"; key 4; sleep 2; }

# --- grading ---
NFAIL=0; INC=""
cnd() { local l; l=$(rl_cond "$@"); echo "$l"; case "$l" in *" FAIL "*) NFAIL=$((NFAIL + 1)) ;; esac; }
inc() { INC="${INC:+$INC; }$*"; }
same() { [ "$1" = "$2" ] && echo 1 || echo 0; }
qs_field() { grep -ao "$2[^,}]*" "$1" 2>/dev/null | head -1 | tr -d '"\\ ' | cut -d: -f2-; }
dex_count() { grep -a "dex $1=" "$EV/ident-$RUN.txt" 2>/dev/null | head -1 | cut -d= -f2; }
untestable_if_hot() { if [ -f "$EV/HOST_HOT" ] && [ ! -f "$EV/$RUN-ran" ]; then
  rl_verdict UNTESTABLE "host thermal: the second cooling wait failed"; exit 0; fi; }
# ident_conds ARM COMMIT VERSIONNAME TRP: section 1's identity row; a mismatch is UNTESTABLE (wrong build)
ident_conds() { local q="$EV/qs-$RUN.txt" md
  md=$(awk '{split($1, a, "="); split($2, b, "=")} a[2] != "" && a[2] == b[2] {n++} END {print n+0}' "$EV/ident-$RUN.txt" 2>/dev/null)
  cnd id-md5 eq "${md:-0}" 1 "live APK is apk/$1.apk"
  cnd id-commit eq "$(same "$(qs_field "$q" commit)" "$2")" 1 "QUERY_STATE commit $2"
  cnd id-version eq "$(same "$(qs_field "$q" versionName)" "$3")" 1 "versionName $3"
  if [ "$4" = 0 ]; then cnd id-dex eq "$(dex_count TouchReleasePolicy)" 0 "no TouchReleasePolicy in the DEX"
  else cnd id-dex ge "$(dex_count TouchReleasePolicy)" 1 "TouchReleasePolicy in the DEX"; fi
  [ "$NFAIL" -eq 0 ] || { rl_verdict UNTESTABLE "the build on the unit is not arm $1"; exit 0; }; }
session_or_untestable() { case "$(cat "$EV/$RUN-session.txt" 2>/dev/null)" in
  session=ok*) ;; *) rl_verdict UNTESTABLE "no session on two bring-ups"; exit 0 ;; esac
  grep -q focus=ok "$EV/$RUN-session.txt" || inc "the projection was not in front after two raises: touch, clock and scroll void"; }
# r5_conds: R5 for this run's session, start to end
r5_conds() { local H="$EV/$RUN-hu.txt" W vs rm
  W=$(rl_win "$H" "$RUN-start" "$RUN-end")
  vs=$(printf '%s\n' "$W" | grep -aF 'Media Sink Setup Request: ' | awk 'index($0, "on channel VIDEO") {n++} END {print n+0}')
  rm=$(printf '%s\n' "$W" | grep -ao 'rendered=[0-9]*' | cut -d= -f2 | sort -n | tail -1)
  cnd R5-sdr eq "$(printf '%s\n' "$W" | grep -acF 'Service Discovery Request: ')" 1 "R5 $RUN one discovery"
  cnd R5-vsink ge "$vs" 1 "R5 $RUN a video sink"
  cnd R5-thr ge "$(printf '%s\n' "$W" | grep -acF 'Throughput over')" 10 "R5 $RUN Throughput lines"
  cnd R5-rmax gt "${rm:-0}" 0 "R5 $RUN rendered"
  cnd R5-crit eq "$(rl_cnt "$EV/$RUN-phone.txt" "$RUN-phone" "$RUN-end" 'Critical error')" 0 "R5 $RUN phone Critical error"   # phone
  echo "INFO R5 $RUN wake=$(printf '%s\n' "$W" | grep -acF 'MATCH! Starting AapService')" \
    "bar=[$(rl_win "$EV/$RUN-phone.txt" "$RUN-phone" "$RUN-end" | grep -aF -m1 'GhFacetBar.onWindowSurfaceAvailable width:' | grep -ao 'width: .*')]"   # phone
}
# r1_grade ARM CANCEL: COND lines for the first two graded cycles; CANCEL is the expected tm_cancel
r1_grade() { local H="$EV/$RUN-hu.txt" c n=0
  for c in "R1-$1-c1" "R1-$1-c2" "R1-$1-c3"; do
    if r1_void "$H" "$c"; then echo "INFO $c void or not run"; continue; fi
    [ "$n" -ge 2 ] && break; n=$((n + 1))
    echo "INFO $c tm_else=$(rl_cnt "$H" "$c-else" "$c-btn" '[UI_DEBUG] Touch map: raw=') tm_btn=$(rl_cnt "$H" "$c-btn" "$c-back" '[UI_DEBUG] Touch map: raw=')"
    cnd "$c-down" eq "$(rl_cnt "$H" "$c-down" "$c-cancel" '[UI_DEBUG] Touch map: raw=')" 1 "$c tm_down"
    cnd "$c-cancel" eq "$(rl_cnt "$H" "$c-cancel" "$c-else" '[UI_DEBUG] Touch map: raw=')" "$2" "$c tm_cancel"
  done
  R1_GRADED=$n; }
# r4_counts FILE C: prints "rx right left"
r4_counts() { local W; W=$(rl_win "$1" "$2-a" "$2-done")
  echo "$(printf '%s\n' "$W" | grep -acF 'CarKeyReceiver: Handling intent action: ')" \
    "$(printf '%s\n' "$W" | grep -aF 'CommManager: TX Key -> AA=' | awk 'index($0, "AA=2 (isPress=true)") {n++} END {print n+0}')" \
    "$(printf '%s\n' "$W" | grep -aF 'CommManager: TX Key -> AA=' | awk 'index($0, "AA=1 (isPress=true)") {n++} END {print n+0}')"; }
# verdict_line control|candidate SHOTS: the VERDICT after the COND lines (section 3)
verdict_line() {
  if [ "$1" = control ]; then
    if [ "$NFAIL" -gt 0 ] || [ -n "$INC" ]; then rl_verdict INCONCLUSIVE "control did not hold: ${INC:-see the FAIL lines}"
    else rl_verdict INCONCLUSIVE "HOST READS SHOTS: $2 (the control's log half holds)"; fi
    return 0; fi
  [ "$NFAIL" -gt 0 ] && return 0
  if [ -n "$INC" ]; then rl_verdict INCONCLUSIVE "$INC"
  elif [ -n "$2" ]; then rl_verdict INCONCLUSIVE "HOST READS SHOTS: $2 (log half PASS)"; fi
  return 0; }
```

**`ui.py`**
```python
import re, sys, xml.etree.ElementTree as ET
def nodes(path, rid):
    return [n for n in ET.parse(path).getroot().iter('node') if n.get('resource-id', '').endswith(rid)]
cmd = sys.argv[1]
if cmd == 'find':            # find DUMP RID [TEXT] -> "x y" of the one match, else COUNT=n and exit 1
    hits = nodes(sys.argv[2], sys.argv[3])
    if len(sys.argv) > 4:
        hits = [n for n in hits if n.get('text', '').strip().lower() == sys.argv[4].strip().lower()]
    if len(hits) != 1:
        print('COUNT=%d' % len(hits)); sys.exit(1)
    b = [int(v) for v in re.findall(r'\d+', hits[0].get('bounds'))]
    print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
elif cmd == 'texts':         # texts DUMP -> the dialog's title, message and three buttons
    for rid in ('id/alertTitle', 'android:id/message', 'android:id/button1', 'android:id/button2', 'android:id/button3'):
        print(rid, '=', '|'.join(n.get('text', '') for n in nodes(sys.argv[2], rid)))
elif cmd == 'str':           # str RES_DIR LANG NAME -> the string in values-LANG, else in values
    for d in ('values-' + sys.argv[3], 'values'):
        try:
            for s in ET.parse('%s/%s/strings.xml' % (sys.argv[2], d)).getroot().iter('string'):
                if s.get('name') == sys.argv[4]:
                    print((s.text or '').replace("\\'", "'")); sys.exit(0)
        except (FileNotFoundError, ET.ParseError):
            pass
    sys.exit(1)
```

## 6. The deciding lines

Each head unit string was checked with `git grep -F` on `app/src/main` at every SHA the table names. All print at
INFO or WARN except `Touch map`, which needs `log-level` 0.

**Head unit capture** (`$EV/<run>-hu.txt`, the window named in each run):

| Grep | Arms | Meaning |
|---|---|---|
| `AutomationReceiver: ` / `AutomationMarker: ` | all | a verb or a marker landed; a missing line voids the step |
| `[UI_DEBUG] Touch map: raw=` | B, T (VERBOSE) | one per touch event the app sent with coordinates; B prints it for a CANCEL, T does not |
| `CarKeyReceiver: Handling intent action: ` | B, U | an injected key reached the app |
| `CommManager: TX Key -> AA=` | B, U | a key went to the phone (run-time `CommManager: TX Key -> AA=2 (isPress=true) src=carkey`) |
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | all | the Bluetooth listeners are open (settle line) |
| `Service Discovery Request: ` | all | the phone asked for discovery: one per session |
| `Media Sink Setup Request: ` | all | the phone set up a sink (run-time `Media Sink Setup Request: 3 on channel VIDEO`) |
| `Throughput over` | all | video decoded; read `rendered=` from it |
| `SSL handshake complete` | all | record only (two per session at VERBOSE) |
| `MATCH! Starting AapService` | all | a self wake (record) |
| `SettingsFragment: opening update link (` | A\* only | a dialog button opened a link (run-time `... (apk) https://...` or `... (release page) https://...`) |
| `Update check failed: ` | A\* | the update server did not answer (run-time `UpdateChecker: Update check failed: `) |
| `Failed to open GitHub releases URL: ` | A\* | no app took the link |

**Phone capture** (`$EV/<run>-phone.txt`, from `<run>-phone` to `<run>-end`; the markers come from
`log -t RIG`). `pre` records the Gearhead version in `$EV/pre-records.txt`.

| Grep | Meaning |
|---|---|
| `Critical error` | Android Auto ended a session on a protocol fault (MUST be 0). The window ends at `<run>-end`, before the Exit, so the Exit's own lines are outside it |
| `GhFacetBar.onWindowSurfaceAvailable width:` | the bar's size; record the first line of each session |

**System reads**: `mCurrentFocus` (`dumpsys window`), `mResumedActivity` or `topResumedActivity` (`dumpsys
activity activities`), `navigation_mode` (`settings get secure`). The screencaps are read by the host.

## 7. Runs

Each run below is one `run` line of section 0. Its script and its grade are the named blocks under it.

### pre: records and the D-HU backup (read only)

Records what later runs need: the `input` usage (R1 and R2 need `CANCEL` in it), `navigation_mode` (R2),
the display size, the station, the browser that takes a link, the Gearhead version, the `shared_prefs`
owner, and the keys section 4 lists. It also sets `svc power stayon true` and backs up `settings.xml`.
`wm size` on D-HU prints the panel in portrait (720x1440); the display size is the `cur=` line.

**`pre.sh`**
```bash
rl_step "records"
adb -s "$HU" shell input 2>&1 | grep -a motionevent > "$EV/pre-input.txt"
adb -s "$HU" shell settings get secure navigation_mode | tr -d '\r' > "$EV/pre-navmode.txt"
{ adb -s "$HU" shell wm size
  adb -s "$HU" shell dumpsys window displays | grep -a -m2 -E 'cur=|app='
  adb -s "$HU" shell dumpsys wifi | grep -a -m2 mWifiInfo
  adb -s "$HU" shell cmd package resolve-activity --brief -a android.intent.action.VIEW -d https://github.com
  adb -s "$PH" shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
  adb -s "$HU" shell stat -c '%U:%G %a' "/data/data/$PKG/shared_prefs"; } > "$EV/pre-records.txt" 2>&1
adb -s "$HU" shell svc power stayon true
adb -s "$PH" shell input keyevent KEYCODE_HOME
rl_step "backup"
hustop
adb -s "$HU" shell cat "/data/data/$PKG/shared_prefs/settings.xml" > "$EV/settings-backup-hu.xml"
grep -aoE '(enable-rotary|video-profile-starvation-cap|fullscreen-mode|resolutionId|native-driver-selection-mode|native-poke-all-paired)"[^/]*' \
  "$EV/settings-backup-hu.xml" > "$EV/pre-keys.txt"
grep -a -A3 -E 'key-codes|native-poke-bt-macs' "$EV/settings-backup-hu.xml" > "$EV/pre-keycodes.txt"
```

**`grade-pre.sh`**
```bash
cnd pre-backup ge "$(grep -ac '</map>' "$EV/settings-backup-hu.xml" 2>/dev/null)" 1 "D-HU settings.xml backed up"
echo "INFO input: $(grep -ao 'motionevent.*' "$EV/pre-input.txt" 2>/dev/null | head -1)"   # system
echo "INFO navigation_mode=$(cat "$EV/pre-navmode.txt" 2>/dev/null)"
```

PASS: the backup holds a whole file. A FAIL stops the round, because `cleanup` would have nothing to restore.

### S-B: install B, then R1 and R4 on B (the control)

`link_off`, `inst B`, `sess_up 0 true`, R1 on B, R4 on B, `sess_end`. `base` re-writes the keys after the
install, because an install can wipe `settings.xml`.

**`s-b.sh`**
```bash
cool
link_off
inst B || exit 4
sess_up 0 true || { sess_none; exit 0; }
r1_arm B
r4_scroll R4-B
sess_end
```

**`grade-sb.sh`**
```bash
untestable_if_hot
ident_conds B "$B_COMMIT" 4.0.0-alpha 0
session_or_untestable
r5_conds
r1_grade B 1
[ "$R1_GRADED" -ge 1 ] || inc "no graded R1-B cycle"
read -r RX RIGHT LEFT <<< "$(r4_counts "$EV/$RUN-hu.txt" R4-B)"
cnd R4-B-rx ge "$RX" 12 "R4-B every key broadcast landed"
cnd R4-B-right ge "$RIGHT" 3 "R4-B right steps sent"
cnd R4-B-left ge "$LEFT" 3 "R4-B left steps sent"
verdict_line control "R1-B-c* a to f; R4-B-s0 to s4; S-B-clock"
```

S-B is the reference for R1, R3, R4 and R5. Its log half holds when the build is B, a session forms, every
graded R1-B cycle logs one `Touch map` for the DOWN and one for the CANCEL, and every R4-B key lands.

### S-T: install T, then R1 and R2 on T (THE POINT)

**`s-t.sh`**
```bash
cool
link_off
inst T || exit 4
sess_up 0 true || { sess_none; exit 0; }
r1_arm T
if [ "$(tr -d '\r ' < "$EV/pre-navmode.txt" 2>/dev/null)" = 2 ]; then
  step R2-T-a; shot R2-T-a
  step R2-T-swipe; adb -s "$HU" shell input swipe 2 660 400 660 300; sleep 3; shot R2-T-b
  step R2-T-done
fi
sess_end
```

**`grade-st.sh`**
```bash
untestable_if_hot
ident_conds T "$T_COMMIT" 4.0.0-alpha 1
session_or_untestable
r5_conds
grep -q CANCEL "$EV/pre-input.txt" 2>/dev/null || { rl_verdict UNTESTABLE "D-HU input lists no CANCEL motionevent (R1, R2)"; exit 0; }
r1_grade T 0
[ "$R1_GRADED" -ge 2 ] || inc "R1-T has $R1_GRADED graded cycles, 2 needed"
BREF=0
for c in R1-B-c1 R1-B-c2 R1-B-c3; do
  r1_void "$EV/S-B-hu.txt" "$c" ||
    { [ "$(rl_cnt "$EV/S-B-hu.txt" "$c-cancel" "$c-else" '[UI_DEBUG] Touch map: raw=')" = 1 ] && BREF=$((BREF + 1)); }
done
[ "$BREF" -ge 1 ] || inc "no graded R1-B cycle logged the CANCEL, so R1 has no control"
if [ "$(tr -d '\r ' < "$EV/pre-navmode.txt" 2>/dev/null)" = 2 ]; then
  cnd R2-sdr eq "$(rl_cnt "$EV/$RUN-hu.txt" R2-T-a R2-T-done 'Service Discovery Request: ')" 0 "R2 no new session"
  echo "INFO R2 tm=$(rl_cnt "$EV/$RUN-hu.txt" R2-T-swipe R2-T-done '[UI_DEBUG] Touch map: raw=')"
else
  echo "INFO R2 UNTESTABLE: navigation_mode=$(cat "$EV/pre-navmode.txt" 2>/dev/null), not 2"
fi
verdict_line candidate "R1-B-c* b, c, e and R1-T-c* b to f; R2-T-a and R2-T-b"
```

The host reads shots `a` to `f` of every graded cycle and writes one line for each: `b` held highlight on
the target (yes/no), `c` held highlight (yes/no) and new screen (yes/no), `d` held highlight and new screen,
`e` new screen opened (yes/no and which), `f` back at the start screen (yes/no).

- **Void cycle** (the scripts decide it): fewer than 7 markers from `<c>-a` to `<c>-done` (both ends are
  counted), or a new session in the cycle, or no `Touch map` for the DOWN. A void cycle is replaced by one
  extra cycle (`c3`), once per arm.
- **INCONCLUSIVE cycle** (the host decides it): `e` opened nothing (the point is not a control), or `b` shows
  no held highlight (a held press is not visible on this layout).
- **B, expected (the control):** `tm_cancel` = 1 and `c` shows the highlight still held. A B cycle that
  shows no held highlight in `c` makes R1 INCONCLUSIVE: the screencap cannot tell the two arms apart.
  A T cycle that shows a held highlight in `c` is still a FAIL.
- **T, PASS (each graded cycle):** `tm_down` = 1, `tm_cancel` = 0, `c` shows no held highlight and no new
  screen, `d` shows no new screen from the bar target, `e` opened a screen, and the phone's `Critical error`
  count from `S-T-phone` to `S-T-end` is 0.
- **T, FAIL:** `c` shows the highlight still held (the phone ignored the off-canvas pointer), or `c` or `d`
  shows the target's screen (a release was taken as a click), or `tm_cancel` >= 1, or `Critical error` >= 1.
- **R1 PASS:** at least one graded B cycle shows the held press, and both graded T cycles pass.

What a PASS would look like if T did nothing: `c` held, as on B. So B's `c` is the reference, and `b` on the
same arm shows what a held press looks like.

**R2, a real edge-back gesture over the bar clicks nothing (T only).** It runs only if `pre` read `2`.
The host reads `R2-T-a` and `R2-T-b`. **PASS:** `b` shows no held highlight on the bar and no screen the bar
opens, and `R2-sdr` = 0. **FAIL:** `b` shows a held highlight or a screen opened from the bar. Record `tm`: a
value near 2 means the system took the swipe, and a large value means the app received the whole swipe.

### S-U1: install U, then R4 on U

**`s-u1.sh`**
```bash
cool
link_off
inst U || exit 4
sess_up 2 true || { sess_none; exit 0; }
r4_scroll R4-U
sess_end
```

**`grade-su1.sh`**
```bash
untestable_if_hot
ident_conds U "$U_COMMIT" 4.0.0-alpha 0
session_or_untestable
r5_conds
read -r RX RIGHT LEFT <<< "$(r4_counts "$EV/$RUN-hu.txt" R4-U)"
read -r BRX BRIGHT BLEFT <<< "$(r4_counts "$EV/S-B-hu.txt" R4-B)"
echo "INFO R4-B rx=$BRX right=$BRIGHT left=$BLEFT"
echo "INFO key-codes: $(tr '\n' ' ' < "$EV/pre-keycodes.txt" 2>/dev/null)"
if [ "${RX:-0}" -lt 12 ]; then inc "R4-U void: $RX of 12 key broadcasts landed"
else
  cnd R4-U-right ge "$RIGHT" 3 "R4-U right steps sent"
  cnd R4-U-left ge "$LEFT" 3 "R4-U left steps sent"
fi
[ "${BRIGHT:-0}" -ge 3 ] || inc "R4-B reference sent $BRIGHT right steps, 3 needed"
verdict_line candidate "R4-B-s0 to s4 and R4-U-s0 to s4 (and key-codes); S-U1-clock"
```

Keycode 2 is `KEYCODE_SOFT_RIGHT`, which `AapTransport.send` turns into a `ScrollWheelEvent` with delta +1.
Keycode 1 is the step back. The host reads `s0` to `s4` of both arms and writes, for each, whether a focus
highlight shows and on which control.

- **Void:** `rx` below 12 (a broadcast did not land).
- **PASS:** on R4-U, `right` >= 3, `left` >= 3, a focus highlight appears by `s1` and is on a different control
  in `s3` than in `s1`, and R4-B shows the same.
- **FAIL:** R4-B moves the focus and R4-U does not, with `right` >= 3 on both.
- **INCONCLUSIVE:** R4-B moves no focus either. Then the rotary input does nothing on this phone, on either
  build. Record `enable-rotary`.

### S-U2: U with the clock shown, then R3 and R5

U is already installed: S-U2 checks the live APK and reads `QUERY_STATE`, then brings a session up with
`hide-clock` false.

**`s-u2.sh`**
```bash
cool
link_off
hustop
apk_ident U "$HU" || exit 4
send ACTION_QUERY_STATE > "$EV/qs-$RUN.txt"; hustop
sess_up 2 false || { sess_none; exit 0; }
sess_end
```

**`grade-su2.sh`**
```bash
untestable_if_hot
ident_conds U "$U_COMMIT" 4.0.0-alpha 0
session_or_untestable
r5_conds
verdict_line candidate "R3: S-B-clock, S-T-clock, S-U1-clock and S-U2-clock"
```

**R3, the hidden clock stays hidden (U), with a control.** The clock shots are taken 5 s after each session
came up. The host reads where Android Auto draws its clock (on the bar) and writes clock shown (yes/no) for each.

- **PASS:** `S-U1-clock` shows no clock (`hide-clock` true), and `S-U2-clock` shows the clock (`hide-clock`
  false).
- **FAIL:** `S-U1-clock` shows the clock while `S-B-clock` does not. Then the phone read field 12 after all.
- **INCONCLUSIVE:** `S-B-clock` shows the clock too. Then `hide-clock` does nothing on this Gearhead, on
  either build.

**R5, a session still forms and renders (U), with B and T beside it.** Every session's grade prints the R5
lines from its own capture, `<run>-start` to `<run>-end`.

- **PASS (S-U1 and S-U2):** `sdr` = 1, `vsink` >= 1, `thr` >= 10, `rmax` > 0, and `crit` = 0.
- **FAIL:** any of those missed on a U session while S-B meets it.
- The same lines are graded for S-B and S-T. A T session that misses one of them is a FAIL of T.

### R6-HU and R6-SAM: "Check for updates" offers "Download APK" and opens it (A\*)

One script for both units. R6-HU installs A\* on D-HU with the phone off the link. R6-SAM pings
`api.github.com`, records D-SAM's build, backs up its `settings.xml`, installs A\* over USB and writes its two
keys. Phone capture: no. At most 4 taps on the app, all on dumped targets; the script enforces the budget.
D-SAM draws its first settings frame about 4.6 s after a cold start, so the row search polls for 20 s.

**`r6.sh`**
```bash
cool
case "$RUN" in
  R6-SAM) U=$SAM
    adb -s "$SAM" shell ping -c 1 -W 3 api.github.com > "$EV/$RUN-ping.txt" 2>&1
    sends ACTION_QUERY_STATE > "$EV/qs-$RUN-before.txt"
    adb -s "$SAM" shell am force-stop "$PKG"
    adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-backup-sam.xml"
    adb -s "$SAM" install -r -d "$EV/apk/A.apk" > "$EV/install-$RUN.txt" 2>&1
    apk_ident A "$SAM" || exit 4
    python3 -I "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" scalar:int:log-level:2 scalar:int:onboarding-version:2 >> "$EV/keys.log" 2>&1
    adb -s "$SAM" shell am start -n "$MAIN" >/dev/null; sleep 10
    sends ACTION_QUERY_STATE > "$EV/qs-$RUN.txt" ;;
  *) U=$HU
    link_off
    inst A || exit 4
    base 2 true ;;
esac
L=$(adb -s "$U" shell getprop persist.sys.locale | tr -d '\r')
[ -z "$L" ] && L=$(adb -s "$U" shell getprop persist.sys.language | tr -d '\r')
LG=${L%%-*}
T_ROW=$(python3 -I "$UI" str "$RES" "$LG" check_for_updates); T_APK=$(python3 -I "$UI" str "$RES" "$LG" download_apk)
T_REL=$(python3 -I "$UI" str "$RES" "$LG" open_github_releases); T_TTL=$(python3 -I "$UI" str "$RES" "$LG" update_available_title)
Q=${T_ROW##* }                         # one word of the row's title, so the search needs no quoting
printf '%s\n' "locale=$L" "row=$T_ROW" "apk=$T_APK" "rel=$T_REL" "title=$T_TTL" "q=$Q" > "$EV/$RUN-strings.txt"
adb -s "$U" shell am force-stop "$PKG"; sleep 1
adb -s "$U" logcat -c
rl_cap_start "$RUN-hu" "$U"
NTAP=0
utap() { [ "$NTAP" -lt 4 ] || { echo "TAP_BUDGET" >> "$EV/$RUN-taps.log"; return 1; }
  adb -s "$U" shell input tap "$1" "$2"; NTAP=$((NTAP + 1)); echo "tap$NTAP $3 at $1,$2" >> "$EV/$RUN-taps.log"; sleep 0.3; }
umark() { adb -s "$U" shell am broadcast -f 0x00000020 -n "$RX" -a com.andrerinas.openheadunit.ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; }
cycle() { local n=0 xy   # cycle NAME BUTTON: open Settings filtered to the row, open the dialog, press BUTTON
  rl_step "$1"; umark "$1-open"
  adb -s "$U" shell run-as "$PKG" am start --user 0 -n "$SA" --es extra_search_query "$Q" >/dev/null
  until dump "$1-list" "$U" && xy=$(python3 -I "$UI" find "$EV/$1-list.xml" :id/settingName "$T_ROW"); do
    sleep 2; n=$((n + 2)); [ "$n" -ge 20 ] && { echo "$1 ROW_NOT_FOUND" >> "$EV/$RUN-runs.log"; return 1; }; done
  utap $xy row || return 1
  n=0
  until dump "$1-dlg" "$U" && python3 -I "$UI" find "$EV/$1-dlg.xml" android:id/button1 >/dev/null; do
    sleep 2; n=$((n + 2)); [ "$n" -ge 30 ] && { echo "$1 NO_DIALOG" >> "$EV/$RUN-runs.log"; return 1; }; done
  python3 -I "$UI" texts "$EV/$1-dlg.xml" > "$EV/$1-buttons.txt"; shot "$1-dlg" "$U"
  xy=$(python3 -I "$UI" find "$EV/$1-dlg.xml" "android:id/$2") || { echo "$1 NO_$2" >> "$EV/$RUN-runs.log"; return 1; }
  umark "$1-press"; utap $xy "$2" || return 1; sleep 6
  adb -s "$U" shell dumpsys activity activities | grep -a -E 'mResumedActivity|topResumedActivity' | head -2 > "$EV/$1-resumed.txt"   # system
  shot "$1-after" "$U"; umark "$1-done"; }
cycle "$RUN-c1" button1                # Download APK
adb -s "$U" shell input keyevent 3; sleep 2
cycle "$RUN-c2" button3                # Open GitHub Releases
adb -s "$U" shell input keyevent 3; sleep 2
echo "taps=$NTAP" > "$EV/$RUN-taps.txt"
rl_cap_stop; adb -s "$U" shell am force-stop "$PKG"
```

**`grade-r6.sh`**
```bash
untestable_if_hot
ident_conds A "$A_COMMIT" 3.4.0 0
H="$EV/$RUN-hu.txt"; S="$EV/$RUN-strings.txt"; B1="$EV/$RUN-c1-buttons.txt"
sv()  { grep -a "^$1=" "$S" 2>/dev/null | head -1 | cut -d= -f2-; }
bv()  { grep -aF "$1 =" "$B1" 2>/dev/null | head -1 | sed 's/^[^=]*= //'; }
low() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }
FAILED=$(grep -acF 'Update check failed: ' "$H" 2>/dev/null)
if [ "${FAILED:-0}" -ge 1 ]; then rl_verdict INCONCLUSIVE "no update server: $FAILED update check failures; see $RUN-ping.txt"; exit 0; fi
L=$(sv locale); LG=${L%%-*}
if [ -z "$LG" ] || [ "$LG" = en ] || [ -d "$RES/values-$LG" ]; then
  cnd R6-title eq "$(same "$(low "$(bv id/alertTitle)")" "$(low "$(sv title)")")" 1 "dialog title is update_available_title"
  cnd R6-button1 eq "$(same "$(low "$(bv android:id/button1)")" "$(low "$(sv apk)")")" 1 "button1 is download_apk"
  cnd R6-button3 eq "$(same "$(low "$(bv android:id/button3)")" "$(low "$(sv rel)")")" 1 "button3 is open_github_releases"
else
  inc "locale $L has no values-$LG folder: the host compares $RUN-c1-buttons.txt with the locale's own strings.xml"
fi
APK=$(grep -aF 'SettingsFragment: opening update link (' "$H" 2>/dev/null | awk 'index($0, "(apk) ") && $NF ~ /\.apk\r?$/ {n++} END {print n+0}')
PAGE=$(grep -aF 'SettingsFragment: opening update link (' "$H" 2>/dev/null | awk 'index($0, "(release page) ") {n++} END {print n+0}')
NOAPP=$(grep -acF 'Failed to open GitHub releases URL: ' "$H" 2>/dev/null)
cnd R6-apk eq "$APK" 1 "Download APK opened a link that ends in .apk"
cnd R6-page eq "$PAGE" 1 "Open GitHub Releases opened the release page"
if [ "${NOAPP:-0}" -ge 1 ]; then inc "no app takes a link on this unit: 'opens the link' is INCONCLUSIVE"
else
  for c in c1 c2; do
    r="$EV/$RUN-$c-resumed.txt"; ok=0
    [ -s "$r" ] && ! grep -qF "$PKG" "$r" && ok=1
    cnd "R6-$c-open" eq "$ok" 1 "$c: another app took the link"
  done
fi
cnd R6-taps le "$(cut -d= -f2 "$EV/$RUN-taps.txt" 2>/dev/null)" 4 "at most 4 taps"
verdict_line candidate ""
```

- **INCONCLUSIVE (no update server):** `Update check failed: ` printed, or `c1-buttons.txt` shows the
  update-failed dialog. Record the ping result.
- **PASS (each unit):** in `<run>-c1-buttons.txt`, `id/alertTitle` equals `update_available_title`, `button1`
  equals `download_apk` and `button3` equals `open_github_releases` (case does not matter); `apk` = 1;
  `page` = 1; `noapp` = 0; and each `*-resumed.txt` names an app other than `com.andrerinas.headunitrevived`
  (the link opened).
- **FAIL:** `button1` is not `download_apk`, or `button3` is missing, or `apk` = 0 after the c1 press, or the
  c1 link does not end in `.apk`.
- If `noapp` >= 1, the app side passed and "opens the link" is INCONCLUSIVE (no app takes a link on that
  unit). Record what `pre` read for `resolve-activity`.
- If the unit's locale has no `values-<lang>` folder, `ui.py str` falls back to English. Then the host
  compares the button texts with the locale's own `strings.xml` (for example `values-zh-rCN`) before grading.
- Record, do not grade: whether a download started (`<run>-c1-after.png`). The browser's own dialogs are not
  ours, and no tap goes to them.

### cleanup: end of round

D-HU: B goes back on, so the unit does not keep versionName `3.4.0`. Then the backup goes back as root, with
the app's uid:gid and mode 660, and is diffed. `svc power stayon false`. D-SAM, if R6-SAM backed it up: B goes
back on, and `restore_settings.sh` restores the backup, diffed with CRs and blank lines stripped. D-POCO goes
home. The radios go to `rig-lib/baseline.conf` after the round (`rig_requires.py baseline`), D-MOTO's
Bluetooth included.

**`cleanup.sh`**
```bash
rl_step "restore D-HU"
hustop
adb -s "$HU" shell cat "/data/data/$PKG/shared_prefs/settings.xml" > "$EV/settings-end-hu.xml"
adb -s "$HU" install -r -d "$EV/apk/B.apk" > "$EV/install-cleanup-hu.txt" 2>&1
N=$(grep -ac '</map>' "$EV/settings-backup-hu.xml" 2>/dev/null)
if [ "${N:-0}" -ge 1 ]; then
  F=/data/data/$PKG/shared_prefs/settings.xml
  OWN=$(adb -s "$HU" shell stat -c %u:%g "/data/data/$PKG" | tr -d '\r')
  adb -s "$HU" push "$EV/settings-backup-hu.xml" /data/local/tmp/ar1-restore.xml >/dev/null
  adb -s "$HU" shell cp /data/local/tmp/ar1-restore.xml "$F"
  adb -s "$HU" shell chown "$OWN" "$F"
  adb -s "$HU" shell chmod 660 "$F"
  adb -s "$HU" shell cat "$F" > "$EV/settings-after-hu.xml"
  diff "$EV/settings-backup-hu.xml" "$EV/settings-after-hu.xml" > "$EV/settings-diff-hu.txt"
fi
adb -s "$HU" shell svc power stayon false
if [ -s "$EV/settings-backup-sam.xml" ]; then
  rl_step "restore D-SAM"
  adb -s "$SAM" shell am force-stop "$PKG"
  adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-end-sam.xml"
  adb -s "$SAM" install -r -d "$EV/apk/B.apk" > "$EV/install-cleanup-sam.txt" 2>&1
  adb -s "$SAM" push "$EV/settings-backup-sam.xml" /data/local/tmp/settings-restore.xml >/dev/null
  adb -s "$SAM" push "$RIG_TOOLKIT/restore_settings.sh" /data/local/tmp/restore_settings.sh >/dev/null
  adb -s "$SAM" shell run-as "$PKG" sh /data/local/tmp/restore_settings.sh
  adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-after-sam.xml"
  diff <(tr -d '\r' < "$EV/settings-backup-sam.xml" | grep -v '^$') <(tr -d '\r' < "$EV/settings-after-sam.xml" | grep -v '^$') \
    > "$EV/settings-diff-sam.txt"
fi
adb -s "$PH" shell input keyevent KEYCODE_HOME
```

**`grade-cleanup.sh`**
```bash
cnd restore-hu eq "$(wc -c < "$EV/settings-diff-hu.txt" 2>/dev/null)" 0 "D-HU settings.xml back to the backup"
if [ -s "$EV/settings-backup-sam.xml" ]; then
  cnd restore-sam eq "$(wc -c < "$EV/settings-diff-sam.txt" 2>/dev/null)" 0 "D-SAM settings.xml back to the backup"
fi
```

## 8. Do not re-run

- Nothing. This is the thread's first round.
- The pinch then cancel and the assistant close are not runs this round (section 3). Do not add them.

## 9. Report back

1. R1: one row per cycle, both arms: `tm_down`, `tm_cancel`, `tm_else`, `tm_btn`, and the host's
   reading of shots `b`, `c`, `d` and `e`. Then the answer to the open question: on T, did the phone take
   the off-canvas MOVE and UP (no held highlight in `c`), or keep the press (highlight held)?
2. R3 and R4: the clock reading of the four sessions, and the focus reading of `s1` and `s3` on R4-B and R4-U,
   with `right` and `left`.
3. R5 and R6: the R5 lines of each session (`crit` above all), and per R6 unit the `buttons.txt` lines, `apk`,
   `page`, `noapp` and the resumed app.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
[UI_DEBUG] Touch map: raw=
CarKeyReceiver: Handling intent action: 
CommManager: TX Key -> AA=
NativeAA: ACTIVELY LISTENING on Android Auto UUID
Service Discovery Request: 
Media Sink Setup Request: 
Throughput over
SSL handshake complete
MATCH! Starting AapService
SettingsFragment: opening update link (
Update check failed: 
Failed to open GitHub releases URL: 
Critical error
GhFacetBar.onWindowSurfaceAvailable width:
```
