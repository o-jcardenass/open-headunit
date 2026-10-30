# Transfer branch, not part of the app

This branch carries test briefs and results between the session that writes the code and the agent
that runs it on hardware. Orphan branch: no shared history with `main` or any feature branch, and
nothing here is ever merged.

It is `transfer/rig-rounds`. Until 2026-09-10 it was named after the round it was created for,
`transfer/hotspot-unreadable-config-results-20260807`; links into that name are dead, so cite files
here by path.

**The branch was squashed again on 2026-10-22** (and on 2026-10-17 before it) into one baseline commit, every file byte-identical. A checkout
from before that resets once with `git fetch fork && git reset --hard fork/transfer/rig-rounds`, after any
unpushed results are pushed or copied out.
**The branch was squashed on 2026-10-15** to a base commit plus one commit per queued brief, and the old history
was deleted. `rig-sdlc/APPLY-2026-10-15.md` resets a checkout once.
**It was also rewritten on 2026-09-18** to move the captures out to release assets. If your
checkout predates that, or a transfer SHA you hold no longer resolves, read `archive/REPOINT-20260918.md`
once: it carries the reset commands and what changed. Otherwise you do not need it.

## Read this, in this order, and stop there

1. **`TESTING-TEMPLATE.md`**, the standing method (`/rig-round` reads it without §7b and §8): capture rules, how to read and
   write `settings.xml`, the app's automation surface, the clean-run protocol, install discipline,
   the four verdicts, and the format results come back in. The rig quirks are in `rig-quirks/`, indexed by
   §7a: read the files for your units and your runs' areas, not all of them.
2. **`## Queue` below, then your own row in `## Threads`**, found with
   `grep -F '| `<thread>`' README.md`. The row names the one brief to read; it names everything
   else it needs.

**Do not read the other threads' briefs and results.** They are a different fault on a different
build, and several were superseded by later rounds in their own thread. Reading them costs context
and, worse, primes you for a signature that does not apply to the run you are doing. If a brief
needs a prior round, it cites it by filename. Fetch that one, not its neighbours.

The same goes for `archive/`. It is the historical record, not orientation.

**A brief may cite a file that is not at the root.** Closed threads' briefs, results and findings
moved to `archive/rounds/` on 2026-10-08 under their own filenames. Look there by the same name.
Their README rows are in `archive/threads-table-through-2026-10-08.md`. On 2026-10-09 the threads of the contributor's stacked PRs (1045, 1047, 1064, 1065, 1067 and the stack round) and `settings-defaults` moved the same way, then `build-speed`, `wizard-display-and-vehicle` and `bluetooth-audio-disabled-usb-connect`, then `usb-reattach` and `samsung-driver-native`, rows in `archive/threads-table-through-2026-10-09.md`.
On 2026-10-10 `driver-select-off-phone`, `forget-car-every-connection`, `aa-178-protocol-levers` and `pr-1090-audio-transitions` followed, rows in `archive/threads-table-through-2026-10-10.md`. On 2026-10-13 `wizard-dpi-layout`, `pr-1042-disabled-home-buttons`, `pr-1076-text-keycodes`, `dsam-widget-layout-dpi` and `main-beta5-regression` followed, rows in `archive/threads-table-through-2026-10-13.md`. On 2026-10-15 `call-audio-route`, `pre-350-fixes`, `portrait-maps-touch`, `second-screen-outputs` and `second-screen-rtp-push` followed, rows in `archive/threads-table-through-2026-10-15.md`. On 2026-10-17 `pr-1114-audio-continuity` and `audio-stutter` followed, rows in `archive/threads-table-through-2026-10-17.md`. On 2026-10-21 `pr-1110-wifi-direct-wedge` followed, row in `archive/threads-table-through-2026-10-21.md`. On 2026-10-22 `native-join-scan` and `hus-version-retry` followed (PRs 1130 and 1158 merged), rows in `archive/threads-table-through-2026-10-22.md`. On 2026-10-29 ten threads whose PRs merged followed, among them `automatic-dpi-panel`, `home-widgets-opacity` and `pr-1119-home-redesign`, rows in `archive/threads-table-through-2026-10-29.md`.

**Captures are not on this branch.** It is markdown only. Screenshots, logs and traces go to the fork
as a release asset, **one release per thread and one asset per round**: the release is
`rig-evidence-<thread>`, created on that thread's first round, and every later round uploads
`<thread>-round<N>-captures.zip` to the same one. The results file cites the release, the asset
filename and a sha256 rather than a path, and `TESTING-TEMPLATE.md` §7 gives the commands and the
retention rule for a thread that closes. Do not commit a capture here, the commit guard refuses one.

