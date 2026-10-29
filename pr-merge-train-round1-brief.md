# pr-merge-train, round 1 brief: video and audio (stage A)

Published name: `pr-merge-train-round1-brief.md`. Results go in `pr-merge-train-round1-results.md`.
Evidence: create the release `rig-evidence-pr-merge-train` and upload `pr-merge-train-round1-captures.zip`.

Units: D-HU (head unit), D-POCO (phone). D-MOTO, D-SAM and D-HP take no part; D-SAM's Bluetooth stays off.

Queue line: `pr-merge-train-round1-brief.md` (A `3ad7c78c` on `fork/test/pr-merge-train`, B `f9a72d48` = `main`;
two builds in one worktree, the B APK is shared with `usb-native-loop-round2`; units D-HU and D-POCO; R1 is the point).

Pairing: any round that does not use D-HU or D-POCO (§3b). Hand steps: none. Injected taps: at most one per run,
into the projected video, only if music does not start (`music` in lib.sh).

**Time budget.** Builds 30 min (one if the B APK is already on hand), pre-flight 5 min, runs 18 min, thermal waits
about 6 min. **About 1 h.**

| Run | Arm | What it proves | Minutes |
|---|---|---|---|
| R0 | B, `main` | the control: PCM music on today's renderer | 6 |
| **R1** | A | **the point: the same session on the new renderer is no worse than R0** | 6 |
| R2 | A | a smoke: the bundled FFmpeg decoder draws H.264 on D-HU | 6 |

This round is the first of three. They test one branch, `test/pr-merge-train`, at the point after each merge step.
This one is short on purpose: a user has already confirmed the H.264 decoder on their own unit, so R2 is a smoke only.

## 1. Build and baseline

`test/pr-merge-train` is `main` `f9a72d48` with ten contributor PRs merged in the order they will go to `main`.
Round 1 builds the stage A commit: the H.264 software decoder, then the audio renderer work. One conflict was
resolved in `app/src/main/cpp/CMakeLists.txt`: the decoder PR renames the native library `hur_soft_hevc` to
`hur_soft_video`, and the audio PR adds `hur_time_stretch` beside it; both are kept, with the new name.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| A (candidate) | `fork/test/pr-merge-train` | `3ad7c78c8860d7da3f4a2429157931a36a03129f` | `596afde2259ce8e5e71247a581e5f7296117b599` |
| B (control) | `main` | `f9a72d480d51105870f62df6f0a40a1276e98a34` | `ff8fe6c2ea0a30f9e44276c6e9d3152591173969` |

The dev host ran the JVM suite at A: 4099 tests, 0 failures, 0 skipped. Do not run the unit tests on the rig.

**get_apk.sh**

```bash
OUT=rig-data/rounds/pr-merge-train-round1; mkdir -p $OUT
WT=$(ls -d worktrees/* 2>/dev/null | head -1)        # any existing worktree; else create one with wt-new.sh
git -C $WT fetch fork test/pr-merge-train main
for arm in B:f9a72d480d51105870f62df6f0a40a1276e98a34 A:3ad7c78c8860d7da3f4a2429157931a36a03129f; do
  n=${arm%%:*}; c=${arm#*:}
  [ $n = B ] && [ -f "$B_APK" ] && { cp "$B_APK" $OUT/apk-B.apk; continue; }   # B_APK: the usb-native-loop-round2 main APK, if built
  git -C $WT checkout --detach $c; git -C $WT rev-parse 'HEAD^{tree}'   # MUST match the table
  $RIG_TOOLKIT/rig_thermal.sh wait 70
  HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh            # copy the APK to $OUT/apk-$n.apk at once
done
md5sum $OUT/apk-*.apk                                                  # MUST differ; record in Setup notes
```

Identity after each install on D-HU: `send ACTION_QUERY_STATE` carries the arm's commit, and for A,
`unzip -p live.apk 'classes*.dex' | strings | grep -acE 'FfmpegVideoDecoder|NativeTimeStretchEngine'` is 2 or more.
Both classes are new on this branch. For B the same count is 0.

## 2. What this is and why

- **The audio renderer PR** reduces mixer and waveform-search work and adds optional time-correction engines. The
  default engine stays `ohu`, but its code changes too. A user reports audio stutter about every 5 s on PCM since
  3.5.0 that 3.4.0 did not have, over USB and wireless. Their tests of this PR's builds were mixed. The rig cannot
  rebuild their unit, so this round asks one question: **is a PCM session on the new renderer no worse than on
  `main`, with the rig's audio settings?** Those settings are a deliberate worst case; keep them.
