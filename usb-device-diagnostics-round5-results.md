# usb-device-diagnostics — round 5 results

**Candidate:** `feat/usb-device-list-diagnostics` @ `843b78d7` (3 commits on `main` @ `2f07eeec`)
**Baseline:** none. The before-state is round 4's `r5_bfu_crash.log`, and it was reproduced again
this round (see R1 attempt 2).
**APK md5:** candidate `d9172c5a1de6e972ef6deb1e0888c8b9` (`assembleGithubDebug`,
`3.3.0-beta4` / versionCode 103, built from a clean `git reset --hard fork/feat/...`)
**Unit:** host = motorola edge 30 neo (`miami`, Android 14, `c2.qti.avc.decoder`, 7462 MB RAM),
wireless adb. USB device = Xiaomi POCO X3 NFC (`M2007J20CG`), serial `4f4027e9`, plugged into the
moto over OTG. Head unit MT50 not involved.
**Date:** 2026-08-31

## Verdicts

| run | verdict |
|---|---|
| R0 build gate | **PASS** — 984 / 0 |
| R1 app survives a locked cold boot + blacklist applies | **PASS** (attempt 4; attempts 1–3 were setup problems, see notes) |
| R2 the unlock handover | **PASS** |
| R3 locked boot still switches a phone to accessory mode | **PASS** |
| R4 the unlocked path is unchanged | **PASS** |
| R5 the attach path names the right reason | **PASS** |
| R6 the other two deferrals | not run (skipped by direction — round was clean, no appetite for another reboot) |

`App started in Direct Boot mode (locked). Settings access deferred.` **appeared on R1, R2 and R3**
(the same line, same process each time). Every Direct Boot verdict below rests on it.

## Setup notes

### The moto shipped with a pre-fix build — attempts 1 and 2 tested the wrong APK

The moto arrived with `3.3.0-beta4` / vc103 already installed (updated the evening before the
round). Its `usb_device_filter.xml` matched `843b78d7` byte-for-byte and it carried the
`App started in Direct Boot mode (locked)` **string** — so a filter-xmltree check and a DEX string
grep both passed. They were not enough: that build's `App.onCreate` still calls `getComponent()`
at `App.kt:46`, i.e. it was `9b85bcb4` lineage (the tree round 4 measured), not `843b78d7`. The
`33b241f4` guard-reorder was absent. Confirmed after the fact: the pulled APK has **none** of the
fix's symbols (`initUnlockedOnce`, `App$userUnlockedReceiver$1`, `access$initUnlockedOnce`); the
freshly-built `843b78d7` APK has all three.

- **Attempt 1** — Poco enumerated as `18D1:4EE2` (USB debugging was on), which has no recorded
  default handler, so `UsbAttachedActivity` never launched; the USB attach also landed ~1 min after
  unlock. Untestable.
- **Attempt 2** — Poco debugging turned off → enumerated as `18D1:4EE1`, `UsbAttachedActivity`
  launched at the lock screen (22:39:36, unlock at 22:40:49). **The build crashed three times**,
  identical to round 4's R5:
  ```
  java.lang.IllegalStateException: SharedPreferences in credential encrypted storage are not
  available until after user is unlocked
      at com.andrerinas.openheadunit.utils.Settings.getPrefs(Settings.kt:36)
      at com.andrerinas.openheadunit.utils.Settings.getDebugForceMemoryProfile(Settings.kt:402)
      at com.andrerinas.openheadunit.AppComponent$videoDecoder$1.invoke(AppComponent.kt:20)
      at com.andrerinas.openheadunit.decoder.video.VideoDecoder.<init>(VideoDecoder.kt:323)
      at com.andrerinas.openheadunit.AppComponent.<init>(AppComponent.kt:19)
      at com.andrerinas.openheadunit.App.onCreate(App.kt:46)
  ```
  one from `BootCompleteReceiver` (22:39:00), two from the `UsbAttachedActivity` plug enumerations
  (22:39:37, 22:39:45), each `force-crash` finished. Full filtered capture:
  `evidence/usb-device-diagnostics-round5/prefix-build-3-crashes-attempt2.log`.

The verified `843b78d7` APK was then installed with `adb install -r` (data and the R7 USB grants
preserved — checked) and the round restarted.

### The moto's `main` log buffer is 256 KiB and rolls in seconds

