# Idle-screen reconnect overlay — round 1 brief

## 1. Build and baseline

**Candidate:** `fork/fix/852-idle-screen-reconnect-overlay` @ `eec751970b58702d99fc3749475a37049ead544f`
(short `eec75197`), two commits off `main`.

```bash
git fetch fork fix/852-idle-screen-reconnect-overlay
git checkout eec75197
```

**Baseline:** `v.3.2.5` (`9f7c3b20`) — the released build the report is against. This round **needs
both APKs**: R1 has to reproduce the defect before R2 can mean anything.

Both are **versionCode 98**, so plain `adb install -r` works in either order and the `-d` downgrade
flag §7a documents is not needed here. Copy each APK out of `apks/` into a round-specific folder as
soon as it is built — `build_hur.sh` deletes the previous one before it builds.

History was not rewritten; this branch was pushed for the first time on 2026-08-18.

## 2. What this is and why it exists

Issue #852. On 3.2.5, with Android Auto showing a **music player full screen and playback paused**,
the app covers the projection with "Connection lost / Retrying…" every 15-30 s, for 30-60 s at a
time. Resuming playback clears it instantly. 3.2.4 does not do it, same phone, same unit, clean
installs of both.

The reporter's own scenario table is the diagnosis:

| Layout | Music playing | Music paused | Overlay |
|---|---|---|---|
| Full-screen Music | Yes | — | No |
| Full-screen Music | No | Yes | **Yes** |
| Split: Music + Maps | either | either | No |
| Full-screen Maps | either | either | No |

Every row that shows the overlay is a row where **nothing on screen animates**. Android Auto stops
sending video entirely when the picture is static — the standing note about "3 fps on a parked map is
normal" is the same behaviour — so the frame gap grows without limit on a perfectly healthy link.

Their 3.2.5 log shows the overlay landing **exactly 10 s** after the last rendered frame, with
`Throughput over 5001ms: rendered=0 (0fps), fed=0 (0fps), dropped=0, skipped=0`. Nothing arrived and
nothing was lost. Their 3.2.4 log is the control and settles it: the same stream idles the same way —
`rendered=0` across runs of 25-40 s, twenty-three such lines — and the overlay never appears once.

**3.2.4 was not measuring anything better.** The check is byte-for-byte identical. What differed is
that the watchdog opened by testing for `HandshakeComplete`, a state a session passes through once
and briefly, and returned *without re-posting itself* — so it died on the first tick of every session
and this code never ran at all. The #822 work revived it, because a stream that died mid-session had
nothing asking for it back, and a latent wrong criterion executed for the first time.

The fix separates two decisions that used to be one:

- **the overlay** says the connection is lost, so it now needs the connection to have gone quiet —
  a new all-channel timestamp, stamped on every decrypted inbound AAP message, not just on video;
- **the recovery request** stays on the frame gap alone, because a genuinely stalled stream and an
  idle one look identical from outside, and leaving the stalled one with nothing asking for video
  back is exactly what #822 was about.

A real disconnect never used this path: the `Disconnected` collector shows the same overlay
immediately, with its own 20 s exit timer.

## 3. What is different about this round

- **R1 is the point of the round as much as R2 is.** If the baseline does not reproduce the overlay
  on this rig, R2 proves nothing and the round is INCONCLUSIVE. Say so and stop rather than reading
  a silent candidate as a pass.
- **The gate for both is a log fact, not a screenshot.** What the runs need is a projected screen
  with nothing animating on it, and the objective proof of that is **three or more consecutive
  `Throughput` lines reading `rendered=0`** (15 s). A `screencap` of the projection is worth
  attaching if it comes out non-black on this backend, but do not spend the run on it and do not
  gate anything on it.
- **R3 costs no rig time.** It is a count taken from R2's capture, not a separate run.
- **R4 will probably exercise the disconnect path, not the gated one, and that is a fine result.**
  Killing the link kills the socket, so the `Disconnected` collector normally wins the race. There is
  no way on this rig to manufacture a link that goes silent while the socket stays up, which is the
  only case the gated path covers. R4's job is to prove the fix did not break the ordinary loss.
