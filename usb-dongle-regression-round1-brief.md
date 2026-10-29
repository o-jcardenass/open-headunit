# usb-dongle-regression, round 1 brief

Published name on the transfer branch: `usb-dongle-regression-round1-brief.md`. Results file: `usb-dongle-regression-round1-results.md`. Evidence: create the release `rig-evidence-usb-dongle-regression` and add the asset `usb-dongle-regression-round1-captures.zip`.

**The point of the round is P-C against P-B.** A wireless Android Auto USB dongle fails on every attempt with v.3.5.0 and worked with earlier versions. P-B reproduces it on the rig's dongle. P-C is v.3.5.0 with one change: the announced keycode list cut back to the 50 codes of v.3.5.0-beta4. If P-C projects where P-B does not, the larger keycode list is the cause.

Queue line: units D-POCO, D-HP, D-MOTO and the dongle. D-HU and D-SAM are free, so this round can pair with a round on those two (template §3b).

## 1. Build and baseline

No candidate branch. Three arms from release commits plus one local diagnostic commit, all built on the rig in one worktree so they share one signing key. Do not install the upstream release APKs: they carry another key and `install -r` refuses them.

| Arm | Source | Commit | Tree |
|---|---|---|---|
| A | v.3.4.0 | `013b05bc0e7f5a881ea1d4363b9275df69bd5f5a` | `e86e02a1885d9e719e4e11064a2d203d9f179e04` |
| B | v.3.5.0 | `1c1b5b7261ebebde8000778ff2c06e3af3e3a326` | `c126e7a937476fb9500c59986fef1320767198ac` |
| C | v.3.5.0 plus `armc_keycodes.py` (below), local commit, never pushed | parent `1c1b5b72…` | record it |
| b4, b5 | Stage E only, built only if Stage E runs: v.3.5.0-beta4 `0cbff00432d13c164799fe2d20e79444be2c0983`, v.3.5.0-beta5 `d25c07031f345e91cd6045aa89e02eb50a156d12` | | |

All five commits are on `fork/main`. The fork carries no release tags, so check out by SHA.

- **JVM tests:** arm C on the dev host: 3309 tests, 7 failures, all in `KeyCodeTest`, which asserts the 98-code list that arm C removes on purpose. A and B are release commits. Do not run tests on the rig.
- **History:** nothing rewritten; this is round 1.

**prep.sh**

```bash
RIG=$(dirname "$(find ~ -maxdepth 3 -type d -name rig-toolkit -print -quit)"); echo "$RIG"   # MUST print a non-empty path
RIG_TOOLKIT=$RIG/rig-toolkit
OUT=$RIG/rig-data/rounds/usb-dongle-regression-round1; APKS=$RIG/rig-data/apks/usb-dongle-regression-round1; mkdir -p $OUT $APKS
git fetch fork main
git cat-file -t 1c1b5b7261ebebde8000778ff2c06e3af3e3a326    # MUST print commit
git cat-file -t 013b05bc0e7f5a881ea1d4363b9275df69bd5f5a    # MUST print commit
```

Make the worktree with `$RIG_TOOLKIT/wt-new.sh` (usage in `$RIG_TOOLKIT/TOOLS.md`), name `usb-dongle-r1`, at `1c1b5b7261ebebde8000778ff2c06e3af3e3a326`. Set `WT` to its path. Copy `local.properties` into `$WT` if it has none. Save `armc_keycodes.py` (section 6) in `$OUT`. Build in the order B, C, A, so the two arms on one base follow each other:

**build.sh**

