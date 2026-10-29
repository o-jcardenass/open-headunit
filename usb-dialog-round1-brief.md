# usb-dialog, round 1 brief: the USB permission dialog that returns on every connect

Published name: `usb-dialog-round1-brief.md`. Results go in `usb-dialog-round1-results.md`.
Evidence: create the release `rig-evidence-usb-dialog` with the asset `usb-dialog-round1-captures.zip`.

Units: D-HP (API 17), D-SAM (API 19) and D-POCO (API 35) as USB hosts, one at a time. D-MOTO is the wired phone in
every stage. D-HU takes no part.

Queue line: `usb-dialog-round1-brief.md` (one arm, `main` `947b3ae9`, the APK of `main-smoke` round 1; units D-HP, D-SAM,
D-POCO, D-MOTO; R2 is the point).

Pairing: possible only with a round that uses D-HU alone. If this round is the second of a pair, it takes the lock
`/tmp/ohu-rig-usbdlg.lock`. Its hand steps are many and short, so the other round must have none during this one.

Hand steps: cable moves, one plug and one unplug per cycle, every USB system dialog, Clear defaults before R2 and R3,
one count per run (section 4). Injected taps: none.

**Time budget.** APK 5 min (25 if it has to be built), Prepare 20 min, stage H 32 min, stage S 32 min, stage P 32 min,
cable moves 10 min, thermal waits about 10 min. **About 2 h 20 min.** Write the results file after every run.

| Stage | Host | Runs | Min |
|---|---|---|---|
| H | D-HP (API 17), wireless adb, D-MOTO on its OTG port | R1, R2, R3 in one capture | 32 |
| S | D-SAM (API 19), wireless adb, D-MOTO on its OTG port | R1, R2, R3 in one capture | 32 |
| P | D-POCO (API 35), wireless adb, D-MOTO on its OTG port | R1, R2, R3 in one capture | 32 |

**R2 is the point of the round.** R1 tests the second mechanism. R3 is the positive control.

## 1. Build and baseline

One arm, no control build. The code under test is `main`:

| Ref | Commit | Tree |
|---|---|---|
| `origin/main` | `947b3ae9fae9834aa8e4145ee91751ed743c2ae0` | `e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165` |

**Install `947b3ae9`, not `v.4.0.0-alpha`.** One reporter ran `v.4.0.0-alpha`. Between the two, these files are
byte-identical: `UsbAttachedActivity`, `UsbAccessoryHandoffPolicy`, `AutoStartFragment`, `AndroidManifest.xml` and
`usb_device_filter.xml`. `Settings.setUsbAttachedActivityEnabled` and `UsbLauncherListener.onUsbAttach` did not change.
The diff in `UsbLauncherManager`, `UsbLauncherListener` and `UsbReceiver` is the stale-accessory recovery only. That
code runs after a failed handshake on an accessory that stays on the bus, so a clean plug does not reach it. Two
reasons to take `main`: `main-smoke` round 1 builds this exact SHA, so the round needs no build of its own, and the fix
will branch from it, so rounds 2 and later compare on one base. Guard: every cycle MUST log 0
`UsbLauncher: stale accessory ` and 0 `UsbLauncher: targeted recovery` lines. A cycle with one ran the changed code;
grade it, and say so.

The dev host suite at this commit: 4245 tests, 0 failures (recorded for `main` `947b3ae9` in the work-in-flight
ledger). Do not run the unit tests on the rig. No history was rewritten; this is the thread's first round.

**`get_apk.sh`**
```bash
OUT=rig-data/rounds/usb-dialog-round1; mkdir -p $OUT
SRC=rig-data/rounds/main-smoke-round1/apk-M.apk
if [ -f $SRC ]; then cp $SRC $OUT/apk-M.apk
else
  WT=$($RIG_TOOLKIT/wt-new.sh usb-dialog)          # usage in TOOLS.md; record the path in Setup notes
  git -C $WT fetch origin main
  git -C $WT checkout --detach 947b3ae9fae9834aa8e4145ee91751ed743c2ae0
  git -C $WT rev-parse 'HEAD^{tree}'               # MUST print e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165
  $RIG_TOOLKIT/rig_thermal.sh wait 70
  HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
  cp rig-data/apks/*.apk $OUT/apk-M.apk
fi
md5sum $OUT/apk-M.apk                               # record in Setup notes
```

**Identity on each host, after Prepare:** `send ACTION_QUERY_STATE` and its `data=` line carries `947b3ae9`; the md5
of the pulled live APK matches `apk-M.apk`. Install with `adb install -r -d` only when the live md5 differs. Never
uninstall: on Android 4.x an install also clears matching USB defaults, and Prepare clears them anyway after it.

## 2. What this is and why

Two reports describe the same fault. On every connect, a USB permission dialog appears, often twice. A tick on "Use by
default" is not remembered. On one tablet the checkbox was there at first and then went away. One photo shows the
pre-Android-10 wording of `UsbPermissionActivity` ("Allow the app Open Headunit to access the USB device?"), the
dialog that our own `requestPermission()` raises.

The diagnosis (`.claude/sdlc/gh-1185/diagnosis.md`, read in AOSP 4.3, 9 and 16, not yet on hardware) names two
mechanisms in our code:

- **M1, setting off.** "Listen for USB Devices" (`listen-for-usb-devices`) off disables `UsbAttachedActivity`. Android
  then ignores our stored defaults, shows no confirm dialog and hides the checkbox, because a disabled component
  takes no part in matching or in `canBeDefault`. The service still asks on every attach: once for the normal identity
  (A) after the 2 s fallback, once for the accessory identity (B, `18D1:2D0x`) after the 1 s poll. Two dialogs per
  connect, no checkbox. The setting's own description tells an annoyed user to turn it off.
