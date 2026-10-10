# bluetooth-audio-disabled-usb-connect, round 2 brief

Published name on the transfer branch: `bluetooth-audio-disabled-usb-connect-round2-brief.md`.

This is a **measurement round** on the same probe build as round 1. No fix is on trial. Round 1 showed that Android Auto on D-MOTO switches off the phone's A2DP setting for the head unit at every session start, and puts it back at a clean end. Round 2 asks what happens after the ends that a driver really makes: a cable pulled out, and an Android Auto process that dies. It also asks if the next ordinary session repairs a setting that an unclean end left off.

## 1. Build and baseline

- Candidate: branch `fix/bluetooth-audio-disabled-usb-connect` on the fork, SHA **`fff96699`** (`fff96699480d878bb36bd04078d77260231b19dc`). This is the round 1 SHA. History was not rewritten, and no commit was added.
  ```bash
  git fetch fork fix/bluetooth-audio-disabled-usb-connect
  git -C ../ohu-wt-bad-fff96699 rev-parse HEAD     # must print fff96699480d878bb36bd04078d77260231b19dc
  ```
- **Reuse the round 1 APK.** If `apks/candidate-fff96699.apk` exists and its md5 is `234caaadff2d4731e006591a0c69bb2a`, set `WANT_MD5=234caaadff2d4731e006591a0c69bb2a` and do not build. If it is gone, build it from the round 1 worktree with `build_hur.sh` (`HUR_DIR=../ohu-wt-bad-fff96699`; that worktree already has `local.properties`), run `run_unit_tests.sh` (gate: **2747 tests, 0 failures**; a failure stops the round), copy the APK to `apks/candidate-fff96699.apk` at once, and record its md5 as `WANT_MD5`.
- On D-POCO (`4f4027e9`) and D-HU (`27870808938846`): if `apk_check` (section 6) already matches, do not install. If not, `adb -s <serial> install -r apks/candidate-fff96699.apk`.
- `send ACTION_QUERY_STATE` on each unit must reply with a `commit` that begins `fff96699`.
- DEX symbol for identity (template §5): `BluetoothAnnouncePolicy`. Check it only if you built a new APK.
- **No baseline APK.** Every comparison is between runs on the candidate.

## 2. What this is and why it exists

**The report.** A user's phone is paired with the head unit over Bluetooth. When Android Auto connects by cable, the phone switches off the head unit's "Media audio" toggle by itself. The report does not say if the toggle comes back.

**What round 1 measured** (`bluetooth-audio-disabled-usb-connect-round1-results.md`, D-MOTO on Android Auto 17.9.664004):

- Android Auto's `CAR.BT.SVC.LITE` prints `disabling A2dp via profile disabler` 0.7 to 1.0 s after our `SSL handshake complete`, for our default identity (`Google` / `Desktop Head Unit`) and for the list identity. The probe's `blank` and `skip` arms stop it.
- The round graded the toggle by eye, because its `dumpsys` instrument read nothing (`A2DP=NA`).

**What the host found in round 1's captures after the grade.** D-MOTO's own Bluetooth stack logs every change to the A2DP connection policy, at its default log settings. These three lines are in every DISABLED run of the round 1 asset (`bluetooth-audio-disabled-usb-connect-round1-captures.zip`, sha256 `d0267cb69eaedf9bfb563cfa867701ae59bf9854397d9f9dd67b03c68e291009`), for example U1a's phone capture:

```
10-07 10:55:27.017 V/BluetoothDatabase(25042): getProfileConnectionPolicy: XX:XX:XX:XX:4E:59, profile=2, connectionPolicy = -1
10-07 10:55:27.019 D/BluetoothA2dp(27662): setConnectionPolicy(DC:B7:2E:5E:4E:59, 0)
10-07 10:55:27.021 D/A2dpService(25042): Saved connectionPolicy DC:B7:2E:5E:4E:59 = 0
10-07 10:56:41.804 D/BluetoothA2dp(27662): setConnectionPolicy(DC:B7:2E:5E:4E:59, 100)
10-07 10:56:41.808 D/A2dpService(25042): Saved connectionPolicy DC:B7:2E:5E:4E:59 = 100
```

- `setConnectionPolicy(<address>, <value>)` is printed by the process that asks for the change. Pid 27662 is `com.google.android.projection.gearhead:car` (`Start proc 27662:com.google.android.projection.gearhead:car` in the same capture). Pid 21611, which set `100` after each N3 run, is `com.android.settings`, the operator's hand.
- `Saved connectionPolicy <address> = <value>` is the stored value: `100` is on (ALLOWED), `0` is off (FORBIDDEN), `-1` is not set.
- `getProfileConnectionPolicy: XX:XX:XX:XX:<last two octets>, profile=2, connectionPolicy = <value>` is a read. Android Auto reads the value a few milliseconds before it disables, so the first read in a run is the value before the session.

So this round grades every reading on these lines. No reading depends on a person's eye.

**The open questions, and why each one matters to the reply:**

