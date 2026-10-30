#!/usr/bin/env python3
"""What a round needs before its first run, read back from the rig rather than assumed.

Every brief carries one fenced block, for example:

    ```rig-requires
    lib 1a2b3c4d
    unit D-HU   role=headunit wifi=on bt=on
    unit D-POCO role=phone wifi=on bt=on bond=D-HU aa=D-HU battery=30
    unit D-MOTO bt=off
    hardware the ESP32 board on D-HU's hub, powered
    confirm D-POCO has a Spotify playlist of 3 or more tracks
    run R1 cap=300 script=r1.sh grade=grade-r1.sh
    run R2 cap=900 script=r2.sh grade=grade-r2.sh point=yes hand="unplug the ESP32 when OPERATOR NOW prints"
    run cleanup cap=120 script=cleanup.sh always=yes
    stop R1 FAIL,UNTESTABLE skip=all
    ```

unit keys: role = phone|headunit (a swap gets headunit://exit first), wifi/bt = on|off|any,
bond = units both sides must list (both radios on), nobond = units this unit's own list must NOT hold
(a phone's Android Auto dials a bonded head unit and ignores the one under test), aa = units this phone
has projected to once (the operator confirms it), battery = minimum percent. `unit D-POCO@B,S nobond=D-HU`
scopes the line to runs B and S: rig_session.py checks it just before those runs and, when only the
operator can meet it (a pair or a forget), prints one OPERATOR line and waits for it. A run's needs are
the session's lines plus its scoped lines, scoped values winning. hardware lines are physical
actions only the operator can do (a cable, a board, a charger); confirm lines are facts the operator
answers yes or no to, never tasks. Anything a command can do is not an operator line. A unit that is not on adb is skipped with a note when it needs nothing but radios off.
A unit a brief moved to wireless adb is read by the address a script appended to the rounds dir's
unit-serials.txt (`D-POCO 192.168.1.20:5555`) when its cable serial does not answer.
candidate NAME SHA names each build arm (T, B, C...); brief_check.py checks the brief's log
strings against them.
run keys: cap (hard seconds), script and grade (named blocks of the brief), point=yes for the run the
round exists for, hand="cue" for a step only the operator can do, always=yes for cleanup. Runs go in
the order listed. stop ID VERDICTS [skip=all|R3,R4]: after ID grades one of VERDICTS, skip those runs
(always=yes runs still go).

  rig_requires.py lint BRIEF...            exit 1 on a malformed block (brief_check.py calls this)
  rig_requires.py order [--live] BRIEF...  the session order with the fewest operator actions, and every
                                           transition between briefs (who does it, and how). --live reads
                                           each bond the briefs name from the rig first, so a pair or a
                                           forget that is already true costs nothing; without it every
                                           bond starts unknown and its first need is listed.
  rig_requires.py plan BRIEF...            the pre-flight command and one operator list for the session
  rig_requires.py yes --ev EV BRIEF...     record the operator's one yes for every brief named
  rig_requires.py check --ev EV BRIEF [--fix] [--run ID]
                                           read every need back; --fix switches what adb can switch (with
                                           read-back), swaps roles, and runs a nobond forget (rig_forget.py)
                                           or a bond pair (rig_pair.py) through Settings, with read-back;
                                           on success write EV/requirements.ok.
                                           --run checks one run's needs (scoped unit lines on top). A failure
                                           only the operator can clear is printed as "  - OPERATOR <action>".
  rig_requires.py baseline                 put every unit's radios to rig-lib/baseline.conf, read back
  rig_requires.py devfiles snapshot --ev EV BRIEF...
                                           record the test folders, and the folder of every device path the
                                           brief names, on every connected unit (once per EV)
  rig_requires.py devfiles sweep --ev EV BRIEF...
                                           delete from a unit only an entry that is new since the snapshot AND
                                           named by a path in the brief's scripts, after copying it to
                                           EV/device-files/<unit>/; any other new entry is listed, never deleted
  rig_requires.py devfiles list            print what the test folders of every connected unit hold now
  rig_requires.py clocks [--ev EV] [BRIEF...]  each unit's log clock against the PC's (a marker's own logcat
                                           stamp); sets it where adb's shell is root, and writes
                                           EV/clock-offsets.txt with the offset left on every other unit
"""
import hashlib, itertools, os, re, shlex, subprocess, sys, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))   # -I leaves the script's folder off sys.path
ROLES = ("D-HU", "D-POCO", "D-MOTO", "D-HP", "D-SAM", "D-T230")
RADIO_VALUES = ("on", "off", "any")
VERDICTS = ("PASS", "FAIL", "INCONCLUSIVE", "UNTESTABLE")
PKG = "com.andrerinas.headunitrevived"
RIG_DEVICES = os.environ.get("RIG_DEVICES",
                             str(Path.home() / "Coding/StudioProjects/rig-toolkit/rig_devices.sh"))
BLOCK = re.compile(r"^```rig-requires[ \t]*\n(.*?)^```", re.M | re.S)