`adb shell logcat -g -b main` → `256 KiB`. `persist.logd.size`, `persist.logd.size.main` and
`persist.adb.tcp.port` are all **refused** on this user build (`Failed to set property … See
dmesg`). `logcat -G 16M` is accepted but resets on reboot, and adb is not up during the Direct Boot
window to run it.

At ~1200–2500 log lines/sec of ROM chatter (Facebook/`AppInitScheduler`, `CompatibilityChangeReporter`
at 2435 lines/run, and dozens of BOOT_COMPLETED wakers), a 256 KiB `main` buffer holds only a few
seconds. **Attempt 3** lost the entire Direct-Boot→unlock window this way: `--------- beginning of
main` sat at 22:53:29, three minutes after the 22:50 events, so every `OPENHU` line was gone even
though the events/system buffers reached back 10 minutes and showed a clean, crash-free
`UsbAttachedActivity` lifecycle.

**Mitigation used for attempt 4 onward:** `persist.log.tag` properties. `persist.log.tag` (bare) →
`E`, plus `persist.log.tag.<TAG>` → `S` on the 28 top text spammers, with `OPENHU`,
`AndroidRuntime`, `DEBUG`, `UsbHostManager`, `UsbDeviceManager` → `V`. These take effect at the next
logd start (i.e. the reboot), and cut the capture from ~25 000 lines to ~7 300 with every decisive
`OPENHU` line intact. **They were reverted at the end** — every `persist.log.tag*` and `log.tag*`
key set back to empty; a 5-second `logcat -b main` sample afterward was back to ~12 400 lines,
confirming the filters are off. The empty property *keys* remain in the property store (only a
factory reset removes a key entirely on a non-root build); they have no effect.

**For the next round:** either accept that anything on `-b main` older than ~10 s post-connect is
gone on this host, or set the same `persist.log.tag` filter before the reboot and revert it after.
`-b events` / `-b system` are fine (they held 10 min).

### Other

- Wireless debugging on the moto changes its port every boot and, twice, needed re-enabling by hand
  after the reboot — once costing ~6 minutes, which is what blew out attempt 3's buffer. Get the
  port back fast.
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `set_usb_blacklist.sh` (round 4),
  `set_prefs_runas.sh`. No new script this round.
- Poco USB debugging must be **off** for it to enumerate as `18D1:4EE1` (the identity the R7 default
  handler is recorded against). With debugging on it is `18D1:4EE2`, no handler, no
  `UsbAttachedActivity`.
- Screen lock confirmed set on the moto before every reboot (`locksettings get-disabled` → `false`,
  `dumpsys trust` → `deviceLocked=1`).

## R0 — build gate

**PASS**

- `./gradlew assembleGithubDebug` → `BUILD SUCCESSFUL`. APK `3.3.0-beta4` vc103, md5
  `d9172c5a1de6e972ef6deb1e0888c8b9`.
- `./gradlew testGithubDebugUnitTest` → **984 tests, 0 failures, 0 errors** (aggregated from
  `test-results/testGithubDebugUnitTest/*.xml`). Exactly the total the brief predicts; no count
  moved.
- Installed-APK identity verified two ways: `usb_device_filter.xml` xmltree byte-identical to source
  (8 entries: `2D00`, `2D01`, `FF/FF/0`, `FF/42/1`, `6/1/1`, `E0/1/3`, `EF/4/1`, `8/6/1`), and the
  DEX carries `initUnlockedOnce` / `App$userUnlockedReceiver$1`.

## R1 — the app survives a locked cold boot, and the blacklist still applies

**PASS** (attempt 4)

- Settings written: `usb-blacklist` = `{name:xiaomi poco x3 nfc}` (mirrored), `log-level` = `2`,
  `auto-start-on-usb` = `false`, `auto-start-on-wifi` = `false`.
- Radio state: n/a (USB round).
- Discard-rule check: attempts 1–3 discarded (wrong build / buffer roll, see Setup notes); attempt 4
  clean.
