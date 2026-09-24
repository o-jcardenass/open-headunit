# Dropped-frame keyframe recovery — round 6 brief

## 1. Build and baseline

**Candidate:** `fix/830-request-keyframe-on-dropped-frame` @ **`d4f42814`**
**Baseline:** **`62889f29`** — round 5's candidate, already built and measured. **This round needs
both APKs**, see R2.

```bash
git fetch fork fix/830-request-keyframe-on-dropped-frame
git checkout d4f42814
```

One new commit on round 5's `62889f29`. If round 5's APK (md5 `4d54b75538877378fcd25d27a2a718d8`)
was kept, reuse it as the baseline; otherwise rebuild `62889f29` first and keep both, because
`build_hur.sh` deletes the previous APK on its next run.

## 2. What changed and why

Round 5 settled the recovery half: 2.672–2.678 s from a shed frame to a repaired picture, against
~35 s before. That half is done and this round does not re-litigate it.

`d4f42814` addresses the *other* half — not shedding the frame in the first place. Two constants on
the same feed path had drifted apart:

- `feedInputBuffer` waits **300 ms** for the codec to free an input buffer before giving up.
- The queue behind it held **12 frames**, "around 200ms" by its own comment.

So on any codec stall between those figures the queue was shedding reference frames while the thread
in front of it was still deliberately being patient. One chosen against touch latency, the other
against codec behaviour, never reconciled. Both now live in `VideoFeedQueuePolicy`, and the depth is
derived from a 500 ms window against `fpsLimit` — 30 frames at 60 fps — with a test asserting the
queue always holds more video than the feed thread waits for.

**Why a deeper queue is not the latency it looks like:** a backlog drains in milliseconds once the
codec unblocks, and the output thread then discards all but the newest *decoded* frame. A discarded
decoded frame breaks no prediction. So the expected signature of this change is **`dropped=` falling
while `skipped=` rises** — a prediction-breaking input drop becoming a prediction-safe output skip.

## 3. What is different about this round

- **This is an A/B between two builds**, which no previous round in this thread has been. Same rig,
  same session shape, same provocation, only the queue depth differs. Install order matters; record
  the live APK md5 before every run.
- **The provocation that matters here is a *transient* stall, not sustained overload.** Round 5 said
  this outright: under sustained forced-software-decoding, drops arrive faster than any 2 s window
  can clear, so nothing about light intermittent drops is observable. A deeper buffer is exactly the
  wrong thing to test against a decoder that never catches up — it can only delay the drop, not
  prevent it. R2 is therefore a new kind of run for this thread.
- **R2 has an honest INCONCLUSIVE outcome, stated up front.** If neither lever below produces a drop
  on the hardware path, this rig cannot manufacture a transient stall and the queue change's evidence
  has to come from R3's count comparison instead. That is a real result about the rig, not a failure.
- `log-level=0` throughout, capture to a file via `stdbuf -oL`.
- Screen moving for every capture, same media-playback fallback rounds 1, 4 and 5 used. Round 5's
  own lesson applies: the media session reverts to `PAUSED` after the force-stop `set_hu_prefs.sh`
  performs, so resend the play key after each relaunch and confirm `PLAYING` via
  `dumpsys media_session` before starting a timed capture.
- **Round 5's capture-boundary lesson:** start the logcat capture *before* applying provocation
  settings, or leave generous margin after the relaunch. Round 5 lost its first escalation into that
  gap.

## 4. Settings

| Key | R1 | R2 | R3 |
|---|---|---|---|
| `log-level` | `0` | `0` | `0` |
| `video-codec` | `H.264` | `H.264` | `H.264` |
| `view-mode` | `1` (TEXTURE) | `1` | `1` |
| `force-software-decoding` | delete | **delete** | `true` |
| `software-video-decoder` | delete | **delete** | `0` |

R2 runs on **hardware** decoding deliberately — rounds 1, 2, 4 and 5's R1 all measured `dropped=0`
there, so any drop during R2 was caused by the lever and nothing else.

## 5. Lines that decide the round

| Line | Level | Means |
|---|---|---|
| `Feed thread started (queue holds N frames, Mms at Ffps)` | I | **new in `d4f42814`** — confirms which depth is live. Expect 12 on the baseline (it prints no such detail — see below) and `30 frames, 500ms at 60fps` on the candidate |
| `Throughput over ` | I | `rendered=`, `fed=`, **`dropped=`**, **`skipped=`** — the two that matter here |
| `VideoDecoder: dropped a reference frame, requesting keyframe` | W | a shed reference frame |
| `Input buffer full. Dropping frame.` | W | the *other* drop site — the codec gave nothing for 300 ms |
| `picture unrepaired for Nms - cycling video focus (n/3)` | W | escalation fired |
| `VideoDecoder: keyframe reached the codec (N bytes)` | I | the repair |
| `Codec initialized: ` | I | which component; also should stay at zero mid-session |