def parse(text):
    """Returns (req, problems). req = {lib, units, operator, runs: [{id, cap, script, grade, ...}], stops}."""
    req = {"lib": None, "units": {}, "scoped": [], "operator": [], "runs": [], "stops": [], "candidates": {}}
    problems = []
    blocks = BLOCK.findall(text)
    if not blocks:
        return req, ["no ```rig-requires block (TESTING-TEMPLATE.md section 8, part 0)"]
    if len(blocks) > 1:
        problems.append("more than one rig-requires block")
    for n, raw in enumerate(blocks[0].splitlines(), 1):
        stripped = raw.strip()
        word = stripped.split(" ", 1)[0] if stripped else ""
        if word in ("hardware", "confirm"):
            rest = stripped[len(word):].strip()
            if not rest:
                problems.append(f"line {n}: {word} needs the text the operator confirms")
            req["operator"].append(f"{word}: {rest}")
            continue
        try:
            toks = shlex.split(raw, comments=True)
        except ValueError as e:
            problems.append(f"line {n}: {e}"); continue
        if not toks:
            continue
        word, args = toks[0], toks[1:]
        if word == "candidate":
            if len(args) != 2 or not re.fullmatch(r"[0-9a-f]{7,40}", args[1]):
                problems.append(f"line {n}: candidate needs NAME SHA, got {' '.join(args)!r}")
            else:
                req["candidates"][args[0]] = args[1]
        elif word == "lib":
            sha = args[0] if args else ""
            if not re.fullmatch(r"[0-9a-f]{7,40}", sha):
                problems.append(f"line {n}: lib needs a commit SHA of rig-lib, got {sha!r}")
            req["lib"] = sha
        elif word == "unit":
            if not args:
                problems.append(f"line {n}: unit needs a name"); continue
            role, pairs = args[0], args[1:]
            role, _, scope = role.partition("@")
            if role not in ROLES:
                problems.append(f"line {n}: unknown unit {role!r} (one of {', '.join(ROLES)})")
            if scope:
                u = {}
                req["scoped"].append((scope.split(","), role, u))
            else:
                u = req["units"].setdefault(role, {})
            for p in pairs:
                k, eq, v = p.partition("=")
                if not eq:
                    problems.append(f"line {n}: {p!r} is not key=value")
                elif k in ("wifi", "bt"):
                    if v not in RADIO_VALUES:
                        problems.append(f"line {n}: {k}={v} must be one of {'|'.join(RADIO_VALUES)}")
                    u[k] = v
                elif k == "role":
                    if v not in ("phone", "headunit"):
                        problems.append(f"line {n}: role={v} must be phone or headunit")
                    u[k] = v
                elif k in ("bond", "nobond", "aa"):
                    roles = [r for r in v.split(",") if r]
                    bad = [r for r in roles if r not in ROLES]
                    if bad:
                        problems.append(f"line {n}: {k}= names unknown unit {', '.join(bad)}")
                    u[k] = roles
                elif k == "battery":
                    if not v.isdigit():
                        problems.append(f"line {n}: battery must be a percent")
                    u[k] = int(v) if v.isdigit() else 0
                else:
                    problems.append(f"line {n}: unknown unit key {k!r} (role, wifi, bt, bond, nobond, aa, battery)")
        elif word == "run":
            if not args:
                problems.append(f"line {n}: run needs an id"); continue
            r = {"id": args[0]}
            for p in args[1:]:
                k, eq, v = p.partition("=")
                if not eq or k not in ("cap", "script", "grade", "point", "hand", "always"):
                    problems.append(f"line {n}: unknown run key {p!r} (cap, script, grade, point, hand, always)")
                r[k] = v
            if not r.get("cap", "").isdigit() or int(r["cap"]) <= 0:
                problems.append(f"line {n}: run {r['id']} needs cap=<seconds>")
            r["cap"] = int(r["cap"]) if r.get("cap", "").isdigit() else 0
            if not r.get("script"):
                problems.append(f"line {n}: run {r['id']} needs script=<named block>")
            if not r.get("grade") and r.get("always") != "yes":
                problems.append(f"line {n}: run {r['id']} needs grade=<named block> (only always=yes runs may omit it)")
            if any(x["id"] == r["id"] for x in req["runs"]):
                problems.append(f"line {n}: run {r['id']} listed twice")
            req["runs"].append(r)
        elif word == "stop":
            if len(args) < 2:
                problems.append(f"line {n}: stop needs a run id and verdicts"); continue
            vs = args[1].split(",")
            skip = dict(p.partition("=")[::2] for p in args[2:]).get("skip", "all")
            if any(v not in VERDICTS for v in vs):
                problems.append(f"line {n}: stop verdicts must be from {', '.join(VERDICTS)}")
            req["stops"].append({"after": args[0], "on": vs, "skip": skip})
        else:
            problems.append(f"line {n}: unknown line {word!r} (lib, candidate, unit, hardware, confirm, run, stop)")
    ids = [r["id"] for r in req["runs"]]
    for s in req["stops"]:
        if s["after"] not in ids:
            problems.append(f"stop names run {s['after']}, which has no run line")
        if s["skip"] != "all" and any(x not in ids for x in s["skip"].split(",")):
            problems.append(f"stop after {s['after']}: skip= names a run with no run line")
    if not req["lib"]:
        problems.append("no lib line: pin rig-lib with `lib <sha>` (git log -1 --format=%h -- rig-lib)")
    if not req["runs"]:
        problems.append("no run lines: every run needs `run <id> cap=<seconds> script=<block> grade=<block>`")
    for scope, role, _ in req["scoped"]:
        for rid in scope:
            if rid not in ids:
                problems.append(f"unit {role}@{','.join(scope)}: {rid} has no run line")
    for rid in [None] + ids:
        units = run_units(req, rid)
        for role, u in units.items():
            for k, needs_other in (("bond", True), ("nobond", False)):
                for other in u.get(k, []):
                    for who in ((role, other) if needs_other else (role,)):
                        if units.get(who, {}).get("bt") != "on":
                            problems.append(f"{role} {k}={other}{' in run ' + rid if rid else ''}: a bonded list "
                                            f"reads empty with Bluetooth off, so {who} needs bt=on")
                    if other in units.get(role, {}).get("bond" if k == "nobond" else "nobond", []):
                        problems.append(f"{role} both bond= and nobond= {other}{' in run ' + rid if rid else ''}")
    for role, u in req["units"].items():
        for other in u.get("aa", []):
            req["operator"].append(f"aa: {role} has projected to {other} in Android Auto at least once")
    problems = list(dict.fromkeys(problems))
    return req, problems


