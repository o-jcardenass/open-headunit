# release/next — round 4 results

**Candidate:** `fix/video-stack` @ `bbf328e8b0d616d5782ed0fd9664d87a50ddf13b`       **Baseline:** none (no A/B this round)
**APK md5:** `7289f36df111d24c317544158290ef0a`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14; phone: POCO X3 NFC (Redmi M2007J20CG)
**Date:** 2026-08-19

## Setup notes

- Used `hur-wifi-test-scripts/build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh` (`SKIP_BUILD=1`), `set_hu_prefs.sh` — existing tooling covered every step.
- **R1 and R2 are UNTESTABLE.** Mode `5` (`DROP_MIDDLE_FRAGMENT_IN_READER`) kills the connection within seconds of engaging, every time — see the headline finding below. No amount of retrying produces a sustained multi-minute mode-`5` capture, because the fault mode's current implementation makes that structurally impossible. R2 was not attempted for the same reason once R1 established the mechanism on the first try; retrying would only reproduce the same result.
- **The renderer-confirmation banner (`AapProjectionActivity: the renderer confirmation banner is up`) reappeared this round despite `pending-renderer-confirm` staying persisted `false`** from round 3's fix. It showed up on R1's underlying sessions (implicated in the repeated reconnect churn, though the primary cause there is the TLS desync below) and on R3's first attempt, appearing **7.7s after the SSL handshake** — after this round's first banner-detection script's one-shot 3s check window had already passed, which is why it was missed initially. A continuous poll-and-tap loop (added mid-round) caught and dismissed it on R3's second attempt with no further recurrence in R3 or R4. Likely re-arms independent of the persisted setting when the session's own decoder/renderer health looks degraded — plausibly a consequence of the repeated forced reconnects during R1's crash-loop testing leaving some session-scoped "did the renderer break" heuristic tripped. Worth a log line distinguishing *why* the banner re-armed (persisted flag vs. a fresh health check), since right now the two look identical in the capture.
- Settings verified by reading `settings.xml` back before every run. `settings-backup.xml` restored at the end (verified: `view-mode=2`, `log-level=0`, `force-software-decoding=true`, `software-video-decoder=1`, `video-codec=H.265`).
- Discard-rule check applied per round 3's correction: counted SSL handshakes and `p2p-wlan0-N` interface indices reaching the measurement window. R3 and R4 were each single-session, single-handshake captures. (R1's capture had **four** SSL handshakes — itself a symptom of the finding below, not ordinary setup noise, and is reported as such rather than silently discarded.)

## The headline finding: mode 5 desyncs the TLS session, not just the byte framing

`AapReadSingleMessage.doRead()` calls `shouldDropForFaultInjection()` (`AapRead.kt:118`) **after** the message's encrypted body has been fully read off the socket (`recvBlocking`, consuming exactly `recvHeader.enc_len` bytes) but **before** it is handed to `AapMessageIncoming.decrypt()` (`AapReadSingleMessage.kt:118-127`). When the injector says drop, the function returns `0` immediately, discarding the already-consumed bytes and reading the next message. The code's own comment (`AapRead.kt:47-48`) states this is safe: *"Only the bytes already consumed from the connection are discarded, so the stream stays framed — the failure this branch must never cause."*

That is true for the raw TCP byte alignment. It is not true for the TLS session. `AapSslContext.decrypt()` (`AapSslContext.kt:241-274`) calls `sslEngine.unwrap(encrypted, rxBuffer)` — Android's `SSLEngine`, a stateful, ordered protocol engine whose record-layer sequence numbers advance with every record it unwraps. The phone's sending side increments its own sequence number for every record it encrypts and sends, regardless of whether the head unit chooses to decrypt it. Dropping a message **before** `unwrap()` sees it means our `SSLEngine`'s expected sequence number falls one behind the phone's actual one — permanently, for the rest of the session. Every subsequent `unwrap()` call then fails record authentication.

**This is exactly what was captured**, four times in one run:

```
13:34:35.316  SSL handshake complete
13:34:39.039  AapRead: FAULT INJECTED (#1 of 3 candidates)
13:34:39.049  Companion.decrypt | Decrypted payload too short: 0     <- next message, already failing
             ... (storm of the same, worsening) ...
13:34:39.236  AapRead: Connection closed (EOF). Disconnecting.        <- session dead, 3.9s after handshake, 1 fault in
```

