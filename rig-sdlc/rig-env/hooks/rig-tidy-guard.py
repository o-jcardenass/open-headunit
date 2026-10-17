#!/usr/bin/env python3
"""PreToolUse guard: keeps ~/Coding/StudioProjects/ tidy (see headunit-revived/.claude/CLAUDE.md,
section "Where files go on this machine"). Exit 2 blocks the call and the message goes to Claude.
Bypass for one session: OHU_TIDY_OFF=1. Paths still on the old layout: rig-tidy-grandfathered.txt."""
import json, os, re, sys

ROOT = os.path.expanduser("~/Coding/StudioProjects")
SCRIPTS = ROOT + "/rig-toolkit"
OK_TOP = {"headunit-revived", "code-researchs", "wireless-helper", "rig-toolkit",
          "rig-data", "worktrees", "StudioProjects.code-workspace"}
BULKY_EXT = (".apk", ".logcat", ".zip", ".pcap", ".png", ".mp4", ".dump", ".jpg", ".xz", ".tar", ".gz")
BULKY_DIRS = ("results", "evidence", "apks", "out", "extracted", "tools")

def die(msg):
    sys.stderr.write("rig-tidy-guard: " + msg + "\nLayout: worktrees -> StudioProjects/worktrees/<topic>__<role>__<sha8>__<date> "
                     "(use rig-toolkit/wt-new.sh); captures/APKs -> StudioProjects/rig-data/{rounds,apks,scratch}/. "
                     "Check what is safe to delete: rig-toolkit/studio-clean.sh status.\n")
    sys.exit(2)

def grandfathered(text):
    try:
        with open(os.path.expanduser("~/.claude/hooks/rig-tidy-grandfathered.txt")) as f:
            return any(l.strip() and not l.startswith("#") and l.strip() in text for l in f)
    except OSError:
        return False


HOME = os.path.expanduser("~")
# Names that look like OHU round output; never allowed directly under $HOME (use StudioProjects/rig-data/).
HOME_STRAY = re.compile(r"(round|captures?|logcat|(^|[-_])pr-?\d{3,4}|stage[-_]?[A-Za-z0-9]?$|^rig-|zipwork|build-speed|evidence|btsnoop|decompile|\.apk$|\.logcat$)", re.I)

def check_home(p):
    """Block round-looking files/folders created directly under $HOME."""
    p = os.path.abspath(os.path.expanduser(p))
    if not p.startswith(HOME + "/") or p.startswith(ROOT + "/"):
        return
    top = p[len(HOME) + 1:].split("/")[0]
    if top.startswith(".") or top in ("Coding", "Downloads", "Documents", "Desktop", "Android", "Pictures", "Music", "Videos"):
        return
    rel = p[len(HOME) + 1:]
    if HOME_STRAY.search(top) or re.search(r"logcat|captures?\.zip$|\.apk$", rel, re.I):
        die(f"'{rel}' under {HOME} looks like OHU round output. Put it under ~/Coding/StudioProjects/rig-data/rounds/<topic>-round<N>/ "
            "(captures, zips, staging) or rig-data/scratch/ (anything else).")

TOOLKITS = (ROOT + "/rig-toolkit", ROOT + "/hur-wifi-test-scripts")

def tools_md():
    for t in TOOLKITS:
        try:
            with open(t + "/TOOLS.md") as f:
                return f.read()
        except OSError:
            pass
    return ""

def need_inventory(path):
    """A top-level script in the toolkit must already be listed in TOOLS.md."""
    for t in TOOLKITS:
        if os.path.dirname(path) == t and path.endswith((".sh", ".py")):
            name = os.path.basename(path)
            if name not in tools_md():
                die(f"'{name}' is a new toolkit script but is not in TOOLS.md. Add its entry first (Does / Use when / Usage / "
                    "Inputs / Needs / Writes / Notes, plus a row in 'Pick a tool'). Persistent non-standard scripts go in the "
                    "'Non-standard (persistent)' section with the round that owns them. One-round scripts belong in "
                    "rig-data/rounds/<round>/scripts/ and need no entry.")