def run_units(req, run_id=None):
    """The unit needs in force for one run: the session's lines, then that run's scoped lines on top."""
    units = {r: dict(u) for r, u in req["units"].items()}
    for scope, role, u in req["scoped"]:
        if run_id in scope:
            units.setdefault(role, {}).update(u)
    return units


def scoped_runs(req):
    """Run ids with needs of their own."""
    return [rid for rid in (r["id"] for r in req["runs"])
            if any(rid in scope for scope, _, _ in req["scoped"])]


def load(path):
    return parse(Path(path).read_text(errors="replace"))


def operator_hash(req):
    return hashlib.sha256("\n".join(req["operator"]).encode()).hexdigest()[:16]


def baseline():
    """{unit: {wifi, bt, operator: set of radios only the operator can bring back}}"""
    out = {}
    for raw in (HERE / "baseline.conf").read_text().splitlines():
        line = raw.split("#", 1)[0].split()
        if line:
            pairs = dict(p.partition("=")[::2] for p in line[1:])
            pairs["operator"] = set(x for x in pairs.get("operator", "").split(",") if x)
            out[line[0]] = pairs
    return out


def serials():
    names = " ".join(r.replace("-", "_") for r in ROLES)
    cmd = f'source "{RIG_DEVICES}" >/dev/null 2>&1; for v in {names}; do printf "%s=%s\\n" $v "${{!v}}"; done'
    out = subprocess.run(["bash", "-c", cmd], capture_output=True, text=True).stdout
    found = {}
    for line in out.splitlines():
        k, _, v = line.partition("=")
        if v:
            found[k.replace("_", "-")] = v
    return found


def adb(serial, *args, timeout=20):
    try:
        r = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, timeout=timeout)
        return r.stdout.replace("\r", "")
    except Exception:
        return ""


def radio_state(serial, radio):
    if radio == "bt":
        m = re.search(r"^\s*(state|enabled):\s*(\S+)", adb(serial, "shell", "dumpsys", "bluetooth_manager"), re.M)
        v = m.group(2) if m else ""
        st = "on" if v in ("ON", "true") else "off" if v in ("OFF", "false") or "BLE" in v else "unknown"
    else:
        m = re.search(r"Wi-Fi is (\w+)", adb(serial, "shell", "dumpsys", "wifi"))
        st = {"enabled": "on", "disabled": "off"}.get(m.group(1) if m else "", "unknown")
    if st == "unknown":   # API 17/19 dumps print no state line; the global setting is there from 4.2
        v = adb(serial, "shell", "settings", "get", "global", "bluetooth_on" if radio == "bt" else "wifi_on").strip()
        st = {"1": "on", "2": "on", "0": "off", "3": "off"}.get(v, "unknown")
    return st


def app_running(serial):
    """pidof first (API 24 and up); below that it is missing and its "not found" must not read as a pid."""
    out = adb(serial, "shell", "pidof", PKG).strip()
    if out and out.split()[0].isdigit():
        return True
    return any(l.split()[-1:] == [PKG] for l in adb(serial, "shell", "ps").splitlines())


def set_radio(serial, radio, want):
    svc = "bluetooth" if radio == "bt" else "wifi"
    adb(serial, "shell", "svc", svc, "enable" if want == "on" else "disable")
    got = "unknown"
    for _ in range(int(os.environ.get("RL_RADIO_WAIT_S", "35")) * 2):   # D-HP takes up to 30 s
        got = radio_state(serial, radio)
        if got == want:
            break
        time.sleep(0.5)
    return got


