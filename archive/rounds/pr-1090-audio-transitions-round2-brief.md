# pr-1090-audio-transitions round 2 brief

A short D-POCO-only round. It asks one question that round 1 could not answer: with the same device buffer in both arms, does waveform-matched recovery leave a larger backlog than the old 1 ms recovery? It also checks one app fix found in round 1, on the same unit. The operator listens for under 1 minute of music per audio run and stamps each click heard. Nobody listens to the test tone.

Publish as `pr-1090-audio-transitions-round2-brief.md`. Results go in `pr-1090-audio-transitions-round2-results.md`.

## 1. Build and baseline

| | |
|---|---|
| Candidate (P) | `fork/review/pr-1090` @ `f81fd51aede743ff5da9185f8cdc960a497764a1` (`f81fd51a`), the PR head, the same build as round 1 |
| Control (C1) | `fork/review/pr-1090~2` @ `528a174125fad9b5d936aca2ce724420e0a630fb` (`528a1741`), the PR's commit 1 only: the buffer floor without matched recovery or the ending ramp |
| Boot fix (F) | `fork/fix/boot-service-refusal` @ `bb5ff8d5`, one commit on `main` `ec9d9c33`. Used in R3 only |
| History | `review/pr-1090` is unchanged since round 1. `fix/boot-service-refusal` is new |

```bash
git fetch fork review/pr-1090 fix/boot-service-refusal
git rev-parse fork/review/pr-1090              # must print f81fd51aede7...
git rev-parse fork/review/pr-1090~2            # must print 528a174125fa...
git rev-parse fork/fix/boot-service-refusal    # must print bb5ff8d5...
git rev-parse fork/fix/boot-service-refusal~1  # must print ec9d9c33...
```

Build each SHA in its own detached worktree (`wt-new.sh`) with `build_hur_cool.sh`, after the thermal gate. Copy `local.properties` from the main checkout into each new worktree before the build (round 1, Setup notes). Copy each APK out of `apks/` at once into `pr-1090-round2/` as `c1.apk`, `candidate.apk` and `boot.apk`. You may reuse round 1's `candidate.apk` if its md5 is `1c1a9e55f18bf70eb66b87fda452cfb4`. Run `run_unit_tests.sh` once, on the `boot.apk` worktree.

## 2. What this is and why

**Round 1** (`pr-1090-audio-transitions-round1-results.md`) passed the floor, loss and stream-end bars, and failed the recovery bar on D-POCO (R2) and D-HU (R6). The median excess (`depth` minus `target`) rose by 69 ms on D-POCO and 73 ms on D-HU.

**Why that bar did not test commit 2.** On those two units commit 1 raised the AudioTrack buffer from 960 to 4320 frames, which is 70 ms more. The mixer renders in bursts of the device buffer (`AudioMixer.renderBurstFrames`). `AdaptivePcmBuffer` counts the extra burst as `outputSlack` and does not repay backlog inside it. So a larger buffer raises the excess by itself. On D-HP, where the buffer was the same in both arms, the candidate passed (+14.5 ms).

**This round** compares C1 with P. Both arms carry commit 1, so both should run at 4320 frames on D-POCO. The only difference is commit 2 (matched recovery) and commit 3 (ending ramp). The hold grades commit 2 from the log. The music edges give an ear comparison of commit 3: C1 ends a stream with silence, and P ends it with a 5 ms ramp.

**The boot fix (R3).** In round 1, any broadcast to the force-stopped app on D-POCO (API 35) made Android send `LOCKED_BOOT_COMPLETED` to `BootCompleteReceiver`. The receiver called `startForegroundService`, Android refused it (`ForegroundServiceStartNotAllowedException ... mAllowStartForeground false`), and the app crashed. Three times the "Application Error" dialog covered `MainActivity` and no session formed. `bb5ff8d5` catches the refusal, logs it and gives back the boot strike. Round 1 is the control: the crash happened on `ec9d9c33` every time (R1 at 07:50:27, and R2 attempts 1 to 3).

## 3. What is different this round

