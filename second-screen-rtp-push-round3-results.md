# second-screen-rtp-push, round 3 results (PARTIAL: stopped by the operator during S12)

**Candidate:** C = fork/fix/rtp-push-recovery @ 8d4af793b5e87a7b44f1e0ac01976a8db21766f6 (tree 153794946844e3a07135c9ad9b505bdcee096d4f)   **Baseline:** none
**APK md5:** C 72f6c98975476f1c9212add2cf166637
**Unit:** D-HU (head unit), D-POCO (phone), the tester PC as RTP receiver   **Date:** 2026-10-29   **Operator interventions:** 2 (the opening yes, which included the PC firewall rule `sudo ufw allow proto udp from 192.168.1.0/24 to any port 5004:5005`; the stop request)

## Setup notes

- Attempt 0 (23:29-23:30) was a brief defect, not a result: udp_probe.sh, udp_rx_probe.py and ssr_keys.py are printed in the brief under bold headings and were not extracted into scripts/, so R0's UDP probe ran no receiver and R0 graded FAIL (UDP probe none, from= 0 of 1), S12 to S5 NOT_RUN by the stop rule. The host extracted the three blocks verbatim into scripts/ and ran the session again from R0. Attempt-0 files are in attempt0-missing-helpers/.
- Attempt 1: R0 PASS at 23:31:48. S12 (the point of the round) started at 23:31:48 and was stopped by the operator's request to pause and report; the session process was killed and its 9 processes reaped (rig_run.sh reap). No S12 grade exists, so it is recorded NOT_RUN, not FAIL. S2, S3, S4 and S5 never started.
- The brief's cleanup run was run by hand through rig_run.sh (cleanup.sh with ss_lib.sh): RESTORED=1 (D-HU settings.xml equals the backup), script exit=1 for a reason not read. The PC firewall rule above was NOT removed; the operator keeps it for the re-run.
- Closing: rig_requires.py baseline printed BASELINE FAILED: 2 radios did not reach the baseline (D-HP bt and D-SAM bt read unknown in that listing); the closing pre-flight afterwards read D_SAM bt 1, D_HU, D_POCO and D_MOTO wifi 1 bt 1: PREFLIGHT OK.

## R0 Identity and prerequisites
**PASS**

13/13 conditions, attempt 1: live APK md5 equals apk/C.apk, QUERY_STATE carries 8d4af793b5e8, DEX holds RtpRefreshPolicy (4) and RtpPacingPolicy (3), D-HU wlan0 192.168.1.9, ffmpeg on the PC, UDP probe reached the PC.

## S12 First frame and refresh on the aux channel (the point)
**UNTESTABLE**

Not run to a verdict: stopped by the operator 0 to 1 minute in (23:31:48 start). Re-run needed. UNTESTABLE here means the run was not completed, not that the rig cannot do it.

## S2, S3, S4, S5
**UNTESTABLE**

Never started (round paused by the operator).

## Rcleanup
**INCONCLUSIVE**

Run by hand; RESTORED=1, exit 1, cause unexplained. The firewall hand step was deliberately skipped.

## Anything the brief did not ask about

- The brief needs its unnamed script blocks extracted; see Setup notes, a defect for the brief's writer.

- Evidence: release `rig-evidence-second-screen-rtp-push`, asset `second-screen-rtp-push-round3-captures.zip` (4257345 bytes), sha256 `f21bb2b27f2a79c92f356bc87efb4d11334f94e1d8f12d2ea8e03828e3a5b0e8` (moved from `rig-evidence-second-screen-rtp-push-round3` on 2026-10-30).
