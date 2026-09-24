# release/next — round 6 results

**Candidate:** `fork/fix/video-stack` @ `9a1257ca`       **Baseline:** none (no A/B this round)
**APK md5:** `cc822ea4cdcd0402c0eda2c9227b28b3`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14; phone: POCO X3 NFC (Redmi M2007J20CG)
**Date:** 2026-08-19

## Setup notes

- `recv_gaps.py` was missing from `hur-wifi-test-scripts/` (the round 1 write-up of the
  `link-stall-periodic-scan` thread said it had been saved there, but it wasn't present at the
  start of this round). Re-saved verbatim from `link-stall-periodic-scan-round1-brief.md` §5 before
  R4. Confirmed unchanged against that brief's source.
- Used `hur-wifi-test-scripts/build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh`
  (`SKIP_BUILD=1`), `set_hu_prefs.sh`, `banner_watch.sh` — existing tooling covered every step
  except the settings-restore-between-runs step, where `run-as $PKG sh -c 'cp ...'` failed
  (`can't create shared_prefs/settings.xml: No such file or directory` — same class of failure
  `TESTING-TEMPLATE.md` already documents for inline `sh -c`), fixed by pushing a tiny
  `restore_settings.sh` (`cp /data/local/tmp/settings-restore.xml shared_prefs/settings.xml`) and
  running it via `run-as $PKG sh /data/local/tmp/restore_settings.sh`, same pattern as
  `set_pref.sh`. Left in `round-release-next/round6/` alongside this file, not in
  `hur-wifi-test-scripts/` itself since it's round-scoped (one-line, references a round-specific
  backup file).
- **R5's UI tap needed two failed attempts before it landed.** The brief's authorized hand-setup
  (§3: switch `wifiConnectionMode` to Native via the Settings search field, one tap) was first
  attempted by this session via `adb shell input tap` immediately after `input text` — that tap
  landed back in the search `EditText` itself (kept typing into it: the field ended up reading
  "Wireless Modey"), not on the segmented button below, because the keyboard was still up and the
  first tap after typing only dismisses it. Cleaned up (`KEYCODE_MOVE_END` + repeated `KEYCODE_DEL`,
  verified via `uiautomator dump` that "Wireless Helper" was still checked and the Save button still
  read `enabled="false"` — no accidental change reached the model). A second scripted attempt at the
  verified correct coordinates, with the keyboard confirmed dismissed, *still* didn't register on the
  segmented `CompoundButton` (bounds and bounds-center were correct; the tap simply didn't take on
  that specific custom control over `adb input tap`). Handed the two taps (Native, then Save) to the
  user directly rather than keep guessing; confirmed done, then read `wifi-connection-mode=3` back
  from `settings.xml` and cross-referenced the exact commit timestamp from the log
  (`AapService: Initializing WiFi Mode: 3 (Strategy: 1)` at `17:47:00.183`). Recorded below.
- **R3 needed the brief's own authorized remedy: moved the map, extended past 6 minutes.** See R3.
- Settings verified by reading `settings.xml` back before every run. `settings-backup-original.xml`
  restored at the end and diffed byte-for-byte identical to the pre-round capture (`view-mode=2`,
  `log-level=0`, `video-codec=H.265`, `wifi-connection-mode=3`, `helper-connection-strategy=4`,
  `force-software-decoding=true`, `software-video-decoder=1`, no `debug-video-fault-*` keys).
- Discard-rule check applied per round 3/4's correction: counted SSL handshakes and `p2p-wlan0-N`
  indices reaching each run's own measurement window, not raw appearances of `MATCH!` or an index
  bump from a *pre-launch* stale-interface teardown (R1 showed exactly that: `p2p-wlan0-0` torn down
  in the first two seconds of capture, before our own `createGroup SUCCESS`, followed by a single
  clean `p2p-wlan0-1` for the whole measured session — not a second group). R1, R2, R3, R4 were each
  single-session, single-handshake, single-`createGroup`, single-interface-index captures — clean.
  **R5 is a deliberate exception by design**: it forms a mode-2 group (`p2p-wlan0-5`) and then, per
  the brief's own protocol, tears it down and forms a second, mode-3 group (`p2p-wlan0-6`) mid-capture
  when the hand-tap switches the mode. That second group and its own single SSL handshake are the
  run's actual subject, not contamination.

