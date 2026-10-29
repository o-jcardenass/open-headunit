# check-for-updates: round 2 results

**Candidate:** checkforupdate @ `8f88d9a6e276de0d16b541031907ef531dd365fd` plus an uncommitted 3-line working-tree change to `UpdateChecker.kt` (log calls only, diff below)       **Baseline:** round 1, same commit without the change
**APK md5:** `3145575b01ad5dbbe6f20c6cfbc6a550` on both units (pulled back from each device after install) / round 1 `371d19fd997bb8e1e5e98402c9f08c65`
**Unit:** D-HP (HP Slate 7 Plus, Android 4.2.2, API 17) and D-SAM (SM-T230, Android 4.4.2, API 19)
**Date:** 2026-09-29

Round 1 could not name the failure because the exception was never logged. This round fixes only that and repeats the press. Grading: PASS if the button ends in a real result dialog, FAIL if it ends in the failure dialog.

```diff
-            AppLog.w(TAG, "Update check failed: ${e.message}", e)
+            AppLog.e("$TAG: Update check failed: ${e.message}", e)
-                AppLog.e(TAG, "Failed to open Play Store", e)
+                AppLog.e("$TAG: Failed to open Play Store", e)
-            AppLog.e(TAG, "Failed to open GitHub releases URL: $url", e)
+            AppLog.e("$TAG: Failed to open GitHub releases URL: $url", e)
```

## Setup notes

- Built with `hur-wifi-test-scripts/build_hur.sh` and installed with `install_all.sh` (both units were the only adb devices). No script was added or changed.
- The press is a hand step by the operator, as in round 1 (no automation verb exists). `MainActivity` is launched with `am start`, since `SettingsActivity` is not exported on API 17.
- `log-level`: D-HP was already 0 (VERBOSE). D-SAM was 2 and was set to 0 with the app force-stopped (pull, edit locally, push whole file), then restored afterwards; the restored file is byte-identical to the backup.
- Capture filter `OPENHU:V System.err:V dalvikvm:W AndroidRuntime:E Conscrypt:V '*:S'`, one unit at a time. **The D-HP capture was started without clearing the buffer**, so `dhp2_logcat.txt` begins with round 1's old lines (pid 2573, 12:32 to 12:36). Only lines with pid 2884 are this round.
- D-SAM's WiFi was not restored by the app after the run: see R2 and finding 1. It is still `DISCONNECTED` at the end of the round.

## R1 - Check for Updates on D-HP (API 17)

**FAIL**

- Settings written: none.
- Discard-rule check: clean, one press.
- Dialog the operator saw: "update check failed".
- Decisive log lines (12:41:26.629, pid 2884, 21 lines of trace):
  - `E OPENHU : UpdateChecker: Update check failed: javax.net.ssl.SSLProtocolException: SSL handshake aborted: ssl=0x638ab4a8: Failure in SSL library, usually a protocol error`
  - `error:14077410:SSL routines:SSL23_GET_SERVER_HELLO:sslv3 alert handshake failure (external/openssl/ssl/s23_clnt.c:741 ...)`
  - trace top to bottom: `org.apache.harmony.xnet.provider.jsse.NativeCrypto.SSL_do_handshake` <- `OpenSSLSocketImpl.startHandshake:378` <- `libcore.net.http.HttpConnection.setupSecureSocket:209` <- `HttpsURLConnectionImpl$HttpsEngine.makeSslConnection:478` <- `HttpURLConnectionImpl.getResponseCode:495` <- `UpdateChecker$check$2.invokeSuspend(UpdateChecker.kt:112)` (the `conn.responseCode` line).
- The failure is at the TLS handshake, before any HTTP status. The server (GitHub) answered the ClientHello with a handshake_failure alert.

## R2 - Check for Updates on D-SAM (API 19)

**FAIL**

- Settings written: `log-level` 2 to 0, restored.
- Discard-rule check: **not clean for the question asked.** The failure is a DNS failure caused by the app itself, so this run does not show what the TLS layer does on API 19.
- Dialog the operator saw: "update check failed".
- Decisive log lines:
  - `12:42:04.169 MainActivity.beginAutoConnect | Auto-connect: begin (Native-AA driver: POCO X3 NFC, mode=PILL_THEN_OVERLAY)`
  - `12:42:04.890 StationStandDown.standDown | asked this unit to leave its WiFi network so the group can have the radio to itself (... disableNetwork returned true). It is rejoined when the session ends.`
  - `12:42:06.742 StationStandDown.standDown$lambda$1 | this unit has left its WiFi network.`
  - `12:42:07.643 WifiDirectManager.onStandardCreateSucceeded | Standard createGroup SUCCESS!`
  - `12:42:13.568 E OPENHU : UpdateChecker: Update check failed: Unable to resolve host "api.github.com": No address associated with hostname`
- Afterwards: `ping api.github.com` gives `unknown host`, `ping 8.8.8.8` gives `Network is unreachable`, `dumpsys wifi` shows `state: DISCONNECTED/DISCONNECTED`. Before the launch, both pings had succeeded (round 1 setup).

## Anything the brief did not ask about

**1. On a unit configured as a Native AA head unit, launching the app takes the tablet off its own WiFi, so the update check cannot reach the internet.** D-SAM's settings run Native AA (wireless mode 3), and launching `MainActivity` auto-connects and calls `StationStandDown`, which disables the WiFi network to give the P2P group the radio. The check ran 6 s later with no network. The same launch sequence is in round 1's D-SAM capture (`12:33:48 Auto-connect: begin`, `claimNativeCreateWindow ... waiting for this unit to leave its own network`), so round 1's D-SAM failure was very probably this same DNS failure, unlogged then. This is a design gap for the coding agent, not a TLS fault: on a single-radio unit in Native AA mode the button will fail whenever the stand-down is active. Whether to disable the button, say "no internet" instead of "check failed", or check before the stand-down is the coding agent's call.

**2. D-SAM was left off WiFi.** The stand-down text says the network is rejoined when the session ends, but the app was force-stopped, and `dumpsys wifi` still showed `DISCONNECTED` 10 s after. The operator may need to reconnect it by hand. Not investigated further.

**3. D-HP (API 17) shows the real defect for old units.** The stack is the platform's own `libcore.net.http` / `org.apache.harmony.xnet` stack (an `SSL23_GET_SERVER_HELLO` client hello, which on this OS does not offer TLS 1.2), not Conscrypt, even though `App.onCreate` logs `Conscrypt security provider is active`. `HttpURLConnection` here goes through the platform's `HttpsURLConnectionImpl`, and GitHub refuses the hello. Observation only, unproven: something like giving the connection an `SSLSocketFactory` from Conscrypt's `SSLContext`, or using the app's existing TLS-capable path, is the direction to check. D-SAM (API 19) has not been shown to behave the same; that needs a run where the WiFi is not stood down (for example `wifi-connection-mode` other than 3, or a press before the launch's auto-connect).

**4. Log-call fix confirmed.** With the 3-line change the failure line carries the exception class, message and full trace. `AppLog` has no `w(msg, Throwable)` overload, so the check failure is now logged at ERROR priority.

**5. Captures.** Release `rig-evidence-check-for-updates`, asset `check-for-updates-round2-captures.zip`, sha256 `f68ed336696af2f6c116bb3f219dc636490eb8606244b0bde5c9673a37d013d1` (both logcats and both dialog screenshots). The verdicts rest on the lines quoted above.
