# Dropped-frame keyframe recovery — round 1 brief

## 1. Build and baseline

**Candidate:** `fix/830-request-keyframe-on-dropped-frame` @ **`563ae013`** (on the fork).

```bash
git fetch fork fix/830-request-keyframe-on-dropped-frame
git checkout 563ae013
```

Two commits, stacked on `fix/warm-relaunch-keyframe` @ `a8e21850` (the branch whose round 8 closed
out `video-black-after-background`), which is itself on upstream `main` @ `a8830caa` (the 3.2.4
release). History was **not** rewritten; a fast-forward works.

**Neither commit has ever been compiled anywhere.** R0 is their first build, and a failure there
stops the round — see §6.

**No baseline APK is needed.** Everything this round measures is either a property of the rig
(does it drop frames at all?) or a property of the phone (does it answer a nudge?), and the one
control the round needs is a settings change on the candidate, not a second build.

## 2. What this is and why it exists

Issue #830 reports that on 3.2.4 the picture randomly washes out toward white with blocky
corruption, on both USB and wireless, and repairs itself "after a minute or so". The photo shows
the map still visible *under* the wash — that is prediction drift after a lost reference frame,
not a black screen, not a stall.

3.2.4's link-stall catch-up (`9f98afd1`) put a bounded 12-slot queue between the transport and
MediaCodec. On overflow it sheds the arriving frame; the pre-existing "Input buffer full" path
sheds one too. Both were silent. A shed frame is a reference every later frame predicts from, so
the picture drifts until the phone's own next keyframe — which on a mostly static screen is the
reported minute.

`563ae013` makes both drop sites ask the phone for a keyframe, using the gain-only unsolicited
`VideoFocusEvent` that corrupt-frame recovery already sends, throttled to one per second and
silent until the codec has rendered a frame.

**The whole round exists because that ask is unproven.** Two independent code reviews of the fix
agreed on everything except this: nothing anywhere shows a *mid-stream* gain-only nudge producing
a keyframe. What we do have is the opposite measurement, from the `video-black-after-background`
rounds recorded on this branch: while the phone believes the head unit already holds video focus,
dozens of unsolicited gains went out per slow return and **`Media Start Request VIDEO` followed 0
of 10 of them** — only a real release/regain cycle moved the phone. A running stream is also a
state where the phone believes we hold focus. If the nudge is inert here too, `563ae013` is safe
but does nothing for #830, and the fix has to become a different mechanism.

So this round asks two questions, in order:

- **Q1 — can this rig shed frames at all?** If it never does, the fix cannot be exercised on this
  hardware and its coverage belongs to the JVM tests.
- **Q2 — when a shed frame fires the nudge, does the phone answer with a keyframe?** This is the
  point of the round.

## 3. What is different about this round

- **Native AA wireless is the only transport here** (§7a: no USB accessory path). #830 reports the
  fault on USB *and* wireless, so testing only wireless is not a gap — the drop sites are downstream
  of the transport and identical either way.
- **Leave `view-mode` at its default (1, TEXTURE).** This round wants one long, undisturbed
  session with a stable surface. TEXTURE never destroys its surface on this rig, which keeps the
  whole warm-relaunch escalation path out of the capture. Do not press Home, do not relaunch, do
  not change view mode.
- **R2 has a designed-INCONCLUSIVE outcome, stated up front so it is not read as a failure.** It
  forces software decoding to provoke drops. If the software decoder is so slow it never renders a
  single frame, the fix's gate stays shut by design and no nudge can fire — `lastFrameRenderedMs`
  is still 0. That is a correct result about the run, not a defect. Record it and carry on.
- **R3 needs `log-level=0` (VERBOSE) and the driver stack floods logcat** (§7a). The flood costs
  disk and grep time here, not evidence: the capture goes to a **file** via `stdbuf -oL`, so
  nothing wraps the way it would if the buffer were read back afterwards with `logcat -d`. Do not
  shorten R3 to protect the ring buffer. Do keep R3 to the one run that needs it — R0/R1/R2 all
  work at `log-level=2`.
- **The stream must be moving.** A parked map drops Android Auto to a few frames a second all by
  itself, which is the single most misread signal in this tracker. Have navigation running with the
  map animating, or the phone playing something with a moving UI, for the whole of R1–R3. Say in
  Setup notes what was on screen. Prior rounds on this rig measured 29–56 fps of real traffic, so
  that is the number to expect when it is behaving.

## 4. Settings keys this round needs

Use `set_hu_prefs.sh` (§5) — every run below writes more than one key, and the single-key
`set_hu_pref.sh` relaunches the app per call.

