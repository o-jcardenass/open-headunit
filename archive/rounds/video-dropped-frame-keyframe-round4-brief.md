# Dropped-frame keyframe recovery — round 4 brief

## 1. Build and baseline

**Candidate:** `test/830-keyframe-lever-probe` @ **`1dc7e6ec`** (on the fork).

```bash
git fetch fork test/830-keyframe-lever-probe
git checkout 1dc7e6ec
```

One commit, stacked on round 2's candidate `ec0a2d28`, which is itself on `563ae013` →
`fix/warm-relaunch-keyframe` @ `a8e21850` → upstream `main` @ `a8830caa`. No history rewrite; a
fast-forward works.

**TEST ONLY — this branch must never merge**, same standing as round 3's
`test/830-lowered-escalation-threshold`. It adds a broadcast-triggered probe and one observation-only
log line. Nothing in it changes what the app does on its own.

**No baseline APK.** Every measurement here is a property of the *phone* — whether it answers a
given message with a keyframe — and the control is the phone's own keyframe cadence measured in the
same capture.

## 2. What this is and why it exists

Rounds 1 and 2 settled that the gain-only unsolicited `VideoFocusEvent` — the message the app sends
everywhere it wants a keyframe — **does nothing**. 419 nudges across two builds, median wait to the
next keyframe 30.4 s and 27.9 s, against natural cadences of 7.5–70.1 s and 9.0–69.9 s. Both medians
land at half the natural interval, which is exactly the wait a *random* observer of a periodic
process gets. That is the arithmetic of no causal link, and it reproduced on two independent builds.

Round 3 then showed the one thing that does move the phone: a real focus release/regain cycle. The
phone answered with `Media Sink Stop Request: VIDEO` in 53 ms and rebuilt the stream on the regain
403 ms later, with an incremented `session=1`. It also survived cleanly — no #755 freeze.

That leaves #830 in an awkward place. The escalation exists and works, but its shipping threshold
(150 s of unbroken drops) can never fire for the reported fault: the reporter's "fixes after a
minute" matches **one** natural keyframe interval, i.e. a single lost frame — an episode of length
zero. Making the escalation reach that case means lowering the threshold until a **focus release
fires in ordinary use, on hardware nobody has tested**. That release is precisely #755's
precondition, and the entire safety evidence for it is one cycle, on this rig, with this phone.

**So this round asks whether something cheaper works before we take that bet.** AAP has no
keyframe-request message at all — `media.proto`'s `MsgType` has no such member — which is why a focus
transition is the only known lever. But the nudge is inert specifically because a focus notification
that does not *change state* is a no-op to the phone. Three candidates change state for less than a
teardown:

| Lever | What it sends | If it works |
|---|---|---|
| **L1** | `UpdateUiConfigRequest` carrying the margins already in force | recovery costs nothing at all — no state change, no session risk |
| **L2** | the same, bottom margin toggled by one pixel, so each send is a genuine change | recovery costs one pixel of layout |
| **L3** | focus release as `VIDEO_FOCUS_NATIVE_TRANSIENT`, then the regain | a real transition, but "transient" should tell the phone to hold the session |
| **L4** | focus release as `VIDEO_FOCUS_NATIVE`, then the regain — **the control** | already known to work (round 3); the #755 lever |

If L1, L2 or L3 elicits a keyframe, the escalation keeps its gates and swaps its payload, and the
#755 exposure disappears. If none does, we ship the L4 cycle knowing we checked.

`VIDEO_FOCUS_NATIVE_TRANSIENT` is declared in `media.proto` and used nowhere in the app, so nobody
has ever seen how this phone answers it.

## 3. What is different about this round

- **This round runs on the *healthy* hardware-decoding path, not the software one.** Rounds 1 and 2
  both measured `dropped=0` over five minutes there. That is the point: with no drops, nothing else
  in the app requests a keyframe, so the probe is the only candidate cause in the capture. Do **not**
  set `force-software-decoding` this round.
- **The instrument changed, and R1 exists to check it before it is trusted.** Rounds 1–3 had to infer
  keyframes from assembled frame size; round 2 found the clean size gap round 1 relied on is not
  always there. `1dc7e6ec` adds `VideoKeyframeScanner`, which reads the NAL type against the codec
  the decoder actually pinned and logs `VideoDecoder: keyframe reached the codec`. R1 cross-checks
  that line against the old size method on an ordinary session. **If they disagree, stop and report
  it** — everything after depends on the new line being right.
- **The probe is fired by broadcast, never by launching anything.** Starting an activity would
  destroy and rebuild the projection surface, which drags the whole warm-relaunch escalation into the
  capture and invalidates the round. Do not press Home, do not relaunch, do not change view mode.
