# automation-screen-disconnect, round 5 brief: does a named connect wake a phone that nothing else would bring in?

> **Revised 2026-10-26: arm C moved and must be rebuilt.** C was rebased from `3101712a` (on `main` `f9a72d48`)
> to `d7d9596bd5f4` on `main` `947b3ae9`. The patch was hand-resolved: the external-power stand-down moved onto
> main's new cleanup-ticket ownership in `AapService.onDisconnected`. Round 4's APK no longer matches, so R0 builds,
> installs and checks identity. The md5 reuse path is gone. The test count is 4310. No run, string or verdict rule changed.

Publish name on `transfer/rig-rounds`: `automation-screen-disconnect-round5-brief.md`. Results go in
`automation-screen-disconnect-round5-results.md`. Evidence goes in the existing release
`rig-evidence-automation-screen-disconnect` as the asset
`automation-screen-disconnect-round5-captures.zip`.

One question, three units, one build. About 60 min (section 7 has the sum). No operator is needed unless a contingency in section 3
fires.

**Queue line:** `automation-screen-disconnect` round 5: D-POCO (head unit), D-MOTO (phone), D-HU
(Bluetooth held off only). D-SAM and D-HP are free, and a round on them can pair with this one (§3b)
only if it wakes neither D-POCO nor D-MOTO.

