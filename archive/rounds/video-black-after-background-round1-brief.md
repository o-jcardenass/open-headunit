# Round 1 brief — black screen after backgrounding the projection

Read `TESTING-TEMPLATE.md` §7a first. Three entries there decide how this round is set up: the driver
stack floods logcat, the default renderer is not what you would guess, and Native AA wireless is the
only transport this rig has.

This round is unusual in two ways, both worth stating up front.

**There is no candidate branch.** No fix has been written. This is a diagnostic round on two *released
tags*, and its entire job is to decide which of three mechanisms is at work before a line of
`VideoDecoder.kt` is touched. The A/B is `v.3.2.3` against `v.3.2.4`, and R3 is the point of the
round.

**A FAIL here is the good outcome.** It means the reported defect reproduced on a rig we control. Say
which cycle failed, capture it, and move on — do not retry a failed cycle hoping for a pass.

---

## 1. Build

Two builds, both from tags on `origin`, both from source. There is no branch to check out.

| Build | Tag | SHA |
|---|---|---|
| **A** | `v.3.2.4` | `c9556803` |
| **B** | `v.3.2.3` | `e900de78` |

```bash
git fetch origin --prune --prune-tags
git rev-parse v.3.2.4 v.3.2.3
# expect: c9556803bbf90f495f5be1a360c7698b59ac8637
#         e900de7832178e91558749bc6c781fa689130661
```

`9f98afd1` — *Video: catch up after a link stall instead of replaying the backlog* — is the **only**
commit touching video decode between those two tags. Nothing in `view/`, `AapVideo.kt`,
`AapProjectionActivity.kt` or `AapService.kt` changed on the video path in that window. So R2 against
R3 isolates that one commit exactly.

**Do not install the published release APKs.** They are not debuggable, so `run-as` cannot read or
write `settings.xml`, and their signature would force an uninstall — which re-runs the setup wizard
and rewrites resolution, DPI and video codec, three variables this round would then be testing by
accident. Build both from source with `build_hur.sh`, install with `adb install -r`, record both md5s
and confirm which one is live before each run (§5).

### R0 — build gate

`run_unit_tests.sh` on `v.3.2.4`, then `build_hur.sh` for both tags.

- Full suite green on A. `DecoderStopPolicyTest` must be present and green — if that class is missing,
  the wrong SHA is checked out.
- Both APKs built, **md5s recorded and different**.

**If R0 fails, stop and report.**

---

## 2. What this is and why it exists

A user on 3.2.4 reports that minimising the app or switching away and coming back leaves the
projection black — sometimes with the "Android Auto is starting…" overlay, sometimes bare black.
**Audio keeps working the whole time.** A 1–2 second switch is fine; longer is not. Only fully closing
and reopening the app restores the picture.

Audio surviving is not a clue. AUD (channel 6) and VID (channel 2) are dispatched separately with
independent ACK windows, and the audio path never touches `VideoDecoder`. Audio survives *every*
video-side failure, so it tells us nothing except that the link is alive.

Backgrounding is the only thing that destroys the projection surface, and surface teardown is the only
thing that stops the decoder mid-session, so the whole search space is one code path. Reading it
turned up three defects on it. Each one alone produces exactly this symptom, which is why a log is
needed before anything is changed.

### M1 — a failure latch that a returning surface can never clear

`VideoDecoder.setSurface()` only tears the decoder down when a codec object still exists:

```kotlin
if (codec != null || softwareHevcDecoder != null) { stop(DecoderStopPolicy.REASON_NEW_SURFACE) }
```

and `decoderPermanentlyFailed` is reset **only** inside `stop()`. So once that flag is set, `codec` is
already `null`, `setSurface()` skips `stop()`, and nothing clears it — not a new surface, not a
renderer switch, not another background cycle. `decode()` then returns immediately for every frame for
the rest of the process. That is what "have to fully close the app" looks like from the inside.

Getting there: a surface teardown wipes the cached SPS/PPS and zeroes `lastFrameRenderedMs`, while
`codecTypePinned` stays true (deliberately — that was the 3.2.3 fix for the wrong-decoder bug). On
resume the decoder can restart on a mid-GOP P-frame with no CSD and produce no output. The sync-stall
watchdog restarts it every 8 s; after three restarts with nothing rendered it **flips the pinned codec
type to the other one**, which is guaranteed wrong for the running stream, and three more strikes set
the latch.

