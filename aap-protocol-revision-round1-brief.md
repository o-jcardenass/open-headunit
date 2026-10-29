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

Build all four in one worktree, in this order. B and A\* come first, back to back. All four share the base
`947b3ae9`, so the cache stays warm.

**get_apks.sh**

```bash
OUT=rig-data/rounds/aap-protocol-revision-round1; mkdir -p $OUT
git fetch fork fix/touch-cancel-release chore/aap-unread-fields && git fetch origin main
WT=$($RIG_TOOLKIT/wt-new.sh aap-protocol-revision scratch 947b3ae9fae9834aa8e4145ee91751ed743c2ae0 | tail -1)
co() { git -C $WT checkout --detach $1 && [ "$(git -C $WT rev-parse 'HEAD^{tree}')" = "$2" ] || { echo TREE_MISMATCH $1; return 1; }; }
$RIG_TOOLKIT/rig_thermal.sh wait 70
co 947b3ae9fae9834aa8e4145ee91751ed743c2ae0 e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk-B.apk at once
sed -i 's/versionName = "4.0.0-alpha"/versionName = "3.4.0"/' $WT/app/build.gradle.kts
grep -c 'versionName = "3.4.0"' $WT/app/build.gradle.kts    # MUST print 1
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy to $OUT/apk-A.apk at once
git -C $WT checkout -- app/build.gradle.kts
git -C $WT status --porcelain                               # MUST print nothing
co a9988ed85aaeb47cf7bca97e93df82a61c0c8b75 320d394de5a0bff50d1a128b7f34b0b8f953dbec
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy to $OUT/apk-T.apk at once
co 97d1ee0656a35000b55f446085dad8fc9ee9e1b7 9c2ff5daf8cb9377662983792fc757a2e1f5fcd8
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy to $OUT/apk-U.apk at once
co 947b3ae9fae9834aa8e4145ee91751ed743c2ae0 e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165                                                 # leave $WT on main for R6
md5sum $OUT/apk-B.apk $OUT/apk-T.apk $OUT/apk-U.apk $OUT/apk-A.apk   # all four MUST differ
```

Keep `$WT` checked out at `947b3ae9` after the builds (the last `co` does it): R6 reads the locale strings from it.

Identity, after every install (`inst` in section 5 does it):

| Arm | `ACTION_QUERY_STATE` `commit` | `versionName` | DEX `TouchReleasePolicy` | DEX `UpdateLinkPolicy$Choice` |
|---|---|---|---|---|
| B | `947b3ae9fae9` | `4.0.0-alpha` | 0 | at least 1 |
| T | `a9988ed85aae` | `4.0.0-alpha` | at least 1 | at least 1 |
| U | `97d1ee0656a3` | `4.0.0-alpha` | 0 | at least 1 |
| A\* | `947b3ae9fae9-dirty` | `3.4.0` | 0 | at least 1 |

The `UpdateLinkPolicy$Choice` column no longer tells the arms apart. `commit` and `versionName` do, and B and
A\* also differ in md5.

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
- **R2, the real edge-back gesture, needs gesture navigation.** Read `settings get secure navigation_mode` in
  pre-flight. If it is not `2`, R2 is UNTESTABLE (no gesture navigation). Do not change the navigation mode.
- **The clock and the rotary focus are graded from screencaps too.** The host reads them.
- **`Service Discovery Response` prints only at VERBOSE** (`AapTransport` logs each message with `AppLog.v`).
  S-U1 and S-U2 run at INFO, so the session lines are `Service Discovery Request: `,
  `Media Sink Setup Request: ` and `Throughput over`.
- **A VERBOSE capture carries two `SSL handshake complete` lines per session** (the DEBUG form joins the INFO
  form). Count sessions by `Service Discovery Request: `, never by the SSL line.
- **R6 needs the internet.** `UpdateChecker` asks `api.github.com`. If a unit has no route, the app logs
  `Update check failed: ` and R6 on that unit is INCONCLUSIVE (no update server).