On the baseline `62889f29` the feed-thread line reads plain `Feed thread started` with no detail —
that absence *is* the confirmation you are running the baseline.

## 6. Runs

### R0 — build and unit-test gate

`build_hur.sh`, then `run_unit_tests.sh`.

- **PASS:** expect **292** tests — round 5's 286 plus **6** in the new `VideoFeedQueuePolicyTest`.
- **FAIL:** stops the round; quote the compiler output. First compile of `d4f42814`.

### R1 — silence and depth confirmation (gate for R2)

Candidate build, hardware decoding, 5 minutes undisturbed, screen moving. Same regression guard round
5 passed.

- **PASS:** `dropped=0`; **zero** `dropped a reference frame` / `picture unrepaired` /
  `cycling video focus`; and the feed-thread line reports the new depth. Record the natural keyframe
  gaps again (round 5 measured ~68.3 s, round 4 ~69.45 s).
- **FAIL:** any escalation line with `dropped=0`, or a depth other than 30 frames at the default
  60 fps. Stop and report.

### R2 — the transient stall, both builds (the point of the round)

A short, repeated CPU starvation against hardware decoding, run identically on **both** builds.

**Lever, primary — CPU burst.** Roughly 400 ms of full-core contention, every 10 s, 20 times:

```bash
N=$(adb shell nproc | tr -d '\r')
for i in $(seq 20); do
  for c in $(seq $((N*2))); do adb shell "timeout 0.4 sh -c 'while :; do :; done'" & done
  wait; sleep 10
done
```

**Lever, fallback — thermal throttle**, if the burst produces no drops on either build:

```bash
adb shell cmd thermalservice override-status 3   # SEVERE
sleep 20
adb shell cmd thermalservice override-status 0   # release
```

Repeat that cycle a few times. **Put it back to 0 before leaving**, and say in Setup notes which
lever was used. If `cmd thermalservice` is not permitted on this unit, say so and move on.

Run **8 minutes** per build, same lever, same content.

Report per build: total `dropped=`, total `skipped=`, count of `dropped a reference frame`, count of
`Input buffer full`, and cycles fired.

- **PASS:** the candidate's `dropped=` is materially below the baseline's under the same lever, with
  `skipped=` at or above it. That is the input-drop-becomes-output-skip trade, measured.
- **INCONCLUSIVE:** both builds show `dropped=0` — this rig cannot manufacture a transient stall on
  the hardware path. Say so plainly; R3 then carries the round.
- **FAIL:** the candidate drops *more* than the baseline, or its `rendered` fps is materially lower.
  Either would mean the deeper queue is costing something the reasoning did not predict.

### R3 — sustained overload, candidate only (the count comparison)

Provocation settings from §4, 10 minutes, candidate build. This is the run rounds 1–3 and 5 all did,
so the historical control is direct:

| Round | `dropped=` over the window |
|---|---|
| 1 | 510 (5.5 min) |
| 2 | 543 (10.6 min) |
| 3 | 533 (5 min) |
| 5 | 367 (10 min) |

Those durations differ, so **report a per-minute rate**, not a raw total, and compare against round
5's 36.7/min on the identical settings and duration.

- Also report `skipped=` (the same per-minute rate), the number of distinct unrepaired-clock runs,
  cycles fired against the cap of 3, and cooldown spacing.
- **Expected effect, stated so a null result is not misread:** small. A deeper queue helps transient
  stalls; under sustained overload the decoder never catches up and the queue mostly delays the drop.
  A modest fall in `dropped=` per minute is the honest expectation. **A large fall would be the
  surprise, and a rise would be a finding.**
- The #755 sentinel and `Codec initialized:` count stay as in round 5.

## 7. Do not re-run

- **The recovery chain's timing.** Round 5 measured it at 2.672–2.678 s on both captured cycles.
  Cycles firing in this round are context, not the subject.
- **Whether the nudge works, whether a cheaper lever exists, whether a cycle is survivable.** Rounds
  1–5 between them settled all three.
- Anything involving surface teardown or warm relaunch.

## 8. Report back

**R2's two `dropped=` figures, side by side.** That is the whole round: same rig, same lever, same
minute count, one constant changed. If the candidate sheds fewer frames, the queue was the defect.

Then **R3's per-minute drop rate against round 5's 36.7/min**, and whether `skipped=` moved the
opposite way.

One line worth having in "Anything the brief did not ask about": whether any drop episode in R2
cleared *inside* the 2 s window without an escalation. Round 5 saw zero of those under sustained
overload and flagged that a lighter provocation was needed to see them at all — R2 is that lighter
provocation, so it is the first run that could answer whether 2 s is well tuned for a real isolated
drop.