def bonded(serial, other_serial):
    """(bonded?, label). By the other unit's address; by its exact name only when no address reads, because
    units are renamed and a phone keeps the name it saw at pairing."""
    from rig_forget import bonded_entries, own_address
    entries = bonded_entries(adb(serial, "shell", "dumpsys", "bluetooth_manager"))
    addr = own_address(adb(other_serial, "shell", "settings", "get", "secure", "bluetooth_address"),
                       adb(other_serial, "shell", "dumpsys", "bluetooth_manager"))
    if addr:
        shown = [n for a, n in entries if a == addr]
        return bool(shown), f"{shown[0] if shown else 'not listed'}, {addr}"
    name = adb(other_serial, "shell", "settings", "get", "secure", "bluetooth_name").strip()
    hit = bool(name) and name != "null" and any(n.casefold() == name.casefold() for _, n in entries)
    return hit, f"{name or '?'}, address unreadable"


def wireless_serials(ev, ser):
    """Replace a cable serial that does not answer by the wireless one a brief recorded for that unit."""
    notes = []
    try:
        rows = [l.split() for l in (Path(ev).parent / "unit-serials.txt").read_text().splitlines()]
    except OSError:
        return notes
    for row in rows:
        if len(row) >= 2 and row[0] in ROLES and adb(ser.get(row[0], ""), "get-state").strip() != "device" \
                and adb(row[1], "get-state").strip() == "device":
            ser[row[0]] = row[1]
            notes.append(f"{row[0]} read over wireless adb at {row[1]}")
    return notes


def roles_file(ev):
    return Path(ev).parent / "unit-roles.txt"


def last_roles(ev):
    try:
        return dict(l.split()[:2] for l in roles_file(ev).read_text().splitlines() if len(l.split()) >= 2)
    except OSError:
        return {}


# ---------------- commands ----------------

def cmd_lint(paths):
    bad = 0
    for p in paths:
        _, problems = load(p)
        for pr in problems:
            print(f"{p}: {pr}")
        bad += bool(problems)
    return 1 if bad else 0


def transitions(prev, nxt, base):
    """Steps between the state one brief leaves and the state the next needs: [(who, text)]."""
    steps = []
    for role in sorted(set(prev) | set(nxt)):
        a, b = prev.get(role, {}), nxt.get(role, {})
        for k in ("wifi", "bt"):
            want = b.get(k)
            have = a.get(k, base.get(role, {}).get(k, "on"))
            if want in ("on", "off") and want != have:
                who = "operator" if (k in base.get(role, {}).get("operator", set()) and want == "on") else "auto"
                steps.append((who, f"{role} {k} {have} -> {want}"))
        if b.get("role") and a.get("role") and a["role"] != b["role"]:
            steps.append(("auto", f"{role} role {a['role']} -> {b['role']} (headunit://exit first)"))
        for k in sorted(x for x in b if x.startswith("b:")):
            if a.get(k) != b[k]:
                steps.append(("operator", f"{role} {'pair' if b[k] == 'yes' else 'forget'} {k[2:]}"))
    return steps


def state_of(units):
    """The part of a run's needs that carries over to the next: radios, role, and each bond as b:<unit>."""
    out = {}
    for r, u in units.items():
        st = {k: v for k, v in u.items() if k in ("wifi", "bt", "role") and v != "any"}
        st.update({f"b:{o}": "yes" for o in u.get("bond", [])})
        st.update({f"b:{o}": "no" for o in u.get("nobond", [])})
        out[r] = st
    return out


def run_states(req):
    """[(run id, state)] in run order, the session's lines plus each run's scoped lines."""
    return [(r["id"], state_of(run_units(req, r["id"]))) for r in req["runs"]]


def merge(prev, units):
    out = {r: dict(prev.get(r, {})) for r in set(prev) | set(units)}
    for r, u in units.items():
        out[r].update(u)
    return out


def live_bonds(reqs):
    """{role: {b:<other>: yes|no}} read from the rig for every bond the briefs name (one side, as nobond is)."""
    ser, out = serials(), {}
    for _, states in reqs:
        for _, units in states:
            for role, st in units.items():
                for k in st:
                    if k.startswith("b:") and k not in out.get(role, {}) and ser.get(role) and ser.get(k[2:]):
                        out.setdefault(role, {})[k] = "yes" if bonded(ser[role], ser[k[2:]])[0] else "no"
    return out


def order(paths, live=False):
    base = baseline()
    reqs = [(p, run_states(load(p)[0])) for p in paths]
    start = {r: {k: v for k, v in u.items() if k in ("wifi", "bt")} for r, u in base.items()}
    if live:
        for role, bonds in live_bonds(reqs).items():
            start.setdefault(role, {}).update(bonds)

    def cost(seq):
        prev, c, ops = start, 0, 0
        for _, states in seq:
            for _, units in states:
                st = transitions(prev, units, base)
                ops += sum(w == "operator" for w, _ in st)
                c += len(st)
                prev = merge(prev, units)
        return ops, c
    perms = list(itertools.permutations(reqs)) if len(reqs) <= 7 else [tuple(reqs)]
    best = min(perms, key=lambda s: (*cost(s), [reqs.index(x) for x in s]))
    return [p for p, _ in best], base, start


