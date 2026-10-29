# native-bt-broker-esp32, finding: rig inventory of the two ESP32 boards, what is on them, and what the candidate needs

Gathered 2026-10-25 16:47 to 16:56 (host PC clock, local time) by the testing side, to give the coding side what it needs to write a brief. No round was run, nothing was flashed, no settings were written on any phone or head unit. Observed facts and code-reading claims are kept apart below.

**Candidate under test:** the contributor's open PR that adds ESP32 broker support to Native AA, head `79298974` (`792989746b8f71b03562e44e067ee4dd47594c4f`), branch `main-aa-native-bt-broker`, base `main`, 16 files, +2209 -2, no review comments yet. Its description says it was tested on one phone (a Galaxy S25) only and asks for other phones. It adds a "Native BT Broker" settings screen and a `connection/broker/` package.

**Firmware under test:** the contributor's separate ESP32 repo, release `v1.0.0` (repo HEAD `2a1cb58c`, 2026-10-23). Release assets were downloaded and hashed on the host; they were not written to either board.

## 1. The two boards (observed)

Both are classic ESP32, so both can run the broker (it needs Bluetooth Classic; the S3 would not work). Both were reached with esptool 5.6.0 at 115200 and 460800 baud and answered normally, so both are flashable from this PC with no button presses (auto-reset via RTS/DTR worked first try; board E-2 needed a few more connect retries).

| | E-1 | E-2 |
|---|---|---|
| Host tty today | `/dev/ttyUSB0` | `/dev/ttyUSB1` |
| Stable path | `/dev/serial/by-path/pci-0000:00:14.0-usb-0:1.4:1.0` | `/dev/serial/by-path/pci-0000:00:14.0-usb-0:1.3:1.0` |
| USB bridge | CP2102, `10c4:ea60` | CP2102, `10c4:ea60` |
| Chip | ESP32-D0WD-V3 rev v3.1, dual core, 240 MHz, 40 MHz crystal | same |
| MAC (Wi-Fi base) | `78:1c:3c:2b:ac:ec` | `70:4b:ca:47:30:88` |
| Flash | 4 MB, 3.3 V, JEDEC manufacturer `5e` device `4016` | 4 MB, 3.3 V, JEDEC manufacturer `1c` device `4116` |
| Partition table now | Arduino default: `nvs` 0x9000, `otadata` 0xe000, `app0` 0x10000 size 0x140000 (1.25 MB) | different layout: `phy_init` 0xf000, `otadata` 0x10000, `nvs` 0x12000; `app0` not at 0x10000 |
| What runs now | A stock LED blink sketch. Boot log at 115200: `Turning LED ON` / `Turning LED OFF` repeating. App image is Arduino/ESP-IDF `v5.5-1-gb66b5448e0`, built Jul 22 2025. | Unidentified. Output at 115200 is a repeating non-text pattern; 74880, 57600, 38400, 230400, 921600 and 9600 baud also give no readable text. |
| Broker firmware on it | **No** | **No** |

**Traps on this PC**

- Both bridges report the same USB serial number `0001`, so `/dev/serial/by-id/` holds one link only (it points at whichever enumerated last, `ttyUSB1` at the time of writing). Do not use by-id. Use the by-path names above, or the MAC from `esptool chip-id`. `ttyUSB0`/`ttyUSB1` swap if the cables are replugged.
- The user is in group `dialout`, so no sudo is needed.
- Neither board had a reader holding the port at the end. A stray `cat /dev/ttyUSB1` left from a passive read did hold E-2 for about a minute during this session and blocked `stty`; check `fuser -v /dev/ttyUSB*` before esptool.
- Opening either port with esptool or toggling RTS resets the board (hard reset on exit). That is harmless here because neither runs anything that matters, but it will restart a flashed broker and drop its BLE link.
- Not installed on the host: `esptool`, `pyserial`, `xxd`, PlatformIO. I installed esptool into a throwaway venv in the session scratchpad only. The testing side needs `python3 -m venv <dir> && <dir>/bin/pip install esptool` before any flash step, or the brief should say the coding side supplies it.

E-2's original firmware is unknown and not backed up. If the brief flashes E-2, the first step should be `esptool --port <E-2 path> read-flash 0 0x400000 e2-original-4MB.bin` (about 3 min at 460800) so it can be restored. E-1 only runs a blink sketch and needs no backup.

## 2. Release artifacts (observed, downloaded and hashed)

Release `v1.0.0` of the broker repo, flash offsets from its README (`bootloader.bin` 0x1000, `partitions.bin` 0x8000, `boot_app0.bin` 0xe000, `firmware.bin` 0x10000):

```
22d12685b5d131492a54cc1ee491768ac4c3be93b7a10ba05fdabb3636533534  bootloader.bin   23488
d90b0f480968f1cde746d7d8746261b38ec098b63c7f45b1aa23698cca4a2236  partitions.bin    3072
f94c5d786a7a8fab06ac5d10e33bf37711a6697636dc037559ea19cc410a17f0  boot_app0.bin     8192
9e62a6b87ab535e25e2da83389d7378d2cd56a3ee4adcde4b72b8b425314496b  firmware.bin   1726736
```

