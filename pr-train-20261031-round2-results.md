# pr-train-20261031, round 2 results

- Candidate: T `8e0452ddc` (APK md5 `e925f1aa31d4f9f761211aaac81d95c4`, the round 1 APK, reused, not rebuilt)
- Baseline: none
- Units: D-HU, D-POCO, D-SAM, board E-1. D-MOTO not used (see Setup notes). Date: 2026-10-31.
- Lib `8191b7c4c`. Final session 12:58:22 to 13:04:02 (attempt 3). Operator interventions: 2 (see Setup notes).
- Totals (attempt 3): 4 PASS, 0 FAIL, 1 INCONCLUSIVE (setup, E1, E3, E5, cleanup).

## Setup notes

- Pre-flight before the first session: OK for D-HU (wifi 1, bt 1), D-MOTO (wifi 1, bt 0), D-POCO (wifi 1, bt 1); no strays; lock free. D-SAM was not named in the pre-flight roles.
- Read for this round: the trimmed template was not re-read in full and no quirk file was read (the session is script-driven); only the session `view` and the brief's own block were used.
- The T APK was copied from round 1's evidence folder, md5 checked equal; no build.
- Clocks: D-HU -110 ms, D-POCO -372 ms, D-SAM -26355 ms (no root shell, left; subtract from D-SAM times).
- **Attempt 1** (12:39 to 12:42): setup PASS 8/8, E1 PASS 14/14, E3 killed 35 s in. The operator needed D-MOTO and asked to skip the round. The session and the run were stopped (`rig_run.sh reap`), and the brief's cleanup script was run (PASS, exit 0). No verdict from this attempt is used.
- **Attempt 2** (12:51 to 12:57), run on request without D-MOTO, from a local copy of the brief with the `unit D-MOTO` line removed (the brief on the branch is unchanged; this round only). Graded setup PASS, E1 FAIL, E3 INCONCLUSIVE, E5 INCONCLUSIVE, cleanup PASS. **E1 and E3 verdicts were artifacts of my relaunch**: I did not archive `taps.log`, `marks.txt` and `serial-E1.txt` after attempt 1. E1's "6 taps" was 3 from attempt 1 plus 3 from attempt 2 (`awk '$1>="12:51:25"' taps.log | wc -l` = 3). E3's "F-BOOT 3" counted the three board boots between attempt 1's E3-go (12:42:07) and attempt 2's E3-end (12:55:00); the true window 12:53:36 to 12:55:00 held 0. Not used.
- **Attempt 3** (12:58 to 13:04): all evidence files archived to `attempt1/` and `attempt2/` first, the same trimmed brief copy, every run from scratch. These are the verdicts below.
- D-MOTO is bonded to D-HU. Without the brief's "radios off" line the session did not check D-MOTO's radios in attempts 2 and 3, and D-MOTO was not in `adb devices` at the closing pre-flight of attempt 1. No stray dial from it showed in E1 or E3 (L-RELAY 0, no second handshake).
- Operator interventions: (1) the opening A to D message and its answers; (2) the operator's request to stop the round for D-MOTO and rerun without it. No `OPERATOR` line fired in any session.

## Rsetup

**PASS**

8/8 conditions: T.apk md5 `e925f1aa31d4f9f761211aaac81d95c4`, T installed on D-HU by md5, D-HU reports commit 8e0452ddc, prior APK and settings backed up, E-1 booted and advertises, E-1 app region matches the v1.0.1 firmware.

## RE1

**PASS**

14/14. The relay row is in the API 34 list (E5's control): 1. `Esp32Relay.xml` holds an address and is enabled; D-POCO holds a bond to the board (78:1C:3C:2B:AC:EE) made by the helper with no hand step (H-PAIR 1); S-CRASH 0, F-PFAIL 0; injected taps 3 (limit 5).

## RE3

**PASS**

23/23, the point of the round. Board saved, relay switch on, board held in reset.
- F-BOOT 0: the board stayed in reset.
- D-HU and D-POCO on one /24 before the arm (hu=192.168.1.9, poco=192.168.1.20); LISTEN socket on :5277 on D-POCO at the arm; D-HU pings D-POCO.
- L-DISC 1 (a Headunit Server session found); L-RELAY 0 (not a relay session).
- L-THR 4 from 30 s after SSL to the exit (session up); no second handshake before the exit (0); L-PUW, L-PUF, L-PUS, L-GIVE, L-PROBE all 0; S-CRASH 0; injected taps 0.
- E-1 advertising again after the release.

## RE5

**INCONCLUSIVE**

COND 5 FAIL: the list was searched for esp32: 0 ge 1. The same on both graded attempts (12:56:51 attempt 2, 13:03:36 attempt 3). The other conditions passed: T and commit 8e0452ddc on D-SAM, wifi-connection-mode 3 read back, the dump is our settings screen, no relay row on API 19 (0), S-CRASH 0, the relay screen left to the settings list (2 ge 1). The `am start ... --es extra_search_query esp32` printed "Warning: Activity not started because the current activity is being kept for the user" (`E5-start.txt`), and `ui-E5-list.xml` has no `text="esp32"`. The extra is defined in T (`SettingsActivity.kt:264`). The cause is unexplained: the observation is that the activity was already on top and the query did not reach the list. Because the list was not filtered, "no relay row" is a dump of an unfiltered list and not the filtered check the brief asks for.

## Rcleanup

**PASS**

5/5: the prior APK and settings back on D-HU and D-SAM, no board bond on D-POCO, the board advertising.

## Anything the brief did not ask about

- E5 needs a different way to apply the search text (the brief's launch leaves the settings activity on top and does not take the extra). A brief fix, not a build finding.
- A relaunch of the same session in one evidence folder must archive `taps.log`, `marks.txt` and the serial capture first, or the run-counted conditions inherit the previous attempt's lines.
- Evidence: release `rig-evidence-pr-train-20261031`, asset `pr-train-20261031-round2-captures.zip`, sha256 `14cf3e1f25433335efb49096b397a8a2f1d00672d3c317be1f439cf3c7b35272`.
- Closing pre-flight: OK (D-HU and D-POCO wifi and bt on; D-SAM wifi and bt on, screen asleep; D-MOTO not attached, not checked); baseline radios OK; no strays; device files 0 left. Two stale `tail` pids of this round monitors were killed by pid before the final check.