| Run | What it proves | Head unit | Phone |
|---|---|---|---|
| R0 | build C, install on D-POCO, check the APK identity | D-POCO | |
| P | precheck: bonds, radios, names, the keyguard, D-HU's Bluetooth | all | |
| **Q** | **control:** the start state, armed, with no command: nothing dials for 60 s plus 140 s | D-POCO | D-MOTO |
| **N1** | **the point of the round:** `ACTION_CONNECT --es device_name` puts a poke on the air and the phone comes in | D-POCO | D-MOTO |
| **N2** | the same by `--es mac` | D-POCO | D-MOTO |
| B | only if N1 or N2 forms no session: the old `ACTION_NATIVE_AA_POKE --es extra_mac`, same state (the C5p rule) | D-POCO | D-MOTO |
| X | restore | all | |

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` |
|---|---|---|---|
| **C** (candidate) | `fork/feat/automation-screen-disconnect` | `d7d9596bd5f45c638609770480b6ccf33019b5c0` | `d7d9596bd5f4` (a `-dirty` suffix is expected) |

History was rewritten once since round 4: C was rebased onto `main` `947b3ae9` on 2026-10-26. Round 4's build
(`3101712a`, APK md5 `edac10621db7e3a3dcd37f4b256207db`) is stale. Build C once in R0. 4310 JVM tests passed on the
dev host at this SHA, with 0 failures. Do not run the unit tests.

**gate.sh**
```bash
git fetch fork feat/automation-screen-disconnect
git rev-parse fork/feat/automation-screen-disconnect          # must print d7d9596bd5f45c638609770480b6ccf33019b5c0
git rev-parse fork/feat/automation-screen-disconnect^{tree}   # must print c75e7818023cb434253d6e07206762e154707d91
```

If either line differs, stop the round.

### R0. Build, install and APK identity

Make `rig-data/rounds/automation-screen-disconnect-round5/` and set `OUT` to it. Save the scripts in
section 5 first. In one worktree, run `git fetch fork feat/automation-screen-disconnect`, then
`git checkout --detach d7d9596bd5f45c638609770480b6ccf33019b5c0`. Check that the tree hash is
`c75e7818023cb434253d6e07206762e154707d91` and that `git status --porcelain` prints nothing. Run
`$RIG_TOOLKIT/rig_thermal.sh wait 70`, then build with
`HUR_DIR=<wt> MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh`. Copy the APK out of `apks/` at once to
`$OUT/apk/C.apk` and record its md5. Run `install.sh` below, then `r0_identity.sh`.

**r0_identity.sh**
```bash
mkdir -p $OUT/apk
md5sum $OUT/apk/C.apk                                                              # record it in Setup notes
adb -s $POCO shell am force-stop $PKG; sleep 0.3
send ACTION_QUERY_STATE | grep -a 'data=' | tee $OUT/R0.state                      # commit must start d7d9596bd5f4
adb -s $POCO shell am force-stop $PKG; sleep 0.3
```

- If `R0.state` shows `commit` starting `d7d9596bd5f4`, R0 PASSES.
- If the build fails, stop the round.
- If `commit` shows any other value, stop the round.
- D-HU needs no install: this round holds only its Bluetooth off.

**install.sh**
```bash
adb -s $POCO shell am force-stop $PKG; sleep 0.3
adb -s $POCO shell run-as $PKG cat $F > $OUT/pre-install-D-POCO.xml
adb -s $POCO install -r -d $OUT/apk/C.apk
adb -s $POCO shell run-as $PKG cat $F > $OUT/post-install-D-POCO.xml
diff $OUT/pre-install-D-POCO.xml $OUT/post-install-D-POCO.xml   # record the delta; P takes its backup after this
```

If the install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and tell the operator. Do not
uninstall. **A failure in R0 stops the round.**

## 2. What this is and why

`ACTION_CONNECT --es device_name <name>` (or `--es mac <mac>`) is a new form on this branch. The
service resolves the name against the bonded list (`ConnectByNameGate`) and calls
`selectDriver(mac, explicit = true)`, which runs the same manual poke as a tap on the driver selector.
Round 4 graded every part of that on D-HU with D-POCO as the phone: the resolve line, the explicit
pick, the refusals, the deep link, `no_ui`. One thing stayed unmeasured.

**Round 4 could not show that the command alone wakes the phone.** In C1 and C1d the wake guard stood
the poke down (`NativeAA: waking a phone over a hands-free link it holds is` 1,
`NativeAA: Calling socket.connect() for ` 0), because D-POCO held its hands-free link to D-HU. And
D-POCO's Android Auto dialled D-HU's RFCOMM 3 to 5 times per 20 s with no command, so every quiet
window broke (`quiet1=broken` in C1 twice, C4 and C1d). The session that formed after the command
was the phone's own.

This round asks only that question, on a pair where the phone holds no hands-free link and has no
reason to dial on its own.

## 3. What is different about this round

### The start state, and why this pair

The state must meet three conditions before every command:

1. The phone holds no hands-free link to the head unit.
2. The phone does not dial the head unit by itself for 60 s (phone `Creating rfcomm socket for device: ` 0, head unit `NativeAA: Connection accepted from ` 0).
3. The phone is bonded and reachable, so a poke can connect.

**The pair is D-POCO as the head unit and D-MOTO as the phone.** The reasons:

- **Neither unit has a hands-free client.** Both are phones, so each is an audio gateway. A hands-free
  link between them exists only while our poke holds its socket on D-POCO's stand-in record. It is
  gone before SSL (`CLAUDE.md`, "The stand-in link is completed wherever the stand-in record is
  published", and the stand-in call rounds). So condition 1 holds by construction, and the app's own
  guard reads it: on `NO_LINK` the poke path prints no hands-free line at all.
- **Android Auto starts a wireless setup on a Bluetooth event** (`ACL_CONNECTED`, a hands-free or A2DP
  connection change; `WifiBluetoothReceiver` in Gearhead 17.5 to 18.0) and keeps re-dialling only
  while a profile keeps the unit "present". With no profile between the two, the phone has nothing to
  react to. That is the mechanism behind condition 2. It is unmeasured on this pair, so Q measures it
  first, and every named run measures it again in its own quiet window.
- **The pair is bonded both ways and has woken by poke before.** Round 4 PA read D-MOTO in D-POCO's
  bonded list, and Stage A formed six sessions on this pair after 18:10 (V4n try 3 to R7k), each from
  the poke loop. So condition 3 holds.

**The other pairs fail a condition:**

| Pair | Why not |
|---|---|
| D-HU with D-POCO | Round 4 measured it: the hands-free link to D-HU stands the poke down, and the phone dials 3 to 5 times per 20 s. |
| D-HU with D-MOTO | The same structure. D-HU's own `HeadsetClientService` takes the phone's hands-free slot (`mMaxHeadsetConnections: 1`, `units/D-HU.md`), and that link is what the phone re-dials on. |
| D-SAM with either phone | D-POCO has no bond to D-SAM (round 4 NB: `poco_has_dsam=0`), pairing is an operator step, and a stale D-HU bond on D-POCO stopped it waking D-SAM before (`units/D-POCO.md`). |
| D-HP | No WiFi Direct, so no session can form (`units/D-HP.md`). |

A fresh boot or a Bluetooth cycle on one side was weighed and not used. A cycle of the phone's own
radio does not reconnect it to a head unit (`topics/bt.md`), and a cycle of D-HU's radio is the lever
that brings a link back. Neither is needed here.

### Order inverts §4 steps 4 and 5, on purpose

§4 brings the head unit up before the phone, so that the phone's own reconnect does not race the
launch. This round wants the opposite: the phone's own reconnect is what must be kept out. So P turns
D-MOTO's radios on once, while D-POCO's app is stopped, and lets it settle for 60 s. With the app
stopped, D-POCO publishes no stand-in record and no Android Auto record, so any profile auto-connect
from D-MOTO finds nothing. After P, D-MOTO's radios stay on for the whole round. Only D-POCO's app is
stopped and started between runs, and that raises no Bluetooth event on D-MOTO.

### D-HU's Bluetooth is held off

D-MOTO and D-POCO are both bonded to D-HU's radio (`Navegadortz2`). With D-HU's Bluetooth on, D-HU
can take D-MOTO's hands-free slot, and D-MOTO then dials D-HU over and over. So every run starts with
`dhu_bt_off` and reads D-HU's state again at the end of the quiet window (`topics/bt.md` records a
self-revert of about 14 s; rounds 2 and 4 held it off for whole stages). D-HU's app is force-stopped
in P. X turns D-HU's Bluetooth back on.

### Other facts that change the runs

- **D-POCO's group identity keys are left as its backup has them.** Do not write any `wifi-direct-*`
  key. Round 4's six Stage A sessions on this pair used these values. A new random group would leave
  any WPP-over-TCP endpoint D-MOTO stored pointing at a group that no longer exists, which is what
  failed V4n tries 1 and 2 (`Failed to find network`, then `SHUTDOWN`).
- **No poke target is set.** `native-poke-bt-macs` is empty and `native-poke-all-paired` is false, so
  the retry loop pokes nobody (round 4 Stage N: `Attempting active poke` 0). A poke in a run can only
  come from the command under test.
- **D-POCO's app is armed by a launch, not headless.** D-POCO is API 35, and a foreground service
  start from a stopped app can be refused there. A launch of `MainActivity` arms mode 3 by itself.
  Send `ACTION_START_WIRELESS_SCAN` only if no `WifiLauncher: Initializing WiFi Mode: ` line appears
  within 10 s (§4 step 4). The driver selector is off (`native-driver-selection-mode=0`), so the
  launch connects nobody.
- **D-POCO's keyguard is off for the round** (`locksettings set-disabled true`; the verb is
  `set-disabled`). D-POCO dozes between runs, so `ready` lights the panel before each launch and
  before each command. X restores the keyguard setting and the screen timeout from `$OUT/p.env`.
- **`NativeAA: Calling socket.connect() for ` names the phone, not its MAC.** The source prints
  `device.name`. The runs grade it on `$MOTO_NAME`.
- **D-POCO's bonded list masks D-MOTO's address** (round 2). `MOTO_BT` comes from D-MOTO itself, and
  `MOTO_NAME` from the count of the name in D-POCO's bonded list.
- **Expected INCONCLUSIVE:** none. If a session forms in Q's quiet window, no state on the rig meets
  the three conditions, and the round stops (stop rules in section 7).

### Contingencies

| Id | Fires when | Action | Why there is no verb |
|---|---|---|---|
| THR | D-MOTO's capture holds `THROTTLE_LIMIT_EXCEEDED` in a run that formed no session | Run `thr.sh` (reboot D-MOTO with D-POCO's app stopped, 60 s settle), then run that run once more | Gearhead's RFCOMM throttle clears only on a phone reboot |
| FORGET | `ssl=1` in N1, N2 or B, and D-MOTO's window holds `Failed to find network` | Cue the operator: on D-MOTO, Android Auto settings, forget `POCO X3 NFC`. Then run that run once more. With no operator, the run is UNTESTABLE | a forget is UI only on Gearhead (`units/D-POCO.md`) |
| HS0 | D-MOTO shows a PIN screen in P | Cue the operator to unlock D-MOTO once | adb cannot enter a PIN |

`cue` prints and beeps each step and writes it to `$OUT/hand-steps.log`. No script injects a tap on
any unit.

## 4. Settings keys

Write with the app stopped, with `put`. `put` starts every write from D-POCO's own backup (taken in
P), so the rig's audio settings and the group identity keep their values. Read the keys back with
`rd` before the launch. A key that does not read back voids the run before it starts: write it again.

**r5keys.sh**
```bash
BASE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs= set:auto-start-bt-macs= del:last-connected-native-mac del:native-preferred-device-mac bool:auto-connect-last-session=false bool:auto-start-on-boot=false bool:auto-start-on-screen-on=false bool:auto-start-on-usb=false bool:auto-start-self-mode=false bool:kill-on-disconnect=false bool:external-power-disconnect=false bool:allow-external-configuration=false int:onboarding-version=2 int:log-level=2 del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"
```

Every run (Q, N1, N2, B) uses `$BASE`.

Why the less obvious keys:

- The two MAC keys are deleted, so the gate's live-session read and the resolver start from nothing.
- `external-power-disconnect` false: D-POCO is on a PC cable, and this round measures no power event.
- `native-aa-wake-damage-verdict` and `video-profile-starvation-cap` are cleared because an earlier
  thread can leave either latched (`topics/bt.md`).
- `log-level=2` (INFO) carries every app line in section 6. `ACTION_LOG_MARKER` and `ACTION_CONNECT`
  work with `allow-external-configuration` false on C (round 4 Stage N).

## 5. Helpers and verbs

Copy `ptr_lib.sh`, `sx.py` and `scripts/runlocked.sh` from
`rig-data/rounds/automation-screen-disconnect-round4/` into `$OUT`. Save `r5_lib.sh` and `r5keys.sh`
from this brief. Run each script through `runlocked.sh`, as round 4 did. Every script sources
`ptr_lib.sh`, then `r5_lib.sh`, then `r5keys.sh`, then `$OUT/p.env` once P has written it. Use only
`th_gate` and `th_report` from `ptr_lib.sh` (they run `$RIG_TOOLKIT/rig_thermal.sh wait 75`, per
`rig-quirks/topics/tooling.md`).

**r5_lib.sh**
```bash
# r5_lib.sh : source after ptr_lib.sh. Needs OUT. RUN is set per run. D-POCO is the head unit, D-MOTO the phone.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
F=shared_prefs/settings.xml
POCO=4f4027e9; MOTO=ZY22GC3BM4; DHU=27870808938846
send() { local a=$1; shift; adb -s $POCO shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
# mark L : the marker reaches both captures
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s $MOTO shell log -t OHURIG "$1"; sleep 0.3; }
cue() { echo "$(date +%T) OPERATOR_STEP $1"; printf '\a'; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
HF() { echo "$OUT/$RUN.hu.txt"; }   # D-POCO capture
PF() { echo "$OUT/$RUN.ph.txt"; }   # D-MOTO capture
cap_start() { adb -s $POCO logcat -G 16M; sleep 0.3; adb -s $POCO logcat -c; sleep 0.3
  stdbuf -oL adb -s $POCO logcat -v time > "$(HF)" & echo $! > "$OUT/$RUN.hu.pid"
  adb -s $MOTO logcat -G 16M; sleep 0.3; adb -s $MOTO logcat -c; sleep 0.3
  stdbuf -oL adb -s $MOTO logcat -v time > "$(PF)" & echo $! > "$OUT/$RUN.ph.pid"
  sleep 1; }
cap_stop() { sleep 1
  for f in "$OUT/$RUN.hu.pid" "$OUT/$RUN.ph.pid"; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null; rm -f "$f"; done
  if grep -aqF "AutomationMarker: $RUN-start" "$(HF)" && grep -aqF "$RUN-start" "$(PF)"; then echo "wrapped=no" >> "$OUT/$RUN.summary"
  else echo "wrapped=yes" | tee -a "$OUT/$RUN.summary"; fi; }
# hwin A B : D-POCO lines from marker A to marker B. pwin A B : D-MOTO lines between the OHURIG markers.
hwin() { awk -v a="AutomationMarker: $1" -v b="AutomationMarker: $2" '{sub(/\r$/,"")} function at(s){i=index($0,s); return i>0 && substr($0,i+length(s))~/^[[:space:]]*$/} at(a){on=1} on{print} on&&at(b){exit}' "$(HF)"; }
pwin() { awk -v a="$1" -v b="$2" '{sub(/\r$/,"")} /OHURIG/ && $NF==a {on=1} on{print} on && /OHURIG/ && $NF==b {exit}' "$(PF)"; }
# n TEXT LIT [LIT2] [LIT3] : count of lines in TEXT that hold every LIT
n() { local t=$1; shift; local out; out=$(printf '%s\n' "$t")
  for l in "$@"; do out=$(printf '%s\n' "$out" | grep -aF -- "$l"); done
  if [ -z "$out" ]; then echo 0; else printf '%s\n' "$out" | grep -ac .; fi; }
# rend TEXT : windows with a rendered frame. It takes its text as an argument, like n.
rend() { printf '%s\n' "$1" | grep -acE 'Throughput over [0-9]+ms: rendered=[1-9]'; }
tms() { grep -aF -m1 -- "$1" | awk '{split($2,t,":"); printf "%d\n", (t[1]*3600+t[2]*60+t[3])*1000}'; }
# gap TEXT A B : ms from the first line holding A to the first line holding B, or NA
gap() { local a b; a=$(printf '%s\n' "$1" | tms "$2"); b=$(printf '%s\n' "$1" | tms "$3")
  if [ -n "$a" ] && [ -n "$b" ]; then echo $((b-a)); else echo NA; fi; }
# waitfor S STR : wait up to S seconds for a new D-POCO line holding STR. 0 = seen.
waitfor() { local s=$1 str=$2 b i; b=$(wc -l < "$(HF)")
  for i in $(seq 1 "$s"); do tail -n +"$((b+1))" "$(HF)" | grep -aqF -- "$str" && return 0; sleep 1; done; return 1; }
rd() { adb -s $POCO shell run-as $PKG cat $F | grep -aoE "($1)[^/]*"; sleep 0.3; }
put() { adb -s $POCO shell am force-stop $PKG; sleep 0.3
  cp "$OUT/backup-D-POCO.xml" "$OUT/w.xml"; python3 -I "$OUT/sx.py" "$OUT/w.xml" "$@"
  adb -s $POCO push "$OUT/w.xml" /data/local/tmp/ohu-w.xml >/dev/null; sleep 0.3
  adb -s $POCO shell run-as $PKG cp /data/local/tmp/ohu-w.xml $F; sleep 0.3; }
clean_hu() { adb -s $POCO shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 3
  adb -s $POCO shell am force-stop $PKG; sleep 0.3; }
wake() { adb -s $POCO shell dumpsys power | grep -a -m1 -oE 'mWakefulness=[A-Za-z]+|mScreenOn=[a-z]+'; sleep 0.3; }
kg() { adb -s $POCO shell dumpsys window | grep -a -m2 -oE '(isKeyguardShowing|mShowingLockscreen)=[a-z]+' | tr '\n' ' '; echo; sleep 0.3; }
focus() { adb -s $1 shell dumpsys window | grep -a -m1 mCurrentFocus; sleep 0.3; }
# ready TAG : light D-POCO's panel and clear a keyguard; write the readings to $OUT/$RUN.w-TAG
ready() { adb -s $POCO shell input keyevent 224; sleep 0.5; adb -s $POCO shell wm dismiss-keyguard; sleep 1
  { wake; kg; } | tee "$OUT/$RUN.w-$1"; }
btstate() { adb -s $1 shell dumpsys bluetooth_manager | grep -a -m1 -E 'enabled:|state:'; sleep 0.3; }
dhu_bt_off() { adb -s $DHU shell svc bluetooth disable; sleep 3; btstate $DHU; }
state() { send ACTION_QUERY_STATE | grep -a 'data='; }
# arm : launch D-POCO's app, arm mode 3, wait for the listeners. Writes init= and listening= to the summary.
arm() { adb -s $POCO shell am start -n $MAIN >/dev/null
  waitfor 10 'WifiLauncher: Initializing WiFi Mode: '; local i=$?; echo "init=$i" >> "$OUT/$RUN.summary"
  if [ $i -ne 0 ]; then send ACTION_START_WIRELESS_SCAN > "$OUT/$RUN-scan.reply"; fi
  waitfor 45 'NativeAA: ACTIVELY LISTENING on Android Auto UUID'; echo "listening=$?" >> "$OUT/$RUN.summary"; }
# quiet : a 60 s window with no command, then its verdict. Writes quiet=held or quiet=broken.
quiet() { mark $RUN-quiet; sleep 60; mark $RUN-go
  btstate $DHU | tee "$OUT/$RUN.dhu-after-quiet"
  local w p; w=$(hwin $RUN-quiet $RUN-go); p=$(pwin $RUN-quiet $RUN-go); QB=0
  for s in 'SSL handshake complete' 'NativeAA: Connection accepted from ' 'WirelessServer: Incoming connection detected' 'NativeAA: Calling socket.connect() for ' 'NativeAA: Attempting manual poke to ' 'NativeAA: Attempting active poke to device: '; do
    [ "$(n "$w" "$s")" -gt 0 ] && QB=1; done
  for s in 'Creating rfcomm socket for device: ' 'CONNECTING_RFCOMM' 'Starting manager of type: '; do
    [ "$(n "$p" "$s")" -gt 0 ] && QB=1; done
  if [ $QB -eq 0 ]; then echo "quiet=held" | tee -a "$OUT/$RUN.summary"; else echo "quiet=broken" | tee -a "$OUT/$RUN.summary"; fi; }
# prep : the common start of Q, N1, N2 and B
prep() { th_gate; put $BASE; rd 'wifi-connection-mode|native-poke|native-driver|log-level|external-power-disconnect' | tee "$OUT/$RUN.keys"
  dhu_bt_off | tee "$OUT/$RUN.dhu-bt"; btstate $MOTO | tee "$OUT/$RUN.moto-bt"
  cap_start; mark $RUN-start; ready pre; arm; quiet; }
# endrun : user exit, then a clean stop of D-POCO's app
endrun() { mark $RUN-x; send ACTION_DISCONNECT > "$OUT/$RUN-x.reply"; sleep 8
  mark $RUN-end; clean_hu; cap_stop; th_report $RUN; }
```

**Names with spaces need two layers of quotes.** `adb shell` joins its arguments and the device shell
splits them again. Write `--es device_name "'$MOTO_NAME'"`. If the quotes are lost, `am` reports an
error and the `connect by` line is missing; fix the quoting and run that step again.

**Verbs and commands used. Nothing else acts on the app:**

| Action | Command |
|---|---|
| launch (arms mode 3) | `adb -s $POCO shell am start -n $MAIN` (inside `arm`) |
| arm, only if the launch did not | `send ACTION_START_WIRELESS_SCAN` (inside `arm`) |
| connect by name, the verb under test | `send ACTION_CONNECT --es device_name "'$MOTO_NAME'"` |
| connect by MAC, the verb under test | `send ACTION_CONNECT --es mac $MOTO_BT` |
| the old poke (B only) | `send ACTION_NATIVE_AA_POKE --es extra_mac $MOTO_BT` |
| user exit | `send ACTION_DISCONNECT` (inside `endrun`) |
| marker | `mark <label>` (labels have no spaces) |
| build identity and session state | `state` |
| hard stop | `clean_hu` |

Every verb must print `AutomationReceiver: ` in D-POCO's capture. A step with no such line never
landed: that step is void, not a FAIL. Save each verb's `am` output to a `.reply` file. Never run two
adb calls against one unit at the same time.

## 6. The deciding lines

Every app string below matched `git grep -F` in `app/src/main` at `d7d9596b`. All are INFO or WARN,
so `log-level` 2 carries them. App lines are grepped in D-POCO's capture (`$(HF)`), phone lines in
D-MOTO's capture (`$(PF)`), each in the marker window its run names.

| Line or part (grep -F) | File | Used for |
|---|---|---|
| `AutomationReceiver: ` | HF | a verb landed; with `ACTION_CONNECT` or `ACTION_NATIVE_AA_POKE` |
| `AutomationMarker: ` | HF | markers |
| `WifiLauncher: Initializing WiFi Mode: ` | HF | with `NATIVE`: the launch armed Native AA |
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | HF | listeners open, before the quiet window |
| `gets the stand-in HFP record, because` | HF | record: D-POCO published the stand-in record |
| `AapService: connect by ` | HF | the command path; with `resolved to ` the gate passed |
| `resolved to ` | HF | part of the resolve line |
| `refused: ` | HF | any gate refusal; must be 0 in N1 and N2 |
| `BluetoothHelper: driver candidates: ` | HF | record: the verdict roll-up |
| `NativeAA: Driver selected: ` | HF | with `explicit` (N1, N2) or `automatic` (B) |
| `AapService: Received manual Native-AA poke request for ` | HF | B: the old poke branch ran |
| `NativeAA: Attempting manual poke to ` | HF | the explicit wake started |
| `NativeAA: Calling socket.connect() for ` | HF | **the poke is on the air**; the line names the phone |
| `NativeAA: Successfully poked ` | HF | record: the phone accepted the poke socket |
| `NativeAA: Poke via ` | HF | record: a poke record that failed, with the reason |
| `NativeAA: waking a phone over a hands-free link it holds is` | HF | must be 0: the guard saw no link that could be the phone's |
| `with a hands-free link up: ` | HF | must be 0: no hands-free link at all at poke time |
| `NativeAA: Not poking ` | HF | must be 0 |
| `NativeAA: Attempting active poke to device: ` | HF | the retry loop poked; must be 0 everywhere |
| `NativeAA: Connection accepted from ` | HF | the phone came back over Bluetooth |
| `WirelessServer: Incoming connection detected` | HF | the phone reached the group's server |
| `SSL handshake complete` | HF | a session formed |
| `Throughput over ` | HF | picture; `rend` matches `Throughput over [0-9]+ms: rendered=[1-9]` |
| `ms: rendered=` | HF | part of the `rend` regex |
| `AapService: raising the projection by ` | HF | record the route |
| `AapService: WiFi button on the Bluetooth module route: ` | HF | must be 0 |
| `Boot WakeLock acquired (10min timeout)` | HF | must be 0: the command did not fall into the USB check |
| `createGroup SUCCESS` | HF | count per run |
| `MATCH! Starting AapService` | HF | count per run; must be 0 |

### Not in our tree (D-MOTO's Android Auto, and our own phone marker)

| Line | Used for |
|---|---|
| `Creating rfcomm socket for device: ` | `GH.WIRELESS.BT`: the phone dials a head unit's RFCOMM; record the MAC it names |
| `CONNECTING_RFCOMM` | a `State changed to ` value: the phone started the RFCOMM leg |
| `Starting manager of type: ` | the phone started a wireless setup manager (`TCP` or another type); seen on D-MOTO in round 4 |
| `State changed to ` | record the phone's setup states |
| `Failed to find network` | the FORGET contingency (stale WPP-over-TCP endpoint, round 4 V4n) |
| `THROTTLE_LIMIT_EXCEEDED` | the THR contingency |
| `OHURIG` | our phone markers |

## 7. Runs

Order: R0, P, Q, N1, N2, then B only if its trigger fires, then X.

Every run: `th_gate` first (inside `prep`). After a second failed wait, stop the round and mark the
rest UNTESTABLE (host thermal), per `rig-quirks/topics/tooling.md`. Report `th_report $RUN`. Write the
results file after every run.

**Every run is valid only when** its `$RUN.summary` reads `wrapped=no`, `MATCH! Starting AapService`
is 0 in `hwin $RUN-start $RUN-end`, and `$RUN.dhu-bt` reads Bluetooth off. A run with `wrapped=yes` is
void: run it once more. Report all three, and `$RUN.dhu-after-quiet`.

**Time.** R0 27 min, with one build. P 8. Q 5. N1 5. N2 5. B 5 if it runs. X 3. Thermal-gate waits
about 5. Total about 58 min, 63 with B. Do not let a run cross
midnight: `gap` uses the time of day.

**Stop rules (counts).**

- If Q reads `quiet=broken` twice (the second time after `requiet.sh`), stop the round. No state on
  this rig meets the three conditions. Mark N1, N2 and B UNTESTABLE and report Q's counts.
- If N1 or N2 reads `quiet=broken`, that run skips its command (the script does it) and is
  INCONCLUSIVE. Run it once more. A second break makes it INCONCLUSIVE for good.
- THR and FORGET each run at most once in the round.

### P. Precheck, once (about 8 min)

Record every output in Setup notes. P writes `p.env`; every later script sources it.

**p_precheck.sh**
```bash
adb -s $DHU shell am force-stop $PKG; sleep 0.3
adb -s $POCO shell am force-stop $PKG; sleep 0.3
adb -s $POCO shell run-as $PKG cat $F > $OUT/backup-D-POCO.xml
diff <(tr -d '\r' < rig-data/rounds/automation-screen-disconnect-round4/backup-D-POCO.xml) <(tr -d '\r' < $OUT/backup-D-POCO.xml)   # record the delta, even if zero
grep -aoE '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|wifi-direct-stable-identity|wifi-direct-group-name)[^/]*' $OUT/backup-D-POCO.xml   # record; do not change
adb -s $POCO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'   # isGroupOwner must be false
adb -s $MOTO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'   # isGroupOwner must be false
KG_POCO=$(adb -s $POCO shell locksettings get-disabled | tr -d '\r'); echo "KG_POCO=$KG_POCO"
TIMEOUT_POCO=$(adb -s $POCO shell settings get system screen_off_timeout | tr -d '\r'); echo "TIMEOUT_POCO=$TIMEOUT_POCO"
MOTO_WIFI0=$(adb -s $MOTO shell dumpsys wifi | grep -a -m1 'Wi-Fi is' | tr -d '\r'); echo "MOTO_WIFI0=[$MOTO_WIFI0]"
MOTO_BT0=$(btstate $MOTO | tr -d '\r'); echo "MOTO_BT0=[$MOTO_BT0]"
POCO_BT=$(adb -s $POCO shell settings get secure bluetooth_address | tr -d '\r'); echo "POCO_BT=$POCO_BT"   # round history: DC:B7:2E:5E:4E:59
MOTO_BT=$(adb -s $MOTO shell settings get secure bluetooth_address | tr -d '\r'); echo "MOTO_BT=$MOTO_BT"   # round history: A0:46:5A:97:E4:95
adb -s $POCO shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices' > $OUT/poco-bonds.txt
adb -s $MOTO shell dumpsys bluetooth_manager | grep -a -A20 'Bonded devices' > $OUT/moto-bonds.txt
MOTO_NAME='motorola edge 30 neo'
grep -acF "$MOTO_NAME" $OUT/poco-bonds.txt     # must be 1
grep -acF 'POCO X3 NFC' $OUT/moto-bonds.txt    # must be 1 or more
adb -s $MOTO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb -s $MOTO shell dumpsys window | grep -a -m2 -oE '(isKeyguardShowing|mShowingLockscreen)=[a-z]+'
printf 'KG_POCO=%s\nTIMEOUT_POCO=%s\nPOCO_BT=%s\nMOTO_BT=%s\nMOTO_NAME=%q\n' "$KG_POCO" "$TIMEOUT_POCO" "$POCO_BT" "$MOTO_BT" "$MOTO_NAME" > $OUT/p.env
cat $OUT/p.env
```

Then:

1. If `isGroupOwner` reads true on D-POCO, send
   `adb -s $POCO shell am start -a android.intent.action.VIEW -d "headunit://exit"`, wait 3 s,
   force-stop the app, and read again.
2. If the `MOTO_NAME` count is not 1, read the name of D-MOTO's line in `poco-bonds.txt` by hand,
   write it to `p.env` as `MOTO_NAME=`, and say so. If no line names D-MOTO, or the D-POCO count in
   `moto-bonds.txt` is 0, the pair is not bonded: stop the round (pairing is an operator step).
3. If D-MOTO's keyguard reads `true` and its screen asks for a PIN, cue HS0 once.
4. Run `r0_identity.sh` again and record `R0.state`.

**p_radios.sh**
```bash
adb -s $POCO shell locksettings set-disabled true; sleep 0.5
adb -s $POCO shell locksettings get-disabled                  # must print true
adb -s $POCO shell settings put system screen_off_timeout 1800000; sleep 0.3
dhu_bt_off                                                     # must read off
btstate $POCO                                                  # must read on; else svc bluetooth enable and read again
adb -s $MOTO shell svc wifi enable; sleep 0.3; adb -s $MOTO shell svc bluetooth enable; sleep 5
adb -s $MOTO shell dumpsys wifi | grep -a -m1 'Wi-Fi is'; sleep 0.3   # must read enabled
btstate $MOTO                                                  # must read on
adb -s $MOTO shell ps -A | grep -ac gearhead                   # 1 or more
adb -s $MOTO shell input keyevent KEYCODE_HOME; sleep 1; focus $MOTO
adb -s $POCO shell input keyevent KEYCODE_HOME; sleep 1; focus $POCO
sleep 60                                                       # D-MOTO settles with D-POCO's app stopped
```

If `get-disabled` does not print `true`, go on and record it: `ready` clears a swipe keyguard before
each launch and each command.

### Q. Control: the start state with no command (about 5 min)

This is the control for N1 and N2. It shows that, in this state, nothing brings the phone in during
a window as long as a named run's command window.

**q.sh**
```bash
RUN=Q; prep
ready cmd; mark Q-cmd; sleep 140; mark Q-session
endrun
W=$(hwin Q-quiet Q-session); PW=$(pwin Q-quiet Q-session)
```

**PASS when all hold:**

1. `listening=0` in `Q.summary`, and `n "$(hwin Q-start Q-quiet)" 'WifiLauncher: Initializing WiFi Mode: ' 'NATIVE'` is 1 or more.
2. `quiet=held`.
3. In `W` (the quiet window and the 140 s after it), each of these is 0: `SSL handshake complete`,
   `NativeAA: Connection accepted from `, `WirelessServer: Incoming connection detected`,
   `NativeAA: Calling socket.connect() for `, `NativeAA: Attempting manual poke to `,
   `NativeAA: Attempting active poke to device: `.
4. In `PW`, each of these is 0: `Creating rfcomm socket for device: `, `CONNECTING_RFCOMM`,
   `Starting manager of type: `.

Record: `createGroup SUCCESS` in `hwin Q-start Q-end`, the `gets the stand-in HFP record, because`
line, `n "$PW" 'State changed to '`, and `Q.dhu-after-quiet`.

**If condition 2, 3 or 4 fails:** record every non-zero count and the MAC each phone
`Creating rfcomm socket for device: ` line names. Then run `requiet.sh` and run `q.sh` once more.
A second failure stops the round (section 7).

**requiet.sh**
```bash
clean_hu; dhu_bt_off
adb -s $MOTO shell svc bluetooth disable; sleep 5; btstate $MOTO     # must read off
adb -s $MOTO shell svc bluetooth enable; sleep 5; btstate $MOTO      # must read on
sleep 60
```

### N1. The point of the round: connect by name (about 5 min)

**n1.sh**
```bash
RUN=N1; prep
if grep -aqF 'quiet=held' $OUT/N1.summary; then
  ready cmd; mark N1-cmd; send ACTION_CONNECT --es device_name "'$MOTO_NAME'" > $OUT/N1-cmd.reply
  waitfor 120 'SSL handshake complete'; ssl=$?; echo "ssl=$ssl" | tee -a $OUT/N1.summary
  sleep 20; mark N1-session; state | tee $OUT/N1.state
else mark N1-cmd; mark N1-session; echo "ssl=skipped" >> $OUT/N1.summary; fi
endrun
Q=$(hwin N1-quiet N1-go); PQ=$(pwin N1-quiet N1-go)
W=$(hwin N1-cmd N1-session); PW=$(pwin N1-cmd N1-session)
```

**PASS when all hold:**

1. Preflight: `listening=0`, and `n "$(hwin N1-start N1-quiet)" 'WifiLauncher: Initializing WiFi Mode: ' 'NATIVE'` is 1 or more.
2. Quiet: `quiet=held` (the same six head unit counts and three phone counts as Q, each 0 in `Q` and `PQ`).
3. `n "$W" 'AutomationReceiver: ' 'ACTION_CONNECT'` is 1.
4. `n "$W" 'AapService: connect by ' 'resolved to '` is 1, that line holds `name "$MOTO_NAME"` and
   ends with `$MOTO_BT`. `n "$W" 'AapService: connect by ' 'refused: '` is 0.
5. `n "$W" 'NativeAA: Driver selected: ' 'explicit'` is 1, and that line holds `$MOTO_BT`.
6. **The poke is on the air:** `n "$W" 'NativeAA: Calling socket.connect() for ' "$MOTO_NAME"` is 1
   or more, and `gap "$W" 'AutomationMarker: N1-cmd' 'NativeAA: Calling socket.connect() for '` is
   15000 or less. In `W`, `NativeAA: waking a phone over a hands-free link it holds is`,
   `with a hands-free link up: ` and `NativeAA: Not poking ` are each 0.
7. **The phone answered the poke:** in `PW`, `Creating rfcomm socket for device: ` plus
   `CONNECTING_RFCOMM` is 1 or more. If both are 0 and `Starting manager of type: ` is 1 or more,
   condition 7 is met by the phone's other route: say so and record the type.
8. **A session followed:** `ssl=0`, and `gap "$W" 'AutomationMarker: N1-cmd' 'SSL handshake complete'`
   is 120000 or less.
9. `rend "$W"` is 1 or more.
10. In `W`: `Boot WakeLock acquired (10min timeout)`, `AapService: WiFi button on the Bluetooth module route: `
    and `NativeAA: Attempting active poke to device: ` are each 0.

**Order check.** In `W` these head unit lines must appear in this time order, each at or after the
one before: `AapService: connect by `, `NativeAA: Driver selected: `,
`NativeAA: Calling socket.connect() for `, `SSL handshake complete`, the first `Throughput over `.
Report the five timestamps. An order fault is a FAIL of condition 6 or 8.

**What a PASS would look like if the change did nothing.** Without the change, `ACTION_CONNECT` with
`device_name` is the USB check: condition 4 would be 0, condition 10 would read 1 for the wake lock,
condition 6 would be 0, and condition 2 shows that nothing else would have brought the phone in.
**What it would look like if the poke did nothing.** Condition 6 holds but the phone stays silent:
condition 7 is 0 and `ssl=1`, with Q and the quiet window both clean. That is a FAIL of the wake,
graded against B (the C5p rule below).

Record: `gap "$W" 'AutomationMarker: N1-cmd' 'NativeAA: Connection accepted from '`, every
`NativeAA: Successfully poked ` and `NativeAA: Poke via ` line, the `AapService: raising the projection by `
line, the `BluetoothHelper: driver candidates: ` line from the whole capture, `createGroup SUCCESS` in
`hwin N1-start N1-cmd` and in `W`, the MAC each phone `Creating rfcomm socket for device: ` line
names, the `State changed to ` values in `PW`, `SSL handshake complete` count in `W`, `N1.state`, and
the `N1-cmd.reply` `data=` line.

**If condition 8 fails:** check the contingencies (THR, FORGET) first. If neither fires, run B.

### N2. Connect by MAC (about 5 min)

**n2.sh**
```bash
RUN=N2; prep
if grep -aqF 'quiet=held' $OUT/N2.summary; then
  ready cmd; mark N2-cmd; send ACTION_CONNECT --es mac $MOTO_BT > $OUT/N2-cmd.reply
  waitfor 120 'SSL handshake complete'; ssl=$?; echo "ssl=$ssl" | tee -a $OUT/N2.summary
  sleep 20; mark N2-session; state | tee $OUT/N2.state
else mark N2-cmd; mark N2-session; echo "ssl=skipped" >> $OUT/N2.summary; fi
endrun
Q=$(hwin N2-quiet N2-go); PQ=$(pwin N2-quiet N2-go)
W=$(hwin N2-cmd N2-session); PW=$(pwin N2-cmd N2-session)
```

**PASS when** N1's conditions 1 to 10 and its order check hold on N2's windows, with one change to
condition 4: the resolve line holds `MAC $MOTO_BT` and ends with `$MOTO_BT`. N2's quiet window comes
after N1's session, so it also shows that a session that ended leaves the phone silent. Record the
same values as N1.

### B. Only if N1 or N2 gave `ssl=1`: the old poke, same state (about 5 min)

The C5p rule from round 4: B uses the old poke path, which this branch does not change, on the same
state. If B forms a session and the named run did not, the named run FAILs condition 8. If B also
forms none, the named run's condition 8 is INCONCLUSIVE (rig or phone state, shared by both paths).

**b.sh**
```bash
RUN=B; prep
if grep -aqF 'quiet=held' $OUT/B.summary; then
  ready cmd; mark B-cmd; send ACTION_NATIVE_AA_POKE --es extra_mac $MOTO_BT > $OUT/B-cmd.reply
  waitfor 120 'SSL handshake complete'; ssl=$?; echo "ssl=$ssl" | tee -a $OUT/B.summary
  sleep 20; mark B-session
else mark B-cmd; mark B-session; echo "ssl=skipped" >> $OUT/B.summary; fi
endrun
W=$(hwin B-cmd B-session); PW=$(pwin B-cmd B-session)
```

**PASS when all hold:** `quiet=held`; `n "$W" 'AapService: Received manual Native-AA poke request for '`
is 1 and holds `$MOTO_BT`; `n "$W" 'NativeAA: Driver selected: ' 'automatic'` is 1;
`n "$W" 'AapService: connect by '` is 0. Record `ssl`, `rend "$W"`,
`n "$W" 'NativeAA: Calling socket.connect() for ' "$MOTO_NAME"`, and the three phone counts in `PW`.

### Contingency scripts

**thr.sh**
```bash
clean_hu; dhu_bt_off
adb -s $MOTO reboot; adb -s $MOTO wait-for-device
for i in $(seq 1 30); do   # poll every 2 s, cap 60 s
  [ "$(adb -s $MOTO shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && [ "$(adb -s $MOTO shell ps -A | grep -ac gearhead)" -gt 0 ] && break; sleep 2; done; echo "boot_poll=$i"
adb -s $MOTO shell dumpsys window | grep -a -m2 -oE '(isKeyguardShowing|mShowingLockscreen)=[a-z]+'   # a PIN screen: cue HS0
adb -s $MOTO shell input keyevent KEYCODE_HOME; sleep 1; btstate $MOTO
sleep 60
```

**forget_cue.sh**
```bash
clean_hu
cue "FORGET: on D-MOTO, open Android Auto settings and forget the car POCO X3 NFC; say done"
```

After FORGET, wait for the operator, then run the run once more.

### X. Restore

**x_restore.sh**
```bash
adb -s $POCO shell am force-stop $PKG; sleep 0.3
adb -s $POCO push $OUT/backup-D-POCO.xml /data/local/tmp/ohu-w.xml >/dev/null; sleep 0.3
adb -s $POCO shell run-as $PKG cp /data/local/tmp/ohu-w.xml $F; sleep 0.3
adb -s $POCO shell run-as $PKG cat $F | diff - $OUT/backup-D-POCO.xml        # must be empty
adb -s $POCO shell locksettings set-disabled "$KG_POCO"; sleep 0.5
adb -s $POCO shell locksettings get-disabled                                  # must equal KG_POCO
adb -s $POCO shell settings put system screen_off_timeout "$TIMEOUT_POCO"; sleep 0.3
adb -s $POCO shell settings get system screen_off_timeout                     # must equal TIMEOUT_POCO
adb -s $DHU shell svc bluetooth enable; sleep 5; btstate $DHU                 # must read on
ps -ef | grep -a '[l]ogcat' | grep -aE "$POCO|$MOTO" | wc -l                  # must be 0
```

Then set D-MOTO's radios back to `MOTO_WIFI0` and `MOTO_BT0` from P with `svc wifi` and
`svc bluetooth` as needed, and read both back. Leave the candidate installed on D-POCO.

## 8. Do not re-run

- **Unit tests.** 4310 JVM tests, 0 failures, at `d7d9596b` on the dev host.
- **Every round 4 run that passed:** R0, V3, V4n, R5n, R7n, V4k, R5k, R7k, C5u, C3, C5p, C2, C4,
  C4c. They ran on `3101712a`; the rebase changed no logic they measured.
- **R11.** Our grade passed it against the plan: the brief's `Initializing` clause did not allow for
  an accessory that stays attached, and the existing USB hand-off hold kept wireless down by design.
- **C1 and C1d, apart from the wake.** The resolve line, the explicit pick, the deep link's query
  parsing and the lower-case name all held in round 4. N1 and N2 measure only what round 4 could not.
- **The wake guard standing a poke down over a held link.** That is the driver selector's existing
  path, unchanged by the rebase, and the reason this round moved off D-HU.

## 9. Report back

The numbers that decide shipping:

1. **Q:** `quiet=` and the six head unit and three phone counts over the whole 200 s. All 0 means the
   start state is real.
2. **N1 and N2:** the five order-check timestamps, the command to `NativeAA: Calling socket.connect() for `
   time, the command to `SSL handshake complete` time, `rend`, and the phone counts in the quiet
   window and after the command, side by side.
3. **The hands-free reading at poke time:** the counts of `NativeAA: waking a phone over a hands-free link it holds is`,
   `with a hands-free link up: ` and `NativeAA: Not poking ` in N1 and N2.

Give each verdict in the fixed format of `TESTING-TEMPLATE.md` §7, and put every deviation in Setup
notes. Add to `rig-quirks/units/D-MOTO.md` whether D-MOTO dialled D-POCO by itself in any quiet
window, and which route (`Creating rfcomm socket for device: ` or `Starting manager of type: `) it took
after a poke.

These strings are also grepped, but they are not in our tree. They are Android Auto lines in D-MOTO's
capture, plus our own phone marker tag.

```external-strings
Creating rfcomm socket for device: 
CONNECTING_RFCOMM
Starting manager of type: 
State changed to 
Failed to find network
THROTTLE_LIMIT_EXCEEDED
OHURIG
```

The block below lists every app string the runs grep. Each one is in `app/src/main` at `d7d9596b`.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
ACTION_CONNECT
WifiLauncher: Initializing WiFi Mode: 
NATIVE
NativeAA: ACTIVELY LISTENING on Android Auto UUID
gets the stand-in HFP record, because
AapService: connect by 
resolved to 
refused: 
BluetoothHelper: driver candidates: 
NativeAA: Driver selected: 
explicit
automatic
AapService: Received manual Native-AA poke request for 
NativeAA: Attempting manual poke to 
NativeAA: Calling socket.connect() for 
NativeAA: Successfully poked 
NativeAA: Poke via 
NativeAA: waking a phone over a hands-free link it holds is
with a hands-free link up: 
NativeAA: Not poking 
NativeAA: Attempting active poke to device: 
NativeAA: Connection accepted from 
WirelessServer: Incoming connection detected
SSL handshake complete
Throughput over 
ms: rendered=
AapService: raising the projection by 
AapService: WiFi button on the Bluetooth module route: 
Boot WakeLock acquired (10min timeout)
createGroup SUCCESS
MATCH! Starting AapService
```
