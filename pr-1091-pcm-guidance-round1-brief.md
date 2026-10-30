# pr-1091-pcm-guidance, round 1 brief

Published name: `pr-1091-pcm-guidance-round1-brief.md`. Results go in `pr-1091-pcm-guidance-round1-results.md`.
Evidence: create the release `rig-evidence-pr-1091-pcm-guidance` and upload `pr-1091-pcm-guidance-round1-captures.zip`.

Units: D-HU (head unit), D-POCO (phone). D-MOTO, D-SAM and D-HP take no part. D-SAM's Bluetooth stays off.

Queue line: `pr-1091-pcm-guidance-round1-brief.md` (C `dcc3c708` on `fork/test/pr-1091`, a contributor audio PR merged
onto `main` `b3ef3355`, one arm, four settings arms, no baseline build; units D-HU, D-POCO; R2 is the point).

Pairing: D-POCO bonded to D-HU, as for any D-HU stock run (section 0). Any round that does not use D-HU or D-POCO
can run beside it (`TESTING-TEMPLATE.md` §3b).

Hand steps: none. Injected taps: at most one per run, into the projected video, only if music does not start (section 5, `music`).

**Time budget.** Pre-flight 6 min, build 25 min, runs 30 min, thermal waits about 8 min. About 1 h 10 min in all.

| Run | Arm | `use-aac-audio` | `use-pcm-guidance` | `prefer-48k-guidance` | Minutes |
|---|---|---|---|---|---|
| R1 | C, the control | true | true | false | 7 |
| R2 | E, **the point** | true | true | true | 7 |
| R3 | D | false | true | true | 7 |
| R4 | B, option off | true | false | true | 7 |

Each run has its own capture on both units. Each run rewrites three keys with the app stopped and starts a new
session, and ends it. R1 and R2 differ in one key only, so R1 is the control for R2.

## 0. What the session needs

`rig_session.py` runs the round from this block, in this order.

```rig-requires
lib 2c7a270e0
candidate C dcc3c7085d7be1aa520c802a825685c539f41ce4
unit D-HU   role=headunit wifi=on bt=on
unit D-POCO role=phone wifi=on bt=on bond=D-HU aa=D-HU
unit D-SAM  bt=off
confirm Spotify on D-POCO holds a playlist of 6 or more tracks as its current queue, so MEDIA_PLAY starts it
run pre     cap=300  script=pre.sh     grade=grade-pre.sh
run R1      cap=900  script=arm.sh     grade=grade-arm.sh
run R2      cap=900  script=arm.sh     grade=grade-arm.sh point=yes
run R3      cap=900  script=arm.sh     grade=grade-arm.sh
run R4      cap=900  script=arm.sh     grade=grade-arm.sh
run cleanup cap=180  script=cleanup.sh grade=grade-cleanup.sh always=yes
stop pre FAIL,UNTESTABLE skip=all
stop R1 UNTESTABLE skip=all
stop R2 UNTESTABLE skip=all
stop R3 UNTESTABLE skip=all
```

With D-POCO bonded to D-HU, Android Auto on D-POCO dials D-HU and nothing else. A run with no session is
UNTESTABLE after its own second bring-up, so one such run is two misses in a row and stops the round. Every
arm cap includes up to 10 min of the thermal gate.

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

**Prepare (the host, before the session):** build C and copy the APK to `$EV/apk/C.apk` at once. Prepare installs
nothing: `pre` backs up `settings.xml` first, then installs C on D-HU only. D-POCO is the phone and keeps the APK it has.

```text
WT=$($RIG_TOOLKIT/wt-new.sh pr-1091-pcm-guidance scratch dcc3c7085d7be1aa520c802a825685c539f41ce4 | tail -1)
git -C $WT fetch fork test/pr-1091; git -C $WT checkout --detach dcc3c7085d7be1aa520c802a825685c539f41ce4
git -C $WT rev-parse 'HEAD^{tree}'      MUST print 630a06ce1874ab8a0642f8fd08f6cf1601aa3f2b
$RIG_TOOLKIT/rig_thermal.sh wait 70; HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh
copy the APK out of rig-data/apks/ to $EV/apk/C.apk; record its md5 in Setup notes
```

Identity on D-HU after the install (`grade-pre.sh` checks all three):

- The live APK's md5 equals `$EV/apk/C.apk`'s.
- `send ACTION_QUERY_STATE`: the `data=` line carries commit `dcc3c7085d7b`.
- `unzip -p live.apk 'classes*.dex' | strings | grep -c AudioSinkServices` is at least 1. The class is new in this
  PR, so no earlier build has it.

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
  `pre` finds no fix, D1 and D2 are `Parque Principal Marinilla` and `Parque Principal Rionegro` (`$EV/dest.txt`).
