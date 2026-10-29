# projection-teardown-and-relays, round 1 brief: decoder teardown, kept-surface returns, three relayed verbs and a connect race, A/B on D-HU, D-HP and D-SAM

## 0. Pre-flight request, and the time budget

**The round runs unattended.** It has no hand step after the first run starts. Send the request below to the operator once, before R0, and wait for the answer. It is the only request of the round.

> **Pre-flight request.** Please do these before the round starts:
> 1. On D-POCO, open Android Auto's developer settings and start the head unit server. D-HP connects to it, and no adb route can start it.
> 2. Only if the read below says D-SAM's Bluetooth is off: turn it on in D-SAM's own Settings. API 19 has no adb route for it.
> 3. Only if D-SAM's battery reads below 30 %: connect D-SAM to a separate charger. Its USB port does not charge it.

Read the two D-SAM values before you send the request, and drop the items that do not apply:

```bash
adb -s 30041c35642d2200 shell dumpsys bluetooth_manager | grep -a -m3 -iE 'enabled|state'
adb -s 30041c35642d2200 shell dumpsys battery | grep -a level
```

After the answer, check the server once with `listening` (section 5). **Do not wait for it and do not ask again.** If it does not listen at the start of stage 2, mark every D-HP run UNTESTABLE and go on to stage 3. If D-SAM's Bluetooth still reads off at stage 3, mark R6 UNTESTABLE for both builds. **D-MOTO is not used.** It can be behind a PIN, and adb has no route past one.

| Step | Estimated wall clock |
|---|---|
| R0: build both APKs, unit tests, identity | 25 min |
| Pre-flight, backups, starting state | 10 min |
| R3 TEXTURE, 6 screen-off returns, both builds | 12 min |
| R3G and R3S, 3 screen-off and 2 Home returns each, both builds | 20 min |
| R5, both builds | 10 min |
| R2, 20 cycles per build | 25 min |
| Installs on D-HU (2) | 6 min |
| R1, 20 Home cycles per build | 18 min |
| R5P, both builds | 5 min |
| R4-C, up to 10 cycles | 12 min |
| R4L-C | 3 min |
| Installs on D-HP (2) | 5 min |
| R6, 5 Home cycles per build, with installs | 15 min |
| Restore, zip, upload | 8 min |
| **Round total** | **about 2 h 55 min** |

A crash segment adds about 2 min for a new bring-up. A setup failure adds one re-run of that run.

## 1. Build and baseline

| | Where | SHA | Gate |
|---|---|---|---|
| Candidate | fork `o-jcardenass/open-headunit`, branch `fix/projection-teardown-and-relays` | `24b4bc0a7eb93498a58f0e4d22b7e4c5d0eb2b37` | 2702 JVM tests, 0 failures |
| Baseline | `andreknieriem/open-headunit` `main` | `2ca3b1f1a807faa11ba8608fd8a905acc09c65a9` | 2689 JVM tests |

The candidate is four commits on the baseline. No history was rewritten. **Every run is an A/B and needs both builds**, except R4 and R4L, which run on the candidate only.

```bash
git fetch fork fix/projection-teardown-and-relays
git checkout 24b4bc0a7eb93498a58f0e4d22b7e4c5d0eb2b37
git log --oneline 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9..HEAD | wc -l      # 4
git rev-parse HEAD~4                                                          # 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9 = the baseline
```

Build the candidate, then `git checkout HEAD~4` and build the baseline. **Copy each APK out of `apks/` as soon as it is built**, because `build_hur.sh` deletes the previous one. Install with `adb -s <serial> install -r <named apk>` every time. Never install through `set_hu_pref.sh`.

Identity is a symbol in the DEX, because version name and code do not move:

```bash
for s in FeedLoopPolicy ReturnRearmPolicy SameEndpointConnectPolicy; do printf '%s ' $s; unzip -p cand.apk 'classes*.dex' | strings | grep -cF $s; done   # each >= 1 on the candidate, 0 on the baseline
```

Before every run, `apk_check` (in `ptr_lib.sh`) pulls the installed APK and hashes it on the host. A piped `cat | md5sum` is not trustworthy. Set `WANT_MD5` to the md5 of the build under test whenever you install a build. `hu_open`, `hp_open` and `tab_open` call `apk_check` first and refuse to start on a mismatch. Every result goes to `$OUT/apk-check.log`; quote that file in Setup notes.

### Host thermal gate (every run)

The host PC (i7-3632QM) throttles under load and can shut down when it overheats, even with new thermal paste. A throttled host delays adb and log capture, which skews the picture times this round grades. A shutdown stops the round. So every run is gated on the host's temperature.

- **Before every run segment:** `apk_check` calls `th_gate`. It waits until the CPU package reads below 75C, for at most 15 min. Then it starts a watcher that logs the temperature and the throttle counter every 20 s to `$OUT/<RUN>.thermal`.
- **If the host does not cool in 15 min:** wait 15 more min with no adb work. If it is still at 75C or above, stop the round. Mark every run not yet started UNTESTABLE (host thermal). Push the results written so far.
- **Write the results file after every run, not at the end.** A shutdown then loses at most the run in progress.
- **Grade each run's thermal record.** Report `thermal_max` and the throttle-counter delta for every run in its section.
  - If the delta is above 0, or `thermal_max` is 90C or above, the run's timing numbers are suspect. That covers picture time T in R3, R3G, R3S and R6, and the race timing in R4. Cool to 70C and re-run that run once.
  - Count verdicts (crash counts, the race invariant, the verb counts) stand either way.
- **If `rig_thermal.sh` from the last round is on the host, keep it.** `th_gate` uses it when present, and the inline fallback below when not.

### R0. Gate

The build is the hottest step on the host: the last round peaked at 94C there. So, before R0, run `th_wait 70`, start `th_watch $OUT/R0.thermal &`, and build with `--max-workers=2`. Then run `run_unit_tests.sh` on the candidate. **PASS:** 2702 tests and 0 failures, counted from the JUnit XML, including `FeedLoopPolicyTest`, `ReturnRearmPolicyTest`, `SameEndpointConnectPolicyTest` and `AutomationCommandPolicyTest`. The two md5s differ. The three identity greps pass. A build or test failure stops the round.

## 2. What this is and why it exists

The baseline arms of the last three rounds (`pr-1045-framing-round1-results.md`, `pr-1046-video-retirement-round1-results.md`, `pr-1047-session-reconnect-round1-results.md` and its addendum) found five defects that are on `main`. This branch fixes them. One commit fixes two of them.

1. **`VideoDecoder`: a codec built on a dying surface, and a feed thread that feeds a failed codec.**
   - D-HU, H.265 hardware decode, GLES: `Fatal signal 11 (SIGSEGV)` in thread `VideoDecoder-Fe`, once, in the voided first V1H-B run (return 2, a Home return). The output thread had declared the codec dead, and the feed thread kept calling into it.
   - D-HP, API 17, SURFACE, Headunit Server mode: `@@@ ABORTING: LIBC: HEAP MEMORY CORRUPTION IN dlfree` in thread `AapTransport:Ha`, right after a Home press (V7P-B, return 3). The signature is `Decoder stopped: surfaceDestroyed`, then `Codec initialized:` on the dying surface, then a second `Decoder stopped: surfaceDestroyed`, then the abort.
   - The fix: a teardown for a dying surface now forgets the surface (`detachSurface`), so `decode()` cannot rebuild until `setSurface` hands over the next one. The feed thread stops when the output thread asks for a restart. Below API 21 the feed thread uses the input buffers of its own codec.
   - **The new invariant:** on the candidate, no `Codec initialized:` appears between a `Decoder stopped: surfaceDestroyed` and the next `New surface set: `. The baseline can show one. That is the race.
   - Both crashes were seen once in a run. So this round grades the race invariant below, which every teardown tests. It runs 20 Home cycles on D-HP and 20 cycles on D-HU per build.
2. **`AapProjectionActivity`: a TEXTURE screen-off return has no picture for 90 s or more.** TextureView keeps its SurfaceTexture across screen off, so no surface callback arrives on the return. The keyframe re-arm lived only in that callback. On the baseline, V2T-B had no picture in 90 s on return 4 and 98 s on return 6. The fix acts 300 ms after a resume that follows a decoder stop. If no surface callback arrived since the resume, the activity re-arms the recovery itself. It then logs `AapProjectionActivity: returned to a kept surface with a stopped decoder; re-arming keyframe recovery`.
3. **`AapService`: three automation verbs did nothing.** `AutomationReceiver` relays `ACTION_RESTART_AUDIO`, `ACTION_REFRESH_SENSORS` and `ACTION_RAISE_PROJECTION` (and their legacy `aap.action.*` spellings) as a service start, and `onStartCommand` had no branch for them. The pr-1047 round measured it in C2.0: receiver line present, handler lines 0, tracks restarted 0, on both builds. The candidate adds the three branches. A raise with no session logs `AapService: raise projection ignored, no session`.
4. **`CommManager`: a USER connect killed our own auto-connect to the same endpoint.** In Headunit Server mode the app connects to the phone's server by itself within about 1 to 3 s. An `ACTION_CONNECT` that lands while that attempt holds its claim preempts it (`ConnectionArbiter: <ip>:5277 (USER) preempts the socket from <ip> (WIRELESS_HANDSHAKE)`). The phone's server then wedges: 27 failed cycles in 6 minutes on the baseline (pr-1045, O2). The candidate joins the attempt in flight instead and logs `CommManager: <ip>:5277 is already connecting; not preempting it`. A USER connect to a session that is already live is not joined and still redials.

## 3. What is different about this round

- **The point of the round is two runs: R1 (D-HP Home cycling) and R3 (D-HU TEXTURE screen-off returns).** R2, R4 and R5 are the other fixes. R3G, R3S and R6 are controls.
- **The return to the projection is an `am start` of the projection activity, not the raise verb.** The raise verb is defect 3: it does nothing on the baseline, so an A/B cannot use it as its return. The command `back` (section 5) sends the same intent the service builds for a raise (`NEW_TASK | REORDER_TO_FRONT`, `focus=true`). `AapProjectionActivity` is exported and `singleTask`, so this brings the live instance forward. It is the same on both builds. It replaces the 35 s `monkey` route of the last rounds. R5 tests the raise verb itself.
- **Do not use `am start -n .../MainActivity` as a return.** `MainActivity` and the projection share one task and both are `singleTask`, so that command can clear the projection activity off the task.
- **A crash is the measurement in R1, R2 and R6, not a discard.** A crash re-forms the session, so a second `SSL handshake complete` after a crash does not void the run. `crashloop` ends the segment at a crash and opens a new segment with a new capture.
- **Teardown has to be proved per cycle.** `rig-quirks/topics/projection.md` says Home does not tear a surface down on D-HU. The pr-1046 round measured the opposite on GLES: Home and launcher returns logged `Decoder stopped: surfaceDestroyed` on both builds, and only screen off kept the surface. `ptr_crash.py` counts the teardown stops per cycle, and R1 and R2 need them.
- **Rig lessons from the last three rounds, applied in the helpers:**
  - D-HU's adb shell is root and has no `su` binary. Use the fixed `ohu_lib.sh` from `hur-wifi-test-scripts/pr-1046-round1/`, which has no `su -c`.
  - Phone radios go off with `svc wifi disable` and `svc bluetooth disable`, verified with `dumpsys`. `cmd connectivity airplane-mode` does not turn D-POCO's radios off.
  - `adb pull` has no `-q`. Use one logcat capture per run segment, never two. Read `topResumedActivity` on Android 14 and `mResumedActivity` on API 17 and 19 (`top` reads both). Never use `pkill -f`; kill by pid.
  - D-HP's serial is `0123456789ABCDEF`. The template's units table says `CNU350BGBJ`; record the difference in Setup notes. D-SAM needs `connection-modes` to contain `wifi`. Every `adb` call and every `adb push` names the unit with `-s`.
  - In Headunit Server mode the app connects by itself within about 1 to 3 s. `hp_open` waits 45 s and sends `ACTION_CONNECT` only as a fallback. Only R4 races it on purpose.
  - Restore `settings.xml` from the backup at the end and diff it against the backup.
