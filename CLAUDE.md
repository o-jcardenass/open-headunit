# CLAUDE.md: ohu-transfer (testing agent orientation)

This file orients the **testing agent** that works in this directory: a git worktree holding the
transfer branch, an orphan hand-off channel between a planning agent on a PC and a physical test
rig with real Android head units and phones on adb. It does not repeat how to run a round.
**`TESTING-TEMPLATE.md` is the operational authority for that** (capture rules, `settings.xml`,
the automation surface, the clean-run protocol, install discipline, verdicts, rig quirks). Read it
before touching a device, and re-read it if a round feels unfamiliar; this file only covers what it
does not: how to change app code when a round needs that, and how to commit and report back on this
branch.

## 2026-09-23: the app is driven only through `AutomationReceiver`

**No `input tap` on the app, in any round.** Every action (the WiFi and USB buttons, the pill's X,
exit, a poke, a marker) is an `am broadcast` verb; `TESTING-TEMPLATE.md` §3 has the `send` helper
and the verb-to-control table. A brief that says "tap" is a brief bug: send the verb and say so in
Setup notes. A step with no verb is UNTESTABLE unless the brief names it a hand step.

## Layout

On the rig: the app checkout is `~/Coding/StudioProjects/headunit-revived` (`build_hur.sh`'s default
`HUR_DIR`), build worktrees go under `~/Coding/StudioProjects/worktrees/` (`wt-new.sh`), scripts are in
`rig-toolkit/`, and round data in `rig-data/`. This worktree carries the `transfer/rig-rounds` branch. Nothing
here is ever merged into the app; it is a message channel, not a codebase.

Remotes on the shared object store: `origin` is `andreknieriem/open-headunit` (upstream, read-only
in practice), `fork` is `o-jcardenass/open-headunit` (where our branches, including this transfer
branch, are pushed).

Start every round the way the template says, and read as little as it takes. `README.md` is a
router: its `## Queue` section is a few lines naming every brief that has no results file, and its
`## Threads` table has one line per thread. Read the Queue, then only your thread's row:

```bash
grep -F '| `<thread>`' README.md
```

Do not read the rest of the table or other threads' briefs and results. Then read the one brief
the row names, and read `rig-toolkit/TOOLS.md` on the rig before building anything.

## Subagent model routing

This machine runs **Sonnet as the host session** and **Haiku as the executor** (2026-09-25; until
then this section named Opus as host and judge, which is not what runs here). The split is by kind
of work, and the point is token spend and keeping the host's context small:

| Work | Model | How it is set |
|---|---|---|
| External lookup: an `adb` or `gradle` error message, an Android API or `dumpsys` field, a vendor ROM or chipset fact, an Android Auto release note | **Haiku** | `Agent(model: "haiku")` (the alias; the pinned id lives in `rig-executor`'s frontmatter) |
| **Execution of a brief with a `rig-requires` block (2026-10-29):** `rig-lib/rig_session.py run` runs every run, grades it from the brief's grade scripts and applies the stop rules, with no model in the loop. The host launches it in the background and reads only `$EV/session.status` | **none** | `python3 -I $EV/rig-lib/rig_session.py run --ev $EV <brief>`, one Monitor on `session.status` |
| **Execution of an older brief, installs, and any diagnosis grep:** running a brief's runs (the `send` verbs, the helper scripts in `rig-toolkit/`, captures, markers), counting landmarks in a capture with the greps a brief names, md5s and `git log` listings for the header block, installing APKs and checking their identity (builds are the host's, in the background; unit tests only if a brief asks) | **Haiku** | the `rig-executor` agent, **in the foreground**: the host waits for it, never a background agent (see below). One agent per run or per capture, carrying every command and grep the brief names for it, never one agent per grep |
| **Judgement:** assigning a run its verdict, reading a FAIL's extract end to end, deciding whether a log string that does not match means the brief or the build is wrong, deciding whether a code change a round needs is within scope, writing the Setup notes, the results file and the closing section, editing the README's thread row | **Sonnet** | the host session |
| Reading the brief, choosing the run order, a change to the clean-run protocol, the commit and the push | **Sonnet** | the host session only, never delegated |
| **Refusal fallback:** a prompt that stops on a classifier refusal is re-run once in the host session with the prompt narrowed to the step at hand; if it still refuses, hand the exact command to the operator | **Sonnet** | the host session, and say so in Setup notes |

Five rules the table implies.

1. **A capture is never opened with Read or `cat` in the host.** One is about 140k tokens and is
   re-sent on every turn after. Grep it with Bash, or hand it to a Haiku agent.
2. **Haiku returns data, never a verdict** (and a session's grade scripts return COND lines, not prose). Every execution agent ends with one JSON block, and
   nothing else counts as its result:

   ```json
   {"run": "R3", "commands": ["send ...", "..."], "exit_codes": [0, 0],
    "markers": {"R3-start": "15:27:01.112", "R3-end": "15:31:40.020"},
    "greps": [{"pattern": "Client list empty", "file": "dsam_c2.logcat", "count": 4,
               "first_ts": "15:27:04.658", "last_ts": "15:29:11.003",
               "excerpt_lines": [1841, 1902]}],
    "anomalies": ["logcat -c left 3 lines older than R3-start"]}
   ```

   Counts are anchored to the run's own markers, never to a whole-file grep (D-SAM's `logcat -c`
   does not clear its buffer). A run is PASS or FAIL only after the Sonnet host reads these blocks
   against the brief's conditions: five runs are five Haiku blocks feeding one Sonnet read.
