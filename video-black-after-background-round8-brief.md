# Round 8 brief — the escalation window at 850 ms, and the race round 7 found

Round 7 proved the fix and, in doing so, measured the two things this round changes. It is a short
round: three runs, no baseline build, no new scripts.

**The number to beat is round 7's own 3.0-3.2 s.** Target: **under 2.5 s** on GLES and TEXTURE, with
SURFACE unmoved at 0.68-0.80 s.

## 1. Build

**Candidate:** `fix/warm-relaunch-keyframe` @ `a304bf14` on `fork` — two commits on top of round 7's
candidate.

```bash
git fetch fork --prune
git checkout -B fix/warm-relaunch-keyframe fork/fix/warm-relaunch-keyframe
git log --oneline -4
# expect exactly: a304bf14, 2ccfa641, eb4bc8e7, 19d7cc79 — anything else is the wrong build
```

**No baseline build this round.** Round 7's candidate is the comparison and its figures are
published per cycle; it also proved stable to ±0.2 s across eight cycles, unlike the build it
replaced. Build and install one APK, record its md5, confirm live with `pm path` + `md5sum`.

### R0 — gate

`run_unit_tests.sh`. Expected: full suite **261** (round 7's 260 plus one),
`WarmRelaunchKeyframePolicyTest` **9** (was 8), `DecoderRestartPolicyTest` 4,
`ProjectionWatchdogPolicyTest` 4, `DecoderStopPolicyTest` 6 — all unchanged. **A failure stops the
round**, and the new test is the one most worth reading the message of: it asserts the escalation
window stays at least twice the slowest first frame ever measured, and its failure text prints both
numbers.

## 2. What changed

**`2ccfa641` — the escalation window drops from 2000 ms to 850 ms.** Round 7 measured the whole
recovery at a flat 3.0-3.2 s, of which this constant was the largest single term (the rest: ~480 ms
relaunch, ~402 ms release→retake, ~150 ms phone response, ~145 ms decode). The window was set before
any of the code had run; the healthy path it must clear is now known to be at most 404 ms. 850 ms is
twice that. **Expect the return to land near 2.0 s** and the `New surface set:` → escalation gap to
read ~850 ms where round 7 read 2000-2001 ms.

**`a304bf14` — a surface that dies mid-configure no longer restarts into itself.** This is round 7's
own R3 finding. `decode()` checks the surface is valid, the framework destroys it before
`MediaCodec.configure`, `start()` throws, and the old code scheduled a restart that could only fail
the same way — each attempt asking the phone for a keyframe, which is how an unsolicited video sink
start arrived while no surface existed. Now it bails and waits for `setSurface()`.

New lines, all `grep -F`-verified against `a304bf14`:

| Meaning | Line |
|---|---|
| the race caught and handled — the fix working | `Decoder start aborted: the surface went away mid-configure` |
| the race's symptom; may still appear | `Failed to start decoder` |
| **must not follow the above** | `Decoder restart requested: decoder_start_failed` |

The grep trap from round 7's brief still applies: several of these contain a leading dash in context,
so use `grep -F -e "…"`.

## 3. Settings

Identical to rounds 5 and 7, so the numbers stay comparable: `view-mode` per run (`2` GLES / `1`
TEXTURE / `0` SURFACE), `enable-audio-sink=true`, `wifi-connection-mode=3`, `log-level=2`. Back up
`settings.xml` first, restore at the end.

## 4. Runs

Round 7's matrix and scripts unchanged — `run_r5_cover_return.sh` for the cycles, `legs.sh` to
reduce each capture, holds 5 s / 45 s / 180 s / 5 s, 30 s soak. Device-log timestamps only.

| Run | Backend | PASS condition |
|---|---|---|
| **R1** | GLES (`view-mode=2`) | every cycle **under 2.5 s**; escalation fires 4/4 and `Media Start Request VIDEO` follows each |
| **R2** | TEXTURE (`view-mode=1`) | same |
| **R3** | SURFACE (`view-mode=0`) | 0.68-0.80 s, **zero** escalations |

Per cycle, report: the three round-6 legs, the total, the `New surface set:` → escalation gap
(expect ~850 ms), the release→retake gap (expect ~402 ms, unchanged), and the `Forcing restart (`
count.

FAIL on any cycle over 90 s, any forbidden line (`times in a row without rendering a frame`,
`Both codec types failed`, `Giving up to avoid an infinite restart loop`), or any
`Media Sink Stop Request: VIDEO` after a return that is **not** immediately followed by
`Video Sink Stopped -> Ignored (Forced Keyframe Request)`.

### Across all three runs, count

- `Failed to start decoder` versus `Decoder restart requested: decoder_start_failed`. **The first may
  occur; the second must be zero.** The race is intermittent, so zero occurrences of *both* proves
  nothing either way — say which you saw. If the first appears, quote the surrounding lines and
  confirm `Decoder start aborted:` follows it.
- `Fallback to negotiated dimensions: ` — still expected exactly once per capture, on the initial
  launch only, as round 7 established.

## 5. What to watch for, since 850 ms is the one loosened gate

Round 7 checked both of these and found neither; at 850 ms they are live again and are the reason
this round exists.

1. **An escalation firing on a surface that was already about to render.** The signature is a
   `ms - cycling video focus` line within a few hundred ms of a `First frame rendered` that the
   escalation cannot have caused — i.e. a first frame arriving *before* the retake, or within ~100 ms
   of it. Report every escalation's gap to its own first frame, not just the totals.
2. **Any escalation at all in R3.** SURFACE's teardown already releases focus, so the policy should
   never fire there. Round 7 saw exactly one, caused by the race that `a304bf14` now fixes — so a
   firing here is either that race recurring (check for `Failed to start decoder` nearby) or the
   window now being too tight.

If either shows up, the answer is a value between 850 and 2000, not a redesign — report the numbers
and stop rather than trying to find it on the rig.

## 6. Do not re-run

Everything rounds 5-7 settled: which backends tear the surface down, the leg decomposition, the
`monkey` injection delay, the A/B against `1192daa5` (round 7 did it; this round's comparison is
round 7's own published per-cycle figures), and the mechanism itself.

## 7. Report back

1. **Return→picture per cycle, R1/R2**, against round 7's 3.0-3.2 s.
2. **The `New surface set:` → escalation gap**, confirming the constant took effect.
3. **`Decoder restart requested: decoder_start_failed` count across all three runs** — expected zero.
4. **Any escalation in R3**, or confirmation there were none.

## 8. One process note from round 7

Round 7's own setup notes recorded that none of the five `stdbuf -oL adb logcat` capture processes
was killed before the next run started, so every earlier capture kept growing with later rounds'
traffic — `r1-gles.txt` reached 499 MB against the ~124 MB of its own window. The figures were
salvaged by filtering on each round's PID, and the report said so, which is exactly right. Simpler
this round: **kill the capture process at the end of each run** before starting the next, and confirm
with `ps aux | grep logcat` that none is left. `adb logcat -c` does not disconnect an attached reader.
