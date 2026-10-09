# check-for-updates: round 5 results

**Candidate:** fork `fix/update-check-old-tls` @ `2689d1a23` (two commits over `8f88d9a6`)       **Baseline:** round 4 (`b77bf005`, md5 `582e2114a1c2cfcaaeb0a1acdc2e95d7`)
**APK md5:** `9da335d0cea4ba8afc98b44c61f83784`, pulled with `adb pull` from D-HP and D-SAM and read with `md5sum` on the device for D-HU; differs from round 4
**Symbols in the candidate:** `BundledRootTrustManager` 17 (dex strings), `res/raw/usertrust_` 2
**Unit:** D-HP (HP Slate 7 Plus, API 17), D-SAM (SM-T230, API 19), D-HU (UNISOC MT50, API 34)
**Date:** 2026-09-29

## Round verdict: PASS

Bundling GitHub's root fixes the update check on both old units. R1 and R2 no longer fail on `Trust anchor for certification path not found`: the check completes and the dialog reads "You are already using the latest version (3.5.0-beta2)". R3 PASS as a regression guard. R4 UNTESTABLE. Two caveats on evidence (Setup notes): the R2 and R3 dialogs are graded on the operator's report because my screenshots missed them, and the failure-dialog title change was not observed.

## Setup notes

- Build: detached checkout of `2689d1a23`; `git log --oneline 8f88d9a6..HEAD` showed exactly 2 commits (`2689d1a23` "Update check: trust GitHub's root CA on old Android" over `b77bf005a`), `git status --short` empty. Built with `build_hur.sh`. Identity: `BundledRootTrustManager` 17 hits in `classes*.dex` strings, `res/raw/usertrust_` 2 entries in the APK.
- Install: `adb install -r` on D-SAM and D-HU (Success). On D-HP `adb install -r` failed twice with `failed to read copy response` because its USB link dropped mid-transfer; fell back to `adb push` to `/data/local/tmp` plus `pm install -r`, which also lost the device before printing a result, and the package was confirmed by pulling `/data/app/com.andrerinas.headunitrevived-1.apk` once the unit re-listed (md5 `9da335d0...`, identical to the D-SAM pull and the D-HU device-side md5). The temp file was removed afterwards.
- Scripts used: `build_hur.sh`, `set_prefs_runas_host.py` (D-SAM, D-HP), `set_hu_prefs.sh` (D-HU). None added or changed.
- Settings: `stand-down-station-mode=2` written on every unit and read back before each launch. `log-level` left as found (D-SAM 2, D-HP 0, D-HU 2). Backups taken first. The backups held `stand-down-station-mode=0`; restored to `0` on all three. D-HU's file differs from the backup only in the two `wifi-direct-*-bssid` values, which the app rewrote itself (owner `10176:10176` checked). D-HP and D-SAM identical apart from element order and blank lines.
- Phone in airplane mode for the whole round (operator confirmed; not verifiable from adb). No session formed in any run: no `SSL handshake complete` or `Incoming connection` in any window, `MATCH! Starting AapService` 0 on D-HU.
- `logcat -G 16M` is not an option on API 17 or API 19 (`invalid option -- G`), and `logcat -c` is refused on D-SAM (`klogctl: Operation not permitted`), as in round 4.
- **R1 host capture lost the press again.** D-HP re-enumerated after the press, so `r1-dhp.txt` (988 lines) holds the start marker but neither the press nor the end marker. The ring dump taken once the unit re-listed (`r1-dhp-ringdump.txt`, 1293 lines) holds the start marker (18:17:06.019), the Settings screen opening (18:19:24.529) and the update check code loading (18:19:27.9). The end marker could only be sent afterwards (18:20:18.459, in `r1-dhp-ringdump-final.txt`). R1 is graded on the ring dump, window start marker to end marker, and the same counts on the whole dump are also 0.
- **Dialog screenshots.** R1's screenshot (`r1-dhp.png`) shows the dialog. On R2 and R3 my screenshots were taken after the operator had already dismissed it (`r2-dsam.png`, `r2-dsam-late.png` and `r3-dhu.png` show the Settings list with no dialog). A success logs nothing, so the logs cannot show it either. R2 and R3 dialog text is the operator's report (title "Up to Date", body "You are already using the latest version"), with the log counts as the machine evidence. The brief's R1 PASS names a screenshot; R2 was not re-run for one.
- R3: the `Settings screen is open` line (18:45:13.840) precedes the `r3-dhu-start` marker (18:45:25.233) in the capture; the marker broadcast was logged late. Counts are 0 over both the marker window and the whole capture.
- Hand step each run: one press of Settings > About > Check for updates, no `input tap`.
- R4 not run, see its section.
- No discarded runs.

## R1 - D-HP (API 17)

**PASS**

