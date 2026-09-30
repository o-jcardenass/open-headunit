# auto-start-loading-screen — round 2 results

**Candidate:** `feat/auto-start-loading-screen` @ `8a1968a3e`       **Baseline:** none (brief states none needed)
**APK md5:** `83896e398a7a5d563b804865dc71b65d`
**Unit:** D-HU (UNISOC MT50, Android 14), 1440x720 landscape. D-POCO (POCO X3 NFC) as the connecting phone.
**Date:** 2026-09-18

## Setup notes

- **Build gate:** `./gradlew :app:testGithubDebugUnitTest` via `run_unit_tests.sh` — **2176 tests, 0
  failures**, matching the brief exactly. Confirmed by summing `app/build/test-results/testGithubDebugUnitTest/*.xml`
  (`tests=2176 skipped=0 failures=0 errors=0`), not just the Gradle "BUILD SUCCESSFUL" line.

- **Install was over round 1's build, not fresh**, via `install_and_launch.sh`. `adb install -r`
  succeeded on the first try (no `INSTALL_FAILED_UPDATE_INCOMPATIBLE`), and the installed APK's md5
  matched the locally built one exactly. `settings.xml` was **not** wiped by the install.

- **Brief erratum: the three fresh-install keys did not survive round 1 the way the brief assumed.**
  §2 says "If this round can install over round 1's build rather than uninstalling, none of them will
  have moved," but round 1's own protocol restores `settings.xml` to its **pre-round-1** backup at the
  end of the round — not to round 1's own armed state — so by the time this round started,
  `native-driver-selection-mode` had reverted from round 1's `0` (DISABLED) back to the original `1`
  (AUTO), and `native-poke-all-paired` had reverted to unset (default `true`). Only `onboarding-version=2`
  had actually stuck (it was never part of round 1's restore diff). Re-applied `native-driver-selection-mode=0`
  and `native-poke-all-paired=false` explicitly for this round, matching round 1's actual Stage B setup
  rather than trusting the carry-over assumption. `native-poke-bt-macs` (a `<set>` key scoped to
  D-POCO's MAC, `DC:B7:2E:5E:4E:59`) **did** survive intact from round 1's restore, multi-line
  `<set>` element unmangled — the opposite risk (line-scoped `del` corruption) never came up because
  nothing needed to delete it.

- **New finding: `adb shell am start --es <key> "<value with a space>"` silently truncates the value at
  the space**, at least from this session's shell invocation. The first V1 arm 1 attempt used
  `--es launch_source "Bluetooth auto-start"` exactly as the brief and round 1 show it, but the app
  logged `App launched via: Bluetooth` (missing `auto-start`) and the `am start` banner showed a stray
  `pkg=auto-start` field that should not have been there — the shell had re-split the quoted argument
  on its internal space before adb forwarded it to the device, and `am` absorbed the orphaned second
  word as a package-name argument. Because `MainActivity`'s `beginAutoConnect()` gate is an exact string
  match against `LAUNCH_SOURCE_BLUETOOTH = "Bluetooth auto-start"`, the mismatched value skipped that
  whole path — no `Auto-connect: begin` line ever appeared, even though the background NativeAA
  quiet-hosting stack still connected the phone successfully via its own always-on status-pill path.
  **The fix is to escape the inner quotes so they survive adb's argv-join:**
  `--es launch_source \"Bluetooth auto-start\"` (backslash-escaped, not shell-quoted). Confirmed this
  produces the correct `App launched via: Bluetooth auto-start` and downstream `Auto-connect: begin`
  line every time afterward (V1 arm 1 retake, V1 arm 2, V2 arm 1, V2 arm 2 all used the escaped form
  and all logged correctly). The first V1 arm 1 attempt's capture is kept as `v1-arm1-attempt1-log.txt.gz`
  for the record, but the retake (`v1-arm1-log.txt.gz`) is the one this round's V1 arm 1 result is drawn
  from.

- **D-POCO's Bluetooth was found OFF and its screen on a stray `NotificationShade`** at the start of the
  round (leftover from unrelated prior session state), which would have silently starved the poke.
  Fixed with `input keyevent KEYCODE_HOME` plus `svc bluetooth enable` before the first run, per the
  known-quirk precondition check.

- **Frame-timing note for anyone re-using these recordings:** the MP4's own `mvhd` `creation_time` field
  is close to but not reliable for frame-to-wall-clock alignment (off by several seconds against the
  log in initial testing). What worked was calibrating off the recording's own content: the loading
  screen is static (near-zero frame-to-frame diff) right up until the real video motion starts, so a
  brightness/diff scan against the known `Hiding loading overlay after first video frame` timestamp
  locates the true recording start far more precisely than the container metadata does.

- **Scripts used:** `run_unit_tests.sh`, `build_hur.sh`, `install_and_launch.sh`, `set_hu_prefs.sh` for
  every scalar key. No new script added; `native-poke-bt-macs`'s existing `<set>` value needed no edit
  since it already held the right MAC from round 1's restore.

- Settings restored to the round's own pre-round backup at the end; verified byte-equal
  (`diff` against the pushed-back copy showed nothing).

---

## V1 — The pill on the projection's loading screen. This is the point of the round.

**PASS** (both arms)

### Arm 1 (`loading-screen-show-pill=true`)

- Settings written: `wifi-connection-mode=3`, `log-level=2`, `auto-start-loading-screen=true`,
  `loading-screen-show-pill=true`, `native-poke-all-paired=false`, `native-driver-selection-mode=0`,
  `native-poke-bt-macs={DC:B7:2E:5E:4E:59}` (carried over from round 1's restore).
- Discard-rule check: clean. `createGroup SUCCESS` ×1; the one `p2p-wlan0-N` index bump
  (`p2p-wlan0-1` removed → `p2p-wlan0-2` added) happened **before** that single creation, the
  documented benign stale-group-teardown-at-launch pattern, not a second group. `SSL handshake
  complete` ×1. `MATCH! Starting AapService` ×0. `Magic Garbage` ×0.
- Decisive log lines (all timestamps from the corrected retake, `v1-arm1-log.txt.gz`):
  - `13:05:25.260 App launched via: Bluetooth auto-start`
  - `13:05:25.562 Auto-connect: begin (Bluetooth auto-start, mode=OVERLAY)`
  - `13:05:30.249 Auto-connect overlay: handshake complete, handing off to projection`
  - `13:05:30.543 AapProjectionActivity.onCreate | HeadUnit for Android Auto (tm) - Copyright 2011-2015 ...`
  - `13:05:30.767 AapProjectionActivity.renderProjectionPill | AapProjectionActivity: status pill step: STARTING_PROJECTION` — **unqualified, no parenthesised reason.**
  - `13:05:32.121 AapProjectionActivity.hideLoadingOverlay | Hiding loading overlay after first video frame`
- **Window (banner → hiding overlay): 1578 ms.** Begin-to-handoff: **4.687 s.**
- Frame evidence (`v1-arm1-handoff.mp4.gz`, offsets extracted by calibrated time, not polling):
  `v1-arm1-mainactivity-overlay.png` shows MainActivity's own overlay (bottom step-detail card:
  "Android Auto is starting… / Starting Android Auto / WiFi Direct on 5 GHz (5805 MHz)") just before
  the handoff. `v1-arm1-projection-loading-screen.png`, sampled inside the window, shows a visually
  distinct screen: the app's AA arrow-logo watermark, the spinner, and "Android Auto is starting…"
  centred, with **no bottom step-detail card**. This is a real, camera-confirmed hand-off between the
  two activities' loading UIs, corroborating the log. I could not isolate a separately-rendered "pill
  chip" shape distinct from this whole screen in the sampled frames — consistent with round 1's own
  finding that this ~1.5 s window is very hard to catch by frame sampling; the log line is what settles
  the arm.

### Arm 2 (`loading-screen-show-pill=false`)

- Settings written: as arm 1, with `loading-screen-show-pill=false`.
- Discard-rule check: clean. `createGroup SUCCESS` ×1, `SSL handshake complete` ×1, `MATCH! Starting
  AapService` ×0, `Magic Garbage` ×0.
- Decisive log lines (`v1-arm2-log.txt.gz`):
  - `13:06:56.394 App launched via: Bluetooth auto-start`
  - `13:06:56.697 Auto-connect: begin (Bluetooth auto-start, mode=OVERLAY)`
  - `13:07:05.212 Auto-connect overlay: handshake complete, handing off to projection`
  - `13:07:05.570 AapProjectionActivity.onCreate | HeadUnit for Android Auto (tm) - Copyright 2011-2015 ...`
  - `13:07:05.792 AapProjectionActivity.renderProjectionPill | AapProjectionActivity: status pill step: STARTING_PROJECTION (not shown, the pill is switched off)`
  - `13:07:07.080 AapProjectionActivity.hideLoadingOverlay | Hiding loading overlay after first video frame`
- **Window: 1510 ms.** Begin-to-handoff: **8.515 s.**
- Frame evidence (`v1-arm2-handoff.mp4.gz`): `v1-arm2-mainactivity-cancel-button.png` shows
  MainActivity's own loading screen with the pill off — no step-detail card, but a **"Cancel" button**
  top-right, exactly the brief's stated substitute affordance.
  `v1-arm2-projection-loading-screen-no-pill.png`, sampled inside the window, shows the projection's
  own screen: **only** the AA arrow-logo watermark and spinner — no "Android Auto is starting…" text,
  no Cancel button, no pill in any form. This is a stronger visual result than arm 1's: the text that
  arm 1's projection screen carries is genuinely absent here, confirmed across every sampled frame in
  the window.

**Report answers:**
1. Arm 1: **yes**, an unqualified `AapProjectionActivity: status pill step: STARTING_PROJECTION`
   falls inside the window. The candidate's fix works.
2. Windows: arm 1 1578 ms / connect 4.687 s; arm 2 1510 ms / connect 8.515 s. Both windows are close to
   round 1's own ~275 ms estimate's ballpark of "short but real," and both were long enough this round
   for the frames to at least show the correct screen, if not a distinctly separate pill chip.
3. Neither arm printed `(not shown, the loading screen is down)`.

---

## V2 — The regression the new commit could have caused

**PASS** (both arms)

- Run: `am force-stop` then `am start -n .../MainActivity --es launch_source \"Bluetooth auto-start\"`
  (escaped-quote form — see Setup notes), no phone connection needed or awaited; each arm's log was
  read within ~5 s of launch and the app was then disconnected/force-stopped rather than left running.
- Arm 1 (`auto-start-loading-screen=true`), `v2-arm1-log.txt.gz`:
  `13:14:15.807 App launched via: Bluetooth auto-start`,
  `13:14:16.109 Auto-connect: begin (Bluetooth auto-start, mode=OVERLAY)`.
- Arm 2 (`auto-start-loading-screen=false`), `v2-arm2-log.txt.gz`:
  `13:14:40.957 App launched via: Bluetooth auto-start`,
  `13:14:41.265 Auto-connect: begin (Bluetooth auto-start, mode=PILL)`.

**Report answer:** both lines printed exactly as round 1's A1 arm 2 / A4 did. The one-function change
in `8a1968a3e` did not disturb this path.

---

## Anything the brief did not ask about

- **The `--es <key> "value with a space"` quoting failure (Setup notes) is worth carrying into
  `TESTING-TEMPLATE.md`'s existing quoting-hazard list** the next time that file is touched — it sits
  alongside the already-documented `sh -c`/sed/cp quoting hazards but is a different command
  (`am start --es`) and a different failure shape (silent value truncation plus a stray `pkg=` field,
  not an outright command failure), so it would not be found by grepping for the existing entries.
- **The background NativeAA quiet-hosting stack connects and drives the full status-pill sequence
  (ARMED through STARTING_PROJECTION) even when `beginAutoConnect()`'s own bookend logging never
  fires** (as happened in the first, mis-quoted V1 arm 1 attempt). This reinforces A5's round 1 finding
  that the pill is fundamentally driven by the background service's own state, not by the auto-connect
  overlay's bookend log lines — worth remembering if a future brief tries to gate a run's validity on
  seeing `Auto-connect: begin` specifically, since a real, complete, correctly-handled session can occur
  without it.