```bash
b() { $RIG_TOOLKIT/rig_thermal.sh wait 70; HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh && cp $WT/apks/com.andrerinas.headunitrevived_*.apk "$APKS/$1.apk" && md5sum "$APKS/$1.apk"; }
git -C $WT checkout -q --detach 1c1b5b7261ebebde8000778ff2c06e3af3e3a326
git -C $WT rev-parse 'HEAD^{tree}'      # MUST print c126e7a937476fb9500c59986fef1320767198ac
b B
( cd $WT && python3 -I $OUT/armc_keycodes.py )            # MUST print: armc: KeyCode.supported replaced with the beta4 list
git -C $WT diff --stat                                     # MUST print 1 file changed, 57 deletions(-)
git -C $WT -c user.name=rig -c user.email=rig@local commit -qam "rig: beta4 keycode list"
git -C $WT rev-parse HEAD^                                 # MUST print 1c1b5b7261ebebde8000778ff2c06e3af3e3a326
git -C $WT rev-parse --short=12 HEAD                       # C's commit: record it
b C
git -C $WT checkout -q --detach 013b05bc0e7f5a881ea1d4363b9275df69bd5f5a
git -C $WT rev-parse 'HEAD^{tree}'      # MUST print e86e02a1885d9e719e4e11064a2d203d9f179e04
b A
$WT/gradlew -p $WT --stop
```

If `apks/` holds another file name, copy the one APK `build_hur_cool.sh` names. The three md5s must differ. **Stop and escalate** if a tree differs, a build fails, or the arm C diff is not 57 deletions in one file.

### 1a. Identity gate, after each install

Pull the installed APK with a real `adb pull` to `$OUT/installed-<arm>-<unit>.apk` and hash it locally. It must equal the md5 of `$APKS/<arm>.apk`. Then:

**ident.sh**

```bash
for s in AapTlsWriter StaleAccessoryRecoveryPolicy; do printf '%s\t' "$s"; unzip -p "$1" 'classes*.dex' | strings | grep -cF "$s"; done
```

Both counts 0 on A, 1 or more on B and C. `send ACTION_QUERY_STATE` must reply with `commit` `1c1b5b7261eb` on B, C's 12-character commit on C, and `013b05bc0e7f` on A. A and B differ in DEX; B and C differ only in the commit.

## 2. What this is and why it exists

**The defect.** A cheap wireless Android Auto dongle (it enumerates as `smartBox`, `2717:FF40`, then switches to `18D1:2D00`) fails every attempt on v.3.5.0 on an API 23 head unit. A phone on a cable projects on the same build. The reporter's six logs hold 39 dongle handshakes and none rendered a frame. 15 reached SSL and the phone's `Service Discovery Request`. After our response the phone answered in 1 of the 15. In the other 14 nothing more came from the phone: no `Audio Focus Request`, no `Sensor Start Request`, no `Media Sink Setup Request`. 1 to 3 s later the dongle left the bus (`USB_DEVICE_DETACHED`). Our writes completed in 14 of the 15. No read-side or TLS failure line appears.

**Why the keycode list.** The announced `keycodes_supported` grew from 50 codes (v.3.4.0 and beta4) to 98 (beta5 and v.3.5.0), in `Input: advertise alphabetic and editing keycodes`. It is the one change in that window that alters the response on every attempt. A dongle that parses the response, or mishandles one that spans two reads, would fail most of the time and not every time. That matches the one session that worked with the same content. The night key `65539` is not announced in any arm.

**What is ruled out, and how.** The TLS writer rewrite also arrived in beta5. The rig's dongle already ran it: `pr-stack-1047-1067` round 1 Stage U and `usb-reattach` round 4, both without the 98-code list. There the phone answered our response and video flowed (8 throughput windows in US). So on this dongle the TLS rewrite alone does not reproduce the fault.

**What a PASS would look like if the change did nothing.** P-B and P-C would both project, or both fail. Only a split (B fails, C projects) names the keycode list. A P-B that projects means the rig's dongle does not reproduce the fault at all; then C cannot be read and the round says so.

## 3. What is different about this round

**Two hosts, one dongle.** D-HU cannot host USB (`rig-quirks/topics/tooling.md`, "No USB accessory path").

| Stage | Head unit and USB host | On its OTG port | adb | Arms |
|---|---|---|---|---|
| H | D-HP (API 17), wireless adb | the dongle | D-HP wireless, D-MOTO on its PC cable | B, C, 3 cycles each |
| P | D-POCO (API 35), wireless adb | the dongle | D-POCO wireless, D-MOTO on its PC cable | A, B, C, 5 cycles each |
| E | D-POCO, as P | the dongle | as P | b4, b5, 5 cycles each; only if the stop rules send you there |

