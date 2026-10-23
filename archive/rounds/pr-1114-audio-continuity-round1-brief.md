# pr-1114-audio-continuity round 1 brief

A three-arm audio round on three head units. The main question is on D-HP (API 17). With music and repeated navigation prompts, does the upstream audio continuity PR (P) stutter less than `main` (B)? Does P plus our 200 ms fixed buffer (C) stutter less than P? D-SAM (API 19) and D-HU (API 34) check the PR's other risks.

Publish as `pr-1114-audio-continuity-round1-brief.md`. Results go in `pr-1114-audio-continuity-round1-results.md`.

**Queue.** This round goes first in the Queue, ahead of every other line. **It supersedes the unrun R2 of `audio-stutter` round 1** (`audio-stutter-round1-results.md`): arm C against arm P asks that question on a newer base (section 2). This round uses all five units (D-HP, D-MOTO, D-SAM, D-POCO, D-HU), so it cannot be paired.

## 1. Build and baseline

| Arm | Ref | SHA | What it is |
|---|---|---|---|
| B | upstream `main`, the parent of P's first commit (not on `fork/main`; fetching P brings it) | `d2d0a6bf71217e8658545314de63ed87c0031451` (`d2d0a6bf`) | the PR's base |
| P | `fork/review/pr-1114` | `ccc089293644f5876b4a446c4e6db1a5aef1cf7b` (`ccc08929`) | the PR head, six commits on B |
| C | `fork/review/pr-1114-stutter-reserve` | `802920baf0ae657c00b6fce96dfd51df066c973b` (`802920ba`) | P plus one commit: a 200 ms fixed device buffer below API 24 |

History: both review branches are new. C's one commit is `d96f17c1` from `fix/audio-stutter`, cherry-picked onto P without conflict. It changes only `AudioTrackBufferSizing.allocationBytes`. So on API 24 and later, C plays the same as P.

```bash
git fetch fork review/pr-1114 review/pr-1114-stutter-reserve
git rev-parse fork/review/pr-1114                    # must print ccc089293644...
git rev-parse fork/review/pr-1114-stutter-reserve    # must print 802920baf0ae...
git rev-parse fork/review/pr-1114-stutter-reserve~1  # must print ccc089293644...
git merge-base --is-ancestor d2d0a6bf fork/review/pr-1114 && echo ok   # must print ok
```

Build all three in ONE worktree made with `$RIG_TOOLKIT/wt-new.sh`. Copy `local.properties` into it. Order: B, then P, then C, because P and C share a base. After each `git checkout --detach <sha>`, check `git rev-parse HEAD`. Build each with `HUR_DIR=<wt> MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh` after `$RIG_TOOLKIT/rig_thermal.sh wait 70`. Copy each APK out of `apks/` at once into `rig-data/rounds/pr-1114-audio-continuity-round1/` as `b.apk`, `p.apk` and `c.apk`.

Dev-host JVM suite: P 3289 tests, 0 failures. C 3291 tests, 0 failures. The rig runs no unit tests. No other queued round shares this base.

## 2. What this is and why

**The PR.** It changes the audio path from the decoder to the device, in six commits:

1. Below API 21 the AudioTrack write is now bounded by the free space in the native buffer, so a full track returns 0 instead of blocking. API 21 and 22 write through a ByteBuffer. On API 16 to 18, `pause()` no longer flushes the track.
2. A failed output retries after 1 s, 2 s and 5 s without a new Start from the phone.
3. Each audio DATA message returns its ACK to the phone, also when our side rejects it.
4. The AAC decoder retry budget renews after 10 s of good output.
5. The guidance and system channels use the selected latency multiplier, not a cap of 4. A short prompt keeps its reserve across Stop and Start.
6. The mixer defers concealment while queued audio still covers the delivery gap. It logs this as `renderWaits=`.

It also adds diagnostic lines: `PCM reserve`, `PCM starvation`, and an `AapAudio: AUDIO1 ... boundary=Stop` line at each prompt end.

**Our review** (`review.md` in the ticket) found no blocker. These are the findings the rig can decide:

- **F1.** After a dead output, each retry takes system audio focus again. That is up to 4 times per prompt, and with no bound for an output that fails every 2 to 3 s.
- **F2 (API 16 to 18).** `pause()` no longer flushes, so the silence written while the mixer drained stays queued. The next prompt can play up to one device buffer of silence first.
- **F3 (API 19 and 20).** KitKat may apply a flush late, after `start()`. Then our written-frames counter stays high, the track never reads as having space, and each prompt logs `500ms write stall; reopening output`.
- **F5.** At multiplier 16, a prompt can wait up to 450 ms for preroll.
- **F6.** `PCM reserve` logs every 5 s per channel at INFO.

**The stutter question (from `audio-stutter` round 1).** Below API 24 an AudioTrack cannot resize. The PR keeps its allocation at the platform minimum: about 2229 frames (46 ms) on D-HP. Our diagnosis ranked that buffer first as the cause of the stutter the operator heard on D-HP while every counter read clean. If the mixer thread stalls for longer than the buffer holds, the device underruns. C makes the buffer 200 ms (9600 frames). The PR's concealment deferral also needs a buffer larger than its guard of `max(buffer/2, 40 ms)`. On 46 ms it can wait about 6 ms; on 200 ms it reaches its full 10 ms. So C against P answers what `audio-stutter` round 1 asked, now on top of the PR. That round's R1 (`audio-stutter-round1-results.md`, baseline `d5528b51`, D-HP over USB) measured a fault to remove: `loopGapMax` above the 46.4 ms buffer in 47 of 59 windows, a maximum of 1263 ms, and 63 heard stamps. Its R2 (`d96f17c1`) was not run; arm C replaces it.

**Not measured by any run here:** API 21 and 22. No rig unit runs them, so the ByteBuffer write path stays unmeasured. F4 (CPU cost of the 1 ms write poll) and F7 (no log for an AAC budget renewal) have no rig instrument.

## 3. What is different about this round

