# hold-aa-rfcomm, round 1 addendum 3 brief: an incoming call against band, hold and stand-in link

## 1. Build and baseline

- Candidate only: branch `fix/hold-aa-rfcomm` on the fork, SHA **`c3c5a2d8`**, unchanged since round 1. No control build: the hold switch is the control.
  ```bash
  git fetch fork fix/hold-aa-rfcomm
  git checkout c3c5a2d8
  ```
- Install it on **D-POCO** and **D-SAM** (D-SAM with `install_and_launch.sh`, `HU=30041c35642d2200`, §7a).
- The switch is `native-aa-hold-bluetooth-channel`:
  - `true` (the default) holds the phone's Android Auto RFCOMM channel for the whole session;
  - `false` releases it the moment the session lands, as builds before this one did.

## 2. What this is and why it exists

- **Addendum 1 (D-SAM head unit, D-POCO phone)** lost the session within 7 s of the phone starting to ring, and survived the same call with D-SAM's own Bluetooth off. It then proposed releasing the channel on units that use the app's stand-in hands-free record. Three things were not recorded:
  - which switch position that run used;
  - which Bluetooth links were up when the phone rang;
  - where the call audio went.

  No capture was published.
- **Addendum 2 (D-POCO head unit, D-MOTO phone)** held through three calls. Its own captures show that no Bluetooth link to the phone was up at any of the rings:
  - the session formed over WPP-over-TCP (`WppTcpServer: connection from 192.168.49.9`) from an endpoint stored in an earlier round, so there was no `Handling handshake for` line and the hold never engaged;
  - the stand-in hands-free link lived only inside the 15 s wake poke, and the phone logged `ConnectionStateCallback 0` a minute before the first ring;
  - the group was on 5 GHz.

  So it measured a call with nothing under test in play.
- **The live suspect is a 2.4 GHz link.** The only collapse was on 2.4 GHz. Both controls so far (D-HU in round 1, D-POCO in addendum 2) ran 5 GHz. Bluetooth and a 2.4 GHz WiFi link share one radio's airtime; a 5 GHz link does not.
- **This round separates three candidates, one variable per run:**
  - the band;
  - the held channel;
  - a stand-in hands-free link at the ring.

  Every run records which Bluetooth links were actually up when the phone rang, so no result can be read without its precondition again.

## 3. What is different about this round

- **Two blocks, run in order. Block A first.**
  - **Block A:** D-POCO (`4f4027e9`) is the head unit and D-MOTO (`ZY22GC3BM4`) is the phone. This is the band and hold grid.
  - **Block B:** D-SAM (`30041c35642d2200`) is the head unit and D-POCO is the phone. This reproduces addendum 1 with the instruments it lacked.
- **D-HU stays out of the round.** Before Block A, turn its Bluetooth off (`adb -s 27870808938846 shell svc bluetooth disable`), or leave it unplugged and powered off. Gearhead caches the last head unit's MAC and will dial D-HU instead of the unit under test (§7a).
- **The role swap between blocks needs `headunit://exit` on D-POCO, never a bare force-stop.** A force-stop leaves D-POCO's P2P group up as a Group Owner at `192.168.49.1`, and the next phone-side run then talks to itself.
  ```bash
  adb -s 4f4027e9 shell am start -a android.intent.action.VIEW -d headunit://exit
  ```
- **Force the Bluetooth handshake on D-POCO (Block A).** D-MOTO holds a WPP-over-TCP record for D-POCO from an earlier round, and it will take that route and skip the handshake. Setting `wifi-direct-stable-identity=false` and deleting the saved group keys (§4) makes D-POCO present a group the phone has never seen. The endpoint is then withheld because the identity is unproven, so no new record is stored. Two things follow and are expected:
  - the stale record still makes the phone log `CONNECTED_TO_WRONG_SSID` beside the Bluetooth handshake;
  - the group can re-randomise and re-handshake two or three times before the session settles.

  **The §4 discard rule on a second `createGroup SUCCESS` applies only after `SSL handshake complete` in this round.**
