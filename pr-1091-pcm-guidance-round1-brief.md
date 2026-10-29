# pr-1091-pcm-guidance, round 1 brief

Published name: `pr-1091-pcm-guidance-round1-brief.md`. Results go in `pr-1091-pcm-guidance-round1-results.md`.
Evidence: create the release `rig-evidence-pr-1091-pcm-guidance` and upload `pr-1091-pcm-guidance-round1-captures.zip`.

Units: D-HU (head unit), D-POCO (phone). D-MOTO, D-SAM and D-HP take no part. D-SAM's Bluetooth stays off.

Queue line: `pr-1091-pcm-guidance-round1-brief.md` (C `dcc3c708` on `fork/test/pr-1091`, a contributor audio PR merged
onto `main` `b3ef3355`, one arm, four settings arms, no baseline build; units D-HU, D-POCO; R2 is the point).

Pairing: none needed. Any round that does not use D-HU or D-POCO can run beside it (`TESTING-TEMPLATE.md` §3b).

Hand steps: none. Injected taps: at most one per run, into the projected video, only if music does not start (section 5, `music`).

**Time budget.** Pre-flight 6 min, build 25 min, runs 30 min, thermal waits about 8 min. About 1 h 10 min in all.

| Run | Arm | `use-aac-audio` | `use-pcm-guidance` | `prefer-48k-guidance` | Minutes |
|---|---|---|---|---|---|
| R1 | C, the control | true | true | false | 7 |
| R2 | E, **the point** | true | true | true | 7 |
| R3 | D | false | true | true | 7 |
| R4 | B, option off | true | false | true | 7 |

All four runs share one capture. Each run ends the session, rewrites three keys with the app stopped and starts a new
session. R1 and R2 differ in one key only, so R1 is the control for R2.

## 1. Build and baseline

One arm. The contributor's PR (head `dcf82716`, five commits, on an older `main`) conflicts with current `main`. The
test branch is `main` `b3ef3355` with the PR merged on top. Three files conflicted and both sides were kept in each:
`AapAudio.kt` (`main`'s audio continuity work against the PR's codec and Start bookkeeping), the Korean strings
(appended on both sides) and the host audio regression list. No history was rewritten. When the contributor rebases,
the new head may differ from this build; the round still answers the protocol questions.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| C (candidate) | `fork/test/pr-1091` | `dcc3c7085d7be1aa520c802a825685c539f41ce4` | `630a06ce1874ab8a0642f8fd08f6cf1601aa3f2b` |

The dev host ran the JVM suite at `dcc3c708`: 3462 tests, 0 failures, 0 skipped. The host audio checks
(`app/src/test/host-audio/run.py`) gave 84 PASS, 0 FAIL. Do not run the unit tests on the rig.

**get_apk.sh**

```bash
OUT=rig-data/rounds/pr-1091-pcm-guidance-round1; mkdir -p $OUT
WT=$(ls -d worktrees/* 2>/dev/null | head -1)        # any existing worktree; else create one with wt-new.sh
git -C $WT fetch fork test/pr-1091
git -C $WT checkout --detach dcc3c7085d7be1aa520c802a825685c539f41ce4
git -C $WT rev-parse 'HEAD^{tree}'                    # MUST print 630a06ce1874ab8a0642f8fd08f6cf1601aa3f2b
$RIG_TOOLKIT/rig_thermal.sh wait 70
HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh   # copy the APK out of apks/ to $OUT/apk-C.apk at once
md5sum $OUT/apk-C.apk                                 # record in Setup notes
```

Install with `adb install -r -d $OUT/apk-C.apk` on D-HU only. D-POCO is the phone and keeps the APK it has.
Identity on D-HU after the install:

- `send ACTION_QUERY_STATE`: the `data=` line MUST carry commit `dcc3c708`.
- Pull the live APK and run `unzip -p live.apk 'classes*.dex' | strings | grep -c AudioSinkServices`. It MUST be at
  least 1. The class is new in this PR, so no earlier build has it.

## 2. What this is and why

