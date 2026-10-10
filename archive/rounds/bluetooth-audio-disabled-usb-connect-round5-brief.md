# bluetooth-audio-disabled-usb-connect, round 5 brief

Published name on the transfer branch: `bluetooth-audio-disabled-usb-connect-round5-brief.md`.

This is a **measurement round** on the same probe build as rounds 1 to 4. No fix is on trial. Round 4 stopped after R1 and produced no skip data. This round repeats round 4 with four changes:

1. **Stage P, a pre-flight** before any session. It reads D-MOTO's bond to D-HU and its profiles, and compares them with rounds 3 and 4.
2. **R0, the round 3 control, runs before R1.** D-POCO announces its own address with its Bluetooth on, as in round 3. R0 against R1 separates the round 4 layout from a change on D-MOTO.
3. **The skip arm (S1 to S4) runs whatever R1 shows.** Its "does not disable" claim is graded against whichever control disabled, R1 or R0.
4. **The abort rule covers an unbond only.** A pairing request is counted. It does not stop the round.

**The point of the round is S1 to S3** (the skip arm). R0, R1 and S4 are its controls. Goal 1 (the grey switch, R2a to R3b) runs only after a full R1 control.

Everything else is round 4's brief (`bluetooth-audio-disabled-usb-connect-round4-brief.md`) and round 4's harness as the rig ran it. This brief changes only what it names.

## 1. Build and baseline

- Candidate: branch `fix/bluetooth-audio-disabled-usb-connect` on the fork, SHA **`fff96699`** (`fff96699480d878bb36bd04078d77260231b19dc`). This is the SHA of rounds 1 to 4. History was not rewritten, and no commit was added.
  ```bash
  git fetch fork fix/bluetooth-audio-disabled-usb-connect
  git -C ../ohu-wt-bad-fff96699 rev-parse HEAD     # must print fff96699480d878bb36bd04078d77260231b19dc
  ```
- **Reuse the round 3 and 4 APK.** If `apks/candidate-fff96699.apk` exists and its md5 is `234caaadff2d4731e006591a0c69bb2a`, set `WANT_MD5=234caaadff2d4731e006591a0c69bb2a` and do not build. If it is gone, build it from `../ohu-wt-bad-fff96699` with `build_hur.sh`, run `run_unit_tests.sh` (gate: **2747 tests, 0 failures**; a failure stops the round), copy the APK to `apks/candidate-fff96699.apk` at once, and record its md5 as `WANT_MD5`.
- **Only D-POCO runs the app this round.** If `apk_check` on D-POCO does not match, back up its `settings.xml` first, then `adb -s 4f4027e9 install -r apks/candidate-fff96699.apk`, then diff `settings.xml` against the backup. Do not install on D-HU: its app stays stopped for the whole round.
- `send ACTION_QUERY_STATE` on D-POCO must reply with a `commit` that begins `fff96699`.
- DEX symbol for identity (template §5): `BluetoothAnnouncePolicy`. Check it only if you built a new APK.
- **No baseline APK.** Every comparison is between runs on the candidate.

## 2. What this is and why it exists

**The report.** A user's phone is paired with the head unit over Bluetooth. When Android Auto connects by cable, the phone switches off the head unit's "Media audio" toggle by itself. The reporter says the toggle is greyed out.

**What rounds 1 to 3 measured** on D-MOTO (Android 14, Android Auto `17.9.664004`), from D-MOTO's own Bluetooth log:

| End of the session | Stored A2DP policy for the announced address | Where measured |
|---|---|---|
| Session starts (any transport) | `gearhead:car` sets `0` about 0.7 to 1.0 s after SSL, with `disabling A2dp via profile disabler` | every run, rounds 1 to 3 |
| Clean end | `gearhead:car` sets `100` about 1.4 to 1.9 s later | USB and Native AA |
| Cable pulled out | `gearhead:car` sets `100` within 460 ms of the disconnect | rounds 2 and 3 |
| Android Auto force-stopped | stays `0` | USB and Native AA |
| `bt-announce=blank` or `skip` | no `setConnectionPolicy(` for the address | round 1 U2, U3 |

**What round 4 found.** D-POCO (R1p) and D-HP (R1) each announced D-HU's address `11:46:03:10:33:59` over USB. In both, D-MOTO ran a pairing flow and **no profile disabler**:

```
20:20:56.619 disabling A2dp route while in projection
20:20:57.804 D/CAR.BT.LITE: BluetoothFsm Transition from STATE_WAITING_FOR_BLUETOOTH_PROFILE_UTIL to STATE_REQUESTING_CAR_PAIRING_PREPARATION
20:20:57.805 D/CAR.BT.SVC.LITE: Sending a pairing request
```

The stored policy stayed `100`, and the switch read `ON`. Round 4's abort rule matched `Sending a pairing request` and stopped the round. No bond changed.

**Two things changed between round 3 and round 4, and round 4 cannot separate them.** Round 3's USB runs announced D-POCO's own address `DC:B7:2E:5E:4E:59` with D-POCO's Bluetooth on. Round 4 announced D-HU's address with D-POCO's Bluetooth off. R0 repeats round 3's layout. If R0 disables and R1 does not, the round 4 layout is the cause. If neither disables, D-MOTO changed. The engineer confirmed that D-MOTO's Android Auto version did not change since round 3 (2026-10-08), so this round does not read it.

**Why D-POCO announces D-HU's address in R1 to S4.** D-POCO is the only USB head unit on the rig. D-MOTO most likely shows no "Media audio" switch for a phone. D-HU is a real head unit with an A2DP sink, bonded to D-MOTO. Android Auto keys the disable on the announced address (round 1 U1 and U4). This is the reporter's setup: a head unit on Bluetooth, then Android Auto by cable.

**The skip value.** `SKIP_THIS_BLUETOOTH` is a value that Android Auto knows and handles in its own branch. Round 1 U3 showed that it stops the policy change. If this round shows that media and calls still work with it, and that the setting returns to `real` cleanly, a user setting that announces the skip value is the candidate fix.

## 3. What is different about this round

**Hosts.** D-POCO is the USB host in every run. **Do not use D-HP**: it is Android 4.2, with no `exec-in` and no Bluetooth service call (round 4 notes). If D-POCO cannot host (battery, adb, USB), stop and escalate. Charge D-POCO to 80 % or more on the PC cable before Prepare step 9. D-POCO powers D-MOTO over OTG, so check its level before each run (section 6, `bat`).

**Layouts.** Two layouts, switched by `lay0` and `lay1` (section 6):

| Layout | Runs | D-POCO `bt-address` | D-POCO Bluetooth | `ADDR` (the policy entry that `psum` reads) |
|---|---|---|---|---|
| 0 (round 3) | R0, R0x | `$POCO_BT` | **on** | `$POCO_BT` |
| 1 (round 4) | R1 and every later run | `$DHU_BT` | **off** | `$DHU_BT` |