**That budget is ≈26 s measured from the resume, not from the Home press.** The output thread is
stopped while backgrounded, so no stall can be detected during the hold. What this predicts is how
long after coming back the screen has to stay black before the failure becomes permanent — which is
why every run below observes for 60 s rather than 15.

The one exception is GLES, where `stop()` is never called on backgrounding at all (see R4), so there
the budget *is* burned during the hold and the ≈26 s applies to the hold duration.

### M2 — `stop()` can release the MediaCodec while the feed thread is inside it. New in 3.2.4.

Before `9f98afd1`, the MediaCodec input feed ran inside `decode()`'s `synchronized(this)`, and
`stop()` is also `synchronized(this)`. That monitor made it impossible for `codec.release()` to run
while another thread was inside a MediaCodec call.

`9f98afd1` moved feeding onto its own `VideoDecoder-Feed` thread — correctly, to get a 300 ms wait off
the transport read thread, which was starving audio and control. But it replaced the monitor with a
timed join: `stop()` interrupts the feed thread, joins it for **200 ms**, then releases the codec
whether or not the join succeeded. The feed loop can block for **300 ms**
(`30 × dequeueInputBuffer(10 ms)`), and `interrupt()` does not abort a MediaCodec call.

The 100 ms gap is not incidental — it is correlated with backgrounding. When the surface goes away the
codec cannot render output, its input queue fills, and the feed thread parks for the full 300 ms;
that is precisely when `surfaceDestroyed` calls `stop()`. Calling into a released MediaCodec either
throws — swallowed by a `catch (e: Exception)` and invisible — or wedges the vendor component, and a
wedged component survives further cycles and needs the process killed.

### M3 — nothing retries

Both recovery watchdogs in `AapProjectionActivity` are dead in a live session.
`reconnectingWatchdog` returns unless the connection state is `HandshakeComplete` **and does not
re-post itself** — but the steady state during projection is `TransportStarted`, so it dies on its
first tick after `onResume`, taking the whole display-stall recovery with it. `videoWatchdogRunnable`
re-arms only *inside* its "loading overlay is visible" branch, so it stops once the first frame of the
session hides the overlay.

Net: when the activity is not recreated, the entire resume path asks for a keyframe exactly once. If
that request is lost, nothing ever asks again. This is not a 3.2.4 regression — it predates both tags,
and it is why any of the three turns permanent instead of glitching for a second.

---

## 3. What is different about this round

**INFO is the right log level here, not VERBOSE.** Every line that decides a run below is
`AppLog.i`/`.w`/`.e` — verified with `grep -F` against the source. §7a warns this unit's driver stack
floods logcat, and VERBOSE would wrap the ring buffer during the long holds while buying nothing.
Set `log-level=2`.

**Use both log streams, because this round needs framework lines too.** Set `log-source=1` so our
`OPENHU` lines go to the app's own file, which is immune to the logcat ring buffer — and keep a logcat
capture running for the framework, because `ACodec` / `OMX` / `MediaCodec` errors are what distinguish
M2's wedged component and those never appear in our file. Enlarge the buffer once per boot:

```bash
adb logcat -G 16M
```

The app's file lands at
`/sdcard/Android/data/com.andrerinas.headunitrevived/files/HUR_Log_<ts>.txt`, one per `AppLog.init`.
Keep both files per run and record the wall-clock time of every Home press and every relaunch so the
two can be lined up.

**The default renderer is TextureView, not SurfaceView.** `view-mode` defaults to `1`, so the reporter
is most likely on TEXTURE — every run except R4 uses it. Do not assume SURFACE.

**Keep Android Auto animating.** Turn on `fake_speed` on the phone so the map keeps redrawing. A static
AA screen drops to a few frames per second on its own, and reading that as our defect has cost days
before.

**Native AA wireless (mode 3) is the only transport on this rig** — there is no USB accessory path, so
every run connects the same way and no run should ask for USB.

**Check `hur-wifi-test-scripts/` for a cycling script before writing one.** The codec-pin measurement
on 2026-08-07 ran 20 adb-driven background/foreground cycles on this same unit, so the script may
already be there. If it is not, add one and list it in Setup notes.

**One run may be expected to fail badly.** R4 (GLES) is the worst case by design, not a contaminated
run — record it and carry on.

---

## 4. Settings keys this round needs

