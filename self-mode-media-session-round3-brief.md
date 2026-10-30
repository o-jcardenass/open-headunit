# self-mode-media-session, round 3 brief

> **Revised 2026-10-26: C moved again.** C moved from `e4f8f6d9` (on `main` `e9377ff9`; it also stood at `13005f48`
> on `f9a72d48` on 2026-10-25) to `6d4de0d0` (tree `c41a0a2c`) on `main` `947b3ae9`. The patch was hand-resolved: a Self
> Mode disconnect keeps `main`'s launch-job retirement (`stop(wasConnected = true)` when the session had a physical
> connection), then the wireless re-arm. Self Mode acceptance now adopts `main`'s retained VPN before it stands the
> wireless stack down. Rebuild C and re-check the identity. Every log string in section 6 is present at `6d4de0d0`.
> A session end now also prints `SelfMode: stopping Self Mode (wasConnected=true, ...)` before the re-arm line. No run,
> string or verdict rule changed. A copy that names `e4f8f6d9` is stale.

> **Revised 2026-10-29: moved onto `rig-lib`.** The round now runs as one `rig_session.py` process from the
> `rig-requires` block below. The runs, the builds and the verdict rules did not change. The scripts and the grade
> scripts did: section 5 and section 7 replace the old helpers with `rl_*` calls.

Published name: `self-mode-media-session-round3-brief.md`. Results go in
`self-mode-media-session-round3-results.md`. Evidence: create the release
`rig-evidence-self-mode-media-session` again (the thread's old release went into `rig-evidence-legacy`) and
upload `self-mode-media-session-round3-captures.zip` to it.

Units: D-HU (head unit) and D-POCO (phone in stage H, phone and head unit at once in stage P). D-MOTO, D-SAM
and D-HP take no part. At most two hand steps (D-POCO's head unit server toggle, and a Spotify track if the
keys do not start one) and at most one injected tap into the projected video (H1).

Queue line: `self-mode-media-session-round3-brief.md` (C `6d4de0d0` on `fork/fix/929-self-mode-media-session`,
on `main` `947b3ae9`, one build, no baseline; units D-HU and D-POCO, D-MOTO Bluetooth off; P1 and H2 are the
point).

Estimated time: 12 min build (warm worktree), prep 6 min, H1 8 min, H2 6 min (12 with a second attempt), P1 8 min,
P2 5 min, cleanup 3 min, 6 min thermal waits. About 55 min, of which 36 min are runs.

**This is a re-check after two rebases.** Round 2 passed on `d5d463d5`. Nothing since then ran on hardware.
This round runs only what the changes since round 2 put at risk, and two runs that round 2 could not reach.

```rig-requires
lib 871881d26
candidate C 6d4de0d0bb1ddb5f057616115ce72ccf72bb427b
unit D-HU   role=headunit wifi=on bt=on
unit D-POCO role=phone wifi=on bt=on
unit D-MOTO bt=off
unit D-POCO@H1,H2 bond=D-HU
unit D-HU@P1,P2 bt=off
unit D-POCO@P1,P2 role=headunit
confirm on D-POCO, Android Auto's developer setting "Start head unit server" is on (P1 and P2 need :5277)
confirm on D-POCO, Spotify is signed in and its Liked Songs list holds 3 or more tracks
run prep cap=900 script=prep.sh grade=grade-prep.sh
run H1 cap=900 script=h1.sh grade=grade-h1.sh
run H2 cap=1080 script=h2.sh grade=grade-h2.sh point=yes
run P1 cap=1080 script=p1.sh grade=grade-p1.sh point=yes
run P2 cap=720 script=p2.sh grade=grade-p2.sh
run cleanup cap=420 script=cleanup.sh always=yes
stop prep FAIL,UNTESTABLE skip=all
stop H1 UNTESTABLE skip=all
stop P1 UNTESTABLE skip=P2
```

The bond is scoped to stage H: there D-POCO is the phone of a D-HU Native session. In stage P, D-HU's Bluetooth is
off so D-POCO's own stack finds no D-HU, and `check --fix` makes the role swap (`headunit://exit`, then a
force-stop) before P1.

## 1. Build and baseline

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/fix/929-self-mode-media-session` | `6d4de0d0bb1ddb5f057616115ce72ccf72bb427b` | `c41a0a2ca743949ae006e5504d3008da63301e5b` |

History was rewritten three times since round 2: a rebase onto `main` `28af450d` on 2026-10-17 (hand-resolved), a
clean rebase onto `main` `e9377ff9` on 2026-10-22, and a hand-resolved rebase onto `main` `947b3ae9` on 2026-10-26.
There is no baseline arm. Round 2's controls (A1c, B1c, C1c) settled what the old build does.

The dev host ran the JVM suite at `6d4de0d0`: **4257 tests, 0 failures**. The host-connection harness passed.
Do not run the unit tests on the rig.

`second-screen-outputs-round3-brief.md` also builds on `main` `947b3ae9`. If the same session runs it, build both
in this one worktree, this candidate first.

The host builds C (rig-round skill, Prepare) and puts the APK at `$EV/apk/C.apk`:

```text
git fetch fork fix/929-self-mode-media-session
WT=$($RIG_TOOLKIT/wt-new.sh self-mode-media-session candidate 6d4de0d0bb1ddb5f057616115ce72ccf72bb427b | tail -1)
git -C $WT rev-parse 'HEAD^{tree}'               # MUST print c41a0a2ca743949ae006e5504d3008da63301e5b
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh    # copy the APK out of rig-data/apks/ to $EV/apk/C.apk at once
```

The `prep` run installs it with `adb install -r -d` on D-HU and on D-POCO. Never uninstall. Identity, after the
install and at the start of every run (`apk_check` in section 5):

- the pulled APK's md5 equals `$EV/apk/C.apk`'s;
- `ACTION_QUERY_STATE`: the `data=` line carries commit `6d4de0d0` (a `-dirty` suffix is fine; `prep` only);
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

- **Self Mode needs D-POCO's Android Auto head unit server on `:5277`.** The operator confirms it at the start, and
  P1 and P2 read it back (`:149D` in `/proc/net/tcp*`); with none, the run is UNTESTABLE. **Never force-stop
  `com.google.android.projection.gearhead` in this round.**
- **The Gearhead wedge costs one operator round trip.** If a Self Mode attempt's capture holds
  `the peer accepted the connection and then sent nothing at all`, the server is wedged. The script prints an
  OPERATOR line (turn "Start head unit server" off and on once), gives the operator 90 s, and tries once more.
- **H1 and H2 need the legacy Self Mode path on D-HU** (round 2 read Gearhead 17.3 there, `SelfMode: AA < 17.4
  detected.`). If D-HU now takes the 17.4+ path, H1 still runs (the launch fails instead of timing out) and H2
  is UNTESTABLE.
- **H2 is a race against a 30 s deadline.** The Native session must form before the legacy launch times out. If
  the timeout line comes first, the script tries once more. Two lost races make H2 INCONCLUSIVE, not FAIL.
- **D-POCO changes role between stage H and stage P.** `check --fix` does the swap. `cleanup` sends
  `headunit://exit` to D-POCO's app at the end.
- **D-POCO's WiFi turns itself back on** after one `svc wifi disable` (`units/D-POCO.md`). H1 and H2 hold it off
  with `rl_hold_off` until the step that brings the phone back.
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

