#!/usr/bin/env python3
"""Run a round from its brief without the host: requirements, every run in order, grades, stop rules.

  rig_session.py run --ev EV BRIEF       run the round; resumable (a run with a grade is skipped)
  rig_session.py view BRIEF [RUN]        the round in one screen, or one run's section of the brief
  rig_session.py status --ev EV          one line per run

The host launches `run` with run_in_background and arms one Monitor on EV/session.status for
`OPERATOR|ESCALATE|DONE`. Everything else stays in files, so the host's context grows by a few lines a
run. The session stops (exit 3) only for what neither it nor rig_run.sh can settle: a requirement that
needs the operator, a held lock, or a grade script that is broken. Rerun `run` after the fix.
A run that times out or stalls is retried once and then graded INCONCLUSIVE, with no operator step.
"""
import json, os, re, subprocess, sys, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import rig_requires  # noqa: E402
from brief_check import blocks, LIB_NAME, is_device  # noqa: E402


def say(ev, line, operator=False):
    stamp = time.strftime("%T")
    with open(Path(ev, "session.status"), "a") as f:
        f.write(f"{stamp} {line}\n")
    with open(Path(ev).parent / "PROGRESS.log", "a") as f:
        f.write(f"{stamp} session {Path(ev).name} {line}\n")
    if operator:
        with open(Path(ev, "interventions.txt"), "a") as f:
            f.write(f"{stamp} {line}\n")


def escalate(ev, why):
    say(ev, f"ESCALATE {why}", operator=True)
    return 3


def grade_run(ev, rl, scripts, libs, run, exit_code, log):
    """Run the brief's grade script; returns (verdict, reason, conds)."""
    g = scripts / run["grade"]
    src = "".join(f"source '{scripts / l}'\n" for l in libs)
    env = dict(os.environ, EV=str(ev), RL_EV=str(ev), RUN=run["id"], RUN_EXIT=str(exit_code), RUN_LOG=str(log))
    try:
        r = subprocess.run(["bash", "-c", f"source '{rl / 'rig_lib.sh'}'\n{src}source '{g}'"],
                           capture_output=True, text=True, timeout=120, env=env, stdin=subprocess.DEVNULL)
        out = r.stdout
    except subprocess.TimeoutExpired:
        return None, "grade script ran over 120 s", []
    conds = [l for l in out.splitlines() if l.startswith("COND ")]
    verdicts = [l.split(" ", 2) for l in out.splitlines() if l.startswith("VERDICT ")]
    if verdicts:
        v = verdicts[-1]
        return v[1], (v[2] if len(v) > 2 else ""), conds
    if not conds:
        if exit_code:
            return "INCONCLUSIVE", f"the run exited {exit_code} and the grade printed no COND", conds
        return None, "grade script printed no COND or VERDICT line", conds
    failed = [c for c in conds if c.split()[2] == "FAIL"]
    if failed:
        return "FAIL", "; ".join(c.split(" ", 3)[3] for c in failed), conds
    return "PASS", f"{len(conds)}/{len(conds)} conditions", conds


