# check-for-updates: round 3 results

**Candidate:** checkforupdate @ `8f88d9a6e276de0d16b541031907ef531dd365fd` plus the uncommitted 3-line log-call change from round 2       **Baseline:** round 2 (same APK)
**APK md5:** `3145575b01ad5dbbe6f20c6cfbc6a550`, unchanged from round 2 (no rebuild, no reinstall)
**Unit:** D-SAM (SM-T230, Android 4.4.2, API 19)
**Date:** 2026-09-29

Round 2 could not tell what D-SAM's TLS does, because the app took the tablet off WiFi first. This round repeats only that press with the WiFi staying up.

## Setup notes

- The operator switched the unit to Self Mode and reconnected its WiFi before the run. The pulled `settings.xml` still had `wifi-connection-mode=3` and `connection-modes` = `usb`; comparing name/value attributes it was identical to round 2's, so the operator's change is not visible in those keys. Either way, this launch did **not** auto-connect Native AA: 0 `StationStandDown` lines, no `Auto-connect: begin`, and `dumpsys wifi` read `CONNECTED` both before and after the press. What actually changed the launch behaviour was not identified.
- Before the press `ping -c1 api.github.com` succeeded (1 received, 0% loss).
- `log-level` 2 changed to 0 with the app force-stopped (pull, edit locally, push whole file); restored afterwards and byte-identical to the backup.
- Hand step: the operator pressed Settings > About > Check for Updates once. `MainActivity` launched with `am start`. The buffer clear was refused (`klogctl: Operation not permitted`) but the capture starts at this launch (first app line 12:45:55, pid 5547).
- No scripts from `hur-wifi-test-scripts/` used or changed this round.

## R1 - Check for Updates on D-SAM (API 19), WiFi up

**FAIL**

- Settings written: `log-level` 0, restored.
- Discard-rule check: clean, one press, network verified before and after.
- Dialog the operator saw: "update check failed".
- Decisive log lines (pid 5547):
  - `12:46:11.300 W dalvikvm: VFY: unable to resolve virtual method ... getInstallSourceInfo` (press)
  - `12:46:11.981 E OPENHU : UpdateChecker: Update check failed: javax.net.ssl.SSLProtocolException: SSL handshake aborted: ssl=0x7e8a8ee0: Failure in SSL library, usually a protocol error`
  - `error:14077410:SSL routines:SSL23_GET_SERVER_HELLO:sslv3 alert handshake failure (external/openssl/ssl/s23_clnt.c:744 ...)`
  - trace: `com.android.org.conscrypt.OpenSSLSocketImpl.startHandshake:449` <- `com.android.okhttp.Connection.upgradeToTls:146` <- `Connection.connect:107` <- `HttpEngine.sendSocketRequest:255` <- `HttpURLConnectionImpl.getResponseCode:503` <- `UpdateChecker`
- Measurements: 681 ms from press to failure line.

## Anything the brief did not ask about

**1. D-SAM fails the same way as D-HP.** Both units get `SSL23_GET_SERVER_HELLO ... sslv3 alert handshake failure` from GitHub at the handshake. The difference is only the stack underneath: D-HP (API 17) uses `org.apache.harmony.xnet` and `libcore.net.http`; D-SAM (API 19) uses the system's own `com.android.org.conscrypt` under the platform `com.android.okhttp`. So on D-SAM the connection already goes through a Conscrypt, but the platform one, and not the bundled Conscrypt the app initialises. Observation, unproven: on API 19 the platform client hello does not offer TLS 1.2 by default, and `api.github.com` requires it. The bundled Conscrypt (`App.kt:72` logs it active) evidently is not what `HttpsURLConnection` uses here. Both old units point at needing an `SSLSocketFactory` from the bundled provider (or another TLS 1.2 path), set on the connection in `UpdateChecker.check`.

**2. Round 1's D-SAM result and round 2's D-SAM DNS failure are both now explained.** Round 2's DNS failure was the Native AA stand-down (see round 2 finding 1); this round shows that, with the network intact, the underlying fault is TLS, same as D-HP.

**3. Not tested:** what happens after a fix (no candidate exists that changes the connection). Whether Play Store or newer units are affected is out of scope; `UpdateChecker` was not exercised on any API 21+ unit.

**4. Captures.** Release `rig-evidence-check-for-updates`, asset `check-for-updates-round3-captures.zip`, sha256 `5613595e7e1216768953389e79a4285fcae51b9d622b723f815b4689b8f33980` (logcat and the dialog screenshot). The verdict rests on the lines quoted above.
