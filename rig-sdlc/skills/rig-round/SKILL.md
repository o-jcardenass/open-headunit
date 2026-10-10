---
name: rig-round
description: Run a queued round brief on the rig. The host reads the brief, runs each run through the Haiku rig-executor in the foreground, grades each run itself, drafts the results, and commits and pushes after the operator approves. Use when the operator types /rig-round.
disable-model-invocation: true
---

# /rig-round

Usage: `/rig-round <thread> <N> [hand <runId> | approve]` or `/rig-round <sha> [hand <runId> | approve]`. This worktree's `CLAUDE.md` is already
loaded; this skill does not replace it or `TESTING-TEMPLATE.md`, it only says which parts to read. Rule 4 holds: **no
background agent drives a device.** The host runs every `rig-executor` in the foreground, one at a
time, and does all the judgement itself. There is no workflow and no grader agent: the host has
already read the brief and the template, so grading in its own context is the cheapest place to do it.

Shell prelude for every command: `source /home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/rig_devices.sh`.
`EV=/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/evidence/<thread>-round<N>`.

0. **Resolve a SHA.** If the first argument is 7 to 40 hex characters and no `<N>` follows, it is a commit
   on the transfer branch, not a thread. After `git fetch fork`, run
   `git show -s --format=%s <sha>`
   and check `git merge-base --is-ancestor <sha> fork/transfer/rig-rounds`. Take `<thread>` and `<N>` from
   the files the commit adds, not from the subject wording: `git show --stat --format= <sha>` lists
   `<thread>-round<N>-<brief|results|addendum>.md` (subjects vary, e.g. `usb-reattach: publish round 3 brief`).
   Use the subject only as a cross-check. Print the resolved `<thread> <N>` in one line and continue as if
   they had been typed. Stop and ask only if no such file is added, several threads are added, or the SHA is
   not on the branch. Later `hand`/`approve` calls may use the SHA too.
