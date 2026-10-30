# rig-lib

One tested set of helpers for every brief, so a round never runs shell code that nobody ran before.
Each brief pins a commit of this folder in its `rig-requires` block; the rig unpacks that commit into
`$EV/rig-lib` (`git archive <sha> rig-lib | tar -x -C $EV`), so a later change cannot alter a round
in flight.

| File | Does |
|---|---|
| `rig_lib.sh` | `rl_*` helpers: waits from a byte offset with wall-clock caps, tracked captures and background jobs, radios with read-back, bond reads that refuse with Bluetooth off, the thermal gate |
| `rig_run.sh` | runs one script: requirements gate, lock (fd closed for children), own session, hard cap, status file with heartbeat and STALL, kills the whole tree before freeing the lock |
| `rig_requires.py` | parses a brief's `rig-requires` block; `order` picks the session order with the fewest operator actions, `plan` gives one operator list, `check --fix` reads radios and both sides of every bond back and switches what adb can, `yes` records the operator's answer, `baseline` ends a round in `baseline.conf` |
| `rig_session.py` | runs a whole round from the block with no model in the loop: `check --fix`, each run through `rig_run.sh`, its grade script, one retry of a timeout or a stall, the stop rules; the host watches `session.status` only |
| `rig_resolve.py` | turns a transfer commit SHA into the rounds it asks for (`/rig-round <sha>`): briefs added or modified, rounds with results skipped, SHAs a re-root removed resolved by file name |
| `brief_check.py` | lint and a stub-adb smoke run for a brief; `commit-guard.py` on the coding PC refuses a brief whose exact content has not passed it |

Change a helper only with a test in `tests/test_rig_lib.sh` (`bash rig-lib/tests/test_rig_lib.sh`, no device needed).
