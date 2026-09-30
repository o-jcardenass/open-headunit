# auto-start-loading-screen, round 1 brief: the app opens on the loading screen, and stays on it

## What this round is

When the app starts itself, it is supposed to show the "Android Auto is starting" loading screen
rather than the home screen with the four big buttons. A build from 3.3.0-beta3 already did that for
USB, and a reporter came back saying the home screen still appeared and that it felt **worse** than
before. Their log says why, and it is three separate faults rather than one:

- **A frame of the home screen is uncovered in the middle of the handoff.**
  `MainActivity.endAutoConnect(success = true)` called `bringProjectionToFront()` and then hid the
  loading overlay in the next statement. The raise is asynchronous: in that log the handoff line is
  at `15:33:33.481` and the projection activity's own first line at `15:33:33.594`, so the home
  screen was on show for ~113 ms between two screens that were meant to be continuous.
- **The starting screen is then shown a second time.** The projection activity has a loading overlay
  of its own and held it from `33.594` to `37.473`, 3.9 s, restarting any custom media from the top.
  With the home frame in between, that reads as two screens rather than one.
- **Only USB ever got the loading screen.** `handleLaunchIntent` hard-coded the small status pill for
  the Bluetooth launch source, nothing acted on the WiFi one, and `MainActivity` is `singleTask` with
  no branch in `onNewIntent` at all, so a USB attach against an already-open app did nothing.

The candidate fixes all three, extends the loading screen to every automatic launch, and puts the
status pill on **both** loading screens so the wait is narrated rather than a bare spinner. Two new
settings gate it.

**None of this is hardware-measured.** Every claim above is from source and from that reporter's log.
The three faults are all in code that runs the same way on every transport, so this round does not
need a particular connection mode to grade them.

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `feat/auto-start-loading-screen` | `9cca8ddc` | 1 on `main` `80a81099` | 2176 tests, 0 failures |
| Baseline | none needed | | | |

```bash
git fetch fork
git checkout -B auto-start-loading-r1 fork/feat/auto-start-loading-screen
./gradlew :app:testGithubDebugUnitTest   # 2176, 0 failures
```

One commit on top of `main`, no history rewritten, nothing to reset. One APK, installed on D-HU.

**No baseline APK is needed.** A4 is a positive control that reproduces the old behaviour from a
settings change on the candidate itself.

## 2. The rig and the preconditions

**D-HU as head unit throughout, USB-attached to the PC.** Stage A needs nothing else and no phone.
Stage B needs D-POCO.

- **`log-level=2` (INFO) is enough for every line this round reads**, and is preferred here: all nine
  lines below are `AppLog.i` and none is wrapped in a `LOG_VERBOSE` guard (checked with `grep -F`
  against `9cca8ddc`). D-HU prints `WifiDirectManager.getWifiDirectMac | Found interface:` once per
  interface per callback and carries 18 of them, so VERBOSE costs evidence here without buying any.
- **Leave `connection-modes` absent.** An empty selection means everything is selected, which is what
  the runs below assume.
- **`wifi-connection-mode=3` for all of Stage A.** Not because the round is about Native AA, but
  because it is the one mode where `BtAutoStartRearmPolicy.launchesSelfMode` returns false with
  wireless selected, so A1's Bluetooth arm does not also kick Self Mode off and muddy the log. It
  also arms the wireless stack, which is what gives A3 a reported step to hold on.
- **Clear `native-aa-wake-damage-verdict` to `0` before Stage A** and clear `auto-start-bt-macs` and
  `native-poke-bt-macs`. No phone should be poked this round; Stage A wants the stack armed and
  waiting, not connecting.
- **No custom loading media for A1 to A5 and for B2 arm 1.** Clear `loading-screen-media-path` and
  `loading-screen-media-type` so the default spinner is what is on screen.
- Restore each unit from a full `settings.xml` backup between stages.

**The scripted lever this round leans on.** `MainActivity` is `android:exported="true"` and reads its
launch source from a plain string extra, so every automatic launch can be produced headlessly:

```bash
adb shell am force-stop com.andrerinas.headunitrevived
adb shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity \
  --es launch_source "USB auto-start"
```

The four accepted values are exactly `Boot auto-start`, `USB auto-start`, `WiFi auto-start` and
`Bluetooth auto-start`. **Verify this works before running the rest of Stage A**: the first
`am start` should produce `App launched via: USB auto-start`. If it does not, Stage A is dead and
should be reported INCONCLUSIVE rather than FAIL. One side effect to expect and ignore: a synthetic
launch source also suppresses the boot-loop guard clear, which nothing here grades.

## 3. Settings keys this round needs

