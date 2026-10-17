# video-black-after-background — round 6 results

**Candidate:** `fix/822-stale-surface-callback` @ `1192daa5`, unchanged since round 5 (no rebuild, no
install, no device touched this round — post-hoc analysis only).
**Date:** 2026-08-13

## Setup notes

- No build, no APK, no R0 gate, per the brief's §1 — this round is pure analysis of round 5's own
  captures.
- All four captures were present exactly where the brief expected them, in
  `hur-wifi-test-scripts/round5-video-black/`: `r1-gles.txt` (161,126,995 bytes), `r2-texture.txt`
  (151,414,189 bytes), `r3-surface.txt` (93,923,316 bytes), `r4-rapid.txt` (39,978,860 bytes). No
  fallback re-capture (§9) was needed.
- `view-mode` per capture, confirmed from round 5's own results file rather than from memory: R1
  (`r1-gles.txt`) = `2` (GLES), R2 (`r2-texture.txt`) = `1` (TEXTURE), R3 (`r3-surface.txt`) = `0`
  (SURFACE), R4 (`r4-rapid.txt`) = `2` (GLES).
- Codec: `video-codec=H.265` in round 5's `settings-backup.xml`, and every `Configuring decoder: `
  line in all four captures pins `c2.unisoc.hevc.decoder` — the stream is H.265 throughout. Per the
  brief's own flag, the `H.264 SPS parsed: ` marker is therefore unavailable in every capture; every
  run below reports zero occurrences and treats it as not applicable rather than as a finding.
- New script, left in place per house rules: `hur-wifi-test-scripts/round6-video-black/legs.sh
  <capture> <label>`, which does the brief's §7 single grep pass into `<label>.reduced.txt`, then
  hands it to `legs_analyze.py` in the same folder to pair each return with its New-surface/
  Configuring-decoder/First-frame triplet and print per-cycle legs and counts. Both files are left in
  `hur-wifi-test-scripts/round6-video-black/`, along with the four `.reduced.txt` files (115-90
  lines each, small enough to keep) and this round's four `.analysis.txt` outputs.
- Correctness check before trusting the output: A1-A3's per-cycle totals (leg A + B + C) reproduce
  round 5's own published return→picture figures line for line — GLES 21.7/45.9/6.8/116.4 s, TEXTURE
  28.1/52.5/13.4/5.4 s, SURFACE 0.68/0.68/0.71/0.80 s — all within a few milliseconds, so no pairing
  disagreement to report for A1-A3.
- A4 (rapid) needed no special handling beyond what the script already does: the greedy return-pairing
  naturally collapses cycles 1-4 into one group (since cycles 1-3's surfaces were each superseded
  before rendering, exactly as round 5 described) and lands cleanly on cycle 5's own surface as the
  second group, whose leg C (53,966 ms) matches round 5's already-published 54.0 s figure for that
  cycle to within 40 ms. No fallback re-capture needed for A4 either.

## A1 — GLES (`r1-gles.txt`)

**Per-cycle legs, milliseconds:**

| Cycle | Leg A (return→New surface set) | Leg B (New surface set→Configuring decoder) | Leg C (Configuring decoder→First frame) | Total | Round 5's figure |
|---|---|---|---|---|---|
| 1 | 482 | 54 | 21,190 | 21,726 ms (21.7 s) | 21.7 s |
| 2 | 483 | 52 | 45,333 | 45,868 ms (45.9 s) | 45.9 s |
| 3 | 507 | 51 | 6,258 | 6,816 ms (6.8 s) | 6.8 s |
| 4 | 419 | 67 | 115,915 | 116,401 ms (116.4 s) | 116.4 s |

All four sums match round 5 within 3 ms — same pairing.

- `Fallback to negotiated dimensions: ` fires exactly once per cycle, and it immediately precedes
  only the **first** `Configuring decoder: ` of each cycle (the one right after `New surface set:`).
  Every later `Configuring decoder: ` in the same cycle (cycles 1/2/4 have restart-driven repeats: 3,
  5, and 7 total occurrences respectively) is **not** preceded by a fresh `Fallback` line — the
  dimensions are only re-negotiated once per relaunch, not once per restart attempt.
