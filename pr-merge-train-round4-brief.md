# pr-merge-train, round 4 brief: the audio renderer on the two slowest units

Published name: `pr-merge-train-round4-brief.md`. Results go in `pr-merge-train-round4-results.md`.
Evidence: add `pr-merge-train-round4-captures.zip` to the release `rig-evidence-pr-merge-train`.

Units: D-SAM (head unit, Native wireless) with D-POCO (phone); D-HP (head unit, USB) with D-MOTO (phone). D-HU takes no
part, but stage S turns its Bluetooth off and stage S's end turns it back on.

Queue line: `pr-merge-train-round4-brief.md` (A `3ad7c78c` on `fork/test/pr-merge-train`, B `f9a72d48` = `main`;
round 1's two APKs, no build if they are on hand; units D-SAM, D-POCO, D-HP, D-MOTO, plus D-HU's Bluetooth; SA and HA
are the point).

Pairing: any round that uses none of these five units (§3b). Hand steps: one cable move before stage H, at most one
D-MOTO replug per USB run, one D-POCO unpair only if stage S needs it (§7), and the operator's `heard` stamps in stage H.
Injected taps: none.

**Time budget.** No build if round 1's APKs are on hand (else 30 min), pre-flight 10 min, runs 4 x 8 min, installs
and the cable move 10 min, thermal waits about 8 min. **About 1 h.**

| Run | Unit, route | Arm | What it proves | Minutes |
|---|---|---|---|---|
| SB | D-SAM (API 19), Native wireless | B, `main` | the control on the slow wireless path | 8 |
| **SA** | D-SAM, Native wireless | A | **the point: the new renderer is no worse there** | 8 |
| HB | D-HP (API 17), USB | B | the control on the unit where stutter was heard before | 8 |
| **HA** | D-HP, USB | A | **the point: the new renderer is no worse there** | 8 |

## 1. Build and baseline

The same two arms as round 1. A is `main` plus the bundled H.264 decoder PR and the audio renderer PR. B is `main`.

| Arm | Ref | Commit | Tree | Round 1 APK md5 |
|---|---|---|---|---|
| A (candidate) | `fork/test/pr-merge-train` | `3ad7c78c8860d7da3f4a2429157931a36a03129f` | `596afde2259ce8e5e71247a581e5f7296117b599` | `f365e15aa2dbaf31972c68d09859d278` |
| B (control) | `main` | `f9a72d480d51105870f62df6f0a40a1276e98a34` | `ff8fe6c2ea0a30f9e44276c6e9d3152591173969` | `7dbddf19ee63fa0d0772dcf32878b337` |

Copy `apk-A.apk` and `apk-B.apk` from `rig-data/rounds/pr-merge-train-round1/` into
`rig-data/rounds/pr-merge-train-round4/` and check both md5s against the table. If either is missing or differs,
build it as round 1's §1 does (with `wt-new.sh`, the thermal gate and `MAX_WORKERS=2`), and record the new md5.
The dev host ran the JVM suite at A: 4099 tests, 0 failures. Do not run the unit tests on the rig.

Identity after each install: pull the installed APK (`pm path`, `adb pull`) and match its md5;
`unzip -p <apk> 'classes*.dex' | strings | grep -acF NativeTimeStretchEngine` is 1 or more for A and 0 for B.

## 2. What this is and why

The #1159 reporter hears PCM audio stutter about every 5 s since 3.5.0 on an **Android 4.x** head unit. #1095 (now in
`main`) fixed it over USB for them, but it stays over Native wireless. The maintainer reads their wireless log as
playback falling behind until the buffer fills and old audio is dropped, which means the unit cannot keep up. PR #1177
cuts mixer and waveform-search work and adds optional time-correction engines; the default engine stays `ohu`.

Round 1 ran #1177 on D-HU (Android 14) and saw 0 stale and 0 concealed frames on both arms: D-HU is too fast to show
the fault. This round runs the same A/B on the two slowest units:

- **D-SAM (stage S)** is the reporter's setup: Android 4.4, PCM, Native wireless.
- **D-HP (stage H)** is the unit where the owner heard many stutters (`adaptive-audio` round 1 H1,
  `audio-stutter` round 1 R1: 63 heard, `loopGapMax` up to 1263 ms). D-HP has no WiFi Direct, so it runs USB.

The question on each unit: **is A no worse than B, and is it better?** A also carries the H.264 decoder PR, which
changes code every session runs. Its decoder is off here, but this is that PR's first session below API 21.

## 3. What is different about this round

- **PCM, not AAC.** The rig's worst case is AAC on; this round sets `use-aac-audio` false on purpose, because the
  report is PCM only. Keep the other worst-case values (latency multiplier 2, queue 20).