`prep` backs up both units' `settings.xml`, and `cleanup` restores and diffs them. State the delta in Setup notes.
`prep` records the owner and mode of D-HU's `shared_prefs/`. Leave the audio keys (`use-aac-audio`,
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
| delete `video-profile-starvation-cap` | `prep` records its value first; the cap forces AAC and 720p |
| `<boolean name="auto-start-self-mode" value="false" />` | Self Mode starts only by its verb |

**D-POCO, every P run** (`PHKEYS`): the same twelve, plus
`<boolean name="native-poke-all-paired" value="false" />` so the pre-Self Mode stack pokes no bonded speaker.
P2 also writes `<boolean name="static-audio-focus" value="true" />` and puts it back to `false` after.

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at `6d4de0d0`, so
`allow-external-configuration` is not needed.

## 5. Helpers

Every script runs through `rig_run.sh`, with `rig_lib.sh` and this `lib.sh` loaded. Outputs go to `$EV`. Script
exit codes: 9 no session, 11 APK identity, 12 host hot, 13 no `:5277` on D-POCO, 14 Gearhead wedge, 15 a radio
did not read back.

**lib.sh**

```bash
. "$RIG_TOOLKIT/rig_devices.sh"
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
HU=$D_HU; PH=$D_POCO; MOTO=$D_MOTO; SP=com.spotify.music
APK=$EV/apk/C.apk
nm() { case $1 in "$HU") echo hu ;; "$PH") echo ph ;; esac; }
sendto() { local s=$1 a=$2; shift 2; adb -s "$s" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
# mark SERIAL LABEL: the app's marker; in stage H also a tag line in the phone capture
mark() { sendto "$1" ACTION_LOG_MARKER --es text "$2" >/dev/null
  if [ "$1" = "$HU" ]; then adb -s "$PH" shell log -t AutomationMarker "$2"; fi; sleep 0.3; }
launch() { adb -s "$1" shell am start -n $MAIN >/dev/null; sleep 2; }
# appexit: only while the app runs (an exit on a stopped app starts the service)
appexit() { if [ -n "$(adb -s "$1" shell pidof $PKG | tr -d '\r')" ]; then
  adb -s "$1" shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5; fi
  adb -s "$1" shell am force-stop $PKG; sleep 1; }
logclear() { adb -s "$1" logcat -G 16M >/dev/null 2>&1; adb -s "$1" logcat -c; }
# slow_step CAP TEXT: a long step rig_run.sh must not call a stall (installs, the thermal gate)
slow_step() { rl_step "WAIT cap=$1s $2"; }
thermal() { slow_step 1260 "host thermal gate"; rl_thermal_gate; }
server5277() { adb -s "$PH" shell cat /proc/net/tcp6 /proc/net/tcp | grep -aci ':149D'; }

# settings, with the app stopped (rig-sdlc/rig-env/TOOLS.md)
COMMON="scalar:int:wifi-connection-mode:3 scalar:int:log-level:1 del:log-source scalar:int:onboarding-version:2 scalar:int:native-driver-selection-mode:0 scalar:boolean:enable-audio-sink:true scalar:boolean:gps-navigation:true scalar:boolean:static-audio-focus:false scalar:boolean:playback-focus-self-defeating:false del:media-key-routing del:video-profile-starvation-cap scalar:boolean:auto-start-self-mode:false"
HUKEYS="$COMMON"; PHKEYS="$COMMON scalar:boolean:native-poke-all-paired:false"
hu_keys() { python3 -I "$RIG_TOOLKIT/set_hu_settings_host.py" "$HU" $HUKEYS "$@" >> "$EV/keys.log" 2>&1; }
ph_keys() { python3 -I "$RIG_TOOLKIT/set_prefs_runas_host.py" "$PH" "$@" >> "$EV/keys.log" 2>&1; }
KEYS_RE='name="(wifi-connection-mode|log-level|log-source|onboarding-version|native-driver-selection-mode|enable-audio-sink|gps-navigation|static-audio-focus|playback-focus-self-defeating|media-key-routing|video-profile-starvation-cap|auto-start-self-mode|native-poke-all-paired)"[^/]*'
keys_read() { adb -s "$1" shell run-as $PKG cat shared_prefs/settings.xml > "$EV/settings-$2.xml"; grep -aoE "$KEYS_RE" "$EV/settings-$2.xml" | tee -a "$EV/keys.log"; }

# APK identity on one unit: md5 and DEX symbols
apk_check() { local s=$1 f="$EV/live-$(nm "$1").apk" m w a b
  adb -s "$s" pull "$(adb -s "$s" shell pm path $PKG | cut -d: -f2 | tr -d '\r')" "$f" >/dev/null 2>&1
  m=$(md5sum "$f" 2>/dev/null | cut -d' ' -f1); w=$(md5sum "$APK" 2>/dev/null | cut -d' ' -f1)
  a=$(unzip -p "$f" 'classes*.dex' 2>/dev/null | strings | grep -c 'SelfModeWirelessPausePolicy')
  b=$(unzip -p "$f" 'classes*.dex' 2>/dev/null | strings | grep -c 'MediaSessionOwnershipPolicy')   # not-literal: absent on C by design
  echo "$(date +%T) ${RUN:-?} $(nm "$s") live=$m want=$w pause_policy=$a ownership_policy=$b" >> "$EV/apk-check.log"
  if [ -n "$m" ] && [ "$m" = "$w" ] && [ "$a" -ge 1 ] && [ "$b" -eq 0 ]; then echo "APK_OK ${RUN:-?} $(nm "$s")" >> "$EV/apk-check.log"; return 0; fi
  echo "APK_MISMATCH ${RUN:-?} $(nm "$s")" >> "$EV/apk-check.log"; return 1; }

# Spotify on D-POCO: its PlaybackState number (3 playing, 2 paused), or none. These keys go to Spotify, not to the app.
sp_state() { adb -s "$PH" shell dumpsys media_session | awk -v p="package=$SP" 'index($0,p){f=1} f && /state=PlaybackState/{match($0,/state=[0-9]+/); print substr($0,RSTART+6,RLENGTH-6); d=1; exit} END{if(!d) print "none"}'; }
sp_play() { local s; adb -s "$PH" shell am start -a android.intent.action.VIEW -d spotify:collection:tracks >/dev/null; sleep 6
  adb -s "$PH" shell input keyevent 126; sleep 5; s=$(sp_state)
  if [ "$s" != 3 ]; then adb -s "$PH" shell input keyevent 85; sleep 5; s=$(sp_state); fi
  if [ "$s" != 3 ]; then echo "OPERATOR NOW: start any track in Spotify on D-POCO, then leave it playing" >&2; sleep 45; s=$(sp_state); fi
  adb -s "$PH" shell input keyevent KEYCODE_HOME; echo "spotify_state=$s"; }

# grading: windows are marker to marker (rl_win)
cntE() { rl_win "$1" "$2" "$3" | grep -acE -- "$4"; }
# after FILE FROM TO ANCHOR STRING: count STRING in FROM..TO after the first line that holds ANCHOR
after() { rl_win "$1" "$2" "$3" | awk -v a="$4" -v s="$5" 'f && index($0,s){n++} !f && index($0,a){f=1} END{print n+0}'; }
# gap FILE FROM TO ANCHOR STRING: seconds from the first ANCHOR to the first STRING after it, or none
gap() { rl_win "$1" "$2" "$3" | awk -v a="$4" -v s="$5" 'function t(l){split(l,x," ");split(x[2],h,":");return h[1]*3600+h[2]*60+h[3]}
  !f && index($0,a){f=1;t0=t($0);next} f && index($0,s){printf "%.1f\n",t($0)-t0;d=1;exit} END{if(!d)print "none"}'; }
NFAIL=0; INC=""
c() { local o; o=$(rl_cond "$@"); echo "$o"; case "$o" in "COND $1 FAIL"*) NFAIL=$((NFAIL+1)) ;; esac; }
inc() { INC="$INC${INC:+; }$*"; }
fin() { if [ -n "$INC" ] && [ "$NFAIL" -eq 0 ]; then rl_verdict INCONCLUSIVE "$INC"; fi; }
# early: the script's own exit codes; called last in a grade, so its VERDICT wins
early() { case "${RUN_EXIT:-0}" in 11) rl_verdict UNTESTABLE "APK identity mismatch" ;; 12) rl_verdict UNTESTABLE "host thermal" ;;
  13) rl_verdict UNTESTABLE "no :5277 listener on D-POCO" ;; 15) rl_verdict INCONCLUSIVE "a D-POCO radio did not read back" ;; esac; }
```

