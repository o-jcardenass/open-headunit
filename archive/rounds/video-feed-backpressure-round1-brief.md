# video-feed-backpressure, round 1 brief: pace the transport instead of shedding reference frames

**Candidate:** `fix/video-feed-backpressure` @ `4731a2c7` on `fork` (`o-jcardenass/open-headunit`).
**Baseline:** `test/baseline-feed-hold` @ `bf389ccf` on `fork`. **Two APKs, and the baseline is not optional** - see §2.

```bash
git fetch fork
git checkout -B fix/video-feed-backpressure fork/fix/video-feed-backpressure
git rev-parse HEAD          # must print 4731a2c7...
git log --oneline -5
# 4731a2c7 Video: the pacing counter counts the waits that ran out of budget too
# 6760afbc Video: debug hold on the feed thread, to exercise backpressure on a rig
# 87417f19 AAP: let an exhausted escalation budget recover once the fault allows it
# 6baa7606 Video: pace the transport thread instead of shedding reference frames
# 048f4eaf Merge pull request #845 ...   <- main

git checkout -B test/baseline-feed-hold fork/test/baseline-feed-hold
git rev-parse HEAD          # must print bf389ccf...
# bf389ccf Video: debug hold on the feed thread, to exercise backpressure on a rig
# 048f4eaf Merge pull request #845 ...   <- main
```

`test/baseline-feed-hold` is `main` plus the debug hold and **nothing else**. It is a test artefact,
not a shipping branch; delete it after the round.

---

## 1. Why this round exists

A reporter's unit goes blocky and washed out after 20-30 minutes of driving. Their log says what is
happening, in one line repeated:

```
17:14:39  rendered=215 (42fps) fed=216 dropped=36 inputWait=3885ms  OMX.MTK.VIDEO.DECODER.AVC
17:14:44  rendered=210 (41fps) fed=212 dropped=56 inputWait=3950ms
17:15:49  rendered=202 (40fps) fed=205 dropped=53 inputWait=3958ms
```

The codec is decoding and rendering the whole time - it is simply slower than the 60fps the phone
negotiated. The feed queue filled, and the enqueue answered every arrival burst by shedding the
newest access unit. **Every unit in this stream is a reference frame**, so each shed one costs a
washed-out picture until the phone's own keyframe, which arrives on a fixed period measured between
69 and 120 seconds depending on the phone. The escalation ladder's three-cycle budget cannot outrun
a fault that recurs every second.

The same unit ran the same negotiation artefact-free before the feed queue existed, because the
synchronous feed blocked the transport's read thread and the phone's ack window throttled it.

**The candidate restores that flow control, bounded.** With the queue full, `decode()` now waits for
a slot in 50ms slices up to one second instead of shedding. While it waits, the read thread sends no
media acks, the phone's unacked-message window closes, and the phone slows to the rate the codec
actually drains. Past the one-second budget the frame is shed exactly as before, because a codec that
took nothing for a whole second is wedged rather than slow, and a wedge belongs to the `sync_stall`
watchdog.

**What this round has to establish, in order:** that the lever reaches the mechanism at all (R1),
that pacing replaces shedding (R2), and **what the pacing costs the rest of the session** (R3). The
read thread is not the video thread - it carries audio, control, keepalive pings, mic start/stop,
sensors and every SSL unwrap, and nothing on that list has an independent inbound path. Pacing it
paces all of them. Nobody has measured that, and R3 is the number this round exists to produce.

---

## 2. What is different about this round

- **The baseline is the positive control and the round is worthless without it.** This rig's codec
  is healthy, so the pacing path is unreachable on it by ordinary means. Both builds carry a debug
  lever (`debug-video-feed-hold-ms`) that sleeps the feed thread after every frame it hands the
  codec, capping the drain rate while the codec itself stays healthy and rendering - the exact shape
  of a real decoder at its ceiling. The baseline can be slowed *and still sheds*; the candidate can
  be slowed and should not. **R2's `dropped=0` is satisfied both by "the pacing worked" and by "the
  queue never filled", and only R1 tells those apart.** Run R1 first, and if it does not produce
  drops, nothing after it means anything.
