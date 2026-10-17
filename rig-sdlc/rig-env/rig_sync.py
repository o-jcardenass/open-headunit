#!/usr/bin/env python3
"""Two-way sync between the rig's live Claude Code setup and its mirror in rig-sdlc/ on this branch.

Each item is compared three ways: the rig's live copy, the branch copy, and the branch copy at the last
sync (the commit in .claude/rig-sync-base). Whichever side changed since then wins; both changed means a
three-way merge into the branch file. The rig may edit its live files freely; this carries the edit back.

  rig_sync.py status                 what differs, and which way it would go (exit 1 if anything does)
  rig_sync.py sync [--prefer branch|rig]
                                     apply it; --prefer settles a first run (no base) or a conflict
                                     the host has resolved by hand in the branch file (--prefer branch)

After a sync that brought files IN, commit and push them, then run sync again: a clean, matching run
records HEAD as the new base. The fork is public, so no CLAUDE.md outside this worktree is mirrored, and
of settings.json only the model and hooks keys are.
"""
import filecmp, json, os, shutil, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]          # the transfer worktree
HOME = Path.home()
STUDIO = HOME / "Coding" / "StudioProjects"
BASE_FILE = ROOT / ".claude" / "rig-sync-base"


def live_claude_dir():
    """The .claude/ Claude Code loads on the rig: RIG_CLAUDE_DIR, else the app checkout's (where the rig runs
    its sessions, per rig_sdlc_drift.sh), else this worktree's."""
    if os.environ.get("RIG_CLAUDE_DIR"):
        return Path(os.environ["RIG_CLAUDE_DIR"]).expanduser()
    app = STUDIO / "headunit-revived" / ".claude"
    return app if (app / "skills").is_dir() or (app / "agents").is_dir() else ROOT / ".claude"


LIVE = live_claude_dir()
SETTINGS_KEYS = ("model", "hooks")

# (branch path, live path, kind). "dir" items are whole folders; "glob" pairs mirror every entry by name.
FIXED = [
    ("rig-sdlc/rig-env/TOOLS.md", STUDIO / "rig-toolkit" / "TOOLS.md", "file"),
    ("rig-sdlc/rig-env/settings.hooks.json", HOME / ".claude" / "settings.json", "settings"),
]
GLOBS = [  # (branch dir, live dir, entry filter)
    ("rig-sdlc/skills", LIVE / "skills", lambda p: p.is_dir()),
    ("rig-sdlc/agents", LIVE / "agents", lambda p: p.suffix == ".md"),
    ("rig-sdlc/rig-env/hooks", HOME / ".claude" / "hooks", lambda p: p.name.startswith("rig-") and p.is_file()),
]
RESTART_KINDS = ("rig-sdlc/skills/", "rig-sdlc/agents/", "rig-sdlc/rig-env/settings")


def git(*a, check=True):
    return subprocess.run(["git", "-C", str(ROOT), *a], capture_output=True, check=check)


def items():
    out = list(FIXED)
    for bdir, ldir, keep in GLOBS:
        names = set()
        for d in (ROOT / bdir, ldir):
            if d.is_dir():
                names |= {p.name for p in d.iterdir() if keep(p)}
        base = read_base()
        if base:
            r = git("ls-tree", "--name-only", f"{base}:{bdir}", check=False)
            names |= {n for n in r.stdout.decode().split() if n}
        for n in sorted(names):
            kind = "dir" if bdir.endswith("skills") else "file"
            out.append((f"{bdir}/{n}", ldir / n, kind))
    return out


def read_base():
    try:
        return BASE_FILE.read_text().strip() or None
    except OSError:
        return None


def settings_text(path):
    try:
        d = json.loads(Path(path).read_text())
    except (OSError, ValueError):
        return None
    return json.dumps({k: d[k] for k in SETTINGS_KEYS if k in d}, indent=2, sort_keys=True) + "\n"


def snapshot(bpath, live, kind, side):
    """Return {relative file: bytes} for one side, or None if the item does not exist there."""
    if side == "base":
        base = read_base()
        if not base:
            return "NOBASE"
        r = git("ls-tree", "-r", "--name-only", f"{base}", "--", bpath, check=False)
        files = [f for f in r.stdout.decode().splitlines() if f]
        if not files:
            return None
        out = {}
        for f in files:
            blob = git("show", f"{base}:{f}").stdout
            out[f[len(bpath):].lstrip("/") or "."] = blob
        if kind == "settings":
            out = {".": (settings_text_bytes(out["."]) or b"")}
        return out
    p = ROOT / bpath if side == "branch" else Path(live)
    if not p.exists():
        return None
    if kind == "settings":
        t = settings_text(p)
        return None if t is None else {".": t.encode()}
    if p.is_dir():
        return {str(f.relative_to(p)): f.read_bytes() for f in sorted(p.rglob("*")) if f.is_file()}
    return {".": p.read_bytes()}


def settings_text_bytes(b):
    try:
        d = json.loads(b)
    except ValueError:
        return None
    return (json.dumps({k: d[k] for k in SETTINGS_KEYS if k in d}, indent=2, sort_keys=True) + "\n").encode()


