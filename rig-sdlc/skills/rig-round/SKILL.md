---
name: rig-round
description: Run a queued round brief on the rig. The host reads the brief, runs each run through the Haiku rig-executor in the foreground, grades each run itself, drafts the results, and commits and pushes after the operator approves. Use when the operator types /rig-round.
disable-model-invocation: true
---

# /rig-round

Usage: `/rig-round <thread> <N> [hand <runId> | approve]`. This worktree's `CLAUDE.md` is already
loaded; this skill does not replace it or `TESTING-TEMPLATE.md`, it only says which parts to read. Rule 4 holds: **no
background agent drives a device.** The host runs every `rig-executor` in the foreground, one at a
time, and does all the judgement itself. There is no workflow and no grader agent: the host has
already read the brief and the template, so grading in its own context is the cheapest place to do it.

Shell prelude for every command: `source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/rig_devices.sh`.
`EV=/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/evidence/<thread>-round<N>`.

1. **Before launching.** Note the branch you came from. `git fetch fork`, check out and
   fast-forward `transfer/rig-rounds`, read `README.md`'s `## Queue` and the thread's row only,
   confirm `<thread>-round<N>-brief.md` exists with no results file.
   **Read only what the round needs**, once, and never the whole template or the whole quirk list:
   - the template without §7b and §8: `sed '/^## 7b\./,/^## 8\./{/^## 8\./!d}; /^## 8\./,$d' TESTING-TEMPLATE.md`
     (§7b only when a phone is the USB host; §8 is for whoever writes briefs);
   - from §7a's index: `rig-quirks/topics/tooling.md`, `rig-quirks/units/<unit>.md` for every unit the
     brief names, and the topic files for the areas its runs touch
     (`grep -il <term> rig-quirks/topics/*.md` when unsure). Say in Setup notes which quirk files you read.
   Inventory `hur-wifi-test-scripts/`, then `rig_check_devices` for the roles the brief names, `ps aux | grep logcat` for strays, and
   `flock -n /tmp/ohu-rig.lock true` to confirm the rig is free.
2. **Plan (host).** Read the brief once and write down the run list in the brief's order: id, title,
   whether it is the point of the round, whether it has a hand step, the stop rule, and for each run
   the exact commands, markers and greps the executor needs (copied verbatim, never paraphrased).
   Flag anything §8 says a brief must not do (a "tap", a decisive string that is not verbatim in the
   build, a run §7a says cannot work) as a Setup note, and mark that run UNTESTABLE up front rather
   than improvising. Note `needsMarkers` if any run uses `ACTION_LOG_MARKER`.
3. **Prepare** (one foreground `rig-executor`, evidence to `$EV/prepare.json`): `git worktree add` a
   scratch worktree of the candidate SHA (never build in this worktree), `HUR_DIR=<it> build_hur.sh`
   then `run_unit_tests.sh`, `adb -s <serial> install -r -d` per role, `apk_identity.sh`, back up
   `settings.xml`. A failed gate is a §3a escalation: stop and report.
   **Markers.** If `needsMarkers`, check the candidate: `grep -n -A10 'CONFIGURING = setOf'
   <worktree>/app/src/main/java/com/andrerinas/openheadunit/automation/AutomationCommandPolicy.kt`.
   If `ACTION_LOG_MARKER` is listed, the candidate refuses markers until `allow-external-configuration`
   is `true`: write it in `settings.xml` with the app stopped (`set_hu_prefs.sh`, or
   `set_hu_settings_runas.py` when not rooted), never through the UI, and restore the baseline after
   the round. Either way, say which it was in Setup notes.
4. **Each run, in order.** Skip a run whose `$EV/<id>.json` and `$EV/<id>.grade.json` both exist (a
   round may span a restart; read the grade back instead). `untestable` runs are recorded, not run.
   A hand step: tell the operator, wait for `/rig-round <thread> <N> hand <runId>`. Otherwise run
   `rig-executor` in the foreground with the run's steps; no block back means stop and check the rig
   yourself. Then **grade it yourself**:
   - Re-run **one** `grep -c` inside the same marker window and compare it with the block's count.
     On a mismatch, run `rig-executor` once more as a grep-only pass (no device commands) over the
     same capture; if the counts still disagree, grade from your own greps and say so in Setup notes.
   - Grade exactly one of PASS, FAIL, INCONCLUSIVE, UNTESTABLE per §6, from comparisons over counts
     and timestamps, never from reading the capture whole.
   - Write `{id, verdict, reason}` to `$EV/<id>.grade.json`.
   - Stop the loop when the stop rule is met. If this result changes what the remaining runs should
     be in a way the brief did not decide, that is §3a: stop and report the question.
5. **Report (host).** Draft `$EV/<thread>-round<N>-results.md` exactly per §7: header block,
   `## Setup notes` (every deviation, wrong key, unmatched string, script used), one `## R<id>` per
   run with the bold verdict alone on its line, measurements as numbers, a quoted decisive line with
   its timestamp, and `## Anything the brief did not ask about`. Include runs graded before a
   restart from their `.grade.json`. Show the operator the verdicts and the draft, and wait for `approve`.
6. **On `approve`** (host only): zip the evidence and upload it as release asset
   `rig-evidence-<thread>-round<N>` with the sha256 in the results file, copy the results file next
   to its brief, update the thread's README row (router style, name the results file), `git add` the
   results file and `README.md` by name, commit (`<thread>: round <N> results - <short outcome>`, no
   issue numbers, no trailers), push to `fork`, restore the settings baseline, kill any logcat by
   pid, then return to the branch you came from (`git branch --show-current` first).

House rules: no issue or discussion numbers anywhere on this branch; no em dashes; measurements not
adjectives; a FAIL keeps its full capture.

Device serials, the lock and the marker verb live in `/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/rig_devices.sh`.
