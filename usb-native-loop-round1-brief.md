# usb-native-loop, round 1 brief

Publish name on `transfer/rig-rounds`: `usb-native-loop-round1-brief.md`. Results go in
`usb-native-loop-round1-results.md`. Evidence goes in a new release `rig-evidence-usb-native-loop`
(first round of the thread, so create it) as the asset `usb-native-loop-round1-captures.zip`.

This is a reproduction round. No fix is on trial. It builds two release tags and compares three arms
on one USB layout. The round answers two questions:

1. **H1, the point of the round.** After a USB drop, does Native mode on 3.4.0 turn one drop into a
   reconnect loop, where Helper mode on 3.4.0 and Native mode on 3.5.0 recover?
2. **H2.** Does Native mode on 3.4.0 end an uninterrupted USB session by itself within 25 minutes?

| Run | What it proves | Units |
|---|---|---|
| P | builds, APK identity, bonds, settings backup, wireless adb | D-POCO, D-MOTO, D-HU (radio only) |
| P3 | which scripted drop is closest to a cable event, and that 3.4.0 recovers from it with no wireless stack | D-POCO, D-MOTO |
| **B** | **the point of the round: 3 drop and reconnect cycles per arm, compared across N34, H34 and N35** | D-POCO, D-MOTO |
| A | one 25 min uninterrupted USB session per arm, chained after B | D-POCO, D-MOTO |
| X | restore settings, screen, radios | all |

Estimated time: builds about 35 min (one cold, one warm), P about 10 min, P3 about 12 min, each arm
about 45 min (3 arms, 135 min). Total about 3 h 15 min, plus the thermal-gate waits. The operator is
needed once, before the first run (section 3, "Hand steps"), and again only if a fallback fires.

**Queue line:** `usb-native-loop-round1-brief.md` (tags `v.3.4.0` and `v.3.5.0`, no candidate; units
D-POCO, D-MOTO, D-HU Bluetooth only). It can pair with a round that uses only D-SAM or D-HP.

## 1. Build and baseline

Two release tags, both on `origin` (upstream). The fork carries `v.3.4.0` but not `v.3.5.0`, so fetch
both from `origin`. No branch of ours is on trial, and no history was rewritten.

| Arm | Ref | Commit | Tree | `commit` in `ACTION_QUERY_STATE` | Mode |
|---|---|---|---|---|---|
| **N34** | tag `v.3.4.0` | `013b05bc0e7f5a881ea1d4363b9275df69bd5f5a` | `e86e02a1885d9e719e4e11064a2d203d9f179e04` | `013b05bc0e7f` | `wifi-connection-mode` 3 (Native) |
| **H34** | tag `v.3.4.0` (same APK) | as N34 | as N34 | `013b05bc0e7f` | `wifi-connection-mode` 2 (Helper) |
| **N35** | tag `v.3.5.0` | `1c1b5b7261ebebde8000778ff2c06e3af3e3a326` | `c126e7a937476fb9500c59986fef1320767198ac` | `1c1b5b7261eb` | `wifi-connection-mode` 3 (Native) |

A `-dirty` suffix on the `commit` is acceptable: the build worktree holds an untracked
`local.properties`.

**No unit-test count applies.** The usual dev-host JVM count belongs to a candidate SHA. These are
published release tags with no change of ours on them, so there is nothing for the suite to vouch for.
Do not run the unit tests on the rig.

Make `rig-data/rounds/usb-native-loop-round1/` and set `OUT` to it. Build both APKs first, in one
worktree, before any run. 3.4.0 first, then 3.5.0, so the second build is warm.

**get_apks.sh**

```bash
OUT=rig-data/rounds/usb-native-loop-round1; mkdir -p $OUT/apk
WT=$(ls -d worktrees/* 2>/dev/null | head -1)        # any existing worktree; else create one with wt-new.sh
git -C $WT fetch --no-tags origin tag v.3.4.0 tag v.3.5.0
git -C $WT checkout --detach 013b05bc0e7f5a881ea1d4363b9275df69bd5f5a
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print e86e02a1885d9e719e4e11064a2d203d9f179e04
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk/v340.apk at once
git -C $WT checkout --detach 1c1b5b7261ebebde8000778ff2c06e3af3e3a326
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print c126e7a937476fb9500c59986fef1320767198ac
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk/v350.apk at once
md5sum $OUT/apk/v340.apk $OUT/apk/v350.apk            # record both in Setup notes; they MUST differ
for a in v340 v350; do printf '%s %s\n' $a "$(unzip -p $OUT/apk/$a.apk 'classes*.dex' | strings | grep -c ConnectionArbiter)"; done
# v340 MUST print 0 and v350 MUST print 1 or more: the arbiter exists only in 3.5.0
```

Never run `git fetch --prune-tags` in that worktree. If a tree line or the DEX check differs, or a
build fails, stop the round.

Install with `adb install -r -d` only (`inst` in section 5). 3.4.0 is versionCode 109 and 3.5.0 is
119, and D-POCO may carry a later build, so the first install is a downgrade. Never uninstall.

## 2. What this is and why

**The report.** A head unit on 3.4.0, used over USB with the wireless mode set to Native AA. The
session connects, drops after about 10 minutes, and then tries to reconnect again and again. Turning
the home screen's WiFi toggle off changes nothing. Setting the wireless mode to Helper makes USB work
with no fault. No log came with the report.

**What the code says, on both tags.**

- The home screen's WiFi toggle does not stop the wireless stack. It gates the UI and the Bluetooth
  auto-start re-arm only. So "WiFi off" in the report is still Native mode underneath.
- A live USB session stops the wireless stack (`quiesceWirelessForWiredSession`), and Bluetooth
  auto-start does nothing while a session is up.
- Every end of a USB session re-arms the stored mode 1.5 s later (`rearmWirelessAfterWiredSession`).
  In Native mode that is a P2P group create, the RFCOMM listeners and a Bluetooth wake poke about
  2 s later. Helper mode re-arms its own discovery and sends no poke.
- **On 3.4.0 that re-arm calls `setActiveFromSettings` directly.** It skips the USB deferral
  (`deferWirelessForUsbHandoff`), which holds the wireless bring-up while an accessory is on the bus.
  **On 3.5.0 the same re-arm goes through `initWifiModeWithOptionalWait`, so the deferral applies.**
  3.5.0 also adds `ConnectionArbiter` (one attempt at a time: user, then USB, then a wireless
  handshake, then the armed stack) and the stale accessory recovery.
- No timer of 10 minutes runs during a wired session.

