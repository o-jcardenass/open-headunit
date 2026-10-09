# build-speed: round 2 results

**Candidate:** `fork/chore/build-speed` @ `b97f96b94ac8c91cb8c9621a270dd022eb95e429`   **Baseline:** round 1 numbers on `main` `77914f18` (not rebuilt)
**APK md5:** `c5ee5cba129d52b5b7fd4b9194594b00` (`cand-b97f96b9.apk`, built by A1) / none
**Unit:** Part A: the tester PC (i7-3632QM, 8 threads, 7.7 GiB RAM, 10 GiB swap). Part S: D-HP (HP Slate 7 Plus, Android 4.2.2, API 17) and D-HU (UNISOC MT50, Android 14), both in Headunit Server mode against D-MOTO's head unit server.
**Date:** 2026-10-08 to 2026-10-09

## Setup notes

1. **Quirk files read:** `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HP.md`, `D-HU.md`, `D-MOTO.md`, `D-SAM-and-D-HP.md`.
2. **Pre-flight** (`rig_preflight.sh D_HU:wifi D_HP:wifi D_MOTO:wifi`): all three `ok`, wifi_on=1, bluetooth_on=1, screen Awake, no BT profiles. Later it printed one FAIL for a stray process that was this session's own `tail` of `partA.log`; killed by pid, then `PREFLIGHT OK`.
3. **A0:** `~/.gradle/gradle.properties` absent. Before any build the PC had 5.0 GiB used, 378 MiB free, 2.7 GiB available, and 3.5 GiB of the 10 GiB swap in use (Firefox, Claude and VS Code resident). Load left as found.
4. **A1 and the REPO path.** The brief's A1 runs `git checkout --detach` in the headunit-revived worktree, which is the transfer worktree here. A1 built in a scratch worktree `ohu-bs2-a1` at `b97f96b9` instead (`HUR_DIR` set for `build_hur.sh`), removed afterwards. `build_hur.sh` runs plain `./gradlew assembleGithubDebug` with no `--max-workers=2` and no `--console=plain`, as the brief's A1 specifies the script. `A1.out` has no `BUILD SUCCESSFUL` line because `build_hur.sh` writes Gradle's output to its own log; A1 is graded on `rc=0` and the APK it produced.
5. **Helpers.** Saved as `bs2_env.sh` and `bs2_edit_prefs.py` from the brief, with two edits: `REPO` points at the scratch worktree, and every `pkill -f` / `pgrep -f` pattern uses the bracket form (`[K]otlinCompileDaemon`) so it cannot match the host shell. Added `bs2_partA.sh`, which chains A1, A2, M1, M2, A3, A4 and the cleanup from the brief's section 7 verbatim; it was run once in the background with a monitor on its log. No other script was added.
6. **Gradle 8.14.4** printed by `./gradlew --version` before A1; the wrapper download happened there, outside the timed build. A1 still includes the first run of Kotlin 2.4.20 (compiler and plugin artifacts were not cached before this round).
7. **Thermal.** Cooled to 70C before every build (`th_wait 70`). No `HOST_TOO_HOT`. `throttle_delta=0` on every build, so none was voided or re-run. Builds reached 83C to 95C, as in round 1.
8. **A3 daemon.** A3 started a new daemon, because M2's cleanup had stopped the last one.
9. **A4c brief gap.** See A4. The brief's A4 cannot produce `from_cache=1` as written; the cause below is my reading of the logs, not something I tested further.
10. **Part S ran after a precondition delay.** D-MOTO dropped off adb after Part A and came back. Its head unit server then refused port 5277. The operator's first "restart" had started D-POCO's server by mistake, so the brief's one allowed restart was not really used; the operator then started D-MOTO's server and `PHIP=192.168.1.5` read `REACHABLE`. Both runs went through the new script `bs2_smoke.sh` (the brief's two Part S blocks as one script, with the pre-checks: server reachable, backup ok, mode read back as 1, phone sent HOME first). It was run in the background with a monitor, S1 then S2.
11. **`su` is absent on D-HU now** (`su: inaccessible or not found`; `adb shell` is already uid 0). `push_prefs` therefore took its `run-as` branch, and the read-back proved the write: D-HU read `wifi-connection-mode value="1"` after `put_keys`. After S2 the live `settings.xml` md5 equals the backup's md5 (`9f0d3f22600a32b17192a1863df13b5e`), owner `u0_a176:u0_a176`, and the original `wifi-connection-mode` value 3 is back.
12. **D-HP's `logcat -G 16M` printed its usage text** (the old `logcat` has no `-G`); harmless, both captures grew throughout.
13. **Markers** were accepted without `allow-external-configuration`: the candidate's `CONFIGURING` set does not contain `ACTION_LOG_MARKER`, so no setting was needed.

## A1: the rig's build, cold daemon

**PASS**

- `A1 rc=0 WALL_S=297.325 thermal_max=89C throttle_delta=0`; `swap_in_MB=685 swap_out_MB=2915 min_free_MB=118`.
- Round 1's R1 was 243.6 s: A1 took 53.7 s longer. A1 includes the first Kotlin 2.4.20 run on this PC and started with 3.5 GiB of swap already in use; the brief did not ask for a second cold build, so the difference is not isolated.
- `build_hur.sh` line: `Built and copied: com.andrerinas.headunitrevived_3.5.0-beta4_debug.apk`.

## A2: full rebuild, cold daemon, profiled

**PASS**

- `A2 rc=0 WALL_S=177.285 thermal_max=92C throttle_delta=0`; `swap_in_MB=1115 swap_out_MB=0 min_free_MB=150`. `BUILD SUCCESSFUL in 2m 56s`; `Total Build Time: 2m56.82s`.
- No `kapt` row in `A2.tasks.txt` (grep count 0).
- Against round 1: R2 270.7 s (4m29.97s) and R6 231.3 s (3m50.62s). A2 is 93.4 s and 54.0 s faster by wall time.
- Top 12 tasks: `:app:compileGithubDebugKotlin` 87.0 s, `:app:dexBuilderGithubDebug` 27.0 s, `:app:compileGithubDebugJavaWithJavac` 12.4 s, `:contract:compileDebugKotlin` 10.8 s, `:app:mergeGithubDebugJavaResource` 8.7 s, `:app:mergeDexGithubDebug` 8.4 s, `:app:mergeGithubDebugResources` 5.8 s, `:app:processGithubDebugResources` 2.1 s, `:app:parseGithubDebugLocalResources` 1.7 s, `:app:buildCMakeDebug[arm64-v8a]-2` 1.6 s, `:app:desugarGithubDebugFileDependencies` 1.5 s, `:app:buildCMakeDebug[armeabi-v7a]-2` 1.5 s.

## M1: both processes capped at 2 GB

**PASS**

- `M1 rc=0 WALL_S=155.220 thermal_max=94C throttle_delta=0`; `swap_in_MB=20 swap_out_MB=0 min_free_MB=238`. `Total Build Time: 2m34.79s`; `compileGithubDebugKotlin` 80.0 s.
- Against A2: 22.1 s faster wall, swap-in 1115 to 20 MB, minimum free 150 to 238 MB.

## M2: Kotlin compiler inside the Gradle process

**PASS**

- `M2 rc=0 WALL_S=158.709 thermal_max=95C throttle_delta=0`; `swap_in_MB=0 swap_out_MB=0 min_free_MB=1202`. `Total Build Time: 2m38.27s`; `compileGithubDebugKotlin` 82.1 s.
- Against A2: 18.6 s faster wall, no swap, minimum free 150 to 1202 MB. Against M1: 3.5 s slower, inside the 20 s noise band, with about 960 MB more free at the minimum.
- M2's last line ran: `~/.gradle/gradle.properties` is absent (`ls: cannot access ... No such file or directory`, logged 00:13:14 and again after A3).
- The arms were run once each, in the order A2, M1, M2, so ordering effects (disk cache, thermal state) are not separated.

## A3: unit tests, warm daemon

**PASS**

- `A3 rc=0 WALL_S=86.974 thermal_max=91C throttle_delta=0`; `swap_in_MB=0 swap_out_MB=0 min_free_MB=1362`. `BUILD SUCCESSFUL in 1m 26s`.
- `A3 tests=2855 failures=0 errors=0`.
- Round 1's R5 was 76.0 s: A3 took 11.0 s longer, inside the 20 s noise band. A3 started a new daemon (note 8).

## A4: the build cache on a revisited state

**FAIL**

- Restore after A4a printed nothing: `restore-status=[]`.
- `A4a rc=0 WALL_S=30.889` (round 1's R4 was 19.4 s). `A4c rc=0 WALL_S=12.782`.
- `A4c from_cache=0`: the brief's decisive line `compileGithubDebugKotlin FROM-CACHE` is absent. `A4c.out` shows `> Task :app:compileGithubDebugKotlin` executed; three other tasks in A4c were `FROM-CACHE` (for example `> Task :app:generateGithubDebugBuildConfig FROM-CACHE`).
- Likely cause, not tested: the original source state was never stored in the build cache. A2, M1 and M2 ran with `--no-build-cache`, and in A3 the compile task was up to date, so no run before A4a executed that task with the cache on. A4a stored only the edited state. That makes the brief's `from_cache=1` unreachable as written. A4c at 12.8 s is an incremental compile, not a cache restore.

## S1: D-HP (API 17), Headunit Server, 5 minutes

**PASS**

- Settings written (from the unit's backup): `wifi-connection-mode=1`, `log-level=2`, `onboarding-version=2`; `native-aa-wireless`, `wifi-launcher-mode`, `video-profile-starvation-cap` and the three `debug-video-fault-*` keys deleted. Read and quoted, not written: `use-aac-audio` false, `audio-latency-multiplier` 8, `audio-queue-capacity` 20, `view-mode` 2, `video-codec` H.264, `resolutionId` 2.
- State: `ACTION_QUERY_STATE` returned `"commit":"b97f96b94ac8"`, `"connected":true` during the session.
- Bring-up: `BRINGUP-OK attempt=1` (no manual `ACTION_CONNECT`). `CAP-ALIVE` at the end of the hold.
- `S1 ssl=1`, `windows=60`, `rendered_nonzero=60`, `quit=0`, `FATAL EXCEPTION=0 ours=0`, `NoClassDefFoundError=0 ours=0`, `NoSuchMethodError=0 ours=0`, `VerifyError=0 ours=0`, `ClassNotFoundException=0 ours=0`, `UnsatisfiedLinkError=0 ours=0`, `ph_lines=71123`, `phone_critical=0`.
- A second count of `Throughput over` inside the marker window gave 60, the same as the script.
- Result for the round's point: the Kotlin 2.4.20 build runs on API 17 for 300 s with no missing class and no verify error, on one device and one session. This does not cover the rest of API 16 to 20 or other phones.
- Settings restored from `settings-backup-dhp.xml`; `wifi-connection-mode value="1"` read back (its original value).

## S2: D-HU (modern control), Headunit Server, 5 minutes

**PASS**

- Settings written (from the unit's backup): `wifi-connection-mode=1`, `log-level=2`, `onboarding-version=2`; `native-aa-wireless`, `wifi-launcher-mode`, `video-profile-starvation-cap` and the three `debug-video-fault-*` keys deleted. Read and quoted, not written: `use-aac-audio` false, `audio-latency-multiplier` 8, `audio-queue-capacity` 20, `view-mode` 2, `video-codec` H.264, `resolutionId` 2.
- State: `ACTION_QUERY_STATE` returned `"commit":"b97f96b94ac8"`, `"connected":true` during the session.
- Bring-up: `BRINGUP-OK attempt=1` (no manual `ACTION_CONNECT`). `CAP-ALIVE` at the end of the hold.
- `S2 ssl=1`, `windows=60`, `rendered_nonzero=60`, `quit=0`, `FATAL EXCEPTION=0 ours=0`, `NoClassDefFoundError=0 ours=0`, `NoSuchMethodError=0 ours=0`, `VerifyError=0 ours=0`, `ClassNotFoundException=0 ours=0`, `UnsatisfiedLinkError=0 ours=0`, `ph_lines=54761`, `phone_critical=0`.
- A second count of `Throughput over` inside the marker window gave 60, the same as the script.
- Settings restored from `settings-backup-dhu.xml` as described in note 11.

## Evidence

Release `rig-evidence-build-speed`, asset `build-speed-round2-captures.zip` (26876725 bytes), sha256 `bcc36561039578c93f7172885081d1e61b9280db9315c44f2f8ad705b73c01fb`. It holds the build outputs, profiles, thermal and vmstat logs, `summary.txt`, and the Part S captures and session logs.

## Anything the brief did not ask about

- **Memory arms.** The Kotlin daemon inheriting 4 GB from `org.gradle.jvmargs` is the likeliest source of A1's and A2's swap: M1 and M2 each cut wall time by 18 to 22 s and swap by 95 percent or more, and M2 kept at least 1202 MB free. That is the figure round 1 lacked. Both arms are single runs.
- **Kotlin compile share.** `compileGithubDebugKotlin` is 87.0 s of A2's 176.8 s profile and 80 to 82 s in M1 and M2, so it is the task to shrink next.
- **D-HU holds 52 MB of logcat for a 5-minute INFO session** (`S2.hu.logcat`), against 3.4 MB on D-HP; the zip is large.
- **D-MOTO's adb link dropped** between Part A and Part S and returned on its own after a re-check about a minute later; it showed up both over USB (`usb:3-1`) and over wireless adb (`192.168.1.5:5555`).
- **Stray-process check in `rig_preflight.sh`** flags any `tail` of a round log, including the host's own monitor. Kill it by pid before Part S.
