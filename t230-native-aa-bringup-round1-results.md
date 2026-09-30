# t230-native-aa-bringup — round 1 results

**Candidate:** installed APK v.3.4.0-beta3 @ 8418ed025 (predates `61143283f` "two group bring-ups at
once removed the group under the phone" by 5 commits on `fix/audio-sink-and-wireless-bring-up`)
**Baseline:** none (first bring-up round on new hardware, no fix under test)
**APK md5:** not captured (pre-installed, not built this round)
**Unit:** Samsung SM-T230 (Galaxy Tab 4 7.0, "degaswifi"), Android 4.4.2 (API 19),
`samsung/degaswifixx/degaswifi:4.4.2/KOT49H/T230XXU0ANJ4:user/release-keys`, 800x1280, no root,
replacing the HP tablet from `hp-slate-bringup`
**Date:** 2026-09-15

## Setup notes

- Ran directly (device is adb-attached to the same machine as the coding session this round, not
  the separate physical rig; same situation `hp-slate-bringup` was in). Not a scripted round against
  a brief — the operator plugged in a new tablet and asked for the failing Native AA attempt to be
  logged and characterized. No pre-existing brief for this thread.
- **`test_native_aa.sh` / `set_hu_pref.sh` don't fit this device**: both assume a rooted shell
  (`HU` defaults to the MT50 and edits `/data/data/$PKG/...` directly as root). This tablet has no
  `su`. Used `set_pref.sh` (the `run-as`-based script) instead, which is built for exactly this case.
- **This device's toolbox has no `sed`, no `awk`, no `busybox`/`toybox`** (`cp`, `cat`, `grep`, `mv`
  exist). `set_pref.sh`'s pushed on-device script failed (`sed: not found`). Worked around by editing
  a local copy of `shared_prefs/settings.xml` with `python3` on the host and pushing the whole file
  back, then `run-as $PKG cp <pushed file> shared_prefs/settings.xml`. Worth adding a `sed`-free
  variant of `set_pref.sh` if more Android-4.x devices join the rig.
