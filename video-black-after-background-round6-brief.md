# Round 6 brief — why the picture takes 5-116 s to come back on TEXTURE and GLES

Round 5 measured return→picture on all three backends for the first time and found an asymmetry it
flagged as counter-intuitive but did not explain:

| Backend | Return → `First frame rendered`, per cycle |
|---|---|
| SURFACE | 0.68 s, 0.68 s, 0.71 s, 0.80 s |
| TEXTURE | 28.1 s, 52.5 s, 13.4 s, 5.4 s |
| GLES | 21.7 s, 45.9 s, 6.8 s, **116.4 s** |

This round decides what those seconds are spent on. **It needs no build, no APK and no device
time** — it is a post-hoc analysis of the captures round 5 already took. Everything it asks for is
already in them.

## 1. Build

**None.** No candidate, no baseline, no install, no R0 gate. The tree under analysis is
`fix/822-stale-surface-callback` @ `1192daa5`, unchanged since round 5; history has not been
rewritten. The branch is needed only if you want to read the source next to a log line.

Round 5's verdicts are settled and this round re-proves none of them. If anything here contradicts
round 5, report it as a finding rather than revising round 5's PASS/FAIL.

## 2. What this is and why it exists

The three backends differ in one thing that matters here: **whether covering the projection destroys
the surface**, which round 5 established for the first time.

SURFACE's surface *is* destroyed by the cover, so the app sends the phone a video-focus release, and
round 5 saw the phone answer with `Media Sink Stop Request: VIDEO` on all four cycles. On return the
phone re-runs sink setup, and a sink start begins with a fresh keyframe. That is the 0.68 s.

GLES and TEXTURE never destroy their surface, so focus is never released and the phone is never told
anything. On return the app rebuilds the decoder anyway (the launcher tap destroys and recreates the
projection activity on all three backends) and asks for a keyframe with an *unsolicited focus gain*
while the phone still believes the head unit holds focus. Round 5's R1 cycle 4 shows six of those
requests going out across 116 s with no picture.

**The hypothesis this round tests: the seconds are spent waiting for the phone's next keyframe, and
the gain-only request does not bring one forward.** The decoder itself is not a candidate — round 4
measured `Configuring decoder:` → `First frame rendered` at 51-53 ms on cold starts.

The competing explanation is that the relaunch itself is slow on these two backends — that the cost
is before the decoder ever gets a surface. The two are cleanly separable in the log, which is what
the legs below do. **Which one it is decides whether the fix belongs in the video-focus path or in
the activity lifecycle**, so this measurement is worth more than another hardware round.

## 3. What is different about this round

- **No device is driven and no setting is written.** §4's clean-run protocol and the discard rules do
  not apply; there is nothing to contaminate. Do not force-stop the app, do not touch the rig's
  settings, and do not restore `settings-backup.xml` — round 5 already restored it.
- **The inputs are round 5's own captures**, expected in `hur-wifi-test-scripts/round5-video-black/`
  as `r1-gles.txt`, `r2-texture.txt`, `r3-surface.txt` and the R4 rapid-switching capture, 93-161 MB
  each. Start by listing that folder and recording the actual filenames and sizes in Setup notes.
- **If the captures are gone**, do not stop and ask. Fall back to §9's re-capture, which is a
  deliberately smaller version of round 5's matrix, and say in Setup notes that you ran the fallback.
- **All timestamps are device-log timestamps.** Round 5's ~26.1 s `monkey` injection delay means a
  host-side trigger time is wrong by that much, every time. Nothing in this round uses one.
- **Every number is a difference between two lines in the same file**, so the ~1.5-2 s device-behind-
  host clock skew round 4 established does not enter into any of them.

## 4. The lines that decide every run

Verified with `grep -F` against `1192daa5`. Composed lines are marked; those are assembled from a
format string or a variable at the call site and will not `grep -F` in the source as written here,
but appear verbatim in a log.