- **L3 and L4 deliberately disturb the session** — that is what they are. Expect a codec restart and
  a fresh `Media Start Request VIDEO` after each. Expect the picture to blink. That is not a fault.
- **The stream must be moving** for the whole capture, same as rounds 1–3: navigation animating, or
  phone media with a moving UI. A parked map drops Android Auto to a few fps by itself and is the
  most misread signal in this tracker. Say in Setup notes what was on screen.
- **`log-level=0` (VERBOSE) throughout**, because R1's cross-check needs `RECV: VIDEO`. Capture to a
  **file** via `stdbuf -oL` as always — the flood costs disk and grep time, not evidence.

## 4. Settings keys this round needs

Use `set_hu_prefs.sh` (§5 of the template).

| Key | Type | Element |
|---|---|---|
| `log-level` | int | `<int name="log-level" value="0" />` |
| `video-codec` | string | `<string name="video-codec">H.264</string>` |

**Delete `force-software-decoding` and `software-video-decoder`** if rounds 1–3 left them set —
delete the keys rather than writing `false`/`1`. Leave `view-mode` at 1 (TEXTURE), which never
destroys its surface on this rig.

`video-codec=H.264` is pinned so the scanner's H.264 branch is the one under test and the capture is
comparable with rounds 1–3.

## 5. Firing a lever

```bash
probe() {
  adb shell am broadcast \
    -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.app.KeyframeLeverProbeReceiver \
    -a com.andrerinas.headunitrevived.action.KEYFRAME_LEVER_PROBE \
    --es lever "$1" > /dev/null
}
```

The explicit `-n` matters: an implicit broadcast to a manifest-registered receiver is unreliable on
modern Android, and this rig is Android 14. `am broadcast` should print `result=0`; if it reports the
receiver was not found, the APK on the device is not this build — check the md5 before going further.

## 6. The lines that decide every run

All verified with `grep -F` against `1dc7e6ec`.

| Line | Level | Means |
|---|---|---|
| `KeyframeLeverProbe: [L1] UpdateUiConfigRequest, margins unchanged` | W | **T0 for L1** |
| `KeyframeLeverProbe: [L2] UpdateUiConfigRequest, margins changed` | W | **T0 for L2** |
| `KeyframeLeverProbe: [L3] releasing video focus as NATIVE_TRANSIENT` | W | **T0 for L3** |
| `KeyframeLeverProbe: [L4] releasing video focus as NATIVE` | W | **T0 for L4** |
| `KeyframeLeverProbe: [L3] retaking video focus` / `[L4] retaking` | W | the regain, 400 ms later |
| `VideoDecoder: keyframe reached the codec` | I | **the new instrument** — a keyframe was queued into MediaCodec |
| `Media Sink Stop Request: VIDEO` | I | the phone tore its video sink down (expect after L3/L4) |
| `Media Start Request VIDEO` | I | the phone rebuilt the stream — note the `session=` number |
| `Codec initialized: ` | I | which component; expect `c2.unisoc.avc.decoder` |
| `Throughput over ` | I | `rendered=`, `fed=`, `dropped=`, `skipped=` |
| `AapTransport: Requesting recovery keyframe (unsolicited focus gain).` | W | the inert nudge — should be **absent** this round; if it appears, something dropped a frame and the run has a confounder |
| `RECV: VIDEO` | **VERBOSE only** | per-message `flags:` and `size:` — R1's cross-check |

## 7. Runs

### R0 — build and unit-test gate

Build with `build_hur.sh`, then `run_unit_tests.sh`.

- **PASS:** both succeed. Expect **284** tests — round 2/3's 272 plus **12** in the new
  `VideoKeyframeScannerTest`. Record the APK md5, copy it out of `apks/` immediately, install with
  `adb install -r`, and confirm the live-APK md5 before any run.
- **FAIL:** stops the round. Quote the compiler output verbatim and stop. This is the first compile
  of `1dc7e6ec`.

### R1 — is the new keyframe line trustworthy? (gate for R2)

Ordinary session, settings from §4, **no probes fired**. Run **3 minutes** undisturbed with the
screen moving.

Then compare the two instruments over the same window:

1. Every `VideoDecoder: keyframe reached the codec` timestamp.
2. The assembled-frame method from round 1's brief §6 — the `RECV: VIDEO` awk recipe, then the
   frames far above the median size.

- **PASS:** the two agree on how many keyframes there were and when, within a few tens of
  milliseconds. Report both counts, the size of each frame the scanner flagged, and the median frame
  size for context. The scanner's own log line carries the byte count, so this is a direct
  comparison.