Today one setting picks one codec for all three audio sinks: music (channel 6, `AUDIO`), guidance and the assistant
(channel 4, `AUDIO1`, SPEECH) and system sounds (channel 5, `AUDIO2`). The PR adds two settings, both off by default:

- **`use-pcm-guidance`**: SPEECH and SYSTEM are announced as PCM even when music is AAC (from `use-aac-audio` or the
  2.4 GHz band cap).
- **`prefer-48k-guidance`**: when PCM guidance is on and the phone negotiated AAP 1.6 or later, the SPEECH sink offers
  a second configuration, index 1, 48 kHz mono, beside index 0, 16 kHz mono. The phone picks one in its Media Start
  (`config_index=`). The app then decodes and times the PCM at the rate the phone picked. Before Start, a sink with
  two formats does not open an output and drops any data, because the rate is not known yet.

What can go wrong, and what each run checks:

- **The phone refuses or stops on the new announcement.** A second SPEECH configuration, or mixed codecs across
  sinks, could make Android Auto end the session. Graded on the phone's `Critical error` and `ByeBye` lines, and on
  a session that keeps playing (all runs).
- **Wrong rate.** If the phone picks 48 kHz and the app plays it as 16 kHz (or the reverse), the prompt plays at a
  third or three times its speed. No log says "wrong pitch", so the runs compare the phone's `config_index=`, the
  app's `selected configuration=K sampleRate=S` line and the rate the output was built at (`AudioDecoder.start:
  channel=4, ... sampleRate=`), and the mixer's `source=` rate when it prints one. All four MUST agree.
- **A prompt that never plays.** A two-format sink waits for Start. If Start never arrives, or the selection is
  refused, no prompt plays. Graded on `Media Start Request AUDIO1:` against `Audio Stop: AUDIO1`, and on the
  `invalid configuration=` line.
- **The option off must change nothing.** R4 leaves `prefer-48k-guidance` true but turns `use-pcm-guidance` off. The
  hidden preference MUST NOT reach the wire: SPEECH is AAC with one configuration, as on `main` with AAC on.

Whether this phone picks 48 kHz at all is not known. The PR's analysis of Android Auto 17.4 says SPEECH looks for
48 kHz mono only above AAP 1.5. D-POCO negotiates 1.7. If D-POCO picks index 0 in R2 and R3, the 48 kHz path is
INCONCLUSIVE on this phone and the fallback to 16 kHz is what those runs prove. That is a result, not a FAIL.

## 3. What is different about this round

- **No speaker on the rig** (`rig-quirks/topics/audio.md`). Nothing is graded by ear. The proxies are the log lines in
  section 6.
- **Guidance prompts are scripted with two Maps routes**, as in `pr-1114-audio-continuity-round1-brief.md`: two
  destinations about 1.3 km north and south of D-POCO's last fix, alternated, so each intent starts a new route that
  speaks its first instruction. That round got an `AUDIO1` start on 6 of 8 intents. D-POCO has no mobile data and its
  offline maps cover Medellin, Marinilla and Rionegro (`rig-quirks/units/D-POCO.md`); the rig sits inside them. If
  `loc` finds no fix, use `Parque Principal Marinilla` and `Parque Principal Rionegro` as D1 and D2.
- **A run needs at least 2 `AUDIO1` starts to grade its guidance bullets.** With fewer, those bullets are
  INCONCLUSIVE and the session bullets still grade.
- **The 2.4 GHz cap can turn music AAC by itself.** If the session's link is 2.4 GHz, `[ServiceDiscovery] AAC music
  announced by the 2.4 GHz cap (Use AAC Audio is off)` prints in R3 and its music is AAC. Then R3 measures E again
  rather than D: record it, and grade R3's music bullet against AAC.
- **Music.** D-POCO plays a playlist of 6 or more tracks in the rig's player, playing before each session starts
  (as in `emotionbug-batch-round2-brief.md`). Prompts are spoken over the music.
- **SSL line.** Match `SSL handshake complete` with no `Handshake:` prefix (`log-level=2`).
- **On mode 3 the launch arms the stack.** No `ACTION_START_WIRELESS_SCAN` straight after a launch.
- **The phone leaves the link by `svc`**, not airplane mode (`ph_off`).
- **`playback-focus-self-defeating`.** Read it before R1 and after R4 (`rig-quirks/topics/audio.md`). If it reads
  true before R1, write false with the app stopped and say so.
- **Expected INCONCLUSIVE:** the 48 kHz bullets of R2 and R3 if the phone picks index 0; the guidance bullets of any
  run with fewer than 2 `AUDIO1` starts. The rate change during a draining prompt (a Start that picks a different
  index than the one before) cannot be forced from the rig; the host audio checks cover it.

## 4. Settings keys

Back up D-HU's `settings.xml` at the start (`$OUT/settings-backup-hu.xml`) and diff it against the end. State the
delta in Setup notes. Record `video-profile-starvation-cap`; if it is true, delete it and say so (it forces AAC).
Leave the other audio keys as they are and record `audio-latency-multiplier` and `use-aaudio-output`.

**D-HU, every run:**

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<int name="log-level" value="2" />
<int name="onboarding-version" value="2" />
<boolean name="allow-external-configuration" value="true" />
<boolean name="enable-audio-sink" value="true" />
<boolean name="external-bt-zbt-transport" value="false" />
<boolean name="external-bt-blink-transport" value="false" />
```