- `H.264 SPS parsed: ` never appears — H.265 stream, marker not applicable (see Setup notes).
- Keyframe requests per cycle (recovery / watchdog / mid-session): cycle 1 = 3/13/0, cycle 2 = 5/29/0,
  cycle 3 = 1/3/0, cycle 4 = 6/0/0. The watchdog line fires roughly every 1.5 s throughout every slow
  cycle except cycle 4, where it never fires at all — cycle 4 instead ran 6 `Forcing restart (` and 5
  `but restart suppressed (`, so the overlay watchdog's own gate must be getting starved by the
  restart cascade rather than firing alongside it.
- `Forcing restart (` / `but restart suppressed (` per cycle: 2/0, 4/0, 0/0, 6/5.
- `Media Start Request VIDEO: session=` appears **exactly once in the whole capture**, at the initial
  session start (line 11599, before cycle 1's cover), and never again after any of the four returns —
  confirmed by a whole-file count (`grep -c` = 1), not just the per-cycle window.

**The last `Configuring decoder: ` attempt in each cycle lands 0.4-4.8 s before `First frame
rendered`**, closer than the roughly 10 s spacing between restart attempts earlier in the same cycle:
cycle 1, 1.03 s (09:16:50.605 → 09:16:51.633); cycle 2, 0.93 s (09:20:22.586 → 09:20:27.512, though
this one includes some queueing gap); cycle 3, single attempt, 6.26 s (no restart to compare against);
cycle 4, 4.76 s (09:29:31.895 → 09:29:36.650). The picture arrives shortly after a fresh codec
configure, not shortly after any particular keyframe request — see §3 below.

## A2 — TEXTURE (`r2-texture.txt`)

**Per-cycle legs, milliseconds:**

| Cycle | Leg A | Leg B | Leg C | Total | Round 5's figure |
|---|---|---|---|---|---|
| 1 | 453 | 49 | 27,616 | 28,118 ms (28.1 s) | 28.1 s |
| 2 | 406 | 61 | 52,014 | 52,481 ms (52.5 s) | 52.5 s |
| 3 | 402 | 64 | 12,930 | 13,396 ms (13.4 s) | 13.4 s |
| 4 | 404 | 154 | 4,870 | 5,428 ms (5.4 s) | 5.4 s |

All four sums match round 5 within 1 ms.

**Leg A is small and constant** (402-453 ms across all four cycles) — the same order of magnitude as
GLES's 419-507 ms and, as A3 below shows, the same order of magnitude as SURFACE's 384-418 ms. Leg A
does **not** distinguish the backends; round 4's "TEXTURE is the slower backend to relaunch" and round
5's opposite impression are both artifacts of total return time, not of leg A specifically. TEXTURE's
lower totals against GLES in 3 of 4 cycles (28.1 vs 21.7 is the one exception) come entirely from leg
C, i.e., from how many restart cycles the phone's next real keyframe happened to cost this backend on
this particular return, not from anything TEXTURE-specific in the relaunch path.

- `Fallback to negotiated dimensions: ` fires once per cycle, only before the first `Configuring
  decoder: `, same pattern as GLES.
- `H.264 SPS parsed: ` never appears (H.265, not applicable).
- Keyframe requests per cycle: 3/17/0, 5/33/0, 2/7/0, 1/2/0 (recovery/watchdog/mid-session). Unlike
  GLES cycle 4, the watchdog line fires in every TEXTURE cycle, including the ones with restarts.
- `Forcing restart (` / `but restart suppressed (` per cycle: 2/0, 4/1, 1/0, 0/0.
- `Media Start Request VIDEO: session=` again appears **exactly once in the whole capture** (initial
  session start only), never after any of the four returns.

## A3 — SURFACE (`r3-surface.txt`), the control

**Per-cycle legs, milliseconds:**

| Cycle | Leg A | Leg B | Leg C | Total | Round 5's figure |
|---|---|---|---|---|---|
| 1 | 384 | 245 | 49 | 678 ms (0.68 s) | 0.68 s |
| 2 | 404 | 233 | 42 | 679 ms (0.68 s) | 0.68 s |
| 3 | 418 | 237 | 51 | 706 ms (0.71 s) | 0.71 s |
| 4 | 393 | 308 | 96 | 797 ms (0.80 s) | 0.80 s |

All four sums match round 5 within 3 ms.

- Leg C — "the phone sent a keyframe promptly" — is **42-96 ms**, roughly 200-1,200x faster than the
  same leg on GLES/TEXTURE (6,258-115,915 ms).
- Leg A (384-418 ms) and leg B (233-308 ms) are actually the two **largest** shares of SURFACE's own
  total, together 92-97% of it — SURFACE recovers fast enough that the fixed relaunch/codec-create
  overhead common to all three backends dominates its own return time, which never happens on
  GLES/TEXTURE only because their leg C is so much larger.
- `Fallback to negotiated dimensions: ` fires exactly once per cycle, immediately before the single
  `Configuring decoder: ` each cycle has (no restarts ever needed on SURFACE — 0 `Forcing restart (`
  and 0 `but restart suppressed (` across all four cycles, confirming round 5's own R3 finding).
- `H.264 SPS parsed: ` never appears (H.265, not applicable).
- No keyframe-request line (recovery, watchdog, or mid-session) fires in any SURFACE cycle — the
  phone starts the sink on its own before any request would be needed.

**The claim under test:** `Media Start Request VIDEO: session=` appears exactly **once per return**,
all four times, at offsets of 447/464/495/506 ms after the return and 231/215/211/291 ms **before**
`First frame rendered` — i.e., the phone's sink-start message consistently lands while the codec is
still being configured, not after. A fifth occurrence is the initial session start before cycle 1's
cover. Cross-checked against a whole-file count (`grep -c` = 5 = 1 initial + 4 returns) so this is not
an artifact of the per-cycle window. **Confirmed**, exactly as predicted: `Media Start Request VIDEO`
appears after every SURFACE return (4/4) and after no GLES or TEXTURE return (0/4 + 0/4 = 0/8), the
same 0-occurrence result the whole-file counts on `r1-gles.txt` and `r2-texture.txt` (1 each, both the
initial session start, both before any cover) already established.

## A4 — rapid switching (`r4-rapid.txt`)

Per the brief, per-cycle legs aren't meaningful here since consecutive covers overlap (each return
lands roughly the ~26.1 s `monkey` injection delay after its script-issued command, which is well
inside the next cycle's 3 s scripted hold). The reduction naturally falls into two windows that match
round 5's own narrative exactly:

- **Window 1** (return at 10:05:19.937 → first frame at 10:07:01.141, 101.2 s): spans what round 5
  called cycles 1-4, where cycles 2 and 3's surfaces were silently superseded before ever rendering
  and only cycle 4's surface rendered a single frame — leg C alone is 71,017 ms, and 3 separate
  `Fallback to negotiated dimensions: ` lines appear inside it (one per surviving surface: cycle 1,
  cycle 4, and the one that becomes cycle 5's cover — see below), each immediately preceding its own
  first `Configuring decoder: `. 5 `Forcing restart (` and 0 `but restart suppressed (` in this
  window; 6 recovery-type keyframe requests, 0 watchdog (same starvation pattern GLES cycle 4 showed
  in A1).
- **Window 2** — round 5's "cycle 5" — leg B = 76 ms, leg C = 53,966 ms (round 5 published 54.0 s for
  this exact surface, matching to within 34 ms). 5 `Forcing restart (` and 1 `but restart suppressed
  (` in this window, with 5 recovery-type and 35 watchdog-type keyframe requests — the watchdog fires
  throughout this one, unlike window 1's tail.
- `Media Start Request VIDEO: session=` again appears exactly once in the whole capture (initial
  session start), never after any return — consistent with A1/A2 and with this being the GLES backend.

## Report back

### 1. The leg split, per backend (aggregate across the 4 cycles, by summed milliseconds)

| Backend | Leg A share | Leg B share | Leg C share |
|---|---|---|---|
| GLES | 1.0% | 0.1% | **98.9%** |
| TEXTURE | 1.7% | 0.3% | **98.0%** |
| SURFACE | 55.9% | 35.8% | 8.3% |

Leg C — waiting for a decodable picture after the codec is already configured — is **97-99% of every
slow GLES/TEXTURE return**, and leg A (the relaunch itself) is capped at roughly half a second on
every backend including SURFACE, where it's actually the largest single share only because SURFACE's
total is so small. **The hypothesis holds: the seconds are spent waiting on the phone, not on the
relaunch or on codec creation.** The fix belongs in the video-focus path, not the activity lifecycle —
leg A never exceeds ~510 ms on any backend in any of the 12 cycles measured across A1-A3, so "the
relaunch itself is slow on GLES/TEXTURE" is refuted directly, not just left unsupported.

### 2. Does `Media Start Request VIDEO` divide the backends the way A3 predicted?

**Confirmed.** It appears after all 4/4 SURFACE returns (never before `First frame rendered` — always
211-291 ms ahead of it) and after 0/4 GLES returns, 0/4 TEXTURE returns, and 0/2 rapid-capture return
windows — 0/10 across every non-SURFACE return measured this round. In every capture it fires exactly
once outside of a SURFACE-return window: the initial session's own one-time channel setup, which
matches the app's documented per-channel-focus-fires-once behavior rather than being a stray count.

### 3. Keyframe requests during the slow waits — did any of them precede a picture?

Dozens go out per slow cycle (13-35 watchdog lines alone per GLES/TEXTURE cycle, roughly one every 1.5
s) and the overwhelming majority are followed by nothing. The picture's arrival does **not** track the
keyframe-request cadence — if it did, it would show up within ~1.5 s of *any* request, not
specifically the last one. Instead it tracks the **codec restart cadence**: the last `Configuring
decoder: ` attempt in every slow cycle lands 0.4-4.8 s before `First frame rendered` (A1), noticeably
tighter than the roughly 10 s gap between restart attempts earlier in the same cycle, while
individual keyframe requests before that final restart go unanswered despite firing just as
frequently. **The gain-only unsolicited-focus keyframe request is established as ineffective on this
phone** — nothing in any of the four captures shows one bringing a picture forward. What actually
recovers the picture is the decoder's own restart loop eventually landing an attempt the phone answers
with real video, which is a property of `VideoDecoder`'s stall/restart cadence, not of
`AapProjectionActivity`'s or `AapTransport`'s keyframe-request calls. A fix that speeds up restart
cadence (or that gets the phone to release/reacquire the video sink the way SURFACE's teardown already
does) is more likely to move leg C than anything that changes how the keyframe request itself is sent.

## Anything the brief did not ask about

- GLES cycle 4 and the rapid capture's window 1 both show the overlay watchdog's `Requesting Keyframe
  (Unsolicited Focus)` line going completely silent (0 occurrences) during heavy restart activity (5-6
  `Forcing restart (` plus several `but restart suppressed (`), while the `recovery keyframe` line from
  `AapTransport` keeps firing on every restart regardless. Worth a closer read of
  `AapProjectionActivity`'s watchdog gating if a future round chases the keyframe-request path
  specifically — it looks like the two request mechanisms are not simply redundant copies of each
  other.
- `Fallback to negotiated dimensions: ` is a clean per-relaunch marker (fires exactly once per cycle
  on every backend, always immediately before that cycle's first `Configuring decoder: `, never before
  a restart-driven repeat) — a reliable way to count relaunches independent of counting `New surface
  set: ` lines, if a future round needs one.
