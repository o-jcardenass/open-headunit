# dsam-surfaceview-crop, round 1 brief: does SurfaceView crop the projection on D-SAM on main

Published name: `dsam-surfaceview-crop-round1-brief.md`. Results go in `dsam-surfaceview-crop-round1-results.md`.
Evidence: create the release `rig-evidence-dsam-surfaceview-crop` with the asset `dsam-surfaceview-crop-round1-captures.zip`.

**One point.** In `pr-train-20261029` round 2, D-SAM showed the projection in the top ~740 px of its 800x1280 screen,
the rest black, while the view mode was SurfaceView. After a switch to TextureView the operator did not see it again.
This round asks whether `main` does the same, by measuring the lit part of the screen in three sessions per view mode.

Units: D-SAM (head unit, API 19) with D-POCO (phone). D-HU Bluetooth held off, D-MOTO radios off.
Time: setup 5 min, TX 12, S1 12, cleanup 5, plus thermal waits. **About 35 min.**

```rig-requires
lib 0f6b5a7a1
unit D-SAM role=headunit wifi=on bt=any battery=50
unit D-POCO role=phone wifi=on bt=on nobond=D-HU aa=D-SAM battery=30
unit D-HU bt=off
unit D-MOTO wifi=off bt=off
candidate M 6ff42d9a
hardware D-SAM charges from its own supply (its USB port does not charge it)
run setup cap=420 script=setup.sh grade=grade-setup.sh
run TX cap=1200 script=tx.sh grade=grade-tx.sh
run S1 cap=1200 script=s1.sh grade=grade-s1.sh point=yes
run cleanup cap=600 script=cleanup.sh grade=grade-cleanup.sh always=yes
stop setup FAIL,INCONCLUSIVE,UNTESTABLE skip=all
stop TX UNTESTABLE skip=S1
```

`bt=any` on D-SAM: API 19 prints no `state:` line, so a read-back reads `unknown`.

| Run | View mode | What it proves | Min |
|---|---|---|---|
| setup | | M on D-SAM, D-SAM keys written, settings backed up | 5 |
| TX | TextureView (`view-mode` 1) | control: how much of the screen a full projection lights on this unit | 12 |
| **S1** | SurfaceView (`view-mode` 0) | **the point: whether SurfaceView lights less of the screen than TextureView on `main`** | 12 |
| cleanup | | D-SAM's network given back, settings restored | 5 |

Each of TX and S1 runs three sessions in the order of `pr-train-20261029` round 2: s1 a fresh launch, s2 a relaunch
after a force-stop with the session up (the case where the crop was seen), s3 a relaunch after a clean exit.

## 1. Build and baseline

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| M | `main` | `6ff42d9a06a3a0a00d2f50c67bc7d9b73c885572` | `6b65d9c694802fdc1fb984a2267a5a1d22f72722` |

`origin/main` is now `c2c0e95a`, which changes one unit test only, so the APK is the same as at `6ff42d9a`. This is
also the B arm of `pr-train-20261030` round 1: if that APK is at hand, copy it to `$EV/apk/M.apk` and check its md5
against the build record instead of building again. Otherwise build it in one worktree:

```text
WT=$($RIG_TOOLKIT/wt-new.sh dsam-surfaceview-crop candidate 6ff42d9a06a3a0a00d2f50c67bc7d9b73c885572 | tail -1)
git -C $WT rev-parse 'HEAD^{tree}'          # MUST print 6b65d9c694802fdc1fb984a2267a5a1d22f72722
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh    # copy the APK from rig-data/apks/ to $EV/apk/M.apk
```

The dev host ran the JVM suite at `6ff42d9a`: 4380 tests, 0 failures. No rig unit tests.

## 2. What this is and why

`pr-train-20261029` round 2, Setup notes: a screenshot during W1 (`user-dsam-projection-223236.png`, 22:32) showed
the map filling only the top ~740 px of the 800x1280 screen, the rest black, and the Search pill at the right edge
clipped. No condition graded it, and WB (the `main` arm) was not looked at. The operator saw it with SurfaceView and
not after a switch to TextureView. The W1 build carried #1209, which changes only WiFi Direct code, so the likely
place is the SurfaceView path. Nothing has measured that. This round measures it on `main` with no PR in the build.

