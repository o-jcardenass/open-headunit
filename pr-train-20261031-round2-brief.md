# pr-train-20261031, round 2 brief: #1187 review item 4 on a Headunit Server session

Published name: `pr-train-20261031-round2-brief.md`. Results go in `pr-train-20261031-round2-results.md`.
Evidence: add `pr-train-20261031-round2-captures.zip` to the release `rig-evidence-pr-train-20261031`.

**One point.** E3 (#1187, review item 4): with a board saved and switched on but held in reset, a Headunit Server
session on D-HU must stay up 30 s after SSL, with no relay-driven disconnect. Round 1 formed no session in E3, so
this round makes the network and the phone's server checkable before the arm. E1 gets the board saved and paired,
with no hand step. E5 repeats the API 19 check with a dump that waits for the right screen.

Units: D-HU (head unit), D-POCO (phone), board E-1; D-SAM (API 19) in E5. D-MOTO stays off the air.
Time: setup 6 min, E1 8, E3 10, E5 6, cleanup 8, plus thermal waits. **About 40 min.**

Queue line: `pr-train-20261031-round2-brief.md` (T `8e0452dd` on `fork/test/pr-train-20261031`, the round 1 APK, no
new build; units D-HU, D-POCO, D-MOTO, D-SAM, E-1; E3 is the point).
Pairing: none. The round uses every Android unit, so it cannot run beside another round.

```rig-requires
lib 8191b7c4c
candidate T 8e0452ddc
unit D-HU role=headunit wifi=on bt=on
unit D-MOTO wifi=off bt=off
unit D-POCO role=phone wifi=on bt=on nobond=D-HU aa=D-HU battery=30
unit D-SAM@E5 role=headunit
hardware board E-1 (ESP32, MAC 78:1c:3c:2b:ac:ec) is on the host's serial port and runs v1.0.1 from round 1; board E-2 is not touched
hardware D-SAM charges from its own supply or the PC port
run setup cap=900 script=setup.sh grade=grade-setup.sh
run E1 cap=900 script=e1.sh grade=grade-e1.sh
run E3 cap=900 script=e3.sh grade=grade-e3.sh point=yes
run E5 cap=600 script=e5.sh grade=grade-e5.sh
run cleanup cap=900 script=cleanup.sh grade=grade-cleanup.sh always=yes
stop setup FAIL,UNTESTABLE skip=all
```

D-POCO must not be bonded to D-HU: a phone bonded to D-HU dials D-HU and ignores the relay (esp32 round 2, stage B).
D-MOTO is bonded to D-HU, so its radios stay off for the whole round.

| Run | Units | What it proves | Min |
|---|---|---|---|
| setup | D-HU, D-POCO, E-1 | T installed by md5, the prior APK and settings backed up, the board alive | 6 |
| E1 | D-HU + D-POCO + E-1 | a saved, paired, switched-on board with no hand step; the relay row on API 34 (E5's control) | 8 |
| **E3** | D-HU + D-POCO + E-1 | **board saved, on, held in reset: a Headunit Server session is still up 30 s after SSL** | 10 |
| E5 | D-SAM | API 19: no relay row in a dump of our own settings screen; the relay screen leaves with no crash | 6 |
| cleanup | all | the prior APK and settings on D-HU and D-SAM, no board bond on D-POCO, the board advertising | 8 |

## 1. Build and baseline

| Arm | Ref | Commit | APK md5 |
|---|---|---|---|
| T (candidate) | `fork/test/pr-train-20261031` | `8e0452ddc2181ef2dd67de57f2232c087763a2f5` | `e925f1aa31d4f9f761211aaac81d95c4` |

The same build as round 1. No history was rewritten. Setup copies `apk/T.apk` from round 1's evidence folder
(`$RIG_DATA/rounds/pr-train-20261031-round1/apk/T.apk`) and checks its md5. Only if that file is gone, build T in one
worktree (`git checkout --detach 8e0452ddc`, `rl_thermal_gate`, `MAX_WORKERS=2`) and put it at `$EV/apk/T.apk` before
the session. The DEX tell is `Esp32RelaySession` (1 or more). The dev host ran the JVM suite at T: 4423, 0 failures.
No rig unit tests. No baseline arm this round: E3's control is the code path itself (see "What a PASS looks like").

## 2. What this is and why

#1187 adds an ESP32 board that carries the Bluetooth half of Native AA. Its first head kept a 10 s `PROJECTION_UP`
watch armed whenever a board was saved, so a session that never used the board (USB, Headunit Server) ended 10 s
after SSL. Our review item 4 asked for the watch to arm only on a relay session. Commit `544cf488` does that:
`Esp32RelaySession.shouldNotifyProjection` needs a saved board **and** `relayTransportActive`, and the
`onProjectionRecordFailed` hook in `AapService` checks `isRelayTransportActive()` before it disconnects. E3 is the
hardware check of that gate.

Round 1 graded X1, PB, P1, R1, E2 and E4 (E4 PASS after the host's regrade: a full WPP handshake ran over the relay,
SSL 7 s after the relaunch, no board reset). E3 formed no session in 150 s, and the results named no cause:

- D-HU's sweep of `192.168.1.*` found nothing. The tally line counts 5289 only, and no
  `NetworkDiscovery: Found Headunit Server on ` printed, so no 5277 connect succeeded either.
- D-POCO's phone capture shows no `Head unit connected` in the window, so nothing reached its server.
- The `DEAUTH_LEAVING` and failed-association kernel lines in that capture are **not** in the window. Their kernel
  stamps read 13:56 UTC, which is 08:56 on the rig clock, an hour before `E3-go` (09:55:06). The capture holds the
  buffer from before the run. In the window, D-POCO's `netd` still sets `wlan0` options, so the station was up.
- Round 1's listener check grepped `:149D` anywhere in `/proc/net/tcp*`. That also matches a socket whose remote
  port is 5277, or one in `TIME_WAIT`. It does not prove a listener.

So the cause is open between three things: no real listener on D-POCO, no path between the two units, or a deaf
server (memory `aa-headunit-server-binds-one-connection`: an abandoned connection deafens it, and only a stop and
start of the server clears it). This round reads each one before the arm.

## 3. What is different about this round

- **E3 checks the path and the server before the arm, and names the cause when no session forms.** It reads both
  `wlan0` addresses, both SSIDs and a 3-packet ping each way at four points: before, at the arm, at the session or
  the timeout, and at the end. ICMP does not touch :5277, so the check cannot deafen the server. The listener check
  now reads only sockets whose **local** port is 5277 in state `0A` (LISTEN). A failed pre-check grades
  INCONCLUSIVE with its cause, never FAIL. If D-POCO is off D-HU's /24, E3 cycles D-POCO's WiFi once and reads again.
- **E3 restarts the head unit server with 4 dumped-target taps, no hand step** (fixes round 1's C1 question). It opens
  Android Auto's settings through its exported action `com.google.android.projection.gearhead.SETTINGS`
  (`DefaultSettingsActivity`), waits for Gearhead to hold focus, then taps the overflow `More options`,
  `Stop head unit server`, `More options` and `Start head unit server`, and reads the listener after each half. When
  the server is off, only the first and last taps run. The labels are Gearhead's own strings
  (`menu_action_developer_head_unit_network_server`, `..._stop`, read from the 18.0 decompile; the phones run 18.1,
  which is not decompiled, so the labels are an assumption the dump checks). Only if the listener is still absent does
  the run print one `OPERATOR NOW` line as a fallback.
- **E1 pairs D-POCO with the board through the rig's Bluetooth helper** (fixes round 1's hand step).
  `rig_bt.py pair D-POCO 78:1C:3C:2B:AC:EE` asks for the bond and confirms it under the shell's
  `BLUETOOTH_PRIVILEGED`. Round 1's phone capture shows the board pairs by SSP just-works
  (`btif_dm_ssp_cfm_req_evt ... just_works:true`, pairing variant 3, consent), which the helper's
  `setPairingConfirmation(true)` answers. The `OPERATOR NOW` line stays only as a fallback when the helper prints
  `FAIL`. E1 skips the pairing when the board is already saved, switched on and bonded.
- **E5 waits for our settings screen before it dumps** (fixes round 1's recents dump). Round 1's D-SAM capture shows
  `input keyevent 224` injected as `KEYCODE_RECENTAPPS` on this Samsung 4.4 (09:58:19.779), which opened the recents
  panel the dump caught. E5 now never sends 224: it sends POWER only when `dumpsys power` reads the screen off, MENU
  only when the lock screen shows, then polls `mCurrentFocus` for `SettingsActivity` (cap 20 s) before the dump, and
  dumps once more if the dump is not our package.
- **The settings search fills the list on D-HU (E1) and D-SAM (E5) alike.** E1's dump is the positive control: round
  1 found `text="esp32"` and the relay row in it.
- **Hand steps: none planned.** Injected taps: E1 at most 4 on D-HU, E3 at most 4 on D-POCO, cleanup none. Each
  target is found by exact text or content description in a `uiautomator dump` and logged in `$EV/taps.log`.
- **Rig state round 1 left:** E-1 runs v1.0.1 and advertises; D-HU and D-POCO run B (`c2c0e95a`), and setup keeps
  that APK to restore; `Esp32Relay.xml` is removed; D-POCO holds no board bond and no D-HU bond; D-HU keeps its LE
  bond to `OpenHU-BT-Relay-Setup`.
- Read `rig-quirks/topics/tooling.md`, `topics/bt.md`, `topics/gearhead.md`, `topics/wifi.md`, `units/D-HU.md`,
  `units/D-POCO.md`, `units/D-SAM.md`, `units/D-SAM-and-D-HP.md`, and `native-bt-broker-esp32-hardware-findings.md`.
  Thermal limits are the ones in `rig-quirks/topics/tooling.md`; `rl_thermal_gate` applies them.

## 4. Settings keys

Written by `setkeys` with the app stopped, in one pass, and read back into `$EV/<run>-settings.xml`. D-HU goes through
`set_hu_settings_host.py` (root), D-SAM through `set_prefs_runas_host.py`.

| Key | Type | Value | Runs |
|---|---|---|---|
| `log-level` | int | 2 (INFO) | all |
| `onboarding-version` | int | 2 | all |
| `allow-external-configuration` | boolean | true | all |
| `kill-on-disconnect`, `enable-floating-button`, `narrow-band-profile-cap`, `external-bt-zbt-transport` | boolean | false | all |
| `native-driver-selection-mode` | int | 0 | all |
| `video-profile-starvation-cap` | | delete | all |
| `wifi-connection-mode` | int | 3 in E1 and E5; 1 in E3 | |
| `connection-modes` | set | `wifi` | all |
| `native-ap-transport` | int | 0 | E1 |
| `native-poke-all-paired` | boolean | false | E1, E5 |
| `native-poke-bt-macs`, `native-preferred-device-mac`, `last-connected-native-mac` | set, string | D-POCO's MAC | E1 |
| `native-mac` | | delete | E1 |
| `native-aa-wake-damage-verdict`, `native-aa-wake-armings-without-session` | int | 0 | E1 |

The relay file `shared_prefs/Esp32Relay.xml` on D-HU, written as root with the app stopped:

| Run | File |
|---|---|
| E1 | `enabled` true, no `address` (the pick writes it); left as it is when E1 finds it saved, on and bonded |
| E3 | as E1 left it; if it holds no address, `enabled` true, `address` `78:1C:3C:2B:AC:EE`, `name` `OpenHU-BT-Relay-Setup` |
| cleanup | removed |

The rig audio settings stay as they are; this round grades no audio.

## 5. The deciding lines

App strings checked with `grep -F` in `app/src/main` of the T worktree at `8e0452ddc`. Head-unit lines are in
`$EV/<run>-hu.txt` (D-SAM: `$EV/E5-sam.txt`), phone lines in `$EV/<run>-ph.txt`, board lines in
`$EV/serial-E1.txt` (host clock). Windows are named by their markers.

| Id | Line | Where, window | Read |
|---|---|---|---|
| L-SSL | `SSL handshake complete` | HU, E3 | a session formed (one line per handshake at INFO) |
| L-THR | `Throughput over ` | HU, `E3-check` to `E3-exit` | frames still render 30 s after SSL |
| L-DISC | `NetworkDiscovery: Found Headunit Server on ` | HU, E3 | D-HU found D-POCO's server |
| L-RELAY | `NativeAA: Bluetooth relay transport is on` | HU, E3 | must stay 0: not a relay session |
| L-PUW | `PROJECTION_UP not recorded for 10s` | HU, E3 | `AapService`'s hook ended the session (the bug) |
| L-PUF | `ESP32 relay: PROJECTION_UP failed for ` | HU, E3 | the relay's fail watch fired (the bug) |
| L-PUS | `ESP32 relay: PROJECTION_UP silent for ` | HU, E3 | the same watch, its timer branch |
| L-GIVE | `ESP32 relay: still down after ` | HU, E3 | the link retry gave up and asked to end the session |
| L-PROBE | `ESP32 relay: link probe lost, ending the phone session` | HU, E3 | the link probe asked to end the session |
| L-PAIRW | `ESP32 relay: opening new-phone pairing window on` | HU, E1 | the app opened the pairing window |
| L-PAIRC | `ESP32 relay: phone pairing confirmed` | HU, E1 | the app saw the pair complete |
| S-CRASH | `Process: com.andrerinas.headunitrevived` | HU, whole | system line of a `FATAL EXCEPTION` block |
| P-CRIT | `Critical error` | phone, E3 less 2 s after `E3-exit` | Gearhead's own error |
| P-HUC | `Head unit connected` | phone, E3 | Gearhead's server accepted a head unit (report; 18.1 is not decompiled) |
| F-BOOT | `Classic name OpenHU-BT-Relay, BLE name` | board | a boot: inside E3 it voids the hold |
| F-ADV | `advertising OpenHU-BT-Relay-Setup` | board | BLE is up |
| F-PMODE | `pairing mode for ` | board, E1 | the pairing window opened |
| F-PAIRED | `phone paired: ` | board, E1 | the phone paired with the board |
| F-PFAIL | `pairing failed: ` | board, E1 | a pairing attempt failed |
| H-PAIR | `PAIR_OK` | `$EV/E1-btpair.txt` | the helper made the bond |

## 6. Runs

### setup. Known state

**setup.sh**
```bash
rl_step "host: T.apk from round 1, checked by md5"
mkdir -p "$EV/apk"
[ -s "$APK_T" ] || cp "$R1EV/apk/T.apk" "$APK_T" 2> /dev/null
echo "md5=$(md5sum < "$APK_T" 2> /dev/null | cut -c1-32)" > "$EV/setup-apk.txt"
echo "dex=$(unzip -p "$APK_T" 'classes*.dex' 2> /dev/null | strings | grep -acF Esp32RelaySession)" >> "$EV/setup-apk.txt"   # not-literal
rl_step "D-HU: back up settings and the installed APK, install T"
adb -s "$DHU" shell am force-stop "$PKG"
[ -s "$EV/settings-backup-hu.xml" ] || adb -s "$DHU" shell cat "$SP/settings.xml" > "$EV/settings-backup-hu.xml"
if [ ! -s "$EV/apk/prior-hu.apk" ]; then
  dev=$(adb -s "$DHU" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -1)
  [ -n "$dev" ] && adb -s "$DHU" pull "$dev" "$EV/apk/prior-hu.apk" > /dev/null 2>&1
fi
install_apk "$DHU" "$APK_T" setup-hu
send_to "$DHU" ACTION_QUERY_STATE > "$EV/setup-hu-query.txt" 2>&1
adb -s "$DHU" shell am force-stop "$PKG"
rl_step "D-POCO: the app stopped, the phone at home"
app_exit "$POCO"
adb -s "$POCO" shell input keyevent KEYCODE_HOME
echo "poco=$(btaddr "$POCO" DC:B7:2E:5E:4E:59)" > "$EV/macs.txt"
rl_step "E-1: firmware read against v1.0.1, then a boot to BLE"
if board_ready; then
  [ -s "$R1EV/fw/firmware.bin" ] && "$VENV/bin/esptool" --chip esp32 --port "$E1" verify-flash 0x10000 "$R1EV/fw/firmware.bin" > "$EV/setup-verify.txt" 2>&1
  o=$(rl_offset "$SER"); serial_start --reset
  rl_wait_line "$SER" 'advertising OpenHU-BT-Relay-Setup' 30 "$o" > "$EV/setup-ble.txt"   # system
  rl_stop ser; SER_ON=0
else echo "no E1 path ($E1) or no venv at $VENV" > "$EV/setup-ble.txt"; fi
```

**grade-setup.sh**
```bash
rl_cond 1 eq "$(grep -c "^md5=$T_MD5" "$EV/setup-apk.txt")" 1 "T.apk md5 $T_MD5"
rl_cond 2 ge "$(sed -n 's/^dex=//p' "$EV/setup-apk.txt")" 1 'Esp32RelaySession in the T DEX'
rl_cond 3 eq "$(grep -c '^md5match=1' "$EV/setup-hu-identity.txt")" 1 'T installed on D-HU (md5)'  # not-literal
rl_cond 4 ge "$(grep -c "$T_SHA" "$EV/setup-hu-query.txt")" 1 "D-HU reports commit $T_SHA"
rl_cond 5 ge "$(grep -ac '<map' "$EV/settings-backup-hu.xml")" 1 'D-HU settings backup taken'  # not-literal
rl_cond 6 eq "$([ -s "$EV/apk/prior-hu.apk" ] && echo 1 || echo 0)" 1 'D-HU prior APK kept for cleanup'
rl_cond 7 eq "$(grep -c '^ok' "$EV/setup-ble.txt")" 1 'E-1 booted and advertises'  # not-literal
rl_cond 8 ge "$(grep -aci 'digest matched' "$EV/setup-verify.txt" 2> /dev/null)" 0 'report: E-1 app region matches the v1.0.1 firmware.bin (1 = yes)'  # system
```

- **PASS** when conditions 1 to 7 hold. Any other verdict stops the round; cleanup still runs.
- Report condition 8 and D-POCO's Bluetooth address.

Decision: PASS starts E1 on the known build with the board alive. FAIL means the wrong APK, no backup or a dead
board, so the round stops before any unit changes further.

### E1. A saved, paired, switched-on board, with no hand step

**e1.sh**
```bash
MARK_ON="$DHU $POCO"; F="$EV/E1-hu.txt"
rm -f "$EV/E1-thermal.txt" "$EV/E1-prereq.txt" "$EV/E1-btpair.txt" "$EV/E1-operator.txt"
board_ready || { echo "no E1 path or venv" > "$EV/E1-prereq.txt"; exit 3; }
rl_thermal_gate || { echo HOST_HOT > "$EV/E1-thermal.txt"; exit 2; }
apk_check "$DHU" "$APK_T" E1
relay_read
mode=pair
if grep -q 'name="address">' "$EV/E1-relay.txt" && grep -q 'name="enabled" value="true"' "$EV/E1-relay.txt"; then   # not-literal
  [ "$(board_bond E1-pre)" -ge 1 ] && mode=kept
fi
echo "mode=$mode" > "$EV/E1-mode.txt"
setkeys "$DHU" $(native_keys "$POCO_BT") $E_KEYS
[ "$mode" = pair ] && relay_write true
adb -s "$POCO" shell am force-stop "$PKG"
adb -s "$POCO" shell input keyevent KEYCODE_HOME
serial_start
rl_cap_start E1-hu "$DHU"; rl_cap_start E1-ph "$POCO"
mark E1-go
rl_step "D-HU: the settings list, searched for esp32 (E5's control)"
adb -s "$DHU" shell am start -n "$SETT" --es extra_search_query esp32 > "$EV/E1-start.txt" 2>&1
focus_poll "$DHU" "$PKG/$SETT_CLS" 15 E1-list
rl_sleep 3
dump_ui "$DHU" E1-list
if [ "$mode" = pair ]; then
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
    rl_step "D-POCO: pair the board through the rig's Bluetooth helper"
    python3 -I "$RL_HOME/rig_bt.py" pair "$POCO" "$BOARD_MAC" > "$EV/E1-btpair.txt" 2>&1
    if ! grep -q '^PAIR_OK' "$EV/E1-btpair.txt"; then   # not-literal
      echo "fallback" > "$EV/E1-operator.txt"
      echo "OPERATOR NOW: on D-POCO, open Bluetooth, Pair new device, pair OpenHU-BT-Relay (PIN 0000 if asked) within 120 s"
    fi
    rl_wait_line "$SER" 'phone paired: ' 150 "$os" > "$EV/E1-paired.txt"   # system
  fi
fi
rl_sleep 5
mark E1-end
rl_cap_stop E1-hu; rl_cap_stop E1-ph; rl_stop ser; SER_ON=0
app_exit "$DHU"
relay_read
board_bond E1-post > /dev/null
```

**grade-e1.sh**
```bash
[ -s "$EV/E1-prereq.txt" ] && { rl_verdict UNTESTABLE "$(cat "$EV/E1-prereq.txt")"; exit 0; }
[ -s "$EV/E1-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
F="$EV/E1-hu.txt"
MODE=$(sed -n 's/^mode=//p' "$EV/E1-mode.txt")
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/E1-identity.txt")" 1 'T on D-HU (md5)'  # not-literal
rl_cond 2 ge "$(grep -acF 'text="Bluetooth relay (ESP32)"' "$EV/ui-E1-list.xml")" 1 'the relay row is in the API 34 list (E5 control)'  # not-literal
rl_cond 3 ge "$(grep -acF 'package="com.andrerinas.headunitrevived"' "$EV/ui-E1-list.xml")" 1 'the dump is our settings screen'  # not-literal
rl_cond 4 ge "$(grep -ac 'name="address">' "$EV/E1-relay.txt")" 1 'Esp32Relay.xml holds an address'  # not-literal
rl_cond 5 ge "$(grep -ac 'name="enabled" value="true"' "$EV/E1-relay.txt")" 1 'Esp32Relay.xml enabled'  # not-literal
rl_cond 6 ge "$(sed -n 's/^board_bond=//p' "$EV/E1-post-board.txt")" 1 "D-POCO holds a bond to the board ($BOARD_MAC)"
rl_cond 7 eq "$(grep -acF 'Process: com.andrerinas.headunitrevived' "$F")" 0 'S-CRASH 0'  # system
if [ "$MODE" = pair ]; then
  PAIRED=$(rl_cnt "$SER" E1-go E1-end 'phone paired: ')   # system
  rl_cond 8 ge "$(rl_cnt "$F" E1-go E1-end 'ESP32 relay: opening new-phone pairing window on')" 1 'L-PAIRW'
  rl_cond 9 ge "$(rl_cnt "$SER" E1-go E1-end 'pairing mode for ')" 1 'F-PMODE'   # system
  rl_cond 10 eq "$(grep -c '^PAIR_OK' "$EV/E1-btpair.txt" 2> /dev/null)" 1 'H-PAIR: the helper made the bond (no hand step)'  # not-literal
  rl_cond 11 ge "$PAIRED" 1 'F-PAIRED'
  rl_cond 12 ge "$(rl_cnt "$F" E1-go E1-end 'ESP32 relay: phone pairing confirmed')" 1 'L-PAIRC'
  rl_cond 13 le "$(grep -ac ' E1 tap ' "$EV/taps.log")" 5 'injected taps 5 or fewer'  # not-literal
  rl_cond 14 eq "$(rl_cnt "$SER" E1-go E1-end 'pairing failed: ')" 0 'F-PFAIL 0'   # system
fi
```

- **PASS** when every condition holds. In `mode=pair` that means the screen's steps plus the helper paired the
  phone with the board and no operator touched D-POCO. In `mode=kept` the board was already saved, on and bonded,
  so only conditions 1 to 7 apply.
- **FAIL** when the helper did not bond (condition 10), the board refused (`pairing failed: `), or a screen step did
  not appear. The `OPERATOR NOW` fallback still lets E3 run on a real pairing.
- Report `mode=`, the helper's result line from `$EV/E1-btpair.txt`, the seconds from L-PAIRW to F-PAIRED, and the
  taps used.

Decision: PASS removes the pairing hand step from every later relay brief, and E3 runs on the state a user makes.
FAIL on condition 10 sends the helper's result line to the `rig-lib` owner (the board's consent variant), and relay
briefs keep the operator fallback. E3 runs either way: it writes the relay file itself if E1 saved none.

### E3. Board saved and on, board held in reset, a Headunit Server session (the point for review item 4)

**e3.sh**
```bash
MARK_ON="$DHU $POCO"; F="$EV/E3-hu.txt"; P="$EV/E3-ph.txt"
rm -f "$EV/E3-thermal.txt" "$EV/E3-prereq.txt" "$EV/E3-gate.txt" "$EV/E3-net.txt" "$EV/E3-server.txt"
board_ready || { echo "no E1 path or venv" > "$EV/E3-prereq.txt"; exit 3; }
rl_thermal_gate || { echo HOST_HOT > "$EV/E3-thermal.txt"; exit 2; }
apk_check "$DHU" "$APK_T" E3
app_exit "$DHU"
adb -s "$POCO" shell am force-stop "$PKG"
adb -s "$POCO" shell svc power stayon true
rl_cap_start E3-hu "$DHU"; rl_cap_start E3-ph "$POCO"
mark E3-pre
rl_step "both units on one network: read, and cycle D-POCO's WiFi once if not"
net_read pre
if ! net_same; then
  rl_radio "$POCO" wifi off; rl_radio "$POCO" wifi on
  for i in $(seq 1 40); do [ -n "$(waddr "$POCO")" ] && break; sleep 1; done
  net_read pre2
fi
if net_same; then
  rl_step "D-POCO: stop and start Android Auto's head unit server"
  hus_restart
  if [ "$(listen5277 "$POCO")" -lt 1 ]; then
    echo "fallback" > "$EV/E3-operator.txt"
    echo "OPERATOR NOW: on D-POCO, Android Auto settings, the menu at the upper right, Start head unit server"
    for i in $(seq 1 24); do [ "$(listen5277 "$POCO")" -ge 1 ] && break; rl_sleep 5; done
  fi
  setkeys "$DHU" $HUSRV
  relay_read
  if grep -q 'name="address">' "$EV/E3-relay.txt"; then echo "relay=E1" >> "$EV/E3-gate.txt"   # not-literal
  else relay_write true "$BOARD_MAC" OpenHU-BT-Relay-Setup; echo "relay=written" >> "$EV/E3-gate.txt"; fi
  rl_step "hold E-1 in reset"
  serial_start --hold
  net_read arm
  if [ "$(listen5277 "$POCO")" -ge 1 ]; then
    mark E3-go; o=$(rl_offset "$F")
    adb -s "$DHU" shell am start -n "$MAIN" > /dev/null
    if rl_wait_line "$F" 'SSL handshake complete' 150 "$o" > /dev/null; then
      echo "session=ok" >> "$EV/E3-gate.txt"
      mark E3-ssl; net_read ssl; rl_sleep 30
      mark E3-check; rl_sleep 20
      mark E3-exit
      send_to "$DHU" ACTION_DISCONNECT > /dev/null 2>&1
      rl_sleep 8
    else echo "session=none" >> "$EV/E3-gate.txt"; net_read fail; fi
  else echo "session=skipped cause=listener" >> "$EV/E3-gate.txt"; fi
else echo "session=skipped cause=network" >> "$EV/E3-gate.txt"; fi
mark E3-end
net_read end
app_exit "$DHU"
rl_cap_stop E3-hu; rl_cap_stop E3-ph
rl_stop ser; SER_ON=0
adb -s "$POCO" shell svc power stayon false
rl_step "release E-1 and wait for BLE"
o=$(rl_offset "$SER")
serial_start --reset
rl_wait_line "$SER" 'advertising OpenHU-BT-Relay-Setup' 30 "$o" > "$EV/E3-back.txt"   # system
rl_stop ser; SER_ON=0
```

**grade-e3.sh**
```bash
[ -s "$EV/E3-prereq.txt" ] && { rl_verdict UNTESTABLE "$(cat "$EV/E3-prereq.txt")"; exit 0; }
[ -s "$EV/E3-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the second wait failed"; exit 0; }
F="$EV/E3-hu.txt"; P="$EV/E3-ph.txt"; N="$EV/E3-net.txt"
nv() { local v; v=$(grep -a "^net at=$1 " "$N" 2> /dev/null | tail -1 | tr ' ' '\n' | sed -n "s/^$2=//p"); echo "${v:-0}"; }   # not-literal
PRE=pre; grep -q '^net at=pre2 ' "$N" 2> /dev/null && PRE=pre2   # not-literal
GATE=$(grep -a '^session=' "$EV/E3-gate.txt" 2> /dev/null | tail -1)
HELD=$(grep -acF "SERIAL_CAP open $E1 hold" "$SER" 2> /dev/null)   # not-literal
BOOT=$(rl_cnt "$SER" E3-go E3-end 'Classic name OpenHU-BT-Relay, BLE name')   # system
DISC=$(rl_cnt "$F" E3-go E3-end 'NetworkDiscovery: Found Headunit Server on ')
HUC=$(rl_cnt "$P" E3-pre E3-end 'Head unit connected')   # phone
STAYED=$(grep -ac ' same24=1 ' "$N" 2> /dev/null); READS=$(grep -ac '^net at=' "$N" 2> /dev/null)   # not-literal
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/E3-identity.txt")" 1 'T on D-HU (md5)'  # not-literal
rl_cond 2 eq "$(nv "$PRE" same24)" 1 "D-POCO on D-HU's /24 before the arm (hu=$(nv "$PRE" hu) poco=$(nv "$PRE" poco))"
rl_cond 3 ge "$(nv arm listen)" 1 'a LISTEN socket on :5277 on D-POCO at the arm'
rl_cond 4 ge "$(nv arm ping_hu_poco)" 1 'D-HU pings D-POCO at the arm'
rl_cond 5 le "$(grep -ac ' E3 tap ' "$EV/taps.log" 2> /dev/null)" 5 'injected taps 5 or fewer'  # not-literal
rl_cond 6 ge "$STAYED" 0 "report: network reads on one /24, $STAYED of $READS; server restart: $(tr '\n' ' ' < "$EV/E3-server.txt" 2> /dev/null)"
rl_cond 7 ge "$HUC" 0 "report: P-HUC, Gearhead accepted a head unit ($HUC)"
cause=""
if [ "$(nv "$PRE" same24)" != 1 ]; then cause="D-POCO was not on D-HU's /24 before the arm, after one WiFi cycle (hu=$(nv "$PRE" hu) poco=$(nv "$PRE" poco) ssid $(nv "$PRE" hussid)/$(nv "$PRE" pocossid))"
elif [ "${GATE#session=skipped cause=}" = listener ] || [ "$(nv arm listen)" -lt 1 ]; then cause="no LISTEN socket on :5277 on D-POCO after the scripted restart ($(tr '\n' ' ' < "$EV/E3-server.txt" 2> /dev/null))"
elif [ "$(nv arm ping_hu_poco)" -lt 1 ]; then cause="D-HU could not ping D-POCO at the arm: the network keeps the two units apart"
elif [ "$GATE" = session=none ] && [ "$(nv fail same24)" != 1 ]; then cause="D-POCO left D-HU's network during the 150 s wait (poco=$(nv fail poco))"
elif [ "$GATE" = session=none ] && [ "$(nv fail listen)" -lt 1 ]; then cause="the :5277 listener closed during the 150 s wait"
elif [ "$GATE" = session=none ] && [ "${DISC:-0}" -eq 0 ]; then cause="D-HU's sweep never found D-POCO:5277 with one /24, ping and a listener all in place"
elif [ "$GATE" = session=none ] && [ "${HUC:-0}" -eq 0 ]; then cause="D-HU found the server, and the phone logged no Head unit connected"
elif [ "$GATE" = session=none ]; then cause="the phone accepted D-HU and no SSL followed (a deaf server or a Gearhead dialog)"
fi
if [ -n "$cause" ]; then rl_verdict INCONCLUSIVE "no session: $cause"; exit 0; fi
rl_cond 8 ge "$(grep -ac 'name="wifi-connection-mode" value="1"' "$EV/E3-settings.xml")" 1 'wifi-connection-mode 1 read back'  # not-literal
rl_cond 9 ge "$(grep -ac 'name="enabled" value="true"' "$EV/E3-relay.txt")" 1 'the relay switch is on'  # not-literal
rl_cond 10 ge "$(grep -ac 'name="address">' "$EV/E3-relay.txt")" 1 'a board is saved'  # not-literal
rl_cond 11 eq "$BOOT" 0 'F-BOOT 0 (the board stayed in reset)'
rl_cond 12 ge "$DISC" 1 'L-DISC: D-HU found the server (a Headunit Server session)'
rl_cond 13 eq "$(rl_cnt "$F" E3-go E3-end 'NativeAA: Bluetooth relay transport is on')" 0 'L-RELAY 0 (not a relay session)'
rl_cond 14 ge "$(rl_cnt "$F" E3-check E3-exit 'Throughput over ')" 1 'L-THR from 30 s after SSL to the exit (the session is up)'
rl_cond 15 eq "$(rl_cnt "$F" E3-ssl E3-exit 'SSL handshake complete')" 0 'no second handshake before the exit (the session never dropped)'
rl_cond 16 eq "$(rl_cnt "$F" E3-go E3-end 'PROJECTION_UP not recorded for 10s')" 0 'L-PUW 0'
rl_cond 17 eq "$(rl_cnt "$F" E3-go E3-end 'ESP32 relay: PROJECTION_UP failed for ')" 0 'L-PUF 0'
rl_cond 18 eq "$(rl_cnt "$F" E3-go E3-end 'ESP32 relay: PROJECTION_UP silent for ')" 0 'L-PUS 0'
rl_cond 19 eq "$(rl_cnt "$F" E3-go E3-end 'ESP32 relay: still down after ')" 0 'L-GIVE 0'
rl_cond 20 eq "$(rl_cnt "$F" E3-go E3-end 'ESP32 relay: link probe lost, ending the phone session')" 0 'L-PROBE 0'
rl_cond 21 eq "$(grep -acF 'Process: com.andrerinas.headunitrevived' "$F")" 0 'S-CRASH 0'  # system
rl_cond 22 eq "$(python3 -I "$SCR/crit_count.py" "$P" E3-go E3-end 'Critical error' E3-exit)" 0 'phone: P-CRIT, 2 s after the exit left out'  # phone
rl_cond 23 ge "$(grep -c '^ok' "$EV/E3-back.txt" 2> /dev/null)" 0 'report: E-1 advertising again after the release (1 = yes)'  # not-literal
if [ "${HELD:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "the hold never opened the serial port"
elif [ "${BOOT:-0}" -gt 0 ]; then rl_verdict INCONCLUSIVE "the board booted during E3: the hold did not keep it off"
fi
```

- **PASS** when conditions 1 to 5 and 8 to 22 hold: with a saved, switched-on board that is off, a Headunit Server
  session runs on past the 10 s watch, and no relay line ends it.
- **FAIL** when any of L-PUW, L-PUF, L-PUS, L-GIVE or L-PROBE prints, or frames stop or a second handshake starts
  before the exit.
- **INCONCLUSIVE** when no session forms. The grade names the first failed check in this order: D-POCO off D-HU's
  /24, no LISTEN socket on :5277 after the restart, no ping from D-HU to D-POCO, D-POCO left the network during the
  wait, the listener closed during the wait, the sweep found nothing, the phone logged no connect, or a connect with
  no SSL. Also INCONCLUSIVE when the hold did not open or the board booted.
- **What a PASS looks like if the change did nothing:** on the old head, L-PUW printed about 10 s after SSL and the
  session ended. Conditions 14 and 15 read 30 to 50 s after SSL, past that point, so they fail on the old code.
- Report the four `net at=` lines, `$EV/E3-server.txt`, `relay=` from the gate file, seconds from `E3-go` to L-DISC
  and to L-SSL, P-HUC, and the last L-THR before the exit.

Decision: PASS closes review item 4 (`544cf488` holds), the last open #1187 question on this rig, so #1187 can merge.
FAIL sends the relay line with its timestamp to the contributor: a relay path still ends a session that did not use
the board, and the line names which one. INCONCLUSIVE with a network or listener cause is a rig fix before one more
E3; one with "the sweep found nothing" despite a clean path opens a `main` ticket on `NetworkDiscovery`.

### E5. D-SAM (API 19): no relay row, and the relay screen leaves with no crash

**e5.sh**
```bash
MARK_ON="$SAM"; L="$EV/E5-sam.txt"
rm -f "$EV/E5-thermal.txt" "$EV/E5-list-focus.txt" "$EV/E5-dumps.txt"
rl_thermal_gate || { echo HOST_HOT > "$EV/E5-thermal.txt"; exit 2; }
export ANDROID_HOME=${ANDROID_HOME:-/home/oscar/Android/Sdk}
DEST=""
A2=$(find "$ANDROID_HOME" "$HOME/Android" /opt -name aapt2 -type f -perm -u+x 2> /dev/null | sort | tail -1)
H=""; [ -n "$A2" ] && H=$("$A2" dump resources "$APK_T" 2> /dev/null | grep -a -m1 -E 'resource 0x[0-9a-f]+ id/esp32RelaySettingsFragment' | grep -o '0x[0-9a-f]*')  # not-literal
[ -n "$H" ] && DEST=$((H))
echo "dest=${DEST:-none}" > "$EV/E5-dest.txt"
rl_step "D-SAM: back up, install T, keys"
adb -s "$SAM" shell am force-stop "$PKG"
[ -s "$EV/settings-backup-sam.xml" ] || adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-backup-sam.xml"
if [ ! -s "$EV/apk/prior-sam.apk" ]; then
  dev=$(adb -s "$SAM" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -1)
  [ -n "$dev" ] && adb -s "$SAM" pull "$dev" "$EV/apk/prior-sam.apk" > /dev/null 2>&1
fi
install_apk "$SAM" "$APK_T" E5
setkeys "$SAM" $COMMON scalar:int:wifi-connection-mode:3 set:connection-modes:wifi scalar:boolean:native-poke-all-paired:false del:native-poke-bt-macs
send_to "$SAM" ACTION_QUERY_STATE > "$EV/E5-query.txt" 2>&1
rl_step "D-SAM: screen on and unlocked, never keyevent 224 (RECENTAPPS on this ROM)"
[ "$(adb -s "$SAM" shell dumpsys power | grep -acE 'mScreenOn=true|mWakefulness=Awake')" -ge 1 ] || adb -s "$SAM" shell input keyevent KEYCODE_POWER   # system
sleep 1
[ "$(adb -s "$SAM" shell dumpsys window | grep -acE 'mShowingLockscreen=true|isStatusBarKeyguard=true')" -ge 1 ] && adb -s "$SAM" shell input keyevent KEYCODE_MENU   # system
adb -s "$SAM" shell input keyevent KEYCODE_HOME
rl_cap_start E5-sam "$SAM"
mark E5-go
adb -s "$SAM" shell run-as "$PKG" am start --user 0 -n "$SETT" --es extra_search_query esp32 > "$EV/E5-start.txt" 2>&1
focus_poll "$SAM" "$PKG/$SETT_CLS" 20 E5-list
rl_sleep 5
dump_ui "$SAM" E5-list
echo "dump1=$(grep -acF "package=\"$PKG\"" "$EV/ui-E5-list.xml" 2> /dev/null)" > "$EV/E5-dumps.txt"   # not-literal
if ! grep -qF "package=\"$PKG\"" "$EV/ui-E5-list.xml" 2> /dev/null; then   # not-literal
  focus_poll "$SAM" "$PKG/$SETT_CLS" 10 E5-list
  rl_sleep 3
  dump_ui "$SAM" E5-list
  echo "dump2=$(grep -acF "package=\"$PKG\"" "$EV/ui-E5-list.xml" 2> /dev/null)" >> "$EV/E5-dumps.txt"   # not-literal
fi
adb -s "$SAM" shell am force-stop "$PKG"
mark E5-deep
if [ -n "$DEST" ]; then
  adb -s "$SAM" shell run-as "$PKG" am start --user 0 -n "$SETT" --ei extra_destination "$DEST" > "$EV/E5-deep.txt" 2>&1
  rl_sleep 10
  adb -s "$SAM" shell dumpsys activity activities | grep -a -m2 -E 'mResumedActivity|mFocusedActivity' > "$EV/E5-activity.txt"   # system
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
OURS=$(grep -acF 'package="com.andrerinas.headunitrevived"' "$EV/ui-E5-list.xml" 2> /dev/null)   # not-literal
SEARCH=$(grep -aciF 'text="esp32"' "$EV/ui-E5-list.xml" 2> /dev/null)   # not-literal
CONTROL=$(grep -acF 'text="Bluetooth relay (ESP32)"' "$EV/ui-E1-list.xml" 2> /dev/null)   # not-literal
OTHER=$(grep -ao 'package="[^"]*"' "$EV/ui-E5-list.xml" 2> /dev/null | sort | uniq -c | sort -rn | head -1)   # not-literal
DEST=$(sed -n 's/^dest=//p' "$EV/E5-dest.txt")
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/E5-identity.txt")" 1 'T on D-SAM (md5)'  # not-literal
rl_cond 2 ge "$(grep -c "$T_SHA" "$EV/E5-query.txt")" 1 "commit $T_SHA"
rl_cond 3 ge "$(grep -ac 'name="wifi-connection-mode" value="3"' "$EV/E5-settings.xml")" 1 'wifi-connection-mode 3 read back (the row is built only in Native mode)'  # not-literal
rl_cond 4 ge "$OURS" 1 "the dump is our settings screen (top package: $OTHER; $(tr '\n' ' ' < "$EV/E5-dumps.txt" 2> /dev/null))"
rl_cond 5 ge "$SEARCH" 1 'the list was searched for esp32'
rl_cond 6 eq "$(grep -acF 'text="Bluetooth relay (ESP32)"' "$EV/ui-E5-list.xml" 2> /dev/null)" 0 'no relay row on API 19'  # not-literal
rl_cond 7 eq "$(rl_cnt "$L" E5-go E5-end 'Process: com.andrerinas.headunitrevived')" 0 'S-CRASH 0 from E5-go to E5-end'  # system
if [ "$DEST" != none ]; then
  rl_cond 8 ge "$(grep -ac 'SettingsActivity' "$EV/E5-activity.txt" 2> /dev/null)" 1 'the relay screen left to the settings list (the onDestroyView guard)'  # not-literal
fi
if [ "${OURS:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "both dumps show another screen ($OTHER), so the list cannot be read"
elif [ "${CONTROL:-0}" -eq 0 ]; then rl_verdict INCONCLUSIVE "the search did not find the row on D-HU either (E1 control)"
elif [ "${SEARCH:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "the search text did not apply, so the list was not filtered"
elif [ "$(grep -aci 'error' "$EV/E5-start.txt" 2> /dev/null)" -gt 0 ]; then rl_verdict INCONCLUSIVE "run-as am start printed an error (E5-start.txt)"  # not-literal
fi
```

- **PASS** when every condition holds: our searched list on API 19 has no relay row, and the relay screen, reached
  by its nav id, leaves at once with no crash.
- **FAIL** when the row shows on API 19, or a `FATAL EXCEPTION` names the app (quote its first 20 lines).
- **INCONCLUSIVE** when both dumps show another package, the search did not apply, or the E1 control did not find the
  row on D-HU.
- Report `$EV/E5-list-focus.txt` (the focus readings with times) and `$EV/E5-dumps.txt`.
- Condition 8 runs only when `aapt2` gave the nav id; `dest=none` in `$EV/E5-dest.txt` says it did not.

Decision: PASS closes esp32 round 3's S crash and the API 23 row rule for #1187. FAIL with a stack in
`onDestroyView` points at `3c20980a`; a visible row points at the `SettingsFragment` SDK gate.

### cleanup. Units back to the state round 1 left (always runs)

**cleanup.sh**
```bash
fail=0
rl_step "E-1: reset and wait for BLE"
if board_ready; then
  o=$(rl_offset "$SER"); serial_start --reset
  rl_wait_line "$SER" 'advertising OpenHU-BT-Relay-Setup' 30 "$o" > "$EV/cleanup-ble.txt" || fail=1   # system
  rl_stop ser; SER_ON=0
fi
rl_step "D-HU: exit, remove the relay file, the prior APK and settings back, Bluetooth on"
app_exit "$DHU"
adb -s "$DHU" shell rm -f "$SP/Esp32Relay.xml"
if [ -s "$EV/apk/prior-hu.apk" ]; then install_apk "$DHU" "$EV/apk/prior-hu.apk" cleanup-hu; else fail=1; fi
restore_hu || fail=1
rl_radio "$DHU" bt on || fail=1
rl_step "D-POCO: remove the board bond through the helper, home, screen policy back"
app_exit "$POCO"
python3 -I "$RL_HOME/rig_bt.py" unpair "$POCO" "$BOARD_MAC" > "$EV/cleanup-unpair.txt" 2>&1
board_bond cleanup > /dev/null
adb -s "$POCO" shell input keyevent KEYCODE_HOME
adb -s "$POCO" shell svc power stayon false
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
rl_cond 1 eq "$(grep -c '^md5match=1' "$EV/cleanup-hu-identity.txt")" 1 'the prior APK on D-HU (md5)'  # not-literal
rl_cond 2 eq "$(grep -c . "$EV/cleanup-hu-diff.txt" 2> /dev/null)" 0 'D-HU settings equal the backup'  # not-literal
rl_cond 3 eq "$(sed -n 's/^board_bond=//p' "$EV/cleanup-board.txt")" 0 'D-POCO holds no board bond'
rl_cond 4 eq "$(grep -c '^ok' "$EV/cleanup-ble.txt" 2> /dev/null)" 1 'E-1 advertises'  # not-literal
rl_cond 5 eq "$(grep -c . "$EV/cleanup-sam-diff.txt" 2> /dev/null)" 0 'D-SAM settings equal the backup'  # not-literal
```

- **PASS** when all five hold. A FAIL leaves a note for the next round and changes no other verdict.
- D-HU keeps its LE bond to `OpenHU-BT-Relay-Setup`, E-1 keeps v1.0.1, and D-POCO's head unit server stays on. List
  all three in the results.

Decision: PASS leaves the rig as round 1 left it, with the server on. FAIL names the unit to restore by hand before
the next round.

## 7. Scripts

**lib.sh**
```bash
# Serials, paths, verbs, settings and read-backs for pr-train-20261031 round 2. Sourced before every script and grade.
source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh
RIG_DATA=${RIG_DATA:-/home/oscar/Coding/StudioProjects/rig-data}
PKG=com.andrerinas.headunitrevived
GH=com.google.android.projection.gearhead
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SETT_CLS=com.andrerinas.openheadunit.main.SettingsActivity
SETT=$PKG/$SETT_CLS
SP=/data/data/$PKG/shared_prefs
DHU=$D_HU; POCO=$D_POCO; SAM=$D_SAM
SCR=$EV/scripts; APK_T=$EV/apk/T.apk
T_SHA=8e0452ddc; T_MD5=e925f1aa31d4f9f761211aaac81d95c4
BOARD_MAC=78:1C:3C:2B:AC:EE
R1EV=$RIG_DATA/rounds/pr-train-20261031-round1
R2=$RIG_DATA/rounds/native-bt-broker-esp32-round2; VENV=$R2/venv
E1=$( [ -f "$R2/ids.env" ] && . "$R2/ids.env" > /dev/null 2>&1; echo "${E1:-}" )
SER=$EV/serial-E1.txt; SER_ON=0; MARK_ON=""
POCO_BT=$(sed -n 's/.*poco=\([0-9A-F:]*\).*/\1/p' "$EV/macs.txt" 2> /dev/null); POCO_BT=${POCO_BT:-DC:B7:2E:5E:4E:59}

COMMON="scalar:int:log-level:2 scalar:int:onboarding-version:2 scalar:boolean:allow-external-configuration:true
 scalar:boolean:kill-on-disconnect:false scalar:boolean:enable-floating-button:false scalar:boolean:narrow-band-profile-cap:false
 scalar:boolean:external-bt-zbt-transport:false scalar:int:native-driver-selection-mode:0 del:video-profile-starvation-cap"
HUSRV="$COMMON scalar:int:wifi-connection-mode:1 set:connection-modes:wifi"
E_KEYS="scalar:int:native-aa-wake-damage-verdict:0 scalar:int:native-aa-wake-armings-without-session:0"
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
  local a; a=$(adb -s "$1" shell settings get secure bluetooth_address 2> /dev/null | tr -d '\r' | tr 'a-f' 'A-F')
  case "$a" in ??:??:??:??:??:??) echo "$a" ;; *) echo "$2" ;; esac
}
running() { local p; p=$(adb -s "$1" shell pidof "$PKG" 2> /dev/null | tr -d '\r'); case "$p" in *[0-9]*) return 0 ;; esac; return 1; }
app_exit() {   # app_exit SERIAL: Disconnect and headunit://exit only while the app runs, then force-stop
  if running "$1"; then
    send_to "$1" ACTION_DISCONNECT > /dev/null 2>&1; sleep 5
    adb -s "$1" shell am start -a android.intent.action.VIEW -d headunit://exit > /dev/null; sleep 5
  fi
  adb -s "$1" shell am force-stop "$PKG"
}
apk_check() {   # apk_check SERIAL APK TAG: md5 of the installed APK against the given one, to $EV/TAG-identity.txt
  local s=$1 apk=$2 tag=$3 dev a b
  dev=$(adb -s "$s" shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -1)
  rm -f "$EV/$tag-pulled.apk"
  [ -n "$dev" ] && adb -s "$s" pull "$dev" "$EV/$tag-pulled.apk" > /dev/null 2>&1
  a=$(md5sum < "$apk" 2> /dev/null | cut -c1-32); b=$(md5sum < "$EV/$tag-pulled.apk" 2> /dev/null | cut -c1-32)
  if [ -n "$a" ] && [ "$a" = "$b" ]; then echo "md5match=1 $a"; else echo "md5match=0 given=$a installed=$b"; fi > "$EV/$tag-identity.txt"
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
dump_ui() {   # dump_ui SERIAL LABEL: a uiautomator dump to $EV/ui-LABEL.xml
  adb -s "$1" shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb -s "$1" pull /sdcard/ui.xml "$EV/ui-$2.xml" > /dev/null 2>&1
  adb -s "$1" shell rm -f /sdcard/ui.xml
}
tap_ui() {   # tap_ui SERIAL LABEL TEXT: tap the one node whose text, else content description, is TEXT; 2 when it does not resolve
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
focus_poll() {   # focus_poll SERIAL NEEDLE CAP_S TAG: read mCurrentFocus each second until it holds NEEDLE; readings to $EV/TAG-focus.txt; 1 at the cap
  local s=$1 n=$2 cap=$3 tag=$4 i line
  for i in $(seq 1 "$cap"); do
    line=$(adb -s "$s" shell dumpsys window 2> /dev/null | tr -d '\r' | grep -a -m1 'mCurrentFocus')   # system
    echo "$(date +%T) $line" >> "$EV/$tag-focus.txt"
    case "$line" in *"$n"*) return 0 ;; esac
    sleep 1
  done
  return 1
}
board_bond() {   # board_bond TAG: D-POCO's bonds through the helper to $EV/TAG-bonds.txt; prints and stores the board count
  local n
  python3 -I "$RL_HOME/rig_bt.py" bonds "$POCO" > "$EV/$1-bonds.txt" 2>&1
  n=$(grep -aci "^$BOARD_MAC" "$EV/$1-bonds.txt")
  echo "board_bond=${n:-0}" > "$EV/$1-board.txt"
  echo "${n:-0}"
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
ssid() {   # ssid SERIAL: the station's SSID, spaces as _
  adb -s "$1" shell cmd wifi status 2> /dev/null | tr -d '\r' | sed -n 's/.*connected to "\(.*\)".*/\1/p' | head -n 1 | tr ' ' '_'
}
listen5277() {   # listen5277 SERIAL: sockets whose LOCAL port is 5277 (0x149D) in state 0A (LISTEN); a remote 5277 does not count
  adb -s "$1" shell cat /proc/net/tcp /proc/net/tcp6 2> /dev/null | tr -d '\r' | awk '$2 ~ /:149D$/ && $4 == "0A" {n++} END {print n+0}'
}
net_read() {   # net_read LABEL: both wlan0 addresses, SSIDs, 3 pings each way and the listener, one line to $EV/E3-net.txt
  local hip pip p1=0 p2=0 same=0
  hip=$(waddr "$DHU"); pip=$(waddr "$POCO")
  [ -n "$pip" ] && p1=$(adb -s "$DHU" shell ping -c 3 -W 2 "$pip" 2> /dev/null | grep -ac 'bytes from')   # system
  [ -n "$hip" ] && p2=$(adb -s "$POCO" shell ping -c 3 -W 2 "$hip" 2> /dev/null | grep -ac 'bytes from')   # system
  [ -n "$hip" ] && [ -n "$pip" ] && [ "${hip%.*}" = "${pip%.*}" ] && same=1
  echo "net at=$1 hu=${hip:-none} poco=${pip:-none} same24=$same ping_hu_poco=${p1:-0} ping_poco_hu=${p2:-0} listen=$(listen5277 "$POCO") hussid=$(ssid "$DHU") pocossid=$(ssid "$POCO") t=$(date +%T)" >> "$EV/E3-net.txt"
}
net_same() { tail -1 "$EV/E3-net.txt" 2> /dev/null | grep -q ' same24=1 '; }   # not-literal
hus_restart() {   # hus_restart: stop and start Android Auto's head unit server from its settings menu on D-POCO (4 taps at most); readings to $EV/$RUN-server.txt
  local f="$EV/$RUN-server.txt" i
  echo "before=$(listen5277 "$POCO")" > "$f"
  adb -s "$POCO" shell input keyevent KEYCODE_WAKEUP
  adb -s "$POCO" shell wm dismiss-keyguard
  adb -s "$POCO" shell am start -a "$GH.SETTINGS" > "$EV/$RUN-gh-start.txt" 2>&1
  if ! focus_poll "$POCO" "$GH" 15 "$RUN-gh"; then echo "menu=no-focus" >> "$f"; adb -s "$POCO" shell input keyevent KEYCODE_HOME; return 1; fi
  sleep 2
  if tap_ui "$POCO" gh-menu1 'More options'; then
    sleep 2; dump_ui "$POCO" gh-items1
    if python3 -I "$SCR/tap_text.py" "$EV/ui-gh-items1.xml" 'Stop head unit server' > /dev/null 2>&1; then
      tap_ui "$POCO" gh-stop 'Stop head unit server'
      for i in $(seq 1 10); do [ "$(listen5277 "$POCO")" -eq 0 ] && break; sleep 1; done
      echo "after_stop=$(listen5277 "$POCO")" >> "$f"
      sleep 2; tap_ui "$POCO" gh-menu2 'More options' && sleep 2
    fi
    tap_ui "$POCO" gh-start 'Start head unit server' || echo "start=unresolved" >> "$f"
    for i in $(seq 1 15); do [ "$(listen5277 "$POCO")" -ge 1 ] && break; sleep 1; done
  else echo "menu=no-overflow" >> "$f"; fi
  echo "after=$(listen5277 "$POCO")" >> "$f"
  adb -s "$POCO" shell input keyevent KEYCODE_HOME
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
# Print the centre of the one node whose text is argv[2] (any case), else whose content-desc is; exit 2 unless exactly one.
import re, sys, xml.etree.ElementTree as E
try:
    root = E.parse(sys.argv[1]).getroot()
except Exception as e:
    print(f"tap_text: unreadable dump: {e}", file=sys.stderr); sys.exit(2)
want = sys.argv[2].casefold()
nodes = list(root.iter("node"))
hits = [n for n in nodes if (n.get("text") or "").casefold() == want]
if not hits:
    hits = [n for n in nodes if (n.get("content-desc") or "").casefold() == want]
if len(hits) != 1:
    print(f"tap_text: {len(hits)} matches for {sys.argv[2]!r}", file=sys.stderr); sys.exit(2)
x1, y1, x2, y2 = map(int, re.findall(r"\d+", hits[0].get("bounds")))
print((x1 + x2) // 2, (y1 + y2) // 2)
```

## 8. Do not re-run

- X1 (#1212, double Exit): round 1 INCONCLUSIVE on timing only, with S-CRASH, S-FGS and S-FGSX all 0. Settled for
  this train by the dev host; not a rig question this round.
- PB, P1, R1 and RB (#1211, #1167): P1 and R1 PASS, RB PASS. PB's one `Critical error 3` is a baseline-arm event.
- flash: v1.0.1 on E-1, hash-checked in round 1. Setup only reads it back.
- E2: 5 of 5 relay sessions, the phone dialled the board and never D-HU. PASS.
- E4: PASS on the host's regrade. After the force-stop, a full WPP handshake ran over the relay, SSL 7 s after the
  relaunch, and no board reset. The phone kept its RFCOMM link to the board, so it made no new dial.

## 9. Report back

1. E3: the verdict, and for INCONCLUSIVE the cause line. The four `net at=` lines, `$EV/E3-server.txt`, `relay=`,
   seconds from `E3-go` to L-DISC and to L-SSL, P-HUC, and every L-PUW, L-PUF, L-PUS, L-GIVE or L-PROBE line with its
   time.
2. E1: `mode=`, the helper's result line, and whether the `OPERATOR NOW` fallback printed.
3. E5: the top package of each dump, the focus readings, and the row count on D-SAM against D-HU's.

```decisive-strings
SSL handshake complete
Throughput over 
NetworkDiscovery: Found Headunit Server on 
NativeAA: Bluetooth relay transport is on
PROJECTION_UP not recorded for 10s
ESP32 relay: PROJECTION_UP failed for 
ESP32 relay: PROJECTION_UP silent for 
ESP32 relay: still down after 
ESP32 relay: link probe lost, ending the phone session
ESP32 relay: opening new-phone pairing window on
ESP32 relay: phone pairing confirmed
Process: com.andrerinas.headunitrevived
Critical error
Head unit connected
Classic name OpenHU-BT-Relay, BLE name
advertising OpenHU-BT-Relay-Setup
pairing mode for 
phone paired: 
pairing failed: 
PAIR_OK
```
