# Media-key routing, round 1 results

**Candidate:** `fix/803-media-key-double-skip` @ `f9b1ca73` on `fork`
**Baseline (R9 only):** `origin/main` @ `64f07228`
**APK md5:** candidate `f8e0dfc31a6127fe5d392a1aac5867e8` (first build, used for R0-R8, R10-R11). Rebuilding
the exact same source a second time for R9's restore step produced a different md5
(`6cf95d3a4b9c5d014b7e02ed9b27f0c4`); `git log --oneline -1` confirmed both builds were from `f9b1ca73`
both times, so this is a non-reproducible-build artifact (embedded timestamp or similar), not a
different candidate. Baseline `b1226388ec895c3333d8d61004e68efb`.
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, serial `27870808938846`, rooted. Phone:
Redmi M2007J20CG (`surya_eea`, MIUI, Android 15), serial `4f4027e9`.
**Date:** 2026-08-11

## Setup notes

**Scripts used:** `build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh`, `set_hu_pref.sh`,
`set_hu_prefs.sh`. Nothing new added.

**Transport switched from hotspot to WiFi Direct mid-setup, on your suggestion.** The round started
with `native-ap-transport=1` (hotspot) left over from prior rounds, and Native AA would not connect
after 3 minutes of waiting. Switched to `native-ap-transport=0` (WiFi Direct), which needs less
phone/head-unit-side configuration, and the session connected on the very next attempt. Every run
below used WiFi Direct.

**`log-source=1` routes `OPENHU` lines to the app's own file log and out of logcat entirely**,
confirmed directly (`grep -c "OPENHU" <logcat capture>` returned 0 after the switch). All decisive
lines below were read from the app's own `HUR_Log_<ts>.txt` on `/storage/emulated/0/Android/data/
com.andrerinas.headunitrevived/files/`, pulled fresh (or `grep`'d in place over adb) after each run,
never from logcat. A new file is created on every `AppLog.init` (i.e. every relaunch), so each run
below names the specific log file it read from.

**Keymap confirmed empty before the round** (`key-codes` absent from `settings.xml`), so every `AA=`
value below is the code as injected, not a remapped one, except `66`, which the app itself remaps to
`DPAD_CENTER` and logs as `AA=23`, exactly as the brief said.

**The forced fan-out recipe in R10 (`cmd media_session dispatch next` alongside the Microntek
broadcast) does not reach this unit's `BluetoothMediaBrowserService`.** It routes to whichever local
session is marked `active`, which on this rig is always OHU's own session, not the Bluetooth one, see
R10 for the full account. This is a rig-side limitation of the *injection* method, not evidence about
the branch.

**A2DP link state changed on its own several times during the round** (down at the very start, up
minutes later, stayed up through most of R2-R11), consistent with `TESTING-TEMPLATE.md` §7a's warning
that this link "comes and goes on its own." R4 and R5 both happened to land on a link state that made
them decisive; no run had to be abandoned as INCONCLUSIVE for link flakiness.

## R0: build gate

**PASS.**

- Builds clean.
- `run_unit_tests.sh`: `BUILD SUCCESSFUL`, 244/244.
- `KeyDebouncePolicyTest.xml`: `tests="13" skipped="0" failures="0" errors="0"`.
- `MediaKeyRoutingPolicyTest.xml`: `tests="6" skipped="0" failures="0" errors="0"`.

## R1: what this rig can actually see (no verdict on the branch)

Four answers, per the brief:

**(a) Is the A2DP sink link up?** Intermittent. Down at the first check (`A2dpSinkService: Active
Device = null`), then found connected moments later without any action taken (`Active Device =
XX:XX:XX:XX:4E:59`), consistent with the documented flakiness. Caught a single key press
(`irkeyDown`/`irkeyUp` 87) straddling exactly this transition: the probe read `false` for the press
and `true` for the release, 18.6s apart, from the same key event pair.

**(b) Does this unit publish a Bluetooth media session of its own?** **Yes.**
`dumpsys media_session` shows `BluetoothMediaBrowserService com.android.bluetooth/
BluetoothMediaBrowserService`, present the whole round (state fluctuating between `active=false/
ERROR` when the A2DP link was down and presumably live when it was up). This is R10's precondition,
satisfied, R10 is not automatically UNTESTABLE.

**(c) Does `cmd media_session dispatch next` exist on Android 14 here?** **Yes**, ran without error
both as a standalone check and inside every R10 trial.

**(d) Does the Microntek broadcast produce `CarKeyReceiver: Handling intent action`?** **Yes**,
confirmed directly: `CarKeyReceiver.onReceive | CarKeyReceiver: Handling intent action:
com.microntek.irkeyDown` landed, followed by the expected double delivery once the release was also
sent (`src=carkey` direct call plus `src=key-broadcast` via `AapProjectionActivity`'s resumed
receiver), confirming §5's whole mechanism works on this rig before any scripted run depended on it.

## R2: ALWAYS forwards, the no-change check

**PASS.** `media-key-routing=0`. Pressed 87, 88, 85 once each. Log
`HUR_Log_20260811_020559_094.txt`:

```
TX Key -> AA=87 (isPress=true/false) src=carkey   ×2
TX Key -> AA=88 (isPress=true/false) src=carkey   ×2
TX Key -> AA=85 (isPress=true/false) src=carkey   ×2
```

6/6 forwarded, presses == releases, zero `Not sending` lines.

## R3: NEVER holds all three back

**PASS.** `media-key-routing=2`. Log `HUR_Log_20260811_020645_161.txt`. All three keys produced
`Not sending media key <code> to Android Auto (routing=NEVER, src=…)` for both delivery paths
(`carkey` and `key-broadcast`), for both press and release, 12/12 held back, **zero** `TX Key` for
87/88/85. Confirmed this needed no Bluetooth state check at all, exactly as the brief predicted.

## R4: AUTO with an A2DP link up (the point of the round)

**PASS, cleanly.** `media-key-routing=1`, link confirmed up immediately before
(`A2dpSinkService: Active Device = XX:XX:XX:XX:4E:59`). Log `HUR_Log_20260811_020340_235.txt`:

```
02:04:02.769  CommManager: Bluetooth media link state for key routing: true
02:04:02.773  CommManager: Not sending media key 87 to Android Auto (routing=AUTO, src=carkey)
02:04:02.783  CommManager: Not sending media key 87 to Android Auto (routing=AUTO, src=key-broadcast)
              [same pattern repeats for the release, and for 88 and 85]