**The instrument is the screen itself.** After each session renders, the run takes a raw `screencap` and
`crop_measure.py` finds the box that holds every row and column with lit pixels (luminance above 24 in 2% or more of the
line). It prints that box as a share of the screen in permille. A full projection on this unit should be close to
1000; the round 2 crop was about 580 (740 of 1280). S1 is graded against TX's median, not against a fixed number,
because the unit's real full-screen value is not known yet. The run also keeps a PNG of each screen and the
`dumpsys SurfaceFlinger` and `dumpsys window windows` output, for the record.

## 3. What is different about this round

- **The physical buffer is portrait (800x1280) and the app draws landscape into it** (`rig-quirks/units/D-SAM-and-D-HP.md`).
  The measure counts lit rows and columns of the physical buffer, so the rotation does not matter to it.
- **D-SAM's station is held off before each launch.** `hold_station_off` saves `station-stand-down-network-id` once and
  deletes it, so no relaunch gives D-SAM's home network back (the race that cost `pr-train-20261029` round 2 its W1).
  `cleanup` writes the key back and checks the `enabled again` line.
- **TextureView can fall back to SurfaceView by itself** after two display stalls (`Falling back to SurfaceView for this
  session`). A TX session that prints it is left out of the control.
- **A PASS means "not seen in three sessions"**, not "never". The crop was seen once in round 2, so a FAIL is the strong
  result and a PASS is a weak one.
- **No setting other than `view-mode` changes between TX and S1.** The other video settings stay as D-SAM has them, and
  `setup` records them. Do not change them by hand.
- Markers are `adb shell log -t AutomationMarker <label>` on D-SAM and D-POCO.
- Read `rig-quirks/topics/tooling.md`, `topics/wifi.md`, `units/D-SAM.md`, `units/D-SAM-and-D-HP.md`, `units/D-POCO.md`.

## 4. Settings keys

**D-SAM**, written by `write_keys` with the app stopped and read back into `$EV/<run>-settings.xml`:

| Key | Type | Value |
|---|---|---|
| `log-level` | int | 1 (DEBUG) |
| `onboarding-version` | int | 2 |
| `allow-external-configuration` | boolean | true |
| `enable-audio-sink` | boolean | true |
| `use-aac-audio`, `narrow-band-profile-cap`, `kill-on-disconnect`, `enable-floating-button`, `external-bt-zbt-transport` | boolean | false |
| `connection-modes` | set | `wifi` |
| `wifi-connection-mode` | int | 3 |
| `native-ap-transport` | int | 0 |
| `native-poke-all-paired` | boolean | true |
| `wifi-direct-stable-identity` | boolean | true |
| `wifi-direct-rotation-pending` | boolean | false |
| `view-mode` | int | `VIEW`: TX 1 (TEXTURE), S1 0 (SURFACE) |
| `video-profile-starvation-cap`, `native-poke-bt-macs`, `native-mac`, `last-connected-native-mac`, `native-preferred-device-mac` | | delete |
| `station-stand-down-network-id` | | delete before each launch; written back in cleanup |

## 5. The deciding lines

App strings checked with `git grep -F` in `app/src/main` at `6ff42d9a`: all exist. Head-unit lines are in
`$EV/<run>-hu.txt`, phone lines in `$EV/<run>-ph.txt`, both threadtime. The session windows are `<run>-s1` to
`<run>-k2`, `<run>-s2` to `<run>-x3` and `<run>-s3` to `<run>-end`.