Reproduced identically three more times in the same capture (sessions dying after 1, 13, 1 and 4 injected faults respectively — the exact count varies with timing, but the outcome never does). **Zero of R5's four framing-desync lines appeared anywhere in this capture** — confirming the framing genuinely does stay intact exactly as the code claims; the desync is real but happens one layer up, at the TLS record layer, which nothing in this codebase currently guards.

This makes R1 and R2 as specified (a sustained, multi-minute mode-`5` session) structurally impossible with the current implementation. It also means `DELTA_CHANGED on VIDEO` and `fragment run lost bytes` **did** fire during the run (14 and 5 times respectively, across the four short-lived sessions) — the detection and its new recovery wiring are not in question — but the connection never survives long enough afterward to test the hold, the budget, or the recovery-gap timing the round exists to measure.

## R0 — build and unit-test gate

**PASS**

- `build_hur.sh`: BUILD SUCCESSFUL. APK md5 `7289f36df111d24c317544158290ef0a`.
- `run_unit_tests.sh`: BUILD SUCCESSFUL, **505 tests, 0 failures, 0 ignored** — matches the brief's expected count exactly (round 3's 495 + 10 new).

## R1 — the detection fires, and the picture comes back

**UNTESTABLE.** The run's precondition — mode `5` sustaining a connection long enough to spend a 30-fault budget over 4+ minutes — cannot be met. See the headline finding above.

