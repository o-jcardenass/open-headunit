# release/next — round 1 brief

## 1. Build and baseline

**Candidate:** `fork/release/next` @ `e2e658555e48c34c5ab0b82d2212c70585d9fa54` (short `e2e65855`).

```bash
git fetch fork release/next
git checkout e2e65855
```

It is `main` plus two feature branches, merged with `--no-ff`:

```
* e2e65855 Merge fix/session-liveness into the release branch
|\
| * 9a5c884a Projection: an idle Android Auto screen is not a lost connection
| * fdfc34e0 Transport: record when the phone last spoke, not just when it last sent a picture
| * 118c922c Transport: a read that loses bytes ends the session instead of skipping the message
* 10006d33 Merge fix/video-pipeline into the release branch
* d43112e2 Video: wait for a keyframe instead of rebuilding the decoder that needs one
* 0e6d5747 Video: configure the decoder from the stream, and name it when it is the bottleneck
* 40e6c4eb Video: size the pipeline to the device instead of to a guess
* 53dfd66a Video: never assemble a frame without its first fragment, and instrument the rest
```

**Baseline:** `v.3.2.5` (`9f7c3b20`) — the released build #852 is reported against. This round
**needs both APKs**: R1 has to reproduce #852 before R2 can mean anything.

Both are **versionCode 98** — `release/next` does not touch `app/build.gradle.kts` — so plain
`adb install -r` works in either order and the `-d` downgrade flag §7a documents is not needed.
Copy each APK out of `apks/` into a round-specific folder as soon as it is built; `build_hur.sh`
deletes the previous one before it builds.

**Nothing on this branch has ever been compiled.** R0 is the merged tree's first compile.

## 2. What this is and why it exists

`release/next` is the integration branch the next release will come from. This round asks two
different questions of it, and they need to stay separate in the report.

**The A/B: does it fix #852?** On 3.2.5, with Android Auto showing a **music player full screen and
playback paused**, the app covers the projection with "Connection lost / Retrying…" every 15-30 s,
for 30-60 s at a time. Resuming playback clears it instantly. 3.2.4 does not do it, same phone, same
unit, clean installs of both. The reporter's own scenario table is the diagnosis:

| Layout | Music playing | Music paused | Overlay |
|---|---|---|---|
| Full-screen Music | Yes | — | No |
| Full-screen Music | No | Yes | **Yes** |
| Split: Music + Maps | either | either | No |
| Full-screen Maps | either | either | No |

Every row that shows the overlay is a row where **nothing on screen animates**. Android Auto stops
sending video entirely when the picture is static — the standing note about "3 fps on a parked map is
normal" is the same behaviour — so the frame gap grows without limit on a perfectly healthy link.
The reporter's 3.2.5 log shows the overlay landing **exactly 10 s** after the last rendered frame,
with `rendered=0, fed=0, dropped=0`: nothing arrived and nothing was lost. Their 3.2.4 log is the
control — the same stream idles the same way across twenty-three throughput lines, and the overlay
never appears once.

3.2.4 was not measuring anything better. The check is byte-for-byte identical; what differed is that
the watchdog opened by testing for `HandshakeComplete`, a state a session passes through once and
briefly, and returned *without re-posting itself*, so it died on the first tick of every session and
this code never ran. The #822 work revived it, and a latent wrong criterion executed for the first
time. The fix separates two decisions that used to be one: **the overlay** claims the connection is
lost, so it now needs the connection to have gone quiet too — a new all-channel timestamp stamped on
every decrypted inbound AAP message, not just on video; **the recovery request** stays on the frame
gap alone, because a genuinely stalled stream and an idle one look identical from outside, and
leaving the stalled one with nothing asking for video back is exactly what #822 was about.

**The regression question: did merging break anything already measured?** Everything else on this
branch has been measured before, at other SHAs, and this is the first time any of it has run
*together*. Four files are edited by both feature branches and were auto-merged by git with no
conflict — `AapReadSingleMessage.kt`, `AapTransport.kt`, `AapProjectionActivity.kt` and
`CommManager.kt`. R5 and R6 exist to check the merge held, against numbers the previous rounds
already recorded, not to discover anything new.

## 3. What is different about this round

- **R1 is the point of the round as much as R2 is.** If the baseline does not reproduce the overlay
  on this rig, R2 proves nothing and #852 is INCONCLUSIVE. Say so and stop rather than reading a
  silent candidate as a pass. The rest of the round still runs.
- **The #852 A/B has more than one variable now, and that is a deliberate trade.** Baseline is 3.2.5;
  the candidate carries the whole video stack as well as the #852 fix. So a *pass* is trustworthy —
  the overlay either appears or it does not — but an unexpected *regression* in R2 cannot be pinned
  on the #852 change without R5 and R6 to attribute it. If that ever needs untangling, the #852
  commit alone is preserved as tag `idle-screen-round1-candidate` (`eec75197`) and can be built on
  its own.
