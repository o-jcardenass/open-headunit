---
name: rig-smoke
description: Run the main smoke or the video smoke on a branch, a SHA or origin/main, with no brief to write. The host starts the smoke's own runner, waits for it, and reports its one-line-per-run summary. Use when the operator types /rig-smoke.
disable-model-invocation: true
---

# /rig-smoke

Usage: `/rig-smoke video [<ref>] [approve]` or `/rig-smoke main [<ref>] [approve]`.

- `video` runs `rig-lib/video_smoke.py`: SurfaceView, TextureView and GLES on D-SAM, D-HU and D-HP.
- `main` runs `rig-lib/main_smoke.py`: Native, Self Mode, Headunit Server, audio output and USB through the dongle.
- `<ref>` is a branch (`fix/surfaceview-crop`), a remote ref (`fork/fix/surfaceview-crop`) or a SHA. With no
  `<ref>`, the smoke runs on `origin/main`.
- `approve` commits the summary of the last smoke (step 5). Nothing else is committed.

This worktree's `CLAUDE.md` is already loaded. The smoke's runner drives the devices through `rig_session.py`, with
no model in the loop, so the host reads no brief, no capture and no grade script.

Shell prelude for every command: `source /home/oscar/Coding/StudioProjects/rig-toolkit/rig_devices.sh`.

1. **Get the newest runner.** Note the branch you came from. Run `git fetch fork`, check out
   `transfer/rig-rounds`, and run `git merge --ff-only fork/transfer/rig-rounds`. If the merge refuses, stop and
   show the operator the error.
2. **Resolve `<ref>`.** Leave a SHA or a ref that starts with `fork/` or `origin/` as it is. For a bare branch
   name, run `git ls-remote --heads fork <name>`. Use `fork/<name>` when it prints a line. Otherwise use
   `origin/<name>`. With no `<ref>`, leave out `--ref`.
3. **Start the smoke.** Tell the operator, in one line, which smoke runs on which ref, and that it takes about
   75 min for `video` and longer for `main`. Then run this as one background shell command:
   `python3 -I rig-lib/<kind>_smoke.py run --ref <ref>`
   (`<kind>` is `video` or `main`). Do not start anything else that uses a device until it ends. Do not poll
   its output; wait for the notification. The runner prints a progress line for each run.
4. **Report.** When it ends, show the operator the runner's last lines: the `summary` block (one line per run)
   and the evidence folder under `rig-data/rounds/`. Then name the exit code:

   | Exit | Meaning | What to do |
   |---|---|---|
   | 0 | every run PASS, or a FAIL listed as known | nothing |
   | 1 | a run FAILED: a regression | offer `approve`, so the coding side sees it |
   | 2 | a run was INCONCLUSIVE, UNTESTABLE or not run | show the reason column; offer `approve` |
   | 3 | the session stopped for the operator | show the OPERATOR or ESCALATE line; run the same command again after |
   | 4 | the brief greps a line this commit no longer prints | tell the coding side; do not edit the brief here |
   | 5 | the build failed | show the last 20 lines of `build.log` in the evidence folder |

   Do not open captures or grade files to explain a verdict. The summary's reason column is the report.
   If the operator asks why a run failed, use `/rig-diagnose`.
5. **`approve`.** Copy the evidence folder's `summary.txt` to `smoke-results/<kind>-smoke-<yyyymmddHHMM>.md`
   at the worktree root, with the candidate SHA and the evidence folder path as its first two lines. Stage that
   file by name, commit it with the message `<kind>-smoke: <short sha> <no regression|REGRESSION|incomplete>`,
   and push to `fork`. Captures stay on this PC unless the operator asks for a release asset.
6. Check out the branch you came from.
