#!/usr/bin/env python3
"""The Bluetooth API route: rig-bt-helper (bt-helper/), an instrumentation that runs on API 17 and up.

adb's shell user holds BLUETOOTH_PRIVILEGED and BLUETOOTH_CONNECT on D-HU, D-POCO and D-MOTO (measured
2026-10-30, shell-bt-permission-check-results.md). From API 29 the helper borrows them, so a bond is removed,
made or accepted with no screen. Below API 29 (D-SAM, D-HP) it holds only BLUETOOTH and BLUETOOTH_ADMIN: it
lists and removes bonds, and a pairing there still needs one tap on the unit's own dialog.

The bonded list comes from the helper first, because dumpsys prints no usable list on Android 15 (D-POCO) and
none at all on 4.4 (D-SAM), measured in bt-helper round 1.

  rig_bt.py list SERIAL                  the unit's own address and its bonded list, through the API
  rig_bt.py bonds SERIAL                 the bonded list, one "ADDRESS name" line each (helper, else dumpsys)
  rig_bt.py unpair SERIAL ADDR           remove the bond; UNPAIR_OK or FAIL
  rig_bt.py pair SERIAL ADDR             ask for a bond and accept this side's request; PAIR_OK or FAIL
  rig_bt.py accept SERIAL ADDR           wait for ADDR's request, accept it, wait for the bond; ACCEPT_OK or FAIL
"""
import hashlib, re, subprocess, sys, tempfile, threading
from pathlib import Path

HERE = Path(__file__).resolve().parent
APK = HERE / "bt-helper" / "build" / "rig-bt-helper.apk"
PKG = "ohu.rig.bthelper"
COMPONENT = PKG + "/.BtInstrumentation"
RESULT = re.compile(r"INSTRUMENTATION_RESULT: result=(.*)")
MAC = re.compile(r"([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})")


def run_adb(serial, *args, timeout=20):
    try:
        r = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, timeout=timeout,
                           stdin=subprocess.DEVNULL)   # adb shell reads stdin: a caller's while-read loop loses lines
        return r.stdout.replace("\r", "")
    except Exception:
        return ""


def sdk(serial, adb=run_adb):
    v = adb(serial, "shell", "getprop", "ro.build.version.sdk").strip()
    return int(v) if v.isdigit() else 0


def usable(serial, adb=run_adb):
    """The helper runs: list, unpair, and pair as the asking side when the far side confirms."""
    return sdk(serial, adb) >= 17


def confirms(serial, adb=run_adb):
    """The helper confirms a pairing with no tap: it needs the shell's BLUETOOTH_PRIVILEGED, so API 29 and up."""
    return sdk(serial, adb) >= 29


def build():
    if not APK.exists():
        subprocess.run(["bash", str(HERE / "bt-helper" / "build.sh")], capture_output=True, text=True, timeout=300)
    return APK.exists()


def installed_md5(serial, adb=run_adb):
    """The md5 of the installed helper, hashed on the host: D-SAM has no md5sum, and a piped cat is not exact."""
    path = adb(serial, "shell", "pm", "path", PKG).strip().replace("package:", "")
    if not path:
        return ""
    with tempfile.TemporaryDirectory() as d:
        local = Path(d) / "helper.apk"
        adb(serial, "pull", path, str(local), timeout=60)
        return hashlib.md5(local.read_bytes()).hexdigest() if local.exists() else ""


def ensure(serial, adb=run_adb):
    """Install the helper when the unit lacks it or holds another build; grant BLUETOOTH_CONNECT. True when ready."""
    if not build():
        return False
    want = hashlib.md5(APK.read_bytes()).hexdigest()
    if installed_md5(serial, adb) != want:
        out = adb(serial, "install", "-r", "-t", str(APK), timeout=120)
        if installed_md5(serial, adb) != want:   # a helper signed by another key: no state to keep, so replace it
            adb(serial, "uninstall", PKG)
            out = adb(serial, "install", "-t", str(APK), timeout=120)
        if installed_md5(serial, adb) != want:
            print(f"rig_bt: the helper did not install on {serial}: {' '.join(out.split())[-200:]}", file=sys.stderr)
            return False
    adb(serial, "shell", "pm", "grant", PKG, "android.permission.BLUETOOTH_CONNECT")
    return True