def cmd_order(paths, live=False):
    seq, base, start = order(paths, live)
    print("Session order (fewest operator actions, then fewest changes; ties keep the Queue order):")
    prev = start
    for i, p in enumerate(seq, 1):
        print(f"  {i}. {Path(p).name}")
        for n, (rid, units) in enumerate(run_states(load(p)[0])):
            for who, text in transitions(prev, units, base):
                print(f"       {'OPERATOR' if who == 'operator' else 'auto    '} {text}"
                      + (f"  (before run {rid})" if n else ""))
            prev = merge(prev, units)
    print("auto steps run inside `check --fix`. OPERATOR steps go in the one message before the session;")
    print("one marked (before run X) is a mid-round step: the session prints it as an OPERATOR line and waits.")
    return 0


def cmd_plan(paths):
    pre, ops = {}, []
    for p in paths:
        req, problems = load(p)
        name = Path(p).name
        for pr in problems:
            print(f"PROBLEM {name}: {pr}")
        if p == paths[0]:
            for role, u in req["units"].items():
                pre[role] = [k for k in ("wifi", "bt") if u.get(k) == "on"]
        ops += [f"{name}: {o}" for o in req["operator"]]
    print("Pre-flight for the first brief:")
    print("  rig_preflight.sh " + " ".join(r.replace("-", "_") + (":" + ",".join(n) if n else "") for r, n in pre.items()))
    acts = [o for o in ops if ": hardware: " in o]
    facts = [o for o in ops if ": hardware: " not in o]
    print("Send the operator ONE message with these parts, in this order and with these headings:")
    print("  A. Watch progress live: open a separate terminal on the testing PC and run this line. It updates")
    print("     by itself and stops when the session ends:")
    print(f"     bash {HERE / 'rig_watch.sh'}")
    if acts:
        print("  B. Do these by hand before the first run, then answer 'done':")
        for i, o in enumerate(acts, 1):
            print(f"     B{i}. {o.replace(': hardware: ', ': ', 1)}")
    if facts:
        print("  C. Are these true? Answer yes or no for each. They are questions, not tasks:")
        for i, o in enumerate(facts, 1):
            print(f"     C{i}. {o.replace(': confirm: ', ': ', 1).replace(': aa: ', ': ', 1)}")
    print("  D. The testing PC does everything else itself: radios, roles, bond reads, installs, settings,")
    print("     the adb switch between USB and wireless, and every command. Do not touch a unit until DONE")
    print("     unless an OPERATOR line asks.")
    if not ops:
        print("  (No B or C items: send A and D only.)")
    return 0


def cmd_yes(ev, paths):
    f = Path(ev).parent / "operator-yes.txt"
    f.parent.mkdir(parents=True, exist_ok=True)
    with f.open("a") as out:
        for p in paths:
            req, problems = load(p)
            if problems:
                print("\n".join(problems)); return 1
            out.write(f"{time.strftime('%F %T')} {Path(p).name} list {operator_hash(req)} ({len(req['operator'])} items)\n")
            print(f"recorded the operator's yes for {Path(p).name} ({len(req['operator'])} items)")
    return 0


def group_owner(serial):
    return bool(re.search(r"isGroupOwner: true", adb(serial, "shell", "dumpsys", "wifip2p")))


