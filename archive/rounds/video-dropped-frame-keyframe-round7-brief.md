# Dropped-frame keyframe recovery — round 7 brief

## 0. Read this first: R3's rise does not block the branch

Round 6 asked a direct question and it deserves a direct answer rather than another round to hide
behind: **is R3's 36.7 → 67.0 drops/min a cost of the deeper queue?**

**No, and it cannot be.** Three separate reasons, in order of how decisive they are.

**1. The metric is an identity, and the queue is not in it.** Every arriving frame either reaches the
codec or is shed, so

```
dropped = arrived − fed
```

Under *sustained* overload the queue is permanently full, and in that state the drop rate settles at
exactly (phone's send rate − codec's accept rate). Queue depth appears nowhere in that expression. All
a deeper queue buys is a one-time head start of 18 extra frames — about 0.4 s of a 10-minute window.
So a rise in `dropped=` is, by arithmetic and not by argument, either **more frames arriving** or
**fewer frames being decoded**. The queue changes neither.

**2. It is a residual of two large, nearly equal numbers.** At ~46 fps over ~600 s, roughly **27,000
frames** arrive and roughly 27,000 are fed. Round 5's 367 drops are **1.3%** of that; round 6's 699 are
**2.5%**. The "1.8x rise" is a decoder that kept up 98.7% of the time keeping up 97.5% of the time
instead — **about one frame per second**. Any metric that reports the 1–2% gap between two numbers near
27,000 will double on a rounding error in either one.

**3. The historical control has never been stable.** Four builds in this thread carry the *identical*
12-frame queue — the queue was untouched until `d4f42814`. Their sustained-overload rates:

| Round | Build | Queue | Rate |
|---|---|---|---|
| 5 | `62889f29` | 12 | 36.7/min |
| 2 | `ec0a2d28` | 12 | 51.2/min |
| 1 | `563ae013` | 12 | 92.7/min |
| 3 | `a2e0268e` | 12 | 106.6/min |
| **6** | **`d4f42814`** | **30** | **67.0/min** |

**A 2.9x spread with the queue held constant**, and round 6's 67.0 sits inside it, between rounds 2 and
1. The noise on this measurement is larger than the effect being read off it. Round 6 was right to
report the number and right to refuse to bury it — but a 1.8x difference cannot be resolved by a metric
whose own repeatability is worse than 2.9x.

**Round 6's own data already says which term moved.** R3's fps ran **19–52** with a genuine 19 fps
point, against round 5's **42–51**. The decoder was demonstrably slower, which is the direction that
raises drops — and it is the direction the queue cannot cause. That is consistent with what else
differed: R3 ran after R1's 5 minutes, two ~8-minute R2 captures and 20 full-core CPU bursts, roughly
25 minutes of load on a passively cooled UNISOC tablet. Round 5's equivalent capture ran after 5
minutes.

**So: the branch is not gated on this round.** R2 answered what round 6 was built to answer, and the PR
should go ahead. What follows is worth doing anyway, because this thread has now leaned on cross-round
drop-rate comparison five times and nobody has ever measured whether it means anything.

## 1. Candidate

No new build. `fix/830-request-keyframe-on-dropped-frame` @ **`d4f42814`**, the same APK round 6 ran
(md5 `c5d4c0feeb60d81d38aca693bcf7940c`). If it is still installed, this round needs no build at all.

## R1 — desk check, no rig time (do this first)

**If round 6's `r3.txt` / `r3b.txt` and round 5's R2 capture are still on the rig**, this costs a few
`grep`s and settles §0's point 1 with numbers instead of reasoning. If they were deleted, say so and
skip to R2 — a deleted log is a legitimate INCONCLUSIVE here, not a failure.

For each of the two captures, sum the `Throughput over 5000ms` line's fields across the whole file and
divide by the capture's real duration:

```bash
grep -o 'rendered=[0-9]*, fed=[0-9]*, dropped=[0-9]*, skipped=[0-9]*' r3.txt r3b.txt \
  | grep -o '[a-z]*=[0-9]*' | awk -F= '{s[$1]+=$2} END {for (k in s) print k, s[k]}'
```

Report, for round 5's R2 and for round 6's R3 (r3.txt + r3b.txt combined):

| | fed total | dropped total | duration | **arrived/s** = (fed+dropped)/dur | **fed/s** |
|---|---|---|---|---|---|

**What each outcome means** — all three exonerate the queue, and they differ only in what they say about
the rig:

- **`fed/s` lower in round 6, `arrived/s` about equal** → the decoder was slower, exactly as the fps
  range suggests. Thermal or accumulated load. This is the expected answer.
- **`arrived/s` higher in round 6** → the phone was sending more, so the provocation is not rate-
  controlled between sessions and cross-round drop counts were never comparable in the first place.
- **Both about equal** → arithmetically impossible while `dropped` differs, so it would mean one of the
  three sums is wrong. Recheck before reporting.

Also confirm R3's duration while you are in there: the results give 625.9 s but the two segments are
described as 308.9 s + 300 s = 608.9 s. It moves the rate by about 2/min either way, so it changes
nothing — but the two figures should agree.

## R2 — how repeatable is this measurement at all? (optional, ~30 min)

The point of this run is **not** #830. It is to put a number on the noise floor of the sustained-
overload drop rate, so that this thread — and any future one — knows whether a cross-round comparison
of it is worth making. Five rounds have quoted it; none has ever measured its repeatability.

**Same build throughout. No A/B.** Install/confirm `d4f42814`, apply the standard provocation
(`force-software-decoding=true`, `software-video-decoder=0`, `video-codec=H.264`), and take **three
back-to-back 5-minute captures in one session**, with nothing changed between them except that time
passes:

- Do **not** relaunch the app between captures. Same pid, same session, same settings.
- Screen moving throughout, media playing (resend the play key after the initial relaunch and confirm
  `PLAYING` — round 6 found this needed after *every* relaunch).
- Leave ~30 s between captures and start each with `logcat -c` **before** killing the previous pipe,
  the lesson round 6 wrote down.

Report per capture: `dropped=` total, `fed=` total, `skipped=` total, fps range, and the derived
drops/min. Then the **spread across the three** — max/min ratio — which is the number this round exists
to produce.

**How to read it:**

- **Spread ≥ 1.8x** → the round 6 R3 comparison had no power, said plainly and permanently. Every
  future brief should stop quoting cross-round drop rates as evidence, and this table is the citation.
- **Spread well under 1.8x** (say ≤ 1.2x) → the measurement is more stable than §0 assumes, R3's rise
  is larger than session noise, and it becomes worth a genuine same-session A/B against `62889f29`.
  That would be round 8; do **not** run it inside this round.

Either result is a finding. A tight spread does not retroactively make the queue causal — §0's identity
still holds — but it would mean something else about round 6's session is unaccounted for, and that is
worth knowing.

## Verdicts

- **R1 PASS** — both captures summed, the table filled in, and one of the three outcomes identified.
- **R1 INCONCLUSIVE** — logs no longer on the rig. Expected and fine; say so and move on.
- **R2 PASS** — three captures, spread reported. There is no failing outcome here; the number is the
  deliverable.
- **UNTESTABLE** — the rig cannot hold a stable software-decode session for 15 minutes.

Nothing in this round gates the PR. If the rig is needed for something else, R1 alone is worth more
than R2 and costs nothing.
