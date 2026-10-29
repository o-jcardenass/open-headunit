# usb-dialog, round 2 brief: one USB permission dialog per identity

Published name: `usb-dialog-round2-brief.md`. Results go in `usb-dialog-round2-results.md`.
Evidence: upload `usb-dialog-round2-captures.zip` to the existing release `rig-evidence-usb-dialog`.

Units: D-HP (API 17) and D-POCO (API 35) as USB hosts, one at a time. D-MOTO is the wired phone in both stages. D-HU
and D-SAM take no part (D-SAM has no USB host, round 1).

Queue line: `usb-dialog-round2-brief.md` (one arm, `fork/fix/usb-permission-dialog` `63f20a8f`; units D-HP, D-POCO,
D-MOTO; R4a is the point).

Pairing: possible only with a round that uses D-HU alone. If this round is the second of a pair, it takes the lock
`/tmp/ohu-rig-usbdlg.lock`. Its hand steps are many and short, so the other round must have none during this one.

Hand steps: cable moves, one plug and one unplug per cycle, every USB system dialog by a fixed rule per run (section 5),
Clear defaults when cued, one count per run. Injected taps: 1 per R5t cycle, at most 2 per stage.

**Time budget.** Build 25 min, Prepare 20 min, stage H 55 min, stage P 55 min, cable moves 10 min, thermal waits about
10 min. **About 2 h 55 min.** Write the results file after every run.

| Stage | Host | Runs | Min |
|---|---|---|---|
| H | D-HP (API 17), wireless adb, D-MOTO on its OTG port | seed, R4a, R4b, R4c, R5a, R5b, R5t, R5c, F4a, F4b in one capture | 55 |
| P | D-POCO (API 35), wireless adb, D-MOTO on its OTG port | the same | 55 |

**R4a is the point of the round.** R4b tests the second request for the accessory identity. R4c is the positive control.
R5a to R5c test "Listen for USB Devices" off. F4 is a report, not a verdict.

## 1. Build and baseline

One arm. The baseline is round 1, which measured `main` `947b3ae9`. No USB file changed from there to `main` `2018d8f09`,
the base of the candidate, so this round builds no baseline and cites round 1 for every "before" number.

| Ref | Commit | Tree |
|---|---|---|
| `fork/fix/usb-permission-dialog` | `63f20a8fc83366e386ef29c746f846bf5794137e` | `6aa22f1ec1ee1e80d0cc48f06e85bda1b3b2a6b6` |

The branch is 3 commits, fixups folded, on `main` `2018d8f09`. It is new on the fork, so no history was rewritten. The dev
host suite at this commit: 4318 JVM tests, 0 failures. Do not run the unit tests on the rig. No other queued round
shares this base that this brief knows of; check the README Queue for one on `main` `2018d8f09` and build it in the
same worktree if there is.

**`get_apk.sh`**
```bash
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round2; mkdir -p $OUT
WT=$($RIG_TOOLKIT/wt-new.sh usb-dialog)            # usage in TOOLS.md; record the path in Setup notes
git -C $WT fetch fork fix/usb-permission-dialog
git -C $WT checkout --detach 63f20a8fc83366e386ef29c746f846bf5794137e
git -C $WT rev-parse 'HEAD^{tree}'                 # MUST print 6aa22f1ec1ee1e80d0cc48f06e85bda1b3b2a6b6
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
cp rig-data/apks/*.apk $OUT/apk-C.apk               # at once: build_hur.sh deletes the previous APK
md5sum $OUT/apk-C.apk                                # record in Setup notes
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF UsbPermissionAskPolicy   # MUST be 1 or more
```

`UsbPermissionAskPolicy` is a class the candidate adds; round 1's APK cannot carry it.

**Identity on each host, after the install:** `send ACTION_QUERY_STATE` and its `data=` line carries `63f20a8f`, and
the md5 of the pulled live APK matches `apk-C.apk`. Install with `adb install -r -d`. Never uninstall, except the one
D-POCO case in section 3 that the operator accepts.

## 2. What this is and why

Two reports describe a USB permission dialog on every connect, often two of them, and a "Use by default" tick that is
not remembered. Round 1 measured `main` on D-HP and D-POCO and confirmed two mechanisms:

- **M1, Listen off.** "Listen for USB Devices" off disables `UsbAttachedActivity`. The service then asks on every plug,
  once for the phone's normal identity (A) and once for its accessory identity (B, `18D1:2D01`). Two dialogs per plug
  on both units (`R-H1`, `R-P1`), and a tick cannot stop them.
- **M2, Listen on.** Android shows its own confirm dialog (`UsbConfirmActivity`), and the service stacks its own
  `UsbPermissionActivity` on top 1 s (B) or 2 s (A) later. A first connect showed 3 or 4 dialogs. On D-POCO the service
  asked twice for B. A stored default is per identity, so a missed tick on one leaves a dialog on every connect.

The candidate changes three things:

1. **The service asks only where Android shows no dialog of its own.** Android shows one when our attach activity is
   on, our filter matches, and the MTP rule (API 24 and up) does not apply. The service then waits (`action=wait`). It
   waits only when Android has started our attach activity for an attach in this boot (`routeSeen=yes`) and the path
   attached less than 30 s ago. Below API 24 no boot count exists, so only a sighting in this process counts. One
   request per bus path at a time (`action=asked` for a second automatic ask).
2. **Listen off means no prompt on attach** (`action=hold`). The USB button, the USB list, the automation verbs and
   `headunit://connect` still ask (`source=user`). The accessory identity that follows our own switch in the last 10 s
   is still asked for, or the button could never finish a connect.
3. **Each decision prints one INFO line**, `UsbLauncher: permission for <name> at <path>: identity=... mtp=...
   filter=... activity=... listen=... routeSeen=... attach=... source=... action=...`. It prints only when the
   device has no grant at the moment of the decision.

## 3. What is different about this round