def cmd_check(ev, path, fix=False, run_id=None):
    req, problems = load(path)
    units = run_units(req, run_id)
    Path(ev).mkdir(parents=True, exist_ok=True)
    ok_file = Path(ev, "requirements.ok")
    ok_file.unlink(missing_ok=True)
    fails, lines, base, ser = list(problems), [], baseline(), serials()
    lines += wireless_serials(ev, ser)
    roles_before = last_roles(ev)
    for role, u in units.items():
        s = ser.get(role)
        if not s:
            fails.append(f"{role}: no serial in {RIG_DEVICES}"); continue
        if adb(s, "get-state").strip() != "device":
            if all(u.get(k) in (None, "off", "any") for k in ("wifi", "bt")) and not any(
                    u.get(k) for k in ("role", "bond", "nobond", "aa", "battery")):
                lines.append(f"{role} is not on adb: skipped (it needs only radios off; a unit left powered on can still dial)")
            else:
                fails.append(f"OPERATOR reconnect {role} ({s}) to adb")
            continue
        want_role, had = u.get("role"), roles_before.get(role)
        if fix and want_role and had and had != want_role:
            if app_running(s):       # an exit on a stopped app starts the service
                adb(s, "shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", "headunit://exit")
                time.sleep(5)
            adb(s, "shell", "am", "force-stop", PKG)
            lines.append(f"{role} role {had} -> {want_role}: exited and stopped the app")
        for k in ("wifi", "bt"):
            want = u.get(k)
            if want not in ("on", "off"):
                continue
            got = radio_state(s, k)
            operator_only = k in base.get(role, {}).get("operator", set()) and want == "on"
            if got != want and fix and not operator_only:
                got = set_radio(s, k, want)
                lines.append(f"{role} {k} switched to {got} (needs {want})")
            else:
                lines.append(f"{role} {k}={got} (needs {want})")
            if got != want:
                fails.append(f"OPERATOR turn {role} {k} {want} (it reads {got})" if operator_only
                             else f"{role} {k} is {got}, the brief needs {want}")
        if u.get("role") == "phone" and u.get("wifi") != "off" and group_owner(s):
            # a phone left as a P2P group owner by an earlier head-unit run cannot join the unit under test
            if fix:
                if app_running(s):
                    adb(s, "shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", "headunit://exit")
                    time.sleep(5)
                    adb(s, "shell", "am", "force-stop", PKG)
                else:
                    set_radio(s, "wifi", "off"); set_radio(s, "wifi", "on")
            owner = group_owner(s)
            lines.append(f"{role} isGroupOwner={'true' if owner else 'false'} (a phone needs false)")
            if owner:
                fails.append(f"{role} is still a WiFi Direct group owner from an earlier run")
        if "battery" in u:
            m = re.search(r"level: (\d+)", adb(s, "shell", "dumpsys", "battery"))
            lvl = int(m.group(1)) if m else -1
            lines.append(f"{role} battery={lvl}% (needs {u['battery']}%)")
            if lvl < u["battery"]:
                fails.append(f"{role} battery is {lvl}%, the brief needs {u['battery']}%")
    for role, u in units.items():
        s = ser.get(role)
        for other in u.get("bond", []) + u.get("nobond", []):
            o = ser.get(other)
            if not (s and o):
                fails.append(f"{role} bond={other}: no serial"); continue
            want = other in u.get("bond", [])
            if want and fix and not (bonded(s, o)[0] and bonded(o, s)[0]):
                from rig_pair import Pair
                rc, msg = Pair(s, o, ev, adb=adb).run()
                lines.append(f"{role} pair {other}: {msg}")
            for a, b, an, bn in ((s, o, role, other), (o, s, other, role))[:2 if want else 1]:
                hit, nm = bonded(a, b)
                if hit and not want and fix:
                    from rig_forget import Forget
                    rc, msg = Forget(a, b, ev, adb=adb).run()
                    lines.append(f"{an} forget {bn}: {msg}")
                    hit, nm = bonded(a, b)
                lines.append(f"{an} lists {bn} ({nm}): {'yes' if hit else 'no'} (needs {'yes' if want else 'no'})")
                if hit != want:
                    fails.append(f"OPERATOR on {an}, pair {bn} ({nm}) in Bluetooth settings" if want else
                                 f"OPERATOR on {an}, forget {bn} ({nm}) in Bluetooth settings")
    if req["operator"]:
        yes = Path(ev).parent / "operator-yes.txt"
        if not yes.exists() or operator_hash(req) not in yes.read_text():
            fails.append("the operator has not said yes to this brief's list: show it with "
                         "rig_requires.py plan, then rig_requires.py yes --ev EV BRIEF...")
    for l in lines:
        print("  " + l)
    if fails:
        print("REQUIREMENTS FAILED: stop, tell the operator, do not start the round")
        for f in fails:
            print("  - " + f)
        return 1
    roles = dict(roles_before)
    roles.update({r: u["role"] for r, u in units.items() if u.get("role")})
    roles_file(ev).write_text("".join(f"{r} {v}\n" for r, v in sorted(roles.items())))
    ok_file.write_text(f"{time.strftime('%F %T')} {Path(path).name}{' run ' + run_id if run_id else ''}\n" + "".join(f"  {l}\n" for l in lines))
    print(f"REQUIREMENTS OK: wrote {ok_file}")
    return 0


# Folders a round writes to on a unit: adb push targets, screenshots, exports. Listed one level deep, with
# ls -a, because API 19 units have no find.
DEV_DIRS = ("/data/local/tmp", "/sdcard", "/sdcard/Download", "/sdcard/Pictures", "/sdcard/Pictures/Screenshots",
            "/sdcard/DCIM", "/sdcard/DCIM/Screenshots", "/sdcard/Movies", "/sdcard/Documents")
DEV_PATH = re.compile(r"(?:/data/local/tmp|/sdcard)/[\w.${}/*-]+")
MAX_NEW_PER_DIR = 30   # more new entries than this in one folder is not a round's doing: delete nothing there


def watched_dirs(briefs):
    """DEV_DIRS plus the folder of every path a brief names (a literal folder, never one with a variable)."""
    extra = {g.rsplit("/", 1)[0] for g in declared_paths(briefs)}
    return list(DEV_DIRS) + sorted(d for d in extra if d not in DEV_DIRS and not any(c in d for c in "*[?"))


