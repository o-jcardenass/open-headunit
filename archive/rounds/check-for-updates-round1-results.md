# check-for-updates: round 1 results

**Candidate:** checkforupdate @ `8f88d9a6e276de0d16b541031907ef531dd365fd`       **Baseline:** origin/main @ `5f426cabf578dda09a3115803604c5463f062c56`
**APK md5:** `371d19fd997bb8e1e5e98402c9f08c65` (identical on both units, pulled from the device) / no baseline built
**Unit:** D-HP (HP Slate 7 Plus, Android 4.2.2, API 17) and D-SAM (SM-T230, Android 4.4.2, API 19). Both are below API 21, so TLS 1.2 depends on the bundled Conscrypt. Installed `versionName=3.5.0-beta2`, `versionCode=115`.
**Date:** 2026-09-29

There was no brief. The question asked was "does the new Check for Updates button work on the old units", so a run is PASS if the button ends in a real result dialog (update available or up to date) and FAIL if it ends in the failure dialog.

## Setup notes

- The button press is a **hand step**: there is no `AutomationReceiver` verb for it and the feature is new. The operator opened Settings > About and pressed "Check for Updates" once per unit. No `input tap` was used. Each unit's app was launched with `am start ... main.MainActivity`.
- `SettingsActivity` cannot be started with `am start` on D-HP (API 17): `SecurityException ... not exported from uid 10071`. `MainActivity` works, so that was used on both.
- `log-level`: D-HP was already `0` (VERBOSE) in `settings.xml`, nothing written. D-SAM was `2` (INFO). It was changed to `0` with the app force-stopped, by pulling the file, editing locally, and pushing the whole file (no `sed` on the device). After the run the original file was pushed back; it differs from the backup only by CR bytes added by the adb tty, `diff` after `tr -d '\r'` is empty.
- Capture: `stdbuf -oL adb logcat -v threadtime OPENHU:V System.err:V dalvikvm:W AndroidRuntime:E Conscrypt:V '*:S'`, one unit at a time, each killed by pid after its press. No other logcat is left running. (Filtering to those tags means a Java exception logged under a different tag would not show. See the findings below.)
- Context before each press: both units had `ping -c1 8.8.8.8` and `ping -c1 api.github.com` succeed (1 received, 0% loss), which proves WiFi, routing and DNS work. Device clocks matched the host to within 12 s (`Tue Sep 29 12:31 COT 2026`). D-HP `auto_time=1`, D-SAM `auto_time=0`.
- No scripts from `hur-wifi-test-scripts/` were used or added.

## R1 - Check for Updates on D-HP (API 17)

**FAIL**

- Settings written: none (`log-level` already 0).
- Discard-rule check: clean, one press.
- Dialog the operator saw: "update check failed".
- Decisive log lines:
  - `09-29 12:32:51.809 I OPENHU : [1] App.onCreate | Conscrypt security provider is active`
  - `09-29 12:33:29.389 W dalvikvm: VFY: unable to resolve virtual method 359: Landroid/content/pm/PackageManager;.getInstallSourceInfo (...)` (press)
  - `09-29 12:33:29.889 W OPENHU : [228] 2.invokeSuspend | UpdateChecker`
- Measurements: 500 ms from the press-time line to the failure line, so it is a fast failure, not the 8000 ms connect or read timeout. Nothing else from `System.err` or `AndroidRuntime` appears.

## R2 - Check for Updates on D-SAM (API 19)

**FAIL**

- Settings written: `log-level` 2 to 0, restored afterwards.
- Discard-rule check: clean, one press.
- Dialog the operator saw: "update check failed".
- Decisive log lines:
  - `09-29 12:33:47.425 I OPENHU : [1] App.onCreate | Conscrypt security provider is active`
  - `09-29 12:34:17.013 W dalvikvm: VFY: unable to resolve virtual method 359: ...getInstallSourceInfo ...`
  - `09-29 12:34:17.073 W OPENHU : [466] 2.invokeSuspend | UpdateChecker`
- Measurements: 60 ms between those two lines, again far below the 8000 ms timeouts. The `System.err` lines at 12:33:54 are Samsung ShareShot's own `Resources$NotFoundException`, unrelated.

## Anything the brief did not ask about

**1. The failure cause is not in the log, which is a defect in the logging call, not just bad luck.** `UpdateChecker.kt:168` calls `AppLog.w(TAG, "Update check failed: ${e.message}", e)`. `AppLog` has `w(msg: String)` and `w(msg: String, vararg params: Any)`, so this resolves to the vararg overload: `TAG` is used as the format string and the real message and the exception are passed as ignored params. That is why the whole line reads just `UpdateChecker`. The exception class and message were therefore not captured on either unit. `AppLog` has no `w(msg, Throwable)` overload (only `e(msg, tr)` at `AppLog.kt:223`), so the call needs a message with the class prefix built into one string and the throwable passed through an overload that takes it. That is the first thing needed before any cause can be named. `SettingsFragment.handleCheckForUpdates` also discards the failure (`onFailure = { ... }` ignores `it`), so nothing else records it. `AppLog.e(TAG, "...", e)` at `openPlayStore` has the same shape.

**2. What the evidence does and does not rule out (observations, unproven).** Network and DNS work on both units, the clock is right, Conscrypt reports active, and the failure lands in 60 to 500 ms. That points at a fast local exception (TLS handshake or certificate trust on API 17/19, or the JSON/HTTP handling) rather than a timeout or no connectivity. It does not identify which. Candidate areas for the coding agent: the old system root store versus the GitHub certificate chain, whether the Conscrypt provider is actually the one used by `HttpURLConnection` (the default `HttpsURLConnection` on API 17/19 uses the platform TLS stack, which caps at TLS 1.0 for the client unless the socket factory is replaced), and SNI. Check the TLS-version candidate first, since `api.github.com` refuses TLS 1.0/1.1.

**3. Noise, not a bug:** `VFY: unable to resolve virtual method ... getInstallSourceInfo` appears at every press. It is the verifier noting an API 30 method that is guarded by `Build.VERSION.SDK_INT >= R`, so it is harmless.

**4. Captures.** Full logcat for both units and the two dialog screenshots: release `rig-evidence-check-for-updates` on `o-jcardenass/open-headunit`, asset `check-for-updates-round1-captures.zip`, sha256 `af28300a94e0b2f61df8999a9fcf09110662801e9a880123eff39f349be716fe`. Everything the verdicts rest on is quoted above.