**There is exactly one archive release, `rig-evidence-legacy`, and every closed thread's captures are
in it.** A `rig-evidence-*` release that a results file cites no longer exists: `native-aa-wireless`
was swept in on 2026-09-21 along with `hands-free-wake-verdict`, `rotation-geometry` and
`wpp-endpoint-depoison`, and the per-round releases that predate the one-release-per-thread rule went
the same way. `adaptive-audio`, `check-for-updates`, `hold-aa-rfcomm` and
`native-aa-dsam-wifi-unavailable` followed on 2026-09-30, and the ten releases of the stacked PRs
(1045 to 1047, 1064 to 1067, the stack round) and `settings-defaults` on 2026-10-09, then the four of `build-speed`, `wizard-display-and-vehicle` and `bluetooth-audio-disabled-usb-connect` the same day, then `usb-reattach`'s and `samsung-driver-native`'s,
and on 2026-10-10 the four of `driver-select-off-phone`, `forget-car-every-connection`, `aa-178-protocol-levers` and `pr-1090-audio-transitions`, and on 2026-10-13 the three of `wizard-dpi-layout`, `pr-1042-disabled-home-buttons` and `main-beta5-regression`, and on 2026-10-15 the five of `call-audio-route`, `pre-350-fixes`, `portrait-maps-touch`, `second-screen-outputs` and `second-screen-rtp-push`, and on 2026-10-17 the three of `pr-1114-audio-continuity`, `audio-stutter` and `station-scan`, and on 2026-10-21 the one of `pr-1110-wifi-direct-wedge`, and on 2026-10-22 the two of `native-join-scan` and `hus-version-retry`, and on 2026-10-29 the seven of `automatic-dpi-panel`, `home-widgets-opacity`, `import-settings-unsaved-changes`, `pr-1119-home-redesign`, `pr-1132-exit-dialog`, `pr-1141-cover-keeps-decoder` and `pr-1146-video-preflight`; a thread that runs another round creates its
release again, as on its first round. Asset filenames are unchanged, so fetch by filename rather than by the tag a results
file names:

```bash
gh release download rig-evidence-legacy --repo o-jcardenass/open-headunit -p '<asset>.zip'
```

`archive/evidence-manifest-20260921.tsv` maps each old release tag to the asset now holding it, with
its size and sha256. Every hash a results file quoted was verified against its asset before the sweep
and again after it, so a cited sha256 still identifies its capture.

An `evidence/<round>/...` path in a results file written before 2026-09-18 is a historical path. Look
it up in `archive/evidence-manifest-20260918.tsv`, which carries every archived capture's size,
sha256 and the zip holding it, and fetch that zip from `rig-evidence-legacy` by the same filename.

Three rules from the template are worth repeating, because breaking any of them invalidates a round:

- **Use the rig's existing scripts.** `rig-toolkit/` already has `build_hur.sh`,
  `run_unit_tests.sh` and others, listed in its `TOOLS.md`. Read that at the start of every round, use what
  fits, and only add a script when nothing does (`TESTING-TEMPLATE.md` §5 says where it goes).
- **Settings are changed in `shared_prefs/settings.xml` with the app stopped**, never through the
  UI, and the settings list is never scrolled with adb.
- **Run the whole round unattended.** Moving between runs, restoring state and deciding that a gated
  run is INCONCLUSIVE are all yours. Escalate only for a failed build gate, a genuinely ambiguous
  fork the brief did not cover, something destructive, or a broken rig.

## Queue

A brief with no results file of the same name. Add a line here when a brief is pushed; delete it
in the commit that pushes the results. Run them in the order listed: fixes first, then features. A line
that holds two briefs is a pair: run them at the same time, per `TESTING-TEMPLATE.md` §3b.

**Changed on 2026-10-29:** every brief below carries a `rig-requires` block and runs as one
`rig_session.py` process (`rig-sdlc/skills/rig-round/SKILL.md` step 4). Set the session order with
`rig-lib/rig_requires.py order --live`, not by the numbers here.

**Changed on 2026-10-15:** §3b (paired rounds), §5 (no rig unit tests unless a brief asks, one build
worktree, batch builds) and §8. `rig-sdlc/` changed with them. Before the next `/rig-round`, type
`Run git fetch fork, then follow rig-sdlc/APPLY-2026-10-15.md as it is on fork/transfer/rig-rounds` once, then
restart Claude Code. It copies the new skills into `.claude/`.
After that, `/rig-round` re-copies them itself when a pull changes them, and asks for the restart.

