# second-screen-outputs, round 3 brief

Published name: `second-screen-outputs-round3-brief.md`. Results go in `second-screen-outputs-round3-results.md`.
Evidence: create the release `rig-evidence-second-screen-outputs` again (the thread's old release went into
`rig-evidence-legacy`) and upload `second-screen-outputs-round3-captures.zip` to it.

Units: D-HU (head unit) and D-POCO (phone). D-MOTO, D-SAM and D-HP take no part. No PC receiver, no firewall
rule, no hand steps and no injected taps.

Queue line: `second-screen-outputs-round3-brief.md` (C `e85e5c10` on `fork/feat/second-screen-outputs`, on
`main` `e9377ff9`, one build, no baseline; units D-HU and D-POCO, D-MOTO radios off; a smoke after two
rebases; P1 is the point).

Estimated time: 6 min pre-flight, 12 min build (warm worktree), O1 4 min, P1 11 min, A1 8 min, 6 min thermal
waits. About 47 min, of which 23 min are runs.

**This is a smoke after two rebases.** The feature is off by default. The rounds before this one settled the
outputs themselves. This round checks only that a session still renders and ends cleanly, and that a session
with a second screen ends by user exit and by link loss with no hang and no crash, then reconnects and renders.

## 1. Build and baseline

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/feat/second-screen-outputs` | `e85e5c106b77f3215afae59f1ec4ae0577977db4` | `914b7c6e29f9890769118b79c866e9a80a19f756` |

Seven commits on `main` `e9377ff9`. History was rewritten twice since the last round (`8fc611a4`): a rebase onto
`main` `28af450d` on 2026-10-17 and one onto `main` `e9377ff9` on 2026-10-22. There is no baseline arm. O1 is the
feature-off control on the candidate.

The dev host ran the JVM suite at `e85e5c10`: **3633 tests, 0 failures**. The host-connection harness passed.
Do not run the unit tests on the rig.

`self-mode-media-session-round3-brief.md` also builds on `main` `e9377ff9`. If the same session runs it, build
both in one worktree, that one first, then this one.

**get_apk.sh**

```bash
git fetch fork feat/second-screen-outputs
WT=$(ls -d worktrees/*second-screen* 2>/dev/null | head -1)      # else create one with $RIG_TOOLKIT/wt-new.sh
git -C $WT checkout --detach e85e5c106b77f3215afae59f1ec4ae0577977db4
git -C $WT rev-parse 'HEAD^{tree}'               # MUST print 914b7c6e29f9890769118b79c866e9a80a19f756
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh    # copy the APK out of apks/ at once, to:
APK_C=rig-data/rounds/second-screen-outputs-round3/apk-C.apk; mkdir -p ${APK_C%/*}
md5sum $APK_C                                    # record it
```

Install with `adb -s $HU install -r -d $APK_C` on D-HU only (R0). Identity (`apk_check` in section 5):

- the pulled APK's md5 equals `$APK_C`'s;
- `send ACTION_QUERY_STATE`: the `data=` line carries commit `e85e5c106b77` (a `-dirty` suffix is fine);
- the DEX holds `RtpStreamOutput` (1 or more; `main` has none) and `AaPresentationSession` (1 or more; the old
  candidate `8fc611a4` has none).

## 2. What this is and why

The branch offers Android Auto a second screen on a chosen output: an Android display, a network stream, an
RTP push to a typed address, or a USB adapter. With `aux-display-enabled` off, nothing about a session changes.

**What the rebases changed.** Today's rebase merged `AapTransport.quit`. It now calls `main`'s new
`presentationSession.retire()` first. It then waits for the poll and send threads and for both video lane
threads, `videoLane.thread` and `auxVideoLane?.thread`. Only after those joins does it close the second-screen
hub and release TLS. `main` also added a presentation session that retires before the disconnect is published.
None of this ran on hardware with a second screen.

**The risk is a hang.** If a lane thread does not end, `quit` waits for it. `CommManager` then waits on
`awaitTermination()`, the decoder stop after it never runs, and no next session can form. The lines after the
joins are the instrument: `SecondScreen: RTP push stopped` and `SecondScreen: the RTP_PUSH output is closed` come
from the hub close, and `Decoder stopped: CommManager: doDisconnect` comes after `awaitTermination()` returns.

## 3. What is different about this round

- **No PC receiver.** The RTP push sends to the PC's address with nothing listening. Round 2 of
  `second-screen-rtp-push` measured that path (D4): ICMP port unreachable comes back, no send fails, the session
  carries on. The head unit's own 10 s report (`RTP push sent ...`) shows the stream ran. So no firewall rule is
  needed.
- **Link loss is new to this thread.** P1 takes D-POCO off the link by `svc` (never airplane mode) and holds it
  off. D-POCO's WiFi turns itself back on after a single disable (`rig-quirks/units/D-POCO.md`), so the
  `holddown` helper disables both radios every 3 s until the head unit ends the session. The app ends a silent
  WiFi session on its 10 s read timeout.
- **A reconnect after a user exit needs the WiFi button.** A user exit drops the P2P group and holds the stack
  down, so P1 and A1 send `ACTION_START_WIRELESS_SCAN` after the exit. That is not a send straight after a
  launch (template §4 step 4). After the link loss, the stack re-arms by itself. If no session forms in 120 s,
  P1 sends the scan once and records `SCAN2_USED`.
- **The Android display output uses an overlay display**, `overlay_display_devices=800x480/160`, as round 2's R3
  did. The aux and main decoders print the same `Throughput over` lines. `tput.py` splits them by thread id.
- **D-HU's station must be on the house WiFi.** The push binds to `wlan0`. Pre-flight step 4 checks it.
- **D-HU's `enable-audio-sink` read `false` in both earlier rounds.** No run grades audio, so leave it.
- Read `rig-quirks/topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `topics/projection.md` and
  `topics/wifi.md` first. The thermal limits are the table in `tooling.md`.

## 4. Settings keys

All runs are on D-HU, written as root with the app stopped, by `hu_keys` (from round 2's `ssr_lib.sh`). It
reads every key back and prints `KEYS_OK` or `KEYS_MISMATCH <keys>`. Leave the audio keys as they are: they are
a deliberate worst case. `POCO_MAC` comes from pre-flight step 5 and `HOSTIP` from step 6.

**Every run (`BASE`), the same as `second-screen-rtp-push` round 2:**

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `native-poke-bt-macs` | set | `POCO_MAC` | wake D-POCO only |
| `native-poke-all-paired` | boolean | `false` | no other bonded device is poked |
| `native-driver-selection-mode` | int | `0` | no selector countdown |
| `onboarding-version` | int | `2` | no wizard |
| `log-level` | int | `2` | INFO carries every line in section 6 |
| `video-codec` | string | `H.264` | main sink `3` |
| `resolutionId` | int | `3` | the main picture |
| `view-mode` | int | `1` | TEXTURE |
| `force-software-decoding` | boolean | `false` | hardware main decoder |
| `software-video-decoder`, `debug-video-fault-injection` | int | delete | |
| `video-profile-starvation-cap` | boolean | delete | a latch that forces 720p30 and AAC |
| `night-mode` | int | `1` | DAY |

**Per run:**

| Key | O1 | P1 | A1 |
|---|---|---|---|
| `aux-display-enabled` | delete (default `false`) | `true` | `true` |
| `aux-output` | `RTP_PUSH` | `RTP_PUSH` | `ANDROID_DISPLAY` |
| `aux-rtp-destination` | `HOSTIP` | `HOSTIP` | delete |
| `aux-rtp-port` | `5004` | `5004` | delete |
| `aux-network-size` | `1` | `1` | delete |
| `aux-display-id` | delete | delete | `OVL` (from A1's setup) |
| `aux-network-port`, `aux-display-role`, `aux-display-content`, `preferred-display-mode`, `preferred-display-id` | delete | delete | delete |

O1 stores a full push setup with the switch off, so the only difference to P1 is the switch.

**keys.sh**

```bash
BASE="int:wifi-connection-mode=3 set:native-poke-bt-macs=$POCO_MAC bool:native-poke-all-paired=false int:native-driver-selection-mode=0 int:onboarding-version=2 int:log-level=2 str:video-codec=H.264 int:resolutionId=3 int:view-mode=1 bool:force-software-decoding=false del:software-video-decoder del:debug-video-fault-injection del:video-profile-starvation-cap int:night-mode=1"
AUXREST="del:aux-network-port del:aux-display-role del:aux-display-content del:preferred-display-mode del:preferred-display-id"
OKEYS="del:aux-display-enabled str:aux-output=RTP_PUSH str:aux-rtp-destination=$HOSTIP int:aux-rtp-port=5004 int:aux-network-size=1 del:aux-display-id $AUXREST"
PKEYS="bool:aux-display-enabled=true str:aux-output=RTP_PUSH str:aux-rtp-destination=$HOSTIP int:aux-rtp-port=5004 int:aux-network-size=1 del:aux-display-id $AUXREST"
# AKEYS is defined in A1's setup, after OVL is known
```

Define these after pre-flight steps 5 and 6. Echo them and check that no value is empty. `ACTION_LOG_MARKER` is not in
`AutomationCommandPolicy.CONFIGURING` at `e85e5c10`, so `allow-external-configuration` is not needed.

## 5. Helpers

Make the folder `rig-data/rounds/second-screen-outputs-round3/scripts/` and `cd` into it for the whole round.
Copy four files from `second-screen-rtp-push` round 2's folder and compare their md5 prefixes with that round's
Setup notes:

**copy_scripts.sh**

```bash
S2=$(ls -d $RIG_TOOLKIT/rig-data/rounds/second-screen-rtp-push-round2/scripts rig-data/rounds/second-screen-rtp-push-round2/scripts 2>/dev/null | head -1)
cp $S2/ssr_lib.sh $S2/ssr_keys.py $S2/tput.py $S2/rtp_lib.sh .
md5sum ssr_lib.sh ssr_keys.py tput.py rtp_lib.sh
# round 2 read: ssr_lib.sh 37fa464e, ssr_keys.py 3e9ffa2e, tput.py 4df665f4, rtp_lib.sh 9c1530fc
```

If the folder is missing, take `ssr_lib.sh` from section 5 of `archive/rounds/second-screen-outputs-round2-brief.md`,
`ssr_keys.py` and `tput.py` from section 5 of `archive/rounds/second-screen-outputs-round1-brief.md`, and
`rtp_lib.sh` from section 5 of `archive/rounds/second-screen-rtp-push-round1-brief.md`. Record which source you used.

From `ssr_lib.sh` this round uses: `send`, `mark` (both captures), `nl`, `waitfor <secs> <ERE> <fromline>` (on
`$CAP`), `seg`, `cnt`, `cntE`, `ph_on`, `ph_off`, `hu_keys`, `bringup` (0 a session with a picture, 1 no session,
2 a phone `Critical error`, 3 host too hot), `keep_attempt`, `preflight`, `run_end` and `phone_grades`. From
`rtp_lib.sh` it uses `hu_ip`. Then write and source this file. It replaces the thermal read with the toolkit's
own tool and adds the end checks.

**ss3_lib.sh**

```bash
source ./ssr_lib.sh; source ./rtp_lib.sh
export LC_ALL=C
APK_C=$D/../apk-C.apk
SSL_RE='SSL handshake complete\. (Session id|No session id)'
REND_RE='Throughput over [0-9]+ms: rendered=[1-9]'
# host thermal through the toolkit (rig-quirks/topics/tooling.md); th_gate and th_report in ssr_lib.sh call this
th_pkg() { $RIG_TOOLKIT/rig_thermal.sh status | grep -oE '(^|[^_a-z])pkg=[0-9]+' | head -1 | grep -oE '[0-9]+$'; }
# APK identity
apk_check() { local f=$OUT/live.apk; adb -s $HU pull $(adb -s $HU shell pm path $PKG | cut -d: -f2 | tr -d '\r') $f >/dev/null 2>&1
  local m=$(md5sum $f | cut -d' ' -f1) w=$(md5sum $APK_C | cut -d' ' -f1)
  local a=$(unzip -p $f 'classes*.dex' | strings | grep -c RtpStreamOutput) b=$(unzip -p $f 'classes*.dex' | strings | grep -c AaPresentationSession)
  echo "$(date +%T) live=$m want=$w rtp=$a presentation=$b" | tee -a $OUT/apk-check.log
  [ "$m" = "$w" ] && [ $a -ge 1 ] && [ $b -ge 1 ] || { echo APK_MISMATCH | tee -a $OUT/apk-check.log; return 1; }; }
# gap FILE FROM TO ANCHOR STRING: seconds from the first ANCHOR to the first STRING after it
gap() { seg "$1" "$2" "$3" | awk -v a="$4" -v s="$5" 'function t(l){split(l,x," ");split(x[2],h,":");return h[1]*3600+h[2]*60+h[3]}
  !f && index($0,a){f=1;t0=t($0);next} f && index($0,s){printf "%.1f\n",t($0)-t0;d=1;exit} END{if(!d)print "none"}'; }
# user_exit <label>: the Exit verb, then wait for the quit and the decoder stop after it
user_exit() { mark $1; local L=$(nl); send ACTION_DISCONNECT >/dev/null
  waitfor 20 'AapTransport quitting \(clean=' $L || echo "NO_QUIT $1"
  waitfor 15 'Decoder stopped: CommManager: doDisconnect' $L || echo "NO_STOP $1"; sleep 3; mark $1-end; }
# holddown <label>: D-POCO off the link until the session ends (cap 45 s), then 6 s more, then back on
holddown() { mark $1; local L=$(nl) n=0 q=; while [ $n -lt 45 ]; do adb -s $PH shell svc wifi disable; sleep 0.3
    adb -s $PH shell svc bluetooth disable; tail -n +$L "$CAP" | grep -aqF 'AapTransport quitting (clean=' && { q=$n; break; }; sleep 2.7; n=$((n+3)); done
  echo "LL_QUIT_AFTER_S=${q:-none}" | tee $OUT/$1.ll
  for i in 1 2; do adb -s $PH shell svc wifi disable; sleep 0.3; adb -s $PH shell svc bluetooth disable; sleep 2.7; done
  waitfor 15 'Decoder stopped: CommManager: doDisconnect' $L || echo "NO_STOP $1"; mark $1-end; }
# reconnect <label> <scan|auto>: wait for the next session with a picture
reconnect() { local L=$(nl); [ "$2" = scan ] && send ACTION_START_WIRELESS_SCAN >/dev/null
  [ "$2" = auto ] && ph_on
  waitfor 120 "$SSL_RE" $L || { [ "$2" = auto ] && { echo "SCAN2_USED $1"; send ACTION_START_WIRELESS_SCAN >/dev/null; waitfor 90 "$SSL_RE" $L; }; } || { echo "SESSION_FAIL $1"; return 1; }
  waitfor 40 "$REND_RE" $L || { send ACTION_RAISE_PROJECTION >/dev/null; echo "RAISE_VERB_USED $1"; waitfor 30 "$REND_RE" $L; }
  mark $1; }
# endcheck <RUN> <from> <to>: the end of one session
endcheck() { local F=$OUT/$1-hu.txt; echo "$2 quit=$(cnt $F $2 $3 'AapTransport quitting (clean=')" \
  "stop_main=$(cntE $F $2 $3 'Decoder stopped: CommManager: doDisconnect[[:space:]]*$') stop_aux=$(cnt $F $2 $3 'Decoder stopped: CommManager: doDisconnect (second screen)')" \
  "q2s=$(gap $F $2 $3 'AapTransport quitting (clean=' 'Decoder stopped: CommManager: doDisconnect')" \
  "rtp_stopped=$(cnt $F $2 $3 'SecondScreen: RTP push stopped') closed=$(cntE $F $2 $3 'SecondScreen: the [A-Z_]+ output is closed')" \
  "cleanup_fail=$(cnt $F $2 $3 'AapTransport cleanup ') timeout=$(cnt $F $2 $3 'AapRead: WiFi read timeout (') eof=$(cnt $F $2 $3 'AapRead: Connection closed (EOF). Disconnecting.')"; }
# sesscheck <RUN> <from> <holdfrom> <to>: one session; <from>..<to> for the bring-up, <holdfrom>..<to> for the hold
sesscheck() { local F=$OUT/$1-hu.txt; echo "$2 ssl=$(cntE $F $2 $4 "$SSL_RE") ann=$(cnt $F $2 $4 'Announcing an auxiliary display on VIDEO_AUX: ')" \
  "ann_off=$(cnt $F $2 $4 'output is not available, so one display is announced') lane=$(cnt $F $2 $4 "AapTransport: the auxiliary display's video lane is open")" \
  "rtp_open=$(cnt $F $2 $4 'SecondScreen: RTP push to ') rtp_sent=$(cnt $F $3 $4 'RTP push sent ') rtp_fail=$(cnt $F $2 $4 'SecondScreen: RTP push send failed (')" \
  "start_fail=$(cnt $F $2 $4 'SecondScreen: could not start the ') rend=$(cntE $F $3 $4 "$REND_RE") vid_err=$(cnt $F $2 $4 'Error processing video message')"; }
# runcheck <RUN> <from> <to>: crash and restart signs over the whole run
runcheck() { local F=$OUT/$1-hu.txt; echo "$1 pids=$(seg $F $2 $3 | grep -a ' OPENHU' | awk '{print $3}' | sort -u | wc -l)" \
  "destroy=$(cnt $F $2 $3 'AapService destroying') fatal=$(cnt $F $2 $3 'FATAL EXCEPTION') anr=$(cnt $F $2 $3 'ANR in com.andrerinas.headunitrevived')"; }
```

Script exit codes in the run blocks: 0 ok, 1 no session after the retry, 2 a phone `Critical error` at bring-up,
3 host too hot.

## 6. The deciding lines

Every app string was checked with `git grep -F` in `app/src` at `e85e5c10`. All print at INFO, WARN or ERROR,
so `log-level` 2 carries them. Lines with a run-time value are shown here in their run-time form and by their
source fragment in the `decisive-strings` block. Files: `$OUT/<RUN>-hu.txt` (head unit) and
`$OUT/<RUN>-phone.txt` (phone). Windows are marker pairs.

| Line (head unit) | Meaning |
|---|---|
| `AutomationReceiver: ` / `AutomationMarker: ` | a verb or a marker landed; a missing line voids the step |
| `WifiLauncher: Initializing WiFi Mode: NATIVE`, `Projection backend: viewMode=TEXTURE `, `Media Sink Setup Request: 3 on channel VIDEO` | `preflight`: the path under test |
| `SSL handshake complete. Session id: ...` or `SSL handshake complete. No session id (full handshake).` | one session (`SSL_RE`) |
| `Throughput over <n>ms: rendered=<n> ...` | a picture; `tput.py` splits main from aux |
| `[ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX: RTP_PUSH 1280x720 ...` | the second screen was offered |
| `SecondScreen: the RTP_PUSH output is not available, so one display is announced` | no valid address (MUST be 0 in P1) |
| `AapTransport: the auxiliary display's video lane is open` | the phone opened the aux channel |
| `SecondScreen: RTP push to <ip>:5004 open (network=wlan0)` | the push socket opened |
| `SecondScreen: RTP push sent <n> packets, ...` | the 10 s report: the stream ran |
| `SecondScreen: RTP push send failed (<Class>): ...` | a send threw (record) |
| `SecondScreen: could not start the <OUTPUT> output: ...` | the output did not open (MUST be 0) |
| `AapTransport quitting (clean=<b>)` | a session end started |
| `SecondScreen: RTP push stopped` / `SecondScreen: the RTP_PUSH output is closed` | the hub closed, after the lane joins |
| `Decoder stopped: CommManager: doDisconnect` | `awaitTermination()` returned (main decoder; grep with an end anchor) |
| `Decoder stopped: CommManager: doDisconnect (second screen)` | the aux decoder stopped (A1) |
| `AapTransport cleanup <step> failed` | a cleanup step threw (MUST be 0) |
| `AapRead: WiFi read timeout (10000ms) - connection lost.` / `AapRead: Connection closed (EOF). Disconnecting.` | how the link loss was seen (record) |
| `AapProjectionActivity: the auxiliary display is up on <id>` | the Android display output is up |
| `could not open the auxiliary display` / `cannot carry a second screen` | the Android display output failed (MUST be 0) |
| `Output Format Changed: ...width=800...` | `tput.py` marks the aux decoder's thread |
| `Error processing video message` | a lane threw (MUST be 0) |
| `AapService destroying` | the service went down inside a run (MUST be 0) |

**System lines** (head unit capture, not ours): `FATAL EXCEPTION` and `ANR in com.andrerinas.headunitrevived`,
both MUST be 0.

**Phone lines** (`$OUT/<RUN>-phone.txt`, by `phone_grades`). They are Android Auto's and drift between builds.
Record the Gearhead version (round 2 read `18.0.164204-release.daily`).

| Line | Graded | Meaning |
|---|---|---|
| `GH.` | 1 or more | the phone capture works |
| `AutomationMarker: ` | 1 or more in the window | the markers reached the phone log |
| `Critical error` | 0 inside the hold windows | Android Auto ended a session over something we sent |
| `No input for display` | 0 inside the hold windows | the aux display has no input |

A session end makes the phone log `Critical error` lines by design. The hold windows stop before each end, so
those do not count. Report `critical_error_file` beside the windowed count.

## 7. Runs

### Pre-flight (one batch)

1. `sed -n '/^## Pick a tool/,/^## Rig and devices/p' $RIG_TOOLKIT/TOOLS.md`. Record the tools you used.
2. `$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`. Record the table.
3. D-MOTO: `adb -s ZY22GC3BM4 shell svc bluetooth disable; adb -s ZY22GC3BM4 shell svc wifi disable` for the
   whole round. Force-stop the app on D-SAM and D-HP if they are on adb.

**4. D-HU state.**

**p2_state.sh**

```bash
source ./ss3_lib.sh
adb -s $HU shell am force-stop $PKG; sleep 1
adb -s $HU shell stat -c '%U:%G' /data/data/$PKG/shared_prefs                    # record it
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > settings-backup-HU.xml; md5sum settings-backup-HU.xml
grep -o -E '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink|video-profile-starvation-cap)[^/]*' settings-backup-HU.xml
grep -o -E 'aux-[a-z-]*|preferred-display-[a-z]*' settings-backup-HU.xml
adb -s $HU shell settings get global overlay_display_devices                     # MUST be null; else record it and delete it
HUIP_P=$(hu_ip); echo "HUIP_P=$HUIP_P"; HUIP=${HUIP_P%/*}                         # MUST be an address on wlan0
```

If `HUIP_P` is empty, D-HU's station is off the house WiFi. Then P1 is UNTESTABLE ("no station for the push").
Run O1 and A1.

**5. D-POCO state.**

**p3_state.sh**

```bash
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
POCO_MAC=$(adb -s $PH shell settings get secure bluetooth_address | tr -d '\r' | tr a-f A-F); echo "POCO_MAC=$POCO_MAC"
adb -s $PH shell am force-stop $PKG                                              # D-POCO is the phone only
adb -s $PH shell dumpsys wifip2p | grep -a isGroupOwner                          # MUST be false, else svc wifi disable / enable
adb -s $PH shell dumpsys window | grep -a mCurrentFocus; adb -s $PH shell input keyevent KEYCODE_HOME
```

`POCO_MAC` MUST be a real address, not empty and not `02:00:00:00:00:00`.

**6. The push address.**

**p4_addr.sh**

```bash
HOSTIP=$(ip -4 route get "$HUIP" | grep -o 'src [0-9.]*' | cut -d' ' -f2); echo "HOSTIP=$HOSTIP"
adb -s $HU shell ping -c 2 -W 2 $HOSTIP | tail -1
```

Define `BASE`, `AUXREST`, `OKEYS` and `PKEYS` from section 4 now.

**R0. Identity.** `adb -s $HU install -r -d $APK_C`, then `apk_check`, then `adb -s $HU shell am start -n
$MAIN; sleep 4; send ACTION_QUERY_STATE` (commit `e85e5c106b77`), then `adb -s $HU shell am force-stop $PKG`.
**PASS:** `apk_check` prints no `APK_MISMATCH` and the commit matches. A failed build or identity stops the round.

`preflight` prints `PREFLIGHT_FAIL <RUN>` when a capture did not run the path under test. Then fix the keys and
re-run that run once.

### O1. Feature off: the session renders and ends cleanly (4 min)

Phone capture: yes.

**o1.sh**

```bash
RUN=O1; bringup O1 $OKEYS; rc=$?
[ $rc = 1 ] && { run_end; keep_attempt O1; bringup O1 $OKEYS; rc=$?; }
[ $rc = 0 ] || { [ $rc != 3 ] && run_end; echo "O1_STOPPED rc=$rc"; exit $rc; }
preflight
mark O1-s1; sleep 40
user_exit O1-x1
run_end
sesscheck O1 O1-launch O1-s1 O1-x1 | tee $OUT/O1.grade
endcheck O1 O1-x1 O1-x1-end | tee -a $OUT/O1.grade
runcheck O1 O1-launch O1-x1-end | tee -a $OUT/O1.grade
phone_grades O1 O1-s1 O1-x1 | tee $OUT/O1.phone
```

**O1 PASS:** all of these.

- `ssl` = 1, `ann` = 0, `lane` = 0, `rtp_open` = 0, `rend` >= 3.
- End: `quit` = 1, `stop_main` = 1, `q2s` <= 10, `cleanup_fail` = 0.
- `pids` = 1, `destroy` = 0, `fatal` = 0, `anr` = 0.
- Phone: `win|Critical error=0`, `phone_markers_in_window` >= 1.

**FAIL:** any of those is not met. `rc=1` twice: the rig is broken, stop and report.

### P1. RTP push: a user exit, a reconnect, a link loss, a reconnect (11 min, THE POINT)

Phone capture: yes. One process carries three sessions.

**p1.sh**

```bash
RUN=P1; bringup P1 $PKEYS; rc=$?
[ $rc = 1 ] && { run_end; keep_attempt P1; bringup P1 $PKEYS; rc=$?; }
[ $rc = 0 ] || { [ $rc != 3 ] && run_end; echo "P1_STOPPED rc=$rc"; exit $rc; }
preflight
mark P1-s1; sleep 30
user_exit P1-x1                       # session 1 ends by user exit
reconnect P1-s2 scan; sleep 30        # session 2, after the WiFi button
holddown P1-ll                        # session 2 ends by link loss
reconnect P1-s3 auto; sleep 30        # session 3, after the radios come back
mark P1-s3-end
run_end
{ sesscheck P1 P1-launch P1-s1 P1-x1; sesscheck P1 P1-x1-end P1-s2 P1-ll; sesscheck P1 P1-ll-end P1-s3 P1-s3-end
  endcheck P1 P1-x1 P1-x1-end; endcheck P1 P1-ll P1-ll-end; cat $OUT/P1-ll.ll; runcheck P1 P1-launch P1-s3-end; } | tee $OUT/P1.grade
phone_grades P1 P1-s1 P1-x1 P1-s2 P1-ll P1-s3 P1-s3-end | tee $OUT/P1.phone
```

If `reconnect` prints `SESSION_FAIL`, the script carries on, and the session after it grades as absent.

**P1 PASS:** all of these.

- Each of the three sessions: `ssl` >= 1, `ann` >= 1, `ann_off` = 0, `lane` >= 1, `rtp_open` >= 1, `rtp_sent` >= 2,
  `start_fail` = 0, `rend` >= 3, `vid_err` = 0.
- Each of the two ends (`P1-x1`, `P1-ll`): `quit` = 1, `stop_main` = 1, `q2s` <= 10, `rtp_stopped` >= 1,
  `closed` >= 1, `cleanup_fail` = 0. `LL_QUIT_AFTER_S` is a number.
- `pids` = 1, `destroy` = 0, `fatal` = 0, `anr` = 0.
- Phone: `win|Critical error=0`, `win|No input for display=0`, `phone_markers_in_window` >= 3.

**FAIL:** any end with `quit` = 1 and `stop_main` = 0 (the hang this round looks for), or `q2s` above 10, or
`closed` = 0, or `cleanup_fail` >= 1; `start_fail` >= 1 in session 2 or 3 (the hub did not let go of the output);
`LL_QUIT_AFTER_S=none` (the session outlived 45 s with the phone off the link); `pids` >= 2, `fatal` >= 1 or
`anr` >= 1; or any other PASS condition missed.

**INCONCLUSIVE:** `SESSION_FAIL` for session 2 or 3 while the end before it passed and `start_fail` = 0. The
phone did not come back, and our side ended cleanly. Record whether the phone capture shows a dial in that
window (`grep -acF 'Creating rfcomm socket for device:'`). Record `SCAN2_USED`, `timeout` and `eof` either way.

What a PASS would look like if the change did nothing: the same. This is a regression smoke, and `rtp_sent`
>= 2 with `lane` >= 1 is what proves the aux lane thread was alive when each `quit` had to join it.

### A1. Android display: a user exit and a reconnect (8 min)

Phone capture: yes. The aux decoder sits behind `auxVideoLane.thread` here, so its stop line is the second
instrument.

**a1_setup.sh**

```bash
adb -s $HU shell settings put global overlay_display_devices 800x480/160; sleep 3
OVL=$(adb -s $HU shell dumpsys display | grep -a -o 'DisplayInfo{"Overlay #1", displayId [0-9]*' | head -1 | grep -o '[0-9]*$')
[ -z "$OVL" ] && OVL=$(adb -s $HU shell dumpsys display | grep -a -B40 'mPrimaryDisplayDevice=Overlay #1' | grep -a -o 'mDisplayId=[0-9]*' | tail -1 | cut -d= -f2)
echo "OVL=$OVL"
AKEYS="bool:aux-display-enabled=true str:aux-output=ANDROID_DISPLAY int:aux-display-id=$OVL del:aux-rtp-destination del:aux-rtp-port del:aux-network-size $AUXREST"
```

If `OVL` is empty, A1 is UNTESTABLE ("no overlay display"). Delete the overlay and go to the end of round.

**a1.sh**

```bash
RUN=A1; bringup A1 $AKEYS; rc=$?
[ $rc = 1 ] && { run_end; keep_attempt A1; bringup A1 $AKEYS; rc=$?; }
[ $rc = 0 ] || { [ $rc != 3 ] && run_end; echo "A1_STOPPED rc=$rc"; exit $rc; }
preflight
mark A1-s1; sleep 30
user_exit A1-x1
reconnect A1-s2 scan; sleep 30
mark A1-s2-end
run_end
adb -s $HU shell settings delete global overlay_display_devices
{ echo "A1 up1=$(cnt $OUT/A1-hu.txt A1-launch A1-x1 'AapProjectionActivity: the auxiliary display is up on ')" \
    "open_fail=$(grep -acF 'could not open the auxiliary display' $OUT/A1-hu.txt) carry=$(grep -acF 'cannot carry a second screen' $OUT/A1-hu.txt)"
  python3 -I tput.py "$CAP" A1-s1 A1-x1; python3 -I tput.py "$CAP" A1-x1-end A1-s2-end A1-x1-end
  endcheck A1 A1-x1 A1-x1-end; runcheck A1 A1-launch A1-s2-end; } | tee $OUT/A1.grade
phone_grades A1 A1-s1 A1-x1 A1-s2 A1-s2-end | tee $OUT/A1.phone
```

**A1 PASS:** all of these.

- `up1` >= 1, `open_fail` = 0, `carry` = 0.
- Session 1 (`tput.py` A1-s1..A1-x1): aux `windows_rendered_ge1` >= 1, main `windows_rendered_ge1` >= 3.
- End (`A1-x1`): `quit` = 1, `stop_main` = 1, `stop_aux` = 1, `q2s` <= 10, `cleanup_fail` = 0.
- Session 2 (`tput.py` A1-x1-end..A1-s2-end): aux `windows_rendered_ge1` >= 1 with
  `first_aux_picture_after_A1-x1-end_s` <= 90, main `windows_rendered_ge1` >= 3.
- `pids` = 1, `destroy` = 0, `fatal` = 0, `anr` = 0. Phone: `win|Critical error=0`, `win|No input for display=0`.

**FAIL:** `quit` = 1 with `stop_aux` = 0 or `stop_main` = 0, or `q2s` above 10, or `cleanup_fail` >= 1, or
session 2 formed with a main picture and no aux picture, or any other PASS condition missed.
**INCONCLUSIVE:** session 1 has no aux picture (the output never came up, so the end tested nothing), or
`SESSION_FAIL A1-s2` while the end passed.

### End of round

1. `adb -s $HU shell settings get global overlay_display_devices` MUST print `null`.
2. Restore D-HU's `settings.xml` from `settings-backup-HU.xml` as root (`rig-quirks/units/D-HU.md`), `chown` and
   `chmod 660` it, and `diff` it against the backup: MUST be empty.
3. D-POCO: `ph_on`; `dumpsys wifip2p | grep isGroupOwner` false. D-MOTO: radios on.
4. No `logcat` process of this round is left (`ps aux | grep -c "[l]ogcat"` before and after).

**Stop rule.** Each run has one retry inside its block. A run whose `bringup` returns 1 twice is INCONCLUSIVE
(rig). Two host-hot gates in a row stop the round: mark the rest UNTESTABLE (host thermal).

## 8. Do not re-run

- **`second-screen-rtp-push` round 2's R4** (early receiver, closed port, refused sends, resumed sends), **R5's
  stream checks** (sequence loss, IDR groups and cadence) and **C-FPS** (the push costs the main picture
  nothing: 29 fps both ways). The push path has not changed since `8fc611a4`; only its teardown has.
- **`second-screen-outputs` round 2's R2 and R2C** (the network output with two viewers, the CLUSTER role),
  **R4** (screen off and wake) and **VIEWER-SAM**. A1 repeats only R3's session end and next session.
- **N1**, the network output with no viewer. It runs only after a cost FAIL, and C-FPS passed.
- **The MacroSilicon and USB display outputs.** No rig unit has the hardware; the dev host's 3633 tests cover
  their policies.
- **The settings rows** for the address and the port. They need typing, which the rig does not do.

## 9. Report back

1. **P1:** for each end, `quit`, `stop_main`, `q2s`, `rtp_stopped`, `closed` and `cleanup_fail`, plus
   `LL_QUIT_AFTER_S` and whether `timeout` or `eof` named the link loss.
2. **P1 and A1 reconnects:** `ssl`, `rtp_sent` (P1) or the aux `windows_rendered_ge1` (A1) for each later
   session, and `SCAN2_USED` if it printed.
3. **Crash signs across the round:** `pids`, `fatal` and `anr` per run, and O1's `stop_main` and `q2s`.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
WifiLauncher: Initializing WiFi Mode: 
Projection backend: viewMode=
Media Sink Setup Request: 
SSL handshake complete. Session id: 
SSL handshake complete. No session id (full handshake).
Throughput over 
Announcing an auxiliary display on 
output is not available, so one display is announced
AapTransport: the auxiliary display's video lane is open
SecondScreen: RTP push to 
 open (network=
RTP push sent 
SecondScreen: RTP push send failed (
SecondScreen: could not start the 
AapTransport quitting (clean=
SecondScreen: RTP push stopped
SecondScreen: the 
 output is closed
Decoder stopped: 
CommManager: doDisconnect
CommManager: doDisconnect (second screen)
AapTransport cleanup 
AapRead: WiFi read timeout (
AapRead: Connection closed (EOF). Disconnecting.
AapProjectionActivity: the auxiliary display is up on 
could not open the auxiliary display
cannot carry a second screen
Output Format Changed: 
Error processing video message
AapService destroying
AapService: Force-starting WIFI-Scan from UI
HotspotManager:
SoftApCredentials:
```

Android Auto's and the system's strings, not in `app/src`:

```decisive-strings-phone
GH.
Critical error
No input for display
Creating rfcomm socket for device:
FATAL EXCEPTION
ANR in com.andrerinas.headunitrevived
```