`c` is `rl_cond` that also counts FAILs. A check the prose calls INCONCLUSIVE is passed to `inc` and prints no
COND; `fin` then makes the run INCONCLUSIVE only when no other condition failed, so a FAIL is never hidden.
`early` turns exit 11, 12 and 13 into UNTESTABLE and exit 15 into INCONCLUSIVE.

## 6. The deciding lines

Every app string was checked with `git grep -F` in `app/src/main` at `6d4de0d0`. All print at INFO except
`MediaSession: State updated to`, which is DEBUG. Lines with a run-time value are shown in their run-time form
here; the scripts grep a literal fragment of them, or mark the line `# not-literal`.

**App lines** (`$EV/<RUN>-hu.txt` in stage H, `$EV/<RUN>-ph.txt` in stage P):

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
| `SSL handshake complete. Session id: ...` or `SSL handshake complete. No session id (full handshake).` | one session (INFO form; the DEBUG `Handshake: SSL handshake complete. TS:` is not counted) |
| `Hiding loading overlay after first video frame` | the picture is up (the scripts wait on it) |
| `Throughput over <n>ms: rendered=<n> ...` | the picture (graded when rendered is 1 or more) |
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

**Android Auto lines** (`$EV/<RUN>-ph.txt` in both stages). They are Gearhead's and drift between builds.
`prep` records the Gearhead version on both units. A 0 on `Head unit connected` or `Head unit disconnected` means
"string not seen", not FAIL. Both were read in the Gearhead 18.0 decompile (`DeveloperHeadUnitNetworkService`).

| Line | Meaning |
|---|---|
| `GH.` | capture gate: the phone capture works |
| `Critical error` | Android Auto ended a session on a protocol fault |
| `Head unit connected` / `Head unit disconnected` | D-POCO's head unit server took and lost our loopback socket (Self Mode, 17.4+) |

## 7. Runs

### prep. Records, backups, installs

Reads what the round needs and installs C on both units. `check --fix` already did the radios, the bond and the
group owner check, so this run does not repeat them.

**prep.sh**

```bash
rl_step "records"
for s in "$HU" "$PH"; do
  echo "$(nm "$s") $(adb -s "$s" shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName | tr -d '\r')" >> "$EV/gearhead.txt"
done
adb -s "$PH" shell input keyevent KEYCODE_HOME
adb -s "$PH" shell dumpsys window | grep -a mCurrentFocus > "$EV/prep-poco-focus.txt"
echo "spotify_installed=$(adb -s "$PH" shell pm list packages | grep -c "$SP")" > "$EV/prep-poco.txt"
echo "server5277=$(server5277)" >> "$EV/prep-poco.txt"
rl_step "backups"
for s in "$HU" "$PH"; do
  adb -s "$s" shell am force-stop $PKG
  adb -s "$s" shell run-as $PKG cat shared_prefs/settings.xml > "$EV/settings-backup-$(nm "$s").xml"
  grep -aoE 'name="(native-poke-bt-macs|native-poke-all-paired|connection-modes|video-profile-starvation-cap)"[^/]*' "$EV/settings-backup-$(nm "$s").xml" > "$EV/prep-keys-$(nm "$s").txt"
done
adb -s "$HU" shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs > "$EV/prep-hu-prefs-stat.txt"
for s in "$HU" "$PH"; do
  slow_step 600 "install and identity on $(nm "$s")"
  adb -s "$s" install -r -d "$APK" > "$EV/prep-install-$(nm "$s").txt" 2>&1
  apk_check "$s"
  launch "$s"; sendto "$s" ACTION_QUERY_STATE > "$EV/prep-query-$(nm "$s").txt"; appexit "$s"
done
```

**grade-prep.sh**

```bash
c 1 eq "$(grep -ac 'APK_OK prep hu' "$EV/apk-check.log")" 1 "D-HU runs C (md5 and DEX)"   # not-literal: this brief's own file
c 2 eq "$(grep -ac 'APK_OK prep ph' "$EV/apk-check.log")" 1 "D-POCO runs C (md5 and DEX)"   # not-literal
c 3 ge "$(grep -ac '6d4de0d0' "$EV/prep-query-hu.txt")" 1 "D-HU ACTION_QUERY_STATE names 6d4de0d0"   # not-literal: the am reply
c 4 ge "$(grep -ac '6d4de0d0' "$EV/prep-query-ph.txt")" 1 "D-POCO ACTION_QUERY_STATE names 6d4de0d0"   # not-literal
```

A FAIL here stops the round (`stop prep`). `spotify_installed=0` makes K1 and P2's player check INCONCLUSIVE.

### Stage H: D-HU is the head unit, D-POCO is the phone

### H1. Self Mode fails on D-HU, then an ordinary session in the same process

Phone capture: yes. Checks C3 (the stack comes back after a failed or timed-out launch), A3 (an ordinary
session still takes focus through the leased path), B2 (it still offers GPS), K2 (it still sends media keys)
and D1 (it still holds an active media session after a Self Mode attempt in the same process).

**h1.sh**

