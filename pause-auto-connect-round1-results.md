# pause-auto-connect: round 1 results

**Candidate:** fix/pause-auto-connect-all-modes @ 1984b1ad4       **Baseline:** main @ dd454eed1
**APK md5:** c482906e095a181a01bff17f0040d719 (candidate). No baseline APK was built: every run in
this brief checks the candidate's own new log lines and behavior directly, none is an A/B diff
against a baseline build.
**Unit:** Stage A — D-POCO (POCO X3 NFC, Android 15) as USB host over OTG, wireless adb at
`192.168.1.4:5555`, Carlinkit-class AA dongle (idle `18d1:4ee1`, accessory `18d1:2d00`), paired
phone Motorola edge 30 neo (D-MOTO). Stage B — D-HU (UNISOC MT50, Android 14, rooted shell), peer
phone D-MOTO over Bluetooth.
**Date:** 2026-09-23

## Setup notes

- D-POCO's wireless adb address this round was `192.168.1.4:5555` (DHCP-assigned; differs from the
  `192.168.1.10` recorded in the usb-aoa-handoff round 2 memory). Put in Setup notes per the brief;
  confirmed reachable before every Stage A run.
- Fresh install on D-POCO hit `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (a differently-signed build was
  already on the device from earlier the same day); resolved with `adb uninstall` then a clean
  install. D-HU updated in place cleanly, keeping its existing settings.xml.
- **`auto-start-on-usb`'s device-protected-storage mirror does not need the brief's documented UI
  toggle to resync.** First reading after the `settings.xml` write was stale (`false`), as expected.
  Rather than toggling the setting in the UI, the app's own `App.kt:127` (`initUnlockedOnce()`)
  resyncs every auto-start mirror key from `settings.xml` on the process's next start — a plain
  force-stop/relaunch (which the round already does between steps) was enough, and the second
  reading came back `true`. No UI tap was needed or used for this.
- **D-POCO independently carries a leftover `wifi-connection-mode=3` (Native AA) from earlier,
  unrelated testing on this device.** This produced background wireless poke activity (HFP-AG pokes
  to bonded phones, WiFi Direct group churn) throughout Stage A that is unrelated to the USB path
  under test. Not part of this brief's settings list, left as observed rig state, and visible as
  noise in the raw captures. A future round on D-POCO may want to clear it first.
- **The standard USB accessory-mode (AOA) route on this rig is marginal**, matching the pre-existing
  #800 signature already in project memory: repeated cold-plug attempts fail at
  `javax.net.ssl.SSLException: Unable to parse TLS packet header` before ever reaching a working
  session. The libusb route (`use-libusb=true`) was used as a workaround to reliably reach a live
  session for R1's setup precondition and is not itself part of the branch under test.
- **Keeping the in-app USB device list open (the home screen's USB button) measurably improved
  connect reliability**, an observation made mid-round and not something the brief asked to test: R3
  cycle 2, run with that screen open, reached SSL handshake complete on 4 out of 4 accessory-mode
  switches; R3 cycles 1 and 3 and R1, run without it, needed anywhere from several to ~10+ retries
  over up to ~2 minutes before eventually succeeding. Not deterministic (cycle 3 still needed 7
  retries once), but a real shift in the odds. See "Anything the brief did not ask about" below.
- **First `auto-start-bt-macs` write for D-HU's Stage B setup used the wrong MAC** — D-POCO's
  (`DC:B7:2E:5E:4E:59`) instead of D-MOTO's (`A0:46:5A:97:E4:95`). Caught because the first R5
  attempt never printed `MATCH!` after enabling D-MOTO's Bluetooth; corrected before the run that is
  reported below. Not a code defect — self-inflicted setup error.
- **R1's first full capture (`r1_run1`) was voided**: the `UsbHostManager` logcat reader (started via
  the round's own `capture.sh`, nohup+disown, not an inline `&`) silently stalled — process stayed
  alive but stopped receiving lines — while `dumpsys usb`'s `host_manager.num_connects` kept climbing
  (18 → 36) from the dongle's own attach/detach cycling. Different failure mode than the brief's
  "replug too fast" warning; the held+replay portion of that capture is still solid evidence (quoted
  below) since it predates the stall, but the capture as a whole was not graded past that point per
  house rule 5 / the brief's own re-run instruction.
- **R4's precise before-SSL tap window could not be hit.** Three attempts — two scripted
  (`r4_race_tap.sh`: detect the accessory-mode line, then up to 20 rounds of uiautomator dump + tap,
  ~2.3s per round-trip over wireless adb) and one manual human attempt — all missed, because this
  dongle's connect time is highly variable (anywhere from under a second to ~2 minutes of retries)
  and all three attempts happened to land on the fast end. The brief's own fallback applies
  ("If the pill is not on screen when you tap, R4 is INCONCLUSIVE, not FAIL").
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `set_prefs_runas.sh`, `set_autostart_btmac.sh`,
  `install_and_launch.sh` (all pre-existing). New this round, left in
  `round-pause-auto-connect-r1/`: `capture.sh` (start/stop pair for the two Stage A logcat readers;
  the `round-usb-aoa-handoff-r2/capture.sh` the brief and memory point to no longer exists on disk)
  and `r4_race_tap.sh` (the R4 detect-and-tap loop).
- Settings were restored from backups on both devices and D-MOTO's Bluetooth was left enabled
  (its normal state) at the end of the round.

## R0 — Build gate

**PASS**

- SHA: `git rev-parse --short HEAD` → `1984b1ad4`, matches the brief.
- DEX: `AutoConnectHoldPolicy` present (5 hits via `strings` on the merged `classes*.dex`).
- JVM gate: 2330 tests, 0 failures (summed from `testGithubDebugUnitTest`'s per-class XML reports),
  matches the brief exactly.
- `adb install -r` succeeded on D-POCO (after removing the stale differently-signed build) and on
  D-HU (updated in place).

## R1 — A dongle event during settings is held and replayed on close (the point of the round)

**INCONCLUSIVE** (the hold-and-replay mechanism itself is proven correct; the bundled "then SSL
handshake complete" leg could not be confirmed from a single unbroken capture, blocked by the rig's
marginal standard-route USB link, not by the branch)

- Settings: `auto-start-on-usb=true`, `reopen-on-reconnection=true`, `auto-connect-last-session=true`,
  `kill-on-disconnect=false` (mirror confirmed synced before the run).
- From the clean portion of `r1_run1_ohu.txt` (before its `UsbHostManager` reader stalled):
  - `13:34:29.574` BACK pressed.
  - `13:34:31.505 AapService: the settings screen closed with a USB auto-connect held behind it,
    checking USB now.` — replay fired **1.93 s** after BACK (within the brief's 1.3–2.5 s window).
  - Held-line count during the settings-open window: **6**
    (`UsbLauncher: USB auto-connect held while the settings screen is open`).
  - Forbidden-line count during that same window (`Found device already in accessory mode`,
    `Switching USB device to accessory mode`, `Reopen on reconnection: launching MainActivity`): **0**.
  - Front activity 5 s after settings opened: `SettingsActivity`.
- The specific automatic USB check that fired at the replay found the bus empty
  (`UsbDiagnostics: service scan (force=true) sees 0 USB device(s)`), matching
  `13:34:39.791 UsbHostManager: Removed device at /dev/bus/usb/001/018: Pixel 4` in the usbhost log
  at essentially the same moment — the dongle's own self-revert cycle (it flaps roughly every
  400 ms once nothing has claimed its interface, which is exactly the state the hold logic is
  supposed to create) happened to be mid-detach at that instant. This is a rig/dongle-timing
  coincidence, not the hold-and-replay logic failing to fire — it fired exactly on schedule.
- That capture's `UsbHostManager` reader then stalled (see Setup notes), which voids grading the
  final SSL leg from this capture. Two further attempts to capture a single clean end-to-end run
  (`r1_run2`, `r1_run3`) repeatedly hit the marginal standard-route link
  (`javax.net.ssl.SSLException: Unable to parse TLS packet header`), the same #800-signature
  condition documented in project memory, independent of this branch.
- Corroborating evidence the underlying connect path is sound on this rig/dongle when not gated
  behind a settings-close replay: R3 below reached SSL handshake complete on all three of its
  physical replug cycles. That does not itself prove R1's specific replay-triggered check would
  always complete to SSL, only that nothing about this dongle/rig makes that leg structurally
  impossible.
- Full capture, including the stalled reader and both retry attempts, in the evidence zip.

## R2 — A settings visit with no USB event replays nothing

**PASS**

- 30 s idle-quiet check before opening settings: 0 `Added device` lines.
- Settings open 60 s: 0 `Added device`, 0 `checking USB now.`, 0 `SSL handshake complete`.
- BACK, watch 60 s: 0 `checking USB now.`, 0 `SSL handshake complete`.
- `dumpsys usb` confirmed the dongle still attached throughout (`host_connected=true`,
  `current_mode=dfp`) — "nothing connected" was not "nothing there".

## R3 — Positive control: with settings closed, nothing changed

**PASS** (3/3 cycles)

Home screen in front, no session, for all three cycles. Each cycle: unplug, wait 5 s (hand-operated),
plug back in.

- **Cycle 1**: several accessory-mode switches and repeated handshake failures over ~2 minutes
  (`javax.net.ssl.SSLException: Unable to parse TLS packet header`, matching R1's rig-link finding),
  then `13:48:35.717 AapSslContext.performHandshake | SSL handshake complete.` Zero
  `held while the settings screen is open` lines throughout. Session then ran clean and stable for
  2+ minutes with 0 dropped/skipped/concealed frames at ~29 fps.
- **Cycle 2** (run with the in-app USB device list screen open, see Setup notes): 4 accessory-mode
  switches, all 4 reached `SSL handshake complete`. Zero held-lines.
- **Cycle 3**: 7 accessory-mode switches, 8 handshake failures, 1 eventual `SSL handshake complete`.
  Zero held-lines.
- All three cycles: `Switching USB device to accessory mode` / `Found device already in accessory
  mode` observed, then `SSL handshake complete` reached, zero `held while the settings screen is
  open` in every cycle — confirms the hold is not a blanket block outside settings.

## R4 — The pill's X on a USB attempt latches USB off

**INCONCLUSIVE** — the pill was never on screen at tap time in any of three attempts, which the
brief names explicitly as INCONCLUSIVE rather than FAIL.

- Attempt 1: manual single dump+tap, missed (SSL had already completed).
- Attempt 2: scripted, up to 20 dump+tap retries (~2.3 s per round-trip) after detecting the
  accessory-mode line; SSL completed on the very first check (near-instant connect), before any tap
  landed.
- Attempt 3: same script, 25 max retries, 40 s detect timeout; a manual human tap was also tried in
  parallel. Both missed for the same reason — SSL completed before either tap could land.
- Because step 1 (tap the X before SSL) never landed, step 2 (USB button lifting the stop) was not
  reached in any attempt.
- Not reported: refused-count after a replug, or whether a tap timed at-or-after SSL takes the USB
  branch — neither condition was exercised.

## R5 — A Bluetooth arrival during settings, Native mode (Stage B)

**PASS**

- Settings: `wifi-connection-mode=3` (already set on D-HU), `auto-start-bt-macs` holding D-MOTO's MAC
  (`A0:46:5A:97:E4:95`, corrected after the mis-set first attempt — see Setup notes).
- `14:07:46.501 AutoStartReceiver.onReceive | MATCH! Starting AapService via Bluetooth Auto-start...`
- `14:07:46.539 AapService: Bluetooth auto-start while the settings screen is on show; raising the
  home screen when it closes.`
- Front activity ~15 s after `MATCH!`: still `SettingsActivity` (on 3.4.0 this is `MainActivity` —
  the regression this round fixes).
- BACK sent `14:08:05.353`. `14:08:07.755 AapService: the settings screen closed with a Bluetooth
  arrival held behind it, raising the home screen now.` — gap **2.4 s** (the brief describes "about
  1.5 s" as an approximate figure, not a numbered pass/fail gate for this run, unlike R1's strict
  window).
- `14:07:46.540 AapService: Bluetooth auto-start while the settings screen is open; re-arming when it
  closes.` printed before BACK, and `14:08:05.273 AapService: the settings screen closed with a
  wireless request held behind it, re-arming wireless mode NATIVE` followed the close, as required.
- `14:08:17.426 AapSslContext.performHandshake | SSL handshake complete.` — session formed ~12 s
  after BACK, well inside the 90 s window.

## R6 — Regression: the wireless X is unchanged (Stage B)

**PASS**

- `AapService: the status pill's X stopped the wireless bring-up.` — 1 occurrence.
- `stopped the USB attempt` — 0 occurrences.
- `WifiLauncherManager.liftUserCancel | WifiLauncher: the user asked for a wireless connection, so
  the stop from the status pill is lifted.` — printed after `ACTION_START_WIRELESS_SCAN`.