**Per run** (the table at the top): `use-aac-audio`, `use-pcm-guidance`, `prefer-48k-guidance`, all booleans.

Write them with `set_hu_settings_host.py` with the app stopped, and read every one back.

## 5. Helpers

Run every adb call in sequence, never two at once against one unit. Write the results file after every run.

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846; PH=4f4027e9; D=$HU
MAPS=com.google.android.apps.maps
OUT=rig-data/rounds/pr-1091-pcm-guidance-round1; mkdir -p $OUT
export LC_ALL=C
send() { a=$1; shift; adb -s $D shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
# marker on both units: AutomationMarker on D-HU, RIGMARK on D-POCO
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; adb -s $PH shell log -t RIGMARK "$1"
  echo "$1 $(date '+%m-%d %H:%M:%S')" >> $OUT/marks.txt; }
launch() { adb -s $D shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; }
appexit() { adb -s $D shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5
  adb -s $D shell am force-stop $PKG; sleep 1; }
# lines of the D-HU capture between <run>-start and <run>-end; of the phone capture between the RIGMARK lines
win()  { sed -n "/AutomationMarker: $1-start/,/AutomationMarker: $1-end/p" $OUT/hu.txt; }
pwin() { sed -n "/RIGMARK.*$1-start/,/RIGMARK.*$1-end/p" $OUT/phone.txt; }
cnt()  { win $1 | grep -acF -- "$2"; }
# count of a fixed string after the last occurrence of a marker, and a capped wait on it
after() { sed -n "/AutomationMarker: $1/,\$p" $OUT/hu.txt | grep -acF -- "$2"; }
waitn() { local m=$1 s=$2 n=$3 cap=$4 t=0
  while [ $t -lt $cap ]; do [ "$(after $m "$s")" -ge $n ] && return 0; sleep 1; t=$((t+1)); done; return 1; }

# host thermal (rig-quirks/topics/tooling.md)
th_gate() { $RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || { echo HOST_HOT; return 1; }
  $RIG_TOOLKIT/rig_thermal.sh watch $OUT/thermal-$1.log & THPID=$!; }
th_end() { kill $THPID 2>/dev/null; wait $THPID 2>/dev/null; }