- **A run needs at least 2 `AUDIO1` starts to grade its guidance bullets.** With fewer, those bullets are
  INCONCLUSIVE and the session bullets still grade.
- **The 2.4 GHz cap can turn music AAC by itself.** If the session's link is 2.4 GHz, `[ServiceDiscovery] AAC music
  announced by the 2.4 GHz cap (Use AAC Audio is off)` prints in R3 and its music is AAC. Then R3 measures E again
  rather than D: the grade records it and grades R3's music bullets against AAC.
- **Music.** Each run sends `MEDIA_PLAY` (key 126) to D-POCO once the session is up, so Spotify plays its queue.
  Prompts are spoken over the music. If no music channel opens in 20 s, `music` taps Android Auto's play control once.
- **SSL line.** Match `SSL handshake complete` with no `Handshake:` prefix (`log-level=2`).
- **On mode 3 the launch arms the stack.** No `ACTION_START_WIRELESS_SCAN` straight after a launch.
- **The phone leaves the link by `svc`**, not airplane mode (`link_off`). D-POCO's WiFi turns itself back on a few
  seconds after one disable (`D-POCO.md`), so `rl_hold_off` repeats the disable every 3 s until `link_on`.
- **`playback-focus-self-defeating`.** `pre` reads it before R1, and `cleanup` after R4 (`rig-quirks/topics/audio.md`).
  If it reads true before R1, `pre` writes false with the app stopped; say so in Setup notes.
- **The export is named by the app.** `ACTION_EXPORT_LOG` logs `Automation: log export finished (<path>)`, and the
  run pulls that path. D-HU's clock is months off, so the newest file by time is not the run's.
- **The thermal gate is `rl_thermal_gate`** (`$RIG_TOOLKIT/rig_thermal.sh`, limits in `rig-quirks/topics/tooling.md`).
  If its second wait fails, the run writes `$EV/HOST_HOT`, grades UNTESTABLE, and the stop rule ends the round.
- **Expected INCONCLUSIVE:** the 48 kHz bullets of R2 and R3 if the phone picks index 0; the guidance bullets of any
  run with fewer than 2 `AUDIO1` starts. The rate change during a draining prompt (a Start that picks a different
  index than the one before) cannot be forced from the rig; the host audio checks cover it.

## 4. Settings keys

`pre` backs up D-HU's `settings.xml` (`$EV/settings-backup-hu.xml`), and `cleanup` saves the end state, restores the
backup and diffs it. State the delta in Setup notes. `pre` records `video-profile-starvation-cap`; if it is true,
`pre` deletes it (it forces AAC): say so. Leave the other audio keys as they are and record
`audio-latency-multiplier` and `use-aaudio-output` (`$EV/pre-audio-keys.txt`).

**D-HU, every run** (`arm_try` writes them with `set_hu_settings_host.py`, app stopped, and reads them back to
`$EV/settings-<run>-keys.xml`):

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

**Per run** (the table at the top, `arm_keys` in `lib.sh`): `use-aac-audio`, `use-pcm-guidance`,
`prefer-48k-guidance`, all booleans. `allow-external-configuration` is there for `ACTION_EXPORT_LOG`, which the
candidate gates.

## 5. Helpers

Every adb call runs in sequence, never two at once against one unit. `lib.sh` is sourced before every script and
grade. A run's script reads its run id from `$RUN`.

**`lib.sh`**
```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
HU=27870808938846; PH=4f4027e9
MAPS=com.google.android.apps.maps
C_COMMIT=dcc3c7085d7b
send() { local a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
# one marker in both captures: AutomationMarker on D-HU, the same text under tag RIG on D-POCO
step() { rl_step "$1"; send ACTION_LOG_MARKER --es text "$1" >/dev/null; adb -s "$PH" shell log -t RIG "AutomationMarker: $1"; sleep 0.3; }
launch() { adb -s "$HU" shell am start -n "$MAIN" >/dev/null; }
hustop() { adb -s "$HU" shell am force-stop "$PKG"; sleep 1; }
appexit() { adb -s "$HU" shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5; hustop; }

# host thermal: a second failed wait marks the run UNTESTABLE, and the stop rule ends the round
cool() { [ -f "$EV/HOST_HOT" ] && { echo HOST_HOT; exit 3; }
  rl_step "WAIT cap=600s host thermal gate"; rl_thermal_gate 75 || { touch "$EV/HOST_HOT"; exit 3; }
  touch "$EV/$RUN-ran"; }

# D-POCO off and on the link with svc; its WiFi turns itself back on, so the disable is held
link_off() { rl_hold_off "$PH" wifi 3; rl_radio "$PH" bt off; }
link_on()  { rl_release "$PH" wifi; rl_radio "$PH" wifi on; rl_radio "$PH" bt on; }

# settings on D-HU, app stopped (adbd is root on D-HU, so no su)
hu_keys() { python3 -I "$RIG_TOOLKIT/set_hu_settings_host.py" "$HU" "$@" >> "$EV/keys.log" 2>&1; }
hu_cat()  { adb -s "$HU" shell cat "/data/data/$PKG/shared_prefs/settings.xml"; }
# the four arms: use-aac-audio use-pcm-guidance prefer-48k-guidance narrow-band-profile-cap
# R3 turns the 2.4 GHz cap off, so its music is PCM on any band; every other arm writes the default (true)
arm_keys() { case "$RUN" in
  R2) echo "true true true true" ;; R3) echo "false true true false" ;; R4) echo "true false true true" ;; *) echo "true true false true" ;; esac; }

