# self-mode-media-session, round 3 brief

Published name: `self-mode-media-session-round3-brief.md`. Results go in
`self-mode-media-session-round3-results.md`. Evidence: create the release
`rig-evidence-self-mode-media-session` again (the thread's old release went into `rig-evidence-legacy`) and
upload `self-mode-media-session-round3-captures.zip` to it.

Units: D-HU (head unit) and D-POCO (phone in stage H, phone and head unit at once in stage P). D-MOTO, D-SAM
and D-HP take no part. At most two hand steps (D-POCO's head unit server toggle, and a Spotify track if the
keys do not start one) and at most one injected tap into the projected video (H1).

Queue line: `self-mode-media-session-round3-brief.md` (C `e4f8f6d9` on `fork/fix/929-self-mode-media-session`,
on `main` `e9377ff9`, one build, no baseline; units D-HU and D-POCO, D-MOTO Bluetooth off; P1 and H2 are the
point).

Estimated time: 8 min pre-flight, 12 min build (warm worktree), H1 8 min, H2 6 min, role swap 2 min, P1 8 min,
P2 5 min, 6 min thermal waits. About 55 min, of which 29 min are runs.

**This is a re-check after two rebases.** Round 2 passed on `d5d463d5`. Nothing since then ran on hardware.
This round runs only what the changes since round 2 put at risk, and two runs that round 2 could not reach.

## 1. Build and baseline

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/fix/929-self-mode-media-session` | `e4f8f6d9fe560b92913ed358d28e1fa2d03a4afd` | `af663169edbfb33d9caa22754b4c894574550276` |

History was rewritten twice since round 2: a rebase onto `main` `28af450d` on 2026-10-17 (hand-resolved) and a
clean rebase onto `main` `e9377ff9` on 2026-10-22. There is no baseline arm. Round 2's controls (A1c, B1c, C1c)
settled what the old build does.

The dev host ran the JVM suite at `e4f8f6d9`: **3501 tests, 0 failures**. The host-connection harness passed.
Do not run the unit tests on the rig.

`second-screen-outputs-round3-brief.md` also builds on `main` `e9377ff9`. If the same session runs it, build both
in this one worktree, this candidate first.

**get_apk.sh**

```bash
OUT=rig-data/rounds/self-mode-media-session-round3; mkdir -p $OUT
git fetch fork fix/929-self-mode-media-session
WT=$(ls -d worktrees/*929* worktrees/*self-mode-media* 2>/dev/null | head -1)   # else create one with $RIG_TOOLKIT/wt-new.sh
git -C $WT checkout --detach e4f8f6d9fe560b92913ed358d28e1fa2d03a4afd
git -C $WT rev-parse 'HEAD^{tree}'               # MUST print af663169edbfb33d9caa22754b4c894574550276
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh    # copy the APK out of apks/ to $OUT/apk-C.apk at once
md5sum $OUT/apk-C.apk                            # record it
```

Install with `adb -s <serial> install -r -d $OUT/apk-C.apk` on D-HU and on D-POCO. Never uninstall.

Identity, after every install, on each unit (`apk_check` in section 5 does all three):

- the pulled APK's md5 equals `$OUT/apk-C.apk`'s;
- `send ACTION_QUERY_STATE`: the `data=` line carries commit `e4f8f6d9` (a `-dirty` suffix is fine);
- the DEX holds `SelfModeWirelessPausePolicy` (1 or more; `main` has none) and no `MediaSessionOwnershipPolicy`
  (0; round 2's `d5d463d5` has it).

## 2. What this is and why

In Self Mode the app projects this device's own Android Auto to itself over loopback. It announces no media
or speech audio sink, so it is not in the audio path. Four commits stop it from doing things that only make
sense for a separate phone:

1. **Media buttons.** A media key on the projection is not sent to Android Auto in Self Mode. It falls through
   to the system, which gives it to the player that is playing.
2. **Audio focus.** The app never takes system audio focus from the local player in Self Mode. This covers the
   dynamic path and static mode's permanent grab.
3. **GPS.** Self Mode does not announce the head unit GPS sensor. Android Auto reads this device's location
   directly.
4. **Wireless stack.** Starting Self Mode stands the wireless stack down (no P2P group, no poke loop). The stack
   comes back when Self Mode ends, fails, times out or is stopped. A user request (the WiFi button) still arms it.

## 3. What changed since round 2, and what is different now

The changes, none of them measured on hardware:

- **Commit 1 lost its media-session half.** Upstream `main` now keeps the media session inactive in Self Mode by
  itself, so `MediaSessionOwnershipPolicy` and its line `media session left to the player on this device (Self
  Mode)` are gone. Commit 1 now holds only the key routing. Round 2 never graded the Self Mode half of the key
  routing (round 1's R2 and R3 were INCONCLUSIVE). **P1's K1 is the first hardware read of it.**
- **The focus gate moved into `main`'s leased playback-focus path.** An ordinary session must still take focus
  through it (H1, A3). Static mode's decline now prints only the `CommManager:` line, not an `AapService:` line.
- **`stopIfCurrent` now gives the wireless stack back.** That path runs when a different route wins over a
  Self Mode launch that is still waiting. It re-arms a stack that may already carry a live session. **H2 is the
  first hardware read of it.** The pass rule is that the live session survives the re-arm.
- **Two decline strings changed.** The playback-path decline is now `AapAudio: playback without system focus (`.
  The `AA audio started (AUDIO2) - leaving system audio focus alone` line no longer exists.

Rig facts that change the runs:

- **Self Mode needs D-POCO's Android Auto head unit server on `:5277`.** Pre-flight step 6 checks it. Turning it on is a hand
  step. **Never force-stop `com.google.android.projection.gearhead` in this round.**
- **Budget one operator round trip for the Gearhead wedge.** If a Self Mode capture holds
  `the peer accepted the connection and then sent nothing at all`, the server is wedged. Ask the operator to turn
  "Start head unit server" off and on once, then re-run that run once.
- **H1 and H2 need the legacy Self Mode path on D-HU** (round 2 read Gearhead 17.3 there, `SelfMode: AA < 17.4
  detected.`). If D-HU now takes the 17.4+ path, H1 still runs (the launch fails instead of timing out) and H2
  is UNTESTABLE.
- **H2 is a race against a 30 s deadline.** The Native session must form before the legacy launch times out. If
  the timeout line comes first, H2 is INCONCLUSIVE, not FAIL. Run it at most twice.
- **D-POCO changes role between stage H and stage P.** It is the phone in stage H. In stage P it runs the app.
  The swap step sends `headunit://exit` at the end of stage P.
- **Use logcat, not the app's log file.** Round 2 used `log-source=1`, which moves the app's lines out of
  logcat. This round deletes `log-source` (default LOGCAT), so the app's lines and Android Auto's lines are in
  one capture per device. In stage P that one D-POCO capture is also the phone capture.
- **Discard rule inverted for P1's C1 window.** `createGroup SUCCESS`, `Attempting active poke to device` and
  `MATCH! Starting AapService` after the stand-down line are the measurement there, not contamination.
- **Do not discard H2 for `MATCH! Starting AapService`.** D-POCO's Bluetooth arrives while Self Mode holds the
  stack down, so an auto-start there is expected, and its refusal is one of the lines H2 reports.
- **`ACTION_START_WIRELESS_SCAN` in H2 is deliberate.** It comes well after the launch, while Self Mode holds the
  stack down. It is the WiFi button that the commit says must still work.
- Read `rig-quirks/topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `topics/audio.md`,
  `topics/gearhead.md` and `topics/lifecycle.md` first. The thermal limits are the table in `tooling.md`.

## 4. Settings keys

Back up both units' `settings.xml` at the start and diff them at the end. State the delta in Setup notes.
`stat` D-HU's `shared_prefs/` and record owner and mode. Leave the audio keys (`use-aac-audio`,
`audio-latency-multiplier`, `audio-queue-capacity`) as they are: they are a deliberate worst case. Record, do
not change: `native-poke-bt-macs`, `native-poke-all-paired` (D-HU), `connection-modes` (both units).

**D-HU, every H run** (`HUKEYS`):

| Element | Why |
|---|---|
| `<int name="wifi-connection-mode" value="3" />` | Native AA |
| `<int name="log-level" value="1" />` | DEBUG: `MediaSession: State updated to` is `AppLog.d` |
| delete `log-source` | default LOGCAT |
| `<int name="onboarding-version" value="2" />` | no wizard |
| `<int name="native-driver-selection-mode" value="0" />` | no selector countdown |
| `<boolean name="enable-audio-sink" value="true" />` | rounds left it `false`; without it no media channel opens |
| `<boolean name="gps-navigation" value="true" />` | B2 needs the sensor announced |
| `<boolean name="static-audio-focus" value="false" />` | the dynamic path |
| `<boolean name="playback-focus-self-defeating" value="false" />` | the latch would decline every grab |
| delete `media-key-routing` | default ALWAYS |
| delete `video-profile-starvation-cap` | record its value first; the cap forces AAC and 720p |
| `<boolean name="auto-start-self-mode" value="false" />` | Self Mode starts only by its verb |

**D-POCO, every P run** (`PHKEYS`): the same twelve, plus
`<boolean name="native-poke-all-paired" value="false" />` so the pre-Self Mode stack pokes no bonded speaker.
P2 also writes `<boolean name="static-audio-focus" value="true" />` and puts it back to `false` after.

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at `e4f8f6d9`, so
`allow-external-configuration` is not needed.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Write the results file after every run.

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4; SP=com.spotify.music
OUT=rig-data/rounds/self-mode-media-session-round3; mkdir -p $OUT
export LC_ALL=C
SSL_RE='SSL handshake complete\. (Session id|No session id)'
REND_RE='Throughput over [0-9]+ms: rendered=[1-9]'
nm() { case $1 in $HU) echo hu;; $PH) echo ph;; esac; }
sendto() { local s=$1 a=$2; shift 2; adb -s $s shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
# mark <app-serial> <label>: the app's marker; in stage H also a tag line in the phone capture
mark() { sendto $1 ACTION_LOG_MARKER --es text "$2" >/dev/null
  [ "$1" = "$HU" ] && adb -s $PH shell log -t AutomationMarker "$2"; sleep 0.3; }
launch() { adb -s $1 shell am start -n $MAIN >/dev/null; sleep 2; }
# appexit: only while the app runs (an exit on a stopped app starts the service)
appexit() { adb -s $1 shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5
  adb -s $1 shell am force-stop $PKG; sleep 1; }

# captures, started before the launch: $OUT/<RUN>-hu.txt and $OUT/<RUN>-ph.txt
cap_on() { RUN=$1; shift; PIDS=; for s in "$@"; do adb -s $s logcat -G 16M; sleep 0.3; adb -s $s logcat -c
  stdbuf -oL adb -s $s logcat -v threadtime > $OUT/$RUN-$(nm $s).txt & PIDS="$PIDS $!"; done; sleep 2; }
cap_off() { sleep 2; kill $PIDS 2>/dev/null; wait $PIDS 2>/dev/null; }
nl() { wc -l < "$1"; }
# waitfor FILE ERE CAP_S [FROMLINE]: prints the seconds waited, or TIMEOUT
waitfor() { local n=0 f=${4:-1}; while [ $n -lt $3 ]; do tail -n +$f "$1" | grep -aqE -- "$2" && { echo "waited=$n"; return 0; }
  sleep 1; n=$((n+1)); done; echo TIMEOUT; return 1; }
seg()  { sed -n "/AutomationMarker: $2\$/,/AutomationMarker: $3\$/p" "$1"; }
cnt()  { seg "$1" "$2" "$3" | grep -acF -- "$4"; }
cntE() { seg "$1" "$2" "$3" | grep -acE -- "$4"; }
# after FILE FROM TO ANCHOR STRING: count STRING in FROM..TO after the first line that holds ANCHOR
after() { seg "$1" "$2" "$3" | awk -v a="$4" -v s="$5" 'f && index($0,s){n++} !f && index($0,a){f=1} END{print n+0}'; }
# gap FILE FROM TO ANCHOR STRING: seconds from the first ANCHOR to the first STRING after it
gap() { seg "$1" "$2" "$3" | awk -v a="$4" -v s="$5" 'function t(l){split(l,x," ");split(x[2],h,":");return h[1]*3600+h[2]*60+h[3]}
  !f && index($0,a){f=1;t0=t($0);next} f && index($0,s){printf "%.1f\n",t($0)-t0;d=1;exit} END{if(!d)print "none"}'; }
# before FILE FROM TO A B: 1 if the first A comes before the first B (or B never comes), else 0
before() { seg "$1" "$2" "$3" | awk -v a="$4" -v b="$5" '!ia && index($0,a){ia=NR} !ib && index($0,b){ib=NR} END{print (ia && (!ib || ia<ib))?1:0}'; }

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$RUN.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# D-POCO radios, by svc only
ph_read() { adb -s $PH shell dumpsys wifi | grep -a -m1 'Wi-Fi is'; sleep 0.3; adb -s $PH shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *state:'; }
ph_off() { adb -s $PH shell svc wifi disable; sleep 0.3; adb -s $PH shell svc bluetooth disable; sleep 4; ph_read; }
ph_up()  { adb -s $PH shell svc bluetooth enable; sleep 0.3; adb -s $PH shell svc wifi enable; sleep 6; ph_read; }

# settings. SPEC is set:TYPE:KEY:VALUE or del:KEY. The app MUST be stopped.
kvscript() { echo 'F=shared_prefs/settings.xml'; for kv in "$@"; do IFS=: read -r op t k v <<< "$kv"; [ "$op" = del ] && k=$t
  echo "sed -i -E 's#<[a-z]+ name=\"$k\"[^>]*/>##g' \$F"; echo "sed -i -E 's#<string name=\"$k\">[^<]*</string>##g' \$F"
  if [ "$op" = set ]; then if [ "$t" = string ]; then echo "sed -i 's|</map>|<string name=\"$k\">$v</string></map>|' \$F"
    else echo "sed -i 's|</map>|<$t name=\"$k\" value=\"$v\" /></map>|' \$F"; fi; fi; done; }
hu_kv() { { echo "cd /data/data/$PKG"; kvscript "$@"; echo "chown \$(stat -c %u:%g /data/data/$PKG) \$F; chmod 660 \$F"; } > $OUT/kv-hu.sh
  adb -s $HU push $OUT/kv-hu.sh /data/local/tmp/kv-hu.sh >/dev/null; adb -s $HU shell sh /data/local/tmp/kv-hu.sh; }
ph_kv() { kvscript "$@" > $OUT/kv-ph.sh; adb -s $PH shell "run-as $PKG sh" < $OUT/kv-ph.sh; }
KEYS_RE='name="(wifi-connection-mode|log-level|log-source|onboarding-version|native-driver-selection-mode|enable-audio-sink|gps-navigation|static-audio-focus|playback-focus-self-defeating|media-key-routing|video-profile-starvation-cap|auto-start-self-mode|native-poke-all-paired)"[^/]*'
keys_read() { adb -s $1 shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-$2.xml; grep -aoE "$KEYS_RE" $OUT/settings-$2.xml; }
COMMON="set:int:wifi-connection-mode:3 set:int:log-level:1 del:log-source set:int:onboarding-version:2 set:int:native-driver-selection-mode:0 set:boolean:enable-audio-sink:true set:boolean:gps-navigation:true set:boolean:static-audio-focus:false set:boolean:playback-focus-self-defeating:false del:media-key-routing del:video-profile-starvation-cap set:boolean:auto-start-self-mode:false"
HUKEYS="$COMMON"; PHKEYS="$COMMON set:boolean:native-poke-all-paired:false"

# APK identity on one unit: md5, DEX symbols (the commit comes from ACTION_QUERY_STATE)
apk_check() { local s=$1 f=$OUT/live-$(nm $1).apk; adb -s $s pull $(adb -s $s shell pm path $PKG | cut -d: -f2 | tr -d '\r') $f >/dev/null 2>&1
  local m=$(md5sum $f | cut -d' ' -f1) w=$(md5sum $OUT/apk-C.apk | cut -d' ' -f1)
  local a=$(unzip -p $f 'classes*.dex' | strings | grep -c SelfModeWirelessPausePolicy) b=$(unzip -p $f 'classes*.dex' | strings | grep -c MediaSessionOwnershipPolicy)
  echo "$(date +%T) $(nm $s) live=$m want=$w pause_policy=$a ownership_policy=$b" | tee -a $OUT/apk-check.log
  [ "$m" = "$w" ] && [ $a -ge 1 ] && [ $b -eq 0 ] || { echo APK_MISMATCH | tee -a $OUT/apk-check.log; return 1; }; }

# Spotify on D-POCO: its PlaybackState number (3 playing, 2 paused), or none
sp_state() { adb -s $PH shell dumpsys media_session | awk -v p="package=$SP" 'index($0,p){f=1} f && /state=PlaybackState/{match($0,/state=[0-9]+/); print substr($0,RSTART+6,RLENGTH-6); d=1; exit} END{if(!d) print "none"}'; }
sp_play() { adb -s $PH shell am start -a android.intent.action.VIEW -d spotify:collection:tracks >/dev/null; sleep 6
  adb -s $PH shell input keyevent 126; sleep 5; local s=$(sp_state)
  [ "$s" = 3 ] || { adb -s $PH shell input keyevent 85; sleep 5; s=$(sp_state); }
  adb -s $PH shell input keyevent KEYCODE_HOME; echo "spotify_state=$s"; }
```

Script exit codes: 9 no session, 11 APK identity, 12 host hot, 13 no `:5277` on D-POCO, 14 Gearhead wedge.

The Spotify keys go to Spotify's own window before the app runs. They are not app actions. If
`spotify_state` is not `3` after `sp_play`, ask the operator once to start a track in Spotify on D-POCO (hand
step: the player is not our app), then read `sp_state` again.

## 6. The deciding lines

Every app string was checked with `git grep -F` in `app/src` at `e4f8f6d9`. All print at INFO except
`MediaSession: State updated to`, which is DEBUG. Lines with a run-time value are shown in their run-time form
here and by their source fragment in the `decisive-strings` block.

**App lines** (`$OUT/<RUN>-hu.txt` in stage H, `$OUT/<RUN>-ph.txt` in stage P):

| Line | Meaning |
|---|---|
| `AutomationReceiver: com.andrerinas.openheadunit.<ACTION>` | a verb landed; a missing line voids the step |
| `AutomationMarker: <label>` | a marker landed |
| `SelfMode: AA < 17.4 detected.` / `SelfMode: AA 17.4+ detected.` | which Self Mode path ran |
| `SelfMode: standing the wireless stack down; a loopback session needs no network` | the stand-down |
| `SelfMode: the Self Mode session ended; letting the wireless stack arm again` | given back on a session end |
| `SelfMode: the Self Mode launch timed out; letting the wireless stack arm again` | given back on the 30 s deadline (legacy path) |
| `SelfMode: the Self Mode launch failed; letting the wireless stack arm again` | given back when every launcher failed |
| `SelfMode: Self Mode was stopped; letting the wireless stack arm again` | given back by `stopIfCurrent` (H2) |
| `SelfMode: stopping Self Mode (wasConnected=true, ...)` | the stop that H2 needs |
| `SelfMode: nothing connected within 30000ms of the launch` | the legacy deadline fired |
| `WifiLauncher: wireless bring-up requested while Self Mode is running.` | an automatic bring-up was refused |
| `WifiLauncher: Initializing WiFi Mode: NATIVE` | the stack armed |
| `WifiLauncher: WiFi Mode NATIVE.mode with same start-configuration is already initialized.` | a re-arm found the stack already up and did nothing |
| `AapService: Force-starting WIFI-Scan from UI` | the WiFi button verb reached the service |
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | the Bluetooth listeners are open |
| `createGroup SUCCESS` / `Attempting active poke to device` / `MATCH! Starting AapService` | a group, a poke, a self wake |
| `SSL handshake complete. Session id: ...` or `SSL handshake complete. No session id (full handshake).` | one session (INFO form; `SSL_RE`) |
| `Throughput over <n>ms: rendered=<n> ...` | the picture (`REND_RE` when rendered is 1 or more) |
| `AapTransport quitting (clean=...)` | a session ended |
| `Self Mode is projecting this device to itself, ... the head unit GPS sensor is not announced - this is not a fault` | GPS withheld (grep `sensor is not announced - this is not a fault`) |
| `LOCATION sensor requested. ...` / `Night sensor requested. ...` | the phone asked for a sensor |
| `CommManager: Not sending media key 127 to Android Auto (routing=ALWAYS, selfMode=true, src=projection)` | a key left to the system |
| `CommManager: TX Key -> AA=<code> (isPress=...) src=projection` | a key sent to Android Auto |
| `CommManager: Static Audio Focus - leaving system audio focus alone (mode=..., bluetoothMedia=..., selfMode=true)` | static grab declined |
| `AapAudio: Playback transient focus request result: GRANTED` / `Audio focus request result: GRANTED` | we took system focus (grep `focus request result( \(legacy\))?: `) |
| `AapAudio: phone asked for audio focus - leaving system audio focus alone (...)` | protocol-path decline |
| `AapAudio: playback without system focus (...)` | playback-path decline |
| `Media Start Request AUDIO: session=...` | the media channel started (the colon separates it from AUDIO1 and AUDIO2) |
| `MediaSession: State updated to PLAYING, positionMs=...` | our media session follows Android Auto (DEBUG) |
| `the peer accepted the connection and then sent nothing at all` | the Gearhead wedge (section 3) |

**Android Auto lines** (`$OUT/<RUN>-ph.txt` in both stages). They are Gearhead's and drift between builds.
Record the Gearhead version on both units. A 0 on `Head unit connected` or `Head unit disconnected` means
"string not seen", not FAIL. Both were read in the Gearhead 18.0 decompile (`DeveloperHeadUnitNetworkService`).

| Line | Meaning |
|---|---|
| `GH.` | capture gate: the phone capture works |
| `Critical error` | Android Auto ended a session on a protocol fault |
| `Head unit connected` / `Head unit disconnected` | D-POCO's head unit server took and lost our loopback socket (Self Mode, 17.4+) |

## 7. Runs

### Pre-flight (read only, then the installs)

1. `sed -n '/^## Pick a tool/,/^## Rig and devices/p' $RIG_TOOLKIT/TOOLS.md`. Record the tools you used.
2. `$RIG_TOOLKIT/rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`. Record the table.
3. D-MOTO: `adb -s $MOTO shell svc bluetooth disable` for the whole round.
4. Gearhead versions: `adb -s <unit> shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName`
   on D-HU and D-POCO. Record both.
5. D-POCO: `adb -s $PH shell dumpsys wifip2p | grep isGroupOwner` MUST be false (else `svc wifi disable`, then
   `svc wifi enable`). `adb -s $PH shell dumpsys window | grep mCurrentFocus`: at home (else
   `input keyevent KEYCODE_HOME`). `adb -s $PH shell pm list packages | grep -c $SP` MUST be 1, else P1's K1 and
   P2's player check are INCONCLUSIVE (no player).
6. D-POCO's head unit server: `adb -s $PH shell cat /proc/net/tcp6 /proc/net/tcp | grep -ci ':149D'`. If
   it is 0, ask the operator to turn on "Start head unit server" in Android Auto's developer settings on D-POCO
   (hand step), then read it again. It MUST be 1 or more before P1.
7. Both units: force-stop the app, back up `settings.xml` to `$OUT/settings-backup-<hu|ph>.xml`, `stat` D-HU's
   `shared_prefs/`, and record `native-poke-bt-macs`, `native-poke-all-paired`, `connection-modes` and
   `video-profile-starvation-cap` from each backup.
8. Install on both units, then `apk_check $HU` and `apk_check $PH`. `launch` each unit, `sendto <unit>
   ACTION_QUERY_STATE` (commit `e4f8f6d9`), then `appexit` each unit. An install can wipe `settings.xml`: the
   runs write their keys anyway.

### Stage H: D-HU is the head unit, D-POCO is the phone

Before H1: D-POCO's app stays stopped. `adb -s $PH shell am force-stop $PKG`.

### H1. Self Mode fails on D-HU, then an ordinary session in the same process

Phone capture: yes. Checks C3 (the stack comes back after a failed or timed-out launch), A3 (an ordinary
session still takes focus through the leased path), B2 (it still offers GPS), K2 (it still sends media keys)
and D1 (it still holds an active media session after a Self Mode attempt in the same process).

**h1.sh**

```bash
RUN=H1; th_gate || exit 12; apk_check $HU || exit 11
adb -s $HU shell am force-stop $PKG; sleep 1; ph_off            # MUST read Wi-Fi is disabled, state: OFF
hu_kv $HUKEYS; keys_read $HU H1-pre
cap_on H1 $HU $PH
launch $HU; mark $HU H1-launch
waitfor $OUT/H1-hu.txt 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 60; sleep 5
mark $HU H1-self; sendto $HU ACTION_START_SELF_MODE > $OUT/H1-self-reply.txt
waitfor $OUT/H1-hu.txt 'SelfMode: the Self Mode launch (timed out|failed); letting the wireless stack arm again' 60
L=$(nl $OUT/H1-hu.txt); waitfor $OUT/H1-hu.txt 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 30 $L
mark $HU H1-phone; L=$(nl $OUT/H1-hu.txt); ph_up               # MUST read Wi-Fi is enabled, state: ON
waitfor $OUT/H1-hu.txt "$SSL_RE" 120 $L || { mark $HU H1-end; cap_off; appexit $HU; th_end; exit 9; }
waitfor $OUT/H1-hu.txt "$REND_RE" 40 $L || { sendto $HU ACTION_RAISE_PROJECTION >/dev/null; echo RAISE_VERB_USED; waitfor $OUT/H1-hu.txt "$REND_RE" 30 $L; }
mark $HU H1-up; sleep 10
mark $HU H1-k; adb -s $HU shell input keyevent 126
waitfor $OUT/H1-hu.txt 'Media Start Request AUDIO:' 12 $L || { adb -s $HU exec-out screencap -p > $OUT/H1-pretap.png; sleep 0.3
  adb -s $HU shell input tap 272 657; echo "TAP_USED 272 657" | tee $OUT/H1-taps.txt; waitfor $OUT/H1-hu.txt 'Media Start Request AUDIO:' 15 $L; }
mark $HU H1-a; sleep 20
adb -s $HU shell dumpsys media_session > $OUT/H1-media-session.txt
mark $HU H1-end; sendto $HU ACTION_DISCONNECT >/dev/null; sleep 5; mark $HU H1-closed
cap_off; appexit $HU; th_end
```

The tap is the one standing exception (a tap into the projected video goes to the phone over the touch
channel, `rig-quirks/topics/audio.md`). It runs only if the key did not start the media channel. Record it.

**h1_grade.sh**

```bash
F=$OUT/H1-hu.txt; P=$OUT/H1-ph.txt
MS=$(awk '/package=com.andrerinas.headunitrevived/{f=1} f && /active=/{print ($0 ~ /active=true/)?1:0; exit}' $OUT/H1-media-session.txt)
echo "H1 verbs=$(cnt $F H1-launch H1-closed 'AutomationReceiver: com.andrerinas.openheadunit.ACTION_START_SELF_MODE')" \
 "legacy=$(cnt $F H1-self H1-phone 'SelfMode: AA < 17.4 detected.') server=$(cnt $F H1-self H1-phone 'SelfMode: AA 17.4+ detected.')" \
 "standdown=$(cnt $F H1-self H1-phone 'SelfMode: standing the wireless stack down; a loopback session needs no network')" \
 "rearm=$(cntE $F H1-self H1-phone 'SelfMode: the Self Mode launch (timed out|failed); letting the wireless stack arm again')" \
 "rearm_s=$(gap $F H1-self H1-phone 'SelfMode: standing the wireless stack down' '; letting the wireless stack arm again')" \
 "init_after=$(after $F H1-self H1-phone '; letting the wireless stack arm again' 'WifiLauncher: Initializing WiFi Mode: NATIVE')" \
 "listen_after=$(after $F H1-self H1-phone '; letting the wireless stack arm again' 'NativeAA: ACTIVELY LISTENING on Android Auto UUID')" \
 "refused=$(cnt $F H1-self H1-phone 'WifiLauncher: wireless bring-up requested while Self Mode is running.')" \
 "ssl=$(cntE $F H1-phone H1-end "$SSL_RE") rend=$(cntE $F H1-phone H1-end "$REND_RE")" \
 "aud=$(cnt $F H1-phone H1-end 'Media Start Request AUDIO:') grant=$(cntE $F H1-phone H1-end 'focus request result( \(legacy\))?: GRANTED')" \
 "decl=$(cnt $F H1-launch H1-closed 'leaving system audio focus alone') nofocus=$(cnt $F H1-launch H1-closed 'AapAudio: playback without system focus (')" \
 "loc=$(cnt $F H1-phone H1-end 'LOCATION sensor requested.') night=$(cnt $F H1-phone H1-end 'Night sensor requested.') gpsw=$(cnt $F H1-phone H1-end 'sensor is not announced - this is not a fault')" \
 "tx126=$(cnt $F H1-k H1-end 'CommManager: TX Key -> AA=126') notsent=$(cnt $F H1-launch H1-closed 'CommManager: Not sending media key')" \
 "playing=$(cnt $F H1-up H1-end 'MediaSession: State updated to PLAYING') ms_active=${MS:-none}" \
 "taps=$(cat $OUT/H1-taps.txt 2>/dev/null | wc -l) gh=$(grep -ac 'GH\.' $P) crit=$(cnt $P H1-phone H1-end 'Critical error')"
```

- **Void:** `verbs` = 0, or `gh` = 0 (the phone capture did not work). Re-run once.
- **C3 PASS:** `standdown` = 1, `rearm` = 1, `init_after` >= 1, `listen_after` >= 1, and `ssl` >= 1. **FAIL:**
  `standdown` = 1 with `rearm` = 0 (Self Mode failed and cost the unit its wireless), or `rearm` = 1 with
  `listen_after` = 0.
- **Session gate:** `ssl` = 0 or `rend` = 0 makes A3, B2, K2 and D1 INCONCLUSIVE (rig), not FAIL. Record `crit`.
- **A3 PASS:** `grant` >= 1, `decl` = 0 and `nofocus` = 0. **FAIL:** `aud` >= 1 and `grant` = 0, or `decl` >= 1,
  or `nofocus` >= 1. **INCONCLUSIVE:** `aud` = 0 (no media channel ever started, so focus was never asked).
- **B2 PASS:** `loc` >= 1 and `gpsw` = 0. **FAIL:** `loc` = 0 with `night` >= 1, or `gpsw` >= 1.
  **INCONCLUSIVE:** `loc` = 0 and `night` = 0 (the phone asked for no sensor).
- **K2 PASS:** `tx126` >= 1 and `notsent` = 0. **FAIL:** otherwise.
- **D1 PASS:** `ms_active` = 1. Report `playing` beside it (it needs music, so 0 is not a FAIL when `aud` = 0).
  **FAIL:** `ms_active` = 0. A PASS on D1 with the change doing nothing looks the same, so D1 is a regression
  guard only.
- Phone: `crit` MUST be 0 for A3, B2, K2 and D1 to PASS.

### H2. A Native session wins over a waiting Self Mode launch (THE POINT, with P1)

Phone capture: yes. Runs only if H1 read `legacy` = 1. Else H2 is UNTESTABLE ("D-HU takes the 17.4+ path,
which has no waiting launch").

The user starts Self Mode on D-HU, then presses the WiFi button. The phone connects over Native AA before the
legacy launch times out. The app then retires the Self Mode launch (`stopIfCurrent`) and re-arms the wireless
stack, which already carries the live session. The re-arm must do nothing.

**h2.sh**

```bash
RUN=H2; th_gate || exit 12; apk_check $HU || exit 11
adb -s $HU shell am force-stop $PKG; sleep 1; ph_off
hu_kv $HUKEYS; keys_read $HU H2-pre
cap_on H2 $HU $PH
launch $HU; mark $HU H2-launch
waitfor $OUT/H2-hu.txt 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 60; sleep 5
mark $HU H2-self; sendto $HU ACTION_START_SELF_MODE > $OUT/H2-self-reply.txt
waitfor $OUT/H2-hu.txt 'SelfMode: standing the wireless stack down' 10
ph_up                                                           # the phone's Bluetooth arrives while Self Mode holds the stack
mark $HU H2-scan; L=$(nl $OUT/H2-hu.txt); sendto $HU ACTION_START_WIRELESS_SCAN > $OUT/H2-scan-reply.txt
waitfor $OUT/H2-hu.txt "$SSL_RE" 90 $L || { mark $HU H2-end; cap_off; appexit $HU; th_end; exit 9; }
mark $HU H2-ssl; sleep 30; mark $HU H2-check
mark $HU H2-end; sendto $HU ACTION_DISCONNECT >/dev/null; sleep 5; mark $HU H2-closed
cap_off; appexit $HU; th_end
```

**h2_grade.sh**

```bash
F=$OUT/H2-hu.txt; P=$OUT/H2-ph.txt; S='SelfMode: Self Mode was stopped; letting the wireless stack arm again'
echo "H2 legacy=$(cnt $F H2-self H2-scan 'SelfMode: AA < 17.4 detected.') standdown=$(cnt $F H2-self H2-scan 'SelfMode: standing the wireless stack down')" \
 "scan=$(cnt $F H2-scan H2-ssl 'AapService: Force-starting WIFI-Scan from UI') init=$(cnt $F H2-scan H2-ssl 'WifiLauncher: Initializing WiFi Mode: NATIVE')" \
 "refused=$(cnt $F H2-self H2-check 'WifiLauncher: wireless bring-up requested while Self Mode is running.')" \
 "ssl_s=$(gap $F H2-self H2-check 'SelfMode: standing the wireless stack down' 'SSL handshake complete. ')" \
 "timedout=$(cnt $F H2-self H2-check 'SelfMode: the Self Mode launch timed out; letting the wireless stack arm again')" \
 "stopped=$(cnt $F H2-self H2-check "$S") stopline=$(cnt $F H2-self H2-check 'SelfMode: stopping Self Mode (wasConnected=true')" \
 "same_cfg=$(after $F H2-self H2-check "$S" '.mode with same start-configuration is already initialized.')" \
 "reinit=$(after $F H2-self H2-check "$S" 'WifiLauncher: Initializing WiFi Mode:') quit=$(cnt $F H2-ssl H2-check 'AapTransport quitting (clean=')" \
 "ssl_n=$(cntE $F H2-scan H2-check "$SSL_RE") rend_after=$(seg $F H2-ssl H2-check | grep -acE "$REND_RE")" \
 "gh=$(grep -ac 'GH\.' $P) crit=$(cnt $P H2-scan H2-check 'Critical error')"
```

- **UNTESTABLE:** `legacy` = 0.
- **INCONCLUSIVE (race lost):** `timedout` >= 1. The deadline fired before the session, so `stopIfCurrent` did
  not run its re-arm. Run H2 once more. Two lost races make H2 INCONCLUSIVE; report both `ssl_s` values.
  Also INCONCLUSIVE: `ssl_n` = 0 (no session formed; `exit 9`).
- **PASS:** `timedout` = 0, `stopped` = 1, `stopline` >= 1, `same_cfg` >= 1, `reinit` = 0, `quit` = 0,
  `ssl_n` = 1, `rend_after` >= 2 and `crit` = 0.
- **FAIL:** `timedout` = 0 and `ssl_n` >= 1, and any of: `stopped` = 0 (the stand-down debt leaked into the
  session), `reinit` >= 1 or `quit` >= 1 or `ssl_n` >= 2 (the re-arm tore the session down), `rend_after` < 2.
- Report `refused` (the Bluetooth auto-start that the stand-down refused). A count of 0 is not a FAIL: the
  phone may not raise an auto-start here.

What a PASS would look like if the change did nothing: on `main` there is no stand-down, so no `stopped` line.
`stopped` = 1 is the line only this candidate prints.

### Role swap

D-POCO becomes the head unit. D-HU takes no part in stage P.

**swap.sh**

```bash
adb -s $HU shell am force-stop $PKG; adb -s $HU shell svc bluetooth disable     # so D-POCO's stack finds no D-HU
adb -s $PH shell am force-stop $PKG; sleep 1
adb -s $PH shell dumpsys wifip2p | grep isGroupOwner                           # MUST be false
```

### Stage P: D-POCO alone, Self Mode

D-POCO's capture `$OUT/<RUN>-ph.txt` carries the app's lines and Android Auto's lines. It is the phone
capture too. If a run exits 14 or its capture holds the wedge line, do the hand step in section 3, then re-run
the run once. If a run exits 9 with no wedge line, run it once more with the activity route in place of the
verb (`adb -s $PH shell am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE -p $PKG`), as round 2
did, and say so.

### P1. Self Mode on D-POCO: keys, GPS, the stand-down and the give-back (THE POINT, with H2)

Checks K1 (a media key goes to the local player, not to Android Auto), C1 (no group and no poke while Self
Mode runs), B1 (the GPS sensor is not announced) and C2 (the stack comes back when the session ends).

**p1.sh**

```bash
RUN=P1; th_gate || exit 12; apk_check $PH || exit 11
[ "$(adb -s $PH shell cat /proc/net/tcp6 /proc/net/tcp | grep -ci ':149D')" -ge 1 ] || { echo NO_5277; th_end; exit 13; }
adb -s $PH shell am force-stop $PKG; sleep 1
ph_kv $PHKEYS; keys_read $PH P1-pre
sp_play | tee $OUT/P1-sp-start.txt                              # MUST print spotify_state=3 (else the hand step in section 5)
cap_on P1 $PH
launch $PH; mark $PH P1-launch
waitfor $OUT/P1-ph.txt 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 60; sleep 10
mark $PH P1-self; L=$(nl $OUT/P1-ph.txt); sendto $PH ACTION_START_SELF_MODE > $OUT/P1-self-reply.txt
waitfor $OUT/P1-ph.txt "$SSL_RE" 60 $L || { grep -aqF 'the peer accepted the connection and then sent nothing at all' $OUT/P1-ph.txt && E=14 || E=9
  mark $PH P1-end; cap_off; appexit $PH; th_end; exit $E; }
waitfor $OUT/P1-ph.txt "$REND_RE" 40 $L || { sendto $PH ACTION_RAISE_PROJECTION >/dev/null; echo RAISE_VERB_USED; waitfor $OUT/P1-ph.txt "$REND_RE" 30 $L; }
mark $PH P1-up; sleep 15
adb -s $PH shell dumpsys window | grep -a mCurrentFocus > $OUT/P1-focus.txt
grep -aq AapProjectionActivity $OUT/P1-focus.txt || { sendto $PH ACTION_RAISE_PROJECTION >/dev/null; sleep 3; adb -s $PH shell dumpsys window | grep -a mCurrentFocus >> $OUT/P1-focus.txt; }
echo "sp0=$(sp_state)" > $OUT/P1-sp.txt
mark $PH P1-k; adb -s $PH shell input keyevent 127; sleep 4; echo "sp1=$(sp_state)" >> $OUT/P1-sp.txt
adb -s $PH shell input keyevent 126; sleep 4; echo "sp2=$(sp_state)" >> $OUT/P1-sp.txt
adb -s $PH shell dumpsys media_session | grep -a 'Media button session is' >> $OUT/P1-sp.txt
mark $PH P1-k-end; sleep 60
mark $PH P1-disc; L=$(nl $OUT/P1-ph.txt); sendto $PH ACTION_DISCONNECT >/dev/null
waitfor $OUT/P1-ph.txt 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 40 $L; sleep 3
mark $PH P1-end; cap_off; appexit $PH; th_end
```

**p1_grade.sh**

```bash
F=$OUT/P1-ph.txt; SD='SelfMode: standing the wireless stack down'; N127='CommManager: Not sending media key 127 to Android Auto'
echo "P1 armed_before=$(cnt $F P1-launch P1-self 'WifiLauncher: Initializing WiFi Mode: NATIVE') server=$(cnt $F P1-self P1-up 'SelfMode: AA 17.4+ detected.')" \
 "standdown=$(cnt $F P1-self P1-disc "$SD") grp=$(after $F P1-self P1-disc "$SD" 'createGroup SUCCESS')" \
 "poke=$(after $F P1-self P1-disc "$SD" 'Attempting active poke to device') wake=$(after $F P1-self P1-disc "$SD" 'MATCH! Starting AapService')" \
 "refused=$(cnt $F P1-self P1-disc 'WifiLauncher: wireless bring-up requested while Self Mode is running.')" \
 "gpsw=$(cnt $F P1-self P1-disc 'sensor is not announced - this is not a fault') loc=$(cnt $F P1-self P1-disc 'LOCATION sensor requested.')" \
 "ns127=$(cnt $F P1-k P1-k-end "$N127") ns127_self=$(seg $F P1-k P1-k-end | grep -aF "$N127" | grep -acF 'selfMode=true')" \
 "ns126=$(cnt $F P1-k P1-k-end 'CommManager: Not sending media key 126 to Android Auto') tx=$(cnt $F P1-k P1-k-end 'CommManager: TX Key -> AA=')" \
 "focus_ok=$(grep -ac AapProjectionActivity $OUT/P1-focus.txt) $(tr '\n' ' ' < $OUT/P1-sp.txt)" \
 "rearm=$(cnt $F P1-disc P1-end 'SelfMode: the Self Mode session ended; letting the wireless stack arm again')" \
 "init_after=$(after $F P1-disc P1-end 'SelfMode: the Self Mode session ended; letting' 'WifiLauncher: Initializing WiFi Mode: NATIVE')" \
 "listen_after=$(after $F P1-disc P1-end 'SelfMode: the Self Mode session ended; letting' 'NativeAA: ACTIVELY LISTENING on Android Auto UUID')" \
 "huc=$(cnt $F P1-self P1-up 'Head unit connected') hud=$(cnt $F P1-disc P1-end 'Head unit disconnected')" \
 "gh=$(cnt $F P1-self P1-disc 'GH.') crit=$(cnt $F P1-self P1-disc 'Critical error')"
```

- **Void:** `armed_before` = 0 (the stack never armed, so C1's zero means nothing; check `connection-modes`).
  Re-run once after fixing the keys. Also void: `gh` = 0.
- **K1 PASS:** `focus_ok` >= 1, `ns127` >= 1, `ns127_self` = `ns127`, `tx` = 0, `sp0=3` and `sp1=2`.
  **FAIL:** `tx` >= 1, or `ns127` = 0 with `focus_ok` >= 1, or `sp0=3` and `sp1=3` while the `Media button session
  is` line names `com.andrerinas.headunitrevived` (the key reached nothing that plays).
  **INCONCLUSIVE:** `focus_ok` = 0 (the key never reached our window), or `sp0` is not 3 (no player), or `sp1=3`
  while the media button session is another package (record which). Report `sp2` beside it.
- **C1 PASS:** `standdown` = 1, `grp` = 0 and `poke` = 0. **FAIL:** `grp` >= 1 or `poke` >= 1. Report `wake`: a
  bonded speaker can raise an auto-start on its own, so `wake` alone is not a FAIL.
  Round 2's control showed a group forming during Self Mode on a build without the stand-down. That is what a
  PASS would not show if the change did nothing.
- **B1 PASS:** `gpsw` >= 1 and `loc` = 0. **FAIL:** `loc` >= 1, or `gpsw` = 0 (the setting reads `true`).
- **C2 PASS:** `rearm` = 1, `init_after` >= 1 and `listen_after` >= 1. **FAIL:** `rearm` = 0, or `rearm` = 1 and
  `init_after` = 0.
- Phone: `crit` MUST be 0 for K1, C1 and B1 to PASS. Record `huc` and `hud` (0 is "string not seen").

### P2. Static audio focus in Self Mode

Checks A2 under the moved gate: static mode's permanent grab still stands down, and the local player keeps
playing.

**p2.sh**

```bash
RUN=P2; th_gate || exit 12; apk_check $PH || exit 11
adb -s $PH shell am force-stop $PKG; sleep 1
ph_kv $PHKEYS set:boolean:static-audio-focus:true; keys_read $PH P2-pre       # static-audio-focus MUST read true
sp_play | tee $OUT/P2-sp-start.txt
cap_on P2 $PH
launch $PH; mark $PH P2-launch
waitfor $OUT/P2-ph.txt 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 60; sleep 10
mark $PH P2-self; L=$(nl $OUT/P2-ph.txt); sendto $PH ACTION_START_SELF_MODE > $OUT/P2-self-reply.txt
waitfor $OUT/P2-ph.txt "$SSL_RE" 60 $L || { grep -aqF 'the peer accepted the connection and then sent nothing at all' $OUT/P2-ph.txt && E=14 || E=9
  mark $PH P2-end; cap_off; appexit $PH; th_end; exit $E; }
echo "sp_before=$(sp_state)" > $OUT/P2-sp.txt
mark $PH P2-up; sleep 40; echo "sp_after=$(sp_state)" >> $OUT/P2-sp.txt
mark $PH P2-disc; L=$(nl $OUT/P2-ph.txt); sendto $PH ACTION_DISCONNECT >/dev/null
waitfor $OUT/P2-ph.txt 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 40 $L; sleep 3
mark $PH P2-end; cap_off; appexit $PH
ph_kv set:boolean:static-audio-focus:false; keys_read $PH P2-post             # MUST read false
th_end
```

**p2_grade.sh**

```bash
F=$OUT/P2-ph.txt
echo "P2 static_decl=$(seg $F P2-self P2-disc | grep -aF 'CommManager: Static Audio Focus - leaving system audio focus alone' | grep -acF 'selfMode=true')" \
 "grants=$(cntE $F P2-launch P2-end 'focus request result( \(legacy\))?: ') ssl=$(cntE $F P2-self P2-disc "$SSL_RE")" \
 "rearm=$(cnt $F P2-launch P2-end 'SelfMode: the Self Mode session ended; letting the wireless stack arm again')" \
 "$(tr '\n' ' ' < $OUT/P2-sp.txt)gh=$(cnt $F P2-self P2-disc 'GH.') crit=$(cnt $F P2-self P2-disc 'Critical error')"
```

- **PASS:** `ssl` >= 1, `static_decl` >= 1, `grants` = 0, `rearm` = 1, `crit` = 0, and `sp_after=3` when
  `sp_before=3`.
- **FAIL:** `grants` >= 1, or `static_decl` = 0 with `ssl` >= 1, or `sp_before=3` and `sp_after=2` (the permanent
  loss the gate exists to stop), or `rearm` >= 2 (the give-back ran twice for one session).
- **INCONCLUSIVE:** `ssl` = 0 after the re-run rules above.

### End of round

1. D-POCO, while the app runs: `appexit $PH` already ran. Check `adb -s $PH shell dumpsys wifip2p | grep
   isGroupOwner` is false. `ph_up`. Leave Spotify paused: `adb -s $PH shell input keyevent 127`.
2. D-HU: `adb -s $HU shell svc bluetooth enable`.
3. Restore both `settings.xml` backups with `$RIG_TOOLKIT/restore_settings.sh` and diff them. The diff MUST be empty.
4. D-MOTO: `adb -s $MOTO shell svc bluetooth enable`.

**Stop rule.** Each run has at most one re-run, and H2 at most two attempts. A third void or lost race on the
same run makes it INCONCLUSIVE. Two host-hot gates in a row stop the round: mark the rest UNTESTABLE (host
thermal).

## 8. Do not re-run

- **Round 2's A1 and A1c**, the dynamic focus grab in Self Mode. No media sink is announced and AUDIO2 never
  gets a `Media Start Request`, so no dynamic grab can happen on this rig, on any build. P2 covers the one route
  that does fire.
- **Round 2's controls A1c, B1c and C1c** on the old build. They settled what the defect looks like. No
  baseline is built this round.
- **Round 2's C4 button half.** H2 sends the WiFi button verb during Self Mode, which is the same request.
- **Round 1's R1 to R3.** This rig's Gearhead does not reproduce the media-session symptom.
- **Round 1's R4 and R5**, the ordinary-session key runs. K2 (H1) covers the send, and K1 (P1) covers the
  fall-through that R5 graded under `routing=NEVER`.
- **Round 2's D1 line** `media session left to the player on this device (Self Mode)`. The policy that printed
  it is gone; H1's D1 checks the media session by `dumpsys` instead.
- **The policy transition tables** (`PlaybackFocusPolicy`, `MediaKeyRoutingPolicy`,
  `LocationSourceAnnouncementPolicy`, `SelfModeWirelessPausePolicy`). The dev host's 3501 tests cover them.
- **Video, the hotspot, the decoder, the poke cadence.** Nothing here touches them.

## 9. Report back

1. **K1:** `ns127`, `ns127_self`, `tx`, `sp0`, `sp1`, `sp2` and the `Media button session is` line.
2. **H2:** the verdict with `stopped`, `same_cfg`, `reinit`, `quit`, `ssl_n`, `ssl_s` and `timedout`, for each
   attempt.
3. **H1 and P1 give-backs:** `rearm`, `rearm_s` and `listen_after` from H1, and `rearm`, `init_after` from P1.
   Then A3's `grant` with `aud`, and P2's `static_decl`, `grants`, `sp_before` and `sp_after`.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
SelfMode: AA < 17.4 detected.
SelfMode: AA 17.4+ detected.
SelfMode: standing the wireless stack down; a loopback session needs no network
; letting the wireless stack arm again
the Self Mode session ended
the Self Mode launch timed out
the Self Mode launch failed
Self Mode was stopped
SelfMode: stopping Self Mode (wasConnected=
SelfMode: nothing connected within 
WifiLauncher: wireless bring-up requested while Self Mode is running.
WifiLauncher: Initializing WiFi Mode: 
.mode with same start-configuration is already initialized.
AapService: Force-starting WIFI-Scan from UI
NativeAA: ACTIVELY LISTENING on Android Auto UUID
createGroup SUCCESS
Attempting active poke to device
MATCH! Starting AapService
SSL handshake complete. Session id: 
SSL handshake complete. No session id (full handshake).
Throughput over 
AapTransport quitting (clean=
sensor is not announced - this is not a fault
LOCATION sensor requested.
Night sensor requested.
CommManager: Not sending media key 
selfMode=
CommManager: TX Key -> AA=
CommManager: Static Audio Focus - leaving system audio focus alone 
Audio focus request result
Playback transient focus request result
leaving system audio focus alone
AapAudio: playback without system focus (
the player is on this device (Self Mode)
Media Start Request 
MediaSession: State updated to 
the peer accepted the connection and then sent nothing at all
```

Android Auto's strings, read in the Gearhead 18.0 decompile, not in `app/src`:

```decisive-strings-phone
GH.
Critical error
Head unit connected
Head unit disconnected
```
