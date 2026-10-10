# pr-1090-audio-transitions round 1 brief

An A/B audio round for upstream PR #1090 (emotionbug), on three head units. It asks three questions, one per commit of the PR. Does the AudioTrack buffer stay at or above the platform minimum? Does waveform-matched latency recovery still repay the backlog, with no new loss and no extra mixer work? Does a stream that stops still release cleanly, with its new 5 ms ending ramp? The operator listens for about 2 minutes of music on each of two units and stamps each click heard. Nobody listens to the test tone.

This round is independent of `audio-stutter` round 1. It does not change that brief and does not use its APKs. Run the two rounds one after the other, in either order, never interleaved.

Publish as `pr-1090-audio-transitions-round1-brief.md`. Results go in `pr-1090-audio-transitions-round1-results.md`.

## 1. Build and baseline

| | |
|---|---|
| Candidate (P) | `fork/review/pr-1090` @ `f81fd51aede743ff5da9185f8cdc960a497764a1` (`f81fd51a`, "PCM: finish stopped streams with a short output ramp"), the PR head as published, not merged with anything |
| Baseline (B) | `main` @ `ec9d9c33`, the PR's own base |
| History | first publication. `review/pr-1090` is a copy of the contributor's branch `pr/audiotrack-device-buffer`, three commits on `ec9d9c33` |

```bash
git fetch fork review/pr-1090
git rev-parse fork/review/pr-1090              # must print f81fd51aede7...
git rev-parse fork/review/pr-1090~3            # must print ec9d9c33...
```

Build each SHA in its own detached worktree (`wt-new.sh`) with `build_hur_cool.sh`, after the thermal gate (rig-quirks `topics/tooling.md`, first entry). Copy each APK out of `apks/` at once into `pr-1090-round1/` as `baseline.apk` and `candidate.apk`, because `build_hur.sh` deletes the previous APK. Run `run_unit_tests.sh` once, on the candidate worktree.

## 2. What this is and why

Issue #1084 reports crackle and clicks after an update from 3.3.0 to 3.5.0-beta4: on Waze prompts, on Gemini replies, and on quiet music over USB. PR #1090 makes three changes. Its author says plainly that no device log ties a click to any of them. This round gives them that log.

1. **`528a1741`, platform minimum floor.** The AudioTrack buffer floor becomes `max(960, ceil(getMinBufferSize / 4))` frames instead of a fixed 960. Every resize, fallback and recovery included, is clamped to it. The mixer's `opened` line prints this floor as `minimum=`. On the baseline it always reads 960 for AudioTrack. Below API 24 the size cannot change, so on D-HP this commit changes nothing unless the platform minimum is above 3840 bytes.
2. **`281f920d`, waveform-matched recovery.** When the bank holds more audio than its target, the mixer removes up to 1 ms at a time with a 5 ms crossfade. The baseline always removes exactly 1 ms. On a 500 Hz tone that is half a period, so the crossfade cancels the signal and the volume dips. The candidate picks the offset whose waveform matches best, and can defer for up to 3 render cycles to let a sharp attack pass. The count of removed frames is `compressedFrames`.
3. **`f81fd51a`, ending ramp.** When the phone stops a stream (`Audio Stop:`), the baseline fills the last 10 ms render block with silence after the last real sample. The candidate keeps every real sample, then ramps from the last sample to zero over 5 ms. The ramp keeps the channel active until it is written, so it can delay the point where the channel counts as quiet and audio focus is released.

What can go wrong, and what this round checks: the floor must hold on every buffer change; recovery must still bring the bank back down; the matcher's extra arithmetic must not lengthen the 10 ms mixer cycle on an old CPU; and a stopped stream must still release focus and let the next stream start.

## 3. What is different this round