- Decisive log lines (all from **pid 4093**, the process that started at Direct Boot):

  ```
  23:04:09.721  UsbHostManager: Added device … mProductId=20193 …          (0x4EE1)
  23:04:10.119 W OPENHU  App.onCreate | App started in Direct Boot mode (locked). Settings access deferred.
  23:04:10.158 I OPENHU  UsbAttachedActivity.onCreate | USB Intent: … USB_DEVICE_ATTACHED …
  23:04:10.191 I OPENHU  UsbAttachedActivity.onCreate | UsbAttachedActivity: Ignored blacklisted USB device (VID: 0x18D1, PID: 0x4EE1)
  23:04:11.241 I OPENHU  UsbDiagnostics:  Xiaomi POCO X3 NFC (VID: 18D1 PID: 4EE1) [class 00/00/00, permission] rejected: blacklisted by the user | if0 06/01/01 …
  23:04:11.244 I OPENHU  UsbAttachedActivity.onCreate | UsbAttachedActivity: Ignored blacklisted USB device (VID: 0x18D1, PID: 0x4EE1)
  ```
- `FATAL EXCEPTION` / `am_crash` / `AndroidRuntime` for the package across the whole boot, in `-b
  crash` and `-b main`: **0**.
- `Switching USB device to accessory mode` / `connectAndSwitch` / `beginAutoConnect` naming the
  Poco: **absent**.
- Direct-Boot process pid: **4093**.

The pre-fix build crashed at `App.kt:46` two lines before the control line could print; on
`843b78d7` the control line prints and `UsbAttachedActivity.onCreate` runs to completion (it
`Ignored` the device and finished itself — no `force-crash`). Evidence:
`evidence/usb-device-diagnostics-round5/r1r2-directboot-pass-attempt4.log`.

## R2 — the unlock handover, and the point of the round

**PASS**

- Settings written: same as R1, plus the probe — `auto-start-wifi-ssid` set to `OHU-UNLOCK-PROBE`
  **in `settings.xml` only** after the mirroring launch, so the mirror still read empty at reboot.
- Pre-reboot `settings_device_protected.xml`: `<string name="auto-start-wifi-ssid"></string>`
  (`r2-mirror-pre-reboot.xml`).
- Post-unlock `settings_device_protected.xml`:
  `<string name="auto-start-wifi-ssid">OHU-UNLOCK-PROBE</string>` (`r2-mirror-post-unlock.xml`).
- Decisive line, **from pid 4093 — the same process R1 recorded**, no `am_proc_start` for the
  package between the Direct Boot start and this line:

  ```
  23:05:04.354 I OPENHU  1.onReceive | User unlocked: credential storage is available, applying settings
  ```
  (`1.onReceive` = the anonymous `userUnlockedReceiver` registered in `App.onCreate`'s locked
  branch.)
- Every `OPENHU` line from 23:04:10 to 23:05:04 carries `4093 4093`; no second package pid appears
  anywhere in the capture. pid 4093 outlived the unlock and received `ACTION_USER_UNLOCKED` on the
  runtime-registered receiver.

Both PASS conditions met: the receiver fired *and* the body ran — the mirror actually came to carry
the new value, it is not just the log line. The fix is finished; the `BootCompleteReceiver`
fallback the brief describes is not needed on this hardware.

## R3 — a locked boot still switches a phone into accessory mode

**PASS**

- Settings written: `usb-blacklist` = `{}` (cleared, mirror confirmed empty), `auto-start-on-usb` =
  `true` (mirrored), `log-level` = `2`.
- Discard-rule check: clean, one attempt.
- Decisive sequence, all from **pid 4085**:

  ```
  23:10:15.216 W OPENHU  App.onCreate | App started in Direct Boot mode (locked). Settings access deferred.
  23:10:15.313 W OPENHU  UsbAttachedActivity.onCreate | Could not start UI from USB auto-start: Unable to find explicit activity class {…/…main.MainActivity}; …
  23:10:15.314 I OPENHU  UsbAttachedActivity.onCreate | Switching USB device to accessory mode Xiaomi POCO X3 NFC (VID: 18D1 PID: 4EE1)
  23:10:15.343 I OPENHU  UsbAccessoryMode.switch | Success controlTransfer len: 2  acc_ver: 2
  23:10:15.345–15.353     UsbAccessoryMode.initStringControlTransfer | Success … "Android" / "Android Auto" / "2.0.1" / … / "HU-AAAAAA001"
  23:10:15.354 I OPENHU  UsbAccessoryMode.switch | Sending acc start
  23:10:15.861 I OPENHU  UsbAccessoryMode.connectAndSwitch | Result: true
  23:10:16.606       UsbHostManager: Added device … mProductId=11520 …           (0x2D00, accessory)
  23:10:16.643 W OPENHU  UsbAttachedActivity.onCreate | Usb in accessory mode, but the user has not unlocked yet and a session needs credential storage. Waiting for unlock.
  23:11:04.925 I OPENHU  1.onReceive | User unlocked: credential storage is available, applying settings
  ```
