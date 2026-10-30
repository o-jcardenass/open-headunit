#!/usr/bin/env python3
"""Forget a rig unit's Bluetooth pairing on another unit through its Settings screen, with every tap checked.

Android has no shell command that removes a bond, and BluetoothDevice.removeBond() needs BLUETOOTH_PRIVILEGED
from Android 13, so the only route without root is the Settings screen. The flow is four steps, each one
checked on a fresh uiautomator dump before the next:

  1. the target is found by its Bluetooth ADDRESS in this unit's bonded list, and the row name is the name
     THIS unit shows for that address (units are renamed, and a phone keeps the name it saw at pairing)
  2. Bluetooth settings (fresh, `am start -S`) shows exactly one row with that name and a gear in it
  3. the gear opens a page that names that address (or, where the ROM prints no address, that row name)
     and holds exactly one Forget button
  4. Forget, then the dialog's one "Forget device" button, then the bonded list is read until the address
     is gone

Anything that does not match exactly once stops the flow with a reason and taps nothing more; the caller
then falls back to an OPERATOR line. On API 29 and up rig_bt.py removes the bond through the API first, and
this screen flow runs only when that fails. Dumps and a step log go to EV/forget/<unit>-<other>-<n>/.

  rig_forget.py names SERIAL                       this unit's bonded list: address and name
  rig_forget.py forget --ev EV SERIAL OTHER_SERIAL [--dry-run]
                                                   exit 0 forgotten (or never bonded), 1 stopped (reason
                                                   printed), 2 bad use. --dry-run does steps 1 to 3, then
                                                   leaves with BACK and taps neither Forget button.
"""
import re, subprocess, sys, time
import xml.etree.ElementTree as ET
from pathlib import Path

MAC = re.compile(r"([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})")
FORGET = ("forget", "unpair", "olvidar", "desvincular")
CONFIRM = ("forget device", "olvidar dispositivo", "forget", "unpair", "olvidar", "desvincular")
SEE_ALL = ("see all", "ver todo", "ver todos")
DUMP = "/sdcard/ohu-forget-ui.xml"


def run_adb(serial, *args, timeout=20):
    try:
        r = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, timeout=timeout)
        return r.stdout.replace("\r", "")
    except Exception:
        return ""


# ---------------- pure parts (tested without a device) ----------------

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


def own_address(settings_value, dump):
    """This unit's address: the secure setting, else the first address line above the bonded list."""
    v = (settings_value or "").strip()
    if MAC.fullmatch(v) and v != "02:00:00:00:00:00":
        return v.upper()
    head = dump.split("Bonded devices")[0]
    m = re.search(r"address:\s*" + MAC.pattern, head)
    return m.group(1).upper() if m and m.group(1) != "02:00:00:00:00:00" else None


def nodes(xml_text):
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return None, {}
    parent = {c: p for p in root.iter() for c in p}
    return root, parent


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def text_of(n):
    return (n.get("text") or "").strip()


def find_row_gear(xml_text, name):
    """(x, y) of the gear in the one row whose text is NAME, or (None, reason)."""
    root, parent = nodes(xml_text)
    if root is None:
        return None, "the screen dump did not parse"
    hits = [n for n in root.iter("node") if text_of(n).casefold() == name.casefold()]
    if len(hits) != 1:
        return None, f"{len(hits)} rows read {name!r} on the Bluetooth screen (need exactly 1)"
    row = hits[0]
    for _ in range(5):
        row = parent.get(row)
        if row is None:
            break
        gears = [n for n in row.iter("node") if (n.get("resource-id") or "").endswith("settings_button")]
        if len(gears) == 1:
            return center(gears[0]), None
        if len(gears) > 1:
            return None, f"the row for {name!r} holds {len(gears)} gears"
    return None, f"no gear in the row for {name!r}"


def find_text(xml_text, wanted, button_only=False):
    """(x, y) of the one node whose text is one of WANTED (any case), or (None, reason)."""
    root, _ = nodes(xml_text)
    if root is None:
        return None, "the screen dump did not parse"
    for w in wanted:                                   # first spelling with exactly one hit wins
        hits = [n for n in root.iter("node") if text_of(n).casefold() == w
                and (not button_only or n.get("clickable") == "true")]
        if len(hits) == 1:
            return center(hits[0]), None
        if len(hits) > 1:
            return None, f"{len(hits)} nodes read {w!r}"
    return None, f"none of {list(wanted)} on screen"


def page_is_for(xml_text, addr, name):
    """'address' when the page prints ADDR, 'name' when it only titles NAME, else None."""
    root, _ = nodes(xml_text)
    if root is None:
        return None
    texts = [text_of(n) for n in root.iter("node")]
    if any(addr.casefold() in t.casefold() for t in texts):
        return "address"
    if any(t.casefold() == name.casefold() for t in texts):
        return "name"
    return None


# ---------------- device flow ----------------

