# usb-dialog, round 5 brief: a file-transfer phone, the "No data transfer" lever, and the USB blacklist on API 17

Published name: `usb-dialog-round5-brief.md`. Results go in `usb-dialog-round5-results.md`.
Evidence: upload `usb-dialog-round5-captures.zip` to the existing release `rig-evidence-usb-dialog`.

Units: D-POCO (POCO X3 NFC, API 35) and D-HP (HP Slate 7 Plus, API 17) as USB hosts, one at a time. D-MOTO (Motorola
edge 30 neo, API 34) is the wired phone, and the phone of D-DONGLE. D-DONGLE (the rig's wireless Android Auto USB
dongle) is the second blacklist device. P-1 (the rig's RP2040 USB stand-in, `tools/pico-usb-stand-in/README.md`) is
the third, on D-HP only. D-HU and D-SAM take no part.

Queue line: `usb-dialog-round5-brief.md` (one arm, `fork/fix/usb-permission-dialog` `4a603930`, force-pushed; units
D-POCO, D-HP, D-MOTO, D-DONGLE, P-1; about 2 h 50 min, 3 h 20 min with the optional runs; S1-N, S2-bl and S2-P1 are the
point; run after `pico-usb-stand-in-round1`).

Pairing: possible only with a round that uses D-HU alone. If this round is the second of a pair, it takes the lock
`/tmp/ohu-rig-usbdlg.lock`. Its hand steps are many and short, so the other round must have none during this one.

Hand steps: cable moves, one plug and one unplug per cycle, every USB system dialog by a fixed rule (section 5), Clear
defaults when cued, one count per run, USB debugging on D-MOTO back on after each stage 1 arm, and one hand reboot of
D-HP (optional). In stage I: P-1 moves between a test-PC port and D-HP's OTG adapter, and a 4 s hold of P-1's KEY
when the script asks. Injected taps: 4 per blacklist run (S2-bl, S2-P1-I4bl), 1 in S2-P1-M3 and 1 in S2-P1-I3bl, each
on a dumped target, none elsewhere.

**Time budget.** Build 20 min, with Prepare steps 1 to 6 beside it. Then:

| Part | Host | Runs, in this order, in one capture | Min |
|---|---|---|---|
| Prepare 7 to 10 | both | install, identity, backups, the P-1 check | 10 |
| Stage P | D-POCO, wireless adb, D-MOTO on its OTG port | S1-P-F (3 plugs), S1-P-N (3 plugs), ORD (optional) | 30 (+3) |
| Stage H | D-HP, wireless adb, D-MOTO on its OTG port | S1-H-N (3 plugs), S1-H-F (3 plugs, also the control of S2-M), S2-M-bl, S2-P1-M3, S2-M-re, B8 (optional) | 45 (+4) |
| Stage D | D-HP, wireless adb, D-DONGLE on its OTG port, D-MOTO on its PC cable | S2-D-ctl, S2-D-bl, BOOT (optional), S2-D-re | 18 (+12) |
| Stage I | D-HP, wireless adb, P-1 on its OTG port (set on the test PC between runs) | S2-P1-I1, S2-P1-I2 (optional), S2-P1-I4, S2-P1-I4bl, S2-P1-I4rp, S2-P1-I5 (optional), S2-P1-I3, S2-P1-I3bl, P-1 back to the console | 35 (+9) |
| Cable moves, thermal gates | | | 13 |

**About 2 h 50 min** with the build, 3 h 20 min with the optional runs. Write the results file after every run.

**S1-N, S2-bl and S2-P1 are the point of the round.**

- S1-N answers the Android 7.0 report: a head unit with a Samsung phone in file transfer, where the app asks for USB on
  every plug. Our reply tells the user to set the phone's "Default USB configuration" to "No data transfer". S1-N tests
  that lever: with it, Android must offer the app once, keep the "always" tick, and show no dialog on the next plugs.
- S2-bl answers the Android 4.3 report: a head unit with a CarPlay/Android Auto box on one port and the phone on the
  other. Below API 21 the blacklist keys on VID:PID. S2-bl proves on API 17 that a blacklisted device gets no dialog of
  ours, no switch and no session on every path that can act on it.
- S2-P1 answers the stock CCPA question and the cost of the VID:PID key. With P-1 it shows, on API 17, what the app
  does with a stock Carlinkit CCPA (identity 1), with a nameless vendor device before and after a blacklist (identity
  4), and with an AOA accessory blacklisted by `vidpid:18d1:2d00` (identity 3). S2-P1-M3 then measures whether that key
  also refuses a real phone in accessory mode.

## 1. Build and baseline

One arm, no baseline build. Rounds 1 to 4 give every "before" number.

| Ref | Commit | Tree |
|---|---|---|
| `fork/fix/usb-permission-dialog` | `4a603930ecbfdeb224907a1d57d8434b1259163c` | `ae3050ba391aa9538cb85b0497109fad7aedce93` |

**History was rewritten.** The branch is force-pushed to `fork` over its tip of 2026-10-28, `e2e560eb3fb5`. It is 4
commits on `main` `2018d8f09`: `99c0f3824`, `143c4593e`, `a69d23359`, `4a603930e`. Its `app/src/main` is identical to
round 4's `3dcbf76a`: the only difference is two test files. So the APK behaves as round 4's did. The dev host suite at
this commit: **4345 JVM tests, 0 failures**. Do not run the unit tests on the rig. No other queued round shares this
base.

**`get_apk.sh`**
```bash
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round5; mkdir -p $OUT
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh usb-dialog candidate main | tail -1)}   # record the path in Setup notes
git -C $WT fetch fork fix/usb-permission-dialog
git -C $WT checkout --detach 4a603930ecbfdeb224907a1d57d8434b1259163c    # fails if the force-push is missing: stop, UNTESTABLE (build)
git -C $WT rev-parse 'HEAD^{tree}'                 # MUST print ae3050ba391aa9538cb85b0497109fad7aedce93
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
cp rig-data/apks/*.apk $OUT/apk-C.apk               # at once: build_hur.sh deletes the previous APK
md5sum $OUT/apk-C.apk                                # record in Setup notes
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF 'rejected: blacklisted by the user'               # MUST be 1 or more
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF 'Connect when plugged in is off; opening the app' # MUST be 1 or more
```

**Identity on each host, after the install:** `send ACTION_QUERY_STATE` carries `4a603930`, and the md5 of the pulled
live APK matches `apk-C.apk`. Install with `adb install -r -d`. Never uninstall.

## 2. What this is and why

Rounds 1 to 4 fixed the "dialog on every connect" fault and added the "Connect when plugged in" gate. All their runs
passed. Two reports still need a measured answer.

**The Android 7.0 report (API 24 head unit, Samsung phone in file transfer).** From API 24, an Android host that sees an
MTP device with no stored default posts an MTP notification and does not offer any app. The code copies that rule
(`UsbPermissionAskPolicy.androidShowsDialog`: `sdkInt >= 24 && mtp && !automotive` means Android shows nothing). So
our service asks with its own dialog, and the reporter's log shows that dialog on every plug. Our reply says: set the
phone's Developer options > Default USB configuration to "No data transfer". A charging-only phone is not an MTP
device, so Android offers the app with an "always" box, and the tick sticks. Nobody has measured this.

**The Android 4.3 report (API 18 head unit, two ports).** One port carries a CarPlay/Android Auto box, the other the
phone. Below API 21 the app cannot read device or interface names. So the blacklist key is the VID:PID form
(`UsbDeviceCompat.blacklistKey` passes null names to `UsbBlacklistPolicy.key`, which then writes `vidpid:xxxx:yyyy`).
And a nameless vendor-class `FF/FF/00` interface with a bulk pair is accepted as an Android Auto candidate
(`UsbDeviceIdentityPolicy.accessoryVerdict`, reason `AOAP (vendor interface named nothing)`). The code drops a
blacklisted device from every path that acts on it:

- `UsbLauncherListener.onUsbAttach` (`Ignoring USB device attached in service (...): rejected: blacklisted by the user`),
- the service scan (`UsbLauncherManager.checkAlreadyConnected`, through `UsbDeviceCompat.isConnectable`),
- Home's single-USB auto-connect (`HomeFragment.attemptSingleUsbAutoConnect` counts only connectable devices),
- the USB button (`HomeFragment.onUsbClicked`: no connectable device opens the USB list),
- the USB list (`UsbListFragment`: the row says "Blacklisted" and its start button refuses),
- `UsbAttachedActivity` (`UsbAttachedActivity: Ignored blacklisted USB device (...)`).

We have no such box and no API 18 unit. The stand-ins are D-DONGLE in its idle identity (`18d1:4ee1`) and D-MOTO in
file transfer, both on D-HP (API 17).

**P-1 adds the identities the rig does not own** (README, Identities). Identity 1 has the stock CCPA's descriptors:
`1314:1520`, device class `EF/02/01`, one `FF/F0/00` interface with a bulk pair. No entry of `usb_device_filter.xml`
matches it, and `UsbDeviceIdentityPolicy.evaluate` answers `rejected: no Android interface`. So Android must offer no
app, and the app must only log the refusal. Identity 4 (`1314:1521`, a nameless `FF/FF/00`) matches the vendor-class
filter entry, so Android offers the app, and the app answers `accepted: AOAP (vendor interface named nothing)`. It
stalls the AOA requests, so the switch fails. Identity 3 (`18D1:2D00`, named "Android Accessory Interface") is accepted
as `already in accessory mode` and never answers on bulk, so the session times out. Identity 5 answers AOA and comes back
as identity 3.

**The VID:PID key has a cost.** Below API 21 every phone that switches without USB debugging arrives as `18D1:2D00`. So
`vidpid:18d1:2d00` can refuse every phone in accessory mode, not only one box. The USB list cannot write that key: for
an accessory-mode device the row button reads "Allowed" and is disabled (`UsbListFragment`, `isInAccessoryMode` is
checked before the blacklist). The key can still reach the list in other ways, for example an imported settings file.
S2-P1 writes it with `xprefs` and measures what it blocks.

## 3. What is different about this round

- **D-MOTO runs stage 1 with USB debugging off.** Template §7b: a phone with debugging on exposes `FF/42/01` and is the
  wrong instrument for which devices Android offers the app for. The reporter's phone has debugging off. So each stage
  1 arm turns debugging off by shell (`settings put global adb_enabled 0`) and the operator turns it back on at the end
  of the arm (DEBUG-ON). While it is off, nothing reaches D-MOTO. Its logcat survives, so the arm's phone capture is one
  dump at the end, cut per plug by time (`phslice.py`). The phone `RIGMARK` lines exist only at the arm edges.
- **D-MOTO's default USB mode is set by shell.** `svc usb setScreenUnlockedFunctions mtp` is "File transfer" and
  `svc usb setScreenUnlockedFunctions none` is "No data transfer". This is the call the Developer options row makes. The
  script reads it back from `dumpsys usb`. If the shell refuses, the operator sets the row (DEFUSB). The mode applies
  only while D-MOTO's screen is unlocked, so every plug cue says to check the screen.
- **A measured hint, not a fact: on D-MOTO the two modes may look the same.** A sweep on 2026-08-30 read
  `22B8:2E82` with `FF/FF/00` for both charging-only and file transfer. The interface name decides whether Android
  treats it as MTP, and API 35 prints it in the app's verdict line (`accepted: MTP` or
  `accepted: AOAP (vendor interface named '...')`). So the identity of each arm is graded, not assumed.
- **No API 24 host exists on the rig.** D-POCO is API 35 and D-HP is API 17. API 35 uses a stored default before the
  MTP rule; the plan reads API 24 as the other way round. So S1-P-F may show 0 dialogs after a tick on API 35 where the
  reporter's API 24 unit cannot. S1-N carries over better: a non-MTP device takes the same path on API 17, 24 and 35.
- **The log level is VERBOSE (`log-level=0`).** The app's verdict line for an accepted device
  (`UsbDiagnostics:   <device> [...] accepted: ... | if0 FF/FF/00 (...)`) prints only at VERBOSE. A refused device
  prints it at INFO. Sessions are short, so the extra `RECV` lines are harmless.
- **The MTP notification is read from the events buffer.** The notification goes away at the AOA switch, a few seconds
  after the plug, so a `dumpsys notification` at a fixed time can miss it. NotificationManagerService writes
  `notification_enqueue` with the package on every post. `x1.sh` counts those that name `com.android.mtp`.
- **The rule "tick only on the first dialog" changed to "tick on every dialog that has a box".** An OK on Android's
  dialog without the tick clears the stored default (round 1 diagnosis). With the narrower rule, plug 3 could fail for
  that reason and not for the one under test.
- **D-POCO keeps lasting grants after this round.** On API 35 a tick on our dialog writes a grant keyed by VID, PID and
  serial. Clear defaults does not remove it (template §7b). S1-P leaves one for D-MOTO's debugging-off identities. Record
  `serial_outside` after the round, and say so in Setup notes for the next round.
- **A blacklisted device prints no `Normal USB device attached:` line.** The service returns before it. So the
  blacklist run waits on `Ignoring USB device attached in service (` instead.
- **The blacklist is written with this round's own writer, `xprefs`.** It edits `settings.xml` on the host and puts it
  back with `run-as` (app stopped). Round 4's `writeprefs` is kept for everything else. The USB list's row button is
  then tapped three times, so the app writes the key itself, and the brief reads back what the app wrote.
- **The USB button and the USB list have no verb.** `send ACTION_CHECK_USB` reaches the service scan only, not
  `HomeFragment.onUsbClicked`. So S2-bl taps `:id/usb_button` once and `android:id/button1` three times, each on a target
  resolved from a `uiautomator dump`, at most 5 per run (`rig-quirks/topics/tooling.md`, "Injected taps"). On D-HP a tap
  at the dumped bounds of the home's WiFi button worked before (`units/D-HP.md`).
- **D-HP keeps the attach route verdict in the app process.** Every `setkeys` or `xprefs` force-stop loses it, so the
  first plug after one can stack our dialog over Android's (round 4, H-B1). Only plug 1 of an arm can show it.
- **D-HP may hold defaults for D-DONGLE** from the dongle round. S2-D-ctl starts with a CLEAR.
- **D-DONGLE draws power from D-HP.** Charge gates: D-HP starts a stage at 60% or more and stops at 30%; D-POCO starts
  at 50% and stops at 25%. If two plugs in a row print `NO_PLUG` on D-HP, reseat the OTG adapter.
- **BOOT needs D-HP on its PC cable afterwards.** `adb tcpip` does not survive a reboot on API 17, so the capture is a
  `logcat -d` taken after the cable move. The main buffer on API 17 is small. If the boot lines rotated out, BOOT is
  INCONCLUSIVE.
- **Expected INCONCLUSIVE:** S1-P-F if D-MOTO in file transfer does not reach the MTP path; S2-bl's list items if the
  USB button tap opens no list; BOOT if `Boot auto-start: received action=` is absent; the dongle's phone item if
  `GH.CarConnSession` is absent from the control; the list items of S2-P1-M3 and S2-P1-I3bl if the tap opens no list.
- **Thermal.** `rig-quirks/topics/tooling.md`: `rig_thermal.sh wait 75` before each stage (once more on exit 1),
  `rig_thermal.sh watch` during it, and UNTESTABLE (host thermal) after a second failed wait.
