# Pico USB stand-in (P-1)

Firmware for an RP2040 board that presents a chosen USB identity to a head unit. It stands in for devices the rig does not own, first of all the stock Carlinkit CCPA, so a round can test the app's USB identity check and the USB blacklist on hardware.

The board is **P-1**: a "Pico RP2040" clone with USB-C, 8 MB Winbond flash (JEDEC `ef 40 17`), and three buttons: BOOTSEL, NRST (reset) and KEY (GP23). It matches the WeAct Studio RP2040 layout. Its chip ID is `d1634435d33e3524`. The firmware runs on the 2 MB and 4 MB versions too.

## Identities

| # | Presents | VID:PID | Interfaces | Manifest filter | App verdict (`UsbDeviceIdentityPolicy`) |
|---|---|---|---|---|---|
| 0 | diag console | `2E8A:000A` | CDC-ACM | no match | rejected: CDC-ACM serial device |
| 1 | stock CCPA, accessory gadget | `1314:1520` | `FF/F0/00`, bulk pair | no match | rejected: no Android interface |
| 2 | stock CCPA, NCM mode | `1314:1520` | CDC-NCM `02/0D/00` + `0A/00/01` | no match | rejected: no Android interface |
| 3 | AOA accessory | `18D1:2D00` | `FF/FF/00` named "Android Accessory Interface" | match | accepted: already in accessory mode |
| 4 | nameless vendor device | `1314:1521` | `FF/FF/00`, no name | match | accepted: AOAP (vendor interface named nothing) |
| 5 | AOA switcher | `1314:1521` | as 4 | match | as 4, then the switch works and it comes back as 3 |

- Identities 1 to 5 report "Magic Communication Tec." / "Auto Box" and serial `OHUPICO0001`. Identity 1 and 2 also report device class `EF/02/01`, like the real box.
- Each identity reports `bcdDevice` 1.0N, where N is its number. That is the only way `lsusb` tells 4 from 5. The app does not read it.
- Identity 4 stalls the AOA requests 51 to 53, so the app's switch fails. Identity 5 answers 51 with protocol 2, accepts 52 and 53, and re-enumerates as identity 3 about 50 ms after 53. An unplug returns it to identity 5, the way a phone leaves accessory mode.
- Identities 3 and 5 never answer on the bulk pipe. The app's session after the switch times out. That is expected.
- Identities 4 and 5 share `1314:1521`. Below API 21 the blacklist keys on VID:PID only, so blacklisting one blacklists both.
- The bulk endpoints are 64 bytes (USB full speed). The real box is high speed with 512-byte endpoints. The app's checks do not read the endpoint size.
- What the real box shows in each mode comes from firmware analysis, not from a head-unit capture. Those notes stay on the coding side. A real CCPA log stays the authority.

## Buttons and LED

- **LED (GP25):** blinks the identity number, then pauses. The console (identity 0) blinks slowly, once a second.
- **KEY or BOOTSEL, short press:** steps to the next identity (1, 2, 3, 4, 5, 1...), stores it, and reboots. It never steps to the console.
- **KEY or BOOTSEL, held 3 s:** goes back to the console at 3 s, while the key is still down. The release after it does nothing.
- **NRST:** reboots and keeps the stored identity.
- **BOOTSEL held while you press NRST or plug in:** the ROM bootloader. The board shows up as the drive `RPI-RP2`.
- A newly flashed build always starts on the console, whatever an older build stored.

On P-1, KEY is GP23 and **active-high**: a press ties it to 3V3, and it reads low at rest. That was measured with the console's pin scan on 2026-10-28, and the sweep 1 to 5, hold to console, passed. The console's `i` text prints `KEY now pressed` while the key is held. On another board, find the key with the pin scan (the console prints `GPn high` / `GPn low` as pins change) and rebuild with `-DKEY_PIN=<n> -DKEY_ACTIVE_HIGH=0|1`. BOOTSEL does the same job as KEY on any board.

## Build

The first build needs internet access: the SDK downloads and builds `picotool` from source.

1. Install the toolchain. With sudo: `sudo apt install gcc-arm-none-eabi cmake build-essential git`. Without sudo, unpack the Arm GNU Toolchain 13.3.rel1 (`arm-gnu-toolchain-13.3.rel1-x86_64-arm-none-eabi.tar.xz`) and CMake 3.30.5 (`cmake-3.30.5-linux-x86_64.tar.gz`) into `~/opt` and put their `bin` folders on `PATH`.
2. Get the SDK: `git clone --depth 1 -b 2.1.1 https://github.com/raspberrypi/pico-sdk.git ~/opt/pico-sdk`, then `git -C ~/opt/pico-sdk submodule update --init --depth 1 lib/tinyusb`.
3. Build: `PICO_SDK_PATH=~/opt/pico-sdk tools/pico-usb-stand-in/build.sh`.
4. The output is `~/.cache/ohu-pico-build/ohu_pico_usb_stand_in.uf2`. Never commit it: the branch takes no binaries.

## Flash

1. Put the board in BOOTSEL. If the console runs, run `tools/pico-usb-stand-in/console.py b`. If not, hold BOOTSEL and press NRST.
2. Copy the `.uf2` onto the `RPI-RP2` drive. The board reboots by itself on the console.
3. Check it: `tools/pico-usb-stand-in/console.py i`. The first line names the build time.

## Console

`console.py [--port /dev/ttyACMn] <command>` sends one command and prints the answer. Without `--port`, it finds the board by its console ID `2e8a:000a`, because another device can hold `ttyACM0` (on the test PC, a Samsung phone does). It needs only Python's standard library, and the user must be in group `dialout`.

| Command | Does |
|---|---|
| `i` | Prints the build, flash size, chip ID, stored identity, pins and the identity list. |
| `1` to `5` | Stores that identity and reboots into it. The console is gone until a 3 s hold. |
| `b` | Reboots into BOOTSEL. |

## Use in a round

1. On the test PC, set the identity: `console.py <n>`. Then `lsusb -d <VID:PID>` must list it.
2. Move the board to the head unit's port. D-HP needs a micro-USB OTG to USB-C cable or adapter, and adb must then run over WiFi.
3. Read the app's verdict with `adb logcat -v time OPENHU:V '*:S'` at VERBOSE. Grep the app's own USB identity lines; the brief names the exact strings.
4. To change the identity on the head unit, press KEY or BOOTSEL once and count the LED blinks. That is a hand-operated lever: a brief must name each press as a step for the person at the rig.