- **Block A precondition gate.** A run counts only if its HU capture has:
  - at least one `Handling handshake for`, and
  - zero `WppTcpServer: connection from` before SSL.

  Otherwise grade it **INCONCLUSIVE (TCP route)** and stop Block A. Escalate for approval to run `pm clear com.google.android.projection.gearhead` on D-MOTO, which wipes Android Auto's data (§7a).
- **D-POCO cannot stand its station down.** On Android 15 `StationStandDownPolicy.isAvailable` is false. A 2.4 GHz group beside a station joined on 5 GHz is a split that has cost sessions before. Record the station's frequency per run (§5). A run whose picture is not steady before the ring is graded **INCONCLUSIVE (link unfit)**, not held or collapsed. Steady means every `Throughput over` line in the 60 s before the ring marker reads 20 fps or more.
- **D-SAM:**
  - charge it from a separate supply before Block B (its USB port is data-only);
  - check its clock with `adb -s 30041c35642d2200 shell date` against the host's `date`, and put the offset in Setup notes;
  - write its settings with the host `python3` edit, then push and `run-as ... cp` (§7a, D-SAM only).
- **Do Not Disturb off on the phone, every run.** Addendum 1 lost a run to it. Before each arm:
  ```bash
  adb -s <phone> shell settings get global zen_mode      # must read 0
  adb -s <phone> shell cmd notification set_dnd off      # if it does not
  ```
- **Two hand steps, and why no verb exists:**
  - **The call.** The operator dials the phone from a handset outside the rig. Nothing on the rig can make a phone ring.
  - **The audible check.** The operator says whether the caller's voice came out of the head unit, and the executor stamps it as a marker.

  Answering and hanging up are key events on the **phone** (`KEYCODE_CALL`, `KEYCODE_ENDCALL`), not taps on the app.
- **Log level DEBUG** (`log-level=1`). `HFP RX (` is an unguarded `AppLog.d`; every other app line below prints at INFO. VERBOSE is not needed and would wrap D-SAM's buffer. Run `adb logcat -G 16M` on every device first.

## 4. Settings

All with the app stopped, backed up first (§1). Read each key back before launching.

**Both head units, every run:**

