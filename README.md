# Transfer branch, not part of the app

This branch carries test briefs and results between the session that writes the code and the agent
that runs it on hardware. Orphan branch: no shared history with `main` or any feature branch, and
nothing here is ever merged.

It is `transfer/rig-rounds`. Until 2026-09-10 it was named after the round it was created for,
`transfer/hotspot-unreadable-config-results-20260807`; links into that name are dead, so cite files
here by path.

## Read this, in this order, and stop there

1. **`TESTING-TEMPLATE.md`**, the standing method. Read it once: capture rules, how to read and
   write `settings.xml`, the app's automation surface, the clean-run protocol, install discipline,
   the four verdicts, and the format results come back in.
2. **`## Queue` below, then your own row in `## Threads`**, found with
   `grep -F '| `<thread>`' README.md`. The row names the one brief to read; it names everything
   else it needs.

**Do not read the other threads' briefs and results.** They are a different fault on a different
build, and several were superseded by later rounds in their own thread. Reading them costs context
and, worse, primes you for a signature that does not apply to the run you are doing. If a brief
needs a prior round, it cites it by filename. Fetch that one, not its neighbours.

The same goes for `archive/`. It is the historical record, not orientation.

Three rules from the template are worth repeating, because breaking any of them invalidates a round:

- **Use the rig's existing scripts.** `hur-wifi-test-scripts/` already has `build_hur.sh`,
  `run_unit_tests.sh` and others. Inventory the folder at the start of every round, use what fits,
  and only add a script when nothing does, leaving it there for next time.
- **Settings are changed in `shared_prefs/settings.xml` with the app stopped**, never through the
  UI, and the settings list is never scrolled with adb.
- **Run the whole round unattended.** Moving between runs, restoring state and deciding that a gated
  run is INCONCLUSIVE are all yours. Escalate only for a failed build gate, a genuinely ambiguous
  fork the brief did not cover, something destructive, or a broken rig.

## Queue

A brief with no results file of the same name. Add a line here when a brief is pushed; delete it
in the commit that pushes the results. Four older briefs have no same-named results because they
were reported under another filename, and are listed so the pairing rule does not re-queue them.

- `zlink-aa-sink-decision-brief.md`, PC only, the descriptor decode and disassembly part two could not run; tools are in `tools/zbt/`
- `audio-focus-round11-brief.md`
- `link-stall-periodic-scan-round4-brief.md` (2.4 GHz plus constrained memory; round 5 ran first and PASSed)
- `video-dropped-frame-keyframe-round7-brief.md` (R1 is a desk check, R2 optional)

Superseded, do not run: `bring-up-status-pill-round1-brief.md` is folded into
`bring-up-status-pill-and-poke-readiness-round1-brief.md` as its Part A, and
`aac-audio-round1-brief.md` into `bring-up-status-pill-and-poke-readiness-round5-brief.md` as its
Part D.

Reported elsewhere, do not run: `bt-auto-start-disconnect-round1-brief.md`,
`wifi-direct-stable-identity-round3-brief.md` and `-round4-brief.md` are all answered by
`wifi-direct-and-bt-auto-start-round1-results.md`; `headunit-reloaded-wireless-wifidirect-brief.md`
by `bssid-round1-results.md` and its addendum.

## Threads