| Unit | Serial | Role | adb |
|---|---|---|---|
| D-POCO | `4f4027e9` | USB head unit, the app under test | wireless |
| D-MOTO | `ZY22GC3BM4` | the phone; plugged into D-POCO's OTG port only during a session | wireless |
| D-HU | `27870808938846` | a plain Bluetooth car unit, app stopped, `wifi-connection-mode=0` | PC cable |

**In R0 the switch and the policy describe different devices.** The switch reading is still D-HU's screen (`DEVNAME`). The `policy=` column of `tsum` and every `p.*` value are D-POCO's entry on D-MOTO. Report them apart. In layout 0 D-POCO can take D-HU's one hands-free slot (`rig-quirks/units/D-HU.md`), so D-MOTO's link to D-HU may drop during R0. That does not affect R0. `pre_rig` in layout 1 repairs it before R1.

**The branch after R1.** `ctl` (section 6) prints the control verdict of a run:

| `ctl` prints | Means |
|---|---|
| `VOID` | no session, or a `VOID` line: run it once more under `<id>x` |
| `NO_DISABLE` | a session, and `ph.disabler` = 0 or `p.disabled_by_gh_before_stop` = 0 |
| `DISABLES` | `ph.disabler` >= 1 and `p.disabled_by_gh_before_stop` >= 1, and the `live` switch does not read `OFF` or `GREY_OFF` |
| `DISABLES_SWITCH_OFF` | as `DISABLES`, and the `live` switch reads `OFF` or `GREY_OFF` |

- R1 (or R1x) `DISABLES_SWITCH_OFF`: this is the round 4 plan. Run R2a, R2b, R3a, R3b, then S1 to S4.
- Anything else: **do not stop.** Skip R2a to R3b, because they read a grey state that the control did not produce. Run S1 to S4.

**Abort, narrowed.** Only an unbond of D-HU on D-MOTO stops the round: `Device was unbonded at some point`, or `BOND_NONE` on a line that carries D-HU's address suffix. `usum4` writes `$OUT/STOP`, and `step` then skips every later run. Go to the close. `Sending a pairing request` and `Wrong device is being paired` are counted per run (`ph.pairreq`, `ph.wrongdev`). They do not stop anything.

**A pairing dialog on D-MOTO.** The pairing flow may now run to its end and raise a dialog on D-MOTO. A dump whose package is not `com.android.settings` reads `UNREAD`. Nobody answers the dialog inside a run. `usum4` reads D-MOTO's focus after each run. If it names `BluetoothPairing`, it cues H11: **Cancel** the dialog. Never accept it, because a new bond resets the stored policy and changes every later run.

**Harness.** Round 4's harness as the rig left it: `lib1008r3.sh`, `lib1008r4.sh`, `lib1008r5.sh`, `ui1008.py`, `navxy.py`, `ohu_setkeys.py`. That includes the round 4 fixes: `nl` before the plug cue, the `call` that dials out from D-MOTO to D-POCO, `av` reading 14 lines for `Devices:`, the hold test only where the switch reads off, and round 4's route to D-HU's screen on D-MOTO (H12: `BLUETOOTH_SETTINGS`, then one injected input on the gear beside Navegadortz2). The new file `lib1008-round5.sh` (section 6) adds `lay0`, `lay1`, `ctl`, `step`, `bat` and `fresh`, and replaces `pre_rig`, `usum4` and `av`. It also wraps `rd`, so that `pre`, `live`, `end+60` and the clearing points read a freshly opened screen (round 4's `live` could be stale).

**UI input budget.** No run sends an input to the app: every app action is an `AutomationReceiver` verb. The only injected inputs go to Android's own screens on D-MOTO, which no verb of the app can reach (H4 and H12 in section 4). The operator's standing rule allows at most 5 injected inputs per run (`rig-quirks/topics/tooling.md`). The route to D-HU's screen costs 1 injected input. The SIM chooser on a dial costs 1. `fresh` stops reopening the screen when the run has used 4 injected inputs, and the reading then uses the screen in front (`summary.tsv` says `reused-screen`). Every injected input goes in `$OUT/inputs.log`, one line each with the run id, the time, the coordinates and the target. Round 4's harness writes these lines to a log file of another name: in the round 5 copies of `lib1008r5.sh` and `navxy.py`, change that file name to `inputs.log`, and say so in Setup notes.

**Expected INCONCLUSIVE, and that is not a failure:**

- The S claim "skip does not disable", if neither R0 nor R1 disables. It stays ungraded, and the S runs still grade every part in section 8 that needs no control.
- The second-copy check of a run, if its `av live` fields disagree (section 8, S1).
- A point read `UNREAD` twice. The run still counts for its other points.

**Incoming calls are untested.** The call is outgoing from D-MOTO, as in round 4. The call route is recorded, not graded.

## 4. Hand steps, and why no verb exists

Round 4's H0 to H9 stand, with these changes. Cue the operator directly at the moment of the action (popup and voice, round 3's `cue`). Never cue through a log that someone reads later.