- The `2D00`-has-a-grant branch fired (`Waiting for unlock.`), as the brief predicted — report which:
  **`2D00`**, not `2D01`.
- `FATAL EXCEPTION` / `am_crash` for the package: **0**.
- `AapService` start before the unlock (`am_proc_start`, `ServiceRecord{…AapService}`,
  `onStartCommand`): **absent**.

The `directBootAware` accessory-mode switch runs to `Result: true` while locked, the phone
re-enumerates to `2D00`, and the session is correctly held for after the unlock. Evidence:
`evidence/usb-device-diagnostics-round5/r3-locked-accessory-switch-pass.log`.

## R4 — the unlocked path is unchanged

**PASS**

- Settings written: `usb-blacklist` = `{}`, `log-level` = `0` (VERBOSE, this run only),
  `auto-start-on-usb` = `false`.
- Log level honored: **3293 `D OPENHU` + 2736 `V OPENHU`** lines in the session capture, including
  `AppLog.d` output (`App.onCreate | native library dir …`, per-`.so` lines).
- Theme / night mode: `NightModeManager` starts on connect, `NightMode update: true` fires
  repeatedly. UI renders normally (projection activity, `HomeFragment`, GL surfaces) — not unstyled.
- Full USB projection session:
  ```
  23:15:21.089  UsbAccessoryMode.connectAndSwitch | Result: true
  23:15:21.237  UsbLauncherManager.checkAlreadyConnected | Found device already in accessory mode …
  23:15:21.257  UsbAttachedActivity.onCreate | Usb in accessory mode and has permission. Starting AapService.
  23:15:21.355  AapTransport.startHandshake$…_3_3_0_beta4_githubDebug | Start Aap transport handshake …
  23:15:21.955  AapSslContext.performHandshake | SSL handshake complete. Session id: 8X5BHtUfED1x…
  23:15:25.184  VideoDecoder.start | Configuring decoder: c2.qti.avc.decoder for 1920x1080 … optionalKeys=none
  23:15:30.263  VideoDecoder.logThroughput | Throughput over 5006ms: rendered=262 (52fps), fed=263, dropped=0, skipped=0, concealed=0, decodeLatency=18ms p95=22ms
  23:15:35.261  … rendered=241 (48fps), dropped=0
  23:15:40.267  … rendered=243 (48fps), dropped=0
  ```
  Sustained 47–52 fps, `dropped=0` every window; user confirmed AA projecting on the moto screen.
- **Foreground notification present** (`dumpsys notification`): `pkg=com.andrerinas.headunitrevived
  id=1`, `channel=headunit_service_v2` (`mName=Headunit Service`), `android.title=String (Open
  Headunit)`, action `"Exit"`, `isForeground=true foregroundId=1`. This is the end-to-end proof the
  channels-from-`getSystemService` change works.
- `FATAL EXCEPTION` / `am_crash`: **0**.

Evidence: `evidence/usb-device-diagnostics-round5/r4-unlocked-session-pass.log`.

## R5 — the attach path names the right reason

**PASS**

- Settings written: `usb-blacklist` = `{name:xiaomi poco x3 nfc}`, `log-level` = `2`.
- `AapService` running foreground (pid 16012), then the Poco was unplugged and replugged twice.
- Decisive line, both replugs:
  ```
  23:14:04.668 I OPENHU  UsbLauncherListener.onUsbAttach | Ignoring USB device attached in service (VID: 6353): rejected: blacklisted by the user
  23:14:06.037 I OPENHU  UsbLauncherListener.onUsbAttach | Ignoring USB device attached in service (VID: 6353): rejected: blacklisted by the user
  ```
- Round 4 got `accepted: ADB` on this exact line for the same device. It now prints
  `rejected: blacklisted by the user` — the same verdict the scan path and the list path already
  gave in the same second. FAIL condition ("still prints a descriptor verdict") not met.