| Meaning | Line |
|---|---|
| the return actually landed on the device | `Events injected: 1` |
| the new activity's surface reached the decoder | `New surface set: ` |
| the old decoder was torn down for it *(composed)* | `Decoder stopped: New surface` |
| MediaCodec was created and started | `Configuring decoder: ` |
| the codec was configured with **no** stream config in hand | `Fallback to negotiated dimensions: ` |
| the phone's fresh H.264 config arrived (H.264 streams only) | `H.264 SPS parsed: ` |
| picture back | `First frame rendered (hardware decode)` |
| a keyframe was asked for, from the decoder's restart path | `AapTransport: Requesting recovery keyframe (unsolicited focus gain).` |
| a keyframe was asked for, from the startup overlay watchdog | `Watchdog: No video received yet. Requesting Keyframe (Unsolicited Focus)...` |
| a keyframe was asked for, mid-session | `connected but no frames - requesting video focus (unsolicited)` |
| the phone stopped the video sink *(composed)* | `Media Sink Stop Request: VIDEO` |
| the phone started the video sink *(composed)* | `Media Start Request VIDEO: session=` |
| a stall restart fired | `Forcing restart (` |
| a stall restart was suppressed by cooldown or cap | `but restart suppressed (` |
| steady state | `Throughput over ` |

There are no forbidden lines this round — nothing is running, so nothing can fail. Round 5 already
established that none of them appear in these captures.

## 5. Runs

### A1 — GLES (`r1-gles.txt`), the point of the round

For each of the four returns, take the four device timestamps below and report the three gaps. The
returns are the four `Events injected: 1` lines; if the file holds more (the round-5 script may have
logged its own), pair each with the `ActivityTaskManager: START` for the projection that follows it,
exactly as round 5 did.

| Leg | From | To | What it measures |
|---|---|---|---|
| **A** | `Events injected: 1` | `New surface set: ` | the relaunch: activity destroyed, recreated, surface handed over |
| **B** | `New surface set: ` | `Configuring decoder: ` | codec creation |
| **C** | `Configuring decoder: ` | `First frame rendered (hardware decode)` | waiting for a decodable picture |

Report all three per cycle, in milliseconds, plus their sum, and check the sum against round 5's
published total for that cycle (21.7 / 45.9 / 6.8 / 116.4 s). **A sum that disagrees with round 5 by
more than a second means the cycles have been paired up differently — say so and show the pairing you
used, rather than reconciling it silently.**

Then, per cycle:

- how many `Fallback to negotiated dimensions: ` lines fall inside the cycle, and whether one
  immediately precedes each `Configuring decoder: `
- the timestamp of the first `H.264 SPS parsed: ` after each `New surface set: `, and its offset from
  `First frame rendered` — if the stream is H.265 there is no equivalent line, so record that the
  marker was unavailable and say which codec the run used (§6)
- a count of each of the three keyframe-request lines, with the timestamp of every one
- a count of `Forcing restart (` and `but restart suppressed (`
- whether `Media Start Request VIDEO: session=` appears anywhere after the return, and if so, where

**What the answer looks like.** If leg C holds nearly all of every slow cycle while A and B stay
small, the phone is the bottleneck and the fix belongs in the video-focus path. If leg A is large,
the relaunch is the bottleneck and the fix belongs in the activity lifecycle. If leg B is large,
neither — the codec is slow to create on this backend, which nothing so far predicts and which would
be the round's real finding.

### A2 — TEXTURE (`r2-texture.txt`)

Identical treatment, four cycles. Round 4 called TEXTURE the slower backend to relaunch and round 5
found it the faster of the two; leg A is the number that settles which, so report it prominently.

### A3 — SURFACE (`r3-surface.txt`), the control

Same three legs for all four cycles. This backend recovers in 0.68-0.80 s, so its legs are the shape
a healthy return has, and the comparison is the point:

- leg C on SURFACE is what "the phone sent a keyframe promptly" looks like in milliseconds
- confirm `Media Start Request VIDEO: session=` **does** appear after each SURFACE return, and note
  its offset from the return and from `First frame rendered`

The claim under test, stated from the phone's side: **`Media Start Request VIDEO` appears after every
SURFACE return and after no GLES/TEXTURE return.** Report it as confirmed or refuted, with counts.

### A4 — rapid switching (the R4 capture), only if A1-A3 leave time

Cycles overlap there, so per-cycle legs are not meaningful. One number is: for cycle 5's surface —
round 5 timed it at 54.0 s from its own `New surface set:` to `First frame rendered` — give legs B
and C, and the offset of every keyframe request in that window. It is the longest single wait in the
round with the cleanest boundaries.

## 6. Two facts to record from the rig

Neither needs the app running.

1. **Which codec the stream used.** `video-codec` in `settings.xml` (`"H.264"`, `"H.265"` or
   `"Auto"`), plus what the captures show the decoder actually pinned — the mime type in
   `Configuring decoder: ` says it directly. This decides whether the `H.264 SPS parsed: ` marker was
   available at all.
2. **The `view-mode` values round 5 wrote**, confirmed from the round-5 folder rather than from
   memory, so each capture is attributed to the right backend: `2` = GLES, `1` = TEXTURE, `0` =
   SURFACE.

## 7. Scripting it

Nothing here should be done by eye across a 1.4-million-line file. Write one script,
`hur-wifi-test-scripts/round6-video-black/legs.sh <capture> <label>`, that emits one row per cycle
with the three legs and the per-cycle counts, and leave it there for the next round. The whole job is
a single pass:

```bash
grep -nE "Events injected: 1|New surface set: |Configuring decoder: |First frame rendered \(hardware decode\)|Fallback to negotiated dimensions: |H\.264 SPS parsed: |Requesting recovery keyframe|Requesting Keyframe \(Unsolicited Focus\)|requesting video focus \(unsolicited\)|Media Sink Stop Request: VIDEO|Media Start Request VIDEO: session=|Forcing restart \(|but restart suppressed \(" "$1"
```

That reduces each capture to a few hundred lines, in order, with line numbers — bucket them by
return and the legs fall out. Attach that reduced file per capture alongside the results; it is small
enough to commit and it is what a future round will want instead of the 161 MB original.

## 8. Do not re-run

Round 5's runs are settled and nothing here revisits them: whether a cover tears the surface down per
backend, the forbidden-line verdicts, the `(4/4)`-then-reset-then-recover path, the discard-rule
checks, and the `monkey` injection delay. Round 4's cold-start ladder and its 51-53 ms
configure→first-frame figure are also settled and are used here as a reference point, not re-measured.

## 9. Fallback, only if the captures are gone

Re-capture a reduced matrix — GLES and SURFACE only, two cycles each, holds 5 s and 45 s — following
round 5's own setup exactly (`wifi-connection-mode=3`, `enable-audio-sink=true`, `view-mode` per run,
`log-level=2`, cover with `am start -a android.settings.SETTINGS`, return with
`monkey -p $PKG -c android.intent.category.LAUNCHER 1`), then run §5 against the new captures.
TEXTURE is the one to drop if time is short: it and GLES have never differed in kind, only in degree.

Say in Setup notes that the fallback was used, and treat the new absolute times as this round's own —
do not compare them to round 5's numbers cycle by cycle.

## 10. Report back

Three things decide the next branch:

1. **The leg split.** Per backend, what fraction of the return sits in A, B and C. One table.
2. **Whether `Media Start Request VIDEO` divides the backends** the way §5's A3 predicts.
3. **How many keyframe requests went out during the slow waits, and whether any of them was followed
   by a picture** within a second or two. If several fired and none was answered, the gain-only
   request is established as ineffective on this phone, which is the single fact the fix turns on.
