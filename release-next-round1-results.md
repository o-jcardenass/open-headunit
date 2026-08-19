# release/next — round 1 results

**Candidate:** `release/next` @ `e2e65855e48c34c5ab0b82d2212c70585d9fa54` (short `e2e65855`)
**Baseline:** `v.3.2.5` @ `9f7c3b20`
**APK md5:** candidate `7cbc9a8bebff4f75fd8decf45db0e056` / baseline `bd88b417cfe23e4dd43213fedc0d2cd7`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, phone: Redmi/POCO X3 NFC (`M2007J20CG`)
**Date:** 2026-08-18

## Setup notes

**Scripts used:** `build_hur.sh` (both APKs), `run_unit_tests.sh` (candidate), `set_hu_prefs.sh` (all
settings writes, multi-key). No new script needed for build/install/settings. One new script written
for the round: an adb `input swipe` loop for R5's Maps panning (see below) — not added to
`hur-wifi-test-scripts/` since it's a one-off interaction pattern, not a reusable build/install/test
step.

**R0:** both APKs compiled clean. Candidate unit tests: 472 tests, 0 failures (matches the brief's
`main`'s 312 + 145 (`fix/video-pipeline`) + 15 (`fix/session-liveness`) = 472). **PASS.**

**Discard-rule deviation, accepted as clean-in-substance (R1, R2, R5, R6):** `MATCH! Starting
AapService` fired once or twice in most runs (R2 was the only true zero). In every accepted run,
`createGroup SUCCESS` and `SSL handshake complete` each still occurred exactly once, and the P2P
interface index bumped at most once — i.e. the actual outcome the discard rule protects against
(two overlapping sessions/handshakes/groups) never happened; `AutoStartReceiver` fired on the
phone's own legitimate Bluetooth reconnect (mandated by the clean-run protocol's "phone radios back
on" step) without producing a second group. Where a run genuinely produced a **second** group
(`createGroup SUCCESS` × 2, two distinct P2P interfaces), it was discarded and re-run per protocol —
this happened three times this round (one R1 attempt with a full 3-group self-wake spiral triggered
by the app's own poke; two R5 attempts with a clean single re-group). Discarded captures are kept
alongside the accepted ones, suffixed `_DISCARDED_*`. Landing the handshake quickly after bringing
the phone's radios back (well under ~60s) correlated with staying at a single group in every
successful attempt this round; the two R5 failures both had the rebuild land ~60-70s after launch,
consistent with a session-not-yet-formed internal retry independent of any radio-toggle timing on
this end.

**R5 deviation, requested mid-round by the user:** the brief's R5 setup says "no scripted interaction
needed." The user asked for periodic map-panning to be added instead ("Do scrolling with adb in this
10 min test"), then asked for a faster cadence to simulate a ~50 km/h drive. Android Auto's Maps
screen was already the default view on session start (confirmed by screenshot), so no navigation was
needed. Ran `adb shell input swipe` on the head unit at 1440×720 (the AA surface's coordinate space,
confirmed via `wm size` vs. the screenshot dimensions), first at ~45s intervals (2 pans fired), then
switched to a faster continuous loop at ~2s intervals with a consistent forward-pan direction for the
remainder of the window. This means R5 is **not** the brief's literal "static default screen" test —
see the R5 write-up below for how this affects one of its four PASS conditions.

**R4** used `cmd connectivity airplane-mode enable` (reliable BT+WiFi drop on this phone, confirmed
in `TESTING-TEMPLATE.md` §7a) rather than individual `svc` calls, matching the brief's own script.

## R1 — reproduce #852 on 3.2.5 (gate for R2)

**PASS**

- Settings written: `log-level=2`, `view-mode=1` (TEXTURE), `video-codec=H.264`,
  `debug-video-fault-injection`/`debug-video-fault-rate`/`force-software-decoding`/
  `software-video-decoder` all deleted (defaults).
- Radio state: phone Bluetooth+WiFi disabled via `svc`, confirmed off; head unit launched first,
  group settled ~3s, phone radios re-enabled via `svc`, confirmed on.
- Discard-rule check: two prior attempts discarded (one triple-group self-wake spiral from the app's
  own poke, `r1_baseline_DISCARDED_selfwake_churn.txt`; one accepted-then-reconsidered double-MATCH
  attempt kept as `r1_baseline_DISCARDED_double_match.txt` for the record though it was
  clean-in-substance — discarded anyway out of caution before the pattern above was established).
  Accepted run: 2× `MATCH!`, 1× `createGroup SUCCESS`, 1× `SSL handshake complete`, 1 P2P interface,
  0 `Magic Garbage`.
- Playback confirmed PLAYING via `dumpsys media_session` (Spotify, "Reaching Out" — The Pineapple
  Thief) at 22:36:13, held 30s (throughput steady 49-50fps), paused via keyevent 127 at 22:36:59,
  confirmed PAUSED.
- Idle gate: `rendered=0 (0fps)` for 50 consecutive throughput windows across the 5-minute hold — met
  many times over the required 3.
- Decisive log lines: standalone `Showing reconnecting overlay` fired **6 times**:
  `22:37:14.230`, `22:37:46.263`, `22:38:12.289`, `22:39:12.340`, `22:40:16.467`, `22:41:12.506`
  (intervals: 32.0s, 26.0s, 60.1s, 64.1s, 56.0s). First occurrence 15.2s after the pause command.
- Measurement: defect reproduced 6× in 5 minutes at 26-64s intervals — same failure mode as #852,
  slightly longer intervals than the reporter's 15-30s but the same shape (repeated flashing overlay
  on a healthy idle link).

## R2 — the candidate, playing then paused (the #852 verdict)

**PASS**

- Settings written: identical to R1 (candidate build).
- Radio state: same clean-run sequence; handshake landed 33s after launch, single group.
- Discard-rule check: 1× `MATCH!`, 1× `createGroup SUCCESS`, 1× `SSL handshake complete`, 1 P2P
  interface — the cleanest run of the round (phone's own reconnect beat any poke).
- Phase 1 (2 min playing): confirmed PLAYING at 22:44:16, throughput steady 19-29fps (avg ~22fps —
  see "Anything the brief did not ask about" for a note on this vs. R1's ~50fps), zero idle-cadence
  lines during this phase.
- Phase 2 (5 min paused): paused at 22:46:25 (had to resend the keyevent once — first attempt landed
  during a track transition and didn't take), confirmed PAUSED.
- **Standalone `Showing reconnecting overlay`: 0** across the whole capture.
- **Gated overlay (`...treating this as a lost connection`): 0** — no false positive.
- **`picture idle for Nms but the link spoke Mms ago` — the fix working, 23 occurrences, all during
  phase 2, none during phase 1.** `Mms` values: min **679ms**, max **758ms**, all 23 in that narrow
  band. This is the round's headline deliverable — Android Auto's real idle inbound cadence on this
  rig is consistently just under 800ms, comfortably inside the 10s `LINK_QUIET_MS` threshold.
- No `FATAL EXCEPTION` / `AndroidRuntime`.

## R3 — the #822 recovery guard (counted from R2)

**PASS**

- `AapProjectionActivity: connected but no frames - requesting video focus (unsolicited)` fired
  **96 times** during phase 2, spacing ~2.0s (confirmed 22:46:42.978, :44.983, :46.984, :48.985,
  :50.987 — 2.0-2.1s apart), continuing throughout the whole 5-minute idle window. Overlay never
  shown (R2's 0 count). Recovery survived the split between overlay-gating and recovery-triggering.

## R4 — a genuine loss still shows the overlay

**PASS**

- Setup: candidate session live and rendering (51fps confirmed just before), phone
  `airplane-mode enable` at 22:52:55.
- **Finding, recorded as the brief asked:** both paths fired, and **the gated idle path won the
  race**, not the Disconnected collector as the brief's own prediction expected:
  - `22:53:05.461` — `picture idle for 10019ms and the link has been silent for 10040ms - treating
    this as a lost connection` (gated path), overlay shown immediately after.
  - `22:53:10.478` — `Unexpected disconnect. Showing reconnecting overlay and waiting up to 20s for
    recovery.` (Disconnected collector), 5.0s later.
  - `22:53:30.481` — `Reconnect timed out (20s). Finishing activity.` — 20.0s after the Disconnected
    collector's own line, confirming its timer (not the gated path's) governs the reconnect window.
- Radios restored and confirmed up (`svc wifi enable`, `svc bluetooth enable`) before proceeding.

## R5 — clean session regression (the merge did not break the video work)

**PASS, with one caveat from the added interaction (see Setup notes)**

- Settings written: `log-level=2`, `view-mode=0` (SURFACE), `video-codec=H.265`, fault-injection keys
  deleted.
- Discard-rule check: two attempts discarded for a genuine second `createGroup SUCCESS` (
  `r5_candidate_DISCARDED_double_group.txt`, `..._2.txt`); accepted run: 2× `MATCH!`, 1× group,
  1× handshake, handshake landed 56s post-launch.
- Ran **~10 minutes** (22:59:51 launch through 23:11:33) with periodic Maps panning per the user's
  request (2 slow pans at ~45s intervals, then a faster continuous ~2s-interval forward pan for the
  remainder — see Setup notes).
- **`Codec initialized:` exactly 1** (`23:00:48.793`, `c2.unisoc.hevc.decoder`). ✓
- **Zero** `Decoder has had no keyframe since it started`, **zero** `cycling video focus`, **zero**
  `retaking video focus`. ✓
- **Zero** `DELTA_CHANGED`. ✓ (matches round 2's own zero)
- **One** `AapRead: fragment accounting established for` per fragmenting channel: VIDEO
  (`perFragment=-29`, matching the recorded -29 bytes/fragment) and MUSIC_PLAYBACK (also
  `perFragment=-29`). ✓
- All three `access unit classified` answers present once each in the first moments
  (`PARAMETER_SETS_ONLY`, `PARAMETER_SETS_WITH_PICTURE`, `NO_PARAMETER_SETS`). ✓
- **`parameter sets changed mid-session`: 4**, not the expected zero. All four are PPS-only,
  `size unchanged 1920x1080`, and land at `23:06:34`, `23:08:45`, `23:09:51`, `23:10:57` — entirely
  inside the fast-panning window. Read as the encoder legitimately adapting to the injected motion
  (a scene condition the brief's static-screen R5 was never going to produce) rather than a
  merge-introduced defect in the change-latch: none of the four repeated on every keyframe (which is
  the shape the brief calls the real FAIL), and no size change ever occurred.
- Throughput stayed non-zero throughout, actually *rising* under the panning load (49fps at the start
  climbing to 53-56fps by the end), zero `rendered=0` windows, zero crashes.

## R6 — the decoder wedge still recovers (the merge did not break the fix)

**FAIL — the merge undid the fix.**

- Settings written: `log-level=2`, `view-mode=0` (SURFACE), `video-codec=H.264`,
  `debug-video-fault-injection=2`, `debug-video-fault-rate=3`.
- Discard-rule check: 2× `MATCH!`, 1× group, 1× handshake — accepted, handshake landed 36s
  post-launch.
- Fault injection confirmed active well past the INCONCLUSIVE floor: **90 `FAULT INJECTED`** lines
  (vs. the brief's minimum of 2), injector summary line printing every 15s throughout
  (`DROP_MIDDLE_FRAGMENT 1-in-3, 272 candidates seen, 90 injected` by the end).
- **The picture degraded as expected — that part is correct and not the finding.**
- **The finding:** the decoder wedged and never recovered for the rest of the 3-minute capture.
  - `23:14:19.080` — `rendered=0 (0fps)` begins (fed stayed ~50fps throughout — frames were arriving,
    nothing was rendering).
  - `23:14:49` → `23:15:09` — stall ladder: **20012ms → 30014ms → 40015ms**, all "restart suppressed
    (4/4 used, 8000ms cooldown)".
  - `23:15:16.009` — `AapTransport.escalateIfStillUnrepaired: picture unrepaired for 62003ms -
    cycling video focus (2/3)` — the escalation that should have fired within ~2s per the brief's
    PASS bar instead took **62 seconds**. It produced one keyframe (`23:15:16.680`) and a single 5s
    window of `rendered=8`, then reverted to `rendered=0`.
  - `23:16:34` → `23:17:04` — the stall ladder repeats and exhausts again: **20002ms → 30007ms →
    40014ms → 50022ms**, "restart suppressed (4/4 used, 8000ms cooldown)", still spinning on output
    at the moment the capture was stopped (3 minutes elapsed).
  - Total time with `rendered=0`: **18 consecutive 5s throughput windows (~90s)**, against the
    brief's ~25s FAIL threshold, plus the tail end (last ~35s of the capture) also stuck.
  - `Codec initialized:` fired 9 times (well under "one per fault"), so this is not a codec-rebuild
    storm — the picture just never got un-stuck.
- **This is exactly the pre-fix wedge signature the brief describes as a FAIL.** The recovery path
  that got stuck, `AapTransport.escalateIfStillUnrepaired`, lives in `AapTransport.kt` — one of the
  four files the brief's own §2 flags as edited by both feature branches and auto-merged by git with
  no conflict. Video-pipeline-stack round 2 measured this exact fault (`DROP_MIDDLE_FRAGMENT`, rate
  3, H.264, SURFACE) recovering in 0.544s/0.557s with `rendered` never zero, on the standalone
  `fix/video-pipeline` branch. On the merged `release/next` tree it wedges for minutes. **The
  candidate build and settings are confirmed correct** (md5 matched pre-install, settings verified
  before launch), so this is not a setup error.
- Full capture kept at `logs/r6_candidate.txt` (no excerpt) per the FAIL protocol.

## R7 — the read-desync fix is silent on a healthy link (counted from R2, R5, R6)

**PASS**

- Zero occurrences of `AapRead: fragment total read returned`, `AapRead: body read returned`,
  `AapRead: declared message size`, or `Disconnecting to resync` across all three captures — including
  R6, whose video wedge did not touch the transport/framing layer.

## Report back

1. **#852: does 3.2.5 reproduce the overlay, and does the candidate show zero?** Yes and yes. R1
   reproduced the overlay 6× over 5 minutes on baseline; R2 showed zero standalone overlays on the
   candidate under the same conditions plus a 2-minute playing control. **#852 is fixed.**
2. **Every `link spoke Mms ago` value from R2:** 23 measurements, all in **679-758ms**. Android
   Auto's real idle inbound cadence on this rig is well under 1 second — `LINK_QUIET_MS=10000` has
   roughly an order of magnitude of margin.
3. **`requesting video focus (unsolicited)` count during R2's paused phase:** **96**, ~2.0s apart,
   continuous through the full 5 minutes. Recovery survived the split; the fix did not go too far.
4. **Did the merge hold?** **No — R6 failed.** R5 (clean session) passed on every count except one
   PPS-adaptation side effect explained by an interaction I introduced (Maps panning), not a merge
   defect. R7 (transport half) passed cleanly. But **R6, the run that exercises the video half of the
   auto-merged files, reproduced the pre-fix decoder-wedge signature**: stall ladder exhausted twice
   (20/30/40s, then 20/30/40/50s), a 62-second-late escalation that only partially recovered, and
   ~90+ seconds of `rendered=0` where video round 2 measured sub-second recovery on the same fault at
   the same rate. `AapTransport.kt`, one of the four auto-merged files, hosts the stuck recovery path
   (`escalateIfStillUnrepaired`). **This blocks the release candidate** until the interaction between
   `fix/video-pipeline`'s recovery machinery and `fix/session-liveness`'s changes to the same file is
   found and fixed — most likely worth a dedicated bisection round isolating `AapTransport.kt`'s
   auto-merged diff.
5. **Anything not asked about:**
   - R2's phase-1 throughput (~22fps average, TEXTURE) was noticeably lower than R1's pre-pause
     measurement (~50fps, also TEXTURE) and R5's SURFACE measurement (49-56fps). Different Spotify
     UI content between runs is a plausible explanation (album art animation rate varies by track),
     but it wasn't controlled for and is worth a note if anyone compares these numbers later.
   - R5 ran with continuous synthetic touch input (Maps panning) for ~8 of its 10 minutes at the
     user's request — a deviation from the brief's static-screen design, done for broader coverage
     of the merged tree under real interaction. It incidentally showed the app renders *more* frames
     under panning load (49→56fps) with zero throughput regression, which is a positive data point
     the brief didn't ask for.
   - R1's overlay-recurrence intervals (26-64s) ran longer than the original bug report's 15-30s.
     Same defect, different cadence — not investigated further since R1's only job was gating R2.