| Id | Line | Window | Read |
|---|---|---|---|
| L-SSL | `SSL handshake complete` | each session window | a session formed (2 lines per handshake at DEBUG) |
| L-THR | `Throughput over ` | each session window | the decoder is rendering |
| L-USE-S | `Using SurfaceView` | whole run | the activity built a SurfaceView |
| L-USE-T | `Using TextureView` | whole run | the activity built a TextureView |
| L-FALL | `Falling back to SurfaceView for this session` | whole run | TextureView gave up by itself |
| L-SURF | `onSurfaceChanged. Actual surface dimensions: width=` | each session window | record: the surface size the activity got |
| L-MISM | `Surface mismatch! Expected: ` | each session window | record: the surface is not the size the canvas expected |
| L-INIT | `Honest Init` | each session window | record: the anchor and insets the session used |
| S-CRASH | `Process: com.andrerinas.headunitrevived` | whole head-unit capture | system line of a `FATAL EXCEPTION` block |
| P-CRIT | `Critical error` | run start to end, less 2 s after each `k2` and `x3` marker | phone |
| P-PROJ | `Creating rfcomm socket for device: ` | whole run | phone: Android Auto dialled a head unit |

## 6. Runs

### setup. Known state

**setup.sh**
```bash
rl_step "D-SAM: back up, install M, write the keys"
adb -s "$SAM" shell am force-stop "$PKG"
[ -s "$EV/settings-backup-sam.xml" ] || adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-backup-sam.xml"
install_apk "$SAM" "$APK_M" setup
send ACTION_QUERY_STATE > "$EV/setup-query.txt" 2>&1
VIEW=1; write_keys
grep -aE 'resolution|video-fit|fullscreen|dpi|view-mode|screen-orientation' "$EV/setup-settings.xml" > "$EV/setup-video-keys.txt"   # not-literal: settings.xml keys
adb -s "$SAM" shell wm size > "$EV/setup-wm.txt" 2>&1
adb -s "$SAM" shell date > "$EV/setup-clock.txt"; date >> "$EV/setup-clock.txt"
rl_step "D-POCO: stop the app, go home"
adb -s "$PH" shell am force-stop "$PKG"
adb -s "$PH" shell input keyevent KEYCODE_HOME
```

**grade-setup.sh**
```bash
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/setup-identity.txt")" 1 'M installed on D-SAM (md5 of the pulled APK)'  # not-literal
rl_cond 2 ge "$(grep -c "$M_SHA" "$EV/setup-query.txt")" 1 "D-SAM reports commit $M_SHA"
rl_cond 3 ge "$(grep -ac 'name="wifi-connection-mode" value="3"' "$EV/setup-settings.xml")" 1 'wifi-connection-mode 3 read back'  # not-literal
rl_cond 4 ge "$(grep -ac 'shared_prefs\|<map' "$EV/settings-backup-sam.xml")" 1 'D-SAM settings backup taken'  # not-literal
```

- **PASS** when all four hold. Any other verdict stops the round, and cleanup still runs.

### TX. Control: TextureView

**tx.sh**
```bash
apk_check "$SAM" "$APK_M" TX
VIEW=1
v_cycle
```

**grade-tx.sh**
```bash
[ -s "$EV/TX-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the wait failed"; exit 0; }
v_counts TX
[ "${SESS:-0}" -ge 2 ] || { rl_verdict UNTESTABLE "session gate: $SESS of 3 sessions rendered and were measured"; exit 0; }
eval "$(python3 -I "$SCR/crop_grade.py" "$EV" TX)"
rl_cond 1 eq "$MD5" 1 'M on D-SAM (md5)'
rl_cond 2 ge "$(grep -ac 'name="view-mode" value="1"' "$EV/TX-settings.xml")" 1 'view-mode 1 read back'  # not-literal
rl_cond 3 ge "$USE_T" 1 'L-USE-T: the activity built a TextureView'
rl_cond 4 eq "$FALL" 0 'L-FALL 0: TextureView did not fall back'
rl_cond 5 ge "$SESS" 2 'sessions that rendered and were measured, of 3'
rl_cond 6 ge "${MIN:-0}" 850 "the smallest lit box is 850 permille or more (min $MIN, median $MED)"
rl_cond 7 eq "$CRIT" 0 'phone: Critical error, 2 s after k2 and x3 left out'
rl_cond 8 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
[ "${FALL:-0}" -ge 1 ] && rl_verdict INCONCLUSIVE "TextureView fell back to SurfaceView, so TX is not a control"
[ "${MIN:-0}" -lt 850 ] && rl_verdict INCONCLUSIVE "TX lights less than 850 permille ($MIN): TextureView crops too, or the measure misreads this screen; read the PNGs"
```

