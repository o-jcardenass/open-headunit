# call-audio-route, round 1 brief

A **measurement round** on `main`. No fix is on trial. The round asks one question: does the motorcycle claim (Vehicle Type Motorcycle, Head unit microphone off) change where a **phone call's** audio goes? It also records where the **assistant's** microphone goes in the same layouts.

**The point of the round is the pair I-car and I-moto** (intercom on, both vehicle types). H-car and H-moto are the same pair without the intercom.

## 1. Build and baseline

- Build: `main` @ **`28d73e2f`** (`28d73e2fcb020e064437bb0dfd95f517083d26f9`). No history rewrite. No baseline APK: every comparison is between settings arms on one build.
  ```bash
  git fetch origin main
  git checkout -B cra-r1 28d73e2fcb020e064437bb0dfd95f517083d26f9
  git rev-parse HEAD      # must print 28d73e2fcb020e064437bb0dfd95f517083d26f9
  ```
- Build with `build_hur.sh` (`--max-workers=2`, host below 70C). Copy the APK to `round-call-audio-route-r1/main-28d73e2f.apk` at once and record its md5. Run `run_unit_tests.sh`; record the total. A build or test failure stops the round.
- Back up D-HU's `settings.xml` first, then `adb -s 27870808938846 install -r <apk>`, then diff `settings.xml` against the backup and reseed anything the install wiped (`tooling.md`).
- `send ACTION_QUERY_STATE` must reply with a `commit` that begins `28d73e2f`.

## 2. What this is and why

A user runs a head unit whose own Bluetooth hands-free stack gives poor call audio. The user asks if the app can carry call audio over Android Auto instead. The engineer's position: the motorcycle claim plus the head unit microphone off may move calls to the phone and to a helmet intercom, as it did for the assistant in `headunit-info-round2-results.md` R4.

What is already measured:

- `headunit-info-round2-results.md` R2 to R4: with the microphone off, the phone builds `GH.PhoneMicRecorder` and runs `HeadsetService: startVoiceRecognition: device=<intercom>`. The assistant worked through the intercom. **No call was placed in that round.**
- `hold-aa-rfcomm-round1-addendum3-results.md`: with no hands-free link to the head unit, 6 of 6 calls stayed on the phone, and Gearhead logged `GH.CallManager: No high priority audio routes available`.
- Android Auto's audio stream types are SPEECH, SYSTEM, MEDIA, ALARM, GUIDANCE, ANNOUNCEMENT and RING (`app/src/main/proto/media.proto`). None is a call stream.

The desk prediction, which this round tests:

| Run | Call route on D-MOTO | Assistant recorder | Assistant SCO device |
|---|---|---|---|
| H-car | Bluetooth to D-HU | `GH.CarMicRecorder` | none |
| H-moto | Bluetooth to D-HU (**unchanged**) | `GH.PhoneMicRecorder` | **D-HU** |
| I-car | Bluetooth to D-MOTO's active hands-free device | `GH.CarMicRecorder` | none |
| I-moto | the same device as I-car (**unchanged**) | `GH.PhoneMicRecorder` | the intercom, if it is the active device |

**The engineer's claim is confirmed** if a moto run's call route differs from its car twin (same active device). **It is refuted** if the twins route the call to the same device.

## 3. What is different about this round

- **Units.** D-HU (head unit, `wifi-connection-mode=3`, PC cable), D-MOTO (phone, has a SIM, wireless or cable adb), the operator's own phone (rings D-MOTO), the **KY Pro intercom** (paired to D-MOTO in `headunit-info` round 2). **D-POCO: Bluetooth off** for the whole round (`bt.md`, D-POCO takes the poke rotation).
- **The phone's radios stay up between runs.** This is a deliberate deviation from §4 step 1. Airplane mode drops the intercom link, and a live link survives head-unit restarts (`bt.md`). Each run restarts only the head unit app.
- **D-HU holds a hands-free link to D-MOTO in every run.** Native AA needs it (the phone gates wireless setup on it). So "intercom on" means two hands-free links on D-MOTO. Which one the phone calls the active device decides a call's route, so every run reads it before the ring.
- **D-HU has no speaker.** The call half on D-HU is graded from Bluetooth state, never by ear. The intercom half has a sensory report from the operator, marked as such.
- **Each vehicle type gets its own Android Auto record** (`VehicleIdentityPolicy` appends `-moto`), so switching arms needs no "forget car". Gearhead 17.9 may not answer `dumpsys activity service com.google.android.projection.gearhead` (`bluetooth-audio-disabled-usb-connect-round1-results.md`). The run gate therefore uses `GH.Assistant.Recorder: Using phone microphone` / `Not using phone mic`, and the `CarInfoInternal` record is reported only if the dump returns one.

### Hand steps (one batched pre-flight ask, then none unless cued)

