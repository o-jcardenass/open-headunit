# driver-selection-native — round 4 results

**Candidate:** `fork/testing/driver-selection-plus-automation` @ `8bf8340d` (stamp `8bf8340d0c82`)
**Baseline:** none this round (see brief §1)
**APK md5:** `a899924b1624166506420983fe2785d6`
**Unit:** D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14. Driver phones: D-POCO (POCO X3 NFC,
`DC:B7:2E:5E:4E:59`) and D-MOTO (motorola edge 30 neo, `A0:46:5A:97:E4:95`), both bonded throughout.
**Date:** 2026-09-05

## Setup notes

- **R0 PASS**: build printed `Building from commit: 8bf8340d0c82`, no `-dirty`; `ACTION_QUERY_STATE`
  echoed the same commit; unit gate **1333 / 0**, exactly as the brief states.
- **Brief erratum, blocks R5b as scripted**: `ACTION_NATIVE_AA_CANCEL_POKE` does not exist as an
  automation-broadcastable action in this candidate. §3's table documents it ("The Cancel the
  selector's Back press sends. R5b uses it directly.") but neither
  `contract/.../HeadUnitIntent.kt` nor `AutomationCommandPolicy.kt` defines or handles it — sending
  the broadcast returns `{"error":"unknown action com.andrerinas.openheadunit.ACTION_NATIVE_AA_CANCEL_POKE","ok":false}`.
  The *code* path it names (`NativeAaHandshakeManager.cancelPoke()`, reached from
  `AapService.ACTION_NATIVE_AA_CANCEL_POKE`) is real and was exercised via the UI's actual Back-press
  in R4, where it worked correctly. R5b could not be run as designed; see its section.
- `pkill -f "logcat.*<serial>"` matches this session's own shell wrapper on this box and kills the
  wrong thing (exit 144, capture process left running) — killed captures by exact PID instead for the
  rest of the round.
- `cp <backup> shared_prefs/settings.xml` inline under `run-as ... sh -c '...'` failed with
  `cp: Needs 1 argument` exactly as flagged in project notes; switched to pushing a one-line `cp`
  script and running that, which restored both `settings.xml` and the device-protected mirror
  byte-identical (diffed against the pre-round backups).
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `set_hu_settings_host.py` (rooted D-HU, scalar +
  `set`/`setclear` edits) for every settings write this round. No new script needed; the restore step
  used a one-off inline script (see above), not worth adding permanently.
- Settings backed up before the first touch: `shared_prefs/settings.xml` and
  `/data/user_de/0/<pkg>/shared_prefs/settings_device_protected.xml`, both restored byte-identical at
  the end (diffed, `IDENTICAL`).

## R0 — build gate

**PASS**

- Build: `Building from commit: 8bf8340d0c82`, no `-dirty`.
- `ACTION_QUERY_STATE` reply: `"commit":"8bf8340d0c82"`.
- Unit gate: **1333 / 0**.
- APK md5: `a899924b1624166506420983fe2785d6`.
- No install blocker (rig already carried a debug build).

## R1 — headless bring-up. Regression guard.

**PASS**

- Settings: `wifi-connection-mode=3`, `native-ap-transport=0`, `native-driver-selection-mode=1`,
  `native-driver-selection-timeout=10`, `native-preferred-device-mac=""`,
  `last-connected-native-mac=""`, `native-poke-bt-macs=<empty set>`, `auto-start-bt-macs=<empty set>`
  (both files), `kill-on-disconnect=false`, `log-level=2`. All read back empty/matching before the run.
- Radio state: both phones' Bluetooth and WiFi off (`svc bluetooth/wifi disable`, confirmed
  `bluetooth_on=0` on both).
- MainActivity never launched; only `ACTION_LOG_MARKER` then `ACTION_START_WIRELESS` via the
  automation receiver.
- Decisive lines: `5GHz createGroup SUCCESS!` at 11:03:15.146; five
  `Attempting active poke to device` lines at 11:03:17.371 (moto), 11:03:48.254 (poco), 11:04:18.083
  (moto), 11:04:49.928 (poco), 11:05:18.398 (moto); zero `Multi-driver selection is active` lines;
  zero deferral lines.
- Measurements: **5 pokes** in the 120 s window; **2.225 s** from `createGroup SUCCESS` to the first
  poke (round 3: 2.199 s, round 2: 2.20 s, `main`: 2.264 s — consistent).

## R2 — the deadline still reads the timeout setting. Regression guard, one sub-run.

**PASS**

- Settings: as R1, `native-driver-selection-timeout=10`, `native-poke-bt-macs` cleared and read back
  empty immediately before the run.