- **`SettingsActivity` is not exported.** Start it with `run-as` on both D-HU and D-SAM.
- Read `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-POCO.md`,
  `rig-quirks/units/D-SAM.md`, `rig-quirks/units/D-SAM-and-D-HP.md` and `rig-quirks/topics/projection.md`
  first. The thermal limits are the table in `tooling.md`.

## 4. Settings keys

Back up `settings.xml` on D-HU and D-SAM at the start (`$OUT/settings-backup-hu.xml`,
`$OUT/settings-backup-sam.xml`) and diff each against the end. State the delta in Setup notes. Leave the
audio keys as they are: they are a deliberate worst case. No run grades audio.

Record, do not change: `key-codes`, `enable-rotary`, `video-profile-starvation-cap`, `fullscreen-mode`,
`resolutionId`, `native-driver-selection-mode`, `native-poke-bt-macs`, `native-poke-all-paired`.
If `key-codes` holds any entry, the host reads it before R4 and says whether keycode 1, 2 or 4 is
remapped. A remapped key makes R4 INCONCLUSIVE.

**D-HU, per session** (`base LOG CLOCK` in section 5 writes them):

| Session | `wifi-connection-mode` | `log-level` | `onboarding-version` | `hide-clock` |
|---|---|---|---|---|
| S-B, S-T | `<int ... value="3" />` | `<int ... value="0" />` | `<int ... value="2" />` | `<boolean ... value="true" />` |
| S-U1 | 3 | 2 | 2 | true |
| S-U2 | 3 | 2 | 2 | **false** |
| R6-HU | 3 | 2 | 2 | (as left) |