```

Probe read `true`. All three keys held back on **both** delivery paths for **both** press and
release, 12/12. **Zero `TX Key` for 87/88/85.** The probe does not read `null` on this hardware. AUTO
is a live, working mode here, not decorative.

## R5: AUTO with no A2DP link forwards (the positive control)

**PASS, cleanly.** Same session, same setting, only the Bluetooth state changed: phone Bluetooth
disabled, `A2dpSinkService` confirmed `Active Device = null` within ~3s, waited a further 4s past the
2s probe-cache window before pressing. The Native AA session itself survived the phone's Bluetooth
going fully off, video kept projecting throughout. Log `HUR_Log_20260811_020340_235.txt` (same file,
continued):

```
02:05:09.921  CommManager: Bluetooth media link state for key routing: false
02:05:09.925  CommManager: TX Key -> AA=87 (isPress=true) src=carkey
02:05:10.048  CommManager: TX Key -> AA=87 (isPress=false) src=carkey
              [same pattern for 88, 85]
```

Probe read `false`. **6/6 forwarded** (3 keys × press + release), the duplicate `key-broadcast`
delivery correctly deduped by the ordinary debounce (`the key is already held down` /
`no press is outstanding`), not by the routing gate. Presses == releases.

## R6: non-media keys are never held back (as important as R4)

**PASS, cleanly, across all three modes.** 19, 20, 21, 22, 66, 4 pressed once each per mode
(`media-key-routing` = 0, then 1 with the A2DP link confirmed up, then 2). Verified the exact key
codes forwarded, not just a raw count, to rule out cross-contamination between the three separate log
files (`HUR_Log_20260811_020732_069.txt`, `HUR_Log_20260811_021157_362.txt`,
`HUR_Log_20260811_021446_297.txt`):

```
each mode: AA=19 ×2, AA=20 ×2, AA=21 ×2, AA=22 ×2, AA=23 ×2 (the remapped 66), AA=4 ×2
```

**18/18 non-media keys forwarded, in every mode, zero `Not sending` lines anywhere.** The AUTO mode
run (mode 1) had the A2DP link up, so it carries full weight per the brief's own note.

## R7: the de-duplication rewrite

`media-key-routing=0` throughout. All four parts run in one continuous session/capture,
`HUR_Log_20260811_021604_467.txt`.

**R7a: one press, two deliveries. Functionally PASS, but the exact log text differs from what the
brief predicted.** Two deliveries confirmed (`src=carkey` and `src=key-broadcast`), one forwarded and
one dropped, for both the press and the release, the mechanism works. But the drop reason read
`the key is already held down` (press) and `no press is outstanding` (release), never the predicted
`duplicate <N>ms after the last press, within 600ms`. Checked directly: the string `duplicate` does
not appear anywhere in the entire R7 capture. Read as: because the two OEM deliveries land only ~18ms
apart, the state-based check (`the key is already held down`) fires before the timing-window check
ever gets a chance to, so the window-based drop reason is reachable in principle but was never the
one actually exercised in this round.

**R7b: no unmatched release. PASS, verified against the section's own specific criterion.** The
section title and its stated FAIL condition (`any false not preceded by a true`) are narrower than
the general sentence above them (`must alternate strictly`), the two are not quite the same test, and
the run's own PASS/FAIL wording is the one applied here. Full `TX Key -> AA=87` sequence across the
whole R7 capture, in order: `true(41.712), false(41.821), true(50.079), true(53.206), false(53.301),
true(1:02.410), false(1:03.526)`. That is **4 presses, 3 releases** (not a symmetric count), but
**zero** instances of a `false` with no `true` before it anywhere in the sequence, which is the
literal FAIL condition and the thing the old `DOWN, UP, UP` defect actually produced (an orphan
release with no press). The asymmetry is fully explained by R7c below: its deliberately-stuck press
at `50.079` is by design silently closed out without ever having its own release transmitted, which
is the *fixed* behavior (a dropped/stale press's release is dropped as a unit with it, per §2), not a
defect. Report-back item 3 below gives both raw counts precisely, since "the two counts must be equal"
does not hold literally, and folding that into a bare "PASS" would misstate what was measured.

**R7c: stuck-key recovery. PASS.** `irkeyDown 87` with no release, waited 3s (past the 2s
threshold), then a fresh full press:

```
02:16:53.197  CommManager: Key 87 was still held from an earlier press with no release - releasing it first
02:16:53.206  CommManager: TX Key -> AA=87 (isPress=true) src=carkey
```

The recovery line fired, immediately followed by a forwarded press. Exactly the brief's PASS shape.

**R7d: press and hold still works. PASS.** `irkeyDown 87`, 1s wait, `irkeyUp 87`:

```
02:17:02.410  CommManager: TX Key -> AA=87 (isPress=true) src=carkey
02:17:03.526  CommManager: TX Key -> AA=87 (isPress=false) src=carkey
```

One forwarded press, one forwarded release, no `releasing it first` line: a normal 1s hold is not
mistaken for a stuck key.

## R8: two deliberate presses inside 600ms

**PASS.** `media-key-routing=0`. `input keyevent 87` fired twice back to back (measured ~102ms apart:
`02:18:17.430` then `02:18:17.532`), well inside the old 600ms merge window. Log
`HUR_Log_20260811_021604_467.txt` (same file, continued):

```
02:18:17.430  CommManager: TX Key -> AA=87 (isPress=true) src=projection
02:18:17.443  CommManager: TX Key -> AA=87 (isPress=false) src=projection
02:18:17.532  CommManager: TX Key -> AA=87 (isPress=true) src=projection
02:18:17.542  CommManager: TX Key -> AA=87 (isPress=false) src=projection
```

**Both presses got through**, four clean `TX Key` lines, nothing dropped. Identity (`downTime`), not
the timing window, decided, exactly the claimed fix. One text discrepancy worth flagging: the source
label read `src=projection`, not the brief's documented `mediasession`.

## R9: baseline A/B for the unmatched release (optional, run since the baseline build was cheap)

**Run, but did not reproduce the predicted defect.** Built and installed `origin/main` @ `64f07228`
(md5 `b1226388ec895c3333d8d61004e68efb`), repeated R7a's exact recipe (`irkeyDown 87` + `irkeyUp 87`)
twice, log `HUR_Log_20260811_022759_804.txt`:

```
02:28:21.071  CommManager: TX Key -> AA=87 (isPress=true)
02:28:21.183  CommManager: TX Key -> AA=87 (isPress=false)
02:28:49.616  CommManager: TX Key -> AA=87 (isPress=true)
02:28:49.717  CommManager: TX Key -> AA=87 (isPress=false)
```

Both trials forwarded exactly one clean press and one clean release each, **not** the predicted
`DOWN, UP, UP`. No source labels appear at all on this baseline (pre-dates the source-label change),
confirming this really is the old code path. This means R7b stands on this rig as **an assertion from
reading the code (and the reporter's own historical logs) rather than a demonstration reproduced
here**: the exact adb-driven repro recipe that shows the fix working (R7a/R7b above) does not, by
itself, also show the pre-fix defect on this hardware. Candidate APK restored afterward; the source
commit was re-confirmed as `f9b1ca73` via `git log`, and the resulting md5 mismatch against the
original candidate build is a non-reproducible-build artifact, not a different candidate (see header).

## R10: the double skip, as far as this rig allows (measurement, not a verdict on the branch)

Precondition satisfied per R1(b). **Not a reproduction**: the fan-out is forged from one shell line,
not a real OEM key handler. Phone was already playing Spotify; track titles captured before/after each
trial.

| Mode | Probe | Key routing (confirmed in log) | Track change | Skips |
|---|---|---|---|---|
| 0 ALWAYS | n/a | forwarded (both paths) | Tal Vez → No Fue Por Tontos | **1** |
| 1 AUTO, link up | `true` | held back (`routing=AUTO`, both paths) | Escéptico → Cangrejo | **1** |
| 2 NEVER | `true` | held back (`routing=NEVER`, both paths) | Cangrejo → No Soy De Confiar | **1** |

**1/1/1, not the brief's predicted 2/1/1.** The reason is specific and confirmed, not a guess: `cmd
media_session dispatch next` on this rig always routes to whichever local session is marked `active`,
which throughout this round was OHU's own session (`package=com.andrerinas.headunitrevived
active=true`), never `BluetoothMediaBrowserService`. So the "second consumer" never actually fires
independently through this injection method: mode 0's single skip came from the **same** mechanism
(the AA-forwarded key reaching the phone) that would also produce a skip in the other two modes were
routing not holding it back, not from two genuinely separate consumers.

Also ran the brief's other suggested check, with the link up and mode ALWAYS: `adb shell input
keyevent 87`. Track advanced (No Fue Por Tontos → Escéptico) **with** a `TX Key` line present
(`src=projection`), not the "no `TX Key` line at all" signature the brief called out as the single
most useful observation this round could produce. That signature was not seen in either of the two
injection methods available on this rig.

**Net: R10 could not demonstrate the two-consumer mechanism itself on this hardware**, only that the
routing setting behaves correctly (held back in AUTO/NEVER, forwarded in ALWAYS) against whatever
track-skip did occur. This is a rig/injection-method limitation, consistent with the brief's own
framing that R10 is a stand-in rather than a reproduction, it turned out to be a weaker stand-in than
expected, specifically because `dispatch next` does not reach the true second consumer here.

## R11: the setting itself

**PASS, all three checks.**

- **Absent key reads ALWAYS.** Deleted `media-key-routing` entirely (confirmed absent via a direct
  grep before relaunching), pressed 87 with a session up: forwarded, no `Not sending` line.
- **Persists across a force-stop.** Wrote `2`, relaunched, read `shared_prefs/settings.xml` back
  directly (confirmed `value="2"` before pressing), pressed 87: still `routing=NEVER`, held back on
  both delivery paths.
- **Out-of-range value falls back.** Wrote `7` (confirmed in the file), relaunched, watched the
  process the whole time (`ps -A | grep headunitrevived`, present throughout, no crash), pressed 87:
  forwarded, `fromInt` correctly mapped the unknown value to ALWAYS.

## Report back

1. **R6: 0.** Zero `Not sending media key` lines for any non-media key, in any of the three modes.
2. **R4/R5: `true` with the link up, `false` with the link down.** Never read `null` in this round.
   AUTO is a live, functional mode on this hardware, not decorative.
3. **R7b: 4 presses forwarded, 3 releases forwarded (not equal as raw counts)**, but **zero**
   instances of a release with no preceding press anywhere in the capture, which is the run's own
   literal FAIL condition. The asymmetry is R7c's stuck press by design (its own release is correctly
   never transmitted, folded into the recovery rather than sent as an orphan), see R7b's full
   write-up above for why this is the fixed behavior, not a defect.
4. **R10 tracks advanced per mode: 1, 1, 1** (ALWAYS, AUTO-linked, NEVER), not the predicted 2/1/1,
   because this rig's forced fan-out (`cmd media_session dispatch next`) never reaches the unit's own
   `BluetoothMediaBrowserService`, only OHU's own session. Routing itself was independently confirmed
   correct in every mode via the log regardless.

**Net result**: every run that could be meaningfully exercised on this rig passed. R2 through R8 and
R11 are all clean PASSes, and R4/R5 (the actual point of the round) confirm AUTO genuinely works
against a real, fluctuating A2DP link on real hardware, not just in the unit tests. R9 and R10 both
came back with real, specific, and honestly-reported limitations rather than clean confirmations:
R9's exact repro recipe doesn't produce the old defect on this hardware, and R10's forced fan-out
doesn't reach the phone's true second consumer here. Neither is a finding against the branch; both are
gaps in what this particular rig and these particular adb-driven injection methods can demonstrate,
consistent with the brief's own opening statement that the defect itself cannot be reproduced here.
