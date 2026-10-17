# projection-teardown-and-relays, round 3 brief: the connect race on the go-on-found trigger, and the early-dial reset, on D-HP with D-POCO

## 0. Pre-flight request, and the time budget

**This round is stage 2 only: D-HP with D-POCO.** D-HU is not used. If D-HU is plugged in, the stage block in section 7 takes it off the air.

**The round has named hand steps, and they are all the same step.** Before each R4E cycle, and once before R4-C, the script force-stops Android Auto on D-POCO and asks the operator to start its head unit server again. A reset session leaves that server deaf, and only a restart clears it (section 2). The script prints `OPERATOR_STEP <label>` to the terminal and to `$OUT/hand-steps.log`, then waits up to 10 min for proof that the server started. Nothing else in the round needs a hand.

Send this request to the operator once, before R0, and wait for the answer:

> **Pre-flight request.** This round asks you to start D-POCO's head unit server up to 8 times, between about minute 35 and minute 90 of the round. Each time, the script first force-stops Android Auto on D-POCO and prints `OPERATOR_STEP <label>`. When you see it, open Android Auto's developer settings on D-POCO and start the head unit server. The script continues by itself when the phone logs that the server runs. Please also confirm that D-HP and D-POCO are on the house WiFi.

Relay each `OPERATOR_STEP` line to the operator as soon as it appears. **Do not ask anything else during the round.**

| Step | Estimated wall clock |
|---|---|
| R0: candidate build, unit tests, identity, classifier self-tests (baseline APK reused when its md5 matches) | 25 min |
| Pre-flight, stage block, D-HP backup, phone log tags | 10 min |
| Baseline install on D-HP | 3 min |
| R4E-B: 3 cycles, a server restart before each | 15 min |
| Candidate install on D-HP | 3 min |
| R4E-C: 3 cycles, a server restart before each | 15 min |
| R4-C: a server restart, then up to 10 cycles | 20 min |
| R4L-C (plus a server restart only if R4-C ended on a wedge) | 6 min |
| R0's last condition, restore, zip, upload, results | 10 min |
| **Round total** | **about 1 h 50 min** |

A host thermal wait adds up to 15 min per run. An operator who answers slowly adds that time to each restart.

## 1. Build and baseline

| | Where | SHA | Gate |
|---|---|---|---|
| Candidate | fork `o-jcardenass/open-headunit`, branch `fix/projection-teardown-and-relays` | `a70ba7e24fff276d5610c35bd23150f64ae1d3a2` | 2714 JVM tests, 0 failures |
| Baseline | `andreknieriem/open-headunit` `main` | `2ca3b1f1a807faa11ba8608fd8a905acc09c65a9` | as rounds 1 and 2 |

**The candidate's history was rewritten again since round 2.** Round 2 built `b7a3b2e5`. `a70ba7e2` replaces it, and the branch is still four commits on the baseline. The only change is in `connection/HeldServerSocket.kt`: `settle` and `discard` no longer call `removeIf`. That call is API 24, and on D-HP (API 17) it crashed the app at every discovery hand-off with `NoClassDefFoundError: ...HeldServerSocket$$ExternalSyntheticLambda0`.

```bash
git fetch fork fix/projection-teardown-and-relays
git checkout --detach fork/fix/projection-teardown-and-relays     # or a new worktree at that ref
git rev-parse HEAD                                     # must be a70ba7e24fff276d5610c35bd23150f64ae1d3a2
git rev-parse 'HEAD^{tree}'                            # must be 6af9b23e47f67e28b697810e4251c32605a70c39
git merge-base HEAD 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9   # must be 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9
git log --oneline 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9..HEAD | wc -l   # must be 4
```

**If the SHA or the tree hash differs, stop and ask.** The branch then carries code this brief did not check.

**Two build fixes from round 2's Setup notes:**

- A new worktree has no `local.properties`. Copy it from the main checkout before the build, or the build fails with `SDK location not found`.
- Cap the Gradle workers with `export GRADLE_OPTS=-Dorg.gradle.workers.max=2`, not with `--max-workers=2`. Round 2's `r0_build.sh` does this; copy it and change `HUR_DIR` and `OUT` to the round 3 paths.

**The baseline APK:** reuse round 2's `base.apk` if its md5 is `50d3c116369f99784908b2380cdec421`. Otherwise build it from `2ca3b1f1a807faa11ba8608fd8a905acc09c65a9`. Copy each APK out of `apks/` as soon as it is built, because `build_hur.sh` deletes the previous one. Install with `adb -s <serial> install -r -d <named apk>`. Never install through `set_hu_pref.sh`.

### Host thermal gate (every run)

Round 1's rules stand (`rig-quirks/topics/tooling.md`):

- `apk_check` calls `th_gate`. It waits until the CPU package reads below 75C, for at most 15 min, then logs the temperature every 20 s to `$OUT/<RUN>.thermal`.
- If the host does not cool in 15 min, wait 15 more min with no adb work. If it is still at 75C or above, stop the round. Mark every run not yet started UNTESTABLE (host thermal).
- Write the results file after every run.
- Put the output of `th_report <RUN>` in each run's section. If R4-C's throttle delta is above 0, or its `thermal_max` is 90C or above, re-run R4-C once after a cool-down to 70C, because its class depends on timing.

### R0. Gate

Before the build, run `th_wait 70` and start `th_watch $OUT/R0.thermal &`.

1. Run the five `git` checks above. Record each output.
2. Build the candidate with `GRADLE_OPTS` set as above. Record its md5.
3. Run `run_unit_tests.sh` on the candidate.
4. Get the baseline APK (reuse or build, as above). Record its md5.
5. Run the identity greps. The last string is the lambda class that crashed D-HP in round 2; the fix removes it.

```bash
for a in cand.apk base.apk; do printf '%s ' $a
  for s in 'adopting the socket discovery already opened' HeldServerSocket SameEndpointConnectPolicy FeedLoopPolicy 'HeldServerSocket$$ExternalSyntheticLambda'; do
    printf '%s=%s ' "$s" $(unzip -p $a 'classes*.dex' | strings | grep -cF "$s"); done; echo; done
```

6. Run the classifier self-tests with the round 3 scripts (section 5) on the captures of rounds 1 and 2:

```bash
R1D=../projection-teardown-and-relays-round1; R2D=../projection-teardown-and-relays-round2
python3 -I ptr_race.py $R1D/R4-C.logcat R4-C 192.168.1.8 | python3 -c 'import json,sys; c=json.load(sys.stdin)["cycles"][0]; print(c["class"], c["verb_after_found_ms"], c["refused"], c["ssl"], c["route"])'
#   must print: IN_WINDOW 390 1 0 NONE
python3 -I ptr_race.py $R2D/R4-C.logcat R4-C 192.168.1.8 | python3 -c 'import json,sys; c=json.load(sys.stdin)["cycles"][0]; print(c["class"], c["verb_after_found_ms"], c["stood_down"], c["version_response"], c["econnreset"], c["aap_header_fail"], c["ssl"], c["ncdfe"], c["died"])'
#   must print: EARLY -3890 1 1 2 1 0 1 1
python3 -I ptr_phone.py $R2D/R4-C.phone.logcat $R2D/R4-C.windows.tsv | python3 -c 'import json,sys; w=json.load(sys.stdin)["windows"][0]; print(" ".join("%s=%s"%(k,w[k]) for k in ["hu_connected","duplex","handoff","requested_version","version_ok","end_of_stream","xfer_proxy","server_running","last_line"]))'
#   must print: hu_connected=1 duplex=1 handoff=1 requested_version=1 version_ok=1 end_of_stream=0 xfer_proxy=1 server_running=0 last_line=version_ok
```

7. **Graded at the end of the round, after R4L-C:** count `NoClassDefFoundError` in every D-HP capture of this round, including any under `failed-attempts/`.