- **First round of this thread.** Open a `README.md` row for `pr-1090-audio-transitions`.
- **D-HU cannot host USB** (rig-quirks `topics/tooling.md`, "No USB accessory path"). **D-POCO is the API 24+ USB host** and **D-HP the API 17 USB host**. D-HU runs only stage W, over Native AA, log only, because it has no speaker.
- **D-MOTO is the phone in every run**, with VLC as the source.
- **Two parts per run.** First the **edges**: 3 pause and resume cycles on music (`ohu1008.mp3`), about 2 minutes, with the operator listening. Then the **hold**: a quiet 500 Hz tone, because the recovery change only shows on a steady tone. The hold is graded from the log only. **Nobody listens to it.** The operator may leave, and may turn the unit's volume down by hand before it starts. Volume is applied after the app's mixer, so it changes no number in section 7.
- **Pause and resume use media keys on the phone.** If `KEYCODE_MEDIA_PLAY` does not reopen the channel on this pairing (rig-quirks `topics/audio.md`, media keys entry), the step falls back to relaunching VLC, which restarts the track. Record which one each cycle used.
- **The operator listens in the edges of R1 to R4** and presses Enter in a second terminal at each click heard. That stamps `HEARD` into the head unit's log. No verdict rests on it (TESTING-TEMPLATE §0). The heard counts are a separate result.
- **VLC opens the last file it played**, whatever the intent says (rig-quirks `topics/audio.md`, VLC entry). So each switch between the music and the tone goes through `vlcreset`, which costs about 15 s of first-run scan.
- **This build has no `AudioStutter:` line.** That diagnostic is on another branch. The instruments are the 10 s mixer line, the 10 s channel line, the stream lines and the heard stamps.
- **Expected INCONCLUSIVE:** the recovery question on a unit whose baseline run shows a `compressedFrames` delta of 0, because recovery never ran there. The ending question by ear on a unit whose baseline shows 0 heard clicks at the edges.
- **The rig's audio settings are a deliberate worst case**: `use-aac-audio` true, `audio-latency-multiplier` 2, `audio-queue-capacity` 20. Do not reset them. Both arms on a unit run with the same file.

## 4. Pre-flight (one batched ask to the operator, before R0)

Check what adb can check first, then ask for the rest in one message, then run.

1. **The operator listens in the edges of R1 to R4**: about 2 minutes of music per run, 8 minutes in all. A normal listening volume, the same in both arms on a unit. The tone holds need nobody present.
2. **Sound comes from the unit's own speaker.** Switch D-POCO's bonded `Magnetic Speaker` off for the round, and check `adb shell dumpsys audio` reports `speaker(2)` (rig-quirks `topics/audio.md`).
3. **Cable moves at each stage change** (section 6), and one replug of D-MOTO if a USB session does not form.
4. **D-MOTO unlocked, no PIN prompt.** Check `adb -s ZY22GC3BM4 shell dumpsys window | grep -a mCurrentFocus`. Then `adb -s ZY22GC3BM4 shell svc power stayon true`.
5. **D-MOTO bonded to D-HU (`Navegadortz2`)** for stage W: `adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -A12 'Bonded devices'`.
6. **The tone file.** On the PC, in an empty directory:
   ```bash
   python3 -I - <<'EOF'
   import array, math, wave
   r = 48000
   def tone(f, a, s): return array.array('h', (int(a * math.sin(2 * math.pi * f * i / r)) for i in range(r * s)))
   d = tone(500, 400, 420)
   w = wave.open('ohu-1090-tone.wav', 'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(r)
   w.writeframes(d.tobytes()); w.close()
   EOF
   adb -s ZY22GC3BM4 push ohu-1090-tone.wav /sdcard/Music/
   ```
   7 minutes at about -38 dBFS, about 40 MB. Check the music file too: `adb -s ZY22GC3BM4 shell ls -l /sdcard/Music/ohu1008.mp3`. If it is missing, push any MP3 of 2 minutes or more under that name. Run `adb -s ZY22GC3BM4 shell appops set org.videolan.vlc MANAGE_EXTERNAL_STORAGE allow`.
7. **USB permission state** on D-HP and D-POCO: `adb shell dumpsys usb | grep -a -i -A4 headunitrevived`. Record it. Do not change it.
8. **Batteries.** D-HP and D-POCO 60% or more before their stage, D-MOTO 50% or more.

## 5. Settings keys

Write with the app stopped (TESTING-TEMPLATE §1) with the rig's writers: `set_prefs_runas_host.py` on D-HP and D-POCO, `set_hu_settings_host.py` on D-HU. Back up each unit's `settings.xml` first, diff it against its last backup, and put the delta in Setup notes. **After every APK install, read every key below back before the next launch** (rig-quirks `topics/tooling.md`: an install can wipe `settings.xml`).

