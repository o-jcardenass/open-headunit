# second-screen-rtp-push, round 1 results

**Candidate:** `feat/second-screen-outputs` @ `8fc611a4901c09247b52c54c4c8e87ee862a71aa` (tree `310fca3c60ee287c1b152bc7db4c44773928aa7c`, per the round 1 addendum)   **Baseline:** none (R1 is the feature-off control)
**APK md5:** `2b24c2b3f2075713824a1511da06ad4c` (candidate, built and installed copy agree)
**Unit:** D-HU (UNISOC MT50, Android 14, rooted) as head unit; D-POCO (POCO X3, Gearhead 18.0.164204-release.daily) as phone; the tester PC (192.168.1.11) as the RTP receiver. House WiFi `Pegue Cdesta`, D-HU station 192.168.1.9/24 on wlan0.
**Date:** 2026-10-13
**Evidence:** release `rig-evidence-second-screen-rtp-push`, asset `second-screen-rtp-push-round1-captures.zip`, sha256 `867075067efd5bd62b9a2190193328713d77a26200f193a0564bd3eadad2de7a`

## Setup notes

- **Brief and addendum.** Used the addendum's branch, SHA, tree, base and worktree. The worktree was made with `wt-new.sh` (`second-screen-rtp-push__candidate__8fc611a4__20261013`). Tree, `BASE_OK` and merge base `73b52b64` all matched.
- **Script folder.** `hur-wifi-test-scripts` no longer exists; used `rig-toolkit/` and `rig-data/rounds/second-screen-rtp-push-round1/scripts/`. Quirk files read: `rig-quirks/topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`.
- **Scripts and md5.** Copied from `second-screen-outputs-round2/scripts` (`grep -c 'phone_grades()'` = 1): `ssr_lib.sh` 37fa464e, `ssr_keys.py` 3e9ffa2e, `tput.py` 4df665f4, `nal_count.py` e199ca79, `pngdims.py` d11bf834, `ssr_build.sh` acdd37f3. New from the brief (extracted with `extract_brief_blocks.py`): `rtp_recv.py` 7b6df231, `udp_probe.sh` 30872225, `rtp_lib.sh` 9c1530fc. Added by this round: `udp_probe2.sh`, `env.sh`, `r1.sh`, `r2.sh`, `r3.sh` (R3A then R3C).
- **Pre-flight table.** `rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`: D_HU adb ok, wifi 1, bt 1, awake; D_POCO adb ok, wifi 1, bt 1, awake; HFP/A2DP none on both. It printed FAIL only for one idle Gradle daemon (the build reused it). All five rig units were on adb; P1 switched D-MOTO's Bluetooth and WiFi off and force-stopped the app on D-SAM and D-HP.
- **D-HU state (P2).** `shared_prefs` owner `u0_a176:u0_a176` (correct). Audio keys read back, not changed: `audio-latency-multiplier` 8, `audio-queue-capacity` 20, `enable-audio-sink` false, `use-aac-audio` false. `keep-dummy-vpn-during-session` false; no `video-profile-starvation-cap`. No `aux-*` or `preferred-display-*` keys were stored. `settings.xml` md5 `8d77a54b`; `adb install -r` left it byte-identical.
- **P5 and `udp_probe.sh`.** The brief's `udp_probe.sh` hung on D-HU (toybox `nc -u` never returned; no `PROBE_SENT`). First read: 0 datagrams, indeterminate. Re-ran with `udp_probe2.sh` (same, `timeout 3` on each `nc`): `PROBE_SENT`, no error, still 0 datagrams, so `UDP_PROBE=blocked`. The operator then ran `sudo apt-get install -y ffmpeg` and opened the port; I did not see the exact firewall command (the PC's firewall is unreadable without root). Re-probe: `PROBE datagrams=5 from=192.168.1.9`, so `UDP_PROBE=ok`. ffmpeg 7.1.5 present. **The operator's firewall rule still needs removing (Cleanup); I cannot do it.** `SAME_SUBNET=yes`, `HOSTIP=192.168.1.11`, ping 3/3 (0% loss), `UNUSED=192.168.1.253`.
- **`recv_stop R1 | tee` bug.** In the brief, `recv_stop ... | tee` runs in a subshell, so it cannot `wait` for the receiver and `R1.rtp` came out empty. The receiver's own `R1-rtp.txt` holds the line. R2 used `recv_stop R2 > R2.rtp`.
- **R0 and tests.** Built with `ssr_build.sh`, `--max-workers=2`, host 60C before and 61C max during runs, `throttle_delta=0` in every run. Unit tests not re-run, per the brief. `data=` reply: `"commit":"8fc611a4901c-dirty"` (the `.WORKTREE-INFO` file).
- **Not covered by this round (known gap, not a finding):** the settings rows for address and port, the refused-address toast, and the write-only-when-changed rule on those rows. Settings went in `settings.xml`.
- **Windows (see R2).** Two brief conditions are counted in windows that include things outside the push; the by-the-letter result and the cause are both given under R2.
- No taps, no hand steps. Every `RTP_PUSH` run used Native AA wireless.