class Forget:
    def __init__(self, serial, other, ev, adb=run_adb, sleep=time.sleep, ensure=None):
        import rig_bt
        self.s, self.o, self.adb, self.sleep = serial, other, adb, sleep
        self.ensure = ensure or rig_bt.ensure
        base = Path(ev) / "forget"
        base.mkdir(parents=True, exist_ok=True)
        n = len(list(base.glob(f"{serial}-{other}-*".replace(":", "_")))) + 1
        self.dir = base / f"{serial}-{other}-{n}".replace(":", "_")
        self.dir.mkdir()
        self.taps = 0

    def log(self, msg):
        with (self.dir / "steps.log").open("a") as f:
            f.write(f"{time.strftime('%T')} {msg}\n")

    def dump(self, tag):
        for _ in range(3):
            self.adb(self.s, "shell", "uiautomator", "dump", DUMP)
            text = self.adb(self.s, "shell", "cat", DUMP)
            if "<hierarchy" in text:
                (self.dir / f"{tag}.xml").write_text(text)
                return text
            self.sleep(2)
        return ""

    def tap(self, xy, what):
        self.taps += 1
        self.log(f"tap {self.taps} {what} at {xy[0]},{xy[1]}")
        self.adb(self.s, "shell", "input", "tap", str(xy[0]), str(xy[1]))
        self.sleep(2)

    def stop(self, reason):
        self.log(f"STOP {reason}")
        self.adb(self.s, "shell", "input", "keyevent", "KEYCODE_HOME")
        self.adb(self.s, "shell", "rm", "-f", DUMP)
        return 1, reason

    def bonds(self):
        return bonded_entries(self.adb(self.s, "shell", "dumpsys", "bluetooth_manager"))

    def api_unpair(self, addr):
        """API 29 and up: the helper removes the bond with no screen. False sends the caller to the Settings flow."""
        import rig_bt
        if not (rig_bt.usable(self.s, self.adb) and self.ensure(self.s, self.adb)):
            return False
        word, detail = rig_bt.call(self.s, "unpair", addr, 20, self.adb)
        self.log(f"api unpair: {word} {detail}")
        if word == "UNPAIR_OK" and not any(a == addr for a, _ in self.bonds()):
            self.log("RESULT forgotten verified_by=api taps=0")
            return True
        self.log("the API did not remove the bond; trying the Settings screen")
        return False

    def run(self, dry=False):
        dump_o = self.adb(self.o, "shell", "dumpsys", "bluetooth_manager")
        addr = own_address(self.adb(self.o, "shell", "settings", "get", "secure", "bluetooth_address"), dump_o)
        if not addr:
            return self.stop("the other unit's Bluetooth address is unreadable")
        entries = self.bonds()
        self.log(f"target {addr}; this unit lists {len(entries)} bonds")
        mine = [nm for a, nm in entries if a == addr]
        if not mine:
            self.log("RESULT not bonded")
            return 0, f"{addr} is not bonded here"
        if not dry and self.api_unpair(addr):
            return 0, f"forgot {mine[0]!r} ({addr}) through the API"
        name = mine[0]
        if not name or sum(nm.casefold() == name.casefold() for _, nm in entries) != 1:
            return self.stop(f"the name this unit shows for {addr} ({name!r}) is empty or not unique")
        self.log(f"row name {name!r}")
        self.adb(self.s, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
        self.adb(self.s, "shell", "wm", "dismiss-keyguard")
        self.adb(self.s, "shell", "am", "start", "-S", "-a", "android.settings.BLUETOOTH_SETTINGS")
        self.sleep(3)
        screen = self.dump("1-list")
        xy, why = find_row_gear(screen, name)
        if not xy:
            more, _ = find_text(screen, SEE_ALL)
            if more:
                self.tap(more, "see all")
                screen = self.dump("1b-list")
                xy, why = find_row_gear(screen, name)
        if not xy:
            return self.stop(why + " (screen locked, or the row is below the fold)")
        self.tap(xy, f"gear of {name!r}")
        page = self.dump("2-details")
        by = page_is_for(page, addr, name)
        if not by:
            return self.stop(f"the page after the gear names neither {addr} nor {name!r}")
        xy, why = find_text(page, FORGET)
        if not xy:
            return self.stop(f"details page: {why}")
        self.log(f"details page verified by {by}; Forget at {xy[0]},{xy[1]}")
        if dry:
            self.adb(self.s, "shell", "input", "keyevent", "KEYCODE_BACK")
            self.adb(self.s, "shell", "input", "keyevent", "KEYCODE_HOME")
            self.adb(self.s, "shell", "rm", "-f", DUMP)
            self.log(f"RESULT dry-run ok verified_by={by} taps={self.taps}")
            return 0, f"dry run ok ({by}, {self.taps} taps)"
        self.tap(xy, "Forget")
        dialog = self.dump("3-dialog")
        xy, why = find_text(dialog, CONFIRM, button_only=True)
        if xy:
            self.tap(xy, "confirm")
        else:
            self.log(f"no confirm dialog ({why}); reading the bonds")
        self.adb(self.s, "shell", "input", "keyevent", "KEYCODE_HOME")
        self.adb(self.s, "shell", "rm", "-f", DUMP)
        for _ in range(10):
            if not any(a == addr for a, _ in self.bonds()):
                self.log(f"RESULT forgotten verified_by={by} taps={self.taps}")
                return 0, f"forgot {name!r} ({addr}) in {self.taps} taps"
            self.sleep(1)
        self.log("RESULT still bonded after the taps")
        return 1, f"{addr} is still bonded after the taps"


def main(argv):
    if len(argv) >= 2 and argv[0] == "names":
        for a, n in bonded_entries(run_adb(argv[1], "shell", "dumpsys", "bluetooth_manager")):
            print(f"{a} {n}")
        return 0
    if len(argv) >= 5 and argv[0] == "forget" and argv[1] == "--ev":
        rc, msg = Forget(argv[3], argv[4], argv[2]).run(dry="--dry-run" in argv[5:])
        print(("FORGET_OK " if rc == 0 else "FORGET_STOPPED ") + msg)
        return rc
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