- `log-level=2` (INFO) carries every line this round needs — the two new ones are `AppLog.w`, the
  overlay lines are `AppLog.i`, and none of them sits behind a `LOG_VERBOSE` guard. Checked against
  the guard, not the call. Prefer it to VERBOSE: this unit's driver stack wraps the ring buffer.
- The reporter runs `TEXTURE` (their log shows `TextureProjectionView`), so this round does too.

## 4. Settings

Types: `log-level` and `view-mode` are **int**; `video-codec` is **string**;
`force-software-decoding` is **boolean**. "delete" means run only the removal half of §1's template.

| Key | R1 | R2 | R4 |
|---|---|---|---|
| `log-level` | `2` | `2` | `2` |
| `view-mode` | `1` (TEXTURE) | `1` | `1` |
| `video-codec` | `H.264` | `H.264` | `H.264` |
| `force-software-decoding` | delete | delete | delete |
| `software-video-decoder` | delete | delete | delete |

Use `set_hu_prefs.sh` — every run writes more than one key.

## 5. The lines that decide the round

All verified with `grep -F` against `eec75197`.

**New in the candidate — these do not exist on `main`, so their presence also confirms which APK is
live:**

| Line | Level | Means |
|---|---|---|
| `AapProjectionActivity: picture idle for Nms but the link spoke Mms ago - Android Auto has stopped sending, not disconnected` | W | **the fix working.** `Mms` is the phone's real idle cadence — the number the whole threshold rests on. Throttled to one per 10 s, and re-armed the moment frames resume |
| `AapProjectionActivity: picture idle for Nms and the link has been silent for Mms - treating this as a lost connection` | W | the gated overlay firing, i.e. a stopped picture the candidate still calls a lost connection |

**Pre-existing:**

| Line | Level | Means |
|---|---|---|
| `Showing reconnecting overlay` | I | the defect, on the baseline. **Count only the standalone line** — `Unexpected disconnect. Showing reconnecting overlay and waiting up to 20s` contains the same words and is the *other* path. `grep -c '| Showing reconnecting overlay$'` separates them |
| `Hiding reconnecting overlay — frames resumed` | I | note the em dash |
| `AapProjectionActivity: connected but no frames - requesting video focus (unsolicited)` | W | the #822 recovery request. **R3 counts these** |
| `Throughput over Nms: rendered=N (Nfps), fed=N (Nfps), dropped=N, skipped=N, inputWait=Nms, codec=…` | I | `rendered=0` runs are the idle gate; also the global regression sentinel |
| `AapProjectionActivity: Unexpected disconnect. Showing reconnecting overlay and waiting up to 20s for recovery.` | I | the disconnect path, R4 |
| `AapProjectionActivity: Reconnect timed out (20s). Finishing activity.` | I | R4's end state if nothing recovers |

## 6. Runs

### R0 — build and unit-test gate

`build_hur.sh` for each SHA, then `run_unit_tests.sh` on the candidate.

- **PASS:** both compile, and the suite reports **318** tests — `main`'s 312 plus 6 new in
  `ProjectionWatchdogPolicyTest` (which goes from 4 to 10; two of the original four were also
  renamed, so the file's names will not match round 6's notes). All green.
- **FAIL:** stops the round. Quote the compiler output. First compile of `eec75197`.

### R1 — reproduce the defect on 3.2.5 (gate for R2)

Baseline APK. Bring the head unit up first, let the group settle, then the phone (§7a). Once
projection is live:

```bash
adb -s <phone> shell am force-stop <media app>
adb -s <phone> shell monkey -p <media app> -c android.intent.category.LAUNCHER 1
adb shell input keyevent 85          # play, relayed to the phone through the head unit
# confirm PLAYING via dumpsys media_session, and let the media app take the projected screen
sleep 30
adb shell input keyevent 127         # pause
# hold 5 minutes, touch nothing
```

- **Idle gate:** at least three consecutive `Throughput` lines with `rendered=0` after the pause. If
  the projected screen keeps animating (a launcher, Maps, an album-art transition), the run has not
  set up the condition — say so and retry once; twice failing is INCONCLUSIVE for the whole round.