No rig device can place a call to D-MOTO and no verb can switch an intercom on or off. Ask the operator once, before R0:

1. Have your own phone ready to ring D-MOTO's number when the script cues `RING`, and hang up from your phone when it cues `HANG UP`.
2. Have the KY Pro charged and **off**. Switch it on only at the cue `INTERCOM ON`.
3. Wear or hold the intercom in the I runs. At each `LISTEN` cue, say into the intercom "testing one two three" and report: did you hear the caller in the intercom (yes/no), and did your own phone hear you (yes/no)?
4. Unlock D-MOTO once and leave it unlocked (`D-MOTO.md`).

## 4. Settings keys

Only these change. Everything else stays at the round-start backup.

| Key | Type | H-car, I-car | H-moto, I-moto |
|---|---|---|---|
| `use-head-unit-microphone` | boolean | `true` | `false` |
| `vehicle-type` | int | `1` | `3` |
| `log-level` | int | `2` | `2` |

Read back and report, do not change unless wrong: `wifi-connection-mode` (must be 3), `enable-audio-sink` (must be `true`), `bt-address` (must be D-HU's own MAC, else Android Auto keeps calls on the phone; if it is empty, write D-HU's MAC from `adb -s $DH shell settings get secure bluetooth_address`), `native-poke-bt-macs` (must hold D-MOTO's MAC; it is a `<set>`, so if it is wrong rebuild the file on the host per §1), `key-codes` (must be absent, the assistant trigger needs it).

## 5. Harness

Copy `th_gate`, `th_watch` and `th_report` from `ptr_lib.sh` (`projection-teardown-and-relays-round1-brief.md`). Then:

```bash
PKG=com.andrerinas.headunitrevived
DH=27870808938846; PH=ZY22GC3BM4; PO=4f4027e9
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
RC=$PKG/com.andrerinas.openheadunit.app.RemoteControlReceiver
OUT=round-call-audio-route-r1; mkdir -p $OUT
send() { a=$1; shift; adb -s $DH shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s $PH shell log -p w -t RIGMARK "$1"; sleep 0.3; }
cue()  { printf '\a\n>>> OPERATOR: %s\n' "$1"; echo "$(date +%T) $RUN $1" >> $OUT/hand-steps.log; }

cat > $OUT/cra_keys.sh <<'EOF'
f=/data/data/com.andrerinas.headunitrevived/shared_prefs/settings.xml
u=$(stat -c %U /data/data/com.andrerinas.headunitrevived)
for k in use-head-unit-microphone vehicle-type log-level; do
  sed -i -E "s#<[a-z]+ name=\"$k\"[^>]*/>##g" $f
done
sed -i "s|</map>|<boolean name=\"use-head-unit-microphone\" value=\"$1\" /><int name=\"vehicle-type\" value=\"$2\" /><int name=\"log-level\" value=\"2\" /></map>|" $f
chown $u:$u $f; chmod 660 $f
grep -o 'use-head-unit-microphone[^/]*\|vehicle-type[^/]*\|log-level[^/]*' $f
EOF
adb -s $DH push $OUT/cra_keys.sh /data/local/tmp/

# keys <true|false> <1|3> : app stopped, keys written as root, owner restored, read back
keys() { adb -s $DH shell am start -a android.intent.action.VIEW -d "headunit://exit"; sleep 3
  adb -s $DH shell am force-stop $PKG; sleep 1
  adb -s $DH shell sh /data/local/tmp/cra_keys.sh "$1" "$2" | tee $OUT/$RUN.keys; }

cap_start() { adb -s $DH logcat -G 16M; adb -s $PH logcat -G 16M
  adb -s $DH logcat -c; adb -s $PH logcat -c
  stdbuf -oL adb -s $DH logcat -v time > $OUT/$RUN.dhu.txt & DHP=$!
  stdbuf -oL adb -s $PH logcat -v time > $OUT/$RUN.moto.txt & PHP=$!; sleep 1; }
cap_stop() { kill $DHP $PHP; sleep 1; date +%T >> $OUT/$RUN.killtime; }

# links <tag> : who holds hands-free on each side, and which device D-MOTO calls active
links() { adb -s $PH shell dumpsys bluetooth_manager > $OUT/$RUN.$1.moto-bt.txt; sleep 0.3
  adb -s $DH shell dumpsys bluetooth_manager > $OUT/$RUN.$1.dhu-bt.txt; sleep 0.3
  echo -e "$RUN\t$1\tactive=$(grep -a -m1 'mActiveDevice' $OUT/$RUN.$1.moto-bt.txt | tr -d '\r' | sed 's/^ *//')" | tee -a $OUT/links.tsv; }

# up : launch and wait up to 120 s for the session; a second chance with the WiFi button verb
up() { mark "$RUN-start"; adb -s $DH shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity
  for i in $(seq 1 60); do grep -aq 'SSL handshake complete' $OUT/$RUN.dhu.txt && break; sleep 2
    [ $i = 30 ] && send ACTION_START_WIRELESS_SCAN; done
  grep -aq 'SSL handshake complete' $OUT/$RUN.dhu.txt && mark "$RUN-ssl" || mark "$RUN-nossl"; sleep 15; }

# assist : one assistant trigger with the projection in front, 15 s to settle
assist() { send ACTION_RAISE_PROJECTION; sleep 2; mark "$RUN-assist"
  adb -s $DH shell am broadcast -n $RC -a com.android.music.musicservicecommand --es command voice; sleep 15
  mark "$RUN-assist-end"; }

# call : one incoming call, answered on D-MOTO, held 20 s, routes read 5 s after answer
telst() { adb -s $PH shell dumpsys telephony.registry | grep -aoE 'mCallState=[0-9]' | cut -d= -f2 | sort -n | tail -1 | tr -d '\r'; }
call() { local e; links pre-ring; mark "$RUN-call"; cue "RING D-MOTO now"
  e=$((SECONDS+90)); while [ $SECONDS -lt $e ] && [ "$(telst)" != 1 ]; do sleep 2; done
  if [ "$(telst)" != 1 ]; then echo -e "$RUN\tno-ring-in-90s" | tee -a $OUT/calls.tsv; mark "$RUN-call-end"; return 1; fi
  mark "$RUN-ringing"; sleep 4; adb -s $PH shell input keyevent KEYCODE_CALL; sleep 4
  if [ "$(telst)" != 2 ]; then cue "answer the call on D-MOTO's screen"
    e=$((SECONDS+30)); while [ $SECONDS -lt $e ] && [ "$(telst)" != 2 ]; do sleep 2; done; fi
  mark "$RUN-answered"; sleep 5
  adb -s $PH shell dumpsys telecom > $OUT/$RUN.moto-telecom.txt; sleep 0.3
  links in-call
  [ "${RUN#I}" != "$RUN" ] && cue "LISTEN: speak into the intercom, then report what you heard"
  sleep 15; cue "HANG UP from your phone"
  e=$((SECONDS+60)); while [ $SECONDS -lt $e ] && [ "$(telst)" != 0 ]; do sleep 2; done
  [ "$(telst)" != 0 ] && adb -s $PH shell input keyevent KEYCODE_ENDCALL
  mark "$RUN-call-end"
  echo -e "$RUN\troute=$(grep -aoE "$ROUTE_RE" $OUT/$RUN.moto-telecom.txt | head -3 | tr '\n' ' ')" | tee -a $OUT/calls.tsv; }
```

## 6. Deciding lines, verbatim

Head unit lines were checked with `grep -F` on `28d73e2f`. Phone lines are Gearhead's and Android's, quoted from earlier results; report the Gearhead version, because its strings drift (`gearhead.md`).

| Line | File | Level / source |
|---|---|---|
| `SSL handshake complete` | `$RUN.dhu.txt` | app, INFO |
| `Head unit microphone is off in Settings. Skipping the microphone` | `$RUN.dhu.txt` | app, INFO; moto arms only |
| `AapTransport: not taking the microphone` | `$RUN.dhu.txt` | app, INFO; moto arms only |
| `Mic request:` | `$RUN.dhu.txt` | app, INFO; car arms after a trigger |
| `BT MAC Address is empty` | `$RUN.dhu.txt` | app, INFO; must be 0 |
| `GH.Assistant.Recorder: Using phone microphone` | `$RUN.moto.txt` | Gearhead 17.5; moto arms |
| `GH.PhoneMicRecorder` / `GH.CarMicRecorder` | `$RUN.moto.txt` | Gearhead, the recorder built on a trigger |
| `HeadsetService: startVoiceRecognition: device=` | `$RUN.moto.txt` | Android; the device the assistant opened SCO to |
| `GH.CallManager: No high priority audio routes available` | `$RUN.moto.txt` | Gearhead; expected 0 |

Count each one in the run's whole capture with `grep -ac`. For the SCO device, quote every `startVoiceRecognition: device=` line and say which unit's MAC it names (D-HU, the intercom, or other).

## 7. Runs

### R0. Gate and calibration (no session)

1. Build gate per §1. Host temperature per `th_gate`.
2. `adb -s $PO shell svc bluetooth disable`; confirm with `adb -s $PO shell dumpsys bluetooth_manager | grep -a -m1 'state:'`.
3. Record: D-MOTO's Gearhead version (`adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName`), D-HU's MAC, the KY Pro's MAC (from D-MOTO's bonded list), and the keys in §4.
4. `RUN=R0; keys true 1; cap_start; mark R0-start; links idle`. The app stays stopped. Then `ROUTE_RE='CallAudioState[^]]*\]|activeBluetoothDevice[^,]*|[Aa]ctive[A-Za-z]*Route[^,]*'` and `call`.
5. **Host step:** read `$OUT/R0.moto-telecom.txt` and confirm `ROUTE_RE` names the route and the Bluetooth device. If not, pick a regex that does and use it for every later run. Compare `$OUT/R0.idle.dhu-bt.txt` with `$OUT/R0.in-call.dhu-bt.txt` and pick `SCO_RE`, a regex in D-HU's hands-free client section that matches in-call and not idle. If no field changes, the D-HU side rests on D-MOTO's route only; say so.
6. `mark R0-end; cap_stop`.