- **The decoder PR** adds H.264 to the bundled FFmpeg (ARMv7 and ARM64, Android 5 and up) and also changes code every
  session runs: parameter-set replay, the codec fallback and the Texture/GLES fallback. R0 and R1 cover the default
  hardware path; R2 runs the opt-in decoder once.

## 3. What is different about this round

- **No speaker on the rig.** Audio is graded on log lines only. `disabled due to previous underrun` is a system line;
  if it reads 0 in R0 too, use `underrunframes=` from the `MediaAnalyticsItem` teardown line (`rig-quirks/topics/audio.md`).
- Music, the `music` fallback tap and the SSL line are as in `pr-1091-pcm-guidance-round1-brief.md`.
- **The 2.4 GHz cap can turn music AAC.** If `AAC music announced by the 2.4 GHz cap` prints, the run measured AAC,
  not PCM: record it and mark R0 and R1 INCONCLUSIVE for the audio bullets.
- Read `rig-quirks/topics/tooling.md` (thermal limits), `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-POCO.md`.
- **Expected INCONCLUSIVE:** R2 if `getprop ro.product.cpu.abi` on D-HU is not `arm64-v8a` or `armeabi-v7a`.

## 4. Settings keys

Back up D-HU's `settings.xml` first and diff it at the end. If `video-profile-starvation-cap` is true, delete it and
say so: it forces AAC. Record `audio-latency-multiplier`, `use-aaudio-output` and `audio-matching-mode` (expected
absent).

| Key | Type | R0, R1 | R2 |
|---|---|---|---|
| `wifi-connection-mode` | int | 3 | 3 |
| `native-ap-transport` | int | 0 | 0 |
| `log-level` | int | 2 | 2 |
| `onboarding-version` | int | 2 | 2 |
| `allow-external-configuration` | boolean | true | true |
| `enable-audio-sink` | boolean | true | true |
| `use-aac-audio` | boolean | false | false |
| `external-bt-zbt-transport` | boolean | false | false |
| `force-software-decoding` | boolean | false | true |
| `software-video-decoder` | int | (leave) | 1 |
| `video-codec` | string | (leave) | `H.264` |

Write them with `set_hu_settings_host.py` with the app stopped, and read every one back.

