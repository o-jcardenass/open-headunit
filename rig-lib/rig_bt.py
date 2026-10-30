#!/usr/bin/env python3
"""The Bluetooth API route: rig-bt-helper (bt-helper/) under the shell's permissions, on API 29 and up.

adb's shell user holds BLUETOOTH_PRIVILEGED and BLUETOOTH_CONNECT on D-HU, D-POCO and D-MOTO (measured
2026-10-30, shell-bt-permission-check-results.md). An instrumentation borrows them, so a bond is removed,
made or accepted through the API with no screen. Below API 29 (D-SAM, D-HP) the borrowing API does not exist,
and rig_forget.py and rig_pair.py use the Settings screens there.

  rig_bt.py list SERIAL                  the unit's own address and its bonded list, through the API
  rig_bt.py unpair SERIAL ADDR           remove the bond; UNPAIR_OK or FAIL
  rig_bt.py pair SERIAL ADDR             ask for a bond and accept this side's request; PAIR_OK or FAIL
  rig_bt.py accept SERIAL ADDR           wait for ADDR's request, accept it, wait for the bond; ACCEPT_OK or FAIL
"""
import hashlib, re, subprocess, sys, threading
from pathlib import Path

HERE = Path(__file__).resolve().parent
APK = HERE / "bt-helper" / "build" / "rig-bt-helper.apk"
PKG = "ohu.rig.bthelper"
COMPONENT = PKG + "/.BtInstrumentation"
RESULT = re.compile(r"INSTRUMENTATION_RESULT: result=(.*)")


def run_adb(serial, *args, timeout=20):
    try:
        r = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, timeout=timeout)
        return r.stdout.replace("\r", "")
    except Exception:
        return ""


def sdk(serial, adb=run_adb):
    v = adb(serial, "shell", "getprop", "ro.build.version.sdk").strip()
    return int(v) if v.isdigit() else 0


def usable(serial, adb=run_adb):
    return sdk(serial, adb) >= 29


def build():
    if not APK.exists():
        subprocess.run(["bash", str(HERE / "bt-helper" / "build.sh")], capture_output=True, text=True, timeout=300)
    return APK.exists()


def ensure(serial, adb=run_adb):
    """Install the helper when the unit lacks it or holds another build; grant BLUETOOTH_CONNECT. True when ready."""
    if not build():
        return False
    want = hashlib.md5(APK.read_bytes()).hexdigest()
    path = adb(serial, "shell", "pm", "path", PKG).strip().replace("package:", "")
    have = adb(serial, "shell", "md5sum", path).split(" ")[0] if path else ""
    if have != want:
        adb(serial, "install", "-r", "-t", str(APK), timeout=120)
        path = adb(serial, "shell", "pm", "path", PKG).strip().replace("package:", "")
        if not path or adb(serial, "shell", "md5sum", path).split(" ")[0] != want:
            return False
    adb(serial, "shell", "pm", "grant", PKG, "android.permission.BLUETOOTH_CONNECT")
    return True


def args_for(cmd, addr=None, timeout=30):
    a = ["shell", "am", "instrument", "-w", "--no-hidden-api-checks", "-e", "cmd", cmd, "-e", "timeout", str(timeout)]
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
    return parse(adb(serial, *args_for(cmd, addr, timeout), timeout=timeout + 30))


def call_async(serial, cmd, addr=None, timeout=60, adb=run_adb):
    """The same call on a thread (the far side of a pair). join() it, then read .result as (WORD, detail)."""
    t = threading.Thread(target=lambda: setattr(t, "result", call(serial, cmd, addr, timeout, adb)), daemon=True)
    t.result = ("FAIL", "the background call did not end")
    t.start()
    return t


def main(argv):
    if len(argv) >= 2 and argv[0] in ("list", "unpair", "pair", "accept"):
        s = argv[1]
        if not usable(s):
            print("FAIL the unit is below API 29"); return 1
        if not ensure(s):
            print("FAIL the helper did not build or install"); return 1
        word, rest = call(s, argv[0], argv[2] if len(argv) > 2 else None)
        print(f"{word} {rest}".strip())
        return 0 if word != "FAIL" else 1
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