- **First round of this thread.** Add a `README.md` row for `pr-1114-audio-continuity`, and remove the `audio-stutter` Queue line and mark its row superseded.
- **Navigation prompts have never been reliably scripted on this rig, and this round needs them.** Two rounds started navigation with a `google.navigation:` intent and saw the route on the head unit but no `Media Start Request AUDIO1`. One round saw guidance traffic only during a mock drive. On a phone with the app installed, the intent opens an "Open with" chooser. This brief therefore names Maps as the package (`-p com.google.android.apps.maps`), uses the phone's own location for two destinations about 1.3 km away, and alternates them, so each intent starts a new route. Each stage's first run probes the trigger before it relies on it (section 8, probe). If the probe fails, the guidance parts of that stage are INCONCLUSIVE, and stage H still grades music.
- **Two segments per session.** Segment M plays music with a prompt every 40 s. The output never pauses in M, so M grades stutter. Segment G pauses the music and plays a prompt every 35 s, so the output goes idle and pauses between prompts. Only G reaches F2 and F3.
- **The prompt-start delay that F2 predicts is not in any log line.** The mixer's `first PCM channel=4` line comes once per channel lifetime, so it times the first prompt only. Per prompt, this round records what the log does show (section 7) plus one `dumpsys media.audio_flinger` read in G. The host reads that dump; it decides nothing.
- **The arms do not share one device buffer on API 16 to 23, and that is expected.** B reads its buffer as the requested size. P and C read the native frame count, which can be larger. C asks for 9600 frames. The memory rule for audio rounds says to compare backlog excess only at the same `effective=`. This round does not grade excess. It grades loss counters and underrun lines, which a larger buffer does not inflate.
- **The `AudioStutter:` attribution lines of `audio-stutter` round 1 are on no arm here.** In their place, each 10 s mixer line gives `loopGapMax=`. A window whose `loopGapMax` is longer than that arm's buffer in ms is a mixer stall the buffer could not cover.
- **F1 stops D-HU's audioserver for 12 s.** Killing it is not enough: `adaptive-audio` round 1 A6 saw a killed audioserver come back with no app-side recovery. D-HU's shell is already root and has no `su`. The pre-flight asks for consent (section 4). Without it, F1 is not measured.
- **Phones.** D-MOTO is D-HP's phone, over USB on D-HP's OTG port, as in `audio-stutter` round 1 and `pr-1090-audio-transitions` round 1. D-POCO is the phone for D-SAM and D-HU over Native AA. D-POCO is bonded to both head units, and Gearhead can dial a cached head unit (rig-quirks `units/D-POCO.md`). So the head unit that is not in use keeps its app stopped and its Bluetooth held off (`bthold`, section 6).
- **D-HP over USB, not Headunit Server.** D-HP has no WiFi Direct, so Headunit Server is its only wireless mode. USB with D-MOTO as accessory is the proven audio route on it, and it keeps link jitter out of a stutter count.
- **The rig's audio settings are a deliberate worst case**: `use-aac-audio` true, `audio-latency-multiplier` 2, `audio-queue-capacity` 20. Do not reset them to defaults. Read each unit's saved values first and record them. If a unit differs (D-HU once read 8), write the worst-case values and say so. All arms on one unit use the same file.
- **Expected INCONCLUSIVE:** guidance parts of a stage whose probe fails; H2 and H3 when the arm they compare against shows no loss (section 8); F1 when no recovery line appears in the outage window; S runs with fewer than 4 prompts in G.

## 4. Pre-flight (one batched ask to the operator, before R0)

Run the adb checks first, then send the operator one message with items 1 to 6, then run unattended.

**preflight.sh**
```bash
# Serials: D-HP CNU350BGBJ (or 0123456789ABCDEF), D-MOTO ZY22GC3BM4, D-SAM 30041c35642d2200,
# D-POCO 4f4027e9, D-HU 27870808938846.
for s in ZY22GC3BM4 4f4027e9; do
  adb -s $s shell pm list packages com.google.android.apps.maps      # must print the package
  adb -s $s shell pm list packages org.videolan.vlc                  # record
  adb -s $s shell cmd location is-location-enabled                   # must print true; else set-location-enabled true
  adb -s $s shell dumpsys window | grep -a -m2 -oE '(isKeyguardShowing|mShowingLockscreen)=[a-z]+'
  adb -s $s shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
  adb -s $s shell svc power stayon true
done
adb -s ZY22GC3BM4 shell appops set org.videolan.vlc MANAGE_EXTERNAL_STORAGE allow
adb -s ZY22GC3BM4 shell ls -l /sdcard/Music/ohu-stutter-long.mp3 || \
  adb -s ZY22GC3BM4 shell 'cd /sdcard/Music && cat ohu1008.mp3 ohu1008.mp3 ohu1008.mp3 ohu1008.mp3 > ohu-stutter-long.mp3'
adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -A14 'Bonded devices'   # Navegadortz2 and Navegadortz3 both present
adb -s 30041c35642d2200 shell date; date                                            # record the offset
adb -s CNU350BGBJ shell dumpsys usb | grep -a -i -A4 headunitrevived              # record, do not change
for s in CNU350BGBJ ZY22GC3BM4 30041c35642d2200 4f4027e9; do adb -s $s shell dumpsys battery | grep -a level; done
```

If D-POCO has VLC, pull `/sdcard/Music/ohu-stutter-long.mp3` from D-MOTO and push it to the same path on D-POCO, then run the same `appops` line on D-POCO. If D-POCO has no VLC, stage W's music is Spotify (`media` in section 6 handles both). If the location check still reads false after `set-location-enabled true`, record it: the probe will decide.

The one message to the operator:

1. **Cables.** After R0, D-HP and D-MOTO go to wireless adb and D-MOTO moves to D-HP's OTG port (section 6). D-MOTO stays there for the whole round. One replug of D-MOTO if a USB session does not form. At closing, both go back to the PC.
2. **Listening, optional.** In segment M of H1, H2 and H3 (8 minutes each), press Enter in the `heard` terminal at each stutter or click from D-HP's speaker. Same volume for all three. If nobody listens, the runs still go ahead and the heard counts read n/a.
3. **Maps sound.** On D-MOTO and D-POCO, Google Maps navigation sound must be Unmuted. No adb route reads it.
4. **Locks.** D-MOTO and D-POCO unlocked with no PIN prompt for the round.
5. **Consent for W1F and W2F.** These stop D-HU's audioserver for 12 s, twice. D-HU's system UI may restart. A reboot of D-HU is possible. Answer yes or no.
6. **Batteries.** D-HP 60% or more, D-MOTO 50% or more, D-SAM charged from a separate supply if it reads under 30%.

## 5. Settings keys

Write with the app force-stopped (TESTING-TEMPLATE §1): `set_prefs_runas_host.py` on D-HP and D-SAM, `set_hu_settings_host.py` on D-HU. Back up each unit's `settings.xml` first, diff it against its last backup, and put the delta in Setup notes. **After every APK install, force-stop the app and read every key back before the next launch** (rig-quirks `topics/tooling.md`: an install can wipe `settings.xml`). On D-HU, `stat` the `shared_prefs` directory and record it (rig-quirks `units/D-HU.md`).

**Every unit (`AKEYS`):**