**Every unit, every run (`AKEYS`):**

| Element | Why |
|---|---|
| `<int name="log-level" value="2" />` | INFO carries every line in section 7 |
| `<int name="onboarding-version" value="2" />` | no wizard |
| `<boolean name="use-aac-audio" value="true" />` | rig worst case |
| `<int name="audio-latency-multiplier" value="2" />` | rig worst case |
| `<int name="audio-queue-capacity" value="20" />` | rig worst case |
| `<boolean name="use-aaudio-output" value="false" />` | AudioTrack, the path commit 1 changes |
| `<boolean name="enable-audio-sink" value="true" />` | the media channel exists |
| `<boolean name="playback-focus-self-defeating" value="false" />` | focus latch clear |
| `<boolean name="attach_hw_dsp_equalizer" value="false" />` | as shipped |
| `<boolean name="kill-on-disconnect" value="false" />` | service survives an exit |
| `<boolean name="enable-floating-button" value="false" />` | nothing over the projection |
| delete `video-profile-starvation-cap` | no silent 720p cap |

Do not write `static-audio-focus`. Read it back on each unit and record it. It must be the same in both arms on a unit.

**D-HP and D-POCO as USB hosts (`UKEYS`), in addition:** `set:connection-modes=usb bool:auto-start-on-usb=true bool:auto-connect-last-session=true bool:reopen-on-reconnection=true bool:use-libusb=false`. Use the fixed `set:` writer (rig-quirks `topics/tooling.md`, empty `set:` entry).

**D-HU for stage W (`WKEYS`), in addition:** `int:wifi-connection-mode=3 int:native-driver-selection-mode=0 bool:native-poke-all-paired=true int:native-aa-wake-damage-verdict=0 del:aa-exit-action`. Leave `connection-modes` as found if it holds `wifi`; if not, write `set:connection-modes=wifi`.

## 6. Stages, ports and shell helpers

| Stage | Head unit | Phone | Ports |
|---|---|---|---|
| P (R1, R2) | D-POCO, wireless adb | D-MOTO on D-POCO's OTG port, wireless adb | D-POCO and D-MOTO off the PC |
| H (R3, R4) | D-HP, wireless adb | D-MOTO on D-HP's OTG port, wireless adb | D-HP and D-MOTO off the PC |
| W (R5, R6) | D-HU, cabled | D-MOTO, cabled | D-POCO in airplane mode, so it cannot take the poke |