| Key | Type | Element |
|---|---|---|
| `auto-start-loading-screen` | boolean | `<boolean name="auto-start-loading-screen" value="true" />` |
| `loading-screen-show-pill` | boolean | `<boolean name="loading-screen-show-pill" value="true" />` |
| `auto-connect-last-session` | boolean | `<boolean name="auto-connect-last-session" value="true" />` |
| `auto-connect-single-usb` | boolean | `<boolean name="auto-connect-single-usb" value="false" />` |
| `auto-start-self-mode` | boolean | `<boolean name="auto-start-self-mode" value="false" />` |
| `auto-connect-delay-seconds` | int | `<int name="auto-connect-delay-seconds" value="0" />` |
| `wifi-connection-mode` | int | `<int name="wifi-connection-mode" value="3" />` |
| `log-level` | int | `<int name="log-level" value="2" />` |

Both new keys default to **true** when absent, so A4 and B2 arm 2 must write `false` explicitly
rather than deleting the element.

## 4. The lines that decide every run

Copied from `9cca8ddc` with `grep -F`, all `AppLog.i`:

```
App launched via: <source>
Auto-connect: begin (<reason>, mode=<PILL|OVERLAY>)
MainActivity: status pill step: <STAGE>
MainActivity: status pill step: <STAGE> (not shown, the pill is switched off)
Auto-connect: the stack is still working (<STAGE>), holding the loading screen
Auto-connect: nothing answered this attempt (mode=<mode>), ending it
Auto-connect overlay: handshake complete, handing off to projection
HeadUnit for Android Auto (tm) - Copyright 2011-2015 Michael A. Reid.
Hiding loading overlay after first video frame
```

The copyright banner is the projection activity's first line in `onCreate`; it is the far end of the
gap B1 measures.

---

## Stage A - D-HU alone, no phone, nothing connecting

### A1. Every automatic launch opens on the loading screen

**Why.** Before this change only `USB auto-start` reached the full-screen loading overlay. The
Bluetooth source was hard-coded to the small pill over the home screen, and the WiFi source reached
neither.

**Run.** Three arms. For each: force-stop, write the settings above, `am start` with the extra, wait
10 s, read the log.

| Arm | `launch_source` |
|---|---|
| 1 | `USB auto-start` |
| 2 | `Bluetooth auto-start` |
| 3 | `WiFi auto-start` |

**Report.** For each arm, the `App launched via:` line and the `Auto-connect: begin` line, quoted
with timestamps, plus one `adb exec-out screencap -p` taken 3 s after the start.

**PASS.** All three print `mode=OVERLAY`, and all three screencaps show the loading screen rather
than the four home buttons.

**If the change did nothing**, arm 1 would still read `mode=OVERLAY` on its own. Arm 1 is therefore
the regression guard and arms 2 and 3 are the point: arm 2 read `mode=PILL` before, and arm 3
produced no `Auto-connect: begin` line at all.

### A2. A launch into an already-open app

**Why.** `MainActivity` is `singleTask`, so this path goes through `onNewIntent`, which had no branch
for any of this. A USB attach against an open app did nothing at all.

**Run.** Force-stop, `am start` with **no** extra, wait until the home screen is up and the pill has
settled. Then, **without force-stopping**, `am start` again with `--es launch_source "USB auto-start"`.

**PASS.** The second start prints `Auto-connect: begin (USB auto-start, mode=OVERLAY)` and the
screencap 3 s later shows the loading screen. Before this change the second start produced no
`Auto-connect: begin` line.

### A3. The hold, and the bound that is deliberately kept

**Why.** A head unit that boots in an empty car has nothing to connect to. An attempt the app started
itself should keep the loading screen while the stack is still reporting steps, because a wireless
wake has been measured taking 91 s and the old 30 s bound would drop the user on the home screen
mid-bring-up. An attempt a **person** started keeps the 30 s bound, so their own screen comes back.

**Run.** Two arms, 60 s each, no phone in range.

*Arm 1, the app started it.* Repeat A1 arm 2 (`Bluetooth auto-start`) and leave it for 60 s.

*Arm 2, a person started it.* Force-stop, set `auto-connect-last-session=true`, `am start` with **no**
extra, leave it for 60 s.

**Report.** For each arm: every `status pill step:` line, whether
`holding the loading screen` appears and at what elapsed time, whether
`nothing answered this attempt` appears, and a screencap at 45 s.

**PASS.** Arm 1 prints `holding the loading screen` at roughly 30 s and its screencap still shows the
loading screen. Arm 2 prints `nothing answered this attempt (mode=OVERLAY), ending it` at roughly
30 s, never prints `holding the loading screen`, and its screencap shows the home screen.

**Check the premise before grading arm 1.** The hold only engages while the stack is reporting a
step. If arm 1 shows no `status pill step:` line at all, the wireless stack never armed and the arm
is INCONCLUSIVE, not a FAIL. Say which it was.

### A4. Positive control: the toggle off restores the old behaviour

**Why.** A control that is a settings change on the candidate proves the new path is what is doing
the work.