- **Stage I needs P-1 set up.** `pico-usb-stand-in-round1-results.md` must exist on this branch with R1 and R3 PASS.
  If it does not, Prepare 10 writes `PICO_OK=0`, `stageI.sh` exits at once, and every S2-P1-I run is UNTESTABLE (P-1
  not set up). S2-P1-M3 does not use P-1 and runs in any case.
- **P-1 is set on the test PC, never on D-HP.** Each identity change is a cable move to a test-PC port, a 4 s hold of
  KEY if P-1 is not on its console, then `console.py <n>`. The script then reads `bcdDevice` with `lsusb`, which must
  be `1.0<n>`: that is the only field that tells identity 4 from 5. A KEY press on D-HP would step through the other
  identities on D-HP's bus, so this brief never asks for one. If round 1's R2 failed, BOOTSEL does the job of KEY.
- **Ports.** While P-1 is on the test PC, it takes the third of four ports, beside D-POCO and D-MOTO. D-HP stays on
  wireless adb for the whole of stage I.
- **P-1 reaches D-HP through a micro-USB OTG to USB-C adapter**, or through stage D's OTG adapter with a USB-A to USB-C
  cable. Record which in Setup notes.
- **No phone capture in the S2-P1-I runs.** P-1 runs no Android Auto. S2-P1-M3 uses D-MOTO and has its phone capture.
- **S2-P1-M3 runs in stage H.** It needs D-MOTO in file transfer with USB debugging off, so that it switches to
  `18D1:2D00` and not `18D1:2D01`. Stage H already has that state between S2-M-bl and S2-M-re. S2-M-re then also
  proves that D-MOTO connects again after the `18d1:2d00` key is removed, so no extra plug is needed.
- **`acc_any` is not graded in S2-P1-M3 and S2-P1-I3bl.** The refusal line itself names `VID: 0x18D1, PID: 0x2D00`, so
  `acc_any` is 1 or more by design there. `attach_B` and `found_acc` are the accessory counts that decide.
- **Android's own dialog can still show for a blacklisted P-1 identity 3 or 4.** The manifest filter matches both, and
  the blacklist acts only inside the app, after the dialog. `android` at 1 or more is then correct. Only our "Allow"
  dialog and a switch are faults.
- Read `rig-quirks/topics/tooling.md`, `topics/lifecycle.md`, `units/D-HP.md`, `units/D-POCO.md`, `units/D-MOTO.md`,
  `units/D-DONGLE.md`, `tools/pico-usb-stand-in/README.md` and template §7b before the first run.

## 4. Settings keys