- **PASS** when all eight hold: TextureView lights the screen in every measured session.
- **INCONCLUSIVE** when TextureView fell back, or a TX box is below 850 permille. S1 then grades INCONCLUSIVE.
- **UNTESTABLE** when fewer than 2 sessions render, or the host stays hot. The stop rule then skips S1.

### S1. SurfaceView (the point)

**s1.sh**
```bash
apk_check "$SAM" "$APK_M" S1
VIEW=0
v_cycle
```

**grade-s1.sh**
```bash
[ -s "$EV/S1-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the wait failed"; exit 0; }
TXV=$(grep -ao 'VERDICT [A-Z]*' "$EV/TX.grade.txt" 2>/dev/null | head -1 | cut -d' ' -f2)   # not-literal: the TX grade file
v_counts S1
[ "${SESS:-0}" -ge 2 ] || { rl_verdict UNTESTABLE "session gate: $SESS of 3 sessions rendered and were measured"; exit 0; }
eval "$(python3 -I "$SCR/crop_grade.py" "$EV" S1 TX)"
rl_cond 1 eq "$MD5" 1 'M on D-SAM (md5)'
rl_cond 2 ge "$(grep -ac 'name="view-mode" value="0"' "$EV/S1-settings.xml")" 1 'view-mode 0 read back'  # not-literal
rl_cond 3 ge "$USE_S" 1 'L-USE-S: the activity built a SurfaceView'
rl_cond 4 ge "$SESS" 2 'sessions that rendered and were measured, of 3'
rl_cond 5 ge "${RATIO:-0}" 900 "every S1 lit box is 900 permille or more of TX's median (min $RATIO; S1 min $MIN, TX median $REF)"
rl_cond 6 eq "$CRIT" 0 'phone: Critical error, 2 s after k2 and x3 left out'
rl_cond 7 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
rl_cond 8 ge "$MISM" 0 "report: L-MISM lines in the session windows ($MISM)"
rl_cond 9 ge "$CROPPED" 0 "report: S1 sessions below 900 permille of TX ($CROPPED; which: $WHICH)"
if [ "${TXV:-}" != PASS ]; then rl_verdict INCONCLUSIVE "TX did not pass ($TXV), so there is no full-screen reference"
elif [ "${RATIO:-0}" -ge 750 ] && [ "${RATIO:-0}" -lt 900 ]; then rl_verdict INCONCLUSIVE "the smallest S1 box is $RATIO permille of TX: smaller, but not the round 2 crop; read the PNGs"
fi
```

- **PASS** when all seven graded conditions hold: SurfaceView lights as much of the screen as TextureView in every
  measured session. Read it as "not seen in three sessions".
- **FAIL** when any S1 box is below 750 permille of TX's median with TX PASS: the crop happens on `main`. Report which
  session (s1 fresh, s2 after a force-stop, s3 after an exit) and its PNG, L-SURF and L-MISM lines.
- **INCONCLUSIVE** when TX did not pass, or the smallest S1 box is 750 to 899 permille of TX.

### cleanup. D-SAM back to a known state (always runs)

