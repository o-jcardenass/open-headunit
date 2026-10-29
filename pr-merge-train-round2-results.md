# pr-merge-train, round 2 results: wireless lifecycle and Bluetooth routes (stage B)

**Candidate:** `fork/test/pr-merge-train` @ `1b71618176668fe6ab01bcee7a80677a5ab0b34f` (tree `10b0734592f9f9b6a33e57388f61a9408cf62fd9`)
**Baseline:** `main` @ `f9a72d480d51105870f62df6f0a40a1276e98a34` (round 1's B APK)
**APK md5:** S `a62575ec529855e9297af850a6999d78` / B `7dbddf19ee63fa0d0772dcf32878b337`
**Unit:** D-HU (UNISOC MT50, Android 14, rooted adbd) as head unit; D-POCO (POCO X3 NFC) as phone, Spotify playing.
**Date:** 2026-10-26

## Setup notes

- Evidence: release `rig-evidence-pr-merge-train` on the fork, asset `pr-merge-train-round2-captures.zip`, sha256 `5d8a42d6d200a6172bd4e3a36e9f57759bcb78ebda91e1f33db3e196e14b45d4`.
- Rounds 1 to 3 ran back to back in one session; settings, radios and worktree were restored once, after round 3: D-HU `settings.xml` restored from round 1's backup (byte-identical, owner `u0_a176:u0_a176`), build worktree removed, no logcat left. Closing pre-flight: D_HU wifi=1 bt=1 Awake HFP:none A2DP:none; D_POCO wifi=1 bt=1 Awake HFP:none A2DP:none (opening read D_POCO HFP:..33:59 A2DP:up, the links had dropped by the end, not restored); `rig_cleanup.sh`: no strays, lock free. D-SAM Bluetooth was never changed (still on).
- Quirk files read: topics/tooling.md, topics/audio.md, units/D-HU.md, units/D-POCO.md (the lifecycle.md and wifi.md the brief names were only skimmed, lifecycle.md first 40 lines). Settings keys are round 1's R0 keys (keys_R01.sh), written before each run with the app stopped and read back.
- Build: S built from tree `10b07345` (dex symbol count 34, B 0); B APK reused from round 1 (same md5). Installs checked with `apk_identity.sh`; `ACTION_QUERY_STATE` commit check not run.
- Scripts: `lib2.sh` verbatim from the brief, plus one-round `run2.sh` (pre-checks and the run) and `analyze.py` (per-cycle timings from the HU capture), in `rig-data/rounds/pr-merge-train-round2/scripts/`.
- X0 first launch refused by pre-check (Spotify slow to start after play key, 3 s wait); wait raised to 8 s, relaunched.
- X1 first attempt VOID: the ACTION_LOG_MARKER for X1-c1-go was enqueued 13:35:41.669 and its receiver finished (75 ms) but printed no 'AutomationReceiver: ...' line, so waitn never saw the window and the script waited 120 s (NO_SESSION) though the session actually formed (SSL 13:35:51.206). Rerun once as X1r. First attempt kept in captures.
- X1r is the graded X1 run; first X1 attempt void (see above). L-ZX: brief expected silence on D-HU, but the PR's XyAutoMediaSession.report prints 'XYAuto projection media policy: IDLE/UNAVAILABLE' at INFO 11 times per run (0 on main, X0). Graded FAIL on the stated condition; nothing else of the module acted. [ZBT] 0.
- Phone 'Critical error' (brief says MUST be 0): X0 15, X1r 13, all GH.WirelessStartup/CAR.SERVICE lines within 1.1 s after each deliberate Exit (io error), same on main and S, none elsewhere in the window. Brief expectation is incompatible with a test that Exits 3 times; not graded as a FAIL, recorded.
- Brief erratum: "Phone `Critical error` MUST be 0" cannot hold for a test that Exits three times. See above: 15 (X0) and 13 (X1r) lines, all within 1.1 s of an Exit, from the phone's own Gearhead (`GH.WirelessStartup`, `CAR.SERVICE ... io error`).
- Host thermal: max package 68C (X0), 67C (X1 and X1r), throttle counter 0.

## X0

**PASS**

- Settings: round 1 R0 keys. Radios: phone wifi and bluetooth cycled by `ph_off`/`ph_on`.
- Window 13:31:46 to 13:34:23; cycles at 13:32:15, 13:32:53, 13:33:38. The control passes in the sense that it measured what it should: the race was reached in all three cycles and the defect did not show.

| Cycle | go to L-ARM | L-EXIT relative to L-ARM | L-GRP | Race reached | L-EXIT/L-GRP after L-ARM | go to L-SSL | last L-TP rendered |
|---|---|---|---|---|---|---|---|
| c1 | 0.43 s | -0.77 s | none | yes | 0 | 9.43 s | 151 |
| c2 | 0.44 s | -0.80 s | none | yes | 0 | 16.40 s | 150 |
| c3 | 0.43 s | -0.79 s | none | yes | 0 | 16.16 s | 151 |

- `L-GRP` (`CommManager teardown complete. Stopping WiFi Direct group.`) did not print in any cycle on either arm; the group was kept across the Exit. L-ZX 0 on main.
- On `main` the three cycles show no sign of the reported defect on this unit: every Start armed 0.4 s after the Exit command and formed a session.

## X1

**FAIL**

- Graded run is X1r (the second attempt); the first attempt is void, see Setup notes. Window 13:41:20 to 13:43:57.
- Failed condition: L-ZX must be 0. `XYAuto` printed 11 times in the window (0 in X0), all from `XyAutoMediaSession.report`, for example `13:41:29.702 XyAutoMediaSession.report | XYAuto projection media policy: IDLE (property state, not HFP/A2DP status)` followed by `13:41:29.703 ... UNAVAILABLE ...`. `[ZBT]` 0. The lines are INFO reports of a state on a unit with no XYAuto stack; nothing in the capture shows the module changing behaviour. The brief expected silence, so the stated condition fails.
- Every other X1 condition holds:

| Cycle | go to L-ARM | L-EXIT relative to L-ARM | L-GRP | Race reached | L-EXIT/L-GRP after L-ARM | go to L-SSL | last L-TP rendered |
|---|---|---|---|---|---|---|---|
| c1 | 0.44 s | -0.77 s | none | yes | 0 | 15.95 s | 148 |
| c2 | 0.43 s | -0.74 s | none | yes | 0 | 15.96 s | 151 |
| c3 | 0.42 s | -0.74 s | none | yes | 0 | 5.53 s | 150 |

- Whole window: L-T3 4, each followed by an L-CS `SUCCESS(0)` before the next L-T3 (4 of 4). L-WD 0, L-EP 0, L-ZX 11 (all XYAuto), FATAL EXCEPTION 0.
- First (void) attempt, for completeness: cycles 2 and 3 read the same (ARM 0.4 s after go, EXIT 0.7 s before ARM, session formed), cycle 1 formed a session at 13:35:51.206 (ARM 13:35:42.177, EXIT 0.77 s before it, about 9.5 s after the Exit command) but its marker was lost; c2 and c3 SSL 16.31 s and 15.89 s, last rendered 150 and 149. XYAuto 11 there too.

## Anything the brief did not ask about

- The three PRs that should be inert on D-HU are not silent: the XYAuto policy report prints at INFO on every session start and end (IDLE, then UNAVAILABLE), unlike main.
- A `ACTION_LOG_MARKER` broadcast (X1-c1-go) was delivered to the receiver (75 ms) but printed no `AutomationReceiver:` line, 0.4 s after an `ACTION_DISCONNECT` that was still tearing down. One occurrence in 12 cycle markers; the cause is not known.
- Race window: on both arms the old Exit's cleanup line prints 0.74 to 0.80 s before the new arm, never after it, so the 0.3 s Exit-to-Start gap did not reproduce the described ordering on this unit with either build.