| Key | Type | Values |
|---|---|---|
| `log-level` | int | `2` INFO |
| `log-source` | int | `1` APPLOG_FILE |
| `log-capture-enabled` | boolean | `true` |
| `view-mode` | int | `1` TEXTURE (default) · `2` GLES |
| `video-codec` | string | `Auto` · `H.264` in R5 |

Use `hur-wifi-test-scripts/set_pref.sh` / `set_hu_prefs.sh` per §5. The three log keys are set once for
the whole round; only `view-mode` and `video-codec` change between runs. Remember §5's warning that
`set_hu_pref.sh` relaunches the app on every call — use the multi-key sibling for the initial setup.

`video-codec` is a **string**, and the value the code compares against is exactly `H.264`. Setting it
is meaningful: it is passed into `VideoDecoder.decode()` on every frame from `AapVideo`.

---

## 5. Driving it

```bash
PKG=com.andrerinas.headunitrevived
ACT=$PKG/com.andrerinas.openheadunit.aap.AapProjectionActivity   # exported, singleTask

adb shell input keyevent KEYCODE_HOME     # background
sleep "$HOLD"
adb shell am start -n "$ACT"              # foreground
sleep 60                                  # observe — see below on why 60 and not 15
```

**Observe for 60 s after every relaunch.** The sync-stall watchdog restarts the decoder on an 8 s
cooldown and needs several rounds to reach the latch, so a 15 s window cannot tell "recovered slowly"
from "never recovered", and those are different findings.

### The PASS check is scriptable — do not judge it by eye

Per §0 a verdict must not depend on someone watching the screen. The recovery marker is a log line,
and it is emitted once per decoder start (the flag behind it is cleared in `stop()`), so it fires on
every restart, not just the first of the session:

```
OPENHU ... First frame rendered (hardware decode)
```

**PASS for a cycle = that line appears after the cycle's `New surface set:` line, within 60 s.** Back
it with the sustained rate from the throughput tick, which should return to a non-zero `rendered`:

```
OPENHU ... Throughput over 5000ms: rendered=N (Nfps), fed=N (Nfps), dropped=N, skipped=N, inputWait=Nms, codec=...
```

Also look at the screen and say what you saw — black, black with the "Android Auto is starting…"
overlay, or a picture — as a separate observation. That is real evidence and the two variants matter
(they distinguish whether the activity was recreated), but the **verdict** comes from the log.

### Measure this for every cycle

- **Seconds from `New surface set:` to `First frame rendered (hardware decode)`.** Give the number.
  "Recovered in 1.8 s" and "recovered in 34 s" are different results; "quickly" is not a result.
- Whether the activity was recreated — an `onCreate` in the log for that cycle.

---

## 6. The lines that decide every run

Copied verbatim from the source and verified with `grep -F`. All are INFO or above, so `log-level=2`
carries every one.

| Meaning | Level | Line |
|---|---|---|
| the teardown happened — the run reached the path | I | `Decoder stopped: surfaceDestroyed` |
| a new surface arrived on resume | I | `New surface set: ` |
| the decoder was rebuilt | I | `Configuring decoder: ` |
| **the picture came back** | I | `First frame rendered (hardware decode)` |
| sustained rate | I | `Throughput over ` |
| **M1** — the codec-type flip | E | `Falling back to ` |
| **M1** — the latch | E | `Giving up to avoid an infinite restart loop` |
| M1's approach run, repeating | W | `Decoder restart requested: sync_stall` |
| **M2** | E | `Error feeding input buffer` |
| **M2** | E | `Failed to start decoder` |
| M2, input side | E | `Input buffer feed failed (full)` |
| **M2** — thread-publication race, 3.2.4 only | I | `Feed thread started` immediately followed by `Feed thread stopped` |
| **M3** — absence is the signal | W | `Watchdog: No video received yet` |
| which component was chosen | I | `findBestCodec: ` |

From logcat rather than our file, and only in the M2 case: any `ACodec` / `OMX` error, or
`createByCodecName` failing on the return.

---

## 7. Runs

Every run: clean-run protocol (§4), session up and projecting with `fake_speed` on, confirm the live
APK's md5 first.

### R1 — short cycles, 3.2.4, TEXTURE

Build A. `view-mode=1`, `video-codec=Auto`. Three cycles at `HOLD=3`.

**PASS:** `First frame rendered (hardware decode)` within 60 s of each relaunch, all three cycles.