```bash
F="$EV/H1-hu.txt"
thermal || exit 12
apk_check "$HU" || exit 11
rl_step "phone off, keys"
adb -s "$PH" shell am force-stop $PKG
adb -s "$HU" shell am force-stop $PKG; sleep 1
rl_hold_off "$PH" wifi 3 || exit 15
rl_radio "$PH" bt off || exit 15
hu_keys; keys_read "$HU" H1-pre
logclear "$HU"; logclear "$PH"
rl_cap_start H1-hu "$HU"; rl_cap_start H1-ph "$PH"
launch "$HU"; mark "$HU" H1-launch
rl_wait_line "$F" 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 60
rl_sleep 5
O=$(rl_offset "$F"); mark "$HU" H1-self; sendto "$HU" ACTION_START_SELF_MODE > "$EV/H1-self-reply.txt"
rl_wait_line "$F" '; letting the wireless stack arm again' 60 "$O"
O=$(rl_offset "$F"); rl_wait_line "$F" 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 30 "$O"
O=$(rl_offset "$F"); mark "$HU" H1-phone
rl_release "$PH" wifi; rl_radio "$PH" bt on; rl_radio "$PH" wifi on
if ! rl_wait_line "$F" 'SSL handshake complete. ' 120 "$O"; then
  mark "$HU" H1-end; mark "$HU" H1-closed; rl_cap_stop; appexit "$HU"; exit 9
fi
if ! rl_wait_line "$F" 'Hiding loading overlay after first video frame' 40 "$O"; then
  sendto "$HU" ACTION_RAISE_PROJECTION >/dev/null; echo RAISE_VERB_USED >> "$EV/H1-notes.txt"
  rl_wait_line "$F" 'Hiding loading overlay after first video frame' 30 "$O"
fi
mark "$HU" H1-up; rl_sleep 10
O=$(rl_offset "$F"); mark "$HU" H1-k; adb -s "$HU" shell input keyevent 126
if ! rl_wait_line "$F" 'Media Start Request AUDIO:' 12 "$O"; then   # not-literal: "Media Start Request %s: session"
  adb -s "$HU" exec-out screencap -p > "$EV/H1-pretap.png"; sleep 0.3
  adb -s "$HU" shell input tap 272 657; echo "TAP_USED 272 657" >> "$EV/H1-taps.txt"
  rl_wait_line "$F" 'Media Start Request AUDIO:' 15 "$O"   # not-literal
fi
mark "$HU" H1-a; rl_sleep 20
adb -s "$HU" shell dumpsys media_session > "$EV/H1-media-session.txt"
mark "$HU" H1-end; sendto "$HU" ACTION_DISCONNECT >/dev/null; rl_sleep 5; mark "$HU" H1-closed
rl_cap_stop; appexit "$HU"
```

The tap is the one standing exception (a tap into the projected video goes to the phone over the touch
channel, `rig-quirks/topics/audio.md`). It runs only if the key did not start the media channel. Record it.

**grade-h1.sh**

```bash
F="$EV/H1-hu.txt"; P="$EV/H1-ph.txt"; A='; letting the wireless stack arm again'
MS=$(awk '/package=com.andrerinas.headunitrevived/{f=1} f && /active=/{print ($0 ~ /active=true/)?1:0; exit}' "$EV/H1-media-session.txt" 2>/dev/null)
verbs=$(rl_win "$F" H1-launch H1-closed | grep -acF 'ACTION_START_SELF_MODE')
gh=$(grep -acF 'GH.' "$P")   # phone
standdown=$(rl_cnt "$F" H1-self H1-phone 'SelfMode: standing the wireless stack down; a loopback session needs no network')
rearm=$(rl_cnt "$F" H1-self H1-phone "$A")
init_after=$(after "$F" H1-self H1-phone "$A" 'WifiLauncher: Initializing WiFi Mode: NATIVE')
listen_after=$(after "$F" H1-self H1-phone "$A" 'NativeAA: ACTIVELY LISTENING on Android Auto UUID')
ssl=$(( $(rl_cnt "$F" H1-phone H1-end 'SSL handshake complete. Session id') + $(rl_cnt "$F" H1-phone H1-end 'SSL handshake complete. No session id') ))
rend=$(cntE "$F" H1-phone H1-end 'Throughput over [0-9]+ms: rendered=[1-9]')
aud=$(rl_cnt "$F" H1-phone H1-end 'Media Start Request AUDIO:')   # not-literal
grant=$(cntE "$F" H1-phone H1-end 'focus request result( \(legacy\))?: GRANTED')
decl=$(rl_cnt "$F" H1-launch H1-closed 'leaving system audio focus alone')
nofocus=$(rl_cnt "$F" H1-launch H1-closed 'AapAudio: playback without system focus (')
loc=$(rl_cnt "$F" H1-phone H1-end 'LOCATION sensor requested.')
night=$(rl_cnt "$F" H1-phone H1-end 'Night sensor requested.')
gpsw=$(rl_cnt "$F" H1-phone H1-end 'sensor is not announced - this is not a fault')
tx126=$(rl_cnt "$F" H1-k H1-end 'CommManager: TX Key -> AA=126')   # not-literal: "AA=$logicalCode"
notsent=$(rl_cnt "$F" H1-launch H1-closed 'CommManager: Not sending media key')
playing=$(rl_cnt "$F" H1-up H1-end 'MediaSession: State updated to PLAYING')   # not-literal: "State updated to $state"
crit=$(rl_cnt "$P" H1-phone H1-end 'Critical error')   # phone
{ echo "H1 legacy=$(rl_cnt "$F" H1-self H1-phone 'SelfMode: AA < 17.4 detected.') server=$(rl_cnt "$F" H1-self H1-phone 'SelfMode: AA 17.4+ detected.')"
  echo "rearm_s=$(gap "$F" H1-self H1-phone 'SelfMode: standing the wireless stack down' "$A") refused=$(rl_cnt "$F" H1-self H1-phone 'WifiLauncher: wireless bring-up requested while Self Mode is running.')"
  echo "aud=$aud playing=$playing taps=$(wc -l < "$EV/H1-taps.txt" 2>/dev/null || echo 0) crit=$crit"; } > "$EV/H1-record.txt"
if [ "${verbs:-0}" -eq 0 ] || [ "${gh:-0}" -eq 0 ]; then rl_verdict INCONCLUSIVE "void: verbs=$verbs gh=$gh; rerun H1 once"; else
  c C3.1 eq "$standdown" 1 "C3 the stand-down printed once"
  c C3.2 eq "$rearm" 1 "C3 the failed launch gave the stack back"
  c C3.3 ge "$init_after" 1 "C3 the stack armed after the give-back"
  c C3.4 ge "$listen_after" 1 "C3 the listeners opened after the give-back"
  c C3.5 ge "$ssl" 1 "C3 an ordinary session formed"
  if [ "$ssl" -eq 0 ] || [ "${rend:-0}" -eq 0 ]; then inc "session gate: ssl=$ssl rend=$rend, so A3 B2 K2 D1 untested (rig)"; else
    c X1 eq "$crit" 0 "the phone logged no Critical error"
    if [ "$aud" -eq 0 ]; then inc "A3: no media channel started"; else
      c A3.1 ge "$grant" 1 "A3 focus granted through the leased path"
      c A3.2 eq "$decl" 0 "A3 no decline line"
      c A3.3 eq "$nofocus" 0 "A3 no playback-without-focus line"
    fi
    if [ "$loc" -eq 0 ] && [ "$night" -eq 0 ]; then inc "B2: the phone asked for no sensor"; else
      c B2.1 ge "$loc" 1 "B2 the phone asked for LOCATION"
      c B2.2 eq "$gpsw" 0 "B2 GPS was not withheld"
    fi
    c K2.1 ge "$tx126" 1 "K2 key 126 sent to Android Auto"
    c K2.2 eq "$notsent" 0 "K2 no key left to the system"
    c D1 eq "${MS:-none}" 1 "D1 our media session is active"
  fi
  fin
fi
early
```

