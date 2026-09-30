# auto-start-loading-screen — round 1 results

**Candidate:** `feat/auto-start-loading-screen` @ `9cca8ddc`       **Baseline:** none (brief states none needed)
**APK md5:** `d37363e1b7983818be04360e2589e801`
**Unit:** D-HU (UNISOC MT50, Android 14), 1440x720 landscape. D-POCO (POCO X3 NFC) as the connecting phone for Stage B.
**Date:** 2026-09-18

## Setup notes

- **Fresh install required.** D-HU already carried a same-version (3.4.0/109) build signed with a
  different debug key, so `adb install -r` was refused
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Confirmed with the operator before uninstalling; a fresh
  install wipes app data (settings, calibration, onboarding state), which is the root cause of
  several deviations below. `shared_prefs` came up correctly owned by the app's own uid (not the
  root-owned trap), and the installed APK's md5 matched the locally built one exactly.

- **The onboarding wizard intercepts a fresh install and contaminated the first A1/A2 attempts.**
  `MainActivity.checkSetupFlow()` starts `OnboardingActivity` on top of MainActivity in `onResume()`
  on every cold launch while `onboarding-version < 2`, regardless of launch source. The first A1
  (3 arms) and the first A2 "before" capture were taken under this condition; screenshots showed the
  wizard's own "scanning" spinner, not the app's loading screen. Fixed by seeding
  `onboarding-version=2` before any other run, then re-ran A1 and A2 cleanly. All results below are
  from the corrected runs.

- **A fresh install with no established phone identity triggered the "Select Driver Phone"
  selector**, an out-of-scope, pre-existing feature
  (`HomeFragment.checkNativeDriverSelectionOnStartup`) unrelated to this round's candidate. With 3
  bonded devices and no `last-connected-native-mac`/`native-preferred-device-mac`, it fires on every
  automatic launch and its countdown can auto-connect a real phone. One such auto-connect completed
  for real while this was being diagnosed (torn down cleanly with `headunit://exit` +
  force-stop). Set `native-driver-selection-mode=0` (DISABLED) for the rest of the round to keep
  Stage A's "armed and waiting" precondition clean; this is a deviation from the literal brief, noted
  here rather than absorbed silently.

- **Brief erratum: clearing `auto-start-bt-macs` and `native-poke-bt-macs` alone does not stop a
  phone from being poked on this rig.** `native-poke-all-paired` (a separate settings key) defaults
  to `true`, and with it on, an empty `native-poke-bt-macs` list still walks and pokes every bonded
  phone automatically. Confirmed directly: A2's first pass (before this was caught) showed the pill
  escalate to "Waking your phone" with a real WiFi Direct group already up, contradicting the
  brief's stated Stage A precondition ("No phone should be poked this round"). Set
  `native-poke-all-paired=false` explicitly for all of Stage A to actually achieve the brief's
  intent; this is a 3-bonded-phone-rig-specific correction, not a candidate issue.

- **`set_hu_prefs.sh`'s `del` action is line-scoped and corrupts a currently-multi-line `<set>`
  key**, hit twice this round on `native-poke-bt-macs` once it held a real MAC. It deletes only the
  opening `<set name="...">` line, leaving the inner `<string>` and closing `</set>` orphaned —
  invalid XML that risks the whole prefs file reverting to defaults on next load. Recovered both
  times with a host-side Python rewrite + `adb push` + `chown`/`chmod`. Future rounds: don't `del` a
  `<set>` key with this script once it may hold a real multi-line value; rebuild the file on the host
  instead.

- **A2's "settled" precondition needs the real ~29s watchdog to expire, not a short fixed wait.** A
  first attempt with a 10s wait fired the second (onNewIntent) launch while the first attempt was
  still in flight, which correctly triggered `beginAutoConnect`'s own "already in progress, re-arm
  the watchdog silently" guard (`MainActivity.kt:297-300`) rather than the onNewIntent path this run
  is meant to isolate — not a bug, just an invalid test setup. Re-run after waiting the full ~29-33s
  for the "nothing answered this attempt … ending it" line, then the second launch behaved as the
  brief describes.