# nav N LABEL: a route to line N of $EV/dest.txt; a new route speaks its first instruction
nav() { local d; d=$(sed -n "${1}p" "$EV/dest.txt" 2>/dev/null); [ -n "$d" ] || d=Parque+Principal+Marinilla
  step "$2"; adb -s "$PH" shell "am start -a android.intent.action.VIEW -d 'google.navigation:q=$d&mode=d' -p $MAPS" >/dev/null; }
# music: MEDIA_PLAY on the phone; if no music channel opens in 20 s, one tap on Android Auto's play control
music() { adb -s "$PH" shell input keyevent 126
  rl_wait_line "$EV/$RUN-hu.txt" 'Media Start Request AUDIO:' 20 && return 0   # not-literal
  adb -s "$HU" shell input tap 272 657; echo "music tap $(date +%T)" >> "$EV/$RUN-music.txt"
  rl_wait_line "$EV/$RUN-hu.txt" 'Media Start Request AUDIO:' 20 && return 0   # not-literal
  echo NO_MUSIC >> "$EV/$RUN-music.txt"; }

# one bring-up: phone off, keys, captures, launch, phone on. 0 when the SSL handshake completed
arm_try() { local aac pcm p48 nbc
  read -r aac pcm p48 nbc <<< "$(arm_keys)"
  link_off; hustop
  hu_keys scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:int:log-level:2 \
    scalar:int:onboarding-version:2 scalar:boolean:allow-external-configuration:true scalar:boolean:enable-audio-sink:true \
    scalar:boolean:external-bt-zbt-transport:false scalar:boolean:external-bt-blink-transport:false \
    "scalar:boolean:use-aac-audio:$aac" "scalar:boolean:use-pcm-guidance:$pcm" "scalar:boolean:prefer-48k-guidance:$p48" \
    "scalar:boolean:narrow-band-profile-cap:$nbc"
  hu_cat > "$EV/settings-$RUN-keys.xml"
  adb -s "$HU" logcat -G 16M; adb -s "$HU" logcat -c
  adb -s "$PH" logcat -G 16M; adb -s "$PH" logcat -c
  rl_cap_start "$RUN-hu" "$HU"
  rl_cap_start "$RUN-phone" "$PH"
  launch; sleep 2; step "$RUN-start"
  link_on
  rl_wait_line "$EV/$RUN-hu.txt" 'SSL handshake complete' 120 || return 1
  rl_wait_line "$EV/$RUN-hu.txt" 'Throughput over' 30; return 0; }

# --- grading ---
NFAIL=0; INC=""
cnd() { local l; l=$(rl_cond "$@"); echo "$l"; case "$l" in *" FAIL "*) NFAIL=$((NFAIL + 1)) ;; esac; }
inc() { INC="${INC:+$INC; }$*"; }
same() { [ "$1" = "$2" ] && echo 1 || echo 0; }
untestable_if_hot() { if [ -f "$EV/HOST_HOT" ] && [ ! -f "$EV/$RUN-ran" ]; then
  rl_verdict UNTESTABLE "host thermal: the second cooling wait failed"; exit 0; fi; }
