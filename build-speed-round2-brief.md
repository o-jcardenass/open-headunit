# build-speed, round 2 brief

Two parts. **Part A** is host only: build times of the candidate on the tester PC, compared with round 1, plus two memory settings for this PC. **Part S** is a smoke test of the candidate on the oldest unit, D-HP (API 17), and on D-HU, because the candidate moves the app to Kotlin 2.4.20.

## 1. Build and baseline

- Candidate: `fork/chore/build-speed` at `b97f96b9` (12-char `b97f96b94ac8`). Three commits on `main` `77914f18`. The branch was regrouped once before this brief, so fetch it fresh.
- Baseline: round 1's numbers on `77914f18` (`build-speed-round1-results.md`). Do not rebuild `main`.

```bash
git -C /home/oscar/Coding/StudioProjects/headunit-revived fetch fork chore/build-speed
git -C /home/oscar/Coding/StudioProjects/headunit-revived rev-parse fork/chore/build-speed
# must print b97f96b94ac8c91cb8c9621a270dd022eb95e429
```

## 2. What this is and why

Round 1 measured the tester PC: a cold build took 244 s, a full rebuild took 270 s, and removing kapt saved 39 to 55 s. Every cold build swapped, with about 118 MB free at the lowest. The candidate carries three commits:

1. `4fe501ef`: removes the kapt processors (they had no input), turns off Jetifier (`checkJetifier` found no old support library), and turns on the Gradle build cache.
2. `c83647cb`: CI changes only. Nothing to test on the rig.
3. `b97f96b9`: Kotlin 1.9.22 to 2.4.20, and the Gradle wrapper 8.13 to 8.14.4. The first `./gradlew` call downloads Gradle 8.14.4 once. A1 does that before its timed build, so the download is not in its number.

On the dev machine, a cold compile of the main and test code fell from 113 s to 87 s, and all 2855 unit tests passed.

**The memory arms (M1, M2) test the swap.** Gradle runs with `-Xmx4096m`, and the Kotlin compiler daemon inherits the same limit unless `kotlin.daemon.jvmargs` sets its own (Kotlin docs, "Gradle compilation and caches"). That is two 4 GB processes on a 7.7 GiB PC. M1 caps both at 2 GB. M2 runs the Kotlin compiler inside the Gradle process, so there is one process. Both arms write only `~/.gradle/gradle.properties` on this PC, never the repo, and the brief deletes that file afterwards.

**The risk in Part S is at run time on old Android.** The `github` flavor allows API 16. The Kotlin 2.x compiler builds lambdas in a new way, and the build tools must convert them for old devices. Only a real device can prove that the app still runs. D-HP is the oldest unit on the rig (API 17).

**If the change broke nothing, Part S looks the same as a run on `main`.** That is the expected PASS. Part S looks for a crash or a dead session, not for an improvement.

## 3. What is different about this round

- **Round 1's helper defects are fixed below.** Bash `time` replaces `/usr/bin/time`. `JAVA_HOME=/opt/android-studio/jbr` is exported in the helpers. The `vmstat` sum skips header rows. `th_pkg` uses an absolute path to `rig_thermal.sh`.
- **The host thermal gate applies to every build**: cool to 70C before each build, use `--max-workers=2` on the raw commands, and log temperature and throttle. **This brief overrides `rig-quirks/topics/tooling.md` on one point, on purpose:** do not re-run a build only because it reached 90C. Round 1 reached 90 to 93C on every cold build with `throttle_delta=0`, so a re-run measures the same thing. Re-run once only if `throttle_delta` is above 0.
- **Leave the PC's load as found** and record it in A0.
- **Noise:** round 1's two cold repeats differed by up to 15 s. Treat a difference under 20 s between two cold builds as no difference.
- **Part S has one hand step:** turn on D-MOTO's Android Auto head unit server. It is a developer toggle on the phone, so no script reaches it.
- **D-HP cannot use Native AA** (no WiFi Direct). It runs in Headunit Server mode, as in `pr-1045-framing-round1` O2. In that mode the app connects to the server by itself. **Send `ACTION_CONNECT` only if no connection forms in 45 s**: that round showed the verb preempts an automatic socket and wedges the server.
- **D-HP's USB link is flaky, and its logcat capture has died silently in long runs** (`rig-quirks/units/D-HP.md`). The run checks the capture at the end of each hold.

