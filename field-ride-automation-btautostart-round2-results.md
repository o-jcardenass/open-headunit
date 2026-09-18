# field-ride-automation-btautostart — round 2 results

**Candidate:** `fork/testing/automation-plus-btautostart` @ `1f06d8a83224`
(field-ride fixes `f1718991` merged with the automation surface `f625403b`). **Baseline:** none
(no A/B; R2 is R1's own positive control on the same APK).
**APK md5:** `3f53e72fac039bef23f96efa4da092e6` (single APK, `3.3.0` / versionCode 103,
`github/debug`, `commit=1f06d8a83224`).
**Unit:** headunit = UNISOC MT50 (`MT50_YT610E4GFPSL_U`, serial `27870808938846`, Android 14,
1440x720), Native AA wireless only. Phone = POCO X3 NFC (`DC:B7:2E:5E:4E:59`, serial `4f4027e9`),
Gearhead `17.5.663204`. The two are bonded; only the POCO is in the MT50's paired list.
**Date:** 2026-09-03

> **Correction (after round 3):** an earlier revision of this file said "the POCO's Gearhead reports
> `17.3.662864`". That was wrong. `SelfLauncherManager.isAaVersion174OrHigher` reads the Android Auto
> installed **on the headunit device**, which in this round is the MT50 and its own bundled
> `gearhead:car` (`17.3.662864`). The POCO, acting as the *phone* here, still runs Gearhead
> `17.5.663204`. R2's `AA < 17.4` legacy Self Mode branch was chosen from the MT50's bundled AA
> version, not the POCO's.

## Result in one line

**R1 PASS, R2 PASS, R3 PASS, R4 PASS, R5 PASS.** The §9 numbers:

- **R1** after the trigger: `forcing a Self Mode launch` = **0**, `createGroup SUCCESS!` = **0**
  (one group, formed *before* the trigger). `leaving Self Mode alone` = 1.
- **R2** the control reproduced the pre-fix behaviour: `forcing a Self Mode launch` = **1**,
  `leaving Self Mode alone` = **0**. R1 and R2 point in opposite directions — the veto is
  mode-specific, not too wide.
- **R3** audio channels announced on the wireless session: **all three** —
  `AUDIO2` (system), `AUDIO1` (speech), `AUDIO` (media). Neither skip-reason line present.
- **R4** `native-poke-bt-macs` = `{DC:B7:2E:5E:4E:59}` (written by the completed handshake);
  `auto-start-bt-macs` = empty (`<set … />`), unchanged.
- **R5** `listening for Android Auto on TCP 5299` once per bring-up × 4; `could not listen on 5299`
  and `EADDRINUSE` = 0 throughout.

The branch is hardware-clear for a PR.

## Setup notes

### Build gate

- `build_hur.sh` → `BUILD SUCCESSFUL`, APK md5 `3f53e72f…`; confirmed live on D-HU with
  `md5sum $(pm path …)`.
- `run_unit_tests.sh` → `BUILD SUCCESSFUL`; test-results XML totals **1266 tests / 0 failures /
  0 errors**, matching the brief's stated 1266 exactly.
- APK identity confirmed by fix-introduced DEX strings, present:
  `… as the wake poke device.` and
  `NativeAA: No wake poke device selected, and poking all paired devices is on. Poking all of them…`.
- R4 prefs-dir gate: `run-as … stat -c '%U:%G' shared_prefs` and `… shared_prefs/settings.xml`
  both `u0_a168:u0_a168` (app-owned) — the app's writes land.

### Deviations from the brief

1. **R1's trigger method was changed, and R1 was run twice.** The brief's method — bring the app
   up, then `svc bluetooth disable` on the head unit and wait ~40 s for the self-revert and the
   `ACL_CONNECTED` it raises — was run first (capture `r1.txt.gz`) and came back **INCONCLUSIVE**:

   - On this rig the phone's own Android Auto reconnect forms a **full Native AA session within
     ~20 s of launch** (`session state projecting` at `12:51:34`, ~28 s after launch), well before
     the BT self-revert. §7a's "the phone's own reconnect beats our poke" applies to the auto-start
     path too.
   - By the time the post-trigger `ACL_CONNECTED` arrived (`12:52:16`, after the `R1_trigger_bt_off`
     marker at `12:52:02`), `commManager.isConnected` was already true, so
     `AutoStartReceiver`'s pre-existing `[FIX] Don't trigger auto-start if we are already connected!`
     guard (`AutoStartReceiver.kt`, `App.provide(context).commManager.isConnected`) returned early.
     **No `MATCH! Starting AapService` after the marker** → the reachability gate the brief itself
     defines fails → INCONCLUSIVE, not PASS.

   Re-run (`r1b.txt.gz`) with the phone's radios **off at launch** so no session forms, then the
   phone's **Bluetooth switched on** as the trigger. This raised `ACL_CONNECTED` for the phone and
   fired `AutoStartReceiver` with `isConnected == false`, which is the exact condition the field
   bug occurred under (auto-start fires while the session is not yet up). The decision path
   exercised — `AutoStartReceiver` → `context.startActivity(MainActivity, EXTRA_LAUNCH_SOURCE =
   "Bluetooth auto-start")` → `handleLaunchIntent` → `BtAutoStartRearmPolicy.launchesSelfMode()` —
   is identical to what the brief's method would have driven; only the ACL's origin differs.

   **Note, contradicting §7a:** cycling the *phone's* Bluetooth **on** did raise `ACL_CONNECTED` on
   the head unit on this rig today (`AutoStartReceiver.onReceive | BT Device connected: POCO X3 NFC`
   at `12:56:30.793` in R1b, `12:58:37.159` in R2, `13:00:50.148` in R3). The §7a claim that "cycling
   the phone's radio raises no `ACL_CONNECTED` here" did not hold. R2 and R3 used the phone-BT-on
   trigger for the same reason and both saw `MATCH!`.

2. **`ACTION_LOG_MARKER` text is truncated at the first space** when sent as
   `adb shell am broadcast … --es text "R1 trigger"` — the device shell splits the argument and
   `AutomationMarker:` logs only `R1`. Wrap the whole `am` command in one quoted string
   (`adb shell "am broadcast … --es text 'R1_trigger'"`) and use underscores. All markers from R1b
   onward use that form.

3. **R2's forced Self Mode launch did not fail.** The brief expects `SelfMode: All launchers
   failed` after `forcing a Self Mode launch` ("the control working"). On this rig the POCO's
   Gearhead honoured the legacy `WirelessStartupReceiver` broadcast, connected back to the OHU
   `WirelessServer` on `127.0.0.1:5288`, and a loopback Self Mode session formed
   (`session state projecting` at `12:58:39`). This does **not** affect the R2 verdict — the two
   PASS conditions (`forcing a Self Mode launch` present, `leaving Self Mode alone` absent) are both
   met, and a Self Mode launch *succeeding* in a non-Native mode is correct behaviour, not a fault.
   `All launchers failed` count for R2 = 0.

4. **R3's session bring-up spanned two `createGroup SUCCESS!` events** (`13:00:32`, `13:01:33`) before
   the session formed at `13:01:44`. This is the rig's known slow-handshake group churn (poke
   connectivity was flaky — HFP-AG poke did not land until `13:01:35`, HSP-AG tried at `13:01:10`);
   `recoverNativeGroup` recreates the group on its 60 s cycle while the phone has not yet associated.
   Exactly **one** session formed (one `SSL handshake complete`, one `Incoming connection detected`),
   and R3 scores channel announcements on that session, not group count. `Magic Garbage` = 0.

### Scripts

No new scripts. Used `build_hur.sh`, `run_unit_tests.sh`, and `set_hu_settings_host.py`
(scalar + `set:` + `setclear:` edits, host-side, single force-stop, no relaunch — the only helper
that handles `<set>` keys). Round baseline `settings.xml` backed up to the host and to
`/data/local/tmp/settings-round-baseline.xml`, restored byte-identical at the end (verified
`diff` → exact match).

### Settings delta going in (vs. the thread's carried `settings.xml`)

The carried file already had `wifi-connection-mode=3`, `log-level=2`,
`auto-start-bt-macs={DC:B7:2E:5E:4E:59}`, `auto-start-bt-name=POCO X3 NFC`. This round added:
`self` to `connection-modes` (→ `{wifi, self}`), `enable-audio-sink=true`,
`native-poke-bt-macs={DC:B7:2E:5E:4E:59}`, `native-poke-all-paired=true`,
`allow-external-configuration=true`. All reverted at the end.

---

## R1 — a Bluetooth auto-start in Native mode leaves Self Mode alone (the point of the round)

**PASS**

- **Settings written:** `wifi-connection-mode=3`, `connection-modes={wifi,self}`,
  `enable-audio-sink=true`, `auto-start-bt-macs={DC:B7:2E:5E:4E:59}`,
  `native-poke-bt-macs={DC:B7:2E:5E:4E:59}`, `native-poke-all-paired=true`,
  `allow-external-configuration=true`, `log-level=2`.
- **Radio state:** phone Bluetooth + WiFi **off** at launch (`svc bluetooth disable` / `svc wifi
  disable` on the phone, verified `state: OFF` / `Wi-Fi is disabled`); head-unit app launched, group
  + RFCOMM listeners up with no phone reachable; then phone Bluetooth **on** (`svc bluetooth enable`)
  as the trigger.
- **Discard-rule check:** clean. One `p2p-wlan0-1` interface, one `createGroup SUCCESS!` (pre-marker),
  one `SSL handshake complete`, zero `Magic Garbage`, zero unintended `MATCH!`.
- **Decisive log lines** (capture `r1b.txt.gz`):

  ```
  12:55:57.347  NativeAA: ACTIVELY LISTENING on Android Auto UUID … Waiting for phone to connect back!
  12:55:58.065  WifiDirectManager: 5GHz createGroup SUCCESS!
  12:56:20.157  AutomationMarker: R1_trigger            ← state query here: connected=false, state=Disconnected
  12:56:30.793  AutoStartReceiver.onReceive | BT Device connected: POCO X3 NFC (DC:B7:2E:5E:4E:59)
  12:56:30.793  AutoStartReceiver.onReceive | MATCH! Starting AapService via Bluetooth Auto-start...
  12:56:30.892  MainActivity.handleLaunchIntent | MainActivity: Bluetooth auto-start: leaving Self Mode alone
  12:56:34.822  AapService: session state connecting
  12:56:34.835  AapService: session state connected
  12:56:35.850  AapService: session state projecting
  12:57:22.892  AutomationMarker: R1_end               ← state query: connected=true, wifiMode=NATIVE, TransportStarted
  ```

- **Counts after the `R1_trigger` marker:**

  | pattern | count |
  |---|---|
  | `MATCH! Starting AapService` (reachability gate) | 1 |
  | `Bluetooth auto-start: leaving Self Mode alone` | 1 |
  | `Bluetooth auto-start: forcing a Self Mode launch` | 0 |
  | `SelfMode: All launchers failed` | 0 |
  | `userExitedAA is true. Skipping auto-poke.` | 0 |
  | `session state disconnected (user_exit)` | 0 |
  | `createGroup SUCCESS!` | 0 |

The auto-start fired with the session not yet up (`connected=false` at the marker), the fix took the
decision (`leaving Self Mode alone`), and the Native session then came up on its own,
`connecting → connected → projecting`, with no Self Mode launch, no false user-exit, and no second
group. This is the field-ride F1/F2 failure path fully closed.

---

## R2 — the same trigger in Helper mode still launches Self Mode (positive control)

**PASS**

- **Settings written:** as R1 but `wifi-connection-mode=2`.
- **Radio state:** identical to R1 — phone radios off at launch, phone Bluetooth on as the trigger.
- **Discard-rule check:** clean (one session, one `MATCH!`).
- **Decisive log lines** (capture `r2.txt.gz`):

  ```
  12:58:33.984  AutomationMarker: R2_trigger           ← state query: connected=false, wifiMode=HELPER, Disconnected
  12:58:37.159  AutoStartReceiver.onReceive | MATCH! Starting AapService via Bluetooth Auto-start...
  12:58:37.216  AapService: Bluetooth auto-start: BtAutoStartActions(clearUserExit=true, forceRearmWireless=false, armWirelessIfIdle=false)
  12:58:37.249  MainActivity: Bluetooth auto-start: forcing a Self Mode launch
  12:58:37.494  SelfMode: AA < 17.4 detected. Starting WirelessServer on 5288 and running legacy triggers...
  12:58:37.534  SelfLauncherBroadcast.run | SelfMode: Broadcast fallback 1 (WirelessStartupReceiver) sent.
  12:58:38.640  AapService: session state connected
  12:58:39.679  AapService: session state projecting
  12:59:19.228  AutomationMarker: R2_end
  ```

- **Counts after the `R2_trigger` marker:** `forcing a Self Mode launch` = **1**,
  `leaving Self Mode alone` = **0**, `All launchers failed` = 0 (the launch succeeded, see Setup
  note 3), `session state disconnected (user_exit)` = 0.

`forceRearmWireless=false` in the `BtAutoStartActions` line confirms the Native-only rebuild is not
applied in Helper mode. R1 and R2 on the same APK give opposite decisions on the same trigger — the
fix narrowed the Self Mode veto to Native mode, it did not delete it.

---

## R3 — the media and speech sinks are announced on a wireless session

**PASS**

- **Settings written:** R1 set, `wifi-connection-mode=3`, `enable-audio-sink=true`.
- **Route to a session:** head-unit app launched with phone radios off; phone Bluetooth on; a Native
  AA session formed after the poke landed (HFP-AG poke succeeded `13:01:35`, phone associated,
  `Incoming connection detected from /192.168.49.54` at `13:01:44`).
- **Decisive log lines** (capture `r3.txt.gz`):

  ```
  13:01:44.077  WirelessServer: Incoming connection detected from /192.168.49.54
  13:01:44.303  AapSslContext.performHandshake | SSL handshake complete.
  13:01:45.058  AapService: session state projecting
  13:01:45.308  Media Sink Setup Request: 7 on channel VIDEO      → Config response: status: HEADUNIT
  13:01:45.395  Media Sink Setup Request: 1 on channel AUDIO2     → Config response: status: HEADUNIT
  13:01:45.399  Media Sink Setup Request: 1 on channel AUDIO1     → Config response: status: HEADUNIT
  13:01:45.404  Media Sink Setup Request: 1 on channel AUDIO      → Config response: status: HEADUNIT
  ```

- **Channels announced:** `AUDIO2` (system) **and** `AUDIO1` (speech) **and** `AUDIO` (media) — all
  three, each answered `status: HEADUNIT`. Before the fix only `AUDIO2` appeared.
- **Skip-reason check:** `Self Mode is projecting this device to itself` = 0,
  `Audio sink is off in Settings` = 0. Neither fired, so the announcement was not short-circuited for
  a stated reason — the fix's session-endpoint gate was exercised and let all three through.

---

## R4 — clearing the Bluetooth auto-start entry sticks

**PASS**

- **Gate:** `shared_prefs` and `shared_prefs/settings.xml` both `u0_a168:u0_a168` — app-writable.
- **Settings written:** R1 set, but `auto-start-bt-macs` and `native-poke-bt-macs` both written as
  **empty sets** (`setclear:`). `native-poke-all-paired=true` kept.
- **Radio state:** head-unit app up, phone Bluetooth on; a full handshake + session completed
  (`Incoming connection detected` `13:03:30`, `session state projecting` `13:03:31`).
- **Decisive log lines** (capture `r4.txt.gz`):

  ```
  13:03:11.561  NativeAA: No wake poke device selected, and poking all paired devices is on. Poking all of them...
  13:03:26.837  NativeAA: Saving DC:B7:2E:5E:4E:59 (POCO X3 NFC) as the wake poke device.
  13:03:30.385  WirelessServer: Incoming connection detected from /192.168.49.54
  13:04:59.386  AutomationMarker: R4_handshake_done      → am force-stop → run-as cat settings.xml
  ```

- **`settings.xml` readback** (app force-stopped, via `run-as cat`, before the round-end restore):

  ```
  <set name="auto-start-bt-macs" />
  …
  <set name="native-poke-bt-macs">
      <string>DC:B7:2E:5E:4E:59</string>
  </set>
  ```

`native-poke-bt-macs` gained the phone's address (positive control — same file, same code path, so
the write mechanism works); `auto-start-bt-macs` stayed empty. The completed handshake writes the
poke target and only the poke target. The evidence file `r4-poke-target.txt` had a post-restore
readback accidentally appended in an earlier revision; corrected there, and the verbatim
force-stopped readback is quoted above.

---

## R5 — port 5299 still binds every bring-up (regression guard only)

**PASS** (as a guard; the original `EADDRINUSE` cannot be reproduced on this rig — nothing dials
5299 here)

- **Settings written:** R1 set (`native-poke*` still `{DC:B7:2E:5E:4E:59}` from R4's end — not
  load-bearing for a transport bind).
- **Procedure:** launch, then `am start -a …ACTION_STOP_SERVICE` / wait 6 s / `am start
  MainActivity`, ×3, each cycle marked. Process pid stayed **22025** across all four bring-ups, so
  this is an in-process re-bind — exactly where a leaked socket would throw.
- **Decisive log lines** (capture `r5.txt.gz`):

  ```
  13:05:25.225  WppTcpServer: listening for Android Auto on TCP 5299    (cycle 0, launch)
  13:05:43.001  WppTcpServer: listening for Android Auto on TCP 5299    (cycle 1)
  13:06:01.535  WppTcpServer: listening for Android Auto on TCP 5299    (cycle 2)
  13:06:19.991  WppTcpServer: listening for Android Auto on TCP 5299    (cycle 3)
  ```

- **Counts (whole capture):** `listening … TCP 5299` = 4, `could not listen on 5299` = 0,
  `EADDRINUSE` = 0. `WirelessServer: binding port 5288` = 4 (also clean). One bind per bring-up,
  every bring-up.

---

## Anything the brief did not ask about

- **R2's Self Mode launcher logged `Installed AA version: 17.3.662864-release`.** That is the
  **MT50 headunit's own bundled `gearhead:car`**, which `SelfLauncherManager` reads because it runs
  on the headunit device — not the POCO phone's Gearhead (`17.5.663204`). The `AA < 17.4` legacy
  Self Mode branch was chosen from the bundled version. (An earlier revision of this file
  mis-attributed the 17.3 to the POCO; corrected here and in the header.)
- **`AutoStartReceiver`'s "already connected" guard is a `AppLog.d`.** At `log-level=2` (INFO) the
  line `AutoStartReceiver: Already connected to Android Auto. Ignoring BT event.` is not emitted, so
  the first R1 attempt's INCONCLUSIVE cause was only visible by *absence* of `MATCH!` plus the
  pre-trigger state query. A brief that expects this guard to be observable needs `log-level=1`
  (DEBUG) or a promotion of that line to INFO.
- **R3's slow, churny bring-up** (two groups, ~72 s to a session, poke flaky) is the rig, not the
  branch — `NativeAA: Handoff still settling — not starting a poke that would compete with the
  phone's WiFi association.` fired at `13:01:43`, which is the branch-1 poke-suppression fix doing
  its job during exactly that window.
- **`AutoStartReceiver` fired mid-bring-up in R3 too** (`13:00:50`, before the session, with
  `isConnected=false`) and logged `leaving Self Mode alone` — a third independent confirmation of
  R1's fix on top of R1b's.
