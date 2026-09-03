# Round 5 brief — black screen after backgrounding: the user route, all three backends

Round 4 verified the cascade fix under the scripted relaunch trigger, GLES and TEXTURE. This round
runs the same scenario the way a user actually produces it — put another app in front of the
projection for a while, then come back through the launcher icon — across all three view backends,
including the first dedicated SURFACE soak of this whole investigation.

It also exercises, for the first time on this rig, the **legitimate-background path**: a fullscreen
activity actually covering the projection, which (unlike Home — round 1 proved that tears down
nothing here) may genuinely destroy the surface while it still owns the decoder. The fix explicitly
claims that path is unchanged — focus release and decoder stop proceed as they always did, audio
continues. Nothing has ever measured that claim on this rig, and this round does.

## 1. Build

**History was rewritten since round 4.** The branch was compacted from 10 commits to 4; the tree is
byte-identical to round 4's candidate `75334e3c` (verified with an empty `git diff` before the
rewrite). Round 4's results therefore stand unchanged and nothing here re-verifies them — this
round is new coverage, not a re-run.

**Candidate:** `fix/822-stale-surface-callback` @ `1192daa5` on `fork`. **Baseline:** none (no A/B).

```bash
git fetch fork --prune
git checkout -B fix/822-stale-surface-callback fork/fix/822-stale-surface-callback
git log --oneline -4
# expect exactly: 1192daa5, d2cafa27, 5a87d90b, dc0ddc1c — anything else is the wrong build
```

### R0 — gate

`run_unit_tests.sh` then `build_hur.sh`; md5 recorded, APK copied out of `apks/` immediately,
`adb install -r`, confirmed live via `pm path` + `md5sum`. Expected counts identical to round 4:
full suite 252/252, `DecoderRestartPolicyTest` 4/4, `ProjectionWatchdogPolicyTest` 4/4,
`DecoderStopPolicyTest` 6/6. A different total is a stop-and-ask — the tree is supposed to be
identical to what round 4 built.

## 2. What is different about this round

- **The cover step is a real app, not Home.** `adb shell am start -a android.settings.SETTINGS`
  puts the system Settings app (guaranteed present, fullscreen) in front of the projection. Do not
  drive its UI; it is only a cover.