- **R5 and R6 are confirmations, not experiments.** Their PASS conditions are numbers a previous
  round already produced on this rig, quoted in each run. A disagreement is the finding.
- **The read-desync fix cannot be tested positively here.** It only acts when a read returns fewer
  bytes than the header declared, which needs a link losing bytes mid-message; there is no way to
  manufacture that on this rig. R7 is an absence check — those lines must never appear on a healthy
  session, because each one now ends the session instead of skipping a message.
- **A bad picture is expected in R6, and is not a failure.** It injects corruption on purpose at a
  rate far past anything real.
- **Two settings families.** The #852 runs use `TEXTURE`, because the reporter does. The video runs
  use `SURFACE`, because that is what every previous video measurement used and the numbers only
  compare if the backend matches. §4 has both.
- `log-level=2` (INFO) carries every line this round needs. All of them are `AppLog.i` or `AppLog.w`
  and none sits behind a `LOG_VERBOSE` guard — checked against the guard, not the call. Prefer it to
  VERBOSE: this unit's driver stack wraps the ring buffer, and no run here counts `RECV:` lines.
- **Grep every capture with `-a`** (§7a). These logs are long enough that `grep` calls them binary,
  and `grep -c` then prints *nothing* and exits 1 rather than printing `0` — so a refused count reads
  exactly like a pattern that is absent, which is how half this brief's PASS conditions are phrased.

## 4. Settings

Types: `log-level`, `view-mode`, `debug-video-fault-injection` and `debug-video-fault-rate` are
**int**; `video-codec` is **string**; `force-software-decoding` is **boolean**. "delete" means run
only the removal half of §1's template. Use `set_hu_prefs.sh` — every run writes more than one key.

| Key | R1 | R2 | R4 | R5 | R6 |
|---|---|---|---|---|---|
| `log-level` | `2` | `2` | `2` | `2` | `2` |
| `view-mode` | `1` (TEXTURE) | `1` | `1` | `0` (SURFACE) | `0` |
| `video-codec` | `H.264` | `H.264` | `H.264` | `H.265` | `H.264` |
| `debug-video-fault-injection` | delete | delete | delete | delete | `2` |
| `debug-video-fault-rate` | delete | delete | delete | delete | `3` |
| `force-software-decoding` | delete | delete | delete | delete | delete |
| `software-video-decoder` | delete | delete | delete | delete | delete |

R3 and R7 are counted from captures already taken and need no settings and no rig time.

## 5. The lines that decide the round

Every fixed substring below was verified with `grep -F` against `e2e65855`. Three lines are
assembled at runtime rather than written out in the source, so they are marked — grep the fixed part
of those, never the whole sentence:

- `AapRead: DELTA_CHANGED on VIDEO` comes from `AapRead: %s on %s - %s%s`. **Grep `DELTA_CHANGED`.**
- `Throughput over Nms: …` and the `AapProjectionActivity: picture idle …` pair interpolate their
  numbers mid-sentence. Grep the fixed prefix given in each row.

**#852, new in the candidate — their presence also confirms which APK is live:**

| Line | Level | Means |
|---|---|---|
| `AapProjectionActivity: picture idle for Nms but the link spoke Mms ago - Android Auto has stopped sending, not disconnected` | W | **the fix working.** `Mms` is the phone's real idle cadence — the number the whole threshold rests on. Throttled to one per 10 s, re-armed the moment frames resume |
| `AapProjectionActivity: picture idle for Nms and the link has been silent for Mms - treating this as a lost connection` | W | the gated overlay firing: a stopped picture the candidate *still* calls a lost connection |

Both print the quiet time through one formatter, which renders "the whole session" instead of a
number when the phone has not sent anything at all yet. That variant is not a cadence measurement —
do not average it in; report it separately if it appears.

**#852, pre-existing:**

| Line | Level | Means |
|---|---|---|
| `Showing reconnecting overlay` | I | the defect, on the baseline. **Count only the standalone line** — the disconnect line below contains the same words. `grep -ac '| Showing reconnecting overlay$'` separates them |
| `Hiding reconnecting overlay — frames resumed` | I | note the em dash |
| `AapProjectionActivity: connected but no frames - requesting video focus (unsolicited)` | W | the #822 recovery request. **R3 counts these** |
| `AapProjectionActivity: Unexpected disconnect. Showing reconnecting overlay and waiting up to` | I | the disconnect path, R4. The seconds and the trailing text are interpolated — grep the prefix |
| `AapProjectionActivity: Reconnect timed out (20s). Finishing activity.` | I | R4's end state if nothing recovers |

**Video, for R5 and R6:**

