# usb-dialog, round 8 brief: a Samsung Galaxy S24+ on D-POCO (its USB modes, the dialogs, Charging only) and USB link-loss recovery

Published name: `usb-dialog-round8-brief.md`. Results go in `usb-dialog-round8-results.md`.
Evidence: upload `usb-dialog-round8-captures.zip` to the existing release `rig-evidence-usb-dialog`.

Units: D-POCO (POCO X3 NFC, API 35), the only USB host. D-S24P (Samsung Galaxy S24+, a personal phone, not a rig unit)
is the wired phone. D-HU, D-MOTO, D-HP, D-SAM, D-DONGLE and P-1 take no part. **D-HU cannot be a host:**
`rig-quirks/topics/tooling.md` ("No USB accessory path", `host_connected=false`) and `units/D-DONGLE.md` ("D-HU cannot
host it").

Queue line: `usb-dialog-round8-brief.md` (one arm, `fork/fix/usb-permission-dialog` `15a13bff`; units D-POCO and D-S24P;
about 75 min after the build, 90 min with the optional runs; Q2, Q3 and R1 are the point).

Pairing: possible with a round that uses D-HU alone and has no hand steps during this one. The second of a pair takes
the lock `/tmp/ohu-rig-usbdlg.lock`.

Hand steps: section 5. Injected taps: none. The user's ask is the verb `ACTION_CHECK_USB`.

| Part | Runs, in this order, in one capture | Min |
|---|---|---|
| Build | `get_apk.sh`, with Prepare 1 to 5 beside it | 20 |
| Prepare 6 to 10 | install and identity over USB, D-S24P pairing and reads, wireless adb | 15 |
| Stage | Q1 (3 plugs, 5 modes), Q2 (3 plugs), L (R3 and R1 in one plug, R4 3 plugs, R1-on 1 plug), Q3 (2 plugs) | 50 |
| Optional | Q4 (2 plugs, only after a Q3 session), Q5 (2 plugs, only if the setting exists) | 15 |
| Close | restore list | 7 |

**About 75 min after the build, 90 min with Q4 and Q5.** Write the results file after every run.

## 1. Build and baseline

| Ref | Commit | Tree |
|---|---|---|
| `fork/fix/usb-permission-dialog` | `15a13bff0d1b6a7fe4e32664afa6b53d9fa320ae` | `deff5f62c97720f738c26f674619a11f7dcc5862` |

**History was rewritten.** 7 commits on upstream `main` `a4f80ff1`: `d61ae460` (the dialog rule), `64648b8a` (Home and
the list), `7b19dc79` (Connect when plugged in), `5b562903` (Settings rows), `ab530358` ("AAP: end the session at the
first impossible frame header"), `9371f46d` ("USB: reconnect after a link loss and show one notice per lost session"),
`15a13bff` ("Debug: add a one-shot USB read fault for rig rounds"). Round 7's `9ae9bef2` is not an ancestor. Dev host
suite at this commit: **4435 JVM tests, 0 failures**. No rig unit-test step. No other queued round shares this base. No
md5 is known: record `apk-C.apk`'s. Never reuse round 7's APK.

**The build waits for the push.** Start only when `git ls-remote fork fix/usb-permission-dialog` prints
`15a13bff0d1b6a7fe4e32664afa6b53d9fa320ae`. Another SHA: stop and ask, UNTESTABLE (build) until then.

**`get_apk.sh`**
```bash
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round8; mkdir -p $OUT
WT=${WT:-$($RIG_TOOLKIT/wt-new.sh usb-dialog candidate main | tail -1)}   # record the path in Setup notes
git -C $WT fetch fork fix/usb-permission-dialog
git ls-remote fork fix/usb-permission-dialog         # MUST print 15a13bff0d1b6a7fe4e32664afa6b53d9fa320ae
git -C $WT checkout --detach 15a13bff0d1b6a7fe4e32664afa6b53d9fa320ae
git -C $WT rev-parse 'HEAD^{tree}'                   # MUST print deff5f62c97720f738c26f674619a11f7dcc5862
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
cp rig-data/apks/*.apk $OUT/apk-C.apk; md5sum $OUT/apk-C.apk   # at once; run from StudioProjects
for s in ohu-usb-ask UsbSysfsNames 'Connect when plugged in is off; opening the app' 'debug-usb-read-fault' \
  'carries flags 0x' 'USB link lost with the phone still attached' ' notice for a session lost on '; do       # each MUST be 1 or more
  echo "$s: $(unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF "$s")"; done
unzip -p $OUT/apk-C.apk 'classes*.dex' | strings | grep -acF 'Android starts this app for a USB attach'   # MUST be 0
```

Identity on D-POCO after the install: `ACTION_QUERY_STATE` carries `15a13bff`, and the live APK's md5 matches
`apk-C.apk`. Install with `adb install -r -d` over D-POCO's USB cable, before any cable moves. Never uninstall.

## 2. What this is and why

**The dialog thread.** A reporter runs an Android 7.0 head unit with a Galaxy S24 Ultra (One UI 9) in "File transfer /
Android Auto" (`04E8:6860`), and sees a USB permission dialog on every plug. AOSP 7.x shows the MTP notification
before it reads a stored default, so a tick is never used at the plug; from 8.0 (AOSP `a455dd9f`) a stored default
bypasses it (`research-q-mtp-notification-by-version.md` in the ticket folder). AOSP calls a device MTP when one
interface is `06/01/01`, or `FF/FF/00` named `MTP`. No rig host runs Android 7 to 9, so this round measures the Samsung
side on API 35. One UI's per-connection modes, in English and in the reporter's French:

| Id | English (One UI) | French |
|---|---|---|
| `ft` | File transfer / Android Auto | Transfert de fichiers / Android Auto |
| `tether` | USB tethering ("USB modem") | Modem USB |
| `midi` | MIDI | MIDI |
| `ptp` | Transferring images (PTP) | Transfert d'images |
| `chg` | Charging phone only | Rechargement du téléphone seulement |

D-S24P may show a third language; the operator picks the item that means the same thing.

**The candidate's dialog rule (round 7's, unchanged).** `UsbPermissionAskPolicy.androidShowsDialog` is true when
`UsbAttachedActivity` is on, our manifest filter matches, and Android shows no MTP notification (API 24+, MTP, not
automotive). An automatic scan then waits (`action=wait`); else it asks (`action=ask`). The USB button
(`source=user`) always asks. "Connect when plugged in" off (`usb-connect-on-plug-in=false`) makes a plug-in open the
app without a switch; a re-attach under 30 s after a session the user did not end still switches
(`UsbPlugInPolicy.GLITCH_WINDOW_MS`). Round 7's TD-P on an MTP Motorola gave: first plug, our "Allow" then Android's
"Open" for the accessory; replugs, 0 dialogs.

**The link-loss thread (plan `gh-1204/plan-r1.md`, build notes `gh-1204/build-r1.md`).** A USB transaction error can
drop bytes from the AAP stream. Commit `ab530358` ends the session at the first frame header with a flag bit above
`0x0F`. Commit `9371f46d` makes `scheduleReconnectIfNeeded` recheck the bus after a formed session ends with
`LINK_FAILED` while the accessory is still on the bus, also when the auto-connect settings are off, and shows one
notice per lost session: the USB text on USB, the WiFi tip only for a wireless loss, none for a cable pull. Commit
`15a13bff` adds a one-shot fault: the next standard USB bulk read that carries data is discarded and returns `-1`.

## 3. What is different about this round

- **D-S24P is a personal phone.** Developer options on for this round only, USB debugging **off**, Wireless debugging
  **on**. Install nothing on it. Close puts every setting back, then the operator turns Developer options off.
- **Phone capture over Wireless debugging:** live logcat (`S-phone.txt`, same markers), and `sys.usb.config` and
  `sys.usb.state` once a second per plug (`<P>-pmode.txt`). The operator's MODEREAD is the second column.
- **Wireless debugging must not change the descriptor.** Q1 compares a plug with it on and one with it off. If they
  differ, every later verdict carries "not representative of the reporter".
- **Wireless debugging turns off after a WiFi change or a reboot, and its port changes each time it turns on.** Keep
  D-S24P on the test PC's WiFi. `s24up` finds the port by mDNS, else asks the operator (stop rule).
- **A stored default makes a File transfer plug switch at once.** So Q3 to Q5 run with Connect when plugged in off
  (`NCKEYS`), each plug 45 s after the last unplug (`glitchwait`). The USB button is then the only switch.
- **Q2 plug A is D-S24P's first Android Auto setup with this head unit.** Session cap 300 s; the operator walks through
  AASETUP. Samsung may ask "Allow access to phone data?": Allow (DATA).
- **D-POCO must be the host.** On D-S24P "USB controlled by" must read "Connected device" (CTRL). No `plug=` line on
  D-POCO is the sign the roles are reversed.
- **"USB tethering" shares D-S24P's mobile data:** Q1 holds it 10 s; Close checks it is off. **D-POCO powers D-S24P:**
  80% battery to start, stop at 30%.
- **The fault (L runs).** Armed by `ACTION_SET_SETTINGS` with the `json` extra. `SettingsBackupManager.parseImportJson`
  needs the backup shape, so a bare `{"debug-usb-read-fault": true}` throws "Unsupported settings backup format". The
  shape that works: `{"format":"open-headunit-settings","version":1,"settings":{"debug-usb-read-fault":true}}`. It
  needs `allow-external-configuration=true` (in `S1KEYS`). The reply must read `"imported":1`. The fault exists only in
  `StandardUsbProjectionConnection`, so `use-libusb=false` must hold (it is in `S1KEYS`). It disarms itself on firing.
- **"Auto-connect at defaults" is the new arm.** `auto-connect-last-session` and `auto-connect-single-usb` both default to
  `false`, and `scheduleReconnectIfNeeded` takes the new branch only when both are off. So R1 at defaults tests
  `9371f46d`'s new recheck (`USB link lost with the phone still attached`). R1-on (`auto-connect-single-usb=true`) is the
  old path (`USB disconnect. Scheduling reconnect check in`), 3 reps instead of 5 to save time.
- **The notice logs.** `AapProjectionActivity.showDisconnectNotice` prints, at INFO and just before the toast,
  `AapProjectionActivity: showing the <Notice> notice for a session lost on <Type>` (`DisconnectNoticePolicy.Notice`:
  `USB_LOST`, `WIRELESS_TIP`; `ProjectionConnection.Type`: `USB`, `WIFI`). R2 and R4 grade on it. The operator's TOAST
  read is the second column, so `show-toast-messages` is written `true`.
- **R5 is UNTESTABLE.** A wireless session on this rig today needs Native AA on D-HU with D-MOTO: a third unit, a second
  install, Bluetooth and the poke keys. D-POCO's Headunit Server route is down. D-S24P must not be paired to a rig unit.
  The rule (one WiFi tip per wireless loss) is covered by `DisconnectNoticePolicyTest`.
- Log level VERBOSE (`log-level=0`). Thermal: `rig-quirks/topics/tooling.md` limits through `th_gate`. Read
  `topics/tooling.md`, `units/D-POCO.md` and template §7b first.

## 4. Settings keys

Written with the app stopped by `setkeys` (round 4), every key read back. Back up `settings.xml` in Prepare; `xrestore`
at Close. `S1KEYS` is round 5's (see `usb-dialog-round7-brief.md` §4) plus `bool:show-toast-messages=true`. Audio keys
and `screen-orientation` stay as found.

| Tag | Keys | `usb-connect-on-plug-in` | `auto-connect-single-usb` | Runs |
|---|---|---|---|---|
| `nc` | `NCKEYS` | `false` | `false` | Q1, Q3, Q4, Q5 |
| `f` | `S1KEYS` | `true` | `false` | Q2, L-a, R4 |
| `on` | `ONKEYS` | `true` | `true` | R1-on |

## 5. Hand steps and the answer rules

Cabling: Prepare has D-POCO on the test PC. In the stage D-POCO is on wireless adb, and D-S24P plugs into D-POCO's port
with its USB-C to USB-C cable at each PLUG cue.

| Id | Hand step | When |
|---|---|---|
| DEVON | On D-S24P: Settings > About phone > Software information, press Build number 7 times, enter the PIN (if Developer options are off) | Prepare 7 |
| DEBUGCHK | Developer options: USB debugging must be **off**; turn it off if it is on | Prepare 7 |
| WDPAIR | Developer options: Wireless debugging on; at "Allow wireless debugging on this network?" press Allow. Press the words "Wireless debugging", then "Pair device with pairing code". Read aloud the code and the IP address and port | Prepare 7, stop rule |
| WDADDR | On the Wireless debugging screen read aloud the address under "IP address & Port" | when `s24up` asks |
| ABOUT | Settings > About phone > Software information: read aloud the One UI and Android versions | Prepare 8 |
| DEVREAD | Developer options: does "Default USB configuration" (or a Samsung name for it) exist? Say its options and the selected one. Change nothing | Prepare 9 |
| AAREAD | Settings > Connected devices > Android Auto: is "Add new cars to Android Auto" on? Change nothing | Prepare 9 |
| BLOCKREAD | Settings > Security and privacy > Auto Blocker: on or off? Change nothing | Prepare 9 |
| UNLOCK, PLUG, UNPLUG | D-S24P unlocked, then plug or unplug its cable at D-POCO | each cue |
| CTRL | On D-S24P open the USB notification. If "USB controlled by" does not read "Connected device", choose it | after every plug |
| DATA | "Allow access to phone data?" on D-S24P: press Allow, say so | any time |
| MODE / MODEREAD | On D-S24P's USB settings, "Use USB for": choose the cue's item (touch nothing if selected) and say what it showed before / only say what is selected | each cue |
| AASETUP | Android Auto's setup screens on D-S24P: press the button that goes on (Continue, Accept, Allow, OK); for a sign-in, install or update press Not now, Skip or Cancel. Say each title | Q2 plug A, and any later plug |
| DLG | The dialogs on D-POCO, by the rule below | each plug |
| CLEAR | On D-POCO's App info screen: Open by default, then Clear default preferences | each CLEAR cue |
| COUNT | For this plug: how many USB dialogs on D-POCO since PLUG and the first word of each, the last USB mode on D-S24P, and whether Android Auto started on it | after every unplug |
| TOAST | Watch D-POCO's screen. Say the first words of every short message at the bottom ("Android Auto disconnected: the USB ..." or "... If using wireless AA ...") | L runs, each TOAST cue |
| PULL | Unplug D-S24P's cable from D-POCO now, mid-session | R4 |
| WDOFF / WDON | Developer options: Wireless debugging off, or on again. USB debugging stays off | Q1 |
| DEFUSB | Developer options > Default USB configuration: the value the cue names | Q5 only |
| RESTORE | The restore cues of `close8.sh` | Close |

Record every spoken answer at once with `said <LABEL> "<words>"`. Labels: `S24-devopts`, `S24-usbdebug`, `S24-pair`,
`S24-addr`, `S24-about`, `S24-defusb`, `S24-addnewcars`, `S24-autoblocker`, `<P>-mode`, `<P>-data`, `<P>-aasetup`,
`<P>-count`, `<K>-toast`, `S24-defusb-after`, `S24-restore`. Open the cue terminal first:
`tail -n0 -F /home/oscar/Coding/StudioProjects/rig-data/rounds/usb-dialog-round8/cues.log`.

On D-POCO, "Open ..." is Android's dialog and "Allow ..." is ours (template §7b). Act only on what the rule names:

| Run | D-POCO | D-S24P |
|---|---|---|
| Q1 | Cancel every "Open" and "Allow". Never tick | MODE at MODE cues, DATA |
| Q2, L, R4 | First D-S24P on File transfer. Then every "Open" or "Allow": tick the box if there is one, then OK | File transfer before any dialog; DATA, AASETUP |
| Q3, Q4, Q5 | Every "Open" or "Allow": tick the box if there is one, then OK | MODE or MODEREAD at cues; DATA; AASETUP |

## 6. Helpers

Copy `$RIG_DATA/rounds/usb-dialog-round7/scripts/` into `$RIG_DATA/rounds/usb-dialog-round8/scripts/` (rounds 4 to 7's
helpers: `lib.sh` to `lib7.sh`, `keys.sh`, `cycle.sh`, `usbparse.sh`, `timeline.sh`, `x1.sh`, `xprefs.py`, `xpcp.sh`,
`f7.sh` and the rest). If it is gone, extract them from the round 4 to 7 briefs with `extract_brief_blocks.py`. Then
extract the blocks below into the same folder. Do not copy `wireless.env` or `s24.env`. Run every script from that
folder, every adb call against one unit in sequence (template rule 8); the logcat streams and the 1 s mode watcher are the only
background calls. Grep with `-a`. `lib8.sh` redefines `mark` and `moto_dump`; every other
helper is as rounds 4 to 7 defined it.

**`lib8.sh`**
```bash
# lib8.sh: round 7's helpers with this round's folder, candidate and phone (D-S24P over Wireless debugging only).
. ./lib7.sh; . ./f7.sh
OUT=${RIG_DATA:-$HOME/Coding/StudioProjects/rig-data}/rounds/usb-dialog-round8; mkdir -p $OUT
CAND=15a13bff; BSTART=80; BSTOP=30
S24_W=; S24_IP=; [ -f ./s24.env ] && . ./s24.env
S1KEYS="$S1KEYS bool:show-toast-messages=true"
NCKEYS=${S1KEYS/bool:usb-connect-on-plug-in=true/bool:usb-connect-on-plug-in=false}
ONKEYS=${S1KEYS/bool:auto-connect-single-usb=false/bool:auto-connect-single-usb=true}
FAULTJSON='{"format":"open-headunit-settings","version":1,"settings":{"debug-usb-read-fault":true}}'
Q1RULE="D-POCO: Cancel every Open and Allow, never tick. D-S24P: MODE at MODE cues only. Unlocked before the plug."
Q2RULE="First D-S24P on File transfer / Android Auto. Then D-POCO: every Open or Allow, tick if a box, then OK. AASETUP."
Q3RULE="D-POCO: every Open or Allow, tick if a box, then OK. D-S24P: MODE or MODEREAD at cues, AASETUP if it shows."
modename() { case $1 in ft) echo 'File transfer / Android Auto';; tether) echo 'USB tethering';; midi) echo MIDI;;
  ptp) echo 'Transferring images (PTP)';; chg) echo 'Charging phone only';; esac; }
SA() { timeout 15 adb -s "$S24_W" "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3
  [ -n "$S24_W" ] && timeout 3 adb -s "$S24_W" shell log -t RIGMARK "RIGMARK $1" >/dev/null 2>&1
  echo "$1 host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
moto_dump() { :; }
# s24up: connect D-S24P by mDNS (S24_IP), else WDADDR and the address the executor writes to $OUT/s24.addr (180 s)
s24up() { local a= t
  [ -n "$S24_W" ] && [ "$(timeout 5 adb -s "$S24_W" get-state 2>/dev/null)" = device ] && return 0
  for t in 1 2 3; do
    a=$(adb mdns services 2>/dev/null | grep -aF _adb-tls-connect | tr -s ' \t' '\n' | grep -m1 "^${S24_IP:-none}:[0-9]*$")
    [ -n "$a" ] && { adb connect "$a" >/dev/null 2>&1; sleep 2; [ "$(timeout 5 adb -s "$a" get-state 2>/dev/null)" = device ] && break; }
    a=; sleep 5; done
  if [ -z "$a" ]; then rm -f $OUT/s24.addr
    cue "WDADDR: on D-S24P open Developer options > Wireless debugging (turn it on if off). Read aloud the IP address and port"
    t=0; while [ $t -lt 180 ] && [ ! -s $OUT/s24.addr ]; do sleep 3; t=$((t+3)); done
    a=$(tr -d ' \r\n' < $OUT/s24.addr 2>/dev/null); [ -n "$a" ] && { adb connect "$a" >/dev/null 2>&1; sleep 2; }; fi
  if [ -n "$a" ] && [ "$(timeout 5 adb -s "$a" get-state 2>/dev/null)" = device ]; then S24_W=$a
    printf 'S24_IP=%s\nS24_W=%s\n' "${a%%:*}" "$a" > ./s24.env; echo "S24 up $a host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; return 0; fi
  echo "S24 LOST host=$(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; return 1; }
pcap() { [ -n "$S24_W" ] || return 1; stdbuf -oL adb -s "$S24_W" logcat -v time -T 1 >> $OUT/S-phone.txt 2>/dev/null & echo $! > $OUT/S-phone.pid; }
palive() { kill -0 "$(cat $OUT/S-phone.pid 2>/dev/null)" 2>/dev/null && return 0; s24up || return 1
  echo "$(date +%T) phone capture restarted" >> $OUT/marks.txt; pcap; }
puncap() { kill "$(cat $OUT/S-phone.pid 2>/dev/null)" 2>/dev/null; }
# pmode_on P: D-S24P's sys.usb.config and sys.usb.state once a second, one line per change, into P-pmode.txt
pmode_on() { ( last=x; while :; do c=$(timeout 4 adb -s "$S24_W" shell getprop sys.usb.config 2>/dev/null | tr -d '\r\n')
    s=$(timeout 4 adb -s "$S24_W" shell getprop sys.usb.state 2>/dev/null | tr -d '\r\n'); v="config=[$c] state=[$s]"; [ -z "$c$s" ] && v=unreach
    [ "$v" != "$last" ] && { echo "$(date '+%m-%d %H:%M:%S') $v" >> $OUT/$1-pmode.txt; last=$v; }; sleep 1; done ) & echo $! > $OUT/pmode.pid; }
pmode_off() { kill "$(cat $OUT/pmode.pid 2>/dev/null)" 2>/dev/null; }
pfunc() { echo "$1 phone config=[$(SA shell getprop sys.usb.config 2>/dev/null | tr -d '\r')] state=[$(SA shell getprop sys.usb.state 2>/dev/null | tr -d '\r')] functions=[$(SA shell svc usb getFunctions 2>&1 | tr -d '\r' | tr '\n' ' ' | cut -c1-160)]" >> $OUT/marks.txt; }
pslice() { tr -d '\r' < $OUT/S-phone.txt 2>/dev/null | sed -n "/RIGMARK $1-cue\$/,/RIGMARK $1-unplug\$/p" > $OUT/$1-phone.txt; }
# prow P: one phone.tsv row (capture lines, accessory lines, Android Auto lines, the mode watcher's sequence)
prow() { local pm=$OUT/$1-pmode.txt f=$OUT/$1-phone.txt
  printf '%s\tphone_lines=%s\tphone_acc=%s\tgh=%s\tgh_conn=%s\tpm_first=%s\tpm_acc=%s\tpm_unreach=%s\tpm_seq=%s\n' "$1" \
    "$(wc -l < $f 2>/dev/null || echo 0)" "$(grep -acF 'entering USB accessory mode' $f 2>/dev/null)" "$(grep -acF ' GH.' $f 2>/dev/null)" \
    "$(grep -acF GH.CarConnSession $f 2>/dev/null)" "$(grep -av unreach $pm 2>/dev/null | head -1 | cut -d' ' -f3-)" \
    "$(grep -acF accessory $pm 2>/dev/null)" "$(grep -acF unreach $pm 2>/dev/null)" "$(cut -d' ' -f3- $pm 2>/dev/null | tr '\n' '>' | cut -c1-400)" >> $OUT/phone.tsv; }
# readid L FROM: D-S24P's identity from FROM to now (app lines plus a fresh dumpsys usb), one desc.tsv row
readid() { mark $1-read; A shell dumpsys usb > $OUT/$1-usb.txt 2>&1
  python3 -I ./desc8.py $(F) "$2" "$1-read" $OUT/$1-usb.txt "$1" $OUT/$1-hostdev.txt >> $OUT/desc.tsv; pfunc $1; }
```

**`desc8.py`**
```python
#!/usr/bin/env python3
# desc8.py CAPTURE FROM TO USBDUMP LABEL EXCERPT: one desc.tsv row for VID 04E8 in FROM..TO (no TO: to the end). Triples
# and verdict from the app's last "UsbDiagnostics:   " line, mtp=/filter=/action= from its last decision line, names from
# dumpsys usb (key=value or UsbInterface[...] form) or the capture. filter_calc applies usb_device_filter.xml. No verdict.
import os, re, sys
cap, frm, to, dump, label, excerpt = sys.argv[1:7]
VID, IDS = 0x04E8, {(0x18D1, 0x2D00), (0x18D1, 0x2D01)}
TRIPLES = {(0xFF, 0xFF, 0), (0xFF, 0x42, 1), (6, 1, 1), (0xE0, 1, 3), (0xEF, 4, 1), (8, 6, 1)}
seen, w, on = set(), [], False
for line in (r.rstrip("\r\n") for r in open(cap, encoding="utf-8", errors="replace")):
    if line in seen: continue
    seen.add(line)
    if line.endswith("AutomationMarker: " + frm): on = True
    elif on and line.endswith("AutomationMarker: " + to): break
    elif on: w.append(line)
def field(line, k):
    m = re.search(k + r"=(\S+)", line); return m.group(1) if m else "-"
def from_tostring(text):
    for b in text.split("UsbDevice[")[1:]:
        m = re.search(r"mVendorId=(\d+)", b)
        r = {int(i): (n, int(c), int(s), int(p)) for i, n, c, s, p in re.findall(
            r"UsbInterface\[mId=(\d+),mAlternateSetting=\d+,mName=([^,]*),mClass=(\d+),mSubclass=(\d+),mProtocol=(\d+)", b)}
        if m and int(m.group(1)) == VID and r: return r
    return {}
def from_keyvalue(text):
    r, vid, cur = {}, None, None
    def close():
        if cur is not None and "id" in cur:
            r[cur["id"]] = (cur.get("name", ""), cur.get("class", -1), cur.get("subclass", -1), cur.get("protocol", -1))
    for s in (x.strip() for x in text.splitlines()):
        m = re.match(r"vendor_id=(\d+)$", s)
        if m: close(); cur = None; vid = int(m.group(1)); continue
        if vid != VID: continue
        if s.startswith("interfaces={") or s.startswith("endpoints={"):
            close(); cur = {} if s.startswith("interfaces") else None; continue
        m = re.match(r"(id|name|class|subclass|protocol)=(.*)$", s) if cur is not None else None
        if m: cur[m.group(1)] = m.group(2) if m.group(1) == "name" else (int(m.group(2)) if m.group(2).isdigit() else -1)
    close(); return r
diag = [l for l in w if "UsbDiagnostics:   " in l and "VID: 04E8" in l]
dec = [l for l in w if "UsbLauncher: permission for " in l and "VID: 04E8" in l]
vidpid = devcls = verdict = "none"; trip = []
if diag:
    d = diag[-1]
    m = re.search(r"VID: ([0-9A-F]{4}) PID: ([0-9A-F]{4})", d); vidpid = m.group(1) + ":" + m.group(2) if m else vidpid
    m = re.search(r"\[class ([0-9A-F]{2})/([0-9A-F]{2})/([0-9A-F]{2})", d); devcls = "/".join(m.groups()) if m else devcls
    m = re.search(r"\] ((?:accepted|rejected): .*?)(?: \| if\d|$)", d); verdict = m.group(1).strip() if m else verdict
    trip = [(int(i), int(a, 16), int(b, 16), int(c, 16))
            for i, a, b, c in re.findall(r"\| if(\d+) ([0-9A-F]{2})/([0-9A-F]{2})/([0-9A-F]{2})", d)]
text = open(dump, encoding="utf-8", errors="replace").read() if os.path.exists(dump) else ""
names, src = from_keyvalue(text), "dumpsys-kv"
if not names: names, src = from_tostring(text), "dumpsys-tostring"
if not names: names, src = from_tostring("\n".join(w)), "logcat"
src = src if names else "none"
trip = trip or [(i, v[1], v[2], v[3]) for i, v in sorted(names.items())]
ifs, adb_if, mtp = [], 0, "no"
for i, c, s, p in trip:
    n = names[i][0] if i in names else "?"
    if i in names and names[i][1:] != (c, s, p): n += "(dump:%02X/%02X/%02X)" % names[i][1:]
    ifs.append("%02X/%02X/%02X:%s" % (c, s, p, n or "-")); adb_if += (c, s, p) == (0xFF, 0x42, 1)
    if (c, s, p) == (6, 1, 1) or ((c, s, p) == (0xFF, 0xFF, 0) and n == "MTP"): mtp = "yes"
    elif (c, s, p) == (0xFF, 0xFF, 0) and n == "?" and mtp == "no": mtp = "unknown"
try:
    v, p = (int(x, 16) for x in vidpid.split(":")); dc = tuple(int(x, 16) for x in devcls.split("/"))
    fil = "match" if (v, p) in IDS or dc in TRIPLES or any(t[1:] in TRIPLES for t in trip) else "none"
except ValueError:
    fil = "unknown"
app = dec[-1] if dec else ""
print("\t".join([label, "vidpid=" + vidpid, "devclass=" + devcls, "n_if=%d" % len(trip), "ifs=[" + "; ".join(ifs) + "]",
                 "adb_if=%d" % adb_if, "aosp_mtp=" + mtp, "filter_calc=" + fil, "verdict=" + verdict,
                 "app_mtp=" + field(app, "mtp"), "app_filter=" + field(app, "filter"), "app_action=" + field(app, "action"),
                 "names_src=" + src, "diag_lines=%d" % len(diag)]))
lines = text.splitlines()
with open(excerpt, "w") as f:
    for i in [i for i, l in enumerate(lines) if "vendor_id=1256" in l or "mVendorId=1256" in l][:3]:
        f.write("\n".join(lines[i:i + 70]) + "\n----\n")
```

**`s8.sh`**
```bash
# s8.sh: the shared plug cycle and the runs. Needs lib8.sh.
# splug P RULE: one plug; 0 plugged, 1 NO_PLUG, 2 low battery. plugtry: splug, once more as Pr after NO_PLUG (PL = label used).
splug() { P=$1; RULE="$2"; PRE=-; batok || return 2
  alive; [ "${NOPHONE:-0}" = 1 ] || palive || echo "$P PHONE_LINK_DOWN" >> $OUT/marks.txt; home; pid0=$(hupid)
  cue "NEXT $P: read the rule, nothing starts for 12 s"; sleep 12; mark $P-cue; pmode_on $P
  cue "PLUG $P: D-S24P unlocked, plug its USB-C cable into D-POCO now. Then CTRL on D-S24P"
  if ! waitany $P-cue "$PLUGRE" 120; then echo "$P NO_PLUG" >> $OUT/marks.txt; mark $P-end
    cue "UNPLUG $P: unplug D-S24P from D-POCO if it is plugged in"; sleep 15; mark $P-unplug; pmode_off; return 1; fi
  echo "$P plug=$(firstre $P-cue "$PLUGRE")" >> $OUT/marks.txt; return 0; }
plugtry() { local rc; PL=$1; splug $1 "$2"; rc=$?; [ $rc -ne 1 ] && return $rc; PL=${1}r; splug ${1}r "$2"; }
# sunplug: unplug (skipped when the cable is already out), COUNT, and every row of plug P
sunplug() { mark $P-end; [ "${1:-}" = pulled ] || { cue "UNPLUG $P: unplug D-S24P from D-POCO"; unplugwait $P; }
  mark $P-unplug; pmode_off; LASTUNPLUG=$(date +%s); RULE=
  cue "COUNT $P: for this plug, how many USB dialogs on D-POCO since PLUG and the first word of each, the last mode on D-S24P, and did Android Auto start on it (25 s)"; sleep 25
  usbdef $P-after; pslice $P; ./timeline.sh $(F) $P > $OUT/$P.timeline.tsv; row $P $OUT/$P.timeline.tsv
  fwin $P all $P-cue $P-unplug; answers $P; ./x1.sh $(F) $P 'VID: 04E8' >> $OUT/x1.tsv; prow $P; }
win() { ./timeline.sh $(F) $1/$2 $3 $4 > $OUT/$1-$2.timeline.tsv; row $1/$2 $OUT/$1-$2.timeline.tsv; fwin $1 $2 $3 $4; }
sessn() { { waitfor $1 'SSL handshake complete' $2 && waitfor $1 'Media Sink Setup Request: ' 30; } || echo "$P NO_SESSION" >> $OUT/marks.txt; sleep 10; }
# verb P CAP: the app in front (API 35 refuses a background start), the user's ask, the session. NO_USER_DEC is information.
verb() { launch; sleep 3; mark $1-verb; send ACTION_CHECK_USB >> $OUT/marks.txt
  waitfor $1-verb 'UsbLauncher: permission for ' 15 || echo "$1 NO_USER_DEC" >> $OUT/marks.txt; sessn $1-verb ${2:-150}; }
modeset() { mark $1-m-$2; cue "MODE $1 $2: on D-S24P choose Use USB for > $(modename $2) now (touch nothing if selected). Say what it showed before"
  if waitany $1-m-$2 "$PLUGRE" 40; then echo "$1-m-$2 reattach=$(firstre $1-m-$2 "$PLUGRE")" >> $OUT/marks.txt
  else echo "$1-m-$2 NO_REATTACH" >> $OUT/marks.txt; fi; sleep 10; }
q1() { local m from rc t=0
  setkeys nc $NCKEYS || { echo "Q1 UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  plugtry Q1-w1 "$Q1RULE"; rc=$?; [ $rc -eq 2 ] && return 2; [ $rc -eq 0 ] && { modeset $PL ft; readid $PL $PL-cue; sunplug; }
  cue "WDOFF: on D-S24P turn Wireless debugging off (Developer options). USB debugging stays off. Say done"
  while [ $t -lt 120 ] && [ "$(timeout 5 adb -s "$S24_W" get-state 2>/dev/null)" = device ]; do sleep 3; t=$((t+3)); done
  [ "$(timeout 5 adb -s "$S24_W" get-state 2>/dev/null)" = device ] && echo "Q1 WD_STILL_ON" >> $OUT/marks.txt
  puncap; S24_SAVE=$S24_W; S24_W=; NOPHONE=1
  plugtry Q1-w2 "$Q1RULE"; rc=$?; [ $rc -eq 2 ] && return 2; [ $rc -eq 0 ] && { modeset $PL ft; readid $PL $PL-cue; sunplug; }
  cue "WDON: on D-S24P turn Wireless debugging on again. USB debugging stays off. Say done"; sleep 20
  NOPHONE=0; adb disconnect "$S24_SAVE" >/dev/null 2>&1; s24up && pcap || echo "Q1 S24_LOST_AFTER_WDON" >> $OUT/marks.txt
  plugtry Q1-s "$Q1RULE"; rc=$?; [ $rc -ne 0 ] && return $rc
  for m in ft tether midi ptp chg; do from=$PL-m-$m; [ $m = ft ] && from=$PL-cue
    modeset $PL $m; readid $PL-$m $from; win $PL $m $from $PL-$m-read
    echo "$PL-$m mtpn=$(window $from $PL-$m-read | grep -aF notification_enqueue | grep -acF com.android.mtp)" >> $OUT/marks.txt; done
  sunplug; pfunc Q1-after; return 0; }
q2() { local K rc; cleardef Q2-clear; usbdef Q2-clear-check
  setkeys f $S1KEYS || { echo "Q2 UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  for K in A B C; do plugtry Q2-p$K "$Q2RULE"; rc=$?; [ $rc -eq 2 ] && return 2; [ $rc -eq 1 ] && continue
    [ $K = A ] && cue "AASETUP $PL: Android Auto may open setup screens on D-S24P. Go on through each, say each title"
    sessn $PL-cue $([ $K = A ] && echo 300 || echo 150); readid $PL $PL-cue; sunplug; done; return 0; }
lcount() { echo "$1 fault=$(wc1 $1-fault $1-end 'debug fault, discarding a ') desync=$(wc1 $1-fault $1-end 'carries flags 0x') wrongflag=$(wc1 $1-fault $1-end 'WRONG FLAG: ') lost_attached=$(wc1 $1-fault $1-end 'USB link lost with the phone still attached') sched=$(wc1 $1-fault $1-end 'USB disconnect. Scheduling reconnect check in ') overlay=$(wc1 $1-fault $1-end 'Showing reconnecting overlay') ssl=$(wc1 $1-fault $1-end 'SSL handshake complete') ms=$(wc1 $1-fault $1-end 'Media Sink Setup Request: ') detach=$(wc1 $1-fault $1-end 'USB_DEVICE_DETACHED') t_fault=$(firstts $1-fault 'debug fault, discarding a ') t_desync=$(firstts $1-fault 'carries flags 0x') t_ssl=$(firstts $1-fault 'SSL handshake complete') usb_notice=$(wc1 $1-fault $1-end 'showing the USB_LOST notice') wifi_notice=$(wc1 $1-fault $1-end 'showing the WIRELESS_TIP notice') any_notice=$(wc1 $1-fault $1-end 'AapProjectionActivity: showing the ')" >> $OUT/marks.txt; }
# lrep K: one fault in a live session (R1 and R2): arm, the desync, the recheck, the new session, TOAST
lrep() { mark $1-fault
  send ACTION_SET_SETTINGS --es json "'$FAULTJSON'" > $OUT/$1-arm.txt 2>&1
  echo "$1 imported=$(grep -ao '"imported":[0-9]*' $OUT/$1-arm.txt | cut -d: -f2)" >> $OUT/marks.txt
  cue "TOAST $1: watch D-POCO's screen for 20 s; say every short message at the bottom"
  waitfor $1-fault 'carries flags 0x' 10 || echo "$1 NO_DESYNC" >> $OUT/marks.txt
  waitfor $1-fault 'Media Sink Setup Request: ' 40 || echo "$1 NO_RESESSION" >> $OUT/marks.txt
  sleep 5; mark $1-end; lcount $1; pfunc $1; }
lidle() { mark $1-fault; sleep 10; mark $1-end; lcount $1
  echo "$1 fed=[$(window $1-fault $1-end | grep -aF 'Throughput over' | grep -ao 'fed=[0-9]*' | tr '\n' ' ')]" >> $OUT/marks.txt; }
# lruns: L-a (R3 then R1, one plug, S1KEYS), R4 (3 pulls), L-b (R1-on, ONKEYS). Phone stays on File transfer.
lruns() { local k rc
  plugtry L-a "$Q2RULE"; rc=$?; [ $rc -eq 2 ] && return 2
  if [ $rc -eq 0 ]; then sessn $PL-cue 150; for k in 1 2 3 4 5; do lidle R3-$k; done
    for k in 1 2 3 4 5; do lrep R1-$k; done; sunplug; fi
  for k in 1 2 3; do plugtry R4-$k "$Q2RULE"; rc=$?; [ $rc -eq 2 ] && return 2; [ $rc -eq 1 ] && continue
    sessn $PL-cue 150; mark R4-$k-fault; cue "PULL R4-$k: unplug D-S24P's cable from D-POCO now. Then TOAST: say every message"
    waitfor R4-$k-fault 'USB_DEVICE_DETACHED' 60 || echo "R4-$k NO_DETACH" >> $OUT/marks.txt; sleep 15
    mark R4-$k-end; lcount R4-$k; sunplug pulled; done
  setkeys on $ONKEYS || { echo "R1-on UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  plugtry L-b "$Q2RULE"; rc=$?; [ $rc -eq 2 ] && return 2
  [ $rc -eq 0 ] && { sessn $PL-cue 150; for k in 1 2 3; do lrep R1on-$k; done; sunplug; }; return 0; }
q3() { local rc; setkeys nc $NCKEYS || { echo "Q3 UNTESTABLE settings" >> $OUT/marks.txt; return 0; }
  plugtry Q3-p1 "$Q3RULE"; rc=$?; [ $rc -eq 2 ] && return 2
  if [ $rc -eq 0 ]; then sleep 20; readid $PL-start $PL-cue; modeset $PL chg; readid $PL $PL-m-chg
    verb $PL 150; sunplug; win $PL pre $PL-cue $PL-verb; win $PL user $PL-verb $PL-end; fi
  glitchwait; plugtry Q3-p2 "$Q3RULE"; rc=$?; [ $rc -eq 2 ] && return 2
  [ $rc -eq 0 ] && { sleep 20; cue "MODEREAD $PL: say which item D-S24P shows under Use USB for. Change nothing"; sleep 20; readid $PL $PL-cue; sunplug; }; return 0; }
# qv RUN MODES: Q4 (MODE then the verb) or Q5 (MODEREAD then the verb), one plug per item
qv() { local R=$1 m rc; shift; for m in "$@"; do glitchwait; plugtry $R-$m "$Q3RULE"; rc=$?; [ $rc -eq 2 ] && return 2; [ $rc -eq 1 ] && continue
    sleep 15; if [ $R = Q4 ]; then modeset $PL $m; readid $PL $PL-m-$m
    else cue "MODEREAD $PL: say which item D-S24P shows under Use USB for. Change nothing"; sleep 20; readid $PL $PL-cue; fi
    verb $PL 120; sunplug; win $PL pre $PL-cue $PL-verb; win $PL user $PL-verb $PL-end; done; return 0; }
q5() { cue "DEFUSB Q5: on D-S24P set Developer options > Default USB configuration > Charging phone only. Say done"; sleep 45
  echo "Q5 default_set=[$(SA shell dumpsys usb 2>/dev/null | tr -d '\r' | grep -aiE -m2 'screen.?unlocked.?functions' | tr '\n' ' ')]" >> $OUT/marks.txt
  qv Q5 p1 p2; local rc=$?
  cue "DEFUSB Q5 restore: set Default USB configuration back to the value from Prepare. Say the value it shows"; sleep 45
  echo "Q5 default_restored=[$(SA shell dumpsys usb 2>/dev/null | tr -d '\r' | grep -aiE -m2 'screen.?unlocked.?functions' | tr '\n' ' ')]" >> $OUT/marks.txt; return $rc; }
```

**`stage8.sh`**
```bash
#!/usr/bin/env bash
# ./stage8.sh (or Q5=1 ./stage8.sh): Q1, Q2, the L runs, Q3; Q4 after a Q3 plug-1 session; Q5 when Q5=1.
ST=P; . ./lib8.sh; . ./wireless.env; . ./s8.sh; HU=$POCO_W; HUNAME=D-POCO; export ST HU HUNAME; RULE=
cue "CAB: take D-POCO off its PC cable. D-S24P's USB-C to USB-C cable ready at D-POCO. D-S24P stays on the test PC's WiFi"
adb connect $HU >/dev/null; s24up || { echo "P NO_PHONE_LINK" >> $OUT/marks.txt; exit 1; }
batok $BSTART || { echo "P BATTERY_START" >> $OUT/marks.txt; exit 1; }; th_gate || exit 1
echo "P stay_on_before=$(A shell settings get global stay_on_while_plugged_in | tr -d '\r')" >> $OUT/marks.txt
A shell svc power stayon true; A shell am force-stop $PKG
cap; pcap; mark P-start-$(date +%H%M%S); launch; sleep 3
for r in q1 q2 lruns q3; do $r; [ $? -eq 2 ] && { th_end; exit 2; }; done
Q3S=$(grep -ao '^Q3-p1r* plug=' $OUT/marks.txt | tail -1 | cut -d' ' -f1)
if [ -n "$Q3S" ] && [ "$(wc1 $Q3S-verb $Q3S-end 'SSL handshake complete')" -ge 1 ]; then qv Q4 ptp midi
else echo "Q4 UNTESTABLE no_session_in_Q3" >> $OUT/marks.txt; fi
if [ "${Q5:-0}" = 1 ]; then q5; else echo "Q5 UNTESTABLE no_default_usb_setting" >> $OUT/marks.txt; fi
mark P-stage-end; th_end
```

**`close8.sh`**
```bash
#!/usr/bin/env bash
# ./close8.sh: clear and restore D-POCO, then D-S24P's restore cues. Run it even after a stopped stage.
ST=P; . ./lib8.sh; . ./wireless.env; HU=$POCO_W; HUNAME=D-POCO; export ST HU HUNAME; RULE=
cleardef P-end-clear; usbdef P-end; mark P-end; send ACTION_EXPORT_LOG; sleep 5   # pull the file on the data= line
uncap; puncap; pmode_off; xrestore $OUT/backup-P.xml                               # the backup has no debug-usb-read-fault
A shell settings put global stay_on_while_plugged_in "$(sed -n 's/^P stay_on_before=\([0-9]*\).*/\1/p' $OUT/marks.txt | head -1)"
if s24up; then SA shell settings put global stay_on_while_plugged_in "$(sed -n 's/^S24 stay_on_before=\([0-9]*\).*/\1/p' $OUT/marks.txt | head -1)"
  echo "S24 end stay_on=$(SA shell settings get global stay_on_while_plugged_in | tr -d '\r') adb_enabled=$(SA shell settings get global adb_enabled | tr -d '\r') usb_config=[$(SA shell getprop sys.usb.config | tr -d '\r')]" >> $OUT/marks.txt; fi
cue "RESTORE 1: on D-S24P, Android Auto settings > Previously connected cars: forget only the car this round added. Say its name"
cue "RESTORE 2: if you turned Add new cars on in Prepare, turn it off. Check Connections > Mobile Hotspot and Tethering > USB tethering is off"
cue "RESTORE 3: Developer options > Wireless debugging > Paired devices: forget the test PC, then turn Wireless debugging off"
cue "RESTORE 4: if Default USB configuration exists, check it shows the value from Prepare. Then turn Developer options off. Say done"
adb disconnect "$S24_W" >/dev/null 2>&1
```

## 7. The deciding lines

Every app string prints at INFO except the accepted-device `UsbDiagnostics:   ` line (VERBOSE) and the fault line
(WARN). Each was checked with `git grep -F` at `15a13bff`. Windows: a plug is `<P>-cue` to `<P>-unplug`; Q3 to Q5 also
`/pre` (`<P>-cue` to `<P>-verb`) and `/user` (`<P>-verb` to `<P>-end`); a Q1 mode `/<mode>` (its MODE mark to its read
mark); an L rep `<K>-fault` to `<K>-end`. Files: `P-hu.txt`, `marks.txt`, `desc.tsv`, `cycles.tsv`, `x1.tsv`,
`phone.tsv` and `<P>-pmode.txt`.

| Id | Line (fixed substring) | Read |
|---|---|---|
| L-RX, L-MK | `AutomationReceiver: `, `AutomationMarker: ` | a verb landed (no line: the step is void); window bounds |
| L-PLUG | `Normal USB device attached: `, or `USB Intent: ` with `USB_DEVICE_ATTACHED`, or `Ignoring USB device attached in service (` | a plug or a mode re-enumeration |
| L-DIAG | `UsbDiagnostics:   `, the name with `VID: 04E8 PID: ....`, ` [`, `class %02X/%02X/%02X`, `, permission` or `, no permission`, `] `, `accepted: ` or `rejected: ` and the reason, ` \| if%d` triples (composed) | the identity, read by `desc8.py` |
| L-DEC | `UsbLauncher: permission for ` with `identity=`, ` mtp=`, ` filter=`, ` activity=`, ` listen=`, ` attach=`, ` source=`, ` action=` (composed) | one decision; the app's own `mtp=` and `filter=` |
| L-REQ, L-REF | `Requesting USB permission for `; `UsbLauncher: a dialog for ` ... `is already open; not opening a second` | a request of ours; a second ask refused |
| T-ANS, T-GRANT | `UsbPermissionAsks: answer for ` ... ` (granted=` ... `); entry ` (composed); `USB permission granted for `, `USB permission denied for ` | the answers |
| L-NOSW, L-OPEN | `UsbLauncher: not switching ` ... ` on a plug-in: ` `Connect when plugged in is off`; `UsbAttachedActivity: Connect when plugged in is off; opening the app, not switching ` | a plug-in that did not switch |
| L-SW, L-SWF | `Switching USB device to accessory mode `, `Switching to accessory mode.`, `Successfully requested switch to accessory mode for `, `UsbAccessoryMode: Performing AOA switch`, `Acc start sent (len=`; `UsbAccessoryMode: AOA switch failed: ` | a switch started; the phone refused it |
| L-ACC, L-SSL, L-MS | `USB accessory device attached, connecting.`; `SSL handshake complete`; `Media Sink Setup Request: ` | accessory identity; a session; the phone answered |
| F-FAULT | `StandardUsbProjectionConnection: debug fault, discarding a ` ... ` byte read as failed.` | the fault fired (once per arm) |
| F-DESYNC | `AapRead: frame header on channel ` ... ` carries flags 0x` ... `, which AAP never sets.` | `ab530358` ended the session |
| F-WRONG | `WRONG FLAG: ` | the old path read body bytes as frames |
| F-NEW, F-OLD | `AapService: USB link lost with the phone still attached. Reconnecting in `; `AapService: USB disconnect. Scheduling reconnect check in ` | the new recheck (auto-connect off); the old one (on) |
| F-NOTE | `AapProjectionActivity: showing the ` then `USB_LOST` or `WIRELESS_TIP`, then ` notice for a session lost on ` then `USB` or `WIFI` (composed) | one notice shown (`usb_notice`, `wifi_notice`) |
| F-OVL | `Showing reconnecting overlay` | the overlay (information) |
| F-TP | `Throughput over ` with `fed=` | R3's idle check |
| D-MATCH | `MATCH! Starting AapService` | discard rule (template §4) |

**System strings, not in `app/src`:** `START u0`, `.usb.UsbConfirmActivity`,
`.usb.UsbPermissionActivity`, `.usb.UsbResolverActivity`, `notification_enqueue` with `com.android.mtp`,
`USB_DEVICE_DETACHED`; in `dumpsys usb` `vendor_id=`, `interfaces={`, `name=`, `class=` or `UsbDevice[`,
`UsbInterface[mId=` (neither form is verified on API 35; `names_src=none` sends the grader to `<L>-hostdev.txt`, at most
210 lines); on D-S24P `sys.usb.config`, `sys.usb.state` (`accessory` is the
accessory function), `svc usb getFunctions`, `entering USB accessory mode` (may be absent on One UI), `GH.`,
`GH.CarConnSession`, `RIGMARK`; `"imported":` in the `am broadcast` reply. **FAIL lines, every window:** `FATAL
EXCEPTION` naming `com.andrerinas`, `ANR in com.andrerinas`, `NoClassDefFoundError`.

`desc8.py`: `aosp_mtp=yes` when one interface is `06/01/01` or `FF/FF/00` named `MTP`, `unknown` for an unnamed
`FF/FF/00`, else `no`. `filter_calc=match` when the device triple, an interface triple or the VID:PID is in
`usb_device_filter.xml` (`18D1:2D00`, `18D1:2D01`, `FF/FF/00`, `FF/42/01`, `06/01/01`, `E0/01/03`, `EF/04/01`,
`08/06/01`).

## 8. Runs

**Stop rule.** `NO_PLUG` is retried once as `<P>r`; two skip that cycle (INCONCLUSIVE for its item). `NO_SESSION`,
`NO_DESYNC` and `NO_RESESSION` are graded, never retried. `BATTERY_LOW` ends the stage: the rest is UNTESTABLE
(battery). A failed `setkeys` makes its run UNTESTABLE (settings). **Phone link:** `palive` runs `s24up` before every
plug. On `S24 LOST`: give WDPAIR, run `adb pair <ip>:<pairing port> <code>`, then `echo <ip>:<port> > $OUT/s24.addr`.
The stage goes on; a plug with `pm_unreach` above 0 and `phone_lines=0` has its phone items INCONCLUSIVE (no capture).

### Prepare

1. Make `$OUT/scripts/` (section 6). Give the operator the cue line (section 5) and read the rule table.
2. `$RIG_TOOLKIT/rig_preflight.sh D_POCO:wifi` into Setup notes. D-POCO at 80% or more.
3. `adb version`: platform-tools 30 or later. Record `adb mdns check`.
4. D-POCO on its PC cable (`HU=$POCO ST=P`, `. ./lib8.sh`): `A shell am start -a android.intent.action.VIEW -d
   headunit://exit`, 3 s, `A shell am force-stop $PKG`.
5. `A shell dumpsys usb > $OUT/prep-P-usb.txt`; `./usbparse.sh $OUT/prep-P-usb.txt prep`: record `defaults=`, `pairs=`
   (no `1256/26720` expected), `pkg_lines`; and `usb-blacklist`, `allow-devices` from `settings.xml`.
6. After the build, over the cable: `A shell am force-stop $PKG`; save `settings.xml` to `$OUT/backup-P.xml`; `A install
   -r -d $OUT/apk-C.apk`; `launch`; `ident P-new $CAND` MUST read `commit_15a13bff=1` and the `apk-C.apk` md5, else
   UNTESTABLE (build). Push `xpcp.sh` to `/data/local/tmp/` and `chmod 755` it.
7. D-S24P by hand: DEVON, DEBUGCHK, WDPAIR. Then `adb pair <ip>:<pairing port> <code>` (MUST print `Successfully
   paired`), WDADDR, `adb connect <ip>:<port>`, `printf 'S24_IP=%s\nS24_W=%s\n' <ip> <ip>:<port> > s24.env`, `.
   ./lib8.sh; s24up` (MUST return 0). Record whether `adb mdns services` lists `<ip>:`.
8. Into Setup notes over `SA shell`: `getprop ro.product.model`, `ro.build.version.release`, `ro.build.version.sdk`,
   `ro.build.version.oneui`, `ro.build.display.id`; Android Auto's `versionName` (`dumpsys package
   com.google.android.projection.gearhead`); `settings get global adb_enabled` (MUST be `0`, else stop: DEBUGCHK) and
   `adb_wifi_enabled`; `getprop sys.usb.config`, `svc usb getFunctions`, `dumpsys usb | grep -aiE
   'screen.?unlocked.?functions'`. Record `S24 stay_on_before=` into `marks.txt`, then `SA shell svc power stayon true`.
   ABOUT.
9. DEVREAD, AAREAD, BLOCKREAD. If "Default USB configuration" exists, run `Q5=1 ./stage8.sh`, else `./stage8.sh`. If
   "Add new cars" is off, the operator turns it on (restored at Close). Auto Blocker: record only.
10. Wireless adb for D-POCO (`tcpip 5555`, or an existing wireless session as round 7 did). Write `POCO_W=` to
    `wireless.env`. `chmod +x` the scripts. Nothing on D-POCO's port but its PC cable; no dialog on its screen.

Then `./stage8.sh`, then `./close8.sh`. Grade from the files in section 7. "Android's dialogs" are `confirm_A +
confirm_B + confirm_x + resolver`; "ours" are `perm_A + perm_B + perm_x`. Spoken answers (COUNT, MODEREAD, TOAST) are a
second column; a disagreement is a finding, not a verdict.

### Q1, the descriptor of each Samsung mode

Why: every later grade needs the identity of each mode, and the reporter's mode is one of them. `NCKEYS`, dialogs
cancelled. Plugs `Q1-w1` (Wireless debugging on, `ft`), `Q1-w2` (off, `ft`, no phone capture), `Q1-s` (on; `ft`,
`tether`, `midi`, `ptp`, `chg` in one plug).

PASS needs:

1. **Wireless debugging check:** `desc.tsv` `Q1-w1` and `Q1-w2` have the same `vidpid`, `devclass`, `ifs`, and
   `adb_if=0`. If not, write "WD changes the descriptor", quote both, and mark Q2 to Q5 "not representative of the
   reporter". This item alone is not a Q1 FAIL.
2. Every mode has a row with a `vidpid` and `n_if` 1 or more, and `phone config=` or `<P>-mode` names the mode.
3. Where `aosp_mtp` is `yes` or `no`, `app_mtp` is the same word; where both are read, `app_filter` equals `filter_calc`.
4. Android did what the app predicts: `app_filter=match`, `app_mtp=no` gives `confirm_A` 1 or more and `dec_A` holds
   `wait`; `app_mtp=yes` gives `confirm_A=0` and `dec_A` holds `ask`; `app_filter=none` gives `confirm_A=0`.
5. `fatal=0` everywhere.

FAIL if item 3 fails, or item 4 fails with `perm_A` 1 or more on that mode, or item 5 fails. INCONCLUSIVE if a mode has
no row (`NO_REATTACH`, `diag_lines=0`), or `names_src=none` leaves `aosp_mtp=unknown` (quote `<L>-hostdev.txt`), or
item 4 fails with `perm_A=0` (a finding). Report one line per mode: filter match, AOSP MTP, verdict, `mtpn`.

### Q2, File transfer, three plugs after a CLEAR. The point of the round

Why: the reporter's mode, against round 7's expectation, on Android 15. `S1KEYS` after `cleardef Q2-clear`
(`defaults=0`). Plug A runs Android Auto's setup (300 s cap). Every dialog ticked and OKed.

Expected (round 7 TD-P): plug A `mtpn` 1, `perm_A` 1, `req_A` 1, `confirm_B` 1, a session; plugs B and C no dialog and
a session.

PASS needs:

1. `Q2-pA`: `aosp_mtp=yes`, `app_mtp=yes`, `dec_A` holds `ask`; `req_A=1`, `perm_A=1`, `confirm_A=0`, `stacked=0`;
   `confirm_B=1`, `req_B=0`; `sw_lines`, `attach_B`, `ssl`, `ms` each 1 or more; `pm_acc` 1 or more (phone);
   `ans_true_ended` 1 or more; COUNT 2 ("Allow", "Open").
2. `Q2-pA` `after=` lists `1256/26720` and `6353/11520` (or `6353/11521`).
3. `Q2-pB` and `Q2-pC`, each with plug A's `vidpid` and `ifs`: `dlg_all=0`, `req_A=0`, `req_B=0`; `ssl`, `ms`,
   `pm_acc` each 1 or more; `attach_A` 1 or more (a plug was seen); COUNT 0.
4. `fatal=0`.

FAIL if any plug has `stacked` 1 or more or both `confirm_A` and `perm_A` 1 or more; two requests for one identity
(`req_A` or `req_B` 2 or more); a request of ours on plug B or C while item 2 held and the identity matched; a grant
with no session while `pm_acc` is 1 or more; or `fatal` 1 or more. Name the plug and item.

INCONCLUSIVE if plug A reads `aosp_mtp=no` (not in an MTP mode at the plug: quote `pm_first`, `<P>-mode`); item 2
fails because a dialog had no tick box (then dialogs on B and C are **the finding the user wants**: a dialog on every
plug on API 35); plug B or C comes back with another `vidpid` or `ifs` (the phone left File transfer: quote `pm_first`,
`pm_seq`, the COUNT mode); or no phone capture (grade the host items only). In one line: does D-S24P stay in File
transfer after each replug?

### L, USB link loss on D-S24P (R1 to R4 of `gh-1204/plan-r1.md`). R1 is the point

Why: `ab530358` and `9371f46d` must end a desynced session at once, reconnect without a replug, and show one honest
notice. One plug `L-a` (`S1KEYS`, auto-connect at defaults, i.e. off) carries R3 (`R3-1` to `R3-5`, 10 s idle windows)
then R1 (`R1-1` to `R1-5`, one fault each). Then R4 (`R4-1` to `R4-3`, one plug and pull each). Then plug `L-b`
(`ONKEYS`) carries R1-on (`R1on-1` to `R1on-3`). R2 is graded on the R1 and R1-on reps. Each rep's numbers are its
`marks.txt` line from `lcount`.

**R1** PASS needs, in 5 of 5 reps: `imported=1`; `fault=1`; `desync=1` with `t_desync` within 300 ms after `t_fault`;
`wrongflag=0`; `lost_attached=1` and `sched=0`; `ssl` 1 or more with `t_ssl` within 15 s after `t_fault`; `ms` 1 or
more; `detach=0` (no replug); `fatal=0`; and the phone's `phone config=` line of the rep holds `accessory` (D-S24P
capture). **R1-on** PASS needs the same in 3 of 3, except `sched=1` and `lost_attached=0`. FAIL if a rep has `fault=1`
and then `desync=0` with `wrongflag` 1 or more (the old path), or a desync with no new session in 15 s while
`detach=0`, or `fault` 2 or more (not one-shot), or `fatal` 1 or more. INCONCLUSIVE if `imported` is not `1` (quote
`<K>-arm.txt`), or `fault=0` (the fault never fired).

**R2** PASS needs, in every R1 and R1-on rep with a desync: `usb_notice=1`, `wifi_notice=0`, `any_notice=1`. The TOAST
answer is the second column. FAIL if a rep has `usb_notice` 0 or 2 or more, or `wifi_notice` 1 or more.

**R3** PASS needs, in 5 of 5 windows: `desync=0`, `ssl=0`, `detach=0`, `fatal=0`; the session lives on into R1 (`R1-1`
has `fault=1`). Record `fed=`. If every window reads `fed` above 100 per 5 s, write "the phone was not idle" and grade
INCONCLUSIVE: the reads that matter are the empty ones.

**R4** PASS needs, in 3 of 3: `detach` 1 or more, `ssl=0` after the pull, `any_notice=0` (no `USB_LOST` and no
`WIRELESS_TIP` line). `lost_attached` 1 is allowed and recorded (build notes: a pull may arm one
recheck that finds nothing). FAIL if a notice shows or a session forms. INCONCLUSIVE on `NO_DETACH`.

**R5** UNTESTABLE (section 3: no wireless route on this rig today without D-HU, D-MOTO and Bluetooth;
`DisconnectNoticePolicyTest` covers the rule).

### Q3, Charging only and the USB button. The point of the round

Why: a user may pick Charging only; the USB button must still connect if the phone allows it. `NCKEYS`. `Q3-p1`: the
plug opens the app at most, MODE `chg`, then the verb. `Q3-p2` (45 s later): MODEREAD only.

PASS needs:

1. `Q3-p1/pre`: `sw_lines=0`, `ssl=0`.
2. `desc.tsv` `Q3-p1` has a `vidpid`, and `Q3-p1-m-chg reattach=` is in `marks.txt`.
3. `Q3-p1/user`: `rx_check`, `sw_lines`, `attach_B`, `ssl`, `ms`, `pm_acc` each 1 or more; `fatal=0`.
4. `Q3-p2`: a `desc.tsv` row, `pm_first` and `<P>-mode` recorded, and `sw_lines=0`.

FAIL if `/user` shows the verb landed with an `accepted:` verdict for the Charging-only identity and then neither a
request (`reqs=0`) nor a switch (`sw_lines=0`); or `req_A` 2 or more, `stacked` 1 or more, a `source=user action=ask`
with `reqs=0` and `grants=0`, or `fatal` 1 or more. `NO_USER_DEC` is information (a device that holds permission
prints no decision).

INCONCLUSIVE if `/pre` has `sw_lines` 1 or more (quote the first switch line); the verdict is `rejected:` (the USB
button cannot connect from Charging only: **a product finding**, quote `ifs`); `Acc start sent (len=` or `AOA switch
failed: ` with `attach_B=0` (the phone refused the switch); or `NO_USER_DEC` beside an `AutomationReceiver: ` ...
` failed: ` line. Report which mode plug 2 came back in, and whether its identity equals `Q3-p1-start` (File transfer)
or `Q3-p1` (Charging only).

### Q4 (optional), PTP and MIDI with the USB button

Runs only after a `Q3-p1/user` session. One plug per mode: MODE, then the verb (a plug-in would switch in File
transfer before the mode change, so only the verb is a clean test). PASS per mode: a `desc.tsv` row; `/user`
`rx_check` 1 or more; then a session (`ssl`, `ms`, `pm_acc` 1 or more) or a `rejected:` verdict with `req_A=0`;
`fatal=0`. FAIL on `req_A` 2 or more, `stacked` 1 or more, a request of ours for an identity the app rejects, or
`fatal`. INCONCLUSIVE if the mode was not reached or the phone refused the switch. UNTESTABLE: `Q4 UNTESTABLE
no_session_in_Q3`.

### Q5 (optional), Default USB configuration set to Charging only

Runs only with `Q5=1`. Two plugs, MODEREAD, then the verb; then the value goes back. PASS per plug: `pm_first` and the
mode answer recorded; a `desc.tsv` row; `/pre` `sw_lines=0`; `/user` a session or a `rejected:` verdict with
`req_A=0`; `fatal=0`; and `S24-defusb-after` equals `S24-defusb`. FAIL as Q4. INCONCLUSIVE if both plugs came up in
another mode (the setting did not apply: a finding). UNTESTABLE if the setting does not exist.

### Close

`./close8.sh`. Quote `P restored`, `P-end` `defaults=` and `pairs=`, and `S24 end` (`stay_on` equal to `S24
stay_on_before`, `adb_enabled=0`, no `rndis` or `ncm` in `usb_config`). Record every RESTORE answer with `said
S24-restore`. Developer options off is the last step.

## 9. Do not re-run

| Run | Round | Verdict |
|---|---|---|
| R4a-c, R5a-c, R5t, F4a-c | 2, 3 | PASS: dialogs on a first connect, the verb asks as the user |
| B1 to B10n, L1b | 4 | PASS: Connect when plugged in, the settings hold, detection off, the boot start |
| S1-H-*, S2-*, BOOT, B8, ORD | 5 | PASS: API 17 offers the app once and the tick sticks; the blacklist |
| DR-H, TD-H, R-H, L-H | 6 | PASS: API 17 restart and rebuild, first connect, the named key |
| TD-P, F-P, F-H, L-P | 7 | PASS: API 35 restart with our dialog up; first plug on API 17 and 35; the blacklisted row |

## 10. Report back

Every run's verdict with its deciding numbers (the PASS items above), then: Q1's line per mode and the Wireless
debugging check; Q2's answer to "no dialog on the replugs, and does the phone stay in File transfer"; per L rep the
`lcount` line (with `usb_notice` and `wifi_notice`) and the TOAST answer; Q3's Charging-only verdict and plug 2's mode and identity. Also
D-S24P's model, One UI, Android and Android Auto versions; Default USB configuration and its options; Add new cars and
Auto Blocker; whether mDNS worked; every `S24 LOST`, `NO_*` mark and `BATTERY_UNREAD`; every RESTORE answer.

```decisive-strings
AutomationReceiver: 
 failed: 
AutomationMarker: 
ACTION_CHECK_USB
ACTION_SET_SETTINGS
open-headunit-settings
debug-usb-read-fault
Normal USB device attached: 
USB Intent: 
Ignoring USB device attached in service (
USB accessory device attached, connecting.
UsbDiagnostics:   
class %02X/%02X/%02X
, permission
, no permission
 | if
accepted: 
rejected: 
already in accessory mode
no Android interface
AOAP (vendor interface named 
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
UsbLauncher: a dialog for 
is already open; not opening a second
UsbPermissionAsks: answer for 
 (granted=
); entry 
USB permission granted for 
USB permission denied for 
UsbLauncher: not switching 
 on a plug-in: 
Connect when plugged in is off
UsbAttachedActivity: Connect when plugged in is off; opening the app, not switching 
Switching USB device to accessory mode 
Switching to accessory mode.
Successfully requested switch to accessory mode for 
UsbAccessoryMode: Performing AOA switch
UsbAccessoryMode: AOA switch failed: 
Acc start sent (len=
SSL handshake complete
Media Sink Setup Request: 
StandardUsbProjectionConnection: debug fault, discarding a 
 byte read as failed.
AapRead: frame header on channel 
carries flags 0x
, which AAP never sets. 
WRONG FLAG: 
AapService: USB link lost with the phone still attached. Reconnecting in 
AapService: USB disconnect. Scheduling reconnect check in 
Showing reconnecting overlay
Throughput over 
fed=
AapProjectionActivity: showing the 
 notice for a session lost on 
USB_LOST
WIRELESS_TIP
MATCH! Starting AapService
```
