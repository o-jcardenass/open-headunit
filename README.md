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
On 2026-10-10 `driver-select-off-phone`, `forget-car-every-connection`, `aa-178-protocol-levers` and `pr-1090-audio-transitions` followed, rows in `archive/threads-table-through-2026-10-10.md`. On 2026-10-13 `wizard-dpi-layout`, `pr-1042-disabled-home-buttons`, `pr-1076-text-keycodes`, `dsam-widget-layout-dpi` and `main-beta5-regression` followed, rows in `archive/threads-table-through-2026-10-13.md`. On 2026-10-15 `call-audio-route`, `pre-350-fixes`, `portrait-maps-touch`, `second-screen-outputs` and `second-screen-rtp-push` followed, rows in `archive/threads-table-through-2026-10-15.md`. On 2026-10-17 `pr-1114-audio-continuity` and `audio-stutter` followed, rows in `archive/threads-table-through-2026-10-17.md`. On 2026-10-21 `pr-1110-wifi-direct-wedge` followed, row in `archive/threads-table-through-2026-10-21.md`. On 2026-10-22 `native-join-scan` and `hus-version-retry` followed (PRs 1130 and 1158 merged), rows in `archive/threads-table-through-2026-10-22.md`.

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
and on 2026-10-10 the four of `driver-select-off-phone`, `forget-car-every-connection`, `aa-178-protocol-levers` and `pr-1090-audio-transitions`, and on 2026-10-13 the three of `wizard-dpi-layout`, `pr-1042-disabled-home-buttons` and `main-beta5-regression`, and on 2026-10-15 the five of `call-audio-route`, `pre-350-fixes`, `portrait-maps-touch`, `second-screen-outputs` and `second-screen-rtp-push`, and on 2026-10-17 the three of `pr-1114-audio-continuity`, `audio-stutter` and `station-scan`, and on 2026-10-21 the one of `pr-1110-wifi-direct-wedge`, and on 2026-10-22 the two of `native-join-scan` and `hus-version-retry`; a thread that runs another round creates its
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

**Changed on 2026-10-15:** §3b (paired rounds), §5 (no rig unit tests unless a brief asks, one build
worktree, batch builds) and §8. `rig-sdlc/` changed with them. Before the next `/rig-round`, type
`Run git fetch fork, then follow rig-sdlc/APPLY-2026-10-15.md as it is on fork/transfer/rig-rounds` once, then
restart Claude Code. It copies the new skills into `.claude/`.
After that, `/rig-round` re-copies them itself when a pull changes them, and asks for the restart.

2. `wifi-direct-named-connect-round1-brief.md` (candidate `6a0089d6` on `fork/feat/wifi-direct-named-connect`, rebased 2026-10-22; no baseline)
3. `forget-car-still-needed-round2-brief.md` (F `048fd16a` on `fork/fix/forget-car-still-needed`, C `e9377ff9` = `main`, both rebuilt; units D-HU and D-POCO; R1 is the point)
4. `usb-native-loop-round1-brief.md` (tags `v.3.4.0` `013b05bc` and `v.3.5.0` `1c1b5b72`; units D-POCO as USB host and D-MOTO; one hand step to cable them; Part B is the point)
5. `fps-cap-round1-brief.md` (candidate `8e031b17` on `fork/feat/overlay-device-fields`; conditional `v.3.4.0` `013b05bc`; units D-POCO as head unit and D-MOTO, D-HU idle; R1 and R3 are the point)
6. `pr-1091-pcm-guidance-round1-brief.md` (C `dcc3c708` on `fork/test/pr-1091`, a contributor audio PR merged onto `main` `b3ef3355`; one build, four settings arms; units D-HU and D-POCO; R2 is the point)
7. `automation-screen-disconnect-round3-brief.md` (C `b5bd8145` on `fork/feat/automation-screen-disconnect`, on `main` `e9377ff9`; units D-POCO, D-MOTO and D-HU for Bluetooth; operator present for HS2 and Stage U; V3, V4k and R11 are the point)
9. `aap-protocol-revision-round1-brief.md` (T `c30da573` on `fork/fix/touch-cancel-release`, U `d811b612` on `fork/chore/aap-unread-fields`, A `f809e6dd` on `fork/feat/update-apk-button`, B `e9377ff9` = `main`; four builds in one worktree; units D-HU, D-POCO and D-SAM, D-MOTO Bluetooth off; R1 is the point)
10. `self-mode-media-session-round3-brief.md` (C `e4f8f6d9` on `fork/fix/929-self-mode-media-session`, on `main` `e9377ff9`, one build, no baseline; units D-HU and D-POCO, D-MOTO Bluetooth off; P1 and H2 are the point)
11. `second-screen-outputs-round3-brief.md` (C `e85e5c10` on `fork/feat/second-screen-outputs`, on `main` `e9377ff9`, one build, no baseline; units D-HU and D-POCO, D-MOTO radios off; a smoke after two rebases; P1 is the point)
12. `zj-bt-unix-socket-round1-brief.md` (C `d594102c` on `fork/feat/zj-bt-unix-socket`, on `main` `94082d41`; one build, no baseline; units D-HU (root) and D-POCO, D-MOTO Bluetooth off; R2 is the point)

