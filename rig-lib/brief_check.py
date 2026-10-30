#!/usr/bin/env python3
"""Check a rig brief before it can reach the rig. Every brief script is otherwise first run on the rig.

  brief_check.py lint BRIEF [--repo DIR]     the rules below; exit 1 on any error
  brief_check.py smoke BRIEF [--repo DIR]    run every named host script under a stub adb, twice (waits
                                             succeed, waits time out); fail on a hang, an orphaned process,
                                             a command not found or a syntax error; on success stamp the
                                             brief's content hash in <git-common-dir>/brief-smoke/
  brief_check.py check BRIEF                 lint, then smoke. Run this before asking for approval.
  brief_check.py stamped BRIEF [--repo DIR]  exit 0 if this exact content passed smoke (commit-guard asks)
  brief_check.py same-body OLD NEW           exit 0 if NEW differs from OLD only in SHAs and its header

Lint also checks: a shell block that drives the rig must be named (an unnamed one is never extracted);
a run's script takes no arguments (rig_run.sh passes none: read RUN); and every log string a script or
grade greps for must be in app/src of at least one `candidate` named in the rig-requires block (a line
that greps a phone or system log ends with `# phone` or `# system`; `# not-literal` skips one line).

A named script is a fenced block with a **name.sh** (or **`name.sh`**) line above it, the same
convention extract_brief_blocks.py uses on the rig. Blocks named *lib.sh or *_env.sh are sourced
before every other script. Blocks whose first line is #!/system/bin/sh run on a device and only get
the syntax check.
"""
import hashlib, os, re, shutil, subprocess, sys, tempfile, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import rig_requires  # noqa: E402

NAME = re.compile(r"^\*\*`?([\w.+-]+\.(?:sh|py|bash))`?\*\*\s*$")
LIB_NAME = re.compile(r"(?:^|[_-])(?:lib|env)\.sh$|^lib[\w-]*\.sh$")
LIB_HELPERS = re.compile(r"^(wait\w*|cap_\w+|th_\w+|ph_(?:on|off|radios?)|radio\w*|bonds?|bonded|kill_?tree|rl_\w+)$")
FUNC_DEF = re.compile(r"^\s*(?:function\s+)?([A-Za-z_][\w]*)\s*\(\)")
PLACEHOLDER = re.compile(r"(?<![<\w])<[A-Z][A-Z0-9_]*(?: [^<>\n]*)?>")
STUDIO = ("/home/oscar/Coding/StudioProjects", "~/Coding/StudioProjects", "$HOME/Coding/StudioProjects")
SMOKE_CAP_S = 25


def fences(text):
    """[(lang, body, line)] for every fenced block, named or not."""
    out, lines, i = [], text.splitlines(), 0
    while i < len(lines):
        if lines[i].lstrip().startswith("```"):
            fence = lines[i].lstrip(); start = i
            ticks = len(fence) - len(fence.lstrip("`"))
            body, i = [], i + 1
            while i < len(lines) and not (lines[i].lstrip().startswith("`" * ticks) and lines[i].strip("` ") == ""):
                body.append(lines[i]); i += 1
            out.append((fence.strip("`").strip(), "\n".join(body), start + 1))
        i += 1
    return out


RIG_CODE = re.compile(r"\badb\b|\brl_\w+|\$EV\b|\$\{EV\}|\$RIG_|\bcap_(?:start|stop|on|off)\b|\bmark\b|\bsvc\s+(?:wifi|bluetooth)")
POSITIONAL = re.compile(r"\$(?:[1-9#@*]|\{[1-9#@*])")
GREP_CALL = re.compile(r"\b(?:rl_cnt\s+\S+\s+\S+\s+\S+|rl_wait_line\s+\S+|rl_wait_count\s+\S+\s+\S+|"
                       r"grep\s+(?:-[A-Za-z]+\s+)*)\s*(['\"])(.+?)\1")


def log_strings(text):
    """[(script, line, string, tag)] for every literal a script or grade greps for."""
    out = []
    for name, _, body, at in blocks(text):
        if name.endswith(".py"):
            continue
        for n, raw in enumerate(body.splitlines(), at + 2):
            tag = next((t for t in ("phone", "system", "not-literal") if re.search(rf"#\s*{t}\b", raw)), "")
            for m in GREP_CALL.finditer(raw):
                lit = m.group(2)
                if "$" in lit or len(lit) < 8 or "AutomationMarker" in lit:
                    continue
                if re.search(r"[\\^$*+?()\[\]{}|]", lit) and not re.search(r"\bgrep\b[^'\"]*-\w*F", raw):
                    lit = re.split(r"[\\^$*+?()\[\]{}|.]", lit)[0]
                if len(lit) >= 8:
                    out.append((name, n, lit, tag))
    return out


