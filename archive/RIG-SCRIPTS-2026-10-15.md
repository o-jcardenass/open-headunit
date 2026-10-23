# Rig script changes from the 2026-10-15 audit

The coding side audited the rig's skills and procedures against the last eight rounds' Setup notes. It fixed
everything that lives on this branch. The items below change scripts in `~/Coding/StudioProjects/rig-toolkit/`,
which only the rig can edit. `APPLY-2026-10-15.md` sends the agent here after the first sync.

For each item: make the change, check it, then update the tool's entry in `rig-toolkit/TOOLS.md`. At the end, run
`python3 -I rig-sdlc/rig-env/rig_sync.py sync`, commit the `TOOLS.md` it brings IN
(`Rig SDLC: toolkit changes from the 2026-10-15 audit`) and push. Nothing here touches a device. If an item
cannot be done as written, skip it and say why in the commit's second line.

## 1. `build_hur.sh` takes `MAX_WORKERS`

**Why:** three briefs and the thermal rule asked for `--max-workers=2`, which the script never took. One round
ignored it and another wrote its own Gradle call.

**Change:** when `MAX_WORKERS` is set, pass `--max-workers=$MAX_WORKERS` to Gradle. `build_hur_cool.sh` passes
its environment through, so it gets this for free; check that it does.

**Check:** `MAX_WORKERS=2 HUR_DIR=<any worktree> build_hur.sh` shows `--max-workers=2` in the Gradle command
or log.

## 2. `build_hur_cool.sh` uses 95/85

**Why:** `thermal_guarded.sh` moved to HOT 95 and COOL 85 because 86/68 cost build time, but `build_hur_cool.sh`
still defaults to 86/68. One round paused Gradle four times.

**Change:** default `HOT=95 COOL=85`. Keep the env overrides.

## 3. `wt-new.sh` prints its path, and `.WORKTREE-INFO` stays out of git

**Why:** the briefs find the worktree with a `ls -d ... | tail -1` guess. The untracked `.WORKTREE-INFO` makes
the app's `ACTION_QUERY_STATE` report a `-dirty` commit, which cost several rounds time at the identity gate.

**Change:**
- Print the worktree's absolute path as the last line of stdout, alone.
- Add `.WORKTREE-INFO` to the common exclude file of the app repository once:
  `git -C ~/Coding/StudioProjects/headunit-revived rev-parse --git-path info/exclude` names it.

**Check:** `WT=$(wt-new.sh probe scratch HEAD | tail -1); git -C "$WT" status --short` prints nothing, then
`git -C ~/Coding/StudioProjects/headunit-revived worktree remove "$WT"`.

## 4. `rig_cleanup.sh` and `rig_preflight.sh` know a paired round

**Why:** in a paired round (`TESTING-TEMPLATE.md` §3b) the other round's logcat and lock count as strays, so
the second round's pre-flight fails and `kill` would end the other round's capture.

**Change:** take `RIG_KEEP_PIDS` (space-separated pids) and `RIG_KEEP_LOCKS` (lock paths). List them as
`kept (paired round)`, never kill them, and do not fail on them.

**Check:** start `sleep 300 &`, then run `RIG_KEEP_PIDS=$! rig_cleanup.sh list`. It must list the pid as kept,
not as a stray. Kill the sleep.

## 5. `rig_thermal.sh` names what it reads

**Why:** `TOOLS.md` says it reads "the head unit's CPU temperature", while every round uses it for the host PC.

**Change:** read the script. Correct the `TOOLS.md` line to what it reads. If it reads a device and not the
host, say so in the commit, because the thermal rules in `rig-quirks/topics/tooling.md` assume the host.

## 6. Optional: drop the dead path in the hook

`~/.claude/hooks/rig-tidy-guard.py` still checks `hur-wifi-test-scripts/`, which no longer exists. It does no
harm. If you remove it, the sync carries the change to the branch.
