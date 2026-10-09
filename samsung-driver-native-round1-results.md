# samsung-driver-native, round 1 results

- **Candidate:** none. Measurement on `main` `71375a689f704df1dc844e6a0656a58e471a12aa`, tree `4bdf708fad4db692e8a0d71c60eb259b06a4120a` (verified in the scratch worktree).
- **Baseline:** none.
- **APK:** `com.andrerinas.headunitrevived_3.5.0-beta4_debug.apk`, md5 `fdda065cedc4af01bcaad029147901dd`, installed with `install -r` on D-HU, D-POCO and D-SAM (all three `apk_identity.sh` MATCH). Never installed on the S24. The app reports `commit":"71375a689f70-dirty"`: the worktree carried an untracked `local.properties` and `.WORKTREE-INFO`; the tracked tree is clean.
- **Unit:** head units D-SAM (Stage A) and D-POCO (Stage B); driver phone S24 (Android 16, `ro.build.version.oneui` = 80500, Gearhead 17.7.663654-release). D-HU was installed but not used (R4 not reached).
- **Date:** 2026-10-09.
- **Evidence:** head unit captures only, release asset `rig-evidence-samsung-driver-native` (`samsung-driver-native-round1-captures.zip`, sha256 `aefc6a86e44aa4ec665cae805fcf3e12c0d935dc7687819f0df4580e98cd7e6c`). S24 captures stay private on the tester PC and are not uploaded.
- **Round verdict:** see R1. Unit tests: `testGithubDebugUnitTest` passed.

## Setup notes

