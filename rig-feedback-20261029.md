# Rig session feedback, 2026-10-29

Written by the testing session for the coding PC, so briefs, helper libs and the `/rig-round` skill can be tuned.
Session scope: the four briefs added or changed by `b2ff181d` (automation-screen-disconnect round 5,
zj-bt-unix-socket round 1, pr-train-20261029 round 1, native-bt-broker-esp32 round 2). Written while round 3 was
being prepared; rounds 1 and 2 are finished.

## A. Mistakes by the testing session (not brief faults)

The operator's verdict was "a lot of novice errors". The list below is complete as far as I can tell, most costly first.

1. **I never checked that a session could form before the first run that needs one.** zj R1 needs a phone that has
   bonded to D-HU, has Bluetooth and WiFi on, and has connected to it once in Android Auto. I started R1 without
   reading bonds, and it ran four times (about 45 min). The operator found the bond gap, and then the radios gap.
2. **I ignored missing output.** `ph_on` should print a Bluetooth `state:` line and a WiFi line. The run log had
   neither for two attempts, and I did not ask why. The operator said "you are running R1 with D-POCO BT off". I
   should have read the radio state back after every radio helper, as `TESTING-TEMPLATE.md` §3 already says for
   `dumpsys`.
3. **I guessed a cause and wrote it down.** After attempt 2 I told the operator D-POCO's Android Auto probably had no
   consent entry for D-HU. The real cause was the hung `ph_on`. I broke the "do not trust a diagnosis" rule on my own
   diagnosis. The guess was in a message to the operator and in the first draft of the results file.
4. **Orphaned lock holders, three times.** `ptr_lib.sh` `th_gate` starts a background `th_watch` that inherits the
   rig lock; my first wrapper (`run5.sh`) did not kill `THPID`, so the next run timed out on `flock` (N1 attempt 1,
   no device action). Later I killed a run's parent shells but not their children, and a stale `bash -c` kept
   polling and released a hung `wait` by accident, which produced a half-run with dead captures. Killing by pid needs
   the whole tree (`ps -ef --forest`), not the parent.
5. **I tripped the kill guard twice** by putting `pgrep -af` and `kill` in one command (it blocks and can exit 144).
6. **Wrong tool call form.** I passed the brief's role names (`D-POCO:wifi,bt`) to `rig_preflight.sh`, which needs
   `D_POCO`. I read the script only after it failed.
7. **My grader had a quoting bug** (matched `name motorola ...` without the quotes the log prints). Harmless here,
   but a wrong grade line is the thing the template warns about.
8. **I had no live progress view.** The operator could not tell whether a long, quiet run was normal and had to ask
   for a log. A progress file should exist from the first command of a session, not on request.
9. **I stopped runs by hand twice** after a `waitn` took more than 10 minutes. I should have measured why before
   killing (see B3), and I should not have ended R1 without the closing steps in the same call.
10. **I built all five APKs before reading the later briefs' preconditions.** The build batch was fine (§5 allows
    it), but I read the four pre-flights only after the first failure. Reading all four first would have surfaced
    the pairing, board and playlist needs at the start.

## B. Brief and helper defects found

1. **zj-bt-unix-socket, pre-flight** has no bond, radio or first-connect check for D-POCO and D-HU, though R1 cannot
   pass without them. Add: D-POCO lists the head unit in `Bonded devices`; D-POCO's Android Auto has connected to it
   at least once.
2. **zj-bt-unix-socket `lib.sh` `ph_on`** does `wait $PHLOOP`. `r1.sh` never calls `ph_off`, so `PHLOOP` is empty and
   `wait` becomes a bare `wait` that blocks on the logcat jobs forever. The radios never come on, and the run waits
   until someone kills it. Fix: guard on a non-empty `PHLOOP` (patched in this round's copy).
3. **zj-bt-unix-socket `waitn`** runs `grep -c` over the whole capture each poll. D-HU writes about 60 MB in 10 min,
   so a `120 s` wait took over 10 min. Poll from a byte offset (`tail -c +N`) or use `timeout`.
4. **automation-screen-disconnect round 5, `arm()`** reads `listening=1` even when the `ACTIVELY LISTENING` line is
   in the capture 46 s before the quiet window: `waitfor` only sees lines newer than its own start, and the `init`
   wait consumed the window in which that line printed. Graded from the capture instead. Fix: check from the start
   marker, not from "now".
5. **automation-screen-disconnect round 5, P** reads D-MOTO's bonded list while its Bluetooth is off and gets an empty
   list (count 0), which the brief then calls "pair not bonded: stop the round". Switch the radio on first, or say so.
6. **`ptr_lib.sh` `th_gate`** leaks its watcher into the lock (see A4). Make `th_gate` return the pid and have
   `runlocked.sh` kill it, or run the watcher with the lock fd closed (`9>&-`).
7. **`rig_preflight.sh`** fails on an idle Gradle daemon left by the build batch; the skill covers it (kill, re-run),
   but the build batch could run `rig_cleanup.sh kill` itself at the end.
8. **`x_restore.sh`** (automation-screen-disconnect) ends with a logcat count that matches its own `grep` and prints
   `1`; the brief says "must be 0". Use `pgrep -x adb`-style matching or exclude the grep.
9. **Brief README Queue** lists D-MOTO as a phone for some stages with its Bluetooth off at session start. A one-line
   "radios this round needs on, per unit" table in every brief would let `rig_preflight.sh` run with the right
   `:wifi,bt` specs without guessing.
10. **pr-train brief** needs D-POCO (as phone) bonded to D-HU and a first Android Auto connect, same as B1, and a
    Spotify playlist on two phones that adb cannot verify. The brief should say which of these the operator confirms
    in the batched ask, and the skill should require an explicit "yes" before starting.

## C. Suggestions for the `/rig-round` skill

- Add a **requirements gate** between steps 2 and 3: for each run, list "a session can form" prerequisites (bond in
  both directions, radios on, first Android Auto connect done, board/cable present, battery, PIN-free screen) and
  check them by read-back before the first run, not after the first failure.
- Require a **read-back of any radio or state a helper changes**, and treat a helper that printed nothing as a fault.
- Create `rig-data/rounds/PROGRESS.log` (one line per marker change) at session start, with the `tail -f` command in
  the first message.
- Add a **kill-tree helper** (`rig_killtree.sh <pid>`) to the toolkit so stale runs do not hold the lock, and have
  `rig_cleanup.sh` report lock holders with their command line.
- Tell the host that a run which exceeds twice its brief time is a liveness failure (discipline 3) and must be
  examined, not waited out or killed blind.
