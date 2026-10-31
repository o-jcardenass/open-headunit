#!/usr/bin/env python3
"""Run the wireless main smoke on the newest `main`, at will, and report what regressed.

  main_smoke.py [run] [--ref origin/main] [--apk APK]   fetch, pin, lint, build, run the session, summarise
  main_smoke.py render --sha SHA --out FILE             write main_smoke_brief.md pinned to SHA (no device)
  main_smoke.py summary --ev EV                         print the summary of a finished session again

D-HP (the USB host, with the dongle on its powered OTG Y-cable) is reached over wireless adb. `run` finds it at its
recorded address or by a sweep of the PC's subnet, and moves it to wireless adb itself when it is on its PC cable.
When it cannot reach D-HP, the smoke leaves M6 out and the summary says what to do.

`run` writes the pinned brief and every capture to rig-data/rounds/main-smoke-auto-round<yyyymmddHHMM>/, then
prints one line per run and writes summary.txt there. No model and no hand step: rig_session.py runs the brief.
Exit: 0 every run PASS (or a known FAIL), 1 a run FAILED, 2 a run was INCONCLUSIVE, UNTESTABLE or not run,
3 the session stopped for the operator, 4 the brief greps a line the commit no longer prints, 5 the build failed.
"""
import json, os, re, shutil, socket, subprocess, sys, threading, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
TRANSFER = HERE.parent
BRIEF = HERE / "main_smoke_brief.md"
STUDIO = Path(os.environ.get("RIG_STUDIO", Path.home() / "Coding/StudioProjects"))
TOOLKIT = Path(os.environ.get("RIG_TOOLKIT", STUDIO / "rig-toolkit"))
RIG_DATA = Path(os.environ.get("RIG_DATA", STUDIO / "rig-data"))
# Runs that FAIL on main for a known reason; a FAIL there is reported but does not fail the smoke.
KNOWN_FAIL = {"M8": "the station stand-down race after a force-stop (D-SAM), not fixed on main yet"}


def sh(cmd, **kw):
    return subprocess.run(cmd, shell=isinstance(cmd, str), text=True, capture_output=True, **kw)


def render(sha, out, skip_hp=False):
    text = BRIEF.read_text()
    if skip_hp:   # no D-HP this time: the session must not wait for it, so its lines and M6 go
        text = re.sub(r"(?m)^(?:run M6 .*|unit D-HP[@ ].*|unit \S+@M6 .*|hardware the USB dongle .*)\n", "", text)
    text, n1 = re.subn(r"(?m)^candidate M [0-9a-f]{7,40}$", f"candidate M {sha}", text)
    text, n2 = re.subn(r"(?m)^MAIN_SHA=\S+", f"MAIN_SHA={sha[:9]}", text)
    if n1 != 1 or n2 != 1:
        raise SystemExit(f"main_smoke_brief.md: expected one candidate line and one MAIN_SHA line, found {n1} and {n2}")
    Path(out).write_text(text)


def set_serial(path, unit, addr):
    """Replace UNIT's line in unit-serials.txt (rig_requires.py reads a moved unit there) with ADDR."""
    path = Path(path)
    keep = [l for l in (path.read_text().splitlines() if path.exists() else []) if l.split()[:1] != [unit]]
    path.write_text("\n".join(keep + [f"{unit} {addr}"]) + "\n")


def hp_serial():
    return sh(f"bash -c 'source \"{TOOLKIT}/rig_devices.sh\"; echo $D_HP'").stdout.strip()


def answers(addr, cap_s=0):
    """The unit's serial over wireless adb, asking again for up to CAP_S seconds ("offline" and "authorizing"
    pass on their own); "" when it never answers."""
    end = time.time() + cap_s
    while True:
        sh(["adb", "connect", addr])
        got = sh(["adb", "-s", addr, "shell", "getprop", "ro.serialno"], timeout=15).stdout.strip()
        if got or time.time() >= end:
            return got
        time.sleep(1)


def to_wireless(serial):
    """D-HP on its PC cable: switch its adb to TCP and return ip:5555, or None."""
    out = sh(["adb", "-s", serial, "shell", "ip", "-f", "inet", "addr", "show", "wlan0"]).stdout
    ip = (re.findall(r"inet (\d+\.\d+\.\d+\.\d+)", out) or [""])[0]
    if not ip:
        return None
    sh(["adb", "-s", serial, "tcpip", "5555"])
    return f"{ip}:5555" if answers(f"{ip}:5555", cap_s=30) else None


