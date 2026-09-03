# Dropped-frame keyframe recovery — round 5 brief

## 1. Build and baseline

**Candidate:** `fix/830-request-keyframe-on-dropped-frame` @ **`62889f29`** (on the fork).

```bash
git fetch fork fix/830-request-keyframe-on-dropped-frame
git checkout 62889f29
```

One new commit on round 2's `ec0a2d28`, which is on `563ae013` → `fix/warm-relaunch-keyframe` @
`a8e21850` → upstream `main` @ `a8830caa`. No history rewrite; a fast-forward works.

**This is the shipping candidate**, not a test branch. Everything below decides whether it opens a
PR. `test/830-keyframe-lever-probe` and `test/830-lowered-escalation-threshold` are finished with and
must never merge.

**No baseline APK.** The comparison for R2 is rounds 1–3, which ran the identical provocation on the
identical rig and produced **zero** focus cycles between them.

## 2. What changed and why

Rounds 1–4 settled everything except the tuning:

- The gain-only nudge is inert (three rounds, 419+ fires).
- No cheaper lever exists — round 4 ruled out `UpdateUiConfigRequest` both ways and showed
  `VIDEO_FOCUS_NATIVE_TRANSIENT` costs exactly what a full `NATIVE` release costs.
- The focus release/regain cycle works, producing a keyframe 0.52–0.78 s after the release.
- The phone runs a **fixed ~69 s keyframe period** (round 4: median 69.448 s, spread under 2 s). So
  one shed reference frame washes the picture for ~35 s on average, up to ~69 s. That is the bug.

The old escalation waited 150 s for an unbroken drop episode, which the reported fault — a single
lost frame — can never produce. `62889f29` replaces that trigger:

- A shed frame starts an **unrepaired clock**; a keyframe reaching the codec stops it.
- Two seconds unrepaired earns a focus cycle. Expected total: **~2.5–2.8 s of corruption**, against
  ~35 s of waiting the GOP out.
- Budget is **3 cycles per session, at least 60 s apart** — raised from 1 on round 4's seven clean
  cycles.
- Only the dropped-frame path can escalate; a decoder error now clears the clock instead, so nothing
  reaches for the focus lever inside `WarmRelaunchKeyframePolicy`'s window.
