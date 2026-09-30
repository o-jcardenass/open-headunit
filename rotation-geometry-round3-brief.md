# rotation geometry: round 3 brief

Round 2 fired all five levers and settled that Android Auto refuses two of them. This round does two
things round 2 could not: it fires a lever **with no rotation at all**, which removes a confound that
invalidated round 2's own explanation, and it asks whether the one lever that survives is being
ignored only because of the protocol version we announce.

**This round has a pre-round step that needs no hardware.** Section 2 is four greps against round 2's
own captures, which are on the rig and were never uploaded. Do those first and report them even if the
rest of the round is deferred.

## 1. Build

**Candidate:** `probe/geometry-renegotiation` @ `c4186efd` (3 commits on
`fix/rotation-keeps-negotiated-geometry` @ `65e91b56`, itself 2 commits on `main` @ `80a81099`).

**No baseline build this round.** Nothing here is a comparison against `main`.

### R0, build gate

`./gradlew :app:testGithubDebugUnitTest` must report **2200** tests, with
`GeometryProbePolicyTest` 12, `AapVersionPolicyTest` 7, `MaxUnackedPolicyTest` 4,
`ProjectionOrientationPolicyTest` 9, `SessionGeometryLockPolicyTest` 4. Report the APK md5.

If any count differs, stop and report. It means the tip is not the one this brief describes.

## 2. Pre-round: four greps against round 2's captures

Round 2's `.txt` captures were never uploaded and are not in the archive, so these were never read.
They cost nothing and one of them may settle the round's central question outright. Run them against
every round 2 capture you still hold and quote what comes back, including "no match".

```bash
grep -n "RECEIVED BYEBYE REQUEST FROM PHONE" <each round 2 capture>
grep -n "Handshake: Version response received: the phone selected" <each round 2 capture>
grep -n "UpdateUiConfigRequest\|UpdateUiConfig reply" b6r*.txt
grep -n "Media Sink Stop Request\|Media Start Request" <each round 2 capture>
```

1. **G1, the ByeBye.** If the phone sent `ByeByeRequest` before the disconnect, its `Reason:` is the
   finding. `NOT_SUPPORTED` or `NOT_CURRENTLY_SUPPORTED` would confirm from our own side that Android
   Auto rejected the message deliberately, which is currently inferred from validation strings in its
   binary rather than observed on the wire.
2. **G2, the negotiated version.** What did the phone select in round 2? This decides how much the
   1.6 arm below is worth. Quote the whole line.
3. **G3, the ordering in B6r.** How many `UpdateUiConfigRequest` lines are there, in what order, and
   how far apart? The theme one and the ordinary margin one both went out; which was first matters.
4. **G4.** Whether any `Media Sink Stop Request` or new `Media Start Request` ever appeared after a
   probe fired.

## 3. What changed since round 2, and why

**Round 2's explanation was wrong and has been replaced.** It concluded that "a second message on the
video and UI-config channel during a rotation" was the trigger, with B1r as the arm that survived.
B1r sent one too: `reannounceMargins()` is not gated on the probe, so a plain
`UpdateUiConfigRequest{margins}` went out on the video channel in **all five runs**. The claimed
variable was present in the arm that survived, so it explained nothing.

What replaced it: Android Auto's own binary, in 17.5 and 17.8, carries the validation strings
**"Multiple media configs received"** and **"UpdateUiConfigRequest must not specify an updated
UiTheme"**, which is exactly what modes 2 to 4 and mode 5 send. Both levers are refused by design. The
`:projection` crash is downstream of the refusal, on a car connection that has already gone.

**Two things follow, and this round tests both.**

- The refusal should not need a rotation. Section 5 fires the levers with the panel held still.
- With no rotation there is no surface change, so `reannounceMargins()` does not run and the probe's
  message goes out **alone**. Round 2 could not produce that condition.

**The version work is new on this tip.** We announce AAP 1.2 while Google's own constant is 1.6, and
until `c4186efd` nothing read the phone's answer back. Now the handshake logs what the phone selected,
refuses only on `NO_COMPATIBLE_VERSION`, and withholds control 26 from a phone below 1.6. Note this
changes mode 1's behaviour: **at 1.2 it is now withheld rather than sent**, so round 2's mode 1 result
is not reproducible by default. That is deliberate and section 4 checks it.

