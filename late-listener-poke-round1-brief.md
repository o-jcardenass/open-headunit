# late-listener-poke - round 1 brief

**Queue:** units D-HU (head unit) and D-POCO (phone). D-MOTO stays plugged in with its Bluetooth **off**. D-SAM and D-HP are not used. No baseline and no candidate branch: one APK, built from `origin/main`.
**Time:** 11 cycles of about 5 min each (12 if R5 runs), plus one build and the thermal gates. About 65 min. Each discard re-run adds 5 min.

## 1. Build and baseline

- **SHA:** `22392a15`, the tip of `origin/main`. History was not rewritten. Its code is the same as `28af450d`: the two commits after it change only `versionName` (now `4.0.0-alpha`, versionCode 120), `CHANGELOG.md` and `README.md`.
- **Checkout:** `git -C <wt> fetch origin && git -C <wt> checkout --detach 22392a15 && git -C <wt> rev-parse --short HEAD`. It must print `22392a15`.
- **Build:** `$RIG_TOOLKIT/rig_thermal.sh wait 70`, then `HUR_DIR=<wt> MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh`. Copy the APK out of `apks/` at once and record its md5.
- **Install:** `adb -s 27870808938846 install -r -d <apk>`. Then send `ACTION_QUERY_STATE` (§5) and check that `commit` is `22392a15`.
- **JVM suite on the dev host at `22392a15`:** 3309 tests in 362 classes, 0 failures, 0 skipped (run 2026-10-17). The rig runs no unit tests.

## 2. What this is and why

Stock head-unit apps (ZLink) project a few seconds after ignition, and we take 10 to 20 s. An investigation of both sides found these facts:

- **The phone always dials, and the head unit always listens.** Android Auto 18.0 has no RFCOMM listen call anywhere.
- **The phone starts wireless setup only on a Bluetooth event**, such as a hands-free (HFP) link connecting.
- **ZLink is already listening when that event comes.** We often start later, after the head unit's own HFP link to the phone is already up.

Our code and Android Auto's code disagree about what happens next:

- **Our code.** `HandsFreeWakeEscalationPolicy` says that a link that never changes "raises no further event, so the phone never re-triggers". So while this head unit's HFP link to the target phone is up, the poke stands down (`BluetoothWakePolicy.WakeReason.TARGET_CONNECTED`). After `ESCALATE_AFTER_MS` = 90 s, it wakes the phone anyway, at the cost of that HFP link.
- **Android Auto 18.0.** On a failed RFCOMM connect it logs `Rfcomm socket connection failed, restarting WPP using HU presence tracker.` and dials again after 5000 ms. It does this for as long as HFP or A2DP stays up, with no count limit.

If Android Auto is right, the phone finds a late listener within about 5 s, with no poke. Our 90 s escalation is then never needed in this state, and the poke does nothing useful there. This round measures it.

## 3. What is different this round

- **No code change is under test.** The round measures `origin/main`. The verdicts grade the phone's behaviour, so the phone capture is primary evidence.
- **The precondition is the head unit's own HFP link, not our app's.** D-HU's system `HeadsetClientService` holds the HFP link to D-POCO whether our app runs or not. Every "late" cycle brings that link up while our app is force-stopped. A force-stopped app gets no broadcast (`rig-quirks/topics/lifecycle.md`), so nothing of ours runs until the launch.
- **Before the launch, markers come from `log`, not from the app.** `ACTION_LOG_MARKER` would start the app's process. So before the launch, `mark` writes `adb shell log -t RIGMARK <label>` on both devices.
- **The two devices' clocks differ.** Compute every delta on one device's clock. Use a `RIGMARK` (written to both devices back to back) only to line up events across the devices.
- **On WiFi Direct, D-HU gives its group a new address on each create** (`rig-quirks/topics/wifi.md`), so it advertises no WPP-over-TCP endpoint. Every cycle should take the RFCOMM route. If a cycle's first contact is `WppTcpServer: [TX] WifiVersionRequest`, it took the TCP route: record it and grade it apart.
- **Expected INCONCLUSIVE:** a cycle where D-HU's HFP link to D-POCO does not reach Connected within 60 s. That cycle never met its precondition, and the script exits with code 3.

## 4. Settings keys

