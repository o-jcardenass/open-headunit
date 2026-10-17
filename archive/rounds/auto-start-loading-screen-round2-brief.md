# auto-start-loading-screen, round 2 brief: prove the pill on the projection's own loading screen

## What this round is

Round 1 PASSed A1 through A5, B1 and B2, and the headline result stands: the handoff measured
**236 ms** with no home-screen frame in any of the recording's 1006, and a second run measured
218 ms. Every one of those runs is settled and none is repeated here.

**One half of one run was reported as covered and was not.** B2 arm 1 window 2, the status pill on
`AapProjectionActivity`'s own loading screen, between that activity starting and the first video
frame. Two things went wrong, both mine rather than the tester's:

- **The log fallback the results leaned on does not exist.** All twelve `status pill step:` lines in
  that capture are `MainActivity.renderStagePill`. `AapProjectionActivity.renderProjectionPill()`
  wrote nothing at any level, so no log could speak to the projection's pill at all.
- **The frame kept as evidence is not that screen.** `b2arm1-window2-f15.png` is Android Auto's own
  first video frame, with the app rail and the media card in it. Arm 2's `f10` is AA's logo splash,
  the same thing. Neither arm caught the window, and arm 2's own log says why: the copyright banner
  at `11:38:47.453` and `onSurfaceChanged` at `11:38:47.728` put it at roughly **275 ms**, which a
  0.3 s screencap poll cannot land in.

So the projection pill is unproven rather than broken. The candidate now logs its decision, which
makes it gradeable from a log whatever the window does, and this round is that one measurement plus
a cheap regression check on the new commit.

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `feat/auto-start-loading-screen` | `8a1968a3` | 2 on `main` `80a81099` | 2176 tests, 0 failures |
| Baseline | none needed | | | |

```bash
git fetch fork
git checkout -B auto-start-loading-r2 fork/feat/auto-start-loading-screen
./gradlew :app:testGithubDebugUnitTest   # 2176, 0 failures
```

Round 1's `9cca8ddc` is unchanged underneath; `8a1968a3` adds one log call and two fields, nothing
else. No history rewritten, one APK, D-HU.

**`grep -c 'status pill step'` counts differently from this commit on.** Two activities write the
line now, each with its own prefix, so a count against a round 1 log is not comparable. Match on the
prefix, never on the bare phrase.

## 2. The rig and the preconditions

D-HU as head unit, D-POCO as the phone, the connection round 1 used: Native AA
(`wifi-connection-mode=3`), poke scoped to D-POCO's MAC in `native-poke-bt-macs`.

**§7a's new `### Fresh installs, and the three things that wake up with one` now covers what round 1
had to discover**, so it is cited rather than restated: `onboarding-version=2`,
`native-driver-selection-mode=0`, `native-poke-all-paired=false`. Apply all three. If this round can
install over round 1's build rather than uninstalling, none of them will have moved.

`log-level=2` (INFO) again. The new line is `AppLog.i` with no `LOG_VERBOSE` guard, checked with
`grep -F` against `8a1968a3`.

Restore from a full `settings.xml` backup at the end, as round 1 did.

## 3. Settings keys this round needs

| Key | Type | Element |
|---|---|---|
| `loading-screen-show-pill` | boolean | `<boolean name="loading-screen-show-pill" value="true" />` |
| `auto-start-loading-screen` | boolean | `<boolean name="auto-start-loading-screen" value="true" />` |
| `wifi-connection-mode` | int | `<int name="wifi-connection-mode" value="3" />` |
| `log-level` | int | `<int name="log-level" value="2" />` |

`loading-screen-show-pill` defaults to true when absent, so V1 arm 2 writes `false` explicitly.

## 4. The lines that decide every run

From `8a1968a3` with `grep -F`, all `AppLog.i`:

```
AapProjectionActivity: status pill step: <STAGE>
AapProjectionActivity: status pill step: <STAGE> (not shown, the pill is switched off)
AapProjectionActivity: status pill step: <STAGE> (not shown, the loading screen is down)
MainActivity: status pill step: <STAGE>
Auto-connect overlay: handshake complete, handing off to projection
HeadUnit for Android Auto (tm) - Copyright 2011-2015 Michael A. Reid.
Hiding loading overlay after first video frame
```

The window under test opens at the copyright banner and closes at
`Hiding loading overlay after first video frame`.

---