def check_path(p):
    p = os.path.abspath(os.path.expanduser(p))
    if not p.startswith(ROOT + "/"):
        return
    need_inventory(p)
    if grandfathered(p):
        return
    rel = p[len(ROOT) + 1:]
    top = rel.split("/")[0]
    if top not in OK_TOP:
        die(f"'{top}' is not an allowed top-level folder in StudioProjects/ (writing {p}).")
    if p.startswith(SCRIPTS + "/"):
        sub = p[len(SCRIPTS) + 1:]
        first = sub.split("/")[0]
        if first in BULKY_DIRS or (sub.lower().endswith(BULKY_EXT)) or (os.path.isdir(os.path.join(SCRIPTS, first)) and "/" in sub and not sub.endswith((".sh", ".py", ".md"))):
            die(f"{p} is round data inside the scripts dir; write it under rig-data/ instead.")

def main():
    if os.environ.get("OHU_TIDY_OFF") == "1":
        return
    d = json.load(sys.stdin)
    t, i = d.get("tool_name"), d.get("tool_input", {})
    if t in ("Write", "Edit", "NotebookEdit"):
        fp = i.get("file_path") or i.get("notebook_path") or ""
        check_path(fp)
        check_home(fp)
        return
    if t != "Bash":
        return
    c = i.get("command", "")
    if grandfathered(c) and "git worktree add" not in c:
        return
    # 1) new worktrees only under worktrees/
    for m in re.finditer(r"git\s+(?:-C\s+\S+\s+)?worktree\s+add\s+(?:-\S+\s+)*(?:-b\s+\S+\s+)?(\S+)", c):
        tgt = os.path.abspath(os.path.expanduser(m.group(1).strip("'\"")))
        if tgt.startswith(ROOT + "/") and not tgt.startswith(ROOT + "/worktrees/"):
            die(f"git worktree add target {tgt} is outside StudioProjects/worktrees/. Use rig-toolkit/wt-new.sh.")
    # 2) new top-level folders in StudioProjects
    for m in re.finditer(r"\b(?:mkdir(?:\s+-\S+)*|mv\s+\S+|cp\s+(?:-\S+\s+)*\S+)\s+['\"]?(?:~|\$HOME|/home/oscar)/Coding/StudioProjects/([^/\s'\"]+)", c):
        if m.group(1) not in OK_TOP:
            die(f"'{m.group(1)}' is not an allowed top-level folder in StudioProjects/.")
    # 2b) new toolkit scripts created from the shell must be inventoried too
    wr0 = r"(?:>>?|\btee\b|\bcp\b|\bmv\b|\binstall\b)"
    for m in re.finditer(r"(?:rig-toolkit|hur-wifi-test-scripts)/([A-Za-z0-9_.-]+\.(?:sh|py))\b", c):
        seg = c[max(0, m.start() - 120):m.end() + 40]
        if re.search(wr0, seg):
            need_inventory(os.path.join(ROOT, os.path.dirname(m.group(0)), m.group(1)))
    # 2c) round-looking output written straight into $HOME
    wrh = r"(?:>>?|\btee\b|\bmkdir\b|\bcp\b|\bmv\b|\bunzip\b|\bzip\b|\badb\b[^|;&]*\bpull\b|\bcurl\b[^|;&]*\s-o\b|\bgh\b[^|;&]*\bdownload\b|-o\s|-d\s)"
    for m in re.finditer(r"(?:~|\$HOME|\$\{HOME\}|/home/oscar)/([A-Za-z0-9._/-]+)", c):
        seg = c[max(0, m.start() - 120):m.end() + 40]
        if re.search(wrh, seg):
            check_home(os.path.join(HOME, m.group(1)))
    # 3) round data written into the scripts dir
    wr = r"(?:>>?|\btee\b|\bmkdir\b|\bcp\b|\bmv\b|\bunzip\b|\badb\b[^|;&]*\bpull\b|\bzip\b|\bcurl\b[^|;&]*\s-o\b|-o\s)"
    pat = re.compile(r"hur-wifi-test-scripts/(?:results|evidence|apks|out|extracted|tools)\b|hur-wifi-test-scripts/\S+\.(?:apk|logcat|zip|pcap|dump)\b")
    if re.search(wr, c):
        for m in pat.finditer(c):
            seg = c[max(0, m.start() - 160):m.end() + 160]
            if re.search(wr, seg) and not grandfathered(seg):
                die("this command writes round data into hur-wifi-test-scripts/ (" + m.group(0) + "); use rig-data/ instead.")

try:
    main()
except SystemExit:
    raise
except Exception as e:  # never block on a guard bug
    sys.stderr.write(f"rig-tidy-guard: internal error ignored: {e}\n")