def check_strings(text, repo):
    req, _ = rig_requires.parse(text)
    errs, notes = [], []
    if not req["candidates"] or not repo:
        return errs, notes
    have = {}
    for arm, sha in req["candidates"].items():
        if subprocess.run(["git", "-C", str(repo), "cat-file", "-e", f"{sha}^{{commit}}"], capture_output=True).returncode:
            errs.append(f"candidate {arm} {sha} is not in this clone: git fetch fork, then check again")
        else:
            have[arm] = sha
    seen = set()
    for name, n, lit, tag in log_strings(text):
        if tag or lit in seen or not have:
            continue
        seen.add(lit)
        hits = [arm for arm, sha in have.items()
                if subprocess.run(["git", "-C", str(repo), "grep", "-q", "-F", lit, sha, "--", "app/src/main"],
                                  capture_output=True).returncode == 0]
        if not hits:
            errs.append(f"{name}:{n}: '{lit}' is in no candidate's app/src/main ({', '.join(have)}); fix the string, "
                        "or end the line with # phone or # system if another log prints it")
        elif len(hits) < len(have):
            notes.append(f"{name}:{n}: '{lit}' only exists on {', '.join(hits)}; it reads 0 on "
                         f"{', '.join(a for a in have if a not in hits)}, so grade that arm on something else")
    return errs, notes


def blocks(text):
    """[(name, lang, body, line)] for every named block. Fences are tracked line by line."""
    out, lines, i, pending = [], text.splitlines(), 0, None
    while i < len(lines):
        m = NAME.match(lines[i].strip())
        if m:
            pending = (m.group(1), i + 1)
        elif lines[i].lstrip().startswith("```"):
            fence = lines[i].lstrip()
            ticks = len(fence) - len(fence.lstrip("`"))
            lang = fence.strip("`").strip()
            body, i = [], i + 1
            while i < len(lines) and not (lines[i].lstrip().startswith("`" * ticks) and lines[i].strip("` ") == ""):
                body.append(lines[i]); i += 1
            if pending and pending[1] >= i - len(body) - 4:   # the name sits just above the fence
                out.append((pending[0], lang, "\n".join(body) + "\n", pending[1]))
            pending = None
        elif lines[i].strip():
            if pending and i - pending[1] > 3:
                pending = None
        i += 1
    return out


def is_device(body):
    return body.lstrip().startswith("#!/system/bin/sh")


def unquote(line):
    """Blank out quoted text and comments so the shell rules read only code."""
    s = re.sub(r"'[^']*'", "'Q'", line)
    s = re.sub(r'"(?:[^"\\]|\\.)*"', '"Q"', s)
    return re.sub(r"(^|\s)#.*$", r"\1", s)


