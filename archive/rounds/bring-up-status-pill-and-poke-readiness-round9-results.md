# bring-up-status-pill-and-poke-readiness — round 9 results

**Candidate:** fork/feat/native-aa-enhancements-extbt @ `a8917a7a`       **Baseline:** none (brief §1: no baseline needed)
**APK md5:** `a626997ccc0d5addf0aabe808e66572e` (identical on D-HU and D-POCO, matches the locally built APK)
**Unit:** D-HU = UNISOC MT50_YT610E4GFPSL_U, Android 14, rooted (direct root shell, no `su`). D-POCO = Redmi/POCO X3 NFC, Android, non-rooted (`run-as`). D-MOTO = motorola edge 30 neo, phone-only role.
**Date:** 2026-09-11

## Setup notes

**Scripts used / added.** Inventoried `hur-wifi-test-scripts/` per house rule 1 before starting.
`build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh` used unchanged for R0.
`set_hu_settings_host.py` (existing, rooted-D-HU scalar/`<set>`/delete editor) used for every D-HU
settings write in Part O. Three new scripts added this round, all left in place:

- **`set_prefs_runas_host.py`** (new) — a run-as equivalent of `set_hu_settings_host.py` for a
  non-rooted phone. Needed because the existing `set_prefs_runas.sh`'s `DEL` only matches a
  self-closing element or a `<string>...</string>`, not a multi-line `<set name="...">...</set>`
  block — it would have corrupted `auto-start-bt-macs` / `auto-start-offer-answered-macs` on
  D-POCO. Pulls `settings.xml` via `run-as cat`, edits on the host in Python, pushes back via
  `run-as cp`. Used for every D-POCO settings write in Part A.
- **`offer_bar_watch_tap.sh`** (new) — polls a capture file for
  `the Bluetooth auto-start offer is up for`, screenshots the bar untouched, then taps Yes before
  the 20s countdown expires. Supports a fast path (`TAP_X`/`TAP_Y` coordinates measured off a prior
  screenshot at the same resolution) and a `uiautomator dump` fallback; the fallback is what failed
  on this rig (see below), so the fast path is what actually shipped this round's O1 result.
- **`periodic_screencap.sh`** (new) — takes `COUNT` screenshots at `INTERVAL_S` seconds apart, named
  `<TAG>-NN.png`. Used for O2's countdown-decrease evidence.

**Deviations from the brief, most to least material:**

1. **§2a's precondition was not already true.** `TESTING-TEMPLATE.md` §7a's note that D-MOTO has
   been bonded to D-HU since round 5 was stale: D-HU's Bluetooth metadata showed D-MOTO's pairing
   was deleted earlier the same day (2026-09-11, 19:38:39), before this round started. Re-pairing
   D-HU↔D-MOTO for O5 and un-pairing it again for O1-O4 both needed the operator's hands — this
   build has no scriptable bond/un-bond path, and hand-editing
   `/data/misc/bluedroid/bt_config.conf` on a running `bluetoothd` was judged too risky to the live
   Bluetooth stack to script. Both changes were done by the user at my request, both confirmed
   before/after with `dumpsys bluetooth_manager`, and the original two-phone-then-one-phone-then-
   two-phone sequence was restored by the end of the round.
2. **A2b's first attempt was discarded — contaminated by a stray tap.**
   `Auto-connect: cancelled by user` fired at ~65.5s into the attempt, well before the recreation
   lever was due, with nobody driving the device. No input-injecting process (`monkey`,
   `uiautomator`) was found running on D-POCO at the time (`ps -A` clean); cause undetermined.
   Capture kept as `a2b-dpoco-DISCARDED-strayinput.txt`. The retry that is graded below was clean.
3. **The recreation lever landed outside the brief's ±10s windows on two of the three Part A runs**,
   driven by this session's own tool-call round-trip latency rather than anything on the rig:
   A2b's single recreation landed at ~88.0s (target 60±10s), and A2e's first landed at ~24.6s
   (target 40±10s) with its second at ~100.1s (target 90±10s, essentially at the edge). All three
   runs still graded cleanly because the brief's actual pass conditions are about line counts, the
   `re-armed` suffix, and the wall clock from the *first* begin to the final give-up — all of which
   came out correct and internally consistent with the measured lever timing (see each run below).
