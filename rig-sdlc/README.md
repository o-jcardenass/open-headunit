# Rig SDLC: `/rig-round`

The rig half of the coding side's `/ohu-sdlc` pipeline. The coding side publishes
`<thread>-round<N>-brief.md`, the rig runs it with `/rig-round`, and the coding side's
`ohu-rig-grade` agent picks up `<thread>-round<N>-results.md`. The brief/results contract is
unchanged. Adopted 2026-09-30; the operator's rules in `CLAUDE.md` win wherever they disagree.

**Upgrading an existing install:** follow `rig-sdlc/APPLY-2026-09-30.md` once.

## What is here

| File | Role | Model |
|---|---|---|
| `agents/rig-executor.md` | runs one run's commands (or the prepare step) under the rig lock, returns the JSON block `CLAUDE.md` defines | Haiku |
| `skills/rig-round/SKILL.md` | `/rig-round <thread> <N>`: the host plans, runs executors in the foreground, grades, drafts, and commits after `approve` | host (Sonnet) |
| `skills/rig-diagnose/SKILL.md` | what to do when a run surprises you mid-round | host |

**Rule 4 is unchanged:** no background agent drives a device. Every `rig-executor` run is a
foreground call from the host, one at a time, under `flock -n /tmp/ohu-rig.lock`.

**No workflow and no grader agent on the rig, on purpose.** The rig runs on a Pro allowance, and
once execution is sequential and in the foreground a workflow only re-reads the brief and the
template in a fresh Sonnet context per grade. The host has both loaded already, so it plans, grades
and drafts itself. The coding side's `/ohu-sdlc` keeps its workflow, which fans out in parallel.

Install on the rig, from the root of this worktree (`.claude/` stays untracked here; add
`/.claude/agents/` and `/.claude/skills/` to `.git/info/exclude` so it is
never committed to this branch):

```bash
mkdir -p .claude/agents .claude/skills
cp rig-sdlc/agents/*.md .claude/agents/
cp -r rig-sdlc/skills/rig-round rig-sdlc/skills/rig-diagnose .claude/skills/
```

Restart Claude Code afterwards: agents added mid-session do not register until then. An install from
before this change also removes `.claude/workflows/rig-round.js` and `.claude/agents/rig-grader.md`.

## Conventions

Helpers live in `hur-wifi-test-scripts/` (sibling directory); evidence, grades and results drafts in
its `evidence/<thread>-round<N>/`.

- **Build and install:** `build_hur.sh` then `run_unit_tests.sh`, both with `HUR_DIR=<worktree of the
  candidate>` (never the transfer worktree), then `adb -s <serial> install -r -d` per role.
- **APK identity:** `apk_identity.sh <apk> <serial>...` (md5 of the pulled install vs the built APK).
  When the md5 cannot settle it, use a symbol the candidate introduces (`TESTING-TEMPLATE.md` §5).
- **Serials, lock, marker:** `source hur-wifi-test-scripts/rig_devices.sh` (roles, `rig_check_devices`,
  `rig_marker <serial> <tag>`; `-f 0x00000020` included, no spaces in the tag). Serials can change;
  `rig_check_devices` stops the round on a stale one.
- **Markers and the gate:** current `main` does not gate `ACTION_LOG_MARKER`; older candidates do
  (it was in `AutomationCommandPolicy.CONFIGURING`). When a plan has `needsMarkers`, prepare greps
  the candidate's `CONFIGURING` set and, only if the marker is listed, writes
  `allow-external-configuration=true` into `settings.xml` with the app stopped, then restores the
  baseline after the round.
- **Restart mid-round:** allowed. A run with an evidence JSON is skipped; check for stray `logcat`
  and a free lock before relaunching.
