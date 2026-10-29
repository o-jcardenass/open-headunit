# usb-dialog, round 4 brief: "Connect when plugged in" decides whether a plug-in switches the phone

Published name: `usb-dialog-round4-brief.md`. Results go in `usb-dialog-round4-results.md`.
Evidence: upload `usb-dialog-round4-captures.zip` to the existing release `rig-evidence-usb-dialog`.

Units: D-HP (HP Slate 7 Plus, API 17) and D-POCO (POCO X3 NFC, API 35) as USB hosts, one at a time. D-MOTO (Motorola
edge 30 neo) is the wired phone in both stages. D-HU and D-SAM take no part (D-SAM has no USB host, round 1).

Queue line: `usb-dialog-round4-brief.md` (one arm, `fork/fix/usb-permission-dialog` `3dcbf76a`, force-pushed over
`67bc82d5`; units D-HP, D-POCO, D-MOTO; about 2 h 5 min, 2 h 20 min with the optional runs; B3 and B5 are the point).

Pairing: possible only with a round that uses D-HU alone. If this round is the second of a pair, it takes the lock
`/tmp/ohu-rig-usbdlg.lock`. Its hand steps are many and short, so the other round must have none during this one.

Hand steps: cable moves, one plug and one unplug per cycle, every USB system dialog by a fixed rule per run (section 5),
Clear defaults when cued, one count per run, and on D-POCO two SETTINGS steps and two SCREEN steps. Injected taps: none.

**Time budget.** Build 20 min, with Prepare steps 1 to 4 beside it. Then Prepare's install steps 10 min, stage H
25 min, L1b on D-HP 6 min, stage P 50 min (61 min with B7, B10 and B8), L1b on D-POCO 6 min, cable moves 5 min,
thermal gates about 5 min. **About 2 h 5 min.** Write the results file after every run.

| Part | Host | Runs, in this order, in one capture | Min |
|---|---|---|---|
| Stage H | D-HP, wireless adb, D-MOTO on its OTG port | B1 (3 plugs), B3 then B4 (1 cycle), B5, B9 | 25 |
| L1b on D-HP | D-HP on its PC cable, no phone | L1b | 6 |
| Stage P | D-POCO, wireless adb, D-MOTO on its OTG port | B1 (3 plugs), B2 (3 plugs), B3 then B4, B5 (with B7, optional), B6-off, B6-on, control dump, B9 (dump, then 1 cycle), B10 and B10n (optional), B8 (optional) | 50 |
| L1b on D-POCO | D-POCO on its PC cable, no phone | L1b | 6 |

**B3 and B5 are the point of the round.** With "Connect when plugged in" off, a plug-in must open the app and leave
the phone alone, with Android's dialog (B3) and with the defaults stored (B5). B4 and the second half of B5 prove the
USB button still connects in that state. B1 and B2 are the regression guards: with the key absent, every plug still
connects. B6 and B9 cover the settings-screen hold and detection off. L1b proves the boot start still runs with
detection off.

## 1. Build and baseline

One arm, no baseline build. Rounds 1 to 3 give every "before" number, and B1 reuses round 3's APK as the upgrade base
(section 8, Prepare).

| Ref | Commit | Tree |
|---|---|---|
| `fork/fix/usb-permission-dialog` | `3dcbf76a7256f25a9a4015e834db1f960d920773` | `fb4922ddd741b878d96c57d5a626667ba011628b` |

**History was rewritten.** The branch is force-pushed to `fork` over `67bc82d58c60a7e3d04a8412b854103294e4e826` (the lease), which had replaced round 3's `9f90e121`. It is 4 commits on `main`
`2018d8f09`: `9a3da0cf0` (USB: ask for permission only where Android shows no dialog of its own), `23b4b926f` (Home:
the USB button and the USB list through the single ask), `02ab8e302` (USB: the plug-in gate) and `3dcbf76a7`
(Settings: the rows). The dev host suite at this commit: 4344 JVM tests, 0 failures. Do not run the unit tests on the
rig. No other queued round shares this base.

**`get_apk.sh`**
```bash
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round4; mkdir -p $OUT
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh usb-dialog candidate main | tail -1)}   # record the path in Setup notes
git -C $WT fetch fork fix/usb-permission-dialog
git -C $WT checkout --detach 3dcbf76a7256f25a9a4015e834db1f960d920773    # fails if the force-push is missing: stop, UNTESTABLE (build)
git -C $WT rev-parse 'HEAD^{tree}'                 # MUST print fb4922ddd741b878d96c57d5a626667ba011628b
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
cp rig-data/apks/*.apk $OUT/apk-C.apk               # at once: build_hur.sh deletes the previous APK
md5sum $OUT/apk-C.apk                                # record in Setup notes
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF 'UsbLauncher: not switching'                         # MUST be 1 or more
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF 'Connect when plugged in is off; opening the app'    # MUST be 1 or more
```

Both strings are new in `02ab8e302`, so round 3's APK cannot carry them. Run the same two greps on round 3's APK in
Prepare: both MUST read 0 there.

**Identity on each host, after the install:** `send ACTION_QUERY_STATE` carries `3dcbf76a`, and the md5 of the pulled
live APK matches `apk-C.apk`. Install with `adb install -r -d`. Never uninstall.

## 2. What this is and why

Issue #1185 and PR #1207. Round 3 passed the dialog fixes (9 of 9). The owner then asked for honest labels on the
USB auto-start rows. The plan is `.claude/sdlc/gh-1185/plan-r3.md`. Its finding: the old row "Auto-start on USB" did not
decide whether a plug-in connects. With it off, the service's 2 s fallback still switched any single Android phone
(the `Fallback: force=true` branch). So a row that said "connect" could not stop a connect.

The candidate's change:

1. **A new key, `usb-connect-on-plug-in`, row "Connect when plugged in".** Default `true` when absent, so every install
   keeps plug-and-connect with no migration write. Off: a plug-in opens the app and does not switch the phone. The USB
   button still connects. `UsbPlugInPolicy.maySwitchNormal` gates only the `PLUG_IN` trigger, on the stored key AND
   `listen-for-usb-devices`. A re-attach within 30 s (`GLITCH_WINDOW_MS`) of a USB session end the user did not ask
   for passes, so a cable glitch reconnects.
2. **Where the gate sits.** `UsbAttachedActivity` with the key off logs
   `UsbAttachedActivity: Connect when plugged in is off; opening the app, not switching <name>` and opens
   `MainActivity`. The listener's 2 s fallback and the attach screen's hand-off pass `Trigger.PLUG_IN` into
   `checkAlreadyConnected`, which then logs `UsbLauncher: not switching <name> on a plug-in: <reason>` and returns. The
   reason names the setting that refused: `Connect when plugged in is off` with detection on, and
   `Detect plugged-in phones/AA dongles is off` with detection off. That refusal comes before the settings-screen hold. A held plug-in replays with its trigger
   (`checking USB now (trigger=PLUG_IN)`). The press (`USER`), reconnects, wakes, accessory identities and the boot start
   are not gated.
3. **"Open the app when plugged in"** is the old `auto-start-on-usb`. Its automatic readers now use
   `effectiveAutoStartOnUsb` = stored AND connect AND listen.
4. **`BootCompleteReceiver`'s USB branch** reads `auto-start-on-usb` AND `usb-connect-on-plug-in` from the device
   storage mirror (API 24 and up) or `settings.xml` (below). It does not read Listen, so the boot start survives
   detection off (L1b).
5. **The screens.** "Detect plugged-in phones/AA dongles" (`listen-for-usb-devices`) moved to Settings > General,
   after "Use Native USB Driver". The Auto-start USB group is row 1 "Connect when plugged in", row 2 "Open the app when
   plugged in" (shown when Listen and row 1 are on), row 3 "Reopen on reconnection" (shown when row 2 is also on). With
   Listen off, row 1 stays visible and disabled, and its description reads
   `Turn on "Detect plugged-in phones/AA dongles" in General settings first.`

## 3. What is different about this round

- **Round 3's rig notes are in the scripts.**
  - The cue terminal command is a literal path (section 5). Prepare prints it once more.
  - D-MOTO's phone dump retries over WiFi for 180 s. If that fails, the script cues D-MOTO onto its PC cable, dumps
    the buffer over USB, and turns wireless adb back on (`moto_dump`, section 6).
  - Charge gates on the host that feeds D-MOTO over OTG: D-HP starts at 60% or more and stops at 30%; D-POCO starts at
    50% or more and stops at 25%. Charge D-HP to 100% in Prepare. If two plugs in a row print `NO_PLUG` on D-HP,
    reseat the OTG adapter in its port (round 2: the connector, not the code).
- **The glitch window can make a plug switch on purpose.** A USB session that ends by an unplug opens a 30 s window.
  A plug inside it switches even with the key off. So B5 and B6-off start 45 s or more after the previous unplug
  (`glitchwait`). A `setkeys` force-stop also clears the window. B7 (optional) uses the window as its own test.
- **B6-off expects no replay line.** The refusal comes before the hold, so with the key off nothing is held and the
  settings close replays nothing. B6-on (key on) is the control that makes the replay line print with
  `trigger=PLUG_IN`.
- **The settings screens need hands on D-POCO.** `SettingsActivity` is not exported, and no shell route opens it there
  (`brief-traps`, automatic-dpi-panel round 1). The operator opens it (SETTINGS and SCREEN steps). The script reads the
  screen with `uiautomator dump`, then presses Home, because a visible settings screen holds every USB check. The
  dumps run on D-POCO only: they test app code, not the host.
- **The old round 4 brief's reachability row was wrong.** It used "Auto-start on Bluetooth", but `AutoStartFragment`
  hides the Bluetooth rows when `connection-modes` is `usb` only. This brief uses "Start on screen on" as the arrival
  row, and the control dump (all three USB rows shown) as proof that the dump reaches below row 1.
- **B2 runs on D-POCO only.** D-HP keeps the route verdict in the app process, so every `setkeys` force-stop loses it.
  B2's first plug would then stack our dialog over Android's (round 3, seed). Round 2's R4a covered D-HP. On D-POCO the
  verdict is stored with the boot count. If `usb-attach-route-boot` differs from `boot_count`, the stage seeds first.
- **B1 is an upgrade where the rig still has round 3's APK.** Prepare installs
  `rounds/usb-dialog-round3/apk-C.apk` (md5 `8f3889d4b75a4606074d8d4d7282cdef`), writes the keys with it, and then
  installs the candidate over it. If that file is missing or its md5 differs, B1 runs with the key deleted on the
  candidate. Record which form ran (`form=` in `marks.txt`).
- **Two runs differ from the review table on purpose.** B7 tests the glitch window with an unplug and a replug inside
  10 s: D-MOTO's wireless adb drops in accessory mode, so `svc usb setFunctions` cannot be sent mid-session. B10 tests
  the wake gate with D-MOTO unplugged: with the key on, a plugged phone is already in a session, and the wake skips.
- **A force-stop before a reboot blocks the boot receiver** (`rig-quirks/topics/lifecycle.md`). L1b stops the app with
  `ACTION_STOP_SERVICE` and reads `stopped=false` before the reboot.
- **D-POCO's boot start may meet the API 35 rule on boot-time foreground services.** Then the log shows
  `ForegroundServiceStartNotAllowedException/Exception caught in onCreate: ` right after `AapService creating...`. That
  is the same on `main`. Record it, do not grade it. D-HP has no such rule.
- **D-POCO may hold a lasting grant for D-MOTO.** Round 3 read `serial_outside=0`. Prepare reads it again. If it is
  above 0, the dialog items for that identity are INCONCLUSIVE, and the results say why.
- **D-MOTO keeps USB debugging on**, as in rounds 1 to 3. Its normal identity A read `22B8:2E81` with `mtp=no`. Its
  Bluetooth stays off all round.
- **Expected INCONCLUSIVE:** L1b on D-POCO if the boot broadcast never reaches the app; B8 if
  `HomeFragment: Requesting single-USB auto-connect via AapService` never prints. `Throughput over` is not graded.
- **Thermal.** `rig-quirks/topics/tooling.md`: `rig_thermal.sh wait 75` before each stage and each L1b (once more on
  exit 1), `rig_thermal.sh watch` during them, and the round stops as UNTESTABLE (host thermal) after a second failed
  wait.
- Read `rig-quirks/topics/tooling.md`, `topics/lifecycle.md`, `units/D-HP.md`, `units/D-SAM-and-D-HP.md`,
  `units/D-POCO.md`, `units/D-MOTO.md` and template §7b before the first run.

## 4. Settings keys

Write with the app stopped, with `$RIG_TOOLKIT/set_prefs_runas_host.py` through `writeprefs` (section 6; neither host
is rooted). `upgrade.sh` backs up `settings.xml` to `$OUT/backup-<ST>.xml` before its first write. `writeprefs` reads
every key back after every write. At the end of the round, restore each backup with the app stopped and diff it with
carriage returns stripped.

The base block, `BASEKEYS` in `lib.sh`, written once per host by `upgrade.sh` in Prepare. The stages do not rewrite it,
so B1 runs on the file that the old APK left:

```
int:log-level=2 int:onboarding-version=2 bool:allow-external-configuration=true
int:wifi-connection-mode=0 set:connection-modes=usb
bool:auto-connect-last-session=false bool:auto-connect-single-usb=false
set:allow-devices= bool:use-libusb=false bool:kill-on-disconnect=false bool:enable-floating-button=false
bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:auto-start-on-wifi=false
```

`upgrade.sh` also deletes `video-profile-starvation-cap` and `usb-connect-on-plug-in`. Keep `usb-attach-route-boot`.
Leave the audio keys as they are (the rig's worst case is deliberate) and leave `screen-orientation` as found. Why the
less obvious keys:

- `log-level=2` (INFO) is enough. Every app line this round greps prints at INFO, except the
  `ForegroundServiceStartNotAllowedException` line, which is `AppLog.e`.
- `allow-external-configuration=true`: `ACTION_EXPORT_LOG` is gated on it. `ACTION_LOG_MARKER` is not.
- The two auto-connect keys off and `allow-devices` empty: each plug reaches the 2 s fallback, which is the path the
  new gate covers. B8 turns `auto-connect-single-usb` on by itself.
- The three other auto-start keys off: `BootCompleteReceiver` tries boot, then screen-on, then USB, so L1b takes the
  USB branch. Screen-on off also keeps `onScreenOnAutoStart` out of the B10 wake.

The per-run keys, written by `setkeys` (`del` means the key is removed, so its default applies):

| `setkeys` tag | `listen-for-usb-devices` | `usb-connect-on-plug-in` | `auto-start-on-usb` | `reopen-on-reconnection` | Other | Runs |
|---|---|---|---|---|---|---|
| (`upgrade.sh`) | `true` | del | `false` | `true` | | B1 |
| `b2` | `true` | del | del | `true` | | seed, B2 |
| `b3` | `true` | `false` | `false` | `true` | | B3, B4, B5, B7, B6-off |
| `b6on` | `true` | `true` | `false` | `true` | | B6-on |
| `ctl` | `true` | `true` | `true` | `true` | | control dump, B10 |
| `b9` | `false` | `true` | `true` | `true` | | B9 dump, B9 |
| `b10n` | `true` | `false` | `true` | `true` | | B10n |
| `b8` | `true` | `false` | `false` | `true` | `auto-connect-single-usb=true`, `has-accepted-disclaimer=true` | B8 |
| `l1b` | `false` | `true` | `true` | | `auto-start-on-boot=false`, `auto-start-on-screen-on=false`, `auto-start-on-wifi=false` | L1b |

**The device storage mirror (D-POCO only).** On API 24 and up, `BootCompleteReceiver` and the locked-boot branch of
`UsbAttachedActivity` read
`/data/user_de/0/com.andrerinas.headunitrevived/shared_prefs/settings_device_protected.xml`. `App.onCreate` copies
`auto-start-on-boot`, `auto-start-on-usb`, `usb-connect-on-plug-in` and `auto-start-on-wifi` into it at every launch,
but not `auto-start-on-screen-on` and not `listen-for-usb-devices` (no code reads the Listen mirror). `setkeys`
launches the app, so the mirror follows `settings.xml`. `mirror` reads it after every `setkeys` on D-POCO and records
the four keys. `l1b.sh` corrects a `true` screen-on value with `mirrorfix.sh`. Below API 24 (D-HP) the receiver reads
`settings.xml`.

Round 3 found no `usb-blacklist` and no `enable-car-launcher` key on either host. Read both again in Prepare. If the
blacklist names D-MOTO, empty it and say so. If `enable-car-launcher` is `true`, row 3 is never shown, and the dump
items for it are INCONCLUSIVE.

## 5. Cabling, hand steps and the answer rules

| Part | On the PC | On an OTG port | Wireless adb |
|---|---|---|---|
| Prepare | D-HP, D-POCO, D-MOTO | | |
| Stage H | D-POCO | D-MOTO on D-HP, at each PLUG cue | D-HP, D-MOTO |
| L1b on D-HP | D-HP, D-POCO | nothing | |
| Stage P | D-HP | D-MOTO on D-POCO, at each PLUG cue | D-POCO, D-MOTO |
| L1b on D-POCO | D-HP, D-POCO | nothing | |

| Id | Hand step | When | Why there is no verb |
|---|---|---|---|
| CAB | Move the cables and the OTG adapter as the table says | each part's start | hardware |
| PLUG / UNPLUG | Plug or unplug D-MOTO's cable at the host's OTG adapter | each cue | hardware |
| DLG | Answer the USB dialogs by the run's rule below | during each cycle | a SystemUI dialog: no verb reaches it, and the round grades what a person's answer does |
| CLEAR | On the App info screen the script opens, press Clear defaults (API 35: Open by default, then Clear default preferences) | each CLEAR cue | Settings is another app, and no shell command clears USB defaults without `MANAGE_USB` |
| COUNT | Tell the host, per plug of the run just ended, how many USB dialogs you saw and the first word of each | each COUNT cue | the second column of the dialog count |
| SETTINGS | On D-POCO, in Open Headunit, press Settings. Leave the first Settings page open. Change nothing | B6-off and B6-on, once each | `SettingsActivity` is not exported, and no shell route opens it on D-POCO |
| SCREEN | On D-POCO, press Settings, scroll by hand to "Detect plugged-in phones/AA dongles". At the next cue, press "Auto-start settings" and scroll by hand to the bottom. Change nothing | control dump and B9 dump, once each | as SETTINGS; the rows can sit below the first screen, and the rig bans scripted scrolling |
| PCMOTO | Plug D-MOTO into its PC cable, then unplug it when cued | only if D-MOTO's wireless adb does not come back | the phone capture needs a link |
| WAKE | Press D-POCO's power button once | only if adb cannot wake the screen (B10) | the screen-off test needs the screen back |
| REBOOT | Restart the unit from its power menu | only if the host refuses `adb reboot` | the host's permission check |
| RESEAT | Reseat the OTG adapter in D-HP's port | only after two `NO_PLUG` in a row on D-HP | hardware |

**Open the cue terminal first.** Prepare step 1 prints the cue file's full path. Give the operator that line exactly. On
this rig's default data folder it is:

```bash
tail -n0 -F /home/oscar/Coding/StudioProjects/rig-data/rounds/usb-dialog-round4/cues.log
```

Read the operator these tables once, at the start, and leave them on screen. Two words tell the dialogs apart.
Android's own dialog starts with "Open" ("Open Open Headunit ..."). The app's own starts with "Allow" ("Allow ... to
access the USB device?").

| Run | Rule for every USB dialog in the run |
|---|---|
| B1 | "Open ...": tick the box, then press OK. "Allow ...": press OK, tick nothing. |
| seed | "Open ...": tick the box, then press OK, at once. "Allow ...": press Cancel. |
| B2 | "Open ...": tick the box, then press OK, at once. "Allow ..." (not expected): press OK, tick nothing. |
| B3, B4 (one cycle) | "Open ...": tick the box, then press OK. "Allow ..." (not expected): press OK, tick nothing. |
| B5, B7, B6-off, B6-on, B8 | Touch nothing. Remember any dialog you see. |
| B9 | "Allow ...": press OK, tick nothing. "Open ..." (not expected): press OK, tick nothing. |
| B10, B10n, L1b | Touch nothing. |

Always: act only on a cue or by the rule. Do not touch D-MOTO. During B6, do not close the Settings screen: the script
does it.

## 6. Helpers

Save every block below in `$OUT/scripts/` with `extract_brief_blocks.py`, and run every script from that directory.
Run every adb call against one unit in sequence (template rule 8). All parsing runs on the host. Grep every capture with
`-a`.

**`lib.sh`**
```bash
. $RIG_TOOLKIT/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round4; mkdir -p $OUT; export LC_ALL=C
HP=CNU350BGBJ; POCO=4f4027e9; MOTO=ZY22GC3BM4; CAND=3dcbf76a
BASEKEYS="int:log-level=2 int:onboarding-version=2 bool:allow-external-configuration=true int:wifi-connection-mode=0
 set:connection-modes=usb bool:auto-connect-last-session=false bool:auto-connect-single-usb=false set:allow-devices=
 bool:use-libusb=false bool:kill-on-disconnect=false bool:enable-floating-button=false bool:auto-start-on-boot=false
 bool:auto-start-on-screen-on=false bool:auto-start-on-wifi=false"
SEEDRULE="Open: tick the box, then press OK, at once. Allow: press Cancel."
# Set in Prepare (wireless.env): HP_W, POCO_W, MOTO_W (ip:5555). Set per part by its script: ST (H|P), HU, HUNAME.
case "${ST:-}" in H) BSTART=60; BSTOP=30;; *) BSTART=50; BSTOP=25;; esac
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
home() { A shell input keyevent 3; sleep 1; }
LOGARGS="-b main -b system -b events -v time"
cap() { A logcat -G 16M >/dev/null 2>&1; A logcat -b main -b system -b events -c
  stdbuf -oL adb -s "$HU" logcat $LOGARGS >> $(F) & echo $! > $OUT/$ST-hu.pid; }
alive() { kill -0 "$(cat $OUT/$ST-hu.pid)" 2>/dev/null && return 0
  adb connect "$HU" >/dev/null 2>&1; echo "$(date +%T) $ST capture restarted" >> $OUT/marks.txt
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
# top: the resumed activity line (API 35 prints topResumedActivity, API 17 mResumedActivity)
top() { A shell dumpsys activity activities | tr -d '\r' | grep -aE -m1 'topResumedActivity|mResumedActivity'; }
# comp: UsbAttachedActivity as dumpsys package lists it (enabled, disabled, or empty = manifest default)
comp() { A shell dumpsys package $PKG 2>/dev/null | tr -d '\r' | awk '/disabledComponents:/{s="disabled"}
  /enabledComponents:/{s="enabled"} s && /app\.UsbAttachedActivity/{print s; exit}'; }
# usbdef TAG: save dumpsys usb, append its usbparse.sh row to defaults.tsv, set DEFN (stored defaults of ours)
usbdef() { A shell dumpsys usb > $OUT/$1-usb.txt 2>&1; ./usbparse.sh $OUT/$1-usb.txt $1 >> $OUT/defaults.tsv
  DEFN=$(tail -1 $OUT/defaults.tsv | grep -ao 'defaults=[0-9]*' | cut -d= -f2); }
# moto_dump TAG: D-MOTO's buffer after the unplug. WiFi first (cap 180 s), then its PC cable (cap 120 s).
moto_dump() { local t=0; while [ $t -lt 180 ]; do adb connect $MOTO_W >/dev/null 2>&1
    adb -s $MOTO_W shell true 2>/dev/null && { adb -s $MOTO_W logcat -d -v time > $OUT/$1-phone.txt
      echo "$1 phone ok wifi" >> $OUT/marks.txt; return 0; }
    sleep 3; t=$((t+3)); done
  cue "PCMOTO $1: plug D-MOTO into its PC cable (not the OTG adapter)"
  if timeout 120 adb -s $MOTO wait-for-device; then adb -s $MOTO logcat -d -v time > $OUT/$1-phone.txt
    adb -s $MOTO tcpip 5555 >/dev/null 2>&1; sleep 4; adb connect $MOTO_W >/dev/null 2>&1
    echo "$1 phone ok cable" >> $OUT/marks.txt; cue "PCMOTO $1 done: unplug D-MOTO from its PC cable"; return 0; fi
  echo "$1 phone UNREACHABLE" >> $OUT/marks.txt; return 1; }
# writeprefs KEY...: int:k=v bool:k=v set:k=a,b del:k. Wraps set_prefs_runas_host.py (force-stops the app), reads every key back.
writeprefs() { local args=() a t k v n
  for a in "$@"; do t=${a%%:*}; k=${a#*:}
    case $t in int) args+=("scalar:int:${k%%=*}:${k#*=}");; bool) args+=("scalar:boolean:${k%%=*}:${k#*=}");;
      string) args+=("scalar:string:${k%%=*}:${k#*=}");; set) args+=("set:${k%%=*}:${k#*=}");; del) args+=("del:$k");; esac; done
  PKG=$PKG python3 -I $RIG_TOOLKIT/set_prefs_runas_host.py "$HU" "${args[@]}" >> $OUT/writeprefs.log 2>&1 || return 1
  A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/$ST-settings-now.xml
  for a in "$@"; do t=${a%%:*}; k=${a#*:}; case $t in del) k=$k;; *) k=${k%%=*};; esac
    n=$(grep -ac "name=\"$k\"" $OUT/$ST-settings-now.xml); case $t in del) [ "$n" -eq 0 ] || { echo "$ST READBACK_FAIL $a" >> $OUT/marks.txt; return 1; };; *) [ "$n" -ge 1 ] || { echo "$ST READBACK_FAIL $a" >> $OUT/marks.txt; return 1; };; esac; done; }
# keyval KEY: the value of one key in the last settings read ($ST-settings-now.xml), or none
keyval() { sed -n "s/.*name=\"$1\" value=\"\([^\"]*\)\".*/\1/p" $OUT/$ST-settings-now.xml | head -1 | grep . || echo none; }
# keyread TAG: read settings.xml without writing, and record the four USB keys
keyread() { A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/$ST-settings-now.xml
  echo "$1 keys listen=$(keyval listen-for-usb-devices) connect=$(keyval usb-connect-on-plug-in) auto-start-on-usb=$(keyval auto-start-on-usb) reopen=$(keyval reopen-on-reconnection)" | tee -a $OUT/marks.txt; }
# mirror TAG: D-POCO only, the device storage mirror's auto-start keys
mirror() { [ "$ST" = P ] || return 0
  A shell run-as $PKG cat /data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml | tr -d '\r' > $OUT/$1-mirror.xml
  echo "$1 mirror $(grep -aoE 'name="(auto-start-on-(usb|boot|screen-on|wifi)|usb-connect-on-plug-in|listen-for-usb-devices)" value="[a-z]*"' $OUT/$1-mirror.xml | tr '\n' ' ')" >> $OUT/marks.txt; }
# ident TAG SHA: 1 when ACTION_QUERY_STATE names SHA, and the live APK's md5
ident() { local p; p=$(A shell pm path $PKG | tr -d '\r' | sed -n 's/^package://p' | head -1); A pull "$p" $OUT/live-$1.apk >/dev/null 2>&1
  echo "$1 commit_$2=$(send ACTION_QUERY_STATE | tr -d '\r' | grep -acF $2) md5_live=$(md5sum < $OUT/live-$1.apk | cut -c1-32)" | tee -a $OUT/marks.txt; }
```

**`usbparse.sh`** (unchanged from rounds 2 and 3)
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
# timeline.sh CAPTURE P [FROM TO]: the decisive lines between the markers FROM and TO (default P-cue and P-unplug),
# one per row with the whole line, then one SUMMARY row. A = the phone's normal identity, B = its accessory identity.
# CREATE=1 counts the events buffer's *_create_activity lines as dialog starts (only if the host prints no START u0).
set -u
f=$1; P=$2; FROM=${3:-$P-cue}; TO=${4:-$P-unplug}
tr -d '\r' < "$f" | awk '!seen[$0]++' \
 | sed -n "/AutomationMarker: $FROM\$/,/AutomationMarker: $TO\$/p" \
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
/Switching USB device to accessory mode |Found known USB device with permission: |Successfully requested switch to accessory mode for / { k = "SW" }
/Fallback: force=true and found single normal-mode Android device / { k = "FALLBACK" }
/UsbLauncher: not switching .* on a plug-in: / { k = "NOSW"; if ($0 ~ /on a plug-in: Connect when plugged in is off/) nconn++; if ($0 ~ /on a plug-in: Detect plugged-in phones\/AA dongles is off/) ndet++ }
/UsbAttachedActivity: Connect when plugged in is off; opening the app, not switching / { k = "OPENHOME" }
/UsbLauncher: USB auto-connect held while the settings screen is open/ { k = "HELD" }
/UsbAttachedActivity: settings on show or the status pill.s X holding; handing / { k = "HANDOFF" }
/checking USB now \(trigger=/ { k = "REPLAY" }
/HomeFragment: Requesting single-USB auto-connect via AapService/ { k = "HOMEAUTO" }
/SSL handshake complete/ { k = "SSL" }
/Media Sink Setup Request: / { k = "MS" }
/Reopen on reconnection: launching MainActivity/ { k = "RAISE" }
/Reopen on reconnection: not raised over Android.s USB dialog/ { k = "HOLDRAISE" }
/USB auto-start: launching app for / { k = "AUTOSTART" }
/USB Intent: / && /USB_DEVICE_DETACHED/ { k = "DETACH" }
/WakeDetect: SCREEN_OFF/ { k = "WD_OFF" }
/WakeDetect: SCREEN_ON \(/ { k = "WD_ON" }
/WakeDetect: hibernate wake detected/ { k = "WD_HIB" }
/WakeDetect: (checking USB devices|possible wake, checking USB)/ { k = "WD_USB" }
/WakeDetect: (wake already handled|already connected\/connecting, skipping)/ { k = "WD_SKIP" }
/FATAL EXCEPTION|ANR in com\.andrerinas|NoClassDefFoundError/ { k = "FATAL" }
/Switching USB device to accessory mode |Switching to accessory mode\.|Successfully requested switch to accessory mode for / { swl++ }
k != "" { n++; T[n] = ms(t); TS[n] = t; K[n] = k; L[n] = substr($0, 20) }
END {
  decA = decB = rsl = mtpA = srcA = srcB = lsn = rtrig = "-"; ssl = ms_n = fatal = stacked = reqA = reqB = mains = 0
  raiseA = raiseB = holdA = holdB = auto = refuse = rx = attA = attB = fb = 0; tms = -1
  nosw = openh = held = handoff = replay = homeauto = sw = 0
  wdoff = wdon = wdhib = wdusb = wdskip = 0; wdons = "none"
  for (i = 1; i <= n; i++) {
    id = "-"
    if (K[i] == "DEC") {
      id = (L[i] ~ /identity=accessory/) ? "B" : "A"
      if (id == "A") { decA = add(decA, field(L[i], "action")); srcA = add(srcA, field(L[i], "source")); mtpA = add(mtpA, field(L[i], "mtp")) }
      else { decB = add(decB, field(L[i], "action")); srcB = add(srcB, field(L[i], "source")) }
      rsl = add(rsl, field(L[i], "routeSeen")); lsn = add(lsn, field(L[i], "listen"))
    } else if (K[i] == "REQ") {
      id = (L[i] ~ /VID: 18D1/) ? "B" : "A"; if (id == "A") reqA++; else reqB++
    } else if (K[i] ~ /^(CONFIRM|PERM|RESOLVER)_START$/) {
      best = -1e18; id = "x"
      for (j = 1; j <= n; j++) if (K[j] ~ /^ATTACH_/ && T[j] <= T[i] + 500 && T[j] > best) { best = T[j]; id = substr(K[j], 8) }
      ty = substr(K[i], 1, index(K[i], "_") - 1); q[ty, ++qe[ty]] = i; FIN[i] = -1; C[ty "_" id]++
    } else if (K[i] ~ /_FINISH$/) {
      ty = substr(K[i], 1, index(K[i], "_") - 1)
      if (qs[ty] < qe[ty]) { s = q[ty, ++qs[ty]]; FIN[s] = T[i]; id = ID[s] }
    } else if (K[i] == "ATTACH_A") attA++
    else if (K[i] == "ATTACH_B") attB++
    else if (K[i] == "SSL") ssl++
    else if (K[i] == "MS") { ms_n++; if (tms < 0) tms = T[i] }
    else if (K[i] == "MAIN_START") mains++
    else if (K[i] == "RAISE") { id = src(L[i]); if (id == "A") raiseA++; else raiseB++ }
    else if (K[i] == "HOLDRAISE") { id = src(L[i]); if (id == "A") holdA++; else holdB++ }
    else if (K[i] == "AUTOSTART") auto++
    else if (K[i] == "REFUSE") refuse++
    else if (K[i] == "RX_CHECK") rx++
    else if (K[i] == "FALLBACK") fb++
    else if (K[i] == "SW") sw++
    else if (K[i] == "NOSW") nosw++
    else if (K[i] == "OPENHOME") openh++
    else if (K[i] == "HELD") held++
    else if (K[i] == "HANDOFF") handoff++
    else if (K[i] == "REPLAY") { replay++; if (match(L[i], /trigger=[A-Z_]+/)) rtrig = add(rtrig, substr(L[i], RSTART + 8, RLENGTH - 8)) }
    else if (K[i] == "HOMEAUTO") homeauto++
    else if (K[i] == "WD_OFF") wdoff++
    else if (K[i] == "WD_ON") { wdon++; if (match(L[i], /off for -?[0-9]+s/)) wdons = substr(L[i], RSTART + 8, RLENGTH - 9) }
    else if (K[i] == "WD_HIB") wdhib++
    else if (K[i] == "WD_USB") wdusb++
    else if (K[i] == "WD_SKIP") wdskip++
    else if (K[i] == "FATAL") fatal++
    ID[i] = id
    printf "%s\t%s\t%s\t%s\n", TS[i], K[i], id, L[i]
  }
  open_ms = unfinished = covered = dlg = 0
  for (i = 1; i <= n; i++) {
    if (K[i] == "PERM_START")
      for (j = 1; j <= n; j++) if (K[j] == "CONFIRM_START" && ID[j] == ID[i] && (T[i] - T[j] <= 2000 && T[j] - T[i] <= 2000)) { stacked++; break }
    if (K[i] ~ /^(CONFIRM|PERM|RESOLVER)_START$/) {
      dlg++
      if (FIN[i] < 0) unfinished++
      if (FIN[i] < 0 || (tms >= 0 && FIN[i] > tms)) open_ms++
    }
    if (K[i] ~ /^(CONFIRM|RESOLVER)_START$/) {
      e = (FIN[i] < 0) ? 1e18 : FIN[i]
      for (j = 1; j <= n; j++) if (K[j] == "MAIN_START" && T[j] > T[i] && T[j] < e) covered++
    }
  }
  printf "SUMMARY\t%s\tdlg_all=%d\tconfirm_A=%d\tconfirm_B=%d\tconfirm_x=%d\tperm_A=%d\tperm_B=%d\tperm_x=%d\tresolver=%d\tstacked=%d\treq_A=%d\treq_B=%d\tdec_A=%s\tdec_B=%s\tsrc_A=%s\tsrc_B=%s\tmtp_A=%s\trs=%s\tlsn=%s\tssl=%d\tms=%d\topen_at_ms=%d\tunfinished=%d\tmain_start=%d\tcovered=%d\traise_A=%d\traise_B=%d\thold_A=%d\thold_B=%d\tautostart=%d\trefuse=%d\trx_check=%d\tattach_A=%d\tattach_B=%d\tfallback=%d\tsw=%d\tsw_lines=%d\tnosw=%d\tnosw_conn=%d\tnosw_det=%d\topenhome=%d\theld=%d\thandoff=%d\treplay=%d\treplay_trig=%s\thomeauto=%d\twd_off=%d\twd_on=%d\twd_on_s=%s\twd_hib=%d\twd_usb=%d\twd_skip=%d\tfatal=%d\n", \
    P, dlg, C["CONFIRM_A"], C["CONFIRM_B"], C["CONFIRM_x"], C["PERM_A"], C["PERM_B"], C["PERM_x"], C["RESOLVER_A"] + C["RESOLVER_B"] + C["RESOLVER_x"], stacked, reqA, reqB, decA, decB, srcA, srcB, mtpA, rsl, lsn, ssl, ms_n, open_ms, unfinished, mains, covered, raiseA, raiseB, holdA, holdB, auto, refuse, rx, attA, attB, fb, sw, swl + 0, nosw, nconn + 0, ndet + 0, openh, held, handoff, replay, rtrig, homeauto, wdoff, wdon, wdons, wdhib, wdusb, wdskip, fatal
}'
```

What the SUMMARY fields mean:

| Field | Meaning |
|---|---|
| `dlg_all` | every USB dialog start in the window, of any kind and identity |
| `confirm_A`, `confirm_B`, `perm_A`, `perm_B`, `resolver` | `START u0` lines for `UsbConfirmActivity` (Android's dialog), `UsbPermissionActivity` (ours) and `UsbResolverActivity`. A start belongs to the latest app attach line at or before its time plus 500 ms; `_x` is a start with no attach line in the window |
| `stacked` | starts of ours within 2000 ms of a start of Android's for the same identity |
| `req_A`, `req_B` | `Requesting USB permission for ` lines; `VID: 18D1` is B |
| `dec_A`, `dec_B`, `src_A`, `src_B`, `mtp_A`, `rs`, `lsn` | the `action=`, `source=`, `mtp=`, `routeSeen=` and `listen=` values of the decision lines, in order; `-` is none |
| `open_at_ms`, `unfinished` | dialog starts not finished before the first `Media Sink Setup Request: `; dialog starts with no finish in the window |
| `main_start`, `covered` | `MainActivity` starts in the window; those that came while an Android dialog was open |
| `raise_A`, `raise_B`, `hold_A`, `hold_B`, `autostart` | the "Reopen on reconnection" raise and hold lines by identity, and `USB auto-start: launching app for ` lines |
| `refuse`, `rx_check` | refused second asks; `AutomationReceiver: ` lines that name `ACTION_CHECK_USB` |
| `attach_A`, `attach_B` | `Normal USB device attached: ` and `USB accessory device attached, connecting.`: the service saw identity A, or the phone came back as B |
| `fallback` | `Fallback: force=true and found single normal-mode Android device ` lines |
| `sw`, `sw_lines` | switch lines that are not the fallback line; every line that names a switch, the fallback line included. **`sw_lines=0` means no switch was started** |
| `nosw`, `nosw_conn`, `nosw_det` | `UsbLauncher: not switching ... on a plug-in: ` lines: the service refused a plug-in; of them, those whose reason is `Connect when plugged in is off`, and those whose reason is `Detect plugged-in phones/AA dongles is off` |
| `openhome` | `UsbAttachedActivity: Connect when plugged in is off; opening the app, not switching ` lines |
| `held`, `handoff` | the service held a check for the settings screen; the attach screen handed a held plug-in to the service |
| `replay`, `replay_trig` | settings-close replays, and the `trigger=` of each, in order |
| `homeauto` | `HomeFragment: Requesting single-USB auto-connect via AapService` lines (B8) |
| `wd_*` | the `WakeDetect` lines, as in the old round 4 brief: `wd_usb` is the three USB checks, `wd_hib` their reachability, `wd_skip` a wake that stopped first |
| `fatal` | crash lines |

**`ui.py`**
```python
# ui.py DUMP TAG: one row from a uiautomator dump of a settings screen. For each label: the number of nodes with exactly
# that text, then the settingSwitch in the same row as checked:enabled ("-" no node, "?" no switch). Also whether the
# needs-detect line shows in the "Connect when plugged in" row, and whether detection sits after the libusb row. No verdict.
import sys
import xml.etree.ElementTree as ET
f, tag = sys.argv[1], sys.argv[2]
LABELS = ["Detect plugged-in phones/AA dongles", "Use Native USB Driver", "Start on screen on", "Connect when plugged in",
          "Open the app when plugged in", "Reopen on reconnection", "Listen for USB Devices", "Auto-start on USB"]
NEED = 'Turn on "Detect plugged-in phones/AA dongles" in General settings first.'
try:
    root = ET.parse(f).getroot()
except Exception as e:
    print(f"{tag}\tPARSE_FAIL\t{e}")
    sys.exit(0)
parent = {c: p for p in root.iter() for c in p}
allnodes = list(root.iter("node"))
def row_of(n):
    return parent.get(parent.get(n))
out = [tag]
for lab in LABELS:
    nodes = [n for n in allnodes if n.get("text") == lab]
    state = "-"
    if nodes:
        row = row_of(nodes[0])
        sw = [d for d in (row.iter("node") if row is not None else [])
              if (d.get("resource-id") or "").endswith(":id/settingSwitch")]
        state = f'{sw[0].get("checked")}:{sw[0].get("enabled")}' if sw else "?"
    out.append(f"{lab}={len(nodes)}:{state}")
need_any = sum(1 for n in allnodes if n.get("text") == NEED)
need_row = "-"
conn = [n for n in allnodes if n.get("text") == "Connect when plugged in"]
if conn:
    row = row_of(conn[0])
    need_row = "yes" if row is not None and any(d.get("text") == NEED for d in row.iter("node")) else "no"
order = "-"
idx = {lab: next((i for i, n in enumerate(allnodes) if n.get("text") == lab), None)
       for lab in ("Detect plugged-in phones/AA dongles", "Use Native USB Driver")}
if None not in idx.values():
    order = "yes" if idx["Detect plugged-in phones/AA dongles"] > idx["Use Native USB Driver"] else "no"
out += [f"need_any={need_any}", f"need_in_row1={need_row}", f"detect_after_libusb={order}"]
print("\t".join(out))
```

**`keys.sh`**
```bash
# setkeys TAG KEY...: write the keys with the app stopped, launch, and wait up to 20 s for
# UsbAttachedActivity to follow listen-for-usb-devices. A force-stop: it also clears the glitch window.
setkeys() { local tag=$1; shift; local want=enabled
  case " $* " in *" bool:listen-for-usb-devices=false "*) want=disabled;; esac
  A shell am force-stop $PKG
  writeprefs "$@" || { echo "$ST $tag WRITE_FAILED" >> $OUT/marks.txt; return 1; }
  launch; local t=0; while [ $t -lt 20 ]; do [ "$(comp)" = $want ] && break; sleep 2; t=$((t+2)); done
  echo "$ST $tag comp=$(comp) want=$want pid=$(hupid)" >> $OUT/marks.txt; keyread $ST-$tag; sleep 2; mirror $ST-$tag
  [ "$(comp)" = $want ]; }
# cleardef TAG: open App info and cue CLEAR when a default of ours is stored; wait for the parsed count to reach 0 (cap 120 s)
cleardef() { usbdef $1-pre; [ "$DEFN" -eq 0 ] && { echo "$1 nothing to clear" >> $OUT/marks.txt; return 0; }
  A shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:$PKG >/dev/null
  cue "CLEAR $1: on $HUNAME, App info for Open Headunit is open. Press Clear defaults (API 35: Open by default, then Clear default preferences). Say so if there is no such button"
  local t=0 d; while [ $t -lt 120 ]; do sleep 3; t=$((t+3)); A shell dumpsys usb > $OUT/tmp-usb.txt 2>&1
    d=$(./usbparse.sh $OUT/tmp-usb.txt x | grep -ao 'defaults=[0-9]*' | cut -d= -f2); [ "$d" -eq 0 ] && break; done
  usbdef $1-post; home; launch; }
# dump X: one uiautomator dump into X
dump() { A shell uiautomator dump /sdcard/ohu-ui.xml >/dev/null 2>&1; A pull /sdcard/ohu-ui.xml $1 >/dev/null 2>&1; }
# waitdump X TEXT: dump every 5 s until X holds a node with exactly TEXT (cap 180 s); then one more dump 2 s later
waitdump() { local t=0; while [ $t -lt 180 ]; do dump $1; grep -aqF "text=\"$2\"" $1 2>/dev/null && break; sleep 5; t=$((t+5)); done
  [ $t -ge 180 ] && return 1; sleep 2; dump $1; return 0; }
# uidump TAG (D-POCO): cue SCREEN, dump General, then Auto-start settings. Append the ui.py rows to ui.tsv.
# Then leave the settings screen: it holds every USB check while it is visible.
uidump() { local x
  launch; cue "SCREEN $1: on D-POCO press Settings. Scroll by hand until \"Detect plugged-in phones/AA dongles\" is on screen. Change nothing"
  x=$OUT/$1-gen-ui.xml; waitdump $x 'Detect plugged-in phones/AA dongles' || echo "$1 GENERAL_NOT_REACHED" >> $OUT/marks.txt
  A shell screencap -p /sdcard/ohu-ui.png; A pull /sdcard/ohu-ui.png $OUT/$1-gen-ui.png >/dev/null 2>&1
  python3 -I ./ui.py $x $1-gen | tee -a $OUT/ui.tsv
  cue "SCREEN $1: now press \"Auto-start settings\" (scroll by hand if needed), then scroll by hand to the bottom. Change nothing"
  x=$OUT/$1-as-ui.xml; waitdump $x 'Start on screen on' || echo "$1 AUTOSTART_NOT_REACHED" >> $OUT/marks.txt
  sleep 8; dump $x                                  # 8 s: the operator's scroll to the bottom
  A shell screencap -p /sdcard/ohu-ui.png; A pull /sdcard/ohu-ui.png $OUT/$1-as-ui.png >/dev/null 2>&1
  python3 -I ./ui.py $x $1-as | tee -a $OUT/ui.tsv
  cue "SCREEN $1 done: hands off D-POCO"; home; launch; home
  echo "$1 after-screen $(top)" >> $OUT/marks.txt; }
# setopen P (B6): cue SETTINGS and wait until SettingsActivity is resumed (cap 120 s)
setopen() { local t=0; launch
  cue "SETTINGS $1: on D-POCO press Settings in Open Headunit. Leave the first Settings page open. Change nothing"
  while [ $t -lt 120 ]; do top | grep -aqF 'SettingsActivity' && break; sleep 3; t=$((t+3)); done
  echo "$1 before-plug $(top)" >> $OUT/marks.txt; }
```

**`cycle.sh`**
```bash
# cycle P KIND: one plug of D-MOTO into the host, answered by the run's rule (section 5), then the unplug.
# KIND: session (B1, B2, B8, seed) | open (B3 then B4) | quiet (B5, and B7 with WITHB7=1) | setoff, seton (B6) | double (B9)
# Returns 0 graded, 1 retry, 2 low battery. Rows go to cycles.tsv: the whole cycle, and for open, quiet, setoff and
# seton also its two halves (P/pre and P/post), split at the P-press or P-close marker, and P/b7 after P-gap.
sess() { { waitfor $1 'SSL handshake complete' 150 && waitfor $1 'Media Sink Setup Request: ' 30; } \
    || echo "$P NO_SESSION" >> $OUT/marks.txt   # graded, never retried: the session is what these runs measure
  sleep 10; }                                    # a late dialog would START in these 10 s
# press P: count D-MOTO's accessory lines over WiFi (it is not switched yet), then the USB button as a verb
press() { if adb -s $MOTO_W shell true 2>/dev/null; then
    PRE=$(adb -s $MOTO_W logcat -d 2>/dev/null | grep -acF 'entering USB accessory mode'); else PRE='?'; fi
  mark $P-press; send ACTION_CHECK_USB >> $OUT/marks.txt; sess $P-press; }
cycle() { P=$1; local K=$2 rc=0 pid0 cut=""; PRE=-
  [ "$(bat)" -ge $BSTOP ] || { echo "$P BATTERY_LOW $(bat)" >> $OUT/marks.txt; return 2; }
  alive; pid0=$(hupid)
  adb connect $MOTO_W >/dev/null 2>&1; timeout 5 adb -s $MOTO_W logcat -c 2>/dev/null
  mark $P-cue; cue "PLUG $P: plug D-MOTO's cable into the OTG adapter on $HUNAME now (take it off the PC cable first if it is there)"
  if ! waitfor $P-cue 'Normal USB device attached: ' 120; then echo "$P NO_PLUG" >> $OUT/marks.txt; rc=1
  else
    echo "$P plug=$(firstts $P-cue 'Normal USB device attached: ') host=$(date +%s)" >> $OUT/marks.txt
    case $K in
      session) sess $P-cue ;;
      open) waitfor $P-cue 'UsbAttachedActivity: Connect when plugged in is off; opening the app' 120 || echo "$P NO_OPEN" >> $OUT/marks.txt
            sleep 20; press; cut=press ;;       # 20 s: a late switch would start in these 20 s
      quiet) waitfor $P-cue 'UsbAttachedActivity: Connect when plugged in is off; opening the app' 30 || echo "$P NO_OPEN" >> $OUT/marks.txt
            sleep 30; press; cut=press          # 30 s: B5 measures that nothing switches in 30 s
            [ "${WITHB7:-0}" = 1 ] && b7tail ;;
      setoff|seton) sleep 10; mark $P-close; home; cut=close   # 10 s: the hand-off, the refusal or the hold lands in these 10 s
            if [ $K = seton ]; then waitfor $P-close 'checking USB now (trigger=' 20 || echo "$P NO_REPLAY" >> $OUT/marks.txt; sess $P-close
            else sleep 15; fi ;;                 # 15 s: a replay would land 1.5 s after the close
      double) sleep 8; A shell "am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_CHECK_USB; am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_CHECK_USB"
            sess $P-cue ;;                       # 8 s: past the 2 s fallback
    esac
  fi
  mark $P-end; cue "UNPLUG $P: unplug D-MOTO from $HUNAME"
  if waitfor $P-end 'USB_DEVICE_DETACHED' 60; then echo "$P unplug=$(firstts $P-end 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt
  else cue "UNPLUG $P again: D-MOTO is still plugged into $HUNAME. Unplug it now"
    if waitfor $P-end 'USB_DEVICE_DETACHED' 120; then echo "$P unplug=$(firstts $P-end 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt
    else echo "$P NO_UNPLUG_LINE" >> $OUT/marks.txt; fi
  fi
  LASTUNPLUG=$(date +%s); echo "$P unplug_host=$LASTUNPLUG" >> $OUT/marks.txt
  mark $P-unplug; moto_dump $P; usbdef $P-after
  ./timeline.sh $(F) $P > $OUT/$P.timeline.tsv; row $P $OUT/$P.timeline.tsv
  local post_end=$P-unplug; [ "${B7DONE:-0}" = 1 ] && post_end=$P-gap
  if [ -n "$cut" ]; then
    ./timeline.sh $(F) $P/pre $P-cue $P-$cut > $OUT/$P-pre.timeline.tsv; row $P/pre $OUT/$P-pre.timeline.tsv
    ./timeline.sh $(F) $P/post $P-$cut $post_end > $OUT/$P-post.timeline.tsv; row $P/post $OUT/$P-post.timeline.tsv
  fi
  if [ "${B7DONE:-0}" = 1 ]; then
    ./timeline.sh $(F) $P/b7 $P-gap $P-unplug > $OUT/$P-b7.timeline.tsv; row $P/b7 $OUT/$P-b7.timeline.tsv; B7DONE=0; fi
  return $rc; }