## 4. Settings keys (Part S only)

`put_keys` (section 5) writes them with the app stopped, from the unit's backup, and reads them back. It works on D-HP (`run-as`) and on D-HU (root), and it needs no `sed` on the unit.

| Key | Element |
|---|---|
| mode | `<int name="wifi-connection-mode" value="1" />` |
| log level | `<int name="log-level" value="2" />` (INFO) |
| onboarding | `<int name="onboarding-version" value="2" />` |
| deleted | `native-aa-wireless`, `wifi-launcher-mode`, `video-profile-starvation-cap`, `debug-video-fault-injection`, `debug-video-fault-rate`, `debug-video-fault-budget` |

**Read and quote, never write:** `use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`, `view-mode`, `video-codec`, `resolutionId`.

## 5. Helpers

Save the block below as `/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh`, and the Python block after it as `/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_edit_prefs.py`. Start every call with:

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
```

```bash
# bs2_env.sh
export JAVA_HOME=/opt/android-studio/jbr
SCRIPTS=/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts
REPO=/home/oscar/Coding/StudioProjects/headunit-revived
PROBE=/home/oscar/Coding/StudioProjects/ohu-buildprobe
OUT=$HOME/build-speed-round2; mkdir -p "$OUT"
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
DHP=CNU350BGBJ; DHU=27870808938846; PH=ZY22GC3BM4      # D-HP, D-HU, D-MOTO
CAND=b97f96b94ac8c91cb8c9621a270dd022eb95e429