| Element | Why |
|---|---|
| `<int name="log-level" value="2" />` | INFO carries every app line in section 7. The PR's lines go through `AudioDiagnostics` at INFO or WARN, with no `LOG_VERBOSE` guard. |
| `<int name="onboarding-version" value="2" />` | no wizard |
| `<boolean name="use-aac-audio" value="true" />` | rig worst case |
| `<int name="audio-latency-multiplier" value="2" />` | rig worst case (W3 writes 16) |
| `<int name="audio-queue-capacity" value="20" />` | rig worst case |
| `<boolean name="use-aaudio-output" value="false" />` | AudioTrack, the path the PR changes |
| `<boolean name="enable-audio-sink" value="true" />` | the media and speech channels exist |
| `<boolean name="playback-focus-self-defeating" value="false" />` | focus latch clear |
| `<boolean name="attach_hw_dsp_equalizer" value="false" />` | as shipped |
| `<boolean name="kill-on-disconnect" value="false" />` | the service survives an exit |
| `<boolean name="enable-floating-button" value="false" />` | nothing over the projection |
| delete `video-profile-starvation-cap` | no silent 720p and AAC cap |

As writer tokens: `int:log-level=2 int:onboarding-version=2 bool:use-aac-audio=true int:audio-latency-multiplier=2 int:audio-queue-capacity=20 bool:use-aaudio-output=false bool:enable-audio-sink=true bool:playback-focus-self-defeating=false bool:attach_hw_dsp_equalizer=false bool:kill-on-disconnect=false bool:enable-floating-button=false del:video-profile-starvation-cap`

**D-HP (`HKEYS`), in addition:** `set:connection-modes=usb bool:auto-start-on-usb=true bool:auto-connect-last-session=true bool:reopen-on-reconnection=true bool:use-libusb=false`. Use the fixed `set:` writer (rig-quirks `topics/tooling.md`, empty `set:` entry). Do not write `static-audio-focus`; read it back, record it, and keep it the same on all three arms.

**D-SAM (`SKEYS`), in addition:** `int:wifi-connection-mode=3 int:native-driver-selection-mode=0 bool:native-poke-all-paired=true`. If `connection-modes` does not hold `wifi`, write `set:connection-modes=wifi`. Read back `screen-orientation` (must be 2). Do not write `static-audio-focus`; read it back and record it.

**D-HU (`WKEYS`), in addition:** `int:wifi-connection-mode=3 int:native-driver-selection-mode=0 bool:native-poke-all-paired=true int:native-aa-wake-damage-verdict=0 del:aa-exit-action bool:static-audio-focus=false`. If `connection-modes` does not hold `wifi`, write `set:connection-modes=wifi`. `static-audio-focus` false is required: F1 is about dynamic focus.

`allow-external-configuration` is not needed. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on any arm; the automation package is the same on B, P and C.

## 6. Stages, ports and shell helpers

| Stage | Head unit | Phone | Transport | Ports |
|---|---|---|---|---|
| H (H1, H2, H3) | D-HP, wireless adb | D-MOTO on D-HP's OTG port, wireless adb | USB | D-HP and D-MOTO off the PC |
| S (S1, S2, S3) | D-SAM, cabled | D-POCO, cabled | Native AA | D-HU's app stopped, Bluetooth held off |
| W (W1, W2, W3) | D-HU, cabled | D-POCO, cabled | Native AA | D-SAM's app stopped, Bluetooth held off |

Before stage H, with D-HP and D-MOTO cabled: install `b.apk` on D-HP (`adb install -r -d`), then `adb -s <unit> tcpip 5555` on both. Read each IP with `adb -s <unit> shell ip -4 addr show wlan0`. The operator then moves the cables (pre-flight item 1). Stage H: `HU=<D-HP IP>:5555 PH=<D-MOTO IP>:5555`. Stage S: `HU=30041c35642d2200 PH=4f4027e9 IDLE=27870808938846`. Stage W: `HU=27870808938846 PH=4f4027e9 IDLE=30041c35642d2200`. Run `adb -s <unit> logcat -G 16M` once on each unit except D-SAM, which has no `-G`; if D-HP refuses it, record that. Grep every capture with `grep -a`.

Write the helpers to `rig-data/rounds/pr-1114-audio-continuity-round1/scripts/` and source them in every run shell. `$EV` is the round's evidence folder.