- **Round 1's scripts cost 40 minutes. This round removes every cause:**
  - No step waits on `dumpsys window windows`: it lists dialogs hidden behind the projection. Dialog counts come from
    the activity manager `START u0` lines and the events buffer `*_finish_activity` lines in the host logcat.
  - No snapshot sits between a dialog and its answer. The operator answers by a fixed rule per run (section 5), so
    nothing can beat the script.
  - No plug marker comes from a sysfs poll. The plug time is the logcat time of the app's `Normal USB device attached: `
    line, and the unplug time that of the `USB_DEVICE_DETACHED` intent line. The markers around them only bound the
    window, and both land before the plug and after the unplug.
  - `mCurrentFocus` decides nothing. F4 records it beside the `START` order, because API 35 left it stale in round 1.
  - Defaults come only from the `Device preferences` block (API 17) and the `device_preferences` blocks (API 35),
    parsed by `usbparse.sh` (section 6). Round 1's parser counted API 17's `Device permissions`, which lists live
    grants, and read nothing on API 35.
- **The route verdict needs a seed plug, and D-HP keeps it only in memory.** With `routeSeen=no` the service asks as
  `main` does, so R4 grades nothing until Android has started our activity for one attach. The stage seeds it
  (`seed`, section 8). On D-POCO the verdict is stored with the boot count and survives a force-stop. On D-HP (API 17)
  it lives in the app process only, so a force-stop or a process death loses it. The scripts read the app pid around
  every cycle. An R4 cycle that prints `routeSeen=no` is void and triggers a new seed.
- **D-POCO may still hold a lasting grant for D-MOTO.** Round 1 found 3 persistent entries for D-MOTO's serial before
  stage P, and 0 after its last Clear defaults. Prepare reads `dumpsys usb` on D-POCO again with D-MOTO unplugged.
  - What a lasting grant changes: that identity has a grant at the attach, so the service makes no decision and asks
    nothing for it. Android still shows its confirm dialog while no default is stored, and a session can form under
    that open dialog (round 1 `R-P3`, q3 and q4). So for that identity, "no stacked dialog" holds without the code
    doing anything, `action=wait` cannot appear, and R5a can form a session with no dialog.
  - What the operator must clear first: the stored defaults, on D-POCO's App info screen for Open Headunit (Open by
    default, then Clear default preferences). Prepare cues this. No button clears a lasting grant. Only an uninstall
    does (template §7b).
  - If `serial_outside` is above 0 after that clear, ask the operator: "D-POCO still holds a lasting USB grant for
    D-MOTO. Uninstall and reinstall the app on D-POCO (template §7b) so R4 can measure the accessory identity?" On yes,
    run `reinstall_poco.sh` (section 8). On no, grade the affected identity's R4 and R5a conditions INCONCLUSIVE and
    say why.
- **D-MOTO keeps USB debugging on**, as in round 1, because its capture runs over wireless adb. Round 1 saw A as
  `22B8:2E81` (ADB, no MTP) on both hosts. Record `mtp=` from the decision lines: `mtp=yes` on D-POCO changes what
  R4a expects for A (section 8). Record D-MOTO's USB functions (`getprop sys.usb.config`).
- **D-MOTO's wireless adb drops in accessory mode.** The phone buffer is cleared before each plug and dumped after each
  unplug (`moto_dump`). Its Bluetooth stays off all round.
- **D-HP feeds D-MOTO over OTG and its battery is degraded.** Start stage H at 60% or more, read before every plug,
  stop the stage at 30%.
- **R4b and R5c run once per unit.** R4b needs a slow answer, and R5c a double press. One cycle each is what the plan
  asks for.
- **Expected INCONCLUSIVE:** any identity with a lasting grant on D-POCO (above). `Throughput over` is not graded on
  stage P (a phone host may hold the projection for milliseconds, round 1). F4 is REPORTED, never PASS or FAIL.
- **Thermal.** `rig-quirks/topics/tooling.md`: `rig_thermal.sh wait 75` before each stage (once more on exit 1),
  `rig_thermal.sh watch` during it, and the round stops as UNTESTABLE (host thermal) after a second failed wait.
- Read `rig-quirks/topics/tooling.md`, `units/D-HP.md`, `units/D-SAM-and-D-HP.md`, `units/D-POCO.md`, `units/D-MOTO.md`
  and template §7b before the first run.

## 4. Settings keys

Write with the app stopped, on each host, with `$RIG_TOOLKIT/set_prefs_runas_host.py` (neither host is rooted). Use
round 1's `writeprefs` function from `rounds/usb-dialog-round1/scripts/lib.sh`, which wraps that tool; copy it into
this round's `lib.sh` and say so in Setup notes. Back up `settings.xml` to `$OUT/backup-<ST>.xml` first, read every key
back after every write and after the install, and diff at the end with carriage returns stripped.

The base block, `BASEKEYS` in `lib.sh`:

```
int:log-level=2 int:onboarding-version=2 bool:allow-external-configuration=true
int:wifi-connection-mode=0 set:connection-modes=usb
bool:auto-start-on-usb=false bool:reopen-on-reconnection=true
bool:auto-connect-last-session=false bool:auto-connect-single-usb=false
set:allow-devices= bool:use-libusb=false bool:kill-on-disconnect=false bool:enable-floating-button=false
```

