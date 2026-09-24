# video-black-after-background — round 2 results

**Candidate A:** `v.3.2.4` @ `c9556803bbf90f495f5be1a360c7698b59ac8637`
**Candidate B (baseline):** `v.3.2.3` @ `e900de7832178e91558749bc6c781fa689130661`
**APK md5:** A `5a19bdb1696d95ba2bc224de853e29da` / B `6489a53c822676c217b6a6adf0a1da70` (both survived
from round 1, no rebuild)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, Native AA wireless (mode 3) only
**Date:** 2026-08-12

**Headline: the gate opened, the bug reproduced live, and the reporter's own workaround was
confirmed to fix it — on both builds, by two different failure mechanisms.**

## Setup notes

- **R0 was a pure gate check.** APK A's md5 confirmed live before any test; no rebuild, no unit
  tests re-run (round 1 already has 244/244 on this SHA).
- **Audio could not be confirmed active this session, despite three separate attempts using the
  documented-working recipe** (force-stop + relaunch Spotify + `input keyevent 126` on the head
  unit; a phone-side `cmd media_session dispatch play`; and an explicit pause→play state
  transition on the phone), each followed by 15–90 s waits. `Media Sink Setup Request: 1 on
  channel AUDIO2` fired once at session connect and nothing else audio-related followed in any of
  it — no `AapAudio:` line, no `Media Start Request AUDIO2`. This matches the rig's documented
  A2DP/audio unpredictability (`TESTING-TEMPLATE.md` §7a: "won't come up at all, for a while, for
  no visible reason"). **Every run below is video-only**; §9 item 3 (did audio survive) cannot be
  answered as posed this round — there was no confirmed audio channel to lose.
- **`am start -n $MAIN` and `am start -n $ACT` routinely return
  `Warning: Activity not started, ...`** rather than a clean start, because the app's task already
  has an instance on top. This is expected given the task's launch modes and does not indicate the
  command failed — the effect (or non-effect) is read from the app's own log, not from `adb`'s
  return message.
- **M-b's actual mechanism differs from what the brief anticipated.** It doesn't background the
  app for a hold duration at all — `MainActivity.onResume` contains an "Active session detected,
  bringing projection to front" branch that **immediately** (within ~250 ms) recreates
  `AapProjectionActivity` on top of itself when triggered while a session is live. Because of this,
  R2's three planned hold lengths (10 s / 30 s / 30 s) collapse to one meaningful cycle — the
  break isn't hold-dependent, so a second and third cycle would only repeat the first's evidence
  verbatim. Per the brief's own stop-at-first-failure rule (§7, R2), one full cycle plus its R6
  latch-probe follow-up is reported as R2's answer, run twice independently (once during R1, once
  formally for R6) with identical outcomes both times.