**pac_lib.sh**
```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
VLC=org.videolan.vlc
MEDIA=/sdcard/Music/ohu-stutter-long.mp3
MAPS=com.google.android.apps.maps
# Every head unit call goes through A, so this shell and the operator's heard() never overlap on one unit.
A() { flock "/tmp/rig-adb-${HU//[:.]/_}.lock" adb -s "$HU" "$@"; }
send() { a=$1; shift; A shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1"; sleep 0.3; adb -s "$PH" shell log -t RIGMARK "$1"; }
HUCAP() { A logcat -c; stdbuf -oL adb -s "$HU" logcat -v time >> "$EV/$1-hu.txt" & echo $! > "$EV/$1-hu.pid"; }
PHCAP() { adb -s "$PH" logcat -c; stdbuf -oL adb -s "$PH" logcat -v time >> "$EV/$1-phone.txt" & echo $! > "$EV/$1-phone.pid"; }
# Restart a dead capture without clearing. The grader removes duplicate lines (section 7).
alive() { for s in hu phone; do kill -0 "$(cat "$EV/$1-$s.pid")" 2>/dev/null && continue
  d=$HU; [ $s = phone ] && d=$PH; echo "$(date +%T) $1-$s capture died, restarted" >> "$EV/$1-notes.txt"
  stdbuf -oL adb -s "$d" logcat -v time >> "$EV/$1-$s.txt" & echo $! > "$EV/$1-$s.pid"; done; }
capstop() { for s in hu phone; do kill "$(cat "$EV/$1-$s.pid")" 2>/dev/null; done; }
# Lines from a marker to the end of the file. D-SAM's logcat -c does not clear, so every count starts at a marker.
after() { awk -v m="AutomationMarker: $2" 'index($0,m){f=1} f' "$1"; }
# waitfor <file> <marker> <fixed string> <count> <secs>: exit 0 once <count> hits follow <marker>.
waitfor() { end=$((SECONDS+$5)); while [ $SECONDS -lt $end ]; do
  [ "$(after "$1" "$2" | grep -a -c -F -- "$3")" -ge "$4" ] && return 0; sleep 2; done; return 1; }
# Two destinations about 1.3 km north and south of the phone's last fix.
loc() { L=$(adb -s "$PH" shell dumpsys location | grep -a -m1 -oE -- '-?[0-9]{1,3}\.[0-9]{4,},-?[0-9]{1,3}\.[0-9]{4,}')
  [ -z "$L" ] && { adb -s "$PH" shell monkey -p $MAPS -c android.intent.category.LAUNCHER 1 >/dev/null; sleep 15
    L=$(adb -s "$PH" shell dumpsys location | grep -a -m1 -oE -- '-?[0-9]{1,3}\.[0-9]{4,},-?[0-9]{1,3}\.[0-9]{4,}'); }
  D1=$(awk -v p="$L" 'BEGIN{split(p,a,","); printf "%.6f,%.6f", a[1]+0.012, a[2]}')
  D2=$(awk -v p="$L" 'BEGIN{split(p,a,","); printf "%.6f,%.6f", a[1]-0.012, a[2]}')
  echo "fix=$L D1=$D1 D2=$D2" | tee -a "$EV/loc.txt"; }
# nav <1|2> <label>: start a route to D1 or D2. A new route speaks its first instruction.
nav() { d=$D1; [ "$1" = 2 ] && d=$D2; mark "$2"
  adb -s "$PH" shell "am start -a android.intent.action.VIEW -d 'google.navigation:q=$d&mode=d' -p $MAPS" >/dev/null; }
media() { if adb -s "$PH" shell pm list packages $VLC | grep -q $VLC; then
    adb -s "$PH" shell am start -a android.intent.action.VIEW -d "file://$MEDIA" -t audio/mpeg -n $VLC/.StartActivity
  else adb -s "$PH" shell monkey -p com.spotify.music -c android.intent.category.LAUNCHER 1; sleep 5
    adb -s "$PH" shell input keyevent KEYCODE_MEDIA_PLAY; fi; }
pausemusic() { adb -s "$PH" shell input keyevent KEYCODE_MEDIA_PAUSE; }
# Projected-video swipe, the standing exception (TESTING-TEMPLATE §3). Stage H only; SX1 SX2 SY from H1's dump.
swipe() { [ -n "$SX1" ] || return 0; A shell input swipe $SX1 $SY $SX2 $SY; A shell log -t RIGSWIPE swipe; }  # no duration: API 17 rejects it
# segm <run> <prompts>: music is playing; one prompt each 40 s, a swipe each 20 s, a capture check each prompt.
segm() { for i in $(seq 1 $2); do nav $(( (i % 2) + 1 )) "$1-m$i"; sleep 20; swipe; sleep 20; swipe; alive "$1"; done; }
# segg <run> <prompts>: music paused; one prompt each 35 s, so the output idles and pauses between prompts.
segg() { for i in $(seq 1 $2); do nav $(( (i % 2) + 1 )) "$1-g$i"; sleep 35; alive "$1"; done; }
# bthold: keep the idle head unit's Bluetooth off; it self-reverts. Kill by pid at the end of the stage.
bthold() { while true; do adb -s "$IDLE" shell svc bluetooth disable >/dev/null 2>&1; sleep 10; done; }
endrun() { send ACTION_DISCONNECT; sleep 5; adb -s "$PH" shell am force-stop $VLC; adb -s "$PH" shell am force-stop $MAPS
  send ACTION_EXIT; sleep 3; A shell am force-stop $PKG; capstop "$1"; }
```

