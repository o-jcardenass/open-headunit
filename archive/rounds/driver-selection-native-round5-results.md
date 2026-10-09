# driver-selection-native — round 5 results

**Candidate:** `fork/testing/driver-selection-plus-automation` @ `42133e6d` (stamp `42133e6d9e8a`)
**Baseline:** none this round (no A/B; the brief carries round 1-4 numbers inline)
**APK md5:** `5f720b110d44a663acbd0ca57711452f`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, D-HU; phones D-MOTO (motorola edge 30 neo) and D-POCO (POCO X3 NFC), both bonded to D-HU throughout
**Date:** 2026-09-05

## Setup notes

- Inventory: `hur-wifi-test-scripts/` scripts used — `build_hur.sh`, `run_unit_tests.sh`,
  `install_and_launch.sh` (build/install/launch), `set_hu_settings_host.py` (all settings writes:
  scalars and the `<set>` MAC lists). No new script needed this round.
- Both phones started this round with Bluetooth **off**, WiFi **on** — confirmed via `dumpsys`
  before touching anything, and restored to exactly that at the end.
- `settings.xml` was restored from a byte-identical backup at the end of the round (diffed clean).
- **R11's "as soon as a picture appears" dump could not be caught.** The escalation
  (`Auto-connect: a phone is answering...`) to the projection activity actually being displayed
  measured 950 ms (first attempt) and 1077 ms (second attempt) end to end. A single
  `uiautomator dump` round-trip on this rig measured **~3.0 s average** (a 30-dump rapid-fire loop
  spanning the transition window took 90 s total). No adb-only technique can land a dump inside a
  sub-1.1 s window when the tool itself takes 3x that long. The log is airtight on both attempts
  (escalation line exactly once, session completes cleanly); the dump requirement is corroboration
  that this rig's tooling cannot supply for this specific transition. Not a defect, not a brief bug
  either — just a hardware/tooling speed mismatch worth recording for the next round that touches
  this path.
- **R13's accessory** ("Magnetic Speaker", `2E:F0:03:99:D6:FB`) was paired by the operator with one
  manual UI confirmation (no adb-only pairing route exists), per the brief's authorized exception.
  Its raw Class-of-Device read two different ways during setup (`0x040424` via D-HU's native BTM
  log, `0x240404` via a format seen on D-POCO's own bonded-device dump for the same physical
  device) — rather than trust either parse, the wake-poke picker was opened and read directly; see
  R13 below. Unpaired via the system Bluetooth settings' "Forget device" after the run (the app has
  no unpair route); both driver phones confirmed still bonded afterward.
- **R5 needed three attempts** before a clean pass; attempts 1 and 2 surfaced a genuine rig-level
  Bluetooth radio-contention mechanism, not a candidate defect. See R5 below — the mechanism is
  documented in full because it is new, reproducible, and worth a future round's attention.

---

## R0 — build gate

**PASS**

- Build printed `Building from commit: 42133e6d9e8a`, no `-dirty`.
- `ACTION_QUERY_STATE` replied `"commit":"42133e6d9e8a"` — matches.
- Unit gate: **1340 / 0** (`app/build/test-results`, summed `tests=1340 failures=0 errors=0`) — matches the brief exactly.
- APK md5 `5f720b110d44a663acbd0ca57711452f`, confirmed installed (`pm path` + `md5sum` match).

---

## R10 — no phone, no starting screen. The point of the round.

**PASS**

- Settings written: `native-driver-selection-mode=1`, `native-driver-selection-timeout=10`,
  `native-preferred-device-mac=A0:46:5A:97:E4:95`, `last-connected-native-mac=` (empty),
  `native-poke-bt-macs=` (empty set), `kill-on-disconnect=false`, `log-level=2`,
  `wifi-connection-mode=3`, `native-ap-transport=0`.
- Radio state: both phones' Bluetooth **and** WiFi off, confirmed via `dumpsys` on both immediately
  before launch.
