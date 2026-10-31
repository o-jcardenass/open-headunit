# Rig SDLC: `/rig-round`

The rig half of the coding side's `/ohu-sdlc` pipeline. The coding side publishes
`<thread>-round<N>-brief.md`, the rig runs it with `/rig-round`, and the coding side's
`ohu-rig-grade` agent picks up `<thread>-round<N>-results.md`. The brief/results contract is
unchanged. Adopted 2026-09-30; the operator's rules in `CLAUDE.md` win wherever they disagree.

**Upgrading an existing install:** follow `rig-sdlc/APPLY-2026-10-15.md` once. The 2026-09-30 one is in `archive/`.

## What is here

| File | Role | Model |
|---|---|---|
| `agents/rig-executor.md` | runs one run's commands (or the prepare step) under the rig lock, returns the JSON block `CLAUDE.md` defines | Haiku |
| `skills/rig-round/SKILL.md` | `/rig-round <thread> <N>`: the host plans, runs executors in the foreground, grades, drafts, and commits after `approve` | host (Sonnet) |
| `skills/rig-diagnose/SKILL.md` | what to do when a run surprises you mid-round | host |
| `skills/rig-smoke/SKILL.md` | `/rig-smoke video [<ref>]` or `/rig-smoke main [<ref>]`: runs `video_smoke.py` or `main_smoke.py` on a branch, a SHA or `origin/main`, and reports the summary; `approve` commits it to `smoke-results/` | host |

**Rule 4 is unchanged:** no background agent drives a device. Every `rig-executor` run is a
foreground call from the host, one at a time, under `flock -n /tmp/ohu-rig.lock`.

**No workflow and no grader agent on the rig, on purpose.** The rig runs on a Pro allowance, and
once execution is sequential and in the foreground a workflow only re-reads the brief and the
template in a fresh Sonnet context per grade. The host has both loaded already, so it plans, grades
and drafts itself. The coding side's `/ohu-sdlc` keeps its workflow, which fans out in parallel.

Install on the rig: start Claude Code at the root of this worktree and type
`Follow rig-sdlc/APPLY-2026-10-15.md`. It runs the first sync and keeps `.claude/` out of the branch.
Restart Claude Code afterwards: skills and agents load only at start.

**Two-way sync.** `python3 -I rig-sdlc/rig-env/rig_sync.py sync` compares each item's live copy on the rig, its copy here, and its
copy at the last sync. The side that changed wins; both changed means a three-way merge into the file here.
So the rig may edit its own skills, agents, hooks and `TOOLS.md` in place, and the coding side may edit them
here; neither loses the other's edit. A new skill or agent on either side is picked up by name.
`/rig-round` runs it at start and at `approve`; the rig's `CLAUDE.md` says to run it after any edit.

The rest of the testing space (hooks, hook wiring, tool inventory) is mirrored in `rig-env/`; start at
`rig-env/INVENTORY.md`, which says what each file is and how the rig receives an edit.

## Conventions

Helpers live in `rig-toolkit/` (sibling directory, inventory in its `TOOLS.md`, mirrored in
`rig-env/TOOLS.md`); evidence, grades and results drafts in `rig-data/rounds/<thread>-round<N>/`.
Build worktrees go under `worktrees/` through `rig-toolkit/wt-new.sh`.

- **Build and install:** `build_hur.sh` per arm in one worktree (`run_unit_tests.sh` only if the brief asks), with `HUR_DIR=<worktree of the
  candidate>` (never the transfer worktree), then `adb -s <serial> install -r -d` per role.
- **APK identity:** `apk_identity.sh <apk> <serial>...` (md5 of the pulled install vs the built APK).
  When the md5 cannot settle it, use a symbol the candidate introduces (`TESTING-TEMPLATE.md` §5).
- **Serials, lock, marker:** `source rig-toolkit/rig_devices.sh` (roles, `rig_check_devices`,
  `rig_marker <serial> <tag>`; `-f 0x00000020` included, no spaces in the tag). Serials can change;
  `rig_check_devices` stops the round on a stale one.
- **Markers and the gate:** current `main` does not gate `ACTION_LOG_MARKER`; older candidates do
  (it was in `AutomationCommandPolicy.CONFIGURING`). When a plan has `needsMarkers`, prepare greps
  the candidate's `CONFIGURING` set and, only if the marker is listed, writes
  `allow-external-configuration=true` into `settings.xml` with the app stopped, then restores the
  baseline after the round.
- **Restart mid-round:** allowed. A run with an evidence JSON is skipped; check for stray `logcat`
  and a free lock before relaunching.
