# Transfer branch — not part of the app

This branch carries test briefs and results between the session that writes the code and the agent
that runs it on hardware. Orphan branch: no shared history with `main` or any feature branch, and
nothing here is ever merged.

Its name still says `hotspot-unreadable-config` because that is the round it was created for. It is
now the general channel; the name is left alone so existing links keep working.

## Read this, in this order — and stop there

1. **`TESTING-TEMPLATE.md`** — the standing method. Read it once: capture rules, how to read and
   write `settings.xml`, the app's automation surface, the clean-run protocol, install discipline,
   the four verdicts, and the format results come back in.
2. **The brief named in the table below for your thread.** It names everything else it needs.

**Do not read the other threads' briefs and results.** They are a different fault on a different
build, and several were superseded by later rounds in their own thread. Reading them costs context
and, worse, primes you for a signature that does not apply to the run you are doing. If a brief
needs a prior round, it cites it by filename — fetch that one, not its neighbours.

The same goes for `archive/`. It is the historical record, not orientation.

Three rules from the template are worth repeating, because breaking any of them invalidates a round:

- **Use the rig's existing scripts.** `hur-wifi-test-scripts/` already has `build_hur.sh`,
  `run_unit_tests.sh` and others. Inventory the folder at the start of every round, use what fits,
  and only add a script when nothing does — leaving it there for next time.
- **Settings are changed in `shared_prefs/settings.xml` with the app stopped** — never through the
  UI, and the settings list is never scrolled with adb.
- **Run the whole round unattended.** Moving between runs, restoring state and deciding that a gated
  run is INCONCLUSIVE are all yours. Escalate only for a failed build gate, a genuinely ambiguous
  fork the brief did not cover, something destructive, or a broken rig.

## Threads

