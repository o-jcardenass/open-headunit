# aap-reorg — round 1 results

**Candidate:** `fix/head-unit-server-silence-and-log-attribution` @ `7395d21b` (`7395d21b4231512c7fec70a202304bd321c4a2e5`)
**Baseline:** none (thread's first round; brief §1)
**APK md5:** none produced — the candidate does not build (see R0)
**Unit:** build host only. UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14) was reachable and the
Native AA phone (POCO X3 / `M2007J20CG`, Android 15, Gearhead `17.5.663204-release`) is still
BR/EDR+LE `BOND_TYPE_PERSISTENT` bonded to the head unit ("Navegadortz2"), but no on-device run was
reached.
**Date:** 2026-08-27

## Verdict in one line

**R0 FAIL — the candidate does not compile.** `65212776` ("Chore clean aap") relocates
`NativeCredentialsPreflightPolicy.kt` (and ~9 other main-source files) into a new package
`com.andrerinas.openheadunit.connection.wifi.modes.native`. **`native` is a Java reserved word.**
kapt generates a Java stub for `SettingsFragment` that names the moved type by its fully-qualified
Kotlin package, producing uncompilable Java, and `:app:kaptGithubDebugKotlin` fails. This affects
both `assembleGithubDebug` and `testGithubDebugUnitTest`. R1/R2/R3 are all downstream of an APK
that cannot be built, so none of them ran. The brief's premise ("the relocation builds green
either way", §"What this is"; "compile-time correctness already proven by review and by R0's
build", §7) does not hold.

## Setup notes

- Scripts used: `hur-wifi-test-scripts/build_hur.sh` (build), `hur-wifi-test-scripts/run_unit_tests.sh`
  (unit tests). No new scripts added. No settings written on any device. No `settings.xml` backup
  was taken because R2/R3 (the only runs that touch settings) were never reached.
- `build_hur.sh` exits before its APK-copy step on failure, so `hur-wifi-test-scripts/apks/`
  still holds the **previous** thread's github APK (`com.andrerinas.headunitrevived_3.3.0-beta2_debug.apk`,
  md5 `7d793c0cd716c9b984554bb5796684f4` — that is `58802778`, the `selfmode-playstore-route`
  round 1 candidate, not this one). Nothing was installed to any device.
- Candidate checked out with `git checkout 7395d21b` (detached HEAD); `git rev-parse HEAD` =
  `7395d21b4231512c7fec70a202304bd321c4a2e5`, matching the brief. `usbhelper.c` at that commit
  carries 10 `Java_com_andrerinas_openheadunit_connection_usb_UsbNative_*` symbols as the brief
  describes — but this could not be exercised, live or by APK audit, because no APK exists.
- Device state observed in passing (not part of any run): the head unit's own
  `dumpsys bluetooth_manager` prints an empty "Bonded devices:" list, but the phone side shows the
  head unit as a persistent dual-mode bond with an active ACL, so the bond is intact — the empty
  HU-side list is a ROM display quirk on this unit, consistent with `TESTING-TEMPLATE.md` §7a's
  note that only the phone-side bond fact is load-bearing. The head unit's WiFi station radio is
  currently unassociated (`SSID: <unknown ssid>`, `Supplicant state: DISCONNECTED`), where §7a
  records it as normally joined to "Pegue Cdesta" — not relevant to a Native AA P2P session, noted
  only so a later reader knows the rig state drifted.

## R0 — Unit test gate and count parity

**FAIL**

- Build command: `./gradlew assembleGithubDebug` (via `build_hur.sh`), then
  `./gradlew testGithubDebugUnitTest` (via `run_unit_tests.sh`), at `7395d21b`,
  `JAVA_HOME=/opt/android-studio/jbr`.
- Both fail at the same task, before any test is compiled or run, so **there is no executed count
  to report** (brief expected 780/0). Delta is undefined, not 0.
- Decisive log lines (identical in `results/hur_gradle_out.txt` and `results/hur_unittest_out.txt`):

  ```
  > Task :app:kaptGenerateStubsGithubDebugKotlin
  > Task :app:kaptGithubDebugKotlin FAILED
  .../build/tmp/kapt3/stubs/githubDebug/com/andrerinas/openheadunit/main/SettingsFragment.java:498: error: <identifier> expected
      private final void promptForField(java.util.List<? extends com.andrerinas.openheadunit.connection.wifi.modes.native.CredentialField> missing, int index) {
                                                                                                                   ^
  .../SettingsFragment.java:498: error: illegal start of type
  .../SettingsFragment.java:498: error: <identifier> expected      (x3 more)
  .../SettingsFragment.java:498: error: ';' expected

  FAILURE: Build failed with an exception.
  * What went wrong:
  Execution failed for task ':app:kaptGithubDebugKotlin'.
  > A failure occurred while executing org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask$KaptExecutionWorkAction
  BUILD FAILED in 21s        (assembleGithubDebug)
  BUILD FAILED in 3s         (testGithubDebugUnitTest)
  ```

