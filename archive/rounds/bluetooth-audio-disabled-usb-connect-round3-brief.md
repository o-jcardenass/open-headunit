# bluetooth-audio-disabled-usb-connect, round 3 brief

Published name on the transfer branch: `bluetooth-audio-disabled-usb-connect-round3-brief.md`.

This is a **measurement round** on the same probe build as rounds 1 and 2. No fix is on trial. Round 2 left two questions open. The point of this round is the first one: **does a pulled USB cable get the phone's A2DP setting back?** Round 2 saw it on one valid pull only, because operator cues arrived early or late. The second question is the Native AA stage, which round 2 could not start: Android Auto on D-MOTO never answered the poke.

## 1. Build and baseline

- Candidate: branch `fix/bluetooth-audio-disabled-usb-connect` on the fork, SHA **`fff96699`** (`fff96699480d878bb36bd04078d77260231b19dc`). This is the round 1 and round 2 SHA. History was not rewritten, and no commit was added.
  ```bash
  git fetch fork fix/bluetooth-audio-disabled-usb-connect
  git -C ../ohu-wt-bad-fff96699 rev-parse HEAD     # must print fff96699480d878bb36bd04078d77260231b19dc
  ```
- **Reuse the round 1 APK.** If `apks/candidate-fff96699.apk` exists and its md5 is `234caaadff2d4731e006591a0c69bb2a`, set `WANT_MD5=234caaadff2d4731e006591a0c69bb2a` and do not build. If it is gone, build it from the round 1 worktree with `build_hur.sh` (`HUR_DIR=../ohu-wt-bad-fff96699`), run `run_unit_tests.sh` (gate: **2747 tests, 0 failures**; a failure stops the round), copy the APK to `apks/candidate-fff96699.apk` at once, and record its md5 as `WANT_MD5`.
- On D-POCO (`4f4027e9`) and D-HU (`27870808938846`): if `apk_check` (section 6) matches, do not install. If not, back up `settings.xml` first (section 8, Prepare step 2), then `adb -s <serial> install -r apks/candidate-fff96699.apk`, then diff `settings.xml` against the backup. Round 2 found another APK (md5 `50f8137b85adf64b917c8e9274c70da0`) on both units and had to reinstall.
- `send ACTION_QUERY_STATE` on each unit must reply with a `commit` that begins `fff96699`.
- DEX symbol for identity (template §5): `BluetoothAnnouncePolicy`. Check it only if you built a new APK.
- **No baseline APK.** Every comparison is between runs on the candidate.

## 2. What this is and why it exists

**The report.** A user's phone is paired with the head unit over Bluetooth. When Android Auto connects by cable, the phone switches off the head unit's "Media audio" toggle by itself.

**What rounds 1 and 2 measured** on D-MOTO (Android 14, Android Auto 17.9.664004). The instrument is D-MOTO's own Bluetooth log, read in the phone capture:

- `setConnectionPolicy(<address>, <value>)` under tag `BluetoothA2dp(<pid>)`: a process asks for a change. The pid names the process.
- `Saved connectionPolicy <address> = <value>` under tag `A2dpService(...)`: the stored value. `100` is on, `0` is off, `-1` is not set.
- `getProfileConnectionPolicy: XX:XX:XX:XX:<last two octets>, profile=2, connectionPolicy = <value>`: a read. The first read in a run is the value before the session (P0).

The findings so far:

| End of the session | Reading | Where measured |
|---|---|---|
| Android Auto session starts (any transport) | `gearhead:car` sets `0` within about 1 s of SSL | rounds 1 and 2, every run |
| Clean end (`ACTION_DISCONNECT`) | `gearhead:car` sets `100` about 1.8 s later | U1, U4, U0p (USB); N1, N2 (Native AA) |
| Android Auto force-stopped | stays `0` | U5b1, U5b2 (USB); N3a, N3b (Native AA, by eye) |
| Next clean session after a force-stop | `gearhead:car` sets `100` at its end | U6a, U6b, U6r2v (USB only) |
| **Cable pulled out** | `gearhead:car` set `100` in every pull that happened, but **only U5a2 was a valid run** | round 2 |

**Why the pull matters.** A pulled cable is the end that the reporter makes every day. If it leaves the setting at `0`, the reporter's symptom has an explanation that is not ours to fix in the app. If it restores, the symptom needs another cause.

**Why round 2's pulls did not count.** Three of five pull runs failed on the operator cue: the cue went to a log that the host read, and the operator did not see it in time. One pull came too late for the 120 s window. One replug came before the plug-back cue, so a second session formed. One link was already lost before the stop marker. This round changes the harness for each of these (section 3).

## 3. What is different about this round

**Layout.** Same as rounds 1 and 2.

| Stage | Head unit | Phone | adb | Runs |
|---|---|---|---|---|
| **U** | D-POCO (`4f4027e9`), Android 15 | D-MOTO (`ZY22GC3BM4`), on D-POCO's OTG port | both over wireless adb | U5a4 to U5a9 (stop at 3 counted), U6r1, U6r2 if needed |
| **N** | D-HU (`27870808938846`), Native AA | D-MOTO, on its PC cable | cables | N0p (N0px), N4a1, N4b1, N4a2, N4b2 |

**Changes to the harness, each one for a round 2 failure.**

