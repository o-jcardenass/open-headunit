#!/usr/bin/env python3
"""rig_forget.py and rig_pair.py against a simulated phone and head unit: screens, taps and bonded lists.
Run: python3 -I rig-lib/tests/test_bt_ui.py. Prints one ok/FAIL line per case and exits 1 on a failure."""
import sys, tempfile, threading, time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from rig_forget import Forget, bonded_entries, own_address
from rig_pair import Pair

P, H = "PH1", "HU1"
PA, HA, XA = "DC:B7:2E:5E:4E:59", "11:46:03:10:33:59", "78:1C:3C:2B:AC:EE"


def node(text="", rid="", bounds="[0,0][10,10]", clickable="false", kids=""):
    return (f'<node text="{text}" resource-id="{rid}" class="android.widget.TextView" clickable="{clickable}" '
            f'bounds="{bounds}">{kids}</node>')


def hier(*kids):
    return '<?xml version="1.0"?><hierarchy rotation="0">' + "".join(kids) + "</hierarchy>"


class Rig:
    """Two units. names: what each unit calls itself; shown: what P shows for H in its bonded list."""
    def __init__(self, bonded=True, shown="Navegadortz2", h_name="HU renamed", dup_rows=False, locked=False,
                 details_addr=True, code_t="123456", scan_dup=False, half=False, sdk=(19, 19), api_fail=False):
        self.bonds = {P: {}, H: {}}
        if bonded or half:
            self.bonds[P][HA] = shown
        if bonded:
            self.bonds[H][PA] = "POCO X3 NFC"
        self.bonds[P][XA] = "Watch"
        self.addr = {P: PA, H: HA}
        self.name = {P: "POCO X3 NFC", H: h_name}
        self.shown, self.dup_rows, self.locked, self.details_addr = shown, dup_rows, locked, details_addr
        self.code_t, self.scan_dup = code_t, scan_dup
        self.screen = {P: "home", H: "home"}
        self.sdk = {P: sdk[0], H: sdk[1]}
        self.api_fail, self.pairing, self.accepting, self.h_tapped = api_fail, False, False, False
        self.bonded_ev = threading.Event()
        self.taps = {P: [], H: []}

    def dumpsys(self, s):
        rows = "".join(f"  {a} [ BR/EDR ] {n}\n" for a, n in self.bonds[s].items())
        return f"  state: ON\n  address: {self.addr[s]}\n  name: {self.name[s]}\nBonded devices:\n{rows}"

    def view(self, s):
        st = self.screen[s]
        if s == P:
            if st == "list":
                rows = [node(kids=node(self.shown) + node(rid="com.android.settings:id/settings_button",
                                                           bounds="[700,100][760,160]")) for _ in range(1 + self.dup_rows)]
                if HA not in self.bonds[P]:
                    rows = []
                return hier(node("Pair new device", bounds="[0,20][300,60]", clickable="true"), *rows)
            if st == "details":
                foot = node(f"Device's Bluetooth address: {HA}") if self.details_addr else ""
                return hier(node(self.shown), node("Forget", bounds="[100,500][300,560]", clickable="true"), foot)
            if st == "fdialog":
                return hier(node("Forget device?"), node("Forget device", bounds="[400,900][600,960]", clickable="true"))
            if st == "scan":
                rows = [node(self.name[H], bounds="[0,300][400,360]") for _ in range(1 + self.scan_dup)]
                return hier(*rows)
            if st == "pdialog":
                return hier(node("Pairing code 123456"), node("Pair", bounds="[500,900][600,960]", clickable="true"))
        if s == H and (st == "pdialog" or (self.pairing and self.sdk[H] < 29 and st == "list")):
            return hier(node(f"Pairing code {self.code_t}"), node("Pair", bounds="[500,800][600,860]", clickable="true"))
        if st == "locked":
            return hier(node("Swipe up to unlock"))
        return hier(node("Launcher"))

    def tap(self, s, x, y):
        self.taps[s].append((x, y))
        st = self.screen[s]
        if s == P and st == "list" and (x, y) == (730, 130):
            self.screen[P] = "details"
        elif s == P and st == "list" and (x, y) == (150, 40):
            self.screen[P] = "scan"
        elif s == P and st == "details" and (x, y) == (200, 530):
            self.screen[P] = "fdialog"
        elif s == P and st == "fdialog" and (x, y) == (500, 930):
            self.bonds[P].pop(HA, None); self.screen[P] = "list"
        elif s == P and st == "scan" and (x, y) == (200, 330):
            self.screen[P] = self.screen[H] = "pdialog"
        elif s == H and self.pairing and self.sdk[H] < 29 and (x, y) == (550, 830):
            self.h_tapped = True
        elif st == "pdialog" and ((s == P and (x, y) == (550, 930)) or (s == H and (x, y) == (550, 830))):
            self.screen[s] = "paired"
            if self.screen[P] == "paired" and self.screen[H] == "paired":
                self.bonds[P][HA] = self.name[H]; self.bonds[H][PA] = self.name[P]

    def instrument(self, s, a):
        cmd = a[a.index("cmd") + 1]
        addr = a[a.index("addr") + 1] if "addr" in a else None
        if self.api_fail:
            return "INSTRUMENTATION_RESULT: result=FAIL removeBond returned false\n"
        if cmd == "unpair":
            self.bonds[s].pop(addr, None)
            return "INSTRUMENTATION_RESULT: result=UNPAIR_OK removed\n"
        if cmd == "accept":
            self.accepting = True
            ok = self.bonded_ev.wait(3)
            return f"INSTRUMENTATION_RESULT: result={'ACCEPT_OK bonded' if ok else 'FAIL not bonded'}\n"
        if cmd == "pair":
            self.pairing = True
            end = time.time() + 3
            while time.time() < end and not (self.accepting or self.h_tapped):
                time.sleep(0.02)
            if self.accepting or self.h_tapped:
                self.bonds[P][HA] = self.name[H]; self.bonds[H][PA] = self.name[P]; self.bonded_ev.set()
                return "INSTRUMENTATION_RESULT: result=PAIR_OK bonded\n"
            return "INSTRUMENTATION_RESULT: result=FAIL not bonded\n"
        return ""

    def __call__(self, s, *args, timeout=20):
        a = list(args)
        if a[:3] == ["shell", "getprop", "ro.build.version.sdk"]:
            return f"{self.sdk[s]}\n"
        if a[:3] == ["shell", "am", "instrument"]:
            return self.instrument(s, a)
        if a[:3] == ["shell", "dumpsys", "bluetooth_manager"]:
            return self.dumpsys(s)
        if a[:5] == ["shell", "settings", "get", "secure", "bluetooth_address"]:
            return self.addr[s] + "\n"
        if a[:5] == ["shell", "settings", "get", "secure", "bluetooth_name"]:
            return self.name[s] + "\n"
        if a[:2] == ["shell", "am"] and "android.settings.BLUETOOTH_SETTINGS" in a:
            self.screen[s] = "locked" if self.locked else "list"
        elif a[:3] == ["shell", "input", "tap"]:
            self.tap(s, int(a[3]), int(a[4]))
        elif a[:3] == ["shell", "input", "keyevent"] and a[3] in ("KEYCODE_HOME", "KEYCODE_BACK"):
            self.screen[s] = "home"
        elif a[:2] == ["shell", "cat"]:
            return self.view(s)
        return ""


