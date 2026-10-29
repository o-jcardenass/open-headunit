# pico-usb-stand-in, round 1: set up the build and check P-1 on the test PC

**Thread:** `pico-usb-stand-in`. **Candidate:** the firmware in `tools/pico-usb-stand-in/` at this branch's tip. **Units:** the test PC and P-1 (the Pico board) only. No phone, no head unit, no app build. **Time:** about 20 minutes, plus the first build's download.

**Why:** P-1 will stand in for a stock Carlinkit CCPA and for AOA devices in USB rounds, starting with the USB blacklist on D-HP. Before a round depends on it, the test PC must be able to build and flash it, and each identity must enumerate as `tools/pico-usb-stand-in/README.md` says.

**Hand steps:** runs R2 and R3 need the person at the rig to press a button. The executor stops and asks for each press. Everything else is scripted.

Read `tools/pico-usb-stand-in/README.md` once before R0.

## R0: toolchain and build

1. Check what is there: `command -v arm-none-eabi-gcc cmake git python3`.
2. If `arm-none-eabi-gcc` or `cmake` is missing, install them as the README "Build" step 1 says. Prefer the `~/opt` tarballs if sudo asks for a password.
3. Get the SDK as the README "Build" step 2 says, unless `~/opt/pico-sdk` already exists at tag `2.1.1`.
4. Build: `PICO_SDK_PATH=~/opt/pico-sdk tools/pico-usb-stand-in/build.sh 2>&1 | tail -5`.

**PASS:** the last line lists `ohu_pico_usb_stand_in.uf2`, about 68 KB, and the build printed no `warning:` or `error:` from `main.c` or `usb_descriptors.c`. Record the size and `sha256sum` of the `.uf2`.

## R1: flash and info

1. Plug P-1 into the test PC. Record the port it takes, because it counts against the four (see `TESTING-TEMPLATE.md`, Units).
2. If `ls /dev/ttyACM*` lists a port and `tools/pico-usb-stand-in/console.py i` prints `OHU Pico USB stand-in`, run `console.py b`. If not, ask the person to hold BOOTSEL and press NRST.
3. Wait for the `RPI-RP2` drive: `lsblk -o NAME,LABEL,MOUNTPOINT | grep RPI-RP2`. If it is not mounted, mount it with `udisksctl mount -b /dev/<name>`.
4. Copy the `.uf2` onto it: `cp ~/.cache/ohu-pico-build/ohu_pico_usb_stand_in.uf2 <mountpoint>/ && sync`.
5. Wait 5 s, then run `tools/pico-usb-stand-in/console.py i`.

**PASS:** the text names today's build time, `flash: JEDEC ef 40 17, 8192 KB`, `board id: d1634435d33e3524` and `stored identity: 0`. Paste the whole text into the results.

## R2: the KEY pin (hand step)

1. Ask the person to hold KEY down and keep it down.
2. While it is down, run `console.py i`.
3. Ask the person to release KEY. A hold of 3 s or more keeps the console; a shorter one switches to identity 1. Either is fine here.

**PASS:** the pins line reads `KEY GP23 active high (KEY now pressed)`. **FAIL:** it reads `KEY now released`. Then record it and use BOOTSEL for every press in R3.

## R3: identity sweep (hand steps)

If `console.py i` still answers, run `console.py 1`. Then `lsusb -d 1314:1520` must list the board.

For each identity, run `lsusb -v -d <VID:PID> 2>/dev/null | grep -E 'idVendor|idProduct|bcdDevice|bDeviceClass|iManufacturer|iProduct|bInterfaceClass|bInterfaceSubClass|bInterfaceProtocol|iInterface'`, and record the output. Then ask the person to press KEY once (or BOOTSEL, if R2 failed) and wait 3 s.

| Identity | VID:PID | Must show |
|---|---|---|
| 1 | `1314:1520` | `bcdDevice 1.01`, `bDeviceClass 239`, one interface `255/240/0`, iProduct `Auto Box` |
| 2 | `1314:1520` | `bcdDevice 1.02`, `bDeviceClass 239`, interfaces `2/13/0` and `10/0/1` |
| 3 | `18d1:2d00` | `bcdDevice 1.03`, `bDeviceClass 0`, interface `255/255/0`, iInterface `Android Accessory Interface` |
| 4 | `1314:1521` | `bcdDevice 1.04`, `bDeviceClass 0`, interface `255/255/0`, iInterface `0` (no name) |
| 5 | `1314:1521` | `bcdDevice 1.05`, otherwise the same as 4 |

After identity 5, ask the person to hold KEY for 4 s. The LED goes to a slow blink at 3 s. Then `console.py i` must print `stored identity: 0`.

Identity 1 and 2 carry `bDeviceClass 239` in the firmware, but Windows reported class `00` for the composite identity 2. Record what `lsusb` prints; it reads the raw descriptor.

**PASS:** every row matches. Report each mismatch with its `lsusb` line. The AOA switch of identity 5 is not tested here; it needs the app as host.

## R4: record the tool

Add an entry for P-1 to `rig-toolkit/TOOLS.md` on the rig: where the toolchain and SDK are, the build and flash commands, the `console.py` path, and the port P-1 takes.

**PASS:** the entry is there. Quote it in the results.

## Report

Write `pico-usb-stand-in-round1-results.md` with one verdict per run (PASS, FAIL, INCONCLUSIVE or UNTESTABLE) and the outputs each run asks for. No captures: everything above is text.