```

## 6. The deciding lines

All verified with `grep -F -r` in `app/src/main` at `dcc3c708`. Every one is INFO or WARN, so `log-level=2` carries
them. Grep every capture with `-a`. A line with a run-time value (`AUDIO1`, `channel=4,`) is matched in two steps:
the literal prefix with `grep -F`, then the value with `awk index()`.

| Id | Line | Level | Read |
|---|---|---|---|
| L-SD | `[ServiceDiscovery] Audio sink channel=` | INFO | one per announced sink: `channel=N stream=S codec=MEDIA_CODEC_AUDIO_PCM` or `..._AAC_LC` |
| L-CAP | `[ServiceDiscovery] AAC music announced by the 2.4 GHz cap` | INFO | present means music is AAC whatever `use-aac-audio` says |
| L-VER | `Handshake: the phone selected ` | INFO | the negotiated AAP version; 1.6 or more is needed for 48 kHz |
| L-SETUP | `Media Sink Setup Request: ` | INFO | `N on channel AUDIO1`; the Config response follows it |
| L-IDX | `configuration_indices: 1` | INFO, a line of the `Config response:` proto | between `Config response:` and `(maxUnacked=` after the `on channel AUDIO1` setup: the 48 kHz offer went out |
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
| L-EXPF | `Automation: log export finished (` | INFO | names the exported file the run pulls |

FAIL lines. Any one of these in a run's window is a FAIL for that run:

| Line | Level |
|---|---|
| `AapAudio: invalid configuration=` | WARN |
| ` has only configuration 0; ignoring index=` | WARN |
| `is not an audio codec, keeping codec=` | WARN |
| `FATAL EXCEPTION` in a line block that names `com.andrerinas` | ERROR (AndroidRuntime) |

Phone capture, between the run's markers (`log -t RIG`): `Critical error` and `ByeBye` (Gearhead). Each MUST be 0.

**What each arm must announce (L-SD) and decode (L-DEC, L-DEC6).** L-SD prints `MEDIA_CODEC_AUDIO_AAC_LC` or
`MEDIA_CODEC_AUDIO_PCM`; L-DEC prints `codec=AAC_LC` or `codec=PCM`.

| Run | channel 6 (music) | channel 4 (SPEECH) | channel 5 (SYSTEM) | L-IDX after `AUDIO1` setup |
|---|---|---|---|---|
| R1 | AAC_LC | PCM | PCM | 0 |
| R2 | AAC_LC | PCM | PCM | 1 |
| R3 | PCM (`narrow-band-profile-cap` false, so no L-CAP) | PCM | PCM | 1 |
| R4 | AAC_LC | AAC_LC | AAC_LC | 0 |

L-IDX needs L-VER of 1.6 or more in R2 and R3. If L-VER reads below 1.6, L-IDX 0 is correct there; the grade
records it and marks the 48 kHz bullets INCONCLUSIVE.

## 7. Runs

### pre: backup, install, identity, destinations (6 min)

Records the `shared_prefs` owner (`rig-quirks/units/D-HU.md`), backs up `settings.xml`, records the audio keys and the
audio route, clears the two latches section 3 and 4 name, installs C, checks its identity, and writes the two
destinations.

**`pre.sh`**
```bash
rl_step "backup"
hustop
adb -s "$HU" shell stat -c '%U:%G %a' "/data/data/$PKG/shared_prefs" > "$EV/pre-stat.txt"
hu_cat > "$EV/settings-backup-hu.xml"
grep -aoE '(video-profile-starvation-cap|playback-focus-self-defeating|audio-latency-multiplier|use-aaudio-output|use-aac-audio)"[^/]*' \
  "$EV/settings-backup-hu.xml" > "$EV/pre-audio-keys.txt"
adb -s "$HU" shell dumpsys audio | grep -a -m3 -i "speaker\|A2DP" > "$EV/pre-route.txt"
grep -q 'name="video-profile-starvation-cap" value="true"' "$EV/settings-backup-hu.xml" &&   # not-literal
  { hu_keys del:video-profile-starvation-cap; echo "deleted video-profile-starvation-cap=true" >> "$EV/pre-notes.txt"; }
grep -q 'name="playback-focus-self-defeating" value="true"' "$EV/settings-backup-hu.xml" &&   # not-literal
  { hu_keys scalar:boolean:playback-focus-self-defeating:false; echo "wrote playback-focus-self-defeating=false" >> "$EV/pre-notes.txt"; }
rl_step "install"
adb -s "$HU" install -r -d "$EV/apk/C.apk" > "$EV/pre-install.txt" 2>&1
P=$(adb -s "$HU" shell pm path "$PKG" | cut -d: -f2 | tr -d '\r')
adb -s "$HU" pull "$P" "$EV/live.apk" >/dev/null 2>&1
echo "want=$(md5sum < "$EV/apk/C.apk" | cut -d' ' -f1) live=$(md5sum < "$EV/live.apk" | cut -d' ' -f1)" > "$EV/ident-C.txt"
echo "dex AudioSinkServices=$(unzip -p "$EV/live.apk" 'classes*.dex' | strings | grep -c AudioSinkServices)" >> "$EV/ident-C.txt"
launch; sleep 5; send ACTION_QUERY_STATE > "$EV/qs-C.txt"; appexit
rl_step "destinations"
adb -s "$PH" shell input keyevent KEYCODE_HOME
L=$(adb -s "$PH" shell dumpsys location | grep -a -m1 -oE -- '-?[0-9]{1,3}\.[0-9]{4,},-?[0-9]{1,3}\.[0-9]{4,}')
if [ -z "$L" ]; then printf '%s\n' Parque+Principal+Marinilla Parque+Principal+Rionegro > "$EV/dest.txt"
else awk -v p="$L" 'BEGIN {split(p, a, ","); printf "%.6f,%.6f\n%.6f,%.6f\n", a[1] + 0.012, a[2], a[1] - 0.012, a[2]}' > "$EV/dest.txt"; fi
echo "fix=$L" > "$EV/loc.txt"
```

**`grade-pre.sh`**
```bash
cnd pre-backup ge "$(grep -ac '</map>' "$EV/settings-backup-hu.xml" 2>/dev/null)" 1 "D-HU settings.xml backed up"
MD=$(awk '{split($1, a, "="); split($2, b, "=")} a[2] != "" && a[2] == b[2] {n++} END {print n+0}' "$EV/ident-C.txt" 2>/dev/null)
cnd id-md5 eq "${MD:-0}" 1 "live APK is apk/C.apk"
cnd id-commit ge "$(grep -acF -- "$C_COMMIT" "$EV/qs-C.txt" 2>/dev/null)" 1 "QUERY_STATE commit $C_COMMIT"
cnd id-dex ge "$(grep -a 'dex AudioSinkServices=' "$EV/ident-C.txt" 2>/dev/null | cut -d= -f2)" 1 "AudioSinkServices in the DEX"   # not-literal
echo "INFO $(tr '\n' ' ' < "$EV/pre-audio-keys.txt" 2>/dev/null)"
echo "INFO $(cat "$EV/pre-notes.txt" 2>/dev/null) dest=$(tr '\n' ' ' < "$EV/dest.txt" 2>/dev/null)"
[ "$NFAIL" -eq 0 ] || rl_verdict UNTESTABLE "no backup, or the build on D-HU is not C"
```

PASS: a whole backup, and the live APK, its commit and its DEX all match C. Otherwise the run is UNTESTABLE and the
round stops: there is nothing to measure and nothing to restore.

### R1, R2, R3 and R4: one settings arm each (R2 is THE POINT)

`arm.sh` is the same script for every run, and `$RUN` picks the keys from `arm_keys`. One bring-up, and one more on
a miss (the first capture is kept as `<run>-hu-miss.txt`). Then music, 10 s, three routes 35 s apart (D2, D1, D2),
the export, `<run>-end`, `ACTION_DISCONNECT`, Maps stopped, the app exited. The `ByeBye` from `ACTION_DISCONNECT` comes
after `<run>-end`, so it is outside the window.

**`arm.sh`**
```bash
cool
if ! arm_try; then
  step "$RUN-miss"; rl_cap_stop; appexit
  mv "$EV/$RUN-hu.txt" "$EV/$RUN-hu-miss.txt"; mv "$EV/$RUN-phone.txt" "$EV/$RUN-phone-miss.txt"
  arm_try || { echo session=none > "$EV/$RUN-session.txt"; step "$RUN-end"; rl_cap_stop; appexit; link_off; exit 0; }
fi
echo session=ok > "$EV/$RUN-session.txt"
music
rl_sleep 10
nav 2 "$RUN-p1"; rl_sleep 35
nav 1 "$RUN-p2"; rl_sleep 35
nav 2 "$RUN-p3"; rl_sleep 35
OFF=$(rl_offset "$EV/$RUN-hu.txt")
send ACTION_EXPORT_LOG > "$EV/$RUN-export-reply.txt"
rl_wait_line "$EV/$RUN-hu.txt" 'Automation: log export finished (' 60 "$OFF"
F=$(tail -c +$((OFF + 1)) "$EV/$RUN-hu.txt" | grep -aF 'Automation: log export finished (' | tail -1 | sed 's/.*finished (\(.*\)).*/\1/' | tr -d '\r')
[ -n "$F" ] && adb -s "$HU" pull "$F" "$EV/$RUN-export.txt" >/dev/null 2>&1
step "$RUN-end"
send ACTION_DISCONNECT >/dev/null; sleep 5
adb -s "$PH" shell am force-stop "$MAPS"
rl_cap_stop; appexit; link_off
```

**`grade-arm.sh`**
```bash
untestable_if_hot
H="$EV/$RUN-hu.txt"; P="$EV/$RUN-phone.txt"; W="$EV/$RUN-win.txt"
[ "$(cat "$EV/$RUN-session.txt" 2>/dev/null)" = session=ok ] ||
  { rl_verdict UNTESTABLE "no SSL handshake within 120 s, on two bring-ups"; exit 0; }
rl_win "$H" "$RUN-start" "$RUN-end" > "$W"
read -r AAC PCM P48 NBC <<< "$(arm_keys)"
S4=PCM; [ "$PCM" = false ] && S4=AAC_LC
M6=AAC_LC; [ "$AAC" = false ] && M6=PCM
CAP=$(grep -acF '[ServiceDiscovery] AAC music announced by the 2.4 GHz cap' "$W")
[ "${CAP:-0}" -ge 1 ] && { M6=AAC_LC; echo "INFO L-CAP printed: music is AAC by the 2.4 GHz cap"; }
[ "$NBC" = false ] && cnd C0 eq "${CAP:-0}" 0 "L-CAP absent with narrow-band-profile-cap false"

# session bullets
cnd S1 ge "$(grep -acF 'SSL handshake complete' "$W")" 1 "L-SSL the session formed"
REND=$(grep -aF 'Throughput over' "$W" | tail -1 | grep -ao 'rendered=[0-9]*' | cut -d= -f2)   # not-literal
cnd S2 gt "${REND:-0}" 0 "the last L-TP rendered frames"
VL=$(grep -aF 'Handshake: the phone selected ' "$W")
cnd S3 eq "$(printf '%s' "$VL" | grep -ac .)" 1 "L-VER exactly one version line"
cnd S4 eq "$(printf '%s\n' "$VL" | grep -acF 'STATUS_SUCCESS')" 1 "L-VER status STATUS_SUCCESS"
VER=$(printf '%s\n' "$VL" | grep -ao 'selected [0-9]*\.[0-9]*' | head -1 | cut -d' ' -f2)
V16=0; case "$VER" in 1.[6-9]|1.[1-9][0-9]|[2-9].*) V16=1 ;; esac
echo "INFO L-VER $VER"
sdc() { grep -aF '[ServiceDiscovery] Audio sink channel=' "$W" |
  awk -v ch="channel=$1 " -v cd="codec=MEDIA_CODEC_AUDIO_$2" 'index($0, ch) && index($0, cd) {n++} END {print n+0}'; }
cnd S5 eq "$(grep -acF '[ServiceDiscovery] Audio sink channel=' "$W")" 3 "L-SD exactly three sinks"
cnd S6 eq "$(sdc 6 "$M6")" 1 "L-SD channel 6 is $M6"
cnd S7 eq "$(sdc 4 "$S4")" 1 "L-SD channel 4 is $S4"
cnd S8 eq "$(sdc 5 "$S4")" 1 "L-SD channel 5 is $S4"
if grep -q NO_MUSIC "$EV/$RUN-music.txt" 2>/dev/null; then inc "music bullet: no music channel opened"
else
  DEC6=$(grep -aF 'AudioDecoder.start: channel=' "$W" | awk -v cd="codec=$M6" 'index($0, "channel=6,") && index($0, cd) {n++} END {print n+0}')
  cnd S9 ge "$DEC6" 1 "L-DEC6 music output codec=$M6"
  cnd S10 ge "$(awk 'index($0, "Media Start Request AUDIO:") {n++} END {print n+0}' "$W")" 1 "L-MSR6 music started"
fi
read -r SET1 IDX1 <<< "$(awk '
  index($0, "Media Sink Setup Request: ") && index($0, "on channel AUDIO1") {s++; want = 1; next}
  want && index($0, "Config response:") {inb = 1}
  inb && index($0, "configuration_indices: 1") {i++}
  inb && index($0, "(maxUnacked=") {inb = 0; want = 0}
  END {print s + 0, (i > 0 ? 1 : 0)}' "$W")"
WANT_IDX=0; case "$RUN" in R2|R3) WANT_IDX=$V16 ;; esac
cnd S11 ge "$SET1" 1 "L-SETUP the AUDIO1 sink was set up"
cnd S12 eq "$IDX1" "$WANT_IDX" "L-IDX the 48 kHz offer is $WANT_IDX"
case "$RUN" in R2|R3) [ "$V16" = 1 ] || inc "AAP $VER is below 1.6: the 48 kHz bullets are INCONCLUSIVE" ;; esac
cnd F1 eq "$(grep -acF 'AapAudio: invalid configuration=' "$W")" 0 "no invalid configuration line"
cnd F2 eq "$(grep -acF ' has only configuration 0; ignoring index=' "$W")" 0 "no ignored index line"
cnd F3 eq "$(grep -acF 'is not an audio codec, keeping codec=' "$W")" 0 "no non-audio codec line"
cnd F4 eq "$(grep -a -A3 'FATAL EXCEPTION' "$W" | grep -acF com.andrerinas)" 0 "no crash of the app"   # system
cnd P1 eq "$(rl_cnt "$P" "$RUN-start" "$RUN-end" 'Critical error')" 0 "phone Critical error"   # phone
cnd P2 eq "$(rl_cnt "$P" "$RUN-start" "$RUN-end" 'ByeBye')" 0 "phone ByeBye"
EXP=$(grep -a -m1 -o 'pcmGuidance:[a-z]* prefer48kGuidance:[a-z]*' "$EV/$RUN-export.txt" 2>/dev/null)
cnd S13 eq "$(same "$EXP" "pcmGuidance:$PCM prefer48kGuidance:$P48")" 1 "L-EXP export header matches the keys"

