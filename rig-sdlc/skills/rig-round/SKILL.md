---
name: rig-round
description: DRAFT. Run a queued round brief on the rig through the rig-round workflow, then commit and push the results after the operator approves. Use when the operator types /rig-round.
disable-model-invocation: true
---

# /rig-round

Usage: `/rig-round <thread> <N> [hand <runId> | approve]`. Invoking it is the operator's opt-in to
run the `rig-round` workflow. Read `TESTING-TEMPLATE.md` and this worktree's `CLAUDE.md` first, as
always; this skill does not replace them.

1. **Before launching.** `git fetch fork`, fast-forward this worktree, read `README.md`'s `## Queue`
   and the thread's row only, confirm `<thread>-round<N>-brief.md` exists and has no results file.
   Inventory `hur-wifi-test-scripts/`.
2. **Launch** `Workflow({name: "rig-round", args})` with
   `{thread, round: N, brief: "<file>", worktree: "<abs path>", done: [ids that have
   evidence/<thread>-round<N>/<id>.json, plus "prepare" if prepare.json exists], handDone: [...]}`.
3. **On the result:**
   - `AWAITING_HAND`: tell the operator the hand step, wait for `/rig-round <thread> <N> hand <runId>`,
     then relaunch with it in `handDone`.
   - `ESCALATE` or `STOPPED`: the §3a cases. Check the rig state yourself, report, and wait.
   - `AWAITING_APPROVAL`: re-run one `grep -c` per run yourself against the evidence (rig CLAUDE.md
     rule 3), show the operator the verdicts and the results file, and wait for `approve`.
4. **On `approve`** (host only, never an agent): upload the evidence zip to `rig-evidence-<thread>`
   with the sha256 in the results file, `git add` the results file and `README.md` by name, commit
   (`<thread>: round <N> results - <short outcome>`, no issue numbers, no trailers), push to `fork`.

TODO(tester): device serials per role, where the rig lock lives if not `/tmp/ohu-rig.lock`, and
whether a round may span a session restart (the workflow skips finished runs by their evidence file).