| Id | Step | Why no verb |
|---|---|---|
| H1 | As round 4. **A recovery is budgeted.** After a cable event the first bring-up often fails (`Found device already in accessory mode`, `Version request send failed`, `Unable to parse TLS packet header`). Recover: unplug, wait 5 s, plug back in, allow `UsbConfirmActivity` on D-POCO (do not tick "Always"). Each recovery is counted (`h1`). Two recoveries without a session make the run VOID | A cable is hardware. The re-attach fault belongs to another thread |
| H3 | As round 4. If D-MOTO shows its USB preferences, choose "USB controlled by: This device", as in round 4 R1p | Android's screens have no adb lever |
| H4 | The call. D-MOTO dials D-POCO (round 4's `call`). D-MOTO then shows "Choose SIM for this call". The script chooses SIM 1 with one injected input at `540,1384` (round 4 setup note 2) and logs it in `inputs.log` | D-POCO cannot dial out, so a call needs a second phone. The SIM chooser is a dialog of D-MOTO's dialer, not of the app, so no verb reaches it |
| H12 | **New in this table, used since round 4.** Open D-HU's details screen on D-MOTO (`ui_open`): `am start -a android.settings.BLUETOOTH_SETTINGS`, then one injected input on the gear beside Navegadortz2, found by `navxy.py` from a `uiautomator dump`. Each use is logged in `inputs.log` | The screen is in D-MOTO's Settings app, not in the app under test, so no `AutomationReceiver` verb can open it. Android exposes no intent for it on D-MOTO: `BLUETOOTH_DEVICE_DETAIL_SETTINGS` does not resolve and `SubSettings` is not exported (round 4 setup note 3) |
| H7 | Re-pair D-MOTO and D-HU. **Allowed once, and only in Stage P** | Pairing needs a confirmation on both screens |
| H10 | **New.** Connect D-MOTO to Navegadortz2 by hand: open Bluetooth settings, then press Navegadortz2 in the device list. Only in Stage P and in `pre_rig` | Round 4 Stage 0 needed this: a D-HU cycle and a D-MOTO cycle did not reconnect |
| H11 | **New.** Cancel a pairing dialog on D-MOTO. Never accept it. Only at an H11 cue, which comes only after a run's `-end` | A system dialog |

Before Stage P, the host tells the operator in one message: "Cues arrive as a popup and a voice. A PULL cue means pull the cable from D-POCO and leave it out. A HOLD cue means turn Media audio on for Navegadortz2 once and then do not touch the phone. A pairing dialog on D-MOTO waits for an H11 cue, and then you press Cancel. Never touch D-MOTO's Settings without a cue."

## 5. Settings keys

Write the keys with the app stopped, after a backup (template §1). D-POCO is not rooted: use `pocoput`. D-HU: use `hu_put`. Read every key back before each launch. Record the rig audio keys (`use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`) from both backups as found. Do not write them. They are a deliberate worst case.

| Key | Type | D-POCO, layout 0 | D-POCO, layout 1 | D-HU |
|---|---|---|---|---|
| `wifi-connection-mode` | int | `0` | `0` | `0` |
| `log-level` | int | `2` (INFO) | `2` | as found, do not write |
| `onboarding-version` | int | `2` | `2` | |
| `allow-external-configuration` | boolean | `true` | `true` | |
| `enable-audio-sink` | boolean | `true` | `true` | |
| `kill-on-disconnect` | boolean | `false` | `false` | |
| `auto-connect-last-session` | boolean | `false` | `false` | |
| `auto-connect-single-usb` | boolean | `false` | `false` | |
| `auto-start-on-usb` | boolean | `false` | `false` | |
| `reopen-on-reconnection` | boolean | `false` | `false` | |
| `use-libusb` | boolean | `false` | `false` | |
| `connection-modes` | string set | `usb,wifi` | `usb,wifi` | |
| `bt-address` | string | **`$POCO_BT`** | **`$DHU_BT`** | as found, do not write |
| `auto-start-bt-macs` | string set | | | as found, read back and record |
| `bt-announce`, `head-unit-make`, `head-unit-model` | | delete | delete | as found, do not write |
| `video-profile-starvation-cap`, `native-aa-wake-damage-verdict`, `native-aa-wireless`, `wifi-launcher-mode` | | delete | delete | |

`orun` sets `bt-announce` (`real` or `skip`), `head-unit-make` `Google` and `head-unit-model` `Desktop Head Unit` per run through `ACTION_SET_SETTINGS`, and reads them back with `ACTION_GET_SETTINGS`. `allow-external-configuration` is `true`, because both verbs are in `AutomationCommandPolicy.CONFIGURING` on `fff96699`. The announce line on the wire is the proof of the value in force.

## 6. Shell setup

Make `hur-wifi-test-scripts/bluetooth-audio-disabled-usb-connect-round5/`. Copy into it from `../bluetooth-audio-disabled-usb-connect-round4/`, as the rig ran them in round 4: `lib1008r3.sh`, `lib1008r4.sh`, `lib1008r5.sh`, `ui1008.py`, `navxy.py`, `ohu_setkeys.py`. Save `lib1008-round5.sh` below beside them. List all seven in Setup notes. If a function does not match the real line or dump format, fix it, say so in Setup notes, and keep going. Run each run as one script call under `flock /tmp/ohu-rig.lock`. One adb call at a time per unit; the three log captures are the only streams that run beside them (house rule 8).

Before the round, find a leftover thermal watcher or capture and kill it by pid: `ps aux | grep -E "[r]ig_thermal.sh watch|[s]leep 20|[l]ogcat"`.

Every shell sources, in this order:

```bash
cd hur-wifi-test-scripts/bluetooth-audio-disabled-usb-connect-round5
source ./lib1008r3.sh; source ./lib1008r4.sh; source ./lib1008r5.sh; source "$OUT/r5.env"; source ./lib1008-round5.sh
```

**`lib1008-round5.sh`** (source it last; it needs `rd` from the round 4 libs):

```bash
# lib1008-round5.sh : round 5 additions. Needs HU PH DH OUT DHU_BT POCO_BT KEYS0 KEYS1 DEVNAME from r5.env.

# lay0 / lay1 : the two layouts. orun reads KEYS and ADDR; pre_rig reads LAYOUT.
lay0() { LAYOUT=0; ADDR=$POCO_BT; SUF=${POCO_BT: -5}; KEYS=$KEYS0; echo "$(date +%T) layout 0" >> "$OUT/marks.log"; }
lay1() { LAYOUT=1; ADDR=$DHU_BT; SUF=${DHU_BT: -5}; KEYS=$KEYS1; echo "$(date +%T) layout 1" >> "$OUT/marks.log"; }

# bat : D-POCO's and D-MOTO's battery. Below 30 on D-POCO: charge it on the PC cable before the next run.
bat() { echo -e "${RUN:-}\tbattery poco=$(adb -s "$HU" shell dumpsys battery | grep -a -m1 level | tr -dc 0-9) moto=$(adb -s "$PH" shell dumpsys battery | grep -a -m1 level | tr -dc 0-9)" | tee -a "$OUT/summary.tsv"; }

# pre_rig : layout 0 = D-POCO Bluetooth on; layout 1 = D-POCO Bluetooth off and D-MOTO's A2DP and hands-free on D-HU (D-HU cycle, then H10)
pre_rig() { local b l e
  if [ "$LAYOUT" = 0 ]; then
    b=$(pocobt); [ "$b" = enabled:true ] || { adb -s "$HU" shell svc bluetooth enable; sleep 10; b=$(pocobt); }
    echo -e "$RUN\tlayout=0 poco_bt=$b" | tee -a "$OUT/summary.tsv"
    [ "$b" = enabled:true ] || { echo -e "$RUN\tVOID D-POCO Bluetooth does not come on" | tee -a "$OUT/summary.tsv"; return 1; }
    echo -e "$RUN\tpre\t$(linkst)" | tee -a "$OUT/links.tsv"; return 0; fi
  b=$(pocobt); [ "$b" = enabled:false ] || { adb -s "$HU" shell svc bluetooth disable; sleep 5; b=$(pocobt); }
  echo -e "$RUN\tlayout=1 poco_bt=$b" | tee -a "$OUT/summary.tsv"
  [ "$b" = enabled:false ] || { echo -e "$RUN\tVOID D-POCO Bluetooth does not stay off" | tee -a "$OUT/summary.tsv"; return 1; }
  l=$(linkst); echo -e "$RUN\tpre\t$l" | tee -a "$OUT/links.tsv"
  case "$l" in *A2dpService=Connected*HeadsetService=Connected*) return 0 ;; esac
  adb -s "$DH" shell svc bluetooth disable; sleep 20; adb -s "$DH" shell svc bluetooth enable; sleep 40
  l=$(linkst); echo -e "$RUN\tpre-after-dhu-cycle\t$l" | tee -a "$OUT/links.tsv"
  case "$l" in *A2dpService=Connected*HeadsetService=Connected*) return 0 ;; esac
  cue "H10: on D-MOTO open Bluetooth settings and press Navegadortz2 in the device list. Then do not touch the phone"
  echo "$(date +%T) ${RUN:-} H10" >> "$OUT/hand-steps.log"
  e=$((SECONDS+120)); while [ $SECONDS -lt $e ]; do sleep 10; l=$(linkst)
    case "$l" in *A2dpService=Connected*HeadsetService=Connected*) echo -e "$RUN\tpre-after-h10\t$l" | tee -a "$OUT/links.tsv"; home; return 0 ;; esac; done
  home; echo -e "$RUN\tVOID D-MOTO is not on D-HU with A2DP and hands-free" | tee -a "$OUT/summary.tsv"; return 1; }

# fresh / rd : pre, live, end+60 and the clearing points reopen D-HU's screen first (BACK, then ui_open, H12),
# up to 4 injected inputs a run; the SIM chooser (H4) takes the fifth
declare -F rd_base >/dev/null || eval "rd_base() $(declare -f rd | tail -n +2)"   # once per shell
fresh() { local n; n=$(grep -acF -- "$RUN" "$OUT/inputs.log" 2>/dev/null); n=${n:-0}
  if [ "$n" -ge 4 ]; then echo -e "$RUN\t$1\treused-screen inputs=$n" | tee -a "$OUT/summary.tsv"; return 0; fi
  adb -s "$PH" shell input keyevent KEYCODE_BACK; sleep 1; ui_open; }
rd() { case "$1" in pre|live|end+60|clr3m|clr5m|clrbt|clr-live|clr-end+60) fresh "$1" ;; esac; rd_base "$1"; }

# av <point> : as round 4 (14 lines to Devices:), plus D-HU's audio_flinger "Standby: no" count
av() { local d="$OUT/av"; mkdir -p "$d"
  adb -s "$PH" shell dumpsys audio > "$d/$RUN.$1.moto-audio.txt"; sleep 0.3
  adb -s "$PH" shell dumpsys bluetooth_manager > "$d/$RUN.$1.moto-bt.txt"; sleep 0.3
  adb -s "$PH" shell dumpsys media_session > "$d/$RUN.$1.moto-ms.txt"
  adb -s "$DH" shell dumpsys bluetooth_manager > "$d/$RUN.$1.dhu-bt.txt"; sleep 0.3
  adb -s "$DH" shell dumpsys media.audio_flinger > "$d/$RUN.$1.dhu-af.txt"
  echo -e "$RUN\t$1\tmoto_a2dp_play=$(grep -acE "$A2DP_PLAY_RE" "$d/$RUN.$1.moto-bt.txt")\tdhu_sink_play=$(grep -acE "$SINK_PLAY_RE" "$d/$RUN.$1.dhu-bt.txt")\tdhu_standby_no=$(grep -acF 'Standby: no' "$d/$RUN.$1.dhu-af.txt")\tmusic_dev=$(grep -a -A14 -- '- STREAM_MUSIC:' "$d/$RUN.$1.moto-audio.txt" | grep -a -m1 'Devices:' | tr -d '\r' | sed 's/^ *//')" | tee -a "$OUT/av.tsv"; }

# usum4 <run> : unit-side counts, one row in table.tsv, the unbond abort and the pairing-dialog check
usum4() { local c=$CAP r=$1 ann ssl ma dis ro pr wd ub ubp h1 foc
  ann=$(grep -aoE 'Bluetooth service announced with carAddress=[^ ]+ \(bt-announce=[a-z]+\)' $c | sed 's/^Bluetooth service announced with //' | sort | uniq -c | sed 's/^ *//' | paste -sd';')
  ssl=$(grep -acF 'SSL handshake complete' $c); ma=$(grep -acF 'Media Start Request AUDIO:' $c)
  dis=$(wc_ $PCAP $r 'disabling A2dp via profile disabler'); ro=$(wc_ $PCAP $r 'disabling A2dp route while in projection')
  pr=$(wc_ $PCAP $r 'Sending a pairing request'); wd=$(wc_ $PCAP $r 'Wrong device is being paired')
  ub=$(( $(wc_ $PCAP $r 'Device was unbonded at some point') + $(win $PCAP $r | grep -aF "${DHU_BT: -5}" | grep -acF 'BOND_NONE') ))
  ubp=$(win $PCAP $r | grep -aF "${POCO_BT: -5}" | grep -acF 'BOND_NONE')
  h1=$(awk -F'\t' -v r="$r" '$1 == r && $2 ~ /^H1-recovery-/' "$OUT/summary.tsv" | wc -l)
  foc=$(adb -s "$PH" shell dumpsys window | grep -a -m1 mCurrentFocus | tr -d '\r' | sed 's/^ *//')
  { echo "== $r"
    echo "layout=$LAYOUT addr=$ADDR"
    echo "hu.verbs_whole=$(grep -acF 'AutomationReceiver: ' $c) hu.check_usb=$(grep -aF 'AutomationReceiver: ' $c | grep -acF ACTION_CHECK_USB) hu.set=$(grep -aF 'AutomationReceiver: ' $c | grep -acF ACTION_SET_SETTINGS) hu.get=$(grep -aF 'AutomationReceiver: ' $c | grep -acF ACTION_GET_SETTINGS)"
    echo "hu.announce=$ann"
    echo "hu.announce_empty=$(grep -acF 'BT MAC Address is empty, so no Bluetooth service is announced' $c)"
    echo "hu.ssl=$ssl hu.media_audio=$ma"
    echo "hu.live_at_stop=$(win $c $r | LC_ALL=C awk -v m="AutomationMarker: $r-stop" '
      index($0, "SSL handshake complete") { s = 1; d = 0 } index($0, "AapService: session state disconnected") { d = 1 }
      index($0, m) { print (s && !d) ? 1 : 0; x = 1; exit } END { if (!x) print 0 }')"
    echo "hu.disconnected:"; grep -aF 'AapService: session state disconnected' $c | head -3
    echo "hu.acc_mode=$(grep -acF 'Found device already in accessory mode' $c) hu.vfail=$(grep -acF 'Version request send failed' $c) hu.tls_parse=$(grep -acF 'Unable to parse TLS packet header' $c) hu.usb_perm=$(grep -acF 'Requesting USB permission for' $c)"
    echo "hu.fatal=$(grep -acF 'FATAL EXCEPTION' $c)"
    echo "dh.wake=$(grep -acE 'MATCH! Starting AapService|Attempting active poke to device' $DCAP) dh.openhu=$(grep -acE '/OPENHU *\(' $DCAP)"
    echo "ph.disabler=$dis ph.route_off=$ro ph.noroute=$(wc_ $PCAP $r 'No high priority audio routes available')"
    echo "ph.pairreq=$pr ph.wrongdev=$wd ph.unbond=$ub ph.unbond_poco=$ubp"
    echo "ph.pairing_state:"; win $PCAP $r | grep -aE 'STATE_REQUESTING_CAR_PAIRING_PREPARATION|Bluetooth pairing method chosen' | head -4
    echo "ph.fatal_processes:"; win $PCAP $r | grep -a -A1 'FATAL EXCEPTION' | grep -aF 'Process: ' | head -3
    echo "ph.restarts=$(cat "$PCAP.restarts" 2>/dev/null | wc -l) ph.focus_at_end=$foc"
  } | tee -a "$OUT/summary.tsv"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$r" "$LAYOUT" "${ann:-none}" "$ssl" "$ma" "$dis" "$ro" "$pr" "$wd" "$ub" "$ubp" "$h1" >> "$OUT/table.tsv"
  if [ "$ub" -gt 0 ]; then echo "$r unbond" > "$OUT/STOP"; echo -e "$r\tABORT unbond of $DEVNAME" | tee -a "$OUT/summary.tsv"; fi
  case "$foc" in *BluetoothPairing*)
    echo -e "$r\tpairing-dialog $foc" | tee -a "$OUT/summary.tsv"
    cue "H11: on D-MOTO press Cancel on the pairing dialog. Never accept it"; echo "$(date +%T) $r H11" >> "$OUT/hand-steps.log"; sleep 20 ;; esac; }

# ctl <run> : VOID, NO_DISABLE, DISABLES or DISABLES_SWITCH_OFF (section 3)
ctl() { local r=$1 t d p l v
  v=$(awk -F'\t' -v r="$r" '$1 == r && $2 ~ /^VOID/' "$OUT/summary.tsv" | wc -l)
  t=$(awk -F'\t' -v r="$r" '$1 == r' "$OUT/table.tsv" | tail -1)
  d=$(echo "$t" | cut -f6); p=$(grep -a "^$r p.disabled_by_gh_before_stop=" "$OUT/policy.tsv" | tail -1 | cut -d= -f2)
  l=$(grep -a "^$r t.live " "$OUT/summary.tsv" | tail -1 | grep -aoE 'state=[A-Z_]+' | cut -d= -f2)
  if [ "$v" -gt 0 ] || [ -z "$t" ] || [ "$(echo "$t" | cut -f4)" != 1 ]; then echo VOID
  elif [ "${d:-0}" -ge 1 ] && [ "${p:-0}" -ge 1 ]; then case "$l" in OFF|GREY_OFF) echo DISABLES_SWITCH_OFF ;; *) echo DISABLES ;; esac
  else echo NO_DISABLE; fi; }

# step <run> <arm> <how> "<options>" [noprechk] : skips once $OUT/STOP exists
step() { [ -e "$OUT/STOP" ] && { echo -e "$1\tSKIPPED round stopped on an unbond" | tee -a "$OUT/summary.tsv"; return 4; }
  RUN=$1; bat
  if [ "$5" != noprechk ]; then prechk "$1" || return 1; fi
  orun "$1" "$2" "$3" "$4"; local rc=$?; echo -e "$1\tctl=$(ctl "$1")" | tee -a "$OUT/controls.tsv"; return $rc; }
```

`table.tsv` columns: `run layout hu.announce hu.ssl hu.media_audio ph.disabler ph.route_off ph.pairreq ph.wrongdev ph.unbond ph.unbond_poco h1`. `orun` must call `usum4` after `cap_stop`, as in round 4. If `lib1008r5.sh` calls its summary by another name, wire `usum4` in there and say so in Setup notes.

**Read points** are round 4's (round 4 brief, section 6). The only change: `fresh` reopens D-HU's screen before `pre`, `live`, `end+60` and the clearing points, up to 4 injected inputs per run (H12).

## 7. The lines that decide the runs

App lines were checked with `grep -F -r` against `app/src/main` on `fff96699`. All print at INFO or above. Counts are inside the run's window, from `<run>-start` to `<run>-end`, except the `hu.*` counts, which take the whole run capture.

| Line (fixed substring) | File | Meaning |
|---|---|---|
| `Bluetooth service announced with carAddress=` | `$CAP` | the address on the wire, then ` (bt-announce=real)` or ` (bt-announce=skip)` |
| `BT MAC Address is empty, so no Bluetooth service is announced` | `$CAP` | an empty `bt-address`: the run is VOID |
| `SSL handshake complete` | `$CAP` | a session formed (never prefix `Handshake:`) |
| `Media Start Request AUDIO:` | `$CAP` | the media channel opened over the cable (from `Media Start Request %s: session=`) |
| `AapService: session state disconnected` | `$CAP` | the session ended (from `AapService: session state `) |
| `AutomationReceiver: ` with `ACTION_CHECK_USB`, `ACTION_SET_SETTINGS`, `ACTION_GET_SETTINGS` | `$CAP` | a verb landed |
| `AutomationMarker: ` | `$CAP` | a run marker |
| `Found device already in accessory mode`, `Version request send failed`, `Requesting USB permission for` | `$CAP` | the USB bring-up and the re-attach fault |
| `Unable to parse TLS packet header` | `$CAP` | the second re-attach fault (an `SSLException` message) |
| `MATCH! Starting AapService`, `Attempting active poke to device` | `$DCAP` | D-HU's app woke up: the run is VOID |
| `/OPENHU (` | `$DCAP` | any line of D-HU's app; expected 0 (informational) |
| `FATAL EXCEPTION`, then `Process: ` | `$CAP`, `$PCAP` | a crash, and whose |
| `RIGMARK` | `$PCAP`, `$DCAP` | the run markers |
| `getProfileConnectionPolicy: XX:XX:XX:XX:` | `$PCAP` | a read of the stored policy; the first one for `$SUF` in a window is **P0** |
| `setConnectionPolicy(`, `/BluetoothA2dp(` | `$PCAP` | a request to change the policy, and the pid that asked |
| `Saved connectionPolicy `, `/A2dpService(` | `$PCAP` | the stored value: `100` on, `0` off |
| `Start proc ` | `$PCAP` | maps a pid to a process name |
| `disabling A2dp via profile disabler` | `$PCAP` | **the control signal.** Android Auto's disabler ran |
| `disabling A2dp route while in projection` | `$PCAP` | recorded (`ph.route_off`); round 4 saw it with no disabler |
| `Sending a pairing request`, `Wrong device is being paired` | `$PCAP` | **recorded, not an abort** (`ph.pairreq`, `ph.wrongdev`) |
| `STATE_REQUESTING_CAR_PAIRING_PREPARATION`, `Bluetooth pairing method chosen` | `$PCAP` | the pairing flow, quoted per run (round 4 lines) |
| `Device was unbonded at some point` | `$PCAP` | **abort** |
| `BOND_NONE` with D-HU's suffix on the same line | `$PCAP` | **abort.** With D-POCO's suffix: recorded only (`ph.unbond_poco`) |
| `No high priority audio routes available` | `$PCAP` | Android Auto found no call route |
| `BluetoothPairing` | D-MOTO's `mCurrentFocus` | a pairing dialog is up: H11 |
| `Standby: no` | D-HU's `dumpsys media.audio_flinger` | an output on D-HU is playing (`dhu_standby_no`) |

**Reading a run's policy (`psum`) and a switch (`ui1008.py`)** is unchanged from round 4 (round 4 brief, section 7). A `com.android.settings` setter inside an `-H<k>` window is the hold test, not a fault.

## 8. Runs

### Prepare (P), on cables

Round 4's Prepare steps 1 to 11 (round 4 brief, section 8), with these changes:

- **Step 2:** back up both settings files and diff each against **round 4's closing** backup. State the delta, even if zero. Round 4 skipped this; do not skip it.
- **Step 3:** `stat` both `shared_prefs/` directories and record owner, group and mode. Round 4 skipped this; do not skip it.
- **Step 4:** also record D-POCO's own bond list: `adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -i -A30 "Bonded devices" > $OUT/poco-bonds.txt` (informational).
- **Step 5 moves to Stage P.**
- **Step 7:** do not read the Android Auto version. Keep the `setprop log.tag.* VERBOSE` lines, and add `CAR.BT.SVC.LITE` if it is not in the list. Battery: D-POCO 80 or more, D-MOTO 50 or more.
- **Step 10:** write `$OUT/r5.env`. Copy the Stage 0 values from round 4's `r4.env` or `s0.env` (`ROUTE`, `DACTION`, `DEXTRA`, `SETTINGS_CMP`, `LABEL`, `DEVNAME`, `BTCYC`, `A2DP_PLAY_RE`, `SINK_PLAY_RE`, `MEDIA`, `ROUTE_RE`). Do not copy `HU`: round 4's file may name D-HP. Then:
  ```bash
  HU=$POCO_IP:5555; PH=$MOTO_IP:5555; DH=27870808938846; BASE=$OUT/settings_backup_poco.xml; WANT_MD5=...
  DHU_BT=...; POCO_BT=...; MOTO_BT=...; DEVNAME=...
  COMMON="int:wifi-connection-mode=0 int:log-level=2 int:onboarding-version=2 bool:allow-external-configuration=true bool:enable-audio-sink=true bool:kill-on-disconnect=false bool:auto-connect-last-session=false bool:auto-connect-single-usb=false bool:auto-start-on-usb=false bool:reopen-on-reconnection=false bool:use-libusb=false set:connection-modes=usb,wifi del:bt-announce del:head-unit-make del:head-unit-model del:video-profile-starvation-cap del:native-aa-wake-damage-verdict del:native-aa-wireless del:wifi-launcher-mode"
  KEYS0="$COMMON str:bt-address=$POCO_BT"; KEYS1="$COMMON str:bt-address=$DHU_BT"
  REP=0
  ```
- **Step 11:** the operator message of section 4.

### Stage P: pre-flight (no session, before Stage 0)

Do these steps in order. Put each result in Setup notes and in one `table.tsv`-style row in the results (bond diff, profiles, re-pair yes or no).

1. **Bonded list.**
   ```bash
   adb -s $PH shell dumpsys bluetooth_manager | grep -a -i -A30 "Bonded devices" > $OUT/bonds-before.txt
   grep -aF "$DHU_BT" $OUT/bonds-before.txt; grep -aF "$POCO_BT" $OUT/bonds-before.txt
   mkdir -p $OUT/prev
   gh release download rig-evidence-bluetooth-audio-disabled-usb-connect --repo o-jcardenass/open-headunit \
     -p 'bluetooth-audio-disabled-usb-connect-round3-captures.zip' -D $OUT/prev
   gh release download rig-evidence-bluetooth-audio-disabled-usb-connect-round4 --repo o-jcardenass/open-headunit \
     -p 'bluetooth-audio-disabled-usb-connect-round4-captures.zip' -D $OUT/prev
   (cd $OUT/prev && for z in *.zip; do mkdir -p "${z%.zip}" && unzip -q -o "$z" -d "${z%.zip}"; done)
   find $OUT/prev -name 'bonds-before*.txt' -o -name 'bonds-after*.txt'
   ```
   The list must name `$DHU_BT` with the name `Navegadortz2`. Diff it against each file `find` prints. If round 3's asset has no bonds file, say so and diff against round 4's only. Round 4's lists may carry D-HP (`88:33:14:53:1A:75`), which round 4 removed at its close.
2. **Profiles.** `RUN=P; linkst`. The round 4 layout needs `A2dpService=Connected HeadsetService=Connected` (round 4 Stage 0, after H10).
3. **Repair, in this order, at most once each:**
   - `$DHU_BT` is bonded with its name, and only the profiles are down: `adb -s 4f4027e9 shell svc bluetooth disable`, then `LAYOUT=1; RUN=P; pre_rig` (D-HU cycle, then H10).
   - `$DHU_BT` is missing, or its name changed, or `pre_rig` returned 1: **H7, re-pair D-HU once.** Then repeat steps 1 and 2. Record that a re-pair ran, and why.
   - Still not bonded with both profiles connected: the round is **UNTESTABLE** before any session. Go to the close.
4. **Stored policy and switch at `pre`.**
   ```bash
   adb -s $PH logcat -d -v time | grep -aF "Saved connectionPolicy $DHU_BT = " | tail -1
   adb -s $PH logcat -d -v time | grep -aF "getProfileConnectionPolicy: XX:XX:XX:XX:${DHU_BT: -5}" | tail -1
   RUN=P; ui_open; rd_base pre; home
   ```
   Record the last stored value (or `none in buffer`) and the switch state. Round 4 read `ON` with the policy unset. Record both. Do not grade them. If H7 ran, these values are the new baseline for every later run.

### Stage 0: calibration (no session)

Round 4's Stage 0 (round 4 brief, section 8), unchanged, with one change: start from the values that `r5.env` copied. A step that confirms its round 4 value needs no host step. Change a value only when the step shows it no longer works, and quote both values in Setup notes. Step 4 (D-POCO's own screen on D-MOTO) is optional. **Stage 0 gate:** a dump names D-HU and its `LABEL` row reads `ON`. Otherwise the round is UNTESTABLE.

