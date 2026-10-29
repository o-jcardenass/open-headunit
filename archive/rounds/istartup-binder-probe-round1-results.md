# istartup-binder-probe — round 1 results

**Candidate:** `probe/istartup-binder` @ `6699862` on `o-jcardenass/wireless-helper` (base `upstream/main` @ `8ac36c9`)
**Baseline:** none (brief states no baseline; neither verdict depends on our code)
**APK md5:** `81000d96010b124c5b93e98466483f9b` (`com.andrerinas.wirelesshelper_1.9.4-debug.apk`)
**Unit:** D-POCO (POCO X3 NFC, LineageOS, Android 15) and D-MOTO (Motorola edge 30 neo, stock, Android 14)
**Date:** 2026-09-21

## Setup notes

- Built with `hur-wifi-test-scripts/build_wireless_helper.sh` (not an ad-hoc `./gradlew` invocation).
  The brief's own build recipe (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`) does not match this
  PC: no JDK 17 is installed here, only a JRE-only `java-21-openjdk-amd64` (fails with "does not
  provide the required capabilities: [JAVA_COMPILER]"). The standard script's
  `JAVA_HOME=/opt/android-studio/jbr` built clean on the first try — an environment difference in the
  brief's build section, not a candidate problem.
- D-MOTO was not physically connected to the test PC when the round started; it was plugged in
  partway through and the round proceeded once `adb devices` showed it. No other deviation.
- An unrelated, unfamiliar device (`adb` serial `0123456789ABCDEF`, `model:HP_Slate_7_Plus`) briefly
  appeared in `adb devices` before D-MOTO was connected, with a different serial than the known
  retired D-HP (`CNU350BGBJ`). It was gone after an `adb kill-server`/`start-server` cycle and never
  interacted with. Noted only in case it recurs.
- Debug package (`com.andrerinas.wirelesshelper.debug`) installed alongside the release Wireless
  Helper on both phones per the brief's install discipline, and uninstalled from both at the end of
  the round.

## P1-POCO — the probe on D-POCO

**PASS**

- Gearhead versionName: `17.8.163804-release.daily`, Android 15
- Decisive log line, quoted:
  `09-21 19:31:58.388  3932  3932 I HUREV_PROBE: VERDICT: OPEN. descriptor=com.google.android.gms.car.startup.IStartup`
- Verdict: **OPEN**. The `GearheadCarService__startup_proxy_signature_check_enabled` Phenotype flag
  reads off on this handset/cohort.

## P1-MOTO — the probe on D-MOTO

**PASS**

- Gearhead versionName: `17.8.163804-release.daily`, Android 14
- Decisive log line, quoted:
  `09-21 19:32:58.850 15379 15379 W HUREV_PROBE: VERDICT: CLOSED. Caller is not Google-signed.`
- Verdict: **CLOSED**. Same Gearhead version as D-POCO, opposite flag state — confirms the brief's
  expectation that cohorts can disagree even at identical Gearhead versions.

## P2 — the deep arm, D-POCO only (P1-POCO read OPEN)

**PASS**

- Decisive log lines, quoted:
  ```
  09-21 19:32:08.626  3932  3932 I HUREV_PROBE: VERDICT: OPEN. descriptor=com.google.android.gms.car.startup.IStartup
  09-21 19:32:08.641  3932  3932 W HUREV_PROBE: deep: CLOSED on dispatch though the descriptor query passed: Caller is not allowed
  ```
- The descriptor query is OPEN but the real transaction dispatch is separately gated and reads
  **CLOSED** ("Caller is not allowed" — a different message than P1's "Caller is not Google-signed."
  used for the descriptor-level check). On this handset the binder answers `queryLocalInterface`/
  descriptor lookups without the signature check, but `onTransact` itself still enforces a
  caller check on the real transaction. Not attempted on D-MOTO per the brief.
- No Android Auto misbehavior observed on D-POCO afterward; `pm clear` recovery step not needed.

## Anything the brief did not ask about

- The two phones report the **identical** Gearhead versionName (`17.8.163804-release.daily`) despite
  disagreeing on both the flag state (OPEN vs CLOSED) and, on D-POCO, disagreeing between the
  descriptor-level check (OPEN) and the dispatch-level check (CLOSED). This round's brief describes
  a single boolean gate; the D-POCO result shows at least two independent checks stacked in
  `onTransact`, only one of which is controlled by the named Phenotype flag. Worth folding into
  whatever the next-stage brief for the fallback chain says about D-POCO specifically, since a naive
  reading of "P1-POCO OPEN" would overstate what is actually usable there.
