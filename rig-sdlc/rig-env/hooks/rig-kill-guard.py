#!/usr/bin/env python3
"""PreToolUse guard for Bash: blocks `pkill -f`, `killall` and `pgrep -f` feeding a kill.
The tool runs every command as `bash -c '<whole command>'`, so a -f pattern also matches the
tool's own shell and kills or lists it (exit 144). Rule 9 of the rig-round skill says the same
in prose. Exit 2 blocks the call and the message goes to Claude. Bypass for one session: OHU_KILL_OFF=1.
Read-only `pgrep -af <pattern>` listings stay allowed.
Scope: only sessions whose cwd or project dir is under ~/Coding/StudioProjects; everywhere else it exits 0."""
import json, os, re, sys

if os.environ.get("OHU_KILL_OFF"):
    sys.exit(0)

try:
    data = json.load(sys.stdin)
except ValueError:
    sys.exit(0)
if data.get("tool_name") != "Bash":
    sys.exit(0)

ROOT = os.path.realpath(os.path.expanduser("~/Coding/StudioProjects"))
def inside(path):
    if not path:
        return False
    path = os.path.realpath(path)
    return path == ROOT or path.startswith(ROOT + os.sep)
if not (inside(data.get("cwd")) or inside(os.environ.get("CLAUDE_PROJECT_DIR"))):
    sys.exit(0)
cmd = (data.get("tool_input") or {}).get("command", "")

# Drop heredoc bodies and quoted text, so notes that merely mention a command are not blocked.
text = re.sub(r"<<-?\s*'?\"?(\w+)'?\"?.*?\n.*?\n\s*\1\b", " ", cmd, flags=re.S)
text = re.sub(r"'[^']*'|\"[^\"]*\"", "''", text)

START = r"(?:^|[;&|(`]|\$\()\s*(?:sudo\s+)?"
FFLAG = r"(?:-[A-Za-z0-9]*f[A-Za-z0-9]*|--full)"
problems = []
if re.search(START + r"pkill\b[^;&|\n]*?\s" + FFLAG + r"(?:\s|$)", text, re.M):
    problems.append("`pkill -f`")
if re.search(START + r"killall\b", text, re.M):
    problems.append("`killall`")
pg = START + r"pgrep\b[^;&|\n)`]*?\s" + FFLAG + r"(?:\s|$)"
if re.search(pg, text, re.M) and re.search(r"\bkill\b|xargs", text):
    problems.append("`pgrep -f` feeding a kill")

if problems:
    sys.stderr.write(
        "rig-kill-guard: " + ", ".join(problems) + " matches the tool's own `bash -c` shell and ends it (exit 144). "
        "Kill by pid instead: the pid files the helpers write (`kill $(cat <capture>.pid)`, `uncap`), the pid you recorded "
        "from `$!`, or the ids in $EV/armed.txt. Wait on a pid with `kill -0 <pid>`. To look, `pgrep -af <pattern>` "
        "without a kill is fine. One-session bypass: OHU_KILL_OFF=1.\n")
    sys.exit(2)
sys.exit(0)