4. **O1's graded attempt is the third.** Attempt 1 was spent because the watch/tap script wasn't
   armed yet when the session formed faster than expected; the offer resolved "by running out"
   on its own, which incidentally supplied an unplanned but valid first data point for O2 (logged,
   not otherwise used). Attempt 2 failed because `uiautomator dump` + `adb pull` did not complete
   inside the 20s window — the dump command reported success but the file was never present to
   pull, cause not diagnosed. Attempt 3 used coordinates measured directly off attempt 1's
   screenshot (`TAP_X=1218 TAP_Y=668` at 1440x720) instead, landed the tap about a second after the
   bar appeared, and is the one graded.
5. **Two `adb logcat` processes briefly wrote to the same file.** After discarding A2b's first
   attempt and restarting, and again across O1's three attempts, a previous capture's `adb logcat`
   process was not always killed before the next one started against the same output path. This
   produced a `grep: binary file matches` warning (consistent with interleaved/duplicate lines) but
   every decisive line quoted below was cross-checked against its own timestamp and is not in
   doubt. Fixed by checking `ps aux | grep logcat` is empty before every subsequent capture.
6. **Neither device's `settings.xml` was snapshotted in full before this round's edits**, against
   `TESTING-TEMPLATE.md`'s general "always back up settings.xml" guidance — only the individual
   keys the brief names were touched, and every write was read back immediately, but there is no
   full pre-round baseline file on either device to restore to. Flagging so a future round does not
   assume one exists.
7. **O4's "sit on the home screen... without connecting" needed an interpretation.** With a single
   unambiguous driver candidate, the app auto-connects and would leave the home screen within
   seconds on its own. Read this the same way Part A reads "a phone that never answers": D-POCO's
   own Bluetooth radio was switched off for the duration (`svc bluetooth disable`/`enable`), so
   D-HU still sees POCO as a bonded candidate and keeps the pill cycling on the home screen for the
   full window rather than completing a session. This is not spelled out in the brief; noting the
   interpretation in case a different one was intended.

## R0 — build, gate, install, identity

**PASS**

- Gate: **1831 / 0** (JUnit XML totals across `app/build/test-results/testGithubDebugUnitTest/`:
  `tests=1831 failures=0 errors=0`), matching the brief exactly.
- `adb install -r` on D-HU (`27870808938846`) and D-POCO (`4f4027e9`); `pm path` + `md5sum` on both
  read back `a626997ccc0d5addf0aabe808e66572e`, matching the locally built APK.
- Identity: `unzip -p classes*.dex | strings | grep -c` → `maybeOfferAutoStart` = 2,
  `re-armed` = 1. Both confirmed present.

## Part A — the bound survives an activity rebuild (D-POCO as head unit, D-MOTO paired, BT off)

### A2 — the control

**PASS**

- Settings (D-POCO, via `set_prefs_runas_host.py`): `wifi-connection-mode=3` (int),
  `native-driver-selection-mode=1` (int), `screen-orientation=0` (int), `auto-start-bt-macs` and
  `auto-start-offer-answered-macs` deleted. All read back as applied/absent.
- Radio state: D-MOTO Bluetooth confirmed `enabled: false` before launch.
- Discard-rule check: clean.
- Decisive lines:
  - `20:25:29.020 MainActivity.beginAutoConnect | Auto-connect: begin (Native-AA driver: motorola edge 30 neo, mode=PILL_THEN_OVERLAY)`
  - `20:25:29.029 MainActivity.startAutoConnectWatchdog | Auto-connect: this attempt gives up in 149s (mode=PILL_THEN_OVERLAY)` — exactly once, no `, re-armed`
  - `20:27:59.023 MainActivity.endAutoConnectIfExpired | Auto-connect: nothing answered this attempt (mode=PILL_THEN_OVERLAY), ending it`
- Measurements: 1 give-up line (149s); wall clock begin→end = **150.003 s**. Pill still cycling
  (`WAKING_PHONE`) after give-up, not rewound to `ARMED`. 0 `FATAL EXCEPTION`, 0 `WindowLeaked`.

### A2b — one recreation re-arms what is left (the point of the round)

**PASS**