# Host thermal, from ptr_lib.sh (rig-quirks/topics/tooling.md), with an absolute rig_thermal.sh path
th_pkg() { local r=$SCRIPTS/../rig_thermal.sh; if [ -x "$r" ]; then "$r" status | grep -o ' pkg=[0-9]*' | head -1 | cut -d= -f2; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
th_report() { awk -F'[ =C]+' '{p=$3; t=$5; if(p>m)m=p; if(NR==1)t0=t} END{print "thermal_max=" m "C throttle_delta=" t-t0}' "$OUT/$1.thermal"; }

# run_build <RUN> <dir> <command...> : cool to 70C, watch thermal and memory, time the command
run_build() { local run=$1 dir=$2; shift 2
  th_wait 70 || { echo "$(date +%T) HOST_TOO_HOT $run" | tee -a "$OUT/thermal.log"; return 3; }
  th_watch "$OUT/$run.thermal" & local thp=$!
  vmstat -S M 5 > "$OUT/$run.vmstat" & local vmp=$!
  echo "$(date +%T) $run start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"
  local TIMEFORMAT="WALL_S=%R"
  { time ( cd "$dir" && "$@" ) > "$OUT/$run.out" 2>&1 ; } 2>> "$OUT/$run.out"; local rc=$?
  kill $thp $vmp 2>/dev/null
  echo "$run rc=$rc $(grep -o 'WALL_S=[0-9.]*' "$OUT/$run.out" | tail -1) $(th_report $run)" | tee -a "$OUT/summary.txt"
  awk '$1 ~ /^[0-9]+$/ {si+=$7; so+=$8; if(mf==""||$4<mf)mf=$4} END{print "'$run' swap_in_MB=" si*5 " swap_out_MB=" so*5 " min_free_MB=" mf}' \
    "$OUT/$run.vmstat" | tee -a "$OUT/summary.txt"; }

# prof <RUN> <dir> : top tasks from the newest Gradle profile in <dir>
prof() { local f; f=$(ls -t "$2"/build/reports/profile/*.html | head -1); cp "$f" "$OUT/$1.profile.html"
  python3 -I - "$f" <<'EOF' | tee "$OUT/$1.tasks.txt"
import re,sys
t=open(sys.argv[1]).read()
for k in ('Total Build Time','Configuring Projects','Task Execution'):
    m=re.search(k+r'</td>\s*<td[^>]*>([^<]+)',t); print(k+':', m.group(1) if m else '?')
rows=re.findall(r'<td class="indentPath">(:[^<]+)</td>\s*<td class="numeric">([^<]+)</td>',t)
def sec(v):
    m=re.match(r'(?:(\d+)m)?([\d.]+)s',v); return (int(m.group(1) or 0)*60+float(m.group(2))) if m else 0
for a,b in sorted(rows,key=lambda r:-sec(r[1]))[:12]: print(f'{sec(b):8.1f}s {a}')
EOF
}

# stop_daemons : stop Gradle and any Kotlin compiler daemon, so new JVM settings apply
stop_daemons() { ( cd "$PROBE" 2>/dev/null || cd "$REPO"; ./gradlew --stop >/dev/null 2>&1 ); pkill -f KotlinCompileDaemon; sleep 3
  pgrep -f "GradleDaemon|KotlinCompileDaemon" >/dev/null && echo DAEMONS-STILL-UP || echo daemons-stopped; }

# Part S
send()  { local s=$1 a=$2; shift 2; adb -s "$s" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
mark()  { send "$1" ACTION_LOG_MARKER --es text "$2" >/dev/null; sleep 1; }
wait_for() { local f=$1 pat=$2 t=${3:-90} i=0; while [ $i -lt $t ]; do grep -aqF -e "$pat" "$f" && return 0; sleep 1; i=$((i+1)); done; return 1; }
readkey() { adb -s "$1" shell run-as $PKG cat shared_prefs/settings.xml 2>/dev/null | tr -d '\r' | grep -o "name=\"$2\"[^/]*" || echo "$2 absent"; }
# backup <serial> <name> : copy the unit's settings.xml to $OUT/settings-backup-<name>.xml
backup() { adb -s "$1" shell am force-stop $PKG; adb -s "$1" shell run-as $PKG cat shared_prefs/settings.xml | tr -d '\r' > "$OUT/settings-backup-$2.xml"
  grep -q '</map>' "$OUT/settings-backup-$2.xml" && echo "backup-ok $2" || echo "BACKUP-FAILED $2"; }
# push_prefs <serial> <file> : install a settings.xml with the app stopped (root if the unit has it, else run-as)
push_prefs() { adb -s "$1" shell am force-stop $PKG; adb -s "$1" push "$2" /data/local/tmp/settings.new >/dev/null
  if adb -s "$1" shell su -c id 2>/dev/null | grep -q 'uid=0'; then
    local own; own=$(adb -s "$1" shell su -c "stat -c %U:%G /data/data/$PKG" | tr -d '\r')
    adb -s "$1" shell su -c "cp /data/local/tmp/settings.new /data/data/$PKG/shared_prefs/settings.xml; chown $own /data/data/$PKG/shared_prefs/settings.xml; chmod 660 /data/data/$PKG/shared_prefs/settings.xml"
  else adb -s "$1" shell run-as $PKG cp /data/local/tmp/settings.new shared_prefs/settings.xml; fi; }
# put_keys <serial> <name> OPS... : from the backup, apply OPS (key=value writes an int, -key deletes), install, read back
put_keys() { local s=$1 n=$2; shift 2
  python3 -I "$SCRIPTS/bs2_edit_prefs.py" "$OUT/settings-backup-$n.xml" "$OUT/settings-$n.new" "$@"
  push_prefs "$s" "$OUT/settings-$n.new"
  for k in wifi-connection-mode log-level onboarding-version native-aa-wireless wifi-launcher-mode video-profile-starvation-cap \
           use-aac-audio audio-latency-multiplier audio-queue-capacity view-mode video-codec resolutionId; do readkey "$s" "$k"; done; }
S_OPS="wifi-connection-mode=1 log-level=2 onboarding-version=2 -native-aa-wireless -wifi-launcher-mode -video-profile-starvation-cap -debug-video-fault-injection -debug-video-fault-rate -debug-video-fault-budget"

cap_start() { # cap_start <RUN> <hu-serial>
  adb -s "$2" logcat -G 16M 2>/dev/null; adb -s "$2" logcat -c; adb -s "$PH" logcat -c
  setsid nohup stdbuf -oL adb -s "$2" logcat -v time > "$OUT/$1.hu.logcat" 2>&1 &
  setsid nohup stdbuf -oL adb -s "$PH" logcat -v time > "$OUT/$1.ph.logcat" 2>&1 & sleep 1; }
cap_alive() { pgrep -f "adb -s $1 logcat -v time" >/dev/null && echo CAP-ALIVE || echo CAP-DEAD; }
cap_stop() { pkill -f "adb -s $1 logcat -v time"; pkill -f "adb -s $PH logcat -v time"; sleep 1; }
# end_session <serial> : the section 3a reset
end_session() { send "$1" ACTION_DISCONNECT >/dev/null; sleep 6
  adb -s "$1" shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 3; adb -s "$1" shell am force-stop $PKG; }
# win <file> <run> : lines between the run's start and end markers
win() { LC_ALL=C awk -v s="$2-start" -v e="$2-end" 'index($0,"AutomationMarker: " s){f=1} f{print} f&&index($0,"AutomationMarker: " e){exit}' "$1"; }
# smoke_counts <RUN> : every count Part S grades
smoke_counts() { local R=$1 H=$OUT/$1.hu.logcat P=$OUT/$1.ph.logcat
  echo "$R ssl=$(grep -acF 'SSL handshake complete' $H)"
  echo "$R windows=$(win $H $R | grep -acF 'Throughput over')"
  echo "$R rendered_nonzero=$(win $H $R | grep -aF 'Throughput over' | grep -acE 'rendered=[1-9]')"
  echo "$R quit=$(win $H $R | grep -acF 'AapTransport quitting')"
  for s in 'FATAL EXCEPTION' NoClassDefFoundError NoSuchMethodError VerifyError ClassNotFoundException UnsatisfiedLinkError; do
    echo "$R $s=$(grep -acF "$s" $H) ours=$(grep -aA2 -F "$s" $H | grep -acF "$PKG")"; done
  echo "$R ph_lines=$(wc -l < $P)"
  echo "$R phone_critical=$(grep -acF 'Critical error' $P)"; }
```

```python
# bs2_edit_prefs.py <in> <out> OPS... : key=value writes <int>, -key deletes every element form of key
import re,sys
src,dst=sys.argv[1],sys.argv[2]; t=open(src).read()
for op in sys.argv[3:]:
    k,_,v=op.partition('=')
    n=re.escape(k.lstrip('-'))
    t=re.sub(r'<set name="'+n+r'"\s*>.*?</set>|<set name="'+n+r'"\s*/>|<string name="'+n+r'">[^<]*</string>|<[a-z]+ name="'+n+r'"[^>]*/>','',t,flags=re.S)
    if not k.startswith('-'):
        t=t.replace('</map>','<int name="%s" value="%s" /></map>'%(k,v))
open(dst,'w').write(t)
```

## 6. The lines that decide every run

Each app string below was checked with `grep -F` on `b97f96b9`. The crash lines and `Critical error` come from Android.

| Line | File | Use |
|---|---|---|
| `BUILD SUCCESSFUL in` | `$OUT/<RUN>.out` | Part A: the build finished |
| `WALL_S=`, `thermal_max=`, `throttle_delta=`, `swap_in_MB=`, `swap_out_MB=`, `min_free_MB=` | `$OUT/summary.txt` | Part A: time, heat, memory |
| `compileGithubDebugKotlin FROM-CACHE` | `$OUT/A4c.out` | A4: the cache restored the compile |
| `"commit":"b97f96b94ac8"` | `$OUT/<RUN>.state.txt` | Part S: the candidate is the build installed |
| `SSL handshake complete` | `$OUT/<RUN>.hu.logcat` | Part S: a session formed. It matches the INFO and the DEBUG line |
| `Throughput over` | `$OUT/<RUN>.hu.logcat`, marker window | Part S: video windows, one per 5 s |
| `AapTransport quitting` | `$OUT/<RUN>.hu.logcat`, marker window | Part S: the session ended |
| `FATAL EXCEPTION`, `NoClassDefFoundError`, `NoSuchMethodError`, `VerifyError`, `ClassNotFoundException`, `UnsatisfiedLinkError` | `$OUT/<RUN>.hu.logcat`, whole file | Part S: a crash or a missing class |
| `Critical error` | `$OUT/<RUN>.ph.logcat`, whole file | Part S: Android Auto on the phone rejected the session |

## 7. Runs

### Part A: build times (host only, no device)

#### A0: setup notes

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
{ free -h; swapon --show; ps -eo rss,comm --sort=-rss | head -6; ls -la ~/.gradle/gradle.properties 2>&1; } > "$OUT/A0.txt"; cat "$OUT/A0.txt"
git -C "$REPO" status --porcelain | head     # must print nothing; if not, stop and report
```

If `~/.gradle/gradle.properties` exists, stop and report its contents: M1 and M2 would overwrite it. Round 1 found none.

#### A1: the rig's build, cold daemon (the point of Part A)

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
git -C "$REPO" fetch fork chore/build-speed && git -C "$REPO" checkout --detach $CAND
( cd "$REPO" && ./gradlew --version | grep "^Gradle" )     # must print Gradle 8.14.4 (downloads it the first time)
stop_daemons
run_build A1 "$SCRIPTS" ./build_hur.sh
APK=$(ls -t "$REPO"/app/build/outputs/apk/github/debug/*.apk | head -1); cp "$APK" "$OUT/cand-b97f96b9.apk"; md5sum "$OUT/cand-b97f96b9.apk" | tee -a "$OUT/summary.txt"
```

PASS: `rc=0`. Compare `WALL_S` with round 1's R1, 243.6 s.

#### A2: full rebuild, cold daemon, profiled

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
git -C "$REPO" worktree add --detach "$PROBE" $CAND
cp "$REPO/local.properties" "$PROBE/" 2>/dev/null
stop_daemons
run_build A2 "$PROBE" ./gradlew :app:assembleGithubDebug --profile --max-workers=2 --console=plain --rerun-tasks --no-build-cache
prof A2 "$PROBE"
```

PASS: `BUILD SUCCESSFUL`, and `A2.tasks.txt` has no `kapt` row. Compare `WALL_S` and `Total Build Time` with round 1's R2 (270.7 s, 4m29.97s) and R6 (231.3 s, 3m50.62s).

#### M1: A2 with both processes capped at 2 GB

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
printf 'org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g -XX:+UseParallelGC\nkotlin.daemon.jvmargs=-Xmx2g\n' > ~/.gradle/gradle.properties
stop_daemons
run_build M1 "$PROBE" ./gradlew :app:assembleGithubDebug --profile --max-workers=2 --console=plain --rerun-tasks --no-build-cache
prof M1 "$PROBE"
```

#### M2: A2 with the Kotlin compiler inside the Gradle process

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
printf 'org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g -XX:+UseParallelGC\nkotlin.compiler.execution.strategy=in-process\n' > ~/.gradle/gradle.properties
stop_daemons
run_build M2 "$PROBE" ./gradlew :app:assembleGithubDebug --profile --max-workers=2 --console=plain --rerun-tasks --no-build-cache
prof M2 "$PROBE"
rm ~/.gradle/gradle.properties; stop_daemons; ls ~/.gradle/gradle.properties 2>&1   # must say "No such file"
```

M1 and M2 PASS: `BUILD SUCCESSFUL`. They have no FAIL: they report `WALL_S`, `Total Build Time` and the swap figures against A2. Run M2's last line even if M1 or M2 failed.

#### A3: unit tests, warm daemon

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
run_build A3 "$PROBE" ./gradlew :app:testGithubDebugUnitTest --max-workers=2 --console=plain
cat "$PROBE"/app/build/test-results/testGithubDebugUnitTest/*.xml \
  | grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  | awk -F'"' '{t+=$2;f+=$6;e+=$8} END{print "A3 tests=" t " failures=" f " errors=" e}' | tee -a "$OUT/summary.txt"
```

PASS: `BUILD SUCCESSFUL`, `tests=2855`, `failures=0`, `errors=0`. Compare `WALL_S` with round 1's R5, 76.0 s. The first A3 build starts a new daemon, because M2 stopped the last one; say so beside the number.

#### A4: the build cache on a revisited state, warm daemon

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
F=app/src/main/java/com/andrerinas/openheadunit/utils/AppLog.kt
( cd "$PROBE" && echo "// build-speed probe" >> "$F" )
run_build A4a "$PROBE" ./gradlew :app:assembleGithubDebug --max-workers=2 --console=plain
( cd "$PROBE" && git checkout -- "$F" && git status --porcelain )     # must print nothing
run_build A4c "$PROBE" ./gradlew :app:assembleGithubDebug --max-workers=2 --console=plain
echo "A4c from_cache=$(grep -c 'compileGithubDebugKotlin FROM-CACHE' "$OUT/A4c.out")" | tee -a "$OUT/summary.txt"
```

PASS: the restore prints nothing, and `from_cache=1`. Report `WALL_S` for A4a (an edit; round 1's R4 was 19.4 s) and A4c (back to a state already built).

#### Cleanup of Part A

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
stop_daemons; git -C "$REPO" worktree remove --force "$PROBE"; git -C "$REPO" worktree prune
```

### Part S: smoke on API 17 and a modern unit

**Precondition (hand step, then verify).** Turn on D-MOTO's Android Auto head unit server (Developer settings, "Start head unit server"). Then:

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
PHIP=$(adb -s $PH shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2); echo "PHIP=$PHIP"
timeout 3 bash -c "echo > /dev/tcp/$PHIP/5277" && echo REACHABLE || echo DOWN
```

If it prints DOWN after one restart of the server, S1 and S2 are **UNTESTABLE**. D-POCO's server is known down, so do not try it. D-HP and D-HU both use this `PHIP`.

#### S1: D-HP (API 17), Headunit Server, 5 minutes (the point of the round)

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
backup $DHP dhp                                        # must print backup-ok dhp
adb -s $DHP install -r "$OUT/cand-b97f96b9.apk"
put_keys $DHP dhp $S_OPS | tee "$OUT/S1.keys.txt"      # mode 1, log-level 2, onboarding 2, the rest absent
send $DHP ACTION_QUERY_STATE | tee "$OUT/S1.state.txt"
adb -s $DHP shell am force-stop $PKG; cap_start S1 $DHP
adb -s $DHP shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity
wait_for "$OUT/S1.hu.logcat" 'SSL handshake complete' 45 && echo BRINGUP-OK || {
  send $DHP ACTION_CONNECT --es ip $PHIP >/dev/null
  wait_for "$OUT/S1.hu.logcat" 'SSL handshake complete' 120 && echo BRINGUP-OK-AFTER-CONNECT || echo BRINGUP-FAIL; }
```

If `BRINGUP-FAIL`, run `cap_stop $DHP; end_session $DHP`, restart D-MOTO's server once (hand step), and repeat this block once. Otherwise:

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
mark $DHP S1-start; sleep 300; mark $DHP S1-end; cap_alive $DHP
cap_stop $DHP; end_session $DHP
smoke_counts S1 | tee "$OUT/S1.counts.txt"
push_prefs $DHP "$OUT/settings-backup-dhp.xml"; readkey $DHP wifi-connection-mode
```

**PASS:** `S1.state.txt` contains `"commit":"b97f96b94ac8"`; `BRINGUP-OK` or `BRINGUP-OK-AFTER-CONNECT`; `CAP-ALIVE`; `ssl` at least 1; `windows` at least 50; `rendered_nonzero` at least 50; `quit` 0; every `ours=` 0; `ph_lines` above 0; `phone_critical` 0.
**FAIL:** any `ours=` above 0 (quote the first 15 lines after it), `BRINGUP-FAIL` twice, or `quit` above 0.
**INCONCLUSIVE:** `CAP-DEAD`, `ph_lines` 0, or a crash count above 0 with every `ours=` at 0 (another process; quote it).

#### S2: D-HU, Headunit Server, 5 minutes (modern control)

Run both S1 blocks again with these substitutions: `$DHU` for `$DHP`, `dhu` for `dhp`, and `S2` for `S1`. `push_prefs` uses root on D-HU automatically. The last line of the second block restores D-HU's own settings, which other threads depend on. Same PASS, FAIL and INCONCLUSIVE.

### Stop rule

Stop the round after three `HOST_TOO_HOT` results, or after two `BRINGUP-FAIL` results on one unit. Mark what is left UNTESTABLE or FAIL as written above. Always run M2's last line and both `push_prefs ... settings-backup-*` restores before you stop.

## 8. Do not re-run

Round 1's R0 to R6 on `77914f18`. Use their numbers as the baseline.

## 9. Report back

Write `build-speed-round2-results.md` (TESTING-TEMPLATE.md §7) and upload `$OUT` as `build-speed-round2-captures.zip` to `rig-evidence-build-speed`.

1. **A1 against R1, and A2 against R2 and R6** (`WALL_S`, `Total Build Time`), with the top 12 tasks of A2.
2. **M1 and M2 against A2:** `WALL_S`, `Total Build Time`, swap in and out, minimum free memory, and the `compileGithubDebugKotlin` row.
3. **A3** test count and `WALL_S`. **A4a and A4c** `WALL_S`, and `from_cache`.
4. **S1 on D-HP and S2 on D-HU:** every line of `S<n>.counts.txt` and the bring-up result. S1 decides whether Kotlin 2.x can ship for API 16 to 20.
5. Per build: `thermal_max` and `throttle_delta`.