- **FAIL:** they disagree — different counts, or scanner lines with no large frame near them, or
  large frames the scanner never flagged. **Stop the round and report it.** A broken instrument makes
  R2 unreadable, and the disagreement itself is the finding. Quote a handful of both kinds.

Also record: `Codec initialized:`, and the total `dropped=` (expect 0, matching rounds 1 and 2).

### R2 — which lever produces a keyframe? (the point of the round)

**Gated on R1 = PASS.**

One continuous capture, roughly 13 minutes. Start it, bring the session up, let it settle, then run
this schedule. Keep the screen moving throughout and do not touch either device except to fire
probes.

| Phase | Duration | What |
|---|---|---|
| Quiet | 2 min | no probes — this is the natural-cadence control |
| L1 | 8 × 25 s | `probe L1` |
| L2 | 8 × 25 s | `probe L2` |
| L3 | 5 × 40 s | `probe L3` — longer spacing, it disturbs the session |
| L4 | 2 × 60 s | `probe L4` — the control, fired sparingly on purpose |
| Quiet | 2 min | no probes — second cadence control, after the session has been disturbed |

```bash
for l in L1 L1 L1 L1 L1 L1 L1 L1; do probe $l; sleep 25; done
for l in L2 L2 L2 L2 L2 L2 L2 L2; do probe $l; sleep 25; done
for l in L3 L3 L3 L3 L3;          do probe $l; sleep 40; done
for l in L4 L4;                   do probe $l; sleep 60; done
```

**For each lever, report a table of every (T0, Δ) pair**, where Δ is the delay from the trigger line
to the next `VideoDecoder: keyframe reached the codec`. Then, per lever: n, median Δ, min, max, and
how many were under 1 s.

**The control** is the natural keyframe interval from the two quiet phases — the gaps between
consecutive `keyframe reached the codec` lines with no probe anywhere near them. Report that
distribution separately.

Read each lever against the control, not against intuition:

- **The lever works:** Δ collapses — consistently well under a second, and nothing like the control.
- **The lever is inert:** Δ's median sits near *half* the control's median. That is the signature
  rounds 1 and 2 found, and it means the keyframes following the lever were the ones already coming.

Report it as tables, not as a verdict adjective. Fewer than five samples for a lever is not a
conclusion — say how many there were.

**Also record, per lever:**

- Whether `Media Sink Stop Request: VIDEO` and `Media Start Request VIDEO` appear after it, and the
  `session=` number in the start request. **For L3 this is the whole question**: if NATIVE_TRANSIENT
  produces a keyframe *without* a sink stop, that is the best possible outcome of this round and
  should be called out at the top of the results.
- The `Throughput` lines spanning each trigger. **If `rendered` fps drops after any lever and does
  not recover, that is #755 happening and it outranks everything else here** — quote it, keep the
  full capture, and stop firing that lever.
- Whether the picture visibly blinked (§0 of the template: what was actually seen, not what should
  have happened). L3 and L4 are expected to; L1 and L2 are not.

## 8. Do not re-run

- **Whether the gain-only nudge works.** Rounds 1 and 2 answered it twice, on two builds, with
  matching numbers. It is inert. Nothing in this round needs to re-establish that.
- **Whether the L4 cycle survives.** Round 3 measured it. L4 appears here only as a positive control
  for the measurement, not to re-litigate its safety.
- **Anything about surface teardown, relaunch or warm relaunch.** Round 8 of
  `video-black-after-background` closed that out, and this round never disturbs the surface itself.
- **Provoking drops with forced software decoding.** Rounds 1–3 did that three times; this round
  deliberately wants the clean path.

## 9. Report back

Two things decide the shape of the #830 fix:

1. **Whether any of L1, L2, L3 collapses Δ the way L4 should.** A yes on L1 or L2 means recovery can
   be made free and the #755 question never has to be answered. A yes on L3 means it can be made
   cheap. A no on all three means the fix ships on the L4 cycle, with a once-per-session cap, and we
   will have checked rather than assumed.
2. **R1's verdict on `VideoKeyframeScanner`.** That file is written to ship — if the round confirms a
   lever, it gets cherry-picked onto the fix branch as the evidence source for a
   request-until-answered latch. A hardware disagreement in R1 is a defect in code that is otherwise
   on its way into a PR, so it matters well beyond this round.

Worth a line each in "Anything the brief did not ask about": whether `AapTransport: Requesting
recovery keyframe` appeared at all (it should not — its presence means something shed a frame and the
capture has a confounder), and whether the phone ever answered `UpdateUiConfigRequest` with anything
at all, keyframe or not.
