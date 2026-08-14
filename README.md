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
| `video-dropped-frame-keyframe` | **queued** (fix already in PR #826) | `video-dropped-frame-keyframe-round7-brief.md` — R1 is a desk check with no rig time; R2 is optional |
| `audio-focus` | **queued** | `audio-focus-round11-brief.md` |
| `discovery-socket-leak` | answered at round 7 | nothing queued; awaiting a PR |
| `video-black-after-background` | closed at round 8 | shipped in PR #826 |
| `link-stall-periodic-scan` | blocked | needs the reporter's own hardware; the rig cannot reproduce it |
| `media-key-routing` | closed | merged upstream |
| `external-bt-zbt` / `zlink-wpp-channel` | answered | teardown round done |
| `qf001-firmware-teardown` | answered | — |
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
