# video-feed-backpressure — round 1 results

**Candidate:** `fix/video-feed-backpressure` @ `4731a2c7`       **Baseline:** `test/baseline-feed-hold` @ `bf389ccf`
**APK md5:** `6cae896e251229b303006b96985f4532` (candidate) / `6dff0c953b8dcf540269c2b874ae4ddd` (baseline)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, Native AA wireless (no USB path on this rig)
**Date:** 2026-08-24

## Setup notes

- `hur-wifi-test-scripts/` inventoried at round start; used `build_hur.sh`, `run_unit_tests.sh`,
  `set_hu_prefs.sh` as-is. No script fit the brief's "moving navigation screen" requirement, so a
  new one was added: `mock_drive.sh <duration_seconds> [start_lat] [start_lon]`, which feeds the
  phone's `gps` test-provider a continuously-drifting coordinate via `cmd location providers
  set-test-provider-location` (mock-location op granted to `com.android.shell`, not just Maps).
  Left in place per house rule.
- **One UI tap used, and only one**, per the "script it, don't drive it" exception for a feature
  with no scriptable trigger: Google Maps' recenter/"follow" button (screenshot pixel coords
  `(505, 246)` on the 1440×720 projected surface — confirmed `input tap` coordinates match
  screencap pixel space directly on this device despite `wm size` reporting the native portrait
  720×1440). Needed because a free map view does not auto-follow a moving mock-GPS dot; one tap
  put Maps into guided-navigation follow mode (camera panning/rotating, speed readout), after
  which zero further taps were used for the remainder of R1/R2.
- `settings.xml` carried the previous thread's leftovers as the brief warned: `wifi-direct-band=1`,
  `connection-issue-hotspot-config`, `hotspot-teardown-proven-unsafe`, `wifi-connection-mode=2`,
  `video-codec=H.265`. Backed up before any write (`round-video-feed-backpressure/settings-backup-
  pre-round.xml`) and restored byte-identical (`diff` clean) at the end of the round.
- `shared_prefs` ownership was already `u0_a168:u0_a168` (the app's own uid), not root — the §3
  check this brief asked for came back clean, no `chown` needed.
- **R3's hold=100 step was discarded and re-run once**: a manual/accidental disconnect on the
  physical rig landed ~1m18s into the first attempt (`AapService: Disconnected` at `06:48:14`,
  ~1m18s after the `Feed thread: DEBUG hold 100ms` line), the app auto-recovered and formed a
  second `createGroup SUCCESS` — a discard-rule hit (2× group in one capture). Re-ran clean
  (single group, full 5:20 span); only the clean re-run's numbers are reported below. The
  discarded first attempt's capture is kept as `r3_hold100.txt` (trimmed to its own window).
- **Process-management mistake, caught and fixed mid-round**: the `adb logcat` reader for each of
  R3's four steps was not killed before starting the next step's capture (only `logcat -c` was
  run, which clears the daemon ring buffer but does not disconnect existing readers — exactly the
  failure mode `[[feedback_always_stop_logcat]]` warns about). Each step's own file therefore kept
  growing with every subsequent step's output appended after it, up to 270 MB. This did **not**
  corrupt any reported number: each step's `grep` analysis was run immediately after that step's
  timer completed, before the next step's session existed, so the content read at analysis time
  was correct for that step. All five leaked readers were killed once noticed, and all five files
  were trimmed back to their own session's line range (bounded by the next step's `ACTIVELY
  LISTENING` line) before this report was finalized; a spot-check re-count after trimming matched
  the original live numbers to within a few seconds of trailing data (hold=25 pacing count moved
  322→333, the extra events falling in the ~10s between the timer firing and the next step's
  teardown, still within the same clean session). Every capture is bounded by exactly one
  `createGroup SUCCESS` after trimming.
- Two qualitative, non-instrumented observations from watching the physical rig, recorded here
  because the brief has no field for them: perceptible touch/control input lag at hold=25 and
  hold=40 (see R3), and one small transient visual artifact during R6 not corroborated by any
  drop/conceal/restart counter (see R6).
- `video-codec` and `wifi-connection-mode`/`native-ap-transport` were re-written every run per the
  brief's table; `debug-video-fault-injection`/`-rate` were deleted immediately after R4.
- Settings restored to the pre-round backup at the end, `diff`-verified byte-identical.

## R0 — build and unit gate

**PASS**

- Both APKs built from the exact SHAs in the brief (`git rev-parse HEAD` confirmed `4731a2c7...`
  and `bf389ccf...` before each build); md5s recorded above and different.
- `VideoFeedThrottlePolicyTest`: **8** test methods. `KeyframeCycleEscalationPolicyTest`: **43**.
- Total: **749**, exact match against the brief's expectation (738 at `048f4eaf` + 11 new).
- Installed with `adb install -r` throughout; the one exception was the baseline's first install,
  which hit `INSTALL_FAILED_VERSION_DOWNGRADE` (candidate's versionCode 100 vs baseline's 98) —
  used `-r -d` per the template's known-quirk list, `settings.xml` preserved across it (verified).
- Confirmed live APK via `pm path` + `md5sum` before every arm.

## R1 — the positive control (baseline `bf389ccf`, hold=40ms, ~10:19)

**PASS**

- `Feed thread: DEBUG hold 40ms per frame` present **exactly once**.
- `dropped` **non-zero in every one of ~123 throughput windows**, ranging 30–164 (most windows
  142–146 at steady state). Full per-window series retained in `r1.txt`.
- Discard check clean: `createGroup SUCCESS`=1, one `MATCH! Starting AapService` with zero
  attached group churn (the phone's own benign reconnect, not contamination per the template's
  narrowed rule), single `p2p-wlan0-0` interface, one SSL handshake, zero `Magic Garbage`.
- No value-stepping needed — hold=40 produced drops on the first attempt, so R2 used the same
  value.
- Visually reproduced the reporter's exact symptom: a screenshot mid-run showed heavy macroblocking
  indistinguishable from the bug report, and the vendor decoder itself logged `an unintentional
  loss of picture occures!` — corroborating evidence beyond the counters.

## R2 — pacing replaces shedding, the point of the round (candidate `4731a2c7`, hold=40ms, ~10:13)

**PASS, all three conditions**

- `Feed queue full - pacing`: **539 log lines, 12,817 total events** including `(+N more)`
  suffixes.
- `enqueueWait`: non-zero and rising — **2443ms → ~4470ms**, then plateaus at that steady state for
  the rest of the session (physically expected: the system reaches equilibrium rather than
  climbing indefinitely).
- **`dropped=0` in every single throughput window** (only unique value observed across the whole
  session).
- `rendered`/`fed` held steady at 112–117 (22–23fps) for the great majority of the session, with
  one transient ~55s dip (windows 20–31, ~10fps) that fully recovered and never returned. In every
  window of that dip, `fed` matched `rendered` exactly (e.g. `rendered=50, fed=50`) — no frames
  were lost inside the decoder during the dip, confirming it was fewer frames *arriving* from the
  phone (encoder/source-side variance, plausibly Maps' free-map→guided-navigation transition),
  not the candidate's pacing costing frames.
- `Decoder restart requested: sync_stall`: **0**, as required.
- Discard check clean: `createGroup SUCCESS`=1, `MATCH! Starting AapService`=0, single
  `p2p-wlan0-1` interface (bump from R1's `-0` is a fresh session's own group, not mid-run churn),
  one SSL handshake, zero `Magic Garbage`.
- Screen state matched R1: same mock-drive route, plus one recenter tap that put Maps into guided
  navigation (visibly cleaner picture than R1's blocky baseline, consistent with `dropped=0`).

## R3 — what the pacing costs the rest of the session (candidate, 4×5min, Spotify playing)

**No PASS condition — reporting the numbers as asked.**

| Hold | Pacing events | Underruns/min | `enqueueWait` median / max | Median fps |
|---|---|---|---|---|
| 10ms | 0 | 0 | 0ms / 0ms | 52 (n=64) |
| 25ms | 333 | 0 | 4303ms / 4348ms | 34 (n=68) |
| 40ms | 318 | 0 | 4422ms / 4489ms | 22 (n=65) |
| 100ms | 325 | 0 | 4597ms / 4700ms | 9 (n=68, clean re-run) |

- **10ms was inert exactly as predicted** — zero pacing lines, `enqueueWait=0ms` throughout, and
  the highest fps of the four steps. Confirmed as the second control.
- **`disabled due to previous underrun` never fired at any hold value**, including 100ms — by the
  audio metric alone, the pacing fix is free.
- **But it is not free**: the operator watching the physical rig reported perceptible touch/control
  input lag at both hold=25 and hold=40 (not checked at 10 or 100 — no interaction was attempted
  at those steps). This is exactly the mechanism the brief flagged as untested — the read thread
  carries control messages on the same paced path as video — surfacing as real, felt lag with
  **zero signal in the one instrumented cost metric available (audio underruns)**. Worth flagging
  for anyone deciding the budget value: the audio-underrun count alone is not sufficient evidence
  that a given hold value is safe.
- fps scaled with the hold value almost exactly as the mechanism predicts (1000/hold ≈ ceiling):
  25ms→34fps, 40ms→22fps, 100ms→9fps (~10fps ceiling).

## R4 — budget expiry still sheds (candidate, hold=250ms, fault-injection mode 5 rate 3)

**INCONCLUSIVE against the brief's stated PASS condition — but with a finding that needs its own
paragraph, not a bare dismissal.**

- Fault injector: 73 candidates seen, **24 faults injected** (1-in-3 rate).
- `Decoder restart requested: sync_stall`: **16** — the codec **did** genuinely wedge repeatedly,
  contrary to the brief's stated assumption that "no hold value can produce" a wedge on this rig.
  The 250ms hold evidently was not the limiting factor once real fault injection was layered on
  top of it.
- `dropped` stayed **0 in every window nonetheless** — the specific PASS signal (an enqueue giving
  up on a frame after the full 1s budget) never fired.
- **The escalation budget was fully exhausted** (`cycling video focus (3/3)` at `06:58:56`) and
  never refunded, because the injector kept firing continuously for the rest of the run. The
  picture went to **sustained black (`rendered=0`) for 54 consecutive 5-second windows** — over
  4.5 minutes — and had **not recovered by the time the capture was stopped**.
- Reading of the mechanism: the `sync_stall` watchdog appears to win the race against the
  enqueue's own 1-second shed-budget every time here, so the "shed after budget expiry" path this
  run was built to exercise is preempted before it can fire. That is a plausible, not confirmed,
  explanation — offered for whoever next touches this interaction, not asserted as fact.
- Per the brief's own instruction this run is a bonus, not a gate, and was not extended or retried
  with other modes. Reported as **INCONCLUSIVE** on the stated PASS condition, with the sustained
  unrecovered black screen flagged separately as a real, previously-unquantified interaction
  between aggressive fault injection and this candidate's pacing+escalation stack.

## R5 — a teardown is not held behind the wait (candidate, hold=100ms)

**PASS, all three conditions**

- Pacing confirmed active (`Feed queue full - pacing` present) before triggering
  `headunit://disconnect`.
