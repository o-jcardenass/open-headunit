# usb-dialog, round 7 brief: the first plug left to Android's dialog, a service restart while our dialog is up on API 35, and the blacklisted accessory row in the USB list

Published name: `usb-dialog-round7-brief.md`. Results go in `usb-dialog-round7-results.md`.
Evidence: upload `usb-dialog-round7-captures.zip` to the existing release `rig-evidence-usb-dialog`.

Units: D-POCO (POCO X3 NFC, API 35) and D-HP (HP Slate 7 Plus, API 17) as USB hosts, one at a time. D-MOTO (Motorola
edge 30 neo, API 34) is the wired phone. P-1 (the rig's RP2040 USB stand-in) is the list device on D-POCO. D-HU, D-SAM
and D-DONGLE take no part.

Queue line: `usb-dialog-round7-brief.md` (one arm, `fork/fix/usb-permission-dialog` `9ae9bef2`; units D-POCO, D-HP,
D-MOTO, P-1; about 2 h with the build; F-P, F-H and TD-P are the point).

Pairing: possible only with a round that uses D-HU alone and has no hand steps during this one. If this round is the
second of a pair, it takes the lock `/tmp/ohu-rig-usbdlg.lock`.

Hand steps: the Default USB configuration read aloud once, set three times and restored once, cable moves, one plug
and one unplug per cycle, the USB dialogs by the rule of section 5, Clear defaults when cued, one COUNT per plug, one
DIALOGS answer, four PC-cable replugs of D-MOTO, USB debugging on D-MOTO back on once, "USB controlled by" on D-MOTO
at each plug, and P-1 moved between the test PC and D-POCO three times. Injected taps: 2 in L-P (3 at most), each on a
dumped target found by its text. F-P and F-H use no tap: the user's ask is a verb.

**Time budget.** Build 20 min, with Prepare steps 1 to 6 beside it. Then:

| Part | Host | Runs, in this order, in one capture | Min |
|---|---|---|---|
| Prepare 7 to 13 | both | install and identity over USB on both hosts, backups, the restart lever probe, wireless adb | 24 |
| Stage P | D-POCO | TD-P (2 plugs), F-P (3 plugs), L-P (P-1 identity 3), P-1 back to its console | 48 |
| Stage H | D-HP | F-H (3 plugs) | 18 |
| Cable moves, thermal gates | | | 12 |

**About 2 h** with the build. Write the results file after every run.

**F-P, F-H and TD-P are the point of the round.**

- F-P and F-H test the new first-plug rule: when Android shows its own "Open" dialog for a plug this process saw, the
  app waits for it as long as the device stays plugged in, and asks only when the user asks. Rounds 5 and 6 read four
  dialogs on a first connect (R-H plug 1, `dlg_all=4`). F-P is on Android 15, the version the user asks about.
- TD-P tests a service restart while our dialog is up on API 35. Round 6 passed it on API 17 (TD-H, DR-H). On API 35
  the restart was refused, so nothing tested whether the record survives a restart there.

## 1. Build and baseline

One arm, no baseline build. Rounds 1 to 6 give every "before" number.

| Ref | Commit | Tree |
|---|---|---|
| `fork/fix/usb-permission-dialog` | `9ae9bef2b050622be278c46093ded1dbae2fad24` | `563e0289dbcfbcf44a650021d9426d9e3a9e4d1e` |

The branch is 7 commits on `main` `2018d8f09`: round 6's six (`99c0f382`, `143c4593`, `a69d2335`, `4a603930`,
`0bb61637`, `ceca2126`), unchanged, plus `9ae9bef2`, the first-plug fix. History was not rewritten. The dev host suite
at this commit: **4356 JVM tests, 0 failures**. Do not run the unit tests on the rig. No other queued round shares this
base. **Do not reuse round 6's APK**: the code changed. No md5 is known for this build. Record the md5 of the rig's
own `apk-C.apk`; identity is the commit in `ACTION_QUERY_STATE` and the live APK's md5 against that file.

**The build waits for the push.** Start only when `git ls-remote fork fix/usb-permission-dialog` prints
`9ae9bef2b050622be278c46093ded1dbae2fad24`. If it prints another SHA, stop and ask: UNTESTABLE (build) until then.

**`get_apk.sh`**
```bash
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round7; mkdir -p $OUT
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh usb-dialog candidate main | tail -1)}   # record the path in Setup notes
git -C $WT fetch fork fix/usb-permission-dialog
git ls-remote fork fix/usb-permission-dialog         # MUST print 9ae9bef2b050622be278c46093ded1dbae2fad24, else stop
git -C $WT checkout --detach 9ae9bef2b050622be278c46093ded1dbae2fad24
git -C $WT rev-parse 'HEAD^{tree}'                   # MUST print 563e0289dbcfbcf44a650021d9426d9e3a9e4d1e
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
cp rig-data/apks/*.apk $OUT/apk-C.apk               # at once: build_hur.sh deletes the previous APK. Run from StudioProjects
md5sum $OUT/apk-C.apk                                # record it in Setup notes: no md5 is known for this build
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF 'ohu-usb-ask'                              # MUST be 1 or more (0bb61637)
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF 'UsbSysfsNames'                            # MUST be 1 or more (ceca2126)
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF 'Android starts this app for a USB attach' # MUST be 0 (9ae9bef2 removed it)
```

**Identity on each host, after the install:** `send ACTION_QUERY_STATE` carries `9ae9bef2`, and the md5 of the pulled
live APK matches `apk-C.apk`. Install with `adb install -r -d` **over each host's USB cable**, before any cable moves
(quirks commit `4a448b657`). Never uninstall.

## 2. What this is and why

Rounds 1 to 6 fixed the "dialog on every connect" fault, added "Connect when plugged in", proved the blacklist on API
17, and proved on API 17 that a service restart or a rebuilt dialog opens no second dialog (round 6, TD-H and DR-H).

**The mechanism under test, commit `0bb61637`.** `UsbPermissionAsks` keeps one entry per bus path while our dialog is
up. Each ask carries a token in its PendingIntent data (`ohu-usb-ask:<token>`). The manifest receiver
`UsbPermissionResultReceiver` (not exported) hands each answer to the record, which logs
`UsbPermissionAsks: answer for <path> (granted=<bool>); entry ended|kept`. Only a grant with the entry's own token
ends it. The service no longer clears the record when it stops. So after a restart, the next automatic check must read
`action=asked` and open no second dialog, and the user's later OK must still connect.

**Round 6 could not test it on API 35.** `ACTION_REFRESH_SENSORS` reaches the service through the automation
receiver, which calls `ContextCompat.startForegroundService` from the background
(`AutomationEffectRunner.startService`). Android 12 and later refuse that: the broadcast returned `result=1` with
`startForegroundService() not allowed due to mAllowStartForeground false`, and the pid stayed `21431`. The app's own
receiver logs that as `AutomationReceiver: <action> failed: <message>`. The service's own line never prints, because
the service never starts. Round 6's `restart_refused` grepped only the service's line, so it read 0.

**The USB list, commit `ceca2126`.** `UsbListFragment` now tests the blacklist before accessory mode, so a blacklisted
`18D1:2D00` row reads "Blacklisted" with the button on. Round 6 proved that on API 17 (L-H). On D-POCO the tap matched
nothing: the dump carries empty `resource-id` attributes, and the round tapped by id.

**The first-plug fix, commit `9ae9bef2`, "USB: leave a plug to Android's dialog while it stays plugged in".** On the
first plug of a boot, or after a CLEAR, the app stacked its "Allow" dialog on Android's "Open" dialog (R-H plug 1 read
`dlg_all=4` in rounds 5 and 6). The old rule waited only while the attach was under 30 s old and a route was seen in
this boot. Now `UsbPermissionAskPolicy` answers WAIT for an automatic scan whenever Android shows its own dialog
(`androidShowsDialog`) and this process saw the attach (`attachAgeMs != null`), for as long as the device stays
plugged in. A scan for a plug this process never saw (a boot, an app start after the plug) still asks. A user ask
(`source=user`: the USB button, `ACTION_CHECK_USB`) always asks. The decision line no longer carries ` routeSeen=`,
and the line `UsbPermissionAsks: Android starts this app for a USB attach in this boot (recorded)` and the
`usb-attach-route-boot` key are gone.

**What the fix means for TD-P.** Plug 1 is an MTP phone on API 35. Android shows a notification for it, not a dialog
(`androidShowsDialog` is false), so our ask at the plug is still expected. After the switch, the accessory identity
gets Android's "Open" dialog and not ours: the operator ticks and OKs it, `req_B` stays 0, and plug 2 then shows no
dialog.

## 3. What is different about this round

- **F-P and F-H are new.** Each runs after a CLEAR, with D-MOTO in "No data transfer" and USB debugging **on**,
  proved as round 6 did (`sys.usb.state=adb`, and the test PC sees only `ff/42/01 ADB Interface`). That phone shows no
  MTP interface, so Android shows its "Open" dialog on both hosts. Plug A answers Android's dialogs only. Plug B is
  ticked. Plug C follows a second CLEAR: the operator leaves Android's dialog for 45 s, cancels it, waits 30 s more,
  and then the user's ask comes as `send ACTION_CHECK_USB`.
- **The user's ask is a verb.** `AutomationCommandPolicy.checkUsb()` sends `AapService.ACTION_CHECK_USB` with
  `EXTRA_USER_REQUESTED=true`; `checkOnRequest` then scans at `Tier.USER`, and `sourceFor` makes that `source=user`.
  This is the same source as the home button's `UsbPermissionAsks.askFromUi`. So F-H needs no tap on D-HP.
- **Android 15 may show no "Open" dialog for a non-MTP phone.** Round 6 TD-P plug 3 saw one on D-POCO for
  `18D1:2D00`, the accessory identity. Nothing has measured the normal identity there with debugging on. If plug A of
  F-P reads `confirm_A=0`, that is the finding the user wants: record it, and F-P is INCONCLUSIVE.
- **A CLEAR removes stored defaults, not grants.** Template §7b: only an uninstall removes a permission grant, and this
  round does not uninstall. Each F run saves `dumpsys usb` after its CLEAR and records `pkg_lines`. A grant that hides
  our ask makes the plug INCONCLUSIVE, not FAIL.
- **Round 4's `rs` field now reads `-`.** `timeline.sh` reads `routeSeen=` from the decision line, and the line no
  longer carries it. No run grades `rs`.
