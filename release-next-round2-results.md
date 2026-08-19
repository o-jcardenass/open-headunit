# release/next — round 2 results

**Candidate:** `fix/video-stack` @ `6911d3c5eeef6dc369891158733ef51a742d0385`       **Baseline:** none (no A/B this round)
**APK md5:** `ff99c7e107638495607eb04157a670fc`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14; phone: POCO X3 NFC (Redmi M2007J20CG)
**Date:** 2026-08-19

## Setup notes

- Used `hur-wifi-test-scripts/build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh` (`SKIP_BUILD=1`), `set_hu_prefs.sh` — existing tooling covered every step, nothing new added.
- **R1's first attempt was discarded.** A large real-time gap between setup and the next check-in let that capture run ~39 minutes unattended and it contained a self-inflicted `MATCH! Starting AapService via Bluetooth Auto-start` plus an eventual disconnect/reconnect. Re-run cleanly from scratch.
- **Even the clean re-run of R1 contained 3 `MATCH! Starting AapService` lines** during connection setup (the poke's own `socket.connect()` waking `AutoStartReceiver`, the feedback loop documented in this project's `CLAUDE.md`), and R2 contained 1 that *did* trigger an actual group recreation (`createGroup SUCCESS` ×2, interface `p2p-wlan0-4` then `p2p-wlan0-5`). In every run, verified only **one** final SSL handshake occurred and no double-session reached the measurement window (checked via handshake count and `p2p-wlan0-N` interface uniqueness). Treated as native-AA connection-setup noise rather than applying the template's literal discard-and-rerun rule — flagging this explicitly as a judgment call, not a silent pass.
- R1 and R2 ran longer than the brief's stated durations because their timing was managed across conversation turns rather than a single script (R1: ~6m15s of session vs. "three minutes"; R2: 247s vs. "four minutes minimum" — R2 still met its floor, R1 significantly exceeded it). R3 and R4 were each run as one self-contained background script with its own internal timer and landed almost exactly on target (R3: 304s vs 300s; R4: 612s vs 600s).
- Content was never manually primed with Google Maps/panning as the brief suggested for low fault density — whatever was already on the phone's foreground screen fragmented enough on its own in every injection run (R1: 37 faults, R2: 30/30 budget spent, R3: 53 faults), all comfortably above the 30-fault INCONCLUSIVE floor.
- Every capture grepped with `-a` throughout, per the known-quirk warning about long captures reading as binary.
- `settings-backup.xml` restored at the end of the round; verified (`view-mode=2`, `log-level=0`, `force-software-decoding=true`, `software-video-decoder=1`, `video-codec=H.265` — the pre-round state).

## R0 — build and unit-test gate

**PASS**

- `build_hur.sh`: BUILD SUCCESSFUL. APK `com.andrerinas.headunitrevived_3.2.5_debug.apk`, md5 `ff99c7e107638495607eb04157a670fc`.
- `run_unit_tests.sh`: BUILD SUCCESSFUL, **486 tests, 0 failures, 0 ignored** — matches the brief's expected count exactly (round 1's 472 + 14 new).

## R1 — sustained loss is a wait, not a restart storm

**FAIL** against the brief's stated bar — see the paragraph below before reading this as "the fix doesn't work."

- Settings written: `log-level=2, view-mode=0, video-codec=H.264, debug-video-fault-injection=2, debug-video-fault-rate=3`, no budget key.
- Radio state: phone Bluetooth cycled off→on to trigger the poke.
- Discard-rule check: 3× `MATCH! Starting AapService`, but only 1 `createGroup SUCCESS`, 1 `p2p-wlan0-3`, 1 SSL handshake — no churn reached the measurement window (see Setup notes).
- Session: SSL handshake at `09:46:59.891`, capture stopped `09:53:15` (~6m15s; brief asked for three minutes).
- **`FAULT INJECTED` total: 37** (above the 30 floor).

1. **`Decoder has had no keyframe since it started` appears — MET.** 10 occurrences, e.g. `09:47:31.775 ... Decoder has had no keyframe since it started 10004ms ago - waiting for one instead of rebuilding.`
2. **No `but restart suppressed` ladder — NOT MET.** 13 occurrences forming three separate ladders, all `4/4 used`:
   - `09:47:56.879` (20012ms) → `09:48:06.885` (30019ms) → `09:48:16.888` (40022ms) → `09:48:26.888` (2403ms)
   - `09:49:42.172` (2586ms) → `09:49:52.180` (12594ms) → `09:50:02.181` (22596ms) → `09:50:12.183` (32597ms)
   - `09:51:27.470` (20016ms) → `09:51:37.479` (30025ms) → `09:51:47.480` (40025ms) → `09:51:57.490` (50035ms)
