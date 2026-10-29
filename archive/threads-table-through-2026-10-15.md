# Threads table rows archived on 2026-10-15

The `## Threads` rows of `README.md` for five threads with results and no queued round, and their files moved to `archive/rounds/`. `portrait-maps-touch` and `second-screen-outputs` wait on PRs #1117 and #1097; a new round on either restarts its thread. Verbatim, in the original order.

| Thread | State | Next |
|---|---|---|
| `call-audio-route` | **DONE, round 1** | `call-audio-route-round1-results.md`. Moto claim moves no call: car and moto arms route alike, with and without the intercom. Call is on D-HU Bluetooth only while ringing, earpiece once answered. Open: timer or answer? |
| `pre-350-fixes` | Round 1 done | `pre-350-fixes-round1-results.md`. R0, R4, R5, R2 PASS. R3 INCONCLUSIVE (phone keyboard not reproduced; night key PASS on key 189, F1 to F12 swallowed by D-HU firmware). R1 UNTESTABLE as written; R1s INCONCLUSIVE (IME fullscreen). |
| `second-screen-rtp-push` | Round 2 done on `8fc611a4`: O1, R5, O2, C-FPS PASS (main 29 fps push on and off); R4 INCONCLUSIVE (D1 FFmpeg quit at 10 s before any datagram; D5 fail line 1.7 s outside window). IDR every ~60 s | `second-screen-rtp-push-round2-results.md`. Next: FFmpeg D1 retry started just before the push opens, if wanted. |
| `second-screen-outputs` | Round 2 done at `4897c121`: R0-R4 PASS, VIEWER-SAM PASS, VIEWER-PC FAIL (VLC wrote 0 PNGs); the aux input fixes round 1 | `second-screen-outputs-round2-results.md`. Next: PR-ready; PC viewer needs another capture method. |
| `portrait-maps-touch` | **DONE, round 3 PASS** | `portrait-maps-touch-round3-results.md`: candidate `ec6cb516` clamps portrait DPI to a 730 dp phone width. Q1 198 DPI w731, Q3 square pixels cap 157 w734, Q2 automatic 161 w795, Q4 landscape 1034x646 unchanged: all PASS, Maps takes touch. |
| `station-scan` | **DONE round 1** | `archive/rounds/station-scan-round1-results.md`: D-POCO 5 scans/600 s unjoined, 0 joined, no stall; D-SAM idle 0, session 3. R3/R4 INCONCLUSIVE on D-POCO and D-HU: `LocalOnlyHotspot` fails reason=2. | Needs a start with no P2P interface up. |
