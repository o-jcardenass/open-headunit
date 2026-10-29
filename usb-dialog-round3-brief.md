# usb-dialog, round 3 brief: no raise over Android's USB dialog, one dialog per double press

Published name: `usb-dialog-round3-brief.md`. Results go in `usb-dialog-round3-results.md`.
Evidence: upload `usb-dialog-round3-captures.zip` to the existing release `rig-evidence-usb-dialog`.

Units: D-HP (API 17) and D-POCO (API 35) as USB hosts, one at a time. D-MOTO is the wired phone in both stages. D-HU
and D-SAM take no part (D-SAM has no USB host, round 1).

Queue line: `usb-dialog-round3-brief.md` (one arm, `fork/fix/usb-permission-dialog` `9f90e121`; units D-HP, D-POCO,
D-MOTO; F4a and F4b are the point).

Pairing: possible only with a round that uses D-HU alone. If this round is the second of a pair, it takes the lock
`/tmp/ohu-rig-usbdlg.lock`. Its hand steps are many and short, so the other round must have none during this one.

Hand steps: cable moves, one plug and one unplug per cycle, every USB system dialog by a fixed rule per run (section 5),
Clear defaults when cued, one count per run. Injected taps: none this round.

**Time budget.** Build 20 min, Prepare 15 min, stage H 20 min, stage P 25 min, cable moves 5 min, thermal gates about
5 min. **About 1 h 30 min.** Write the results file after every run.

| Stage | Host | Runs, in this order, in one capture | Min |
|---|---|---|---|
| H | D-HP (API 17), wireless adb, D-MOTO on its OTG port | R5c, seed, F4a, F4b, F4c | 20 |
| P | D-POCO (API 35), wireless adb, D-MOTO on its OTG port | R5c, seed, F4a, F4b, F4c, R4a | 25 |

**F4a and F4b are the point of the round.** Round 2 found that the app covers Android's USB dialog when "Start on
USB" is on (F4, host grade FAIL for the branch). The candidate now skips that raise. F4c is the control with defaults
stored. R5c tests the second fix. R4a on D-POCO is a regression check.

## 1. Build and baseline

One arm. Round 1 measured `main` `947b3ae9`. No USB file changed from there to `main` `2018d8f09`, the base of the
candidate, so this round builds no baseline and cites rounds 1 and 2 for every "before" number.

| Ref | Commit | Tree |
|---|---|---|
| `fork/fix/usb-permission-dialog` | `9f90e121e962ffe2fd70142ccd41ae68485cc8fd` | `dcb105332230c7e644e8df9a7e18be7421ba5e2e` |

**History was rewritten.** The branch was force-pushed: round 2's `63f20a8f` is gone from it. The branch is still 3
commits, fixups folded, on `main` `2018d8f09`. The dev host suite at this commit: 4322 JVM tests, 0 failures. Do not run
the unit tests on the rig. No other queued round shares this base that this brief knows of. Check the README Queue for
one on `main` `2018d8f09`, and build it in the same worktree if there is one.

**`get_apk.sh`**
```bash
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round3; mkdir -p $OUT
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh usb-dialog candidate main | tail -1)}   # record the path in Setup notes
git -C $WT fetch fork fix/usb-permission-dialog
git -C $WT checkout --detach 9f90e121e962ffe2fd70142ccd41ae68485cc8fd
git -C $WT rev-parse 'HEAD^{tree}'                 # MUST print dcb105332230c7e644e8df9a7e18be7421ba5e2e
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
cp rig-data/apks/*.apk $OUT/apk-C.apk               # at once: build_hur.sh deletes the previous APK
md5sum $OUT/apk-C.apk                                # record in Setup notes
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF "not raised over Android's USB dialog"   # MUST be 1 or more
```

The string in the last line is new since round 2, so round 2's APK cannot carry it.

**Identity on each host, after the install:** `send ACTION_QUERY_STATE` and its `data=` line carries `9f90e121`, and
the md5 of the pulled live APK matches `apk-C.apk`. Install with `adb install -r -d`. Never uninstall.

## 2. What this is and why

Two reports describe a USB permission dialog on every connect, often two of them, and a "Use by default" tick that is
not remembered. Round 1 measured two causes on `main`: with "Listen for USB Devices" off the service asks on every
plug, and with it on the service stacks its own dialog on top of Android's. Round 2 measured the candidate's fix for
both (R4a, R4b, R4c, R5a, R5b, R5t PASS on both hosts).

Round 2 also found two faults on the branch:

- **F4.** With "Start on USB" and "Reopen on reconnection" on and no default stored, `UsbLauncherListener.onUsbAttach`
  raised `MainActivity` 26 to 80 ms after Android's confirm dialog appeared, on 4 of 4 cycles. The dialog was then
  hidden, also in Recents, and no session formed. On `main` the service stacked its own dialog over `MainActivity`, so
  the user could still answer. The branch replaced that ask with `wait`, so the user saw nothing.
- **R5c.** Two `ACTION_CHECK_USB` broadcasts 51 ms and 570 ms apart opened two of our dialogs for one device. One of
  them stayed open under the session.

The candidate's two new changes:

1. **F4 fix.** `onUsbAttach` now asks `UsbPermissionAskPolicy.raiseCoversDialog`: the device has no permission and
   Android shows its own dialog for it. Then the raise is skipped and the service logs
   `Reopen on reconnection: not raised over Android's USB dialog (<source>)`. The source is `USB normal attach (...)`
   for A or `USB accessory attach` for B. After the user answers, Android starts `UsbAttachedActivity`, which launches
   the app (`USB auto-start: launching app for`). This check does not read `routeSeen`.
2. **R5c fix.** A user ask passes an open entry only when that entry is at least `USER_REPEAT_MS` = 3000 ms old. A
   refused second ask logs `UsbLauncher: a dialog for <path> is already open; not opening a second` on the service
   path, or `UsbPermissionAsks: a dialog for <path> opened under 3000ms ago; not opening a second` on the UI path.
   `ACTION_CHECK_USB` takes the service path.

## 3. What is different about this round

- **The rig's round 2 fixes are in the scripts here.**
  - `SEED_FAILED` skips R4a and marks the F4 dialog conditions INCONCLUSIVE (section 8). The stage goes on.
  - Every cycle label and every seed label is fresh: `runplugs` and `seed` skip a label whose marker is already in
    the capture, so a restarted stage cannot reuse one and poison `since`.
  - `timeline.sh` keeps whole lines. Round 2 cut them at 240 characters, so `action=` read `ho` or `wai`.
  - `timeline.sh` now lists the `AutomationReceiver: ` lines for `ACTION_CHECK_USB` (`RX_CHECK`).
  - `cue()` appends the run's rule to every cue. Each run opens with a NEXT cue and a 12 s pause. Each COUNT cue
    has a 25 s pause.
  - The LOOK cue has a 45 s pause after it.
  - The unplug wait cues UNPLUG a second time after 60 s and waits 120 s more.
  - `wt-new.sh` takes `<topic> <role> <ref>`. `rig_preflight.sh` roles use underscores.
  - `writeprefs` is the rig's key-argument version, in `lib.sh`.
- **The seed rule now ticks.** Round 2's seed rule ("tick nothing") failed three times on D-HP. Our dialog stacked
  over Android's before the operator answered inside 2 s. A ticked "Open" stores a default, so the next seed plug
  needs no answer at all. The F4 runs clear the defaults first.
- **The seed runs after the last force-stop of the stage.** D-HP (API 17) keeps the route verdict in the app process
  only, so every `setkeys` (a force-stop) loses it. The F4 fix does not read `routeSeen`. But with `routeSeen=no` the
  service still asks for A at its 2 s check, and that dialog of ours would stack on Android's. F4's "none of ours"
  conditions therefore need `routeSeen=yes`, and the stage seeds after `setkeys f4`. On D-POCO the verdict is stored
  with the boot count and survives `setkeys listen-on` before R4a.
- **F4 starts from the launcher.** The app is sent to the background (`input keyevent 3`) before each F4 plug. That is
  where a car user meets the raise. It also makes F4c's raise visible as a `MainActivity` start.
- **D-POCO may hold a lasting grant for D-MOTO.** Round 2 read `serial_outside=0` before and after. Prepare reads it
  again. If it is above 0, grade the affected identity's R4a and F4 dialog conditions INCONCLUSIVE and say why. This
  round does not reinstall.
