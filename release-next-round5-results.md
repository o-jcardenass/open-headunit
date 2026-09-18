# release/next — round 5 results

**Candidate:** `fix/video-stack` @ `64033c4a511a9a337adeec5871298e55ada46a42`       **Baseline:** none (no A/B this round)
**APK md5:** `a00bd81ebb541a896f31cf06b0910379`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14; phone: POCO X3 NFC (Redmi M2007J20CG)
**Date:** 2026-08-19

## Setup notes

- Used `hur-wifi-test-scripts/build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh` (`SKIP_BUILD=1`), `set_hu_prefs.sh` — existing tooling covered every step.
- **The renderer confirmation banner needed a working poll-and-tap script this round.** The one ad-hoc'd in round 4 matched `text="Yes"`, but the on-device button text renders uppercased (`text="YES"`, Material default styling), so the round 4-style matcher never fires. Wrote `banner_watch.sh` in `hur-wifi-test-scripts/` (case-insensitive match on the question text and the `YES` button, polls every 2s, taps via `uiautomator dump` + `input tap`) and left it there for the next round. **R1 lost about a minute of clean measurement to this before the fix landed** — the banner blocked rendering from 14:47:38 to 14:49:35 while the first (broken) watcher sat idle; a manual tap recovered it. All later banners in R1-R3 were caught and dismissed automatically within seconds.
- **R3's control run took far longer than expected to spend its 30-fault budget: 1029s, versus round 4's 393s for the identical settings.** The three `escalateIfStillUnrepaired` focus cycles were all spent early (by 15:05:46, within 3.5 minutes of launch) and the mode-2 fault injector kept accumulating candidates for another ~14 minutes before hitting 30. The AA screen for this run was the default post-connect dashboard (Maps + a static mini-player), not an actively-changing screen, which plausibly slowed how often mode 2's matching frame type occurred. This is noted, not diagnosed — see the R3 write-up below for what it does to the recovery-gap number.
- **R5 needed a redo.** The first attempt combined the standard clean-run protocol with switching the phone to a paused Spotify screen (to get genuine `rendered=0`, following R4's method) *after* the connection was already up, which put a device interaction inside the window the brief wants left alone. That run is kept as `r5_contaminated_spotify_touch.txt` and is not scored; see "Anything the brief did not ask about" for what it showed. **R5's scored run is a second attempt with zero interaction of any kind between connect and the 10-minute mark** — whatever screen Android Auto defaulted to (not deliberately steered) is what was measured.
- Settings verified by reading `settings.xml` back before every run. `settings-backup.xml` restored at the end (verified: `view-mode=2`, `log-level=0`, `force-software-decoding=true`, `software-video-decoder=1`, `video-codec=H.265`, no `debug-video-fault-*` keys).
- Discard-rule check applied per round 3/4's correction: counted SSL handshakes and `p2p-wlan0-N` indices reaching the measurement window, not `MATCH! Starting AapService` itself. All five runs (R1-R5) were single-session, single-handshake, single-`createGroup SUCCESS`, single `MATCH!` captures — clean.

## R0 — build and unit-test gate

**PASS**

- `build_hur.sh`: BUILD SUCCESSFUL. APK md5 `a00bd81ebb541a896f31cf06b0910379`.
- `run_unit_tests.sh`: BUILD SUCCESSFUL, **513 tests, 0 failures, 0 ignored** — matches the brief's expected count exactly (round 4's 505 + 8 for the relaunch fix).

## R1 — the question rounds 3 and 4 could not answer

**PASS on condition 0; partial on the recovery-speed target.** Mode `5` no longer kills the session — round 4's central blocker is fixed.