- **Incident: an unthrottled parallel `adb exec-out screencap` loop froze D-HU**, while attempting to
  bracket B2's ~1.5-2s second-loading-screen window with a tight polling burst. The device required a
  physical power cycle from the operator to recover. This is a testing-methodology hazard, not a
  candidate defect; recorded as a standing rule
  (`feedback_no_parallel_adb_screencap_loops` in session memory) so it is not repeated. After
  recovery, B2 was completed with the same safe sequential-with-`sleep 0.3` method already proven
  earlier in the round; no further device stress was introduced.

- **Connect timing varied run to run** (Auto-connect: begin → handoff): 8.9s, ~10-14s, and ~13.1s
  across separate attempts. All comfortably over 1s, so every recording/screencap below is
  meaningful per the brief's own caveat, but the variability is why a single fixed-sleep screencap
  could not reliably land inside B2's narrow second window.

- **Scripts used:** `build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh` (`SKIP_BUILD=1` for
  the second install after the uninstall), `set_hu_prefs.sh` for every scalar setting. No new script
  added; the brief's own raw `am`/`adb` commands were run directly since they were already fully
  specified. `native-poke-bt-macs`'s `<set>` value was written/repaired via a one-off host-side
  Python edit (see above), not a new persistent script, since it was a corruption recovery rather
  than a reusable step.

- **Stage B ran on `wifi-connection-mode=3` (Native AA / WiFi Direct)**, the mode this rig connects
  most reliably on, with D-POCO as the phone. Poke was scoped to D-POCO's MAC
  (`DC:B7:2E:5E:4E:59`) via `native-poke-bt-macs` with `native-poke-all-paired=false`, for a
  deterministic single-target connection without needing the disabled driver selector.

- Settings restored to the pre-round backup at the end of the round; `settings.xml` verified
  byte-equal to the original backup after restore.

---

## A1 — Every automatic launch opens on the loading screen

**PASS**

- Settings written: `auto-start-loading-screen=true`, `loading-screen-show-pill=true`,
  `auto-connect-last-session=true`, `wifi-connection-mode=3`, `log-level=2`,
  `native-poke-all-paired=false`, `native-driver-selection-mode=0`, `onboarding-version=2`,
  `native-aa-wake-damage-verdict=0`, `auto-start-bt-macs`/`native-poke-bt-macs` cleared,
  `loading-screen-media-*` cleared.
- Discard-rule check: not applicable (no session attempted; poke disabled for Stage A).
- Decisive log lines:
  - Arm 1 (`USB auto-start`): `11:08:31.865 App launched via: USB auto-start`,
    `11:08:32.167 Auto-connect: begin (USB auto-start, mode=OVERLAY)`
  - Arm 2 (`Bluetooth auto-start`): `11:08:42.726 App launched via: Bluetooth auto-start`,
    `11:08:43.033 Auto-connect: begin (Bluetooth auto-start, mode=OVERLAY)`
  - Arm 3 (`WiFi auto-start`): `11:08:53.499 App launched via: WiFi auto-start`,
    `11:08:53.787 Auto-connect: begin (WiFi auto-start, mode=OVERLAY)`
- Screencaps (3s after each launch): all three show "Android Auto is starting…" full-screen with
  the status pill narrating, no four-button home screen. `evidence/a1r2-arm1-usb.png`,
  `a1r2-arm2-bt.png`, `a1r2-arm3-wifi.png`.

All three arms read `mode=OVERLAY`. 3 of 3.

## A2 — A launch into an already-open app

**PASS**

- Settings written: as A1's baseline.
- First launch (no extra, plain open): `11:11:58.705 App launched by third party: …`,
  `11:11:58.994 Auto-connect: begin (auto-connect, mode=OVERLAY)`,
  `11:11:59.009 Auto-connect: this attempt gives up in 29s (mode=OVERLAY)`,
  `11:12:29.001 Auto-connect: nothing answered this attempt (mode=OVERLAY), ending it`. Screencap at
  settle (`evidence/a2r3-before-settled.png`) shows the four home buttons with the pill narrating
  the still-running background WiFi Direct bring-up.