3. `forget-car-still-needed-round2-brief.md` (F `6c23c264` on `fork/fix/forget-car-still-needed`, C `947b3ae9` = `main`, both rebuilt; units D-HU and D-POCO; R1 is the point)
4. `usb-native-loop-round2-brief.md` (`main` `f9a72d480` and tag `v.3.4.0` `013b05bc`; units D-POCO as USB host and D-MOTO, D-SAM optional for K2, D-HU Bluetooth only; replaces round 1, which is not run; R4-G and K1 are the point)
5. `fps-cap-round1-brief.md` (candidate `main` `947b3ae9`, fetched from origin, PR #1139 merged; conditional `v.3.4.0` `013b05bc`; units D-POCO as head unit and D-MOTO, D-HU idle; R1 and R3 are the point)
6. `pr-1091-pcm-guidance-round1-brief.md` (C `dcc3c708` on `fork/test/pr-1091`, a contributor audio PR merged onto `main` `b3ef3355`; one build, four settings arms; units D-HU and D-POCO; R2 is the point)
10. `self-mode-media-session-round3-brief.md` (C `6d4de0d0` on `fork/fix/929-self-mode-media-session`, on `main` `947b3ae9`, one build, no baseline; units D-HU and D-POCO, D-MOTO Bluetooth off; P1 and H2 are the point)
13. `main-smoke-round1-brief.md` (one arm, `main` `6ff42d9a`, one build; trimmed on 2026-10-30 to M5, M2 and M7, because `rig-lib/main_smoke.py` runs the rest; units D-HP, D-POCO, D-MOTO and the USB dongle, D-HU and D-SAM for prep and close; M7 is the point)
16. `dsam-surfaceview-crop-round1-brief.md` (one arm, `main` `6ff42d9a`, one build, rig-lib `0f6b5a7a1`; units D-SAM as head unit and D-POCO, D-HU Bluetooth off, D-MOTO radios off; S1 is the point)

## Threads

| Thread | State | Next |
|---|---|---|
| `usb-dongle-regression` | **ROUND 1 DONE, inconclusive** | `usb-dongle-regression-round1-results.md`. Rig dongle never fails on v.3.5.0 (H-B 3/3, P-B 5/5 project), so P-C not run; v.3.4.0 control P-A 0/5. Keycode list untested. Next: cold-start cycles on B and C. |
| `pr-1001-devserver-p2p` | Round 1 done on B `0cbff004`, P `7f5bc6e9`, F `ce5e61cb`: R3 P FAIL (expected, H1 real) and B, F PASS; R1-B FAIL, R1-P INCONCLUSIVE; R5A PASS, R5B INCONCLUSIVE; R4 PASS (OpenHU rename refused, joined as the unit name); R6 FAIL (no ByeBye on the P2P route); R2 PASS both. | `pr-1001-devserver-p2p-round1-results.md`. Review the refused rename and R6 condition 2. |
| `automation-screen-disconnect` | **ROUND 5 DONE** on C `512343ca`: R0, P, Q, N1, N2 PASS | `automation-screen-disconnect-round5-results.md`. |
| `forget-car-still-needed` | Round 1 done: R1 INCONCLUSIVE, R2 R3 PASS, R4 FAIL one rep. Round 2 QUEUED on F `6c23c264` vs C `947b3ae9`. | `forget-car-still-needed-round2-brief.md`. Round 1 detail in `forget-car-still-needed-round1-results.md`. |
| `late-listener-poke` | Round 1 done: R1-R4 PASS, R5 not run (`22392a15`) | `late-listener-poke-round1-results.md`. Poke off, phone redials a held late listener every ~5.3 s, D1 2.2-3.3 s, still dialling at 150 s; 90 s escalation never reached. |
| `emotionbug-batch` | Round 2 done on C `4c5960c7`: PRESENT, BRIDGE, SCAN PASS; FRAMING and LIBUSB FAIL on the letter (U1 unplug read error 1654 ms vs 1 s bound; U3 five write timeouts, recovered). | `emotionbug-batch-round2-results.md`. Round 1 in `emotionbug-batch-round1-results.md`. |
| `pr-1091-pcm-guidance` | **Round 1 queued** on C `dcc3c708`: PCM guidance beside AAC music, and a 48 kHz SPEECH format the phone may pick. | `pr-1091-pcm-guidance-round1-brief.md`. |
| `usb-native-loop` | **QUEUED, round 2** | `usb-native-loop-round2-brief.md` (round 1 never ran). Does Native mode wake a phone still plugged in after the 60 s USB budget, and does a failed handshake close the app when "close the app on disconnect" is on. |
| `fps-cap` | **QUEUED, round 1** | `fps-cap-round1-brief.md`. What holds the projection at 30 fps on D-POCO with fps-limit 60: Android Auto's frame-rate dump, idle and panned, in the trip setup and in Self Mode. |
| `aap-protocol-revision` | Round 2 (R6 only): pre and cleanup PASS, R6-HU and R6-SAM INCONCLUSIVE (row never matched; Settings dump on D-SAM holds the row, D-HU stayed on the launcher). Round 1: R3 and R5 PASS, R1 and R4 INCONCLUSIVE, R2 UNTESTABLE | `aap-protocol-revision-round2-results.md`. Fix the R6 wait loop before another round. |
| `self-mode-media-session` | **QUEUED, round 3** (rounds 1 and 2 archived) | `self-mode-media-session-round3-brief.md`. First hardware read of the Self Mode key routing and the wireless re-arm after the 2026-10-17 rework. |
| `second-screen-outputs` | Round 3 on C `2812a70e`: R0, O1, P1 (47/47), A1, cleanup all PASS | `second-screen-outputs-round3-results.md`. |
| `second-screen-rtp-push` | Round 4 on C `8d4af793`: R0, S12 (point), S3, S4 PASS, S2 FAIL (first frame 25.60 s, limit 20), S5 FAIL (rendered_ok demands exactly 30 windows; S3 and S4 had 29, all rendered) | `second-screen-rtp-push-round4-results.md`. |
| `zj-bt-unix-socket` | **ROUND 1 DONE** on C `390b6931`: all PASS, R1 on the fourth attempt (brief hang) | `zj-bt-unix-socket-round1-results.md`. |
| `settings-help-button` | Round 4 on `1c25716f` done: R1 PASS (point), R2-R6, S1, S2 PASS, R7 INCONCLUSIVE (D-HU no rotation). Rail rows spacing noted. | `settings-help-button-round4-results.md`, `settings-help-button-round4-brief.md` |
| `native-bt-broker-esp32` | Round 3 on C `f1bed4ee`: K PASS after regrade (conditions 6 to 14 cannot fail; phone still never dialled), S FAIL (BrokerSettingsFragment lateinit scanner crash on D-SAM API 19), cleanup PASS | `native-bt-broker-esp32-round3-results.md`. |
| `pr-merge-train` | Rounds 1 to 4 run on `fork/test/pr-merge-train`: R1 PASS, R2 X1 FAIL, R3 PASS; round 4 SB/SA/HB/HA all PASS, A no worse on D-SAM and better on D-HP (Headunit Server). | `pr-merge-train-round4-results.md`. Briefs need `narrow-band-profile-cap=false` for PCM on 2.4 GHz units. |
| `pr-train-20261029` | Round 2 stage W: setup, WB, W2 PASS; W1 regraded INCONCLUSIVE by the host (Type 3 went out 3 times; D-SAM's station rejoined beside the new group). D-SAM projection cropped in a corner during W1 | `pr-train-20261029-round2-results.md`. W1 runs again in `pr-train-20261030`. The crop moved to `dsam-surfaceview-crop`. |
| `pr-train-20261030` | Round 1 done, 7 of 7 runs PASS on T `6d038cc5`: W1 (D-SAM, station off) and D1 (D-HU, session ended 6 s after the cut) both PASS | `pr-train-20261030-round1-results.md`. Nothing open. |
| `main-smoke` | Round 1 queued, trimmed to the wired runs M5, M2 and M7. `rig-lib/main_smoke.py` runs the wireless runs and M6 at will. | `main-smoke-round1-brief.md`; `python3 -I rig-lib/main_smoke.py`. |
| `dsam-surfaceview-crop` | Round 1 queued: does SurfaceView crop the projection on D-SAM on `main`, against TextureView, 3 sessions per view mode | Run round 1 (`dsam-surfaceview-crop-round1-brief.md`). |
| `bt-helper` | Round 4 (`bt-helper-round4-results.md`): setup, L, CPS and restore PASS. Forget and pair through the API on D-POCO and D-SAM, 1 tap per pair on D-SAM's OK button, 2 cycles, 0 stops. D-POCO and D-SAM left bonded. | Thread done; no further round queued. |
| `usb-dialog` | Round 8 on `15a13bff` done: R1/R1-on FAIL (fault ends session as read failure, no desync line; recovery 13 s, 36 s with a permission ask), Q3/Q4-ptp/R2/R4 PASS, Q1/Q2/R3 INCONCLUSIVE, Q4-midi/Q5 battery; phone mode was PTP not File transfer | `usb-dialog-round8-results.md` |
| `pico-usb-stand-in` | Round 1 done, R0-R4 PASS: firmware builds, all 5 identities enumerate; console is /dev/ttyACM1, pass --port | `pico-usb-stand-in-round1-results.md`; firmware in `tools/pico-usb-stand-in/` |

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
