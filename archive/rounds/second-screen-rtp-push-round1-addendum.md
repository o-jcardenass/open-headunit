# second-screen-rtp-push round 1 addendum: new branch name and candidate SHA

**Written before** the round ran. The brief stands except for the values below.

The branch was rebased onto the new `main` and renamed. It is now `feat/second-screen-outputs`, the head of the second-screen PR, and it carries all seven commits: the five second-screen commits, then the two RTP push commits. The patches did not change. `feat/second-screen-rtp-push` no longer exists on `fork`.

| Brief check | Was | Is now |
|---|---|---|
| Branch (section 1, every `git` line) | `feat/second-screen-rtp-push` | `feat/second-screen-outputs` |
| Candidate SHA (section 1, `ls-remote` line) | `9341d71fa068f113bc7aebb69e1879322df9da07` | `8fc611a4901c09247b52c54c4c8e87ee862a71aa` |
| Tree (section 1) | `f325fbfeefe0df92405fe2f86801e4b1cfe8ca74` | `310fca3c60ee287c1b152bc7db4c44773928aa7c` |
| Base for `BASE_OK` (section 1) | `82b4214881d462711b853e8205cf4290ebedc53f` | `25ba1e5f5fa7ec384d46b3fcd7afcb9edda20748` (the same five commits after the rebase) |
| Merge base with `main` | `91392e7a` | `73b52b64cbf88beb411f064b014cd4d7ae5b19db` |
| Worktree folder (sections 1 and 7, `HUR_DIR`) | `../ohu-wt-rtp-9341d71f` | `../ohu-wt-rtp-8fc611a4` |
| `commit` in the `data=` reply (R0 PASS line) | starts with `9341d71fa068` | starts with `8fc611a4901c` |
| JVM tests | 3277 | 3352 (`main` gained tests; 0 failures) |

Use these lines in place of the block in section 1:

```bash
git ls-remote fork feat/second-screen-outputs   # MUST print 8fc611a4901c09247b52c54c4c8e87ee862a71aa
git fetch fork feat/second-screen-outputs
git worktree add ../ohu-wt-rtp-8fc611a4 8fc611a4901c09247b52c54c4c8e87ee862a71aa
git -C ../ohu-wt-rtp-8fc611a4 rev-parse 'HEAD^{tree}'               # MUST print 310fca3c60ee287c1b152bc7db4c44773928aa7c
git -C ../ohu-wt-rtp-8fc611a4 merge-base --is-ancestor 25ba1e5f5fa7ec384d46b3fcd7afcb9edda20748 HEAD && echo BASE_OK
```

The candidate now sits on `main`, so the brief's sentence "It does **not** sit on `main`" no longer applies. Nothing else changes: the runs, the settings keys and every deciding line in section 6 are the same. The same strings match at `8fc611a4` as at `9341d71f`.

If `ls-remote` prints another SHA or nothing, stop and report. If you already have a worktree at `9341d71f`, do not use it.