`firmware.bin` contains the strings `OpenHU-NativeBTBroker`, `OpenHU-NativeBTBroker-Setter`, `AndroidAuto`, `broker self-check failed crc=%04x rc=%d`. Its partition table is the repo's `huge_app_32mbit.csv` (`app0` 0x10000, 3 MB, `spiffs` at 0x310000), which neither board has today, so the brief must flash all four files, not only `firmware.bin`.

Flash command for the brief (README form, Linux port):

```bash
esptool --chip esp32 --port <by-path> --baud 460800 write-flash -z \
  0x1000 bootloader.bin 0x8000 partitions.bin 0xe000 boot_app0.bin 0x10000 firmware.bin
```

Pass condition for the flash itself, from the README: the serial log at 115200 does **not** print `broker self-check failed`, and the board advertises `OpenHU-NativeBTBroker-Setter` over BLE and `OpenHU-NativeBTBroker` over Classic (PIN `0000`). Not run: this is what to check, not something I saw.

## 3. The rig side (observed unless marked)

`adb devices` showed three units, all with `android.hardware.bluetooth` and `android.hardware.bluetooth_le`:

| Role (by model) | Model | Android |
|---|---|---|
| D-HU | MT50_YT610E4GFPSL_U | 14 |
| D-POCO | M2007J20CG | 15 |
| D-MOTO | motorola edge 30 neo | 14 |

D-SAM, D-HP and D-T230 were not attached today. The head unit side of the broker uses BLE GATT, so D-HU is the natural head unit; the phone side is classic Bluetooth, so D-POCO or D-MOTO. The host PC also has its own Bluetooth adapter (`0489:e036`), which could act as an independent BLE observer of the broker's advertising; not tried.

## 4. What the candidate does that matters to a brief (code reading only, nothing run)

Read from the PR head. Treat as hypotheses until a round runs them.

- **Settings do not live in `settings.xml`.** The saved broker is in its own SharedPreferences file `NativeBTBroker`, keys `address` and `name` (`NativeBTBrokerStore.kt`). House rule 3 (write prefs only in `shared_prefs/settings.xml`) does not cover it, so the brief must say how to seed or clear it (same pushed-script pattern on `shared_prefs/NativeBTBroker.xml`), and how to tell a stale pairing. A legacy prefs name is migrated on first read.
- **Pairing is a UI flow.** Settings, Native BT Broker, Scan, pick the `OpenHU-NativeBTBroker-Setter` entry, Pair. The scan runs 8 s. Permissions asked: `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` (API 31+) and `ACCESS_FINE_LOCATION`. No deep link or intent for pairing was found, so house rule 2 will need the "no scriptable trigger" exception in Setup notes, with the minimum taps. The phone also has to bond to `OpenHU-NativeBTBroker` once by hand (PIN `0000`).
- **Wire protocol:** GATT service `8e6d1000-3f45-4f1e-a000-6e2b8d012345`, characteristics `...1001` command, `...1002` event, `...1003` data RX, `...1004` data TX; 12-byte frame header, max payload 4096; commands `HELLO` 0x01, `SET_NETWORK` 0x02, `AA_ENABLE` 0x03, `AA_DISABLE` 0x04, `GET_STATUS` 0x05; projection port 5288. Useful for grepping a capture and for a BLE sniffer.
- **Service hooks:** `AapService` calls `NativeBTBrokerSession.notifyProjection(true)` at `HandshakeComplete` and in `onConnected()`, and `notifyProjection(false)` when a session ends, all gated on `hasSavedBroker()`. The PR description says the broker drops HFP once projection is up. A brief can check "broker releases HFP at SSL" against the firmware's serial log (`rx frame` / `tx frame` lines) rather than guessing.
- **Interaction with existing Native AA rules:** the PR touches `NativeAaHandshakeManager` (+74 -1) and `ExternalModuleCarrier`/`BlinkAaCarrier`. The two feedback loops in the project notes (self-wake through `ACL_CONNECTED`, credentials re-delivered several times per group) were not checked against it. The broker's HFP enable could raise an ACL event the same way `triggerPoke()` does; worth one count of `MATCH! Starting AapService` per attempt.
- **Calls:** the PR claims the brief HFP link does not interrupt a call (tested on one phone). Only D-MOTO can place calls on this rig.

## 5. Not done, and open questions for the brief

- Nothing was flashed; no BLE or Bluetooth pairing was attempted; the candidate APK was not built or installed (house rule on builds: ask first).
- E-2's firmware is unidentified. It may be a bootloader-less or different-clock image. If it is not wanted back, it can simply be overwritten.
- Which phone pairs with which board is a brief decision. Two boards allow a two-phone arm (one broker per phone), but the head unit stores only one saved broker, so a second pairing means Forget then Scan again.
- The host has `/dev/ttyUSB*` serial logs available during a run, which no previous round had: a brief can ask for `stdbuf -oL` capture of the broker's own serial log next to the head unit's logcat. Both captures need the same wall-clock note (host and device clocks differ, so record the offset).