- **A session with the hold on announces it loudly at feed-thread start.** No capture taken this way
  can be mistaken for a log of a real fault. Its absence in a run that wanted it is a setup failure,
  not a result.
- **No USB accessory path on this rig, so the round runs over Native AA wireless.** The reporter is
  on USB, where the phone's video `max_unacked` window is 16 messages; on wireless it is 12. The
  pacing mechanism is transport-agnostic and the window size is not, so the wireless arm is a
  slightly *tighter* test of the throttling, not a weaker one. Say in the results that this is what
  was measured.
- **`KEYCODE_HOME` does not tear down the projection surface on this unit** (§7a). R5's lever is
  `headunit://disconnect`, and R5 must confirm the teardown actually happened before measuring
  anything downstream of it.
- **R4 is expected to be INCONCLUSIVE and that is a valid result.** It needs a genuinely wedged
  codec, which no hold value can produce - the hold is capped at 250ms and a slot therefore frees
  every 250ms, four times inside the budget. It is run because the fault injector is the only thing
  on this rig that has ever wedged this decoder, not because it is expected to. Do not chase it.
- **The fault injector does nothing at its default rate of 300** (§7a). R4 sets the rate explicitly.
- `settings.xml` carries the last thread's non-defaults - the `connection-failure-banner` round left
  `wifi-direct-band` and hotspot keys behind. Diff against a fresh backup and state the delta even if
  it is zero.
- `grep -a`, always.

---

## 3. Settings keys this round needs

| Key | Type | Element | Why |
|---|---|---|---|
| `debug-video-feed-hold-ms` | int | `<int name="debug-video-feed-hold-ms" value="40" />` | **New.** The lever. `0` is off; the app coerces anything written here into 0-250. |
| `wifi-connection-mode` | int | `<int name="wifi-connection-mode" value="3" />` | Native AA, this rig's only transport. |
| `native-ap-transport` | int | `<int name="native-ap-transport" value="0" />` | WiFi Direct. Set it explicitly every run. |
| `fps-limit` | int | `<int name="fps-limit" value="60" />` | Pins the queue depth at 30 frames so the feed-thread line is comparable across every run. |
| `video-codec` | string | `<string name="video-codec">H.264</string>` | R1-R5 and R7. R6 flips it to `H.265`. |
| `log-level` | int | `<int name="log-level" value="2" />` | INFO. See §4. |
| `debug-video-fault-injection` | int | `<int name="debug-video-fault-injection" value="5" />` | **R4 only.** Mode 5, `DROP_MIDDLE_FRAGMENT_IN_READER`. Deleted everywhere else. |
| `debug-video-fault-rate` | int | `<int name="debug-video-fault-rate" value="3" />` | **R4 only.** One in three. At the default 300 the injector fires nothing. |

Before the first launch, check `shared_prefs` ownership as the last two rounds had to:

```bash
adb shell stat -c '%U:%G %a' /data/data/com.andrerinas.headunitrevived/shared_prefs
adb shell run-as com.andrerinas.headunitrevived id
```

If it is root-owned again, `chown` it to the uid that prints and say so. Without it, every
settings-read check in this brief is INCONCLUSIVE.

---

## 4. Log level, and why it is not VERBOSE

**`log-level=2` (INFO).** Every line this round reads is `AppLog.i` or `AppLog.w`, and neither is
wrapped in a `LOG_VERBOSE` guard - checked at the guard, not at the call
(`AppLog.kt:197-241`). This unit's driver stack floods logcat, so VERBOSE would cost evidence by
wrapping the ring buffer and buy nothing.

---

## 5. The lines that decide every run

All copied from `4731a2c7` and verified with `grep -aF` against the branch.

| Grep string | Level | Means |
|---|---|---|
| `Feed thread: DEBUG hold` | w | **the lever is on.** Absent in a run that wanted it is a setup failure. |
| `Feed thread started (queue holds` | i | the depth and the ms it holds, once per decoder start. |
| `Feed queue full - pacing the transport thread instead of shedding frames` | i | **new. pacing engaged.** Throttled to one line per interval, with a `(+N more...)` suffix. |
| `enqueueWait=` | i | **new field** inside the throughput line, appended after `inputWait=`. |
| `VideoDecoder: dropped a reference frame, requesting keyframe` | w | a frame was shed at the enqueue. Throttled to one a second. |
| `Input buffer full. Dropping frame.` | w | the **feed thread's** own drop. A different site, do not conflate the two counts. |
| `Decoder restart requested:` | w | a restart, with its reason. `sync_stall` is the one that matters here. |
| `Decoder stopped:` | i | R5's teardown proof. |
| `cycling video focus` | w | the escalation spent a cycle. |
| `quiet stream earned back` | w | R7's line. May never appear; see R7. |
| `disabled due to previous underrun` | AudioTrack, system | the audio instrument. Survives INFO and minification, so it compares directly across builds. |