1. **A cable pulled out mid-session** (the reporter's normal end). Does Android Auto put the policy back? Round 1 saw two link losses that it did put back (section 9), but neither was designed and neither was a USB cable pull.
2. **An Android Auto process death mid-session over USB.** Round 1 measured this only over Native AA (N3: no restore).
3. **Repair.** After an end that leaves the policy at `0`, does the next ordinary session put it back at its clean end? Round 1's U1a started at `-1` and still ended at `100`, so Android Auto 17.9 does not need ALLOWED before. No run has started at `0`.

## 3. What is different about this round

**Layout.** Same as round 1.

| Stage | Head unit | Phone | adb | Runs |
|---|---|---|---|---|
| **U** | D-POCO (`4f4027e9`), Android 15 | D-MOTO (`ZY22GC3BM4`), on D-POCO's OTG port | both over wireless adb | U0p, U5a1 to U5a3, U5b1, U6a, U5b2, U6b |
| **N** | D-HU (`27870808938846`), Native AA | D-MOTO, on its PC cable | cables | N0p, N4a1, N4b1, N4a2, N4b2 |

**Facts that change the runs.**

- **The instrument is the phone log, not `dumpsys`.** The `pol` function and P8 are gone. Do not read `dumpsys bluetooth_manager` for a policy.
- **No call, no music.** Android Auto disables at SSL, not at media start (round 1). The calls were ungraded in round 1, so the runs leave them out.
- **D-MOTO's Settings app stays closed during Stage U and Stage N**, except inside H6. With Settings open, round 1 saw two `com.android.settings` crashes and extra policy writes for another profile (`BluetoothMapService`).
- **D-POCO has no hand lever.** It is a phone, so D-MOTO probably shows no "Media audio" toggle for it, and nothing on the rig can set D-MOTO's policy for D-POCO back to `100`. Only Android Auto can. That is why Stage U puts a clean recovery run after every run that ends at `0` (rule R, section 8), and stops when a recovery fails.
- **A USB re-attach to a phone still in accessory mode fails** with `Version request send failed` until D-MOTO re-enumerates (round 1). So at the end of every Stage U run, the operator unplugs and plugs D-MOTO back (H1). After a pull, the operator only plugs it back.
- **Read windows are on the phone's clock only.** Every delay in this round is between two phone-side lines (a `RIGMARK` marker and a policy line). Do not subtract a head unit time from a phone time.
- **Round 1's harness fixes are in this lib** (section 6): no `su` on D-HU, `MainActivity` started before `ACTION_CHECK_USB`, and the thermal watcher killed at the end of each run.

**Expected INCONCLUSIVE, and that is not a failure:**

- U6a, U6b, N4b1 or N4b2 when its P0 is not `0`. Then Android Auto put the policy back before the next session started, which is itself an answer. Record P0.
- Any run whose phone window has no `setConnectionPolicy(<address>, 0)` from a Gearhead process: the disable did not happen in that session.

## 4. Hand steps, and why no verb exists

The executor prints a line that starts `OPERATOR:`, rings the terminal bell, and appends a line to `$OUT/hand-steps.log` (`cue`). It waits on a log line where one exists, never on a clock alone.

| Id | Step | Why no verb |
|---|---|---|
| H0 | Unlock D-MOTO once in Prepare if it is behind a PIN | adb has no way past a PIN (`rig-quirks/units/D-MOTO.md`) |
| H1 | Cables: move D-POCO and D-MOTO between the PC and D-POCO's OTG port; **pull D-MOTO's cable out of D-POCO by hand** in U5a; re-plug D-MOTO at the end of each Stage U run | A cable is hardware. In U5a it must be a physical pull, not a change of USB mode in D-MOTO's settings |
| H2 | Allow a system USB dialog on D-POCO (`UsbPermissionActivity` or `UsbConfirmActivity`). Do not tick "Always" | A system dialog, not our app |
| H3 | Clear any Android Auto or system screen on D-MOTO that blocks the session, and say what it said | Android Auto's screens have no adb lever |
| H5 | Pair D-MOTO and D-POCO, only if Prepare finds them not bonded | Pairing needs a confirmation on both screens |
| H6 | Stage N only, inside a run window: open D-MOTO's Settings, Connected devices, the D-HU entry (`Navegadortz2`), say if "Media audio" is off, turn it on, press HOME | No public API sets another app's A2DP policy. The script waits for `Saved connectionPolicy <D-HU address> = 100` |

## 5. Settings keys

Write the base keys with the app stopped, back up first (template §1). D-POCO is not rooted: use `pocoput`. D-HU: use `hu_put` (`adb shell` is already root there, round 1). Read every key back before each launch. Record the rig audio keys (`use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`) from D-HU's backup as found. Do not write them.

| Key | Type | D-POCO (Stage U) | D-HU (Stage N) |
|---|---|---|---|
| `wifi-connection-mode` | int | `0` | `3` |
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
| `bt-address` | string | `$POCO_BT` | as found, do not write |
| `native-driver-selection-mode` | int | | `0` |
| `native-poke-all-paired` | boolean | | `false` |
| `native-poke-bt-macs` | string set | | `$MOTO_BT` |
| `last-connected-native-mac`, `native-preferred-device-mac` | string | | `$MOTO_BT` |
| `bt-announce` | | delete | delete |
| `head-unit-make`, `head-unit-model` | | delete | as found, do not write |
| `video-profile-starvation-cap`, `native-aa-wake-damage-verdict` | | delete | delete |
| `native-aa-wireless`, `wifi-launcher-mode` | | delete | |

On D-POCO, `bt-announce`, `head-unit-make` and `head-unit-model` are set per run through `ACTION_SET_SETTINGS` (in `urun`), to `real`, `Google` and `Desktop Head Unit`, the arm that disabled in round 1's U1. Read them back with `ACTION_GET_SETTINGS`. Our announce line on the wire is the proof of the value in force. Stage N uses D-HU's own identity with `bt-announce` absent, which announces the real address.

## 6. Shell setup

Make `hur-wifi-test-scripts/bluetooth-audio-disabled-usb-connect-round2/`. Copy `ohu_setkeys.py` into it from `../bluetooth-audio-disabled-usb-connect-round1/`. Save `lib1008r2.sh` below beside it. List both in Setup notes. If a function does not match the real line format, fix it, say so in Setup notes, and keep going. Run each stage under `flock /tmp/ohu-rig.lock`. One adb call at a time per unit; the two log captures are the only streams that run beside them (house rule 8).

Before the round, check for a leftover thermal watcher from round 1 and kill it by pid: `ps aux | grep -E "[r]ig_thermal.sh watch|[s]leep 20"`.

**`lib1008r2.sh`**:

```bash
# lib1008r2.sh : source after setting STAGE, HU, PH, OUT, BASE, KEYS, ADDR, SUF, WANT_MD5.
# ADDR = the announced address in full (DC:B7:2E:5E:4E:59 form); SUF = its last five characters.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
GH=com.google.android.projection.gearhead
send() { a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; adb -s "$PH" shell log -t RIGMARK "$1"; echo "$(date +%T.%3N) $1" >> "$OUT/marks.log"; }
cue() { printf '\a'; echo "OPERATOR: $1 NOW"; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
nl() { echo $(( $(wc -l < "$CAP") + 1 )); }
pnl() { echo $(( $(wc -l < "$PCAP") + 1 )); }
# waitfor <secs> <fixed-string> <from-line> : 0 when the string is in the unit capture at or after <from-line>
waitfor() { local end=$((SECONDS+$1)); while [ $SECONDS -lt $end ]; do
  tail -n +"$3" "$CAP" | grep -aqF -- "$2" && return 0; sleep 0.5; done; return 1; }
# pwaitfor <secs> <fixed-string> <from-line> : the same on the phone capture, A2dpService lines only
pwaitfor() { local end=$((SECONDS+$1)); while [ $SECONDS -lt $end ]; do
  tail -n +"$3" "$PCAP" | grep -aF '/A2dpService(' | grep -aqF -- "$2" && return 0; sleep 0.5; done; return 1; }

# Host thermal (rig-quirks/topics/tooling.md). th_stop fixes round 1's leaked watcher.
th_pkg() { if [ -x ../rig_thermal.sh ]; then ../rig_thermal.sh status | grep -o ' pkg=[0-9]*' | head -1 | cut -d= -f2; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
th_stop() { [ -n "$THPID" ] && { pkill -P $THPID 2>/dev/null; kill $THPID 2>/dev/null; }; THPID=; }
trap th_stop EXIT
th_gate() { th_wait 75 || { echo "$(date +%T) HOST_TOO_HOT ${RUN:-run}" | tee -a "$OUT/thermal.log"; return 3; }
  th_stop; th_watch "$OUT/${RUN:-run}.thermal" & THPID=$!
  echo "$(date +%T) ${RUN:-run} start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"; }
th_report() { awk -F'[ =C]+' '{p=$3; t=$5; if(p>m)m=p; if(NR==1)t0=t} END{print "thermal_max=" m "C throttle_delta=" t-t0}' "$OUT/$1.thermal"; }

apk_check() { local m; adb -s "$HU" pull $(adb -s "$HU" shell pm path $PKG | cut -d: -f2 | tr -d '\r') "$OUT/live.apk" >/dev/null 2>&1
  m=$(md5sum "$OUT/live.apk" | cut -d' ' -f1); echo "$(date +%T) $HU live=$m want=$WANT_MD5" | tee -a "$OUT/apk-check.log"
  [ "$m" = "$WANT_MD5" ]; }

# Captures. Each restarts itself and reconnects if adb drops (wireless adb in Stage U).
loopcap() { ( while :; do stdbuf -oL adb -s "$1" logcat -v time >> "$2"; echo "$(date +%T) restart" >> "$2.restarts"
  adb connect "$1" >/dev/null 2>&1; sleep 1; done ) >/dev/null 2>&1 & echo $!; }
cap_start() { CAP=$OUT/$1.hu.logcat; PCAP=$OUT/$1.phone.logcat; : > "$CAP"; : > "$PCAP"
  adb -s "$HU" logcat -G 16M; adb -s "$HU" logcat -c; CAPLOOP=$(loopcap "$HU" "$CAP")
  adb -s "$PH" logcat -G 16M; adb -s "$PH" logcat -c; PCAPLOOP=$(loopcap "$PH" "$PCAP"); sleep 1; }
cap_stop() { local p; for p in $CAPLOOP $PCAPLOOP; do pkill -P $p 2>/dev/null; kill $p 2>/dev/null; done
  adb -s "$PH" logcat -d -v time > "$PCAP.dump"
  for f in "$CAP" "$PCAP"; do awk '!seen[$0]++' "$f" | tr -d '\r' > "$f.tmp" && mv "$f.tmp" "$f"; done
  ps aux | grep -c "[l]ogcat"; }          # must print 0; else kill the leftover by its pid

# win <file> <run> : lines from the <run>-start marker to the <run>-end marker (to EOF while the run is open)
win() { LC_ALL=C awk -v s="$2-start" -v e="$2-end" '
  function hit(t) { return (index($0, "AutomationMarker: " t) > 0) || (index($0, "RIGMARK") > 0 && index($0, ": " t) > 0) }
  hit(s) { f = 1 } f { print } f && hit(e) { exit }' "$1"; }
wc_() { win "$1" "$2" | grep -acF -- "$3"; }

setj() { adb -s "$HU" shell "am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.ACTION_SET_SETTINGS --es json '{\"format\":\"open-headunit-settings\",\"version\":1,\"settings\":{$1}}'"; }
getk() { send ACTION_GET_SETTINGS | grep -aoE '"(bt-announce|bt-address|head-unit-make|head-unit-model|allow-external-configuration)":("[^"]*"|true|false)'; }

# pocoput <base.xml> <spec...> : D-POCO (not rooted). App stopped.
pocoput() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  adb -s "$HU" exec-in "run-as $PKG sh -c 'cat > shared_prefs/settings.xml'" < new.xml
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE '(wifi-connection-mode|log-level|allow-external-configuration|enable-audio-sink|auto-connect-last-session|auto-connect-single-usb|auto-start-on-usb|reopen-on-reconnection|use-libusb|bt-address|bt-announce|head-unit-make|head-unit-model)[^/]*'; }
# hu_put <base.xml> <spec...> : D-HU. adb shell is root there, so no su. App stopped.
hu_put() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  OWN=$(adb -s "$HU" shell "stat -c %u:%g /data/data/$PKG" | tr -d '\r')
  printf 'cp /data/local/tmp/new.xml /data/data/%s/shared_prefs/settings.xml\nchown %s /data/data/%s/shared_prefs/settings.xml\nchmod 660 /data/data/%s/shared_prefs/settings.xml\n' "$PKG" "$OWN" "$PKG" "$PKG" > hu_put.sh
  adb -s "$HU" push new.xml /data/local/tmp/new.xml >/dev/null; adb -s "$HU" push hu_put.sh /data/local/tmp/hu_put.sh >/dev/null
  adb -s "$HU" shell "sh /data/local/tmp/hu_put.sh"
  adb -s "$HU" shell "cat /data/data/$PKG/shared_prefs/settings.xml" | grep -aoE '(wifi-connection-mode|log-level|allow-external-configuration|enable-audio-sink|native-poke-all-paired|native-poke-bt-macs|last-connected-native-mac|native-driver-selection-mode|bt-address|bt-announce|head-unit-make|head-unit-model|native-aa-wake-damage-verdict)[^/]*'; }

# ghsnap : which pid is which process on D-MOTO, for the setter attribution in psum
ghsnap() { adb -s "$PH" shell ps -A | grep -aE 'gearhead|com\.android\.settings' | awk '{print $2, $NF}' | tr -d '\r' >> "$OUT/$RUN.pids"; }
# flagdump : Android Auto's live flags, informational only
flagdump() { adb -s "$PH" shell dumpsys activity service $GH/com.google.android.gearhead.service.SharedService > "$OUT/gh-flags-live.txt" 2>&1
  { echo "bytes=$(wc -c < "$OUT/gh-flags-live.txt")"
    grep -a -E 'BluetoothPairing__disable_a2dp|UsbBabysitter__a2dp_fix_car_list|UsbBabysitter__enable_a2dp_at_projection_end' "$OUT/gh-flags-live.txt" | head -6
  } | tee "$OUT/gh-flags-lines.txt"; }
# pend <run> : the last stored A2DP policy for $ADDR in the run window so far
pend() { win "$PCAP" "$1" | grep -aF '/A2dpService(' | grep -aF "Saved connectionPolicy $ADDR = " | tail -1 | awk '{print $NF}' | tr -d '\r'; }

# USB bring-up: ask for USB as the user, wait up to 120 s for SSL, cue H2 and H3 when needed
dlg() { adb -s "$HU" shell dumpsys activity activities | grep -a -m1 -E 'topResumedActivity|mResumedActivity' | grep -aoE 'systemui[./][A-Za-z.]*Usb[A-Za-z]*Activity'; }
usb_up() { local L end d foc=; L=$(nl); end=$((SECONDS+120)); send ACTION_CHECK_USB >/dev/null
  while [ $SECONDS -lt $end ]; do
    tail -n +"$L" "$CAP" | grep -aqF 'SSL handshake complete' && return 0
    d=$(dlg); [ -n "$d" ] && { echo "$(date +%T) $RUN $d" >> "$OUT/dialogs.log"; cue "H2: allow the USB dialog on D-POCO ($d); do not tick Always"; sleep 8; }
    if [ -z "$foc" ] && [ $SECONDS -ge $((end-60)) ]; then foc=1
      adb -s "$PH" shell dumpsys window | grep -a mCurrentFocus | tee -a "$OUT/dialogs.log"
      cue "H3: clear any Android Auto or system screen on D-MOTO and say what it said"; fi
    sleep 2; done; return 1; }

# body <run> <clean|pull|ghkill> <h6 0|1> : from SSL to the end of a run, both stages
body() { local r=$1 LS PL; LS=$(nl); sleep 3; ghsnap; [ "$r" = U0p ] && flagdump; sleep 30
  mark "$r-stop"
  case $2 in
    clean)  send ACTION_DISCONNECT >/dev/null ;;
    pull)   cue "H1: pull D-MOTO's cable out of D-POCO by hand. Do not change the USB mode"
            if waitfor 120 'AapService: session state disconnected' "$LS"; then mark "$r-gone"
            else echo -e "$r\tno-disconnect-line-in-120s" | tee -a "$OUT/summary.tsv"; fi ;;
    ghkill) adb -s "$PH" shell am force-stop $GH; sleep 2; send ACTION_EXIT >/dev/null ;;
  esac
  sleep 60; ghsnap; mark "$r-read"
  if [ "$3" = 1 ] && [ "$(pend "$r")" != 100 ]; then PL=$(pnl)
    cue "H6: on D-MOTO open Settings, Connected devices, Navegadortz2 (D-HU). Say if Media audio is off. Turn it on. Press HOME"
    pwaitfor 180 "Saved connectionPolicy $ADDR = 100" "$PL" || echo -e "$r\tH6-not-seen-in-180s" | tee -a "$OUT/summary.tsv"
    adb -s "$PH" shell input keyevent KEYCODE_HOME; fi
  mark "$r-end"; ghsnap; cap_stop; th_stop
  if [ "$STAGE" = U ]; then
    if [ "$2" = pull ]; then cue "H1: plug D-MOTO back into D-POCO's OTG port"
    else cue "H1: unplug D-MOTO from D-POCO, wait 5 s, plug it back"; fi; fi
  usum "$r"; psum "$r"; th_report "$r" | tee -a "$OUT/summary.tsv"; }

# urun <run> <clean|pull|ghkill> : one Stage U run, default identity, real address
urun() { RUN=$1; th_gate || return 3; apk_check || { echo -e "$RUN\tAPK_MISMATCH" | tee -a "$OUT/summary.tsv"; return 1; }
  adb -s "$HU" shell am force-stop $PKG
  pocoput "$BASE" $KEYS > "$OUT/$RUN.keys" || { echo -e "$RUN\tpocoput-failed" | tee -a "$OUT/summary.tsv"; return 1; }
  cap_start "$RUN"; mark "$RUN-start"; ghsnap
  adb -s "$HU" shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; sleep 3
  setj '"bt-announce":"real","head-unit-make":"Google","head-unit-model":"Desktop Head Unit"' >> "$OUT/$RUN.keys"; getk >> "$OUT/$RUN.keys"
  if ! usb_up; then cue "H1: re-enumerate D-MOTO: unplug it from D-POCO, wait 5 s, plug it back"; sleep 10
    usb_up || { mark "$RUN-end"; cap_stop; th_stop; echo -e "$RUN\tVOID no session" | tee -a "$OUT/summary.tsv"; return 2; }; fi
  body "$RUN" "$2" 0; }

# nrun <run> <clean|ghkill> <h6 0|1> : one Stage N run, D-HU's own identity, real address
nrun() { RUN=$1; th_gate || return 3; apk_check || { echo -e "$RUN\tAPK_MISMATCH" | tee -a "$OUT/summary.tsv"; return 1; }
  adb -s "$PH" shell cmd connectivity airplane-mode enable; sleep 8
  adb -s "$HU" shell am force-stop $PKG
  hu_put "$BASE" $KEYS > "$OUT/$RUN.keys" || { echo -e "$RUN\thu_put-failed" | tee -a "$OUT/summary.tsv"; return 1; }
  cap_start "$RUN"; mark "$RUN-start"; getk >> "$OUT/$RUN.keys"
  local L; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; sleep 15
  adb -s "$PH" shell cmd connectivity airplane-mode disable; sleep 2
  adb -s "$PH" shell svc wifi enable; adb -s "$PH" shell svc bluetooth enable; sleep 3; ghsnap
  waitfor 120 'SSL handshake complete' "$L" || { mark "$RUN-end"; cap_stop; th_stop; echo -e "$RUN\tVOID no session" | tee -a "$OUT/summary.tsv"; return 2; }
  body "$RUN" "$2" "$3"; }

# usum <run> : the head unit counts, and the phone crash and disabler counts
usum() { local c=$CAP r=$1
  { echo "== $r"
    echo "hu.verbs=$(wc_ $c $r 'AutomationReceiver: ')"
    echo "hu.announce=$(win $c $r | grep -aoE 'Bluetooth service announced with carAddress=[^ ]+ \(bt-announce=[a-z]+\)' | sort | uniq -c | tr '\n' ' ')"
    echo "hu.announce_empty=$(wc_ $c $r 'BT MAC Address is empty, so no Bluetooth service is announced')"
    echo "hu.ssl=$(wc_ $c $r 'SSL handshake complete')"
    echo "hu.ssl_after_stop=$(win $c $r | awk -v m="AutomationMarker: $r-stop" 'index($0, m) { f = 1; next } f' | grep -acF 'SSL handshake complete')"
    echo "hu.disconnected:"; win $c $r | grep -aF 'AapService: session state disconnected' | head -3
    echo "hu.acc_mode=$(wc_ $c $r 'Found device already in accessory mode') hu.vfail=$(wc_ $c $r 'Version request send failed')"
    echo "hu.usb_perm=$(wc_ $c $r 'Requesting USB permission for')"
    echo "hu.match=$(wc_ $c $r 'MATCH! Starting AapService') hu.groups=$(wc_ $c $r 'createGroup SUCCESS')"
    echo "hu.fatal=$(wc_ $c $r 'FATAL EXCEPTION')"
    echo "ph.disabler=$(wc_ $PCAP $r 'disabling A2dp via profile disabler')"
    echo "ph.fatal_processes:"; win $PCAP $r | grep -a -A1 'FATAL EXCEPTION' | grep -aF 'Process: ' | head -3
    echo "ph.restarts=$(cat "$PCAP.restarts" 2>/dev/null | wc -l)"
  } | tee -a "$OUT/summary.tsv"; }

# psum <run> : the A2DP policy for $ADDR in the run window, from the phone capture
psum() { local r=$1; win "$PCAP" "$r" > "$OUT/$r.pwin"
  LC_ALL=C awk -v r="$r" -v A="$ADDR" -v S="$SUF" -v pf="$OUT/$r.pids" '
  function ms(t,  a) { split(t, a, /[:.]/); return ((a[1] * 60 + a[2]) * 60 + a[3]) * 1000 + a[4] }
  function gh(p) { return (p in own) && own[p] ~ /^com\.google\.android\.projection\.gearhead/ }
  BEGIN { while ((getline l < pf) > 0) { n = split(l, f, " "); if (n >= 2 && f[1] ~ /^[0-9]+$/) own[f[1]] = f[2] } ph = 0 }
  /Start proc [0-9]+:/ { if (match($0, /Start proc [0-9]+:[^ \/]+/)) { s = substr($0, RSTART + 11, RLENGTH - 11); c = index(s, ":"); own[substr(s, 1, c - 1)] = substr(s, c + 1) } }
  index($0, "RIGMARK") && index($0, ": " r "-stop") { stop = $2; ph = 1 }
  index($0, "RIGMARK") && index($0, ": " r "-gone") { gone = $2 }
  index($0, "RIGMARK") && index($0, ": " r "-read") { rd = $2; ph = 2 }
  p0 == "" && index($0, "getProfileConnectionPolicy: XX:XX:XX:XX:" S ", profile=2, connectionPolicy = ") { p0 = $NF; p0t = $2 }
  index($0, "/BluetoothA2dp(") && index($0, "setConnectionPolicy(" A ", ") {
    match($0, /BluetoothA2dp\( *[0-9]+\)/); pid = substr($0, RSTART + 14, RLENGTH - 14); gsub(/[^0-9]/, "", pid)
    match($0, /, -?[0-9]+\)/); v = substr($0, RSTART + 2, RLENGTH - 3)
    o = (pid in own) ? own[pid] : "unknown"
    print "p.set " $2 " pid=" pid " owner=" o " value=" v " phase=" ph
    if (ph == 0 && v == "0" && gh(pid)) dis++
    if (ph == 1 && v == "100" && rt == "") { rt = $2; rp = pid; ro = o }
    if (ph <= 1 && !gh(pid)) other++
  }
  index($0, "/A2dpService(") && index($0, "Saved connectionPolicy " A " = ") { if (ph <= 1) endr = $NF; endall = $NF }
  END {
    print "p.P0=" (p0 == "" ? "none" : p0) " at " p0t
    print "p.disabled_by_gh_before_stop=" dis + 0
    print "p.stop=" stop " p.gone=" gone " p.read=" rd
    if (rt != "") print "p.restore=" rt " pid=" rp " owner=" ro " delay_from_stop_ms=" ms(rt) - ms(stop) (gone != "" ? " delay_from_gone_ms=" ms(rt) - ms(gone) : "")
    else print "p.restore=none"
    print "p.non_gh_setters_before_read=" other + 0
    print "p.END_at_read=" (endr == "" ? "none" : endr) " p.END_at_end=" (endall == "" ? "none" : endall)
    rdg = "OTHER"
    if (rt != "" && ro ~ /^com\.google\.android\.projection\.gearhead/ && endr == "100") rdg = "RESTORED"
    else if (rt == "" && endr == "0") rdg = "NOT_RESTORED"
    print "p.reading=" rdg
  }' "$OUT/$r.pwin" | sed "s/^/$r /" | tee -a "$OUT/summary.tsv" "$OUT/policy.tsv"; }
```

## 7. The lines that decide the runs

App lines were checked with `grep -rF` against `app/src/main` on `fff96699`. All print at INFO. The phone lines were checked against D-MOTO's own captures in the round 1 asset (Android 14, Android Auto 17.9.664004). They are Android's Bluetooth stack lines and `ActivityManager` lines, so they do not depend on the Android Auto version. Every count is taken inside the run's window, from `<run>-start` to `<run>-end` (`AutomationMarker:` in the unit capture, `RIGMARK` in the phone capture, `win`).

| Line (fixed substring) | File | Meaning |
|---|---|---|
| `Bluetooth service announced with carAddress=` | unit | the address on the wire, then ` (bt-announce=real)` |
| `BT MAC Address is empty, so no Bluetooth service is announced` | unit | an empty `bt-address`: the run is void |
| `SSL handshake complete` | unit | a session formed (never prefix `Handshake:`) |
| `AapService: session state disconnected` | unit | the session ended; the reason follows in brackets (composed from `AapService: session state `) |
| `AutomationReceiver: ` | unit | a verb landed; a missing one voids the step |
| `AutomationMarker: ` | unit | a run marker |
| `Found device already in accessory mode`, `Version request send failed`, `Requesting USB permission for` | unit | the USB bring-up, and the re-attach failure from round 1 |
| `MATCH! Starting AapService`, `createGroup SUCCESS` | unit | Stage N discard rules |
| `FATAL EXCEPTION` | both | a crash; on the phone, the next line's `Process: ` names whose |
| `RIGMARK` | phone | the run markers |
| `getProfileConnectionPolicy: XX:XX:XX:XX:` | phone | a read of the stored policy; the first one for `$SUF` in a window is **P0** |
| `setConnectionPolicy(` | phone | a request to change it, under tag `BluetoothA2dp(<pid of the asker>)` |
| `Saved connectionPolicy ` | phone | the stored value, under tag `A2dpService(...)` |
| `Start proc ` | phone | maps a pid to a process name |
| `disabling A2dp via profile disabler` | phone | Android Auto's disabler ran (round 1's line, now a cross-check only) |

