# projection-teardown-and-relays, round 3 results

**Candidate:** fix/projection-teardown-and-relays @ a70ba7e24fff276d5610c35bd23150f64ae1d3a2 (tree 6af9b23e47f67e28b697810e4251c32605a70c39)       **Baseline:** main @ 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9
**APK md5:** 7312cb55e2f0ce3bf6f515eee2dd18b9 / 50d3c116369f99784908b2380cdec421
**Unit:** D-HP (HP Slate 7 Plus, API 17) with D-POCO (Gearhead 17.9.664004-release)
**Date:** 2026-10-06

## Setup notes

- Operator confirmed presence and the house WiFi before R0. Pre-flight (`rig_preflight.sh D_HP:wifi D_POCO:wifi`): D_HP wifi_on=1 bt=1 Awake, D_POCO wifi_on=1 bt=1 Awake, `PREFLIGHT OK`. Quirk files read: topics/tooling.md, units/D-HP.md, units/D-POCO.md, units/D-SAM-and-D-HP.md, topics/gearhead.md.
- Hand steps (`hand-steps.log`), 7 in total, each followed by `SERVER_RESTARTED ... new_lines=1`:
  - R4E-B-c1 15:57:13 / 15:57:27; R4E-B-c2 15:58:19 / 16:01:34; R4E-B-c3 16:03:04 / 16:03:14
  - R4E-C-c1 16:05:01 / 16:05:21; R4E-C-c2 16:06:01 / 16:06:10; R4E-C-c3 16:06:50 / 16:07:38
  - R4-C-pre 16:08:42 / 16:08:54. R4L-C needed none (R4-C did not wedge).
