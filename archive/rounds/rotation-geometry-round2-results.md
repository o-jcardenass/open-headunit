# rotation geometry: round 2 results

**Part A candidate:** `fix/rotation-keeps-negotiated-geometry` @ `65e91b568` &nbsp;&nbsp;**Part B candidate:**
`probe/geometry-renegotiation` @ `13d29c619` &nbsp;&nbsp;**Baseline:** none built (not asked for this round)
**APK md5:** Part A `45554c6fe5dce8209ed4c753c0a90a08` / Part B `b9cb99b3f8b60ebbb2d28e6644ecccfb`, distinct
**Unit:** D-POCO (Self Mode, all runs). D-HU not used, this round has no two-device arm per the brief.
**Date:** 2026-09-18

## Setup notes

**R0 build gate: PASS for both candidates**, exact match to the brief. Part A: 2176 tests green,
`ProjectionOrientationPolicyTest`=8, `SessionGeometryLockPolicyTest`=4. Part B: 2193 tests green,
`GeometryProbePolicyTest`=12, `MaxUnackedPolicyTest`=4, `ProjectionOrientationPolicyTest`=9.
`build_hur.sh` and `run_unit_tests.sh` used as-is, each candidate's APK copied out of `apks/`
immediately after building (per the known `build_hur.sh` delete-previous-APK quirk).

**Launching via a plain `am start` on D-POCO does not start Self Mode.** D-POCO's `wifi-connection-mode`
is not in `settings.xml` (default in force), and a plain `MainActivity` launch armed a Native AA host
instead, the app began poking bonded phones (D-MOTO, a third paired device) over HFP-AG/HSP-AG. First
A2r attempt was discarded for this (`a2r-DISCARDED-wrong-launch-mode.txt`). Fixed by launching with
`am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE` explicitly for every run this round,
which reliably enters Self Mode regardless of the persisted connection mode.

**D-POCO's rotation recipe uses `user_rotation=1` for landscape, `0` for portrait**, the reverse of
the brief's own table comment (`0 portrait, 1 landscape`) if read as a universal mapping; on this unit
`0` is portrait and `1` is landscape. First A2r landscape-start attempt used `user_rotation=0` and came
up in portrait (`Raw size: 1080x2400`), discarded
(`a2r-DISCARDED-wrong-start-orientation.txt`) and redone with `user_rotation=1`. This matches round 1's
own note that the mapping is unit-specific; round 1 did not need to state which way it went for this
unit because it drove A3 by hand. Every run below used `1`=landscape, `0`=portrait, confirmed against
`HeadUnitScreenConfig`'s own `Raw size:` line before proceeding, per round 1's standing warning that
`dumpsys display` is not a live check on this unit.

**Self Mode's developer head-unit server (`:5277`) was already up** at round start
(`/proc/net/tcp6` showed `:149D` listening), no operator toggle needed this round. D-POCO's screen was
at `NotificationShade` focus at round start (shade pulled down from earlier work); returned home with
`KEYCODE_HOME` before the first run.

