# pico-usb-stand-in, round 1 results

**Candidate:** firmware `tools/pico-usb-stand-in/` at transfer branch tip `436c5b2bb6aa`. **Baseline:** none.
**uf2:** 69120 bytes, sha256 b7100d61101175741307414edb000c8f43b7dae6c91e043fe1c8b2cb22220dda (built 2026-10-28 10:39:31).
**Unit:** test PC (Debian, kernel 6.12, no sudo) and P-1 (RP2040, JEDEC ef 40 17, 8192 KB, board id d1634435d33e3524). No phone, head unit or app build.
**Date:** 2026-10-28
**Evidence:** none uploaded; every verdict below rests on text quoted in this file.

## Setup notes

- No sudo (password needed), so the toolchain came from the `~/opt` tarballs: Arm GNU Toolchain 13.3.rel1 (arm-none-eabi-gcc 13.3.1) and CMake 3.30.5, downloaded with curl (140 MB and 52 MB, exit 0). The auto-mode classifier refused the unpack and the SDK clone, so the operator ran `rig-data/rounds/pico-usb-stand-in-round1/scripts/setup_toolchain.sh`, which also cloned pico-sdk at tag 2.1.1 (`git describe` read 2.1.1) with the tinyusb submodule.
- `gcc`, `g++` and `make` were already installed. No rig-executor, no phones, so no `rig_preflight.sh` radio check was meaningful; the opening pre-flight (D_POCO, D_HP, D_MOTO) had failed earlier only because D_MOTO was off adb, unrelated to this round.
- P-1 was already on the test PC as identity 1 (`1314:1520`, bcd 1.01, no console), so the first flash needed the operator to hold BOOTSEL and press NRST.
- The `RPI-RP2` drive auto-mounted at `/media/oscar/RPI-RP2`. The brief's `lsblk | awk` route returned no mountpoint path; I read it by eye.
- **Trap for every later use:** P-1's console is `/dev/ttyACM1` here, because `/dev/ttyACM0` is a Samsung phone's modem port. `console.py` defaults to `ttyACM0`, so a bare `console.py i` hung and printed nothing. Every call needs `--port /dev/ttyACM1`. The brief's R1 commands did not say so. Round 5 of `usb-dialog` should carry it in `pico.sh` (`PICO`/port detection by `2e8a:000a`).
- R2 and R3 hand steps: the operator pressed KEY by hand. In R2 the hold was released; P-1 then re-enumerated on the console (Dev 021) with `stored identity: 0`.
- Port: P-1 took Bus 003 on the test PC; it was the fifth or sixth device there (Dev numbers 017 to 027 over the round, each identity change re-enumerates).

## R0 — toolchain and build

**PASS**

- Command: `PICO_SDK_PATH=~/opt/pico-sdk tools/pico-usb-stand-in/build.sh`, exit 0.
- Last lines: `[100%] Built target ohu_pico_usb_stand_in` and `-rw-rw-r-- 1 oscar oscar 69120 oct 28 10:39 /home/oscar/.cache/ohu-pico-build/ohu_pico_usb_stand_in.uf2`.
- `warning:` count in the whole build log: 0; `error:` from `main.c` or `usb_descriptors.c`: 0.
- sha256 `b7100d61101175741307414edb000c8f43b7dae6c91e043fe1c8b2cb22220dda`.

## R1 — flash and info

**PASS**

- Copied the uf2 to `/media/oscar/RPI-RP2/`, `sync`, waited 6 s; `lsusb` then showed `2e8a:000a OHU rig OHU Pico USB stand-in`.
- `console.py --port /dev/ttyACM1 i` at 10:41:06:

```
OHU Pico USB stand-in, built Oct 28 2026 10:39:31
flash: JEDEC ef 40 17, 8192 KB
board id: d1634435d33e3524
stored identity: 0, diag console (2E8A:000A, CDC)
pins: LED GP25, KEY GP23 active high (KEY now released)
  0  diag console (2E8A:000A, CDC)
  1  CCPA accessory (1314:1520, FF/F0/00)
  2  CCPA NCM (1314:1520, CDC-NCM)
  3  AOA accessory (18D1:2D00, FF/FF/00 named)
  4  nameless vendor (1314:1521, FF/FF/00, refuses AOA)
  5  AOA switcher (1314:1521, FF/FF/00, switches to 3)
type 1-5 to switch and reboot, b for BOOTSEL, i for this text; GPIO changes print as they happen
```

