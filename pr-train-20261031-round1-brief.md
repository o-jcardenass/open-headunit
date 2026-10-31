# pr-train-20261031, round 1 brief: #1212, #1211, #1167 and #1187 on one build

Published name: `pr-train-20261031-round1-brief.md`. Results go in `pr-train-20261031-round1-results.md`.
Evidence: create the release `rig-evidence-pr-train-20261031` and add `pr-train-20261031-round1-captures.zip`.

**Five points.** X1 (#1212): a second service start inside the 5 s Stop wait must not crash the app. P1 and R1
(#1211): the anchor must not flip at session start, and a resolution changed while `MainActivity` is stopped must
reach the phone. E2, E4 and E3 (#1187): sessions over the ESP32 relay on firmware v1.0.1, a reconnect after a
force-stop, and a non-relay session that the relay must leave alone. #1167 rides along in PB and P1.

Units: stage X on D-HU (head unit) with D-MOTO (phone); stage P on D-POCO (head unit, API 35) with D-MOTO; stage E
on D-HU with D-POCO (phone) and board E-1; E5 on D-SAM (API 19).
Time: setup 10 min, X1 5, PB 5, RB 4, P1 5, R1 4, flash 6, E1 8, E2 12, E4 6, E3 6, E5 6, cleanup 10, plus thermal
waits. **About 1 h 30 min.**

Queue line: `pr-train-20261031-round1-brief.md` (T `8e0452dd` on `fork/test/pr-train-20261031`, B `c2c0e95a` = `main`;
two builds in one worktree; units D-HU, D-POCO, D-MOTO, D-SAM, E-1; X1, P1, R1, E2, E4, E3 are the points).
Pairing: none. The round uses every unit, so it cannot run beside another round.

```rig-requires
lib 8191b7c4c
candidate T 8e0452ddc
candidate B c2c0e95aa
unit D-HU role=headunit wifi=on bt=on
unit D-HU@PB,RB,P1,R1 bt=off
unit D-MOTO@X1 role=phone wifi=on bt=on bond=D-HU aa=D-HU battery=30
unit D-MOTO@PB,RB,P1,R1 role=phone wifi=on bt=on bond=D-POCO aa=D-POCO battery=30
unit D-MOTO@flash,E1,E2,E4,E3,E5 wifi=off bt=off
unit D-POCO@PB,RB,P1,R1 role=headunit wifi=on bt=on battery=50
unit D-POCO@flash,E1,E2,E4,E3,cleanup role=phone wifi=on bt=on nobond=D-HU aa=D-HU battery=30
unit D-SAM@E5 role=headunit
hardware board E-1 (ESP32, MAC 78:1c:3c:2b:ac:ec) is on the host's serial port; the flash run puts release v1.0.1 on it; board E-2 is not touched
hardware D-SAM charges from its own supply or the PC port
confirm on D-POCO, Android Auto's developer setting "Start head unit server" is on (E3 needs :5277)
confirm D-MOTO unlocks with no PIN prompt
run setup cap=1200 script=setup.sh grade=grade-setup.sh
run X1 cap=600 script=x1.sh grade=grade-x1.sh point=yes
run PB cap=700 script=pb.sh grade=grade-pb.sh
run RB cap=600 script=rb.sh grade=grade-rb.sh
run P1 cap=700 script=p1.sh grade=grade-p1.sh point=yes
run R1 cap=600 script=r1.sh grade=grade-r1.sh point=yes
run flash cap=900 script=flash.sh grade=grade-flash.sh
run E1 cap=900 script=e1.sh grade=grade-e1.sh hand="on D-POCO, pair OpenHU-BT-Relay in Bluetooth settings (PIN 0000) when OPERATOR NOW prints"
run E2 cap=1800 script=e2.sh grade=grade-e2.sh point=yes
run E4 cap=720 script=e4.sh grade=grade-e4.sh point=yes
run E3 cap=720 script=e3.sh grade=grade-e3.sh point=yes
run E5 cap=900 script=e5.sh grade=grade-e5.sh
run cleanup cap=1200 script=cleanup.sh grade=grade-cleanup.sh always=yes
stop setup FAIL,UNTESTABLE skip=all
stop PB UNTESTABLE skip=P1
stop RB UNTESTABLE skip=R1
stop flash FAIL,UNTESTABLE skip=E1,E2,E4
stop E1 FAIL,UNTESTABLE skip=E2,E4
```

D-HU's Bluetooth is off in stage P because D-MOTO is bonded to D-HU too, and Gearhead dials the head unit it last
used. D-HU's radio turns itself back on, so each P and R script also holds it off with `rl_hold_off`. D-POCO must not
be bonded to D-HU in stage E: a phone bonded to D-HU dials D-HU and ignores the relay (esp32 round 2, stage B).

| Run | Units | Arm | PR | What it proves | Min |
|---|---|---|---|---|---|
| setup | D-HU, D-POCO | | | DEX of both arms, T on D-HU, B on D-POCO, settings backups, the phones' MACs | 10 |
| **X1** | D-HU + D-MOTO | T | #1212 | **a second Stop inside the 5 s teardown wait: no crash, and the service stops** | 5 |
| PB | D-POCO as HU + D-MOTO | B | #1211, #1167 | control: geometry lines at session start, `fullscreen-mode` 0 | 5 |
| RB | D-POCO as HU + D-MOTO | B | #1211 | control: a resolution changed with `MainActivity` stopped reaches the phone on `main` | 4 |
| **P1** | D-POCO as HU + D-MOTO | T | #1211, #1167 | **the anchor does not flip at session start and ends on `mAppBounds`** | 5 |
| **R1** | D-POCO as HU + D-MOTO | T | #1211 | **the same resolution change reaches the phone on T (the review finding)** | 4 |
| flash | E-1, D-POCO | | #1187 | firmware v1.0.1 on E-1, checked by hash; no old relay bond on D-POCO | 6 |
| E1 | D-HU + D-POCO + E-1 | T | #1187 | the renamed screen and its pairing flow; the row is in the settings list on API 34 | 8 |
| **E2** | D-HU + D-POCO + E-1 | T | #1187 | **5 bring-ups over the board; the phone dials the board, never D-HU** | 12 |
| **E4** | D-HU + D-POCO + E-1 | T | #1187 | **a force-stop during a relay session, a relaunch, and a new session with no board reset** | 6 |
| **E3** | D-HU + D-POCO + E-1 | T | #1187 | **board saved and on, board held in reset: a Headunit Server session is still up 30 s after SSL** | 6 |
| E5 | D-SAM | T | #1187 | API 19: the relay row is not in the settings list, and the relay screen leaves with no crash | 6 |
| cleanup | all | | | board advertising, B and settings back on D-HU and D-POCO, prior APK and settings on D-SAM | 10 |

## 1. Build and baseline

| Arm | Ref | Commit |
|---|---|---|
| T (candidate) | `fork/test/pr-train-20261031` | `8e0452ddc2181ef2dd67de57f2232c087763a2f5` |
| B (control) | `main` | `c2c0e95aa514ae00c8e3ce5047701b83ca5f1264` |

T is `main` `c2c0e95a` plus four merge commits, in this order: #1212 head `5a04f3c8`, #1211 head `b0018e28`,
#1167 head `baf7bb72`, #1187 head `626755bf`. No history was rewritten. Build both arms in one worktree
(`git checkout --detach <sha>`, then check the SHA), with `rl_thermal_gate` and `MAX_WORKERS=2`, and copy them to
`$EV/apk/T.apk` and `$EV/apk/B.apk` at once. The DEX tell is `Esp32RelaySession`: 1 or more in T, 0 in B. The dev
host ran the JVM suite at T: 4423 (0 failures). No rig unit tests.

Firmware: `kendrickkim/aa-native-bt-broker-for-openheadunit` release `v1.0.1` (published 2026-10-31 00:55 UTC).
The flash run downloads the four bins and checks them against these sha256 values:

| File | sha256 |
|---|---|
| `bootloader.bin` | `22d12685b5d131492a54cc1ee491768ac4c3be93b7a10ba05fdabb3636533534` |
| `partitions.bin` | `d90b0f480968f1cde746d7d8746261b38ec098b63c7f45b1aa23698cca4a2236` |
| `boot_app0.bin` | `f94c5d786a7a8fab06ac5d10e33bf37711a6697636dc037559ea19cc410a17f0` |
| `firmware.bin` | `4544db072f42c473d270cfd3a4d87102d42251f848947132c315b13054ccfae9` |

## 2. What this is and why

Four upstream PRs share one build. Each section names the review finding the rig must answer.

- **#1212 (microphone foreground type).** Our last review found that a service start during the 5 s Stop wait
  skipped `startForeground`, so Android could kill the app when `stopSelf()` ran. The new commit `5a04f3c8`
  promotes with the base types while `isDestroying` is set (`AapService.startForegroundWithCurrentTypes`). X1 sends
  two Exits about 1 s apart, so the second start lands inside the wait. `pr-train-20261029` round 1 ran the same
  double Exit on an older head of this PR and passed; this run checks the new commit.
- **#1211 (projection insets, API 35).** In `pr-train-20261029` round 1, P1 on T logged 11 anchor moves in the first
  1.5 s of a session, and the anchor flipped between `2309x937`, `2400x923` and `2400x1080`. B logged none. The new
  commit `b0018e28` publishes geometry only from completed layouts (`HeadUnitScreenConfig.publishWindowGeometry`).
  P1 runs that case again against PB on B.
- **#1211, a new review finding.** `HeadUnitScreenConfig.initGeometry` returns early when `windowOwner` is set and the
  stored measurement's settings hash differs from the current one, before `geometrySettings` is refreshed.
  `announceGeometry` then calls `recalculate()`, which reads the old `resolutionId`. Nothing releases the owner when
  `MainActivity` stops. So a resolution changed while `MainActivity` is stopped can go to the phone as the old value.
  R1 sets that case up. The `[RES_CAP] resolutionId=` line prints the live setting, not the one `recalculate()`
  used, so R1 grades the announced size (`[ServiceDiscovery] NegotiatedResolution is: WxH`), never that field.
- **#1167 (ZBT reachability).** No rig unit has the 3152 daemon. PB and P1 only check that the default Native session
  forms and that `[ZBT] nothing is listening on ` does not print more often on T than on B.
- **#1187 (ESP32 relay).** The route is renamed: package `connection/esp32`, log prefixes `ESP32 relay:` and
  `NativeAA: [ESP32]`, relay file `Esp32Relay.xml`. Commit `544cf488` limits the 10 s `PROJECTION_UP` watch to
  sessions that came up over the board (our review item 4). Firmware v1.0.1 clears stale projection state on a BLE
  disconnect and on HELLO, so a force-killed head unit app does not block the next start (our review item 6). The
  contributor also says the board now resets after 10 s without `PROJECTION_UP`. Commit `3c20980a` hides the row below
  API 23 and guards `scanner` in `onDestroyView`, which crashed D-SAM in esp32 round 3 (run S).

## 3. What is different about this round

- **R1 changes the resolution with a verb, not through the Settings screen.** `ACTION_SET_SETTINGS --es json` imports
  `resolutionId` into the live process's SharedPreferences, as the screen's Save does
  (`SettingsBackupManager.importFromJson`). The screen route would need a scroll to the Video row, which the rig
  forbids. The process stays alive, and Home keeps `MainActivity` stopped. A `settings.xml` edit plus a force-stop
  does not reproduce the case. R1 is possible on this rig.
- **R1 chains from P1, and RB from PB.** The P run ends with a Disconnect that leaves `MainActivity` in front and the
  app alive. The R run starts there. If the app is not running, the R script makes its own session first
  (`chained=0` in the gate file).
- **The session is the only route to the announce.** A session reads nothing until `AapProjectionActivity` has a
  surface, so the projection screen draws before the phone's service discovery is read. If that first draw publishes
  the new geometry, R1 passes and the review finding does not reproduce on the Native reconnect path. That is a
  result, not a rig fault.
- **E3 holds the board in reset instead of a power cut.** `serial_cap.py --hold` keeps EN low through RTS, which
  esp32 round 3 proved (0 boot lines in K). No hand step.
- **E3 is a Headunit Server session, not USB.** D-HU cannot host USB (`rig-quirks/topics/tooling.md`). E3 switches
  D-HU to `wifi-connection-mode` 1 and uses D-POCO's server on :5277 over the home WiFi, as the main smoke's H1 does.
- **E5 reads the list through the settings search.** `--es extra_search_query esp32` filters the one long list, so
  no scroll is needed. E1 takes the same dump on D-HU as the positive control. E5 then opens the relay screen by its
  nav id (from `aapt2`), which is the route that crashed in esp32 round 3.
- **Hand steps:** one, in E1 (the phone side of the pairing). **Injected taps:** at most 5 per run, each on a node
  found by exact text in a `uiautomator dump`, logged in `$EV/taps.log`. E1 uses 4 at most, flash and cleanup 3 at most.
- **Markers** are `adb shell log -t AutomationMarker <label>` on the head unit and the phone of each stage, and a
  stamped line in `$EV/serial-E1.txt` during the E runs. No app process is needed for a marker.
- **Rig state the last rounds left:** E-1 runs v1.0.0 and advertises; D-HU keeps its LE bond to
  `OpenHU-BT-Relay-Setup`; D-POCO has no relay bond and no D-HU bond.
- Read `rig-quirks/topics/tooling.md`, `topics/bt.md`, `topics/gearhead.md`, `topics/lifecycle.md`,
  `topics/projection.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-MOTO.md`, `units/D-SAM.md`,
  `units/D-SAM-and-D-HP.md`, and `native-bt-broker-esp32-hardware-findings.md`.

## 4. Settings keys

Written by `setkeys` with the app stopped, in one pass, and read back into `$EV/<run>-settings.xml`. D-HU goes through
`set_hu_settings_host.py` (root), the others through `set_prefs_runas_host.py`.

| Key | Type | Value | Runs |
|---|---|---|---|
| `log-level` | int | 2 (INFO) | all |
| `onboarding-version` | int | 2 | all |
| `allow-external-configuration` | boolean | true (R1 and RB need `ACTION_SET_SETTINGS`) | all |
| `kill-on-disconnect`, `enable-floating-button`, `narrow-band-profile-cap`, `external-bt-zbt-transport` | boolean | false | all |
| `native-driver-selection-mode` | int | 0 | all |
| `video-profile-starvation-cap` | | delete | all |
| `wifi-connection-mode` | int | 3; 1 in E3 | |
| `connection-modes` | set | `wifi` | all |
| `native-ap-transport` | int | 0 | Native runs |
| `native-poke-all-paired` | boolean | false | Native runs |
| `native-poke-bt-macs`, `native-preferred-device-mac`, `last-connected-native-mac` | set, string | D-MOTO's MAC in X and P; D-POCO's in E | Native runs |
| `native-mac` | | delete | Native runs |
| `use-head-unit-microphone` | boolean | true | X1 |
| `fullscreen-mode` | int | 0 (Normal, bars shown) | PB, P1, RB, R1 |
| `resolutionId` | int | 1 (480p); R runs set 2 (720p) by verb | PB, P1, RB, R1 |
| `native-aa-wake-damage-verdict`, `native-aa-wake-armings-without-session` | int | 0 | E runs |

The relay file `shared_prefs/Esp32Relay.xml` on D-HU, written as root with the app stopped:

| Run | File |
|---|---|
| E1 | `enabled` true, no `address` (the pick writes it) |
| E2, E4 | as E1 saved it |
| E3 | `enabled` true, `address` `78:1C:3C:2B:AC:EE`, `name` `OpenHU-BT-Relay-Setup` |
| cleanup | removed |

The rig audio settings stay as they are; this round grades no audio.

## 5. The deciding lines

App strings checked with `git grep -F` in `app/src/main` at `8e0452ddc` and `c2c0e95aa`. The ESP32 lines exist on T
only, and only T runs read them. Head-unit lines are in `$EV/<run>-hu.txt` (D-SAM: `$EV/E5-sam.txt`), phone lines in
`$EV/<run>-ph.txt`, board lines in `$EV/serial-E1.txt` (host clock). Windows are named by their markers.

| Id | Line | Where, window | Read |
|---|---|---|---|
| L-SSL | `SSL handshake complete` | HU, per run | a session formed (one line per handshake at INFO) |
| L-THR | `Throughput over ` | HU | frames still render |
| L-STOP | `Stop action received.` | HU, `X1-exit` to `X1-end` | each Stop that reached the service |
| L-DES | `AapService destroying` | HU, `X1-exit` to `X1-end` | the service ended |
| L-TDW | `the wireless teardown did not finish in` | HU, X1 | the 5 s wait ran out (report) |
| L-MIC | `claimed the microphone foreground-service type` | HU, X1 | the phone opened the microphone (report) |
| S-FGS | `did not then call Service.startForeground` | HU, X1 whole | system line: the bug |
| S-FGSX | `ForegroundServiceDidNotStartInTimeException` | HU, X1 whole | system line: the bug |
| S-CRASH | `Process: com.andrerinas.headunitrevived` | HU, whole | system line of a `FATAL EXCEPTION` block |
| L-MOVE | `HeadUnitScreenConfig: anchor ` | HU, P runs | the anchor moved (both arms print it) |
| L-HON | `Honest Init \| Mode: ` | HU, P runs | a full geometry pass, with its anchor |
| L-SMM | `Surface mismatch detected!` | HU, P runs | must stay 0 |
| L-ZBT | `[ZBT] nothing is listening on ` | HU, P runs | #1167: T no more than B |
| L-ANN | `NegotiatedResolution is: ` | HU, `R-arm` to `R-end` | the size announced to the phone |
| L-RES | `[RES_CAP] resolutionId=` | HU, R runs | report only: it prints the live setting |
| L-RELAY | `NativeAA: Bluetooth relay transport is on` | HU, E runs | the relay route was chosen |
| L-LIST | `ACTIVELY LISTENING on Android Auto UUID` | HU, E runs | the stock route was used instead |
| L-PAIRW | `ESP32 relay: opening new-phone pairing window on` | HU, E1 | the app opened the pairing window |
| L-PAIRC | `ESP32 relay: phone pairing confirmed` | HU, E1 | the app saw the pair complete |
| L-HFP | `ESP32 relay: releasing HFP (` | HU, E2 | HFP off at projection (report) |
| L-PUW | `PROJECTION_UP not recorded for 10s` | HU, E3 | the watch ended a non-relay session (the bug) |
| L-PUF | `ESP32 relay: PROJECTION_UP failed for ` | HU, E3 | the same, from the relay side |
| L-DISC | `NetworkDiscovery: Found Headunit Server on ` | HU, E3 | D-HU found D-POCO's server (report) |
| L-GRP | `createGroup SUCCESS` | HU, E4 | a group after the relaunch (report) |
| P-DIAL | `Creating rfcomm socket for device: ` | phone | the MAC follows: board `78:1C:3C:2B:AC:EE`, D-HU `11:46:03:10:33:59` |
| P-CRIT | `Critical error` | phone, run window less 2 s after each exit marker | Gearhead's own error |
| F-BOOT | `Classic name OpenHU-BT-Relay, BLE name` | board | a boot: inside a window it voids the window |
| F-ADV | `advertising OpenHU-BT-Relay-Setup` | board | BLE is up |
| F-PMODE | `pairing mode for ` | board, E1 | the pairing window opened |
| F-PAIRED | `phone paired: ` | board, E1 | the phone paired with the board |
| F-PFAIL | `pairing failed: ` | board, E1 | a pairing attempt failed |
| F-CLEAR | `projection cleared (` | board, E4 | v1.0.1 cleared stale state (report) |
| F-BLE | `head unit BLE connected` | board, E4 | the relaunched app reached the board (report) |
| F-HASH | `Hash of data verified` | esptool, flash | one line per flashed file |

## 6. Runs

### setup. Known state

**setup.sh**
```bash
rl_step "host: the DEX of both arms"
for a in T B; do
  echo "$a $(unzip -p "$EV/apk/$a.apk" 'classes*.dex' 2>/dev/null | strings | grep -acF Esp32RelaySession)"
done > "$EV/setup-dex.txt"
rl_step "D-HU: back up settings, read the shared_prefs owner, install T"
adb -s "$DHU" shell am force-stop "$PKG"
[ -s "$EV/settings-backup-hu.xml" ] || adb -s "$DHU" shell cat "$SP/settings.xml" > "$EV/settings-backup-hu.xml"
adb -s "$DHU" shell stat -c '%U:%G %a' "$SP" > "$EV/setup-hu-sp.txt"
install_apk "$DHU" "$APK_T" setup-hu
send_to "$DHU" ACTION_QUERY_STATE > "$EV/setup-hu-query.txt" 2>&1
adb -s "$DHU" shell am force-stop "$PKG"
rl_step "D-POCO: back up settings, install B"
adb -s "$POCO" shell am force-stop "$PKG"
[ -s "$EV/settings-backup-poco.xml" ] || adb -s "$POCO" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-backup-poco.xml"
install_apk "$POCO" "$APK_B" setup-poco
send_to "$POCO" ACTION_QUERY_STATE > "$EV/setup-poco-query.txt" 2>&1
adb -s "$POCO" shell am force-stop "$PKG"
rl_step "the phones' Bluetooth addresses"
echo "moto=$(btaddr "$MOTO" A0:46:5A:97:E4:95) poco=$(btaddr "$POCO" DC:B7:2E:5E:4E:59)" > "$EV/macs.txt"
```

**grade-setup.sh**
```bash
rl_cond 1 ge "$(awk '$1=="T"{print $2}' "$EV/setup-dex.txt")" 1 'Esp32RelaySession in the T DEX'
rl_cond 2 eq "$(awk '$1=="B"{print $2}' "$EV/setup-dex.txt")" 0 'Esp32RelaySession not in the B DEX'
rl_cond 3 eq "$(grep -c '^md5match=1' "$EV/setup-hu-identity.txt")" 1 'T installed on D-HU (md5)'  # not-literal
rl_cond 4 eq "$(grep -c '^md5match=1' "$EV/setup-poco-identity.txt")" 1 'B installed on D-POCO (md5)'  # not-literal
rl_cond 5 ge "$(grep -c "$T_SHA" "$EV/setup-hu-query.txt")" 1 "D-HU reports commit $T_SHA"
rl_cond 6 ge "$(grep -c "$B_SHA" "$EV/setup-poco-query.txt")" 1 "D-POCO reports commit $B_SHA"
rl_cond 7 ge "$(grep -ac '<map' "$EV/settings-backup-hu.xml")" 1 'D-HU settings backup taken'  # not-literal
rl_cond 8 ge "$(grep -ac '<map' "$EV/settings-backup-poco.xml")" 1 'D-POCO settings backup taken'  # not-literal
```

- **PASS** when all eight hold. Any other verdict stops the round; cleanup still runs.
- Report the `stat` line of D-HU's `shared_prefs` and both MACs.

Decision: PASS starts X1 on a known build. FAIL means the wrong APK or no backup, so the round stops before any unit
changes further.

### X1. A second Stop inside the teardown wait (the point for #1212)

**x1.sh**
```bash
MARK_ON="$DHU $MOTO"; F="$EV/X1-hu.txt"
rm -f "$EV/X1-thermal.txt" "$EV/X1-gate.txt"
rl_thermal_gate || { echo HOST_HOT > "$EV/X1-thermal.txt"; exit 2; }
apk_check "$DHU" "$APK_T" X1
send_to "$DHU" ACTION_QUERY_STATE > "$EV/X1-query.txt" 2>&1
setkeys "$DHU" $(native_keys "$MOTO_BT") scalar:boolean:use-head-unit-microphone:true
adb -s "$MOTO" shell svc power stayon true
adb -s "$MOTO" shell input keyevent KEYCODE_WAKEUP
rl_cap_start X1-hu "$DHU"; rl_cap_start X1-ph "$MOTO"
if session "$DHU" "$F" X1-start; then
  rl_wait_line "$F" 'Throughput over ' 30 > /dev/null
  rl_sleep 30
  mark X1-exit
  send_to "$DHU" ACTION_EXIT > "$EV/X1-exit1.txt" 2>&1
  mark X1-exit2
  send_to "$DHU" ACTION_EXIT > "$EV/X1-exit2.txt" 2>&1
  rl_sleep 20
fi
mark X1-end
adb -s "$DHU" shell dumpsys activity services "$PKG" | tr -d '\r' > "$EV/X1-services.txt"
rl_cap_stop X1-hu; rl_cap_stop X1-ph
adb -s "$DHU" shell am force-stop "$PKG"
adb -s "$MOTO" shell svc power stayon false
```

**grade-x1.sh**
```bash
[ -s "$EV/X1-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
F="$EV/X1-hu.txt"; P="$EV/X1-ph.txt"
grep -q '^session=ok' "$EV/X1-gate.txt" 2>/dev/null || { rl_verdict UNTESTABLE "session gate: no SSL in 2 attempts"; exit 0; }  # not-literal
STOPS=$(rl_cnt "$F" X1-exit X1-end 'Stop action received.')
INWAIT=$(rl_win "$F" X1-exit X1-end | awk '/AapService destroying/{exit} /Stop action received\./{n++} END{print n+0}')
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/X1-identity.txt")" 1 'T on D-HU (md5)'  # not-literal
rl_cond 2 ge "$(grep -c "$T_SHA" "$EV/X1-query.txt")" 1 "commit $T_SHA"
rl_cond 3 ge "$(grep -ac 'name="use-head-unit-microphone" value="true"' "$EV/X1-settings.xml")" 1 'use-head-unit-microphone true read back'  # not-literal
rl_cond 4 ge "$STOPS" 2 'L-STOP twice after X1-exit'
rl_cond 5 ge "$INWAIT" 2 'both Stops before the first L-DES (the second start fell in the wait)'
rl_cond 6 eq "$(grep -acF 'did not then call Service.startForeground' "$F")" 0 'S-FGS 0'  # system
rl_cond 7 eq "$(grep -acF 'ForegroundServiceDidNotStartInTimeException' "$F")" 0 'S-FGSX 0'  # system
rl_cond 8 eq "$(grep -acF 'Process: com.andrerinas.headunitrevived' "$F")" 0 'S-CRASH 0 (no FATAL EXCEPTION for the app)'  # system
rl_cond 9 ge "$(rl_cnt "$F" X1-exit X1-end 'AapService destroying')" 1 'L-DES: the service was destroyed'
rl_cond 10 eq "$(grep -ac 'AapService' "$EV/X1-services.txt")" 0 'no AapService record 20 s after the Exits'  # not-literal
rl_cond 11 eq "$(python3 -I "$SCR/crit_count.py" "$P" X1-start X1-end 'Critical error' X1-exit X1-exit2)" 0 'phone: P-CRIT, 2 s after each Exit left out'  # phone
rl_cond 12 ge "$(rl_cnt "$F" X1-exit X1-end 'the wireless teardown did not finish in')" 0 'report: L-TDW'
rl_cond 13 ge "$(rl_cnt "$F" X1-start X1-end 'claimed the microphone foreground-service type')" 0 'report: L-MIC'
if [ "${STOPS:-0}" -lt 2 ]; then rl_verdict INCONCLUSIVE "the second Exit never reached the service"
elif [ "${INWAIT:-0}" -lt 2 ]; then rl_verdict INCONCLUSIVE "the second Exit landed after the destroy, outside the teardown wait"
fi
```

- **PASS** when conditions 1 to 11 hold: the second start promoted, nothing crashed, and the service stopped.
- **FAIL** on S-FGS, S-FGSX or S-CRASH, or when the service is still up 20 s after the Exits.
- **INCONCLUSIVE** when only one Stop reached the service, or the second one came after `AapService destroying`.
- **What a PASS looks like if the change did nothing:** condition 5. Without it, both Exits could land outside the
  wait and pass on any build. L-MIC stays 0 unless the phone opens the microphone (its assistant is offline on the
  rig), so this run covers the shutdown promotion, not a live capture.

Decision: PASS closes our #1212 review item, and the PR can merge. FAIL points at `startForegroundWithCurrentTypes`
during `isDestroying` and goes back to the contributor with the crash block.

### PB. Control: geometry at session start on B

**pb.sh**
```bash
APK=$APK_B
p_cycle
```

**grade-pb.sh**
```bash
[ -s "$EV/PB-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
p_counts PB
[ "${GATE:-0}" -ge 1 ] || { rl_verdict UNTESTABLE "session gate: no SSL in 2 attempts"; exit 0; }
rl_cond 1 eq "$MD5" 1 'B on D-POCO (md5)'
rl_cond 2 ge "$(grep -c "$B_SHA" "$EV/PB-query.txt")" 1 "commit $B_SHA"
rl_cond 3 ge "$FS0" 1 'fullscreen-mode 0 read back'
rl_cond 4 ge "$T0" 1 'an L-SSL line opened the 2 s window'
rl_cond 5 ge "$MOVES2" 0 "report: L-MOVE in the 2 s after SSL ($MOVES2; whole session $MOVESALL)"
rl_cond 6 ge "$HONEST2" 0 "report: L-HON in the 2 s after SSL ($HONEST2; whole session $HONESTALL)"
rl_cond 7 ge "$MATCH" 0 "report: final anchor $FINAL against mAppBounds $BOUNDS (1 = equal)"
rl_cond 8 eq "$SMM" 0 'L-SMM 0'
rl_cond 9 eq "$CRASH" 0 'S-CRASH 0'
rl_cond 10 ge "$ZBT" 0 "report: L-ZBT ($ZBT)"
rl_cond 11 eq "$CRIT" 0 'phone: P-CRIT, 2 s after the exit left out'
```

- **PASS** when the session forms and conditions 1 to 4, 8, 9 and 11 hold. Conditions 5, 6, 7 and 10 are the
  numbers P1 compares against.
- **UNTESTABLE** when no session forms in 2 attempts. The stop rule then skips P1.

Decision: PASS gives P1 its baseline counts. UNTESTABLE removes the comparison, so P1 does not run.

### RB. Control: a resolution change with MainActivity stopped, on B

**rb.sh**
```bash
APK=$APK_B
r_cycle
```

**grade-rb.sh**
```bash
[ -s "$EV/RB-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
r_counts RB
rl_cond 1 eq "$MD5" 1 'B on D-POCO (md5)'
rl_cond 2 ge "$PRE1" 1 'resolutionId 1 before the change'
rl_cond 3 ge "$IMPORTED" 1 'ACTION_SET_SETTINGS imported one key'
rl_cond 4 ge "$POST2" 1 'resolutionId 2 read back with the process alive'
rl_cond 5 eq "$FOCUS_MAIN" 0 'MainActivity not resumed between Home and the arm'
rl_cond 6 ge "$SECOND" 1 'the phone reconnected (L-SSL after RB-arm)'
rl_cond 7 eq "$CRIT" 0 'phone: P-CRIT, 2 s after the exit left out'
rl_cond 8 ge "$NEWV" 0 "report: announced $ANN (1 = the new 720p)"
if [ "${SECOND:-0}" -lt 1 ]; then rl_verdict UNTESTABLE "the phone did not reconnect after the arm"
elif [ -z "$ANN" ]; then rl_verdict UNTESTABLE "no L-ANN after RB-arm"
elif [ "${NEWV:-0}" -ne 1 ]; then rl_verdict INCONCLUSIVE "main also announced $ANN, so R1 cannot separate the PR from main"
fi
```

- **PASS** when conditions 1 to 7 hold and `main` announces the new 720p. That is the expected control.
- **INCONCLUSIVE** when `main` announces the old value too. R1 then reports its number and grades INCONCLUSIVE.
- **UNTESTABLE** when the phone does not reconnect or no announce prints. The stop rule then skips R1.

Decision: PASS makes R1 a clean A/B. INCONCLUSIVE means the stale announce is older than #1211, and the finding moves
to a separate `main` ticket.

### P1. The anchor at session start on T (the point for #1211's geometry)

**p1.sh**
```bash
APK=$APK_T
p_cycle
```

**grade-p1.sh**
```bash
[ -s "$EV/P1-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
p_counts PB; PB_M=$MOVES2; PB_H=$HONEST2; PB_Z=$ZBT; PB_GATE=$GATE
p_counts P1
[ "${GATE:-0}" -ge 1 ] || { rl_verdict UNTESTABLE "session gate: no SSL in 2 attempts"; exit 0; }
rl_cond 1 eq "$MD5" 1 'T on D-POCO (md5)'
rl_cond 2 ge "$(grep -c "$T_SHA" "$EV/P1-query.txt")" 1 "commit $T_SHA"
rl_cond 3 ge "$FS0" 1 'fullscreen-mode 0 read back'
rl_cond 4 ge "$T0" 1 'an L-SSL line opened the 2 s window'
rl_cond 5 le "$MOVES2" $(( ${PB_M:-0} + 2 )) "L-MOVE in the 2 s after SSL: T $MOVES2, B ${PB_M:-none} (+2 allowed; whole session $MOVESALL)"
rl_cond 6 le "$HONEST2" $(( ${PB_H:-0} + 2 )) "L-HON in the 2 s after SSL: T $HONEST2, B ${PB_H:-none} (+2 allowed)"
rl_cond 7 eq "$MATCH" 1 "final anchor $FINAL equals mAppBounds $BOUNDS"
rl_cond 8 eq "$SMM" 0 'L-SMM 0'
rl_cond 9 eq "$CRASH" 0 'S-CRASH 0'
rl_cond 10 le "$ZBT" "${PB_Z:-0}" "L-ZBT: T $ZBT, B ${PB_Z:-none} (#1167)"
rl_cond 11 eq "$CRIT" 0 'phone: P-CRIT, 2 s after the exit left out'
if [ "${PB_GATE:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "PB formed no session, so there is no B count"
elif [ "$BOUNDS" = none ]; then rl_verdict INCONCLUSIVE "no AapProjectionActivity window in the dump"
fi
```

- **PASS** when all eleven hold: T moves the anchor no more than B plus 2 in the 2 s after SSL, and it ends on the
  content area.
- **FAIL** when the anchor flips more than that, or ends off `mAppBounds`, or a surface mismatch or crash appears.
- **INCONCLUSIVE** when PB has no count or the window dump holds no projection window.

Decision: PASS closes last round's P1 flip, and the geometry half of #1211 can merge. FAIL sends the counts and the
L-MOVE lines back to the contributor: completed-layout publishing did not stop the flip.

### R1. A resolution change with MainActivity stopped, on T (the point for the new #1211 finding)

**r1.sh**
```bash
APK=$APK_T
r_cycle
```

**grade-r1.sh**
```bash
[ -s "$EV/R1-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
r_counts RB; RB_ANN=$ANN; RB_NEW=$NEWV
r_counts R1
rl_cond 1 eq "$MD5" 1 'T on D-POCO (md5)'
rl_cond 2 ge "$PRE1" 1 'resolutionId 1 before the change'
rl_cond 3 ge "$IMPORTED" 1 'ACTION_SET_SETTINGS imported one key'
rl_cond 4 ge "$POST2" 1 'resolutionId 2 read back with the process alive'
rl_cond 5 eq "$FOCUS_MAIN" 0 'MainActivity not resumed between Home and the arm'
rl_cond 6 ge "$SECOND" 1 'the phone reconnected (L-SSL after R1-arm)'
rl_cond 7 eq "$NEWV" 1 "announced $ANN is the new 720p (B announced ${RB_ANN:-none}; 480p here is the stale announce)"
rl_cond 8 eq "$CRIT" 0 'phone: P-CRIT, 2 s after the exit left out'
if [ "${FOCUS_MAIN:-0}" -gt 0 ]; then rl_verdict INCONCLUSIVE "MainActivity was resumed, so the case was not set up"
elif [ "${SECOND:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "the phone did not reconnect after the arm"
elif [ -z "$ANN" ]; then rl_verdict INCONCLUSIVE "no L-ANN after R1-arm"
elif [ "${RB_NEW:-0}" -ne 1 ]; then rl_verdict INCONCLUSIVE "the control on B did not announce 720p ($RB_ANN)"
fi
```

- **PASS** when all eight hold: the phone gets 720p, the value in force.
- **FAIL** when T announces 480p while B announced 720p. That confirms the review finding on hardware.
- **INCONCLUSIVE** when `MainActivity` was in front, the phone did not come back, no announce printed, or B did not
  announce 720p.
- Report the first L-RES line after `R1-arm` (`$EV/R1-rescap.txt`): on a stale announce it reads `resolutionId=2` with
  `chosen=_800x480`.

Decision: FAIL confirms the stale announce, and the change request asks the contributor to refresh `geometrySettings`
before the early return or release the owner on `onStop`. PASS means the projection's first layout republishes the
geometry before the announce on this path; the finding then stays a code-review note, not a merge blocker.

### flash. Firmware v1.0.1 on E-1, and no old relay bond on D-POCO

**flash.sh**
```bash
SER_ON=0
rm -f "$EV/flash-prereq.txt"
board_ready || { echo "no E1 path ($E1) or no venv at $VENV: esp32 round 2's ids.env or venv is missing" > "$EV/flash-prereq.txt"; exit 3; }
rl_step "host: download v1.0.1 and check the four hashes"
mkdir -p "$FW"
gh release download v1.0.1 --repo kendrickkim/aa-native-bt-broker-for-openheadunit --pattern '*.bin' --dir "$FW" --clobber > "$EV/flash-download.txt" 2>&1
printf '%s\n' "22d12685b5d131492a54cc1ee491768ac4c3be93b7a10ba05fdabb3636533534  bootloader.bin" \
  "d90b0f480968f1cde746d7d8746261b38ec098b63c7f45b1aa23698cca4a2236  partitions.bin" \
  "f94c5d786a7a8fab06ac5d10e33bf37711a6697636dc037559ea19cc410a17f0  boot_app0.bin" \
  "4544db072f42c473d270cfd3a4d87102d42251f848947132c315b13054ccfae9  firmware.bin" > "$FW/SHA256SUMS"
( cd "$FW" && sha256sum -c SHA256SUMS ) > "$EV/flash-sha.txt" 2>&1
strings -a "$FW/firmware.bin" 2>/dev/null | grep -acF 'projection cleared (' > "$EV/flash-strings.txt"   # system
if [ "$(grep -c ': OK$' "$EV/flash-sha.txt")" -eq 4 ]; then
  rl_step "WAIT cap=180s flash E-1"
  "$VENV/bin/esptool" --chip esp32 --port "$E1" --baud 460800 write-flash 0x1000 "$FW/bootloader.bin" \
    0x8000 "$FW/partitions.bin" 0xe000 "$FW/boot_app0.bin" 0x10000 "$FW/firmware.bin" > "$EV/flash-esptool.txt" 2>&1
fi
rl_step "boot E-1 and wait for BLE"
o=$(rl_offset "$SER")
serial_start --reset
rl_wait_line "$SER" 'advertising OpenHU-BT-Relay-Setup' 30 "$o" > "$EV/flash-boot.txt"   # system
rl_stop ser
rl_step "D-POCO: no OpenHU-BT-Relay bond"
adb -s "$POCO" shell am force-stop "$PKG"
forget_board
```

**grade-flash.sh**
```bash
[ -s "$EV/flash-prereq.txt" ] && { rl_verdict UNTESTABLE "$(cat "$EV/flash-prereq.txt")"; exit 0; }
rl_cond 1 eq "$(grep -c ': OK$' "$EV/flash-sha.txt")" 4 'four bins match the v1.0.1 sha256 values'  # not-literal
rl_cond 2 ge "$(cat "$EV/flash-strings.txt" 2>/dev/null)" 1 'firmware.bin holds projection cleared ('
rl_cond 3 eq "$(grep -acF 'Hash of data verified' "$EV/flash-esptool.txt")" 4 'esptool verified four regions'  # system
rl_cond 4 eq "$(grep -c '^ok' "$EV/flash-boot.txt")" 1 'E-1 booted and advertises'  # not-literal
rl_cond 5 eq "$(grep -c '^board_bond=0' "$EV/flash-board.txt")" 1 'D-POCO holds no OpenHU-BT-Relay bond'  # not-literal
```

- **PASS** when all five hold. Any other verdict skips E1, E2 and E4; E3 and E5 still run.

Decision: PASS puts the firmware under test on the board. FAIL means the release changed or the flash failed, so the
relay runs would test the wrong firmware and stop.

### E1. The renamed screen and its pairing flow

**e1.sh**
```bash
MARK_ON="$DHU $POCO"; F="$EV/E1-hu.txt"
rm -f "$EV/E1-thermal.txt" "$EV/E1-prereq.txt" "$EV/E1-gate.txt"
board_ready || { echo "no E1 path or venv" > "$EV/E1-prereq.txt"; exit 3; }
rl_thermal_gate || { echo HOST_HOT > "$EV/E1-thermal.txt"; exit 2; }
apk_check "$DHU" "$APK_T" E1
setkeys "$DHU" $(native_keys "$POCO_BT") $E_KEYS
relay_write true
adb -s "$POCO" shell am force-stop "$PKG"
adb -s "$POCO" shell input keyevent KEYCODE_HOME
serial_start
rl_cap_start E1-hu "$DHU"; rl_cap_start E1-ph "$POCO"
mark E1-go
rl_step "D-HU: the settings list, searched for esp32 (the row must show on API 34)"
adb -s "$DHU" shell am start -n "$SETT" --es extra_search_query esp32 > "$EV/E1-start.txt" 2>&1
rl_sleep 6
dump_ui "$DHU" E1-list
tap_ui "$DHU" E1-row 'Bluetooth relay (ESP32)' && rl_sleep 4
dump_ui "$DHU" E1-screen
mark E1-scan; o=$(rl_offset "$F"); os=$(rl_offset "$SER")
tap_ui "$DHU" E1-scan 'Look for OpenHU-BT-Relay-Setup' && rl_sleep 8
tap_ui "$DHU" E1-pick 'OpenHU-BT-Relay-Setup'
for i in 1 2 3 4; do   # a system pairing dialog on D-HU, only if one shows within 8 s
  sleep 2; dump_ui "$DHU" E1-dlg
  python3 -I "$SCR/tap_text.py" "$EV/ui-E1-dlg.xml" 'Pair' > /dev/null 2>&1 && { tap_ui "$DHU" E1-hudlg 'Pair'; break; }
done
if rl_wait_line "$F" 'ESP32 relay: opening new-phone pairing window on' 30 "$o" > /dev/null; then
  echo "OPERATOR NOW: on D-POCO, open Bluetooth, Pair new device, pair OpenHU-BT-Relay (PIN 0000 if asked) within 120 s"
  rl_wait_line "$SER" 'phone paired: ' 150 "$os" > "$EV/E1-paired.txt"   # system
fi
rl_sleep 5
mark E1-end
rl_cap_stop E1-hu; rl_cap_stop E1-ph; rl_stop ser
adb -s "$DHU" shell am force-stop "$PKG"
relay_read
rl_bonds "$POCO" > "$EV/E1-poco-bonds.txt"
```

**grade-e1.sh**
```bash
[ -s "$EV/E1-prereq.txt" ] && { rl_verdict UNTESTABLE "$(cat "$EV/E1-prereq.txt")"; exit 0; }
[ -s "$EV/E1-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
F="$EV/E1-hu.txt"
PAIRED=$(rl_cnt "$SER" E1-go E1-end 'phone paired: ')   # system
PFAIL=$(rl_cnt "$SER" E1-go E1-end 'pairing failed: ')   # system
WINDOW=$(rl_cnt "$F" E1-go E1-end 'ESP32 relay: opening new-phone pairing window on')
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/E1-identity.txt")" 1 'T on D-HU (md5)'  # not-literal
rl_cond 2 ge "$(grep -acF 'text="Bluetooth relay (ESP32)"' "$EV/ui-E1-list.xml")" 1 'the relay row is in the API 34 list (E5 control)'  # not-literal
rl_cond 3 ge "$(grep -acF 'text="Look for OpenHU-BT-Relay-Setup"' "$EV/ui-E1-screen.xml")" 1 'the renamed screen opened'  # not-literal
rl_cond 4 ge "$WINDOW" 1 'L-PAIRW'
rl_cond 5 ge "$(rl_cnt "$SER" E1-go E1-end 'pairing mode for ')" 1 'F-PMODE'   # system
rl_cond 6 ge "$PAIRED" 1 'F-PAIRED'
rl_cond 7 ge "$(rl_cnt "$F" E1-go E1-end 'ESP32 relay: phone pairing confirmed')" 1 'L-PAIRC'
rl_cond 8 ge "$(grep -ac 'name="address">' "$EV/E1-relay.txt")" 1 'Esp32Relay.xml holds an address'  # not-literal
rl_cond 9 ge "$(grep -ac 'name="enabled" value="true"' "$EV/E1-relay.txt")" 1 'Esp32Relay.xml enabled'  # not-literal
rl_cond 10 ge "$(grep -acF 'OpenHU-BT-Relay' "$EV/E1-poco-bonds.txt")" 1 'D-POCO lists OpenHU-BT-Relay'  # system
rl_cond 11 le "$(grep -ac ' E1 tap ' "$EV/taps.log")" 5 'injected taps 5 or fewer'  # not-literal
rl_cond 12 eq "$(grep -acF 'Process: com.andrerinas.headunitrevived' "$F")" 0 'S-CRASH 0'  # system
if [ "${WINDOW:-0}" -ge 1 ] && [ "${PAIRED:-0}" -eq 0 ] && [ "${PFAIL:-0}" -eq 0 ]; then
  rl_verdict INCONCLUSIVE "the window opened and the board saw no pairing attempt: the hand step was not done"
fi
```

- **PASS** when all twelve hold: the screen's steps pair the phone with the board.
- **FAIL** when a step of the screen does not appear or the board refuses the pairing (`pairing failed: `).
- **INCONCLUSIVE** when the window opened and the phone never tried to pair (the hand step).
- Report every board line from `rx frame type=06` to `phone paired: `, the saved address and the taps used.

Decision: PASS lets E2 and E4 run on a fresh pairing. FAIL is a change request on the setup flow, and the stop rule
skips E2 and E4.

### E2. Five bring-ups over the board (the point for #1187)

**e2.sh**
```bash
MARK_ON="$DHU $POCO"; F="$EV/E2-hu.txt"
rm -f "$EV/E2-thermal.txt" "$EV/E2-prereq.txt" "$EV/E2-gate.txt"
board_ready || { echo "no E1 path or venv" > "$EV/E2-prereq.txt"; exit 3; }
rl_thermal_gate || { echo HOST_HOT > "$EV/E2-thermal.txt"; exit 2; }
apk_check "$DHU" "$APK_T" E2
setkeys "$DHU" $(native_keys "$POCO_BT") $E_KEYS
relay_read
adb -s "$POCO" shell am force-stop "$PKG"
serial_start
rl_cap_start E2-hu "$DHU"; rl_cap_start E2-ph "$POCO"
for k in 1 2 3 4 5; do
  rl_step "cycle $k"
  adb -s "$POCO" shell input keyevent KEYCODE_HOME
  if session "$DHU" "$F" "E2-c$k-go"; then mark "E2-c$k-ssl"; rl_sleep 30; fi
  mark "E2-c$k-end"
  app_exit "$DHU"
  rl_sleep 15
  mark "E2-c$k-post"
done
rl_cap_stop E2-hu; rl_cap_stop E2-ph; rl_stop ser
```

**grade-e2.sh**
```bash
[ -s "$EV/E2-prereq.txt" ] && { rl_verdict UNTESTABLE "$(cat "$EV/E2-prereq.txt")"; exit 0; }
[ -s "$EV/E2-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
F="$EV/E2-hu.txt"; P="$EV/E2-ph.txt"
sessions=0; hudial=0; boots=0
for k in 1 2 3 4 5; do
  a="E2-c$k-go"; z="E2-c$k-post"
  ssl=$(rl_cnt "$F" "$a" "$z" 'SSL handshake complete')
  bd=$(rl_win "$P" "$a" "$z" | grep -aciF "Creating rfcomm socket for device: $BOARD_MAC")   # phone
  hd=$(rl_win "$P" "$a" "$z" | grep -aciF "Creating rfcomm socket for device: $HU_MAC")   # phone
  bt=$(rl_cnt "$SER" "$a" "$z" 'Classic name OpenHU-BT-Relay, BLE name')   # system
  rl_cond "c$k.1" ge "$ssl" 1 "cycle $k: L-SSL"
  rl_cond "c$k.2" ge "$(rl_cnt "$F" "$a" "$z" 'NativeAA: Bluetooth relay transport is on')" 1 "cycle $k: L-RELAY"
  rl_cond "c$k.3" eq "$(rl_cnt "$F" "$a" "$z" 'ACTIVELY LISTENING on Android Auto UUID')" 0 "cycle $k: L-LIST 0"
  rl_cond "c$k.4" ge "$bd" 1 "cycle $k: the phone dialled the board ($BOARD_MAC)"
  rl_cond "c$k.5" eq "$hd" 0 "cycle $k: the phone did not dial D-HU ($HU_MAC)"
  rl_cond "c$k.6" ge "$(rl_cnt "$F" "$a" "$z" 'ESP32 relay: releasing HFP (')" 0 "cycle $k: report L-HFP"
  [ "${ssl:-0}" -ge 1 ] && sessions=$((sessions + 1))
  hudial=$((hudial + ${hd:-0})); boots=$((boots + ${bt:-0}))
done
rl_cond 6 eq "$sessions" 5 'sessions out of 5'
rl_cond 7 eq "$(grep -c '^md5match=1' "$EV/E2-identity.txt")" 1 'T on D-HU (md5)'  # not-literal
rl_cond 8 eq "$(grep -acF 'Process: com.andrerinas.headunitrevived' "$F")" 0 'S-CRASH 0'  # system
rl_cond 9 eq "$(python3 -I "$SCR/crit_count.py" "$P" E2-c1-go E2-c5-post 'Critical error' E2-c1-end E2-c2-end E2-c3-end E2-c4-end E2-c5-end)" 0 'phone: P-CRIT, 2 s after each exit left out'  # phone
if [ "$boots" -gt 0 ]; then rl_verdict INCONCLUSIVE "the board rebooted inside a cycle ($boots F-BOOT lines)"
elif [ "$hudial" -gt 0 ] && [ "$sessions" -lt 5 ]; then rl_verdict INCONCLUSIVE "D-POCO dialled D-HU: a bond the session should have removed"
fi
```

- **PASS** when every condition holds: 5 sessions of 5, each over the board, with no dial to D-HU.
- **FAIL** when a cycle forms no session while the phone dialled the board, or a cycle used the stock listeners.
- **INCONCLUSIVE** when the board rebooted inside a cycle, or D-POCO dialled D-HU in a cycle that failed.
- **What a PASS looks like if the board did nothing:** conditions 2 to 5 separate it. A session over D-HU's own radio
  shows L-LIST 1 or more and phone dials to `11:46:03:10:33:59` only.
- Report per cycle: seconds from `-go` to L-SSL, L-HFP, and the board's `HFP off (` lines.

Decision: PASS means the relay route works on v1.0.1 with the renamed code, which esp32 round 2 last measured on
v1.0.0. FAIL points at the rename commit `f0b3c9d1` or the firmware change, by which cycle step failed.

### E4. A force-stop during a relay session, then a relaunch with no board reset (the point for review item 6)

**e4.sh**
```bash
MARK_ON="$DHU $POCO"; F="$EV/E4-hu.txt"
rm -f "$EV/E4-thermal.txt" "$EV/E4-prereq.txt" "$EV/E4-gate.txt"
board_ready || { echo "no E1 path or venv" > "$EV/E4-prereq.txt"; exit 3; }
rl_thermal_gate || { echo HOST_HOT > "$EV/E4-thermal.txt"; exit 2; }
apk_check "$DHU" "$APK_T" E4
setkeys "$DHU" $(native_keys "$POCO_BT") $E_KEYS
relay_read
adb -s "$POCO" shell am force-stop "$PKG"
adb -s "$POCO" shell input keyevent KEYCODE_HOME
serial_start
rl_cap_start E4-hu "$DHU"; rl_cap_start E4-ph "$POCO"
if session "$DHU" "$F" E4-go; then
  mark E4-ssl; rl_sleep 20
  mark E4-kill
  adb -s "$DHU" shell am force-stop "$PKG"
  rl_sleep 5
  mark E4-relaunch; o=$(rl_offset "$F")
  adb -s "$DHU" shell am start -n "$MAIN" > /dev/null
  if rl_wait_line "$F" 'SSL handshake complete' 150 "$o" > /dev/null; then echo "second=ok" >> "$EV/E4-gate.txt"; rl_sleep 20
  else echo "second=none" >> "$EV/E4-gate.txt"; fi
fi
mark E4-end
app_exit "$DHU"
rl_sleep 15
rl_cap_stop E4-hu; rl_cap_stop E4-ph; rl_stop ser
```

**grade-e4.sh**
```bash
[ -s "$EV/E4-prereq.txt" ] && { rl_verdict UNTESTABLE "$(cat "$EV/E4-prereq.txt")"; exit 0; }
[ -s "$EV/E4-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
F="$EV/E4-hu.txt"; P="$EV/E4-ph.txt"
grep -q '^session=ok' "$EV/E4-gate.txt" 2>/dev/null || { rl_verdict UNTESTABLE "session gate: no first session in 2 attempts"; exit 0; }  # not-literal
BOOT=$(rl_cnt "$SER" E4-kill E4-end 'Classic name OpenHU-BT-Relay, BLE name')   # system
SECOND=$(rl_cnt "$F" E4-relaunch E4-end 'SSL handshake complete')
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/E4-identity.txt")" 1 'T on D-HU (md5)'  # not-literal
rl_cond 2 ge "$(rl_cnt "$F" E4-go E4-kill 'NativeAA: Bluetooth relay transport is on')" 1 'the first session went over the relay'
rl_cond 3 eq "$BOOT" 0 'F-BOOT 0 from the kill to the end (no board reset)'
rl_cond 4 ge "$SECOND" 1 'L-SSL after E4-relaunch (a new session)'
rl_cond 5 ge "$(rl_win "$P" E4-relaunch E4-end | grep -aciF "Creating rfcomm socket for device: $BOARD_MAC")" 1 'phone: dialled the board after the relaunch'  # phone
rl_cond 6 eq "$(grep -acF 'Process: com.andrerinas.headunitrevived' "$F")" 0 'S-CRASH 0'  # system
rl_cond 7 ge "$(rl_cnt "$SER" E4-kill E4-end 'projection cleared (')" 0 'report: F-CLEAR'   # system
rl_cond 8 ge "$(rl_cnt "$SER" E4-relaunch E4-end 'head unit BLE connected')" 0 'report: F-BLE after the relaunch'   # system
rl_cond 9 ge "$(rl_cnt "$F" E4-relaunch E4-end 'createGroup SUCCESS')" 0 'report: L-GRP after the relaunch'
[ "${BOOT:-0}" -gt 0 ] && rl_verdict INCONCLUSIVE "the board rebooted after the kill, so the run did not test a board that kept its state"
```

- **PASS** when conditions 1 to 6 hold: after a force-stop, the relaunched app forms a session through the same board
  with no reset.
- **FAIL** when no session forms after the relaunch and the board did not reboot.
- **INCONCLUSIVE** when the board rebooted inside the window.
- Report F-CLEAR, F-BLE and L-GRP, and any `reason=` text on a group create after the relaunch.

Decision: PASS closes review item 6 with firmware v1.0.1. FAIL with F-BLE 0 points at the board (it does not take the
new app back); FAIL with F-BLE 1 or more points at the app's relaunch path.

### E3. Board saved and on, board held in reset, a Headunit Server session (the point for review item 4)

**e3.sh**
```bash
MARK_ON="$DHU $POCO"; F="$EV/E3-hu.txt"
rm -f "$EV/E3-thermal.txt" "$EV/E3-prereq.txt" "$EV/E3-gate.txt"
board_ready || { echo "no E1 path or venv" > "$EV/E3-prereq.txt"; exit 3; }
rl_thermal_gate || { echo HOST_HOT > "$EV/E3-thermal.txt"; exit 2; }
apk_check "$DHU" "$APK_T" E3
[ "$(server5277 "$POCO")" -ge 1 ] || { echo "no :5277 listener on D-POCO (Start head unit server is off)" > "$EV/E3-prereq.txt"; exit 3; }
hip=$(waddr "$DHU"); pip=$(waddr "$POCO"); echo "hu=$hip poco=$pip" > "$EV/E3-net.txt"
[ -n "$hip" ] && [ -n "$pip" ] || { echo "D-HU or D-POCO is on no WiFi network (hu=$hip poco=$pip)" > "$EV/E3-prereq.txt"; exit 3; }
setkeys "$DHU" $HUSRV
relay_write true "$BOARD_MAC" OpenHU-BT-Relay-Setup
adb -s "$POCO" shell am force-stop "$PKG"
adb -s "$POCO" shell input keyevent KEYCODE_HOME
rl_step "hold E-1 in reset"
serial_start --hold
rl_cap_start E3-hu "$DHU"; rl_cap_start E3-ph "$POCO"
mark E3-go; o=$(rl_offset "$F")
adb -s "$DHU" shell am start -n "$MAIN" > /dev/null
if rl_wait_line "$F" 'SSL handshake complete' 150 "$o" > /dev/null; then
  echo "session=ok" >> "$EV/E3-gate.txt"
  mark E3-ssl; rl_sleep 30
  mark E3-check; rl_sleep 20
  mark E3-exit
  send_to "$DHU" ACTION_DISCONNECT > /dev/null 2>&1
  rl_sleep 8
else echo "session=none" >> "$EV/E3-gate.txt"; fi
mark E3-end
app_exit "$DHU"
rl_cap_stop E3-hu; rl_cap_stop E3-ph
rl_stop ser
rl_step "release E-1 and wait for BLE"
o=$(rl_offset "$SER")
serial_start --reset
rl_wait_line "$SER" 'advertising OpenHU-BT-Relay-Setup' 30 "$o" > "$EV/E3-back.txt"   # system
rl_stop ser
```

**grade-e3.sh**
```bash
[ -s "$EV/E3-prereq.txt" ] && { rl_verdict UNTESTABLE "$(cat "$EV/E3-prereq.txt")"; exit 0; }
[ -s "$EV/E3-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
F="$EV/E3-hu.txt"; P="$EV/E3-ph.txt"
grep -q '^session=ok' "$EV/E3-gate.txt" 2>/dev/null || { rl_verdict UNTESTABLE "no Headunit Server session in 150 s"; exit 0; }  # not-literal
BOOT=$(rl_cnt "$SER" E3-go E3-end 'Classic name OpenHU-BT-Relay, BLE name')   # system
HELD=$(grep -acF "SERIAL_CAP open $E1 hold" "$SER" 2>/dev/null)   # not-literal
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/E3-identity.txt")" 1 'T on D-HU (md5)'  # not-literal
rl_cond 2 ge "$(grep -ac 'name="wifi-connection-mode" value="1"' "$EV/E3-settings.xml")" 1 'wifi-connection-mode 1 read back'  # not-literal
rl_cond 3 ge "$(grep -ac 'name="enabled" value="true"' "$EV/E3-relay.txt")" 1 'the relay switch is on'  # not-literal
rl_cond 4 ge "$(grep -ac 'name="address">' "$EV/E3-relay.txt")" 1 'a board is saved'  # not-literal
rl_cond 5 eq "$BOOT" 0 'F-BOOT 0 (the board stayed in reset)'
rl_cond 6 ge "$(rl_cnt "$F" E3-check E3-exit 'Throughput over ')" 1 'L-THR from 30 s after SSL to the exit (the session is up)'
rl_cond 7 eq "$(rl_cnt "$F" E3-go E3-end 'PROJECTION_UP not recorded for 10s')" 0 'L-PUW 0'
rl_cond 8 eq "$(rl_cnt "$F" E3-go E3-end 'ESP32 relay: PROJECTION_UP failed for ')" 0 'L-PUF 0'
rl_cond 9 eq "$(rl_cnt "$F" E3-go E3-end 'NativeAA: Bluetooth relay transport is on')" 0 'L-RELAY 0 (not a relay session)'
rl_cond 10 eq "$(grep -acF 'Process: com.andrerinas.headunitrevived' "$F")" 0 'S-CRASH 0'  # system
rl_cond 11 eq "$(python3 -I "$SCR/crit_count.py" "$P" E3-go E3-end 'Critical error' E3-exit)" 0 'phone: P-CRIT, 2 s after the exit left out'  # phone
rl_cond 12 ge "$(rl_cnt "$F" E3-go E3-end 'NetworkDiscovery: Found Headunit Server on ')" 0 'report: L-DISC'
rl_cond 13 ge "$(grep -c '^ok' "$EV/E3-back.txt" 2>/dev/null)" 0 'report: E-1 advertising again after the release (1 = yes)'  # not-literal
if [ "${HELD:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "the hold never opened the serial port"
elif [ "${BOOT:-0}" -gt 0 ]; then rl_verdict INCONCLUSIVE "the board booted during E3: the hold did not keep it off"
fi
```

- **PASS** when all thirteen hold: with a saved, switched-on board that is off, a non-relay session runs on past the
  10 s watch.
- **FAIL** when L-PUW or L-PUF prints, or frames stop before the exit.
- **INCONCLUSIVE** when the hold failed. **UNTESTABLE** when D-POCO's server is off or a unit is on no WiFi.
- **What a PASS looks like if the change did nothing:** on the old head, L-PUW printed about 10 s after SSL and the
  session ended. Condition 6 reads 30 s after SSL, past that point.

Decision: PASS closes review item 4 (`544cf488` holds). FAIL sends L-PUW with its timestamp back to the contributor:
the watch still arms on a session that did not use the board.

### E5. D-SAM (API 19): no relay row, and the relay screen leaves with no crash

**e5.sh**
```bash
MARK_ON="$SAM"; L="$EV/E5-sam.txt"
rm -f "$EV/E5-thermal.txt"
rl_thermal_gate || { echo HOST_HOT > "$EV/E5-thermal.txt"; exit 2; }
DEST=""
A2=$(find "$HOME/Android" "$HOME/.android" /opt -name aapt2 -type f -perm -u+x 2>/dev/null | sort | tail -1)
H=""; [ -n "$A2" ] && H=$("$A2" dump resources "$APK_T" 2>/dev/null | grep -a -m1 -E 'resource 0x[0-9a-f]+ id/esp32RelaySettingsFragment' | grep -o '0x[0-9a-f]*')  # not-literal
[ -n "$H" ] && DEST=$((H))
echo "dest=${DEST:-none}" > "$EV/E5-dest.txt"
rl_step "D-SAM: back up, install T, keys"
mkdir -p "$EV/apk"
adb -s "$SAM" shell am force-stop "$PKG"
[ -s "$EV/settings-backup-sam.xml" ] || adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-backup-sam.xml"
if [ ! -s "$EV/apk/prior-sam.apk" ]; then
  dev=$(adb -s "$SAM" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -1)
  [ -n "$dev" ] && adb -s "$SAM" pull "$dev" "$EV/apk/prior-sam.apk" > /dev/null 2>&1
fi
install_apk "$SAM" "$APK_T" E5
setkeys "$SAM" $COMMON scalar:int:wifi-connection-mode:3 set:connection-modes:wifi scalar:boolean:native-poke-all-paired:false del:native-poke-bt-macs
send_to "$SAM" ACTION_QUERY_STATE > "$EV/E5-query.txt" 2>&1
adb -s "$SAM" shell input keyevent 224; adb -s "$SAM" shell input keyevent 82
rl_cap_start E5-sam "$SAM"
mark E5-go
adb -s "$SAM" shell run-as "$PKG" am start --user 0 -n "$SETT" --es extra_search_query esp32 > "$EV/E5-start.txt" 2>&1
rl_sleep 10
dump_ui "$SAM" E5-list
adb -s "$SAM" shell am force-stop "$PKG"
mark E5-deep
if [ -n "$DEST" ]; then
  adb -s "$SAM" shell run-as "$PKG" am start --user 0 -n "$SETT" --ei extra_destination "$DEST" > "$EV/E5-deep.txt" 2>&1
  rl_sleep 10
  adb -s "$SAM" shell dumpsys activity activities | grep -a -m2 -E 'mResumedActivity|mFocusedActivity' > "$EV/E5-activity.txt"
fi
rl_sleep 3
mark E5-end
rl_cap_stop E5-sam
adb -s "$SAM" shell am force-stop "$PKG"
```

**grade-e5.sh**
```bash
[ -s "$EV/E5-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
L="$EV/E5-sam.txt"
SEARCH=$(grep -aciF 'text="esp32"' "$EV/ui-E5-list.xml" 2>/dev/null)   # not-literal
CONTROL=$(grep -acF 'text="Bluetooth relay (ESP32)"' "$EV/ui-E1-list.xml" 2>/dev/null)   # not-literal
DEST=$(sed -n 's/^dest=//p' "$EV/E5-dest.txt")
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/E5-identity.txt")" 1 'T on D-SAM (md5)'  # not-literal
rl_cond 2 ge "$(grep -c "$T_SHA" "$EV/E5-query.txt")" 1 "commit $T_SHA"
rl_cond 3 ge "$(grep -ac 'name="wifi-connection-mode" value="3"' "$EV/E5-settings.xml")" 1 'wifi-connection-mode 3 read back (the row is built only in Native mode)'  # not-literal
rl_cond 4 ge "$SEARCH" 1 'the list was searched for esp32'
rl_cond 5 eq "$(grep -acF 'text="Bluetooth relay (ESP32)"' "$EV/ui-E5-list.xml" 2>/dev/null)" 0 'no relay row on API 19'  # not-literal
rl_cond 6 eq "$(rl_cnt "$L" E5-go E5-end 'Process: com.andrerinas.headunitrevived')" 0 'S-CRASH 0 from E5-go to E5-end'  # system
if [ "$DEST" != none ]; then
  rl_cond 7 ge "$(grep -ac 'SettingsActivity' "$EV/E5-activity.txt" 2>/dev/null)" 1 'the relay screen left to the settings list (the onDestroyView guard)'  # not-literal
fi
if [ "${SEARCH:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "the search text did not apply, so the list was not filtered"
elif [ -n "$CONTROL" ] && [ "$CONTROL" -eq 0 ]; then rl_verdict INCONCLUSIVE "the search did not find the row on D-HU either (E1 control)"
elif [ "$(grep -aci 'error' "$EV/E5-start.txt" 2>/dev/null)" -gt 0 ]; then rl_verdict INCONCLUSIVE "run-as am start printed an error (E5-start.txt)"
fi
```

- **PASS** when every condition holds: the searched list has no relay row, and the relay screen, reached by its nav id,
  leaves at once with no crash.
- **FAIL** when the row shows on API 19, or a `FATAL EXCEPTION` names the app (quote its first 20 lines).
- **INCONCLUSIVE** when the search did not apply, or the E1 control did not find the row on D-HU.
- Condition 7 runs only when `aapt2` gave the nav id; `dest=none` in `$EV/E5-dest.txt` says it did not.

Decision: PASS closes esp32 round 3's S crash and the API 23 row rule. FAIL with a stack in `onDestroyView` points at
`3c20980a`; a visible row points at the `SettingsFragment` SDK gate.

### cleanup. Units back to a known state (always runs)

**cleanup.sh**
```bash
fail=0
rl_step "E-1: reset and wait for BLE"
if board_ready; then
  o=$(rl_offset "$SER"); serial_start --reset
  rl_wait_line "$SER" 'advertising OpenHU-BT-Relay-Setup' 30 "$o" > "$EV/cleanup-ble.txt" || fail=1   # system
  rl_stop ser
fi
rl_step "D-HU: exit, remove the relay file, install B, restore settings, Bluetooth on"
app_exit "$DHU"
adb -s "$DHU" shell rm -f "$SP/Esp32Relay.xml"
install_apk "$DHU" "$APK_B" cleanup-hu
restore_hu || fail=1
rl_radio "$DHU" bt on || fail=1
rl_step "D-POCO: forget OpenHU-BT-Relay, install B, restore settings"
app_exit "$POCO"
forget_board
install_apk "$POCO" "$APK_B" cleanup-poco
restore_runas "$POCO" poco || fail=1
adb -s "$POCO" shell svc power stayon false
adb -s "$MOTO" shell svc power stayon false
rl_step "D-SAM: the APK and settings E5 found"
if [ -s "$EV/apk/prior-sam.apk" ]; then
  adb -s "$SAM" shell am force-stop "$PKG"
  adb -s "$SAM" install -r -d "$EV/apk/prior-sam.apk" > "$EV/cleanup-sam-install.txt" 2>&1 || fail=1
fi
[ -s "$EV/settings-backup-sam.xml" ] && { restore_runas "$SAM" sam || fail=1; }
echo "fail=$fail" > "$EV/cleanup-result.txt"
exit "$fail"
```

**grade-cleanup.sh**
```bash
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/cleanup-hu-identity.txt")" 1 'B on D-HU'  # not-literal
rl_cond 2 eq "$(grep -c '^md5match=1' "$EV/cleanup-poco-identity.txt")" 1 'B on D-POCO'  # not-literal
rl_cond 3 eq "$(grep -c . "$EV/cleanup-hu-diff.txt" 2>/dev/null)" 0 'D-HU settings equal the backup'  # not-literal
rl_cond 4 eq "$(grep -c . "$EV/cleanup-poco-diff.txt" 2>/dev/null)" 0 'D-POCO settings equal the backup'  # not-literal
rl_cond 5 eq "$(grep -c '^board_bond=0' "$EV/cleanup-board.txt")" 1 'D-POCO holds no OpenHU-BT-Relay bond'  # not-literal
```

- **PASS** when all five hold. A FAIL leaves a note for the next round and changes no other verdict.
- D-HU keeps its LE bond to `OpenHU-BT-Relay-Setup`. E-1 keeps v1.0.1 and ends the round advertising. List both in
  the results.

Decision: PASS leaves the rig as the next round expects. FAIL names the unit to restore by hand before the next round.

## 7. Scripts

**lib.sh**
```bash
# Serials, paths, verbs, settings and the session cycles for pr-train-20261031 round 1. Sourced before every script and grade.
source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh
RIG_DATA=${RIG_DATA:-/home/oscar/Coding/StudioProjects/rig-data}
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SETT=$PKG/com.andrerinas.openheadunit.main.SettingsActivity
SP=/data/data/$PKG/shared_prefs
DHU=$D_HU; POCO=$D_POCO; MOTO=$D_MOTO; SAM=$D_SAM
SCR=$EV/scripts; APK_T=$EV/apk/T.apk; APK_B=$EV/apk/B.apk; APK=$APK_T
T_SHA=8e0452ddc; B_SHA=c2c0e95aa
HU_MAC=11:46:03:10:33:59; BOARD_MAC=78:1C:3C:2B:AC:EE
R2=$RIG_DATA/rounds/native-bt-broker-esp32-round2; VENV=$R2/venv
E1=$( [ -f "$R2/ids.env" ] && . "$R2/ids.env" > /dev/null 2>&1; echo "${E1:-}" )
SER=$EV/serial-E1.txt; FW=$EV/fw; SER_ON=0; MARK_ON=""
MOTO_BT=$(sed -n 's/.*moto=\([0-9A-F:]*\).*/\1/p' "$EV/macs.txt" 2>/dev/null); MOTO_BT=${MOTO_BT:-A0:46:5A:97:E4:95}
POCO_BT=$(sed -n 's/.*poco=\([0-9A-F:]*\).*/\1/p' "$EV/macs.txt" 2>/dev/null); POCO_BT=${POCO_BT:-DC:B7:2E:5E:4E:59}

COMMON="scalar:int:log-level:2 scalar:int:onboarding-version:2 scalar:boolean:allow-external-configuration:true
 scalar:boolean:kill-on-disconnect:false scalar:boolean:enable-floating-button:false scalar:boolean:narrow-band-profile-cap:false
 scalar:boolean:external-bt-zbt-transport:false scalar:int:native-driver-selection-mode:0 del:video-profile-starvation-cap"
HUSRV="$COMMON scalar:int:wifi-connection-mode:1 set:connection-modes:wifi"
E_KEYS="scalar:int:native-aa-wake-damage-verdict:0 scalar:int:native-aa-wake-armings-without-session:0"
P_KEYS="scalar:int:fullscreen-mode:0 scalar:int:resolutionId:1"
native_keys() {   # native_keys MAC: Native AA that wakes only MAC
  echo "$COMMON scalar:int:wifi-connection-mode:3 set:connection-modes:wifi scalar:int:native-ap-transport:0" \
    "scalar:boolean:native-poke-all-paired:false set:native-poke-bt-macs:$1 scalar:string:native-preferred-device-mac:$1" \
    "scalar:string:last-connected-native-mac:$1 del:native-mac"
}

send_to() { local s=$1 a=$2; shift 2; adb -s "$s" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
mark() {   # mark LABEL: the same marker on every unit in MARK_ON, and in the board's serial file while it records
  local s
  for s in $MARK_ON; do adb -s "$s" shell log -t AutomationMarker "$1"; done
  [ "$SER_ON" = 1 ] && echo "$(date '+%m-%d %H:%M:%S.%3N') AutomationMarker: $1" >> "$SER"
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> "$EV/marks.txt"
}
btaddr() {   # btaddr SERIAL FALLBACK: the unit's Bluetooth address in upper case
  local a; a=$(adb -s "$1" shell settings get secure bluetooth_address 2>/dev/null | tr -d '\r' | tr 'a-f' 'A-F')
  case "$a" in ??:??:??:??:??:??) echo "$a" ;; *) echo "$2" ;; esac
}
running() { local p; p=$(adb -s "$1" shell pidof "$PKG" 2>/dev/null | tr -d '\r'); case "$p" in *[0-9]*) return 0 ;; esac; return 1; }
app_exit() {   # app_exit SERIAL: Disconnect and headunit://exit only while the app runs, then force-stop
  if running "$1"; then
    send_to "$1" ACTION_DISCONNECT > /dev/null 2>&1; sleep 5
    adb -s "$1" shell am start -a android.intent.action.VIEW -d headunit://exit > /dev/null; sleep 5
  fi
  adb -s "$1" shell am force-stop "$PKG"
}
apk_check() {   # apk_check SERIAL APK TAG: md5 of the installed APK against the built one, to $EV/TAG-identity.txt
  local s=$1 apk=$2 tag=$3 dev a b
  dev=$(adb -s "$s" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -1)
  rm -f "$EV/$tag-pulled.apk"
  [ -n "$dev" ] && adb -s "$s" pull "$dev" "$EV/$tag-pulled.apk" > /dev/null 2>&1
  a=$(md5sum < "$apk" 2>/dev/null | cut -c1-32); b=$(md5sum < "$EV/$tag-pulled.apk" 2>/dev/null | cut -c1-32)
  if [ -n "$a" ] && [ "$a" = "$b" ]; then echo "md5match=1 $a"; else echo "md5match=0 built=$a installed=$b"; fi > "$EV/$tag-identity.txt"
  rm -f "$EV/$tag-pulled.apk"
}
install_apk() { adb -s "$1" install -r -d "$2" > "$EV/$3-install.txt" 2>&1; apk_check "$1" "$2" "$3"; }
readkeys() {   # readkeys SERIAL TAG: settings.xml to $EV/TAG-settings.xml (root on D-HU, run-as elsewhere)
  if [ "$1" = "$DHU" ]; then adb -s "$1" shell cat "$SP/settings.xml"; else adb -s "$1" shell run-as "$PKG" cat shared_prefs/settings.xml; fi | tr -d '\r' > "$EV/$2-settings.xml"
}
setkeys() {   # setkeys SERIAL KEY...: app stopped, every key in one pass, then the file read back to $EV/$RUN-settings.xml
  local s=$1; shift
  adb -s "$s" shell am force-stop "$PKG"
  if [ "$s" = "$DHU" ]; then python3 -I "$RIG_TOOLKIT/set_hu_settings_host.py" "$s" "$@"
  else python3 -I "$RIG_TOOLKIT/set_prefs_runas_host.py" "$s" "$@"; fi >> "$EV/$RUN-keys.log" 2>&1
  readkeys "$s" "$RUN"
}
restore_hu() {   # D-HU settings back as root; cat keeps the file's owner and mode
  [ -s "$EV/settings-backup-hu.xml" ] || return 1
  adb -s "$DHU" push "$EV/settings-backup-hu.xml" /data/local/tmp/settings-restore.xml > /dev/null
  adb -s "$DHU" shell "cat /data/local/tmp/settings-restore.xml > $SP/settings.xml; rm -f /data/local/tmp/settings-restore.xml"
  readkeys "$DHU" cleanup-hu
  tr -d '\r' < "$EV/settings-backup-hu.xml" | diff - "$EV/cleanup-hu-settings.xml" > "$EV/cleanup-hu-diff.txt"
}
restore_runas() {   # restore_runas SERIAL TAG: settings back through the toolkit's restore_settings.sh (run-as)
  local s=$1 u=$2
  [ -s "$EV/settings-backup-$u.xml" ] || return 1
  adb -s "$s" shell am force-stop "$PKG"
  adb -s "$s" push "$EV/settings-backup-$u.xml" /data/local/tmp/settings-restore.xml > /dev/null
  adb -s "$s" push "$RIG_TOOLKIT/restore_settings.sh" /data/local/tmp/restore_settings.sh > /dev/null
  adb -s "$s" shell run-as "$PKG" sh /data/local/tmp/restore_settings.sh
  adb -s "$s" shell rm -f /data/local/tmp/settings-restore.xml /data/local/tmp/restore_settings.sh
  readkeys "$s" "cleanup-$u"
  tr -d '\r' < "$EV/settings-backup-$u.xml" | diff - "$EV/cleanup-$u-settings.xml" > "$EV/cleanup-$u-diff.txt"
}
session() {   # session HU CAPTURE MARK: launch from a stopped app and wait for SSL, 2 attempts; result in $EV/$RUN-gate.txt
  local hu=$1 f=$2 tag=$3 att o
  for att in 1 2; do
    adb -s "$hu" shell am force-stop "$PKG"
    mark "$tag"; o=$(rl_offset "$f")
    adb -s "$hu" shell am start -n "$MAIN" > /dev/null
    if rl_wait_line "$f" 'SSL handshake complete' 150 "$o" > /dev/null; then echo "session=ok attempt=$att" >> "$EV/$RUN-gate.txt"; return 0; fi
    echo "session=none attempt=$att" >> "$EV/$RUN-gate.txt"; app_exit "$hu"
  done
  return 1
}
dump_ui() {   # dump_ui SERIAL LABEL: a uiautomator dump to $EV/ui-LABEL.xml
  adb -s "$1" shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb -s "$1" pull /sdcard/ui.xml "$EV/ui-$2.xml" > /dev/null 2>&1
  adb -s "$1" shell rm -f /sdcard/ui.xml
}
tap_ui() {   # tap_ui SERIAL LABEL TEXT: tap the one node whose text is TEXT (any case); 2 when it does not resolve
  local s=$1 label=$2 text=$3 xy="" i
  for i in 1 2 3; do
    dump_ui "$s" "$label"
    xy=$(python3 -I "$SCR/tap_text.py" "$EV/ui-$label.xml" "$text" 2> /dev/null) && break
    xy=""; sleep 2
  done
  [ -n "$xy" ] || { echo "$(date +%T) $RUN TAP_UNRESOLVED $s $label" >> "$EV/taps.log"; return 2; }
  echo "$(date +%T) $RUN tap $s $label $xy" >> "$EV/taps.log"
  adb -s "$s" shell input tap $xy
}
forget_board() {   # remove an OpenHU-BT-Relay bond on D-POCO (3 taps at most); result in $EV/$RUN-board.txt
  local xy n i
  adb -s "$POCO" shell am start -a android.settings.BLUETOOTH_SETTINGS > /dev/null; sleep 3
  dump_ui "$POCO" "$RUN-btlist"
  if xy=$(python3 -I "$SCR/tap_gear.py" "$EV/ui-$RUN-btlist.xml" OpenHU-BT-Relay OpenHU-NativeBTBroker 2> /dev/null); then
    echo "$(date +%T) $RUN tap $POCO board-gear $xy" >> "$EV/taps.log"; adb -s "$POCO" shell input tap $xy; sleep 2
    tap_ui "$POCO" board-forget 'Forget' && sleep 2 && tap_ui "$POCO" board-confirm 'Forget device'; sleep 2
  fi
  adb -s "$POCO" shell input keyevent KEYCODE_HOME
  n=$(rl_bonds "$POCO" | grep -aci 'OpenHU-BT-Relay')   # system
  if [ "${n:-0}" -gt 0 ]; then
    echo "OPERATOR NOW: on D-POCO, forget OpenHU-BT-Relay in Bluetooth settings (the scripted forget did not take)"
    for i in $(seq 1 40); do rl_sleep 15; n=$(rl_bonds "$POCO" | grep -aci 'OpenHU-BT-Relay'); [ "${n:-0}" -eq 0 ] && break; done   # system
  fi
  echo "board_bond=${n:-0}" > "$EV/$RUN-board.txt"
}
relay_read() { adb -s "$DHU" shell cat "$SP/Esp32Relay.xml" 2> /dev/null | tr -d '\r' > "$EV/$RUN-relay.txt"; }
relay_write() {   # relay_write true|false [ADDRESS NAME]: the whole Esp32Relay.xml as root, app stopped, then read back
  local owner
  owner=$(adb -s "$DHU" shell stat -c %U "/data/data/$PKG" | tr -d '\r')
  { echo "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"; echo "<map>"
    echo "    <boolean name=\"enabled\" value=\"$1\" />"
    if [ -n "${2:-}" ]; then echo "    <string name=\"address\">$2</string>"; echo "    <string name=\"name\">$3</string>"; fi
    echo "</map>"; } > "$EV/Esp32Relay-w.xml"
  adb -s "$DHU" push "$EV/Esp32Relay-w.xml" /data/local/tmp/Esp32Relay.xml > /dev/null
  adb -s "$DHU" shell "cp /data/local/tmp/Esp32Relay.xml $SP/Esp32Relay.xml && chown $owner:$owner $SP/Esp32Relay.xml && chmod 660 $SP/Esp32Relay.xml; rm -f /data/local/tmp/Esp32Relay.xml"
  relay_read
}
board_ready() { [ -n "$E1" ] && [ -x "$VENV/bin/python" ]; }
serial_start() {   # serial_start [--reset|--hold]: the board's serial capture, appended to $SER, tracked as "ser"
  rl_bg ser "$VENV/bin/python" -I -u "$SCR/serial_cap.py" "$E1" "$SER" "$@"
  sleep 2; SER_ON=1
}
waddr() {   # waddr SERIAL: the unit's wlan0 IPv4 address
  adb -s "$1" shell ip -f inet addr show wlan0 2> /dev/null | tr -d '\r' | grep -aoE 'inet ([0-9]{1,3}\.){3}[0-9]{1,3}' | head -n 1 | cut -d' ' -f2
}
server5277() { adb -s "$1" shell cat /proc/net/tcp6 /proc/net/tcp 2> /dev/null | grep -aci ':149D'; }   # Android Auto's head unit server listens

p_cycle() {   # one P run: D-POCO as head unit with D-MOTO, a session, the window dump, then a Disconnect that leaves MainActivity in front. Needs APK.
  local f="$EV/$RUN-hu.txt"
  MARK_ON="$POCO $MOTO"
  rm -f "$EV/$RUN-thermal.txt" "$EV/$RUN-gate.txt"
  rl_thermal_gate || { echo HOST_HOT > "$EV/$RUN-thermal.txt"; return 2; }
  rl_hold_off "$DHU" bt || echo "dhu_bt_not_off" >> "$EV/$RUN-gate.txt"
  adb -s "$POCO" shell am force-stop "$PKG"
  install_apk "$POCO" "$APK" "$RUN"
  send_to "$POCO" ACTION_QUERY_STATE > "$EV/$RUN-query.txt" 2>&1
  setkeys "$POCO" $(native_keys "$MOTO_BT") $P_KEYS
  adb -s "$POCO" shell svc power stayon true; adb -s "$MOTO" shell svc power stayon true
  rl_cap_start "$RUN-hu" "$POCO"; rl_cap_start "$RUN-ph" "$MOTO"
  if session "$POCO" "$f" "$RUN-start"; then
    rl_wait_line "$f" 'Throughput over ' 30 > /dev/null
    rl_sleep 20
    adb -s "$POCO" shell dumpsys window windows | tr -d '\r' > "$EV/$RUN-window.txt"
    mark "$RUN-exit"
    send_to "$POCO" ACTION_DISCONNECT > "$EV/$RUN-disc.txt" 2>&1
    rl_sleep 8
  fi
  mark "$RUN-end"
  rl_cap_stop "$RUN-hu"; rl_cap_stop "$RUN-ph"
  rl_release "$DHU" bt
}
p_counts() {   # p_counts RUN: the counts every P grade reads
  local r=$1 f="$EV/$1-hu.txt"
  T0=0; MOVES2=""; HONEST2=""; MOVESALL=""; HONESTALL=""; FINAL=""
  eval "$(python3 -I "$SCR/geom.py" "$f" "$r-start" "$r-end" 2)"
  BOUNDS=$(python3 -I "$SCR/appbounds.py" "$EV/$r-window.txt")
  MATCH=0; [ -n "$FINAL" ] && [ "$FINAL" = "$BOUNDS" ] && MATCH=1
  SMM=$(rl_cnt "$f" "$r-start" "$r-end" 'Surface mismatch detected!')
  ZBT=$(rl_cnt "$f" "$r-start" "$r-end" '[ZBT] nothing is listening on ')
  CRASH=$(grep -acF 'Process: com.andrerinas.headunitrevived' "$f" 2> /dev/null)   # system
  CRIT=$(python3 -I "$SCR/crit_count.py" "$EV/$r-ph.txt" "$r-start" "$r-end" 'Critical error' "$r-exit")   # phone
  FS0=$(grep -ac 'name="fullscreen-mode" value="0"' "$EV/$r-settings.xml" 2> /dev/null)   # not-literal
  MD5=$(grep -c '^md5match=1' "$EV/$r-identity.txt" 2> /dev/null)   # not-literal
  GATE=$(grep -c '^session=ok' "$EV/$r-gate.txt" 2> /dev/null)   # not-literal
}

r_cycle() {   # one R run: from P's end, Home, resolutionId 1 to 2 by verb with the process alive, then the phone reconnects. Needs APK.
  local f="$EV/$RUN-hu.txt" o
  MARK_ON="$POCO $MOTO"
  rm -f "$EV/$RUN-thermal.txt" "$EV/$RUN-gate.txt"
  rl_thermal_gate || { echo HOST_HOT > "$EV/$RUN-thermal.txt"; return 2; }
  rl_hold_off "$DHU" bt || echo "dhu_bt_not_off" >> "$EV/$RUN-gate.txt"
  apk_check "$POCO" "$APK" "$RUN"
  rl_cap_start "$RUN-hu" "$POCO"; rl_cap_start "$RUN-ph" "$MOTO"
  if running "$POCO"; then echo "chained=1" >> "$EV/$RUN-gate.txt"
  else
    echo "chained=0" >> "$EV/$RUN-gate.txt"
    setkeys "$POCO" $(native_keys "$MOTO_BT") $P_KEYS
    if session "$POCO" "$f" "$RUN-pre"; then rl_sleep 15; send_to "$POCO" ACTION_DISCONNECT > /dev/null 2>&1; rl_sleep 8; fi
  fi
  readkeys "$POCO" "$RUN-pre"
  mark "$RUN-start"
  adb -s "$POCO" shell input keyevent KEYCODE_HOME
  rl_sleep 4
  adb -s "$POCO" shell dumpsys activity activities | grep -a -m2 -E 'topResumedActivity|mResumedActivity' | tr -d '\r' > "$EV/$RUN-focus.txt"
  mark "$RUN-set"
  rl_broadcast "$POCO" ACTION_SET_SETTINGS --es json "'{\"format\":\"open-headunit-settings\",\"version\":1,\"settings\":{\"resolutionId\":2}}'" > "$EV/$RUN-set.txt" 2>&1
  readkeys "$POCO" "$RUN-post"
  rl_sleep 6
  adb -s "$POCO" shell dumpsys activity activities | grep -a -m2 -E 'topResumedActivity|mResumedActivity' | tr -d '\r' >> "$EV/$RUN-focus.txt"
  mark "$RUN-arm"; o=$(rl_offset "$f")
  send_to "$POCO" ACTION_START_WIRELESS_SCAN > "$EV/$RUN-arm.txt" 2>&1
  if rl_wait_line "$f" 'SSL handshake complete' 150 "$o" > /dev/null; then
    echo "second=ok" >> "$EV/$RUN-gate.txt"
    rl_wait_line "$f" 'NegotiatedResolution is: ' 40 "$o" > /dev/null
    rl_sleep 15
  else echo "second=none" >> "$EV/$RUN-gate.txt"; fi
  mark "$RUN-exit"
  send_to "$POCO" ACTION_DISCONNECT > /dev/null 2>&1
  rl_sleep 5
  mark "$RUN-end"
  rl_win "$f" "$RUN-arm" "$RUN-end" | grep -aF '[RES_CAP] resolutionId=' | head -1 > "$EV/$RUN-rescap.txt"
  app_exit "$POCO"
  rl_cap_stop "$RUN-hu"; rl_cap_stop "$RUN-ph"
  rl_release "$DHU" bt
}
r_counts() {   # r_counts RUN: the counts every R grade reads
  local r=$1 f="$EV/$1-hu.txt"
  ANN=$(rl_win "$f" "$r-arm" "$r-end" | grep -ao 'NegotiatedResolution is: [0-9]*x[0-9]*' | head -1 | sed 's/.*: //')
  case "$ANN" in 1280x720|720x1280) NEWV=1 ;; *) NEWV=0 ;; esac
  PRE1=$(grep -ac 'name="resolutionId" value="1"' "$EV/$r-pre-settings.xml" 2> /dev/null)   # not-literal
  POST2=$(grep -ac 'name="resolutionId" value="2"' "$EV/$r-post-settings.xml" 2> /dev/null)   # not-literal
  IMPORTED=$(grep -ac '"imported":1' "$EV/$r-set.txt" 2> /dev/null)   # not-literal
  FOCUS_MAIN=$(grep -ac 'MainActivity' "$EV/$r-focus.txt" 2> /dev/null)   # not-literal
  SECOND=$(grep -c '^second=ok' "$EV/$r-gate.txt" 2> /dev/null)   # not-literal
  MD5=$(grep -c '^md5match=1' "$EV/$r-identity.txt" 2> /dev/null)   # not-literal
  CRIT=$(python3 -I "$SCR/crit_count.py" "$EV/$r-ph.txt" "$r-start" "$r-end" 'Critical error' "$r-exit")   # phone
}
```

**geom.py**
```python
# Geometry lines between markers FROM and TO of a threadtime capture. T0 is the first "SSL handshake complete";
# MOVES2 and HONEST2 count anchor moves and full passes in [T0, T0 + S]; FINAL is the last anchor printed.
# usage: geom.py CAPTURE FROM TO S. Prints shell assignments.
import re, sys
from datetime import datetime, timedelta
cap, a, b, secs = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4])
MOVE, HONEST = "HeadUnitScreenConfig: anchor ", "Honest Init | Mode: "
ANCHOR = re.compile(r"(?:Anchor: |anchor \d+x\d+ -> )(\d+)x(\d+)")
def ts(line):
    m = re.match(r"(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d{3})", line)
    return datetime.strptime("2026-" + m.group(1) + " " + m.group(2), "%Y-%m-%d %H:%M:%S.%f") if m else None
try:
    lines = open(cap, errors="replace").read().splitlines()
except OSError:
    lines = []
win, on = [], False
for l in lines:
    if not on and ("AutomationMarker: " + a) in l:
        on = True
    if on:
        win.append(l)
        if ("AutomationMarker: " + b) in l:
            break
t0, m2, h2, ma, ha, final = None, 0, 0, 0, 0, ""
for l in win:
    t = ts(l)
    if t0 is None and "SSL handshake complete" in l and t:
        t0 = t
    near = t0 is not None and t is not None and t0 <= t <= t0 + timedelta(seconds=secs)
    if MOVE in l:
        ma += 1; m2 += near
    if HONEST in l:
        ha += 1; h2 += near
    m = ANCHOR.search(l)
    if m:
        final = m.group(1) + "x" + m.group(2)
print(f"T0={1 if t0 else 0}\nMOVES2={m2}\nHONEST2={h2}\nMOVESALL={ma}\nHONESTALL={ha}\nFINAL={final}")
```

**appbounds.py**
```python
# Print WxH of mAppBounds for the AapProjectionActivity window in a "dumpsys window windows" file, or "none".
import re, sys
HEAD = re.compile(r"^\s*Window #\d+ ")
RECT = re.compile(r"mAppBounds=Rect\((-?\d+), (-?\d+) - (-?\d+), (-?\d+)\)")
try:
    lines = open(sys.argv[1], errors="replace").read().splitlines()
except OSError:
    lines = []
inside = False
for l in lines:
    if HEAD.match(l):
        inside = "AapProjectionActivity" in l
        continue
    if inside:
        m = RECT.search(l)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print(f"{x2 - x1}x{y2 - y1}")
            sys.exit(0)
print("none")
```

**crit_count.py**
```python
# Count lines holding NEEDLE between markers FROM and TO, leaving out the 2 s after each EXCL marker.
# usage: crit_count.py CAPTURE FROM TO NEEDLE [EXCL ...]. Prints nothing (exit 1) when the window is missing.
import re, sys
from datetime import datetime, timedelta
cap, a, b, needle, excl = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5:]
def ts(line):
    m = re.match(r"(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d{3})", line)
    return datetime.strptime("2026-" + m.group(1) + " " + m.group(2), "%Y-%m-%d %H:%M:%S.%f") if m else None
try:
    lines = open(cap, errors="replace").read().splitlines()
except OSError:
    sys.exit(1)
win, on = [], False
for l in lines:
    if not on and ("AutomationMarker: " + a) in l:
        on = True
    if on:
        win.append(l)
        if ("AutomationMarker: " + b) in l:
            break
if not win:
    sys.exit(1)
cuts = [t for t in (ts(l) for l in win if any(("AutomationMarker: " + e) in l for e in excl)) if t]
n = 0
for l in win:
    if needle in l:
        t = ts(l)
        if t and any(c <= t <= c + timedelta(seconds=2) for c in cuts):
            continue
        n += 1
print(n)
```

**serial_cap.py**
```python
# Read the ESP32 serial port and stamp each line with the host clock.
# --reset pulses EN for a normal boot. --hold keeps EN low (RTS high), so the board stays in reset, and still reads.
import sys, time, datetime, serial
port, out = sys.argv[1], sys.argv[2]
hold = "--hold" in sys.argv
s = serial.Serial(); s.port = port; s.baudrate = 115200; s.timeout = 1; s.dtr = False; s.rts = hold
s.open()
if "--reset" in sys.argv and not hold:
    s.rts = True; time.sleep(0.1); s.rts = False
def stamp():
    return datetime.datetime.now().strftime("%m-%d %H:%M:%S.%f")[:-3]
buf = b""
with open(out, "a", buffering=1, errors="replace") as f:
    f.write(f"{stamp()} SERIAL_CAP open {port} {'hold' if hold else 'run'}\n")
    while True:
        b = s.read(256)
        if not b:
            continue
        buf += b
        while b"\n" in buf:
            line, buf = buf.split(b"\n", 1)
            f.write(stamp() + " " + line.decode("ascii", "replace").rstrip("\r") + "\n")
```

**tap_text.py**
```python
# Print the centre of the one node whose text is argv[2] (any case) in a uiautomator dump; exit 2 unless exactly one.
import re, sys, xml.etree.ElementTree as E
try:
    root = E.parse(sys.argv[1]).getroot()
except Exception as e:
    print(f"tap_text: unreadable dump: {e}", file=sys.stderr); sys.exit(2)
want = sys.argv[2].casefold()
hits = [n for n in root.iter("node") if (n.get("text") or "").casefold() == want]
if len(hits) != 1:
    print(f"tap_text: {len(hits)} matches for {sys.argv[2]!r}", file=sys.stderr); sys.exit(2)
x1, y1, x2, y2 = map(int, re.findall(r"\d+", hits[0].get("bounds")))
print((x1 + x2) // 2, (y1 + y2) // 2)
```

**tap_gear.py**
```python
# Print the centre of the gear (resource id ending settings_button) in the row holding a node whose text is one of argv[2:].
import re, sys, xml.etree.ElementTree as E
try:
    root = E.parse(sys.argv[1]).getroot()
except Exception as e:
    print(f"tap_gear: unreadable dump: {e}", file=sys.stderr); sys.exit(2)
parent = {c: p for p in root.iter() for c in p}
hits = [n for n in root.iter("node") if n.get("text") in sys.argv[2:]]
if len(hits) != 1:
    print(f"tap_gear: {len(hits)} rows match", file=sys.stderr); sys.exit(2)
row = hits[0]
for _ in range(4):
    row = parent.get(row)
    if row is None:
        break
    gears = [n for n in row.iter("node") if (n.get("resource-id") or "").endswith("settings_button")]
    if len(gears) == 1:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", gears[0].get("bounds"))); print((x1 + x2) // 2, (y1 + y2) // 2); sys.exit(0)
print("tap_gear: no single gear in the row", file=sys.stderr); sys.exit(2)
```

## 8. Do not re-run

- XB (the double Exit on `main`): PASS in `pr-train-20261029` round 1. X1 needs no control arm.
- P2 (immersive is unchanged): PASS in `pr-train-20261029` round 1. #1211's new commit does not touch the immersive
  branch of `SystemUI`.
- Esp32 rounds 2 and 3: C1, C2 and the pairing order are settled. K (board in reset, Native mode) is not repeated:
  E3 asks the item-4 question on a non-relay session instead.
- #1167's ZBT route on a real module: no rig unit has the daemon; the dev host reviewed it.

## 9. Report back

1. X1: L-STOP, the Stops before L-DES, S-FGS, S-FGSX, any FATAL block with its first 5 lines, and the services dump.
2. P1 against PB: L-MOVE and L-HON in the 2 s after SSL and in the whole session, the final anchor, `mAppBounds`,
   and L-ZBT on both arms.
3. R1 against RB: the announced size on each arm, the L-RES line, and `chained=`.
4. E2: sessions out of 5, seconds to SSL per cycle, and every phone dial MAC. E4: the second session, F-CLEAR, F-BLE.
   E3: L-PUW, L-PUF, and the last L-THR before the exit.
5. E5: the searched list's row count on D-SAM against D-HU's, and the resumed activity after the nav-id start.

```decisive-strings
SSL handshake complete
Throughput over 
Stop action received.
AapService destroying
the wireless teardown did not finish in
claimed the microphone foreground-service type
did not then call Service.startForeground
ForegroundServiceDidNotStartInTimeException
Process: com.andrerinas.headunitrevived
HeadUnitScreenConfig: anchor 
Honest Init | Mode: 
Surface mismatch detected!
[ZBT] nothing is listening on 
NegotiatedResolution is: 
[RES_CAP] resolutionId=
NativeAA: Bluetooth relay transport is on
ACTIVELY LISTENING on Android Auto UUID
ESP32 relay: opening new-phone pairing window on
ESP32 relay: phone pairing confirmed
ESP32 relay: releasing HFP (
PROJECTION_UP not recorded for 10s
ESP32 relay: PROJECTION_UP failed for 
NetworkDiscovery: Found Headunit Server on 
createGroup SUCCESS
Creating rfcomm socket for device: 
Critical error
Classic name OpenHU-BT-Relay, BLE name
advertising OpenHU-BT-Relay-Setup
pairing mode for 
phone paired: 
pairing failed: 
projection cleared (
head unit BLE connected
Hash of data verified
```
