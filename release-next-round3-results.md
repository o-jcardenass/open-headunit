# release/next — round 3 results

**Candidate:** `fix/video-stack` @ `e1c00ec7471b33dc6dfbfe214a114a5255b29d1b`       **Baseline:** none (no A/B this round)
**APK md5:** `ad898ef16ee48f2dc796880f489e5f08`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14; phone: POCO X3 NFC (Redmi M2007J20CG)
**Date:** 2026-08-19

## Setup notes

- Used `hur-wifi-test-scripts/build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh` (`SKIP_BUILD=1`), `set_hu_prefs.sh` — existing tooling covered every step.
- **The headline finding of this round: zero `holding the cycle until it settles` lines appeared in any run, R1 through R4.** Traced to source, not just inferred from the log: `DROP_MIDDLE_FRAGMENT` (R1 and R2's fault mode, and the one that originally motivated this whole thread) drops a `FLAG_MIDDLE` fragment that `VideoFragmentAssembler.onMessage()` (`VideoFragmentAssembler.kt:100-163`) never notices — a middle fragment going missing doesn't orphan the run, doesn't truncate a previous one, and doesn't touch the first-fragment start-code check, so none of its four `Anomaly` cases fire. `AapVideo.requestKeyframe()` (`AapVideo.kt:112-119`), the only caller of `onFrameCorrupted()` — which is the only setter of `AapTransport`'s `lastWireCorruptionMs` (`AapTransport.kt:251`, called with `escalatable=false`) — is gated on `handleAnomaly()` seeing one of those four cases. Confirmed against both captures: zero `requesting keyframe to recover stream` lines (the log line inside `requestKeyframe()`) across R1 and R2 combined, despite 37 and 59 `FAULT INJECTED` respectively. So `KeyframeCycleEscalationPolicy.decide()`'s `WAIT_FOR_QUIET` branch (`lastWireCorruptionMs != 0L` is its first condition) is structurally unreachable for this exact fault mode — the fault mode the round exists to fix. This is a real gap in the fix's wiring, not a test artifact; every PASS/FAIL verdict below is written with this in mind, per the brief's own instruction that "a pass with zero hold lines is not a pass — it measured a different run."
- **R2's first attempt was discarded and needed four tries to get valid data**, none of them a code problem:
  1. First attempt: only 11 `FAULT INJECTED` in 3 minutes (below the 30 floor) — candidates flatlined at 35 after about a minute.
  2. Second attempt (adding periodic `input swipe` on the head unit's projected screen, at the brief's own suggestion for exactly this situation): no SSL handshake within 90s. Diagnosed as ordinary WiFi-association flakiness (Bluetooth handshake completed fully — `WifiStartResponse status=0` — but the phone never joined the P2P group; `TESTING-TEMPLATE.md` §7a documents this rig's A2DP/WiFi-Direct flakiness as pre-existing).
  3. Third attempt: no handshake again, this time with the app's own diagnostic firing — `NativeAA: The phone has answered 3 wake pokes but has never opened the Android Auto channel... typically this head unit's own OEM/factory Bluetooth module... or another car`. Recovered with a full phone-side Bluetooth disable/enable cycle (documented recovery technique in `TESTING-TEMPLATE.md` §7a for `HeadsetClientService`-adjacent stalls).
  4. Fourth attempt connected, but was still stuck at 13 `FAULT INJECTED` with candidates flat — a screenshot revealed the session was sitting on this app's own **renderer-confirmation dialog** (`pending-renderer-confirm`, "Do you see the Android Auto screen? YES / SWITCH RENDERER") the entire time, never reaching Maps. This is a one-time confirmation with no scriptable trigger (`TESTING-TEMPLATE.md` §0's carve-out for genuinely non-scriptable steps); tapped **YES** once via `adb shell input tap`, which persists `pending-renderer-confirm=false` and did not recur on the next relaunch. A second tap opened Google Maps from the AA launcher.
  5. Fifth attempt (with the dialog cleared) reached a real Maps session and produced clean data — 59 faults, reported below as R2.
- Every capture grepped with `-a`. Settings verified by reading `settings.xml` back before every run. `settings-backup.xml` restored at the end (verified: `view-mode=2`, `log-level=0`, `force-software-decoding=true`, `software-video-decoder=1`, `video-codec=H.265`).
- Discard-rule check applied per this round's own correction (§3 of the brief): counted SSL handshakes and `p2p-wlan0-N` interface indices reaching the measurement window, not the presence of `MATCH! Starting AapService` itself. Every run had exactly one handshake reach the measurement window; R2's clean (fifth) attempt had two `createGroup SUCCESS` events from setup churn before that single handshake, same pattern as round 2.

## R0 — build and unit-test gate

**PASS**

- `build_hur.sh`: BUILD SUCCESSFUL. APK md5 `ad898ef16ee48f2dc796880f489e5f08`.
- `run_unit_tests.sh`: BUILD SUCCESSFUL, **495 tests, 0 failures, 0 ignored** — matches the brief's expected count exactly (round 2's 486 + 9 new).

## R1 — the picture comes back as soon as the loss stops

**FAIL** — but not for the reason a bare FAIL suggests. See the paragraph below; the run measured the pre-existing behavior, not the new fix.

- Settings written: `log-level=2, view-mode=0, video-codec=H.264, debug-video-fault-injection=2, debug-video-fault-rate=3, debug-video-fault-budget=30`.
- Discard-rule check: clean — 1 `createGroup SUCCESS`, `p2p-wlan0-0`/`p2p-wlan0-1` (the `-0` was a prior session's interface tearing down before ours formed, not a second group), 1 SSL handshake.
- Duration: 246s from launch; budget-spent to capture end 196s (both floors met).
- **`FAULT INJECTED`: 30/30**, budget hit cleanly.
- **Budget-spent line:** `11:50:45.689 ... fault injection budget spent after 30 faults - the stream is clean from here`.
- **Gap to first non-zero `rendered=`: 66.889 s** (`11:51:52.578: rendered=76`) — essentially unchanged from round 2's 63.1s on the same fault mode, not improved.
- **Condition 1 (gap ≤ 10s): NOT MET.**
- **Condition 2 (at least one `holding the cycle` line before the budget-spent line): NOT MET — zero appeared in the entire capture.** Per the brief, this means the run measured something other than the fix.
- **Condition 3 (route visible via post-budget `cycling video focus`): NOT MET as specified, but the cycle that did fire is explained.** `cycling video focus (1/3)` fired at `11:50:41.426`, **4.3s before** the budget-spent line — the exact "recovery came from a cycle that fired before the loss stopped" case the brief calls FAIL. `cycling video focus (2/3)` fired at `11:51:49.427`, `62003ms` unrepaired — 60.001s after the prior "no cycle available now (1/3 spent)" check at `11:50:49.425`. This is `KeyframeCycleEscalationPolicy.CYCLE_COOLDOWN_MS` again, identical to round 2's mechanism, because the new `WAIT_FOR_QUIET` gate that was supposed to change this never had a chance to engage (see Setup notes).
- **Also recorded:** `Codec initialized:` = 4. `keyframe decoded - the picture is repaired` = 5. Longest run of consecutive `rendered=0`: 12 of 40 ~5s windows (≈60s). `never carries a keyframe's timestamp`: did not appear.

## R2 — the hold must not starve the budget

**PASS on the two stated conditions, with the same caveat as R1 — zero hold lines, so the ceiling was never actually tested.**

- Settings written: R1's minus the budget key (continuous injection).
- Discard-rule check: clean on the reported (fifth) attempt — 2 `createGroup SUCCESS` from setup churn, 1 SSL handshake reaching the measurement window.
- Duration: 186s (fault-count-gated: minimum 180s, extended only as needed — reached 59 faults right at 183s, no extension required after content was fixed per Setup notes).
- **`FAULT INJECTED`: 59** (comfortably above the 30 floor once the renderer-confirm dialog was cleared).
- **Condition 1 (`cycling video focus` fires at least once): MET.** Twice — `(1/3)` at `12:19:28.826` (2001ms unrepaired) and `(2/3)` at `12:20:36.828` (62002ms unrepaired, the same `CYCLE_COOLDOWN_MS` pattern as R1).
- **Condition 2 (no hold line reports > 60000ms): trivially MET — zero hold lines total, so there was nothing to exceed 60000ms.** This is the same structural gap as R1: `DEFER_FOR_QUIET_LIMIT_MS`'s ceiling was never exercised, because `WAIT_FOR_QUIET` was never entered in the first place.
- **Expected, not scored:** `but restart suppressed` = 5, `Codec initialized:` = 9 — both lower than round 2's 13/17 (shorter effective high-density window this run), consistent with `KEYFRAME_STARVATION_PATIENCE_MS`, out of this round's scope per §3.

## R3 — clean session regression

**PASS**, every condition met.

- Settings written: `log-level=2, view-mode=0, video-codec=H.265`; all debug keys deleted.
- Discard-rule check: clean — 1 `createGroup SUCCESS`, 1 SSL handshake.
- Duration: 609s (target 600s).
- **Zero** `holding the cycle until it settles` — the round's most important pass condition on a clean wire, met.
- **`Codec initialized:` = 1.**
- **Zero** `cycling video focus`, **zero** `retaking video focus`, **zero** `Decoder has had no keyframe since it started`, **zero** `never carries a keyframe's timestamp`.
- **`keyframe decoded - the picture is repaired` = 5** over 10 minutes — non-zero, signal not inert.
- **Zero** `DELTA_CHANGED`. No `FATAL EXCEPTION` / `AndroidRuntime`. `rendered=` never 0 across all 115 throughput windows.
- **`fragment accounting established`: one per fragmenting channel, both flat -29 bytes/fragment** (`MUSIC_PLAYBACK`, `VIDEO`) — matches round 2 exactly.
- All three `access unit classified` answers present once each in the first moments.
- **`parameter sets changed mid-session`: 2 occurrences**, session left completely alone — matches round 2's count exactly (2, with zero interaction both times). Two rounds now agree this rig's encoder reconfigures unaided; settled as a stream property, not something the app or the tester does.

## R4 — corruption alone must not produce a hold

**PASS**, every condition met.

- Settings written: `log-level=2, view-mode=0, video-codec=H.264, debug-video-fault-injection=4, debug-video-fault-rate=2`, no budget.
- Discard-rule check: clean — 1 `createGroup SUCCESS`, 1 SSL handshake.
- Duration: 185s (target 180s, shortened from round 2's 300s per this round's brief).
- **Zero** `holding the cycle until it settles`.
- **Zero** `cycling video focus`, **zero** `Decoder has had no keyframe since it started`, **zero** `but restart suppressed`.
- **`Codec initialized:` = 1.**
- **`FAULT INJECTED` = 22, matched 1:1 by `Discarding the frame instead of assembling it headless` = 22.** Lower than round 2's 53 (shorter window — 3 min vs 5 min — and this rig's per-minute rate varies run to run), but the 1:1 match is exact and the run's purpose (proving corruption-without-consequence produces no hold and no cycle) needs no higher count to answer.

## R5 — the read-desync fix is still silent

**PASS.** All four lines — zero occurrences across R1, R2, R3 and R4's captures combined.

## Report back

1. **How long does the picture take to come back once the loss stops?** **66.9 seconds** (R1), against round 2's 63.1s — no real improvement, because the mechanism meant to produce the improvement never engaged (see below). The budget-spent → `cycling video focus` gap is **not the ~2-4s `CORRUPTION_QUIET_MS` read the brief expected** — the cycle that fired after the budget line fired 60.001s after the *previous* escalation check, the same `CYCLE_COOLDOWN_MS` pattern from round 2, because it was never routed through `WAIT_FOR_QUIET` at all.
2. **Did the hold actually engage?** **No — zero `holding the cycle until it settles` lines in any of the four runs.** Root cause, verified against source: `AapVideo`'s corruption-report path (`handleAnomaly()` → `requestKeyframe()` → `onFrameCorrupted()` → `lastWireCorruptionMs`) is wired to `VideoFragmentAssembler.Anomaly`, which has no case for a `FLAG_MIDDLE` fragment silently dropped mid-run — exactly `DROP_MIDDLE_FRAGMENT`'s behavior, and exactly the failure mode this whole thread exists to fix. The stamp this round's fix depends on cannot be set by the fault that originally motivated it. This is this round's central finding.
3. **Does the hold have a floor?** Unanswered by this data — `cycling video focus` did fire at least once in R2 (twice), so the session was never permanently wedged, but that's the pre-existing `CYCLE_COOLDOWN_MS`/`MAX_CYCLES_PER_SESSION` behavior working as it did in round 2, not the new `DEFER_FOR_QUIET_LIMIT_MS` ceiling — no hold line ever existed to test a ceiling against.
4. **Does a clean stream ever see the new line?** **Correctly zero in both directions.** R3 (no corruption to stamp) and R4 (constant corruption but nothing to hold) both report zero `holding the cycle` lines, so the new code is not incorrectly triggering — it's just also not triggering when it's supposed to.
5. **`parameter sets changed mid-session` on R3, session left alone: 2**, matching round 2's count exactly. Now measured twice with zero interaction; settled as a property of this rig's encoder.
6. **Anything not asked about:** R2 needed four setup attempts before it produced usable data — a WiFi-association flake, an AA-channel-binding stall the app diagnosed itself, and (the one worth carrying forward) this app's own renderer-confirmation dialog silently blocking a session at a near-static screen for the run's entire duration with no log signal distinguishing it from a genuinely connected, idle session. A future round or the app itself might want a log line when `pendingRendererConfirm` is blocking projection, since nothing in the capture said so — only a screenshot did.