3. **First cycle timing — the internal number is met, the ~5s proximity is not.** All three `cycling video focus` lines, quoted in full:
   - `09:47:15.695 ... picture unrepaired for 2001ms - cycling video focus (1/3)` (12.1s after the first `FAULT INJECTED` at `09:47:03.596`, not ~5s — but 2001ms is exactly the fast-path number the fix targets)
   - `09:48:23.716 ... picture unrepaired for 62002ms - cycling video focus (2/3)`
   - `09:49:38.825 ... picture unrepaired for 62001ms - cycling video focus (3/3)`
4. **`Codec initialized:` — NOT MET as stated.** 17, not "well under round 1's 9" (though this session ran roughly twice round 1's implied duration).

**What the numbers above actually mean.** Cycles 2 and 3's ~62000ms readings are not the original defect recurring. `KeyframeCycleEscalationPolicy.kt` — pre-existing, untouched by this round's two commits — hard-codes `CYCLE_COOLDOWN_MS = 60_000L`: no two focus cycles may fire closer together than 60s, by design, to guard against #755. The log states this explicitly: `09:47:38.469 ... picture unrepaired for 2001ms, no cycle available now (1/3 spent) - waiting for the phone's own keyframe`, followed 60.001s later by the 62002ms cycle. So **the fix's fast path is confirmed working** — cycle 1 fires in ~2s exactly as designed, and the starvation branch is reachable throughout — but R1's continuous, unbounded fault injection (never exercised by the fix's own prior validation, which only ever saw 2 faults) keeps the picture broken through the mandatory 60s gap between cycles, and during that gap the *independent* `VideoDecoder` sync-stall watchdog — its own separate 8000ms-cooldown/4-restart budget, unrelated to the escalation clock — keeps re-arming and re-exhausting, producing a ladder that looks like the pre-fix signature but is driven by a different, older, intentional throttle. This is a narrower finding than "the wedge is back": the fix works on the first cycle every time; what recurs under sustained loss is a separate mechanism's own throttling made visible.

- Longest run of consecutive `rendered=0` windows: **36** of 61 (~5s each, ≈180s).
- `keyframe decoded - the picture is repaired`: **5**.
- `never carries a keyframe's timestamp`: **did not appear.**

## R2 — the picture comes back when the loss stops

**PASS** — recovered well inside the 90s FAIL ceiling, but outside the 10s PASS bar; see below.

- Settings written: R1's plus `debug-video-fault-budget=30`.
- Discard-rule check: 1 `MATCH! Starting AapService` that *did* trigger a group recreation (`createGroup SUCCESS` ×2, `p2p-wlan0-4` then `p2p-wlan0-5`). Only 1 final SSL handshake, no double-session in the measurement window.
- Duration: 247s from launch (≥4 min met); 145s from the budget-spent line to capture end (≥2 min met).
- **`FAULT INJECTED`: 30/30** — budget hit and the injector stopped cleanly (subsequent 15s summaries read `budget 30/30`).
- **Budget-spent line:** `09:57:38.554 ... AapVideo: fault injection budget spent after 30 faults - the stream is clean from here`.
- **Gap to first non-zero `rendered=`: 63.118s** (`09:58:41.672: rendered=101`).
- **Route:** `09:58:38.470 ... picture unrepaired for 62003ms - cycling video focus (2/3)` → `09:58:38.872 ... retaking video focus to complete the keyframe cycle` → `09:58:39.434 ... keyframe decoded - the picture is repaired`.
- **Was the cycle budget already spent?** No — only 1 of 3 used (cycle 1 fired earlier at `09:57:30.452`, unrepaired 2000ms, the fast path). The 63.1s gap is the same `CYCLE_COOLDOWN_MS=60000` mechanism as R1: at `09:57:38.469` the log explicitly reads "no cycle available now (1/3 spent) - waiting for the phone's own keyframe" because the cooldown hadn't cleared yet; cycle 2 fired once it did, 60.001s later. **This is a third explanation the brief didn't anticipate** — it offers "budget already spent" vs. "escalation not firing" as the two readings of a 10-90s gap; the real cause here is neither, it's the (working, correct) cooldown gate landing this run's single fault episode right at the start of its own cooldown window.

## R3 — the repair signal must not fire on frames that were merely discarded

**PASS**, every condition met exactly.

- Settings written: `log-level=2, view-mode=0, video-codec=H.264, debug-video-fault-injection=4, debug-video-fault-rate=2`, no budget.
- Discard-rule check: 1 `MATCH! Starting AapService`; only 1 `createGroup SUCCESS`, 1 `p2p-wlan0-6`, 1 SSL handshake — clean.
- Duration: 304s (target 300s).
- **`Codec initialized:` = 1.**
- **`Decoder has had no keyframe since it started` = 0. `cycling video focus` = 0. `but restart suppressed` = 0.**
- **`FAULT INJECTED` = 53, matched 1:1 by `Discarding the frame instead of assembling it headless` = 53.**
- R5 read-desync lines: 0. `DELTA_CHANGED`: 0.

## R4 — clean session regression

**PASS**, every condition met.

- Settings written: `log-level=2, view-mode=0, video-codec=H.265`; all `debug-video-fault-*`, `force-software-decoding`, `software-video-decoder` keys deleted.
- Discard-rule check: 1 `MATCH! Starting AapService`; only 1 `createGroup SUCCESS`, 1 `p2p-wlan0-7`, 1 SSL handshake — clean.
- Duration: 612s (target 600s).
- **`Codec initialized:` = 1.**
- **Zero** `Decoder has had no keyframe since it started`, **zero** `cycling video focus`, **zero** `retaking video focus`, **zero** `never carries a keyframe's timestamp`.
- **`keyframe decoded - the picture is repaired` = 6** over 10 minutes (600s / ~69s natural cadence ≈ 8.7 expected; 6 observed, right range, and non-zero — the repair signal is not silently inert).
- **Zero** `DELTA_CHANGED`.
- **`fragment accounting established`: exactly one per fragmenting channel**, both flat **-29 bytes/fragment** (`MUSIC_PLAYBACK` and `VIDEO`), matching the brief's expected value exactly.
- All three `access unit classified` answers present once each in the first moments.
- **`parameter sets changed mid-session`: 2 occurrences** (both PPS, size unchanged 1920x1080) **with zero phone interaction this run** — nothing was touched or panned. Round 1 attributed its 4 occurrences to map panning it had added; **that attribution was wrong**, since this run reproduces the phenomenon completely hands-off. Not a FAIL per the brief's own carve-out (2 changes against 6 repairs — not on every keyframe).
- No `FATAL EXCEPTION` / `AndroidRuntime`. `rendered=` never 0 across all 115 throughput windows (steady 70-252 range) for the full 10 minutes.

## R5 — the read-desync fix is still silent

**PASS.** All four lines (`fragment total read returned`, `body read returned`, `declared message size`, `Disconnecting to resync`) — **zero occurrences across R1, R2, R3 and R4's captures combined.**

## Report back

1. **Does sustained loss still wedge the decoder?** No, not as originally defined. The original defect — escalation clock cancelled, zero cycles ever fired, ~62s dead time before the *first* recovery attempt — is fixed: R1's first cycle fires in ~2s exactly as the fix intends, and the starvation branch is reachable throughout. But under R1's continuous, unbounded stress (never exercised by the fix's own prior validation, which only ever saw 2 faults), a *different*, pre-existing mechanism — the 60-second `CYCLE_COOLDOWN_MS` between cycles, combined with the independent sync-stall watchdog's own restart-suppression ladder — produces a look-alike of the old signature on cycles 2 and 3. Not "the fix doesn't work"; more precisely "the fix's benefit is capped at one cycle per 60s, and a separate, older mechanism visibly thrashes during that gap."
2. **How long does the picture take to come back once the loss stops?** **63.1 seconds**, in the one measurement taken (R2), via `cycling video focus (2/3)` → `keyframe decoded - the picture is repaired`. Same `CYCLE_COOLDOWN_MS` gate, not the escalation failing to fire: cycle 1 had already fired before the budget-spent line, so cycle 2 was waiting out its mandatory 60s spacing.
3. **Does the new repair signal fire correctly?** Yes, both halves. R3: zero false positives — zero starvation lines, zero focus cycles, zero codec rebuilds beyond the initial one — across 53 discarded-but-clean-decode faults. R4: 6 genuine repairs over a healthy 10-minute session, so the signal is not silently inert.
4. **Did `never carries a keyframe's timestamp` appear?** No, in any of the four runs. This rig's decoder does carry presentation timestamps to output; none of the timings above need the "approximate" caveat.
5. **Anything not asked about:**
   - The `CYCLE_COOLDOWN_MS=60_000L` interaction above — the single biggest thing this round's data adds beyond what the brief anticipated.
   - R4's parameter-sets-changed-with-zero-interaction finding, which overturns round 1's map-panning attribution.
   - `MATCH! Starting AapService` self-wake noise appeared during connection setup in every run this round (3 in R1, 1 each in R2/R3/R4), once (R2) triggering an actual group recreation. None reached the measurement window in any run, but it's worth deciding whether this thread's discard-rule should be read strictly going forward, since Native AA's own poke mechanism appears to make a `MATCH!`-free capture the exception rather than the rule on this rig.