The throughput line now reads:

```
Throughput over 5007ms: rendered=N (Nfps), fed=N (Nfps), dropped=N, skipped=N, concealed=N, inputWait=Nms, enqueueWait=Nms, codec=...
```

`enqueueWait` is appended after the pre-existing fields, so every earlier grep of this line still
works.

---

## 6. Runs

**Every run reports the `Feed queue full - pacing` count *and* the throughput line.** A count on its
own cannot separate "the fix worked" from "the condition was never reached". Pair every count with
the measurement that proves it was reachable.

Route and screen activity have to match between R1 and R2 or the comparison is void. Use the same
navigation screen, moving, for the same duration - a static map sends almost nothing and neither run
will fill a queue.

### R0 - build and unit gate

Both APKs built from the SHAs above, md5s recorded and **different**, installed with
`adb install -r` (never uninstall/reinstall). Confirm which one is live before each arm.

```bash
./gradlew :app:assembleGithubDebug        # once per branch; copy the APK out of apks/ immediately
./gradlew :app:testGithubDebugUnitTest    # candidate only
```

**PASS:** clean build on both, suite green on the candidate, with:

| Suite | Test methods |
|---|---|
| `VideoFeedThrottlePolicyTest` | 8 (new file) |
| `KeyframeCycleEscalationPolicyTest` | 43 (40 at `048f4eaf`) |

Total: expect **749**, against 738 at `048f4eaf`, the merge base both branches sit on. `fork/main`
is a stale ref carrying 565 and is not the comparison. Report the number either way. A failure here
stops the round.

### R1 - the positive control. Run this first.

Baseline `bf389ccf`. `debug-video-feed-hold-ms=40`, everything else per §3. One session, **10
minutes** of a moving navigation screen.

**PASS, both:**

- `Feed thread: DEBUG hold 40ms per frame` present exactly once;
- **`dropped` non-zero in multiple throughput windows.** Report the per-window numbers, not a total.

`Feed queue full - pacing` cannot appear here - that line does not exist in this build. Its absence
is not a result.

If `dropped` stays 0 across the whole run, the hold is too small for this rig's arrival rate.
**Step to `100` and re-run**, then use the value that worked for R2 as well. Say which value was
used. Without a value that produces drops on the baseline, R2 proves nothing and the round should
stop here and report that.

### R2 - pacing replaces shedding. The point of the round.

Candidate `4731a2c7`. Same hold value R1 succeeded at, same route, same 10 minutes.

**PASS, all three:**

- `Feed queue full - pacing` at least 1, and report the total including the `(+N more)` suffixes;
- `enqueueWait` non-zero and **rising across windows**;
- **`dropped=0` in every window.**

Report `rendered` and `fed` alongside. If those collapsed too, the pacing is costing frames rather
than saving them, and that is a FAIL however clean `dropped` looks.

Also count, and expect zero: `grep -ac "Decoder restart requested: sync_stall"`.

### R3 - what the pacing costs the rest of the session

Candidate. Four steps, **5 minutes each**, hold = `10`, `25`, `40`, `100`. Music playing from the
phone throughout - force-stop and relaunch the media app before the first step, because media keys
alone do not open a fresh audio channel (§7a). Keep one session across all four steps if the rig
allows it; if a settings write forces a relaunch, say so.

Report per step, as numbers:

- `grep -ac "disabled due to previous underrun"` **divided by the step's span in minutes**;
- the `Feed queue full - pacing` count;
- the median `enqueueWait` per window, and the maximum;
- the median `rendered` fps.

**`10` is expected to be inert.** At that hold the codec drains far faster than the phone sends, the
queue never fills, and zero pacing lines there is the correct result and a second control - not a
failure. If it *does* pace, that is a finding worth its own paragraph.