def sweep(serial):
    """D-HP got a new DHCP address: find it on the PC's own /24 by port 5555 and its serial number."""
    ips = re.findall(r"\b(\d+\.\d+\.\d+)\.\d+\b", sh("hostname -I").stdout)
    for net in dict.fromkeys(ips):
        for host in range(1, 255):
            ip = f"{net}.{host}"
            with socket.socket() as c:
                c.settimeout(0.15)
                if c.connect_ex((ip, 5555)):
                    continue
            if answers(f"{ip}:5555") == serial:
                return f"{ip}:5555"
    return None


def ensure_hp():
    """Return (address, None) when D-HP answers over wireless adb, else (None, the reason M6 does not run)."""
    serials = RIG_DATA / "rounds" / "unit-serials.txt"
    serial = hp_serial()
    old = next((l.split()[1] for l in (serials.read_text().splitlines() if serials.exists() else [])
                if l.split()[:1] == ["D-HP"] and len(l.split()) > 1), None)
    if old and serial and answers(old) == serial:
        return old, None
    if serial and serial in sh(["adb", "devices"]).stdout:
        addr = to_wireless(serial)
        if addr:
            set_serial(serials, "D-HP", addr)
            print(f"D-HP was on its PC cable; it now answers on {addr}. After this smoke:")
            for step in CABLING:
                print(f"  {step}")
            return None, "D-HP was on its PC cable, so the dongle is not on it; it is set up for the next smoke"
        return None, "D-HP is on its PC cable but has no WiFi address"
    addr = sweep(serial) if serial else None
    if addr:
        set_serial(serials, "D-HP", addr)
        print(f"D-HP moved to {addr}; recorded")
        return addr, None
    return None, "D-HP is not on wireless adb (it rebooted?): plug it into the PC once and the next smoke sets it up"


CABLING = ["1. Unplug D-HP from the PC.",
           "2. Plug the powered OTG Y-cable into D-HP, and its power leg into a charger.",
           "3. Plug the USB dongle into the Y-cable.",
           "4. On D-HP, answer the USB dialog for the dongle: tick 'Use by default', then OK."]


def summary(ev, known=None, skipped=("M6",), title="main smoke"):
    """One line per graded run and the worst exit code. video_smoke.py passes its own KNOWN_FAIL, D-HP runs and title."""
    known = KNOWN_FAIL if known is None else known
    rows, worst = [], 0
    for g in sorted(Path(ev).glob("*.grade.json")):
        d = json.loads(g.read_text())
        rid, v, why = d["id"], d["verdict"], d.get("reason", "")
        if v == "PASS":
            code = 0
        elif v == "FAIL" and rid in known:
            v, why, code = "FAIL (known)", known[rid], 0
        elif v == "FAIL":
            code = 1
        else:
            code = 2
        worst = code if worst == 0 or code == 1 else worst
        rows.append(f"{rid:<6} {v:<13} {why}".rstrip())
    skip = Path(ev) / "hp.skip"
    if skip.exists():
        rows += [f"{rid:<6} {'NOT_RUN':<13} {skip.read_text().strip()}" for rid in skipped]
        worst = worst or 2
    if not rows:
        rows, worst = ["no run was graded"], 2
    word = {0: "no regression", 1: "REGRESSION", 2: "incomplete"}[worst]
    head = f"{title} on {(Path(ev) / 'candidate.txt').read_text().strip() if (Path(ev) / 'candidate.txt').exists() else '?'}: {word}"
    text = "\n".join([head] + rows) + "\n"
    (Path(ev) / "summary.txt").write_text(text)
    print(text, end="")
    return worst


def follow(status, stop):
    seen = 0
    while not stop.is_set():
        if status.exists():
            lines = status.read_text(errors="replace").splitlines()
            for line in lines[seen:]:
                print(f"  {line}", flush=True)
            seen = len(lines)
        time.sleep(2)


