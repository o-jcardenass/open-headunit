# pr-train-20261029, round 2 brief: stage W only (#1209, a pending rotation below API 29)

Published name: `pr-train-20261029-round2-brief.md`. Results go in `pr-train-20261029-round2-results.md`.
Evidence: add `pr-train-20261029-round2-captures.zip` to the release `rig-evidence-pr-train-20261029`.

**The point of the round is W1.** Round 1 graded XB, X1, PB and P2 PASS and P1 INCONCLUSIVE, and the operator stopped
it before stage W. Its WB formed no session, because D-POCO was still bonded to D-HU: D-POCO's Android Auto dialled
D-HU's address 30 times and never D-SAM. This round runs stage W only, on the same two builds, with that bond gone.

Units: D-SAM (head unit, API 19, Native wireless) with D-POCO (phone). D-HU Bluetooth held off. D-MOTO radios off.
Time: setup 5 min, WB 8, W1 9, W2 8, cleanup 6, plus thermal waits. **About 40 min.**

```rig-requires
lib 19f862828
unit D-SAM role=headunit wifi=on bt=any battery=50
unit D-POCO role=phone wifi=on bt=on nobond=D-HU aa=D-SAM battery=30
unit D-HU bt=off
unit D-MOTO wifi=off bt=off
candidate T e2b1264c
candidate B a4f80ff1b
hardware D-SAM charges from its own supply (its USB port does not charge it)
run setup cap=420 script=setup.sh grade=grade-setup.sh
run WB cap=900 script=wb.sh grade=grade-wb.sh
run W1 cap=1000 script=w1.sh grade=grade-w1.sh point=yes
run W2 cap=900 script=w2.sh grade=grade-w2.sh
run cleanup cap=600 script=cleanup.sh always=yes
stop setup FAIL,INCONCLUSIVE,UNTESTABLE skip=all
stop WB UNTESTABLE skip=all
stop W1 UNTESTABLE skip=all
```

`bt=any` on D-SAM: its `dumpsys bluetooth_manager` prints no `state:` line on API 19 (round 1 of
`native-bt-broker-esp32`), so a read-back would read `unknown` and stop the session. Its Bluetooth is on in the
baseline. `nobond=D-HU` on D-POCO is the fix for round 1's WB.