- Settings written: `stand-down-station-mode=2` (was 0). `log-level` 0, unchanged.
- Pre-press `ping api.github.com` answered, 85.0 ms. `StationStandDown: asked this unit` 0. No session formed.
- Dialog (screenshot `r1-dhp.png`): title "Up to Date", body "You are already using the latest version (3.5.0-beta2)."
- `Update check failed:` 0. `no bundled HTTPS socket factory` 0. `Conscrypt security provider is active` 1 (18:17:00.289).
- Decisive lines:
  - `18:17:06.019 W OPENHU : AutomationMarker: r1-dhp-start`
  - `18:19:24.529 I OPENHU : AapService: the settings screen is open, ...`
  - `18:19:27.939 I dalvikvm: Could not find method android.content.pm.PackageManager.getInstallSourceInfo, referenced from method ...UpdateChecker.isPlayStoreInstallation` (the check running)
  - `18:20:18.459 W OPENHU : AutomationMarker: r1-dhp-end`
- Round 4 on the same unit: `CertPathValidatorException: Trust anchor for certification path not found` at the same step. It is absent here.
- APK md5 `9da335d0cea4ba8afc98b44c61f83784`; `BundledRootTrustManager` 17, `usertrust_` 2.

## R2 - D-SAM (API 19)

**PASS**

- Settings written: `stand-down-station-mode=2` (was 0). `log-level` 2, unchanged.
- WiFi before the launch and after the press: `mNetworkInfo ... state: CONNECTED/CONNECTED ... extra: "Pegue Cdesta"` both times. Pre-press ping answered, 1325 ms.
- Dialog: operator reported title "Up to Date", body "You are already using the latest version"; not captured on screenshot (Setup notes).
- Window `r2-dsam-start` 18:20:37.998 to `r2-dsam-end` 18:44:18.704: `Update check failed:` 0, `no bundled HTTPS socket factory` 0, `StationStandDown: asked this unit` 0. `Conscrypt security provider is active` 1 (18:20:32.452).
- Settings opened 18:44:01.497; the check code loaded at 18:44:05.761; no failure line followed. Round 4 logged the failure 4.2 s after Settings opened on D-HP and about 28 s into the window on D-SAM.
- APK md5 `9da335d0cea4ba8afc98b44c61f83784` (pulled); identity counts as above.

## R3 - D-HU (API 34), regression guard

**PASS**

- Settings written: `stand-down-station-mode=2` (was 0). `log-level` 2, unchanged.
- Pre-press ping answered, 79.9 ms; WiFi up. `asked this unit to leave` 0. `MATCH! Starting AapService` 0.
- Dialog: operator reported title "Up to Date", body "You are already using the latest version"; not captured on screenshot.
- `Update check failed:` 0. `no bundled HTTPS socket factory` 0. `Conscrypt security provider is active` 0 (not expected above API 20).
- APK md5 `9da335d0cea4ba8afc98b44c61f83784` (device-side `md5sum`).

## R4 - D-HP, failure dialog title

**UNTESTABLE**

Taking D-HP off the network needs the access point's cable pulled or the router switched off, which is not harmless to the rest of the rig, and `svc wifi` does not work on D-HP. The retitled failure dialog ("Check for updates") was therefore not observed on any unit this round, and no `UnknownHostException` or `ConnectException` line was produced.

## Anything the brief did not ask about

**1. D-HP's USB link is the round's biggest cost.** It dropped during the install (three failed transfers) and again right after the press, killing the host capture, exactly as in round 4. The ring dump after the press is what made R1 gradable; the brief's §3 addition (dump after every press) was needed, but it must be taken as soon as the unit re-lists, and the end marker cannot be sent until then. `adb devices` omitted the unit for up to about 5 minutes at a time while `lsusb` still showed it (device numbers 117, 122, 123 seen).

**2. Success is silent.** The update check logs nothing on success, so a dialog that was dismissed before the screenshot leaves no machine evidence; only the absence of the failure line and the operator's report. A screenshot taken while the operator holds the dialog open, or one INFO line on success, would close this.

**3. Slow first check on D-SAM is not established.** The D-SAM ping took 1325 ms and the dialog appeared within the 13 s between the check starting (18:44:05.761) and my end marker (18:44:18.704), per the operator. Not timed.

**4. Captures.** Release `rig-evidence-check-for-updates`, asset `check-for-updates-round5-captures.zip`, sha256 `a2b07cff84cfe1b06583fbc75db88b56203298677e17c3ee74d8d2d2b8ba6744` (653226 bytes): the three host captures, the D-HP ring dumps (`r1-dhp-ringdump.txt`, `-ringdump2.txt`, `-ringdump-final.txt`), the four screenshots and the three settings backups. The verdicts rest on the lines quoted above.