- **The user watching the rig confirmed the black screen visually** at the point the log already
  predicted it (~10:44, mid-way through R1's first M-b trial) — this is the first round on this
  investigation with a directly observed, not just log-inferred, reproduction.
- No new script needed. `set_hu_prefs.sh` for settings, plain `adb shell settings put global
  always_finish_activities` for M-c, inline `sleep`/`grep` loops for everything else.
- `always_finish_activities` confirmed `0` both before M-c and after, per §3's requirement.
- Settings restored to the pre-round-2 backup (byte-identical, confirmed via `diff`) at the end;
  build A (debug, v.3.2.4) left installed.
- The full-round `adb logcat` capture (263 MB) is retained at
  `hur-wifi-test-scripts/results/round2-video-black/round2_logcat.txt` and was checked directly
  for the R3 hang (see below) — no `ACodec`/`OMX` errors anywhere in the affected window.

## R1 — method probe — the point of the round

**PASS: M-b opens the gate.**

**Step 0 (free, no rig time):** `AapProjectionActivity: onPause` **is present** in round 1's own
saved captures — 6 occurrences in `HUR_Log_20260812_092820_843.txt`, one per Home-press cycle,
each followed by `onNewIntent`/`onResume` at the expected relaunch time with no unrequested
self-relaunch in between. This settles round 1's open question: the Home press **did** background
the activity at the Android lifecycle level every time; the surface simply survived anyway. Round
1's finding was hypothesis 2 (paused but stayed visible), not hypothesis 1 (never backgrounded).

| Method | `onPause` fired | Gate line fired | Unrequested `onResume` (confound) | `dumpsys activity` | Audio |
|---|---|---|---|---|---|
| **M-a** (opaque Settings app) | Yes, `10:41:03.048` | **No** | No | `com.android.settings/.homepage.SettingsHomepageActivity` on top at +3s | not confirmed active (see Setup notes) |
| **M-b** (own `MainActivity`, same task) | Yes, `10:42:50.214` | **Yes** — `Decoder stopped: surfaceDestroyed` at `10:42:51.321` | No — the "resume" is the recreate cascade itself, not a spurious extra one | New `AapProjectionActivity` instance created and torn down within ~1s of the trigger | not confirmed active |
| **M-c** (`always_finish_activities=1` + Home) | Yes, `10:48:21.736` | **No** | No | throughput uninterrupted at ~49fps through the full 20s hold | not confirmed active |
| **M-d** (plain Home, negative control) | Yes, `10:49:13.074` | **No** | No | matches round 1 exactly | not confirmed active |

**M-win = M-b.** Only method that opens the gate; M-a (closest to the reporter's own words) does
not, so no preference tie-break was needed.

### What M-b actually does, in detail

Triggering it (`adb shell am start -n $MAIN` while `AapProjectionActivity` is resumed and a
session is live) fires `MainActivity.onResume`'s "Active session detected, bringing projection to
front" branch, which recreates `AapProjectionActivity`:

```
onPause (old instance) → MainActivity.onResume "Active session detected..." → onCreate (new
instance) → New surface set → VideoDecoder.stop("New surface") → reconfigure → [briefly renders
again in the repeat trial, see R2] → old instance's onDestroy(isFinishing=true), ~250-650ms later
→ onSurfaceDestroyed on the OLD (now-stale) surface → VideoDecoder.stop("surfaceDestroyed") →
decoder reconfigures itself a second time → phone reports Media Sink Stop Request: VIDEO → stall
watchdog fires every ~10s, every time reporting "restart suppressed" → stuck.
```

The critical defect: the **old** activity instance's delayed `onSurfaceDestroyed` callback fires
*after* the **new** instance has already reconfigured the (singleton) decoder for a *different*
surface, and unconditionally calls `VideoDecoder.stop()` again — tearing down a decoder that was
just working, tied to a surface the callback doesn't check is still current. This matches round 1
M1's structural class of bug (a stale lifecycle callback with no ownership check) but is a
distinct trigger from anything round 1 tested.

**The picture went black and stayed black.** Visually confirmed by the user at ~10:44, well into
a black-screen window that started at `10:42:51.321` and was still black when force-stopped at
`10:47:25` — **over 4 minutes**, with the stall watchdog firing roughly every 10s the entire time
and reporting `restart suppressed (0/4 used, 8000ms cooldown)` on every single check, never once
actually restarting. `Watchdog: No video received yet` (the session-start keyframe-request line)
fired exactly once per process, right at initial connect, and never again during any mid-session
black screen in any run this round — confirming round 1's own prediction that nothing watches for
a video stoppage once the session is already established, only for one that never starts.

**Force-stop + relaunch (the reporter's own stated workaround) restored the picture in ~5s.**
Tested twice (once per M-b trial), both times conclusive.

## R2 — graduated holds, 3.2.4, TEXTURE, using M-win

**FAIL.** (Per Setup notes: M-b's break is immediate and hold-independent, so the brief's
stop-at-first-failure rule applies to the first cycle and no further holds were run.)

- Settings: `view-mode=1`, `video-codec=Auto`, build A.
- Cycle (formal repeat, run immediately before R6 with a fresh trigger): `New surface set:` at
  `10:46:21.094`, `First frame rendered` at `10:46:21.448` — **354 ms**, genuinely recovered
  briefly — then torn down again by the stale-callback bug at `10:46:21.722`, ~274 ms later.
  Never recovered again on its own; confirmed stuck at `rendered=0fps` through the full
  observation window before force-stop.
- Recovery time to a **lasting** picture: N/A by self-recovery (never happened); via force-stop +
  relaunch, ~9s from `am start` to `First frame rendered` (`10:47:27` → `10:47:35.992`).
- Audio: not confirmed active before or during the run (Setup notes).

## R3 — the same on 3.2.3, using M-win — the A/B, and the round's second finding

**FAIL, but not the way R2 failed.**

Build B installed (`adb install -r -d`, downgrade — `TESTING-TEMPLATE.md` §7a), md5 confirmed,
settings carried over unchanged. Session established (`First frame rendered` at `10:50:55.880`),
then M-b triggered:

```
10:51:17.444  AapProjectionActivity: onPause
10:51:17.525  MainActivity: Active session detected, bringing projection to front
10:51:17.761  AapProjectionActivity.onCreate (new instance)
10:51:17.914  New surface set
10:51:17.931  Decoder stopped: New surface
10:51:17.991  Configuring decoder: c2.unisoc.avc.decoder for 1920x1080
10:51:18.015  Codec initialized: c2.unisoc.avc.decoder
10:51:18.018  Output thread started
10:51:18.121–.133  HeadUnitScreenConfig.init / .recalculate (four lines, then cuts off mid-word)
                    ↑ the app's own log stops here, permanently
```

**The app's log file stopped growing entirely** — `wc -l` returned the identical 285 lines and
`wc -c` the identical 40960 bytes across three separate checks spanning over two minutes, with the
very last line cut off mid-word (`...HeadUnitScreenConfig.recalculat`). No further `onDestroy` for
the old activity instance, no `Feed thread started`, no `First frame rendered` for the new
instance, nothing — a level of silence round 1 and R2 never showed (R2's log kept producing
throughput/stall lines every 5-10s throughout its own failure).

**This was checked, not assumed, before calling it a hang:**

- The process was alive throughout (`pidof` returned a stable pid), state `S` (sleeping, not
  `D`/uninterruptible or `Z`/zombie), ~10% CPU — not spinning, not dead.
- `adb shell input keyevent KEYCODE_HOME` **worked** (the launcher came to foreground normally),
  so the OS-level window/activity manager was not itself frozen.
- Relaunching (`am start -n $ACT`) after backgrounding did **not** unstick it — the log stayed at
  the identical 285 lines/40960 bytes afterward too.
- **Checked the framework logcat capture for the same window: the native codec opened cleanly.**
  `C2UnisocAvcDec: openDecoder, lib: libomx_avcdec_hw_sprd.so` and
  `MediaCodec: [c2.unisoc.avc.decoder] setting surface generation to 7775235` both appear at
  `10:51:18.6xx` with **zero `ACodec`/`OMX` errors anywhere in the window** — ruling out a wedged
  native/vendor component. The freeze is on the app's own side, after the native codec had
  already succeeded, which points at an app-level deadlock (plausibly the same stale-callback
  race identified in R2, but landing differently — as a lock contention rather than a clean
  double-stop) rather than a driver problem.

**Force-stop + relaunch recovered it too** — `First frame rendered` at `10:54:31.659`, ~8s after
the relaunch command (`10:54:23`).

**R2 did not fail where R3 passed, and R2/R3 did not fail identically either.** Both builds break
under the identical trigger, but 3.2.4 breaks *cleanly* (a well-logged stop/reconfigure/stop
sequence ending in a stall watchdog that perpetually declines to restart) while 3.2.3 appears to
*hang* the app's own main-thread activity outright (no further app-level logging of any kind,
though the OS and the native codec layer stay healthy). This is a third outcome the brief's binary
framework didn't anticipate. It argues that the root cause — `MainActivity`'s unsafe recreation of
a live `AapProjectionActivity`, and the stale surface-destroyed callback that follows it — **predates
`9f98afd1`** and is not primarily a video-decoder-threading question; `9f98afd1` may have changed
*how* the failure manifests (a recoverable-looking stuck state vs. a genuine hang) without being
the trigger itself. Confirming that the pre-9f98afd1 hang and the post-9f98afd1 stall-suppression
share the exact same root line of code would need reading `MainActivity`'s "Active session
detected" branch and `AapProjectionActivity`'s surface-callback ownership handling directly, not
further hardware rounds.

## R4 — GLES, 3.2.4, using M-win

**FAIL, identical signature to R2.**

Build A, `view-mode=2`. Triggered M-b:

```
10:55:59.035  onPause
10:55:59.517  New surface set
10:55:59.556  Decoder stopped: New surface
10:56:00.108  old instance onDestroy(isFinishing=true)
10:56:00.220  Decoder stopped: surfaceDestroyed
10:56:00.739  Media Sink Stop Request: VIDEO
10:56:02.294  Decoder stall detected ... restart suppressed (0/4 used, 8000ms cooldown)
              [repeats every ~10s, confirmed through 10:57:44 — ~105s of continuous 0fps]
```

Round 1 found GLES apparently immune (steady 50fps through a 120s Home hold) because Home never
tore the surface down at all. **Under a method that actually backgrounds the app, GLES fails
exactly like TEXTURE** — round 1's GLES result was an artifact of the reproduction method, not a
property of the view mode.

## R5 — codec-flip probe, 3.2.4, TEXTURE, using M-win

**PASS.**

`view-mode=1`, `video-codec=H.264`. `findBestCodec` confirmed `selected=c2.unisoc.avc.decoder`
(H.264) at session start, honored. Triggered M-b; observed 90s.

**No `Falling back to ` anywhere in the capture.** The stall watchdog fired every ~10s through the
full window (`10:58:13.767` through `10:59:44.082`, eight occurrences), always reporting the same
`restart suppressed (0/4 used, 8000ms cooldown)`, and the codec-flip logic — which lives *inside*
the restart path — never got a chance to run because the restart itself is what's perpetually
suppressed. This is consistent, not a contradiction of round 1: M1's specific flip-on-restart
mechanism isn't what this trigger exercises; it exercises the restart-suppression path instead.

## R6 — latch probe

Ran twice (naturally, once per M-b trial in R1 and again as R2's cycle) rather than as a separate
staged run, since R1's own first M-b trial already produced the black screen the brief's gate
requires.

- **Picture never returned on its own** — observed continuously for 4+ minutes in the first trial,
  ~50s+ in the R2/R6 repeat, both ending in `rendered=0fps` sustained.
- **`Giving up to avoid an infinite restart loop` never appeared** — M1's specific latch line is
  absent. The actual mechanism is the stall watchdog's own cooldown check reporting `suppressed`
  on every single tick rather than ever exhausting a budget and giving up explicitly.
- **Nothing wedged below the app** — confirmed via logcat for the R3 case (see above); no
  `ACodec`/`OMX`/`createByCodecName` errors in any window this round.
- **Force-stop + relaunch restores the picture — confirmed twice on 3.2.4 (~5-9s) and once on
  3.2.3 (~8s).** This matches the reporter's own stated workaround exactly.

## Anything the brief did not ask about

**The "restart suppressed (0/4 used, 8000ms cooldown)" line is not in either brief's own §6
table.** Round 1 and round 2's briefs both quote `Decoder restart requested: sync_stall` as the
watchdog's approach-run line; the actual current source emits a differently-worded,
differently-structured message (`Decoder stall detected (no output for Xms) but restart
suppressed (Y/4 used, Zms cooldown). Still spinning on output.`). Functionally it's the same
watchdog, but the exact string and its argument shape (`0/4 used` — a budget counter that appears
to never advance past zero across 8+ consecutive ticks) should be updated in the next brief that
references it, and is itself worth a closer read: either the cooldown timer isn't actually
expiring between checks, or the budget-increment step is being skipped entirely whenever the
suppression fires — either would explain why this never self-clears.

**3.2.3's hang is arguably the more serious finding of the two builds**, precisely because it is
*harder to notice*: 3.2.4's failure leaves throughput logging running (an operator or an
automated log-scraper watching for `Throughput over` lines would see `rendered=0fps` and could
alert on it), while 3.2.3's failure produces **no further application logging of any kind** —
indistinguishable from the app simply going idle unless someone is specifically watching for the
log file to stop growing, which is not a signal this round's brief, or any prior one on this
investigation, thought to watch for.