**D-SAM** (R6-SAM): `<int name="log-level" value="2" />` and `<int name="onboarding-version" value="2" />`.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Write the results file after every run.

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
CK=$PKG/com.andrerinas.openheadunit.connection.carkey.CarKeyBroadcastReceiver
SA=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
HU=27870808938846; PH=4f4027e9; SAM=30041c35642d2200; MOTO=ZY22GC3BM4
OUT=rig-data/rounds/aap-protocol-revision-round1; mkdir -p $OUT
export LC_ALL=C
send()  { a=$1; shift; adb -s $HU  shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
sends() { a=$1; shift; adb -s $SAM shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
pnow() { adb -s $PH shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
step() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; echo "$1 $(pnow)" >> $OUT/phone-marks.txt; }
launch() { adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; }
huexit() { adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5
  adb -s $HU shell am force-stop $PKG; sleep 1; }        # only while the app runs
hustop() { adb -s $HU shell am force-stop $PKG; sleep 1; }

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$RUN.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# the phone on and off the link (svc, never airplane mode)
ph_rd()  { adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; }
ph_off() { adb -s $PH shell svc wifi disable; sleep 0.3; adb -s $PH shell svc bluetooth disable; sleep 4; ph_rd; }
ph_up()  { adb -s $PH shell svc wifi enable; sleep 0.3; adb -s $PH shell svc bluetooth enable; sleep 4; ph_rd; }

# input into the projected video, and keys through the car-key receiver
mev()   { adb -s $HU shell input touchscreen motionevent $1 $2 $3; sleep 0.3; }   # DOWN|CANCEL x y
tap()   { adb -s $HU shell input tap $1 $2; sleep 0.3; }
key()   { adb -s $HU shell am broadcast -f 0x00000020 -n $CK -a com.microntek.irkeyDown --ei keyCode $1 >/dev/null; sleep 0.3
          adb -s $HU shell am broadcast -f 0x00000020 -n $CK -a com.microntek.irkeyUp --ei keyCode $1 >/dev/null; sleep 0.3; }
shot()  { local s=${2:-$HU}; adb -s $s shell screencap -p /sdcard/ar1.png; sleep 0.3
          adb -s $s pull /sdcard/ar1.png $OUT/$1.png >/dev/null; sleep 0.3; }
dump()  { local s=${2:-$HU}; adb -s $s shell uiautomator dump /sdcard/ar1.xml >/dev/null; sleep 0.3
          adb -s $s pull /sdcard/ar1.xml $OUT/$1.xml >/dev/null; sleep 0.3; }

# captures: started before the launch
cap_on() { adb -s $HU logcat -c; adb -s $HU logcat -G 16M; stdbuf -oL adb -s $HU logcat -v threadtime > $OUT/$1-hu.txt & HUPID=$!
  PHPID=; if [ "$2" = phone ]; then stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash,events > $OUT/$1-phone.txt & PHPID=$!; fi; sleep 2; }
cap_off() { sleep 2; kill $HUPID $PHPID 2>/dev/null; wait $HUPID $PHPID 2>/dev/null; }
waitfor() { local n=0; while [ $n -lt $3 ]; do grep -aqF -- "$2" "$1" && { echo $n; return 0; }; sleep 5; n=$((n+5)); done; echo TIMEOUT; return 1; }

# counts: head unit between two markers of one capture; phone between two marks on the phone's clock
win() { awk -v a="AutomationMarker: $2" -v b="AutomationMarker: $3" -v s="$4" \
  'index($0,a){f=1;next} f&&index($0,b){exit} f&&index($0,s){n++} END{print n+0}' $OUT/$1-hu.txt; }   # win CAP FROM TO STRING
pt()   { grep -aF "$1 " $OUT/phone-marks.txt | tail -1 | cut -d' ' -f2-; }
pwin() { awk -v a="$(pt "$2")" -v b="$(pt "$3")" 'substr($0,1,18)>=a && substr($0,1,18)<=b' $OUT/$1-phone.txt; }
pc()   { pwin "$1" "$2" "$3" | grep -acF -- "$4"; }                                           # pc CAP FROM TO STRING

# settings on D-HU, app stopped. Args: "set TYPE KEY VALUE" or "del x KEY". adbd is root on D-HU.
hu_kv() {
  local IFS=' '
  { echo 'F=/data/data/com.andrerinas.headunitrevived/shared_prefs/settings.xml'
    for kv in "$@"; do set -- $kv
      echo "sed -i -E 's#<[a-z]+ name=\"$3\"[^>]*/>##g' \$F"
      echo "sed -i -E 's#<string name=\"$3\">[^<]*</string>##g' \$F"
      if [ "$1" = set ]; then
        if [ "$2" = string ]; then echo "sed -i 's|</map>|<string name=\"$3\">$4</string></map>|' \$F"
        else echo "sed -i 's|</map>|<$2 name=\"$3\" value=\"$4\" /></map>|' \$F"; fi
      fi
    done
    echo 'chown $(stat -c %u:%g /data/data/com.andrerinas.headunitrevived) $F; chmod 660 $F'
  } > $OUT/ar_kv.sh
  adb -s $HU push $OUT/ar_kv.sh /data/local/tmp/ar_kv.sh >/dev/null
  adb -s $HU shell "sh /data/local/tmp/ar_kv.sh"
}
base() { hu_kv "set int wifi-connection-mode 3" "set int log-level $1" "set int onboarding-version 2" "set boolean hide-clock $2"; }
hu_read() { adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-$1.xml
  grep -aoE '(wifi-connection-mode|log-level|onboarding-version|hide-clock|enable-rotary)"[^/]*' $OUT/settings-$1.xml; }

# install one arm on D-HU and check its identity (the phone MUST be off the link first)
apk_check() { adb -s $1 pull $(adb -s $1 shell pm path $PKG | cut -d: -f2 | tr -d '\r') $OUT/live.apk >/dev/null 2>&1
  local m=$(md5sum $OUT/live.apk | cut -d' ' -f1); echo "$(date +%T) $1 live=$m want=$WANT_MD5" | tee -a $OUT/apk-check.log
  [ "$m" = "$WANT_MD5" ] || { echo APK_MISMATCH | tee -a $OUT/apk-check.log; return 1; }
  for c in TouchReleasePolicy 'UpdateLinkPolicy$Choice'; do echo "dex $c=$(unzip -p $OUT/live.apk 'classes*.dex' | strings | grep -cF "$c")"; done | tee -a $OUT/apk-check.log; }
inst() { hustop; adb -s $HU install -r -d $OUT/apk-$1.apk; WANT_MD5=$(md5sum $OUT/apk-$1.apk | cut -d' ' -f1)
  apk_check $HU || return 1; launch; sleep 5; send ACTION_QUERY_STATE | tee $OUT/qs-$1.txt; huexit; }
```

**ui.py** (save beside `lib.sh`; run with `python3 -I`)

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

**sess_up.sh** (set `ID`, `ARM`, `LOG`, `CLOCK`; for example `ID=S-B ARM=B LOG=0 CLOCK=true`)

```bash
RUN=$ID th_gate || exit 1
ph_off                                 # MUST read Wi-Fi is disabled, state: OFF
WANT_MD5=$(md5sum $OUT/apk-$ARM.apk | cut -d' ' -f1); apk_check $HU || exit 1
hustop; base $LOG $CLOCK; hu_read $ID-pre
cap_on $ID phone
launch; sleep 1; step $ID-start
waitfor $OUT/$ID-hu.txt "NativeAA: ACTIVELY LISTENING on Android Auto UUID" 30
sleep 10
ph_up; step $ID-phone                  # MUST read Wi-Fi is enabled, state: ON
waitfor $OUT/$ID-hu.txt "Service Discovery Request: " 120 || echo "$ID NO_SESSION" | tee -a $OUT/runs.log
waitfor $OUT/$ID-hu.txt "Throughput over" 40
send ACTION_RAISE_PROJECTION >/dev/null; sleep 3
adb -s $HU shell dumpsys window | grep -a mCurrentFocus | tee $OUT/$ID-focus.txt   # MUST name AapProjectionActivity
step $ID-up; sleep 5; shot $ID-clock
```

If the session line times out, run `sess_end.sh` and re-run the session once. A second miss makes every run
in that session UNTESTABLE (no session). If `mCurrentFocus` does not name `AapProjectionActivity`, send
`ACTION_RAISE_PROJECTION` once more and read it again. A second miss voids the touch, clock and scroll runs
of that session.

**sess_end.sh** (set `ID`)

```bash
sleep_to=$(( 60 - $(( $(date +%s) - $(stat -c %Y $OUT/$ID-focus.txt) )) )); [ $sleep_to -gt 0 ] && sleep $sleep_to
step $ID-end; cap_off; huexit; ph_off; th_end
```

The first line holds each session at least 60 s after it came up, so R5 has 60 s of `Throughput over` lines.

## 6. The deciding lines

Each head unit string was checked with `git grep -F` on `app/src` at every SHA the table names. All print at
INFO or WARN except `Touch map`, which needs `log-level` 0.

**Head unit capture** (`$OUT/<ID>-hu.txt`, the window named in each run):

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

**Phone capture** (`$OUT/<ID>-phone.txt`, from `<ID>-phone` to `<ID>-end`). Record the Gearhead version
(`adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -m1 versionName`).

| Grep | Meaning |
|---|---|
| `Critical error` | Android Auto ended a session on a protocol fault (MUST be 0) |
| `GhFacetBar.onWindowSurfaceAvailable width:` | the bar's size; record the first line of each session |

**System reads**: `mCurrentFocus` (`dumpsys window`), `mResumedActivity` (`dumpsys activity activities`),
`navigation_mode` (`settings get secure`). The screencaps are read by the host.

## 7. Runs

### Pre-flight (read only)

1. `sed -n '/^## Pick a tool/,/^## Rig and devices/p' $RIG_TOOLKIT/TOOLS.md`. Record which tools you used.
2. `$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`.
3. D-MOTO: `adb -s $MOTO shell svc bluetooth disable`, for the whole round. If D-MOTO is not plugged in, say so.
4. D-HU: `adb -s $HU shell input 2>&1 | grep -a motionevent` MUST list `CANCEL`. Else R1 and R2 are
   UNTESTABLE (no injected CANCEL).
5. D-HU: `adb -s $HU shell settings get secure navigation_mode`. Record it. It decides R2.
6. D-HU: `adb -s $HU shell wm size`, and `adb -s $HU shell dumpsys wifi | grep -iE "mWifiInfo" | head -2`. Record
   both. R6-HU needs the station joined to a network with internet.
7. D-HU: `adb -s $HU shell cmd package resolve-activity --brief -a android.intent.action.VIEW -d https://github.com`.
   Record which app takes a link.
8. D-HU: `adb -s $HU shell svc power stayon true`.
9. D-POCO: `adb -s $PH shell dumpsys wifip2p | grep isGroupOwner` MUST be false (else `svc wifi disable`,
   `svc wifi enable`). `adb -s $PH shell dumpsys window | grep mCurrentFocus`: at home (else
   `input keyevent KEYCODE_HOME`). Record the Gearhead version.
10. D-SAM: `adb -s $SAM shell ping -c 1 -W 3 api.github.com`. Record the result. `send` on D-SAM is `sends`
    (no `-p`, quirk in `D-SAM.md`).
11. `hustop`. Back up D-HU's `settings.xml` to `$OUT/settings-backup-hu.xml` and D-SAM's to
    `$OUT/settings-backup-sam.xml`. Record the keys section 4 lists.

### Block B

`ph_off`; `inst B`. `qs-B.txt` MUST carry commit `947b3ae9fae9`. Read the base keys back: an install can
wipe `settings.xml`. Then `ID=S-B ARM=B LOG=0 CLOCK=true sess_up.sh`, R1 (B), R4 (B), `sess_end.sh`.

### Block T

`ph_off`; `inst T` (commit `a9988ed85aae`). `ID=S-T ARM=T LOG=0 CLOCK=true sess_up.sh`, R1 (T), R2 (T),
`sess_end.sh`.

### Block U

`ph_off`; `inst U` (commit `97d1ee0656a3`). `ID=S-U1 ARM=U LOG=2 CLOCK=true sess_up.sh`, R4 (U),
`sess_end.sh`. Then `ID=S-U2 ARM=U LOG=2 CLOCK=false sess_up.sh`, `sess_end.sh`. R3 and R5 grade from all
four sessions.

### R1. A touch CANCEL over a control leaves no press held (THE POINT)

Sessions S-B and S-T. Phone capture: yes (the session's). Two cycles per arm: `C=R1-B-c1`, `C=R1-B-c2`,
`C=R1-T-c1`, `C=R1-T-c2`. A void cycle is re-run once. Stop after two graded cycles per arm, or after one
extra cycle.

**r1_cycle.sh** (set `C` and `S`, the session ID, for example `C=R1-B-c1 S=S-B`)

```bash
BX=60; BY=660; MX=640; MY=300          # the bar's left end, and the map
step $C-a; shot $C-a                   # idle
step $C-down;   mev DOWN $BX $BY;     sleep 1.5; shot $C-b   # press held on purpose
step $C-cancel; mev CANCEL $BX $BY; sleep 2;   shot $C-c   # after the CANCEL
step $C-else;   tap $MX $MY;          sleep 2;   shot $C-d   # a tap elsewhere
step $C-btn;    tap $BX $BY;          sleep 3;   shot $C-e   # positive control: the point is a control
step $C-back;   key 4;                sleep 3;   shot $C-f   # BACK to the phone
step $C-done
```

**r1_grade.sh** (set `C` and `S`)

```bash
echo "$C tm_down=$(win $S $C-down $C-cancel '[UI_DEBUG] Touch map: raw=') tm_cancel=$(win $S $C-cancel $C-else '[UI_DEBUG] Touch map: raw=') tm_else=$(win $S $C-else $C-btn '[UI_DEBUG] Touch map: raw=') tm_btn=$(win $S $C-btn $C-back '[UI_DEBUG] Touch map: raw=') sdr=$(win $S $C-a $C-done 'Service Discovery Request: ') marks=$(win $S $C-a $C-done 'AutomationMarker: ')"
```

The host then reads shots `a` to `f` of the cycle and writes one line for each: `b` held highlight on the
target (yes/no), `c` held highlight (yes/no) and new screen (yes/no), `d` held highlight and new screen, `e`
new screen opened (yes/no and which), `f` back at the start screen (yes/no).

- **Void cycle:** `marks` below 5, or `sdr` >= 1 (a new session), or `tm_down` = 0 (the DOWN never reached
  the app).
- **INCONCLUSIVE cycle:** `e` opened nothing (the point is not a control), or `b` shows no held highlight (a
  held press is not visible on this layout).
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

### R2. A real edge-back gesture over the bar clicks nothing (T only)

Session S-T, after R1. Run it only if pre-flight step 5 read `2`. Else mark it UNTESTABLE (no gesture
navigation) and go on.

**r2_edge.sh**

```bash
C=R2-T; step $C-a; shot $C-a
step $C-swipe; adb -s $HU shell input swipe 2 660 400 660 300; sleep 3; shot $C-b
step $C-done
echo "$C tm=$(win S-T $C-swipe $C-done '[UI_DEBUG] Touch map: raw=') sdr=$(win S-T $C-a $C-done 'Service Discovery Request: ')"
```

The host reads `a` and `b`. **PASS:** `b` shows no held highlight on the bar and no screen the bar opens,
and `sdr` = 0. **FAIL:** `b` shows a held highlight or a screen opened from the bar. Record `tm`: a value
near 2 means the system took the swipe, and a large value means the app received the whole swipe.

### R3. The hidden clock stays hidden (U), with a control

Shots `S-B-clock`, `S-T-clock`, `S-U1-clock`, `S-U2-clock`, taken 5 s after each session came up. The host
reads where Android Auto draws its clock (on the bar) and writes clock shown (yes/no) for each.

- **PASS:** `S-U1-clock` shows no clock (`hide-clock` true), and `S-U2-clock` shows the clock (`hide-clock`
  false).
- **FAIL:** `S-U1-clock` shows the clock while `S-B-clock` does not. Then the phone read field 12 after all.
- **INCONCLUSIVE:** `S-B-clock` shows the clock too. Then `hide-clock` does nothing on this Gearhead, on
  either build.

### R4. A rotary step still moves the focus (U), with B as the reference

Sessions S-B (after R1) and S-U1. `C=R4-B` in S-B, `C=R4-U` in S-U1. Keycode 2 is `KEYCODE_SOFT_RIGHT`, which
`AapTransport.send` turns into a `ScrollWheelEvent` with delta +1. Keycode 1 is the step back.

**r4_scroll.sh** (set `C` and `S`)

```bash
step $C-a; shot $C-s0
key 2; sleep 1.5; shot $C-s1
key 2; sleep 1.5; shot $C-s2
key 2; sleep 1.5; shot $C-s3
key 1; key 1; key 1; sleep 1.5; shot $C-s4
step $C-done; key 4; sleep 2
echo "$C rx=$(win $S $C-a $C-done 'CarKeyReceiver: Handling intent action: ') right=$(win $S $C-a $C-done 'CommManager: TX Key -> AA=2 (isPress=true)') left=$(win $S $C-a $C-done 'CommManager: TX Key -> AA=1 (isPress=true)')"
```

The host reads `s0` to `s4` and writes, for each, whether a focus highlight shows and on which control.

- **Void:** `rx` below 12 (a broadcast did not land).
- **PASS:** on R4-U, `right` >= 3, `left` >= 3, a focus highlight appears by `s1` and is on a different control
  in `s3` than in `s1`, and R4-B shows the same.
- **FAIL:** R4-B moves the focus and R4-U does not, with `right` >= 3 on both.
- **INCONCLUSIVE:** R4-B moves no focus either. Then the rotary input does nothing on this phone, on either
  build. Record `enable-rotary`.

### R5. A session still forms and renders (U), with B and T beside it

Grades from each session's full capture, start to `<ID>-end`.

**r5_grade.sh** (set `ID`)

```bash
H=$OUT/$ID-hu.txt
echo "$ID sdr=$(grep -acF 'Service Discovery Request: ' $H) vsink=$(grep -aF 'Media Sink Setup Request: ' $H | grep -acF 'on channel VIDEO') thr=$(grep -acF 'Throughput over' $H) rmax=$(grep -ao 'rendered=[0-9]*' $H | cut -d= -f2 | sort -n | tail -1) wake=$(grep -acF 'MATCH! Starting AapService' $H) crit=$(pc $ID $ID-phone $ID-end 'Critical error') bar=[$(pwin $ID $ID-phone $ID-end | grep -aF -m1 'GhFacetBar.onWindowSurfaceAvailable width:' | grep -ao 'width: .*')]"
```

- **PASS (S-U1 and S-U2):** `sdr` = 1, `vsink` >= 1, `thr` >= 10, `rmax` > 0, and `crit` = 0.
- **FAIL:** any of those missed on a U session while S-B meets it.
- Record the same line for S-B and S-T. A T session that misses one of them is a FAIL of T.

### Block A: install A\* on D-HU, then R6-HU, then D-SAM and R6-SAM

`ph_off` (no session during R6). `inst A`: `qs-A.txt` MUST carry commit `947b3ae9fae9-dirty` and versionName
`3.4.0`. Then `hustop`, `base 2 true`, and R6-HU.

D-SAM: back up its `settings.xml`, then
`python3 -I $RIG_TOOLKIT/set_prefs_runas_host.py $SAM scalar:int:log-level:2 scalar:int:onboarding-version:2`,
and read both keys back. `adb -s $SAM shell am force-stop $PKG`; `adb -s $SAM install -r -d $OUT/apk-A.apk`;
`WANT_MD5=$(md5sum $OUT/apk-A.apk | cut -d' ' -f1)`; `apk_check $SAM`;
`adb -s $SAM shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`; `sleep 5`;
`sends ACTION_QUERY_STATE` (commit `947b3ae9fae9-dirty`). Then R6-SAM.

### R6. "Check for updates" offers "Download APK" and opens it (A\*)

Two units: `U=$HU ID=R6-HU` and `U=$SAM ID=R6-SAM`. Phone capture: no. At most 4 taps on the app, all on
dumped targets, enforced by the script.

**r6_run.sh** (set `U` and `ID`)

```bash
RUN=$ID th_gate || exit 1
L=$(adb -s $U shell getprop persist.sys.locale | tr -d '\r'); [ -z "$L" ] && L=$(adb -s $U shell getprop persist.sys.language | tr -d '\r')
LG=${L%%-*}; RES=$WT/app/src/main/res
T_ROW=$(python3 -I ui.py str $RES $LG check_for_updates); T_APK=$(python3 -I ui.py str $RES $LG download_apk)
T_REL=$(python3 -I ui.py str $RES $LG open_github_releases); T_TTL=$(python3 -I ui.py str $RES $LG update_available_title)
Q=${T_ROW##* }                         # one word of the row's title, so the search needs no quoting
echo "locale=$L row=[$T_ROW] apk=[$T_APK] rel=[$T_REL] title=[$T_TTL] q=$Q" | tee $OUT/$ID-strings.txt
adb -s $U shell am force-stop $PKG; sleep 1
adb -s $U logcat -c; stdbuf -oL adb -s $U logcat -v threadtime > $OUT/$ID-hu.txt & LPID=$!; sleep 2
NTAP=0
utap() { [ $NTAP -lt 4 ] || { echo "$ID TAP_BUDGET" | tee -a $OUT/$ID-taps.log; return 1; }
  adb -s $U shell input tap $1 $2; NTAP=$((NTAP+1)); echo "$ID tap$NTAP $3 at $1,$2" | tee -a $OUT/$ID-taps.log; sleep 0.3; }
mark() { adb -s $U shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; }
cycle() {   # cycle NAME BUTTON: open Settings filtered to the row, open the dialog, press BUTTON
  mark $1-open
  adb -s $U shell run-as $PKG am start --user 0 -n $SA --es extra_search_query $Q >/dev/null; sleep 5
  dump $1-list $U; xy=$(python3 -I ui.py find $OUT/$1-list.xml :id/settingName "$T_ROW") || { echo "$1 ROW_NOT_FOUND $xy" | tee -a $OUT/runs.log; return 1; }
  utap $xy row || return 1
  local n=0; until dump $1-dlg $U && python3 -I ui.py find $OUT/$1-dlg.xml android:id/button1 >/dev/null; do
    sleep 2; n=$((n+2)); [ $n -ge 30 ] && { echo "$1 NO_DIALOG" | tee -a $OUT/runs.log; return 1; }; done
  python3 -I ui.py texts $OUT/$1-dlg.xml | tee $OUT/$1-buttons.txt; shot $1-dlg $U
  xy=$(python3 -I ui.py find $OUT/$1-dlg.xml android:id/$2) || { echo "$1 NO_$2" | tee -a $OUT/runs.log; return 1; }
  mark $1-press; utap $xy $2 || return 1; sleep 6
  adb -s $U shell dumpsys activity activities | grep -a mResumedActivity | head -2 | tee $OUT/$1-resumed.txt
  shot $1-after $U; mark $1-done
}
cycle $ID-c1 button1                   # Download APK
adb -s $U shell input keyevent 3; sleep 2
cycle $ID-c2 button3                   # Open GitHub Releases
adb -s $U shell input keyevent 3; sleep 2
sleep 2; kill $LPID; wait $LPID 2>/dev/null; adb -s $U shell am force-stop $PKG; th_end
H=$OUT/$ID-hu.txt
echo "$ID apk=$(grep -aF 'SettingsFragment: opening update link (apk) ' $H | grep -acF '.apk') page=$(grep -acF 'SettingsFragment: opening update link (release page) ' $H) failed=$(grep -acF 'Update check failed: ' $H) noapp=$(grep -acF 'Failed to open GitHub releases URL: ' $H) taps=$NTAP"
```

Run `r6_run.sh` from the folder that holds `ui.py`, with `lib.sh` sourced and `$WT` set.

- **INCONCLUSIVE (no update server):** `failed` >= 1, or `c1-buttons.txt` shows the update-failed dialog.
  Record the ping result from pre-flight.
- **PASS (each unit):** in `$ID-c1-buttons.txt`, `id/alertTitle` equals `T_TTL`, `button1` equals `T_APK` and
  `button3` equals `T_REL` (case does not matter); `apk` = 1; `page` = 1; `noapp` = 0; and each
  `*-resumed.txt` names an app other than `com.andrerinas.headunitrevived` (the link opened).
- **FAIL:** `button1` is not `T_APK`, or `button3` is missing, or `apk` = 0 after the c1 press, or the c1 link
  does not end in `.apk`.
- If `noapp` >= 1, the app side passed and "opens the link" is INCONCLUSIVE (no app takes a link on that
  unit). Record what pre-flight step 7 read.
- If the unit's locale has no `values-<lang>` folder, `ui.py str` falls back to English. Then the host
  compares the button texts with the locale's own `strings.xml` (for example `values-zh-rCN`) before grading.
- Record, do not grade: whether a download started (`$ID-c1-after.png`). The browser's own dialogs are not
  ours, and no tap goes to them.

### End of round

1. D-HU: `hustop`; `adb -s $HU install -r -d $OUT/apk-B.apk`, so the unit does not keep versionName `3.4.0`.
   Restore `$OUT/settings-backup-hu.xml` as root, `chown` it to the app's uid:gid, `chmod 660`, and diff it:
   MUST be empty. `svc power stayon false`.
2. D-SAM: record which build it had before the round (pre-flight `sends ACTION_QUERY_STATE` if the app was
   installed). Install `apk-B.apk` with `-r -d`. Restore `$OUT/settings-backup-sam.xml` with
   `$RIG_TOOLKIT/restore_settings.sh` and diff it with CRs stripped: MUST be empty.
3. D-POCO: `ph_up`, `isGroupOwner` false, `input keyevent KEYCODE_HOME`.
4. D-MOTO: `svc bluetooth enable`.

## 8. Do not re-run

- Nothing. This is the thread's first round.
- The pinch then cancel and the assistant close are not runs this round (section 3). Do not add them.

## 9. Report back

1. R1: one row per cycle, both arms: `tm_down`, `tm_cancel`, `tm_else`, `tm_btn`, `sdr`, and the host's
   reading of shots `b`, `c`, `d` and `e`. Then the answer to the open question: on T, did the phone take
   the off-canvas MOVE and UP (no held highlight in `c`), or keep the press (highlight held)?
2. R3 and R4: the clock reading of the four sessions, and the focus reading of `s1` and `s3` on R4-B and R4-U,
   with `right` and `left`.
3. R5 and R6: the R5 line of each session (`crit` above all), and per R6 unit the `buttons.txt` lines, `apk`,
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
