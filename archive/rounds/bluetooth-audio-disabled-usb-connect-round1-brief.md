# bluetooth-audio-disabled-usb-connect, round 1 brief

Published name on the transfer branch: `bluetooth-audio-disabled-usb-connect-round1-brief.md`.

This is a new thread and a **measurement round**. The build carries a probe that never merges. No fix is on trial. The round reads what Android Auto on the phone does to the phone's A2DP setting for the head unit, for three values of the Bluetooth address that the head unit announces.

## 1. Build and baseline

- Candidate: branch `fix/bluetooth-audio-disabled-usb-connect` on the fork, SHA **`fff96699`** (`fff96699480d878bb36bd04078d77260231b19dc`). One commit on `main` `145a0c76`. New branch, so no history was rewritten.
  ```bash
  git fetch fork fix/bluetooth-audio-disabled-usb-connect
  git checkout fff96699480d878bb36bd04078d77260231b19dc
  git rev-parse HEAD     # must print fff96699480d878bb36bd04078d77260231b19dc
  ```
- Build with `build_hur.sh` and test with `run_unit_tests.sh`, both with `HUR_DIR=<that checkout>`. The JVM gate is **2747 tests, 0 failures**. A failure stops the round.
- Copy the APK out of `apks/` at once, as `apks/candidate-fff96699.apk`, and record its md5 as `WANT_MD5`.
- DEX symbol for identity (template §5): `BluetoothAnnouncePolicy`.
- `send ACTION_QUERY_STATE` on each head unit must reply with a `commit` that begins `fff96699`.
- **No baseline APK.** Every comparison is a settings change on the candidate.

## 2. What this is and why it exists

**The report.** A user's phone is paired with the head unit over Bluetooth. When Android Auto connects by cable, the phone's Bluetooth settings switch off the head unit's "Media audio" toggle by themselves. The report has no app version, no log, and does not say if the toggle comes back.

**What the code read found** (Android Auto 17.8.163744, decompiled; not yet measured):

- Android Auto has a class that logs under `CAR.BT.A2dpDisabler`. It sets the phone's A2DP connection policy to 0 (FORBIDDEN) for the address that the head unit announces in its Bluetooth service (`carAddress`).
- It runs when Android Auto starts to send media audio to the head unit. With no media sink, it never runs.
- With Android Auto's compiled defaults, it runs only for a car whose identity is on a server-pushed list, `UsbBabysitter__a2dp_fix_car_list`. The compiled default entry is head unit make `Aptiv`, model `Volvo [AAR]`. A second path (`Set A2DP policy for car to`) runs only when `BluetoothPairing__disable_a2dp` is true (default false). So on compiled defaults, our default identity (`Google` / `Desktop Head Unit`) should see no change. A real phone can carry pushed values.
- **The restore.** At a clean session end, the list path sets the policy back to 100 (ALLOWED), but only when the policy was ALLOWED before it disabled it, or when `UsbBabysitter__enable_a2dp_at_projection_end` is true (default false). It keeps this in memory only. **So an Android Auto process death before the session end leaves the toggle off.**
- A blank address, no Bluetooth service, and the value `SKIP_THIS_BLUETOOTH` all stop before the disabler is set up.

**Why the address matters to us.** Since 3.3.0 the app fills in `bt-address` by itself, so almost every install now announces a Bluetooth service. Before that, most installs announced none, and Android Auto skipped the disable.

**The probe.** A new settings key, `bt-announce`, picks what `ServiceDiscoveryResponse` announces. `bt-address` itself does not change.

| `bt-announce` | `carAddress` sent | Our INFO line, once per session |
|---|---|---|
| absent, `real`, or any other value | the stored `bt-address` | `Bluetooth service announced with carAddress=<address> (bt-announce=real)` |
| `blank` | none, the service is left out | `No Bluetooth service announced: bt-announce=blank` |
| `skip` | `SKIP_THIS_BLUETOOTH` | `Bluetooth service announced with carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)` |

The round answers four questions:

