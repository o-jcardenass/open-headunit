# home-wifi-reconnect-mid-session, round 1 results

**Candidate:** `fix/home-wifi-reconnect-mid-session` @ `9676eee4e2ef7bdda90b995a87782c6db2f6479c`       **Baseline:** none (every comparison is inside the candidate)
**APK md5:** `afc30e6d1b94d3284dad8a8fd8ea0db0`, byte-identical on both head units (`apk_identity.sh` MATCH; `ACTION_QUERY_STATE` on D-SAM replied `commit` `9676eee4e2ef`; `StationStandDownReassertPolicy` present in the dex, 10 hits). JVM suite on that SHA: 2467 tests, 0 failures.
**Unit:** Block A head unit D-SAM (SM-T230, Android 4.4.2, API 19, station on 2462 MHz); Block B head unit D-HU (UNISOC MT50, Android 14, API 34, station `Pegue Cdesta` on 5500 MHz); phone D-POCO throughout. D-MOTO not used.
**Date:** 2026-10-02

## Round summary

| Run | Verdict |
|---|---|
| A0 / B0 identity | PASS (A0 read back `9676eee4e2ef`; B0 by `apk_identity.sh` md5 and the dex symbol) |
| A-R1a, A-R1b, A-R1c | PASS each (0 spontaneous rejoins) |
| A-R2 (D-SAM forced rejoin, hand) | **FAIL** |
| B-R1 | PASS (0 spontaneous rejoins) |
| B-R2 (D-HU forced rejoin) | PASS (one brief erratum, below) |
| B-R3 | PASS |
| A-R4 (NEVER control) | PASS on its five conditions, session lost 102 s in |
| R5 | **FAIL** (D-SAM A-R1b and A-R1c) |
| R6 | INCONCLUSIVE (measured, numbers below) |

## Setup notes