- **M2, setting on.** For a device that Android routes through its confirm dialog (`UsbConfirmActivity`), the service
  stacks its own `UsbPermissionActivity` on top 1 s (B) or 2 s (A) later. Two dialogs for one identity. A tick on ours
  stores the default. OK **without** the tick on the confirm dialog underneath then clears it. The next connect shows
  the pair again.

| Hypothesis | Run | True if | Refuted if |
|---|---|---|---|
| H1 (M1) | R1 | 2 dialogs per plug, all `UsbPermissionActivity`, 0 checkboxes | a checkbox, a `UsbConfirmActivity`, or 0 dialogs on a plug |
| H2 (M2) | R2 | each reconnect shows our dialog stacked on the confirm dialog for B, and no default of ours for B after it | a reconnect with no dialog for B, or a default for B after the answers |
| control | R3 | ticks on every dialog give reconnects with 0 dialogs and a session | a dialog on a reconnect |

H3 (the record-audio warning removes the checkbox) is read off the dialog text in every snapshot. H4 (defaults lost on
an ignition cycle) is not tested this round.

## 3. What is different about this round

- **The obvious instruments are wrong here, so the round uses others.** Read these before the first run:
  - `USB Intent: ` prints from two places: `UsbAttachedActivity.onCreate` (Android routed the attach to our activity)
    and `UsbReceiver.onReceive` (the service heard any attach or detach). Count only lines that carry
    `UsbAttachedActivity.onCreate | USB Intent: `. The diagnosis table and its first instrument landmark counted both;
    section 6's copy of the instrument is corrected.
  - API 19 logs no activity `START` line (`rig-quirks/topics/tooling.md`), so logcat cannot show a dialog on D-SAM.
    Dialog presence is graded from `dumpsys window windows` snapshots (`dialogs.tsv`, section 5), on every host.
  - The two dialogs look alike and mean opposite things (template §7b). Grade the activity name, never the wording.
  - `pm clear-package-preferred-activities` clears intent defaults, not USB defaults, and no shell command clears USB
    defaults without `MANAGE_USB`. So Clear defaults is a hand step in Settings, read back with `dumpsys usb`.
- **"Listen for USB Devices" takes effect only on a `MainActivity` launch.** `MainActivity.onCreate` calls
  `Settings.setUsbAttachedActivityEnabled`, which calls `setComponentEnabledSetting` only when the state differs. A
  `settings.xml` edit therefore needs a force-stop and a launch, then a read-back of the component state from
  `dumpsys package` (`comp`, section 5). On 4.x that component change also clears every matching USB default
  (diagnosis fact 8). So the order is always: write the key, launch, read the component, then clear defaults, then plug.
- **D-HU cannot host USB.** The hosts are D-HP and D-POCO, as in `main-smoke` round 1 and `emotionbug-batch` round 2,
  and D-SAM, which has never hosted USB on this rig. Prepare checks that it can (`android.hardware.usb.host`) and that
  wireless adb works on it. If either check fails, stage S is UNTESTABLE.
- **The brief that queued this round named D-HP as API 34. It is API 17** (`rig-quirks/units/D-HP.md`). The only USB
  host on the rig at API 29 or later is D-POCO, so stage P carries the modern-Android half.
- **Stage P changes D-POCO for later rounds, so ask first.** From Android 10, a tick on our dialog also writes a
  persistent grant for that identity, and only an uninstall removes it (template §7b). R2 and R3 tick our dialog for
  D-MOTO's accessory identity. Before stage P, ask the operator: "Stage P leaves a persistent USB grant for D-MOTO on
  D-POCO that only an uninstall removes. Run it?" On no, stage P is UNTESTABLE (rig policy) and the round ends after S.
- **D-MOTO keeps USB debugging on**, because the phone capture runs over its wireless adb. So A is `22B8:2E8x` with an
  ADB interface (`FF/42/01`, which our filter matches), and B is `18D1:2D01`. On 4.x this changes nothing: those
  versions have no MTP short cut. On API 35 it can decide whether Android shows a confirm dialog for A, so A on D-POCO
  is recorded, not graded. Record D-MOTO's USB mode (`svc usb getFunctions`, or `getprop sys.usb.config` if that fails).
- **D-MOTO's wireless adb drops in accessory mode** (`emotionbug-batch` round 2). The phone buffer is cleared before
  each plug and dumped after each unplug (`moto_dump`). Its Bluetooth stays off all round.
- **The tablets' batteries feed D-MOTO over OTG.** D-HP's battery is degraded. Gate each tablet stage at 60% to start,
  read before every plug, stop the stage at 30%. Charge D-HP to 100% and D-SAM on a separate charger in Prepare.
- **On API 35 the persistent grant from an earlier round may already exist for D-MOTO's identities.** Then our dialog
  does not stack for B in R2 on D-POCO, and only the confirm dialog shows. That is a valid R2 result there (section 8).
- **Expected INCONCLUSIVE:** R3's `rendered=` on stage P (a phone host may hold the projection for milliseconds,
  `main-smoke` round 1); the phone bullets if the framework strings in section 7 are not in D-MOTO's ROM (0 of them on a
  plug that reached SSL means the string, not the phone).
- **Thermal.** Cite `rig-quirks/topics/tooling.md`: `rig_thermal.sh wait 75` before each stage (once more on exit 1),
  `rig_thermal.sh watch` during it, and stop the round as UNTESTABLE (host thermal) after a second failed wait.
- Read `rig-quirks/topics/tooling.md`, `units/D-HP.md`, `units/D-SAM.md`, `units/D-SAM-and-D-HP.md`,
  `units/D-POCO.md`, `units/D-MOTO.md`, and template §7b.

## 4. Cabling and hand steps

The PC has four USB ports. D-HU may stay where it is if a port is free; nothing uses it.