- **D-POCO only, USB, D-MOTO as the phone.** D-HP and D-HU are not used.
- **Start order.** The audio runs launch `MainActivity` before the first broadcast, so the crash cannot happen in R1 or R2. R3 sends the broadcast first on purpose.
- **The floor is read from L-OUT, not L-OPEN.** L-OPEN prints a fixed `effective=960 minimum=960` before the AudioTrack exists.
- **The mixer-work bar is comparative and uses the hold window only.** Both arms spike above 10 ms at run start and at the edges.
- **`heard` reads stdin only.** Round 1's form let `adb` take the Enter presses. The fixed form is in section 6.
- **A key resume works on this pairing** (round 1: 3 of 3 cycles by key). The edges take about 52 s.
- **After `vlcreset; tone`, the phone may not send a new `Media Start Request`.** Round 1 saw this in every USB run, and the hold still measured the tone. Record whether L-MSR came. Do not re-run for it.
- **Write settings with the app force-stopped**, then read every key back (round 1, Setup notes item 5).
- **The rig's audio settings are a deliberate worst case**: `use-aac-audio` true, `audio-latency-multiplier` 2, `audio-queue-capacity` 20. Do not reset them.
- **Expected INCONCLUSIVE:** the recovery bar if C1's hold-window `compressedFrames` delta is 0, or if the two arms' L-OUT `effective=` differ. R3 if no `Boot auto-start: received action=` line comes, because then the trigger did not happen.

## 4. Pre-flight (one batched ask to the operator, before R0)

Check what adb can check first, then ask for the rest in one message, then run.

1. **The operator listens in the edges of R1 and R2**: about 52 s of music per run, under 2 minutes in all, at the same volume in both runs. The tone holds need nobody present.
2. **Sound comes from D-POCO's own speaker.** D-POCO's Bluetooth is switched off for the round (section 6), then check `adb shell dumpsys audio` reports `speaker(2)`.
3. **Cable moves:** D-MOTO on D-POCO's OTG port for the whole round, and one replug if a session does not form.
4. **D-MOTO unlocked, no PIN prompt.** Check `adb -s ZY22GC3BM4 shell dumpsys window | grep -a mCurrentFocus`. Then `adb -s ZY22GC3BM4 shell svc power stayon true`.
5. **The tone file.** If `adb -s ZY22GC3BM4 shell ls -l /sdcard/Music/ohu-1090-tone.wav` shows no file, generate and push it with round 1's section 4 item 6 script. Check `/sdcard/Music/ohu1008.mp3` is there. Run `adb -s ZY22GC3BM4 shell appops set org.videolan.vlc MANAGE_EXTERNAL_STORAGE allow`.
6. **Batteries.** D-POCO 60% or more, D-MOTO 50% or more.

## 5. Settings keys

Back up D-POCO's `settings.xml` first, diff it against round 1's round-start backup, and put the delta in Setup notes. Force-stop the app, then write with `set_prefs_runas_host.py`. **After every APK install, force-stop the app and read every key below back before the next launch.**

`AKEYS` and `UKEYS` as round 1 section 5:

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
| `set:connection-modes=usb bool:auto-start-on-usb=true bool:auto-connect-last-session=true bool:reopen-on-reconnection=true bool:use-libusb=false` | USB host. `auto-start-on-usb` true is also the branch R3 needs |

Do not write `static-audio-focus` or `auto-start-on-boot`. Read both back and record them; each must be the same in every run. If `auto-start-on-boot` reads true, R3 takes the boot-start branch instead of the USB branch; the PASS bar is the same.

## 6. Ports and shell helpers

D-POCO and D-MOTO on wireless adb, D-MOTO on D-POCO's OTG port. Before R1, with both cabled: `adb -s <unit> tcpip 5555` on each, read each IP with `adb -s <unit> shell ip -4 addr show wlan0`, then the operator moves the cables. `HU=<D-POCO IP>:5555 PH=<D-MOTO IP>:5555`. Run `A logcat -G 16M` once. Then `A shell svc bluetooth disable` and check `A shell dumpsys bluetooth_manager | grep -a 'enabled'` reads false.