def lint(text, repo=None):
    errs = []
    req, problems = rig_requires.parse(text)
    errs += [f"rig-requires: {p}" for p in problems]
    if req["lib"] and repo:
        r = subprocess.run(["git", "-C", str(repo), "cat-file", "-e", f"{req['lib']}:rig-lib/rig_lib.sh"],
                           capture_output=True)
        if r.returncode:
            errs.append(f"rig-requires: lib {req['lib']} has no rig-lib/rig_lib.sh on this branch")
    headings = [l for l in text.splitlines() if re.match(r"^#{2,4} ", l)]
    named = {n for n, *_ in blocks(text)}
    for r in req["runs"]:
        if not any(re.search(rf"(?<![\w-]){re.escape(r['id'])}(?![\w-])", h) for h in headings):
            errs.append(f"rig-requires: run {r['id']} has no heading in the brief")
        for k in ("script", "grade"):
            if r.get(k) and r[k] not in named:
                errs.append(f"rig-requires: run {r['id']} {k}={r[k]} is not a named block (**{r[k]}** above a fence)")
    named_lines = {at for *_, at in blocks(text)}
    for lang, body, at in fences(text):
        if lang in ("bash", "sh", "shell") and not any(0 < at - nl <= 4 for nl in named_lines) and RIG_CODE.search(body):
            errs.append(f"line {at}: an unnamed {lang} block that drives the rig; put **name.sh** above it so "
                        "rig_session.py extracts it, or fence it as ```text if it is only an example")
    scripts = {r.get("script") for r in req["runs"]}
    for name, lang, body, at in blocks(text):
        where = f"{name} (line {at})"
        if name in scripts and not name.endswith(".py"):
            infunc, depth, opened = False, 0, False
            for n, raw in enumerate(body.splitlines(), at + 2):
                code = unquote(raw)
                if FUNC_DEF.match(code):
                    infunc, depth, opened = True, 0, False
                if not infunc and POSITIONAL.search(re.sub(r"(^|\s)#.*$", "", re.sub(r"'[^']*'", "''", raw))):
                    errs.append(f"{where}:{n}: reads a positional argument, but rig_run.sh starts a run's script "
                                "with none; use $RUN or a variable the script sets")
                if infunc:
                    depth += code.count("{") - code.count("}")
                    opened = opened or "{" in code
                    if opened and depth <= 0:
                        infunc = False
        if name.endswith(".py"):
            r = subprocess.run([sys.executable, "-c", "import ast,sys; ast.parse(sys.stdin.read())"],
                               input=body, capture_output=True, text=True)
            if r.returncode:
                errs.append(f"{where}: python syntax error: {r.stderr.strip().splitlines()[-1]}")
            continue
        r = subprocess.run(["bash", "-n"], input=body, capture_output=True, text=True)
        if r.returncode:
            errs.append(f"{where}: bash -n: {r.stderr.strip().splitlines()[0]}")
        for n, raw in enumerate(body.splitlines(), at + 2):
            if PLACEHOLDER.search(raw) and not raw.lstrip().startswith("#"):
                errs.append(f"{where}:{n}: placeholder {PLACEHOLDER.search(raw).group(0)} in a script; "
                            "give the value or read it into a variable")
        if is_device(body):
            continue
        for n, raw in enumerate(body.splitlines(), at + 2):
            code = unquote(raw)
            m = FUNC_DEF.match(code)
            if m and LIB_HELPERS.match(m.group(1)):
                errs.append(f"{where}:{n}: defines {m.group(1)}(); use rig-lib instead "
                            "(rl_wait_line, rl_cap_start, rl_radio, rl_hold_off, rl_bonded, rl_thermal_gate)")
            if re.search(r"(?<![-.\w])wait\b(?!\s+[\"'])", code):
                errs.append(f"{where}:{n}: `wait` without a quoted pid waits for every job, captures included; "
                            "stop jobs with rl_stop or wait \"$PID\"")
            if re.search(r"(?<![&>|<])&(?![&>])", code):
                errs.append(f"{where}:{n}: untracked background job; start it with rl_bg or rl_cap_start "
                            "so rig_run.sh and the EXIT trap stop it")
            if re.search(r"\bp(?:grep|kill)\b[^|;&]*\s-\w*f", code):
                errs.append(f"{where}:{n}: pgrep/pkill -f matches its own shell; track the pid with rl_bg")
    serr, _ = check_strings(text, repo)
    return errs + serr


def brief_cost(text):
    """What the round asks of the operator and of the rig host's context, so the author sees it before the rig."""
    req, _ = rig_requires.parse(text)
    hands = [r["id"] for r in req["runs"] if r.get("hand")]
    out = [f"cost: {len(req['operator'])} operator confirmations, {len(hands)} hand steps"
           + (f" ({', '.join(hands)})" if hands else "")
           + f", ~{len(text) // 4000}k tokens if the host reads the whole brief (it reads `rig_session.py view` instead)"]
    if len(hands) > 1:
        out.append("cost: more than one hand step; each one is an operator interruption mid-round, so add a verb where you can")
    return out


def git_common_dir(repo):
    r = subprocess.run(["git", "-C", str(repo), "rev-parse", "--path-format=absolute", "--git-common-dir"],
                       capture_output=True, text=True)
    return Path(r.stdout.strip()) if r.returncode == 0 else None


def stamp_path(repo, data):
    common = git_common_dir(repo)
    return common / "brief-smoke" / hashlib.sha256(data).hexdigest() if common else None


