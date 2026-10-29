# projection-teardown-and-relays, round 2 results

**Candidate:** fix/projection-teardown-and-relays @ b7a3b2e59bf6b4676c3a13c63307358f6ec0f5bb       **Baseline:** main @ 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9
**APK md5:** 131c1e93cfdd7675f5ac6ceeefa85298 (candidate) / 50d3c116369f99784908b2380cdec421 (baseline)
**Unit:** D-HU (UNISOC MT50, Android 14) with D-POCO; D-HP (API 17) with D-POCO
**Date:** 2026-10-06

## Setup notes

- Quirk files read: rig-quirks/topics/tooling.md, topics/projection.md, topics/gearhead.md, units/D-HU.md, units/D-HP.md, units/D-SAM-and-D-HP.md, units/D-POCO.md.
- Pre-flight table (rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_HP:wifi): D_HU wifi 1 bt 1 Awake; D_POCO wifi 1 bt 1 Awake; D_HP wifi 1 bt 1 Awake; all HFP:none A2DP:none. It first printed D_HP MISSING because `rig_devices.sh` still named the old serial 0123456789ABCDEF; I changed that line to CNU350BGBJ (what `adb devices` prints) and it then passed. D-HP's serial this round is CNU350BGBJ.
- Pre-flight request: D-POCO bonds before: motorola edge 30 neo, Navegadortz3, Magnetic Speaker, FX Plus (no Navegadortz2). I force-stopped Android Auto on D-POCO and asked the operator for three steps (start the server, pair Navegadortz2, forget Navegadortz3). First read after the answer (poco-bonds-after.txt) was unchanged, with Navegadortz2 absent. The operator then reported the pairing had hit an error, fixed it by hand, and said D-HU and D-POCO connected. A second read (poco-bonds-after2.txt) shows Navegadortz2 present and Navegadortz3 still present (Navegadortz3 count 1; the brief says record it and continue). I had briefly counted R2 UNTESTABLE on the first read and withdrew that when the second read showed the bond. `SERVER_UP_PREFLIGHT` printed. `server_running` in preflight.phone.json is 1, quoted line: `1791313016.450 32299 32385 I GH.DHUService: Network server running on port 5277` (tag GH.DHUService).
- Gearhead lines on D-POCO, with tags: `GH.DHUService: Head unit connected` (R4-C cycle 1, epoch 1791315063.387); `CAR.SERVICE: Car connection state changed: PREFLIGHT->CONNECTED` (R2-B segment 1, epoch 1791314493.976); `GH.WIRELESS.SETUP:` lines at session start. `startDuplexConnection` never appeared (0), `Critical error` never appeared.
- R0 git checks: candidate tip b7a3b2e59bf6b4676c3a13c63307358f6ec0f5bb; tree 4be97cfbd6f4a135b9c912c259657faba94e0f87 (matches); merge-base 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9 (matches); 4 commits above the baseline. The brief's section 6 names `8d4656ab` as the tip, which is a stale name for the same tree. ACTION_QUERY_STATE after each candidate install returned commit b7a3b2e59bf6 (D-HU and D-HP).
- R0 build: first attempt failed in 13 s with `SDK location not found` (the new worktree had no local.properties); I copied it from the main checkout and re-ran. Built with `build_hur_cool.sh` (thermal_guarded), `GRADLE_OPTS=-Dorg.gradle.workers.max=2` in place of `--max-workers=2`. Host thermal during the build: the package sensor reached 96C and throttle_pkg went 2430 to 2456 (delta 26), despite the guard's 86C stop. The build and unit tests are not timing runs, so I kept them; the throttled build is why the md5 is recorded rather than assumed.
- Baseline APK reused from round 1, md5 50d3c116369f99784908b2380cdec421 (matches). Identity greps (`adopting the socket discovery already opened`, HeldServerSocket, SameEndpointConnectPolicy, FeedLoopPolicy): cand.apk 1/16/5/3, base.apk 0/0/0/0. Classifier self-test: `IN_WINDOW 390 1 0 NONE`, then `R3S-B 3`, `R3S-C 0`, all as expected.
- Scripts: ohu_lib.sh and ohu_setkeys.py copied from round 1; ptr_lib.sh, ptr_crash.py, ptr_race.py, ptr_phone.py extracted verbatim from the brief by script. Added in `hur-wifi-test-scripts/projection-teardown-and-relays-round2/`: r0_build.sh, stage1.sh, stage1b.sh, stage2.sh, stage2b.sh and four launch*.sh wrappers (session scripts run in the background by the host; none driven by rig-executor). Existing scripts used: rig_preflight.sh, rig_thermal.sh, build_hur_cool.sh, thermal_guarded.sh, run_unit_tests.sh.
- Script bugs of mine: the first launch of stage1.sh refused with `stray logcat` because my own grep matched the launching shell's command line; I changed the check to `pgrep -f '^adb .*logcat'` and relaunched (failed-attempts/stage1-refused.log). The R2-B re-run script first refused on `listening` (D-POCO's server had dropped after R2-C); R2 does not use the server, so I removed that check for the re-run (failed-attempts/stage1b-refused.log).
- A leak found: when `hu_open` fails at `session_up` it never stops the D-HU capture, so the failed R2-B attempt's logcat (pid 142836) kept appending to its moved file during R2-C. I killed it by pid after R2-C. R2-C's own capture is its own file and was not affected. Likewise `hp_open` leaked its restart loop after R4L-C failed (killed by pid, 157745 and 159053). `ps` shows no logcat left.
- Settings: D-HU settings.xml equals round 1's backup (empty diff). Audio keys as found on D-HU: use-aac-audio=false, audio-latency-multiplier=8, audio-queue-capacity=20, enable-audio-sink=false; connection-modes `wifi,self`. shared_prefs owner:mode u0_a176:771, settings.xml owner u0_a176:u0_a176. D-HP settings.xml equals round 1's backup (empty diff, read after D-HP's restart); D-HP as found: video-codec H.264, resolutionId 1, view-mode 0, wifi-connection-mode 1, log-level 0 (written 2), use-aac-audio true, audio-latency-multiplier 16, audio-queue-capacity 50, enable-audio-sink true. Clocks (clocks.log): host, phone and unit agree within 1 s at every edge.
- Rig events: D-POCO's head unit server dropped (`:149D` not listening) after R2-C and was listening again by the stage change without anyone starting it. D-HP fell off USB at the stage change (`offline`, then gone from lsusb); the operator found it bricked for an unknown reason and restarted it, after which it came back on the LAN at 192.168.1.22 (D-POCO 192.168.1.8). I used `pkill -f` once by mistake in the host shell (exit 144) and then killed by pid.
- R4L-C was run twice at the operator's request after a server restart (attempt 1 against the server left deaf by R4-C, attempt 2 after the operator restarted it); both are INCONCLUSIVE for the same crash. The brief says not to recover the server after R4-C, so attempt 2 is an extra the operator asked for, not a brief step.
- D-MOTO (`motorola edge 30 neo`) is bonded on D-POCO and was poked by D-HU's rotation in R2-B attempt 1 (3 pokes); see R2-B.
- Stage 1 order deviation: R2-C ran before the R2-B re-run (R2-B attempt 1 formed no session). Both builds were installed by md5-checked `adb install -r -d` and verified by `apk_check` (apk-check.log).
- Evidence: release asset `projection-teardown-and-relays-round2-captures.zip` on `rig-evidence-projection-teardown-and-relays` (fork `o-jcardenass/open-headunit`), 16957614 bytes, sha256 `473e53fd40505e66ea05cc3a6ac2d763e6fe5d1c6af8de8b0237e46d47154040`. It holds every capture, phone capture, preflight, JSON, thermal log, the scripts and `failed-attempts/`; the APKs are left out (md5s in the header).
- Final state: both units' settings restored from the backups with video-profile-starvation-cap removed (reads 0 on D-HU and D-HP); restored files diff clean against the backups; D-HU owner:group:mode u0_a176:u0_a176:660 (the recorded owner); D-HU Bluetooth and WiFi re-enabled; no logcat left (checked by pid).

## R0

**PASS**

- Settings written: none.
- Thermal: R0.thermal pkg max 96C, throttle_pkg 2430 to 2456 (build, not a timing run; see Setup notes).
- 2713 tests, 0 failures, 0 errors, 0 skipped (JUnit XML); `HeldServerSocketTest` and `SameEndpointConnectPolicyTest` present. md5s differ. All four identity strings 1 or more in cand.apk and 0 in base.apk. Self-test printed the three expected lines.

## R2-B

**PASS**

- Role: baseline arm; no verdict on the branch. The mechanism was reached: `early_init_cycles` 11 of 20.
- Settings written: R2KEYS (view-mode 0, video-codec H.265, resolutionId 3, wifi-connection-mode 3, log-level 2, night-mode 1, native-driver-selection-mode 0, onboarding-version 2, starvation cap deleted). Preflight: mode=NATIVE, viewMode=SURFACE, sink=7, codec=c2.unisoc.hevc.decoder, hotspot_lines=0, groupFormed true, connection-modes wifi,self, starvation-cap-keys 0.
- Radio state: D-POCO radios cycled by `phone_air_on/off` (svc); D-HU Bluetooth and WiFi on.
- Discard-rule check: attempt 1 was a setup failure (`SESSION_FAIL_SSL`: D-HU poked POCO twice then motorola edge 30 neo three times in 150 s and no connection-back; files in failed-attempts/). Re-run once: clean (SSL handshake complete 1, `MATCH!`/`createGroup SUCCESS`/`Magic Garbage` 0 or one legitimate group, no second SSL).
- Thermal: R2-B-s1 thermal_max=63C throttle_delta=0.
- Measurements: cycles 20; crash segments 0; fatal 0, aborting 0, died 0; `race_count` 1 (window opened line 131131, closed at the run end, 31 codec inits inside; overstated on SURFACE as the brief says); `early_init_cycles` 11 (cycles 1H, 2S, 8S, 9H, 11H, 13H, 14S, 15H, 16S, 18S, 19H; `early_init_lines` 131336, 140174, 201764, 214042, 234692, 255643, 264482, 276699, 285536, 306304, 318578); `home_cycles_with_detach_stop` 10; `off_cycles_with_activity_stop` 10; `detach_stops_total` 40, so mean 2.0 per Home cycle; `Codec exception in output thread` 0; `Decoder restart requested: ` 0; `skipped: surface is no longer current` 0; phone `critical_error` 0 and `out_of_car` 0 in all 20 windows.

The baseline built a codec on the dying surface in 11 of 20 cycles and never crashed in this run, so the crash itself was not reproduced.

## R2-C

**PASS**

- Settings written: same R2KEYS. Preflight identical to R2-B (codec c2.unisoc.hevc.decoder).
- Radio state: as R2-B.
- Discard-rule check: clean (one session, SSL handshake complete 1, no `MATCH!`, no second `createGroup SUCCESS`, no `Magic Garbage`).
- Thermal: R2-C-s1 thermal_max=68C throttle_delta=0.
- Measurements: cycles 20; crash segments 0; fatal 0, aborting 0, died 0; `race_count` 0; `early_init_cycles` 0; `home_cycles_with_detach_stop` 10; `off_cycles_with_activity_stop` 10; `detach_stops_total` 20, mean 1.0 per Home cycle; `Codec exception in output thread` 0; `Decoder restart requested: ` 0; `skipped: surface is no longer current` 0; `cycles_without_resume` 0; phone `critical_error` 0 and `out_of_car` 0 in all 20 windows.

Candidate halves the dying-surface stops (1.0 vs 2.0 per Home cycle) and builds no codec on a dying surface.

## R4-C

**FAIL**

- Settings written: HPKEYS (wifi-connection-mode 1, log-level 2, view-mode 0, onboarding-version 2, connection-modes wifi, starvation cap deleted). The brief's preflight did not run: cycle 1 formed no session, so the script stopped by the stop rule before it.
- Radio state: D-HP and D-POCO on the house LAN, D-HU Bluetooth and WiFi disabled. `SERVER_UP_STAGE2` printed.
- Discard-rule check: n/a (one cycle).
- Thermal: thermal_max=61C throttle_delta=0.
- Per-cycle row (cycle 1, T1): class EARLY, same_endpoint true, verb_after_found_ms -3890, route NONE, adopt 0, join 0, not_held 0, refused 0, refused_bringup 0, preempt_socket 0, stood_down 1, peer_silent 0, ssl 0, ssl_after_go_s none, picture_after_go_s none. Phone: hu_connected 1, duplex 0, hu_disconnected 0, server_running 0, last_line `hu_connected`. IN_WINDOW cycles: 0 (adopt 0, join 0). `R4-C.listening-after` reads UP.
- Decisive lines (D-HP clock): `14:31:00.242 AutomationMarker: R4-C-c1-T1-go`; `14:31:03.572 AutomationReceiver: ...ACTION_CONNECT` (verb, 3.3 s after the launch); `14:31:04.122 ConnectionArbiter: 192.168.1.8:5277 (USER) stood the wireless stack down until it ends`; `14:31:05.052 Handshake: Version response received: the phone selected 1.7`; `14:31:05.352 SSL Handshake: Failed to read AAP header`; `14:31:07.462 NetworkDiscovery: Found Headunit Server on 192.168.1.8:5277` (3.89 s after the verb); `14:31:07.472 Auto-connecting to Headunit Server at 192.168.1.8:5277 (reusing socket)`; then `14:31:08.232 E/AndroidRuntime FATAL EXCEPTION: main` with `java.lang.NoClassDefFoundError: com.andrerinas.openheadunit.connection.HeldServerSocket$$ExternalSyntheticLambda0 at HeldServerSocket.settle(HeldServerSocket.kt:34) at CommManager$connect$4.invokeSuspend(CommManager.kt:360)`; `14:31:13.552 ActivityManager: Process com.andrerinas.headunitrevived (pid 1724) has died.` The app restarted as pid 1866 and the stop rule ended the run.
- Two findings, both in the candidate:
  1. **The candidate process crashes on API 17 whenever discovery hands its socket over.** `HeldServerSocket.settle` and `discard` call `removeIf` with a lambda; `Collection.removeIf` and `java.util.function.Predicate` are API 24, D-HP is API 17 and the github flavor builds at minSdk 16. The lambda class is in classes2.dex (strings count 2) and fails to load. This is consistent with the trace; I did not run the code on another API level to prove the cause.
  2. **The cycle was EARLY, not IN_WINDOW.** The verb landed 3.89 s before the Found line on D-HP (round 1 landed 390 ms after it), so the USER dial was the only connection and the fix path was not reached. The phone server accepted it (`Head unit connected` 1), answered the version request and dropped the socket during SSL. Nothing here shows whether that wedge is the old mechanism, a consequence of the freshly restarted server, or a consequence of the crash that followed 3 s later.

The brief's FAIL rule on `ssl` 0 applies to any class. The adopt and join paths were never exercised on D-HP in this round, so the branch's intended R4 behaviour is neither confirmed nor refuted.

## R4L-C

**INCONCLUSIVE**

- Settings written: HPKEYS as R4-C.
- Radio state: as R4-C. This is the second attempt: the first (failed-attempts/attempt1-*) ran against a deaf server and is described below. The operator restarted D-POCO's head unit server by hand and asked for the re-run; `listening` read 1 before it started.
- Discard-rule check: n/a.
- Thermal: none printed (the run never reached `run_end`); `R4L-C.thermal` is in the evidence.
- Attempt 2 (server freshly restarted): no session formed (`SESSION_FAIL_SSL R4L-C`). The app died at the first discovery hand-off, three times: `14:39:44.492 NetworkDiscovery: Found Headunit Server on 192.168.1.8:5277`, `14:39:44.822 Auto-connecting ... (reusing socket)`, `14:39:45.302 FATAL EXCEPTION: main` (`NoClassDefFoundError: ...HeldServerSocket$$ExternalSyntheticLambda0`, pid 2420, `14:39:54.202 Process ... (pid 2420) has died.`); again at 14:40:02.332 (pid 2524, died 14:40:05.592); and at 14:40:42.312 (pid 2630) after the fallback verb `14:40:29.512 ACTION_CONNECT` got `14:40:40.482 Handshake: the peer accepted the connection and then sent nothing at all`. Phone: `GH.DHUService: Head unit connected` 1 (epoch 1791315583.407, the sweep's socket). Window counts: `is already connecting; not preempting it` 0, adopting 0, `session state connecting` 0. `SERVER_UP_after_R4L` printed. The `go-mid` window was never reached, so `hu_connected` there is not measured.
- Attempt 1 (deaf server, after R4-C): same outcome. Sweep 0 of 253 responding at 14:34:29, fallback verb at 14:35:11.682 got `the peer accepted the connection and then sent nothing at all`, crashes at 14:35:25.252 and 14:36:05.512.

R4L-C cannot grade the live-session redial because the candidate process dies before any session forms, on both attempts. The operator's message during attempt 2, "OHU died after trying to connect", is the same crash.

## Anything the brief did not ask about

- The crash in R4-C and both R4L-C attempts is the main result of the round. It would hit every D-HP-class unit (API below 24) the first time Headunit Server mode finds the server. The same `removeIf` appears in `discard` (HeldServerSocket.kt line 43). The unit tests cannot see it because they run on a JVM.
- R2-B reached the early-codec-init mechanism 11 of 20 cycles without a native crash, so the baseline's round 1 D-HP crash is still only reproduced there, not on D-HU.
- D-POCO's head unit server dropped by itself between R2-C and the stage change and came back by itself before stage 2. I did not capture why.
- The stage 1 poke rotation took 150 s and 5 pokes (2 to D-POCO, 3 to the bonded Motorola) to reach D-POCO in R2-B attempt 1, and 110 s in R2-C (14:13:41 to 14:15:31). With Navegadortz3 still bonded the brief's "must be 0" condition was not met.
- D-HP bricked at the stage change for a reason not captured; the operator restarted it.
