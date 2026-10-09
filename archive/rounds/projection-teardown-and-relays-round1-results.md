# projection-teardown-and-relays, round 1 results

**Candidate:** fix/projection-teardown-and-relays @ 24b4bc0a7eb93498a58f0e4d22b7e4c5d0eb2b37       **Baseline:** main @ 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9
**APK md5:** 6025ed5ecda7c71d4850bc96c086c41e (candidate) / 50d3c116369f99784908b2380cdec421 (baseline)
**Unit:** D-HU (UNISOC MT50, Android 14) with D-POCO; D-HP (API 17); D-SAM (API 19)
**Date:** 2026-10-06

## Setup notes

- Pre-flight: D-POCO Bluetooth read off and D-HP WiFi was unreadable at the table; the operator said go, and the brief's own radio helpers (phone_air_on/off) set the radios per run. D-HP's serial this round is CNU350BGBJ (the template's), not the brief's 0123456789ABCDEF.
- Quirk files read: tooling, D-HU, D-HP, D-SAM-and-D-HP, D-SAM, projection.
- Scripts: ohu_lib.sh, ohu_setkeys.py, ohu_pair.py, ohu_returns.py copied from pr-1046-round1; ptr_lib.sh, ptr_returns.py, ptr_crash.py, ptr_race.py extracted from the brief at 79e819bd0 (thermal gate included); added stage1.sh, stage2.sh, build_hur_cool.sh (build_hur.sh under thermal_guarded.sh).
- Fixes to the brief's scripts, both from the first R3-B attempt: (1) th_pkg matched both pkg= and throttle_pkg=; now takes the first. (2) The preflight hotspot check counted the line "HotspotManager: Setting hotspot enabled=false" (the app switching a hotspot off at Native AA start); it now excludes that one line. (3) hu_open set RUN after apk_check, so thermal files were named for the previous run. The first R3-B attempt failed that preflight (setup failure, not graded); its files are in failed-attempts/. The re-run passed.
- Deviations: R0 was built by the host in Bash, not an executor. The candidate build and unit tests ran before the thermal update at 79e819bd0, ungated; the first baseline build reached 98C (throttle counter 2430) and was killed and rebuilt under thermal_guarded-style gating. Stage runs were driven by session scripts run by the host in the background, not by rig-executor.
- D-HU settings.xml equals the pr-1046 backup except for XML serialisation of empty strings. Audio keys as found: use-aac-audio=false, audio-latency-multiplier=8, audio-queue-capacity=20, enable-audio-sink=false. connection-modes on D-HU: wifi,self. SYSTEM_ALERT_WINDOW: default. Clock: D-HU and host agree to the second.
- Run settings: view-mode and codec per the brief; D-HU shared_prefs owner u0_a176:771.
- Stage 2 (D-HP) changes: D-HP's logcat has CRLF line ends, so `$`-anchored window matches on its captures need `tr -d '\r'`; I used that for the by-hand R5P and R4L windows (ptr_race.py and ptr_crash.py use `\s*$` and were unaffected). The D-HP backup differs from pr-1046's only by serialisation of empty strings and by the absent video-profile-starvation-cap key. D-HP as found: video-codec H.264, resolutionId 1, view-mode 0, wifi-connection-mode 1, log-level 0 (written 2), use-aac-audio true, audio-latency-multiplier 16, audio-queue-capacity 50, enable-audio-sink true. D-SAM as found: video-codec H.264, resolutionId 0, view-mode 2, wifi-connection-mode 3, native-driver-selection-mode 1 (written 0), use-aac-audio false, audio-latency-multiplier 16, audio-queue-capacity 50, enable-audio-sink true. D-HP and D-POCO shared 192.168.1.x (phone 192.168.1.8). D-HP clock COT, host -05, same instant.
- Capture-loop leak: on D-HP and D-SAM `cap_stop` killed the restart-loop subshell before its `adb logcat` child, so children outlived their run (4 after the baseline arm, 1 after R1-C, 1 after R4L-C, and three orphaned loops after the failed R6 attempts). I killed them by pid between segments and patched `ptr_lib.sh` (pkill the children first). The orphaned loops reopen the capture path, so a run started while one was alive could have taken duplicate lines: I stopped the third R6-B attempt, killed them, and re-ran it clean. The baseline D-HP captures may carry a few extra appended lines after their run_end markers; every count was computed at run_end.
- R6 setup: the first two attempts of R6-B and the first of R6-C formed no session (`SESSION_FAIL_SSL`): the phone answered 3 wake pokes over HFP and never opened the Android Auto channel (the app's own warning, 3 occurrences per attempt, says its Android Auto is most likely bound to another Bluetooth device that advertises the service). The operator named the cause as D-HU's Bluetooth radio `Navegadortz2` on D-POCO; the phone's Bluetooth dump did list `Navegadortz2` beside D-SAM's `Navegadortz3` before the operator unpaired it. This is consistent with, but was not proven by, a control: no run was made with the bond present and D-SAM otherwise identical to a passing one. After the operator unpaired it, R6-C's second attempt (13:01:40) and R6-B's third attempt (13:04:55) each formed a session and ran 5 cycles. So the R6 pair was run C-then-B, with an operator step during the round that the brief says does not happen. Failed attempts are in failed-attempts/.
- Evidence: release asset `projection-teardown-and-relays-round1-captures.zip` on `rig-evidence-projection-teardown-and-relays` (fork `o-jcardenass/open-headunit`), 24701168 bytes, sha256 bfd4ece2131cfd7c75afde9e5d640c7796f08f7e2127f155ce64763778e06386. It holds every capture, preflight, returns/crash/race JSON, thermal log, the scripts and failed-attempts/; the APKs are left out (md5s in the header).
- Final state: settings restored on all three units from the backups with video-profile-starvation-cap absent (reads 0 on D-HU, D-HP and D-SAM); D-HU owner:group:mode u0_a176:u0_a176:660; restored files diff clean against the backups; D-HU Bluetooth and WiFi re-enabled; no logcat left (checked by pid).
- D-MOTO was present in `adb devices` and not used. The APKs are not in the evidence zip (md5s above); nothing else was excluded.
- The brief's R3 verdict wording asks for the kept-surface path to be tested on the candidate: every R3-C return had Texture_available 0 and New_surface_set 0.

## R0

**PASS**

- 2702 tests, 0 failures, 0 errors (JUnit XML); FeedLoopPolicyTest, ReturnRearmPolicyTest, SameEndpointConnectPolicyTest and AutomationCommandPolicyTest present. md5s differ. Identity greps: candidate 3/3/3, baseline 0/0/0.

## R3-B

**PASS**

- Role: baseline arm: the defect reproduced; no verdict on the branch

- thermal_max 69C, throttle delta 0. T per return (s): none, 27.3, none, 27.16, none, none (none = no picture in 30 s); 6 of 6 above 10 s; all on the kept-surface path, rearm_lines 0.

## R3-C

**PASS**

- thermal_max 61C, throttle delta 0. T per return: 1.96, 1.90, 1.95, 1.85, 1.92, 1.72 s. Each return: rearm_lines 1 at 0.30 to 0.31 s after resume, cycle_lines 1, fatal_signal 0, Texture_available 0 and New_surface_set 0 (kept-surface path). ohu_pair: no return fails rules a to d; FAIL_b_fires false.

## R3G-B

**PASS**

- Role: baseline arm; T 1.88, 1.73, 1.84, 1.69, 1.78 s

## R3G-C

**PASS**

- thermal_max 59C, delta 0. T: 1.76, 1.80, 1.87, 1.68, 1.67 s. rearm_lines 0 on every return; surface_cb_rel_resume_s 0.14, 0.24, 0.20, 0.10, 0.10 (GL callback within 0.3 s, so no re-arm expected). cycle_lines 1, fatal_signal 0. ohu_pair clean.

## R3S-B

**PASS**

- Role: baseline arm; T 0.41, 0.32, 0.40, 0.32, 0.34 s

## R3S-C

**PASS**

- thermal_max 57C, delta 0. T: 0.41, 0.33, 0.30, 0.32, 0.33 s. rearm_lines 0, double_arm false on all five, cycle_lines 0, fatal_signal 0. ohu_pair clean.

## R5-B

**PASS**

- Role: positive control: defect 3 reproduced

- thermal_max 60C, delta 0. Receiver line 1 in every window; AapService handler lines 0 for restart audio, refresh sensors and raise; "raise projection ignored" 0; top-after is the launcher on both raises; connected true.

## R5-C

**PASS**

- thermal_max 65C, delta 0. nosess: receiver 1, "raise projection ignored, no session" 1, "raising the projection by" 0, top-nosess names MainActivity. Each audio window: receiver 1, "Received request to restart audio" 1, "Restarting all audio tracks" 1. Each sensor window: "Received request to refresh all sensors" 1, NightMode update 1. Each raise window: "raising the projection by OVERLAY (overlay=true, foreground=false)" 1 (12:25:04.974 and 12:25:17.641), top-after names AapProjectionActivity. SSL handshake complete 0 from start to tail; QUERY_STATE connected true.

## R2-B

**INCONCLUSIVE**

- thermal_max 66C. 20 cycles, 1 segment, fatal 0, aborting 0, died 0, race_count 0. Home cycles with a detach stop: 0 of 10 (GLES surface survives Home on D-HU); screen-off cycles with activity_stop 10 of 10. The mechanism was not reached on the baseline.

## R2-C

**INCONCLUSIVE**

- thermal_max 73C. 20 cycles, fatal 0, aborting 0, died 0, race_count 0. Home cycles with a detach stop 0 of 10, below the 8 the brief needs, so the teardown the race invariant tests never happened. Regression guard only: no crash, no race window.

## R1-B

**PASS**

- Role: baseline arm: the mechanism was reached, and the baseline crashed

- thermal_max 71C, throttle delta 0. 14 cycles in 3 segments, stopped by the 3-crash rule. Crash lines: s1 `Fatal signal 11 (SIGSEGV) at 0xdeadbaad (code=1), thread 2212 (AapTransport:Ha)` with `@@@ ABORTING: LIBC: ARGUMENT IS INVALID HEAP ADDRESS IN dlfree addr=0x636dd018` (12:33:36.815, immediately after `Decoder stopped: surfaceDestroyed`); s2 `Fatal signal 11 (SIGSEGV) at 0x00070000 (code=1), thread 3067 (AapTransport:Ha)` (12:35:25.025); s3 `Fatal signal 11 (SIGSEGV) at 0xdeadbaad (code=1), thread 3686 (headunitrevived)` with `@@@ ABORTING: LIBC: HEAP MEMORY CORRUPTION IN dlfree` (12:36:47.185). fatal 3, aborting 2, died 3, race_count 3 (one window per segment, each holding Codec initialized: lines after a surfaceDestroyed stop: 12, 8 and 2). Detach stops: 7 of 7, 5 of 5 and 2 of 2 Home cycles; mean 1.86 per Home cycle. cycles_without_resume 0. No capture restarts (the .restarts files are empty).

## R1-C

**PASS**

- thermal_max 69C, throttle delta 0. 20 cycles, 1 segment. fatal 0, aborting 0, died 0, race_count 0. home_cycles_with_detach_stop 20 of 20 (needs 17). Mean detach_stops per Home cycle 1.0 (baseline 1.86). cycles_without_resume 0 (baseline 0). Codec exception in output thread 0, Decoder restart requested 0. The race invariant held over 20 real teardowns where the baseline crashed within 14.

## R5P-B

**PASS**

- Role: baseline arm: defect reproduced

- thermal_max 72C. ACTION_RAISE_PROJECTION receiver line 1 (12:38:26.615), "raising the projection by" 0, .top-after is com.android.launcher2.Launcher.

## R5P-C

**PASS**

- thermal_max 64C. `AapService: raising the projection by DIRECT (overlay=true, foreground=false)` 1 (12:44:03.425), `AapProjectionActivity: onResume` 1 (12:44:04.805), .top-after names AapProjectionActivity.

## R4-C

**FAIL**

- thermal_max 66C. Cycle 1 (T1) stopped the run with `ssl` 0, class `EARLY`: the USER connect landed at 12:44:28.225, 130 ms before discovery's `Auto-connecting to Headunit Server at 192.168.1.8:5277` (12:44:28.355), so the join path was never exercised and no `IN_WINDOW` cycle occurred (0 of the 3 needed). `join` 0, `preempt_socket` 0, `refused` 1 (the wireless-handshake socket refused while the USER claim was in flight), `peer_silent` 1. The first handshake took `ECONNRESET` at 12:44:31.495, the retry at 12:44:44.825 logged "the peer accepted the connection and then sent nothing at all", and no session formed in 60 s. The brief grades any cycle with `ssl` 0 as FAIL and says not to recover a wedge, so FAIL stands. What it does and does not show: the candidate did not preempt its own socket in this cycle, but the verb-first ordering the fix does not cover still wedged the phone's server. The fix itself (the IN_WINDOW join) is untested by this round.

## R4L-C

**UNTESTABLE**

- Setup failure: the phone's server stayed wedged after R4-C. The bring-up logged `SESSION_FAIL_SSL R4L-C`; the app's auto-connect (12:46:05.945), a USER connect (12:46:48.875) and further auto-connects each ended in "the peer accepted the connection and then sent nothing at all". `listening` still read true afterwards (`SERVER_UP_after`), so the port was open but the server was not answering. The brief forbids recovering a wedge or asking again.

## R6-B

**PASS**

- Role: baseline arm: no crash and no race window, so the mechanism was not reached on D-SAM

- thermal_max 70C, delta 0. Third attempt (13:02:30 to 13:04:55); 5 cycles, fatal 0, aborting 0, died 0, race_count 0. Detach stops: 0 of 5 Home cycles (the GLES surface survives Home on D-SAM). Picture T per return: 1.81, 1.83, 1.84, 1.76, 1.82 s. No MATCH or Magic Garbage lines. Preflight clean (NATIVE, GLES, sink 3, codec OMX.MARVELL.VIDEO.HW.CODA7542DECODER).

## R6-C

**PASS**

- Role: regression guard only

- thermal_max 73C, delta 0. Second attempt (13:01:40); 5 cycles, fatal 0, aborting 0, died 0, race_count 0. Detach stops 0 of 5. T per return: 1.64, 1.79, 1.87, 1.84, 1.80 s; rearm_lines 0 on every return; cycle_lines 1 each. ohu_pair: no return fails rules a, c or d; rule b (report only) does not fire. Because the baseline arm showed no crash and no race window, and no teardown happened on either build, this does not test the fix: it shows the candidate did not regress D-SAM's Home path.

## Anything the brief did not ask about

- **D-HU's Home press tears no surface down on GLES (R2) and D-SAM's none on GLES (R6).** R2 was therefore INCONCLUSIVE on both builds: the candidate's race invariant had no teardown to test on D-HU. R1 on D-HP (SURFACE) is the run that exercised it. If R2's H.265 leg matters, it needs a covering app or a SURFACE backend on D-HU rather than Home.
- **R4's wedge came from the verb-first ordering, not from the join path.** The candidate's own join (`is already connecting; not preempting it`) was never exercised: cycle 1 was EARLY (USER connect 130 ms before discovery), the first handshake took ECONNRESET, and the phone's server then accepted and sent nothing for the rest of the round (R4L-C could not run). A round that tests the fix needs a fresh server and verbs timed after the auto-connect; the T2 trigger (cycles 2 onward) never ran because cycle 1 stopped the run.
- **D-POCO's second bond.** A phone paired to both a head unit's OEM radio and the rig's tablet will not wake the tablet; this cost four R6 attempts. Worth a line in rig-quirks/units/D-SAM.md and D-POCO.md.
- **Host thermal.** The first baseline build ran unguarded to 98C (package throttle count 2430) before the thermal gate existed in my session; no run was affected: every graded run had package throttle delta 0 and peaked at 73C or below.
- **The brief's `apk_check` sets the thermal watcher name from RUN, which hu_open did not set before the call** (fixed in my copy; the brief's ptr_lib.sh at 79e819bd0 still has it).

