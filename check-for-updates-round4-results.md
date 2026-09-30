# check-for-updates: round 4 results

**Candidate:** fork `fix/update-check-old-tls` @ `b77bf005a14b78df75abd0c4e184b175fbe60dcb` (one commit over `8f88d9a6`)       **Baseline:** rounds 2 and 3 (`8f88d9a6` plus the log-call change, md5 `3145575b01ad5dbbe6f20c6cfbc6a550`)
**APK md5:** `582e2114a1c2cfcaaeb0a1acdc2e95d7`, pulled from each unit with `adb pull` (D-SAM, D-HP) and read with `md5sum` on device (D-HU); differs from the baseline
**Symbols in the candidate's dex:** `no bundled HTTPS socket factory` 1, `ModernTlsSocketFactory` 8
**Unit:** D-HP (HP Slate 7 Plus, API 17), D-SAM (SM-T230, API 19), D-HU (UNISOC MT50, API 34)
**Date:** 2026-09-29

## Round verdict: FAIL

The fix engages on both old units: the bundled Conscrypt now runs the handshake and GitHub accepts it, which round 2 and 3's `sslv3 alert handshake failure` never allowed. The check then fails one step later on certificate trust, the case the brief named as the thing this round could newly fail on. R1 and R2 FAIL, R3 PASS.

## Setup notes

- Build: `build_hur.sh` on a detached checkout of `b77bf005`; `git log --oneline 8f88d9a6..HEAD` showed exactly 1 commit and `git status --short` was empty (no uncommitted round 2 copy to discard). Installed with `adb install -r` on all three units. D-HU was carrying an older build (md5 `7d4113ae68aea356c37fb5e27e2407dd`), replaced by the candidate.
- Scripts used: `build_hur.sh`, `set_prefs_runas_host.py` (D-SAM and D-HP, whole-file host edit then `run-as` push), `set_hu_prefs.sh` (D-HU). No script added or changed.
- Settings written on every unit: `stand-down-station-mode=2`, read back before each launch. `log-level` left as found: D-SAM 2, D-HP 0, D-HU 2. Every `settings.xml` was backed up first and restored at the end; D-HU restored byte-identical (owner 10176:10176 checked), D-SAM and D-HP identical apart from carriage returns that `adb shell` adds to piped output.
- Clocks: host `date` 17:23:18 -05, D-SAM 17:23:07, D-HP 17:23:18 (the two tablets report `COT`, same offset). D-HU 17:45:55, same as host.
- The phone is not attached to the PC, so its airplane mode could not be verified from adb; the operator confirmed it. No session formed in any graded run.
- `logcat -c` is refused on D-SAM (`klogctl: Operation not permitted`), as in round 3; the capture starts at the launch.
- Hand step each run: the operator pressed Settings > About > Check for updates once. Opening Settings stops the wireless stack (`AapService: the settings screen is open, so the wireless stack stops until it closes`), which is also why no session formed once Settings was open.
- **Discarded runs:**
  - R1 first attempt (`r1-dhp-void1.txt`): D-HP auto-connected to the phone's Headunit Server at 192.168.1.5:5277 at 17:25:02 and completed SSL at 17:25:05, because the phone was not in airplane mode. No press was recorded. Not graded. Re-run after the operator put the phone in airplane mode.
  - R3 first attempt (`r3-dhu-void1-nowifi.txt`): `ping api.github.com` answered `unknown host`. D-HU's WiFi was `DISCONNECTED` with `Ignoring auto join disabled SSID: "Pegue Cdesta"`; there is no adb verb that re-enables auto-join, so the operator reconnected it by hand. Re-run.
