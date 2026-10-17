# samsung-driver-native, round 1 brief

Published name on the transfer branch: `samsung-driver-native-round1-brief.md`. Results file:
`samsung-driver-native-round1-results.md`. **No evidence release for the phone captures** (section 3).

A Samsung Galaxy S24+ (the operator's personal phone, called **S24** below) is the driver phone in
Native AA. The round reads Android Auto's own log on that phone, beside the head unit's log. It
asks one question: does a Samsung driver fail on a phone or tablet head unit where another phone
passes, and if so, at which gate? Estimated time: 35 min of runs, plus one build and three hand
pairings.

## 0. The S24 is not a rig device. Read this first.

The S24 belongs to a family member. **Nothing from this round may stay on it.** On the S24:

- **Allowed, read-only:** `adb logcat` streamed to the PC (no `-c`, no `-G`, no `-f`), `dumpsys`,
  `getprop`, `settings get`, `settings list`, `ls`, `find`, `pm path`, `dumpsys package`,
  `adb exec-out screencap -p > <file on the PC>`.
- **Forbidden:** `adb push`, `adb install`, `pm clear`, `pm disable*`, `am force-stop`, `settings put`,
  `setprop`, `svc`, `cmd wifi|bluetooth_manager|connectivity` (state changes), `input`,
  `uiautomator dump` (it writes `/sdcard/window_dump.xml`), `screenrecord`, `bugreport`,
  `logcat -f`, `logcat -c`, `logcat -G`, `forget_car_gearhead.sh`, a reboot, and any write to a file.
- **No airplane-mode cycle on the S24.** §4's clean-run protocol does not apply to it. Each run starts
  from the head unit side only.
- If a run seems to need a forbidden command, mark it UNTESTABLE and go on. Do not improvise.
- Gearhead's own first-run screens on the S24 ("Welcome to Android Auto", Continue, permissions) are
  **hand steps for the operator**. Record each one in Setup notes.

## 1. Build and baseline

- **Build:** `main` at **`71375a68`** (`71375a689f704df1dc844e6a0656a58e471a12aa`), tree
  `4bdf708fad4db692e8a0d71c60eb259b06a4120a`. No candidate and no baseline: this round measures
  shipped behaviour.

```bash
git ls-remote fork main     # MUST print 71375a689f704df1dc844e6a0656a58e471a12aa
git fetch fork main
git worktree add ../ohu-wt-main-71375a68 71375a689f704df1dc844e6a0656a58e471a12aa
git -C ../ohu-wt-main-71375a68 rev-parse 'HEAD^{tree}'   # MUST print 4bdf708fad4db692e8a0d71c60eb259b06a4120a
```

Build with `build_hur.sh` and `HUR_DIR=../ohu-wt-main-71375a68`. Install with `adb -s <unit> install -r`
on D-POCO, D-SAM and D-HU. **Never on the S24.** Identity gate on each head unit:
`send ACTION_QUERY_STATE`, and the `data=` line MUST carry commit `71375a68`.

## 2. What this is and why

Users report: the phone shows "To continue select Android Auto" for a few seconds, then it goes
away, and the head unit shows nothing. Two of three reporters drive with a Samsung phone (S26
Ultra, S24+). Two of three head units are a phone or a tablet. The operator's S24 also struggled
against D-SAM in Native mode.

Four explanations fit, and this round separates them:

- **H0, notification access.** A Gearhead data clear revokes Android Auto's notification access.
  Gearhead then stops at `TapHeadUnitActivity` ("To continue, select Android Auto on your vehicle
  screen") while the Bluetooth handshake completes again and again (`rig-quirks/topics/gearhead.md`).
  One reporter cleared Android Auto's data.
- **H1, the stand-in hands-free link.** A phone or tablet head unit has no hands-free role, so the
  app publishes a stand-in record. Android Auto needs that link before it starts wireless setup.
  A Samsung driver may refuse it, or ask to pair again on every poke.
- **H2, the WiFi join.** The Samsung WiFi stack may join the P2P group late or not at all.
- **H3, the head unit.** D-SAM deletes a group when its only client leaves (`rig-quirks/units/D-SAM.md`
  and the D-SAM fix on `main`), so a struggle on D-SAM is not proof of a Samsung fault.

## 3. What is different about this round

- **Privacy.** S24 captures carry personal data (device names, network names, notifications).
  Keep them on the tester PC under `~/rig-private/samsung-driver-native-r1/`. **Do not upload them as
  release assets and do not commit them.** The results file quotes counts and short lines only, with
  `<REDACTED>` for every MAC, SSID, BSSID, passphrase, device name and account. The head unit
  captures may go to the usual evidence release.
- **No video is expected on D-POCO as head unit** (a portrait phone carries no video). Grade D-POCO
  sessions on SSL and `Service Discovery Response`, never on frames.
- **Gearhead dials one cached head unit.** If the S24 stays bonded to an earlier head unit, Gearhead
  can dial that unit's MAC and ignore the unit under test (`rig-quirks/units/D-POCO.md`). Each stage
  therefore runs with the S24 bonded to one rig head unit only, and R0 grades the dialled MAC.
- **Stages, because of USB ports.** Stage A: D-SAM, S24, D-POCO, D-MOTO. Stage B: D-POCO (as head
  unit), S24, D-MOTO. Stage C: D-HU, S24, D-POCO, D-MOTO. Name what is plugged in, in Setup notes.
- **Runs R3 and R4 are conditional.** Section 7 says when to skip them. A skipped run is not a FAIL.

## 4. Settings keys (each head unit, app stopped)

Write with the unit's own method: `set_hu_prefs.sh` on D-HU, the `run-as` method on D-POCO, the host
`python3` edit plus `run-as cp` on D-SAM (`rig-quirks/units/D-SAM.md`). Back up `settings.xml` first
and restore it at the end of the stage. `S24_MAC` comes from pre-flight step P3.

```xml
<int name="wifi-connection-mode" value="3" />
<set name="native-poke-bt-macs"><string>S24_MAC</string></set>
<boolean name="native-poke-all-paired" value="false" />
<int name="native-driver-selection-mode" value="0" />
<int name="onboarding-version" value="2" />
<int name="log-level" value="1" />
```

For the control run R3, the wake list holds `MOTO_MAC` instead of `S24_MAC`. `log-level` 1 is DEBUG.
`ACTION_LOG_MARKER` is not gated on `main`, so `allow-external-configuration` is not needed.

## 5. Helpers

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=<serial of the head unit for this stage>      # D-SAM 30041c35642d2200, D-POCO 4f4027e9, D-HU 27870808938846
S24=$(adb devices -l | awk '/model:SM_S926/{print $1}')
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
OUT=~/rig-private/samsung-driver-native-r1; mkdir -p $OUT

# one bring-up: ID is the run id plus attempt, PH is the driver phone serial
bringup() {
  ID=$1; PH=$2
  adb -s $HU shell am force-stop $PKG; adb -s $HU logcat -c
  stdbuf -oL adb -s $HU logcat -v threadtime > $OUT/$ID-hu.txt &  HUPID=$!
  stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash,events > $OUT/$ID-phone.txt &  PHPID=$!
  sleep 2
  adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity
  sleep 3; send ACTION_LOG_MARKER --es text $ID-start
  sleep 60
  adb -s $PH exec-out screencap -p > $OUT/$ID-phone-60s.png
  sleep 0.3
  adb -s $PH shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" > $OUT/$ID-phone-top.txt
  sleep 87
  send ACTION_LOG_MARKER --es text $ID-end
  adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit"; sleep 3
  kill $HUPID $PHPID
}
```

The phone logcat uses `-T 1`: it streams from now and does not clear the phone's buffer. The window
is 150 s from `$ID-start` to `$ID-end`. Never run two adb calls against one unit at the same time.

## 6. The deciding lines

**Head unit capture** (`$ID-hu.txt`, between `AutomationMarker: $ID-start` and `$ID-end`).
Each string checked with `grep -F` on `71375a68`:

| Line | Means |
|---|---|
| `WifiLauncher: Initializing WiFi Mode: NATIVE` | preflight: Native path active |
| `Attempting active poke to device` | a wake went out |
| `hands-free service level connection established` | the stand-in link reached the phone |
| `Connection accepted from` | the phone came back over Bluetooth |
| `Sending WifiStartRequest (Type 1)` | one handshake (count them) |
| `[RX] WifiStartResponse` and `[RX] WifiConnectStatus status=` | the phone's replies; record the status text |
| `Incoming connection detected from` | the phone joined the group and dialled |
| `SSL handshake complete` | session formed |
| `Service Discovery Response` | session past discovery |
| `(the platform took it down)` | D-SAM removed the group |
| `Client list empty` | D-SAM platform line before a removal (system line) |

**Phone capture** (`$ID-phone.txt`, from its first line to the wall-clock of `$ID-end`). These are
Gearhead and framework strings, not ours, and they drift between builds. Report the Gearhead
version in Setup notes. A zero means "absent or renamed", so always report the histogram too.

```bash
P=$OUT/$ID-phone.txt
grep -a -c "GH\." $P                                              # capture gate: MUST be > 0
grep -a -o "WIRELESS_SETUP_[A-Z_]*" $P | sort | uniq -c           # Gearhead's setup states
grep -a -c "NO_HFP_FROM_HU_PRESENCE" $P
grep -a -c "THROTTLE_LIMIT_EXCEEDED" $P
grep -a -c "WIRELESS_SETUP_CANCELLED_ALREADY_STARTED" $P
grep -a -c "TapHeadUnitActivity" $P
grep -a "Creating rfcomm socket for device" $P | grep -a -o "..:..$" | sort | uniq -c   # last 2 octets only
grep -a -o "CTRL-EVENT-[A-Z-]*" $P | sort | uniq -c               # the phone's WiFi join
grep -a -c -i "PAIRING_REQUEST\|bond state\|BOND_BONDING" $P      # pairing prompts
grep -a -c "Critical error" $P
```

On the head unit capture also run the pairing count, because one reporter sees pairing prompts on
the head unit side: `grep -a -c -i "PAIRING_REQUEST\|bond state\|BOND_BONDING" $ID-hu.txt`.

**Session formed** means, in the window: `SSL handshake complete` >= 1 AND
`Service Discovery Response` >= 1. Report the seconds from `$ID-start` to the first SSL line.

## 7. Runs

### Pre-flight (one batch, before Stage A)

P1. The operator enables USB debugging on the S24 and accepts the PC's key. Check
`adb devices -l` lists `model:SM_S926`.

P2. **Residue baseline on the S24**, read-only. Keep the output as `$OUT/residue-before.txt`:

```bash
adb -s $S24 shell 'date; ls -la /data/local/tmp; ls /sdcard/window_dump.xml /sdcard/*.png /sdcard/*.txt /sdcard/*.mp4 2>&1; ls /data/user_de/0/com.android.shell/files/bugreports 2>&1'
```

P3. **Inventory, read-only** (`$OUT/inventory.txt`, private):

```bash
adb -s $S24 shell getprop ro.build.version.release; adb -s $S24 shell getprop ro.build.version.oneui
adb -s $S24 shell dumpsys package com.google.android.projection.gearhead | grep -m2 -E "versionName|versionCode"
adb -s $S24 shell settings get secure enabled_notification_listeners | tr ':' '\n' | grep -c gearhead   # H0 input
adb -s $S24 shell dumpsys bluetooth_manager | grep -a -m3 -i -E "^ *address|state:"   # S24_MAC, BT on
adb -s $S24 shell dumpsys bluetooth_manager | grep -a -A12 "Bonded devices"           # names stay private
adb -s $S24 shell dumpsys wifi | grep -a -m3 -E "Wi-Fi is|mWifiInfo"                  # WiFi on
adb -s $S24 shell settings list global | grep -a -i -E "wifi|p2p|ble_scan"
```

Record in the results only: Android version, One UI version, Gearhead version, the notification
access count (0 or 1), Bluetooth on, WiFi on, and the number of bonded devices. If Bluetooth or WiFi
is off, ask the operator to turn it on. That is the only hand fix allowed here.

P4. Ask the operator, in one message, for all of these at once:
1. On the S24, keep the pairing with D-SAM's radio (`Navegadortz3`) for Stage A. Pair D-POCO's radio
   for Stage B and D-HU's radio for Stage C when the round reaches them.
2. Keep the S24 unlocked on its home screen during each run, and do the Gearhead first-run taps when
   they appear.
3. If P3 read 0 for notification access: may the operator grant it once for run R2 (Settings >
   Notifications > Device and app notifications > Android Auto) and remove it after the round?
4. Whether to keep the D-SAM pairing after the round (it existed before).

### Stage A: D-SAM as head unit

**R0 - S24 to D-SAM, 1 bring-up (4 min).** D-POCO and D-MOTO Bluetooth off:
`adb -s 4f4027e9 shell cmd bluetooth_manager disable`, the same on `ZY22GC3BM4`, then check both read
off after 20 s. D-HU's app stopped. Write section 4's keys on D-SAM, then `bringup R0-1 $S24`.

- Gate: phone `Creating rfcomm socket for device` last two octets equal D-SAM's radio, if the line
  appears at all. Wrong MAC: INCONCLUSIVE (stale target), and note which octets appeared.
- Record: session formed yes or no, handshakes before SSL, `WifiConnectStatus` values,
  `Client list empty` and `(the platform took it down)` counts, the phone histograms.
- No PASS or FAIL. R0 measures what the operator saw before.

Hand step after R0: on the S24, unpair D-SAM's radio and forget D-SAM in Android Auto's vehicle list
(only the rig head unit, never another vehicle). Pair D-POCO's radio. Skip the unpair if P4.4 said
keep, but then grade R1's dialled MAC with extra care.

### Stage B: D-POCO as head unit

Setup: D-POCO as head unit (`rig-quirks/units/D-POCO.md`). D-MOTO Bluetooth off for R1 and R2.
D-SAM's app stopped (`headunit://exit`, then force-stop).

**R1 - S24 to D-POCO, 2 bring-ups (7 min). This is the point of the round.**
`bringup R1-1 $S24`, then `bringup R1-2 $S24`.

- Preflight: `Initializing WiFi Mode: NATIVE` = 1 per bring-up, and at least 1
  `Attempting active poke to device`. If not, fix the settings and re-run once.
- Gate: dialled MAC is D-POCO's radio (as in R0).
- **PASS:** session formed in both bring-ups.
- **FAIL:** no session in either bring-up. Then report which gate stopped it:
  - **HFP gate (H1):** phone `NO_HFP_FROM_HU_PRESENCE` >= 1, or HU
    `hands-free service level connection established` = 0, or pairing count >= 1 on either side.
  - **Notification gate (H0):** `Connection accepted from` >= 1 and `Sending WifiStartRequest` >= 2
    and `Incoming connection detected` = 0, with `TapHeadUnitActivity` >= 1 in the phone capture or
    in `$ID-phone-top.txt`.
  - **WiFi gate (H2):** `WifiConnectStatus status=` not SUCCESS, or phone
    `CTRL-EVENT-ASSOC-REJECT` / `CTRL-EVENT-NETWORK-NOT-FOUND` >= 1.
  - **After the join:** `Incoming connection detected` >= 1 and no SSL.
- 1 of 2: report as MIXED with both sets of numbers.
- If `THROTTLE_LIMIT_EXCEEDED` >= 1 or `WIRELESS_SETUP_CANCELLED_ALREADY_STARTED` >= 1: the phone is
  latched. Stop Stage B, report, and do not reboot or clear the S24.

**R2 - S24 to D-POCO with notification access granted (4 min). Only if** P3 read 0 AND R1 hit the
notification gate AND the operator said yes in P4.3. The operator grants the access by hand, then
`bringup R2-1 $S24`. PASS: session formed. This is the positive control for H0. The operator removes
the access again after the round.

**R3 - D-MOTO to D-POCO, 1 bring-up (4 min). Only if R1 FAILED.** Control phone. S24 Bluetooth is
not touched: the wake list names only `MOTO_MAC`, and D-MOTO Bluetooth goes back on
(`cmd bluetooth_manager enable` on `ZY22GC3BM4`). Check D-MOTO is bonded to D-POCO first with
`adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -A12 "Bonded devices"`. If it is not, pair
by hand and note it. `bringup R3-1 ZY22GC3BM4`.
- **R3 PASS and R1 FAIL:** the fault follows the Samsung phone. Run R4.
- **R3 FAIL at the same gate as R1:** the fault is the phone-as-head-unit path, not Samsung.
  Skip R4.

Hand step after Stage B: unpair D-POCO's radio on the S24 and forget it in Android Auto. Pair D-HU's
radio if R4 runs.

### Stage C: D-HU as head unit

**R4 - S24 to D-HU, 1 bring-up (4 min). Only if R1 FAILED and R3 PASSED.** D-HU has a real
hands-free role, so no stand-in record. D-POCO and D-MOTO Bluetooth off. `bringup R4-1 $S24`.
- **PASS:** session formed. With R1 FAIL and R3 PASS, the fault is Samsung plus a stand-in head
  unit (H1 or H2 by R1's gate).
- **FAIL:** the Samsung phone fails on a real head unit too. Report the gate.

Hand step after Stage C: unpair D-HU's radio on the S24 and forget it in Android Auto.

### Final: residue check (2 min, last adb command on the S24)

Re-run P2 into `$OUT/residue-after.txt` and diff it against `residue-before.txt`, ignoring the
`date` line. Then:

```bash
adb -s $S24 shell 'find /sdcard /data/local/tmp -type f -mmin -90 2>/dev/null | grep -v "/Android/" | wc -l'
```

**PASS:** the diff is empty. The `find` count is reported as a number only; if it is not 0, list the
paths to the operator in chat (not in the results file), so they can confirm each one is not ours.

Then the operator, by hand, in this order:
1. Android Auto > Settings > Previously connected cars: confirm only the operator's own vehicles
   remain. Never use "Forget all cars".
2. Bluetooth: confirm no rig radio remains paired (D-HU `Navegadortz2`, D-SAM `Navegadortz3`, D-POCO), except D-SAM if P4.4 said keep.
3. WiFi > Saved networks: forget any `DIRECT-` entry.
4. If R2 ran: remove Android Auto's notification access again only if it was off at P3.
5. Developer options > **Revoke USB debugging authorisations** (this deletes the PC's key on the
   phone), then turn USB debugging off, then turn Developer options off if it was off before.

## 8. Do not re-run

- D-SAM's empty-group removal and its fix: settled (`native-aa-dsam-wifi-unavailable` round 3).
  R0 only records whether the S24 meets it.
- D-POCO to D-HU Native sessions: settled in many rounds; no D-POCO-as-phone control is needed.

## 9. Report back

1. R1: session formed in how many of 2 bring-ups, and the gate that stopped each failure.
2. R3 (if run): the same for D-MOTO, so the result reads "Samsung" or "phone as head unit".
3. P3's notification access count and Gearhead version, and the residue check verdict.