| Stage | On the PC | On an OTG port | Wireless adb |
|---|---|---|---|
| Prepare | D-HP, D-SAM, D-POCO, D-MOTO | | |
| H | D-SAM, D-POCO | D-MOTO on D-HP | D-HP, D-MOTO |
| S | D-HP, D-POCO | D-MOTO on D-SAM | D-SAM, D-MOTO |
| P | D-HP, D-SAM | D-MOTO on D-POCO | D-POCO, D-MOTO |

| Id | Hand step | When | Why there is no verb |
|---|---|---|---|
| CAB | Move the cables and the OTG adapter as the stage table says | stage start and end | hardware |
| PLUG | Plug D-MOTO's cable into the OTG adapter on the host | each `PLUG` cue | hardware |
| UNPLUG | Unplug D-MOTO from the OTG adapter | each `UNPLUG` cue | hardware |
| DLG | Do what the cue says on the **top** USB dialog of the host: tick the box or not, then press OK | each `DLG` cue | a SystemUI dialog; no verb reaches it, injected taps miss on D-HP (rig facts), and the round grades what a person's tick does |
| CLEAR | On the App info screen that the script opens, press Clear defaults (API 35: Open by default, then Clear default preferences) | before R2 and R3, only if cued | Settings is another app, and no shell command clears USB defaults without `MANAGE_USB` |
| PH | Look at D-MOTO; if it shows an Android Auto or USB prompt, accept it | only if cued | phone UI |
| COUNT | Tell the host, for each plug of the run just ended: how many USB dialogs you saw, and how many had a checkbox | end of each run | the second column of the dialog count |

Open the cue terminal first: `tail -n0 -F $OUT/cues.log`. Each cue beeps and names the stage, the run, the plug and
the action. Tell the operator once, at the start: "Act only on a cue. On a DLG cue, act on the top dialog only, and tick
only when the cue says tick. Do not touch D-MOTO without a PH cue."

## 5. Helpers

Run every adb call against one unit in sequence (template rule 8). All parsing runs on the host: D-SAM has no `sed`,
`awk` or `busybox`.

**`lib.sh`**
```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
OUT=rig-data/rounds/usb-dialog-round1; mkdir -p $OUT; export LC_ALL=C
SAM=30041c35642d2200; HP=CNU350BGBJ; POCO=4f4027e9; MOTO=ZY22GC3BM4
# Set in Prepare: HP_W, SAM_W, POCO_W, MOTO_W (ip:5555). Set per stage: ST (H|S|P), HU (the host's ip:5555), HUNAME.
A() { adb -s "$HU" "$@"; }
F() { echo $OUT/$ST-hu.txt; }
send() { a=$1; shift; A shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3
  timeout 3 adb -s "$MOTO_W" shell log -t RIGMARK "RIGMARK $1" >/dev/null 2>&1
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
cue() { printf '\a'; echo "$(date +%T) $*" | tee -a $OUT/hand-steps.log >> $OUT/cues.log; }
launch() { A shell am start -n $MAIN >/dev/null; sleep 2; }
cap() { A logcat -G 16M >/dev/null 2>&1; A logcat -c; stdbuf -oL adb -s "$HU" logcat -v time >> $(F) & echo $! > $OUT/$ST-hu.pid; }
alive() { kill -0 "$(cat $OUT/$ST-hu.pid)" 2>/dev/null && return 0
  echo "$(date +%T) $ST capture restarted" >> $OUT/marks.txt
  stdbuf -oL adb -s "$HU" logcat -v time >> $(F) & echo $! > $OUT/$ST-hu.pid; }
uncap() { kill "$(cat $OUT/$ST-hu.pid)" 2>/dev/null; }
# after MARK STRING: count of a fixed string after the last line that ends with "AutomationMarker: MARK"
after() { tr -d '\r' < $(F) | sed -n "/AutomationMarker: $1\$/,\$p" | grep -acF -- "$2"; }
waitn() { local t=0; while [ $t -lt $3 ]; do [ "$(after $1 "$2")" -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$ST.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }
bat() { A shell dumpsys battery | tr -d '\r' | grep -a -m1 level | tr -dc 0-9; }
usbdev() { A shell ls /sys/bus/usb/devices | tr -d '\r' | grep -acE '^[0-9]+-[0-9.]+$'; }
# USBBASE = usbdev with D-MOTO unplugged, read at stage start (0 on most hosts: root hubs only)
waitdev() { local t=0; while [ $t -lt $2 ]; do if [ "$1" = on ]; then [ "$(usbdev)" -gt $USBBASE ] && return 0
  else [ "$(usbdev)" -le $USBBASE ] && return 0; fi; sleep 1; t=$((t+1)); done; return 1; }
# wins: the system USB dialog windows now on the host, for example "UsbConfirmActivity UsbPermissionActivity"
wins() { A shell dumpsys window windows 2>/dev/null | tr -d '\r' | grep -aE '^ *Window #[0-9]+ Window\{' \
  | grep -aoE 'Usb(Confirm|Permission|Resolver)Activity' | tr '\n' ' '; }
# comp: the state of UsbAttachedActivity as dumpsys package lists it (enabled, disabled, or empty = manifest default)
comp() { A shell dumpsys package $PKG 2>/dev/null | tr -d '\r' | awk '/disabledComponents:/{s="disabled"}
  /enabledComponents:/{s="enabled"} s && /app\.UsbAttachedActivity/{print s; exit}'; }
# usbdef TAG: save dumpsys usb, count the lines that name our package, and list the vendor ids next to them
usbdef() { A shell dumpsys usb > $OUT/$1-usb.txt 2>&1
  DEFN=$(tr -d '\r' < $OUT/$1-usb.txt | grep -aciF headunitrevived)
  DEFV=$(tr -d '\r' < $OUT/$1-usb.txt | grep -a -B8 -iF headunitrevived | grep -aoE '(mVendorId=|vendor_id=)[0-9]+' \
    | grep -aoE '[0-9]+$' | sort -u | tr '\n' ' ')
  printf '%s\t%s\tpkg_lines=%s\tvendors=%s\n' "$(date +%T)" "$1" "$DEFN" "${DEFV:-none}" >> $OUT/defaults.tsv; }
# snap TAG: one dialog snapshot into dialogs.tsv; sets TOP (the focused USB dialog, or other) and CB (checkboxes on it)
snap() { local t=$1 w ph; A shell dumpsys window windows > $OUT/$t-win.txt 2>&1
  w=$(tr -d '\r' < $OUT/$t-win.txt | grep -aE '^ *Window #[0-9]+ Window\{' | grep -aoE 'Usb(Confirm|Permission|Resolver)Activity' | tr '\n' '+')
  TOP=$(tr -d '\r' < $OUT/$t-win.txt | grep -a -m1 'mCurrentFocus' | grep -aoE 'Usb(Confirm|Permission|Resolver)Activity' || echo other)
  sleep 0.3; A shell rm -f /sdcard/ud.xml; A shell uiautomator dump /sdcard/ud.xml >/dev/null 2>&1; sleep 0.3
  rm -f $OUT/$t-ui.xml; A pull /sdcard/ud.xml $OUT/$t-ui.xml >/dev/null 2>&1
  CB=$(grep -ao 'class="android.widget.CheckBox"' $OUT/$t-ui.xml 2>/dev/null | wc -l | tr -d ' ')
  [ -f $OUT/$t-ui.xml ] || CB=unread
  ph=A; [ "$(after $P-plug 'USB accessory device attached, connecting.')" -ge 1 ] && ph=B
  printf '%s\t%s\tphase=%s\twindows=%s\ttop=%s\tcheckbox=%s\t%s\n' "$(date +%T)" "$t" "$ph" "${w:-none}" "$TOP" "$CB" \
    "$(grep -ao 'text="[^"]\+"' $OUT/$t-ui.xml 2>/dev/null | head -5 | tr '\n' ' ')" >> $OUT/dialogs.tsv; }
# answer RULE: the DLG cue for the top dialog. R1: OK only. R2: tick ours, never the confirm. R3: tick every box.
answer() { local act="press OK. Do not tick anything"
  case "$1:$TOP" in
    R2:UsbPermissionActivity) [ "$CB" != 0 ] && act="tick the box if there is one, then press OK";;
    R2:UsbConfirmActivity) act="press OK. Do NOT tick the box";;
    R3:*) [ "$CB" != 0 ] && act="tick the box if there is one, then press OK";;
  esac
  cue "DLG $P: top dialog on $HUNAME is $TOP: $act"; }
# moto_dump TAG: D-MOTO's buffer after its wireless adb comes back (cap 60 s)
moto_dump() { local t=0; while [ $t -lt 60 ]; do adb connect $MOTO_W >/dev/null 2>&1
    adb -s $MOTO_W shell true 2>/dev/null && { adb -s $MOTO_W logcat -d -v time > $OUT/$1-phone.txt; echo "$1 phone ok" >> $OUT/marks.txt; return 0; }
    sleep 2; t=$((t+2)); done; echo "$1 phone UNREACHABLE" >> $OUT/marks.txt; return 1; }
```

