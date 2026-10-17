# projection-teardown-and-relays, round 2 brief: the connect race with the adopted socket, and H.265 SURFACE crash cycling, on D-HU and D-HP

## 0. Pre-flight request, and the time budget

**The round runs unattended after the pre-flight request.** It has no hand step after R2-B starts. Do the reads below, then send the request to the operator once, before R0, and wait for the answer.

Read D-POCO's Bluetooth bonds and clear round 1's wedge first. Round 1 left the phone's head unit server deaf, and only a restart of Android Auto clears it. A force stop also drops the server, so the operator must start it again. A phone-side capture runs from the force stop to the `listening` check, so the round proves that the server restarted.

Before this block, make the folder and save the scripts (section 5).

```bash
OUT=~/hur-wifi-test-scripts/projection-teardown-and-relays-round2; mkdir -p $OUT; cd $OUT      # adjust to where hur-wifi-test-scripts/ lives
HU=27870808938846; PH=4f4027e9; OTHER=; PUT=hu_put; source ./ohu_lib.sh; source ./ptr_lib.sh
RUN=preflight; clockcheck preflight-start
adb -s $PH shell svc bluetooth enable; sleep 5
adb -s $PH shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices' | tee $OUT/poco-bonds-before.txt
pcap_start preflight; stamp server start
adb -s $PH shell am force-stop com.google.android.projection.gearhead
```