- Discard-rule check: clean, single run.
- Decisive log lines:
  - `13:37:18.369 AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received`
  - `13:37:28.337 HomeFragment: Connecting to Native-AA device: motorola edge 30 neo (A0:46:5A:97:E4:95), btConnected=false`
  - `13:37:28.338 Auto-connect: begin (Native-AA driver: motorola edge 30 neo, mode=PILL_THEN_OVERLAY)`
  - `13:37:28.426 NativeAA: Attempting manual poke to motorola edge 30 neo...`
  - `13:37:58.357 Auto-connect overlay: watchdog timeout, hiding`
- Dump taken at 13:37:37 (~9 s after resolving): `auto_connect_pill` present, `auto_connect_loading_overlay` absent (count 1 / 0).
- Measurements: PROMPT_SHOWN → Connecting = **9.968 s** (round 4 read 9.983 s and 9.960 s). Poke fired **twice** (13:37:28.426, 13:38:14.265). Pill was **not** still on screen 30 s after resolving — its own watchdog hid it at **30.02 s** exactly (13:37:58.357), matching the documented 30 s design window.

All four PASS conditions met: `mode=PILL_THEN_OVERLAY`, `btConnected=false` with both radios off, pill (not overlay) in the dump, and the poke still fired.

---

## R11 — the pill becomes the overlay when a phone does arrive. New.