## Threads

| Thread | State | Next |
|---|---|---|
| `usb-dongle-regression` | **ROUND 1 DONE, inconclusive** | `usb-dongle-regression-round1-results.md`. Rig dongle never fails on v.3.5.0 (H-B 3/3, P-B 5/5 project), so P-C not run; v.3.4.0 control P-A 0/5. Keycode list untested. Next: cold-start cycles on B and C. |
| `pr-1132-exit-dialog` | round 1 done on C `f17c14ef`: SAM-4 and HP-4 PASS (font scale kept, F 0.986 and 1.006; B loses it, Fb100 0.763), HU/SAM/HP-2 scale PASS, HP-1 Stop INCONCLUSIVE (tap missed row) | `pr-1132-exit-dialog-round1-results.md`. HU-2 130% dialog fills full height. Fix the HP tap mapping and the HU focus read if re-run. |
| `pr-1001-devserver-p2p` | Round 1 done on B `0cbff004`, P `7f5bc6e9`, F `ce5e61cb`: R3 P FAIL (expected, H1 real) and B, F PASS; R1-B FAIL, R1-P INCONCLUSIVE; R5A PASS, R5B INCONCLUSIVE; R4 PASS (OpenHU rename refused, joined as the unit name); R6 FAIL (no ByeBye on the P2P route); R2 PASS both. | `pr-1001-devserver-p2p-round1-results.md`. Review the refused rename and R6 condition 2. |
| `automation-screen-disconnect` | **ROUND 2 DONE, round 3 QUEUED** on C `b5bd8145` | R1, R2, R10, R6, R5, T1, S1, S2 PASS; V3, V4, R7 FAIL: screen-on wakes the panel by wake lock first, so the raise is skipped and the lock screen covers the projection. R1s UNTESTABLE. See `automation-screen-disconnect-round2-results.md`. |
| `wifi-direct-named-connect` | **QUEUED, round 1** | `wifi-direct-named-connect-round1-brief.md`. Candidate `6a0089d6` (rebased 2026-10-22). Does a named or MAC connect request reach the right phone over WiFi Direct. |
| `import-settings-unsaved-changes` | **ROUND 2 DONE** | `import-settings-unsaved-changes-round2-results.md`. C `2ab6f398`: R1 PASS 3/3 (0 dialogs on BACK), R6 PASS 3/3, PC-1 PASS, R3 INCONCLUSIVE (float row off screen after import recreate). Next: redesign R3. |
| `forget-car-still-needed` | Round 1 done: R1 INCONCLUSIVE, R2 R3 PASS, R4 FAIL one rep. Round 2 QUEUED on F `048fd16a` vs C `e9377ff9`. | `forget-car-still-needed-round2-brief.md`. Round 1 detail in `forget-car-still-needed-round1-results.md`. |
| `late-listener-poke` | Round 1 done: R1-R4 PASS, R5 not run (`22392a15`) | `late-listener-poke-round1-results.md`. Poke off, phone redials a held late listener every ~5.3 s, D1 2.2-3.3 s, still dialling at 150 s; 90 s escalation never reached. |
| `emotionbug-batch` | Round 2 done on C `4c5960c7`: PRESENT, BRIDGE, SCAN PASS; FRAMING and LIBUSB FAIL on the letter (U1 unplug read error 1654 ms vs 1 s bound; U3 five write timeouts, recovered). | `emotionbug-batch-round2-results.md`. Round 1 in `emotionbug-batch-round1-results.md`. |
| `pr-1091-pcm-guidance` | **Round 1 queued** on C `dcc3c708`: PCM guidance beside AAC music, and a 48 kHz SPEECH format the phone may pick. | `pr-1091-pcm-guidance-round1-brief.md`. |
| `pr-1119-home-redesign` | R3 on `135131ce`: CD, B5 (finished path), W12a/b, ROW, W8 PASS; B1 and U1 UNTESTABLE (brief order, armed home cannot be dumped); POCO-U3 UNTESTABLE (no route); W10 reverts, ring clear. Reconnect overlay after End session. | `pr-1119-home-redesign-round3-results.md`; `-round2-results.md`, `-round1-results.md`. |
| `usb-native-loop` | **QUEUED, round 1** | `usb-native-loop-round1-brief.md`. Does a stored Native mode turn one USB drop into a reconnect loop on 3.4.0, and does 3.5.0 stop it. |
| `fps-cap` | **QUEUED, round 1** | `fps-cap-round1-brief.md`. What holds the projection at 30 fps on D-POCO with fps-limit 60: Android Auto's frame-rate dump, idle and panned, in the trip setup and in Self Mode. |
| `aap-protocol-revision` | **QUEUED, round 1** | `aap-protocol-revision-round1-brief.md`. A cancelled touch ends off the canvas, never as a held press; four unread AAP fields dropped; the APK download on API 19. |
| `self-mode-media-session` | **QUEUED, round 3** (rounds 1 and 2 archived) | `self-mode-media-session-round3-brief.md`. First hardware read of the Self Mode key routing and the wireless re-arm after the 2026-10-17 rework. |
| `second-screen-outputs` | **QUEUED, round 3** (rounds 1 and 2 archived) | `second-screen-outputs-round3-brief.md`. Smoke after two rebases: an RTP push session ends cleanly by user exit and link loss, then reconnects. |
| `zj-bt-unix-socket` | **QUEUED, round 1** | `zj-bt-unix-socket-round1-brief.md`. Does the Bluetooth route over a Unix socket connect a phone on the head unit. R2 is the point. |
| `pr-1141-cover-keeps-decoder` | **Round 1 done, no FAIL** | `pr-1141-cover-keeps-decoder-round1-results.md`. C keeps the decoder through 6 s covers on TEXTURE and GLES (R1 5/5); SURFACE still stops it by surfaceDestroyed (R4); after a 30 s cover the codec rebuilds within 81 ms (R2). |
| `settings-help-button` | Round 3 on C `e3b2597c` done: R1-TALL PASS (point), R2/R3/R4/R6/R7 PASS, no-Save PASS, R1-T2/T1 INCONCLUSIVE (below fold), R5 info (seed fills box only with orientation 0). | `settings-help-button-round3-results.md` |
| `pr-1146-video-preflight` | Round 1 done on C `90b3d74b`: W1/W2/H1/H2 PASS; R-A, R-B, R-D confirmed (FAIL); W3 UNTESTABLE (session forms before poke); H3 INCONCLUSIVE; S1 FAIL (D-SAM TEXTURE FEW_FRAMES); Stage O not run. | `pr-1146-video-preflight-round1-results.md`. Re-run W3/H3 with a race-free session and Stage O on the moved PR head. |

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