**The rig's dongle is another model.** It enumerates as `Google Pixel 4` (idle `18d1:4ee1`, accessory `18d1:2d00`) and is bonded to D-MOTO as `carplay_box_F96B`. The reporter's is `smartBox` (`2717:FF40`). If neither host reproduces the fault on P-B and H-B, that is a finding about the dongle model, not a failed round.

**D-HP's battery is degraded and the dongle draws from it over OTG.** It died mid-run in `pr-1114-audio-continuity` round 1. So Stage H runs first, on a full charge, two arms of 3 cycles (about 12 min). Gates: 60% or more to start, read before every cycle, stop Stage H at 30% or below. Its host capture can die silently (`rig-quirks/units/D-HP.md`): check the capture is alive before grading each cycle, and void the cycle if not.

**`video-profile-starvation-cap` can switch itself on.** Three sessions in a row that render nothing set it, and it then forces 720p30 and AAC, which changes the response. P-B is expected to fail, so the cap is deleted before every cycle (it is in `KEYS`, and `cycle` rewrites the keys each time).

**Settings mirror the reporter's failing runs:** USB only, wireless mode MANUAL, 1280x720 (so no fallback video config is announced), H.264, audio sink off. Identical in every arm, so the build is the only variable.

**`auto-start-on-usb` is false**, so the API 35 `BootCompleteReceiver` crash dialog of `usb-reattach` round 4 is not expected. The launch's own single-USB auto-connect finds the dongle.

**Expected INCONCLUSIVE, said up front:**

- **P-C:** P-B projects (3 or more cycles of 5). The rig's dongle then does not reproduce the fault.
- **H-C:** H-B projects (2 or more of 3).

## 4. Hand steps

| Id | Step | When | Why no verb |
|---|---|---|---|
| H0 | Unlock D-MOTO if it is behind a PIN | Prepare | adb has no way past a PIN (`rig-quirks/units/D-MOTO.md`) |
| H1 | Unplug D-HP from the PC. Plug the dongle into D-HP's OTG port | Stage H start, after D-HP's wireless adb is up | A cable is hardware |
| H2 | Unplug the dongle from D-HP and put D-HP on its PC cable. Unplug D-POCO from the PC and plug the dongle into D-POCO's OTG port | Between Stage H and Stage P, after D-POCO's wireless adb is up | A cable is hardware |
| H3 | Allow a system USB dialog for the dongle on the host. "Always" is allowed | Only if one appears | A system dialog, not our app |
| H4 | Reseat the dongle | Only if the host's `/sys/bus/usb/devices` shows root hubs only; once per stage | A cable is hardware |
| H5 | Unplug the dongle, put D-POCO back on its PC cable | Closing | A cable is hardware |
| H7 | Open a terminal with `tail -n0 -F $OUT/hand-steps.log` | Prepare | A cue must reach the operator in time |

No injected taps in this round.

## 5. Settings keys

Write with the app stopped, from each unit's Prepare backup (template §1). Both hosts are not rooted. `log-level` 2 (INFO) carries every app line in section 7: each is `AppLog.i`, on both v.3.4.0 and v.3.5.0. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on either tag, so `allow-external-configuration` is not needed.

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `0` | MANUAL: no wireless stack, as the reporter's 16:52 run |
| `connection-modes` | string set | `usb` | |
| `resolutionId` | int | `2` | 1280x720: one video config, no fallback |
| `video-codec` | string | `H.264` | as the reporter |
| `enable-audio-sink` | boolean | `false` | as the reporter: no media or speech audio channels |
| `log-level` | int | `2` | INFO |
| `onboarding-version` | int | `2` | no wizard |
| `auto-connect-last-session` | boolean | `true` | the launch's single-USB auto-connect |
| `auto-start-on-usb` | boolean | `false` | no boot redelivery crash |
| `reopen-on-reconnection` | boolean | `true` | |
| `kill-on-disconnect` | boolean | `false` | |
| `use-libusb` | boolean | `false` | Java USB path, as the reporter |
| `native-driver-selection-mode` | int | `0` | no driver selector |
| `native-poke-all-paired` | boolean | `false` | nothing pokes D-MOTO |
| `video-profile-starvation-cap` | | delete | section 3 |
| `native-aa-wireless`, `wifi-launcher-mode` | | delete | legacy mode keys |