- **D-MOTO keeps USB debugging on**, as in rounds 1 and 2. Its normal identity A read `22B8:2E81` with `mtp=no` on both
  hosts. Record `mtp=` from the decision lines. Its wireless adb drops in accessory mode, so its buffer is cleared
  before each plug and dumped after each unplug (`moto_dump`). Its Bluetooth stays off all round.
- **D-HP feeds D-MOTO over OTG and its battery is degraded.** Start stage H at 60% or more. The script reads it before
  every plug and stops the stage at 30%. If two plugs in a row print `NO_PLUG`, reseat the OTG adapter in D-HP's port
  (round 2: the connector, not the code).
- **Expected INCONCLUSIVE:** an identity with a lasting grant on D-POCO, and the F4 dialog conditions on a stage whose
  seed failed. `Throughput over` is not graded.
- **Thermal.** `rig-quirks/topics/tooling.md`: `rig_thermal.sh wait 75` before each stage (once more on exit 1),
  `rig_thermal.sh watch` during it, and the round stops as UNTESTABLE (host thermal) after a second failed wait.
- Read `rig-quirks/topics/tooling.md`, `units/D-HP.md`, `units/D-SAM-and-D-HP.md`, `units/D-POCO.md`, `units/D-MOTO.md`
  and template §7b before the first run.

## 4. Settings keys

Write with the app stopped, on each host, with `$RIG_TOOLKIT/set_prefs_runas_host.py` through `writeprefs` (section 6;
neither host is rooted). `stage3.sh` backs up `settings.xml` to `$OUT/backup-<ST>.xml` first. `writeprefs` reads every
key back after every write. Diff the restored file against the backup at the end, with carriage returns stripped.

The base block, `BASEKEYS` in `lib.sh`, as round 2:

```
int:log-level=2 int:onboarding-version=2 bool:allow-external-configuration=true
int:wifi-connection-mode=0 set:connection-modes=usb
bool:auto-start-on-usb=false bool:reopen-on-reconnection=true
bool:auto-connect-last-session=false bool:auto-connect-single-usb=false
set:allow-devices= bool:use-libusb=false bool:kill-on-disconnect=false bool:enable-floating-button=false
```

Delete `usb-attach-route-boot` and `video-profile-starvation-cap`. Leave the audio keys as they are (the rig's worst
case is deliberate) and leave `screen-orientation` as found. Why the less obvious keys:

- `log-level=2` (INFO) is enough: every app line this round greps prints at INFO (`AppLog.i`), and INFO prints
  `SSL handshake complete` once per session.
- `allow-external-configuration=true`: `ACTION_EXPORT_LOG` is gated on it. `ACTION_LOG_MARKER` is not.
- The three auto-connect keys off and `allow-devices` empty put A on the 2 s fallback path, as in rounds 1 and 2.
- `reopen-on-reconnection=true` stays on for every run. It does nothing while `auto-start-on-usb` is false.
- `listen-for-usb-devices` and `auto-start-on-usb` are written per run by `setkeys`, never in this block:

| `setkeys` tag | `listen-for-usb-devices` | `auto-start-on-usb` | Runs |
|---|---|---|---|
| `listen-off` | `false` | `false` | R5c |
| `f4` | `true` | `true` | seed, F4a, F4b, F4c |
| `listen-on` | `true` | `false` | R4a (stage P only) |

Round 2 found no `usb-blacklist` and no `enable-car-launcher` key on either host. Read both again. If the blacklist
names D-MOTO, empty it and say so. If `enable-car-launcher` is `true`, "Reopen on reconnection" is off, and F4 is
INCONCLUSIVE.

## 5. Cabling, hand steps and the answer rules

| Stage | On the PC | On an OTG port | Wireless adb |
|---|---|---|---|
| Prepare | D-HP, D-POCO, D-MOTO | | |
| H | D-POCO | D-MOTO on D-HP | D-HP, D-MOTO |
| P | D-HP | D-MOTO on D-POCO | D-POCO, D-MOTO |

| Id | Hand step | When | Why there is no verb |
|---|---|---|---|
| CAB | Move the cables and the OTG adapter as the stage table says | stage start and end | hardware |
| PLUG / UNPLUG | Plug or unplug D-MOTO's cable at the OTG adapter | each cue | hardware |
| DLG | Answer the USB dialogs by the run's rule below | during each cycle | a SystemUI dialog: no verb reaches it, and the round grades what a person's answer does |
| LOOK | Say if a USB dialog is visible, then answer by the F4 rule | F4a, F4b | only a person can say what the screen shows |
| CLEAR | On the App info screen the script opens, press Clear defaults (API 35: Open by default, then Clear default preferences) | each CLEAR cue | Settings is another app, and no shell command clears USB defaults without `MANAGE_USB` |
| COUNT | Tell the host, per plug of the run just ended, how many USB dialogs you saw and the first word of each | each COUNT cue | the second column of the dialog count |

Open the cue terminal first: `tail -n0 -F $OUT/cues.log`. Read the operator this table once, at the start, and leave it
on screen. Two words tell the dialogs apart: Android's own dialog starts with "Open" ("Open Open Headunit ..."), and
the app's own starts with "Allow" ("Allow ... to access the USB device?").

| Run | Rule for every USB dialog in the run |
|---|---|
| R5c | Press OK. Tick nothing. |
| seed | "Open ...": tick the box, then press OK, at once. "Allow ...": press Cancel. |
| F4a, F4b | Touch nothing until the host says LOOK. At LOOK, say if a USB dialog is visible. Then answer each USB dialog as it shows: "Open ...": tick, then OK. "Allow ...": Cancel. If none is visible at LOOK, say so, press Recents once, and answer a USB dialog there by the same rule. Never press Back or Home. |
| F4c | Touch nothing. Remember any dialog you see. |
| R4a | Tick the box if there is one, then press OK, as soon as the dialog shows. |

Always: act only on a cue or by the rule. Do not touch D-MOTO.

## 6. Helpers

Save every block below in `$OUT/scripts/` and run every script from that directory. Run every adb call against one
unit in sequence (template rule 8). All parsing runs on the host. Grep every capture with `-a`.

