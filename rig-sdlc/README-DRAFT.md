# Rig SDLC draft (for the tester to refine)

**Status: DRAFT, not installed on the rig yet. Refine it with the planning side before adopting it.** It is the rig half of the coding
side's `/ohu-sdlc` pipeline: the coding side publishes `<thread>-round<N>-brief.md` after a human
approves it, the rig runs it with `/rig-round`, and the coding side's `ohu-rig-grade` agent picks up
`<thread>-round<N>-results.md` when it lands. Nothing about the brief/results contract changes.

## What is here

| File | Role | Model |
|---|---|---|
| `agents/rig-executor.md` | runs one run's commands under the rig lock, returns the JSON block the rig `CLAUDE.md` defines | Haiku |
| `agents/rig-grader.md` | reads the brief into a run list, grades each run after its own `grep -c`, drafts the results file | Sonnet |
| `workflows/rig-round.workflow.md` | the round: prepare, runs strictly one at a time, report; stops on a dead agent or a §3a case | (script) |
| `skills/rig-round/SKILL.md` | `/rig-round <thread> <N>`: launches it, handles hand steps, does the evidence upload, commit and push itself after `approve` | host |
| `skills/rig-diagnose/SKILL.md` | what to do when a run surprises you mid-round (from Matt Pocock's `diagnosing-bugs`) | host |

Install on the rig, from the root of this worktree (`.claude/` stays untracked here; add it to
`.git/info/exclude` if it is not already, so it is never committed to this branch):

```bash
mkdir -p .claude/agents .claude/skills .claude/workflows
cp rig-sdlc/agents/*.md .claude/agents/
cp -r rig-sdlc/skills/rig-round rig-sdlc/skills/rig-diagnose .claude/skills/
sed -n '/^```js$/,/^```$/p' rig-sdlc/workflows/rig-round.workflow.md | sed '1d;$d' > .claude/workflows/rig-round.js
```

Restart Claude Code afterwards: agents and workflows added mid-session do not register until then.

## Where it bends the current rig rules, to decide together

1. **Background.** A workflow runs in the background, and rig rule 4 says no background agent drives
   a device, after one died silently and left the rig unsupervised for ~68 minutes. The draft's
   answer: runs are strictly sequential, a dead agent stops the round at once with `STOPPED`, the
   workflow's completion notifies the host, and every run's JSON is on disk so a relaunch skips it.
   Is that enough, or should execution stay in the host with only grading in a workflow?
2. **Who reads the brief and grades.** The rig `CLAUDE.md` gives both to the Sonnet host. Here a
   Sonnet `rig-grader` agent does them (same model, fresh context), and the host re-runs one
   `grep -c` per run before approval. Acceptable?
3. **Commit and push stay in the host**, after the operator's `approve`, exactly as today.
4. **Hand steps** pause the workflow (`AWAITING_HAND`) and resume on `/rig-round ... hand <runId>`.

## TODO(tester)

- The build-and-install script the prepare step should call (§5), and the APK identity check.
- Device serials per role (D-HU, D-POCO, D-SAM, D-HP) and how `send` targets each.
- The marker verb, if it is not the §3 one the draft assumes.
- Whether `/tmp/ohu-rig.lock` is the lock path on the rig.
- Which `hur-wifi-test-scripts/` helpers the executor should prefer over raw `adb`.
- Whether a round may span a Claude restart (the draft supports it; the rig may not want it).

## Proposed line for the rig `CLAUDE.md`

> **DRAFT: `/rig-round <thread> <N>`** runs a queued brief through the `rig-round` workflow
> (`rig-sdlc/`), Haiku executing one run at a time under the rig lock and Sonnet grading, and
> leaves the commit and push to the host after the operator approves. Not yet adopted; the
> operator's rules above win wherever the two disagree.