**heard.sh** (the operator's terminal, stage H segment M only; same `HU` and `A`)
```bash
# </dev/null keeps adb from reading the next Enter presses.
heard() { n=0; while read -r _; do n=$((n+1)); A shell log -t HEARD "stutter-$n" </dev/null; echo "stamped $n"; done; }
```

**Thermal gate** (rig-quirks `topics/tooling.md`): `$RIG_TOOLKIT/rig_thermal.sh wait 75` before every run; if it exits 1, call it once more. Run `$RIG_TOOLKIT/rig_thermal.sh watch $EV/thermal-<run>.log` in the background during each run and kill it by pid at the end. Re-run a run once if its throttle counter rose or the host reached 95C. After the second failed wait, stop and mark the rest UNTESTABLE (host thermal). Update `$EV/setup-notes.md` and the grades after every run.

**Swipe coordinates, once**, in H1 step 6 while the projection is up: `A shell uiautomator dump /sdcard/u.xml`, `A pull /sdcard/u.xml`, read the first node's `bounds="[0,0][W,H]"`. Set `SX1` to 0.7 x W, `SX2` to 0.3 x W and `SY` to 0.5 x H. Use the same values in H2 and H3 (rig-quirks `units/D-SAM-and-D-HP.md`). Unset them for stages S and W.

## 7. The deciding lines

All app lines are `AppLog.i` or `AppLog.w` with no `LOG_VERBOSE` guard. The mixer lines go through `AudioDiagnostics`, which appends ` eventElapsedMs=N`. Each fixed part was checked with `grep -F -r` on `app/src/main`: P in the PR worktree, B with `git grep d2d0a6bf`, C with `git grep 802920ba`. The Arms column says where a string exists. Grep in `<run>-hu.txt`, from the window's first marker to its last.

**Before counting, remove duplicate lines** from a capture that `alive` restarted: `awk '!seen[$0]++' <run>-hu.txt > <run>-hu.dedup.txt`, then count on that file.

**Windows:** M is `<run>-music` to `<run>-m-end`. G is `<run>-g` to `<run>-g-end`. F is `<run>-F` to `<run>-F-end`.

| Id | Grep (`grep -a`) | Arms | What to read |
|---|---|---|---|
| L-OUT | `AudioMixer: id=[0-9]* output .* requested=` | all | Whole capture. The first line comes when the AudioTrack opens. Read `output AudioTrack`, `requested=`, `effective=`, `minimum=`. Later lines are buffer changes; count them per window. |
| L-MIX | `AudioMixer: id=[0-9]* AudioTrack effective=.*loopGapMax=` | all | The 10 s device line. Read `effective=` (frames; divide by 48 for ms), `xruns=`, `loopGapMax=`, `mixWorkMax=`, `writeMax=`, and `renderWaits=` (P and C only). |
| L-CH | `AudioMixer: id=[0-9]* channel=[46] target=` | all | The 10 s line per channel: 6 is media, 4 is guidance. Read `concealedFrames=`, `staleFrames=`, `compressedFrames=`. |
| L-PCM | `AudioMixer: id=[0-9]* PCM channel=` | all | WARN on a loss. Count per channel; read `concealedDelta=`, `arrivalGapMax=`. |
| L-RES | `AudioMixer: id=[0-9]* PCM reserve channel=` | P, C | Count per minute in M (F6). For channel 4, read `boundary=`, `depthAtStop=` and `concealedFramesTotal=` on the Stop lines. |
| L-STARVE | `PCM starvation channel=` | P, C | Count per channel. |
| L-FIRST | `first PCM channel=4 ` | all | Once per session. Read `requestToPcm=` and `pcmToRender=`. |
| L-REOPEN | `reopening output` | all | Count. The line carries the reason: `500ms write stall` or `write: N`. |
| L-STALL | `500ms write stall` | all | Count. |
| L-THREW | `write threw: ` | all | Count. |
| L-OFAIL | `output reopen failed: ` | all | Count. |
| L-PAUSED | `recovery paused; retaining PCM and retrying in ` | P, C | Count; read each delay. |
| L-EXH | `automatic recovery exhausted; retaining PCM until Start` | P, C | Count. |
| L-EXHB | `recovery exhausted; retaining PCM until a new Start` | B | Count. |
| L-STOPPED | `AudioMixer: output stopped` | all | Must be 0. |
| L-FOCUS | `AapAudio: Playback transient focus request result` | all | Count. Matches the API 26+ form and the `(legacy)` form. |
| L-REL | `AapAudio: Releasing playback transient audio focus` | all | Count. |
| L-FCH | `AapAudio: playback audio focus changed: ` | all | Count. |
| L-MSR1 | `Media Start Request AUDIO1:` | all | Composed from `Media Start Request %s: session=`. One per prompt that reached us. |
| L-MSR6 | `Media Start Request AUDIO:` | all | The music channel opened. |
| L-STOP1 | `Audio Stop: AUDIO1` | all | Composed from `"Audio Stop: " + Channel.name(channel)`. One per prompt end. |
| L-PEND | `AapAudio: AUDIO1 .* boundary=` | P, C | One per prompt end, from the `boundary=$boundary` line. Report the count. |
| L-DEC | `AudioDecoder.start: channel=` | all | Read `codec=` and `latencyMultiplier=` for channel 6 and channel 4. |
| L-OVF | `PCM overflow channel=` | all | Count. |
| L-SINK | `Media Sink Setup Request: ` | all | Report the AUDIO and AUDIO1 lines. |
| L-SSL | `SSL handshake complete` | all | One per session at INFO. |
| L-ACC | `Sending acc start` | all | The AOA switch, stage H. |
| L-WIFI | `WifiLauncher: Initializing WiFi Mode: ` | all | Stage H: 0. Stages S and W: 1 or more, reading `NATIVE`. |
| L-TP | `Throughput over ` | all | Report the median `rendered=` per run. |
| L-AR | `AutomationReceiver: ` | all | Every `send` landed. |
| L-MK | `AutomationMarker: ` | all | The markers. |
| L-MATCH | `MATCH! Starting AapService` | all | Stages S and W: discard rule (TESTING-TEMPLATE §4). |
| L-HEARD | `I/HEARD` | n/a | One per stamp. Not an app line. |
| L-SWIPE | `I/RIGSWIPE` | n/a | One per swipe. Not an app line. |
| S-UND | `disabled due to previous underrun` | n/a | AudioFlinger line, ROM-dependent. Count per window. |
| S-UF | `underrunframes=` | n/a | Media metrics line at track teardown, API 26+ only. Whole capture. |

**Phone lines**, in `<run>-phone.txt` between the `RIGMARK` lines of the same names. Each was found with `grep -F` in the Gearhead 17.8 decompile. The phones may run a later build, so P-FC checks that the audio stack logs at all.

| Id | Grep (`grep -a`) | Use |
|---|---|---|
| P-FC | `Creating MediaSourceFlowController for` | Report. If 0, P-PERM to P-DROP read n/a, not 0. |
| P-PERM | `MediaSourceFlowController out of permits` | Count. |
| P-QUEUE | `Audio transmission queue reached` | Count. |
| P-ACK | `Car not sending ACK` | Count. The PR's ACK commit targets this. |
| P-DROP | `Dropping frame due to sender queue overflow` | Count. |
| P-STATE | `Car connection state changed` | The phone saw the session. |
| P-FATAL | `FATAL EXCEPTION` | Gearhead process only. Must be 0. |

**How to compute a delta.** For a cumulative field (`concealedFrames`, `staleFrames`, `compressedFrames`), read every line of that channel in the window. The delta is the sum of the increases between consecutive lines. If a value falls, the channel reopened: add the new value.

**The three numbers per window**, used by the bars in section 8:

- **LOSS** = the delta of `concealedFrames` plus the delta of `staleFrames`, for channel 6 and channel 4 added together, in frames.
- **UND** = the S-UND count.
- **GAPW** = the number of L-MIX lines whose `loopGapMax` in ms is at or above that line's `effective=` divided by 48.

**How to match a heard stamp.** An L-HEARD line at time `t` matches when an L-PCM, L-STARVE or S-UND line, or an L-CH line with a rising `concealedFrames` or `staleFrames`, falls in `[t - 2500 ms, t + 500 ms]`. Report matched and unmatched counts.

## 8. Runs

**H3 is the point of the round.** Order: R0, H1, H2, H3, S1, S2, S3, W1, W2, W3. Write the results file after every run.

### R0. Build and identity gate (about 35 min)

1. Build B, P and C (section 1).
2. `md5sum b.apk p.apk c.apk`. All three must differ.
3. `unzip -p <apk> 'classes*.dex' | strings | grep -c -F <symbol>`:
   - `PcmRenderDeadline`: 0 in `b.apk`, 1 or more in `p.apk` and `c.apk`.
   - `FIXED_FRAMES`: 0 in `b.apk` and `p.apk`, 1 or more in `c.apk`.
4. Run `preflight.sh` and send the operator's message (section 4).

PASS: md5s differ, symbols as listed. **A failure here stops the round.**

**Install rule for every run below.** Force-stop the app. `adb -s $HU install -r -d <apk>`. Pull the installed APK (`pm path`, then `adb pull`) and `md5sum` it; it must match. Launch `MainActivity`, `sleep 5`, `send ACTION_QUERY_STATE`; the `commit` must start with the arm's short SHA (a `-dirty` suffix is accepted; say so). Force-stop the app, write the stage's keys, and read every key back.

### Stage H. D-HP (API 17), USB, D-MOTO

Stage H needs the cable move of pre-flight item 1.

### H1. D-HP, B (about 21 min)

Install `b.apk` (install rule). Keys: `AKEYS` + `HKEYS`.

1. `$RIG_TOOLKIT/rig_thermal.sh wait 75`
2. `RUN=H1; HUCAP H1; PHCAP H1; mark H1-start`
3. `A shell am start -n $MAIN`, `sleep 5`
4. `send ACTION_CHECK_USB`. Wait up to 60 s for L-SSL: `waitfor $EV/H1-hu.txt H1-start 'SSL handshake complete' 1 60`. If none: `send ACTION_CHECK_USB` once more and wait 60 s. If still none, the operator replugs D-MOTO once and you wait 60 s. If still none, `mark H1-nossl`, `endrun H1`, and mark H1 UNTESTABLE.
5. `loc`. `D1` and `D2` must be set. If `fix=` is empty, record it and go on: the probe decides.
6. **Probe.** `mark H1-probe`. `nav 1 H1-probe1`, then `waitfor $EV/H1-hu.txt H1-probe 'Media Start Request AUDIO1:' 1 30`. If it fails, `nav 2 H1-probe2` and wait 30 s the same way. If it fails again, `nav 1 H1-probe3` and wait 30 s. Set `GUIDE=yes` if any wait passed, else `GUIDE=no`. Record `GUIDE` and, on the phone, `adb -s $PH shell dumpsys window | grep -a mCurrentFocus`. `GUIDE` holds for all of stage H.
7. `media`. Wait up to 20 s for L-MSR6 after `H1-probe`. If none: `adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY` and wait 20 s more. Take the swipe coordinates (section 6). `sleep 20`.
8. `mark H1-music`. The operator, if listening, starts `heard`. If `GUIDE=yes`: `segm H1 12`. If `GUIDE=no`: play music only for 480 s, swiping each 20 s and running `alive H1` each 40 s. `mark H1-m-end`. The operator stops `heard`.
9. If `GUIDE=yes`: `pausemusic`, `sleep 10`, `mark H1-g`, `segg H1 6`. Between `H1-g3` and `H1-g4`, 25 s after `H1-g3`, run `A shell dumpsys media.audio_flinger > $EV/H1-af.txt`. `mark H1-g-end`.
10. `mark H1-end`, `endrun H1`.

**Preflight checks** (a failed check is a setup failure: fix the settings and re-run once, never grade it):
- The first L-OUT reads `output AudioTrack`. Record `requested=`, `effective=` and `minimum=` (B expected about 2229).
- L-DEC: channel 6 reads `codec=AAC_LC` or `codec=AAC_LC_ADTS` and `latencyMultiplier=2`.
- L-ACC 1 or more, L-SSL exactly 1, L-WIFI 0, L-MSR6 1 or more.
- L-MIX lines in M: 45 or more.

No PASS bar: H1 is the baseline. **Record:** L-OUT; LOSS, UND and GAPW for M; the largest `loopGapMax` and `mixWorkMax` in M; L-PCM count per channel; L-MSR1 and L-STOP1 counts in M and G; L-REOPEN, L-STALL, L-THREW in M and G; L-FIRST; L-HEARD total and matched; S-UND for G; L-TP median; every phone count.

### H2. D-HP, P (about 20 min)

Install `p.apk` over wireless adb (it took 97 s in an earlier round). Then H1 steps 1 to 10 with label `H2`, except step 6: send one `nav 1 H2-probe1` and wait 30 s; do not change `GUIDE`.

Preflight checks: as H1. Record `effective=`; it can be larger than B's (section 3).

PASS, all of these (a FAIL is a regression against B):
- In M, LOSS(H2) is not above max(LOSS(H1), 4800 frames). 4800 frames is 100 ms; the tolerance is this brief's, so near-zero counts do not fail on noise.
- In M, UND(H2) is not above UND(H1).
- L-REOPEN, L-THREW and L-STOPPED are 0 in M and in G.
- If `GUIDE=yes`: in G, L-STOP1 equals L-MSR1 (each prompt ended).
- Phone: P-STATE 1 or more, P-FATAL 0.

**Classify** P against B as REDUCED if LOSS(H2) is below LOSS(H1) minus 4800, or UND(H2) is below UND(H1); otherwise SAME. **Reachability:** if H1 has LOSS of 4800 or less, UND 0, GAPW 0 and no heard stamp, B had no stutter to remove: the classification is INCONCLUSIVE, and the PASS bar still stands.

**Record:** as H1, plus L-RES per minute in M, L-STARVE per channel, `renderWaits=` sum in M, and L-PEND in G.

### H3. D-HP, C (about 20 min) (the point)

Install `c.apk`. Then H2's steps with label `H3`.

Preflight checks: as H1, and the first L-OUT reads `effective=` 9600 or more. If it does not, C's buffer did not apply: re-run once, then mark H3 UNTESTABLE (setup).

PASS, all of these:
- GAPW(H3) in M is 0. A window at or above 200 ms is a FAIL only when the same window also has a rising `concealedFrames` or `staleFrames`, an L-PCM line or an S-UND line; otherwise report it.
- In M, LOSS(H3) is not above LOSS(H2) plus 4800, and UND(H3) is not above UND(H2).
- L-REOPEN, L-THREW and L-STOPPED are 0 in M and in G.
- Phone: P-STATE 1 or more, P-FATAL 0.

**Classify** C against P as REDUCED if LOSS(H3) is below LOSS(H2) minus 4800, or UND(H3) is below UND(H2), or GAPW(H2) was above 0 and GAPW(H3) is 0; otherwise SAME. **Reachability:** if H2 has LOSS of 4800 or less, UND 0, GAPW 0 and no heard stamp, P left nothing for C to remove: the classification is INCONCLUSIVE.

**Sensory result, not part of any verdict (§0):** heard stamps in H1, H2 and H3, matched and unmatched. Also compare G's prompts by ear if the operator listened: C can add up to 200 ms of silence before each prompt on this API level (F2).

**Record:** as H2, and give each arm's `effective=` in ms beside its LOSS and GAPW.

### Stage S. D-SAM (API 19), Native AA, D-POCO

Before S1: `adb -s 27870808938846 shell am force-stop $PKG`, then `bthold &` and record its pid. Kill it at the end of stage S. D-SAM settles with two `createGroup SUCCESS` and two `Handling handshake for` lines before SSL; that is not contamination (rig-quirks `units/D-SAM.md`). Use no `-p` with `am` on D-SAM.

**bringup.sh** (stages S and W; `$1` is the run label)
```bash
adb -s "$PH" shell svc bluetooth disable; sleep 2
adb -s "$PH" shell dumpsys bluetooth_manager | grep -a -m2 -E 'enabled:|state:'
HUCAP $1; PHCAP $1; mark $1-start
A shell am start -n $MAIN
waitfor $EV/$1-hu.txt $1-start 'WifiLauncher: Initializing WiFi Mode: ' 1 10 || send ACTION_START_WIRELESS_SCAN
sleep 10
adb -s "$PH" shell svc bluetooth enable; sleep 2
adb -s "$PH" shell dumpsys bluetooth_manager | grep -a -m2 -E 'enabled:|state:'
waitfor $EV/$1-hu.txt $1-start 'SSL handshake complete' 1 150 || { mark $1-nossl; echo NOSSL; }
```

### S1. D-SAM, B (about 10 min)

Install `b.apk` (install rule; cabled). Keys: `AKEYS` + `SKEYS`.

1. `$RIG_TOOLKIT/rig_thermal.sh wait 75`
2. `RUN=S1; bringup.sh S1`. If it prints `NOSSL`, `endrun S1`, retry once from step 1. A second `NOSSL` makes S1 UNTESTABLE.
3. `loc`, then the probe of H1 step 6 with label `S1`. `GUIDE` holds for all of stage S. If `GUIDE=no`, `endrun S1` and mark S1, S2 and S3 INCONCLUSIVE (no prompts): skip to stage W.
4. `sleep 20`, `mark S1-g`, `segg S1 8`, `mark S1-g-end`.
5. `mark S1-end`, `endrun S1`.

**Preflight checks:** L-WIFI reads `NATIVE`; L-SSL 1 or more and the discard rules of TESTING-TEMPLATE §4 hold (L-MATCH 0, one SSL); the first L-OUT reads `output AudioTrack`; L-DEC channel 4 present.

No PASS bar. **Record:** L-OUT; in G: L-MSR1, L-STOP1, L-REOPEN, L-STALL, L-THREW, L-OFAIL, L-EXHB, LOSS for channel 4, L-PCM for channel 4, S-UND; L-FIRST; phone counts.

### S2. D-SAM, P (about 10 min)

Install `p.apk`. S1 steps with label `S2`; in step 3, send one `nav 1 S2-probe1`, wait 30 s, and do not change `GUIDE`.

**F3 bar.** F3 is REPRODUCED if L-STALL or L-REOPEN in G is 1 or more on S2 and 0 on S1. Grade S2 **FAIL** (F3 confirmed on hardware) if REPRODUCED, and give the count per prompt. Grade **PASS** if L-STALL, L-REOPEN and L-THREW are all 0 in G, L-STOP1 equals L-MSR1, and LOSS for channel 4 is not above max(S1's, 4800). **INCONCLUSIVE** if L-MSR1 in G is under 4.

**Record:** as S1, plus L-PAUSED, L-EXH, L-STARVE, L-PEND, and the times of each L-STALL against the nearest earlier L-MSR1 (ms from Start to stall).

### S3. D-SAM, C (about 10 min)

Install `c.apk`. S2's steps and bar with label `S3`, compared against S1. The first L-OUT must read `effective=` 9600 or more. **Record** as S2. Report whether F3's count changes from S2 to S3.

### Stage W. D-HU (API 34), Native AA, D-POCO

Before W1: kill S's `bthold`, `adb -s 30041c35642d2200 shell am force-stop $PKG`, set `IDLE=30041c35642d2200`, then `bthold &` and record its pid. If `svc bluetooth disable` does not hold on D-SAM, record it and go on. Kill `bthold` at the end of stage W, then `adb -s 30041c35642d2200 shell svc bluetooth enable`. D-HU has no speaker, so stage W grades logs only.

**outage.sh** (W1 and W2 only, only with the operator's consent; `$1` is the run label)
```bash
mark $1-F
A shell setprop ctl.stop audioserver
sleep 12
A shell setprop ctl.start audioserver
for i in $(seq 1 15); do [ "$(A shell getprop init.svc.audioserver | tr -d '\r')" = running ] && break; sleep 2; done
A shell getprop init.svc.audioserver                  # must print running
sleep 25
mark $1-F-end
```

If `getprop` does not print `running` after the loop, run `A shell setprop ctl.start audioserver` once more and wait 30 s. If it is still not running, or adb loses D-HU, stop the round: the rig is broken (TESTING-TEMPLATE §3a).

### W1. D-HU, B (about 15 min)

Install `b.apk` (install rule). Keys: `AKEYS` + `WKEYS`.

1. `$RIG_TOOLKIT/rig_thermal.sh wait 75`
2. `RUN=W1; bringup.sh W1`. `NOSSL` handling as S1.
3. `loc`, then the probe of H1 step 6 with label `W1`. `GUIDE` holds for all of stage W.
4. `media`. Wait up to 20 s for L-MSR6. If none: `adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY` and wait 20 s more. `sleep 20`.
5. `mark W1-music`. If `GUIDE=yes`: `segm W1 6`; else music only for 240 s. `mark W1-m-end`.
6. If consent: `outage.sh W1` (music still playing). Otherwise `mark W1-noF`.
7. If `GUIDE=yes`: `pausemusic`, `sleep 10`, `mark W1-g`, `segg W1 4`, `mark W1-g-end`.
8. `mark W1-end`, `endrun W1`.

**Preflight checks:** as S1, plus L-DEC channel 6 `latencyMultiplier=2` and `static-audio-focus` read back false.

No PASS bar. **Record:** L-OUT; LOSS, UND, GAPW and `xruns=` deltas for M; in F: L-FOCUS, L-REL, L-FCH, L-REOPEN, L-THREW, L-OFAIL, L-EXHB, LOSS for channel 6, and the time of the first L-MIX after `W1-F` whose `xruns=` stops rising; in G as S1; S-UF; phone counts.

### W2. D-HU, P (about 15 min)

Install `p.apk`. W1 steps with label `W2`; in step 3, send one `nav 1 W2-probe1`, wait 30 s, and do not change `GUIDE`.

PASS, all of these (the API 23+ control):
- In M, LOSS(W2) is not above max(LOSS(W1), 4800), and UND(W2) is not above UND(W1).
- L-REOPEN, L-THREW and L-STOPPED are 0 in M and in G.
- Phone: P-STATE 1 or more, P-FATAL 0. P-ACK not above W1's.

**F1, graded separately.** In F: if L-PAUSED, L-EXH, L-REOPEN, L-THREW and L-OFAIL are all 0 on W2, the outage never reached the retry path: F1 is INCONCLUSIVE. Otherwise F1 is REPRODUCED when L-FOCUS in F on W2 is 2 or more and above W1's. Report L-FOCUS and L-REL for both arms and every L-PAUSED delay. F1 is a review finding, not a FAIL of this run.

**Record:** as W1, plus L-RES per minute in M (F6), L-STARVE, `renderWaits=` sum, L-PEND in G, and L-FIRST.

### W3. D-HU, P at multiplier 16 (about 7 min, F5)

Keep `p.apk`. Force-stop the app, write `int:audio-latency-multiplier=16`, read it back. Only if `GUIDE=yes`; otherwise mark W3 INCONCLUSIVE and skip it.

1. `$RIG_TOOLKIT/rig_thermal.sh wait 75`
2. `RUN=W3; bringup.sh W3`. `NOSSL` handling as S1.
3. `sleep 20`, `mark W3-g`, `segg W3 3`, `mark W3-g-end`.
4. `mark W3-end`, `endrun W3`. Force-stop the app, write `int:audio-latency-multiplier=2`, read it back.

Preflight: L-DEC channel 4 reads `latencyMultiplier=16`.

PASS: L-FIRST `pcmToRender=` is 450 ms or less, and L-STOP1 equals L-MSR1. **Record:** L-FIRST in full beside W2's.

### Stop rules

- R0 fails: stop the round.
- A run misses its session after the retries in its steps: mark it UNTESTABLE. If H1 is UNTESTABLE, H2 still runs and its classification becomes INCONCLUSIVE. If H2 is UNTESTABLE, H3 still runs and compares against H1 instead, and says so.
- A preflight check fails twice in one run: mark the run UNTESTABLE (setup), and go on.
- A stage's probe gives `GUIDE=no`: stage S is skipped; stages H and W grade music only and their G parts are INCONCLUSIVE.
- audioserver does not come back on D-HU: stop the round (outage.sh).
- The host stays hot after two waits: stop, and mark the rest UNTESTABLE (host thermal).

### Closing

Restore each unit's round-start `settings.xml` and read it back. Leave `p.apk` installed on all three head units and say so in Setup notes. `adb -s <unit> usb` on D-HP and D-MOTO; the operator moves D-MOTO back to the PC. `svc power stayon false` on D-MOTO and D-POCO. `svc bluetooth enable` on D-HU and D-SAM, read back with `dumpsys`. Check `getprop init.svc.audioserver` on D-HU reads `running`.

## 9. What a result means (for the grader)

- **H3 REDUCED with H2 reachable:** the 200 ms buffer removes stutter that the PR leaves. Our commit should land on top of the PR.
- **H3 SAME with GAPW(H2) 0:** the PR's mixer did not stall past its buffer on this run, so the buffer had nothing to cover.
- **H2 REDUCED:** the PR alone reduces stutter on API 17.
- **Heard stamps unmatched on every arm:** the stutter is below every counter. Give the count per arm; it is a finding in itself.
- **S2 FAIL:** F3 is real on KitKat. Give the stall count per prompt and the Start-to-stall times.
- **W2 F1 REPRODUCED:** give L-FOCUS per arm. The fix is a bound on focus retakes, not on retries.
- **P-ACK lower on P than on B:** the ACK commit changed the phone's view. Report it; it decides nothing here.

## 10. Do not re-run

- `audio-stutter-round1-brief.md`: superseded by H2 and H3. Do not run it.
- The merged audio-transitions PR's floor, matched recovery and ending ramp: `pr-1090-audio-transitions` rounds 1 and 2. All arms carry them.
- A killed audioserver on D-HU: `adaptive-audio` round 1 A6, which recovered on its own. W uses a 12 s stop instead.
- Mock GPS for navigation: it does not reach Android Auto's own navigation (rig-quirks `topics/gearhead.md`).
- A shell-posted notification as a guidance source: `audio-sink-jitter` round 2, no AUDIO1 or AUDIO2 traffic.
- AAudio: off in every run.

## 11. Report back

1. **The point, D-HP:** LOSS, UND and GAPW in M for H1, H2 and H3, each with its `effective=` in ms; the two classifications (P against B, C against P); heard stamps per arm, matched and unmatched.
2. **F3, D-SAM:** L-STALL and L-REOPEN in G for S1, S2 and S3, per prompt.
3. **F1, D-HU:** L-FOCUS and L-REL in F for W1 and W2.

Also give `GUIDE` per stage, L-FIRST on every arm, W3's `pcmToRender=`, L-RES per minute, and every phone count. Captures go to the release `rig-evidence-pr-1114-audio-continuity` as `pr-1114-audio-continuity-round1-captures.zip` (TESTING-TEMPLATE §7).

**Time.** R0 about 35 min with builds. Stage H about 65 min, stage S about 30 min, stage W about 37 min, with cable moves and thermal waits about 15 min. Total about 3 h, about 2 h 25 min without builds.

The first block holds the phone and system strings, which are not in this repository: the phone strings were found in the Gearhead 17.8 decompile, and the other two are AudioFlinger and media metrics lines. The second block holds the app's own strings, each checked with `grep -F -r` on `app/src/main` (P in the PR worktree, B at `d2d0a6bf`, C at `802920ba`). Composed lines appear as their fixed parts. Some lines end in a space, which is part of the string. Eight exist on some arms only, as section 7 says: `renderWaits=`, `PCM reserve channel=`, `concealedFramesTotal=`, `PCM starvation channel=`, `recovery paused; retaining PCM and retrying in `, `automatic recovery exhausted; retaining PCM until Start` and `boundary=$boundary` on P and C; `recovery exhausted; retaining PCM until a new Start` on B.

```decisive-strings-external
Creating MediaSourceFlowController for
MediaSourceFlowController out of permits
Audio transmission queue reached
Car not sending ACK
Dropping frame due to sender queue overflow
Car connection state changed
FATAL EXCEPTION
disabled due to previous underrun
underrunframes=
```

```decisive-strings
AudioMixer: id=
requested=$target effective=
loopGapMax=
mixWorkMax=
renderWaits=
xruns=
AudioMixer: id=$diagnosticId channel=$id target=
concealedFrames=
staleFrames=
compressedFrames=
PCM channel=
concealedDelta=
arrivalGapMax=
PCM reserve channel=
concealedFramesTotal=
PCM starvation channel=
first PCM channel=
requestToPcm=
pcmToRender=
reopening output
500ms write stall
write threw: 
output reopen failed: 
recovery paused; retaining PCM and retrying in 
automatic recovery exhausted; retaining PCM until Start
recovery exhausted; retaining PCM until a new Start
AudioMixer: output stopped
AapAudio: Playback transient focus request result
AapAudio: Releasing playback transient audio focus
AapAudio: playback audio focus changed: 
Media Start Request %s: session=
Audio Stop: 
boundary=$boundary
AudioDecoder.start: channel=
latencyMultiplier=
PCM overflow channel=
Media Sink Setup Request: 
SSL handshake complete
Sending acc start
WifiLauncher: Initializing WiFi Mode: 
Throughput over 
AutomationReceiver: 
AutomationMarker: 
MATCH! Starting AapService
```