D-HU is rooted. Write the keys with `$RIG_TOOLKIT/set_hu_settings_host.py` while the app is force-stopped, then read every key back. An empty set must read `<set name="KEY" />`, not `<set name="KEY"><string /></set>` (`rig-quirks/topics/tooling.md`, the `ohu_setkeys.py` entry). `POCO` below is D-POCO's Bluetooth MAC as D-HU's bonded list shows it (P3).

Common to every run:

| Element | Why |
|---|---|
| `<int name="wifi-connection-mode" value="3" />` | Native AA |
| `<int name="native-ap-transport" value="0" />` | WiFi Direct |
| `<int name="log-level" value="2" />` | INFO. Every deciding line is `AppLog.i` or `AppLog.w`, and D-HU floods the log at DEBUG |
| `<int name="native-driver-selection-mode" value="0" />` | no driver selector |
| `<string name="native-preferred-device-mac">POCO</string>` | the target |
| `<string name="last-connected-native-mac">POCO</string>` | lets arm B escalate (`HandsFreeWakeEscalationPolicy.pairingHasRunAaHere`) |
| `<int name="native-aa-wake-damage-verdict" value="0" />` | no latched verdict |
| `<int name="native-aa-wake-armings-without-session" value="0" />` | |
| `<set name="auto-start-bt-macs" />` | no `AutoStartReceiver` match, so an arrival cannot re-arm the stack |
| `<int name="onboarding-version" value="2" />` | no wizard |

Arm A, **poke off** (`PokeTargets.None`):

| Element |
|---|
| `<set name="native-poke-bt-macs" />` |
| `<boolean name="native-poke-all-paired" value="false" />` |

Arm B, **poke on**: the default behaviour, aimed at D-POCO only:

| Element |
|---|
| `<set name="native-poke-bt-macs"><string>POCO</string></set>` |
| `<boolean name="native-poke-all-paired" value="false" />` |

Before the first run, diff `settings.xml` against a fresh backup and state the delta in Setup notes. Leave `wifi-direct-stable-identity` as found, and record its value.

## 5. Shared commands

Fill in `POCO` and `HU_MAC` from P3, then `source` this file in every shell. Write `HU_MAC` in upper case.

**llp_env.sh**

```bash
export PKG=com.andrerinas.headunitrevived
export HU=27870808938846 PH=4f4027e9 MOTO=ZY22GC3BM4
export EV=$HOME/Coding/StudioProjects/rig-data/rounds/late-listener-poke-round1
export RIG_ROUND=$EV/scripts
export POCO=<D-POCO MAC from P3>
export HU_MAC=<D-HU MAC from P3, upper case>
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { adb -s $HU shell log -t RIGMARK "$1"; adb -s $PH shell log -t RIGMARK "$1"; echo "$(date +%T.%3N) $1" >> $EV/marks.txt; }
waitline() { for i in $(seq 1 "$3"); do grep -aqF "$2" "$1" && return 0; sleep 1; done; return 1; }  # file, fixed string, cap s
phone_bt() {  # on|off; returns 0 when dumpsys agrees, after up to 5 tries 3 s apart
  for i in 1 2 3 4 5; do
    if [ "$1" = off ]; then adb -s $PH shell svc bluetooth disable; else adb -s $PH shell svc bluetooth enable; fi
    sleep 3
    s=$(adb -s $PH shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:' | tr -d '\r')
    if [ "$1" = off ] && echo "$s" | grep -q OFF; then return 0; fi
    if [ "$1" = on ] && echo "$s" | grep -q ' ON'; then return 0; fi
  done; return 1; }
phone_wifi() {  # on|off; D-POCO's WiFi self-reverts after one disable
  for i in 1 2 3 4 5; do
    if [ "$1" = off ]; then adb -s $PH shell svc wifi disable; else adb -s $PH shell svc wifi enable; fi
    sleep 3
    if [ "$1" = off ]; then adb -s $PH shell dumpsys wifi | grep -aq 'Wi-Fi is disabled' && return 0
    else adb -s $PH shell dumpsys wifi | grep -aq 'Wi-Fi is enabled' && return 0; fi
  done; return 1; }
```

- **The launch** is `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`. It arms mode 3 by itself. **Do not send `ACTION_START_WIRELESS_SCAN` after it** (template §4 step 4).
- **The phone** leaves the link only by `svc`, never by airplane mode.

**hfp_state.sh** prints `CONNECTED` or `NOT` for D-HU's own hands-free client link to `$POCO`, and saves the section it read:

```bash
#!/bin/bash
# usage: hfp_state.sh <out-file>
adb -s "$HU" shell dumpsys bluetooth_manager > /tmp/llp-bt.txt
awk '/HeadsetClientService/{f=1} f&&/^ *Profile:/&&!/HeadsetClient/{f=0} f' /tmp/llp-bt.txt > "$1"
if grep -aiqF "$POCO" "$1" && grep -aiqE 'curState.*Connected|state: *CONNECTED' "$1"; then echo CONNECTED; else echo NOT; fi
```

If the saved section names no state that this match can read, send the raw section to the host and stop. The host sets the match from the real text and records the change in Setup notes.

## 6. The deciding lines

Every head-unit string was checked with `git grep -F` at `22392a15`. Grep every capture with `-a`. Grep the MAC with `-aiF`, because a bonded list and a log can differ in case.

Head unit capture, `$EV/<run>-c<n>-hu.txt`:

| Id | Fixed string | Source |
|---|---|---|
| H1 | `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | `NativeAaHandshakeManager.kt` |
| H2 | `createGroup SUCCESS!` | `WifiDirectManager.kt` |
| H3 | `NativeAA: Connection accepted from` | `NativeAaHandshakeManager.kt` |
| H4 | `SSL handshake complete` | `AapSslContext.kt` |
| H5 | `NativeAA: Attempting active poke to device` | `NativeAaHandshakeManager.kt` |
| H6 | `NativeAA: Calling socket.connect()` | `NativeAaHandshakeManager.kt`, a real poke dial |
| H7 | `NativeAA: Not poking` | `noteHandsFreePokeSkip`, the stand-down, at INFO once per device |
| H8 | `despite the hands-free` | `escalateHandsFreeWake`. The runtime line joins two literals (`... despite the hands-free ` + `link ...`), so grep this first half |
| H9 | `NativeAA: No wake poke phone selected, so nothing is poked.` | arm A's proof that the poke is off |
| H10 | `WppTcpServer: [TX] WifiVersionRequest` | a TCP-route contact |
| H11 | `WifiLauncher: Initializing WiFi Mode:` | the arm |
| H12 | `MATCH! Starting AapService` | discard rule |
| R | `RIGMARK` | our marks before and after the launch |
| E | `AutomationMarker: <run>-c<n>-end` | the cycle end |

Phone capture, `$EV/<run>-c<n>-ph.txt`. These strings come from the Android Auto 18.0 decompile. Record the phone's Android Auto version (P4), because these strings change between builds:

| Id | Fixed string | Android Auto source |
|---|---|---|
| P-dial | `Creating rfcomm socket for device: <HU_MAC>` | `oyc.java:48`, tag `GH.WIRELESS.BT` |
| P-fail | `Rfcomm socket connection failed, restarting WPP using HU presence tracker.` | `pva.java:1333` |
| P-edge | `Connection action:` | `WifiBluetoothReceiver.java:237`, tag `GH.WifiBluetoothRcvr` |

If P-fail never prints but P-dial repeats, grade on P-dial and say so. The failure line can log below the build's visible level.

## 7. Runs

### Preparation (once, before R1)

- **P1.** Back up `settings.xml`. Diff it against the last round's file and record the delta.
- **P2.** On D-MOTO, run `adb -s $MOTO shell svc bluetooth disable`. Read it back as off with `adb -s $MOTO shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'`. D-HU serves one HFP link at a time (`rig-quirks/units/D-HU.md`).
- **P3.** Read both bonded lists, and set `POCO` and `HU_MAC` in `llp_env.sh`. `HU_MAC` is the entry on D-POCO whose name matches D-HU's adapter name. If D-POCO's list holds another rig head unit's radio, stop and ask the operator to forget it on D-POCO (`rig-quirks/units/D-POCO.md`).

  **llp_bonded.sh**

  ```bash
  #!/bin/bash
  adb -s 27870808938846 shell settings get secure bluetooth_name
  adb -s 27870808938846 shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices'
  adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices'
  ```
- **P4.** Run `adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a versionName` and record the result.
- **P5.** Run `adb -s $PH shell dumpsys window | grep mCurrentFocus`. If the focus is not the launcher, run `adb -s $PH shell input keyevent KEYCODE_HOME`.

### The cycle