# guidance bullets: one pass over the window, in log order
read -r NMSR NSEL BADSEL BADRATE BADDEC BADCODEC BADSRC NSTOP OPEN PICK1 IDXS CONC STALE <<< "$(awk -v codec="codec=$S4" '
  function num(s, k,   i, t) { i = index(s, k); if (!i) return -1; t = substr(s, i + length(k)); sub(/[^0-9].*/, "", t); return t + 0 }
  index($0, "Media Start Request AUDIO1:") { m++; p = num($0, "config_index="); pend = 1; lm = NR; if (p == 1) one++
    idx = idx (idx == "" ? "" : ",") p }
  index($0, "AapAudio: AUDIO1 selected configuration=") { s++; k = num($0, "configuration="); r = num($0, "sampleRate=")
    if (pend && k != p) bsel++; pend = 0
    if ((k == 0 && r != 16000) || (k == 1 && r != 48000)) brate++
    cur = r; seen = 1 }
  index($0, "AudioDecoder.start: channel=4,") { r = num($0, "sampleRate=")
    if ((seen && r != cur) || (!seen && r != 16000)) bdec++
    if (!index($0, codec)) bcod++ }
  index($0, "PCM channel=4 source=") { r = num($0, "source="); if (seen && r != cur) bsrc++ }
  index($0, "PCM overflow channel=4 negotiatedSource=") { r = num($0, "negotiatedSource="); if (seen && r != cur) bsrc++ }
  index($0, "Audio Stop: AUDIO1") { st++; ls = NR }
  index($0, "AudioMixer: id=") && index($0, " channel=4 target=") { c = num($0, "concealedFrames="); x = num($0, "staleFrames=")
    if (c > mc) mc = c; if (x > mx) mx = x }
  END { print m + 0, s + 0, bsel + 0, brate + 0, bdec + 0, bcod + 0, bsrc + 0, st + 0, (lm > ls ? 1 : 0), one + 0,
    (idx == "" ? "-" : idx), mc + 0, mx + 0 }' "$W")"
