# usb-dialog, round 6 brief: the "No data transfer" lever with a proof, a permission dialog that survives a restart and a rebuild, and the USB list fixes

Published name: `usb-dialog-round6-brief.md`. Results go in `usb-dialog-round6-results.md`.
Evidence: upload `usb-dialog-round6-captures.zip` to the existing release `rig-evidence-usb-dialog`.

Units: D-POCO (POCO X3 NFC, API 35) and D-HP (HP Slate 7 Plus, API 17) as USB hosts, one at a time. D-MOTO (Motorola
edge 30 neo, API 34) is the wired phone. P-1 (the rig's RP2040 USB stand-in) is the list device on both hosts. D-HU,
D-SAM and D-DONGLE take no part.

Queue line: `usb-dialog-round6-brief.md` (one arm, `fork/fix/usb-permission-dialog` `ceca2126`, force-pushed; units
D-POCO, D-HP, D-MOTO, P-1; about 2 h 10 min; S1-P-N, TD-P, TD-H and DR-H are the point).

Pairing: possible only with a round that uses D-HU alone and has no hand steps during this one. If this round is the
second of a pair, it takes the lock `/tmp/ohu-rig-usbdlg.lock`.

Hand steps: cable moves, one plug and one unplug per cycle, the USB dialogs by a fixed rule (section 5), Clear defaults
when cued, one count per cycle, "No data transfer" set by hand once, two PC-cable replugs of D-MOTO, USB debugging on
D-MOTO back on twice, P-1 moved between the test PC and a host five times, and on D-HP one press of the USB button and
three presses of a list row button. One quarter turn of D-HP by hand only if the rotation setting does nothing.
Injected taps: 2, both in L-P, each on a dumped target.

**Time budget.** Build 20 min, with Prepare steps 1 to 6 beside it. Then:

| Part | Host | Runs, in this order, in one capture | Min |
|---|---|---|---|
| Prepare 7 to 10 | both | install, identity, backups, the rotation lever | 12 |
| Stage P | D-POCO, wireless adb; D-MOTO on its PC cable between plugs | S1-P-N (2 plugs), TD-P (2 plugs), L-P (P-1 identity 3) | 42 |
| Stage H | D-HP, wireless adb; D-MOTO, then P-1, on its OTG adapter | DR-H (1 plug), TD-H (1 plug), R-H (2 plugs), L-H (P-1 identities 4, 3, 1), P-1 back to its console | 46 |
| Cable moves, thermal gates | | | 10 |

**About 2 h 10 min** with the build. Write the results file after every run.

**S1-P-N, TD-P, TD-H and DR-H are the point of the round.**

- S1-P-N answers the Android 7.0 report again. Round 5 could not prove that D-MOTO was in "No data transfer", and
  plug 1 still read `accepted: MTP`. This round proves the mode two ways before plug 1, and grades the run on the mode
  the host really saw.
- TD-P and TD-H test that a service restart while our dialog is on screen opens no second dialog, and that the answer
  still connects. Each ask now carries its own token, and the service no longer empties the record when it stops.
- DR-H tests that a rebuilt dialog's false "denied" keeps the record entry. Below Android 12, SystemUI's
  `UsbPermissionActivity` sends its answer from `onDestroy()`, so a rotation sends "denied" and opens the dialog again.

## 1. Build and baseline

One arm, no baseline build. Rounds 1 to 5 give every "before" number.

| Ref | Commit | Tree |
|---|---|---|
| `fork/fix/usb-permission-dialog` | `ceca2126c8a6992b39330bc569b054476a7bd4c8` | `a6f5a409fe57d4daf9d615d35db4452a2fbee7dd` |

**History was rewritten.** The branch is force-pushed to `fork` over round 5's `4a603930e`. It is 6 commits on `main`
`2018d8f09`: `99c0f382`, `143c4593`, `a69d2335`, `4a603930`, `0bb61637`, `ceca2126`. The first four are round 5's,
unchanged. The dev host suite at this commit: **4357 JVM tests, 0 failures**. Do not run the unit tests on the rig. No
other queued round shares this base.

**`get_apk.sh`**
```bash
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round6; mkdir -p $OUT
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh usb-dialog candidate main | tail -1)}   # record the path in Setup notes
git -C $WT fetch fork fix/usb-permission-dialog
git -C $WT checkout --detach ceca2126c8a6992b39330bc569b054476a7bd4c8    # fails if the force-push is missing: stop, UNTESTABLE (build)
git -C $WT rev-parse 'HEAD^{tree}'                 # MUST print a6f5a409fe57d4daf9d615d35db4452a2fbee7dd
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
cp rig-data/apks/*.apk $OUT/apk-C.apk               # at once: build_hur.sh deletes the previous APK. Run from StudioProjects
md5sum $OUT/apk-C.apk                                # record in Setup notes
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF 'ohu-usb-ask'                       # MUST be 1 or more (0bb61637)
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF 'arrived while permission is held'  # MUST be 1 or more (0bb61637)
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF 'UsbSysfsNames'                     # MUST be 1 or more (ceca2126)
```

**Identity on each host, after the install:** `send ACTION_QUERY_STATE` carries `ceca2126`, and the md5 of the pulled
live APK matches `apk-C.apk`. Install with `adb install -r -d`. Never uninstall.

## 2. What this is and why

Rounds 1 to 5 fixed the "dialog on every connect" fault, added the "Connect when plugged in" gate, and proved the
blacklist on API 17. Round 5 left the S1-P-N lever unproven, and the code review after it found three more faults.

**S1-P-N had no proof of its lever.** `svc usb setScreenUnlockedFunctions none` gave no read-back on D-MOTO
(`dumpsys usb` prints no field for `none`). The operator set "No data transfer" by hand. Plug 1 then read
`accepted: MTP` with the same `22B8:2E82` as the File transfer arm. The code reports what it reads, so the phone was
still in MTP. The grade calls that a rig result, not a branch result.

**Commit `0bb61637`, "USB: end a permission entry only on its own request's answer".** `UsbPermissionAsks` keeps one
entry per bus path while our dialog is up. Before this commit, the service emptied the record when it stopped, and any
answer ended the entry. Now:

- each ask makes a token and puts it in its PendingIntent's data, `ohu-usb-ask:<token>`;
- a non-exported manifest receiver, `UsbPermissionResultReceiver`, hands each answer to
  `UsbPermissionAsks.answered(path, token, granted)` and logs
  `UsbPermissionAsks: answer for <path> (granted=<bool>); entry ended|kept`;
- only a grant with the entry's own token ends it. After a denial, automatic and recovery asks stay `ASKED` for that
  path until a detach. A user ask (the USB button, `ACTION_CHECK_USB`) passes after 3 s (`USER_REPEAT_MS`);
- the service no longer clears the record when it stops, and the recovery path no longer cancels its PendingIntent;
- a denial that arrives while permission is already held is dropped:
  `USB permission: a denial for <name> arrived while permission is held; ignoring it`.

The reason for "only a grant": below Android 12, SystemUI's `UsbPermissionActivity` declares no `configChanges` and
sends its answer from `onDestroy()`. A rotation or a uiMode change rebuilds it, so the old dialog answers "denied" and a
new one opens with the same PendingIntent. Under the earlier code that false denial ended the entry, so the next
automatic check asked again and stacked a second dialog.

**Commit `ceca2126`, the USB list and the key below API 21.**

- `UsbListFragment` tested `isInAccessoryMode` before `isBlacklisted`. A blacklisted accessory-mode device
  (`18D1:2D00`) showed "Allowed" with the button off, so the user could not see or remove its key. The bind code now
  tests the blacklist first.
- Below API 21, `UsbDeviceCompat.blacklistKey` now reads the manufacturer and product strings from
  `/sys/bus/usb/devices/*/{busnum,devnum,manufacturer,product}` (`UsbSysfsNames`). So on D-HP a named device gets a
  `name:` key. P-1's identities 1 to 5 all report "Magic Communication Tec." / "Auto Box", so one key,
  `name:magic communication tec. auto box`, holds every P-1 identity. Round 5 stored `vidpid:1314:1521`, which held
  identities 4 and 5 only.
- A key that is still VID:PID only (a device with no names) gets a long toast when the list adds it:
  `This device gives no name, so the blacklist matches its USB ID: every device with that ID, and only in this USB mode. In Android Auto mode it can still connect.`
  P-1 always has names, so this round cannot show the toast.

**The levers this round uses, read in the code at `ceca2126`:**

- `send ACTION_STOP_SERVICE` relays `AapService.ACTION_STOP_SERVICE`. With no session and no wireless launcher, the
  service calls `stopSelf()` and logs `AapService destroying... (`. The process stays, and so does the record, which is
  process-wide. A changed pid voids the run.
- `send ACTION_REFRESH_SENSORS` starts the service with a plain action and no user flag. `onCreate` logs
  `AapService creating...` and runs `usbLauncherManager.checkAlreadyConnected()`. With `auto-connect-single-usb=true`
  that scan reaches `performSingleConnect`, logs `Single USB auto-connect: device found but no permission`, and asks
  with the automatic source. That is "the next automatic check" in TD and DR.
- `send ACTION_CHECK_USB` asks as the user. A user ask may pass an open entry after 3 s by design, so it is never the
  restart lever.
- **The rotation lever on D-HP** is `settings put system accelerometer_rotation 0`, then
  `settings put system user_rotation <n>`. Android 4.2 has the `settings` command and has no `cmd uimode`. The
  rotation must be a quarter turn: a half turn keeps the orientation, so nothing is rebuilt. D-HP sits at
  `mRotation=3` (`units/D-HP.md`), so the script asks for `(mRotation + 1) % 4`. Prepare 10 proves the lever on the
  unit by reading `dumpsys window` before and after. If it does not move `mRotation`, DR-H uses a hand quarter turn.

## 3. What is different about this round

- **S1-P-N proves D-MOTO's mode before plug 1, two ways.** First, with USB debugging on and D-MOTO replugged on its PC
  cable: `getprop sys.usb.state` must read `adb` for "No data transfer" (`mtp,adb` for File transfer). Second, after
  debugging goes off: the test PC lists D-MOTO's interfaces from sysfs (`ifaces.sh`). That list must have no `06/01/01`
  interface and no `ff/ff/00` interface named `MTP`. The plug into D-POCO comes straight after, with no change between.
- **S1-P-N is graded on the mode it really had.** If the proof fails, S1-P-N is UNTESTABLE (lever not proven), not
  FAIL. If the proof passes and plug 1 still reads `accepted: MTP`, S1-P-N is UNTESTABLE (lever not in force at the
  host), with every number recorded.
- **S1-P-N has 2 plugs, not 3.** Plug 2 shows whether the tick sticks. Round 5's third plug added nothing.
- **S1-P-F is not re-run as its own run.** TD-P's plug 1 is the same MTP first offer. Its `mtp_A` field prints `yes`,
  not `true`; round 5's brief wrote `true`.
- **The answer is graded on the record's own line now:** `UsbPermissionAsks: answer for <path> (granted=...); entry ...`.
  `USB permission granted for ` still shows that the service acted on a grant.
- **D-MOTO has USB debugging off in stage P and on in stage H.** On D-POCO the MTP path needs debugging off (template
  §7b). On API 17 the mode makes no difference (round 5), and debugging on keeps the phone capture live. Stage P's
  phone capture is one dump per arm, cut per plug by time (`phslice.py`), as in round 5.
- **D-HP shows Android's "Open" dialog at a plug, and TD-H and DR-H cancel it.** Our dialog must be the one on screen.
  The operator presses Cancel on Android's dialog and leaves ours. If ours did not open by itself, the script asks as
  the user 40 s after the plug (`ask_by=verb`).
- **DR-H changes D-HP's rotation and puts it back after the unplug.** Prepare records `user_rotation`,
  `accelerometer_rotation` and `mRotation`; the end of stage H restores both settings. A dialog that is rebuilt starts
  no new `START u0` line, so DR-H grades the rebuild on the `granted=false` answer line and on the operator's count.
- **TD-P may be INCONCLUSIVE on D-POCO for a reason outside the branch.** After `ACTION_STOP_SERVICE` the app has no
  activity in front. API 35 can refuse a foreground-service start from the background. The service then logs
  `ForegroundServiceStartNotAllowedException/Exception caught in onStartCommand: `, and the run marks `NO_RESTART`.
  Prepare reads the app's overlay permission, which lifts that limit.
- **The USB list opens only with 0 or 2 or more usable devices** (`HomeFragment.onUsbClicked`). A blacklisted device is
  not usable, so both list runs start with a key written by `xprefs`. L-H seeds the old `vidpid:1314:1521` form: the
  first row press removes it and every other form for that device, and the third press adds the key the app chooses
  now.
- **L-H proves that the app's uid can read the sysfs names on D-HP**, with `run-as` and a small script, before it
  grades the key. `UsbSysfsNames` logs nothing, so this read and the stored key are the only evidence.
- **D-HP's `uiautomator dump` has no `resource-id`** (round 5). L-H uses hand presses and reads the list by node text.
  L-P on D-POCO taps dumped targets.
- **`xprefs` writes only `settings.xml`.** On D-POCO the service reads the device-protected mirror
  (`Settings.isUsbDeviceBlacklisted(context, device)`). `App.onCreate` copies the set into the mirror, and every
  `xprefs` call is followed by a `launch`, so the key is in force. L-P reads the mirror back too. Below API 24 there
  is no mirror.
- **P-1 is set on the test PC, never on a host** (round 5). The HOLD cue says 4 s: count to four slowly, or the press
  steps P-1 to the next identity. The PCPICO cue names D-HP's OTG adapter in both stages; in stage P read it as "the
  host's port".
- **P-1 reaches D-POCO on D-MOTO's USB-C to USB-C cable.** If D-POCO sees no attach, the retry cue asks for a USB-C OTG
  adapter with P-1's USB-A to USB-C cable. Record which one worked.
- **The log level is VERBOSE (`log-level=0`)**, for the `UsbDiagnostics:   ` verdict line of an accepted device.
- **Thermal.** `rig-quirks/topics/tooling.md`: `rig_thermal.sh wait 75` before each stage (once more on exit 1),
  `rig_thermal.sh watch` during it, and UNTESTABLE (host thermal) after a second failed wait. Round 4's `th_gate` does
  this.
- Read `rig-quirks/topics/tooling.md`, `units/D-HP.md`, `units/D-POCO.md`, `units/D-MOTO.md`,
  `tools/pico-usb-stand-in/README.md` and template §7b before the first run.

## 4. Settings keys

Neither host is rooted. Write with the app stopped. `setkeys` (round 4) and `xprefs` (round 5) do it and read every key
back. Back up `settings.xml` on each host in Prepare, and restore it at the end of the host's stage with `xrestore`.

`S1KEYS` is round 5's, unchanged:

```
int:log-level=0 int:onboarding-version=2 bool:allow-external-configuration=true
int:wifi-connection-mode=0 set:connection-modes=usb
bool:auto-connect-last-session=false bool:auto-connect-single-usb=false int:auto-connect-delay-seconds=0
bool:has-accepted-disclaimer=true set:allow-devices= bool:use-libusb=false bool:kill-on-disconnect=false
bool:enable-floating-button=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false
bool:auto-start-on-wifi=false bool:listen-for-usb-devices=true bool:usb-connect-on-plug-in=true
bool:auto-start-on-usb=false bool:reopen-on-reconnection=false set:usb-blacklist= del:video-profile-starvation-cap
```

Leave the audio keys as they are (the rig's worst case is deliberate) and leave `screen-orientation` as found.

| Writer and tag | `auto-connect-single-usb` | `usb-blacklist` | `allow-devices` | Runs |
|---|---|---|---|---|
| `setkeys s1N` (`S1KEYS`) | `false` | empty | empty | S1-P-N |
| `setkeys td` (`TDKEYS`) | `true` | empty | empty | TD-P, DR-H, TD-H |
| `setkeys s1R` (`S1KEYS`) | `false` | empty | empty | R-H |
| `xprefs` (lp) | `false` | `vidpid:18d1:2d00` | | L-P |
| `xprefs` (lh) | `false` | `vidpid:1314:1521`, then the app's own | empty | L-H, and its identity 3 and 1 plugs |
| `xprefs` (unbl) | `false` | empty | empty | after L-P and after L-H |

`TDKEYS` is `S1KEYS` with `auto-connect-single-usb=true`. That key makes the restarted service's own scan reach the
device. `kill-on-disconnect=false` keeps the process alive through the stop. An empty cell keeps the value before it.

## 5. Cabling, hand steps and the answer rules

| Part | On the PC | On a host port | Wireless adb |
|---|---|---|---|
| Prepare | D-HP, D-POCO, D-MOTO | | |
| Stage P, D-MOTO arms | D-HP; D-MOTO between plugs | D-MOTO on D-POCO's cable, at each PLUG cue | D-POCO, D-MOTO |
| Stage P, L-P | D-HP, D-MOTO; P-1 at each PCPICO cue | P-1 on D-POCO, at its PLUG cue | D-POCO, D-MOTO |
| Stage H | D-POCO, D-MOTO between plugs; P-1 at each PCPICO cue | D-MOTO, then P-1, on D-HP's OTG adapter | D-HP, D-MOTO |

At most three test-PC ports are in use. P-1 is never on the test PC and on a host at the same time.

| Id | Hand step | When | Why there is no verb |
|---|---|---|---|
| CAB | Move the cables as the table says | each part's start | hardware |
| PLUG / UNPLUG | Plug or unplug D-MOTO or P-1 at the host | each cue | hardware |
| DLG | Answer the USB dialogs by the run's rule below | during each cycle | a SystemUI dialog |
| CLEAR | On the App info screen the script opens, press Clear defaults (API 35: Open by default, then Clear default preferences) | each CLEAR cue | Settings is another app |
| COUNT | Tell the host, per plug, how many USB dialogs you saw and the first word of each | each COUNT cue | the second column of the dialog count |
| UNLOCK | Before every plug of D-MOTO, check that its screen is on and unlocked | every PLUG cue for D-MOTO | D-MOTO can sit behind a PIN, and its USB mode applies only while unlocked |
| DEFUSB | On D-MOTO choose Developer options > Default USB configuration > "No data transfer", even if it reads so already | S1-P-N, once | the shell route has no read-back |
| REPC | Keep D-MOTO unlocked. Unplug its PC cable, count to three, plug it back into the PC | S1-P-N and TD-P, once each | a fresh connection applies the default mode |
| DEBUG-OFF | On D-MOTO turn USB debugging off (Settings > System > Developer options) | only if the shell command left adb up | the shell route is tried first |
| DEBUG-ON | On D-MOTO turn USB debugging back on, same screen | the end of S1-P-N and of TD-P | with debugging off, no adb reaches D-MOTO |
| PCMOTO | Plug D-MOTO into its PC cable, then unplug it when cued | only if D-MOTO's wireless adb does not come back | the phone dump needs a link |
| DIALOGS | Tell the host how many "Allow" dialogs are on the host's screen now. Touch nothing | TD-P, DR-H and TD-H, once each | what the screen shows |
| ANSWER | On the "Allow" dialog tick the box if it has one, then press OK | TD-P, DR-H and TD-H, once each | a SystemUI dialog, answered after the restart |
| ROTATE | Turn D-HP a quarter turn and hold it there until the UNPLUG cue | DR-H, only if the rotation setting did not rebuild the dialog in 10 s | the fallback for the settings lever |
| PCPICO / UNPICO | Plug P-1 into a free test-PC port, or unplug it from there | each cue | hardware |
| HOLD | Hold KEY on P-1 while you count to four slowly, then let go. The LED then blinks once a second | only when P-1 is not on its console | the console is gone while P-1 presents an identity |
| LIST | On D-HP press the USB button ("Connect over USB") once | L-H, once | API 17 dumps carry no resource id, so no tap can be aimed |
| TAPROW | On D-HP press the coloured button at the right of the P-1 row once | L-H, three cues | the same |
| RESEAT | Reseat the OTG adapter in D-HP's port | only after two `NO_PLUG` in a row on D-HP | hardware |

**Open the cue terminal first.** Prepare step 1 prints the cue file's full path. On this rig's default data folder:

```bash
tail -n0 -F /home/oscar/Coding/StudioProjects/rig-data/rounds/usb-dialog-round6/cues.log
```

Android's own dialog starts with "Open" ("Open Open Headunit ..."). The app's own starts with "Allow" ("Allow ... to
access the USB device?"). Read the operator this table once, at the start:

| Runs | Rule for every USB dialog |
|---|---|
| S1-P-N, R-H, TD-P plug 2 | Every plug: tick the box if the dialog has one, then press OK. Do not touch any notification. |
| TD-P plug 1, DR-H, TD-H | Until the ANSWER cue: "Open ...": press Cancel at once. "Allow ...": touch nothing, also when it goes away and comes back. From the ANSWER cue: tick the box if it has one, then OK, on every dialog. |
| L-P, L-H | "Open ...": press OK, tick nothing. "Allow ..." (not expected): press Cancel. Touch the host's screen only at a LIST or TAPROW cue. |

Always: act only on a cue or by the rule. Do not touch D-MOTO except for UNLOCK, DEFUSB, REPC, DEBUG-OFF and DEBUG-ON.
Do not press any button on P-1 except at a HOLD cue.

## 6. Helpers

Copy every file from `$RIG_DATA/rounds/usb-dialog-round5/scripts/` into `$OUT/scripts/`. That folder holds round 4's
helpers and round 5's (`lib5.sh`, `s1.sh`, `x1.sh`, `bl.sh`, `p1.sh`, `xprefs.py`, `xpcp.sh`, `node.py`, `blset.py`,
`phslice.py`, `pico.sh`, `pico/`, `pico.env`). If it is gone, extract them from `usb-dialog-round4-brief.md` and
`usb-dialog-round5-brief.md` on this branch. Then save every block below into the same folder with
`extract_brief_blocks.py`. Run every script from `$OUT/scripts/`. Run every adb call against one unit in sequence
(template rule 8). Grep every capture with `-a`.

Helpers used as earlier rounds defined them: round 4's `A`, `F`, `send`, `mark`, `cue`, `launch`, `home`, `cap`,
`alive`, `uncap`, `since`, `cnt`, `firstts`, `waitfor`, `th_gate`, `th_end`, `bat`, `hupid`, `usbdef`, `ident`,
`setkeys`, `cleardef`, `dump`, `cycle` (kind `session`), `sess`, `row`, `runplugs` and `timeline.sh` (its SUMMARY
fields are in round 4's brief, section 6); round 5's `run5`, `plugs`, `waitany`, `dbgon`, `moto_dump`, `xprefs`,
`xrestore`, `tapnode`, `blread`, `pset` and the scripts `x1.sh`, `bl.sh`, `p1.sh`, `phslice.py`, `node.py`,
`blset.py`.

**`lib6.sh`**
```bash
# lib6.sh: round 5's helpers with this round's folder and candidate, plus this round's additions.
. ./lib5.sh
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round6; mkdir -p $OUT
CAND=ceca2126
TDKEYS=${S1KEYS/bool:auto-connect-single-usb=false/bool:auto-connect-single-usb=true}
TDRULE="Until the ANSWER cue: Open: Cancel at once. Allow: touch nothing, also when it goes and comes back. From ANSWER: tick if there is a box, then OK, every dialog. D-MOTO unlocked before the plug."
P1KEY="name:magic communication tec. auto box"
pre_R() { home; }
# MA: adb on D-MOTO's PC cable (MOTO_USB in wireless.env)
MA() { adb -s $MOTO_USB "$@"; }
# usbup N: wait up to N s for D-MOTO's PC link to answer; usbgone N: wait up to N s for it to drop
usbup() { local t=0; until [ "$(MA get-state 2>/dev/null)" = device ]; do sleep 1; t=$((t+1)); [ $t -ge $1 ] && return 1; done; }
usbgone() { local t=0; while [ "$(MA get-state 2>/dev/null)" = device ]; do sleep 1; t=$((t+1)); [ $t -ge $1 ] && return 1; done; }
# pcdev: how many D-MOTO devices the test PC sees in sysfs
pcdev() { ./ifaces.sh 22b8 | grep -c '^dev '; }
# window MARK1 MARK2: the capture between two markers, repeats dropped
window() { tr -d '\r' < $(F) | awk '!seen[$0]++' | sed -n "/AutomationMarker: $1\$/,/AutomationMarker: $2\$/p"; }
# wc1 MARK1 MARK2 ERE: lines between two markers that match an extended regex
wc1() { window "$1" "$2" | grep -acE -- "$3"; }
# answers P: one marks.txt line with the record's answer lines and the service's grant and denial lines in plug P
answers() { echo "$1 grants=$(wc1 $1-cue $1-unplug 'USB permission granted for ') denied=$(wc1 $1-cue $1-unplug 'USB permission denied for ') held_denial=$(wc1 $1-cue $1-unplug 'USB permission: a denial for .* arrived while permission is held') ans_true_ended=$(wc1 $1-cue $1-unplug 'UsbPermissionAsks: answer for .* \(granted=true\); entry ended') ans_true_kept=$(wc1 $1-cue $1-unplug 'UsbPermissionAsks: answer for .* \(granted=true\); entry kept') ans_false_kept=$(wc1 $1-cue $1-unplug 'UsbPermissionAsks: answer for .* \(granted=false\); entry kept') ans_false_ended=$(wc1 $1-cue $1-unplug 'UsbPermissionAsks: answer for .* \(granted=false\); entry ended')" >> $OUT/marks.txt; }
# rot: D-HP's displayed rotation, from dumpsys window
rot() { A shell dumpsys window | tr -d '\r' | grep -aoE -m1 'mRotation=[0-3]' | cut -d= -f2; }
```

**`ifaces.sh`**
```bash
#!/usr/bin/env bash
# ifaces.sh VID: on the test PC, one "dev" line per USB device with idVendor VID, then one "if" line per interface:
# class/subclass/protocol in hex and the interface name ("-" when it has none). Read from sysfs, no root. No verdict.
for d in /sys/bus/usb/devices/*; do
  [ "$(cat $d/idVendor 2>/dev/null)" = "$1" ] || continue
  b=$(basename $d); echo "dev $b $(cat $d/idVendor):$(cat $d/idProduct) cfg=$(cat $d/bConfigurationValue 2>/dev/null)"
  for i in $d/$b:*; do [ -d "$i" ] || continue
    echo "if $(basename $i) $(cat $i/bInterfaceClass)/$(cat $i/bInterfaceSubClass)/$(cat $i/bInterfaceProtocol) name=$(cat $i/interface 2>/dev/null || echo -)"
  done
done
```

**`sysnames.sh`** (pushed to `/data/local/tmp/` on D-HP in Prepare; run under `run-as`, so it reads as the app's uid)
```bash
#!/system/bin/sh
# One line per USB device in sysfs: path, busnum, devnum, idVendor:idProduct, [manufacturer] [product]
for d in /sys/bus/usb/devices/*; do
  [ -f $d/busnum ] || continue
  echo "$d bus=$(cat $d/busnum) dev=$(cat $d/devnum) id=$(cat $d/idVendor):$(cat $d/idProduct) [$(cat $d/manufacturer 2>/dev/null)] [$(cat $d/product 2>/dev/null)]"
done
```

**`s6.sh`**
```bash
# s6.sh: the D-MOTO runs. proof, the two stage P arms, and tdcycle (TD-P, DR-H and TD-H plug 1).
# proof M TAG: D-MOTO's USB mode before plug 1, read on the phone and on the test PC. M is none or mtp.
# D-MOTO is on its PC cable with debugging on at entry. Returns 0 with debugging off (NOPHONE=1), 1 on a failed proof.
proof() { local M=$1 T=$2 k st cf ok=0 n mtp adb
  usbup 30 || { echo "$T NO_PC_LINK" >> $OUT/marks.txt; cue "PCMOTO $T: plug D-MOTO into its PC cable"; usbup 120 || return 1; }
  for k in 1 2; do
    MA shell svc usb setScreenUnlockedFunctions $M > $OUT/$T-svc$k.txt 2>&1
    [ $M = none ] && { cue "DEFUSB $T: on D-MOTO choose Developer options > Default USB configuration > No data transfer, even if it reads so already. Then say done"; sleep 60; }
    cue "REPC $T: keep D-MOTO unlocked. Unplug its PC cable, count to three, plug it back into the PC"
    usbgone 90 || echo "$T REPC_NOT_SEEN" >> $OUT/marks.txt
    usbup 90 || { echo "$T NO_PC_LINK" >> $OUT/marks.txt; return 1; }
    sleep 3
    st=$(MA shell getprop sys.usb.state | tr -d '\r'); cf=$(MA shell getprop sys.usb.config | tr -d '\r')
    echo "$T proof$k state=[$st] config=[$cf] dumpsys=[$(MA shell dumpsys usb 2>/dev/null | tr -d '\r' | grep -aiE -m1 'screen.?unlocked.?functions')]" >> $OUT/marks.txt
    case $M in none) [ "$st" = adb ] && { ok=1; break; };; mtp) echo "$st" | grep -q mtp && { ok=1; break; };; esac
  done
  [ $ok = 1 ] || { echo "$T PROOF_FAIL phone" >> $OUT/marks.txt; return 1; }
  # debugging off on the PC link; the gadget then re-enumerates on the PC in the mode it will show the host
  timeout 8 adb -s $MOTO_USB shell settings put global adb_enabled 0 >/dev/null 2>&1
  if ! usbgone 20; then cue "DEBUG-OFF $T: on D-MOTO turn USB debugging off (Settings > System > Developer options)"
    usbgone 120 || { echo "$T DEBUG_STILL_ON" >> $OUT/marks.txt; return 1; }; fi
  adb disconnect $MOTO_W >/dev/null 2>&1; NOPHONE=1
  echo "$T debugging off host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt
  k=0; until [ "$(pcdev)" -ge 1 ] || [ $k -ge 30 ]; do sleep 1; k=$((k+1)); done; sleep 3
  ./ifaces.sh 22b8 > $OUT/$T-pc-ifaces.txt
  n=$(grep -c '^dev ' $OUT/$T-pc-ifaces.txt); mtp=$(grep -acE ' 06/01/01 | ff/ff/00 name=MTP$' $OUT/$T-pc-ifaces.txt)
  adb=$(grep -ac ' ff/42/01 ' $OUT/$T-pc-ifaces.txt)
  echo "$T pc dev=$n mtp=$mtp adb=$adb ifaces=[$(grep '^if ' $OUT/$T-pc-ifaces.txt | cut -d' ' -f3- | tr '\n' ';')]" >> $OUT/marks.txt
  case $M in none) [ $mtp = 0 ] && [ $adb = 0 ];; mtp) [ $mtp -ge 1 ] && [ $adb = 0 ];; esac \
    || { echo "$T PROOF_FAIL pc" >> $OUT/marks.txt; return 1; }
  echo "$T PROOF_OK" >> $OUT/marks.txt; return 0; }
# armN: S1-P-N. setkeys, the proof, two session plugs, then DEBUG-ON and the phone dump.
armN() { local T=P-N P
  cleardef $T-clear
  setkeys s1N $S1KEYS || { echo "$T UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  usbdef $T-pre
  MA shell input keyevent 224; MA shell wm dismiss-keyguard; timeout 5 adb -s $MOTO_USB logcat -c; mark $T-phone-start
  proof none $T || { echo "$T UNTESTABLE lever" >> $OUT/marks.txt; dbgon $T; return 0; }
  RULES="$S1RULE"; run5 N "session session"; [ $? -eq 2 ] && return 2
  for P in $(plugs $T); do ./x1.sh $(F) $P 'VID: 22B8' >> $OUT/x1.tsv; answers $P; done
  usbdef $T-post; dbgon $T; python3 -I ./phslice.py $OUT/$T-arm-phone.txt $OUT/marks.txt $OUT $(plugs $T) >> $OUT/phone.tsv; return 0; }
# armTD: TD-P. File transfer, debugging off; plug 1 is tdcycle, plug 3 a session plug that must show no dialog.
armTD() { local T=P-TD P rc
  cleardef $T-clear
  setkeys td $TDKEYS || { echo "$T UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  usbdef $T-pre
  MA shell input keyevent 224; MA shell wm dismiss-keyguard; timeout 5 adb -s $MOTO_USB logcat -c; mark $T-phone-start
  proof mtp $T || { echo "$T UNTESTABLE mode" >> $OUT/marks.txt; dbgon $T; return 0; }
  tdcycle $T-p1; rc=$?; [ $rc -eq 2 ] && return 2; [ $rc -eq 1 ] && { tdcycle $T-p2; rc=$?; [ $rc -eq 2 ] && return 2; }
  RULE="$S1RULE"; cue "NEXT $T-p3: read the rule, nothing starts for 12 s"; sleep 12
  cycle $T-p3 session; rc=$?; [ $rc -eq 2 ] && return 2; RULE=
  cue "COUNT $T-p3: tell the host how many USB dialogs you saw and the first word of each (25 s)"; sleep 25
  for P in $(plugs $T); do ./x1.sh $(F) $P 'VID: 22B8' >> $OUT/x1.tsv; answers $P; done
  usbdef $T-post; dbgon $T; python3 -I ./phslice.py $OUT/$T-arm-phone.txt $OUT/marks.txt $OUT $(plugs $T) >> $OUT/phone.tsv; return 0; }
# rebuild P: DR-H only. A quarter turn by the settings lever (ROTATE by hand if no false denial follows in 10 s).
rebuild() { local P=$1 r0 r1 want
  r0=$(rot); want=$(( (${r0:-3} + 1) % 4 )); mark $P-rot
  A shell settings put system accelerometer_rotation 0; A shell settings put system user_rotation $want; sleep 3; r1=$(rot)
  echo "$P rot_before=${r0:-none} rot_want=$want rot_after=${r1:-none}" >> $OUT/marks.txt
  if waitany $P-rot 'UsbPermissionAsks: answer for .* \(granted=false\)' 10; then echo "$P rotate_by=settings" >> $OUT/marks.txt; return 0; fi
  A shell settings put system accelerometer_rotation 1
  cue "ROTATE $P: turn D-HP a quarter turn now and hold it there until the UNPLUG cue. Touch no dialog"
  if waitany $P-rot 'UsbPermissionAsks: answer for .* \(granted=false\)' 60; then echo "$P rotate_by=hand" >> $OUT/marks.txt
  else echo "$P NO_REBUILD" >> $OUT/marks.txt; fi; }
# tdcycle P [rot]: one plug of D-MOTO. Our dialog up; with "rot", rebuild it; past the 30 s attach window, stop the
# service and start it again with no user flag; count the dialogs; then the answer and the session.
# Returns 0 graded, 1 NO_PLUG, 2 low battery.
tdcycle() { P=$1; local how=${2:-} pid0 pid1 t0 t w; PRE=-
  [ "$(bat)" -ge $BSTOP ] || { echo "$P BATTERY_LOW $(bat)" >> $OUT/marks.txt; return 2; }
  alive; home; pid0=$(hupid)
  [ "${NOPHONE:-0}" = 1 ] || { adb connect $MOTO_W >/dev/null 2>&1; timeout 5 adb -s $MOTO_W logcat -c 2>/dev/null; }
  RULE="$TDRULE"; cue "NEXT $P: read the rule, nothing starts for 12 s"; sleep 12
  mark $P-cue; cue "PLUG $P: plug D-MOTO's cable into $HUNAME now (take it off the PC cable first)"
  if ! waitfor $P-cue 'Normal USB device attached: ' 120; then
    echo "$P NO_PLUG" >> $OUT/marks.txt; mark $P-end; cue "UNPLUG $P: unplug D-MOTO from $HUNAME if it is plugged in"; sleep 15; mark $P-unplug; return 1; fi
  t0=$(date +%s); echo "$P plug=$(firstts $P-cue 'Normal USB device attached: ')" >> $OUT/marks.txt
  # 1. one dialog of ours on screen: the plug's own ask, else the user's ask 40 s after the plug
  if waitfor $P-cue 'Requesting USB permission for ' 40; then echo "$P ask_by=plug" >> $OUT/marks.txt
  else mark $P-verb; send ACTION_CHECK_USB >> $OUT/marks.txt
    if waitfor $P-verb 'Requesting USB permission for ' 15; then echo "$P ask_by=verb" >> $OUT/marks.txt
    else echo "$P NO_ASK" >> $OUT/marks.txt; fi; fi
  # 2. DR-H only: rebuild the dialog, so it answers "denied" and opens again
  [ "$how" = rot ] && rebuild $P
  # 3. 45 s after the plug the 30 s attach window is over, so older code would ask again. Then the stop.
  t=$(( 45 - ($(date +%s) - t0) )); [ $t -gt 0 ] && sleep $t
  mark $P-stop; send ACTION_STOP_SERVICE >> $OUT/marks.txt
  waitfor $P-stop 'AapService destroying... (' 20 || echo "$P NO_DESTROY" >> $OUT/marks.txt
  sleep 3; mark $P-restart; send ACTION_REFRESH_SENSORS >> $OUT/marks.txt
  waitfor $P-restart 'AapService creating...' 20 || echo "$P NO_RESTART" >> $OUT/marks.txt
  waitfor $P-restart 'UsbLauncher: permission for ' 20 || echo "$P NO_DEC" >> $OUT/marks.txt
  sleep 10                                       # 10 s: a second dialog would start in these 10 s
  pid1=$(hupid); echo "$P pid_before=$pid0 pid_after=$pid1" >> $OUT/marks.txt
  cue "DIALOGS $P: tell the host how many Allow dialogs are on $HUNAME's screen now. Touch nothing (20 s)"; sleep 20
  # 4. the answer, then the session
  mark $P-answer; RULE="$S1RULE"
  cue "ANSWER $P: on the Allow dialog tick the box if it has one, then press OK. Every later dialog: tick the box if it has one, then OK"
  sess $P-answer
  mark $P-end; cue "UNPLUG $P: unplug D-MOTO from $HUNAME"
  waitfor $P-end 'USB_DEVICE_DETACHED' 60 && echo "$P unplug=$(firstts $P-end 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt
  mark $P-unplug; RULE=
  cue "COUNT $P: tell the host how many USB dialogs you saw since the plug and the first word of each (25 s)"; sleep 25
  moto_dump $P; usbdef $P-after; answers $P
  echo "$P restart_refused=$(window $P-restart $P-answer | grep -acF 'ForegroundServiceStartNotAllowedException/Exception caught in onStartCommand: ') noperm=$(window $P-restart $P-answer | grep -acF 'Single USB auto-connect: device found but no permission') refuse=$(window $P-restart $P-answer | grep -acF 'is already open; not opening a second') gap_ans=$(wc1 $P-restart $P-answer 'UsbPermissionAsks: answer for ')" >> $OUT/marks.txt
  ./timeline.sh $(F) $P > $OUT/$P.timeline.tsv; row $P $OUT/$P.timeline.tsv
  w=$P-stop; [ "$how" = rot ] && w=$P-rot
  ./timeline.sh $(F) $P/pre $P-cue $w > $OUT/$P-pre.timeline.tsv; row $P/pre $OUT/$P-pre.timeline.tsv
  if [ "$how" = rot ]; then
    ./timeline.sh $(F) $P/rot $P-rot $P-stop > $OUT/$P-rot.timeline.tsv; row $P/rot $OUT/$P-rot.timeline.tsv
    echo "$P rot_false_kept=$(wc1 $P-rot $P-stop 'UsbPermissionAsks: answer for .* \(granted=false\); entry kept') rot_false_ended=$(wc1 $P-rot $P-stop 'UsbPermissionAsks: answer for .* \(granted=false\); entry ended') rot_denied=$(wc1 $P-rot $P-stop 'USB permission denied for ')" >> $OUT/marks.txt
  fi
  ./timeline.sh $(F) $P/gap $P-restart $P-answer > $OUT/$P-gap.timeline.tsv; row $P/gap $OUT/$P-gap.timeline.tsv
  ./timeline.sh $(F) $P/post $P-answer $P-unplug > $OUT/$P-post.timeline.tsv; row $P/post $OUT/$P-post.timeline.tsv
  echo "$P post_true_ended=$(wc1 $P-answer $P-unplug 'UsbPermissionAsks: answer for .* \(granted=true\); entry ended')" >> $OUT/marks.txt
  return 0; }
```

**`list6.sh`**
```bash
# list6.sh: lp (L-P on D-POCO, P-1 identity 3) and lh (L-H on D-HP, P-1 identity 4, then 3 and 1).
# p1plug L ALT FIRST: cue the plug of P-1 and wait for its attach; one retry cue after a miss. 0 when it attached.
p1plug() { local L=$1 alt=$2
  mark $L-cue; cue "PLUG $L: plug P-1 into $HUNAME now ($3)"
  if waitany $L-cue 'USB Intent: .*USB_DEVICE_ATTACHED|Ignoring USB device attached in service \(' 120; then
    echo "$L plug=$(since $L-cue | grep -aE -m1 'USB Intent: .*USB_DEVICE_ATTACHED|Ignoring USB device attached in service \(' | cut -c1-18)" >> $OUT/marks.txt; return 0; fi
  echo "$L NO_PLUG" >> $OUT/marks.txt; cue "PLUG $L again: unplug P-1, then plug it into $HUNAME $alt"
  if waitany $L-cue 'USB Intent: .*USB_DEVICE_ATTACHED|Ignoring USB device attached in service \(' 120; then
    echo "$L plug=$(since $L-cue | grep -aE -m1 'USB Intent: .*USB_DEVICE_ATTACHED|Ignoring USB device attached in service \(' | cut -c1-18) retry=1" >> $OUT/marks.txt; return 0; fi
  echo "$L NO_PLUG2" >> $OUT/marks.txt; return 1; }
p1unplug() { mark $1-end; cue "UNPLUG $1: unplug P-1 from $HUNAME"
  waitfor $1-end 'USB_DEVICE_DETACHED' 60 && echo "$1 unplug=$(firstts $1-end 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt
  mark $1-unplug; RULE=
  cue "COUNT $1: tell the host how many USB dialogs you saw since the plug and the first word of each (25 s)"; sleep 25
  ./bl.sh $(F) $1 $1-cue $1-unplug >> $OUT/bl.tsv; ./p1.sh $(F) $1 $1-cue $1-unplug "$2" >> $OUT/p1.tsv; }
# sysread L IDS: the sysfs names as the app's uid reads them (D-HP), and the line for the P-1 identity IDS (vid:pid)
sysread() { A shell run-as $PKG sh /data/local/tmp/sysnames.sh 2>&1 | tr -d '\r' > $OUT/$1-sysfs.txt
  echo "$1 sys_lines=$(grep -ac ' bus=' $OUT/$1-sysfs.txt) sys_p1=$(grep -aF "id=$2" $OUT/$1-sysfs.txt | grep -acF '[Magic Communication Tec.] [Auto Box]') sys_line=[$(grep -aF "id=$2" $OUT/$1-sysfs.txt | head -1)]" >> $OUT/marks.txt; }
# lp: the 18d1:2d00 key, the list row, one tap on it, the key read back from settings.xml and from the mirror
lp() { local L=P-L3 x t; TAPN=0
  xprefs set:usb-blacklist=vidpid:18d1:2d00 bool:auto-connect-single-usb=false || { echo "$L UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  echo "$L stored_key=$(grep -acF '<string>vidpid:18d1:2d00</string>' $OUT/$ST-settings-now.xml)" >> $OUT/marks.txt
  launch; sleep 3; home
  RULE="$BLRULE"; cue "NEXT $L: read the rule, nothing starts for 12 s"; sleep 12
  p1plug $L "with a USB-C OTG adapter and P-1's USB-A to USB-C cable" "with D-MOTO's USB-C to USB-C cable" || { p1unplug $L 'VID: 18D1'; return 0; }
  sleep 15; mark $L-btn; launch; sleep 3      # 15 s: Android's dialog and the refusal land in these 15 s
  tapnode ':id/usb_button'; sleep 3; x=$OUT/$L-list0.xml; dump $x
  echo "$L list0_text=$(python3 -I ./node.py $x 'android:id/button1' '?') list0_state=$(python3 -I ./node.py $x 'android:id/button1' '')" >> $OUT/marks.txt
  t=$(python3 -I ./node.py $x 'android:id/button1' '?')     # a button may print its text in capitals
  if [ "$(echo "$t" | tr 'A-Z' 'a-z')" = blacklisted ]; then tapnode 'android:id/button1' "$t"; else echo "$L NO_BLACKLISTED_ROW ($t)" >> $OUT/marks.txt; fi
  sleep 2
  A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/$L-after.xml
  A shell run-as $PKG cat /data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml 2>/dev/null | tr -d '\r' > $OUT/$L-mirror.xml
  echo "$L after key=$(grep -acF '<string>vidpid:18d1:2d00</string>' $OUT/$L-after.xml) set=[$(python3 -I ./blset.py $OUT/$L-after.xml)] mirror=[$(python3 -I ./blset.py $OUT/$L-mirror.xml 2>/dev/null || echo unreadable)]" >> $OUT/marks.txt
  x=$OUT/$L-list1.xml; dump $x
  echo "$L list1_text=$(python3 -I ./node.py $x 'android:id/button1' '?') list1_state=$(python3 -I ./node.py $x 'android:id/button1' '')" >> $OUT/marks.txt
  mark $L-list-end; A shell input keyevent 4; home
  p1unplug $L 'VID: 18D1'
  xprefs set:usb-blacklist= bool:auto-connect-single-usb=false; blread $L-cleared; }
# pollp TAG PATTERN WANT: read settings.xml each second until PATTERN counts WANT (cap 60 s); the last read is TAG.xml
pollp() { local t=0; while [ $t -lt 60 ]; do A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/$1.xml
    [ "$(grep -acF -- "$2" $OUT/$1.xml)" = "$3" ] && return 0; sleep 1; t=$((t+1)); done; echo "$1 POLL_TIMEOUT" >> $OUT/marks.txt; return 1; }
# lh: identity 4 with the old vidpid key, the sysfs read, the list by hand, three row presses, the key the app wrote
lh() { local L=H-L4 OLD=vidpid:1314:1521 VP='VID: 1314 PID: 1521' x n
  xprefs set:usb-blacklist=$OLD set:allow-devices= bool:auto-connect-single-usb=false || { echo "$L UNTESTABLE settings" >> $OUT/marks.txt; return 1; }
  echo "$L stored_key=$(grep -acF "<string>$OLD</string>" $OUT/$ST-settings-now.xml)" >> $OUT/marks.txt
  launch; sleep 3; home
  RULE="$BLRULE"; cue "NEXT $L: read the rule, nothing starts for 12 s"; sleep 12
  p1plug $L "after you reseat the OTG adapter" "on the OTG adapter" || { p1unplug $L 'VID: 1314'; return 1; }
  sleep 15; sysread $L 1314:1521; launch; sleep 3
  mark $L-list; cue "LIST $L: on D-HP press the USB button (Connect over USB) once. The USB list opens"
  x=$OUT/$L-list0.xml; n=0; until dump $x; grep -aqiF 'text="Blacklisted"' $x || [ $n -ge 120 ]; do sleep 5; n=$((n+5)); done
  echo "$L list0 blacklisted_nodes=$(grep -aciF 'text="Blacklisted"' $x)" >> $OUT/marks.txt
  cue "TAPROW $L-1: press the coloured button at the right of the P-1 row once. It reads Blacklisted now"
  pollp $L-after1 "<string>$OLD</string>" 0
  mark $L-t1; sleep 2
  cue "TAPROW $L-2: press the same button once more. It reads Ignored now"
  pollp $L-after2 "<string>$VP</string>" 1
  sleep 2
  cue "TAPROW $L-3: press the same button once more. It reads Allowed now"
  pollp $L-after3 "<string>$P1KEY</string>" 1
  for n in 1 2 3; do echo "$L after_$n old=$(grep -acF "<string>$OLD</string>" $OUT/$L-after$n.xml) name=$(grep -acF "<string>$P1KEY</string>" $OUT/$L-after$n.xml) vidpid_keys=$(grep -acF '<string>vidpid:' $OUT/$L-after$n.xml) allowed=$(grep -acF "<string>$VP</string>" $OUT/$L-after$n.xml)" >> $OUT/marks.txt; done
  sleep 3; x=$OUT/$L-list3.xml; dump $x; echo "$L list3 blacklisted_nodes=$(grep -aciF 'text="Blacklisted"' $x)" >> $OUT/marks.txt
  blread $L-final; A shell input keyevent 4; home
  ./bl.sh $(F) $L/blk $L-cue $L-t1 >> $OUT/bl.tsv
  p1unplug $L 'VID: 1314'; return 0; }
# lhid L N IDS VIDPAT: P-1 on identity N with the app's own key kept; one plug, the sysfs read, a scan, the refusal lines
lhid() { local L=$1
  pset $2 || { echo "$L UNTESTABLE pset" >> $OUT/marks.txt; return 0; }
  RULE="$BLRULE"; cue "NEXT $L: read the rule, nothing starts for 12 s"; sleep 12
  p1plug $L "after you reseat the OTG adapter" "on the OTG adapter" || { p1unplug $L "$4"; return 0; }
  sleep 15; sysread $L $3
  send ACTION_CHECK_USB >> $OUT/marks.txt; sleep 8   # a user scan: the verdict line, and proof no path connects it
  p1unplug $L "$4"; }
```

**`stageP6.sh`**
```bash
#!/usr/bin/env bash
# ST=P ./stageP6.sh: S1-P-N, TD-P, then L-P, on D-POCO.
ST=P; . ./lib6.sh; . ./wireless.env; . ./s6.sh; . ./pico.env; . ./pico.sh; . ./list6.sh
HU=$POCO_W; HUNAME=D-POCO; export ST HU HUNAME; RULE=
cue "CAB P: take D-POCO off its PC cable. D-MOTO stays on its PC cable, unlocked. Have D-MOTO's USB-C to USB-C cable ready on D-POCO"
adb connect $HU >/dev/null; adb connect $MOTO_W >/dev/null
[ "$(bat)" -ge $BSTART ] || { echo "P BATTERY_START $(bat)" >> $OUT/marks.txt; exit 1; }
th_gate || exit 1
echo "P stay_on_before=$(A shell settings get global stay_on_while_plugged_in | tr -d '\r')" >> $OUT/marks.txt
A shell svc power stayon true; A shell am force-stop $PKG
S0=P-start-$(date +%H%M%S); cap; mark $S0; launch; sleep 3
[ "$(since $S0 | grep -aF 'START u0' | grep -acF 'MainActivity')" -lt 1 ] && { echo "P NO_START_LINES: CREATE=1" >> $OUT/marks.txt; export CREATE=1; }
armN; [ $? -eq 2 ] && exit 2
armTD; [ $? -eq 2 ] && exit 2
# L-P: P-1 on identity 3, set on the test PC
if [ "${PICO_OK:-0}" = 1 ] && pset 3; then lp; else echo "P-L3 UNTESTABLE pset" >> $OUT/marks.txt; fi
cleardef P-end-clear; usbdef P-end; mark P-end
send ACTION_EXPORT_LOG; sleep 5                    # the export path is on the data= line; pull that file
th_end; uncap; xrestore $OUT/backup-P.xml
A shell settings put global stay_on_while_plugged_in "$(sed -n 's/^P stay_on_before=//p' $OUT/marks.txt | head -1)"
```

**`stageH6.sh`**
```bash
#!/usr/bin/env bash
# ST=H ./stageH6.sh: DR-H, TD-H, R-H, then L-H (identities 4, 3, 1) on D-HP; P-1 back to its console at the end.
ST=H; . ./lib6.sh; . ./wireless.env; . ./s6.sh; . ./pico.env; . ./pico.sh; . ./list6.sh
HU=$HP_W; HUNAME=D-HP; export ST HU HUNAME; RULE=; NOPHONE=0
cue "CAB H: put the OTG adapter in D-HP's port and take D-HP off its PC cable. D-MOTO stays unplugged, debugging on"
adb connect $HU >/dev/null; adb connect $MOTO_W >/dev/null
[ "$(bat)" -ge $BSTART ] || { echo "H BATTERY_START $(bat)" >> $OUT/marks.txt; exit 1; }
th_gate || exit 1
echo "H stay_on_before=$(A shell settings get global stay_on_while_plugged_in | tr -d '\r')" >> $OUT/marks.txt
echo "H user_rotation_before=$(A shell settings get system user_rotation | tr -d '\r') accel_before=$(A shell settings get system accelerometer_rotation | tr -d '\r') rot_before=$(rot)" >> $OUT/marks.txt
A shell svc power stayon true; A shell am force-stop $PKG
S0=H-start-$(date +%H%M%S); cap; mark $S0; launch; sleep 3
[ "$(since $S0 | grep -aF 'START u0' | grep -acF 'MainActivity')" -lt 1 ] && { echo "H NO_START_LINES: CREATE=1" >> $OUT/marks.txt; export CREATE=1; }
# DR-H, then TD-H: each after a CLEAR and setkeys td
for R in DR TD; do
  cleardef H-$R-clear
  if setkeys td $TDKEYS; then usbdef H-$R-pre
    how=; [ $R = DR ] && how=rot
    tdcycle H-$R-p1 $how; rc=$?; [ $rc -eq 2 ] && exit 2; [ $rc -eq 1 ] && { tdcycle H-$R-p2 $how; [ $? -eq 2 ] && exit 2; }
  else echo "H-$R UNTESTABLE settings" >> $OUT/marks.txt; fi
  # put the rotation back after DR-H
  A shell settings put system user_rotation "$(sed -n 's/^H user_rotation_before=\([0-9]*\).*/\1/p' $OUT/marks.txt | head -1)"
  A shell settings put system accelerometer_rotation "$(sed -n 's/.* accel_before=\([0-9]*\).*/\1/p' $OUT/marks.txt | head -1)"
done
# R-H: a first connect after a CLEAR and a force-stop, then a plug that must show no dialog
cleardef H-R-clear
if setkeys s1R $S1KEYS; then usbdef H-R-pre; RULES="$S1RULE"; run5 R "session session"; [ $? -eq 2 ] && exit 2
  for P in $(plugs H-R); do answers $P; done
else echo "H-R UNTESTABLE settings" >> $OUT/marks.txt; fi
# L-H: P-1 on identity 4, then 3 and 1 with the app's own key; then P-1 back to its console
cue "CAB H-L4: D-MOTO stays off D-HP from here"
if [ "${PICO_OK:-0}" = 1 ] && pset 4; then
  if lh; then lhid H-L4i3 3 18d1:2d00 'VID: 18D1'; lhid H-L4i1 1 1314:1520 'VID: 1314'; fi
else echo "H-L4 UNTESTABLE pset" >> $OUT/marks.txt; fi
xprefs set:usb-blacklist= set:allow-devices= bool:auto-connect-single-usb=false; blread H-L4-cleared
pset 0 || echo "H P-1 NOT_ON_CONSOLE" >> $OUT/marks.txt
cleardef H-end-clear; usbdef H-end; mark H-end
echo "H user_rotation_end=$(A shell settings get system user_rotation | tr -d '\r') accel_end=$(A shell settings get system accelerometer_rotation | tr -d '\r') rot_end=$(rot)" >> $OUT/marks.txt
send ACTION_EXPORT_LOG; sleep 5; th_end; uncap; xrestore $OUT/backup-H.xml
A shell settings put global stay_on_while_plugged_in "$(sed -n 's/^H stay_on_before=//p' $OUT/marks.txt | head -1)"
```

## 7. The deciding lines

Every app string prints at INFO, except the `UsbDiagnostics:   ` verdict line of an accepted device (VERBOSE). Each was
checked with `grep -F -r` in `app/src/main` at `ceca2126`. "Composed" marks a line whose parts the source joins at run
time; each part was checked. Cycles are graded in `<ST>-hu.txt` through `timeline.sh` (window `<P>-cue` to
`<P>-unplug`, and for a TD or DR plug also `/pre` `<P>-cue` to `<P>-stop` (DR: to `<P>-rot`), `/rot` `<P>-rot` to
`<P>-stop` (DR only), `/gap` `<P>-restart` to `<P>-answer`, `/post` `<P>-answer` to `<P>-unplug`) and `x1.sh`. L-P and
L-H are graded through `bl.sh` and `p1.sh` in `<L>-cue` to `<L>-unplug`, plus `marks.txt`. Phone lines are graded in
the `<label>-phone.txt` files.

| Id | Line (fixed substring) | Read |
|---|---|---|
| L-RX, L-MK | `AutomationReceiver: `, `AutomationMarker: ` | a verb landed (no line: the step is void); the window bounds |
| L-NORM, L-ACC | `Normal USB device attached: `, `USB accessory device attached, connecting.` | identity A seen; the phone came back as identity B |
| L-DIAG | `UsbDiagnostics:   ` then the device, the verdict (`accepted: ` or `rejected: `) and the interface triples (composed) | **the identity the host saw.** `accepted: MTP` is the MTP mode |
| L-DEC | `UsbLauncher: permission for ` with `identity=`, ` mtp=`, ` listen=`, ` routeSeen=`, ` attach=`, ` source=`, ` action=` (composed; the action is `ask`, `wait`, `asked` or `hold`) | **one decision.** `action=asked` after a restart is the entry that survived |
| L-REQ | `Requesting USB permission for ` (also inside `UsbPermissionAsks: Requesting USB permission for `) | every request of ours |
| L-REF | `UsbLauncher: a dialog for ` ... `is already open; not opening a second` | a claim refused at a path already asked |
| T-ANS | `UsbPermissionAsks: answer for ` path ` (granted=` `true` or `false` `); entry ` `ended` or `kept` (composed) | **the record's own reading of each answer.** A grant must read `ended`; a denial must read `kept` |
| T-GRANT | `USB permission granted for ` | the service acted on a grant |
| T-DENY | `USB permission denied for ` | the service read a denial (DR-H's rebuild) |
| T-HELD | `USB permission: a denial for ` ... `arrived while permission is held; ignoring it` | a late denial after a grant, dropped (information) |
| T-SINGLE | `Single USB auto-connect: device found but no permission` | the restarted service's scan reached the device |
| T-STOP | `Stop action received. Broadcasting finish request to activities.`, `AapService destroying... (` | the stop |
| T-START | `AapService creating...` | the restart |
| T-FGS | `ForegroundServiceStartNotAllowedException/Exception caught in onStartCommand: ` | API 35 refused the restart (NO_RESTART) |
| L-SW | `Switching USB device to accessory mode `, `Switching to accessory mode.`, `Successfully requested switch to accessory mode for `, `UsbAccessoryMode: Performing AOA switch` | a switch started |
| L-SSL, L-MS | `SSL handshake complete`, `Media Sink Setup Request: ` | a session formed; the phone answered |
| L-INT | `USB Intent: ` | with `USB_DEVICE_ATTACHED`: a plug; with `USB_DEVICE_DETACHED`: the unplug |
| G-IGNSVC | `Ignoring USB device attached in service (` then `VID: 0x%04X, PID: 0x%04X` (`Settings.formatUsbVidPidDisplay`), `): `, then the reason, here `rejected: blacklisted by the user` (composed) | **the service refused a blacklisted device**, and for which identity |
| G-IGNACT | `Ignored blacklisted USB device` | the attach screen ignored it |
| L-DIAG0 | `UsbDiagnostics: ` caller ` sees ` N ` USB device(s), ` N ` usable for Android Auto` (composed) | a scan and its caller |
| D-MATCH | `MATCH! Starting AapService` | discard rule (template §4) |

The other strings `timeline.sh`, `bl.sh`, `p1.sh` and `x1.sh` grep are listed in the block at the end; all matched.

UI and resource strings, from `app/src/main/res/values/strings.xml`, read in dumps: `Blacklisted`, `Allowed`,
`Ignored`. Dump attributes: `resource-id`, `text`, `enabled`, `bounds`. Resource ids, from `fragment_home.xml` and
`list_item_device.xml`: `:id/usb_button`, `android:id/button1`. The stored key P-1 must get on D-HP:
`name:magic communication tec. auto box` (`UsbBlacklistPolicy.key`: `name:` plus manufacturer and product, trimmed,
joined by a space, lower case).

System strings, not in `app/src`: `START u0`, `.usb.UsbConfirmActivity`, `.usb.UsbPermissionActivity`,
`.usb.UsbResolverActivity`, `_finish_activity`, `notification_enqueue`, `com.android.mtp`, `Device preferences`,
`device_preferences`, `mRotation=` (`dumpsys window`). Phone strings, on D-MOTO: `entering USB accessory mode` (AOSP),
`GH.CarConnSession` (information only), and the properties `sys.usb.state`, `sys.usb.config`. Sysfs files, on the test
PC and on D-HP: `busnum`, `devnum`, `idVendor`, `idProduct`, `manufacturer`, `product`, `bConfigurationValue`,
`bInterfaceClass`, `bInterfaceSubClass`, `bInterfaceProtocol`, `interface`. P-1's strings: `Magic Communication Tec.`,
`Auto Box` (README). For P-1's identity: `bcdDevice` and `stored identity: 0` (round 5's `pset`). **FAIL lines, every
window:** `FATAL EXCEPTION` in a block that names `com.andrerinas`, `ANR in com.andrerinas`, `NoClassDefFoundError`.

The files the runs write: `cycles.tsv` (round 4's `row`, one row per plug and per TD or DR window), `x1.tsv`,
`phone.tsv`, `bl.tsv`, `p1.tsv`, `defaults.tsv` (as round 5), and `marks.txt` (markers, `plug=`, `unplug=`, `proof`,
`pc dev=`, `PROOF_*`, `ask_by=`, `rot_*`, `rotate_by=`, `pid_before=`, `restart_refused=`, `grants=`, `ans_*`,
`post_true_ended=`, `stored_key=`, `sys_*`, `list*_text`, `after*`, `blacklisted_nodes=`, `blacklist=`, `I pset`, taps,
voids, identity lines).

## 8. Runs

**Stop rule.** A cycle with `NO_PLUG` is retried once with the next label. Two in one run end the run, graded on what
it has (INCONCLUSIVE with 0 graded cycles). A cycle with `NO_SESSION` is graded, never retried. `BATTERY_LOW` ends the
stage, and the runs not reached are UNTESTABLE (battery). A failed `setkeys`, `xprefs` or `pset` makes the run under it
UNTESTABLE (settings or pset); quote the line. A `PROOF_FAIL` makes its run UNTESTABLE (lever or mode).

### Prepare (steps 1 to 6 beside the build, then 12 min)

1. Make `$OUT/scripts/` (section 6). Print the cue path: `. ./lib6.sh; echo "tail -n0 -F $OUT/cues.log"`. Give the
   operator that line exactly.
2. `$RIG_TOOLKIT/rig_preflight.sh D_HP:wifi D_POCO:wifi D_MOTO:wifi`. Paste the table into Setup notes. Charge D-HP
   to 100% and D-POCO to 50% or more.
3. D-MOTO, on its PC cable:
   1. `svc bluetooth disable`, read it back. WiFi stays on.
   2. Record `settings get global stay_on_while_plugged_in` and `settings get system screen_off_timeout`. Then
      `svc power stayon true` and `settings put system screen_off_timeout 1800000`.
   3. Record `getprop sys.usb.state`, `getprop sys.usb.config` and the Developer options row "Default USB
      configuration" (ask the operator once). `logcat -G 16M`. Confirm the lock state with the operator (UNLOCK).
   4. On the test PC run `./ifaces.sh 22b8` and paste its output into Setup notes. It MUST print a `dev` line; if it
      prints nothing, sysfs cannot see D-MOTO and S1-P-N's second proof cannot run: stop and say so.
4. Wireless adb for D-HP, D-POCO and D-MOTO, each still on its PC cable: `adb -s <serial> tcpip 5555`, read
   `ip -4 addr show wlan0`, `adb connect <ip>:5555`. Write `HP_W`, `POCO_W`, `MOTO_W`, and `MOTO_USB=<D-MOTO's cable
   serial>` to `$OUT/scripts/wireless.env`.
5. D-POCO: `am start -a android.intent.action.VIEW -d headunit://exit`, wait 3 s, then `am force-stop` its app
   (template §8 role-swap rule).
6. On D-HP and D-POCO:
   1. Save `dumpsys usb > $OUT/prep-<unit>-usb.txt` and run `./usbparse.sh` on it. Record `defaults=` and
      `serial_outside=`.
   2. From `run-as $PKG cat shared_prefs/settings.xml`, record `usb-blacklist` and `allow-devices`.
   3. On D-POCO record `appops get $PKG SYSTEM_ALERT_WINDOW` (section 3, NO_RESTART).
7. After the build, on each host (`ST=P HU=$POCO_W`, then `ST=H HU=$HP_W`, with `. ./lib6.sh`):
   1. `A shell am force-stop $PKG`, then `A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/backup-$ST.xml`.
   2. `A install -r -d $OUT/apk-C.apk`, `launch`, `sleep 5`, `ident $ST-new $CAND`. `marks.txt` MUST read
      `commit_ceca2126=1` (or more) and an `md5_live` equal to `apk-C.apk`, else stop: UNTESTABLE (build).
   3. `A push xpcp.sh /data/local/tmp/` and `A shell chmod 755 /data/local/tmp/xpcp.sh`.
   4. On D-HP only: `A push sysnames.sh /data/local/tmp/`, `A shell chmod 755 /data/local/tmp/sysnames.sh`, then
      `A shell run-as $PKG sh /data/local/tmp/sysnames.sh`. Paste the output into Setup notes. It MUST print one line
      per USB device with `bus=` and `dev=` (the OTG adapter may be empty, so the root hub alone is enough). If it
      prints an error, `run-as` cannot read sysfs: L-H's key item is then graded only on the stored key, and say so.
8. P-1: in `pico.env` set `PICO=$OUT/scripts/pico` (the copied folder) and check that `console.py` is there. Keep
   `PICO_OK=1` (P-1 round 1, R1 to R4 PASS). `id -nG | grep -cw dialout` MUST print 1, else write `PICO_OK=0`: L-P and
   L-H are then UNTESTABLE (P-1 not set up).
9. `chmod +x usbparse.sh timeline.sh x1.sh bl.sh p1.sh ifaces.sh stageP6.sh stageH6.sh` in `$OUT/scripts/`. Then
   `. ./lib6.sh; echo "$TDKEYS" | grep -c 'auto-connect-single-usb=true'` MUST print 1.
10. The rotation lever on D-HP, with no dialog up (`ST=H HU=$HP_W`, `. ./lib6.sh`):
    1. Record `settings get system user_rotation`, `settings get system accelerometer_rotation` and `rot`.
    2. `A shell settings put system accelerometer_rotation 0`, then `A shell settings put system user_rotation <(rot + 1) % 4>`.
       Wait 3 s and read `rot` again.
    3. Put both settings back to the values of step 1, wait 3 s, and read `rot` a third time.
    4. Record `rot_lever=works` if the second `rot` equals the value asked for, else `rot_lever=no`. With `no`, DR-H's
       `rebuild` reaches the ROTATE hand step by itself; tell the operator once.

Then run `./stageP6.sh`, then `./stageH6.sh`.

Grade each run from `cycles.tsv`, `x1.tsv`, `phone.tsv`, `bl.tsv`, `p1.tsv` and `marks.txt`. "Android's dialogs" are
`confirm_A + confirm_B + confirm_x + resolver`; "ours" are `perm_A + perm_B + perm_x`. The operator's COUNT and
DIALOGS answers go beside the log as a second column; a disagreement is a finding, not a verdict.

### Stage P (D-POCO)

#### S1-P-N, "No data transfer" proved: Android offers the app once and the tick sticks. The point of the round

Setup checks: `P-N-clear-post` reads `defaults=0`; `P-N proof<k> state=[adb]`; `P-N pc dev=1 mtp=0 adb=0`;
`P-N PROOF_OK`. Record both `proof<k>` lines, the `pc ... ifaces=[...]` line and `REPC_NOT_SEEN` if it is there.

Read the mode the host saw from `x1.tsv` row `P-N-p1`: its `diag` and `mtpn`.

- **UNTESTABLE (lever not proven)** if `marks.txt` reads `P-N UNTESTABLE lever`. Quote the `PROOF_FAIL` line.
- **UNTESTABLE (lever not in force at the host)** if the proof passed and plug 1's `diag` reads `accepted: MTP` or
  `mtpn` is 1 or more. Record every item below anyway, and say that the phone showed MTP to D-POCO in a mode that
  showed none to the PC.
- **FAIL (lever)** if plug 1's `diag` reads `rejected: `: in this mode the phone is no Android Auto device to the app.
  This is a result about the advice to the reporter, not a branch defect.

Otherwise S1-P-N PASS needs:

1. Plug 1: `dlg_all` at least 1.
2. Plug 2: `dlg_all=0`, `req_A=0`, `req_B=0`.
3. Every plug: `sw_lines` at least 1, `attach_B` at least 1, `ssl` at least 1, `ms` at least 1, `fatal=0`. Phone
   (`phone.tsv`): `phone_acc` at least 1.
4. Every plug: `mtpn=0`, and `ans_false_ended=0`, `ans_true_kept=0`.
5. The operator's COUNT reads 1 or more, then 0.

S1-P-N FAIL if item 2 or 3 fails on a graded plug, or `ans_true_kept` or `ans_false_ended` is 1 or more. Say which item
failed. S1-P-N INCONCLUSIVE if plug 1 reads `dlg_all=0` (a stored default or grant hid the first offer; quote
`defaults=` and `serial_outside`).

Record per plug: `diag`, `vp`, `route_A`, `route_B`, `confirm_A`, `confirm_B`, `perm_A`, `perm_B`, `dec_A`, `mtp_A`,
`grants`, `ans_true_ended`, `after=`.

#### TD-P, a service restart while our dialog is up, on API 35 with an MTP phone. The point of the round

D-MOTO in File transfer with debugging off, `TDKEYS`. Plug 1 is `tdcycle`: our dialog opens at the plug (an MTP phone
gets no dialog from Android on API 35), the service stops at 45 s and starts again with no user flag, and only then
does the operator answer. Plug 2 is a session plug.

Setup checks: `P-TD proof<k> state=[...mtp...]`, `P-TD pc dev=1 mtp=1 adb=0` (or more `mtp`), `P-TD PROOF_OK`, and
`P-TD-clear-post` `defaults=0`.

TD-P PASS needs, on plug 1 (`P-TD-p1`; `P-TD-p2` if p1 was `NO_PLUG`):

1. `/pre`: `dec_A` holds `ask`, `mtp_A` holds `yes`, `req_A` at least 1, `perm_A` at least 1. `marks.txt` reads
   `ask_by=plug`.
2. `marks.txt`: `pid_before` equals `pid_after`, and none of `NO_DESTROY`, `NO_RESTART`, `NO_DEC` is there.
   `noperm` at least 1.
3. `/gap`: `dec_A` holds only `asked`, `req_A=0`, `req_B=0`, `perm_A=0`, `perm_x=0`, `dlg_all=0`; `gap_ans=0`. The
   DIALOGS answer is 1.
4. `/post`: `post_true_ended` at least 1, `grants` at least 1, `sw_lines` at least 1, `attach_B` at least 1, `ssl` at
   least 1, `ms` at least 1. Phone: `phone_acc` at least 1.
5. Plug 2 (`P-TD-p3`): `dlg_all=0`, `req_A=0`, `req_B=0`, `ssl` at least 1, `ms` at least 1, `phone_acc` at least 1.
6. `fatal=0` in every window, and `ans_true_kept=0`.

TD-P FAIL if item 3 fails (a second dialog or request after the restart, or `action=ask` there), or if item 4 fails
after the operator pressed OK (no `granted=true); entry ended` line, or a grant with no session), or if item 5 fails.
TD-P INCONCLUSIVE if item 1 fails (no dialog of ours to test: quote `defaults=` and `x1.tsv`'s `diag`), or if item 2
fails (a changed pid means the record died with the process; `NO_RESTART` with `restart_refused` at 1 or more is the
API 35 background limit; `NO_DEC` with `noperm=0` means the scan never reached the device).

**What the code before `0bb61637` would do here:** `/gap` would read `dec_A=ask`, `req_A=1`, `perm_A=1`, and DIALOGS
would be 2. Item 3 is the number that separates the two builds.

Also record S1-P-F's fields from plug 1 (round 5 left them between PASS and INCONCLUSIVE): `diag`, `mtp_A` (expected
`yes`), `route_A`, `mtpn`, `dec_A`, `ssl`.

#### L-P, a blacklisted accessory-mode device in the USB list (P-1 identity 3)

`vidpid:18d1:2d00` written by `xprefs` (`P-L3 stored_key=1`). One plug of P-1 on identity 3 (`I pset 3 ... bcd=1.03`).
Two injected taps: the USB button, then the row button that reads "Blacklisted".

L-P PASS needs:

1. `bl.tsv` row `P-L3`: `ign_svc` at least 1, and `p1.tsv`'s `ign_why` names `VID: 0x18D1, PID: 0x2D00): rejected:
   blacklisted by the user`.
2. `marks.txt`: `tap 1 rid=:id/usb_button`, `list0_text=Blacklisted` (any case), and `list0_state` two numbers (the
   button is enabled).
3. `tap 2 rid=android:id/button1`, then `P-L3 after key=0 set=[]`.
4. `list1_text=Allowed` (any case) and `list1_state=DISABLED` (no key, accessory mode).
5. `ours=0`, `req=0`, `fatal=0` in the row.

L-P FAIL if `list0_text=Allowed` with `list0_state=DISABLED` (the old bind order; `NO_BLACKLISTED_ROW` follows), or
`after key=1` after the tap, or `list1_text=Blacklisted`. L-P INCONCLUSIVE if `TAP_VOID` on the USB button,
`list0_text` reads `MATCHES=0` (no list), `NO_PLUG2`, or `ign_svc=0` (the key was not in force: quote `mirror=`).
Record `mirror=` (`unreadable` is a rig fact, not a verdict), `android`, `ign_act`, `acc`, `found_acc`, and the COUNT.

### Stage H (D-HP)

Setup check: the `H user_rotation_before=` line, and Prepare 10's `rot_lever=`.

#### DR-H, a rebuilt dialog's false denial keeps the entry, on API 17. The point of the round

D-MOTO with debugging on, `TDKEYS`, after a CLEAR. As TD-H, with one step more: while our dialog is up, `rebuild`
turns the screen a quarter turn. The old dialog answers "denied" and a new one opens with the same request. Then the
service stops at 45 s and starts again with no user flag, and only then does the operator answer.

DR-H PASS needs, on plug 1 (`H-DR-p1`; `H-DR-p2` if p1 was `NO_PLUG`):

1. `/pre`: `req_A` at least 1 and `perm_A` at least 1. `ask_by` is `plug` (then `dec_A` holds `ask`) or `verb` (then
   `src_A` holds `user`).
2. The rebuild: `rotate_by` is `settings` or `hand`; `rot_false_kept` at least 1, `rot_false_ended=0`; `rot_denied`
   at least 1; `/rot` `req_A=0`.
3. As TD-P item 2.
4. `/gap`: `dec_A` holds only `asked`, `req_A=0`, `req_B=0`, `perm_A=0`, `perm_x=0`, `dlg_all=0`. The DIALOGS answer is
   1 (the rebuilt dialog).
5. `/post`: `post_true_ended` at least 1, `grants` at least 1, `sw_lines` at least 1, `attach_B` at least 1, `ssl` at
   least 1, `ms` at least 1. Phone: `phone_acc` at least 1 in `cycles.tsv`.
6. `fatal=0` in every window, and `ans_false_ended=0` for the whole plug.

DR-H FAIL if item 2 reads `rot_false_ended` at 1 or more (a false denial ended the entry), or item 4 fails (a second
request or dialog after the restart), or item 5 fails after the operator pressed OK (the rebuilt dialog's grant did not
end the entry, or did not connect). DR-H INCONCLUSIVE if item 1 fails (`NO_ASK`), if `NO_REBUILD` (no lever rebuilt
the dialog: quote `rot_before`, `rot_want`, `rot_after`), or if item 3 fails. Record `confirm_A` in `/pre`, `rs`,
`held_denial`, the DIALOGS answer and the COUNT.

**What the first form of this fix would do:** there, any answer ended the entry. The false denial would then end it,
and `/gap` would read `dec_A=ask`, `req_A=1`, with DIALOGS 2. Items 2 and 4 separate the two forms.

#### TD-H, a service restart while our dialog is up, on API 17. The point of the round

D-MOTO with debugging on, `TDKEYS`, after a CLEAR. Android shows its "Open" dialog at the plug, and the operator
cancels it. Our dialog opens at the plug (the first plug after a force-stop has no route seen) or from the user's ask
at 40 s.

TD-H PASS needs, on plug 1 (`H-TD-p1`; `H-TD-p2` if p1 was `NO_PLUG`):

1. As DR-H item 1.
2. As TD-P item 2.
3. As TD-P item 3.
4. As DR-H item 5.
5. `fatal=0` in every window, and `ans_true_kept=0`.

TD-H FAIL and INCONCLUSIVE as TD-P (items 3 and 4 FAIL; items 1 and 2 INCONCLUSIVE, `NO_ASK` included). Record
`confirm_A` in `/pre` (Android's dialog, cancelled), `rs`, `ask_by` and the DIALOGS answer. The old code would read as
in TD-P: the attach is older than 30 s at the restart, so it would ask again.

#### R-H, a first connect and a ticked plug on API 17

After a CLEAR and `setkeys s1R`. Two session plugs. Rule: tick every box, then OK.

R-H PASS needs: plug 1 (`H-R-p1`) `dlg_all` at least 1; plug 2 `dlg_all=0`, `req_A=0`, `req_B=0`; every plug
`sw_lines` at least 1, `attach_B` at least 1, `ssl` at least 1, `ms` at least 1, `fatal=0`, `phone_acc` at least 1,
`ans_true_kept=0`, `ans_false_ended=0`; the COUNT 1 or more, then 0. R-H FAIL if plug 2 shows a dialog or a request,
or a plug has no session. R-H INCONCLUSIVE if plug 1 reads `dlg_all=0`. Record `confirm_A`, `confirm_B`, `perm_A`,
`perm_B`, `req_A`, `req_B`, `grants`, `ans_true_ended` and `held_denial` per plug: round 5 read 1, 1, 1, 1 for the
first four on plug 1.

#### L-H, a named key below API 21 from the USB list, and one key for every P-1 identity (P-1 identities 4, 3, 1)

`vidpid:1314:1521` written by `xprefs` (`H-L4 stored_key=1`), so the list opens. One plug of P-1 on identity 4
(`I pset 4 ... bcd=1.04`). The sysfs read as the app's uid, then LIST and three TAPROW presses (Blacklisted to Ignored,
Ignored to Allowed, Allowed to Blacklisted). Then, with the app's own key kept, one plug each of identity 3
(`H-L4i3`, `bcd=1.03`) and identity 1 (`H-L4i1`, `bcd=1.01`), each with a sysfs read and a user scan. P-1 has names, so
the one-mode toast cannot show in this run (`UsbBlacklistPolicy.bindsOneMode` is false for a `name:` key): the toast
is not tested this round.

L-H PASS needs:

1. The sysfs read: `H-L4 sys_p1=1`, and its `sys_line` names `id=1314:1521`, `[Magic Communication Tec.] [Auto Box]`
   and a `bus=` and `dev=` value. Quote the line.
2. `bl.tsv` row `H-L4/blk` (plug to the first press): `ign_svc` at least 1, `ours=0`, `req=0`, `sw_lines=0`.
3. `H-L4 list0 blacklisted_nodes=1`.
4. `after_1 old=0 name=0`; `after_2 old=0 allowed=1`; `after_3 name=1 vidpid_keys=0 allowed=0`; no `POLL_TIMEOUT`.
5. `H-L4-final blacklist=[name:magic communication tec. auto box]` and `list3 blacklisted_nodes=1`.
6. Identity 3 (`H-L4i3`): `p1.tsv`'s `ign_why` names `VID: 0x18D1, PID: 0x2D00): rejected: blacklisted by the user`;
   `acc=0`, `found_acc=0`, `ours=0`, `req=0`, `sw_lines=0`; `sys_p1=1`.
7. Identity 1 (`H-L4i1`): `ign_why` names `VID: 0x1314, PID: 0x1520): rejected: blacklisted by the user` (round 5 read
   `rejected: no Android interface` for it with no key); `ours=0`, `req=0`, `sw_lines=0`; `sys_p1=1`.
8. `fatal=0` in every `H-L4*` row of `bl.tsv`.

L-H FAIL if item 4 or 5 fails (the app wrote `vidpid:1314:1521` or another key), or item 6 or 7 shows another reason,
a switch, a request or a session. L-H INCONCLUSIVE if `blacklisted_nodes=0` (no list opened), a `POLL_TIMEOUT` with the
operator saying the button did not change, `NO_PLUG2`, or a failed `pset` (items 6 and 7 only). If item 1 fails but
item 5 passes, the app read the names some other way: record it, and grade on item 5. Record `android`, `ign_act` and
the COUNT for each plug, and every `sys_line`.

#### Clean-up, the end of stage H

Quote `P-L3-cleared` and `H-L4-cleared` `blacklist=[]`, `I pset 0 stored0=1` (P-1 back on its console), `P restored`
and `H restored`, and the `H user_rotation_end=` line, which MUST equal the `before` values. Restore on D-MOTO: the
default USB mode recorded in Prepare 3.3 (`svc usb setScreenUnlockedFunctions none` if it read "No data transfer", or
have the operator set it), `screen_off_timeout`, `stay_on_while_plugged_in`, and Bluetooth as found. Record
`serial_outside` on D-POCO after the round (`usbdef P-end`).

If `stageH6.sh` stopped before its end, run from `$OUT/scripts/`:

```bash
ST=H; . ./lib6.sh; . ./wireless.env; . ./pico.env; . ./pico.sh; HU=$HP_W
xprefs set:usb-blacklist= set:allow-devices= bool:auto-connect-single-usb=false; blread H-after
pset 0; xrestore $OUT/backup-H.xml
```

Then put `stay_on_while_plugged_in`, `user_rotation` and `accelerometer_rotation` back from the `H ... before=` lines,
and leave P-1 unplugged.

## 9. Do not re-run

| Run | Round | Verdict |
|---|---|---|
| R4a-c, R5a-c, R5t, F4a-c | 2, 3 | PASS: dialogs on a first connect, the verb asks as the user, no raise over Android's dialog |
| B1 to B10n, L1b | 4 | PASS: "Connect when plugged in", the settings hold, detection off, the boot start |
| S1-H-N, S1-H-F | 5 | PASS: API 17 offers the app once and the tick sticks, in both modes |
| S2-M-bl, S2-M-re, S2-D-ctl, S2-D-bl, BOOT, B8, ORD | 5 | PASS: the blacklist on every path on API 17, the cold start, the row order |
| S2-P1-M3, S2-P1-I1 to I5, I3bl, I4bl, I4rp | 5 | PASS: the stock CCPA, the nameless device, the AOA identities, the cost of `vidpid:18d1:2d00` |
| S1-P-F | 5 | INCONCLUSIVE on a brief gap only; its fields come from TD-P plug 1 |
| S2-D-re | 5 | INCONCLUSIVE (wrong device); S2-M-re covers the same path |

Round 5's S2 runs used `vidpid:` keys written by `xprefs`. They still hold for that form, which the app still matches.
L-H covers the key the app writes now.

## 10. Report back

1. **S1-P-N:** both `proof` lines, the `pc ... ifaces=[...]` line, plug 1's `diag` and `mtpn`, and per plug
   `dlg_all` beside the COUNT, `req_A`, `req_B`, `ssl`, `phone_acc`. Did "No data transfer" hold at the host, and did
   it give one offer that sticks?
2. **TD-P, TD-H and DR-H:** per window (`/pre`, `/rot`, `/gap`, `/post`) `dec_A`, `req_A`, `perm_A`, `dlg_all`;
   `ask_by`, `rotate_by`, `rot_false_kept`, `rot_false_ended`, `pid_before`, `pid_after`, `restart_refused`, `noperm`,
   the DIALOGS answer, `post_true_ended`, `grants`, `ssl`, `ms`, `phone_acc`; and TD-P plug 2's `dlg_all` and `req`.
   Did a restart or a rebuild open a second dialog, and did the answer still connect?
3. **The USB list:** L-P's `list0_text`, `list0_state`, `after key=`, `set=`, `mirror=`, `list1_text`, `list1_state`;
   L-H's `sys_line` for each identity, `after_1` to `after_3`, `final blacklist=`, and the `ign_why` of `H-L4i3` and
   `H-L4i1`.

Also: R-H's per-plug counts, every void and its reason, every `TAP_VOID`, every `REPC_NOT_SEEN`, `DEBUG-OFF`,
`PCMOTO`, `ROTATE` or `RESEAT` cue that fired, Prepare 10's `rot_lever`, the cable that joined P-1 to D-POCO, every
`I pset` line and HOLD cue, and `serial_outside` on D-POCO before and after.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
ACTION_CHECK_USB
ACTION_STOP_SERVICE
ACTION_REFRESH_SENSORS
Normal USB device attached: 
USB accessory device attached, connecting.
UsbDiagnostics:   
UsbDiagnostics: 
 sees 
 USB device(s), 
 usable for Android Auto
UsbDiagnostics: everything on the bus was refused
accepted: 
rejected: 
rejected: blacklisted by the user
already in accessory mode
Ignoring USB device attached in service (
VID: 0x%04X, PID: 0x%04X
Ignored blacklisted USB device
UsbAttachedActivity: Ignored blacklisted USB device (
Ignoring non-Android USB device in onCreate (VID: 
HomeFragment: Requesting single-USB auto-connect via AapService
single-USB auto-connect found 
USB button: Single device found - 
Switching USB device to accessory mode 
Switching to accessory mode.
Successfully requested switch to accessory mode for 
UsbAccessoryMode: Performing AOA switch
UsbAccessoryMode: AOA switch failed: 
Acc start sent (len=
Handshake: Version exchange timed out after 
Found known USB device with permission: 
Fallback: force=true and found single normal-mode Android device 
Found device already in accessory mode: 
UsbLauncher: permission for 
identity=
 mtp=
 listen=
 routeSeen=
 attach=
 source=
 action=
Requesting USB permission for 
UsbPermissionAsks: Requesting USB permission for 
UsbLauncher: a dialog for 
is already open; not opening a second
UsbPermissionAsks: a dialog for 
UsbPermissionAsks: answer for 
 (granted=
); entry 
USB permission granted for 
USB permission denied for 
USB permission: a denial for 
arrived while permission is held; ignoring it
Single USB auto-connect: device found but no permission
Stop action received. Broadcasting finish request to activities.
AapService destroying... (
AapService creating...
ForegroundServiceStartNotAllowedException/Exception caught in onStartCommand: 
USB Intent: 
UsbAttachedActivity: Connect when plugged in is off; opening the app, not switching 
UsbLauncher: not switching 
 on a plug-in: 
Reopen on reconnection: launching MainActivity (
Reopen on reconnection: not raised over Android's USB dialog (
USB auto-start: launching app for 
UsbLauncher: USB auto-connect held while the settings screen is open
UsbAttachedActivity: settings on show or the status pill
checking USB now (trigger=
WakeDetect: SCREEN_OFF
SSL handshake complete
Media Sink Setup Request: 
MATCH! Starting AapService
```