**`lib.sh`**
```bash
. $RIG_TOOLKIT/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round3; mkdir -p $OUT; export LC_ALL=C
HP=CNU350BGBJ; POCO=4f4027e9; MOTO=ZY22GC3BM4
BASEKEYS="int:log-level=2 int:onboarding-version=2 bool:allow-external-configuration=true int:wifi-connection-mode=0
 set:connection-modes=usb bool:auto-start-on-usb=false bool:reopen-on-reconnection=true bool:auto-connect-last-session=false
 bool:auto-connect-single-usb=false set:allow-devices= bool:use-libusb=false bool:kill-on-disconnect=false
 bool:enable-floating-button=false"
SEEDRULE="Open: tick the box, then press OK, at once. Allow: press Cancel."
# Set in Prepare (wireless.env): HP_W, POCO_W, MOTO_W (ip:5555). Set per stage by stage3.sh: ST (H|P), HU, HUNAME.
A() { adb -s "$HU" "$@"; }
F() { echo $OUT/$ST-hu.txt; }
send() { local a=$1; shift; A shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3
  timeout 3 adb -s "$MOTO_W" shell log -t RIGMARK "RIGMARK $1" >/dev/null 2>&1
  echo "$1 host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
# used: 0 when the capture already holds the marker LABEL-cue (a label never runs twice)
used() { grep -aqF "AutomationMarker: $1-cue" $(F) 2>/dev/null; }
cue() { printf '\a'; echo "$(date +%T) $*${RULE:+  || RULE: $RULE}" | tee -a $OUT/hand-steps.log >> $OUT/cues.log; }
launch() { A shell am start -n $MAIN >/dev/null; sleep 2; }
LOGARGS="-b main -b system -b events -v time"
cap() { A logcat -G 16M >/dev/null 2>&1; A logcat -b main -b system -b events -c
  stdbuf -oL adb -s "$HU" logcat $LOGARGS >> $(F) & echo $! > $OUT/$ST-hu.pid; }
alive() { kill -0 "$(cat $OUT/$ST-hu.pid)" 2>/dev/null && return 0
  echo "$(date +%T) $ST capture restarted" >> $OUT/marks.txt
  stdbuf -oL adb -s "$HU" logcat $LOGARGS >> $(F) & echo $! > $OUT/$ST-hu.pid; }
uncap() { kill "$(cat $OUT/$ST-hu.pid)" 2>/dev/null; }
# since MARK: the capture from the line that ends "AutomationMarker: MARK"; a restarted capture's repeats are dropped
since() { tr -d '\r' < $(F) | awk '!seen[$0]++' | sed -n "/AutomationMarker: $1\$/,\$p"; }
cnt() { since "$1" | grep -acF -- "$2"; }
firstts() { since "$1" | grep -aF -m1 -- "$2" | cut -c1-18; }
waitfor() { local t=0; while [ $t -lt $3 ]; do alive; [ "$(cnt "$1" "$2")" -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$ST.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }
bat() { A shell dumpsys battery | tr -d '\r' | grep -a -m1 level | tr -dc 0-9; }
# hupid: the app's pid (pidof on API 35, ps on API 17), or none
hupid() { local p; p=$(A shell "pidof $PKG 2>/dev/null" | tr -d '\r ')
  case "$p" in ''|*[!0-9]*) p=$(A shell ps | tr -d '\r' | awk -v k=$PKG '$NF==k{print $2; exit}');; esac; echo "${p:-none}"; }
# comp: UsbAttachedActivity as dumpsys package lists it (enabled, disabled, or empty = manifest default)
comp() { A shell dumpsys package $PKG 2>/dev/null | tr -d '\r' | awk '/disabledComponents:/{s="disabled"}
  /enabledComponents:/{s="enabled"} s && /app\.UsbAttachedActivity/{print s; exit}'; }
# usbdef TAG: save dumpsys usb, append its usbparse.sh row to defaults.tsv, set DEFN (stored defaults of ours)
usbdef() { A shell dumpsys usb > $OUT/$1-usb.txt 2>&1; ./usbparse.sh $OUT/$1-usb.txt $1 >> $OUT/defaults.tsv
  DEFN=$(tail -1 $OUT/defaults.tsv | grep -ao 'defaults=[0-9]*' | cut -d= -f2); }
# focus TAG: what the window manager and the activity manager say is in front (F4 only; records, decides nothing)
focus() { { echo "== $1 $(date +%T)"; A shell dumpsys window | tr -d '\r' | grep -aE 'mCurrentFocus|mFocusedApp'
  A shell dumpsys activity activities | tr -d '\r' | grep -aE 'topResumedActivity|mResumedActivity|mFocusedActivity'; } >> $OUT/focus.txt; }
# moto_dump TAG: D-MOTO's buffer after its wireless adb comes back (cap 60 s)
moto_dump() { local t=0; while [ $t -lt 60 ]; do adb connect $MOTO_W >/dev/null 2>&1
    adb -s $MOTO_W shell true 2>/dev/null && { adb -s $MOTO_W logcat -d -v time > $OUT/$1-phone.txt; echo "$1 phone ok" >> $OUT/marks.txt; return 0; }
    sleep 2; t=$((t+2)); done; echo "$1 phone UNREACHABLE" >> $OUT/marks.txt; return 1; }
# writeprefs KEY...: int:k=v bool:k=v set:k=a,b del:k. Wraps set_prefs_runas_host.py (force-stops the app), reads every key back.
writeprefs() { local args=() a t k v n
  for a in "$@"; do t=${a%%:*}; k=${a#*:}
    case $t in int) args+=("scalar:int:${k%%=*}:${k#*=}");; bool) args+=("scalar:boolean:${k%%=*}:${k#*=}");;
      string) args+=("scalar:string:${k%%=*}:${k#*=}");; set) args+=("set:${k%%=*}:${k#*=}");; del) args+=("del:$k");; esac; done
  PKG=$PKG python3 -I $RIG_TOOLKIT/set_prefs_runas_host.py "$HU" "${args[@]}" >> $OUT/writeprefs.log 2>&1 || return 1
  A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/$ST-settings-now.xml
  for a in "$@"; do t=${a%%:*}; k=${a#*:}; case $t in del) k=$k;; *) k=${k%%=*};; esac
    n=$(grep -ac "name=\"$k\"" $OUT/$ST-settings-now.xml); case $t in del) [ "$n" -eq 0 ] || { echo "$ST READBACK_FAIL $a" >> $OUT/marks.txt; return 1; };; *) [ "$n" -ge 1 ] || { echo "$ST READBACK_FAIL $a" >> $OUT/marks.txt; return 1; };; esac; done; }
```

**`usbparse.sh`** (unchanged from round 2)
```bash
#!/usr/bin/env bash
# usbparse.sh DUMP TAG: one row from a saved dumpsys usb. Only the "Device preferences" block (API 17)
# and the device_preferences blocks (API 35) are defaults. serial_outside counts D-MOTO's serial anywhere else.
tr -d '\r' < "$1" | awk -v tag="$2" '
function ind(s) { match(s, /^ */); return RLENGTH }
function num(s, k,  r) { if (match(s, k "[0-9]+")) { r = substr(s, RSTART, RLENGTH); sub(/.*=/, "", r); return r } return "" }
{ i = ind($0) }
insec && i <= si && $0 ~ /[^ ]/ { insec = 0; v = ""; p = ""; pend = 0 }
tolower($0) ~ /device[ _]preferences/ { insec = 1; si = i; secs++; v = ""; p = ""; pend = 0; next }
insec { a = num($0, "(mVendorId=|vendor_id=)"); if (a != "") v = a; b = num($0, "(mProductId=|product_id=)"); if (b != "") p = b }
insec && pend && v != "" && p != "" { pairs = pairs v "/" p " "; n++; pend = 0 }
insec && /headunitrevived/ { if (v != "" && p != "") { pairs = pairs v "/" p " "; n++ } else pend = 1 }
!insec && /ZY22GC3BM4/ { other++ }
END { printf "%s\tdefaults=%d\tpairs=%s\tsections=%d\tserial_outside=%d\n", tag, n, (pairs == "" ? "none" : pairs), secs, other }'
```

`pairs=` is decimal vendor/product: `8888/11905` is A (`22B8:2E81`), `8888/11906` is A in MTP mode (`22B8:2E82`),
`6353/11521` is B (`18D1:2D01`). `serial_outside` is meaningful only with D-MOTO unplugged.

