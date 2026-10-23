---
name: rig-round
description: Run a queued round brief on the rig. The host reads the brief, runs each run through the Haiku rig-executor in the foreground, grades each run itself, drafts the results, and commits and pushes after the operator approves. Use when the operator types /rig-round.
disable-model-invocation: true
---

# /rig-round

Usage: `/rig-round <thread> <N> [hand <runId> | approve]` or `/rig-round <sha> [hand <runId> | approve]`. `hand` resumes at step 4 and `approve` at step 6;
neither runs step 1 again. This worktree's `CLAUDE.md` is already
loaded; this skill does not replace it or `TESTING-TEMPLATE.md`, it only says which parts to read. Rule 4 holds: **no
background agent drives a device.** The host runs every `rig-executor` in the foreground, one at a
time, and does all the judgement itself. There is no workflow and no grader agent: the host has
already read the brief and the template, so grading in its own context is the cheapest place to do it.

Shell prelude for every command: `source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh`.
`EV=/home/oscar/Coding/StudioProjects/rig-data/rounds/<thread>-round<N>`.

0. **Resolve a SHA.** If the first argument is 7 to 40 hex characters and no `<N>` follows, it is a commit
   on the transfer branch, not a thread. After `git fetch fork`, run
   `git show -s --format=%s <sha>`
   and check `git merge-base --is-ancestor <sha> fork/transfer/rig-rounds`. Take `<thread>` and `<N>` from
   the files the commit adds, not from the subject wording: `git show --stat --format= <sha>` lists
   `<thread>-round<N>-<brief|results|addendum>.md` (subjects vary, e.g. `usb-reattach: publish round 3 brief`).
   Use the subject only as a cross-check. Print the resolved `<thread> <N>` in one line and continue as if
   they had been typed. Stop and ask only if no such file is added, several threads are added, or the SHA is
   not on the branch. Later `hand`/`approve` calls may use the SHA too.