def launch(ev, rl, scripts, libs, run):
    """One rig_run.sh launch, mirroring OPERATOR NOW and STALL lines into session.status. Returns (exit, log)."""
    cmd = [str(rl / "rig_run.sh"), run["id"], str(run["cap"]), str(scripts / run["script"])]
    for l in libs:
        cmd += ["--source", str(scripts / l)]
    env = dict(os.environ, EV=str(ev), RL_STALL_KILL="1")
    p = subprocess.Popen(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    first = p.stdout.readline()
    m = re.search(r"log=(\S+) status=(\S+)", first)
    log, status = (Path(m.group(1)), Path(m.group(2))) if m else (None, None)
    seen = {"log": 0, "status": 0}
    while True:
        done = p.poll() is not None
        for kind, path, pat in (("log", log, "OPERATOR NOW"), ("status", status, " STALL ")):
            if path and path.exists():
                with open(path, errors="replace") as f:
                    f.seek(seen[kind]); new = f.read(); seen[kind] = f.tell()
                for line in new.splitlines():
                    if pat in line:
                        say(ev, f"OPERATOR {run['id']}: {line.split('OPERATOR NOW', 1)[-1].strip(': ')}" if kind == "log"
                            else f"STALL {run['id']}: {line.split(' STALL ', 1)[1]}", operator=(kind == "log"))
        if done:
            break
        time.sleep(1)
    rest = p.stdout.read()
    if not m:
        say(ev, f"LAUNCH {run['id']} failed: {(first + rest).strip()[:200]}")
    return p.returncode, log


def cmd_run(ev, brief):
    ev = Path(ev).resolve(); ev.mkdir(parents=True, exist_ok=True)
    text = Path(brief).read_text(errors="replace")
    req, problems = rig_requires.parse(text)
    if problems:
        return escalate(ev, "brief block: " + "; ".join(problems))
    rl = ev / "rig-lib"
    if not (rl / "rig_run.sh").exists():
        repo = Path(brief).resolve().parent
        tar = subprocess.run(f"git -C '{repo}' archive {req['lib']} rig-lib | tar -x -C '{ev}'", shell=True,
                             capture_output=True, text=True)
        if tar.returncode or not (rl / "rig_run.sh").exists():
            return escalate(ev, f"cannot unpack rig-lib {req['lib']}: {tar.stderr.strip()[:200]}")
        say(ev, f"PINNED rig-lib {req['lib']}")
    scripts = ev / "scripts"; scripts.mkdir(exist_ok=True)
    for name, _, body, _ in blocks(text):
        (scripts / name).write_text(body)
    libs = [n for n, _, b, _ in blocks(text) if LIB_NAME.search(n) and not is_device(b)]
    names = {n for n, *_ in blocks(text)}
    for r in req["runs"]:
        for k in ("script", "grade"):
            if r.get(k) and r[k] not in names:
                return escalate(ev, f"run {r['id']} names {k}={r[k]}, which is not a named block of the brief")

    chk = subprocess.run([sys.executable, "-I", str(rl / "rig_requires.py"), "check", "--ev", str(ev), str(brief), "--fix"],
                         capture_output=True, text=True)
    (ev / "requirements.txt").write_text(chk.stdout)
    if chk.returncode:
        fails = [l.strip("- ").strip() for l in chk.stdout.splitlines() if l.startswith("  - ")]
        return escalate(ev, "requirements: " + "; ".join(fails))
    say(ev, "REQUIREMENTS OK")

    skipped = set()
    for run in req["runs"]:
        gj = ev / f"{run['id']}.grade.json"
        if gj.exists():
            continue
        if run["id"] in skipped and run.get("always") != "yes":
            gj.write_text(json.dumps({"id": run["id"], "verdict": "NOT_RUN", "reason": "stop rule"}))
            say(ev, f"GRADE {run['id']} NOT_RUN (stop rule)")
            continue
        if run.get("hand"):
            say(ev, f"OPERATOR {run['id']} next: {run['hand']}", operator=True)
        say(ev, f"RUN {run['id']} cap={run['cap']}s{' (point of the round)' if run.get('point') == 'yes' else ''}")
        attempts, verdict, reason, conds, code, log = 0, None, "", [], 0, None
        while attempts < 2:
            attempts += 1
            code, log = launch(ev, rl, scripts, libs, run)
            if code == 75:
                return escalate(ev, f"{run['id']}: the rig lock is held by another run (see {log or 'rig_run.sh output'})")
            if code == 78:
                return escalate(ev, f"{run['id']}: requirements.ok went stale; rerun the session")
            if code in (124, 125):
                say(ev, f"RETRY {run['id']} after {'timeout' if code == 124 else 'stall'} (attempt {attempts})")
                verdict, reason = "INCONCLUSIVE", f"rig: {'timeout' if code == 124 else 'stall'} on both attempts"
                continue
            verdict = None
            break
        if run.get("always") == "yes" and not run.get("grade"):
            verdict, reason = ("PASS" if code == 0 else "FAIL"), f"exit {code}"
        elif verdict is None:
            verdict, reason, conds = grade_run(ev, rl, scripts, libs, run, code, log)
            if verdict is None:
                return escalate(ev, f"{run['id']}: {reason}; the run's capture is kept, fix the grade and rerun")
        gj.write_text(json.dumps({"id": run["id"], "verdict": verdict, "reason": reason, "conds": conds,
                                  "attempts": attempts, "exit": code, "point": run.get("point") == "yes",
                                  "log": str(log) if log else None}, indent=1))
        say(ev, f"GRADE {run['id']} {verdict} {reason[:160]}")
        for s in req["stops"]:
            if s["after"] == run["id"] and verdict in s["on"]:
                ids = [r["id"] for r in req["runs"]]
                after = ids[ids.index(run["id"]) + 1:]
                skipped |= set(after if s["skip"] == "all" else s["skip"].split(","))
                say(ev, f"STOP RULE after {run['id']} {verdict}: skip {', '.join(sorted(skipped)) or 'nothing'}")
    return finish(ev, req)


def finish(ev, req):
    rows, counts = [], {}
    for run in req["runs"]:
        try:
            g = json.loads((ev / f"{run['id']}.grade.json").read_text())
        except OSError:
            continue
        counts[g["verdict"]] = counts.get(g["verdict"], 0) + 1
        rows.append(f"| {run['id']}{' (point)' if g.get('point') else ''} | **{g['verdict']}** | {g.get('reason', '')} | {g.get('attempts', '')} |")
        rows += [f"|  | {c.split()[2]} | {c.split(' ', 3)[3]} |  |" for c in g.get("conds", [])]
    try:
        n_ops = len(Path(ev, "interventions.txt").read_text().splitlines())
    except OSError:
        n_ops = 0
    Path(ev, "summary.md").write_text("| Run | Verdict | Reason or condition | Attempts |\n|---|---|---|---|\n"
                                      + "\n".join(rows) + f"\n\nOperator interventions: {n_ops}\n")
    say(ev, "DONE " + " ".join(f"{k.lower()}={v}" for k, v in sorted(counts.items())) + f" interventions={n_ops}")
    return 0


def cmd_view(brief, run_id=None):
    text = Path(brief).read_text(errors="replace")
    if run_id:
        lines, out, level = text.splitlines(), [], None
        for l in lines:
            m = re.match(r"^(#{2,4}) ", l)
            if m and level and len(m.group(1)) <= level:
                break
            if m and level is None and re.search(rf"(?<![\w-]){re.escape(run_id)}(?![\w-])", l):
                level = len(m.group(1))
            if level:
                out.append(l)
        print("\n".join(out) or f"no heading names {run_id}")
        return 0
    req, problems = rig_requires.parse(text)
    print(f"{Path(brief).name}: lib {req['lib']}")
    for p in problems:
        print(f"  PROBLEM {p}")
    for role, u in req["units"].items():
        print(f"  unit {role} " + " ".join(f"{k}={','.join(v) if isinstance(v, list) else v}" for k, v in u.items()))
    for r in req["runs"]:
        flags = " ".join(f"{k}={r[k]}" for k in ("point", "hand", "always") if r.get(k))
        print(f"  run {r['id']} cap={r['cap']}s script={r.get('script')} grade={r.get('grade', '-')} {flags}")
    for s in req["stops"]:
        print(f"  stop after {s['after']} on {','.join(s['on'])}: skip {s['skip']}")
    for o in req["operator"]:
        print(f"  operator: {o}")
    return 0


def cmd_status(ev):
    for g in sorted(Path(ev).glob("*.grade.json")):
        d = json.loads(g.read_text())
        print(f"{d['id']}: {d['verdict']} {d.get('reason', '')}")
    return 0


def main(argv):
    if len(argv) < 3:
        print(__doc__); return 64
    cmd, rest = argv[1], argv[2:]
    ev = None
    if "--ev" in rest:
        i = rest.index("--ev"); ev = rest[i + 1]; rest = rest[:i] + rest[i + 2:]
    if cmd == "run" and ev and len(rest) == 1:
        return cmd_run(ev, rest[0])
    if cmd == "view" and rest:
        return cmd_view(rest[0], rest[1] if len(rest) > 1 else None)
    if cmd == "status" and ev:
        return cmd_status(ev)
    print(__doc__); return 64


if __name__ == "__main__":
    sys.exit(main(sys.argv))