- Stage block: `SERVER_UP_STAGE`. D-HU was on adb, so the stage block took it off the air (ACTION_EXIT, force-stop, Bluetooth and WiFi disabled) and the final block re-enabled both. D-HP serial CNU350BGBJ (1 hit in `rig_devices.sh`), D-HP 192.168.1.22, D-POCO 192.168.1.8.
- settings.xml: backup diffed against round 2's after stripping CR and whitespace, semantic delta empty (raw diff differs on line endings only). As found: video-codec H.264, resolutionId 1, use-aac-audio true, audio-latency-multiplier 16, audio-queue-capacity 50, enable-audio-sink true, last-connection-ip 192.168.1.5, `auto-connect-last-session` absent (`auto_last_session` 0 in every cycle), log-level 0 as found (the round wrote 2). Restore: key read back 0, restored file identical to the backup.
- Gearhead log tags: `phone-logtags.txt` reads VERBOSE for all four tags. They added car_gal_vd lines (54 to 76 per window) and xfer_vd 0 in every window.
- R0: SHA, tree, merge-base and 4 commits match. Built with `r0_build.sh` (round 2's, paths changed to round 3). Unit tests: 2714 tests, 0 failures, 0 skipped from the JUnit XML, `HeldServerSocketTest` and `SameEndpointConnectPolicyTest` present. Identity greps: cand.apk adopt string=1 HeldServerSocket=12 SameEndpointConnectPolicy=5 FeedLoopPolicy=3 `HeldServerSocket$$ExternalSyntheticLambda`=0; base.apk all 0. QUERY_STATE on the candidate: `"commit":"a70ba7e24fff"`. Baseline install read back live md5 = 50d3c116... in `apk-check.log`.
- Self-tests printed exactly `IN_WINDOW 390 1 0 NONE`, `EARLY -3890 1 1 2 1 0 1 1` and `hu_connected=1 duplex=1 handoff=1 requested_version=1 version_ok=1 end_of_stream=0 xfer_proxy=1 server_running=0 last_line=version_ok`.
- Scripts: copied `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_crash.py`, `r0_build.sh` (edited); saved `ptr_lib.sh`, `ptr_race.py`, `ptr_phone.py` from the brief unchanged; added `stage2.sh` (the section 7 blocks in order, run under `flock /tmp/ohu-rig.lock`). Evidence lives in `hur-wifi-test-scripts/projection-teardown-and-relays-round3/`, as in rounds 1 and 2, not under `evidence/`.
- **Deviation, R4E-B cycle 1 is not in `R4E-B.race.json`.** Its `R4E-B-c1-srv-restart` and `R4E-B-c1-go` markers never reached the capture (D-HP cold-started the app 17 s after the launch, and the markers went out within 2 s of the force-stop). With no go marker the classifier built no cycle, so `last_early` printed an IndexError and `R4E-B.progress` line 1 is empty. The race JSON's `cycle` 2 and 3 are really cycles 2 and 3. I graded cycle 1 by hand from the capture: launch `START u0 ... MainActivity` 15:57:31.3, `ACTION_CONNECT` 15:57:48.7, no `Found Headunit Server` line, `Version response received` 15:57:49.9, `SSL handshake complete` 15:57:50.1, no ECONNRESET. Class EARLY, ssl 1.
- Every phone-side count is windowed by the `windows.tsv` host epochs. D-HP counts are windowed by the run's go and end markers. Clocks: `clocks.log` shows the phone 1 s behind host at every check, the unit 0 to 1 s ahead. I did not align the version exchange per cycle because no cycle reset.
- `ps aux | grep -c "[l]ogcat"` printed 0 after every run, and no `adb logcat` is running now. Thermal: R4E-B thermal_max=71C throttle_delta=0, R4E-C 62C 0, R4-C 60C 0, R4L-C 58C 0. R0 build and tests ran at 68C with throttle_pkg 2456 to 2458 (the counter rose by 2 during the build, before any capture).
- Round time: about 15 min of captures, far under the 1 h 50 min estimate, because every restart was answered within 10 to 135 s.

## R0

**PASS**

- Settings written: none.
- Radio state: n/a.
- Discard-rule check: n/a.
- Decisive lines: all in Setup notes. Last condition: `ncdfe.txt` reads 0 for `R4E-B.logcat`, `R4E-C.logcat`, `R4-C.logcat` and `R4L-C.logcat` (no `failed-attempts/` captures exist).
- Measurements: 2714 tests, 0 failures; md5 7312cb55... vs 50d3c116....

## R4E-B

**PASS**

- Settings written: `HPKEYS` (wifi-connection-mode 1, log-level 2, view-mode 0, onboarding-version 2, connection-modes wifi, starvation-cap deleted); `Q` cycle adds wifi-connection-mode 0.
- Radio state: WiFi enabled on D-HP and D-POCO (`Wi-Fi is enabled`).
- Discard-rule check: clean. Thermal: `thermal_max=71C throttle_delta=0`.
- Decisive lines (D-HP time), cycle 2, the only baseline cycle with ssl 0:
  - 16:01:41.142 `NetworkDiscovery: Found Headunit Server on 192.168.1.8:5277`
  - 16:01:41.472 `AutomationReceiver: ...ACTION_CONNECT` (330 ms after Found)
  - 16:01:42.062 `ConnectionArbiter: the socket from 192.168.1.8 (WIRELESS_HANDSHAKE) refused while 192.168.1.8:5277 (USER) is in flight`
  - 16:01:53.462 and 16:02:06.602 `Handshake: the peer accepted the connection and then sent nothing at all`

| cycle | trigger | class | verb after go | after Found | stood_down | version_response | econnreset | peer_silent | ssl | picture | phone hu_connected / handoff / version_ok / xfer_proxy | phone last_line |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 (by hand) | E | EARLY | 17.4 s from the launch line | none | n/a | 1 | 0 | 0 | 1 | n/a | 1 / 1 / 1 / 1 | version_ok |
| 2 | E | IN_WINDOW | 2.95 s | 330 ms | 1 | 0 | 0 | 2 | 0 | none | 1 / 1 / 0 / 0 | end_of_stream |
| 3 | Q | EARLY | 16.42 s | none | 1 | 1 | 0 | 0 | 1 | 24.07 s | 1 / 1 / 1 / 1 | version_ok |

Other fields: `ncdfe` 0 and `died` 0 in every cycle, `aap_header_fail` 0, `auto_last_session` 0, `server_running` 0 in every window, `phone_lines` 27121. Cycle 2 phone: `critical_error` 1 (`CAR.SERVICE: Critical error`), `end_of_stream` 1. `car_gal_vd` 66, 22 and 76; `xfer_vd` 0.

Cycle 2 is not an early sample: the verb landed after the Found line and the baseline raced (round 1's mechanism, two silent accepts), and the phone logged `hu_connected` 1 for it. The brief's "not a failure" rule for an E cycle after Found applies.

## R4E-C

**PASS**

- Settings written, radio state: as R4E-B.
- Discard-rule check: clean. Thermal: `thermal_max=62C throttle_delta=0`.
- Decisive lines: none needed; every cycle formed a session with no reset.

| cycle | trigger | class | verb after go | stood_down | version_response | econnreset | ssl | ssl after go | picture after go | phone hu_connected / handoff / version_ok / xfer_proxy | last_line |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | E | EARLY | 3.30 s | 1 | 1 | 0 | 1 | 5.42 s | 12.11 s | 1 / 1 / 1 / 1 | version_ok |
| 2 | E | EARLY | 3.18 s | 1 | 1 | 0 | 1 | 5.11 s | 12.01 s | 1 / 1 / 1 / 1 | version_ok |
| 3 | Q | EARLY | 16.45 s | 1 | 1 | 0 | 1 | 17.47 s | 24.13 s | 1 / 1 / 1 / 1 | version_ok |

`ncdfe` 0, `died` 0, `aap_header_fail` 0, `peer_silent` 0, `server_running` 0 in every window, `phone_lines` 32269, `car_gal_vd` 66, 66 and 66, `xfer_vd` 0.

**Pre-registered reading: round 2's reset does not reproduce.** Both builds' E cycles that reached SSL have `econnreset` 0, and every Q cycle has ssl 1. Hypothesis 2 (the first session after a restart is reset) is refuted: all 6 cycles were first sessions after a proved restart and 5 formed SSL, the sixth (R4E-B cycle 2) failed by the baseline race, not by a reset. Hypothesis 1 is not supported (an E dial at 3.2 to 3.3 s after the launch on the candidate, with no Found line yet, reached SSL). Hypothesis 3 is not supported either. Round 2's reset needs a verb 3.9 s before the Found line plus a stand-down, and on D-HP the Found line arrived 2.6 to 4.5 s after the launch here; I did not reproduce that ordering. The two builds differ on the same trigger only through the baseline's in-window race (R4E-B cycle 2).

## R4-C

**PASS**

- Settings written: `HPKEYS`. Preflight (after cycle 1): `mode=AUTO`, `viewMode=SURFACE`, `sink=3`, codec `OMX.Nvidia.h264.decode`, `connection-modes=wifi starvation-cap-keys=0`.
- Radio state: WiFi enabled on both units.
- Discard-rule check: clean (3 `SSL handshake complete`, one per cycle; no wedge marker). Thermal: `thermal_max=60C throttle_delta=0`.
- Decisive lines (D-HP time), cycle 1 (ADOPT):
  - 16:08:58.752 `NetworkDiscovery: Found Headunit Server on 192.168.1.8:5277`
  - 16:08:59.402 `AutomationReceiver: ...ACTION_CONNECT`
  - 16:08:59.682 `CommManager: 192.168.1.8:5277 adopting the socket discovery already opened`
  - 16:08:59.712 `WifiLauncherSharedServices: 192.168.1.8:5277 is no longer held for discovery; not dialling it`
  - 16:09:01.552 `SSL handshake complete`
- Cycle 2 (JOIN): 16:09:36.562 Found, 16:09:36.942 verb, 16:09:37.472 `CommManager: 192.168.1.8:5277 is already connecting; not preempting it`, 16:09:38.462 SSL. Cycle 3 (JOIN): 16:10:10.452 Found, 16:10:11.042 verb, 16:10:11.352 the join line, 16:10:12.922 SSL.

| cycle | trigger | class | after Found | route | adopt | join | not_held | refused | preempt_socket | stood_down | peer_silent | econnreset | ssl | ssl after go | picture after go | ncdfe | phone hu_connected / duplex / handoff / version_ok / xfer_proxy / server_running | last_line |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | F | IN_WINDOW | 650 ms | ADOPT | 1 | 0 | 1 | 0 | 0 | 1 | 0 | 0 | 1 | 5.47 s | 12.59 s | 0 | 1 / 1 / 1 / 1 / 1 / 0 | version_ok |
| 2 | F | IN_WINDOW | 380 ms | JOIN | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 6.35 s | 12.70 s | 0 | 1 / 1 / 1 / 1 / 1 / 0 | version_ok |
| 3 | F | IN_WINDOW | 590 ms | JOIN | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 5.33 s | 11.67 s | 0 | 1 / 1 / 1 / 1 / 1 / 0 | version_ok |

All 3 cycles are IN_WINDOW with `same_endpoint` true: 1 took the adopt path and 2 took the join path. `refused_bringup` 0, `died` 0, `auto_last_session` 0, `auto_connect` 1 per cycle. `R4-C.inwin` = 3, `R4-C.listening-after` = UP, `phone_lines` 25222. The phone's accept line (`Head unit connected`) was present, so no accept-line drift. The stop rule ended the run after cycle 3. Clocks: phone 1 s behind host, unit 0 to 1 s ahead (`clocks.log`), no difference above 1 s.

## R4L-C

**PASS**

- Settings written: `HPKEYS`. Radio state: WiFi enabled. Discard-rule check: clean. Thermal: `thermal_max=58C throttle_delta=0`.
- Decisive counts in `seg R4L-C-go R4L-C-mid`: `is already connecting; not preempting it` 0, `AapService: session state connecting` 1 (16:11:32.842). Phone `bringup` window: `hu_connected` 1, `last_line` version_ok. `SERVER_UP_after_R4L`.
- Report only: adopt line in go-mid 0. Phone `go-mid` window: `hu_connected` 1, `handoff` 1, `requested_version` 0, `end_of_stream` 1, `hu_disconnected` 1, `critical_error` 4 (first `1791321089.804 CAR.SERVICE: Critical error 3 detail: 50 msg: io error`), `last_line` handoff. `SSL handshake complete` after `R4L-C-go`: 0 (the one SSL in the capture is the bring-up at about 16:10:58). The projection did not come back inside the 45 s window: 16:11:30.132 `session state disconnected (link_lost)` after the verb at 16:11:30.072, `connecting` 16:11:32.842, `connected` 16:11:32.862, `disconnected (link_lost)` 16:11:43.292, `connected` 16:12:09.482 (no picture line after 16:11:01.602).

## Anything the brief did not ask about

- The redial after a USER connect to a live session (R4L-C) leaves the phone at `handoff` and never reaches the version exchange, with Gearhead logging `Critical error ... io error`. The grade stands on the brief's two conditions, but the session did not recover a picture within the window. Round 2 said nothing about this because its R4L-C attempts crashed first.
- R4E-B cycle 2 reproduced the baseline's round 1 failure (a refused WIRELESS_HANDSHAKE socket, then two silent accepts) with the dial 330 ms after Found. The same ordering on the candidate (R4-C) formed SSL 3 of 3.
- Every E cycle on D-HP lands the verb after the app is actually ready, not 1 s after the launch: the cold start takes 3 s (candidate) to 17 s (cold baseline install) before `ACTION_CONNECT` is delivered. The `E` trigger therefore stays EARLY only when discovery has not yet found the phone; it is not a controllable 1 s offset on this unit.
- The markers sent right after `am force-stop` can be lost on D-HP (R4E-B cycle 1, two markers). Anything that grades from go markers needs a marker sent after the app is up or a fallback anchor.
- Captures: asset `projection-teardown-and-relays-round3-captures.zip` on release `rig-evidence-projection-teardown-and-relays`, sha256 a9bfee98123ea5489080f6f9f752daf13d1adccbad6877310f4a0ae29478aed8.