1. **The operator watches the cue log directly** (hand step H7). Before Stage U, the operator opens a terminal on the rig PC with `tail -n0 -F <OUT>/hand-steps.log` and keeps it in view. The host also relays every `OPERATOR:` line in its chat at once.
2. **The pull has no 120 s deadline.** The script cues the pull, then repeats the cue every 30 s for up to 600 s, until the head unit logs `AapService: session state disconnected`.
3. **The cue says "leave it out".** The plug-back cue comes only after the 60 s read. A replug before it forms a second session, and the run is not valid (`hu.ssl_after_stop` above 0).
4. **The session must be live at the stop marker** (`hu.live_at_stop` = 1). This is the check that U5a3 would have failed.
5. **`usb_up` finds a session that formed before `ACTION_CHECK_USB`.** It searches the whole run capture, not only the lines after the verb.
6. **A scripted recovery for a failed USB bring-up.** Round 2 saw `Version request send failed` and `Unable to parse TLS packet header` after cable events, and a Gearhead force-stop cleared them. `urun` now does this itself, before any session, and records it. A force-stop with no session open does not change the policy.
7. **`pocoput` retries its readback**, as the rig fixed it in round 2.

**Stage N.** It runs after Stage U, with a recovery step between its first two bring-ups (section 8). Round 2 found D-MOTO's system USB Preferences screen in focus before N0p, and Gearhead had been force-stopped several times in Stage U. This round's Stage U force-stops Gearhead only in the recovery of item 6. Record each one, because it is a candidate cause if Stage N fails again.

**Facts carried over from round 2.**

- **No call, no music.** Android Auto disables at SSL, not at media start.
- **D-MOTO's Settings app stays closed** during both stages, except inside H6.
- **D-POCO has no hand lever.** Nothing on the rig can set D-MOTO's policy for D-POCO back to `100`. Only an Android Auto clean end can. That is why rule R (section 8) puts a clean recovery run after every run that ends below `100`.
- **Read windows are on the phone's clock only.** Every delay is between two phone-side lines.
- **The rig audio keys are a deliberate worst case.** Record them as found. Do not write or reset them.

**Expected INCONCLUSIVE, and that is not a failure:**

- N4b1 or N4b2 when its P0 is not `0`: Android Auto put the policy back before the next session started. Record P0.
- Stage N as a whole, if both bring-ups of N0p end VOID after the recovery step.

## 4. Hand steps, and why no verb exists

The executor prints a line that starts `OPERATOR:`, rings the terminal bell, and appends a line to `$OUT/hand-steps.log` (`cue`). It waits on a log line where one exists, never on a clock alone.

| Id | Step | Why no verb |
|---|---|---|
| H0 | Unlock D-MOTO once in Prepare if it is behind a PIN | adb has no way past a PIN (`rig-quirks/units/D-MOTO.md`) |
| H1 | Cables: move D-POCO and D-MOTO between the PC and D-POCO's OTG port; **pull D-MOTO's cable out of D-POCO by hand** in U5a and **leave it out until the plug-back cue**; re-plug D-MOTO at the end of each Stage U run | A cable is hardware. It must be a physical pull, not a change of USB mode in D-MOTO's settings |
| H2 | Allow a system USB dialog on D-POCO (`UsbPermissionActivity` or `UsbConfirmActivity`). Do not tick "Always" | A system dialog, not our app |
| H3 | Clear any Android Auto or system screen on D-MOTO that blocks the session, and say what it said | Android Auto's screens have no adb lever. If the screen is the USB Preferences screen, the host may send `KEYCODE_HOME` instead, as in round 2, and say so |
| H5 | Pair D-MOTO and D-POCO, only if Prepare finds them not bonded | Pairing needs a confirmation on both screens |
| H6 | Stage N only, inside a run window: open D-MOTO's Settings, Connected devices, the D-HU entry (`Navegadortz2`), say if "Media audio" is off, turn it on, press HOME | No public API sets another app's A2DP policy. The script waits for `Saved connectionPolicy <D-HU address> = 100` |
| H7 | Before Stage U, open a terminal on the rig PC, run `tail -n0 -F <OUT>/hand-steps.log`, and keep it in view until the close | The cue must reach the operator in time. In round 2 it did not, and three runs were lost |

Before each pull run, the host tells the operator the sequence in one message: "Wait for the PULL cue. Pull the cable from D-POCO. Leave it out. Plug it back only at the plug-back cue, about 70 s later."

## 5. Settings keys

Same as round 2. Write the base keys with the app stopped, after a backup (template §1). D-POCO is not rooted: use `pocoput`. D-HU: use `hu_put` (`adb shell` is root there). Read every key back before each launch. Record the rig audio keys (`use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`) from D-HU's backup as found (round 2: `false`, `8`, `20`). Do not write them.

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

On D-POCO, `urun` sets `bt-announce`, `head-unit-make` and `head-unit-model` per run through `ACTION_SET_SETTINGS`, to `real`, `Google` and `Desktop Head Unit`, and reads them back with `ACTION_GET_SETTINGS`. Our announce line on the wire is the proof of the value in force. Stage N uses D-HU's own identity with `bt-announce` absent, which announces the real address.

## 6. Shell setup

Make `hur-wifi-test-scripts/bluetooth-audio-disabled-usb-connect-round3/`. Copy `ohu_setkeys.py` into it from `../bluetooth-audio-disabled-usb-connect-round2/`. Save `lib1008r3.sh` below beside it. List both in Setup notes. If a function does not match the real line format, fix it, say so in Setup notes, and keep going. Run each stage under `flock /tmp/ohu-rig.lock`. One adb call at a time per unit; the two log captures are the only streams that run beside them (house rule 8).