**cleanup.sh**
```bash
fail=0
rl_step "D-SAM: give the network back, restore settings"
NETID=$(head -1 "$EV/netid.txt" 2>/dev/null)
adb -s "$SAM" shell am force-stop "$PKG"
if [ -n "$NETID" ]; then
  "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" "scalar:int:station-stand-down-network-id:$NETID" > "$EV/cleanup-netid.txt" 2>&1
  rl_cap_start cleanup-hu "$SAM"; from=$(rl_offset "$EV/cleanup-hu.txt")
  adb -s "$SAM" shell am start -n "$MAIN" >/dev/null
  rl_wait_line "$EV/cleanup-hu.txt" 'WiFi network is enabled again and should rejoin' 60 "$from" > "$EV/cleanup-give.txt" || fail=1
  app_exit
  rl_cap_stop cleanup-hu
fi
if [ -s "$EV/settings-backup-sam.xml" ]; then
  adb -s "$SAM" push "$EV/settings-backup-sam.xml" /data/local/tmp/settings-restore.xml >/dev/null
  adb -s "$SAM" shell run-as "$PKG" cp /data/local/tmp/settings-restore.xml shared_prefs/settings.xml
  adb -s "$SAM" shell rm -f /data/local/tmp/settings-restore.xml
  adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/cleanup-sam-settings.xml"
  diff <(tr -d '\r' < "$EV/settings-backup-sam.xml") <(tr -d '\r' < "$EV/cleanup-sam-settings.xml") > "$EV/cleanup-sam-diff.txt" || fail=1
fi
adb -s "$SAM" shell rm -f /sdcard/ohu-crop.raw /sdcard/ohu-crop.png
rl_step "D-HU and D-MOTO: Bluetooth on"
rl_radio "$DHU" bt on || fail=1
rl_radio "$MOTO" bt on || fail=1
echo "netid=${NETID:-none} fail=$fail" > "$EV/cleanup-result.txt"
exit "$fail"
```

**grade-cleanup.sh**
```bash
NETID=$(head -1 "$EV/netid.txt" 2>/dev/null)
if [ -n "$NETID" ]; then
  rl_cond 1 ge "$(grep -acF 'WiFi network is enabled again and should rejoin' "$EV/cleanup-hu.txt")" 1 'L-GIVE: D-SAM got its network back'
else
  rl_cond 1 eq 0 0 'no network id was saved, so nothing to give back'
fi
rl_cond 2 eq "$(grep -c . "$EV/cleanup-sam-diff.txt" 2>/dev/null)" 0 'D-SAM settings equal the backup'  # not-literal
```

- **PASS** when both hold. A FAIL leaves a note for the next round and changes no other verdict. M stays installed on
  D-SAM, because it is `main`.

## 7. Scripts

