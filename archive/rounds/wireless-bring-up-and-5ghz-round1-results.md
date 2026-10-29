# wireless-bring-up-and-5ghz — round 1 results

**Candidate:** `fix/wireless-bring-up-and-5ghz-channel` @ `65014ed8` (`65014ed8bf265ec53226e3767bdf7618e8df29b5`)
**Baseline:** none built (no A/B; the new code does not exist on `main`)
**APK md5:** `f44bc175255c6983c2ee69246da59c0b` (candidate, both devices)
**Unit gate:** 1351 / 0 (`testGithubDebugUnitTest`)
**Units:**
- R1-R4: MT50 (`MT50_YT610E4GFPSL_U`, UNISOC T610, Android 14, 1440x720), permanently joined to `Pegue Cdesta` at 5500 MHz (channel 100). Phone = POCO X3 NFC (`DC:B7:2E:5E:4E:59`), Gearhead not version-checked this round.
- R5: POCO X3 NFC (`M2007J20CG`, Android 13) as head unit over wireless adb (`192.168.1.10:5555`), USB port free, a Google Pixel 4 plugged into it through a Carlinkit dongle.

**Date:** 2026-09-05

## Verdicts

| Run | Verdict | One line |
|---|---|---|
| R1 ordinary Native AA session still forms | **PASS** | session forms; `a USB projection attempt is in flight` absent with no USB |
| R2 stand-down runs with no setting, gives the network back | **PASS** | unconditional stand-down fires, session forms, MT50 rejoins `Pegue Cdesta` |
| R3 a pinned channel is walked | **PASS** (API-34 form) | `wifi-5ghz-channel=36` -> group formed on exactly 5180 MHz |
| R4 refusal banner | **PASS** (seeded form) | banner renders with the right text; tap opens the WiFi Direct band setting |
| R5 USB deferral holds the bring-up | **PASS** (backstop path) | `holding the wireless bring-up` appears before any createGroup; released after the 8 s budget with wireless armed |

---

## Setup notes

### Deviations from the brief

1. **`native-driver-selection-mode` set to `0` (DISABLED) for R1-R4.** The rig's `settings.xml` baseline carries `native-driver-selection-mode=2` (ALWAYS) from the driver-selection thread. With the clean-run protocol's radios-off-at-launch, no phone is BT-connected when the app starts, so the selector is shown and (after `native-driver-selection-timeout=10`) auto-selects the last-connected device, which is the **motorola edge 30 neo** (`last-connected-native-mac=A0:46:5A:97:E4:95`). The Moto was not present, so its pokes all failed (`read failed, socket might closed or timeout`) and no session formed. First R1 attempt (`hu_r1.txt`, `r1_console.txt`) is that non-session run, kept for the record: 2 `createGroup SUCCESS` (60 s apart, the native-recover cadence), 0 `SSL handshake`, 0 `Incoming connection detected`. Setting the mode to `0` gets an ordinary single-phone connect, which is what the brief asks for. Nothing in this branch touches the selector. Restored to `2` after the round; MT50 `settings.xml` is byte-identical to `settings-backup.xml` (md5 `3edb48c16a14addaaedb4ca0b31ecf28`).

2. **R3 and R4 live forms as written cannot run on the MT50 (API 34).** The whole 5 GHz channel **walk** ladder, and the regulatory-domain dump, live in the sub-Android-10 branch of `WifiDirectManager.createQuietGroup()` only. `logWifiCountrySourceDump()` has a single call site (line 1723), reached only after the `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { ... return }` block returns, so on API 34 it never runs. Consequently these brief lines **never appear on this rig**:
   - `WifiDirectManager: == WiFi regulatory domain source dump ==`
   - `WifiDirectManager: this unit refused a group owner on every 5 GHz channel it was offered`
   - `WifiDirectManager: operating channel <n> (<freq> MHz) ...`

   On API 34 a pinned channel goes through `WifiP2pConfig.setGroupOperatingFrequency()`, and the decisive line is `WifiDirectManager: 5 GHz channel is channel <n> (<freq> MHz), asked for as a fixed <freq> MHz.` (predates this branch) plus the actual group frequency read back from `dumpsys wifip2p`. R3 was judged on those. The channel-walk logic itself is exercised only by the JVM tests (`WifiP2pOperatingChannelPolicyTest`, `WifiCountryPolicyTest`).

3. **R4 ran in the seeded form.** The MT50 honours pinned 5 GHz channels (R3: ch 36 -> 5180 MHz; R4 live probe: ch 149 -> 5745 MHz), so the "every rung refused" path is unreachable live, exactly as the brief anticipated.