- **Quirk files read:** `rig-quirks/topics/tooling.md`, `topics/gearhead.md`, `units/D-SAM.md`, `units/D-POCO.md`, `units/D-MOTO.md`. Not read: `topics/bt.md`, `units/D-HU.md`.
- **Pre-flight.** First attempt FAILED on a stray watcher process belonging to another round (`wizard-display-and-vehicle-round1`); the operator said to kill it; second pre-flight: D_SAM, D_POCO, D_MOTO, D_HU all `adb ok, wifi_on 1, bt 1`, HFP:none A2DP:none, D_SAM screen Asleep, others Awake, `PREFLIGHT OK`. The S24 is not a rig role; its Bluetooth read 0 (`BLE_ON`) until the operator switched it on (the one hand fix the brief allows).
- **S24 inventory (P2, P3), read-only:** notification access for Gearhead = 1 (so R2's precondition P3=0 was false and R2 did not run); Bluetooth was off, then on; WiFi on; 5 bonded devices, of which one rig radio (D-SAM's `Navegadortz3`). P4: operator answered keep the D-SAM pairing, stay unlocked.
- **Scripts used:** `build_hur_cool.sh`, `run_unit_tests.sh`, `apk_identity.sh`, `set_prefs_runas_host.py` (D-SAM and D-POCO), `restore_settings.sh`, `rig_thermal.sh`, `rig_preflight.sh`; one-round scripts `scripts/bringup.sh` (the brief's `bringup()` plus pre-checks and a rig lock) and `scripts/grade_counts.sh` in the round's data folder. Host thermal: build peaked 89C and was paused by `thermal_guarded` (CONT at 67C); runs started at 55 to 67C, `throttle_pkg=0` throughout.
- **Build:** the fresh worktree had no `local.properties`; the first build failed with `SDK location not found`; copied the main checkout's file in (untracked), second build exit 0.
- **Brief errata, each affects how a line was read:**
  1. **P3's `dumpsys bluetooth_manager | grep address` redacts the first four octets** (`XX:XX:XX:XX:70:EF`). I wrote that string into the head unit's wake list. The app saw a wake list that matched nothing (`No wake poke phone selected, so nothing is poked`). The real MAC was taken from the value the app itself saved after the phone dialled in. **R0-1 is void for this.**
  2. **`Service Discovery Response` is not in this build's log.** The substitute used: `Service Discovery Request: Android` plus the `[ServiceDiscovery]` announcement lines after SSL.
  3. **`Initializing WiFi Mode: NATIVE` is logged 1 to 2 s before the `-start` marker**, so a marker-windowed count is always 0. Present once per bring-up in the full capture.
  4. **The phone's `Creating rfcomm socket for device` line never appeared** (0 in all four phone captures). The dialled radio is graded from the head unit line `Connection accepted from <phone> on local radio [<radio>]`.
  5. `~/rig-private/` is blocked by the tidy hook; private files live in `rig-data/rounds/samsung-driver-native-round1/private/`.
  6. D-SAM (API 19) has no `cmd bluetooth_manager`, so its Bluetooth could not be switched off for Stage B. It stayed on, with its app stopped.
- **Deviations by me:**
  - R1-1 was launched while D-POCO showed a Settings page (`ConnectedDeviceDashboardActivity`); I saw the focus read and launched anyway. The script's launch of MainActivity foregrounded the app 2 s in. R1-1 formed a session, so it was not re-run; R1-2 started with D-POCO on its launcher.
  - D-SAM clock reads about 16 s behind the host (`Fri Oct 9 16:57:29` against host `16:57:45`). Head unit times below are D-SAM / D-POCO device clocks.
  - D-SAM's `connection-modes` is `{wifi}`; D-POCO's is `{wifi, usb}`.
- **Hand steps recorded:** Bluetooth on the S24 (P3 fix); S24 Home screen before R0-2 (the script refused twice, S24 was in Settings and in Android Auto settings); pairing D-POCO's radio before Stage B; unpairing D-POCO's radio and forgetting vehicles after Stage B. No Gearhead first-run screens appeared. **The operator toggled the S24's call-over-Bluetooth setting for D-SAM's radio during R0-1** (marker `R0-1-operator-calls-toggle`, 16:57:18 host clock, after the D-SAM group was already removed). **The operator forgot all vehicles in Android Auto at the Stage B hand step**, where the brief says never use "Forget all cars"; the operator's own vehicle entries may have been affected.
- **Settings written** (D-SAM and D-POCO, app stopped, then restored from the pre-round backup; diff against backup empty apart from line endings): `wifi-connection-mode` 3, `native-poke-bt-macs` {S24 MAC}, `native-poke-all-paired` false, `native-driver-selection-mode` 0, `onboarding-version` 2, `log-level` 1.
- **Clean-run protocol:** not applied to the S24 (brief section 0). D-POCO and D-MOTO Bluetooth off for Stage A (read 0 after 20 s); D-MOTO back on at the end.

## R0

**RECORDED (no PASS or FAIL by the brief)**

Stage A: S24 to D-SAM.

**R0-1: void** (redacted MAC in the wake list, plus the operator call toggle). For the record: window 150 s; first `Connection accepted from` at 16:56:36.682 (S24 dialled in, 118 s after the start marker 16:54:38.347; the app saw no poke target until it saved the S24 at 16:56:36.812); `WifiVersionResponse ... status=NO_SUPPORTED_WIFI_CHANNELS(-8)` at 16:56:37.283; `[RX] WifiStartResponse ip= status=SUCCESS(0)` at 16:56:38.424; `Client list empty, remove non-persistent p2p group` 16:56:45.491 then `(the platform took it down)` 16:56:47.563; phone `CTRL-EVENT-ASSOC-REJECT` x7 and `TapHeadUnitActivity` x203 afterwards; no `Incoming connection`, no SSL.

**R0-2: session formed.** Start marker 17:02:57.063, SSL at 17:03:35.431 (38.4 s), after two handshakes.

| Count (window `R0-2-start` to `R0-2-end`) | R0-2 |
|---|---|
| `Attempting active poke to device` | 2 |
| `hands-free service level connection established` | 2 |
| `Connection accepted from` | 2 |
| `Sending WifiStartRequest (Type 1)` | 2 |
| `[RX] WifiStartResponse` (status SUCCESS) | 2 |
| `WifiVersionResponse ... NO_SUPPORTED_WIFI_CHANNELS(-8)` | 2 |
| `[RX] WifiConnectStatus status=SUCCESS(0)` | 1 |
| `Incoming connection detected from` | 1 (17:03:34.500) |
| `SSL handshake complete` | 2 (lines) |
| `Client list empty` / `(the platform took it down)` | 1 / 1 (17:03:11.317 / 17:03:13.520, first handshake) |
| HU pairing lines | 0 |

Phone (R0-2): `GH.` 1292; `NO_HFP_FROM_HU_PRESENCE` 0; `THROTTLE_LIMIT_EXCEEDED` 0; `WIRELESS_SETUP_CANCELLED_ALREADY_STARTED` 3; `TapHeadUnitActivity` 0; pairing lines 0; `CTRL-EVENT-CONNECTED` 3, `-DISCONNECTED` 6; `Critical error` 4 (all after the end marker, when the app exited). Top activity at 60 s: Maps `GhostActivity` (projection up).

## R1

**PASS**

S24 to D-POCO, 2 bring-ups: session formed in 2 of 2. Dialled radio `POCO X3 NFC` in both (`Connection accepted from <REDACTED> on local radio [POCO X3 NFC]`, 17:07:51.691 and 17:10:50.414).

| Window count | R1-1 | R1-2 |
|---|---|---|
| `Attempting active poke to device` | 1 (17:07:49.668) | 0 in window; 1 in full capture at 17:10:49.630, 0.1 s before the marker |
| `hands-free service level connection established` | 1 | 1 |
| `Connection accepted from` | 1 | 1 |
| `Sending WifiStartRequest (Type 1)` | 1 | 1 |
| `[RX] WifiStartResponse` | 1 (SUCCESS) | 1 (SUCCESS) |
| `WifiVersionResponse ... NO_SUPPORTED_WIFI_CHANNELS(-8)` | 1 | 1 |
| `Incoming connection detected from` | 1 (17:08:00.183) | 1 (17:10:58.872) |
| `[RX] WifiConnectStatus status=` | SUCCESS(0) | SUCCESS(0) |
| `SSL handshake complete` | 17:08:00.535 | 17:10:59.190 |
| seconds from start marker to SSL | 10.9 | 9.5 |
| `Service Discovery Request: Android` | 17:08:00.769 | 17:10:59.413 |
| HU pairing lines | 1 (bond-state change at 17:08:03, after SSL) | 0 |

Phone: `NO_HFP_FROM_HU_PRESENCE` 1 and 0, at 17:08:09, after the session was already up in R1-1; `TapHeadUnitActivity` 0 and 0; `THROTTLE_LIMIT_EXCEEDED` 0 and 0; `WIRELESS_SETUP_CANCELLED_ALREADY_STARTED` 2 and 2 (later restart attempts, session already formed); `CTRL-EVENT-ASSOC-REJECT` 0 and 10 (R1-2, session still formed at 17:10:59); phone pairing lines 2 and 0 (R1-1: bond-state changes at 17:08:03 and 17:08:05, after SSL). Top activity at 60 s: Maps `GhostActivity` both times. No latch. D-POCO as head unit carries no video, so graded on SSL and the discovery request, as the brief says.

## R2

**UNTESTABLE**

Not run: its precondition (P3 notification access = 0 and R1 hit the notification gate) was false; access was 1 and R1 passed.

## R3

**UNTESTABLE**

Not run: only if R1 FAILED.

## R4

**UNTESTABLE**

Not run: only if R1 FAILED and R3 PASSED.

## Residue check

**PASS**

`residue-before.txt` against `residue-after.txt` (ignoring the date line): diff empty. `find /sdcard /data/local/tmp -type f -mmin -90` outside `/Android/`: **0**. Run once, as the last adb command on the S24. The operator's closing hand steps (Android Auto car list, Bluetooth pairings, `DIRECT-` saved networks, revoke USB debugging) are theirs and happen after this check.

## Anything the brief did not ask about

- **D-SAM sends the phone `NO_SUPPORTED_WIFI_CHANNELS(-8)` in every handshake** (2 of 2 in R0-2, and D-POCO sends the same in R1-1 and R1-2), and sessions formed regardless. Status is reported in `WifiVersionResponse`, not a gate here.
- **A group named from before the bring-up was already up on D-SAM at the start of R0-1** (`a group named ... is already up from before this bring-up; reading it instead of tearing it down`).
- **Report-back (brief section 9):** (1) R1: session formed in 2 of 2 bring-ups, no failing gate. (2) R3 not run, so the result reads neither "Samsung" nor "phone as head unit"; nothing failed. (3) P3: notification access 1, Gearhead 17.7.663654-release; residue check PASS.
- **Brief changes worth making before another round:** P3 must read the adapter address from somewhere that is not redacted (for example the head unit's own `Connection accepted from` line, or ask the operator); drop `Service Discovery Response`; take `Initializing WiFi Mode: NATIVE` from the full capture; drop the phone `Creating rfcomm socket` gate or note it never appears on Gearhead 17.7; mention that D-SAM cannot switch its Bluetooth off over adb.

## Addendum

Run of `samsung-driver-native-round1-addendum.md` on the same build (`main` `71375a68`, APK md5 `fdda065c`), D-POCO as head unit, S24 as driver, 2026-10-09 17:44 to 17:51 (D-POCO clock). Scripts `scripts/a0.sh`, `scripts/a1.sh`, `scripts/grade_a1.sh` in the round's data folder. Head unit captures: release asset `rig-evidence-samsung-driver-native`, `samsung-driver-native-round1-addendum-captures.zip`, sha256 `8828194ade992d68f1414c0e3caf20c92999cacabc24fb18b7f4b4acfd9ff1ab`. S24 captures stay private.

### Addendum setup notes

- **Pre-flight:** D-POCO, D-HU wifi 1 bt 1 and D-MOTO bt 0, `PREFLIGHT OK` on the second attempt; the first FAILED on my own idle Gradle daemon from the earlier build (killed by pid).
- **S24 set-up:** the operator unpaired D-SAM's radio on the S24 (D-POCO's only), re-granted Gearhead's permissions after a separate cleaned-permissions test (not part of this addendum), and kept the S24 unlocked on home. USB debugging was still on. `residue-before-addendum.txt` equals round 1's baseline.
- **`S24_MAC`:** D-POCO's bonded list also redacts it (`XX:XX:XX:XX:70:EF`), so the address saved by the app after the earlier dial-in was used from cycle 1 on (a wake list present from the start, not empty). A0 used an empty wake list (`<set name="native-poke-bt-macs">` with no strings) and `native-poke-all-paired` false; no poke went out in any A0 create.
- **Before A0:** D-POCO's `settings.xml` had no `wifi-direct-last-identity-verdict` and no `wifi-direct-last-group-bssid` key; after A0 the verdict reads `STABLE`.
- **A1 script:** the brief's `bringup` with its fixed 60 s and 87 s sleeps replaced by the addendum's wait loop (poll `SSL handshake complete` every 5 s up to 150 s, 20 s with the session up, `ACTION_DISCONNECT`, 10 s, end marker). D-MOTO Bluetooth off for the stage and back on afterwards; D-POCO settings restored from the pre-round backup (diff empty).
- **A2 not run:** only if A1 was POISONED.

### A0

**RECORDED (no PASS or FAIL by the addendum)**

D-POCO's cell: **IP holds, BSSID holds** (safe unit, like D-HU).

| Create | `GO IP` | SSID token | BSSID token | `stable=` |
|---|---|---|---|---|
| A0-1 | `192.168.49.1` | SSID-1 | BSSID-1 | no (first group under this name) |
| A0-2 | `192.168.49.1` | SSID-1 | BSSID-1 | yes (same name and same BSSID as the last group) |
| A0-3 | `192.168.49.1` | SSID-1 | BSSID-1 | yes |

The three IPs are equal, the three BSSID tokens are equal. Each group was persistent (`persistent=yes`, one network id across the three). `ip -4 addr show` at +30 s read `192.168.49.1/24` on `p2p0` in each. No WPP-over-TCP endpoint line printed in A0 (no phone joined). A0-1 `Group formed` 17:44:53.996, A0-2 17:45:44.480, A0-3 17:46:35.012. This is not the field fault's shape, so A1 is not expected to poison on this unit.

### A1

**PASS**

3 cycles, S24 bonded to D-POCO only. Paired with A0's cell, this proves only that the safe path works.

| Cycle | Start to SSL | Endpoint line | Dial of the endpoint | Grade |
|---|---|---|---|---|
| A1-1 (forms the record) | 10.3 s | `NativeAA: advertising WPP over TCP at 192.168.49.1:5299` at 17:48:03.401; `Connection accepted from` 1; `Incoming connection detected from` 1 | none (first connection over Bluetooth) | session formed; `Native AA user exit` 17:48:36.636 |
| A1-2 (reconnect) | 8.7 s | no `advertising` line in the window | `WppTcpServer: connection from 192.168.49.112` at 17:49:26.173; SSL 17:49:26.877 | **RECONNECT-OK**; user exit 17:49:48.319 |
| A1-3 (reconnect) | 8.0 s | no `advertising` line in the window | `WppTcpServer: connection from 192.168.49.112` at 17:50:23.619; SSL 17:50:24.277 | **RECONNECT-OK**; user exit 17:50:46.385 |

Both reconnects came in over WPP-over-TCP at the stored endpoint, with `Connection accepted from` 0 and `Attempting active poke` 0 in their windows (the phone dialled without any Bluetooth wake). Phone side, A1-2 and A1-3: `Trying to start WPP on TCP with configuration` 1 each, `No WPP on TCP configuration found in storage` 0, `NETWORK_NOT_FOUND` / `TCP_SOCKET_CONNECTION_FAILED` / `BSSID_MISMATCH` 0, `Restarting WPP over TCP` 0, `THROTTLE_LIMIT_EXCEEDED` 0, `TapHeadUnitActivity` 0. In A1-1 the phone logged `No WPP on TCP configuration found in storage` 1, `Trying to start WPP on TCP` 1 and `Restarting WPP over TCP` 1 (record created). `needs this head unit forgotten` 0 and `not advertising WPP over TCP:` 0 in all three. No POISONED cycle, so the recovery step and A2 were not needed.

### Addendum residue check

**PASS**

`residue-before-addendum.txt` against `residue-after-addendum.txt` (ignoring the date line): diff empty; `find` count 0. The operator's closing hand steps (forget only rig vehicles in Android Auto, unpair D-POCO's radio, forget `DIRECT-` saved networks, revoke USB debugging authorisations, USB debugging off) are theirs.

### Addendum report back

1. A0's cell for D-POCO: IP holds, BSSID holds; `stable=` no, yes, yes.
2. A1: cycle 2 and cycle 3 both RECONNECT-OK, by WPP-over-TCP at `192.168.49.1:5299` (endpoint line printed once, in cycle 1).
3. A2: not run.