- **R1 host capture lost the press.** D-HP's USB link re-enumerated during the run (`lsusb` showed device numbers 104, 105, 106 within about a minute), so the host `logcat` process died and its file (`r1-dhp.txt`, 827 lines) holds neither the start marker nor the press. The 16M device ring buffer still held it: `adb logcat -d` once the unit came back gave `r1-dhp-ringdump.txt` (OPENHU tag only) and `r1-dhp-ringdump-full.txt` (everything, 1145 lines), both containing `r1-dhp-start` at 17:41:11.839 and the failure at 17:41:24.999. The end marker was sent after the recovery, so the window is start marker to the failure line, not start to end. The greps for R1 below are on the ring dump.
- `am broadcast -f 0x00000020 -n ...` was accepted on API 17 and API 19 (result=0, `ok:true`); no need to drop the flag or use `-p`.

## R1 - D-HP (API 17)

**FAIL**

- Settings written: `stand-down-station-mode=2` (was 0). `log-level` 0, unchanged.
- Discard-rule check: clean on the graded run (ping to `api.github.com` answered 79.9 ms; `asked this unit to leave` 0; no auto-connect, no SSL handshake). One earlier attempt discarded, see Setup notes.
- Dialog the operator saw: title "Up to Date", body "Failed to check for updates. Please check your internet connection." (screenshot `r1-dhp.png`).
- `Update check failed:` count: 1. `no bundled HTTPS socket factory` count: 0. `Conscrypt security provider is active` count: 1 (17:40:51).
- Decisive lines:
  - `17:41:11.839 W OPENHU : AutomationMarker: r1-dhp-start`
  - `17:41:24.999 E OPENHU : UpdateChecker: Update check failed: java.security.cert.CertPathValidatorException: Trust anchor for certification path not found.`
  - `javax.net.ssl.SSLHandshakeException: java.security.cert.CertPathValidatorException: Trust anchor for certification path not found.`
  - First TLS frames: `org.conscrypt.SSLUtils.toSSLHandshakeException`, `org.conscrypt.ConscryptEngine.convertException`, ... `org.conscrypt.ConscryptEngineSocket.doHandshake:230` <- `org.conscrypt.PreKitKatPlatformOpenSSLSocketImplAdapter.startHandshake:324` <- `libcore.net.http.HttpConnection.setupSecureSocket:209` <- `UpdateChecker$check$2.invokeSuspend:117`
  - Caused by: `org.apache.harmony.xnet.provider.jsse.TrustManagerImpl.checkServerTrusted(TrustManagerImpl.java:197)` (the platform trust manager, called from `org.conscrypt.Platform.checkServerTrusted`)
- First TLS frame's package per the brief's table: **`org.conscrypt.` with no `com.android.` prefix, so the bundled Conscrypt ran the handshake and the fix engaged.**
- Measurement: 13.2 s between the start marker and the failure line, of which the operator's navigation into Settings is most (Settings opened 17:41:20; failure 4.2 s after that).

The failure moved from the handshake (rounds 2 and 3) to chain validation: the server hello was accepted and the chain was rejected by the device's 2013 platform root store. This is the different fix the brief described, a bundled root or a bundled trust manager.

## R2 - D-SAM (API 19)

**FAIL**

- Settings written: `stand-down-station-mode=2` (was 0). `log-level` 2, unchanged.
- Discard-rule check: clean, one press. WiFi before the launch and after the press: `mNetworkInfo ... state: CONNECTED/CONNECTED ... extra: "Pegue Cdesta"` both times. Ping to `api.github.com` answered (726 ms).
- Dialog: the operator reported "failed to check for updates"; screenshot `r2-dsam.png` shows title "Up to Date", body "Failed to check for updates. Please check your internet connection."
- `Update check failed:` count: 1. `no bundled HTTPS socket factory` count: 0. `asked this unit to leave` count: 0. `Conscrypt security provider is active` count: 1.
- Decisive lines (between `r2-dsam-start` 17:44:35.608 and `r2-dsam-end` 17:45:16.738):
  - `17:45:03.755 E OPENHU : UpdateChecker: Update check failed: java.security.cert.CertPathValidatorException: Trust anchor for certification path not found.`
  - First TLS frames: `org.conscrypt.SSLUtils.toSSLHandshakeException` ... `org.conscrypt.ConscryptEngineSocket.startHandshake:209` <- `org.conscrypt.KitKatPlatformOpenSSLSocketImplAdapter.startHandshake:324` <- `com.android.okhttp.Connection.upgradeToTls:146` <- `com.android.okhttp.internal.http.HttpsURLConnectionImpl.getResponseCode` <- `UpdateChecker$check$2.invokeSuspend:117`
  - Caused by: `com.android.org.conscrypt.TrustManagerImpl.checkServerTrusted(TrustManagerImpl.java:202)` (the platform trust manager)