- First attempt DISCARDED (Setup notes #2). Retry settings/radio state identical to A2, re-verified.
- Recreation lever (`user_rotation 1`) fired at ~88.0s into the attempt (target 60±10s — see
  Setup notes #3). Confirmed via a second `MainActivity.logLaunchSource` (`20:52:28.988`) and
  `WindowManager: finishDrawing of relaunch` (`20:52:29.216`).
- Discard-rule check: 1 re-run (the stray-tap discard); the graded attempt is clean.
- Decisive lines:
  - First begin: `20:51:00.969`
  - `20:51:00.977 gives up in 149s (mode=PILL_THEN_OVERLAY)` — no suffix
  - `20:52:29.096 gives up in 61s (mode=PILL_THEN_OVERLAY, re-armed)` — second and last, `re-armed`
  - `20:53:30.970 Auto-connect: nothing answered this attempt (mode=PILL_THEN_OVERLAY), ending it`
- Measurements: exactly **2** give-up lines (149s plain, 61s `re-armed`); wall clock first-begin→end
  = **150.001 s**. 0 `FATAL EXCEPTION`, 0 `WindowLeaked`.

This is the round's core result: the re-arm reflects genuine remaining time (61s ≈ 150 − 88.0s,
not a restarted 150s), and the attempt still ends at the original deadline. Round 8's defect
(one arm line, no give-up ever) does not reproduce.

### A2e — two recreations, deadline still doesn't move

**PASS**

- Settings/radio state identical to A2/A2b.
- Recreation levers: first (`user_rotation 1`) at ~24.6s (target 40±10s, landed early), second
  (`user_rotation 0`) at ~100.1s (target 90±10s, at the edge). See Setup notes #3.
- Discard-rule check: clean.
- Decisive lines:
  - First begin: `20:54:11.675`
  - `20:54:11.684 gives up in 149s (mode=PILL_THEN_OVERLAY)` — no suffix
  - First recreation confirmed (`logLaunchSource` `20:54:36.243` + `finishDrawing of relaunch` `20:54:36.474`)
  - `20:54:36.353 gives up in 125s (mode=PILL_THEN_OVERLAY, re-armed)`
  - Second recreation confirmed (`logLaunchSource` `20:55:51.789` + `finishDrawing of relaunch` `20:55:52.028`)
  - `20:55:51.892 gives up in 49s (mode=PILL_THEN_OVERLAY, re-armed)`
  - `20:56:41.677 Auto-connect: nothing answered this attempt (mode=PILL_THEN_OVERLAY), ending it`
- Measurements: exactly **3** give-up lines (149 → 125 → 49, each smaller than the last, `re-armed`
  on the 2nd and 3rd only, not the 1st); wall clock first-begin→end = **150.002 s**.
  0 `FATAL EXCEPTION`, 0 `WindowLeaked`.

## Part O — the auto-start offer at projection start (D-HU only)

Order per §2a: O5 first (two-phone state), then D-MOTO unpaired by hand (Setup notes #1), then
O1–O4, then D-MOTO re-paired at the end.

### O5 — two paired phones still clear the stored trigger

**PASS**

- Pairing: D-HU bonded to POCO X3 NFC + motorola edge 30 neo (existing pairing restored by the
  user for this run).
- Settings: `wifi-connection-mode=3`, `auto-start-bt-macs=[DC:B7:2E:5E:4E:59]`,
  `auto-start-bt-name="POCO X3 NFC"`, `log-level=0` (Verbose).
- Decisive lines: `BluetoothHelper: driver candidates: 2 phone, 0 unknown, 0 not a phone`;
  `HomeFragment: more than one phone is paired, so the Bluetooth auto-start device is cleared.`
- Measurement: `auto-start-bt-macs` read back as `<set name="auto-start-bt-macs" />` (empty)
  after 30s on the home screen without connecting.

### O1 — offer appears at the first frame, Yes turns auto-start on

**PASS** (third attempt graded — Setup notes #4)

- Settings: `auto-start-bt-macs` / `auto-start-bt-name` / `auto-start-offer-answered-macs` all
  cleared and read back absent. Pairing: D-HU + POCO X3 NFC only (D-MOTO unpaired by hand).
- `driver candidates: 1 phone, 0 unknown, 0 not a phone`.
- Decisive lines: `21:28:47.962 the Bluetooth auto-start offer is up for POCO X3 NFC (DC:B7:2E:5E:4E:59).`
  (after the picture was up); `21:28:48.630 the Bluetooth auto-start offer was answered yes.`
  (tapped within ~1s of the bar appearing, well inside the 20s window).
- Screenshot `o1-bar-before-tap.png`: the bar reads "Do you want to auto-start the app with this
  device, POCO X3 NFC?", "No in 20s", with both YES/NO buttons, legible over the live
  "Android Auto is starting…" screen.
- Afterward: `auto-start-bt-macs=[DC:B7:2E:5E:4E:59]`, `auto-start-bt-name="POCO X3 NFC"`;
  the device-protected mirror
  (`/data/user_de/0/com.andrerinas.headunitrevived/shared_prefs/settings_device_protected.xml`,
  read directly — D-HU's shell is already root, no `su` needed) carries the same MAC. Bar gone in
  `o1-after-tap.png`, projection undisturbed underneath.

### O2 — the countdown answers No on its own

**PASS**

- Settings: same three keys cleared and read back absent.
- `driver candidates: 1 phone, 0 unknown, 0 not a phone`.
- Decisive lines: `21:30:49.086 the Bluetooth auto-start offer is up for POCO X3 NFC (DC:B7:2E:5E:4E:59).`;
  `21:31:09.114 the Bluetooth auto-start offer was answered by running out.` —
  measured **20.028 s** (brief's tolerance 20±3s).
- Screenshots (3s cadence, `o2-shots/`): `o2-04.png` (+3.5s from launch, "No in 19s") and
  `o2-07.png` (+15.5s, "No in 8s") show the countdown decreasing; the bar persists correctly across
  the loading-screen-to-map transition rather than being torn down by the view change underneath it.
- Afterward: `auto-start-bt-macs` stayed empty (`<set name="auto-start-bt-macs" />`);
  `auto-start-offer-answered-macs` contains `DC:B7:2E:5E:4E:59`. Bar gone in `o2-09.png`, map fully
  rendered underneath.

### O3 — asked once per phone, not again

**PASS**

- `auto-start-offer-answered-macs` left exactly as O2 ended it (containing POCO's MAC); nothing
  else cleared.
- `driver candidates: 1 phone, 0 unknown, 0 not a phone`.
- Decisive lines: `the Bluetooth auto-start offer` — 0 occurrences in the whole capture;
  `21:32:09.804 SSL handshake complete` reached. `o3-picture-check.png` confirms a fully rendered
  picture (Google Maps) with no bar anywhere.

### O4 — the home screen no longer asks (positive control)

**PASS**

- Settings: all three keys cleared and read back absent again. D-POCO's own Bluetooth radio
  disabled for the duration (Setup notes #7) so the pill keeps cycling on the home screen instead
  of the session completing in a few seconds.
- Home screen from `21:33:53` to at least `21:36:06` (**≈133 s**, past the required 90s).
- Screenshot at +52s (`o4-at60s.png`): home screen showing the "POCO X3 NFC is disconnected,
  waking it… / Waking your phone / WiFi Direct on 5 GHz (5805 MHz)" pill and no dialog or bar
  anywhere.
- Decisive lines: `the Bluetooth auto-start offer` — 0 occurrences in the whole capture.
  0 `FATAL EXCEPTION`, 0 `WindowLeaked`.
- Afterward: `auto-start-offer-answered-macs` absent from `settings.xml` — nothing was consumed by
  a question that was never put.

**On every build up to and including `e466171a` this is precisely where the old dialog appeared
(round 7 caught it twice); it does not appear here.**

## Anything the brief did not ask about

- This is the first round of this thread with **no FAIL and no INCONCLUSIVE at all** across 8
  graded runs (compare round 8's single A2b FAIL) — a full clean sweep on the first properly-run
  attempt at every run once the A2b stray-tap and O1 tooling issues were worked through.
- The offer bar correctly survives the loading-screen-to-map transition underneath it (visible
  incidentally in O2's screenshot sequence) rather than being torn down when the projected view
  changes — worth knowing given `AapProjectionActivity`'s other bottom-bar (the renderer
  confirmation banner) has had view-lifecycle issues in other threads.
- `TESTING-TEMPLATE.md` §7a's D-HU pairing note ("D-MOTO bonded since round 5") is now stale and
  should be updated or removed — see Setup notes #1.