- **S1-P-N is not re-run** (section 9).
- **The restart lever is probed before TD-P.** Prepare 10 runs `probe7.sh` on D-POCO with no dialog up. It repeats
  round 6's refused restart as a control, then tries two levers in order and keeps the first that logs
  `AapService creating...` with the same pid:
  - **a.** `cmd deviceidle tempwhitelist -d 120000 com.andrerinas.headunitrevived`, then the same broadcast. The
    temporary allowlist is a documented exemption from the background start limit.
  - **b.** A start from the shell: `am start-foreground-service -n
    com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService -a
    com.andrerinas.openheadunit.aap.action.REFRESH_SENSORS`. The manifest declares `AapService`
    `android:exported="false"`, so Android may refuse a shell caller. The probe records the text either way.
  Both levers are unverified on this unit. If neither works, `lever.env` reads `LEVER=none` and TD-P is UNTESTABLE
  (no restart lever on API 35).
- **The stop step is round 6's, unchanged:** `send ACTION_STOP_SERVICE`, then a capped wait on
  `AapService destroying... (`. Round 6 stopped the service on D-POCO with it.
- **TD-P grades the restart gap on what matters.** A `(granted=false); entry kept` line in the gap is allowed (round 6
  TD-H logged one 1 s after the restart, with no second dialog). The gap FAILs on `gap_false_ended` at 1 or more, or
  on a second request or dialog. `gap_ans=0` is no longer a condition.
- **`restart_refused` reads three sources:** the service's line, the receiver's ` failed: ` line, and the text that
  `am` prints for the restart command (`$P-restart-am.txt`).
- **TD-P plug 2 is graded only after plug 1 switched.** In round 6 plug 1 never switched, so plug 3 met Android's
  first "Open" dialog for the accessory identity `18D1:2D00`, which no tick covered (`defaults` 1 to 2). When plug 1
  reaches a session and the operator ticks the accessory dialog, plug 2 must show no dialog.
- **Every plug is detected three ways.** Round 6 marked `NO_PLUG` on a plug that went straight through
  `UsbAttachedActivity`, which prints no `Normal USB device attached: `. The plug pattern now also takes
  `USB Intent: ` with `USB_DEVICE_ATTACHED` (`UsbReceiver` and `UsbAttachedActivity` both log it) and
  `Ignoring USB device attached in service (`. So every plug gets a `plug=` line, and `phslice.py` can cut the phone
  capture for both TD-P plugs (round 6 plug 3 read `phone=NO_TIMES`).
- **Grade `phone_acc` from `phone.tsv`, not from `cycles.tsv`.** With debugging off, D-MOTO's buffer is dumped once at
  the end of the arm, so `cycles.tsv` has no phone file yet when it writes its row.
- **L-P taps by text.** The USB list opens from the home card's button, text `Connect over USB`
  (`home_action_connect_usb`, set by `HomeUiHelper.actionRes`; its click runs `onUsbClicked`). If that text is not
  one node, the fallback is the USB tile's label `USB` (`@string/usb` in `fragment_home.xml`), only when it is exactly
  one node. The row's state button is found by its text: `Blacklisted`, `Allowed` or `Ignored`.
- **Helpers fixed from round 6:** `usbup` and `usbgone` end with `return 0`; `batok` reads the battery up to 3 times
  and logs `BATTERY_UNREAD` instead of stopping on an empty read; `cue` adds a blank line after each cue.
- **D-MOTO's Default USB configuration is recorded first.** Prepare 3 asks the operator to read it aloud. TD-P needs
  **File transfer**; F-P and F-H need **No data transfer**. The end of stage H puts the recorded value back.
- **"USB controlled by" on D-MOTO is a hand step.** `adb shell cmd usb` answers `No shell command implementation.` on
  D-MOTO (API 34).
- **The log level is VERBOSE (`log-level=0`).** Thermal: `rig-quirks/topics/tooling.md` limits, through `th_gate`.
- **D-HP:** its `uiautomator dump` has no `resource-id`, its USB connector misses plugs (a retry cures it), and its
  wireless adb is too slow for an install. Install over its cable first.
- Read `rig-quirks/topics/tooling.md`, `units/D-POCO.md`, `units/D-MOTO.md`, `units/D-HP.md`,
  `tools/pico-usb-stand-in/README.md` and template §7b before the first run.

## 4. Settings keys

Neither host is rooted. Write with the app stopped. `setkeys` (round 4) and `xprefs` (round 5) do it and read every
key back. Back up `settings.xml` on each host in Prepare, and restore it at the end of the host's stage with
`xrestore`.

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

| Writer and tag | `auto-connect-single-usb` | `usb-blacklist` | Runs |
|---|---|---|---|
| `setkeys td` (`TDKEYS`) | `true` | empty | TD-P |
| `setkeys f` (`S1KEYS`) | `false` | empty | F-P, F-H |
| `xprefs` (lp) | `false` | `vidpid:18d1:2d00` | L-P |
| `xprefs` (unbl) | `false` | empty | after L-P |

`TDKEYS` is `S1KEYS` with `auto-connect-single-usb=true`. That key makes the restarted service's own scan reach the
device (`Single USB auto-connect: device found but no permission`). `kill-on-disconnect=false` keeps the process alive
through the stop.

## 5. Cabling, hand steps and the answer rules

| Part | On the PC | On a host's port | Wireless adb |
|---|---|---|---|
| Prepare | D-POCO, D-HP, D-MOTO | | |
| TD-P | D-HP; D-MOTO between plugs | D-MOTO on D-POCO, at each PLUG cue | D-POCO |
| F-P | D-HP; D-MOTO between plugs | D-MOTO on D-POCO, at each PLUG cue | D-POCO, D-MOTO |
| L-P | D-HP, D-MOTO; P-1 at each PCPICO cue | P-1 on D-POCO, at its PLUG cue | D-POCO, D-MOTO |
| F-H | D-POCO; D-MOTO between plugs | D-MOTO on D-HP's OTG adapter, at each PLUG cue | D-HP, D-MOTO |

At most three test-PC ports are in use. P-1 is never on the test PC and on a host at the same time.

| Id | Hand step | When | Why there is no verb |
|---|---|---|---|
| DEFREAD | On D-MOTO open Developer options > Default USB configuration. Read the selected value aloud. Change nothing | Prepare 3, once | the setting has no shell read-back |
| DEFUSB | On D-MOTO choose Developer options > Default USB configuration > the value the cue names (**File transfer** in Prepare 3, **No data transfer** in F-P and F-H), even if it reads so already | Prepare 3, F-P, F-H | the same |
| RESTOREUSB | On D-MOTO choose Developer options > Default USB configuration > the value from DEFREAD | the end of stage H, once | the same |
| CAB | Move the cables as the table says | each part's start | hardware |
| PLUG / UNPLUG | Plug or unplug D-MOTO or P-1 at the host | each cue | hardware |
| UNLOCK | Before every plug of D-MOTO, check that its screen is on and unlocked | every PLUG cue for D-MOTO | D-MOTO can sit behind a PIN |
| CTRL | On D-MOTO, if the USB notification shows "USB controlled by", set it to "This device" | after every plug of D-MOTO | `cmd usb` is not on API 34 |
| DLG | Answer the USB dialogs by the run's rule below | during each cycle | a SystemUI dialog |
| CLEAR | On the App info screen the script opens, press Open by default, then Clear default preferences | each CLEAR cue | Settings is another app |
| COUNT | For **this plug only**, say how many USB dialogs you saw since its PLUG cue, and the first word of each | after every unplug | the second column of the dialog count |
| DIALOGS | Say how many "Allow" dialogs are on D-POCO's screen now. Touch nothing | TD-P plug 1, once | what the screen shows |
| ANSWER | On the "Allow" dialog tick the box if it has one, then press OK | TD-P plug 1, once | a SystemUI dialog, answered after the restart |
| REPC | Keep D-MOTO unlocked. Unplug its PC cable, count to three, plug it back into the PC | the TD-P, F-P and F-H proofs | a fresh connection applies the mode |
| CANCEL | On Android's "Open" dialog press Cancel. Then touch nothing | F-P and F-H plug C, 45 s after the plug | the old rule would ask at 30 s; this is its test |
| ALLOW | On our "Allow" dialog press OK, tick nothing. Then, on the "Open" dialog that follows, tick the box and press OK | F-P and F-H plug C, after the user's ask | a SystemUI dialog |
| DEBUG-OFF | On D-MOTO turn USB debugging off (Settings > System > Developer options) | only if the shell command left adb up | the shell route is tried first |
| DEBUG-ON | On D-MOTO turn USB debugging back on, same screen | the end of TD-P | with debugging off, no adb reaches D-MOTO |
| PCMOTO | Plug D-MOTO into its PC cable, then unplug it when cued | only if D-MOTO's wireless adb does not come back | the phone dump needs a link |
| PCPICO / UNPICO | Plug P-1 into a free test-PC port, or unplug it from there | each cue | hardware |
| HOLD | Hold KEY on P-1 while you count to four slowly, then let go | only when P-1 is not on its console | the console is gone while P-1 presents an identity |

**The executor records every spoken answer** with `said <LABEL> "<what the operator said>"` (`lib7.sh`), at once.
Labels: `MOTO-defusb-before`, `MOTO-defusb-set`, `<P>-ctrl`, `<P>-dialogs`, `<P>-count`, `MOTO-defusb-after`.

**Open the cue terminal first.** Prepare step 1 prints the cue file's full path. On this rig's default data folder:

```bash
tail -n0 -F /home/oscar/Coding/StudioProjects/rig-data/rounds/usb-dialog-round7/cues.log
```

Android's own dialog starts with "Open" ("Open Open Headunit ..."). The app's own starts with "Allow" ("Allow ... to
access the USB device?"). **Rule for every dialog: act only on the dialog the rule or the cue names, and touch nothing
else.** Read the operator this table once, at the start:

| Run and part | Rule |
|---|---|
| TD-P plug 1, until the ANSWER cue | Touch no dialog, whatever it says, also when it goes away and comes back |
| TD-P plug 1 from the ANSWER cue | On the "Allow" dialog: tick the box if it has one, then OK. On the "Open" dialog that follows the switch: tick the box, then OK |
| TD-P plug 2 | Every dialog that starts with "Allow" or "Open": tick the box if it has one, then OK (none is expected) |
| F-P, F-H plug A | Every "Open" dialog: tick the box ("Use by default" or "Always open"), then OK. An "Allow" dialog (not expected): touch nothing |
| F-P, F-H plug B | Every dialog (none is expected): tick the box if it has one, then OK |
| F-P, F-H plug C | Touch nothing until the CANCEL cue. At CANCEL: Cancel on the "Open" dialog, then touch nothing. At ALLOW: OK on the "Allow" dialog with no tick, then on the next "Open" dialog tick the box and press OK |
| L-P | "Open ...": press OK, tick nothing. Any other dialog: touch nothing. Touch D-POCO's screen at no other time |

The only Cancel in this round is at a CANCEL cue. Do not touch D-MOTO except for DEFREAD, DEFUSB, RESTOREUSB, UNLOCK,
CTRL, REPC, DEBUG-OFF and DEBUG-ON. Do not press
any button on P-1 except at a HOLD cue.

## 6. Helpers

Copy every file from `$RIG_DATA/rounds/usb-dialog-round6/scripts/` into `$RIG_DATA/rounds/usb-dialog-round7/scripts/`.
That folder holds rounds 4, 5 and 6's helpers (`lib.sh`, `keys.sh`, `cycle.sh`, `usbparse.sh`, `timeline.sh`,
`lib5.sh`, `x1.sh`, `bl.sh`, `p1.sh`, `xprefs.py`, `xpcp.sh`, `node.py`, `blset.py`, `phslice.py`, `pico.sh`, `pico/`,
`pico.env`, `lib6.sh`, `ifaces.sh`, `s6.sh`, `list6.sh`). If it is gone, extract them from the round 4, 5 and 6 briefs
on this branch. Then save every block below into the same folder with `extract_brief_blocks.py`. Do not copy
`wireless.env` or `lever.env`: Prepare writes both again. `f7.sh` uses `cleardef`, `setkeys`, `usbdef`, `row` and
`answers` as earlier rounds defined them. Run every script from that folder. Run every adb call
against one unit in sequence (template rule 8). Grep every capture with `-a`.

