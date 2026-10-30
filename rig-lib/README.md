# rig-lib

One tested set of helpers for every brief, so a round never runs shell code that nobody ran before.
Each brief pins a commit of this folder in its `rig-requires` block; the rig unpacks that commit into
`$EV/rig-lib` (`git archive <sha> rig-lib | tar -x -C $EV`), so a later change cannot alter a round
in flight.

| File | Does |
|---|---|
| `rig_lib.sh` | `rl_*` helpers: waits from a byte offset with wall-clock caps, tracked captures and background jobs, radios with read-back, bond reads that refuse with Bluetooth off, the thermal gate |
| `rig_run.sh` | runs one script: requirements gate, lock (fd closed for children), own session, hard cap, status file with heartbeat and STALL, kills the whole tree before freeing the lock |
| `rig_requires.py` | parses a brief's `rig-requires` block; `order` picks the session order with the fewest operator actions, `plan` gives one operator list, `check --fix` reads radios, both sides of every bond and every forbidden bond (`nobond=`) back, clears a phone left as a group owner and switches what adb can; `--run ID` checks one run's scoped lines, `yes` records the operator's answer, `baseline` ends a round in `baseline.conf` |
| `rig_forget.py` | forgets a unit's pairing on another through Settings: finds the bond by the other unit's address, taps the gear of the one row with the name this unit shows for it, checks the page names that address, taps Forget and the confirm, then reads the bond gone; anything that does not match exactly once stops it with no further tap. `--dry-run` stops before Forget. `check --fix` calls it for a `nobond=`; a stop falls back to the OPERATOR line |
| `rig_bt.py` and `bt-helper/` | the Bluetooth API route on API 29 and up: `bt-helper/build.sh` builds a 10 KB instrumentation APK with the SDK's own tools (no Gradle); `am instrument` borrows the shell's BLUETOOTH_PRIVILEGED and BLUETOOTH_CONNECT, so a bond is listed, removed, asked for or accepted with no screen. `rig_forget.py` and `rig_pair.py` try it first and fall back to Settings. The helper app `ohu.rig.bthelper` stays installed on the unit |
| `rig_pair.py` | pairs two units through Settings: the target's Bluetooth screen is open (discoverable), the initiator scans for the target's current name, both pairing codes must match, then each Pair is tapped and both bonded lists are read back. `check --fix` calls it for a `bond=`; a stop falls back to the OPERATOR line |
| `rig_session.py` | runs a whole round from the block with no model in the loop: `check --fix`, each run through `rig_run.sh`, its grade script, one retry of a timeout or a stall, the stop rules; the host watches `session.status` only |
| `rig_resolve.py` | turns a transfer commit SHA into the rounds it asks for (`/rig-round <sha>`): briefs added or modified, rounds with results skipped, SHAs a re-root removed resolved by file name |
| `main_smoke.py` | the main smoke on the newest `origin/main`, at will (Native, Self Mode, Headunit Server, audio output, USB through the dongle on D-HP, which it finds over wireless adb or sets up from its PC cable, and leaves out when it cannot): pins `main_smoke_brief.md` to that commit, lints it, builds, runs `rig_session.py`, prints one line per run (exit 0 clean, 1 a regression, 2 incomplete, 3 operator, 4 brief out of date, 5 build) |
| `brief_check.py` | lint and a stub-adb smoke run for a brief; `commit-guard.py` on the coding PC refuses a brief whose exact content has not passed it |

Change a helper only with a test in `tests/test_rig_lib.sh` (`bash rig-lib/tests/test_rig_lib.sh`, no device needed).