Before each arm, write that arm's keys (§4) with the app stopped, and read them back. Then run one cycle per command.

**llp_cycle.sh** takes the run id, the cycle number, the arm (`A` or `B`), the order (`late` or `early`), the hold in seconds, and optionally the session wait in seconds (default 90). Exit 0 means graded, 3 means no HFP and 4 means a radio would not switch.

```bash
#!/bin/bash
set -u
source "$(dirname "$0")/llp_env.sh"
: "${EV:?}" "${RIG_ROUND:?}" "${POCO:?}" "${HU_MAC:?}"
RUN=$1; C=$2; ARM=$3; ORDER=$4; HOLD=$5; WAIT=${6:-90}
T=$EV/$RUN-c$C
$RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || exit 5
$RIG_TOOLKIT/rig_thermal.sh watch $EV/thermal-$RUN-c$C.log & echo $! > $T-th.pid

cleanup() { # stop captures and the thermal watch; exit cleanly if the app runs
  kill "$(cat $T-hu.pid)" "$(cat $T-ph.pid)" "$(cat $T-th.pid)" 2>/dev/null
  adb -s $HU shell pidof $PKG >/dev/null && { adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit"; sleep 3; }
  adb -s $HU shell am force-stop $PKG; }

# 1. phone off the link, read back
phone_wifi off && phone_bt off || { echo NO_RADIO_OFF > $T-gate.txt; kill "$(cat $T-th.pid)"; exit 4; }

# 2. head unit stopped, captures on
adb -s $HU shell am force-stop $PKG
adb -s $HU logcat -G 16M; adb -s $PH logcat -G 16M
adb -s $HU logcat -c; adb -s $PH logcat -c
stdbuf -oL adb -s $HU logcat -v time > $T-hu.txt & echo $! > $T-hu.pid
stdbuf -oL adb -s $PH logcat -v time > $T-ph.txt & echo $! > $T-ph.pid

if [ "$ORDER" = late ]; then
  # 3L. phone on first, while our app is stopped
  mark "$RUN-c$C-phone-on"
  phone_bt on && phone_wifi on || { echo NO_RADIO_ON > $T-gate.txt; cleanup; exit 4; }
  ok=0; for i in $(seq 1 30); do [ "$($RIG_ROUND/hfp_state.sh $T-hfc.txt)" = CONNECTED ] && ok=1 && break; sleep 2; done
  if [ $ok = 0 ]; then mark "$RUN-c$C-no-hfp"; echo NO_HFP > $T-gate.txt; cleanup; exit 3; fi
  mark "$RUN-c$C-hfp-up"
  sleep "$HOLD"                    # the late gap is what the run measures
  mark "$RUN-c$C-launch"
  adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity
else
  # 3E. listener first, phone after (the control)
  mark "$RUN-c$C-launch"
  adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity
  waitline $T-hu.txt 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 30
  waitline $T-hu.txt 'createGroup SUCCESS!' 30
  sleep 15                         # head unit settled before the phone (rig-quirks/topics/bt.md)
  mark "$RUN-c$C-phone-on"
  phone_bt on && phone_wifi on || { echo NO_RADIO_ON > $T-gate.txt; cleanup; exit 4; }
fi

# 4. wait for the session
waitline $T-hu.txt 'SSL handshake complete' "$WAIT" || mark "$RUN-c$C-no-ssl"
sleep 5
send ACTION_LOG_MARKER --es text "$RUN-c$C-end"
sleep 1
cleanup
exit 0
```

Put `llp_env.sh`, `hfp_state.sh` and `llp_cycle.sh` in `$RIG_ROUND` and make them executable. The script never uses the arm argument itself: the arm is the settings write before it, and the argument only labels the cycle.

### Extract for every cycle (Haiku returns these numbers, and Sonnet grades)

Head unit capture:
- the first timestamp of H1, H2, H3, H4 and H10;
- the timestamp of every `RIGMARK` line and of E;
- the counts of H5, H6, H7, H8, H9, H11 and H12, and the count of H6 lines before the first H3.

Phone capture:
- every P-dial timestamp between `-hfp-up` and `-launch`;
- the count of P-dial lines in the 60 s before `-launch`;
- the first P-dial timestamp after `-launch`;
- the count of P-fail lines;
- the MAC in every P-dial line, compared with `HU_MAC` (`-aiF`);
- every P-edge line, with its timestamp.