4. **The stand-down restore log string in the brief does not occur on this rig.** `StationStandDown.restore()` calls `wm.enableNetwork(networkId, false)`, which returns **false** on the MT50 (the same as `disableNetwork()` returning false, already noted for this rig). So `restore()` always logs the WARN branch:
   `StationStandDown: the platform refused to re-enable this unit's WiFi network. It may have to be reconnected by hand.`
   and never the brief's PASS string `StationStandDown: this unit's WiFi network is enabled again and should rejoin`. `wm.reconnect()` still runs and autojoin re-associates within ~5 s every time. R2 judged on the actual rejoin (`dumpsys wifi`), not the log string.

5. **`MATCH! Starting AapService` appears once in every R1-R4 session run.** It is the poke's own `socket.connect()` raising an OS `ACL_CONNECTED` that `AutoStartReceiver` answers (`GH.WifiBluetoothRcvr: Connection action: ... ACL_CONNECTED`), the documented self-wake. In every run it carried **zero group churn**: 1 `AapService creating`, 1 `createGroup SUCCESS`, 1 SSL session, 1 `p2p-wlan0-N`. Benign per the standing template.

6. **`stand-down-station-for-wifi-direct` was not in `settings.xml`** at the start of the round (removed by an earlier thread or never set). Nothing to clear.

7. **`WifiScanner` on the MT50 is broken** (known); group channels were read from `dumpsys wifip2p` (`frequency:` line under `mWifiP2pInfo`), not from a scan.

### R5 setup

- The POCO's installed HUR was a **release-signed, non-debuggable** 3.3.1 (versionCode 105, installed 2026-09-04). The debug candidate would not install over it (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) and `run-as` was refused. Per the user's instruction the release build was **uninstalled** and the debug candidate installed fresh; onboarding re-ran. Settings then written by `set_prefs_runas.sh`: `wifi-connection-mode=3`, `native-ap-transport=0`, `wifi-direct-band=1`, `wifi-5ghz-channel=0`, `native-driver-selection-mode=0`, `log-level=0`. **The POCO is left carrying the debug candidate; its previous release build was not restored (not available locally).**
- Wireless adb: `adb tcpip 5555` while on USB, POCO joined `Pegue Cdesta` (`192.168.1.10`), `adb connect 192.168.1.10:5555`, USB cable to PC removed.
- **The Carlinkit dongle only enumerates on the POCO once a phone is on its Bluetooth.** Before that: `UsbDiagnostics: nothing is on the bus ... a wireless adapter is waiting for its phone before it presents itself`.
- **The USB link on this rig is unstable.** Once enumerated, the Pixel 4 flapped on and off the POCO's bus every 5 to 15 s (`UsbHostManager: Removed device ... Pixel 4` / `new high-speed USB device number N`), the AOA switch could not complete, and the app's own permission dialog kept being dismissed by the device vanishing. This matches the marginal-USB2.0-hardware note from the usb-aoa thread. Runs `poco_r5b` and `poco_r5c` launched the app while no device happened to be on the bus, so the deferral had nothing to act on and wireless armed straight away (correct, but not the run). `poco_r5d` and `poco_r5e` polled `/dev/bus/usb/001/` and launched the instant a device appeared; both then exercised the deferral cleanly.

### Scripts

- New: `hur-wifi-test-scripts/wireless_bringup_5ghz_run.sh` (one clean Native AA bring-up on the MT50 with D-POCO as phone; caller writes prefs first with `set_hu_prefs.sh`; polls the station rejoin for up to 60 s after teardown).
- Used: `build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh` (MT50), `set_prefs_runas.sh` (POCO).

---

## R1 — an ordinary Native AA session still forms

**PASS**

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=0`, `wifi-direct-band=1`, `wifi-5ghz-channel=0`, `native-driver-selection-mode=0`, `log-level=0`
- Radio state: MT50 on `Pegue Cdesta` 5500 MHz throughout (`RSSI -23`, verified before launch). POCO radios off at launch, on at settle+.
- Discard-rule check: clean. 1 `createGroup SUCCESS`, 1 `AapService creating`, 1 SSL session (`Session id: JeT6CYDU...`), 1 `Incoming connection detected`, `p2p-wlan0-2` only. `MATCH! Starting AapService` = 1 (benign poke self-wake, no churn).
- Decisive log lines (`hu_r1b.txt`):
  - `19:01:02.281  AapService.onCreate | AapService creating...`
  - `19:01:04.606  WifiDirectManager: 5GHz createGroup SUCCESS!`  (single)
  - `19:01:21.109  NativeAA: Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms...`
  - `19:01:21.280  NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59)`
  - `19:01:23.235  WirelessServer: Incoming connection detected from /192.168.49.50`
  - `19:01:23.484  SSL handshake complete. Session id: JeT6CYDU...`