| Thread | State | Next |
|---|---|---|
| `native-aa-5288` | **round 1 done, all PASS** | R0-R4 all **PASS**. R0: first-ever compile of `c2efedda`/`e9f5d2b6`, clean; 565/565 tests, `WirelessServerRestartPolicyTest` 12/12. R1 (clean start): bind in 6ms, single group. **R2 (the point of the round): held port 5288, released it, repair chain fired in full — 1.819s from `found port 5288 unbound` to `Incoming connection detected`.** **R3 (the run that mattered most): port held 5 minutes straight, rebuilds bounded to exactly 3 per 60s window (min gap 15.1s, floor 10s), correct INFO backoff after the third, and — the real subject — `createGroup SUCCESS!` count stayed at 1 and `p2p-wlan0-N` stayed at one interface (`8`) the whole 5 minutes despite constant repair attempts.** R4 (10-min clean control): zero rebuild/backoff/error/null-handshake lines; 4 credential deliveries all agreeing on SSID+BSSID (torn-read race itself didn't fire — pre-registered INCONCLUSIVE on that half, as expected). One Setup-notes finding worth a look: every run produced a benign `MATCH! Starting AapService` from the phone's own Bluetooth reconnect (not a poke) with zero group churn each time — `TESTING-TEMPLATE.md`'s discard rule may need narrowing to "a second `createGroup SUCCESS`" specifically. See `native-aa-5288-round1-results.md`. Nothing queued next for this thread. |
| `media-gap-instrument` | **round 2 done, going to PR, nothing queued** | Branch is now `fix/media-gap-instrument-and-attribution` @ `f48baee7`: two log-line commits were added after round 2 from round 1's remaining findings (the station-state line now prints on the unjoined arm too, and service discovery says when the audio sink is switched off). Neither is queued for a round of its own; both print at session start, so two greps appended to whatever runs on this rig next will confirm them. **Caveat on round 2's R2**: it passed, but the picture ran at 39-46fps for the whole window, so there were zero qualifying gaps and the 85% ceiling was short-circuited rather than evaluated. Its only proof remains the unit-test replay of round 1's measured numbers, and round 2 shows the trickle is not reproducible on demand. Round 2 otherwise confirmed the ceiling fix: **R0 PASS** (594/594, `LinkGapMonitorTest`=17, `StationCoexistencePolicyTest`=8, all named tests present). **R2 PASS — the point of the round**: `inbound video quiet` stayed 0 on a stationary, untouched Google Maps screen for 3 minutes, throughput steady at 39-46fps the whole time. **R1 PASS**, all four zeroes over a clean 10-minute session, same live session reused for R2. **R4 PASS**, `Disconnecting the other network` gone (0), the coexistence line still fires descriptively. R3/R5/the unjoined arm were not re-run, per the brief. See `media-gap-instrument-round2-results.md`. Round 1: R0 PASS (588/588), R1 PASS (four zeroes, 45-55 fps), R3 PASS after a stale `enable-audio-sink=false` was found and fixed, R4 joined arm PASS (unjoined arm UNTESTABLE on this rig), R5 INCONCLUSIVE at 1-in-300. R2 FAIL was that round's finding and drove the round-2 design change. See `media-gap-instrument-round1-results.md`. |
| `native-gps-forwarding` | round 1 done, plus a user-requested R8 | R0-R7: R3 (the point of the round) PASSED: `sentOnWire=true` on the first try, no LOCATION drop after channel open. R4 (cadence count) is INCONCLUSIVE — OS-level mock-injection artifact on this rig, not a code defect, confirmed with a positive control. R5 (backstop/re-arm) PASSED. R2/R6/R7 all PASSED (R2 flipped per the brief's own contingency, since this rig had a real live GPS fix). **R8 (added post-round): phone-side GPS spoof still pulls the projected Google Maps view away from the car's real position, with screenshot evidence** — but the branch's own send path was verified correct and unbroken throughout (303 clean fixes, no re-priming); `com.google.android.apps.maps` on the phone was found holding direct listeners on the phone's own (spoofed) location provider, independent of the AA sensor channel. See `native-gps-forwarding-round1-results.md`. Worth a dedicated follow-up round on the end-to-end map-source question; nothing else queued. |
| `release-next` | **round 6 done, awaiting next brief** | R1 (reserved cycle) **FAIL**: zero holds, never reached a 3rd check, recovery still via the `sync_stall` ladder at 77.6s — source-traced cause is a decoder-rebuild re-arm racing a 60s-from-check (not 60s-from-cycle) NUDGE retry. R2 (control) zero-counts **PASS** but its own recovery-gap vs R1 is void (6.3x apart) and it surfaced a **604.6s continuous black screen**, invisible to the app's own audit the whole time (mode-2 corruption never trips the reader-stage signal) — round 5's R3 outlier (253.9s) recurring at 2.4x the magnitude, worth its own round. R3 (isolated-drop guard) **INCONCLUSIVE**, 0/2 faults in 11.4min even after the brief's own authorized remedy (panned the map). R4 clean control **PASS** on all three jobs including the free discovery-loop check (0 on a plain native session). R6 read-desync **PASS**. See `release-next-round6-results.md`. |
| `video-pipeline-stack` | **acted on** | Round 2 validated the decoder-wedge fix: **R0 PASS** (454/454, first ever compile), **R2 PASS** (both faults recovered in ~0.55s via `cycling video focus` → `keyframe reached the codec`, `rendered=` never 0 against round 1's 90+s), **R4 PASS** (control clean, one codec init across 10 corrupted frames, zero starvation lines), **R6 PASS** (10 clean minutes, zero `DELTA_CHANGED`). **R3 INCONCLUSIVE** — 0 faults in 5 min at rate=20, which the brief predicted and which is now fixed at the source: the injector prints its candidate count per fault and a summary every 15s, so a zero-fault run says so during the run. **R5** answered the mode-4 probe: inert, and with the session's only working lever replaced it reproduced round 1's wedge in full (`rendered=0` for 108+s, unrecovered at capture end) — the probe has been removed. The whole five-branch stack has since been compacted into **one branch, `fix/video-pipeline` @ `d43112e2`, four commits** (457 tests), and the five old refs were **deleted** — anything still on one is orphaned and needs a fetch and reset onto the new branch, not a pull. Two tags keep this document's SHAs resolvable: `round2-validated` (`8f0beab1`, the APK measured below) and `stack-pre-compaction` (`b1057bf8`, all sixteen commits). **Superseded — this thread's work now lives on `fix/video-stack` and is tested under the `release-next` thread.** R2's validation was 2 injected faults; the `release-next` round 1 R6 run drew 90 at the same setting and reopened the wedge, so do not quote R2 as proof without its fault count. See `video-pipeline-stack-round2-results.md`. |
| `video-dropped-frame-keyframe` | **queued** (fix already in PR #826) | `video-dropped-frame-keyframe-round7-brief.md` — R1 is a desk check with no rig time; R2 is optional |
| `audio-focus` | **queued** | `audio-focus-round11-brief.md` |
| `discovery-socket-leak` | answered at round 7 | nothing queued; awaiting a PR |
| `video-black-after-background` | closed at round 8 | shipped in PR #826 |
| `link-stall-periodic-scan` | **round 5 done — round 4 still queued** | Round 5 tested whether a WiFi station scan blanks the P2P radio. **The brief was revised mid-round** (withdrew `wifi_scan_always_available` as a no-op while WiFi is on; redefined R3 from "suppress scanning" to "characterize this unit's real cadence against AOSP's 20/40/80/160s schedule"). **R0-R4 all PASS.** R0's unit-test gate found and fixed a real, pre-existing, scope-unrelated bug (`P2pOperatingChannelPolicy.frequencyMhzFor(14)` missing the 2484 MHz special case; one-line fix, local commit `307d85f2`, not yet pushed), then 552/552. **R1 (unassociated): zero stalls > 1.2s over 733s** — the brief's pre-registered "mechanism absent" outcome, scan events sparse/irregular (7 events, 27-160s gaps). **R2 (associated to 5GHz): also zero stalls**, scan rate roughly halved vs R1 (3/665s vs 7/733s) — confirms the directional claim with no stall difference on either arm. **R3 original (`wifi_scan_always_available=0`): no suppressive effect** — independently confirms the brief's own revision. **R3 redefined (15-min characterization + display off/on probe): the pure AOSP schedule only partially fits** (two clean post-toggle steps, 19.8s/40.2s, then breaks pattern) — **but a real, named third scanner explains the cadence far better**: `com.google.android.gms`'s `NetworkLocationScanner` (work-sourced to `com.unisoc.phone`) correlates within 30-200ms of 9 of 14 scan events. The brief's own requested `dumpsys wifi` `WifiConnectivityManager` localLog instrumentation is empty on this build — dead on this hardware. **R4: both lifecycle fixes confirmed working** — re-arm after a clean user exit forms a full second session with no join-watchdog teardown, and the manual poke button now re-arms closed listeners and successfully pokes (`Native AA listeners are closed — re-arming before the poke.` → `Successfully poked`), where before the fix it did nothing. Per the brief's own honest limit (§2): **none of this refutes the theory for the reporters' MediaTek silicon** — this UNISOC/Android-14 rig cannot carry #839/#824's fault at all on timescale grounds, but it newly shows a specific non-AOSP location-services scanner is live and worth checking for on the reporters' side. See `link-stall-periodic-scan-round5-results.md`. **Round 4 (`link-stall-periodic-scan-round4-brief.md`, 2.4GHz band + constrained memory profile) remains queued**, now the weaker of the two remaining hypotheses. |
| `media-key-routing` | closed | merged upstream |
| `external-bt-zbt` / `zlink-wpp-channel` | answered | teardown round done |
| `qf001-firmware-teardown` | answered | — |
| `headunit-reloaded-decompile` | answered | PC-only, no brief. Competing app's video decoder is independently written and behind ours (H.264-only, no HEVC, hardcoded buffer size) — nothing copied, nothing worth adopting. Its Native AA wireless path (fake-HFP AT-command values, P2P/BSSID fallback chain) matches this project's current source closely enough to look copied. See `headunit-reloaded-decompile-findings.md`. |
| `gemini-research-video-optimization` | answered | PC-only, no brief. Fact-check of a user-run Gemini research transcript on low-RAM optimization and decoder artifacts. Its headline "cycle AAP Video Focus to force an IDR" idea is already built and hardware-validated (`triggerFocusCycleRecovery`/`KeyframeCycleEscalationPolicy`), more conservatively than proposed; most other "fixes" already exist in `main`. Two open, untested items: `KEY_LOW_LATENCY`, `AudioTrack` low-latency mode. See `gemini-research-video-optimization-findings.md`. |
| `native-aa-poke-hardening` | answered | — |
| `video-latency` | answered at round 1 | — |
| `hotspot-unreadable-config` | closed | the round this branch was created for |

Round files are `<thread>-round<N>-brief.md` and `<thread>-round<N>-results.md`. A brief with no
matching results file is a round nobody has run yet — that pairing is the only queue there is, so
keep the names regular.

## Keeping this file lean

This README was 1,137 lines on 2026-08-14, and a tester agent read all of it before every round:
about 1,050 of those lines were finished rounds from threads unrelated to whatever was being run.
That whole history is preserved verbatim in `archive/rounds-log-through-2026-08-14.md`.

So, for whoever writes the next brief: **the outcome of a round goes in its own results file and the
table above, not into a narrative here.** One row, one state, one filename. If the state needs a
sentence of context, it belongs in the next brief, where the person who needs it is already reading.
