# post-beta1-latency-instruments — round 1 results

**Candidate:** `fix/post-beta1-latency-instruments` @ `42bd9820`   **Baseline:** `main` @ `71930d54`
**APK md5:** `7985a4b458863f6405157dbe00173056` (candidate; baseline not built — no hardware A/B in this round)
**Devices:**
- **D-HU** `27870808938846` — UNISOC `MT50_YT610E4GFPSL_U`, Android 14 (api 34), board `uis7861_6h10`, 3745 MB RAM, projection 1920x1080. Gearhead `17.3.662864-release`. Decoder `c2.unisoc.avc.decoder`.
- **D-POCO** `4f4027e9` — Xiaomi `M2007J20CG` (Poco X3), Android 15 (api 35), board `sm6150`, 5558 MB RAM, projection 1920x1080. Gearhead `17.5.663204-release`. Decoder `c2.qti.avc.decoder`.
- **D-MOTO** `ZY22GC3BM4` — motorola `edge 30 neo`, Android 14 (api 34), board `miami`, 7462 MB RAM, projection 1080x1920 (portrait). Gearhead `17.3.662854-release`. Decoder `c2.qti.avc.decoder`.
**Date:** 2026-08-26

---

## Verdict: ROUND BLOCKED after R0

**R0 PASS** (with a one-line test fix, see below). The hardware runs R1–R8 were **not completed**: a
clean 5-minute Self Mode session could not be established and held on all three devices concurrently
on this rig. What was learned before stopping is below, because most of it will still matter to a
revised brief. The decision to stop and write up rather than push through a degraded 2-device run was
made with the coding-session operator.

Three independent obstacles, none of them a fault in the candidate's instrument code:

1. **The session banner requires `log-capture-enabled=true`, which triggers Android's
   `LogAccessDialogActivity` system-consent prompt on every app launch, on all three devices** — even
   after `adb shell pm grant … android.permission.READ_LOGS`. `LogExporter.startCapture()` spawns
   `logcat` from inside the app and SystemUI gates that regardless of the held permission. In one
   scripted run the dialog fired 55 times on D-HU as the harness relaunched. It steals foreground,
   and once produced a SystemUI `wmshell.main` crash (`SplitScreenController.startIntent` NPE). No
   scriptable bypass was found. Every banner-dependent check (R1 cond 1, R2 cond 1, all of R7) is
   blocked on this.
2. **D-MOTO's Gearhead throws its own error screen (`CarErrorDisplayActivityImpl`) on the legacy
   Self Mode path**, because that path brings up `DummyVpnService` with `excludeSelf=false` and the
   tun captures every uid non-bypassably (`Uids: <{0-99999}>`, `bypassable=false`), so Gearhead's own
   traffic is inside the VPN. D-HU (Gearhead `17.3.662864`, same legacy path) tolerates it; D-MOTO
   (`17.3.662854`) does not. **The candidate branch changes no VPN code** — `git diff 71930d54..42bd9820`
   touches nothing under `AapService`, `DummyVpnService`, `VpnControl` or `DummyVpnPolicy`. This is a
   pre-existing legacy-path / Gearhead-version (or Motorola VPN-policy) interaction. Reported here as
   an out-of-scope observation; it makes Self Mode **UNTESTABLE on D-MOTO** on this rig.
3. **The legacy (AA < 17.4) Self Mode bring-up is slow and flaky** on both D-HU and D-MOTO. Every
   attempt logs `SelfMode: All launchers failed (timeout)` → `PermissionTrampolineActivity` returns
   in <1000 ms → `SelfMode: Permission activity closed immediately or failed` → fall back to
   `HomeFragment.startSelfMode` (DummyVPN) ~15–20 s later. `PermissionTrampolineActivity.onActivityResult`
   treats a sub-second return as a failure (`AAPermissionTrampolineActivity.kt:56`) even though its own
   comment says a fast close means permissions are already granted. Pre-existing, not this branch, but
   it makes the legacy path unreliable to script and it repeatedly dropped D-HU into Native-AA mode-3
   (`createGroup SUCCESS` ×2, the self-wake churn `CLAUDE.md` documents) mid-observation.