**The two hypotheses** (from the ticket's diagnosis):

- **H1.** The first drop has a cause outside the wireless mode, for example a USB or power event on the
  reporter's unit. In Native mode on 3.4.0, the re-arm after that drop (group create and wake poke)
  races the USB re-attach and turns one drop into a loop. Helper sends no poke, so it recovers. 3.5.0's
  deferral and arbiter should stop the race. **Falsified if** 3.4.0 Native recovers from a forced drop
  as cleanly as 3.4.0 Helper, with the re-arm running inside the re-attach.
- **H2.** Something in Native mode survives the quiesce and ends a USB session by itself after about
  10 minutes. **Falsified if** a 25 min USB session on 3.4.0 Native shows no drop.

The reporter's head unit cannot be reproduced. So the first drop may never happen by itself on the rig,
and H1 needs a forced drop (P3 picks how).

## 3. What is different about this round

- **Layout.** D-POCO is the head unit and the USB host, on wireless adb, with its OTG port free.
  D-MOTO is the projecting phone, plugged into D-POCO's OTG port, also on wireless adb. D-HU takes no
  part except that its Bluetooth is held off (next point). D-HU cannot host USB
  (`host_connected=false`, `rig-quirks/topics/tooling.md`).
- **Hold D-HU's Bluetooth off before every bring-up and every drop.** D-MOTO is bonded to D-HU's
  radio (`Navegadortz2`), and Gearhead can dial a cached head unit instead of the one under test
  (`rig-quirks/units/D-POCO.md`). It self-reverts, so `dhu_bt_off` repeats. X turns it back on.
- **No video is graded.** A phone that stands in as the head unit carries audio and no video. Grade
  session lifetime, session ends and reconnects only. "No video" is not a finding here.
- **D-POCO's screen must stay on.** The session raises the projection screen, and both tags end a
  session 16 s after the handshake if the screen never comes up. 3.5.0 also stops reconnecting by
  itself after two such ends. D-POCO's panel dozes between runs and `am start` does not wake it
  (`rig-quirks/units/D-POCO.md`). So the arm sets `svc power stayon true`, wakes the panel and dismisses
  the keyguard. Brightness goes down to save the battery. X restores all three.
- **D-MOTO's Bluetooth is off while each arm brings its first session up, and on after it.** In
  Native mode the launch pokes the phone. With Bluetooth on, D-MOTO could form the first session over
  WiFi instead of USB. Once the USB session is up, Bluetooth goes on and stays on, because H1 needs
  the poke to reach the phone after a drop.
- **The wake poke targets D-MOTO only** (`native-poke-all-paired` false, `native-poke-bt-macs` =
  D-MOTO). D-POCO is also bonded to D-HU's and D-SAM's radios, and the reporter has one phone. This key
  is the same in all three arms. Helper ignores it.
- **No station stand-down can cut D-POCO's wireless adb.** `StationStandDownPolicy.isAvailable` is
  false from API 35 on both tags. P reads `ro.build.version.sdk` and stops the round below 35.
- **D-POCO powers D-MOTO over OTG and cannot charge while it does.** The round runs about 2.5 h on
  battery. Each arm checks the level first and does not start below 40%. Part A stops at 20%.
- **Two of the §4 discard rules are measurements here.** `MATCH! Starting AapService` and a second
  `createGroup SUCCESS` are part of the mechanism H1 describes. Count them. Do not discard on them.
- **A stale USB accessory is a known rig fault on 3.4.0, and the drop is chosen to avoid it.** On
  builds before 3.5.0, a session end with the phone left in accessory mode makes every next handshake
  fail until the phone re-enumerates (`archive/rounds/usb-reattach-round2-results.md`: a plain retry
  recovered 0 of 11 cycles, `svc usb setFunctions mtp` recovered 15 of 15). Every drop below makes the
  phone leave accessory mode, as a cable pull does.
- **The rig audio keys are a deliberate worst case on D-HU.** This round does not use D-HU's audio and
  does not write audio keys on D-POCO. Record D-POCO's values once in P.
- **The `video-profile-starvation-cap` key is deleted at the start of every arm.** A head unit that
  renders no video sets it after three sessions with no frame. Each arm then starts from the same state.
  Read it back at each arm's end and record it.

### The drop: which method, and why

A real cable pull drops VBUS. The host then sees the phone absent entirely until the plug comes back,
and the phone leaves accessory mode. On a replug the phone enumerates under its own VID:PID and the app
switches it to accessory mode again (memory `usb-detach-vs-gadget-teardown`). Four software methods
were weighed:

| Method | Where | Bus shape it gives | Status |
|---|---|---|---|
| unbind or rebind under `/sys/bus/usb/drivers/usb` | D-POCO | absent, then back under its own identity | **not possible**: D-POCO is not rooted |
| `dumpsys usb set-port-roles <port> sink device`, wait `GAP` s, then `source host` (`PORT`) | D-POCO | absent for `GAP` s, then back under its own identity. **Closest to a cable pull**, with the gap under script control | **unmeasured**: P3 tries it first |
| `svc usb setFunctions mtp` (`GADGET`) | D-MOTO | the phone leaves accessory mode and re-enumerates under its own identity in about 1 s: a phone-side gadget teardown, the same host-side sequence as a very short pull | **measured**: 15 of 15 recoveries in `usb-reattach` round 2 |
| `svc usb resetUsbGadget` | D-MOTO | a pull-up toggle that keeps the current functions, so the phone can come back still in accessory mode: the stale accessory shape | **rejected**: it tests the stale-accessory fault, which fails on 3.4.0 in every mode and would hide H1 |

**Why the gap matters.** The re-arm starts 1.5 s after the session end, and the poke about 2 s after
the group. With `GADGET` the phone is back in about 1 s, so USB may reach SSL before any poke. Then the
race H1 describes never runs. `PORT` holds the phone off the bus for `GAP=8` s, which puts the group
create and the poke inside the re-attach, as a human pull does. Every cycle records whether the re-arm
overlapped (`ovl`, section 7), so a method that never overlaps shows up as INCONCLUSIVE, not as a
false PASS.

P3 runs 2 trials of `PORT`, then 2 of `GADGET`, with no wireless stack (mode 0). It picks `PORT` if
both of its trials pass, else `GADGET` if both of its trials pass, else `HAND` (a cued cable pull per
cycle, 9 cues in all). The rules are in P3.

### Expected INCONCLUSIVE, said up front

- **B: INCONCLUSIVE** if the chosen method gives no overlap (`ovl=0`) in 2 or more N34 cycles. Most
  likely with `GADGET`.
- **B: INCONCLUSIVE** if H34 also fails to recover. Then the drop itself breaks 3.4.0 whatever the mode.
- **A: expected FAIL** (no drop in 25 min) in all arms. The rig has no Topway power or USB event. That
  falsifies H2 on this rig only. It cannot clear the reporter's hardware.
- **Phone conditions** for a cycle are void if D-MOTO's wireless adb dropped across both markers
  (`ph.lines=0`). Each arm needs at least one cycle with a valid phone window.
- **N35 cycles marked `CONFOUND`** if the projection screen never comes up on D-POCO. They do not count,
  and the arm runs an extra cycle.

### Hand steps

There is **one batched request**, before the first run (P1). After it the round needs no hand step
unless a fallback fires. Keep a terminal open on `tail -n0 -F $OUT/hand-steps.log`; `cue` prints, rings
the bell and appends there.

| Id | When | Action | Why there is no verb |
|---|---|---|---|
| H0 | P1 | Charge D-POCO and D-MOTO to 100% on their PC cables before P0 | a charger is hardware |
| H1 | P1 | Unplug D-POCO and D-MOTO from the PC. Plug D-MOTO into D-POCO's OTG port. On D-MOTO set "USB controlled by" to "This device". Unlock D-MOTO if it shows a PIN | a cable is hardware; the role setting and a PIN are system screens |
| H1p | P1, only if P0 found no bond | Pair D-MOTO with D-POCO in both phones' Bluetooth settings | pairing is a system dialog |
| H2 | fallback | Allow a USB dialog on D-POCO (`UsbPermissionActivity` or `UsbConfirmActivity`). **Never tick "Always"** | a system dialog; "Always" changes the rig for later rounds (template §7b) |
| H3 | fallback | Unplug D-MOTO from D-POCO, wait 5 s, plug it back, then set "USB controlled by" to "This device" | the last recovery step; a cable is hardware |
| PULL-PLUG | only if P3 picks `HAND` | Pull D-MOTO's cable out of D-POCO, count to 8, plug it back, set the USB role if D-MOTO asks | P3 found no scripted drop that works |
| Hx | after X | Put D-POCO and D-MOTO back on their PC cables and run `adb usb` on each | a cable is hardware |

**Tell the operator in P1:** if P3 picks `HAND`, Part B needs 9 cued pulls, about 4 minutes apart.

## 4. Settings keys

Write with the app stopped, with `put` (section 5). `put` starts every write from P's backup, so each
arm carries exactly the backup plus these keys. Read every key back with `rd` before the launch. A key
that does not read back with its value voids the arm before launch: write it again. If `put` cannot
write, use `$RIG_TOOLKIT/set_prefs_runas_host.py` with the same keys and say so in Setup notes.

| Key | Type | N34 | H34 | N35 | Why |
|---|---|---|---|---|---|
| `wifi-connection-mode` | int | `3` | `2` | `3` | the variable (P3 uses `0`) |
| `helper-connection-strategy` | int | `2` | `2` | `2` | the default, written so all arms match |
| `log-level` | int | `2` | `2` | `2` | INFO carries every app line in section 6 |
| `onboarding-version` | int | `2` | `2` | `2` | no wizard |
| `kill-on-disconnect` | boolean | `false` | `false` | `false` | the app stays up across a drop |
| `auto-connect-single-usb` | boolean | `true` | `true` | `true` | the app reconnects USB by itself, as the reporter's does |
| `auto-connect-last-session` | boolean | `false` | `false` | `false` | one reconnect path, not two |
| `auto-start-on-usb` | boolean | `false` | `false` | `false` | as above |
| `reopen-on-reconnection` | boolean | `true` | `true` | `true` | the default |
| `use-libusb` | boolean | `false` | `false` | `false` | the Java AOA path |
| `connection-modes` | string set | `usb,wifi` | `usb,wifi` | `usb,wifi` | home-screen toggles both on |
| `native-driver-selection-mode` | int | `0` | `0` | `0` | no driver dialog over the projection |
| `native-poke-all-paired` | boolean | `false` | `false` | `false` | poke D-MOTO only (section 3) |
| `native-poke-bt-macs` | string set | `$MOTO_BT` | `$MOTO_BT` | `$MOTO_BT` | as above |
| `auto-start-bt-macs` | string set | empty | empty | empty | Bluetooth auto-start is not under test |
| `auto-start-on-boot`, `auto-start-on-screen-on` | boolean | `false` | `false` | `false` | no other starter |
| `video-profile-starvation-cap` | | delete | delete | delete | same start state per arm |
| `native-aa-wake-damage-verdict` | | delete | delete | delete | a latched verdict changes the poke |
| `connection-issue-dismissed-at` | | delete | delete | delete | as in earlier USB rounds |
| `native-aa-wireless`, `wifi-launcher-mode` | | delete | delete | delete | legacy mode keys |

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on either tag, so markers need no
`allow-external-configuration`.

**unl_keys.sh**

```bash
BASE="int:helper-connection-strategy=2 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:auto-connect-single-usb=true bool:auto-connect-last-session=false bool:auto-start-on-usb=false bool:reopen-on-reconnection=true bool:use-libusb=false set:connection-modes=usb,wifi int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MOTO_BT set:auto-start-bt-macs= bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false del:video-profile-starvation-cap del:native-aa-wake-damage-verdict del:connection-issue-dismissed-at del:native-aa-wireless del:wifi-launcher-mode"
RDK='wifi-connection-mode|helper-connection-strategy|log-level|auto-connect|auto-start|reopen-on-reconnection|use-libusb|connection-modes|native-driver-selection-mode|native-poke|video-profile-starvation-cap'
```

`MOTO_BT` must be set before this file is sourced, because `BASE` expands it at assignment.

## 5. Helpers and verbs

Save the three files below in `$OUT`. Source `unl_lib.sh` in every shell of the round. Run the whole
round under `flock /tmp/ohu-rig.lock`. One adb call at a time per unit; the two log captures are the
only streams beside them (house rule 8).

**unl_lib.sh**

```bash
# unl_lib.sh : source in every shell. Needs OUT, HU, PH, MOTO_BT, RIG_TOOLKIT. METHOD, GAP, PORTID after P3.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
DHU=27870808938846
SSL='SSL handshake complete'
SEND='AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected'
send() { local a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s "$PH" shell log -t OHURIG "$1" >/dev/null 2>&1; sleep 0.3; }
cue() { echo "$(date +%T) OPERATOR_STEP $1"; printf '\a'; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
note() { echo "$(date +%T) $*" | tee -a "$OUT/setup-notes.md"; }
reconn() { local i; for i in $(seq 1 12); do adb -s "$1" get-state 2>/dev/null | grep -q device && return 0
  adb connect "$1" >/dev/null 2>&1; sleep 5; done; note "ADB_LOST $1"; return 1; }
th_gate() { "$RIG_TOOLKIT/rig_thermal.sh" wait 75 || "$RIG_TOOLKIT/rig_thermal.sh" wait 75 || { note HOST_HOT; return 1; }; }
th_watch() { "$RIG_TOOLKIT/rig_thermal.sh" watch "$OUT/thermal-$1.log" & echo $! > "$OUT/th.pid"; }
th_stop() { [ -f "$OUT/th.pid" ] && kill "$(cat "$OUT/th.pid")" 2>/dev/null; rm -f "$OUT/th.pid"; }
batt() { adb -s "$HU" shell dumpsys battery | grep -a -m1 -oE 'level: [0-9]+' | grep -oE '[0-9]+'; sleep 0.3; }
busn() { adb -s "$HU" shell ls /sys/bus/usb/devices | tr -d '\r' | grep -cE '^[0-9]+-[0-9.]+$'; sleep 0.3; }
dlg() { adb -s "$HU" shell dumpsys window | grep -a -m1 mCurrentFocus | grep -aqE 'UsbPermissionActivity|UsbConfirmActivity|UsbResolverActivity'; }
state() { send ACTION_QUERY_STATE | grep -a 'data='; }
rd() { adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE "($1)[^/]*"; sleep 0.3; }
put() { adb -s "$HU" shell am force-stop $PKG; sleep 0.3
  cp "$OUT/backup-poco.xml" "$OUT/w.xml"; python3 -I "$OUT/sx.py" "$OUT/w.xml" "$@"
  adb -s "$HU" push "$OUT/w.xml" /data/local/tmp/ohu-w.xml >/dev/null; sleep 0.3
  adb -s "$HU" shell run-as $PKG cp /data/local/tmp/ohu-w.xml shared_prefs/settings.xml; sleep 0.3; }
livemd5() { adb -s "$HU" pull "$(adb -s "$HU" shell pm path $PKG | head -1 | cut -d: -f2 | tr -d '\r')" "$OUT/live.apk" >/dev/null; sleep 0.3; md5sum < "$OUT/live.apk" | cut -c1-32; }
inst() { local want; want=$(md5sum < "$1" | cut -c1-32); [ "$(livemd5)" = "$want" ] && return 0
  adb -s "$HU" shell am force-stop $PKG; sleep 0.3
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml > "$OUT/pre-install-$(basename "$1").xml"; sleep 0.3
  adb -s "$HU" install -r -d "$1"; sleep 0.3
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml > "$OUT/post-install-$(basename "$1").xml"; sleep 0.3
  diff "$OUT/pre-install-$(basename "$1").xml" "$OUT/post-install-$(basename "$1").xml" > "$OUT/install-delta-$(basename "$1").txt"
  [ "$(livemd5)" = "$want" ] || { note "INSTALL_MISMATCH $1"; return 1; }; }
dhu_bt_off() { adb -s $DHU shell svc bluetooth disable >/dev/null 2>&1; sleep 3; }
ph_bt() { adb -s "$PH" shell svc bluetooth "$1"; sleep 4; adb -s "$PH" shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'; sleep 0.3; }
# exit only a running app: headunit://exit on a stopped app starts the service and arms the stack
clean_hu() { if adb -s "$HU" shell pidof $PKG >/dev/null 2>&1; then adb -s "$HU" shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 3; fi
  adb -s "$HU" shell am force-stop $PKG; sleep 0.3; }
panel() { adb -s "$HU" shell svc power stayon true; sleep 0.3; adb -s "$HU" shell input keyevent KEYCODE_WAKEUP; sleep 0.3
  adb -s "$HU" shell wm dismiss-keyguard; sleep 0.3; adb -s "$PH" shell input keyevent KEYCODE_WAKEUP; sleep 0.3; adb -s "$PH" shell input keyevent KEYCODE_HOME; sleep 0.3; }
cap_start() { RUN=$1; HUF="$OUT/$RUN.hu.txt"; PHF="$OUT/$RUN.ph.txt"
  adb -s "$HU" logcat -G 16M; sleep 0.3; adb -s "$HU" logcat -c; sleep 0.3
  stdbuf -oL adb -s "$HU" logcat -v time > "$HUF" & echo $! > "$OUT/hu.pid"
  adb -s "$PH" logcat -G 16M; sleep 0.3; adb -s "$PH" logcat -c; sleep 0.3
  stdbuf -oL adb -s "$PH" logcat -v time > "$PHF" & echo $! > "$OUT/ph.pid"; sleep 1; }
cap_stop() { local f; for f in hu ph; do [ -f "$OUT/$f.pid" ] && kill "$(cat "$OUT/$f.pid")" 2>/dev/null; rm -f "$OUT/$f.pid"; done; }
# a dead stream is restarted from the newest line; grading uses dumps, so a gap only costs a wait
hu_ok() { kill -0 "$(cat "$OUT/hu.pid")" 2>/dev/null && return 0; reconn "$HU" || return 1
  stdbuf -oL adb -s "$HU" logcat -v time -T 1 >> "$HUF" & echo $! > "$OUT/hu.pid"; note "$RUN hu stream restarted"; }
ph_ok() { kill -0 "$(cat "$OUT/ph.pid")" 2>/dev/null && return 0; reconn "$PH" || return 1
  stdbuf -oL adb -s "$PH" logcat -v time -T 1 >> "$PHF" & echo $! > "$OUT/ph.pid"; note "$RUN ph stream restarted"; }
nl() { wc -l < "$HUF"; }
seen() { tail -n +"$(( $2 + 1 ))" "$HUF" | grep -aqF -- "$1"; }            # seen STR FROMLINE
waitfor() { local i; for i in $(seq 1 "$1"); do seen "$2" "$3" && return 0; [ $((i % 10)) = 0 ] && hu_ok; sleep 1; done; return 1; }
dump() { reconn "$HU"; adb -s "$HU" logcat -d -v time > "$OUT/$1.hu.dump"; sleep 0.3
  reconn "$PH"; adb -s "$PH" logcat -d -v time > "$OUT/$1.ph.dump"; sleep 0.3; }
hwin() { awk -v a="AutomationMarker: $2" -v b="AutomationMarker: $3" '{sub(/\r$/,"")} function at(s){i=index($0,s); return i>0 && substr($0,i+length(s))~/^[[:space:]]*$/} at(a){on=1} on{print} on&&at(b){exit}' "$1"; }
pwin() { awk -v a="$2" -v b="$3" '{sub(/\r$/,"")} /OHURIG/ && $NF==a {on=1} on{print} on && /OHURIG/ && $NF==b {exit}' "$1"; }
# the drop. PORT: D-POCO stops hosting for GAP s. GADGET: D-MOTO leaves accessory mode. HAND: a cued pull.
drop() { local i; case $METHOD in
  PORT) adb -s "$HU" shell dumpsys usb set-port-roles "$PORTID" sink device >> "$OUT/$1.drop.txt" 2>&1; sleep "$GAP"
        adb -s "$HU" shell dumpsys usb set-port-roles "$PORTID" source host >> "$OUT/$1.drop.txt" 2>&1 ;;
  GADGET) adb -s "$PH" shell svc usb setFunctions mtp >> "$OUT/$1.drop.txt" 2>&1 ;;
  HAND) cue "PULL-PLUG: pull D-MOTO's cable out of D-POCO, count to 8, plug it back; set USB controlled by to This device if D-MOTO asks"
        for i in $(seq 1 120); do [ "$(busn)" = 0 ] && break; sleep 1; done; mark "$1-out"
        for i in $(seq 1 120); do [ "$(busn)" -ge 1 ] && break; sleep 1; done ;;
  esac; }
# recover: only after a cycle missed SSL. Records REC=GADGET, RELAUNCH, CABLE or NONE.
recover() { local c=$1 L
  mark "$c-rec1"; L=$(nl); adb -s "$PH" shell svc usb setFunctions mtp; sleep 8
  seen "$SSL" "$L" || send ACTION_CHECK_USB > "$OUT/$c-rec1.reply"
  if waitfor 60 "$SSL" "$L"; then echo -e "$c\tREC=GADGET" >> "$OUT/summary.tsv"; return 0; fi
  mark "$c-rec2"; L=$(nl); ph_bt disable >/dev/null; clean_hu; panel; adb -s "$HU" shell am start -n $MAIN
  waitfor 60 "$SSL" "$L" || { send ACTION_CHECK_USB > "$OUT/$c-rec2.reply"; waitfor 60 "$SSL" "$L"; }
  local ok=$?; ph_bt enable >/dev/null
  if [ $ok = 0 ]; then echo -e "$c\tREC=RELAUNCH" >> "$OUT/summary.tsv"; return 0; fi
  mark "$c-rec3"; L=$(nl); cue "H3: unplug D-MOTO from D-POCO, wait 5 s, plug it back, then set USB controlled by to This device on D-MOTO"
  waitfor 180 'Normal USB device attached:' "$L"; sleep 8
  seen "$SSL" "$L" || send ACTION_CHECK_USB > "$OUT/$c-rec3.reply"
  if waitfor 90 "$SSL" "$L"; then echo -e "$c\tREC=CABLE" >> "$OUT/summary.tsv"; return 0; fi
  echo -e "$c\tREC=NONE" >> "$OUT/summary.tsv"; return 1; }
# gcyc C : grade one cycle from its dumps, window C-drop to C-end
gcyc() { local c=$1
  hwin "$OUT/$c.hu.dump" "$c-drop" "$c-end" > "$OUT/$c.hwin"; pwin "$OUT/$c.ph.dump" "$c-drop" "$c-end" > "$OUT/$c.pwin"
  LC_ALL=C awk -v c="$c" '
  function has(s) { return index($0, s) > 0 }
  function ms() { split($2, t, ":"); return (t[1]*3600 + t[2]*60 + t[3]) * 1000 }
  { sub(/\r$/, "") }
  NR == 1 { t0 = ms() }
  has("AutomationMarker: " c "-norec") { stop = 1 }
  has("SSL handshake complete") && !has("Handshake: SSL") { nssl++; if (ts == "") ts = ms(); next }
  { pre = (ts == "" && !stop) }
  has("Handshake: Version request send failed") || has("Handshake: SSL performHandshake failed.") || has("Handshake: Version request/response failed after") || has("Handshake failed with exception") || has("Handshake: Version exchange timed out after") { if (pre) hsf++ }
  has("Successfully requested switch to accessory mode") { if (pre) sw++ }
  has("Normal USB device attached:") { if (pre) natt++ }
  has("USB accessory device attached, connecting.") { if (pre) aatt++ }
  has("AapService: wired session ended") { rearm++ }
  has("WifiLauncher: Initializing WiFi Mode: ") { if (pre) { ini++; if (mode == "") { m = $0; sub(/.*Initializing WiFi Mode: /, "", m); mode = m } } }
  has("createGroup SUCCESS") { if (pre) grp++ }
  has("NativeAA: Attempting active poke to device") { if (pre) poke++ }
  has("AapService: a USB projection attempt is in flight") { dfr++ }
  has("ConnectionArbiter: ") { arb++ }
  has("WirelessServer: Incoming connection detected") { if (pre) wl++ }
  has("AapService: USB session established while wireless mode") { if (!pre) usb++ }
  has("AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected") { if (pre) endpre++; else endpost++ }
  has("AapService: the projection screen never came up") { rf++ }
  has("not reconnecting by itself, the projection screen could not be raised.") { hold++ }
  has("MATCH! Starting AapService") { mw++ }
  END { dt = (ts != "") ? ts - t0 : "NA"
    if (NR == 0) out = "VOID"; else if (ts == "" || dt > 180000) out = "NOREC"
    else if (rf + hold > 0) out = "CONFOUND"; else if (nssl >= 2 || endpost > 0) out = "LOOP"
    else if (wl > 0) out = "REC_WIFI"; else if (usb == 0) out = "CHECK"; else out = "REC_CLEAN"
    printf "%s out=%s t_ssl_ms=%s nssl=%d hsf=%d sw=%d natt=%d aatt=%d rearm=%d ini=%d mode=%s grp=%d poke=%d ovl=%d dfr=%d arb=%d wl=%d usb=%d endpre=%d endpost=%d rf=%d hold=%d mw=%d\n",
      c, out, dt, nssl, hsf, sw, natt, aatt, rearm, ini, (mode == "" ? "-" : mode), grp, poke, (grp + poke > 0), dfr, arb, wl, usb, endpre, endpost, rf, hold, mw }' "$OUT/$c.hwin" | tee -a "$OUT/cycles.tsv"
  LC_ALL=C awk -v c="$c" 'function has(s) { return index($0, s) > 0 } { sub(/\r$/, "") }
  has("GH.WIRELESS") { wl++ } has("Creating rfcomm socket for device:") { rf++ }
  has("entering USB accessory mode") { ai++ } has("exited USB accessory mode") { ao++ }
  has("Critical error") { ce++ } has("ConnLoggerV2") { cl++ }
  END { printf "%s ph.lines=%d ph.wl=%d ph.rf=%d ph.acc_in=%d ph.acc_out=%d ph.crit=%d ph.connlog=%d\n", c, NR, wl, rf, ai, ao, ce, cl }' "$OUT/$c.pwin" | tee -a "$OUT/cycles.tsv"
  grep -aE 'ConnLoggerV2|GH\.WIRELESS' "$OUT/$c.pwin" > "$OUT/$c.phone-lines.txt"; }
```

**sx.py** (edits a local copy of `settings.xml`; `put` pushes it)

```python
# sx.py FILE type:key=value ... ; type is int, bool, str, set (comma list) or del. Edits FILE in place.
import re, sys
f = sys.argv[1]; s = open(f).read()
for kv in sys.argv[2:]:
    t, rest = kv.split(':', 1); k, v = (rest.split('=', 1) + [''])[:2]
    s = re.sub(r'<\w+ name="%s"[^>]*/>' % re.escape(k), '', s)
    s = re.sub(r'<string name="%s">[^<]*</string>' % re.escape(k), '', s)
    s = re.sub(r'<set name="%s">.*?</set>' % re.escape(k), '', s, flags=re.S)
    if t == 'set':
        items = ''.join('<string>%s</string>' % i for i in (v.split(',') if v else []))
        el = '<set name="%s">%s</set>' % (k, items) if items else '<set name="%s" />' % k
    elif t in ('int', 'bool', 'str'):
        el = {'int': '<int name="%s" value="%s" />', 'bool': '<boolean name="%s" value="%s" />',
              'str': '<string name="%s">%s</string>'}[t] % (k, v)
    else:
        el = ''
    s = s.replace('</map>', el + '</map>')
open(f, 'w').write(s)
```

**unl_run.sh** (the arm driver and Part A; source after `unl_lib.sh`)

```bash
# up A MODE COMMIT : bring a USB session up for run A with Bluetooth off on D-MOTO. 0 = gate held.
up() { local A=$1 M=$2 C=$3 L
  clean_hu; put $BASE int:wifi-connection-mode=$M; rd "$RDK" | tee "$OUT/$A.keys"
  ph_bt disable | tee "$OUT/$A.ph-bt-off"; dhu_bt_off
  adb -s "$PH" shell svc usb setFunctions mtp; sleep 5; panel
  th_gate || return 3; th_watch "$A"; cap_start "$A"; mark "$A-start"; L=$(nl)
  adb -s "$HU" shell am start -n $MAIN
  waitfor 15 'WifiLauncher: Initializing WiFi Mode: ' "$L"
  waitfor 60 "$SSL" "$L" || { send ACTION_CHECK_USB > "$OUT/$A-check.reply"; waitfor 60 "$SSL" "$L"; } || { echo "$A GATE no_ssl" | tee -a "$OUT/summary.tsv"; return 1; }
  sleep 30; state > "$OUT/$A.state"
  local g=ok
  grep -aqF "$C" "$OUT/$A.state" || g="wrong_apk"
  seen 'AapService: USB session established while wireless mode' "$L" || g="$g,not_usb"
  seen 'WirelessServer: Incoming connection detected' "$L" && g="$g,wireless_session"
  seen 'AapService: the projection screen never came up' "$L" && g="$g,no_screen"
  seen 'AapService: raising the projection by' "$L" || g="$g,no_raise"
  echo "$A GATE $g mode_line=$(tail -n +"$((L+1))" "$HUF" | grep -aF -m1 'WifiLauncher: Initializing WiFi Mode: ' | sed 's/.*Initializing WiFi Mode: //')" | tee -a "$OUT/summary.tsv"
  [ "$g" = ok ]; }
# cyc C : one drop and reconnect cycle; the next drop comes 180 s after this one's SSL
cyc() { local c=$1 L
  dhu_bt_off; hu_ok; ph_ok
  mark "$c-drop"; L=$(nl); drop "$c"; mark "$c-dropped"
  if ! waitfor 170 "$SSL" "$L"; then mark "$c-norec"; recover "$c" || echo "$c ARM_STOP" | tee -a "$OUT/summary.tsv"; fi
  dlg && { cue "H2: allow the USB dialog on D-POCO, never tick Always"; echo "$c DIALOG" | tee -a "$OUT/summary.tsv"; }
  sleep 180; mark "$c-end"; dump "$c"; gcyc "$c"; }
# partA A : 25 min with no action; stops at 1500 s, the 3rd session end, or battery under 20%
partA() { local A=$1 L i n=0 b
  mark "$A-A-start"; L=$(nl)
  for i in $(seq 1 300); do sleep 5
    [ $((i % 6)) = 0 ] && { hu_ok; ph_ok; }
    n=$(tail -n +"$((L+1))" "$HUF" | grep -acF "$SEND"); [ "$n" -ge 3 ] && break
    if [ $((i % 60)) = 0 ]; then b=$(batt); echo "$A A t=$((i*5))s battery=$b ends=$n" | tee -a "$OUT/summary.tsv"; [ "$b" -lt 20 ] && break; fi
  done
  mark "$A-A-end"; dump "$A-A"
  for f in "$HUF:hu" "$OUT/$A-A.hu.dump:dump"; do
    hwin "${f%%:*}" "$A-A-start" "$A-A-end" | LC_ALL=C awk -v a="$A" -v src="${f##*:}" '
    function has(s) { return index($0, s) > 0 } function ms() { split($2, t, ":"); return (t[1]*3600 + t[2]*60 + t[3]) * 1000 }
    { sub(/\r$/, "") } NR == 1 { t0 = ms() } { tl = ms() }
    has("AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected") { e++; at = at sprintf("%.1f,", (ms() - t0) / 60000) }
    has("SSL handshake complete") && !has("Handshake: SSL") { s++ }
    has("AapRead: Connection lost. Stopping read loop.") { cl++ } has("USB Accessory detached.") { ad++ }
    has("AapService: wired session ended") { rr++ } has("createGroup SUCCESS") { g++ }
    has("NativeAA: Attempting active poke to device") { p++ } has("MATCH! Starting AapService") { mw++ }
    END { printf "%s A src=%s lines=%d dur_s=%d ends=%d end_min=%s ssl=%d connlost=%d accdet=%d rearm=%d grp=%d poke=%d mw=%d\n", a, src, NR, (tl - t0) / 1000, e, (at == "" ? "-" : at), s, cl, ad, rr, g, p, mw }' | tee -a "$OUT/cycles.tsv"; done
  pwin "$OUT/$A-A.ph.dump" "$A-A-start" "$A-A-end" | LC_ALL=C awk -v a="$A" 'function has(s) { return index($0, s) > 0 }
    has("exited USB accessory mode") { ao++ } has("Critical error") { ce++ } has("GH.WIRELESS") { wl++ } has("ConnLoggerV2") { cl++ }
    END { printf "%s A ph.lines=%d ph.acc_out=%d ph.crit=%d ph.wl=%d ph.connlog=%d\n", a, NR, ao, ce, wl, cl }' | tee -a "$OUT/cycles.tsv"; }
# arm A MODE APK COMMIT : one arm, Part B then Part A in one capture
arm() { local A=$1 M=$2 APK=$3 C=$4 b k n=0 tries=0
  b=$(batt); echo "$A battery_start=$b" | tee -a "$OUT/summary.tsv"
  [ "$b" -ge 40 ] || { echo "$A UNTESTABLE battery=$b" | tee -a "$OUT/summary.tsv"; return 2; }
  inst "$APK" || return 1
  up "$A" "$M" "$C" || { cap_stop; th_stop; note "$A gate failed, re-run once"; up "$A" "$M" "$C" || { echo "$A UNTESTABLE gate" | tee -a "$OUT/summary.tsv"; cap_stop; th_stop; return 1; }; }
  ph_bt enable | tee "$OUT/$A.ph-bt-on"; sleep 150
  while [ $n -lt 3 ] && [ $tries -lt 5 ]; do tries=$((tries+1)); k="$A-c$tries"; cyc "$k"
    grep -aq "^$k ARM_STOP" "$OUT/summary.tsv" && break
    grep -aE "^$k out=(VOID|CONFOUND)" "$OUT/cycles.tsv" >/dev/null || grep -aq "^$k DIALOG" "$OUT/summary.tsv" || n=$((n+1)); done
  echo "$A counted_cycles=$n attempts=$tries" | tee -a "$OUT/summary.tsv"
  grep -aqE "^$A-c[0-9]+ ARM_STOP" "$OUT/summary.tsv" || partA "$A"
  rd 'video-profile-starvation-cap' | tee -a "$OUT/$A.keys"
  mark "$A-end"; cap_stop; th_stop; clean_hu; ph_bt disable >/dev/null; }
```

**Verbs and commands used, and nothing else acts on the app:**

| Action | Command |
|---|---|
| launch (the arm's first session) | `adb -s $HU shell am start -n $MAIN` |
| the USB button, as a fallback only | `send ACTION_CHECK_USB` |
| build identity | `send ACTION_QUERY_STATE` (`state`) |
| marker | `mark <label>` (both units; labels have no spaces) |
| stop the service cleanly | `headunit://exit` deep link, then `am force-stop` (`clean_hu`) |
| the drop | `drop` (`PORT`, `GADGET` or `HAND`, chosen in P3) |

Every verb must print `AutomationReceiver: ` in the head unit capture. A step with no such line never
landed: that step is void, not a FAIL. The `.reply` and `.state` files keep each `data=` line. The
drop and the radios are system commands, not app actions, so they print no `AutomationReceiver:` line.

## 6. The deciding lines

Every app string below matched `git grep -F` in `app/src/main` at **both** `013b05bc` and `1c1b5b72`,
except the five marked 3.5.0 only. Every one is `AppLog.i`, `AppLog.w` or `AppLog.e` with no
`LOG_VERBOSE` guard, so `log-level` 2 carries them. All are grepped in the head unit dump
(`$c.hu.dump`, window `$c-drop` to `$c-end`) or, for A, in the stream and the dump (window `$A-A-start`
to `$A-A-end`). Several strings are prefixes of longer source lines, and the prefix is enough to
match.

| Line (grep -F) | Source | Used for |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | a verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` | markers |
| `SSL handshake complete` | `AapSslContext` (INFO) | a session formed. `Handshake: SSL handshake complete` is `AppLog.d` and is absent at INFO; `gcyc` excludes it anyway |
| `AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected` | `AapService.onDisconnected` | a disconnect (a session end, or a failed attempt) |
| `AapService: wired session ended` | `rearmWirelessAfterWiredSession` | the re-arm after a USB session ended (prefix) |
| `AapService: USB session established while wireless mode` | `quiesceWirelessForWiredSession` | the session is USB; prints in every mode, MANUAL included |
| `WifiLauncher: Initializing WiFi Mode: ` | `WifiLauncherManager` | a launcher started; the mode name follows |
| `createGroup SUCCESS` | `WifiDirectManager` | a P2P group came up |
| `NativeAA: Attempting active poke to device` | `NativeAaHandshakeManager` | a wake poke went out |
| `WirelessServer: Incoming connection detected` | `WirelessServer` | a session came over WiFi |
| `AapService: a USB projection attempt is in flight` | `deferWirelessForUsbHandoff` | the deferral held the bring-up (prefix) |
| `Successfully requested switch to accessory mode` | `UsbLauncherManager`, `UsbLauncherListener` | an AOA switch went out |
| `Normal USB device attached:` | `UsbLauncherListener` | the phone came back under its own identity |
| `USB accessory device attached, connecting.` | `UsbLauncherListener` | the phone came back still in accessory mode |
| `USB Accessory detached.` | `UsbLauncherListener` | A: an accessory detach |
| `AapRead: Connection lost. Stopping read loop.` | `AapReadMultipleMessages` | A: the read loop lost the link |
| `Handshake: Version request send failed` | `AapTransport` | a failed handshake (one of five) |
| `Handshake: SSL performHandshake failed.` | `AapTransport` | as above |
| `Handshake: Version request/response failed after` | `AapTransport` | as above |
| `Handshake failed with exception` | `AapTransport` | as above |
| `Handshake: Version exchange timed out after` | `AapTransport` | as above |
| `AapService: raising the projection by` | `AapService` | the gate: the projection was raised |
| `AapService: the projection screen never came up` | `AapService` | a raise deadline ended the session: `CONFOUND` |
| `MATCH! Starting AapService` | `AutoStartReceiver` | a self-wake; counted, not a discard |
| `ConnectionArbiter: ` | `ConnectionArbiter` | **3.5.0 only**: any arbiter decision |
| `stood the wireless stack down until it ends` | `ConnectionArbiter` | **3.5.0 only**: USB stood the stack down |
| `wireless bring-up refused while` | `ConnectionArbiter` | **3.5.0 only**: the bring-up was refused |
| `the projection screen never came up again` | `AapService` | **3.5.0 only**: the second raise failure |
| `not reconnecting by itself, the projection screen could not be raised.` | `AapService` | **3.5.0 only**: the hold after two raise failures |

**Not in our tree** (grepped, not decisive strings): the phone's `GH.WIRELESS` (Gearhead's wireless
setup), `Creating rfcomm socket for device:` (Gearhead dials a head unit), `ConnLoggerV2` (Gearhead's
connection events), `Critical error` (Gearhead's session end), Android's `entering USB accessory mode`
and `exited USB accessory mode` on D-MOTO, and the marker tag `OHURIG`. All six Gearhead and Android
strings are quoted from earlier D-MOTO and D-POCO captures on this branch (for example
`archive/rounds/usb-reattach-round1-brief.md` and its results).

**The 3.4.0 session-end line is not used.** `AapService: Disconnected. wasPlayingBeforeDisconnect=`
exists at `013b05bc` only; 3.5.0 removed it. The `Stopping GpsLocationService ...` line above is on
both tags and is used instead.

## 7. Runs

### P. Prepare (cabled, then wireless)

**P0 (both phones on their PC cables, after H0):**

**p0.sh**

```bash
OUT=rig-data/rounds/usb-native-loop-round1
$RIG_TOOLKIT/rig_preflight.sh D_POCO:wifi,bt D_MOTO:wifi,bt | tee $OUT/preflight.txt     # quote it in Setup notes
adb -s 4f4027e9 shell getprop ro.build.version.sdk | tee $OUT/poco-sdk.txt              # MUST be 35 or more; else stop the round
adb -s 4f4027e9 shell dumpsys battery | grep -a level; adb -s ZY22GC3BM4 shell dumpsys battery | grep -a level
MOTO_BT=$(adb -s ZY22GC3BM4 shell settings get secure bluetooth_address | tr -d '\r'); echo "MOTO_BT=$MOTO_BT" > $OUT/env.txt   # expect A0:46:5A:97:E4:95
adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -A16 'Bonded devices' > $OUT/poco-bonds.txt
grep -acF "$MOTO_BT" $OUT/poco-bonds.txt                                              # 0 means H1p is needed
adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:' | tee $OUT/moto-bt-start.txt
adb -s ZY22GC3BM4 shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName | tee $OUT/gearhead.txt
for k in screen_brightness screen_brightness_mode; do echo "$k=$(adb -s 4f4027e9 shell settings get system $k | tr -d '\r')"; done | tee $OUT/poco-screen.txt
POCO_IP=$(adb -s 4f4027e9 shell ip -4 addr show wlan0 | grep -aoE 'inet [0-9.]+' | cut -d' ' -f2)
MOTO_IP=$(adb -s ZY22GC3BM4 shell ip -4 addr show wlan0 | grep -aoE 'inet [0-9.]+' | cut -d' ' -f2)
echo "POCO_IP=$POCO_IP" >> $OUT/env.txt; echo "MOTO_IP=$MOTO_IP" >> $OUT/env.txt; cat $OUT/env.txt
adb -s 4f4027e9 tcpip 5555; sleep 3; adb -s ZY22GC3BM4 tcpip 5555; sleep 3
adb connect $POCO_IP:5555; adb connect $MOTO_IP:5555
```

**P1: the one batched request.** Send H1 (and H1p if P0 counted 0), and the `HAND` warning from
section 3. Wait for the operator's "done". Then set the round's variables and check the layout:

**p1.sh**

```bash
OUT=rig-data/rounds/usb-native-loop-round1; . $OUT/env.txt     # one NAME=value per line
HU=$POCO_IP:5555; PH=$MOTO_IP:5555; . $OUT/unl_lib.sh; . $OUT/unl_keys.sh; . $OUT/unl_run.sh
reconn $HU && reconn $PH                                       # both MUST succeed; else the rig is broken: stop
busn                                                           # MUST be 1 or more (D-MOTO on the bus)
adb -s $PH shell svc usb getFunctions                          # record
adb -s $HU shell dumpsys usb | grep -a -i -A4 headunitrevived > $OUT/usb-grants-before.txt   # record, do not change
adb -s $HU shell am force-stop $PKG; sleep 0.3
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/backup-poco.xml    # the round's baseline; never overwrite it
grep -aoE '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)[^/]*' $OUT/backup-poco.xml   # record once, never write
livemd5 | tee $OUT/poco-apk-before.txt                         # the build D-POCO carried before the round
adb -s $DHU shell am force-stop $PKG; dhu_bt_off
adb -s $HU shell settings put system screen_brightness_mode 0; adb -s $HU shell settings put system screen_brightness 10
```

Run `p1.sh`'s first three lines again in every new shell of the round. They load `env.txt`, which
also carries `PORTID`, `GAP` and `METHOD` once P3 has written them.

**P2: install 3.4.0.** `inst $OUT/apk/v340.apk`. Record `install-delta-v340.apk.txt`. `put` reseeds
from the backup anyway, so a delta does not stop the round, but quote it.

**PASS when all hold:** `poco-sdk.txt` reads 35 or more; `reconn` worked for both; `busn` is 1 or more;
`MOTO_BT` is set; the bond count is 1 or more (after H1p if needed); `livemd5` equals `v340.apk`'s md5
after P2. **A failure in P stops the round.**

### P3. Which drop (record the shape of each method; choose one)

Mode 0 (MANUAL), 3.4.0, Bluetooth off on D-MOTO, one capture `P3`. No wireless stack runs, so this
also proves that 3.4.0 recovers USB from the drop by itself. A control that fails here makes every arm
of B INCONCLUSIVE in advance.

**p3.sh**

```bash
adb -s $HU shell dumpsys usb -h > $OUT/poco-usb-help.txt 2>&1; adb -s $HU shell dumpsys usb ports > $OUT/poco-ports.txt 2>&1
grep -acF set-port-roles $OUT/poco-usb-help.txt                # 0 means PORT is not available: skip to GADGET
PORTID=$(grep -aoE 'id=[A-Za-z0-9_.-]+' $OUT/poco-ports.txt | head -1 | cut -d= -f2); GAP=8; echo "PORTID=$PORTID" >> $OUT/env.txt; echo "GAP=$GAP" >> $OUT/env.txt
up P3 0 013b05bc0e7f || echo "P3 GATE FAILED"                  # mode 0 still prints the USB session line; a failed gate here is a P3 setup failure
p3t() { local m=$1 k=$2 c=P3-$1-$2 L a back i r
  dhu_bt_off; mark $c-drop; L=$(nl)
  case $m in
    PORT) adb -s $HU shell dumpsys usb set-port-roles "$PORTID" sink device > $OUT/$c.off.txt 2>&1; sleep 3; a=$(busn); sleep $((GAP-3))
          adb -s $HU shell dumpsys usb set-port-roles "$PORTID" source host > $OUT/$c.on.txt 2>&1 ;;
    GADGET) adb -s $PH shell svc usb setFunctions mtp; sleep 0.5; a=$(busn) ;;
  esac
  mark $c-dropped; back=NA; for i in $(seq 1 20); do [ "$(busn)" -ge 1 ] && { back=$i; break; }; sleep 0.7; done
  waitfor 90 "$SSL" "$L"; r=$?
  if [ "$back" = NA ] && [ $m = PORT ]; then adb -s $HU shell dumpsys usb set-port-roles "$PORTID" source host >> $OUT/$c.on.txt 2>&1; sleep 10
    [ "$(busn)" -ge 1 ] || { adb -s $PH shell svc usb setFunctions mtp; sleep 10; }
    [ "$(busn)" -ge 1 ] || { cue "H3: unplug D-MOTO from D-POCO, wait 5 s, plug it back, then set USB controlled by to This device on D-MOTO"; for i in $(seq 1 180); do [ "$(busn)" -ge 1 ] && break; sleep 1; done; }; fi
  [ $r = 0 ] || { seen "$SSL" "$L" || send ACTION_CHECK_USB > $OUT/$c.check.reply; waitfor 60 "$SSL" "$L" || adb -s $PH shell svc usb setFunctions mtp; waitfor 60 "$SSL" "$L"; }
  adb -s $HU shell dumpsys usb ports > $OUT/$c.ports-after.txt 2>&1
  sleep 20; mark $c-end; dump $c; gcyc $c
  echo "$c bus_during=$a back_passes=$back ssl_rc=$r" | tee -a $OUT/summary.tsv; }
