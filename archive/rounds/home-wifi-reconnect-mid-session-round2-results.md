# home-wifi-reconnect-mid-session, round 2 results

**Candidate:** `fix/home-wifi-reconnect-mid-session` @ `9676eee4` (unchanged from round 1)   **Baseline:** none, every comparison is inside the candidate
**APK md5:** `afc30e6d1b94d3284dad8a8fd8ea0db0` (live on D-SAM, not reinstalled; `ACTION_QUERY_STATE` replied `commit":"9676eee4e2ef"`, `versionCode":115`; dex symbol `StationStandDownReassertPolicy` present)
**Unit:** D-SAM (SM-T230, API 19) as head unit, D-POCO as phone. Station on the home network: 2462 MHz (`F4.8D.4F`) and 5500 MHz (`F4.8D.4E`); P2P group on 2412 MHz in every session.
**Date:** 2026-10-02

## Setup notes

- **Pre-flight.** First run FAILED: D-SAM Bluetooth off (`bluetooth_on=0`), screen asleep. Hand step (operator turned it on in Android Settings), second run `PREFLIGHT OK`:
  `D_SAM ok wifi=1 bt=1 Awake HFP:none A2DP:none`, `D_POCO ok wifi=1 bt=1 Awake HFP:..33:59 A2DP:up`. No stray logcat.
- **Clocks.** `adb -s D-SAM shell date` read `Fri Oct 2 14:56:00 COT 2026` against the host's `14:56:12`. No offset to correct.
- **Identity.** md5 and dex symbol matched, so no build, no unit-test run, no reinstall (brief section 1). Commit read back through `ACTION_QUERY_STATE` after the runs.
- **Quirk files read:** `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-SAM.md`, `rig-quirks/units/D-POCO.md`. Wifi, bt and audio topic files not read.
- **Scripts.** `home_wifi_reconnect_session.sh` (round 1) lacks the brief's `L0` record, windowed greps, retry naming and extraction block, so two scripts were **added** to `hur-wifi-test-scripts/`: `home_wifi_reconnect_session_r2.sh` (session procedure S, pre-checks: phone on launcher, D-SAM `bluetooth_on=1`, station `COMPLETED`, settings read back via `hwr_set_sam.sh`, stack armed after `L0`; records `<RUN>.L0`; extracts `.win/.timeline/.sta-bssid/.pids`; kills capture by pid) and `hwr_steps_r2b_sam.sh` (the A-R2 forced-rejoin steps with 90 s `waitfor`, one retry, 15 s settle, HOME, `ACTION_RAISE_PROJECTION`). `hwr_set_sam.sh` reused unchanged (it uses `set_prefs_runas_host.py`).
- **Brief error, fixed.** The extraction `awk '{print $4}'` over `Trying to associate with <B>` returns the word `with`; the BSSID is field `$5`. A-R5's `.sta-bssid.txt` was regenerated with `$5` (`F4.8D.4F`); later runs used the fixed script. Station join detection in this report used the BSSIDs `F4.8D.4F` and `F4.8D.4E`.
- **Settings.** Backup `settings-backup.xml` (sha256 `87729380b2d4...`). Delta written by `hwr_set_sam.sh`: `stand-down-station-mode` 0 to 1 (ALWAYS) for A-R5/A-R2a/A-R2b and 2 (NEVER) for A-R4b/A-R4c; `log-level` 2, `wifi-connection-mode` 3, `native-ap-transport` 0, `connection-modes` {usb, wifi} already as required. Read-only: `station-stand-down-network-id` -1 before every launch, `use-aac-audio` false, `audio-latency-multiplier` 16, `audio-queue-capacity` 50, `onboarding-version` 2, `allow-external-configuration` true, `video-profile-starvation-cap` read `false` and was deleted (delete half only) by the setter.
- **Windowed greps.** `logcat -c` is refused on D-SAM, so every count is on `<RUN>.win.txt` (from `L0` to `<RUN>-after`). The host-clock `sta` samples were taken (`<RUN>.poll.txt`) and are not used for any verdict.
- **Hand steps.** Operator joined the home network from Android's WiFi list at each cue (4 rejoins). A-R2a rejoin 2 try 1 (cue 15:10:01) timed out at 90 s with no `Trying to associate`; HOME, then the retry cue (marker `A-R2a-rejoin-2-retry`, 15:11:37) produced the join. Counted as one Connect.
- **Ordering of `S` step 9.** `-end` marker then `ACTION_DISCONNECT` landed while A-R2a was re-handshaking (see R5). Contamination counts: no run had `MATCH! Starting AapService` (0 in all five). `createGroup SUCCESS`: A-R5 3, A-R2a 3, A-R2b 1, A-R4b 1, A-R4c 2; every extra group in A-R5 and A-R2a is before the first SSL or follows a `P2P-GROUP-REMOVED`, so none counts as contamination. SSL counts: A-R5 1, A-R2a 2 (second one after `-end`, see R5), A-R2b 1, A-R4b 2 (reconnect after loss, see R6), A-R4c 1. No `Fatal signal` line in any window.
- **Run order and extra runs.** A-R5, A-R2a, A-R2b, A-R4b, then A-R4c under the brief's stop rule (A-R4b lost its session 43 s after `A-R4b-session`).
- Evidence dir on the rig: `hur-wifi-test-scripts/evidence/home-wifi-reconnect-mid-session-round2/`. Captures: asset `home-wifi-reconnect-mid-session-round2-captures.zip` on release `rig-evidence-home-wifi-reconnect-mid-session` (sha256 `47df76d6bfa0664f3b79a9b4e739a83596b8acd4d4c9c5d1c7d9201f478b1abd`).

