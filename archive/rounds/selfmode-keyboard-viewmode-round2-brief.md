# selfmode-keyboard-viewmode — round 2 brief

**Type: fix verification.** Round 1 was logs only and handed back two measured defects. Both now
have code. This round decides whether the keyboard stays, whether the picture comes back, and
whether either change cost anything on the paths it was not aimed at.

## 1. Build and baseline

```
git fetch fork
git checkout fix/883-self-mode-call-raise
git reset --hard 6411eaef
```

**History on this branch was rewritten since round 1.** It was rebased onto `main` and two commits
were dropped, so `85eca1e4` — the tip round 1 tested — is no longer an ancestor. Do not fast-forward
from a local copy; `reset --hard` to the SHA above. Verify before building:

```
git log --oneline -5      # expect 6411eaef, c38ae725, 04dc6eab, 1fffad01, 47290f01
```

**No baseline APK is needed.** Round 1 measured the unfixed behaviour on this same unit, and the one
control this round needs is a settings-free path on the candidate itself (R2).

**Build gate:** `run_unit_tests.sh` must be green, including two new files,
`PictureCredibilityPolicyTest` and `VideoFocusReleasePolicyTest`. A red gate is an escalation, not a
run.

## 2. What this is and why it exists

**Problem 1 — the keyboard bounce (#882).** Round 1 R1 proved the mechanism is ours. On SurfaceView
the phone keyboard covering the projection makes the framework destroy our surface; we answered with
`VideoFocusEvent(gain=false)`; Android Auto read that as the projection being over and tore its
session down, taking the keyboard with it 28 ms later. TextureView and GLES never lose their surface
to a cover, never send the release, and kept the keyboard for 29.8 s and 17.4 s — which is the
control that identifies the release, not the cover, as the cause.

The fix withholds that one message for one cover shape: opaque, live session, arriving within 3 s of
a touch we forwarded, not finishing, not PiP, no focus cycle already in flight. Everything else
still sends it. It is withheld, never deferred — a release posted to fire later can land after a
keyframe cycle finished and strand the phone released with nothing pending to take focus back.

**The cost, deliberately accepted.** That release is what makes the phone re-run sink setup, worth
42-96 ms to a picture. Without it the return is carried by the existing warm-relaunch focus cycle,
which a previous thread measured at a flat 3.04-3.20 s. R3 is where that trade gets its number.

**Problem 2 — the live view-mode switch (round 1 R4, FAIL).** The rebuilt codec keeps its cached
parameter sets, starts on a P-frame, and this chipset emits gray output from it. `First frame
rendered` printed in ~150 ms and every recovery gate then read "there is a picture" and stood down,
so the screen stayed gray for 11.0 s, ≥32.4 s and 66.5 s across the three transitions. The gates now
ask whether a keyframe accounts for what is on screen, not merely whether a frame rendered.

**The two are coupled.** Problem 2's fix is what makes Problem 1's return work: the return path
rebuilds the codec the same way and renders the same gray P-frames, so without it the escalation
would stand down there too. R3 failing while R4 passes would mean the coupling is wrong.

## 3. What is different about this round

- **The trigger is still not scriptable.** Round 1 established that on AA 17.5 focusing a text field
  opens AA's *in-projection* keyboard and is fine on every backend; only an explicit tap on the
  **phone-keyboard key** (right of the `0` on the AA keyboard) launches `PhoneKeyboardActivity`.
  That tap is inside the projected video at coordinates that are not fixed, so R1, R3 and R6 need
  the operator to tap on cue. Everything else in the round is scriptable.
- **`log-level` must be `0` (Verbose).** The focus notification is logged by `AapTransport.send` at
  verbose. Every decisive condition in R1 and R2 is about whether that line is present, and at
  `log-level=2` it is absent either way — which would make R1 a false PASS.
- **`raise-projection-over-keyboard` no longer exists.** The keyboard-raise arm round 1 measured as
  ineffective was removed with the rebase. Delete the key from `settings.xml` if it is still there;
  a stale value is inert but will confuse the capture.
- **Re-check `view-mode` after installing.** An install runs onboarding, which re-picks the backend.
  Every run in this round except R4 depends on being on SurfaceView, so read the key back after
  install rather than assuming the write held.
- **Do not force-stop Gearhead** at any point. `external_keyboard_last_open_state` is a persisted
  Gearhead preference and the phone-key route depends on its current state; round 1 left it alone
  and this round must too, so the two are comparable.

## 4. Settings keys

```xml
<int name="log-level" value="0" />
<int name="view-mode" value="0" />
<boolean name="auto-start-self-mode" value="true" />
```

`view-mode`: `0` SURFACE, `1` TEXTURE, `2` GLES. R4 changes it live through Quick Settings, which is
the run's whole subject — do not pre-write it there.

## 5. The lines that decide every run

Verified with `grep -F` against `6411eaef`.

**New this round:**

```
AapProjectionActivity: the surface went away <N>ms after a touch - holding video focus so Android Auto keeps its keyboard up
AapProjectionActivity: frames are rendering but no keyframe has decoded since the codec started - counting this surface as having no picture
```

**Existing, and load-bearing here:**

```
SurfaceCallback: onSurfaceDestroyed.
AapTransport.send | VIDEO Video Focus Notification            <- verbose only
AapProjectionActivity: relaunched surface has no picture after <N>ms - cycling video focus
VideoDecoder: keyframe reached the codec (<N> bytes)
VideoDecoder: keyframe decoded - the picture is repaired
AapProjectionActivity.recreateProjectionView | Recreating projection view due to settings change
```

Note there are **two** different "cycling video focus" lines. Only the one prefixed
`AapProjectionActivity: relaunched surface has no picture after` belongs to this round; the one
prefixed `AapTransport: picture unrepaired for` is a different lever and its appearance is a finding
worth reporting, not a pass condition.

**From Gearhead, in logcat rather than the OPENHU tag:**

```
GH.PhoneKeyboard  Asked by projected IME to detach
GH.KeyboardBinderWrappr  setExternalKeyboardCallback called after being told to detach.
```

## 6. Runs

### R1 — SURFACE, phone-key keyboard: does it stay? **This is the point of the round.**

`view-mode=0`. Connect, let the session settle, open the AA keyboard, tap the phone-keyboard key.
Leave the keyboard up for at least 10 s without touching anything, then dismiss it.

**PASS** requires all four:
- `SurfaceCallback: onSurfaceDestroyed.` **is present**. This is the reachability guard: if the
  surface was never destroyed the run proves nothing, and the verdict is INCONCLUSIVE, not PASS.
- The `holding video focus` line is present, once.
- **No** `VIDEO Video Focus Notification` between that line and the keyboard being dismissed.
- **No** `Asked by projected IME to detach` in that window, and the keyboard is still on screen
  after 10 s.

**FAIL** is the keyboard vanishing, or the release going out anyway. Report the keyboard's measured
time on screen either way.

### R2 — SURFACE, Home press: the positive control

Same setup. Instead of the keyboard, press Home, wait 5 s, return to the projection.

This run is what proves the gate is narrow. If the change had accidentally suppressed the release in
general, R1 would pass for the wrong reason and only this run would show it.

**PASS** requires:
- `VIDEO Video Focus Notification` **is** sent right after `SurfaceCallback: onSurfaceDestroyed.`
- **No** `holding video focus` line.
- `onResume` → `First frame rendered` **under 500 ms**. Give the number.

### R3 — the return after the keyboard, measured

Continues from R1's dismiss. Measure `onResume` to a repaired picture.

**PASS**: `relaunched surface has no picture after ...ms - cycling video focus` appears, followed by
`keyframe reached the codec`, and `onResume` → `keyframe decoded - the picture is repaired` is
**under 4000 ms**. Expected 1.7-3.2 s.

Report the number even on a pass; it is the price being paid for the keyboard and the shipping
question depends on it. Above 4 s, or no cycle at all, is a FAIL and means the coupling with the
credible-picture change is not working.

### R4 — live Quick Settings view-mode switch, all three transitions

Round 1's failing run, repeated unchanged: start on SURFACE with a session rendering, switch to
TEXTURE, then GLES, then back to SURFACE, leaving each in place until the picture returns.

**PASS**: on every transition, `frames are rendering but no keyframe has decoded ...` appears
**once**, followed by the cycle and `keyframe reached the codec`, with the gray-to-picture time
**under 5000 ms**. Round 1 measured 11.0 s, ≥32.4 s and 66.5 s.

Give all three times as numbers. A transition that recovers without the new line appearing is worth
reporting separately — it means the picture was credible on its own and the fix was not needed there.

### R5 — steady-state regression guard

Ten minutes of ordinary projection on `view-mode=0`: connect, drive the map, play media, do not
cover the projection.

**PASS** requires both:
- **Zero** `frames are rendering but no keyframe has decoded` lines, and zero
  `relaunched surface has no picture` lines.
- Throughput **45-60 fps with `dropped=0`** across the whole run.

The second condition is not decoration. A zero count on a session that was not rendering would be a
green that proves nothing, so the two are reported together or the run is INCONCLUSIVE.

### R6 — a long hold

As R1, but leave the keyboard up for **two minutes** before dismissing, and type into the field
while it is there.

This answers a question the code cannot: whether the phone tolerates a long hold of video focus
while our decoder is stopped. The code says it should — the bytes are discarded on an invalid
surface and acks keep flowing independently — but that is inference.

**PASS**: the session is still live at dismiss, the picture returns as in R3, and there is no
`ByeBye`, no disconnect, and no unacked-window stall in the capture. Report anything the phone did
that R1's shorter hold did not produce.

## 7. Do not re-run

- The unfixed bounce on SurfaceView, and TextureView and GLES keeping the keyboard. Round 1 settled
  all three on this unit.
- Whether the keyboard-raise arm helps. It does not, that is why it is gone, and the setting it used
  no longer exists.
- The AA 17.5 trigger question. Round 1 established that field focus alone does not launch
  `PhoneKeyboardActivity` on this build.

## 8. Report back

Three numbers decide whether this ships:

1. **R1** — did the keyboard stay, and for how long.
2. **R3** — `onResume` to repaired picture, in ms. This is the cost of the fix.
3. **R2** — `onResume` to first frame, in ms. This is the proof the cost was not charged to
   everything else.

R4's three recovery times decide the second change on its own. R5 and R6 are guards: report them as
verdicts with their paired measurements, and put anything surprising in "Anything the brief did not
ask about" — that section produced the finding that redirected this whole thread last round.