def dev_listing(serial, dirs=DEV_DIRS):
    """{dir: [entry names] or None}. None means the listing failed, so nothing in that folder may be deleted."""
    out = {}
    for d in dirs:
        try:
            r = subprocess.run(["adb", "-s", serial, "shell", "ls", "-a", d], capture_output=True, text=True, timeout=20)
        except Exception:
            out[d] = None; continue
        text = (r.stdout + r.stderr).replace("\r", "")   # a missing folder answers on stderr
        if r.returncode != 0 and not text.strip():
            out[d] = None; continue
        if "No such file" in text:
            out[d] = []; continue                    # the folder does not exist on this unit
        if "Permission denied" in text or text.lstrip().startswith(("ls:", "opendir")):
            out[d] = None; continue
        out[d] = sorted(n.strip() for n in text.splitlines() if n.strip() and n.strip() not in (".", ".."))
    return out


def declared_paths(briefs):
    """Glob patterns for every device path a brief's scripts name. A variable matches one path segment."""
    sys.path.insert(0, str(HERE))
    from brief_check import blocks
    pats = set()
    for b in briefs:
        for _, _, body, _ in blocks(Path(b).read_text(errors="replace")):
            for m in DEV_PATH.findall(body):
                pats.add(re.sub(r"\$\{?\w+\}?", "[!/]*", m.rstrip("/")))
    return sorted(pats)


def deletable(path, pats):
    """A new entry may go when a brief named it, or named a file inside it."""
    import fnmatch
    return any(fnmatch.fnmatchcase(path, g) or fnmatch.fnmatchcase(path, g.split("/*")[0])
               or any(fnmatch.fnmatchcase(path, "/".join(g.split("/")[:k])) for k in range(len(path.split("/")), len(g.split("/"))))
               for g in pats)


def connected():
    return {r: s for r, s in serials().items() if adb(s, "get-state").strip() == "device"}


CLOCK_TOLERANCE_MS = 1000


def clock_offset_ms(serial):
    """The unit's log clock minus the PC's local clock, in the units every capture uses: a marker's own
    logcat timestamp against the PC time it was written. Works on every API level; None when unreadable."""
    import datetime
    tag = f"rigclock-{os.getpid()}-{time.time_ns()}"
    t0 = time.time()
    adb(serial, "shell", "log", "-t", "RIGCLOCK", tag)
    t1 = time.time()
    out = adb(serial, "logcat", "-d", "-v", "time", "-s", "RIGCLOCK:I")   # old units default to a format with no time
    lines = [l for l in out.splitlines() if tag in l]
    m = re.match(r"(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3})", lines[-1]) if lines else None
    if not m:
        return None
    host = datetime.datetime.fromtimestamp((t0 + t1) / 2)
    mo, d, h, mi, s, ms = map(int, m.groups())
    best = None
    for y in (host.year - 1, host.year, host.year + 1):   # a log line has no year
        try:
            dev = datetime.datetime(y, mo, d, h, mi, s, ms * 1000)
        except ValueError:
            continue
        off = round((dev - host).total_seconds() * 1000)
        best = off if best is None or abs(off) < abs(best) else best
    return best


def set_clock(serial):
    """Set a unit's clock to the PC's, only where adb's shell is already root. Never su: on a phone it can
    raise a prompt nobody answers. Returns the method that ran, or None."""
    if adb(serial, "shell", "id", "-u").strip() != "0":
        return None
    adb(serial, "shell", "date", f"@{int(time.time()) + 1}")   # toybox
    off = clock_offset_ms(serial)
    if off is not None and abs(off) <= CLOCK_TOLERANCE_MS:
        return "date @epoch"
    adb(serial, "shell", "date", "-s", time.strftime("%Y%m%d.%H%M%S", time.localtime(time.time() + 1)))   # toolbox
    return "date -s"


def cmd_clocks(ev, briefs):
    """Measure every connected unit the briefs use against the PC clock, set it where root allows, and
    write EV/clock-offsets.txt. A unit left off is reported with the offset to subtract from its captures."""
    roles = set()
    for b in briefs:
        req, _ = parse(Path(b).read_text())
        roles |= set(req["units"]) | {role for _, role, _ in req["scoped"]}
    rows, short = [], []
    for role, s in sorted(connected().items()):
        if roles and role not in roles:
            continue
        before = clock_offset_ms(s)
        if before is None:
            rows.append(f"{role} {s} offset=unreadable"); short.append(f"{role} unreadable"); continue
        if abs(before) <= CLOCK_TOLERANCE_MS:
            rows.append(f"{role} {s} offset_ms={before:+d} action=none")
            short.append(f"{role} {before / 1000:+.1f}s"); continue
        how = set_clock(s)
        after = clock_offset_ms(s) if how else None
        if how and after is not None and abs(after) <= CLOCK_TOLERANCE_MS:
            rows.append(f"{role} {s} offset_ms={after:+d} action=set({how}) was_ms={before:+d}")
            short.append(f"{role} {after / 1000:+.1f}s (set from {before / 1000:+.1f}s)")
        else:
            left = after if after is not None else before
            why = "no root shell" if not how else f"{how} did not hold (time zone or auto time)"
            rows.append(f"{role} {s} offset_ms={left:+d} action=left({why}) was_ms={before:+d}")
            short.append(f"{role} {left / 1000:+.1f}s LEFT ({why}): subtract it from {role}'s timestamps")
    if ev:
        Path(ev, "clock-offsets.txt").write_text("\n".join(rows) + "\n")
    print("CLOCKS " + ("; ".join(short) or "no connected unit"))
    return 0