| Run | Arm | What it proves | Min |
|---|---|---|---|
| setup | B | the units are in a known state: B on D-SAM, the keys written, no board bond on D-POCO | 5 |
| WB | B | positive control: `main` reads the surviving group and skips the pending rotation | 8 |
| **W1** | T | **the point: a pending rotation purges and re-creates below API 29 (#1209)** | 9 |
| W2 | T | an ordinary relaunch still reads the group it left | 8 |
| cleanup | | B on D-SAM, D-POCO and D-HU; settings back; D-HU Bluetooth on; D-MOTO `stayon false` | 6 |

## 1. Build and baseline

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| T (candidate) | `fork/test/pr-train-20261029` | `e2b1264c5265ac32ad2667f889838a24664c467c` | `13e22f6e1ddf16234344ab318c6ba88a23b96077` |
| B (control) | `main` | `a4f80ff1b6aa6726c17e7385192cf23abe0d0e9f` | `f36d4598ff4bcd75182661f91f87841b0f08483f` |

The same builds as round 1, and no history was rewritten. Round 1's APKs had md5 T `2dda80522bfb9b5453ba0a6e7bdf8bcd`
and B `9a077f29fb3ba545285fc028f69cba0a`; a rebuild may differ, so the setup run checks the DEX instead. Build both
arms in one worktree (`git checkout`), with the thermal gate and `MAX_WORKERS=2`, and copy them to `$EV/apk/T.apk`
and `$EV/apk/B.apk`. The dev host ran the JVM suite at T: 4399 tests, 1 order-dependent failure (`WppTcpTlsTest`).
No rig unit tests.

## 2. What this is and why

**#1209 (WiFi Direct below API 29).** A pending identity rotation (`wifi-direct-rotation-pending`) now stops the
bring-up from reading a surviving group: it removes the group, purges the saved profile and creates a new one
(`P2pIdentityRotationPolicy.purgeBeforeCreate` in `WifiDirectManager`). On `main` the bring-up reads the surviving
group, which skips the rotation. WB shows the skip on B; W1 shows the purge on T; W2 shows that a relaunch with no
rotation pending still reads the group.

The other eight PRs of the train were graded in round 1, or need no rig run. They are not re-run.

## 3. What is different about this round

- **Known state first.** Round 1 did not do its closing. So `setup` installs B on D-SAM and writes every key, and
  `cleanup` (always runs) installs B on D-SAM, D-POCO and D-HU and puts the rest back. The block switches the radios.
- **The bond.** `nobond=D-HU` is in the block, so the session asks the operator to forget D-HU on D-POCO before the
  first run if the bond is back. `setup` also removes an `OpenHU-BT-Relay` bond on D-POCO with up to 3 injected taps:
  `native-bt-broker-esp32` round 2 left one, and D-POCO's Android Auto dials the head unit it last projected to.
- **D-HU Bluetooth self-reverts on.** Each W run holds it off with `rl_hold_off` and releases it at the end.
- **Markers** are `adb shell log -t AutomationMarker <label>` on D-SAM and D-POCO, so they need no app process.
- **A group must survive the force-stop.** D-SAM deletes an empty P2P group, so the relaunch follows the kill at
  once, while D-POCO is still a client. The kill-to-relaunch gap is reported. If WB reads no surviving group, W1 is
  INCONCLUSIVE (reachability), not FAIL.
- **Round 1's defects, for the record** (this round grades none of these lines): L-DES (`AapService destroying`)
  reads 2 on both arms after a double Exit, not 1; L-ANC (`HeadUnitScreenConfig: anchor `) prints only when a canvas is
  adopted, so it reads 0 on a run that adopts none; the P1 INCONCLUSIVE rule (PB's anchor equals `mAppBounds`) was
  contradicted by seeded insets of T77 R91 B66 on D-POCO; D-POCO as head unit does carry video.
- `rl_thermal_gate` calls `$RIG_TOOLKIT/rig_thermal.sh` (limits in `rig-quirks/topics/tooling.md`). Settings go
  through `$RIG_TOOLKIT/set_prefs_runas_host.py` (D-SAM, D-POCO) and `$RIG_TOOLKIT/set_hu_settings_host.py` (D-HU).
- Read `rig-quirks/topics/tooling.md`, `topics/wifi.md`, `units/D-SAM.md`, `units/D-SAM-and-D-HP.md`,
  `units/D-POCO.md`, `units/D-HU.md`, `units/D-MOTO.md`.

**Open note for the PR owner of #1211 (not a run).** In round 1's P1 (T, `fullscreen-mode=0`, D-POCO as head unit,
API 35), T logged 11 `HeadUnitScreenConfig: anchor` lines and B logged 0 (PB). The anchor flipped between 2309x937,
2309x1094, 2400x923 and 2400x1080 in the first 1.5 s of the session (18:39:56.609 to 18:39:57.430), then held at
2400x1080 with seeded insets L0 T77 R91 B66. Is that settling expected, or does each flip re-scale a live surface?

## 4. Settings keys

`write_keys` writes these on D-SAM with the app stopped, and reads `settings.xml` back into `$EV/<run>-settings.xml`.

| Key | Type | Value |
|---|---|---|
| `log-level` | int | 1 (DEBUG) |
| `onboarding-version` | int | 2 |
| `allow-external-configuration` | boolean | true |
| `enable-audio-sink` | boolean | true |
| `use-aac-audio`, `narrow-band-profile-cap`, `kill-on-disconnect`, `enable-floating-button`, `external-bt-zbt-transport` | boolean | false |
| `connection-modes` | set | `wifi` |
| `wifi-connection-mode` | int | 3 |
| `native-ap-transport` | int | 0 |
| `native-poke-all-paired` | boolean | true |
| `wifi-direct-stable-identity` | boolean | true |
| `wifi-direct-rotation-pending` | boolean | false; after the kill, `ROT` (WB true, W1 true, W2 false) |
| `video-profile-starvation-cap`, `native-poke-bt-macs`, `native-mac`, `last-connected-native-mac`, `native-preferred-device-mac` | | delete |

## 5. The deciding lines

App strings checked with `git grep -F` in `app/src/main` at `e2b1264c` and `a4f80ff1b`: all exist on both arms.
Head-unit lines are in `$EV/<run>-hu.txt`, phone lines in `$EV/<run>-ph.txt`, both threadtime.

| Id | Line | Window | Read |
|---|---|---|---|
| L-SSL | `SSL handshake complete` | start to kill; relaunch to end | a session formed (2 lines per handshake at DEBUG) |
| L-ADOPT | `is already up from ` | relaunch to end | the surviving group was read |
| L-PURGE | `WifiDirectManager: persistent profile purge: ` | relaunch to end | the saved profile was purged |
| L-ID | `WifiDirectManager: group identity ssid=` | start to kill; relaunch to end | the SSID before and after the kill (last line of each window) |
| S-CRASH | `Process: com.andrerinas.headunitrevived` | whole D-SAM capture | system line of a `FATAL EXCEPTION` block |
| P-DIAL | `Creating rfcomm socket for device: ` | start to kill | phone: dials, less those to D-HU (`11:46:03:10:33:59`) and the board (`78:1C:3C:2B:AC:EE`) |
| P-CRIT | `Critical error` | start to end, less 2 s after the kill marker | phone |

## 6. Runs

### setup. Known state

**setup.sh**
```bash
rl_step "host: the DEX of both arms"
for a in T B; do
  echo "$a $(unzip -p "$EV/apk/$a.apk" 'classes*.dex' 2>/dev/null | strings | grep -acF NightOverlayState)"
done > "$EV/setup-dex.txt"
rl_step "D-SAM: back up, install B, write the keys"
adb -s "$SAM" shell am force-stop "$PKG"
[ -s "$EV/settings-backup-sam.xml" ] || adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-backup-sam.xml"
install_apk "$SAM" "$APK_B" setup
send ACTION_QUERY_STATE > "$EV/setup-query.txt" 2>&1
write_keys
rl_step "D-POCO: stop the app, remove a board bond, go home"
adb -s "$PH" shell am force-stop "$PKG"
forget_board
adb -s "$SAM" shell date > "$EV/setup-clock.txt"; date >> "$EV/setup-clock.txt"
```

**grade-setup.sh**
```bash
rl_cond 1 ge "$(awk '$1=="T"{print $2}' "$EV/setup-dex.txt")" 1 'NightOverlayState in the T DEX'
rl_cond 2 eq "$(awk '$1=="B"{print $2}' "$EV/setup-dex.txt")" 0 'NightOverlayState not in the B DEX'
rl_cond 3 eq "$(grep -c '^md5match=1' "$EV/setup-identity.txt")" 1 'B installed on D-SAM (md5 of the pulled APK)'  # not-literal
rl_cond 4 ge "$(grep -c "$B_SHA" "$EV/setup-query.txt")" 1 "D-SAM reports commit $B_SHA"
rl_cond 5 ge "$(grep -ac 'name="wifi-connection-mode" value="3"' "$EV/setup-settings.xml")" 1 'wifi-connection-mode 3 read back'  # not-literal
rl_cond 6 ge "$(grep -ac 'name="wifi-direct-stable-identity" value="true"' "$EV/setup-settings.xml")" 1 'wifi-direct-stable-identity true read back'  # not-literal
rl_cond 7 eq "$(grep -c '^board_bond=0' "$EV/setup-board.txt")" 1 'D-POCO holds no OpenHU-BT-Relay bond'  # not-literal
```

- **PASS** when all seven hold. Any other verdict stops the round (the stop rule), and cleanup still runs.

### WB. Positive control: main skips the pending rotation

**wb.sh**
```bash
apk_check "$SAM" "$APK_B" WB
ARM=B; ROT=true
w_cycle
```

**grade-wb.sh**
```bash
[ -s "$EV/WB-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
w_counts WB
[ "${FIRST:-0}" -ge 1 ] || { rl_verdict UNTESTABLE "session gate: no SSL before WB-kill in 2 attempts"; exit 0; }
rl_cond 1 eq "$MD5" 1 'B on D-SAM (md5)'
rl_cond 2 ge "$(grep -c "$B_SHA" "$EV/WB-query.txt")" 1 "commit $B_SHA"
rl_cond 3 ge "$(grep -c 'value="true"' "$EV/WB-rot.txt")" 1 'rotation-pending true read back after the kill'  # not-literal
rl_cond 4 ge "$ADOPT" 1 'L-ADOPT after WB-relaunch (main reads the surviving group)'
rl_cond 5 eq "$SAMESSID" 1 "L-ID: the same SSID before and after the kill ($SSID_BEFORE / $SSID_AFTER)"
rl_cond 6 ge "$SECOND" 1 'L-SSL after WB-relaunch (a second session)'
rl_cond 7 ge "$DIAL_OTHER" 1 'phone: RFCOMM dials to a unit other than D-HU and the board'
rl_cond 8 eq "$CRIT" 0 'phone: Critical error, 2 s after the kill left out'
rl_cond 9 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
rl_cond 10 ge "$GAP" 0 'report: kill-to-relaunch seconds (aim for 5 or less)'
[ "${ADOPT:-0}" -ge 1 ] || rl_verdict INCONCLUSIVE "L-ADOPT 0: no group survived the kill, so W1 cannot be read"
```

- **PASS** when all ten hold: B reads the group it left and skips the rotation, as `main` is expected to.
- **INCONCLUSIVE** when L-ADOPT is 0 (no group survived the kill). W1 then grades INCONCLUSIVE too.
- **UNTESTABLE** when no first session forms in 2 attempts, or the host stays hot. The stop rule then skips W1 and W2.

### W1. A pending rotation purges and re-creates (the point)

**w1.sh**
```bash
install_apk "$SAM" "$APK_T" W1
ARM=T; ROT=true
w_cycle
```

**grade-w1.sh**
```bash
[ -s "$EV/W1-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
WB_ADOPT=$(rl_cnt "$EV/WB-hu.txt" WB-relaunch WB-end 'is already up from ')
WB_SECOND=$(rl_cnt "$EV/WB-hu.txt" WB-relaunch WB-end 'SSL handshake complete')
w_counts W1
[ "${FIRST:-0}" -ge 1 ] || { rl_verdict UNTESTABLE "session gate: no SSL before W1-kill in 2 attempts"; exit 0; }
rl_cond 1 eq "$MD5" 1 'T on D-SAM (md5)'
rl_cond 2 ge "$(grep -c "$T_SHA" "$EV/W1-query.txt")" 1 "commit $T_SHA"
rl_cond 3 ge "$(grep -c 'value="true"' "$EV/W1-rot.txt")" 1 'rotation-pending true read back after the kill'  # not-literal
rl_cond 4 eq "$ADOPT" 0 'L-ADOPT 0 after W1-relaunch (the surviving group is not read)'
rl_cond 5 ge "$PURGE" 1 'L-PURGE after W1-relaunch'
rl_cond 6 eq "$NEWSSID" 1 "L-ID: a new SSID after the kill ($SSID_BEFORE / $SSID_AFTER)"
rl_cond 7 ge "$SECOND" 1 'L-SSL after W1-relaunch (a second session)'
rl_cond 8 ge "$DIAL_OTHER" 1 'phone: RFCOMM dials to a unit other than D-HU and the board'
rl_cond 9 eq "$CRIT" 0 'phone: Critical error, 2 s after the kill left out'
rl_cond 10 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
rl_cond 11 ge "$GAP" 0 'report: kill-to-relaunch seconds (aim for 5 or less)'
if [ "${WB_ADOPT:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "WB read no surviving group (reachability)"
elif [ "${SECOND:-0}" -lt 1 ] && [ "${WB_SECOND:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "no second session on WB either"
fi
```

- **PASS** when all eleven hold: T purges the saved profile, creates a new group and still forms a second session.
- **FAIL** when L-ADOPT is 1 (the rotation was skipped, as on `main`), or no second session forms while WB's did.
- **INCONCLUSIVE** when WB read no surviving group, or neither WB nor W1 formed a second session.

### W2. No rotation: the relaunch still reads the group

**w2.sh**
```bash
apk_check "$SAM" "$APK_T" W2
ARM=T; ROT=false
w_cycle
```

**grade-w2.sh**
```bash
[ -s "$EV/W2-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
w_counts W2
[ "${FIRST:-0}" -ge 1 ] || { rl_verdict UNTESTABLE "session gate: no SSL before W2-kill in 2 attempts"; exit 0; }
rl_cond 1 eq "$MD5" 1 'T on D-SAM (md5)'
rl_cond 2 ge "$(grep -c "$T_SHA" "$EV/W2-query.txt")" 1 "commit $T_SHA"
rl_cond 3 ge "$(grep -c 'value="false"' "$EV/W2-rot.txt")" 1 'rotation-pending false read back after the kill'  # not-literal
rl_cond 4 ge "$ADOPT" 1 'L-ADOPT after W2-relaunch'
rl_cond 5 eq "$PURGE" 0 'L-PURGE 0 after W2-relaunch'
rl_cond 6 ge "$SECOND" 1 'L-SSL after W2-relaunch (a second session)'
rl_cond 7 ge "$DIAL_OTHER" 1 'phone: RFCOMM dials to a unit other than D-HU and the board'
rl_cond 8 eq "$CRIT" 0 'phone: Critical error, 2 s after the kill left out'
rl_cond 9 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
rl_cond 10 ge "$GAP" 0 'report: kill-to-relaunch seconds (aim for 5 or less)'
```

- **PASS** when all ten hold. **FAIL** otherwise: with no rotation pending, T must still read the group it left.

### cleanup. Units back to a known state (always runs)

**cleanup.sh**
```bash
fail=0
rl_step "D-SAM: exit, install B, restore settings"
app_exit
install_apk "$SAM" "$APK_B" cleanup-sam
if [ -s "$EV/settings-backup-sam.xml" ]; then
  adb -s "$SAM" push "$EV/settings-backup-sam.xml" /data/local/tmp/settings-restore.xml >/dev/null
  adb -s "$SAM" shell run-as "$PKG" cp /data/local/tmp/settings-restore.xml shared_prefs/settings.xml
  adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml | tr -d '\r' | grep -v '^$' > "$EV/cleanup-sam-settings.xml"
  tr -d '\r' < "$EV/settings-backup-sam.xml" | grep -v '^$' | diff - "$EV/cleanup-sam-settings.xml" > "$EV/cleanup-sam-diff.txt" || fail=1
fi
rl_step "D-POCO: install B"
adb -s "$PH" shell am force-stop "$PKG"
install_apk "$PH" "$APK_B" cleanup-poco
rl_step "D-HU: install B, drop round 1's keys, car_mode 0, Bluetooth on"
adb -s "$DHU" shell am force-stop "$PKG"
install_apk "$DHU" "$APK_B" cleanup-dhu
python3 "$RIG_TOOLKIT/set_hu_settings_host.py" "$DHU" del:night-mode del:car-signal-invert del:fake_speed > "$EV/cleanup-dhu-keys.txt" 2>&1
adb -s "$DHU" shell settings put system car_mode 0
rl_radio "$DHU" bt on || fail=1
rl_step "D-MOTO: stayon false"
adb -s "$MOTO" shell svc power stayon false
grep -h . "$EV"/cleanup-*-identity.txt
exit "$fail"
```

D-SAM gets this round's own backup back, which is the state round 1 left. D-POCO keeps the settings round 1 wrote
on it as a head unit (`fullscreen-mode` among them): round 1's backup is not named in its results, so this round does
not restore it. The host's `rig_requires.py baseline` at `approve` puts every radio back.

## 7. Scripts

**lib.sh**
```bash
# Serials, paths, verbs and the W cycle for pr-train-20261029 round 2. Sourced before every script and grade.
source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SAM=$D_SAM; PH=$D_POCO; DHU=$D_HU; MOTO=$D_MOTO
SCR=$EV/scripts; APK_T=$EV/apk/T.apk; APK_B=$EV/apk/B.apk
T_SHA=e2b1264c5; B_SHA=a4f80ff1b
HU_MAC=11:46:03:10:33:59; BOARD_MAC=78:1C:3C:2B:AC:EE
KEYS="scalar:int:log-level:1 scalar:int:onboarding-version:2 scalar:boolean:allow-external-configuration:true
 scalar:boolean:enable-audio-sink:true scalar:boolean:use-aac-audio:false scalar:boolean:narrow-band-profile-cap:false
 scalar:boolean:kill-on-disconnect:false scalar:boolean:enable-floating-button:false set:connection-modes:wifi
 scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:boolean:native-poke-all-paired:true
 scalar:boolean:external-bt-zbt-transport:false scalar:boolean:wifi-direct-stable-identity:true
 scalar:boolean:wifi-direct-rotation-pending:false del:video-profile-starvation-cap del:native-poke-bt-macs
 del:native-mac del:last-connected-native-mac del:native-preferred-device-mac"

send() { local a=$1; shift; adb -s "$SAM" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
mark() {   # the same marker line on D-SAM and D-POCO; no app process needed
  adb -s "$SAM" shell log -t AutomationMarker "$1"; adb -s "$PH" shell log -t AutomationMarker "$1"
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> "$EV/marks.txt"
}
write_keys() {   # every key of section 4 on D-SAM, app stopped, then the file read back
  "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" $KEYS > "$EV/$RUN-keys.txt" 2>&1
  adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/$RUN-settings.xml"
}
apk_check() {   # apk_check SERIAL APK TAG: md5 of the installed APK against the built one, to $EV/TAG-identity.txt
  local s=$1 apk=$2 tag=$3 dev a b
  dev=$(adb -s "$s" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -1)
  rm -f "$EV/$tag-pulled.apk"
  [ -n "$dev" ] && adb -s "$s" pull "$dev" "$EV/$tag-pulled.apk" >/dev/null 2>&1
  a=$(md5sum < "$apk" 2>/dev/null | cut -c1-32); b=$(md5sum < "$EV/$tag-pulled.apk" 2>/dev/null | cut -c1-32)
  if [ -n "$a" ] && [ "$a" = "$b" ]; then echo "md5match=1 $a"; else echo "md5match=0 built=$a installed=$b"; fi > "$EV/$tag-identity.txt"
  rm -f "$EV/$tag-pulled.apk"
}
install_apk() { adb -s "$1" install -r -d "$2" > "$EV/$3-install.txt" 2>&1; apk_check "$1" "$2" "$3"; }
app_exit() {
  send ACTION_DISCONNECT >/dev/null 2>&1; sleep 5
  adb -s "$SAM" shell am start -a android.intent.action.VIEW -d headunit://exit >/dev/null; sleep 5
  adb -s "$SAM" shell am force-stop "$PKG"
}
phone_link() { rl_radio "$PH" wifi "$1"; rl_radio "$PH" bt "$1"; }   # phone_link on|off

tap_ui() {   # tap_ui SERIAL LABEL TEXT: tap the one node whose text is TEXT (any case); 2 when it does not resolve
  local s=$1 label=$2 text=$3 xy="" i
  for i in 1 2 3; do
    adb -s "$s" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb -s "$s" pull /sdcard/ui.xml "$EV/ui-$label.xml" >/dev/null 2>&1
    xy=$(python3 -I "$SCR/tap_text.py" "$EV/ui-$label.xml" "$text" 2>/dev/null) && break
    xy=""; sleep 2
  done
  [ -n "$xy" ] || { echo "$(date +%T) TAP_UNRESOLVED $s $label" >> "$EV/taps.log"; return 2; }
  echo "$(date +%T) tap $s $label $xy" >> "$EV/taps.log"
  adb -s "$s" shell input tap $xy
}
forget_board() {   # remove an OpenHU-BT-Relay bond on D-POCO (3 taps at most); result in $EV/$RUN-board.txt
  local xy n i
  adb -s "$PH" shell am start -a android.settings.BLUETOOTH_SETTINGS >/dev/null; sleep 3
  adb -s "$PH" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb -s "$PH" pull /sdcard/ui.xml "$EV/ui-$RUN-btlist.xml" >/dev/null 2>&1
  if xy=$(python3 -I "$SCR/tap_gear.py" "$EV/ui-$RUN-btlist.xml" OpenHU-BT-Relay OpenHU-NativeBTBroker 2>/dev/null); then
    echo "$(date +%T) tap $PH board-gear $xy" >> "$EV/taps.log"; adb -s "$PH" shell input tap $xy; sleep 2
    tap_ui "$PH" board-forget 'Forget' && sleep 2 && tap_ui "$PH" board-confirm 'Forget device'; sleep 2
  fi
  adb -s "$PH" shell input keyevent KEYCODE_HOME
  n=$(rl_bonds "$PH" | grep -aci 'OpenHU-BT-Relay')   # system
  if [ "${n:-0}" -gt 0 ]; then
    echo "OPERATOR NOW: on D-POCO, forget OpenHU-BT-Relay in Bluetooth settings (the scripted forget did not take)"
    for i in $(seq 1 40); do sleep 15; n=$(rl_bonds "$PH" | grep -aci 'OpenHU-BT-Relay'); [ "${n:-0}" -eq 0 ] && break; done   # system
  fi
  echo "board_bond=${n:-0}" > "$EV/$RUN-board.txt"
}

w_cycle() {   # one W run: a session, a force-stop with the group up, ROT written, a relaunch. Needs ARM, ROT.
  local att from killt
  rm -f "$EV/$RUN-thermal.txt" "$EV/$RUN-gate.txt"
  rl_thermal_gate || { echo HOST_HOT > "$EV/$RUN-thermal.txt"; return 2; }
  rl_hold_off "$DHU" bt || echo "dhu_bt_not_off" >> "$EV/$RUN-gate.txt"
  send ACTION_QUERY_STATE > "$EV/$RUN-query.txt" 2>&1
  write_keys
  adb -s "$PH" shell am force-stop "$PKG"
  adb -s "$PH" shell input keyevent KEYCODE_HOME
  rl_cap_start "$RUN-hu" "$SAM"; rl_cap_start "$RUN-ph" "$PH"
  for att in 1 2; do
    rl_step "first session, attempt $att"
    phone_link off
    adb -s "$SAM" shell am force-stop "$PKG"
    mark "$RUN-start"; from=$(rl_offset "$EV/$RUN-hu.txt")
    adb -s "$SAM" shell am start -n "$MAIN" >/dev/null
    sleep 2; phone_link on
    if rl_wait_line "$EV/$RUN-hu.txt" 'SSL handshake complete' 150 "$from"; then
      echo "first_session=ok attempt=$att" >> "$EV/$RUN-gate.txt"; break
    fi
    echo "first_session=none attempt=$att" >> "$EV/$RUN-gate.txt"; app_exit
  done
  if grep -q '^first_session=ok' "$EV/$RUN-gate.txt"; then
    rl_sleep 30
    mark "$RUN-kill"; killt=$(date +%s)
    adb -s "$SAM" shell am force-stop "$PKG"
    "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" "scalar:boolean:wifi-direct-rotation-pending:$ROT" > "$EV/$RUN-rot-write.txt" 2>&1
    adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml | grep -a 'wifi-direct-rotation-pending' > "$EV/$RUN-rot.txt"
    mark "$RUN-relaunch"; from=$(rl_offset "$EV/$RUN-hu.txt")
    adb -s "$SAM" shell am start -n "$MAIN" >/dev/null
    echo "$(( $(date +%s) - killt ))" > "$EV/$RUN-gap.txt"
    rl_wait_line "$EV/$RUN-hu.txt" 'SSL handshake complete' 150 "$from" > "$EV/$RUN-second.txt"
    rl_sleep 30
  fi
  mark "$RUN-end"
  app_exit
  rl_cap_stop "$RUN-hu"; rl_cap_stop "$RUN-ph"
  rl_release "$DHU" bt
}

w_counts() {   # w_counts RUN: the counts every W grade reads, from that run's two captures
  local r=$1 hu="$EV/$1-hu.txt" ph="$EV/$1-ph.txt" total dhu board
  FIRST=$(rl_cnt "$hu" "$r-start" "$r-kill" 'SSL handshake complete')
  SECOND=$(rl_cnt "$hu" "$r-relaunch" "$r-end" 'SSL handshake complete')
  ADOPT=$(rl_cnt "$hu" "$r-relaunch" "$r-end" 'is already up from ')
  PURGE=$(rl_cnt "$hu" "$r-relaunch" "$r-end" 'WifiDirectManager: persistent profile purge: ')
  SSID_BEFORE=$(rl_win "$hu" "$r-start" "$r-kill" | grep -ao 'group identity ssid=[^ ]*' | tail -1 | cut -d= -f2)
  SSID_AFTER=$(rl_win "$hu" "$r-relaunch" "$r-end" | grep -ao 'group identity ssid=[^ ]*' | tail -1 | cut -d= -f2)
  SAMESSID=0; NEWSSID=0
  [ -n "$SSID_BEFORE" ] && [ "$SSID_BEFORE" = "$SSID_AFTER" ] && SAMESSID=1
  [ -n "$SSID_BEFORE" ] && [ -n "$SSID_AFTER" ] && [ "$SSID_BEFORE" != "$SSID_AFTER" ] && NEWSSID=1
  CRASH=$(grep -acF 'Process: com.andrerinas.headunitrevived' "$hu" 2>/dev/null)   # system
  total=$(rl_cnt "$ph" "$r-start" "$r-kill" 'Creating rfcomm socket for device: ')   # phone
  dhu=$(rl_win "$ph" "$r-start" "$r-kill" | grep -aciF "Creating rfcomm socket for device: $HU_MAC")   # phone
  board=$(rl_win "$ph" "$r-start" "$r-kill" | grep -aciF "Creating rfcomm socket for device: $BOARD_MAC")   # phone
  DIAL_OTHER=$(( ${total:-0} - ${dhu:-0} - ${board:-0} ))
  CRIT=$(python3 -I "$SCR/crit_count.py" "$ph" "$r-start" "$r-end" 'Critical error' "$r-kill")   # phone
  MD5=$(grep -c '^md5match=1' "$EV/$r-identity.txt" 2>/dev/null)   # not-literal
  GAP=$(cat "$EV/$r-gap.txt" 2>/dev/null)
}
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

- XB, X1, PB, P1 and P2: graded in round 1. P1's anchor flip is the open note in section 3, not a run.
- Two-finger gestures and the night overlay: no injection route.

## 9. Report back

1. W1: L-ADOPT, L-PURGE (with its verdict text), both L-ID SSIDs, and whether the second session formed.
2. WB: L-ADOPT and both SSIDs (the control), and the kill-to-relaunch gap of all three W runs.
3. W2: L-ADOPT and L-PURGE, and any cleanup diff on D-SAM.