### Validity of a run

Round 4's validity items 1 to 8 (round 4 brief, section 8), with these changes. Read each from `summary.tsv`, `table.tsv`, `policy.tsv` and `$OUT/<run>.keys`:

- **Item 1:** `hu.announce` is exactly one line per session, with the run's value:
   - R0, R0x: `carAddress=<POCO_BT> (bt-announce=real)`.
   - R1, R1x, R2a to R3b, S4, and every `Rrep`: `carAddress=<DHU_BT> (bt-announce=real)`.
   - S1 to S3: `carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)`.
- **Item 2:** `hu.ssl` = 1. R3b: 2, when its clearing session formed.
- **Item 3:** `getk` in `$OUT/<run>.keys` shows the arm's `bt-announce`, the layout's `bt-address`, `Google` and `Desktop Head Unit`.
- **Item 9, abort, narrowed:** `ph.unbond` = 0. Otherwise `usum4` has written `$OUT/STOP`: stop, read the bonded list (`adb -s $PH shell dumpsys bluetooth_manager | grep -a -i -A30 "Bonded devices"`), do not re-pair, and go to the close. `ph.pairreq`, `ph.wrongdev` and `ph.unbond_poco` are recorded only.

A run with no session after two H1 recoveries is VOID (`ctl` prints `VOID`). Run it once more under `<id>x`. Two VOID runs of the same id make it UNTESTABLE. A run that misses items 1 to 7 for another reason is also VOID, with the item named. `hu.media_audio` of 0 does not void a run. It makes that run's second-copy check INCONCLUSIVE.