This is the reporter's "1–2 seconds is ok" claim. If this fails, the analysis above is wrong and the
rest of the round is more valuable, not less — carry on.

### R2 — graduated holds, 3.2.4, TEXTURE

Build A, same settings. One cycle each at `HOLD=10`, `HOLD=30`, `HOLD=90`, in that order.

**PASS:** the marker appears within 60 s after every one of the three.
**FAIL:** any cycle without it. **Stop the run at the first failure** and keep both captures — later
cycles overwrite the evidence of which mechanism fired first, and that is the only thing this round
exists to determine.

Record the recovery time for each cycle even when it passes. The shape of those three numbers is a
result in itself.

### R3 — the same holds on 3.2.3 — **this is the point of the round**

Build B. Identical settings, identical holds, identical observation window.

**PASS:** marker within 60 s after all three.

R2 against R3 isolates `9f98afd1`. Both outcomes are useful and neither is a disappointment:

- **R2 fails where R3 passes** → M2 is implicated and the 3.2.4 threading gets fixed.
- **R2 and R3 fail the same way** → M2 is *not* the cause, the 3.2.4 decoder work is left alone, and
  the fix targets M1 and M3 only.

### R4 — GLES, 3.2.4

Build A. `view-mode=2`. One cycle at `HOLD=45`, then one at `HOLD=120`.

**PASS:** marker within 60 s after each.

**Expected to be the worst run of the set.** On GLES the surface destruction is never reported on a
Home press, so the decoder is never stopped and keeps feeding a `SurfaceTexture` nobody is draining —
the stall budget burns *during* the hold, which is the one place the ≈26 s figure applies to hold
duration. A FAIL here is a measurement, not contamination. Note whether `Decoder stopped:` appears at
all during the hold; if it does not, that is the finding.

### R5 — codec-flip probe, 3.2.4, TEXTURE

Build A. `view-mode=1`, **`video-codec=H.264`** (string). One cycle at `HOLD=30`, observe 90 s.

**PASS:** no `Falling back to ` anywhere in the capture.
**FAIL:** `Falling back to ` appears.

This is the positive control, and it needs no second build. The codec-type flip ignores an explicit
codec choice, so seeing the decoder fall back to H.265 while the user asked for H.264 is M1 caught in
the act — and confirms a second, separate defect at the same time. Note the `findBestCodec: `
selection before and after.

### R6 — latch probe — **only if R2, R4 or R5 produced a black screen**

Immediately after that failure, without restarting the app or the session: one more cycle at
`HOLD=10`, observe 60 s.

- **Marker appears** → the decoder recovered, so nothing was latched. Whatever failed is transient,
  and M1's latch is not what the reporter is hitting.
- **Marker never appears, and the capture contains `Giving up to avoid an infinite restart loop`** →
  **M1 confirmed.** The latch is set and a returning surface cannot clear it.
- **Marker never appears and that line is absent** → something is wedged below us. Check logcat for
  `ACodec` / `OMX` / `createByCodecName`; that reading points at M2.

Then force-stop and relaunch the app and confirm the picture returns — the reporter's workaround. If
it does not, say so; that is a bigger finding than anything else in this round.

If no run produced a black screen, R6 is **UNTESTABLE** and the round's answer is "does not reproduce
on this rig", which is a legitimate result — report it and stop.

---

## 8. Do not re-run

Nothing is settled yet; this is round 1. But do not spend time on:

- **USB.** §7a: this rig has no accessory path. Native AA wireless only.
- **Reproducing by hand.** Every step above is scriptable, and a tap is unrecorded and mistimed.
- **Reaching the renderer or codec setting through the UI.** Both are `settings.xml` keys, and §7a
  records that settings categories are not deep-linkable.

---

## 9. Report back

Beyond the per-run verdicts, these are the numbers that decide what gets written:

1. **Did R2 fail where R3 passed?** One sentence. This decides whether `VideoDecoder`'s threading is
   touched at all.
2. **The recovery times**, in seconds, for every cycle of R1, R2 and R3 — the twelve numbers, even the
   passes. The threshold between "recovers" and "does not" is the thing no amount of code reading has
   been able to predict.
3. **Which mechanism's lines appeared**, from the §6 table, and R6's verdict.
4. **Did R5 print `Falling back to `?**
5. Whether force-stop and relaunch actually restores the picture, as the reporter says it does.

And per §7, anything noticed in passing — that section has produced more real findings than some
rounds' runs.