**`timeline.sh`**
```bash
#!/usr/bin/env bash
# timeline.sh CAPTURE P: the decisive lines of cycle P, from marker P-cue to marker P-unplug, one per row with the
# whole line, then one SUMMARY row. A = the phone's normal identity, B = its accessory identity. No verdict.
# CREATE=1 counts the events buffer's *_create_activity lines as dialog starts (only if the host prints no START u0).
set -u
f=$1; P=$2
tr -d '\r' < "$f" | awk '!seen[$0]++' \
 | sed -n "/AutomationMarker: $P-cue\$/,/AutomationMarker: $P-unplug\$/p" \
 | awk -v P="$P" -v create="${CREATE:-0}" '
function ms(t,  a) { split(substr(t, 7, 12), a, ":"); return ((a[1] * 60 + a[2]) * 60 + a[3]) * 1000 }
function act(s) { return s ~ /UsbConfirmActivity/ ? "CONFIRM" : (s ~ /UsbPermissionActivity/ ? "PERM" : "RESOLVER") }
function field(s, k,  r) { if (match(s, k "=[^ ]+")) { r = substr(s, RSTART + length(k) + 1, RLENGTH - length(k) - 1); return r } return "?" }
function add(lst, v) { return lst == "-" ? v : lst "," v }
function src(s) { return s ~ /\(USB accessory attach\)/ ? "B" : "A" }
{ t = substr($0, 1, 18); k = "" }
/START u0/ && /\.usb\.Usb(Confirm|Permission|Resolver)Activity/ { k = act($0) "_START" }
create == 1 && /_create_activity/ && /\.usb\.Usb(Confirm|Permission|Resolver)Activity/ { k = act($0) "_START" }
/_finish_activity/ && /\.usb\.Usb(Confirm|Permission|Resolver)Activity/ { k = act($0) "_FINISH" }
/START u0/ && /openheadunit\.main\.MainActivity/ { k = "MAIN_START" }
/Normal USB device attached: / { k = "ATTACH_A" }
/USB accessory device attached, connecting\./ { k = "ATTACH_B" }
/UsbLauncher: permission for / { k = "DEC" }
/Requesting USB permission for / { k = "REQ" }
/(UsbLauncher|UsbPermissionAsks): a dialog for .*not opening a second/ { k = "REFUSE" }
/AutomationReceiver: .*ACTION_CHECK_USB/ { k = "RX_CHECK" }
/SSL handshake complete/ { k = "SSL" }
/Media Sink Setup Request: / { k = "MS" }
/Reopen on reconnection: launching MainActivity/ { k = "RAISE" }
/Reopen on reconnection: not raised over Android.s USB dialog/ { k = "HOLDRAISE" }
/USB auto-start: launching app for / { k = "AUTOSTART" }
/USB Intent: / && /USB_DEVICE_DETACHED/ { k = "DETACH" }
/FATAL EXCEPTION|ANR in com\.andrerinas|NoClassDefFoundError/ { k = "FATAL" }
k != "" { n++; T[n] = ms(t); TS[n] = t; K[n] = k; L[n] = substr($0, 20) }
END {
  decA = decB = rsl = mtpA = srcA = srcB = "-"; ssl = ms_n = fatal = stacked = reqA = reqB = mains = 0
  raiseA = raiseB = holdA = holdB = auto = refuse = rx = 0; tms = -1; tb = -1; tauto = -1
  for (i = 1; i <= n; i++) {
    id = "-"
    if (K[i] == "DEC") {
      id = (L[i] ~ /identity=accessory/) ? "B" : "A"
      if (id == "A") { decA = add(decA, field(L[i], "action")); srcA = add(srcA, field(L[i], "source")); mtpA = add(mtpA, field(L[i], "mtp")) }
      else { decB = add(decB, field(L[i], "action")); srcB = add(srcB, field(L[i], "source")) }
      rsl = add(rsl, field(L[i], "routeSeen"))
    } else if (K[i] == "REQ") {
      id = (L[i] ~ /VID: 18D1/) ? "B" : "A"; if (id == "A") reqA++; else reqB++
    } else if (K[i] ~ /^(CONFIRM|PERM|RESOLVER)_START$/) {
      best = -1e18
      for (j = 1; j <= n; j++) if (K[j] ~ /^ATTACH_/ && T[j] <= T[i] + 500 && T[j] > best) { best = T[j]; id = substr(K[j], 8) }
      ty = substr(K[i], 1, index(K[i], "_") - 1); q[ty, ++qe[ty]] = i; FIN[i] = -1; C[ty "_" id]++
    } else if (K[i] ~ /_FINISH$/) {
      ty = substr(K[i], 1, index(K[i], "_") - 1)
      if (qs[ty] < qe[ty]) { s = q[ty, ++qs[ty]]; FIN[s] = T[i]; id = ID[s] }
    } else if (K[i] == "ATTACH_B" && tb < 0) tb = T[i]
    else if (K[i] == "SSL") ssl++
    else if (K[i] == "MS") { ms_n++; if (tms < 0) tms = T[i] }
    else if (K[i] == "MAIN_START") mains++
    else if (K[i] == "RAISE") { id = src(L[i]); if (id == "A") raiseA++; else raiseB++ }
    else if (K[i] == "HOLDRAISE") { id = src(L[i]); if (id == "A") holdA++; else holdB++ }
    else if (K[i] == "AUTOSTART") { auto++; if (tauto < 0) tauto = T[i] }
    else if (K[i] == "REFUSE") refuse++
    else if (K[i] == "RX_CHECK") rx++
    else if (K[i] == "FATAL") fatal++
    ID[i] = id
    printf "%s\t%s\t%s\t%s\n", TS[i], K[i], id, L[i]
  }
  open_ms = unfinished = covered = gotA = 0; banswer = "none"; finA = -1
  for (i = 1; i <= n; i++) {
    if (K[i] == "PERM_START")
      for (j = 1; j <= n; j++) if (K[j] == "CONFIRM_START" && ID[j] == ID[i] && (T[i] - T[j] <= 2000 && T[j] - T[i] <= 2000)) { stacked++; break }
    if (K[i] ~ /^(CONFIRM|PERM|RESOLVER)_START$/) {
      if (FIN[i] < 0) unfinished++
      if (FIN[i] < 0 || (tms >= 0 && FIN[i] > tms)) open_ms++
      if (K[i] == "CONFIRM_START" && ID[i] == "B" && banswer == "none" && tb >= 0) banswer = (FIN[i] < 0) ? -1 : int(FIN[i] - tb)
    }
    if (K[i] ~ /^(CONFIRM|RESOLVER)_START$/) {
      e = (FIN[i] < 0) ? 1e18 : FIN[i]
      for (j = 1; j <= n; j++) if (K[j] == "MAIN_START" && T[j] > T[i] && T[j] < e) covered++
    }
    if (K[i] == "CONFIRM_START" && ID[i] == "A" && !gotA) { gotA = 1; finA = FIN[i] }
  }
  a2auto = (gotA && finA >= 0 && tauto >= 0) ? int(tauto - finA) : "none"
  printf "SUMMARY\t%s\tconfirm_A=%d\tconfirm_B=%d\tperm_A=%d\tperm_B=%d\tresolver=%d\tstacked=%d\treq_A=%d\treq_B=%d\tdec_A=%s\tdec_B=%s\tsrc_A=%s\tsrc_B=%s\tmtp_A=%s\trs=%s\tssl=%d\tms=%d\topen_at_ms=%d\tunfinished=%d\tb_answer_ms=%s\tmain_start=%d\tcovered=%d\traise_A=%d\traise_B=%d\thold_A=%d\thold_B=%d\tautostart=%d\ta_fin_to_auto_ms=%s\trefuse=%d\trx_check=%d\tfatal=%d\n", \
    P, C["CONFIRM_A"], C["CONFIRM_B"], C["PERM_A"], C["PERM_B"], C["RESOLVER_A"] + C["RESOLVER_B"], stacked, reqA, reqB, decA, decB, srcA, srcB, mtpA, rsl, ssl, ms_n, open_ms, unfinished, banswer, mains, covered, raiseA, raiseB, holdA, holdB, auto, a2auto, refuse, rx, fatal
}'
```

What the SUMMARY fields mean:

| Field | Meaning |
|---|---|
| `confirm_A`, `confirm_B`, `perm_A`, `perm_B`, `resolver` | `START u0` lines for `UsbConfirmActivity` (Android's dialog), `UsbPermissionActivity` (ours) and `UsbResolverActivity`. A start belongs to the latest app attach line at or before its time plus 500 ms |
| `stacked` | starts of ours within 2000 ms of a start of Android's for the same identity |
| `req_A`, `req_B` | `Requesting USB permission for ` lines; `VID: 18D1` is B |
| `dec_A`, `dec_B`, `src_A`, `src_B`, `mtp_A`, `rs` | the `action=`, `source=`, `mtp=` and `routeSeen=` values of the decision lines, in order; `-` is none |
| `open_at_ms` | dialog starts with no `*_finish_activity` before the first `Media Sink Setup Request: ` |
| `unfinished` | dialog starts with no finish in the window |
| `b_answer_ms` | from the first B attach line to the finish of Android's dialog for B; `none` when no such dialog, `-1` when it never finished |
| `main_start` | `MainActivity` starts in the window |
| `covered` | `MainActivity` starts after an Android dialog (`CONFIRM` or `RESOLVER`) started and before it finished. This is the F4 fault |
| `raise_A`, `raise_B` | `Reopen on reconnection: launching MainActivity` lines, by source: `(USB accessory attach)` is B, `(USB normal attach ...)` is A |
| `hold_A`, `hold_B` | `Reopen on reconnection: not raised over Android's USB dialog` lines, by the same source rule |
| `autostart`, `a_fin_to_auto_ms` | `USB auto-start: launching app for ` lines, and the time from the finish of A's first Android dialog to the first of them; `none` when either is missing |
| `refuse` | refused second asks, from either path (`... not opening a second`) |
| `rx_check` | `AutomationReceiver: ` lines that name `ACTION_CHECK_USB` |
| `fatal` | crash lines |

A START line's identity can be wrong when ours follows A's dialog by more than 500 ms less than the B attach does. Each
row prints the line beside the label, so the host checks any `perm_*` above 0 against the rows.

**`keys.sh`** (unchanged from round 2)
```bash
# setkeys TAG KEY...: write the keys with the app stopped, launch, and wait up to 20 s for
# UsbAttachedActivity to follow listen-for-usb-devices. A force-stop: on D-HP it loses the route verdict.
setkeys() { local tag=$1; shift; local want=enabled
  case " $* " in *" bool:listen-for-usb-devices=false "*) want=disabled;; esac
  A shell am force-stop $PKG
  writeprefs "$@" || { echo "$ST $tag WRITE_FAILED" >> $OUT/marks.txt; return 1; }
  launch; local t=0; while [ $t -lt 20 ]; do [ "$(comp)" = $want ] && break; sleep 2; t=$((t+2)); done
  echo "$ST $tag comp=$(comp) want=$want pid=$(hupid)" | tee -a $OUT/marks.txt; [ "$(comp)" = $want ]; }
# cleardef TAG: open App info and cue CLEAR when a default of ours is stored; wait for the parsed count to reach 0 (cap 120 s)
cleardef() { usbdef $1-pre; [ "$DEFN" -eq 0 ] && { echo "$1 nothing to clear" >> $OUT/marks.txt; return 0; }
  A shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:$PKG >/dev/null
  cue "CLEAR $1: on $HUNAME, App info for Open Headunit is open. Press Clear defaults (API 35: Open by default, then Clear default preferences). Say so if there is no such button"
  local t=0 d; while [ $t -lt 120 ]; do sleep 3; t=$((t+3)); A shell dumpsys usb > $OUT/tmp-usb.txt 2>&1
    d=$(./usbparse.sh $OUT/tmp-usb.txt x | grep -ao 'defaults=[0-9]*' | cut -d= -f2); [ "$d" -eq 0 ] && break; done
  usbdef $1-post; A shell input keyevent 3; sleep 1; launch; }
```

**`cycle.sh`**
```bash
# cycle P KIND: one plug of D-MOTO into the host, answered by the run's rule (section 5), then the unplug.
# KIND: session | double (R5c) | look (F4a, F4b)
# Returns 0 graded, 1 retry, 2 low battery. One row per cycle goes to cycles.tsv.
cycle() { P=$1; local K=$2 rc=0 pid0
  [ "$ST" = P ] || [ "$(bat)" -ge 30 ] || { echo "$P BATTERY_LOW $(bat)" >> $OUT/marks.txt; return 2; }
  alive; pid0=$(hupid)
  adb connect $MOTO_W >/dev/null 2>&1; timeout 5 adb -s $MOTO_W logcat -c 2>/dev/null
  mark $P-cue; cue "PLUG $P: plug D-MOTO's cable into the OTG adapter on $HUNAME now"
  if ! waitfor $P-cue 'Normal USB device attached: ' 120; then echo "$P NO_PLUG" >> $OUT/marks.txt; rc=1
  else
    echo "$P plug=$(firstts $P-cue 'Normal USB device attached: ')" >> $OUT/marks.txt
    case $K in
      double) sleep 8; A shell "am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_CHECK_USB; am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_CHECK_USB" ;;   # 8 s: past the 2 s fallback and its hold
      look) sleep 15; focus $P; cue "LOOK $P: say if a USB dialog is visible on $HUNAME, then follow the F4 rule"; sleep 45 ;;   # 45 s: the operator's answer time
    esac
    if [ $rc -eq 0 ]; then
      { waitfor $P-cue 'SSL handshake complete' 150 && waitfor $P-cue 'Media Sink Setup Request: ' 30; } \
        || { echo "$P NO_SESSION" >> $OUT/marks.txt; [ $K = look ] || rc=1; }   # an F4 cycle with no session is graded, never retried
      [ $K = look ] && focus $P-session
      sleep 10                                                          # a late dialog would START in these 10 s
    fi
  fi
  mark $P-end; cue "UNPLUG $P: unplug D-MOTO from $HUNAME"
  if waitfor $P-end 'USB_DEVICE_DETACHED' 60; then echo "$P unplug=$(firstts $P-end 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt
  else cue "UNPLUG $P again: D-MOTO is still plugged into $HUNAME. Unplug it now"
    if waitfor $P-end 'USB_DEVICE_DETACHED' 120; then echo "$P unplug=$(firstts $P-end 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt
    else echo "$P NO_UNPLUG_LINE" >> $OUT/marks.txt; fi
  fi
  mark $P-unplug; moto_dump $P; usbdef $P-after
  ./timeline.sh $(F) $P > $OUT/$P.timeline.tsv
  printf '%s\tpid=%s>%s\tphone_acc=%s\tafter=%s\t%s\n' "$P" "$pid0" "$(hupid)" \
    "$(grep -acF 'entering USB accessory mode' $OUT/$P-phone.txt 2>/dev/null)" \
    "$(tail -1 $OUT/defaults.tsv | cut -f2-3 | tr '\t' ' ')" "$(tail -1 $OUT/$P.timeline.tsv | cut -f3-)" >> $OUT/cycles.tsv
  return $rc; }
# valid RUN P: 0 when the cycle can be graded. After a good seed, R4a, F4a or F4b on routeSeen=no is void.
valid() { local s; s=$(tail -1 $OUT/$2.timeline.tsv)
  case $1 in R4a|F4a|F4b) [ "${SEEDOK:-0}" = 1 ] && echo "$s" | grep -q 'rs=[a-z,]*no' \
    && { echo "$2 VOID routeSeen=no" >> $OUT/marks.txt; return 1; };; esac
  return 0; }
pre_R4a() { cleardef $P-clear; }
pre_F4a() { cleardef $P-clear; A shell input keyevent 3; sleep 1; }   # F4 starts with the app in the background
pre_F4b() { cleardef $P-clear; A shell input keyevent 3; sleep 1; }
pre_F4c() { A shell input keyevent 3; sleep 1; }
# runplugs RUN KIND N: N graded cycles, at most 2 retries (the stop rule). Returns 2 on low battery.
runplugs() { local run=$1 kind=$2 need=$3 good=0 bad=0 k=0 rc
  while [ $good -lt $need ] && [ $bad -lt 2 ]; do k=$((k+1)); while used $ST-$run-p$k; do k=$((k+1)); done
    P=$ST-$run-p$k
    type pre_$run >/dev/null 2>&1 && pre_$run
    cycle $P $kind; rc=$?; [ $rc -eq 2 ] && return 2
    [ $rc -eq 0 ] && { valid $run $P || rc=1; }
    if [ $rc -eq 0 ]; then good=$((good+1)); else bad=$((bad+1)); echo "$P RETRY" >> $OUT/marks.txt
      grep -q "^$P VOID routeSeen" $OUT/marks.txt && { seed; [ $? -eq 0 ] || SEEDOK=0; }; fi
  done
  echo "$ST-$run graded=$good retries=$bad" >> $OUT/marks.txt; return 0; }
# seed: plug until Android starts our activity for an attach once (at most 3 plugs). Labels never repeat.
seed() { local n=0 old="$RULE" L; RULE="$SEEDRULE"
  while [ $n -lt 3 ]; do n=$((n+1)); SEEDN=$(( ${SEEDN:-0} + 1 )); while used $ST-seed$SEEDN; do SEEDN=$((SEEDN+1)); done
    L=$ST-seed$SEEDN; cycle $L session; [ $? -eq 2 ] && { RULE="$old"; return 2; }
    if [ "$(since $L-cue | grep -aF 'UsbAttachedActivity' | grep -aF 'USB Intent: ' | grep -acF 'USB_DEVICE_ATTACHED')" -ge 1 ]; then
      echo "$ST seed OK at $L pid=$(hupid) recorded=$(cnt $L-cue 'Android starts this app for a USB attach in this boot (recorded)')" >> $OUT/marks.txt
      RULE="$old"; return 0; fi
  done; echo "$ST SEED_FAILED" >> $OUT/marks.txt; RULE="$old"; return 1; }
```

The diagnosis's log instrument, with three rows added for this round's lines. Run it on each run's slice. It is a second
reading only; `timeline.sh` decides.

**`usb-dialog-instrument.sh`**
```bash
#!/usr/bin/env bash
# Counts the USB permission landmarks in an OHU/HUR log or a logcat capture.
# Usage: usb-dialog-instrument.sh <log> [<log> ...]
# Prints counts and the first 5 line numbers per landmark. No verdict.
set -u
landmarks=(
  "attach routed by Android to our activity|UsbAttachedActivity.onCreate \| USB Intent: "
  "attach or detach broadcast (receiver)|UsbReceiver.onReceive \| USB Intent: "
  "activity: accessory, no permission, hands to service|UsbAttachedActivity: accessory mode without permission, handing it to the service"
  "activity: accessory, has permission|Usb in accessory mode and has permission"
  "activity: launches the app (Start on USB)|USB auto-start: launching app for "
  "service: raise skipped over Android's dialog|Reopen on reconnection: not raised over Android.s USB dialog"
  "service: raise|Reopen on reconnection: launching MainActivity"
  "service: 2 s fallback ran (activity did not act)|UsbAttachedActivity didn't handle"
  "service: normal-mode device, no permission, asks|Single USB auto-connect: device found but no permission"
  "service: last-session device, no permission, asks|Found known USB device but no permission"
  "service: accessory grant arrived during the poll|Accessory-mode permission arrived after"
  "service: accessory poll expired, asks|Accessory-mode device has no permission \\(re-enumerated\\)"
  "service: permission request sent|Requesting USB permission for"
  "second ask refused|a dialog for .*not opening a second"
  "service: permission reply|USB permission (granted|denied) for"
  "system: confirm dialog (manifest route)|UsbConfirmActivity"
  "system: permission dialog (requestPermission)|UsbPermissionActivity"
  "system: chooser (several apps match)|UsbResolverActivity"
  "system: MTP notification instead of a dialog|MtpNotificationManager|showNotification.*Mtp"
)
for log in "$@"; do
  echo "=== $log ($(wc -l < "$log") lines)"
  for entry in "${landmarks[@]}"; do
    label=${entry%%|*}; pattern=${entry#*|}
    n=$(grep -acE -- "$pattern" "$log")
    lines=$(grep -anE -- "$pattern" "$log" | head -5 | cut -d: -f1 | paste -sd, -)
    printf '%4d  %-52s %s\n' "$n" "$label" "$lines"
  done
done
```

## 7. The deciding lines

All app strings print at INFO. Each was verified with `grep -F -r` in `app/src/main` at `9f90e121`, except the ones
marked "composed", whose parts the source joins at run time. Each is graded in `<ST>-hu.txt`, between `<P>-cue` and
`<P>-unplug` for one cycle, through `timeline.sh`.

| Id | Line (fixed substring) | Read |
|---|---|---|
| L-RX | `AutomationReceiver: ` | a verb landed; with `ACTION_CHECK_USB` on the line it is `RX_CHECK`. A step with no line is void, not a FAIL |
| L-MK | `AutomationMarker: ` | the window bounds |
| L-HOLD | `Reopen on reconnection: not raised over Android's USB dialog (` | **new.** The raise was skipped for that attach. Source (composed): `USB accessory attach` is B, `USB normal attach (` is A |
| L-RAISE | `Reopen on reconnection: launching MainActivity (` | the service raised `MainActivity` for that attach, same source rule |
| L-AUTO | `USB auto-start: launching app for ` | our activity launched the app after Android started it |
| L-REF | `UsbLauncher: a dialog for ` ... ` is already open; not opening a second` | **new.** The service refused a second ask (composed) |
| L-REFUI | `UsbPermissionAsks: a dialog for ` ... `ms ago; not opening a second` | **new.** The UI path refused a second ask (composed). Not expected this round |
| L-DEC | `UsbLauncher: permission for ` | one decision. Fields (composed): `identity=normal`/`identity=accessory`, `mtp=`, `filter=`, `activity=`, `listen=`, `routeSeen=`, `attach=`, `source=`, `action=` (`ask`, `wait`, `asked`, `hold`). It prints before the refusal check, so a refused ask still prints `action=ask` |
| L-WAITB | `Accessory-mode device has no permission yet; Android shows its own dialog for it: ` | the poll for B ran out and the service waits |
| L-ASKB | `Accessory-mode device has no permission (re-enumerated); requesting permission: ` | the poll for B ran out and the service asks |
| L-REQ | `Requesting USB permission for ` | every `requestPermission`. The name carries `VID: <hex> PID: <hex>` (composed) |
| L-ROUTE | `Android starts this app for a USB attach in this boot (recorded)` | API 24 and up only, once per boot change |
| L-NORM | `Normal USB device attached: ` | the plug time, and identity A |
| L-ACC | `USB accessory device attached, connecting.` | identity B |
| L-INT | `USB Intent: ` | with `USB_DEVICE_ATTACHED` and `UsbAttachedActivity` on one line: Android started our activity for an attach (the seed check). With `USB_DEVICE_DETACHED`: the unplug time |
| L-FALL | `UsbAttachedActivity didn't handle ` | the 2 s fallback ran for A |
| L-ACTOK | `Usb in accessory mode and has permission. Starting AapService.` | B reached our activity with a grant |
| L-SW | `Switching USB device to accessory mode ` | a switch of A |
| L-SSL | `SSL handshake complete` | a session formed (once per session at INFO) |
| L-MS | `Media Sink Setup Request: ` | the phone answered our service discovery |
| D-MATCH | `MATCH! Starting AapService` | discard rule (template §4) |

System strings, not in `app/src`: `START u0`, `.usb.UsbConfirmActivity`, `.usb.UsbPermissionActivity`,
`.usb.UsbResolverActivity`, `_finish_activity` (`am_` on API 17, `wm_` on API 35), `_create_activity`,
`openheadunit.main.MainActivity` (the component in a `START` line), `mCurrentFocus`, `topResumedActivity`,
`mResumedActivity`, `Device preferences`, `device_preferences`. Phone string, from AOSP on D-MOTO:
`entering USB accessory mode`. **FAIL lines, every cycle:** `FATAL EXCEPTION` in a block that names `com.andrerinas`,
`ANR in com.andrerinas`, and `NoClassDefFoundError` (D-HP runs API 17).

The files the runs write:

| File | One row per | Fields |
|---|---|---|
| `cycles.tsv` | plug | label, `pid=<before>><after>`, `phone_acc=`, `after=defaults=N pairs=...` (read after the unplug), then the SUMMARY fields |
| `<P>.timeline.tsv` | decisive line | time, kind, identity, the whole line; last row SUMMARY |
| `defaults.tsv` | `dumpsys usb` read | tag, `defaults=`, `pairs=`, `sections=`, `serial_outside=` |
| `marks.txt` | event | markers with host time, `plug=` and `unplug=` with logcat time, retries, voids, seeds |
| `focus.txt` | F4a, F4b read | `mCurrentFocus`, `mFocusedApp`, `topResumedActivity` or `mResumedActivity` |
| `<P>-phone.txt` | plug | D-MOTO's buffer from before the plug to after the unplug |

## 8. Runs

Each stage is one capture with all runs chained. Cycle labels are `<ST>-<run>-p<k>`, seeds `<ST>-seed<n>`, and no label
runs twice.

**Stop rule.** A cycle with `NO_PLUG`, `NO_SESSION` where a session is expected, or a void is retried once with the
next `k`. Two such cycles in one run end the run, graded on what it has (INCONCLUSIVE with 0 graded cycles). An F4a or
F4b cycle with no session is graded, never retried: a missing session is the F4 fault. `BATTERY_LOW` ends the stage.
`SEED_FAILED` makes R4a INCONCLUSIVE and skips it, and makes the F4a and F4b dialog conditions (section 8, F4 items 4
and 5) INCONCLUSIVE. The raise conditions are still graded, because the F4 fix does not read `routeSeen`.

### Prepare (15 min)

1. `get_apk.sh` (section 1). Charge D-HP to 100%.
2. `$RIG_TOOLKIT/rig_preflight.sh D_HP:wifi D_POCO:wifi D_MOTO:wifi`. Paste the table into Setup notes.
3. On D-HP and D-POCO, with the app stopped: record the live APK md5, save
   `dumpsys usb > $OUT/prep-<unit>-usb.txt`, and install `apk-C.apk` with `adb install -r -d`. Run the identity check
   (section 1). Record `getprop ro.build.version.sdk` and `date` on each unit beside the host's `date`, and D-MOTO's
   offset the same way.
4. D-POCO: `am start -a android.intent.action.VIEW -d headunit://exit`, then force-stop its app (it is a phone in other
   rounds; template §8 role-swap rule). Record `appops get $PKG SYSTEM_ALERT_WINDOW`. F4's raise on API 35 goes
   through the overlay trampoline, so `allow` is expected (round 2 read `allow`).
5. Wireless adb, each while still on its PC cable: `adb -s <serial> tcpip 5555`, read `ip -4 addr show wlan0`,
   `adb connect <ip>:5555`, and write `HP_W`, `POCO_W`, `MOTO_W` to `$OUT/scripts/wireless.env`.
6. D-MOTO: `svc bluetooth disable` and read it back (`dumpsys bluetooth_manager`); WiFi stays on. Read
   `settings get global stay_on_while_plugged_in`, record it, then `svc power stayon true`. Record Gearhead's
   `versionName` and `getprop sys.usb.config`. `logcat -G 16M`. Restore the stay-on value at the end of the round.
7. `chmod +x usbparse.sh timeline.sh usb-dialog-instrument.sh` in `$OUT/scripts/`.
8. D-POCO's lasting grant (section 3): with D-MOTO unplugged,
   `ST=P HU=$POCO_W HUNAME=D-POCO; . ./lib.sh; . ./wireless.env; . ./keys.sh; launch; cleardef P-prep; usbdef P-prep-grant`.
   Record `serial_outside=` on the last row of `defaults.tsv`.

### One stage (run it for H, then P)

Run `ST=H ./stage3.sh` for stage H, then `ST=P ./stage3.sh` for stage P.

**`stage3.sh`**
```bash
#!/usr/bin/env bash
. ./lib.sh; . ./wireless.env; . ./keys.sh; . ./cycle.sh
case "$ST" in H) HU=$HP_W; HUNAME=D-HP;; P) HU=$POCO_W; HUNAME=D-POCO;; *) echo "run as ST=H or ST=P"; exit 1;; esac
export ST HU HUNAME; RULE=; SEEDOK=0
cue "CAB $ST: move the cables for stage $ST (brief section 5). D-MOTO stays unplugged"
adb connect $HU >/dev/null; adb connect $MOTO_W >/dev/null
[ "$ST" = P ] || [ "$(bat)" -ge 60 ] || { echo "$ST BATTERY_START $(bat)" >> $OUT/marks.txt; exit 1; }
th_gate || exit 1
echo "$ST stay_on_before=$(A shell settings get global stay_on_while_plugged_in | tr -d '\r')" >> $OUT/marks.txt
A shell svc power stayon true; A shell am force-stop $PKG
A shell run-as $PKG cat shared_prefs/settings.xml > $OUT/backup-$ST.xml
writeprefs $BASEKEYS del:usb-attach-route-boot del:video-profile-starvation-cap || exit 1
S0=$ST-start-$(date +%H%M%S); cap; mark $S0; launch; sleep 3
if [ "$(since $S0 | grep -aF 'START u0' | grep -acF 'MainActivity')" -lt 1 ]; then
  echo "$ST NO_START_LINES: every timeline.sh call runs with CREATE=1" >> $OUT/marks.txt; export CREATE=1; fi
usbdef $ST-start
run() { RULE="$RULES"; cue "NEXT $ST-$1: read the rule, nothing starts for 12 s"; sleep 12
  mark $ST-$1-start; runplugs "$@"; local rc=$?; mark $ST-$1-end; RULE=
  cue "COUNT $ST-$1: tell the host, per plug, how many USB dialogs you saw and the first word of each (25 s)"; sleep 25; return $rc; }

RULES=""; if setkeys listen-off bool:listen-for-usb-devices=false bool:auto-start-on-usb=false; then
  RULES="Press OK on every dialog. Tick nothing."; run R5c double 1 || exit 2; fi

RULES=""; setkeys f4 bool:listen-for-usb-devices=true bool:auto-start-on-usb=true || { echo "$ST F4 UNTESTABLE settings" >> $OUT/marks.txt; }
RULE="$SEEDRULE"; cue "NEXT $ST-seed: $SEEDRULE"; sleep 12; RULE=
seed; SRC=$?; [ $SRC -eq 2 ] && exit 2; [ $SRC -eq 0 ] && SEEDOK=1
RULES="Touch nothing until LOOK. At LOOK say if a USB dialog is visible. Then answer each: Open = tick, then OK. Allow = Cancel. None visible: say so, press Recents once, answer there. Never Back or Home."
run F4a look 1 || exit 2; run F4b look 1 || exit 2
RULES="Touch nothing. Remember any dialog you see."; run F4c session 1 || exit 2

if [ "$ST" = P ]; then
  RULES=""; if setkeys listen-on bool:listen-for-usb-devices=true bool:auto-start-on-usb=false && [ $SEEDOK = 1 ]; then
    RULES="Tick the box if there is one, then press OK, as soon as a dialog shows."; run R4a session 1 || exit 2
  else echo "P R4a skipped (settings or SEED_FAILED)" >> $OUT/marks.txt; fi
fi

cleardef $ST-end-clear; mark $ST-end
send ACTION_EXPORT_LOG; sleep 5                               # the export path is on the data= line; pull that file
th_end; uncap; A shell am force-stop $PKG
for r in R5c F4a F4b F4c R4a; do
  tr -d '\r' < $(F) | sed -n "/AutomationMarker: $ST-$r-start\$/,/AutomationMarker: $ST-$r-end\$/p" > $OUT/$ST-$r.slice
  [ -s $OUT/$ST-$r.slice ] && ./usb-dialog-instrument.sh $OUT/$ST-$r.slice > $OUT/$ST-$r.instrument.txt; done
# restore backup-$ST.xml with the app stopped (owner and mode as tooling.md says), diff it, then:
# A shell settings put global stay_on_while_plugged_in <stay_on_before from marks.txt>
cue "CAB $ST: stage $ST is over. Move the cables back"
```

A failed `setkeys` skips the runs under it: they are UNTESTABLE (settings). Low battery stops the stage the same way,
and the skipped runs are UNTESTABLE (battery). Grade each run from `cycles.tsv` and the run's `<P>.timeline.tsv` rows.
Quote every `action=` and `source=` from the timeline rows, which now carry the whole line. The operator's COUNT goes
beside `confirm_*` plus `perm_*` as a second column. A disagreement is a finding, not a verdict, except in F4 item 6.

### R5c, Listen off, a double press

After `setkeys listen-off` (`comp=disabled` in `marks.txt`, else R5c is UNTESTABLE). One cycle. 8 s after the plug,
two `ACTION_CHECK_USB` broadcasts in one adb shell call. Rule: OK, no tick.

R5c PASS needs:

1. `rx_check=2`. Record the gap between the two `RX_CHECK` rows.
2. `req_A=1` and `perm_A=1`: one request and one dialog for A.
3. `refuse` at least 1, and at least one `REFUSE` row that reads `UsbLauncher: a dialog for ` ... ` is already open;
   not opening a second`.
4. `req_B=1`, `perm_B=1`, and `ask` with `automatic` in `dec_B` and `src_B` (our own switch less than 10 s before).
5. `confirm_A=0`, `confirm_B=0`, `unfinished=0`.
6. `ssl` at least 1, `ms` at least 1. Phone: `phone_acc` at least 1.
7. `fatal=0`.

Expected `dec_A`: `hold,ask,ask`. The second `ask` is the refused one, because the decision line prints before the
refusal check. If the change did nothing, round 2's reading repeats: `req_A=2`, `perm_A=2`, `refuse=0`,
`unfinished=1`. R5c FAIL if item 2, 3 or 5 fails while `dec_A` holds two `ask` values. If `dec_A` holds only one `ask`,
the second broadcast never reached the decision: grade items 2 and 3 INCONCLUSIVE and quote the rows.

### seed (no verdict)

After `setkeys f4`. Rule: "Open": tick, then OK, at once. "Allow": Cancel. `marks.txt` MUST then read `<ST> seed OK`.
On stage P, also record `recorded=` (1 or more expected, since the stage deleted `usb-attach-route-boot`). Our dialog
for A can still stack on a seed plug, because `routeSeen` is still `no` at its 2 s decision. That is why the rule
cancels "Allow" dialogs. A ticked "Open" stores a default, so a second seed plug starts our activity with no dialog.

### F4a and F4b, Start on USB, no defaults, first connect: the point of the round

After the seed. One cycle each, each after a `cleardef`, with the app sent to the background before the plug. F4b
repeats F4a, so one odd cycle cannot decide the run. Rule: the F4 row of section 5. The script records `focus.txt`
15 s after the plug and after the session, cues LOOK, and waits 45 s for the answers.

Setup checks: `marks.txt` shows `seed OK` for this stage. Each `<P>-clear-post` row reads `defaults=0`. On stage H, the
`pid=` before matches the seed's pid; a changed pid is a lost route, and the cycle is void when `rs` then reads `no`.

F4a PASS and F4b PASS each need, for the graded cycle:

1. `hold_A` at least 1 and `raise_A=0`: the raise for A was skipped.
2. For B, either `hold_B` at least 1 and `raise_B=0`, or `confirm_B=0`: B had a grant at its attach, so no Android
   dialog was there to cover. Quote the B rows either way.
3. `covered=0`: no `MainActivity` start while an Android dialog was open.
4. `perm_A=0`, `perm_B=0`, `resolver=0`, `req_A=0`, `req_B=0`, and every value in `dec_A` and `dec_B` is `wait` or
   `asked` (an empty one is fine). INCONCLUSIVE on a stage with `SEED_FAILED`.
5. `confirm_A + confirm_B` at most 2: at most 2 dialogs per plug, all Android's. INCONCLUSIVE with `SEED_FAILED`.
6. The operator says at LOOK that a USB dialog starting "Open" is visible, with no Recents press.
7. `autostart` at least 1, and `a_fin_to_auto_ms` is 0 or more: the app came up after the answer, not before.
8. `ssl` at least 1, `ms` at least 1, `open_at_ms=0`. Phone: `phone_acc` at least 1.
9. `after=` lists both A (`8888/...`) and B (`6353/11521`) in `pairs=`: the ticks stored both defaults.
10. `fatal=0`.

If the change did nothing, round 2's reading repeats: `RAISE` and `MAIN_START` 26 to 80 ms after `CONFIRM_START` for A,
so `raise_A` at least 1, `hold_A=0`, `covered` at least 1, the operator sees nothing, and `ssl=0`.

F4a or F4b FAIL if item 1, 2, 3 or 6 fails, or if item 4 or 5 fails on a stage whose seed was OK. A cycle that misses
only item 7, 8 or 9 is reported with its rows, and the run is FAIL only if the host shows the code caused it. Record
`focus.txt` at both reads beside the `START` order, and say if they disagree.

### F4c, Start on USB, defaults stored (the control)

One cycle after F4b, with no clear, so F4b's defaults are in force. The app is sent to the background before the plug.
Rule: touch nothing.

F4c PASS needs: 0 dialog starts (`confirm_*`, `perm_*`, `resolver` all 0), `req_A=0`, `req_B=0`, `main_start` at least
1, `raise_A + raise_B + autostart` at least 1, `ssl` at least 1, `ms` at least 1, `phone_acc` at least 1, and `fatal=0`.
Record `raise_A`, `raise_B`, `hold_A`, `hold_B` and `autostart` separately.

The plan expects the raise (`raise_A` at least 1) here, because the device has a grant. But Android sends the
attach broadcast before it grants a stored default and starts our activity. So the service can still read "no
permission" and print `hold_A`. Then `UsbAttachedActivity` launches the app (`autostart`) and the user sees no
difference. Report which path brought the app up. A `hold_A` here is a finding, not a FAIL. F4c FAIL if any dialog
starts, or if `main_start=0`.

### R4a, Listen on, no defaults, first connect (stage P only, regression check)

After `setkeys listen-on`. One cycle after a `cleardef`. Rule: tick and OK every dialog at once. Skipped on
`SEED_FAILED`. D-POCO keeps the route verdict across the force-stop (it is stored with the boot count).

R4a PASS needs:

1. `stacked=0`.
2. `req_B=0`, and every value in `dec_B` is `wait` or `asked`. An empty `dec_B` is fine: an answer inside B's 1 s
   poll leaves no decision (`Accessory-mode permission arrived after` instead).
3. `req_A` at most 1. If `mtp_A` lists only `no` (or `-`): `perm_A=0`, `req_A=0`, and every value in `dec_A` is
   `wait` or `asked`. If `mtp_A` lists `yes` (API 35 MTP rule): `confirm_A=0`, `perm_A=1`, `req_A=1`.
4. At most 1 dialog start per identity: `confirm_A + perm_A` at most 1, and `confirm_B + perm_B` at most 1.
5. `open_at_ms=0`: every dialog finished before the first `Media Sink Setup Request: `.
6. `ssl` at least 1 and `ms` at least 1. Phone: `phone_acc` at least 1.
7. `after=` lists both A (`8888/...`) and B (`6353/11521`) in `pairs=`.
8. `fatal=0`.

Expected with `mtp=no`: `confirm_A=1`, `confirm_B=1`, `perm_A=0`, `perm_B=0`, `req_A=0`, `req_B=0`, as in round 2 on
all six cycles. R4a FAIL if the cycle misses item 1, 2, 3, 4 or 8. A cycle that misses only 5, 6 or 7 is reported with
its rows, and the run is FAIL only if the host shows the code caused it.

## 9. Do not re-run

Round 1 settled `main`: two of our dialogs per plug with Listen off (`R-H1`, `R-P1`), the stacked pair and 3 or 4
dialogs on a first connect with Listen on (`R-H2`, `R-P2`), and the tick-everything control (`R-H3`, `R-P3`). Do not
build `main` again. D-SAM has no USB host: do not try it.

Round 2 settled these on `63f20a8f`, on both hosts. Neither new change touches their paths, so this round does not
repeat them (R4a on D-POCO is the one check):

| Run | Round 2 verdict |
|---|---|
| R4a on D-HP | PASS (3 cycles: `stacked=0`, `req=0`, 2 Android dialogs per plug) |
| R4b | PASS (B left with Android's dialog for 24.6 s and 15.2 s) |
| R4c | PASS (6 replugs, 0 dialogs) |
| R5a | PASS (`action=hold`, 0 dialogs) |
| R5b | PASS (`ACTION_CHECK_USB` asks for A as `source=user`) |
| R5t | PASS, narrower (the tap hit "Connect over USB", which also calls `onUsbClicked`) |

## 10. Report back

1. F4a and F4b per host: `hold_A`, `raise_A`, `hold_B`, `raise_B`, `covered`, the operator's LOOK answer, `autostart`
   and `ssl`. Could the operator see and answer Android's dialog, and did a session follow?
2. R5c per host: `rx_check` with the gap, `req_A`, `perm_A`, `refuse` with its row, and `unfinished`. Did the double
   press open one dialog for A?
3. F4c per host: which path brought the app up (`raise_*` or `autostart`), and any `hold_A`.

Also: R4a's row on D-POCO, every void and its reason, every cycle whose pid changed, `serial_outside` on D-POCO before
the round, D-MOTO's `mtp=` per host, and any `perm_*` above 0 with its timeline rows.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
ACTION_CHECK_USB
Reopen on reconnection: not raised over Android's USB dialog (
Reopen on reconnection: launching MainActivity (
USB accessory attach
USB normal attach (
USB auto-start: launching app for 
UsbLauncher: a dialog for 
 is already open; not opening a second
UsbPermissionAsks: a dialog for 
ms ago; not opening a second
UsbLauncher: permission for 
identity=accessory
identity=normal
mtp=
routeSeen=
source=
action=
Accessory-mode device has no permission yet; Android shows its own dialog for it: 
Accessory-mode device has no permission (re-enumerated); requesting permission: 
Requesting USB permission for 
VID: 18D1
Android starts this app for a USB attach in this boot (recorded)
Normal USB device attached: 
USB accessory device attached, connecting.
USB Intent: 
USB_DEVICE_ATTACHED
USB_DEVICE_DETACHED
UsbAttachedActivity
UsbAttachedActivity.onCreate | USB Intent: 
UsbReceiver.onReceive | USB Intent: 
UsbAttachedActivity: accessory mode without permission, handing it to the service
UsbAttachedActivity didn't handle 
Single USB auto-connect: device found but no permission
Found known USB device but no permission
Accessory-mode permission arrived after
Usb in accessory mode and has permission. Starting AapService.
Switching USB device to accessory mode 
USB permission granted for 
USB permission denied for 
SSL handshake complete
Media Sink Setup Request: 
MATCH! Starting AapService
FATAL EXCEPTION
ANR in com.andrerinas
NoClassDefFoundError
START u0
.usb.UsbConfirmActivity
.usb.UsbPermissionActivity
.usb.UsbResolverActivity
_finish_activity
_create_activity
openheadunit.main.MainActivity
mCurrentFocus
topResumedActivity
mResumedActivity
Device preferences
device_preferences
MtpNotificationManager
entering USB accessory mode
```