**AAP 1.6 is not WPP 4.2.** They are different protocols on different transports with no shared state,
and the AAP version cannot cause a WPP-over-TCP endpoint to be advertised. Nothing in this round
touches wireless bring-up. Leave `native-wifi-version-exchange` off, as it is by default.

## 4. Preconditions

**D-POCO only, Self Mode, every run.** There is no two-device arm.

- **Launch with the explicit action**, never a bare `am start`:
  `am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE`. Round 2 lost a run to a plain
  launch arming Native AA and poking bonded phones.
- **On this unit `user_rotation=1` is landscape and `0` is portrait**, the reverse of what a generic
  table suggests. Confirm against `Raw size:` in our own log before proceeding, never `dumpsys
  display`. Round 2 lost a run to this.
- **`night-mode` must be `1` (DAY)** for every run in section 5, so nothing re-evaluates night state
  during the window. `0` is AUTO and `4` is LIGHT_SENSOR; either could put a sensor message on the
  wire and spoil the isolation the section depends on.
- `log-capture-enabled=false`; capture with logcat, not the in-app exporter.
- Screenshots via `screencap`, not a camera.
- Set every probe key with `hur-wifi-test-scripts/set_pref.sh`. The new key this round is
  `geometry-probe-announce-16` (boolean), registered for `ACTION_SET_SETTINGS`.
- Back up `settings.xml` at round start and restore it at the end.

## 5. The lines that decide every run

| What | Line |
|---|---|
| Negotiated version | `Handshake: Version response received: the phone selected` |
| 1.6 availability | the same line's tail, `1.6 message set available` or `withheld` |
| Probe armed and fired | `[GEOMETRY_PROBE] mode=N firing for a ... canvas` |
| Probe refused, with reason | `[GEOMETRY_PROBE] mode=N not fired:` |
| Mode 1 sent | `[GEOMETRY_PROBE] TX ServiceDiscoveryUpdate for the video service` |
| Mode 1 withheld | `[GEOMETRY_PROBE] ServiceDiscoveryUpdate withheld: the phone selected` |
| Canvas did not move | `[GEOMETRY_PROBE] canvas is already ...; nothing to adopt` |
| Canvas moved | `[GEOMETRY_PROBE] adopting canvas` |
| What mode 1 carried | `[ServiceDiscovery] NegotiatedResolution is:` |
| Ordinary margin path | `TX UpdateUiConfigRequest:` and `UpdateUiConfig reply received` |
| Session alive | `Throughput over` with `rendered=` non-zero |
| Session lost | `AapProjectionActivity: Disconnected unexpectedly` |
| Phone refused explicitly | `RECEIVED BYEBYE REQUEST FROM PHONE` with its `Reason:` |
| Gearhead died | `FATAL EXCEPTION`, `OutOfCarLifecycle`, `Car connection state changed` |

```bash
grep -nE "GEOMETRY_PROBE|the phone selected|NegotiatedResolution|UpdateUiConfig|Throughput over|Disconnected unexpectedly|BYEBYE|FATAL EXCEPTION|OutOfCarLifecycle|Car connection state changed" <capture>
```

**There is no PASS or FAIL in sections 6 and 7.** Every outcome is a result. Section 6 has pass
conditions because it checks new code does what it says.

## 6. Part A: the withhold does what it claims

### A1r, mode 1 at 1.2 is withheld and says so

`geometry-probe-mode=1`, `geometry-probe-announce-16=false`, `screen-orientation=0`, `night-mode=1`.
Session up in landscape, screenshot, rotate to portrait, wait 20 s, screenshot, rotate back.

**PASS** requires all four:

1. The version line reads `the phone selected 1.2` (or whatever it selects) and ends
   `1.6 message set withheld`.
2. `[GEOMETRY_PROBE] mode=1 firing for a portrait canvas` is present. The probe still fires; it is the
   send that is withheld.
3. `[GEOMETRY_PROBE] ServiceDiscoveryUpdate withheld: the phone selected 1.2, which is below the 1.6
   message set` is present, and **`TX ServiceDiscoveryUpdate` is absent**.
4. The session survives: `Throughput over` with `rendered=` non-zero after the rotation.

Report the version line verbatim. It is the single most useful line in the round.

## 7. Part B: does 1.6 change what the phone does

