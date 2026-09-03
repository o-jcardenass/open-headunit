# field-ride-automation-btautostart — round 3 results

Extra run of the round-2 brief with the **device roles swapped**: POCO X3 NFC as the headunit,
Motorola edge 30 neo as the phone. Requested to check the fixes hold when the head unit is not the
MT50. Same five runs, same APK.

**Candidate:** `fork/testing/automation-plus-btautostart` @ `1f06d8a83224`. **Baseline:** none.
**APK md5:** `3f53e72fac039bef23f96efa4da092e6` — installed on the POCO with `adb install -r -d`
over the pr-readiness build (`0abee20c…`); confirmed live with `md5sum $(pm path …)`. Fix-introduced
DEX strings present.
**Units (serials fixed for this channel):**

| Serial | Device | Role this round |
|---|---|---|
| `27870808938846` | UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14) | *not used* — dedicated motorcycle headunit |
| `4f4027e9` | POCO X3 NFC (`M2007J20CG`, Android 15, Gearhead `17.5.663204`) | **headunit** (also: sometimes phone / Self Mode) |
| `ZY22GC3BM4` | Motorola edge 30 neo (`A0:46:5A:97:E4:95`, Gearhead `17.5.663204`) | **phone** (always phone; sometimes Self Mode) |

POCO ⇄ Moto are BR/EDR-bonded both directions.
**Date:** 2026-09-03

## Result in one line

**R1 PASS, R2 PASS, R3 PASS, R4 PASS (on the re-run R4b), R5 PASS.** The R1/R2/R3/R5 outcomes match
the MT50-headunit round exactly. R4 was **INCONCLUSIVE on the first attempt** (the Moto rejoins the
POCO's cached Wi-Fi Direct group directly, so the Bluetooth RFCOMM handshake that writes
`native-poke-bt-macs` never runs; `auto-start-bt-macs` did not gain the address, so not a FAIL).
Re-run as **R4b** after rotating the POCO's Wi-Fi Direct group identity so the Moto could not
shortcut-rejoin: the full handshake then ran, `native-poke-bt-macs` gained the Moto's address, and
`auto-start-bt-macs` stayed empty — **PASS**.

## Setup notes

- **Serials/roles are now recorded in the table above and should go into `TESTING-TEMPLATE.md`
  §7a.** The POCO is dual-purpose (phone *or* headunit *or* Self Mode target); the Moto is always the
  phone; the MT50 is the fixed motorcycle headunit.
- **The POCO is not rooted** (`adb shell id` → uid 2000). `set_hu_settings_host.py` assumes a root
  shell (`adb shell cat`/`cp` on `/data/data/...`) and does not work here. Added
  **`set_hu_settings_runas.py`** — same scalar / `set:` / `setclear:` / `del:` interface, but reads
  and writes `settings.xml` through `run-as PKG` from `/data/local/tmp`. Left in
  `hur-wifi-test-scripts/`.
- **Trigger method** as in round 2: phone radios off at launch (no session forms), then the phone's
  Bluetooth switched **on** as the trigger. On the POCO↔Moto pair the ACL reconnect that raises
  `ACL_CONNECTED` is **less reliable than MT50↔POCO** — it fired first try for R1 and R3, but not
  for R2 across three attempts (BT off/on cycles, and an `ACTION_NATIVE_AA_POKE`, which is a no-op
  outside Native mode: `AapService: manual Native-AA poke ignored: the wireless mode is not Native
  AA.`). For R2 the `MainActivity` launch intent that `AutoStartReceiver` would have sent was
  injected directly — `am start -n …/MainActivity --es launch_source 'Bluetooth auto-start'` — which
  reaches the identical decision path (`handleLaunchIntent` → `BtAutoStartRearmPolicy.launchesSelfMode`).
  Noted per-run.
- **`ACTION_LOG_MARKER --es text` truncates at the first space over `adb shell`** (as in round 2) —
  wrapped the whole `am` in one quoted string, underscore-separated marker text.
