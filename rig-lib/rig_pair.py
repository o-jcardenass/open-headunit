#!/usr/bin/env python3
"""Pair two rig units over Bluetooth through their Settings screens, with every tap checked.

The initiator (a phone) scans and asks; the target (a head unit) is discoverable while its own Bluetooth
screen is open, and confirms. Each step is checked on a fresh uiautomator dump before the next:

  1. both units read their own address; if each already lists the other's address, nothing is done; a
     side that alone lists the other (a half bond) is forgotten first with rig_forget.py
  2. the target opens Bluetooth settings (discoverable while it is open); the initiator opens Bluetooth
     settings and taps "Pair new device" when the ROM has one
  3. the initiator's scan list shows exactly one row with the target's CURRENT name (read from the target
     itself, so a renamed unit is found by its new name); it is polled for up to 40 s
  4. both pairing dialogs come up; when both print a 6-digit code the codes must be equal, then each
     side's one Pair button is tapped
  5. both bonded lists are read until each holds the other's address

Anything that does not match exactly once stops the flow with a reason; the caller then falls back to an
OPERATOR line. When the initiator is API 29 or up, rig_bt.py pairs through the API first: the helper asks on the
initiator and accepts on a target that is also API 29 or up, or a target below that gets one tap on its own
dialog. The screen flow above runs only when that fails. Dumps and a step log go to EV/pair/<initiator>-<target>-<n>/.

  rig_pair.py pair --ev EV INITIATOR_SERIAL TARGET_SERIAL   exit 0 paired (or already), 1 stopped, 2 bad use
"""
import re, sys, time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rig_bt  # noqa: E402
from rig_forget import DUMP, Forget, bonded_entries, find_text, nodes, own_address, run_adb, text_of

PAIR_NEW = ("pair new device", "+ pair new device", "vincular nuevo dispositivo", "sincronizar nuevo dispositivo")
PAIR_BUTTON = ("pair", "emparejar", "vincular", "sincronizar")
ACCEPT = ("ok", "aceptar")   # Samsung 4.4 names the button OK on its passkey dialog
CODE = re.compile(r"\b(\d{6})\b")


def own_name(settings_value, dump):
    v = (settings_value or "").strip()
    if v and v != "null":
        return v
    m = re.search(r"^\s*name:\s*(.+)$", dump.split("Bonded devices")[0], re.M)
    return m.group(1).strip() if m else None