**`cycle.sh`**
```bash
# cycle P RULE: one plug of D-MOTO into the host, every dialog answered by RULE, then the unplug.
# P is the cycle label, for example H-R2-p1. Needs lib.sh. Prints one line per cycle to cycles.tsv.
# Returns 0 for a graded cycle, 1 for a cycle to retry (no plug, no SSL, no answer), 2 for low battery.
cycle() { P=$1; local R=$2 n=0 t0 sslat=0 asked=0 noans=0 w
  [ "$ST" = P ] || [ "$(bat)" -ge 30 ] || { echo "$P BATTERY_LOW $(bat)" >> $OUT/marks.txt; return 2; }
  alive; launch; A shell input keyevent 82
  [ "$(usbdev)" -le $USBBASE ] || { cue "UNPLUG $P: unplug D-MOTO from $HUNAME"; waitdev off 60; }
  usbdef $P-before
  adb connect $MOTO_W >/dev/null 2>&1; adb -s $MOTO_W logcat -c 2>/dev/null
  mark $P-cue; cue "PLUG $P: plug D-MOTO's cable into the OTG adapter on $HUNAME now"
  waitdev on 120 || { echo "$P NO_PLUG" >> $OUT/marks.txt; return 1; }
  mark $P-plug; t0=$SECONDS
  while [ $((SECONDS-t0)) -lt 150 ]; do
    alive; w=$(wins)
    if [ -n "$w" ]; then
      sleep 5        # the stacking window under test: ours follows Android's dialog by 1 s (B) or 2 s (A)
      n=$((n+1)); snap $P-d$n; answer $R; mark $P-d$n
      local before="$(wins)" k=0
      while [ "$(wins)" = "$before" ] && [ $k -lt 90 ]; do sleep 2; k=$((k+2)); done
      [ $k -ge 90 ] && { cue "DLG $P (again): $TOP is still on $HUNAME"; k=0
        while [ "$(wins)" = "$before" ] && [ $k -lt 60 ]; do sleep 2; k=$((k+2)); done
        [ $k -ge 60 ] && { echo "$P-d$n NO_ANSWER" >> $OUT/marks.txt; noans=1; break; }; }
      continue
    fi
    if [ $sslat -eq 0 ] && [ "$(after $P-plug 'SSL handshake complete')" -ge 1 ]; then sslat=$SECONDS; fi
    [ $sslat -gt 0 ] && [ $((SECONDS-sslat)) -ge 8 ] && break        # 8 s with no dialog after SSL
    if [ $sslat -eq 0 ] && [ $asked -eq 0 ] && [ $((SECONDS-t0)) -ge 60 ]; then
      cue "PH $P: look at D-MOTO; accept an Android Auto or USB prompt if one shows"; asked=1; fi
    sleep 1.5
  done
  [ $sslat -gt 0 ] && [ "$ST" != P ] && waitn $P-plug 'Throughput over' 30
  sleep 5; usbdef $P-after; mark $P-end
  cue "UNPLUG $P: unplug D-MOTO from $HUNAME"; waitdev off 60; mark $P-unplug; moto_dump $P
  printf '%s\tdialogs=%s\tssl=%s\treq=%s\tact_intent=%s\tstale=%s\n' "$P" "$n" "$(after $P-plug 'SSL handshake complete')" \
    "$(after $P-plug 'Requesting USB permission for ')" "$(after $P-plug 'UsbAttachedActivity.onCreate | USB Intent: ')" \
    "$(( $(after $P-plug 'UsbLauncher: stale accessory ') + $(after $P-plug 'UsbLauncher: targeted recovery') ))" >> $OUT/cycles.tsv
  [ $sslat -gt 0 ] && [ $noans -eq 0 ] && return 0; echo "$P RETRY" >> $OUT/marks.txt; return 1; }
# runplugs RUN RULE N: N graded cycles, at most 2 retries (the stop rule). Returns 2 on low battery.
runplugs() { local good=0 bad=0 k=0 rc
  while [ $good -lt $3 ] && [ $bad -lt 2 ]; do k=$((k+1)); cycle $ST-$1-p$k $2; rc=$?
    [ $rc -eq 2 ] && return 2; if [ $rc -eq 0 ]; then good=$((good+1)); else bad=$((bad+1)); fi; done
  echo "$ST-$1 graded=$good retries=$bad" >> $OUT/marks.txt; return 0; }
```