Only **D-POCO** (Gearhead 17.5, direct-to-`127.0.0.1:5277`, no VPN, no trampoline) has a clean path,
and even it wedged once behind the LogAccessDialog (app process alive, zero log lines for 90 s).

---

## Setup notes

- **Scripts used:** `hur-wifi-test-scripts/build_hur.sh` (R0 build), `run_unit_tests.sh` (R0 tests).
  Added `hur-wifi-test-scripts/set_prefs_runas.sh` — a multi-key `run-as` SharedPreferences editor for
  non-rooted devices (the phones), companion to the existing single-key `set_pref.sh`; writes/deletes
  several keys in one force-stop with no relaunch.
- **`log-capture-enabled` is not in the brief's §4 settings table but every banner-dependent check
  needs it.** Brief bug. `Settings.exporterCaptureEnabled` (`KEY_LOG_CAPTURE_ENABLED = "log-capture-enabled"`,
  boolean, default `false`) gates `AapService`'s `LogExporter.startCapture()` call; with it unset there
  is no `LogExporter: session` line anywhere. Setting it true is what triggers obstacle 1.
- **Brief §3 count discrepancy:** §3 says the 5 GHz band work is "covered by 9 JVM cases in
  `NativeGroupBandPolicyTest` and `WifiP2pOperatingChannelPolicyTest`". Actual: `NativeGroupBandPolicyTest`
  = 19, `WifiP2pOperatingChannelPolicyTest` = 18 (37 total). The `@Test` grand total of **811 matches**
  the brief.
- **`run-as` works on all three devices** — `run-as $PKG cat shared_prefs/settings.xml` returned real
  content on each. No root-owned `shared_prefs` problem on any of the three.
- **Installing the candidate APK with `adb install -r -d` did NOT wipe settings** on any device
  (`has-completed-setup-wizard`/`onboarding-version` and all keys survived). Brief §3's "installing can
  wipe settings" did not occur here. All three had a prior HUR build; D-MOTO was versionCode 98 (needed
  `-d`), D-HU and D-POCO were already 100.
- **Only two distinct decoder components across the three devices:** `c2.unisoc.avc.decoder` (D-HU),
  `c2.qti.avc.decoder` (**both** D-POCO and D-MOTO). No `.mtk.`/`mediatek` component anywhere → **R3
  UNTESTABLE** (pre-registered). D-MOTO would have been a second qti device only.
- **`pm grant android.permission.READ_LOGS`** was applied to all three during setup and **revoked**
  during teardown. It did not suppress the LogAccessDialog (see obstacle 1).
