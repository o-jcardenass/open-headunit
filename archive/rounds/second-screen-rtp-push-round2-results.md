# second-screen-rtp-push, round 2 results

**Candidate:** `feat/second-screen-outputs` @ `8fc611a4901c09247b52c54c4c8e87ee862a71aa` (tree `310fca3c60ee287c1b152bc7db4c44773928aa7c`, unchanged since round 1)    **Baseline:** none (O1 and O2 are the feature-off controls)
**APK md5:** `2b24c2b3f2075713824a1511da06ad4c` (installed copy and the round 1 build agree)
**Unit:** D-HU (UNISOC MT50, Android 14, rooted shell), D-POCO (POCO X3 NFC, Gearhead 18.0.164204-release.daily), tester PC as RTP receiver (FFmpeg 7.1.5, Python 3.14.6)
**Date:** 2026-10-14

Round verdicts: R0 PASS, O1 PASS, R4 INCONCLUSIVE (see the ladder note), R5 PASS, O2 PASS, C-FPS PASS. N1 not run (it runs only after FAIL-COST).

## Setup notes

- **Quirk files read:** `rig-quirks/topics/tooling.md`, `topics/gearhead.md`, `topics/projection.md`, `units/D-HU.md`, `units/D-POCO.md`. Template read without 7b and 8.
- **Pre-flight table** (`rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`, 21:27): D_HU wifi 1, bt 1, Awake, HFP none, A2DP none. D_POCO wifi 1, bt 1, Awake, HFP up, A2DP up. `PREFLIGHT OK`. Host 62C, `throttle_pkg=0`.
- **P1.** On adb: D-HU, D-SAM, D-POCO, D-HP, D-MOTO (`0123456789ABCDEF` absent). D-MOTO: Bluetooth and WiFi disabled. The head unit app was force-stopped on D-SAM and D-HP. D-MOTO's radios were left off at the end of the round.
- **P2.** `shared_prefs` owner `u0_a176:u0_a176`. Backup md5 `8d77a54bc54478ab9d19031230eb9f00`. Audio keys read back, not changed: `audio-latency-multiplier` 8, `audio-queue-capacity` 20, `use-aac-audio` false, `enable-audio-sink` false. `keep-dummy-vpn-during-session` false. No `aux-*` or `preferred-display-*` keys in the backup. `HU_MAC=11:46:03:10:33:59`, `HUIP_P=192.168.1.9/24`. `iptables -S OUTPUT` held no rule naming port 5004.
- **P3.** `POCO_MAC=DC:B7:2E:5E:4E:59`. D-POCO focus was the Play Store at read time; `KEYCODE_HOME` was sent.
- **P5 and P6.** `HOSTIP=192.168.1.11`, `SAME_SUBNET=yes`, ping 3/3. The brief said round 1's PC firewall rule was still in place. It was not: the first probe read `PROBE datagrams=0 from=none` after `PROBE_SENT`, so `UDP_PROBE=blocked`. The operator then ran `sudo ufw allow proto udp from 192.168.1.0/24 to any port 5004:5005`. Re-probe: `PROBE datagrams=5 from=192.168.1.9`, `UDP_PROBE=ok`. This rule still needs removing by the operator (Cleanup).
- **P8.** `ADB_EXIT=1`. `ICMP_DU before=3 after=8 delta=5`, so `ICMP_PROBE=ok`. `ipt_on` printed `IPT_ON`, `ipt_off` printed `IPT_OFF`, so `IPT=ok`.
- **R0, no build.** D-HU carried another build (md5 `9e7ba902713336cee1850167795cf314`, `commit":"ec6cb51655fe-dirty"`, DEX count of `RtpStreamOutput` 0). The candidate APK from round 1 was still on disk twice (`rig-data/apks/second-screen-rtp-push-round1/cand.apk` and the worktree's `app/build/outputs`), both md5 `2b24c2b3...`, and the worktree `second-screen-rtp-push__candidate__8fc611a4__20261013` was at `8fc611a4`, tree `310fca3c`. I installed that file with `adb install -r` and did not build. After install: pulled live APK md5 `2b24c2b3f2075713824a1511da06ad4c`, `data=` commit `8fc611a4901c-dirty`, DEX count 9. Unit tests were not run (not asked for).
- **Scripts** (`rig-data/rounds/second-screen-rtp-push-round2/scripts/`). Round 1 files, md5 prefixes all equal round 1's: `ssr_lib.sh 37fa464e`, `ssr_keys.py 3e9ffa2e`, `tput.py 4df665f4`, `nal_count.py e199ca79`, `pngdims.py d11bf834`, `rtp_recv.py 7b6df231`, `rtp_lib.sh 9c1530fc`, `ssr_build.sh acdd37f3` (not used). New from the brief: `udp_probe.sh a9ebfbf3`, `fps30.py c31e93ef`, `idr_gaps.py 3d11debb`, `rtp2_lib.sh 95a552cc`. Round runners written here: `p_pre.sh`, `p_p8.sh`, `run_off.sh` (O1, O2), `run_r4.sh`, `run_r5.sh`, `cleanup.sh`, `env.sh`. `fps30.py` and `idr_gaps.py` ran on real captures and gave numbers in every field. Evidence directory is `scripts/out/` (the lib sets `OUT=$D/out`), not `evidence/<thread>-round<N>`.
- **Runner shape.** Each run was one script started with `nohup`, its log ending in `script exit=`, watched by one monitor at a time. A launcher wrapper reported exit 1 once at O1 because its own trailing `wc` ran before the capture file existed; the run was alive and finished with `script exit=0`. The cleanup script's first run exited 144 because it contained a `pkill -f` that matched the host shell (my slip); I removed the line and re-ran, no state was lost.
- **Not covered, again:** the settings rows for the address and the port, the toast for a refused address, and the write-only-when-changed rule on those rows. Entry needs typing, which the rig does not do. Known gap, not a finding.
- **Host thermal:** per-run max O1 59C, R4 59C, R5 65C, O2 58C; `throttle_delta=0` in all four. No run voided.
- **Marker note.** The candidate does not list `ACTION_LOG_MARKER` as configuring, so `allow-external-configuration` was not written. All markers landed (`phone_markers_in_window` 5, 11, 5, 5 in the up..end windows).

## R0. Identity

**PASS**

- Installed APK md5 `2b24c2b3f2075713824a1511da06ad4c`, `data=` reply `"commit":"8fc611a4901c-dirty"`, `RtpStreamOutput` in DEX 9 times. `git ls-remote fork feat/second-screen-outputs` printed `8fc611a4901c09247b52c54c4c8e87ee862a71aa`.

## O1. Feature off, the first fps control

**PASS**

- Keys: `OKEYS` (`aux-display-enabled` deleted, RTP_PUSH stored), `KEYS_OK`.
- `e_secondscreen=0 e_vidaux=0 e_announce=0`, `RTP_RECV packets=0`, `e_ssl=1 e_destroy=0`.
- `FPS30 from=O1-up windows=30 rendered_ge1=30 median_fps=29 median_fed_fps=29 dropped_sum=0`. My own count of `Throughput over` lines in O1-up..O1-end: 32.
- Phone `O1-up..O1-end`: `win|Critical error=0`, `phone_markers_in_window=3`. `critical_error_file=4` (after `O1-end`, not counted).

## R4. Early receiver, closed port, refused sends

**INCONCLUSIVE**

Settings: `PKEYS` (`aux-display-enabled` true, `aux-output` RTP_PUSH, destination `192.168.1.11`, port 5004, size 1), `KEYS_OK`. `R4_BRINGUP rc=0`. `HUIP_SESSION=192.168.1.9/24`. Preflight: `mode_native=1 view_texture=1 main_sink_h264=1 hotspot_lines=0`. Markers (HU log): launch 21:40:05.304, up 21:40:33.550, p1 21:40:36.904, p1-end 21:43:37.627, p2 21:43:38.864, p2-end 21:44:19.592, p3 21:44:22.368, p3-end 21:44:53.085, p4 21:44:56.109, p4-end 21:45:26.861, end 21:45:28.491, closed 21:45:33.611.

**D1, the SDP opens as written: INCONCLUSIVE.**
- `FFMPEG_EARLY rc=234 run_s=10.2 exit_after_up_s=-32.5`. No PNG written (`PNG count` line absent).
- FFmpeg started at 21:39:53 and ended at 21:40:03, 2 s before the `R4-launch` marker and 24 s before the push socket opened (21:40:27.363). No datagram existed while FFmpeg listened. `P0` (`R4-launch..R4-p1`) `e_keyframes=0`, so the brief's rule gives INCONCLUSIVE, not FAIL.
- FFmpeg's lines: `[sdp @ ...] Could not find codec parameters for stream 0 (Video: h264, none): unspecified size`, `Stream #0:0: Video: h264, none, 90k tbr, 90k tbn`, `[out#0/image2 @ ...] Output file does not contain any stream`, `Error opening output files: Invalid argument`. The 360 s timeout passed to `ff_start` was not what ended it.

**D2, the socket and the stream: met.**
- `ALL`: `e_announce=1`, line `21:40:27.170 ... Announcing an auxiliary display on VIDEO_AUX: RTP_PUSH 1280x720 as _1280x720, margins 0x0, density 213, role=AUXILIARY, content 65538, input on INPUT_AUX`. `e_open=1 e_outopen=1 e_nostart=0 e_unavail=0`. `21:40:27.363 RtpStreamOutput.start | SecondScreen: RTP push to 192.168.1.11:5004 open (network=wlan0)`.
- `R4.rtp`: `packets=6391 seq_lost=0 loss_pct=0.000 srcs=192.168.1.9:6391 marker_bad=0 fu_bad=0 idr_groups=3 idr_intact_with_ps=3 idr_intact_without_ps=0`.

**D3, IDR cadence (report only).** `IDR_TIMES n=3 span_s=183.1 times=54.8,114.8,174.8 gaps=60.0,60.0 max_gap_s=60.0 tail_s=8.3`. `KF_REPORTS 21:40:37.387 21:41:37.602 21:42:37.813 21:43:38.020 21:44:38.235` (the first report holds the opening IDR, sent before the counter started at 21:40:36.9, so the counter's first IDR came at 54.8 s).

**D4, the closed port: PASS.** `P2`: `e_fail_lines=0 e_sent_lines=4 e_packets=1205`. `ICMP_DU p1_delta=0 p2_delta=47`.

**D5, a refused send: INCONCLUSIVE by the brief's letter.**
- `R4.ipt`: `IPT_ON`. `P3`: `e_fail_lines=0`, `e_sent_lines=3 e_packets=903 e_errors=0 e_shed=0`. `R4b.rtp`: `packets=0`.
- The one failure line is inside `ALL` but 1.7 s before the `R4-p3` marker (`ipt_on` ran between `p2-end` and `p3`): `21:44:20.638 RtpStreamOutput.noteFailure | SecondScreen: RTP push send failed (IOException): sendto failed: EPERM (Operation not permitted); later failures of this kind are not logged`. `ALL` shows `e_fail_lines=1` and that class once (not FAIL). In substance the candidate logged the failure once, kept sending counts (903 in P3), kept the session, and resumed in P4. The class is `IOException`, not `SocketException` by name.

**D6, sends resume: PASS.** `IPT_OFF` follows the phase 3 line. `R4c.rtp`: `packets=971 seq_lost=0 srcs=192.168.1.9:971 marker_bad=0 fu_bad=0`, `idr_groups=0` (normal for a mid-GOP join). `P4`: `e_sent_lines=3 e_fail_lines=0`.

**D7, no cost to the session: PASS.** `ALL`: `e_ssl=1 e_destroy=0 e_errors=0 e_auxdec=0 e_kf_req=0`. `TPUT main windows=58 windows_rendered_ge1=58 median_fps=29 dropped_sum=0`. Phone `R4-up..R4-end`: `Critical error=0`, `No input for display=0`, `Video focus gained before configurations received=0`. Bring-up window (quote only): `21:40:28.918 CAR.INPUT: Discovered input for display 0 with service 3 ...` and `21:40:28.919 ... display 1 with service 15 ...`.

**D8, the close: PASS.** `CLOSE`: `e_stopped=1`, `e_destroy=1`.

**Ladder note.** D2, D4, D6, D7, D8 are met, D5 is INCONCLUSIVE, and D1 is INCONCLUSIVE. The brief's outcome ladder has no line for an INCONCLUSIVE D1: PASS needs D1 met, and "FAIL: anything else" would read FAIL. I graded INCONCLUSIVE because D1 could not be produced, not failed. Please decide at approval whether the thread wants it read strictly.

Other numbers: `FPS30 from=R4-up windows=30 rendered_ge1=30 median_fps=29 median_fed_fps=29 dropped_sum=0`. Phone bring-up window `critical_error_file=5` (all after `R4-end`).

## R5. A second push session

**PASS**

- `e_open=1 e_network=wlan0 e_fail_lines=0`, `e_announce=1 e_unavail=0`. `HUIP_SESSION=192.168.1.9/24`.
- `R5.rtp`: `packets=7274 seq_lost=0 loss_pct=0.000 marker_bad=0 fu_bad=0 idr_groups=3 idr_intact_with_ps=3 idr_intact_without_ps=0`, first datagram 42.2 s after the counter started.
- `e_ssl=1 e_destroy=0 e_errors=0 e_kf_req=0`. `FPS30 windows=30 rendered_ge1=30 median_fps=29 median_fed_fps=29 dropped_sum=0`. Phone `R5-up..R5-end`: `Critical error=0`, `No input for display=0`. `R5-end..R5-closed`: `e_stopped=1`.
- D3: `IDR_TIMES n=3 span_s=169.7 times=0.0,62.4,122.4 gaps=62.4,60.0 max_gap_s=62.4 tail_s=47.3` (the counter was up before the launch, so it caught the opening IDR at 0.0). `KF_REPORTS 21:47:30.053 21:48:30.264 21:49:30.486`.

## O2. Feature off, the second fps control

**PASS**

- `e_secondscreen=0 e_vidaux=0 e_announce=0`, `RTP_RECV packets=0`, `e_ssl=1 e_destroy=0`. `FPS30 windows=30 rendered_ge1=30 median_fps=29 median_fed_fps=29 dropped_sum=0`. Phone `Critical error=0` in `O2-up..O2-end` (`critical_error_file=5` after the end).

## C-FPS. The cost to the main picture

**PASS**

- `F_off` = mean(29, 29) = 29; `F_push` = mean(29, 29) = 29 (R4 and R5 each `rendered_ge1=30`). Fed fps: `F_off` 29, `F_push` 29. `F_push` is not lower than `F_off - 3`. N1 was not run.

## Anything the brief did not ask about

- **FFmpeg gives up on an SDP with no traffic after about 10 s.** In R4 it ended at 21:40:03 with "unspecified size" while the push socket opened at 21:40:27. So "start FFmpeg before the session" cannot answer the brief's first question. Starting FFmpeg only when the push is about to open, or giving it options that wait longer, would be a new brief. Nothing was tried here.
- **A late receiver waits up to about one GOP.** IDRs came every 60.0 s (62.4 once at the start) in both sessions; a receiver that joined just after the opening IDR in R4 saw its first IDR 54.8 s later, and R5's tail was 47.3 s with no IDR yet.
- **The failed send is visible only once.** The single `EPERM` line came about 1 s after the rule went in, as the design says (logged once per class). Sends continued to be counted (903 in P3) and resumed to the PC in P4 (971 packets).
- **ICMP port unreachable reached D-HU in volume while nothing listened:** 47 in 40 s, with no failure line, as the design says.
- **Round 1's firewall rule on the PC had gone before this round.** The operator re-added one for 5004:5005 from `192.168.1.0/24`. It still needs removing.
- D-MOTO's Bluetooth and WiFi were switched off by P1 and not restored.

## Evidence

Release `rig-evidence-second-screen-rtp-push`, asset `second-screen-rtp-push-round2-captures.zip` (67272722 bytes), sha256 `37cf628dba55fc2861732f0cb30c86e401e36a8e25d1849fe980b2e3ced8f13c`. It holds the scripts folder with every capture, grade file and window file.