Before stage P, with all units cabled: install `baseline.apk` on D-POCO and D-HP (`adb install -r -d`), then `adb -s <unit> tcpip 5555` on D-POCO, D-HP and D-MOTO. Read each IP with `adb -s <unit> shell ip -4 addr show wlan0`. Then the operator moves the cables for stage P.

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
VLC=org.videolan.vlc
TONE=/sdcard/Music/ohu-1090-tone.wav
MUSIC=/sdcard/Music/ohu1008.mp3
A() { flock "/tmp/rig-adb-${HU//[:.]/_}.lock" adb -s "$HU" "$@"; }
send() { a=$1; shift; A shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1"; sleep 0.3; adb -s "$PH" shell log -t RIGMARK "$1"; }
HUCAP() { A logcat -c; stdbuf -oL adb -s "$HU" logcat -v time >> "$1-hu.txt" & echo $! > "$1-hu.pid"; }
PHCAP() { adb -s "$PH" logcat -c; stdbuf -oL adb -s "$PH" logcat -v time >> "$1-phone.txt" & echo $! > "$1-phone.pid"; }
alive() { for s in hu phone; do kill -0 "$(cat "$1-$s.pid")" 2>/dev/null && continue
  d=$HU; [ $s = phone ] && d=$PH; echo "$(date +%T) $1-$s capture died, restarted" >> "$1-notes.txt"
  stdbuf -oL adb -s "$d" logcat -v time >> "$1-$s.txt" & echo $! > "$1-$s.pid"; done; }
# VLC reopens its last file whatever the intent says, so clear it before each switch.
vlcreset() { adb -s "$PH" shell am force-stop $VLC; adb -s "$PH" shell pm clear $VLC
  adb -s "$PH" shell appops set $VLC MANAGE_EXTERNAL_STORAGE allow; }
music() { adb -s "$PH" shell am start -a android.intent.action.VIEW -d "file://$MUSIC" -t audio/mpeg -n $VLC/.StartActivity; }
tone() { adb -s "$PH" shell am start -a android.intent.action.VIEW -d "file://$TONE" -t audio/x-wav -n $VLC/.StartActivity; }
swipe() { A shell input swipe $SX1 $SY $SX2 $SY 300; A shell log -t RIGSWIPE swipe; }
hold() { end=$((SECONDS+$2)); ns=$((SECONDS+30)); nl=$((SECONDS+60))
  while [ $SECONDS -lt $end ]; do sleep 5
    [ $SECONDS -ge $ns ] && { swipe; ns=$((SECONDS+30)); }
    [ $SECONDS -ge $nl ] && { alive "$1"; nl=$((SECONDS+60)); }
  done; }
# waitfor <run> <fixed string> <seconds>: 0 when a new line holding it lands in the capture.
waitfor() { n0=$(grep -a -c -F "$2" "$1-hu.txt"); end=$((SECONDS+$3))
  while [ $SECONDS -lt $end ]; do sleep 1; [ "$(grep -a -c -F "$2" "$1-hu.txt")" -gt "$n0" ] && return 0; done; return 1; }
# edges <run> <cycles>: pause and resume the music.
edges() { for i in $(seq 1 $2); do
    mark "$1-pause-$i"; adb -s "$PH" shell input keyevent KEYCODE_MEDIA_PAUSE
    waitfor "$1" "Audio Stop: AUDIO" 15 || mark "$1-nostop-$i"
    sleep 3; mark "$1-resume-$i"; adb -s "$PH" shell input keyevent KEYCODE_MEDIA_PLAY
    if ! waitfor "$1" "Media Start Request AUDIO:" 15; then
      mark "$1-relaunch-$i"; music; waitfor "$1" "Media Start Request AUDIO:" 20 || mark "$1-nostart-$i"
    fi
    sleep 8
  done; }
```

**The operator's terminal** (R1 to R4 only). Same `HU` and the same `A` function. Start it at `<run>-edges`, stop it with Ctrl-C at `<run>-edges-end`:

```bash
heard() { n=0; while read -r _; do n=$((n+1)); A shell log -t HEARD "click-$n"; echo "stamped $n"; done; }
```

**Swipe coordinates, once per stage**, while the projection is up in the stage's first run: `A shell uiautomator dump /sdcard/u.xml`, `A pull /sdcard/u.xml`, read the first node's `bounds="[0,0][W,H]"`. `SX1` = 0.7 x W, `SX2` = 0.3 x W, `SY` = 0.5 x H. Same values in both runs of the stage.

Stage P: `HU=<D-POCO IP>:5555 PH=<D-MOTO IP>:5555`. Stage H: `HU=<D-HP IP>:5555 PH=<D-MOTO IP>:5555`. Stage W: `HU=27870808938846 PH=ZY22GC3BM4`. Run `adb -s <unit> logcat -G 16M` once on each unit; if D-HP refuses `-G`, record it and go on. Grep every capture with `grep -a`.

**Thermal gate:** `rig_thermal.sh wait 75` before every run, and the watch during it. Re-run a run once if the throttle counter rose or the host reached 90C.

**End of every run:** `send ACTION_DISCONNECT`, `sleep 5`, `adb -s $PH shell am force-stop $VLC`, `send ACTION_EXIT`, `sleep 3`, `A shell am force-stop $PKG`, stop both captures by pid, and restore the unit's round backup with the run's keys.

## 7. The deciding lines

All head unit lines are `AppLog.i` or `AppLog.w` with no `LOG_VERBOSE` guard, so INFO (`log-level` 2) carries them. Each string was checked with `grep -F -r` against `app/src/main` on `f81fd51a`; composed lines are listed by their fixed parts. The **edge window** is `<run>-edges` to `<run>-edges-end`. The **hold window** is `<run>-music` to `<run>-hold-end`.

**Before counting, remove duplicate lines** from any capture that `alive` restarted: `awk '!seen[$0]++' <run>-hu.txt > <run>-hu.dedup.txt`, and count on that file.

| Id | Grep (`grep -a`) | What to read |
|---|---|---|
| L-OPEN | `AudioMixer: id=[0-9]* opened ` | backend, `effective=`, `burst=`, **`minimum=`**, `maximum=`. Whole capture. |
| L-MIX | `AudioMixer: id=[0-9]* .* loopGapMax=` | the 10 s device line. Read `effective=`, `xruns=`, `loopGapMax=`, `mixWorkMax=`, `writeMax=` |
| L-CH6 | `AudioMixer: id=[0-9]* channel=6 target=` | the 10 s media channel line. Read `target=`, `depth=` (both ms), `concealedFrames=`, `staleFrames=`, `compressedFrames=` |
| L-OUT | `AudioMixer: id=[0-9]* output .* requested=` | a buffer change. Read `effective=` and `minimum=`. Count it. |
| L-OVF | `PCM overflow channel=` | must be 0 |
| L-STOP | `Audio Stop: AUDIO` | the phone stopped the media stream |
| L-MSR | `Media Start Request AUDIO:` | composed from `Media Start Request %s: session=`. The media stream started. |
| L-REL | `Releasing .*audio focus` | covers `Releasing audio focus`, `AapAudio: Releasing playback transient audio focus` and `AapAudio: Releasing all audio focus.` |
| L-DEC | `AudioDecoder.start: channel=6` | `codec=` must be `AAC_LC` or `AAC_LC_ADTS`; `latencyMultiplier=2`, `queueCapacity=20` |
| L-ERR | `AudioMixer: output stopped` and `AAC decode loop stopped` | must be 0 |
| L-SSL | `SSL handshake complete` | one per session at INFO |
| L-ACC | `Sending acc start` | USB runs |
| L-WIFI | `WifiLauncher: Initializing WiFi Mode: ` | USB runs: 0. Stage W: 1 or more, reading `NATIVE` |
| L-FREQ | `WifiDirectManager: onGroupInfoAvailable: ` | stage W: `Freq: ` must be 4900 MHz or more |
| L-MATCH | `MATCH! Starting AapService` | discard rule, stage W |
| L-TP | `Throughput over ` | report the median `rendered=` per run |
| L-AR | `AutomationReceiver: ` | every `send` landed |
| L-MK | `AutomationMarker: ` | the run markers |
| L-HEARD | `I/HEARD` | one per stamp. Not an app line. |
| L-SWIPE | `I/RIGSWIPE` | one per swipe. Not an app line. |
| S-UND | `disabled due to previous underrun` | system line, ROM-dependent. Report the count. |

**Phone lines**, in `<run>-phone.txt`. Gearhead strings, checked against the 17.8 decompile; D-MOTO runs 17.9.

| Id | Grep (`grep -a`) | Use |
|---|---|---|
| P-STATE | `Car connection state changed` | the phone saw the session; 1 or more |
| P-FATAL | `FATAL EXCEPTION` | Gearhead process only; must be 0 |

**How to compute a delta.** For a cumulative field (`concealedFrames`, `staleFrames`, `compressedFrames`, `xruns`), read every line in the window. The delta is the sum of the increases between consecutive lines. If a value falls, the channel reopened: add the new value.

**How to compute the excess.** For each L-CH6 line in the hold window, excess = `depth` minus `target`, in ms. Report the median and the maximum per run. This is the backlog recovery exists to remove.

**How to match a heard stamp.** A stamp belongs to cycle `i` when it falls between `<run>-pause-i` and 2500 ms after that cycle's L-STOP. Any other stamp is unmatched.

## 8. Runs

**R2 is the point of the round.** Order: R0, stage P (R1, R2), stage H (R3, R4), stage W (R5, R6). Write the results file after every run.

### R0. Build and identity gate (about 30 min)

1. Build both SHAs (section 1). Run `run_unit_tests.sh` on the candidate. Expect 0 failures; record the count.
2. `md5sum baseline.apk candidate.apk`. The two must differ.
3. `unzip -p candidate.apk 'classes*.dex' | strings | grep -c -F AudioTrackBufferSizing` must be 1 or more, and 0 on `baseline.apk`.
4. Install `baseline.apk` on D-POCO, D-HP and D-HU with `adb install -r -d`. Pull each installed APK and `md5sum` it. It must match.
5. On each unit: `send ACTION_QUERY_STATE`. The `commit` on the `data=` line must start with `ec9d9c33`.

PASS: 0 test failures, md5s differ and match, commits match. **A failure here stops the round.**

### Stage P priming (not graded, about 5 min)

On D-POCO with `AKEYS` + `UKEYS`, baseline installed, D-MOTO on D-POCO's OTG port. `adb -s $HU shell svc bluetooth disable`, then check `dumpsys bluetooth_manager | grep -a 'enabled'` reads false.

1. `HUCAP SP; PHCAP SP; mark SP-start`
2. `A shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, `sleep 5`, `send ACTION_CHECK_USB`. Wait up to 60 s for L-SSL.
3. `vlcreset; music`. Wait up to 35 s for L-MSR.
4. Take the swipe coordinates (section 6).
5. `adb -s $PH shell input keyevent KEYCODE_MEDIA_PAUSE`, wait 15 s, and record whether L-STOP came. Then `adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY`, wait 15 s, and record whether L-MSR came. This says which path `edges` will take in R1 and R2.
6. End the run (section 6).

If a system USB dialog appears, the operator answers it. Record the activity name from `A shell dumpsys window | grep -a mCurrentFocus` and what was ticked. If L-SSL or L-MSR does not come, the operator replugs D-MOTO once and the priming repeats once. A second failure makes R1 and R2 UNTESTABLE; go to stage H.

**If step 5 shows no L-STOP**, a media pause does not stop the stream on this pairing. Still run `edges` in R1 and R2, and grade the ending question INCONCLUSIVE (no stream end reached).

### R1. D-POCO (API 35), USB, baseline

Setup: `AKEYS` + `UKEYS`, `baseline.apk`. Read the keys back.

1. `rig_thermal.sh wait 75`
2. `HUCAP R1; PHCAP R1; mark R1-start`
3. `A shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, `sleep 5`
4. `send ACTION_CHECK_USB`. Wait up to 60 s for L-SSL. If none, `send ACTION_CHECK_USB` once more and wait 60 s. If still none, the operator replugs D-MOTO once and you wait 60 s. If still none, `mark R1-nossl` and end the run.
5. `vlcreset; music`. Wait up to 35 s for L-MSR.
6. `sleep 20`, `mark R1-edges`. The operator starts `heard`.
7. `edges R1 3`, `mark R1-edges-end`. The operator stops `heard`. **Listening ends here.**
8. `vlcreset; tone`. Wait up to 35 s for L-MSR.
9. `sleep 20`, `mark R1-music`, `hold R1 300`, `mark R1-hold-end`
10. `mark R1-end`. End the run (section 6).

**Preflight checks** (a failed check is a setup failure: fix and re-run once, never grade it):
- L-OPEN reads `opened AudioTrack` and `minimum=960`.
- L-DEC as section 7. L-ACC 1 or more, L-SSL exactly 1, L-WIFI 0.
- L-CH6 lines in the hold window: 27 or more.

No PASS bar: this is the baseline. **Record:** L-OPEN in full; every L-MIX `effective=` and `mixWorkMax=`; the hold-window deltas of `concealedFrames`, `staleFrames`, `compressedFrames` and `xruns`; the median and maximum excess; for each edge cycle, the L-STOP time, the first L-REL after it and the gap in ms, whether it resumed by key or relaunch, and the next L-CH6 `depth=` after the L-MSR; the L-HEARD count per cycle and unmatched; S-UND, the L-TP median and the phone lines.

### R2. D-POCO (API 35), USB, candidate (the point)

Install `candidate.apk` over wireless adb (`adb -s $HU install -r -d candidate.apk`). Pull and `md5sum` it, then `send ACTION_QUERY_STATE`: the commit must start with `f81fd51a`. Read every key back. Then run R1's steps 1 to 10 with label `R2`.

Preflight checks: as R1, except L-OPEN `minimum=` is 960 or more. Record its value: it is this unit's platform minimum in frames.

PASS, all of these:
- **Floor (commit 1):** every L-MIX and L-OUT `effective=` is at or above R2's L-OPEN `minimum=`.
- **Recovery repays (commit 2):** R2's median excess is not more than R1's plus 20 ms, and its maximum excess not more than R1's plus 40 ms.
- **No new loss:** the R2 deltas of `concealedFrames` and `staleFrames` are each not above R1's, or are 4800 frames (100 ms) or less.
- **No extra mixer work:** every L-MIX `mixWorkMax=` in R2 is under 10 ms, and R2's largest is not more than R1's largest plus 2 ms.
- **Streams still end (commit 3):** R2 has as many L-STOP lines in the edge window as R1. Every cycle that reached L-MSR shows an L-CH6 `depth=` above 0 ms within 15 s of it. Where R1 shows an L-REL after an L-STOP, R2 shows one too, and R2's median stop-to-release gap is not more than R1's plus 100 ms.
- L-OVF 0, L-ERR 0. Phone: P-STATE 1 or more, P-FATAL 0.

**Reachability.** If R1's hold-window `compressedFrames` delta is 0, recovery never ran: grade the recovery bar **INCONCLUSIVE** and the rest as written. If priming step 5 showed no L-STOP, grade the stream-end bar **INCONCLUSIVE**. If R2's L-OPEN reads `minimum=960`, the platform minimum is at or below 960 frames on this unit and the floor bar passes without exercising commit 1: say so.

**Sensory result, not part of the verdict (§0):** heard clicks per edge cycle, R1 against R2.

### Stage H priming (not graded, about 5 min)

The operator moves D-MOTO to D-HP's OTG port. Run the stage P priming steps on D-HP with label `SH`, without the Bluetooth step. D-HP already has `baseline.apk` from R0; read its keys back.

### R3. D-HP (API 17), USB, baseline

As R1 with label `R3`. Preflight: as R1, except `xruns` reads `N/A`. D-HP's host capture can die silently (rig-quirks `units/D-HP.md`); `alive` restarts it each 60 s. Record as R1.

### R4. D-HP (API 17), USB, candidate

Install `candidate.apk` over wireless adb (it took 97 s in an earlier round), check the md5 and the commit, read the keys back. Then as R3 with label `R4`.

PASS: the R2 bars, against R3. The mixer-work bar matters most here, on the slowest CPU. The floor bar: every `effective=` equals R4's L-OPEN `effective=`, since the size cannot change below API 24. Reachability as R2.

### R5. D-HU, Native AA over WiFi Direct 5 GHz, baseline (log only)

The operator moves D-MOTO back to the PC. `adb -s <D-POCO IP>:5555 shell cmd connectivity airplane-mode enable`. Setup: `AKEYS` + `WKEYS` on D-HU, `baseline.apk` from R0. D-MOTO in airplane mode at the start (TESTING-TEMPLATE §4).

1. `rig_thermal.sh wait 75`
2. `HUCAP R5; PHCAP R5; mark R5-start`
3. `A shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, `sleep 20`
4. `adb -s $PH shell cmd connectivity airplane-mode disable`, `adb -s $PH shell svc wifi enable`, `adb -s $PH shell svc bluetooth enable`, `mark R5-phone-on`. Wait up to 90 s for L-SSL. If none, re-run R5 once; two misses make R5 UNTESTABLE.
5. `vlcreset; tone`. Wait up to 35 s for L-MSR.
6. `sleep 20`, `mark R5-music`. Take the swipe coordinates for D-HU's dump.
7. `hold R5 300`, `mark R5-hold-end`, `mark R5-end`. End the run (section 6).

No edges and no listening: D-HU has no speaker.

Preflight: L-WIFI reads `NATIVE`. L-FREQ `Freq: ` 4900 MHz or more; if the group landed on 2.4 GHz, re-run once. L-DEC as section 7. Discard and re-run once on L-MATCH 1 or more, or a second L-SSL. Record as R1, without the edge items.

### R6. D-HU, Native AA, candidate (log only)

Install `candidate.apk` on D-HU, check the md5 and the commit, read the keys back. Then as R5 with label `R6`.

PASS: the R2 bars for floor, recovery, loss and mixer work, against R5, plus L-OVF 0, L-ERR 0, P-STATE 1 or more and P-FATAL 0. The recovery bar here sees the link's own jitter, so it is the one most likely to be reachable.

### Stop rules

- R0 fails: stop the round.
- A run misses its session after the retries in its steps: mark it UNTESTABLE and go to the next stage. If a baseline run is UNTESTABLE, its candidate run still runs, and its verdict becomes INCONCLUSIVE.
- A preflight check fails twice in one run: mark the run UNTESTABLE (setup), and go on.
- The host stays hot for 30 min: stop, and mark the rest UNTESTABLE (host thermal).

### Closing

Restore each unit's round-start `settings.xml` and read it back. On each unit, `adb install -r -d` the build that was there before R0 if it was recorded; otherwise leave the baseline and say so. D-POCO: airplane mode off, `svc bluetooth enable`, check with `dumpsys`. D-MOTO: `svc power stayon false`, airplane mode off, WiFi and Bluetooth on, and `rm /sdcard/Music/ohu-1090-tone.wav`. All phones back to USB adb (`adb -s <unit> usb`).

## 9. What a result means (for the grader)

- **Floor bar FAIL:** a buffer change went below the platform minimum, which commit 1 exists to stop. Give the line.
- **Recovery bar FAIL with the loss and work bars PASS:** matched recovery is slower to repay, so the bank carries more delay. Report both excess figures; this is a latency cost, not a click.
- **Mixer-work bar FAIL on D-HP only:** the matcher costs too much on an old CPU. Give the largest `mixWorkMax` of each arm.
- **Stream-end bar FAIL:** the ending ramp kept a channel open, delayed focus release, or stopped the next stream starting. Give the cycle.
- **Clicks heard at the edges in R1 and fewer in R2:** the ending ramp is audible. Clicks in both arms alike: the click comes from the phone's own stop, not the app's.
- **Unmatched stamps:** a click outside a pause. Give the count per run.

## 10. Do not re-run

- The device buffer floor of 100 ms and the stutter attribution line: `audio-stutter` round 1, a different branch.
- AAudio under stress: `adaptive-audio` round 1 A5 passed. This round keeps AAudio off.
- Navigation prompts from mock locations: retired in `audio-sink-jitter` round 1.
- The PR's waveform and ending unit tests: R0's `run_unit_tests.sh` covers them.

## 11. Report back

1. **R2 against R1 (the point):** each PASS bar, the platform `minimum=`, the two excess figures, the largest `mixWorkMax`, and the stop-to-release gaps.
2. **R4 against R3 and R6 against R5:** the same bars, with the largest `mixWorkMax` of each D-HP arm.
3. **Heard clicks:** per run, per edge cycle and unmatched.

Also give each unit's platform `minimum=` from the candidate's L-OPEN, and whether the edges resumed by key or by relaunch. Captures go to the release `rig-evidence-pr-1090-audio-transitions` as `pr-1090-audio-transitions-round1-captures.zip` (TESTING-TEMPLATE §7). Run time: about 30 minutes of unattended tone holds and 8 minutes of listened music; about 2 hours with builds, stage changes and priming.

The first block holds the phone's and the system's strings, which are not in this repository. The second holds the app's own strings, each checked with `grep -F -r` against `app/src/main` on `f81fd51a`. Composed lines appear as their fixed parts. Some lines end in a space, which is part of the string.

```decisive-strings-external
Car connection state changed
FATAL EXCEPTION
disabled due to previous underrun
```

```decisive-strings
AudioMixer: id=
opened 
minimum=
loopGapMax=
mixWorkMax=
compressedFrames=
depth=
PCM overflow channel=
requested=
Audio Stop: 
Media Start Request 
Releasing audio focus
Releasing playback transient audio focus
Releasing all audio focus.
AudioDecoder.start: channel=
AudioMixer: output stopped
AAC decode loop stopped
SSL handshake complete
Sending acc start
WifiLauncher: Initializing WiFi Mode: 
WifiDirectManager: onGroupInfoAvailable: 
MATCH! Starting AapService
Throughput over 
AutomationReceiver: 
AutomationMarker: 
```