| Key | Type | Element |
|---|---|---|
| `log-level` | int | `<int name="log-level" value="2" />` (R0–R2) / `value="0"` (R3) |
| `force-software-decoding` | boolean | `<boolean name="force-software-decoding" value="true" />` |
| `software-video-decoder` | int | `<int name="software-video-decoder" value="0" />` |
| `video-codec` | string | `<string name="video-codec">H.264</string>` |

**`software-video-decoder=0` is not optional when `force-software-decoding=true`.** Its default is
**1 (BUNDLED_FFMPEG)**, and the bundled FFmpeg path returns from `decode()` before it ever reaches
the queue — so leaving the default would route the run around both drop sites and quietly measure
nothing. `0` is `DEVICE_MEDIACODEC`, which is the path that has them.

`video-codec=H.264` is pinned so R1 and R2 are the same stream; this rig has been seen negotiating
both (`c2.unisoc.avc.decoder` in six prior rounds, `c2.unisoc.hevc.decoder` in one).

## 5. The lines that decide every run

All verified with `grep -F` against `563ae013`. Level matters — this rig floods, and §1 of the
template's warning about guards applies: these were checked at the call, and none of them is
wrapped in `LOG_VERBOSE` except `RECV:`.

| Line | Level | Means |
|---|---|---|
| `Codec initialized: ` | I | which component was chosen — record it for every run |
| `Throughput over ` | I | the census line: `rendered=`, `fed=`, `dropped=`, `skipped=` |
| `Input buffer full. Dropping frame.` | W | codec gave no input buffer for 300 ms; a frame was shed |
| `VideoDecoder: dropped a reference frame, requesting keyframe` | W | **new in `e1bf548a`** — a shed frame passed the gate and asked |
| `Frame larger than the codec input buffer:` | W | **new in `563ae013`** — oversized frame dropped whole |
| `AapTransport: Requesting recovery keyframe (unsolicited focus gain).` | W | the nudge actually went out — **this is T0 for Q2** |
| `AapVideo: Frame corrupted, requesting keyframe to recover stream` | W | the *other* requester; must be separated from ours |
| `Media Start Request VIDEO` | I | the phone re-ran video sink setup |
| `RECV: VIDEO` | **VERBOSE only** | per-message `flags:` and `size:` — R3's instrument |

Note that two independent paths send the identical nudge. When counting for Q2, always pair each
`AapTransport: Requesting recovery keyframe` with the line immediately before it to attribute it,
and report the two counts separately.

## 6. Runs

### R0 — build and unit-test gate

**This is the first compile of both commits.** Build the candidate with `build_hur.sh` and run
`run_unit_tests.sh`.

- **PASS:** both succeed. Record the APK md5 and copy it out of `apks/` immediately (§7a:
  `build_hur.sh` deletes the previous APK on its next run).
- **FAIL:** stops the round — quote the compiler output verbatim and stop. Everything below needs
  this APK. The new unit tests to look for are in `VideoRecoveryPolicyTest`: `a drop before the
  first rendered frame never asks`, `the first drop after a rendered frame asks immediately`,
  `drops share the corrupt-frame throttle window`.

Install with `adb install -r` (never uninstall — §5).

### R1 — baseline drop census (answers Q1)

Stock decoding. `log-level=2`. Clear `force-software-decoding` and `software-video-decoder`
(delete the keys rather than writing `false`/`1`).

Bring the head unit up first, let it settle, then the phone (§7a). Run one **uninterrupted 5
minute** session with the screen moving. Do not touch either device during it.

- Record: which codec (`Codec initialized:`), and every `Throughput over` line.
- **Sum `dropped=` across the whole capture.** That number is the answer to Q1.
- Also count `Input buffer full`, `VideoDecoder: dropped a reference frame`, and
  `AapVideo: Frame corrupted` separately.

There is no PASS/FAIL here — it is a census, and both outcomes are useful. `dropped=0` for five
minutes means this rig does not reproduce #830's precondition on hardware decoding, which is worth
knowing precisely.

### R2 — provoke drops (gate for R3)

Same session shape, `log-level=2`, but with all four keys from §4 written
(`force-software-decoding=true`, `software-video-decoder=0`, `video-codec=H.264`). Run 5 minutes.

- **PASS:** `dropped=` is non-zero **and** `VideoDecoder: dropped a reference frame, requesting
  keyframe` appears at least once.
- **INCONCLUSIVE:** drops occur but the fix's line never appears **and** no `Throughput` line ever
  shows `rendered>0` — the decoder never rendered, so the gate was correctly shut. Say so plainly;
  this is the designed outcome from §3, not a fault.