**Run.** `auto-start-loading-screen=false`, everything else as A1 arm 2, one arm.

**PASS.** `Auto-connect: begin (Bluetooth auto-start, mode=PILL)`, and the screencap shows the four
home buttons with the pill at the bottom. That is exactly what this source did before the change.

### A5. An ordinary open is still an ordinary open

**Why.** The loading screen must not cover the home screen for somebody who just opened the app.

**Run.** `auto-connect-last-session`, `auto-connect-single-usb` and `auto-start-self-mode` all
`false`. Force-stop, `am start` with no extra, wait 10 s.

**PASS.** No `Auto-connect: begin` line at all, and the screencap shows the home screen.

---

## Stage B - D-HU as head unit, D-POCO connected

Use whichever mode this rig connects most reliably on D-HU today and **name it in Setup notes**. The
code both runs grade is in `MainActivity.endAutoConnect` and `AapProjectionActivity`, neither of
which knows the transport, so the mode does not change what a PASS means.

### B1. The handoff shows no home screen. This is the point of the round.

**Why.** The ~113 ms of exposed home screen described at the top. The fix is that the loading overlay
is no longer hidden in `endAutoConnect`; it now stays painted until `onStop` runs, which only happens
once the projection activity is actually covering it.

**Run.** Start a screen recording on D-HU, then trigger the connection, and keep recording until the
picture is up:

```bash
adb shell screenrecord --time-limit 30 /sdcard/handoff.mp4 &
# trigger the connection
adb pull /sdcard/handoff.mp4
```

**Report.** Three things:

1. The elapsed time between `Auto-connect overlay: handshake complete, handing off to projection`
   and the `Copyright 2011-2015` banner, in milliseconds.
2. The elapsed time between `Auto-connect: begin` and the handoff line, in seconds. **Pair it with
   the number above**: if the whole connection took under a second the loading screen barely
   rendered, and the recording proves nothing either way. Say so if that happens.
3. Whether any frame of the recording between those two log lines shows the four home buttons. Step
   through it frame by frame rather than watching it.

**PASS.** No frame shows the home screen, and the picture arrives with one continuous starting screen
from `Auto-connect: begin` through to `Hiding loading overlay after first video frame`.

A **FAIL** is any home-screen frame in that window. A brief flash of black between the two activities
is not a FAIL; note it and its duration.

### B2. The pill rides both loading screens

**Why.** The pill was suppressed outright whenever the loading screen was up, so the user got a
spinner and no idea what was happening. It now renders over both the home screen's loading overlay
and the projection's own, behind `loading-screen-show-pill`.

**Run.** Two arms, same connection as B1.

*Arm 1, `loading-screen-show-pill=true`.* Take a screencap during the loading screen before the
handoff, and a second one **after** the projection activity is up but before the first video frame.
The second window is the harder one to catch; the log's gap between the copyright banner and
`Hiding loading overlay after first video frame` says how long you have.

*Arm 2, `loading-screen-show-pill=false`.* Same, one screencap in each window.

**Report.** The four screencaps, plus every `status pill step:` line from both arms.

**PASS.** Arm 1 shows the pill in both windows, with a second line naming a step rather than an empty
pill. Arm 2 shows no pill in either window and its log carries
`status pill step: <STAGE> (not shown, the pill is switched off)`.

**If arm 1's second window cannot be caught**, report the log evidence alone and mark the screencap
half UNTESTABLE. The `status pill step:` lines still separate the two arms.

---

## 5. What this round cannot settle

- **Whether the second loading screen is gone.** It is not, and it is not meant to be: the projection
  activity still holds its own overlay until the first frame, which was 3.9 s in the reporter's log
  and is the phone's pace, not ours. What the candidate changes is that the two screens now look like
  one, because the home frame between them is gone and the pill carries across. B1 and B2 grade that
  continuity, never the duration.
- **Custom loading media continuity.** `AapProjectionActivity.pendingLoadingMediaPositionMs` hands
  the video's position across so it resumes rather than restarting. Grading it needs a video set on
  both screens and a frame-accurate comparison, which is worth its own run and is not in this round.
  Leave the media keys clear.
- **Whether the phone connects at all, and how fast.** Out of scope. If Stage B cannot get a session
  on D-HU today, report Stage A and mark Stage B UNTESTABLE.
- **The 22 locales.** The three new strings are translated in all of them; nothing on this rig reads
  them and no run below changes the device language.

## 6. Not repeated

Nothing. This is the thread's first round.

## 7. Report back

The three numbers that decide whether this ships:

1. **A1: how many of the three arms read `mode=OVERLAY`.** Three is the shipping answer.
2. **B1: the handoff gap in milliseconds, and whether any home-screen frame falls inside it.** A
   number with no frame is the shipping answer; the number alone is not.
3. **A3: whether arm 1 held past 30 s and arm 2 did not.** Both halves, or the hold is either useless
   or a trap.
