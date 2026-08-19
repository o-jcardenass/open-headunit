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
| `native-gps-forwarding` | round 1 done, plus a user-requested R8 | R0-R7: R3 (the point of the round) PASSED: `sentOnWire=true` on the first try, no LOCATION drop after channel open. R4 (cadence count) is INCONCLUSIVE — OS-level mock-injection artifact on this rig, not a code defect, confirmed with a positive control. R5 (backstop/re-arm) PASSED. R2/R6/R7 all PASSED (R2 flipped per the brief's own contingency, since this rig had a real live GPS fix). **R8 (added post-round): phone-side GPS spoof still pulls the projected Google Maps view away from the car's real position, with screenshot evidence** — but the branch's own send path was verified correct and unbroken throughout (303 clean fixes, no re-priming); `com.google.android.apps.maps` on the phone was found holding direct listeners on the phone's own (spoofed) location provider, independent of the AA sensor channel. See `native-gps-forwarding-round1-results.md`. Worth a dedicated follow-up round on the end-to-end map-source question; nothing else queued. |
| `release-next` | **answered, pending a decision** | Round 3 tested the `CYCLE_COOLDOWN_MS` fix (`fix/video-stack` @ `e1c00ec7`, one commit, 495 tests) and found the fix's own trigger is unreachable by the fault mode used to test it: **zero `holding the cycle until it settles` lines appeared in any of R1-R4.** Traced to source — `DROP_MIDDLE_FRAGMENT` drops a `FLAG_MIDDLE` fragment that `VideoFragmentAssembler`'s state machine never notices (no `Anomaly` case fires for a missing middle fragment), so `AapVideo`'s corruption-report path never calls back into `AapTransport`, so `lastWireCorruptionMs` never gets stamped, so the new `WAIT_FOR_QUIET` gate can never engage for the exact fault this whole thread exists to fix. R1's recovery gap was **66.9s**, essentially unchanged from round 2's 63.1s — not because the fix's <10s target is wrong, but because its precondition can't be reached this way. **R3 PASS and R4 PASS** confirm the new code is inert in both directions on a clean stream and on stream-only corruption (zero hold lines either way, as expected). **R2 PASS on its literal conditions but with the same caveat** — needed four setup attempts (a WiFi-association flake, an AA-channel-binding stall, and this app's own renderer-confirmation dialog silently blocking a session at a near-static screen with no log signal saying so) before landing a clean, high-density capture; even then, zero hold lines. **Next: someone decides whether `AapVideo`'s corruption-report path should also cover a mid-run fragment loss that doesn't trip the existing `Anomaly` cases, since as shipped the fix cannot see the fault it was built for.** Do not re-test #852 or the holed-keyframe fix itself (round 2, closed). See `release-next-round3-results.md`. |
| `video-pipeline-stack` | **acted on** | Round 2 validated the decoder-wedge fix: **R0 PASS** (454/454, first ever compile), **R2 PASS** (both faults recovered in ~0.55s via `cycling video focus` → `keyframe reached the codec`, `rendered=` never 0 against round 1's 90+s), **R4 PASS** (control clean, one codec init across 10 corrupted frames, zero starvation lines), **R6 PASS** (10 clean minutes, zero `DELTA_CHANGED`). **R3 INCONCLUSIVE** — 0 faults in 5 min at rate=20, which the brief predicted and which is now fixed at the source: the injector prints its candidate count per fault and a summary every 15s, so a zero-fault run says so during the run. **R5** answered the mode-4 probe: inert, and with the session's only working lever replaced it reproduced round 1's wedge in full (`rendered=0` for 108+s, unrecovered at capture end) — the probe has been removed. The whole five-branch stack has since been compacted into **one branch, `fix/video-pipeline` @ `d43112e2`, four commits** (457 tests), and the five old refs were **deleted** — anything still on one is orphaned and needs a fetch and reset onto the new branch, not a pull. Two tags keep this document's SHAs resolvable: `round2-validated` (`8f0beab1`, the APK measured below) and `stack-pre-compaction` (`b1057bf8`, all sixteen commits). **Superseded — this thread's work now lives on `fix/video-stack` and is tested under the `release-next` thread.** R2's validation was 2 injected faults; the `release-next` round 1 R6 run drew 90 at the same setting and reopened the wedge, so do not quote R2 as proof without its fault count. See `video-pipeline-stack-round2-results.md`. |
| `video-dropped-frame-keyframe` | **queued** (fix already in PR #826) | `video-dropped-frame-keyframe-round7-brief.md` — R1 is a desk check with no rig time; R2 is optional |
| `audio-focus` | **queued** | `audio-focus-round11-brief.md` |
| `discovery-socket-leak` | answered at round 7 | nothing queued; awaiting a PR |
| `video-black-after-background` | closed at round 8 | shipped in PR #826 |
| `link-stall-periodic-scan` | blocked | needs the reporter's own hardware; the rig cannot reproduce it |
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