**lib.sh**
```bash
# Serials, paths, verbs and the view cycle for dsam-surfaceview-crop round 1. Sourced before every script and grade.
source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SAM=$D_SAM; PH=$D_POCO; DHU=$D_HU; MOTO=$D_MOTO
SCR=$EV/scripts; APK_M=$EV/apk/M.apk
M_SHA=6ff42d9a0
KEYS="scalar:int:log-level:1 scalar:int:onboarding-version:2 scalar:boolean:allow-external-configuration:true
 scalar:boolean:enable-audio-sink:true scalar:boolean:use-aac-audio:false scalar:boolean:narrow-band-profile-cap:false
 scalar:boolean:kill-on-disconnect:false scalar:boolean:enable-floating-button:false set:connection-modes:wifi
 scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:boolean:native-poke-all-paired:true
 scalar:boolean:external-bt-zbt-transport:false scalar:boolean:wifi-direct-stable-identity:true
 scalar:boolean:wifi-direct-rotation-pending:false del:video-profile-starvation-cap del:native-poke-bt-macs
 del:native-mac del:last-connected-native-mac del:native-preferred-device-mac"

send() { local a=$1; shift; adb -s "$SAM" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
mark() {   # mark LABEL: the same marker line on D-SAM and D-POCO
  adb -s "$SAM" shell log -t AutomationMarker "$1"; adb -s "$PH" shell log -t AutomationMarker "$1"
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> "$EV/marks.txt"
}
write_keys() {   # every D-SAM key of section 4 with view-mode VIEW, app stopped, then the file read back
  "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" $KEYS "scalar:int:view-mode:$VIEW" > "$EV/$RUN-keys.txt" 2>&1
  adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/$RUN-settings.xml"
}
apk_check() {   # apk_check SERIAL APK TAG: md5 of the installed APK against the built one, to $EV/TAG-identity.txt
  local s=$1 apk=$2 tag=$3 dev a b
  dev=$(adb -s "$s" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -1)
  rm -f "$EV/$tag-pulled.apk"
  [ -n "$dev" ] && adb -s "$s" pull "$dev" "$EV/$tag-pulled.apk" >/dev/null 2>&1
  a=$(md5sum < "$apk" 2>/dev/null | cut -c1-32); b=$(md5sum < "$EV/$tag-pulled.apk" 2>/dev/null | cut -c1-32)
  if [ -n "$a" ] && [ "$a" = "$b" ]; then echo "md5match=1 $a"; else echo "md5match=0 built=$a installed=$b"; fi > "$EV/$tag-identity.txt"
  rm -f "$EV/$tag-pulled.apk"
}
install_apk() { adb -s "$1" install -r -d "$2" > "$EV/$3-install.txt" 2>&1; apk_check "$1" "$2" "$3"; }
app_exit() {   # disconnect, exit, force-stop D-SAM
  adb -s "$SAM" shell am broadcast -f 0x00000020 -n "$RX" -a com.andrerinas.openheadunit.ACTION_DISCONNECT >/dev/null 2>&1; sleep 5
  adb -s "$SAM" shell am start -a android.intent.action.VIEW -d headunit://exit >/dev/null; sleep 5
  adb -s "$SAM" shell am force-stop "$PKG"
}
phone_link() { rl_radio "$PH" wifi "$1"; rl_radio "$PH" bt "$1"; }   # phone_link on|off
hold_station_off() {   # before a launch: save the stand-down network id once, then delete it so the launch gives nothing back
  local id
  id=$(adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml | tr -d '\r' | grep -ao 'name="station-stand-down-network-id" value="[0-9]*"' | grep -ao '[0-9]*"$' | tr -d '"')   # not-literal: a settings.xml line
  [ -n "$id" ] && [ ! -s "$EV/netid.txt" ] && echo "$id" > "$EV/netid.txt"
  echo "netid_seen=${id:-none}" >> "$EV/$RUN-gate.txt"
  "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" "del:station-stand-down-network-id" >> "$EV/$RUN-station.txt" 2>&1
}

shot() {   # shot TAG: raw and PNG screencap of D-SAM, the lit-box measure, and the window and layer dumps
  adb -s "$SAM" shell screencap /sdcard/ohu-crop.raw
  adb -s "$SAM" pull /sdcard/ohu-crop.raw "$EV/$1.raw" >/dev/null 2>&1
  adb -s "$SAM" shell screencap -p /sdcard/ohu-crop.png
  adb -s "$SAM" pull /sdcard/ohu-crop.png "$EV/$1.png" >/dev/null 2>&1
  adb -s "$SAM" shell rm -f /sdcard/ohu-crop.raw /sdcard/ohu-crop.png
  python3 -I "$SCR/crop_measure.py" "$EV/$1.raw" > "$EV/$1-lit.txt" 2>&1
  rm -f "$EV/$1.raw"
  adb -s "$SAM" shell dumpsys SurfaceFlinger > "$EV/$1-sf.txt" 2>&1
  adb -s "$SAM" shell dumpsys window windows > "$EV/$1-win.txt" 2>&1
}
see_picture() {   # see_picture N FROM: wait for SSL and a rendering decoder after byte FROM, settle, then shot RUN-N
  if rl_wait_line "$EV/$RUN-hu.txt" 'SSL handshake complete' 150 "$2"; then
    rl_wait_line "$EV/$RUN-hu.txt" 'Throughput over ' 60 "$2" || echo "$1 thr=0" >> "$EV/$RUN-gate.txt"
    rl_sleep 20
    shot "$RUN-$1"; echo "$1 ssl=1" >> "$EV/$RUN-gate.txt"
  else
    echo "$1 ssl=0" >> "$EV/$RUN-gate.txt"
  fi
}

v_cycle() {   # one view-mode run: s1 fresh, s2 after a force-stop with the session up, s3 after a clean exit. Needs VIEW.
  local att from ok=0
  rm -f "$EV/$RUN-thermal.txt" "$EV/$RUN-gate.txt"
  rl_thermal_gate || { echo HOST_HOT > "$EV/$RUN-thermal.txt"; return 2; }
  rl_hold_off "$DHU" bt || echo "dhu_bt_not_off" >> "$EV/$RUN-gate.txt"
  adb -s "$SAM" shell am force-stop "$PKG"
  write_keys
  adb -s "$PH" shell am force-stop "$PKG"
  adb -s "$PH" shell input keyevent KEYCODE_HOME
  rl_cap_start "$RUN-hu" "$SAM"; rl_cap_start "$RUN-ph" "$PH"
  for att in 1 2; do
    rl_step "s1, attempt $att"
    phone_link off
    adb -s "$SAM" shell am force-stop "$PKG"
    hold_station_off
    mark "$RUN-s1"; from=$(rl_offset "$EV/$RUN-hu.txt")
    adb -s "$SAM" shell am start -n "$MAIN" >/dev/null
    sleep 2; phone_link on
    see_picture s1 "$from"
    grep -q '^s1 ssl=1' "$EV/$RUN-gate.txt" && { ok=1; break; }
    app_exit
  done
  rl_step "s2: force-stop with the session up, relaunch"
  mark "$RUN-k2"
  adb -s "$SAM" shell am force-stop "$PKG"
  hold_station_off
  mark "$RUN-s2"; from=$(rl_offset "$EV/$RUN-hu.txt")
  adb -s "$SAM" shell am start -n "$MAIN" >/dev/null
  [ "$ok" = 1 ] && see_picture s2 "$from"
  rl_step "s3: clean exit, relaunch"
  mark "$RUN-x3"
  app_exit
  phone_link off
  hold_station_off
  mark "$RUN-s3"; from=$(rl_offset "$EV/$RUN-hu.txt")
  adb -s "$SAM" shell am start -n "$MAIN" >/dev/null
  sleep 2; phone_link on
  [ "$ok" = 1 ] && see_picture s3 "$from"
  mark "$RUN-end"
  app_exit
  rl_cap_stop "$RUN-hu"; rl_cap_stop "$RUN-ph"
  rl_release "$DHU" bt
}

v_counts() {   # v_counts RUN: the counts every view grade reads
  local r=$1 hu="$EV/$1-hu.txt" ph="$EV/$1-ph.txt" n
  SESS=0
  for n in s1 s2 s3; do grep -q "^$n ssl=1" "$EV/$r-gate.txt" 2>/dev/null && [ -s "$EV/$r-$n-lit.txt" ] && SESS=$((SESS + 1)); done
  USE_S=$(grep -acF 'Using SurfaceView' "$hu" 2>/dev/null)
  USE_T=$(grep -acF 'Using TextureView' "$hu" 2>/dev/null)
  FALL=$(grep -acF 'Falling back to SurfaceView for this session' "$hu" 2>/dev/null)
  MISM=$(rl_cnt "$hu" "$r-s1" "$r-end" 'Surface mismatch! Expected: ')
  CRASH=$(grep -acF 'Process: com.andrerinas.headunitrevived' "$hu" 2>/dev/null)   # system
  CRIT=$(python3 -I "$SCR/crit_count.py" "$ph" "$r-s1" "$r-end" 'Critical error' "$r-k2" "$r-x3")   # phone
  MD5=$(grep -c '^md5match=1' "$EV/$r-identity.txt" 2>/dev/null)   # not-literal
}
```