Use round 1's section 6 helpers unchanged (`PKG`, `RX`, `VLC`, `TONE`, `MUSIC`, `A`, `send`, `mark`, `HUCAP`, `PHCAP`, `alive`, `vlcreset`, `music`, `tone`, `swipe`, `hold`, `waitfor`, `edges`). Swipe values for D-POCO, from round 1's dump (`[0,0][2400,1080]`): `SX1=1680 SX2=720 SY=540`.

**The operator's terminal** (R1 and R2 only). Same `HU` and `A`. Start it at `<run>-edges`, stop it with Ctrl-C at `<run>-edges-end`. The `</dev/null` keeps `adb` from reading the Enter presses:

```bash
heard() { n=0; while read -r _; do n=$((n+1)); A shell log -t HEARD "click-$n" </dev/null; echo "stamped $n"; done; }
```

Check it once before R1: press Enter twice, then `A logcat -d | grep -a -c 'I/HEARD'` must be 2 or more. Then `A logcat -c`.

**Thermal gate:** `rig_thermal.sh wait 75` before every run. Re-run a run once if the throttle counter rose or the host reached 90C.

**End of every run:** `send ACTION_DISCONNECT`, `sleep 5`, `adb -s $PH shell am force-stop $VLC`, `send ACTION_EXIT`, `sleep 3`, `A shell am force-stop $PKG`, stop both captures by pid.

## 7. The deciding lines

Round 1's section 7 tables apply unchanged (L-OPEN to S-UND, P-STATE, P-FATAL), and so do its rules for deltas, excess and heard stamps. The **edge window** is `<run>-edges` to `<run>-edges-end`. The **hold window** is `<run>-music` to `<run>-hold-end`. Grep with `grep -a`. Each app string below was checked with `grep -F` against `app/src/main` on its build.

Added for R3, in `R3-hu.txt`:

| Id | Grep (`grep -a`) | What to read |
|---|---|---|
| L-BOOT | `Boot auto-start: received action=` | the trigger happened. Read the action |
| L-REFUSED | `Boot auto-start: Android refused to start AapService` | the fix caught a refusal. `AppLog.w` |
| S-RCV | `Unable to start receiver com.andrerinas.openheadunit.app.BootCompleteReceiver` | system line, the round 1 crash. Must be 0 |
| S-FATAL | `Process: com.andrerinas.headunitrevived` | system line, the line after `FATAL EXCEPTION` for this app. Must be 0 |

## 8. Runs

**R2 is the point of the round.** Order: R0, R1, R2, R3. Write the results file after every run.

### R0. Build and identity gate (about 30 min)

1. Build the three SHAs (section 1). Run `run_unit_tests.sh` on the `boot.apk` worktree. Expect 0 failures; record the count.
2. `md5sum c1.apk candidate.apk boot.apk`. All three must differ.
3. `unzip -p <apk> 'classes*.dex' | strings | grep -c -F <s>`:
   - `AudioTrackBufferSizing`: 1 or more in `c1.apk` and `candidate.apk`.
   - `PcmOverlap`: 0 in `c1.apk`, 1 or more in `candidate.apk`.
   - `Android refused to start AapService`: 1 or more in `boot.apk`, 0 in the other two.
4. Install `c1.apk` on D-POCO with `adb install -r -d`. Pull the installed APK and `md5sum` it. It must match.
5. `A shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, `sleep 5`, then `send ACTION_QUERY_STATE`. The `commit` must start with `528a1741`. Then force-stop the app and write and read back the keys (section 5).

PASS: 0 test failures, md5s differ and match, strings as listed, commit matches. **A failure here stops the round.**

### R1. D-POCO (API 35), USB, C1

Setup: section 5 keys, `c1.apk`. Read the keys back.

1. `rig_thermal.sh wait 75`
2. `HUCAP R1; PHCAP R1`
3. `A shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, `sleep 5`, `mark R1-start`
4. `send ACTION_CHECK_USB`. Wait up to 60 s for L-SSL. If none, `send ACTION_CHECK_USB` once more and wait 60 s. If still none, the operator replugs D-MOTO once and you wait 60 s. If still none, `mark R1-nossl` and end the run.
5. `vlcreset; music`. Wait up to 35 s for L-MSR.
6. `sleep 15`, `mark R1-edges`. The operator starts `heard`.
7. `edges R1 3`, `mark R1-edges-end`. The operator stops `heard`. **Listening ends here.**
8. `vlcreset; tone`. Wait up to 35 s for L-MSR, and record whether it came.
9. `sleep 20`, `mark R1-music`, `hold R1 300`, `mark R1-hold-end`
10. `mark R1-end`. End the run (section 6).