- Settings written: `log-level=2, view-mode=0, video-codec=H.264, debug-video-fault-injection=5, debug-video-fault-rate=3, debug-video-fault-budget=30`.
- What the capture does show, for the record: **`AapRead: DELTA_CHANGED on VIDEO` = 14. `fragment run lost bytes, requesting keyframe to recover stream` = 5** (below the `DELTA_CHANGED` count, consistent with the brief's throttle expectation). **`holding the cycle until it settles` = 0** across all four sub-sessions — but with sessions dying 1-13 faults in, none of them ran long enough for the 2s quiet window or the 60s cooldown to matter either way, so this zero is not comparable to round 3's zero.
- **Total `FAULT INJECTED` (bare token): 23**, spread across 4 sessions — never approached the 30-fault budget in any single session.
- No `rendered=` recovery gap could be measured: the budget was never spent in a surviving session.

## R2 — the hold has a floor

**Not attempted.** Same fault mode as R1; R1 already established the mechanism kills the connection before any 3-minute continuous run is possible. Running it would only reproduce R1's result.

## R3 — the old mode is still blind, which is the point

**PASS**, every condition met, and it did its job as the round's control.

- Settings written: `log-level=2, view-mode=0, video-codec=H.264, debug-video-fault-injection=2, debug-video-fault-rate=3, debug-video-fault-budget=30`.
- Discard-rule check: clean — 1 `createGroup SUCCESS`, 1 SSL handshake.
- Duration: 393s from launch (target: 4 min minimum + 2 min post-budget; both comfortably met).
- **`FAULT INJECTED`: 30/30**, budget hit cleanly (needed a second attempt after the renderer banner blocked the first — see Setup notes).
- **Zero** `DELTA_CHANGED on VIDEO`. **Zero** `fragment run lost bytes`. **Zero** `holding the cycle until it settles` — exactly as expected: mode `2` drops the fragment after the audit has already counted it, so nothing can see the hole, by construction.
- **Recovery gap: 61.6 s** (budget-spent `13:54:05.897` → first non-zero `rendered=` at `13:55:07.521`) — in the same range as round 3's 66.9s and round 2's 63.1s, **not** the sub-10s this round's fix targets. This is what makes the (unattainable) R1 comparison meaningful in principle: R3 shows the old, blind-to-the-hole behavior; R1 was supposed to show the new one.
- `cycling video focus`: 3 total (`1/3` at `13:51:26.453`, `2/3` at `13:52:39.942`, `3/3` at `13:53:53.623`, all ~2000ms unrepaired — cycles ~73s apart, past `CYCLE_COOLDOWN_MS`, so all three fire freely and are spent by the time the budget runs out). `Codec initialized:` = 8. `but restart suppressed` = 2 (expected, out of scope per round 3 §3).

## R4 — clean session regression

**PASS**, every condition met.

- Settings written: `log-level=2, view-mode=0, video-codec=H.265`; all debug keys deleted.
- Discard-rule check: clean — 1 `createGroup SUCCESS`, 1 SSL handshake. No renderer banner this run.
- Duration: 611s (target 600s).
- **Zero** `DELTA_CHANGED`, **zero** `fragment run lost bytes`, **zero** `holding the cycle until it settles` — the round's most important finding, confirmed a third time (rounds 2 and 3 both measured zero on a clean session; this is the third).
- **`Codec initialized:` = 1.**
- **Zero** `cycling video focus`, **zero** `retaking video focus`, **zero** `Decoder has had no keyframe since it started`.
- **`keyframe decoded - the picture is repaired` = 9** over 10 minutes — non-zero, signal not inert.
- **`fragment accounting established`: one per fragmenting channel, both flat -29 bytes/fragment** (`MUSIC_PLAYBACK`, `VIDEO`).
- No `FATAL EXCEPTION` / `AndroidRuntime`. `rendered=` never 0 across all 116 throughput windows.
- **`parameter sets changed mid-session`: 2** — matches rounds 2 and 3 exactly, settled.

## R5 — the read-desync fix is still silent

**PASS.** All four lines — zero occurrences across R1, R3 and R4's captures combined (R2 not run). This includes R1's crash-loop capture: despite four full session deaths from the TLS-layer desync above, **none** of the four framing-desync lines fired, confirming that finding's own claim — the raw byte framing genuinely stays intact; the failure is one layer higher, at the TLS record layer, which R5's instrumentation was never built to see.

## Report back

1. **Does the audit's detection reach recovery?** Yes, on the evidence available: `DELTA_CHANGED on VIDEO` fired 14 times and `fragment run lost bytes` fired 5 times in the one (short-lived, multi-session) R1 capture. The wiring works. Whether it reaches recovery **usefully** — i.e., whether the hold, the budget and the recovery-gap timing behave as designed — could not be tested, because no session survived long enough.
2. **Did the hold finally engage?** Not measurably. Zero `holding the cycle until it settles` lines appeared, but every R1 sub-session died 1-13 faults in, before the 2s quiet window or 60s cooldown could plausibly matter. This zero cannot be read the same way as round 3's zero (which was a genuine, sustained-session finding).
3. **How long does the picture take to come back once the loss stops?** Unmeasurable for the new mode. For the record, R3 (the old, unfixed-looking control) measured **61.6 s**, matching round 3's 66.9s and round 2's 63.1s.
4. **Is the new mode testing something the old one could not?** Yes, decisively — but not in the way intended. Mode `5` reaches the detection (`DELTA_CHANGED`, `fragment run lost bytes`) that mode `2` structurally cannot, exactly as designed. What the round did not anticipate is that mode `5`'s method of dropping a fragment — discarding it after the raw bytes are consumed but before decryption — breaks something mode `2` never touches: the TLS engine's record sequence. So mode `5` is testing something real, just not (yet) the thing R1 was written to measure.
5. **Does the hold have a floor?** Not tested (R2 not run).
6. **Does a clean stream ever see any of it?** **Three zeroes**, now measured three rounds running (rounds 2, 3, 4 all zero on a clean session). This question is settled; a fourth measurement would not change anything.
7. **Did the renderer-banner line appear, and did it save a capture?** Yes to both, with a caveat. It appeared on R1's sessions and on R3's first attempt, and the new instrumentation is exactly what made it possible to diagnose and route around (round 3 lost an entire capture to this same banner with no such line to grep for). But the banner reappeared *despite* the persisted setting round 3 relied on staying `false` — see Setup notes. A one-shot check immediately after the handshake is not sufficient; it needs to be polled for the run's duration, since it can appear several seconds after the connection forms.
8. **Anything not asked about:** the TLS-desync finding above is the round's real result. `VideoFaultInjector`'s `Stage.READER` mode needs to feed the dropped message's bytes through `sslEngine.unwrap()` (discarding the *output*, not skipping the *input*) if it is to simulate "a fragment lost on the wire" without also destroying the session — otherwise no reader-stage fault mode can ever produce a sustained capture, and this round's central question stays unanswerable on every future re-run of R1/R2 exactly as it was this time.