```bash
for f in $OUT/R4E-B.logcat $OUT/R4E-C.logcat $OUT/R4-C.logcat $OUT/R4L-C.logcat $OUT/failed-attempts/*.logcat; do
  [ -f "$f" ] || continue; case "$f" in *.phone.logcat) continue;; esac; echo "$f $(grep -acF 'NoClassDefFoundError' "$f")"; done | tee $OUT/ncdfe.txt
```

**PASS:** all of these hold:

- the SHA, the tree hash and the merge-base match, with 4 commits;
- 2714 tests and 0 failures, counted from the JUnit XML, with `HeldServerSocketTest` and `SameEndpointConnectPolicyTest` present;
- the two md5s differ;
- each of the first four identity strings counts 1 or more in `cand.apk` and 0 in `base.apk`;
- `HeldServerSocket$$ExternalSyntheticLambda` counts 0 in `cand.apk`;
- the three self-tests print the expected lines;
- every line of `ncdfe.txt` ends in ` 0`.

A build or test failure stops the round. A self-test mismatch means the scripts were copied wrong: fix the copy, then continue. If `HeldServerSocket$$ExternalSyntheticLambda` counts 1 or more in `cand.apk`, record it and continue: the `ncdfe.txt` count decides whether the crash is gone.

After each install, send `ACTION_QUERY_STATE` and record the reply's `commit`. On the candidate it must start with `a70ba7e24fff`.

## 2. What this is and why it exists

Round 2 (`projection-teardown-and-relays-round2-results.md`) passed R0, R2-B and R2-C, so the decoder half of the branch is settled. R4-C failed and R4L-C was INCONCLUSIVE twice. The diagnosis found two causes.

**Finding 1, fixed on this tip: the candidate crashed on API 17 at every discovery hand-off.** `HeldServerSocket.settle` called `Collection.removeIf`, which is API 24. D-HP logged `java.lang.NoClassDefFoundError: com.andrerinas.openheadunit.connection.HeldServerSocket$$ExternalSyntheticLambda0` the first time the network sweep handed its socket on, and the process died. That explains both R4L-C attempts in full. The JVM tests run on a JDK, so they could not see it. The tip now uses a loop with the conditional `ConcurrentHashMap.remove(key, value)`, which exists on every API level.

**Finding 2, open: an early USER dial on a freshly started server is reset during SSL, and the server is then deaf.** Round 2's only R4-C cycle was EARLY. The verb landed 3.3 s after the launch and 3.9 s before `NetworkDiscovery: Found Headunit Server on`, so the fix's adopt and join paths were never reached. Aligned on the version exchange, the two captures read:

```
phone 63.387  GH.DHUService: Head unit connected                = D-HP 04.53, the USER socket (opened 04.42)
phone 63.593  CONNECTION_HANDOFF_COMPLETE
phone 63.900  XFER.Proxy.LITE: Forward throttled, dropping packet
phone 63.905  CAR.GAL.GAL.LITE: ... negotiated 1.7 (STATUS_SUCCESS)  = D-HP 05.052 version response
D-HP  05.352  ECONNRESET in AapSslContext.performHandshake        = phone 64.205: no Gearhead line at all
D-HP  07.462  Found Headunit Server on ...:5277                    phone logs no second Head unit connected
```

The server served our socket through the version exchange, then reset it before SSL, and accepted nothing after. This is not round 1's mechanism, where our own code closed an accepted probe. Three hypotheses remain, ranked:

1. **A USER dial within about 1 s of the app start on D-HP is reset by the phone.** The suspects are the stand-down that the USER claim runs (WirelessServer release, NSD unregister, `Legacy: Requested route to host`) or the `XFER.Proxy` drop. If true, a USER dial 15 s after the launch with no discovery running reaches SSL, and the same dial at about 1 s does not.
2. **The phone resets the first session after its server (re)starts.** If true, every first session after a restart is reset, on both builds and on every dial path.
3. **Our SSL first flight on API 17 differs on a USER dial.** This is unlikely, because the auto-connect path uses the same `AapSslContext`. If true, the reset follows the dial path and not the timing.

**What the branch changes in R4.** The sweep holds the socket it opened, under its endpoint, from the Found line on (`HeldServerSocket`). A USER connect to that endpoint takes the held socket and logs `CommManager: <ip>:5277 adopting the socket discovery already opened`. If the sweep took the socket first, the USER connect joins the attempt in flight and logs `CommManager: <ip>:5277 is already connecting; not preempting it`. So a verb inside the window never opens a second connection to the phone.

## 3. What is different about this round