## R0. Build gate

**PASS**

- Build succeeded in 1m 30s. `md5sum` of the pulled live APK and the built APK: both `2b24c2b3f2075713824a1511da06ad4c`.
- `ACTION_QUERY_STATE` reply: `"commit":"8fc611a4901c-dirty","versionName":"3.5.0-beta4","versionCode":117`.
- `strings` on the live DEX: `RtpStreamOutput` count 9.

## R1. Feature off, a full RTP setup stored

**PASS**

- Settings: BASE + `aux-display-enabled` deleted, `aux-output` RTP_PUSH, `aux-rtp-destination` 192.168.1.11, port 5004, size 1. `KEYS_OK`. Bring-up rc 0 on the first attempt.
- `R1.counts`: `e_secondscreen` 0, `e_vidaux` 0, `e_announce` 0, `e_open` 0. `R1-rtp.txt`: `RTP_RECV packets=0`. `e_ssl` 1, `e_destroy` 0.
- `R1.tput`: main `windows=9 windows_rendered_ge1=9 median_fps=29 dropped_sum=0`.
- Phone: `win|Critical error=0`, `phone_markers_in_window=7`.

## R2. RTP push to the PC (the point of the round)

**PASS** (host's reading; see the open question below, which needs the operator's decision before this file is final)

Counts are in the windows the brief names (`P1`, `P2`, `P3`, `ALL`, `CLOSE`). Bring-up rc 0, preflight ok, `HUIP_SESSION=192.168.1.9/24`.

