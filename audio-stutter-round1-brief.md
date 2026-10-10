# audio-stutter round 1 brief

An A/B audio round on three head units. It asks one question: does a larger device buffer stop the stutters that the mixer thread causes, on an old tablet and on a modern phone, over USB? A third, shorter run checks that the change does not add audio loss on a normal wireless link. The operator listens on two of the units and stamps each stutter heard.

Publish as `audio-stutter-round1-brief.md`. Results go in `audio-stutter-round1-results.md`.

## 1. Build and baseline

| | |
|---|---|
| Candidate | `fork/fix/audio-stutter` @ `95b78b1a2377d344437bb13df06c4e2aaef42e9e` (`95b78b1a`, "Playback: size the device buffer to ride out mixer-thread stalls") |
| Baseline | the commit below it, `a88e881bcbe076cafcd8396800f2c01b434b6da3` (`a88e881b`, "Audio: name the cause of each audible stutter in the log") |
| Base | `main` @ `7e92cd7a` (PR #1090 merged) |
| History | **rebased and force-pushed on 2026-10-10**, before any run of this round. The first publication was `fe3f9c7e` on `ec9d9c33`. The rebase took PR #1090 into the base. Commit 1 did not change. In commit 2, the 200 ms buffer below API 24 moved into #1090's `AudioTrackBufferSizing`. |

The baseline is commit 1 alone, not `main`. Both builds then print the same new diagnostic lines, and the only difference between them is the device buffer.

```bash
git fetch fork fix/audio-stutter
git rev-parse fork/fix/audio-stutter            # must print 95b78b1a2377...
git rev-parse fork/fix/audio-stutter~1          # must print a88e881bcbe0...
```

Build each SHA in its own detached worktree (`wt-new.sh`) with `build_hur_cool.sh`, after the thermal gate (rig-quirks `topics/tooling.md`, first entry). Copy each APK out of `apks/` at once into `audio-stutter-round1/` as `baseline.apk` and `candidate.apk`, because `build_hur.sh` deletes the previous APK. Run `run_unit_tests.sh` once, on the candidate worktree.

## 2. What this is and why

Users report audio stutter, crackle and clicks on 3.5.0-beta3 and later. One report is over USB, with two phones, and no setting changes it. On 3.3.0 the same unit played clean. The owner heard many stutters on D-HP (API 17) in `adaptive-audio` round 1 run H1, while every counter in the log read clean.

The diagnosis ranks one cause first (H1). Since 3.5.0-beta3, all audio goes `bank -> AudioMixer thread -> one AudioTrack`. The device buffer between the mixer thread and the speaker is 20 ms at start, grows 10 ms per underrun and stops at 60 ms. Below API 24 it stays at the platform minimum for the whole session. If the mixer thread stalls for longer than the buffer holds, the device underruns. The bank cannot help, because the stalled thread feeds it. No user setting reaches this buffer.

**PR #1090 is now in `main`, so both arms carry it.** It sets the floor to the platform minimum (`getMinBufferSize`), with the same 10 ms growth step, and adds a waveform-matched recovery and a 5 ms ramp at a stream end. The `pr-1090-audio-transitions` rounds measured the platform minimum at 3844 frames (80 ms) on D-POCO, 3848 on D-HU and 2229 (46 ms) on D-HP. Below API 24 the baseline buffer is that minimum for the whole session. So this round now asks if our branch adds to #1090.

The two commits:

1. **`a88e881b` (baseline), diagnostics only.** Each audible event (device underrun, start of a concealment, stale discard, catch-up compression) prints one `AudioStutter:` line with the cause it fits. `MIXER_STALL` means the time the device went unfed (`unfedMs`) was longer than the device buffer (`deviceMs`). Time that output is held for audio focus does not count. `PHONE_FLOW_RESET` needs an arrival gap of 280 to 400 ms; a longer gap is `LINK_LATE`. `PHONE_FLOW_RESET`, `LINK_LATE` and `PHONE_SOURCE_GAP` put the fault on the phone or the link. `STREAM_EDGE` is a stream start or stop. The 10 s mixer line gains a per-cause roll-up. It also adds a clock drift estimate (`clock drift=`) and timing for the AAC path (`media timing:`). It does not change playback.
2. **`95b78b1a` (candidate), the fix.** The device buffer floor is 100 ms or the platform minimum, whichever is larger. The ceiling is 400 ms, and growth is 50 ms per underrun. Below API 24 the buffer is a fixed 200 ms, or the platform minimum if that is larger. Nothing else changes.

Expected buffer sizes, in frames (48 frames = 1 ms), as the L-OUT line prints them:

| Unit | Baseline `effective=` / `maximum=` | Candidate `effective=` / `maximum=` |
|---|---|---|
| D-HP (API 17) | 2229 (46 ms) / 3360 | 9600 (200 ms) / 19200 |
| D-POCO (API 35) | 4320 (90 ms) / 5280 | 4800 (100 ms) / 19200 |
| D-HU (API 34), R5 | not run | 4800 (100 ms) / 19200 |

On D-HP the arms differ by 154 ms of buffer. On D-POCO the start buffer differs by only 10 ms, and the arms differ mostly in the growth step and the ceiling.

The JVM model of the mixer gives 0 underruns on the candidate for 30 ms and 80 ms stalls, against 3 and 23 on the baseline. Only hardware says how long the mixer thread really stops, and whether a heard stutter goes away.

## 3. What is different this round

- **First round of this thread.** Open a `README.md` row for `audio-stutter`.
- **Revised before any run** for the rebase onto PR #1090 (section 1). Other changes in that revision: the buffer values are read from L-OUT, not L-OPEN; the R0 symbol is `GROWTH_MS`; `heard` reads nothing from stdin. The `pr-1090-audio-transitions` rounds found the last two faults in an earlier brief.
- **L-OPEN is a placeholder.** It prints `opened Awaiting output ... effective=960 ... minimum=960` before the AudioTrack exists, on every unit and in both arms. Every buffer check in this brief reads L-OUT.
- **D-HU cannot host USB** (rig-quirks `topics/tooling.md`, "No USB accessory path"). The plan's two D-HU USB runs move to **D-POCO as the USB host** (API 35). That keeps the API 24+ path, where the buffer size changes at run time. D-HU runs only R5, over Native AA, log only, because it has no speaker.
- **D-MOTO is the phone in every run.** It has VLC and an MP3, and it has run USB with D-POCO and D-HP as hosts, and Native AA with D-HU, in `bluetooth-audio-disabled-usb-connect` rounds 1 to 4.
- **D-HP over USB.** D-HP hosts D-MOTO on its only port, so D-HP runs on wireless adb, as in `bluetooth-audio-disabled-usb-connect` round 4. D-HP's host capture has died silently in long runs (rig-quirks `units/D-HP.md`), so every hold checks the capture each 60 s and restarts it.
- **The operator listens in R1 to R4** and presses Enter in a second terminal at each stutter or click heard. That stamps `HEARD` into the head unit's log. This is the one hand step in the runs. Per TESTING-TEMPLATE §0, no verdict rests on it: each verdict uses the log, and the heard counts are a separate measurement in the results.
- **`xruns` reads `N/A` on D-HP.** The platform gives no underrun count below API 24. On D-HP the instruments are `loopGapMax`, the `AudioStutter:` lines and the heard markers.
- **No navigation prompts.** Nothing on this rig reliably sends guidance audio (`audio-sink-jitter` round 1: mock locations retired). So `STREAM_EDGE` and the 16 kHz guidance path are graded only if guidance traffic happens by chance. Report it if it does.
- **Expected INCONCLUSIVE:** on a unit where the baseline arm shows 0 `cause=MIXER_STALL` lines, 0 `xruns` growth and 0 heard stutters, there is no fault to remove, and the candidate's PASS on that unit says nothing about H1. Section 8 marks where this applies. This is now likely on D-POCO, where the baseline already starts at 90 ms.
- **The rig's audio settings are a deliberate worst case**: `use-aac-audio` true, `audio-latency-multiplier` 2, `audio-queue-capacity` 20. Do not reset them to defaults. Read each unit's values at the start of the round and record them. If a unit differs, write the worst-case values, and say so in Setup notes. Both arms on a unit must run with the same file.

## 4. Pre-flight (one batched ask to the operator, before R0)

Check what adb can check first, then ask for the rest in one message, then run.

1. **The operator listens in R1 to R4**: about 40 minutes of listening in four holds, in stages H and P. Media volume on D-HP and D-POCO at a level where a 20 ms gap is audible, and the same level for both arms on a unit.
2. **Sound comes from the unit's own speaker.** D-POCO has `Magnetic Speaker` bonded. Switch that speaker off for the round (rig-quirks `topics/audio.md`, A2DP speaker entry).
3. **Cable moves at each stage change** (section 6). The operator also replugs D-MOTO once if a USB session does not form (section 8, stop rules).
4. **D-MOTO stays unlocked** with no PIN prompt. Check: `adb -s ZY22GC3BM4 shell dumpsys window | grep -a mCurrentFocus`. Then `adb -s ZY22GC3BM4 shell svc power stayon true`.
5. **D-MOTO is bonded to D-HU (`Navegadortz2`)** for stage W. Check: `adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -A12 'Bonded devices'`.
6. **VLC and a long MP3 on D-MOTO.** `adb -s ZY22GC3BM4 shell pm list packages org.videolan.vlc` must print the package. Run `adb -s ZY22GC3BM4 shell appops set org.videolan.vlc MANAGE_EXTERNAL_STORAGE allow`. Make one file of 12 minutes or more, so that no run restarts the track: `adb -s ZY22GC3BM4 shell 'cd /sdcard/Music && cat ohu1008.mp3 ohu1008.mp3 ohu1008.mp3 ohu1008.mp3 > ohu-stutter-long.mp3'` (4 x 216 s). If `ohu1008.mp3` is missing, push any MP3 of 3 minutes or more and use it four times.
7. **USB permission state.** On D-HP and D-POCO: `adb shell dumpsys usb | grep -a -i -A4 headunitrevived`. Record it. Do not change it.
8. **Batteries.** D-HP and D-POCO power D-MOTO over OTG. Each host 60% or more before its stage, D-MOTO 50% or more.

## 5. Settings keys

Write with the app stopped (TESTING-TEMPLATE §1) with the rig's writers: `set_prefs_runas_host.py` on D-HP and D-POCO, `set_hu_settings_host.py` on D-HU. Back up each unit's `settings.xml` first, diff it against its last backup, and put the delta in Setup notes. On D-HU, `stat` `shared_prefs/` and report the owner. **After every APK install, read every key below back before the next launch** (rig-quirks `topics/tooling.md`: an install can wipe `settings.xml`).

**Every unit, every run (`AKEYS`):**

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

Do not write `static-audio-focus`. Read it back on each unit and record it. It must be the same in both arms on a unit.

**D-HP and D-POCO as USB hosts (`UKEYS`), in addition:** `set:connection-modes=usb bool:auto-start-on-usb=true bool:auto-connect-last-session=true bool:reopen-on-reconnection=true bool:use-libusb=false`. With `usb` the only connection mode, the wireless stack does not arm. Use the fixed `set:` writer (rig-quirks `topics/tooling.md`, empty `set:` entry).

**D-HU for R5 (`WKEYS`), in addition:** `int:wifi-connection-mode=3 int:native-driver-selection-mode=0 bool:native-poke-all-paired=true int:native-aa-wake-damage-verdict=0 del:aa-exit-action`. Leave `connection-modes` as found if it holds `wifi`; if not, write `set:connection-modes=wifi`.

`allow-external-configuration` is not needed. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on this branch.

## 6. Stages, ports and shell helpers

Three stages. D-HU stays cabled to the PC for the whole round.

| Stage | Head unit | Phone | Ports |
|---|---|---|---|
| H (R1, R2) | D-HP, wireless adb | D-MOTO on D-HP's OTG port, wireless adb | D-HP and D-MOTO off the PC |
| P (R3, R4) | D-POCO, wireless adb | D-MOTO on D-POCO's OTG port, wireless adb | D-POCO and D-MOTO off the PC |
| W (R5) | D-HU, cabled | D-MOTO, cabled | D-POCO in airplane mode, so it cannot take the poke |

Before stage H, with D-HP, D-POCO and D-MOTO still cabled: install `baseline.apk` on D-HP and D-POCO (`adb install -r -d`), then `adb -s <unit> tcpip 5555` on D-HP, D-POCO and D-MOTO. Read each IP with `adb -s <unit> shell ip -4 addr show wlan0`. Then the operator moves the cables for stage H. D-HP's serial is whatever `adb devices` lists (it has read `CNU350BGBJ` and `0123456789ABCDEF`).

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

**The operator's terminal** (R1 to R4 only). Same `HU` and the same `A` function. Start it after `<run>-music` and stop it with Ctrl-C after `<run>-end`:

```bash
# </dev/null: without it, adb reads the next Enter presses and those stamps are lost.
heard() { n=0; while read -r _; do n=$((n+1)); A shell log -t HEARD "stutter-$n" </dev/null; echo "stamped $n"; done; }
```

`log -t` is a native binary, so a stamp costs the unit almost no CPU. `input` and `am` start a Java process, which costs real CPU on D-HP; both arms carry the same swipes, and every swipe is stamped `RIGSWIPE`.

**Swipe coordinates, once per stage**, while the projection is up in the stage's first run: `A shell uiautomator dump /sdcard/u.xml`, `A pull /sdcard/u.xml`, then read the first node's `bounds="[0,0][W,H]"`. Set `SX1` to 0.7 x W, `SX2` to 0.3 x W and `SY` to 0.5 x H. Use the same values in both runs of the stage (rig-quirks `units/D-SAM-and-D-HP.md`: `input` takes the dump's coordinate space).

Stage H: `HU=<D-HP IP>:5555 PH=<D-MOTO IP>:5555`. Stage P: `HU=<D-POCO IP>:5555 PH=<D-MOTO IP>:5555`. Stage W: `HU=27870808938846 PH=ZY22GC3BM4`. Run `adb -s <unit> logcat -G 16M` once on each unit; if D-HP refuses `-G`, record it and go on. Grep every capture with `grep -a`.

**Thermal gate:** `rig_thermal.sh wait 75` before every run, and the watch during it (rig-quirks `topics/tooling.md`). Re-run a run once if the throttle counter rose or the host reached 90C.

**End of every run:** `send ACTION_DISCONNECT`, `sleep 5`, `adb -s $PH shell am force-stop $VLC`, `send ACTION_EXIT`, `sleep 3`, `A shell am force-stop $PKG`, stop both captures by pid, and restore the unit's round backup with the run's keys.

## 7. The deciding lines

All head unit lines are `AppLog.i` or `AppLog.w` with no `LOG_VERBOSE` guard, so INFO (`log-level` 2) carries them. The mixer lines go through `AudioDiagnostics`, which appends ` eventElapsedMs=N`. Each string was checked with `grep -F -r` against `app/src/main` on `95b78b1a`; composed lines are listed by their fixed parts. Grep them in `<run>-hu.txt` between `<run>-music` and `<run>-end`, unless the table says otherwise.

**Before counting, remove duplicate lines** from any capture that `alive` restarted: `awk '!seen[$0]++' <run>-hu.txt > <run>-hu.dedup.txt`, and count on that file.

| Id | Grep (`grep -a`) | What to read |
|---|---|---|
| L-OPEN | `AudioMixer: id=[0-9]* opened ` | the placeholder line (section 3). Report it only; it decides nothing. |
| L-MIX | `AudioMixer: id=[0-9]* .* loopGapMax=` | the 10 s device line. Read `effective=` (frames; divide by 48 for ms), `xruns=`, `loopGapMax=`, `writeMax=`, and the roll-up `stutters= mixer= flowReset= linkLate= sourceGap= edge= unknown= suppressed=` |
| L-CH6 | `AudioMixer: id=[0-9]* channel=6 target=` | the 10 s media channel line. Read `arrivalGapMax=`, `concealedFrames=`, `staleFrames=`, `compressedFrames=` |
| L-ST | `AudioStutter: id=` | one per audible event. Read the time, `kind=`, `cause=`, `unfedMs=`, `deviceMs=`, `arrivalGapMs=`, `sourceGapMs=`, `edgeMs=`. At most 2 a second per channel; the rest are in `suppressed=`. |
| L-OUT | `AudioMixer: id=[0-9]* output .* requested=` | the real buffer. Whole capture: the first line comes when the AudioTrack opens, before the window. Read `output AudioTrack`, `requested=`, `effective=`, `minimum=`, `maximum=`. Later lines are buffer changes, `AppLog.w` when one follows an xrun. Count the lines in the window. |
| L-OVF | `PCM overflow channel=` | must be 0 in R5 |
| L-DRIFT | `AUDIO clock drift=` | composed `AapAudio: AUDIO clock drift=+N ppm over Ns (N windows)`. Report the last value. |
| L-TIME | `AUDIO media timing:` | report the last line |
| L-DEC | `AudioDecoder.start: channel=6` | `codec=` must be `AAC_LC` or `AAC_LC_ADTS`; `latencyMultiplier=` and `queueCapacity=` must match section 5 |
| L-MSR | `Media Start Request AUDIO:` | composed from `Media Start Request %s: session=`. The media channel opened. |
| L-SINK | `Media Sink Setup Request: ` | report the line for `channel AUDIO` |
| L-SSL | `SSL handshake complete` | one per session at INFO |
| L-ACC | `Sending acc start` | the AOA switch, USB runs |
| L-WIFI | `WifiLauncher: Initializing WiFi Mode: ` | USB runs: must be 0. R5: 1 or more, reading `NATIVE`. |
| L-NOWIFI | `WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen ` | USB runs: report |
| L-FREQ | `WifiDirectManager: onGroupInfoAvailable: ` | R5: read `Freq: N MHz`; must be 4900 or more |
| L-MATCH | `MATCH! Starting AapService` | discard rule, R5 |
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

**R2 is the point of the round.** Run in this order: R0, stage H (R1, R2), stage P (R3, R4), stage W (R5). Write the results file after every run.

Every run's window starts at `<run>-music`, 20 s after the media channel opens, so the start-up settle is not graded. Durations below are the hold only.

### R0. Build and identity gate (about 30 min)

1. Build both SHAs (section 1). Run `run_unit_tests.sh` on the candidate. Expect 3177 tests, 0 failures.
2. `md5sum baseline.apk candidate.apk`. The two must differ.
3. `unzip -p candidate.apk 'classes*.dex' | strings | grep -c -F GROWTH_MS` must be 1 or more. Run the same on `baseline.apk` and record the count (expected 0; the commit check in step 5 decides if it is not).
4. Install `baseline.apk` on D-HP and D-POCO, and `candidate.apk` on D-HU, with `adb install -r -d`. Pull each installed APK (`pm path`, then `adb pull`) and `md5sum` it. It must match its source.
5. On each unit: `send ACTION_QUERY_STATE`. The `commit` on the `data=` line must start with `a88e881b` on D-HP and D-POCO, and with `95b78b1a` on D-HU. A `-dirty` suffix from an untracked file is accepted; say so.

PASS: 0 test failures, md5s differ and match, commits match. **A failure here stops the round.**

### Stage H priming (not graded, about 5 min)

On D-HP with `AKEYS` + `UKEYS`, baseline installed, D-MOTO on D-HP's OTG port:

1. `HUCAP SH; PHCAP SH; mark SH-start`
2. `A shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, `sleep 5`, `send ACTION_CHECK_USB`. Wait up to 60 s for L-SSL.
3. `media`. Wait up to 20 s for L-MSR.
4. Take the swipe coordinates (section 6).
5. End the run (section 6).

If a system USB dialog appears, the operator answers it as earlier USB rounds on D-HP did. Record the activity name from `A shell dumpsys window | grep -a mCurrentFocus` and what was ticked. If L-SSL or L-MSR does not come, the operator replugs D-MOTO once and this step repeats once. A second failure makes R1 and R2 UNTESTABLE; go to stage P.

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
- The first L-OUT reads `output AudioTrack`, `effective=2229` and `maximum=` below 19200 (expected 3360). If `effective=` differs, record it; the check is `maximum=` below 19200.
- L-DEC reads `channel=6`, `codec=AAC_LC` or `codec=AAC_LC_ADTS`, `latencyMultiplier=2`, `queueCapacity=20`.
- L-ACC 1 or more, L-SSL exactly 1 in the run, L-WIFI 0.
- L-MIX lines in the window: 55 or more.

No PASS bar: this is the baseline. **Record:** the first L-OUT `effective=` and `minimum=` in frames and ms; every L-MIX `loopGapMax=` and `writeMax=` with its time; the count of L-MIX windows whose `loopGapMax` is above the `effective` ms; the L-ST counts by `cause=` and by `kind=`; the L-CH6 deltas of `concealedFrames`, `staleFrames` and `compressedFrames`; the L-HEARD count and how many matched, with their causes; L-DRIFT, S-UND, S-UF, the L-TP median; and the phone counts.

### R2. D-HP (API 17), USB, candidate (10 min hold) (the point)

Install `candidate.apk` on D-HP over wireless adb (`adb -s $HU install -r -d candidate.apk`; it took 97 s in an earlier round). Pull and `md5sum` it, then `send ACTION_QUERY_STATE`: the commit must start with `95b78b1a`. Read every key back. Then run R1's steps 1 to 8 with label `R2`.

Preflight checks: as R1, except the first L-OUT must read `maximum=19200` and `effective=` 9600 or more (200 ms).

PASS, all of these:
- L-ST with `cause=MIXER_STALL`: 0 in the window.
- Every L-MIX `loopGapMax=` is under 190 ms. A window at 190 ms or above is a FAIL only when it also holds an L-ST `cause=MIXER_STALL` line; otherwise report it.
- The R2 delta of each of `concealedFrames`, `staleFrames` and `compressedFrames` is not above R1's, or is 4800 frames (100 ms) or less. The 4800 tolerance is this brief's, so that two near-zero counts do not fail on noise.
- Phone: P-STATE 1 or more, P-FATAL 0.

**Reachability.** If R1 shows 0 `cause=MIXER_STALL`, 0 L-MIX windows with `loopGapMax` above the effective ms, and 0 L-HEARD, then R1 had no fault for R2 to remove: grade R2 **INCONCLUSIVE** for H1, and still report every number.

**Sensory result, not part of the verdict (§0):** the R2 L-HEARD count against R1's. The plan's bar is 1 or fewer heard in R2. Report both counts and the causes of the matched stamps.

### Stage P priming (not graded, about 5 min)

The operator moves D-MOTO to D-POCO's OTG port. `adb -s <D-POCO IP>:5555 shell svc bluetooth disable`, then check `dumpsys bluetooth_manager | grep -a 'enabled'` reads false. Then run the stage H priming steps on D-POCO with label `SP`. D-POCO already has `baseline.apk` from R0; read its keys back.

### R3. D-POCO (API 35), USB, baseline (8 min hold)

Setup: `AKEYS` + `UKEYS` on D-POCO, `baseline.apk`. Run R1's steps with label `R3` and `hold R3 480`.

Preflight checks: as R1, except the first L-OUT reads `output AudioTrack`, `effective=` below 4800 (expected 4320) and `maximum=` below 19200 (expected 5280). L-MIX windows: 44 or more.

No PASS bar. **Record** as R1, plus the `xruns=` delta and the count of L-OUT lines.

### R4. D-POCO (API 35), USB, candidate (8 min hold)

Install `candidate.apk` on D-POCO over wireless adb, check the md5 and the commit, read the keys back. Run R1's steps with label `R4` and `hold R4 480`.

Preflight checks: as R3, except the first L-OUT must read `maximum=19200` and `effective=` 4800 or more.

PASS, all of these:
- `xruns=` delta 0. If R3's delta is also 0, a delta of 1 still passes.
- The R4 deltas of `concealedFrames`, `staleFrames` and `compressedFrames` are each not above R3's, or 4800 frames or less.
- Every L-MIX `effective=` is 4800 or more.
- Phone: P-STATE 1 or more, P-FATAL 0.

Report the R4 L-ST count with `cause=MIXER_STALL`; it is not a condition here, because the `xruns` counter grades this unit directly.

**Reachability.** If R3 shows `xruns` delta 0, 0 `cause=MIXER_STALL` and 0 L-HEARD, grade R4 **INCONCLUSIVE** for H1.

**Sensory result, not part of the verdict:** R4 L-HEARD against R3's.

### R5. D-HU, Native AA over WiFi Direct 5 GHz, candidate (5 min hold, log only)

The operator moves D-MOTO back to the PC. `adb -s <D-POCO IP>:5555 shell cmd connectivity airplane-mode enable`, so D-POCO cannot take D-HU's poke. Setup: `AKEYS` + `WKEYS` on D-HU, `candidate.apk` from R0. D-MOTO in airplane mode at the start (TESTING-TEMPLATE §4).

1. `rig_thermal.sh wait 75`
2. `HUCAP R5; PHCAP R5; mark R5-start`
3. `A shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, `sleep 20`
4. `adb -s $PH shell cmd connectivity airplane-mode disable`, `adb -s $PH shell svc wifi enable`, `adb -s $PH shell svc bluetooth enable`, `mark R5-phone-on`. Wait up to 90 s for L-SSL. If none, re-run R5 once; two misses make R5 UNTESTABLE.
5. `media`. Wait up to 20 s for L-MSR.
6. `sleep 20`, `mark R5-music`. Take the swipe coordinates for D-HU's dump.
7. `hold R5 300`
8. `mark R5-end`. End the run (section 6). Then `adb -s $PH shell svc power stayon false`.

Preflight checks: L-WIFI reads `NATIVE`. L-FREQ reads `Freq: ` 4900 MHz or more; if the group landed on 2.4 GHz, re-run once. The first L-OUT reads `maximum=19200`. L-DEC as R1. Discard and re-run once on L-MATCH 1 or more, or a second L-SSL.

PASS, all of these:
- L-OVF is 0.
- In every L-CH6 window whose `arrivalGapMax=` is under 500 ms, the `staleFrames` increase over the previous line is 0.
- Phone: P-STATE 1 or more, P-FATAL 0.

Report the windows with `arrivalGapMax` of 500 ms or more, with their `staleFrames` increase and the L-ST causes in them. That is the link-hole loss (H2), which this branch does not change.

### Stop rules

- R0 fails: stop the round.
- A run misses its session after the retries in its steps: mark it UNTESTABLE and go to the next stage. If R1 is UNTESTABLE, R2 still runs, and R2's verdict becomes INCONCLUSIVE.
- A preflight check fails twice in one run: mark the run UNTESTABLE (setup), and go on.
- The host stays hot for 30 min: stop, and mark the rest UNTESTABLE (host thermal).

### Closing

Put each unit back as it was: restore its round-start `settings.xml` and read it back; on D-HP and D-POCO, `adb install -r -d` the build that was there before R0 if it was recorded, otherwise leave the baseline and say so. D-POCO: airplane mode off, `svc bluetooth enable`, check with `dumpsys`. D-MOTO: `svc power stayon false`, airplane mode off, WiFi and Bluetooth on. All phones back to USB adb (`adb -s <unit> usb`).

## 9. What a result means (for the grader)

- **R1 stamps match `staleFrames` jumps or `LINK_LATE`, not `MIXER_STALL` or a high `loopGapMax`:** H1 is not the D-HP cause. The engineer re-diagnoses; this branch is not tuned.
- **R1 and R3 `cause=` shares mostly `LINK_LATE` or `PHONE_FLOW_RESET` on USB:** the fault is not the device buffer. Report it first in the results.
- **R2 and R4 PASS, with R1 and R3 reachable:** the branch removes the USB stutter on both units.
- **`compressedFrames` falls on R2 or R4 against the baseline:** the quiet-passage clicks were a result of H1. Clicks heard with `compressedFrames` flat belong to a separate cause.
- **L-DRIFT steady above about 100 ppm in R1 to R4:** report it; it opens a separate clock-drift ticket.
- **Unmatched stamps:** each is a stutter no instrument saw. Give the count per run.
- **Phone counts non-zero on a USB run (P-PERM, P-QUEUE, P-ACK, P-DROP):** the loss in that run started on the phone. Report it beside the verdict; it is not a FAIL of this branch.

## 10. Do not re-run

- AAudio under stress and its fallback: `adaptive-audio` round 1 A5 passed. This round keeps AAudio off.
- The AAC permit window: `adaptive-audio` round 1 A4, untestable on this phone.
- A 15 minute drift soak: `clock drift=` grades drift inside each run.
- Navigation prompts from mock locations: retired in `audio-sink-jitter` round 1.
- The stall model rows, the policy sizes and the attribution rules: JVM tests on the branch (3177 tests).
- PR #1090's floor, waveform-matched recovery and ending ramp: `pr-1090-audio-transitions` rounds 1 and 2. Both arms here carry them.

## 11. Report back

1. **D-HP:** R1 against R2 for the count of `cause=MIXER_STALL`, the `compressedFrames` delta, and the heard stamps (count, matched, unmatched).
2. **D-POCO:** R3 against R4 for the `xruns` delta, the three bank deltas, and the heard stamps.
3. **R5:** the L-OVF count and the `staleFrames` increase in windows under 500 ms.

Also give the `cause=` shares of R1 and R3, the last `clock drift=` value of each run, and every phone count. Captures go to the release `rig-evidence-audio-stutter` as `audio-stutter-round1-captures.zip` (TESTING-TEMPLATE §7). Run time: about 41 minutes of holds; about 2 hours 15 minutes with builds, stage changes and priming.

The first block below holds the phone's and the system's strings, which are not in this repository: the phone strings are checked against the Gearhead 17.8 decompile, and the two system strings are AudioFlinger and media metrics lines. The last block, `decisive-strings`, holds the app's own strings, each checked with `grep -F -r` against `app/src/main` on `95b78b1a`. Composed lines appear as their fixed parts. Some lines end in a space, which is part of the string.

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
WifiDirectManager: onGroupInfoAvailable: 
MATCH! Starting AapService
Throughput over 
AutomationReceiver: 
AutomationMarker: 
```