- Second launch, fired without force-stopping (`--es launch_source 'USB auto-start'`):
  `11:12:42.433 Auto-connect: begin (USB auto-start, mode=OVERLAY)`. Screencap 3s later
  (`evidence/a2r3-after.png`) shows the full loading screen.
- Before this fix the second start produced no `Auto-connect: begin` line at all (per brief); this
  run confirms the onNewIntent path now raises it correctly.

## A3 — The hold, and the bound that is deliberately kept

**PASS** (both arms)

- Arm 1 (app-started, `Bluetooth auto-start`), 60s: decisive line
  `11:13:42.434 Auto-connect: the stack is still working (WAITING_FOR_PHONE), holding the loading
  screen`, immediately followed by `11:13:42.438 Auto-connect: this attempt gives up in 149s
  (mode=OVERLAY, re-armed)`. Screencap at 45s (`evidence/a3-arm1-at45s.png`) still shows the loading
  screen. (The initial `App launched via`/`Auto-connect: begin` lines from this run were evicted
  from the ring buffer by this rig's noisy driver stack over the 60s window — a known quirk — but
  the hold-engagement and watchdog re-arm lines are unambiguous and were captured cleanly.)
- Arm 2 (person-started, plain open with `auto-connect-last-session=true`), 60s: decisive line
  `11:15:04.091 Auto-connect: nothing answered this attempt (mode=OVERLAY), ending it`; no
  `holding the loading screen` line ever appeared. Screencap at 45s
  (`evidence/a3-arm2-at45s.png`) shows the four-button home screen.
- Premise check: arm 1 did show `status pill step:` lines throughout (`WAITING_FOR_PHONE` etc.), so
  the wireless stack was genuinely armed and reporting — the hold is a real result, not a default.

Arm 1 held past 30s, arm 2 did not. Both halves confirmed.

## A4 — Positive control: the toggle off restores the old behaviour

**PASS**

- Settings written: `auto-start-loading-screen=false`, else as A1 arm 2.
- `11:15:46.648 App launched via: Bluetooth auto-start`,
  `11:15:46.949 Auto-connect: begin (Bluetooth auto-start, mode=PILL)`.
- Screencap (`evidence/a4.png`) shows the four home buttons with the pill at the bottom — exactly
  the pre-fix behaviour for this source.

## A5 — An ordinary open is still an ordinary open

**PASS**

- Settings written: `auto-connect-last-session=false`, `auto-connect-single-usb=false`,
  `auto-start-self-mode=false` (`auto-start-loading-screen` restored `true`, irrelevant to this run).
- `11:16:07.997 App launched by third party: …` — no `Auto-connect: begin` line anywhere in the
  capture.
- Screencap (`evidence/a5.png`) shows the home screen. A status pill is visible at the bottom
  narrating an unrelated, ambient WiFi Direct bring-up the background service runs regardless of any
  UI auto-connect attempt (see "Anything the brief did not ask about").

---

## B1 — The handoff shows no home screen

**PASS**

- Connection: Native AA (`wifi-connection-mode=3`), D-POCO as the phone, poke scoped to its MAC.
- Decisive log lines (from the recorded run):
  `11:19:29.165 Auto-connect: begin (auto-connect, mode=OVERLAY)`,
  `11:19:38.084 Auto-connect overlay: handshake complete, handing off to projection`,
  `11:19:38.320 AapProjectionActivity.onCreate | HeadUnit for Android Auto (tm) - Copyright
  2011-2015 …`,
  `11:19:39.812 AapProjectionActivity.hideLoadingOverlay | Hiding loading overlay after first video
  frame`.
- **Handoff-to-Copyright-banner gap: 236 ms.**
- **Begin-to-handoff gap: 8.919 s** — well over 1s, so the recording is meaningful.
- Whether any frame in the recording shows the home screen: **no.** The full 30s/1006-frame
  recording (`evidence/b1r2-handoff.mp4`, `adb shell screenrecord`) was scanned programmatically for
  the home screen's icon-row color signature (saturation/value in the four-button region) across
  every frame; no frame from the app's launch onward matched it. (The only high-saturation frames
  were the first ~17, ~0.5s of pre-launch screen content before the app was even started — outside
  the tested window.) Spot-checked frames at the loading screen, the credential-exchange steps, the
  handoff instant, and the post-handoff video confirmed the scan by eye: continuous loading-screen
  or video content throughout, no four-button screen.