| Line | Level | Means |
|---|---|---|
| `Codec initialized:` | I | one per codec build. The round's primary count |
| `Throughput over Nms: rendered=N (Nfps), fed=N (Nfps), dropped=N, skipped=N, inputWait=Nms, codec=…` | I | the global sentinel, and the idle gate for R1/R2 |
| `DELTA_CHANGED` (composed: `AapRead: DELTA_CHANGED on VIDEO - …`) | W | the framing audit firing. **Must be zero on a clean session** |
| `AapRead: fragment accounting established for` | I | the audit's baseline, once per fragmenting channel |
| `Decoder has had no keyframe since it started` | W | the starvation branch deferring a rebuild |
| `cycling video focus` / `retaking video focus` | W | a focus cycle spent to force a keyframe |
| `keyframe reached the codec` | W | the cycle worked |
| `access unit classified` | I | the config scanner's answer, once per distinct kind per session |
| `parameter sets changed mid-session` | W | the encoder reconfigured. Measured zero so far |
| `AapVideo: FAULT INJECTED (#N of M candidates)` | W | R6's injected fault, with its denominator |
| `AapVideo: fault injection - MODE 1-in-N, M candidates seen, K injected` | W | the 15 s summary. **A run with zero faults still prints this**, which is how "the stream did not fragment" is told apart from "the setting did not take" |

**Read-desync, for R7 — every one of these ends the session:**

| Line | Level | Means |
|---|---|---|
| `AapRead: fragment total read returned` | E | a short read on the 4-byte fragment total |
| `AapRead: body read returned` | E | a short read on the message body |
| `AapRead: declared message size` | E | a declared length outside the buffer |
| `Disconnecting to resync` | E | the common suffix of all three |

## 6. Runs

### R0 — build and unit-test gate

`build_hur.sh` for each SHA, then `run_unit_tests.sh` on the candidate.

- **PASS:** both compile, and the suite reports **472** tests — `main`'s 312, plus 145 from
  `fix/video-pipeline` and 15 from `fix/session-liveness`. All green.
- **FAIL:** stops the round. Quote the compiler output. This is the merged tree's first compile, so a
  failure here is most likely in one of the four auto-merged files listed in §2 — say which.

### R1 — reproduce #852 on 3.2.5 (gate for R2)

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
  set up the condition — say so and retry once; twice failing makes #852 INCONCLUSIVE for the round.
- **PASS = the defect reproduces:** `Showing reconnecting overlay` appears within ~10 s of the last
  rendered frame, and repeats. Record how many times, and the interval between them.
- **FAIL / INCONCLUSIVE:** no overlay in 5 minutes of confirmed idle. Then this rig does not
  reproduce #852 and R2 cannot be interpreted — report it, skip R2 and R3, and run the rest.

### R2 — the candidate, playing then paused (the #852 verdict)

Candidate APK, same setup. Two phases in **one** capture:

1. **2 minutes with playback running** — the control. The screen animates, frames flow.
2. **5 minutes paused** — the reported condition.

- **Record, as numbers:** count of standalone `Showing reconnecting overlay` (expect **0**); every
  `picture idle for Nms but the link spoke Mms ago` line **with its `Mms` value** — this is the
  round's headline deliverable; `Throughput` totals for each phase; count of
  `requesting video focus (unsolicited)` (feeds R3).
- **PASS:** zero standalone `Showing reconnecting overlay` across the whole capture, the new idle
  line present during phase 2 and absent during phase 1, and phase 1's throughput comparable to the
  baseline's while playing.
- **FAIL:** any standalone `Showing reconnecting overlay`, or the new line appearing while the
  picture is actively moving.
- **A finding either way:** if `picture idle … and the link has been silent for Mms - treating this
  as a lost connection` appears, the phone really did go quiet for over 10 s on an idle screen and
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
  `Unexpected disconnect. Showing reconnecting overlay and waiting up to` (the `Disconnected`
  collector, the expected one) or `picture idle … and the link has been silent … - treating this as a
  lost connection` (the gated path). Either is a pass; **record which**, because that tells us which
  path actually carries real losses.
- **FAIL:** no overlay at all within 30 s of the radios going down.
- Restore the phone's radios and confirm before finishing, whatever the verdict.
- Killing the link kills the socket, so the `Disconnected` collector normally wins the race. There is
  no way on this rig to manufacture a link that goes silent while the socket stays up, which is the
  only case the gated path covers. R4's job is to prove the fix did not break the ordinary loss.

### R5 — clean session regression (the merge did not break the video work)

No injection. **Ten minutes**, H.265, `SURFACE`, whatever Android Auto shows by default — no
scripted interaction needed. PASS is a set of zeroes and ones, each against a number the video
round 2 recorded on this rig:

- **`Codec initialized:` exactly 1.**
- **Zero** `Decoder has had no keyframe since it started`, **zero** `cycling video focus`, **zero**
  `retaking video focus`. The recovery machinery must be unreachable on a healthy stream.