# b7tail (B7, inside B5's cycle): unplug after B5's session, then a replug inside 10 s. The glitch window is 30 s, so
# nothing (no phone dump, no COUNT) may sit between the unplug and the replug.
b7tail() { mark $P-gap; cue "UNPLUG $P (B7): unplug D-MOTO from $HUNAME, then wait for the next cue"
  waitfor $P-gap 'USB_DEVICE_DETACHED' 60 && echo "$P b7_unplug=$(firstts $P-gap 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt
  cue "PLUG $P (B7): plug D-MOTO back into the OTG adapter on $HUNAME NOW, within 10 s"
  if waitfor $P-gap 'Normal USB device attached: ' 40; then
    echo "$P b7_plug=$(since $P-gap | grep -aF 'Normal USB device attached: ' | tail -1 | cut -c1-18)" >> $OUT/marks.txt; sess $P-gap
  else echo "$P B7 NO_PLUG" >> $OUT/marks.txt; fi
  B7DONE=1; }
# row LABEL TIMELINE: one cycles.tsv row. phone_acc is the whole cycle; phone_pre is D-MOTO's count just before the press.
row() { printf '%s\tpid=%s>%s\tphone_acc=%s\tphone_pre=%s\tafter=%s\t%s\n' "$1" "$pid0" "$(hupid)" \
    "$(grep -acF 'entering USB accessory mode' $OUT/$P-phone.txt 2>/dev/null)" "$PRE" \
    "$(tail -1 $OUT/defaults.tsv | cut -f2-3 | tr '\t' ' ')" "$(tail -1 $2 | cut -f3-)" >> $OUT/cycles.tsv; }