- `Decoder stopped: CommManager: doDisconnect` at `07:05:42.731`, **~345ms** after the disconnect
  intent's transition landed (`07:05:42.386`) — well within the 1-second requirement.
- Zero ANR in the capture.
- A second session (formed via a phone Bluetooth cycle, since Native AA has no reconnect deep
  link) started its own independent feed thread (`[99]` vs the first session's `[82]`) with **no**
  stale `Feed thread started`/`Feed thread: DEBUG hold` lines from the torn-down first session
  bleeding through — confirmed by full line-number ordering in the capture.

## R6 — H.265 is untouched, the regression guard (candidate, hold=0)

**PASS, all three conditions**

- `enqueueWait=0ms` in **all 122** throughput windows (only value observed).
- `Feed queue full - pacing`: **0**.
- `dropped=0` in **all 122** windows.
- Median `rendered` fps: **52**, over a clean 10:05 session — video was carried at a healthy rate
  throughout, not silently starved.
- One transient visual artifact was observed on-screen mid-run; a screenshot taken immediately
  after showed a clean, sharp picture, and no drop/conceal/restart signal appears anywhere in the
  capture around that time. Recorded as an uninstrumented, uncorroborated observation — most
  likely a one-off source-side compression artifact — not evidence against this PASS.

## R7 — the escalation refund, may not fire (candidate, hold=40ms, H.264, 15:01 continuous)

**Valid non-appearance, as the brief allows.**

- `quiet stream earned back` and `cycling video focus`: **both never appeared.**
- `Feed queue full - pacing`: **879** events over the full 15 minutes.
- `dropped=0` in every window, `Decoder restart requested: sync_stall`=0.
- Consistent explanation: with pacing preventing any drops, the escalation ladder was never
  triggered in the first place, so there was nothing for the refund path to act on. The refund
  path stays hardware-unproven this round, covered by `KeyframeCycleEscalationPolicyTest` per the
  brief's own fallback. Not extended, per instruction.

## Round-wide checks

| Check | r1 | r2 | r3×4 | r4 | r5 | r6 | r7 |
|---|---|---|---|---|---|---|---|
| `Decoder restart requested: sync_stall` | 0 | 0 | 0/0/0/0 | **16** | 0 | 0 | 0 |
| `Input buffer full. Dropping frame.` | 0 | 0 | 0/0/0/0 | 0 | 0 | 0 | 0 |
| `rendered=0` windows | 0 | 0 | 0/0/0/0 | **54** | 0 | 0 | 0 |
| `send failed (ret=` | 0 | 0 | 0/0/0/0 | 0 | 3* | 0 | 0 |

\* r5's 3 hits all land at `07:08:04`, during the app's own clean shutdown for the next step's
settings change (`AapTransport: send failed (ret=-1); the link is already gone` right alongside
`MediaCodec::reclaim` and codec teardown) — not a spontaneous link death during a live session.
Across every *live, paced* session in this round, `send failed (ret=` is **zero**. That is
consistent with — but does not prove — pacing preventing the reporter's link-death signature; the
brief was explicit this is not evidence either way, only a number worth having.

Discard rule (`createGroup SUCCESS` > 1 in one capture) hit exactly once, in R3's first hold=100
attempt, for the reason given in Setup notes; that attempt was discarded and re-run clean. Every
other capture is bounded by exactly one `createGroup SUCCESS`.

## Report back — the five numbers

1. **R0**: 749/749 exact match, `VideoFeedThrottlePolicyTest`=8, `KeyframeCycleEscalationPolicyTest`=43.
2. **R1**: `dropped` non-zero in every window at hold=40ms (30–164 per window); no baseline, no
   round — the baseline reproduced the defect on the first attempt.
3. **R2**: `dropped=0` in every window (vs R1's consistently non-zero), with `rendered`/`fed`
   matched throughout (no hidden frame loss) — the fix reaches the mechanism and pacing genuinely
   replaces shedding.
4. **R3**: **zero audio underruns per minute at every tested hold (10/25/40/100ms)** — but real,
   felt input lag at 25ms and 40ms that the audio metric does not capture. Anyone picking a
   shipping hold/budget value should not rely on the underrun count alone.
5. **R6**: `enqueueWait=0ms` in all 122 windows on the H.265 arm — completely undisturbed.

**Net**: the core mechanism (R1/R2) is confirmed and clean, H.265 is unaffected (R6), teardown is
not held behind the wait (R5), and the audio-cost question R3 was built to answer comes back at
zero — but paired with an unmeasured, user-felt input-lag cost at the same hold values, and R4
surfaced a real, previously-unknown black-screen-until-capture-end interaction between aggressive
fault injection and the pacing+escalation stack that is worth its own look before this ships,
distinct from the INCONCLUSIVE verdict on its stated PASS condition.