## 5. Helpers

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846; PH=4f4027e9; D=$HU
OUT=${OUT:-rig-data/rounds/pr-merge-train-round1}; mkdir -p $OUT
export LC_ALL=C
send() { a=$1; shift; adb -s $D shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; adb -s $PH shell log -t RIGMARK "$1"
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
launch() { adb -s $D shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; }
appexit() { adb -s $D shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5
  adb -s $D shell am force-stop $PKG; sleep 1; }
win()  { sed -n "/AutomationMarker: $1-start/,/AutomationMarker: $1-end/p" $OUT/hu.txt; }
pwin() { sed -n "/RIGMARK.*$1-start/,/RIGMARK.*$1-end/p" $OUT/phone.txt; }
cnt()  { win $1 | grep -acF -- "$2"; }
after() { sed -n "/AutomationMarker: $1/,\$p" $OUT/hu.txt | grep -acF -- "$2"; }
waitn() { local m=$1 s=$2 n=$3 cap=$4 t=0
  while [ $t -lt $cap ]; do [ "$(after $m "$s")" -ge $n ] && return 0; sleep 1; t=$((t+1)); done; return 1; }
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }
ph_bt()  { adb -s $PH shell svc bluetooth $1; sleep 4; adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; }
ph_off() { adb -s $PH shell svc wifi disable; sleep 0.3; ph_bt disable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_on()  { adb -s $PH shell svc wifi enable; sleep 0.3; ph_bt enable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
music() { waitn $1-start "Media Start Request AUDIO:" 1 20 && return 0
  adb -s $D shell input tap 272 657; echo "$1 music tap $(date +%T)" >> $OUT/marks.txt
  waitn $1-start "Media Start Request AUDIO:" 1 20 || echo "$1 NO_MUSIC" >> $OUT/marks.txt; }
# session <run>: a 3-minute session with music, then a clean exit
session() { local R=$1
  th_gate $R || return 1; ph_off; adb -s $D shell am force-stop $PKG
  # write this run's keys (section 4) with set_hu_settings_host.py; read them back
  launch; sleep 2; mark $R-start; ph_on
  waitn $R-start "SSL handshake complete" 1 120 || { echo "$R NO_SESSION" >> $OUT/marks.txt; mark $R-end; th_end; appexit; return 1; }
  waitn $R-start "Throughput over" 1 30; music $R; sleep 180
  mark $R-end; send ACTION_DISCONNECT; sleep 5; th_end; appexit; }
```

**capture.sh** (once, before R0; one capture per unit for the whole round)

```bash
. ./lib.sh
adb -s $HU shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs | tee $OUT/pre-stat.txt
adb -s $HU shell su -c "cat /data/data/$PKG/shared_prefs/settings.xml" > $OUT/settings-backup-hu.xml
adb -s $HU shell getprop ro.product.cpu.abi | tee $OUT/abi.txt
adb -s 30041c35642d2200 shell svc bluetooth disable 2>/dev/null
adb -s $HU logcat -G 16M; adb -s $HU logcat -c; stdbuf -oL adb -s $HU logcat -v time > $OUT/hu.txt & echo $! > $OUT/hu.pid
adb -s $PH logcat -G 16M; adb -s $PH logcat -c; stdbuf -oL adb -s $PH logcat -v time > $OUT/phone.txt & echo $! > $OUT/phone.pid
```

Start a playlist of 6 or more tracks on D-POCO and leave it playing for the round.

## 6. The deciding lines

All verified with `grep -F -r` in `app/src/main` at `3ad7c78c`; the first five also exist on `main`. Grep with `-a`.

| Id | Line | Read |
|---|---|---|
| L-SSL | `SSL handshake complete` | the session formed |
| L-TP | `Throughput over` | `rendered=N` |
| L-CODEC | `Media Sink Setup Request: ` with `on channel VIDEO` | 3 = H.264 |
| L-MUS | `Media Start Request AUDIO:` | music started |
| L-MIX | `AudioMixer: id=` with ` channel=6 target=` | every 10 s; read `concealedFrames=` and `staleFrames=` |
| L-UND | `disabled due to previous underrun` | system line, one per underrun |
| L-CAP | `AAC music announced by the 2.4 GHz cap` | present: music was AAC |
| L-FF1 | `Configuring bundled FFmpeg H.264/AVC decoder for` | R2 only |
| L-FF2 | `Bundled FFmpeg H.264/AVC decoder initialized` | R2 only |
| L-FF3 | `First bundled software H.264/AVC frame rendered` | R2 only |

FAIL lines, any run: `FATAL EXCEPTION` in a block naming `com.andrerinas`; in R2 also
`decoder failed to initialize`, `Failed to load hur_soft_video`, `Stopping initialization retries for this codec`.
Phone, between the run's RIGMARK lines: `Critical error` MUST be 0.

## 7. Runs

Install B, then `session R0`. Install A (`adb install -r -d $OUT/apk-A.apk`, identity check), then `session R1`,
then `session R2` with R2's keys. Then stop both captures and restore D-HU's `settings.xml` from the backup.

Stop rule: a run with no L-SSL within 120 s ends there. Retry a run once. Stop the round after 2 runs with no session.

Every run's PASS needs, in its `win`: L-SSL ≥ 1, the last L-TP has `rendered=` > 0, L-CODEC present, 0 FAIL lines,
and phone `Critical error` = 0.

- **R0 and R1, audio.** Record L-MUS (1 or more, else `NO_MUSIC` and the audio bullets are INCONCLUSIVE), L-UND,
  the largest `concealedFrames=` and `staleFrames=` on L-MIX lines, and L-CAP. **R1 PASS** when its L-UND is at most
  R0's + 1 and its largest `staleFrames=` is at most twice R0's (or 50, whichever is larger). R1 worse than that is a
  FAIL: it is the regression the user reports, reproduced on the rig.
- **R2.** L-FF1, L-FF2 and L-FF3 each ≥ 1, in that order, and L-CODEC reads 3. If L-FF1 is 0 the setting did not
  take: check `force-software-decoding` and `software-video-decoder` in `settings.xml`, fix, retry once.

## 8. Do not re-run

The 48 kHz guidance runs belong to `pr-1091-pcm-guidance`. Do not grade music stutter by ear.

## 9. Report back

1. R0 against R1: L-UND, the largest `staleFrames=` and `concealedFrames=`, and L-CAP.
2. R2: the time from L-SSL to L-FF3, and the last L-TP's `rendered=`.
3. Any FATAL EXCEPTION, with its first five lines.