**keys.sh**

```bash
KEYS="int:wifi-connection-mode=0 set:connection-modes=usb int:resolutionId=2 str:video-codec=H.264 bool:enable-audio-sink=false int:log-level=2 int:onboarding-version=2 bool:auto-connect-last-session=true bool:auto-start-on-usb=false bool:reopen-on-reconnection=true bool:kill-on-disconnect=false bool:use-libusb=false int:native-driver-selection-mode=0 bool:native-poke-all-paired=false del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
```

Check the writer first: `grep -c 'if v else \[\]' $OUT/ohu_setkeys.py` must print 1, and its kinds must include `str:`. If `str:` is not a kind, read `ohu_setkeys.py` for the string kind's name and use it; say so in Setup notes. After every write, `keys_ok` (from `libreattach4.sh`) must print `keys_ok ok`; it checks only `int`, `bool` and `del`, so also check `video-codec` with `grep -ac 'name="video-codec">H.264<'` on the read-back.

## 6. Shell setup

Copy `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh`, `lib1047.sh`, `lib1047r3.sh`, `libreattach4.sh` and, if present, `env.sh` into `$OUT` from `$RIG/rig-data/rounds/usb-reattach-round4/` (or round 3's `stageD/` where round 4 has none). They supply `send`, `mark` (`AutomationMarker:` on the unit, `RIGMARK` on the phone), `cue`, `nl`, `cnt`, `waitfor`, `pcnt`, `r_open`, `r_close`, `th_gate`, `pocoput` and `keys_ok`. `pocoput` writes to `$HU`; if it is D-POCO-only, use `$RIG_TOOLKIT/set_prefs_runas_host.py` for D-HP (usage in `TOOLS.md`) and say so in Setup notes. Save the three files below beside them. One adb call at a time per unit; the two log captures are the only streams beside them (house rule 8). Run each stage under `flock /tmp/ohu-rig.lock`.

**armc_keycodes.py**

```python
# Arm C: put v.3.5.0-beta4's KeyCode.supported list into the checked-out tree. Run from the worktree root.
import re, subprocess, sys
P = "app/src/main/java/com/andrerinas/openheadunit/input/KeyCode.kt"
BETA4 = "0cbff00432d13c164799fe2d20e79444be2c0983"
blk = re.compile(r"    val supported = listOf\(.*?\n    \)\.distinct\(\)\.sorted\(\)", re.S)
old = subprocess.run(["git", "show", f"{BETA4}:{P}"], capture_output=True, text=True, check=True).stdout
cur = open(P).read()
a, b = blk.findall(old), blk.findall(cur)
if len(a) != 1 or len(b) != 1: sys.exit(f"armc: expected one block each, got {len(a)} and {len(b)}")
open(P, "w").write(cur.replace(b[0], a[0]))
print("armc: KeyCode.supported replaced with the beta4 list")
```

**dongle_cycle.py**

```python
# One TSV row for the capture lines between two AutomationMarker labels: each AAP handshake, graded.
import re, sys
from datetime import datetime
cap, go, end = sys.argv[1], sys.argv[2], sys.argv[3]
L = open(cap, errors="replace").read().splitlines()
def at(lbl):
    for i, l in enumerate(L):
        if "AutomationMarker: " + lbl in l: return i
    return None
i0, i1 = at(go), at(end)
if i0 is None or i1 is None: print(f"{go}\tVOID markers_missing"); sys.exit(0)
W = L[i0:i1]
def ts(l):
    m = re.match(r"(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d+)", l)
    return datetime.strptime("2026-" + m.group(1) + " " + m.group(2), "%Y-%m-%d %H:%M:%S.%f") if m else None
st = [i for i, l in enumerate(W) if "Start Aap transport handshake" in l]
ssl = sdr = reply = silent_detach = 0; thr_max = 0; projected = 0; sel = set(); first_silent = "-"
for k, i in enumerate(st):
    seg = W[i:(st[k + 1] if k + 1 < len(st) else len(W))]
    has = lambda s: any(s in l for l in seg)
    def first(s):
        for l in seg:
            if s in l: return l
    for l in seg:
        m = re.search(r"the phone selected (\d+\.\d+)", l)
        if m: sel.add(m.group(1))
    if has("SSL handshake complete"): ssl += 1
    if not has("Service Discovery Request"): continue
    sdr += 1
    rep = has("Audio Focus Request") or has("Sensor Start Request") or has("Media Sink Setup Request")
    n = sum("Throughput over" in l for l in seg); thr_max = max(thr_max, n)
    if rep: reply += 1
    if rep and n >= 3: projected = 1
    if not rep:
        s, d = first("Service Discovery Request"), first("USB_DEVICE_DETACHED")
        if d and ts(s) and ts(d) and (ts(d) - ts(s)).total_seconds() <= 5:
            silent_detach += 1
            if first_silent == "-": first_silent = f"{(ts(d) - ts(s)).total_seconds():.2f}s"
print(f"{go}\thandshakes={len(st)}\tssl={ssl}\tsdr={sdr}\treply={reply}\tsilent_detach={silent_detach}\tfirst_silent_gap={first_silent}"
      f"\tthr_max={thr_max}\tprojected={projected}\tselected={','.join(sorted(sel)) or '-'}")
```

**libdongle1.sh** (source last)

```bash
PKG_ID=com.andrerinas.headunitrevived
MAIN=$PKG_ID/com.andrerinas.openheadunit.main.MainActivity
readers() { pgrep -fc "^adb .*logcat"; }
hu_cap_alive() { pgrep -fc "^adb -s $HU .*logcat"; }        # MUST print 1 during a run
bat() { adb -s "$HU" shell dumpsys battery | grep -a -m1 level | tr -dc 0-9; }
# phone Critical error lines between two RIGMARK labels in the run's phone capture
pcrit() { awk -v a="RIGMARK $2" -v b="RIGMARK $3" 'index($0,a){on=1} on&&/Critical error/{n++} index($0,b){on=0} END{print n+0}' "$1"; }
# cycle <RUN> <k>: one bring-up from a stopped app, a 45 s hold after SSL, then a user exit
cycle() { local run=$1 k=$2 L
  adb -s "$HU" shell am force-stop $PKG_ID; sleep 1
  $PUT $BASEXML $KEYS || { echo "$run-$k VOID keys"; return 9; }
  mark $run-$k-go; L=$(nl)
  adb -s "$HU" shell am start -n $MAIN >/dev/null
  if waitfor 60 'SSL handshake complete' $L; then sleep 45; fi
  mark $run-$k-end
  send ACTION_DISCONNECT
  waitfor 20 'USB_DEVICE_ATTACHED' $(nl) || true
  [ "$(hu_cap_alive)" = 1 ] || { echo "$run-$k VOID capture_dead"; return 8; }
  echo -e "$(python3 -I $OUT/dongle_cycle.py $CAP $run-$k-go $run-$k-end)\tcrit=$(pcrit $PCAP $run-$k-go $run-$k-end)\tbat=$(bat)" | tee -a $OUT/$run.tsv; }
```

`CAP` and `PCAP` are the unit and phone captures `r_open` opens (`$OUT/<run>.hu.logcat` and `$OUT/<run>.phone.logcat` in round 4). Check the names `r_open` uses and set them before the first `cycle`.

**Every action in this round:**

| Action | Command | Notes |
|---|---|---|
| Launch our app | `adb -s $HU shell am start -n $MAIN` | the launch's single-USB auto-connect finds the dongle |
| Hard stop | `adb -s $HU shell am force-stop com.andrerinas.headunitrevived` | |
| End a session | `send ACTION_DISCONNECT` | the user-exit path |
| Mark a step | `mark <label>` | |
| Read the build | `send ACTION_QUERY_STATE` | `commit` on the `data=` line |
| Stop the service | `send ACTION_EXIT` | Closing only |

Every `send` must print `AutomationReceiver: <action>` in the capture. A step with no such line never landed: its cycle is void, not a FAIL.

## 7. The lines that decide the runs

Checked with `grep -F` against `app/src/main` at both `013b05bc` and `1c1b5b72`. All print at INFO. Unit lines are in `$CAP`, phone lines in `$PCAP`, each cycle between its `-go` and `-end` markers.

| Line (fixed substring) | File | Meaning |
|---|---|---|
| `AutomationReceiver: ` | unit | a verb landed |
| `AutomationMarker: ` | unit | a marker |
| `Start Aap transport handshake` | unit | one AAP attempt starts |
| `the phone selected ` | unit | the version answer (report the value) |
| `SSL handshake complete` | unit | TLS done (never prefix `Handshake:`) |
| `Service Discovery Request` | unit | **the phone asked for our response** |
| `Audio Focus Request`, `Sensor Start Request`, `Media Sink Setup Request` | unit | **the phone answered our response** |
| `Throughput over` | unit | **video is rendering** (one line per 5 s) |
| `USB Intent: ` with `USB_DEVICE_DETACHED` / `USB_DEVICE_ATTACHED` | unit | the dongle left or came back |
| `Found device already in accessory mode` | unit | a USB check found the dongle |
| `UsbLauncher: stale accessory ` | unit | v.3.5.0's recovery ladder on the dongle (report) |
| `Bluetooth service announced with carAddress` | unit | v.3.5.0 only (report) |
| `MATCH! Starting AapService` | unit | discard rule |
| `Critical error` | phone | Android Auto ended the session and why (graded on P-B, P-C) |
| `RIGMARK` | phone | phone markers |

**A cycle projects** when `dongle_cycle.py` prints `projected=1`: a session where the phone answered our response and 3 or more `Throughput over` lines followed.

## 8. Runs

Estimated time: Prepare and three builds 40 min, Stage H 15 min, Stage P 30 min, Closing 5 min. **Round total: about 90 min**, plus Stage E (25 min with two builds) only if the stop rules send you there. Write the results file after every run.

**Preflight in every run.** `readers` prints 0 before `r_open`. The keys read back (`keys_ok ok`, plus the `video-codec` check). The identity gate (1a) passed for that arm on that unit. `MATCH! Starting AapService` 0 times in the run's capture.

### Prepare

D-HP, D-POCO and D-MOTO on their PC cables. The dongle unplugged. D-HP charged to 100% if possible.

1. `prep.sh` and `build.sh` (section 1).
2. **Batteries:** `dumpsys battery | grep -a -m1 level` on D-HP and D-POCO. D-HP 60% or more, D-POCO 60% or more. Record both.
3. **Back up each host** with the app stopped: the installed APK (`adb pull` of `pm path`) to `$APKS/prior-<unit>.apk` with its md5, `settings.xml` to `$OUT/settings_backup_<unit>.xml`, and `dumpsys usb | grep -i -A4 headunitrevived` to `$OUT/usb-grants-before-<unit>.txt`. Diff each `settings.xml` against its last round's backup and state the delta.
4. **Install B on D-HP and on D-POCO** (`adb install -r -d $APKS/B.apk`), then the 1a gate on each. Diff `settings.xml` against the backup after the install.
5. **D-MOTO:** Gearhead `versionName` (record); `svc power stayon true`; `svc wifi enable`, `svc bluetooth enable`; `dumpsys bluetooth_manager` reads enabled/ON; the bonded list holds `carplay_box_F96B`; `input keyevent KEYCODE_HOME`.
6. **Wireless adb for both hosts:** `adb -s <serial> tcpip 5555`; read `wlan0`'s address; `adb connect <ip>:5555`; `getprop ro.product.model` answers. Record `HP_IP` and `POCO_IP`. D-HP's `svc wifi enable` can take 25 to 30 s to take effect (`rig-quirks/units/D-HP.md`): poll `dumpsys wifi | grep -a 'Wi-Fi is'`.
7. **One batched request to the operator:** "1) Unlock D-MOTO if it has a PIN. 2) Open the cue terminal: `tail -n0 -F <OUT>/hand-steps.log`. 3) Now: unplug D-HP from the PC and plug the dongle into D-HP's OTG port. Leave D-MOTO and D-POCO on their PC cables. 4) About 15 minutes later a cue asks you to move the dongle from D-HP to D-POCO. 5) At the end a cue asks you to unplug the dongle and recable D-POCO. A system USB dialog may appear; 'Always' is allowed for the dongle."
8. Shell: section 6. `readers` prints 0.

### Stage H. D-HP as host (B then C, 3 cycles each)

**stageH.sh**

```bash
HU=$HP_IP:5555; MOTO=ZY22GC3BM4; PH=$MOTO; UNIT=D-HP; BASEXML=$OUT/settings_backup_D-HP.xml; PUT=pocoput
cd $OUT; source ./ohu_lib.sh; source ./ptr_lib.sh; source ./lib1047.sh; source ./lib1047r3.sh; [ -f ./env.sh ] && source ./env.sh; source ./libreattach4.sh; source ./libdongle1.sh
adb -s $HU shell ls /sys/bus/usb/devices        # MUST show more than the root hubs; else cue "H4: reseat the dongle in D-HP", once, then re-check
for ARM in B C; do
  [ $ARM = C ] && { adb -s $HU shell am force-stop $PKG_ID; adb -s $HU install -r -d $APKS/C.apk; }   # then the 1a gate for C on D-HP
  RUN=H-$ARM; th_gate || exit 3; r_open $RUN $KEYS
  for k in 1 2 3; do
    b=$(bat); echo "$RUN-$k bat=$b" >> $OUT/battery.log; [ "$b" -le 30 ] && { echo "STOP stage H: battery $b"; break 2; }
    cycle $RUN $k; sleep 3
  done
  r_close $RUN
done
readers          # MUST print 0
```

- **H-B:** projects when 2 or more of 3 cycles print `projected=1`; fails when 0 of 3. 1 of 3 is INCONCLUSIVE.
- **H-C:** read only when H-B fails. PASS when 2 or more of 3 project; FAIL when 0 of 3; 1 of 3 INCONCLUSIVE.
- **Report per cycle:** the whole TSV row. For H-B cycles that fail, quote the `Service Discovery Request` line, the first `USB_DEVICE_DETACHED` after it, and the lines between them from the app (cut `SurfaceView` and `Gralloc`).

Then `cue "H2: move the dongle from D-HP to D-POCO's OTG port, and put D-HP on its PC cable"`, and wait for `adb -s $POCO_IP:5555 shell ls /sys/bus/usb/devices` to show more than the root hubs (cap 5 min, then one reseat cue).

### Stage P. D-POCO as host (B, then C, then A, 5 cycles each). P-C IS THE POINT OF THE ROUND

**stageP.sh**

```bash
HU=$POCO_IP:5555; MOTO=ZY22GC3BM4; PH=$MOTO; UNIT=D-POCO; BASEXML=$OUT/settings_backup_D-POCO.xml; PUT=pocoput
for ARM in B C A; do
  [ $ARM != B ] && { adb -s $HU shell am force-stop $PKG_ID; adb -s $HU install -r -d $APKS/$ARM.apk; }   # then the 1a gate
  RUN=P-$ARM; th_gate || exit 3; bat_gate || exit 4; r_open $RUN $KEYS
  for k in 1 2 3 4 5; do cycle $RUN $k; sleep 3; done
  r_close $RUN
  [ $ARM = B ] && [ $(grep -ac 'projected=1' $OUT/P-B.tsv) -ge 3 ] && { echo "P-B projects: skip P-C, run P-A"; }
done
readers          # MUST print 0
```

If P-B projects in 3 or more cycles, skip P-C (it cannot be read) and still run P-A.

- **P-B (reproduce):** reproduces when 1 or fewer of 5 cycles project **and** `silent_detach` 1 or more in 3 or more cycles. Projects (does not reproduce) when 3 or more of 5 project. Otherwise INCONCLUSIVE. Phone, graded: `crit` per cycle; quote the first `Critical error` line of each failing cycle with the 5 phone lines before it.
- **P-C, THE POINT:** **PASS** (the keycode list is the cause) when P-B reproduces **and** 3 or more of 5 P-C cycles project **and** `crit` is 0 in every P-C cycle that projects. **FAIL** (not the keycode list) when P-B reproduces and 1 or fewer of 5 P-C cycles project. **INCONCLUSIVE** otherwise, or when P-B does not reproduce.
- **P-A (control):** PASS when 3 or more of 5 project. A P-A FAIL makes the whole stage INCONCLUSIVE (the rig or the dongle is not healthy); say so.
- **Report per cycle:** the whole TSV row, and `selected`. Quote each `UsbLauncher: stale accessory ` line.

### Stage E. Only if P-C FAILS and P-A PASSES

The window then lies in the rest of beta4..v.3.5.0, outside the keycode list. Build b4 and b5 in `$WT` (thermal gate first; no capture running): `git -C $WT checkout -q --detach <sha>` with the SHAs in section 1, `b b4`, `b b5`. Install each with the 1a gate (`AapTlsWriter` count: 0 on b4, 1 or more on b5). Run the Stage P block with `for ARM in b4 b5` and 5 cycles each.

- **Report:** projected cycles per arm. b4 projecting and b5 not puts the fault inside beta4..beta5 with the keycode list ruled out; both failing puts it before beta4; both projecting puts it after beta5.

### Closing

Run all of it, whatever happened before.

1. On each host: `send ACTION_EXIT`; `adb -s $HU shell am start -a android.intent.action.VIEW -d headunit://exit`; `sleep 3`; force-stop.
2. Reinstall each host's Prepare APK (`adb install -r -d $APKS/prior-<unit>.apk`), pull it, check the md5.
3. Restore each `settings.xml` from its backup and diff: the diff must be empty.
4. `dumpsys usb | grep -i -A4 headunitrevived` on each host into `$OUT/usb-grants-after-<unit>.txt`; report any new grant.
5. `adb -s ZY22GC3BM4 shell svc power stayon false`.
6. `cue "H5: unplug the dongle from D-POCO and put D-POCO back on its PC cable"`. Then `adb -s $POCO_IP:5555 usb` and `adb -s $HP_IP:5555 usb`.
7. `readers` prints 0. Kill any leftover thermal watcher by pid. The local arm C commit stays local.

### Stop rules

- Stage H: D-HP at 30% or below stops Stage H; mark the rest of it UNTESTABLE (battery) and go to Stage P.
- `bat_gate` false on D-POCO: stop, run the Closing, mark the rest UNTESTABLE (battery).
- A cycle voided (`VOID` in its row) is re-run once as the next `k`. At most 2 voids per arm; a third makes the arm INCONCLUSIVE.
- Stage E runs only on P-C FAIL with P-A PASS.
- Thermal rules of `rig-quirks/topics/tooling.md`: `th_gate` before every run.

## 9. Do not re-run

- **The TLS writer rewrite on this dongle:** `pr-stack-1047-1067` round 1 US and `usb-reattach` round 4, sessions answered and rendering on the rig's dongle. Stage E covers the rest of the window only if needed.
- **The late VERSION_RESPONSE discard and the arbiter:** `usb-version-retry` and `pause-auto-connect` rounds, PASS on this dongle.
- **Two permission prompts per connect:** structural (template §7b). Report a dialog; do not grade the count.

## 10. Report back

1. **P-B and P-C:** projected cycles of 5 in each, and `silent_detach` per cycle. A split (B 1 or fewer, C 3 or more) names the keycode list.
2. **H-B and H-C:** the same on the API 17 host.
3. **P-A:** projected cycles of 5, the control.

Also: `selected` per arm, every phone `Critical error` line quoted from failing cycles, D-HP's battery per cycle, every cue, and the md5s of A, B, C and the Prepare APKs. Put the captures in `usb-dongle-regression-round1-captures.zip` on the new release `rig-evidence-usb-dongle-regression`, with its size and sha256 in the results file. Include every `.tsv`, `battery.log`, `keys.log`, `hand-steps.log`, `marks.log`, every `.thermal` file, and both logcat captures of each run.