def find_row(xml_text, name):
    """(x, y) of the one node whose text is NAME, or (None, reason)."""
    root, _ = nodes(xml_text)
    if root is None:
        return None, "the screen dump did not parse"
    hits = [n for n in root.iter("node") if text_of(n).casefold() == name.casefold()]
    if len(hits) != 1:
        return None, f"{len(hits)} rows read {name!r} (need exactly 1)"
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", hits[0].get("bounds", "")))
    return ((x1 + x2) // 2, (y1 + y2) // 2), None


def code_of(xml_text):
    root, _ = nodes(xml_text)
    if root is None:
        return None
    codes = {m.group(1) for n in root.iter("node") for m in CODE.finditer(text_of(n))}
    return codes.pop() if len(codes) == 1 else None


def accept_button(xml_text):
    """(x, y) of the one Pair button, or of the one OK button on a dialog that shows a pairing code."""
    xy, why = find_text(xml_text, PAIR_BUTTON, button_only=True)
    if xy or not why.startswith("none of") or not code_of(xml_text):
        return xy, why
    return find_text(xml_text, ACCEPT, button_only=True)


class Pair:
    def __init__(self, initiator, target, ev, adb=run_adb, sleep=time.sleep, ensure=None):
        self.i, self.t, self.adb, self.sleep = initiator, target, adb, sleep
        self.ensure = ensure or rig_bt.ensure
        base = Path(ev) / "pair"
        base.mkdir(parents=True, exist_ok=True)
        stem = f"{initiator}-{target}".replace(":", "_")
        self.dir = base / f"{stem}-{len(list(base.glob(stem + '-*'))) + 1}"
        self.dir.mkdir()
        self.taps = 0

    def log(self, msg):
        with (self.dir / "steps.log").open("a") as f:
            f.write(f"{time.strftime('%T')} {msg}\n")

    def dump(self, s, tag):
        for _ in range(3):
            self.adb(s, "shell", "uiautomator", "dump", DUMP)
            text = self.adb(s, "shell", "cat", DUMP)
            if "<hierarchy" in text:
                (self.dir / f"{tag}.xml").write_text(text)
                return text
            self.sleep(2)
        return ""

    def tap(self, s, xy, what):
        self.taps += 1
        self.log(f"tap {self.taps} {what} at {xy[0]},{xy[1]} on {s}")
        self.adb(s, "shell", "input", "tap", str(xy[0]), str(xy[1]))
        self.sleep(2)

    def home(self):
        for s in (self.i, self.t):
            self.adb(s, "shell", "input", "keyevent", "KEYCODE_HOME")
            self.adb(s, "shell", "rm", "-f", DUMP)

    def stop(self, reason):
        self.log(f"STOP {reason}")
        self.home()
        return 1, reason

    def addr(self, s):
        return own_address(self.adb(s, "shell", "settings", "get", "secure", "bluetooth_address"),
                           self.adb(s, "shell", "dumpsys", "bluetooth_manager"))

    def lists(self, s, a):
        """True or False when S's bonded list reads, None when it does not."""
        b = rig_bt.bonds(s, self.adb, self.ensure)
        return None if b is None else any(x == a for x, _ in b)

    def api(self, s):
        """The helper pairs and confirms on S with no tap (API 29 and up)."""
        return rig_bt.confirms(s, self.adb) and self.ensure(s, self.adb)

    def clear_half(self):
        """Forget a side that alone lists the other (the scan never shows a device still bonded). A reason on failure."""
        for side, other in ((self.i, self.t), (self.t, self.i)):
            if self.lists(side, self.addr(other)) and self.lists(other, self.addr(side)) is False:
                rc, msg = Forget(side, other, self.dir, adb=self.adb, sleep=self.sleep, ensure=self.ensure).run()
                self.log(f"half bond on {side}: {msg}")
                if rc:
                    return f"a half bond on {side} could not be forgotten: {msg}"
        return None

    def api_pair(self, ai, at):
        """API 29 and up on the initiator: the helper asks and accepts its side. The target accepts through the helper
        too, or, below API 29, with one tap on its own dialog. False sends the caller to the Settings flow."""
        if not self.api(self.i):
            return False
        far = rig_bt.call_async(self.t, "accept", ai, 60, self.adb) if self.api(self.t) else None
        if far:
            self.sleep(1)
        else:
            self.adb(self.t, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
            self.adb(self.t, "shell", "am", "start", "-S", "-a", "android.settings.BLUETOOTH_SETTINGS")
        near = rig_bt.call_async(self.i, "pair", at, 45, self.adb)
        if not far:
            for n in range(20):
                if not near.is_alive():
                    break
                xy, _ = accept_button(self.dump(self.t, f"api-dialog-t-{n}"))
                if xy:
                    self.tap(self.t, xy, "Pair on the target (below API 29)")
                    break
                self.sleep(2)
        near.join(60)
        if far:
            far.join(70)
        self.log(f"api pair: initiator {' '.join(near.result)}; target {' '.join(far.result) if far else 'screen'}")
        for _ in range(10):
            if self.lists(self.i, at) and self.lists(self.t, ai):
                self.log(f"RESULT paired verified_by=api taps={self.taps}")
                return True
            self.sleep(1)
        self.log("the API did not pair; trying the Settings screens")
        return False

    def run(self):
        ai, at = self.addr(self.i), self.addr(self.t)
        if not (ai and at):
            return self.stop(f"an address is unreadable (initiator {ai}, target {at})")
        if self.lists(self.i, at) and self.lists(self.t, ai):
            self.log("RESULT already paired")
            return 0, "already paired on both sides"
        why = self.clear_half()
        if why:
            return self.stop(why)
        if self.api_pair(ai, at):
            self.home()
            return 0, f"paired through the API in {self.taps} taps"
        why = self.clear_half()
        if why:
            return self.stop(why)
        name = own_name(self.adb(self.t, "shell", "settings", "get", "secure", "bluetooth_name"),
                        self.adb(self.t, "shell", "dumpsys", "bluetooth_manager"))
        if not name:
            return self.stop("the target's Bluetooth name is unreadable")
        self.log(f"initiator {ai}, target {at} named {name!r}")
        for s in (self.t, self.i):
            self.adb(s, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
            self.adb(s, "shell", "wm", "dismiss-keyguard")
            self.adb(s, "shell", "am", "start", "-S", "-a", "android.settings.BLUETOOTH_SETTINGS")
        self.sleep(3)
        screen = self.dump(self.i, "1-list")
        xy, _ = find_text(screen, PAIR_NEW)
        if xy:
            self.tap(self.i, xy, "pair new device")
        xy, why = None, "the scan list never showed the target"
        for n in range(14):
            screen = self.dump(self.i, f"2-scan-{n}")
            xy, why = find_row(screen, name)
            if xy:
                break
            self.sleep(3)
        if not xy:
            return self.stop(f"scan: {why}")
        self.tap(self.i, xy, f"row {name!r}")
        di, dt = "", ""
        for n in range(8):
            di, dt = self.dump(self.i, f"3-dialog-i-{n}"), self.dump(self.t, f"3-dialog-t-{n}")
            if accept_button(di)[0] and accept_button(dt)[0]:
                break
            self.sleep(2)
        bi, why_i = accept_button(di)
        bt, why_t = accept_button(dt)
        if not (bi and bt):
            return self.stop(f"pairing dialogs: initiator {why_i or 'ok'}, target {why_t or 'ok'}")
        ci, ct = code_of(di), code_of(dt)
        self.log(f"codes initiator={ci} target={ct}")
        if ci and ct and ci != ct:
            return self.stop(f"the two pairing codes differ ({ci} and {ct}): another device answered")
        self.tap(self.i, bi, "Pair on the initiator")
        self.tap(self.t, bt, "Pair on the target")
        for _ in range(15):
            if self.lists(self.i, at) and self.lists(self.t, ai):
                self.home()
                self.log(f"RESULT paired codes={ci}/{ct} taps={self.taps}")
                return 0, f"paired {name!r} in {self.taps} taps"
            self.sleep(1)
        return self.stop("the bonded lists do not show the pair after the taps")


def main(argv):
    if len(argv) == 5 and argv[0] == "pair" and argv[1] == "--ev":
        rc, msg = Pair(argv[3], argv[4], argv[2]).run()
        print(("PAIR_OK " if rc == 0 else "PAIR_STOPPED ") + msg)
        return rc
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