1. On this phone's live flags, does the default identity get its A2DP policy disabled? (U1, N1)
2. Does the instrument see a disable when one is forced? The list identity forces it. (U4, N2)
3. With the list identity, do `blank` and `skip` stop the disable? (U2, U3)
4. Does an unclean end leave the policy at 0? (N3, and U5 only if D-POCO's policy starts at ALLOWED)

## 3. What is different about this round

**Layout.** D-HU cannot host USB (`host_connected=false`). So the USB stage uses D-POCO as the head unit and USB host, as in the `usb-version-retry` rounds, with D-MOTO as the phone on D-POCO's OTG port.

| Stage | Head unit | Phone | adb | Runs |
|---|---|---|---|---|
| **U** | D-POCO (`4f4027e9`), Android 15 | D-MOTO (`ZY22GC3BM4`), on D-POCO's OTG port | both over wireless adb | U0 to U5 |
| **N** | D-HU (`27870808938846`), Native AA | D-MOTO, on its PC cable | cables | N0 to N3 |

**Facts that change the runs.**

- **D-POCO is a phone, so it is probably not an A2DP sink.** D-MOTO then has no "Media audio" toggle for it, and its stored A2DP policy for D-POCO is probably not 100. Android Auto still calls `setConnectionPolicy` on the announced address. So Stage U grades on Android Auto's log lines and on D-MOTO's stored policy, never on a toggle.
- **The restore needs ALLOWED before the disable.** If D-POCO's policy does not read 100 before the first session, a restore can never show on D-POCO. That is why the unclean-end runs on D-POCO (U5) are conditional, and why Stage N exists: D-HU is an A2DP sink, so D-MOTO's policy for it starts at 100.
- **Order matters in Stage U.** On D-POCO a disable can leave the policy at 0 for the rest of the stage. Run U1, U2 and U3 before U4, and never change that order.
- **`blank` and `skip` run with the list identity**, not the default one. With the default identity, compiled defaults already give "no disable", so a PASS there would prove nothing. With the list identity, U4 shows that the same identity does disable with a real address. U4's PASS is what makes U2 and U3 readable.
- **A phone that was killed restarts and reconnects.** After an Android Auto force-stop, the head unit gets `ACTION_EXIT` 2 s later. Otherwise a new session runs the disabler again and stores "not ALLOWED" as its before-value, which hides the restore question.
- **Calls on D-POCO cannot reach the head unit.** D-POCO has no hands-free client role, so a call stays on D-MOTO in every arm. The call in U1, U2 and U3 records what Android Auto says (`No high priority audio routes available`) and the telecom route, ungraded. N1's call on D-HU, which does have a hands-free link, is the baseline.
- **Android Auto's lines may not print.** Its `CAR.BT.*` tags are raised to VERBOSE in Prepare. `CarBluetoothService. car address=` prints once per session in which our Bluetooth service is announced, so a run whose phone window has none of it ran with Android Auto's Bluetooth lines invisible. Those runs grade on the stored policy only, and the results say so.
- **D-MOTO carries a USB data link to D-POCO, and wireless adb may drop when it switches to accessory mode.** The phone capture restarts itself and reconnects, and a `logcat -d` dump is taken at each run's end as a backstop.
- **The template's §4 airplane-mode protocol does not apply to Stage U.** D-MOTO's Bluetooth must stay on and bonded to D-POCO, which is the reported condition. Stage N uses it.

**Expected not to run or INCONCLUSIVE, and that is not a failure:**

- U5, if U0 reads D-POCO's policy as anything but 100.
- N3, if N2 shows no disable over Native AA. That would mean the list check is tied to USB.
- Any run whose phone window has no Android Auto Bluetooth line and whose policy did not move. That run grades on the policy only.

## 4. Hand steps, and why no verb exists

The executor prints a line that starts `OPERATOR:`, rings the terminal bell, and appends a line to `$OUT/hand-steps.log` (`cue`). It then waits on a log line or a poll, never on a clock alone.

| Id | Step | Why no verb |
|---|---|---|
| H1 | Move cables: D-POCO and D-MOTO between the PC and D-POCO's OTG port; replug D-MOTO when a USB bring-up fails; pull D-MOTO's cable in U5a | A cable is hardware |
| H2 | Allow a system USB dialog on D-POCO (`systemui` permission or chooser); tick "Always" if it is offered | A system dialog, not our app. Record `dumpsys usb` before and after Stage U (template §7b) |
| H3 | Clear any Android Auto or system screen on D-MOTO that blocks the session (first-car consent, "Use USB for"), and say what it said | Android Auto's screens have no adb lever (`rig-quirks/units/D-POCO.md`) |
| H4 | Dial D-MOTO from a handset outside the rig | Nothing on the rig can make a phone ring. Answer and hang-up are key events on D-MOTO |
| H5 | Pair D-MOTO and D-POCO over Bluetooth, only if Prepare finds them not bonded | Pairing needs a confirmation on both screens |
| H6 | Turn D-MOTO's "Media audio" toggle for D-HU back on, only when a run leaves the policy at 0 | No public API sets another app's A2DP policy |
| H7 | Stage N only, ungraded: open D-MOTO's Bluetooth settings, read the "Media audio" toggle for D-HU, and say on or off | Phone UI, read by eye |

## 5. Settings keys

Write the base keys with the app stopped, back up first (template §1). D-POCO is not rooted: use `pocoput` (section 6). D-HU: use `hu_put` (root). Read every key back before each launch. Record the rig audio keys (`use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`) as found and do not write them.

| Key | Type | D-POCO (Stage U) | D-HU (Stage N) |
|---|---|---|---|
| `wifi-connection-mode` | int | `0` (manual) | `3` (Native AA) |
| `log-level` | int | `2` (INFO) | `2` (INFO) |
| `onboarding-version` | int | `2` | `2` |
| `allow-external-configuration` | boolean | `true` | `true` |
| `enable-audio-sink` | boolean | `true` | `true` |
| `kill-on-disconnect` | boolean | `false` | |
| `auto-connect-last-session` | boolean | `false` | |
| `auto-connect-single-usb` | boolean | `false` | |
| `auto-start-on-usb` | boolean | `false` | |
| `reopen-on-reconnection` | boolean | `false` | |
| `use-libusb` | boolean | `false` | |
| `connection-modes` | string set | `usb,wifi` | |
| `bt-address` | string | `$POCO_BT` (section 8, P4) | as found, do not write |
| `native-driver-selection-mode` | int | | `0` |
| `native-poke-all-paired` | boolean | | `false` |
| `native-poke-bt-macs` | string set | | `$MOTO_BT` |
| `last-connected-native-mac`, `native-preferred-device-mac` | string | | `$MOTO_BT` |
| `bt-announce`, `head-unit-make`, `head-unit-model` | | delete | `bt-announce` delete; the identity as found |
| `video-profile-starvation-cap`, `native-aa-wake-damage-verdict` | | delete | delete |
| `native-aa-wireless`, `wifi-launcher-mode` | | delete | |

**`bt-announce`, `head-unit-make` and `head-unit-model` change per run through `ACTION_SET_SETTINGS`**, after the app has started and before the session. They are in `SettingsBackupManager.backupKeys`, so the verb takes them. Read them back with `ACTION_GET_SETTINGS`. Do not write these three into `settings.xml` by hand. The app's own write can be lost on disk if `shared_prefs/` is root-owned, but the process holds it in memory, and the session reads it in the same process. Our announce line on the wire is the proof of the value in force.

| Arm | `bt-announce` | `head-unit-make` | `head-unit-model` |
|---|---|---|---|
| real, default identity (D-POCO) | `real` | `Google` | `Desktop Head Unit` |
| list identity | per run | `Aptiv` | `Volvo [AAR]` |
| real, D-HU's own identity | key absent | as found | as found |

## 6. Shell setup

Make `hur-wifi-test-scripts/bluetooth-audio-disabled-usb-connect-round1/`. Copy `ohu_setkeys.py` into it from `hur-wifi-test-scripts/projection-teardown-and-relays-round3/` (spec words `int:`, `bool:`, `str:`, `set:`, `del:`). Save `lib1008.sh` below beside it. List both in Setup notes. If a function does not match the real line format, fix it, say so in Setup notes, and keep going. Run each stage under `flock /tmp/ohu-rig.lock`. One adb call at a time per unit; the two log captures are the only streams that run beside them (house rule 8).

**`lib1008.sh`**:

```bash
# lib1008.sh : source after setting HU, PH, OUT, BASE, KEYS, SUF, PLAYER, WANT_MD5 for the stage.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
GH=com.google.android.projection.gearhead
send() { a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s "$PH" shell log -t RIGMARK "$1"; echo "$(date +%T.%3N) $1" >> "$OUT/marks.log"; }
cue() { printf '\a'; echo "OPERATOR: $1 NOW"; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
nl() { echo $(( $(wc -l < "$CAP") + 1 )); }
# waitfor <secs> <fixed-string> <from-line> : 0 when the string is in the unit capture at or after <from-line>
waitfor() { local end=$((SECONDS+$1)); while [ $SECONDS -lt $end ]; do
  tail -n +"$3" "$CAP" | grep -aqF -- "$2" && return 0; sleep 0.5; done; return 1; }

# Host thermal, from ptr_lib.sh (rig-quirks/topics/tooling.md)
th_pkg() { if [ -x ../rig_thermal.sh ]; then ../rig_thermal.sh status | grep -o ' pkg=[0-9]*' | head -1 | cut -d= -f2; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
th_gate() { th_wait 75 || { echo "$(date +%T) HOST_TOO_HOT ${RUN:-run}" | tee -a "$OUT/thermal.log"; return 3; }
  [ -n "$THPID" ] && kill $THPID 2>/dev/null; th_watch "$OUT/${RUN:-run}.thermal" & THPID=$!
  echo "$(date +%T) ${RUN:-run} start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"; }
th_report() { awk -F'[ =C]+' '{p=$3; t=$5; if(p>m)m=p; if(NR==1)t0=t} END{print "thermal_max=" m "C throttle_delta=" t-t0}' "$OUT/$1.thermal"; }

# apk_check : pull the installed APK from $HU and compare its md5 with $WANT_MD5
apk_check() { local m; adb -s "$HU" pull $(adb -s "$HU" shell pm path $PKG | cut -d: -f2 | tr -d '\r') "$OUT/live.apk" >/dev/null 2>&1
  m=$(md5sum "$OUT/live.apk" | cut -d' ' -f1); echo "$(date +%T) $HU live=$m want=$WANT_MD5" | tee -a "$OUT/apk-check.log"
  [ "$m" = "$WANT_MD5" ]; }

# Captures. Each one restarts itself and reconnects if adb drops (wireless adb in Stage U).
loopcap() { ( while :; do stdbuf -oL adb -s "$1" logcat -v time >> "$2"; echo "$(date +%T) restart" >> "$2.restarts"
  adb connect "$1" >/dev/null 2>&1; sleep 1; done ) >/dev/null 2>&1 & echo $!; }
cap_start() { CAP=$OUT/$1.hu.logcat; PCAP=$OUT/$1.phone.logcat; : > "$CAP"; : > "$PCAP"
  adb -s "$HU" logcat -G 16M; adb -s "$HU" logcat -c; CAPLOOP=$(loopcap "$HU" "$CAP")
  adb -s "$PH" logcat -G 16M; adb -s "$PH" logcat -c; PCAPLOOP=$(loopcap "$PH" "$PCAP"); sleep 1; }
cap_stop() { local p; for p in $CAPLOOP $PCAPLOOP; do pkill -P $p 2>/dev/null; kill $p 2>/dev/null; done
  adb -s "$PH" logcat -d -v time > "$PCAP.dump"
  for f in "$CAP" "$PCAP"; do awk '!seen[$0]++' "$f" | tr -d '\r' > "$f.tmp" && mv "$f.tmp" "$f"; done
  ps aux | grep -c "[l]ogcat"; }          # must print 0; else kill the leftover by its pid

# win <file> <run> : the lines from the <run>-start marker to the <run>-end marker, in either capture
win() { awk -v s="$2-start" -v e="$2-end" '
  function hit(t) { return (index($0, "AutomationMarker: " t) > 0) || (index($0, "RIGMARK") > 0 && index($0, ": " t) > 0) }
  hit(s) { f = 1 } f { print } f && hit(e) { exit }' "$1"; }
wc_() { win "$1" "$2" | grep -acF -- "$3"; }

# pol <label> : D-MOTO's stored A2DP policy for the announced address (suffix $SUF). One row to policy.tsv.
pol() { local f="$OUT/$RUN.$1.btdump" line v; adb -s "$PH" shell dumpsys bluetooth_manager > "$f"
  line=$(awk '/BluetoothDatabase/{d=1} d' "$f" | grep -a -i -F -- "$SUF" | head -3 | tr '\n' ' ')
  v=$(echo "$line" | grep -oE '(^|[^_A-Z])A2DP=-?[0-9]+' | head -1 | grep -oE -- '-?[0-9]+$')
  echo -e "$RUN\t$1\t$(date +%T.%3N)\tA2DP=${v:-NA}\t$line" | tee -a "$OUT/policy.tsv"; }

# setj <json body> / getk : the per-run keys through the verbs (section 5)
setj() { adb -s "$HU" shell "am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_SET_SETTINGS --es json '{\"format\":\"open-headunit-settings\",\"version\":1,\"settings\":{$1}}'"; }
getk() { send ACTION_GET_SETTINGS | grep -aoE '"(bt-announce|bt-address|head-unit-make|head-unit-model|allow-external-configuration)":("[^"]*"|true|false)'; }

# pocoput <base.xml> <spec...> : D-POCO (not rooted). App stopped. Host-side edit, written through run-as on stdin, read back.
pocoput() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  adb -s "$HU" exec-in "run-as $PKG sh -c 'cat > shared_prefs/settings.xml'" < new.xml
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE '(wifi-connection-mode|log-level|allow-external-configuration|enable-audio-sink|auto-connect-last-session|auto-connect-single-usb|auto-start-on-usb|reopen-on-reconnection|use-libusb|bt-address|bt-announce|head-unit-make|head-unit-model)[^/]*'; }
# hu_put <base.xml> <spec...> : D-HU (rooted). App stopped. Host-side edit, root copy, owner restored, read back.
hu_put() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  OWN=$(adb -s "$HU" shell "su -c 'stat -c %u:%g /data/data/$PKG'" | tr -d '\r')
  printf 'cp /data/local/tmp/new.xml /data/data/%s/shared_prefs/settings.xml\nchown %s /data/data/%s/shared_prefs/settings.xml\nchmod 660 /data/data/%s/shared_prefs/settings.xml\n' "$PKG" "$OWN" "$PKG" "$PKG" > hu_put.sh
  adb -s "$HU" push new.xml /data/local/tmp/new.xml >/dev/null; adb -s "$HU" push hu_put.sh /data/local/tmp/hu_put.sh >/dev/null
  adb -s "$HU" shell "su -c 'sh /data/local/tmp/hu_put.sh'"
  adb -s "$HU" shell "su -c 'cat /data/data/$PKG/shared_prefs/settings.xml'" | grep -aoE '(wifi-connection-mode|log-level|allow-external-configuration|enable-audio-sink|native-poke-all-paired|native-poke-bt-macs|last-connected-native-mac|native-driver-selection-mode|bt-address|bt-announce|head-unit-make|head-unit-model|native-aa-wake-damage-verdict)[^/]*'; }

# media_go <from-line> : start music on D-MOTO; 0 when our media channel starts
media_go() { adb -s "$PH" shell input keyevent KEYCODE_MEDIA_PLAY
  waitfor 20 'Media Start Request AUDIO:' "$1" && return 0
  adb -s "$PH" shell monkey -p "$PLAYER" -c android.intent.category.LAUNCHER 1 >/dev/null; sleep 5
  adb -s "$PH" shell input keyevent KEYCODE_MEDIA_PLAY; waitfor 20 'Media Start Request AUDIO:' "$1"; }

# ring <run> : one incoming call, ungraded. Answer and hang-up are key events on D-MOTO.
ring() { mark "$1-ring-request"; cue "H4: dial D-MOTO from a handset outside the rig"
  for i in $(seq 1 60); do adb -s "$PH" shell dumpsys telecom | grep -qai "RINGING" && break; sleep 1; done; mark "$1-ringing"; sleep 5
  adb -s "$PH" shell input keyevent KEYCODE_CALL; mark "$1-answered"; sleep 8
  adb -s "$PH" shell dumpsys telecom | grep -aiE "CallAudioState|audioRoute|route" | head -10 > "$OUT/$1.callroute"
  sleep 7; adb -s "$PH" shell input keyevent KEYCODE_ENDCALL; mark "$1-hangup"; sleep 5; }

# usb_up : ask for USB as the user and wait up to 120 s for SSL, cueing H2 and H3 when they are needed
dlg() { adb -s "$HU" shell dumpsys activity activities | grep -a -m1 -E 'topResumedActivity|mResumedActivity' | grep -aoE 'systemui[./][A-Za-z.]*Usb[A-Za-z]*Activity'; }
usb_up() { local L end d foc=; L=$(nl); end=$((SECONDS+120)); send ACTION_CHECK_USB >/dev/null
  while [ $SECONDS -lt $end ]; do
    tail -n +"$L" "$CAP" | grep -aqF 'SSL handshake complete' && return 0
    d=$(dlg); [ -n "$d" ] && { echo "$(date +%T) $RUN $d" >> "$OUT/dialogs.log"; cue "H2: allow the USB dialog on D-POCO ($d); tick Always if offered"; sleep 8; }
    if [ -z "$foc" ] && [ $SECONDS -ge $((end-60)) ]; then foc=1
      adb -s "$PH" shell dumpsys window | grep -a mCurrentFocus | tee -a "$OUT/dialogs.log"
      cue "H3: clear any Android Auto or system screen on D-MOTO and say what it said"; fi
    sleep 2; done; return 1; }

# body <run> <clean|pull|ghkill> <call 0|1> : from SSL to the end of a run, shared by both stages
body() { local LS; LS=$(nl); sleep 5
  media_go "$LS" || echo -e "$1\tmedia-not-started" >> "$OUT/summary.tsv"
  sleep 30; pol P1
  [ "$3" = 1 ] && ring "$1"
  [ "$STAGE" = N ] && { cue "H7: read D-MOTO's Media audio toggle for D-HU"; }
  mark "$1-stop"
  case $2 in
    clean)  send ACTION_DISCONNECT >/dev/null ;;
    pull)   cue "H1: pull D-MOTO's cable out of D-POCO"; waitfor 60 'AapService: session state disconnected' "$LS" ;;
    ghkill) adb -s "$PH" shell am force-stop $GH; sleep 2; send ACTION_EXIT >/dev/null ;;
  esac
  sleep 5; pol P2; sleep 55; pol P3
  [ "$STAGE" = N ] && { cue "H7: read D-MOTO's Media audio toggle for D-HU"; }
  [ "$2" = pull ] && cue "H1: plug D-MOTO back into D-POCO's OTG port"
  mark "$1-end"; cap_stop; usum "$1"; th_report "$1"; }

# urun <run> <bt-announce> <make> <model> <clean|pull|ghkill> <call 0|1> : one Stage U run
urun() { RUN=$1; th_gate || return 3; apk_check || { echo "APK_MISMATCH" | tee -a "$OUT/summary.tsv"; return 1; }
  adb -s "$HU" shell am force-stop $PKG; pocoput "$BASE" $KEYS > "$OUT/$RUN.keys" || return 1
  cap_start "$RUN"; mark "$RUN-start"; pol P0
  setj "\"bt-announce\":\"$2\",\"head-unit-make\":\"$3\",\"head-unit-model\":\"$4\"" >> "$OUT/$RUN.keys"; getk >> "$OUT/$RUN.keys"
  if ! usb_up; then cue "H1: unplug D-MOTO from D-POCO, wait 5 s, plug it back"; sleep 10
    usb_up || { mark "$RUN-end"; cap_stop; echo -e "$RUN\tVOID no session" | tee -a "$OUT/summary.tsv"; return 2; }; fi
  body "$RUN" "$5" "$6"; }

# nrun <run> <make|-> <model|-> <clean|ghkill> <call 0|1> : one Stage N run; "-" keeps D-HU's own identity
nrun() { RUN=$1; th_gate || return 3; apk_check || { echo "APK_MISMATCH" | tee -a "$OUT/summary.tsv"; return 1; }
  adb -s "$PH" shell cmd connectivity airplane-mode enable; sleep 8
  adb -s "$HU" shell am force-stop $PKG; hu_put "$BASE" $KEYS > "$OUT/$RUN.keys" || return 1
  cap_start "$RUN"; mark "$RUN-start"; pol P0
  [ "$2" != - ] && setj "\"head-unit-make\":\"$2\",\"head-unit-model\":\"$3\"" >> "$OUT/$RUN.keys"; getk >> "$OUT/$RUN.keys"
  local L; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; sleep 15
  adb -s "$PH" shell cmd connectivity airplane-mode disable; sleep 2
  adb -s "$PH" shell svc wifi enable; adb -s "$PH" shell svc bluetooth enable
  waitfor 120 'SSL handshake complete' "$L" || { mark "$RUN-end"; cap_stop; echo -e "$RUN\tVOID no session" | tee -a "$OUT/summary.tsv"; return 2; }
  body "$RUN" "$4" "$5"; }

# usum <run> : the counts every run reports. One block to summary.tsv.
usum() { local c=$CAP p=$PCAP r=$1
  { echo "== $r"
    echo "hu.verbs=$(wc_ $c $r 'AutomationReceiver: ')"
    echo "hu.announce=$(win $c $r | grep -aoE 'Bluetooth service announced with carAddress=[^ ]+ \(bt-announce=[a-z]+\)' | sort | uniq -c | tr '\n' ' ')"
    echo "hu.announce_blank=$(wc_ $c $r 'No Bluetooth service announced: bt-announce=blank')"
    echo "hu.announce_empty=$(wc_ $c $r 'BT MAC Address is empty, so no Bluetooth service is announced')"
    echo "hu.ssl=$(wc_ $c $r 'SSL handshake complete')"
    echo "hu.media_start=$(wc_ $c $r 'Media Start Request AUDIO:')"
    echo "hu.disconnected=$(wc_ $c $r 'AapService: session state disconnected')"
    echo "hu.fatal=$(wc_ $c $r 'FATAL EXCEPTION')"
    for s in 'CarBluetoothService. car address=' 'Bluetooth address is empty' 'Special car Bluetooth address that should be skipped' \
             'Car does not support Bluetooth service; skipping disableA2dp.' 'disabling A2dp route while in projection' \
             'disabling A2dp via profile disabler' 'Set A2DP policy for car to' 'Failed to set A2DP policy for car to' \
             'Condition not met, so not setting A2DP policy for car to' 'Found A2DP entry for car to be re-enabled' \
             'Did not find A2DP entry for car to be re-enabled' 'a2dp cannot be re-enabled at the end of the drive' \
             'No high priority audio routes available' 'Critical error' 'FATAL EXCEPTION'; do
      echo "ph[$s]=$(wc_ $p $r "$s")"; done
    echo "ph.car_address_lines:"; win $p $r | grep -aF -e 'CarBluetoothService. car address=' -e 'Set A2DP policy for car to' | head -5
    echo "ph.restarts=$(cat "$p.restarts" 2>/dev/null | wc -l)"
    awk -F'\t' -v r="$r" '$1 == r { print $2, $3, $4 }' "$OUT/policy.tsv"
  } | tee -a "$OUT/summary.tsv"; }
```

## 7. The lines that decide the runs

App lines were checked with `grep -rF` against `app/src/main` on `fff96699`; all print at INFO. Phone lines were checked against the string table of Android Auto `17.8.163744`'s dex. Confirm the phone's Android Auto version in Setup notes (`rig-quirks/topics/gearhead.md`: strings drift between builds). Every count is taken inside the run's window: from the `<run>-start` marker to the `<run>-end` marker, `AutomationMarker:` in the unit capture and `RIGMARK` in the phone capture (`win`).

| Line (fixed substring) | File | Meaning |
|---|---|---|
| `Bluetooth service announced with carAddress=` | unit | the address on the wire, with ` (bt-announce=real)` or ` (bt-announce=skip)` after it |
| `No Bluetooth service announced: bt-announce=blank` | unit | the blank arm left the service out |
| `BT MAC Address is empty, so no Bluetooth service is announced` | unit | `real` with an empty `bt-address`: the run is void |
| `SSL handshake complete` | unit | a session formed (never prefix `Handshake:`) |
| `Media Start Request AUDIO:` | unit | the media channel started, which is what runs the disabler (composed from `Media Start Request %s:`) |
| `AapService: session state disconnected` | unit | the session ended |
| `AutomationReceiver: ` | unit | a verb landed; a missing one voids the step |
| `Requesting USB permission for`, `Found device already in accessory mode`, `Fallback: force=true and found single normal-mode Android device`, `Sending acc start` | unit | the USB bring-up |
| `CarBluetoothService. car address=` | phone | Android Auto read our Bluetooth service, and its Bluetooth lines are visible |
| `Bluetooth address is empty` | phone | Android Auto saw a service with an empty address |
| `Special car Bluetooth address that should be skipped` | phone | Android Auto took the skip value |
| `Car does not support Bluetooth service; skipping disableA2dp.` | phone | the disable path ran and found no service |
| `disabling A2dp route while in projection` | phone | the media-capture path ran (route move only, no policy change) |
| `disabling A2dp via profile disabler` | phone | **the list disabler fired** |
| `Set A2DP policy for car to` | phone | **the flag path set a policy** (`0` disables, `100` restores) |
| `Failed to set A2DP policy for car to`, `Condition not met, so not setting A2DP policy for car to` | phone | the flag path ran and did not set it |
| `Found A2DP entry for car to be re-enabled`, `Did not find A2DP entry for car to be re-enabled`, `a2dp cannot be re-enabled at the end of the drive` | phone | the restore path at the session end |
| `No high priority audio routes available` | phone | call audio stayed on the phone |
| `Critical error` | phone | Android Auto ended a session with an error |
| `FATAL EXCEPTION` | both | a crash |

**The policy instrument.** `pol` reads D-MOTO's `dumpsys bluetooth_manager`, takes the `BluetoothDatabase` lines that carry the announced address's last two octets (`$SUF`; Android prints the rest masked), and pulls the `A2DP=` value: `100` ALLOWED, `0` FORBIDDEN, `-1` UNKNOWN. **If `pol` prints `A2DP=NA` in Prepare**, the dump format differs from the expected one. Then run `grep -a -n -i -F "$SUF" <the .btdump file> | head -20`, put the output in Setup notes, and the host names the A2DP field from it once for the whole round. That is the one condition here that needs a read rather than an extract.

**Reading a run.**

- **DISABLED**: one or more of `disabling A2dp via profile disabler` or `Set A2DP policy for car to 0` in the phone window, or P1 = `0` with P0 not `0`.
- **NOT DISABLED**: none of those two lines, and P1 = P0.
- **H4 signal**: P1 differs from P0 with none of those lines, while `CarBluetoothService. car address=` is present. Report it whenever it happens.

## 8. Runs

### Prepare (P), on cables, before Stage U

1. **Build and install** (section 1). Install `apks/candidate-fff96699.apk` with `adb -s 4f4027e9 install -r` and `adb -s 27870808938846 install -r`. On each, `send ACTION_QUERY_STATE` and record `commit`. Check the DEX symbol.
2. **Back up both settings files** with each app stopped: D-POCO with `run-as $PKG cat shared_prefs/settings.xml > $OUT/settings_backup_poco.xml`; D-HU with `su -c 'cat /data/data/$PKG/shared_prefs/settings.xml' > $OUT/settings_backup_dhu.xml`. Never name a backup `*.bak` on a device. Diff each against the file after the install and state the delta. Record the rig audio keys from D-HU's backup.
3. **`stat` both `shared_prefs/` directories** and record owner, group and mode (D-POCO with `run-as $PKG stat -c '%U:%G %a' shared_prefs`, D-HU with `su -c`).
4. **Addresses.** Read each with `adb -s <serial> shell settings get secure bluetooth_address`: `POCO_BT` on `4f4027e9`, `DHU_BT` on `27870808938846`, `MOTO_BT` on `ZY22GC3BM4`. If `POCO_BT` is empty, `null` or `02:00:00:00:00:00`, take it from D-POCO's `dumpsys bluetooth_manager` (`address:` line) instead. If that also fails, Stage U is UNTESTABLE. Also read D-HU's stored `bt-address` from its backup as `DHU_ANN`, the value D-HU announces. Record all four.
5. **Bonds.** On D-MOTO, `adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -i -A30 "Bonded devices"` must list D-POCO and D-HU. On D-POCO, the same must list D-MOTO. If D-MOTO and D-POCO are not bonded, use H5, then check again. Still not bonded: Stage U is UNTESTABLE.
6. **Android Auto on D-MOTO.**
   ```bash
   adb -s ZY22GC3BM4 shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
   for t in CAR.BT.SVC CAR.BT.A2DP CAR.BT.A2dpDisabler CAR.BT.AdapterWrapper CAR.BT.DeviceWrapper CAR.SERVICE CAR.AUDIO GH.CallManager; do
     adb -s ZY22GC3BM4 shell setprop log.tag.$t VERBOSE; done
   adb -s ZY22GC3BM4 shell am force-stop com.google.android.projection.gearhead
   adb -s ZY22GC3BM4 shell dumpsys activity service com.google.android.projection.gearhead > $OUT/gh-flags-prepare.txt
   grep -a -E "BluetoothPairing__disable_a2dp|UsbBabysitter__a2dp_fix_car_list|UsbBabysitter__enable_a2dp_at_projection_end" $OUT/gh-flags-prepare.txt
   ```
   Record the three flag lines verbatim. If the grep prints nothing, say so; U1a takes the dump again during a live session.
7. **D-MOTO state.** `settings get global zen_mode` must read `0` (`cmd notification set_dnd off` if not). `svc power stayon true`. Check the lock state with `dumpsys window | grep -a -E "mShowingLockscreen|mDreamingLockscreen"`; if it is locked behind a PIN, the operator unlocks it once and Setup notes say so. Pick `PLAYER` as the first installed of `com.spotify.music`, `com.google.android.apps.youtube.music`, `org.videolan.vlc` (`pm list packages`). None installed: escalate. `input keyevent KEYCODE_HOME`.
8. **Policy calibration.** With `PH=ZY22GC3BM4`, `RUN=P`, `SUF` = the last five characters of `POCO_BT` (for example `AB:CD`): `pol P0-poco`. Then `SUF` = the last five of `DHU_ANN`: `pol P0-dhu`. Record both. If either prints `A2DP=NA`, follow the fallback in section 7. **Record whether D-POCO's value is `100`**: it decides U5.
9. **D-HU out of the way for Stage U:** `adb -s 27870808938846 shell am force-stop com.andrerinas.headunitrevived`. Its Bluetooth self-reverts on, and that is fine with the app stopped.

### Stage U: D-POCO as head unit over USB, D-MOTO as the phone

**Switch to wireless adb.**

```bash
adb -s 4f4027e9 tcpip 5555; adb -s ZY22GC3BM4 tcpip 5555; sleep 3
POCO_IP=$(adb -s 4f4027e9 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
MOTO_IP=$(adb -s ZY22GC3BM4 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
adb connect $POCO_IP:5555; adb connect $MOTO_IP:5555
adb -s $POCO_IP:5555 shell dumpsys usb > $OUT/dumpsys-usb-before.txt
```

Then H1: unplug D-POCO from the PC; unplug D-MOTO from the PC and plug it into D-POCO's OTG port. Check that `adb -s $POCO_IP:5555 shell getprop ro.product.model` and `adb -s $MOTO_IP:5555 shell getprop ro.product.model` both answer. If the attach starts a session on its own, that is fine: each run starts with a force-stop.

```bash
STAGE=U; HU=$POCO_IP:5555; PH=$MOTO_IP:5555; BASE=$OUT/settings_backup_poco.xml
SUF=<last five characters of POCO_BT>
KEYS="int:wifi-connection-mode=0 int:log-level=2 int:onboarding-version=2 bool:allow-external-configuration=true bool:enable-audio-sink=true bool:kill-on-disconnect=false bool:auto-connect-last-session=false bool:auto-connect-single-usb=false bool:auto-start-on-usb=false bool:reopen-on-reconnection=false bool:use-libusb=false set:connection-modes=usb,wifi str:bt-address=$POCO_BT del:bt-announce del:head-unit-make del:head-unit-model del:video-profile-starvation-cap del:native-aa-wake-damage-verdict del:native-aa-wireless del:wifi-launcher-mode"
source ./lib1008.sh
```

**Valid run (Stage U).** A run counts only when all of these hold in its window. Otherwise re-run it once, then grade it INCONCLUSIVE with the failed item named:

- an `AutomationReceiver: ` line for every verb sent;
- `hu.ssl` = 1;
- exactly one announce line, and it is the arm's own: `carAddress=$POCO_BT (bt-announce=real)`, `carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)`, or `No Bluetooth service announced: bt-announce=blank` with `hu.announce` empty;
- `getk` shows the arm's make and model;
- `hu.media_start` of 1 or more before the `<run>-stop` marker;
- P0, P1, P2 and P3 present in `policy.tsv`;
- the phone window is not empty (it carries both `RIGMARK` markers);
- `hu.fatal` = 0 and `ph[FATAL EXCEPTION]` = 0.

A `real` arm whose announced address is `02:00:00:00:00:00` or empty is void.

#### U0. Gate

PASS needs all of:

- P1: md5 recorded, 2747/0, the DEX symbol, `commit` beginning `fff96699` on both units;
- P4: `POCO_BT` usable;
- P5: D-MOTO and D-POCO bonded;
- P8: an A2DP value read for both addresses (directly or by the host's reading);
- both wireless adb links answering;
- `allow-external-configuration` reads `true` after the first `pocoput`.

A FAIL on any of these stops Stage U; go to Stage N.

#### U1. Real address, default identity, three times (the point of the round)

```bash
urun U1a real Google "Desktop Head Unit" clean 1
adb -s $PH shell dumpsys activity service com.google.android.projection.gearhead > $OUT/gh-flags-live.txt   # right after U1a's SSL is fine too; take it once
urun U1b real Google "Desktop Head Unit" clean 0
urun U1c real Google "Desktop Head Unit" clean 0
```

Take the live flags dump while U1a's session is up if you can: run it from a second shell after `SSL handshake complete`, sequential with the other adb calls. Grep it as in P6.

- **PASS**: three valid repetitions with the same reading (DISABLED or NOT DISABLED, section 7).
- **FAIL**: three valid repetitions whose readings differ. Report each repetition.
- **Report**: the reading; P0 to P3 for each; every `ph[...]` count; the `CarBluetoothService. car address=` line verbatim; the live flag lines.
- **If the change did nothing**, this run would read the same. U1 measures Android Auto on this phone, not the probe; U4 is what proves the instrument can see a disable.

#### U2. Blank, list identity

```bash
urun U2 blank Aptiv "Volvo [AAR]" clean 1
```

- **PASS**: valid, 0 `disabling A2dp via profile disabler`, 0 `Set A2DP policy for car to 0`, and P1 = P0.
- **FAIL**: valid and either line present, or P1 differs from P0.
- **Report** `ph[Car does not support Bluetooth service; skipping disableA2dp.]` and `ph[disabling A2dp route while in projection]`. One of them at 1 or more shows the media path ran. If both are 0, say so: the PASS then rests on U4 showing a disable with the same identity.
- If P0 already reads `0`, the policy half cannot see a disable; grade on the lines, and INCONCLUSIVE if `ph[CarBluetoothService. car address=]` and both reachability lines are all 0.

#### U3. Skip value, list identity

```bash
urun U3 skip Aptiv "Volvo [AAR]" clean 1
```

- **PASS and FAIL**: as U2.
- **Report** `ph[Special car Bluetooth address that should be skipped]` (the code read expects 1 or more) and the call route file.

#### U4. Real address, list identity, clean end (positive control)

```bash
urun U4 real Aptiv "Volvo [AAR]" clean 0
```

- **PASS**: valid, and either `ph[disabling A2dp via profile disabler]` of 1 or more, or P1 = `0` with P0 not `0`.
- **FAIL**: valid, with neither. Then the list check did not fire on this phone for this identity, so U1, U2 and U3's "no disable" proves nothing. Report the flag lines beside it.
- **Report** P2 and P3. Expected from the code read: P2 = P0 when P0 was `100`; otherwise P2 stays `0`, unless `UsbBabysitter__enable_a2dp_at_projection_end` reads true. Ungraded on D-POCO unless P0 was `100`.
- **After U4, D-MOTO's policy for D-POCO may stay at `0`.** That does not touch any other unit. Record it in Setup notes.

#### U5. Unclean ends on USB (only if P8 read D-POCO's policy as `100`)

If P8 read anything else, write "not run: D-POCO's starting policy is not ALLOWED, so a restore cannot be seen; Stage N covers it" and go on. Otherwise use the identity that disabled: `Google` / `Desktop Head Unit` if U1 read DISABLED, else `Aptiv` / `Volvo [AAR]`. Before each repetition, P0 must read `100`; if it does not, use H6 for D-POCO's entry on D-MOTO and read it again.

```bash
urun U5a1 real <make> "<model>" pull 0;   urun U5a2 real <make> "<model>" pull 0;   urun U5a3 real <make> "<model>" pull 0
urun U5b1 real <make> "<model>" ghkill 0; urun U5b2 real <make> "<model>" ghkill 0; urun U5b3 real <make> "<model>" ghkill 0
```

- A repetition counts when it is valid and P1 = `0`.
- **Reading per repetition**: NO RESTORE when P2 and P3 both read `0`; RESTORED when P3 = P0.
- **PASS**: per arm (U5a cable pull, U5b Android Auto force-stop), three counted repetitions with the same reading. **FAIL**: counted repetitions disagree. Report the reading for each arm.

**Closing Stage U.**

1. `send ACTION_EXIT`.
2. `adb -s $HU shell am start -a android.intent.action.VIEW -d headunit://exit`, then `sleep 3`, then `adb -s $HU shell am force-stop $PKG`.
3. Restore D-POCO's backup with `pocoput $BASE` (no spec) and read it back.
4. `adb -s $HU shell dumpsys usb > $OUT/dumpsys-usb-after.txt`.
5. H1: unplug D-MOTO from D-POCO and put D-MOTO and D-POCO back on PC cables.
6. `adb -s $POCO_IP:5555 usb` and `adb -s $MOTO_IP:5555 usb`.
7. D-POCO stays out of Stage N: `adb -s 4f4027e9 shell svc bluetooth disable` and `adb -s 4f4027e9 shell cmd connectivity airplane-mode enable` (`rig-quirks/topics/bt.md`: it is bonded to D-HU and takes the poke from D-MOTO). Confirm both with `dumpsys`.

### Stage N: D-HU as head unit over Native AA, D-MOTO as the phone

```bash
STAGE=N; HU=27870808938846; PH=ZY22GC3BM4; BASE=$OUT/settings_backup_dhu.xml
SUF=<last five characters of DHU_ANN>
KEYS="int:wifi-connection-mode=3 int:log-level=2 int:onboarding-version=2 int:native-driver-selection-mode=0 bool:allow-external-configuration=true bool:enable-audio-sink=true bool:native-poke-all-paired=false set:native-poke-bt-macs=$MOTO_BT str:last-connected-native-mac=$MOTO_BT str:native-preferred-device-mac=$MOTO_BT del:bt-announce del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"
source ./lib1008.sh
```

**Valid run (Stage N)**: as Stage U, with these changes:

- the announce line must read `carAddress=$DHU_ANN (bt-announce=real)`;
- discard and re-run on any of `MATCH! Starting AapService` or a second `createGroup SUCCESS` before SSL (template §4);
- `hu.ssl` = 1.

#### N0. Gate

- `pol N0` with `RUN=N0` must read `100` for D-HU.
- If it reads `0`, the toggle is already off: use H6, read again, and say so.
- If it reads anything other than `100` after that, Stage N is UNTESTABLE.

#### N1. Real address, D-HU's own identity, clean end

```bash
nrun N1 - - clean 1
```

- **PASS**: valid. **Report** the reading (section 7), P0 to P3, the H7 answers, the call route, and `ph[No high priority audio routes available]`.
- This is the toggle the user sees, on a unit that is an A2DP sink.

#### N2. Real address, list identity, clean end, twice

```bash
nrun N2a Aptiv "Volvo [AAR]" clean 0
nrun N2b Aptiv "Volvo [AAR]" clean 0
```

Per repetition:

- **PASS**: valid, P0 = `100`, DISABLED (section 7), and P2 = `100` or P3 = `100`.
- **FAIL**: valid, DISABLED, and P3 still reads `0`. A clean end did not restore it. Use H6 before the next run.
- **INCONCLUSIVE**: valid and NOT DISABLED. Then the list check did not fire over Native AA, so the restore was not reached.

**If both repetitions read NOT DISABLED, do not run N3.** Write "not run: the list check did not fire over Native AA".

#### N3. Real address, list identity, Android Auto force-stop, twice

```bash
nrun N3a Aptiv "Volvo [AAR]" ghkill 0
nrun N3b Aptiv "Volvo [AAR]" ghkill 0
```

- A repetition counts when it is valid and DISABLED.
- **Reading**: NO RESTORE when P2 and P3 both read `0`; RESTORED when P3 = `100`.
- After each repetition that reads `0` at P3: use H6, then `RUN=<run>; pol P4-after-H6`, which must read `100` before the next run.
- **PASS**: two counted repetitions with the same reading. **FAIL**: they disagree. Report the reading.

**Closing Stage N, and the round.**

1. `send ACTION_EXIT`.
2. `adb -s $HU shell am force-stop $PKG`.
3. Restore D-HU's backup with `hu_put $BASE` (no spec) and read back `head-unit-make` and `head-unit-model`: they must match the backup.
4. **D-MOTO's policy for D-HU must read `100`** (`RUN=END; pol final-dhu`). If it does not, use H6 and read again. Never leave the rig with that toggle off.
5. On D-POCO, `svc bluetooth enable` and `cmd connectivity airplane-mode disable`, and confirm with `dumpsys`.
6. On D-MOTO, `svc power stayon false`.
7. Put the final `policy.tsv` in the results.

**Stop rule.** Stage U has at most 12 runs (U1 three, U2, U3, U4, U5 six). Stage N has at most 5 (N1, N2 two, N3 two). Each run is re-run at most once. A stage whose first two bring-ups both end VOID is UNTESTABLE from that point.

## 9. Do not re-run

Nothing is settled. This is the thread's first round. The mechanism comes from a code read of Android Auto 17.8, not from a measurement.

## 10. Report back

1. **D-MOTO's Android Auto version and the three flag lines**, verbatim, from the prepare dump and the live dump.
2. **U1**: the reading on each of the three repetitions, with P0 and P1 for each. This is the reported user's case on this phone's flags.
3. **U4 and N2**: does the list identity disable, over USB and over Native AA? Does a clean end restore it (P2 against P0)?
4. **U2 and U3**: with the list identity, do `blank` and `skip` stop the disable (P1 against P0, and the two disable-line counts)?
5. **N3, and U5 if it ran**: the policy at +5 s and +60 s after an unclean end.