def args_for(cmd, addr=None, timeout=30, level=29):
    a = ["shell", "am", "instrument", "-w"] + (["--no-hidden-api-checks"] if level >= 28 else [])  # am below 28 refuses it
    a += ["-e", "cmd", cmd, "-e", "timeout", str(timeout)]
    if addr:
        a += ["-e", "addr", addr]
    return a + [COMPONENT]


def parse(out):
    """(WORD, detail) from the instrumentation's output; ("FAIL", why) when no result line came back."""
    m = RESULT.search(out or "")
    if not m:
        return "FAIL", "no result line: " + " ".join((out or "").split())[:160]
    word, _, rest = m.group(1).strip().partition(" ")
    return word, rest


def call(serial, cmd, addr=None, timeout=30, adb=run_adb):
    return parse(adb(serial, *args_for(cmd, addr, timeout, sdk(serial, adb)), timeout=timeout + 30))


def bonded_entries(dump):
    """[(ADDRESS, name)] from `dumpsys bluetooth_manager`'s Bonded devices section."""
    i = dump.find("Bonded devices")
    out = []
    if i < 0:
        return out
    for line in dump[i:].splitlines()[1:]:
        m = re.match(r"^\s+([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})\s*((?:\[[^\]]*\]\s*)*)(.*)$", line)
        if not m:
            if line.strip() and not line.startswith(" "):
                break
            continue
        out.append((m.group(1).upper(), m.group(3).strip()))
    return out


def parse_list(detail):
    """[(ADDRESS, name)] from a LIST result: "<own address> <count> | ADDRESS name | ..."."""
    out = []
    for part in detail.split(" | ")[1:]:
        addr, _, name = part.strip().partition(" ")
        if MAC.fullmatch(addr):
            out.append((addr.upper(), name.strip()))
    return out


def bonds(serial, adb=run_adb, ensure=ensure):
    """This unit's bonded list as [(ADDRESS, name)], or None when it cannot be read. Never [] for unreadable:
    only the helper can report an empty list."""
    if usable(serial, adb) and ensure(serial, adb):
        word, detail = call(serial, "list", None, 20, adb)
        if word == "LIST":
            return parse_list(detail)
    dump = adb(serial, "shell", "dumpsys", "bluetooth_manager")
    if "Bonded devices" not in dump:
        return None
    rows = bonded_entries(dump)
    return rows if rows or sdk(serial, adb) < 35 else None   # Android 15 prints the section empty (D-POCO)


def call_async(serial, cmd, addr=None, timeout=60, adb=run_adb):
    """The same call on a thread (the far side of a pair). join() it, then read .result as (WORD, detail)."""
    t = threading.Thread(target=lambda: setattr(t, "result", call(serial, cmd, addr, timeout, adb)), daemon=True)
    t.result = ("FAIL", "the background call did not end")
    t.start()
    return t


def main(argv):
    if len(argv) == 2 and argv[0] == "bonds":
        b = bonds(argv[1])
        if b is None:
            print("FAIL the bonded list is unreadable"); return 1
        for a, n in b:
            print(f"{a} {n}")
        return 0
    if len(argv) >= 2 and argv[0] in ("list", "unpair", "pair", "accept"):
        s = argv[1]
        if not usable(s):
            print("FAIL the unit is below API 17"); return 1
        if not ensure(s):
            print("FAIL the helper did not build or install"); return 1
        word, rest = call(s, argv[0], argv[2] if len(argv) > 2 else None)
        print(f"{word} {rest}".strip())
        return 0 if word != "FAIL" else 1
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