## R0 — build and unit-test gate

**PASS**

- `build_hur.sh`: BUILD SUCCESSFUL. APK md5 `cc822ea4cdcd0402c0eda2c9227b28b3`.
- `run_unit_tests.sh`: BUILD SUCCESSFUL, **525 tests, 0 failures, 0 errors, 0 skipped** — matches the
  brief's expected count exactly (round 5's 513 + 8 for `LinkGapMonitor` + 4 for the reserved cycle).
  `LinkGapMonitorTest` and `KeyframeCycleEscalationPolicyTest` both present and passing.

## R1 — the reserved cycle

**FAIL**, both conditions.

- Settings written: `log-level=2, view-mode=0, video-codec=H.264, wifi-connection-mode=3 (as usual),
  debug-video-fault-injection=5, debug-video-fault-rate=3, debug-video-fault-budget=30`.
- Discard-rule check: clean (see Setup notes on the `p2p-wlan0-0`→`-1` boundary). One SSL handshake,
  one `MATCH!` (this session's own launch).
- **Condition 1 — FAIL: zero `holding the cycle until it settles` lines anywhere in the run.** The
  reserved cycle was never held. It also never fired a 3rd time — only `(1/3)` and `(2/3)` appear.
  Every `picture unrepaired` check in the run, in full:
  ```
  17:00:30.871  picture unrepaired for 2002ms - cycling video focus (1/3)
  17:00:38.858  picture unrepaired for 2000ms, no cycle available now (1/3 spent) - waiting for the phone's own keyframe
  17:01:38.859  picture unrepaired for 62002ms - cycling video focus (2/3)
  17:02:09.069  picture unrepaired for 2000ms, no cycle available now (2/3 spent) - waiting for the phone's own keyframe
  ```
  No further check of any kind appears after `17:02:09.069`, through the end of the capture.
- **Condition 2 — FAIL: still `rendered=0` well past 60s after budget-spent, and the repair is
  attributable to the decoder's own restart ladder, not the escalation.** Timestamp chain:
  ```
  17:01:39.565  AapRead: fault injection budget spent after 30 faults - the stream is clean from here
  17:01:38.859  (immediately before) cycling video focus (2/3) - the last cycle this run ever spent
  17:01:39.466  keyframe decoded - the picture is repaired          <- brief flicker, 8 frames over the next 5s window, then dark again
  17:02:07.066 / :22.171 / :37.273 / :52.373   Decoder stopped: restart: sync_stall  (4 more restarts; a first batch of 4 had already run 17:00:28-17:01:07, "restart suppressed, 4/4 used" logged 17:01:27-17:01:57)
  17:02:52.422  Codec initialized: (8th of the run)
  17:02:57.137  keyframe decoded - the picture is repaired          <- the actual, durable repair
  17:02:57.437  Throughput window: first non-zero rendered=17
  ```
  **Budget-spent → repair: 77.6s** (`17:01:39.565` → `17:02:57.137`). At the 60s mark
  (`17:02:39.565`) `rendered=0` was still true (confirmed by the Throughput window ending
  `17:02:47.362`, which covers it, at `rendered=0`). The FAIL bar is explicit on this point and both
  its conditions are met on the nose: no `(2/3 spent)` hold anywhere, and still dark 60s after
  budget-spent.
- **Why the reserved cycle was never reached, traced through `AapTransport.kt` and
  `KeyframeCycleEscalationPolicy.kt`:** a decoder rebuild re-arms the unrepaired clock (`decide()`'s
  callers include the `sync_stall` restart path, per the class doc's "who arms the clock" section),
  and each of those re-arms schedules a fast 2s recheck. The `17:02:09.069` check landed inside
  `CYCLE_COOLDOWN_MS` (60s from the `17:01:38.859` cycle), so `decide()` correctly returned `NUDGE`
  — not a bug on its own. But the *retry* scheduled after a `NUDGE` is a flat `CYCLE_COOLDOWN_MS`
  (60s) from that check, i.e. from `17:02:09.069`, landing at `17:03:09.069` — not anchored to when
  the cooldown itself actually clears (`17:02:38.859`). Three more decoder rebuilds happened in
  between (`17:02:22`, `17:02:37`, `17:02:52`), but each hit `triggerFocusCycleRecovery()`'s early
  return (`unrepairedSinceMs != 0L`, already armed) and re-armed nothing. The picture then
  self-recovered via the 8th `sync_stall`/codec-init pair at `17:02:52-57`, **34 seconds before**
  the next scheduled escalation check would have run at all. The reserved cycle's own logic
  (`WAIT_FOR_QUIET`, rechecked every 2s once reached) never got a chance to run in a state where it
  could matter, because the coarser 60s NUDGE-cooldown retry was still pending when a different
  mechanism finished the job first. This is consistent with, not contradictory to, round 5's finding
  — the specific way it stayed unreachable is new information this round adds.
- **Also recorded:** `DELTA_CHANGED on VIDEO` = 13. `fragment run lost bytes` = 16. `FAULT INJECTED`
  = 30/30. `Codec initialized:` = 9. Time from launch (`16:59:45`) to budget-spent
  (`17:01:39.565`) = **114.6s**. `cycling video focus`, full list: warm-relaunch at `17:00:30.291`
  (6888ms, at session start), then the two escalation cycles above ((1/3) 2002ms, (2/3) 62002ms).
  Renderer confirmation banner appeared once (`17:00:30.264`, "the phone is streaming and nothing
  has drawn"), dismissed by `banner_watch.sh` within 4s.

## R2 — the control, run immediately after R1

**PASS on the zero-counts** (mode 2 is blind to the hole by construction, confirmed again); **the
recovery-gap comparison to R1 is void by the brief's own rule, and the underlying black-screen
duration is the biggest single finding of the round.**

- Settings written: `log-level=2, view-mode=0, video-codec=H.264, wifi-connection-mode=3,
  debug-video-fault-injection=2, debug-video-fault-rate=3, debug-video-fault-budget=30`. Same screen
  state as R1 (default post-connect AA dashboard), reached the same way (force-stop, settings write,
  fresh launch — no deliberate navigation).
- **Zero** `DELTA_CHANGED on VIDEO`, **zero** `fragment run lost bytes`, **zero**
  `holding the cycle until it settles` for the entire run — exactly as designed.
- **Time-to-budget-spent: 725.6s** (launch `17:08:11` → budget-spent `17:20:11.550`) against R1's
  **114.6s — a 6.3x difference. Per the brief's own instruction, this voids the R1/R2
  time-to-budget-spent comparison.** This is the same failure mode round 5's R3 hit (that time 1029s
  vs round 4's 393s, also voided) — a static/slow-changing AA screen makes mode-2's frame-type
  matching take far longer to accumulate the needed candidate count, independent of anything this
  candidate changes.
- **The recovery gap, reported as the brief asked: budget-spent → repair = 75.8s**
  (`17:20:11.550` → `17:21:27.358`, matching keyframe-decoded; first non-zero `rendered=` follows in
  the window ending `17:21:32.250`). **But this dramatically understates what actually happened on
  the rig.** The picture had already gone dark **8.5 minutes before budget was even spent** — the
  last repair before this stretch was at `17:11:22.743`, and it did not repair again until
  `17:21:27.358`, a **continuous 604.6s (10m 4.6s) black screen**, confirmed independently by the
  user watching the physical rig mid-run ("the screen is black") while this session was mid-capture.
  Through that entire 604.6s stretch, `DELTA_CHANGED`/`fragment-lost`/hold counts stayed at zero the
  whole time — the app had no idea anything was broken, because mode-2 corruption never trips the
  reader-stage signal that arms the escalation clock at all. Recovery, when it came, was via the
  decoder's own `sync_stall` ladder: **30 `Codec initialized:`, 29 `sync_stall` restarts** across the
  run, cycling every ~15s with a `4/4 used, 8000ms cooldown` restart-suppression ladder visibly
  running dry and refilling repeatedly (`Need I frame!` / `failed to decode video frame, stream
  error` repeating in the raw `C2UnisocAvcDec` log throughout the dark stretch, including *after*
  budget-spent — the underlying decode failures did not stop just because fault injection did).
  This is round 5's R3 outlier (253.9s, already flagged as "a major outlier") recurring at more than
  double the magnitude, on the same "mode 2, default screen" setup, in consecutive rounds — worth
  treating as a real, repeatable property of this fault mode rather than a one-off.
- Discard-rule check: clean (one `MATCH!`, one `p2p-wlan0-2`, one SSL handshake, one createGroup, zero
  Magic Garbage, zero read-desync lines).
- Renderer confirmation banner appeared once, dismissed automatically within 3s.

## R3 — the isolated drop did not get taxed

**INCONCLUSIVE.** Fewer than 2 `FAULT INJECTED` even after extending well past the brief's 6-minute
window using its own authorized remedy.

- Settings written: `log-level=2, view-mode=0, video-codec=H.264, wifi-connection-mode=3,
  debug-video-fault-injection=5, debug-video-fault-rate=300, debug-video-fault-budget=2`.
- At the 6-minute mark: 33 candidates seen, **0 injected** — exactly the INCONCLUSIVE scenario the
  brief predicted, candidates climbing but not landing. Per the brief's instruction ("move the map
  and extend rather than lowering the rate"), swiped the head unit's screen (`input swipe`,
  minimal/scripted, on the AA video surface — no scriptable trigger exists for panning the phone's
  map) roughly every 20-30s and extended the run to **~11.4 minutes total**. Candidates climbed
  slowly and unevenly with the swiping (33→40→44→50→54→58) but plateaued; **`FAULT INJECTED` stayed
  at 0 throughout.** At 1-in-300 sampling, 58 candidates has an expected value of ~0.19 injected
  faults — the zero result is statistically unsurprising, not a sign anything is wrong.
- **No conclusion possible on the isolated-drop guard this round.** The candidate accumulation rate
  even under active map motion was too low to reach 2 landed faults in a practical amount of extra
  time; getting there would need either substantially more run time or a screen genuinely busy enough
  to generate far more DROP_MIDDLE_FRAGMENT_IN_READER candidates per minute than this rig's AA
  session produced.
- Discard-rule check: clean (one `MATCH!`, one `p2p-wlan0-3`, one SSL handshake, one createGroup,
  zero Magic Garbage). Zero `holding the cycle` lines, consistent with zero faults ever landing.

## R4 — clean control, three jobs

**PASS, all three.**

- Settings: no injection, `view-mode=0`, `video-codec=H.264`, `log-level=0` (VERBOSE), Native AA
  reached the usual way (`set_hu_prefs.sh` + force-stop, no hand setup). 5.75 minutes.
- **Thread B control — PASS:** `recv_gaps.py`: **0 stalls > 1.2s, 0.0% dead time, 24965 RECV lines
  over 322.8s**, audio delivered at 100.1% of real-time rate. Zero `inbound link quiet` lines from
  `LinkGapMonitor` — the two instruments agree.
- **Thread A clean guard — PASS:** zero `holding the cycle until it settles`, zero
  `cycling video focus`, exactly **one** `Codec initialized:`. The widened quiet window and the
  reserved-cycle change fire on nothing when nothing is broken.
- **The free check on thread B's whole theory — PASS: `grep -c "Discovery active - peer search
  running"` = 0.** A plain, never-touched-mode-2 Native AA session does not arm the discovery loop at
  all. This matters for reading R5: the leak is not "every Native AA session has this," it is
  specific to having passed through mode 2 / strategy 1 first in the same process.
- Discard-rule check: clean (one `MATCH!`, one `p2p-wlan0-4`, one SSL handshake, one createGroup). No
  renderer banner.

## R5 — the experiment

**NEGATIVE result, and a more precise one than the brief anticipated: the leak reproduces exactly as
source-predicted through the mode switch, but it is bounded by the phone's own WiFi association, not
by session length — and never overlapped a live session in this run.**

Setup, exactly per the brief's authorized hand-setup (§3), with the tap execution issue in Setup
notes above:

1. App stopped, wrote `wifi-connection-mode=2`, `helper-connection-strategy=1` (plus this round's
   other R5 keys: `log-level=0`, `view-mode=0`, `video-codec=H.264`).
2. Launched `17:42:25`. Group formed (`P2P Group created (fresh this session)`,
   SSID `DIRECT-TQ-Navegadortz2`, `p2p-wlan0-5`) at `17:42:27.249`. `Discovery active - peer search
   running` began at `17:42:37.269`, confirmed on the 10s cadence through several checks.
3. **Without stopping the app**, switched Wireless Mode to Native via the Settings search field +
   Save. Tap performed by the user after two failed scripted attempts (see Setup notes). Committed
   at **`17:47:00.183`** (`AapService: Initializing WiFi Mode: 3 (Strategy: 1)`, read back from the
   log; `settings.xml` confirmed `wifi-connection-mode=3` afterward).
4. Session connected: new group formed (`5GHz createGroup SUCCESS!`, `p2p-wlan0-6`,
   `DIRECT-G6-Navegadortz2`) at `17:47:00.887`; poke triggered `17:47:00.984`; phone brought back
   from airplane mode at `17:47:52`; **SSL handshake complete `17:47:55.871`**. Session ran 5.75
   minutes from there.
5. `recv_gaps.py` over the full capture.

**Findings:**

- **The loop survived the mode switch unbroken, exactly as the source predicted.** It kept ticking on
  its 10s cadence straight through `17:47:00.183` with no gap: `...17:46:57 (implied by cadence),
  17:47:07.481, 17:47:17.485, 17:47:27.487, 17:47:37.489, 17:47:47.492`. Nothing in `stop()` or the
  mode switch cancelled it — confirms `WifiDirectManager.kt:401`'s own comment and the brief's
  `WifiModePolicy.usesWifiDirect` analysis.
- **But it stopped on its own at `17:47:47.492`, 8.4s before the SSL handshake completed, and never
  logged a session-connected suffix.** `Discovery active - peer search running` = **32 total**, **0**
  carrying `while an Android Auto session is connected`. `Discovery failed:` appeared once, at the
  very start (`reason=2`, before the first group existed — the expected startup "no group yet"
  refusal, not a mid-session one).
- **This refines the brief's stated mechanism.** §2 of the brief reasoned "`isClientConnected` never
  flips [in native mode], because the phone joins out-of-band over Bluetooth so `clientList` stays
  empty" — but the loop plainly did stop, and it stopped right as the phone's real WiFi association
  to the P2P group was completing (credentials are Bluetooth-delivered in native mode, but the phone
  still has to join the group's actual WiFi network before the TCP session can open on port 5288, the
  same as any other P2P client join). That WiFi-level join is what appears to flip
  `isClientConnected` here, in mode 3 same as mode 2 — the loop is not gated on the credential
  *delivery* channel, it is gated on the client actually landing on the WiFi network, which happens
  in both modes. **The leak is real and reproduces exactly as predicted for the ~55s window between a
  mode-2→mode-3 hand switch and the phone's WiFi join** — R4 already showed it is absent on an
  ordinary from-scratch native session — but on this run that window closed before any session was
  live, so the harm here was confined to the pre-connect P2P/poke phase, not an established AAP
  session's audio/video channel.
- **`recv_gaps.py`: 0 stalls > 1.2s, 0.0% dead time, 31966 RECV lines over 415.9s**, audio at 100.0%
  of real time. **`LinkGapMonitor`: zero `inbound link quiet` lines.** The two instruments agree —
  this is the clean-negative case both were built to report, and they report it the same way.
- Discard-rule check: **two groups and one interface-index bump are correct for this run's own
  design** (mode-2 group `p2p-wlan0-5` torn down, mode-3 group `p2p-wlan0-6` formed at the hand
  switch) — not contamination; see Setup notes. One `MATCH!` (this session's own launch), one SSL
  handshake (only the mode-3 session ever completed one). No renderer confirmation banner.

## R6 — read-desync silence

**PASS. Zero** of all four lines (`fragment total read returned`, `body read returned`,
`declared message size`, `Disconnecting to resync`) across R1, R2, R3, R4 and R5's captures combined
— five captures, including R1's full 30-fault mode-5 session and R2's 604.6s black-screen stretch.
The fix stays silent under both fault modes and under a mid-session mode switch.

## Report back

1. **Was the reserved cycle actually held? No.** Zero `(2/3 spent)` holds, and the escalation never
   even reached a third check — see R1's timestamp chain and the source-level explanation of why:
   decoder-rebuild re-arms plus a 60s-from-check (not 60s-from-cycle) NUDGE retry meant the next
   scheduled look was 34s later than the moment the wire actually self-recovered by another route.
2. **What brought the picture back? The decoder's own `sync_stall` restart ladder, same as round 5.**
   R1: the 8th restart/codec-init pair at `17:02:52-57`, 77.6s after budget-spent, with the escalation
   sitting at `(2/3 spent)` and no further check pending until 34s after the fact. This round's answer
   to round 5's open question is: unchanged, still the restart ladder, and now with a concrete
   mechanism for why the reserved cycle specifically never got a look in.
3. **Are R1 and R2 comparable this time? No — 6.3x apart (114.6s vs 725.6s time-to-budget-spent),
   voided per the brief's own rule**, same failure mode as round 5's R3. The more important R2 number
   is not the voided comparison but the **604.6s continuous black screen** that ran mostly *before*
   budget was even spent, invisible to the app's own audit the entire time.
4. **Did the isolated drop still escalate at 2s? Unknown — R3 is INCONCLUSIVE.** 0 faults landed in
   ~11.4 minutes (extended well past the required 6 using the brief's own authorized remedy of
   panning the map), 58 candidates seen, statistically consistent with bad luck at 1-in-300 sampling
   rather than any code behaviour.
5. **Is a clean session untouched? Yes.** Zero holds, zero cycles, one codec init.
6. **Does the discovery loop run without a mode change? No — `Discovery active` count on a plain
   native session: 0.** The leak needs the mode-2-then-mode-3 transition to arm at all.
7. **Does the leak reproduce, and does it silence the link? It reproduces exactly as predicted
   through the mode switch, but self-resolves before any session forms, so no link silencing was
   observed this run.** `Discovery active` = 32, all pre-connect, 0 with the session-connected
   suffix; `recv_gaps.py` and `LinkGapMonitor` both report a clean session throughout.
8. **Does `LinkGapMonitor` agree with `recv_gaps.py`? Yes, on every run this round** — R4 and R5 both
   0 stalls / 0.0% dead time from the script, 0 `inbound link quiet` lines from the monitor. First
   hardware validation of the instrument, and no disagreement to report.
9. **Anything not asked about:** the 604.6s R2 black screen (see R2, and worth its own investigation
   independent of this round's actual subject) and the source-level mechanism for R1's negative
   result (decoder-rebuild re-arm + 60s NUDGE-retry granularity), which the brief's own report-back
   question 2 explicitly invited but which needed reading `AapTransport.kt` and
   `KeyframeCycleEscalationPolicy.kt` directly to answer precisely.