- **FAIL:** `rendered>0` and `dropped>0`, yet `VideoDecoder: dropped a reference frame` never
  appears. That would mean the gate is wrong.

Record `Codec initialized:` — expect a software component such as `c2.android.avc.decoder`. If it
still reports `c2.unisoc.avc.decoder`, the setting did not take; re-read `settings.xml` and say so.

**R2b — positive control, settings only.** Immediately after R2, delete the two software keys,
relaunch, and run 2 minutes. Drops and the new W line should return to whatever R1 measured. This
is what proves R2's drops were caused by the lever and not by the session.

### R3 — does the nudge produce a keyframe? (the point of the round)

**Gated on R2 = PASS.** If R2 was INCONCLUSIVE or FAIL, mark R3 INCONCLUSIVE and stop — do not
invent a substitute (§6 of the template).

Same settings as R2, but `log-level=0`. Run 5 minutes.

The measurement has two halves, and the second is what makes the first mean anything:

**(a) The phone's natural keyframe cadence, from the same capture.** Assemble frames from the
`RECV: VIDEO` lines and total their sizes — a keyframe is many times larger than a P-frame and is
usually fragmented (flags 9 → 8… → 10), where an ordinary frame is often a single flags 11.

```bash
grep -F "RECV: VIDEO" r3.txt \
 | sed -E 's/^([0-9-]+ [0-9:.]+).*flags: ([0-9]+) size: ([0-9]+).*/\1 \2 \3/' \
 | awk '$3==11{print $2, $4; next}
        $3==9 {t=$2; acc=$4; next}
        $3==8 {acc+=$4; next}
        $3==10{acc+=$4; print t, acc; acc=0}' > frames.txt
```

Sanity check that against a dozen raw lines before trusting it — if the `sed` does not match this
build's exact message text, fix it and **say so in Setup notes**, because the next round will
inherit the recipe. Then take the frame totals: the large ones are keyframes. Report the median
frame size, the threshold you chose for "keyframe", and the **intervals between keyframes** with
no nudge anywhere near them. That distribution is the control.

**(b) What follows each nudge.** For every
`AapTransport: Requesting recovery keyframe (unsolicited focus gain).` timestamp T0, find the
first keyframe-sized frame after it and report Δ.

- **The nudge works:** Δ is consistently small (well under a second) and clearly shorter than the
  natural intervals from (a).
- **The nudge is inert:** Δ is indistinguishable from waiting out the natural cadence — i.e. the
  keyframes that follow nudges are the ones that were coming anyway.

Report it as a table of every (T0, Δ) pair plus the control distribution, not as a verdict
adjective. If there are fewer than five nudges in the capture, say how many there were; a
conclusion from two is not one.

**Also record, for the #755 fear:** the `Throughput` lines spanning each nudge. If `rendered` fps
drops after a nudge and does not recover, that is a finding on its own and outranks everything
else in this round — quote it and keep the full capture.

## 7. Do not re-run

- **Anything about surface teardown, relaunch or the warm-relaunch escalation.** Round 8 of
  `video-black-after-background` closed that out (1.9–2.1 s GLES/TEXTURE, 850–859 ms window,
  SURFACE 0.6–0.8 s with zero escalations). This round deliberately never disturbs the surface.
- **Whether Home tears the surface down on this rig.** It does not (§7a); no run here presses it.
- **Poke behaviour, A2DP link state, hotspot bands.** Nothing in this round depends on them beyond
  getting one ordinary session up.

## 8. Report back

Three numbers decide what happens to `563ae013`:

1. **R1's total `dropped=` over five minutes on hardware decoding.** Zero means this rig cannot
   reproduce #830's precondition and the fix's hardware coverage has to come from a reporter.
2. **Whether R2 produced `VideoDecoder: dropped a reference frame, requesting keyframe` at all**,
   and how many times.
3. **R3's (T0 → Δ) table against the natural keyframe interval.** This is the one that decides
   whether the fix's mechanism is real. If the nudge is inert, say so plainly — an honest negative
   here saves shipping a no-op and redirects the work to a request-until-answered design that is
   already drafted.

Also worth a line each in "Anything the brief did not ask about": whether
`Frame larger than the codec input buffer:` ever appeared (it should not — it is rare by design,
and its appearance would be a genuine find), and the ratio of `AapVideo: Frame corrupted` to
`VideoDecoder: dropped a reference frame` nudges, which tells us which requester actually matters
on real hardware.