1. **Before launching.** Note the branch you came from. `git fetch fork`, check out
   `transfer/rig-rounds`, note `OLD=$(git rev-parse HEAD)`, and fast-forward it.
   **Sync and restart check, first:** run `python3 -I rig-sdlc/rig-env/rig_sync.py sync` (it carries edits both ways; see
   `rig-sdlc/rig-env/INVENTORY.md`). If it lists `IN` or `MERGED` paths, commit them by name
   (`Rig SDLC: <what changed>`), push to `fork` and run it again. On `CONFLICT`, resolve the markers in the
   branch file, then `sync --prefer branch`. If it printed `RESTART`, or `git diff --name-only $OLD HEAD -- CLAUDE.md`
   prints anything, ask the operator to restart Claude Code and stop. Do not start the round. Otherwise read `README.md`'s `## Queue` and the thread's row only,
   confirm `<thread>-round<N>-brief.md` exists with no results file. If the Queue line pairs this round
   with another, read `TESTING-TEMPLATE.md` §3b and both briefs' pairing sections, and plan both rounds as one
   session: one prepare step builds every APK, and the second round's long holds run beside the first's runs.
   Each round keeps its own `$EV`, `armed.txt`, monitor and lock (§3b). Put the round's lock path on line 1 of
   every executor prompt. Kill only pids the round recorded. The other round's logcat and lock are expected
   strays in `rig_cleanup.sh` and `rig_preflight.sh`: name them in Setup notes, never kill them.
   **Read only what the round needs**, once, and never the whole template or the whole quirk list:
   - the template without §7b and §8: `sed '/^## 7b\./,/^## 8\./{/^## 8\./!d}; /^## 8\./,$d' TESTING-TEMPLATE.md`
     (§7b only when a phone is the USB host; §8 is for whoever writes briefs);
   - from §7a's index: `rig-quirks/topics/tooling.md`, `rig-quirks/units/<unit>.md` for every unit the
     brief names, and the topic files for the areas its runs touch
     (`grep -il <term> rig-quirks/topics/*.md` when unsure). Say in Setup notes which quirk files you read.
   Read only the "Pick a tool" table of `rig-toolkit/TOOLS.md`
   (`sed -n '/^## Pick a tool/,/^## Rig and devices/p'`) and the entries of the tools you use, then run the **pre-flight**, always, before anything touches a device:
   `rig_preflight.sh <ROLE>[:wifi,bt] ...` naming every role the brief uses and, after the colon, the
   radios its runs need on (wifi, bt; any wireless, Native AA, HFP or A2DP run needs both on the head
   unit and the phone). Derive the radios from the brief's settings table (a
   `wifi-connection-mode` of 3 or any Native AA step needs bt on that unit, even if only one stage uses it);
   if a radio is needed by only some runs, name it and say so, then ask the operator rather than guessing. Also
   check any battery or API gate the brief's Prepare lists, in the same pass, so one message to the operator
   covers every blocker. It is read-only and checks adb presence, stray logcat, the rig lock, WiFi, BT,
   BT profiles and screen state. A FAIL stops the round: tell the operator, never switch a radio on
   yourself (the HU's WiFi re-join needs an operator tap). One exception: if `rig_cleanup.sh list` shows idle
   Gradle daemons and nothing else, run `rig_cleanup.sh kill` and re-run the pre-flight without asking. Paste its
   table into Setup notes; step 6 restores every radio to what this table shows.
2. **Plan (host).** Read the brief once and write down the run list in the brief's order: id, title,
   whether it is the point of the round, whether it has a hand step, the stop rule, and for each run
   the exact commands, markers and greps the executor needs (copied verbatim, never paraphrased).
   Flag anything §8 says a brief must not do (a "tap", a decisive string that is not verbatim in the
   build, a run §7a says cannot work) as a Setup note, and mark that run UNTESTABLE up front rather
   than improvising. Note `needsMarkers` if any run uses `ACTION_LOG_MARKER`.
   **Write the plan to `$EV/plan.md`, and add each Setup note to `$EV/setup-notes.md` when it happens.** A
   compaction or a restart keeps only files: the grades, the plan and the notes.
3. **Prepare.** The host builds; the executor installs. A build with thermal pauses can pass the 10-minute
   limit of a foreground call, and the executor may not run anything in the background.
   - **Host, builds:** `wt-new.sh` makes the worktree under `worktrees/`
     (the rig's hook refuses any other place): `WT=$($RIG_TOOLKIT/wt-new.sh <thread> scratch <first sha> | tail -1)`.
     For each arm, `git -C $WT checkout --detach <sha>` and check
     `git -C $WT rev-parse HEAD`, run `$RIG_TOOLKIT/rig_thermal.sh wait 70`, then run
     `HUR_DIR=$WT MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh` with `run_in_background` and a Monitor on its
     exit. Copy the APK out of `rig-data/apks/` to `$EV/apk/<arm>.apk` at once (the next build deletes it) and
     record its md5 in `$EV/prepare.json`. Never rebuild an arm whose md5 is recorded there. Run
     `run_unit_tests.sh` only if the brief asks; the dev host ran the suite.
   - **Executor, installs** (one foreground call): `adb -s <serial> install -r -d` per role, `apk_identity.sh`,
     back up `settings.xml`.
   - A failed gate is a §3a escalation: stop and report.
   **Markers.** If `needsMarkers`, check the candidate: `grep -n -A10 'CONFIGURING = setOf'
   <worktree>/app/src/main/java/com/andrerinas/openheadunit/automation/AutomationCommandPolicy.kt`.
   If `ACTION_LOG_MARKER` is listed, the candidate refuses markers until `allow-external-configuration`
   is `true`: write it in `settings.xml` with the app stopped (`set_hu_settings_host.py`, or
   `set_prefs_runas_host.py` when not rooted), never through the UI, and restore the baseline after
   the round. Either way, say which it was in Setup notes.
4. **Each run, in order.** Skip a run whose `$EV/<id>.json` and `$EV/<id>.grade.json` both exist (a
   round may span a restart; read the grade back instead). `untestable` runs are recorded, not run.
   **On resume** (after a restart or a compaction): read `$EV/armed.txt` and `TaskStop` stale jobs, read
   `$EV/plan.md`, `$EV/setup-notes.md` and `$EV/prepare.json`, and check each lock with `flock -n`.
   A hand step: tell the operator, wait for `/rig-round <thread> <N> hand <runId>`. Otherwise run
   `rig-executor` in the foreground with the run's steps; no block back means stop and check the rig
   yourself. Then **grade it yourself**:
   - Re-run **one** `grep -c` inside the same marker window and compare it with the block's count.
     On a mismatch, run `rig-executor` once more as a grep-only pass (no device commands) over the
     same capture; if the counts still disagree, grade from your own greps and say so in Setup notes.
   - Grade exactly one of PASS, FAIL, INCONCLUSIVE, UNTESTABLE per §6, from comparisons over counts
     and timestamps, never from reading the capture whole.
   - Write `{id, verdict, reason}` to `$EV/<id>.grade.json`.
   - Stop the loop when the stop rule is met. If this result changes what the remaining runs should
     be in a way the brief did not decide, that is §3a: stop and report the question.
5. **Report (host).** Draft `$EV/<thread>-round<N>-results.md` exactly per §7: header block,
   `## Setup notes` (from `$EV/setup-notes.md`: every deviation, wrong key, unmatched string, script used), one `## R<id>` per
   run with the bold verdict alone on its line, measurements as numbers, a quoted decisive line with
   its timestamp, and `## Anything the brief did not ask about`. Include runs graded before a
   restart from their `.grade.json`. Show the operator the verdicts and the draft, and wait for `approve`.
6. **On `approve`** (host only), in this order:
   1. **Restore:** the settings baseline; every radio to its state in the opening pre-flight table
      (`svc wifi|bluetooth enable|disable`, read back); kill any logcat by pid; `TaskStop` every id in
      `$EV/armed.txt` (monitors, thermal watch, background scripts).
   2. **Check:** `rig_cleanup.sh list` reads clean, then run `rig_preflight.sh` with the opening roles. Paste
      it into Setup notes as the closing pre-flight. A FAIL is reported to the operator, not left for the next round.
   3. **Remove the build worktree:** `git worktree remove --force $WT` (the APKs are already in `$EV/apk/`).
   4. **Publish:** zip the evidence as `<thread>-round<N>-captures.zip` and upload it as an asset of the
      thread's one release `rig-evidence-<thread>` (`gh release create` on the thread's first round,
      `gh release upload` after; see §7) with the sha256 in the results file. Copy the results file next to
      its brief. In `README.md`, update the thread's row (router style, name the results file) and delete the
      round's Queue line. Run `python3 -I rig-sdlc/rig-env/rig_sync.py sync` and add any `IN` or `MERGED` paths
      it lists. `git add` the results file and `README.md` by name, commit
      (`<thread>: round <N> results - <short outcome>`, no issue numbers, no trailers), push to `fork`.
   5. Return to the branch you came from (`git branch --show-current` first).

## Execution discipline (added after a round lost about 40 minutes to idle runs)

These are mechanical, and they override the looser wording above.

1. **Never hand an executor a placeholder.** Every executor prompt starts with literal lines
   `EV=<absolute path>`, `source .../rig_devices.sh` and the full serials; commands use only variables the
   prompt itself defines. A prompt that said `OUT=$EV` with `EV` undefined made a session script exit in
   one second while the executor waited 20 minutes for it.
2. **Never wait on `pgrep -f <script name>`.** It matches its own shell, so the loop never ends. Wait on a
   pid (`wait`, `kill -0 <pid>`) or on the script log's last line (`script exit=`). An executor has no
   background wait loop at all: for a session longer than 8 minutes the host runs the session script itself
   with `run_in_background` and is notified on completion.
3. **Liveness check inside 90 s of every launch.** The host reads the script log and `wc -c` of the capture
   twice, 20 s apart. A missing capture, a non-growing capture or a log that already ends in `script exit=`
   stops the run, kills the capture by pid and reports. A run that is quiet is a defect, never "still working".
4. **The session script owns the pre-checks, not the brief's prose.** It must refuse to start (exit non-zero,
   one line saying why) unless: the phone is on its launcher (HOME first); the head unit's station is in the
   state the run needs; every setting the run needs reads back correctly (including `connection-modes`
   containing the mode under test); and the stack has armed before the phone's Bluetooth is switched on.
   A one-round script lives in `rig-data/rounds/<thread>-round<N>/scripts/`; `rig-toolkit/TOOLS.md` says when one moves into the toolkit.