- Settings written: `log-level=2, view-mode=0, video-codec=H.264, debug-video-fault-injection=5, debug-video-fault-rate=3, debug-video-fault-budget=30`.
- **Condition 0: the session survived.** One SSL handshake, **zero** `Decrypted payload too short`, **zero** `Connection closed (EOF)` in the entire run. This is the headline result: round 4's mode-5 crash loop (dead after 1-13 faults, every time) is gone.
- **The chain:**
  1. `AapRead: DELTA_CHANGED on VIDEO` = **17**.
  2. `fragment run lost bytes, requesting keyframe to recover stream` = **40** — *above* the DELTA_CHANGED count this time (round 4 measured it below, 5 vs 14). Worth a note for whoever reads this next: the throttle-to-1/s expectation held in round 4's short-lived sub-sessions but not here across the full run; not investigated further this round.
  3. **`holding the cycle until it settles` = 1**, first and only occurrence: `14:47:38.843 ... picture unrepaired for 2001ms but the stream is still losing frames (last 1898ms ago) - holding the cycle until it settles (0/3 spent)`. Non-zero, as round 3's finding required and round 4 could not produce.
  4. **Budget-spent → first non-zero `rendered=`: 44.5 s** (`14:54:57.775` → `14:55:42.305`), against R3's recovery gap this round of ~254s (see below) and round 4's R3 of 61.6s. Better than both, but not the ≤10s the fix targets.