- **Void:** `verbs` = 0, or `gh` = 0 (the phone capture did not work). Rerun once (the host deletes `H1.grade.json`).
- **C3 PASS:** `standdown` = 1, `rearm` = 1, `init_after` >= 1, `listen_after` >= 1, and `ssl` >= 1. **FAIL:**
  `standdown` = 1 with `rearm` = 0 (Self Mode failed and cost the unit its wireless), or `rearm` = 1 with
  `listen_after` = 0.
- **Session gate:** `ssl` = 0 or `rend` = 0 makes A3, B2, K2 and D1 INCONCLUSIVE (rig), not FAIL.
- **A3 PASS:** `grant` >= 1, `decl` = 0 and `nofocus` = 0. **INCONCLUSIVE:** `aud` = 0 (no media channel ever
  started, so focus was never asked).
- **B2 PASS:** `loc` >= 1 and `gpsw` = 0. **INCONCLUSIVE:** `loc` = 0 and `night` = 0 (the phone asked for no sensor).
- **K2 PASS:** `tx126` >= 1 and `notsent` = 0.
- **D1 PASS:** `ms_active` = 1. `playing` is in `H1-record.txt` (it needs music, so 0 is not a FAIL when `aud` = 0).
  A PASS on D1 with the change doing nothing looks the same, so D1 is a regression guard only.
- Phone: `crit` MUST be 0 for A3, B2, K2 and D1 to PASS.

### H2. A Native session wins over a waiting Self Mode launch (THE POINT, with P1)