Minor: the VID is printed in decimal (`6353`), not hex (`0x18D1`), unlike the sibling
`UsbAttachedActivity` line. Cosmetic. Evidence:
`evidence/usb-device-diagnostics-round5/r5-attach-path-verdict-pass.log`.

## R6 — the other two deferrals

Not run. Skipped by direction: the round was clean through R5 and there was no appetite for another
reboot plus a real reachable SSID and a BT-MAC in the auto-start list. The two lines it would check
(`WifiAutoStartReceiver: device is locked, deferring …` and `AutoStartReceiver: device is locked,
ignoring the Bluetooth event …`) remain hardware-unverified; they are simple `!isLocked` guards and
are covered by the fact that R1/R2/R3 showed the same-shaped guard working on the USB path.

## Restore

- `settings.xml` pushed back from `moto-settings-backup.xml` via `run-as cp` — **`diff` clean,
  byte-identical**. Mirror re-synced by one launch and matches `mirror-backup.xml` exactly
  (`usb-blacklist` = `{0a12:4007}`, all auto-start off, ssid empty).
- All `persist.log.tag*` and `log.tag*` properties set back to empty; spam rate confirmed restored
  (~12 400 lines / 5 s).
- USB default handlers `18D1:4EE1` + `18D1:2D00` (serial `4f4027e9` → headunitrevived) still
  present — the R7 grants the brief forbade clearing.
- Candidate `843b78d7` (`d9172c5a…`) left installed. App force-stopped. No background logcat
  readers.

## Answers to the three things the brief asked for specifically

1. **`App started in Direct Boot mode (locked)` per arm:** present on **R1, R2, R3**. R4 is the
   unlocked path (correctly no Direct Boot line). R5 has no reboot.
2. **pid across the unlock:** R1/R2 — single pid **4093**, alive from the Direct Boot
   `UsbAttachedActivity` start (23:04:10) through the `User unlocked` line (23:05:04) and beyond; no
   second package pid in the capture. R3 — single pid **4085**, same pattern. The two
   `settings_device_protected.xml` dumps are in
   `evidence/usb-device-diagnostics-round5/r2-mirror-pre-reboot.xml` (ssid empty) and
   `r2-mirror-post-unlock.xml` (ssid `OHU-UNLOCK-PROBE`).
3. **Which buffer the evidence came from, and did the buffer enlargement take:** `main` is 256 KiB
   and `persist.logd.size*` is refused on this user build, so no enlargement was possible. `-b main`
   **rolled and lost the whole window on attempt 3**. The working captures (attempt 4 onward) came
   from `-b all` after suppressing ROM spam with `persist.log.tag` properties (reverted afterward);
   the `OPENHU` lines are `main`-buffer, the `mProductId` / `UsbHostManager` lines are `system`, and
   crash-absence was cross-checked in `-b crash`. The next round should assume `-b main` older than
   ~10 s is unrecoverable on this host unless it sets the same tag filter before the reboot.

## Anything the brief did not ask about

- **`App.kt:46` line number in the pre-fix crash.** Round 4's brief and this one both quote the
  crash as `App.onCreate(App.kt:46)`. On the fixed tree, `App.kt:46` is the Conscrypt check and the
  `getComponent()` call is gone from `onCreate` entirely — so a crash frame pointing at
  `App.onCreate` + `getComponent` is a reliable one-line "this is a pre-fix build" tell, more
  reliable than the filter xml or the DEX string (both of which the pre-fix build also has).
- **First enumeration sees an empty bus.** On R1 attempt 4 and R5, the first `USB_DEVICE_ATTACHED`
  fired while `UsbManager.getDeviceList()` was still empty (`USB attach sees 0 USB device(s)` /
  `nothing is on the bus`), and `UsbAttachedActivity` blacklisted the device anyway from the
  intent's `EXTRA_DEVICE`. The second enumeration ~1 s later sees the full descriptor. Both paths
  reach the blacklist, so this is not a bug, but any logic that keys off the *list* rather than the
  *intent extra* on that first callback would see nothing.
- **The Poco always re-enumerates twice per plug** (`002/002` then `002/003`), on every attempt this
  round and in rounds 2–4. Each triggers its own `UsbAttachedActivity` / `UsbLauncherListener`
  callback, so every locked-boot log has the Direct Boot lines twice. Harmless here because the
  device is blacklisted or deferred; worth knowing if a future change makes the accessory switch
  non-idempotent.