def build(sha, ev, name="main-smoke-auto", arm="M"):
    t0 = time.time()
    wt = sh([str(TOOLKIT / "wt-new.sh"), name, "candidate", sha]).stdout.strip().splitlines()
    if not wt:
        return None
    wt = wt[-1]
    try:
        sh([str(TOOLKIT / "rig_thermal.sh"), "wait", "70"])
        print(f"building {sha[:9]} in {wt} (this takes a while)", flush=True)
        r = subprocess.run([str(TOOLKIT / "build_hur_cool.sh")], env=dict(os.environ, HUR_DIR=wt, MAX_WORKERS="2"),
                           stdout=open(ev / "build.log", "w"), stderr=subprocess.STDOUT)
        apks = [p for p in (RIG_DATA / "apks").glob("*.apk") if p.stat().st_mtime >= t0]
        if r.returncode or not apks:
            return None
        (ev / "apk").mkdir(exist_ok=True)
        dst = ev / "apk" / f"{arm}.apk"
        shutil.copy2(max(apks, key=lambda p: p.stat().st_mtime), dst)
        return dst
    finally:
        sh(f"cd '{wt}' && ./gradlew --stop")
        sh(["git", "-C", wt, "worktree", "remove", "--force", wt])


def run(ref, apk):
    f = sh(["git", "-C", str(TRANSFER), "fetch", "origin", "main"])
    if f.returncode:
        print(f"warning: git fetch origin main failed, so {ref} may be old: {f.stderr.strip()[:200]}")
    sha = sh(["git", "-C", str(TRANSFER), "rev-parse", ref]).stdout.strip()
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        print(f"cannot resolve {ref}; is the origin remote set in {TRANSFER}?")
        return 5
    ev = RIG_DATA / "rounds" / f"main-smoke-auto-round{time.strftime('%Y%m%d%H%M')}"
    ev.mkdir(parents=True)
    (ev / "candidate.txt").write_text(sha + "\n")
    brief = ev / "main-smoke-auto-brief.md"
    hp, why = ensure_hp()
    if why:
        (ev / "hp.skip").write_text(why + "\n")
        print(f"M6 is left out: {why}")
    render(sha, brief, skip_hp=bool(why))
    print(f"main smoke on {sha[:9]}; evidence in {ev}", flush=True)
    lint = sh([sys.executable, "-I", str(HERE / "brief_check.py"), "lint", str(brief), "--repo", str(TRANSFER)])
    (ev / "lint.txt").write_text(lint.stdout + lint.stderr)
    if lint.returncode:
        print("the brief is out of date for this commit (a graded line is gone, or a script broke):")
        print(lint.stdout + lint.stderr, end="")
        return 4
    pin = re.search(r"(?m)^lib (\S+)", brief.read_text()).group(1)
    tar = sh(f"git -C '{TRANSFER}' archive {pin} rig-lib | tar -x -C '{ev}'")
    if tar.returncode:
        print(f"cannot unpack rig-lib {pin}: {tar.stderr.strip()}")
        return 5
    # Starting the smoke is the operator's yes to the brief's confirm lines; print them so it is an informed one.
    for line in re.findall(r"(?m)^(?:confirm|hardware) .*$", brief.read_text()):
        print(f"assumed true: {line}")
    sh([sys.executable, "-I", str(ev / "rig-lib" / "rig_requires.py"), "yes", "--ev", str(ev), str(brief)])
    if apk:
        (ev / "apk").mkdir(exist_ok=True)
        shutil.copy2(apk, ev / "apk" / "M.apk")
    elif not build(sha, ev):
        print(f"the build failed; see {ev / 'build.log'}")
        return 5
    stop = threading.Event()
    t = threading.Thread(target=follow, args=(ev / "session.status", stop), daemon=True)
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
    cmd = args.pop(0) if args and args[0] in ("run", "render", "summary", "set-serial") else "run"
    opt = {k: v for k, v in zip(args[::2], args[1::2])}
    if cmd == "render":
        render(opt["--sha"], opt["--out"], skip_hp="--no-hp" in args); return 0
    if cmd == "set-serial":
        set_serial(opt["--file"], opt["--unit"], opt["--addr"]); return 0
    if cmd == "summary":
        return summary(opt["--ev"])
    return run(opt.get("--ref", "origin/main"), opt.get("--apk"))


if __name__ == "__main__":
    sys.exit(main(sys.argv))
