# build-speed, round 3 brief

Host only. **No head unit, no phone, no APK install.** One standing change to the tester PC and one timed build that proves it took effect.

## 1. Build and baseline

- Candidate: `fork/chore/build-speed` at `44c75139`. It is round 2's `b97f96b9` squashed into one commit, with an identical tree, so round 2's numbers still apply.
- Baseline: round 2's A1 (297.3 s, the first build of the candidate), A2 (177.3 s, cold full rebuild) and M2 (158.7 s, the setting below).

## 2. What this is and why

Round 2 measured two memory settings against the default. With the default, the Kotlin compiler daemon inherits Gradle's `-Xmx4096m`, so a cold build runs two 4 GB processes on a 7.7 GiB PC and swaps. M2 (the Kotlin compiler inside the Gradle process, a 3 GB Gradle heap, the parallel collector) cut 18.6 s from the cold rebuild, removed the swap, and kept 1202 MB free at the lowest point. **This round makes M2 the standing setting on this PC.** It lives only in `~/.gradle/gradle.properties`, never in the repo.

The timed build also answers one open question from round 2: A1 took 297 s, but it was the first build with Kotlin 2.4.20 on this PC. The Kotlin artifacts are cached now, so this build shows what a first build of a new commit costs from here on.

## 3. What is different about this round

- **The setting stays after the round.** Do not delete `~/.gradle/gradle.properties` at the end. Every later build on this PC uses it.
- **The timed build is a plain `./gradlew assembleGithubDebug`**, the same Gradle call `build_hur.sh` makes. It does not run `build_hur.sh`, because that script deletes the APKs in `apks/` that other queued rounds may need.
- It runs in a scratch worktree, so the shared checkout is not touched.
- The host thermal gate applies (cool to 70C first). As in round 2, re-run only if `throttle_delta` is above 0.

## 4. Settings keys

None on any device.

## 5. Helpers

Reuse `/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh` from round 2. Round 2 pointed its `REPO` at a scratch worktree that no longer exists, so every call sets `REPO` again. Any worktree of the app repository works for `git worktree add`. Start every call with:

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh
REPO=/home/oscar/Coding/StudioProjects/headunit-revived
OUT=$HOME/build-speed-round3; mkdir -p "$OUT"
SCR3=/home/oscar/Coding/StudioProjects/ohu-bs3
```

## 6. The lines that decide every run

| Line | File | Use |
|---|---|---|
| `org.gradle.jvmargs=-Xmx3g` and `kotlin.compiler.execution.strategy=in-process` | `~/.gradle/gradle.properties` | T1: the setting is written |
| `xmx=-Xmx3g` | `$OUT/T2.daemons` | T2: the Gradle daemon took the setting |
| `kotlin_daemons=0` | `$OUT/T2.daemons`, every row | T2: no separate Kotlin compiler process ran |
| `BUILD SUCCESSFUL in`, `WALL_S=`, `swap_in_MB=`, `swap_out_MB=`, `min_free_MB=` | `$OUT/T2.out`, `$OUT/summary.txt` | T2: result, time, memory |

## 7. Runs

### T1: write the standing setting

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh; OUT=$HOME/build-speed-round3; mkdir -p "$OUT"
ls -la ~/.gradle/gradle.properties 2>&1 | tee "$OUT/T1.before.txt"
```

If the file exists, stop and report its contents: something else wrote it after round 2. Otherwise:

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh; OUT=$HOME/build-speed-round3
printf '%s\n' \
  '# Standing tester PC setting from build-speed round 2 (M2): one JVM, no swap on a 7.7 GiB host.' \
  'org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g -XX:+UseParallelGC' \
  'kotlin.compiler.execution.strategy=in-process' > ~/.gradle/gradle.properties
cat ~/.gradle/gradle.properties | tee "$OUT/T1.after.txt"
```

PASS: the file holds exactly the three lines above.

### T2: a cold build of a new checkout with the setting (the point of the round)

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh; REPO=/home/oscar/Coding/StudioProjects/headunit-revived; OUT=$HOME/build-speed-round3; SCR3=/home/oscar/Coding/StudioProjects/ohu-bs3
git -C "$REPO" fetch fork chore/build-speed
git -C "$REPO" worktree add --detach "$SCR3" 44c751394ee88c90f26f2fb865ca951e20f0da06
cp "$REPO/local.properties" "$SCR3/" 2>/dev/null
PROBE=$SCR3 stop_daemons
( while :; do echo "$(date +%T) xmx=$(pgrep -af '[G]radleDaemon' | grep -o -- '-Xmx[0-9a-zA-Z]*' | head -1) kotlin_daemons=$(pgrep -fc '[K]otlinCompileDaemon')"; sleep 10; done ) > "$OUT/T2.daemons" 2>&1 & SAMP=$!
run_build T2 "$SCR3" ./gradlew assembleGithubDebug
kill $SAMP
grep -c 'BUILD SUCCESSFUL in' "$OUT/T2.out"
grep -c 'xmx=-Xmx3g' "$OUT/T2.daemons"; grep -vc 'kotlin_daemons=0' "$OUT/T2.daemons"
```

**PASS:** `rc=0` and one `BUILD SUCCESSFUL`; at least one `xmx=-Xmx3g` row; and the last command prints `0` (every row read `kotlin_daemons=0`).
**FAIL:** the build fails, no row reads `-Xmx3g`, or any row shows a Kotlin compiler daemon. Quote the first such row.

Report `WALL_S` against round 2's A1 (297.3 s), A2 (177.3 s) and M2 (158.7 s), and the swap and minimum free figures.

### Cleanup

```bash
source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/bs2_env.sh; REPO=/home/oscar/Coding/StudioProjects/headunit-revived; SCR3=/home/oscar/Coding/StudioProjects/ohu-bs3
PROBE=$SCR3 stop_daemons; git -C "$REPO" worktree remove --force "$SCR3"; git -C "$REPO" worktree prune
cat ~/.gradle/gradle.properties      # must still hold the three lines
```

### Stop rule

Stop after three `HOST_TOO_HOT` results. If T2 fails, delete `~/.gradle/gradle.properties`, run `stop_daemons`, and report.

## 8. Do not re-run

Round 2's Part A and Part S.

## 9. Report back

Write `build-speed-round3-results.md` and upload `$OUT` as `build-speed-round3-captures.zip` to `rig-evidence-build-speed`. Then add one line to `rig-quirks/topics/tooling.md` saying the PC now carries this `~/.gradle/gradle.properties`, so later rounds know it is there.

1. T1: the file's final contents.
2. T2: `WALL_S`, `thermal_max`, `throttle_delta`, swap in and out, minimum free memory, and the count of `-Xmx3g` and `kotlin_daemons=0` rows.