- **The app's launcher component uses the `applicationId`, not the Kotlin namespace**: `am start -n
  com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity` (not
  `com.andrerinas.openheadunit/...main.MainActivity`, which 404s with "Activity class ... does not
  exist").
- Backed up `shared_prefs/settings.xml` before any change and restored it (same push+`cp` pattern)
  at the end of the round; log-level set back to unset (was default INFO before this round).
- Settings found already in place from the operator's own manual attempt before this round started:
  `wifi-connection-mode=3` (Native AA), `connection-modes={wifi}`, `native-poke-bt-macs={DC:B7:2E:
  5E:4E:59}`, `last-connected-native-mac=DC:B7:2E:5E:4E:59` — all correctly pointed at D-POCO (POCO
  X3 NFC), confirmed by reading D-POCO's own `settings get secure bluetooth_address`. No stale
  poke-target quirk here.
- **Battery was at 9% for the entire round.** Not corrected before capturing (operator wasn't asked
  in time) — flagged here per §7a's own caution about drawing conclusions from a run with an
  uncontrolled variable. A low-battery power-save mode restricting WiFi/P2P scan aggressiveness on
  this device cannot be ruled out as a contributing factor to what's reported below. Re-run this
  round on a charged unit before trusting the failure signature as device-representative.
- Log level set to Verbose (`log-level=0`) before capturing — default INFO omits the poke and
  group-info lines that carry the signal here, per this repo's own `.claude/CLAUDE.md`.
- One clean cycle captured: `force-stop` → `logcat -c` → streamed capture → explicit `am start`
  (AapService was already running from the operator's own earlier attempt; this round forced a
  fresh launch rather than trusting that state). Capture ran ~11:33:55-11:36:25 (150s), stopped by
  hand once the failure signature had repeated with a clear cause.
- Full capture: `evidence/t230-native-aa-bringup-round1/hu_native_aa_attempt1_logcat.txt` (5793
  lines, unfiltered `logcat -v time`).

## R1 — Native AA bring-up, D-POCO already bonded

**FAIL**

- Settings written: `log-level=0` (int) only; connection settings were already correct (see above).
- Radio state: D-POCO's Bluetooth on, already bonded to this tablet from a prior session; screen on
  throughout.
- Discard-rule check: not re-run — one clean cycle was enough to show the failure signature twice
  independently (see below); a repeat is recommended once the battery caveat is controlled for.
- Decisive log lines, quoted with timestamps:

  Group 1 (short-lived, ~12.1s):
  ```
  11:33:59.069 WifiDirectManager: Standard createGroup SUCCESS!
  11:34:08.638 WifiDirectManager: SUCCESS - Providing credentials to listener. SSID=DIRECT-mO-Navegadortz3, IP=192.168.49.1
  11:34:11.200 wpa_supplicant: P2P_GROUP_REMOVE p2p-wlan0-0
  11:34:11.701 NativeAA: [RX] WifiConnectStatus status=SUCCESS(0) (SUCCESS = the phone got onto our network)
  ```
  The phone's own success report for group 1 lands **half a second after this unit already tore that
  group down** — the same class of bug `61143283f` (not in this build) targets: a group removed out
  from under a phone that had just associated to it. `refreshNativeCredentials` then found "no group
  is up" and rebuilt one:
  ```
  11:34:12.822 WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any...
  11:34:13.823 WifiDirectManager: Standard createGroup SUCCESS!
  ```

  Group 2 (lived exactly 60.1s, no app-side "removing group" log line near its end — looks like an
  OS/supplicant-side idle-group timeout, not an app-triggered teardown):
  ```
  11:35:05.453 NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) ...
  11:35:06.885 NativeAA: [TX] Wrote TYPE 3 ... Handshake completed successfully on Bluetooth side.
  11:35:06.905 NativeAA: [RX] WifiStartResponse ip= status=SUCCESS(0)
  11:35:43.320 NativeAA: [RX] WifiConnectStatus status=WIFI_NETWORK_UNAVAILABLE(-11)
  11:35:43.320 NativeAA: Handshake failed — phone reported join failure (type 6, status=WIFI_NETWORK_UNAVAILABLE(-11))
  ```
  A second BT handshake against the same still-live group repeated the same protocol success up to
  `WifiStartResponse SUCCESS` at `11:35:49.496`, then the capture was stopped before its outcome
  landed. Group 2 was removed at `11:35:13.962` — wait, that's *before* both of these retries, i.e.
  the group was gone at the WiFi-Direct layer while the app kept running BT handshakes against
  credentials for a group that no longer existed. (Times are correct as logged; see full capture —
  this gap between the WiFi-Direct-layer teardown and the app still transacting BT handshakes is
  itself worth a second look.)
- Landmarks never seen anywhere in the 150s capture: `Incoming connection detected` (0), `SSL
  handshake complete` (0), `OMXClient` (0). The phone never reaches the TCP handoff on this device.
- Counts: `createGroup SUCCESS` x2, `P2P_GROUP_REMOVE` x2, `Providing credentials` x8 (across the 2
  SSIDs, 3-4 deliveries per group as documented), `Successfully poked` x3, `WifiConnectStatus
  status=SUCCESS` x1, `WIFI_NETWORK_UNAVAILABLE` x1 explicit (the second handshake's outcome wasn't
  captured before the round was stopped).
- One `java.lang.NullPointerException` at `11:35:48.585` in `android.bluetooth.BluetoothAdapter.
  finalize()` (GC finalizer thread) — benign framework noise, not app code, not correlated with
  anything else in the capture.
- `dalvikvm` verifier warnings for `WifiP2pConfig$Builder` (API 29+) and `MediaCodec$CodecException`
  (this API-19 device can't resolve either class) are expected noise from targeting a much newer
  `targetSdk` (36) than this device's API level; the app's own logs show it falls back correctly
  ("this unit will not say whether it has a 5 GHz band (below Android 5.0 ...)", "the band goes back
  to being the driver's choice") rather than crashing on them.

Two things are true at once here, and this round did not have time to separate them: (1) a group-1
class teardown-races-a-successful-join bug that the not-yet-installed `61143283f` may already
address, and (2) a distinct, longer-lived-group failure where the phone flatly reports
`WIFI_NETWORK_UNAVAILABLE` against a network that stayed up the full 60s watched — which looks like
a join failure on the phone's WiFi side (band/channel or driver incompatibility with this tablet's
P2P GO), not a head-unit-side timing bug. `WifiDirectManager.createQuietGroup`'s own log ("every
operating channel this unit was offered (36 (5180 MHz), 6 (2437 MHz)) has been tried, so the band
goes back to the driver's choice") means the actual operating channel for either group was never
confirmed — this unit is below Android 5.0 and can't ask. Worth capturing `dumpsys wifi p2p` or
`iw dev p2p-wlan0-0 info` on the *next* round, while a group is live, to nail down which channel the
driver picked; DFS 5 GHz P2P-client join failures on the phone side are a plausible match for
`WIFI_NETWORK_UNAVAILABLE`.

## Anything the brief did not ask about

- No brief exists yet for this thread; this results file is the first artifact. Recommend the next
  round starts from a fresh brief that: (a) charges the unit fully first, (b) rebuilds against
  current `fix/audio-sink-and-wireless-bring-up` tip (`5bf505d7a`, which includes `61143283f`) so the
  "two bring-ups" fix is actually under test on this hardware, and (c) captures the operating
  channel/frequency of the P2P group while it's live.
- This tablet's toolbox (no `sed`/`awk`/`busybox`) is a first for the rig's device roster — every
  other unit has had at least one of those. Any future settings-writer script for a non-rooted,
  toolbox-light device should follow the "edit the pulled file locally, push the whole file back"
  pattern used here rather than assuming `sed` on-device.
- `wpa_supplicant`'s own `P2P_GROUP_REMOVE` control-interface log line fired for group 2 with no
  correlated app-side "removing group" log nearby — worth checking whether this Samsung/degaswifi
  supplicant build has its own idle-group timeout independent of anything `WifiDirectManager` calls.