Before the round, check for a leftover thermal watcher and kill it by pid: `ps aux | grep -E "[r]ig_thermal.sh watch|[s]leep 20"`.

**`lib1008r3.sh`** (round 2's lib; the changed functions are `cue`, `pocoput`, `usb_up`, `body`, `urun`, `nrun`, `usum`, and the new `void_`, `cue_pull`):

```bash
# lib1008r3.sh : source after setting STAGE, HU, PH, OUT, BASE, KEYS, ADDR, SUF, WANT_MD5.
# ADDR = the announced address in full (DC:B7:2E:5E:4E:59 form); SUF = its last five characters.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
GH=com.google.android.projection.gearhead
send() { a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; adb -s "$PH" shell log -t RIGMARK "$1"; echo "$(date +%T.%3N) $1" >> "$OUT/marks.log"; }
# cue: the operator reads hand-steps.log in a terminal (H7); the host also relays the OPERATOR line
cue() { printf '\a'; echo "OPERATOR: $1 NOW"; echo "$(date +%T) ${RUN:-} $1" >> "$OUT/hand-steps.log"; }
nl() { echo $(( $(wc -l < "$CAP") + 1 )); }
pnl() { echo $(( $(wc -l < "$PCAP") + 1 )); }
# waitfor <secs> <fixed-string> <from-line> : 0 when the string is in the unit capture at or after <from-line>
waitfor() { local end=$((SECONDS+$1)); while [ $SECONDS -lt $end ]; do
  tail -n +"$3" "$CAP" | grep -aqF -- "$2" && return 0; sleep 0.5; done; return 1; }
# pwaitfor <secs> <fixed-string> <from-line> : the same on the phone capture, A2dpService lines only
pwaitfor() { local end=$((SECONDS+$1)); while [ $SECONDS -lt $end ]; do
  tail -n +"$3" "$PCAP" | grep -aF '/A2dpService(' | grep -aqF -- "$2" && return 0; sleep 0.5; done; return 1; }

# Host thermal (rig-quirks/topics/tooling.md).
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

POCOKEYS='(wifi-connection-mode|log-level|allow-external-configuration|enable-audio-sink|auto-connect-last-session|auto-connect-single-usb|auto-start-on-usb|reopen-on-reconnection|use-libusb|bt-address|bt-announce|head-unit-make|head-unit-model)[^/]*'
# pocoput <base.xml> <spec...> : D-POCO (not rooted). App stopped. The readback retries (round 2 fix).
pocoput() { local i o; python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  adb -s "$HU" exec-in "run-as $PKG sh -c 'cat > shared_prefs/settings.xml'" < new.xml
  for i in 1 2 3 4 5; do o=$(adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE "$POCOKEYS")
    [ -n "$o" ] && { echo "$o"; return 0; }; sleep 1; done; return 1; }
# hu_put <base.xml> <spec...> : D-HU. adb shell is root there, so no su. App stopped.
hu_put() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  OWN=$(adb -s "$HU" shell "stat -c %u:%g /data/data/$PKG" | tr -d '\r')
  printf 'cp /data/local/tmp/new.xml /data/data/%s/shared_prefs/settings.xml\nchown %s /data/data/%s/shared_prefs/settings.xml\nchmod 660 /data/data/%s/shared_prefs/settings.xml\n' "$PKG" "$OWN" "$PKG" "$PKG" > hu_put.sh
  adb -s "$HU" push new.xml /data/local/tmp/new.xml >/dev/null; adb -s "$HU" push hu_put.sh /data/local/tmp/hu_put.sh >/dev/null
  adb -s "$HU" shell "sh /data/local/tmp/hu_put.sh"
  adb -s "$HU" shell "cat /data/data/$PKG/shared_prefs/settings.xml" | grep -aoE '(wifi-connection-mode|log-level|allow-external-configuration|enable-audio-sink|native-poke-all-paired|native-poke-bt-macs|last-connected-native-mac|native-driver-selection-mode|bt-address|bt-announce|head-unit-make|head-unit-model|native-aa-wake-damage-verdict)[^/]*'; }

# ghsnap : which pid is which process on D-MOTO, for the setter attribution in psum
ghsnap() { adb -s "$PH" shell ps -A | grep -aE 'gearhead|com\.android\.settings' | awk '{print $2, $NF}' | tr -d '\r' >> "$OUT/$RUN.pids"; }
# pend <run> : the last stored A2DP policy for $ADDR in the run window so far
pend() { win "$PCAP" "$1" | grep -aF '/A2dpService(' | grep -aF "Saved connectionPolicy $ADDR = " | tail -1 | awk '{print $NF}' | tr -d '\r'; }
# phstate : D-MOTO's focus, Gearhead processes and active Bluetooth devices, for the Stage N diagnosis
phstate() { { echo "== $(date +%T) ${RUN:-} $1"; adb -s "$PH" shell dumpsys window | grep -a mCurrentFocus
  adb -s "$PH" shell ps -A | grep -a gearhead; adb -s "$PH" shell dumpsys bluetooth_manager | grep -a -A1 mActiveDevice; } | tr -d '\r' >> "$OUT/phstate.log"; }

void_() { mark "$RUN-end"; cap_stop; th_stop; echo -e "$RUN\tVOID $1" | tee -a "$OUT/summary.tsv"; }

# USB bring-up: ask for USB as the user, wait up to 120 s for SSL, cue H2 and H3 when needed.
# It searches the whole run capture, so a session that formed before ACTION_CHECK_USB counts (round 2 fix).
dlg() { adb -s "$HU" shell dumpsys activity activities | grep -a -m1 -E 'topResumedActivity|mResumedActivity' | grep -aoE 'systemui[./][A-Za-z.]*Usb[A-Za-z]*Activity'; }
usb_up() { local end d foc=; end=$((SECONDS+120)); send ACTION_CHECK_USB >/dev/null
  while [ $SECONDS -lt $end ]; do
    grep -aqF 'SSL handshake complete' "$CAP" && return 0
    d=$(dlg); [ -n "$d" ] && { echo "$(date +%T) $RUN $d" >> "$OUT/dialogs.log"; cue "H2: allow the USB dialog on D-POCO ($d); do not tick Always"; sleep 8; }
    if [ -z "$foc" ] && [ $SECONDS -ge $((end-60)) ]; then foc=1
      adb -s "$PH" shell dumpsys window | grep -a mCurrentFocus | tee -a "$OUT/dialogs.log"
      cue "H3: clear any Android Auto or system screen on D-MOTO and say what it said"; fi
    sleep 2; done; return 1; }

# cue_pull <run> <from-line> : cue the pull every 30 s until the unit logs the disconnect, at most 600 s
cue_pull() { local t0=$SECONDS
  while [ $((SECONDS-t0)) -lt 600 ]; do
    cue "H1: PULL D-MOTO's cable out of D-POCO. Leave it OUT until the plug-back cue (about 70 s)"
    waitfor 30 'AapService: session state disconnected' "$2" && return 0; done; return 1; }

# body <run> <clean|pull|ghkill> <h6 0|1> : from SSL to the end of a run, both stages
body() { local r=$1 LP PL; sleep 3; ghsnap; sleep 30
  mark "$r-stop"; LP=$(nl)
  case $2 in
    clean)  send ACTION_DISCONNECT >/dev/null ;;
    pull)   if cue_pull "$r" "$LP"; then mark "$r-gone"
            else echo -e "$r\tno-disconnect-line-in-600s" | tee -a "$OUT/summary.tsv"; fi ;;
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

# urun <run> <clean|pull> : one Stage U run, default identity, real address, with the bring-up recovery
urun() { RUN=$1; th_gate || return 3; apk_check || { echo -e "$RUN\tAPK_MISMATCH" | tee -a "$OUT/summary.tsv"; return 1; }
  adb -s "$HU" shell am force-stop $PKG
  pocoput "$BASE" $KEYS > "$OUT/$RUN.keys" || { echo -e "$RUN\tpocoput-failed" | tee -a "$OUT/summary.tsv"; return 1; }
  cap_start "$RUN"; mark "$RUN-start"; ghsnap
  adb -s "$HU" shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; sleep 3
  setj '"bt-announce":"real","head-unit-make":"Google","head-unit-model":"Desktop Head Unit"' >> "$OUT/$RUN.keys"; getk >> "$OUT/$RUN.keys"
  if ! usb_up; then cue "H1: re-enumerate D-MOTO: unplug it from D-POCO, wait 5 s, plug it back"; sleep 10
    if ! usb_up; then
      if grep -aqE 'Version request send failed|Unable to parse TLS packet header' "$CAP"; then
        echo -e "$RUN\tgh_force_stop_before_session" | tee -a "$OUT/summary.tsv"
        adb -s "$PH" shell am force-stop $GH; sleep 3
        cue "H1: re-enumerate D-MOTO: unplug it from D-POCO, wait 5 s, plug it back"; sleep 10
        usb_up || { void_ "no session after the Gearhead force-stop"; return 2; }
      else void_ "no session"; return 2; fi
    fi
  fi
  body "$RUN" "$2" 0; }

# nrun <run> <clean|ghkill> <h6 0|1> : one Stage N run, D-HU's own identity, real address
nrun() { RUN=$1; th_gate || return 3; apk_check || { echo -e "$RUN\tAPK_MISMATCH" | tee -a "$OUT/summary.tsv"; return 1; }
  phstate before; adb -s "$PH" shell input keyevent KEYCODE_HOME
  adb -s "$PH" shell cmd connectivity airplane-mode enable; sleep 8
  adb -s "$HU" shell am force-stop $PKG
  hu_put "$BASE" $KEYS > "$OUT/$RUN.keys" || { echo -e "$RUN\thu_put-failed" | tee -a "$OUT/summary.tsv"; return 1; }
  cap_start "$RUN"; mark "$RUN-start"; getk >> "$OUT/$RUN.keys"
  local L; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null; sleep 15
  adb -s "$PH" shell cmd connectivity airplane-mode disable; sleep 2
  adb -s "$PH" shell svc wifi enable; adb -s "$PH" shell svc bluetooth enable; sleep 3; ghsnap
  if ! waitfor 120 'SSL handshake complete' "$L"; then phstate void; void_ "no session"; usum "$RUN"; return 2; fi
  body "$RUN" "$2" "$3"; }

# usum <run> : the head unit counts, and the phone crash and disabler counts
usum() { local c=$CAP r=$1
  { echo "== $r"
    echo "hu.verbs=$(wc_ $c $r 'AutomationReceiver: ')"
    echo "hu.announce=$(win $c $r | grep -aoE 'Bluetooth service announced with carAddress=[^ ]+ \(bt-announce=[a-z]+\)' | sort | uniq -c | tr '\n' ' ')"
    echo "hu.announce_empty=$(wc_ $c $r 'BT MAC Address is empty, so no Bluetooth service is announced')"
    echo "hu.ssl=$(wc_ $c $r 'SSL handshake complete')"
    echo "hu.ssl_after_stop=$(win $c $r | awk -v m="AutomationMarker: $r-stop" 'index($0, m) { f = 1; next } f' | grep -acF 'SSL handshake complete')"
    echo "hu.live_at_stop=$(win $c $r | LC_ALL=C awk -v m="AutomationMarker: $r-stop" '
      index($0, "SSL handshake complete") { s = 1; d = 0 } index($0, "AapService: session state disconnected") { d = 1 }
      index($0, m) { print (s && !d) ? 1 : 0; x = 1; exit } END { if (!x) print 0 }')"
    echo "hu.session_before_check_usb=$(win $c $r | LC_ALL=C awk '
      index($0, "SSL handshake complete") && !k { print "yes"; x = 1; exit } index($0, "ACTION_CHECK_USB") { k = 1 } END { if (!x) print "no" }')"
    echo "hu.disconnected:"; win $c $r | grep -aF 'AapService: session state disconnected' | head -3
    echo "hu.acc_mode=$(wc_ $c $r 'Found device already in accessory mode') hu.vfail=$(wc_ $c $r 'Version request send failed') hu.tls_parse=$(wc_ $c $r 'Unable to parse TLS packet header')"
    echo "hu.usb_perm=$(wc_ $c $r 'Requesting USB permission for')"
    echo "hu.match=$(wc_ $c $r 'MATCH! Starting AapService') hu.groups=$(wc_ $c $r 'createGroup SUCCESS')"
    echo "hu.listen=$(wc_ $c $r 'ACTIVELY LISTENING on Android Auto UUID') hu.poke=$(wc_ $c $r 'Attempting active poke to device') hu.poke_ok=$(wc_ $c $r 'Successfully poked') hu.accept=$(wc_ $c $r 'Connection accepted from')"
    echo "hu.fatal=$(wc_ $c $r 'FATAL EXCEPTION')"
    echo "ph.disabler=$(wc_ $PCAP $r 'disabling A2dp via profile disabler')"
    echo "ph.gh_lines=$(wc_ $PCAP $r '/GH.')"
    echo "ph.fatal_processes:"; win $PCAP $r | grep -a -A1 'FATAL EXCEPTION' | grep -aF 'Process: ' | head -3
    echo "ph.usb_after_stop:"; win $PCAP $r | awk -v m="$r-stop" 'index($0, "RIGMARK") && index($0, ": " m) { f = 1; next } f' | grep -ai 'usb' | head -5
    echo "ph.restarts=$(cat "$PCAP.restarts" 2>/dev/null | wc -l)"
  } | tee -a "$OUT/summary.tsv"; }

# psum <run> : the A2DP policy for $ADDR in the run window, from the phone capture (unchanged from round 2)
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

App lines were checked with `grep -rF` against `app/src/main` on `fff96699`. All print at INFO or above. The phone lines are Android's Bluetooth stack lines and `ActivityManager` lines, checked against D-MOTO's captures in the round 1 and round 2 assets. Every count is taken inside the run's window, from `<run>-start` to `<run>-end` (`AutomationMarker:` in the unit capture `$OUT/<run>.hu.logcat`, `RIGMARK` in the phone capture `$OUT/<run>.phone.logcat`, function `win`).

| Line (fixed substring) | File | Meaning |
|---|---|---|
| `Bluetooth service announced with carAddress=` | unit | the address on the wire, then ` (bt-announce=real)` |
| `BT MAC Address is empty, so no Bluetooth service is announced` | unit | an empty `bt-address`: the run is void |
| `SSL handshake complete` | unit | a session formed (never prefix `Handshake:`) |
| `AapService: session state disconnected` | unit | the session ended; the reason follows in brackets (composed from `AapService: session state `) |
| `AutomationReceiver: ` | unit | a verb landed; a missing one voids the step |
| `AutomationMarker: ` | unit | a run marker |
| `ACTION_CHECK_USB` | unit | inside the `AutomationReceiver: ` line of the USB verb |
| `Found device already in accessory mode`, `Version request send failed`, `Requesting USB permission for` | unit | the USB bring-up, and the re-attach failure |
| `Unable to parse TLS packet header` | unit | the second re-attach failure (an `SSLException` message, not this app's string) |
| `MATCH! Starting AapService`, `createGroup SUCCESS` | unit | Stage N discard rules |
| `ACTIVELY LISTENING on Android Auto UUID`, `Attempting active poke to device`, `Successfully poked`, `Connection accepted from` | unit | Stage N bring-up: listeners open, poke sent, poke held, phone came back |
| `FATAL EXCEPTION` | both | a crash; on the phone, the next line's `Process: ` names whose |
| `RIGMARK` | phone | the run markers |
| `getProfileConnectionPolicy: XX:XX:XX:XX:` | phone | a read of the stored policy; the first one for `$SUF` in a window is **P0** |
| `setConnectionPolicy(` | phone | a request to change it, under tag `BluetoothA2dp(<pid of the asker>)` |
| `Saved connectionPolicy ` | phone | the stored value, under tag `A2dpService(...)` |
| `Start proc ` | phone | maps a pid to a process name |
| `disabling A2dp via profile disabler` | phone | Android Auto's disabler ran (cross-check only) |
| `/GH.` | phone | any Android Auto log line; informational, for Stage N only |

**Reading a run (`psum`).** Phases: 0 is before the `<run>-stop` marker, 1 is from `<run>-stop` to `<run>-read` (60 s after the end), 2 is after `<run>-read` (H6 only).

- **P0**: the value in the first `getProfileConnectionPolicy` line for `$SUF`.
- **Disabled**: `p.disabled_by_gh_before_stop` of 1 or more, that is a `setConnectionPolicy(<ADDR>, 0)` in phase 0 from a process whose name starts `com.google.android.projection.gearhead`.
- **RESTORED**: the first `setConnectionPolicy(<ADDR>, 100)` in phase 1 is from a Gearhead process, and the last `Saved connectionPolicy <ADDR> = ` value up to `<run>-read` is `100`.
- **NOT_RESTORED**: no `setConnectionPolicy(<ADDR>, 100)` in phase 1, and the last stored value up to `<run>-read` is `0`.
- **OTHER**: anything else. Quote every `p.set` line. Report `p.non_gh_setters_before_read` whenever it is above 0: a setter that is not Gearhead is the signal that something else changed the policy.

## 8. Runs

### Prepare (P), on cables, before Stage U

1. **Build and identity** (section 1). Record `WANT_MD5`, both `commit` replies, and whether you built or installed.
2. **Back up both settings files** with each app stopped: D-POCO with `adb -s 4f4027e9 shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings_backup_poco.xml`; D-HU with `adb -s 27870808938846 shell cat /data/data/$PKG/shared_prefs/settings.xml > $OUT/settings_backup_dhu.xml`. Diff each against round 2's closing backup if you still have it, and state the delta (even if zero). Record the rig audio keys from D-HU's backup.
3. **`stat` both `shared_prefs/` directories** and record owner, group and mode.
4. **Addresses.** `adb -s <serial> shell settings get secure bluetooth_address`: `POCO_BT` on `4f4027e9` (round 2: `DC:B7:2E:5E:4E:59`), `MOTO_BT` on `ZY22GC3BM4` (round 2: `A0:46:5A:97:E4:95`). Read `DHU_ANN` as the `bt-address` value in D-HU's backup (round 2: `11:46:03:10:33:59`). If a value differs from round 2, use the new one and say so.
5. **Bonds.** `adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -i -A30 "Bonded devices"` must list D-POCO and D-HU. If D-POCO is missing, H5, then check again. Still missing: Stage U is UNTESTABLE.
6. **D-MOTO.** Run `rig_preflight.sh`; Bluetooth must be on. Then:
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
8. **H7.** Ask the operator to open the cue terminal (`tail -n0 -F $OUT/hand-steps.log`, with `$OUT` written out as a full path). Do not start Stage U until the operator confirms it is open.

### Stage U: D-POCO as head unit over USB, D-MOTO as the phone

**Switch to wireless adb**, as in round 2:

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
source ./lib1008r3.sh
```

**Valid pull run.** A pull run counts only when all of these hold. Read each from `$OUT/summary.tsv` and `$OUT/policy.tsv`:

1. `hu.verbs` of 8 or more (five markers `-start`, `-stop`, `-gone`, `-read`, `-end`, plus `ACTION_SET_SETTINGS`, `ACTION_GET_SETTINGS`, `ACTION_CHECK_USB`);
2. `hu.ssl` = 1 and `hu.ssl_after_stop` = 0;
3. `hu.live_at_stop` = 1;
4. `hu.announce` is exactly one line, `carAddress=$POCO_BT (bt-announce=real)`, and `hu.announce_empty` = 0;
5. `getk` in `$OUT/$RUN.keys` shows `Google` and `Desktop Head Unit`;
6. the phone window carries the `-start`, `-stop`, `-gone`, `-read` and `-end` markers (`p.stop`, `p.gone` and `p.read` are not empty);
7. `p.P0` = `100`, and `p.disabled_by_gh_before_stop` is 1 or more;
8. `hu.fatal` = 0, and `ph.fatal_processes` names no `com.google.android.projection.gearhead` process.

A run that misses an item is not counted. Name the item in the results. Do not re-run it under the same id: go on to the next id.

**Rule R (recovery).** After any Stage U run whose `p.END_at_read` is not `100`, run a recovery run before the next pull: `urun U6r<n> clean` (n = 1, 2). A recovery run is valid on items 1 (7 or more verbs: no `-gone` marker), 2, 4, 5 and 8, and the phone markers `-start`, `-stop`, `-read`, `-end`. If a valid recovery run ends with `p.END_at_read` not `100`, stop Stage U: D-MOTO's policy for D-POCO is then `0` and the rig has no other lever. Grade the runs not reached UNTESTABLE ("D-POCO's policy left at 0 with no lever to reset it") and go to the close of Stage U.

**Stage U gate.** If the first run that reaches SSL has `ph.disabler` of 1 or more but no `p.set` line, the phone no longer prints the instrument lines. Stage U is then UNTESTABLE.

#### U5a. Cable pulled out mid-session (the point of the round)

Before each run, tell the operator the pull sequence (section 4). Run the ids in order, and stop as soon as **three** runs count:

```bash
urun U5a4 pull     # then U5a5, U5a6, U5a7, U5a8, U5a9 until three count
```

- **PASS**: at least **two** runs count, and every counted run reads `p.reading=RESTORED`. With round 2's U5a2, that is three or more valid pulls with the same reading.
- **FAIL**: a counted run reads NOT_RESTORED or OTHER. Report each counted reading with its `p.set` lines.
- **INCONCLUSIVE**: fewer than two runs count after U5a9. Name the missed item for each run.
- **Report** per counted run: P0, `p.reading`, `delay_from_gone_ms`, `delay_from_stop_ms`, the `session state disconnected` line with its reason, and the `ph.usb_after_stop` lines (informational).
- **The restore delay is a second question, and it does not change the verdict.** Round 2 saw two restores within 100 ms of the `-gone` marker and two at about 8.5 s. Put the `delay_from_gone_ms` of every counted run in one list, and say if the values fall into two groups.
- **What a reading means for the reply.** RESTORED: a pulled cable gives the toggle back, as a clean end does. NOT_RESTORED: a pulled cable leaves it off, which is the reporter's symptom.

**Closing Stage U.**

1. `send ACTION_EXIT`.
2. `adb -s $HU shell am start -a android.intent.action.VIEW -d headunit://exit`, then `sleep 3`, then `adb -s $HU shell am force-stop $PKG`.
3. Restore D-POCO's backup with `pocoput $BASE` (no spec) and read it back.
4. `adb -s $HU shell dumpsys usb > $OUT/dumpsys-usb-after.txt`.
5. H1: unplug D-MOTO from D-POCO and put D-MOTO and D-POCO back on PC cables.
6. `adb -s $POCO_IP:5555 usb` and `adb -s $MOTO_IP:5555 usb`.
7. D-POCO stays out of Stage N (`rig-quirks/topics/bt.md`: it is bonded to D-HU and takes the poke from D-MOTO): `adb -s 4f4027e9 shell svc bluetooth disable` and `adb -s 4f4027e9 shell cmd connectivity airplane-mode enable`. Confirm both with `dumpsys`.
8. Record the last `p.END_at_end` of Stage U: it is D-MOTO's policy for D-POCO as the rig is left.
9. Record the count of `gh_force_stop_before_session` lines in `summary.tsv`. Stage N reads it.

### Stage N: D-HU as head unit over Native AA, D-MOTO as the phone

This stage is second in priority. Do it only after Stage U is closed.

```bash
STAGE=N; HU=27870808938846; PH=ZY22GC3BM4; BASE=$OUT/settings_backup_dhu.xml
ADDR=$DHU_ANN; SUF=${DHU_ANN: -5}
KEYS="int:wifi-connection-mode=3 int:log-level=2 int:onboarding-version=2 int:native-driver-selection-mode=0 bool:allow-external-configuration=true bool:enable-audio-sink=true bool:native-poke-all-paired=false set:native-poke-bt-macs=$MOTO_BT str:last-connected-native-mac=$MOTO_BT str:native-preferred-device-mac=$MOTO_BT del:bt-announce del:native-aa-wake-damage-verdict del:video-profile-starvation-cap"
source ./lib1008r3.sh
```

**Valid run (Stage N)**: items 2, 4 (with `carAddress=$DHU_ANN (bt-announce=real)`, and `getk` shows no `bt-announce`), 8, and the phone markers `-start`, `-stop`, `-read`, `-end`, plus:

- `hu.verbs` of 7 or more (four markers, `ACTION_GET_SETTINGS`, `ACTION_START_WIRELESS_SCAN`, and `ACTION_DISCONNECT` or `ACTION_EXIT`);
- `p.disabled_by_gh_before_stop` of 1 or more;
- `hu.match` = 0 and `hu.groups` = 1 (template §4). Otherwise re-run once under the id with `x` added.

`nrun` cycles D-MOTO's airplane mode, so D-MOTO's Bluetooth starts again in every run and reads every bonded device's policy. That read is P0.

#### N0p. Gate and primer: clean end, H6 if needed

```bash
nrun N0p clean 1
```

**If N0p ends VOID**, do this recovery once, then run N0px:

```bash
adb -s ZY22GC3BM4 shell input keyevent KEYCODE_HOME
adb -s 27870808938846 shell svc bluetooth disable; sleep 5
adb -s 27870808938846 shell svc bluetooth enable; sleep 15
adb -s 27870808938846 shell dumpsys bluetooth_manager | grep -a -m1 -E "enabled: true|state: ON"
nrun N0px clean 1
```

- **PASS**: valid, `p.P0` = `100`, `p.reading=RESTORED`.
- **FAIL**: valid, `p.P0` = `100`, and the reading is not RESTORED. Stop Stage N.
- If `p.P0` is `0`, the run still counts as the primer: H6 ran inside it. Grade it INCONCLUSIVE and say so.
- **UNTESTABLE for the whole stage**: N0p and N0px both VOID. Then report, for each of the two: `hu.listen`, `hu.poke`, `hu.poke_ok`, `hu.accept`, `ph.gh_lines`, and the `$OUT/phstate.log` blocks. Say if Stage U ran any `gh_force_stop_before_session`. Do not name a cause.

#### N4. Android Auto force-stopped, then a clean recovery session, twice

```bash
nrun N4a1 ghkill 0
nrun N4b1 clean  1
nrun N4a2 ghkill 0
nrun N4b2 clean  1
```

There is no H6 between N4a and N4b. That is the point: N4b starts from what N4a left.

- **N4a, per repetition**: counts when valid and `p.P0` = `100`. **PASS**: two counted repetitions read NOT_RESTORED (round 1 N3 and round 2 U5b). **FAIL**: the two readings differ, or either reads RESTORED.
- **N4b, per repetition**: **PASS**: valid, `p.P0` = `0`, and `p.reading` is RESTORED or NOT_RESTORED, with the same reading in N4b1 and N4b2. **FAIL**: the two readings differ. **INCONCLUSIVE**: `p.P0` is not `0`; quote the `p.set` lines of the N4a window and this one. If N4b ends below `100`, the H6 inside it puts the toggle back before N4a2; report the operator's words ("Media audio was off" or "on") beside `p.END_at_read`.

**Closing Stage N, and the round.**

1. `send ACTION_EXIT`, then `adb -s $HU shell am force-stop $PKG`.
2. Restore D-HU's backup with `hu_put $BASE` (no spec). Read back `head-unit-make` and `head-unit-model`; they must match the backup.
3. D-MOTO's policy for D-HU must be `100`: the last `Saved connectionPolicy $DHU_ANN = ` line of the last Stage N run says so. If it is not `100`, or a Stage N run ended after a force-stop with no N4b after it, run H6 once more with a capture open:
   ```bash
   RUN=END; cap_start END; mark END-start; cue "H6: turn Media audio on for Navegadortz2 on D-MOTO"
   pwaitfor 180 "Saved connectionPolicy $DHU_ANN = 100" 1; mark END-end; cap_stop
   ```
   Never leave the rig with that toggle off. If Stage N never formed a session, no run touched it; say so and skip this step.
4. On D-POCO, `svc bluetooth enable` and `cmd connectivity airplane-mode disable`, and confirm with `dumpsys`.
5. On D-MOTO, `svc power stayon false`.
6. Put the `p.reading`, `p.P0` and `p.restore` lines of every run in the results file. The full `policy.tsv` goes in the asset.

**Stop rule.** Stage U runs at most 6 pull runs (U5a4 to U5a9) and 2 recovery runs (U6r1, U6r2). It stops after the third counted pull. Stage N runs at most 7 runs: N0p, N0px, N4a1, N4b1, N4a2, N4b2, and one `x` re-run. If the host passes 30 min above 75C, stop and mark the remaining runs UNTESTABLE (host thermal).

## 9. Do not re-run

These are settled. Delays are on D-MOTO's clock, from the `<run>-stop` marker.

| Run | P0 | After the end | Delay |
|---|---|---|---|
| Round 1 U1a, U1b, U1c, U4 (clean, USB) | `-1`, `100` | Gearhead set `100` | 1811 to 1853 ms |
| Round 1 U2 (`blank`), U3 (`skip`) | none | no `setConnectionPolicy(` for the address | |
| Round 1 N1, N2a, N2b (clean, Native AA) | `100` | Gearhead set `100` | 1361 to 1389 ms |
| Round 1 N3a, N3b (Android Auto force-stop, Native AA) | `100` | no Gearhead set | |
| Round 2 U0p (clean, USB) | `100` | Gearhead set `100` | 1773 ms |
| Round 2 U5a2 (cable pull, USB) | `100` | Gearhead set `100` | `delay_from_gone_ms` -88 |
| Round 2 U5b1, U5b2 (Android Auto force-stop, USB) | `100` | NOT_RESTORED, stored `0` | |
| Round 2 U6a, U6b, U6r2v (clean, USB, from `0`) | `0` | Gearhead set `100` | 1824, 1864, 1841 ms |

- **A clean end restores on both transports.** Do not re-run U0p, U1, U4, N1 or N2.
- **`blank` and `skip` stop the disable.** Do not re-run U2 or U3.
- **An Android Auto force-stop leaves the policy at `0` on both transports.** N4a repeats it only as the set-up for N4b.
- **The next clean USB session repairs a policy left at `0`.** Do not re-run U5b or U6 except as rule R recovery.
- **U5a2 counts** toward the U5a verdict of this round.
- **A restore lasts only until the next session** (round 2, U6r1b). It is not a question for this round.
- **Calls are not re-run.** The call does not touch the A2DP policy.

## 10. Report back

1. **U5a**: the reading of each counted pull, with P0 and `delay_from_gone_ms`, and the count of counted runs against attempts. This is the reporter's end.
2. **N4b**: from P0 = `0`, does the next clean Native AA session put the policy back? Give the reading of each repetition. If Stage N was UNTESTABLE again, give the N0p and N0px diagnostic counts instead.
3. **Any `p.non_gh_setters_before_read` above 0**, with its `p.set` lines quoted.

The decisive strings below are the app's own, one per line, for a mechanical `grep -F -r` against `app/src/main` on `fff96699`. `AapService: session state ` is the fixed part of the composed line that the runs grep as `AapService: session state disconnected`. `ACTION_CHECK_USB` is grepped inside the `AutomationReceiver: ` line, which prints the action name.