- **The Moto never does the BT credential handshake on this pair.** In every run (R1, R3, R4) the
  session formed as `WirelessServer: Incoming connection detected from /192.168.49.62` with **zero**
  `NativeAA: Handling handshake for …` lines — the Moto's Gearhead has the POCO's stable-identity
  group (`DIRECT-X3-Navegadortz`) cached and joins it directly. This is the §7a "the phone's own
  reconnect beats the poke" quirk; here it also skips the WifiInfoResponse exchange entirely. It is
  what makes R4 INCONCLUSIVE (see R4).
- **POCO settings** backed up via `run-as cat` before the round and restored byte-identical after
  (`diff` → exact match). The round-2 APK was **left installed** on the POCO (prior-round
  convention; both APKs are from the same `automation-plus-btautostart` lineage).
- Scripts used: `build_hur.sh` / `run_unit_tests.sh` (round 2's gate, unchanged — 1266/0),
  `set_hu_settings_runas.py` (new), `set_autostart_btmac.sh` not needed.

---

## R1 — a Bluetooth auto-start in Native mode leaves Self Mode alone

**PASS**

- **Settings (POCO, via `run-as`):** `wifi-connection-mode=3`, `connection-modes={wifi,self}`,
  `enable-audio-sink=true`, `auto-start-bt-macs={A0:46:5A:97:E4:95}`,
  `native-poke-bt-macs={A0:46:5A:97:E4:95}`, `native-poke-all-paired=true`,
  `allow-external-configuration=true`, `log-level=2`. (Only the last three needed adding — the POCO
  carried the rest from earlier rounds.)
- **Radio state:** Moto Bluetooth + WiFi off at launch (verified); POCO Bluetooth on; POCO app
  launched, group + RFCOMM listeners up on radio `[POCO X3 NFC]` with no phone reachable
  (state query at the marker: `connected=false, state=Disconnected`); then Moto Bluetooth on.
- **Discard-rule check:** clean — one 5 GHz `createGroup SUCCESS!` (pre-marker), one
  `SSL handshake complete`, 0 `Magic Garbage`, no unintended `MATCH!`.
- **Decisive lines** (`r1.txt.gz`):

  ```
  13:28:43.364  WifiDirectManager: 5GHz createGroup SUCCESS!
  13:29:02.774  AutomationMarker: R1_trigger              (state: connected=false)
  13:29:16.042  AutoStartReceiver.onReceive | BT Device connected: motorola edge 30 neo (A0:46:5A:97:E4:95)
  13:29:16.042  AutoStartReceiver.onReceive | MATCH! Starting AapService via Bluetooth Auto-start...
  13:29:16.088  MainActivity.handleLaunchIntent | MainActivity: Bluetooth auto-start: leaving Self Mode alone
  13:29:21.599  WirelessServer: Incoming connection detected from /192.168.49.62
  13:29:21.605→22.109  session state connecting → connected → projecting
  13:30:02.881  AutomationMarker: R1_end                  (state: connected=true, wifiMode=NATIVE)
  ```

- **Counts after `R1_trigger`:** `MATCH! Starting AapService` = 1 (reachability gate met),
  `leaving Self Mode alone` = 1, `forcing a Self Mode launch` = 0, `All launchers failed` = 0,
  `Skipping auto-poke` = 0, `session state disconnected (user_exit)` = 0, `createGroup SUCCESS!` = 0.

Identical to the MT50-headunit round.

---

## R2 — the same trigger in Helper mode still launches Self Mode (positive control)

**PASS**

- **Settings:** as R1 but `wifi-connection-mode=2`. (`helper-connection-strategy=1` / WiFi Direct on
  the POCO — the POCO used `WifiLauncher: Using strategy WIFI_DIRECT.`)
- **Trigger:** the genuine ACL trigger did **not** fire on POCO↔Moto across three attempts, so the
  `AutoStartReceiver` → MainActivity launch intent was injected directly:
  `am start -n …/MainActivity --es launch_source 'Bluetooth auto-start'` (state at that moment:
  `connected=false, wifiMode=HELPER, Disconnected`).
- **Decisive lines** (`r2.txt.gz`):

  ```
  13:34:09.994  AutomationMarker: R2_inject
  13:34:10.070  MainActivity.handleLaunchIntent | MainActivity: Bluetooth auto-start: forcing a Self Mode launch
  13:34:10.139  SelfMode: Installed AA version: 17.5.663204-release (major=17, minor=5)   ← the POCO's own AA (it is the headunit here)
  13:34:10.145  session state connecting
  13:34:10.162  SelfLauncherV17_4.run | SelfMode: Headunit Server (127.0.0.1:5277) is NOT running.
  13:34:10.217  SelfMode: All launchers failed
  13:34:18.116  AutomationMarker: R2_inject_end
  ```

- **Counts after `R2_inject`:** `forcing a Self Mode launch` = 1, `leaving Self Mode alone` = 0,
  `All launchers failed` = 1, **`session state disconnected (user_exit)` = 0**,
  `Self Mode disconnected. Not restarting` = 0, `Skipping auto-poke` = 0.

R1 and R2 give opposite decisions on the same code path with only `wifi-connection-mode` changed —
the Native-only veto is not too wide. This round's R2 is also the **stronger** demonstration of the
F2 fix: unlike the MT50 round (where the forced Self Mode launch happened to succeed via a loopback
5288 session on the POCO-as-phone), here the launch genuinely **fails** (`All launchers failed`,
AA 17.4+ / no `:5277` server) and the fix (`77e0a650` "a launch that fails is not a session that
ended" + `5b9481ab`) holds: **no false `user_exit`, no auto-poke suppression, no wireless-launcher
teardown**. Only `session state connecting` was emitted, then nothing.

Because the trigger was injected rather than ACL-driven, this run does not exercise
`AutoStartReceiver` itself in Helper mode — but R1 on the same pair proved that plumbing, and
`AutoStartReceiver`'s only Helper-mode-specific behaviour (`BtAutoStartActions(…
forceRearmWireless=false …)`) was logged in a discarded R2 attempt at `13:30:57`.

---

## R3 — the media and speech sinks are announced on a wireless session

**PASS**

- **Settings:** R1 set, `wifi-connection-mode=3`, `enable-audio-sink=true`.
- **Route:** POCO app up, Moto Bluetooth on; Native AA session formed at `13:35:44` (~25 s), Moto
  joining the cached P2P group directly.
- **Decisive lines** (`r3.txt.gz`):

  ```
  13:35:44.440  WirelessServer: Incoming connection detected from /192.168.49.62
  13:35:44.596  AapSslContext.performHandshake | SSL handshake complete.
  13:35:44.844  session state projecting
  13:35:45.115  Media Sink Setup Request: 3 on channel VIDEO   → Config response: status: HEADUNIT
  13:35:45.152  Media Sink Setup Request: 1 on channel AUDIO2  → status: HEADUNIT
  13:35:45.158  Media Sink Setup Request: 1 on channel AUDIO1  → status: HEADUNIT
  13:35:45.159  Media Sink Setup Request: 1 on channel AUDIO   → status: HEADUNIT
  13:35:46         First frame rendered
  ```

- **Channels announced:** `AUDIO2` + `AUDIO1` + `AUDIO` — all three, each `status: HEADUNIT`.
- **Skip-reason check:** `Self Mode is projecting this device to itself` = 0,
  `Audio sink is off in Settings` = 0.
- One session (1 SSL, 1 `Incoming connection`), 0 `Magic Garbage`. The POCO renders video fine as a
  Native AA headunit (`First frame rendered` = 1).

---

## R4 — clearing the Bluetooth auto-start entry sticks

**PASS** (on the re-run R4b). First attempt was INCONCLUSIVE — the code path that writes
`native-poke-bt-macs` is not reached while the phone can shortcut-rejoin a cached group.

- **Gate:** `run-as … stat -c '%U:%G' shared_prefs` → `u0_a268:u0_a268` (app-owned — writes would
  land).
- **Settings:** R1 set, `auto-start-bt-macs` and `native-poke-bt-macs` both written as empty sets
  (`setclear:`). `native-poke-all-paired=true` kept.
- **What happened** (`r4.txt.gz`):

  ```
  13:37:03.969  NativeAA: No wake poke device selected, and poking all paired devices is on. Poking all of them...
  13:37:03.980 → 13:37:15.229  pokeDevice() walks the paired list: Navegadortz2 (the MT50) HFP-AG/HSP-AG, then motorola edge 30 neo HFP-AG/HSP-AG
  13:37:17.311  NativeAA: Successfully poked motorola edge 30 neo via HSP-AG. Holding 15000ms...
  13:37:24.268  WppTcpServer: TLS handshake complete with 192.168.49.62
  13:37:24.471  WirelessServer: Incoming connection detected from /192.168.49.62
  13:37:24.505  NativeAA: session is up — cancelling the poke retry loop
  ```

  **No `NativeAA: Handling handshake for …` and no `NativeAA: Saving … as the wake poke device.`**
  The Moto joined the POCO's cached stable-identity group directly over Wi-Fi Direct; it never
  connected back to the AA RFCOMM UUID, so `handleHandshake()` — which is where
  `PokeTargetPolicy.adoptsHandshakedDevice(settings.nativePokeBtMacs)` runs and writes
  `native-poke-bt-macs` — did not execute.

- **`settings.xml` readback** (`run-as`, app force-stopped, before restore):

  ```
  <set name="auto-start-bt-macs" />
  <set name="native-poke-bt-macs" />
  ```

  Both still empty. `auto-start-bt-macs` did **not** gain the address → not a FAIL. Neither key was
  written → the brief's stated **INCONCLUSIVE** condition ("INCONCLUSIVE if neither key was
  written").

- **Why the MT50 round could run it:** there the phone (POCO) *did* connect back over the AA RFCOMM
  and `handleHandshake` ran (`Handling handshake for POCO X3 NFC` → `Saving DC:B7:… as the wake poke
  device.`).

### R4b — re-run after forcing the phone through the handshake

**PASS**

The Moto has no "car" in Android Auto's own list to forget (`Vehicles` → `Accepted vehicles: None`,
`Rejected vehicles: None`); what it rejoins is an **OS-level Wi-Fi Direct persistent group**, and
`cmd wifip2p` is root-only (`SecurityException: Uid 2000 does not have access`) so it cannot be
cleared over adb on this non-rooted phone. Instead the **POCO's** group identity was rotated so the
Moto's cache could not match:

- Settings added on the POCO: `wifi-direct-stable-identity=false`, and
  `wifi-direct-group-name` / `wifi-direct-last-group-ssid` / `wifi-direct-last-group-bssid` /
  `wifi-direct-group-passphrase` deleted. Everything else as R4 (`auto-start-bt-macs` and
  `native-poke-bt-macs` empty, `native-poke-all-paired=true`).
- The POCO then formed a fresh group each bring-up (`DIRECT-M1-…`, `DIRECT-7I-…`,
  `identity stable=unproven`), which the Moto had never joined.

**Decisive lines** (`r4b.txt.gz`):

```
13:54:21.856  NativeAA: Connection accepted from motorola edge 30 neo (A0:46:5A:97:E4:95) on local radio [POCO X3 NFC]
13:54:21.870  NativeAA: Handling handshake for motorola edge 30 neo (A0:46:5A:97:E4:95)
13:54:21.872  NativeAA: Saving A0:46:5A:97:E4:95 (motorola edge 30 neo) as the wake poke device.
13:54:22.317  NativeAA: [TX] Sending WifiInfoResponse (Type 3) with full credentials in 1000ms...
13:55:13.356  WirelessServer: Incoming connection detected from /192.168.49.132
13:55:13.562  AapSslContext.performHandshake | SSL handshake complete.
13:55:13.809  session state projecting
```

**`settings.xml` readback** (`run-as`, app force-stopped, before restore):

```
<set name="auto-start-bt-macs" />
<set name="native-poke-bt-macs">
    <string>A0:46:5A:97:E4:95</string>
</set>
```

`native-poke-bt-macs` gained the Moto's address (positive control — the write mechanism works);
`auto-start-bt-macs` stayed empty. Same result as the MT50 round: a completed handshake writes the
wake-poke target and never the auto-start list.

With `wifi-direct-stable-identity=false` the group re-randomised on each `recoverNativeGroup` and the
handshake ran two–three times (`13:54:21`, `13:55:04`) before the session settled at `13:55:13` —
the rig's known unstable-identity churn, not verdict-affecting. POCO `settings.xml` restored
byte-identical afterwards (`diff` → exact match).

---

## R5 — port 5299 still binds every bring-up (regression guard only)

**PASS**

- **Procedure:** launch, then `ACTION_STOP_SERVICE` / 6 s / `am start MainActivity`, ×3, each cycle
  marked. Process pid `19623` stable across all four bring-ups (in-process re-bind).
- **Decisive lines** (`r5.txt.gz`):

  ```
  13:40:34.802  WppTcpServer: listening for Android Auto on TCP 5299   (cycle 0)
  13:40:52.361  WppTcpServer: listening for Android Auto on TCP 5299   (cycle 1)
  13:41:10.747  WppTcpServer: listening for Android Auto on TCP 5299   (cycle 2)
  13:41:29.118  WppTcpServer: listening for Android Auto on TCP 5299   (cycle 3)
  ```

- **Counts (whole capture):** `listening … TCP 5299` = 4, `could not listen on 5299` = 0,
  `EADDRINUSE` = 0.

---

## Anything the brief did not ask about

- **`SelfLauncherManager` reads the headunit device's own installed Android Auto**, not the phone's.
  On the MT50 that is the bundled `gearhead:car` (`17.3.662864`, legacy path); on the POCO it is the
  POCO's Gearhead (`17.5.663204`, 17.4+ path). Round 2's results file has been corrected — it
  previously mis-attributed the `17.3` to the POCO phone.
- **POCO↔Moto ACL reconnect is flakier than MT50↔POCO.** Only PAN has a connection policy of 100
  between them (`HEADSET`, `HEADSET_CLIENT`, `A2DP*` all `-1`), which may be why enabling the Moto's
  Bluetooth does not reliably raise `ACL_CONNECTED` on the POCO. It worked for R1/R3, failed for R2.
- **On this pair the Native AA session normally skips the Bluetooth handshake** — the Moto joins the
  POCO's cached `DIRECT-X3-Navegadortz` group directly (R1/R3/first R4). The Moto has nothing in
  Android Auto's own `Vehicles` list to "forget", and its OS Wi-Fi Direct persistent-group cache
  is root-only over adb. The working lever is on the **head unit**: set
  `wifi-direct-stable-identity=false` and delete the saved `wifi-direct-group-*` keys so it presents
  a group the phone has never joined — then the phone is forced through the type 1/2/3 exchange
  (used for R4b, and the way to reach `handleHandshake` / wake-poke adoption / "poke landed → phone
  back over BT" on this pair generally).
- **`ACTION_NATIVE_AA_POKE` is silently inert outside Native mode**
  (`manual Native-AA poke ignored: the wireless mode is not Native AA.`) — not usable as an
  ACL-raising trigger for the Helper-mode R2.