3. **The host checks the files, not the message.** Before grading, re-run one grep per run
   yourself (`grep -c`) and compare it with the block's count; a mismatch means the Haiku pass is
   re-run once, and a second mismatch is graded from the host's own greps and noted in Setup notes.
4. **Execution agents run in the foreground, one runner at a time.** A background agent died
   silently mid-round and left the rig unsupervised for about 68 minutes
   (`archive/rounds/audio-focus-round10-results.md`, Setup notes), and two concurrent runs of one script corrupted
   a cycle (`archive/rounds/native-aa-dsam-wifi-unavailable-round2-results.md`, Setup notes). So: no background
   agent ever drives a device, and every script run takes the rig lock, which fails fast instead of
   racing a second terminal:

   ```bash
   EV=<round dir> $RL/rig_run.sh <run> <cap_s> ./partB_cycles.sh --source lib.sh   # exit 75: another run holds the rig
   ```

   **Paired rounds (2026-10-15).** When the README Queue pairs two rounds on separate units, the
   second round's long holds run as host session scripts beside the first, under their own lock file.
   Agents still drive one at a time, in the foreground. `TESTING-TEMPLATE.md` §3b has the rules.

5. **Haiku never decides scope and never writes this branch.** It does not edit briefs, results,
   the README or app code, does not commit, and runs no `git reset`, `git checkout` of another
   branch, `git push` or `git stash`. Those belong to the host.

## Running a round with `/rig-round`

`/rig-round <thread> <N>` (see `rig-sdlc/README.md`) runs a queued brief. A brief with a `rig-requires`
block runs as one `rig_session.py` process: it drives one run at a time under the rig lock (rule 4 stands,
because it is a script, not an agent), and the host only launches it, answers its `ESCALATE` lines and
writes the results from `summary.md`. That keeps the host's context to a few lines per run on a Pro
allowance. An older brief still runs each run through the Haiku `rig-executor` in the foreground. The host commits
and pushes after the operator's `approve`, then returns to the branch it came from.
**The rig's setup is mirrored in `rig-sdlc/`, both ways.** Edit the live files on the rig freely (`headunit-revived/.claude/skills`
and `agents`, the `.claude/` Claude Code loads here, `~/.claude/hooks/rig-*`, the hooks and model keys of `~/.claude/settings.json`, `rig-toolkit/TOOLS.md`). After any such edit, run `python3 -I rig-sdlc/rig-env/rig_sync.py sync`, commit the paths it lists
(`Rig SDLC: <what changed>`) and push, so the coding side sees it. The coding side edits `rig-sdlc/`, and the same
command carries that to the rig. `/rig-round` runs it at start and at `approve`. A session keeps what it loaded at
start, so restart Claude Code when the sync prints `RESTART`. Never put a `CLAUDE.md` from outside this
worktree on the branch: the fork is public. A rule from one that rounds need goes in this file.

## Modifying app code

A round sometimes needs a code change: a log line the brief asks for, a build break to fix, or a
small correction a round's own finding proves. Do the edit in the app worktree, never in this one
(there is no app code here to edit). The conventions below are condensed from the app repo's own
`CLAUDE.md`; read that file directly if a rule here feels incomplete, but do not restate it back
here.

- **Build:** `HUR_DIR=<worktree> build_hur.sh` (`assembleGithubDebug`, the only APK build that works). **Test:**
  the dev host runs `testGithubDebugUnitTest`; the rig runs `run_unit_tests.sh` only when a brief asks. Two product flavors on the `distribution` dimension,
  `github` and `playstore`; only `github` is buildable here. Do not add flavor-specific code for a
  rig round.
- `applicationId` `com.andrerinas.headunitrevived` and namespace/package
  `com.andrerinas.openheadunit` differ on purpose (the app kept its Play Store identity across a
  rename). Never "fix" that mismatch.
- **Logging is `AppLog`, never `android.util.Log`**, prefixed with the class name. Users attach
  these logs to bug reports, so a line added for a round should stay informative after the round is
  over. A log call's priority is not its level: a line can be `AppLog.d` and still sit behind an
  `if (AppLog.LOG_VERBOSE)` guard, so grep the guard, not the call, before writing a brief around
  what a log level should surface.
- **Pure decidable logic goes in a Kotlin `object` with a JUnit4 test in the same package**, under
  `app/src/test/`. Robolectric is not used, so anything touching an `Activity` or a `View` cannot be
  unit tested and has to be verified on the rig instead; that is exactly the coverage a round exists
  to provide.
- **Comments are 1-2 lines, 4 is the hard ceiling.** State the hazard and what the code does about
  it, never history or measurements. `[FIX]` / `[BUG_FIX]` markers stay; read the comment before
  touching code near one, several encode hardware workarounds a round already paid for once.