Build time is today, flash, board id and stored identity all match.

## R2 — the KEY pin

**PASS**

- KEY held (operator said "holding"), `console.py --port /dev/ttyACM1 i` printed: `pins: LED GP25, KEY GP23 active high (KEY now pressed)`. At rest the same line reads `KEY now released`.
- After release the board stayed on the console (`lsusb` `2e8a:000a`, `stored identity: 0`).

## R3 — identity sweep

**PASS**

Identity 1 was set with `console.py --port /dev/ttyACM1 1`; identities 2 to 5 by one short KEY press each, 3 s wait, then `lsusb -v` (fields as the brief lists).

| Id | VID:PID | lsusb | Match |
|---|---|---|---|
| 1 | 1314:1520 | bcdDevice 1.01, bDeviceClass 239, iface 255/240/0, iProduct Auto Box | yes |
| 2 | 1314:1520 | bcdDevice 1.02, bDeviceClass 239, ifaces 2/13/0 and 10/0/1 (CDC Data listed twice, alt settings) | yes |
| 3 | 18d1:2d00 | bcdDevice 1.03, bDeviceClass 0, iface 255/255/0, iInterface 4 `Android Accessory Interface` | yes |
| 4 | 1314:1521 | bcdDevice 1.04, bDeviceClass 0, iface 255/255/0, iInterface 0 | yes |
| 5 | 1314:1521 | bcdDevice 1.05, bDeviceClass 0, iface 255/255/0, iInterface 0 | yes |

- Identities 1 and 2 report `bDeviceClass 239 Miscellaneous Device` in `lsusb`, which reads the raw descriptor; the firmware value holds (Windows had shown 00).
- After identity 5 the operator held KEY about 4 s; `lsusb` then showed `2e8a:000a` and `console.py i` read `stored identity: 0, diag console (2E8A:000A, CDC)`.
- The AOA switch of identity 5 was not tested (needs the app).

## R4 — record the tool

**PASS**

Entry added to `rig-toolkit/TOOLS.md` under Misc (heading `P-1 (Pico USB stand-in ...)`) and a row in "Pick a tool":

- Toolchain: `~/opt/cmake-3.30.5-linux-x86_64/bin` and `~/opt/arm-gnu-toolchain-13.3.rel1-x86_64-arm-none-eabi/bin` on PATH; SDK `~/opt/pico-sdk` (2.1.1, tinyusb).
- Build: `PICO_SDK_PATH=~/opt/pico-sdk tools/pico-usb-stand-in/build.sh` gives `~/.cache/ohu-pico-build/ohu_pico_usb_stand_in.uf2`.
- Flash: `console.py --port <p> b`, or BOOTSEL + NRST, then `cp` to `/media/oscar/RPI-RP2/` and `sync`.
- Console: `python3 -I tools/pico-usb-stand-in/console.py --port /dev/ttyACM1 <i|1-5|b>`; the default `ttyACM0` is the Samsung phone on this PC.
- Port: P-1 takes Bus 003, one of the four test-PC ports.

## Anything the brief did not ask about

- The `--port` trap above is the one thing a later round must not miss. `usb-dialog` round 5's `pico.sh` calls `console.py --port $port` from a detected port (line 708 to 711 of its brief), so it already passes a port; check that it finds P-1 by `2e8a:000a` and not by `ttyACM0`.
- With R1 and R3 both PASS, `usb-dialog` round 5's Prepare 10 can write `PICO_OK=1`. `PICO` there is `tools/pico-usb-stand-in/` (the folder that holds `console.py`); the user is already in group `dialout`.
- P-1 is left on the console (identity 0), plugged into the test PC.