Helpers used as earlier rounds defined them: round 4's `A`, `F`, `send`, `mark`, `launch`, `home`, `cap`, `alive`,
`uncap`, `since`, `cnt`, `firstts`, `waitfor`, `th_gate`, `th_end`, `bat`, `hupid`, `usbdef`, `ident`, `setkeys`,
`cleardef`, `dump`, `sess`, `row` and `timeline.sh`; round 5's `waitany`, `plugs`, `dbgon`, `moto_dump`, `xprefs`,
`xrestore`, `blread`, `pset`, `x1.sh`, `bl.sh`, `p1.sh`, `phslice.py`, `blset.py`; round 6's `MA`, `window`, `wc1`,
`answers`, `proof`, `p1plug`, `p1unplug`, `ifaces.sh`.

**`lib7.sh`**
```bash
# lib7.sh: round 6's helpers with this round's folder and candidate, the round 6 fixes, and this round's additions.
. ./lib6.sh
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round7; mkdir -p $OUT
CAND=9ae9bef2
SVC=$PKG/com.andrerinas.openheadunit.aap.AapService
REFRESH=com.andrerinas.openheadunit.aap.action.REFRESH_SENSORS
# a plug: the service's attach line, either receiver's intent line, or the service's refusal of a blacklisted device
PLUGRE='Normal USB device attached: |USB Intent: .*USB_DEVICE_ATTACHED|Ignoring USB device attached in service \('
REFUSERE='result=1|"ok":false|not allowed|Permission Denial|Error'
STATES=allowed,ignored,blacklisted
TDRULE7="Until the ANSWER cue: touch no dialog. From ANSWER: every Allow or Open dialog, tick if there is a box, then OK. D-MOTO unlocked before the plug."
S1RULE7="Every Allow or Open dialog: tick if there is a box, then OK. D-MOTO unlocked before the plug. No notifications."
BLRULE7="Open: OK, tick nothing. Any other dialog: touch nothing. Touch D-POCO's screen at no other time."
# cue: as round 4, with a blank line after each cue (the operator's request)
cue() { printf '\a'; printf '%s\n\n' "$(date +%T) $*${RULE:+  || RULE: $RULE}" | tee -a $OUT/hand-steps.log >> $OUT/cues.log; }
# usbup N / usbgone N: round 6's, fixed: 0 when D-MOTO's PC link came up (went away) inside N s
usbup() { local t=0; until [ "$(MA get-state 2>/dev/null)" = device ]; do sleep 1; t=$((t+1)); [ $t -ge $1 ] && return 1; done; return 0; }
usbgone() { local t=0; while [ "$(MA get-state 2>/dev/null)" = device ]; do sleep 1; t=$((t+1)); [ $t -ge $1 ] && return 1; done; return 0; }
# batok [MIN]: 1 only on a real reading below MIN (default BSTOP). An empty read is retried 3 times, then logged and passed.
batok() { local v= n=0 lim=${1:-$BSTOP}
  while [ $n -lt 3 ]; do v=$(bat); [ -n "$v" ] && break; n=$((n+1)); adb connect $HU >/dev/null 2>&1; sleep 5; done
  [ -z "$v" ] && { echo "${P:-$ST} BATTERY_UNREAD" >> $OUT/marks.txt; return 0; }
  [ "$v" -ge "$lim" ] && return 0
  echo "${P:-$ST} BATTERY_LOW $v" >> $OUT/marks.txt; return 1; }
# firstre MARK ERE: the time stamp of the first line after MARK that matches ERE
firstre() { since "$1" | grep -aE -m1 -- "$2" | cut -c1-18; }
# said LABEL TEXT: one operator answer, word for word
said() { echo "$1 said=[$2] host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
# svcup: 1 when AapService is in dumpsys, else 0
svcup() { A shell dumpsys activity services $PKG 2>/dev/null | tr -d '\r' | grep -acF 'openheadunit.aap.AapService'; }
# restart_by LEVER FILE: start the service with no user flag. ctl = round 6's plain relay; a = temp allowlist, then
# the relay; b = a start from the shell. The start call's output goes to FILE, the allowlist's to FILE.tw.
restart_by() { case $1 in
    ctl) send ACTION_REFRESH_SENSORS > $2 2>&1 ;;
    a) A shell cmd deviceidle tempwhitelist -d 120000 $PKG > $2.tw 2>&1; A shell cmd deviceidle tempwhitelist >> $2.tw 2>&1
       send ACTION_REFRESH_SENSORS > $2 2>&1 ;;
    b) A shell am start-foreground-service -n $SVC -a $REFRESH > $2 2>&1 ;;
    *) echo "no lever ($1)" > $2; return 1 ;;
  esac; }
# unplugwait P: the unplug line after P-end, with one more cue after 60 s
unplugwait() { if waitfor $1-end 'USB_DEVICE_DETACHED' 60; then echo "$1 unplug=$(firstts $1-end 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt; return 0; fi
  cue "UNPLUG $1 again: the device is still in $HUNAME. Unplug it now"
  if waitfor $1-end 'USB_DEVICE_DETACHED' 120; then echo "$1 unplug=$(firstts $1-end 'USB_DEVICE_DETACHED') retry=1" >> $OUT/marks.txt
  else echo "$1 NO_UNPLUG_LINE" >> $OUT/marks.txt; fi; }
# taptext TEXT: one injected tap on the one enabled node whose text is TEXT, from a fresh dump; budget 5 per run (TAPN)
taptext() { local x=$OUT/$ST-tap-$(date +%H%M%S).xml xy; dump $x
  xy=$(python3 -I ./tnode.py $x tap "$1") || { echo "$ST TAP_VOID text=$1 ($xy)" >> $OUT/marks.txt; return 1; }
  [ "${TAPN:-0}" -ge 5 ] && { echo "$ST TAP_BUDGET text=$1" >> $OUT/marks.txt; return 1; }
  TAPN=$((${TAPN:-0}+1)); A shell input tap $xy
  echo "$ST tap $TAPN text=$1 at $xy host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; sleep 3; }
```

**`tnode.py`**
```python
# tnode.py DUMP MODE ARG: read a uiautomator dump by node text, for a unit whose dump carries no resource-id.
# Text is compared with case and outer spaces ignored. No verdict.
#   tap TEXT      "x y", the centre of the one node whose text is TEXT; exit 1 if it is not enabled
#   text LIST     the text of the one node whose text is in LIST (comma separated)
#   enabled LIST  "enabled" or "DISABLED" for that node
#   count TEXT    how many nodes have the text TEXT
#   has SUB       the first node text that holds SUB, line breaks as " / " ("-" when none)
# Exit 1 with MATCHES=<n>, DISABLED, NO_BOUNDS or PARSE_FAIL when one node is needed and is not there.
import re
import sys
import xml.etree.ElementTree as ET
f, mode, arg = sys.argv[1], sys.argv[2], sys.argv[3]
try:
    root = ET.parse(f).getroot()
except Exception as e:
    print(f"PARSE_FAIL {e}")
    sys.exit(1)
def norm(s):
    return (s or "").strip().lower()
nodes = list(root.iter("node"))
if mode == "count":
    print(sum(norm(n.get("text")) == norm(arg) for n in nodes))
    sys.exit(0)
if mode == "has":
    m = [n.get("text") for n in nodes if arg.lower() in (n.get("text") or "").lower()]
    print(m[0].replace("\n", " / ") if m else "-")
    sys.exit(0)
want = [norm(a) for a in arg.split(",")] if mode in ("text", "enabled") else [norm(arg)]
n = [x for x in nodes if norm(x.get("text")) in want]
if len(n) != 1:
    print(f"MATCHES={len(n)}")
    sys.exit(1)
n = n[0]
if mode == "text":
    print(n.get("text"))
    sys.exit(0)
if mode == "enabled":
    print("enabled" if n.get("enabled") == "true" else "DISABLED")
    sys.exit(0)
if n.get("enabled") != "true":
    print("DISABLED")
    sys.exit(1)
a = [int(v) for v in re.findall(r"\d+", n.get("bounds", ""))]
if len(a) != 4:
    print("NO_BOUNDS")
    sys.exit(1)
print(f"{(a[0] + a[2]) // 2} {(a[1] + a[3]) // 2}")
```

**`probe7.sh`**
```bash
#!/usr/bin/env bash
# HU=<D-POCO USB serial> ./probe7.sh: which lever restarts AapService from the background on API 35. No dialog up,
# nothing on D-POCO's port but its PC cable. Writes lever.env (LEVER=a|b|none) and Q-* lines in marks.txt.
ST=Q; . ./lib7.sh; HU=${HU:-$POCO}; export ST HU
# probe_one L: stop the service if it runs, fire lever L; 0 when it came back in the same pid
probe_one() { local T=Q-$1 p0 p1 ok=1
  if [ "$(svcup)" -ge 1 ]; then mark $T-stop; send ACTION_STOP_SERVICE >> $OUT/marks.txt
    waitfor $T-stop 'AapService destroying... (' 20 || echo "$T NO_DESTROY" >> $OUT/marks.txt; sleep 3; fi
  p0=$(hupid); mark $T; restart_by $1 $OUT/$T-am.txt
  waitfor $T 'AapService creating...' 15 && ok=0; p1=$(hupid)
  echo "$T started=$(cnt $T 'AapService creating...') svc_up=$(svcup) pid_before=$p0 pid_after=$p1 log_refused=$(since $T | grep -aF 'AutomationReceiver: ' | grep -acF ' failed: ') am_refused=$(grep -acE "$REFUSERE" $OUT/$T-am.txt) am=[$(tr -d '\r' < $OUT/$T-am.txt | tr '\n' ' ' | cut -c1-240)] tw=[$(tr -d '\r' < $OUT/$T-am.txt.tw 2>/dev/null | tr '\n' ' ' | cut -c1-160)]" >> $OUT/marks.txt
  [ $ok = 0 ] && [ "$p0" = "$p1" ] && [ "$p0" != none ]; }
A shell am force-stop $PKG; cap; mark Q-start; launch; sleep 3
waitfor Q-start 'AapService creating...' 20 || echo "Q NO_FIRST_START" >> $OUT/marks.txt
home; sleep 60      # the time is the point: TD-P restarts the service about 60 s after the app leaves the screen
L=none
probe_one ctl && echo "Q CONTROL_STARTED" >> $OUT/marks.txt        # round 6 saw this refused
if probe_one a; then L=a; elif probe_one b; then L=b; fi
echo "LEVER=$L" > ./lever.env; echo "Q LEVER=$L" >> $OUT/marks.txt
uncap; A shell am force-stop $PKG
```