**crop_measure.py**
```python
# Lit box of a raw screencap (header, then RGBA_8888): rows and columns with 2% or more pixels above luminance 24.
# Prints one line: w= h= rows=first-last cols=first-last box_permille=N. Exit 2 when the file is not a raw RGBA frame.
import struct, sys
data = open(sys.argv[1], "rb").read()
w, h, fmt = struct.unpack("<III", data[:12])
head = len(data) - w * h * 4
if fmt != 1 or head < 12 or head > 16:
    print(f"crop_measure: not RGBA_8888 raw (w={w} h={h} fmt={fmt} header={head})"); sys.exit(2)
px = data[head:]
row_lit = [0] * h; col_lit = [0] * w
for y in range(0, h):
    base = y * w * 4
    for x in range(0, w, 2):
        i = base + x * 4
        if px[i] * 299 + px[i + 1] * 587 + px[i + 2] * 114 > 24000:
            row_lit[y] += 2; col_lit[x] += 2
rows = [y for y in range(h) if row_lit[y] >= w * 0.02]
cols = [x for x in range(0, w, 2) if col_lit[x] >= h * 0.02]
if not rows or not cols:
    print(f"w={w} h={h} rows=none cols=none box_permille=0"); sys.exit(0)
area = (rows[-1] - rows[0] + 1) * (cols[-1] - cols[0] + 2)
print(f"w={w} h={h} rows={rows[0]}-{rows[-1]} cols={cols[0]}-{cols[-1]} box_permille={min(1000, area * 1000 // (w * h))}")
```

