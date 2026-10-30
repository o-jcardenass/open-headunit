---
name: rig-round
description: Run a queued round brief on the rig. The host reads the brief, runs each run through the Haiku rig-executor in the foreground, grades each run itself, drafts the results, and commits and pushes after the operator approves. Use when the operator types /rig-round.
disable-model-invocation: true
---

# /rig-round

Usage: `/rig-round <thread> <N> [hand <runId> | approve]`, `/rig-round <sha> [hand <runId> | approve]`, or
`/rig-round queue [approve]` (every queued brief with a `rig-requires` block, one session; see "Context and the operator's time"). `hand` resumes at step 4 and `approve` at step 6;
neither runs step 1 again. This worktree's `CLAUDE.md` is already
loaded; this skill does not replace it or `TESTING-TEMPLATE.md`, it only says which parts to read. Rule 4 holds: **no
background agent drives a device.** The host runs every `rig-executor` in the foreground, one at a
time, and does all the judgement itself. There is no workflow and no grader agent: the host has
already read the brief and the template, so grading in its own context is the cheapest place to do it.

Shell prelude for every command: `source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh`.
`EV=/home/oscar/Coding/StudioProjects/rig-data/rounds/<thread>-round<N>`.

0. **Resolve a SHA.** If the first argument is 7 to 40 hex characters and no `<N>` follows, it is a commit
   on the transfer branch, not a thread. Never resolve it by reading `git show` yourself. After
   `git fetch fork`, run exactly:
   `git show fork/transfer/rig-rounds:rig-lib/rig_resolve.py | python3 -I - <sha>`
   Each `RUN <thread> <N>` line is a round to run. One line: continue as if `<thread> <N>` had been typed.
   Several lines (a commit that queues or revises several briefs): run them as `/rig-round queue` limited
   to those rounds. Copy its `NOTE` lines into Setup notes. Exit 1 (no `RUN` line): show the operator
   the `NOTE` lines and ask for the thread and round; that is the only case that asks. It counts a brief
   the commit adds or modifies, skips a round that already has results, and resolves a SHA that a re-root
   removed from the branch by file name. Later `hand`/`approve` calls may use the SHA too.
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
   table into Setup notes.
   **Session gate, once, before any build or install, for every brief the session will run.**
   1. Pin the library: `RL=$EV/rig-lib; git archive <lib sha from the brief's rig-requires block> rig-lib | tar -x -C $EV`.
   2. Order the session: `python3 -I $RL/rig_requires.py order --live <every queued brief this session runs>` (`--live` reads the bonds the rig holds now).
      It picks the order with the fewest operator actions and lists each transition between briefs as
      `auto` (done by `check --fix`: radios adb can switch, role swaps with `headunit://exit`) or
      `OPERATOR` (D-HU's WiFi re-join, a pairing). Run the briefs in that order, not the Queue's.
   3. `python3 -I $RL/rig_requires.py plan <briefs in that order>` prints the one operator message, with
      headings A to D. Send it as printed, plus the `OPERATOR` transitions and any pre-flight FAIL. **A (the
      live progress view) is mandatory in this message**: a plain command the operator runs in a separate
      terminal, never a `!` line. `rig_watch.sh` updates by itself and stops at the session's DONE.
      B lists physical actions only; C lists yes-or-no questions only, never tasks. On their answers, run
      `rig_requires.py yes --ev $EV <every brief>` once. This is the only question of the session unless
      something breaks.
   4. **Build every APK of every brief in the session now, in one batch, before the first run** (§3,
      Prepare). Never build between briefs or between stages: a build heats the host and stops the rig
      for minutes. A brief whose arm shares a SHA with another brief's arm reuses that APK.
   **`PROGRESS.log` format:** one entry per line, and every line ends with a line break. Only `rl_log`,
   `rig_run.sh` and `rig_session.py` write it; never append to it by hand.
   **What the operator does, and what you do.** The operator does only what needs hands: a cable, a
   board, a charger, a screen only a person can unlock. You run every command yourself, including the adb
   switch between USB and wireless (`adb tcpip`, `adb connect`, `adb usb`). Never ask the operator to run
   code. If a step truly needs the operator (a `sudo` password, an interactive login), give the exact
   line to paste into a separate terminal, with no `!` in front.
   A brief queued before 2026-10-29 has no block: write one from its pre-flight into `$EV/requires.md`, use
   that file in these commands, and run that brief the old way (steps 2 to 4 with the executor).
2. **Plan (host).** For a brief with a block, the plan is `python3 -I $RL/rig_session.py view <brief>`
   (one screen); write its output to `$EV/plan.md` and skip the rest of this step. Never read such a brief
   whole: open one run's section with `rig_session.py view <brief> <run>` only when a line in
   `session.status` needs it. For an older brief: Read the brief once and write down the run list in the brief's order: id, title,
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
     `run_unit_tests.sh` only if the brief asks; the dev host ran the suite. After the last arm is built,
     stop its Gradle daemon (`cd $WT && ./gradlew --stop`), or the next pre-flight fails on it.
   - **Executor, installs** (one foreground call): `adb -s <serial> install -r -d` per role, `apk_identity.sh`,
     back up `settings.xml`.
   - A failed gate is a §3a escalation: stop and report.
   **Markers.** If `needsMarkers`, check the candidate: `grep -n -A10 'CONFIGURING = setOf'
   <worktree>/app/src/main/java/com/andrerinas/openheadunit/automation/AutomationCommandPolicy.kt`.
   If `ACTION_LOG_MARKER` is listed, the candidate refuses markers until `allow-external-configuration`
   is `true`: write it in `settings.xml` with the app stopped (`set_hu_settings_host.py`, or
   `set_prefs_runas_host.py` when not rooted), never through the UI, and restore the baseline after
   the round. Either way, say which it was in Setup notes.
4. **A brief with a block: one session process.** Launch
   `python3 -I $RL/rig_session.py run --ev $EV <brief>` with `run_in_background`, and arm one `Monitor` on
   `$EV/session.status` that exits on `OPERATOR|ESCALATE|DONE` (bounded: the sum of the run caps plus 10
   minutes). The session runs `check --fix`, then every run in order through `rig_run.sh`, grades each one
   from its grade script, retries a timeout or a stall once and then grades it INCONCLUSIVE, and applies the
   stop rules. Do not read run logs, status files or captures while it runs. On a notification:
   - `OPERATOR <run>: <cue>`: send a PushNotification with the cue word for word. Wait for nothing; the run goes on.
   - `OPERATOR <run> now: <action>`: a run's own needs (a pair, a forget, a radio only the operator can turn
     on). Send it word for word. The session re-reads the state every 15 s and goes on by itself; do nothing else.
   The session's one operator message also says: "Touch no unit the session uses (`order` lists them) until
   DONE, except when an OPERATOR line asks." A bond changed mid-run cost round 2 of esp32 its stage K.
   - `ESCALATE <why>`: the session stopped (exit 3) on something only the host or the operator can settle.
     Settle it, then launch `run` again; graded runs are skipped. A broken grade script is a brief defect:
     grade that run by hand from one windowed `grep -c`, write its `.grade.json`, and note it for the coding side.
   - `DONE`: go to step 5, using `$EV/summary.md` and the `.grade.json` files.
   The host still owns the verdicts. Read the COND lines of a FAIL or an INCONCLUSIVE in its `.grade.json`. If
   one looks wrong, check it with one windowed `grep -c`, or hand a grep-only pass to `rig-executor` so the
   capture stays out of this context, and override the grade with a reason in Setup notes.
   **An older brief: each run, in order**, as below. Skip a run whose `$EV/<id>.json` and `$EV/<id>.grade.json` both exist (a
   round may span a restart; read the grade back instead). `untestable` runs are recorded, not run.
   **On resume** (after a restart or a compaction): read `$EV/armed.txt` and `TaskStop` stale jobs, read
   `$EV/plan.md`, `$EV/setup-notes.md` and `$EV/prepare.json`, and check each lock with `flock -n`.
   A hand step: tell the operator, wait for `/rig-round <thread> <N> hand <runId>`. Otherwise launch
   the run's script through the wrapper, never bare:
   `EV=$EV $RL/rig_run.sh <id> <cap from the rig-requires block> $EV/scripts/<script> --source $EV/scripts/lib.sh`.
   A run under 8 minutes goes to `rig-executor` in the foreground with that one command; a longer one
   the host launches with `run_in_background`. Arm `Monitor` on the status path it prints, until a line
   matches `DONE|TIMEOUT|STALL`. On `STALL`, read the line: it names the step and what the script waits
   on. Check that, then decide; never wait it out and never kill it blind. Exit codes: 75 lock held (the
   holder is named; `$RL/rig_run.sh reap` clears only this round's sessions); 78 the requirements gate
   (run `rig_requires.py check --ev $EV $EV/requires.md --fix`); 124 the cap and 125 a stall: re-run once,
   then grade INCONCLUSIVE (rig) and go on without asking the operator. Then **grade it yourself**:
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
   **Clocks:** the session sets each unit's clock to the PC's where adb's shell is root and measures the rest
   (`CLOCKS` line, `$EV/clock-offsets.txt`). Copy that file into Setup notes. Before you compare timestamps
   across units, subtract each `LEFT` unit's offset from its timestamps. A count you add beyond the grade
   needs its string checked with `git grep -F` at the candidate first: a string no build prints reads 0.
6. **On `approve`** (host only), in this order:
   1. **Restore:** the settings baseline; `$RL/rig_run.sh reap` (kills any session a run left); every radio
      to the fixed baseline with `python3 -I $RL/rig_requires.py baseline` (it reads each one back; never to
      the opening table, which carries an off radio forward); `TaskStop` every id in `$EV/armed.txt`.
      **Device files:** a block brief's session already swept them; read `$EV/device-files.txt` and show the
      operator every `LEFT` or `STILL THERE` line. An older brief: delete only the device paths its scripts name,
      after `adb pull` to `$EV/device-files/`. Never delete a file the round did not write.
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
3. **Liveness is the status file, not the capture.** Logcat keeps growing while a script hangs, so capture
   growth proved nothing in the 2026-10-29 session. `rig_run.sh` writes a `HB` line every 30 s with the step's
   age and what the script waits on, and one `STALL` line when a step outlives 180 s (or its wait cap plus 30 s).
   Inside 90 s of every launch, read the status file: no `START` line, or a log that already ends in
   `script exit=`, stops and reports. A `STALL` is a defect to examine, never "still working". After any helper
   that changes a radio or a state, read its output: a helper that printed nothing is a fault.
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
    writes the cause in Setup notes. A stale hand-back for a voided run is ignored. The same holds for the
    host's own guess: a cause goes to the operator or the results file only with the grep that shows it.
    "Probably" is not a cause; write "unexplained" and the observation instead.
11. **Kill a tree, never a parent.** A run's children outlive its shell. `$RL/rig_run.sh reap` ends every
    session a run of this round left behind; for anything else, `ps -ef --forest` first and kill every pid
    in the tree.

## Red flags: stop when you think one of these (from the 2026-10-29 session, `rig-feedback-20261029.md`)

Each thought below cost that session a run or more. When you catch yourself thinking it, do the
right-hand column instead.

| The thought | What to do instead |
|---|---|
| "The run is quiet, so it is still working." | Read the run's status file. A step older than its cap is a STALL: look at what it waits on. |
| "It is slow; I will give it a few more minutes." | A run past twice its brief time is a liveness failure (discipline 3). Find out why, then decide. |
| "Android Auto probably has no record of the unit." | Write "unexplained" and the observation, or grep for the line that shows the cause. |
| "Try the pairing again." | Read the screen dump first. The first failure usually says why in its text. |
| "I will start the stage; the script is surely there." | `ls $EV/scripts/<name>` first. A block without a name is never extracted. |
| "The script header has the usage; I know how to call it." | Use `rig_session.py`, which takes the arguments from the run line. Never launch a stage by hand. |
| "The operator can do the bond change while K runs." | Tell the operator which units to leave alone until DONE. A unit changed mid-run voids the run. |
| "I will pipe it to `cut` to see it here." | Send the output to a file and read the file. A pipe a background job holds never closes. |
| "I will build everything now and read the briefs later." | Read every brief's `rig-requires` block and `order` output first. |
| "I will kill that process; it looks stale." | `ps -ef --forest` first, or `rig_run.sh reap`. Kill the tree, never only the parent. |

## Context and the operator's time (added 2026-10-29: a four-brief session reached 42% of context at half way)

The host runs on a Pro allowance, and a long context is where its mistakes come from. Execution is a
script so that nothing from a run enters this context; keep it that way.

1. **What the host reads, once a session:** the trimmed template, the quirk files the briefs need, and
   `rig_session.py view` for each brief. Never a whole brief with a block, a capture, a run log, a run's
   status file or a `.runner` script. Per run, the host sees the few `session.status` lines its Monitor
   delivers and, only for a FAIL or an INCONCLUSIVE, that run's COND lines.
2. **Diagnosis stays out of this context.** Past one windowed `grep -c`, hand the question to
   `rig-executor` as a grep-only pass; it returns counts, timestamps and line numbers.
3. **`/rig-round queue` runs every queued brief with a block in one session**, in `rig_requires.py order`
   order: the session gate once, then for each brief steps 3 to 5 with a 5-line `$EV/handoff.md` (verdicts,
   open questions, next brief) written before the next starts. A compaction or `/clear` resumes from those.
   Do not stop for `approve` between rounds: draft each results file and go on. Ask for **one** `approve`
   at the end, covering every draft; step 6 then runs once per round.
4. **Count every question to the operator.** Append it to `$EV/interventions.txt` (the session does this for
   its own `OPERATOR` and `ESCALATE` lines) and put `Operator interventions: N` in the results header with the
   list in Setup notes. The target is one per session, the opening yes, plus anything physical (a pairing, a
   tap, a cable). Before asking, check that no command can answer it: a radio adb can switch, a state adb can
   read and a retry are never questions.

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