**Preflight checks** (a failed check is a setup failure: fix and re-run once, never grade it):
- L-OUT reads `output AudioTrack` with `minimum=` above 960. Record `effective=` and `minimum=`. Round 1 read `requested=4320 effective=4320 minimum=3844` for P on this unit.
- L-DEC `codec=AAC_LC` or `AAC_LC_ADTS`, `latencyMultiplier=2`, `queueCapacity=20`. L-ACC 1 or more, L-SSL exactly 1, L-WIFI 0.
- L-CH6 lines in the hold window: 27 or more.

No PASS bar: this is the control. **Record:** L-OUT in full; every L-MIX `effective=`, and `mixWorkMax=` split into the hold window and the rest; the hold-window deltas of `concealedFrames`, `staleFrames`, `compressedFrames` and `xruns`; the median and maximum excess; for each edge cycle, the L-STOP time, the first L-REL after it and the gap in ms, key or relaunch, and the next L-CH6 `depth=` after the L-MSR; the L-HEARD count per cycle and unmatched; S-UND, the L-TP median and the phone lines.

**Attribution check (no bar):** give R1's median excess minus round 1 R1's (-18.5 ms, `ec9d9c33` at 960 frames). If the buffer explains round 1's FAIL, this is near +70 ms.

### R2. D-POCO (API 35), USB, candidate (the point)

Install `candidate.apk` over wireless adb (`adb -s $HU install -r -d candidate.apk`). Pull and `md5sum` it. Launch `MainActivity`, `sleep 5`, `send ACTION_QUERY_STATE`: the commit must start with `f81fd51a`. Force-stop the app and read every key back. Then run R1's steps 1 to 10 with label `R2`.

Preflight checks: as R1.

PASS, all of these:
- **Same buffer (precondition):** R2's L-OUT `effective=` equals R1's. If not, grade the recovery bar **INCONCLUSIVE** and give both values.
- **Floor:** in both runs, every L-MIX and L-OUT `effective=` is at or above that run's L-OUT `minimum=`.
- **Recovery repays (commit 2):** R2's median excess is not more than R1's plus 20 ms, and its maximum excess not more than R1's plus 40 ms.
- **No new loss:** the R2 deltas of `concealedFrames` and `staleFrames` are each not above R1's, or are 4800 frames (100 ms) or less.
- **No extra mixer work:** in the hold window, R2's largest `mixWorkMax=` is under 10 ms and not more than R1's hold-window largest plus 2 ms.
- **Streams still end (commit 3):** R2 has as many L-STOP lines in the edge window as R1. Every cycle that reached L-MSR shows an L-CH6 `depth=` above 0 ms within 15 s of it. R2's median stop-to-release gap is not more than R1's plus 100 ms.
- L-OVF 0, L-ERR 0. Phone: P-STATE 1 or more, P-FATAL 0.

**Reachability.** If R1's hold-window `compressedFrames` delta is 0, recovery never ran: grade the recovery bar **INCONCLUSIVE** and the rest as written.

**Sensory result, not part of the verdict (§0):** heard clicks per edge cycle, R1 against R2. R1 ends each stream with silence and R2 with a 5 ms ramp.

### R3. D-POCO (API 35), boot fix, broadcast to a stopped app (about 5 min)

Install `boot.apk` over wireless adb. Pull and `md5sum` it. Launch `MainActivity`, `sleep 5`, `send ACTION_QUERY_STATE`: the commit must start with `bb5ff8d5`. Read every key back. D-MOTO stays on the OTG port.