`after $P-plug` counts to the end of the capture, and `cycles.tsv` is written right after the unplug, so each count
covers the plug and its unplug only. At VERBOSE each session prints `SSL handshake complete` twice (the INFO and the
DEBUG line), so `ssl=2` is one session. In section 8, "p1" means the first graded cycle of a run and "p2" the next
graded one, whatever `k` the retries gave them.

**`setlisten.sh`**
```bash
# setlisten true|false: write the key with the app stopped, launch, and wait up to 20 s for the component to follow.
setlisten() { local want=disabled; [ "$1" = true ] && want=enabled
  A shell am force-stop $PKG
  # write bool:listen-for-usb-devices=$1 with $RIG_TOOLKIT/set_prefs_runas_host.py on $HU, then read it back
  launch; local t=0; while [ $t -lt 20 ]; do [ "$(comp)" = $want ] && break; sleep 2; t=$((t+2)); done
  echo "$ST listen=$1 comp=$(comp)" | tee -a $OUT/marks.txt; }
# cleardef TAG: open App info and cue CLEAR when a default of ours is listed; wait for dumpsys usb to empty (cap 120 s)
cleardef() { usbdef $1-pre; [ "$DEFN" -eq 0 ] && { echo "$1 nothing to clear" >> $OUT/marks.txt; return 0; }
  A shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:$PKG >/dev/null
  cue "CLEAR $1: on $HUNAME, App info for Open Headunit is open. Press Clear defaults (API 35: Open by default, then Clear default preferences). Say so if there is no such button"
  local t=0; while [ $t -lt 120 ]; do sleep 3; t=$((t+3)); A shell dumpsys usb 2>/dev/null | tr -d '\r' | grep -aqiF headunitrevived || break; done
  usbdef $1-post; A shell input keyevent 3; sleep 1; launch; }
```

## 6. The deciding lines

All app strings verified with `grep -F -r` in `app/src` at `947b3ae9`, and all print at INFO. The round runs at
`log-level=0` (VERBOSE) because the ticket asks for it; nothing here needs more than INFO. Grep with `-a`, in
`<ST>-hu.txt`, between `<P>-plug` and `<P>-unplug` for one cycle.