- **R4 runs on the candidate only.** The baseline failure is already measured: `pr-1045-framing-round1-results.md`, O2, 27 failed cycles in 6 minutes on 2026-10-05. A baseline arm would only wedge the phone's server again. On the candidate, a wedge is itself the FAIL evidence, so R4 stops on the first one and does not try to recover. R4 and R4L run last in stage 2, and R4L runs last of all. A wedge then costs no other run.
- **Every run proves its path before it is graded.** `preflight` (section 5) runs after each bring-up and checks the capture and the settings. A preflight FAIL is a setup failure, not a result. Fix the settings, re-run that run once, and never grade the failed attempt. A second preflight FAIL makes the run UNTESTABLE.
- **`adb logcat -G 16M` fails below API 21, and that is expected on D-HP and D-SAM.** `cap_start` skips it there and uses a tag-filtered capture that restarts itself, as pr-1046 did on the tablets.
- **Pre-registered INCONCLUSIVE outcomes**, none a failure:
  - R1, R2 and R6 for the mechanism when the baseline shows no crash and no race window. The candidate result then stands as a regression guard only.
  - R2's H.265 leg if the phone asks for H.264 (`Media Sink Setup Request: 3 on channel VIDEO` with no `7`).
  - R4 if fewer than 3 cycles land in the window after 10 cycles.
  - R5's front check for a raise whose strategy line reads `NOTIFICATION`. That raise is reached but not shown, by design (`allowNotificationFallback = false`).
  - Every D-HP run is UNTESTABLE if nothing listens on the phone's `:5277` at the start of stage 2.
- **The rig's audio settings are a deliberate worst case.** Do not change `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and record them in Setup notes.
- **D-HU hard-reboots under sustained multi-core load.** No run in this brief adds load. If `adb devices` loses D-HU, stop the round (rig broken).
- **Never run adb calls in parallel against one unit.**

### Stages and USB ports

| Stage | Plugged in | Runs |
|---|---|---|
| 1 | D-HU, D-POCO | R3, R3G, R3S, R5, R2 (baseline arm, then the candidate arm) |
| 2 | D-HP, D-POCO (D-HU and D-SAM may stay plugged in) | R1-B, R5P-B; then the candidate: R1-C, R5P-C, R4-C, R4L-C (last) |
| 3 | D-SAM, D-POCO | R6 |

Before stage 2 and before stage 3, release D-POCO from D-HU, because D-HU holds one hands-free link at a time:

```bash
HU=27870808938846; send ACTION_EXIT; sleep 3; adb -s $HU shell am force-stop $PKG
adb -s $HU shell svc bluetooth disable; adb -s $HU shell svc wifi disable     # restore both with enable at the end of the round
```

## 4. Settings keys

Write the keys with the app stopped, with `ohu_setkeys.py` through `hu_put` (D-HU) or `tab_put` (D-HP, D-SAM). Read every key back before the launch. **At the start of the round, back up each unit's `settings.xml` (`settings-backup-HU.xml`, `-HP.xml`, `-SAM.xml`).** Diff each backup against the same unit's backup from the pr-1046 round folder, `hur-wifi-test-scripts/pr-1046-round1/`. State the delta in Setup notes. If that folder has no backup for a unit, record the unit's full key list instead.

| Key | Type | Value | Notes |
|---|---|---|---|
| `view-mode` | int | `0` SURFACE, `1` TEXTURE, `2` GLES | per run |
| `video-codec` | string | `H.264` | `H.265` in R2 only. Compared as a literal string |
| `resolutionId` | int | `3` | D-HU only. 1080p |
| `force-software-decoding` | boolean | `false` | D-HU only |
| `software-video-decoder` | int | delete | D-HU only |
| `debug-video-fault-injection`, `debug-video-fault-rate`, `debug-video-fault-budget` | int | delete | D-HU only |
| `video-profile-starvation-cap` | boolean | delete | every unit. A latch that forces 720p30 and AAC after three fumbled sessions |
| `wifi-connection-mode` | int | `3` | `1` on D-HP |
| `log-level` | int | `2` | INFO. Every decisive line in section 6 is INFO or WARN |
| `night-mode` | int | `1` | DAY, so `NightMode update:` lines in R5 come only from the verb |
| `native-driver-selection-mode` | int | `0` | D-HU and D-SAM. Stack armed, no selector countdown |
| `onboarding-version` | int | `2` | every unit |
| `connection-modes` | string set | `wifi` | D-HP and D-SAM: write it, because both connect over WiFi only and pr-1046 found D-SAM at `{usb}`. D-HU: read it and record it; write it only if it lacks `wifi` |

`ACTION_LOG_MARKER` is not gated on either build, so `allow-external-configuration` is not needed. On D-HP and D-SAM, write only `wifi-connection-mode`, `log-level`, `view-mode`, `native-driver-selection-mode` (D-SAM), `onboarding-version`, `connection-modes` and the starvation delete. Leave their codec and resolution as found, and record them.

```bash
HUBASE="int:resolutionId=3 bool:force-software-decoding=false del:software-video-decoder del:debug-video-fault-injection del:debug-video-fault-rate del:debug-video-fault-budget del:video-profile-starvation-cap int:wifi-connection-mode=3 int:log-level=2 int:night-mode=1 int:native-driver-selection-mode=0 int:onboarding-version=2"
COMMON="str:video-codec=H.264 $HUBASE"                       # R3, R3G, R3S, R5 (each adds its view-mode)
R2KEYS="str:video-codec=H.265 int:view-mode=2 $HUBASE"       # R2 only: one codec key, no duplicate
HPKEYS="int:wifi-connection-mode=1 int:log-level=2 int:view-mode=0 int:onboarding-version=2 set:connection-modes=wifi del:video-profile-starvation-cap"
SAMKEYS="int:wifi-connection-mode=3 int:log-level=2 int:view-mode=2 int:native-driver-selection-mode=0 int:onboarding-version=2 set:connection-modes=wifi del:video-profile-starvation-cap"
```

## 5. Every action as a verb, and the helpers

Every action on the app is a `send ...` verb (template section 3), with two named exceptions below.

| Step | Command | Note |
|---|---|---|
| marker | `send ACTION_LOG_MARKER --es text <label>` (helper `mark`) | prints `AutomationMarker: <label>` at WARN |
| identity | `send ACTION_QUERY_STATE` | record the reply's `commit` |
| connect to the phone's server (D-HP) | `send ACTION_CONNECT --es ip $PHIP` | TCP to `:5277`, USER tier |
| restart audio | `send ACTION_RESTART_AUDIO`, legacy `send aap.action.RESTART_AUDIO` | R5 |
| refresh sensors | `send ACTION_REFRESH_SENSORS`, legacy `send aap.action.REFRESH_SENSORS` | R5 |
| raise the projection | `send ACTION_RAISE_PROJECTION`, legacy `send aap.action.RAISE_PROJECTION` | R5 and R5P only |
| exit between runs | `send ACTION_EXIT`, `sleep 3`, then `am force-stop` | stops the service and ends the session cleanly |

`send` prefixes `com.andrerinas.openheadunit.`, so the legacy spelling `aap.action.RESTART_AUDIO` reaches the receiver as `com.andrerinas.openheadunit.aap.action.RESTART_AUDIO`.

**Two named non-verb steps, with the reason:**

- **`back`, the return to the projection** (`am start -n $PKG/com.andrerinas.openheadunit.aap.AapProjectionActivity -f 0x10020000 --ez focus true`). The verb that does this job is defect 3 and is inert on the baseline, so an A/B return cannot use it (section 3).
- **`am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`**, the app launch, as in template section 3. It is used only to start the app, never as a return.

OS-level steps, allowed: `input keyevent 3` (HOME), `223` (SLEEP), `224` (WAKEUP), `wm dismiss-keyguard`, `dumpsys activity activities`, `svc wifi|bluetooth enable|disable`, `dumpsys wifip2p`.

**No hand step runs during the round.** The pre-flight request (section 0) is the only human step. At the start of stage 2, check the server once, with no wait:

```bash
listening && echo SERVER_UP || echo "SERVER_DOWN: every D-HP run is UNTESTABLE; go to stage 3"
PHIP=$(adb -s $PH shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
```

**`preflight <RUN>`** runs inside `hu_open`, `hp_open`, `tab_open` and `r5`, after the bring-up. Set `PF_MODE`, `PF_VIEW` and `PF_SINK` before each run. It writes `$OUT/<RUN>.preflight` and marks `<RUN>-preflight-ok` or `<RUN>-preflight-FAIL` in the capture. Its checks:

| Check | Exact test | Units |
|---|---|---|
| WiFi mode | `grep -acF "WifiLauncher: Initializing WiFi Mode: NATIVE"` (D-HU, D-SAM) or `... AUTO` (D-HP) is 1 or more | all |
| view backend | `grep -acF "Projection backend: viewMode=<TEXTURE|GLES|SURFACE> "` is 1 or more | all |
| codec on the wire | `grep -acF "Media Sink Setup Request: 7 on channel VIDEO"` on H.265 runs, `3` on H.264 runs, is 1 or more | all |
| hardware decoder | the last `Codec initialized: <name>` exists, and `<name>` does not match `^(omx\.google\.|c2\.android\.|omx\.ffmpeg\.)|\.sw\.|software` (the app's own `DecoderCapabilityReport.isSoftwareName`) | all |
| no hotspot | `grep -acE 'HotspotManager:|SoftApCredentials:'` is 0 | Native AA runs |
| a WiFi Direct group | `dumpsys wifip2p` prints `groupFormed: true` | Native AA runs |
| `connection-modes` | the `<set>` in `settings.xml` contains `wifi` | all |
| no starvation cap | `video-profile-starvation-cap` is absent from `settings.xml` | all |

`PF_SINK` follows the run's codec. On the tablets it follows the backup's `video-codec`: `7` only if that is literally `H.265`, else `3`.

### Scripts

Make the folder `hur-wifi-test-scripts/projection-teardown-and-relays-round1/`. Copy these four files into it from `hur-wifi-test-scripts/pr-1046-round1/`, as fixed in that round: `ohu_lib.sh`, `ohu_setkeys.py`, `ohu_pair.py`, `ohu_returns.py`. Then save the four files below beside them. List all of them in Setup notes. If a script does not match the real line format, fix it, say so in Setup notes, and keep going.

Source order: `ohu_lib.sh`, then `ptr_lib.sh`. `ptr_lib.sh` uses `PKG`, `send`, `mark`, `nl`, `waitfor`, `observe`, `PIC`, `hu_put`, `tab_put`, `run_open`, `phone_air_on` and `phone_air_off` from `ohu_lib.sh`.

**`ptr_lib.sh`**:

```bash
# ptr_lib.sh : source it AFTER ohu_lib.sh (the fixed copy from pr-1046-round1). Needs HU, PH, OUT, PUT, BASEXML.
PROJ=$PKG/com.andrerinas.openheadunit.aap.AapProjectionActivity
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
# back : the return command. The same intent AapService.launchAapProjectionActivity builds (NEW_TASK | REORDER_TO_FRONT, focus=true).
back() { adb -s "$HU" shell am start -n $PROJ -f 0x10020000 --ez focus true >/dev/null 2>&1; }
top() { adb -s "$HU" shell dumpsys activity activities | grep -aE 'topResumedActivity|mResumedActivity' | head -2; }
# fastwait <secs> <fixed-string> <from-line> : 0 when the string appears at or after <from-line>; polls every 0.1 s
fastwait() { local end=$((SECONDS+$1)); while [ $SECONDS -lt $end ]; do
  tail -n +"$3" "$CAP" | grep -aqF -- "$2" && return 0; sleep 0.1; done; return 1; }
# seg <from-marker> <to-marker> : capture lines between two markers of this run
seg() { sed -n "/AutomationMarker: $1\$/,/AutomationMarker: $2\$/p" "$CAP"; }
crashes() { echo $(( $(grep -ac 'Fatal signal' "$CAP") + $(grep -ac 'ABORTING' "$CAP") + $(grep -a 'Process com.andrerinas.headunitrevived (pid' "$CAP" | grep -ac 'has died') )); }
# apk_check : pulls the installed APK, compares its md5 with $WANT_MD5 (set it per phase), logs the result
# Host thermal: th_pkg prints the CPU package temperature in C, th_thr the package throttle count.
th_pkg() { if [ -x ../rig_thermal.sh ]; then ../rig_thermal.sh status | grep -o 'pkg=[0-9]*' | cut -d= -f2; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
# th_gate : cool below 75C (max 15 min), then replace the previous watcher with one for this run
th_gate() { th_wait 75 || { echo "$(date +%T) HOST_TOO_HOT ${RUN:-run}" | tee -a "$OUT/thermal.log"; return 3; }
  [ -n "$THPID" ] && kill $THPID 2>/dev/null; th_watch "$OUT/${RUN:-run}.thermal" & THPID=$!
  echo "$(date +%T) ${RUN:-run} start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"; }
# th_report RUN : prints thermal_max and the throttle delta for the run's section
th_report() { awk -F'[ =C]+' '{p=$3; t=$5; if(p>m)m=p; if(NR==1)t0=t} END{print "thermal_max=" m "C throttle_delta=" t-t0}' "$OUT/$1.thermal"; }
apk_check() { th_gate || return 3; local m; adb -s "$HU" pull $(adb -s "$HU" shell pm path $PKG | cut -d: -f2 | tr -d '\r') "$OUT/live.apk" >/dev/null 2>&1
  m=$(md5sum "$OUT/live.apk" | cut -d' ' -f1); echo "$(date +%T) $HU live=$m want=$WANT_MD5" | tee -a "$OUT/apk-check.log"
  [ "$m" = "$WANT_MD5" ] || { echo "APK_MISMATCH $HU" | tee -a "$OUT/apk-check.log"; return 1; }; }
# cap_start / cap_stop : one capture per run segment. API 21 and up: full capture after logcat -G 16M.
# Below API 21 (D-HP, D-SAM) logcat -G fails, which is expected: use a tag-filtered capture that restarts
# itself when adb drops it, as pr-1046 did on the tablets. cap_stop removes the lines a restart dumps twice.
TAGS="OPENHU:V ActivityManager:I libc:V DEBUG:V AndroidRuntime:V *:S"
cap_start() { local api; api=$(adb -s "$HU" shell getprop ro.build.version.sdk | tr -d '\r'); adb -s "$HU" logcat -c; : > "$CAP"
  if [ "$api" -ge 21 ]; then adb -s "$HU" logcat -G 16M; stdbuf -oL adb -s "$HU" logcat -v time > "$CAP" & CAPPID=$!; CAPLOOP=
  else ( while :; do stdbuf -oL adb -s "$HU" logcat -v time $TAGS >> "$CAP"; echo "$(date +%T)" >> "$CAP.restarts"; sleep 1; done ) & CAPLOOP=$!; CAPPID=$CAPLOOP; fi; }
cap_stop() { if [ -n "$CAPLOOP" ]; then kill $CAPLOOP 2>/dev/null; pkill -P $CAPLOOP 2>/dev/null; wait $CAPLOOP 2>/dev/null
    awk '!seen[$0]++' "$CAP" > "$CAP.tmp" && mv "$CAP.tmp" "$CAP"
  else kill $CAPPID 2>/dev/null; wait $CAPPID 2>/dev/null; fi
  ps aux | grep -c "[l]ogcat"; }                    # must print 0; else kill the leftover by its pid
# hu_open <RUN> <spec...> : D-HU bring-up. The APK check, then ohu_lib.sh's run_open.
hu_open() { apk_check || return 1; CAPLOOP=; run_open "$@" || return 1; preflight "$1" || { run_end "$1"; return 2; }; }
# tab_open <RUN> <spec...> : D-SAM Native AA bring-up with the tablet capture
tab_open() { RUN=$1; shift; CAP=$OUT/$RUN.logcat; apk_check || return 1
  phone_air_on; adb -s "$HU" shell am force-stop $PKG; cap_start; sleep 1; $PUT "$BASEXML" "$@"
  adb -s "$HU" shell am start -n $MAIN >/dev/null; sleep 20
  local L; L=$(nl); phone_air_off
  waitfor 150 'SSL handshake complete' "$L" || { echo "SESSION_FAIL_SSL $RUN"; return 1; }
  waitfor 40 'AapProjectionActivity: onResume' "$L" || { back; waitfor 20 'AapProjectionActivity: onResume' "$L" || { echo "SESSION_FAIL_RAISE $RUN"; return 1; }; }
  waitfor 60 'Throughput over [0-9]+ms: rendered=[1-9]' "$L" || { echo "SESSION_FAIL_NO_FRAMES $RUN"; return 1; }
  sleep 20; mark "$RUN-start"; preflight "$RUN" || { run_end "$RUN"; return 2; }; }
# listening : the phone's head unit server listens on 5277 (0x149D, state 0A), IPv4 or IPv6
listening() { adb -s "$PH" shell cat /proc/net/tcp /proc/net/tcp6 | grep -aqiE ':149D [0-9A-F]+:0000 0A'; }
# prefs_cat : the unit's settings.xml (root read on D-HU, run-as on the tablets)
prefs_cat() { if [ "$HU" = 27870808938846 ]; then adb -s "$HU" shell cat /data/data/$PKG/shared_prefs/settings.xml
  else adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml; fi; }
# preflight <RUN> : proves in this capture that the path under test is active. Set PF_MODE (NATIVE|AUTO),
# PF_VIEW (TEXTURE|GLES|SURFACE) and PF_SINK (3|7) first. Marks <RUN>-preflight-ok or -FAIL; 2 on a FAIL.
preflight() { local f=$OUT/$1.preflight ok=1 c x; : > "$f"
  c=$(grep -acF "WifiLauncher: Initializing WiFi Mode: $PF_MODE" "$CAP"); echo "mode=$PF_MODE lines=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  c=$(grep -acF "Projection backend: viewMode=$PF_VIEW " "$CAP"); echo "viewMode=$PF_VIEW lines=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  c=$(grep -acF "Media Sink Setup Request: $PF_SINK on channel VIDEO" "$CAP"); echo "sink=$PF_SINK lines=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  x=$(grep -aF 'Codec initialized: ' "$CAP" | tail -n 1 | sed 's/.*Codec initialized: //' | tr -d '\r'); echo "codec=$x" >> "$f"
  if [ -z "$x" ] || echo "$x" | grep -qiE '^(omx\.google\.|c2\.android\.|omx\.ffmpeg\.)|\.sw\.|software'; then ok=0; fi
  if [ "$PF_MODE" = NATIVE ]; then
    c=$(grep -acE 'HotspotManager:|SoftApCredentials:' "$CAP"); echo "hotspot_lines=$c" >> "$f"; [ "$c" -eq 0 ] || ok=0
    x=$(adb -s "$HU" shell dumpsys wifip2p | grep -a -m1 groupFormed | tr -d '\r'); echo "p2p:$x" >> "$f"; echo "$x" | grep -q true || ok=0; fi
  prefs_cat > "$OUT/$1.prefs.xml"
  python3 -I -c 'import sys,xml.etree.ElementTree as E
r=E.parse(sys.argv[1]).getroot(); s=[e for e in r if e.get("name")=="connection-modes"]
v=[x.text for x in s[0]] if s else []; c=[e for e in r if e.get("name")=="video-profile-starvation-cap"]
print("connection-modes=%s starvation-cap-keys=%d" % (",".join(v), len(c))); sys.exit(0 if "wifi" in v and not c else 1)' "$OUT/$1.prefs.xml" >> "$f" || ok=0
  cat "$f"
  if [ $ok = 1 ]; then mark "$1-preflight-ok"; return 0; else mark "$1-preflight-FAIL"; echo "PREFLIGHT_FAIL $1"; return 2; fi; }
# cyc_home <RUN> <n> : Home, 3 s, the return command, a picture or 20 s
cyc_home() { local G L; G=$(nl); mark "$1-r$2-H-go"; adb -s "$HU" shell input keyevent 3; sleep 3
  L=$(nl); back; waitfor 60 'AapProjectionActivity: onResume' "$L" || echo "NO_RESUME $1-r$2"
  waitfor 20 "$PIC" "$G" || true; sleep 3; }
# cyc_off <RUN> <n> : screen off 3 s, wake, a picture or 20 s (crash cycling, not graded on picture time)
cyc_off() { local G L; G=$(nl); mark "$1-r$2-S-go"; adb -s "$HU" shell input keyevent 223; sleep 3
  L=$(nl); adb -s "$HU" shell input keyevent 224; sleep 2
  if ! waitfor 6 'AapProjectionActivity: onResume' "$L"; then adb -s "$HU" shell wm dismiss-keyguard; back
    waitfor 30 'AapProjectionActivity: onResume' "$L" || echo "NO_RESUME $1-r$2"; fi
  waitfor 20 "$PIC" "$G" || true; sleep 3; }
# observe30 <from-line> : a picture, or 30 s, then a 3 s soak. 30 s decides T <= 5 s and T > 10 s.
observe30() { waitfor 30 "$PIC" "$1" || echo "NO_PICTURE_30S"; sleep 3; }
# ret_off <RUN> <n> : a graded screen-off return. Off 5 s, wake, 30 s for a picture
ret_off() { local G L; G=$(nl); mark "$1-r$2-S-go"; adb -s "$HU" shell input keyevent 223; sleep 5
  L=$(nl); adb -s "$HU" shell input keyevent 224; sleep 2
  if ! waitfor 6 'AapProjectionActivity: onResume' "$L"; then adb -s "$HU" shell wm dismiss-keyguard; back; fi
  observe30 "$G"; }
# ret_home <RUN> <n> : a graded Home return. Home 5 s, the return command, 30 s for a picture
ret_home() { local G L; G=$(nl); mark "$1-r$2-H-go"; adb -s "$HU" shell input keyevent 3; sleep 5
  L=$(nl); back; waitfor 60 'AapProjectionActivity: onResume' "$L" || echo "NO_RESUME $1-r$2"; observe30 "$G"; }
# run_end <RUN> : end marker, stop the capture, extract, leave the unit clean
run_end() { mark "$1-end"; sleep 1; cap_stop
  tail -n 1 "$CAP" | cut -c1-40; adb -s "$HU" shell date
  python3 -I ptr_returns.py "$CAP" "$1" > "$OUT/$1.returns.json"
  python3 -I ptr_crash.py "$CAP" "$1" > "$OUT/$1.crash.json"
  send ACTION_EXIT >/dev/null; sleep 3; adb -s "$HU" shell am force-stop $PKG; }
# crashloop <RUN> <total> <open-fn> <spec...> : alternate the cycle functions in $CYC until <total> cycles
# or 3 crashes. A crash ends the segment; the next segment opens a new session and a new capture.
# A failed bring-up or preflight stops the loop with OPEN_FAIL (a setup failure, never graded);
# the open function has already closed that segment's capture.
crashloop() { local done=0 crash=0 n=0 k S C0
  while [ $done -lt $2 ] && [ $crash -lt 3 ]; do
    n=$((n+1)); S=$1-s$n
    $3 $S "${@:4}" || { echo "OPEN_FAIL $S" | tee -a "$OUT/$1.summary"; break; }
    C0=$(crashes)
    while [ $done -lt $2 ]; do
      for k in $CYC; do
        done=$((done+1)); $k $S $done
        if [ "$(crashes)" -gt "$C0" ]; then crash=$((crash+1)); mark "$S-crash"; sleep 5; break 2; fi
        [ $done -ge $2 ] && break
      done
    done
    run_end $S
  done
  echo "$1 cycles=$done crash_segments=$crash segments=$n" | tee -a "$OUT/$1.summary"; }
# hp_open <RUN> <spec...> : D-HP bring-up, after the APK check in Headunit Server mode. Waits 45 s for the app's own
# auto-connect and sends ACTION_CONNECT only as the fallback.
hp_open() { RUN=$1; shift; CAP=$OUT/$RUN.logcat; apk_check || return 1
  adb -s "$HU" shell am force-stop $PKG; cap_start; sleep 1
  $PUT "$BASEXML" "$@"
  local L; L=$(nl); adb -s "$HU" shell am start -n $MAIN >/dev/null
  waitfor 45 'SSL handshake complete' "$L" || { mark "$RUN-fallback-connect"; send ACTION_CONNECT --es ip "$PHIP" >/dev/null
    waitfor 60 'SSL handshake complete' "$L" || { echo "SESSION_FAIL_SSL $RUN"; return 1; }; }
  waitfor 40 'AapProjectionActivity: onResume' "$L" || { back; waitfor 20 'AapProjectionActivity: onResume' "$L" || { echo "SESSION_FAIL_RAISE $RUN"; return 1; }; }
  waitfor 60 'Throughput over [0-9]+ms: rendered=[1-9]' "$L" || { echo "SESSION_FAIL_NO_FRAMES $RUN"; return 1; }
  sleep 20; mark "$RUN-start"; preflight "$RUN" || { run_end "$RUN"; return 2; }; }
# race_cycle <RUN> <n> <T1|T2> : one launch, the verb by its trigger, 60 s for a session, a clean exit
race_cycle() { local L; L=$(nl); mark "$1-c$2-$3-go"
  adb -s "$HU" shell am start -n $MAIN >/dev/null
  if [ "$3" = T1 ]; then sleep 1; send ACTION_CONNECT --es ip "$PHIP" >/dev/null
  elif fastwait 20 'Auto-connecting to Headunit Server at' "$L"; then send ACTION_CONNECT --es ip "$PHIP" >/dev/null
  else mark "$1-c$2-noauto"; fi
  waitfor 60 'SSL handshake complete' "$L" && waitfor 30 'Throughput over [0-9]+ms: rendered=[1-9]' "$L"
  sleep 5; mark "$1-c$2-end"; send ACTION_EXIT >/dev/null; sleep 3; adb -s "$HU" shell am force-stop $PKG; sleep 10; }
# last_race <RUN> : class and ssl count of the last cycle so far
# inwin <RUN> : the number of IN_WINDOW cycles with same_endpoint true so far
inwin() { python3 -I ptr_race.py "$CAP" "$1" "$PHIP" | python3 -c 'import json,sys; print(sum(1 for c in json.load(sys.stdin)["cycles"] if c["class"]=="IN_WINDOW" and c["same_endpoint"]))'; }
last_race() { python3 -I ptr_race.py "$CAP" "$1" "$PHIP" | python3 -c 'import json,sys; c=json.load(sys.stdin)["cycles"][-1]; print(c["class"], c["ssl"])'; }
```

**`ptr_returns.py`** (pr-1046's `ohu_returns.py` plus the re-arm fields; `ohu_pair.py` reads its output unchanged):

```python
#!/usr/bin/env python3
"""ptr_returns.py <capture> <RUN> : per-return metrics as JSON. pr-1046's ohu_returns.py plus the re-arm fields.

A window opens at the marker '<RUN>-r<N>-<route>-go' (H Home, S screen off) and closes at the next
'-go' marker, '<RUN>-end', or 150 s. Device times from `logcat -v time`, in ms.
"""
import json, re, sys

cap, run = sys.argv[1], sys.argv[2]
TS = re.compile(r'^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3})')
rows = []
with open(cap, 'rb') as f:
    for n, raw in enumerate(f, 1):
        line = raw.decode('utf-8', 'replace').rstrip('\n')
        m = TS.match(line)
        if m:
            mo, d, h, mi, s, ms = map(int, m.groups())
            rows.append(((((d * 24 + h) * 60 + mi) * 60 + s) * 1000 + ms, n, line))

def first(pred, lo, hi):
    for t, n, l in rows:
        if lo <= t <= hi and pred(l):
            return t, n, l
    return None

def count(sub, lo, hi):
    return sum(1 for t, n, l in rows if lo <= t <= hi and sub in l)

def marker_t(name):
    for t, n, l in rows:
        if re.search(r'AutomationMarker: ' + re.escape(name) + r'\s*$', l):
            return t
    return None

REARM = 'AapProjectionActivity: returned to a kept surface with a stopped decoder; re-arming keyframe recovery'
SCB = '[UI_DEBUG] [AapProjectionActivity] onSurfaceChanged'
FORBID = ['times in a row without rendering a frame', 'Both codec types failed',
          'Giving up to avoid an infinite restart loop']
TP = re.compile(r'Throughput over \d+ms: rendered=([0-9]+)')
gos = []
for t, n, l in rows:
    m = re.search(r'AutomationMarker: (' + re.escape(run) + r'-r(\d+)-([HS])-go)\s*$', l)
    if m:
        gos.append((t, int(m.group(2)), m.group(3)))
end = marker_t(run + '-end')
LAST = end if end else (rows[-1][0] if rows else 0)
out = {'run': run, 'returns': []}
for i, (t0, k, route) in enumerate(gos):
    t1 = min(gos[i + 1][0] if i + 1 < len(gos) else LAST, t0 + 150000)
    r = {'return': k, 'route': route, 'window_s': round((t1 - t0) / 1000, 1)}
    tps = [t for t, n, l in rows if t0 <= t <= t1 and 'Throughput over ' in l]
    r['throughput_lines'] = len(tps)
    r['throughput_max_gap_s'] = round(max((b - a) for a, b in zip(tps, tps[1:])) / 1000, 1) if len(tps) > 1 else None
    r['decoder_stopped'] = [l.split('Decoder stopped: ')[-1] for t, n, l in rows
                            if t0 <= t <= t1 and 'Decoder stopped: ' in l]
    r['codec_init'] = count('Codec initialized:', t0, t1)
    r['cycle_lines'] = count('relaunched surface has no picture after ', t0, t1)
    r['forbidden'] = {f: count(f, t0, t1) for f in FORBID if count(f, t0, t1)}
    r['fallback_surfaceview'] = count('Falling back to SurfaceView for this session', t0, t1)
    r['second_ssl_or_garbage'] = count('SSL handshake complete', t0, t1) + count('Magic Garbage detected in header', t0, t1)
    r['fatal_signal'] = count('Fatal signal', t0, t1)
    r['rearm_lines'] = count(REARM, t0, t1)
    res = first(lambda l: 'AapProjectionActivity: onResume' in l, t0, t1)
    if not res:
        r['no_onResume'] = True
        out['returns'].append(r)
        continue
    tr = res[0]
    r['resume_after_go_s'] = round((tr - t0) / 1000, 2)
    r['onStop_seen'] = bool(first(lambda l: 'AapProjectionActivity: onStop' in l, t0, tr))
    r['activity_stopped_before_resume'] = count('Decoder stopped: activity_stopped', t0, tr)
    lo, hi = tr - 1000, tr + 3000
    r['surface_path'] = {
        'UI_DEBUG_onSurfaceChanged': count(SCB, lo, hi),
        'New_surface_set': count('New surface set: ', lo, hi),
        'Gl_onSurfaceChanged': count('GlProjectionView: onSurfaceChanged', lo, hi),
        'Texture_available': count('TextureProjectionView: Surface available', lo, hi),
        'surfaceDestroyed_cb': count('SurfaceCallback: onSurfaceDestroyed', t0, tr + 1000)}
    ra = first(lambda l: REARM in l, tr, t1)
    r['rearm_rel_resume_s'] = round((ra[0] - tr) / 1000, 2) if ra else None
    sc = first(lambda l: SCB in l, tr, t1)
    r['surface_cb_rel_resume_s'] = round((sc[0] - tr) / 1000, 2) if sc else None
    # A double arm: the surface callback re-armed after the resume AND the return re-arm fired as well.
    r['double_arm'] = bool(ra and sc)
    ci = first(lambda l: 'Codec initialized:' in l, t0, t1)
    rep = first(lambda l: 'keyframe decoded - the picture is repaired' in l, t0, t1)
    clean = first(lambda l: 'First frame rendered (hardware decode)' in l
                  and 'no keyframe has decoded' not in l, ci[0] if ci else t0, t1)
    pics = [x[0] for x in (rep, clean) if x]
    tp = min(pics) if pics else None
    r['picture_rel_resume_s'] = round(max(0, tp - tr) / 1000, 2) if tp else None
    r['picture_before_resume'] = bool(tp and tp < tr)
    cy = first(lambda l: 'relaunched surface has no picture after ' in l, tr - 1000, t1)
    r['cycle_rel_resume_s'] = round((cy[0] - tr) / 1000, 2) if cy else None
    r['nudges'] = count('relaunched surface still has no picture - requesting video focus (unsolicited)', tr, t1)
    r['gray_lines'] = count('no keyframe has decoded', t0, t1)
    tpr = first(lambda l: (TP.search(l) and int(TP.search(l).group(1)) > 0) is True, tr, t1)
    r['throughput_after_resume_s'] = round((tpr[0] - tr) / 1000, 2) if tpr else None
    out['returns'].append(r)
out['counts'] = {k: count(k, 0, 10 ** 12) for k in
                 ['Decoder stopped: ', 'Codec initialized:', 'Throughput over ', REARM,
                  'Codec exception in output thread', 'Decoder stall detected', 'Fatal signal',
                  'SSL handshake complete', 'Media Sink Setup Request: 3 on channel VIDEO',
                  'Media Sink Setup Request: 7 on channel VIDEO']}
print(json.dumps(out, indent=1))
```

**`ptr_crash.py`**:

```python
#!/usr/bin/env python3
"""ptr_crash.py <capture> <RUN> : crashes, the teardown-race windows and per-cycle teardown proof, as JSON.

A race window opens at a decoder stop for a dying surface and closes at the next 'New surface set: ',
a crash, or '<RUN>-end'. A 'Codec initialized:' inside a window is a codec built on a dying surface.
"""
import json, re, sys

cap, run = sys.argv[1], sys.argv[2]
PKG = 'com.andrerinas.headunitrevived'
OPEN = ('Decoder stopped: surfaceDestroyed', 'Decoder stopped: onDetachedFromWindow',
        'Decoder stopped: projectionViewRecreate')
lines = [raw.decode('utf-8', 'replace').rstrip('\n') for raw in open(cap, 'rb')]
out = {'run': run, 'fatal': [], 'aborting': [], 'died': [], 'race_windows': [], 'cycles': {}}
win, cyc, closed = None, None, 0

def close(n, why):
    global win, closed
    if win is not None:
        win['closed_at'], win['closed_by'] = n, why
        closed += 1
        if win['codec_init']:
            out['race_windows'].append(win)
    win = None

for n, l in enumerate(lines, 1):
    m = re.search(r'AutomationMarker: ' + re.escape(run) + r'-r(\d+)-([HS])-go\s*$', l)
    if m:
        cyc = m.group(1) + m.group(2)
        out['cycles'][cyc] = {'detach_stops': 0, 'activity_stopped': 0, 'new_surface': 0,
                              'codec_init': 0, 'resume': 0}
    c = out['cycles'].get(cyc) if cyc else None
    if 'Fatal signal' in l:
        out['fatal'].append([n, l[:240]]); close(n, 'fatal')
    if 'ABORTING' in l:
        out['aborting'].append([n, l[:240]])
    if 'Process ' + PKG + ' (pid' in l and 'has died' in l:
        out['died'].append([n, l[:240]]); close(n, 'died')
    if re.search(r'AutomationMarker: ' + re.escape(run) + r'-end\s*$', l):
        close(n, 'end')
    if any(o in l for o in OPEN):
        if c: c['detach_stops'] += 1
        if win is None:
            win = {'opened_at': n, 'opened_by': l.split('Decoder stopped: ')[-1].strip(),
                   'codec_init': 0, 'codec_init_lines': [], 'stops_inside': 0}
        else:
            win['stops_inside'] += 1
    if 'Decoder stopped: activity_stopped' in l and c:
        c['activity_stopped'] += 1
    if 'Codec initialized:' in l:
        if c: c['codec_init'] += 1
        if win is not None:
            win['codec_init'] += 1; win['codec_init_lines'].append(n)
    if 'New surface set: ' in l:
        if c: c['new_surface'] += 1
        close(n, 'new_surface')
    if 'AapProjectionActivity: onResume' in l and c:
        c['resume'] += 1
close(len(lines), 'eof')
cyc_list = list(out['cycles'].values())
out['summary'] = {
    'cycles': len(cyc_list),
    'cycles_with_teardown': sum(1 for c in cyc_list if c['detach_stops'] or c['activity_stopped']),
    'cycles_with_detach_stop': sum(1 for c in cyc_list if c['detach_stops']),
    'home_cycles': sum(1 for k in out['cycles'] if k.endswith('H')),
    'home_cycles_with_detach_stop': sum(1 for k, c in out['cycles'].items() if k.endswith('H') and c['detach_stops']),
    'off_cycles': sum(1 for k in out['cycles'] if k.endswith('S')),
    'off_cycles_with_activity_stop': sum(1 for k, c in out['cycles'].items() if k.endswith('S') and c['activity_stopped']),
    'cycles_without_resume': sum(1 for c in cyc_list if not c['resume']),
    'windows_closed': closed,
    'race_count': len(out['race_windows']),
    'fatal_count': len(out['fatal']), 'aborting_count': len(out['aborting']), 'died_count': len(out['died'])}
for k in ['Codec exception in output thread', 'Decoder restart requested: ', 'SSL handshake complete',
          'skipped: surface is no longer current', 'Media Sink Setup Request: 3 on channel VIDEO',
          'Media Sink Setup Request: 7 on channel VIDEO']:
    out['summary'][k] = sum(1 for l in lines if k in l)
print(json.dumps(out, indent=1))
```

**`ptr_race.py`**:

```python
#!/usr/bin/env python3
"""ptr_race.py <capture> <RUN> <phone-ip> : one row per connect-race cycle, as JSON.

A cycle opens at '<RUN>-c<N>-<T1|T2>-go' and closes at '<RUN>-c<N>-end' (or the next go marker).
Class: IN_WINDOW when the verb landed after the auto-connect took the socket and before SSL (or with no
SSL at all); EARLY when it landed first; LATE after SSL; AMBIGUOUS within 100 ms of SSL; NO_VERB.
"""
import json, re, sys

cap, run, phip = sys.argv[1], sys.argv[2], sys.argv[3]
TS = re.compile(r'^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3})')
rows = []
with open(cap, 'rb') as f:
    for raw in f:
        line = raw.decode('utf-8', 'replace').rstrip('\n')
        m = TS.match(line)
        if m:
            mo, d, h, mi, s, ms = map(int, m.groups())
            rows.append(((((d * 24 + h) * 60 + mi) * 60 + s) * 1000 + ms, line))
GO = re.compile(r'AutomationMarker: ' + re.escape(run) + r'-c(\d+)-(T1|T2)-go\s*$')
gos = [(t, int(m.group(1)), m.group(2)) for t, l in rows for m in [GO.search(l)] if m]
AUTO = re.compile(r'Auto-connecting to Headunit Server at (\S+):5277')
TP = re.compile(r'Throughput over \d+ms: rendered=([1-9][0-9]*)')
VERB = 'AutomationReceiver: com.andrerinas.openheadunit.ACTION_CONNECT'
out = []
for i, (t0, k, trig) in enumerate(gos):
    endm = [t for t, l in rows if re.search(r'AutomationMarker: ' + re.escape(run) + r'-c%d-end\s*$' % k, l)]
    t1 = endm[0] if endm else (gos[i + 1][0] if i + 1 < len(gos) else rows[-1][0])
    w = [(t, l) for t, l in rows if t0 <= t <= t1]
    def ft(sub):
        for t, l in w:
            if sub in l:
                return t, l
        return None
    a, v, s = ft('Auto-connecting to Headunit Server at'), ft(VERB), ft('SSL handshake complete')
    tpic = next((t for t, l in w if TP.search(l)), None)
    auto_ip = AUTO.search(a[1]).group(1) if a else None
    ta, tv, tss = (a[0] if a else None), (v[0] if v else None), (s[0] if s else None)
    if tv is None:
        cls = 'NO_VERB'
    elif ta is None or tv < ta:
        cls = 'EARLY'
    elif tss is not None and abs(tv - tss) <= 100:
        cls = 'AMBIGUOUS'
    elif tss is None or tv < tss:
        cls = 'IN_WINDOW'
    else:
        cls = 'LATE'
    c = lambda sub: sum(1 for t, l in w if sub in l)
    out.append({'cycle': k, 'trigger': trig, 'class': cls, 'auto_ip': auto_ip,
                'same_endpoint': auto_ip == phip if auto_ip else None,
                'verb_after_auto_ms': (tv - ta) if (tv is not None and ta is not None) else None,
                'ssl_after_go_s': round((tss - t0) / 1000, 2) if tss else None,
                'picture_after_go_s': round((tpic - t0) / 1000, 2) if tpic else None,
                'join': c('is already connecting; not preempting it'),
                'preempt_socket': c('preempts the socket from'),
                'refused': c('refused while'),
                'peer_silent': c('the peer accepted the connection and then sent nothing'),
                'session_connecting': c('AapService: session state connecting'),
                'ssl': c('SSL handshake complete'),
                'fatal': c('Fatal signal')})
print(json.dumps({'run': run, 'phone_ip': phip, 'cycles': out}, indent=1))
```

## 6. The lines that decide every run

Every app line below was checked with `grep -F -r` against `app/src` at `24b4bc0a` and with `git grep -F` at `2ca3b1f1`. Lines marked **candidate only** are absent on the baseline by design. Lines marked *(composed)* render from a format string, and the runs grep the rendered form; section 10 lists their source fragments. System lines come from the platform, not from `app/src`. Grep every capture with `grep -a`.

| Meaning | Line | Source | Baseline |
|---|---|---|---|
| return reached the activity | `AapProjectionActivity: onResume` | `AapProjectionActivity.kt` | present |
| activity stopped | `AapProjectionActivity: onStop` | same | present |
| surface callback after a return | `[UI_DEBUG] [AapProjectionActivity] onSurfaceChanged` | same | present |
| a new surface was claimed (closes a race window) | `New surface set: ` | `VideoDecoder.kt` | present |
| decoder stop for a dying surface (opens a race window) *(composed)* | `Decoder stopped: surfaceDestroyed`, `Decoder stopped: onDetachedFromWindow`, `Decoder stopped: projectionViewRecreate` | `VideoDecoder.kt` + `DecoderStopPolicy.kt` | present |
| decoder stop on `onStop` *(composed)* | `Decoder stopped: activity_stopped` | same | present |
| codec built | `Codec initialized:` | `VideoDecoder.kt` | present |
| output thread declared the codec dead | `Codec exception in output thread`, `Decoder restart requested: ` | `VideoDecoder.kt` | present |
| **the return re-arm, candidate only** | `AapProjectionActivity: returned to a kept surface with a stopped decoder; re-arming keyframe recovery` | `AapProjectionActivity.kt` | absent |
| the 850 ms escalation fired | `relaunched surface has no picture after ` | `AapProjectionActivity.kt` | present |
| the inert gain fallback | `relaunched surface still has no picture - requesting video focus (unsolicited)` | same | present |
| **picture, form 1** | `VideoDecoder: keyframe decoded - the picture is repaired` | `VideoDecoder.kt` | present |
| **picture, form 2** (line ends here) | `First frame rendered (hardware decode)` | `VideoDecoder.kt` | present |
| gray, not a picture | `no keyframe has decoded` | `VideoDecoder.kt` | present |
| steady state | `Throughput over ` | `VideoDecoder.kt` | present |
| TextureView made a new SurfaceTexture | `TextureProjectionView: Surface available` | `TextureProjectionView.kt` | present |
| GLES re-reports its surface | `GlProjectionView: onSurfaceChanged` | `GlProjectionView.kt` | present |
| surface torn down | `SurfaceCallback: onSurfaceDestroyed` | `AapProjectionActivity.kt` | present |
| view backend demoted | `Falling back to SurfaceView for this session` | `AapProjectionActivity.kt` | present |
| a stale-surface stop skipped (old and new wording) | `skipped: surface is no longer current` | `VideoDecoder.kt` | present (1 site; 2 on candidate) |
| phone chose a codec *(composed)* | `Media Sink Setup Request: 7 on channel VIDEO` (H.265), `Media Sink Setup Request: 3 on channel VIDEO` (H.264) | `AapControl.kt` | present |
| verb landed | `AutomationReceiver: ` + the full action | `AutomationReceiver.kt` | present |
| marker | `AutomationMarker: ` | `AutomationEffectRunner.kt` | present |
| restart-audio handler ran | `AapService: Received request to restart audio` | `AapService.kt` | present (receiver only) |
| tracks restarted | `AapAudio: Restarting all audio tracks` | `AapAudio.kt` | present |
| sensor-refresh handler ran | `AapService: Received request to refresh all sensors` | `AapService.kt` | present (receiver only) |
| night state re-sent | `NightMode update: ` | `AapService.kt` | present |
| raise reached the launcher, with its route | `AapService: raising the projection by ` | `AapService.kt` | present |
| **raise with no session, candidate only** | `AapService: raise projection ignored, no session` | `AapService.kt` | absent |
| discovery handed over the socket | `Auto-connecting to Headunit Server at ` | `WifiLauncherSharedServices.kt` | present |
| **the join, candidate only** | `is already connecting; not preempting it` | `CommManager.kt` | absent |
| the preempt *(composed)* | `preempts the socket from` | `ConnectionArbiter.kt` + `CommManager.kt` | present |
| a claim refused | `refused while` | `ConnectionArbiter.kt` | present |
| a redial started *(composed)* | `AapService: session state connecting` | `AapService.kt` | present |
| server accepted and went silent | `the peer accepted the connection and then sent nothing` | `AapTransport.kt` | present |
| preflight: WiFi mode | `WifiLauncher: Initializing WiFi Mode: ` + `NATIVE` or `AUTO` | `WifiLauncherManager.kt` | present |
| preflight: view backend *(composed)* | `Projection backend: viewMode=` + `TEXTURE`, `GLES` or `SURFACE` | `AapProjectionActivity.kt` | present |
| preflight: hotspot path (must be 0) | `HotspotManager:`, `SoftApCredentials:` | `HotspotManager.kt`, `SoftApCredentialsProvider.kt` | present |
| session formed | `SSL handshake complete` | `AapSslContext.kt` | present |
| discard rules | `MATCH! Starting AapService`, `createGroup SUCCESS`, `Magic Garbage detected in header` | receivers, `WifiDirectManager.kt`, `AapReadSingleMessage.kt` | present |
| **system: native crash** | `Fatal signal` | libc / debuggerd | n/a |
| **system: libc abort (API 17)** | `ABORTING` | libc | n/a |
| **system: our process died** | `Process com.andrerinas.headunitrevived (pid` with `has died` | ActivityManager | n/a |
| **identity, in the DEX** | `FeedLoopPolicy`, `ReturnRearmPolicy`, `SameEndpointConnectPolicy` | candidate only | absent |

## 7. Runs

Run ids carry the build: `-B` baseline, `-C` candidate. **In each stage, run every baseline arm first, then install the candidate and repeat.** Each unit then changes build once per stage. The points of the round are **R1** and **R3**.

Starting state, read and recorded in Setup notes, never assumed:

```bash
OUT=~/hur-wifi-test-scripts/projection-teardown-and-relays-round1; mkdir -p $OUT; cd $OUT     # adjust to where hur-wifi-test-scripts/ lives
HU=27870808938846; PH=4f4027e9; OTHER=; PUT=hu_put; source ./ohu_lib.sh; source ./ptr_lib.sh
adb devices                                                              # D-HU, D-POCO, D-HP (0123456789ABCDEF), D-SAM listed
adb -s $HU shell date; date                                             # clock agreement to the second
adb -s $HU shell stat -c %U:%a /data/data/$PKG/shared_prefs              # record
adb -s $HU shell stat -c %U:%G /data/data/$PKG/shared_prefs/settings.xml | tr -d '\r' > $OUT/settings-owner-HU.txt   # pr-1046 read u0_a176:771
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > $OUT/settings-backup-HU.xml; BASEXML=$OUT/settings-backup-HU.xml
grep -aoE '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink|connection-modes)[^/]*' $BASEXML    # record; do not change
adb -s $PH shell dumpsys window | grep -a mCurrentFocus                  # D-POCO idle or at home; if Settings, input keyevent KEYCODE_HOME
adb -s $HU shell appops get $PKG SYSTEM_ALERT_WINDOW                     # record: R5's raise route depends on it
```

Every capture is `logcat -v time` with `stdbuf -oL`, and it starts before the launch. The helpers do this through `cap_start`. At the end of every run segment, `ps aux | grep -c "[l]ogcat"` must print 0. The last capture line must also be within 5 s of the unit's clock. If either check fails, that segment is INCONCLUSIVE.

**Discard rules (R3, R3G, R3S, R5, R4L):** discard the run and re-run it once if its window holds any of these lines:

- `MATCH! Starting AapService`;
- a second `createGroup SUCCESS` (D-HU);
- `Magic Garbage detected in header`;
- a second `SSL handshake complete`.

A second discard makes the run INCONCLUSIVE. **R1, R2 and R6 do not discard on a second SSL that follows a crash.** In those runs, a second SSL with no crash before it is still a discard.

### The return rules (a) to (d), used by R3, R3G, R3S and R6

`ohu_pair.py <baseline.json> <candidate.json>` applies them per return number, from pr-1046's brief. T is `picture_rel_resume_s`. A return with no picture, or with no `onResume`, counts as T = 999. A return is a FAIL return when any of these holds:

- **(a)** T on the candidate is above **10.0 s** and T on the baseline is below **3.0 s** (key `FAIL_a_over_10s_where_baseline_under_3s`);
- **(b)** T on the candidate exceeds the baseline's by more than **2.0 s**. Rule (b) fires only when **two or more** returns do this (keys `FAIL_b_delta_over_2s_returns`, `FAIL_b_fires`);
- **(c)** a candidate window holds `times in a row without rendering a frame`, `Both codec types failed` or `Giving up to avoid an infinite restart loop`, and the baseline window of the same return holds none (key `FAIL_c_forbidden_only_candidate`);
- **(d)** a candidate window has no `AapProjectionActivity: onResume`, or it logs `Falling back to SurfaceView for this session` where the baseline window does not (key `FAIL_d_no_resume_or_view_fallback_or_front_stop`).

A return whose window holds a second SSL or `Magic Garbage detected in header` in either build is void (key `void`). More than three void returns re-run the run once.

### R3. TEXTURE screen-off returns on D-HU, both builds (**point of the round**)

```bash
r3() {  # r3 <RUN> <view-mode> <screen-off returns> <Home returns>
  hu_open $1 $COMMON int:view-mode=$2 || return 1
  local n=0 i; for i in $(seq 1 $3); do n=$((n+1)); ret_off $1 $n; done
  for i in $(seq 1 $4); do n=$((n+1)); ret_home $1 $n; done
  run_end $1; }
PF_MODE=NATIVE PF_VIEW=TEXTURE PF_SINK=3
r3 R3-B 1 6 0           # baseline phase;  r3 R3-C 1 6 0 in the candidate phase
python3 -I ohu_pair.py $OUT/R3-B.returns.json $OUT/R3-C.returns.json > $OUT/R3.pair.json
```

T is `picture_rel_resume_s` in `R3-*.returns.json`: seconds from `AapProjectionActivity: onResume` to the first picture line (form 1 or 2). No picture in the 30 s observe window counts as 999. Thirty seconds decide both thresholds this run uses, 5 s and 10 s. A return is on the **kept-surface path** when its `surface_path.Texture_available` is 0 and `surface_path.New_surface_set` is 0.

**PASS (R3-C),** all of these:

- every screen-off return has T at most **5.0 s**;
- every return on the kept-surface path with `activity_stopped_before_resume` of 1 or more has `rearm_lines` equal to **1**, with `rearm_rel_resume_s` between 0.2 and 1.5;
- every return has `cycle_lines` of at most 1 and `fatal_signal` 0;
- `ohu_pair.py` reports no return for the return rules (a) to (d) above, and `FAIL_b_fires` is false.

**FAIL** if any of them is not met. **INCONCLUSIVE** if no candidate return is on the kept-surface path: the defect's precondition was not reached, so report which surface lines the returns did see.

**If the change did nothing,** the candidate looks like the baseline: `rearm_lines` 0 and some T of 999. **Report for R3-B** the T of each return and how many exceed 10 s. Expected from the pr-1046 round: about 2 of every 3. Zero such returns on the baseline does not change the candidate verdict, but say so.

### R3G and R3S. GLES and SURFACE controls on D-HU, both builds

```bash
PF_VIEW=GLES;    r3 R3G-B 2 3 2      # baseline phase;  R3G-C in the candidate phase
PF_VIEW=SURFACE; r3 R3S-B 0 3 2      # baseline phase;  R3S-C in the candidate phase
python3 -I ohu_pair.py $OUT/R3G-B.returns.json $OUT/R3G-C.returns.json > $OUT/R3G.pair.json
python3 -I ohu_pair.py $OUT/R3S-B.returns.json $OUT/R3S-C.returns.json > $OUT/R3S.pair.json
```

Returns 1 to 3 are screen off and 4 and 5 are Home.

**PASS (each of R3G-C and R3S-C):**

- `ohu_pair.py` reports no return for the return rules (a) to (d) above, and `FAIL_b_fires` is false;
- every candidate return has `cycle_lines` at most 1 and `fatal_signal` 0;
- **R3S only:** `double_arm` is false on every candidate return. A SURFACE return that its surface callback re-arms must not also log the return re-arm.

**Report only, both runs:** `rearm_lines`, `rearm_rel_resume_s` and `surface_cb_rel_resume_s` per return. On GLES, expect a re-arm only when the GL callback arrives more than 0.3 s after the resume. On SURFACE, a re-arm with no surface callback in the window means that the SurfaceView kept its surface. That is allowed.

### R5. The three relayed verbs on D-HU, both builds

```bash
r5() { RUN=$1; CAP=$OUT/$RUN.logcat; local L v; apk_check || return 1
  phone_air_on
  adb -s $HU shell am force-stop $PKG; cap_start; sleep 1
  $PUT $BASEXML $COMMON int:view-mode=2
  adb -s $HU shell am start -n $MAIN >/dev/null; sleep 20
  mark $RUN-nosess; send ACTION_RAISE_PROJECTION; sleep 4; top > $OUT/$RUN.top-nosess; mark $RUN-nosess-end
  L=$(nl); phone_air_off
  waitfor 150 'SSL handshake complete' $L || { echo "SESSION_FAIL $RUN"; return 1; }
  waitfor 40 'AapProjectionActivity: onResume' $L || back
  waitfor 60 'Throughput over [0-9]+ms: rendered=[1-9]' $L || { echo "NO_FRAMES $RUN"; return 1; }
  sleep 10; mark $RUN-start; PF_MODE=NATIVE PF_VIEW=GLES PF_SINK=3 preflight $RUN || { cap_stop; return 2; }
  for v in ACTION_RESTART_AUDIO aap.action.RESTART_AUDIO ACTION_REFRESH_SENSORS aap.action.REFRESH_SENSORS; do
    mark $RUN-$v; send $v; sleep 3; done
  for v in ACTION_RAISE_PROJECTION aap.action.RAISE_PROJECTION; do
    mark $RUN-$v; adb -s $HU shell input keyevent 3; sleep 3; top > $OUT/$RUN-$v.top-before
    send $v; sleep 4; top > $OUT/$RUN-$v.top-after
    if ! grep -q AapProjectionActivity $OUT/$RUN-$v.top-after; then L=$(nl); back; waitfor 30 'AapProjectionActivity: onResume' $L; fi
    sleep 5; done
  mark $RUN-tail; send ACTION_QUERY_STATE > $OUT/$RUN.state; sleep 2
  mark $RUN-end; sleep 1; cap_stop; send ACTION_EXIT >/dev/null; sleep 3; adb -s $HU shell am force-stop $PKG; }
r5 R5-B      # baseline phase;  r5 R5-C in the candidate phase
```

Count each line in its window with `seg <this marker> <next marker> | grep -acF '<line>'`. The markers in order are `-nosess`, `-nosess-end`, `-start`, the four audio and sensor markers, the two raise markers, `-tail`.

| Window | Line | R5-C must read | R5-B expected |
|---|---|---|---|
| `nosess` | `AutomationReceiver: com.andrerinas.openheadunit.ACTION_RAISE_PROJECTION` | 1 (else void) | 1 |
| `nosess` | `AapService: raise projection ignored, no session` | 1 | 0 |
| `nosess` | `AapService: raising the projection by ` | 0 | 0 |
| each of the two audio windows | `AutomationReceiver: ` + that action | 1 (else void) | 1 |
| each of the two audio windows | `AapService: Received request to restart audio` | 1 | 0 |
| each of the two audio windows | `AapAudio: Restarting all audio tracks` | 1 | 0 |
| each of the two sensor windows | `AapService: Received request to refresh all sensors` | 1 | 0 |
| each of the two sensor windows | `NightMode update: ` | 1 or more | 0 |
| each of the two raise windows | `AapService: raising the projection by ` | 1 | 0 |
| each of the two raise windows | `.top-after` names `AapProjectionActivity` | yes, when the raising line ends `DIRECT` or `OVERLAY` | no |
| `start` to `tail` | `SSL handshake complete` | 0 | 0 |
| `tail` | `ACTION_QUERY_STATE` reply | `"connected":true` | `"connected":true` |

**PASS (R5-C):** every cell in the R5-C column holds. **FAIL:** any handler line is 0 where the receiver line is 1. If a raising line ends `NOTIFICATION`, its front check is INCONCLUSIVE, not FAIL; record the `appops` value. `.top-nosess` must not name `AapProjectionActivity` on either build. **R5-B is the positive control:** receiver lines 1 and handler lines 0 reproduce defect 3. Report it as a finding if R5-B shows a handler line.

### R2. H.265 GLES crash cycling on D-HU, both builds

```bash
CYC="cyc_home cyc_off"; PF_MODE=NATIVE PF_VIEW=GLES PF_SINK=7
crashloop R2-B 20 hu_open $R2KEYS      # baseline phase;  R2-C in the candidate phase
```

20 cycles alternate a Home cycle and a screen-off cycle, so 10 of each. **Stop rule:** 20 cycles, or 3 crash segments, whichever comes first. The race invariant, not the crash count, is the main evidence: 10 Home teardowns give it 10 windows per build. Sum each field of `summary` over every `R2-<build>-s<N>.crash.json`.

**PASS (R2-C),** all of these:

- `fatal_count`, `aborting_count` and `died_count` are 0 in every segment;
- `race_count` is 0 in every segment;
- 20 cycles ran;
- `home_cycles_with_detach_stop` is at least 8 and `off_cycles_with_activity_stop` is at least 8 (the teardown happened).

**FAIL:** any crash line, or a `race_count` above 0. A crash in a process other than ours does not count; quote it. **INCONCLUSIVE:** fewer than 8 Home cycles with a detach stop. A phone that sends H.264 fails the preflight, so that case is a setup failure.

**Report both builds side by side:**

- the cycles run;
- the three crash counts, with each crash line and its thread name quoted;
- `race_count`, with the line numbers of each window;
- the counts of `Codec exception in output thread` and `Decoder restart requested: `.

**The mechanism was reached** if R2-B shows a crash or a `race_count` above 0. If R2-B shows neither, the candidate PASS is a regression guard only. Say so.

### R1. D-HP Home cycling, Headunit Server mode, SURFACE, both builds (**point of the round**)

Stage 2. D-POCO serves the head unit server. Check it once with `listening` (section 5). If it does not listen, every D-HP run is UNTESTABLE; go to stage 3.

```bash
HU=0123456789ABCDEF; PH=4f4027e9; PUT=tab_put; source ./ptr_lib.sh     # re-source: back, top and MAIN read HU when called
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-backup-HP.xml; BASEXML=$OUT/settings-backup-HP.xml
CYC="cyc_home"; PF_MODE=AUTO PF_VIEW=SURFACE PF_SINK=3     # PF_SINK=7 only if the D-HP backup's video-codec is H.265
crashloop R1-B 20 hp_open $HPKEYS      # baseline phase;  crashloop R1-C 20 hp_open $HPKEYS in the candidate phase
```

A host-side `logcat` has died on D-HP before. `cap_start` restarts it by itself and logs each restart in `<capture>.restarts`. Quote those files in Setup notes. Sum each `summary` field over every `R1-<build>-s<N>.crash.json`.

**PASS (R1-C),** all of these:

- `fatal_count`, `aborting_count` and `died_count` are 0 in every segment;
- `race_count` is 0 in every segment;
- 20 cycles ran;
- `home_cycles_with_detach_stop` is at least 17;
- `cycles_without_resume` is at most the baseline's plus 1.

**FAIL:** any crash line, or a `race_count` above 0. **INCONCLUSIVE:** fewer than 17 cycles with a detach stop (the Home press did not tear the surface down). The race invariant is the main evidence: 20 Home teardowns give it 20 windows per build.

**If the change did nothing,** the candidate shows the baseline's shape. Each Home cycle then logs two `Decoder stopped: surfaceDestroyed`, sometimes with a `Codec initialized:` between them.

**Report per build:**

- the mean `detach_stops` per Home cycle. Expect about 2 on the baseline and 1 on the candidate, because the second teardown callback now finds the surface already detached;
- the crash lines, quoted with their thread names;
- each race window.

**The mechanism was reached** if R1-B shows a crash or a `race_count` above 0. If R1-B shows neither, the candidate PASS is a regression guard only. Say so.

### R5P. The raise verb on API 17, D-HP, both builds

Run it right after R1 in each phase.

```bash
r5p() { hp_open $1 $HPKEYS || return 1
  mark $1-raise; adb -s $HU shell input keyevent 3; sleep 3; top > $OUT/$1.top-before
  send ACTION_RAISE_PROJECTION; sleep 4; top > $OUT/$1.top-after; mark $1-raise-end
  run_end $1; }
r5p R5P-B      # baseline phase;  r5p R5P-C in the candidate phase
```

**PASS (R5P-C):** in `seg R5P-C-raise R5P-C-raise-end`, `AapService: raising the projection by DIRECT` 1, `AapProjectionActivity: onResume` 1 or more, and `.top-after` names `AapProjectionActivity`. **R5P-B expected:** the raising line 0 and `.top-after` names the launcher. API 17 always takes the `DIRECT` route, so this is the front check that D-HU may not give.

### R4. The connect race on D-HP, candidate only

**No baseline arm runs.** `pr-1045-framing-round1-results.md`, O2, already measured the baseline: 27 failed cycles in 6 minutes on 2026-10-05, the verb preempting the app's own socket each time. Run R4-C after R5P-C, with the candidate installed. Check `listening` first; if the server is down, R4-C and R4L-C are UNTESTABLE.

```bash
r4open() { RUN=$1; CAP=$OUT/$RUN.logcat; apk_check || return 1
  adb -s $HU shell am force-stop $PKG; cap_start; sleep 1; $PUT $BASEXML $HPKEYS; }
r4close() { mark $1-end; sleep 1; cap_stop; python3 -I ptr_race.py $CAP $1 $PHIP > $OUT/$1.race.json; }
```

T1 sends the verb 1 s after the launch. T2 sends it when the app logs `Auto-connecting to Headunit Server at`, which aims it inside the window. `ptr_race.py` classes each cycle by the order of the three device timestamps. **Only `IN_WINDOW` cycles with `same_endpoint` true test the fix.** `EARLY` cycles (the verb won, discovery stood down) are outside the fix.

```bash
listening || echo "SERVER_DOWN: R4-C and R4L-C are UNTESTABLE"
r4open R4-C
for n in $(seq 1 10); do t=T2; [ $((n % 3)) = 1 ] && t=T1      # T1 on cycles 1, 4, 7, 10; T2 on the rest
  race_cycle R4-C $n $t; r=$(last_race R4-C); echo "$n $t $r" | tee -a $OUT/R4-C.progress
  [ "${r#* }" = 0 ] && { mark R4-C-wedged-c$n; break; }         # no session: stop, do not recover
  [ $n = 1 ] && { preflight R4-C || { echo "SETUP_FAIL R4-C"; break; }; }   # after the first session
  [ "$(inwin R4-C)" -ge 3 ] && break; done
inwin R4-C | tee $OUT/R4-C.inwin
r4close R4-C
```

`preflight` runs once, after the first cycle's session, with the R1 values of `PF_MODE`, `PF_VIEW` and `PF_SINK`. A preflight FAIL stops R4-C as a setup failure (section 3). If cycle 1 forms no session, the preflight cannot run; grade the cycle as below and say so.

**Stop rule:** 3 `IN_WINDOW` cycles with `same_endpoint` true, or 10 cycles, or the first cycle with no session (`ssl` 0), whichever comes first. **Do not try to recover a wedge.** On the candidate, a wedge is itself the FAIL evidence.

**PASS (R4-C),** all of these:

- at least **3** `IN_WINDOW` cycles with `same_endpoint` true;
- each of them has `join` 1 or more, `preempt_socket` 0, `ssl` 1 or more, and a `picture_after_go_s` that is not null;
- `preempt_socket` is 0 in every cycle with `same_endpoint` true;
- no cycle stopped the run with `ssl` 0.

**FAIL:** a `preempt_socket` above 0 in a cycle with `same_endpoint` true, or any cycle with `ssl` 0. Name that cycle's class in the report. **INCONCLUSIVE:** fewer than 3 `IN_WINDOW` cycles after 10 cycles, with every cycle forming a session. Cycles with `same_endpoint` false, `NO_VERB` or `AMBIGUOUS` are void; report their count. **If the change did nothing,** an `IN_WINDOW` cycle shows `preempt_socket` 1 and `join` 0, then no session, as on the baseline.

### R4L. A USER connect to a live session still redials, D-HP, candidate only

Run it last in stage 2, because a redial can wedge the server and no run follows it on D-HP. If `listening` fails first, R4L-C is UNTESTABLE.

```bash
listening && hp_open R4L-C $HPKEYS && { mark R4L-C-go; send ACTION_CONNECT --es ip $PHIP; sleep 15; mark R4L-C-mid; sleep 30; run_end R4L-C; }
listening || echo "SERVER_DOWN after R4L-C: record it in Setup notes"
```

**PASS:** in `seg R4L-C-go R4L-C-mid`, `is already connecting; not preempting it` is 0 and `AapService: session state connecting` is 1 or more. **FAIL:** the join line is 1 or more (a live session was joined). **Report only:** `SSL handshake complete` count after `R4L-C-go`, and whether a picture came back.

### R6. D-SAM (API 19, GLES) Home cycling, both builds

Stage 3. Release D-POCO from D-HU first (section 3). Record `dumpsys battery` and `adb -s 30041c35642d2200 shell date` against the host. Read D-SAM's Bluetooth with `adb -s 30041c35642d2200 shell dumpsys bluetooth_manager | grep -a -m3 -iE 'enabled|state'`. If it reads off, R6 is UNTESTABLE for both builds; do not ask for a hand step now (section 0). API 19 has no screen-off keyevent, so only Home cycles run.

```bash
HU=30041c35642d2200; PH=4f4027e9; PUT=tab_put; source ./ptr_lib.sh
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-backup-SAM.xml; BASEXML=$OUT/settings-backup-SAM.xml
CYC="cyc_home"; PF_MODE=NATIVE PF_VIEW=GLES PF_SINK=3     # PF_SINK=7 only if the D-SAM backup's video-codec is H.265
crashloop R6-B 5 tab_open $SAMKEYS      # baseline phase;  R6-C in the candidate phase
python3 -I ohu_pair.py $OUT/R6-B-s1.returns.json $OUT/R6-C-s1.returns.json > $OUT/R6.pair.json
```

On a clean session, D-SAM prints two `createGroup SUCCESS` and two `Handling handshake for` lines before SSL. That is how it settles, not a discard.

**PASS (R6-C):**

- the three crash counts are 0;
- `race_count` is 0;
- 5 cycles ran;
- `ohu_pair.py` reports no return for the return rules (a), (c) and (d) above.

Rule (b) is report-only here, because `cyc_home` waits only 20 s for a picture. **Report:** `rearm_lines` per return, and the crash and race fields of both builds.

## 8. Do not re-run

- Whether Home or a cover tears a GLES, TEXTURE or SURFACE surface down on D-HU (video-black rounds; pr-1046 round 1). This round only proves the teardown per cycle.
- The 850 ms escalation window and the focus-cycle keyframe timing (video-black round 8).
- The codec pin over background cycles (pr-1046 V3S and V3G). Every stop reason this branch adds is a surface-lifecycle reason, so the pin is unchanged.
- Anything about handshakes, pokes, group identity or audio sinks. Nothing on the branch touches them.

## 9. Report back

The numbers that decide whether the branch goes to review:

1. **R1 and R2:** per build, cycles run, crash lines (`Fatal signal`, `ABORTING`, our `has died`) with thread names, and `race_count`. Say whether the mechanism was reached on the baseline.
2. **R3:** T per return for both builds in one table, with `rearm_rel_resume_s`, `cycle_rel_resume_s` and the kept-surface flag beside it.
3. **R4-C:** the `IN_WINDOW` rows: `join`, `preempt_socket`, `ssl`, `picture_after_go_s`, and the cycle that stopped the run, if any.
4. **R5 and R5P:** the window table filled for both builds, with the raise strategy each raise printed.

Results go in `projection-teardown-and-relays-round1-results.md`, in the template's skeleton (section 7). Use one `## R<id>` section per run id, with the verdict in bold alone on its line. Write the file after every run, and put the output of `th_report <RUN>` in each run's section. The run ids are R0, R3-B, R3-C, R3G-B, R3G-C, R3S-B, R3S-C, R5-B, R5-C, R2-B, R2-C, R1-B, R1-C, R5P-B, R5P-C, R4-C, R4L-C, R6-B, R6-C.

```markdown
# projection-teardown-and-relays, round 1 results

**Candidate:** fix/projection-teardown-and-relays @ 24b4bc0a7eb93498a58f0e4d22b7e4c5d0eb2b37       **Baseline:** main @ 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9
**APK md5:** <candidate> / <baseline>
**Unit:** D-HU (UNISOC MT50, Android 14) with D-POCO; D-HP (API 17); D-SAM (API 19)
**Date:** <yyyy-mm-dd>

## Setup notes

The settings.xml delta per unit, both md5s, `apk-check.log`, the identity greps, the scripts copied and added, the audio keys and connection-modes as found, the appops value, the starting-state output, the pre-flight request and its answer, every preflight file and every setup failure with its re-run, the capture restarts on the tablets, the D-HP serial against the template's units table, every deviation and every string that did not match.

## R<id> (one per run id above)

**PASS** | **FAIL** | **INCONCLUSIVE** | **UNTESTABLE**

- Settings written:
- Radio state and how it was set:
- Discard-rule check: clean / re-run N times
- Decisive lines, quoted with device timestamps:
- Measurements: the JSON fields the run grades, as numbers

## Anything the brief did not ask about
```

**Final step on every unit:** stop the app. Restore the backup `settings.xml` without `video-profile-starvation-cap`, and read the key back. The read must print 0. Diff the restored file against the backup and record the diff in Setup notes.

Tablets (D-HP, D-SAM), through `run-as`:

```bash
tab_put "$BASEXML" del:video-profile-starvation-cap
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml | grep -ac video-profile-starvation-cap    # 0
```

D-HU, as root. Do the copy, owner and mode by hand, with the owner recorded at the start of the round (`u0_a176:771` in pr-1046):

```bash
HU=27870808938846; python3 -I ohu_setkeys.py $OUT/settings-backup-HU.xml $OUT/restore-HU.xml del:video-profile-starvation-cap
OWN=$(cat $OUT/settings-owner-HU.txt)
adb -s $HU shell am force-stop $PKG
adb -s $HU push $OUT/restore-HU.xml /data/local/tmp/restore-HU.xml
adb -s $HU shell cp /data/local/tmp/restore-HU.xml /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell chown $OWN /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell stat -c %U:%G:%a /data/data/$PKG/shared_prefs/settings.xml                     # must read the recorded owner and 660
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml | grep -ac video-profile-starvation-cap   # 0
adb -s $HU shell svc bluetooth enable; adb -s $HU shell svc wifi enable
```

Captures go to the fork's release **`rig-evidence-projection-teardown-and-relays`**, created on this first round, as one asset, `projection-teardown-and-relays-round1-captures.zip`. Cite the asset name and its sha256 in the results file.

```bash
zip -1 -r projection-teardown-and-relays-round1-captures.zip $OUT
sha256sum projection-teardown-and-relays-round1-captures.zip
gh release create rig-evidence-projection-teardown-and-relays --repo o-jcardenass/open-headunit --notes "projection-teardown-and-relays rig captures, one asset per round" projection-teardown-and-relays-round1-captures.zip
```

## 10. Strings, for mechanical re-verification

`decisive-strings` lists every app string the runs grep, as it appears literally in `app/src` (re-verify with `grep -F -r` on the candidate). The composed lines render from these fragments: `Decoder stopped: $reason` with the `DecoderStopPolicy` reasons (`surfaceDestroyed`, `onDetachedFromWindow`, `projectionViewRecreate`, `activity_stopped`); `ConnectionArbiter: $claim preempts $loser` with the claim text `the socket from `; `AapService: session state $state` with `connecting`; `Media Sink Setup Request: %d on channel %s`; `AutomationReceiver: $action`. Lines marked candidate-only in section 6 are absent on the baseline by design. `system-strings` are platform lines, not in `app/src`. The identity symbols are grepped in the DEX.

```decisive-strings
AapProjectionActivity: onResume
AapProjectionActivity: onStop
[UI_DEBUG] [AapProjectionActivity] onSurfaceChanged
New surface set: 
Decoder stopped: 
surfaceDestroyed
onDetachedFromWindow
projectionViewRecreate
activity_stopped
Codec initialized:
Codec exception in output thread
Decoder restart requested: 
AapProjectionActivity: returned to a kept surface with a stopped decoder; re-arming keyframe recovery
relaunched surface has no picture after 
relaunched surface still has no picture - requesting video focus (unsolicited)
VideoDecoder: keyframe decoded - the picture is repaired
First frame rendered (hardware decode)
no keyframe has decoded
Throughput over 
TextureProjectionView: Surface available
GlProjectionView: onSurfaceChanged
SurfaceCallback: onSurfaceDestroyed
Falling back to SurfaceView for this session
skipped: surface is no longer current
times in a row without rendering a frame
Both codec types failed
Giving up to avoid an infinite restart loop
Media Sink Setup Request: 
AutomationReceiver: 
AutomationMarker: 
AapService: Received request to restart audio
AapAudio: Restarting all audio tracks
AapService: Received request to refresh all sensors
NightMode update: 
AapService: raising the projection by 
AapService: raise projection ignored, no session
Auto-connecting to Headunit Server at 
is already connecting; not preempting it
 preempts 
the socket from 
refused while
AapService: session state 
the peer accepted the connection and then sent nothing
WifiLauncher: Initializing WiFi Mode: 
Projection backend: viewMode=
HotspotManager:
SoftApCredentials:
SSL handshake complete
MATCH! Starting AapService
createGroup SUCCESS
Magic Garbage detected in header
```

```system-strings
Fatal signal
ABORTING
Process com.andrerinas.headunitrevived (pid
has died
```

```identity-symbols
FeedLoopPolicy
ReturnRearmPolicy
SameEndpointConnectPolicy
```