- MainActivity launched normally; dialog appeared; untouched.
- Decisive lines: `ACTION_NATIVE_AA_PROMPT_SHOWN received` at 11:06:10.126; `Multi-driver selection is
  active and awaiting user choice` ×1 at 11:06:12.852; `the driver prompt went unanswered` at
  11:06:35.891; `Attempting active poke to device: motorola edge 30 neo` at 11:06:35.903.
- Measurements: deadline **25.765 s** (round 3: 25.773 s — within the "couple of seconds" bar, not
  round 2's 60 s failure signature); **1** `Multi-driver selection is active` line (round 3: 1);
  **0.012 s** from `went unanswered` to the first poke (round 3: 0.012 s — exact match).

## R4 — cancel stops the poke without deafening the unit. Regression guard, and directly touched.

**INCONCLUSIVE** (pre-registered outcome; see brief §6's own contingency for this run)

- Settings: as R2, timeout 10, plus D-MOTO's MAC in `auto-start-bt-macs` (settings.xml only — the
  device-protected mirror resynced to it automatically on the next app launch, confirmed by reading
  both files back after launch).
- MainActivity launched, prompt appeared, then:
  - `ACTION_LOG_MARKER R4-cancel` at 11:07:39.519, `input keyevent KEYCODE_BACK` immediately after.
  - `NativeAA: cancelPoke() called — user explicitly canceled driver selection.` at 11:07:39.649
    (130 ms after the marker). **Half the PASS bar holds cleanly**: zero `Attempting manual poke to`
    or `Attempting active poke to device` lines anywhere in the capture after this point (checked the
    full ~5-minute capture, not just the first 35 s).
  - Waited 35 s (app left running), then cycled D-MOTO's Bluetooth off→on (2 s gap). No
    `BT Device connected` line ever appeared for D-MOTO afterward.
  - The P2P group recreated on its own ~60 s cadence four times (11:07:30, 11:08:31, 11:09:31,
    11:10:32, 11:11:32) — each delivery correctly gated by
    `AapService: userExitedAA is true. Skipping auto-poke.` (12 occurrences total, 3 per delivery) —
    then gave up: `WifiDirectManager: Native AA — phone still not connected after 4 recreations (no
    phone joined within 60s); giving up until the next start.` at 11:12:32.737.
  - Re-tried the same lever independently ~40 minutes later (D-MOTO's Bluetooth confirmed back on,
    `bluetooth_on=1`; disable→3 s→enable) and watched for another 13 minutes: still zero
    `BT Device connected` for D-MOTO. The head unit's own Bluetooth adapter was confirmed stable and
    enabled throughout both attempts (`state: ON`, `time since enabled: 02:24:52`, no self-revert),
    and `dumpsys bluetooth_manager` shows **no `StateMachine` at all** for D-MOTO's MAC on the head
    unit side — the phone's ACL genuinely never re-established, on either attempt.
- **Neither accept-path lever (`the cancelled prompt has expired` or `a phone arrived over
  Bluetooth`) got a chance to fire**, because the precondition for both — D-MOTO reconnecting over
  Bluetooth — never happened, across two independent cycles spanning roughly 55 minutes. This matches
  the documented rig quirk that Bluetooth/A2DP connectivity on this unit "can drop and never reconnect
  for an entire round," not a defect in this round's fix. Per the brief's own pre-registration for
  this exact case: recorded INCONCLUSIVE, with both levers tried and both levers' failure evidenced.
- Also worth flagging for anyone relying on this path: `ACTION_NATIVE_AA_CANCEL_POKE`'s handler
  (pre-existing code, not part of this round's diff) unconditionally sets `userExitedAA = true`,
  which is what gates every credential-triggered auto-poke until something explicitly clears it
  (a genuine new `WirelessServer` connection, or `ACTION_BT_AUTO_START`'s `clearUserExit`). R4's PASS
  therefore depends entirely on one of those two clears actually firing — it is not simply "cancel
  stops the poke, anything else resumes it."

## R5 — Switch Phone reaches the phone the driver chose. The point of the round.

**PASS**, all five criteria, and independently confirmed a second time (see "second instance" below,
under R5b).

- Settings: `native-preferred-device-mac=A0:46:5A:97:E4:95` (D-MOTO), timeout 10,
  `kill-on-disconnect=false`, both phones' radios on, `native-poke-bt-macs` cleared and read back
  empty.
- Launch → countdown resolved untouched: `NativeAA: Driver selected: A0:46:5A:97:E4:95` at
  12:05:06.907. First session: `Connection accepted from motorola edge 30 neo` 12:05:14.625,
  `WirelessServer: Incoming connection detected from /192.168.49.189` 12:05:23.344, `SSL handshake
  complete` 12:05:23.647.
- `R5-switch` marker, `KEYCODE_BACK` → exit dialog. `uiautomator dump` located "Switch Phone" at
  bounds `[315,509][461,542]`; tapped its center (388,525) → `AapProjectionActivity: User requested
  switch driver` 12:05:50.839, `ACTION_NATIVE_AA_SWITCH_DEVICE received` 12:05:50.903, `NativeAA: a
  driver switch is starting, so A0:46:5A:97:E4:95 is not let straight back in.` 12:05:50.904.
  Selector re-shown 12:05:52.628; **screenshot taken** (`r5_selector.png`, round 3 missed this).
- `R5-pick-poco` marker + `ACTION_NATIVE_AA_POKE --es extra_mac DC:B7:2E:5E:4E:59` at 12:06:08.
- `NativeAA: Driver selected: DC:B7:2E:5E:4E:59` 12:06:09.034 — confirms picking via the broadcast
  reaches the same `selectDriver()` function as a UI pick, as the brief states.
- **Criterion 1 (the one rounds 2 and 3 missed)**: `Connection accepted from POCO X3 NFC` 12:06:10.006
  → `WirelessServer: Incoming connection detected from /192.168.49.50` 12:06:11.960 → second `SSL
  handshake complete` 12:06:12.172. **The second handshake belongs to POCO X3 NFC**, on a client IP
  (`.50`) distinct from D-MOTO's (`.189`).
- **Criterion 2**: `a driver switch is starting, so A0:46:5A:97:E4:95 is not let straight back in.` —
  present (above), naming D-MOTO's MAC.
- **Criterion 3**: `createGroup SUCCESS` count = **1** for the whole run (single group, never
  recreated across the switch). Two distinct `p2p-wlan0-N` tokens appear in the text
  (`p2p-wlan0-8`, `p2p-wlan0-9`), but `p2p-wlan0-8` is a single `WirelessServer.logLocalNetworkInterfaces`
  listing at 12:04:56.660 — before the group's own interface (`p2p-wlan0-9`) came up — not a second
  group creation; every `onGroupInfoAvailable` and both connections use `p2p-wlan0-9` consistently.
  Read against the brief's own instruction to count `createGroup SUCCESS` alongside interfaces
  "because one group can log twice": here it's the reverse — one group, one live interface, one
  incidental extra token from an interface-enumeration log line.
- **Criterion 4**: `AapService: Native AA session ended; keeping the WIFI_DIRECT network up for the
  phone's return.` present at 12:05:52.130.
- **Criterion 5**: `AapService: Native AA user exit. Stopping active launcher.` — **0** occurrences.
- The six numbers the brief asks for regardless of outcome:
  1. `Attempting manual poke to POCO X3 NFC`: **1** occurrence, 12:06:09.045.
  2. `Successfully poked POCO X3 NFC via HFP-AG. Holding 20000ms...` — yes, 12:06:09.789.
  3. `Connection accepted from`: motorola edge 30 neo 12:05:14.625; POCO X3 NFC 12:06:10.006.
  4. Client IP on `WirelessServer: Incoming connection detected`: `/192.168.49.189` (moto),
     `/192.168.49.50` (poco) — distinct.
  5. Refusal fragments: **0** `is the phone this switch moved away from` lines, **0**
     `connection attempts before this one.` lines (contrast round 3's 152 and no summary).
  6. Seconds from `Driver selected:` to the second handshake: **3.138 s** measured from POCO's own
     pick (12:06:09.034 → 12:06:12.172); **65.265 s** measured from the run's original pick
     (D-MOTO, 12:05:06.907). Round 3's comparable figure (34.47 s) was measured from a build whose
     manual poke did *not* route through `selectDriver()`, so it only ever logged one `Driver
     selected:` line — this build logs a second one for the switch target, so the two numbers are not
     directly comparable; both are reported for the record.
- No `Attempting active poke to device: POCO X3 NFC` — the round-robin never independently poked
  D-POCO during this window; the entire arrival was the manual-poke path.

## R5b — the driver changes their mind. New.

**UNTESTABLE as scripted; R5 recorded a second, independent PASS instead (per the brief's own
pre-registered fallback for this outcome).**

- `ACTION_NATIVE_AA_CANCEL_POKE` is not implemented in this candidate's automation surface (see Setup
  notes) — the broadcast the brief specifies for this run does not exist.
- Ran R5's setup fresh anyway (force-stop, relaunch, `native-poke-bt-macs` cleared and read back,
  both radios on) to see whether a cancel could still be raced in via the UI's real Back-press, using
  the same pick mechanism as R5:
  - First session (D-MOTO): `Driver selected: A0:46:5A:97:E4:95` 12:08:22.962 → `Successfully poked
    motorola edge 30 neo` 12:08:24.109 → `Connection accepted from` 12:08:24.669 → `SSL handshake
    complete` 12:08:28.284.
  - Switch requested, tapped Switch Phone (same bounds), `R5b-pick-poco` marker +
    `ACTION_NATIVE_AA_POKE DC:B7:2E:5E:4E:59` at 12:08:52.975 → `Driver selected: DC:B7:2E:5E:4E:59`
    12:08:52.964 → `Successfully poked POCO X3 NFC` 12:08:54.167 (1.19 s later) → `Connection accepted
    from POCO X3 NFC` 12:08:54.304 (140 ms later) → second `SSL handshake complete` 12:08:56.429
    (2.13 s later). **Total pick-to-handshake: ~3.45 s.**
  - The follow-up cancel broadcast (attempted at 12:08:53, ~1 s after the pick) returned the
    `unknown action` error, and by the time it would have been reissued via a UI key event the session
    had already formed.
- **D-POCO's reconnection on this rig is consistently near-instant** (3.14–3.45 s pick-to-handshake
  across both R5 and this attempt) — well under the ~20 s window R5b's design assumes, and too tight
  to reliably beat with sequential `adb shell` round-trips even with a working cancel action.
- Per the brief's explicit contingency ("If D-POCO happens to connect before the 20 s are up, the run
  cannot be done that way: say so, and report it as R5 passing twice rather than as an R5b result."):
  this attempt is reported as **R5 passing a second time**, independently confirming: second handshake
  → POCO X3 NFC, distinct client IP (`/192.168.49.50` vs `/192.168.49.189`), `a driver switch is
  starting` present, zero `Native AA user exit` line.
- R5b's actual question (does Cancel end the 120 s gate early) is **not answered this round** — it
  needs either the missing automation action implemented, or a rig/timing combination where D-POCO's
  reply is slow enough to intercept.

## R9 — a chosen driver's poke is not trampled. Regression guard, and directly touched.

**PASS**, all three criteria.

- Settings: as R1 (headless, both radios off, history cleared: both MAC strings empty,
  `native-poke-bt-macs`/`auto-start-bt-macs` empty both files).
- `R9-start` + `ACTION_START_WIRELESS`; group up (`5GHz createGroup SUCCESS!`) at 12:10:35.491.
- `R9-poke` marker + `ACTION_NATIVE_AA_POKE --es extra_mac A0:46:5A:97:E4:95` (D-MOTO) at 12:10:47.
- `Attempting manual poke to motorola edge 30 neo` at 12:10:47.236, 12:11:54.400, 12:12:40.229 —
  **3 occurrences**, as expected (up to 3 per the fix, where every earlier build could only ever emit
  one).
- `NativeAA: a chosen driver's wake poke is running — not replacing it with the multi-device loop.` —
  **6 occurrences** (three deliveries at 12:11:36–37, three more at 12:12:36–37), all while the manual
  poke's slot was held. Round 3 read 3 across an 88 s window; 6 here is expected, not suspicious,
  because the slot is now held longer.
- `Attempting active poke to device: POCO X3 NFC` — **0 occurrences while the manual poke's slot was
  active**. It appears exactly once in the whole capture, at 12:14:09.806 — **206 s after** the manual
  poke started and **31 s after** the round-robin had already resumed poking D-MOTO on its own
  (12:13:38.965, once the ~120 s hold expired). That is the round-robin cycling normally after the
  window closed, not the guard being trampled.
- The P2P group recreated on its usual ~60 s cadence throughout (no phone ever joined, radios off by
  design) — consistent with R1's pattern, not a finding.

## Anything the brief did not ask about

- The `ACTION_NATIVE_AA_CANCEL_POKE` gap (above) is worth fixing before the next round that needs it —
  it blocks R5b outright and would block any future automation-driven cancel test.
- R5's fix is fast enough on this rig (3.1–3.5 s pick-to-handshake) that the round's own R5b design —
  a 20 s cancel window — assumes a slower phone response than this hardware/build combination
  actually produces. A future revision of R5b may want a shorter assumed window, or a way to interrupt
  mid-poke rather than mid-hold.
- R4's dependence on `userExitedAA` (see its section) is a mechanism worth documenting near the
  `cancelPoke()` call site itself, since nothing else in that function's neighborhood hints that a
  successful cancel silently disables the credential-triggered auto-poke path until a separate,
  unrelated event clears it.

Settings restored byte-identical on D-HU (`settings.xml` and the device-protected mirror, both
diffed against pre-round backups). Both driver phones' radios restored to on. All logcat captures
stopped; confirmed via `ps aux | grep logcat` before ending the session.