echo "INFO AUDIO1 starts=$NMSR stops=$NSTOP config_index=$IDXS concealedFrames.max=$CONC staleFrames.max=$STALE"
if [ "${NMSR:-0}" -lt 2 ]; then inc "guidance bullets: $NMSR AUDIO1 starts, 2 needed"
else
  STOPOK=0
  if [ "$NSTOP" -eq "$NMSR" ] || { [ "$NSTOP" -eq $((NMSR - 1)) ] && [ "$OPEN" -eq 1 ]; }; then STOPOK=1; fi
  cnd G1 eq "$STOPOK" 1 "L-STOP1 every prompt ended ($NSTOP stops, $NMSR starts)"
  cnd G2 eq "$NSEL" "$NMSR" "L-SEL one per AUDIO1 start"
  cnd G3 eq "$BADSEL" 0 "L-SEL configuration equals the config_index before it"
  cnd G4 eq "$BADRATE" 0 "L-SEL configuration 0 is 16000, 1 is 48000"
  cnd G5 eq "$BADDEC" 0 "L-DEC sampleRate follows L-SEL (16000 before the first)"
  cnd G6 eq "$BADCODEC" 0 "L-DEC codec=$S4"
  cnd G7 eq "$BADSRC" 0 "L-SRC and L-OVF rates follow L-SEL"