1. **Before launching.** Note the branch you came from. `git fetch fork`, check out and
   fast-forward `transfer/rig-rounds`, read `README.md`'s `## Queue` and the thread's row only,
   confirm `<thread>-round<N>-brief.md` exists with no results file.
   **Read only what the round needs**, once, and never the whole template or the whole quirk list:
   - the template without §7b and §8: `sed '/^## 7b\./,/^## 8\./{/^## 8\./!d}; /^## 8\./,$d' TESTING-TEMPLATE.md`
     (§7b only when a phone is the USB host; §8 is for whoever writes briefs);
   - from §7a's index: `rig-quirks/topics/tooling.md`, `rig-quirks/units/<unit>.md` for every unit the
     brief names, and the topic files for the areas its runs touch
     (`grep -il <term> rig-quirks/topics/*.md` when unsure). Say in Setup notes which quirk files you read.
   Inventory `hur-wifi-test-scripts/`, then run the **pre-flight**, always, before anything touches a device:
   `rig_preflight.sh <ROLE>[:wifi,bt] ...` naming every role the brief uses and, after the colon, the
   radios its runs need on (wifi, bt; any wireless, Native AA, HFP or A2DP run needs both on the head
   unit and the phone). Derive the radios from the brief's settings table (a
   `wifi-connection-mode` of 3 or any Native AA step needs bt on that unit, even if only one stage uses it);
   if a radio is needed by only some runs, name it and say so, then ask the operator rather than guessing. Also
   check any battery or API gate the brief's Prepare lists, in the same pass, so one message to the operator
   covers every blocker. It is read-only and checks adb presence, stray logcat, the rig lock, WiFi, BT,
   BT profiles and screen state. A FAIL stops the round: tell the operator, never switch a radio on
   yourself (the HU's WiFi re-join needs an operator tap). Paste its table into Setup notes.
2. **Plan (host).** Read the brief once and write down the run list in the brief's order: id, title,
   whether it is the point of the round, whether it has a hand step, the stop rule, and for each run
   the exact commands, markers and greps the executor needs (copied verbatim, never paraphrased).
   Flag anything §8 says a brief must not do (a "tap", a decisive string that is not verbatim in the
   build, a run §7a says cannot work) as a Setup note, and mark that run UNTESTABLE up front rather
   than improvising. Note `needsMarkers` if any run uses `ACTION_LOG_MARKER`.
3. **Prepare** (one foreground `rig-executor`, evidence to `$EV/prepare.json`): `git worktree add` a
   scratch worktree of the candidate SHA (never build in this worktree), `HUR_DIR=<it> build_hur.sh`
   then `run_unit_tests.sh`, `adb -s <serial> install -r -d` per role, `apk_identity.sh`, back up
   `settings.xml`. A failed gate is a §3a escalation: stop and report.
   **Markers.** If `needsMarkers`, check the candidate: `grep -n -A10 'CONFIGURING = setOf'
   <worktree>/app/src/main/java/com/andrerinas/openheadunit/automation/AutomationCommandPolicy.kt`.
   If `ACTION_LOG_MARKER` is listed, the candidate refuses markers until `allow-external-configuration`
   is `true`: write it in `settings.xml` with the app stopped (`set_hu_prefs.sh`, or
   `set_hu_settings_runas.py` when not rooted), never through the UI, and restore the baseline after
   the round. Either way, say which it was in Setup notes.
4. **Each run, in order.** Skip a run whose `$EV/<id>.json` and `$EV/<id>.grade.json` both exist (a
   round may span a restart; read the grade back instead). `untestable` runs are recorded, not run.
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
   `## Setup notes` (every deviation, wrong key, unmatched string, script used), one `## R<id>` per
   run with the bold verdict alone on its line, measurements as numbers, a quoted decisive line with
   its timestamp, and `## Anything the brief did not ask about`. Include runs graded before a
   restart from their `.grade.json`. Show the operator the verdicts and the draft, and wait for `approve`.
6. **On `approve`** (host only): zip the evidence as `<thread>-round<N>-captures.zip` and
   upload it as an asset of the thread's one release `rig-evidence-<thread>` (`gh release create`
   on the thread's first round, `gh release upload` after; see §7) with the sha256 in the results file, copy the results file next
   to its brief, update the thread's README row (router style, name the results file), `git add` the
   results file and `README.md` by name, commit (`<thread>: round <N> results - <short outcome>`, no
   issue numbers, no trailers), push to `fork`, restore the settings baseline, kill any logcat by
   pid, `TaskStop` every id listed in `$EV/armed.txt` (monitors, thermal watch, background scripts),
   then return to the branch you came from (`git branch --show-current` first).

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
   `hur-wifi-test-scripts/home_wifi_reconnect_session.sh` and `hwr_set_*.sh` are the pattern.
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
    - **One run-scoped monitor at a time.** Before arming a new one, `TaskStop` the previous one. Stop it
      the moment the run is graded, voided or aborted, including the liveness stop (discipline 3), a thermal
      void and an escalation, not only on clean completion.
    - **Bounded loops.** The until-loop also exits when the script pid is gone (`kill -0 <pid>`) and has a
      hard timeout (run length plus 5 minutes). A script that dies without writing `script exit=` must end
      its monitor, not orphan it.
    - **Unique paths.** Every `.status` and script log carries a run id or timestamp
      (`$EV/<run>-<HHMMSS>.status`). Never reuse a path a previous monitor could still be watching.
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

Device serials, the lock and the marker verb live in `/home/oscar/Coding/StudioProjects/hur-wifi-test-scripts/rig_devices.sh`.
7. **Watch the host PC's thermal state.** The host throttled once under a gradle build plus a long capture,
   and a throttled host distorts timing and can lose a capture. Use `hur-wifi-test-scripts/rig_thermal.sh`:
   run `rig_thermal.sh wait 75` before every gradle build or unit-test run and before every run launch (exit 1
   means it never cooled: stop and tell the operator); during a build or a run start
   `rig_thermal.sh watch $EV/thermal.log` in the background and kill it by pid at the end; after each run read
   the log and the throttle counters (`status` prints `throttle_pkg`). A run whose thermal log shows
   `throttle_pkg` increasing, or `max` at or above 95C, is voided and re-run once after cooling; say so in
   Setup notes. Never run a gradle build while a capture is recording.
