# audio-stutter round 1 brief

An A/B audio round on one head unit, D-HP (API 17), over USB. It asks one question: below API 24, does a 200 ms device buffer stop the stutters that the mixer thread causes? The operator listens and stamps each stutter heard.

Publish as `audio-stutter-round1-brief.md`. Results go in `audio-stutter-round1-results.md`.

## 1. Build and baseline

| | |
|---|---|
| Candidate | `fork/fix/audio-stutter` @ `d96f17c19cf0f169c72b3f38091f92f93ac1bcb1` (`d96f17c1`, "Playback: give a fixed-size track a 200 ms stall reserve below API 24") |
| Baseline | the commit below it, `d5528b51fee45c13c5112c0a004c823224fbff7b` (`d5528b51`, "Audio: name the cause of each audible stutter in the log") |
| Base | `main` @ `d41fdf41` (PR #1090 and the boot fix merged) |
| History | **rebased and force-pushed twice on 2026-10-10**, before any run of this round. The first publication was `fe3f9c7e` on `ec9d9c33`. The rebase took PR #1090 into the base. Commit 1 did not change. Commit 2 was then cut down to the API 17 to 23 case only, so it keeps #1090's floor, growth and ceiling on API 24 and later. |

The baseline is commit 1 alone, not `main`. Both builds then print the same new diagnostic lines, and the only difference between them is the device buffer.

```bash
git fetch fork fix/audio-stutter
git rev-parse fork/fix/audio-stutter            # must print d96f17c19cf0...
git rev-parse fork/fix/audio-stutter~1          # must print d5528b51fee4...
```

Build each SHA in its own detached worktree (`wt-new.sh`) with `build_hur_cool.sh`, after the thermal gate (rig-quirks `topics/tooling.md`, first entry). Copy each APK out of `apks/` at once into `audio-stutter-round1/` as `baseline.apk` and `candidate.apk`, because `build_hur.sh` deletes the previous APK. Run `run_unit_tests.sh` once, on the candidate worktree.

## 2. What this is and why

Users report audio stutter, crackle and clicks on 3.5.0-beta3 and later. One report is over USB, with two phones, and no setting changes it. On 3.3.0 the same unit played clean. The owner heard many stutters on D-HP (API 17) in `adaptive-audio` round 1 run H1, while every counter in the log read clean.

The diagnosis ranks one cause first (H1). Since 3.5.0-beta3, all audio goes `bank -> AudioMixer thread -> one AudioTrack`. The device buffer between the mixer thread and the speaker is 20 ms at start, grows 10 ms per underrun and stops at 60 ms. Below API 24 it stays at the platform minimum for the whole session. If the mixer thread stalls for longer than the buffer holds, the device underruns. The bank cannot help, because the stalled thread feeds it. No user setting reaches this buffer.

**PR #1090 is now in `main`, so both arms carry it.** It sets the floor to the platform minimum (`getMinBufferSize`), with the same 10 ms growth step, and adds a waveform-matched recovery and a 5 ms ramp at a stream end. The `pr-1090-audio-transitions` rounds measured the platform minimum at 3844 frames (80 ms) on D-POCO, 3848 on D-HU and 2229 (46 ms) on D-HP. Below API 24 the baseline buffer is that minimum for the whole session. So this round now asks if our branch adds to #1090 on that one path.

The two commits:

1. **`d5528b51` (baseline), diagnostics only.** Each audible event (device underrun, start of a concealment, stale discard, catch-up compression) prints one `AudioStutter:` line with the cause it fits. `MIXER_STALL` means the time the device went unfed (`unfedMs`) was longer than the device buffer (`deviceMs`). Time that output is held for audio focus does not count. `PHONE_FLOW_RESET` needs an arrival gap of 280 to 400 ms; a longer gap is `LINK_LATE`. `PHONE_FLOW_RESET`, `LINK_LATE` and `PHONE_SOURCE_GAP` put the fault on the phone or the link. `STREAM_EDGE` is a stream start or stop. The 10 s mixer line gains a per-cause roll-up. It also adds a clock drift estimate (`clock drift=`) and timing for the AAC path (`media timing:`). It does not change playback.
2. **`d96f17c1` (candidate), the fix.** Below API 24 the track cannot resize, so its one allocation is the whole buffer. The candidate makes that allocation 200 ms (9600 frames), or the platform minimum if that is larger. On API 24 and later, nothing changes: #1090's floor, growth and ceiling stay as merged.

Expected buffer sizes on D-HP, in frames (48 frames = 1 ms), as the first L-OUT line prints them:

| Arm | `requested=` | `effective=` | `minimum=` | `maximum=` |
|---|---|---|---|---|
| Baseline | 2400 | 2229 (46 ms) | 2229 | 3360 |
| Candidate | 2400 | 9600 (200 ms) | 2229 | 3360 |

`requested=` and `maximum=` are the same in both arms, because the tuner cannot change a track below API 24. Only `effective=` differs.

The candidate's JVM model of a fixed track gives 0 underruns at 9600 frames for 30 ms and 80 ms stalls, and underruns at 2229 frames for an 80 ms stall. Only hardware says how long the mixer thread really stops, and whether a heard stutter goes away.

## 3. What is different this round

- **First round of this thread.** Open a `README.md` row for `audio-stutter`.
- **Revised twice before any run** (section 1). The round is now D-HP only: on API 24 and later the two builds play the same, so the D-POCO runs (old R3, R4) and the D-HU run (old R5) are removed. Other changes: the buffer values are read from L-OUT, not L-OPEN; the R0 symbol is `FIXED_FRAMES`; `heard` reads nothing from stdin. The `pr-1090-audio-transitions` rounds found the last two faults in an earlier brief.
- **L-OPEN is a placeholder.** It prints `opened Awaiting output ... effective=960 ... minimum=960` before the AudioTrack exists, in both arms. Every buffer check in this brief reads L-OUT.
- **D-MOTO is the phone in every run.** It has VLC and an MP3, and it has run USB with D-HP as host in `bluetooth-audio-disabled-usb-connect` round 4.
- **D-HP over USB.** D-HP hosts D-MOTO on its only port, so D-HP runs on wireless adb, as in `bluetooth-audio-disabled-usb-connect` round 4. D-HP's host capture has died silently in long runs (rig-quirks `units/D-HP.md`), so every hold checks the capture each 60 s and restarts it.
- **The operator listens in R1 and R2** and presses Enter in a second terminal at each stutter or click heard. That stamps `HEARD` into the head unit's log. This is the one hand step in the runs. Per TESTING-TEMPLATE §0, no verdict rests on it: each verdict uses the log, and the heard counts are a separate measurement in the results.
- **`xruns` reads `N/A` on D-HP.** The platform gives no underrun count below API 24. On D-HP the instruments are `loopGapMax`, the `AudioStutter:` lines and the heard markers.
- **No navigation prompts.** Nothing on this rig reliably sends guidance audio (`audio-sink-jitter` round 1: mock locations retired). So `STREAM_EDGE` and the 16 kHz guidance path are graded only if guidance traffic happens by chance. Report it if it does.
- **Expected INCONCLUSIVE:** on a unit where the baseline arm shows 0 `cause=MIXER_STALL` lines, 0 `xruns` growth and 0 heard stutters, there is no fault to remove, and the candidate's PASS on that unit says nothing about H1. Section 8 marks where this applies.
- **The rig's audio settings are a deliberate worst case**: `use-aac-audio` true, `audio-latency-multiplier` 2, `audio-queue-capacity` 20. Do not reset them to defaults. Read D-HP's values at the start of the round and record them. If they differ, write the worst-case values, and say so in Setup notes. Both arms must run with the same file.

## 4. Pre-flight (one batched ask to the operator, before R0)

Check what adb can check first, then ask for the rest in one message, then run.

1. **The operator listens in R1 and R2**: about 20 minutes of listening in two holds. Media volume on D-HP at a level where a 20 ms gap is audible, and the same level for both arms.
2. **Sound comes from D-HP's own speaker.** Nothing on Bluetooth takes the audio (rig-quirks `topics/audio.md`, A2DP speaker entry).
3. **One cable move** before stage H (section 6). The operator also replugs D-MOTO once if a USB session does not form (section 8, stop rules).
4. **D-MOTO stays unlocked** with no PIN prompt. Check: `adb -s ZY22GC3BM4 shell dumpsys window | grep -a mCurrentFocus`. Then `adb -s ZY22GC3BM4 shell svc power stayon true`.
5. **VLC and a long MP3 on D-MOTO.** `adb -s ZY22GC3BM4 shell pm list packages org.videolan.vlc` must print the package. Run `adb -s ZY22GC3BM4 shell appops set org.videolan.vlc MANAGE_EXTERNAL_STORAGE allow`. Make one file of 12 minutes or more, so that no run restarts the track: `adb -s ZY22GC3BM4 shell 'cd /sdcard/Music && cat ohu1008.mp3 ohu1008.mp3 ohu1008.mp3 ohu1008.mp3 > ohu-stutter-long.mp3'` (4 x 216 s). If `ohu1008.mp3` is missing, push any MP3 of 3 minutes or more and use it four times.
6. **USB permission state.** On D-HP: `adb shell dumpsys usb | grep -a -i -A4 headunitrevived`. Record it. Do not change it.
7. **Batteries.** D-HP powers D-MOTO over OTG. D-HP 60% or more before stage H, D-MOTO 50% or more.

## 5. Settings keys

Write with the app stopped (TESTING-TEMPLATE §1) with the rig's writer `set_prefs_runas_host.py`. Back up D-HP's `settings.xml` first, diff it against its last backup, and put the delta in Setup notes. **After every APK install, read every key below back before the next launch** (rig-quirks `topics/tooling.md`: an install can wipe `settings.xml`).

**Every run (`AKEYS`):**

| Element | Why |
|---|---|
| `<int name="log-level" value="2" />` | INFO carries every line in section 7 |
| `<int name="onboarding-version" value="2" />` | no wizard |
| `<boolean name="use-aac-audio" value="true" />` | rig worst case, see section 3 |
| `<int name="audio-latency-multiplier" value="2" />` | rig worst case |
| `<int name="audio-queue-capacity" value="20" />` | rig worst case |
| `<boolean name="use-aaudio-output" value="false" />` | AudioTrack, the path the fix changes |
| `<boolean name="enable-audio-sink" value="true" />` | the media channel exists (rig-quirks `topics/audio.md`) |
| `<boolean name="playback-focus-self-defeating" value="false" />` | focus latch clear |
| `<boolean name="attach_hw_dsp_equalizer" value="false" />` | low-latency flag as shipped |
| `<boolean name="kill-on-disconnect" value="false" />` | service survives an exit |
| `<boolean name="enable-floating-button" value="false" />` | nothing over the projection |
| delete `video-profile-starvation-cap` | no silent 720p cap |

Do not write `static-audio-focus`. Read it back and record it. It must be the same in both arms.

**D-HP as USB host (`UKEYS`), in addition:** `set:connection-modes=usb bool:auto-start-on-usb=true bool:auto-connect-last-session=true bool:reopen-on-reconnection=true bool:use-libusb=false`. With `usb` the only connection mode, the wireless stack does not arm. Use the fixed `set:` writer (rig-quirks `topics/tooling.md`, empty `set:` entry).

`allow-external-configuration` is not needed. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on this branch.

## 6. Stages, ports and shell helpers

One stage.

| Stage | Head unit | Phone | Ports |
|---|---|---|---|
| H (R1, R2) | D-HP, wireless adb | D-MOTO on D-HP's OTG port, wireless adb | D-HP and D-MOTO off the PC |

Before stage H, with D-HP and D-MOTO still cabled: install `baseline.apk` on D-HP (`adb install -r -d`), then `adb -s <unit> tcpip 5555` on D-HP and D-MOTO. Read each IP with `adb -s <unit> shell ip -4 addr show wlan0`. Then the operator moves the cables for stage H. D-HP's serial is whatever `adb devices` lists (it has read `CNU350BGBJ` and `0123456789ABCDEF`).

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
VLC=org.videolan.vlc
MEDIA=/sdcard/Music/ohu-stutter-long.mp3
# Every call to the head unit goes through A, so this shell and the operator's heard() never overlap on one unit.
A() { flock "/tmp/rig-adb-${HU//[:.]/_}.lock" adb -s "$HU" "$@"; }
send() { a=$1; shift; A shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1"; sleep 0.3; adb -s "$PH" shell log -t RIGMARK "$1"; }
HUCAP() { A logcat -c; stdbuf -oL adb -s "$HU" logcat -v time >> "$1-hu.txt" & echo $! > "$1-hu.pid"; }
PHCAP() { adb -s "$PH" logcat -c; stdbuf -oL adb -s "$PH" logcat -v time >> "$1-phone.txt" & echo $! > "$1-phone.pid"; }
# Restart a dead capture without clearing. The grader removes duplicate lines (section 7).
alive() { for s in hu phone; do kill -0 "$(cat "$1-$s.pid")" 2>/dev/null && continue
  d=$HU; [ $s = phone ] && d=$PH; echo "$(date +%T) $1-$s capture died, restarted" >> "$1-notes.txt"
  stdbuf -oL adb -s "$d" logcat -v time >> "$1-$s.txt" & echo $! > "$1-$s.pid"; done; }
media() { adb -s "$PH" shell am start -a android.intent.action.VIEW -d "file://$MEDIA" -t audio/mpeg -n $VLC/.StartActivity; }
# Projected-video swipe, the standing exception (TESTING-TEMPLATE §3). SX1 SX2 SY come from the stage's dump.
swipe() { A shell input swipe $SX1 $SY $SX2 $SY 300; A shell log -t RIGSWIPE swipe; }
# hold <run> <seconds>: swipe every 30 s, check the captures every 60 s.
hold() { end=$((SECONDS+$2)); ns=$((SECONDS+30)); nl=$((SECONDS+60))
  while [ $SECONDS -lt $end ]; do sleep 5
    [ $SECONDS -ge $ns ] && { swipe; ns=$((SECONDS+30)); }
    [ $SECONDS -ge $nl ] && { alive "$1"; nl=$((SECONDS+60)); }
  done; }
```

**The operator's terminal** (R1 and R2 only). Same `HU` and the same `A` function. Start it after `<run>-music` and stop it with Ctrl-C after `<run>-end`:

```bash
# </dev/null: without it, adb reads the next Enter presses and those stamps are lost.
heard() { n=0; while read -r _; do n=$((n+1)); A shell log -t HEARD "stutter-$n" </dev/null; echo "stamped $n"; done; }
```

`log -t` is a native binary, so a stamp costs the unit almost no CPU. `input` and `am` start a Java process, which costs real CPU on D-HP; both arms carry the same swipes, and every swipe is stamped `RIGSWIPE`.

**Swipe coordinates, once**, while the projection is up in stage H priming: `A shell uiautomator dump /sdcard/u.xml`, `A pull /sdcard/u.xml`, then read the first node's `bounds="[0,0][W,H]"`. Set `SX1` to 0.7 x W, `SX2` to 0.3 x W and `SY` to 0.5 x H. Use the same values in both runs (rig-quirks `units/D-SAM-and-D-HP.md`: `input` takes the dump's coordinate space).

Stage H: `HU=<D-HP IP>:5555 PH=<D-MOTO IP>:5555`. Run `adb -s <unit> logcat -G 16M` once on D-HP and D-MOTO; if D-HP refuses `-G`, record it and go on. Grep every capture with `grep -a`.

**Thermal gate:** `rig_thermal.sh wait 75` before every run, and the watch during it (rig-quirks `topics/tooling.md`). Re-run a run once if the throttle counter rose or the host reached 90C.

**End of every run:** `send ACTION_DISCONNECT`, `sleep 5`, `adb -s $PH shell am force-stop $VLC`, `send ACTION_EXIT`, `sleep 3`, `A shell am force-stop $PKG`, stop both captures by pid, and restore the unit's round backup with the run's keys.

## 7. The deciding lines

All head unit lines are `AppLog.i` or `AppLog.w` with no `LOG_VERBOSE` guard, so INFO (`log-level` 2) carries them. The mixer lines go through `AudioDiagnostics`, which appends ` eventElapsedMs=N`. Each string was checked with `grep -F -r` against `app/src/main` on `d96f17c1`; composed lines are listed by their fixed parts. Grep them in `<run>-hu.txt` between `<run>-music` and `<run>-end`, unless the table says otherwise.

**Before counting, remove duplicate lines** from any capture that `alive` restarted: `awk '!seen[$0]++' <run>-hu.txt > <run>-hu.dedup.txt`, and count on that file.

| Id | Grep (`grep -a`) | What to read |
|---|---|---|
| L-OPEN | `AudioMixer: id=[0-9]* opened ` | the placeholder line (section 3). Report it only; it decides nothing. |
| L-MIX | `AudioMixer: id=[0-9]* .* loopGapMax=` | the 10 s device line. Read `effective=` (frames; divide by 48 for ms), `xruns=`, `loopGapMax=`, `writeMax=`, and the roll-up `stutters= mixer= flowReset= linkLate= sourceGap= edge= unknown= suppressed=` |
| L-CH6 | `AudioMixer: id=[0-9]* channel=6 target=` | the 10 s media channel line. Read `arrivalGapMax=`, `concealedFrames=`, `staleFrames=`, `compressedFrames=` |
| L-ST | `AudioStutter: id=` | one per audible event. Read the time, `kind=`, `cause=`, `unfedMs=`, `deviceMs=`, `arrivalGapMs=`, `sourceGapMs=`, `edgeMs=`. At most 2 a second per channel; the rest are in `suppressed=`. |
| L-OUT | `AudioMixer: id=[0-9]* output .* requested=` | the real buffer. Whole capture: the first line comes when the AudioTrack opens, before the window. Read `output AudioTrack`, `requested=`, `effective=`, `minimum=`, `maximum=`. Later lines are buffer changes, `AppLog.w` when one follows an xrun. Count the lines in the window. |
| L-OVF | `PCM overflow channel=` | report the count |
| L-DRIFT | `AUDIO clock drift=` | composed `AapAudio: AUDIO clock drift=+N ppm over Ns (N windows)`. Report the last value. |
| L-TIME | `AUDIO media timing:` | report the last line |
| L-DEC | `AudioDecoder.start: channel=6` | `codec=` must be `AAC_LC` or `AAC_LC_ADTS`; `latencyMultiplier=` and `queueCapacity=` must match section 5 |
| L-MSR | `Media Start Request AUDIO:` | composed from `Media Start Request %s: session=`. The media channel opened. |
| L-SINK | `Media Sink Setup Request: ` | report the line for `channel AUDIO` |
| L-SSL | `SSL handshake complete` | one per session at INFO |
| L-ACC | `Sending acc start` | the AOA switch, USB runs |
| L-WIFI | `WifiLauncher: Initializing WiFi Mode: ` | must be 0 |
| L-NOWIFI | `WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen ` | report |
| L-TP | `Throughput over ` | report the median `rendered=` per run (video load) |
| L-AR | `AutomationReceiver: ` | every `send` landed |
| L-MK | `AutomationMarker: ` | the run markers |
| L-HEARD | `I/HEARD` | one per stamp. Not an app line. |
| L-SWIPE | `I/RIGSWIPE` | one per swipe. Not an app line. |
| S-UND | `disabled due to previous underrun` | system line, ROM-dependent. Report the count. |
| S-UF | `underrunframes=` | system line at track teardown. Whole capture. Report. |

**Phone lines**, in `<run>-phone.txt`, between the `RIGMARK` lines of the same names. These are Gearhead strings, checked with `grep -F` against the 17.8 decompile (`rxh`, `jec`, `iqa`, `iqk`, `jdx`, `rxd`). D-MOTO runs 17.9, so a string can drift; P-FC is the check that the audio stack logs at all.

| Id | Grep (`grep -a`) | Use |
|---|---|---|
| P-FC | `Creating MediaSourceFlowController for` | report the line. If it is 0 in a run, the phone's flow lines are **not measured** in that run, and P-PERM to P-DROP read "n/a", not 0. |
| P-PERM | `MediaSourceFlowController out of permits` | count |
| P-QUEUE | `Audio transmission queue reached` | count |
| P-ACK | `Car not sending ACK` | count |
| P-DROP | `Dropping frame due to sender queue overflow` | count |
| P-STATE | `Car connection state changed` | the phone saw the session |
| P-FATAL | `FATAL EXCEPTION` | Gearhead process only; must be 0 |

**How to compute a delta.** For a cumulative field (`xruns`, `concealedFrames`, `staleFrames`, `compressedFrames`), read the first and every later line in the window. The delta is the sum of the increases between consecutive lines. If a value falls, the channel or the output reopened: add the new value. `xruns=N/A` has no delta.

**How to match a heard stamp.** An L-HEARD line at time `t` is matched when an L-ST line falls in `[t - 2500 ms, t + 500 ms]`. The window allows for the operator's reaction and the adb delay. Record the cause of the nearest matched L-ST line. An unmatched stamp is a stutter the instruments miss, and is a finding in itself.

## 8. Runs

**R2 is the point of the round.** Run in this order: R0, stage H priming, R1, R2. Write the results file after every run.

Every run's window starts at `<run>-music`, 20 s after the media channel opens, so the start-up settle is not graded. Durations below are the hold only.

### R0. Build and identity gate (about 30 min)

1. Build both SHAs (section 1). Run `run_unit_tests.sh` on the candidate. Expect 3173 tests, 0 failures.
2. `md5sum baseline.apk candidate.apk`. The two must differ.
3. `unzip -p candidate.apk 'classes*.dex' | strings | grep -c -F FIXED_FRAMES` must be 1 or more. Run the same on `baseline.apk` and record the count (expected 0; the commit check in step 5 decides if it is not).
4. Install `baseline.apk` on D-HP with `adb install -r -d`. Pull the installed APK (`pm path`, then `adb pull`) and `md5sum` it. It must match its source.
5. On D-HP: `send ACTION_QUERY_STATE`. The `commit` on the `data=` line must start with `d5528b51`. A `-dirty` suffix from an untracked file is accepted; say so.

PASS: 0 test failures, md5s differ and match, commits match. **A failure here stops the round.**

### Stage H priming (not graded, about 5 min)

On D-HP with `AKEYS` + `UKEYS`, baseline installed, D-MOTO on D-HP's OTG port:

1. `HUCAP SH; PHCAP SH; mark SH-start`
2. `A shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, `sleep 5`, `send ACTION_CHECK_USB`. Wait up to 60 s for L-SSL.
3. `media`. Wait up to 20 s for L-MSR.
4. Take the swipe coordinates (section 6).
5. End the run (section 6).

If a system USB dialog appears, the operator answers it as earlier USB rounds on D-HP did. Record the activity name from `A shell dumpsys window | grep -a mCurrentFocus` and what was ticked. If L-SSL or L-MSR does not come, the operator replugs D-MOTO once and this step repeats once. A second failure makes R1 and R2 UNTESTABLE; stop the round.

### R1. D-HP (API 17), USB, baseline (10 min hold)

Setup: `AKEYS` + `UKEYS` on D-HP, `baseline.apk`. Read the keys back.

1. `rig_thermal.sh wait 75`
2. `HUCAP R1; PHCAP R1; mark R1-start`
3. `A shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, `sleep 5`
4. `send ACTION_CHECK_USB`, `mark R1-usb`. Wait up to 60 s for L-SSL. If none: `send ACTION_CHECK_USB` once more and wait 60 s. If still none, the operator replugs D-MOTO once and you wait 60 s. If still none, `mark R1-nossl` and end the run.
5. `media`. Wait up to 20 s for L-MSR. If none: `adb -s $PH shell input keyevent KEYCODE_MEDIA_PLAY` and wait 20 s more.
6. `sleep 20`, `mark R1-music`. The operator starts `heard`.
7. `hold R1 600`
8. `mark R1-end`. The operator stops `heard`. End the run (section 6).

**Preflight checks** (a failed check is a setup failure: fix the settings and re-run once, never grade it):
- The first L-OUT reads `output AudioTrack` and `effective=` below 9600 (expected 2229).
- L-DEC reads `channel=6`, `codec=AAC_LC` or `codec=AAC_LC_ADTS`, `latencyMultiplier=2`, `queueCapacity=20`.
- L-ACC 1 or more, L-SSL exactly 1 in the run, L-WIFI 0.
- L-MIX lines in the window: 55 or more.

No PASS bar: this is the baseline. **Record:** the first L-OUT `effective=` and `minimum=` in frames and ms; every L-MIX `loopGapMax=` and `writeMax=` with its time; the count of L-MIX windows whose `loopGapMax` is above the `effective` ms; the L-ST counts by `cause=` and by `kind=`; the L-CH6 deltas of `concealedFrames`, `staleFrames` and `compressedFrames`; the L-HEARD count and how many matched, with their causes; L-DRIFT, S-UND, S-UF, the L-TP median; and the phone counts.

### R2. D-HP (API 17), USB, candidate (10 min hold) (the point)

Install `candidate.apk` on D-HP over wireless adb (`adb -s $HU install -r -d candidate.apk`; it took 97 s in an earlier round). Pull and `md5sum` it, then `send ACTION_QUERY_STATE`: the commit must start with `d96f17c1`. Read every key back. Then run R1's steps 1 to 8 with label `R2`.

Preflight checks: as R1, except the first L-OUT must read `effective=` 9600 or more (200 ms).

PASS, all of these:
- L-ST with `cause=MIXER_STALL`: 0 in the window.
- Every L-MIX `loopGapMax=` is under 190 ms. A window at 190 ms or above is a FAIL only when it also holds an L-ST `cause=MIXER_STALL` line; otherwise report it.
- The R2 delta of each of `concealedFrames`, `staleFrames` and `compressedFrames` is not above R1's, or is 4800 frames (100 ms) or less. The 4800 tolerance is this brief's, so that two near-zero counts do not fail on noise.
- Phone: P-STATE 1 or more, P-FATAL 0.

**Reachability.** If R1 shows 0 `cause=MIXER_STALL`, 0 L-MIX windows with `loopGapMax` above the effective ms, and 0 L-HEARD, then R1 had no fault for R2 to remove: grade R2 **INCONCLUSIVE** for H1, and still report every number.

**Sensory result, not part of the verdict (§0):** the R2 L-HEARD count against R1's. The plan's bar is 1 or fewer heard in R2. Report both counts and the causes of the matched stamps.

### Stop rules

- R0 fails: stop the round.
- A run misses its session after the retries in its steps: mark it UNTESTABLE. If R1 is UNTESTABLE, R2 still runs, and R2's verdict becomes INCONCLUSIVE.
- A preflight check fails twice in one run: mark the run UNTESTABLE (setup), and go on.
- The host stays hot for 30 min: stop, and mark the rest UNTESTABLE (host thermal).

### Closing

Put D-HP back as it was: restore its round-start `settings.xml` and read it back; `adb install -r -d` the build that was there before R0 if it was recorded, otherwise leave the baseline and say so. D-MOTO: `svc power stayon false`. Both back to USB adb (`adb -s <unit> usb`).

## 9. What a result means (for the grader)

- **R1 stamps match `staleFrames` jumps or `LINK_LATE`, not `MIXER_STALL` or a high `loopGapMax`:** H1 is not the D-HP cause. The engineer re-diagnoses; this branch is not tuned.
- **R1 `cause=` shares mostly `LINK_LATE` or `PHONE_FLOW_RESET` on USB:** the fault is not the device buffer. Report it first in the results.
- **R2 PASS, with R1 reachable:** the branch removes the USB stutter below API 24.
- **`compressedFrames` falls on R2 against R1:** the quiet-passage clicks were a result of H1. Clicks heard with `compressedFrames` flat belong to a separate cause.
- **L-DRIFT steady above about 100 ppm in R1 or R2:** report it; it opens a separate clock-drift ticket.
- **Unmatched stamps:** each is a stutter no instrument saw. Give the count per run.
- **Phone counts non-zero on a USB run (P-PERM, P-QUEUE, P-ACK, P-DROP):** the loss in that run started on the phone. Report it beside the verdict; it is not a FAIL of this branch.

## 10. Do not re-run

- AAudio under stress and its fallback: `adaptive-audio` round 1 A5 passed. This round keeps AAudio off.
- The AAC permit window: `adaptive-audio` round 1 A4, untestable on this phone.
- A 15 minute drift soak: `clock drift=` grades drift inside each run.
- Navigation prompts from mock locations: retired in `audio-sink-jitter` round 1.
- The stall model rows, the policy sizes and the attribution rules: JVM tests on the branch (3173 tests).
- API 24 and later: the two builds play the same there. #1090's own rounds measured that path on D-POCO and D-HU.
- PR #1090's floor, waveform-matched recovery and ending ramp: `pr-1090-audio-transitions` rounds 1 and 2. Both arms here carry them.

## 11. Report back

1. **D-HP:** R1 against R2 for the count of `cause=MIXER_STALL`, the `compressedFrames` delta, and the heard stamps (count, matched, unmatched).

Also give the `cause=` shares of R1 and R2, the last `clock drift=` value of each run, and every phone count. Captures go to the release `rig-evidence-audio-stutter` as `audio-stutter-round1-captures.zip` (TESTING-TEMPLATE §7). Run time: about 20 minutes of holds; about 1 hour 15 minutes with builds and priming.

The first block below holds the phone's and the system's strings, which are not in this repository: the phone strings are checked against the Gearhead 17.8 decompile, and the two system strings are AudioFlinger and media metrics lines. The last block, `decisive-strings`, holds the app's own strings, each checked with `grep -F -r` against `app/src/main` on `d96f17c1`. Composed lines appear as their fixed parts. Some lines end in a space, which is part of the string.

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
AudioStutter: id=
loopGapMax=
 requested=
PCM overflow channel=
clock drift=
media timing:
AudioDecoder.start: channel=
Media Start Request %s: session=
Media Sink Setup Request: 
SSL handshake complete
Sending acc start
WifiLauncher: Initializing WiFi Mode: 
WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen 
Throughput over 
AutomationReceiver: 
AutomationMarker: 
```
