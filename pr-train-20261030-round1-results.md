# pr-train-20261030, round 1 results

**Candidate:** T = fork/test/pr-train-20261030 @ 6d038cc5016947bd28f50bcf9da268f840b2a044   **Baseline:** B = main @ 6ff42d9a06a3a0a00d2f50c67bc7d9b73c885572
**APK md5:** T dd787783b588ccafad474f5d8e6f2f3a / B f8df2705808d74509baddcb8247d1895
**Unit:** stage W: D-SAM (head unit, API 19) with D-POCO (phone); stage D: D-HU (head unit) with D-MOTO (phone)
**Date:** 2026-10-30   **Operator interventions:** 2 (the opening yes; the D-POCO forget of D-HU before WB)
**Round verdict:** 7 of 7 runs PASS; both points (W1, D1) PASS.

## Setup notes

- Main checkout could not check out the transfer branch (untracked `rig-lib/` and `progress.pid` would be overwritten); a worktree at the fork tip was used instead.
- Builds: T and B built once each in one batch (worktree detached at each sha, thermal wait 70 first, `build_hur_cool.sh`, MAX_WORKERS=2); Gradle daemon stopped afterwards. No unit tests run (the brief says the dev host ran them: 4402 tests, 1 order-dependent `WppTcpTlsTest` failure).
- Session run by `rig_session.py` (lib ca8a86a90); every run graded by its grade script, one attempt each, no retries.
- Pre-flight: `rig_preflight.sh` takes underscore role names (`D_SAM`, not `D-SAM`); with the hyphen it prints "invalid variable name" and reports OK without checking. Re-run with underscores: D_SAM, D_POCO, D_HU, D_MOTO all adb ok, wifi 1, bt 1, awake, no HFP/A2DP. PREFLIGHT OK.
- Quirk files not read for this round (the session scripts carry the unit handling); tooling quirks not consulted.
- Clocks (`clock-offsets.txt`): D-HU +748 ms (set to PC clock, was -1908 ms), D-MOTO -484 ms, D-POCO -295 ms, D-SAM offset unreadable. Timestamps quoted below are device-local, each from one unit, no cross-unit comparison made.
- Device files: the closing sweep could not list `/sdcard/Pictures/Screenshots/` and `/sdcard/DCIM/Screenshots/` on D-HU nor `/sdcard/DCIM/Screenshots/` on D-POCO (listing failed, not checked). Nothing the round wrote is known to be there.
- Closing baseline: `rig_requires.py baseline` read D-SAM and D-HP Bluetooth as `unknown` (BASELINE FAILED, 2 radios); read directly, `settings get global bluetooth_on` is 1 on both and wifi_on is 1 on D-HP. The closing pre-flight (below) is OK once my own orphaned `tail -F` on session.status was killed by pid.
- Closing pre-flight: D_SAM, D_POCO, D_HU, D_MOTO all wifi 1, bt 1, awake; D_MOTO HFP linked, no A2DP. PREFLIGHT OK.
- Operator interventions: 14:10:15 forget D-HU (Navegadortz2) on D-POCO before WB.

## Rsetup

**PASS**

8/8 conditions: closeIsOurs in the T DEX (2) and not in the B DEX (0); B installed on D-SAM and D-HU (md5 of the pulled APK); D-SAM reports commit 6ff42d9a0; wifi-connection-mode 3 read back; D-POCO holds no OpenHU-BT-Relay bond; D-HU settings backup taken.

## RWB, baseline B on D-SAM

**PASS**

11/11 conditions. The kill-to-relaunch gap was 4 s. B adopts the surviving group: L-ADOPT 1, same SSID before and after (DIRECT-xx-Navegadortz3), second SSL session 2, no network given back, station off, no Critical error, no FATAL EXCEPTION. The phone made 110 RFCOMM dials to a unit other than D-HU and the board.

## RW1, candidate T on D-SAM (point of the round)

**PASS**

14/14 conditions, T md5 matched, commit 6d038cc50 reported.

- Decisive lines (D-SAM, relaunch after the kill):
  - `14:17:16.413 WifiDirectManager.recreateNativeGroup: persistent profile purge: no profile of ou...` (L-PURGE 1)
  - `14:17:17.614 WifiDirectManager.onGroupInfoAvailable: group identity ssid=DIRECT-18-Navegadortz3 ...`
- Measurements: L-ADOPT 0 (the surviving group is not read); SSID changed across the kill (DIRECT-fs-Navegadortz3 to DIRECT-96-Navegadortz3 per the grade COND); second SSL session 2 on the new group; Type 3 writes after the relaunch 2; phone NETWORK_NOT_FOUND 0; kill-to-relaunch 4 s; RFCOMM dials 92; station off (L-COEX 0).

The line at 14:17:17.614 names `DIRECT-18-`; the grade script's own SSID read gave `DIRECT-96-`. Both differ from the pre-kill `DIRECT-fs-`. The grade condition (a new SSID) holds either way; the difference between the two reads is unexplained.

## RDB, baseline B on D-HU (D-MOTO holds the auto-disconnect MAC)

**PASS**

8/8 conditions. B reproduces the bug: the phone closed the held channel, the 5 s delay armed, and main kept the session.

- `14:20:21.778 AapService.armBtAutoDisconnect: Bluetooth auto-disconnect: A0:46:5A:97:E4:95 went away; ending the session in 5000ms unless it comes back.`
- `14:20:26.781 AapService.fireBtAutoDisconnect: Bluetooth auto-disconnect: not ending the session for A0:46:5A:97:E4:95 (up=true, ownCloseMs=5181).`
- L-KEEP 1, L-END 0, session still renders (L-THR 3), no FATAL EXCEPTION.

## RD1, candidate T on D-HU (point of the round)

**PASS**

10/10 conditions, T md5 matched.

- `14:22:11.890 armBtAutoDisconnect: ... went away; ending the session in 5000ms unless it comes back.`
- `14:22:16.895 fireBtAutoDisconnect: ... stayed away; ending the session the way the Exit button does.`
- `14:22:17.059 onDisconnected$standDownIfStillOwned: keeping the wireless bring-up down until something re-arms it.`
- Measurements: D1-cut marker 14:22:10.588; L-END 6 s after the cut (expected 5 to 8, limit 12); L-KEEP 0; L-THR 0 after the end (no frames); no FATAL EXCEPTION.

## RD2, candidate T on D-HU (phone comes back)

**PASS**

6/6 conditions: T md5 matched; L-BACK (the cancel when the phone comes back) 1; L-END 0 (the session was not ended); L-THR 3 (still renders); no FATAL EXCEPTION.

## Rcleanup

**PASS**

4/4 conditions: D-SAM got its network back (L-GIVE 2), B on D-SAM and D-HU (md5), D-HU settings equal the backup (0 differences).

## Anything the brief did not ask about

- The W1 SSID read disagreement above (DIRECT-18 in the log line, DIRECT-96 in the grade COND) is unexplained.
- `rig_preflight.sh` silently accepted hyphenated role names without checking them. Commit e2f604f81 (pushed while this round closed) makes the toolkit take both spellings; not re-tested here.
- Evidence: `pr-train-20261030-round1-captures.zip` in release `rig-evidence-pr-train-20261030`, sha256 d5bf5f8967ec9830392b5069b71a5d2a2bcf49503da0cb8e8a333d60d7fd9fb0.
