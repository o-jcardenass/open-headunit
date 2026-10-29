# fps-overlay-compact round 1 addendum: new candidate SHA, same tree

**Written before** the round ran. The brief stands except for the checks below.

The translations `fixup!` is now folded into the second commit on `fork`. The tree did not change, so the APK content is the same. Three values in the brief change.

| Brief check | Was | Is now |
|---|---|---|
| `git rev-parse HEAD` (Setup, and the Candidate row) | `398b54b172ff78f761bb0cc938580adb5be0c7a3` | `10905b633f4c065a48f4945fd0325ce99cfde1db` |
| `git log --oneline 145a0c76..HEAD \| wc -l` | `3` | `2` |
| `commit` from `ACTION_QUERY_STATE` (step 6 and the PASS line) | starts with `398b54b1` | starts with `10905b63` |

These do not change: the tree `98bd0d721fe0cce8b0fc30fddb6f9858e90ef0ef`, the merge base `145a0c762f0a87386ca63b54518ea5e861b290be`, 2755 JVM tests, and every decisive string.

Ignore the brief's sentence about a third `fixup!` commit. The branch now has two commits: `0e7e1a34` and `10905b63`.

If you already fetched the branch, fetch it again before you build. If `HEAD` is still `398b54b1`, stop and ask.
