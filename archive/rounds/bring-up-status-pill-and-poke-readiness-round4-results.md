# bring-up-status-pill-and-poke-readiness — round 4 results

**Candidate:** `fork/feat/bring-up-status-pill-and-poke-readiness` @ `7f439d4e`       **Baseline:** none (brief §1, no `main` behaviour to compare)
**APK md5:** `045ab73220afb7630ffe3c2879ce0dc6` (`com.andrerinas.headunitrevived_3.4.0-beta1_debug.apk`, versionCode 106), identical on D-HU and D-POCO
**Unit:** D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14, single BT radio, adbd root), bonded name "Navegadortz2" `11:46:03:10:33:59`. D-POCO = POCO X3 NFC (`M2007J20CG`, Android 11, not rooted) `DC:B7:2E:5E:4E:59`, phone role for Part A/D2/D3, head unit role for Part B (W2) and D1. D-MOTO = motorola edge 30 neo (`miami`, not rooted) `A0:46:5A:97:E4:95`, phone role for W2/D1.
**Date:** 2026-09-11

## Setup notes

### Scripts used (`hur-wifi-test-scripts/`, a sibling dir, not this repo)
`build_hur.sh`, `run_unit_tests.sh` (R0), `install_and_launch.sh` (both units), `set_hu_settings_host.py` (D-HU, rooted), `set_hu_settings_runas.py` (D-POCO, run-as). No new script needed; a small inline wait-and-toggle shell script was written ad hoc for D3's scripted +3s toggle (not saved to the scripts folder, it is a five-line wrapper around the brief's own two commands, not a reusable tool).

### Deviations from protocol