### B1r, an ordinary session at 1.6, no probe

`geometry-probe-mode=0`, `geometry-probe-announce-16=true`, `screen-orientation=0`. Bring a session up
and leave it alone for **five minutes with no rotation**.

This is the safety arm and it comes first. Report: the version line, whether the session formed at
all, throughput across the whole five minutes, any `Unsupported ... message type` or `Unknown
msg_type` lines and how many, and whether anything disconnected. If announcing 1.6 costs a session,
this run says so before any lever is fired.

### B2r, mode 1 at 1.6

`geometry-probe-mode=1`, `geometry-probe-announce-16=true`. Same sequence as A1r.

The question is what the phone selected, and there are only two outcomes:

- **It selected 1.6.** Then `TX ServiceDiscoveryUpdate` goes out. Report what
  `[ServiceDiscovery] NegotiatedResolution is:` carried, whether any `Received video dimensions`
  followed, and which of three the photograph shows: **a correct portrait layout**, **the same squeeze
  as `evidence/rotation-geometry-round1/a1-after.png`**, or **frozen or black**. A correct portrait
  layout is the only positive result this whole thread can still produce.
- **It selected less than 1.6 anyway.** Then the withhold line prints again and the arm is answered:
  the phone caps the version regardless of what we ask, and control 26 can never be reached from here.
  That is a real result, not a failed run. Report the version line and stop this arm.

## 8. Part C: the levers with the panel held still

**This is the heart of the round.** Every run here keeps the panel in landscape and never rotates, so
no surface callback fires, `reannounceMargins()` does not run, and the probe's message is the only
thing on the video channel in the window.

The trigger is a night-mode toggle, which delivers a configuration change without a rotation:

```bash
adb shell cmd uimode night yes     # fires the probe
# ... observe for 30 s ...
adb shell cmd uimode night no      # restore
```

**Confirm the trigger worked before reading anything else.** The `[GEOMETRY_PROBE]` block must appear
and must contain `canvas is already 2400x1080; nothing to adopt`. If instead the projection activity
was destroyed and recreated, `uiMode` is not being handled as configured on this ROM: mark the run
**UNTESTABLE** and say so. Do not substitute a rotation.

For each run below, report in this order: the `[GEOMETRY_PROBE]` block verbatim, whether the canvas
line says nothing to adopt, **how many `TX UpdateUiConfigRequest` lines appear in the window** (the
answer should be zero for C1r and one for C2r, and if it is more the isolation failed and the run is
worth less), whether the phone answered anything within 10 s, whether a `BYEBYE` arrived with a
reason, whether Gearhead crashed, and whether the session survived.

| Run | `geometry-probe-mode` | What is fired, with no rotation |
|---|---|---|
| C1r | 2 | `Media.Config` STATUS_READY selecting index 1 |
| C2r | 5 | `UpdateUiConfigRequest` carrying `ui_theme` and nothing else |

`geometry-probe-announce-16=false` and `night-mode=1` for both.

**What each outcome means, decided in advance.**

- **The session drops in C1r and C2r exactly as it did in round 2.** Then the rotation was never
  relevant and the message alone is refused, which confirms the validation reading and closes the
  thread. This is the expected result.
- **The session survives both.** Then the refusal needs the rotation context after all, the validation
  strings are not the whole story, and round 2's crash has a cause nobody has identified yet. Say so
  plainly; it would reopen the question.
- **They split.** C2r is the more interesting half, because with no rotation its theme message is the
  only `UpdateUiConfigRequest` in the session's window and nothing can be blamed on a margin update
  arriving beside it.

## 9. What this round settles

- Whether the two refusals depend on the rotation or only on the message, which round 2 could not
  separate and which decides whether its crash finding is fully explained.
- Whether the phone will negotiate AAP 1.6 with us at all, and if it does, whether service
  rediscovery is acted on rather than ignored. This is the last open route to a working mid-session
  geometry lever.
- Whether announcing 1.6 is safe on an ordinary session, before anything is ever proposed for `main`.
- Whether the new withhold and the version logging behave as written.
- From the greps alone, whether Android Auto told us it was refusing, in a `ByeByeRequest` reason
  nobody has looked for yet.

What it does **not** touch: wireless bring-up, WPP, the TCP endpoint, or `main`. The fix for the
rotation itself is finished and measured, and ships separately.