[ -n "$PORTID" ] && grep -aqF set-port-roles $OUT/poco-usb-help.txt && { p3t PORT 1; p3t PORT 2; }
p3t GADGET 1; p3t GADGET 2
mark P3-end; cap_stop; th_stop; clean_hu
```

Notes for the executor. A `back_passes` value is a count of polls of about 1 s each. If a PORT trial
needed the H3 cue, record it in Setup notes and do not run PORT again.

**Decide `METHOD`, and append it to `$OUT/env.txt` as one line, `METHOD=<value>`:**

- **`PORT`** if both PORT trials have: `bus_during=0` (the phone was absent, as with a cable),
  `back_passes` not `NA`, `out=REC_CLEAN`, `natt` 1 or more (the phone came back under its own
  identity) and `t_ssl_ms` under 90000 + 8000.
- else **`GADGET`** if both GADGET trials have `out=REC_CLEAN`, `natt` 1 or more and `t_ssl_ms` under
  90000.
- else **`HAND`**. Tell the operator now that Part B needs 9 cued pulls.

**Record for each trial:** `bus_during`, `back_passes`, `t_ssl_ms`, `natt`, `aatt`, `sw`, `hsf`, and
the phone's `ph.acc_out` and `ph.acc_in`. Say which shape each method made: "absent, then back under
its own identity" (cable-like), "re-enumerated under its own identity with no absent reading"
(gadget teardown), or "back in accessory mode" (`aatt` 1 or more and `natt` 0: the stale shape).

**PASS** when `METHOD` is `PORT` or `GADGET`. **FAIL** when it is `HAND` (the round still runs, with
cues). If neither method recovered a session in either trial (`out=NOREC` in all four), 3.4.0 cannot
recover USB from a scripted drop even with no wireless stack. Then B is INCONCLUSIVE in advance: run
the arms for Part A only (skip `cyc` by setting the loop bound to 0) and say so in Setup notes.

### B and A. The arms (B is the point of the round)

Run the arms in this order. N34 and H34 are the core comparison for H1, so they go first while the
battery is fullest.

**arms.sh**

```bash
. $OUT/env.txt; echo "METHOD=$METHOD PORTID=$PORTID GAP=$GAP"     # METHOD MUST be set
arm N34 3 $OUT/apk/v340.apk 013b05bc0e7f
arm H34 2 $OUT/apk/v340.apk 013b05bc0e7f
arm N35 3 $OUT/apk/v350.apk 1c1b5b7261eb
```

If an arm returns `UNTESTABLE battery`, stop the round there. The operator charges D-POCO and D-MOTO on
the PC (H0 again), and the next session resumes at that arm with P1's checks first. That is a restart,
not a question to ask mid-round.

**What one arm does, in order** (all in `arm`, `up`, `cyc` and `partA`):

1. Battery 40% or more on D-POCO. Install the arm's APK if it is not live.
2. `up`: stop the app, write `$BASE` plus the arm's mode, read back, D-MOTO Bluetooth off, D-HU
   Bluetooth off, `svc usb setFunctions mtp` on D-MOTO (a fresh enumeration), screen on, thermal gate,
   start both captures, mark `<arm>-start`, launch, wait up to 15 s for `Initializing WiFi Mode`, wait
   up to 60 s for SSL (then one `ACTION_CHECK_USB` and 60 s more).
3. **The gate** (`<arm> GATE ok` in `summary.tsv`): the `commit` matches; `USB session established`
   printed; no `WirelessServer: Incoming connection detected`; `raising the projection by` printed;
   no `the projection screen never came up` in the 30 s after SSL. The `mode_line` reads `NATIVE` for
   N34 and N35 and `HELPER` for H34. A failed gate re-runs `up` once. A second failure makes the arm
   UNTESTABLE.
4. D-MOTO Bluetooth on. Wait 150 s, so the first drop comes about 180 s after the session formed.
5. **B:** up to 5 cycles, until 3 count. Each cycle: D-HU Bluetooth off, mark `<c>-drop`, `drop`, mark
   `<c>-dropped`, wait up to 170 s for SSL. If none, mark `<c>-norec` and run `recover`. Then hold
   180 s, mark `<c>-end`, dump both units, and grade (`gcyc`). A cycle counts unless it is `VOID`,
   `CONFOUND` or had a `DIALOG`. An `ARM_STOP` (no recovery at all, H3 included) ends the arm's B and
   skips its A.
6. **A:** mark `<arm>-A-start`, do nothing for 1500 s, or until the 3rd session end, or until the
   battery reads under 20%. Mark `<arm>-A-end`. Grade from the stream and from the dump. The A session
   is the one that the last B cycle left up.
7. Mark `<arm>-end`, stop the captures, exit the app, D-MOTO Bluetooth off.

**Thermal.** The limits are the ones in `rig-quirks/topics/tooling.md`. If `thermal-<arm>.log` shows
95C or a throttle rise inside a cycle's window, mark that cycle `VOID (thermal)` in Setup notes; the
arm then runs one more cycle within its 5-attempt bound.

**Per cycle, report the two `cycles.tsv` lines** (`<c> out=...` and `<c> ph.lines=...`). The fields:

| Field | Meaning |
|---|---|
| `out` | `REC_CLEAN` (a USB session formed within 180 s and held 180 s with no disconnect), `LOOP` (2 or more SSL, or a disconnect after the first SSL), `NOREC` (no SSL within 180 s), `REC_WIFI` (the session came over WiFi), `CONFOUND` (a raise deadline), `CHECK` (SSL with no USB line and no WiFi line: the host reads the window), `VOID` (no window) |
| `t_ssl_ms` | ms from the `<c>-drop` marker to the first SSL. With `PORT`, it includes the 8 s gap |
| `hsf`, `sw` | failed handshakes and AOA switches before the first SSL: `hsf + 1` is the attempt count of a recovered cycle |
| `ini`, `mode`, `grp`, `poke` | launcher starts, the first one's mode, group creates and wake pokes before the first SSL (or before `-norec`) |
| `ovl` | 1 when a group create or a poke ran before the first SSL: **the re-arm overlapped the USB re-attach** |
| `rearm` | `wired session ended` lines in the window (expect 1 per USB session that ended) |
| `dfr`, `arb` | 3.5.0's deferral and arbiter lines (expect 0 on 3.4.0's wired re-arm) |
| `wl`, `usb` | WiFi session before the first SSL; USB session lines after it |
| `endpre`, `endpost` | disconnect lines before and after the first SSL |
| `mw` | self-wakes |
| `ph.wl`, `ph.rf` | the phone started a wireless setup, and dialled a head unit over RFCOMM, inside the window |
| `ph.acc_in`, `ph.acc_out` | the phone entered and left accessory mode |
| `ph.lines` | 0 means the phone window is missing (markers lost): the phone fields are void for that cycle |

#### Arm classes (computed by the host from the counted cycles)

- **RECOVERS:** 2 or more of 3 counted cycles are `REC_CLEAN`.
- **FAILS:** 2 or more of 3 counted cycles are `NOREC`, `LOOP` or `REC_WIFI`.
- **MIXED:** anything else.

#### B verdict (one verdict for the three arms)

- **PASS (H1 supported):** all four hold.
  1. N34 is FAILS.
  2. In 2 or more of N34's failing cycles, `ovl=1` (the Native re-arm ran inside the re-attach).
  3. H34 is RECOVERS.
  4. N35 is RECOVERS, and in its cycles `poke=0` before the first SSL in 2 or more of 3.
- **FAIL (H1 falsified):** N34 is RECOVERS and H34 is RECOVERS, with `ovl=1` in 2 or more N34 cycles.
  The re-arm ran inside the re-attach and did no harm.
- **FAIL (3.5.0 does not stop it):** N34 is FAILS with `ovl=1` as in PASS item 2, H34 is RECOVERS, and
  N35 is not RECOVERS. Report which of `LOOP`, `NOREC` or `REC_WIFI` N35 shows.
- **INCONCLUSIVE:** H34 is not RECOVERS (the drop breaks 3.4.0 in any mode); or `ovl=0` in 2 or more
  N34 cycles (USB won the race, so the drop never exercised H1); or an arm has fewer than 3 counted
  cycles after 5 attempts.

**What a PASS looks like if nothing were going on.** N34 can show `REC_CLEAN` either because the race
is harmless or because the re-arm came after SSL. `ovl` separates the two, so report it beside every
outcome.

**Phone condition (mandatory, graded).** In N34, report `ph.wl` and `ph.rf` for each cycle with
`ph.lines` above 0. **H1's mechanism is confirmed on the phone** when, in 2 or more N34 failing cycles,
`ph.wl` or `ph.rf` is 1 or more (the poke made Gearhead start a wireless setup during the USB
re-attach), and H34's cycles read `ph.rf=0`. Quote one `ConnLoggerV2` or `GH.WIRELESS` line per N34
failing cycle from `<c>.phone-lines.txt`. If no arm has a cycle with `ph.lines` above 0, B's phone
condition is UNTESTABLE (phone adb), and the B verdict rests on the head unit alone; say so.

#### A verdict (H2)

For each arm, report from both A lines (`src=hu` and `src=dump`; take the larger `ends`): `dur_s`,
`ends`, `end_min` (minutes after `A-start` for each end), `ssl`, `connlost`, `accdet`, `rearm`, `grp`,
`poke`, and the phone's `ph.acc_out` and `ph.crit`. A `dump` line with `lines=0` means the buffer
wrapped; use the `hu` line.

- **PASS (H2 supported):** N34 has 1 or more `ends`, and H34 has 0 `ends` over 1200 s or more.
- **FAIL (H2 falsified on this rig):** N34 has 0 `ends` over 1200 s or more.
- **INCONCLUSIVE:** N34 and H34 both drop (a rig-wide drop, not tied to the mode), or N34's `dur_s` is
  under 1200.

N35's A line is recorded for completeness and does not enter the verdict. If any arm drops in A, report
what followed in the same window (`ssl`, `rearm`, `grp`, `poke`, `mw`): that is a natural H1 cycle.

### X. Clean-up

**x.sh**

```bash
clean_hu; adb -s $HU shell svc power stayon false
while IFS='=' read -r k v; do adb -s $HU shell settings put system "$k" "$v"; sleep 0.3; done < $OUT/poco-screen.txt   # brightness back
put; rd 'wifi-connection-mode|log-level|auto-connect|native-poke'; diff <(adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml) $OUT/backup-poco.xml   # record the delta
adb -s $HU shell dumpsys usb | grep -a -i -A4 headunitrevived > $OUT/usb-grants-after.txt; diff $OUT/usb-grants-before.txt $OUT/usb-grants-after.txt
grep -aqiE 'enabled: *true|state: *ON' $OUT/moto-bt-start.txt && ph_bt enable || ph_bt disable                  # D-MOTO's radio as P0 found it
adb -s $DHU shell svc bluetooth enable; sleep 3; adb -s $DHU shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'
[ "$METHOD" = PORT ] && adb -s $HU shell dumpsys usb ports > $OUT/poco-ports-final.txt   # role MUST read source/host as in poco-ports.txt
$RIG_TOOLKIT/rig_cleanup.sh
```

`put` with no keys copies the backup unchanged. D-POCO keeps 3.5.0 installed (`v350.apk`); record that,
and the md5 in `poco-apk-before.txt`, in Setup notes. Then cue Hx.

## 8. Do not re-run

- **The stale accessory on builds before 3.5.0.** After a session end with the phone left in
  accessory mode, a plain retry recovers 0 of 11 cycles and `svc usb setFunctions mtp` recovers 15 of
  15 (`archive/rounds/usb-reattach-round2-results.md`). That is why no drop here leaves the phone in
  accessory mode.
- **A Gearhead force-stop as a USB recovery.** It formed no session in 15 of 15 tries in the same
  round. `recover` does not use it.
- **D-HU as a USB host.** It cannot host USB.
- **Unit tests.** No candidate SHA; see section 1.

## 9. Report back

The numbers that decide the question:

1. **`METHOD`**, with P3's shape per method (`bus_during`, `back_passes`, `natt`, `aatt`, `t_ssl_ms`).
2. **B:** a table of every cycle (arm, `out`, `t_ssl_ms`, `hsf+1`, `ovl`, `grp`, `poke`, `dfr`, `arb`,
   `endpost`, `ph.wl`, `ph.rf`, `ph.acc_in`), the class of each arm, and the B verdict with the rule
   that gave it.
3. **A:** per arm, `dur_s`, `ends` and `end_min`, and the A verdict.

Give each verdict in the fixed format of `TESTING-TEMPLATE.md` §7. Put every deviation in Setup notes,
including each `REC=` line, each `DIALOG`, each stream restart and each cue. If P3 measured the `PORT`
lever on D-POCO for the first time, add the result to `rig-quirks/units/D-POCO.md`.

The block below lists every app string the runs grep that is in `app/src/main` at both `013b05bc` and
`1c1b5b72`.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
SSL handshake complete
AapService: Stopping GpsLocationService and NightModeManager since connection is disconnected
AapService: wired session ended
AapService: USB session established while wireless mode
WifiLauncher: Initializing WiFi Mode: 
createGroup SUCCESS
NativeAA: Attempting active poke to device
WirelessServer: Incoming connection detected
AapService: a USB projection attempt is in flight
Successfully requested switch to accessory mode
Normal USB device attached:
USB accessory device attached, connecting.
USB Accessory detached.
AapRead: Connection lost. Stopping read loop.
Handshake: Version request send failed
Handshake: SSL performHandshake failed.
Handshake: Version request/response failed after
Handshake failed with exception
Handshake: Version exchange timed out after
AapService: raising the projection by
AapService: the projection screen never came up
MATCH! Starting AapService
```

These app strings are in `app/src/main` at `1c1b5b72` only. Check them against 3.5.0, not 3.4.0. On a
3.4.0 capture a count of 0 is the expected reading.

```decisive-strings-v350
ConnectionArbiter: 
stood the wireless stack down until it ends
wireless bring-up refused while
the projection screen never came up again
not reconnecting by itself, the projection screen could not be raised.
```

These are grepped too, but they are not in our tree: Gearhead's and Android's lines on D-MOTO, and our
phone marker tag.

```external-strings
GH.WIRELESS
Creating rfcomm socket for device:
ConnLoggerV2
Critical error
entering USB accessory mode
exited USB accessory mode
OHURIG
```