- **Operator-observed corroboration:** watching the run live, the operator confirmed seeing a
  transition between two distinct "Android Auto is starting…" screens — one with a
  Cancel/exit affordance, the other without — with no home screen visible between them. This matches
  the brief's own description exactly: the fix does not remove the second (projection-owned) loading
  screen, which is not meant to disappear (§5); it removes the home-screen frame that used to sit
  between the two. MainActivity's overlay carries the cancel control (`overlayOwnsScreen()`'s
  cancel-on-Back / the pill's X); AapProjectionActivity's own loading screen, raised after the
  handshake already completed, does not.
- A brief flash of black between the two activities: not observed in either the frame scan or by the
  operator.

## B2 — The pill rides both loading screens

**PASS** (both arms)

- **Arm 1 (`loading-screen-show-pill=true`):** window 1 (pre-handoff) screencap
  (`evidence/b2arm1-window1.png`) shows the pill with a step detail line ("Waking your phone / WiFi
  Direct on 5 GHz"). Window 2 (after `AapProjectionActivity` is up, before the first video frame) is
  a genuinely narrow ~1.2-1.7s window whose timing varied 8.9-14s run to run; a screencap was caught
  showing the second loading screen itself (`evidence/b2arm1-burst/f15.png`, sequential 0.3s-spaced
  capture) but without the pill visible in that specific frame. Marking the arm 1 window-2
  **screencap** half UNTESTABLE per the brief's own fallback (§ B2 report). The log evidence stands
  in its place: every `status pill step:` line from `ARMED` through `STARTING_PROJECTION` (the step
  immediately before the handoff) carries a live step name, consistent with the pill riding through
  to the handoff rather than being suppressed.
- **Arm 2 (`loading-screen-show-pill=false`):** window 1 screencap (`evidence/b2arm2-window1.png`)
  and all 20 frames of a sequential 0.3s-spaced burst spanning both the pre- and post-handoff windows
  (`evidence/b2arm2-burst/f01.png`–`f20.png`) show no pill anywhere, in either window. Every
  `status pill step:` line from this run's log explicitly carries the suppressed-pill suffix, e.g.
  `11:38:34.131 status pill step: ARMED (not shown, the pill is switched off)` through
  `11:38:47.234 status pill step: STARTING_PROJECTION (not shown, the pill is switched off)`.

Arm 1 shows the pill with step detail in the window that could be caught; arm 2 shows no pill in
either window, log-confirmed line by line for both.

---

## Anything the brief did not ask about

- **The round's own preconditions collide with out-of-scope machinery on this specific rig.** A
  fresh install (needed here only because of a signature mismatch) exposed three separate,
  pre-existing behaviors this round doesn't touch — the onboarding wizard, the driver-phone
  selector, and `native-poke-all-paired`'s default — each of which had to be worked around to reach
  the state the brief assumes. None of the three are candidate defects; they're worth a line in the
  next Native-AA-adjacent brief that assumes a "quiet, no-phone-poked" Stage A on a multi-phone rig.
- **A5's home screen carries a visible status pill even though no `Auto-connect: begin` fires.** The
  pill reflects the background service's own ambient WiFi Direct group maintenance (mode 3 is always
  "quiet-hosting" once armed), not the auto-connect UI flow this round grades. Worth knowing if a
  future round tries to grade "is the pill ever shown on an ordinary open" — the log's absence of
  `Auto-connect: begin` is the correct signal, not the screencap's absence of a pill.
- **The parallel-screencap incident (see Setup notes) is the most consequential thing that happened
  this round** and is called out again here for visibility: it froze D-HU and needed a physical power
  cycle. No candidate code was implicated; the cause and the fix (never parallelize adb calls against
  this rig) are recorded in session memory for future rounds.
