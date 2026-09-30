# wire-corruption-escalation — round 1 results

**Candidate:** fix/wire-corruption-escalation @ 96eefddb (4 commits)       **Baseline:** origin/main @ 562c8dcf
**APK md5:** candidate 5b828598e4ea993b4181b6272c4b3e0e / baseline b564119c23a8e37e2a013ed50e6b39d9
**Unit:** UNISOC MT50_YT610E4GFPSL_U, Android 14, head-unit-make: Google (as found in settings.xml)
**Date:** 2026-08-20

## Setup notes

`hur-wifi-test-scripts/` inventory taken at round start: used `build_hur.sh`, `run_unit_tests.sh`,
`set_hu_prefs.sh` (multi-key writer) for every settings change. No new script was needed.

**Two head-unit reboots interrupted the round, neither a code defect:**

1. Before R1a, the head unit rebooted spontaneously (confirmed via `uptime` reading `up 0 min`
   immediately after). Settings, bonding and the installed APK all survived; the round proceeded
   after re-verifying state.
2. Mid-R3, the head unit rebooted again. `dumpsys battery` showed `level: 1`, `AC powered: false`,
   `status: 4` (not charging) — the rig was running on PC USB power alone, insufficient to sustain
   video/WiFi/Bluetooth load. Paused and asked the user to connect proper power; battery gauge still
   read 1%/not-charging afterward (likely an unreliable gauge on a normally-hardwired unit, per the
   user's confirmation to continue), and no further reboot occurred for the rest of the round. R3 was
   restarted cleanly from scratch after this; nothing from the interrupted attempt is reported below.

**R2 and R3 both fell short of the fault counts the brief asked for** (3 and 4 respectively) inside a
generous wait window — R2 got 2/3 in ~18 minutes, R3 got 2/4 in the 15-minute cap. Both showed the
same signature: `fault injection - ... candidates seen` stalling for 60-70s at a time while
throughput stayed healthy (49-50fps) — an idle-screen candidate trickle, the same phenomenon the
media-gap-instrument thread already documented for a different signal. Numbers are reported as
measured, with the shortfall stated rather than extending the round indefinitely to force a third or
fourth sample.

**R4's first attempt was discarded per the discard rule**: `createGroup SUCCESS` count was 4 and
`MATCH! Starting AapService` was 2, from a self-inflicted poke/wake loop during the clean-run
settle window. All of that churn happened before the run's single SSL handshake, and the discarded
attempt's measurements independently matched the re-run in every respect, but the letter of the rule
("more than 1 `createGroup SUCCESS` in a run is the discard") was honored: re-ran R4 from scratch
with a verified single, stable group before proceeding, and that clean attempt is what is reported.
The discarded attempt's numbers are noted below only as corroboration.

**Log export has no scriptable trigger** (`stableId = "exportLogs"` is a `SettingEntry` inside the
flat `settingsFragment` list, not a nav-graph destination or exported intent action) — driven via the
in-app "Search settings" field per `TESTING-TEMPLATE.md` / `CLAUDE.md`'s exception for controls with
no automation surface, minimum taps. One real pitfall found doing this: **the on-screen keyboard
occludes the results list on this 1440x720 screen** (IME inset bottom=320px) — a tap intended for a
result row while the keyboard is still up lands on the keyboard instead and mistypes the search text.
Always `input keyevent 4` to dismiss the keyboard and confirm via screenshot before tapping a result.
Also noted: the "Start/Stop Capturing Logs" row's **label** can lag the live `isCapturing` state
across a fresh `SettingsActivity` relaunch (showed "Start Capturing Logs" while a capture was still
actively growing its file) — the click handler itself branches on live state correctly regardless of
the stale label, but the label cannot be trusted as ground truth immediately after reopening
Settings; verify via the capture file's growth (`ls -la`, twice, a few seconds apart) instead.

Diff against the pre-round `settings.xml` backup: `wifi-connection-mode=3`, `log-level=2` and
`video-codec=H.265` were already in place at round start (delta zero on those); only the
`debug-video-fault-*` keys were added/changed per run, and all were cleared at the end. Full backup
restored via the pushed-script `cp` pattern after the round.

## R0 — build and unit-test gate

**PASS**

- Clean build of `96eefddb` (github debug) and baseline `562c8dcf`; APK md5s recorded and different.
- 571/571 unit tests green.
- `AapMessageFramingTest`: 4/4 passing, present (new in `64630ce7`).

## R1a — calibration (no verdict)

Mode 3 (`DROP_LAST_FRAGMENT`), rate=100, budget=0, INFO, 3-minute capture, default post-connect
screen.

- Injector announced `FAULT INJECTION IS ON` at 0 candidates seen (18:52:45.461); reached 175
  candidates seen by 18:55:45.598 (180.137s elapsed).
- **Measured rate: 0.971 flag-10 candidates/sec.**
- **R1's chosen rate: 87** (90 × 0.971 ≈ 87.4). Well above the 10-candidate floor, no extension needed.

## R1 — the point of the round: one truncated frame, repaired

**PASS**

- Settings: `debug-video-fault-injection=3`, `debug-video-fault-rate=87`, `debug-video-fault-budget=1`, `log-level=2`.
- Radio state: phone Bluetooth cycled off before launch, back on after ~18s settle (airplane mode is
  not togglable on this phone per `TESTING-TEMPLATE.md` §7a; this is the documented substitute).
- Discard-rule check: clean — `createGroup SUCCESS`=1, one `p2p-wlan0-2` interface, `SSL handshake
  complete`=1, `Magic Garbage`=0. One `MATCH! Starting AapService` with zero attached group churn
  (the phone's own benign reconnect, per the refined discard rule).
- Decisive lines, quoted:
  ```
  18:57:47.188  FAULT INJECTED (#1 of 87 candidates): DROP on flag 10, len=2261
  18:57:47.197  Previous frame was truncated! Resetting assembly state.
  (no AapRead: framing-audit line attributable to this fault)
  18:57:49.203  picture unrepaired for 2001ms - cycling video focus (1/3)
  18:57:49.605  retaking video focus to complete the keyframe cycle
  18:57:49.879  keyframe decoded - the picture is repaired
  ```
- **Truncation-to-repair interval: 2682 ms** (18:57:47.197 → 18:57:49.879).
- Paired throughput over the same window: 44-46fps, `dropped=0`.

## R2 — the same fault on the baseline

**PASS** on the substance the brief asked for; median-of-three not achieved (deviation, not a failure)

- Settings: same as R1 except `debug-video-fault-budget=3`, on baseline `562c8dcf`.
- Only 2 of 3 faults landed in ~18 minutes of capture (idle-screen candidate stall — see Setup notes;
  throughput stayed healthy at 49-50fps the whole time, so the stall is not a stream problem).
- Fault 1 truncated 19:01:15.291; fault 2 truncated 19:01:47.773 — only 32.5s later, **before fault 1
  had healed**, so only one independent repair-interval sample was obtainable rather than three.
- **Zero `cycling video focus` lines across the entire ~20-minute capture.**
- First (only) repair came via the natural GOP cadence: 19:02:23.944 — **68.65s after fault 1 / 36.17s
  after fault 2.** The natural GOP repeated every ~72s for the rest of the capture (17 keyframes
  logged, evenly spaced), confirming this is ordinary phone-side refresh, not anything triggered by
  the candidate.
- Discard-rule check: clean — `createGroup SUCCESS`=1, `SSL handshake complete`=1.
- Verdict basis: the PASS condition's substance — no escalation, and repair materially slower than
  R1's — is unambiguous: 68.65s vs R1's 2.682s is **>25x slower**, with the picture sitting broken far
  longer than R1's 4s threshold would ever tolerate, and zero escalation lines the entire time. The
  brief's three-independent-sample design was not achieved on this screen state; reporting the actual
  numbers rather than forcing a third sample.

## R3 — the budget, and what a long drive looks like

**PASS** on what fired; **INCONCLUSIVE** on budget exhaustion (only 2 of 4 faults landed)

- Settings: candidate, rate=87, `debug-video-fault-budget=4`.
- First attempt aborted by the mid-run reboot described in Setup notes; restarted from scratch.
- Second (reported) attempt: 2 of 4 faults landed within the 15-minute cap (same idle-screen stall as R2).
  ```
  19:36:10.233  Previous frame was truncated!
  19:36:12.239  picture unrepaired for 2000ms - cycling video focus (1/3)
  19:36:12.906  keyframe decoded - the picture is repaired        → interval 2673 ms

  19:43:24.376  Previous frame was truncated!   (7m14s after fault 1)
  19:43:26.381  picture unrepaired for 2000ms - cycling video focus (2/3)
  19:43:27.063  keyframe decoded - the picture is repaired        → interval 2687 ms
  ```
- The cycle counter correctly carried `(1/3)` → `(2/3)` across the 7m14s gap; both repairs matched
  R1's ~2.68s closely.
- Budget exhaustion — the 3rd and 4th cycle, `no cycle available now (3/3 spent)` — was never
  exercised. Genuinely untested on this run, not a failure, per the brief's own contingency for
  faults that don't land close enough together.
- Discard-rule check: clean — `createGroup SUCCESS`=1 (two `p2p-wlan0` indices seen, `0` and `1`, but
  the bump preceded the first `createGroup SUCCESS` — a stale group torn down at launch, the
  documented benign case). `SSL handshake complete`=1.

## R4 — sustained loss must still hold the cycle

**PASS** on the measured behavior; the "holding the cycle" guard itself was **not exercised**
(the fault storm resolves faster than the escalation's own timer on this screen)

- Settings: candidate, `debug-video-fault-rate=3`, `debug-video-fault-budget=30`.
- **First attempt (discarded, discard-rule hit):** all 30 faults landed in a 1.7s burst
  (19:58:56.153-19:58:57.841). `createGroup SUCCESS`=4, `MATCH!`=2 — self-inflicted poke/wake churn
  during clean-run setup, all before the run's single SSL handshake. Discarded per protocol; numbers
  matched the clean re-run below in every respect and are not otherwise reported.
- **Second attempt (reported, clean):** `createGroup SUCCESS`=1, `SSL handshake complete`=1.
  ```
  20:03:30.636  FAULT INJECTED (#1 of 3 candidates)
  20:03:32.145  FAULT INJECTED (#30 of 90 candidates)      → all 30 faults in 1.509s
  20:03:32.146  fault injection budget spent after 30 faults
  20:03:32.649  picture unrepaired for 2001ms - cycling video focus (1/3)
  20:03:33.052  retaking video focus to complete the keyframe cycle
  20:03:33.328  keyframe decoded - the picture is repaired
  ```
  - Only **1** `cycling video focus` fired (not 3 wasted) — its 2s-unrepaired timer started at the
    *first* truncation, and by the time it fired the storm had already stopped (budget spent 1ms
    earlier), so the repair landed clean on the first try.
  - **Budget-spent-to-repair: 1182 ms** (well under 90s).
  - `dropped=0` throughout both attempts — no confounding decoder-shed arming path.
  - `holding the cycle until it settles`: **0** in both attempts. This specific guard — a cycle that
    lands mid-storm and gets handed a still-corrupt keyframe — never got a chance to fire, because a
    30-fault 1-in-3 storm on this screen's motion level completes in 1.5-1.7s, faster than the
    escalation's own 2s-unrepaired timer. Not a failure of the guard; a genuine gap in what this setup
    can produce on this rig's current screen content. Neither FAIL condition (three cycles wasted
    inside the storm, or no repair after budget spent) was met.

## R5 — clean control, and the banner

**PASS**

- Settings: all `debug-video-fault-*` keys deleted, `log-level=2`, 10-minute session on the default
  post-connect screen.
- Five zeroes, paired with throughput:
  - `Previous frame was truncated`: 0
  - `Decrypted payload too short`: 0
  - `cycling video focus`: 0
  - `Configuring decoder`: exactly 1
  - `dropped=` summed over the session: 0 (over ~10.4 minutes, well under the 0.15/min ceiling)
  - Throughput steady 45-50fps for the entire session.
- Banner check, done two ways in the same live session:
  1. **Capture running:** toggled "Start Capturing Logs" on (via settings search, see Setup notes),
     waited for the capture file to appear and grow, then exported. The export returned the *same*
     file the capture was writing (`HUR_Log_20260820_202427_560.txt`), containing the banner:
     ```
     LogExporter: session | build=3.2.6 (98) github/debug | device=UNISOC MT50_YT610E4GFPSL_U
     board=uis7861_6h10 api=34 | video=codec:H.265 fps:60 resId:3 view:TEXTURE forceSw:false
     swDecoder:BUNDLED_FFMPEG | wifi=mode:3 strategy:2 | logLevel=INFO
     ```
     `build=3.2.6 (98)` matches the installed candidate APK; `video=codec:H.265`, `wifi=mode:3
     strategy:2` and `logLevel=INFO` all match `settings.xml` exactly.
  2. **Ring-buffer path:** stopped capture (confirmed via the file's size freezing across two checks 8s
     apart), deleted its capture file, exported again. Got a fresh, distinct, smaller file
     (`HUR_Log_20260820_202756_034.txt`, 5426 bytes vs the deleted file's 24667 bytes) — the banner
     was present again, same field values.
- Discard-rule check on the underlying AA session, unaffected by all the settings-UI navigation:
  `createGroup SUCCESS`=1, `SSL handshake complete`=1, one benign `MATCH!` with no group churn.

## Report back

1. **R1's truncation-to-repair interval: 2682 ms**, with `cycling video focus (1/3)` present in the chain.
2. **R2: only one usable repair-interval sample (not a median of three) — 68.65s**, no `cycling video
   focus` at all across ~20 minutes. >25x slower than R1's 2682ms; the mechanism the branch fixes is
   clearly absent on the baseline.
3. **R4: `holding the cycle until it settles` did not appear** (storm faster than the escalation
   timer, not a failure); budget-spent-to-repair was **1182 ms**.
4. **R5's five zeroes**: truncated=0, short-payload=0, cycling=0, `Configuring decoder`=1,
   dropped=0/10.4min — paired with steady 45-50fps throughout.
5. **R1a's candidate rate: 0.971/s**, so `debug-video-fault-rate=87` can be reused directly by the
   next round on this screen state without recalibrating.

`grep -c "createGroup SUCCESS"` per reported run: R1=1, R2=1, R3=1, R4=1 (first attempt, discarded,
was 4), R5=1. No unexplained churn in anything reported above.