- **Measurement — `AapService creating` to `SSL handshake complete`: 21.2 s** (19:01:02.281 -> 19:01:23.484). This is a cold, poke-driven connect: the clean-run protocol forces the phone's radios off, so the phone is always woken by the HFP poke (`Successfully poked` at launch+18 s) rather than reconnecting on its own. `NativeDriverSelectionPolicy` itself cites "a cold single-phone connect measured about 26 s on the rig"; 21.2 s is inside that. The driver-selection round 6 results carry no comparable plain-connect figure (every run there was a selector / switch-wake / pill measurement); its switch-wake `socket.connect() -> second SSL` figure was 9.08 s, which is a different path.
- `AapService: a USB projection attempt is in flight` — **absent** (0 occurrences). No USB attached. PASS on the brief's standalone check.

## R2 — the stand-down runs with no setting, and gives the network back

**PASS** (on substance; see Setup note 4 for the log-string mismatch)

- Settings written: same as R1 (`stand-down-station-for-wifi-direct` **not present**).
- Radio state: MT50 on `Pegue Cdesta` 5500 MHz before launch (`Supplicant state: COMPLETED`, verified). POCO radios off -> on.
- Discard-rule check: clean. 1 `createGroup SUCCESS`, 1 SSL session (`RpavmeO8...`), 1 `AapService creating`, `p2p-wlan0-3`. `MATCH!` = 1 (benign).
- Decisive log lines (`hu_r2.txt`):
  - `19:03:40.149  StationStandDown: asked this unit to leave its WiFi network so the group can have the radio to itself (disableNetwork returned false). It is rejoined when the session ends.`
  - `19:03:41.654  StationStandDown: this unit has left its WiFi network.`  <- stand-down fired with no setting written
  - `19:03:42.206  WifiDirectManager: 5GHz createGroup SUCCESS!`
  - `19:04:00.833  WirelessServer: Incoming connection detected from /192.168.49.50`
  - `19:04:01.065  SSL handshake complete. Session id: RpavmeO8...`  <- session forms with the stand-down active
  - `19:04:17.316  StationStandDown: the platform refused to re-enable this unit's WiFi network. It may have to be reconnected by hand.`  (the brief's `enabled again and should rejoin` string is unreachable here, Setup note 4)
- **Rejoin: `Pegue Cdesta` COMPLETED at teardown + 5 s** (poll, `hu_station_teardown_r2.txt` = "rejoined: yes at +5s"). Also confirmed rejoined at teardown+5 s in R1b, R3, R4-live.
- The FAIL fallback `this unit is not joined to another WiFi network, so` did **not** appear (the rig was associated at launch, verified).

## R3 — a pinned channel is actually walked

**PASS** (API-34 form; the sub-Q "walk" is JVM-only on this rig, Setup note 2)

- Settings written: R1 set plus `wifi-5ghz-channel=36`.
- Radio state: MT50 on `Pegue Cdesta` 5500 MHz; POCO off -> on.
- Discard-rule check: clean. 1 `createGroup SUCCESS`, 1 SSL session (`/vzxR7ln...`), `p2p-wlan0-4`.
- Decisive log lines (`hu_r3.txt`):
  - `19:05:11.779  WifiDirectManager: 5 GHz channel is channel 36 (5180 MHz), asked for as a fixed 5180 MHz.`
  - `19:05:11.809  WifiDirectManager: 5GHz createGroup SUCCESS!`
  - `19:05:31.984  WirelessServer: Incoming connection detected from /192.168.49.50`
  - `19:05:32.270  SSL handshake complete. Session id: /vzxR7ln...`
- **Group frequency (`dumpsys wifip2p`): `frequency: 5180`** — the pinned channel 36 reached the radio exactly. Session formed. `== WiFi regulatory domain source dump ==` and any `operating channel` line: absent (expected on API 34).

## R4 — positive control: the refusal banner

**PASS** (seeded form)

- **Live probe (`wifi-5ghz-channel=149`, `hu_r4_live_ch149.txt`):** `WifiDirectManager: 5 GHz channel is channel 149 (5745 MHz), asked for as a fixed 5745 MHz.` -> `5GHz createGroup SUCCESS!` -> group on `frequency: 5745` -> full session (`SSL handshake complete` `V8SaQK04...`). The MT50 honours the pin, so the refusal path is unreachable live. Seeded form used.
- **Seeded form:** app force-stopped, wrote `<long name="connection-issue-5ghz-channel-refused" value="1755800000000" />`, deleted `connection-issue-dismissed-at` (was absent), relaunched with POCO radios off (so no session; `bannerFor()` returns null while `sessionConnected`).
  - `hu_r4_seeded2.txt`: `19:11:03.987  MainActivity.updateConnectionIssueBanner | MainActivity: showing the connection issue banner for FIVE_GHZ_CHANNEL_REFUSED`
  - `r4_seeded2.png`: banner on the main screen, text = the full `connection_issue_banner_five_ghz_channel_refused` string ("This unit refused to put its WiFi Direct network on any 5 GHz channel you chose ... Tap to set the WiFi Direct band to 2.4 GHz ..."), with a dismiss (x).
  - UI dump confirms the exact string in `resource-id .../connection_issue_banner`.
  - **Tap target:** `input tap` on the banner -> `SettingsActivity`, Advanced tab, search field pre-filled `WiFi Direct band`, list filtered to the `WiFi Direct band` control (Auto / 5 GHz only / 2.4 GHz only). `r4_tap.png`.
- Note in passing: the live probe at ch 149 (`requestedFrequency = 5745 > 0`) followed by `createGroup SUCCESS` **cleared** a previously-seeded stamp on the first seeded attempt (`hu_r4_seeded.txt` shows `<long ... value="0" />` after the run) — the `ConnectionIssues.clear(FIVE_GHZ_CHANNEL_REFUSED)` on the `requestedFrequency > 0` branch, working as designed ("a group formed on the channel that was asked for disproves the record"). The seeded stamp had to be re-written after the probe.

## R5 — the USB deferral holds the bring-up, on the POCO as head unit

**PASS** (via the 8 s backstop; the two named sub-outcomes did not occur, see below)

- Settings on POCO: `wifi-connection-mode=3`, `native-ap-transport=0`, `wifi-direct-band=1`, `wifi-5ghz-channel=0`, `native-driver-selection-mode=0`, `log-level=0`.
- USB state: Pixel 4 behind a Carlinkit dongle, phone on the dongle's Bluetooth, flapping on/off the POCO's bus every 5-15 s (Setup note "R5 setup").
- Two launches with no device on the bus at `onCreate` (`poco_r5b`, `poco_r5c`): deferral did not engage, `startNativeAaQuietHost()` ran within ~1 s. Correct (nothing to defer for), not the run.
  - `poco_r5b` did later reach a USB AAP session: `19:30:09 UsbAttachedActivity: Usb in accessory mode and has permission. Starting AapService.` -> `19:30:10 SSL handshake complete` — but ~50 s after wireless had already armed with no hold.
- **Two launches timed to a device-present window (`poco_r5d`, `poco_r5e`): deferral engaged both times, identically.**
  - `poco_r5d`, launch 19:32:32.5:
    - `19:32:33.462  AapService.deferWirelessForUsbHandoff | AapService: a USB projection attempt is in flight — holding the wireless bring-up for up to 8000ms`
    - repeated at 19:32:34.889, :35.897, :36.904, :37.913, :38.923, :39.933, :40.943 (**8 in flight lines, ~1 s apart, ~7.5 s of holding**)
    - `19:32:42.043  WifiDirectManager: startNativeAaQuietHost() requested`  <- wireless armed
    - `19:32:42.565  Attempting createGroup for Native AA (Attempt 0)` -> `19:32:42.591  5GHz createGroup SUCCESS!`
    - **the hold is entirely before the first `createGroup`** (createGroup count during the hold: 0).
  - `poco_r5e`, launch 19:34:15: same shape, 8 in flight lines 19:34:16.590 -> 19:34:24.070, then `startNativeAaQuietHost()` 19:34:25.148, `createGroup` 19:34:25.676, `createGroup SUCCESS` 19:34:25.702.
- **`AapService: USB handoff settled after <N>ms — arming wireless now` did NOT appear** (0 occurrences, both runs). See "Anything the brief did not ask about" — it looks unreachable on the timeout path. The release is only visible as the in flight lines stopping and `startNativeAaQuietHost()` following.
- **The USB session did not take over** in the d/e windows — the flaky link dropped the Pixel before an AAP session could complete, so the deferral ran out its full ~8 s `DEFER_BUDGET_MS` and armed wireless via the backstop ("The backstop is a dongle that reaches accessory mode and then never negotiates ... without a bound, that unit would never get its wireless stack").
- Against the brief's conditions: `holding the wireless bring-up` before any `createGroup` — met. Wireless ends up armed — met. The hold did not "outlast 8 s without either outcome": it lasted the budget (~8 s) and then armed, which is the designed backstop outcome. Treated as **PASS**; the deferral demonstrably engaged, bounded itself, and released with wireless up.

---

## Anything the brief did not ask about

1. **`AapService: USB handoff settled after <N>ms — arming wireless now` appears unreachable on the normal timeout path.** In `deferWirelessForUsbHandoff()` the log is guarded by `if (waitedMs > 0 && usbDeferralJob != null)`. The recheck coroutine does `usbDeferralJob = null` **before** calling `initWifiModeWithOptionalWait()` again, so by the time `shouldDefer` returns false on a later pass, `usbDeferralJob` is already null and the guard fails. It could only fire if some *other* caller re-entered `initWifiModeWithOptionalWait()` while a deferral job was pending and `shouldDefer` had just gone false. Observed: 0 occurrences across `poco_r5d` and `poco_r5e`, both of which held the full budget and then armed wireless. The behaviour is right; the confirmation line is missing, and the brief's R5 PASS wording ("either `USB handoff settled after <N>ms` with N under 8000, or the USB session takes over") can be satisfied by neither on a rig where the USB link is too weak to complete a session.

2. **The deferral only protects a bring-up if a USB device is enumerated at the instant `onCreate` runs.** `checkAlreadyConnected()` is called synchronously just before `initWifiModeWithOptionalWait()`, and on a cold normal-mode phone it kicks off an async permission request rather than synchronously setting `isSwitchingToProjection()`. In `poco_r5b`/`poco_r5c` the first `Requesting USB permission` / `Switching USB device to accessory mode` landed ~10 s after `createGroup` had already run. So a phone that is present but slow to be recognised (or, here, absent for that millisecond because the link is flapping) gets no hold. On this rig that is a hardware-stability problem; on a stable rig it is worth checking whether a cold first-plug that needs an AOA switch is actually covered, or only the warm case where the device is already in accessory mode.

3. **`Rx Link speed` on the MT50 station collapses to 6 Mbit/s whenever a P2P group is up** (seen in every R1-R4 run's station read, recovers to 433 after teardown). This is the multi-channel-concurrency cost already documented for this rig, not a regression in this branch.

4. **The native-recover cadence is ~60 s.** The failed first R1 attempt shows `Requesting Native AA P2P group` / `createGroup SUCCESS` at 18:56:44 and again at 18:57:44 when no session formed. Relevant only to reading "2 createGroup SUCCESS" in a no-session capture: it is the recover timer, not churn.

## Report-back answers

- **R1 service-start to `SSL handshake complete`: 21.2 s** (cold, poke-driven). No comparable plain-connect figure exists in the driver-selection round 6 results to divide by; 21.2 s is under the 26 s cold-connect figure `NativeDriverSelectionPolicy` documents for this rig. Not a FAIL.
- **The MT50 rejoined `Pegue Cdesta` after every run that stood it down** (R1b, R2, R3, R4-live), each time within 5 s of teardown, via `wm.reconnect()` + autojoin (the explicit `enableNetwork` path returns false on this rig).
- **R4 ran in the seeded form.** Live refusal is unreachable: the MT50 honours pinned channels (36 -> 5180, 149 -> 5745).
- **One run failed to form a session:** the first R1 attempt, with `native-driver-selection-mode=2` (rig baseline) auto-selecting the absent Moto. Capture `hu_r1.txt` / `r1_console.txt`. Re-run as R1b with `native-driver-selection-mode=0` formed a clean session. Not a candidate defect.

## Rig state left behind

- MT50: `settings.xml` restored byte-identical to `settings-backup.xml` (md5 `3edb48c16a14addaaedb4ca0b31ecf28`), app force-stopped, on `Pegue Cdesta`.
- POCO: **now carries the debug candidate `65014ed8`** (its prior release-signed 3.3.1 vc105 was uninstalled per instruction and not restored). Settings as listed in R5. Wireless adb (`192.168.1.10:5555`) and `tcpip 5555` still active.