### R0: the round 3 control (layout 0)

```bash
lay0; step R0 real clean "" noprechk; C0=$(ctl R0)
[ "$C0" = VOID ] && { step R0x real clean "" noprechk; C0=$(ctl R0x); }
echo -e "R0\tC0=$C0" | tee -a "$OUT/controls.tsv"
```

R0 runs no `prechk`, no call and no hold test: D-POCO's entry has no switch to repair, and R0 needs only the disabler line.

- **Valid** by the validity list, with the layout 0 announce line.
- **Measure:** `ph.disabler`, `p.disabled_by_gh_before_stop`, `p.P0`, `p.reading` (D-POCO's entry; a clean end should read `RESTORED`), `ph.route_off`, `ph.pairreq`, and the D-HU switch at `pre`, `live` and `end+60` as data.
- R0 is not PASS or FAIL. Its result is `C0`.

### R1: the round 4 control (layout 1)

```bash
lay1; step R1 real clean "call hold-live"; C1=$(ctl R1)
[ "$C1" = VOID ] && { step R1x real clean "call hold-live"; C1=$(ctl R1x); }
echo -e "R1\tC1=$C1" | tee -a "$OUT/controls.tsv"
```

The hold test runs only where the switch reads off (round 4 R1p guard). Measure what round 4's R1 measured: the `live` state with its `policy=` column, the `hold1` outcome with `snap_ms` and the snap-back owner, `av live`, `av after-hold1`, and the call route. A switch that reads `ON` while `policy=0` at the same reading is a finding: mark it `STALE_OR_ROM` and quote both.

**What R0 against R1 says** (recorded, not graded):

| `C0` | `C1` | Reading |
|---|---|---|
| `DISABLES*` | `DISABLES*` | round 4's miss did not repeat |
| `DISABLES*` | `NO_DISABLE` | the round 4 layout is the cause: D-HU's address, or D-POCO's Bluetooth off. This round does not separate the two |
| `NO_DISABLE` | `NO_DISABLE` | D-MOTO changed since round 3, not the layout |
| `NO_DISABLE` | `DISABLES*` | not expected; report it |

`DISABLES*` is `DISABLES` or `DISABLES_SWITCH_OFF`. A `VOID` after its `x` re-run is UNTESTABLE for that control.

### Stage R: the grey state (only when `C1` is `DISABLES_SWITCH_OFF`)

```bash
if [ "$C1" = DISABLES_SWITCH_OFF ]; then
  step R2a real pull   "hold-60"
  step R2b real pull   "hold-60"
  step R3a real ghkill "clear-time"
  step R3b real ghkill "clear-session"
else echo -e "R2a-R3b\tSKIPPED C1=$C1" | tee -a "$OUT/summary.tsv"; fi
```

Before R2a and R2b, remind the operator: "Wait for the PULL cue. Pull the cable from D-POCO. Leave it out until the next plug-in cue." Each run measures what round 4's brief gives for it (round 4 brief, section 8, R2a to R3b). Goal 1 is a measurement, not a PASS or FAIL on the app.

### Stage S: the skip value (the point of the round)

```bash
step S1 skip clean "call"
step S2 skip pull  ""
step S3 skip ghkill ""
step S4 real clean "call hold-live"
```

These run in every case. S1 to S3 run no hold test.

**Graded in every case, per run** (no control needed):

| Part | Runs | PASS | FAIL |
|---|---|---|---|
| Announce | S1, S2, S3 | `hu.announce` is `1 carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)` per session | any other value, or none |
| Media over the cable | S1 | `hu.media_audio` >= 1 | `hu.media_audio` = 0 while `media` ran |
| No second copy | S1, at `live` | `moto_a2dp_play=0`, `dhu_sink_play=0` and `dhu_standby_no=0` | `moto_a2dp_play` >= 1, or both `dhu_sink_play` >= 1 and `dhu_standby_no` >= 1 |
| Back to real | S4 | `getk` shows `"bt-announce":"real"` and `hu.announce` is `1 carAddress=<DHU_BT> (bt-announce=real)` | either one differs |

The second-copy check is **INCONCLUSIVE** when the three `av live` fields disagree in any other way, or when `hu.media_audio` = 0. Quote all three and `music_dev`. Round 4 R1 and R1p read `dhu_sink_play=1` with `moto_a2dp_play=0`, which is why D-HU's `Standby: no` count is added.

**The skip claim ("skip does not disable"), graded by control:**

| Controls | S1, S2, S3 PASS | S1, S2, S3 FAIL | Switch readings |
|---|---|---|---|
| `C1` = `DISABLES_SWITCH_OFF` | round 4's S1 to S3 grades: every `pre`, `live`, `end+5`, `poll` and `end+60` reading is `ON` (`UNREAD` left out and counted), `ph.disabler` = 0, no `p.set` line with `value=0`, and S1's second copy as above | any reading `OFF`, `GREY_ON`, `GREY_OFF` or `NOROW`, or `ph.disabler` >= 1, or a `p.set` line with `value=0` | graded |
| `C1` = `DISABLES`, or `C1` is not `DISABLES*` and `C0` is `DISABLES*` | `ph.disabler` = 0 | `ph.disabler` >= 1 | data only |
| neither `C0` nor `C1` is `DISABLES*` | **ungraded** | **ungraded** | data only |

**What a PASS would look like if the skip value did nothing:** `ph.disabler` >= 1 and, under a switch control, the switch reading `OFF` at `live` with `policy=0`, as in the control. A reading of `ON` with `policy=none` is also expected under `skip`, because nothing changes the policy. `p.P0` says what it was.

**S4's disabler** is graded only when `C1` is `DISABLES*`: PASS when `ph.disabler` and `p.disabled_by_gh_before_stop` are both 1 or more. Otherwise record both counts. Compare S4 with R1: `live` state, `hold1` outcome, call route, `ph.pairreq`. A difference shows a drift on D-MOTO across the round.

**Recorded, not graded:** the call route in R1, S1 and S4 (head unit over hands-free, the phone's earpiece or speaker, or `No high priority audio routes available`), `ph.noroute`, `music_dev` at `live`, and `ph.pairreq` per run.

### Closing the round

Round 4's close (round 4 brief, section 8, steps 1 to 9), in that order, run with `lay1` in force. Changes:

- **Step 1:** `lay1; RUN=END; ui_open; rd_base close; home`. If it does not read `ON`, run `prechk END`. Never leave the rig with D-HU's switch off.
- **Step 3:** after the restore, D-POCO's `bt-address` must match `settings_backup_poco.xml`. A `bt-address` left at D-HU's address announces the wrong head unit in every later round.
- **New step 6a:** D-POCO's entry on D-MOTO. Quote R0's `p.reading` and `p.END_at_end`. If it is not `100`, say so in the results; one clean session in layout 0 repairs it (round 2), and that is a run for a later round, not this one.
- **Step 6:** `bonds-after.txt` must still list `$DHU_BT` and `$POCO_BT`. Diff it against `bonds-before.txt`.
- **Step 9:** put `table.tsv`, `controls.tsv`, every `toggle.tsv` and `hold.tsv` line, `av.tsv`, `calls.tsv`, `links.tsv` and the `p.reading`, `p.P0` and `p.restore` lines in the results file. The dumps and captures go in the asset `bluetooth-audio-disabled-usb-connect-round5-captures.zip` on the release `rig-evidence-bluetooth-audio-disabled-usb-connect`.

**Stop rule.** At most 17 runs: R0, R0x, R1, R1x, R2a, R2b, R3a, R3b, S1, S2, S3, S4, at most 3 more `x` re-runs after R1x, and at most 2 `Rrep` repair runs. Stop at once when `$OUT/STOP` exists (an unbond). If the host stays above 75C for 30 min, stop and mark the remaining runs UNTESTABLE (host thermal). If D-POCO's battery reads below 30 before a run, charge it on the PC cable first; this is a pause, not a stop.

## 9. Do not re-run

These are settled. Delays are on D-MOTO's clock.

| Run | P0 | After the end | Delay |
|---|---|---|---|
| Round 1 U1a, U1b, U1c, U4 (clean, USB) | `-1`, `100` | Gearhead set `100` | 1811 to 1853 ms from the stop |
| Round 1 U2 (`blank`), U3 (`skip`) | none | no `setConnectionPolicy(` for the address | |
| Round 1 N1, N2a, N2b; round 3 N0p (clean, Native AA) | `100` | Gearhead set `100` | 1361 to 1448 ms from the stop |
| Round 2 U5a2; round 3 U5a4 to U5a9 (cable pull, USB) | `100` | Gearhead set `100` (RESTORED) | -202 to 460 ms from the disconnect |
| Round 2 U5b1, U5b2; round 3 N4a1, N4a2x (Android Auto force-stop) | `100` | NOT_RESTORED, stored `0` | |
| Round 2 U6a, U6b, U6r2v; round 3 N4b1, N4b2 (clean, from `0`) | `0` | Gearhead set `100` | 880 to 1864 ms from the stop |

- **The stored policy is settled for every end.** This round reads the switch and the disabler, not the policy timing.
- **`blank` stops the disable.** It is not re-run: Android Auto handles `blank` and `skip` in the same branch, and only `skip` is a candidate.
- **Round 4 R1 and R1p are not repeated as such.** This round's R1 is R1p's layout. D-HP is not used again.
- **A switch can read `ON` with the policy unset** (round 4, `policy=none`, `p.P0=100`). Record it; do not re-measure it.

## 10. Report back

1. **The controls:** `C0` and `C1`, each with `ph.disabler`, `p.disabled_by_gh_before_stop`, `ph.route_off`, `ph.pairreq` and the `live` switch state, and the R0 against R1 reading from the table in section 8. Add the Stage P result: bond diff, profiles, and whether H7 ran.
2. **Stage S:** the verdict of each graded part for S1, S2 and S3, with `ph.disabler` per run, the count of `ON` readings against all readings, S1's three `av live` fields, and which control row graded the skip claim (or "ungraded"). S4's verdict with its disabler counts.
3. **Calls and bonds:** the call route in R1, S1 and S4, `ph.pairreq` and `ph.unbond` for every run, and any pairing dialog (H11).

The runs also grep these lines. They are not this app's, so they cannot be checked against `app/src`. `Unable to parse TLS packet header` is an `SSLException` message from D-POCO's round 2 capture. `getProfileConnectionPolicy: XX:XX:XX:XX:`, `setConnectionPolicy(`, `Saved connectionPolicy `, `/BluetoothA2dp(`, `/A2dpService(`, `Start proc ` and `disabling A2dp via profile disabler` are in D-MOTO's captures in the round 1 to 3 assets. `disabling A2dp route while in projection`, `Sending a pairing request`, `STATE_REQUESTING_CAR_PAIRING_PREPARATION` and `Bluetooth pairing method chosen` are quoted from D-MOTO in the round 4 results. `Wrong device is being paired` and `Device was unbonded at some point` are from the 17.5 and 17.8 dex and were not seen on 17.9. `BOND_NONE` is Android's bond state name. `BluetoothPairing` is the start of Android Settings' pairing dialog activity name, not yet seen on D-MOTO. `Standby: no` is an `audio_flinger` dump field that round 4 used on D-HU. `No high priority audio routes available` is Android Auto's call manager line. `FATAL EXCEPTION` and `Process: ` are the system's crash lines, `RIGMARK` is the marker tag that the runs write, and `/OPENHU (` is the log tag of D-HU's own app:

```text
Unable to parse TLS packet header
getProfileConnectionPolicy: XX:XX:XX:XX:
setConnectionPolicy(
Saved connectionPolicy 
/BluetoothA2dp(
/A2dpService(
Start proc 
disabling A2dp via profile disabler
disabling A2dp route while in projection
Sending a pairing request
STATE_REQUESTING_CAR_PAIRING_PREPARATION
Bluetooth pairing method chosen
Wrong device is being paired
Device was unbonded at some point
BOND_NONE
BluetoothPairing
Standby: no
No high priority audio routes available
FATAL EXCEPTION
Process: 
RIGMARK
/OPENHU (
```

The decisive strings below are the app's own, one per line, for a mechanical `grep -F -r` against `app/src/main` on `fff96699`. All 15 matched on 2026-10-08. Two are the fixed parts of composed lines: `AapService: session state ` is the fixed part of `AapService: session state disconnected`, and `Media Start Request %s: session=` is the format string of `Media Start Request AUDIO:`. The action names are grepped inside the `AutomationReceiver: ` line, which prints the full action.