- **PASS = the defect reproduces:** `Showing reconnecting overlay` appears within ~10 s of the last
  rendered frame, and repeats. Record how many times, and the interval between them.
- **FAIL / INCONCLUSIVE:** no overlay in 5 minutes of confirmed idle. Then this rig does not
  reproduce #852 and R2 cannot be interpreted — stop, report, and leave R4 for another round.

### R2 — the candidate, playing then paused (the point of the round)

Candidate APK, same setup. Two phases in **one** capture:

1. **2 minutes with playback running** — the control. The screen animates, frames flow.
2. **5 minutes paused** — the reported condition.

- **Record, as numbers:** count of `Showing reconnecting overlay` (expect **0**); every
  `picture idle for Nms but the link spoke Mms ago` line **with its `Mms` value** — this is the
  round's deliverable; `Throughput` totals for each phase; count of
  `requesting video focus (unsolicited)` (feeds R3).
- **PASS:** zero standalone `Showing reconnecting overlay` across the whole capture, the new idle
  line present during phase 2 and absent during phase 1, and phase 1's throughput comparable to the
  baseline's while playing.
- **FAIL:** any `Showing reconnecting overlay`, or the new line appearing while the picture is
  actively moving.
- **A finding either way:** if `picture idle … and the link has been silent for Mms - treating this
  as a lost connection` appears, the phone really did go quiet for over 10 s on an idle screen, and
  `LINK_QUIET_MS` needs raising. Quote every one.

### R3 — the #822 recovery guard (no new run)

Counted from R2's capture.

- **PASS:** `AapProjectionActivity: connected but no frames - requesting video focus (unsolicited)`
  still fires during phase 2, roughly every 2 s while the picture is idle, with the overlay never
  shown. Report the count and the spacing.
- **FAIL:** zero of them. That would mean the fix tied recovery to the gated overlay after all, and a
  genuinely stalled stream would now have nothing asking for video back — the exact regression #822
  existed to fix. This is the run that catches the fix over-reaching.

### R4 — a genuine loss still shows the overlay

Candidate APK, session live and rendering. Then:

```bash
adb -s <phone> shell cmd connectivity airplane-mode enable
# watch for 60s
adb -s <phone> shell cmd connectivity airplane-mode disable
adb -s <phone> shell svc wifi enable        # §7a: coming back does not restore this reliably
adb -s <phone> shell svc bluetooth enable
# verify both are actually up before leaving
```

- **PASS:** the overlay appears, and the log says by which route —
  `Unexpected disconnect. Showing reconnecting overlay` (the `Disconnected` collector, the expected
  one) or `picture idle … and the link has been silent … - treating this as a lost connection` (the
  gated path). Either is a pass; **record which**, because that tells us which path actually carries
  real losses.
- **FAIL:** no overlay at all within 30 s of the radios going down.
- Restore the phone's radios and confirm before finishing, whatever the verdict.

## 7. Do not re-run

- **Whether a Home press tears down the projection surface.** It does not on this unit; twelve
  scripted cycles proved it. Nothing here needs a teardown.
- **Feed queue depth, the dropped-reference-frame chain, and the black-screen work.** Settled in
  `video-dropped-frame-keyframe` rounds 5-6 and `video-black-after-background` round 8. This branch
  does not touch the decoder at all.
- **The natural ~68-69 s keyframe cadence.** Quote it if useful; do not remeasure.
- **Anything from the `video-pipeline-stack` thread.** Different branch, different round, and its
  brief is queued separately — do not install that APK during this one.

## 8. Report back

Three things decide what happens next:

1. **Does 3.2.5 reproduce the overlay on this rig, and does the candidate show zero?** (R1, R2.)
   That pair is the whole shipping question.
2. **Every `link spoke Mms ago` value from R2.** Android Auto's idle inbound cadence has never been
   recorded by anyone — the phone's `PingRequest` is answered without logging. `LINK_QUIET_MS` is
   currently a judgement at 10 s; these numbers replace it with a measurement.
3. **The count of `requesting video focus (unsolicited)` during R2's paused phase.** (R3.) Non-zero
   means recovery survived the split. Zero means the fix went too far and must be reworked before it
   ships.