**An after-screenshot taken too late misses live evidence.** B3r's and B4r's after-screenshots (taken
~20s after the crash, following the brief's own screenshot cadence) show D-POCO's home launcher, not
the crash: Android Auto's own error dialog had already auto-dismissed by the time the shot was taken.
B6r's after-screenshot, taken immediately after the observation window ended, caught it:
`evidence/rotation-geometry-round2/b6r-after.png` shows the phone-side
**"Android Auto encountered an error, Communication error 6 - The car's software may be out of date."**
dialog. This is the same underlying crash in all four cases (confirmed from the log, not from the
screenshot), noted so a reader of B3r/B4r's after-screenshots does not read "home launcher" as a
different, milder outcome than B6r's.

**A first visual read of B1r's after-screenshot was wrong and was corrected before the round
continued.** `b1r-after.png` was initially read as a genuine portrait reflow (clean map, no visible
distortion at a glance). Re-examined side by side with round 1's `a1-after.png`: both show the
identical left-edge clipping of the icon rail (a numeric readout and a circular icon both truncated at
the same x-position) and the same landscape-shaped rail overlaid on the portrait canvas. Round 1's
frame was frozen (`Frame: 6962ms` in the on-screen overlay, i.e. no new frame in ~7s); round 2's B1r
frame was live (`Frame: 103ms`) because the session did not stall, but the **layout** is the same
squeeze in both, not a reflow. Every "correct portrait layout" / "same squeeze" call below was made by
this same side-by-side check against `evidence/rotation-geometry-round1/a1-after.png`, not by a single
glance.

**Every setting was written with `hur-wifi-test-scripts/set_pref.sh` (single-key) for probe-mode-only
changes between Part B runs**, and a pushed multi-key script for the larger writes (A2r's initial set,
B1r's full set, B5r-a's four-key set). `settings-backup-dpoco.xml` taken at round start and restored
verbatim at the end; diff against it showed only round 1's leftover geometry-probe/rotation state, no
surprises.

**No two-phone/rig hazards this round**: D-POCO was the only unit exercised (Self Mode is single-device
by construction), so no Bluetooth/A2DP link state mattered. Rotation was via `adb shell settings put`
throughout, no physical handling needed.

## R0, build gate

**PASS.** See above.

## Part A

### A2r, the pin holds System Default and says so

**PASS.** Part A candidate, Self Mode, `screen-orientation=0`, session up in landscape
(`Raw size: 2400x1080`), screenshot, rotated to portrait via the recipe, waited 20s, screenshot,
rotated back to landscape, waited 10s.

1. **The projection never turned.** `Raw size:` stayed `2400x1080` through both rotation attempts;
   `evidence/rotation-geometry-round2/a2r-before.png` and `a2r-after.png` are the identical clean
   landscape layout (map + trip-time cards + Spotify widget), confirmed side by side.
2. **`Sticky Orientation` line present**, naming the negotiated orientation:
   `[UI_DEBUG] Sticky Orientation: orientation setting applied, forcing orientation to 0`, printed once
   at launch (`AapProjectionActivity.requestOrientation`). Matches round 1's own correction that this
   is the line to expect (not `"session active"`, which only prints when the pin has to change a
   value, it never did here).
3. **`DROPPING LOCK: 0`** across the whole run.
4. **Throughput held with zero dropped frames** through both rotation attempts: steady 27-30fps,
   `dropped=0, skipped=0, concealed=0` in every window before, during and after both rotations.

Round 1's A0, A3 and A5 were not repeated, per the brief.

## Part B

Every run below is Self Mode only on D-POCO, per the brief (D-HU cannot be rotated on the operator's
instruction and there is no two-device arm this round). **There is no PASS/FAIL in Part B**, every
outcome is a result.

### The headline finding, true for B2r, B3r, B4r and B6r

**The probe fires this round** (round 1's wiring gap is fixed, every mode reached
`[GEOMETRY_PROBE] mode=N firing for a portrait canvas`), and **every mode that puts a second message
on the video/UI-config channel crashes Android Auto's own `:projection` process outright**, within
tens to a few hundred milliseconds of the TX line:

| Run | Message | `CONNECTED->DISCONNECTING` after TX | `FATAL EXCEPTION` after TX |
|---|---|---|---|
| B2r | `Media.Config` STATUS_READY idx1 | +16ms | +33ms |
| B3r | `Media.Config` STATUS_WAIT→READY | +39ms (after WAIT) | +425ms (after WAIT), +24ms (after READY) |
| B4r | `Media.Config` idx1 + focus cycle | +9ms | +374ms |
| B6r | `UpdateUiConfigRequest` ui_theme | +63ms | +412ms |

The crash is Gearhead's own (`Process: com.google.android.projection.gearhead:projection`), signature
identical in all four: `E/AndroidRuntime: FATAL EXCEPTION: main`, `kkz: Car not connected during
display change`, caused by `roz: java.lang.IllegalStateException: OutOfCarLifecycle`, ending in
`GH.UncaughtICSEHandler: Exception handled, killing process.` In every case, Gearhead's own `:car`
process (`CAR.SERVICE`) logs `Car connection state changed: CONNECTED->DISCONNECTING` on its own
initiative in the same window, nothing in our own log shows us requesting a disconnect, and the
`:projection` process's display-change handling then finds the car already gone and crashes. Zero
`Throughput over` windows appear after any of these four probes fired (confirmed by count, not
absence-read-as-zero): the session ends before another 5s window can complete, which is why this reads
as an instant crash rather than round 1's gradual B3/B6 stall. Gearhead's `:projection` process
auto-restarts with a new pid within seconds (confirmed via `ps -A`) and the developer head-unit server
(`:5277`) stayed up throughout, no rig wedge, no operator recovery needed between runs.

**B1r (`ServiceDiscoveryUpdate`, control channel) is the one mode that does not crash.** This is the
one difference in mechanism visible from our own log: B1r's lever rides the control channel
(`CommManager.sendServiceDiscoveryUpdateForVideo`), while B2r/B3r/B4r's `Media.Config` selection and
B6r's second `UpdateUiConfigRequest` all ride the video/UI-config channel a second time within the same
rotation window as the ordinary margin reannounce. Whether that channel collision is the actual trigger
inside Gearhead's own code is inference, not confirmed from our log alone, but it is the only
structural difference between the mode that survives and the four that do not.

**No `-8` (`STATUS_MEDIA_CONFIG_MISMATCH`) appears in any of the five captures**, checked explicitly
and distinguished from coordinate-string false positives (`grep -a -- "-8"` on every file was
cross-checked against context; every hit was a view-bounds string like `825,372-825,372`, not a status
field). The phone never got the chance to answer with an explicit refusal; it crashed before any such
answer could be logged.

**Connect-time `config_index` was `0` in every run** (B1r, B2r, B3r, B4r, B6r), matching round 1.

### B1r, mode 1, `ServiceDiscoveryUpdate`

1. **`[GEOMETRY_PROBE]` block:** `normalisation LANDSCAPE -> PORTRAIT` → `adopting canvas 2400x1080 ->
   1080x2400` → `mode=1 firing for a portrait canvas 1080x2400, negotiated 1080x1920` →
   `TX ServiceDiscoveryUpdate for the video service`.
2. **`[ServiceDiscovery] NegotiatedResolution is:`** carried the update: `1920x1080` at connect →
   `1080x1920` on the wire after the probe fired. The wire genuinely carried the portrait shape.
3. **Phone's answer within 10s:** none from the probe path. The ordinary margin-correction path fired
   independently and was acknowledged (`TX UpdateUiConfigRequest: L=108 T=0 R=108 B=0` →
   `UpdateUiConfig reply received. AA acknowledged new margins.`), same mechanism round 1 documented,
   unrelated to this mode's own lever.
4. **`Received video dimensions`:** unchanged (still the connect-time `1920x1080`).
5. **Photograph: the same squeeze as round 1's `a1-after.png`**, not a reflow, see Setup notes for the
   correction and the side-by-side basis. `evidence/rotation-geometry-round2/b1r-after.png`.
6. **Session survived**: `rendered` non-zero and climbing back to 29-30fps in every throughput window
   after the probe, no stall. Confirmed again on rotating back to landscape.

Not applicable: B1r announces one config, not two, so no connect-time `config_index` question for this
run beyond the general table above.

### B2r, mode 2, `Media.Config` STATUS_READY selecting index 1

1. **`[GEOMETRY_PROBE]` block:** fired cleanly, `TX Media.Config status=STATUS_READY indices=[1]`.
2. **Phone's answer:** Gearhead's `:projection` process crashed 33ms after the TX (table above). No
   `Media Sink Stop Request`, no new `Media Start Request VIDEO`, no `-8`, the crash preempted any
   protocol-level answer.
3. **`Received video dimensions`:** unchanged (no new line after connect).
4. **Photograph:** `b2r-after.png` shows D-POCO's own home launcher: our app had already finished
   `AapProjectionActivity` by the time the shot was taken (see item 6).
5. **Session did not survive.** Our own app detected the disconnect cleanly:
   `AapProjectionActivity: Disconnected unexpectedly` → showed the reconnecting overlay → 20s later
   `Reconnect gave up after 20s (BASE_EXPIRED). Finishing activity` → clean `onDestroy`. No crash, ANR
   or wedge on our side; `AapService` released the WiFi Direct group and video decoder cleanly.

### B3r, mode 3, `Media.Config` STATUS_WAIT then STATUS_READY

Identical shape to B2r. `[GEOMETRY_PROBE]` fired `TX Media.Config status=STATUS_WAIT indices=[1]`,
then 401ms later `TX Media.Config status=STATUS_READY indices=[1]`. Gearhead's `CONNECTED->
DISCONNECTING` landed 39ms after the WAIT TX, before the READY TX was even sent, and the FATAL
EXCEPTION followed 24ms after READY. Same crash signature (`GH.BackdropWinCtrl` this time, rather than
`GH.DisplayLayout`, as the component that first observes `OutOfCarLifecycle`). No media reconfig
answer, no `-8`. Our app's disconnect handling identical to B2r (`Disconnected unexpectedly` → 20s
reconnect timeout → clean finish).

**This is a different outcome from round 1's B3**, which stalled to near-zero throughput without ever
crashing (because round 1's probe never fired at all). The two are not comparable as the same defect:
round 1's B3 stall is still unexplained and may be the same Gearhead re-layout churn round 1's
Setup notes proposed, independent of anything a probe sends.

### B4r, mode 4, `Media.Config` index 1 then a video focus cycle

Same crash. The video focus half of the lever did execute before the crash:
`CommManager.releaseVideoFocusForKeyframe` → `AapTransport.send | VIDEO Video Focus Notification` →
Gearhead's own `CAR.GAL.VIDEO.LITE: VideoFocus lost unsolicited=false transient=false`, so the focus
release was received and acted on 2ms before `CONNECTED->DISCONNECTING` appeared. No focus regain was
ever logged; the connection was already tearing down. FATAL EXCEPTION 374ms after the `Media.Config`
TX. Same clean app-side disconnect handling as B2r/B3r.

### B6r, mode 5, `UpdateUiConfigRequest` carrying `ui_theme`

Same crash, despite `GeometryProbePolicy.renegotiates(UI_THEME) = false` meaning this mode touches no
video config message at all, confirming the trigger is not specific to `Media.Config`, but to a
second message on the video/UI-config channel in this window (see headline finding above).
`evidence/rotation-geometry-round2/b6r-after.png` is the one after-screenshot that caught Android
Auto's own visible error dialog before it auto-dismissed, title **"Android Auto encountered an error"**,
body **"Communication error 6 - The car's software may be out of date."** This is the user-facing face
of the same `OutOfCarLifecycle` crash. Same clean app-side disconnect handling.

## B5r, real_density and the reporter's density workaround

`geometry-probe-mode=0`, `screen-orientation=4` (Portrait, fixed), no rotation in any arm. All four
arms ran without incident, no crash, no probe firing (mode 0 is inert, confirmed no `[GEOMETRY_PROBE]`
lines in any of the four captures).

| Arm | `real-density` | `dpi-pixel-density` | `[ServiceDiscovery] real_density is:` | Touch/pan |
|---|---|---|---|---|
| B5r-a | false | 0 (Auto, 440) | absent (expected) | Correct: `raw=540,1600→video=432,1280`, consistent mapping through the whole swipe |
| B5r-b | true | 0 (Auto, 440) | `440` | Correct, same mapping constants as B5r-a |
| B5r-c | false | 180 | absent (expected) | Correct: pan visibly moved the map to a different area and raised Google Maps' own "Re-centre" button |
| B5r-d | true | 180 (candidate fix arm) | `440` | Correct, same as B5r-c; layout identical to B5r-c |

**`real_density` reports the panel's true 440 regardless of `dpi-pixel-density`**, confirming it is an
independent wire field rather than a substitute for the layout DPI setting. Arms B5r-b and B5r-d both
read `440` even though B5r-d's layout is visibly the low-DPI one.

**B5r-c and B5r-d's low-DPI layout is visibly and substantially different from B5r-a/b's**, not just in
theory: `+`/`-` zoom buttons, a weather widget (`23°`/`22°`, `30°`/`16°`), full track-title text and
transport controls, and a fourth app icon (Waze) all become visible in the bottom dock at 180dpi that
are cropped or absent at native 440dpi. `evidence/rotation-geometry-round2/b5rc-maps.png` and
`b5rd-maps.png` are side-by-side identical to each other, and both visibly different from `b5ra-*` /
`b5rb-maps.png`.

**Touch was confirmed correct in every arm from the `sendTouchEvent` log line**, not from visual pan
alone: `raw=` (screen pixels) mapped to `video=` (negotiated video pixels) with a consistent
`view=1080x2400 video=1080x1920 margin=216x0 fit=FILL` in all four arms; the touch-to-video mapping
does not change with either setting. B5r-a/b's swipe did not visibly move the map (the same swipe
distance apparently fell inside Maps' own re-center snap), so touch correctness there rests on the log
line alone; B5r-c/d's identical swipe distance did visibly pan the map and raise "Re-centre", which is
the stronger of the two confirmations and happens to be on the low-DPI arms, the ones the reporter's
workaround targets.

**Waze was not tested**, per the brief's own instruction to leave it out this round (the projected
content is outside the accessible view hierarchy on this build, so no scriptable bounds exist).

**The question this arm answers**: at `dpi-pixel-density=180` (the reporter's own workaround),
`real_density=true` (B5r-d) produces exactly the same layout and exactly the same working touch
mapping as `real_density=false` (B5r-c). Announcing the panel's true density separately did not change
anything observable about touch correctness at low DPI. On this build, touch already works correctly
in both B5r-c and B5r-d. This round did not reproduce a touch failure at low DPI to have the candidate
fix, so it cannot show the fix *repairing* anything; what it shows is that turning `real_density` on
carries no visible cost or side effect at the reporter's own DPI setting.

## Anything the brief did not ask about

- **The round's central finding is a real crash in a shipped Google app, not a code defect on our
  side.** Every one of B2r/B3r/B4r/B6r's four `AndroidRuntime` crash traces name
  `com.google.android.projection.gearhead:projection` as the crashing process, with our own app's log
  showing correct, clean handling of the resulting disconnect in all four cases (reconnecting overlay,
  20s timeout, clean teardown, no ANR, no leaked decoder or WiFi Direct state). This reframes §8's three
  possible outcomes: none of "mid-session renegotiation is real", "the phone refuses with -8", or
  "the phone acknowledges nothing" describes what happened. A fourth outcome, the message crashes the
  phone's own Android Auto process outright, is what this round found for four of five levers.
- **This has an obvious real-world safety implication if any of modes 2-5 were ever shipped as a live
  fix for the underlying rotation-squeeze defect**: sending any of these messages during a rotation
  would crash the user's Android Auto session on this Gearhead build (`17.5.663204`), not fix the
  squeeze. `ohu-fixes-handoff/996-rotation-analysis.md` should record this as the reason service
  rediscovery and a live `Media.Config` reselection are not viable fixes on current Gearhead, stronger
  than round 1's "never fired" reading.
- **The crash is keyed to timing against Gearhead's own internal `CONNECTED->DISCONNECTING`
  transition, which happens on Gearhead's own initiative** (not something our log shows us requesting)
  within tens of milliseconds of each lever's TX. This looks like our message arriving inside a narrow
  window where Gearhead's own car-connection state machine is mid-transition from the local Android
  configuration change alone (i.e. the same rotation that make our own canvas-adopt fire), and the
  extra message is what it fails to handle gracefully mid-transition. This is inference from timing
  correlation across four repeated instances, not confirmed from any Gearhead-side source.
- **Round 1's B3/B6 stall-without-crash and this round's crash-on-fire are not the same phenomenon.**
  Worth stating plainly since both involve modes 3 and (in round 1's numbering) 6: round 1's stall
  happened with the probe never firing at all, so whatever caused it is independent of anything this
  round measured. Do not fold round 1's B3/B6 note into this round's crash finding as corroboration:
  they are two different observations on two different code paths.