| Id | Line (fixed substring) | Read |
|---|---|---|
| L-RX | `AutomationReceiver: ` | a verb landed; a step with none is void, not a FAIL |
| L-ACT | `UsbAttachedActivity.onCreate | USB Intent: ` | Android routed an attach to our activity (the message part is in the source; the caller prefix is `AppLog`'s) |
| L-NORM | `Normal USB device attached: ` | the service heard A |
| L-FALL | `UsbAttachedActivity didn't handle ` | the 2 s fallback ran for A |
| L-ASKA | `Single USB auto-connect: device found but no permission, requesting...` | the service asks for A |
| L-ASKL | `Found known USB device but no permission: ` | the last-session branch asks for A (expected 0, the key is off) |
| L-ACC | `USB accessory device attached, connecting.` | the service heard B |
| L-POLLOK | `Accessory-mode permission arrived after ` | B's grant came from Android inside the poll |
| L-ASKB | `Accessory-mode device has no permission (re-enumerated); requesting permission: ` | the poll ran out and the service asks for B |
| L-REQ | `Requesting USB permission for ` | every `requestPermission` of the service; the name carries VID:PID |
| L-ACTASK | `Usb in accessory mode but no permission. Requesting...` | the activity asks for B |
| L-ACTOK | `Usb in accessory mode and has permission. Starting AapService.` | the activity got B with a grant |
| L-SW | `Switching USB device to accessory mode ` | the activity switched A |
| L-GR | `USB permission granted for ` / `USB permission denied for ` | the service's permission replies |
| L-SSL | `SSL handshake complete` | a session formed |
| L-MS | `Media Sink Setup Request: ` | the phone answered our service discovery |
| L-TP | `Throughput over ` | `rendered=N` |
| L-STALE | `UsbLauncher: stale accessory `, `UsbLauncher: targeted recovery` | the only code that differs from `v.4.0.0-alpha`; MUST be 0 |
| D-MATCH | `MATCH! Starting AapService` | discard rule (template §4) |

System strings, not in `app/src`: `UsbConfirmActivity`, `UsbPermissionActivity`, `UsbResolverActivity` (window
names in `dumpsys window windows`), `mCurrentFocus`, `MtpNotificationManager` (instrument only). Phone strings, from
AOSP and Android Auto, not in `app/src`: `entering USB accessory mode` (`UsbDeviceManager` on D-MOTO),
`USB_ACCESSORY_ATTACHED`, `Critical error`. **FAIL lines, every cycle:** `FATAL EXCEPTION` in a block that names
`com.andrerinas`, and `ANR in com.andrerinas`.

The files the runs write and the host grades from:

| File | One row per | Fields |
|---|---|---|
| `dialogs.tsv` | dialog snapshot | time, `<P>-d<n>`, `phase=A|B`, `windows=`, `top=`, `checkbox=`, the first five texts |
| `defaults.tsv` | `dumpsys usb` read | time, tag, `pkg_lines=`, `vendors=` (decimal: 6353 is `0x18D1`, so B; 8888 is `0x22B8`, so A) |
| `cycles.tsv` | plug | `dialogs=`, `ssl=`, `req=`, `act_intent=`, `stale=` |
| `<P>-phone.txt` | plug | D-MOTO's buffer from before the plug to after the unplug |

`phase=` names the identity a snapshot belongs to: B from the first L-ACC after the plug, A before it. If a
`vendors=` field cannot be read (an API 35 format this brief did not foresee), the host reads `<tag>-usb.txt` and says
which entry is ours.

The diagnosis's log instrument, with one fix: it counted both sources of `USB Intent: ` as "routed to our activity".
Run it on each run's slice (section 8, after each run).

**`usb-dialog-instrument.sh`**
```bash
#!/usr/bin/env bash
# Counts the USB permission landmarks in an OHU/HUR log or a logcat capture.
# Usage: usb-dialog-instrument.sh <log> [<log> ...]
# Prints counts and the first 5 line numbers per landmark. No verdict.
set -u
landmarks=(
  "attach routed by Android to our activity|UsbAttachedActivity\.onCreate \| USB Intent: "
  "service receiver saw an attach or detach|UsbReceiver\.onReceive \| USB Intent: "
  "service: normal-mode attach heard|Normal USB device attached: "
  "service: accessory attach heard|USB accessory device attached, connecting\."
  "activity: switched a normal-mode device|Switching USB device to accessory mode"
  "activity: accessory, no permission, asks|Usb in accessory mode but no permission. Requesting"
  "activity: accessory, has permission|Usb in accessory mode and has permission"
  "service: 2 s fallback ran (activity did not act)|UsbAttachedActivity didn't handle"
  "service: normal-mode device, no permission, asks|Single USB auto-connect: device found but no permission"
  "service: last-session device, no permission, asks|Found known USB device but no permission"
  "service: accessory grant arrived during the poll|Accessory-mode permission arrived after"
  "service: accessory poll expired, asks|Accessory-mode device has no permission \\(re-enumerated\\)"
  "service: permission request sent|Requesting USB permission for"
  "service: permission reply|USB permission (granted|denied) for"
  "service: stale-accessory recovery (differs from v.4.0.0-alpha)|UsbLauncher: (stale accessory|targeted recovery)"
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
    printf '%4d  %-58s %s\n' "$n" "$label" "$lines"
  done
done
```

The system landmarks in the instrument are a second reading only. On D-SAM they are 0 whatever happens, because
API 19 logs no activity start; `dialogs.tsv` decides.

## 7. Settings keys

Write with the app stopped, on each host, with `$RIG_TOOLKIT/set_prefs_runas_host.py` (no host is rooted for this
round). Back up `settings.xml` to `$OUT/backup-<ST>.xml` first, and diff at the end with carriage returns stripped.
Read every key back after every write and after any install (`adb install -r` can wipe the file, `tooling.md`).

```
int:log-level=0 int:onboarding-version=2 bool:allow-external-configuration=true
int:wifi-connection-mode=0 set:connection-modes=usb
bool:auto-start-on-usb=false bool:auto-connect-last-session=false bool:auto-connect-single-usb=false
set:allow-devices= bool:use-libusb=false bool:kill-on-disconnect=false bool:enable-floating-button=false
bool:listen-for-usb-devices=false
```

Delete `video-profile-starvation-cap` if it is set. Leave the audio keys as they are (the rig's worst case is
deliberate) and leave `screen-orientation` on D-SAM as found. Why the less obvious keys:

- The three auto-connect keys off and `allow-devices` empty put A on the same path as the diagnosis: the 2 s
  fallback, then `performSingleConnect`. A non-empty `allow-devices` that does not list D-MOTO would stop the service
  asking at all.
- `connection-modes=usb` and mode 0 keep the wireless stack out of the capture.
- Record `usb-blacklist`. If it names D-MOTO, the stage is void: empty it and say so.
- Record `enable-car-launcher` and do not change it. On 4.x a change toggles a component, which clears the USB defaults
  mid-run (diagnosis M4).
- `listen-for-usb-devices` is written by `setlisten` (section 5) per run, never in this block after R1.

## 8. Runs

Each stage is one capture with three runs, chained, because R1's end state differs from R2's start only by the key
and the defaults, and R2's end is R3's start after one clear. Cycle labels are `<ST>-R<n>-p<k>`.

**Stop rule.** A cycle with `NO_PLUG`, no L-SSL by the end of its loop, or `NO_ANSWER` is retried once with the next
`k`. Two such cycles in one run end the run, graded on what it has (INCONCLUSIVE when fewer than 2 cycles graded).
`BATTERY_LOW` ends the stage. Two runs INCONCLUSIVE or UNTESTABLE in one stage end that stage.

### Prepare (20 min)

1. `get_apk.sh` (section 1). Charge D-HP to 100% and D-SAM to 80% or more on a separate charger.
2. On D-HP, D-SAM and D-POCO, with the app stopped: back up `settings.xml`, record the live APK md5 and
   `dumpsys usb > $OUT/prep-<unit>-usb.txt`, and install `apk-M.apk` with `adb install -r -d` if the md5 differs.
   Run the identity check (section 1). Record `getprop ro.build.version.sdk` and `date` on each unit beside the host's
   `date`, and D-MOTO's offset the same way.
3. D-SAM: `adb -s $SAM shell pm list features | grep -ac 'android.hardware.usb.host'` MUST print 1. If it prints 0,
   stage S is UNTESTABLE (no USB host); say so and skip its wireless adb.
4. D-POCO: `am start -a android.intent.action.VIEW -d headunit://exit`, then force-stop its app (it is a phone in other
   rounds; template §8 role-swap rule).
5. Wireless adb, each while still on its PC cable: `adb -s <serial> tcpip 5555`, read `ip -4 addr show wlan0`,
   `adb connect <ip>:5555`, and record `HP_W`, `SAM_W`, `POCO_W`, `MOTO_W`. If D-SAM's connect fails, stage S is
   UNTESTABLE (no wireless adb).
6. D-MOTO: `svc bluetooth disable` and read it back (`dumpsys bluetooth_manager`); WiFi stays on. `svc power stayon
   true`. Record Gearhead's `versionName` and D-MOTO's USB functions (section 3). `logcat -G 16M`.
7. Copy `usb-dialog-instrument.sh` from this brief and `chmod +x` it.

### One stage (run it for H, then S, then P)

Set the stage variables, then run the block. H: `ST=H HU=$HP_W HUNAME=D-HP`. S: `ST=S HU=$SAM_W HUNAME=D-SAM`.
P: `ST=P HU=$POCO_W HUNAME=D-POCO`, and only after the operator said yes to the stage P question (section 3).

**`stage.sh`**
```bash
. ./lib.sh; . ./cycle.sh; . ./setlisten.sh
cue "CAB $ST: move the cables for stage $ST (brief section 4). D-MOTO stays unplugged"
adb connect $HU; adb connect $MOTO_W
[ "$ST" = P ] || [ "$(bat)" -ge 60 ] || { echo "$ST BATTERY_START $(bat)" >> $OUT/marks.txt; exit 1; }
th_gate || exit 1
A shell svc power stayon true; A shell am force-stop $PKG
# write the section 7 block on $HU and read it back
cap; launch; mark $ST-start
USBBASE=$(usbdev); echo "$ST USBBASE=$USBBASE" >> $OUT/marks.txt   # D-MOTO is unplugged here
setlisten false; usbdef $ST-start; STOP=0

mark $ST-R1-start                                             # R1: listen off, 3 plugs
runplugs R1 R1 3 || STOP=1
mark $ST-R1-end; cue "COUNT $ST-R1: tell the host, per plug, how many USB dialogs you saw and how many had a checkbox"

if [ $STOP -eq 0 ]; then setlisten true; cleardef $ST-R2-clear   # R2: listen on, no default, 3 plugs
  mark $ST-R2-start; runplugs R2 R2 3 || STOP=1
  mark $ST-R2-end; cue "COUNT $ST-R2: tell the host, per plug, how many USB dialogs you saw and how many had a checkbox"; fi

if [ $STOP -eq 0 ]; then cleardef $ST-R3-clear                  # R3: tick every box, then 3 reconnects
  mark $ST-R3-start; runplugs R3 R3 4 || STOP=1
  mark $ST-R3-end; cue "COUNT $ST-R3: tell the host, per plug, how many USB dialogs you saw and how many had a checkbox"; fi

cleardef $ST-end-clear; mark $ST-end
send ACTION_EXPORT_LOG; sleep 5                               # the export path is on the data= line; pull that file
th_end; uncap; A shell am force-stop $PKG
for r in R1 R2 R3; do tr -d '\r' < $(F) | sed -n "/AutomationMarker: $ST-$r-start\$/,/AutomationMarker: $ST-$r-end\$/p" > $OUT/$ST-$r.slice
  ./usb-dialog-instrument.sh $OUT/$ST-$r.slice > $OUT/$ST-$r.instrument.txt; done
cue "CAB $ST: stage $ST is over. Move the cables back"
```

`STOP=1` means low battery: the stage ends after the current run, and the runs it skipped are UNTESTABLE (battery). The
`COUNT` answers go into the results file as the operator column beside `dialogs.tsv`. The last `cleardef` removes
every default R3 stored, so the next round starts clean on 4.x. On D-POCO it cannot remove the persistent grant.

### R1, listen off (M1, H1)

Setup check before p1: `comp` read `disabled` (`marks.txt`). If it read anything else, R1 is UNTESTABLE.

R1 PASS needs, for every graded cycle:

- `dialogs.tsv`: exactly 2 snapshots, one `phase=A` and one `phase=B`, each `windows=UsbPermissionActivity+` and
  `top=UsbPermissionActivity` and `checkbox=0`.
- 0 `UsbConfirmActivity` and 0 `UsbResolverActivity` in any snapshot of the run.
- `cycles.tsv`: `act_intent=0`, `req=2` with two different VID:PID in the L-REQ names, `ssl` at least 1, `stale=0`.
- L-FALL at least 1 and L-ASKA at least 1 (A came from the 2 s fallback), and L-ASKB at least 1 (B from the poll).
- Phone: `<P>-phone.txt` has `entering USB accessory mode` at least once.

R1 FAIL (H1 refuted) if any snapshot shows `checkbox=` above 0, any `UsbConfirmActivity` window, or a cycle with 0
dialogs. `req=3` or more on a cycle is a third prompt: a defect of its own (template §7b), reported with the names.

### R2, listen on, no default (M2, H2): the point of the round

Setup check before p1: `comp` read `enabled`, and `defaults.tsv` row `<ST>-R2-clear-post` (or `-pre` when nothing
needed clearing) has no `6353` in `vendors=`. If B already has a default, R2 is UNTESTABLE on this stage.

The cue rule: tick our dialog (`UsbPermissionActivity`) and press OK; press OK on Android's confirm dialog
(`UsbConfirmActivity`) without the tick. That is what the reporter did.

R2 PASS needs:

- **p1:** at least one `phase=B` snapshot with `windows=` listing both `UsbConfirmActivity` and `UsbPermissionActivity`,
  `top=UsbPermissionActivity` and `checkbox=` 1 or more. L-ASKB at least 1. `<P>-after` in `defaults.tsv` has no
  `6353` in `vendors=`.
- **p2 and p3, each:** the same stacked `phase=B` snapshot again, L-ASKB at least 1, and no `6353` in `<P>-after`.
- Every cycle: `ssl` at least 1 and `stale=0`; phone `entering USB accessory mode` at least once.
- For the record, per cycle: the `phase=A` snapshots (on 4.x expect the same stacked pair on p1 and none after it,
  because p1's tick on ours stores A's default and A's confirm dialog closes by itself when A leaves the bus), and
  whether `8888` is in `<P>-after`.

R2 FAIL (H2 refuted) if p2 or p3 shows no `phase=B` snapshot, or any `<P>-after` lists `6353`.

**On D-POCO only:** if `prep-poco-usb.txt` or `P-R2-clear-post` shows a persistent grant for B, our dialog cannot
stack. Then a p2 or p3 that shows `windows=UsbConfirmActivity+` alone for `phase=B`, with no `6353` default after it,
still meets H2's second half; grade the stacked-pair bullet INCONCLUSIVE and say why.

### R3, positive control

Setup: `cleardef` ran. If the operator reports no Clear button, write `CLEAR_UNAVAILABLE` in Setup notes and run R3 on
the defaults as they stand; the control does not need an empty start. The cue rule: tick every box, then OK.

R3 PASS needs:

- **p1:** at least 1 snapshot, every one with a checkbox ticked by cue; `<P>-after` lists `6353`.
- **p2, p3 and p4, each:** 0 snapshots in `dialogs.tsv` (`dialogs=0` in `cycles.tsv`), `req=0`, L-ACTASK 0,
  `ssl` at least 1, L-MS at least 1, and L-TP with `rendered=` above 0 (stage P: L-MS only). L-ACTOK or L-POLLOK at least
  1 (B came with Android's grant). On 4.x, L-SW at least 1 (A went to our activity by its default).
- **Phone, p2 to p4, each:** `entering USB accessory mode` at least 1 and `USB_ACCESSORY_ATTACHED` at least 1 in
  `<P>-phone.txt` (Android Auto took the accessory). Count `Critical error` lines for the record only: Android Auto
  prints them on every unplug, and the dump holds the unplug.

R3 FAIL if p2, p3 or p4 shows a dialog. That FAIL also voids the R2 reading on that stage, because the control did not
hold.

A phone bullet with 0 of both strings on a cycle that reached L-SSL is INCONCLUSIVE (string drift on D-MOTO's ROM),
not FAIL. Quote one line of what D-MOTO logged instead near the accessory start, from `<P>-phone.txt`.

## 9. Do not re-run

Nothing in this thread is settled. Do not add the two scenarios that need a fix (the diagnosis's R4 and R5): the
fix branch does not exist yet. `main-smoke` round 1 grades wired sessions on D-HP and D-POCO; this round grades
dialogs, so it does not repeat those sessions' audio or video measurements.

## 10. Report back

1. R1 per host: dialogs per plug, checkboxes seen, `act_intent`, and the operator's count beside it. Does H1 hold?
2. R2 per host: on p2 and p3, was our dialog stacked on Android's confirm dialog for B, and did `dumpsys usb` list a
   default of ours for B after the answers? Does H2 hold?
3. R3 per host: dialogs on p2 to p4 (the control must read 0), and the phone's accessory lines.

Also: every cycle with `stale` above 0, any third prompt, every place the operator column and `dialogs.tsv` disagree
(a dialog hidden under `MainActivity` shows up that way), the record-audio warning text if any snapshot carries it
(H3), and what stage P left in D-POCO's `dumpsys usb`.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
UsbAttachedActivity.onCreate | USB Intent: 
USB Intent: 
Normal USB device attached: 
UsbAttachedActivity didn't handle 
Single USB auto-connect: device found but no permission, requesting...
Found known USB device but no permission: 
USB accessory device attached, connecting.
Accessory-mode permission arrived after 
Accessory-mode device has no permission (re-enumerated); requesting permission: 
Requesting USB permission for 
Usb in accessory mode but no permission. Requesting...
Usb in accessory mode and has permission. Starting AapService.
Switching USB device to accessory mode 
USB permission granted for 
USB permission denied for 
SSL handshake complete
Media Sink Setup Request: 
Throughput over 
UsbLauncher: stale accessory 
UsbLauncher: targeted recovery
MATCH! Starting AapService
FATAL EXCEPTION
ANR in com.andrerinas
UsbConfirmActivity
UsbPermissionActivity
UsbResolverActivity
mCurrentFocus
MtpNotificationManager
entering USB accessory mode
USB_ACCESSORY_ATTACHED
Critical error
```
