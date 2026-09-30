# driver-selection-native: round 3 results

Run 2026-09-04 on **D-HU** (MT50) as the head unit, **D-POCO** (POCO X3 NFC) and **D-MOTO**
(motorola edge 30 neo) as driver phones, both bonded throughout except the deliberate R8 unpair.
One APK: `fork/testing/driver-selection-plus-automation` @ `e926beb0569b` (md5
`98fcfc3fde6b39c537edbf8c77739890`), unit gate **1324 / 0** (matches the brief exactly),
`ACTION_QUERY_STATE` echoed the same commit while the app was stopped, no `-dirty`. No install
blocker this round: the rig already carried a debug build from round 2, so `adb install -r`
succeeded directly and `settings.xml` was preserved byte-identical (verified via diff) — the
uninstall/reinstall remedy was not needed.

**Headline: 8 of 9 runs PASS. R5 FAILS again, on the same criterion round 2 missed, for a
different and now well-understood reason.** R2, the point of the round, PASSes cleanly: the
deadline now reads the timeout setting (25.77 s / 45.84 s / 20.07 s apart, against round 2's
61.54 s / 61.625 s / 0.085 s apart). R3's fix is confirmed after a re-run (see Setup notes). R7's
precondition is now correctly satisfied and PASSes. R5's network-preservation half still passes
and its refusal guard visibly works (152 refusal lines during the exclusive window), but the second
handshake still goes to D-MOTO, not D-POCO — because the bounded 30 s "chosen driver exclusive"
window expires about 4.5 s before D-POCO's own WiFi/DHCP join actually completes, letting the
already-associated D-MOTO back onto the link first.

Evidence: `hur-wifi-test-scripts/round-driver-selection-native-r3/` (10 raw logcat captures — one
discarded and kept for the record — 3 screenshots, one UI-dump XML, the candidate APK, settings
backups).

---

## R0: build gate — PASS

- `./gradlew assembleGithubDebug` printed `Building from commit: e926beb0569b`, no `-dirty`.
- `ACTION_QUERY_STATE` (app stopped, `-f 0x00000020`) replied with `"commit":"e926beb0569b"`.
- Unit gate: **1324 / 0**, `BUILD SUCCESSFUL`. Exactly the brief's number.
- APK md5 `98fcfc3fde6b39c537edbf8c77739890`.
- No install blocker: the rig's installed app was already this round's debug flavor from round 2,
  so `adb install -r` succeeded directly. `settings.xml` confirmed byte-identical before/after via
  `diff`.

---

## R1: headless bring-up — **PASS**. Regression guard.

- Poke count in 120 s: **5**, alternating both phones (motorola edge 30 neo, POCO X3 NFC).
- First poke **2.199 s** after the first `5GHz createGroup SUCCESS` (round 2: 2.20 s, `main`:
  2.264 s — matches).
- `Multi-driver selection is active`: **0**. Deferral lines: **0**.
- Neither forbidden line appeared (`Cancelling background multi-device poke loop`, `Native AA user
  exit. Stopping active launcher`).
- 3 `createGroup SUCCESS` / 3 distinct `p2p-wlan0-N` over the 120 s window (the rig's known ~60 s
  group-recreate cadence, not a defect).
- `native-driver-selection-mode` read back as `1`; 2 bonded devices confirmed via
  `dumpsys bluetooth_manager` before the run; both phones' names appear in the poke lines.
- No `SSL handshake complete`, no `MATCH! Starting AapService` — clean, no contamination.

---

## R2: the deadline reads the timeout setting — **PASS**. The point of the round.

- Sub-run (a), timeout 10: `PROMPT_SHOWN` 13:57:22.498 → `went unanswered` 13:57:48.271 =
  **25.773 s**.
- Sub-run (b), timeout 30: `PROMPT_SHOWN` 13:58:30.195 → `went unanswered` 13:59:16.038 =
  **45.843 s**.