- **The return step is the launcher tap's analog, not `am start -n`:**
  `adb shell monkey -p com.andrerinas.headunitrevived -c android.intent.category.LAUNCHER 1`.
  It resolves to `MainActivity` (the manifest's only LAUNCHER activity) with the flags a real icon
  tap carries, and avoids the `Warning: Activity not started` ambiguity round 3 noted for
  `am start -n` on a live session.
- **Whether covering tears down the surface on this rig is unknown, and finding out is part of the
  round.** Record it per cycle from the teardown lines below, with device timestamps, so it can go
  into §7a either way.
- **`Media Sink Stop Request: VIDEO` changes meaning by phase.** Inside a hold that showed a real
  surface teardown, it is the legitimate-background path doing its job — we release focus, the
  phone stops the sink; expected, not a failure. After a return trigger it keeps its rounds 3–4
  meaning: FAIL. If one appears after a return but the picture still recovers in budget, report it
  as its own finding with the surrounding lines rather than silently passing the run.
- **The self-foreground quirk is in play.** `AapService.launchAapProjectionActivity()` relaunches
  the projection with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_REORDER_TO_FRONT` when the phone
  re-runs media-sink setup, so during a hold OHU may put itself back in front of Settings with no
  command from the rig. That ends the hold early: record the timestamp and the causal
  `ActivityTaskManager: START` line, treat the hold as its actual shorter length, and carry on —
  do not discard the cycle. Every such event is also a reportable finding in its own right: an app
  stealing foreground from what the user chose to look at is user-visible behaviour.
- **Forbidden-line refinement.** Round 4's `Falling back to ` also matches the pre-existing #650
  display-stall fallback (`Falling back to SurfaceView for this session`) and WifiDirectManager's
  5 GHz group fallback, either of which could legitimately appear in this round's captures. The
  codec-escalation forbidden line is now `times in a row without rendering a frame`, which matches
  both escalation forms in `VideoDecoder.kt` and nothing else in the app.
- **Runner scripts.** Reuse/adapt `hur-wifi-test-scripts/round4-video-black/`, but per round 4's
  own setup note the host-side polling verdicts are unreliable on this rig: every verdict comes
  from the capture's embedded device timestamps post-hoc, allowing for the ~1.5–2 s
  device-behind-host clock skew round 4 established.

## 3. Settings keys this round needs

| Key | Type | Value |
|---|---|---|
| `view-mode` | int | per run: `2` (R1, R4), `1` (R2), `0` (R3) |
| `enable-audio-sink` | boolean | `true`, whole round |
| `wifi-connection-mode` | int | `3` |

`log-level` and everything else: unchanged from round 4. Back up `settings.xml` before the first
write and restore it at the end, per the template.

## 4. The lines that decide every run

Verified with `grep -F` against `1192daa5`; composed lines marked.

| Meaning | Line |
|---|---|
| **forbidden anywhere** | `times in a row without rendering a frame` |
| **forbidden anywhere** | `Both codec types failed` |
| **forbidden anywhere** | `Giving up to avoid an infinite restart loop` |
| forbidden after a return trigger; expected inside a hold with a real teardown | `Media Sink Stop Request: VIDEO` |
| warm-up restart, counted per cycle | `Forcing restart (` |
| cover caused a real teardown — activity callback *(composed)* | `SurfaceCallback: onSurfaceDestroyed. Surface:` |
| cover caused a real teardown — decoder stop *(composed)* | `Decoder stopped: surfaceDestroyed` |
| SURFACE view left the window — the converted direct stop *(composed)* | `Decoder stopped: onDetachedFromWindow` |
| the stale-surface gate doing its job (count them) | `SurfaceCallback: onSurfaceDestroyed for a stale surface - ignoring.` |
| relaunch reclaiming the decoder *(composed)* | `Decoder stopped: New surface` |
| new surface claimed | `New surface set: ` |
| picture back | `First frame rendered (hardware decode)` |
| phone restarted the sink after return *(composed)* | `Media Start Request VIDEO: session=` |
| steady state | `Throughput over ` |
| record-if-seen: pre-existing #650 escalation, not forbidden | `Falling back to SurfaceView for this session` |

## 5. Runs

The common cycle, parameterised by HOLD:

```bash
PKG=com.andrerinas.headunitrevived
COVER='adb shell am start -a android.settings.SETTINGS'
RETURN="adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1"

# session up and rendering (Throughput line moving), then per cycle:
$COVER                # note host timestamp
sleep $HOLD
$RETURN               # note host timestamp
# picture back within 90 s, judged post-hoc from device timestamps; then soak 30 s
```

Between runs: `headunit://exit`, force-stop, write `view-mode` for the next run, relaunch, let the
session form and reach a moving `Throughput over ` line before the first cover.

### R1 — GLES (`view-mode=2`), holds 5 s / 45 s / 180 s / 5 s
### R2 — TEXTURE (`view-mode=1`), same holds

Round 4 measured TEXTURE's relaunch warm-up as the slowest of the backends — the 90 s picture
budget is per cycle; do not shorten it because GLES came back faster.

### R3 — SURFACE (`view-mode=0`), same holds

First dedicated SURFACE coverage. Additionally record whether
`Decoder stopped: onDetachedFromWindow` ever fires and whether any stop appears twice for one
teardown — the SURFACE backend keeps two gated safety-net stops that are expected to be idempotent
no-ops on the live path.

### R4 — rapid switching, GLES (`view-mode=2`): 5 cycles of cover → 3 s hold → return, back-to-back

The quick app-flip a user does at a junction. Probes the round 4 deviation case — a fresh surface
superseding a still-recovering codec — through the user route instead of the scripted trigger.

**R1–R3 are collectively the point of the round; R4 is the stress variant.**

**PASS (each run):** zero forbidden lines; every cycle ends with a stable picture within its 90 s
budget (`First frame rendered` or a `Throughput over ` line with rendered > 0 after the return) or
is cleanly absorbed by the next cycle round-4-style (no forbidden lines, next cycle renders); the
session is alive and rendering at run end.
**FAIL:** any forbidden line; `Media Sink Stop Request: VIDEO` after a return trigger; a black
picture persisting past budget with no recovery by run end. Keep the full capture.

Per cycle, record:

- did the cover cause a teardown, and which lines said so (device timestamps);
- `Forcing restart (` count;
- return trigger → picture time, as a number;
- any self-foreground event during the hold (timestamp + causal line);
- any stale-gate line.

## 6. Do not re-run

- Round 4's entire set — same tree, results stand: the scripted-trigger soaks (GLES ×10,
  TEXTURE ×5), the Home-press cycles, the cold-start ladder, idle hygiene, and the latch backstop
  (UNTESTABLE there; still resting on `DecoderRestartPolicyTest` / `DecoderStopPolicyTest`).
- Home alone without a return trigger — round 1 settled it: no teardown on this unit.
- Anything poke/handshake-related; nothing on this branch touches it.

## 7. Report back

1. One sentence per backend: does cover-and-return through the launcher survive, with zero
   escalations?
2. The new rig fact, stated cleanly for §7a: does a fullscreen activity covering the projection
   destroy the surface on this unit, and does the phone then stop the video sink? Lines and
   timestamps.
3. `Forcing restart (` counts per cycle, per backend.
4. Self-foreground events during holds: count, timestamps, what triggered each.
5. Return → picture times, all cycles, as numbers.
6. Anything in passing — rounds 1, 3 and 4 each found their headline there.