## R2 (A-R2a and A-R2b): forced rejoin, the re-assertion mechanism

**PASS**

Four hand-forced rejoins, four station joins (one per Connect), all four answered.

| Session | Join | Station join (`CTRL-EVENT-CONNECTED`) | Our `locally_generated=1` disconnect | Answer line | Join to disconnect | Join to answer | Station freq | Group freq |
|---|---|---|---|---|---|---|---|---|
| A-R2a | 1, `F4.8D.4F` | 15:08:57.505 | 15:08:58.606 | 15:08:58.656 `leaving it again (re-assertion 1/3, disableNetwork returned true)`, WifiLock held 78904 ms | 1101 ms | 1151 ms | 2462 | 2412 |
| A-R2a | 2, `F4.8D.4E` | 15:11:45.069 | 15:11:46.180 | 15:11:46.210 `re-assertion 2/3`, WifiLock held 246472 ms | 1111 ms | 1141 ms | 5500 | 2412 |
| A-R2b | 1, `F4.8D.4F` | 15:17:24.190 | 15:17:25.291 | 15:17:25.341 `re-assertion 1/3`, WifiLock held 85451 ms | 1101 ms | 1151 ms | 2462 | 2412 |
| A-R2b | 2, `F4.8D.4F` | 15:18:22.867 | 15:18:23.968 | 15:18:23.978 `re-assertion 2/3`, WifiLock held 144300 ms | 1101 ms | 1111 ms | 2462 | 2412 |

Conditions, per session, over the stand-down window to `-end`:

1. Reachability: 2 joins in each session after the `-rejoin-<j>` markers; 4 in total (no INCONCLUSIVE).
2. Every join answered: 4 of 4 within 5000 ms (max 1151 ms), exactly one answer line each, no exemptions used, no `locally_generated=0` line in either window (0 in each).
3. Deferrals: no `checking again in` line in either session, nothing to resolve.
4. Every re-assertion leaves: the `locally_generated=1` disconnect lies 50 ms before each re-assertion line (within 2000 ms); each is followed within 1502, 1501, 1510, 1502 ms by `this unit has left its WiFi network again (config=disabled).` (15:09:00.168, 15:11:47.711, 15:17:26.842, 15:18:25.479); zero `after a re-assertion` lines in all windows.
5. Budget and spacing: `1/3` then `2/3` in both sessions, no `3/3`, no `left joined for` line; spacing between the two re-assertions 167.554 s (A-R2a) and 58.637 s (A-R2b), both over 9950 ms.
6. Zero `could not re-assert the stand-down` lines.
7. No `P2P-GROUP-REMOVED` within 10 s after any re-assertion. The only removals in the sessions were 15:07:17.458 (before the session) and 15:13:32.774 in A-R2a, 106.6 s after re-assertion 2/3 and 168 s after the last station `Trying to associate` (15:11:44.668).