**`s7.sh`**
```bash
# s7.sh: TD-P. tdcycle7 (plug 1), pcycle (plug 2), armTD7. Needs lib7.sh, s6.sh (proof) and lever.env.
# tdcycle7 P: one plug of D-MOTO. Our dialog up; 45 s after the plug, round 6's stop, then the restart by $LEVER with
# no user flag; the DIALOGS count; then the answer and the session. Returns 0 graded, 1 NO_PLUG, 2 low battery.
tdcycle7() { P=$1; local pid0 pid1 t0 t; PRE=-
  batok || return 2
  alive; home; pid0=$(hupid)
  RULE="$TDRULE7"; cue "NEXT $P: read the rule, nothing starts for 12 s"; sleep 12
  mark $P-cue; cue "PLUG $P: check D-MOTO is unlocked, take it off its PC cable, plug it into $HUNAME now. Then CTRL on D-MOTO"
  if ! waitany $P-cue "$PLUGRE" 120; then
    echo "$P NO_PLUG" >> $OUT/marks.txt; mark $P-end; cue "UNPLUG $P: unplug D-MOTO from $HUNAME if it is plugged in"; sleep 15; mark $P-unplug; return 1; fi
  t0=$(date +%s); echo "$P plug=$(firstre $P-cue "$PLUGRE")" >> $OUT/marks.txt
  # 1. one dialog of ours on screen: the plug's own ask, else the user's ask 40 s after the plug
  if waitfor $P-cue 'Requesting USB permission for ' 40; then echo "$P ask_by=plug" >> $OUT/marks.txt
  else mark $P-verb; send ACTION_CHECK_USB >> $OUT/marks.txt
    if waitfor $P-verb 'Requesting USB permission for ' 15; then echo "$P ask_by=verb" >> $OUT/marks.txt
    else echo "$P NO_ASK" >> $OUT/marks.txt; fi; fi
  # 2. 45 s after the plug the 30 s attach window is over, so older code would ask again. Round 6's stop, unchanged.
  t=$(( 45 - ($(date +%s) - t0) )); [ $t -gt 0 ] && sleep $t
  mark $P-stop; send ACTION_STOP_SERVICE >> $OUT/marks.txt
  waitfor $P-stop 'AapService destroying... (' 20 || echo "$P NO_DESTROY" >> $OUT/marks.txt
  sleep 3; mark $P-restart; restart_by $LEVER $OUT/$P-restart-am.txt; tr -d '\r' < $OUT/$P-restart-am.txt >> $OUT/marks.txt
  waitfor $P-restart 'AapService creating...' 20 || echo "$P NO_RESTART" >> $OUT/marks.txt
  waitfor $P-restart 'UsbLauncher: permission for ' 20 || echo "$P NO_DEC" >> $OUT/marks.txt
  sleep 10                                       # 10 s: a second dialog would start in these 10 s
  pid1=$(hupid); echo "$P pid_before=$pid0 pid_after=$pid1" >> $OUT/marks.txt
  cue "DIALOGS $P: say how many Allow dialogs are on $HUNAME's screen now. Touch nothing (20 s)"; sleep 20
  # 3. the answer, then the session
  mark $P-answer; RULE="$S1RULE7"
  cue "ANSWER $P: on the Allow dialog tick the box if it has one, then press OK. From now on, every Allow or Open dialog: tick the box if it has one, then OK"
  sess $P-answer
  mark $P-end; cue "UNPLUG $P: unplug D-MOTO from $HUNAME"; unplugwait $P
  mark $P-unplug; RULE=
  cue "COUNT $P: for this plug only, say how many USB dialogs you saw since its PLUG cue, and the first word of each (25 s)"; sleep 25
  moto_dump $P; usbdef $P-after
  echo "$P lever=$LEVER restart_svc_refused=$(window $P-restart $P-answer | grep -acF 'ForegroundServiceStartNotAllowedException/Exception caught in onStartCommand: ') restart_log_refused=$(window $P-restart $P-answer | grep -aF 'AutomationReceiver: ' | grep -acF ' failed: ') restart_am_refused=$(grep -acE "$REFUSERE" $OUT/$P-restart-am.txt) noperm=$(window $P-restart $P-answer | grep -acF 'Single USB auto-connect: device found but no permission') refuse=$(window $P-restart $P-answer | grep -acF 'is already open; not opening a second') gap_false_kept=$(wc1 $P-restart $P-answer 'UsbPermissionAsks: answer for .* \(granted=false\); entry kept') gap_false_ended=$(wc1 $P-restart $P-answer 'UsbPermissionAsks: answer for .* \(granted=false\); entry ended') gap_true=$(wc1 $P-restart $P-answer 'UsbPermissionAsks: answer for .* \(granted=true\)')" >> $OUT/marks.txt
  ./timeline.sh $(F) $P > $OUT/$P.timeline.tsv; row $P $OUT/$P.timeline.tsv
  ./timeline.sh $(F) $P/pre $P-cue $P-stop > $OUT/$P-pre.timeline.tsv; row $P/pre $OUT/$P-pre.timeline.tsv
  ./timeline.sh $(F) $P/gap $P-restart $P-answer > $OUT/$P-gap.timeline.tsv; row $P/gap $OUT/$P-gap.timeline.tsv
  ./timeline.sh $(F) $P/post $P-answer $P-unplug > $OUT/$P-post.timeline.tsv; row $P/post $OUT/$P-post.timeline.tsv
  echo "$P post_true_ended=$(wc1 $P-answer $P-unplug 'UsbPermissionAsks: answer for .* \(granted=true\); entry ended')" >> $OUT/marks.txt
  return 0; }
# pcycle P: one session plug of D-MOTO, answered by S1RULE7. Returns 0 graded, 1 NO_PLUG, 2 low battery.
pcycle() { P=$1; local pid0; PRE=-
  batok || return 2
  alive; home; pid0=$(hupid)
  mark $P-cue; cue "PLUG $P: check D-MOTO is unlocked, take it off its PC cable, plug it into $HUNAME now. Then CTRL on D-MOTO"
  if ! waitany $P-cue "$PLUGRE" 120; then
    echo "$P NO_PLUG" >> $OUT/marks.txt; mark $P-end; cue "UNPLUG $P: unplug D-MOTO from $HUNAME if it is plugged in"; sleep 15; mark $P-unplug; return 1; fi
  echo "$P plug=$(firstre $P-cue "$PLUGRE")" >> $OUT/marks.txt
  sess $P-cue
  mark $P-end; cue "UNPLUG $P: unplug D-MOTO from $HUNAME"; unplugwait $P
  mark $P-unplug; RULE=
  cue "COUNT $P: for this plug only, say how many USB dialogs you saw since its PLUG cue, and the first word of each (25 s)"; sleep 25
  moto_dump $P; usbdef $P-after
  ./timeline.sh $(F) $P > $OUT/$P.timeline.tsv; row $P $OUT/$P.timeline.tsv; return 0; }
# armTD7: TD-P. File transfer, debugging off; plug 1 is tdcycle7, plug 2 (label p3, or p4 after a retry) a session plug.
armTD7() { local T=P-TD P rc
  case "${LEVER:-none}" in a|b) ;; *) echo "$T UNTESTABLE no_restart_lever" >> $OUT/marks.txt; return 0;; esac
  cleardef $T-clear
  setkeys td $TDKEYS || { echo "$T UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  usbdef $T-pre
  MA shell input keyevent 224; MA shell wm dismiss-keyguard; timeout 5 adb -s $MOTO_USB logcat -c; mark $T-phone-start
  proof mtp $T || { echo "$T UNTESTABLE mode" >> $OUT/marks.txt; dbgon $T; return 0; }
  tdcycle7 $T-p1; rc=$?; [ $rc -eq 2 ] && return 2; [ $rc -eq 1 ] && { tdcycle7 $T-p2; rc=$?; [ $rc -eq 2 ] && return 2; }
  RULE="$S1RULE7"; cue "NEXT $T-p3: read the rule, nothing starts for 12 s"; sleep 12
  pcycle $T-p3; rc=$?; [ $rc -eq 2 ] && return 2; [ $rc -eq 1 ] && { pcycle $T-p4; [ $? -eq 2 ] && return 2; }; RULE=
  for P in $(plugs $T); do ./x1.sh $(F) $P 'VID: 22B8' >> $OUT/x1.tsv; answers $P; done
  usbdef $T-post; dbgon $T; python3 -I ./phslice.py $OUT/$T-arm-phone.txt $OUT/marks.txt $OUT $(plugs $T) >> $OUT/phone.tsv; return 0; }
```

