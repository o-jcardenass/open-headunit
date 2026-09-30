---
name: rig-round
description: Run a queued round brief on the rig. The host runs each run through the Haiku rig-executor in the foreground, the rig-round workflow grades and drafts the results, and the host commits and pushes after the operator approves. Use when the operator types /rig-round.
disable-model-invocation: true
---

# /rig-round

Usage: `/rig-round <thread> <N> [hand <runId> | approve]`. Read `TESTING-TEMPLATE.md` and this
worktree's `CLAUDE.md` first, as always; this skill does not replace them. Rule 4 holds: **no
background agent drives a device.** The host runs every `rig-executor` in the foreground, one at a
time. The `rig-round` workflow never touches a device; it only plans, grades and drafts.

Shell prelude for every command: `source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/rig_devices.sh`.

1. **Before launching.** Note the branch you came from. `git fetch fork`, check out and
   fast-forward `transfer/rig-rounds`, read `README.md`'s `## Queue` and the thread's row only,
   confirm `<thread>-round<N>-brief.md` exists with no results file. Inventory `hur-wifi-test-scripts/`.
   `rig_check_devices` for the roles the brief names, `ps aux | grep logcat` for strays, and
   `flock -n /tmp/ohu-rig.lock true` to confirm the rig is free.
2. **Plan.** `Workflow({name: "rig-round", args: {mode: "plan", thread, round: N, brief, worktree}})`
   returns the run list, stop rule, build, Setup notes and `needsMarkers`.
3. **Prepare** (one foreground `rig-executor`, evidence to `prepare.json`): `git worktree add` a
   scratch worktree of the candidate SHA (never build in this worktree), `HUR_DIR=<it> build_hur.sh`
   then `run_unit_tests.sh`, `adb -s <serial> install -r -d` per role, `apk_identity.sh`, back up
   `settings.xml`. A failed gate is `ESCALATE`.
   **Markers.** If `needsMarkers`, check the candidate: `grep -n -A10 'CONFIGURING = setOf'
   <worktree>/app/src/main/java/com/andrerinas/openheadunit/automation/AutomationCommandPolicy.kt`.
   If `ACTION_LOG_MARKER` is listed, the candidate refuses markers until `allow-external-configuration`
   is `true`: write it in `settings.xml` with the app stopped (`set_hu_prefs.sh`, or
   `set_hu_settings_runas.py` when not rooted), never through the UI, and restore the baseline after
   the round. Either way, say which it was in Setup notes.
4. **Each run, in order.** Skip a run whose `/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/evidence/<thread>-round<N>/<id>.json` exists
   (a round may span a restart). `untestable` runs are recorded, not run. A hand step: tell the
   operator, wait for `/rig-round <thread> <N> hand <runId>`. Otherwise run `rig-executor` in the
   foreground with the run's steps; no block back is `STOPPED`, so check the rig yourself. Then
   `Workflow` with `mode: "grade"`, `run: {id, conditions}`, `block`. Act on the result: `escalate`
   means stop and report, `stopRound` ends the loop.
5. **Report.** `Workflow` with `mode: "report"`, `graded`, `setupNotes`. The draft lands in
   `/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/evidence/<thread>-round<N>/`. Re-run one `grep -c` per run yourself against the evidence
   (rig CLAUDE.md rule 3), show the operator the verdicts and the draft, and wait for `approve`.
6. **On `approve`** (host only): zip the evidence and upload it as release asset
   `rig-evidence-<thread>-round<N>` with the sha256 in the results file, copy the results file next
   to its brief, `git add` it and `README.md` by name, commit (`<thread>: round <N> results - <short
   outcome>`, no issue numbers, no trailers), push to `fork`, restore the settings baseline, kill any
   logcat by pid, then return to the branch you came from (`git branch --show-current` first).

Device serials, the lock and the marker verb live in `/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/rig_devices.sh`.
