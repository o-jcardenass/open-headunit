# D-HU quirks

The MT50 head unit. Read for every round that uses it, which is nearly all of them. Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**

The three entries that open with "It" describe D-HU.

- **One hands-free link per device, in both directions, and it shapes any round with two phones or
  two head units.** A phone's Audio Gateway serves one hands-free device, and a head unit serves one
  too: D-HU's own `HeadsetClientService` reports `mMaxHeadsetConnections: 1` / `Max Connected
  Devices = 1` in `dumpsys bluetooth_manager`. So a second head unit cannot take a phone whose
  hands-free another head unit already holds, and a second phone cannot be given a head unit's slot
  while the first holds it. **This is not a defect and no app can fix it** - the disconnect APIs are
  unreachable at a modern target SDK - so never grade an app against beating it. What it means for
  a round is a precondition: whichever pairing a run needs, clear the other one first, by
  disconnecting it or by switching the holder's Bluetooth off. `projection-raise` round 4 hit it
  twice, once inside W3r's own setup and once as a standalone observation, and only the second was
  recognised at the time.

- **`shared_prefs/` can be root-owned, and then the app's own writes never reach disk.** On D-HU
  that directory has been `root:root` while the app runs as its own uid. Reading `settings.xml`
  needs only file permission and works; `SharedPreferences.apply()` writes a temp file and renames
  it, which needs write permission on the **directory** and has none. So every setting the app
  writes updates the in-memory copy correctly and is lost on the next start, for any key. It is
  silent in both directions: a run that reads in-process sees the right value and passes, a run that
  force-stops and reads the file sees the stale one and fails, and neither is the truth. `stat` the
  directory at the start of any round that depends on a value the **app** writes, `chown` it to the
  app's uid:gid if it is wrong, and say in Setup notes what it read first. Values the tester seeds
  are root writes and are unaffected. Measured 2026-08-21 in `connection-failure-banner` round 1,
  which lost its R2c to it; SELinux was checked and is permissive, so that is not the cause.
  **Report what the `stat` read in Setup notes even when it is correct**: `projection-raise` round 2
  did not, and two of its runs graded a value the app writes, so neither result can be read now.
- **The host PC's own firewall can block a listener a round depends on.** `projection-raise` round 2
  needed a rule change before a bare TCP listener on the host could be reached from D-HU at all
  (ICMP worked, TCP did not, on two arbitrary ports). Check host-side reachability before reading a
  head unit's failure to connect to the host as the head unit's fault.
- It **hard-reboots under sustained multi-core spin load**. No spin loops, no CPU stress, ever.
- **Its driver stack floods logcat**, so the ring buffer wraps past several minutes inside one run.
  Prefer the highest log level that still carries the round's lines.
- It **refuses `setSoftApConfiguration()`** but **can read `getSoftApConfiguration()`**; `wlan2` is
  the working access-point interface, `seth_lte0` the modem bridge. Relevant only to hotspot work.
- **There is no non-destructive way to make D-HU leave a network it prefers.** Its `cmd wifi` build
  has `forget-network` but no `disable-network`, and its home network is both stronger and
  higher-priority than any temporary hotspot, so it wins a reconnect race. Round 7 joined the
  hotspot and launched the app back-to-back, faster than the roam-back; that worked every time but
  it is a race, so a run that depends on the station's network should read the frequency back rather
  than assume it.
- **A persistent WiFi Direct group lets an unintended paired phone rejoin in a few seconds,
  independent of Bluetooth state or the app's own poke logic.** With `wifi-direct-stable-identity=
  true`, a phone that has joined the group before can reconnect via the OS-level P2P framework
  alone — no poke, no Bluetooth activity, nothing in the app's own log until the session is already
  forming. Distinct from the "phone's own reconnect beats our poke" entry below, which is about
  Bluetooth/RFCOMM racing the poke; this one is pure WiFi-Direct-framework rejoin. A run that arms
  one specific phone on D-HU while a second paired phone is present should clear
  `wifi-direct-stable-identity` (to `false`), the `wifi-direct-group-name` / `wifi-direct-last-
  group-ssid` / `wifi-direct-last-group-bssid` / `wifi-direct-group-passphrase` keys, and D-HU's
  OS-level saved P2P groups (`cmd wifip2p init`, `list-saved-groups`, `delete-saved-group <id>` for
  each) first. `projection-raise` round 4 lost two W3r captures to this before finding it.
- **D-HU's WiFi HAL cannot hold a SoftAP and a WiFi Direct group-owner interface at the same time,
  and the platform tears the AP down to make room, not the app.** A run that starts a SoftAP by hand
  and then has the app switch to WiFi Direct (`native-ap-transport=0`) loses the AP a few seconds
  into the switch: `HalDevMgr: bestIfaceCreationProposal is null, requestIface=P2P,
  existingIface=[name=wlan2 type=AP, name=wlan0 type=STA]` immediately followed by `WifiService:
  stopSoftAp uid=1073` (`com.android.networkstack.tethering`) and `hostapd: wlan2: AP-DISABLED` —
  nothing in `com.andrerinas.headunitrevived` calls `stopSoftAp`. Any brief whose measurement needs
  both interfaces up at once on this unit cannot be scored here regardless of retries; it needs a
  different unit or a different design. Measured in `wpp-endpoint-depoison-round2`, which lost its R2
  (and R4/R5, which depend on R2's precondition) to exactly this.
- **D-HU's first `start-softap` after an `adb reboot` comes up degraded.** The command prints a
  trailing `Soft AP failed to start. Please check config parameters` while `wlan2` reports an
  address, and nothing can join it. One `stop-softap`/`start-softap` cycle clears it, address
  unchanged. Seen after all three reboots of `hotspot-endpoint-poison` round 1.
- **The app's own hotspot start rarely brings D-HU's AP up inside its window.** `main` and every
  candidate so far give up with `Every start path was tried on`, and `wlan2` sometimes appears late.
  Grade the attempt schedule, never whether the AP came up, unless a brief says otherwise.
- **`cmd wifi get-softap-config` does not exist on D-HU's build.** The persisted AP config is
  `/data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml`, readable as root.
- **D-HU's station did not rejoin `Pegue Cdesta` after two reboots** in `hotspot-endpoint-poison`
  round 1, despite `svc wifi` nudges. Only matters for a run that needs the station.
- **The session's permission classifier may refuse `adb reboot`.** Round 2 of
  `hotspot-endpoint-poison` had the operator reboot D-HU by hand while the session waited on
  `adb wait-for-disconnect` / `wait-for-device`. Confirm the new boot with `boot_count`.
- **A full `settings.xml` restore on D-HU goes in as root, not through `run-as`.** `run-as`'s sandbox
  cannot read `/data/local/tmp`, so `run-as $PKG sh -c 'cat /data/local/tmp/... > shared_prefs/...'`
  fails with `No such file or directory`. Copy the backup to the app's `shared_prefs/` as root, then
  `chown u0_a176:u0_a176` and `chmod 660` it, and read it back before launching.