- `VideoKeyframeScanner` (validated in round 4's R1) is what stops the clock.

**The mechanism has never been observed repairing an actual wash.** Every round so far either had
`dropped=0` (rounds 1, 2, 4 hardware path) or forced the cycle with a test-only constant (round 3).
That is what this round is for.

## 3. What is different about this round

- **R1 comes before the messy run on purpose.** It is a regression guard: on a healthy stream the new
  code must be completely silent. Rounds 1, 2 and 4 all measured `dropped=0` on hardware decoding, so
  any escalation line at all in R1 means the trigger fires when nothing is broken — a worse fault than
  the bug being fixed, and grounds to stop the round.
- **R2 uses the same provocation as rounds 1–3** (`force-software-decoding=true` **plus
  `software-video-decoder=0`** — the default routes through bundled FFmpeg, which returns before the
  queue and would measure nothing). Those rounds produced 46–242 drop triggers and **zero** cycles.
  The same lever now should produce cycles, which is the whole point of the retune.
- **Expect the picture to blink when a cycle fires.** That is the mechanism, confirmed visually in
  round 3. It is not a fault.
- **Under sustained overload a repaired picture gets re-broken immediately.** Round 3 measured ~29.5 s
  to `dropped=0` for that reason. So R2 measures the **mechanism's own timing** — drop to keyframe —
  not how long the screen stays clean. Do not read a slow return to `dropped=0` as the fix failing.
- `log-level=0` (VERBOSE) throughout; capture to a file via `stdbuf -oL` as always.
- Screen moving for the whole capture, same fallback as rounds 1 and 4 if navigation cannot be
  started. Say in Setup notes what was on screen.

## 4. Settings keys

| Key | Type | R1 | R2 |
|---|---|---|---|
| `log-level` | int | `0` | `0` |
| `video-codec` | string | `H.264` | `H.264` |
| `view-mode` | int | `1` (TEXTURE) | `1` |
| `force-software-decoding` | boolean | **delete the key** | `true` |
| `software-video-decoder` | int | **delete the key** | `0` |

## 5. The lines that decide every run

All verified with `grep -F` against `62889f29`.

| Line | Level | Means |
|---|---|---|
| `VideoDecoder: dropped a reference frame, requesting keyframe` | W | **T0** — a shed frame started the clock |
| `AapTransport: Requesting recovery keyframe (unsolicited focus gain).` | W | the nudge, sent first every time |
| `AapTransport: picture unrepaired for Nms - cycling video focus (n/3)` | W | **the escalation fired** — new in this build |
| `AapTransport: retaking video focus to complete the keyframe cycle` | W | the regain, 400 ms later |
| `AapTransport: picture unrepaired for Nms, no cycle available now` | W | the budget or cooldown refused one |
| `VideoDecoder: keyframe reached the codec (N bytes)` | I | **the repair** — clock stops here |
| `Media Sink Stop Request: VIDEO` | I | phone tore its sink down (expected after each cycle) |
| `Media Start Request VIDEO` | I | phone rebuilt the stream — note `session=` |
| `Codec initialized: ` | I | should appear **zero** times mid-session (round 4 measured the component surviving seven cycles) |
| `Throughput over ` | I | `rendered=`, `fed=`, `dropped=`, `skipped=` |

## 6. Runs

### R0 — build and unit-test gate

`build_hur.sh`, then `run_unit_tests.sh`.

- **PASS:** expect **286** tests. Round 4's 284 was 272 + 12 (`VideoKeyframeScannerTest`); this build
  replaces the 8 old `KeyframeCycleEscalationPolicyTest` cases with 10 rewritten ones. Record the APK
  md5, copy it out of `apks/`, install with `adb install -r`, confirm the live md5.
- **FAIL:** stops the round. Quote the compiler output verbatim. First compile of `62889f29`.

### R1 — silence on a healthy stream (regression guard, gate for R2)

Hardware decoding, no probes, 5 minutes undisturbed, screen moving.

- **PASS:** `dropped=0`, and **zero** occurrences of `dropped a reference frame`,
  `Requesting recovery keyframe`, `picture unrepaired`, `cycling video focus`. Record the count of
  `keyframe reached the codec` and the gaps between them — expect ~69 s, and it doubles as a check
  that round 4's fixed-GOP finding reproduces.
- **FAIL:** any escalation line with `dropped=0`. **Stop the round and report it.** The trigger is
  firing when nothing is broken.

### R2 — does the chain actually repair a wash? (the point of the round)

**Gated on R1 = PASS.** Provocation settings from §4. Run **10 minutes**.

For **every** `cycling video focus` line, report the full chain with wall-clock deltas:

| Step | Line |
|---|---|
| T0 | `dropped a reference frame` |
| T0 | `Requesting recovery keyframe` (same millisecond) |
| T0 + ~2 s | `picture unrepaired for Nms - cycling video focus (n/3)` |
| +400 ms | `retaking video focus` |
| +? | `Media Sink Stop Request: VIDEO`, then `Media Start Request VIDEO` with `session=` |
| **+?** | **`keyframe reached the codec`** — this delta from T0 is the headline number |

- **PASS:** at least one complete chain, with T0 → `keyframe reached the codec` in roughly **2.5–3 s**.
  Compare against ~35 s, which is what the same drop cost before this build.
- **INCONCLUSIVE:** drops occur, `dropped a reference frame` fires, but no cycle ever does — report
  every `picture unrepaired … no cycle available now` line and what it said, and check whether a
  keyframe was arriving inside the 2 s window and legitimately clearing the clock each time.
- **FAIL:** a cycle fires and no `keyframe reached the codec` follows it within a few seconds. That
  would mean the lever stopped working, contradicting rounds 3 and 4.

**Also report, from the same capture:**

1. **Budget accounting.** Total `cycling video focus` count (must be **≤ 3**), and the interval
   between consecutive ones (must be **≥ 60 s**). A fourth cycle or a pair closer than a minute is a
   defect, not a tuning question.
2. **The #755 sentinel.** `Throughput` `rendered` fps across the minutes *after* each cycle. Round 4
   held 37–57 fps through seven cycles. Sustained degradation that does not recover outranks
   everything else in this round — quote it, keep the capture, and say so at the top.
3. **`Codec initialized:` count.** Round 4 measured zero mid-session across seven cycles. Anything
   above zero is new behaviour and a finding.
4. **How often the clock cleared on its own** — `keyframe reached the codec` inside 2 s of a
   `dropped a reference frame`, with no cycle in between. That is the escalation correctly declining
   to fire, and its frequency tells us how much of the time the 2 s window is doing useful work.

## 7. Do not re-run

- **Whether the nudge works** (rounds 1, 2, 4 — inert, three times).
- **Whether a cheaper lever exists** (round 4 — no; the protocol has no keyframe-request message).
- **Whether a single forced cycle is survivable** (rounds 3 and 4 — yes, seven of them).
- **Anything about surface teardown or warm relaunch.** This round never disturbs the surface, and
  the new code deliberately steps back from that window.

## 8. Report back

One number decides the PR: **T0 → `keyframe reached the codec`, in seconds, for each cycle.** If that
is ~2.5–3 s where the same drop previously cost ~35 s, the fix does what four rounds of measurement
say it should and the branch is ready.

Two numbers decide whether it ships as tuned rather than needing another pass: the **cycle count
against the cap of 3**, and the **spacing against the 60 s cooldown**.

And one line in "Anything the brief did not ask about" is worth having: how many drop incidents were
repaired by the phone's own keyframe inside the 2 s window without any cycle at all. If that is most
of them, the window could be widened and the lever used even more sparingly.
