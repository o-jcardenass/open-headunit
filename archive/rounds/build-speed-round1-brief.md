# build-speed, round 1 brief

A host-only measurement round. **No head unit, no phone, no APK install, no app.** Every run is a Gradle build on the tester PC. Nothing here touches Android Auto, so the phone-capture rule does not apply.

## 1. Build and baseline

- Commit: `main` `77914f18` (merge of PR #1080). It is on `origin/main`.
- No candidate branch. R6 applies a three-line local patch in a scratch worktree, and nothing is committed.
- History was not rewritten.

```bash
git fetch origin
git rev-parse 77914f18     # must print 77914f18...
```

## 2. What this is and why

One APK build takes up to 10 min on this PC. The same build takes 3 min on GitHub CI. We want to know where the extra time goes before we change the build.

Measured so far, off this PC (the full diagnosis is in the handoff folder, `build-speed-diagnosis.md`):

- A full rebuild of the main and test code on the dev machine takes 113 s of task time.
- `compileGithubDebugKotlin` is the largest task at 57 s.
- kapt takes 22 s and does no work. No annotation it handles exists in the code.
- Removing kapt on the dev machine compiled cleanly, and all 2855 unit tests passed.

Open questions that only this PC can answer:

1. Does `build_hur.sh` run `clean`, `--no-daemon` or `--rerun-tasks`, so that every build starts cold?
2. Does the 10 min figure include a second Gradle run (`run_unit_tests.sh`)?
3. Does the PC swap? Gradle gets `-Xmx4096m`, and the Kotlin daemon inherits the same heap.
4. How much do CPU speed and thermal throttling cost?
5. What does the kapt removal save here?

## 3. What is different about this round

- No adb. Do not connect or touch any device.
- **The host thermal gate applies to every build** (`rig-quirks/topics/tooling.md`). Cool to 70C before each build, build with `--max-workers=2`, and log temperature and the throttle counter every 20 s. Re-run a build once if it throttled or reached 90C.
- **Stop rule:** if `th_wait` fails three times (30 min hot each), stop the round and mark the rest UNTESTABLE (host thermal).
- R2 to R6 run in a scratch worktree, `../ohu-buildprobe`, so the shared checkout is not edited.

## 4. Settings keys

None. No `settings.xml` is read or written.

## 5. Setup (once, before R0)

Paste this block into the shell that runs every later step. `REPO` is the app checkout that `build_hur.sh` builds. R0 names it; set it from R0's output before R1.

```bash
OUT=~/build-speed-round1; mkdir -p "$OUT"
SCRIPTS=<path to hur-wifi-test-scripts>
REPO=<app checkout used by build_hur.sh, from R0>
PROBE=$(dirname "$REPO")/ohu-buildprobe

# Host thermal, from ptr_lib.sh (rig-quirks/topics/tooling.md)
th_pkg() { if [ -x ../rig_thermal.sh ]; then ../rig_thermal.sh status | grep -o ' pkg=[0-9]*' | head -1 | cut -d= -f2; return; fi
  for z in /sys/class/hwmon/hwmon*; do [ "$(cat $z/name 2>/dev/null)" = coretemp ] && { echo $(( $(cat $z/temp1_input) / 1000 )); return; }; done
  echo $(( $(cat /sys/class/thermal/thermal_zone0/temp) / 1000 )); }
th_thr() { cat /sys/devices/system/cpu/cpu0/thermal_throttle/package_throttle_count 2>/dev/null || echo 0; }
th_wait() { local lim=${1:-75} n=0; while [ "$(th_pkg)" -ge "$lim" ]; do n=$((n+1)); [ $n -ge 90 ] && return 1; sleep 10; done; }
th_watch() { while :; do echo "$(date +%T) pkg=$(th_pkg)C throttle_pkg=$(th_thr)"; sleep 20; done >> "$1"; }
th_report() { awk -F'[ =C]+' '{p=$3; t=$5; if(p>m)m=p; if(NR==1)t0=t} END{print "thermal_max=" m "C throttle_delta=" t-t0}' "$OUT/$1.thermal"; }

# One build: cool to 70C, watch thermal and memory, time it.
# usage: run_build <RUN> <dir> <command...>
run_build() { local run=$1 dir=$2; shift 2
  th_wait 70 || { echo "$(date +%T) HOST_TOO_HOT $run" | tee -a "$OUT/thermal.log"; return 3; }
  th_watch "$OUT/$run.thermal" & local thp=$!
  vmstat -S M 5 > "$OUT/$run.vmstat" & local vmp=$!
  echo "$(date +%T) $run start pkg=$(th_pkg)C throttle_pkg=$(th_thr)" >> "$OUT/thermal.log"
  ( cd "$dir" && /usr/bin/time -f "WALL_S=%e MAXRSS_KB=%M" "$@" ) > "$OUT/$run.out" 2>&1; local rc=$?
  kill $thp $vmp 2>/dev/null
  echo "$run rc=$rc $(grep -o 'WALL_S=[0-9.]*' "$OUT/$run.out") $(th_report $run)" | tee -a "$OUT/summary.txt"
  awk 'NR>2 {si+=$7; so+=$8; if($4<mf||mf=="")mf=$4} END{print "'$run' swap_in_MB=" si*5 " swap_out_MB=" so*5 " min_free_MB=" mf}' "$OUT/$run.vmstat" | tee -a "$OUT/summary.txt"; }

# Top tasks from the newest Gradle profile in <dir>.
# usage: prof <RUN> <dir>
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
```

## 6. The lines that decide every run

All of them are in the run's own files under `$OUT`. None comes from the app.

| Line | File | Meaning |
|---|---|---|
| `BUILD SUCCESSFUL in` | `$OUT/<RUN>.out` | Gradle finished. A `BUILD FAILED` makes the run FAIL. |
| `WALL_S=` | `$OUT/summary.txt` | Wall time of the whole command, in seconds. |
| `thermal_max=` / `throttle_delta=` | `$OUT/summary.txt` | Peak package temperature and throttle events in the run. |
| `swap_in_MB=` / `swap_out_MB=` / `min_free_MB=` | `$OUT/summary.txt` | Memory pressure in the run. |
| `Total Build Time:` and the top task rows | `$OUT/<RUN>.tasks.txt` | Gradle's own task times. |
| `kaptGenerateStubsGithubDebugKotlin`, `kaptGithubDebugKotlin` | `$OUT/<RUN>.tasks.txt` | kapt tasks. They must appear in R2 and must not appear in R6. |

## 7. Runs

### R0: inventory (no build)

```bash
{ echo "== host"; uname -a; grep -m1 "model name" /proc/cpuinfo; nproc; free -h; swapon --show
  echo "== java"; java -version 2>&1; echo "JAVA_HOME=$JAVA_HOME"
  echo "== gradle home props"; cat ~/.gradle/gradle.properties 2>/dev/null || echo none
  echo "== disks"; df -h ~ ~/.gradle 2>/dev/null
  echo "== scripts"; ls -la "$SCRIPTS"
} > "$OUT/R0.txt" 2>&1
for s in build_hur.sh run_unit_tests.sh; do echo "===== $s"; cat "$SCRIPTS/$s"; done > "$OUT/R0.scripts.txt" 2>&1
grep -nE "gradlew|clean|--no-daemon|--rerun-tasks|--max-workers|--offline|--stop|cd " "$SCRIPTS"/build_hur.sh "$SCRIPTS"/run_unit_tests.sh | tee "$OUT/R0.flags.txt"
```

Record in the results: the full text of both scripts (replace any password with `<REDACTED>`), the `REPO` path the build script uses, and every line of `R0.flags.txt`.

PASS: both scripts are found and `REPO` is named. Otherwise UNTESTABLE, and stop.

### R1: the build exactly as the rig does it today (the symptom)

```bash
git -C "$REPO" status --porcelain | head      # must be empty; if not, stop and report
git -C "$REPO" checkout --detach 77914f18
run_build R1 "$SCRIPTS" ./build_hur.sh
```

If `build_hur.sh` needs arguments, use the ones the rig always uses and record them.

Report `WALL_S`, `thermal_max`, `throttle_delta`, `swap_in_MB`, `swap_out_MB` and `min_free_MB`. This is the 10 min figure under measurement. PASS: the build succeeds. No time limit decides it.

### R2: cold daemon, full rebuild, profiled

```bash
git -C "$REPO" worktree add --detach "$PROBE" 77914f18
cp "$REPO/local.properties" "$PROBE/" 2>/dev/null
( cd "$PROBE" && ./gradlew --stop )
run_build R2 "$PROBE" ./gradlew :app:assembleGithubDebug --profile --max-workers=2 --console=plain
prof R2 "$PROBE"
```

PASS: `BUILD SUCCESSFUL`, and `R2.tasks.txt` lists both kapt tasks.

### R3: warm daemon, no change (the floor)

```bash
run_build R3 "$PROBE" ./gradlew :app:assembleGithubDebug --profile --max-workers=2 --console=plain
prof R3 "$PROBE"
```

PASS: `BUILD SUCCESSFUL`. Report `WALL_S` and `Total Build Time`.

### R4: warm daemon, one Kotlin line changed (a normal edit)

```bash
F=app/src/main/java/com/andrerinas/openheadunit/utils/AppLog.kt
( cd "$PROBE" && test -f "$F" && echo "// build-speed probe" >> "$F" )
run_build R4 "$PROBE" ./gradlew :app:assembleGithubDebug --profile --max-workers=2 --console=plain
prof R4 "$PROBE"
( cd "$PROBE" && git checkout -- "$F" && git status --porcelain )   # must print nothing
```

PASS: `BUILD SUCCESSFUL`, and the last command prints nothing.

### R5: warm daemon, unit tests, profiled

```bash
run_build R5 "$PROBE" ./gradlew :app:testGithubDebugUnitTest --profile --max-workers=2 --console=plain
prof R5 "$PROBE"
```

PASS: `BUILD SUCCESSFUL`. Report `WALL_S` and the `testGithubDebugUnitTest` row.

### R6: kapt removed, cold daemon, full rebuild (the point of the round)

```bash
( cd "$PROBE" && sed -i -e '/^ *kotlin("kapt")$/d' \
    -e '/^ *kapt("androidx\.lifecycle:lifecycle-compiler/d' \
    -e '/^ *kapt("com\.github\.bumptech\.glide:compiler/d' app/build.gradle.kts \
  && git diff --stat && grep -c kapt app/build.gradle.kts )        # expect "3 deletions" and 0
( cd "$PROBE" && ./gradlew --stop )
run_build R6 "$PROBE" ./gradlew :app:assembleGithubDebug --profile --max-workers=2 --console=plain --rerun-tasks
prof R6 "$PROBE"
```

PASS: the diff shows 3 deletions and `grep -c` prints 0, the build reports `BUILD SUCCESSFUL`, and `R6.tasks.txt` has no `kapt` row. FAIL: a compile error. Quote the first `e:` line.

R2 and R6 run under the same conditions (cold daemon, full task run, warm dependency cache). So `R2 WALL_S - R6 WALL_S` is the kapt cost on this PC.

### Cleanup

```bash
( cd "$PROBE" && ./gradlew --stop )
git -C "$REPO" worktree remove --force "$PROBE"
git -C "$REPO" worktree prune
```

## 8. Do not re-run

Nothing. This is the first round of the thread.

## 9. Report back

Write `build-speed-round1-results.md` (TESTING-TEMPLATE.md §7). Upload `$OUT` as `build-speed-round1-captures.zip` to release `rig-evidence-build-speed`. Put these in a table:

1. **R1 `WALL_S`**, and whether `build_hur.sh` runs `clean`, `--no-daemon` or `--rerun-tasks`.
2. **R2, R3 and R4 `WALL_S`** (cold, warm floor, one edit).
3. **R2 minus R6** `WALL_S` and `Total Build Time` (the kapt saving here).
4. **Per run:** `thermal_max`, `throttle_delta`, `swap_in_MB`, `swap_out_MB` and `min_free_MB`.
5. **The top 12 task rows** of R2 and R6, copied from `.tasks.txt`.