**There is no PASS condition on this run.** It produces the number, and the number decides whether
the one-second budget is the right one. Report it; do not judge it.

### R4 - budget expiry still sheds. Expected INCONCLUSIVE.

Candidate. `debug-video-feed-hold-ms=250` (written straight into `settings.xml`; the on-screen
picker stops at 100), `debug-video-fault-injection=5`, `debug-video-fault-rate=3`. One session until
the injector's own summary reports its faults, or 5 minutes, whichever is longer.

**PASS:** `dropped` non-zero again while `Feed queue full - pacing` is also present - the enqueue
gave up on a frame after the full budget, which is the behaviour that keeps a wedged codec from
blocking the transport forever.

**INCONCLUSIVE** if the codec never wedges - report the injector's fault count and the `Decoder
restart requested` count and move on. That coverage sits on `VideoFeedThrottlePolicyTest` and this
run is a bonus, not a gate. Do not extend it or try other modes.

### R5 - a teardown is not held behind the wait

Candidate. `debug-video-feed-hold-ms=100`. Let a session settle, confirm pacing is happening
(`Feed queue full - pacing` present), then:

```bash
adb shell am start -a android.intent.action.VIEW -d "headunit://disconnect"
```

**PASS, all three:**

- `Decoder stopped:` **within one second** of the disconnect - quote both timestamps;
- no ANR in the capture;
- no `Feed thread started` or `Feed thread: DEBUG hold` line from the old session appearing after a
  new one starts.

**Confirm the teardown actually happened before reading the rest.** Home does not produce one on this
unit, and a run that never tore anything down passes this vacuously.

### R6 - H.265 is untouched. The regression guard.

Candidate. `debug-video-feed-hold-ms=0`, `video-codec=H.265`. 10 minutes.

**PASS, all three:**

- `enqueueWait=0ms` in **every** window;
- zero `Feed queue full - pacing`;
- `dropped=0` in every window.

A reporter on a different unit has just confirmed 761 clean five-second windows on the H.265 path
with `dropped=0` throughout, and this change must not disturb it. Report the median `rendered` fps
so the run is on record as having actually carried video.

### R7 - the escalation refund. May not fire.

Candidate. `debug-video-feed-hold-ms=40`, H.264, **15 minutes continuous**, same moving screen.

If `quiet stream earned back N focus cycle(s)` appears, the `cycling video focus (X/3)` line that
follows must have `X` inside the refunded budget, and the drive counter in the refund line must not
exceed 8.

**If it never appears, that is a valid result** and the path stays unproven on hardware, covered by
`KeyframeCycleEscalationPolicyTest`. Do not extend the run to chase it.

---

## 7. Round-wide

Across every capture:

```bash
grep -ac "Decoder restart requested: sync_stall"   *.txt   # expect 0 outside R4
grep -ac "Input buffer full. Dropping frame."      *.txt   # the feed thread's own drops, for context
grep -a  "Throughput over" *.txt | grep -c "rendered=0 "   # expect 0 outside R4
```

Discard rules per §4 of the template: `grep -ac "createGroup SUCCESS"` greater than 1 in one run is
the discard, and a lone `MATCH! Starting AapService` with no group churn attached is the phone's own
Bluetooth reconnect and is benign.

---

## 8. Report back

Five numbers decide whether this ships:

1. **R0**: the total against 749, and the two suite counts.
2. **R1**: the per-window `dropped` numbers, and which hold value produced them. Without this the
   round has no control.
3. **R2**: `dropped` against R1's, plus `rendered`/`fed` to prove the frames were not simply lost
   elsewhere.
4. **R3**: underruns per minute at each of the four holds. This is the cost nobody has measured.
5. **R6**: `enqueueWait` on the H.265 arm.

One extra count, for a separate open question rather than for any verdict:

```bash
grep -ac "send failed (ret=" *.txt
```

The reporter's session ended with the link itself dying - both channels returning a zero-length
payload, then 75 write failures over 38 seconds. Nothing in this candidate addresses that, and
whether the overload caused it is only a hypothesis. If the count is zero across a paced session it
is worth knowing; it is not evidence either way.