- **Zero** `DELTA_CHANGED`. Before the audit fix this was exactly 10 false ones per
  session; round 2 measured zero. Anything else means the merge disturbed the readers.
- **One** `AapRead: fragment accounting established for` per fragmenting channel (VIDEO, and
  MUSIC_PLAYBACK if music plays). Quote the whole line — it carries the per-fragment delta, measured
  at a flat **-29 bytes** per fragment.
- All three `access unit classified` answers present, once each, in the first moments of the session:
  `PARAMETER_SETS_ONLY`, `PARAMETER_SETS_WITH_PICTURE`, `NO_PARAMETER_SETS`.
- **Zero** `parameter sets changed mid-session`. Firing on every keyframe would be the FAIL shape —
  it would mean the change-latch is wrong, not that the encoder reconfigured.
- No `FATAL EXCEPTION` / `AndroidRuntime`, and `rendered` steady in the throughput lines.

### R6 — the decoder wedge still recovers (the merge did not break the fix)

`debug-video-fault-injection=2` (`DROP_MIDDLE_FRAGMENT`), rate `3`, H.264, `SURFACE`. **Three
minutes.** This is video round 2's own R2, re-run on the merged tree; its numbers there were 2 faults,
2 rebuilds, 2 cycles, keyframes at **0.544 s** and **0.557 s**, `rendered` never zero.

- **The picture will look bad. That is the correct outcome** at this rate and is not a failure.
- **INCONCLUSIVE if fewer than 2 `FAULT INJECTED` lines appear** — the injector targets middle
  fragments and how often a frame fragments depends on what the phone is showing. The new
  `fault injection - … candidates seen …` summary tells you this *during* the run: if candidates are
  accumulating and injections are not, lower `debug-video-fault-rate` toward `2` and restart. If
  candidates are barely moving, the content is not fragmenting — say so and move on.
- **PASS, all four:** at least one `cycling video focus` each followed by `keyframe reached the codec`
  within ~2 s; `rendered=` non-zero on some windows after the first stall; no run of `rendered=0`
  longer than ~25 s; and `Codec initialized:` at most one per injected fault plus the initial one.
- **FAIL:** sustained `rendered=0`, or the restart budget exhausted (the 20/30/40/50 s suppressed
  stall ladder). That is the pre-fix wedge signature and would mean the merge undid the fix.

### R7 — the read-desync fix is silent on a healthy link (no new run)

Counted across R2, R5 and R6's captures.

- **PASS: zero** of `fragment total read returned`, `body read returned`, `declared message size`,
  `Disconnecting to resync`.
- **FAIL: any of them on a session that was otherwise healthy.** Each one now *ends the session*
  where the old code skipped a message and carried on, so a false positive here is a dropped
  connection rather than a log line. If one appears, quote it with the twenty lines either side —
  whether the session actually died is the whole question.

## 7. Do not re-run

- **Whether a Home press tears down the projection surface.** It does not on this unit; twelve
  scripted cycles proved it.
- **Feed queue depth, the dropped-reference-frame chain, and the black-screen work.** Settled in
  `video-dropped-frame-keyframe` rounds 5-6 and `video-black-after-background` round 8.
- **The natural ~68-69 s keyframe cadence.** Quote it if useful; do not remeasure.
- **Focus mode 4 / `PROJECTED_NO_INPUT_FOCUS`.** Fired in video round 2 and measured inert — no sink
  stop, no new session, no keyframe. The probe has been removed from the code. Closed.
- **The full video round 2 matrix.** R5 and R6 here are the two runs worth repeating after a merge;
  the rest of that round does not need redoing.

## 8. Report back

Keep the two questions separate — the release decision needs both, and conflating them is how a
video regression gets read as an #852 failure.

1. **#852: does 3.2.5 reproduce the overlay on this rig, and does the candidate show zero?**
   (R1, R2, R3.) That trio is the shipping question for the reported defect.
2. **Every `link spoke Mms ago` value from R2.** Android Auto's idle inbound cadence has never been
   recorded by anyone — the phone's `PingRequest` is answered without logging. `LINK_QUIET_MS` is
   currently a judgement at 10 s; these numbers replace it with a measurement.
3. **The count of `requesting video focus (unsolicited)` during R2's paused phase.** (R3.) Non-zero
   means recovery survived the split. Zero means the fix went too far and must be reworked.
4. **Did the merge hold?** (R5, R6, R7.) R5's zeroes and R6's four conditions are against numbers
   already on record, so any disagreement is a merge regression and names the file to look at.
   R6 is the one that exercises the video half of the auto-merged files; R7 the transport half.
5. **Anything in a capture that none of the above asked about.** This is the first time these two
   branches have run together, and the round is deliberately broad because of it.
