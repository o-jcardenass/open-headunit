# Transfer branch, not part of the app

This branch carries test briefs and results between the session that writes the code and the agent
that runs it on hardware. Orphan branch: no shared history with `main` or any feature branch, and
nothing here is ever merged.

It is `transfer/rig-rounds`. Until 2026-09-10 it was named after the round it was created for,
`transfer/hotspot-unreadable-config-results-20260807`; links into that name are dead, so cite files
here by path.

**The branch was rewritten on 2026-09-18** to move the captures out to release assets. If your
checkout predates that, or a transfer SHA you hold no longer resolves, read `REPOINT-20260918.md`
once: it carries the reset commands and what changed. Otherwise you do not need it.

## Read this, in this order, and stop there

1. **`TESTING-TEMPLATE.md`**, the standing method. Read it once: capture rules, how to read and
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
(1045 to 1047, 1064 to 1067, the stack round) and `settings-defaults` on 2026-10-09, then the four of `build-speed`, `wizard-display-and-vehicle` and `bluetooth-audio-disabled-usb-connect` the same day, then `usb-reattach`'s and `samsung-driver-native`'s; a thread that runs another round creates its
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
in the commit that pushes the results.

- `station-scan-round1-brief.md` (D-POCO, D-HU and D-SAM as head units, D-MOTO as phone; measurement, no candidate)
- `call-audio-route-round1-brief.md` (measurement on `main` `28d73e2f`, D-HU and D-MOTO with the KY Pro intercom; hand-rung calls; I-car against I-moto is the point)
- `pr-1076-text-keycodes-round1-brief.md` (D-HU with D-POCO, A/B; R1 is the point)
- `pr-1001-devserver-p2p-round1-brief.md` (B `0cbff004`, P `7f5bc6e9` = `fork/review/pr-1001`, F `ce5e61cb` = `fork/review/pr-1001-loopback-fix`; stage 1 D-POCO as head unit in Self Mode, stage 2 D-HU with D-POCO; R3 is the point; hand steps HS1 to HS4; renames D-HU's WiFi Direct device to OpenHU)
- `second-screen-outputs-round2-brief.md` (candidate `4897c121` on `fork/feat/second-screen-outputs`; D-HU, D-POCO, D-SAM; R2 is the point)

## Threads

| Thread | State | Next |
|---|---|---|
| `call-audio-route` | Round 1 queued | `call-audio-route-round1-brief.md`. Measures whether Motorcycle plus head unit microphone off moves a phone call's audio, with and without an intercom on the phone, and where the assistant opens SCO. No candidate. |
| `pr-1001-devserver-p2p` | Round 1 queued | `pr-1001-devserver-p2p-round1-brief.md`. Automatic WiFi Direct for Headunit Server mode. R3: a Self Mode session must survive station WiFi off beside an armed WiFi Direct launcher (B and F keep it, P is expected to close it). |
| `pr-1076-text-keycodes` | Round 1 queued | `pr-1076-text-keycodes-round1-brief.md`. Letter keycodes advertised to the phone. R1: does a Maps search open the head unit or phone keyboard, B vs M; R2 typed keys; R3 a typed 'n' with a night key mapped. |
| `pr-1042-disabled-home-buttons` | Round 1 reported | `pr-1042-disabled-home-buttons-round1-results.md`. `3404e4e4`: R4 FAIL as predicted (only WiFi ticked, session live in picture-in-picture: Self Mode button reads enabled=no, press does nothing); R1, R2, R3, R5 PASS; R2 shows the legacy single choice greys Self Mode and USB. |
| `station-scan` | **QUEUED, round 1** | `station-scan-round1-brief.md` on probe `b887adeb`. Two reporter tablets stutter on every one of their unjoined station's scans; D-HU does not (link-stall-periodic-scan round 5). Measures whether a `LocalOnlyHotspot` takes the station down and carries a session. |
| `dsam-widget-layout-dpi` | Round 1 reported, no brief | `dsam-widget-layout-dpi-round1-results.md`. D-SAM shows the portrait widget layout from the announced density, not orientation: 1280x720 landscape at 175 dpi (about 658 dp tall) shows the weather widget, at 198 it does not. Auto orientation also pins a portrait start (R1 FAIL). dpi left at 198. |
| `second-screen-outputs` | Round 2 queued | `second-screen-outputs-round2-brief.md`. Candidate `4897c121` fixes the round 1 FAIL-B (R2). Reruns R1, R2, R2C, R3 and R4; D-SAM views R2. |
| `forget-car-every-connection` | **Round 1 done: H3 PASS; H1, H2, H4, P1 FAIL** | `forget-car-every-connection-round1-results.md`. Moved IP withheld and warned once (H3). A read of a surviving group is graded as a create (IP grade runs before the read flag is set), so one read proves the IP and clears the banner. |
| `main-beta5-regression` | Round 1 done on `ec9d9c33`: R0, W2, W4, U1, H1 PASS; W1 FAIL (6 fps static map, no overlay in dumps); W3 UNTESTABLE (stale taps) | `main-beta5-regression-round1-results.md`. Fix brief errata (L-SDR string, overlay, taps, no `nc -z`) before any round 2. |

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