Neither host is rooted. Write with the app stopped. `setkeys` (round 4's `keys.sh`) writes through
`$RIG_TOOLKIT/set_prefs_runas_host.py` and reads every key back. `xprefs` (section 6) writes the blacklist steps. Back
up `settings.xml` on each host in Prepare, and restore it at the end of the host's last stage with `xrestore`.

The base block, `BASEKEYS5` in `lib5.sh`:

```
int:log-level=0 int:onboarding-version=2 bool:allow-external-configuration=true
int:wifi-connection-mode=0 set:connection-modes=usb
bool:auto-connect-last-session=false bool:auto-connect-single-usb=false int:auto-connect-delay-seconds=0
bool:has-accepted-disclaimer=true set:allow-devices= bool:use-libusb=false bool:kill-on-disconnect=false
bool:enable-floating-button=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false
bool:auto-start-on-wifi=false
```

Leave the audio keys as they are (the rig's worst case is deliberate) and leave `screen-orientation` as found. Why the
less obvious keys:

- `log-level=0` is VERBOSE (the ordinal of `LogExporter.LogLevel.VERBOSE`), for the verdict line (section 3).
- `allow-external-configuration=true`: `ACTION_EXPORT_LOG` needs it.
- `auto-connect-single-usb=false`, `allow-devices` empty: a plug reaches the plain plug-in path. S2-bl and B8 turn the
  auto-connect on by themselves. `has-accepted-disclaimer=true`: Home's auto-connect needs it.
- `auto-connect-delay-seconds=0`: Home's auto-connect runs at once, with no countdown.

The per-run keys:

| Writer and tag | `listen-for-usb-devices` | `usb-connect-on-plug-in` | `auto-start-on-usb` | `reopen-on-reconnection` | `usb-blacklist` | `auto-connect-single-usb` | Runs |
|---|---|---|---|---|---|---|---|
| `setkeys s1<R>` (`S1KEYS`) | `true` | `true` | `false` | `false` | empty | `false` | S1 arms, S2-D-ctl, ORD |
| `xprefs` (blrun, start) | | | | | `vidpid:<vid>:<pid>` | `false` | S2-bl steps 1 and 2 |
| `xprefs` (blrun, step 3) | | | | | | `true` | S2-bl steps 3 to 6 |
| `xprefs` (re) | | | | | empty | `false` | S2-re |
| `xprefs` (b8) | | `false` | | | | `true` | B8 |
| `xprefs` (boot) | | `true` | `true` | | (kept, the app's own) | `false` | BOOT |
| `xprefs` (m3) | | | | | `vidpid:18d1:2d00` | `false` | S2-P1-M3 (stage H, between S2-M-bl and S2-M-re) |
| `setkeys sI` (`S1KEYS`) | `true` | `true` | `false` | `false` | empty | `false` | stage I start: S2-P1-I1, I2, I4, I5, I3 |
| `xprefs` (blrun) | | | | | `vidpid:1314:1521`, then the app's own | `false`, then `true` | S2-P1-I4bl, as S2-bl |
| `xprefs` (rp4) | | | | | (kept, the app's own) | `false` | S2-P1-I4rp |
| `xprefs` (unbl) | | | | | empty | `false` | after S2-P1-I4rp, and after S2-P1-I3bl |
| `xprefs` (bl3) | | | | | `vidpid:18d1:2d00` | `false` | S2-P1-I3bl |

`S1KEYS` also deletes `video-profile-starvation-cap`. An empty cell keeps the value before it. `<vid>:<pid>` is the
device's identity in lower-case hex, read from its control cycle's `Normal USB device attached:` line.

Round 4 found no `usb-blacklist` and no `enable-car-launcher` on either host. Read both again in Prepare. If
`enable-car-launcher` is `true`, record it; ORD does not depend on it.

## 5. Cabling, hand steps and the answer rules

| Part | On the PC | On an OTG port | Wireless adb |
|---|---|---|---|
| Prepare | D-HP, D-POCO, D-MOTO | | |
| Stage P | D-HP | D-MOTO on D-POCO, at each PLUG cue | D-POCO, D-MOTO |
| Stage H | D-POCO | D-MOTO on D-HP, at each PLUG cue | D-HP, D-MOTO |
| Stage D | D-POCO, D-MOTO | D-DONGLE on D-HP, at each PLUG cue | D-HP |
| BOOT (after the reboot) | D-HP, D-POCO, D-MOTO | nothing | |
| Stage I, P-1 set (each PCPICO cue) | D-POCO, D-MOTO, P-1 | nothing | D-HP |
| Stage I, P-1 runs (each PLUG cue) | D-POCO, D-MOTO | P-1 on D-HP | D-HP |

Stage I uses at most three of the four test-PC ports. P-1 is never on the test PC and on D-HP at the same time.

| Id | Hand step | When | Why there is no verb |
|---|---|---|---|
| CAB | Move the cables and the OTG adapter as the table says | each part's start | hardware |
| PLUG / UNPLUG | Plug or unplug D-MOTO (or D-DONGLE, or P-1) at the host's OTG adapter | each cue | hardware |
| PCPICO / UNPICO | Plug P-1 into a free test-PC port with its USB-C cable, or unplug it from there | each cue in stage I | hardware |
| HOLD | Hold KEY on P-1 for 4 s, then let go (BOOTSEL if round 1's R2 failed). The LED then blinks once a second | only when the script finds P-1 is not on its console | the console is gone while P-1 presents an identity |
| DLG | Answer the USB dialogs by the run's rule below | during each cycle | a SystemUI dialog |
| CLEAR | On the App info screen the script opens, press Clear defaults (API 35: Open by default, then Clear default preferences) | each CLEAR cue | Settings is another app, and no shell command clears USB defaults without `MANAGE_USB` |
| COUNT | Tell the host, per plug of the run just ended, how many USB dialogs you saw and the first word of each | each COUNT cue | the second column of the dialog count |
| UNLOCK | Before every plug in stages P and H, check that D-MOTO's screen is on and unlocked; unlock it if not | every PLUG cue | D-MOTO can sit behind a PIN (`units/D-MOTO.md`), and its USB mode applies only while unlocked |
| DEBUG-OFF | On D-MOTO turn USB debugging off (Settings > System > Developer options) | only if the shell command left adb up | the shell route is tried first |
| DEBUG-ON | On D-MOTO turn USB debugging back on, same screen | the end of each stage 1 arm, and of stage H | with debugging off, no adb reaches D-MOTO |
| DEFUSB | On D-MOTO set Developer options > Default USB configuration to the mode the cue names | only if `svc usb setScreenUnlockedFunctions` is refused | the shell route is tried first |
| PCMOTO | Plug D-MOTO into its PC cable, then unplug it when cued | only if D-MOTO's wireless adb does not come back | the phone capture needs a link |
| REBOOT | Restart D-HP from its power menu | BOOT only | `adb reboot` freezes D-HP (round 4) |
| SCREEN | On D-POCO, press Settings, scroll by hand until both rows the cue names are on screen | ORD only | `SettingsActivity` is not exported, and the rig bans scripted scrolling |
| RESEAT | Reseat the OTG adapter in D-HP's port | only after two `NO_PLUG` in a row on D-HP | hardware |

**Open the cue terminal first.** Prepare step 1 prints the cue file's full path. On this rig's default data folder it
is:

```bash
tail -n0 -F /home/oscar/Coding/StudioProjects/rig-data/rounds/usb-dialog-round5/cues.log
```

Two words tell the dialogs apart. Android's own dialog starts with "Open" ("Open Open Headunit ..."). The app's own
starts with "Allow" ("Allow ... to access the USB device?"). Read the operator this table once, at the start:

| Runs | Rule for every USB dialog |
|---|---|
| S1-P-F, S1-P-N, S1-H-N, S1-H-F, S2-M-re, S2-D-ctl, S2-D-re, B8 | On every plug: tick the box if the dialog has one, then press OK. Do not touch any notification. |
| S2-M-bl, S2-D-bl, BOOT, S2-P1-I4bl, S2-P1-I4rp, S2-P1-I3bl | "Open ...": press OK, tick nothing. "Allow ..." (not expected): press Cancel. Do not touch the host's screen. |
| S2-P1-M3, S2-P1-I1, S2-P1-I2, S2-P1-I4, S2-P1-I5, S2-P1-I3 | "Open ...": press OK, tick nothing. "Allow ...": press OK, tick nothing. Do not touch the host's screen. |

Always: act only on a cue or by the rule. Do not touch D-MOTO except for UNLOCK, DEBUG-OFF, DEBUG-ON and DEFUSB. Do
not press any button on P-1 except at a HOLD cue. The S2-P1 controls tick nothing, so no stored default for a P-1
identity or for D-MOTO's accessory identity carries into the next run.

## 6. Helpers

Save every block below in `$OUT/scripts/` with `extract_brief_blocks.py`. Then copy round 4's helpers into the same
folder, unchanged: `lib.sh`, `keys.sh`, `cycle.sh`, `usbparse.sh`, `timeline.sh`, `ui.py` and `boot.sh` from
`$RIG_DATA/rounds/usb-dialog-round4/scripts/`. If that folder is gone, extract them from `usb-dialog-round4-brief.md`
on this branch. Run every script from `$OUT/scripts/`. Run every adb call against one unit in sequence (template rule
8). Grep every capture with `-a`.

Round 4's helpers this round uses, as round 4 defined them: `A`, `F`, `send`, `mark`, `cue`, `launch`, `home`, `cap`,
`alive`, `uncap`, `since`, `cnt`, `firstts`, `waitfor`, `th_gate`, `th_end`, `bat`, `hupid`, `top`, `comp`, `usbdef`,
`writeprefs`, `keyval`, `keyread`, `mirror`, `ident`, `setkeys`, `cleardef`, `dump`, `waitdump`, `cycle` (kind
`session`), `row`, `runplugs`, and the `timeline.sh` SUMMARY fields (round 4 brief, section 6).

**`lib5.sh`**
```bash
# lib5.sh: round 4's helpers with this round's folder, plus this round's additions.
. ./lib.sh; . ./keys.sh; . ./cycle.sh
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round5; mkdir -p $OUT
CAND=4a603930
case "${ST:-}" in H|D|I) BSTART=60; BSTOP=30;; *) BSTART=50; BSTOP=25;; esac
BASEKEYS5="int:log-level=0 int:onboarding-version=2 bool:allow-external-configuration=true int:wifi-connection-mode=0
 set:connection-modes=usb bool:auto-connect-last-session=false bool:auto-connect-single-usb=false
 int:auto-connect-delay-seconds=0 bool:has-accepted-disclaimer=true set:allow-devices= bool:use-libusb=false
 bool:kill-on-disconnect=false bool:enable-floating-button=false bool:auto-start-on-boot=false
 bool:auto-start-on-screen-on=false bool:auto-start-on-wifi=false"
S1KEYS="$BASEKEYS5 bool:listen-for-usb-devices=true bool:usb-connect-on-plug-in=true bool:auto-start-on-usb=false
 bool:reopen-on-reconnection=false set:usb-blacklist= del:video-profile-starvation-cap"
S1RULE="Every dialog: tick the box if it has one, then OK. Before each plug: D-MOTO's screen on and unlocked. No notifications."
BLRULE="Open: OK, tick nothing. Allow (not expected): Cancel. Do not touch the host's screen."
P1RULE="Open: OK, tick nothing. Allow: OK, tick nothing. Do not touch the host's screen."
# blread TAG: the usb-blacklist set as settings.xml holds it now, into marks.txt
blread() { A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/$1-prefs.xml
  echo "$1 blacklist=[$(python3 -I ./blset.py $OUT/$1-prefs.xml)]" >> $OUT/marks.txt; }
# moto_dump: round 4's, skipped while D-MOTO's debugging is off (the arm dumps once at its end)
eval "$(declare -f moto_dump | sed '1s/^moto_dump /moto_dump_r4 /')"
moto_dump() { if [ "${NOPHONE:-0}" = 1 ]; then echo "$1 phone deferred (debugging off)" >> $OUT/marks.txt; return 0; fi; moto_dump_r4 "$@"; }
pre_F() { home; }; pre_N() { home; }; pre_S2Mre() { home; }; pre_ctl() { home; }; pre_re() { home; }
# run5 RUN KINDS: round 4's run wrapper (NEXT cue, markers, COUNT cue)
run5() { RULE="$RULES"; cue "NEXT $ST-$1: read the rule, nothing starts for 12 s"; sleep 12
  mark $ST-$1-start; runplugs "$@"; local rc=$?; mark $ST-$1-end; RULE=
  cue "COUNT $ST-$1: tell the host, per plug, how many USB dialogs you saw and the first word of each (25 s)"; sleep 25; return $rc; }
# plugs LABELPREFIX: the plug labels marks.txt has a plug= line for
plugs() { grep -ao "^$1-p[0-9]* plug=" $OUT/marks.txt | cut -d' ' -f1 | tr '\n' ' '; }
# waitany MARK REGEX CAP: like waitfor, with an extended regex
waitany() { local t=0; while [ $t -lt $3 ]; do alive; [ "$(since "$1" | grep -acE -- "$2")" -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
# usbmode mtp|none: D-MOTO's default USB configuration, set by shell and read back; DEFUSB if the shell refuses
usbread() { adb -s $MOTO_W shell dumpsys usb 2>/dev/null | tr -d '\r' | grep -aiE -m3 'screen.?unlocked.?functions' | tr '\n' ' '; }
modeok() { [ -n "$2" ] || return 2; case $1 in mtp) echo "$2" | grep -qi 'mtp';; none) ! echo "$2" | grep -qiE 'mtp|ptp|rndis|midi|ncm';; esac; }
usbmode() { local m=$1 r rc; adb -s $MOTO_W shell svc usb setScreenUnlockedFunctions $m > $OUT/usbmode.txt 2>&1; sleep 2
  r=$(usbread); modeok $m "$r"; rc=$?
  if [ $rc -ne 0 ]; then
    cue "DEFUSB $ST-$m: on D-MOTO set Developer options > Default USB configuration to $( [ $m = mtp ] && echo 'File transfer' || echo 'No data transfer'), then say done"
    sleep 90; r=$(usbread); modeok $m "$r"; rc=$?; fi
  echo "$ST usbmode $m rc=$rc svc=[$(tr -d '\r' < $OUT/usbmode.txt | tr '\n' ' ')] read=[$r]" >> $OUT/marks.txt
  [ $rc -ne 1 ]; }          # rc 2 (no field in dumpsys usb) goes on: the arm's identity decides
# dbgoff TAG: D-MOTO's USB debugging off by shell (DEBUG-OFF if that fails); NOPHONE=1 from here
dbgoff() { timeout 8 adb -s $MOTO_W shell settings put global adb_enabled 0 >/dev/null 2>&1; sleep 5
  if timeout 5 adb -s $MOTO_W shell true >/dev/null 2>&1; then
    cue "DEBUG-OFF $1: on D-MOTO turn USB debugging off (Settings > System > Developer options)"
    local t=0; while [ $t -lt 120 ] && timeout 5 adb -s $MOTO_W shell true >/dev/null 2>&1; do sleep 5; t=$((t+5)); done; fi
  timeout 5 adb -s $MOTO_W shell true >/dev/null 2>&1 && { echo "$1 DEBUG_STILL_ON" >> $OUT/marks.txt; return 1; }
  adb disconnect $MOTO_W >/dev/null 2>&1; NOPHONE=1; echo "$1 debugging off host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
# dbgon TAG: DEBUG-ON, then the whole buffer as TAG-arm-phone.txt (WiFi first, then its PC cable)
dbgon() { cue "DEBUG-ON $1: on D-MOTO turn USB debugging back on (Settings > System > Developer options). D-MOTO stays off the OTG adapter"
  NOPHONE=0; moto_dump_r4 $1-arm; mark $1-dbgon
  echo "$1 adb_enabled=$(adb -s $MOTO_W shell settings get global adb_enabled 2>/dev/null | tr -d '\r')" >> $OUT/marks.txt; }
# xprefs ARG...: edit settings.xml with the app stopped (bool:k=v int:k=v set:k=a,b del:k), put it back, compare
xprefs() { A shell am force-stop $PKG
  A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/xp-in.xml
  python3 -I ./xprefs.py $OUT/xp-in.xml $OUT/xp-out.xml "$@" || { echo "$ST XPREFS_FAIL $*" >> $OUT/marks.txt; return 1; }
  A push $OUT/xp-out.xml /data/local/tmp/ohu-xp.xml >/dev/null; A shell chmod 644 /data/local/tmp/ohu-xp.xml
  A shell run-as $PKG sh /data/local/tmp/xpcp.sh
  A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/$ST-settings-now.xml
  [ "$(tr -d '\r\n' < $OUT/xp-out.xml)" = "$(tr -d '\r\n' < $OUT/$ST-settings-now.xml)" ] \
    || { echo "$ST XPREFS_READBACK_FAIL $*" >> $OUT/marks.txt; return 1; }
  echo "$ST xprefs $* onboarding=$(grep -ac 'name="onboarding-version"' $OUT/$ST-settings-now.xml)" >> $OUT/marks.txt; }
# xrestore FILE: put a backup back with the app stopped, and diff it
xrestore() { A shell am force-stop $PKG; A push "$1" /data/local/tmp/ohu-xp.xml >/dev/null; A shell chmod 644 /data/local/tmp/ohu-xp.xml
  A shell run-as $PKG sh /data/local/tmp/xpcp.sh
  A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/$ST-restored.xml
  if [ "$(tr -d '\r\n' < "$1")" = "$(tr -d '\r\n' < $OUT/$ST-restored.xml)" ]; then echo "$ST restored" >> $OUT/marks.txt
  else echo "$ST RESTORE_DIFF" >> $OUT/marks.txt; fi; }
# tapnode RID [TEXT]: one injected tap on the one enabled node RID (with TEXT), from a fresh dump; budget 5 per run (TAPN)
tapnode() { local x=$OUT/$ST-tap-$(date +%H%M%S).xml xy; dump $x
  xy=$(python3 -I ./node.py $x "$1" "${2:-}") || { echo "$ST TAP_VOID rid=$1 text=${2:-} ($xy)" >> $OUT/marks.txt; return 1; }
  [ "${TAPN:-0}" -ge 5 ] && { echo "$ST TAP_BUDGET rid=$1" >> $OUT/marks.txt; return 1; }
  TAPN=$((${TAPN:-0}+1)); A shell input tap $xy
  echo "$ST tap $TAPN rid=$1 text=${2:-} at $xy host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; sleep 3; }
# phrow LABEL FILE: one phone.tsv row from a phone capture already cut to the window
phrow() { printf '%s\tphone_lines=%s\tphone_acc=%s\tgh_connsession=%s\n' "$1" "$(wc -l < $2 2>/dev/null || echo 0)" \
  "$(grep -acF 'entering USB accessory mode' $2 2>/dev/null)" "$(grep -acF 'GH.CarConnSession' $2 2>/dev/null)" >> $OUT/phone.tsv; }
```

**`xpcp.sh`** (pushed to `/data/local/tmp/` on both hosts in Prepare; runs under `run-as`, with the app stopped)
```bash
#!/system/bin/sh
cat /data/local/tmp/ohu-xp.xml > shared_prefs/settings.xml
```

**`xprefs.py`**
```python
# xprefs.py IN OUT ARG...: edit a SharedPreferences file. ARG: bool:k=v int:k=v set:k=a,b (empty = empty set) del:k
import sys
import xml.etree.ElementTree as ET
src, dst, args = sys.argv[1], sys.argv[2], sys.argv[3:]
root = ET.parse(src).getroot()
if root.tag != "map":
    sys.exit("not a preferences file")
def drop(k):
    for e in list(root):
        if e.get("name") == k:
            root.remove(e)
for a in args:
    t, kv = a.split(":", 1)
    if t == "del":
        drop(kv)
        continue
    k, v = kv.split("=", 1)
    drop(k)
    if t == "set":
        e = ET.SubElement(root, "set", name=k)
        for s in [x for x in v.split(",") if x]:
            ET.SubElement(e, "string").text = s
    elif t in ("bool", "int"):
        ET.SubElement(root, "boolean" if t == "bool" else "int", name=k, value=v)
    else:
        sys.exit("unknown kind " + t)
with open(dst, "w", encoding="utf-8") as f:
    f.write("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" + ET.tostring(root, encoding="unicode") + "\n")
```

**`node.py`**
```python
# node.py DUMP RID [TEXT]: "x y", the centre of the one enabled node whose resource-id ends with RID and whose text
# is TEXT (any text when TEXT is empty). With TEXT "?", print that one node's text. Exit 1 with a reason otherwise.
import re
import sys
import xml.etree.ElementTree as ET
f, rid = sys.argv[1], sys.argv[2]
text = sys.argv[3] if len(sys.argv) > 3 else ""
try:
    root = ET.parse(f).getroot()
except Exception as e:
    print(f"PARSE_FAIL {e}")
    sys.exit(1)
n = [x for x in root.iter("node") if (x.get("resource-id") or "").endswith(rid)
     and (text in ("", "?") or x.get("text") == text)]
if len(n) != 1:
    print(f"MATCHES={len(n)}")
    sys.exit(1)
if text == "?":
    print(n[0].get("text"))
    sys.exit(0)
if n[0].get("enabled") != "true":
    print("DISABLED")
    sys.exit(1)
a = [int(v) for v in re.findall(r"\d+", n[0].get("bounds", ""))]
if len(a) != 4:
    print("NO_BOUNDS")
    sys.exit(1)
print(f"{(a[0] + a[2]) // 2} {(a[1] + a[3]) // 2}")
```

**`x1.sh`**
```bash
#!/usr/bin/env bash
# x1.sh CAPTURE P VIDPAT: one row per plug P (window P-cue to P-unplug): Android's attach route before and after the
# switch, MTP notifications, the identity on the attach line, and the app's verdict line for VIDPAT. No verdict.
f=$1; P=$2; V=$3; W=$(mktemp)
tr -d '\r' < "$f" | awk '!seen[$0]++' | sed -n "/AutomationMarker: $P-cue\$/,/AutomationMarker: $P-unplug\$/p" > $W
b=$(grep -anF -m1 'USB accessory device attached, connecting.' $W | cut -d: -f1); b=${b:-999999999}
route() { grep -an 'USB Intent: ' $W | grep -aF 'USB_DEVICE_ATTACHED' | grep -aF 'UsbAttachedActivity' | cut -d: -f1; }
ra=$(route | awk -v b=$b '$1 < b' | wc -l); rb=$(route | awk -v b=$b '$1 >= b' | wc -l)
mtpn=$(grep -aF 'notification_enqueue' $W | grep -acF 'com.android.mtp')
vp=$(grep -aF 'Normal USB device attached: ' $W | grep -ao -m1 'VID: [0-9A-F]* PID: [0-9A-F]*')
diag=$(grep -aF 'UsbDiagnostics:   ' $W | grep -aF "$V" | head -1 | sed 's/.*UsbDiagnostics:   //')
printf '%s\troute_A=%s\troute_B=%s\tmtpn=%s\tvp=%s\tdiag=%s\n' "$P" "$ra" "$rb" "$mtpn" "${vp:-none}" "${diag:-none}"
rm -f $W
```

**`bl.sh`**
```bash
#!/usr/bin/env bash
# bl.sh CAPTURE LABEL [FROM TO]: blacklist counts between the markers FROM and TO (the whole file when FROM is "-").
f=$1; L=$2; FROM=${3:--}; TO=${4:-}; W=$(mktemp)
if [ "$FROM" = - ]; then tr -d '\r' < "$f" | awk '!seen[$0]++' > $W
else tr -d '\r' < "$f" | awk '!seen[$0]++' | sed -n "/AutomationMarker: $FROM\$/,/AutomationMarker: $TO\$/p" > $W; fi
c() { grep -acF -- "$1" $W; }
ce() { grep -acE -- "$1" $W; }
by=$(grep -aoE 'UsbDiagnostics: .* sees [0-9]+ USB device\(s\), 0 usable' $W | sed 's/.*UsbDiagnostics: //; s/ sees .*//' | sort -u | tr '\n' ',')
printf '%s\tign_svc=%s\tign_act=%s\tdiag_bl=%s\tdiag0=%s\tdiag0_by=%s\trefused_all=%s\thomeauto=%s\thome0=%s\tbtn1=%s\tacc_any=%s\tattach_A=%s\tattach_B=%s\tours=%s\tandroid=%s\treq=%s\tsw_lines=%s\tssl=%s\tms=%s\trx_check=%s\tdetach=%s\tfatal=%s\n' "$L" \
  "$(grep -aF 'Ignoring USB device attached in service (' $W | grep -acF 'rejected: blacklisted by the user')" \
  "$(c 'Ignored blacklisted USB device')" \
  "$(grep -aF 'UsbDiagnostics:   ' $W | grep -acF 'rejected: blacklisted by the user')" \
  "$(ce 'UsbDiagnostics: .* sees [0-9]+ USB device\(s\), 0 usable for Android Auto')" "${by:--}" \
  "$(c 'UsbDiagnostics: everything on the bus was refused')" \
  "$(c 'HomeFragment: Requesting single-USB auto-connect via AapService')" \
  "$(c 'single-USB auto-connect found 0 connectable devices')" \
  "$(c 'USB button: Single device found - ')" \
  "$(ce 'VID: (0x)?18D1,? PID: (0x)?2D0[0-5]|USB accessory device attached, connecting\.|Found device already in accessory mode: ')" \
  "$(c 'Normal USB device attached: ')" "$(c 'USB accessory device attached, connecting.')" \
  "$(grep -aF 'START u0' $W | grep -acF '.usb.UsbPermissionActivity')" \
  "$(grep -aF 'START u0' $W | grep -acE '\.usb\.Usb(Confirm|Resolver)Activity')" \
  "$(c 'Requesting USB permission for ')" \
  "$(ce 'Switching USB device to accessory mode |Switching to accessory mode\.|Successfully requested switch to accessory mode for |UsbAccessoryMode: Performing AOA switch')" \
  "$(c 'SSL handshake complete')" "$(c 'Media Sink Setup Request: ')" \
  "$(grep -aF 'AutomationReceiver: ' $W | grep -acF 'ACTION_CHECK_USB')" \
  "$(grep -aF 'USB Intent: ' $W | grep -acF 'USB_DEVICE_DETACHED')" \
  "$(grep -aA3 'FATAL EXCEPTION' $W | grep -acF 'com.andrerinas')"
rm -f $W
```

What the `bl.sh` fields mean: `ign_svc` the service refused the device as blacklisted; `ign_act` the attach screen
ignored it; `diag_bl` verdict lines that say `rejected: blacklisted by the user`; `diag0` and `diag0_by` the "0 usable"
headers and which callers printed them (`USB list`, `USB button`, `USB attach`, `service scan (force=...)`);
`homeauto`, `home0` Home's auto-connect fired, and found 0; `btn1` the USB button picked a device; `acc_any` any
accessory identity in any line; `ours` our dialog starts; `android` Android's dialog starts; `req` our requests;
`sw_lines` any switch line; `rx_check` the verb landed; `detach` unplug intents.

**`p1.sh`**
```bash
#!/usr/bin/env bash
# p1.sh CAPTURE LABEL FROM TO VIDPAT: the S2-P1 counts between the markers FROM and TO, beside bl.sh's row. No verdict.
f=$1; L=$2; FROM=$3; TO=$4; V=$5; W=$(mktemp)
tr -d '\r' < "$f" | awk '!seen[$0]++' | sed -n "/AutomationMarker: $FROM\$/,/AutomationMarker: $TO\$/p" > $W
c() { grep -acF -- "$1" $W; }
sa=$(grep -aF 'USB Intent: ' $W | grep -aF 'USB_DEVICE_ATTACHED' | grep -acvF 'UsbAttachedActivity')
aa=$(grep -aF 'USB Intent: ' $W | grep -aF 'USB_DEVICE_ATTACHED' | grep -acF 'UsbAttachedActivity')
why=$(grep -aF 'Ignoring USB device attached in service (' $W | sed 's/.*attached in service (//' | sort -u | tr '\n' ';')
accid=$(grep -aoE 'VID: (0x)?18D1,? PID: (0x)?2D0[0-5]' $W | sort -u | tr '\n' ';')
diag=$(grep -aF 'UsbDiagnostics:   ' $W | grep -aF "$V" | head -1 | sed 's/.*UsbDiagnostics:   //')
printf '%s\tsvc_attach=%s\tact_attach=%s\tign=%s\tign_why=%s\tnonand=%s\tnorm=%s\tacc=%s\tfound_acc=%s\taccid=%s\tswfail=%s\taccstart=%s\tvto=%s\tdiag=%s\n' "$L" \
  "$sa" "$aa" "$(c 'Ignoring USB device attached in service (')" "${why:--}" \
  "$(c 'Ignoring non-Android USB device in onCreate (VID: ')" "$(c 'Normal USB device attached: ')" \
  "$(c 'USB accessory device attached, connecting.')" "$(c 'Found device already in accessory mode: ')" "${accid:--}" \
  "$(c 'UsbAccessoryMode: AOA switch failed: ')" "$(c 'Acc start sent (len=')" \
  "$(c 'Handshake: Version exchange timed out after ')" "${diag:-none}"
rm -f $W
```

What the `p1.sh` fields mean: `svc_attach` the service's receiver saw an attach; `act_attach` Android started our
attach screen; `ign` and `ign_why` the service refused a device, and every distinct `VID: 0x..., PID: 0x...): reason`;
`nonand` the attach screen refused a non-Android device; `norm` the service accepted a normal-mode device; `acc` it
accepted an accessory-mode device; `found_acc` the scan found one; `accid` every accessory identity named in any line;
`swfail` a failed AOA switch; `accstart` a switch that reached AOA request 53; `vto` the version exchange timed out;
`diag` the first verdict line for VIDPAT.

**`blset.py`**
```python
# blset.py PREFS: the usb-blacklist set of a settings.xml, comma-joined; "absent" when the key is not there.
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
s = [e for e in root if e.get("name") == "usb-blacklist"]
print("absent" if not s else ",".join(x.text or "" for x in s[0]))
```

**`phslice.py`**
```python
# phslice.py PHONE MARKS OUTDIR LABEL...: per label, D-MOTO's lines from 5 s before its plug= to 5 s after its
# unplug= (marks.txt, the host's log clock), saved as LABEL-phone.txt. One row per label. No verdict.
import datetime as D
import re
import sys
ph, marks, outdir, labels = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
yr = D.date.today().year
def t(s):
    return D.datetime.strptime(f"{yr}-{s}", "%Y-%m-%d %H:%M:%S.%f")
m = open(marks, errors="replace").read().splitlines()
lines = open(ph, errors="replace").read().splitlines()
for L in labels:
    p = [x for x in m if x.startswith(f"{L} plug=")]
    u = [x for x in m if x.startswith(f"{L} unplug=")]
    if not p or not u:
        print(f"{L}\tphone=NO_TIMES")
        continue
    a = t(re.search(r"plug=(\S+ \S+)", p[0]).group(1)) - D.timedelta(seconds=5)
    b = t(re.search(r"unplug=(\S+ \S+)", u[0]).group(1)) + D.timedelta(seconds=5)
    sel = []
    for x in lines:
        try:
            ts = t(x[:18])
        except ValueError:
            continue
        if a <= ts <= b:
            sel.append(x)
    open(f"{outdir}/{L}-phone.txt", "w").write("\n".join(sel) + "\n")
    acc = sum("entering USB accessory mode" in x for x in sel)
    gh = sum("GH.CarConnSession" in x for x in sel)
    print(f"{L}\tphone_lines={len(sel)}\tphone_acc={acc}\tgh_connsession={gh}")
```

**`s1.sh`**
```bash
# s1.sh: arm RUN, one stage 1 arm on the current host. RUN F = File transfer, N = No data transfer.
# KEEPOFF=1 leaves D-MOTO's debugging off at the end (stage H's F arm: stage 2 goes on in that state).
arm() { local R=$1 M P; case $R in F) M=mtp;; N) M=none;; esac
  usbmode $M || { echo "$ST-$R UNTESTABLE mode" >> $OUT/marks.txt; return 0; }
  cleardef $ST-$R-clear
  setkeys s1$R $S1KEYS || { echo "$ST-$R UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  usbdef $ST-$R-pre
  adb -s $MOTO_W shell input keyevent 224; adb -s $MOTO_W shell wm dismiss-keyguard; timeout 5 adb -s $MOTO_W logcat -c
  mark $ST-$R-phone-start
  dbgoff $ST-$R || { echo "$ST-$R UNTESTABLE debugging" >> $OUT/marks.txt; return 0; }
  RULES="$S1RULE"; run5 $R "session session session"; [ $? -eq 2 ] && return 2
  for P in $(plugs $ST-$R); do ./x1.sh $(F) $P 'VID: 22B8' >> $OUT/x1.tsv; done
  usbdef $ST-$R-post
  [ "${KEEPOFF:-0}" = 1 ] && return 0
  dbgon $ST-$R; python3 -I ./phslice.py $OUT/$ST-$R-arm-phone.txt $OUT/marks.txt $OUT $(plugs $ST-$R) >> $OUT/phone.tsv; return 0; }
```

**`blrun.sh`**
```bash
# blrun.sh: blrun X KEY VP DEV, one blacklist run on D-HP for device X (M = D-MOTO, D = D-DONGLE).
# KEY is vidpid:<vid>:<pid>, VP the attach line's "VID: xxxx PID: yyyy", DEV the device's name for the cues.
blrun() { local X=$1 KEY=$2 VP=$3 DEV=$4 L=$ST-S2$1 want w; TAPN=0
  xprefs set:usb-blacklist=$KEY bool:auto-connect-single-usb=false || { echo "$L UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  echo "$L stored_key=$(grep -acF "<string>$KEY</string>" $OUT/$ST-settings-now.xml)" >> $OUT/marks.txt
  launch; sleep 3; home
  [ "${NOPHONE:-0}" = 1 ] || timeout 5 adb -s $MOTO_W logcat -c
  RULE="$BLRULE"; cue "NEXT $L: read the rule, nothing starts for 12 s"; sleep 12; mark $L-start
  # 1. the plug-in, detection and "Connect when plugged in" on
  mark $L-plug; cue "PLUG $L: plug $DEV into the OTG adapter on $HUNAME now"
  if waitany $L-plug 'Ignoring USB device attached in service \(|Normal USB device attached: ' 120; then
    echo "$L plug=$(since $L-plug | grep -aE -m1 'Ignoring USB device attached in service \(|Normal USB device attached: ' | cut -c1-18)" >> $OUT/marks.txt
  else echo "$L NO_PLUG" >> $OUT/marks.txt; fi
  sleep 25; mark $L-plug-end                 # 25 s: a fallback, a switch or a dialog would land in these 25 s
  # 2. the app opened with the device present
  A shell am force-stop $PKG; mark $L-open; launch; sleep 15; mark $L-open-end
  # 3. Home's single-USB auto-connect, from a cold start with the device present
  xprefs bool:auto-connect-single-usb=true || echo "$L auto UNTESTABLE settings" >> $OUT/marks.txt
  mark $L-auto; launch
  waitfor $L-auto 'HomeFragment: Requesting single-USB auto-connect via AapService' 30 || echo "$L NO_HOMEAUTO" >> $OUT/marks.txt
  sleep 10; mark $L-auto-end
  # 4. the USB button (tap 1), then the USB button's verb
  mark $L-btn; tapnode ':id/usb_button'; sleep 3; dump $OUT/$L-list0.xml
  echo "$L list0=$(python3 -I ./node.py $OUT/$L-list0.xml 'android:id/button1' '?')" >> $OUT/marks.txt
  send ACTION_CHECK_USB >> $OUT/marks.txt; sleep 10
  # 5. the USB list's row button, the app's own writer: Blacklisted -> Ignored -> Allowed -> Blacklisted (taps 2 to 4)
  for want in Blacklisted Ignored Allowed; do
    tapnode 'android:id/button1' $want || break
    A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/$L-after-$want.xml
    echo "$L after_tap_on_$want key=$(grep -acF "<string>$KEY</string>" $OUT/$L-after-$want.xml) name_keys=$(grep -acF '<string>name:' $OUT/$L-after-$want.xml) allowed=$(grep -acF "<string>$VP</string>" $OUT/$L-after-$want.xml)" >> $OUT/marks.txt
  done
  dump $OUT/$L-list3.xml; echo "$L list3=$(python3 -I ./node.py $OUT/$L-list3.xml 'android:id/button1' '?')" >> $OUT/marks.txt
  mark $L-list-end; A shell input keyevent 4; home
  # 6. the unplug
  mark $L-end; cue "UNPLUG $L: unplug $DEV from $HUNAME"
  waitfor $L-end 'USB_DEVICE_DETACHED' 60 && echo "$L unplug=$(firstts $L-end 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt
  mark $L-unplug; RULE=
  cue "COUNT $L: tell the host how many USB dialogs you saw since the plug and the first word of each (25 s)"; sleep 25
  [ "${NOPHONE:-0}" = 1 ] || { moto_dump_r4 $L; phrow $L $OUT/$L-phone.txt; }
  for w in plug:$L-plug:$L-plug-end open:$L-open:$L-open-end auto:$L-auto:$L-auto-end btn:$L-btn:$L-list-end all:$L-plug:$L-unplug; do
    ./bl.sh $(F) $L/${w%%:*} $(echo $w | cut -d: -f2) $(echo $w | cut -d: -f3) >> $OUT/bl.tsv; done; }
# keyof LABEL: from a control plug's attach line, "VID: xxxx PID: yyyy" and its vidpid: key
keyof() { VP=$(since $1-cue | grep -aF 'Normal USB device attached: ' | grep -ao -m1 'VID: [0-9A-F]* PID: [0-9A-F]*')
  KEY=$(echo "$VP" | awk '{printf "vidpid:%s:%s", tolower($2), tolower($4)}'); echo "$1 vp=[$VP] key=[$KEY]" >> $OUT/marks.txt; [ -n "$VP" ]; }
```

**`m3.sh`**
```bash
# m3.sh: m3, S2-P1-M3 in stage H. The 18d1:2d00 key with D-MOTO in file transfer, debugging off: the phone's own
# identity is not blacklisted, so it switches; does the key then refuse its accessory identity? One tap: the USB button.
m3() { local L=H-P1M3-p1 x=$OUT/H-P1M3-p1-list.xml; TAPN=0
  xprefs set:usb-blacklist=vidpid:18d1:2d00 bool:auto-connect-single-usb=false || { echo "$L UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  echo "$L stored_key=$(grep -acF '<string>vidpid:18d1:2d00</string>' $OUT/$ST-settings-now.xml)" >> $OUT/marks.txt
  launch; sleep 3; home
  RULE="$P1RULE"; cue "NEXT $L: read the rule, nothing starts for 12 s"; sleep 12
  mark $L-cue; cue "PLUG $L: check D-MOTO's screen is on and unlocked, then plug its cable into the OTG adapter on D-HP now"
  if waitfor $L-cue 'Normal USB device attached: ' 120; then
    echo "$L plug=$(firstts $L-cue 'Normal USB device attached: ')" >> $OUT/marks.txt
  else echo "$L NO_PLUG" >> $OUT/marks.txt; fi
  if waitany $L-cue 'Ignoring USB device attached in service \(VID: 0x18D1, PID: 0x2D0' 60; then
    sleep 15; mark $L-btn                     # 15 s: a late connect from another path would start in these 15 s
    tapnode ':id/usb_button'; sleep 3; dump $x
    echo "$L list_text=$(python3 -I ./node.py $x 'android:id/button1' '?') list_state=$(python3 -I ./node.py $x 'android:id/button1' '')" >> $OUT/marks.txt
    A shell input keyevent 4; home
  else echo "$L NO_ACC_REFUSAL" >> $OUT/marks.txt; sleep 30; fi
  mark $L-end; cue "UNPLUG $L: unplug D-MOTO from D-HP"
  waitfor $L-end 'USB_DEVICE_DETACHED' 60 && echo "$L unplug=$(firstts $L-end 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt
  mark $L-unplug; RULE=
  cue "COUNT $L: tell the host how many USB dialogs you saw since the plug and the first word of each (25 s)"; sleep 25
  ./x1.sh $(F) $L 'VID: 22B8' >> $OUT/x1.tsv
  ./bl.sh $(F) $L $L-cue $L-unplug >> $OUT/bl.tsv
  ./p1.sh $(F) $L $L-cue $L-unplug 'VID: 18D1' >> $OUT/p1.tsv; }
```

**`pico.sh`**
```bash
# pico.sh: P-1 helpers for stage I. pset N puts P-1 on identity N on the test PC (0 = the console); p1run plugs it into D-HP.
PVP=(2e8a:000a 1314:1520 1314:1520 18d1:2d00 1314:1521 1314:1521)   # identity -> VID:PID (README, Identities)
P1VID=('VID: 2E8A' 'VID: 1314' 'VID: 1314' 'VID: 18D1' 'VID: 1314' 'VID: 1314')   # identity -> the verdict line's form
onpc() { local v; for v in ${PVP[@]}; do lsusb -d $v >/dev/null 2>&1 && { echo $v; return 0; }; done; return 1; }
ttyof() { local t; for t in /dev/ttyACM*; do
    udevadm info -q property -n $t 2>/dev/null | grep -q '^ID_VENDOR_ID=2e8a$' && { echo $t; return 0; }; done; return 1; }
# pset N: PCPICO, HOLD if P-1 is not on its console, console.py N, the bcdDevice check, then UNPICO. 1 on any failure.
pset() { local n=$1 vp=${PVP[$1]} t=0 now port bcd
  cue "PCPICO $n: plug P-1 into a free test-PC port with its USB-C cable. Leave D-HP's OTG adapter empty"
  until now=$(onpc); do sleep 2; t=$((t+2)); [ $t -ge 120 ] && { echo "I pset $n NO_PICO" >> $OUT/marks.txt; return 1; }; done
  if [ "$now" != 2e8a:000a ]; then
    cue "HOLD $n: hold KEY on P-1 for 4 s, then let go. The LED then blinks once a second"
    t=0; until lsusb -d 2e8a:000a >/dev/null 2>&1; do sleep 2; t=$((t+2))
      [ $t -ge 60 ] && { echo "I pset $n NO_CONSOLE (was $now)" >> $OUT/marks.txt; return 1; }; done
  fi
  sleep 2; port=$(ttyof) || { echo "I pset $n NO_TTY" >> $OUT/marks.txt; return 1; }
  if [ $n = 0 ]; then
    python3 -I $PICO/console.py --port $port i > $OUT/I-pico-info.txt 2>&1
    echo "I pset 0 stored0=$(grep -acF 'stored identity: 0' $OUT/I-pico-info.txt) host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt
  else
    python3 -I $PICO/console.py --port $port $n > $OUT/I-pico-$n.txt 2>&1
    t=0; until lsusb -d $vp >/dev/null 2>&1 || [ $t -ge 15 ]; do sleep 1; t=$((t+1)); done; sleep 1
    bcd=$(lsusb -v -d $vp 2>/dev/null | awk '/bcdDevice/{print $2; exit}')
    echo "I pset $n vp=$vp bcd=${bcd:-none} host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt
    [ "$bcd" = "1.0$n" ] || { echo "I pset $n WRONG_IDENTITY" >> $OUT/marks.txt; cue "UNPICO $n: unplug P-1 from the test PC"; return 1; }
  fi
  cue "UNPICO $n: unplug P-1 from the test PC. Keep it unplugged until the next cue"
  t=0; while lsusb -d $vp >/dev/null 2>&1 && [ $t -lt 60 ]; do sleep 2; t=$((t+2)); done; return 0; }
# p1run RUN N WAIT [scan|btn]: one graded plug of P-1 (identity N) into D-HP, one retry after NO_PLUG (the stop rule).
# scan: ACTION_CHECK_USB at the end, for a verdict line. btn: one tap on the USB button, then the list row is read.
p1run() { local run=$1 n=$2 w=$3 x=${4:-} k=0 bad=0 ok=0 L r
  case $run in rp4|bl3) r="$BLRULE";; *) r="$P1RULE";; esac
  while [ $ok = 0 ] && [ $bad -lt 2 ]; do k=$((k+1)); L=I-$run-p$k; TAPN=0
    [ "$(bat)" -ge $BSTOP ] || { echo "$L BATTERY_LOW $(bat)" >> $OUT/marks.txt; return 2; }
    alive; RULE="$r"; cue "NEXT $L: read the rule, nothing starts for 12 s"; sleep 12
    mark $L-cue; cue "PLUG $L: plug P-1 into the OTG adapter on D-HP now"
    if waitany $L-cue 'USB Intent: .*USB_DEVICE_ATTACHED' 120; then ok=1
      echo "$L plug=$(since $L-cue | grep -aE -m1 'USB Intent: .*USB_DEVICE_ATTACHED' | cut -c1-18)" >> $OUT/marks.txt
    else echo "$L NO_PLUG" >> $OUT/marks.txt; bad=$((bad+1)); fi
    if [ $ok = 1 ]; then
      sleep $w; mark $L-x                     # $w s: a dialog, a switch, the 2 s fallback or a 10 s timeout lands in these
      case $x in
        scan) send ACTION_CHECK_USB >> $OUT/marks.txt; sleep 8 ;;
        btn) tapnode ':id/usb_button'; sleep 3; dump $OUT/$L-list.xml
             echo "$L list_text=$(python3 -I ./node.py $OUT/$L-list.xml 'android:id/button1' '?') list_state=$(python3 -I ./node.py $OUT/$L-list.xml 'android:id/button1' '')" >> $OUT/marks.txt
             A shell input keyevent 4; home ;;
      esac
    fi
    mark $L-end; cue "UNPLUG $L: unplug P-1 from D-HP"
    waitfor $L-end 'USB_DEVICE_DETACHED' 60 && echo "$L unplug=$(firstts $L-end 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt
    mark $L-unplug; RULE=
    if [ $ok = 1 ]; then
      cue "COUNT $L: tell the host how many USB dialogs you saw since the plug and the first word of each (25 s)"; sleep 25
      ./p1.sh $(F) $L $L-cue $L-unplug "${P1VID[$n]}" >> $OUT/p1.tsv; ./bl.sh $(F) $L $L-cue $L-unplug >> $OUT/bl.tsv
    fi
  done
  [ $bad -ge 2 ] && cue "RESEAT I-$run: reseat the OTG adapter in D-HP's port"
  echo "I-$run graded=$ok retries=$bad" >> $OUT/marks.txt; return 0; }
```

**`stageP.sh`**
```bash
#!/usr/bin/env bash
# ST=P ./stageP.sh: stage 1 on D-POCO (F, then N), then ORD (optional).
ST=P; . ./lib5.sh; . ./wireless.env; . ./s1.sh
HU=$POCO_W; HUNAME=D-POCO; export ST HU HUNAME; RULE=; T0=$(date +%s)
cue "CAB P: put the OTG adapter in D-POCO's port and take D-POCO and D-MOTO off their PC cables. D-MOTO stays unplugged"
adb connect $HU >/dev/null; adb connect $MOTO_W >/dev/null
[ "$(bat)" -ge $BSTART ] || { echo "P BATTERY_START $(bat)" >> $OUT/marks.txt; exit 1; }
th_gate || exit 1
echo "P stay_on_before=$(A shell settings get global stay_on_while_plugged_in | tr -d '\r')" >> $OUT/marks.txt
A shell svc power stayon true; A shell am force-stop $PKG
S0=P-start-$(date +%H%M%S); cap; mark $S0; launch; sleep 3
[ "$(since $S0 | grep -aF 'START u0' | grep -acF 'MainActivity')" -lt 1 ] && { echo "P NO_START_LINES: CREATE=1" >> $OUT/marks.txt; export CREATE=1; }
arm F; [ $? -eq 2 ] && exit 2
arm N; [ $? -eq 2 ] && exit 2
# ORD (optional): the General screen's row order, if stage P has run less than 40 min
if [ $(( $(date +%s) - T0 )) -lt 2400 ]; then
  launch; cue "SCREEN P-ord: on D-POCO press Settings. Scroll by hand until both \"Use Native USB Driver\" and \"Detect plugged-in phones/AA dongles\" are on screen. Change nothing"
  x=$OUT/P-ord-gen-ui.xml; waitdump $x 'Detect plugged-in phones/AA dongles' || echo "P-ord GENERAL_NOT_REACHED" >> $OUT/marks.txt
  sleep 6; dump $x; A shell screencap -p /sdcard/ohu-ui.png; A pull /sdcard/ohu-ui.png $OUT/P-ord-gen-ui.png >/dev/null 2>&1
  python3 -I ./ui.py $x P-ord-gen | tee -a $OUT/ui.tsv
  cue "SCREEN P-ord done: hands off D-POCO"; home; launch; home
else echo "P ORD skipped (time)" >> $OUT/marks.txt; fi
cleardef P-end-clear; usbdef P-end; mark P-end
send ACTION_EXPORT_LOG; sleep 5                    # the export path is on the data= line; pull that file
th_end; uncap; xrestore $OUT/backup-P.xml
A shell settings put global stay_on_while_plugged_in "$(sed -n 's/^P stay_on_before=//p' $OUT/marks.txt | head -1)"
```

**`stageH.sh`**
```bash
#!/usr/bin/env bash
# ST=H ./stageH.sh: stage 1 on D-HP (N, then F), then stage 2 with D-MOTO (its control is S1-H-F) with S2-P1-M3
# between S2-M-bl and S2-M-re, then B8 (optional).
ST=H; . ./lib5.sh; . ./wireless.env; . ./s1.sh; . ./blrun.sh; . ./m3.sh
HU=$HP_W; HUNAME=D-HP; export ST HU HUNAME; RULE=; T0=$(date +%s)
cue "CAB H: put the OTG adapter in D-HP's port and take D-HP and D-MOTO off their PC cables. D-MOTO stays unplugged"
adb connect $HU >/dev/null; adb connect $MOTO_W >/dev/null
[ "$(bat)" -ge $BSTART ] || { echo "H BATTERY_START $(bat)" >> $OUT/marks.txt; exit 1; }
th_gate || exit 1
echo "H stay_on_before=$(A shell settings get global stay_on_while_plugged_in | tr -d '\r')" >> $OUT/marks.txt
A shell svc power stayon true; A shell am force-stop $PKG
S0=H-start-$(date +%H%M%S); cap; mark $S0; launch; sleep 3
[ "$(since $S0 | grep -aF 'START u0' | grep -acF 'MainActivity')" -lt 1 ] && { echo "H NO_START_LINES: CREATE=1" >> $OUT/marks.txt; export CREATE=1; }
arm N; [ $? -eq 2 ] && exit 2
KEEPOFF=1 arm F; [ $? -eq 2 ] && exit 2
# stage 2, D-MOTO in File transfer with debugging off. Its control is S1-H-F.
if keyof $(plugs H-F | awk '{print $1}'); then
  blrun M "$KEY" "$VP" "D-MOTO"
  m3                                    # S2-P1-M3: the 18d1:2d00 key against D-MOTO's own accessory identity
  xprefs set:usb-blacklist= bool:auto-connect-single-usb=false && { launch; sleep 3; RULES="$S1RULE"; run5 S2Mre "session"; }
  for P in $(plugs H-S2Mre); do ./x1.sh $(F) $P 'VID: 22B8' >> $OUT/x1.tsv; ./bl.sh $(F) $P $P-cue $P-unplug >> $OUT/bl.tsv; done
else echo "H S2-M and S2-P1-M3 UNTESTABLE (no identity from S1-H-F)" >> $OUT/marks.txt; fi
# B8 (optional): "Connect when plugged in" off, Home's auto-connect on, then a cold start with the phone present
if [ $(( $(date +%s) - T0 )) -lt 3300 ] && xprefs bool:usb-connect-on-plug-in=false bool:auto-connect-single-usb=true; then
  launch; sleep 3; home; P=H-B8-p1; RULE="$S1RULE"; cue "NEXT $P: read the rule, nothing starts for 12 s"; sleep 12
  mark $P-cue; cue "PLUG $P: plug D-MOTO's cable into the OTG adapter on D-HP now"
  waitfor $P-cue 'Normal USB device attached: ' 120 && echo "$P plug=$(firstts $P-cue 'Normal USB device attached: ')" >> $OUT/marks.txt
  sleep 20; mark $P-cold; A shell am force-stop $PKG; launch
  waitfor $P-cold 'HomeFragment: Requesting single-USB auto-connect via AapService' 30 || echo "$P NO_HOMEAUTO" >> $OUT/marks.txt
  { waitfor $P-cold 'SSL handshake complete' 120 && waitfor $P-cold 'Media Sink Setup Request: ' 30; } || echo "$P NO_SESSION" >> $OUT/marks.txt
  sleep 10; mark $P-end; cue "UNPLUG $P: unplug D-MOTO from D-HP"
  waitfor $P-end 'USB_DEVICE_DETACHED' 60 && echo "$P unplug=$(firstts $P-end 'USB_DEVICE_DETACHED')" >> $OUT/marks.txt
  mark $P-unplug; RULE=; cue "COUNT $P: how many USB dialogs, and the first word of each (25 s)"; sleep 25
  for h in pre:$P-cue:$P-cold post:$P-cold:$P-unplug; do
    ./timeline.sh $(F) $P/${h%%:*} $(echo $h | cut -d: -f2) $(echo $h | cut -d: -f3) > $OUT/$P-${h%%:*}.timeline.tsv
    printf '%s\t%s\n' "$P/${h%%:*}" "$(tail -1 $OUT/$P-${h%%:*}.timeline.tsv | cut -f3-)" >> $OUT/cycles.tsv; done
else echo "H B8 skipped" >> $OUT/marks.txt; fi
# the phone: one dump for S1-H-F, S2-M and B8
dbgon H-F
python3 -I ./phslice.py $OUT/H-F-arm-phone.txt $OUT/marks.txt $OUT $(plugs H-F) H-S2M $(plugs H-P1M3) $(plugs H-S2Mre) $(plugs H-B8) >> $OUT/phone.tsv
mark H-end; send ACTION_EXPORT_LOG; sleep 5; th_end; uncap
# D-HP's settings are restored at the end of stage D
```

**`stageD.sh`**
```bash
#!/usr/bin/env bash
# ST=D ./stageD.sh: stage 2 with D-DONGLE on D-HP; D-MOTO on its PC cable is the dongle's phone. WITHBOOT=1 runs BOOT.
ST=D; . ./lib5.sh; . ./wireless.env; . ./blrun.sh
HU=$HP_W; HUNAME=D-HP; MOTO_W=$MOTO; export ST HU HUNAME MOTO_W; RULE=
cue "CAB D: take D-MOTO off D-HP's OTG adapter and plug it into its PC cable. The dongle stays unplugged"
timeout 120 adb -s $MOTO wait-for-device || { echo "D NO_MOTO_CABLE" >> $OUT/marks.txt; exit 1; }
adb -s $MOTO shell svc wifi enable; adb -s $MOTO shell svc bluetooth enable; sleep 5
echo "D moto bt=$(adb -s $MOTO shell dumpsys bluetooth_manager | tr -d '\r' | grep -a -m1 'enabled:') bonded_dongle=$(adb -s $MOTO shell dumpsys bluetooth_manager | grep -acF 'carplay_box_F96B')" >> $OUT/marks.txt
adb connect $HU >/dev/null
[ "$(bat)" -ge $BSTART ] || { echo "D BATTERY_START $(bat)" >> $OUT/marks.txt; exit 1; }
th_gate || exit 1
A shell am force-stop $PKG; S0=D-start-$(date +%H%M%S); cap; mark $S0; launch; sleep 3
cleardef D-ctl-clear
setkeys s1ctl $S1KEYS || { echo "D UNTESTABLE settings" >> $OUT/marks.txt; exit 1; }
RULES="$S1RULE"; run5 ctl "session"; [ $? -eq 2 ] && exit 2
for P in $(plugs D-ctl); do ./x1.sh $(F) $P 'VID: 18D1 PID: 4EE1' >> $OUT/x1.tsv; phrow $P $OUT/$P-phone.txt; done
if keyof $(plugs D-ctl | awk '{print $1}'); then blrun D "$KEY" "$VP" "the dongle"
else echo "D S2-D UNTESTABLE (no identity from S2-D-ctl)" >> $OUT/marks.txt; fi
if [ "${WITHBOOT:-0}" = 1 ] && xprefs bool:auto-start-on-usb=true bool:usb-connect-on-plug-in=true bool:auto-connect-single-usb=false; then
  launch; sleep 3; home; RULE="$BLRULE"; mark D-BOOT-cue; cue "PLUG D-BOOT: plug the dongle into the OTG adapter on D-HP now"
  waitfor D-BOOT-cue 'Ignoring USB device attached in service (' 120 || echo "D-BOOT NO_PLUG" >> $OUT/marks.txt
  send ACTION_STOP_SERVICE >> $OUT/marks.txt; sleep 3   # never force-stop here: a stopped package gets no BOOT_COMPLETED
  echo "D-BOOT before: $(A shell dumpsys package $PKG | tr -d '\r' | grep -ao -m1 'stopped=[a-z]*')" >> $OUT/marks.txt
  th_end; uncap
  cue "REBOOT D-BOOT: restart D-HP from its power menu now. Leave the dongle plugged in"
  t=0; while [ "$(adb -s $HU get-state 2>/dev/null)" = device ] && [ $t -lt 240 ]; do sleep 2; t=$((t+2)); done
  echo "D-BOOT down after ${t}s host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt
  sleep 180                                   # boot (about 90 s) and 90 s of the app's own start: the time is what BOOT measures
  cue "CAB D-BOOT: unplug the dongle from D-HP, then plug D-HP into its PC cable"
  if timeout 300 adb -s $HP wait-for-device; then
    adb -s $HP logcat -d -v time -b main -b system -b events > $OUT/D-boot-hu.txt
    ./boot.sh $OUT/D-boot-hu.txt D-BOOT | tee -a $OUT/boot.tsv; ./bl.sh $OUT/D-boot-hu.txt D-BOOT - >> $OUT/bl.tsv
    adb -s $HP tcpip 5555 >/dev/null; sleep 5; adb connect $HP_W >/dev/null
  else echo "D-BOOT NO_BOOT" >> $OUT/marks.txt; fi
  cue "CAB D-BOOT done: unplug D-HP from its PC cable. Leave the OTG adapter in D-HP's port"; sleep 20
  adb connect $HU >/dev/null; th_gate || exit 1; cap; mark D-after-boot-$(date +%H%M%S); RULE=
fi
xprefs set:usb-blacklist= bool:auto-start-on-usb=false bool:auto-connect-single-usb=false && { launch; sleep 3
  RULES="$S1RULE"; run5 re "session"
  for P in $(plugs D-re); do ./x1.sh $(F) $P 'VID: 18D1 PID: 4EE1' >> $OUT/x1.tsv; ./bl.sh $(F) $P $P-cue $P-unplug >> $OUT/bl.tsv; phrow $P $OUT/$P-phone.txt; done; }
cleardef D-end-clear; usbdef D-end; mark D-end
send ACTION_EXPORT_LOG; sleep 5; th_end; uncap; xrestore $OUT/backup-H.xml
A shell settings put global stay_on_while_plugged_in "$(sed -n 's/^H stay_on_before=//p' $OUT/marks.txt | head -1)"
```

**`stageI.sh`**
```bash
#!/usr/bin/env bash
# ST=I ./stageI.sh: S2-P1 on D-HP with P-1. Needs PICO_OK=1 in pico.env. WITHP1OPT=1 also runs I2 and I5.
ST=I; . ./lib5.sh; . ./wireless.env; . ./pico.env; . ./blrun.sh; . ./pico.sh
HU=$HP_W; HUNAME=D-HP; MOTO_W=none; NOPHONE=1; export ST HU HUNAME MOTO_W NOPHONE; RULE=
[ "${PICO_OK:-0}" = 1 ] || { echo "I S2-P1 UNTESTABLE (P-1 not set up)" >> $OUT/marks.txt; exit 0; }
cue "CAB I: leave D-POCO and D-MOTO on their PC cables and the OTG adapter in D-HP's port, with nothing in it. P-1 stays unplugged"
adb connect $HU >/dev/null
[ "$(bat)" -ge $BSTART ] || { echo "I BATTERY_START $(bat)" >> $OUT/marks.txt; exit 1; }
th_gate || exit 1
echo "I stay_on_before=$(A shell settings get global stay_on_while_plugged_in | tr -d '\r')" >> $OUT/marks.txt
A shell svc power stayon true
blread I-before                                     # the blacklist before P-1 adds anything
A shell am force-stop $PKG; S0=I-start-$(date +%H%M%S); cap; mark $S0
cleardef I-clear
setkeys sI $S1KEYS || { echo "I UNTESTABLE settings" >> $OUT/marks.txt; exit 1; }
launch; sleep 3; home
# S2-P1-I1: the stock CCPA identity (1314:1520, EF/02/01, FF/F0/00). I2 (optional): its NCM mode.
if pset 1; then p1run c1 1 20 scan; [ $? -eq 2 ] && exit 2; else echo "I S2-P1-I1 UNTESTABLE (pset)" >> $OUT/marks.txt; fi
if [ "${WITHP1OPT:-0}" = 1 ]; then
  if pset 2; then p1run c2 2 20 scan; [ $? -eq 2 ] && exit 2; else echo "I S2-P1-I2 UNTESTABLE (pset)" >> $OUT/marks.txt; fi; fi
# S2-P1-I4: the nameless FF/FF/00 device (1314:1521). The control, the blacklist run, then a replug with the app's own key.
if pset 4; then
  p1run c4 4 30; [ $? -eq 2 ] && exit 2
  if keyof $(plugs I-c4 | awk '{print $1}'); then
    blrun P4 "$KEY" "$VP" "P-1"
    if xprefs bool:auto-connect-single-usb=false; then
      echo "I-rp4 stored_key=$(grep -acF "<string>$KEY</string>" $OUT/$ST-settings-now.xml)" >> $OUT/marks.txt
      launch; sleep 3; home; p1run rp4 4 25; [ $? -eq 2 ] && exit 2
    else echo "I S2-P1-I4rp UNTESTABLE settings" >> $OUT/marks.txt; fi
  else echo "I S2-P1-I4bl and I4rp UNTESTABLE (no identity from S2-P1-I4)" >> $OUT/marks.txt; fi
else echo "I S2-P1-I4, I4bl and I4rp UNTESTABLE (pset)" >> $OUT/marks.txt; fi
xprefs set:usb-blacklist= bool:auto-connect-single-usb=false; blread I-mid
# S2-P1-I5 (optional): the AOA switcher (1314:1521, answers AOA, comes back as 18D1:2D00)
if [ "${WITHP1OPT:-0}" = 1 ]; then
  if pset 5; then launch; sleep 3; home; p1run c5 5 40; [ $? -eq 2 ] && exit 2
  else echo "I S2-P1-I5 UNTESTABLE (pset)" >> $OUT/marks.txt; fi; fi
# S2-P1-I3: the AOA accessory (18D1:2D00). The control, then blacklisted by vidpid:18d1:2d00.
if pset 3; then
  launch; sleep 3; home; p1run c3 3 40; [ $? -eq 2 ] && exit 2
  if xprefs set:usb-blacklist=vidpid:18d1:2d00 bool:auto-connect-single-usb=false; then
    echo "I-bl3 stored_key=$(grep -acF '<string>vidpid:18d1:2d00</string>' $OUT/$ST-settings-now.xml)" >> $OUT/marks.txt
    launch; sleep 3; home; p1run bl3 3 25 btn; [ $? -eq 2 ] && exit 2
  else echo "I S2-P1-I3bl UNTESTABLE settings" >> $OUT/marks.txt; fi
else echo "I S2-P1-I3 and I3bl UNTESTABLE (pset)" >> $OUT/marks.txt; fi
# clean-up: take out every key P-1 added, P-1 back to its console, then the Prepare backup
xprefs set:usb-blacklist= bool:auto-connect-single-usb=false; blread I-after
pset 0 || echo "I P-1 NOT_ON_CONSOLE" >> $OUT/marks.txt
cleardef I-end-clear; usbdef I-end; mark I-end
send ACTION_EXPORT_LOG; sleep 5; th_end; uncap; xrestore $OUT/backup-H.xml; blread I-restored
A shell settings put global stay_on_while_plugged_in "$(sed -n 's/^I stay_on_before=//p' $OUT/marks.txt | head -1)"
```

## 7. The deciding lines

All app strings print at INFO, except the `UsbDiagnostics:   ` verdict line of an accepted device (VERBOSE) and the
header ` usable for Android Auto` when 1 or more is usable (VERBOSE). Each was checked with `grep -F -r` in
`app/src/main` at `4a603930`. "Composed" marks a line whose parts the source joins at run time; each part was checked.
Stage 1 and the control cycles are graded in `<ST>-hu.txt` through `timeline.sh` and `x1.sh` (window `<P>-cue` to
`<P>-unplug`). S2-bl and S2-P1-I4bl are graded through `bl.sh` in their five windows (section 6). Every other S2-P1
plug is graded through `p1.sh` and `bl.sh` in the window `<L>-cue` to `<L>-unplug`, in `H-hu.txt` (S2-P1-M3) or
`I-hu.txt`. BOOT is graded in `D-boot-hu.txt`, the
whole file, through `boot.sh` and `bl.sh`. Phone lines are graded in the `<label>-phone.txt` slices.

| Id | Line (fixed substring) | Read |
|---|---|---|
| L-RX, L-MK | `AutomationReceiver: `, `AutomationMarker: ` | a verb landed (no line: the step is void); the window bounds |
| L-NORM | `Normal USB device attached: ` | identity A seen by the service; it carries `VID: xxxx PID: yyyy` (composed) |
| L-ACC | `USB accessory device attached, connecting.` | identity B: the phone switched |
| L-DIAG | `UsbDiagnostics:   ` then the device, `[class ..., permission] ` or `[class ..., no permission] `, the verdict (`accepted: ` or `rejected: `), then the interface triples, `if0 FF/FF/00 (...)` (composed) | **the identity and the app's verdict.** Reasons this round expects: `accepted: MTP`, `accepted: ADB`, `accepted: AOAP (vendor interface named ` ... `)`, `rejected: blacklisted by the user` |
| L-DIAG0 | `UsbDiagnostics: ` caller ` sees ` N ` USB device(s), ` 0 ` usable for Android Auto` (composed) | a scan that found nothing to use; the caller names the path |
| L-REFALL | `UsbDiagnostics: everything on the bus was refused` | information |
| G-IGNSVC | `Ignoring USB device attached in service (` ... `): rejected: blacklisted by the user` (composed) | **the service refused a blacklisted device at the plug** |
| G-IGNACT | `Ignored blacklisted USB device` (`UsbAttachedActivity: Ignored blacklisted USB device (` and `... in onNewIntent (`) | **the attach screen ignored it** |
| G-HOME | `HomeFragment: Requesting single-USB auto-connect via AapService` | Home's auto-connect fired |
| G-HOME0 | `single-USB auto-connect found 0 connectable devices` (full: `HomeFragment: single-USB auto-connect found 0 connectable devices, so no loading screen`, composed) | **Home's auto-connect saw no usable device** |
| G-BTN | `USB button: Single device found - ` | the USB button picked a device. Must be 0 while blacklisted |
| G-SW | `Switching USB device to accessory mode `, `Switching to accessory mode.`, `Successfully requested switch to accessory mode for `, `UsbAccessoryMode: Performing AOA switch` | a switch started |
| P-WHY | `Ignoring USB device attached in service (` then `VID: 0x%04X, PID: 0x%04X` (`Settings.formatUsbVidPidDisplay`), `): `, then the reason (composed). Reasons S2-P1 expects: `rejected: no Android interface` (identities 1 and 2), `rejected: blacklisted by the user` | **the service refused the device, and why** |
| P-VERD | the L-DIAG verdicts `accepted: already in accessory mode` (identity 3), `accepted: AOAP (vendor interface named nothing)` (identities 4 and 5), `rejected: no Android interface` (composed: `accepted: ` or `rejected: ` from `Verdict.toString`, the reason from `UsbDeviceIdentityPolicy.evaluate`) | the app's verdict on a P-1 identity |
| P-NONAND | `Ignoring non-Android USB device in onCreate (VID: ` | the attach screen refused a device the policy rejects (not expected: the filter keeps it away) |
| P-SWFAIL | `UsbAccessoryMode: AOA switch failed: ` (one string joined from three literals), after `Error controlTransfer len: ` | **the AOA switch stalled** (identity 4) |
| P-ACCST | `Acc start sent (len=` | the switch reached AOA request 53 (identity 5) |
| P-FOUND | `Found device already in accessory mode: ` | the scan found an accessory-mode device to connect |
| P-VTO | `Handshake: Version exchange timed out after ` | the session timed out on a device that never answers on bulk (identities 3 and 5) |
| L-DEC | `UsbLauncher: permission for ` | one decision; fields (composed) `identity=`, `mtp=`, `listen=`, `routeSeen=`, `source=`, `action=` |
| L-REQ | `Requesting USB permission for ` | every request of ours |
| L-INT | `USB Intent: ` | with `USB_DEVICE_ATTACHED` and `UsbAttachedActivity` on one line: Android started our attach screen. With `USB_DEVICE_DETACHED`: the unplug |
| L-OPEN | `UsbAttachedActivity: Connect when plugged in is off; opening the app, not switching ` | B8's plug |
| L-NOSW | `UsbLauncher: not switching ` ... ` on a plug-in: ` | information |
| L-SSL, L-MS | `SSL handshake complete`, `Media Sink Setup Request: ` | a session formed; the phone answered |
| B-* | `Boot auto-start: received action=`, `Boot auto-start: USB auto-start enabled, starting AapService to check USB (trigger=`, `AapService creating...`, `Boot auto-start: starting AapService with BOOT_START`, `Boot auto-start: screen-on auto-start enabled` | BOOT, through round 4's `boot.sh` |
| D-MATCH | `MATCH! Starting AapService` | discard rule (template §4) |

System strings, not in `app/src`: `START u0`, `.usb.UsbConfirmActivity`, `.usb.UsbPermissionActivity`,
`.usb.UsbResolverActivity`, `_finish_activity`, `notification_enqueue`, `com.android.mtp`, `Device preferences`,
`device_preferences`, `stopped=`, `screen_unlocked_functions` (read case-blind with a gap allowed), and the dump node
attributes `resource-id`, `text`, `enabled`, `bounds`. Phone strings, on D-MOTO: `entering USB accessory mode` (AOSP),
`GH.CarConnSession` (Android Auto, information only), `RIGMARK`. Test-PC strings, for P-1: `bcdDevice` in `lsusb -v`
(firmware: `1.0N` for identity N), `stored identity: 0` in `console.py i` (firmware `console_info`), and
`ID_VENDOR_ID=2e8a` from `udevadm`. **FAIL lines, every window:** `FATAL EXCEPTION` in a
block that names `com.andrerinas`, `ANR in com.andrerinas`, `NoClassDefFoundError`.

The files the runs write:

| File | One row per | Fields |
|---|---|---|
| `cycles.tsv` | plug (and `H-B8-p1/pre`, `/post`) | round 4's `row`: label, `pid=`, `phone_acc=` (empty in stage 1 and H, see `phone.tsv`), `after=`, the SUMMARY fields |
| `x1.tsv` | plug | `route_A`, `route_B`, `mtpn`, `vp`, `diag` |
| `phone.tsv` | plug or S2-bl run | `phone_lines`, `phone_acc`, `gh_connsession` |
| `bl.tsv` | S2-bl window, S2-re plug, BOOT, S2-P1 plug and S2-P1-I4bl window | the `bl.sh` fields |
| `p1.tsv` | S2-P1 plug (`H-P1M3-p1`, `I-<run>-p<k>`) | the `p1.sh` fields |
| `boot.tsv` | BOOT | round 4's `boot.sh` counts |
| `defaults.tsv` | `dumpsys usb` read | tag, `defaults=`, `pairs=`, `sections=`, `serial_outside=` |
| `ui.tsv` | ORD dump | round 4's `ui.py` row |
| `marks.txt` | event | markers, `plug=`, `unplug=`, `usbmode`, `debugging off`, `adb_enabled`, `stored_key`, `after_tap_on_*`, `list0`, `list3`, `list_text`, `list_state`, `blacklist=`, `pset`, taps, voids, identity lines |

## 8. Runs

Cycle labels are `<ST>-<run>-p<k>`; S2-bl labels are `H-S2M`, `D-S2D` and `I-S2P4`. S2-P1 labels are `H-P1M3-p1` and
`I-<run>-p<k>`, with `<run>` one of `c1`, `c2`, `c4`, `rp4`, `c5`, `c3`, `bl3`. No label runs twice.

**Stop rule.** A cycle with `NO_PLUG` is retried once with the next `k` (round 4's `runplugs`). Two in one run end the
run, graded on what it has (INCONCLUSIVE with 0 graded cycles). A cycle with `NO_SESSION` is graded, never retried. An
S2-bl step with `NO_PLUG`, `NO_HOMEAUTO` or `TAP_VOID` is recorded and the run goes on. `BATTERY_LOW` ends the stage,
and the runs not reached are UNTESTABLE (battery). A failed `setkeys`, `xprefs` or `usbmode` makes the runs under it
UNTESTABLE (settings or mode). ORD runs only if stage P is under 40 min; B8 only if stage H is under 55 min; BOOT only
with `WITHBOOT=1`, which the executor sets if the round is under 1 h 40 min when stage D starts. S2-P1-I2 and S2-P1-I5
run only with `WITHP1OPT=1`, which the executor sets if the round is under 2 h 30 min when stage I starts. A failed
`pset` makes the runs on that identity UNTESTABLE (pset); quote its `I pset` line.

### Prepare (steps 1 to 6 beside the build, then 10 min)

1. Print the cue path: `. ./lib5.sh; echo "tail -n0 -F $OUT/cues.log"`. Give the operator that line exactly.
2. `$RIG_TOOLKIT/rig_preflight.sh D_HP:wifi D_POCO:wifi D_MOTO:wifi`. Paste the table into Setup notes. Charge D-HP to
   100%.
3. D-MOTO, on its PC cable:
   1. `svc bluetooth disable`, read it back. WiFi stays on.
   2. Record `settings get global stay_on_while_plugged_in` and `settings get system screen_off_timeout`. Then
      `svc power stayon true` and `settings put system screen_off_timeout 1800000`.
   3. Record Gearhead's `versionName`, `getprop sys.usb.config`, and `svc usb 2>&1 | grep -c setScreenUnlockedFunctions`.
   4. Record the default USB mode: `dumpsys usb | grep -aiE 'screen.?unlocked.?functions'`.
   5. `logcat -G 16M`. Confirm the lock state with the operator (UNLOCK), once.
4. Wireless adb for D-HP, D-POCO and D-MOTO, each still on its PC cable: `adb -s <serial> tcpip 5555`, read
   `ip -4 addr show wlan0`, `adb connect <ip>:5555`. Write `HP_W`, `POCO_W` and `MOTO_W` to `$OUT/scripts/wireless.env`.
5. D-POCO: `am start -a android.intent.action.VIEW -d headunit://exit`, wait 3 s, then `am force-stop` its app
   (template §8 role-swap rule). Battery 50% or more, else charge it first.
6. On D-HP and D-POCO:
   1. Save `dumpsys usb > $OUT/prep-<unit>-usb.txt` and run `./usbparse.sh` on it. Record `defaults=` and
      `serial_outside=` (D-MOTO is unplugged).
   2. Record `getprop ro.build.version.sdk`, and `date` beside the host's `date`. Do the same on D-MOTO.
   3. From `run-as $PKG cat shared_prefs/settings.xml`, record `usb-blacklist`, `enable-car-launcher` and
      `allow-devices`. On D-POCO also record `usb-attach-route-boot` and `settings get global boot_count`.
7. After the build, on each host (`ST=P HU=$POCO`, then `ST=H HU=$HP`, with `. ./lib5.sh`):
   1. `A shell am force-stop $PKG`, then `A shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > $OUT/backup-$ST.xml`.
   2. `A install -r -d $OUT/apk-C.apk`, `launch`, `sleep 5`, `ident $ST-new $CAND`. `marks.txt` MUST read
      `commit_4a603930=1` (or more) and an `md5_live` equal to `apk-C.apk`, else stop: UNTESTABLE (build).
   3. `A push xpcp.sh /data/local/tmp/` and `A shell chmod 755 /data/local/tmp/xpcp.sh`.
8. `xprefs` self-test on D-HP, app stopped: `ST=H HU=$HP; xprefs int:auto-connect-delay-seconds=0`. `marks.txt` MUST
   read `H xprefs ... onboarding=1`, and a `launch` MUST show the home screen, not the setup wizard (`top` names
   `MainActivity`). Else stop: UNTESTABLE (settings writer), and restore `backup-H.xml` with `xrestore`.
9. `chmod +x usbparse.sh timeline.sh boot.sh x1.sh bl.sh p1.sh stageP.sh stageH.sh stageD.sh stageI.sh` in
   `$OUT/scripts/`.
10. The P-1 check, for stage I:
    1. Open `pico-usb-stand-in-round1-results.md` on this branch. Record the verdicts of R1 and R3.
    2. If the file is missing, or R1 or R3 is not PASS: `echo PICO_OK=0 > $OUT/scripts/pico.env`. Stop here.
    3. Else read the P-1 entry in `rig-toolkit/TOOLS.md` (round 1's R4). Write `PICO_OK=1` and
       `PICO=<the folder that holds console.py>` to `$OUT/scripts/pico.env`, one per line.
    4. Run `id -nG | grep -cw dialout`. It MUST print 1, else write `PICO_OK=0` and record why.
    5. Record R2's verdict. If R2 failed, BOOTSEL does the job of KEY at every HOLD cue: tell the operator once.
    6. Record which adapter joins P-1 to D-HP (section 3).

Then run `./stageP.sh`, then `./stageH.sh`, then `WITHBOOT=<0|1> ./stageD.sh`, then `WITHP1OPT=<0|1> ./stageI.sh`.

Grade each run from `cycles.tsv`, `x1.tsv`, `phone.tsv`, `bl.tsv`, `p1.tsv`, `boot.tsv`, `ui.tsv` and `marks.txt`. Quote every
`dec_A`, `dec_B`, `mtp_A`, `rs` and `diag` value. The operator's COUNT goes beside `dlg_all` as a second column; a
disagreement is a finding, not a verdict. "Android's dialogs" are `confirm_A + confirm_B + confirm_x + resolver`;
"ours" are `perm_A + perm_B + perm_x`.

### Stage 1: the first offer of a phone in file transfer, and the "No data transfer" lever

Each arm: `usbmode`, CLEAR if a default is stored, `setkeys s1<R>`, D-MOTO's debugging off, three plugs with a session
each, then DEBUG-ON and one phone dump (S1-H-F keeps debugging off for stage 2). Rule: tick every box, then OK.

Setup checks per arm: the `usbmode` line reads `rc=0` (or `rc=2`, see below), and `<ST>-<R>-clear-post` reads
`defaults=0`. `rc=2` means `dumpsys usb` has no such field: the arm's mode is then decided by `x1.tsv` alone (F needs
`accepted: MTP` on D-POCO; on D-HP no name is readable, and the `vp` must differ between the arms or match the sweep's
`22B8:2E82`). Record `debugging off` and `adb_enabled=1` after DEBUG-ON.

#### S1-P-N and S1-H-N, "No data transfer": Android offers the app once and the tick sticks. The point of the round

S1-N PASS needs, per host:

1. Plug 1: `dlg_all` at least 1.
2. Plugs 2 and 3: `dlg_all=0`, `req_A=0`, `req_B=0`.
3. Every plug: `sw_lines` at least 1, `attach_B` at least 1, `ssl` at least 1, `ms` at least 1, `fatal=0`. Phone
   (`phone.tsv`): `phone_acc` at least 1.
4. D-POCO only: `mtpn=0` on every plug, and plug 1's `diag` is not `accepted: MTP`.
5. The operator's COUNT reads 1 or more, then 0, then 0.

Record per plug: `confirm_A`, `confirm_B`, `perm_A`, `perm_B`, `dec_A`, `mtp_A`, `rs`, `route_A`, `route_B`, `vp`,
`diag`, and `after=`.

S1-N FAIL if item 2 or 3 fails on a graded plug, or item 4 fails. Read a FAIL here as a result about the lever on this
phone, not as a code defect: say which item failed. S1-N INCONCLUSIVE if plug 1 reads `dlg_all=0` (a stored default or
grant hid the first offer; quote `serial_outside`).

**If the lever did nothing,** D-MOTO would still be an MTP device. S1-P-N would then read as S1-P-F: `mtpn` at least 1,
`route_A=0` and our dialog on plug 1. Compare the two arms' `diag` and `vp`. If they are equal, the lever changed nothing
on this phone, and S1-P-N's counts say what API 35 does with any tick.

#### S1-P-F, "File transfer" on API 35: the first offer of an MTP phone (a measurement)

S1-P-F records what the report's path looks like on API 35. It has no expected dialog count.

S1-P-F PASS needs the MTP path reached on plug 1: `diag` reads `accepted: MTP` or `mtp_A` holds `true`, `route_A=0`,
and `mtpn` at least 1. Then record per plug: `dlg_all`, Android's and ours, `req_A`, `req_B`, `dec_A`, `dec_B`,
`route_B`, `mtpn`, `ssl`, `ms`, `phone_acc`, and the COUNT.

S1-P-F FAIL if the MTP path is reached and the app neither asks nor connects: plug 1's `dec_A` holds only `wait`, and
`ssl=0`. That is the defect the first commit removed. S1-P-F INCONCLUSIVE if the MTP path is not reached (`route_A` at
least 1, or `confirm_A` at least 1, or `mtpn=0` with `mtp_A` not `true`): D-MOTO in file transfer is then not the
report's phone, and say so.

#### S1-H-F, "File transfer" on API 17: the pre-API-24 control

API 17 has no MTP notification, so the mode must make no difference. S1-H-F PASS needs items 1, 2, 3 and 5 of S1-N.
S1-H-F FAIL if item 2 or 3 fails. Its plugs are also the control for S2-M.

### Stage 2: the blacklist on API 17

Device M is D-MOTO in file transfer with debugging off (its control is S1-H-F). Device D is D-DONGLE idle (its control
is S2-D-ctl). Each S2-bl run: the key written by `xprefs`, then six steps with the device on the port (section 6,
`blrun.sh`). Rule: "Open": OK, tick nothing; "Allow": Cancel.

#### S2-D-ctl, the dongle not blacklisted (stage D)

One plug after a CLEAR. Rule: tick every box, then OK. Record `diag` (the expected form is
`accepted: AOAP (vendor interface named nothing)`, the closest stand-in for the report's box), `vp`, `dlg_all`, `req_A`,
`req_B`, `dec_A`, `sw_lines`, `ssl`, `ms`, and `gh_connsession` from `phone.tsv`.

S2-D-ctl PASS needs `attach_A` at least 1, `sw_lines` at least 1, `ssl` at least 1, `ms` at least 1, `fatal=0`. If it
fails, S2-D-bl has no control: its "no session" items are INCONCLUSIVE, and its other items still count.

#### S2-M-bl and S2-D-bl, blacklisted by VID:PID: no dialog of ours, no switch, no session on any path. The point of the round

Setup checks: `marks.txt` reads `<L> vp=[VID: xxxx PID: yyyy] key=[vidpid:xxxx:yyyy]` and `<L> stored_key=1`.

S2-bl PASS needs, on the `bl.tsv` rows of the run:

1. `/plug`: `ign_svc` at least 1 (the service saw the device and refused it). `attach_A=0`, `attach_B=0`.
2. `/open`: `diag_bl` at least 1 and `diag0` at least 1.
3. `/auto`: `homeauto` at least 1 and `home0` at least 1.
4. `/btn`: `btn1=0` and `rx_check` at least 1. If `marks.txt` has `tap 1 rid=:id/usb_button`, `list0=Blacklisted` and
   `diag0_by` holds `USB button`.
5. The list (`marks.txt`): `after_tap_on_Blacklisted key=0`, `after_tap_on_Ignored ... allowed=1`,
   `after_tap_on_Allowed key=1 name_keys=0 allowed=0`, and `list3=Blacklisted`. That is the app's own writer storing the
   `vidpid:` form on API 17.
6. `/all`: `ours=0`, `req=0`, `sw_lines=0`, `ssl=0`, `ms=0`, `acc_any=0`, `fatal=0`. Phone: S2-M `phone_acc=0` in
   `phone.tsv` (`H-S2M`); S2-D `gh_connsession=0` (`D-S2D`).
7. The operator's COUNT names no "Allow" dialog.

Record `ign_act`, `android`, `refused_all`, `detach` and `diag0_by` for every window. `ign_act` reads 1 or more when
Android started our attach screen for the device (a stored default), and `android` reads 1 or more when Android showed
its own dialog instead. Either is correct.

S2-bl FAIL if any window shows `ours`, `req`, `sw_lines`, `ssl`, `attach_B` or `acc_any` at 1 or more; or `/plug` shows
`attach_A` at least 1 with `ign_svc=0` (the service did not refuse it); or `btn1` at least 1; or item 5 reads
`name_keys` above 0 or `key=0` after the tap on Allowed. S2-bl items INCONCLUSIVE: item 1 with `NO_PLUG`; item 3 with
`NO_HOMEAUTO`; items 4 and 5 with `TAP_VOID` or `list0` not `Blacklisted` (the tap opened no list; the `xprefs` key and
items 1 to 3 still decide the run).

**If the blacklist did nothing,** the plug would read `attach_A` at least 1 and `ign_svc=0`, then `sw_lines` at least
1 and a session, as in the control. Item 1 and item 6 are the two numbers that separate the states. **S2-M answers the
accessory question:** with D-MOTO blacklisted in file transfer, `acc_any=0` in `/all` and `phone_acc=0` mean that
`18d1:2d00` never arrived, because nothing switched it.

#### S2-M-re and S2-D-re, removed from the blacklist: the device is offered again

After `xprefs set:usb-blacklist=`. One plug. Rule: tick every box, then OK. S2-M-re follows S2-P1-M3, so the key it
removes is `vidpid:18d1:2d00`. Its PASS is also the check that D-MOTO connects again after that key goes.

S2-re PASS needs, on the plug's `bl.tsv` row: `ign_svc=0`, `ign_act=0`, `attach_A` at least 1, `sw_lines` at least 1,
`ssl` at least 1, `ms` at least 1, `fatal=0`; S2-M-re also `phone_acc` at least 1. S2-re FAIL if `ign_svc` or `ign_act`
is at least 1, or `sw_lines=0`.

#### BOOT (optional, stage D), a boot with "Open the app when plugged in" on and the blacklisted dongle plugged in

After S2-D-bl, with the app's own `vidpid:` entry kept. `auto-start-on-usb=true`, `usb-connect-on-plug-in=true`,
detection on. The service is stopped with `ACTION_STOP_SERVICE`, never a force-stop. The operator reboots D-HP by hand.

Setup check: `D-BOOT before: stopped=false`.

BOOT PASS needs: `boot.tsv` `received` at least 1, `usb_branch` at least 1, `creating_after_usb` at least 1,
`boot_branch=0`, `screen_branch=0`; and `bl.tsv` `D-BOOT` `ign_svc + ign_act + diag_bl` at least 1 (the boot saw the
dongle), `ours=0`, `req=0`, `sw_lines=0`, `ssl=0`, `acc_any=0`, `fatal=0`. BOOT FAIL if `sw_lines`, `req`, `ours` or
`ssl` is at least 1. BOOT INCONCLUSIVE if `received=0` (the lines rotated out or the receiver never ran: quote
`stopped=`), or if `ign_svc + ign_act + diag_bl = 0` (nothing looked at the dongle).

### S2-P1: the stock CCPA, a nameless vendor device and the accessory key on API 17, with P-1. The point of the round

S2-P1-M3 runs in stage H. Every S2-P1-I run is stage I: one plug of P-1 per run (one retry after `NO_PLUG`), each
identity set and read back on the test PC first (`I pset <n> ... bcd=1.0<n>`). Setup checks for stage I: `PICO_OK=1`,
`I-clear-post` reads `defaults=0`, and `I-before blacklist=[...]` is recorded. Fields are from `p1.tsv` and `bl.tsv`
(the same label), the dialog rule is in section 5, and the operator's COUNT goes beside `android` and `ours`.

#### S2-P1-M3 (stage H), the `vidpid:18d1:2d00` key against a real phone: a measurement of the cost

D-MOTO in file transfer, debugging off, with only `vidpid:18d1:2d00` stored (`H-P1M3-p1 stored_key=1`). The code says
the plug switches the phone (its own `22B8` identity is not blacklisted) and then refuses its accessory identity on
every path. If so, below API 21 one blacklisted box with an accessory identity also blocks every such phone.

S2-P1-M3 PASS (the cost is confirmed) needs: `sw_lines` at least 1, and `phone_acc` at least 1 for `H-P1M3-p1` in
`phone.tsv` (the phone reached accessory mode); `ign_why` names `VID: 0x18D1, PID: 0x2D00): rejected: blacklisted by the
user`; `acc=0`, `found_acc=0`, `attach_B=0`, `ssl=0`, `ms=0`, `fatal=0`; and `btn1=0` with `diag0_by` holding `USB
button`. Record `accid`, `ign_act`, `android`, `ours`, `req`, `list_text`, `list_state`, the `x1.tsv` row and the COUNT.
The code expects `list_text=Allowed` and `list_state=DISABLED` for the refused accessory row: say what was read.

S2-P1-M3 FAIL if `ign_why` names the `18D1` key and `ssl` is at least 1: some path connected a device the service
refused as blacklisted. S2-P1-M3 reads **NOT CONFIRMED** (a result, not a FAIL) if the phone connected because its
accessory identity is not `18D1:2D00`: then `ssl` at least 1, the refusal absent, and `accid` names another PID.
S2-P1-M3 INCONCLUSIVE if `sw_lines=0` or `phone_acc=0` (the phone never reached accessory mode), or `NO_ACC_REFUSAL`
with `ssl=0`. S2-M-re, the next run, is the check that D-MOTO connects again once the key is gone.

#### S2-P1-I1, P-1 as a stock CCPA (identity 1): no offer, no ask, a refusal in the log

What a stock CCPA does on an API 17 head unit. A scan (`ACTION_CHECK_USB`) after 20 s prints the verdict line.

S2-P1-I1 PASS needs: `svc_attach` at least 1 or `diag` not `none` (the plug reached the app); if `svc_attach` is at
least 1, `ign_why` names `rejected: no Android interface`; `diag` reads `rejected: no Android interface`, and
`refused_all` at least 1; `android=0`, `act_attach=0`, `nonand=0`, `ours=0`, `req=0`, `norm=0`, `sw_lines=0`,
`fatal=0`; and the COUNT reads 0.

S2-P1-I1 FAIL if `android` or `act_attach` is at least 1 (the manifest filter matched it), or `ours`, `req` or
`sw_lines` is at least 1, or `diag` reads `accepted: `. S2-P1-I1 INCONCLUSIVE if `svc_attach=0` and `diag` is `none`:
nothing proves the plug reached D-HP.

**If the filter did nothing,** a stock CCPA would read as identity 4 does: `android` at least 1 and a switch. The pair
`android=0` with `diag` present separates the two.

#### S2-P1-I2 (optional), P-1 as a CCPA in NCM mode (identity 2)

The same PASS, FAIL and INCONCLUSIVE rules as S2-P1-I1. Record the interface triples in `diag`.

#### S2-P1-I4, a nameless vendor device (identity 4): what the user sees below API 21

The control for S2-P1-I4bl. Rule: OK to everything, tick nothing.

S2-P1-I4 PASS needs: `norm` at least 1 (the service accepted it), `sw_lines` at least 1, `swfail` at least 1,
`accstart=0`, `acc=0`, `ssl=0`, `fatal=0`, and `marks.txt` reads `I-c4-p1 vp=[VID: 1314 PID: 1521]`. Record `diag`
(expected: `accepted: AOAP (vendor interface named nothing)`), `android`, `act_attach`, `ours`, `req`, the number of
`swfail` lines, and the COUNT with the first word of each dialog. That is the answer to "what the user sees".

S2-P1-I4 FAIL if `fatal` is at least 1. S2-P1-I4 INCONCLUSIVE if `sw_lines=0` (no switch ran; quote `req` and the COUNT),
or `accstart` at least 1 (P-1 answered AOA, so it is not on identity 4; quote the `pset 4` line).

#### S2-P1-I4bl and S2-P1-I4rp, identity 4 blacklisted by VID:PID

S2-P1-I4bl is `blrun` with `vidpid:1314:1521`: the same six steps and the same tap budget as S2-bl. It has the S2-bl
PASS, FAIL and INCONCLUSIVE rules, on the `I-S2P4/*` rows, with no phone item. Item 5 proves the app's own writer
stores `vidpid:1314:1521`. S2-P1-I4rp then plugs P-1 once more with the app's own key (`I-rp4 stored_key=1`).

S2-P1-I4rp PASS needs: `ign_svc` at least 1 (`ign_why` names `rejected: blacklisted by the user`), `norm=0`, `ours=0`,
`req=0`, `sw_lines=0`, `swfail=0`, `ssl=0`, `fatal=0`, and the COUNT names no "Allow". Record `android` and `ign_act`:
Android's own dialog can still show, then our attach screen ignores the device. S2-P1-I4rp FAIL if `ours`, `req`,
`sw_lines`, `swfail` or `norm` is at least 1.

#### S2-P1-I5 (optional), the AOA switcher (identity 5): two identities, then the timeout

After the `I-mid` clear, so `1314:1521` is not blacklisted. S2-P1-I5 PASS needs: `norm` at least 1, `accstart` at
least 1, `acc` at least 1 (it came back as identity 3), `accid` names `18D1` and `2D00`, `ssl=0`, `fatal=0`. Record
`vto`, `android`, `ours`, `req` and the COUNT: up to two dialogs are expected, one per identity. S2-P1-I5 FAIL if
`fatal` is at least 1. S2-P1-I5 INCONCLUSIVE if `accstart=0`.

#### S2-P1-I3, the AOA accessory (identity 3): accepted, then the session times out

The control for S2-P1-I3bl. S2-P1-I3 PASS needs: `acc` at least 1, `norm=0`, `ssl=0`, `fatal=0`. Record `diag`
(expected: `accepted: already in accessory mode`), `found_acc`, `vto`, `android`, `act_attach`, `ours`, `req` and the
COUNT. S2-P1-I3 FAIL if `fatal` is at least 1. S2-P1-I3 INCONCLUSIVE if `acc=0` and `ign=0`.

#### S2-P1-I3bl, identity 3 blacklisted by `vidpid:18d1:2d00`

Setup check: `I-bl3 stored_key=1`. One tap on the USB button after 25 s.

S2-P1-I3bl PASS needs: `ign_svc` at least 1 with `ign_why` naming `VID: 0x18D1, PID: 0x2D00): rejected: blacklisted
by the user`; `acc=0`, `found_acc=0`, `vto=0`, `attach_B=0`, `ssl=0`, `ours=0`, `req=0`, `sw_lines=0`, `fatal=0`; and
`btn1=0` with `diag0_by` holding `USB button`. Record `android`, `ign_act`, `list_text` and `list_state` (the code
expects `Allowed` and `DISABLED`), and the COUNT.

S2-P1-I3bl FAIL if `acc`, `found_acc`, `vto`, `ssl`, `ours` or `req` is at least 1, or `btn1` is at least 1. Its list
item is INCONCLUSIVE if `TAP_VOID` or `list_text` reads `MATCHES=0`.

#### Clean-up, the end of stage I

Record, and quote in Setup notes: `I-before`, `I-mid`, `I-after` and `I-restored` `blacklist=[...]`; `I-after` MUST
read `blacklist=[]`, and `I-restored` MUST equal `I-before`. Also quote `I pset 0 stored0=1` (P-1 is back on its
console) and `I restored`.

### Stage 3, optional leftovers from round 4

#### B8 (optional, stage H): "Connect when plugged in" off, Home's auto-connect on, a cold start with the phone present

After S2-M-re, D-MOTO in file transfer with debugging off, defaults stored. One plug: the plug opens the app and must
not switch. 20 s later the script force-stops the app and launches it with the phone still present (marker
`H-B8-p1-cold`). Rule: tick every box, then OK.

B8 PASS needs: `H-B8-p1/pre` `openhome` at least 1 and `sw_lines=0`; `H-B8-p1/post` `homeauto` at least 1, then
`sw_lines` at least 1, `ssl` at least 1, `ms` at least 1; `phone_acc` at least 1 for `H-B8-p1`; `fatal=0`. Home's
auto-connect asks as an app start, which the gate does not cover, so a switch is expected. B8 FAIL if `homeauto` is at
least 1 and `sw_lines=0` in `/post`, or if `/pre` shows `sw_lines` at least 1. With `homeauto=0`, B8 is INCONCLUSIVE.

#### ORD (optional, stage P): "Detect plugged-in phones/AA dongles" comes after "Use Native USB Driver"

One SCREEN step and one General dump on D-POCO. ORD PASS needs, on `P-ord-gen`:
`Detect plugged-in phones/AA dongles=1:true:true`, `Use Native USB Driver=1`, and `detect_after_libusb=yes`. The host
checks `P-ord-gen-ui.png` that no other row sits between the two. ORD FAIL if `detect_after_libusb=no`. With
`detect_after_libusb=-` or `GENERAL_NOT_REACHED`, ORD is INCONCLUSIVE.

### At the end

Restore on D-MOTO: the default USB mode recorded in Prepare 3.4 (`svc usb setScreenUnlockedFunctions <value>`, `none`
if it read empty), `screen_off_timeout`, `stay_on_while_plugged_in`, and Bluetooth as found. `stageP.sh` and
`stageD.sh` restore the hosts' `settings.xml`; quote both `restored` lines. Record `serial_outside` on D-POCO after the
round (`usbdef P-end`), and state in Setup notes which lasting grants this round left.

If `stageI.sh` stopped before its clean-up (a low battery or an error), run the clean-up from `$OUT/scripts/`:

```bash
ST=I; . ./lib5.sh; . ./wireless.env; . ./pico.env; . ./pico.sh; HU=$HP_W; MOTO_W=none
xprefs set:usb-blacklist= bool:auto-connect-single-usb=false; blread I-after
pset 0; xrestore $OUT/backup-H.xml; blread I-restored
```

Then put `stay_on_while_plugged_in` back from the `I stay_on_before=` line, and leave P-1 unplugged.

## 9. Do not re-run

Rounds 1 to 4 settled `main`'s faults and the candidate's gate. Do not build `main`. D-SAM has no USB host.

| Run | Round | Verdict |
|---|---|---|
| R4a, R4b, R4c | 2, 3 | PASS: 2 Android dialogs on a first connect, 0 after the ticks (with debugging on) |
| R5a, R5b, R5c, R5t | 2, 3 | PASS: Listen off, the verb asks as the user, one dialog per path |
| F4a, F4b, F4c | 3 | PASS: no raise over Android's dialog |
| B1, B2 | 4 | PASS: the key absent, every plug connects |
| B3, B4, B5, B7 | 4 | PASS: the key off, a plug opens the app and does not switch; the button connects; the glitch window reconnects |
| B6-off, B6-on, B9, B10, B10n, dumps | 4 | PASS: the settings-screen hold, detection off, the wake check, the rows |
| L1b | 4 | PASS: the boot start with detection off, both hosts |

Round 4's B8 is the only one repeated, in a new form (a cold start), and only as an option.

## 10. Report back

1. **S1-N per host:** per plug, `dlg_all` (log) beside the COUNT, `req_A`, `req_B`, `ssl`, `phone_acc`, and on D-POCO
   `mtpn`. Does "No data transfer" give one offer that sticks?
2. **The identities:** `vp` and `diag` of D-MOTO in each mode on each host, and the `usbmode` read lines. Do the two
   modes present different interfaces on this phone?
3. **S2-bl per device:** the five `bl.tsv` rows (`ign_svc`, `diag_bl`, `home0`, `btn1`, `ours`, `req`, `sw_lines`,
   `ssl`, `acc_any`), the list readbacks (`after_tap_on_*`, `list3`), and the phone count. Is the blacklisted device
   left alone on every path, and does the app store the `vidpid:` form on API 17?
4. **S2-P1:** for identity 1, `android`, `act_attach`, `ours`, `req`, `ign_why` and `diag` beside the COUNT: does a stock
   CCPA get no offer and no ask on API 17? For identity 4, the dialogs the user saw and the `swfail` count, then the
   `I-S2P4` rows and `I-rp4-p1`. For S2-P1-I3bl and S2-P1-M3, `ign_why`, `acc`, `ssl`, `phone_acc`, `list_text` and
   `list_state`: does `vidpid:18d1:2d00` also refuse a real phone, and does S2-M-re connect it again?

Also: S1-P-F's per-plug record, S1-H-F, both S2-re rows, every optional run that ran, every void and its reason, every
`TAP_VOID`, every cycle whose pid changed, `serial_outside` on D-POCO before and after, how each phone dump was taken
(`wifi` or `cable`), and every `DEFUSB`, `DEBUG-OFF` or `PCMOTO` cue that fired. For stage I also: the P-1 round 1
verdicts, every `I pset` line, every HOLD cue, the four `blacklist=` lines, the adapter used, and the optional S2-P1
runs that ran.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
ACTION_CHECK_USB
Normal USB device attached: 
USB accessory device attached, connecting.
UsbDiagnostics:   
UsbDiagnostics: 
 sees 
 USB device(s), 
 usable for Android Auto
UsbDiagnostics: everything on the bus was refused
accepted: 
AOAP (vendor interface named 
rejected: blacklisted by the user
Ignoring USB device attached in service (
Ignored blacklisted USB device
UsbAttachedActivity: Ignored blacklisted USB device (
Ignored blacklisted USB device in onNewIntent (
HomeFragment: Requesting single-USB auto-connect via AapService
single-USB auto-connect found 
connectable devices, so no loading screen
USB button: Single device found - 
Switching USB device to accessory mode 
Switching to accessory mode.
Successfully requested switch to accessory mode for 
UsbAccessoryMode: Performing AOA switch
Found known USB device with permission: 
Fallback: force=true and found single normal-mode Android device 
Found device already in accessory mode: 
UsbLauncher: permission for 
identity=
mtp=
listen=
routeSeen=
source=
action=
Requesting USB permission for 
UsbLauncher: a dialog for 
is already open; not opening a second
UsbPermissionAsks: a dialog for 
USB Intent: 
UsbAttachedActivity: Connect when plugged in is off; opening the app, not switching 
UsbLauncher: not switching 
 on a plug-in: 
Reopen on reconnection: launching MainActivity (
Reopen on reconnection: not raised over Android's USB dialog (
USB auto-start: launching app for 
UsbLauncher: USB auto-connect held while the settings screen is open
checking USB now (trigger=
SSL handshake complete
Media Sink Setup Request: 
Boot auto-start: received action=
Boot auto-start: USB auto-start enabled, starting AapService to check USB (trigger=
Boot auto-start: starting AapService with BOOT_START
Boot auto-start: screen-on auto-start enabled
Boot auto-start: WiFi auto-start enabled
BootCompleteReceiver: Device is locked.
Boot auto-start: Android refused to start AapService
AapService creating...
ForegroundServiceStartNotAllowedException/Exception caught in onCreate: 
MATCH! Starting AapService
rejected: 
no Android interface
already in accessory mode
VID: 0x%04X, PID: 0x%04X
Ignoring non-Android USB device in onCreate (VID: 
UsbAccessoryMode: AOA switch failed: 
Error controlTransfer len: 
Acc start sent (len=
Handshake: Version exchange timed out after 
```