Also give the exit code of `llp_cycle.sh`, and the contents of `$T-gate.txt` if it exists.

Derived, each on one device's clock:
- **D1** = H3 minus H1, on the head unit clock. This is the round's number.
- **D2** = H4 minus H1, on the head unit clock.
- **D3** = H3 minus `-phone-on`, on the head unit clock (R4 only).
- **G** = the gaps between consecutive P-dial lines before `-launch`, on the phone clock (R1 and R3).

### Discard rules (every cycle)

Discard the cycle and run it again once if the head unit capture has an H12, more than one H2 or more than one H4, or if a P-dial names a MAC other than `HU_MAC`. Two discards in one run make that run INCONCLUSIVE.

### R1: late listener, poke off (**the point of the round**)

Arm A, order `late`, hold 30 s, cycles 1 to 3: `$RIG_ROUND/llp_cycle.sh R1 <n> A late 30`.

- **Reachability:** a cycle counts only if it exited 0, the phone dialled at least twice before `-launch` (P-dial ≥ 2), and H9 printed. A cycle that fails this is INCONCLUSIVE, because the late state never existed. A PASS with no dials before the launch would only mean the phone had not started yet.
- **PASS:** every counted cycle has an H3 with D1 ≤ 15 s, and H5 = H6 = 0.
- **FAIL:** a counted cycle has no H3 within 90 s of H1, or D1 > 15 s.
- **Report:** D1, D2 and G per cycle.

### R2: late listener, poke on

Arm B, order `late`, hold 30 s, cycles 1 to 3: `$RIG_ROUND/llp_cycle.sh R2 <n> B late 30`.

- **Reachability:** as in R1, but H7 (`Not poking`) must print instead of H9. That proves the stand-down was in force.
- **PASS:** every counted cycle has an H3 with D1 ≤ 15 s, and no H6 before the first H3.
- **FAIL:** a counted cycle has an H6 before the first H3, or no H3 within 90 s of H1.
- **Report:** D1 per cycle, beside R1's. Also report whether H8 printed. It should not, because H3 comes long before 90 s.

### R3: long late gap, poke off

Arm A, order `late`, hold 150 s, cycles 1 and 2: `$RIG_ROUND/llp_cycle.sh R3 <n> A late 150`.

This run asks whether the phone still redials after more than two minutes of a held link.

- **PASS:** in every cycle, the phone dialled at least twice in the 60 s before `-launch`, and an H3 has D1 ≤ 15 s.
- **FAIL:** the phone made no dial in the 60 s before `-launch`, or no H3 came within 90 s of H1.
- **Report:** the timestamp of the last P-dial before `-launch`. If R3 FAILs, the phone gives up on a held link, and our escalation is right for long gaps.

### R4: control, listener first, poke off

Arm A, order `early`, cycles 1 to 3: `$RIG_ROUND/llp_cycle.sh R4 <n> A early 0`.

This is the order ZLink runs in: our listener and group are up before the phone's Bluetooth event.

- **PASS:** every cycle that exited 0 has an H3 with D3 ≤ 15 s.
- **Report:** D3, and H4 minus `-phone-on`, per cycle. This is the best time this unit reaches when we are ready first.

### R5: escalation, only if R3 FAILs

Run R5 only if R3 FAILs: arm B, order `late`, hold 150 s, session wait 150 s, 1 cycle: `$RIG_ROUND/llp_cycle.sh R5 1 B late 150 150`. If R3 PASSes, record R5 as not run.

- **PASS:** H8 printed, an H6 came after it, and the first H3 came within 30 s of that H6.
- **FAIL:** no H8 within 150 s of H1, or no H3 within 30 s of the H6.

### Stop rule

When two cycles in a row of one run exit 3, mark that run INCONCLUSIVE (no HFP) and go to the next run. If both R1 and R2 end that way, stop the round and report the saved `$T-hfc.txt` sections. A cycle that exits 4 counts the same as exit 3.

## 8. Do not re-run

No run on this thread is settled yet. These are out of scope, because other threads cover them: group creation, the poke's own success rate, and the session's video.

## 9. Report back

1. R1's D1 per cycle: does the phone find a late listener with the poke off?
2. R3's last P-dial time against `-launch`: does the phone keep redialing a held link past 2 minutes?
3. R4's D3 against R1's D1: how many seconds being late costs.
