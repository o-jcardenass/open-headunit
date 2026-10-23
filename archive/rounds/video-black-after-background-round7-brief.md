# Round 7 brief — does the focus cycle bring the TEXTURE/GLES return down to SURFACE's number?

Round 6 established the mechanism with no device time: 97-99% of every slow TEXTURE/GLES return is
spent waiting for a decodable picture after the codec is already configured, `Media Start Request
VIDEO` lands after 4/4 SURFACE returns and 0/10 others, and the gain-only keyframe request is
ineffective — dozens go out per slow return and none is followed by a picture.

This round tests the fix that follows from it. **The one number that decides it: return→picture on
GLES and TEXTURE.** Round 5's baseline is 6.8-116.4 s (GLES) and 5.4-52.5 s (TEXTURE); SURFACE's
0.68-0.80 s is the target shape.

## 1. Build

**Candidate:** `fix/warm-relaunch-keyframe` @ `eb4bc8e7` on `fork` — two commits stacked on round 5's
candidate.

```bash
git fetch fork --prune
git checkout -B fix/warm-relaunch-keyframe fork/fix/warm-relaunch-keyframe
git log --oneline -3
# expect exactly: eb4bc8e7, 19d7cc79, 1192daa5 — anything else is the wrong build
```

**Baseline:** `fix/822-stale-surface-callback` @ `1192daa5` — round 5's own candidate, i.e. the
parent of this branch. This round **does** need an A/B, because the measurement is a timing
distribution and this rig's returns vary by an order of magnitude between cycles. Build both, copy
each APK out of `apks/` as soon as it is built (§7a: `build_hur.sh` deletes the previous one), and
record both md5s.

Install order matters less than knowing which is live: both are debuggable builds of the same
versionCode, so `adb install -r` either way, and confirm with `pm path` + `md5sum` before every run.

### R0 — gate

`run_unit_tests.sh` on the candidate. Expected: round 5's counts **plus** a new
`WarmRelaunchKeyframePolicyTest` at **8**, so the full suite should be **260**, with
`DecoderRestartPolicyTest` 4, `ProjectionWatchdogPolicyTest` 4 and `DecoderStopPolicyTest` 6
unchanged. **A failure here stops the round** — none of this code has ever been compiled, let alone
run, so R0 is a real gate this time rather than a formality. Report the compiler's own message
verbatim if it does not build.

## 2. What changed and what to expect in the log

Two commits:

- **`19d7cc79`** keeps VPS/SPS/PPS and the parsed dimensions across a surface swap. Visible effect:
  **`Fallback to negotiated dimensions: ` should no longer fire on a relaunch** — round 6 measured
  it exactly once per relaunch on every backend. It should still fire on the first surface of a
  fresh session, where nothing is cached yet.
- **`eb4bc8e7`** escalates a relaunched surface that stays black. Two seconds after a new surface is
  claimed, if the session had already rendered and the phone is still sending, video focus is
  released and taken back 400 ms later — the sequence SURFACE gets by accident.

The new lines, all `grep -F`-verified against `eb4bc8e7`:

| Meaning | Line |
|---|---|
| the escalation fired *(composed — the surface's age in ms sits before this)* | `ms - cycling video focus` |
| the release went out | `CommManager: releasing video focus to force a keyframe` |
| the gain completing the pair | `retaking video focus to complete the keyframe cycle` |
| the cycle already spent, falling back to the old nudge | `relaunched surface still has no picture - requesting video focus (unsolicited)` |
| **our own** sink stop, not an unrequested one | `Video Sink Stopped -> Ignored (Forced Keyframe Request)` |

One grep trap, since three of those start with or contain a leading dash: `grep -F "- cycling …"`
reads the dash as an option and silently matches nothing. Use `grep -F -e "…"` or keep the `ms`
prefix as written above.

Round 5's lines all still apply and are not restated. Two whose meaning this round changes:

- **`Media Sink Stop Request: VIDEO` after a return is no longer automatically a FAIL.** When it is
  ours it is immediately followed by `Video Sink Stopped -> Ignored (Forced Keyframe Request)` and
  preceded within ~400 ms by the release line. One that appears *without* that pairing keeps rounds
  3-5's meaning and is a FAIL. Report which kind, with the surrounding lines, every time.
- **`Media Start Request VIDEO: session=` after a GLES/TEXTURE return is the fix working.** Round 6
  measured 0/10 of these; the whole point is to make them appear.

Forbidden anywhere, unchanged from round 5: `times in a row without rendering a frame`,
`Both codec types failed`, `Giving up to avoid an infinite restart loop`.

## 3. Settings keys

Identical to round 5, so the numbers are comparable:

| Key | Type | Value |
|---|---|---|
| `view-mode` | int | per run: `2` (R1, R4), `1` (R2), `0` (R3) |
| `enable-audio-sink` | boolean | `true`, whole round |
| `wifi-connection-mode` | int | `3` |
| `log-level` | int | `2` (INFO), unchanged |

Back up `settings.xml` before the first write, restore at the end.

## 4. Runs

**The cycle is round 5's exactly** — cover with `am start -a android.settings.SETTINGS`, return with
`monkey -p com.andrerinas.headunitrevived -c android.intent.category.LAUNCHER 1`, holds 5 s / 45 s /
180 s / 5 s, soak 30 s after each. Reuse `run_r5_cover_return.sh` and `run_r5_rapid.sh` from
`hur-wifi-test-scripts/round5-video-black/` unchanged; reuse round 6's `legs.sh` to reduce each
capture. **All timing from device-log timestamps only** — the ~26.1 s `monkey` injection delay
applies to every cycle.

| Run | Backend | Build | Notes |
|---|---|---|---|
| **R1** | GLES (`view-mode=2`) | candidate | **the point of the round** |
| **R2** | TEXTURE (`view-mode=1`) | candidate | |
| **R3** | SURFACE (`view-mode=0`) | candidate | regression guard — must stay at 0.68-0.80 s |
| **R4** | GLES (`view-mode=2`) | **baseline `1192daa5`** | same-day A/B against R1 |
| **R5** | GLES (`view-mode=2`) | candidate | rapid switching, 5× (cover → 3 s hold → return) |

For R1-R4 report per cycle: the three round-6 legs, the total, whether the cycle fired, and the
`Forcing restart (` count. For R5 report what round 5 reported — per-surface outcomes rather than
per-cycle budgets.

**PASS for R1/R2:** every cycle's total is **under 5 s**, and at least three of four fired the cycle
and were followed by `Media Start Request VIDEO`. **FAIL:** any cycle over 90 s, any forbidden line,
or an unpaired `Media Sink Stop Request: VIDEO` after a return.

**INCONCLUSIVE is a real verdict here.** If the cycle fires and the picture still takes tens of
seconds, that is the round's finding, not a failed run: it would mean the phone ignores a release it
did not initiate, and the fix would be wrong rather than mistuned. Report the timing between the
release, the gain and `Media Start Request VIDEO` in that case — it is what a redesign would need.

**R3 is a regression guard, not a measurement.** SURFACE's teardown already does all this, so the
policy should never fire there: expect **zero** `- cycling video focus` lines in R3. One appearing is
a finding worth reporting even if the timings are fine.

## 5. The two things most likely to be wrong

Neither has ever run, so look for them specifically rather than only at the verdicts.

1. **The 400 ms gap may be too short.** If the release and the gain both go out and no
   `Media Start Request VIDEO` follows, the phone is coalescing them. Diagnostic, and worth doing
   inside this round if R1 shows it: nothing on the rig can change the constant without a rebuild, so
   just report the timings and stop — do not try to work around it.
2. **The 2 s window may be firing on a surface that was about to render anyway.** If R3 shows any
   cycle at all, or R1/R2 show one firing within a few hundred ms of a first frame, the window is too
   tight. Report the gap between the cycle line and the following `First frame rendered`.

## 6. Do not re-run

Everything rounds 5 and 6 settled: which backends tear the surface down, the leg decomposition
itself, the `monkey` delay, round 5's forbidden-line verdicts. This round measures one thing against
one baseline.

## 7. Report back

1. **Return→picture per cycle, R1/R2/R4**, as numbers — the candidate against its own baseline on
   the same rig on the same day.
2. **Did the cycle fire, and did `Media Start Request VIDEO` follow it**, per cycle.
3. **Did `Fallback to negotiated dimensions: ` stop appearing on relaunches** — the other commit's
   only visible effect.
