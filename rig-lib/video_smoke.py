#!/usr/bin/env python3
"""Run the video smoke on any commit, at will, and report which projection backend regressed.

  video_smoke.py [run] [--ref origin/main] [--apk APK]   fetch the ref's remote, pin, lint, build, run, summarise
  video_smoke.py render --sha SHA --out FILE [--no-hp]   write video_smoke_brief.md pinned to SHA (no device)
  video_smoke.py summary --ev EV                         print the summary of a finished session again

The smoke runs SurfaceView, TextureView and GLES, two sessions each, on D-SAM (API 19, Native AA with D-POCO),
D-HU (API 34, Native AA with D-MOTO) and D-HP (API 17, the USB dongle with D-MOTO behind it). Each unit's
TextureView sessions are the reference for its other two modes. D-HP is found as main_smoke.py finds it; when it
cannot be reached, its three runs are left out and the summary says what to do.

The ref may be any branch of any remote (`fork/fix/x`), a tag or a SHA: `run` fetches from the remote the ref
names. Evidence goes to rig-data/rounds/video-smoke-auto-round<yyyymmddHHMM>/. No model and no hand step.
Exit: 0 every run PASS (or a known FAIL), 1 a run FAILED, 2 a run was INCONCLUSIVE, UNTESTABLE or not run,
3 the session stopped for the operator, 4 the brief greps a line the commit no longer prints, 5 the build failed.
"""
import re, shutil, subprocess, sys, threading, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))   # -I leaves the script's folder off sys.path
import main_smoke as ms  # noqa: E402

TRANSFER = HERE.parent
BRIEF = HERE / "video_smoke_brief.md"
HP_RUNS = ("PT", "PS", "PG")
# Runs that FAIL on main for a known reason; a FAIL there is reported but does not fail the smoke.
KNOWN_FAIL = {}
BLOCK = re.compile(r"(^```rig-requires[ \t]*\n)(.*?)(^```)", re.M | re.S)


def drop_hp(block):
    """The rig-requires block without D-HP: its runs, stop lines, unit lines and the dongle line go, and the D-HP
    runs leave every scoped unit line, which goes too when no run is left in its scope."""
    out = []
    for line in block.splitlines(keepends=True):
        if re.match(r"(?:run|stop) (?:%s) " % "|".join(HP_RUNS), line) or re.match(r"unit D-HP[@ ]", line) \
                or line.startswith("hardware the USB dongle "):
            continue
        m = re.match(r"unit (\S+?)@(\S+)(.*)$", line.rstrip("\n"))
        if m:
            ids = [i for i in m.group(2).split(",") if i not in HP_RUNS]
            if not ids:
                continue
            line = f"unit {m.group(1)}@{','.join(ids)}{m.group(3)}\n"
        out.append(line)
    return "".join(out)


def render(sha, out, skip_hp=False):
    text = BRIEF.read_text()
    if skip_hp:   # no D-HP this time: the session must not wait for it
        text = BLOCK.sub(lambda m: m.group(1) + drop_hp(m.group(2)) + m.group(3), text, count=1)
    text, n1 = re.subn(r"(?m)^candidate V [0-9a-f]{7,40}$", f"candidate V {sha}", text)
    text, n2 = re.subn(r"(?m)^CAND_SHA=\S+", f"CAND_SHA={sha[:9]}", text)
    if n1 != 1 or n2 != 1:
        raise SystemExit(f"video_smoke_brief.md: expected one candidate line and one CAND_SHA line, found {n1} and {n2}")
    Path(out).write_text(text)


def fetch_args(ref, remotes):
    """The git fetch that brings REF up to date: from the remote its first part names (fork/fix/x fetches
    fix/x from fork), or None for a tag or a SHA, which the caller resolves locally first."""
    head, _, rest = ref.partition("/")
    return ["fetch", head, rest] if head in remotes and rest else None