fails = 0


def case(label, ok):
    global fails
    print(("ok   " if ok else "FAIL ") + label)
    fails += not ok


ev = tempfile.mkdtemp()
nosleep = lambda _s: None
ready = lambda _s, _adb: True

case("bonded_entries reads address and name", bonded_entries(Rig().dumpsys(P)) == [(HA, "Navegadortz2"), (XA, "Watch")])
case("own_address takes the dump's line when the setting is masked", own_address("02:00:00:00:00:00", Rig().dumpsys(H)) == HA)

r = Rig(); rc, msg = Forget(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("forget: the row is the name the phone shows, not the unit's new name; 3 taps; the bond is gone",
     rc == 0 and len(r.taps[P]) == 3 and HA not in r.bonds[P] and XA in r.bonds[P])
r = Rig(); rc, msg = Forget(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run(dry=True)
case("forget --dry-run taps only the gear and keeps the bond", rc == 0 and len(r.taps[P]) == 1 and HA in r.bonds[P])
r = Rig(dup_rows=True); rc, msg = Forget(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("forget stops on two rows with that name, with no tap", rc == 1 and not r.taps[P] and "2 rows" in msg)
r = Rig(locked=True); rc, msg = Forget(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("forget stops on a locked screen, with no tap", rc == 1 and not r.taps[P] and HA in r.bonds[P])
r = Rig(details_addr=False); rc, msg = Forget(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("forget accepts a details page that titles the name when the ROM prints no address",
     rc == 0 and HA not in r.bonds[P])
r = Rig(bonded=False); rc, msg = Forget(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("forget does nothing when the unit is not bonded", rc == 0 and not r.taps[P])
r = Rig(shown="Watch"); rc, msg = Forget(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("forget stops when two bonds share the shown name", rc == 1 and not r.taps[P])

r = Rig(bonded=False); rc, msg = Pair(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("pair: found by the target's current name, codes equal, 4 taps, both lists hold the other",
     rc == 0 and len(r.taps[P]) + len(r.taps[H]) == 4 and HA in r.bonds[P] and PA in r.bonds[H])
r = Rig(bonded=False, code_t="654321"); rc, msg = Pair(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("pair stops when the two codes differ, before any Pair tap", rc == 1 and "differ" in msg and not r.taps[H])
r = Rig(bonded=False, scan_dup=True); rc, msg = Pair(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("pair stops when two scanned rows share the target's name", rc == 1 and HA not in r.bonds[P])
r = Rig(); rc, msg = Pair(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("pair does nothing when both sides already list each other", rc == 0 and not r.taps[P])
r = Rig(bonded=False, half=True); rc, msg = Pair(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("pair forgets a half bond first, then pairs", rc == 0 and HA in r.bonds[P] and PA in r.bonds[H])

r = Rig(sdk=(34, 34)); rc, msg = Forget(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("forget on API 29+: the helper removes the bond with no tap", rc == 0 and not r.taps[P] and HA not in r.bonds[P] and "API" in msg)
r = Rig(sdk=(34, 34), api_fail=True); rc, msg = Forget(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("forget falls back to the Settings screen when the helper fails", rc == 0 and len(r.taps[P]) == 3 and HA not in r.bonds[P])
r = Rig(sdk=(34, 34), bonded=False); rc, msg = Pair(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("pair on API 29+ both sides: the helper asks and accepts, no tap", rc == 0 and not r.taps[P] and not r.taps[H]
     and HA in r.bonds[P] and PA in r.bonds[H])
r = Rig(sdk=(34, 19), bonded=False); rc, msg = Pair(P, H, ev, adb=r, sleep=nosleep, ensure=ready).run()
case("pair with a target below API 29: the helper asks, one tap on the target's dialog", rc == 0 and not r.taps[P]
     and len(r.taps[H]) == 1 and HA in r.bonds[P] and PA in r.bonds[H])

sys.exit(1 if fails else 0)