# glitchwait: hold until 45 s after the last unplug. The 30 s window would let a plug switch with the key off.
glitchwait() { local w; w=$(( 45 - ($(date +%s) - ${LASTUNPLUG:-0}) )); [ $w -gt 0 ] && sleep $w; return 0; }
# screencycle P: the screen off for 75 s, then on. The WakeDetect hibernate path needs more than 60 s off.
screencycle() { A shell input keyevent 223; waitfor $1-cue 'WakeDetect: SCREEN_OFF' 10 || echo "$1 NO_SCREEN_OFF" >> $OUT/marks.txt
  sleep 75                                      # the off time is what the hibernate path measures
  local t=0; until A shell input keyevent 224 2>/dev/null; do adb connect $HU >/dev/null 2>&1; sleep 3; t=$((t+3))
    [ $t -ge 60 ] && { cue "WAKE $1: press D-POCO's power button once"; break; }; done
  waitfor $1-cue 'WakeDetect: SCREEN_ON (' 60 || echo "$1 NO_SCREEN_ON" >> $OUT/marks.txt
  A shell wm dismiss-keyguard; sleep 20; }      # 20 s: the USB check, if any, runs in these 20 s
# wakecheck P: B10 and B10n, a screen cycle with no plug, bounded by the same markers as a cycle
wakecheck() { P=$1; alive; mark $P-cue; screencycle $P; mark $P-unplug
  ./timeline.sh $(F) $P > $OUT/$P.timeline.tsv
  printf '%s\twake-only\t%s\n' "$P" "$(tail -1 $OUT/$P.timeline.tsv | cut -f3-)" >> $OUT/cycles.tsv; }