1. `rig_thermal.sh wait 75`
2. `A shell am force-stop $PKG`, `sleep 3`
3. `HUCAP R3; PHCAP R3`
4. `mark R3-start`. This broadcast to the stopped app is the trigger. `sleep 10`
5. `A shell dumpsys window | grep -a mCurrentFocus`. Record the line.
6. `A shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, `sleep 5`, `send ACTION_CHECK_USB`. Wait up to 60 s for L-SSL, with the same retries as R1 step 4.
7. `mark R3-end`. End the run (section 6).

PASS, all of these:
- L-BOOT 1 or more between `R3-start` and step 6. If 0, grade R3 **INCONCLUSIVE** (Android sent no boot broadcast).
- S-RCV 0 and S-FATAL 0 in the whole capture.
- The step 5 focus line does not name `Application Error` or an `AppErrorDialog`.
- L-SSL exactly 1, and P-STATE 1 or more.

**Record:** each L-BOOT action and time, the L-REFUSED count and its text, and the first `AapService` line after `R3-start` if one comes before step 6. L-REFUSED 0 with L-BOOT 1 or more means Android allowed the start this time; that still passes.

### Stop rules

- R0 fails: stop the round.
- R1 is UNTESTABLE after its retries: R2 still runs, and its verdict becomes INCONCLUSIVE.
- A preflight check fails twice in one run: mark the run UNTESTABLE (setup), and go on.
- The host stays hot for 30 min: stop, and mark the rest UNTESTABLE (host thermal).

### Closing

Restore D-POCO's round-start `settings.xml` and read it back. `adb install -r -d` the build that was there before R0 (round 1 restored 3.5.0-beta4) and check its md5. D-POCO: `svc bluetooth enable`, check with `dumpsys`. D-MOTO: `svc power stayon false`, and `rm /sdcard/Music/ohu-1090-tone.wav` if nothing else needs it. Both back to USB adb (`adb -s <unit> usb`).

## 9. What a result means (for the grader)

- **Recovery bar PASS:** round 1's FAIL came from the larger buffer of commit 1, not from matched recovery. Give the attribution check figure.
- **Recovery bar FAIL with the same buffer:** matched recovery repays more slowly than the old 1 ms recovery. Report both excess figures and both `compressedFrames` deltas; it is a latency cost, not a click.
- **Clicks heard in R1 and fewer in R2:** the ending ramp is audible. Clicks alike in both: the click comes from the phone's own stop.
- **R3 FAIL on S-RCV or S-FATAL:** the fix did not catch the refusal. Give the stack's `Caused by:` line.

## 10. Do not re-run

- D-HP and D-HU: round 1 R3 to R6. D-HP passed every comparative bar; D-HU's FAIL has the same buffer cause as D-POCO's.
- The crash on `ec9d9c33`: round 1 measured it four times on D-POCO.
- The floor on API 17 and the stream ends on D-HP: round 1 R4.

## 11. Report back

1. **R2 against R1 (the point):** the two L-OUT `effective=` values, the two excess figures, both `compressedFrames` deltas, the hold-window `mixWorkMax`, and the stop-to-release gaps.
2. **The attribution check:** R1's median excess against round 1 R1's.
3. **R3:** PASS or FAIL, L-BOOT, L-REFUSED, and the focus line.
4. **Heard clicks:** per run, per edge cycle and unmatched.

Captures go to the release `rig-evidence-pr-1090-audio-transitions` as `pr-1090-audio-transitions-round2-captures.zip` (TESTING-TEMPLATE §7). Run time: about 10 minutes of unattended tone holds and under 2 minutes of listened music; about 1 hour with builds.

The first block holds the phone's and the system's strings, which are not in this repository. The second holds the app's own strings, each checked with `grep -F` against `app/src/main` on its build. Composed lines appear as their fixed parts. Some lines end in a space, which is part of the string.

```decisive-strings-external
Car connection state changed
FATAL EXCEPTION
disabled due to previous underrun
Unable to start receiver com.andrerinas.openheadunit.app.BootCompleteReceiver
Process: com.andrerinas.headunitrevived
```

```decisive-strings
AudioMixer: id=
output 
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
Throughput over 
AutomationReceiver: 
AutomationMarker: 
Boot auto-start: received action=
Boot auto-start: Android refused to start AapService
```
