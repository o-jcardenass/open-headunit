# D-DONGLE quirks

The rig's wireless Android Auto USB dongle. Read for every round that plugs it in. Measured in `usb-dongle-regression` round 1 (2026-10-23) unless an entry says otherwise.

- **It is not the model users report.** It enumerates as `Google Pixel 4`: idle `18d1:4ee1`, then `18d1:2d00` after the AOA switch. A user's failing dongle on 3.5.0 was a `smartBox` (`2717:FF40`). A pass here does not clear another model.
- **It is bonded to D-MOTO as `carplay_box_F96B`, and it reaches the phone over its own WiFi.** So D-MOTO stays on its PC cable with WiFi and Bluetooth on while the dongle is plugged in, and the phone capture comes from D-MOTO.
- **D-HU cannot host it** (`topics/tooling.md`). The hosts are D-HP (API 17) and D-POCO (API 35), each on wireless adb with the dongle on its OTG port.
- **It draws power from the host.** D-HP's degraded battery died mid-run once with it plugged in. Gate D-HP at 60% to start and stop at 30%, read before every cycle.
- **A warm re-attach carries 2 to 2.4 KB of stale data, and the first version request then fails with a detach.** v.3.5.0 retries and reaches SSL on the next attach; v.3.4.0 never did (0 of 5). Grep `stale USB bytes before version request` and `discarded a late VERSION_RESPONSE`.
- **Its version answer is slow**, 1.8 to 2.9 s, because it relays to the phone over WiFi. `the phone selected 1.7` on every cycle.
- **A host that shows only root hubs in `/sys/bus/usb/devices` needs the dongle reseated.** Once per run is enough.
- **Brief traps from its first round:** `mark` must also write `log -t RIGMARK` on the phone; the build copies from `rig-data/apks/`; D-HP has no `adb exec-in`.