def resolve(ref):
    """Fetch what REF needs and return its full SHA, or "" when it does not resolve."""
    git = ["git", "-C", str(TRANSFER)]
    remotes = ms.sh(git + ["remote"]).stdout.split()
    args = fetch_args(ref, remotes)
    if args:
        f = ms.sh(git + args)
        if f.returncode:
            print(f"warning: git {' '.join(args)} failed, so {ref} may be old: {f.stderr.strip()[:200]}")
    rev = lambda: ms.sh(git + ["rev-parse", "--verify", "--quiet", f"{ref}^{{commit}}"]).stdout.strip()
    sha = rev()
    if not sha and re.fullmatch(r"[0-9a-f]{7,40}", ref):   # a SHA the clone does not have yet
        for r in remotes:
            ms.sh(git + ["fetch", r, ref] if len(ref) == 40 else git + ["fetch", r])
            sha = rev()
            if sha:
                break
    if not sha:
        print(f"remotes in {TRANSFER}: {', '.join(remotes) or 'none'}; add the one {ref} lives on "
              f"(git -C {TRANSFER} remote add NAME URL) and run again")
    return sha


def summary(ev):
    return ms.summary(ev, KNOWN_FAIL, HP_RUNS, "video smoke")


def run(ref, apk):
    sha = resolve(ref)
    if not re.fullmatch(r"[0-9a-f]{40}", sha or ""):
        print(f"cannot resolve {ref}")
        return 5
    ev = ms.RIG_DATA / "rounds" / f"video-smoke-auto-round{time.strftime('%Y%m%d%H%M')}"
    ev.mkdir(parents=True)
    (ev / "candidate.txt").write_text(f"{sha} ({ref})\n")
    brief = ev / "video-smoke-auto-brief.md"
    hp, why = ms.ensure_hp()
    if why:
        (ev / "hp.skip").write_text(why + "\n")
        print(f"{', '.join(HP_RUNS)} are left out: {why}")
    render(sha, brief, skip_hp=bool(why))
    print(f"video smoke on {sha[:9]} ({ref}); evidence in {ev}", flush=True)
    lint = ms.sh([sys.executable, "-I", str(HERE / "brief_check.py"), "lint", str(brief), "--repo", str(TRANSFER)])
    (ev / "lint.txt").write_text(lint.stdout + lint.stderr)
    if lint.returncode:
        print("the brief is out of date for this commit (a graded line is gone, or a script broke):")
        print(lint.stdout + lint.stderr, end="")
        return 4
    pin = re.search(r"(?m)^lib (\S+)", brief.read_text()).group(1)
    tar = ms.sh(f"git -C '{TRANSFER}' archive {pin} rig-lib | tar -x -C '{ev}'")
    if tar.returncode:
        print(f"cannot unpack rig-lib {pin}: {tar.stderr.strip()}")
        return 5
    # Starting the smoke is the operator's yes to the brief's confirm lines; print them so it is an informed one.
    for line in re.findall(r"(?m)^(?:confirm|hardware) .*$", brief.read_text()):
        print(f"assumed true: {line}")
    ms.sh([sys.executable, "-I", str(ev / "rig-lib" / "rig_requires.py"), "yes", "--ev", str(ev), str(brief)])
    if apk:
        (ev / "apk").mkdir(exist_ok=True)
        shutil.copy2(apk, ev / "apk" / "V.apk")
    elif not ms.build(sha, ev, name="video-smoke-auto", arm="V"):
        print(f"the build failed; see {ev / 'build.log'}")
        return 5
    stop = threading.Event()
    t = threading.Thread(target=ms.follow, args=(ev / "session.status", stop), daemon=True)
    t.start()
    code = subprocess.run([sys.executable, "-I", str(ev / "rig-lib" / "rig_session.py"), "run", "--ev", str(ev),
                           str(brief)]).returncode
    time.sleep(3); stop.set(); t.join()
    worst = summary(ev)
    if code == 3:
        print("the session stopped for the operator (see the OPERATOR or ESCALATE line above); run it again after")
        return 3
    return worst


def main(argv):
    args = argv[1:]
    cmd = args.pop(0) if args and args[0] in ("run", "render", "summary") else "run"
    opt = {k: v for k, v in zip(args[::2], args[1::2])}
    if cmd == "render":
        render(opt["--sha"], opt["--out"], skip_hp="--no-hp" in args); return 0
    if cmd == "summary":
        return summary(opt["--ev"])
    return run(opt.get("--ref", "origin/main"), opt.get("--apk"))


if __name__ == "__main__":
    sys.exit(main(sys.argv))