R0 is valid when one call rang and was answered. Two `no-ring-in-90s` (one retry) make the call half of the whole round UNTESTABLE; run the assistant halves only.

### R1. H-car (control, intercom off)

```bash
RUN=H-car; th_gate; keys true 1; cap_start; up; links session; assist; call; mark H-car-end; cap_stop
```

### R2. H-moto (intercom off)

```bash
RUN=H-moto; th_gate; keys false 3; cap_start; up; links session; assist; call; mark H-moto-end; cap_stop
```

Then: `cue "INTERCOM ON"`. Wait until `adb -s $PH shell dumpsys bluetooth_manager` shows the KY Pro connected for hands-free (up to 60 s). If it never does, I-car and I-moto are UNTESTABLE.

### R3. I-moto (the point of the round)

```bash
RUN=I-moto; th_gate; keys false 3; cap_start; up; links session; assist; call; mark I-moto-end; cap_stop
```

### R4. I-car (the twin of R3)

```bash
RUN=I-car; th_gate; keys true 1; cap_start; up; links session; assist; call; mark I-car-end; cap_stop
```

**Twin rule.** I-car and I-moto must show the same `active=` in `links.tsv` at `pre-ring`. If they do not, `cue "switch the intercom off and on"`, wait 60 s, and re-run the later run of the pair. After two tries with a mismatch, mark the pair INCONCLUSIVE (active device not held) and report both values. The same rule applies to H-car and H-moto.