- **`xruns` reads `N/A` below API 24.** The instruments are the 10 s mixer lines (`loopGapMax=`, the channel 6
  counters) and the two system lines, which may print 0 on these ROMs.
- **D-POCO may be bound to D-HU.** D-SAM as head unit failed to wake D-POCO while D-POCO was bonded to D-HU
  (`rig-quirks/units/D-POCO.md`). Stage S first turns D-HU's Bluetooth off. Only if that does not work does the
  operator unpair D-HU on D-POCO (§7). If they unpair, D-POCO must be paired with D-HU again before the next D-HU round.
- **D-HP runs on wireless adb**, because D-MOTO takes its only port. Its host capture has died silently in long runs:
  `alive` restarts it, and the grader removes duplicate lines before counting.
- **The operator listens in HB and HA** and presses Enter at each stutter heard. No verdict rests on it (§0).
- Read `rig-quirks/topics/tooling.md`, `topics/audio.md`, `units/D-SAM.md`, `units/D-HP.md`,
  `units/D-SAM-and-D-HP.md`, `units/D-POCO.md` and `units/D-MOTO.md`.
- **D-SAM does not charge on its data cable.** Charge it from a separate supply before stage S.
- **Expected INCONCLUSIVE:** a unit whose B run shows no fault (§7, reachability).

## 4. Pre-flight (one batched ask to the operator, before SB)

1. D-SAM 50% or more on a separate charger; D-HP 60% or more; D-MOTO 50% or more.
2. D-MOTO unlocked with no PIN prompt; `adb -s ZY22GC3BM4 shell svc power stayon true`.
3. VLC and `ohu-stutter-long.mp3` (12 min or more) on D-MOTO, as in `archive/rounds/audio-stutter-round1-brief.md`
   §4 step 5. A Spotify playlist of 6 or more tracks ready on D-POCO.
4. D-HP media volume where a 20 ms gap is audible, the same for both arms, on its own speaker.
5. Record `adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -A8 'Bonded'`. D-SAM must be in the list.

## 5. Settings keys

Write with the app stopped with `set_prefs_runas_host.py` (both units are unrooted), and read every key back after
every install. Back up each unit's `settings.xml` first; compare with carriage returns stripped (API 17 and 19 add them).

| Key | Type | Both units | D-SAM only | D-HP only |
|---|---|---|---|---|
| `log-level` | int | 2 | | |
| `onboarding-version` | int | 2 | | |
| `allow-external-configuration` | boolean | true | | |
| `enable-audio-sink` | boolean | true | | |
| `use-aac-audio` | boolean | false | | |
| `audio-latency-multiplier` | int | 2 | | |
| `audio-queue-capacity` | int | 20 | | |
| `use-aaudio-output` | boolean | false | | |
| `kill-on-disconnect` | boolean | false | | |
| `enable-floating-button` | boolean | false | | |
| `video-profile-starvation-cap` | | delete | | |
| `audio-matching-mode` | | leave absent (default `ohu`) | | |
| `connection-modes` | set | | `wifi` | `usb` |
| `wifi-connection-mode` | int | | 3 | |
| `native-ap-transport` | int | | 0 | |
| `native-poke-all-paired` | boolean | | true | |
| `external-bt-zbt-transport` | boolean | | false | |
| `screen-orientation` | int | | 2 | |
| `auto-start-on-usb` | boolean | | | true |
| `auto-connect-last-session` | boolean | | | true |
| `reopen-on-reconnection` | boolean | | | true |
| `use-libusb` | boolean | | | false |

Record `static-audio-focus` and do not write it.

## 6. Helpers