Reported, not graded:
- Joins per operator Connect: 1 in all four (round 1's two-joins-per-Connect did not recur).
- **Session survival.** A-R2b survived both rejoins: 1 SSL in the whole window, 0 `AapRead: Connection closed` or `WiFi read timeout` between `-session` and `-end`, 0 `the platform took it down`. A-R2a lost its session on rejoin 2: `10-02 15:12:04.348 W ... AapRead: WiFi read timeout (15000ms) - connection lost.` 19.3 s after the 5500 MHz join (15:11:45.069), the group on 2412 MHz, no `P2P-GROUP-REMOVED` and no `took it down` until 15:13:32. After the loss the unit re-poked, re-handshook and SSL'd again at 15:14:01.592.
- `inbound link quiet` lines within 10 s after a re-assertion: none in any of the four cases. The ones that exist: A-R2a one at 15:09:11 (`dead=1259ms`, `longest=1259ms`, 12 s after re-assertion 1, outside 10 s); A-R2b three at 15:16:31 (`longest=3270ms`), 15:17:31 (`longest=1308ms`, 6.1 s after re-assertion 1 and inside 10 s), 15:18:31 (`longest=1575ms`, 7.5 s after re-assertion 2 and inside 10 s).
- `-raised-<j>` to the first `Throughput over` line with `rendered` above 0: A-R2a j1 1812 ms (15:09:15.963 to 15:09:17.775); A-R2b j1 4084 ms (15:17:43.589 to 15:17:47.673); A-R2b j2 1061 ms (15:18:41.675 to 15:18:42.736); A-R2a j2 not meaningful (session lost, first frame after the re-SSL at 15:14:07.558).

Decisive lines (A-R2b join 1): `15:17:24.190 CTRL-EVENT-CONNECTED - Connection to F4.8D.4F completed`, `15:17:25.291 CTRL-EVENT-DISCONNECTED bssid=F4.8D.4F reason=3 locally_generated=1`, `15:17:25.341 StationStandDown: the platform rejoined this unit's WiFi network 134s into the stand-down (config=current, WifiLock held for 85451ms); leaving it again (re-assertion 1/3, disableNetwork returned true)`, `15:17:26.842 this unit has left its WiFi network again (config=disabled).`

The candidate answers every station join, with a join-to-answer delay of 1111 to 1151 ms; nothing in the app's behaviour is the cause of the A-R2a session loss (section 2 of the brief predicted it for this unit, and the same reaction in A-R2b held the session).

## R5 (A-R5, A-R2a, A-R2b): a restore is never undone

**INCONCLUSIVE**

Per session: A-R5 PASS, A-R2b PASS, A-R2a not reached (INCONCLUSIVE). No session FAILed. The brief's "every session passes" is not met only because A-R2a never produced the user-exit line.

- **A-R5, branch (a): PASS.** `15:04:38.483 AapService: Native AA user exit. Stopping active launcher.`, `15:04:38.613 StationStandDown: this unit's WiFi network is enabled again ...` (130 ms, same pid 10285), 0 `the platform rejoined` lines after it, station join `15:04:43.538 CTRL-EVENT-CONNECTED - Connection to F4.8D.4F` = **4925 ms** after the restore line, no station `locally_generated=1` disconnect after it to `A-R5-after` (15:05:44.367).
- **A-R2b, branch (a): PASS.** `15:19:47.700 Native AA user exit`, `15:19:47.720 ... is enabled again` (20 ms, pid 12177), 0 `rejoined` lines after, join `15:19:52.665 Connection to F4.8D.4E completed` = **4945 ms** after the restore, no disconnect afterwards to `A-R2b-after` (15:20:47.848). No `Fatal signal`, no `has died`.
- **A-R2a: not reached.** The session was already lost at 15:12:04 (R2). At `-end` (15:13:05.998) the unit was poking again; `ACTION_DISCONNECT` landed 15:13:06.949 (`Disconnect action received.`) in the middle of a new Bluetooth handshake, so there was no `Native AA user exit` line; the phone then reconnected (second SSL 15:14:01.592). The restore came from `ACTION_EXIT` instead: `15:14:10.401 ACTION_EXIT`, `15:14:11.672 StationStandDown: this unit's WiFi network is enabled again`, after the `-after` marker, and the station read `COMPLETED` at 2462 MHz afterwards with `station-stand-down-network-id` back to -1. No crash in this session, so branch (b) did not apply either.
- No Marvell `Fatal signal` appeared in any of the five sessions this round.

## R6 (A-R5 against A-R4c): does the rejoin cause the stutter?

**MEASURED**

Not graded (the brief grades R6 only for INCONCLUSIVE, which does not apply: both sessions have audio sink lines and the NEVER session, A-R4c, was not lost). The audible stutter itself is in no audio counter.

| | A-R5 (stood down) | A-R4c (NEVER, joined) |
|---|---|---|
| Session minutes (`-session` to `-end`) | 3.37 | 3.47 |
| `audio sink` underruns, AUDIO / AUDIO1 / AUDIO2 | 0 / 0 / 0 | 0 / 0 / 0 |
| `silentCycles` (same channels) | 0 / 0 / 0 | 0 / 0 / 0 |
| Lowest `min` depth after the first sample (AUDIO, AUDIO1, AUDIO2) | 684, 0, 0 ms | 0, 0, 0 ms |
| `inbound link quiet` lines, largest `longest=` | **0**, - | **7**, 7607 ms |
| Median fps (`Throughput over`), lines | 29 (40) | 29 (41) |
| `StationScanMonitor: station scans:` lines | 1 | 1 |
| Station freq / group freq | 2462 / 2412 | 2462 / 2412 |

The joined session shows seven inbound stalls (largest 7607 ms) where the stood-down session shows none, which is the direction the field report predicts, with the group and the station on different channels on one radio. Underruns are 0 in both, so the audible stutter is not visible in the audio counters (as the adaptive-audio round already found for weak units). A-R4b (the first NEVER attempt, station 5500 MHz at launch) was not used: its session was lost 43 s after `-session`.

## A-R4b / A-R4c gate and stop rule

**PASS**

A-R4b: gate passed, session lost, void for R6. A-R4c: gate passed, session intact.

- Gate on both: one `StationStandDown: the setting keeps this unit joined to its own WiFi network, so the group shares the radio with it.` line (A-R4b 15:21:24.054, A-R4c 15:27:46.797) and 0 `asked this unit to leave`.
- A-R4b loss: `15:23:19.536 E ... AapRead: body read returned 0 of 2470 expected - an unknown number of bytes were consumed, so the stream can no longer be framed` after `A-R4b-session` (15:22:36.564), then a second SSL 15:23:55.822 and `15:24:14.820 AapRead: WiFi read timeout (15000ms) - connection lost.` No `Fatal signal`. Station was on 5500 MHz at launch. Rerun as A-R4c as the stop rule says; A-R4c ran the full 180 s plus 60 s, ending with `15:32:38.562 AapRead: Connection closed (EOF). Disconnecting.` after the `-end` disconnect.

## A0. Identity

**PASS**: `commit":"9676eee4e2ef"`, md5 `afc30e6d1b94d3284dad8a8fd8ea0db0` (see header).

## A-R5 report items (R5 sample, stood-down arm)

- Station joins inside the stand-down window (14:59:41 to 15:04:38): **0** (about 4.95 minutes).
- Read-back: `14:59:41.343 this unit has left its WiFi network (config=disabled).` The `asked this unit to leave` line reads `station on 0MHz`.
- Group `P2P-GROUP-STARTED ... freq=2412` (14:59:42.314); the station before stand-down on 2462 MHz (`dumpsys wifi` `mFrequency: 2462` at pre-check; `F4.8D.4F`). Three `createGroup SUCCESS` before the single SSL (15:00:51.231), with `P2P-GROUP-REMOVED` at 15:00:22.333 and 15:00:39.590 in between, the settling this unit does.

## Anything the brief did not ask about

- **The brief's one-join-per-Connect premise now holds on this build (4 of 4), and round 1's two-joins case did not repeat.** The 5500 MHz band steering seen in A-R2a join 2 (`F4.8D.4E`) is the only join on 5500 MHz in the rejoins, and it is the one that was followed by the session loss; A-R2b's two rejoins both landed on 2462 MHz and both held the session. Two sessions is a thin sample for that association.
- **`ACTION_DISCONNECT` issued while a new handshake is in flight does not end the cycle.** In A-R2a the unit finished the in-flight handshake and reconnected after `-end` (SSL at 15:14:01.592), so a script that sends DISCONNECT at the end of a session that was just lost sees no user-exit line. The restore still ran on the later `ACTION_EXIT`.
- **A-R4b's loss is a different signature from the rejoin losses**: a framing error (`body read returned 0 of 2470 expected`) with no station event in the window (`Trying to associate` only at 15:25:16, after the loss), under NEVER. No stand-down ran in that session, so it is not attributable to the candidate's stand-down.
- Script defect found and fixed: the `S` helper in round 1's script used `waitfor ... 1` (line 1) rather than `L0`, which would have matched the buffer left from earlier runs on a unit that refuses `logcat -c`. The round 2 script uses `L0`.
- The first A-R2a rejoin-2 try timed out at the operator's end (no association within 90 s of the cue); the retry cue worked. The script's 90 s window is tight for a hand step, and a 120 s window would avoid the retry.