**Reading a run (`psum`).** Phases: 0 is before the `<run>-stop` marker, 1 is from `<run>-stop` to `<run>-read` (60 s after the end), 2 is after `<run>-read` (H6 only).

- **P0**: the value in the first `getProfileConnectionPolicy` line for `$SUF`.
- **Disabled**: `p.disabled_by_gh_before_stop` of 1 or more, that is a `setConnectionPolicy(<ADDR>, 0)` in phase 0 from a process whose name starts `com.google.android.projection.gearhead`.
- **RESTORED**: the first `setConnectionPolicy(<ADDR>, 100)` in phase 1 is from a Gearhead process, and the last `Saved connectionPolicy <ADDR> = ` value up to `<run>-read` is `100`. Report `delay_from_stop_ms`, and `delay_from_gone_ms` for a pull.
- **NOT_RESTORED**: no `setConnectionPolicy(<ADDR>, 100)` in phase 1, and the last stored value up to `<run>-read` is `0`.
- **OTHER**: anything else. Quote every `p.set` line. A setter that is not a Gearhead process in phase 0 or 1 is the H4 signal (something other than Android Auto changed the policy). Report it whenever `p.non_gh_setters_before_read` is above 0.

## 8. Runs

### Prepare (P), on cables, before Stage U

1. **Build and identity** (section 1). Record `WANT_MD5`, both `commit` replies, and whether you built.
2. **Back up both settings files** with each app stopped: D-POCO with `adb -s 4f4027e9 shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings_backup_poco.xml`; D-HU with `adb -s 27870808938846 shell cat /data/data/$PKG/shared_prefs/settings.xml > $OUT/settings_backup_dhu.xml`. Diff each against round 1's closing state if you still have it, and state the delta. Record the rig audio keys from D-HU's backup.
3. **`stat` both `shared_prefs/` directories** and record owner, group and mode.
4. **Addresses.** `adb -s <serial> shell settings get secure bluetooth_address`: `POCO_BT` on `4f4027e9` (round 1: `DC:B7:2E:5E:4E:59`), `MOTO_BT` on `ZY22GC3BM4`. Read `DHU_ANN` as the `bt-address` value in D-HU's backup (round 1 announced `11:46:03:10:33:59`). If a value differs from round 1, use the new one and say so.
5. **Bonds.** `adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -i -A30 "Bonded devices"` must list D-POCO and D-HU. If D-POCO is missing, H5, then check again. Still missing: Stage U is UNTESTABLE.
6. **D-MOTO.** Run `rig_preflight.sh`; Bluetooth must be on (round 1's first preflight failed on `bluetooth_on=0`). Then:
   ```bash
   adb -s ZY22GC3BM4 shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
   for t in CAR.BT.SVC CAR.BT.SVC.LITE CAR.BT.A2DP CAR.BT.A2dpDisabler CAR.BT.LITE; do adb -s ZY22GC3BM4 shell setprop log.tag.$t VERBOSE; done
   adb -s ZY22GC3BM4 shell settings get global zen_mode          # must be 0; else: cmd notification set_dnd off
   adb -s ZY22GC3BM4 shell svc power stayon true
   adb -s ZY22GC3BM4 shell dumpsys window | grep -a -E "mShowingLockscreen|mDreamingLockscreen"   # H0 if locked behind a PIN
   adb -s ZY22GC3BM4 shell input keyevent KEYCODE_HOME
   ```
   Record the Android Auto version. If it is not `17.9.664004`, say so in Setup notes and go on: the policy lines are Android's, not Android Auto's.
7. **D-HU out of the way for Stage U:** `adb -s 27870808938846 shell am force-stop com.andrerinas.headunitrevived`.

### Stage U: D-POCO as head unit over USB, D-MOTO as the phone

**Switch to wireless adb**, as in round 1:

```bash
adb -s 4f4027e9 tcpip 5555; adb -s ZY22GC3BM4 tcpip 5555; sleep 3
POCO_IP=$(adb -s 4f4027e9 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
MOTO_IP=$(adb -s ZY22GC3BM4 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
adb connect $POCO_IP:5555; adb connect $MOTO_IP:5555
adb -s $POCO_IP:5555 shell dumpsys usb > $OUT/dumpsys-usb-before.txt
```

Then H1: unplug D-POCO from the PC; unplug D-MOTO from the PC and plug it into D-POCO's OTG port. Check that `adb -s $POCO_IP:5555 shell getprop ro.product.model` and `adb -s $MOTO_IP:5555 shell getprop ro.product.model` both answer.

```bash
STAGE=U; HU=$POCO_IP:5555; PH=$MOTO_IP:5555; BASE=$OUT/settings_backup_poco.xml
ADDR=$POCO_BT; SUF=${POCO_BT: -5}
KEYS="int:wifi-connection-mode=0 int:log-level=2 int:onboarding-version=2 bool:allow-external-configuration=true bool:enable-audio-sink=true bool:kill-on-disconnect=false bool:auto-connect-last-session=false bool:auto-connect-single-usb=false bool:auto-start-on-usb=false bool:reopen-on-reconnection=false bool:use-libusb=false set:connection-modes=usb,wifi str:bt-address=$POCO_BT del:bt-announce del:head-unit-make del:head-unit-model del:video-profile-starvation-cap del:native-aa-wake-damage-verdict del:native-aa-wireless del:wifi-launcher-mode"
source ./lib1008r2.sh
```

**Valid run (Stage U).** A run counts only when all of these hold. Otherwise re-run it once, then grade it INCONCLUSIVE with the failed item named:

- an `AutomationReceiver: ` line for every verb sent (`hu.verbs` of 7 or more: four or five markers, `ACTION_SET_SETTINGS`, `ACTION_GET_SETTINGS`, `ACTION_CHECK_USB`, and `ACTION_DISCONNECT` or `ACTION_EXIT` where the end sends one);
- `hu.ssl` = 1 and `hu.ssl_after_stop` = 0;
- `hu.announce` is exactly one line, `carAddress=$POCO_BT (bt-announce=real)`, and `hu.announce_empty` = 0;
- `getk` in `$OUT/$RUN.keys` shows `Google` and `Desktop Head Unit`;
- the phone window carries the `-start`, `-stop`, `-read` and `-end` markers;
- `p.P0` is not `none`, and `p.disabled_by_gh_before_stop` is 1 or more;
- `hu.fatal` = 0, and `ph.fatal_processes` names no `com.google.android.projection.gearhead` process;
- for a pull: a `session state disconnected` line after the stop marker.

**Rule R (recovery).** After any Stage U run whose `p.END_at_read` is not `100`, and whose next listed run is not a U6 run, run a recovery run first: `urun U6r<n> clean` (n = 1, 2, ...). Grade it as a U6 run. If a recovery or a U6 run ends with `p.END_at_read` not `100`, stop Stage U: D-MOTO's policy for D-POCO is then `0` and the rig has no other lever. Grade the runs not reached UNTESTABLE ("D-POCO's policy left at 0 with no lever to reset it") and go to the close.

#### U0p. Gate and primer: clean end

```bash
urun U0p clean
```

- **PASS**: valid, `p.reading=RESTORED`. This repeats round 1's U1 with the new instrument, and leaves the policy at `100` for U5a.
- **FAIL**: valid, and the reading is not RESTORED. Then Android Auto on D-MOTO no longer restores at a clean end, which changes every run below. Stop Stage U and go to Stage N.
- **UNTESTABLE** for the whole stage: `ph.disabler` of 1 or more but no `setConnectionPolicy(` line for `$ADDR` in the window. The phone does not print the instrument lines.
- **Report** the flag lines in `$OUT/gh-flags-lines.txt` verbatim, and its byte count. They are informational. If `bytes` is under 200, say "flag dump refused" and go on.

#### U5a. Cable pulled out mid-session, three times (the point of the round)

```bash
urun U5a1 pull
urun U5a2 pull
urun U5a3 pull
```

- A repetition counts when it is valid and `p.P0` = `100`. A repetition with another P0 is not counted; apply rule R and run the next one.
- **PASS**: three counted repetitions with the same `p.reading`.
- **FAIL**: counted repetitions with different readings. Report each.
- **Report** per repetition: P0, `p.reading`, `delay_from_gone_ms`, `delay_from_stop_ms`, and the `session state disconnected` line with its reason.
- **What a reading means for the reply.** RESTORED: a pulled cable gives the toggle back, as a clean end does. NOT_RESTORED: a pulled cable leaves it off, which is the reporter's symptom.

#### U5b. Android Auto force-stopped mid-session over USB, twice, each followed by U6

```bash
urun U5b1 ghkill
urun U6a  clean
urun U5b2 ghkill
urun U6b  clean
```

**U5b, per repetition**: counts when valid and `p.P0` = `100`.

- **PASS**: two counted repetitions with the same reading. Round 1's N3 over Native AA read NOT_RESTORED (section 9).
- **FAIL**: the readings differ.
- If U5b1 reads RESTORED, U6a still runs; then its P0 is `100` and it is graded INCONCLUSIVE as below.

**U6, per repetition** (the repair question): a plain clean session that starts from the policy that U5b left.

- **PASS**: valid, `p.P0` = `0`, and `p.reading` is RESTORED or NOT_RESTORED. If both U6a and U6b ran, the two readings must agree; if they differ, both are FAIL.
- **INCONCLUSIVE**: valid and `p.P0` is not `0`. Write "Android Auto restored the policy before this session" and quote the `p.set` lines from the U5b window and this one.
- **What a reading means for the reply.** RESTORED: the next ordinary drive puts the toggle back by itself. NOT_RESTORED: the toggle stays off until the user turns it on.

**Closing Stage U.**

1. `send ACTION_EXIT`.
2. `adb -s $HU shell am start -a android.intent.action.VIEW -d headunit://exit`, then `sleep 3`, then `adb -s $HU shell am force-stop $PKG`.
3. Restore D-POCO's backup with `pocoput $BASE` (no spec) and read it back.
4. `adb -s $HU shell dumpsys usb > $OUT/dumpsys-usb-after.txt`.
5. H1: unplug D-MOTO from D-POCO and put D-MOTO and D-POCO back on PC cables.
6. `adb -s $POCO_IP:5555 usb` and `adb -s $MOTO_IP:5555 usb`.
7. D-POCO stays out of Stage N: `adb -s 4f4027e9 shell svc bluetooth disable` and `adb -s 4f4027e9 shell cmd connectivity airplane-mode enable`. Confirm both with `dumpsys`.
8. Record the last `p.END_at_end` of Stage U: it is D-MOTO's policy for D-POCO as the rig is left.

### Stage N: D-HU as head unit over Native AA, D-MOTO as the phone

```bash
STAGE=N; HU=27870808938846; PH=ZY22GC3BM4; BASE=$OUT/settings_backup_dhu.xml
ADDR=$DHU_ANN; SUF=${DHU_ANN: -5}
KEYS="int:wifi-connection-mode=3 int:log-level=2 int:onboarding-version=2 int:native-driver-selection-mode=0 bool:allow-external-configuration=true bool:enable-audio-sink=true bool:native-poke-all-paired=false set:native-poke-bt-macs=$MOTO_BT str:last-connected-native-mac=$MOTO_BT str:native-preferred-device-mac=$MOTO_BT del:bt-announce del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"
source ./lib1008r2.sh
```

**Valid run (Stage N)**: as Stage U, with these changes:

- the announce line must read `carAddress=$DHU_ANN (bt-announce=real)`, and `getk` must show no `bt-announce`;
- `hu.verbs` of 7 or more (markers, `ACTION_GET_SETTINGS`, `ACTION_START_WIRELESS_SCAN`, and `ACTION_DISCONNECT` or `ACTION_EXIT`);
- discard and re-run on `hu.match` above 0 or `hu.groups` above 1 (template §4).

`nrun` cycles D-MOTO's airplane mode, so D-MOTO's Bluetooth starts again in every run and reads every bonded device's policy. That read is P0.

#### N0p. Gate and primer: clean end, H6 if needed

```bash
nrun N0p clean 1
```

- **PASS**: valid, `p.P0` = `100`, `p.reading=RESTORED`. Round 1's N1 with the new instrument.
- **FAIL**: valid, `p.P0` = `100`, and the reading is not RESTORED. Stop Stage N.
- If `p.P0` is `0`, the run still counts as the primer: H6 ran inside it. Grade it INCONCLUSIVE and say so.

#### N4. Android Auto force-stopped, then a clean recovery session, twice

```bash
nrun N4a1 ghkill 0
nrun N4b1 clean  1
nrun N4a2 ghkill 0
nrun N4b2 clean  1
```

There is no H6 between N4a and N4b. That is the point: N4b starts from what N4a left.

- **N4a, per repetition**: counts when valid and `p.P0` = `100`. **PASS**: two counted repetitions with the same reading. **FAIL**: they differ.
- **N4b, per repetition**: graded as U6. **PASS**: valid, `p.P0` = `0`, reading RESTORED or NOT_RESTORED, and the same reading in N4b1 and N4b2. **INCONCLUSIVE**: `p.P0` is not `0`. If N4b ends below `100`, the H6 inside it puts the toggle back before N4a2; report the operator's words ("Media audio was off" or "on") beside `p.END_at_read`.

**Closing Stage N, and the round.**

1. `send ACTION_EXIT`, then `adb -s $HU shell am force-stop $PKG`.
2. Restore D-HU's backup with `hu_put $BASE` (no spec). Read back `head-unit-make` and `head-unit-model`; they must match the backup.
3. D-MOTO's policy for D-HU must be `100`: the last `Saved connectionPolicy $DHU_ANN = ` line of N4b2 says so. If it is not `100`, or N4b2 did not run, run H6 once more with a capture open:
   ```bash
   RUN=END; cap_start END; mark END-start; cue "H6: turn Media audio on for Navegadortz2 on D-MOTO"
   pwaitfor 180 "Saved connectionPolicy $DHU_ANN = 100" 1; mark END-end; cap_stop
   ```
   Never leave the rig with that toggle off.
4. On D-POCO, `svc bluetooth enable` and `cmd connectivity airplane-mode disable`, and confirm with `dumpsys`.
5. On D-MOTO, `svc power stayon false`.
6. Put the whole `policy.tsv` in the results file.

**Stop rule.** Stage U runs at most 8 listed runs and 2 recovery runs (U6r1, U6r2). Stage N runs at most 5. Each run is re-run at most once when it is not valid. A stage whose first two bring-ups both end VOID is UNTESTABLE from that point.

## 9. Do not re-run

These are settled. The host read them from round 1's asset with the policy lines of section 7. Delays are on D-MOTO's clock, from the `<run>-stop` marker.

| Round 1 run | P0 | Disabled by `gearhead:car` | After the end | Delay |
|---|---|---|---|---|
| U1a (clean) | `-1` | yes, 10:55:27.019 | Gearhead set `100` at 10:56:41.804 | 1853 ms |
| U1b, U1c (clean) | `100` | yes | Gearhead set `100` | 1822 ms, 1811 ms |
| U4 (list identity, clean) | `100` | yes | Gearhead set `100` | 1849 ms |
| U2 (`blank`), U3 (`skip`) | none | no `setConnectionPolicy(` line for the address | | |
| N1, N2a, N2b (clean) | `100` | yes | Gearhead set `100` | 1361 ms, 1366 ms, 1389 ms |
| N3a, N3b (Android Auto force-stop) | `100` | yes | **no Gearhead set**. `com.android.settings` (the operator's H6) set `100` at +48 s and +32 s | |

- **Default identity disables on this phone, and a clean end restores it.** Do not re-run U1, U4, N1 or N2.
- **`blank` and `skip` stop the disable.** Do not re-run U2 or U3.
- **An Android Auto force-stop over Native AA leaves the policy at `0`.** N4a repeats it only as the set-up for N4b.
- **A restore does not need ALLOWED before.** U1a started at `-1` and ended at `100`.
- **Two link losses restored, by accident.** Round 1's voided first U4 (`link_lost` on the unit at 11:23:31.096; Gearhead set `100` at 11:23:51.840 on the phone's clock) and voided first N3b (Gearhead set `100` at 11:52:37.266, before the unit logged `link_lost` at 11:52:50.500). These were not designed, so U5a measures the USB cable pull on purpose.
- **Calls are not re-run.** Round 1 could place one call only, and the call does not touch the A2DP policy.

## 10. Report back

1. **U5a**: the reading on each of the three pulls, with P0 and `delay_from_gone_ms`. This is the reporter's end.
2. **U6 and N4b**: from P0 = `0`, does the next clean session put the policy back? Give the reading for each, and say if U6 and N4b agree.
3. **U5b and N4a**: the reading after an Android Auto force-stop, over USB and over Native AA.
4. **Any `p.non_gh_setters_before_read` above 0**, with its `p.set` lines quoted.
5. **The flag lines**, verbatim, or "flag dump refused".
