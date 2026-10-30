# Applying the 2026-10-30 role-name standard on the tester PC

**For the operator:** start Claude Code in this worktree and type one line:

```
Run git fetch fork, then follow rig-sdlc/APPLY-2026-10-30-role-names.md as it is on fork/transfer/rig-rounds
```

## The standard

A rig unit has one name, written two ways, and each way has one place:

| Where | Spelling | Why |
|---|---|---|
| Briefs, results files, docs, `rig-requires` blocks, messages to the operator | `D-HU`, `D-POCO`, `D-MOTO`, `D-HP`, `D-SAM` | `rig-lib/rig_requires.py` accepts only these |
| Shell variables from `rig_devices.sh` | `$D_HU`, `$D_POCO`, `$D_MOTO`, `$D_HP`, `$D_SAM` | a shell variable name cannot hold a hyphen |

`D-T230` and `$D_T230` stay as aliases of D-SAM. Do not rename anything in a brief or a results file that is
already on the branch.

**Every rig-toolkit command that takes a role name accepts both spellings.** `D-HU` and `D_HU` mean the same
unit. The tool changes `-` to `_` before it looks up the role, and prints nothing about the spelling.

## What to do

1. Find every place that prints the spelling warning. Run
   `grep -rn -i "underscore" ~/Coding/StudioProjects/rig-toolkit ~/Coding/StudioProjects/headunit-revived/.claude`
   and keep the lines that check or reject a role name. Write the file and line of each one in your report.
2. Find every command that takes a role name as an argument. At least `rig_preflight.sh` (`ROLE[:need,...]`) and
   `rig_check_devices` in `rig_devices.sh` do. Check the other tools in `TOOLS.md` the same way.
3. In each one, change the role argument from `-` to `_` before the lookup, for example
   `role=${role//-/_}` in bash or `role.replace("-", "_")` in Python. Remove the warning or the refusal.
   Keep the check for a name that is not a rig unit at all (`D-FOO` must still fail).
4. Test each changed command with both spellings on a connected unit, for example
   `rig_preflight.sh D-HU:wifi D_POCO` and `rig_preflight.sh D_HU:wifi D-POCO`. Both must give the same table.
   Test one unknown name too, and check that it still fails.
5. In `rig-toolkit/TOOLS.md`, add one line under each changed tool: `Accepts D-HU or D_HU.`
6. Run `python3 rig-sdlc/rig-env/rig_sync.py sync`. It carries the `TOOLS.md` edit back to
   `rig-sdlc/rig-env/TOOLS.md`. Commit that file and push to fork.
7. Report: each file and line that printed the warning, each command you changed, and the result of each test in
   step 4.

Do not change `rig-lib/` or any brief. `rig_requires.py` already turns `D-HU` into `D_HU` when it prints a
toolkit command.