- **Never touch `app/src/main/proto/` during a round.** Editing a `.proto` alone breaks the build:
  the generated Java is committed and hand-regenerated with a pinned `protoc`. That is planning-side
  work, not something a rig round does.
- `CHANGELOG.md` and `README.md` in the app repo belong to the upstream maintainer. Never edit
  them. The app repo's own `CLAUDE.md` is local-only and never goes into a PR.
- **Commits are grouped by component, one thing per branch, no microcommits.** If a round's fix
  touches two unrelated areas, that is two commits, not one.

**Scope discipline:** if what a round needs is more than a log line or a build fix, say so in
Setup notes and in the results file instead of quietly widening the change. The planning agent
owns the branch's shape; a testing agent that grows a fix on its own has changed what the next round
is measuring without anyone deciding that on purpose.

## Committing on this branch

A `PreToolUse` hook, `open-headunit/.claude/hooks/commit-guard.py`, checks every `git commit`
before it runs. It is scoped to this repository's object store, so it applies in **both**
worktrees, including this one. It blocks a commit whose message:

- is more than 5 lines (subject, blank line, one or two body lines);
- has a subject over 80 characters, or a subject not shaped `Scope: what changed` (for example
  `WiFi Direct: ...`, `Native AA: ...`);
- has a non-blank second line;
- contains an em dash;
- carries a `Co-Authored-By: ... Claude` or `Claude-Session:` trailer, or a "Generated with Claude
  Code" footer;
- **on a branch whose name starts with `transfer/` (this one), contains `#<digits>`**: house rule 7,
  below.

A blocked commit prints its reasons on stderr, so treat that output as the fix list rather than
retrying blind.

Real examples from this branch's own log:

```
native-aa-recovery-identity-and-speed: round 3 results - R1 FAIL both HUs (3 non-phone
AG-record devices classified PHONE), standing selector crash, round stopped

ultrawide-touch-alignment: round 8 candidate SHAs corrected
```

The subject carries the topic slug, the round, and what the file is (`brief`, `results`,
`addendum`), optionally followed by ` - <short outcome>`. Keep the body to the one or two lines the
guard allows; anything longer belongs in the results file itself, not the commit message.

**House rule 7, no issue or discussion numbers anywhere on this branch.** Describe a thread by name
and cite a SHA, a log line or an evidence filename instead of a tracker number: this channel has no
access to the tracker, and a number dates badly next to the file it is naming. The guard enforces
this mechanically for commit messages; hold file contents to the same rule by habit. The one file
naming pattern that looks like an exception, `pr<NNN>-review-*.md`, names a pull request under
review rather than pointing at a tracker issue, and is not a case for citing an issue number
elsewhere.

**Append-only once pushed.** A brief or a results file that has been pushed to `fork` is never
force-pushed and never rewritten. A correction is a new commit on top, as in the round 8 example
above. Proofread a file before its first push; after that, fixing it is another commit, not an
edit to history.

## Reporting back

One results file per round, `<topic>-round<N>-results.md`, committed next to its brief. Follow the
skeleton in TESTING-TEMPLATE.md §7 exactly rather than improvising a shape: the header block
(Candidate, Baseline, APK md5s, Unit, Date), one `## Setup notes` section, one `## R<id>` section
per run with a bolded verdict, and a closing `## Anything the brief did not ask about`. Do not
duplicate that skeleton here; if a results file looks wrong, compare it against §7, not against this
paragraph.

**Setup notes is not a formality.** Every deviation from the brief, every wrong settings key,
every log string that does not match, every step that could not be performed as written goes there.
Most of what TESTING-TEMPLATE.md itself now knows about this rig came out of a previous round's
Setup notes; a round that skips it costs the next round the same hours.

Verdicts are exactly one of **PASS**, **FAIL**, **INCONCLUSIVE**, **UNTESTABLE**, per §6. For a
FAIL, attach the full capture, never an excerpt. Give the measurement, never an adjective:
"5180 MHz", not "5 GHz".

**The bolded verdict line is always there, even when a run is graded per hit or per cycle.** A
per-hit table is evidence under the verdict, not a replacement for it: `## R<id>`, then the bold
verdict alone on its line, then the table. A round-level verdict, when the brief asks for one, is
its own `## Round verdict: <VERDICT>` line. Tools that read results files (and the evals that
check the Haiku-to-Sonnet split) find verdicts by those two shapes only;
`archive/rounds/native-aa-dsam-wifi-unavailable-round2-results.md` stated its FAIL only in a sentence under a
table, and nothing but a person can read that.

Evidence goes in `rig-data/rounds/<topic>-round<N>/` on the rig, and zipped into the thread's release (§7).
Photos too: this branch carries markdown and the mirrored rig setup only, never captures or images.

**The README is a router, not a log.** It carries one row per thread in the `## Threads` table,
`| Thread | State | Next |`. A round's outcome belongs in its own results file, never spelled out
as a narrative in the README. When a round finishes, update that thread's row, name the results
filename in it, and delete the round's Queue line; that is the entire edit the README gets.