- First TLS frame's package: **`org.conscrypt.`, the fix engaged.** `com.android.okhttp` still carries the request, but the socket it gets is the bundled Conscrypt's.

Same result as R1 on a different Android version: TLS 1.2 now works and the platform root store has no anchor for GitHub's chain.

## R3 - D-HU (API 34), regression guard

**PASS**

- Settings written: `stand-down-station-mode=2` (was 0). `log-level` 2, unchanged.
- Discard-rule check: one earlier attempt discarded (no WiFi, see Setup notes). The graded run: ping answered before the launch and again before the press (81.6 ms), `asked this unit to leave` 0, `MATCH! Starting AapService` 0, no SSL handshake.
- Dialog: title "Up to Date", body "You are already using the latest version (3.5.0-beta2)." (screenshot `r3-dhu.png`).
- `Update check failed:` count: 0. `no bundled HTTPS socket factory` count: 0. `Conscrypt security provider is active` 0, not expected and not graded on D-HU.

The change did not break the modern path. As the brief says, this passes whether or not the fix works.

## Anything the brief did not ask about

**1. The failure dialog is titled "Up to Date".** On D-HP and D-SAM the dialog for a failed check reads title "Up to Date" over "Failed to check for updates. Please check your internet connection." A reader who only glances at the title sees a success. The brief's PASS condition names "Up to Date" as a passing title, so a grader reading titles alone would have passed R1 and R2; the body and the `Update check failed:` line are what settled them. Worth a separate title for the error case.

**2. The message blames the internet connection.** Both units had a working connection (ping answered, D-SAM `CONNECTED`), and the failure was certificate trust. The text sends the user to the wrong place.

**3. Both old units fail identically now, at one step later.** Rounds 2 and 3: `sslv3 alert handshake failure` on the platform stack. Round 4: `Trust anchor for certification path not found`, thrown by the **platform** `TrustManagerImpl` (`org.apache.harmony.xnet` on API 17, `com.android.org.conscrypt` on API 19) called from the bundled Conscrypt. The candidate's factory uses the system default trust manager, so it inherits the platform root store. Observation, not verified here: a bundled trust anchor set, or the bundled Conscrypt's own trust manager with a bundled root, would be the next candidate. Which root GitHub's chain ends at was not read from the wire this round.

**4. `Conscrypt security provider is active` appears once per launch on D-HP and D-SAM** and never on D-HU, matching the brief.

**5. D-HP's USB link is unstable in a way that costs captures.** Three re-enumerations inside a minute killed the host capture mid-run. `adb devices` did not list the unit for about two minutes even though `lsusb` did (interface class 255/66/1, the ADB interface). It came back on its own. A run on this unit should read the device ring buffer after the press rather than trust the host capture alone.

**6. D-HU does not rejoin its WiFi after a period of being off** (`Ignoring auto join disabled SSID`). It needed a hand tap, as already recorded in the rig notes. It had a leftover `p2p-wlan0-0` group up (192.168.49.1) at the time.

**7. Captures.** Release `rig-evidence-check-for-updates`, asset `check-for-updates-round4-captures.zip`, sha256 `fdffa562a623c85ccac55a20aa3fdd266c5edd8eed5905cd67422a5152a98000` (1873831 bytes): host captures for the three graded runs and the three discarded ones, the D-HP ring-buffer dumps, and the three dialog screenshots. The verdicts rest on the lines quoted above.