- **The budget-spent → cycling-video-focus gap the brief asked for does not exist in this run.** All three `escalateIfStillUnrepaired` cycles (1/3, 2/3, 3/3) fired early — by `14:51:30`, more than three minutes *before* the budget was spent at `14:54:57.775` — so the escalation had nothing left to spend when the fault stream went clean. **The actual recovery came from the decoder's independent `sync_stall` restart ladder**, not from the new fix's escalation: three `Decoder stopped: restart: sync_stall` → `Codec initialized:` pairs at `14:55:07`, `14:55:22`, `14:55:37`, with the first non-zero `rendered=` five seconds after the last of those. This is a real and useful distinction: the fix's hold/escalation logic worked exactly as designed on this run's timeline, but a different, pre-existing mechanism is what actually got the picture back.
- **Total `FAULT INJECTED`: 30/30**, budget hit cleanly, well above the 30-line floor.
- `cycling video focus`: 4 total — 1 from `maybeRecoverWarmRelaunch` at session start (`14:47:38.493`, 6918ms, this round's new relaunch fix firing at first connect too) plus the three escalation cycles (`14:49:00.012` 1/3 2002ms, `14:50:08.010` 2/3 62002ms, `14:51:30.013` 3/3 2000ms). `Codec initialized:` = 18. Longest run of consecutive `rendered=0` windows: from session start through the recovery, i.e. the picture was effectively down (with two brief false starts) for most of the run's first eight minutes.

## R2 — the hold has a floor

**PASS**, both conditions met.

- Settings written: `log-level=2, view-mode=0, video-codec=H.264, debug-video-fault-injection=5, debug-video-fault-rate=3`, no budget.
- `cycling video focus` fired 4 times (same shape as R1: 1 warm-relaunch + 3 escalation cycles at 1/3, 2/3, 3/3 — last at `15:01:02.398`).
- **Zero** `holding the cycle until it settles` lines — vacuously satisfies "no hold line reports a first value above 60000ms," since none fired at all. `FAULT INJECTED` reached 26 by the time the run was stopped (past the 3-minute mark).
- Session survived throughout: zero `Decrypted payload too short`, zero `Connection closed (EOF)`.
- Expected `but restart suppressed` ladder and codec-init churn present, out of scope per the brief.

## R3 — the control, unchanged

**PASS on the zero-counts; the recovery gap is a major outlier.**

- Settings written: `log-level=2, view-mode=0, video-codec=H.264, debug-video-fault-injection=2, debug-video-fault-rate=3, debug-video-fault-budget=30`.
- **Zero** `DELTA_CHANGED on VIDEO`, **zero** `fragment run lost bytes`, **zero** `holding the cycle until it settles` — exactly as expected: mode 2 is blind to the hole by construction.
- **`FAULT INJECTED`: 30/30**, but it took **1029s** (17m9s) from launch to spend the budget, against round 4's 393s for the same settings — see Setup notes for the likely cause (screen content, not code).
- **Recovery gap: 253.9s** (budget-spent `15:19:40.315` → first non-zero `rendered=` at `15:23:54.171`) — **not** "near round 4's 61.6s" as the brief expected; roughly 4x worse. The three `escalateIfStillUnrepaired` cycles were all spent early (`15:03:16` 1/3, `15:04:24` 2/3, `15:05:46` 3/3 — all within the first 3.5 minutes), so for the remaining ~18 minutes of the run the app had no escalation left and was solely dependent on the `sync_stall` watchdog's own restart ladder: **48** `Codec initialized:` events, **46** `but restart suppressed` lines, and **47** actual `Decoder stopped: restart: sync_stall` restarts, running in bursts of ~4 roughly every 60-75s until one finally landed a working keyframe. This is the same recovery path R1 used, not a new one — the difference is R3 had far more real time to grind through it because the fault injector kept running so much longer.
- **R1 vs R3, side by side** (the round's actual point):

  | | R1 (mode 5, fixed) | R3 (mode 2, control) |
  |---|---|---|
  | DELTA_CHANGED on VIDEO | 17 | 0 |
  | fragment run lost bytes | 40 | 0 |
  | holding the cycle | 1 | 0 |
  | recovery gap after budget-spent | 44.5s | 253.9s |

  The reader stage is unambiguously seeing something the assembler stage cannot (all three zero counts on R3 vs non-zero on R1) — that part of the round's question is answered cleanly. The recovery-gap comparison is confounded this round by R3's abnormally long time-to-budget-spent (see above) letting its `sync_stall` ladder grind for 14 extra minutes it didn't get in round 4; a same-length comparison would need R3 re-run under conditions that reach budget-spent in the usual ~6-7 minutes.

## R4 — a relaunch onto an idle screen recovers the first time

**PASS, cleanly, all four reopens.** No alternation observed — this is the opposite of the pre-fix signature.

**`view-mode=1` (TextureView)**, no injection. Setup: after connect, switched the phone to Spotify (force-stop + relaunch) to get a genuinely idle, paused full-screen player — the AA default dashboard was found to keep a low but non-zero background frame rate (~10-15fps) even sitting on the launcher/dashboard, so it does not satisfy "leave it untouched... confirm `rendered=0`" on its own; a paused player does.

| Reopen | Escalation `Nms` | New surface set → repaired | Silent? |
|---|---|---|---|
| 1 | 855ms | 1.524s | No — onCreate, New surface set, escalation, repair, non-zero Throughput all present |
| 2 | 852ms | 1.628s | No |
| 3 | 850ms | 1.516s | No |
| 4 | 873ms | 1.623s | No |

- **Every reopen** logged `relaunched surface has no picture after Nms - cycling video focus` with N in **850-873ms** — right on the ~850ms target, nowhere near the pre-fix ~9000ms signature.
- **Every reopen** rendered within ~1.5-1.6s of `New surface set:`, comfortably under the ~2s bar.
- **No reopen was silent.** No occurrence of the alternation the pre-fix build showed (silent every other reopen).
- No renderer confirmation banner appeared at any point in R4.

## R5 — the widened liveness gate costs an idle screen nothing

**PASS, cleanly, on the scored (uncontaminated) run.**

**`view-mode=1`**, no injection, no relaunch, zero interaction of any kind for the full 10 minutes after connect.

- **Zero** `cycling video focus`, **zero** `relaunched surface still has no picture`, **zero** `relaunched surface has no picture after`, **zero** `treating this as a lost connection`, **one** `Codec initialized:`.
- **`keyframe decoded - the picture is repaired` = 6** over 10 minutes — non-zero, on the phone's own cadence, exactly as expected.
- No renderer confirmation banner. Discard-rule clean (1 createGroup, 1 handshake).

See "Anything the brief did not ask about" for the discarded first attempt, which is a genuine finding worth flagging even though it doesn't count toward this run's verdict.

## R6 — the read-desync fix is still silent

**PASS. Zero** of all four lines, across R1, R2, R3, R4 and R5's captures combined (five captures, not the three round 4 had). Round 4's finding holds on a much larger sample this round, including R1's sustained mode-5 session (which round 4 never got).

## Report back

1. **Does mode 5 sustain a session now?** Yes, cleanly. Zero `Decrypted payload too short`, zero `Connection closed (EOF)` in a run that spent a full 30-fault budget and ran for over 8 minutes. Round 4's best was 13 faults before death; this round reached 30/30 with the session intact throughout, and R2 (no budget cap) held for the full 3-minute run with 26+ faults injected and never died either.
2. **Did the hold finally engage?** Yes. `holding the cycle until it settles` fired once in R1 (`0/3 spent`, `14:47:38.843`), the first time this line has ever been observed on a surviving mode-5 session. R2 saw zero, vacuously satisfying its own bar (no hold line, so none exceeds 60000ms).
3. **How long does the picture take to come back once the loss stops?** R1: **44.5s** — better than R3's control this round (253.9s) and better than round 4's R3 (61.6s), but not the ≤10s the fix targets, and the mechanism that actually recovered it was the decoder's own `sync_stall` restart ladder, not the new escalation (which had already spent all 3 cycles 3.5 minutes earlier). R3's 253.9s number is not directly comparable to round 4's 61.6s — see the R3 write-up for why.
4. **Is the reader stage testing something the assembler stage cannot?** Yes, decisively: R1 saw 17 `DELTA_CHANGED`, 40 `fragment run lost bytes`, and 1 `holding the cycle`; R3 (control) saw zero of all three, on the same build. That comparison is exactly what the round needed and round 4 couldn't get.
5. **Does the first reopen recover?** Yes, and so do the second, third and fourth. All four `view-mode=1` reopens recovered within ~1.5-1.6s of a fresh surface, with the escalation firing at 850-873ms every single time. No alternation, no silent reopen — the reporter's bug did not reproduce once across four attempts.
6. **Does an idle screen still get left alone?** Yes, on the clean (uncontaminated) 10-minute run: five zeroes and exactly one `Codec initialized:`. See below for what the contaminated first attempt showed, though it isn't scored.
7. **Did the renderer banner appear, and with which reason string?** Yes, in R1 and R3 (both fault-injection runs): `(the phone is streaming and nothing has drawn)` in both cases — the "session health check" path, not the `pending-renderer-confirm` setup-wizard path. It did not appear in R2, R4, or R5's scored run.
8. **Anything in a capture none of the above asked about:** see below — R5's discarded first attempt.

## Anything the brief did not ask about

**R5's first (discarded) attempt showed the video pipeline going 123 seconds with zero rendered frames from the very first connection, self-corrected by the new warm-relaunch fix.** Preserved as `r5_contaminated_spotify_touch.txt`. After connect, the session sat on Android Auto's default dashboard (Maps + a mini-player); a single keyframe rendered once at connect (`14:54:42.077` in that capture's clock) and then **nothing new rendered for the next two minutes**, until `AapProjectionActivity.maybeRecoverWarmRelaunch` fired at exactly `New surface set: + 122968ms` and forced a focus cycle, after which a fresh keyframe rendered within 1.3s. The 850ms-class warm-relaunch trigger (the one R4 exercises deliberately via HOME+relaunch) is not the one that fired here — this is the ~120s-class trigger, the same family of logic but a different threshold, apparently also reachable on a session nobody has relaunched at all.

This run is **not** clean evidence of a code defect: a phone-side Spotify force-stop/relaunch was issued about 64 seconds before the 123s escalation fired, as part of trying to reach a genuinely idle screen (see Setup notes), so a causal link to that interaction can't be ruled out. But the escalation's own timestamp anchors to the *original* `New surface set:` from the initial connect — 123 seconds earlier — not to the moment of the Spotify touch, which argues against the touch being the direct trigger. Worth a dedicated, disciplined round if this recurs: does a completely default, single-touch-free Android Auto session ever go two minutes without a second rendered frame, and if so, on what fraction of connects? This round's own clean R5 redo did not reproduce it (`Codec initialized: 1`, no stall at all), so it is not happening every time.