def decide(b, r, base, prefer):
    if b == r:
        return "same"
    if base == "NOBASE":
        if b is None or r is None:
            return "IN" if b is None else "OUT"       # on one side only: nothing to lose
        return {"branch": "OUT", "rig": "IN"}.get(prefer, "NOBASE")
    if r == base:
        return "OUT-DEL" if b is None else "OUT"
    if b == base:
        return "IN-DEL" if r is None else "IN"
    if prefer == "branch":
        return "OUT-DEL" if b is None else "OUT"
    if prefer == "rig":
        return "IN-DEL" if r is None else "IN"
    return "BOTH"


def write(snap, dest, kind):
    dest = Path(dest)
    if kind == "settings":
        try:
            cur = json.loads(dest.read_text())
        except (OSError, ValueError):
            cur = {}
        new = json.loads(snap["."])
        for k in SETTINGS_KEYS:
            cur.pop(k, None)
        cur.update(new)
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(json.dumps(cur, indent=2) + "\n")
        return
    if dest.is_symlink() or dest.is_file():
        dest.unlink()
    elif dest.is_dir():
        shutil.rmtree(dest)
    if list(snap) == ["."] and kind != "dir":
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(snap["."])
        return
    for rel, data in snap.items():
        f = dest / rel
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_bytes(data)


def remove(dest):
    dest = Path(dest)
    if dest.is_symlink() or dest.is_file():
        dest.unlink()
    elif dest.is_dir():
        shutil.rmtree(dest)


def merge(bpath, b, r, base, kind):
    """Three-way merge, file by file, into the branch copy. Returns True when clean."""
    if kind == "settings" or base in (None, "NOBASE") or b is None or r is None:
        return False
    clean = True
    for rel in sorted(set(b) | set(r)):
        bb, rr, oo = b.get(rel), r.get(rel), base.get(rel, b"")
        if bb == rr:
            continue
        target = ROOT / bpath / rel if rel != "." else ROOT / bpath
        if bb is None or rr is None:
            clean = False
            continue
        tmp = Path(os.environ.get("TMPDIR", "/tmp")) / f"rig_sync_{os.getpid()}"
        tmp.mkdir(exist_ok=True)
        (tmp / "o").write_bytes(oo)
        (tmp / "r").write_bytes(rr)
        res = subprocess.run(["git", "merge-file", "-L", "branch", "-L", "base", "-L", "rig",
                              str(target), str(tmp / "o"), str(tmp / "r")])
        shutil.rmtree(tmp, ignore_errors=True)
        clean = clean and res.returncode == 0
    return clean


def main():
    args = sys.argv[1:]
    cmd = args[0] if args else "status"
    prefer = args[args.index("--prefer") + 1] if "--prefer" in args else None
    print(f"live skills and agents: {LIVE}")
    rows = plan(prefer)
    width = max(len(r[0]) for r in rows)
    ins, outs, conflicts, restart = [], [], [], False
    for bpath, live, kind, act in rows:
        if act == "same":
            continue
        if cmd == "sync":
            b, r = snapshot(bpath, live, kind, "branch"), snapshot(bpath, live, kind, "rig")
            if act == "OUT":
                write(b, live, kind)
            elif act == "OUT-DEL":
                remove(live)
            elif act == "IN":
                write(r, ROOT / bpath, "file" if kind == "settings" else kind)
            elif act == "IN-DEL":
                remove(ROOT / bpath)
            elif act == "BOTH":
                act = "MERGED" if merge(bpath, b, r, snapshot(bpath, live, kind, "base"), kind) else "CONFLICT"
                if act == "MERGED":
                    write(snapshot(bpath, live, kind, "branch"), live, kind)   # the rig runs the merged text too
                    restart |= bpath.startswith(RESTART_KINDS)
        print(f"{act:9} {bpath:{width}}  {str(live).replace(str(HOME), '~')}")
        if act.startswith("OUT"):
            outs.append(bpath)
            restart |= bpath.startswith(RESTART_KINDS)
        elif act.startswith("IN") or act in ("MERGED", "BOTH"):
            ins.append(bpath)
        elif act in ("CONFLICT", "NOBASE"):
            conflicts.append(bpath)
    if cmd == "sync" and not (ins or conflicts):
        dirty = git("status", "--porcelain", "--", "rig-sdlc").stdout.decode().strip()
        if dirty:
            print("NEXT: rig-sdlc/ has uncommitted changes; commit and push them, then run sync again.")
        else:
            BASE_FILE.parent.mkdir(exist_ok=True)
            BASE_FILE.write_text(git("rev-parse", "HEAD").stdout.decode())
            print("base =", BASE_FILE.read_text().strip())
    if not (ins or outs or conflicts):
        print("in sync")
        return 0
    if cmd == "sync":
        if conflicts:
            print("CONFLICT: resolve the <<<<<<< branch / >>>>>>> rig markers in the branch file, then run sync --prefer branch.")
            print("NOBASE (first run): run sync --prefer branch, or --prefer rig if the rig's copies are the newer.")
        elif ins:
            print("NEXT: commit and push these paths (Rig SDLC: subject), then run sync again:")
            print("  " + " ".join(ins))
        if restart:
            print("RESTART: skills, agents or settings changed on the rig; restart Claude Code.")
    return 2 if conflicts else 1


def plan(prefer):
    rows = []
    for bpath, live, kind in items():
        b = snapshot(bpath, live, kind, "branch")
        r = snapshot(bpath, live, kind, "rig")
        if b is None and r is None:
            continue
        rows.append((bpath, live, kind, decide(b, r, snapshot(bpath, live, kind, "base"), prefer)))
    return rows


if __name__ == "__main__":
    sys.exit(main())