| Key | Element |
|---|---|
| `log-level` | `<int name="log-level" value="1" />` |
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` |
| `native-aa-hold-bluetooth-channel` | per run, `<boolean name="native-aa-hold-bluetooth-channel" value="true" />` or `"false"` |

**D-POCO only (Block A):**

| Key | Element |
|---|---|
| `wifi-direct-stable-identity` | `<boolean name="wifi-direct-stable-identity" value="false" />` |
| `wifi-direct-band` | per run, `<int name="wifi-direct-band" value="2" />` (2.4 GHz) or `"1"` (5 GHz) |
| `wifi-direct-group-name`, `wifi-direct-group-passphrase`, `wifi-direct-last-group-ssid`, `wifi-direct-last-group-bssid` | delete (both removal forms, §1) |

- Record as found, and change nothing else: `native-poke-bt-macs`, `native-aa-complete-hfp-slc` and `stand-down-station-mode` on both head units.
- Restore D-POCO's backup at the end of Block A, before the role swap.

## 5. Runs

**Helpers, per block.** `HU` and `PH` are the head unit's and the phone's serials.
```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1"; adb -s $PH shell log -t RIGMARK "$1"; }
```
`mark` stamps both captures, so the phone's window can be cut on the same labels. D-SAM's `am` rejects `-p`, and `send` does not use it.

**One call procedure, used by every run.** `<Rn>` is the run id.

1. Clean-run protocol (§4 of the template). Capture both devices from before the launch:
   ```bash
   stdbuf -oL adb -s $HU logcat -v time > <Rn>_hu.txt &
   stdbuf -oL adb -s $PH logcat -v time > <Rn>_ph.txt &
   ```
   Then arm with `send ACTION_START_WIRELESS_SCAN` and wait up to 120 s for `SSL handshake complete` in `<Rn>_hu.txt`. If it does not arrive, the run is void: re-run once, then report.
2. At SSL + 60 s, record the link state:
   ```bash
   mark <Rn>-links
   adb -s $PH shell dumpsys bluetooth_manager | grep -iE -A3 "HeadsetService|mActiveDevice"
   adb -s $HU shell dumpsys wifi | grep -m2 -iE "mWifiInfo|frequency"
   ```
   `adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY` has already been sent at SSL, so there is audio on the link.
3. At SSL + 120 s: `mark <Rn>-ring-request`. **Hand step:** the operator dials the phone now. Poll for the ring, then stamp it:
   ```bash
   for i in $(seq 1 60); do adb -s $PH shell dumpsys telecom | grep -qi "RINGING" && break; sleep 1; done; mark <Rn>-ringing
   ```
4. Let it ring 10 s, then answer and stamp:
   ```bash
   adb -s $PH shell input keyevent KEYCODE_CALL; mark <Rn>-answered
   ```
   If `dumpsys telecom` does not show the call `ACTIVE` within 5 s, the operator answers on the phone by hand. Note it in Setup notes.
5. At answered + 10 s, record the call's audio route verbatim:
   ```bash
   adb -s $PH shell dumpsys telecom | grep -iE "CallAudioState|audioRoute|route" | head -10
   ```
   **Hand step:** the operator says whether the caller is heard from the head unit. Stamp it: `mark <Rn>-heard-on-hu` or `mark <Rn>-not-heard-on-hu`.
6. At answered + 30 s: `adb -s $PH shell input keyevent KEYCODE_ENDCALL; mark <Rn>-hangup`.
7. At hangup + 60 s: `send ACTION_DISCONNECT`, `mark <Rn>-end`, then stop both captures.

### R0. Identity
`send ACTION_QUERY_STATE` on D-POCO, and again on D-SAM before Block B. `commit` must begin `c3c5a2d8` on both.

### Block A: D-POCO head unit, D-MOTO phone (`HU=4f4027e9`, `PH=ZY22GC3BM4`)

| Run | `wifi-direct-band` | hold | Role |
|---|---|---|---|
| **A1** | 2 (2.4 GHz) | true | **the point of the round** |
| A2 | 2 | false | does the collapse follow the held channel? |
| A3 | 1 (5 GHz) | true | control: addendum 2 on a Bluetooth-formed session |
| A4 | 1 | false | lowest priority; skip if the round runs long, and say so |

### Block B: D-SAM head unit, D-POCO phone (`HU=30041c35642d2200`, `PH=4f4027e9`)

D-SAM is 2.4 GHz only, so its band setting is irrelevant.

| Run | hold | Role |
|---|---|---|
| **B1** | true | addendum 1 reproduced, instrumented |
| B2 | false | addendum 1 as its own "switch off" most likely ran |

**Stop rule for Block B:** if B1 or B2 collapses, repeat that run once (`B1r`, `B2r`), and no more.

## 6. The lines that decide it

**Head unit** (`<Rn>_hu.txt`), verbatim from `c3c5a2d8` and checked with `grep -F`:
```
NativeAA: Handling handshake for
WppTcpServer: connection from
WifiDirectManager: operating channel
NativeAA: WiFi session landed. Holding the Bluetooth channel for the session and answering the phone's pings, as a head unit does.
NativeAA: WiFi session landed. Releasing the Bluetooth channel, because holding it is turned off in Settings.
NativeAA: [HOLD] Bluetooth channel held
NativeAA: the phone closed the held Bluetooth channel after
NativeAA: hands-free service level connection established
NativeAA: HFP connection accepted from
NativeAA: HFP socket for
NativeAA: HFP RX (
Throughput over
AapRead: Connection closed
SSL handshake complete
AutomationMarker:
```

**Phone** (`<Rn>_ph.txt`), matched case-insensitively. Seen on D-MOTO in addendum 2's capture; D-POCO as phone is unverified, so report zero hits as zero rather than as absent:
```
ConnectionStateCallback
SCO is Active:true
AudioStateCallback
Triggering WPP restart
Attempting to connect Bluetooth RFCOMM
RIGMARK
```

**Greps per run.** A window from marker X to marker Y means the lines whose timestamps fall between those two `AutomationMarker:` / `RIGMARK` lines.

- **G1 precondition (Block A):** counts of `Handling handshake for`, and of `WppTcpServer: connection from` before the first `SSL handshake complete`.
- **G2 band:** every `operating channel` line, with its MHz, plus the station frequency from step 2.
- **G3 channel state:**
  - which landing line printed;
  - the last `[HOLD]` line before `-ringing`, and the first after `-hangup`;
  - any `the phone closed the held Bluetooth channel after` line, with its timestamp.
- **G4 hands-free link at the ring:**
  - the timestamps of `service level connection established`, `HFP connection accepted from` and `HFP socket for` over the whole run;
  - the timestamp of the last `HFP RX (` before `-ringing`;
  - every `HFP RX (` line from `-ringing` to `-hangup`, verbatim, first 20.
- **G5 phone's link:**
  - every `ConnectionStateCallback` line from SSL to `-end`, verbatim;
  - the counts of `SCO is Active:true` and `AudioStateCallback` from `-ringing` to `-hangup`.
- **G6 picture:** every `Throughput over` line from `-links` to `-end`, verbatim, plus the count and timestamps of `AapRead: Connection closed` and `SSL handshake complete` after the first SSL.
- **G7 phone retry:** counts of `Triggering WPP restart` and `Attempting to connect Bluetooth RFCOMM` from SSL to `-end`.

## 7. What each run answers

This round is a measurement. Each run gets one outcome, decided from G1, G4, G5 and G6 alone:

- **INCONCLUSIVE (TCP route):** Block A only. G1 shows no handshake, or a TCP connection before SSL.
- **INCONCLUSIVE (link unfit):** any `Throughput over` line in the 60 s before `-ring-request` is below 20 fps.
- **COLLAPSED:** between `-ringing` and `-hangup` + 30 s, there is an `AapRead: Connection closed`, or any `Throughput over` line below 5 fps. Give the time from `-ringing` to the first such line.
- **HELD:** neither of those.

**Hands-free at the ring**, from G4 and G5, reported separately from the outcome. It is `up` when:
- the phone's last `ConnectionStateCallback` before `-ringing` is `2` or `3`, **or**
- an `HFP RX (` line falls inside the ring window.

Otherwise it is `down`.

**What the grid then says**, which the grader states in one line per block:

- **A1 COLLAPSED, A3 HELD:** the band decides it on a unit of D-POCO's class.
  - A2 HELD as well: the held channel is the cost on 2.4 GHz.
  - A2 COLLAPSED as well: it is the call's own Bluetooth traffic, and releasing the channel buys nothing.
- **A1 and A2 both HELD:** 2.4 GHz alone does not reproduce it on D-POCO, and Block B carries the question.
- **B1 and B2 with hands-free `up` at the ring, and COLLAPSED:** the stand-in link is the suspect. This is the first time a stand-in link will have been seen live at a ring.
- **B2 COLLAPSED:** addendum 1's proposal (release on stand-in units) would not have prevented it.

**Where the call audio went** comes from the step-5 route line plus the heard marker. The grader quotes both and draws no conclusion from the head unit's audio byte rate alone.

## 8. Do not re-run

- Round 1's R1 to R6: retry suppression and release on D-HU at 5 GHz are settled.
- Addendum 2's three calls: they are superseded by A3 and A4, which put a Bluetooth-formed session under the same call.

## 9. Report back

1. **One row per run:** outcome, hands-free at the ring (`up`/`down`, with the deciding line), group MHz, station MHz, landing line, the `[HOLD]` count before and after the call, and the first `Throughput over` fps after `-ringing`. Also give the time from ring to collapse if any, the step-5 route line, the heard marker, and G7's counts.
2. **Verbatim:** G4's in-ring `HFP RX (` lines and G5's `ConnectionStateCallback` lines, for A1, B1 and B2.
3. **The grid line per block**, from §7.

Captures: `hold-aa-rfcomm-round1-addendum3-captures.zip` on release `rig-evidence-hold-aa-rfcomm`, with both devices' logcats per run.