## V1. The pill on the projection's loading screen. This is the point of the round.

**Why.** The pill is meant to carry across the handoff so the step stays readable instead of the
user getting a bare spinner on the second screen. Nothing has ever seen it there.

**Run.** Two arms, one connection each, recording throughout:

```bash
adb shell screenrecord --time-limit 40 /sdcard/v1.mp4 &
# trigger the connection, let the picture come up
adb pull /sdcard/v1.mp4
```

*Arm 1:* `loading-screen-show-pill=true`.
*Arm 2:* `loading-screen-show-pill=false`.

**Do not poll `screencap` to catch the window.** That is what failed in round 1, and rule 8 now
forbids the parallel form of it outright. The recording is the visual instrument; the log is the
primary one.

**Report.** Per arm:

1. Every `AapProjectionActivity: status pill step:` line, with timestamps.
2. The copyright banner and `Hiding loading overlay after first video frame` timestamps, and the
   window between them in milliseconds.
3. `Auto-connect: begin` to the handoff line, in seconds, as round 1 reported it.
4. Frames from the recording inside the window, extracted by time offset rather than by polling.

**PASS, arm 1.** At least one `AapProjectionActivity: status pill step: <STAGE>` with no
parenthesised reason, timestamped inside the window. `STARTING_PROJECTION` is the expected stage.

**PASS, arm 2.** The same line present but carrying `(not shown, the pill is switched off)`, and no
pill in any frame.

**The discriminator in the recording needs no timestamp sync.** MainActivity's pill carries an X on
its right; the projection's does not, because that screen has nothing to cancel. So a bottom-centre
pill **without** an X is the projection's, and one frame of it settles arm 1 on its own. With the
pill off, MainActivity's loading screen shows its own Cancel button instead and the projection's
shows neither, which separates arm 2's two screens the same way.

**If the window is near zero** because the phone delivered video immediately, that is **not a FAIL**
and not an UNTESTABLE either: the log line is the instrument and it fires whether or not a frame was
ever drawn. Report the window anyway and say the frames could not corroborate it. Round 1 measured
the window at ~275 ms and the connect at 8.9 s to 14 s, so pair the two numbers again.

**If arm 1 prints `(not shown, the loading screen is down)`**, quote it: that is a genuinely
different result meaning the overlay was already gone when the pill was asked for, and it would be
the first evidence of a real defect here.

## V2. The regression the new commit could have caused

**Why.** `8a1968a3` touches one function in the projection activity, but round 1's coverage was
measured on `9cca8ddc` and should not be assumed forward for free.

**Run.** Two scripted launches on D-HU, no phone needed, as round 1's Stage A did:

```bash
adb shell am force-stop com.andrerinas.headunitrevived
adb shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity \
  --es launch_source "Bluetooth auto-start"
```

*Arm 1:* `auto-start-loading-screen=true`. *Arm 2:* `auto-start-loading-screen=false`.

**PASS.** Arm 1 prints `Auto-connect: begin (Bluetooth auto-start, mode=OVERLAY)`, arm 2 prints
`mode=PILL`. These are round 1's A1 arm 2 and A4 unchanged, and both took seconds there.

## 5. What this round cannot settle

- **Whether the projection's loading screen should exist at all.** It should; it is the phone's pace
  before the first frame, not ours. Round 1's operator watched the two screens hand over with no home
  screen between them, which is what the fix was for.
- **Custom loading media continuity across the handoff.** `pendingLoadingMediaPositionMs` hands the
  video position over so it resumes rather than restarting. Grading it needs media set and a
  frame-accurate comparison, and is still its own round. Leave `loading-screen-media-*` clear.
- **Anything round 1 settled.** See below.

## 6. Not repeated

A1, A2, A3 both arms, A4, A5 and B1 all PASSed on `9cca8ddc` and stand. V2 re-runs two of them only
because the commit moved. B2 arm 2's window 1 also PASSed and is not repeated; only the window this
brief names is open.

## 7. Report back

1. **Arm 1: does an unqualified `AapProjectionActivity: status pill step:` line fall inside the
   window.** That is the shipping answer, and the branch opens a pull request on it.
2. **The window in milliseconds, beside the connect time in seconds.** The pair says whether the
   frames could have corroborated the log at all.
3. **V2: `mode=OVERLAY` then `mode=PILL`.** Both, or the log line disturbed something.