# valid RUN P: 0 when the cycle can be graded. B2 on routeSeen=no is void: our dialog would stack on Android's.
valid() { case $1 in B2) tail -1 $OUT/$2.timeline.tsv | grep -q 'rs=[a-z,]*no' \
    && { echo "$2 VOID routeSeen=no" >> $OUT/marks.txt; return 1; };; esac; return 0; }
pre_B1() { home; }
pre_B2() { [ "${GOOD:-0}" -eq 0 ] && cleardef $P-clear; home; }   # only the first graded plug starts with no defaults
pre_B3() { cleardef $P-clear; home; }
pre_B5() { glitchwait; home; }
pre_B6off() { glitchwait; setopen $P; }
pre_B6on() { setopen $P; }
pre_B9() { home; }
pre_B8() { home; }
# runplugs RUN "KIND ...": one graded cycle per KIND, at most 2 retries in the run (the stop rule). Returns 2 on low battery.
runplugs() { local run=$1 kinds=($2) bad=0 k=0 rc; GOOD=0
  while [ $GOOD -lt ${#kinds[@]} ] && [ $bad -lt 2 ]; do k=$((k+1)); while used $ST-$run-p$k; do k=$((k+1)); done
    P=$ST-$run-p$k
    type pre_$run >/dev/null 2>&1 && pre_$run
    cycle $P ${kinds[$GOOD]}; rc=$?; [ $rc -eq 2 ] && return 2
    [ $rc -eq 0 ] && { valid $run $P || rc=1; }
    if [ $rc -eq 0 ]; then GOOD=$((GOOD+1)); else bad=$((bad+1)); echo "$P RETRY" >> $OUT/marks.txt
      grep -q "^$P VOID routeSeen" $OUT/marks.txt && seed; fi
  done
  echo "$ST-$run graded=$GOOD retries=$bad" >> $OUT/marks.txt; return 0; }
# seed: plug until Android starts our activity for an attach once (at most 3 plugs). Labels never repeat.
seed() { local n=0 old="$RULE" L; RULE="$SEEDRULE"
  while [ $n -lt 3 ]; do n=$((n+1)); SEEDN=$(( ${SEEDN:-0} + 1 )); while used $ST-seed$SEEDN; do SEEDN=$((SEEDN+1)); done
    L=$ST-seed$SEEDN; cycle $L session; [ $? -eq 2 ] && { RULE="$old"; return 2; }
    if [ "$(since $L-cue | grep -aF 'UsbAttachedActivity' | grep -aF 'USB Intent: ' | grep -acF 'USB_DEVICE_ATTACHED')" -ge 1 ]; then
      echo "$ST seed OK at $L pid=$(hupid) recorded=$(cnt $L-cue 'Android starts this app for a USB attach in this boot (recorded)')" >> $OUT/marks.txt
      RULE="$old"; return 0; fi
  done; echo "$ST SEED_FAILED" >> $OUT/marks.txt; RULE="$old"; return 1; }
```

**`boot.sh`** (unchanged from the old round 4 brief)
```bash
#!/usr/bin/env bash
# boot.sh CAPTURE TAG: one row of counts from one post-reboot capture. No verdict.
f=$1; tag=$2
u() { tr -d '\r' < "$f" | awk '!seen[$0]++'; }
c() { u | grep -acF -- "$1"; }
after() { u | sed -n '/Boot auto-start: USB auto-start enabled, starting AapService to check USB (trigger=/,$p' | grep -acF -- "$1"; }
dlg() { u | grep -aF 'START u0' | grep -acE '\.usb\.Usb(Confirm|Permission|Resolver)Activity'; }
mains() { u | grep -aF 'START u0' | grep -acF 'openheadunit.main.MainActivity'; }
fatal() { u | grep -aA3 'FATAL EXCEPTION' | grep -acF 'com.andrerinas'; }
printf '%s\treceived=%s\tusb_branch=%s\tboot_branch=%s\tscreen_branch=%s\twifi_branch=%s\tlocked=%s\trefused=%s\tcreating=%s\tcreating_after_usb=%s\tfgs_exc=%s\treq=%s\tdlg=%s\tmain_start=%s\tanr=%s\tfatal=%s\tt_received=%s\tt_usb=%s\n' "$tag" \
  "$(c 'Boot auto-start: received action=')" \
  "$(c 'Boot auto-start: USB auto-start enabled, starting AapService to check USB (trigger=')" \
  "$(c 'Boot auto-start: starting AapService with BOOT_START')" \
  "$(c 'Boot auto-start: screen-on auto-start enabled')" \
  "$(c 'Boot auto-start: WiFi auto-start enabled')" \
  "$(c 'BootCompleteReceiver: Device is locked.')" \
  "$(c 'Boot auto-start: Android refused to start AapService')" \
  "$(c 'AapService creating...')" "$(after 'AapService creating...')" \
  "$(c 'ForegroundServiceStartNotAllowedException/Exception caught in onCreate: ')" \
  "$(c 'Requesting USB permission for ')" "$(dlg)" "$(mains)" "$(c 'ANR in com.andrerinas')" "$(fatal)" \
  "$(u | grep -aF -m1 'Boot auto-start: received action=' | cut -c1-18)" \
  "$(u | grep -aF -m1 'Boot auto-start: USB auto-start enabled' | cut -c1-18)"
```

**`mirrorfix.sh`** (runs on the device under `run-as`, with the app stopped; only if the mirror reads screen-on `true`)
```bash
#!/system/bin/sh
F=/data/user_de/0/com.andrerinas.headunitrevived/shared_prefs/settings_device_protected.xml
sed -i 's|name="auto-start-on-screen-on" value="true"|name="auto-start-on-screen-on" value="false"|' "$F"
```

## 7. The deciding lines

All app strings print at INFO, except the one marked ERROR. Each was checked with `grep -F -r` in `app/src/main` at
`3dcbf76a`. "Composed" marks a line whose parts the source joins at run time; each part was checked. Each cycle line is
graded in `<ST>-hu.txt` through `timeline.sh`: the whole cycle from `<P>-cue` to `<P>-unplug`, and for split runs the
halves at `<P>-press` or `<P>-close`. The L1b lines are graded in `<ST>-boot-hu.txt`, the whole post-reboot capture,
through `boot.sh`.

| Id | Line (fixed substring) | Read |
|---|---|---|
| L-RX | `AutomationReceiver: ` | a verb landed; with `ACTION_CHECK_USB` on the line it is `RX_CHECK`. A step with no line is void, not a FAIL |
| L-MK | `AutomationMarker: ` | the window bounds |
| G-NOSW | `UsbLauncher: not switching ` ... ` on a plug-in: ` then `Connect when plugged in is off` or `Detect plugged-in phones/AA dongles is off` | **the service refused a plug-in** (composed; the device name and the reason are joined at run time). It prints for each connectable normal-mode device. The reason names the setting that refused |
| G-OPEN | `UsbAttachedActivity: Connect when plugged in is off; opening the app, not switching ` | **the attach screen opened the app and left the phone alone** |
| G-SW | `Switching USB device to accessory mode `, `Found known USB device with permission: `, `Successfully requested switch to accessory mode for ` | a switch started: the first from the attach screen, the others from the service. Counted with the fallback line in `sw_lines` |
| L-FB | `Fallback: force=true and found single normal-mode Android device ` | the fallback's `force` branch picked D-MOTO and switches (its line ends `Switching to accessory mode.`) |
| G-HELD | `UsbLauncher: USB auto-connect held while the settings screen is open` | the service held a check for the settings screen |
| G-HAND | `UsbAttachedActivity: settings on show or the status pill's X holding; handing ` | the attach screen handed a held plug-in to the service |
| G-REPLAY | `checking USB now (trigger=` | the settings-close replay (full text `AapService: the settings screen closed with a USB auto-connect held behind it, checking USB now (trigger=PLUG_IN).`, composed) |
| G-HOME | `HomeFragment: Requesting single-USB auto-connect via AapService` | B8: the home screen's single-USB auto-connect fired |
| L-RAISE | `Reopen on reconnection: launching MainActivity (` | the service raised `MainActivity` for that attach. Source (composed): `USB accessory attach` is B, `USB normal attach (` is A |
| L-HOLD | `Reopen on reconnection: not raised over Android's USB dialog (` | the raise was skipped over Android's dialog |
| L-AUTO | `USB auto-start: launching app for ` | "Open the app when plugged in" opened the app |
| L-DEC | `UsbLauncher: permission for ` | one decision. Fields (composed): `identity=` (`normal`, `accessory`), `mtp=`, `listen=` (`on`, `off`), `routeSeen=`, `attach=`, `source=` (`automatic`, `user`), `action=` (`ask`, `wait`, `asked`, `hold`) |
| L-REQ | `Requesting USB permission for ` | every `requestPermission`. The name carries `VID: <hex> PID: <hex>` (composed) |
| L-REF | `UsbLauncher: a dialog for ` ... ` is already open; not opening a second`, `UsbPermissionAsks: a dialog for ` ... `ms ago; not opening a second` | a refused second ask |
| L-ROUTE | `Android starts this app for a USB attach in this boot (recorded)` | the seed check, API 24 and up |
| L-NORM | `Normal USB device attached: ` | the plug time, identity A, and proof the service is alive |
| L-ACC | `USB accessory device attached, connecting.` | identity B: the phone switched |
| L-INT | `USB Intent: ` | with `USB_DEVICE_ATTACHED` and `UsbAttachedActivity` on one line: Android started our activity (the seed check). With `USB_DEVICE_DETACHED`: the unplug time |
| L-FALL | `UsbAttachedActivity didn't handle ` | the 2 s fallback ran for A |
| L-SSL | `SSL handshake complete` | a session formed (once per session at INFO) |
| L-MS | `Media Sink Setup Request: ` | the phone answered our service discovery |
| W-* | `WakeDetect: SCREEN_OFF`, `WakeDetect: SCREEN_ON (screen was off for `, `WakeDetect: hibernate wake detected (off for `, `WakeDetect: checking USB devices (trigger=`, `WakeDetect: possible wake, checking USB (trigger=`, `WakeDetect: checking USB devices on screen on`, `WakeDetect: wake already handled `, `WakeDetect: already connected/connecting, skipping (` | B10 and B10n, as in the old round 4 brief |
| B-RX | `Boot auto-start: received action=` | the boot broadcast reached the app |
| B-USB | `Boot auto-start: USB auto-start enabled, starting AapService to check USB (trigger=` | **the L1b line.** The USB-only branch |
| B-BOOT, B-SCR, B-WIFI | `Boot auto-start: starting AapService with BOOT_START`, `Boot auto-start: screen-on auto-start enabled`, `Boot auto-start: WiFi auto-start enabled` | other branches. The first two must be 0 |
| B-LOCK, B-REF | `BootCompleteReceiver: Device is locked.`, `Boot auto-start: Android refused to start AapService` | information |
| S-CREATE | `AapService creating...` | the service started |
| S-FGS | `ForegroundServiceStartNotAllowedException/Exception caught in onCreate: ` | ERROR. API 35 boot rule (section 3). Information only |
| D-MATCH | `MATCH! Starting AapService` | discard rule (template §4) |

Two more app lines are information only, and no run expects them: `UsbAttachedActivity: Connect when plugged in is off
and the device is locked; leaving ` and `Usb in accessory mode and has permission. Starting AapService.`

Screen texts, from `app/src/main/res/values/strings.xml`, graded in `<TAG>-gen-ui.xml` and `<TAG>-as-ui.xml` through
`ui.py`: `Detect plugged-in phones/AA dongles`, `Use Native USB Driver`, `Start on screen on`, `Connect when plugged in`,
`Open the app when plugged in`, `Reopen on reconnection`, and the rendered needs-detect line
`Turn on "Detect plugged-in phones/AA dongles" in General settings first.` (source:
`Turn on \"%1$s\" in General settings first.`). The old labels `Listen for USB Devices` and `Auto-start on USB` are no
longer in the English strings, so they must read 0.

System strings, not in `app/src`: `START u0`, `.usb.UsbConfirmActivity`, `.usb.UsbPermissionActivity`,
`.usb.UsbResolverActivity`, `_finish_activity`, `_create_activity`, `openheadunit.main.MainActivity`,
`topResumedActivity`, `mResumedActivity`, `SettingsActivity`, `Device preferences`, `device_preferences`, `stopped=`.
Phone string, from AOSP on D-MOTO: `entering USB accessory mode`. **FAIL lines, every cycle:** `FATAL EXCEPTION` in a
block that names `com.andrerinas`, `ANR in com.andrerinas`, and `NoClassDefFoundError`.

The files the runs write:

| File | One row per | Fields |
|---|---|---|
| `cycles.tsv` | plug (and `P/pre`, `P/post` for split runs), or a B10 screen cycle | label, `pid=<before>><after>`, `phone_acc=`, `phone_pre=`, `after=defaults=N pairs=...`, then the SUMMARY fields |
| `<P>.timeline.tsv`, `<P>-pre.timeline.tsv`, `<P>-post.timeline.tsv` | decisive line | time, kind, identity, the whole line; last row SUMMARY |
| `ui.tsv` | screen dump | tag, then `<label>=<nodes>:<checked>:<enabled>`, `need_any=`, `need_in_row1=`, `detect_after_libusb=` |
| `boot.tsv` | L1b capture | the `boot.sh` counts |
| `defaults.tsv` | `dumpsys usb` read | tag, `defaults=`, `pairs=`, `sections=`, `serial_outside=` |
| `marks.txt` | event | markers with host time, `plug=`, `unplug=`, `unplug_host=`, retries, voids, seeds, `keys`, `mirror`, `form=`, identity lines |
| `<P>-phone.txt` | plug | D-MOTO's buffer from before the plug to after the unplug |

## 8. Runs

Each stage is one capture with all its runs chained. Cycle labels are `<ST>-<run>-p<k>`, seeds `<ST>-seed<n>`, and no
label runs twice. L1b is a separate capture on each unit, because a reboot ends any capture.

**Stop rule.** A cycle with `NO_PLUG`, or a void, is retried once with the next `k`. Two such cycles in one run end the
run, graded on what it has (INCONCLUSIVE with 0 graded cycles). A cycle with `NO_SESSION`, `NO_OPEN` or `NO_REPLAY` is
graded, never retried. `BATTERY_LOW` ends the stage, and the runs not reached are UNTESTABLE (battery). A failed
`setkeys` makes the runs under it UNTESTABLE (settings). The optional block runs only if stage P has run less than
45 min when it reaches it.

### Prepare (steps 1 to 4 beside the build, then 10 min)

1. Print the cue path: `. ./lib.sh; echo "tail -n0 -F $OUT/cues.log"`. Give the operator that line exactly (section 5).
2. `$RIG_TOOLKIT/rig_preflight.sh D_HP:wifi D_POCO:wifi D_MOTO:wifi`. Paste the table into Setup notes. Charge D-HP to
   100%.
3. D-MOTO, on its PC cable: `svc bluetooth disable` and read it back (`dumpsys bluetooth_manager`); WiFi stays on.
   Read and record `settings get global stay_on_while_plugged_in`, then `svc power stayon true`. Record Gearhead's
   `versionName` and `getprop sys.usb.config`. `logcat -G 16M`. Restore the stay-on value at the end of the round.
4. Wireless adb for D-HP, D-POCO and D-MOTO, each while still on its PC cable: `adb -s <serial> tcpip 5555`, read
   `ip -4 addr show wlan0`, `adb connect <ip>:5555`, and write `HP_W`, `POCO_W` and `MOTO_W` to
   `$OUT/scripts/wireless.env`.
5. Round 3's APK: `md5sum $RIG_DATA/rounds/usb-dialog-round3/apk-C.apk` (expected `8f3889d4b75a4606074d8d4d7282cdef`),
   and the two `grep -acF` lines of section 1 on it (both MUST read 0). Record all three.
6. D-POCO: `am start -a android.intent.action.VIEW -d headunit://exit`, wait 3 s, then `am force-stop` its app (it is a
   phone in other rounds; template §8 role-swap rule). Read its battery: 50% or more, else charge it first.
7. On D-HP and D-POCO, still on the PC cable, save `dumpsys usb > $OUT/prep-<unit>-usb.txt`, record
   `getprop ro.build.version.sdk` and `date` beside the host's `date`, and D-MOTO's offset the same way. On D-POCO, from
   `run-as $PKG cat shared_prefs/settings.xml`, record `usb-attach-route-boot`, `usb-blacklist` and
   `enable-car-launcher`, and `settings get global boot_count`. On D-HP record `usb-blacklist` and
   `enable-car-launcher`.
8. After the build, B1's upgrade on each host: `ST=H ./upgrade.sh`, then `ST=P ./upgrade.sh`. `marks.txt` MUST then read
   `commit_3dcbf76a=1` (or more) and an `md5_live` equal to `apk-C.apk` for each host, else stop: UNTESTABLE (build).
9. D-POCO's lasting grant, with D-MOTO unplugged:
   `ST=P HU=$POCO HUNAME=D-POCO; . ./lib.sh; . ./wireless.env; . ./keys.sh; launch; cleardef P-prep; usbdef P-prep-grant`.
   Record `serial_outside=` on the last row of `defaults.tsv`.
10. `chmod +x usbparse.sh timeline.sh boot.sh upgrade.sh stage4.sh l1b.sh` in `$OUT/scripts/`.

**`upgrade.sh`**
```bash
#!/usr/bin/env bash
# ST=H ./upgrade.sh (D-HP) or ST=P ./upgrade.sh (D-POCO), on the PC cable: B1's set-up. Round 3's APK writes the
# settings and runs once, then the candidate installs over it. form=upgrade, or key-absent when round 3's APK is missing.
. ./lib.sh; . ./wireless.env
case "$ST" in H) HU=$HP; HUNAME=D-HP;; P) HU=$POCO; HUNAME=D-POCO;; *) echo "run as ST=H or ST=P"; exit 1;; esac
export ST HU HUNAME
OLD=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round3/apk-C.apk
ident $ST-before $CAND; A shell am force-stop $PKG
A shell run-as $PKG cat shared_prefs/settings.xml > $OUT/backup-$ST.xml
FORM=key-absent
if [ "$(md5sum < $OLD 2>/dev/null | cut -c1-32)" = 8f3889d4b75a4606074d8d4d7282cdef ]; then
  A install -r -d $OLD >> $OUT/install.log 2>&1 && FORM=upgrade; fi
writeprefs $BASEKEYS bool:listen-for-usb-devices=true bool:auto-start-on-usb=false bool:reopen-on-reconnection=true \
  del:usb-connect-on-plug-in del:video-profile-starvation-cap || { echo "$ST B1 UNTESTABLE settings" >> $OUT/marks.txt; exit 1; }
if [ $FORM = upgrade ]; then launch; sleep 5; ident $ST-old 9f90e121; A shell am force-stop $PKG; fi
A install -r -d $OUT/apk-C.apk >> $OUT/install.log 2>&1 || { echo "$ST INSTALL_FAILED" >> $OUT/marks.txt; exit 1; }
launch; sleep 5; ident $ST-new $CAND
echo "$ST B1 form=$FORM" | tee -a $OUT/marks.txt
keyread $ST-upgrade; mirror $ST-upgrade; A shell am force-stop $PKG
```

### Stage H, then stage P

Run `ST=H ./stage4.sh`, then `ST=H ./l1b.sh`, then `ST=P ./stage4.sh`, then `ST=P ./l1b.sh`.

**`stage4.sh`**
```bash
#!/usr/bin/env bash
. ./lib.sh; . ./wireless.env; . ./keys.sh; . ./cycle.sh
case "$ST" in H) HU=$HP_W; HUNAME=D-HP;; P) HU=$POCO_W; HUNAME=D-POCO;; *) echo "run as ST=H or ST=P"; exit 1;; esac
export ST HU HUNAME; RULE=; SEEDOK=0; T0=$(date +%s)
cue "CAB $ST: put the OTG adapter in $HUNAME's port and take $HUNAME and D-MOTO off their PC cables. D-MOTO stays unplugged"
adb connect $HU >/dev/null; adb connect $MOTO_W >/dev/null
[ "$(bat)" -ge $BSTART ] || { echo "$ST BATTERY_START $(bat)" >> $OUT/marks.txt; exit 1; }
th_gate || exit 1
echo "$ST stay_on_before=$(A shell settings get global stay_on_while_plugged_in | tr -d '\r')" >> $OUT/marks.txt
A shell svc power stayon true; A shell am force-stop $PKG
S0=$ST-start-$(date +%H%M%S); cap; mark $S0; launch; sleep 3
if [ "$(since $S0 | grep -aF 'START u0' | grep -acF 'MainActivity')" -lt 1 ]; then
  echo "$ST NO_START_LINES: every timeline.sh call runs with CREATE=1" >> $OUT/marks.txt; export CREATE=1; fi
usbdef $ST-start
run() { RULE="$RULES"; cue "NEXT $ST-$1: read the rule, nothing starts for 12 s"; sleep 12
  mark $ST-$1-start; runplugs "$@"; local rc=$?; mark $ST-$1-end; RULE=
  cue "COUNT $ST-$1: tell the host, per plug, how many USB dialogs you saw and the first word of each (25 s)"; sleep 25; return $rc; }

# B1: the file the upgrade left, untouched (no setkeys)
keyread $ST-B1-before
if [ "$(keyval usb-connect-on-plug-in)" = none ] && [ "$(keyval auto-start-on-usb)" = false ]; then
  RULES="Open: tick the box, then OK. Allow: OK, tick nothing."; run B1 "session session session" || exit 2
  keyread $ST-B1-after
else echo "$ST B1 UNTESTABLE settings" >> $OUT/marks.txt; fi

# B2 (stage P only): the key and auto-start both absent, defaults cleared, then two replugs
if [ "$ST" = P ]; then
  if setkeys b2 bool:listen-for-usb-devices=true del:usb-connect-on-plug-in del:auto-start-on-usb bool:reopen-on-reconnection=true; then
    RB=$(keyval usb-attach-route-boot); BC=$(A shell settings get global boot_count | tr -d '\r')
    echo "P route-boot=$RB boot_count=$BC" >> $OUT/marks.txt
    if [ "$RB" != "$BC" ]; then RULE="$SEEDRULE"; cue "NEXT P-seed: $SEEDRULE"; sleep 12; RULE=
      seed; [ $? -eq 2 ] && exit 2; fi
    RULES="Open: tick the box, then OK, at once. Allow (not expected): OK, tick nothing."; run B2 "session session session" || exit 2
    keyread P-B2-after
  else echo "P B2 UNTESTABLE settings" >> $OUT/marks.txt; fi
fi

# B3 then B4 (one cycle), B5, B7: Connect when plugged in off, detection on
if setkeys b3 bool:listen-for-usb-devices=true bool:usb-connect-on-plug-in=false bool:auto-start-on-usb=false bool:reopen-on-reconnection=true; then
  RULES="Open: tick the box, then OK. Allow (not expected): OK, tick nothing."; run B3 "open" || exit 2
  WITHB7=0; [ "$ST" = P ] && [ $(( $(date +%s) - T0 )) -lt 2700 ] && WITHB7=1    # B7 is optional, inside B5's cycle
  echo "$ST WITHB7=$WITHB7" >> $OUT/marks.txt
  RULES="Touch nothing. Remember any dialog you see. On a B7 cue, unplug, then plug back in at once."; run B5 "quiet" || exit 2
  WITHB7=0
  if [ "$ST" = P ]; then RULES="Touch nothing. Leave Settings open."; run B6off "setoff" || exit 2; fi
else echo "$ST B3 B4 B5 B7 B6-off UNTESTABLE settings" >> $OUT/marks.txt; fi

if [ "$ST" = P ]; then
  # B6-on, the control: the key on, the settings screen open
  if setkeys b6on bool:listen-for-usb-devices=true bool:usb-connect-on-plug-in=true bool:auto-start-on-usb=false; then
    RULES="Touch nothing. Leave Settings open."; run B6on "seton" || exit 2
  else echo "P B6-on UNTESTABLE settings" >> $OUT/marks.txt; fi
  # the control dump: every USB row shown
  if setkeys ctl bool:listen-for-usb-devices=true bool:usb-connect-on-plug-in=true bool:auto-start-on-usb=true bool:reopen-on-reconnection=true; then
    uidump P-ctl
  else echo "P control dump UNTESTABLE settings" >> $OUT/marks.txt; fi
fi

# B9: detection off, the stored values on
if setkeys b9 bool:listen-for-usb-devices=false bool:usb-connect-on-plug-in=true bool:auto-start-on-usb=true bool:reopen-on-reconnection=true; then
  [ "$ST" = P ] && uidump P-b9
  RULES="Allow: OK, tick nothing. Open (not expected): OK, tick nothing."; run B9 "double" || exit 2
else echo "$ST B9 UNTESTABLE settings" >> $OUT/marks.txt; fi

# the optional block, stage P only, if stage P has run less than 45 min
if [ "$ST" = P ] && [ $(( $(date +%s) - T0 )) -lt 2700 ]; then
  RULE="Touch nothing."
  if setkeys ctl bool:listen-for-usb-devices=true bool:usb-connect-on-plug-in=true bool:auto-start-on-usb=true bool:reopen-on-reconnection=true; then
    cue "NEXT P-B10: D-MOTO stays unplugged. The screen goes off for 75 s"; sleep 12; home; wakecheck P-B10-p1; fi
  if setkeys b10n bool:listen-for-usb-devices=true bool:usb-connect-on-plug-in=false bool:auto-start-on-usb=true bool:reopen-on-reconnection=true; then
    cue "NEXT P-B10n: D-MOTO stays unplugged. The screen goes off for 75 s"; sleep 12; home; wakecheck P-B10n-p1; fi
  if setkeys b8 bool:listen-for-usb-devices=true bool:usb-connect-on-plug-in=false bool:auto-start-on-usb=false \
       bool:auto-connect-single-usb=true bool:has-accepted-disclaimer=true; then
    RULES="Touch nothing. Remember any dialog you see."; run B8 "session" || exit 2; fi
  RULE=
else [ "$ST" = P ] && echo "P optional block skipped (time)" >> $OUT/marks.txt; fi

cleardef $ST-end-clear; mark $ST-end
send ACTION_EXPORT_LOG; sleep 5                     # the export path is on the data= line; pull that file
th_end; uncap
for r in B1 B2 B3 B5 B6off B6on B9 B8; do
  tr -d '\r' < $(F) | sed -n "/AutomationMarker: $ST-$r-start\$/,/AutomationMarker: $ST-$r-end\$/p" > $OUT/$ST-$r.slice; done
# do not restore settings yet: l1b.sh uses this backup and restores it
```

Grade each run from `cycles.tsv`, the run's timeline rows and `ui.tsv`. Quote every `action=`, `source=`, `listen=` and
`replay_trig=` value. The operator's COUNT goes beside `dlg_all` as a second column. A disagreement is a finding, not a
verdict. A `serial_outside` above 0 on D-POCO makes that host's dialog items for D-MOTO INCONCLUSIVE.

#### B1, an upgrade with stored `auto-start-on-usb=false`: every plug still connects (both hosts)

Setup check: `marks.txt` reads `form=` (record it), `<ST>-B1-before keys ... connect=none auto-start-on-usb=false`.
Three plugs, app in the background. Rule: "Open": tick, OK. "Allow": OK.

B1 PASS needs, on every plug: `attach_A` at least 1, `sw_lines` at least 1, `attach_B` at least 1, `ssl` at least 1,
`ms` at least 1, `nosw=0`, `openhome=0`, `fatal=0`, and phone `phone_acc` at least 1 where the dump came back. Also
`<ST>-B1-after keys` still reads `connect=none` (nothing wrote the new key). Record `dlg_all`, `dec_A` and `dec_B`.

B1 FAIL if any plug shows `nosw` or `openhome` at least 1, or `sw_lines=0`, or if `connect=` reads anything but `none`.
**If the change did nothing,** B1 would look the same: B1 is a regression guard. The numbers that would show a wrong
default are `nosw` and `openhome`, which read 1 or more per plug if an absent key read as off.

#### B2, the key and auto-start absent, defaults cleared (stage P)

Setup check: `P-b2 keys` reads `connect=none auto-start-on-usb=none listen=true`. Plug 1 follows a `cleardef`; plugs 2
and 3 do not. Rule: "Open": tick, then OK, at once.

B2 PASS needs:

1. Plug 1: `confirm_A=1` and `confirm_B=1` (Android's two dialogs), `perm_A=0`, `perm_B=0`, `resolver=0`, `stacked=0`,
   `req_A=0`, `req_B=0`. Every `dec_A` and `dec_B` value is `wait` or `asked`. `after=` lists A (`8888/...`) and B
   (`6353/11521`).
2. Plugs 2 and 3: `dlg_all=0`, `req_A=0`, `req_B=0`.
3. Every plug: `sw_lines` at least 1, `ssl` at least 1, `ms` at least 1, `nosw=0`, `openhome=0`, `fatal=0`. Phone:
   `phone_acc` at least 1.
4. `P-B2-after keys` still reads `connect=none`. The operator's COUNT reads 2 "Open", then 0, then 0.

B2 FAIL if item 1 or 2 fails on a plug that is not void, or if item 3 fails on `nosw`, `openhome` or `sw_lines`. A
plug on `routeSeen=no` is void; the script seeds and retries.

#### B3 then B4, the key off, no defaults: a plug opens the app; the button connects (both hosts). The point of the round

Setup check: `<ST>-b3 keys ... connect=false listen=true auto-start-on-usb=false`, `comp=enabled`. On D-POCO the
mirror line reads `usb-connect-on-plug-in" value="false"`. One cycle after a `cleardef`, app in the background. The
script waits for the activity line (cap 120 s), then 20 s, then sends `send ACTION_CHECK_USB` (marker `<P>-press`).
Rule: "Open": tick, then OK, for both of Android's dialogs.

B3 PASS needs, on the `<P>/pre` row (plug to press):

1. `attach_A` at least 1 and `confirm_A=1`: Android's dialog for A, once.
2. `openhome` at least 1 and `main_start` at least 1.
3. `sw_lines=0`, `attach_B=0`, `ssl=0`. Phone: `phone_pre=0`.
4. `perm_A=0`, `req_A=0`, `resolver=0`. `raise_A=0`, `autostart=0`.
5. `nosw_conn` at least 1 and `nosw_det=0` (the 2 s fallback refused, naming connect). Information if 0 while items 1
   to 4 pass; quote the rows.
6. `fatal=0`.

B4 PASS needs, on the `<P>/post` row (press to unplug):

1. `rx_check` at least 1.
2. `confirm_A=0`, `perm_A=0`, `req_A=0`: A keeps the permission Android gave at "Open".
3. `sw_lines` at least 1 and `attach_B` at least 1.
4. `confirm_B=1`, `perm_B=0`, `req_B=0`, and `dec_B` holds `wait`.
5. `ssl` at least 1, `ms` at least 1. Phone (whole cycle): `phone_acc` at least 1.
6. `fatal=0`. The operator's COUNT for the cycle reads 2, both "Open". `after=` lists A and B.

B3 FAIL if item 2 or 3 fails. B4 FAIL if item 2, 3 or 5 fails while `rx_check` is at least 1. With `rx_check=0` the verb
never landed and B4 is void. `NO_OPEN` with `confirm_A=0`: the dialog never showed, and B3 is INCONCLUSIVE.

**If the change did nothing,** the `<P>/pre` row would show `openhome=0`, `sw_lines` at least 1, `attach_B` at least 1
and a session before the press, and `phone_pre` at least 1. Those four numbers separate the two states.

#### B5, the key off, both defaults stored: a plug opens the app and nothing switches for 30 s (both hosts)

After B3, with no new `setkeys` and no `cleardef`. The script holds until 45 s after B3's unplug (`glitchwait`). One
cycle, app in the background. The script waits for the activity line (cap 30 s), then 30 s, then sends
`send ACTION_CHECK_USB` (marker `<P>-press`). Rule: touch nothing.

B5 PASS needs:

1. `<P>/pre`: `dlg_all=0`, `openhome` at least 1, `main_start` at least 1, `sw_lines=0`, `attach_B=0`, `ssl=0`, and
   `phone_pre=0`. The gap from `plug=` to the `<P>-press` host time in `marks.txt` is 30 s or more.
2. `<P>/post`: `rx_check` at least 1, `dlg_all=0`, `req_A=0`, `req_B=0`, `sw_lines` at least 1, `attach_B` at least 1,
   `ssl` at least 1, `ms` at least 1.
3. Whole cycle: `phone_acc` at least 1, `fatal=0`. The operator's COUNT reads 0.
4. Information: `nosw_conn` on `<P>/pre` (1 or more expected), and `after=` (A and B both stored).

B5 FAIL if item 1 fails on `sw_lines`, `attach_B`, `ssl` or `phone_pre`, or if item 2 fails while `rx_check` is at least
1. A dialog in `<P>/pre` (`dlg_all` above 0) means a default was not stored: B5 is INCONCLUSIVE, quote `after=` of B3.

**If the change did nothing,** Android's default would start our activity, which would switch at once: `<P>/pre` would
read `openhome=0`, `sw_lines` at least 1 and `ssl` at least 1.

#### B7 (optional, stage P), a replug inside the glitch window reconnects with the key off

Inside B5's cycle, when `marks.txt` reads `P WITHB7=1`. After B5's session the script cues an unplug (marker
`<P>-gap`), then a replug "within 10 s", with no phone dump and no COUNT between them. Same keys. Rule: touch nothing.
B5's `<P>/post` row then ends at `<P>-gap`, and B7 is the `<P>/b7` row.

B7 PASS needs: `b7_plug=` within 25 s of `b7_unplug=` in `marks.txt` (else INCONCLUSIVE), and on `<P>/b7`:
`openhome=0`, `nosw=0`, `sw_lines` at least 1, `attach_B` at least 1, `ssl` at least 1, `dlg_all=0`, `fatal=0`. B7
FAIL if the replug came inside 25 s and `openhome` or `nosw` is at least 1 with `ssl=0`. **If the window did nothing,**
`<P>/b7` would read as B5's `<P>/pre`: `openhome` at least 1 and no switch.

#### B6-off, the key off, the settings screen open: the close replays nothing that switches (stage P)

After B5 (or B7) with the same keys. The script holds until 45 s after the last unplug, then cues SETTINGS and waits for
`SettingsActivity` in `top`. One plug; 10 s after it the script presses Home (marker `<P>-close`), then waits 15 s.
Rule: touch nothing.

Setup check: the `before-plug` line in `marks.txt` names `SettingsActivity`, else B6-off is INCONCLUSIVE.

B6-off PASS needs:

1. `<P>/pre`: `handoff` at least 1 or `nosw` at least 1 (the plug reached the gate), and `openhome=0`.
2. Whole cycle: `sw_lines=0`, `attach_B=0`, `ssl=0`, `dlg_all=0`, `phone_acc=0`.
3. Every value in `replay_trig` is `PLUG_IN` (`-` when there was no replay, which the code expects).
4. `fatal=0`. Record `held`, `handoff`, `nosw` and `replay`.

B6-off FAIL if item 2 fails, or if `replay_trig` holds any value but `PLUG_IN`.

#### B6-on, the control: the key on, the settings screen open, the close replays as a plug-in and connects (stage P)

After `setkeys b6on`. The same steps as B6-off, but after the close the script waits for the replay line (cap 20 s),
then for a session. Rule: touch nothing.

B6-on PASS needs: `<P>/pre` `held` at least 1 and `sw_lines=0`; `<P>/post` `replay` at least 1 with every
`replay_trig` value `PLUG_IN`, then `sw_lines` at least 1, `attach_B` at least 1, `ssl` at least 1, `ms` at least 1;
whole cycle `dlg_all=0`, `phone_acc` at least 1 and `fatal=0`.

B6-on FAIL if `replay_trig` holds a value other than `PLUG_IN`, or if `held` is at least 1 and no session follows the
replay. If `held=0` and `replay=0`, the settings screen did not hold the plug: B6-on is INCONCLUSIVE, and B6-off item 3
has no control. Quote both `before-plug` lines.

#### Control dump and B9 dump: the rows and their texts (stage P)

The control dump runs after `setkeys ctl` (detection on, all three stored values on). The B9 dump runs after
`setkeys b9`: the same stored values, detection off. Each is one SCREEN step and two dumps: General (`-gen`) and
Auto-start (`-as`).

Control PASS needs, on `P-ctl-gen`: `Detect plugged-in phones/AA dongles=1:true:true`. On `P-ctl-as`:
`Connect when plugged in=1:true:true`, `Open the app when plugged in=1:true:true`, `Reopen on reconnection=1:true:true`,
`need_any=0`, `Listen for USB Devices=0`, `Auto-start on USB=0`, `Start on screen on=1`. Record `detect_after_libusb`.

B9 dump PASS needs, on `P-b9-gen`: `Detect plugged-in phones/AA dongles=1:false:true`, `Listen for USB Devices=0`. On
`P-b9-as`: `Connect when plugged in=1:true:false` (shown, stored on, disabled), `need_in_row1=yes`,
`Open the app when plugged in=0`, `Reopen on reconnection=0`, `Auto-start on USB=0`, `Start on screen on=1`.

The dumps FAIL if `P-b9-as` shows row 2 or row 3, shows row 1 enabled, or lacks the needs-detect line, or if either
General dump lacks the detection row. Reachability: if `P-ctl-as` does not show rows 2 and 3, the dump did not reach below
row 1, and the B9 "hidden" items are INCONCLUSIVE; the host checks `P-b9-as-ui.png`. `GENERAL_NOT_REACHED` or
`AUTOSTART_NOT_REACHED` makes that dump INCONCLUSIVE.

#### B9, detection off: no dialog at the plug, our asks only on the press, one per path (both hosts)

After `setkeys b9` (`comp=disabled` in `marks.txt`, else B9 is UNTESTABLE). One cycle, app in the background. 8 s after
the plug, two `ACTION_CHECK_USB` broadcasts in one adb shell call. Rule: "Allow": OK, tick nothing.

B9 PASS needs:

1. `nosw_det` at least 1 and `nosw_conn=0`: the plug's fallback was refused, and the line names detection.
2. `rx_check=2`. `dec_A` starts with `ask`, and holds no `hold`.
3. `req_A=1` and `perm_A=1`: one request and one dialog for A.
4. `refuse` at least 1, with one `REFUSE` row of either L-REF form.
5. `req_B=1`, `perm_B=1`, and `ask` with `automatic` in `dec_B` and `src_B`.
6. `confirm_A=0`, `confirm_B=0`, `resolver=0`, `unfinished=0`. Every `lsn` value is `off`.
7. `ssl` at least 1, `ms` at least 1. Phone: `phone_acc` at least 1.
8. `fatal=0`. The operator's COUNT reads 2, both "Allow".

Expected `dec_A`: `ask,ask` (the second is the refused one). B9 FAIL if item 1, 3, 4 or 6 fails while `rx_check=2`. A
refusal that names connect (`nosw_conn` above 0) fails item 1.
**If the change did nothing,** round 3's R5c reading would repeat: `dec_A` would start with `hold` and `nosw=0`.

#### B10 and B10n (optional, stage P), the wake check follows the key

D-MOTO unplugged, no session. Each is a screen cycle: off for 75 s, on, then 20 s more. Rule: touch nothing. B10 runs
after `setkeys ctl` (key on), B10n after `setkeys b10n` (key off, detection and auto-start on).

PASS needs, on both rows: `wd_hib` at least 1, `wd_skip=0`, `wd_on_s` 61 or more, and `fatal=0`; then B10 `wd_usb` at
least 1, and B10n `wd_usb=0`. B10n FAIL if `wd_hib` is at least 1 and `wd_usb` is at least 1. If B10 reads `wd_usb=0`
with `wd_hib` at least 1, B10n's 0 has no control: both are INCONCLUSIVE. If `wd_hib=0`, that row is INCONCLUSIVE.

#### B8 (optional, stage P), the key off with "Auto-connect USB device" on

After `setkeys b8`. One plug, defaults stored from B3 and B4. Rule: touch nothing. This run records how the two settings
meet. With the key off, the home screen opens, and its own single-USB auto-connect then asks for USB as an app start,
which the gate does not cover.

B8 PASS needs: `openhome` at least 1, `homeauto` at least 1, `sw_lines` at least 1, `ssl` at least 1, `dlg_all=0`,
`phone_acc` at least 1, `fatal=0`. With `homeauto=0`, B8 is INCONCLUSIVE: quote the `Auto-connect` lines in the slice.
B8 FAIL if `homeauto` is at least 1 and `sw_lines=0`.

### L1b, the boot start with detection off (D-HP after stage H, D-POCO after stage P)

Run `ST=H ./l1b.sh` after stage H, and `ST=P ./l1b.sh` after stage P.

**`l1b.sh`**
```bash
#!/usr/bin/env bash
# ST=H ./l1b.sh (D-HP) or ST=P ./l1b.sh (D-POCO): the boot start with detection off and no phone plugged.
. ./lib.sh; . ./wireless.env; . ./keys.sh
case "$ST" in H) HU=$HP; HUNAME=D-HP;; P) HU=$POCO; HUNAME=D-POCO;; *) echo "run as ST=H or ST=P"; exit 1;; esac
export ST HU HUNAME; RULE="Touch nothing."; FB=$OUT/$ST-boot-hu.txt
cue "CAB L1b $ST: take the OTG adapter off $HUNAME and plug $HUNAME into its PC cable. Nothing goes on $HUNAME's port"
t=0; until [ "$(adb -s $HU get-state 2>/dev/null)" = device ]; do sleep 3; t=$((t+3))
  [ $t -ge 180 ] && { echo "$ST L1b NO_CABLE" >> $OUT/marks.txt; exit 1; }; done
th_gate || exit 1
setkeys l1b bool:listen-for-usb-devices=false bool:auto-start-on-usb=true bool:usb-connect-on-plug-in=true \
  bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:auto-start-on-wifi=false || { echo "$ST L1b UNTESTABLE settings" >> $OUT/marks.txt; exit 1; }
if [ $ST = P ] && grep -aqF 'name="auto-start-on-screen-on" value="true"' $OUT/P-l1b-mirror.xml; then
  A shell am force-stop $PKG; A push mirrorfix.sh /data/local/tmp/ >/dev/null; A shell run-as $PKG sh /data/local/tmp/mirrorfix.sh
  echo "P mirror screen-on was true; corrected" >> $OUT/marks.txt; launch; sleep 3; mirror P-l1b-fixed; fi
send ACTION_STOP_SERVICE >> $OUT/marks.txt; sleep 3      # never force-stop here: a stopped package gets no BOOT_COMPLETED
echo "$ST L1b before: $(A shell dumpsys package $PKG | tr -d '\r' | grep -ao -m1 'stopped=[a-z]*') boot_count=$(A shell settings get global boot_count | tr -d '\r')" >> $OUT/marks.txt
th_end
A reboot || { echo "$ST adb reboot refused" >> $OUT/marks.txt; cue "REBOOT: restart $HUNAME from its power menu now"; }
t=0; while [ "$(adb -s $HU get-state 2>/dev/null)" = device ] && [ $t -lt 30 ]; do sleep 1; t=$((t+1)); done
timeout 240 adb -s $HU wait-for-device || { echo "$ST L1b NO_BOOT" >> $OUT/marks.txt; exit 1; }
stdbuf -oL adb -s $HU logcat $LOGARGS > $FB & BPID=$!
t=0; until [ "$(A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 3; t=$((t+3)); [ $t -ge 240 ] && break; done
t=0; until [ "$(grep -acF 'Boot auto-start: received action=' $FB)" -ge 1 ]; do sleep 2; t=$((t+2)); [ $t -ge 120 ] && break; done
sleep 30                                                  # the service start and its USB check land in these 30 s
kill $BPID 2>/dev/null
./boot.sh $FB $ST-L1b | tee -a $OUT/boot.tsv
echo "$ST L1b after: $(A shell dumpsys package $PKG | tr -d '\r' | grep -ao -m1 'stopped=[a-z]*') boot_count=$(A shell settings get global boot_count | tr -d '\r') service=$(A shell dumpsys activity services $PKG | tr -d '\r' | grep -acF 'AapService')" >> $OUT/marks.txt
[ $ST = P ] && A shell wm dismiss-keyguard
# restore backup-$ST.xml with the app stopped (owner and mode as tooling.md says), diff it, then:
# A shell settings put global stay_on_while_plugged_in <stay_on_before from marks.txt>
```

Setup checks: `marks.txt` reads `stopped=false` before the reboot, and the boot count rose by 1 on D-POCO (D-HP, API 17,
prints `null`). On D-POCO the last mirror line reads `auto-start-on-usb` `true`, `usb-connect-on-plug-in` `true`,
`auto-start-on-boot` `false`, and `auto-start-on-screen-on` `false` or absent. Else L1b on D-POCO is UNTESTABLE
(mirror). On D-HP the `H-l1b keys` line reads `connect=true auto-start-on-usb=true listen=false`.

L1b PASS needs, per unit, on its `boot.tsv` row:

1. `received` at least 1.
2. `usb_branch` at least 1, and `boot_branch=0`, `screen_branch=0`.
3. `creating_after_usb` at least 1: `AapService creating...` after the USB-branch line.
4. `dlg=0`, `req=0`, `main_start=0`.
5. `anr=0`, `fatal=0`.

Record `fgs_exc`, `refused`, `locked`, `t_received` and `t_usb`. On D-POCO, `fgs_exc` at least 1 is the API 35 rule
(section 3) and does not fail L1b. L1b FAIL if `received` is at least 1, `boot_branch=0`, `screen_branch=0` and
`usb_branch=0`: then the boot start read Listen, or lost a stored value. L1b FAIL also on any `dlg` or `req` above 0.
With `received=0`, L1b is INCONCLUSIVE: quote `stopped=` before and after.

## 9. Do not re-run

Round 1 settled `main`: two of our dialogs per plug with Listen off, the stacked pair and 3 or 4 dialogs on a first
connect with Listen on, and the tick-everything control. Do not build `main` again. D-SAM has no USB host. The old
round 4 brief (for `08a0aaaf`) was never published and is replaced by this one; its L1 to L2w runs are not needed.

Rounds 2 and 3 settled these. This round repeats them only where B2, B4, B5 and B9 meet them in the new settings
states:

| Run | Round | Verdict |
|---|---|---|
| R4a | 2 (D-HP, D-POCO), 3 (D-POCO) | PASS: `stacked=0`, `req=0`, 2 Android dialogs per first connect (B2 repeats it with the new key absent) |
| R4b | 2 | PASS: B left with Android's dialog |
| R4c | 2 | PASS: 6 replugs, 0 dialogs |
| R5a | 2 | PASS: Listen off and Start on USB off, `action=hold`, 0 dialogs |
| R5b | 2 | PASS: `ACTION_CHECK_USB` asks for A as `source=user` |
| R5t | 2 | PASS, narrower |
| R5c | 3 (D-HP, D-POCO) | PASS: a double press opens one dialog for A (B9 repeats it; the plug's `hold` is now a refusal) |
| F4a, F4b | 3 (D-HP, D-POCO) | PASS: no raise over Android's dialog |
| F4c | 3 (D-HP, D-POCO) | PASS: defaults stored, 0 dialogs, the raise |

## 10. Report back

1. **B3 and B5 per host:** on `<P>/pre`, `openhome`, `sw_lines`, `attach_B`, `ssl`, `phone_pre` and `dlg_all`; on
   `<P>/post`, `rx_check`, `sw_lines`, `ssl` and B4's `dec_B`. Did a plug with the key off leave the phone alone, and did
   the button still connect?
2. **B1 per host, and B2:** `form=`, then per plug `ssl`, `nosw`, `openhome`, `dlg_all`, and the `connect=` readback after
   the run. Does every plug still connect with the key absent?
3. **B9 and the dumps:** B9's `nosw`, `dec_A`, `req_A`, `perm_A`, `refuse`; the `P-ctl-as` and `P-b9-as` rows of `ui.tsv`,
   and both `-gen` rows. **L1b per unit:** `received`, `usb_branch`, `creating_after_usb`, `dlg`.

Also: B6-off and B6-on (`held`, `handoff`, `replay`, `replay_trig`, `sw_lines`), the optional runs that ran, every void
and its reason, every cycle whose pid changed, `serial_outside` on D-POCO before the round, D-MOTO's `mtp=`, how each
phone dump was taken (`wifi` or `cable`), every `mirror` line on D-POCO, and any `perm_*` above 0 outside B9.