- **Mock GPS** (`cmd location providers add-test-provider gps`) was accepted on D-POCO and D-MOTO,
  **rejected on D-HU** (`SecurityException: android from uid 0 not allowed to perform MOCK_LOCATION` —
  D-HU's adb shell runs as uid 0/root, which the ROM blocks from mock location). Test providers were
  removed during teardown.
- **Devices restored:** all three `settings.xml` restored from a pre-round backup (`wifi-connection-mode`
  back to 3 / 1 / 2 respectively — it had been normalised to 1 on all three during the failed Self Mode
  attempts), `log-capture-enabled` cleared, `READ_LOGS` revoked, mock providers removed, apps
  force-stopped. **The candidate APK (`7985a4b4…`) was left installed on all three** per the
  no-uninstall rule; a resumed round needs it anyway.
- One transient SystemUI crash on D-HU (`FATAL EXCEPTION: wmshell.main`, `SplitScreenController`
  NPE) from rapid scripted `am start` cascades during a relaunch storm; SystemUI recovered on its own.

---

## R0 — build and unit-test gate

**PASS** (after a one-line test-only fix)

- `./gradlew :app:assembleGithubDebug` — **PASS**. `com.andrerinas.headunitrevived_3.3.0-beta1_debug.apk`,
  md5 `7985a4b458863f6405157dbe00173056`.
- `./gradlew :app:testGithubDebugUnitTest` — first run **811 tests, 1 failed**:

  ```
  InboundRateMonitorTest > a window starts empty, so two windows do not accumulate FAILED
      java.lang.AssertionError: the first window's bytes must not be carried over expected:<2000> but was:<1000>
      at com.andrerinas.openheadunit.aap.InboundRateMonitorTest.a window starts empty, so two windows do not accumulate(InboundRateMonitorTest.kt:67)
  ```

  **This is a bug in the test, not the production code.** The test feeds exactly one 1000-byte VID
  message into the second window (`onMessage(ID_VID, 1000, window * 2)`), so `1000` is the correct
  non-accumulating result. The rest of the suite depends on the same semantics — the closing message
  is counted in the window it closes and the next window starts empty, which `InboundRateMonitor.onMessage()`
  does correctly (`windowStartMs = nowMs` + zeroes the counters). The expected literal at
  `InboundRateMonitorTest.kt:67` should be `1000L`, not `2000L`. Introduced in `f54ca65d`; wrong since
  written. **Fix for the branch owner:**

  ```
  -        assertEquals("the first window's bytes must not be carried over", 2000L, second.videoBytes)
  +        assertEquals("the first window's bytes must not be carried over", 1000L, second.videoBytes)
  ```

  With that one-character change applied locally: **811 tests, 0 failures.**

- Suite counts: `DecodeLatencyMonitorTest`=9, `DecoderExceptionPolicyTest`=6 (the 15 new decoder-policy
  cases the brief predicts), `DecoderConfigLadderTest`=15, `NativeGroupBandPolicyTest`=19,
  `WifiP2pOperatingChannelPolicyTest`=18, `InboundRateMonitorTest`=9, `NarrowBandProfilePolicyTest`=8.

---

## R1 — Self Mode baseline — NOT COMPLETED

A clean 5-minute session on all three was never captured (obstacles above). What the fragments and
the pre-R1 smoke sessions (~45 s clean on each device, `debug-video-low-latency` off) do establish:

**The two new instruments emit correctly on both real components.**

- `presented=` and `decodeLatency=` are present on **every** `Throughput over` line on all three
  devices. `decodeLatency=unreadable` never appeared on any device.
- `optionalKeys=none` on every `Configuring decoder:` line (setting off), all three.
- No `Decoder rejected optionalKeys=` line anywhere, any capture.
- Capability line on all three ends `featureLowLatency=false featureAdaptivePlayback=true`.
- D-HU semi-attended run captured the banner: `LogExporter: session | build=3.3.0-beta1 (100)
  github/debug | device=UNISOC MT50_YT610E4GFPSL_U board=uis7861_6h10 api=34 | video=codec:Auto fps:60
  resId:3 view:SURFACE forceSw:false swDecoder:BUNDLED_FFMPEG | wifi=mode:NATIVE strategy:NEARBY_DEVICES
  | logLevel=INFO | debug=none` — **`debug=none` confirmed** (R1 cond 1) on one device.

**Preliminary numbers (smoke sessions, ~10–17 windows each — NOT the briefed 5 min, treat as
indicative only):**

| Device | component | decodeLatency median | per-window `p95=` field, median (max) | mean rendered/presented ratio |
|---|---|---|---|---|
| D-HU | `c2.unisoc.avc.decoder` | 15 ms | 27 ms (99 ms) | **1.009** |
| D-POCO | `c2.qti.avc.decoder` | 12 ms | 14 ms (39 ms) | **1.003** |
| D-MOTO | `c2.qti.avc.decoder` | 18 ms | 21 ms (291 ms, first window) | **1.001** |

**The rendered/presented ratio is ~1.00 on all three, not the 1.6x the brief cites from one unit.**
In every window `rendered` equalled `presented` almost exactly. Per brief §6 R1 this is "a different
device behaviour, not a broken instrument" — but it is worth the brief author knowing that the 1.6x
figure did not reproduce on any of three other devices (two components, three SoCs), in Self Mode.
A clean 5-minute run is still needed to state this with confidence.

Inbound-rate lines (`AapTransport: inbound rate over 30000ms`) were seen with non-zero `video=` and
`audio=0kB/s` (Self Mode announces no audio sink — expected per brief §7); the ~45 s smoke windows
were too short to accumulate the ≥8 lines R1 asks for.

---

## R2 / R4 / R5 / R6 / R7 — NOT RUN

Blocked on the same obstacles; not attempted rather than run degraded.

- **R3 — UNTESTABLE** (pre-registered): no MediaTek decoder component on any of the three devices.
- **R8 — provisional PASS:** `[ServiceDiscovery] This unit has no 5 GHz band` did **not** appear in any
  capture from any device (smoke or fragment). Consistent with all three having a 5 GHz radio. Worth
  re-confirming from a clean R1 capture.

---

## Anything the brief did not ask about

- **`LogAccessDialogActivity` on every launch, all three devices, with `READ_LOGS` held.** The single
  biggest blocker. If the app is meant to be run with `log-capture-enabled=true` for reporter builds,
  this dialog will hit every reporter too, on every app start. Worth its own investigation — is there
  an API level / OEM split, does the app have a path that reads the ring buffer without spawning
  `logcat`, or should the banner also be emitted on the `logSource=APPLOG_FILE` path (which does not
  spawn `logcat`)?
- **Legacy Self Mode brings up a non-bypassable all-uid VPN that at least one Gearhead build rejects.**
  D-MOTO's `CarErrorDisplayActivityImpl`. `VpnControl.startVpn(excludeSelf=false)` on the legacy path;
  the 17.4+ path skips the VPN entirely. Candidate changes no VPN code, so if this is a regression it
  is older than `71930d54`. Route to the `session-vpn-lever` thread or a `main` investigation. The
  user reports it "didn't appear in prior versions."
- **`PermissionTrampolineActivity` inverts its own success condition.** `AAPermissionTrampolineActivity.kt:56`
  — a return in <1000 ms is treated as failure, but the class comment says a fast close means all
  permissions are already granted. On both AA-<17.4 devices this always fires and always logs
  `SelfMode: Permission activity closed immediately or failed`, adding ~15 s and a fallback hop to
  every legacy Self Mode start. Pre-existing.
- **`wifi-connection-mode` was silently normalised to `1` (AUTO) on all three devices** during the
  failed Self Mode attempts (from 3 / 1 / 2). Restored from backup. Whatever resets it may be worth a
  look — a real user who tries Self Mode and it fails could find their Native-AA mode quietly changed.
- **Two decoder components, not three.** D-POCO and D-MOTO both `c2.qti.avc.decoder` (though D-MOTO's
  capability line reads `widths=[96, 1920]` vs D-POCO's `[96, 4096]` — a narrower qti variant/config).
  The round's premise of "three devices, three components, three rungs" holds for two.

---

## For the revised brief

1. **Add `log-capture-enabled` (boolean, `true`) to the settings table**, and decide how to get the
   banner past `LogAccessDialogActivity` unattended — or drop the banner as a PASS condition and read
   `debug=` state from `settings.xml` (`debug-video-low-latency` present/absent) instead, moving R7 to
   a JVM test or a single supervised run.
2. **Drop D-MOTO, or pick a different third device** — one on AA ≥ 17.4 (skips the VPN and the
   trampoline), or accept two components. D-HU (unisoc) + a single AA-17.4+ qti phone would give a
   clean R1/R2/R4 with no legacy-path fighting.
3. The core question (does the low-latency key move median/p95 decodeLatency on real hardware) is
   answerable on D-HU + D-POCO alone once obstacle 1 is resolved. The instrument itself is confirmed
   working; only the run conditions blocked it.