**lib4.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
OUT=rig-data/rounds/pr-merge-train-round4; mkdir -p $OUT
export LC_ALL=C
DHU=27870808938846; SAM=30041c35642d2200; POCO=4f4027e9; MOTO=ZY22GC3BM4
# Stage S: HU=$SAM PH=$POCO.  Stage H: HU=<D-HP IP>:5555 PH=<D-MOTO IP>:5555.
A() { flock "/tmp/rig-adb-${HU//[:.]/_}.lock" adb -s "$HU" "$@"; }
send() { a=$1; shift; A shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; adb -s "$PH" shell log -t RIGMARK "$1"
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
cap() { A logcat -c; stdbuf -oL adb -s "$HU" logcat -v time >> $OUT/$1-hu.txt & echo $! > $OUT/$1-hu.pid
  adb -s "$PH" logcat -c; stdbuf -oL adb -s "$PH" logcat -v time >> $OUT/$1-phone.txt & echo $! > $OUT/$1-phone.pid; }
alive() { for s in hu phone; do kill -0 "$(cat $OUT/$1-$s.pid)" 2>/dev/null && continue
  d=$HU; [ $s = phone ] && d=$PH; echo "$(date +%T) $1-$s capture restarted" >> $OUT/marks.txt
  stdbuf -oL adb -s "$d" logcat -v time >> $OUT/$1-$s.txt & echo $! > $OUT/$1-$s.pid; done; }
uncap() { kill $(cat $OUT/$1-hu.pid) $(cat $OUT/$1-phone.pid) 2>/dev/null; }
after() { sed -n "/AutomationMarker: $1/,\$p" $OUT/$2-hu.txt | grep -acF -- "$3"; }
waitn() { local m=$1 r=$2 s=$3 cap=$4 t=0       # waitn <marker> <run> <string> <seconds>
  while [ $t -lt $cap ]; do [ "$(after $m $r "$s")" -ge 1 ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }
launch() { A shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; }
appexit() { send ACTION_DISCONNECT; sleep 5; A shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null
  sleep 5; A shell am force-stop $PKG; sleep 1; }
# hold <run> <seconds>: check the captures every 60 s
hold() { local end=$((SECONDS+$2)); while [ $SECONDS -lt $end ]; do sleep 60; alive $1; done; }
```

**stage-s.sh** (`run_s SB`, then install A, then `run_s SA`)

```bash
. ./lib4.sh; HU=$SAM; PH=$POCO
ph_wifi() { adb -s $PH shell svc wifi $1; sleep 0.3; adb -s $PH shell svc bluetooth $1; sleep 4; }
run_s() { local R=$1
  th_gate $R || return 1; ph_wifi disable; A shell am force-stop $PKG; cap $R
  launch; sleep 2; mark $R-start; ph_wifi enable
  waitn $R-start $R "SSL handshake complete" 150 || { echo "$R NO_SESSION" >> $OUT/marks.txt; mark $R-end; th_end; appexit; uncap $R; return 1; }
  waitn $R-start $R "Throughput over" 30
  adb -s $PH shell input keyevent 126; sleep 8
  waitn $R-start $R "Media Start Request AUDIO:" 20 || echo "$R NO_MUSIC" >> $OUT/marks.txt
  sleep 20; mark $R-music; hold $R 300; mark $R-end; th_end; appexit; uncap $R; }
```

Before SB: `adb -s $DHU shell svc bluetooth disable`, and record `dumpsys bluetooth_manager | grep -m1 'state:'`.
After SA: `adb -s $DHU shell svc bluetooth enable`.

**stage-h.sh** (`run_h HB`, then install A over wireless adb, then `run_h HA`)

```bash
. ./lib4.sh                                   # then set HU and PH to the two IPs
VLC=org.videolan.vlc; MEDIA=/sdcard/Music/ohu-stutter-long.mp3
run_h() { local R=$1
  th_gate $R || return 1; A shell am force-stop $PKG; cap $R
  launch; sleep 5; mark $R-start; send ACTION_CHECK_USB
  waitn $R-start $R "SSL handshake complete" 60 || { send ACTION_CHECK_USB; waitn $R-start $R "SSL handshake complete" 60; } ||
    { echo "$R replug cue" >> $OUT/marks.txt; waitn $R-start $R "SSL handshake complete" 90; } ||
    { echo "$R NO_SESSION" >> $OUT/marks.txt; mark $R-end; th_end; appexit; uncap $R; return 1; }
  adb -s $PH shell am start -a android.intent.action.VIEW -d "file://$MEDIA" -t audio/mpeg -n $VLC/.StartActivity
  waitn $R-start $R "Media Start Request AUDIO:" 20 || echo "$R NO_MUSIC" >> $OUT/marks.txt
  sleep 20; mark $R-music; echo "operator: start heard"; hold $R 300; mark $R-end; echo "operator: stop heard"
  adb -s $PH shell am force-stop $VLC; th_end; appexit; uncap $R; }
```

On `replug cue`, the operator replugs D-MOTO once. The operator's terminal, same `HU` and `A`:

**heard.sh**

```bash
. ./lib4.sh; HU=<D-HP IP>:5555
n=0; while read -r _; do n=$((n+1)); A shell log -t HEARD "stutter-$n" </dev/null; echo "stamped $n"; done
```

Stage H setup, with D-HP and D-MOTO still cabled to the PC: install B on D-HP, write its keys, then
`adb -s <unit> tcpip 5555` on both and read each IP with `shell ip -4 addr show wlan0`. The operator then plugs
D-MOTO into D-HP's OTG port. Install A later with `adb -s $HU install -r -d apk-A.apk` (about 97 s over wireless adb).

## 7. The deciding lines and the runs

All app strings verified with `grep -F -r` in `app/src/main` at `3ad7c78c` and at `main`, except L-TC (A only).
Grep with `-a`, in `<run>-hu.txt` between `<run>-music` and `<run>-end` unless noted. Remove duplicate lines first
in any capture `alive` restarted: `awk '!seen[$0]++'`.

| Id | Line | Read |
|---|---|---|
| L-SSL | `SSL handshake complete` | from `<run>-start`; a session formed |
| L-ACC | `Sending acc start` | stage H: the USB switch |
| L-DEC | `AudioDecoder.start: channel=` | whole capture: on the media channel, `codec=` must not be AAC; `latencyMultiplier=2`, `queueCapacity=20` |
| L-TC | `Audio time correction: selected=` | A only, report: the engine in force |
| L-MUS | `Media Start Request AUDIO:` | music started |
| L-OUT | `AudioMixer: id=` with ` output ` and ` requested=` | whole capture: the first line's `effective=` and `minimum=` (frames; 48 = 1 ms) |
| L-MIX | `AudioMixer: id=` with `loopGapMax=` | the 10 s device line: `effective=`, `loopGapMax=`, `writeMax=` |
| L-CH6 | regex `AudioMixer: id=[0-9]+ channel=6 target=` | the 10 s media line: `concealedFrames=`, `staleFrames=`, `compressedFrames=`, `arrivalGapMax=` |
| L-OVF | `PCM overflow channel=` | count |
| L-QF | `Audio queue is full at ` | count |
| L-TP | `Throughput over ` | median `rendered=` |
| L-WARN | `never opened the Android Auto channel` | stage S: the D-POCO bond fault |
| S-UND | `disabled due to previous underrun` | system line, count |
| S-UF | `underrunframes=` | system line, whole capture |
| L-HEARD | `I/HEARD` | stage H, one per stamp; not an app line |

FAIL lines in any window: `FATAL EXCEPTION` in a block naming `com.andrerinas`, and `UnsatisfiedLinkError`.
Phone, between the run's RIGMARK lines: `Critical error` MUST be 0, and `FATAL EXCEPTION` in Gearhead MUST be 0.

**Deltas.** For `concealedFrames`, `staleFrames` and `compressedFrames`, sum the increases between consecutive L-CH6
lines in the window; when a value falls, the channel reopened, so add the new value. Call the sum of the three the
run's **damage**. Also count **slow windows**: L-MIX lines whose `loopGapMax` (ms) is above `effective=`/48.

**Runs, in order: SB, SA, HB, HA.** Write the results file after every run.

- **Session gate, every run:** L-SSL 1 within the caps, L-MUS 1 or more, L-DEC not AAC, L-MIX lines in the window 28
  or more, 0 FAIL lines. A run that misses it is retried once, then UNTESTABLE.
- **Stage S only:** if SB has no L-SSL and L-WARN printed, the operator unpairs D-HU on D-POCO; retry SB once and say
  so in Setup notes.
- **Reachability:** a B run with damage 4800 frames (100 ms) or less, 0 slow windows and (stage H) 0 heard stamps
  showed no fault on that unit. The A run then grades no-regression only, and "better" is INCONCLUSIVE.
- **SA and HA PASS** when, against the same unit's B run: damage is at most 1.5 x B's or 4800 frames, whichever is
  larger; slow windows are at most B's + 3; L-OVF and L-QF are each at most B's + 1; 0 FAIL lines; phone conditions
  hold. Worse than that is a FAIL: the new renderer is worse on a slow unit.
- **Better:** with B reachable, A's damage is at most half of B's and A's slow windows are at most B's. Report it
  beside the verdict.
- **Heard (stage H, not a verdict):** HB against HA counts, and how many stamps fall within [-2.5 s, +0.5 s] of an
  L-CH6 counter rise or a slow window.

Stop rule: 2 runs with no session in one stage end that stage; the other stage still runs. Host hot after the second
thermal wait: stop and mark the rest UNTESTABLE.

**Closing.** Restore both head units' `settings.xml` and read them back. D-HU Bluetooth on. D-MOTO
`svc power stayon false`; D-HP and D-MOTO back to USB adb (`adb -s <unit> usb`). Leave B installed on D-HP and D-SAM
unless their previous builds were recorded.

## 8. Do not re-run

Round 1 (D-HU audio and the H.264 smoke), rounds 2 and 3. Optional engines other than `ohu`: not this round.

## 9. Report back

1. Per unit, B against A: damage (and its three parts), slow windows, the largest `loopGapMax`, L-OVF, L-QF.
2. Reachability per unit, and whether A is better.
3. Stage H heard counts. Any FATAL EXCEPTION or `UnsatisfiedLinkError`, with its first five lines.