5. **Exit codes are read from the script, never through a pipe.** `cmd | tee` hides the exit status. Redirect to
   the log and append `script exit=$?` as the last line.
6. **Hand steps get a cue the operator can see.** Before launching, tell the operator exactly what appears on
   which screen and what to do. Inside the script print `OPERATOR NOW: ...` with a bell, and the host sends a
   PushNotification when the cue time is near. After a hand step is announced, do not report progress until
   the executor returns.
7. **Windowed greps only.** Units that refuse `logcat -c` (D-SAM) return the old buffer first. Window every
   count by marker or by the last `App.onCreate`, and say so in Setup notes. Poll files are truncated at the
   start of a run; never reuse a RUN name without clearing both files.
8. **A lever that changes rig state is part of the run.** `cmd wifi connect-network` without `-d` re-enables
   autojoin on the saved network and silently changes what a stand-down run measures. Use `-d` for any rejoin
   lever, read the autojoin state back, and void a run whose precondition the lever broke.
9. **No `pkill -f` in the host shell** (it matches the host's own command and exits 144). Kill by pid.
10. **Do not trust an executor's diagnosis.** It may name a root cause from a log fragment it did not
    compare against a passing run. The host greps the capture, compares it with a run that worked, and only then
    writes the cause in Setup notes. A stale hand-back for a voided run is ignored.

## Keeping the round moving (the operator should never have to type "continue")

1. **Never end a turn while a round is in progress and the next step is yours.** After a grade, start the
   next run in the same turn. A turn may end only for an operator hand step, an escalation (§3a), a thermal
   stop, or the round being done.
2. **Anything longer than one tool call runs in the background with a watcher.** Launch the session script
   with `run_in_background`, then arm `Monitor` on its log or on `$EV/<run>.status` with an until-loop that
   exits on `script exit=`, `DONE` or `ABORT`. The notification wakes the host; never poll or sleep, and never
   hand control back to the operator to wait for it. Monitors are governed by 2a below.

2a. **Monitor hygiene (stale monitors fire on the next run's sentinels).**
    - **One run-scoped monitor at a time per round** (a pair has one per round, §3b). Before arming a new one, `TaskStop` the previous one. Stop it
      the moment the run is graded, voided or aborted, including the liveness stop (discipline 3), a thermal
      void and an escalation, not only on clean completion.
    - **Bounded loops.** The until-loop also exits when the script pid is gone (`kill -0 <pid>`) and has a
      hard timeout (run length plus 5 minutes). A script that dies without writing `script exit=` must end
      its monitor, not orphan it.
    - **Unique paths.** Every session-script log and status file carries a run id and a timestamp
      (`$EV/<run>-<HHMMSS>.status`). Never reuse a path a previous monitor could still be watching. The
      executor's own heartbeat is `$EV/<run>.status`, written fresh at its start.
    - **Armed list.** Append one line per background job (monitor, thermal watch, session script) to
      `$EV/armed.txt` as `<task id> <kind> <watched path or pid>`. After a `/clear`, compaction or restart,
      read it first and `TaskStop` anything stale before arming anything new. A round's closing step stops
      every id still listed.
3. **Executors write a heartbeat** (`$EV/<run>.status`, see the agent file). If a run's status file has not
   grown in 90 s, that is the liveness failure of discipline 3: stop and check, do not wait.
4. **Send a PushNotification** (load it with ToolSearch) when the round finishes, when it stops for an
   escalation or a thermal stop, and before a hand step. Mid-round progress is not pushed.
5. **When a turn must end with work pending, say in one line what is armed and what happens next**, so a
   stale session is recognisable.

House rules: no issue or discussion numbers anywhere on this branch; no em dashes; measurements not
adjectives; a FAIL keeps its full capture.

Device serials, the lock and the marker verb live in `/home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh`.

## Host thermal

The limits are in one place, the first entry of `rig-quirks/topics/tooling.md`. Call the thermal tool by its
absolute path (`$RIG_TOOLKIT/rig_thermal.sh`): a relative path inside a worktree does not resolve, and one round
skipped its cooling wait silently. Never run a gradle build while a capture is recording.