| Condition | Result | Numbers |
|---|---|---|
| C1 socket and network | PASS | `e_announce` 1, `e_open` 1, `e_outopen` 1, `e_nostart` 0, `e_network=wlan0` (`SAME_SUBNET=yes`) |
| C2 sender runs | PASS | P1 `e_sent_lines` 6, `e_sent_zero` 0, `e_packets` 3864 |
| C3 datagrams whole | PASS | `packets` 4250, `seq_lost` 0, `loss_pct` 0.000, `RTP_GAPS none`, `srcs=192.168.1.9:4250`, `short` 0, `version_bad`/`pt_bad`/`pxcc_bad` 0, `ssrcs` 1, `max_len` 1400, `stap` 0 |
| C4 units and FU-A | PASS | `groups` 1993, `marker_bad` 0, `fu_bad` 0, `fu_starts` 236 |
| C5 parameter sets | PASS | `idr_groups` 2, `idr_intact_with_ps` 2, `idr_intact_without_ps` 0, `idr_partial` 0; P1 `e_keyframes` 1, `e_kf_own` 1 |
| C6 clock | PASS | `ts_rate` 1.000, `ts_step_median` 1792 (about 50 units per second of 90 kHz ticks, not 30 fps: the phone's frame rate is variable) |
| C7 decodes | PASS | `R2.nal`: `sps=3 pps=3 idr=2 width=1280 height=720`. `R2.ffprobe`: `codec_name=h264`, `Baseline`, 1280x720, `nb_read_frames=1992`, `decode_error_lines=0` |
| C8 SDP opens as written | see below | `FFMPEG_LIVE rc=124 seconds=100.6`, no PNG |
| C9 one keyframe request | PASS | `e_kf_req` 0, `e_kf_all` 0, `e_kf_early` 0 |
| C10 receiver gone | INCONCLUSIVE | `e_fail_lines` 0 in ALL and P3; the failure path did not run. P3 `e_sent_lines` 3, `e_packets` 646 |
| C11 close | PASS | CLOSE `e_stopped` 1, `e_destroy` 1 (the run's own exit) |
| C12 no cost | see below | `e_ssl` 1, `e_destroy` 0, `e_errors` 0, `e_auxdec` 0 in ALL; main `windows=39`, `windows_rendered_ge1=39`, `median_fps` 24 (R1: 29, so 5 below, the limit); phone `No input for display` 0, `Video focus gained before configurations received` 0, `Critical error` **2** |

Decisive lines:

- `10-13 22:51:25.817 ... [ServiceDiscovery] Announcing an auxiliary display on VIDEO_AUX: RTP_PUSH 1280x720 as _1280x720, margins 0x0, density 213, role=AUXILIARY, content 65538, input on INPUT_AUX`
- `10-13 22:51:26.009 ... RtpStreamOutput.start | SecondScreen: RTP push to 192.168.1.11:5004 open (network=wlan0)`

**Open question 1, C8 (FFmpeg opened the SDP as written and got no picture in 100.6 s).**
- By the letter: `rc` is 124 and the `P2` window holds `e_keyframes` 1, which the brief grades FAIL.
- That one keyframe is a window-edge artifact. The 10 s report that counts it is stamped 22:52:36.246 and covers 22:52:26.2 to 22:52:36.2. FFmpeg started at 22:52:36.205 (`R2-p2` marker 22:52:36.205). The receiver's second IDR lies 6.3 s before its last datagram (about 22:52:35.4), so about 22:52:29, before FFmpeg started. All 10 later reports show `0 keyframes`.
- So no keyframe was sent during FFmpeg's life, which the brief calls INCONCLUSIVE. FFmpeg saw only P slices (`non-existing PPS 0 referenced`, `no frame!`), never an SPS. No SDP error was seen, and the stream is valid (R2.ffprobe).
- Real finding for the thread: the phone's IDRs went out at about 22:51:26 and about 22:52:29, then none for at least 108 s. A receiver that joins after an IDR waits at least that long, and the candidate asks for no keyframe (`e_kf_req` 0). The brief expected a gap up to about 69 s.

**Open question 2, C12 (`Critical error` 2).**
- By the letter, FAIL-B. Both lines are in `R2-launch..R2-end`: `22:51:00.479 GH.WirelessStartup: Critical error encountered` and `22:51:00.480 CAR.SERVICE: Critical error 3 detail: 52 msg: io error`.
- They fall 25 s before `SSL handshake complete` (22:51:25.204) and 26 s before the push socket opened (22:51:26.009), and before the aux display was announced (22:51:25.817). The push cannot have caused them. They are the phone's first wireless startup attempt in the bring-up, which `R2-launch` already includes.
- The session itself: one SSL handshake, no destroy, no error line, 39 of 39 main windows rendered, nothing from the phone after `R2-up`.
- Later `Critical error` lines (22:54:51 to 22:54:52) fall after `R2-end` (the exit teardown) and do not count, as the brief says.

The host reads R2 as PASS with C8 INCONCLUSIVE. By the brief's letter it would be FAIL on C8 and FAIL-B on C12. The operator decides before `approve`.

## R3A. An address that nobody answers

**PASS**

- Destination 192.168.1.253 (no ARP entry). Bring-up rc 0.
- `10-13 22:56:43.857 ... RTP push to 192.168.1.253:5004 open (network=wlan0)`; `e_open` 1, `e_network=wlan0`.
- `e_sent_lines` 6, `e_sent_zero` 0, `e_packets` 5051, `e_bytes` 6418025. `e_fail_lines` 0: no failure class appeared, so the failure path did not run.
- `e_ssl` 1, `e_destroy` 0, `e_errors` 0; main `windows=12 windows_rendered_ge1=12 median_fps=29`; phone `win|Critical error=0`; `e_stopped` 1 in `R3A-end..R3A-closed`.

## R3C. No address stored

**PASS**

- `e_unavail` 1, `e_announce` 0, `e_vidaux` 0, `e_open` 0, `e_sent_lines` 0, `e_ssl` 1, `e_destroy` 0.
- Main `windows=8 windows_rendered_ge1=8 median_fps=29`; phone `win|Critical error=0`, `win|No input for display=0`.

## Report back (brief section 9)

1. A real receiver took the stream: 4250 packets, `loss_pct` 0.000, `RTP_GAPS none`, `fu_starts` 236, `fu_bad` 0, `marker_bad` 0, `idr_groups` 2, `idr_intact_without_ps` 0, 1280x720, 1992 frames. The SDP opened without error in FFmpeg but gave no picture in 100.6 s because no keyframe came during that time.
2. Bound network: `wlan0` in R2 and R3A, `SAME_SUBNET=yes`.
3. Cost: main `median_fps` and rendered windows: R1 29, 9/9; R2 24, 39/39; R3A 29, 12/12; R3C 29, 8/8. Phone windowed `Critical error`: R1 0, R2 2 (before the session, see above), R3A 0, R3C 0. Send-failure classes in R2 phase 3 and R3A: none.

## Anything the brief did not ask about

- R2 main `median_fps` was 24 against 29 in R1, 5 below with the push running (the brief's limit is 5). R2 ran 39 windows over about 3.5 min; R1 ran 9. With the idle map this may be the phone's own variable frame rate; no run isolates it.
- The phone's first wireless startup attempt in R2 logged `Critical error ... io error` and the bring-up still succeeded on the first attempt (rc 0). R1 and R3A and R3C did not show it in their windows.
- Gradle daemon left idle (pre-flight flags it); the PC firewall rule the operator added still needs removing.
