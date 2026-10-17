# Testing space inventory

What the rig machine has set up for agents, skills and hooks, so the coding side can read it and edit
the files directly. Every row is kept in step both ways by `rig_sync.py` (next to this file): the rig edits
its live copy, the coding side edits the copy here, and `sync` carries each edit to the other side, merging
when both changed. The rig runs it at `/rig-round` start and `approve`, and after any edit of its own. Any
new skill folder, agent file or `rig-*` hook on either side joins the mirror by name.

Snapshot taken 2026-10-15 on the rig machine.

| Item | Kind | Source in this branch | Rig location | Delivery |
|---|---|---|---|---|
| `rig-executor` | agent (Haiku) | `rig-sdlc/agents/rig-executor.md` | `headunit-revived/.claude/agents/rig-executor.md` | sync |
| `rig-round` | skill | `rig-sdlc/skills/rig-round/SKILL.md` | `headunit-revived/.claude/skills/rig-round/` | sync |
| `rig-diagnose` | skill | `rig-sdlc/skills/rig-diagnose/SKILL.md` | `headunit-revived/.claude/skills/rig-diagnose/` | sync |
| rig `CLAUDE.md` | instructions | `CLAUDE.md` | worktree root | tracked |
| `rig-tidy-guard.py` | PreToolUse hook (Bash, Write, Edit, NotebookEdit) | `rig-sdlc/rig-env/hooks/rig-tidy-guard.py` | `~/.claude/hooks/` | sync |
| `rig-tidy-grandfathered.txt` | hook data | `rig-sdlc/rig-env/hooks/` | `~/.claude/hooks/` | sync |
| hook wiring, default model | settings | `rig-sdlc/rig-env/settings.hooks.json` | `~/.claude/settings.json` (hooks and model keys only) | sync |
| `rig-toolkit/` tool inventory | doc | `rig-sdlc/rig-env/TOOLS.md` | `~/Coding/StudioProjects/rig-toolkit/TOOLS.md` | sync |

## Notes for editing

- Drift found when this was taken: the rig's local `rig-executor.md` was ahead of the branch (Haiku 5.5
  model id, stop-on-first-oddity rule, heartbeat file), so the branch copy was replaced by it. The rig's
  local `rig-round` was behind the branch (no restart check, no paired-round step) and was left alone.
- `rig-executor.md` and the `rig-round` skill carry absolute paths from the rig machine (`rig-toolkit/` for
  scripts, `rig-data/rounds/` for evidence); keep them when editing. The old `hur-wifi-test-scripts/` path is gone
  (station-scan round 1 Setup notes), and the 2026-10-15 coding-side pass moved every live file off it.
- The hook blocks new top-level folders in StudioProjects, worktrees outside `worktrees/`, and captures or
  APKs written into `rig-toolkit/`. A new script in `rig-toolkit/` needs its `TOOLS.md` entry first.
- Skills and agents load only at Claude Code start; an edit here takes effect after the next restart on the rig.
- Not mirrored on purpose (the fork is public): every `CLAUDE.md` outside this worktree (`~/.claude/CLAUDE.md`, `headunit-revived/.claude/CLAUDE.md`; a rule from them that rounds need goes in the tracked `CLAUDE.md` here), the other keys of `settings.json`, credentials, history, plugins, the synced Anthropic skills, and the
  `rig-toolkit/` scripts themselves (they stay on the rig; `TOOLS.md` describes them).
