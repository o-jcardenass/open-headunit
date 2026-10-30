# pr-train-20261030, round 1 brief: #1209 W1 again with D-SAM's station held off, and #1222 on D-HU

Published name: `pr-train-20261030-round1-brief.md`. Results go in `pr-train-20261030-round1-results.md`.
Evidence: add `pr-train-20261030-round1-captures.zip` to the release `rig-evidence-pr-train-20261030`.

**Two points.** W1 (#1209) runs again, because `pr-train-20261029` round 2 lost it to D-SAM's own station, not to
the PR. D1 (#1222) is new: with the phone's Bluetooth switched off, the session must end after the 5 s delay.

Units: stage W on D-SAM (head unit, API 19) with D-POCO (phone); stage D on D-HU (head unit) with D-MOTO (phone).
Time: setup 5 min, WB 8, W1 9, DB 6, D1 6, D2 6, cleanup 6, plus thermal waits. **About 50 min.**

```rig-requires
lib ca8a86a90
unit D-SAM@setup,WB,W1 role=headunit wifi=on bt=any battery=50
unit D-POCO@WB,W1 role=phone wifi=on bt=on nobond=D-HU aa=D-SAM battery=30
unit D-POCO@DB,D1,D2 wifi=off bt=off
unit D-HU@WB,W1 bt=off
unit D-HU@DB,D1,D2 role=headunit wifi=on bt=on
unit D-MOTO@WB,W1 wifi=off bt=off
unit D-MOTO@DB,D1,D2 role=phone wifi=on bt=on bond=D-HU aa=D-HU battery=30
candidate T 6d038cc5
candidate B 6ff42d9a
hardware D-SAM charges from its own supply (its USB port does not charge it)
run setup cap=420 script=setup.sh grade=grade-setup.sh
run WB cap=900 script=wb.sh grade=grade-wb.sh
run W1 cap=1000 script=w1.sh grade=grade-w1.sh point=yes
run DB cap=600 script=db.sh grade=grade-db.sh
run D1 cap=600 script=d1.sh grade=grade-d1.sh point=yes
run D2 cap=600 script=d2.sh grade=grade-d2.sh
run cleanup cap=600 script=cleanup.sh grade=grade-cleanup.sh always=yes
stop setup FAIL,INCONCLUSIVE,UNTESTABLE skip=all
stop WB UNTESTABLE skip=W1
stop DB UNTESTABLE skip=D1,D2
```

`bt=any` on D-SAM: API 19 prints no `state:` line, so a read-back reads `unknown`. D-POCO's radios are off in stage
D so that D-HU's poke of every bonded phone cannot reach it.

| Run | Arm | What it proves | Min |
|---|---|---|---|
| setup | B | the DEX of both arms, B on D-SAM and D-HU, D-SAM keys written, no board bond on D-POCO | 5 |
| WB | B | positive control: `main` reads the surviving group and skips the pending rotation | 8 |
| **W1** | T | **the point: a pending rotation purges and re-creates below API 29, and the phone joins the new group (#1209)** | 9 |
| DB | B | positive control: on `main` the session survives the phone's Bluetooth going off (the bug) | 6 |
| **D1** | T | **the point: the session ends after the 5 s delay (#1222)** | 6 |
| D2 | T | the phone's Bluetooth back inside a 30 s delay still cancels the end | 6 |
| cleanup | | B on D-SAM and D-HU; D-SAM's network given back; D-HU keys removed | 6 |

## 1. Build and baseline

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| T (candidate) | `fork/test/pr-train-20261030` | `6d038cc5016947bd28f50bcf9da268f840b2a044` | `7fbaf776dd7ce3d4be17d09685e03b94363b7c0e` |
| B (control) | `main` | `6ff42d9a0` (`git rev-parse 6ff42d9a0` on the rig) | `6b65d9c694802fdc1fb984a2267a5a1d22f72722` |

T is `main` `6ff42d9a` plus #1209 head `5a010a39` and #1222 head `7f3c356d`, as two merge commits. Build both arms in
one worktree (`git checkout`), with the thermal gate and `MAX_WORKERS=2`, and copy them to `$EV/apk/T.apk` and
`$EV/apk/B.apk`. The dev host ran the JVM suite at T: 4402 tests, 1 failure, the order-dependent `WppTcpTlsTest` that also failed in `pr-train-20261029` (it passes alone; `main` alone has 0 failures in 4380). No rig unit tests.

## 2. What this is and why

**#1209 (WiFi Direct below API 29).** A pending identity rotation (`wifi-direct-rotation-pending`) now removes the
surviving group, purges the saved profile and creates a new one. On `main` the bring-up reads the surviving group and
skips the rotation. In `pr-train-20261029` round 2, WB and W2 passed and W1 graded FAIL: Type 3 went out 3 times
with the new SSID and the phone answered `WIFI_NETWORK_UNAVAILABLE(-11)` with `NETWORK_NOT_FOUND`. The cause was
D-SAM's station. After the force-stop, `AapService.onCreate` gave D-SAM's home network back
(`WiFi network is enabled again and should rejoin` at 22:32:34.555), the bring-up's stand-down read "not joined" 0.5 s
later while the station was still associating, and the station joined beside the new group on another channel. That
race is in `main`, not in #1209. This round removes it (section 3) and runs WB and W1 again.

**#1222 (Bluetooth auto-disconnect).** With "Auto-disconnect on Bluetooth" set to the phone and a Native session up,
the phone's Bluetooth going off closes the held Bluetooth channel about 40 ms before the OS reports the link gone.
On `main` our end's close is then stamped as our own close, and when the 5 s delay runs out the session is kept
(`not ending the session for ... ownCloseMs=`), with nothing checking again. #1222 does not stamp a close of a channel
the phone already closed (`BtAutoDisconnectPolicy.closeIsOurs`). DB shows the bug on B, D1 the fix on T, and D2 that
the "came back" cancel still works.

## 3. What is different about this round

- **D-SAM's station is held off from the first kill on.** After each W run's force-stop, `w_cycle` reads
  `station-stand-down-network-id` into `$EV/netid.txt` (first value only) and deletes the key, so the relaunch has
  no network to give back and D-SAM stays off its home network for the second session. `cleanup` writes the key back,
  starts the app once so `onCreate` gives the network back, and checks the `enabled again` line. W1 is INCONCLUSIVE,
  not FAIL, when the coexistence line still prints in its relaunch window.
- **The auto-disconnect MAC is written by `put_macs.py`**, not by the settings helper, because the value holds colons.
  It edits D-HU's pulled `settings.xml` and is pushed back with `cat >`, which keeps the file's owner and mode.
- **Markers** are `adb shell log -t AutomationMarker <label>` on the head unit and the phone of each stage.
- **D-HU Bluetooth self-reverts on.** Each W run holds it off with `rl_hold_off` and releases it at the end.
- Read `rig-quirks/topics/tooling.md`, `topics/wifi.md`, `topics/bt.md`, `units/D-SAM.md`, `units/D-POCO.md`,
  `units/D-HU.md`, `units/D-MOTO.md`.

## 4. Settings keys

**D-SAM**, written by `write_keys` with the app stopped and read back into `$EV/<run>-settings.xml`:

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
| `wifi-direct-rotation-pending` | boolean | false; after the kill, `ROT` (WB true, W1 true) |
| `video-profile-starvation-cap`, `native-poke-bt-macs`, `native-mac`, `last-connected-native-mac`, `native-preferred-device-mac` | | delete |
| `station-stand-down-network-id` | | delete after each kill; written back in cleanup |

**D-HU**, written by `d_keys` with the app stopped, then the MAC by `put_macs.py`, and read back into
`$EV/<run>-hu-settings.xml`:

| Key | Type | Value |
|---|---|---|
| `log-level` | int | 1 (DEBUG) |
| `allow-external-configuration` | boolean | true |
| `connection-modes` | set | `wifi` |
| `wifi-connection-mode` | int | 3 |
| `native-ap-transport` | int | 0 |
| `native-poke-all-paired` | boolean | true |
| `external-bt-zbt-transport`, `kill-on-disconnect` | boolean | false |
| `auto-disconnect-bt-delay-seconds` | int | `DELAY` (DB 5, D1 5, D2 30) |
| `auto-disconnect-bt-macs` | set | D-MOTO's address, read live (`settings get secure bluetooth_address`) |
| `video-profile-starvation-cap` | | delete |

## 5. The deciding lines

App strings checked with `git grep -F` in `app/src/main` at `6d038cc5` and `6ff42d9a`: all exist on both arms.
Head-unit lines are in `$EV/<run>-hu.txt`, phone lines in `$EV/<run>-ph.txt`, both threadtime.

| Id | Line | Window | Read |
|---|---|---|---|
| L-SSL | `SSL handshake complete` | start to kill (W) or start to cut (D); relaunch to end | a session formed (2 lines per handshake at DEBUG) |
| L-ADOPT | `is already up from ` | relaunch to end | the surviving group was read |
| L-PURGE | `WifiDirectManager: persistent profile purge: ` | relaunch to end | the saved profile was purged |
| L-ID | `WifiDirectManager: group identity ssid=` | start to kill; relaunch to end | the SSID before and after the kill |
| L-COEX | `This unit is connected to another WiFi network while hosting` | relaunch to end | D-SAM's station joined beside the group |
| L-GIVE | `WiFi network is enabled again and should rejoin` | relaunch to end; cleanup | the stand-down gave the network back |
| L-HELD | `the phone closed the held Bluetooth channel after` | cut to end | the phone closed the held channel (the bug's start) |
| L-ARM | `went away; ending the session in` | cut to end | the delay started |
| L-KEEP | `Bluetooth auto-disconnect: not ending the session for` | cut to end | the session was kept (the bug) |
| L-END | `stayed away; ending the session the way the Exit button does.` | cut to end | the session was ended |
| L-BACK | `is back; the pending disconnect is cancelled.` | cut to end | the phone came back inside the delay |
| L-THR | `Throughput over ` | check to end | frames still flow after the delay |
| S-CRASH | `Process: com.andrerinas.headunitrevived` | whole head-unit capture | system line of a `FATAL EXCEPTION` block |
| P-DIAL | `Creating rfcomm socket for device: ` | start to kill | phone: dials, less those to D-HU (`11:46:03:10:33:59`) and the board (`78:1C:3C:2B:AC:EE`) |
| P-CRIT | `Critical error` | start to end, less 2 s after the kill or cut marker | phone |

## 6. Runs

### setup. Known state

**setup.sh**
```bash
rl_step "host: the DEX of both arms"
for a in T B; do
  echo "$a $(unzip -p "$EV/apk/$a.apk" 'classes*.dex' 2>/dev/null | strings | grep -acF closeIsOurs)"
done > "$EV/setup-dex.txt"
rl_step "D-SAM: back up, install B, write the keys"
adb -s "$SAM" shell am force-stop "$PKG"
[ -s "$EV/settings-backup-sam.xml" ] || adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-backup-sam.xml"
install_apk "$SAM" "$APK_B" setup
send ACTION_QUERY_STATE > "$EV/setup-query.txt" 2>&1
write_keys
rl_step "D-HU: back up, install B"
adb -s "$DHU" shell am force-stop "$PKG"
[ -s "$EV/settings-backup-hu.xml" ] || adb -s "$DHU" shell cat "/data/data/$PKG/shared_prefs/settings.xml" > "$EV/settings-backup-hu.xml"
install_apk "$DHU" "$APK_B" setup-hu
rl_step "D-POCO: stop the app, remove a board bond, go home"
adb -s "$PH" shell am force-stop "$PKG"
forget_board
adb -s "$SAM" shell date > "$EV/setup-clock.txt"; date >> "$EV/setup-clock.txt"
```

**grade-setup.sh**
```bash
rl_cond 1 ge "$(awk '$1=="T"{print $2}' "$EV/setup-dex.txt")" 1 'closeIsOurs in the T DEX'
rl_cond 2 eq "$(awk '$1=="B"{print $2}' "$EV/setup-dex.txt")" 0 'closeIsOurs not in the B DEX'
rl_cond 3 eq "$(grep -c '^md5match=1' "$EV/setup-identity.txt")" 1 'B installed on D-SAM (md5 of the pulled APK)'  # not-literal
rl_cond 4 eq "$(grep -c '^md5match=1' "$EV/setup-hu-identity.txt")" 1 'B installed on D-HU (md5 of the pulled APK)'  # not-literal
rl_cond 5 ge "$(grep -c "$B_SHA" "$EV/setup-query.txt")" 1 "D-SAM reports commit $B_SHA"
rl_cond 6 ge "$(grep -ac 'name="wifi-connection-mode" value="3"' "$EV/setup-settings.xml")" 1 'wifi-connection-mode 3 read back'  # not-literal
rl_cond 7 eq "$(grep -c '^board_bond=0' "$EV/setup-board.txt")" 1 'D-POCO holds no OpenHU-BT-Relay bond'  # not-literal
rl_cond 8 ge "$(grep -ac 'shared_prefs\|<map' "$EV/settings-backup-hu.xml")" 1 'D-HU settings backup taken'  # not-literal
```

- **PASS** when all eight hold. Any other verdict stops the round, and cleanup still runs.

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
rl_cond 2 ge "$(grep -c 'value="true"' "$EV/WB-rot.txt")" 1 'rotation-pending true read back after the kill'  # not-literal
rl_cond 3 ge "$ADOPT" 1 'L-ADOPT after WB-relaunch (main reads the surviving group)'
rl_cond 4 eq "$SAMESSID" 1 "L-ID: the same SSID before and after the kill ($SSID_BEFORE / $SSID_AFTER)"
rl_cond 5 ge "$SECOND" 1 'L-SSL after WB-relaunch (a second session)'
rl_cond 6 eq "$GIVE" 0 'L-GIVE 0 after WB-relaunch (no network given back)'
rl_cond 7 eq "$COEX" 0 'L-COEX 0 after WB-relaunch (station off)'
rl_cond 8 ge "$DIAL_OTHER" 1 'phone: RFCOMM dials to a unit other than D-HU and the board'
rl_cond 9 eq "$CRIT" 0 'phone: Critical error, 2 s after the kill left out'
rl_cond 10 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
rl_cond 11 ge "$GAP" 0 'report: kill-to-relaunch seconds (aim for 5 or less)'
[ "${ADOPT:-0}" -ge 1 ] || rl_verdict INCONCLUSIVE "L-ADOPT 0: no group survived the kill, so W1 cannot be read"
```

- **PASS** when all eleven hold: B reads the group it left and skips the rotation, with the station off.
- **INCONCLUSIVE** when L-ADOPT is 0. W1 then grades INCONCLUSIVE too.
- **UNTESTABLE** when no first session forms in 2 attempts, or the host stays hot. The stop rule then skips W1.

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
rl_cond 7 ge "$SECOND" 1 'L-SSL after W1-relaunch (a second session on the new group)'
rl_cond 8 eq "$COEX" 0 'L-COEX 0 after W1-relaunch (station off)'
rl_cond 9 ge "$DIAL_OTHER" 1 'phone: RFCOMM dials to a unit other than D-HU and the board'
rl_cond 10 eq "$CRIT" 0 'phone: Critical error, 2 s after the kill left out'
rl_cond 11 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-SAM'
rl_cond 12 ge "$GAP" 0 'report: kill-to-relaunch seconds (aim for 5 or less)'
rl_cond 13 ge "$(rl_cnt "$EV/W1-hu.txt" W1-relaunch W1-end 'Wrote TYPE 3')" 0 'report: Type 3 writes after W1-relaunch'  # not-literal: "Wrote TYPE $type"
rl_cond 14 ge "$(rl_cnt "$EV/W1-ph.txt" W1-relaunch W1-end 'NETWORK_NOT_FOUND')" 0 'report: phone NETWORK_NOT_FOUND after W1-relaunch'  # phone
if [ "${WB_ADOPT:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "WB read no surviving group (reachability)"
elif [ "${COEX:-0}" -ge 1 ]; then rl_verdict INCONCLUSIVE "D-SAM's station joined beside the group again (L-COEX)"
elif [ "${SECOND:-0}" -lt 1 ] && [ "${WB_SECOND:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "no second session on WB either"
fi
```

- **PASS** when conditions 1 to 11 hold: T purges the saved profile, creates a new group, and the phone joins it.
- **FAIL** when L-ADOPT is 1 (the rotation was skipped), or no second session forms with the station off while WB's did.
- **INCONCLUSIVE** when WB read no surviving group, L-COEX printed, or neither WB nor W1 formed a second session.

### DB. Positive control: on main the session survives the phone's Bluetooth going off

**db.sh**
```bash
apk_check "$DHU" "$APK_B" DB
DELAY=5
d_cycle off
```

**grade-db.sh**
```bash
[ -s "$EV/DB-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the wait failed"; exit 0; }
d_counts DB
[ "${FIRST:-0}" -ge 1 ] || { rl_verdict UNTESTABLE "session gate: no SSL before DB-cut in 2 attempts"; exit 0; }
rl_cond 1 eq "$MD5" 1 'B on D-HU (md5)'
rl_cond 2 ge "$MACSET" 1 'auto-disconnect-bt-macs holds D-MOTO read back'
rl_cond 3 ge "$HELD" 1 'L-HELD: the phone closed the held channel'
rl_cond 4 ge "$ARMED" 1 'L-ARM: the delay started'
rl_cond 5 ge "$KEEP" 1 'L-KEEP: main kept the session (the bug)'
rl_cond 6 eq "$ENDED" 0 'L-END 0 on main'
rl_cond 7 ge "$THR" 1 'L-THR after DB-check (the session still renders)'
rl_cond 8 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-HU'
[ "${HELD:-0}" -ge 1 ] || rl_verdict INCONCLUSIVE "L-HELD 0: the phone held no channel, so the bug cannot start"
[ "${ENDED:-0}" -ge 1 ] && rl_verdict INCONCLUSIVE "main ended the session: the bug does not reproduce here"
```

- **PASS** when all eight hold: `main` keeps a session whose phone has gone, as #1222 describes.
- **INCONCLUSIVE** when the phone held no channel, or `main` ended the session itself. D1 is then INCONCLUSIVE too.
- **UNTESTABLE** when no session forms in 2 attempts. The stop rule then skips D1 and D2.

### D1. The session ends after the delay (the point)

**d1.sh**
```bash
install_apk "$DHU" "$APK_T" D1
DELAY=5
d_cycle off
```

**grade-d1.sh**
```bash
[ -s "$EV/D1-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the wait failed"; exit 0; }
DB_KEEP=$(rl_cnt "$EV/DB-hu.txt" DB-cut DB-end 'Bluetooth auto-disconnect: not ending the session for')
d_counts D1
[ "${FIRST:-0}" -ge 1 ] || { rl_verdict UNTESTABLE "session gate: no SSL before D1-cut in 2 attempts"; exit 0; }
rl_cond 1 eq "$MD5" 1 'T on D-HU (md5)'
rl_cond 2 ge "$MACSET" 1 'auto-disconnect-bt-macs holds D-MOTO read back'
rl_cond 3 ge "$HELD" 1 'L-HELD: the phone closed the held channel'
rl_cond 4 ge "$ARMED" 1 'L-ARM: the delay started'
rl_cond 5 eq "$KEEP" 0 'L-KEEP 0 (the session is not kept)'
rl_cond 6 ge "$ENDED" 1 'L-END: the session was ended'
rl_cond 7 ge "$END_S" 4 "report: seconds from D1-cut to L-END ($END_S, expect 5 to 8)"
rl_cond 8 le "$END_S" 12 'L-END within 12 s of the cut'
rl_cond 9 eq "$THR" 0 'L-THR 0 after D1-check (no frames after the end)'
rl_cond 10 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-HU'
if [ "${HELD:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "L-HELD 0: the phone held no channel"
elif [ "${DB_KEEP:-0}" -lt 1 ]; then rl_verdict INCONCLUSIVE "DB did not show the bug, so D1 cannot show the fix"
fi
```

- **PASS** when all ten hold: T ends the session 5 to 12 s after the phone's Bluetooth goes off.
- **FAIL** when L-KEEP prints, or L-END is missing or later than 12 s, with L-HELD present and DB showing the bug.
- **INCONCLUSIVE** when the phone held no channel, or DB did not show the bug.

### D2. The phone back inside the delay cancels the end

**d2.sh**
```bash
apk_check "$DHU" "$APK_T" D2
DELAY=30
d_cycle back
```

**grade-d2.sh**
```bash
[ -s "$EV/D2-thermal.txt" ] && { rl_verdict UNTESTABLE "host thermal: the wait failed"; exit 0; }
d_counts D2
[ "${FIRST:-0}" -ge 1 ] || { rl_verdict UNTESTABLE "session gate: no SSL before D2-cut in 2 attempts"; exit 0; }
BACK=$(rl_cnt "$EV/D2-hu.txt" D2-cut D2-end 'is back; the pending disconnect is cancelled.')
rl_cond 1 eq "$MD5" 1 'T on D-HU (md5)'
rl_cond 2 ge "$ARMED" 1 'L-ARM: the 30 s delay started'
rl_cond 3 ge "$BACK" 1 'L-BACK: the cancel on the phone coming back'
rl_cond 4 eq "$ENDED" 0 'L-END 0 (the session was not ended)'
rl_cond 5 ge "$THR" 1 'L-THR after D2-check (the session still renders)'
rl_cond 6 eq "$CRASH" 0 'no FATAL EXCEPTION for com.andrerinas on D-HU'
[ "${BACK:-0}" -lt 1 ] && [ "${ENDED:-0}" -ge 1 ] && rl_verdict INCONCLUSIVE "the phone's Bluetooth did not reconnect to D-HU inside 30 s"
```

- **PASS** when all six hold: the phone coming back inside the delay keeps the session, as before #1222.
- **FAIL** when L-BACK prints and the session still ends, or frames stop with no L-END.
- **INCONCLUSIVE** when the phone never reconnected inside the delay, so the session ended correctly.

### cleanup. Units back to a known state (always runs)

**cleanup.sh**
```bash
fail=0
rl_step "D-SAM: give the network back, install B, restore settings"
NETID=$(head -1 "$EV/netid.txt" 2>/dev/null)
adb -s "$SAM" shell am force-stop "$PKG"
if [ -n "$NETID" ]; then
  "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" "scalar:int:station-stand-down-network-id:$NETID" > "$EV/cleanup-netid.txt" 2>&1
  rl_cap_start cleanup-hu "$SAM"; from=$(rl_offset "$EV/cleanup-hu.txt")
  adb -s "$SAM" shell am start -n "$MAIN" >/dev/null
  rl_wait_line "$EV/cleanup-hu.txt" 'WiFi network is enabled again and should rejoin' 60 "$from" > "$EV/cleanup-give.txt" || fail=1
  app_exit
  rl_cap_stop cleanup-hu
fi
install_apk "$SAM" "$APK_B" cleanup-sam
if [ -s "$EV/settings-backup-sam.xml" ]; then
  adb -s "$SAM" push "$EV/settings-backup-sam.xml" /data/local/tmp/settings-restore.xml >/dev/null
  adb -s "$SAM" shell run-as "$PKG" cp /data/local/tmp/settings-restore.xml shared_prefs/settings.xml
  adb -s "$SAM" shell rm -f /data/local/tmp/settings-restore.xml
fi
rl_step "D-HU: install B, restore settings, Bluetooth on"
adb -s "$DHU" shell am force-stop "$PKG"
install_apk "$DHU" "$APK_B" cleanup-dhu
if [ -s "$EV/settings-backup-hu.xml" ]; then
  adb -s "$DHU" push "$EV/settings-backup-hu.xml" /data/local/tmp/settings-restore.xml >/dev/null
  adb -s "$DHU" shell "cat /data/local/tmp/settings-restore.xml > /data/data/$PKG/shared_prefs/settings.xml; rm -f /data/local/tmp/settings-restore.xml"
  adb -s "$DHU" shell cat "/data/data/$PKG/shared_prefs/settings.xml" > "$EV/cleanup-hu-settings.xml"
  diff <(tr -d '\r' < "$EV/settings-backup-hu.xml") <(tr -d '\r' < "$EV/cleanup-hu-settings.xml") > "$EV/cleanup-hu-diff.txt" || fail=1
fi
rl_radio "$DHU" bt on || fail=1
rl_step "D-MOTO: Bluetooth on, stayon false"
rl_radio "$MOTO" bt on || fail=1
adb -s "$MOTO" shell svc power stayon false
echo "netid=${NETID:-none} fail=$fail" > "$EV/cleanup-result.txt"
exit "$fail"
```

**grade-cleanup.sh**
```bash
NETID=$(head -1 "$EV/netid.txt" 2>/dev/null)
if [ -n "$NETID" ]; then
  rl_cond 1 ge "$(grep -acF 'WiFi network is enabled again and should rejoin' "$EV/cleanup-hu.txt")" 1 'L-GIVE: D-SAM got its network back'
else
  rl_cond 1 eq 0 0 'no network id was saved, so nothing to give back'
fi
rl_cond 2 eq "$(grep -c '^md5match=1' "$EV/cleanup-sam-identity.txt")" 1 'B on D-SAM'  # not-literal
rl_cond 3 eq "$(grep -c '^md5match=1' "$EV/cleanup-dhu-identity.txt")" 1 'B on D-HU'  # not-literal
rl_cond 4 eq "$(grep -c . "$EV/cleanup-hu-diff.txt" 2>/dev/null)" 0 'D-HU settings equal the backup'  # not-literal
```

- **PASS** when all four hold. A FAIL leaves a note for the next round and changes no other verdict.

## 7. Scripts

**lib.sh**
```bash
# Serials, paths, verbs, the W cycle and the D cycle for pr-train-20261030 round 1. Sourced before every script and grade.
source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
SAM=$D_SAM; PH=$D_POCO; DHU=$D_HU; MOTO=$D_MOTO
SCR=$EV/scripts; APK_T=$EV/apk/T.apk; APK_B=$EV/apk/B.apk
T_SHA=6d038cc50; B_SHA=6ff42d9a0
HU_MAC=11:46:03:10:33:59; BOARD_MAC=78:1C:3C:2B:AC:EE
KEYS="scalar:int:log-level:1 scalar:int:onboarding-version:2 scalar:boolean:allow-external-configuration:true
 scalar:boolean:enable-audio-sink:true scalar:boolean:use-aac-audio:false scalar:boolean:narrow-band-profile-cap:false
 scalar:boolean:kill-on-disconnect:false scalar:boolean:enable-floating-button:false set:connection-modes:wifi
 scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:boolean:native-poke-all-paired:true
 scalar:boolean:external-bt-zbt-transport:false scalar:boolean:wifi-direct-stable-identity:true
 scalar:boolean:wifi-direct-rotation-pending:false del:video-profile-starvation-cap del:native-poke-bt-macs
 del:native-mac del:last-connected-native-mac del:native-preferred-device-mac"
HU_KEYS="scalar:int:log-level:1 scalar:boolean:allow-external-configuration:true set:connection-modes:wifi
 scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:boolean:native-poke-all-paired:true
 scalar:boolean:external-bt-zbt-transport:false scalar:boolean:kill-on-disconnect:false del:video-profile-starvation-cap"

send() { local a=$1; shift; adb -s "$SAM" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
mark() {   # mark LABEL [HU PHONE]: the same marker line on a head unit and a phone (default D-SAM and D-POCO)
  local hu=${2:-$SAM} ph=${3:-$PH}
  adb -s "$hu" shell log -t AutomationMarker "$1"; adb -s "$ph" shell log -t AutomationMarker "$1"
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> "$EV/marks.txt"
}
write_keys() {   # every D-SAM key of section 4, app stopped, then the file read back
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
app_exit() {   # app_exit [SERIAL]: disconnect, exit, force-stop (default D-SAM)
  local s=${1:-$SAM}
  adb -s "$s" shell am broadcast -f 0x00000020 -n "$RX" -a com.andrerinas.openheadunit.ACTION_DISCONNECT >/dev/null 2>&1; sleep 5
  adb -s "$s" shell am start -a android.intent.action.VIEW -d headunit://exit >/dev/null; sleep 5
  adb -s "$s" shell am force-stop "$PKG"
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
hold_station_off() {   # after a kill: save the stand-down network id once, then delete it so the relaunch gives nothing back
  local id
  id=$(adb -s "$SAM" shell run-as "$PKG" cat shared_prefs/settings.xml | tr -d '\r' | grep -ao 'name="station-stand-down-network-id" value="[0-9]*"' | grep -ao '[0-9]*"$' | tr -d '"')   # not-literal: a settings.xml line
  [ -n "$id" ] && [ ! -s "$EV/netid.txt" ] && echo "$id" > "$EV/netid.txt"
  echo "netid_seen=${id:-none}" >> "$EV/$RUN-gate.txt"
  "$RIG_TOOLKIT/set_prefs_runas_host.py" "$SAM" "del:station-stand-down-network-id" >> "$EV/$RUN-rot-write.txt" 2>&1
}

w_cycle() {   # one W run: a session, a force-stop with the group up, ROT written, the station held off, a relaunch. Needs ARM, ROT.
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
    hold_station_off
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
  COEX=$(rl_cnt "$hu" "$r-relaunch" "$r-end" 'This unit is connected to another WiFi network while hosting')
  GIVE=$(rl_cnt "$hu" "$r-relaunch" "$r-end" 'WiFi network is enabled again and should rejoin')
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

d_keys() {   # d_keys DELAY: D-HU keys, app stopped, then the MAC set, then the file read back
  local mac
  mac=$(adb -s "$MOTO" shell settings get secure bluetooth_address | tr -d '\r' | tr 'a-f' 'A-F')
  echo "moto_mac=$mac" >> "$EV/$RUN-gate.txt"
  python3 -I "$RIG_TOOLKIT/set_hu_settings_host.py" "$DHU" $HU_KEYS "scalar:int:auto-disconnect-bt-delay-seconds:$1" > "$EV/$RUN-keys.txt" 2>&1
  adb -s "$DHU" shell cat "/data/data/$PKG/shared_prefs/settings.xml" > "$EV/$RUN-hu-pre.xml"
  python3 -I "$SCR/put_macs.py" "$EV/$RUN-hu-pre.xml" "$EV/$RUN-hu-put.xml" auto-disconnect-bt-macs "$mac" >> "$EV/$RUN-keys.txt" 2>&1
  adb -s "$DHU" push "$EV/$RUN-hu-put.xml" /data/local/tmp/hu-put.xml >/dev/null
  adb -s "$DHU" shell "cat /data/local/tmp/hu-put.xml > /data/data/$PKG/shared_prefs/settings.xml; rm -f /data/local/tmp/hu-put.xml"
  adb -s "$DHU" shell cat "/data/data/$PKG/shared_prefs/settings.xml" > "$EV/$RUN-hu-settings.xml"
}

d_cycle() {   # d_cycle off|back: a D-HU session with D-MOTO, then D-MOTO's Bluetooth off (and back after 10 s). Needs DELAY.
  local att from mode=$1
  rm -f "$EV/$RUN-thermal.txt" "$EV/$RUN-gate.txt"
  rl_thermal_gate || { echo HOST_HOT > "$EV/$RUN-thermal.txt"; return 2; }
  adb -s "$DHU" shell am force-stop "$PKG"
  d_keys "$DELAY"
  adb -s "$MOTO" shell svc power stayon true
  rl_cap_start "$RUN-hu" "$DHU"; rl_cap_start "$RUN-ph" "$MOTO"
  for att in 1 2; do
    rl_step "session, attempt $att"
    adb -s "$DHU" shell am force-stop "$PKG"
    mark "$RUN-start" "$DHU" "$MOTO"; from=$(rl_offset "$EV/$RUN-hu.txt")
    adb -s "$DHU" shell am start -n "$MAIN" >/dev/null
    if rl_wait_line "$EV/$RUN-hu.txt" 'SSL handshake complete' 150 "$from"; then
      echo "first_session=ok attempt=$att" >> "$EV/$RUN-gate.txt"; break
    fi
    echo "first_session=none attempt=$att" >> "$EV/$RUN-gate.txt"; app_exit "$DHU"
  done
  if grep -q '^first_session=ok' "$EV/$RUN-gate.txt"; then
    rl_sleep 45
    mark "$RUN-cut" "$DHU" "$MOTO"
    adb -s "$MOTO" shell svc bluetooth disable
    if [ "$mode" = back ]; then
      rl_sleep 10
      adb -s "$MOTO" shell svc bluetooth enable
    fi
    rl_sleep $(( DELAY + 10 ))
    mark "$RUN-check" "$DHU" "$MOTO"
    rl_sleep 15
  fi
  mark "$RUN-end" "$DHU" "$MOTO"
  rl_radio "$MOTO" bt on
  app_exit "$DHU"
  rl_cap_stop "$RUN-hu"; rl_cap_stop "$RUN-ph"
}

d_counts() {   # d_counts RUN: the counts every D grade reads
  local r=$1 hu="$EV/$1-hu.txt" mac
  FIRST=$(rl_cnt "$hu" "$r-start" "$r-cut" 'SSL handshake complete')
  HELD=$(rl_cnt "$hu" "$r-cut" "$r-end" 'the phone closed the held Bluetooth channel after')
  ARMED=$(rl_cnt "$hu" "$r-cut" "$r-end" 'went away; ending the session in')
  KEEP=$(rl_cnt "$hu" "$r-cut" "$r-end" 'Bluetooth auto-disconnect: not ending the session for')
  ENDED=$(rl_cnt "$hu" "$r-cut" "$r-end" 'stayed away; ending the session the way the Exit button does.')
  THR=$(rl_cnt "$hu" "$r-check" "$r-end" 'Throughput over ')
  END_S=$(python3 -I "$SCR/gap_s.py" "$hu" "AutomationMarker: $r-cut" 'stayed away; ending the session the way the Exit button does.')
  CRASH=$(grep -acF 'Process: com.andrerinas.headunitrevived' "$hu" 2>/dev/null)   # system
  mac=$(grep -ao 'moto_mac=[0-9A-F:]*' "$EV/$r-gate.txt" | head -1 | cut -d= -f2)   # not-literal: the gate file
  MACSET=$(grep -acF "<string>$mac</string>" "$EV/$r-hu-settings.xml" 2>/dev/null)   # not-literal
  MD5=$(grep -c '^md5match=1' "$EV/$r-identity.txt" 2>/dev/null)   # not-literal
}
```

**put_macs.py**
```python
# Write a string set into a SharedPreferences XML: put_macs.py IN OUT KEY VALUE... (replaces the key if present).
import sys, xml.etree.ElementTree as E
src, dst, key, values = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
tree = E.parse(src); root = tree.getroot()
for old in [n for n in root if n.get("name") == key]:
    root.remove(old)
node = E.SubElement(root, "set", {"name": key})
for v in values:
    E.SubElement(node, "string").text = v
tree.write(dst, encoding="utf-8", xml_declaration=True)
print(f"put_macs: {key} = {values}")
```

**gap_s.py**
```python
# Seconds from the first line holding A to the first later line holding B in a threadtime capture; prints -1 when missing.
import re, sys
from datetime import datetime
cap, a, b = sys.argv[1], sys.argv[2], sys.argv[3]
def ts(line):
    m = re.match(r"(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d{3})", line)
    return datetime.strptime("2026-" + m.group(1) + " " + m.group(2), "%Y-%m-%d %H:%M:%S.%f") if m else None
t0 = None
try:
    for line in open(cap, errors="replace"):
        if t0 is None and a in line:
            t0 = ts(line)
        elif t0 is not None and b in line and ts(line):
            print(int((ts(line) - t0).total_seconds())); sys.exit(0)
except OSError:
    pass
print(-1)
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

- W2 (an ordinary relaunch still reads the group): PASS in `pr-train-20261029` round 2 on the same #1209 head.
- Everything else of `pr-train-20261029`: graded, merged, or waiting on a contributor.

## 9. Report back

1. W1: L-ADOPT, L-PURGE (with its verdict text), both L-ID SSIDs, L-COEX, whether the second session formed, and
   the phone's `NETWORK_NOT_FOUND` count after the relaunch.
2. WB: L-ADOPT, both SSIDs and L-COEX (the control), the kill-to-relaunch gaps, and `$EV/netid.txt`.
3. DB and D1: L-HELD, L-ARM, L-KEEP, L-END and the seconds from the cut to L-END.
4. D2: L-BACK, and whether frames continued after the delay.
5. Cleanup: L-GIVE on D-SAM and any D-HU settings diff.