- **Branch/queue:** worked from `transfer/rig-rounds`, fast-forwarded to the fetched tip.
- **Read:** the template without 7b and 8, `rig-quirks/topics/tooling.md`, `topics/wifi.md`, `units/D-SAM.md`, `units/D-SAM-and-D-HP.md`, `units/D-HU.md`, `units/D-POCO.md`. Inventoried `hur-wifi-test-scripts/`.
- **Pre-flight (read-only), first attempt:** D-SAM wifi 1 bt 1; D-POCO wifi 1 **bt 0** (FAIL: needs Bluetooth on); D-HU wifi 1 bt 1. The operator confirmed that the session procedure itself toggles the phone's Bluetooth, and told the round to proceed. Nothing was switched on by the host.
- **Scripts:** `build_hur.sh`, `run_unit_tests.sh`, `apk_identity.sh`, `set_prefs_runas_host.py`, `set_hu_settings_host.py`. **New, left in `hur-wifi-test-scripts/`:** `home_wifi_reconnect_session.sh` (the brief's S procedure with pre-checks), `hwr_set_sam.sh`, `hwr_set_hu.sh` (settings writers with read-back), `hwr_steps_r2_sam.sh`, `hwr_steps_r2_hu.sh`, `hwr_steps_r3_hu.sh`.
- **Marker gate:** `ACTION_LOG_MARKER` is not in `CONFIGURING` on this candidate and `allow-external-configuration` was already `true`. Markers worked.
- **Clock:** D-SAM `date` against the host read 12 s behind at prepare time (10:04:40 against 10:04:52) and 0 to 12 s on later reads; its log timestamps are device time. Where a station sample (host clock) is compared with a log line, the offset recorded for that run is used.
- **Settings delta against the backups:** `stand-down-station-mode` 0 to 1 (ALWAYS) or 2 (NEVER) per run; D-HU `wifi-direct-band` 1 to 0; `connection-modes` rewritten from `{usb}` to `{usb, wifi}` on both units (see below); `video-profile-starvation-cap` absent on D-HU and `false` on D-SAM, deleted anyway. `station-stand-down-network-id` was `-1` or absent before every launch. D-HU `shared_prefs` owner read `u0_a176:u0_a176` (correct). `use-aac-audio` false, `audio-latency-multiplier` 16 (D-SAM) and 8 (D-HU), `audio-queue-capacity` 50 and 20: recorded, not changed.
- **Deviation 1, `connection-modes`.** Both units carried `connection-modes = {usb}`. With that, launch logs `WiFi is not one of the chosen connection modes. Not arming it`, and A-R1a only armed because the phone's Bluetooth arrival triggered the auto-start path. A-R1b then twice failed to arm. The brief's section 4 does not list this key; I wrote `{usb, wifi}` and confirmed the stack arms at launch (`Initializing WiFi Mode: NATIVE`). A-R1a ran before that change, with the old set.
- **Deviation 2, A-R1b first two attempts voided.** D-POCO's focus was `Settings$ConnectedDeviceDashboardActivity` (quirk: a stray Settings screen silences Gearhead). Files kept as `void-settings-screen.*`. The session script now sends HOME to the phone and requires the launcher.
- **Deviation 3, B-R2 first attempt voided.** Before it I rejoined D-HU's station with `cmd wifi connect-network` without `-d`. That re-enabled autojoin on the saved network, so the platform rejoined within 1 to 6 s of every disconnect, spent the budget during bring-up and left the station joined; the forced rejoins then did nothing. Files kept as `void-autojoin.*`. The script now passes `-d`, and the log shows `API_ALLOW_AUTOJOIN toggleState=false`. The rerun is the graded B-R2. This is a rig-state effect of the lever, not a result about the candidate. B-R3 and later rejoins used `-d`.
- **Deviation 4, D-SAM `logcat -c` is refused** (`klogctl: Operation not permitted`) and `logcat -G` does not exist on API 19, so each D-SAM capture begins with older buffer lines. Greps were windowed by marker or pid.
- **Deviation 5, appop.** D-HU read `SYSTEM_ALERT_WINDOW: default`, not `allow`. With the operator's approval it was set to `allow` before Block B. It was not reverted at the end of the round.
- **Deviation 6, hand steps.** D-SAM's Bluetooth cannot be switched by adb on API 19 (`svc` has no `bluetooth`); the operator turned it off by hand before Block B. D-HU's own Bluetooth self-reverted to on within seconds in Block A, as the quirk file records. The two forced rejoins on D-SAM were hand steps as the brief says.
- **Deviation 7, executor failure.** A background wait loop in one executor matched its own `pgrep` and spun for 20 minutes; B-R1 was then run directly from the host session with explicit paths. The same host rig lock applied.
- **Brief erratum:** B-R2 condition 2 predicts `config=unreadable` on D-HU after a re-assertion. The build prints `config=enabled` (read-back and every `again` line). Graded on the station leaving; the literal string differs. The rejoin lines read `config=current` and `disableNetwork returned false`, as the brief expected.
- **D-HU restore:** every ALWAYS session on D-HU ended with `the platform refused to re-enable this unit's WiFi network`, and the station did not rejoin by itself (no COMPLETED sample for 46 s or more). It was rejoined by `cmd wifi connect-network ... -d`. The brief names this as `main` behaviour.
- **Group churn on D-SAM before SSL:** A-R1a 2 `createGroup SUCCESS`, A-R1b 2, A-R1c 4 (three `the platform took it down` lines before the single SSL), A-R2 5 (two sessions), A-R4 2. None after the SSL in a session that held. No `MATCH! Starting AapService` anywhere.

## A-R1a, A-R1b, A-R1c (D-SAM spontaneous rejoin watch)

**PASS**

Per session (device clock; ALWAYS, `disableNetwork returned true`):

| Session | asked line | read-back `config=` | rejoin lines | COMPLETED samples in window | `could not re-assert` | SSL | createGroup before SSL |
|---|---|---|---|---|---|---|---|
| A-R1a | 10:07:37.256 mode=ALWAYS | `disabled` 10:07:38.788 | 0 | 0 | 0 | 1 (10:08:03.402) | 2 |
| A-R1b | 10:28:36.855 | `disabled` 10:28:38.767 | 0 | 0 | 0 | 1 (10:29:30.998) | 2 |
| A-R1c | 10:37:24.069 | `disabled` 10:37:25.931 | 0 | 0 | 0 | 1 (10:38:48.021) | 4 |

Spontaneous rejoins in the stand-down windows: **0** in all three, so R1 measured only that nothing misfired on this rig. COMPLETED samples with no rejoin line: **0**. The read-back is `disabled`, so `disableNetwork` stuck on D-SAM and H3 is refuted for this unit. No rejoin, so no WifiLock figure to report (H2 untested).

Quoted: `10:37:25.931 StationStandDown: this unit has left its WiFi network (config=disabled).`

## A-R2 (D-SAM forced rejoin, the positive control)

**FAIL**

The re-assertion itself worked each time. Station-joined event to `left its WiFi network again`: 1318 ms (rejoin 1), 1502 ms and 1511 ms (rejoin 2), all `config=disabled`. Conditions that failed:

1. Rejoin 2's line reads `re-assertion 3/3`, not `2/3`. The operator's single Connect for rejoin 1 produced two re-assertions: `11:56:12.235` (1/3), then `the platform rejoined ... 5755ms after the last re-assertion; checking again in 4245ms` at 11:56:17.407, then `11:56:21.701` (2/3).
2. A host-clock sample `11:56:30 COMPLETED` falls between the first `left ... again` line and the next marker (the platform rejoined 5.7 s after the first re-assertion).
3. **The session did not survive rejoin 2.** `11:57:01.450 WifiDirectManager: the group is being removed (the platform took it down)`, 10.8 s after marker `A-R2-rejoin-2` and about 3 s before the station-joined event at 11:57:04.223. Then `11:57:22.491 Incoming connection detected` and a second `11:57:29.778 SSL handshake complete`, before `A-R2-end` (11:58:13.751).

Notes: the first try of rejoin 1 saw no join (the operator was late) and was repeated once per the brief, so `A-R2-rejoin-1` appears twice. The budget did refill at the second SSL (`11:57:29.858 re-assertion 1/3`). On this single-radio unit the association itself appears to take the P2P group down before the fix can act; that is a measurement, not proven cause.

Report items: raise to first video: `A-R2-raised-1` 11:56:18.729 to first `Throughput over` with `rendered=17` at 11:56:21.011 (2.3 s); `A-R2-raised-2` 11:57:10.329 to `rendered=102` at 11:57:35.724 (25.4 s, includes the reconnection). `AapTransport: inbound link quiet` and `Providing credentials` lines within 10 s of each re-assertion: 0.

## A-R4 (D-SAM, NEVER control)

**PASS**

All five conditions hold over the capture (pid 6512): zero `asked this unit to leave`; one `the setting keeps this unit joined to its own WiFi network` (10:44:48.633); zero `the platform rejoined`; every station sample from armed to after reads COMPLETED; zero restore lines.

Flagged, not graded: the session was lost 102 s in with the station joined. `10:47:51 Throughput rendered=0`, `10:47:56 picture idle ... Android Auto has stopped sending`, `10:47:58.729 AapRead: body read returned 0 of 1404`, session state `link_lost`, then `10:47:59.179 Fatal signal 11 (SIGSEGV) ... thread MediaCodec_loop` killed the app process; a new process started 10:48:10 with no second session. 13 `AUDIO` underruns in the window.

## B-R1 (D-HU spontaneous rejoin watch)

**PASS**

One session, ALWAYS: `12:24:11.928 asked this unit to leave ... (mode=ALWAYS, station on 5500MHz, 5GHz=true, group asking for AUTO, disableNetwork returned false)`; read-back `12:24:14.492 ... (config=enabled)`. Spontaneous rejoins: **0**; `NetworkMonitor: Network available` lines: 0; all 41 station samples DISCONNECTED; `could not re-assert` 0. 1 createGroup, 1 SSL, 62 `Throughput over`, 10 `audio sink`, 0 `inbound link quiet`, 4 `StationScanMonitor` lines. The rig does not reproduce a spontaneous rejoin on D-HU. WifiLock figure: none (no rejoin).

## B-R2 (D-HU forced rejoin, scripted)

**PASS**

Graded on the rerun (see deviation 3).

| j | marker | `Network available` | rejoined line | assertion | left again | ms |
|---|---|---|---|---|---|---|
| 1 | 12:40:05.869 | 12:40:11.126 | 12:40:11.217 (+91 ms) | 1/3 | 12:40:12.766 | 1549 |
| 2 | 12:40:41.213 | 12:40:45.463 | 12:40:45.534 (+71 ms) | 2/3 | 12:40:47.037 | 1503 |

All 26 station samples non-COMPLETED. Zero `Incoming connection`, `SSL handshake complete`, `AapRead: Connection closed`, `the platform took it down` between `B-R2-session` and `B-R2-end`. `Throughput over` `rendered=` above 0 in the 30 s after each left-again line (147, 150, 150 and 147, 150, 151 frames per 5 s). `inbound link quiet`: 0. `Providing credentials` within 10 s of each re-assertion: 0. `config=enabled` where the brief says `unreadable` (erratum).

## B-R3 (D-HU budget, spacing, refill)

**PASS**

1. One `checking again in` line (12:45:10.526), between `B-R3-j2` (12:45:09.426) and `B-R3-j3` (12:45:28.082).
2. Three re-assertion lines in order: 1/3 `12:45:05.827`, 2/3 `12:45:15.856` (+10029 ms), 3/3 `12:45:32.165`.
3. One `after 3 re-assertions, so it is left joined for 256s` at 12:45:49.208; 12:45:49.2 + 256 s = 12:50:05.2, 0.6 s from the first 1/3 + 300 s.
4. Zero `the platform rejoined` lines between that line and the next re-assertion.
5. Every sample from 12:45:49 to 12:49:56 reads COMPLETED.
6. The refill: second `re-assertion 1/3` at `12:50:05.841`, 300.014 s after the first, left-again 1.49 s later (12:50:07.335).
7. Zero `Incoming connection`, `SSL`, `Connection closed`, `took it down` between session and end.

## R5 (a restore is never undone)

**FAIL**

Sessions: A-R1a, A-R1b, A-R1c, A-R2, B-R1, B-R2, B-R3.

- **A-R1a PASS**: user exit 10:11:33.336, `is enabled again` 10:11:33.346, 0 rejoined lines after, first COMPLETED sample 8 s later, COMPLETED to the after marker.
- **A-R1b FAIL**: user exit printed `10:33:05.367` but **no restore line in that process**. The process died silently at 10:33:12.594 (no Java frames), a new process started 10:33:13, printed `is enabled again` at 10:33:15.607, and a second session formed (SSL 10:33:20.201, after the phone reconnected). The station never read COMPLETED through 10:34:05 (samples SCANNING from 10:33:36).
- **A-R1c FAIL (timing)**: user exit 10:42:23.501, restore 10:42:23.541, first COMPLETED sample at 10:42:44, 20.5 s later (limit 15 s); zero rejoin lines; COMPLETED to the end.
- **A-R2 PASS**: user exit 11:58:15.963, restore 11:58:15.983, first COMPLETED sample about 10 s later, 0 rejoined lines after.
- **B-R1, B-R2, B-R3**: one user-exit line and one restore line each, `the platform refused to re-enable` (12:29:57.890, 12:41:45.511, 12:50:37.602); zero `the platform rejoined` after the restore; the station did not rejoin by itself, so condition 5 is not graded as the brief says.

## R6 (does the rejoin cause the stutter? measured, not graded)

**INCONCLUSIVE**

The NEVER arm lasted 102 s as a session, and its process was killed by a native crash, so the comparison is not like for like. Numbers between `<RUN>-session` and `<RUN>-end`:

| Session | min | `AUDIO` und / silent | `AUDIO1`, `AUDIO2` und / silent | lowest min depth | `inbound link quiet` lines (largest `longest=`) | median fps | `StationScanMonitor` lines |
|---|---|---|---|---|---|---|---|
| A-R1a (ALWAYS) | 3.10 | 0 / 0 | 0 / 0 | 0 ms | 1 (3256 ms) | 29 | 1 |
| A-R1b (ALWAYS) | 3.17 | 0 / 0 | 0 / 0 | 0 ms | 0 | 29 | 2 |
| A-R1c (ALWAYS) | 3.19 | 0 / 0 | 0 / 0 | 0 ms | 0 | 29 | 1 |
| A-R4 (NEVER) | 3.27 | 13 / 0 | 0 / 0 | 0 ms | 2 (2040 ms) | 0 (video stalled after the loss) | 1 |

The lowest `min` depth of 0 ms is the first sample of each session. Audio sink lines were present in all four sessions. Underruns per minute: ALWAYS sessions 0; NEVER 4.0 over the full window (13 over 3.27 min), concentrated in the 102 s before the link was lost.

## Evidence

Captures: release `rig-evidence-home-wifi-reconnect-mid-session` on `o-jcardenass/open-headunit`, asset `home-wifi-reconnect-mid-session-round1-captures.zip`, sha256 `a7e0699140a90608b79b56f880a961fe49873de332556dbcbabb9d4004c5655b`. It holds every `<RUN>.txt`, `<RUN>.poll.txt`, the voided captures (`void-*`), the grade files and `prepare.json`. Script logs that printed a settings file, the settings backups and the APK are left out because they carry group passphrases.

## Anything the brief did not ask about

- **D-SAM process deaths.** Pid 5230 (A-R1b) died 7 s after the user exit with no log frames, which is why no restore line printed; pid 6512 (A-R4) died with a `SIGSEGV` in `MediaCodec_loop` (`invalid address ... passed to dlfree`) right after a session ended. Both are D-SAM codec teardown deaths on the Marvell decoder; the first took the restore with it. Whether the restore is lost when the process dies at that moment is a defect candidate for the candidate's restore path, separate from the re-assertion logic.
- **The fix's limit on D-HU.** `disableNetwork` returns false on API 34, so a re-assertion is a bare `disconnect()`. When the saved network has autojoin enabled the platform rejoins in 1 to 6 s and the budget is spent in about 30 s (the voided first B-R2: re-assertions 1/3 to 3/3 at 12:31:44, 12:31:54 and 12:32:04, with the budget spent line at 12:32:05). With autojoin disabled (the rig's own state) nothing rejoined spontaneously in B-R1.
- **Single-radio D-SAM.** The platform removed the P2P group in two of the three rejoin observations (A-R2 rejoin 2, and the pre-SSL churn in A-R1c), before any re-assertion could run.
- **D-SAM station rejoin latency after the restore** varied: 8 s (A-R1a), 20.5 s (A-R1c), more than 90 s after a dry-run exit.
- The pre-flight check and the settings read-back that this round ended up needing (`connection-modes`, phone on launcher, station joined, stack armed before the phone's Bluetooth) are now in `home_wifi_reconnect_session.sh`; a brief for this thread should name `connection-modes` in section 4.
