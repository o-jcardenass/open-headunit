# build-speed: round 3 results

**Candidate:** `fork/chore/build-speed` @ `b97f96b94ac8c91cb8c9621a270dd022eb95e429`   **Baseline:** round 2's A1 (297.3 s), A2 (177.3 s) and M2 (158.7 s)
**APK md5:** none (host only, no APK installed)
**Unit:** the tester PC only (i7-3632QM, 8 threads, 7.7 GiB RAM, 10 GiB swap). No head unit, no phone.
**Date:** 2026-10-09

## Setup notes

1. **Quirk files read:** none beyond round 2's reading of `rig-quirks/topics/tooling.md` (host only round; no unit file applies). No pre-flight table: no device is used.
2. **Scripts.** `bs2_env.sh` reused from round 2 (`REPO`, `OUT` and `SCR3` set per the brief). Added `bs3_run.sh`, which runs T1, T2 and the cleanup from the brief's section 7 in one script (same commands, same order), launched in the background with a monitor on its log. The `pkill -f` and `pgrep -f` calls in `bs2_env.sh` use the bracket form since round 2.
3. **Host load differed from round 2.** At the start of T1 the PC had 2.2 GiB used, 3.6 GiB free, 5.5 GiB available and 1.7 GiB of swap in use (round 2's A0: 5.0 GiB used, 2.7 GiB available, 3.5 GiB swap in use). Package temperature was 60C. `~/.gradle/gradle.properties` was absent (`T1.before.txt`).
4. **T2 was not a cold compile.** The brief expects the timed build to show what a first build of a new commit costs. It did not: 32 tasks were `FROM-CACHE`, including `:app:compileGithubDebugKotlin`, `:app:compileGithubDebugJavaWithJavac` and `:app:dexBuilderGithubDebug`. The candidate turns the build cache on, and round 2's A4c (original source state, cache on) had stored those outputs; a new checkout path reuses them. So T2's `WALL_S` measures a fully cached build in a fresh worktree, and the Kotlin compiler never ran. The brief's pass conditions are met, but `kotlin_daemons=0` is trivially true when no Kotlin compile happened.
5. **Daemon sampler.** `T2.daemons` has 4 rows; the first (00:39:07) predates the Gradle daemon, so it reads `xmx=` empty. The other 3 rows read `-Xmx3g`.

## T1: write the standing setting

**PASS**

- `T1.before.txt`: `ls: cannot access '/home/oscar/.gradle/gradle.properties': No such file or directory`.
- Final contents of `~/.gradle/gradle.properties` (3 lines, the first a comment as in the brief's `printf`):

```
# Standing tester PC setting from build-speed round 2 (M2): one JVM, no swap on a 7.7 GiB host.
org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g -XX:+UseParallelGC
kotlin.compiler.execution.strategy=in-process
```

- After the round the file is still present (211 bytes, `oct 9 00:39`), as the brief requires.

## T2: a cold build of a new checkout with the setting

**PASS**

- `T2 rc=0 WALL_S=30.332 thermal_max=74C throttle_delta=0`; `swap_in_MB=0 swap_out_MB=0 min_free_MB=2469`.
- `BUILD SUCCESSFUL in` count 1. `xmx=-Xmx3g` rows 3 of 4 (the first row precedes the daemon). `kotlin_daemons=0` on every row (`grep -vc` printed 0).
- Decisive rows: `00:39:17 xmx=-Xmx3g kotlin_daemons=0`, `00:39:37 xmx=-Xmx3g kotlin_daemons=0`.
- Against round 2: A1 297.3 s, A2 177.3 s, M2 158.7 s. T2 is 128 s faster than M2, but see note 4: it is a cache restore (32 of 79 tasks `FROM-CACHE`, 6 `UP-TO-DATE`), so it does not compare with those three cold builds. It does show that the Gradle daemon took the 3 GB heap, that a fresh checkout path reuses the cache, and that the build ran with no swap and at least 2469 MB free.
- The setting was not shown to cut a cold Kotlin compile again this round.

## Evidence

Release `rig-evidence-build-speed`, asset `build-speed-round3-captures.zip`, (7733 bytes), sha256 `5baf77919960955143fae9b59ed04495094eb8140ee803bc1d35a836a1f3405a`. It holds the T2 build output, thermal, vmstat and daemon samples and the session log.

## Anything the brief did not ask about

- **The build cache works across worktree paths.** A new checkout at a different path restored the Kotlin compile, javac and dex outputs in 30 s. This also explains round 2's A4: A4c ran with the cache on and stored the original state, so a later revisit of that state would have restored it. A4's `from_cache=0` was a gap in the order of the brief's runs, not a broken cache.
- **A true cold measurement of a new commit remains unmeasured with the standing setting.** A brief for it would build with `--no-build-cache` or on a commit whose outputs were never stored.
- **Cleanup state:** no Gradle or Kotlin daemon remains, no logcat, and the scratch worktree `ohu-bs3` is removed.