Phone capture: yes. Runs only if H1 did not read the 17.4+ path. Else H2 is UNTESTABLE ("D-HU takes the 17.4+
path, which has no waiting launch").

The user starts Self Mode on D-HU, then presses the WiFi button. The phone connects over Native AA before the
legacy launch times out. The app then retires the Self Mode launch (`stopIfCurrent`) and re-arms the wireless
stack, which already carries the live session. The re-arm must do nothing. A lost race (the timeout line first)
gets a second attempt, `H2b`, in the same run.

**h2.sh**

```bash
if [ "$(grep -acF 'SelfMode: AA 17.4+ detected.' "$EV/H1-hu.txt")" -ge 1 ]; then echo "17.4+ path in H1" > "$EV/H2-skip.txt"; exit 0; fi
thermal || exit 12
apk_check "$HU" || exit 11
: > "$EV/H2-attempts.txt"
h2_try() { local T=$1 F="$EV/$1-hu.txt" O
  adb -s "$PH" shell am force-stop $PKG
  adb -s "$HU" shell am force-stop $PKG; sleep 1
  rl_hold_off "$PH" wifi 3 || return 15
  rl_radio "$PH" bt off || return 15
  hu_keys; keys_read "$HU" "$T-pre"
  logclear "$HU"; logclear "$PH"
  rl_cap_start "$T-hu" "$HU"; rl_cap_start "$T-ph" "$PH"
  launch "$HU"; mark "$HU" "$T-launch"
  rl_wait_line "$F" 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 60
  rl_sleep 5
  O=$(rl_offset "$F"); mark "$HU" "$T-self"; sendto "$HU" ACTION_START_SELF_MODE > "$EV/$T-self-reply.txt"
  rl_wait_line "$F" 'SelfMode: standing the wireless stack down' 10 "$O"
  rl_release "$PH" wifi; rl_radio "$PH" bt on; rl_radio "$PH" wifi on   # the phone arrives while Self Mode holds the stack
  O=$(rl_offset "$F"); mark "$HU" "$T-scan"; sendto "$HU" ACTION_START_WIRELESS_SCAN > "$EV/$T-scan-reply.txt"
  if ! rl_wait_line "$F" 'SSL handshake complete. ' 90 "$O"; then
    mark "$HU" "$T-end"; mark "$HU" "$T-closed"; rl_cap_stop; appexit "$HU"; return 9
  fi
  mark "$HU" "$T-ssl"; rl_sleep 30; mark "$HU" "$T-check"
  mark "$HU" "$T-end"; sendto "$HU" ACTION_DISCONNECT >/dev/null; rl_sleep 5; mark "$HU" "$T-closed"
  rl_cap_stop; appexit "$HU"; return 0; }
for T in H2a H2b; do
  h2_try "$T"; rc=$?
  echo "$T $rc" >> "$EV/H2-attempts.txt"
  [ "$rc" -eq 0 ] || break
  [ "$(rl_cnt "$EV/$T-hu.txt" "$T-self" "$T-check" 'the Self Mode launch timed out')" -ge 1 ] || break
done
```

**grade-h2.sh**

```bash
S='Self Mode was stopped; letting the wireless stack arm again'
if [ -f "$EV/H2-skip.txt" ]; then rl_verdict UNTESTABLE "D-HU takes the 17.4+ path, which has no waiting launch"; else
read -r T RC < <(tail -n 1 "$EV/H2-attempts.txt" 2>/dev/null)
T=${T:-H2a}; RC=${RC:-9}; F="$EV/$T-hu.txt"; P="$EV/$T-ph.txt"
for t in H2a H2b; do [ -f "$EV/$t-hu.txt" ] && echo "$t ssl_s=$(gap "$EV/$t-hu.txt" "$t-self" "$t-check" 'SelfMode: standing the wireless stack down' 'SSL handshake complete. ')" \
  "timedout=$(rl_cnt "$EV/$t-hu.txt" "$t-self" "$t-check" 'the Self Mode launch timed out')" \
  "refused=$(rl_cnt "$EV/$t-hu.txt" "$t-self" "$t-check" 'WifiLauncher: wireless bring-up requested while Self Mode is running.')"; done > "$EV/H2-record.txt"
legacy=$(rl_cnt "$F" "$T-self" "$T-scan" 'SelfMode: AA < 17.4 detected.')
timedout=$(rl_cnt "$F" "$T-self" "$T-check" 'the Self Mode launch timed out')
stopped=$(rl_cnt "$F" "$T-self" "$T-check" "$S")
stopline=$(rl_cnt "$F" "$T-self" "$T-check" 'SelfMode: stopping Self Mode (wasConnected=true')   # not-literal: "wasConnected=$wasConnected"
same_cfg=$(after "$F" "$T-self" "$T-check" "$S" '.mode with same start-configuration is already initialized.')
reinit=$(after "$F" "$T-self" "$T-check" "$S" 'WifiLauncher: Initializing WiFi Mode:')
quit=$(rl_cnt "$F" "$T-ssl" "$T-check" 'AapTransport quitting (clean=')
ssl_n=$(( $(rl_cnt "$F" "$T-scan" "$T-check" 'SSL handshake complete. Session id') + $(rl_cnt "$F" "$T-scan" "$T-check" 'SSL handshake complete. No session id') ))
rend_after=$(cntE "$F" "$T-ssl" "$T-check" 'Throughput over [0-9]+ms: rendered=[1-9]')
gh=$(grep -acF 'GH.' "$P")   # phone
crit=$(rl_cnt "$P" "$T-scan" "$T-check" 'Critical error')   # phone
if [ "$RC" -ne 0 ]; then rl_verdict INCONCLUSIVE "attempt $T: no session formed (exit $RC)"
elif [ "${legacy:-0}" -eq 0 ]; then rl_verdict UNTESTABLE "no legacy Self Mode line in $T"
elif [ "${timedout:-0}" -ge 1 ]; then rl_verdict INCONCLUSIVE "race lost on every attempt, last $T"
elif [ "${gh:-0}" -eq 0 ]; then rl_verdict INCONCLUSIVE "void: the phone capture is empty"
else
  c 1 eq "$stopped" 1 "stopIfCurrent gave the stack back once"
  c 2 ge "$stopline" 1 "the stop line printed"
  c 3 ge "$same_cfg" 1 "the re-arm found the stack already up"
  c 4 eq "$reinit" 0 "the re-arm did not reinitialise"
  c 5 eq "$quit" 0 "the session did not quit"
  c 6 eq "$ssl_n" 1 "one session"
  c 7 ge "$rend_after" 2 "the picture ran on after SSL"
  c 8 eq "$crit" 0 "the phone logged no Critical error"
fi
fi
early
```

- **UNTESTABLE:** H1 read the 17.4+ path, or the attempt has no `legacy` line.
- **INCONCLUSIVE (race lost):** `timedout` >= 1 on both attempts. The deadline fired before the session, so
  `stopIfCurrent` did not run its re-arm. `H2-record.txt` holds both `ssl_s` values. Also INCONCLUSIVE: no
  session formed (`exit 9`).
- **PASS:** `timedout` = 0, `stopped` = 1, `stopline` >= 1, `same_cfg` >= 1, `reinit` = 0, `quit` = 0,
  `ssl_n` = 1, `rend_after` >= 2 and `crit` = 0. Any other value of these is a FAIL: `stopped` = 0 (the stand-down
  debt leaked into the session), `reinit` >= 1 or `quit` >= 1 or `ssl_n` >= 2 (the re-arm tore the session down).
- Report `refused` (the Bluetooth auto-start that the stand-down refused). A count of 0 is not a FAIL: the
  phone may not raise an auto-start here.

What a PASS would look like if the change did nothing: on `main` there is no stand-down, so no `stopped` line.
`stopped` = 1 is the line only this candidate prints.

### Stage P: D-POCO alone, Self Mode

D-POCO becomes the head unit, and D-HU takes no part. Before P1, `check --fix` turns D-HU's Bluetooth off (so
D-POCO's stack finds no D-HU) and swaps D-POCO's role. D-POCO's capture `$EV/<RUN>-ph.txt` carries the app's lines
and Android Auto's lines, so it is the phone capture too.

Both P runs make up to two attempts. After a Gearhead wedge (exit 14) the script prints the OPERATOR line from
section 3 and tries again 90 s later. After an attempt with no session and no wedge line (exit 9) it tries again
with the activity route in place of the verb (`am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE -p
$PKG`), as round 2 did. The grade reads the last attempt in `$EV/<RUN>-attempts.txt`.

### P1. Self Mode on D-POCO: keys, GPS, the stand-down and the give-back (THE POINT, with H2)

Checks K1 (a media key goes to the local player, not to Android Auto), C1 (no group and no poke while Self
Mode runs), B1 (the GPS sensor is not announced) and C2 (the stack comes back when the session ends).

**p1.sh**

```bash
thermal || exit 12
apk_check "$PH" || exit 11
[ "$(server5277)" -ge 1 ] || exit 13
: > "$EV/P1-attempts.txt"
p1_try() { local T=$1 VIA=$2 F="$EV/$1-ph.txt" O
  adb -s "$PH" shell am force-stop $PKG; sleep 1
  ph_keys $PHKEYS; keys_read "$PH" "$T-pre"
  sp_play > "$EV/$T-sp-start.txt"
  logclear "$PH"; rl_cap_start "$T-ph" "$PH"
  launch "$PH"; mark "$PH" "$T-launch"
  rl_wait_line "$F" 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 60
  rl_sleep 10
  O=$(rl_offset "$F"); mark "$PH" "$T-self"
  if [ "$VIA" = verb ]; then sendto "$PH" ACTION_START_SELF_MODE > "$EV/$T-self-reply.txt"
  else adb -s "$PH" shell am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE -p $PKG > "$EV/$T-self-reply.txt"; fi
  if ! rl_wait_line "$F" 'SSL handshake complete. ' 60 "$O"; then
    mark "$PH" "$T-end"; rl_cap_stop; appexit "$PH"
    if [ "$(grep -acF 'the peer accepted the connection and then sent nothing at all' "$F")" -ge 1 ]; then return 14; fi
    return 9
  fi
  if ! rl_wait_line "$F" 'Hiding loading overlay after first video frame' 40 "$O"; then
    sendto "$PH" ACTION_RAISE_PROJECTION >/dev/null; echo "$T RAISE_VERB_USED" >> "$EV/P1-notes.txt"
    rl_wait_line "$F" 'Hiding loading overlay after first video frame' 30 "$O"
  fi
  mark "$PH" "$T-up"; rl_sleep 15
  adb -s "$PH" shell dumpsys window | grep -a mCurrentFocus > "$EV/$T-focus.txt"
  if ! grep -aq 'AapProjectionActivity' "$EV/$T-focus.txt"; then
    sendto "$PH" ACTION_RAISE_PROJECTION >/dev/null; sleep 3; adb -s "$PH" shell dumpsys window | grep -a mCurrentFocus >> "$EV/$T-focus.txt"
  fi
  echo "sp0=$(sp_state)" > "$EV/$T-sp.txt"
  mark "$PH" "$T-k"; adb -s "$PH" shell input keyevent 127; sleep 4; echo "sp1=$(sp_state)" >> "$EV/$T-sp.txt"
  adb -s "$PH" shell input keyevent 126; sleep 4; echo "sp2=$(sp_state)" >> "$EV/$T-sp.txt"
  adb -s "$PH" shell dumpsys media_session | grep -a 'Media button session is' >> "$EV/$T-sp.txt"   # system
  mark "$PH" "$T-k-end"; rl_sleep 60
  O=$(rl_offset "$F"); mark "$PH" "$T-disc"; sendto "$PH" ACTION_DISCONNECT >/dev/null
  rl_wait_line "$F" 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 40 "$O"; rl_sleep 3
  mark "$PH" "$T-end"; rl_cap_stop; appexit "$PH"; return 0; }
p1_try P1 verb; rc=$?; echo "P1 $rc" >> "$EV/P1-attempts.txt"
if [ "$rc" -eq 14 ]; then
  echo "OPERATOR NOW: on D-POCO, turn Android Auto's Start head unit server off and on once (developer settings)"
  rl_sleep 90; p1_try P1r verb; rc=$?; echo "P1r $rc" >> "$EV/P1-attempts.txt"
elif [ "$rc" -eq 9 ]; then
  p1_try P1r activity; rc=$?; echo "P1r $rc" >> "$EV/P1-attempts.txt"
fi
```

**grade-p1.sh**

```bash
read -r T RC < <(tail -n 1 "$EV/P1-attempts.txt" 2>/dev/null)
T=${T:-P1}; RC=${RC:-${RUN_EXIT:-9}}; F="$EV/$T-ph.txt"
SD='SelfMode: standing the wireless stack down'; END='the Self Mode session ended; letting the wireless stack arm again'
N127=$(rl_win "$F" "$T-k" "$T-k-end" | grep -aF 'CommManager: Not sending media key ')
ns127=$(echo "$N127" | grep -acF ' 127 to Android Auto')   # not-literal: "media key $logicalCode to Android Auto"
ns127_self=$(echo "$N127" | grep -aF ' 127 to Android Auto' | grep -acF 'selfMode=true')   # not-literal
spv() { sed -n "s/^$1=//p" "$EV/$T-sp.txt" 2>/dev/null | head -n 1; }
sp0=$(spv sp0); sp1=$(spv sp1)
mbs=$(grep -a 'Media button session is' "$EV/$T-sp.txt" 2>/dev/null)   # system
armed=$(rl_cnt "$F" "$T-launch" "$T-self" 'WifiLauncher: Initializing WiFi Mode: ')
gh=$(rl_cnt "$F" "$T-self" "$T-disc" 'GH.')   # phone
crit=$(rl_cnt "$F" "$T-self" "$T-disc" 'Critical error')   # phone
focus_ok=$(grep -ac 'AapProjectionActivity' "$EV/$T-focus.txt" 2>/dev/null)
tx=$(rl_cnt "$F" "$T-k" "$T-k-end" 'CommManager: TX Key -> AA=')
{ echo "P1 attempt=$T exit=$RC server=$(rl_cnt "$F" "$T-self" "$T-up" 'SelfMode: AA 17.4+ detected.')"
  echo "ns127=$ns127 ns127_self=$ns127_self tx=$tx focus_ok=$focus_ok $(tr '\n' ' ' < "$EV/$T-sp.txt" 2>/dev/null)"
  echo "wake=$(after "$F" "$T-self" "$T-disc" "$SD" 'MATCH! Starting AapService') refused=$(rl_cnt "$F" "$T-self" "$T-disc" 'WifiLauncher: wireless bring-up requested while Self Mode is running.')"
  echo "huc=$(rl_cnt "$F" "$T-self" "$T-up" 'Head unit connected') hud=$(rl_cnt "$F" "$T-disc" "$T-end" 'Head unit disconnected')"; } > "$EV/P1-record.txt"   # phone
if [ "$RC" -ne 0 ]; then rl_verdict INCONCLUSIVE "no session on $T (exit $RC; 14 is the Gearhead wedge)"
elif [ "${armed:-0}" -eq 0 ] || [ "${gh:-0}" -eq 0 ]; then rl_verdict INCONCLUSIVE "void: armed_before=$armed gh=$gh; check connection-modes and rerun P1 once"
else
  c X1 eq "$crit" 0 "the phone logged no Critical error"
  if [ "${focus_ok:-0}" -eq 0 ]; then inc "K1: the key never reached our window"
  elif [ "$sp0" != 3 ]; then inc "K1: no player playing (sp0=$sp0)"
  elif [ "$sp1" = 3 ] && [ "$tx" -eq 0 ] && [ -n "$mbs" ] && ! echo "$mbs" | grep -q 'com.andrerinas.headunitrevived'; then inc "K1: another package holds the media button session ($mbs)"
  else
    c K1.1 ge "$ns127" 1 "K1 key 127 left to the system"
    c K1.2 eq "$ns127_self" "$ns127" "K1 every left key says selfMode=true"
    c K1.3 eq "$tx" 0 "K1 no key sent to Android Auto"
    c K1.4 eq "$sp1" 2 "K1 the local player paused"
  fi
  c C1.1 eq "$(rl_cnt "$F" "$T-self" "$T-disc" "$SD")" 1 "C1 the stand-down printed once"
  c C1.2 eq "$(after "$F" "$T-self" "$T-disc" "$SD" 'createGroup SUCCESS')" 0 "C1 no group during Self Mode"
  c C1.3 eq "$(after "$F" "$T-self" "$T-disc" "$SD" 'Attempting active poke to device')" 0 "C1 no poke during Self Mode"
  c B1.1 ge "$(rl_cnt "$F" "$T-self" "$T-disc" 'sensor is not announced - this is not a fault')" 1 "B1 GPS withheld"
  c B1.2 eq "$(rl_cnt "$F" "$T-self" "$T-disc" 'LOCATION sensor requested.')" 0 "B1 the phone did not ask for LOCATION"
  c C2.1 eq "$(rl_cnt "$F" "$T-disc" "$T-end" "$END")" 1 "C2 the session end gave the stack back"
  c C2.2 ge "$(after "$F" "$T-disc" "$T-end" "$END" 'WifiLauncher: Initializing WiFi Mode: NATIVE')" 1 "C2 the stack armed again"
  c C2.3 ge "$(after "$F" "$T-disc" "$T-end" "$END" 'NativeAA: ACTIVELY LISTENING on Android Auto UUID')" 1 "C2 the listeners opened again"
  fin
fi
early
```

- **Void:** `armed_before` = 0 (the stack never armed, so C1's zero means nothing; check `connection-modes`).
  Rerun once after fixing the keys. Also void: `gh` = 0.
- **K1 PASS:** `focus_ok` >= 1, `ns127` >= 1, `ns127_self` = `ns127`, `tx` = 0, `sp0=3` and `sp1=2`.
  **INCONCLUSIVE:** `focus_ok` = 0 (the key never reached our window), or `sp0` is not 3 (no player), or `sp1=3`
  with `tx` = 0 while the media button session is another package (record which). `sp2` is in `P1-record.txt`.
  Any other result is a FAIL, which includes `sp0=3` and `sp1=3` while the `Media button session is` line names
  `com.andrerinas.headunitrevived` (the key reached nothing that plays).
- **C1 PASS:** `standdown` = 1, `grp` = 0 and `poke` = 0. `wake` is in the record only: a bonded speaker can raise
  an auto-start on its own. Round 2's control showed a group forming during Self Mode on a build without the
  stand-down. That is what a PASS would not show if the change did nothing.
- **B1 PASS:** `gpsw` >= 1 and `loc` = 0.
- **C2 PASS:** `rearm` = 1, `init_after` >= 1 and `listen_after` >= 1.
- Phone: `crit` MUST be 0 for K1, C1 and B1 to PASS. `huc` and `hud` are in the record (0 is "string not seen").

### P2. Static audio focus in Self Mode

Checks A2 under the moved gate: static mode's permanent grab still stands down, and the local player keeps
playing.

**p2.sh**

```bash
thermal || exit 12
apk_check "$PH" || exit 11
[ "$(server5277)" -ge 1 ] || exit 13
: > "$EV/P2-attempts.txt"
p2_try() { local T=$1 VIA=$2 F="$EV/$1-ph.txt" O
  adb -s "$PH" shell am force-stop $PKG; sleep 1
  ph_keys $PHKEYS scalar:boolean:static-audio-focus:true; keys_read "$PH" "$T-pre"   # static-audio-focus MUST read true
  sp_play > "$EV/$T-sp-start.txt"
  logclear "$PH"; rl_cap_start "$T-ph" "$PH"
  launch "$PH"; mark "$PH" "$T-launch"
  rl_wait_line "$F" 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 60
  rl_sleep 10
  O=$(rl_offset "$F"); mark "$PH" "$T-self"
  if [ "$VIA" = verb ]; then sendto "$PH" ACTION_START_SELF_MODE > "$EV/$T-self-reply.txt"
  else adb -s "$PH" shell am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE -p $PKG > "$EV/$T-self-reply.txt"; fi
  if ! rl_wait_line "$F" 'SSL handshake complete. ' 60 "$O"; then
    mark "$PH" "$T-end"; rl_cap_stop; appexit "$PH"
    if [ "$(grep -acF 'the peer accepted the connection and then sent nothing at all' "$F")" -ge 1 ]; then return 14; fi
    return 9
  fi
  echo "sp_before=$(sp_state)" > "$EV/$T-sp.txt"
  mark "$PH" "$T-up"; rl_sleep 40; echo "sp_after=$(sp_state)" >> "$EV/$T-sp.txt"
  O=$(rl_offset "$F"); mark "$PH" "$T-disc"; sendto "$PH" ACTION_DISCONNECT >/dev/null
  rl_wait_line "$F" 'NativeAA: ACTIVELY LISTENING on Android Auto UUID' 40 "$O"; rl_sleep 3
  mark "$PH" "$T-end"; rl_cap_stop; appexit "$PH"; return 0; }
p2_try P2 verb; rc=$?; echo "P2 $rc" >> "$EV/P2-attempts.txt"
if [ "$rc" -eq 14 ]; then
  echo "OPERATOR NOW: on D-POCO, turn Android Auto's Start head unit server off and on once (developer settings)"
  rl_sleep 90; p2_try P2r verb; rc=$?; echo "P2r $rc" >> "$EV/P2-attempts.txt"
elif [ "$rc" -eq 9 ]; then
  p2_try P2r activity; rc=$?; echo "P2r $rc" >> "$EV/P2-attempts.txt"
fi
ph_keys scalar:boolean:static-audio-focus:false; keys_read "$PH" P2-post   # MUST read false
```

**grade-p2.sh**

```bash
read -r T RC < <(tail -n 1 "$EV/P2-attempts.txt" 2>/dev/null)
T=${T:-P2}; RC=${RC:-${RUN_EXIT:-9}}; F="$EV/$T-ph.txt"
spv() { sed -n "s/^$1=//p" "$EV/$T-sp.txt" 2>/dev/null | head -n 1; }
sb=$(spv sp_before); sa=$(spv sp_after)
ssl=$(( $(rl_cnt "$F" "$T-self" "$T-disc" 'SSL handshake complete. Session id') + $(rl_cnt "$F" "$T-self" "$T-disc" 'SSL handshake complete. No session id') ))
echo "P2 attempt=$T exit=$RC ssl=$ssl sp_before=$sb sp_after=$sa" > "$EV/P2-record.txt"
if [ "$RC" -ne 0 ] || [ "$ssl" -eq 0 ]; then rl_verdict INCONCLUSIVE "no session on $T (exit $RC; 14 is the Gearhead wedge)"
else
  c 1 ge "$(rl_win "$F" "$T-self" "$T-disc" | grep -aF 'CommManager: Static Audio Focus - leaving system audio focus alone' | grep -acF 'selfMode=true')" 1 "the static grab stood down in Self Mode"   # not-literal: "selfMode=$selfMode"
  c 2 eq "$(cntE "$F" "$T-launch" "$T-end" 'focus request result( \(legacy\))?: ')" 0 "no focus request at all"
  c 3 eq "$(rl_cnt "$F" "$T-launch" "$T-end" 'the Self Mode session ended; letting the wireless stack arm again')" 1 "one give-back for one session"   # not-literal: "SelfMode: $reason; letting"
  c 4 eq "$(rl_cnt "$F" "$T-self" "$T-disc" 'Critical error')" 0 "the phone logged no Critical error"   # phone
  if [ "$sb" = 3 ]; then c 5 eq "$sa" 3 "the local player still plays"; else inc "player: sp_before=$sb, so the keep-playing check is untested"; fi
  fin
fi
early
```

- **PASS:** `ssl` >= 1, `static_decl` >= 1, `grants` = 0, `rearm` = 1, `crit` = 0, and `sp_after=3` when
  `sp_before=3`.
- **FAIL:** `grants` >= 1, or `static_decl` = 0 with `ssl` >= 1, or `sp_before=3` and `sp_after` not 3 (the
  permanent loss the gate exists to stop), or `rearm` >= 2 (the give-back ran twice for one session).
- **INCONCLUSIVE:** `ssl` = 0 after the retry rules above. `sp_before` not 3 leaves only the player check untested.

### cleanup. End of round

Runs whatever happened before (`always=yes`). Restores both `settings.xml` backups and diffs them: the run exits 1
if a diff is not empty. The radios go to the rig baseline afterwards (`rig_requires.py baseline`).

**cleanup.sh**

```bash
rl_step "D-POCO back to a phone"
appexit "$PH"
adb -s "$PH" shell dumpsys wifip2p | grep -a isGroupOwner > "$EV/cleanup-poco-go.txt"
rl_radio "$PH" bt on; rl_radio "$PH" wifi on
adb -s "$PH" shell input keyevent 127
adb -s "$HU" shell am force-stop $PKG
rl_radio "$HU" bt on; rl_radio "$MOTO" bt on
rl_step "restore settings"
rc=0
if [ -s "$EV/settings-backup-hu.xml" ]; then
  adb -s "$HU" push "$EV/settings-backup-hu.xml" /data/local/tmp/settings-restore.xml >/dev/null
  printf '%s\n' "F=/data/data/$PKG/shared_prefs/settings.xml" 'cp /data/local/tmp/settings-restore.xml $F' \
    "chown \$(stat -c %u:%g /data/data/$PKG) \$F; chmod 660 \$F" > "$EV/restore-hu.sh"
  adb -s "$HU" push "$EV/restore-hu.sh" /data/local/tmp/restore-hu.sh >/dev/null; adb -s "$HU" shell sh /data/local/tmp/restore-hu.sh
fi
if [ -s "$EV/settings-backup-ph.xml" ]; then
  adb -s "$PH" push "$EV/settings-backup-ph.xml" /data/local/tmp/settings-restore.xml >/dev/null
  adb -s "$PH" push "$RIG_TOOLKIT/restore_settings.sh" /data/local/tmp/restore_settings.sh >/dev/null
  adb -s "$PH" shell run-as $PKG sh /data/local/tmp/restore_settings.sh
fi
for u in hu ph; do
  s=$HU; [ "$u" = ph ] && s=$PH
  adb -s "$s" shell run-as $PKG cat shared_prefs/settings.xml > "$EV/settings-restored-$u.xml"
  if ! diff <(tr -d '\r' < "$EV/settings-backup-$u.xml") <(tr -d '\r' < "$EV/settings-restored-$u.xml") > "$EV/settings-diff-$u.txt"; then rc=1; fi
done
exit "$rc"
```

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
  `LocationSourceAnnouncementPolicy`, `SelfModeWirelessPausePolicy`). The dev host's 4257 tests cover them.
- **Video, the hotspot, the decoder, the poke cadence.** Nothing here touches them.

## 9. Report back

1. **K1:** `ns127`, `ns127_self`, `tx`, `sp0`, `sp1`, `sp2` and the `Media button session is` line (`P1-record.txt`).
2. **H2:** the verdict with `stopped`, `same_cfg`, `reinit`, `quit`, `ssl_n`, `ssl_s` and `timedout`, for each
   attempt (`H2-record.txt` and the COND lines).
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
Hiding loading overlay after first video frame
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