1. **Both units had formed their own stray P2P group before this round's first capture**, left over from `install_and_launch.sh` launching each app with `wifi-connection-mode=3` already set from a previous thread. Cleared before R0 by launching each app once and sending `headunit://exit`, confirmed with `dumpsys wifip2p` (`groupFormed: false` on both, no `p2p0` interface) — this is the read §2 asks every role swap to carry, done once at the top of the round as well.
2. **`headunit://disconnect` does not reopen `NativeAA: ACTIVELY LISTENING` on its own.** WB1's setup line ("if one formed, send `headunit://disconnect` and wait for `ACTIVELY LISTENING`") assumes the listeners come back up unattended. Measured: after `headunit://disconnect`, `WifiDirectManager: Stopping and cleaning up...` runs and `AapService: User exit with wirelessServer active. Not restarting discovery.` fires; `ACTIVELY LISTENING` did not reappear in a continuous 173s watch. This matches `TESTING-TEMPLATE.md`'s own documented limit for mode 3 ("no deep link re-arms a session after user exit"). What actually re-arms it is the WiFi button's own `ACTION_NATIVE_AA_POKE` path (`AapService.kt`'s `wifiLauncherManager.setActiveFromSettings(force = true)` branch when the mode isn't active) — the tap itself does the re-arm, so WB1/WB1b were run by disconnecting, confirming the idle/no-group state directly (`dumpsys wifip2p` → `groupFormed: false`), and tapping from there rather than waiting on a line that this build does not print.
3. **The `NativeDriverSelectionPolicy.Mode.AUTO` unambiguous-driver path auto-connects on every fresh launch**, independent of the WiFi button (`HomeFragment.checkNativeDriverSelectionOnStartup`). This meant catching a genuinely "fresh launch, no session, phone reachable" state for WB1 needed the phone's BT profile link to D-HU to be live at the *moment of the tap*, not merely "radios on": `NativeDriverSelectionPolicy.connectUiIsImmediate` reads `BluetoothHelper.driverCandidates(...).connectedOffered`, which is empty once the profile link has gone idle (a few minutes with no session, as it does on this rig — see `TESTING-TEMPLATE.md` §7a's A2DP/HFP flakiness). First WB1 attempt landed with `btConnected=false` despite "radios on" and produced WB1b's own screenshot text ("...is disconnected, waking it..."); the run was discarded and redone after re-establishing the profile link (toggle D-HU's own adapter, self-reverts ~14s, grabs D-POCO's `A2dpSinkService`/`HeadsetClientService`), which produced a clean `btConnected=true` run. **The first (discarded) attempt also silently formed a session that was still live when the retry began** — the retry's screenshot briefly showed live Android Auto Maps until the stray session was found (`ACTION_QUERY_STATE` → `connected:true`) and disconnected. Both stray/discarded captures are not kept; only the clean re-runs are in `evidence/`.
4. **§4's line `NativeAA: Attempting active poke to device: ` no longer appears in this build.** W2's automatic retry loop logs `NativeAA: Attempting manual poke to <name>...` — the same wording the manual-poke path always used — for both automatic and manual invocations, consistent with `fb9ac9391`'s change unifying the two ("the manual poke ... now reports `WAKING_PHONE` ... as the automatic loop does"). The brief's §4 table still carries round 1's original string; W2's condition 3 was graded against the line this build actually prints, functionally equivalent (poke attempts for D-MOTO, zero for D-HU/Navegadortz2, confirmed by grep).
5. **D-POCO's own radios were left off from an earlier Part A run (P3/P4) when Part B's setup began**, producing `NativeAA: Bluetooth adapter not available or disabled` and `WifiDirectManager: WiFi is disabled but needed for Native AA` on the first W2 attempt. Caught immediately from the capture (no `ACTIVELY LISTENING`, no `createGroup SUCCESS` after 90s+), radios restored (`svc wifi enable`, `svc bluetooth enable`, confirmed via `dumpsys`), and W2 re-run cleanly from a fresh capture. This is distinct from "D-MOTO Bluetooth off at launch" (the phone's radio, which W2 wants off) — D-POCO's *own* radios must stay on when it is the head unit.
6. **D2's toggle needed three attempts.** `svc bluetooth disable` on D-POCO self-reverted within the auto-disconnect's 5000ms cancel window twice in a row (`is back; the pending disconnect is cancelled` at +3.472s and +4.292s after each `went away`) — faster than any previously recorded self-revert on this phone (previously ~4s and ~45s in other threads, never inside the cancel window itself). `cmd connectivity airplane-mode enable` alone (the brief's own prescribed D2 lever, "`svc bluetooth disable` self-reverts on D-POCO") also did not visibly drop either radio after ~19s of observation despite `airplane_mode_on=1` reading back correctly — a new finding, since §7a documents this same command working reliably on this phone in earlier rounds. What finally held was airplane mode **plus** an explicit `svc bluetooth disable` + `svc wifi disable` issued together; that combination is what produced the graded PASS. All three attempts are on the record in `evidence/d2-dhu.txt.gz` (kept as one continuous capture, since it is the same live session throughout — no group churn, one `createGroup SUCCESS`, one `SSL handshake complete`).
7. **FX Plus's "powered" precondition (brief §Part B) could not be independently verified.** It is a physical Bluetooth intercom with no software power state visible over adb; its `dumpsys bluetooth_manager` entry (bonded, class `0x001F00`) was confirmed present and the driver-candidates roll-up picked it up as expected (counted under `unknown`), which is the strongest evidence available that it was in range and responsive to inquiry, but "powered" itself is not a claim this round can make from adb alone.
8. Settings backups (`dhu-settings-backup.xml`, `dpoco-settings-backup.xml`) were taken before any write and restored at the end of the round on both units; both radio states and D-MOTO's Bluetooth were restored (all `enabled: true` / `Wi-Fi is enabled`, `airplane_mode_on=0` on D-POCO). `native-preferred-device-mac` was deleted (not merely blanked) on both head units for the whole round, confirmed absent in every settings read-back.

## R0 — build and unit-test gate

**PASS**

- Build: `build_hur.sh` → `BUILD SUCCESSFUL`, apk md5 `045ab73220afb7630ffe3c2879ce0dc6`.
- Unit tests: `run_unit_tests.sh` → `BUILD SUCCESSFUL`; test-result XMLs sum to **1628 tests, 0 failures, 0 errors, 0 skipped**, matching the brief's gate exactly.
- Install: `adb install -r` on both D-HU and D-POCO; live `md5sum` of each `pm path` base.apk matches the built apk and each other.
- Identity: `ACTION_QUERY_STATE` → `"commit":"7f439d4e4ab9"` on both units.

---

## Part A — status pill, D-HU head unit, D-POCO phone

### WB1 — the WiFi button shows the pill

**PASS**

- Settings written: as brief §3 (`wifi-connection-mode=3`, `log-level=2`, `native-poke-all-paired=true`, `native-preferred-device-mac` deleted).
- Radio state: D-POCO both radios on, bonded, BT profile link to D-HU freshly re-established (`A2dpSinkService` Active Device set, `HeadsetClientStateMachine` Connected) immediately before the tap — see Setup note 3.
- Discard-rule check: clean (`MATCH!`=0, `createGroup SUCCESS`=1, `Magic Garbage`=0, one `SSL handshake complete`).
- Decisive log lines, quoted with timestamps:
  - `00:16:57.108 HomeFragment: Connecting to Native-AA device: POCO X3 NFC (DC:B7:2E:5E:4E:59), btConnected=true`
  - `00:16:57.110 Auto-connect: begin (Native-AA driver: POCO X3 NFC, mode=PILL_THEN_OVERLAY)`
  - `00:16:59.144 status pill step: WAKING_PHONE`, `00:16:59.984 status pill step: WAKING_PHONE` (one intervening retreat to `WAITING_FOR_PHONE` at `00:16:59.355`, the documented allowed rank decrease), `00:17:01.146 status pill step: PHONE_ANSWERED` — all between `begin` and `CONNECTING` (`00:17:07.718`). Round 3's `wb1-dhu.txt` had zero such lines in the same window.
  - `00:17:07.724 Auto-connect: a phone is answering, taking the full screen.` then `00:17:07.799 status pill step: hidden` — `hidden` after, not before.
  - `00:17:07.873 SSL handshake complete.`
- Screenshot (`evidence/bring-up-status-pill-and-poke-readiness-round4/wb1-screenshot.png`), taken 0.68s after the tap: pill reads **"Starting Android Auto with POCO X3 NFC"** as its first line, no toast anywhere on screen.

### WB1b — the same with the phone away

**PASS**

- Settings: as WB1. Radio state: D-POCO Bluetooth off (`state: OFF`, confirmed), on again 20s after the tap.
- Discard-rule check: clean (`MATCH!`=0, `createGroup SUCCESS`=1, `Magic Garbage`=0, one `SSL handshake complete`).
- Decisive log lines, quoted with timestamps:
  - `00:18:20.675 HomeFragment: Connecting to Native-AA device: POCO X3 NFC (DC:B7:2E:5E:4E:59), btConnected=false`
  - `00:18:20.677 Auto-connect: begin (... mode=PILL_THEN_OVERLAY)`
  - `00:18:21.911 status pill step: WAKING_PHONE`, `00:18:21.925 NativeAA: Attempting manual poke to POCO X3 NFC...`
  - `00:18:52.829 NativeAA: Manual poke to POCO X3 NFC finished.` → `00:18:52.832 status pill step: WAITING_FOR_PHONE` (the phone had not yet answered at that instant, even though the BT radio-enable command had just been issued — the pill correctly read `WAITING_FOR_PHONE`, not `PHONE_ANSWERED`, at that boundary).
  - `00:18:56.560 status pill step: PHONE_ANSWERED` — the automatic loop's next round.
- Screenshot (`wb1b-screenshot.png`), 0.69s after the tap: pill's first line reads **"POCO X3 NFC is disconnected, waking it..."**, no toast.
- Corroboration (not a condition): `SSL handshake complete` at `00:19:41.597`, ~81s after the tap, well inside the 90s window.

### P1 — Native AA cold bring-up to projection

**PASS**

- Settings/radio: clean-run protocol exactly — D-POCO airplane mode on at capture start, D-HU force-stopped/cleared/launched, D-POCO radios restored 18s after launch (both confirmed on).
- Discard-rule check: `MATCH!`=0, `Magic Garbage`=0, one `SSL handshake complete`. **`createGroup SUCCESS`=2** — the second is the same-identity re-arm ("asking for the kept network DIRECT-TX-Navegadortz2 again") that `WifiDirectManager.createQuietGroup` runs as part of the poke-retry cycle between rounds, not a second session; see the ordered list below for the surrounding pill transitions. Reported per the general template rule but not treated as contamination, consistent with this round's own "the poke loop never gives up" design.
- **Ordered `status pill step:` list, with timestamps:**

  | # | time | value |
  |---|---|---|
  | 1 | 00:20:42.207 | ARMED |
  | 2 | 00:20:42.271 | PREPARING_NETWORK |
  | 3 | 00:20:42.539 | WAITING_FOR_PHONE |
  | — | 00:20:42.548 | `startNativeAaQuietHost() requested` |
  | 4 | 00:20:42.549 | PREPARING_NETWORK *(rank decrease, immediately after the line above — allowed)* |
  | 5 | 00:20:43.140 | CREATING_NETWORK |
  | 6 | 00:20:43.403 | WAKING_PHONE |
  | 7 | 00:21:28.583 | WAITING_FOR_PHONE *(rank decrease, WAKING_PHONE→WAITING_FOR_PHONE — allowed; count = 1)* |
  | 8 | 00:21:44.115 | WAKING_PHONE |
  | 9 | 00:21:45.181 | PHONE_ANSWERED |
  | 10 | 00:21:45.660 | SENDING_CREDENTIALS |
  | 11 | 00:21:46.674 | PHONE_JOINING |
  | 12 | 00:21:51.341 | CONNECTING |
  | 13 | 00:21:51.457 | SECURING |
  | 14 | 00:21:51.575 | STARTING_PROJECTION |

  14 `status pill step:` lines (≥6 required). First value ARMED. `WAITING_FOR_PHONE` before `WAKING_PHONE` (line 3 before line 6). Last value STARTING_PROJECTION. **One** WAKING_PHONE→WAITING_FOR_PHONE decrease (line 6→7), matching round 3's own "either 0 or 1 is PASS" precedent. No other rank decrease. `SSL handshake complete` at `00:21:51.569`.
  Wall clock, first `status pill step:` to `STARTING_PROJECTION`: **69.37s**.

### P2 — the pill does not cover the home controls

**PASS**

- App launched, no phone reachable (D-POCO radios off), pill at ARMED.
- Pill bounds: **`[544,591][896,696]`** (352×105 px) — matches round 3's measured 352×105 exactly.
- Other clickable nodes: `self_mode_button [84,220][312,448]`, `usb_button [432,220][660,448]`, `wifi_button [780,220][1008,448]`, `settings_button [1128,220][1356,448]`, `exit_button [1284,624][1416,696]`. None intersects the pill's rectangle.
- No non-default UI scale: `dpi-pixel-density=218` (unchanged default); the app forces landscape (`screen-orientation=2`), so the 1440×720 coordinate space is orientation, not a density override.

### P3 — an unanswered wake keeps the pill honest

**PASS**

- Clean-run protocol, D-POCO radios held off for the full 120s.
- Discard-rule check: `MATCH!`=0, `createGroup SUCCESS`=1, `Magic Garbage`=0, `SSL handshake complete`=0 (expected, phone never answered).
- `status pill step:` appeared **10 times**. `WAITING_FOR_PHONE` reached (first at `00:24:32.059`). **Three** WAKING_PHONE→WAITING_FOR_PHONE transitions after the first WAKING_PHONE:
  - `00:24:30.109` WAKING_PHONE → `00:25:18.785` WAITING_FOR_PHONE (transition 1, ~48.7s)
  - `00:25:33.806` WAKING_PHONE → `00:26:04.632` WAITING_FOR_PHONE (transition 2, ~30.8s)
  - `00:26:19.654` WAKING_PHONE → `00:26:50.473` WAITING_FOR_PHONE (transition 3, ~30.8s)

  (≥2 required.) `status pill step: hidden` — **zero** times.

### P4 — a different mode produces a different sequence

**PASS**

- `wifi-connection-mode=1`, otherwise as P1.
- `WifiLauncher: Initializing WiFi Mode: AUTO` → `status pill step: ARMED` → `status pill step: SEARCHING` (2 lines, ≥2 required). `WAKING_PHONE` and `SENDING_CREDENTIALS` appear **zero** times in the capture. Confirms P1's sequence is not what mode 1 shows.

### P5 — the pill renders three lines

Screenshot (`p5-pill.png`) taken during `WAKING_PHONE`. All three lines render cleanly, none truncated or overlapping:
1. **"Android Auto is starting..."**
2. **"Waking your phone"**
3. **"WiFi Direct on 5 GHz (5785 MHz)"**

The third line (the round's own new addition, absorbing the old 5GHz toast) is present and legible.

### P6 — USB

**INCONCLUSIVE** (expected). `dumpsys usb` confirms `host_connected=false` on D-HU — no USB host path exists on this rig, per `TESTING-TEMPLATE.md` §7a.

---

## Part B — W2, D-POCO head unit, D-MOTO phone

**PASS**

- Settings written on D-POCO: `wifi-connection-mode=3`, `log-level=2`, `auto-start-bt-macs` empty, `native-poke-bt-macs=11:46:03:10:33:59` (D-HU's MAC, the seeded car-kit value), `native-poke-all-paired=true`, `native-preferred-device-mac` deleted. D-HU's app force-stopped throughout.
- Radio state: D-MOTO Bluetooth off at launch, on 8s after `createGroup SUCCESS`; D-POCO's own radios confirmed on (see Setup note 5 for the discarded first attempt). FX Plus (`D0:D9:4F:C2:C7:1E`) bonded to D-POCO and present in the driver-candidates roll-up (see Setup note 7 on "powered").
- Discard-rule check: clean (`MATCH!`=0, `createGroup SUCCESS`=1, `Magic Garbage`=0, one `SSL handshake complete`).
- **`driver candidates:` line, verbatim:**
  ```
  BluetoothHelper: driver candidates: 1 phone, 1 unknown, 3 not a phone - hidden: Navegadortz2 (gateway but class not phone), FX Plus (gateway but class uncategorized), BC8-Android (hands free unit), Magnetic Speaker (hands free unit)
  ```
  D-HU (Navegadortz2) is hidden as "gateway but class not phone" — never counted as a phone. FX Plus is counted under the **1 unknown**, hidden with reason **"gateway but class uncategorized"**, exactly `GATEWAY_BUT_CLASS_UNCATEGORIZED`.
- `chosen for Auto Start but not a phone` (naming Navegadortz2) at `00:34:23.548`, before any poke. `No wake poke phone selected, and poking all paired devices is on` at `00:34:23.549`.
- Poke attempts: this build's automatic retry loop logs `NativeAA: Attempting manual poke to motorola edge 30 neo...` (three rounds, `00:34:24.764` / `00:34:50.094` / `00:35:15.416` — see Setup note 4 on the renamed line) then `Successfully poked motorola edge 30 neo via HFP-AG` at `00:35:16.574`. **Zero** occurrences of any poke line naming Navegadortz2.
- **Zero** occurrences of `FX Plus` in any poke/manual-poke/`Unambiguous driver` line.
- `NativeAA: Saving A0:46:5A:97:E4:95 (motorola edge 30 neo) as the wake poke device.` at `00:35:22.028` (this landed ~9.8s *before* `SSL handshake complete` at `00:35:31.791`, not strictly after it as the brief's wording implies — the setting is written once the phone is confirmed joining, not gated on full handshake completion). `native-poke-bt-macs` read back from `settings.xml` after the run: **`A0:46:5A:97:E4:95` alone**.

---

## Part C — D1, D2, D3

### D1 — the lag this round sets the constant from (D-POCO head unit, D-MOTO phone)

**PASS**

Continued directly from the W2 session (touched nothing for 92s after `SSL handshake complete`).

- `stayed away; ending the session` — **zero** occurrences.
- One `went away`/`not ending` pair: `00:35:31.097 BT Handshake socket closed.` → `00:35:35.283 went away; ending the session in 5000ms` (gap **4.186s**) → `00:35:40.286 not ending the session for A0:46:5A:97:E4:95 (up=true, ownCloseMs=9189)`.
- Largest gap measured: **4.186s**. `ownCloseMs=9189`, below 15000.

D-POCO's session ended with `headunit://exit`; `dumpsys wifip2p` confirmed `groupFormed: false` before the role swap back to phone.

### D2 — a real disconnect at 20s ends the session (D-HU head unit, D-POCO phone)

**PASS**

- `auto-disconnect-bt-macs=DC:B7:2E:5E:4E:59` on D-HU, delay 5. Precondition: D-POCO's profiles re-established on D-HU before launch (`HeadsetClientStateMachine` Connected, `A2dpSinkService` Active Device set via D-HU's own adapter toggle), confirmed again ~10s after `SSL handshake complete` — still `Connected` / Active Device set.
- **Three toggle attempts were needed** (Setup note 6): two bare `svc bluetooth disable` self-reverts inside the 5000ms cancel window (`is back; the pending disconnect is cancelled` at +3.472s and +4.292s after each `went away`), then `cmd connectivity airplane-mode enable` + explicit `svc bluetooth disable`/`svc wifi disable` together, which held.
- Graded sequence: `00:43:14.843 went away; ending the session in 5000ms` → `00:43:19.845 stayed away; ending the session the way the Exit button does` (**5.002s** later, within the brief's "about 5s") → `00:43:19.866 session state disconnected (user_exit)`. No reconnect.
- Wall clock, toggle-hold to `stayed away`: **5.002s** from `went away`.

### D3 — known limit, a disconnect at 3s

Reported as the documented known limit, not a FAIL, per the brief.

- Toggle scripted per §2: a five-line wrapper tailed the fresh D-HU capture for `SSL handshake complete`, slept exactly 3.004s (script-controlled), then fired `cmd connectivity airplane-mode enable` on D-POCO.
- `SSL handshake complete` at `00:45:13.643` (device time). Script's own toggle-issue timestamp landed **3.004s** after the script detected the SSL line (host-side wall clock), with ≤0.3s additional polling latency before detection — measured offset from the SSL line to the toggle command's own timestamp: **≈3.0–3.3s**, against round 3's ~8s.
- `00:45:18.234 went away; ending the session in 5000ms` → `00:45:23.237 not ending the session for DC:B7:2E:5E:4E:59 (up=true, ownCloseMs=9613)` — the number this round exists to measure at the true +3s boundary.
- The session **survived** the BT auto-disconnect grace (no `stayed away` from that path), then ended separately via `00:45:31.667 session state disconnected (link_lost)` — WiFi's own link loss (airplane mode also drops WiFi), ~8.4s after the `ownCloseMs` line. This is exactly the documented known-limit shape: the grace correctly refuses to end the session on our own handshake-adjacent close, and a different mechanism (the WiFi link itself) ends it once airplane mode fully lands.

---

## Anything the brief did not ask about

- The self-revert behaviour of D-POCO's own Bluetooth adapter (Setup note 6) is worth a standing note for future rounds: `svc bluetooth disable` self-reverted **inside the 5000ms auto-disconnect cancel window itself**, twice in a row, which is a materially different (faster) failure mode than the ~4s/~45s self-reverts recorded elsewhere on this branch. Any future round whose verdict depends on a single bare `svc bluetooth disable` holding for 5+ seconds should budget for this.
- `cmd connectivity airplane-mode enable` alone, on its own, did **not** visibly drop D-POCO's radios during the D2 window (~19s of continuous observation) despite the `airplane_mode_on` global setting reading back correctly — the first time this specific command has been seen to not work on this phone in this channel's record. It is unclear whether this is state-dependent (e.g. an existing Native AA session's WiFi lock holding the radio) or a genuine regression in the command's reliability; worth re-checking cold (no active session) in a future round rather than assuming either explanation.