**`list7.sh`**
```bash
# list7.sh: lp7, L-P on D-POCO with P-1 on identity 3. Round 6's lp with every tap and read by node text.
lp7() { local L=P-L3 x t; TAPN=0
  xprefs set:usb-blacklist=vidpid:18d1:2d00 bool:auto-connect-single-usb=false || { echo "$L UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  echo "$L stored_key=$(grep -acF '<string>vidpid:18d1:2d00</string>' $OUT/$ST-settings-now.xml)" >> $OUT/marks.txt
  launch; sleep 3; home
  RULE="$BLRULE7"; cue "NEXT $L: read the rule, nothing starts for 12 s"; sleep 12
  p1plug $L "with a USB-C OTG adapter and P-1's USB-A to USB-C cable" "with D-MOTO's USB-C to USB-C cable" || { p1unplug $L 'VID: 18D1'; return 0; }
  sleep 15; mark $L-btn; launch; sleep 3      # 15 s: Android's dialog and the refusal land in these 15 s
  x=$OUT/$L-home.xml; dump $x
  echo "$L home cta=$(python3 -I ./tnode.py $x count 'Connect over USB') tile=$(python3 -I ./tnode.py $x count 'USB')" >> $OUT/marks.txt
  taptext 'Connect over USB' || taptext 'USB' || echo "$L NO_LIST_BUTTON" >> $OUT/marks.txt
  sleep 3; x=$OUT/$L-list0.xml; dump $x
  t=$(python3 -I ./tnode.py $x text $STATES)
  echo "$L list0_text=$t list0_state=$(python3 -I ./tnode.py $x enabled $STATES) row=[$(python3 -I ./tnode.py $x has 'PID')]" >> $OUT/marks.txt
  if [ "$(echo "$t" | tr 'A-Z' 'a-z')" = blacklisted ]; then taptext "$t"; else echo "$L NO_BLACKLISTED_ROW ($t)" >> $OUT/marks.txt; fi
  sleep 2
  A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/$L-after.xml
  A shell run-as $PKG cat /data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml 2>/dev/null | tr -d '\r' > $OUT/$L-mirror.xml
  echo "$L after key=$(grep -acF '<string>vidpid:18d1:2d00</string>' $OUT/$L-after.xml) set=[$(python3 -I ./blset.py $OUT/$L-after.xml)] mirror=[$(python3 -I ./blset.py $OUT/$L-mirror.xml 2>/dev/null || echo unreadable)]" >> $OUT/marks.txt
  x=$OUT/$L-list1.xml; dump $x
  echo "$L list1_text=$(python3 -I ./tnode.py $x text $STATES) list1_state=$(python3 -I ./tnode.py $x enabled $STATES)" >> $OUT/marks.txt
  mark $L-list-end; A shell input keyevent 4; home
  p1unplug $L 'VID: 18D1'
  xprefs set:usb-blacklist= bool:auto-connect-single-usb=false; blread $L-cleared; }
```

**`stageP7.sh`**
```bash
#!/usr/bin/env bash
# ./stageP7.sh: TD-P, F-P, then L-P, on D-POCO; P-1 back to its console at the end.
ST=P; . ./lib7.sh; . ./wireless.env; . ./lever.env; . ./s6.sh; . ./s7.sh; . ./f7.sh; . ./pico.env; . ./pico.sh; . ./list6.sh; . ./list7.sh
HU=$POCO_W; HUNAME=D-POCO; export ST HU HUNAME; RULE=; NOPHONE=0
cue "CAB P: take D-POCO off its PC cable. D-MOTO stays on its PC cable, unlocked. Have D-MOTO's USB-C to USB-C cable ready on D-POCO"
adb connect $HU >/dev/null; adb connect $MOTO_W >/dev/null
batok $BSTART || { echo "P BATTERY_START" >> $OUT/marks.txt; exit 1; }
th_gate || exit 1
echo "P stay_on_before=$(A shell settings get global stay_on_while_plugged_in | tr -d '\r') lever=$LEVER" >> $OUT/marks.txt
A shell svc power stayon true; A shell am force-stop $PKG
S0=P-start-$(date +%H%M%S); cap; mark $S0; launch; sleep 3
armTD7; [ $? -eq 2 ] && exit 2
frun F-P; [ $? -eq 2 ] && exit 2
# L-P: P-1 on identity 3, set on the test PC
if [ "${PICO_OK:-0}" = 1 ] && pset 3; then lp7; else echo "P-L3 UNTESTABLE pset" >> $OUT/marks.txt; fi
pset 0 || echo "P P-1 NOT_ON_CONSOLE" >> $OUT/marks.txt
cleardef P-end-clear; usbdef P-end; mark P-end
send ACTION_EXPORT_LOG; sleep 5                    # the export path is on the data= line; pull that file
th_end; uncap; xrestore $OUT/backup-P.xml
A shell settings put global stay_on_while_plugged_in "$(sed -n 's/^P stay_on_before=\([0-9]*\).*/\1/p' $OUT/marks.txt | head -1)"
```

**`f7.sh`**
```bash
# f7.sh: F-P and F-H. proofadb, fplug (plugs A, B, C) and frun. Needs lib7.sh and s6.sh.
# proofadb T: D-MOTO in "No data transfer" with USB debugging on, proved on the phone and on the test PC.
# D-MOTO is on its PC cable at entry. Returns 0 on PROOF_OK, 1 otherwise. Debugging stays on.
proofadb() { local T=$1 st cf n mtp adb
  usbup 30 || { echo "$T NO_PC_LINK" >> $OUT/marks.txt; cue "PCMOTO $T: plug D-MOTO into its PC cable"; usbup 120 || return 1; }
  MA shell svc usb setScreenUnlockedFunctions none > $OUT/$T-svc.txt 2>&1
  cue "DEFUSB $T: on D-MOTO choose Developer options > Default USB configuration > No data transfer, even if it reads so already. Then say done"; sleep 60
  cue "REPC $T: keep D-MOTO unlocked. Unplug its PC cable, count to three, plug it back into the PC"
  usbgone 90 || echo "$T REPC_NOT_SEEN" >> $OUT/marks.txt
  usbup 90 || { echo "$T NO_PC_LINK" >> $OUT/marks.txt; return 1; }
  sleep 3; st=$(MA shell getprop sys.usb.state | tr -d '\r'); cf=$(MA shell getprop sys.usb.config | tr -d '\r')
  ./ifaces.sh 22b8 > $OUT/$T-pc-ifaces.txt
  n=$(grep -c '^dev ' $OUT/$T-pc-ifaces.txt); mtp=$(grep -acE ' 06/01/01 | ff/ff/00 name=MTP$' $OUT/$T-pc-ifaces.txt)
  adb=$(grep -ac ' ff/42/01 ' $OUT/$T-pc-ifaces.txt)
  echo "$T proof state=[$st] config=[$cf] pc dev=$n mtp=$mtp adb=$adb ifaces=[$(grep '^if ' $OUT/$T-pc-ifaces.txt | cut -d' ' -f3- | tr '\n' ';')]" >> $OUT/marks.txt
  [ "$st" = adb ] && [ "$mtp" = 0 ] && [ "$adb" -ge 1 ] || { echo "$T PROOF_FAIL" >> $OUT/marks.txt; return 1; }
  echo "$T PROOF_OK" >> $OUT/marks.txt; return 0; }
# fwin P TAG FROM TO: the decision and request counts of one window, into marks.txt
fwin() { echo "$1/$2 wait_auto=$(wc1 $3 $4 'UsbLauncher: permission for .* source=automatic action=wait') ask_auto=$(wc1 $3 $4 'UsbLauncher: permission for .* source=automatic action=ask') ask_user=$(wc1 $3 $4 'UsbLauncher: permission for .* source=user action=ask') reqs=$(wc1 $3 $4 'Requesting USB permission for ') reqs_ui=$(wc1 $3 $4 'UsbPermissionAsks: Requesting USB permission for ')" >> $OUT/marks.txt; }
# fplug P KIND: one plug of D-MOTO. KIND A (answer Android's dialogs), B (ticked), C (leave it, cancel, then the
# user's ask). Returns 0 graded, 1 NO_PLUG, 2 low battery.
fplug() { P=$1; local K=$2 pid0; PRE=-
  batok || return 2
  alive; home; pid0=$(hupid)
  case $K in A) RULE="$FARULE";; B) RULE="$FBRULE";; C) RULE="$FCRULE";; esac
  cue "NEXT $P: read the rule, nothing starts for 12 s"; sleep 12
  mark $P-cue; cue "PLUG $P: check D-MOTO is unlocked, take it off its PC cable, plug it into $HUNAME now. Then CTRL on D-MOTO"
  if ! waitany $P-cue "$PLUGRE" 120; then
    echo "$P NO_PLUG" >> $OUT/marks.txt; mark $P-end; cue "UNPLUG $P: unplug D-MOTO from $HUNAME if it is plugged in"; sleep 15; mark $P-unplug; return 1; fi
  echo "$P plug=$(firstre $P-cue "$PLUGRE")" >> $OUT/marks.txt
  if [ $K = C ]; then
    sleep 45                                     # 45 s: the old rule asked 30 s after the attach
    mark $P-cancel; cue "CANCEL $P: on Android's Open dialog press Cancel. Then touch nothing"
    sleep 30                                     # 30 s: a late automatic ask would land in these 30 s
    launch; sleep 3                              # API 35 refuses a background service start; the app in front lifts it
    mark $P-verb; send ACTION_CHECK_USB >> $OUT/marks.txt
    if waitfor $P-verb 'Requesting USB permission for ' 15; then
      cue "ALLOW $P: on the Allow dialog press OK, tick nothing. Then on the next Open dialog tick the box and press OK"
    else echo "$P NO_USER_ASK" >> $OUT/marks.txt; fi
    sess $P-verb
  else sess $P-cue; fi
  mark $P-end; cue "UNPLUG $P: unplug D-MOTO from $HUNAME"; unplugwait $P
  mark $P-unplug; RULE=
  cue "COUNT $P: for this plug only, say how many USB dialogs you saw since its PLUG cue, and the first word of each (25 s)"; sleep 25
  moto_dump $P; usbdef $P-after
  ./timeline.sh $(F) $P > $OUT/$P.timeline.tsv; row $P $OUT/$P.timeline.tsv; fwin $P all $P-cue $P-unplug
  if [ $K = C ]; then
    ./timeline.sh $(F) $P/wait $P-cue $P-verb > $OUT/$P-wait.timeline.tsv; row $P/wait $OUT/$P-wait.timeline.tsv; fwin $P wait $P-cue $P-verb
    ./timeline.sh $(F) $P/user $P-verb $P-unplug > $OUT/$P-user.timeline.tsv; row $P/user $OUT/$P-user.timeline.tsv; fwin $P user $P-verb $P-unplug
  fi
  return 0; }
# fclear R TAG: a CLEAR, then dumpsys usb saved and the app's lines in it counted
fclear() { cleardef $1-$2; usbdef $1-$2-check
  echo "$1-$2 pkg_lines=$(grep -acF $PKG $OUT/$1-$2-check-usb.txt) $(tail -1 $OUT/defaults.tsv | cut -f2-3 | tr '\t' ' ')" >> $OUT/marks.txt; }
# frun R: F-P or F-H. CLEAR, settings, the proof, plugs A and B, a second CLEAR, plug C. One retry per NO_PLUG.
frun() { local R=$1 rc K L
  fclear $R clear1
  setkeys f $S1KEYS || { echo "$R UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  proofadb $R || { echo "$R UNTESTABLE lever" >> $OUT/marks.txt; return 0; }
  for K in A B C; do
    [ $K = C ] && fclear $R clear2
    L=$R-p$K; fplug $L $K; rc=$?; [ $rc -eq 2 ] && return 2
    [ $rc -eq 1 ] && { fplug ${L}r $K; rc=$?; [ $rc -eq 2 ] && return 2; }
  done
  for L in $(grep -ao "^$R-p[ABC]r* plug=" $OUT/marks.txt | cut -d' ' -f1); do answers $L; done; return 0; }
FARULE="Every Open dialog: tick the box, then OK. Allow (not expected): touch nothing. D-MOTO unlocked before the plug."
FBRULE="Every dialog (none expected): tick the box if it has one, then OK. D-MOTO unlocked before the plug."
FCRULE="Touch nothing until CANCEL. At CANCEL: Cancel on Open, then touch nothing. At ALLOW: OK on Allow with no tick, then tick and OK on the next Open."
```