| Thread | State | Next |
|---|---|---|
| `hp-slate-bringup` | **DONE** | hp-slate-bringup-round1-results.md; R0-R2 PASS, R3 FAIL with root cause found (FPS overlay hidden under TEXTURE view-mode by an addView-order bug in `AapProjectionActivity`, not device-specific). "Can't export logs" was a wrong retrieval path (`/storage/sdcard0/...`, not `/storage/emulated/0/...`) — export itself works. Fix not applied this round (scope). |
| `native-aa-recovery-identity-and-speed` | **DONE** | native-aa-recovery-identity-and-speed-round4-results.md; round 4 PASS, PR-ready. Nothing queued. |
| `pr954-review` | **CLOSED** | pr954-review-findings.md and reply-draft; PC-only review, no rig time. Nothing posted to GitHub. |
| `ultrawide-touch-alignment (round 8)` | **DONE** | ultrawide-touch-alignment-round8-results.md; round 8 PASS, PR pending. Nothing queued. |
| `ultrawide-touch-alignment (round 7)` | **DONE** | ultrawide-touch-alignment-round7-results.md; round 7 PASS across all screen modes. Nothing queued. |
| `ultrawide-touch-alignment (round 6)` | **DONE** | ultrawide-touch-alignment-round6-results.md; round 6 PASS. Follow-up covered by the round 7 entry above. |
| `ultrawide-touch-alignment (round 4)` | **IN FLIGHT** | ultrawide-touch-alignment-round4-results.md; R5 FAIL, touch-centring offset needs rework before next round. Round 5 PAR/margin probe queued. |
| `ultrawide-touch-alignment (round 3)` | **DONE** | ultrawide-touch-alignment-round3-results.md; round 3 all PASS, PR-ready. Nothing queued. |
| `ultrawide-touch-alignment (round 2)` | **DONE** | ultrawide-touch-alignment-round2-results.md; round 2 PASS, two findings need root-cause before CONTAIN/COVER ships. |
| `ultrawide-touch-alignment (round 1)` | **DONE** | ultrawide-touch-alignment-round1-results.md; round 1 all PASS. Nothing queued, ready to land. |
| `wireless-bring-up-and-5ghz` | **DONE** | wireless-bring-up-and-5ghz-round3-results.md; round 3 PASS, e6ed1ad2 hardware-clear. Nothing queued. |
| `usb-aoa-dongle` | **DONE** | usb-aoa-handoff-round2-results.md; round 2 done, R10/R11/R2 PASS, R7/R8 untestable on this rig, R3 inconclusive. Nothing blocks the branch. |
| `driver-selection-native` | **DONE** | driver-selection-native-round6-results.md; round 6 all PASS, branch PR-ready. Nothing queued. |
| `narrow-band-and-disconnect-scope` | **DONE** | narrow-band-and-disconnect-scope-round1-results.md; round 1 PASS (R4 inconclusive, pre-registered, not a FAIL). Nothing queued. |
| `aa-numeric-keyboard` | **DONE** | aa-numeric-keyboard-round2b-results.md; round 2b PASS, TelephoneKeypadTemplate answers the numeric-keypad lever. Nothing queued. |
| `pr-readiness` | **DONE** | pr-readiness-round1-results.md; round 1 PASS, all three PRs hardware-clear and can open. Nothing queued. |
| `field-ride-automation-btautostart` | **DONE** | field-ride-automation-btautostart-round3-results.md (and round2); R1-R5 PASS on both pairings, PR-ready. Nothing queued. |
| `automation-followups` | **IN FLIGHT** | No brief filed; candidate pr/automation-command-surface @ c8aef187. Verify A1-A3: log marker WARN, dual action spellings, GET_SETTINGS path guard. |
| `wifi-direct-stable-identity` | **CLOSED** | wifi-direct-and-bt-auto-start-round1-results.md; R1+R2 PASS, R6 benign FAIL fixed in 7cd74b5d and confirmed via pr-readiness round1. Superseded. |
| `bt-auto-start-disconnect` | **IN FLIGHT** | wifi-direct-and-bt-auto-start-round1-results.md; R6/R7 PASS in Self Mode, Native-AA lever inconclusive (rig limit). Rebuild 16965e75 still needs R6 re-check. |
| `p2p-stack-cycled` | **CLOSED** | No dedicated round; diagnostic folded into pr/native-aa-wireless. Verified passively via pr-readiness-round1-brief.md §4 (no false fire on our own stand-down). |
| `five-ghz-channel` | **DONE** | five-ghz-channel-round2-results.md; round 2 PASS, branch PR-ready. Retry ladder still unmeasured (this chip has no -2 case). |
| `reloaded-wireless-wifidirect` | **DONE** | bssid-round1-results.md (+addendum); EUI-64 route works, two-phone RFCOMM blocked by a phone-side Gearhead race. Continued as hfp-profile-presence. |
| `hfp-profile-presence` | **DONE** | hfp-profile-presence-round2-results.md; round 2 PASS, default reaches the gate cleanly, branch PR-ready. Nothing queued. |
| `intercom-native-mode` | **DONE** | headunit-info-round3-results.md; round 3 R5 riding config PASS with intercom over SCO, branch PR-ready. |
| `selfmode-playback-focus` | **DONE** | selfmode-playback-focus-round1-results.md; round 1 done, change inert in Self Mode and safe to ship. Nothing queued. |
| `selfmode-keyboard-viewmode` | **DONE** | selfmode-keyboard-viewmode-round2-results.md; round 2 all PASS, ready for PR. Nothing queued. |
| `aap-reorg` | **DONE** | aap-reorg-round3-results.md; round 3 all PASS, rebuild-loop finding fixed, branch ready for PR. Nothing queued. |
| `selfmode-playstore-route` | **DONE** | selfmode-playstore-route-round1-results.md; playstoreDebug compiles clean, hypothesis refuted for the 17.4+ route. Ask reporter for phone-side logcat. |
| `post-beta1-self-mode` | **DONE** | post-beta1-self-mode-round2-results.md; round 2 PASS, rebased candidate ships. Defect 2 hardware confirmation deferred. |
| `post-beta1-latency-instruments` | **DONE** | post-beta1-latency-instruments-round2-results.md; round 2 PASS, ships. Export-ANR finding routed to a separate main fix. |
| `log-and-selfmode-fixes` | **DONE** | log-and-selfmode-fixes-round1-results.md; round 1 PASS, ready for PR. |
| `headunit-info` | **DONE** | headunit-info-round3-results.md (+addendum); rounds 1-3 done, branch PR-ready. |
| `self-mode-call-foreground` | **DONE** | self-mode-call-foreground-round3-results.md; round 3 PASS across four live-call cycles, fix ships. Nothing queued. |
| `video-feed-backpressure` | **DONE** | video-feed-backpressure-round1-results.md; core mechanism confirmed. R4 black-screen interaction and R3 input-lag/underrun mismatch need review before shipping. |
| `wifi-launcher-parity` | **DONE** | wifi-launcher-parity-round1-results.md; 2 regressions confirmed and fixed. UserExitHotspotPolicyTest.kt:154 compile error needs a one-line fix before upstream. |
| `self-mode-bt-audio` | **DONE** | self-mode-bt-audio-round1-results.md; round 1 answered, Bluetooth Media-audio toggle reproduces the silence. Nothing queued. |
| `connection-failure-banner` | **DONE** | connection-failure-banner-round4-results.md; round 4 all PASS, ready for PR. Nothing queued. |
| `release-test` | **IN FLIGHT** | release-test-round1-results.md; Part B complete. Part A (A1-A7) out of scope this round, still open for a future round. |
| `wire-corruption-escalation` | **DONE** | wire-corruption-escalation-round1-results.md; round 1 PASS, branch extended to cd603ac0, going to PR. |
| `render-side-concealment` | **DONE** | render-side-concealment-round1-results.md; round 1 all PASS, going to PR. Nothing queued. |
| `session-vpn-lever` | **DONE** | session-vpn-lever-round2-results.md; round 2 PASS, ships. ACTION_START_WIRELESS_SCAN cascade finding remains open for Native AA work. |
| `native-aa-5288` | **DONE** | native-aa-5288-round1-results.md; round 1 all PASS. Nothing queued. |
| `media-gap-instrument` | **DONE** | media-gap-instrument-round2-results.md; round 2 PASS, going to PR. Ceiling fix confirmed; R2's 85% ceiling untriggered this round. |
| `native-gps-forwarding` | **DONE** | native-gps-forwarding-round1-results.md; round 1 PASS, R8 found phone-side Maps still uses its own spoofed location. Follow-up round on map-source suggested. |
| `release-next` | **IN FLIGHT** | release-next-round6-results.md; R1 FAIL, R2 604.6s black screen found (2.4x round 5), R3 inconclusive. Awaiting next brief. |
| `video-pipeline-stack` | **CLOSED** | video-pipeline-stack-round2-results.md; work now lives on fix/video-stack, tested under the release-next thread. Superseded here. |
| `video-dropped-frame-keyframe` | **QUEUED** | video-dropped-frame-keyframe-round7-brief.md; R1 is a desk check, no rig time; R2 optional. Fix already in the upstream PR. |
| `audio-focus` | **QUEUED** | audio-focus-round11-brief.md; queued, no round run yet. |
| `audio-sink-jitter` | **QUEUED (round 3)** | round3-brief.md against `aaed6509`, gate 1932/0. A repeated Media Sink Setup no longer destroys a live sink, the default cushion is 16 and a sink can deepen its own, and the video ack is the phone's flow control again. P5 is the headline; the round's real question is what `dumpsys media.audio_flinger` says at each marker. |
| `usb-device-diagnostics` | **DONE** | usb-device-diagnostics-round5-results.md; round 5 PASS, Direct Boot fix works, branch PR-ready. R6 (WiFi/BT locked-defer) stays hardware-unverified. |
| `usb-session-teardown` | **DONE** | usb-session-teardown-round1-results.md; diagnosis complete, attributed to Android Auto's own headunit server tearing down on any USB port event. Nothing queued. |
| `audio-start-and-teardown` | **DONE** | audio-start-and-teardown-round1-results.md; audio half validated, WiFi Direct commit dropped from branch. Blocks mic-uplink round1 M1 (CarKeyReceiver trigger not delivered). |
| `mic-uplink` | **DONE** | mic-uplink-round3-results.md; round 3 PASS, M6a fixed 5/5, branch ships. Nothing queued. |
| `discovery-socket-leak` | **DONE** | discovery-socket-leak-round7-results.md; answered at round 7, awaiting a PR. Nothing queued. |
| `video-black-after-background` | **MERGED** | video-black-after-background-round8-results.md; closed at round 8, shipped upstream. |
| `link-stall-periodic-scan` | **QUEUED** | link-stall-periodic-scan-round5-results.md; round 5 PASS, mechanism absent on this rig. Round 4 (2.4GHz + constrained memory) still queued via link-stall-periodic-scan-round4-brief.md. |
| `media-key-routing` | **MERGED** | media-key-routing-round1-results.md; merged upstream. Nothing queued. |
| `external-bt-zbt` / `zlink-wpp-channel` | **IN FLIGHT** | external-bt-tcp-link-findings.md; candidate b64912805, 8 commits on main. Pending: speak-first fallback, Type 4/1 retransmits, daemon-reachability gate, rmnet* exclusion from soft-AP pick. |
| `zlink-media-usb-and-carlink` | **DONE** | zlink-media-usb-and-carlink-results.md; PC-only. AA sink can announce AAC (runtime `get_is_AA_AAC_audiotype`); no video branch on `CHANNELS_24GHZ_ONLY`; no BT link action after `AA_wait_port ok`. Wired AA = libusb AOA; wired CarPlay = USB-gadget NCM + libusbmuxd; no Android USB filter. CarLink = BLE-GATT mode, disjoint from AA. Descriptor decode blocked (script absent). Response: zlink-media-usb-and-carlink-response.md (Q4's AP-host line corrected: the vendor head unit hosts the SoftAP). |
| `zlink-aa-sink-decision` | **QUEUED** | zlink-aa-sink-decision-brief.md; PC-only, part three: descriptor decode plus disassembly of `get_is_AA_AAC_audiotype`, `max_unacked` and the offered video list. Scripts shipped in `tools/zbt/`. |
| `aac-audio` | **IN FLIGHT** | round 7 brief queued. Round 6: F2 PASS clean on a second decoder vendor; F1's band detection, AAC announce and 720p30 cap PASS, its decoder-start condition unreachable. Round 7 redoes it, adds F1b (cap off, same forced band) and still owes F3. |
| `qf001-firmware-teardown` | **DONE** | qf001-firmware-teardown-results.md; answered. Nothing queued. |
| `headunit-reloaded-decompile` | **CLOSED** | headunit-reloaded-decompile-findings.md; PC-only decompile comparison, no rig time. Nothing copied; their Native AA wireless path closely matches ours. |
| `gemini-research-video-optimization` | **CLOSED** | gemini-research-video-optimization-findings.md; PC-only fact-check, no rig time. Two open items: KEY_LOW_LATENCY and AudioTrack low-latency mode. |
| `logcat-access-android13` | **CLOSED** | logcat-access-android13-findings.md; PC-only research, no rig time. Fix landed on fix/log-and-selfmode-fixes (809adff3, a5cd6fc8). |
| `native-aa-poke-hardening` | **DONE** | native-aa-poke-hardening-results.md; answered. Nothing queued. |
| `video-latency` | **DONE** | video-latency-round1-results.md; answered at round 1. Nothing queued. |
| `hotspot-unreadable-config` | **CLOSED** | No dedicated brief; this is the origin round the transfer branch was created for, now historical. |
| `p2p-bringup-loop` | **DONE** | p2p-bringup-loop-round1-results.md; round 1 PASS, regression-clean, ships. Nothing queued. |
| `wpp-over-tcp` | **DONE** | wpp-over-tcp-round5-results.md; PR-ready at aa54e6e9, ping-count question resolved. No further round needed. |
| `bring-up-status-pill-and-poke-readiness` | **DONE** | round 9 (`a8917a7a`) no FAIL: A2/A2b/A2e PASS, re-arm fix confirmed. Part O's five runs (O5, O1-O4) all PASS: offer bar reached the screen with a Yes tap, a No-timeout, once-per-phone, and the home-screen control, first time in nine rounds. See round9-results.md. |
| `bring-up-status-pill-and-poke-readiness (round 7)` | **DONE** | round 7 no FAIL: R1/A1/A3/B2/C1-C5/E5 PASS and the offer's ASK arm reached the screen at last. Three defects found, all fixed on `e466171a`. See round7-results.md. |

Round files are `<thread>-round<N>-brief.md` and `<thread>-round<N>-results.md`. A brief with no
matching results file is a round nobody has run yet. That pairing is the only queue there is, so
keep the names regular.

## Keeping this file lean

This README was 1,137 lines on 2026-08-14 and a tester agent read all of it before every round;
that history is in `archive/rounds-log-through-2026-08-14.md`. By 2026-09-10 it had grown back to
171 KB in 67 table rows averaging 2,450 characters, about 43,000 tokens read at the start of every
round. The rows as they stood are in `archive/threads-table-through-2026-09-10.md`; the table above
is what they condensed to, under 300 characters a row.

So, for whoever writes the next brief: **the outcome of a round goes in its own results file and
the table above, not into a narrative here.** One row, one state, one filename, and a Queue line
while the brief is unrun. The State cell is one of **QUEUED**, **IN FLIGHT**, **DONE**, **MERGED**,
**CLOSED**. If the state needs a sentence of context, it belongs in the next brief, where the
person who needs it is already reading. A row over 300 characters is the signal this file is
growing back.