# phone radios
ph_bt()  { adb -s $PH shell svc bluetooth $1; sleep 4; adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; }
ph_off() { adb -s $PH shell svc wifi disable; sleep 0.3; ph_bt disable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_on()  { adb -s $PH shell svc wifi enable; sleep 0.3; ph_bt enable; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }

# two destinations about 1.3 km north and south of the phone's last fix
loc() { L=$(adb -s $PH shell dumpsys location | grep -a -m1 -oE -- '-?[0-9]{1,3}\.[0-9]{4,},-?[0-9]{1,3}\.[0-9]{4,}')
  if [ -z "$L" ]; then D1="Parque+Principal+Marinilla"; D2="Parque+Principal+Rionegro"
  else D1=$(awk -v p="$L" 'BEGIN{split(p,a,","); printf "%.6f,%.6f", a[1]+0.012, a[2]}')
       D2=$(awk -v p="$L" 'BEGIN{split(p,a,","); printf "%.6f,%.6f", a[1]-0.012, a[2]}'); fi
  echo "fix=$L D1=$D1 D2=$D2" | tee -a $OUT/loc.txt; }
# nav <1|2> <label>: start a route to D1 or D2; a new route speaks its first instruction
nav() { local d=$D1; [ "$1" = 2 ] && d=$D2; mark "$2"
  adb -s $PH shell "am start -a android.intent.action.VIEW -d 'google.navigation:q=$d&mode=d' -p $MAPS" >/dev/null; }
# music: wait up to 20 s for the music channel; else one tap on Android Auto's play control in the projected video
music() { waitn $1-start "Media Start Request AUDIO:" 1 20 && return 0
  adb -s $D shell input tap 272 657; echo "$1 music tap $(date +%T)" >> $OUT/marks.txt
  waitn $1-start "Media Start Request AUDIO:" 1 20 || echo "$1 NO_MUSIC" >> $OUT/marks.txt; }
```

**arm.sh** (one run: `arm R2 true true true`)

```bash
arm() { local R=$1 AAC=$2 PCM=$3 P48=$4
  th_gate $R || return 1
  ph_off
  adb -s $D shell am force-stop $PKG
  # write use-aac-audio=$AAC use-pcm-guidance=$PCM prefer-48k-guidance=$P48 with set_hu_settings_host.py; read all three back
  launch; sleep 2; mark $R-start
  ph_on
  waitn $R-start "SSL handshake complete" 1 120 || { echo "$R NO_SESSION" >> $OUT/marks.txt; mark $R-end; th_end; appexit; return 1; }
  waitn $R-start "Throughput over" 1 30
  music $R
  sleep 10
  for i in 1 2 3; do nav $(( (i % 2) + 1 )) $R-p$i; sleep 35; done
  send ACTION_EXPORT_LOG          # keep the newest HUR_Log_*.txt as $OUT/$R-export.txt
  mark $R-end
  send ACTION_DISCONNECT; sleep 5
  adb -s $PH shell am force-stop $MAPS
  th_end; appexit
}
```

## 6. The deciding lines

All verified with `grep -F -r` in `app/src/main` at `dcc3c708`. Every one is INFO or WARN, so `log-level=2` carries
them. Grep every capture with `-a`.

| Id | Line | Level | Read |
|---|---|---|---|
| L-SD | `[ServiceDiscovery] Audio sink channel=` | INFO | one per announced sink: `channel=N stream=S codec=MEDIA_CODEC_AUDIO_PCM` or `..._AAC_LC` |
| L-CAP | `[ServiceDiscovery] AAC music announced by the 2.4 GHz cap` | INFO | present means music is AAC whatever `use-aac-audio` says |
| L-VER | `Handshake: the phone selected ` | INFO | the negotiated AAP version; 1.6 or more is needed for 48 kHz |
| L-SETUP | `Media Sink Setup Request: ` | INFO | `N on channel AUDIO1`; the Config response follows it |
| L-IDX | `configuration_indices: 1` | INFO, a line of the `Config response:` proto | in the 6 lines after `on channel AUDIO1`: the 48 kHz offer went out |
| L-MSR1 | `Media Start Request AUDIO1:` | INFO | one per prompt; read `config_index=K` |
| L-MSR6 | `Media Start Request AUDIO:` | INFO | music started |
| L-SEL | `AapAudio: AUDIO1 selected configuration=` | INFO | `configuration=K sampleRate=S`, once per `AUDIO1` Start |
| L-DEC | `AudioDecoder.start: channel=4,` | INFO | the guidance output: read `sampleRate=` and `codec=` |
| L-DEC6 | `AudioDecoder.start: channel=6,` | INFO | the music output: read `codec=` |
| L-SRC | `PCM channel=4 source=` | WARN, only when its counters move | `source=SHz/1ch` must equal L-SEL's `S` |
| L-OVF | `PCM overflow channel=4 negotiatedSource=` | WARN, only on an overflow | same rate rule |
| L-STOP1 | `Audio Stop: AUDIO1` | INFO | one per prompt end |
| L-MIX | `AudioMixer: id=` then ` channel=4 target=` | INFO, every 10 s | record `concealedFrames=` and `staleFrames=` (information) |
| L-SSL | `SSL handshake complete` | INFO | the session formed |
| L-TP | `Throughput over` | INFO | `rendered=N` |
| L-EXP | `pcmGuidance:` | export header | `pcmGuidance:<b> prefer48kGuidance:<b>` must match the run's keys |

FAIL lines. Any one of these in a run's window is a FAIL for that run:

| Line | Level |
|---|---|
| `AapAudio: invalid configuration=` | WARN |
| ` has only configuration 0; ignoring index=` | WARN |
| `is not an audio codec, keeping codec=` | WARN |
| `FATAL EXCEPTION` in a line block that names `com.andrerinas` | ERROR (AndroidRuntime) |

Phone capture, between the run's RIGMARK lines: `Critical error` and `ByeBye` (Gearhead). Each MUST be 0.

**What each arm must announce (L-SD) and decode (L-DEC, L-DEC6).** L-SD prints `MEDIA_CODEC_AUDIO_AAC_LC` or
`MEDIA_CODEC_AUDIO_PCM`; L-DEC prints `codec=AAC_LC` or `codec=PCM`.

| Run | channel 6 (music) | channel 4 (SPEECH) | channel 5 (SYSTEM) | L-IDX after `AUDIO1` setup |
|---|---|---|---|---|
| R1 | AAC_LC | PCM | PCM | 0 |
| R2 | AAC_LC | PCM | PCM | 1 |
| R3 | PCM (AAC_LC if L-CAP) | PCM | PCM | 1 |
| R4 | AAC_LC | AAC_LC | AAC_LC | 0 |

L-IDX needs L-VER of 1.6 or more in R2 and R3. If L-VER reads below 1.6, L-IDX 0 is correct there; record it and
mark the 48 kHz bullets INCONCLUSIVE.

## 7. Pre-flight (6 min)

**preflight.sh**

```bash
. ./lib.sh
adb -s $HU shell stat -c '%U:%G %a' /data/data/$PKG/shared_prefs | tee $OUT/pre-stat.txt   # rig-quirks/units/D-HU.md
adb -s $HU shell su -c "cat /data/data/$PKG/shared_prefs/settings.xml" > $OUT/settings-backup-hu.xml
grep -o 'name="\(video-profile-starvation-cap\|playback-focus-self-defeating\|audio-latency-multiplier\|use-aaudio-output\|use-aac-audio\)"[^/]*' $OUT/settings-backup-hu.xml | tee $OUT/pre-audio-keys.txt
adb -s $HU shell dumpsys audio | grep -a -m3 -i "speaker\|A2DP" | tee $OUT/pre-route.txt
adb -s $PH shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices' | tee $OUT/pre-bonds.txt
adb -s $PH shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 4; adb -s $PH shell am force-stop $PKG
adb -s 30041c35642d2200 shell svc bluetooth disable 2>/dev/null   # D-SAM, if attached
loc
# one capture for the whole round, on both units
adb -s $HU logcat -G 16M; adb -s $HU logcat -c
stdbuf -oL adb -s $HU logcat -v time > $OUT/hu.txt & echo $! > $OUT/hu.pid
adb -s $PH logcat -G 16M; adb -s $PH logcat -c
stdbuf -oL adb -s $PH logcat -v time > $OUT/phone.txt & echo $! > $OUT/phone.pid
```

Start music on D-POCO now and leave it playing for the round.

## 8. Runs

`arm R1 true true false`, then `arm R2 true true true`, `arm R3 false true true`, `arm R4 true false true`. After R4:
`kill $(cat $OUT/hu.pid) $(cat $OUT/phone.pid)`, restore D-HU's `settings.xml` from the backup and read
`playback-focus-self-defeating` once more.

Stop rule: a run with no `SSL handshake complete` within 120 s of `<run>-start` ends there and is graded from what it
has. Retry a run at most once. Stop the round after 2 runs with no session.

Every run's PASS needs these **session bullets**, in its `win`:

- L-SSL ≥ 1, the last L-TP has `rendered=` > 0, and L-VER = 1 ending `status STATUS_SUCCESS`. Record `X.Y`.
- L-SD: exactly 3 lines, and the codecs match the run's row in section 6.
- L-DEC6 ≥ 1 with the codec of the row's channel 6 (`codec=AAC_LC` or `codec=PCM`), and L-MSR6 ≥ 1. If `music`
  wrote `NO_MUSIC`, this bullet is INCONCLUSIVE.
- L-SETUP has a line `on channel AUDIO1`, and L-IDX in the 6 lines after it matches the row.
- 0 lines from the FAIL table.
- Phone: `pwin <run> | grep -acF 'Critical error'` = 0 and `pwin <run> | grep -acF ByeBye` = 0. The `ByeBye` from
  `ACTION_DISCONNECT` comes after `<run>-end`, so it is outside the window.
- The export: `grep -a -m1 -o 'pcmGuidance:[a-z]* prefer48kGuidance:[a-z]*' $OUT/<run>-export.txt` matches the run's
  keys.

And these **guidance bullets**, graded only when L-MSR1 ≥ 2 (else INCONCLUSIVE):

- L-STOP1 = L-MSR1, or L-MSR1 - 1 when the last prompt was still speaking at `<run>-end` (each prompt ended).
- L-SEL count = L-MSR1, and for every L-MSR1 line the next L-SEL line has `configuration=` equal to its
  `config_index=`.
- For each L-SEL line: `configuration=0` reads `sampleRate=16000` and `configuration=1` reads `sampleRate=48000`.
- Every L-DEC line after an L-SEL line has `sampleRate=` equal to that L-SEL's `S`. An L-DEC line before the first
  L-SEL (a one-format sink opens its output at Setup) reads `sampleRate=16000`. Every L-DEC line has `codec=PCM`
  (R1 to R3) or `codec=AAC_LC` (R4). A run may have fewer L-DEC lines than prompts: a PCM rate change keeps the
  output.
- Every L-SRC and L-OVF line has `source=` / `negotiatedSource=` equal to `S` of the L-SEL line before it.
- Record each `config_index=` in order, and the largest `concealedFrames=` and `staleFrames=` on L-MIX lines for
  channel 4 (information).

Run-specific:

- **R1 (control):** every `config_index=` is 0.
- **R2 (the point):** record whether the phone picked 1. If every `config_index=` is 0, the 48 kHz bullets are
  INCONCLUSIVE (this phone keeps 16 kHz), and the rest still grade. If any is 1, the rate bullets above MUST hold
  for it.
- **R3:** as R2. Record L-CAP.
- **R4 (option off):** every `config_index=` is 0, L-IDX is 0 although `prefer-48k-guidance` is true, and channel 4
  is `AAC_LC` in L-SD and L-DEC.

## 9. Do not re-run

Nothing from earlier threads. Music-only stutter grading belongs to the audio continuity thread.

## 10. Report back

1. For R2 and R3: L-VER, L-IDX, and every `config_index=` the phone sent. Did D-POCO ever pick 48 kHz?
2. Did the four rates agree for every prompt (phone index, `selected configuration`, `AudioDecoder.start`, mixer
   `source=`)? Name any prompt where they did not.
3. Each run's L-SD codecs against section 6's table, and the phone's `Critical error` and `ByeBye` counts.
4. L-MSR1 and L-STOP1 per run, and the largest channel 4 `concealedFrames=` and `staleFrames=`.

One verdict per run in a table at the top of the results file, and one line on whether the 48 kHz path was reached.
