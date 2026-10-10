# build-speed: round 1 results

- Candidate: none (host-only measurement). Commit built: `main` `77914f18` (merge of the settings-defaults PR)
- Baseline: none
- Unit: the tester PC only (no device touched). Intel Core i7-3632QM at 2.2 GHz, 8 threads, 7.7 GiB RAM, 10 GiB swap file on an SSD, Debian 6.12
- Date: 2026-10-08

## Setup notes

1. **`/usr/bin/time` is not installed.** The brief's `run_build` called it and every build returned `rc=127` with no build run (two void attempts, `void1` and `void2` in the asset). `run_build` now wraps the command in bash's `time` with `TIMEFORMAT="WALL_S=%R MAXRSS_KB=na"`. Wall time is therefore recorded; **peak memory (`MAXRSS_KB`) is not available**. Nothing was installed.
2. **R2 to R6 need `JAVA_HOME`.** The shell's `JAVA_HOME` is empty. `build_hur.sh` exports `/opt/android-studio/jbr`, but the brief's plain `./gradlew` commands picked up the system Java 21 JRE, which has no compiler: R2's first attempt failed in `:app:compileGithubDebugJavaWithJavac` with `Toolchain installation '/usr/lib/jvm/java-21-openjdk-amd64' does not provide the required capabilities: [JAVA_COMPILER]` after 209.6 s (kept as `R2-nojavahome`). The round then exported `JAVA_HOME=/opt/android-studio/jbr` for every build, the same value the rig's scripts use. R1 ran through `build_hur.sh` and was never affected. A stray Kotlin compiler daemon from the failed attempt was killed by pid before the real R2.
3. **The `min_free_MB` the brief's `run_build` prints is wrong.** `vmstat` reprints its header inside the file, so the awk read a header word (`-----io----`) for some runs. The swap and free figures below were recomputed from the saved `.vmstat` files with numeric rows only (`$4` free, `$7` si, `$8` so, times 5 s). The `summary.txt` in the asset still carries the bad `min_free_MB` for R1, R2, R2b, R6 and R6b.
4. **The 90C rule.** R1, R2 and R6 reached 90C or more (90, 92 and 93C) with `throttle_delta=0` in every run. The brief says to re-run a build once if it throttled or reached 90C, so each of the three was re-run once: R1 as R1b, R2 as R2b, R6 as R6b. **R1b is not a second measurement of R1**: the daemon was warm and the tree unchanged, so it took 5.6 s (all tasks up to date). R1's cold time of 243.6 s stands alone. R2b and R6b are true repeats (both cold daemon, same worktree commit).
5. **R1's `BUILD SUCCESSFUL` line is gone.** `build_hur.sh` writes Gradle's output to `results/hur_gradle_out.txt`, which R1b overwrote. R1's success rests on `rc=0` and the script's own `Built and copied: com.andrerinas.headunitrevived_3.5.0-beta4_debug.apk` line in `R1.out` (kept in `R1-hot90/`).
6. **Load on the PC.** Spotify, Firefox and VS Code were open (the operator's statement) and were left alone. Android Studio was not open: the largest resident process before R1 was a Java process from `/opt/android-studio/jbr` (the JDK that `build_hur.sh` and the Gradle daemon use), which I first misread as the IDE. Before R1: `free` showed 6.2 GiB used, 454 MiB free, 1.5 GiB available and 4.6 GiB of swap already in use. Gradle runs with `-Xmx4096m` (`gradle.properties`, no `~/.gradle/gradle.properties`).
7. **Leftover watchers.** 28 stray `sleep 20` thermal loops from earlier rounds (parents with `ppid` 1) were killed by pid before R1. Nothing else of the rig's was running.
8. **Checkouts.** For R1 the shared checkout (`headunit-revived`) was detached at `77914f18` and its `git status --porcelain` was empty first. R2 to R6 ran in `../ohu-buildprobe` (a worktree at `77914f18`), which was removed and pruned after R6b. Another session switched the shared checkout to the transfer branch and back to `main` after R1b; that was not part of this round and did not touch the probe worktree. The shared checkout is on `main` at the end. No candidate branch was created and nothing was committed in either worktree.
9. **Unit tests and scripts in the asset.** The asset holds `scripts/build_hur.sh` and `scripts/run_unit_tests.sh` as they stood on the PC, the run scripts of this round (`bs_env.sh`, `bs_r1.sh`, `bs_r1b.sh`, `bs_rest.sh`, `bs_rerun.sh`), and R5's unit test run in `R5.out` (`:app:testGithubDebugUnitTest` ran, `BUILD SUCCESSFUL in 1m 15s`). Gradle's plain console does not print a test count, and the probe worktree with its test reports was removed after R6b, so **no test count was recorded this round**. An older `results/hur_unittest_out.txt` on the PC is from an earlier round and is not part of this one.
10. **Another note applied while here.** The transfer branch commit `99cba26a2` (`ohu_setkeys.py` wrote an empty `set:` as one empty string) was applied to the two source copies a later brief names, `projection-teardown-and-relays-round3/` and `usb-reattach-round1/`, with the one-line fix. A write of `set:connection-modes=` then read back as `<set name="connection-modes" />`. The other folders' copies were not changed. This is not part of the build-speed measurement.
11. R0 `grep` for passwords in the two scripts printed 0 matches; the scripts are quoted in full below.

## R0

**PASS**

Both scripts were found and `REPO` is `/home/oscar/Coding/StudioProjects/headunit-revived` (`build_hur.sh` `HUR_DIR` default). Lines of `R0.flags.txt`:

```
run_unit_tests.sh:13:cd "$HUR_DIR" || exit 1
run_unit_tests.sh:14:./gradlew testGithubDebugUnitTest >"$TEST_LOG" 2>&1
build_hur.sh:15:cd "$HUR_DIR" || exit 1
build_hur.sh:16:./gradlew assembleGithubDebug >"$BUILD_LOG" 2>&1
```

`build_hur.sh` (in full): sets `JAVA_HOME=/opt/android-studio/jbr`, `HUR_DIR` (default the repo above), `APKS_DIR`, `BUILD_LOG=results/hur_gradle_out.txt`; runs `cd "$HUR_DIR"`, `./gradlew assembleGithubDebug >"$BUILD_LOG" 2>&1`; on success finds the newest APK in `app/build/outputs/apk/github/debug/`, removes old `com.andrerinas.headunitrevived_*.apk` from `APKS_DIR` and copies the new one. **No `clean`, no `--no-daemon`, no `--rerun-tasks`, no `--max-workers`, no `--offline`.** `run_unit_tests.sh` is the same shape with `./gradlew testGithubDebugUnitTest`. No script runs a second Gradle command, so the rig's build does not include the unit tests. Java on the PATH is OpenJDK 21.0.12.1 (a JRE without a compiler); `JAVA_HOME` in the shell is empty.

## R1: the build exactly as the rig does it today

**PASS**

`./build_hur.sh` on `77914f18`, in `$SCRIPTS`: `rc=0`, **`WALL_S=243.607`**, `thermal_max=90C`, `throttle_delta=0`, swap in 540 MB, swap out 2250 MB, minimum free 118 MB. This is the 10 min figure under measurement: here it is 4 min 4 s, with the daemon cold and the tree on a new commit. The repeat under the 90C rule (R1b) was `WALL_S=5.553`, `thermal_max=67C`, all up to date (setup note 4).

## R2: cold daemon, full rebuild, profiled

**PASS**

`BUILD SUCCESSFUL in 4m 29s`, `WALL_S=270.707`, `thermal_max=92C`, `throttle_delta=0`, swap in 475 MB, swap out 545 MB, minimum free 117 MB. Gradle's `Total Build Time` 4m29.97s, `Configuring Projects` 6.689s, `Task Execution` 4m36.36s. Both kapt tasks are in `R2.tasks.txt`. Repeat (R2b): `WALL_S=269.876`, `Total Build Time` 4m29.22s, `thermal_max=92C`, swap in 555 MB, swap out 445 MB, minimum free 136 MB.

## R3: warm daemon, no change (the floor)

**PASS**

`BUILD SUCCESSFUL in 4s`, `WALL_S=4.506`, `thermal_max=68C`, swap 0 MB in and out, minimum free 130 MB.

## R4: warm daemon, one Kotlin line changed

**PASS**

A comment line appended to `AppLog.kt`. `BUILD SUCCESSFUL in 19s`, `WALL_S=19.443`, `thermal_max=67C`, swap in 35 MB, out 35 MB, minimum free 135 MB. The restore command printed nothing (`R4.restore.txt` is empty).

## R5: warm daemon, unit tests

**PASS**

`BUILD SUCCESSFUL in 1m 15s`, `WALL_S=76.048`, `thermal_max=87C`, swap in 35 MB, out 165 MB, minimum free 118 MB. The `:app:testGithubDebugUnitTest` row is 20.5 s.

## R6: kapt removed, cold daemon, full rebuild (the point of the round)

**PASS**

The patch printed `1 file changed, 3 deletions(-)` and `grep -c kapt` printed `0`. `BUILD SUCCESSFUL in 3m 50s`, `WALL_S=231.263`, `thermal_max=93C`, `throttle_delta=0`, swap in 360 MB, out 655 MB, minimum free 118 MB. `Total Build Time` 3m50.62s. No `kapt` row in `R6.tasks.txt`. Repeat (R6b): the patch printed the same, `BUILD SUCCESSFUL in 3m 34s`, `WALL_S=215.045`, `Total Build Time` 3m34.59s, `thermal_max=93C`, swap in 320 MB, out 170 MB, minimum free 118 MB.

## The table the brief asks for

| Item | First pass | Repeat |
|---|---|---|
| R1 `WALL_S` (rig build) | 243.6 s | 5.6 s (warm, up to date; not a repeat) |
| R2 `WALL_S` (cold, with kapt) | 270.7 s | 269.9 s |
| R3 `WALL_S` (warm, no change) | 4.5 s | |
| R4 `WALL_S` (warm, one edit) | 19.4 s | |
| R5 `WALL_S` (warm unit tests) | 76.0 s | |
| R6 `WALL_S` (cold, no kapt) | 231.3 s | 215.0 s |
| **R2 minus R6, `WALL_S`** | **39.4 s** | **54.8 s** |
| **R2 minus R6, `Total Build Time`** | **39.4 s** (4m29.97s - 3m50.62s) | **54.6 s** (4m29.22s - 3m34.59s) |
| `build_hur.sh` runs `clean`, `--no-daemon`, `--rerun-tasks`? | no, none of the three | |

Per run (`thermal_max`, `throttle_delta` 0 in all, swap in MB, swap out MB, minimum free MB, recomputed):

```
R1   90C  540  2250  118      R1b  67C   15  245  143
R2   92C  475   545  117      R2b  92C  555  445  136
R3   68C    0     0  130
R4   67C   35    35  135
R5   87C   35   165  118
R6   93C  360   655  118      R6b  93C  320  170  118
```

Top 12 task rows of R2 (`.tasks.txt`):

```
112.2s :app:compileGithubDebugKotlin       48.3s :app:kaptGenerateStubsGithubDebugKotlin
 28.1s :app:dexBuilderGithubDebug          14.9s :app:compileGithubDebugJavaWithJavac
 11.7s :contract:compileDebugKotlin        11.6s :app:mergeGithubDebugJavaResource
 11.5s :app:kaptGithubDebugKotlin          11.2s :app:mergeDexGithubDebug
  5.8s :app:mergeGithubDebugResources       2.4s :app:processGithubDebugResources
  1.9s :app:desugarGithubDebugFileDependencies   1.8s :app:buildCMakeDebug[arm64-v8a]-2
```

Top 12 task rows of R6:

```
130.2s :app:compileGithubDebugKotlin        27.6s :app:dexBuilderGithubDebug
 12.6s :app:compileGithubDebugJavaWithJavac 10.8s :app:mergeGithubDebugJavaResource
 10.4s :app:mergeDexGithubDebug              9.3s :contract:compileDebugKotlin
  5.7s :app:mergeGithubDebugResources        2.5s :app:processGithubDebugResources
  1.5s :app:packageGithubDebug               0.9s :app:compileGithubDebugAidl
  0.8s :app:packageGithubDebugResources      0.5s :app:checkGithubDebugDuplicateClasses
```

R2b: `compileGithubDebugKotlin` 110.6 s, `kaptGenerateStubsGithubDebugKotlin` 44.4 s, `kaptGithubDebugKotlin` 9.0 s. R6b: `compileGithubDebugKotlin` 124.1 s, `dexBuilderGithubDebug` 29.5 s.

## Anything the brief did not ask about

- **kapt's task time is larger than its wall time saving.** R2's two kapt tasks sum to 59.8 s (R2b 53.4 s) while R2 minus R6 is 39.4 s (R2b minus R6b 54.8 s). `compileGithubDebugKotlin` itself is 112 to 130 s and runs longer when kapt is absent (130.2 s and 124.1 s against 112.2 s and 110.6 s), so the saving is not the sum of the kapt rows.
- **The two R2 minus R6 samples differ by 15 s.** Both cold builds on this PC reach 92 to 93C. Whether temperature changes the Kotlin compile time was not isolated.
- **Swap.** Every cold build swapped (150 to 2250 MB out) with 118 to 143 MB free at the minimum, on a PC where a browser, Spotify and VS Code are also resident. Whether more free memory would shorten the cold build was not measured.
- **The first build of a commit is the slow one.** R1's 243.6 s was cold; warm builds took 4.5 s (no change) and 19.4 s (one edit). A build after switching commits is cold again.
- **Evidence:** release `rig-evidence-build-speed`, asset `build-speed-round1-captures.zip` (89973 bytes), sha256 `334c265ac33afdc3e58b5505032aea4d0351e81547973b21255e655c93560cd5`.