fi
case "$RUN" in
  R1|R4) cnd G8 eq "${PICK1:-0}" 0 "every config_index is 0" ;;
  R2|R3) [ "${NMSR:-0}" -ge 1 ] && [ "${PICK1:-0}" -eq 0 ] && inc "D-POCO kept 16 kHz (no config_index=1): the 48 kHz path is INCONCLUSIVE" ;;
esac
if [ "$NFAIL" -eq 0 ] && [ -n "$INC" ]; then rl_verdict INCONCLUSIVE "$INC"; fi
```

Every run's PASS needs these **session bullets**, in its window from `<run>-start` to `<run>-end` (S1 to S13, F1 to
F4, P1 and P2 in the grade):

- L-SSL ≥ 1, the last L-TP has `rendered=` > 0, and L-VER = 1 ending `status STATUS_SUCCESS`. Record `X.Y`.
- L-SD: exactly 3 lines, and the codecs match the run's row in section 6.
- L-DEC6 ≥ 1 with the codec of the row's channel 6 (`codec=AAC_LC` or `codec=PCM`), and L-MSR6 ≥ 1. If `music`
  wrote `NO_MUSIC`, this bullet is INCONCLUSIVE.
- L-SETUP has a line `on channel AUDIO1`, and L-IDX in its Config response matches the row.
- 0 lines from the FAIL table.
- Phone: `Critical error` = 0 and `ByeBye` = 0 in the window.
- The export header `pcmGuidance:<b> prefer48kGuidance:<b>` matches the run's keys.

And these **guidance bullets** (G1 to G7), graded only when L-MSR1 ≥ 2 (else INCONCLUSIVE):

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

- **R1 (control):** every `config_index=` is 0 (G8).
- **R2 (the point):** record whether the phone picked 1. If every `config_index=` is 0, the 48 kHz bullets are
  INCONCLUSIVE (this phone keeps 16 kHz), and the rest still grade. If any is 1, the rate bullets above MUST hold
  for it.
- **R3:** as R2, with `narrow-band-profile-cap` false so music is PCM on a 2.4 GHz link too. L-CAP MUST be absent.
- **R4 (option off):** every `config_index=` is 0 (G8), L-IDX is 0 although `prefer-48k-guidance` is true, and
  channel 4 is `AAC_LC` in L-SD and L-DEC.

Stop rule: a run with no `SSL handshake complete` within 120 s of `<run>-start` gets one more bring-up. A second
miss makes it UNTESTABLE, and the round stops there (section 0).

### cleanup: restore D-HU

**`cleanup.sh`**
```bash
rl_step "restore D-HU"
hustop
hu_cat > "$EV/settings-end-hu.xml"
grep -ao 'playback-focus-self-defeating"[^/]*' "$EV/settings-end-hu.xml" > "$EV/post-focus-latch.txt"
N=$(grep -ac '</map>' "$EV/settings-backup-hu.xml" 2>/dev/null)
if [ "${N:-0}" -ge 1 ]; then
  F=/data/data/$PKG/shared_prefs/settings.xml
  OWN=$(adb -s "$HU" shell stat -c %u:%g "/data/data/$PKG" | tr -d '\r')
  adb -s "$HU" push "$EV/settings-backup-hu.xml" /data/local/tmp/pcm-restore.xml >/dev/null
  adb -s "$HU" shell cp /data/local/tmp/pcm-restore.xml "$F"
  adb -s "$HU" shell chown "$OWN" "$F"
  adb -s "$HU" shell chmod 660 "$F"
  hu_cat > "$EV/settings-after-hu.xml"
  diff "$EV/settings-backup-hu.xml" "$EV/settings-after-hu.xml" > "$EV/settings-diff-hu.txt"
fi
adb -s "$PH" shell am force-stop "$MAPS"
```

**`grade-cleanup.sh`**
```bash
cnd restore-hu eq "$(wc -c < "$EV/settings-diff-hu.txt" 2>/dev/null)" 0 "D-HU settings.xml back to the backup"
echo "INFO after R4: $(cat "$EV/post-focus-latch.txt" 2>/dev/null)"
```

## 8. Do not re-run

Nothing from earlier threads. Music-only stutter grading belongs to the audio continuity thread.

## 9. Report back

1. For R2 and R3: L-VER, L-IDX, and every `config_index=` the phone sent. Did D-POCO ever pick 48 kHz?
2. Did the four rates agree for every prompt (phone index, `selected configuration`, `AudioDecoder.start`, mixer
   `source=`)? Name any prompt where they did not.
3. Each run's L-SD codecs against section 6's table, and the phone's `Critical error` and `ByeBye` counts.
4. L-MSR1 and L-STOP1 per run, and the largest channel 4 `concealedFrames=` and `staleFrames=`.

One verdict per run in a table at the top of the results file, and one line on whether the 48 kHz path was reached.
