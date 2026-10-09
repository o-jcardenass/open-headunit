# pr-1066-byebye-priority, round 1 brief: does the phone still receive the ByeBye on a socket transport once the 150 ms wait is gone

User exits under load, A/B, on three transports: Headunit Server (D-HP with D-POCO's server), Native AA over WiFi Direct (D-HU with D-POCO), and USB (D-MOTO as host, D-POCO as device). Every run captures the phone beside the head unit. About 3 hours plus builds.

## 1. Build and baseline

Two APKs. Build each with `build_hur_cool.sh` (or `build_hur.sh` under the thermal gate, `--max-workers=2`, template section 7a tooling) and copy it out of `apks/` the moment it is built.

| Arm | Code | Identity | Gate |
|---|---|---|---|
| **B** (baseline) | upstream `main` | commit `145a0c762f0a87386ca63b54518ea5e861b290be` | JVM tests, 0 failures |
| **M** (candidate) | `main` plus the contributor's branch `codex/byebye-send-priority` (`73db22f66fe16f8b58cc68b0025bc77b00f1cef7`, two commits), merged locally | tree `a7c0ca3361f3907eed18f5df11b387e260b7e129` | JVM tests, 0 failures, `FinalMessageDeliveryTest` present |

```bash
git fetch https://github.com/andreknieriem/open-headunit.git main
git fetch https://github.com/emotionbug/open-headunit.git codex/byebye-send-priority
git checkout -B arm-B 145a0c762f0a87386ca63b54518ea5e861b290be
git checkout -B arm-M 145a0c762f0a87386ca63b54518ea5e861b290be
git merge --no-edit 73db22f66fe16f8b58cc68b0025bc77b00f1cef7
git rev-parse 'HEAD^{tree}'        # MUST print a7c0ca3361f3907eed18f5df11b387e260b7e129
```

If the tree hash differs, do not build M and stop the round. A build or test failure stops the round.

**Why a merged arm.** The branch is based on `2ca3b1f1`, eight commits behind `main`. One of them, `a70ba7e2` (a connect to an endpoint already in flight joins it), changes the Headunit Server reconnect this round grades. With the branch tip as the candidate, a reconnect difference could come from that commit and not from the change. M against B differs by the change alone.

**Identity, per arm, on every head unit, before the first run on it.** Pull the installed APK and hash it locally (`apk_check`). Then count the new string in the DEX:

```bash
unzip -p $OUT/live.apk 'classes*.dex' | strings | grep -cF 'AapTransport: ByeBye write'     # B: 0, M: 1 or more
```

Also `send ACTION_QUERY_STATE` and record `commit` (B starts `145a0c76`; M is the local merge commit). A mismatch voids that arm's runs on that unit: reinstall once, then mark them UNTESTABLE.

## 2. What this is and why

When the user exits, `CommManager.doDisconnect` calls `AapTransport.stop()`, which sends a `ByeByeRequest` and then quits the transport. `TeardownGuard` closes the socket after that.

- **On `main`** the ByeBye is queued behind every message already in the send queue, then `stop()` sleeps 150 ms and quits. A ByeBye behind more than 150 ms of queued acks is dropped with no trace.
- **On M** the ByeBye goes to the front of the send queue, `stop()` waits up to 150 ms for that one write, logs the result (`SENT`, `FAILED`, `REJECTED`, `TIMED_OUT`, `INTERRUPTED`), and quits at once. A second commit makes a short encrypted write report itself (`AapTransport: send incomplete (ret=N of M)`).

Our review of the change found no blocking defect and one open question, which is the point of this round. On a socket, `SENT` means the bytes were copied into the kernel, not that they reached the phone. `main` always gave the kernel 150 ms before the close; M closes at once. If inbound video is still unread when the socket closes, Linux sends a reset and drops what is left in the send buffer. Then the phone never gets the ByeBye while our log reads `SENT`. USB is not exposed, because a bulk transfer returns only after it completes. **So the question is: on a socket transport, does the phone receive the ByeBye as often on M as on B?**

**What proves receipt.** No phone-side ByeBye line is known. Gearhead names `PROTOCOL_BYEBYE_REQUESTED_BY_CAR` and `PROJECTION_ENDED_BYEBYE_BY_CAR`, but our decompile is incomplete. So the first run records the first real phone line as the reference (section 6, `REF`). Two more instruments help:

- **Phone `ReaderThread: end of stream received`** means the phone read our FIN. TCP delivers every byte before the FIN, so this line also means the ByeBye arrived. Its absence alone proves nothing, because the phone may close first after it reads the ByeBye.
- **Phone `ECONNRESET`, `Connection reset` or `Broken pipe` with no `REF` line** is the loss signature.
- **Head unit `Byebye Response received`** is the phone's answer. It proves receipt, but it is biased against M: B keeps reading for 150 ms after the send, and M stops reading sooner. Count it and report it; never grade M on it.

**The wedge check.** Android Auto's head unit server stops answering everyone after one peer vanishes without a FIN, and only the phone clears it. So every Headunit Server exit is followed by a reconnect that must work with no restart of the phone's server.

## 3. What is different about this round

- **Three stages, five devices, four ports.** Stage H: D-HP and D-POCO, both on USB adb. Stage W: D-HU and D-POCO. Stage U: D-MOTO and D-POCO, both on wireless adb (`adb tcpip 5555`), so D-MOTO's port is free for the OTG cable. Setup notes say what was plugged in.
- **D-HU cannot host USB** (`host_connected=false`), so stage U uses D-MOTO as the head unit, as pr-1045-framing round 1 stage D did. Read template section 7b first. Do not tick "always" on any USB dialog.
- **No congested-link variant.** The rig has no verb and no documented hand step that congests a link. Load here is music plus live video, which keeps inbound data arriving at the moment of the close.
- **Hand steps, with reasons, and no others.** H1: start D-POCO's head unit server (`srv_restart`, a UI-only toggle in Android Auto's developer settings), only at the start of stage H or after a wedge. H2: unplug and replug D-POCO's OTG cable on D-MOTO, and accept the system USB permission dialog, in stage U (a cable and a system dialog have no verb). **Who reads the cue:** the operator, through `OPERATOR_STEP` lines from `cue`. A cue with no matching line in 120 s voids that cycle, not the run; a second miss ends stage U as INCONCLUSIVE.
- **The user exit is `send ACTION_DISCONNECT`** (the projection's Exit, the user-exit path). It logs `Disconnect action received.` and keeps the service.
- **Pre-registered INCONCLUSIVE:** stage H if D-POCO's server cannot be started (H1 times out); stage U if no operator is present (UNTESTABLE) or if fewer than 2 USB sessions form on B; any exit whose load gate fails (section 6).

## 4. Setup

Make `hur-wifi-test-scripts/pr-1066-byebye-priority-round1/`. Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into it from `hur-wifi-test-scripts/projection-teardown-and-relays-round3/`. List them in Setup notes. Save `rr_lib.sh` beside them:

```bash
# rr_lib.sh : source after ohu_lib.sh and ptr_lib.sh. Needs HU, PH, OUT, PUT, BASEXML, KEYS, CAP, PCAP.
pmark() { adb -s "$PH" shell log -t OHURIG "$1"; }
both()  { mark "$1"; pmark "$1"; }
hseg() { awk -v a="$1" -v b="$2" '/AutomationMarker:/&&$NF==a{on=1} on{print} on&&/AutomationMarker:/&&$NF==b{exit}' "$CAP"; }
pseg() { awk -v a="$1" -v b="$2" '/OHURIG/&&$NF==a{on=1} on{print} on&&/OHURIG/&&$NF==b{exit}' "$PCAP"; }
c() { grep -acF -- "$1"; }
cue() { echo "$(date +%T) OPERATOR_STEP $1"; printf '\a'; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
BYERE='byebye|bye_bye|BYEBYE_BY_CAR|REQUESTED_BY_CAR'
RSTRE='ECONNRESET|Connection reset|Broken pipe'
# ms between the first line holding $2 and the first later line holding $3, in a -v time segment on stdin
gapms() { awk -v A="$1" -v B="$2" 'function ms(s,t){split(s,t,/[:.]/);return ((t[1]*60+t[2])*60+t[3])*1000+t[4]} index($0,A)&&!a{a=ms($2)} a&&!b&&index($0,B){b=ms($2)} END{if(a!=""&&b!="")print b-a; else print "NA"}'; }
# sess_open RUN : captures on, settings in, launch; the stage's own bring-up follows
sess_open() { RUN=$1; CAP=$OUT/$RUN.logcat; apk_check || return 1; adb -s $HU shell am force-stop $PKG
  $PUT "$BASEXML" $KEYS; for t in CAR.GAL CAR.GAL.GAL.LITE CAR.GAL.SECURITY.LITE; do adb -s $PH shell setprop log.tag.$t VERBOSE; done
  pcap_start "$RUN"; cap_start; sleep 1; both $RUN-start; }
# loaded : music on the phone and video moving, or LOAD_FAIL
loaded() { local L; L=$(nl); adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY; sleep 15
  adb -s $PH shell dumpsys media_session | grep -c 'state=PLAYING' > $OUT/$1.playing
  waitfor 15 'Throughput over [0-9]+ms: rendered=[1-9]' $L || echo "LOAD_FAIL $1" | tee -a $OUT/$RUN.summary; }
# exit_one ID : the exit under test, then 8 s for the teardown
exit_one() { loaded $1; both $1-go; send ACTION_DISCONNECT > $OUT/$1.reply; sleep 8; both $1-done; }
close_run() { both $1-end; sleep 1; pcap_stop; cap_stop; th_report $1; send ACTION_EXIT >/dev/null; sleep 3; adb -s $HU shell am force-stop $PKG; }
```

For each stage, set the variables, source the three files, and back up the unit's settings once:

```bash
source ./ohu_lib.sh; source ./ptr_lib.sh; source ./rr_lib.sh
prefs_cat > $OUT/settings-backup-$HU.xml; BASEXML=$OUT/settings-backup-$HU.xml
WANT_MD5=<md5 of the arm's APK>      # set before every arm; apk_check compares the installed APK with it
```

Install each arm with `adb -s $HU install -r -d <named apk>`, never with a script that picks the newest APK. D-MOTO may hold an older build of ours from pr-1045-framing round 1, which is why `-d` is there.

On D-HU, `stat` `/data/data/$PKG/shared_prefs` first and record the owner (template section 7a, D-HU). Record the Gearhead `versionName` on D-POCO once (`dumpsys package com.google.android.projection.gearhead | grep -m1 versionName`).

**Settings keys**, written with the app stopped by `$PUT` (`tab_put` on D-HP and D-MOTO, `hu_put` on D-HU), which reads them back. Audio keys are never written (memory `rig-audio-settings-are-a-deliberate-worst-case`).

| Key | Stage H (D-HP) | Stage W (D-HU) | Stage U (D-MOTO) |
|---|---|---|---|
| `wifi-connection-mode` | int `1` | int `3` | int `0` |
| `connection-modes` | set `wifi` | set `wifi` | set `usb` |
| `log-level` | int `2` (INFO) | int `2` | int `2` |
| `onboarding-version` | int `2` | int `2` | int `2` |
| `native-driver-selection-mode` | | int `0` | |
| `use-libusb` | | | bool `false` |
| `video-profile-starvation-cap` | delete | delete | delete |

```bash
KEYS_H="int:wifi-connection-mode=1 set:connection-modes=wifi int:log-level=2 int:onboarding-version=2 del:video-profile-starvation-cap"
KEYS_W="int:wifi-connection-mode=3 set:connection-modes=wifi int:log-level=2 int:onboarding-version=2 int:native-driver-selection-mode=0 del:video-profile-starvation-cap"
KEYS_U="int:wifi-connection-mode=0 set:connection-modes=usb int:log-level=2 int:onboarding-version=2 bool:use-libusb=false del:video-profile-starvation-cap"
```

**Verbs.**

| Action | Command |
|---|---|
| user exit | `send ACTION_DISCONNECT` |
| reconnect, stage H | `send ACTION_CONNECT --es ip $PHIP` (TCP to `:5277`) |
| reconnect, stage W | `send ACTION_START_WIRELESS_SCAN` (falls back to `adb -s $HU shell am start -n $MAIN` if the reply holds `not allowed`) |
| reconnect, stage U | H2, then `send ACTION_CHECK_USB` |
| state | `send ACTION_QUERY_STATE` |
| end of run | `close_run <RUN>` |

Every verb must print `AutomationReceiver: <full action>` in the capture. A verb with no such line never landed: that cycle is void, not a FAIL.

## 5. The deciding lines

Head unit lines checked with `grep -F` against the B and M trees. All INFO except where marked.

| Line | Source | B | M | Per exit, in `hseg <ID>-go <ID>-done` |
|---|---|---|---|---|
| `Disconnect action received.` | `AapService.kt` | present | present | `disc`, must be 1 |
| `AapTransport stopping and sending byebye (` (full: `(USER_SELECTION)`) | `AapTransport.kt` | present | present | `stop` |
| `AapTransport: ByeBye write ` plus the result word | `AapTransport.kt` | absent | present | `write`, the word |
| `AapTransport quitting (clean=` | `AapTransport.kt` | present | present | `quit` |
| `Failed to join threads` (ERROR) | `AapTransport.kt` | present | present | `join_fail` |
| `AapTransport: send incomplete (ret=` (WARN) | `AapTransport.kt` | absent | present | `incomplete` |
| `AapTransport: send failed (ret=` (WARN) | `AapTransport.kt` | present | absent | `failed_old` |
| `Byebye Response received` | `AapControl.kt` | present | present | `resp`, report only |
| `CommManager: doDisconnect ` then a phase and ` failed: ` (ERROR, a teardown phase threw; regex `CommManager: doDisconnect .* failed: `, because the bare prefix is also a decoder stop reason) | `CommManager.kt` | present | present | `teardown_err` |
| `SSL handshake complete` | `AapSslContext.kt` | present | present | reconnect window |
| `Throughput over ` (regex `Throughput over [0-9]+ms: rendered=[1-9]`) | `VideoDecoder.kt` | present | present | load gate |
| `Media Start Request ` (composed: `Media Start Request AUDIO`) | `AapControl.kt` | present | present | load, report |
| `NetworkDiscovery: Found Headunit Server on ` | `NetworkDiscovery.kt` | present | present | stage H bring-up |
| `Found device already in accessory mode`, `Switching USB device to accessory mode` | USB launcher | present | present | stage U plug |
| `AapService: Native AA user exit. Stopping active launcher.` | `AapService.kt` | present | present | stage W exit |
| `WifiLauncher: Initializing WiFi Mode: ` (`AUTO`, `NATIVE`) | `WifiLauncherManager.kt` | present | present | arm check |
| `User exit cooldown active` | `AapService.kt` | present | present | report |

Phone lines, in `pseg <ID>-go <ID>-done` on D-POCO's capture (Gearhead; not in our tree):

| Line | Per exit |
|---|---|
| `REF` (section 6), or any line matching `$BYERE` (case-insensitive) | `ref`, `bye` |
| `ReaderThread: end of stream received` | `eos` |
| any line matching `$RSTRE` | `rst` |
| `Critical error` | `crit` |
| `Head unit disconnected` | report |
| `Head unit connected`, `Network server running on port` | stage H reconnect window |

The per-exit extract, run after `close_run` on that run's `CAP` and `PCAP`:

```bash
xt() { local S P; S=$(hseg $1-go $1-done); P=$(pseg $1-go $1-done)
  echo "$1 disc=$(echo "$S"|c 'Disconnect action received.') stop=$(echo "$S"|c 'AapTransport stopping and sending byebye (USER_SELECTION)') write=$(echo "$S"|grep -aoE 'ByeBye write [A-Z_]+'|cut -d' ' -f3|tr '\n' ,) quit=$(echo "$S"|c 'AapTransport quitting (clean=') stop_to_quit_ms=$(echo "$S"|gapms 'AapTransport stopping and sending byebye' 'AapTransport quitting (clean=') join_fail=$(echo "$S"|c 'Failed to join threads') incomplete=$(echo "$S"|c 'AapTransport: send incomplete (ret=') failed_old=$(echo "$S"|c 'AapTransport: send failed (ret=') teardown_err=$(echo "$S"|grep -acE 'CommManager: doDisconnect .* failed: ') resp=$(echo "$S"|c 'Byebye Response received') ref=$( [ -n "$REF" ] && echo "$P"|c "$REF" || echo NA) bye=$(echo "$P"|grep -aciE "$BYERE") eos=$(echo "$P"|c 'ReaderThread: end of stream received') rst=$(echo "$P"|grep -aciE "$RSTRE") crit=$(echo "$P"|c 'Critical error') playing=$(cat $OUT/$1.playing)"; }
```

## 6. Runs

Order: stage H (B, then M), stage W (B, then M), stage U (B, then M). Run ids carry the arm. **The point of the round is H-M**, against H-B: it is a socket transport, and it carries the wedge check. W is the second socket transport. U is the control where no loss is expected.

### REF, from the first exit of H-B

After H-B's first exit, before anything else, run this and record the output in Setup notes:

```bash
pseg H-B-x1-go H-B-x1-done | grep -aiE "$BYERE"
```

Set `REF` to the first 40 characters of the first matching line's message (the text after the tag and `: `). If nothing matches, read the same window of H-B-x2 to H-B-x5 and take the first match. If no H-B exit has a match, set `REF=` (empty): the phone does not log ByeBye receipt at these log levels, the `ref` column reads `NA`, and receipt is graded on `eos` and `rst` only. Quote the matching lines with timestamps whatever happens; they are the reference for later rounds.

### H. Headunit Server, D-HP with D-POCO's server, 5 exits per arm (H-B, H-M)

```bash
HU=CNU350BGBJ; PH=4f4027e9; OTHER=; PUT=tab_put; KEYS=$KEYS_H
PHIP=$(adb -s $PH shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
hrun() { local A=$1 n L; sess_open H-$A || return 1
  listening || srv_restart H-$A || { echo "SERVER_DOWN H-$A" | tee -a $OUT/H-$A.summary; close_run H-$A; return 2; }
  L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null
  waitfor 45 'SSL handshake complete' $L || { mark H-$A-fallback; send ACTION_CONNECT --es ip $PHIP >/dev/null; waitfor 60 'SSL handshake complete' $L || { echo "NO_FIRST_SESSION H-$A" | tee -a $OUT/H-$A.summary; close_run H-$A; return 3; }; }
  send ACTION_QUERY_STATE > $OUT/H-$A.state
  for n in 1 2 3 4 5; do L=$(nl); exit_one H-$A-x$n; sleep 2
    both H-$A-r$n-go; send ACTION_CONNECT --es ip $PHIP > $OUT/H-$A-r$n.reply
    if waitfor 60 'SSL handshake complete' $L; then echo "H-$A-r$n reconnect=ok" | tee -a $OUT/H-$A.summary
    else echo "H-$A-r$n reconnect=FAIL listening=$(listening && echo up || echo down)" | tee -a $OUT/H-$A.summary
      both H-$A-r$n-done; srv_restart H-$A-r$n || break; L=$(nl); send ACTION_CONNECT --es ip $PHIP >/dev/null; waitfor 60 'SSL handshake complete' $L || break; continue; fi
    both H-$A-r$n-done; done
  close_run H-$A; }
hrun B      # then: adb -s $HU install -r <M apk>; WANT_MD5=<M md5>; identity check; hrun M
```

The `sleep 2` plus the 8 s inside `exit_one` keeps the reconnect past the 5 s user-exit cooldown. For each reconnect window `H-x-rN-go` to `H-x-rN-done`, also count phone `Head unit connected` (`pseg`): 1 or more means the server accepted us.

**Wedge, per reconnect:** a failed reconnect where `pseg H-x-rN-go H-x-rN-done | grep -acF 'Head unit connected'` is 0 and `listening` read `up`. The server holds the port and answers nobody.

**Stop rule:** 5 exits per arm. Stop an arm early after 2 wedges; each wedge costs one H1.

**PASS (H-M), all of:**

1. Every exit: `disc` 1, `stop` 1, `write` reads `SENT`, `quit` 1, `join_fail` 0, `incomplete` 0, `teardown_err` 0.
2. Phone: `crit` 0 on every exit. Exits with `rst` >= 1 and `ref` 0 (or `bye` 0 when `REF` is empty): M's count is no higher than B's, and at most 1 of 5.
3. If `REF` is set: exits with `ref` >= 1 on M are at least B's count minus 1.
4. Reconnect: 5 of 5 `reconnect=ok`, wedges 0, `srv_restart` calls after the first session 0 (`hand-steps.log`).

**FAIL:** any condition above not met. A `write` other than `SENT` is a FAIL and the run section quotes the line. **INCONCLUSIVE:** H-B could not form a first session, or H-B itself wedged twice (the rig, not the change, is wedging the server), or every exit failed the load gate.

**What a PASS looks like if the change did nothing:** the identity check catches that (`ByeBye write` absent). What it looks like if the change loses ByeByes: `write=SENT` with `rst` >= 1 and no `ref` on the phone, or a wedge on M with none on B.

### W. Native AA over WiFi Direct, D-HU with D-POCO, 5 exits per arm (W-B, W-M)

Keep D-SAM's Bluetooth off for the stage (template section 7a, D-POCO: a stale bond can stop the wake). Check D-POCO's screen is idle (`dumpsys window | grep mCurrentFocus`; `input keyevent KEYCODE_HOME` on D-POCO if a Settings screen is up).

```bash
HU=27870808938846; PH=4f4027e9; OTHER=; PUT=hu_put; KEYS=$KEYS_W
arm_w() { local r; r=$(send ACTION_START_WIRELESS_SCAN | tr -d '\r'); echo "$r" | grep -q 'not allowed\|Exception' && adb -s $HU shell am start -n $MAIN >/dev/null; }
wrun() { local A=$1 n L; sess_open W-$A || return 1
  L=$(nl); arm_w; waitfor 120 'SSL handshake complete' $L || { echo "NO_FIRST_SESSION W-$A" | tee -a $OUT/W-$A.summary; close_run W-$A; return 3; }
  send ACTION_QUERY_STATE > $OUT/W-$A.state
  for n in 1 2 3 4 5; do L=$(nl); exit_one W-$A-x$n; sleep 2
    both W-$A-r$n-go; arm_w
    waitfor 120 'SSL handshake complete' $L && echo "W-$A-r$n reconnect=ok" | tee -a $OUT/W-$A.summary || { echo "W-$A-r$n reconnect=FAIL" | tee -a $OUT/W-$A.summary; both W-$A-r$n-done; break; }
    both W-$A-r$n-done; done
  close_run W-$A; }
wrun B      # then install M, identity check, wrun M
```

Per exit, also count `AapService: Native AA user exit. Stopping active launcher.` in `hseg` (expected 1).

**PASS (W-M):** conditions 1 to 3 of H-M, applied to W. The reconnect is reported, not graded (the Native reconnect is not what the change touches); a failed reconnect ends the arm, and the exits before it still count. **INCONCLUSIVE:** fewer than 3 graded exits on either arm.

### U. USB, D-MOTO as host with D-POCO over OTG, 3 exits per arm (U-B, U-M)

Read template section 7b. Record `dumpsys usb` on D-MOTO before the stage. Put both phones on wireless adb and set `HU` and `PH` to their addresses.

```bash
HU=<D-MOTO ip:5555>; PH=<D-POCO ip:5555>; OTHER=; PUT=tab_put; KEYS=$KEYS_U
plug() { local L; L=$(nl); cue "On D-MOTO: unplug D-POCO's OTG cable, wait 3 s, plug it back, accept the USB dialog without ticking always ($1)"
  P='Found device already in accessory mode|Switching USB device to accessory mode|SSL handshake complete'
  waitfor 120 "$P" $L || { cue "REPEAT: unplug and replug D-POCO's OTG cable ($1)"; waitfor 120 "$P" $L || return 1; }
  sleep 3; send ACTION_CHECK_USB >/dev/null; waitfor 90 'SSL handshake complete' $L || return 2
  waitfor 60 'Throughput over [0-9]+ms: rendered=[1-9]' $L; }
urun() { local A=$1 n; sess_open U-$A || return 1; adb -s $HU shell am start -n $MAIN >/dev/null; sleep 5
  for n in 1 2 3; do plug U-$A-p$n; r=$?; [ $r = 1 ] && { echo "OPERATOR_MISSED U-$A-p$n" | tee -a $OUT/U-$A.summary; break; }
    [ $r = 2 ] && { echo "U-$A-p$n no_session" | tee -a $OUT/U-$A.summary; continue; }
    exit_one U-$A-x$n; sleep 2; done
  close_run U-$A; }
urun B      # then install M on D-MOTO, identity check, urun M
```

**PASS (U-M):** every exit meets condition 1 of H-M, and `crit` 0 on the phone. The phone columns are reported beside B's; no loss is expected on USB on either arm. **INCONCLUSIVE:** fewer than 2 sessions formed on U-B. **UNTESTABLE:** no operator.

### Discard rules (every stage)

Void the exit and run one more in its place when its window holds `Magic Garbage detected in header`, `MATCH! Starting AapService`, or an `SSL handshake complete` between `<ID>-go` and the exit's `AapTransport quitting (clean=` line. An SSL after the quitting line is the app reconnecting on its own: keep the exit, and the reconnect wait counts it (the wait reads from before the exit). Void it as a load-gate failure when `LOAD_FAIL <ID>` was printed. At most 2 replacement exits per arm.

## 7. Do not re-run

- The relayed-verb, decoder and connect-race work on `main`: projection-teardown-and-relays rounds 1 to 3.
- The ByeBye on a framework shutdown (`AapTransport stopping and sending byebye` then a clean server): discovery-socket-leak round 5, R10.

## 8. Report back

1. A table per stage, one row per exit and arm, with every `xt` field and the reconnect result.
2. Per stage and arm: exits with `rst` >= 1 and no receipt, exits with `ref` >= 1 (or `eos` >= 1), and wedges. These decide the merge.
3. `REF` and the lines it came from; the median `stop_to_quit_ms` per arm; `resp` per arm with the bias noted.

Results go in `pr-1066-byebye-priority-round1-results.md` in the template's skeleton (section 7), one `## <RUN>` section per run id (H-B, H-M, W-B, W-M, U-B, U-M). Write the file after every run, with `th_report` output in each section. Captures go to the fork as release `rig-evidence-pr-1066-byebye-priority`, one asset `pr-1066-byebye-priority-round1-captures.zip`, cited with its sha256.

The phone lines are Gearhead's and do not grep in our tree: `ReaderThread: end of stream received`, `Critical error`, `Head unit connected`, `Head unit disconnected`, `Network server running on port`, `ECONNRESET`, `Connection reset`, `Broken pipe`, and the `$BYERE` pattern.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
Disconnect action received.
AapTransport stopping and sending byebye (
AapTransport: ByeBye write 
AapTransport quitting (clean=
Failed to join threads
AapTransport: send incomplete (ret=
AapTransport: send failed (ret=
Byebye Response received
CommManager: doDisconnect
SSL handshake complete
Throughput over 
Media Start Request 
Found device already in accessory mode
Switching USB device to accessory mode
NetworkDiscovery: Found Headunit Server on 
AapService: Native AA user exit. Stopping active launcher.
WifiLauncher: Initializing WiFi Mode: 
User exit cooldown active
Magic Garbage detected in header
MATCH! Starting AapService
```