### Root cause (confirmed, not hypothesis)

`65212776` moves these main-source files from `com.andrerinas.openheadunit.aap` into the new
package `com.andrerinas.openheadunit.connection.wifi.modes.native`:

```
aap/BluetoothWakePolicy.kt              → connection/wifi/modes/native/BluetoothWakePolicy.kt
aap/CredentialsHandoff.kt               → connection/wifi/modes/native/CredentialsHandoff.kt
aap/NativeCredentialsPreflightPolicy.kt → connection/wifi/modes/native/NativeCredentialsPreflightPolicy.kt
aap/NativeHandoffPolicy.kt              → connection/wifi/modes/native/NativeHandoffPolicy.kt
aap/WppFraming.kt                       → connection/wifi/modes/native/WppFraming.kt
aap/WppHandshakeSession.kt              → connection/wifi/modes/native/WppHandshakeSession.kt
```

plus in-place edits to `NativeAaHandshakeManager.kt`, `NativeCredentialsPreflight.kt`,
`SoftApCredentialsProvider.kt` already in that dir, and the matching test files.

- `enum class CredentialField` is declared in `NativeCredentialsPreflightPolicy.kt`, so after the
  move its package is `...connection.wifi.modes.native`.
- `SettingsFragment.kt` (`main/`) calls `promptForField(missing: List<CredentialField>, …)`.
- kapt's stub generator emits `SettingsFragment.java` with the parameter type written out fully as
  `com.andrerinas.openheadunit.connection.wifi.modes.native.CredentialField`. `native` is a Java
  keyword (JLS §3.9), illegal as a package-name identifier, so `javac` rejects the stub.
- The Kotlin front-end accepts `native` as a package segment (it is only a *modifier* keyword in
  Kotlin, usable as an identifier), which is why review and a Kotlin-only glance miss it — the
  break only surfaces when kapt round-trips a reference to the type through Java.
- `native` is the **only** Java-keyword collision among the ~18 new package directories the commit
  creates (`self`, `direct`, `server`, `modes`, `video`, `audio`, `input`, `app`, `view` are all
  fine).

Pre-relocation, at `58802778`, these files sit in `com.andrerinas.openheadunit.aap` with no
keyword segment, and that commit's `assembleGithubDebug` / `testGithubDebugUnitTest` are green on
this same host today (the `selfmode-playstore-route` round 1 built its APK `7d793c0c` from it).
`7395d21b` (the JNI-symbol commit under test alongside the relocation) touches only `usbhelper.c`
and does not affect this.

### Suggested fix (for the coding session — not applied here)

Rename the package segment to something that is not a Java keyword, e.g.
`connection.wifi.modes.nativeaa` (or `.nativemode`), and update the ~19 moved files plus every
importer. A per-file `@file:JvmName`-style shim does not help — the problem is the *package*
identifier in a generated Java stub, not a class name. Verify with a full `assembleGithubDebug`
(not just a Kotlin compile) because only kapt exercises the failing path.

## R1 — APK native symbol audit

**UNTESTABLE** — the audit runs on `lib/*.so` inside the APK from R0, and R0 produces no APK.
Nothing about the `usbhelper.c` symbol fix (`7395d21b`) or the HEVC symbol rename (`65212776`)
could be checked. The source at `7395d21b` has `usbhelper.c` carrying the 10 expected
`...connection_usb_UsbNative_*` symbols and no `connection_UsbNative` remnant, but the brief's
whole point was to confirm that at the built-binary level, which is not possible without a build.

## R2 — Native AA baseline smoke

**UNTESTABLE** — needs the candidate installed. No candidate APK. The rig was otherwise ready
(phone bonded, adb up on all three devices).

## R3 — Bundled FFmpeg HEVC engagement

**UNTESTABLE** — same reason as R2. No settings were written and no `settings.xml` backup was
taken, since the run was never set up.

## Report-back answers (brief §8)

1. **R0 executed-test counts and delta:** none — the build fails at `:app:kaptGithubDebugKotlin`
   before test compilation. Expected was 780/0; actual is a build failure. This is a FAIL, and it
   stops the round.
2. **R1 symbol list per library:** not obtained — no APK to unzip.
3. **R3 verdict with the engagement line and fps:** not obtained — could not install or run.

## Anything the brief did not ask about

- The failure is deterministic and host-independent (it is a JLS violation in generated code), so
  CI will fail on this commit too. If `65212776`/`7395d21b` were pushed to a branch that CI builds,
  the `github` flavor job is currently red.
- The relocation also moves the tests into `...modes.native` test packages. Even once the main
  package is renamed, double-check the test source set compiles — `testGithubDebugUnitTest` shares
  the same kapt stub path and would hit the identical wall if any test-only type in that package
  is referenced across a kapt boundary.
- `build_hur.sh` leaving a stale, differently-versioned APK in `apks/` on failure is a small trap
  for the next round: always re-check `md5sum` against a freshly built file, never trust `apks/`
  after a failed build.