def lib_source(text, repo):
    req, _ = rig_requires.parse(text)
    if req["lib"] and repo:
        r = subprocess.run(["git", "-C", str(repo), "show", f"{req['lib']}:rig-lib/rig_lib.sh"],
                           capture_output=True, text=True)
        if r.returncode == 0:
            return r.stdout, f"rig-lib at {req['lib']}"
    return (HERE / "rig_lib.sh").read_text(), "rig-lib from this checkout (the pin did not resolve)"


STUB_ADB = ("#!/bin/sh\n# smoke stub: every device answers nothing, and a streaming logcat streams forever like the real one\n"
            "case \" $* \" in *\" logcat \"*) case \" $* \" in *\" -c \"*|*\" -d \"*|*\" -G \"*|*\" -g \"*) exit 0 ;; esac\n"
            "  exec {sleep} 600 ;; esac\nexit 0\n")
STUB_STDBUF = "#!/bin/sh\nwhile [ $# -gt 0 ]; do case \"$1\" in -*) shift ;; *) break ;; esac; done\nexec \"$@\"\n"
STUB_TOOL = ("#!/bin/sh\n# smoke stub of a rig-toolkit script: watchers block like the real ones, the rest succeed\n"
             "case \"$1\" in watch|monitor|follow) exec {sleep} 600 ;; esac\nexit 0\n")
STUB_DEVICES = """RIG_TOOLKIT={tk}; RIG_DATA={data}; RIG_LOCK={lock}; HUR_PKG=com.andrerinas.headunitrevived
D_HU=SMOKEHU; D_POCO=SMOKEPOCO; D_MOTO=SMOKEMOTO; D_HP=SMOKEHP; D_SAM=SMOKESAM; D_T230=SMOKESAM
rig_check_devices() {{ return 0; }}
rig_marker() {{ return 0; }}
"""


def smoke(text, repo):
    named = blocks(text)
    libs = [(n, b) for n, l, b, _ in named if n.endswith((".sh", ".bash")) and LIB_NAME.search(n) and not is_device(b)]
    runs = [(n, b) for n, l, b, _ in named if n.endswith((".sh", ".bash")) and not LIB_NAME.search(n) and not is_device(b)]
    real_sleep = shutil.which("sleep") or "/bin/sleep"
    fails, notes = [], []
    lib_text, lib_note = lib_source(text, repo)
    notes.append(lib_note)
    for n, l, b, _ in named:
        if n.endswith(".py"):
            r = subprocess.run([sys.executable, "-c", "import ast,sys; ast.parse(sys.stdin.read())"],
                               input=b, capture_output=True, text=True)
            if r.returncode:
                fails.append(f"{n}: python syntax error")
    with tempfile.TemporaryDirectory(prefix="brief-smoke-") as tmp:
        t = Path(tmp)
        studio, binp = t / "studio", t / "bin"
        tk, data = studio / "rig-toolkit", studio / "rig-data"
        for d in (binp, tk, data / "rounds", t / "home", t / "work"):
            d.mkdir(parents=True, exist_ok=True)

        def put(path, body, mode=0o755):
            path.write_text(body); path.chmod(mode)
        put(binp / "adb", STUB_ADB.format(sleep=real_sleep))
        put(binp / "stdbuf", STUB_STDBUF)
        put(binp / "sleep", f"#!/bin/sh\nexec {real_sleep} 0.01\n")
        put(t / "rig_lib.sh", lib_text, 0o644)
        put(tk / "rig_devices.sh", STUB_DEVICES.format(tk=tk, data=data, lock=t / "lock"), 0o644)
        for tool in set(re.findall(r"(?:\$\{?RIG_TOOLKIT\}?|rig-toolkit)/([\w.-]+)", text)) - {"rig_devices.sh"}:
            put(tk / tool, STUB_TOOL.format(sleep=real_sleep))

        def localise(body):
            for s in STUDIO:
                body = body.replace(s, str(studio))
            return body
        for n, b in libs + runs:
            put(t / "work" / n, localise(b), 0o644)
        env = dict(os.environ, PATH=f"{binp}:{tk}:{os.environ['PATH']}", HOME=str(t / "home"),
                   RIG_TOOLKIT=str(tk), RIG_DATA=str(data), RIG_LOCK=str(t / "lock"),
                   EV=str(data / "rounds" / "smoke-round1"), RL_EV=str(data / "rounds" / "smoke-round1"))
        for n, _ in runs:
            for mode in ("ok", "timeout"):
                runner = t / "work" / f".runner-{n}-{mode}"
                runner.write_text(f"source '{t / 'rig_lib.sh'}'\nrl_init\n"
                                  + "".join(f"source '{t / 'work' / ln}'\n" for ln, _ in libs)
                                  + f"source '{t / 'work' / n}'\n")
                e = dict(env, RUN=n.rsplit(".", 1)[0], RL_SMOKE=mode)
                p = subprocess.Popen(["bash", str(runner)], cwd=t / "work", env=e, stdin=subprocess.DEVNULL,
                                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                                     start_new_session=True)
                try:
                    out, _ = p.communicate(timeout=SMOKE_CAP_S)
                    hung = False
                except subprocess.TimeoutExpired:
                    hung = True
                    os.killpg(p.pid, 9)
                    out, _ = p.communicate()
                time.sleep(0.3)
                left = [l for l in subprocess.run(["ps", "-e", "-o", "pid=,sid=,args="], capture_output=True,
                                                  text=True).stdout.splitlines()
                        if len(l.split()) > 2 and l.split()[1] == str(p.pid)]
                for l in left:
                    try:
                        os.kill(int(l.split()[0]), 9)
                    except OSError:
                        pass
                tag = f"{n} (waits {'succeed' if mode == 'ok' else 'time out'})"
                tail = " | ".join(out.strip().splitlines()[-3:])
                if hung:
                    fails.append(f"{tag}: HANG, did not finish in {SMOKE_CAP_S}s under a stub adb. Last output: {tail}")
                if left and not hung:
                    fails.append(f"{tag}: ORPHAN, left running after the script ended: "
                                 + "; ".join(" ".join(l.split()[2:])[:80] for l in left))
                for bad in sorted({l.strip() for l in out.splitlines()
                                   if "command not found" in l or "syntax error" in l}):
                    fails.append(f"{tag}: {bad[:160]}")
        notes.append(f"{len(runs)} scripts, {len(libs)} sourced libraries, 2 passes each")
    return fails, notes