**`stageH7.sh`**
```bash
#!/usr/bin/env bash
# ./stageH7.sh: F-H on D-HP, then D-MOTO's Default USB configuration back to the value read in Prepare.
ST=H; . ./lib7.sh; . ./wireless.env; . ./s6.sh; . ./f7.sh
HU=$HP_W; HUNAME=D-HP; export ST HU HUNAME; RULE=; NOPHONE=0
cue "CAB H: put the OTG adapter in D-HP's port and take D-HP off its PC cable. D-POCO goes on its PC cable. D-MOTO stays on its PC cable, debugging on"
adb connect $HU >/dev/null; adb connect $MOTO_W >/dev/null
batok $BSTART || { echo "H BATTERY_START" >> $OUT/marks.txt; exit 1; }
th_gate || exit 1
echo "H stay_on_before=$(A shell settings get global stay_on_while_plugged_in | tr -d '\r')" >> $OUT/marks.txt
A shell svc power stayon true; A shell am force-stop $PKG
S0=H-start-$(date +%H%M%S); cap; mark $S0; launch; sleep 3
frun F-H; [ $? -eq 2 ] && exit 2
cleardef H-end-clear; usbdef H-end; mark H-end
send ACTION_EXPORT_LOG; sleep 5; th_end; uncap; xrestore $OUT/backup-H.xml
A shell settings put global stay_on_while_plugged_in "$(sed -n 's/^H stay_on_before=\([0-9]*\).*/\1/p' $OUT/marks.txt | head -1)"
cue "RESTOREUSB: on D-MOTO set Developer options > Default USB configuration back to the value you read at the start. Then say the value it shows"
```

## 7. The deciding lines

Every app string prints at INFO, except the `UsbDiagnostics:   ` verdict line of an accepted device (VERBOSE). Each was
checked with `grep -F -r` in `app/src/main` at `9ae9bef2`. "Composed" marks
a line whose parts the source joins at run time; each part was checked. TD-P is graded in `P-hu.txt` through
`timeline.sh` (window `<P>-cue` to `<P>-unplug`, plus `/pre` `<P>-cue` to `<P>-stop`, `/gap` `<P>-restart` to
`<P>-answer`, `/post` `<P>-answer` to `<P>-unplug`) and `x1.sh`. F-P and F-H are graded in `P-hu.txt` and `H-hu.txt`
through `timeline.sh` (window `<P>-cue` to `<P>-unplug`, and for plug C also `/wait` `<P>-cue` to `<P>-verb` and
`/user` `<P>-verb` to `<P>-unplug`) and the `fwin` lines in `marks.txt` (same windows, tags `all`, `wait`, `user`). L-P is graded through `bl.sh` and `p1.sh` in
`P-L3-cue` to `P-L3-unplug`, plus `marks.txt`. The probe is graded in `Q-hu.txt` and `marks.txt`. Phone lines are
graded in the `<label>-phone.txt` files that `phslice.py` cuts, through `phone.tsv`.

| Id | Line (fixed substring) | Read |
|---|---|---|
| L-RX, L-MK | `AutomationReceiver: `, `AutomationMarker: ` | a verb landed (no line: the step is void); the window bounds |
| L-RXF | `AutomationReceiver: ` ... ` failed: ` (composed, `AutomationReceiver.onReceive`) | **the relay threw**: with `mAllowStartForeground false` it is the API 35 refusal |
| L-PLUG | `Normal USB device attached: `, or `USB Intent: ` with `USB_DEVICE_ATTACHED`, or `Ignoring USB device attached in service (` | a plug (`PLUGRE`) |
| L-ACC | `USB accessory device attached, connecting.` | the phone came back as identity B |
| L-DIAG | `UsbDiagnostics:   ` then the device, the verdict (`accepted: ` or `rejected: `) and the interface triples (composed) | the identity the host saw. `accepted: MTP` is the MTP mode |
| L-DEC | `UsbLauncher: permission for ` with `identity=`, ` mtp=`, ` filter=`, ` activity=`, ` listen=`, ` attach=`, ` source=`, ` action=` (composed, `UsbPermissionAskPolicy.Decision.toString`; the source is `automatic`, `user` or `recovery`, the action `ask`, `wait`, `asked` or `hold`, both enum names in lower case) | **one decision.** `source=automatic action=wait` is a plug left to Android's dialog; `source=user action=ask` is the user's ask; `action=asked` after a restart is the entry that survived |
| L-REQ | `Requesting USB permission for ` (also inside `UsbPermissionAsks: Requesting USB permission for `) | every request of ours |
| L-REF | `UsbLauncher: a dialog for ` ... `is already open; not opening a second` | a claim refused at a path already asked |
| T-ANS | `UsbPermissionAsks: answer for ` path ` (granted=` `true` or `false` `); entry ` `ended` or `kept` (composed) | **the record's reading of each answer.** A grant must read `ended`; a denial must read `kept` |
| T-GRANT, T-DENY | `USB permission granted for `, `USB permission denied for ` | the service acted on an answer |
| T-HELD | `USB permission: a denial for ` ... `arrived while permission is held; ignoring it` | a late denial after a grant, dropped (information) |
| T-SINGLE | `Single USB auto-connect: device found but no permission` | the restarted service's scan reached the device |
| T-STOP | `Stop action received. Broadcasting finish request to activities.`, `AapService destroying... (` | the stop |
| T-START | `AapService creating...` | the restart |
| T-FGS | `ForegroundServiceStartNotAllowedException/Exception caught in onStartCommand: ` | the service started and then could not go foreground |
| L-SW | `Switching USB device to accessory mode `, `Switching to accessory mode.`, `Successfully requested switch to accessory mode for `, `UsbAccessoryMode: Performing AOA switch` | a switch started |
| L-SSL, L-MS | `SSL handshake complete`, `Media Sink Setup Request: ` | a session formed; the phone answered |
| L-INT | `USB Intent: ` with `USB_DEVICE_DETACHED` | the unplug |
| G-IGNSVC | `Ignoring USB device attached in service (` then `VID: 0x%04X, PID: 0x%04X` (`Settings.formatUsbVidPidDisplay`), `): `, then `rejected: blacklisted by the user` (composed) | the service refused a blacklisted device, and for which identity |
| G-IGNACT | `Ignored blacklisted USB device` | the attach screen ignored it |
| D-MATCH | `MATCH! Starting AapService` | discard rule (template §4) |

The other strings `timeline.sh`, `bl.sh`, `p1.sh` and `x1.sh` grep are in the block at the end; all matched.

UI strings, read in dumps by `tnode.py`: `Connect over USB` (`home_action_connect_usb`), `USB` (`usb`),
`Blacklisted`, `Allowed`, `Ignored` (`blacklisted`, `allowed`, `ignored`), all in `app/src/main/res/values/strings.xml`.
The row's state button is `@android:id/button1` in `list_item_device.xml`; `UsbListFragment` sets "Blacklisted" with
the button on for a blacklisted device, and "Allowed" with the button off for an accessory-mode device with no key.
Dump attributes: `text`, `enabled`, `bounds`.

System strings, not in `app/src`: `mAllowStartForeground false`, `not allowed`, `Permission Denial`, `result=1`
(`am broadcast`), `Starting service:` (`am start-foreground-service`), `USB_DEVICE_ATTACHED`, `USB_DEVICE_DETACHED`
(inside the logged intent), `START u0`, `.usb.UsbConfirmActivity`, `.usb.UsbPermissionActivity`,
`.usb.UsbResolverActivity`, `com.android.mtp`, `openheadunit.aap.AapService` (`dumpsys activity services`). Phone
strings, on D-MOTO: `entering USB accessory mode` (AOSP), `GH.CarConnSession` (information), the properties
`sys.usb.state` and `sys.usb.config`. Sysfs on the test PC: as round 6 (`ifaces.sh`). P-1: `Magic Communication Tec.`,
`Auto Box`, `bcdDevice`, `stored identity: 0`. **FAIL lines, every window:** `FATAL EXCEPTION` in a block that names
`com.andrerinas`, `ANR in com.andrerinas`, `NoClassDefFoundError`.

The files the runs write: `cycles.tsv`, `x1.tsv`, `phone.tsv`, `bl.tsv`, `p1.tsv`, `defaults.tsv` (as round 6), and
`marks.txt` (markers, `Q ...` probe lines, `LEVER=`, `plug=`, `unplug=`, `proof`, `pc dev=`, `PROOF_*`, `ask_by=`,
`pid_before=`, `restart_*_refused=`, `gap_*`, `grants=`, `ans_*`, `post_true_ended=`, `stored_key=`, `home cta=`,
`list*_text`, `list*_state`, `after key=`, `blacklist=`, `I pset`, taps, voids, `said=`, identity lines).

## 8. Runs

**Stop rule.** A cycle with `NO_PLUG` is retried once with the next label. Two in one run end the run, graded on what
it has (INCONCLUSIVE with 0 graded cycles). A cycle with `NO_SESSION` is graded, never retried. `BATTERY_LOW` ends the
stage, and the runs not reached are UNTESTABLE (battery). `BATTERY_UNREAD` is a rig note, not a stop. A failed
`setkeys`, `xprefs` or `pset` makes the run under it UNTESTABLE (settings or pset); quote the line. A `PROOF_FAIL`
makes TD-P UNTESTABLE (mode).

### Prepare (steps 1 to 6 beside the build, then 24 min)

1. Make `$OUT/scripts/` (section 6). Print the cue path: `. ./lib7.sh; echo "tail -n0 -F $OUT/cues.log"`. Give the
   operator that line exactly. Read the operator the rule table of section 5.
2. `$RIG_TOOLKIT/rig_preflight.sh D_HP:wifi D_POCO:wifi D_MOTO:wifi`. Paste the table into Setup notes. Charge D-HP
   to 100% and D-POCO to 50% or more.