def cmd_devfiles(sub, ev, briefs=()):
    import json
    if sub == "list":
        for role, s in sorted(connected().items()):
            for d, names in dev_listing(s).items():
                print(f"{role} {d}: {'LISTING FAILED' if names is None else ' '.join(names)}")
        return 0
    snap = Path(ev, "device-files-before.json")
    if sub == "snapshot":
        if snap.exists():
            print(f"snapshot kept: {snap}"); return 0
        Path(ev).mkdir(parents=True, exist_ok=True)
        dirs = watched_dirs(briefs)
        data = {role: dev_listing(s, dirs) for role, s in connected().items()}
        snap.write_text(json.dumps(data, indent=1))
        print(f"snapshot: {len(data)} units")
        return 0
    if sub == "sweep":
        if not snap.exists():
            print("device files: no snapshot, so nothing was deleted; run devfiles list and tell the operator"); return 1
        before, pats = json.loads(snap.read_text()), declared_paths(briefs)
        moved, kept, failed = [], [], []
        for role, s in sorted(connected().items()):
            if role not in before:
                continue
            dirs = list(before[role])
            now = dev_listing(s, dirs)
            for d in dirs:
                old, cur = before[role].get(d), now.get(d)
                if old is None or cur is None:          # a listing failed: compare nothing, delete nothing
                    if cur is None or old is None:
                        kept.append(f"{role} {d}/ (listing failed, not checked)")
                    continue
                new = [f"{d}/{n}" for n in cur if n not in old and f"{d}/{n}" not in dirs]
                if len(new) > MAX_NEW_PER_DIR:
                    kept += [f"{role} {d}/ ({len(new)} new entries, more than {MAX_NEW_PER_DIR}: nothing deleted)"]
                    continue
                for path in new:
                    if not deletable(path, pats):
                        kept.append(f"{role} {path} (not named by the brief: left in place)"); continue
                    dest = Path(ev, "device-files", role)
                    dest.mkdir(parents=True, exist_ok=True)
                    pr = subprocess.run(["adb", "-s", s, "pull", path, str(dest)], capture_output=True, timeout=300)
                    if pr.returncode != 0 or not (dest / Path(path).name).exists():
                        failed.append(f"{role} {path} (copy to the PC failed, so it was not deleted)"); continue
                    adb(s, "shell", "rm", "-rf", path)
                    (failed if adb(s, "shell", "ls", "-d", path).strip() == path else moved).append(f"{role} {path}")
        Path(ev, "device-files.txt").write_text("".join(f"removed {m}\n" for m in moved)
                                                + "".join(f"STILL THERE {f}\n" for f in failed)
                                                + "".join(f"LEFT {k}\n" for k in kept))
        print(f"device files: {len(moved)} copied to {Path(ev, 'device-files')} and removed, "
              f"{len(failed)} could not be removed, {len(kept)} left for the operator to check")
        for f in failed + kept:
            print(f"  {f}")
        return 1 if failed or kept else 0
    print(__doc__); return 64


def cmd_baseline():
    ser, fails = serials(), 0
    for role, pairs in baseline().items():
        s = ser.get(role)
        if not s or adb(s, "get-state").strip() != "device":
            print(f"  {role}: not connected, skipped"); continue
        for k in ("wifi", "bt"):
            want = pairs.get(k)
            if want in ("on", "off"):
                got = set_radio(s, k, want)
                print(f"  {role} {k}={got} (baseline {want})")
                fails += got != want
    print("BASELINE OK" if not fails else f"BASELINE FAILED: {fails} radios did not reach the baseline; tell the operator")
    return 1 if fails else 0


def main(argv):
    if len(argv) < 2:
        print(__doc__); return 64
    cmd, rest = argv[1], argv[2:]
    ev, fix = None, "--fix" in rest
    rest = [r for r in rest if r != "--fix"]
    if "--ev" in rest:
        i = rest.index("--ev"); ev = rest[i + 1]; rest = rest[:i] + rest[i + 2:]
    run_id = None
    if "--run" in rest:
        i = rest.index("--run"); run_id = rest[i + 1]; rest = rest[:i] + rest[i + 2:]
    if cmd == "lint":
        return cmd_lint(rest)
    if cmd == "order":
        return cmd_order([r for r in rest if r != "--live"], "--live" in rest)
    if cmd == "plan":
        return cmd_plan(rest)
    if cmd == "yes" and ev and rest:
        return cmd_yes(ev, rest)
    if cmd == "check" and ev and len(rest) == 1:
        return cmd_check(ev, rest[0], fix, run_id)
    if cmd == "baseline":
        return cmd_baseline()
    if cmd == "clocks":
        return cmd_clocks(ev, rest)
    if cmd == "devfiles" and rest and (rest[0] == "list" or ev):
        return cmd_devfiles(rest[0], ev, rest[1:])
    print(__doc__); return 64


if __name__ == "__main__":
    sys.exit(main(sys.argv))