> **Pre-flight request.** Please do these before the round starts:
> 1. On D-POCO, open Android Auto's developer settings and start the head unit server. I force-stopped Android Auto to clear the wedge from round 1, and that stops the server.
> 2. Only if the bond list below has no `Navegadortz2`: pair D-POCO with D-HU's Bluetooth radio, `Navegadortz2`. Round 1 unpaired it, and R2 needs D-POCO to wake D-HU.
> 3. Only if the bond list shows `Navegadortz3`: forget `Navegadortz3` (D-SAM's radio) on D-POCO. A second head-unit bond can stop D-POCO waking D-HU.

Drop items 2 and 3 when they do not apply. Paste the bond list under the request. After the answer, read the bonds and the server once. **Do not wait and do not ask again.**

```bash
adb -s $PH shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices' | tee $OUT/poco-bonds-after.txt
grep -c Navegadortz2 $OUT/poco-bonds-after.txt     # must be 1 or more, else R2-B and R2-C are UNTESTABLE
grep -c Navegadortz3 $OUT/poco-bonds-after.txt     # must be 0; if not, record it and continue
listening && echo SERVER_UP_PREFLIGHT || echo SERVER_DOWN_PREFLIGHT    # record; stage 2 checks it again
stamp server end; pcap_stop; clockcheck preflight-end
python3 -I ptr_phone.py $OUT/preflight.phone.logcat $OUT/preflight.windows.tsv | tee $OUT/preflight.phone.json
grep -a 'Network server running on port' $OUT/preflight.phone.logcat | tail -n 1     # quote it, with its tag
```

**The server restart is proved** when `server_running` in `preflight.phone.json` is 1 or more and `listening` printed `SERVER_UP_PREFLIGHT`. If `server_running` is 0 while `listening` is up, the phone prints a different line on this Gearhead build. Record that, quote what the capture shows near the toggle, and continue. **D-SAM and D-MOTO are not used.**

| Step | Estimated wall clock |
|---|---|
| R0: candidate build, unit tests, identity, classifier self-test (baseline APK reused when its md5 matches) | 25 min |
| Pre-flight, operator answer, backups, starting state | 10 min |
| R2-B, 20 cycles | 13 min |
| Candidate install on D-HU | 3 min |
| R2-C, 20 cycles | 13 min |
| Stage change, D-HP backup and candidate install | 6 min |
| R4-C, up to 10 cycles | 18 min |
| R4L-C | 4 min |
| Restore, zip, upload, results | 10 min |
| **Round total** | **about 1 h 45 min** |

A crash segment in R2 adds about 2 min for a new bring-up. A setup failure adds one re-run of that run. A host thermal wait adds up to 15 min per run.

## 1. Build and baseline

| | Where | SHA | Gate |
|---|---|---|---|
| Candidate | fork `o-jcardenass/open-headunit`, branch `fix/projection-teardown-and-relays`, its tip | record it in R0 | 2713 JVM tests, 0 failures |
| Baseline | `andreknieriem/open-headunit` `main` | `2ca3b1f1a807faa11ba8608fd8a905acc09c65a9` | as round 1 |

The candidate's history was rewritten since round 1. Round 1 built `24b4bc0a`. A fixup now folds into that commit, so the branch is four commits on the baseline again, with a new tip SHA. **Name the candidate by the branch, and record the SHA and the md5 you build.** The file tree is fixed, so R0 checks it.

```bash
git fetch fork fix/projection-teardown-and-relays
git checkout --detach fork/fix/projection-teardown-and-relays
git rev-parse HEAD                                     # record: the candidate SHA
git rev-parse 'HEAD^{tree}'                            # must be 4be97cfbd6f4a135b9c912c259657faba94e0f87
git merge-base HEAD 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9   # must be 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9
git log --oneline 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9..HEAD | wc -l   # record: 4, or 5 if the fixup was not squashed
```

**If the tree hash differs, stop and ask.** The branch then carries code this brief did not check.

**The baseline APK:** reuse round 1's `base.apk` if its md5 is `50d3c116369f99784908b2380cdec421`. Otherwise build it from `2ca3b1f1a807faa11ba8608fd8a905acc09c65a9`. Copy each APK out of `apks/` as soon as it is built, because `build_hur.sh` deletes the previous one. Install with `adb -s <serial> install -r -d <named apk>`. Never install through `set_hu_pref.sh`.

### Host thermal gate (every run)

The host PC throttles and can shut down when it overheats. Round 1's rules stand (`rig-quirks/topics/tooling.md`):

- `apk_check` calls `th_gate`. It waits until the CPU package reads below 75C, for at most 15 min, then logs the temperature every 20 s to `$OUT/<RUN>.thermal`.
- If the host does not cool in 15 min, wait 15 more min with no adb work. If it is still at 75C or above, stop the round. Mark every run not yet started UNTESTABLE (host thermal).
- Write the results file after every run.
- Put the output of `th_report <RUN>` in each run's section. If the throttle delta is above 0, or `thermal_max` is 90C or above, re-run R4-C once after a cool-down to 70C, because its class depends on timing. The R2 counts stand either way.

### R0. Gate

Before the build, run `th_wait 70` and start `th_watch $OUT/R0.thermal &`. Build with `--max-workers=2`.

1. Run the five `git` checks above. Record each output.
2. Build the candidate. Record its md5.
3. Run `run_unit_tests.sh` on the candidate.
4. Get the baseline APK (reuse or build, as above). Record its md5.
5. Run the identity greps:

```bash
for a in cand.apk base.apk; do printf '%s ' $a
  for s in 'adopting the socket discovery already opened' HeldServerSocket SameEndpointConnectPolicy FeedLoopPolicy; do
    printf '%s=%s ' "$s" $(unzip -p $a 'classes*.dex' | strings | grep -cF "$s"); done; echo; done
```

6. Run the classifier self-test on round 1's captures, with the round 2 scripts (section 5):

```bash
R1D=../projection-teardown-and-relays-round1
python3 -I ptr_race.py $R1D/R4-C.logcat R4-C 192.168.1.8 | python3 -c 'import json,sys; c=json.load(sys.stdin)["cycles"][0]; print(c["class"], c["verb_after_found_ms"], c["refused"], c["ssl"], c["route"])'
#   must print: IN_WINDOW 390 1 0 NONE
for r in R3S-B R3S-C; do printf '%s ' $r; python3 -I ptr_crash.py $R1D/$r.logcat $r | python3 -c 'import json,sys; print(json.load(sys.stdin)["summary"]["early_init_cycles"])'; done
#   must print: R3S-B 3, then R3S-C 0
```

**PASS:** all of these hold:

- the tree hash and the merge-base match;
- 2713 tests and 0 failures, counted from the JUnit XML, with `HeldServerSocketTest` and `SameEndpointConnectPolicyTest` present;
- the two md5s differ;
- each of the four identity strings counts 1 or more in `cand.apk` and 0 in `base.apk`;
- the self-test prints the three expected lines.

A build or test failure stops the round. A self-test mismatch means the scripts were copied wrong: fix the copy, then continue.

After each install, send `ACTION_QUERY_STATE` and record the reply's `commit`. On the candidate it must equal the SHA from step 1.

## 2. What this is and why it exists

Round 1 (`projection-teardown-and-relays-round1-results.md`) passed R0, R1, R3, R3G, R3S, R5, R5P and R6. Two runs need a second round.

**R4-C failed, and the fix did not cover the case it was for.** In Headunit Server mode, the app's network sweep opens a TCP socket to the phone's `:5277`. Android Auto's server accepts that socket and binds to it. The sweep then logs `NetworkDiscovery: Found Headunit Server on <ip>:5277` and hands the socket on. In round 1, the `ACTION_CONNECT` verb landed 390 ms after the Found line and 130 ms before `Auto-connecting to Headunit Server at`. The verb dialled a second connection, took the arbiter by tier, and the sweep's socket was refused and closed. The server then went deaf (`the peer accepted the connection and then sent nothing at all`) for the rest of the round. `ptr_race.py` opened its window at the Auto-connecting line, so it classed that cycle EARLY. The socket was taken 520 ms earlier, so the cycle was really in the window.

**What changed on the candidate.** The sweep now holds the socket it opened, under its endpoint, from the Found line on (`HeldServerSocket`). A USER connect to that endpoint takes the held socket instead of dialling a new one, and logs `CommManager: <ip>:5277 adopting the socket discovery already opened`. Exactly one caller can take the socket. If the sweep takes it first, the USER connect joins the attempt in flight and logs `CommManager: <ip>:5277 is already connecting; not preempting it`. If the USER took it first, the sweep logs `WifiLauncherSharedServices: <ip>:5277 is no longer held for discovery; not dialling it`, or says nothing if the session is already connecting. So a verb inside the window never opens a second connection.

**R2 was INCONCLUSIVE because GLES on D-HU does not tear its surface down on Home** (0 of 10 cycles on both builds). SURFACE does. Round 1's R3S logged `Decoder stopped: surfaceDestroyed` on both Home returns of both builds. On the baseline, 3 of its 5 returns also built a codec on the dying surface (`Codec initialized:` between the first `surfaceDestroyed` stop and the second). That is the race the `VideoDecoder` commit fixes, and it is what crashed D-HP in round 1. So R2 now runs SURFACE with H.265.

## 3. What is different about this round

- **The point of the round is R4-C.** R2 is the second fix, now on a backend that tears down. R4L-C is the control for a live session.
- **The race classifier is new.** The window opens at `NetworkDiscovery: Found Headunit Server on`, not at `Auto-connecting to Headunit Server at`. Classes go by capture line order, not by timestamp, because D-HP stamps to 10 ms. R0 checks the classifier against round 1's capture.
- **T1 is now the main trigger.** T1 sends the verb 1 s after the launch. It landed 390 ms after the Found line in round 1, which is inside the window and before the sweep takes the socket, so it tests the adopt path. T2 sends the verb on the Auto-connecting line, after the sweep took the socket, so it tests the join path. Cycles 1, 2, 4, 5, 7, 8 and 10 are T1. Cycles 3, 6 and 9 are T2.
- **Every run captures D-POCO's log too**, through one shared function pair, `pcap_start` and `pcap_stop` in `ptr_lib.sh`. `hu_open` starts it for each R2 segment and `run_end` stops it. `r4open` and `r4close` do the same for R4-C, and the R4L-C block calls it around `hp_open`. Each cycle function writes host wall-clock stamps (`stamp`) beside its markers, and `ptr_phone.py` cuts the phone capture by them. In R2, every cycle window must hold no `Critical error` from Gearhead: Home and screen off do not end a session, so a critical error in a cycle is not ours to cause.
- **R4-C and R4L-C also count our connections on the phone.** Android Auto's head unit server logs `Head unit connected` once for each connection it accepts. So the phone side counts our connections directly. An IN_WINDOW cycle must show exactly 1; 2 or more means we opened a second connection, even if SSL formed. The phone capture is `logcat -v epoch`, and `ptr_phone.py` cuts it into cycles by host wall-clock stamps that `race_cycle` writes beside its markers. `clockcheck` records the host, phone and D-HP clocks at each edge, so any drift is on record. The four phone lines are matched by message text, not by tag.
- **An EARLY cycle is outside the fix.** If the verb lands before the Found line, the USER dial is the only connection unless the sweep is already mid-probe. A wedge in that case is a different mechanism. The FAIL rule on `ssl` 0 still applies to it, and the report must name the class.
- **`race_count` overstates the baseline in SURFACE runs.** A SurfaceView hands back the same `Surface` object on a return. The baseline then prints no `New surface set: `, so a race window stays open and later normal rebuilds count in it. `ptr_crash.py` now also counts `early_init`: a `Codec initialized:` after a cycle's first dying-surface stop and before the next `New surface set: ` or `AapProjectionActivity: onResume`. The baseline's evidence is its crash lines and `early_init_cycles`, not its `race_count`. On the candidate, `race_count` and `early_init_cycles` must both be 0.
- **Round 1's script fixes are in the new `ptr_lib.sh`**, marked FIX1 to FIX5: `th_pkg` takes the first match; the hotspot preflight ignores `HotspotManager: Setting hotspot enabled=false`; `hu_open` sets `RUN` before `apk_check`; `cap_stop` kills the logcat children before the loop subshell; `cap_stop` strips the CR from every capture line (D-HP writes CRLF).
- **D-HP's serial is `CNU350BGBJ`**, as the template says and round 1 read. Read it from `adb devices` and use what it prints.
- **R4-C stops at the first cycle with no session and does not recover the server.** R4L-C runs last, because a redial to a live session can wedge the server too.
- **The rig's audio settings are a deliberate worst case.** Do not change `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and record them. Round 1 read D-HU at `false`, `8`, `20`, `false`.
- **D-HU hard-reboots under sustained multi-core load.** No run adds load. If `adb devices` loses D-HU, stop the round (rig broken).
- **Never run adb calls in parallel against one unit.**
- **Pre-registered outcomes that are not failures:**
  - R2-C is INCONCLUSIVE if fewer than 8 Home cycles log a dying-surface stop.
  - R2's preflight fails if the phone asks for H.264 (`Media Sink Setup Request: 3 on channel VIDEO` with no `7`). That is a setup failure.
  - R4-C is INCONCLUSIVE if fewer than 3 cycles land IN_WINDOW in 10, with every cycle forming a session.
  - R4-C and R4L-C are UNTESTABLE if `listening` fails at the start of stage 2.
  - R2-B and R2-C are UNTESTABLE if `Navegadortz2` is not in D-POCO's bonds after the pre-flight answer.

### Stages and USB ports

| Stage | Plugged in | Runs |
|---|---|---|
| 1 | D-HU, D-POCO | R2-B, then the candidate install, then R2-C |
| 2 | D-HP, D-POCO (D-HU may stay plugged in) | candidate install, R4-C, R4L-C (last) |

Before stage 2, release D-POCO from D-HU, because D-HU holds one hands-free link at a time. Then put D-POCO's WiFi on the house LAN:

```bash
HU=27870808938846; send ACTION_EXIT; sleep 3; adb -s $HU shell am force-stop $PKG
adb -s $HU shell svc bluetooth disable; adb -s $HU shell svc wifi disable      # restore both with enable at the end
adb -s $PH shell svc wifi enable; sleep 5
adb -s $PH shell dumpsys wifi | grep -a -m1 "Wi-Fi is"                          # must read enabled
```

## 4. Settings keys

Write the keys with the app stopped, through `hu_put` (D-HU) or `tab_put` (D-HP). Read every key back before the launch. **Back up each unit's `settings.xml` at the start (`settings-backup-HU.xml`, `settings-backup-HP.xml`).** Diff each against round 1's backup in `../projection-teardown-and-relays-round1/` and state the delta in Setup notes.

| Key | Type | Value | Notes |
|---|---|---|---|
| `view-mode` | int | `0` (SURFACE) | both units |
| `video-codec` | string | `H.265` | D-HU only. Compared as a literal string. D-HP: leave as found and record it |
| `resolutionId` | int | `3` | D-HU only. 1080p |
| `force-software-decoding` | boolean | `false` | D-HU only |
| `software-video-decoder` | int | delete | D-HU only |
| `debug-video-fault-injection`, `debug-video-fault-rate`, `debug-video-fault-budget` | int | delete | D-HU only |
| `video-profile-starvation-cap` | boolean | delete | both units. A latch that forces 720p30 and AAC after three fumbled sessions |
| `wifi-connection-mode` | int | `3` on D-HU, `1` on D-HP | |
| `log-level` | int | `2` | INFO. Every decisive line in section 6 is INFO or WARN |
| `night-mode` | int | `1` | D-HU only. DAY |
| `native-driver-selection-mode` | int | `0` | D-HU only. Stack armed, no selector countdown |
| `onboarding-version` | int | `2` | both units |
| `connection-modes` | string set | `wifi` | D-HP: write it. D-HU: read and record it; write it only if it lacks `wifi` |

`ACTION_LOG_MARKER` is not gated on either build (`AutomationCommandPolicy.CONFIGURING` does not list it), so `allow-external-configuration` is not needed.

```bash
HUBASE="int:resolutionId=3 bool:force-software-decoding=false del:software-video-decoder del:debug-video-fault-injection del:debug-video-fault-rate del:debug-video-fault-budget del:video-profile-starvation-cap int:wifi-connection-mode=3 int:log-level=2 int:night-mode=1 int:native-driver-selection-mode=0 int:onboarding-version=2"
R2KEYS="str:video-codec=H.265 int:view-mode=0 $HUBASE"
HPKEYS="int:wifi-connection-mode=1 int:log-level=2 int:view-mode=0 int:onboarding-version=2 set:connection-modes=wifi del:video-profile-starvation-cap"
```

## 5. Every action as a verb, and the helpers

Every action on the app is a `send ...` verb (template section 3), with two named exceptions below.

| Step | Command | Note |
|---|---|---|
| marker | `send ACTION_LOG_MARKER --es text <label>` (helper `mark`) | prints `AutomationMarker: <label>` at WARN |
| identity | `send ACTION_QUERY_STATE` | record the reply's `commit` |
| connect to the phone's server (D-HP) | `send ACTION_CONNECT --es ip $PHIP` | TCP to `:5277`, USER tier. The verb under test in R4 and R4L |
| exit between runs and cycles | `send ACTION_EXIT`, `sleep 3`, then `am force-stop` | stops the service and ends the session cleanly |

**Two named non-verb steps, with the reason:**

- **`back`, the return to the projection** (`am start -n $PKG/com.andrerinas.openheadunit.aap.AapProjectionActivity -f 0x10020000 --ez focus true`). The raise verb is inert on the baseline, so an A/B return cannot use it. It is the same intent the service builds for a raise.
- **`am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`**, the app launch (template section 3). It is never a return.

OS-level steps, allowed: `input keyevent 3` (HOME), `223` (SLEEP), `224` (WAKEUP), `wm dismiss-keyguard`, `dumpsys activity activities`, `svc wifi|bluetooth enable|disable`, `dumpsys wifip2p`, `am force-stop com.google.android.projection.gearhead` (pre-flight only).

**No hand step runs after the pre-flight request.**

### Scripts

Make the folder `hur-wifi-test-scripts/projection-teardown-and-relays-round2/`. Copy `ohu_lib.sh` and `ohu_setkeys.py` into it from `../projection-teardown-and-relays-round1/`, as the round 1 tester left them. Then save the three files below beside them. They replace round 1's `ptr_lib.sh`, `ptr_crash.py` and `ptr_race.py`; do not copy those. List every file in Setup notes. If a script does not match the real line format, fix it, say so in Setup notes, and keep going.

Source order: `ohu_lib.sh`, then `ptr_lib.sh`. `ptr_lib.sh` uses `PKG`, `send`, `mark`, `nl`, `waitfor`, `PIC`, `hu_put`, `tab_put` and `run_open` from `ohu_lib.sh`.

**`ptr_lib.sh`**:

```bash
# ptr_lib.sh, round 2 : source it AFTER ohu_lib.sh (the copy in projection-teardown-and-relays-round1/).
# Needs HU, PH, OUT, PUT, BASEXML. Carries the five round 1 tester fixes (marked FIX1 to FIX5).
PROJ=$PKG/com.andrerinas.openheadunit.aap.AapProjectionActivity
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
# back : the return command, the same intent AapService builds for a raise (NEW_TASK | REORDER_TO_FRONT, focus=true)
back() { adb -s "$HU" shell am start -n $PROJ -f 0x10020000 --ez focus true >/dev/null 2>&1; }
top() { adb -s "$HU" shell dumpsys activity activities | grep -aE 'topResumedActivity|mResumedActivity' | head -2; }
# fastwait <secs> <fixed-string> <from-line> : 0 when the string appears at or after <from-line>; polls every 0.1 s
fastwait() { local end=$((SECONDS+$1)); while [ $SECONDS -lt $end ]; do
  tail -n +"$3" "$CAP" | grep -aqF -- "$2" && return 0; sleep 0.1; done; return 1; }
# seg <from-marker> <to-marker> : capture lines between two markers of this run
seg() { sed -n "/AutomationMarker: $1\$/,/AutomationMarker: $2\$/p" "$CAP"; }
crashes() { echo $(( $(grep -ac 'Fatal signal' "$CAP") + $(grep -ac 'ABORTING' "$CAP") + $(grep -a 'Process com.andrerinas.headunitrevived (pid' "$CAP" | grep -ac 'has died') )); }
# Host thermal. FIX1: th_pkg takes the first ' pkg=' match only (rig_thermal.sh also prints throttle_pkg=).
th_pkg() { if [ -x ../rig_thermal.sh ]; then ../rig_thermal.sh status | grep -o ' pkg=[0-9]*' | head -1 | cut -d= -f2; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
th_gate() { th_wait 75 || { echo "$(date +%T) HOST_TOO_HOT ${RUN:-run}" | tee -a "$OUT/thermal.log"; return 3; }
  [ -n "$THPID" ] && kill $THPID 2>/dev/null; th_watch "$OUT/${RUN:-run}.thermal" & THPID=$!
  echo "$(date +%T) ${RUN:-run} start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"; }
th_report() { awk -F'[ =C]+' '{p=$3; t=$5; if(p>m)m=p; if(NR==1)t0=t} END{print "thermal_max=" m "C throttle_delta=" t-t0}' "$OUT/$1.thermal"; }
# apk_check : thermal gate, then pull the installed APK and compare its md5 with $WANT_MD5
apk_check() { th_gate || return 3; local m; adb -s "$HU" pull $(adb -s "$HU" shell pm path $PKG | cut -d: -f2 | tr -d '\r') "$OUT/live.apk" >/dev/null 2>&1
  m=$(md5sum "$OUT/live.apk" | cut -d' ' -f1); echo "$(date +%T) $HU live=$m want=$WANT_MD5" | tee -a "$OUT/apk-check.log"
  [ "$m" = "$WANT_MD5" ] || { echo "APK_MISMATCH $HU" | tee -a "$OUT/apk-check.log"; return 1; }; }
# cap_start / cap_stop : one capture per run segment. API 21 and up: full capture after logcat -G 16M.
# Below API 21 (D-HP): a tag-filtered capture that restarts itself when adb drops it.
TAGS="OPENHU:V ActivityManager:I libc:V DEBUG:V AndroidRuntime:V *:S"
cap_start() { local api; api=$(adb -s "$HU" shell getprop ro.build.version.sdk | tr -d '\r'); adb -s "$HU" logcat -c; : > "$CAP"
  if [ "$api" -ge 21 ]; then adb -s "$HU" logcat -G 16M; stdbuf -oL adb -s "$HU" logcat -v time > "$CAP" & CAPPID=$!; CAPLOOP=
  else ( while :; do stdbuf -oL adb -s "$HU" logcat -v time $TAGS >> "$CAP"; echo "$(date +%T)" >> "$CAP.restarts"; sleep 1; done ) & CAPLOOP=$!; CAPPID=$CAPLOOP; fi; }
# FIX4: kill the loop's logcat children BEFORE the loop subshell. FIX5: strip the CR that D-HP's logcat puts on every line.
cap_stop() { if [ -n "$CAPLOOP" ]; then pkill -P $CAPLOOP 2>/dev/null; kill $CAPLOOP 2>/dev/null; wait $CAPLOOP 2>/dev/null
    awk '!seen[$0]++' "$CAP" > "$CAP.tmp" && mv "$CAP.tmp" "$CAP"
  else kill $CAPPID 2>/dev/null; wait $CAPPID 2>/dev/null; fi
  tr -d '\r' < "$CAP" > "$CAP.tmp" && mv "$CAP.tmp" "$CAP"
  ps aux | grep -c "[l]ogcat"; }                    # must print 0; else kill the leftover by its pid
# Phone-side capture on D-POCO, one function for every run (R2, R4-C, R4L-C, the pre-flight). Host wall clock aligns it (see ptr_phone.py).
# stamp <window-label> start|end : writes the host epoch ms of a window edge to $OUT/$RUN.windows.tsv
stamp() { local now; now=$(date +%s%3N); if [ "$2" = start ]; then printf '%s %s' "$1" "$now" >> "$OUT/$RUN.windows.tsv"
  else printf ' %s\n' "$now" >> "$OUT/$RUN.windows.tsv"; fi; }
# clockcheck <label> : host, phone and D-HP clocks in epoch seconds, appended to $OUT/clocks.log
clockcheck() { echo "$1 host=$(date +%s) phone=$(adb -s "$PH" shell date +%s | tr -d '\r') unit=$(adb -s "$HU" shell date +%s | tr -d '\r')" | tee -a "$OUT/clocks.log"; }
pcap_start() { PCAP=$OUT/$1.phone.logcat; adb -s "$PH" logcat -c; adb -s "$PH" logcat -G 16M >/dev/null 2>&1
  stdbuf -oL adb -s "$PH" logcat -v epoch > "$PCAP" & PCAPPID=$!; }
# pcap_stop : idempotent; a second call does nothing
pcap_stop() { [ -n "$PCAPPID" ] || return 0; kill $PCAPPID 2>/dev/null; wait $PCAPPID 2>/dev/null; PCAPPID=
  tr -d '\r' < "$PCAP" > "$PCAP.tmp" && mv "$PCAP.tmp" "$PCAP"; echo "phone_lines=$(grep -ac . "$PCAP")"; }
# hu_open <RUN> <spec...> : D-HU bring-up. FIX3: RUN is set before apk_check, so the thermal file has this run's name.
# The phone capture starts before the bring-up and run_end stops it.
hu_open() { RUN=$1; apk_check || return 1; : > "$OUT/$RUN.windows.tsv"; clockcheck "$RUN-start"; pcap_start "$RUN"
  CAPLOOP=; run_open "$@" || { pcap_stop; return 1; }; preflight "$1" || { run_end "$1"; return 2; }; }
# listening : the phone's head unit server listens on 5277 (0x149D, state 0A), IPv4 or IPv6
listening() { adb -s "$PH" shell cat /proc/net/tcp /proc/net/tcp6 | grep -aqiE ':149D [0-9A-F]+:0000 0A'; }
# prefs_cat : the unit's settings.xml (root read on D-HU, run-as on D-HP)
prefs_cat() { if [ "$HU" = 27870808938846 ]; then adb -s "$HU" shell cat /data/data/$PKG/shared_prefs/settings.xml
  else adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml; fi; }
# preflight <RUN> : proves the path under test is active. Set PF_MODE (NATIVE|AUTO), PF_VIEW, PF_SINK (3|7) first.
preflight() { local f=$OUT/$1.preflight ok=1 c x; : > "$f"
  c=$(grep -acF "WifiLauncher: Initializing WiFi Mode: $PF_MODE" "$CAP"); echo "mode=$PF_MODE lines=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  c=$(grep -acF "Projection backend: viewMode=$PF_VIEW " "$CAP"); echo "viewMode=$PF_VIEW lines=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  c=$(grep -acF "Media Sink Setup Request: $PF_SINK on channel VIDEO" "$CAP"); echo "sink=$PF_SINK lines=$c" >> "$f"; [ "$c" -ge 1 ] || ok=0
  x=$(grep -aF 'Codec initialized: ' "$CAP" | tail -n 1 | sed 's/.*Codec initialized: //' | tr -d '\r'); echo "codec=$x" >> "$f"
  if [ -z "$x" ] || echo "$x" | grep -qiE '^(omx\.google\.|c2\.android\.|omx\.ffmpeg\.)|\.sw\.|software'; then ok=0; fi
  if [ "$PF_MODE" = NATIVE ]; then
    # FIX2: the app switching a hotspot OFF at Native AA start is not the hotspot path
    c=$(grep -aE 'HotspotManager:|SoftApCredentials:' "$CAP" | grep -avF 'Setting hotspot enabled=false' | grep -ac .); echo "hotspot_lines=$c" >> "$f"; [ "$c" -eq 0 ] || ok=0
    x=$(adb -s "$HU" shell dumpsys wifip2p | grep -a -m1 groupFormed | tr -d '\r'); echo "p2p:$x" >> "$f"; echo "$x" | grep -q true || ok=0; fi
  prefs_cat > "$OUT/$1.prefs.xml"
  python3 -I -c 'import sys,xml.etree.ElementTree as E
r=E.parse(sys.argv[1]).getroot(); s=[e for e in r if e.get("name")=="connection-modes"]
v=[x.text for x in s[0]] if s else []; c=[e for e in r if e.get("name")=="video-profile-starvation-cap"]
print("connection-modes=%s starvation-cap-keys=%d" % (",".join(v), len(c))); sys.exit(0 if "wifi" in v and not c else 1)' "$OUT/$1.prefs.xml" >> "$f" || ok=0
  cat "$f"
  if [ $ok = 1 ]; then mark "$1-preflight-ok"; return 0; else mark "$1-preflight-FAIL"; echo "PREFLIGHT_FAIL $1"; return 2; fi; }
# cyc_home <RUN> <n> : Home, 3 s, the return command, a picture or 20 s
cyc_home() { local G L; G=$(nl); mark "$1-r$2-H-go"; stamp "r$2-H" start; adb -s "$HU" shell input keyevent 3; sleep 3
  L=$(nl); back; waitfor 60 'AapProjectionActivity: onResume' "$L" || echo "NO_RESUME $1-r$2"
  waitfor 20 "$PIC" "$G" || true; sleep 3; stamp "r$2-H" end; }
# cyc_off <RUN> <n> : screen off 3 s, wake, a picture or 20 s
cyc_off() { local G L; G=$(nl); mark "$1-r$2-S-go"; stamp "r$2-S" start; adb -s "$HU" shell input keyevent 223; sleep 3
  L=$(nl); adb -s "$HU" shell input keyevent 224; sleep 2
  if ! waitfor 6 'AapProjectionActivity: onResume' "$L"; then adb -s "$HU" shell wm dismiss-keyguard; back
    waitfor 30 'AapProjectionActivity: onResume' "$L" || echo "NO_RESUME $1-r$2"; fi
  waitfor 20 "$PIC" "$G" || true; sleep 3; stamp "r$2-S" end; }
# run_end <RUN> : end marker, stop both captures, the crash and phone extracts, leave the unit clean
# The phone capture stops first, so cap_stop's logcat count can read 0.
run_end() { mark "$1-end"; sleep 1
  if [ -n "$PCAPPID" ]; then pcap_stop; clockcheck "$1-end"
    python3 -I ptr_phone.py "$PCAP" "$OUT/$1.windows.tsv" > "$OUT/$1.phone.json"; fi
  cap_stop
  tail -n 1 "$CAP" | cut -c1-40; adb -s "$HU" shell date
  python3 -I ptr_crash.py "$CAP" "$1" > "$OUT/$1.crash.json"
  send ACTION_EXIT >/dev/null; sleep 3; adb -s "$HU" shell am force-stop $PKG; }
# crashloop <RUN> <total> <open-fn> <spec...> : alternate the cycle functions in $CYC until <total> cycles
# or 3 crash segments. A crash ends the segment; the next segment opens a new session and a new capture.
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
# hp_open <RUN> <spec...> : D-HP bring-up in Headunit Server mode. Waits 45 s for the app's own auto-connect;
# ACTION_CONNECT is only the fallback.
hp_open() { RUN=$1; shift; CAP=$OUT/$RUN.logcat; apk_check || return 1
  adb -s "$HU" shell am force-stop $PKG; cap_start; sleep 1
  $PUT "$BASEXML" "$@"
  local L; L=$(nl); adb -s "$HU" shell am start -n $MAIN >/dev/null
  waitfor 45 'SSL handshake complete' "$L" || { mark "$RUN-fallback-connect"; send ACTION_CONNECT --es ip "$PHIP" >/dev/null
    waitfor 60 'SSL handshake complete' "$L" || { echo "SESSION_FAIL_SSL $RUN"; return 1; }; }
  waitfor 40 'AapProjectionActivity: onResume' "$L" || { back; waitfor 20 'AapProjectionActivity: onResume' "$L" || { echo "SESSION_FAIL_RAISE $RUN"; return 1; }; }
  waitfor 60 'Throughput over [0-9]+ms: rendered=[1-9]' "$L" || { echo "SESSION_FAIL_NO_FRAMES $RUN"; return 1; }
  sleep 20; mark "$RUN-start"; preflight "$RUN" || { run_end "$RUN"; return 2; }; }
# race_cycle <RUN> <n> <T1|T2> : one launch, the verb by its trigger, 60 s for a session, a clean exit.
# T1: 1 s after the launch (lands after the Found line on D-HP, round 1). T2: on the Auto-connecting line.
race_cycle() { local L; L=$(nl); mark "$1-c$2-$3-go"; stamp "c$2-$3" start
  adb -s "$HU" shell am start -n $MAIN >/dev/null
  if [ "$3" = T1 ]; then sleep 1; send ACTION_CONNECT --es ip "$PHIP" >/dev/null
  elif fastwait 20 'Auto-connecting to Headunit Server at' "$L"; then send ACTION_CONNECT --es ip "$PHIP" >/dev/null
  else mark "$1-c$2-noauto"; fi
  waitfor 60 'SSL handshake complete' "$L" && waitfor 30 'Throughput over [0-9]+ms: rendered=[1-9]' "$L"
  sleep 5; mark "$1-c$2-end"; stamp "c$2-$3" end; send ACTION_EXIT >/dev/null; sleep 3; adb -s "$HU" shell am force-stop $PKG; sleep 10; }
# race_json <RUN> : the classifier on the live capture, with the CR stripped (the capture is still open)
race_json() { tr -d '\r' < "$CAP" > "$CAP.now"; python3 -I ptr_race.py "$CAP.now" "$1" "$PHIP"; }
# inwin <RUN> : IN_WINDOW cycles with same_endpoint true so far
inwin() { race_json "$1" | python3 -c 'import json,sys; print(sum(1 for c in json.load(sys.stdin)["cycles"] if c["class"]=="IN_WINDOW" and c["same_endpoint"]))'; }
# last_race <RUN> : class, ssl and route of the last cycle so far
last_race() { race_json "$1" | python3 -c 'import json,sys; c=json.load(sys.stdin)["cycles"][-1]; print(c["class"], c["ssl"], c["route"])'; }
```

**`ptr_crash.py`** (round 1's, plus `early_init` and the CR strip):

```python
#!/usr/bin/env python3
"""ptr_crash.py <capture> <RUN> : crashes, teardown-race windows and per-cycle teardown proof, as JSON. Round 2.

A race window opens at a decoder stop for a dying surface and closes at the next 'New surface set: ',
a crash, or '<RUN>-end'. A 'Codec initialized:' inside a window counts in race_count.
early_init counts a 'Codec initialized:' after a cycle's first dying-surface stop and before the
first 'New surface set: ' or 'AapProjectionActivity: onResume' after it: a codec built on the
dying surface before the return. It does not depend on 'New surface set: ', which the baseline
does not print when a SurfaceView hands back the same Surface object.
"""
import json, re, sys

cap, run = sys.argv[1], sys.argv[2]
PKG = 'com.andrerinas.headunitrevived'
OPEN = ('Decoder stopped: surfaceDestroyed', 'Decoder stopped: onDetachedFromWindow',
        'Decoder stopped: projectionViewRecreate')
lines = [raw.decode('utf-8', 'replace').rstrip('\r\n') for raw in open(cap, 'rb')]
out = {'run': run, 'fatal': [], 'aborting': [], 'died': [], 'race_windows': [], 'cycles': {}}
win, cyc, closed, early = None, None, 0, None

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
                              'codec_init': 0, 'resume': 0, 'early_init': 0, 'early_init_lines': []}
        early = None
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
        if c:
            c['detach_stops'] += 1
            if early is None:
                early = 'open'
        if win is None:
            win = {'opened_at': n, 'opened_by': l.split('Decoder stopped: ')[-1].strip(),
                   'codec_init': 0, 'codec_init_lines': [], 'stops_inside': 0}
        else:
            win['stops_inside'] += 1
    if 'Decoder stopped: activity_stopped' in l and c:
        c['activity_stopped'] += 1
    if 'Codec initialized:' in l:
        if c:
            c['codec_init'] += 1
            if early == 'open':
                c['early_init'] += 1; c['early_init_lines'].append(n)
        if win is not None:
            win['codec_init'] += 1; win['codec_init_lines'].append(n)
    if 'New surface set: ' in l:
        if c: c['new_surface'] += 1
        if early == 'open': early = 'closed'
        close(n, 'new_surface')
    if 'AapProjectionActivity: onResume' in l:
        if c: c['resume'] += 1
        if early == 'open': early = 'closed'
close(len(lines), 'eof')
cyc_list = list(out['cycles'].values())
out['summary'] = {
    'cycles': len(cyc_list),
    'cycles_with_teardown': sum(1 for c in cyc_list if c['detach_stops'] or c['activity_stopped']),
    'home_cycles': sum(1 for k in out['cycles'] if k.endswith('H')),
    'home_cycles_with_detach_stop': sum(1 for k, c in out['cycles'].items() if k.endswith('H') and c['detach_stops']),
    'off_cycles': sum(1 for k in out['cycles'] if k.endswith('S')),
    'off_cycles_with_activity_stop': sum(1 for k, c in out['cycles'].items() if k.endswith('S') and c['activity_stopped']),
    'detach_stops_total': sum(c['detach_stops'] for c in cyc_list),
    'cycles_without_resume': sum(1 for c in cyc_list if not c['resume']),
    'early_init_cycles': sum(1 for c in cyc_list if c['early_init']),
    'windows_closed': closed,
    'race_count': len(out['race_windows']),
    'fatal_count': len(out['fatal']), 'aborting_count': len(out['aborting']), 'died_count': len(out['died'])}
for k in ['Codec exception in output thread', 'Decoder restart requested: ', 'SSL handshake complete',
          'skipped: surface is no longer current', 'Media Sink Setup Request: 3 on channel VIDEO',
          'Media Sink Setup Request: 7 on channel VIDEO']:
    out['summary'][k] = sum(1 for l in lines if k in l)
print(json.dumps(out, indent=1))
```

**`ptr_race.py`** (rewritten: the window opens at the Found line, and each cycle counts the adopt, join, refusal and SSL lines):

```python
#!/usr/bin/env python3
"""ptr_race.py <capture> <RUN> <phone-ip> : one row per connect-race cycle, as JSON. Round 2.

A cycle opens at '<RUN>-c<N>-<T1|T2>-go' and closes at '<RUN>-c<N>-end' (or the next go marker).
The in-flight window opens at the first 'NetworkDiscovery: Found Headunit Server on' of the cycle,
because that line is where discovery holds a socket the phone's server has already accepted.
Class, by capture line order: NO_VERB (no verb line); EARLY (no Found line, or the verb before it);
AMBIGUOUS (verb within 100 ms of SSL); IN_WINDOW (verb after Found and before SSL, or no SSL); LATE.
"""
import json, re, sys

cap, run, phip = sys.argv[1], sys.argv[2], sys.argv[3]
TS = re.compile(r'^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3})')
rows = []
with open(cap, 'rb') as f:
    for n, raw in enumerate(f, 1):
        line = raw.decode('utf-8', 'replace').rstrip('\r\n')
        m = TS.match(line)
        if m:
            mo, d, h, mi, s, ms = map(int, m.groups())
            rows.append(((((d * 24 + h) * 60 + mi) * 60 + s) * 1000 + ms, n, line))
GO = re.compile(r'AutomationMarker: ' + re.escape(run) + r'-c(\d+)-(T1|T2)-go\s*$')
gos = [(t, n, int(m.group(1)), m.group(2)) for t, n, l in rows for m in [GO.search(l)] if m]
FOUND = re.compile(r'NetworkDiscovery: Found Headunit Server on (\S+):5277')
TP = re.compile(r'Throughput over \d+ms: rendered=([1-9][0-9]*)')
VERB = 'AutomationReceiver: com.andrerinas.openheadunit.ACTION_CONNECT'
REFUSED = re.compile(r'ConnectionArbiter: .* \(\w+\) refused while .* is in flight')
out = []
for i, (t0, n0, k, trig) in enumerate(gos):
    endre = re.compile(r'AutomationMarker: ' + re.escape(run) + r'-c%d-end\s*$' % k)
    endm = [n for t, n, l in rows if n > n0 and endre.search(l)]
    n1 = endm[0] if endm else (gos[i + 1][1] if i + 1 < len(gos) else rows[-1][1])
    w = [(t, n, l) for t, n, l in rows if n0 <= n <= n1]
    def first(pred):
        return next(((t, n, l) for t, n, l in w if pred(l)), None)
    f = first(lambda l: FOUND.search(l) is not None)
    v = first(lambda l: l.rstrip().endswith(VERB))
    s = first(lambda l: 'SSL handshake complete' in l)
    tpic = next((t for t, n, l in w if TP.search(l)), None)
    found_ip = FOUND.search(f[2]).group(1) if f else None
    if v is None:
        cls = 'NO_VERB'
    elif f is None or v[1] < f[1]:
        cls = 'EARLY'
    elif s is not None and abs(v[0] - s[0]) <= 100:
        cls = 'AMBIGUOUS'
    elif s is None or v[1] < s[1]:
        cls = 'IN_WINDOW'
    else:
        cls = 'LATE'
    c = lambda sub: sum(1 for t, n, l in w if sub in l)
    adopt = c('adopting the socket discovery already opened')
    join = c('is already connecting; not preempting it')
    out.append({'cycle': k, 'trigger': trig, 'class': cls, 'found_ip': found_ip,
                'same_endpoint': (found_ip == phip) if found_ip else None,
                'verb_after_found_ms': (v[0] - f[0]) if (v and f) else None,
                'ssl_after_go_s': round((s[0] - t0) / 1000, 2) if s else None,
                'picture_after_go_s': round((tpic - t0) / 1000, 2) if tpic else None,
                'route': 'ADOPT' if adopt else ('JOIN' if join else 'NONE'),
                'adopt': adopt, 'join': join,
                'not_held': c('is no longer held for discovery; not dialling it'),
                'auto_connect': c('Auto-connecting to Headunit Server at'),
                'refused': sum(1 for t, n, l in w if REFUSED.search(l)),
                'refused_bringup': c('wireless bring-up refused while'),
                'preempt_socket': c('preempts the socket from'),
                'stood_down': c('stood the wireless stack down'),
                'peer_silent': c('the peer accepted the connection and then sent nothing'),
                'session_connecting': c('AapService: session state connecting'),
                'ssl': c('SSL handshake complete'),
                'fatal': c('Fatal signal')})
print(json.dumps({'run': run, 'phone_ip': phip, 'cycles': out}, indent=1))
```

**`ptr_phone.py`** (new: the phone-side lines per window, cut by host wall-clock stamps):

```python
#!/usr/bin/env python3
"""ptr_phone.py <phone-capture> <windows.tsv> : Gearhead's head unit server lines per window, as JSON.

The phone capture is `adb -s 4f4027e9 logcat -v epoch`. windows.tsv holds one row per window:
label, host start in epoch ms, host end in epoch ms (written by stamp in ptr_lib.sh).
last_line names the latest of the four head unit server lines; critical_error and out_of_car are
counted beside it and do not take part in last_line. The phone
clock and the host clock agree to the second; ptr_lib.sh records the drift. Lines are matched by
message text, not by tag, so a tag spelling change does not hide them.
"""
import json, re, sys

cap, tsv = sys.argv[1], sys.argv[2]
TS = re.compile(r'^\s*(\d+)\.(\d{3})\s')
LINES = {'hu_connected': 'Head unit connected',
         'duplex': 'startDuplexConnection',
         'hu_disconnected': 'Head unit disconnected',
         'server_running': 'Network server running on port',
         'critical_error': 'Critical error',
         'out_of_car': 'OutOfCarLifecycle'}
rows = []
with open(cap, 'rb') as f:
    for raw in f:
        l = raw.decode('utf-8', 'replace').rstrip('\r\n')
        m = TS.match(l)
        if m:
            rows.append((int(m.group(1)) * 1000 + int(m.group(2)), l))
out = []
for w in open(tsv):
    p = w.split()
    if len(p) < 3:
        continue
    label, t0, t1 = p[0], int(p[1]), int(p[2])
    win = [(t, l) for t, l in rows if t0 <= t <= t1]
    r = {'window': label}
    last, first_lines = None, {}
    for k, s in LINES.items():
        if k in ('critical_error', 'out_of_car'):
            hits = [(t, l) for t, l in win if s in l]
            r[k] = len(hits)
            if hits:
                first_lines[k] = hits[0][1][:200]
            continue
        hits = [(t, l) for t, l in win if s in l]
        r[k] = len(hits)
        if hits:
            first_lines[k] = hits[0][1][:200]
            if last is None or hits[-1][0] > last[0]:
                last = (hits[-1][0], k)
    r['last_line'] = last[1] if last else None
    r['first_lines'] = first_lines
    out.append(r)
print(json.dumps({'capture': cap, 'windows': out}, indent=1))
```

## 6. The lines that decide every run

Every app line below was checked with `grep -F -r` against `app/src/main` at the candidate tip (`8d4656ab`, tree `4be97cfb`) and with `git grep -F` at `2ca3b1f1`. The phone lines are Gearhead's and cannot be checked in this tree: `Head unit connected` and `startDuplexConnection` were read from the Gearhead 17.5 APK, `Critical error` and `OutOfCarLifecycle` were recorded in the rotation rounds, and `Network server running on port` comes from the operator's notes. The pre-flight capture, R2-B segment 1 and cycle 1 of R4-C confirm them: quote the full phone line of each, with its tag, in Setup notes, and say where a tag differs from the table. Lines marked **candidate only** are absent on the baseline by design. Lines marked *(composed)* render from a format string; the runs grep the rendered form, and section 10 lists the source fragments. System lines come from the platform. Grep every capture with `grep -a`.

| Meaning | Line | Source | Baseline | File and window |
|---|---|---|---|---|
| return reached the activity | `AapProjectionActivity: onResume` | `AapProjectionActivity.kt` | present | R2 capture, per cycle |
| a new surface was claimed | `New surface set: ` | `VideoDecoder.kt` | present | R2, closes a race window |
| decoder stop for a dying surface *(composed)* | `Decoder stopped: surfaceDestroyed`, `Decoder stopped: onDetachedFromWindow`, `Decoder stopped: projectionViewRecreate` | `VideoDecoder.kt` + `DecoderStopPolicy.kt` | present | R2, opens a race window |
| decoder stop on `onStop` *(composed)* | `Decoder stopped: activity_stopped` | same | present | R2, screen-off cycles |
| codec built | `Codec initialized:` | `VideoDecoder.kt` | present | R2, race and `early_init` |
| output thread declared the codec dead | `Codec exception in output thread`, `Decoder restart requested: ` | `VideoDecoder.kt` | present | R2, report |
| a stale-surface stop skipped | `skipped: surface is no longer current` | `VideoDecoder.kt` | present (1 site; 2 on candidate) | R2, report |
| picture | `keyframe decoded - the picture is repaired`, `First frame rendered (hardware decode)` | `VideoDecoder.kt` | present | `PIC` in `cyc_home` and `cyc_off` |
| steady state *(composed)* | `Throughput over <N>ms: rendered=<N>` | `VideoDecoder.kt` | present | bring-up waits; R4 `picture_after_go_s` |
| phone chose a codec *(composed)* | `Media Sink Setup Request: 7 on channel VIDEO` (R2), `... 3 ...` (D-HP as found) | `AapControl.kt` | present | preflight |
| verb landed | `AutomationReceiver: com.andrerinas.openheadunit.ACTION_CONNECT` | `AutomationReceiver.kt` | present | R4 per cycle, R4L window |
| marker | `AutomationMarker: ` | `AutomationEffectRunner.kt` | present | every window |
| **the in-flight window opens** | `NetworkDiscovery: Found Headunit Server on <ip>:5277` | `NetworkDiscovery.kt` | present | R4 per cycle |
| discovery hands over its socket | `Auto-connecting to Headunit Server at <ip>:5277 (reusing socket)` | `WifiLauncherSharedServices.kt` | present | R4, T2 trigger and report |
| **the adopt, candidate only** | `CommManager: <ip>:5277 adopting the socket discovery already opened` | `CommManager.kt` | absent | R4 per cycle, R4L window |
| **the join, candidate only** | `CommManager: <ip>:5277 is already connecting; not preempting it` | `CommManager.kt` | absent | R4 per cycle, R4L window |
| **discovery stands back, candidate only** | `WifiLauncherSharedServices: <ip>:5277 is no longer held for discovery; not dialling it` | `WifiLauncherSharedServices.kt` | absent | R4, report only |
| a claim refused *(composed)* | `ConnectionArbiter: <what> (<TIER>) refused while <held> is in flight` | `ConnectionArbiter.kt` | present | R4 per cycle (`refused`) |
| the stack held down, not a claim refusal | `ConnectionArbiter: wireless bring-up refused while ` | `ConnectionArbiter.kt` | present | R4, report only (`refused_bringup`) |
| the preempt *(composed)* | `preempts the socket from` | `ConnectionArbiter.kt` + `CommManager.kt` | present | R4 per cycle |
| a USER claim stood the stack down | `stood the wireless stack down` | `ConnectionArbiter.kt` | present | R4, report only |
| a connect started *(composed)* | `AapService: session state connecting` | `AapService.kt` | present | R4 report, R4L window |
| server accepted and went silent | `the peer accepted the connection and then sent nothing` | `AapTransport.kt` | present | R4, R4L |
| session formed | `SSL handshake complete` | `AapSslContext.kt` (INFO) | present | every run |
| preflight: WiFi mode | `WifiLauncher: Initializing WiFi Mode: ` + `NATIVE` or `AUTO` | `WifiLauncherManager.kt` | present | preflight |
| preflight: view backend *(composed)* | `Projection backend: viewMode=SURFACE ` | `AapProjectionActivity.kt` | present | preflight |
| preflight: hotspot path (must be 0) | `HotspotManager:`, `SoftApCredentials:`, except `HotspotManager: Setting hotspot enabled=false` | `HotspotManager.kt`, `SoftApCredentialsProvider.kt` | present | R2 preflight |
| discard rules | `MATCH! Starting AapService`, `createGroup SUCCESS`, `Magic Garbage detected in header` | receivers, `WifiDirectManager.kt`, `AapReadSingleMessage.kt` | present | R2 |
| **system: native crash** | `Fatal signal` | libc / debuggerd | n/a | R2 |
| **system: libc abort** | `ABORTING` | libc | n/a | R2 |
| **system: our process died** | `Process com.andrerinas.headunitrevived (pid` with `has died` | ActivityManager | n/a | R2 |
| **phone: the server accepted one connection** | `Head unit connected` | Gearhead `GH.DHUService` (not `app/src`) | n/a | D-POCO capture, per cycle (`hu_connected`) |
| phone: the accept went to the car service | `startDuplexConnection` | Gearhead, `CAR.SERVICE` | n/a | D-POCO capture, per cycle (`duplex`) |
| phone: the connection ended | `Head unit disconnected` | Gearhead | n/a | D-POCO capture, per cycle |
| phone: the server is up | `Network server running on port` | Gearhead | n/a | D-POCO capture, pre-flight and per cycle |
| phone: Gearhead refused or ended the car link | `Critical error` | Gearhead, `CAR.SERVICE` | n/a | D-POCO capture, R2 per cycle (`critical_error`), graded |
| phone: the projection process ended | `OutOfCarLifecycle` | Gearhead | n/a | D-POCO capture, R2 per cycle (`out_of_car`), report only |
| **identity, in the DEX** | `adopting the socket discovery already opened`, `HeldServerSocket`, `SameEndpointConnectPolicy`, `FeedLoopPolicy` | candidate only | absent | R0 |

## 7. Runs

Run ids carry the build: `-B` baseline, `-C` candidate. **The point of the round is R4-C.**

Starting state, read and recorded in Setup notes, never assumed:

```bash
cd $OUT; HU=27870808938846; PH=4f4027e9; OTHER=; PUT=hu_put     # OUT and the sourced libraries from section 0
adb devices                                                              # D-HU, D-POCO, D-HP listed; record D-HP's serial
adb -s $HU shell date; date                                             # clock agreement to the second
adb -s $HU shell stat -c %U:%a /data/data/$PKG/shared_prefs              # record
adb -s $HU shell stat -c %U:%G /data/data/$PKG/shared_prefs/settings.xml | tr -d '\r' > $OUT/settings-owner-HU.txt   # round 1 read u0_a176:771
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > $OUT/settings-backup-HU.xml; BASEXML=$OUT/settings-backup-HU.xml
grep -aoE '(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink|connection-modes)[^/]*' $BASEXML    # record; do not change
adb -s $PH shell dumpsys window | grep -a mCurrentFocus                  # D-POCO idle or at home; if Settings, input keyevent KEYCODE_HOME
adb -s $HU shell dumpsys bluetooth_manager | grep -a -m3 -iE 'enabled|state'   # D-HU Bluetooth on; round 1 re-enabled it
```

Every capture is `logcat -v time` with `stdbuf -oL`, started before the launch (the helpers do this). At the end of every run segment, `ps aux | grep -c "[l]ogcat"` must print 0, and the last capture line must be within 5 s of the unit's clock. If either check fails, that segment is INCONCLUSIVE.

### R2. H.265 SURFACE crash cycling on D-HU, both builds

Stage 1. Install the baseline first, set `WANT_MD5` to its md5, run R2-B. Then install the candidate, set `WANT_MD5` again, send `ACTION_QUERY_STATE`, and run R2-C.

```bash
adb -s $HU shell am force-stop $PKG; adb -s $HU install -r -d base.apk; WANT_MD5=$(md5sum base.apk | cut -d' ' -f1)
CYC="cyc_home cyc_off"; PF_MODE=NATIVE PF_VIEW=SURFACE PF_SINK=7
crashloop R2-B 20 hu_open $R2KEYS; th_report R2-B-s1
adb -s $HU shell am force-stop $PKG; adb -s $HU install -r -d cand.apk; WANT_MD5=$(md5sum cand.apk | cut -d' ' -f1); send ACTION_QUERY_STATE
crashloop R2-C 20 hu_open $R2KEYS; th_report R2-C-s1
for f in $OUT/R2-B-s*.crash.json $OUT/R2-C-s*.crash.json; do echo "$f $(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["summary"])' $f)"; done
for f in $OUT/R2-B-s*.phone.json $OUT/R2-C-s*.phone.json; do echo "$f"; python3 -c 'import json,sys; [print(" ", w["window"], "critical_error=%d out_of_car=%d" % (w["critical_error"], w["out_of_car"])) for w in json.load(open(sys.argv[1]))["windows"]]' $f; done
grep -a -E 'CAR\.SERVICE|GH\.WIRELESS' $OUT/R2-B-s1.phone.logcat | head -n 15     # quote in Setup notes: Gearhead's session start lines
grep -a -E 'CAR\.SERVICE|GH\.WIRELESS' $OUT/R2-B-s1.phone.logcat | tail -n 15     # quote in Setup notes: its session end lines, after run_end's exit
```

`pcap_stop` prints `phone_lines=<N>` for each segment. **If a segment's phone capture is empty (`phone_lines=0`), that segment is a setup failure:** re-run it once, and never grade the empty one. On R2-B segment 1, quote the full `Critical error` line and its tag if one appears, so the grader sees the spelling this Gearhead build uses.

20 cycles alternate a Home cycle and a screen-off cycle, so 10 of each. **Stop rule:** 20 cycles, or 3 crash segments, whichever comes first. Sum each `summary` field over every `R2-<build>-s<N>.crash.json`.

**Discard rule:** discard a segment and re-run it once if it holds a second `SSL handshake complete` with no crash line before it, `MATCH! Starting AapService`, a second `createGroup SUCCESS`, or `Magic Garbage detected in header`. A second `SSL handshake complete` after a crash is not a discard: the crash re-forms the session. A second discard makes the run INCONCLUSIVE.

**PASS (R2-C),** all of these:

- `fatal_count`, `aborting_count` and `died_count` are 0 in every segment;
- `race_count` is 0 in every segment;
- `early_init_cycles` is 0 in every segment;
- `critical_error` is 0 in every cycle window of every `R2-C-s<N>.phone.json`, except a cycle in which our process crashed (that cycle is already a FAIL);
- 20 cycles ran;
- `home_cycles_with_detach_stop` is at least 8 and `off_cycles_with_activity_stop` is at least 8.

**FAIL:** any crash line in our process, a `race_count` above 0, an `early_init_cycles` above 0, or a `critical_error` of 1 or more in a cycle window with no crash of ours. Quote each phone `Critical error` line with its device time. A crash in another process does not count; quote it. **INCONCLUSIVE:** fewer than 8 Home cycles with a detach stop.

**R2-B is the baseline arm.** Report its numbers; it has no verdict on the branch. **The mechanism was reached** if R2-B shows a crash line in our process, or `early_init_cycles` of 1 or more. Do not use R2-B's `race_count` for this (section 3). If R2-B shows neither, the R2-C PASS is a regression guard only. Say so.

**Report both builds side by side:** cycles run; the three crash counts, with each crash line and its thread name quoted; `race_count` with the line numbers of each window; `early_init_cycles` with each cycle's `early_init_lines`; `home_cycles_with_detach_stop` and `off_cycles_with_activity_stop`; the counts of `Codec exception in output thread` and `Decoder restart requested: `; the mean `detach_stops` per Home cycle; per cycle, `critical_error` and `out_of_car` from the phone; `clocks.log` for each segment.

### Stage change

```bash
HU=27870808938846; send ACTION_EXIT; sleep 3; adb -s $HU shell am force-stop $PKG
adb -s $HU shell svc bluetooth disable; adb -s $HU shell svc wifi disable
adb -s $PH shell svc wifi enable; sleep 5; adb -s $PH shell dumpsys wifi | grep -a -m1 "Wi-Fi is"     # enabled
HU=CNU350BGBJ; PUT=tab_put                                    # use the serial adb devices printed for D-HP
adb -s $HU shell dumpsys wifi | grep -a -m1 "Wi-Fi is"        # enabled; this unit can take 25 to 30 s after an enable
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-backup-HP.xml; BASEXML=$OUT/settings-backup-HP.xml
grep -aoE '(video-codec|resolutionId|use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)[^/]*' $BASEXML   # record
adb -s $HU shell am force-stop $PKG; adb -s $HU install -r -d cand.apk; WANT_MD5=$(md5sum cand.apk | cut -d' ' -f1); send ACTION_QUERY_STATE
listening && echo SERVER_UP_STAGE2 || echo "SERVER_DOWN_STAGE2: R4-C and R4L-C are UNTESTABLE"
PHIP=$(adb -s $PH shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2); echo PHIP=$PHIP
PF_MODE=AUTO PF_VIEW=SURFACE PF_SINK=3       # PF_SINK=7 only if the D-HP backup's video-codec is literally H.265
```

### R4-C. The connect race on D-HP, candidate only (**point of the round**)

No baseline arm. `pr-1045-framing-round1-results.md`, O2, measured the baseline: 27 failed cycles in 6 minutes. Skip R4-C and R4L-C if stage 2 printed `SERVER_DOWN_STAGE2`.

```bash
r4open() { RUN=$1; CAP=$OUT/$RUN.logcat; apk_check || return 1
  adb -s $HU shell am force-stop $PKG; : > $OUT/$RUN.windows.tsv; clockcheck $RUN-start
  pcap_start $RUN; cap_start; sleep 1; $PUT $BASEXML $HPKEYS; }
r4close() { mark $1-end; sleep 1; pcap_stop; cap_stop; clockcheck $1-end
  python3 -I ptr_race.py $CAP $1 $PHIP > $OUT/$1.race.json
  python3 -I ptr_phone.py $PCAP $OUT/$1.windows.tsv > $OUT/$1.phone.json
  listening && echo UP > $OUT/$1.listening-after || echo DOWN > $OUT/$1.listening-after; cat $OUT/$1.listening-after; }
r4open R4-C
for n in $(seq 1 10); do t=T1; [ $((n % 3)) = 0 ] && t=T2      # T2 on cycles 3, 6, 9; T1 on the rest
  race_cycle R4-C $n $t; r=$(last_race R4-C); echo "$n $t $r" | tee -a $OUT/R4-C.progress
  read cls ssl route <<< "$r"
  [ "$ssl" = 0 ] && { mark R4-C-wedged-c$n; break; }           # no session: stop, do not recover
  [ $n = 1 ] && { preflight R4-C || { echo "SETUP_FAIL R4-C"; break; }; }
  [ "$(inwin R4-C)" -ge 3 ] && break; done
inwin R4-C | tee $OUT/R4-C.inwin
r4close R4-C; th_report R4-C
```

`preflight` runs once, after cycle 1's session. A preflight FAIL stops R4-C as a setup failure: fix the settings, re-run R4-C once, and never grade the failed attempt. If cycle 1 forms no session, the preflight cannot run; grade the cycle as below and say so.

**Stop rule:** 3 `IN_WINDOW` cycles with `same_endpoint` true, or 10 cycles, or the first cycle with `ssl` 0, whichever comes first. **Do not try to recover a wedge.** It is the FAIL evidence.

`ptr_race.py` names cycle N `cycle` N; `ptr_phone.py` names the same cycle `cN-T1` or `cN-T2`. Join the two by cycle number. Grade from `R4-C.race.json`, `R4-C.phone.json` and `R4-C.listening-after`. An IN_WINDOW cycle below means `class` `IN_WINDOW` and `same_endpoint` true.

**Confirm the phone lines on cycle 1 first.** If cycle 1 formed a session (`ssl` 1 or more) and its `hu_connected` is 0, the phone prints a different accept line on this Gearhead build. Then quote the phone lines near cycle 1's Found line, drop the `hu_connected` conditions below, grade the rest, and say so in the verdict.

**PASS (R4-C),** all of these:

- at least **3** IN_WINDOW cycles;
- each IN_WINDOW cycle has `adopt` + `join` of 1 or more (`route` is `ADOPT` or `JOIN`), `refused` 0, `preempt_socket` 0, and `ssl` 1 or more;
- each IN_WINDOW cycle has `hu_connected` of exactly **1** in `R4-C.phone.json`;
- no cycle of any class has `ssl` 0;
- `R4-C.listening-after` reads `UP`.

**FAIL,** any of these:

- a cycle of any class with `ssl` 0. Name its class and trigger. Quote its Found, verb, adopt or join, refused and `sent nothing` lines with timestamps. Name the phone line in its `last_line`: of the four phone lines, it is the last to appear, so it names the stage that stalled;
- an IN_WINDOW cycle with `hu_connected` of 2 or more, even if SSL formed: we opened a second connection;
- an IN_WINDOW cycle with `refused` 1 or more, or `preempt_socket` 1 or more;
- an IN_WINDOW cycle with `route` `NONE` (the verb landed in the window and the fix did not act);
- `R4-C.listening-after` reads `DOWN`.

**INCONCLUSIVE:** fewer than 3 IN_WINDOW cycles after 10 cycles, every cycle with `ssl` 1 or more, and no FAIL condition. Cycles with `same_endpoint` false, `NO_VERB`, `EARLY`, `AMBIGUOUS` or `LATE` count toward neither PASS nor INCONCLUSIVE; report how many of each.

**If the change did nothing,** an IN_WINDOW cycle shows `route` `NONE`, `refused` 1, `peer_silent` 1, `ssl` 0 and `hu_connected` 2, as cycle 1 of round 1 would have.

**Report:** one row per cycle with `cycle`, `trigger`, `class`, `verb_after_found_ms`, `route`, `adopt`, `join`, `not_held`, `refused`, `refused_bringup`, `preempt_socket`, `stood_down`, `peer_silent`, `ssl`, `ssl_after_go_s`, `picture_after_go_s`, and from the phone `hu_connected`, `duplex`, `hu_disconnected`, `server_running` and `last_line`. Say how many IN_WINDOW cycles took the adopt path and how many the join path. Quote `clocks.log`, and say if any clock differs from the host by more than 1 s.

### R4L-C. A USER connect to a live session still redials, D-HP, candidate only

Run it last. If `listening` fails first, R4L-C is UNTESTABLE.

```bash
RUN=R4L-C; : > $OUT/R4L-C.windows.tsv; clockcheck R4L-C-start; pcap_start R4L-C
listening && hp_open R4L-C $HPKEYS && { mark R4L-C-go; stamp go-mid start; send ACTION_CONNECT --es ip $PHIP; sleep 15
  mark R4L-C-mid; stamp go-mid end; sleep 30; run_end R4L-C; th_report R4L-C; }
pcap_stop; clockcheck R4L-C-end
python3 -I ptr_phone.py $PCAP $OUT/R4L-C.windows.tsv | tee $OUT/R4L-C.phone.json
listening && echo SERVER_UP_after_R4L || echo SERVER_DOWN_after_R4L     # record
for s in 'is already connecting; not preempting it' 'adopting the socket discovery already opened' 'AapService: session state connecting'; do
  printf '%s: ' "$s"; seg R4L-C-go R4L-C-mid | grep -acF "$s"; done
```

**PASS:** in `seg R4L-C-go R4L-C-mid`, `is already connecting; not preempting it` is 0 and `AapService: session state connecting` is 1 or more. **FAIL:** the join line is 1 or more (a live session was joined). **Report only:** the adopt line count in the same window (expected 0); `hu_connected` in the `go-mid` window of `R4L-C.phone.json` (a redial to a live session is expected to show 1 new accept) with its `last_line`; the `SSL handshake complete` count after `R4L-C-go`; and whether a picture came back.

## 8. Do not re-run

- R1, R3, R3G, R3S, R5, R5P and R6. All passed in round 1, and the fixup changes only connection code (`CommManager`, `HeldServerSocket`, `SameEndpointConnectPolicy`, `NetworkDiscovery`, `WifiLauncherSharedServices`).
- R2 on GLES. Round 1 measured that Home does not tear a GLES surface down on D-HU (0 of 10 on both builds).
- The baseline arm of R4. `pr-1045-framing-round1-results.md`, O2, measured it.
- Anything about handshakes, pokes, group identity or audio sinks. Nothing on the branch touches them.

## 9. Report back

The numbers that decide whether the branch goes to review:

1. **R4-C:** the per-cycle table, the count of IN_WINDOW cycles by path (adopt, join), any cycle with `ssl` 0 and its class, and `listening-after`.
2. **R2:** per build, cycles run, crash lines with thread names, `race_count`, `early_init_cycles`, the two teardown counts, and the phone's `critical_error` per cycle. Say whether the mechanism was reached on the baseline.
3. **R0:** the candidate SHA and md5 you built, the tree hash, and the unit test count.

Results go in `projection-teardown-and-relays-round2-results.md`, in the template's skeleton (section 7). Use one `## R<id>` section per run id, with the verdict in bold alone on its line. Write the file after every run, and put the output of `th_report` in each run's section. The run ids are R0, R2-B, R2-C, R4-C, R4L-C.

```markdown
# projection-teardown-and-relays, round 2 results

**Candidate:** fix/projection-teardown-and-relays @ <the SHA R0 recorded>       **Baseline:** main @ 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9
**APK md5:** <candidate> / <baseline>
**Unit:** D-HU (UNISOC MT50, Android 14) with D-POCO; D-HP (API 17) with D-POCO
**Date:** <yyyy-mm-dd>

## Setup notes

The pre-flight bond lists before and after, the request and its answer, `SERVER_UP_PREFLIGHT` and `SERVER_UP_STAGE2`, the settings.xml delta per unit, both md5s, `apk-check.log`, the R0 git checks and identity greps, the classifier self-test output, the scripts copied and added, the audio keys as found, the D-HP serial, every preflight file and every setup failure with its re-run, the capture restarts on D-HP, every deviation and every string that did not match.

## R<id> (one per run id above)

**PASS** | **FAIL** | **INCONCLUSIVE** | **UNTESTABLE**

- Settings written:
- Radio state and how it was set:
- Discard-rule check: clean / re-run N times
- Thermal: th_report output
- Decisive lines, quoted with device timestamps:
- Measurements: the JSON fields the run grades, as numbers

## Anything the brief did not ask about
```

**Final step on every unit:** stop the app. Restore the backup `settings.xml` without `video-profile-starvation-cap`, and read the key back. The read must print 0. Diff the restored file against the backup and record the diff in Setup notes.

D-HP, through `run-as`:

```bash
HU=CNU350BGBJ; tab_put "$OUT/settings-backup-HP.xml" del:video-profile-starvation-cap
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml | grep -ac video-profile-starvation-cap    # 0
```

D-HU, as root, with the owner recorded at the start:

```bash
HU=27870808938846; python3 -I ohu_setkeys.py $OUT/settings-backup-HU.xml $OUT/restore-HU.xml del:video-profile-starvation-cap
OWN=$(cat $OUT/settings-owner-HU.txt)
adb -s $HU shell am force-stop $PKG
adb -s $HU push $OUT/restore-HU.xml /data/local/tmp/restore-HU.xml
adb -s $HU shell cp /data/local/tmp/restore-HU.xml /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell chown $OWN /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell chmod 660 /data/data/$PKG/shared_prefs/settings.xml
adb -s $HU shell stat -c %U:%G:%a /data/data/$PKG/shared_prefs/settings.xml                     # the recorded owner and 660
adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml | grep -ac video-profile-starvation-cap   # 0
adb -s $HU shell svc bluetooth enable; adb -s $HU shell svc wifi enable
```

Captures go to the fork's existing release **`rig-evidence-projection-teardown-and-relays`**, as one asset. Cite the asset name and its sha256 in the results file.

```bash
zip -1 -r projection-teardown-and-relays-round2-captures.zip $OUT
sha256sum projection-teardown-and-relays-round2-captures.zip
gh release upload rig-evidence-projection-teardown-and-relays --repo o-jcardenass/open-headunit projection-teardown-and-relays-round2-captures.zip
```

## 10. Strings, for mechanical re-verification

`decisive-strings` lists every app string the runs grep, as it appears literally in `app/src/main` (re-verify with `grep -F -r` on the candidate). The composed lines render from these fragments: `Decoder stopped: $reason` with the reasons `surfaceDestroyed`, `onDetachedFromWindow`, `projectionViewRecreate`, `activity_stopped`; `ConnectionArbiter: $what ($tier) refused while $held is in flight`; `ConnectionArbiter: $claim preempts $loser` with the claim text `the socket from `; `CommManager: $endpoint adopting the socket discovery already opened`; `CommManager: $endpoint is already connecting; not preempting it`; `WifiLauncherSharedServices: $endpoint is no longer held for discovery; not dialling it`; `AapService: session state $state` with `connecting`; `Media Sink Setup Request: %d on channel %s`; `HotspotManager: Setting hotspot enabled=$enabled`; `AutomationReceiver: $action`. The three lines marked candidate-only in section 6 are absent on the baseline by design. `system-strings` are platform lines, not in `app/src`. `identity-strings` are grepped in the DEX.

```decisive-strings
AapProjectionActivity: onResume
New surface set: 
Decoder stopped: 
surfaceDestroyed
onDetachedFromWindow
projectionViewRecreate
activity_stopped
Codec initialized:
Codec exception in output thread
Decoder restart requested: 
skipped: surface is no longer current
keyframe decoded - the picture is repaired
First frame rendered (hardware decode)
Throughput over 
rendered=
Media Sink Setup Request: 
AutomationReceiver: 
AutomationMarker: 
NetworkDiscovery: Found Headunit Server on 
Auto-connecting to Headunit Server at 
adopting the socket discovery already opened
is already connecting; not preempting it
is no longer held for discovery; not dialling it
ConnectionArbiter: 
) refused while 
 is in flight
wireless bring-up refused while 
 preempts 
the socket from 
 stood the wireless stack down
AapService: session state 
the peer accepted the connection and then sent nothing
SSL handshake complete
WifiLauncher: Initializing WiFi Mode: 
Projection backend: viewMode=
HotspotManager:
SoftApCredentials:
Setting hotspot enabled=
MATCH! Starting AapService
createGroup SUCCESS
Magic Garbage detected in header
```

`phone-strings` are Gearhead's lines on D-POCO, matched by message text. They are not in `app/src`; R4-C cycle 1 and the pre-flight capture confirm them.

```phone-strings
Head unit connected
startDuplexConnection
Head unit disconnected
Network server running on port
Critical error
OutOfCarLifecycle
```

```system-strings
Fatal signal
ABORTING
Process com.andrerinas.headunitrevived (pid
has died
```

```identity-strings
adopting the socket discovery already opened
HeldServerSocket
SameEndpointConnectPolicy
FeedLoopPolicy
```