3. D-MOTO's Default USB configuration, before anything else changes it:
   1. Cue DEFREAD. Record the answer at once: `said MOTO-defusb-before "<the value, word for word>"`.
   2. Cue DEFUSB (File transfer). When the operator says it is set: `said MOTO-defusb-set "File transfer"`.
4. D-MOTO, on its PC cable:
   1. `svc bluetooth disable`, read it back. WiFi stays on.
   2. Record `settings get global stay_on_while_plugged_in` and `settings get system screen_off_timeout`. Then
      `svc power stayon true` and `settings put system screen_off_timeout 1800000`.
   3. Record `getprop sys.usb.state` and `getprop sys.usb.config`. `logcat -G 16M`. Confirm the lock state with the
      operator (UNLOCK).
   4. On the test PC run `./ifaces.sh 22b8` and paste its output into Setup notes. It MUST print a `dev` line, else
      no proof can run: stop and say so.
5. D-POCO, on its PC cable (`HU=$POCO`, `ST=P`, `. ./lib7.sh`): `A shell am start -a android.intent.action.VIEW -d
   headunit://exit`, wait 3 s, then `A shell am force-stop $PKG` (template §8 role-swap rule).
6. On D-POCO (`HU=$POCO ST=P`), then on D-HP (`HU=$HP ST=H`), each on its PC cable:
   1. Save `A shell dumpsys usb > $OUT/prep-$ST-usb.txt` and run `./usbparse.sh` on it. Record `defaults=` and
      `serial_outside=`, and `grep -acF $PKG $OUT/prep-$ST-usb.txt` as `pkg_lines`.
   2. From `A shell run-as $PKG cat shared_prefs/settings.xml`, record `usb-blacklist` and `allow-devices`.
   3. On D-POCO only, record `A shell appops get $PKG SYSTEM_ALERT_WINDOW`.
7. After the build, on each host over its USB cable (`HU=$POCO ST=P`, then `HU=$HP ST=H`, with `. ./lib7.sh`):
   1. `A shell am force-stop $PKG`, then `A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/backup-$ST.xml`.
   2. `A install -r -d $OUT/apk-C.apk`, `launch`, `sleep 5`, `ident $ST-new $CAND`. `marks.txt` MUST read
      `commit_9ae9bef2=1` (or more) and an `md5_live` equal to `apk-C.apk`, else stop: UNTESTABLE (build).
   3. `A push xpcp.sh /data/local/tmp/` and `A shell chmod 755 /data/local/tmp/xpcp.sh`.
8. `chmod +x usbparse.sh timeline.sh x1.sh bl.sh p1.sh ifaces.sh probe7.sh stageP7.sh stageH7.sh` in `$OUT/scripts/`. Then
   `. ./lib7.sh; echo "$TDKEYS" | grep -c 'auto-connect-single-usb=true'` MUST print 1.
9. Check that nothing is on D-POCO's port but its PC cable, and that no USB dialog is on its screen.
10. The restart lever: `HU=$POCO ./probe7.sh` (about 2 min). Paste every `Q ` line of `marks.txt` into Setup notes,
    and `cat lever.env`. Read them so:
    - `Q-ctl ... started=0` with `log_refused` or `am_refused` at 1 or more: round 6's refusal is reproduced.
      `Q CONTROL_STARTED` instead means the limit was not in force; say so, and TD-P's own `restart_*_refused` decide.
    - `LEVER=a` or `LEVER=b`: TD-P uses it. Quote that lever's `Q-<lever>` line in full.
    - `LEVER=none`: TD-P is UNTESTABLE (no restart lever on API 35). Quote the `Q-a` and `Q-b` lines. Run L-P anyway.
11. Wireless adb for D-POCO, D-HP and D-MOTO, each still on its PC cable: `adb -s <serial> tcpip 5555`, read
    `ip -4 addr show wlan0`, `adb connect <ip>:5555`. Write `POCO_W`, `HP_W`, `MOTO_W`, and
    `MOTO_USB=<D-MOTO's cable serial>` to `$OUT/scripts/wireless.env`.
12. P-1: in `pico.env` set `PICO=$OUT/scripts/pico` and check that `console.py` is there. Keep `PICO_OK=1`.
    `id -nG | grep -cw dialout` MUST print 1, else write `PICO_OK=0`: L-P is then UNTESTABLE (P-1 not set up).

13. Read the operator the plug C rule of section 5 once more: it is the only run with a Cancel.

Then run `./stageP7.sh`, then `./stageH7.sh`. Record each operator answer with `said` at once (section 5).

Grade each run from `cycles.tsv`, `x1.tsv`, `phone.tsv`, `bl.tsv`, `p1.tsv` and `marks.txt`. "Android's dialogs" are
`confirm_A + confirm_B + confirm_x + resolver`; "ours" are `perm_A + perm_B + perm_x`. The operator's COUNT and
DIALOGS answers go beside the log as a second column; a disagreement is a finding, not a verdict.

### TD-P, a service restart while our dialog is up, on API 35 with an MTP phone. The point of the round

D-MOTO in File transfer with debugging off, `TDKEYS`, after a CLEAR. Plug 1 is `tdcycle7`: Android shows a
notification for an MTP phone, not a dialog, so our dialog opens at the plug. The service stops at 45 s and starts
again by `$LEVER` with no user flag, and only then does the operator answer. After the switch, the accessory identity
gets Android's "Open" dialog, not ours; the operator ticks it and presses OK. Plug 2 (`P-TD-p3`, or `P-TD-p4` after a
`NO_PLUG`) is a session plug.

Setup checks: `P-TD proof<k> state=[...mtp...]`, `P-TD pc dev=1 mtp=1 adb=0` (or more `mtp`), `P-TD PROOF_OK`, and
`P-TD-clear-post` `defaults=0`. Quote `P lever=`.

TD-P PASS needs, on plug 1 (`P-TD-p1`; `P-TD-p2` if p1 was `NO_PLUG`):

1. `/pre`: `req_A` at least 1, `perm_A` at least 1, `mtp_A` holds `yes`, `dec_A` holds `ask`. `marks.txt` reads
   `ask_by=plug`.
2. `marks.txt`: `pid_before` equals `pid_after`; none of `NO_DESTROY`, `NO_RESTART`, `NO_DEC`;
   `restart_svc_refused=0`, `restart_log_refused=0`, `restart_am_refused=0`; `noperm` at least 1.
3. `/gap`: `dec_A` holds only `asked`, `req_A=0`, `req_B=0`, `perm_A=0`, `perm_x=0`, `dlg_all=0`;
   `gap_false_ended=0`. `gap_false_kept` may be any number. The DIALOGS answer is 1.
4. `/post`: `post_true_ended` at least 1, `grants` at least 1, `sw_lines` at least 1, `attach_B` at least 1, `ssl` at
   least 1, `ms` at least 1. The accessory identity: `confirm_B` at least 1 and `req_B=0` (Android's dialog, not
   ours). `phone.tsv` row `P-TD-p1`: `phone_acc` at least 1.
5. Plug 2, **graded only when item 4 passed**: `dlg_all=0`, `req_A=0`, `req_B=0`, `ssl` at least 1, `ms` at least 1;
   `phone.tsv` row of that plug: `phone_acc` at least 1. When item 4 failed, write "item 5 not graded (plug 1 did not
   switch, so no tick covered the accessory identity)" and record the numbers.
6. `fatal=0` in every window; on plug 1 `ans_true_kept=0` and `ans_false_ended=0`.

TD-P FAIL if item 3 fails (a second request or dialog after the restart, `action=ask` in `/gap`, or
`gap_false_ended` at 1 or more), or item 4 fails after the operator pressed OK (no `granted=true); entry ended` line,
or a grant with no session, or `req_B` at 1 or more), or item 5 fails when graded, or item 6 fails. Say which item
failed. `confirm_B=0` with a session is a finding (Android showed no dialog for the accessory identity), not a FAIL.

TD-P INCONCLUSIVE if item 1 fails (`NO_ASK`, `ask_by=verb`, or no dialog of ours: quote `defaults=`, `x1.tsv`'s
`diag` and `P-TD-clear-post`'s `pkg_lines` if read), or item 2
fails (a changed pid means the record died with the process; `NO_RESTART` with a `restart_*_refused` at 1 or more
means the lever did not hold in the run, so quote the `$P-restart-am.txt` text; `NO_DEC` with `noperm=0` means the
scan never reached the device). TD-P UNTESTABLE if `P-TD UNTESTABLE no_restart_lever` (Prepare 10 found no lever).

**What the code before `0bb61637` would do here:** `/gap` would read `dec_A=ask`, `req_A=1`, `perm_A=1`, and DIALOGS
would be 2. Item 3 is the number that separates the two builds.

Record per plug: `diag`, `vp`, `route_A`, `confirm_A`, `confirm_B`, `perm_A`, `dec_A`, `dec_B`, `src_A`, `mtp_A`, `grants`,
`ans_true_ended`, `gap_false_kept`, `gap_true`, `held_denial`, `after=`, `defaults` before and after, the CTRL
answers, the DIALOGS answer and the COUNT.

### L-P, a blacklisted accessory-mode device in the USB list (P-1 identity 3)

`vidpid:18d1:2d00` written by `xprefs` (`P-L3 stored_key=1`). One plug of P-1 on identity 3 (`I pset 3 ... bcd=1.03`).
Two injected taps, both found by text: the home card's `Connect over USB` (the tile's `USB` only if that is void),
then the row button that reads "Blacklisted".

L-P PASS needs:

1. `bl.tsv` row `P-L3`: `ign_svc` at least 1, and `p1.tsv`'s `ign_why` names `VID: 0x18D1, PID: 0x2D00): rejected:
   blacklisted by the user`.
2. `marks.txt`: `P tap 1 text=Connect over USB` (or `text=USB`), then `P-L3 list0_text=Blacklisted` (any case) and
   `list0_state=enabled`.
3. `P tap 2 text=Blacklisted` (any case), then `P-L3 after key=0 set=[]`.
4. `list1_text=Allowed` (any case) and `list1_state=DISABLED` (no key, accessory mode).
5. `ours=0`, `req=0`, `fatal=0` in the row.

L-P FAIL if `list0_text=Allowed` with `list0_state=DISABLED` (the old bind order; `NO_BLACKLISTED_ROW` follows), or
`after key=1` after tap 2, or `list1_text=Blacklisted`. L-P INCONCLUSIVE if `NO_LIST_BUTTON` (both list-button texts
void: quote `home cta=` and `tile=`), `list0_text` reads `MATCHES=0` (no list), `NO_PLUG2`, or `ign_svc=0` (the key
was not in force: quote `mirror=`). Record `row=`, `mirror=` (`unreadable` is a rig fact, not a verdict), `android`,
`ign_act`, `acc`, `found_acc`, every `TAP_VOID`, and the COUNT.

### First-plug runs (F-H on D-HP, F-P on D-POCO). The point of the round

`frun` runs each: a CLEAR (`fclear ... clear1`), `setkeys f` (`S1KEYS`), the proof, plug A, plug B, a second CLEAR
(`clear2`), plug C. F-P runs in stage P after TD-P; F-H runs in stage H. D-POCO is the Android 15 unit, so F-P answers
the Android 15 question. Labels: `F-P-pA`, `F-P-pB`, `F-P-pC` (and `F-H-...`); a retry after `NO_PLUG` adds `r`.

Setup checks, per run: `<R> proof state=[adb]`, `pc ... mtp=0 adb=1` (or more `adb`), with `ifaces=` holding only
`ff/42/01 name=ADB Interface`, and `<R> PROOF_OK`. `PROOF_FAIL` makes the run UNTESTABLE (lever); quote the line.
`<R>-clear1` and `<R>-clear2`: `defaults=0`; record `pkg_lines` and `serial_outside` beside them.

**Plug A, "answer Open".** The operator ticks the box and presses OK on every "Open" dialog, and touches nothing else.
PASS needs, on `<R>-pA`:

1. `confirm_A=1` and `confirm_B=1`: Android's dialog for each identity, once each.
2. `req_A=0`, `req_B=0`, `perm_A=0`, `perm_B=0`; `fwin` `all` `reqs=0`, `reqs_ui=0`, `ask_auto=0`. `wait_auto` is
   recorded; 0 is allowed when the operator answered before any scan ran.
3. `sw_lines` at least 1, `attach_B` at least 1, `ssl` at least 1, `ms` at least 1, `phone_acc` at least 1
   (`cycles.tsv`; debugging is on, so the phone dump is per plug), `fatal=0`.
4. The COUNT is 2, both "Open".

**Plug B, ticked.** PASS needs, on `<R>-pB`: `dlg_all=0`, `req_A=0`, `req_B=0`, `fwin` `all` `reqs=0`; `ssl` at
least 1, `ms` at least 1, `phone_acc` at least 1, `fatal=0`. The COUNT is 0.

**Plug C, "leave it, then cancel".** After `clear2`. The operator touches nothing for 45 s, presses Cancel on
Android's dialog at the CANCEL cue, and touches nothing for 30 s more. Then the script opens the app (`launch`),
because API 35 refuses the verb's service start from the background, and sends `send ACTION_CHECK_USB` (the user's
ask) and the ALLOW cue. A `NO_USER_ASK` with `AutomationReceiver: ... failed:` in the window is a rig lever, so plug C
item 2 is then INCONCLUSIVE, not FAIL. PASS needs, on `<R>-pC`:

1. `/wait` (`<R>-pC-cue` to `<R>-pC-verb`, 75 s): `confirm_A=1`, `perm_A=0`, `req_A=0`, `req_B=0`, `ssl=0`;
   `fwin` `wait` `reqs=0`, `ask_auto=0`, `wait_auto` at least 1.
2. `/user` (`<R>-pC-verb` to `<R>-pC-unplug`): `fwin` `user` `ask_user=1` and `reqs=1`; `perm_A=1`, `req_A=1`;
   `confirm_B` at least 1, `req_B=0`; `sw_lines` at least 1, `attach_B` at least 1, `ssl` at least 1, `ms` at least 1,
   `phone_acc` at least 1 (whole plug).
3. `fatal=0`; `ans_true_kept=0`, `ans_false_ended=0`.
4. The COUNT is 3: "Open" (cancelled), "Allow", "Open".

F FAIL if plug A shows a request of ours (`reqs` or `req_A` or `req_B` at 1 or more) or `dlg_all` at 3 or more, if
plug B shows any dialog (`dlg_all` at 1 or more), or if plug C's `/wait` shows a request of ours (`reqs` at 1 or more,
or `ask_auto` at 1 or more). Say which plug and item failed.

F INCONCLUSIVE if plug A reads `confirm_A=0`: Android showed no "Open" dialog. Record `confirm_A`, `confirm_B`,
`dec_A`, `wait_auto`, `diag` and the COUNT. **On D-POCO this is the Android 15 gap, a finding the user wants**: the app
then waits for a dialog that never shows. Also INCONCLUSIVE if plug C's `/user` reads `ask_user=1` with `reqs=0` and a
session (a grant from an earlier round hid our dialog: quote `pkg_lines`), or `NO_USER_ASK`, or two `NO_PLUG` in one
plug. A plug with `NO_SESSION` is graded.

**What the code before `9ae9bef2` would do here:** plug A would read `req_A=1` and `dlg_all=4` when the route was not
seen in this boot (rounds 5 and 6, R-H plug 1). Plug C's `/wait` would read `ask_auto=1` and `req_A=1` about 30 s
after the attach. Plug A item 2 and plug C item 1 are the numbers that separate the two builds.

Record per plug: `diag`, `vp`, `confirm_A`, `confirm_B`, `perm_A`, `perm_B`, `dec_A`, `dec_B`, `src_A`, `mtp_A`,
`grants`, `ans_true_ended`, `after=`, the CTRL answer and the COUNT; and on D-POCO, in one line, whether Android's
"Open" dialog appeared for the normal identity `22B8` (plug A `confirm_A`) and for the accessory identity (`confirm_B`).

### Clean-up

Quote `P-L3-cleared blacklist=[]`, `I pset 0 stored0=1` (P-1 back on its console), `P restored`, `H restored`, and
`P-end` and `H-end` `serial_outside`. Restore on D-MOTO, at the end of stage H: the Default USB configuration (RESTOREUSB, then
`said MOTO-defusb-after "<value>"`, which MUST equal `MOTO-defusb-before`), `screen_off_timeout`,
`stay_on_while_plugged_in`, USB debugging as found, and Bluetooth (`svc bluetooth enable`).

If `stageP7.sh` stopped before its end, run from `$OUT/scripts/`:

```bash
ST=P; . ./lib7.sh; . ./wireless.env; . ./pico.env; . ./pico.sh; HU=$POCO_W
xprefs set:usb-blacklist= bool:auto-connect-single-usb=false; blread P-after
pset 0; xrestore $OUT/backup-P.xml
```

Then put `stay_on_while_plugged_in` back from the `P stay_on_before=` line and leave P-1 unplugged. If `stageH7.sh`
stopped before its end, run `ST=H; . ./lib7.sh; . ./wireless.env; HU=$HP_W; xrestore $OUT/backup-H.xml`, put
`stay_on_while_plugged_in` back from the `H stay_on_before=` line, and give the RESTOREUSB cue.

## 9. Do not re-run

| Run | Round | Verdict |
|---|---|---|
| R4a-c, R5a-c, R5t, F4a-c | 2, 3 | PASS: dialogs on a first connect, the verb asks as the user, no raise over Android's dialog |
| B1 to B10n, L1b | 4 | PASS: "Connect when plugged in", the settings hold, detection off, the boot start |
| S1-H-N, S1-H-F | 5 | PASS: API 17 offers the app once and the tick sticks, in both modes |
| S2-M-bl, S2-M-re, S2-D-ctl, S2-D-bl, BOOT, B8, ORD | 5 | PASS: the blacklist on every path on API 17 |
| S2-P1-M3, S2-P1-I1 to I5, I3bl, I4bl, I4rp | 5 | PASS: the stock CCPA, the nameless device, the AOA identities |
| DR-H | 6 | PASS: a rebuilt dialog's false denial keeps the entry, API 17 |
| TD-H | 6 | PASS: a restart while our dialog is up opens no second dialog, API 17 |
| R-H | 6 | PASS: a first connect and a ticked plug, API 17. F-H replaces it for the first plug, which `9ae9bef2` changed |
| L-H | 6 | PASS: the named key below API 21, one key for every P-1 identity |
| S1-P-N | 6 | UNTESTABLE: D-MOTO shows an MTP interface with debugging off under "No data transfer", so this phone cannot be the lever |
| S1-P-F | 5, 6 | its fields came from round 6 TD-P plug 1 |

## 10. Report back

1. **F-P and F-H:** per plug `confirm_A`, `confirm_B`, `req_A`, `req_B`, `dlg_all` beside the COUNT, `wait_auto`,
   `ask_auto`, `ask_user`, `reqs`, `ssl`, `phone_acc`; for plug C both windows. Did Android show its "Open" dialog on
   Android 15 for a non-MTP phone, did the app stay out of its way, and did the user's ask still connect?
2. **The probe:** every `Q ` line, `LEVER=`, and the `Q-<lever>` line in full.
3. **TD-P:** per window (`/pre`, `/gap`, `/post`) `dec_A`, `req_A`, `perm_A`, `dlg_all`; `ask_by`, `pid_before`,
   `pid_after`, the three `restart_*_refused`, `noperm`, `gap_false_kept`, `gap_false_ended`, the DIALOGS answer,
   `post_true_ended`, `grants`, `ssl`, `ms`, `phone_acc`; and plug 2's `dlg_all`, `req_A`, `req_B`, `ssl`,
   `phone_acc`. Did a restart on API 35 open a second dialog, and did the answer still connect?
4. **L-P:** `home cta=`, `tile=`, both taps, `list0_text`, `list0_state`, `after key=`, `set=`, `mirror=`,
   `list1_text`, `list1_state`.

Also: the Default USB configuration before, set and after; every void and its reason; every `TAP_VOID`,
`BATTERY_UNREAD`, `REPC_NOT_SEEN`, `DEBUG-OFF`, `PCMOTO`, `UNPLUG again` or HOLD cue that fired; the cable that joined
P-1 to D-POCO; every `I pset` line; and `serial_outside` on D-POCO before and after.

```decisive-strings
AutomationReceiver: 
 failed: 
AutomationMarker: 
ACTION_CHECK_USB
ACTION_STOP_SERVICE
ACTION_REFRESH_SENSORS
com.andrerinas.openheadunit.aap.action.REFRESH_SENSORS
.aap.AapService
Normal USB device attached: 
USB Intent: 
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
 filter=
 activity=
 listen=
 attach=
 source=
 action=
Requesting USB permission for 
UsbPermissionAsks: Requesting USB permission for 
opened under 
ms ago; not opening a second
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
UsbAttachedActivity: Connect when plugged in is off; opening the app, not switching 
UsbLauncher: not switching 
 on a plug-in: 
Reopen on reconnection: launching MainActivity (
Reopen on reconnection: not raised over Android's USB dialog (
USB auto-start: launching app for 
UsbLauncher: USB auto-connect held while the settings screen is open
UsbAttachedActivity: settings on show or the status pill
checking USB now (trigger=
SSL handshake complete
Media Sink Setup Request: 
MATCH! Starting AapService
Connect over USB
Blacklisted
Allowed
Ignored
```
