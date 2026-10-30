#!/usr/bin/env python3
"""Which rounds a transfer-branch commit asks the rig to run. Deterministic, so /rig-round <sha> never guesses.

  rig_resolve.py <sha> [--branch fork/transfer/rig-rounds]

Prints one `RUN <thread> <N>` line per round to run, then `NOTE` lines for what was left out and why.
Exit 0 with at least one RUN line, exit 1 with none. Run it from any checkout of the repo after
`git fetch fork`; on the rig, before the branch is checked out:
  git show fork/transfer/rig-rounds:rig-lib/rig_resolve.py | python3 -I - <sha>
Rules: a brief the commit adds OR modifies counts (a revision is a request too); an addendum counts
for its round; a round whose results file is already on the branch tip is done; a commit that a re-root
removed from the branch still resolves when the same file name is on the tip, with a note if it changed.
"""
import re, subprocess, sys

PAT = re.compile(r"^(?P<thread>[^/]+)-round(?P<n>\d+)-(?P<kind>brief|addendum)\.md$")


def git(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True)
    return r.returncode, r.stdout.strip()


def main(argv):
    if len(argv) < 2:
        print(__doc__); return 64
    sha, branch = argv[1], "fork/transfer/rig-rounds"
    if "--branch" in argv:
        branch = argv[argv.index("--branch") + 1]
    rc, full = git("rev-parse", "--verify", "--quiet", f"{sha}^{{commit}}")
    if rc:
        print(f"NOTE {sha} is not in this clone. Run `git fetch fork`; if it is still missing, a re-root "
              "dropped it and it was never fetched here: ask for the thread and round instead.")
        return 1
    on_branch = git("merge-base", "--is-ancestor", full, branch)[0] == 0
    _, names = git("diff-tree", "--no-commit-id", "--name-status", "-r", "-M", full)
    rounds, notes = [], []
    for line in names.splitlines():
        parts = line.split("\t")
        status, path = parts[0], parts[-1]
        m = PAT.match(path)
        if not m or status.startswith("D"):
            continue
        key = (m["thread"], int(m["n"]))
        brief = f"{m['thread']}-round{m['n']}-brief.md"
        if git("cat-file", "-e", f"{branch}:{brief}")[0]:
            notes.append(f"{brief} is not on {branch} now (renamed or removed): skipped")
            continue
        if git("cat-file", "-e", f"{branch}:{m['thread']}-round{m['n']}-results.md")[0] == 0:
            notes.append(f"{m['thread']} round {m['n']} already has a results file on {branch}: skipped")
            continue
        if not on_branch and git("rev-parse", f"{full}:{path}")[1] != git("rev-parse", f"{branch}:{path}")[1]:
            notes.append(f"{path} changed on {branch} since {sha} (re-rooted or revised): the tip version runs")
        if key not in rounds:
            rounds.append(key)
    if not on_branch:
        notes.insert(0, f"{sha} is not an ancestor of {branch} (a re-root rewrote the branch); resolved by file name")
    for t, n in rounds:
        print(f"RUN {t} {n}")
    for n in notes:
        print(f"NOTE {n}")
    if not rounds:
        print(f"NOTE {sha} ({git('show', '-s', '--format=%s', full)[1]}) asks for no round: it adds or changes no "
              "brief without results. Ask the operator for the thread and round.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