- Difference: **20.070 s**.
- All three PASS criteria met: ~25 s, ~45 s, ~20 s apart (round 2 read 61.54 s / 61.625 s / 0.085 s
  apart — the brief's own stated failure signature, now gone).
- `Multi-driver selection is active` count: **1** per sub-run (round 2 read 3; the fix now logs the
  hold once per prompt).
- Seconds from `went unanswered` to the first poke: **0.012 s** (a), **0.014 s** (b) — immediate,
  not another minute.
- No `SSL handshake complete` in either sub-run — clean.

---

## R3: dialog dismissed by leaving the app — **PASS** (after a re-run; see Setup notes)

Clean re-run, Home sent 6.671 s after `PROMPT_SHOWN` (well inside the ~25 s deadline window):

- `ACTION_NATIVE_AA_PROMPT_DISMISSED received` and `the driver prompt is gone without a choice —
  the accept gate is open again.` both landed **0.199–0.200 s** after the marker (round 2: 0.22 s
  — matches).
- First `Attempting active poke to device` landed **0.904 s** after the marker (round 2: 54.47 s —
  this is the half the round's fix targets, and it is fixed).
- `Multi-driver selection is active` count: 1. No contamination (`SSL handshake complete`: 0).

---

## R4: cancel stops the poke without deafening the unit — **PASS**. Regression guard.

- `cancelPoke() called — user explicitly canceled driver selection.` landed **0.127 s** after the
  `R4-cancel` marker.
- Zero `Attempting active poke to device` after the cancel, and zero
  `User explicitly canceled driver selection` refusal lines for the remainder of the run.
- After 35 s, cycling D-MOTO's Bluetooth produced `a phone arrived over Bluetooth — the cancelled
  prompt no longer stands.` at 14:04:58.915, `Connection accepted from motorola edge 30 neo` at
  14:05:04.139, `SSL handshake complete` at 14:05:11.104. Lever: **BT arrival**, matching round 2.
- 3 `createGroup SUCCESS` / 3 distinct `p2p-wlan0-N` over the run's ~70 s (normal cadence, not a
  regression). Exactly 1 `SSL handshake complete`.

---

## R5: Switch Phone reaches the phone the driver chose — **FAIL**. Same criterion round 2 missed.

**Network-preservation half PASSES.** `a driver switch is starting, so A0:46:5A:97:E4:95 is not
let straight back in.` fired at 14:06:52.399, naming D-MOTO's MAC as the brief requires. Exactly
**1** distinct `p2p-wlan0-N` (`p2p-wlan0-13`) and **1** `createGroup SUCCESS` across the whole
capture. `AapService: Native AA session ended; keeping the` present (14:06:53.634).
`AapService: Native AA user exit. Stopping active launcher` absent. The refusal guard is visibly
working: `the driver chose DC:B7:2E:5E:4E:59, so A0:46:5A:97:E4:95 waits until that phone has had
its turn.` fired **152** times while D-MOTO kept retrying.

**The "reaches D-POCO" half still FAILS.** The second `SSL handshake complete` (14:07:45.239) does
not belong to D-POCO. Every `WirelessServer: Incoming connection detected` in the capture — both
the first session's and this one's — came from the identical client IP `192.168.49.189`; D-POCO's
IP never appears anywhere in the capture. That is D-MOTO reconnecting on the same still-open P2P
link, not D-POCO joining fresh.

**Why, this time:** `Driver selected: DC:B7:2E:5E:4E:59` (D-POCO) was logged at 14:07:10.769. The
brief's own diagnostic threshold is a handshake landing **more than 30 s** after that point, because
that is when the chosen-driver-exclusive window closes. This one landed at **34.47 s** after
selection (**34.57 s** after the `R5-pick-poco` marker) — 4.5 s past the guard's own bound. D-MOTO,
which never left the P2P group and had been retrying every ~150 ms throughout, won the race the
instant the exclusive window lapsed, before D-POCO's slower WiFi/DHCP join could land. The guard
fix is correctly implemented and does exactly what it says; it is simply not long enough on this
rig for a real second phone's network join to beat it.

- Switch-request (`User requested switch driver`, 14:06:52.319) to second handshake: **52.92 s**.
- `R5-pick-poco` marker to second handshake: **34.569 s**.
- `createGroup SUCCESS` count for context: 1 (the network stayed up throughout, as intended).
- A screenshot of the Switch Phone selector was missed this run (see Setup notes); the
  `uiautomator` XML dump was preserved instead as `r5_exitdialog_uidump.xml`.

---

## R6: Switch Phone with "close app on disconnect" on — **PASS**. Regression guard.

- No `AapService destroying`, no `killProcessOnDestroy is true` in the 10 s after `User requested
  switch driver`.
- `adb shell pidof com.andrerinas.headunitrevived` returned a live pid (13481) after the switch.
- Driver selector confirmed on screen via screenshot (`r6_selector.png`): "Select Driver Phone"
  showing motorola edge 30 neo (In vehicle / Connected, ★ Preferred) and POCO X3 NFC (Paired).

---

## R7: `Off` mode's WiFi button offers a picker — **PASS**. Precondition correctly satisfied.

- `native-poke-bt-macs` cleared and **read back empty immediately before the tap** (this is what
  voided round 2's R7 — done correctly this time).
- Picker opened: screenshot `r7_result.png` shows "Select Driver Phone" listing both phones as
  plain "Paired" entries, no preferred star, no auto-connect.
- Zero `HomeFragment: Connecting to Native-AA device:` lines in the capture.

---

## R8: single bonded phone — **PASS**. Regression guard.

- D-MOTO unpaired via the system Bluetooth settings gear icon (confirmed via `dumpsys
  bluetooth_manager` before/after); only D-POCO bonded for the run.
- Zero `PROMPT_SHOWN`, zero `Multi-driver selection is active`, zero `went unanswered`.
- Launch to `SSL handshake complete`: **~25.5–26.5 s** depending on anchor (first app log line vs.
  `AapService.onCreate`), against the 40 s bar — faster than round 2's 27.79 s, no regression.
- D-MOTO re-paired afterward (confirmed via `dumpsys bluetooth_manager`, both phones bonded with
  their original MACs before R9 began).

---

## R9: a chosen driver's poke is not trampled — **PASS**. Regression guard.

- `Attempting manual poke to motorola edge 30 neo` followed the `R9-poke` marker by **0.101 s**.
- `a chosen driver's wake poke is running — not replacing it with the multi-device loop.` appeared
  **3** times during the run (round 2: 3 times in 60 s — matches).
- Zero `Attempting active poke to device: POCO X3 NFC` for the whole ~88 s window
  (`R9-poke` to `R9-end`).

---

## Setup notes

- No install blocker this round (see R0) — the pre-approved uninstall/reinstall remedy was not
  needed.
- **R3's first attempt was discarded and re-run.** The operator (this session) sent the `R3-home`
  marker roughly 50 s after `PROMPT_SHOWN` instead of promptly, letting R2's own internal ~25 s
  deadline fire *before* the Home press. That produced an extra, unintended `went unanswered` +
  poke mid-dialog, and the subsequent Home dismissal then skipped its normal `the driver prompt is
  gone without a choice` log line — not evidence of a defect, just a contaminated precondition (the
  run was meant to test Home racing the dialog, not Home after the deadline already fired). Kept as
  `r3_candidate_discarded_slow_home.txt` for the record; re-run cleanly by polling the capture file
  for `PROMPT_SHOWN received` and firing the marker+Home within ~0.2 s of it landing, well inside
  any deadline. Worth carrying forward: script the wait-for-prompt step as a tight poll loop rather
  than a fixed sleep for any future run that needs to race a dialog.
- **R5's screenshot was missed.** The brief asks for a screenshot of the Switch Phone selector; this
  round only ran a `uiautomator dump` (preserved as `r5_exitdialog_uidump.xml`) and moved on to the
  poke without a `screencap`. R6 and R7's screenshots were taken correctly. Flagging so a future
  round doesn't repeat it.
- **Re-pairing D-MOTO after R8** needed opening D-MOTO's *own* Bluetooth settings screen first — a
  phone with Bluetooth on is not otherwise discoverable to the head unit's scan, even right after
  being forgotten. No operator/keyguard issue this time (screen was already unlocked).
- `hur-wifi-test-scripts/build_hur.sh` and `run_unit_tests.sh` used for R0 (unchanged from round 2).
  `set_hu_settings_host.py` used for every scalar/set settings write this round; the
  device-protected mirror (`settings_device_protected.xml`) was edited directly via
  `adb shell cp`/`chown`/`chmod` each time it needed clearing, since no existing script targets that
  file. No new script was added.
- Both `settings.xml` and the device-protected mirror restored to their pre-round state at the end
  (verified byte-identical via `diff` for `settings.xml`; the mirror was reconstructed from the
  first read taken before any edit, since it was not backed up before the first write — worth
  backing up on the very first touch next round).
- Both phones' radios restored to their pre-round state (D-POCO: BT+WiFi on; D-MOTO: BT off, WiFi
  on) and both phones confirmed bonded before closing the round.

## Two things noticed in passing, not part of the brief

An operator watching the rig live during this round raised two observations, investigated against
the code and this round's own captures:

1. **The multi-phone selection dialog's "Auto-connecting to X in N seconds" progress bar wasn't
   seen since round 2.** The countdown UI (`HomeFragment.kt`'s `countdownContainer` /
   `countdownProgress` / `countdownSubtitle`) is unchanged by this round's fix — diffed against the
   branch base, only MAC-resolution logic changed. It only renders when a target auto-resolves
   *while* the ambiguous selector is still shown (`autoCountdown && autoTargetMac != null`), and no
   run in this round's matrix hits that combination: R2/R3 deliberately start with empty history (no
   target resolves, by design — this is literally what R3's own brief text says), R5/R6 have an
   unambiguous preferred MAC and never show the dialog at all for the initial connect, and R7's
   picker has no countdown by design. Likely explained by which scenarios this round exercises
   rather than a regression, but the one case that would show it (ambiguous selector + a target that
   still resolves, e.g. one phone already BT-profile-connected) was not hardware-tested this round.
2. **"Android Auto is starting" was reported showing even with both phones' Bluetooth off.** Traced
   `AapProjectionActivity` and its `android_auto_starting` notification text to a single gate:
   `AapService.kt`'s `observeConnectionState()` only calls `launchAapProjectionActivity()` on
   `CommManager.ConnectionState.HandshakeComplete`. Checked every capture from this round: in R4,
   R5, R6 and R8 — the only runs that formed a real session — `AapProjectionActivity.onCreate`
   landed 0.3–0.4 s *after* `SSL handshake complete`, never before, and it does not appear at all in
   R1/R2/R3/R7/R9 (no session in those). No code path was found that would show it with both
   radios off; it may have been the driver-selection dialog or the app's own launch splash
   (`MainActivity`'s `splash_overlay`, which does show on every launch regardless of phone state)
   observed instead. Unresolved — needs a timestamp or a repro to pin down further.

Neither is a stated criterion in this brief's §5/§6 and neither changes any run's verdict above;
recorded here for whoever picks up the next round.