Delete `usb-attach-route-boot` and `video-profile-starvation-cap`, with the tool's delete form. Leave the audio keys as
they are (the rig's worst case is deliberate) and leave `screen-orientation` as found. Why the less obvious keys:

- `log-level=2` (INFO): every line this round greps prints at INFO, and INFO prints `SSL handshake complete` once per
  session.
- `allow-external-configuration=true`: `ACTION_EXPORT_LOG` is gated on it. `ACTION_LOG_MARKER` is not.
- The three auto-connect keys off and `allow-devices` empty put A on the 2 s fallback path, as in round 1.
- `usb-attach-route-boot` deleted: the seed must record the route in this stage.
- `listen-for-usb-devices` and `auto-start-on-usb` are written per run by `setkeys` (section 6), never in this block.
- Record `usb-blacklist` and `enable-car-launcher`. If the blacklist names D-MOTO, empty it and say so. Do not change
  `enable-car-launcher`. If it is `true`, "Reopen on reconnection" is off, and F4 is INCONCLUSIVE.

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
| CLEAR | On the App info screen the script opens, press Clear defaults (API 35: Open by default, then Clear default preferences) | each CLEAR cue | Settings is another app, and no shell command clears USB defaults without `MANAGE_USB` |
| COUNT | Tell the host, per plug of the run just ended, how many USB dialogs you saw and the first word of each | each COUNT cue | the second column of the dialog count |

Open the cue terminal first: `tail -n0 -F $OUT/cues.log`. Read the operator this table once, at the start, and leave it
on screen. Two words tell the dialogs apart: Android's own dialog starts with "Open" ("Open Open Headunit ..."), and
the app's own starts with "Allow" ("Allow ... to access the USB device?").

| Run | Rule for every USB dialog in the run |
|---|---|
| seed | "Allow ...": press Cancel. "Open ...": press OK. Tick nothing. |
| R4a | Tick the box if there is one, then press OK, as soon as the dialog shows. |
| R4b | As R4a until the host says WAIT. After WAIT, touch nothing until the host says ANSWER. Then tick and press OK. |
| R4c, R5a | Touch nothing. Remember any dialog you see. |
| R5b, R5t, R5c | Press OK. Tick nothing. |
| F4a, F4b | Touch nothing until the host says LOOK. Then say if a USB dialog is visible. Answer it: "Allow ...": Cancel; "Open ...": tick, then OK. If none is visible, press Recents once, answer a USB dialog there by the same rule, and say what you did. Never press Back or Home. |

Always: act only on a cue or by the rule. Do not touch D-MOTO.

## 6. Helpers

Run every adb call against one unit in sequence (template rule 8). All parsing runs on the host. Grep every capture
with `-a`.

**`lib.sh`**
```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round2; mkdir -p $OUT; export LC_ALL=C
HP=CNU350BGBJ; POCO=4f4027e9; MOTO=ZY22GC3BM4
BASEKEYS="int:log-level=2 int:onboarding-version=2 bool:allow-external-configuration=true int:wifi-connection-mode=0
 set:connection-modes=usb bool:auto-start-on-usb=false bool:reopen-on-reconnection=true bool:auto-connect-last-session=false
 bool:auto-connect-single-usb=false set:allow-devices= bool:use-libusb=false bool:kill-on-disconnect=false
 bool:enable-floating-button=false"
# Set in Prepare (wireless.env): HP_W, POCO_W, MOTO_W (ip:5555). Set per stage: ST (H|P), HU (the host's ip:5555), HUNAME.
# writeprefs KEY...: copy round 1's function here unchanged (section 4).
A() { adb -s "$HU" "$@"; }
F() { echo $OUT/$ST-hu.txt; }
send() { local a=$1; shift; A shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3
  timeout 3 adb -s "$MOTO_W" shell log -t RIGMARK "RIGMARK $1" >/dev/null 2>&1
  echo "$1 host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
cue() { printf '\a'; echo "$(date +%T) $*" | tee -a $OUT/hand-steps.log >> $OUT/cues.log; }
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
# tapusb TAG: one injected tap on the home screen's USB tile, resolved from a dump. Exactly one match, or no tap.
tapusb() { local pat='resource-id="com.andrerinas.headunitrevived:id/usb_button"' n b
  A shell rm -f /sdcard/ud.xml; A shell uiautomator dump /sdcard/ud.xml >/dev/null 2>&1; sleep 0.3
  rm -f $OUT/$1-ui.xml; A pull /sdcard/ud.xml $OUT/$1-ui.xml >/dev/null 2>&1
  n=$(grep -ao "$pat" $OUT/$1-ui.xml 2>/dev/null | wc -l)
  [ "$n" -eq 0 ] && { pat='text="USB"'; n=$(grep -ao "$pat" $OUT/$1-ui.xml 2>/dev/null | wc -l); }   # API 17 dumps carry no resource-id
  [ "$n" -eq 1 ] || { echo "$1 TAP_TARGET matches=$n" >> $OUT/marks.txt; return 1; }
  b=$(grep -ao "$pat[^>]*" $OUT/$1-ui.xml | grep -aoE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -aoE '[0-9]+' | tr '\n' ' ')
  set -- $1 $b; A shell input tap $(( ($2+$4)/2 )) $(( ($3+$5)/2 ))
  echo "$1 TAP $pat at $(( ($2+$4)/2 )),$(( ($3+$5)/2 )) $(date +%T)" >> $OUT/taps.txt; }
```

**`usbparse.sh`**
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
`6353/11521` is B (`18D1:2D01`). `serial_outside` is meaningful only with D-MOTO unplugged. If a row reads
`sections=0` on a dump that has a preferences block, the host reads the raw `<tag>-usb.txt` and says which entries are
ours.

**`timeline.sh`**
```bash
#!/usr/bin/env bash
# timeline.sh CAPTURE P: the decisive lines of cycle P, from marker P-cue to marker P-unplug, one per row,
# then one SUMMARY row. A = the phone's normal identity, B = its accessory identity. No verdict.
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
{ t = substr($0, 1, 18); k = "" }
/START u0/ && /\.usb\.Usb(Confirm|Permission|Resolver)Activity/ { k = act($0) "_START" }
create == 1 && /_create_activity/ && /\.usb\.Usb(Confirm|Permission|Resolver)Activity/ { k = act($0) "_START" }
/_finish_activity/ && /\.usb\.Usb(Confirm|Permission|Resolver)Activity/ { k = act($0) "_FINISH" }
/START u0/ && /openheadunit\.main\.MainActivity/ { k = "MAIN_START" }
/Normal USB device attached: / { k = "ATTACH_A" }
/USB accessory device attached, connecting\./ { k = "ATTACH_B" }
/UsbLauncher: permission for / { k = "DEC" }
/Requesting USB permission for / { k = "REQ" }
/SSL handshake complete/ { k = "SSL" }
/Media Sink Setup Request: / { k = "MS" }
/Reopen on reconnection: launching MainActivity|Boot auto-start: / { k = "RAISE" }
/USB Intent: / && /USB_DEVICE_DETACHED/ { k = "DETACH" }
/FATAL EXCEPTION|ANR in com\.andrerinas|NoClassDefFoundError/ { k = "FATAL" }
k != "" { n++; T[n] = ms(t); TS[n] = t; K[n] = k; L[n] = substr($0, 20, 240) }
END {
  decA = decB = rsl = mtpA = srcA = srcB = "-"; ssl = ms_n = fatal = stacked = reqA = reqB = mains = raise = 0
  tms = -1; tb = -1
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
    else if (K[i] == "RAISE") raise++
    else if (K[i] == "FATAL") fatal++
    ID[i] = id
    printf "%s\t%s\t%s\t%s\n", TS[i], K[i], id, L[i]
  }
  open_ms = unfinished = 0; banswer = "none"; mac = "none"; lastc = -1
  for (i = 1; i <= n; i++) {
    if (K[i] == "PERM_START")
      for (j = 1; j <= n; j++) if (K[j] == "CONFIRM_START" && ID[j] == ID[i] && (T[i] - T[j] <= 2000 && T[j] - T[i] <= 2000)) { stacked++; break }
    if (K[i] ~ /^(CONFIRM|PERM|RESOLVER)_START$/) {
      if (FIN[i] < 0) unfinished++
      if (FIN[i] < 0 || (tms >= 0 && FIN[i] > tms)) open_ms++
      if (K[i] == "CONFIRM_START" && ID[i] == "B" && banswer == "none" && tb >= 0) banswer = (FIN[i] < 0) ? -1 : int(FIN[i] - tb)
    }
    if (K[i] == "CONFIRM_START") lastc = T[i]
    if (K[i] == "MAIN_START" && mac == "none" && lastc >= 0) mac = int(T[i] - lastc)
  }
  printf "SUMMARY\t%s\tconfirm_A=%d\tconfirm_B=%d\tperm_A=%d\tperm_B=%d\tresolver=%d\tstacked=%d\treq_A=%d\treq_B=%d\tdec_A=%s\tdec_B=%s\tsrc_A=%s\tsrc_B=%s\tmtp_A=%s\trs=%s\tssl=%d\tms=%d\topen_at_ms=%d\tunfinished=%d\tb_answer_ms=%s\tmain_start=%d\tmain_after_confirm_ms=%s\traise=%d\tfatal=%d\n", \
    P, C["CONFIRM_A"], C["CONFIRM_B"], C["PERM_A"], C["PERM_B"], C["RESOLVER_A"] + C["RESOLVER_B"], stacked, reqA, reqB, decA, decB, srcA, srcB, mtpA, rsl, ssl, ms_n, open_ms, unfinished, banswer, mains, mac, raise, fatal
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
| `main_start`, `main_after_confirm_ms` | `MainActivity` starts, and the time from the latest Android dialog start to the first of them |
| `raise`, `fatal` | the reopen lines (`Reopen on reconnection: launching MainActivity`, `Boot auto-start: `); crash lines |

A START line's identity can be wrong when ours follows A's dialog by more than 500 ms less than the B attach does. Each
row prints the line beside the label, so the host checks any `perm_*` above 0 against the rows.

**`keys.sh`**
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
# KIND: session | slow (R4b) | quiet (R5a) | verb (R5b) | tap (R5t) | double (R5c) | look (F4)
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
      slow) if waitfor $P-cue 'USB accessory device attached, connecting.' 120; then
              cue "WAIT $P: touch nothing until ANSWER"; sleep 10       # the 10 s is what R4b measures
              cue "ANSWER $P: answer the dialog on $HUNAME now (tick, OK)"; fi ;;
      quiet) sleep 20 ;;                                                # R5a's quiet window: the time is the measurement
      verb) sleep 8; send ACTION_CHECK_USB ;;                           # 8 s: past the 2 s fallback and its hold
      double) sleep 8; A shell "am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_CHECK_USB; am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_CHECK_USB" ;;
      tap) sleep 8; tapusb $P || rc=1 ;;
      look) sleep 15; focus $P; cue "LOOK $P: say if a USB dialog is visible on $HUNAME, then follow the F4 rule" ;;
    esac
    if [ $K != quiet ] && [ $rc -eq 0 ]; then
      { waitfor $P-cue 'SSL handshake complete' 150 && waitfor $P-cue 'Media Sink Setup Request: ' 30; } \
        || { echo "$P NO_SESSION" >> $OUT/marks.txt; [ $K = look ] || rc=1; }   # F4 reports a missing session, never retries it
      [ $K = look ] && focus $P-session
      sleep 10                                                          # a late dialog would START in these 10 s
    fi
  fi
  mark $P-end; cue "UNPLUG $P: unplug D-MOTO from $HUNAME"
  waitfor $P-end 'USB_DEVICE_DETACHED' 90 && echo "$P unplug=$(firstts $P-end 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt \
    || echo "$P NO_UNPLUG_LINE" >> $OUT/marks.txt
  mark $P-unplug; moto_dump $P; usbdef $P-after
  ./timeline.sh $(F) $P > $OUT/$P.timeline.tsv
  printf '%s\tpid=%s>%s\tphone_acc=%s\tafter=%s\t%s\n' "$P" "$pid0" "$(hupid)" \
    "$(grep -acF 'entering USB accessory mode' $OUT/$P-phone.txt 2>/dev/null)" \
    "$(tail -1 $OUT/defaults.tsv | cut -f2-3 | tr '\t' ' ')" "$(tail -1 $OUT/$P.timeline.tsv | cut -f3-)" >> $OUT/cycles.tsv
  return $rc; }
# valid RUN P: 0 when the cycle can be graded. An R4 cycle on routeSeen=no, or an R4b answer under 8 s, is void.
valid() { local s b; s=$(tail -1 $OUT/$2.timeline.tsv)
  case $1 in R4a|R4b) echo "$s" | grep -q 'rs=[a-z,]*no' && { echo "$2 VOID routeSeen=no" >> $OUT/marks.txt; return 1; };; esac
  if [ $1 = R4b ]; then b=$(echo "$s" | grep -ao 'b_answer_ms=[-0-9a-z]*' | cut -d= -f2)
    case "$b" in none|-1) ;; *) [ "$b" -ge 8000 ] || { echo "$2 VOID early answer b_answer_ms=$b" >> $OUT/marks.txt; return 1; };; esac; fi
  return 0; }
pre_R4a() { cleardef $P-clear; }
pre_R4b() { cleardef $P-clear; }
pre_R5t() { launch; sleep 2; }
pre_F4a() { cleardef $P-clear; }
# runplugs RUN KIND N: N graded cycles, at most 2 retries (the stop rule). Returns 2 on low battery.
runplugs() { local run=$1 kind=$2 need=$3 good=0 bad=0 k=0 rc
  while [ $good -lt $need ] && [ $bad -lt 2 ]; do k=$((k+1)); P=$ST-$run-p$k
    type pre_$run >/dev/null 2>&1 && pre_$run
    cycle $P $kind; rc=$?; [ $rc -eq 2 ] && return 2
    [ $rc -eq 0 ] && { valid $run $P || rc=1; }
    if [ $rc -eq 0 ]; then good=$((good+1)); else bad=$((bad+1)); echo "$P RETRY" >> $OUT/marks.txt
      grep -q "^$P VOID routeSeen" $OUT/marks.txt && seed; fi
  done
  echo "$ST-$run graded=$good retries=$bad" >> $OUT/marks.txt; return 0; }
# seed: plug until Android starts our activity for an attach once (at most 3 plugs). Rule: Allow -> Cancel, Open -> OK.
seed() { local n=0
  while [ $n -lt 3 ]; do n=$((n+1)); cycle $ST-seed$n session; [ $? -eq 2 ] && return 2
    if [ "$(since $ST-seed$n-cue | grep -aF 'UsbAttachedActivity' | grep -aF 'USB Intent: ' | grep -acF 'USB_DEVICE_ATTACHED')" -ge 1 ]; then
      echo "$ST seed OK at $ST-seed$n pid=$(hupid) recorded=$(cnt $ST-seed$n-cue 'Android starts this app for a USB attach in this boot (recorded)')" >> $OUT/marks.txt
      return 0; fi
  done; echo "$ST SEED_FAILED" >> $OUT/marks.txt; return 1; }
```

The diagnosis's log instrument, updated for the candidate's texts. The only change from the ticket's copy is `-a` on
both greps (`tooling.md`: a long capture reads as binary without it). Run it on each run's slice.

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
  "service: 2 s fallback ran (activity did not act)|UsbAttachedActivity didn't handle"
  "service: normal-mode device, no permission, asks|Single USB auto-connect: device found but no permission"
  "service: last-session device, no permission, asks|Found known USB device but no permission"
  "service: accessory grant arrived during the poll|Accessory-mode permission arrived after"
  "service: accessory poll expired, asks|Accessory-mode device has no permission \\(re-enumerated\\)"
  "service: permission request sent|Requesting USB permission for"
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

Its `UsbConfirmActivity` and `UsbPermissionActivity` rows count every line that names them, starts and finishes
together. They are a second reading only; `timeline.sh` decides.

## 7. The deciding lines

All app strings print at INFO. Verified with `grep -F -r` in `app/src` at `63f20a8f`, except the ones marked
"composed", whose parts the source joins at run time. Each is graded in `<ST>-hu.txt`, between `<P>-cue` and
`<P>-unplug` for one cycle, through `timeline.sh`.

| Id | Line (fixed substring) | Read |
|---|---|---|
| L-RX | `AutomationReceiver: ` | a verb landed; a step with none is void, not a FAIL |
| L-MK | `AutomationMarker: ` | the window bounds |
| L-DEC | `UsbLauncher: permission for ` | one decision. Fields (composed): `identity=normal`/`identity=accessory`, `mtp=`, `filter=`, `activity=`, `listen=`, `routeSeen=`, `attach=`, `source=`, `action=` (`ask`, `wait`, `asked`, `hold`) |
| L-WAITB | `Accessory-mode device has no permission yet; Android shows its own dialog for it: ` | the poll for B ran out and the service waits |
| L-ASKB | `Accessory-mode device has no permission (re-enumerated); requesting permission: ` | the poll for B ran out and the service asks |
| L-REQ | `Requesting USB permission for ` | every `requestPermission`. The service prints it bare, the button and the list as `UsbPermissionAsks: Requesting USB permission for `. The name carries `VID: <hex> PID: <hex>` (composed) |
| L-ROUTE | `Android starts this app for a USB attach in this boot (recorded)` | API 24 and up only, once per boot change |
| L-NORM | `Normal USB device attached: ` | the plug time, and identity A |
| L-ACC | `USB accessory device attached, connecting.` | identity B |
| L-INT | `USB Intent: ` | with `USB_DEVICE_ATTACHED` and `UsbAttachedActivity` on one line: Android started our activity for an attach (the seed check). With `USB_DEVICE_DETACHED`: the unplug time |
| L-FALL | `UsbAttachedActivity didn't handle ` | the 2 s fallback ran for A |
| L-SINGLE | `Single USB auto-connect: device found but no permission` | the fallback reached a decision for A |
| L-ACTOK | `Usb in accessory mode and has permission. Starting AapService.` | B reached our activity with a grant |
| L-ACTHAND | `UsbAttachedActivity: accessory mode without permission, handing it to the service` | B reached our activity with no grant |
| L-SW | `Switching USB device to accessory mode ` | our activity switched A |
| L-SSL | `SSL handshake complete` | a session formed (once per session at INFO) |
| L-MS | `Media Sink Setup Request: ` | the phone answered our service discovery |
| L-TP | `Throughput over ` | `rendered=N`, stage H only |
| L-RAISE | `Reopen on reconnection: launching MainActivity`, `Boot auto-start: ` | F4: the service raised `MainActivity`, and by which route |
| L-STALE | `UsbLauncher: stale accessory `, `UsbLauncher: targeted recovery` | guard, record only |
| D-MATCH | `MATCH! Starting AapService` | discard rule (template §4) |

System strings, not in `app/src`: `START u0`, `.usb.UsbConfirmActivity`, `.usb.UsbPermissionActivity`,
`.usb.UsbResolverActivity`, `_finish_activity` (`am_` on API 17, `wm_` on API 35), `_create_activity`,
`openheadunit.main.MainActivity` (the component in a `START` line), `mCurrentFocus`, `topResumedActivity`,
`mResumedActivity`, `Device preferences`, `device_preferences`, `MtpNotificationManager`. Phone string, from AOSP on
D-MOTO: `entering USB accessory mode`. **FAIL lines, every cycle:** `FATAL EXCEPTION` in a block that names
`com.andrerinas`, `ANR in com.andrerinas`, and `NoClassDefFoundError` (D-HP runs API 17).

The files the runs write:

| File | One row per | Fields |
|---|---|---|
| `cycles.tsv` | plug | label, `pid=<before>><after>`, `phone_acc=`, `after=defaults=N pairs=...` (read after the unplug), then the SUMMARY fields |
| `<P>.timeline.tsv` | decisive line | time, kind, identity, the line; last row SUMMARY |
| `defaults.tsv` | `dumpsys usb` read | tag, `defaults=`, `pairs=`, `sections=`, `serial_outside=` |
| `marks.txt` | event | markers with host time, `plug=` and `unplug=` with logcat time, retries, voids, seeds |
| `focus.txt` | F4 read | `mCurrentFocus`, `mFocusedApp`, `topResumedActivity` or `mResumedActivity` |
| `taps.txt` | injected tap | target, coordinates, time |
| `<P>-phone.txt` | plug | D-MOTO's buffer from before the plug to after the unplug |

## 8. Runs

Each stage is one capture with all runs chained. A run's end state is the next run's start state, except where
`setkeys` or `cleardef` says otherwise. Cycle labels are `<ST>-<run>-p<k>`.

**Stop rule.** A cycle with `NO_PLUG`, `NO_SESSION` where a session is expected, a void, or a failed tap target is
retried once with the next `k`. Two such cycles in one run end the run, graded on what it has (INCONCLUSIVE when it
graded fewer than 2 of 3, or 0 of 1). `BATTERY_LOW` ends the stage. `SEED_FAILED` makes every R4 run of that stage
INCONCLUSIVE, and the stage goes on to R5.

### Prepare (20 min)

1. `get_apk.sh` (section 1). Charge D-HP to 100%.
2. On D-HP and D-POCO, with the app stopped: back up `settings.xml`, record the live APK md5, save
   `dumpsys usb > $OUT/prep-<unit>-usb.txt`, and install `apk-C.apk` with `adb install -r -d`. Read every section 4
   key back. Run the identity check (section 1). Record `getprop ro.build.version.sdk` and `date` on each unit beside
   the host's `date`, and D-MOTO's offset the same way.
3. D-POCO: `am start -a android.intent.action.VIEW -d headunit://exit`, then force-stop its app (it is a phone in other
   rounds; template §8 role-swap rule). Record `appops get $PKG SYSTEM_ALERT_WINDOW`.
4. Wireless adb, each while still on its PC cable: `adb -s <serial> tcpip 5555`, read `ip -4 addr show wlan0`,
   `adb connect <ip>:5555`, and write `HP_W`, `POCO_W`, `MOTO_W` to `wireless.env`.
5. D-MOTO: `svc bluetooth disable` and read it back (`dumpsys bluetooth_manager`); WiFi stays on. Read
   `settings get global stay_on_while_plugged_in`, record it, then `svc power stayon true`. Record Gearhead's
   `versionName` and `getprop sys.usb.config`. `logcat -G 16M`. Do the same stay-on read before each host's
   `svc power stayon true` in the stage script, and restore all three values at the end of the round.
6. Copy the section 6 blocks, `chmod +x usbparse.sh timeline.sh usb-dialog-instrument.sh`, and copy round 1's
   `writeprefs` into `lib.sh`.
7. D-POCO's lasting grant (section 3): with D-MOTO unplugged,
   `ST=P HU=$POCO_W HUNAME=D-POCO; . ./lib.sh; . ./keys.sh; launch; cleardef P-prep; usbdef P-prep-grant`. Read
   `serial_outside=` on the last row. If it is above 0, ask the operator the section 3 question.

Run this only on the operator's yes (template §7b).

**`reinstall_poco.sh`**
```bash
. ./lib.sh; HU=$POCO
A shell am force-stop $PKG
A shell run-as $PKG cat shared_prefs/settings.xml > $OUT/backup-P-pre-uninstall.xml
A shell dumpsys usb > $OUT/P-pre-uninstall-usb.txt
adb -s $POCO uninstall $PKG && adb -s $POCO install $OUT/apk-C.apk
A shell dumpsys usb > $OUT/P-post-uninstall-usb.txt          # MUST name D-MOTO's serial 0 times
# push backup-P-pre-uninstall.xml back with the app stopped (owner and mode as tooling.md says), diff it, and re-check
# resolution, DPI and codec; then restore the overlay grant read in Prepare step 3:
A shell appops set $PKG SYSTEM_ALERT_WINDOW allow             # only if Prepare step 3 read allow
```

### One stage (run it for H, then P)

Set the stage variables, then run the block. H: `ST=H HU=$HP_W HUNAME=D-HP`. P: `ST=P HU=$POCO_W HUNAME=D-POCO`.

**`stage.sh`**
```bash
. ./lib.sh; . ./wireless.env; . ./keys.sh; . ./cycle.sh
type writeprefs >/dev/null 2>&1 || { echo "copy writeprefs from round 1 into lib.sh first"; exit 1; }
cue "CAB $ST: move the cables for stage $ST (brief section 5). D-MOTO stays unplugged"
adb connect $HU >/dev/null; adb connect $MOTO_W >/dev/null
[ "$ST" = P ] || [ "$(bat)" -ge 60 ] || { echo "$ST BATTERY_START $(bat)" >> $OUT/marks.txt; exit 1; }
th_gate || exit 1
echo "$ST stay_on_before=$(A shell settings get global stay_on_while_plugged_in | tr -d '\r')" >> $OUT/marks.txt
A shell svc power stayon true; A shell am force-stop $PKG
writeprefs $BASEKEYS del:usb-attach-route-boot del:video-profile-starvation-cap || exit 1
cap; mark $ST-start; launch; sleep 3
if [ "$(since $ST-start | grep -aF 'START u0' | grep -acF 'MainActivity')" -lt 1 ]; then
  echo "$ST NO_START_LINES: every timeline.sh call runs with CREATE=1" >> $OUT/marks.txt; export CREATE=1; fi
usbdef $ST-start
run() { mark $ST-$1-start; runplugs "$@"; local rc=$?; mark $ST-$1-end
  cue "COUNT $ST-$1: tell the host, per plug, how many USB dialogs you saw and the first word of each"; return $rc; }

setkeys listen-on bool:listen-for-usb-devices=true bool:auto-start-on-usb=false || exit 1
seed; [ $? -eq 2 ] && exit 2
run R4a session 3 && run R4b slow 1 && run R4c session 3 \
 && setkeys listen-off bool:listen-for-usb-devices=false bool:auto-start-on-usb=false \
 && run R5a quiet 3 && run R5b verb 1 && run R5t tap 1 && run R5c double 1 \
 && setkeys f4 bool:listen-for-usb-devices=true bool:auto-start-on-usb=true \
 && run F4a look 1 && run F4b look 1

cleardef $ST-end-clear; mark $ST-end
send ACTION_EXPORT_LOG; sleep 5                               # the export path is on the data= line; pull that file
th_end; uncap; A shell am force-stop $PKG
for r in R4a R4b R4c R5a R5b R5t R5c F4a F4b; do
  tr -d '\r' < $(F) | sed -n "/AutomationMarker: $ST-$r-start\$/,/AutomationMarker: $ST-$r-end\$/p" > $OUT/$ST-$r.slice
  ./usb-dialog-instrument.sh $OUT/$ST-$r.slice > $OUT/$ST-$r.instrument.txt; done
# restore backup-$ST.xml with the app stopped, diff it, then: A shell settings put global stay_on_while_plugged_in <before>
cue "CAB $ST: stage $ST is over. Move the cables back"
```

A failed `setkeys` stops the chain: the runs it skipped are UNTESTABLE (settings). Low battery stops it the same way,
and the skipped runs are UNTESTABLE (battery). Grade each run from `cycles.tsv` and the run's `<P>.timeline.tsv` rows.
The operator's COUNT goes beside `confirm_*` plus `perm_*` as a second column. A disagreement is a finding, not a
verdict.

### seed (no verdict)

Listen on, after `setkeys listen-on`. No Clear first: with a stored default, Android starts our activity with no dialog,
which also records the route. `marks.txt` MUST then read `<ST> seed OK`. On stage P, also record `recorded=` (1 or
more expected on the first seed of the boot, 0 on a later one). Our dialog for A can still stack on the seed plug,
because `routeSeen` is still no at its 2 s decision. That is why the rule cancels "Allow" dialogs.

### R4a, Listen on, no defaults, first connect: the point of the round

Three cycles, each after a `cleardef`. Rule: tick and OK every dialog at once.

Setup checks: `marks.txt` shows `seed OK` for this stage. Each `<P>-clear-post` row reads `defaults=0`, and the
`pid=` before matches the seed's pid on stage H.

R4a PASS needs, for every graded cycle:

1. `stacked=0`.
2. `req_B=0`, and every value in `dec_B` is `wait` or `asked`. An empty `dec_B` is fine: an answer inside B's 1 s
   poll leaves no decision (`Accessory-mode permission arrived after` instead).
3. `req_A` at most 1. If `mtp_A` lists only `no` (or `-`): `perm_A=0`, `req_A=0`, and every value in `dec_A` is
   `wait` or `asked`. If `mtp_A` lists `yes` (API 35 MTP rule): `confirm_A=0`, `perm_A=1`, `req_A=1`.
4. At most 1 dialog start per identity: `confirm_A + perm_A` at most 1, and `confirm_B + perm_B` at most 1.
5. `open_at_ms=0`: every dialog finished before the first `Media Sink Setup Request: `.
6. `ssl` at least 1 and `ms` at least 1. Phone: `phone_acc` at least 1.
7. `after=` (the `<P>-after` row) lists both A (`8888/...`) and B (`6353/11521`) in `pairs=`.
8. `fatal=0`.

Expected on both hosts with `mtp=no`: `confirm_A=1`, `confirm_B=1`, `perm_A=0`, `perm_B=0`, `req_A=0`, `req_B=0`.
Round 1 on `main` read 3 or 4 dialog starts per first connect and 2 requests per plug (`R-H2`, `R-P2`). If the change
did nothing, condition 1 or 2 fails on every cycle.

R4a FAIL if any graded cycle misses condition 1, 2, 3, 4 or 8. A cycle that misses only 5, 6 or 7 is reported with its
rows, and the run is FAIL only if the host shows the code caused it.

On stage P with a lasting grant for B (section 3, operator said no): `dec_B` reads `-` and `confirm_B` can stay open
under the session. Grade conditions 2, 4 (B) and 5 INCONCLUSIVE, and grade the rest.

### R4b, slow answer (the second request for B)

One cycle after a `cleardef`. Rule: as R4a until WAIT, then wait for ANSWER. The script cues WAIT on the B attach line
and ANSWER 10 s later.

R4b PASS needs: `perm_B=0`, `req_B=0`, at least one `wait` in `dec_B`, `ssl` at least 1, `phone_acc` at least 1, and
`b_answer_ms` at least 8000 (the operator really waited). A cycle with `b_answer_ms` under 8000 is void and retried. On
`main`, the service stacked its dialog for B 1 s after the attach (round 1), so `perm_B=1` is the "did nothing" reading.

### R4c, persistence (the control)

Three replugs, no clear, after R4b. The defaults R4a and R4b stored are in force. Rule: touch nothing.

R4c PASS needs, for each: 0 dialog starts (`confirm_*`, `perm_*`, `resolver` all 0), `req_A=0`, `req_B=0`, no `ask`
in `dec_A` or `dec_B`, `ssl` at least 1, `ms` at least 1, `phone_acc` at least 1, and `fatal=0`. Record L-SW and
L-ACTOK per cycle. The plan asked for 0 decision lines. A `wait` can still print when B's 1 s poll ends before Android's
grant reaches the service, and it raises no dialog. So this brief grades "no `ask`" and records the count. R4c FAIL if
any replug shows a dialog start. That FAIL also voids R4a on that stage.

### R5a, Listen off, attach

After `setkeys listen-off` (`comp=disabled` in `marks.txt`, else R5 is UNTESTABLE). Three replugs. Rule: touch nothing.
Each cycle waits 20 s after the plug, then unplugs.

R5a PASS needs, for each: 0 dialog starts, `req_A=0`, `req_B=0`, at least one `hold` in `dec_A`, no `ask` anywhere,
`ssl=0`. On `main` each plug showed 2 of our dialogs (`R-H1`, `R-P1`). On stage P with a lasting grant for A, the
fallback switches the phone with no decision and a session can form: grade the `hold` and `ssl` conditions
INCONCLUSIVE there, and the dialog and request conditions as written.

### R5b, Listen off, the USB verb

One cycle. 8 s after the plug, the script sends `ACTION_CHECK_USB`. It is the USB button's verb (template §3): it
reaches the service's scan with `user_requested`, not `HomeFragment`. Rule: OK, no tick.

R5b PASS needs: an `AutomationReceiver: ` line for `ACTION_CHECK_USB`; `dec_A` holds `hold` (the attach) and then
`ask` with `user` in `src_A`; `dec_B` holds `ask` with `automatic` in `src_B` (our own switch less than 10 s before);
`req_A=1`, `req_B=1`; `perm_A=1`, `perm_B=1`, `confirm_A=0`, `confirm_B=0`; `ssl` at least 1, `ms` at least 1,
`phone_acc` at least 1; `fatal=0`.

### R5t, Listen off, the USB tile by one tap

One cycle, the same as R5b with one injected tap on the home screen's USB tile in place of the verb. This is the only
path to `HomeFragment.onUsbClicked`, which the candidate changed, and no verb reaches it. `tapusb` resolves the target
from a `uiautomator dump`: `resource-id` `usb_button`, or on API 17, whose dump has no resource ids, the one node with
`text="USB"`. It taps only on exactly one match. Rule: OK, no tick.

R5t PASS needs: one row in `taps.txt`; `ask` with `user` in `dec_A` and `src_A`; exactly one
`UsbPermissionAsks: Requesting USB permission for ` row for A in `<P>.timeline.tsv`; then the R5b conditions from `dec_B`
on.

### R5c, Listen off, a double press

One cycle. 8 s after the plug, two `ACTION_CHECK_USB` broadcasts in one adb shell call. Rule: OK, no tick. A user press
may ask over an open entry (fix F3), so two dialogs for A are allowed.

R5c PASS needs: two `AutomationReceiver: ` lines for `ACTION_CHECK_USB` (record the gap between them), `ssl` at least 1,
`ms` at least 1, `phone_acc` at least 1, and `fatal=0` (no crash, no ANR). Record `req_A`, `perm_A` and `dec_A`.

### F4, does MainActivity cover Android's dialog (REPORTED)

After `setkeys f4`: Listen on, `auto-start-on-usb` on, `reopen-on-reconnection` on (from `BASEKEYS`). F4a runs after a
`cleardef`, F4b with the defaults F4a stored. Rule: the F4 row of section 5. On D-HP the force-stop in `setkeys` lost the
route verdict, so F4a's A decision reads `routeSeen=no` and our dialog can stack once. That is the state an API 17 user
meets after a cold start. Report it, do not void it.

Report per cycle, with no verdict:

- The order of `CONFIRM_START`, `PERM_START`, `MAIN_START` and `RAISE` rows, and `main_after_confirm_ms`.
- `focus.txt` at the LOOK cue and after the session. Say if it disagrees with the `START` order (API 35 left
  `mCurrentFocus` stale in round 1).
- The operator's words: a dialog visible at LOOK or not, and whether Recents reached it.
- `ssl`, `open_at_ms`, `unfinished`. An Android dialog still open under a live session is the case to name.

Grade F4 REPORTED in the results file. A `MAIN_START` after a `CONFIRM_START` with a hidden dialog and no session is the
answer to the open question, not a FAIL.

## 9. Do not re-run

Round 1 settled `main`'s behaviour: two of our dialogs per plug with Listen off (`R-H1`, `R-P1`), the stacked pair and
3 or 4 dialogs on a first connect with Listen on (`R-H2`, `R-P2`, by the host grade), and the tick-everything control
(`R-H3`, `R-P3`). Do not build `main` again. D-SAM has no USB host: do not try it.

## 10. Report back

1. R4a per host: `stacked`, `req_B`, `dec_B`, and the dialog starts per identity, with the operator column. Did the
   first connect show one dialog per identity?
2. R5a per host: dialog starts, requests and `dec_A` per replug. Did Listen off stop the prompts?
3. F4 per host: the `START` order, the focus reads, and whether the operator could answer Android's dialog.

Also: every void and its reason, every cycle whose pid changed, `serial_outside` on D-POCO before and after the round,
D-MOTO's `mtp=` per host, and any `perm_*` above 0 with its timeline rows.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
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
UsbPermissionAsks: Requesting USB permission for 
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
UsbAttachedActivity didn't handle 
Single USB auto-connect: device found but no permission
Found known USB device but no permission
Accessory-mode permission arrived after
Usb in accessory mode and has permission. Starting AapService.
UsbAttachedActivity: accessory mode without permission, handing it to the service
Switching USB device to accessory mode 
USB permission granted for 
USB permission denied for 
SSL handshake complete
Media Sink Setup Request: 
Throughput over 
Reopen on reconnection: launching MainActivity
Boot auto-start: 
UsbLauncher: stale accessory 
UsbLauncher: targeted recovery
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