**crop_grade.py**
```python
# Shell assignments for a view grade: crop_grade.py EV RUN [REF_RUN]. Reads EV/RUN-s{1,2,3}-lit.txt.
# MIN, MED: the run's smallest and median box. With REF_RUN: REF (its median), RATIO (MIN as permille of REF),
# CROPPED (sessions below 900 permille of REF) and WHICH (their names).
import re, statistics, sys
from pathlib import Path
ev, run = Path(sys.argv[1]), sys.argv[2]
ref_run = sys.argv[3] if len(sys.argv) > 3 else None
def boxes(r):
    out = {}
    for n in ("s1", "s2", "s3"):
        try:
            m = re.search(r"box_permille=(\d+)", (ev / f"{r}-{n}-lit.txt").read_text())
        except OSError:
            continue
        if m:
            out[n] = int(m.group(1))
    return out
mine = boxes(run)
vals = sorted(mine.values())
print(f"MIN={vals[0] if vals else 0}; MED={int(statistics.median(vals)) if vals else 0}")
if ref_run:
    rv = sorted(boxes(ref_run).values())
    ref = int(statistics.median(rv)) if rv else 0
    low = [n for n, v in sorted(mine.items()) if ref and v * 1000 < 900 * ref]
    ratio = (vals[0] * 1000 // ref) if vals and ref else 0
    print(f"REF={ref}; RATIO={ratio}; CROPPED={len(low)}; WHICH={','.join(low) or 'none'}")
```

**crit_count.py**
```python
# Count lines holding NEEDLE between markers FROM and TO, leaving out the 2 s after each EXCL marker.
# usage: crit_count.py CAPTURE FROM TO NEEDLE [EXCL ...]. Prints nothing (exit 1) when the window is missing.
import re, sys
from datetime import datetime, timedelta
cap, a, b, needle, excl = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5:]
def ts(line):
    m = re.match(r"(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d{3})", line)
    return datetime.strptime("2026-" + m.group(1) + " " + m.group(2), "%Y-%m-%d %H:%M:%S.%f") if m else None
try:
    lines = open(cap, errors="replace").read().splitlines()
except OSError:
    sys.exit(1)
win, on = [], False
for l in lines:
    if not on and ("AutomationMarker: " + a) in l:
        on = True
    if on:
        win.append(l)
        if ("AutomationMarker: " + b) in l:
            break
if not win:
    sys.exit(1)
cuts = [t for t in (ts(l) for l in win if any(("AutomationMarker: " + e) in l for e in excl)) if t]
n = 0
for l in win:
    if needle in l:
        t = ts(l)
        if t and any(c <= t <= c + timedelta(seconds=2) for c in cuts):
            continue
        n += 1
print(n)
```

## 8. Do not re-run

- `pr-train-20261029` round 2 W1: its crop came from a build with #1209; this round has no PR in the build.
- GLES (`view-mode` 2): not seen to crop and not asked about.

## 9. Report back

1. S1: the box in permille for s1, s2 and s3, RATIO against TX's median, which sessions were cropped, and for each
   cropped one its PNG, its L-SURF and L-MISM lines, and the SurfaceView layer's lines from its `-sf.txt`.
2. TX: the three boxes and whether L-FALL printed.
3. `setup-video-keys.txt` and `setup-wm.txt`, so a crop can be matched to the resolution and fit settings in force.