- **The point of the round is R4-C on the go-on-found trigger.** The fixed 1 s offset landed EARLY on D-HP in round 2. Now the script watches D-HP's live capture and sends `ACTION_CONNECT` the moment `NetworkDiscovery: Found Headunit Server on` reaches it (trigger `F`). The verb then lands after the Found line by construction. The classifier still checks the class of every cycle, because the verb can also land after SSL (LATE).
- **R4E-B and R4E-C are new and report-only.** They measure finding 2 on both builds. Each runs 2 cycles with the verb about 1 s after the launch (trigger `E`, round 2's T1) and 1 control cycle with the verb 15 s after the launch and no discovery running (trigger `Q`). Their verdict grades the measurement, not the branch.
- **The `Q` control runs in manual mode.** "Discovery stopped first" is done with `wifi-connection-mode=0`, written with the app stopped, so no sweep and no last-session dial ever run. `ACTION_STOP_WIRELESS` after the launch is not used, because it can close a probe that the phone's server already accepted, and that alone wedges the server. A stop verb sent before the launch starts the service, which arms the stack.
- **A server restart precedes every R4E cycle and R4-C.** So every R4E cycle is a first session on a fresh server, on both builds. That makes hypothesis 2 testable: if it holds, the `Q` cycles are reset too.
- **The order is R4E-B, R4E-C, R4-C, R4L-C, for three reasons.**
  - R4E tells the grader how to read an R4-C cycle 1 reset. R4-C cycle 1 is also a first session after a restart. If R4E shows that every first session is reset (hypothesis 2), a cycle 1 reset in R4-C is the phone's, and the grader must say so.
  - The order needs 2 installs on D-HP (baseline, then candidate) instead of 3.
  - A wedge from R4E cannot reach R4-C: R4-C starts with its own server restart and its own proof that the server started.
- **The restart is proved, not assumed.** `listening` alone is not proof: round 2's `R4-C.listening-after` read `UP` on a deaf server. Each restart must also put one new `Network server running on port` line in the run's phone capture.
- **Gearhead's verbose tags are tried once, best effort.** Before R4E-B, the stage block sets `log.tag.CAR.GAL` and `log.tag.XFER.Proxy.LITE` to `VERBOSE` on D-POCO. It also sets the two full tags round 2 printed, `CAR.GAL.GAL.LITE` and `CAR.GAL.SECURITY.LITE`, because `Log.isLoggable` matches the full tag. Round 2's captures hold 0 V or D lines for either prefix. Record whether any appear.
- **The phone lines are new.** Round 2 never showed `startDuplexConnection` on this Gearhead build. `ptr_phone.py` now counts the real lines from round 2's captures: the `START_DUPLEX` intent, `CONNECTION_HANDOFF_COMPLETE`, `Requested protocol version`, `VERSION_NEGOTIATION_SUCCESS`, `ReaderThread: end of stream received` and `XFER.Proxy.LITE`.
- **Clock alignment.** In round 2 the phone ran 1.147 s behind D-HP. The phone capture uses epoch time and D-HP's uses `-v time`. To align a cycle, take D-HP's `Handshake: Version response received` time and the phone's `Requested protocol version` time in the same cycle. The difference is the offset; apply it to every other line of that cycle. A cycle with no version exchange uses `clocks.log`, which has 1 s resolution.
- **Round 2's script fixes are in.** `nostray` uses `pgrep -f '^adb .*logcat'` (FIX6). `hu_open` and `hp_open` stop their capture on every failure (FIX7, FIX8). The stage block checks that `rig_devices.sh` names D-HP's serial `CNU350BGBJ`. The build copies `local.properties` and sets `GRADLE_OPTS` (section 1).
- **D-HP is at 192.168.1.22 and D-POCO at 192.168.1.8** at the end of round 2. Record both, but the scripts read them live.
- **The rig's audio settings are a deliberate worst case.** Do not change `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink` on D-HP. Read them back and record them. Round 2 read `true`, `16`, `50`, `true`.
- **Never run adb calls in parallel against one unit.**
- **Pre-registered outcomes that are not failures:**
  - R4-C is INCONCLUSIVE if fewer than 3 cycles land IN_WINDOW in 10, with every cycle forming a session.
  - R4-C is UNTESTABLE if its server restart times out.
  - An R4E run is INCONCLUSIVE if fewer than 3 of its cycles ran after a proved restart.
  - An `E` cycle whose verb lands after the Found line is not an early sample. Report it with its class.

### Stages and USB ports

| Stage | Plugged in | Runs |
|---|---|---|
| 2 | D-HP, D-POCO (D-HU may stay plugged in) | R4E-B, R4E-C, R4-C, R4L-C |

## 4. Settings keys

Write the keys with the app stopped, through `tab_put`. Read every key back before the launch. **Back up D-HP's `settings.xml` at the start (`settings-backup-HP.xml`).** Diff it against round 2's backup in `../projection-teardown-and-relays-round2/` and state the delta in Setup notes.

| Key | Type | Value | Notes |
|---|---|---|---|
| `wifi-connection-mode` | int | `1` | Headunit Server. The `Q` cycles write `0` (manual) on top, and only for that cycle |
| `log-level` | int | `2` | INFO. Every decisive D-HP line in section 6 is INFO, WARN or ERROR |
| `view-mode` | int | `0` | SURFACE |
| `onboarding-version` | int | `2` | |
| `connection-modes` | string set | `wifi` | |
| `video-profile-starvation-cap` | boolean | delete | A latch that forces 720p30 and AAC after three fumbled sessions |
| `video-codec`, `resolutionId`, audio keys | | leave as found | record them |
| `auto-connect-last-session` | boolean | leave as found | record it. Absent means false, so no last-session dial runs at launch. If it reads `true`, record it and continue: `auto_last_session` in the race JSON counts that dial |

`ACTION_LOG_MARKER` is not gated on either build, so `allow-external-configuration` is not needed.

```bash
HPKEYS="int:wifi-connection-mode=1 int:log-level=2 int:view-mode=0 int:onboarding-version=2 set:connection-modes=wifi del:video-profile-starvation-cap"
```

## 5. Every action as a verb, and the helpers

Every action on the app is a `send ...` verb (template section 3), with the named exceptions below.

| Step | Command | Note |
|---|---|---|
| marker | `send ACTION_LOG_MARKER --es text <label>` (helper `mark`) | prints `AutomationMarker: <label>` at WARN |
| identity | `send ACTION_QUERY_STATE` | record the reply's `commit` |
| connect to the phone's server | `send ACTION_CONNECT --es ip $PHIP` | TCP to `:5277`, USER tier. The verb under test in every run |
| exit between cycles | `send ACTION_EXIT`, `sleep 3`, then `am force-stop` | stops the service and ends the session cleanly |

**Non-verb steps, with the reason:**

- **`am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`**, the app launch (template section 3).
- **`back`**, used only inside `hp_open` when the projection does not come forward: the same intent the service builds for a raise.
- **The server restart, a named hand step.** Starting Android Auto's head unit server is a toggle in Android Auto's developer settings on D-POCO. No adb command starts it. The script does the scriptable half (`am force-stop com.google.android.projection.gearhead` on D-POCO) and the operator does the toggle.

OS-level steps, allowed: `input keyevent 3` on D-POCO after a restart (the developer settings screen must not stay in front), `svc wifi|bluetooth enable|disable`, `dumpsys wifi`, `setprop` and `getprop` of `log.tag.*` on D-POCO, `am force-stop com.google.android.projection.gearhead` (inside `srv_restart` only).

### Scripts

Make the folder `hur-wifi-test-scripts/projection-teardown-and-relays-round3/`. Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_crash.py` into it from `../projection-teardown-and-relays-round2/`, as the round 2 tester left them, and copy `r0_build.sh` to adapt (section 1). Then save the three files below beside them. They replace round 2's `ptr_lib.sh`, `ptr_race.py` and `ptr_phone.py`. List every file in Setup notes. If a script does not match the real line format, fix it, say so in Setup notes, and keep going.

Source order: `ohu_lib.sh`, then `ptr_lib.sh`. `ptr_lib.sh` uses `PKG`, `send`, `mark`, `nl`, `waitfor`, `PIC`, `hu_put`, `tab_put` and `run_open` from `ohu_lib.sh`. The R2 helpers (`cyc_home`, `cyc_off`, `crashloop`, `hu_open`) stay in the file for later rounds and are not used here.

**Run the stage as one script in the background**, as round 2 did with `stage2.sh`, with the blocks of section 7 in order. Watch `$OUT/hand-steps.log` while it runs. Relay each new `OPERATOR_STEP` line to the operator at once.

**`ptr_lib.sh`** (round 2's, with FIX6 to FIX8 and the NEW3 helpers):

```bash
# ptr_lib.sh, round 3 : source it AFTER ohu_lib.sh (the copy in projection-teardown-and-relays-round2/).
# Needs HU, PH, OUT, PUT, BASEXML. Carries the round 1 tester fixes (FIX1 to FIX5) and the round 2 ones (FIX6 to FIX8).
# NEW3 marks what round 3 adds: the F, E and Q triggers, the server restart hand step, r4open and r4close.
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
# FIX7: a failed run_open also stops the unit capture, so it cannot append to its file during the next run.
hu_open() { RUN=$1; apk_check || return 1; : > "$OUT/$RUN.windows.tsv"; clockcheck "$RUN-start"; pcap_start "$RUN"
  CAPLOOP=; run_open "$@" || { pcap_stop; cap_stop; return 1; }; preflight "$1" || { run_end "$1"; return 2; }; }
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
# ACTION_CONNECT is only the fallback. FIX8: every failure stops the capture loop before it returns.
hp_fail() { echo "$1 $RUN"; cap_stop; return 1; }
hp_open() { RUN=$1; shift; CAP=$OUT/$RUN.logcat; apk_check || return 1
  adb -s "$HU" shell am force-stop $PKG; cap_start; sleep 1
  $PUT "$BASEXML" "$@"
  local L; L=$(nl); adb -s "$HU" shell am start -n $MAIN >/dev/null
  waitfor 45 'SSL handshake complete' "$L" || { mark "$RUN-fallback-connect"; send ACTION_CONNECT --es ip "$PHIP" >/dev/null
    waitfor 60 'SSL handshake complete' "$L" || { hp_fail SESSION_FAIL_SSL; return 1; }; }
  waitfor 40 'AapProjectionActivity: onResume' "$L" || { back; waitfor 20 'AapProjectionActivity: onResume' "$L" || { hp_fail SESSION_FAIL_RAISE; return 1; }; }
  waitfor 60 'Throughput over [0-9]+ms: rendered=[1-9]' "$L" || { hp_fail SESSION_FAIL_NO_FRAMES; return 1; }
  sleep 20; mark "$RUN-start"; preflight "$RUN" || { run_end "$RUN"; return 2; }; }
# race_cycle <RUN> <n> <F|T1|T2> : one launch, the verb by its trigger, 60 s for a session, a clean exit.
# NEW3 F: the verb the moment the Found line reaches the capture (go-on-found). T1: 1 s after the launch.
# T2: on the Auto-connecting line. Round 3 uses F only.
race_cycle() { local L; L=$(nl); mark "$1-c$2-$3-go"; stamp "c$2-$3" start
  adb -s "$HU" shell am start -n $MAIN >/dev/null
  if [ "$3" = F ]; then
    if fastwait 30 'NetworkDiscovery: Found Headunit Server on' "$L"; then send ACTION_CONNECT --es ip "$PHIP" >/dev/null
    else mark "$1-c$2-nofound"; fi
  elif [ "$3" = T1 ]; then sleep 1; send ACTION_CONNECT --es ip "$PHIP" >/dev/null
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
# FIX6: the stray-capture check matches adb itself, not the shell that launched the script.
nostray() { [ -z "$(pgrep -f '^adb .*logcat')" ] || { echo "STRAY_LOGCAT: $(pgrep -af '^adb .*logcat')"; return 1; }; }
# NEW3 r4open <RUN> / r4close <RUN> : one D-HP capture and one phone capture around a whole run (round 2's, moved here).
r4open() { RUN=$1; CAP=$OUT/$RUN.logcat; apk_check || return 1
  adb -s $HU shell am force-stop $PKG; : > $OUT/$RUN.windows.tsv; clockcheck $RUN-start
  pcap_start $RUN; cap_start; sleep 1; $PUT $BASEXML $HPKEYS; }
r4close() { mark $1-end; sleep 1; pcap_stop; cap_stop; clockcheck $1-end
  python3 -I ptr_race.py $CAP $1 $PHIP > $OUT/$1.race.json
  python3 -I ptr_phone.py $PCAP $OUT/$1.windows.tsv > $OUT/$1.phone.json
  listening && echo UP > $OUT/$1.listening-after || echo DOWN > $OUT/$1.listening-after; cat $OUT/$1.listening-after; }
# NEW3 srv_restart <label> : the named hand step. Stops Android Auto on D-POCO, asks the operator to start its head unit
# server, and waits up to 10 min for a new 'Network server running on port' line in the live phone capture, then listening.
# Needs a phone capture running (pcap_start). Returns 1 on a timeout.
srv_restart() { local c0 end; c0=$(grep -acF 'Network server running on port' "$PCAP")
  mark "$1-srv-restart"; adb -s "$PH" shell am force-stop com.google.android.projection.gearhead
  echo "$(date +%T) OPERATOR_STEP $1: on D-POCO, open Android Auto developer settings and start the head unit server" | tee -a "$OUT/hand-steps.log"
  end=$((SECONDS+600)); while [ $SECONDS -lt $end ]; do
    if [ "$(grep -acF 'Network server running on port' "$PCAP")" -gt "$c0" ] && listening; then
      adb -s "$PH" shell input keyevent 3; sleep 2
      echo "$(date +%T) SERVER_RESTARTED $1 new_lines=$(( $(grep -acF 'Network server running on port' "$PCAP") - c0 ))" | tee -a "$OUT/hand-steps.log"
      mark "$1-srv-up"; return 0; fi
    sleep 2; done
  echo "$(date +%T) SERVER_RESTART_TIMEOUT $1" | tee -a "$OUT/hand-steps.log"; return 1; }
# NEW3 early_cycle <RUN> <n> <E|Q> : settings written with the app stopped, one launch, the verb, 60 s for a session, a clean exit.
# E: Headunit Server mode, the verb 1 s after the launch (round 2's T1, which landed before the Found line).
# Q: manual mode (wifi-connection-mode 0, so no discovery runs), the verb 15 s after the launch.
early_cycle() { local L
  adb -s "$HU" shell am force-stop $PKG; sleep 1
  if [ "$3" = Q ]; then $PUT "$BASEXML" $HPKEYS int:wifi-connection-mode=0; else $PUT "$BASEXML" $HPKEYS; fi
  L=$(nl); mark "$1-c$2-$3-go"; stamp "c$2-$3" start
  adb -s "$HU" shell am start -n $MAIN >/dev/null
  if [ "$3" = Q ]; then sleep 15; else sleep 1; fi
  send ACTION_CONNECT --es ip "$PHIP" >/dev/null
  waitfor 60 'SSL handshake complete' "$L" && waitfor 30 'Throughput over [0-9]+ms: rendered=[1-9]' "$L"
  sleep 5; mark "$1-c$2-end"; stamp "c$2-$3" end; send ACTION_EXIT >/dev/null; sleep 3; adb -s "$HU" shell am force-stop $PKG; sleep 10; }
# NEW3 last_early <RUN> : class, ssl, econnreset of the last cycle so far
last_early() { race_json "$1" | python3 -c 'import json,sys; c=json.load(sys.stdin)["cycles"][-1]; print(c["trigger"], c["class"], c["ssl"], c["econnreset"])'; }
```

**`ptr_race.py`** (round 2's, plus the F, E and Q triggers and the reset, version, crash and last-session counts):

```python
#!/usr/bin/env python3
"""ptr_race.py <capture> <RUN> <phone-ip> : one row per connect cycle, as JSON. Round 3.

A cycle opens at '<RUN>-c<N>-<trigger>-go' and closes at '<RUN>-c<N>-end' (or the next go marker).
Triggers: T1, T2 (round 2), F (verb on the Found line), E (verb about 1 s after launch), Q (verb 15 s
after launch, in manual mode). The in-flight window opens at the first
'NetworkDiscovery: Found Headunit Server on' of the cycle.
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
GO = re.compile(r'AutomationMarker: ' + re.escape(run) + r'-c(\d+)-(T1|T2|F|E|Q)-go\s*$')
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
    vr = first(lambda l: 'Handshake: Version response received' in l)
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
                'verb_after_go_s': round((v[0] - t0) / 1000, 2) if v else None,
                'verb_after_found_ms': (v[0] - f[0]) if (v and f) else None,
                'ssl_after_go_s': round((s[0] - t0) / 1000, 2) if s else None,
                'picture_after_go_s': round((tpic - t0) / 1000, 2) if tpic else None,
                'version_response_time': vr[2][:18] if vr else None,
                'route': 'ADOPT' if adopt else ('JOIN' if join else 'NONE'),
                'adopt': adopt, 'join': join,
                'not_held': c('is no longer held for discovery; not dialling it'),
                'auto_connect': c('Auto-connecting to Headunit Server at'),
                'auto_last_session': c('Auto-connect: Attempting WiFi connection to'),
                'refused': sum(1 for t, n, l in w if REFUSED.search(l)),
                'refused_bringup': c('wireless bring-up refused while'),
                'preempt_socket': c('preempts the socket from'),
                'stood_down': c('stood the wireless stack down'),
                'peer_silent': c('the peer accepted the connection and then sent nothing'),
                'session_connecting': c('AapService: session state connecting'),
                'version_response': c('Handshake: Version response received'),
                'aap_header_fail': c('SSL Handshake: Failed to read AAP header'),
                'econnreset': c('ECONNRESET'),
                'ssl': c('SSL handshake complete'),
                'ncdfe': c('NoClassDefFoundError'),
                'died': sum(1 for t, n, l in w if 'Process com.andrerinas.headunitrevived (pid' in l and 'has died' in l),
                'fatal': c('Fatal signal')})
print(json.dumps({'run': run, 'phone_ip': phip, 'cycles': out}, indent=1))
```

**`ptr_phone.py`** (round 2's, with the phone lines this Gearhead build prints):

```python
#!/usr/bin/env python3
"""ptr_phone.py <phone-capture> <windows.tsv> : Gearhead's head unit server lines per window, as JSON. Round 3.

The phone capture is `adb -s 4f4027e9 logcat -v epoch`. windows.tsv holds one row per window:
label, host start in epoch ms, host end in epoch ms (written by stamp in ptr_lib.sh).
last_line names the latest of the STAGE lines in the window, so it names the stage a stalled
connection reached. The COUNT lines are counted beside it and do not take part in last_line.
Lines are matched by message text, not by tag. first_lines quotes the first hit of each, with its
epoch time, so the grader can align it to D-HP's clock.
"""
import json, re, sys

cap, tsv = sys.argv[1], sys.argv[2]
TS = re.compile(r'^\s*(\d+)\.(\d{3})\s')
STAGE = {'hu_connected': 'Head unit connected',
         'duplex': 'START u0 {act=com.google.android.gms.carsetup.START_DUPLEX',
         'handoff': 'CONNECTION_HANDOFF_COMPLETE',
         'requested_version': 'Requested protocol version',
         'version_ok': 'VERSION_NEGOTIATION_SUCCESS',
         'end_of_stream': 'ReaderThread: end of stream received',
         'hu_disconnected': 'Head unit disconnected',
         'server_running': 'Network server running on port'}
COUNT = {'xfer_proxy': 'XFER.Proxy.LITE',
         'critical_error': 'Critical error',
         'out_of_car': 'OutOfCarLifecycle'}
VD = {'car_gal_vd': re.compile(r' [VD] CAR\.GAL'), 'xfer_vd': re.compile(r' [VD] XFER\.Proxy')}
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
    for k, s in list(STAGE.items()) + list(COUNT.items()):
        hits = [(t, l) for t, l in win if s in l]
        r[k] = len(hits)
        if hits:
            first_lines[k] = hits[0][1].strip()[:200]
            if k in STAGE and (last is None or hits[-1][0] > last[0]):
                last = (hits[-1][0], k)
    for k, rx in VD.items():
        r[k] = sum(1 for t, l in win if rx.search(l))
    r['last_line'] = last[1] if last else None
    r['first_lines'] = first_lines
    out.append(r)
print(json.dumps({'capture': cap, 'windows': out}, indent=1))
```

## 6. The lines that decide every run

Every D-HP line below was checked with `grep -F -r` against `app/src/main` at the candidate tip (`a70ba7e2`, tree `6af9b23e`) and with `git grep -F` at `2ca3b1f1`. Lines marked **candidate only** are absent on the baseline by design. Lines marked *(composed)* render from a format string; the runs grep the rendered form, and section 10 lists the source fragments. The phone lines are Gearhead's and are not in this tree: each was copied from round 2's D-POCO captures, with the tag shown. System lines come from the platform. Grep every capture with `grep -a`.

| Meaning | Line | Source | Baseline | File and window |
|---|---|---|---|---|
| verb landed | `AutomationReceiver: com.andrerinas.openheadunit.ACTION_CONNECT` | `AutomationReceiver.kt` | present | D-HP capture, per cycle |
| marker | `AutomationMarker: ` | `AutomationEffectRunner.kt` | present | every window |
| **the in-flight window opens, and the F trigger** | `NetworkDiscovery: Found Headunit Server on <ip>:5277` | `NetworkDiscovery.kt` | present | D-HP, per cycle |
| discovery hands over its socket | `Auto-connecting to Headunit Server at <ip>:5277 (reusing socket)` | `WifiLauncherSharedServices.kt` | present | D-HP, report |
| a last-session dial at launch (must be absent) | `Auto-connect: Attempting WiFi connection to` | `HomeFragment.kt` | present | D-HP, report (`auto_last_session`) |
| **the adopt, candidate only** | `CommManager: <ip>:5277 adopting the socket discovery already opened` | `CommManager.kt` | absent | R4-C per cycle, R4L-C window |
| **the join, candidate only** | `CommManager: <ip>:5277 is already connecting; not preempting it` | `CommManager.kt` | absent | R4-C per cycle, R4L-C window |
| discovery stands back, candidate only | `WifiLauncherSharedServices: <ip>:5277 is no longer held for discovery; not dialling it` | `WifiLauncherSharedServices.kt` | absent | R4-C, report |
| a claim refused *(composed)* | `ConnectionArbiter: <what> (<TIER>) refused while <held> is in flight` | `ConnectionArbiter.kt` | present | R4-C per cycle (`refused`) |
| the stack held down | `ConnectionArbiter: wireless bring-up refused while ` | `ConnectionArbiter.kt` | present | report (`refused_bringup`) |
| the preempt *(composed)* | `preempts the socket from` | `ConnectionArbiter.kt` + `CommManager.kt` | present | R4-C per cycle |
| a USER claim stood the stack down | `stood the wireless stack down` | `ConnectionArbiter.kt` | present | R4E per cycle (`stood_down`) |
| a connect started *(composed)* | `AapService: session state connecting` | `AapService.kt` | present | R4L-C window |
| **the version exchange, the clock anchor** | `Handshake: Version response received` | `AapTransport.kt` | present | R4E and R4-C per cycle (`version_response`) |
| SSL read nothing | `SSL Handshake: Failed to read AAP header` | `AapSslContext.kt` (ERROR) | present | R4E per cycle (`aap_header_fail`) |
| server accepted and went silent | `the peer accepted the connection and then sent nothing` | `AapTransport.kt` | present | report (`peer_silent`) |
| **session formed** | `SSL handshake complete` | `AapSslContext.kt` (INFO) | present | every run (`ssl`) |
| steady state *(composed)* | `Throughput over <N>ms: rendered=<N>` | `VideoDecoder.kt` | present | `picture_after_go_s`, `hp_open` |
| picture | `keyframe decoded - the picture is repaired`, `First frame rendered (hardware decode)` | `VideoDecoder.kt` | present | `PIC` (R4L-C) |
| return reached the activity | `AapProjectionActivity: onResume` | `AapProjectionActivity.kt` | present | `hp_open` |
| preflight: WiFi mode | `WifiLauncher: Initializing WiFi Mode: AUTO` | `WifiLauncherManager.kt` | present | R4-C preflight |
| preflight: view backend *(composed)* | `Projection backend: viewMode=SURFACE ` | `AapProjectionActivity.kt` | present | R4-C preflight |
| preflight: codec *(composed)* | `Media Sink Setup Request: 3 on channel VIDEO` | `AapControl.kt` | present | R4-C preflight |
| preflight: codec built | `Codec initialized: ` | `VideoDecoder.kt` | present | R4-C preflight |
| **system: the reset** | `ECONNRESET` (in `java.net.SocketException: recvfrom failed: ECONNRESET (Connection reset by peer)`) | libcore, printed by `AppLog` | n/a | R4E per cycle (`econnreset`, counts lines, so 2 is one reset) |
| **system: the API 17 crash** | `NoClassDefFoundError` | ART / Dalvik | n/a | every D-HP capture (`ncdfe`), R0 |
| system: our process died | `Process com.andrerinas.headunitrevived (pid` with `has died` | ActivityManager | n/a | per cycle (`died`) |
| **phone: the server accepted one connection** | `Head unit connected` (`I GH.DHUService`) | Gearhead | n/a | D-POCO capture, per cycle (`hu_connected`) |
| phone: the accept went to the car service | `START u0 {act=com.google.android.gms.carsetup.START_DUPLEX` (`I ActivityTaskManager`) | Android, for Gearhead | n/a | D-POCO, per cycle (`duplex`) |
| phone: the hand-off finished | `CONNECTION_HANDOFF_COMPLETE` (`I GH.ConnLoggerV2`) | Gearhead | n/a | D-POCO, per cycle (`handoff`) |
| **phone: the version exchange, the clock anchor** | `Requested protocol version` (`I CAR.GAL.GAL.LITE`) | Gearhead | n/a | D-POCO, per cycle (`requested_version`) |
| phone: version agreed | `VERSION_NEGOTIATION_SUCCESS` (`I GH.ConnLoggerV2`) | Gearhead | n/a | D-POCO, per cycle (`version_ok`) |
| phone: our side closed | `ReaderThread: end of stream received` (`W CAR.GAL.GAL.LITE`) | Gearhead | n/a | D-POCO, per cycle (`end_of_stream`) |
| phone: a dropped packet | `XFER.Proxy.LITE` (round 2: `W XFER.Proxy.LITE: Forward throttled, dropping packet`) | Gearhead | n/a | D-POCO, per cycle (`xfer_proxy`) |
| **phone: the server is up** | `Network server running on port` (`I GH.DHUService`) | Gearhead | n/a | D-POCO, each restart (`srv_restart`), per cycle (`server_running`) |
| phone: Gearhead ended the car link | `Critical error` | Gearhead | n/a | D-POCO, report |
| phone: the connection ended | `Head unit disconnected` | Gearhead | n/a | D-POCO, report. Round 2's captures hold 0 |
| **identity, in the DEX** | `adopting the socket discovery already opened`, `HeldServerSocket`, `SameEndpointConnectPolicy`, `FeedLoopPolicy`; and `HeldServerSocket$$ExternalSyntheticLambda` (must be 0) | candidate only | absent | R0 |

## 7. Runs

Run ids carry the build: `-B` baseline, `-C` candidate. **The point of the round is R4-C.**

### Stage block (once, after R0)

```bash
OUT=~/hur-wifi-test-scripts/projection-teardown-and-relays-round3; cd $OUT      # adjust to where hur-wifi-test-scripts/ lives
HU=CNU350BGBJ; PH=4f4027e9; OTHER=; PUT=tab_put; source ./ohu_lib.sh; source ./ptr_lib.sh
RUN=stage; nostray || echo "STOP: kill the stray logcat by its pid, then re-run this block"
adb devices                                                     # D-HP (CNU350BGBJ) and D-POCO listed; record D-HU if listed
grep -c CNU350BGBJ ../rig_devices.sh                             # 1 or more; if 0, change the D-HP line to CNU350BGBJ and record it
clockcheck stage-start
# D-HU, only if adb devices lists 27870808938846: take it off the air so it cannot pull D-POCO
D=27870808938846; if adb devices | grep -q "^$D"; then HU=$D; send ACTION_EXIT >/dev/null; sleep 3; adb -s $D shell am force-stop $PKG
  adb -s $D shell svc bluetooth disable; adb -s $D shell svc wifi disable; HU=CNU350BGBJ; echo DHU_OFF_AIR; fi   # restore both at the end
adb -s $PH shell svc wifi enable; sleep 5; adb -s $PH shell dumpsys wifi | grep -a -m1 "Wi-Fi is"   # must read enabled
for i in 1 2 3 4 5 6; do adb -s $HU shell dumpsys wifi | grep -aq "Wi-Fi is enabled" && break; sleep 5; done
adb -s $HU shell dumpsys wifi | grep -a -m1 "Wi-Fi is"                                            # must read enabled (it can take 30 s)
PHIP=$(adb -s $PH shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2); echo PHIP=$PHIP     # round 2: 192.168.1.8
HPIP=$(adb -s $HU shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2); echo HPIP=$HPIP     # round 2: 192.168.1.22
adb -s $HU shell am force-stop $PKG
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-backup-HP.xml; BASEXML=$OUT/settings-backup-HP.xml
diff ../projection-teardown-and-relays-round2/settings-backup-HP.xml $BASEXML                   # record the delta, even if empty
grep -aoE '(video-codec|resolutionId|use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink|auto-connect-last-session|last-connection-ip)[^/]*' $BASEXML   # record
adb -s $PH shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName   # record (string drift)
adb -s $PH shell dumpsys window | grep -a mCurrentFocus                                            # record; if Settings, input keyevent 3
for t in CAR.GAL XFER.Proxy.LITE CAR.GAL.GAL.LITE CAR.GAL.SECURITY.LITE; do adb -s $PH shell setprop log.tag.$t VERBOSE
  printf '%s=%s\n' $t "$(adb -s $PH shell getprop log.tag.$t | tr -d '\r')"; done | tee $OUT/phone-logtags.txt   # record; best effort
HPKEYS="int:wifi-connection-mode=1 int:log-level=2 int:view-mode=0 int:onboarding-version=2 set:connection-modes=wifi del:video-profile-starvation-cap"
PF_MODE=AUTO PF_VIEW=SURFACE PF_SINK=3       # PF_SINK=7 only if the D-HP backup's video-codec is literally H.265
listening && echo SERVER_UP_STAGE || echo SERVER_DOWN_STAGE    # record only; every run restarts the server first
```

If `setprop` fails, record the error and continue. The tags are best effort.

Every capture is `logcat -v time` on D-HP and `logcat -v epoch` on D-POCO, both with `stdbuf -oL`, started before the launch (the helpers do this). At the end of every run, `ps aux | grep -c "[l]ogcat"` must print 0, and the last D-HP capture line must be within 5 s of the unit's clock. If either check fails, that run is INCONCLUSIVE. `pcap_stop` prints `phone_lines=<N>`: **a run whose phone capture is empty (`phone_lines=0`) is a setup failure.** Re-run it once, and never grade the empty one.

### R4E-B and R4E-C. The early-dial reset, both builds (report-only)

Install the baseline, run R4E-B, then install the candidate and run R4E-C. Each run is 3 cycles: `E`, `E`, `Q`. A server restart comes before every cycle.

```bash
adb -s $HU shell am force-stop $PKG; adb -s $HU install -r -d base.apk; WANT_MD5=$(md5sum base.apk | cut -d' ' -f1); send ACTION_QUERY_STATE   # record commit
early_run() { local n t; r4open $1 || { echo "OPEN_FAIL $1"; return 1; }
  for n in 1 2 3; do t=E; [ $n = 3 ] && t=Q
    srv_restart $1-c$n || { echo "$n $t NOT_RUN server-restart-timeout" | tee -a $OUT/$1.progress; continue; }
    early_cycle $1 $n $t; echo "$n $(last_early $1)" | tee -a $OUT/$1.progress; done
  r4close $1; th_report $1; }
early_run R4E-B
adb -s $HU shell am force-stop $PKG; adb -s $HU install -r -d cand.apk; WANT_MD5=$(md5sum cand.apk | cut -d' ' -f1); send ACTION_QUERY_STATE   # commit must start with a70ba7e24fff
early_run R4E-C
grep -a 'SERVER_RESTART' $OUT/hand-steps.log                     # one SERVER_RESTARTED line per cycle, each with new_lines=1 or more
```

**Stop rule:** 3 cycles per run. A cycle whose restart times out is not run; the run moves on to the next cycle. Do not recover a wedge inside a cycle: the next cycle's restart does that.

Grade from `<RUN>.race.json`, `<RUN>.phone.json` and `hand-steps.log`. `ptr_race.py` names cycle N `cycle` N, and `ptr_phone.py` names the same cycle `cN-E` or `cN-Q`. Join them by cycle number. A `Q` cycle has no Found line, so the classifier calls it `EARLY`; that is expected and means "no discovery".

**PASS (the measurement was taken),** all of these, per run:

- 3 cycles ran, each after a `SERVER_RESTARTED` line with `new_lines=1` or more;
- `phone_lines` is above 0;
- in each cycle window, `server_running` in `<RUN>.phone.json` is 0 (the restart line falls before the window, not inside it).

**INCONCLUSIVE:** fewer than 3 cycles ran after a proved restart. **UNTESTABLE:** no restart succeeded. These verdicts grade the measurement. **They are not a verdict on the branch.**

**Report one row per cycle, both builds side by side:**

- from the race JSON: `trigger`, `class`, `verb_after_go_s`, `verb_after_found_ms`, `stood_down`, `auto_connect`, `auto_last_session`, `version_response`, `version_response_time`, `econnreset`, `aap_header_fail`, `peer_silent`, `ssl`, `ssl_after_go_s`, `picture_after_go_s`, `ncdfe`, `died`;
- from the phone JSON: `hu_connected`, `duplex`, `handoff`, `requested_version`, `version_ok`, `end_of_stream`, `xfer_proxy`, `critical_error`, `car_gal_vd`, `xfer_vd`, `last_line`, and each `first_lines` entry with its epoch time;
- the clock offset of each cycle with a version exchange (section 3), and the D-HP time of the reset aligned to the phone's clock;
- the phone's last Gearhead line before the reset, and the first after it, with tags, from `grep -a -E 'GH\.|CAR\.|XFER\.' <RUN>.phone.logcat` inside the window. This one needs a read of the window, not the extract. Read only that window.

**Pre-registered readings,** for the grader to name which one the data matches:

| `E` cycles (both builds) | `Q` cycles (both builds) | R4-C cycle 1 | Supports |
|---|---|---|---|
| `econnreset` 1 or more, `ssl` 0 | `ssl` 1 or more | | hypothesis 1: the early timing or the stand-down |
| `ssl` 0 | `ssl` 0 | `ssl` 0 | hypothesis 2: the first session after a restart |
| `ssl` 0 | `ssl` 0 | `ssl` 1 or more | hypothesis 3: the USER dial path |
| `ssl` 1 or more | `ssl` 1 or more | | round 2's reset does not reproduce; name it |

If R4E-B and R4E-C differ on the same trigger, say so: the reset then depends on the build.

### R4-C. The connect race on D-HP, go-on-found, candidate only (**point of the round**)

No baseline arm. The framing round 1 results (run O2, D-HP in Headunit Server mode) measured the baseline: 27 failed cycles in 6 minutes. The candidate is already installed from R4E-C.

```bash
if ! r4open R4-C; then echo "OPEN_FAIL R4-C: fix the APK or the thermal gate, then re-run this block" | tee -a $OUT/R4-C.progress
elif srv_restart R4-C-pre; then
  for n in $(seq 1 10); do
    race_cycle R4-C $n F; r=$(last_race R4-C); echo "$n F $r" | tee -a $OUT/R4-C.progress
    read cls ssl route <<< "$r"
    [ "$ssl" = 0 ] && { mark R4-C-wedged-c$n; break; }           # no session: stop, do not recover
    [ $n = 1 ] && { preflight R4-C || { echo "SETUP_FAIL R4-C"; break; }; }
    [ "$(inwin R4-C)" -ge 3 ] && break; done
else echo "R4-C UNTESTABLE: the server restart timed out" | tee -a $OUT/R4-C.progress; fi
[ -n "$PCAPPID" ] && { inwin R4-C | tee $OUT/R4-C.inwin; r4close R4-C; th_report R4-C; }
```

`preflight` runs once, after cycle 1's session. A preflight FAIL stops R4-C as a setup failure: fix the settings, re-run R4-C once from a new server restart, and never grade the failed attempt. If cycle 1 forms no session, the preflight cannot run; grade the cycle as below and say so.

**Stop rule:** 3 `IN_WINDOW` cycles with `same_endpoint` true, or 10 cycles, or the first cycle with `ssl` 0, whichever comes first. **Do not try to recover a wedge.** It is the FAIL evidence.

Grade from `R4-C.race.json`, `R4-C.phone.json`, `R4-C.listening-after` and `hand-steps.log`. `ptr_phone.py` names cycle N `cN-F`. An IN_WINDOW cycle below means `class` `IN_WINDOW` and `same_endpoint` true.

**Confirm the phone accept line on cycle 1 first.** If cycle 1 formed a session (`ssl` 1 or more) and its `hu_connected` is 0, the phone prints a different accept line on this Gearhead build. Then quote the phone lines near cycle 1's Found line, drop the `hu_connected` conditions below, grade the rest, and say so in the verdict.

**PASS (R4-C),** all of these:

- `SERVER_RESTARTED R4-C-pre` with `new_lines=1` or more is in `hand-steps.log`;
- at least **3** IN_WINDOW cycles;
- each IN_WINDOW cycle has `adopt` + `join` of exactly 1 (`route` is `ADOPT` or `JOIN`), `refused` 0, `preempt_socket` 0, `ncdfe` 0 and `ssl` 1 or more;
- each IN_WINDOW cycle has `hu_connected` of exactly **1** in `R4-C.phone.json`;
- no cycle of any class has `ssl` 0;
- `R4-C.listening-after` reads `UP`.

**FAIL,** any of these:

- a cycle of any class with `ssl` 0. Name its class. Quote its Found, verb, adopt or join, refused, `ECONNRESET` and `sent nothing` lines with timestamps. Name the phone line in its `last_line`: it is the latest stage line, so it names the stage that stalled;
- a cycle with `ncdfe` 1 or more, or `died` 1 or more;
- an IN_WINDOW cycle with `hu_connected` of 2 or more, even if SSL formed: we opened a second connection;
- an IN_WINDOW cycle with `refused` 1 or more, or `preempt_socket` 1 or more;
- an IN_WINDOW cycle with `route` `NONE` (the verb landed in the window and the fix did not act);
- `R4-C.listening-after` reads `DOWN`.

**If cycle 1 fails with `ssl` 0 and the R4E reading is hypothesis 2,** the FAIL stands, and the verdict must say that R4E predicted this reset on both builds.

**INCONCLUSIVE:** fewer than 3 IN_WINDOW cycles after 10 cycles, every cycle with `ssl` 1 or more, and no FAIL condition. Cycles with `same_endpoint` false, `NO_VERB`, `EARLY`, `AMBIGUOUS` or `LATE` count toward neither PASS nor INCONCLUSIVE; report how many of each. A `LATE` cycle means the verb landed after SSL, so the window was shorter than the verb's delivery time on D-HP: report `verb_after_found_ms` and `ssl_after_go_s` for it.

**If the change did nothing,** an IN_WINDOW cycle shows `route` `NONE`, `refused` 1, `peer_silent` 1, `ssl` 0 and `hu_connected` 2, as cycle 1 of round 1 would have.

**Report:** one row per cycle with `cycle`, `trigger`, `class`, `verb_after_found_ms`, `route`, `adopt`, `join`, `not_held`, `auto_connect`, `refused`, `refused_bringup`, `preempt_socket`, `stood_down`, `peer_silent`, `econnreset`, `ssl`, `ssl_after_go_s`, `picture_after_go_s`, `ncdfe`, and from the phone `hu_connected`, `duplex`, `handoff`, `version_ok`, `xfer_proxy`, `server_running` and `last_line`. Say how many IN_WINDOW cycles took the adopt path and how many the join path. Quote `clocks.log`, and say if any clock differs from the host by more than 1 s.

### R4L-C. A USER connect to a live session still redials, D-HP, candidate only

Run it last. The run is unchanged from round 2, with two additions: a server restart first, but only if R4-C ended on a wedge, and a `bringup` phone window so one condition is graded from the phone.

```bash
RUN=R4L-C; : > $OUT/R4L-C.windows.tsv; clockcheck R4L-C-start; pcap_start R4L-C
W=$(grep -acF 'AutomationMarker: R4-C-wedged-' $OUT/R4-C.logcat); echo "R4-C wedged markers: $W"
SRV=0; if [ "$W" -ge 1 ]; then srv_restart R4L-C-pre || SRV=1; fi
stamp bringup start
if [ $SRV = 0 ] && listening && hp_open R4L-C $HPKEYS; then stamp bringup end
  mark R4L-C-go; stamp go-mid start; send ACTION_CONNECT --es ip $PHIP; sleep 15
  mark R4L-C-mid; stamp go-mid end; sleep 30; run_end R4L-C; th_report R4L-C
else stamp bringup end; echo "R4L-C did not open"; fi
pcap_stop; clockcheck R4L-C-end
python3 -I ptr_phone.py $PCAP $OUT/R4L-C.windows.tsv | tee $OUT/R4L-C.phone.json
listening && echo SERVER_UP_after_R4L || echo SERVER_DOWN_after_R4L     # record
for s in 'is already connecting; not preempting it' 'adopting the socket discovery already opened' 'AapService: session state connecting'; do
  printf '%s: ' "$s"; seg R4L-C-go R4L-C-mid | grep -acF "$s"; done
```

`run_end` stops the phone capture first and runs `ptr_phone.py`; the second `pcap_stop` then does nothing, and the `tee` line writes the same JSON again. If `srv_restart` times out, R4L-C is UNTESTABLE. If `listening` fails, R4L-C is UNTESTABLE.

**PASS,** all of these:

- in `seg R4L-C-go R4L-C-mid`, `is already connecting; not preempting it` is 0 and `AapService: session state connecting` is 1 or more;
- in the `bringup` window of `R4L-C.phone.json`, `hu_connected` is exactly 1: the bring-up opened one connection to the phone.

**FAIL:** the join line is 1 or more (a live session was joined), or `bringup` `hu_connected` is 2 or more. If the session formed and `bringup` `hu_connected` is 0, the accept line drifted: quote the phone lines and grade the rest. **Report only:** the adopt line count in the go-mid window (expected 0); `hu_connected` in the `go-mid` window with its `last_line`; the `SSL handshake complete` count after `R4L-C-go`; and whether a picture came back.

## 8. Do not re-run

- R1, R2, R3, R3G, R3S, R5, R5P and R6. R1, R3, R3G, R3S, R5, R5P and R6 passed in round 1, and R2-B and R2-C passed in round 2. The change since then touches only `HeldServerSocket.settle` and `discard`.
- Stage 1 on D-HU. Nothing on the new tip changes D-HU's path.
- The baseline arm of R4. The framing round 1 results (run O2) measured it.
- Anything about handshakes, pokes, group identity or audio sinks. Nothing on the branch touches them.

## 9. Report back

The numbers that decide whether the branch goes to review:

1. **R4-C:** the per-cycle table, the count of IN_WINDOW cycles by path (adopt, join), any cycle with `ssl` 0 and its class, `ncdfe` per cycle, and `listening-after`.
2. **R4E-B and R4E-C:** per cycle, `ssl`, `econnreset` and the phone's `hu_connected`, `handoff`, `version_ok` and `xfer_proxy`, with the pre-registered reading the data matches. Say whether the verbose tags added any lines (`car_gal_vd`, `xfer_vd`).
3. **R0:** the candidate SHA and md5 you built, the tree hash, the unit test count, and the `NoClassDefFoundError` count across every D-HP capture.

Results go in `projection-teardown-and-relays-round3-results.md`, in the template's skeleton (section 7). Use one `## R<id>` section per run id, with the verdict in bold alone on its line. Write the file after every run, and put the output of `th_report` in each run's section. The run ids are R0, R4E-B, R4E-C, R4-C, R4L-C.

```markdown
# projection-teardown-and-relays, round 3 results

**Candidate:** fix/projection-teardown-and-relays @ <the SHA R0 recorded>       **Baseline:** main @ 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9
**APK md5:** <candidate> / <baseline>
**Unit:** D-HP (HP Slate 7 Plus, API 17) with D-POCO
**Date:** <yyyy-mm-dd>

## Setup notes

The operator's answer, `SERVER_UP_STAGE`, every `OPERATOR_STEP` and `SERVER_RESTARTED` line from `hand-steps.log` with its time, the settings.xml delta, both md5s, `apk-check.log`, the R0 git checks and identity greps, the three self-test outputs, the scripts copied and added, the audio keys and `auto-connect-last-session` as found, the D-HP serial and both IP addresses, the Gearhead version, `phone-logtags.txt`, the preflight file, every setup failure with its re-run, the capture restarts on D-HP, every deviation and every string that did not match.

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

**Final step.** Stop the app on D-HP. Restore the backup `settings.xml` without `video-profile-starvation-cap`, and read the key back. The read must print 0. Diff the restored file against the backup and record the diff in Setup notes.

```bash
HU=CNU350BGBJ; adb -s $HU shell am force-stop $PKG; tab_put "$OUT/settings-backup-HP.xml" del:video-profile-starvation-cap
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/restored-HP-readback.xml
grep -ac video-profile-starvation-cap $OUT/restored-HP-readback.xml                                   # 0
for t in CAR.GAL XFER.Proxy.LITE CAR.GAL.GAL.LITE CAR.GAL.SECURITY.LITE; do adb -s $PH shell setprop log.tag.$t '""'; done
D=27870808938846; adb devices | grep -q "^$D" && { adb -s $D shell svc bluetooth enable; adb -s $D shell svc wifi enable; }   # only if the stage block took D-HU off the air
pgrep -af '^adb .*logcat'                                                                             # must print nothing
```

Leave D-POCO's head unit server running. Captures go to the fork's existing release **`rig-evidence-projection-teardown-and-relays`**, as one asset. Cite the asset name and its sha256 in the results file.

```bash
zip -1 -r projection-teardown-and-relays-round3-captures.zip $OUT -x '*.apk'
sha256sum projection-teardown-and-relays-round3-captures.zip
gh release upload rig-evidence-projection-teardown-and-relays --repo o-jcardenass/open-headunit projection-teardown-and-relays-round3-captures.zip
```

## 10. Strings, for mechanical re-verification

`decisive-strings` lists every app string the runs grep, as it appears literally in `app/src/main` (re-verify with `grep -F -r` on the candidate). The composed lines render from these fragments: `ConnectionArbiter: $what ($tier) refused while $held is in flight`; `ConnectionArbiter: $claim preempts $loser` with the claim text `the socket from `; `CommManager: $endpoint adopting the socket discovery already opened`; `CommManager: $endpoint is already connecting; not preempting it`; `WifiLauncherSharedServices: $endpoint is no longer held for discovery; not dialling it`; `AapService: session state $state` with `connecting`; `Media Sink Setup Request: %d on channel %s`; `Projection backend: viewMode=$mode `; `AutomationReceiver: $action`. The three lines marked candidate-only in section 6 are absent on the baseline by design. `phone-strings` are Gearhead's lines on D-POCO, copied from round 2's captures. `system-strings` are platform lines, not in `app/src`. `identity-strings` are grepped in the DEX.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
NetworkDiscovery: Found Headunit Server on 
Auto-connecting to Headunit Server at 
Auto-connect: Attempting WiFi connection to
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
Handshake: Version response received
SSL Handshake: Failed to read AAP header
the peer accepted the connection and then sent nothing
SSL handshake complete
Throughput over 
rendered=
keyframe decoded - the picture is repaired
First frame rendered (hardware decode)
AapProjectionActivity: onResume
WifiLauncher: Initializing WiFi Mode: 
Projection backend: viewMode=
Media Sink Setup Request: 
Codec initialized: 
```

```phone-strings
Head unit connected
START u0 {act=com.google.android.gms.carsetup.START_DUPLEX
CONNECTION_HANDOFF_COMPLETE
Requested protocol version
VERSION_NEGOTIATION_SUCCESS
ReaderThread: end of stream received
XFER.Proxy.LITE
Network server running on port
Critical error
Head unit disconnected
OutOfCarLifecycle
```

```system-strings
ECONNRESET
NoClassDefFoundError
Process com.andrerinas.headunitrevived (pid
has died
Fatal signal
```

```identity-strings
adopting the socket discovery already opened
HeldServerSocket
SameEndpointConnectPolicy
FeedLoopPolicy
HeldServerSocket$$ExternalSyntheticLambda
```