- Confirms the service's own USB detection did not misfire on a wireless attempt.

## Report back (brief §8)

1. **R1**: held line count **6**; front activity during the window **SettingsActivity**; BACK→replay
   gap **1.93 s**; replay→SSL gap not cleanly measured (blocked by the rig's marginal standard-route
   link across all attempts at a single unbroken capture — see R1 above).
2. **R4**: refused-count after the replug — not applicable, the X was never pressed in any attempt;
   whether a tap at SSL-or-later takes the USB branch — not reached.
3. **R5**: front activity 5 s after `MATCH!` — `SettingsActivity`; a session formed after BACK — yes,
   `SSL handshake complete` at `14:08:17.426`.

## Anything the brief did not ask about

- The device-protected `auto-start-on-usb` mirror resyncs on the app's own next process start
  (`App.kt`'s `initUnlockedOnce()`), so the brief's documented UI-toggle fallback is rarely needed in
  practice — a plain force-stop/relaunch after the `settings.xml` write is enough. Worth noting in
  the brief itself for a future round.
- Keeping the in-app USB device list screen open measurably improved standard-route connect
  reliability against this rig's marginal link (4/4 in R3 cycle 2 vs. many-retries-before-success
  without it). Not understood mechanically this round — plausibly changes UI polling/refresh timing
  around the accessory-mode transition — and worth a dedicated look if the #800-class marginal-link
  issue is ever investigated directly.
- D-POCO independently carries a leftover `wifi-connection-mode=3` from earlier, unrelated testing,
  which produced background wireless poke noise throughout Stage A. Left as observed rig state per
  Setup notes; a future round on this device may want to clear it first for a quieter capture.

Captures: `rig-evidence-pause-auto-connect` release, asset `pause-auto-connect-round1-captures.zip`,
sha256 `261fcd5ed5d3f29fb17121eeb07d2aff7a7c25994c58c9a3aff5f40e5e47b67a`.
