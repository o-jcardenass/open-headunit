# Round 3 brief — black screen after backgrounding: verify the fix

Read round 2's results first (`video-black-after-background-round2-results.md`); this round runs its
exact trigger against a candidate branch. `TESTING-TEMPLATE.md` §7a as always — the entries this
investigation added (Home does not tear the surface down here; `build_hur.sh` deletes the previous
APK; tag A/Bs need `install -r -d`) all still apply.

**This is a verification round, and the verdict logic flips back to normal: PASS = the picture
survives the trigger.** Round 2 proved the trigger and the mechanism; this round proves the fix, and
re-proves the two behaviours that must NOT have changed — a legitimate background, and the link-stall
catch-up.

---

## 1. Build

**Candidate:** `fix/822-stale-surface-callback` @ `fc04147e` on `fork`.
**Baseline:** none needed. Round 2's failing captures are the baseline; nothing needs re-proving.

```bash
git fetch fork --prune
git checkout -B fix/822-stale-surface-callback fork/fix/822-stale-surface-callback
git log --oneline -8
# expect fc04147e at the top, then c37690aa, 1046a484, b7aa150d, 5bc77358, 153457cd, 83891214,
# and a8830caa (main) beneath
```

### R0 — build gate

`run_unit_tests.sh`, then `build_hur.sh`. Record the APK md5 and confirm it is live (§5).

- **`ProjectionWatchdogPolicyTest` must be present and green (4 tests)** — if the class is missing,
  the wrong commit is checked out.
- `DecoderStopPolicyTest` and `VideoRecoveryPolicyTest` still green; full suite green.

**If R0 fails, stop and report.**

---

## 2. What the branch does (what you are verifying)

Round 2's chain, and where each link is cut:

1. Launching `MainActivity` during a live session destroys the projection activity (singleTask
   semantics) and relaunches it. **Unchanged** — the relaunch worked all along (354 ms to first
   frame in your own capture); it was never the problem.
2. The old instance's late `onSurfaceDestroyed` used to release video focus and stop the decoder
   the new instance had just configured. **Now gated on ownership**: the decoder knows which
   `Surface` it renders to, and a teardown from any other surface is ignored with a log line. The
   old instance also deregisters its view callback in `onDestroy` and no longer strips the new
   instance's decoder listeners.
3. Nothing re-requested video mid-session. **The reconnecting watchdog now survives the whole
   session** (it used to die on its first tick because it checked for a state the session had
   already left) and re-requests video focus, throttled, whenever the connection is live but no
   frame has arrived for >10 s.

Plus two decoder-side items: the `restart suppressed (N/4 used)` line that misled round 2 no longer
prints for an idle stream, and the stop-vs-feed-thread race is closed (the join now reliably wins
before the codec is released; the feed thread is published before it starts; a codec that fails to
start is released rather than leaked).

## 3. What is different about this round

- **Media playing for every run, as round 2 wanted** — if the rig grants an audio channel
  (`AapAudio: AA audio started (` in the log). Round 2 could not get one in three attempts; if it
  refuses again, run video-only and mark the audio column "not confirmed active", same as round 2.
  Do not burn more than two attempts on it.
- **The trigger is round 2's M-b, verbatim.** Home presses are known inert here and appear only in
  R4 as part of the ordering variant.
- Settings and log plumbing identical to round 2: `log-level=2`, `log-source=1`,
  `log-capture-enabled=true`, `view-mode` per run, `video-codec=Auto` throughout. Keep the logcat
  capture running for the framework stream and check it is still advancing at the start of every
  run.

## 4. The lines that decide every run

All verified with `grep -F` against `fc04147e`, except the two marked *(composed)*, which are
prefix + reason joined at runtime — both halves verified, and round 2's captures show the composed
form.

| Meaning | Line |
|---|---|
| the new instance claimed the decoder *(composed)* | `Decoder stopped: New surface` |
| **the fix, working** — stale teardown ignored | `SurfaceCallback: onSurfaceDestroyed for a stale surface - ignoring.` |
| the decoder-side gate declining a stale stop | `skipped: surface is no longer current` |
| the relaunch branch fired | `MainActivity: Active session detected` |
| picture is back | `First frame rendered (hardware decode)` |
| **must NOT appear after the trigger** | `Media Sink Stop Request: VIDEO` |
| watchdog alive mid-session | `Showing reconnecting overlay` |
| watchdog re-requesting video | `AapProjectionActivity: connected but no frames - requesting video focus (unsolicited)` |
| **must NOT appear on an idle stream** | `restart suppressed (` |
| audio channel opened | `AapAudio: AA audio started (` |
| audio stopped — absence is the pass | `last AA audio channel stopped` |
| sustained rate | `Throughput over ` |