**PASS** (log criteria 1 and 3; criterion 2's dump could not be captured on this rig — see Setup notes)

- Settings: identical to R10.
- Radio state: both phones off at launch; D-MOTO's Bluetooth and WiFi switched back on once the
  pill was confirmed up via log (`mode=PILL_THEN_OVERLAY`).
- Two attempts, both reproducing the same result:
  - **Attempt 1**: pill confirmed at 13:40:11.091. Radios-on marker at 13:40:18.107 (7.0 s later).
    `WirelessServer: Incoming connection detected` 13:40:30.602. Escalation line
    `Auto-connect: a phone is answering, taking the full screen.` at **13:40:30.615**, count **1**.
    `SSL handshake complete` 13:40:30.914. `AapProjectionActivity` displayed 13:40:31.565
    (+716 ms after handshake). Escalation → displayed = **950 ms**.
  - **Attempt 2** (rapid-dump loop, 30 `uiautomator dump`s fired back-to-back starting before the
    radios went on): pill confirmed 13:42:02.818. Radios-on marker 13:42:12.601 (9.8 s later).
    `WirelessServer` 13:42:29.238. Escalation **13:42:29.251**, count **1**. Handshake 13:42:29.567.
    Displayed 13:42:30.328. Escalation → displayed = **1077 ms**. Of the 30 rapid dumps (spanning
    90 s at ~3.0 s/dump), dumps 1-7 (pre-transition) show `auto_connect_pill`; dump 8 (post-transition)
    shows neither id — the transition happened entirely inside the gap between two dumps.
- Discard-rule check: clean on both attempts.
- Session completion confirmed both times: `SSL handshake complete` then `AapProjectionActivity`
  displayed, video dimensions received, loading overlay hidden after first frame.

Both log criteria (escalation fires exactly once; session completes) held on both attempts. The
dump-based criterion (an intermediate screenshot showing `auto_connect_loading_overlay` and not
`auto_connect_pill`) could not be produced on this rig: the escalation-to-display transition
measured under 1.1 s on both attempts, while a single `uiautomator dump` round-trip here measures
~3.0 s. This is a tooling/hardware speed mismatch, not a candidate defect — the log is the primary
instrument per the brief's own §3, and it is unambiguous on both runs.

---

## R12 — a phone that is there still gets the full screen at once. New, and the regression that matters.

**PASS**

- Settings: `native-preferred-device-mac=A0:46:5A:97:E4:95`, timeout `10`, `native-poke-bt-macs`
  cleared.
- Radio state: both phones' radios on. D-MOTO's Bluetooth profile link to D-HU confirmed live
  before launch: `PbapClientStateMachine state=Connected`, `HeadsetClientStateMachine: BTA_HF_CLIENT_OPEN_ST`.
- Two attempts (the first took the corroborating dump too late — after the session had already
  fully formed — and was redone with a faster reaction):
  - Attempt 1: pick at 13:45:35.381, `btConnected=true`, `mode=OVERLAY`. Session fully formed by
    13:45:49.639, before the dump landed at 13:46:00 — dump shows neither id (both views already
    replaced by the live projection screen). Not a criterion failure, just the wrong instant.
  - Attempt 2: pick at 13:46:23.215, `btConnected=true`, `mode=OVERLAY`. Dump taken 13:46:31 (~8 s
    later, well inside the pre-handshake window this time): `auto_connect_loading_overlay` present
    (count 1), `auto_connect_pill` absent (count 0). `WirelessServer` connection 13:46:33.186,
    handshake 13:46:33.409.
- Discard-rule check: clean, one group per attempt.

`btConnected=true` and `mode=OVERLAY` landed together on the same pick both times; the dump
confirms the overlay (not the pill) once caught inside the live window. No case of `PILL_THEN_OVERLAY`
for a reachable phone — the fix is not too eager.

---

## R1 — headless bring-up. Regression guard, and directly touched.

**PASS**

- Settings: both MAC strings empty, `native-poke-bt-macs` and `auto-start-bt-macs` empty in **both**
  files — confirmed by reading `settings.xml` and the device-protected mirror back before launch,
  and confirmed the mirror re-synced to empty (via `App.onCreate`) once the first broadcast created
  the process.
- Radio state: both phones' Bluetooth and WiFi off. No third device bonded.
- No `am start` at all — `ACTION_LOG_MARKER` then `ACTION_START_WIRELESS` only, both landing
  (`AutomationReceiver:` present for each).
- Decisive log lines: `createGroup SUCCESS` at 13:47:54.291 (5 GHz), 13:48:55.052 (5 GHz),
  13:49:55.374 (Standard) — three headless bring-ups across the 120 s window, as expected with no
  phone ever completing a session. Five `NativeAA: Attempting active poke to device` lines,
  alternating both phones: 13:47:56.519 (moto), 13:48:27.311 (poco), 13:48:57.223 (moto),
  13:49:28.972 (poco), 13:49:57.540 (moto). **Zero** `paired device(s) are not phones` lines,
  **zero** `Multi-driver selection is active` lines.
- Measurements: 5 pokes, first poke **2.228 s** after the first `createGroup SUCCESS` (round 4 read
  2.225 s, round 3 2.199 s, `main` 2.264 s — consistent). `not phones` count: **0**.

Both phones classify as phones and neither is held back — the filter change is a no-op for the
guard case, as intended.

---

## R2 — the deadline still reads the timeout setting. Regression guard.

**PASS**

- Settings: identical to R1, MainActivity launched normally this time (dialog appears, untouched).
- Decisive log lines: `13:50:54.587 AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received` →
  `13:51:20.358 NativeAA: the driver prompt went unanswered — waking every paired phone again.`
- Measurement: **25.771 s** (round 4 read 25.765 s, round 3 25.773 s — consistent, nowhere near the
  round 2 failure signature of ~60 s).

---

## R5 — Switch Phone reaches the phone the driver chose. Regression guard, and directly touched.

**PASS** (third attempt; see below for what the first two attempts found)

- Settings each attempt: `native-preferred-device-mac=A0:46:5A:97:E4:95`, timeout `10`,
  `kill-on-disconnect=false`, both radios on, `native-poke-bt-macs` cleared and read back before
  every attempt (it re-seeds from the last completed handshake's peer, so this needed redoing each
  time — see the brief's own trap note).
- **Passing attempt (attempt 3)**:
  - First pick (auto-connect, D-MOTO): 14:08:27.007, `btConnected=false`. `createGroup SUCCESS`
    14:08:17.631. First handshake 14:08:36.892.
  - `KEYCODE_BACK` → **Switch Phone** tap → selector confirmed on screen (screenshot
    `evidence/driver-selection-native-round5/r5_selector.png`) → `ACTION_NATIVE_AA_POKE
    --es extra_mac DC:B7:2E:5E:4E:59`.
  - `14:08:57.567 NativeAA: a driver switch is starting, so A0:46:5A:97:E4:95 is not let straight back in.`
  - D-POCO's RFCOMM connection accepted 14:09:09.605 (~1.4 s after the poke).
  - Second `WirelessServer: Incoming connection detected from /192.168.49.50` at 14:09:12.975 —
    **distinct** from D-MOTO's `/192.168.49.189`.
  - Second `SSL handshake complete` 14:09:13.167.
  - `14:08:58.797 AapService: Native AA session ended; keeping the WIFI_DIRECT network up for the phone's return.` — present.
  - `createGroup SUCCESS` count for the whole run: **1**.
  - `Native AA user exit` count: **0**.
  - `btConnected=` is only logged on the **first**, auto-connect pick (`HomeFragment.connectToNativeDevice`);
    the manual Switch-Phone pick goes through `ACTION_NATIVE_AA_POKE` → `selectDriver()` directly and
    does not log a `btConnected` value of its own. Reported here as the brief asked, with that caveat.

All five criteria met: second handshake belongs to POCO X3 NFC on a distinct IP, the switch-away
refusal line is present, one group for the whole run, the session-ended line is present, zero user-exit
lines.

### What the first two attempts found (not a candidate defect)

Both discarded attempts failed for the same, now fully characterized reason: **D-MOTO held a live,
multi-profile Bluetooth link to D-HU at the moment of the switch** — confirmed via `dumpsys
bluetooth_manager`: `A2DPSinkStateMachine: Connected`, `AvrcpControllerStateMachine: Connected
(isActive: true)`, `HeadsetClientStateMachine: Connected`, `MceStateMachine (MAP): Connected`,
`PbapClientStateMachine: Connected`. D-POCO, by contrast, had **zero** active profile state
machines. With that link live, D-MOTO's own OS retried its RFCOMM (AA UUID) connection to D-HU
roughly every 100-150 ms for tens of seconds once its channel was closed by the switch — over 280
`NativeAA: Connection accepted from motorola edge 30 neo` lines in under a minute in attempt 1
alone. Manual pokes to POCO were sent and logged as "finished" in ~10-15 ms each (attempt 1: three
pokes at 13:58:28, 13:58:43, 13:58:58; attempt 2: three more) but POCO's RFCOMM connection never
landed while the flood was running.

The exclusive-gate timers ran their documented course exactly as coded
(`NativeDriverSelectionPolicy`: `CHOSEN_EXCLUSIVE_MS`=30 s, `CHOSEN_EXCLUSIVE_MAX_MS`=120 s,
`SWITCH_AWAY_REFUSAL_MS`=60 s) — in attempt 1, `NativeAaHandshakeManager.clearGateRefusals` logged
`turned away 282 connection attempts before this one` at 13:53:42.292, **exactly** 60.04 s after
`beginDriverSwitch` (13:52:42.252), and let D-MOTO itself back in, forming a second session that was
D-MOTO reconnecting to itself rather than the chosen POCO. Attempt 2 reproduced the identical
mechanism (`turned away 266`, second handshake also D-MOTO at 13:59:15.874, again ~60 s after the
switch began).

**A live control test confirmed the mechanism directly.** With attempt 2's D-MOTO session still
active-by-default, D-MOTO's Bluetooth was disabled via `svc bluetooth disable` and POCO was poked
again: POCO's RFCOMM connection landed within **~4.9 s** — back in the 3.1-3.5 s range the existing
rig notes already document for a POCO poke with a clear radio. (That third session could not
complete a handshake because a D-MOTO session was already active by then — correctly aborted with
`USB/other session already active. Aborting BT handshake so phone does not start a parallel
wireless attempt.` This is the accept-gate's other guard working as intended, not a new finding.)

**This is not a defect in the candidate.** The reachability check, the exclusive gate, and the
switch-away refusal all behaved exactly as documented, including the intentional 60 s give-up that
round 4 already flagged from the code. What is new is the precise mechanism: a single Bluetooth
radio cannot service one phone's continuous full-profile reconnect flood *and* give a second,
cold phone a fair, timely shot at a poke within the same 60 s window — and whether that happens
depends on whether the outgoing phone still holds live A2DP/AVRCP/HFP/MAP/PBAP profiles, not on
elapsed time since its last session. Attempt 3 passed cleanly because D-MOTO's profile links had
already dropped by then (no flood, no contention).

**Design recommendation for a future round (not implemented here):** disconnect the outgoing
phone's non-AA Bluetooth profiles (A2DP/AVRCP/HFP audio — not the AA RFCOMM channel itself) at the
moment a driver switch begins, so the radio is not contended while the incoming phone's poke tries
to land. Raised during this round's live diagnosis; worth a future brief.

---

## R5b — the driver changes their mind. New, and the run round 4 could not do.

**PASS**

- Setup exactly as R5's passing configuration, fresh session with D-MOTO first
  (pick 14:03:01.429, handshake 14:03:11.465).
- `KEYCODE_BACK` → **Switch Phone** tap → selector confirmed.
- Pick D-POCO and cancel in one adb invocation:
  - `14:03:45.847 NativeAA: Driver selected: DC:B7:2E:5E:4E:59`
  - `14:03:45.899 AutomationReceiver: com.andrerinas.openheadunit.ACTION_NATIVE_AA_CANCEL_POKE`
  - `14:03:45.911 AapService: ACTION_NATIVE_AA_CANCEL_POKE received — user explicitly canceled driver selection`
  - `14:03:45.912 NativeAA: cancelPoke() called — user explicitly canceled driver selection.`
  - Both broadcasts logged `AutomationReceiver:` — no `unknown action` refusal.
- Measured gap, pick to cancel: **65 ms** (14:03:45.847 → 14:03:45.912). Session did not form
  before the cancel landed; no contingency needed.
- Watched 175 s past the cancel:
  - `Attempting manual poke to` / `Attempting active poke to device`: **one** line, at 14:03:45.862 —
    this is the single legitimate poke from the pick itself, landing 49 ms **before** the cancel
    (14:03:45.911); **zero** after the cancel.
  - `waits until that phone has had its turn.`: **zero** after the cancel.
  - Second `SSL handshake complete`: **zero** in the whole watched window (175 s, well past the
    120 s the gate would otherwise have held).
  - No RFCOMM connection attempt of any kind from either phone for the rest of the capture;
    `createGroup SUCCESS` count for the whole run: **1**.
  - `userExitedAA is true. Skipping auto-poke.`: **0** — context, not a criterion; it never had
    occasion to fire since nothing tried a credential-triggered auto-poke afterward in this window.

All four PASS criteria met. Cancel stopped **both** the wake (zero pokes after) and the exclusive
gate (zero `waits until that phone` refusals, and the gate did not silently keep holding — no
device reconnected at all, chosen or otherwise, for the rest of the capture).

---

## R13 — the wake picker offers phones. New. Run last.

**PASS**

- Accessory bonded: "Magnetic Speaker" (`2E:F0:03:99:D6:FB`), a plain Bluetooth speaker, paired by
  the operator with one manual confirmation tap (`hcidump`/`dumpsys` show a normal SSP `just_works`
  pairing, no other taps used). Both driver phones remained bonded throughout.
- The three device pickers were opened via **Settings → search "Auto-start" → Auto-start settings**,
  then each row's own picker (no deep link exists for any of the three; this is the brief's
  authorized "genuinely cannot be automated otherwise" exception — the row itself is reachable only
  by a bounded scroll inside `AutoStartFragment`, confirmed 3 swipes down from the top).
  - **"Select the Bluetooth device to wake"** (the filtered, Native-AA-only picker): lists
    **motorola edge 30 neo** and **POCO X3 NFC** only. **Magnetic Speaker absent.**
    Screenshot: `evidence/driver-selection-native-round5/r13_wake_picker.png`.
  - **"Select Bluetooth Device"** (auto-start, unfiltered): lists all **three**, including
    Magnetic Speaker. Screenshot: `evidence/driver-selection-native-round5/r13_autostart_picker.png`.
  - **"Select the Bluetooth device to watch"** (auto-disconnect, unfiltered): lists all **three**,
    including Magnetic Speaker. Screenshot: `evidence/driver-selection-native-round5/r13_autodisconnect_picker.png`.
- Headless bring-up, 120 s, `native-poke-bt-macs` empty, `native-poke-all-paired=true`:
  `NativeAA: 1 paired device(s) are not phones and are not poked.` logged **8** times across the
  120 s poll cycle; **10** `Attempting active poke to device` lines total, every one naming only
  motorola edge 30 neo or POCO X3 NFC — **zero** name the accessory.
- Accessory unpaired afterward via system Bluetooth settings ("Forget device"); both driver phones
  confirmed still bonded.

All four PASS criteria met.

Note on the accessory's Class of Device: its raw CoD parsed to two *different* values depending on
which side's Bluetooth stack was asked (`0x040424` on D-HU's native log vs. `0x240404` in a format
seen on D-POCO's own bonded-device dump for the same physical device) — rather than trust either
manual bit-decode, the picker itself was opened and read directly, which settled it unambiguously.
Worth a note for whoever writes the CoD-filter unit tests: this specific speaker is not a reliable
reference for "reports a denylisted class" if its CoD is read from the wrong side.

---

## Report-back summary (brief §8)

1. **R10**: `mode=PILL_THEN_OVERLAY`, `btConnected=false`, `auto_connect_pill` in the dump (not the
   overlay), poke fired (2x).
2. **R11**: escalation line appeared exactly once on both attempts; the session completed
   (handshake → projection displayed) both times. The overlay-vs-pill **dump** could not be caught —
   the transition measured 950 ms / 1077 ms against a ~3.0 s `uiautomator dump` round-trip on this
   rig. Log evidence (the primary instrument) is unambiguous.
3. **R12**: `btConnected=true` and `mode=OVERLAY` together on the same pick, both attempts; the
   dump confirms the overlay once caught inside the live window.
4. **R5b**: Cancel stopped **both** the wake (zero pokes after) **and** dropped the exclusive gate
   (zero refusal lines, no reconnect of any kind for the rest of the capture) — not just the wake.

One line each:
- **R0**: commit `42133e6d9e8a`, md5 `5f720b110d44a663acbd0ca57711452f`, unit gate 1340/0.
- **R1**: 5 pokes, first poke at 2.228 s, 0 `not phones` lines.
- **R2**: measured deadline 25.771 s.
- **R5**: second handshake belongs to POCO X3 NFC (`.50` vs. D-MOTO's `.189`), 1 group for the
  whole run.
- **R13**: three screenshots taken (wake / auto-start / auto-disconnect pickers), all listed above.
- **R5b**: measured gap between pick and cancel = 65 ms.

**The one thing to watch for that is nobody's criterion:** `btConnected` and the following `mode=`
line never disagreed in any capture this round, across ten separate picks (R10 x1, R11 x2, R12 x2,
R5 x3, R5b x1, plus the two contaminated R5 attempts' initial picks). Always `false`/`PILL_THEN_OVERLAY`
together or `true`/`OVERLAY` together.

## Anything the brief did not ask about

- **R5's radio-contention finding** (full mechanism above) is the significant one: whether a driver
  switch's target phone can be reached within the exclusive gate's window depends on whether the
  *outgoing* phone still holds live Bluetooth audio/telephony profiles, not on how long ago its
  session ended. A future round should measure the design recommendation above (drop the outgoing
  phone's non-AA profiles at switch time) rather than leaving it to chance.
- **A single `uiautomator dump` costs ~3.0 s round-trip on this rig** (measured directly via a
  30-iteration back-to-back loop). Any future run whose PASS condition needs a dump to land inside
  a sub-second UI transition should route that coverage to a JVM test or a faster instrument
  instead of a dump — this rig's tooling cannot do it.
- **R13's accessory reported an inconsistent Class of Device depending on which Bluetooth stack
  read it.** Confirmed by direct comparison of D-HU's native BTM log (`cod:0x040424`) against a
  format seen on D-POCO's own bonded-device list for the same MAC (`0x240404`). Empirical UI
  verification settled the actual behavior; a future round relying on this same accessory for a
  CoD-based test should verify via the picker itself, not by decoding either side's raw log.