def body_key(text):
    lines = text.splitlines()
    first = next((i for i, l in enumerate(lines) if l.startswith("## ")), 0)
    keep = [l for l in lines[first:] if not re.match(r"^\s*lib\s+[0-9a-f]{7,40}\s*$", l)]
    return re.sub(r"\b[0-9a-f]{7,40}\b", "<sha>", "\n".join(keep)).strip()


def main(argv):
    if len(argv) < 3:
        print(__doc__); return 64
    cmd, args = argv[1], argv[2:]
    repo = None
    if "--repo" in args:
        i = args.index("--repo"); repo = Path(args[i + 1]); args = args[:i] + args[i + 2:]
    if cmd == "same-body":
        a, b = (Path(x).read_text(errors="replace") for x in args[:2])
        same = body_key(a) == body_key(b)
        print("same body: only SHAs or the header changed" if same else "BODY CHANGED: the brief needs a new approval")
        return 0 if same else 1
    path = Path(args[0])
    data = path.read_bytes()
    text = data.decode(errors="replace")
    repo = repo or path.resolve().parent
    if cmd == "stamped":
        sp = stamp_path(repo, data)
        return 0 if sp and sp.exists() else 1
    if cmd in ("lint", "check"):
        errs = lint(text, repo)
        for e in errs:
            print(f"LINT {path.name}: {e}")
        if errs:
            print(f"BRIEF CHECK FAILED: {len(errs)} lint errors"); return 1
        print(f"lint ok: {path.name}")
        for line in brief_cost(text):
            print(line)
        for nt in check_strings(text, repo)[1]:
            print(f"note: {nt}")
        if cmd == "lint":
            return 0
    if cmd in ("smoke", "check"):
        fails, notes = smoke(text, repo)
        for nt in notes:
            print(f"smoke: {nt}")
        for f in fails:
            print(f"SMOKE {path.name}: {f}")
        if fails:
            print(f"BRIEF CHECK FAILED: {len(fails)} smoke failures"); return 1
        sp = stamp_path(repo, data) if cmd == "check" else None   # a stamp means lint and smoke both passed
        if sp:
            sp.parent.mkdir(parents=True, exist_ok=True)
            sp.write_text(f"{time.strftime('%F %T')} {path.name} passed lint and smoke\n")
        print(f"BRIEF CHECK OK: {path.name}" + (f" (stamped {sp.name[:12]})" if sp else ""))
        return 0
    print(__doc__); return 64


if __name__ == "__main__":
    sys.exit(main(sys.argv))