## 5. Runs

Every run: candidate APK confirmed live by md5, session projecting with `fake_speed` on.

```bash
PKG=com.andrerinas.headunitrevived
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
TRIGGER='adb shell am start -n '"$MAIN"
```

### R1 — the round 2 reproduction, on the fix — **the point of the round**

`view-mode=1` (TEXTURE). Session up and rendering. Fire the trigger once. Observe **90 s**.

**PASS, all of:**
- the stale-ignoring line appears (or the surface ordering made it unnecessary — see R4);
- **no** `Media Sink Stop Request: VIDEO` anywhere after the trigger;
- `First frame rendered` within ~2 s of the trigger, and `Throughput over` shows a healthy
  `rendered` rate through the full 90 s — the picture *stays*.

**FAIL:** black screen, a video sink stop from the phone, or a rendered rate that collapses and
does not recover. Keep the full capture.

### R2 — rapid repeat

Same settings. Fire the trigger **8 times, ~5 s apart**. Observe 60 s after the last.

**PASS:** picture up and stable at the end; no `Media Sink Stop Request: VIDEO`; no
`Error feeding input buffer` following any `Decoder stopped:` line (this is the race-hardening
check — the alternating `setSurface`/`stop` under load is exactly its window).

### R3 — the other two backends

One trigger + 60 s soak each, on `view-mode=0` (SURFACE — exercises the view's own converted
stops) and `view-mode=2` (GLES — the 250–650 ms posted-destroy path, the worst case in round 2).

**PASS per backend:** same criteria as R1.

### R4 — ordering variant: background first, then relaunch by icon

`view-mode=1`. `adb shell input keyevent KEYCODE_HOME`, wait 10 s, then fire the trigger.

Here the surface teardown (if the launcher delivers one — on this rig Home leaves the surface
alive, so likely not) precedes the relaunch, and the ownership gate *should* let a legitimate
teardown act. **PASS:** picture returns within ~5 s of the trigger and stays for 60 s. The route
does not matter — `gain=true` from the new surface, or the watchdog — only that it returns.

### R5 — watchdog proof

`view-mode=1`, session rendering. Pause the video source on the phone side for >15 s (screen-off on
the phone, or pause navigation/map so AA sends nothing — round 2's observed `Media Sink Stop` from
a *phone-side* stop also qualifies if one can be provoked; say which was used).

**PASS:** `Showing reconnecting overlay` appears mid-session (impossible before this branch), the
`connected but no frames - requesting video focus` line appears with ~1 s+ spacing (throttled, not
every 2 s tick), and the overlay clears when frames resume.

**INCONCLUSIVE** if the rig cannot produce a >10 s mid-session video gap; say what was tried.

### R6 — regression guard: legitimate background, and the link-stall catch-up

Two halves, both protecting behaviour that must not have changed:

- **Background/foreground:** three Home-press cycles (3 s / 30 s / 90 s holds), `view-mode=1`.
  **PASS:** behaviour identical to round 1's baseline — no teardown on this rig, throughput
  uninterrupted, and (if audio was confirmed active) no `last AA audio channel stopped`.
- **Link-stall catch-up:** provoke a short wireless stall (round 2's technique: brief RF
  obstruction / walk the phone away, or accept one that occurs naturally during the round).
  **PASS:** the following `Throughput over` lines show `skipped` non-zero during the burst and
  `rendered` tracking `fed` once healthy. This is the tripwire for the feed-thread commit — a
  regression here outranks every pass above.

### R7 — idle-stream log hygiene

Leave the session idle (no `fake_speed`, static AA screen, no touches) for 3 minutes.

**PASS:** zero `restart suppressed (` lines in the window. (Round 2 got one every ~10 s.)

---

## 6. Do not re-run

- Round 2's failing baseline — it is the baseline.
- Home-press holds beyond R6's three — settled in round 1.
- The 3.2.3 hang — nothing in this branch targets 3.2.3; its record stands.

## 7. Report back

1. **R1 in one sentence: does the round 2 reproduction survive the fix?**
2. The R2/R3 table — per backend and per repeat: stale-ignoring line seen? sink stop seen? picture
   stable at the end?
3. R5: did the overlay and the re-request appear mid-session, with the throttle visible in the
   timestamps?
4. **R6 both halves** — any deviation from round 1's background baseline, and the catch-up
   `skipped`/`rendered`/`fed` numbers.
5. R7: count of `restart suppressed (` lines.
6. Audio: confirmed active or not, and whether it survived every trigger.

Anything noticed in passing, as always — that section found round 1's headline.