### Validity of a run (each must hold, else re-run once, then INCONCLUSIVE)

1. `SSL handshake complete` count ≥ 1 and `$RUN-ssl` marker present; no discard-rule hit (§4).
2. `BT MAC Address is empty` = 0.
3. Moto arms: `Head unit microphone is off in Settings. Skipping the microphone` ≥ 1. Car arms: 0.
4. Moto arms: `Using phone microphone` ≥ 1 in `$RUN.moto.txt`, or, if Gearhead does not print it, `GH.PhoneMicRecorder` ≥ 1 and `GH.CarMicRecorder` = 0 after the trigger. Car arms: `GH.PhoneMicRecorder` = 0.
5. One call rang and was answered (`$RUN-answered` marker, `telst` was 2).

### What each run reports (a row each)

| Field | Source |
|---|---|
| `active=` at `session`, `pre-ring`, `in-call` | `links.tsv` |
| call route | `calls.tsv`, the `ROUTE_RE` match |
| D-HU in-call SCO state | `grep -ac "$SCO_RE"` in `$RUN.in-call.dhu-bt.txt` |
| recorder on the trigger | counts of `GH.PhoneMicRecorder`, `GH.CarMicRecorder` |
| SCO device of the assistant | every `startVoiceRecognition: device=` line, mapped to D-HU / intercom / other |
| `Mic request:` on D-HU | count |
| `No high priority audio routes available` | count |
| I runs only: operator heard the caller in the intercom / the caller heard the operator | the operator's words, marked sensory |

## 8. Do not re-run

- The motorcycle claim with the head unit microphone **on** (`vehicle-type=3`, `use-head-unit-microphone=true`). `headunit-info` round 1 measured it: the phone still built `GH.CarMicRecorder`.
- Assistant recognition end to end through the intercom. `headunit-info` round 2 R3 and R4 measured it. A trigger is enough here; no spoken query is needed.

## 9. Report back

1. **I-car against I-moto: the call route and the `active=` device of each.** Same route means the motorcycle claim does not move calls.
2. **H-car against H-moto: the call route and D-HU's in-call SCO state.** This is the layout of the user who asked.
3. **H-moto's assistant SCO device.** If it is D-HU, the motorcycle claim moves the assistant onto the head unit's own hands-free audio in that layout.
4. The operator's sensory report for I-car and I-moto, kept apart from the counts.

Results go in `call-audio-route-round1-results.md` (§7 format). Captures go to release `rig-evidence-call-audio-route` as `call-audio-route-round1-captures.zip`, with its sha256. At round end, restore D-HU's `settings.xml` from the backup as root (`D-HU.md`) and re-enable D-POCO's Bluetooth.
