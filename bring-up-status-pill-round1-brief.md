# bring-up-status-pill: round 1 brief

**Candidate:** `fork/feat/bring-up-status-pill-and-poke-readiness` @ `5aeb1a75`
**Baseline:** none needed. See §1.
**Gate:** 1609 tests / 0 failures
**Read `TESTING-TEMPLATE.md` first.** This brief only says what is different about this round.

---

## 0. What this is and why it exists

The home screen has a small translucent pill that reads "Android Auto is starting…". Until this
branch it appeared only while an *auto-connect* attempt was running, it carried one fixed line, and
a 30 s watchdog hid it again. A Native AA bring-up takes 10 to 30 s of standing the station down,
creating a P2P group, opening Bluetooth listeners, poking the phone and running the WPP handshake,
and for all of that the user saw one unchanging string or nothing at all. There was no way to tell
working from wedged.

The branch makes the pill appear whenever the connection stack is armed and gives it a second line
naming the step. The steps come from a new `ConnectionStageTracker`, a process singleton holding a
`StateFlow` that about 25 call sites report into, each one sitting beside an `AppLog` line that
already existed. A pure `ConnectionStagePolicy` decides whether a reported step is allowed to
replace the one on screen: ranks only move forward inside one attempt, so the WiFi Direct
group and credential callbacks firing three or four times per group cannot drag the display
backwards, while the few sites that mark a genuine restart bypass that rule.

Two commits: `4e409bca` is a separate Native AA fix (the wake poke now stops when the Android Auto
listener has been closed) and is not what this round is about, though R1 exercises it incidentally.
`5aeb1a75` is the pill.

## 1. Build and baseline

```bash
git fetch fork
git checkout feat/bring-up-status-pill-and-poke-readiness   # 5aeb1a75, 2 commits on main f5c2e986
```

History was rewritten once: the pill commit was amended after its first push to add the
`status pill step:` line this whole round is graded on. `46dbfd7d` no longer resolves. Take
`5aeb1a75` from this thread's README row when you start.

**No baseline APK.** Every condition below is stated over a log line that does not exist in `main`,
so there is nothing to A/B. R4 is the discriminator instead: it is a settings change on the
candidate that must produce a *different* step sequence, which is what proves the steps track the
live code path rather than a canned list.

Build with `hur-wifi-test-scripts/build_hur.sh` per §5. **Identity check**: md5 alone is not enough
here, so confirm the DEX carries a class the older builds cannot have:

```bash
PKG=com.andrerinas.headunitrevived
adb shell pm path $PKG                       # pull that apk, then
unzip -p <apk> 'classes*.dex' | strings | grep -F 'ConnectionStageTracker'
```

R0 fails if that grep is empty.

## 2. What is different about this round

**This is a UI change, and §0 forbids a verdict that needs a person.** So every run below is graded
on `MainActivity: status pill step:`, an INFO line the branch adds that records exactly what the
pill is showing at the moment it changes. The screenshot in R5 is corroboration, not the verdict.

**The pill is now up from app launch on most configurations.** `AapService.onCreate` arms the
wireless stack unconditionally, so the pill should be on screen within a second or two of every
launch and stay there until a session forms. That is the intended behaviour, not a stuck indicator.
Which stage it opens on depends on how far the stack got before the home activity started
listening, so do not treat a first value later than `ARMED` as a fault by itself.

**If the change did nothing, `status pill step:` appears zero times.** Report that count for every
run, and pair it with the run's own timing number, so a green cannot come from a run where the
bring-up never actually reached the code under test.

R6 is expected to be INCONCLUSIVE on a wireless-only rig. Said up front so it is not read as a
failure.

## 3. Settings keys this round needs

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA, for R1 to R3 and R5 |
| `wifi-connection-mode` | int | `1` | Headunit Server / NSD, for R4 only |
| `log-level` | int | `2` | INFO. Every line this round cites is an unguarded `AppLog.i`, verified against the branch, so VERBOSE only costs buffer |

Use `set_hu_prefs.sh` for any run that writes more than one key (§5). Write with the app stopped and
read the file back before launching, every time.

## 4. The lines that decide every run

Verified with `grep -F` against `5aeb1a75`. Match on these exact substrings.

| Line | Means |
|---|---|
| `MainActivity: status pill step: ` | the pill changed what it shows. The value is a stage name or `hidden` |
| `WifiLauncher: Initializing WiFi Mode: ` | the stack armed. `ARMED` must follow it |
| `WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any` | the restart edge that is allowed to rewind the display |
| `WifiDirectManager: Attempting createGroup for Native AA` | `CREATING_NETWORK` |
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | `WAITING_FOR_PHONE` |
| `NativeAA: Attempting active poke to device` | `WAKING_PHONE` |
| `SSL handshake complete` | the session formed |

The fourteen stage names, in rank order, are `ARMED`, `USB_ATTACHED`, `PREPARING_NETWORK`,
`SEARCHING`, `USB_SWITCHING`, `CREATING_NETWORK`, `WAITING_FOR_PHONE`, `WAKING_PHONE`,
`PHONE_ANSWERED`, `SENDING_CREDENTIALS`, `PHONE_JOINING`, `CONNECTING`, `SECURING`,
`STARTING_PROJECTION`. `USB_ATTACHED` and `PREPARING_NETWORK` share a rank, as do `USB_SWITCHING`
and `CREATING_NETWORK`; nothing else does.

The one command that produces the round's main evidence:

```bash
grep -F 'MainActivity: status pill step: ' rN.txt
```

## 5. Runs

### R0: build and unit-test gate

Build the candidate, run `./gradlew :app:testGithubDebugUnitTest`, install with `adb install -r`.

**PASS** requires all of: 1609 tests, 0 failures; the `ConnectionStageTracker` grep in §1 is
non-empty; the installed md5 matches what was built.

### R1: Native AA cold bring-up to projection. **This is the point of the round.**

`wifi-connection-mode=3`. Clean-run protocol (§4 of the template) exactly: phone into airplane mode
first, capture started before the launch, launch, settle, phone out of airplane mode, 90 s.

**PASS** requires all of:

1. `status pill step:` appears **6 or more** times.
2. The first value is one of `ARMED`, `PREPARING_NETWORK` or `CREATING_NETWORK`. It is not
   necessarily `ARMED`: the line is written by the home activity, and the stack can arm and take a
   step or two before that activity is listening, in which case the earliest steps are set but
   never logged. Anything later than `CREATING_NETWORK` as the first value is a FAIL, because it
   means the whole early half of the sequence is invisible to a user watching a cold start.
3. `WAITING_FOR_PHONE` and `WAKING_PHONE` both appear, and `WAKING_PHONE` is not before
   `WAITING_FOR_PHONE`.
4. The last value in the capture is `STARTING_PROJECTION`.
5. Reading the values in order and ignoring repeats, the rank never decreases **except**
   immediately after a `startNativeAaQuietHost() requested` line. Any other decrease is a FAIL and
   is the single most valuable thing this round can find.
6. `SSL handshake complete` appears, proving the bring-up actually completed.

Report the wall-clock from the first `status pill step:` line to the `STARTING_PROJECTION` one,
and the full ordered
list of values with timestamps. That list is the round's primary artefact even on a PASS.

### R2: the pill does not cover the home controls

The pill used to be transient and is now permanently on screen, anchored bottom-centre. This checks
it does not sit on top of anything the user needs to press.

With the app launched and the pill up (confirm `ARMED` in the capture first), and without connecting
a phone:

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml r2-ui.xml
```

**PASS** requires: the node whose `resource-id` ends `auto_connect_pill` is present, and its
`bounds` rectangle does not intersect the bounds of any node that is `clickable="true"` other than
itself. Report the pill's bounds and the bounds of the nearest clickable node below or beside it, as
numbers.

If the head unit's screen has a non-default UI scale set, say which, because the app applies it as a
density override at activity creation and it moves everything in this run.

### R3: a failed attempt falls back to the resting line

`wifi-connection-mode=3`. Same as R1 but **the phone stays in airplane mode for the whole run**.
Let it run 120 s, which is past the 30 s watchdog.

**PASS** requires all of: `status pill step:` appears 3 or more times; the capture reaches at least
`WAITING_FOR_PHONE`; and after the highest stage it reached, a later `status pill step: ARMED`
appears. The pill must not be left showing a step that is no longer happening, and must not go
`hidden` while the stack is still armed.

This is the run that exercises the fallback added to the auto-connect end path. If `ARMED` never
comes back, quote the last three `status pill step:` lines with timestamps.

### R4: a different mode produces a different sequence (the discriminator)

`wifi-connection-mode=1`, everything else as R1, phone brought out of airplane mode as usual.

**PASS** requires all of: `SEARCHING` appears; `WAKING_PHONE` and `SENDING_CREDENTIALS` appear
**zero** times; `status pill step:` appears 2 or more times.

A green here plus a green R1 is what proves the steps are driven by the live path. If this run shows
the R1 sequence, the tracker is reporting something canned and R1's PASS is worthless.

### R5: the pill actually renders two lines

During R1, while the capture shows a stage between `CREATING_NETWORK` and `PHONE_JOINING`:

```bash
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png r5-pill.png
```

Attach the image. Describe what the two lines read. **This run has no PASS condition of its own**:
§0 forbids a verdict that needs an eye, so record the observation and let R1 carry the verdict. Say
explicitly if the second line is absent, truncated, or overlaps the first, because none of that is
visible in the log.

### R6: USB

Only if a USB host cable and a phone that will enter accessory mode are available. Attach the phone
over USB with `wifi-connection-mode` left at 3.

**PASS** requires: `USB_ATTACHED` or `USB_SWITCHING` appears at least once.

Expected **INCONCLUSIVE** on a wireless-only rig. Do not build a substitute for it.

## 6. Do not re-run

The rank and rewind rules are settled by JVM tests (`ConnectionStagePolicyTest`,
`ConnectionStageTrackerTest`, 12 cases) and R0 covers them. Do not spend rig time constructing a
rewind by hand; R1 condition 5 catches it if it happens naturally.

Nothing about the Native AA handshake itself is under test here. If the session fails to form for a
reason unrelated to the pill, that is a discard under §4, not a FAIL of R1.

## 7. Report back

Beyond §7 of the template, these decide whether this ships:

1. **The ordered list of `status pill step:` values from R1, with timestamps.** Everything else is
   secondary.
2. **Whether any rank decrease occurred outside a `startNativeAaQuietHost()` line** (R1 condition 5).
3. **The pill and clickable bounds from R2**, as numbers.
4. **Whether `ARMED` came back after the failed attempt in R3.**
5. The `status pill step:` count for every run, including the zeroes.

Two open questions the round does not settle, worth a note in *Anything the brief did not ask about*
if you form a view: whether a spinner that turns for minutes on the resting line reads as stuck
rather than ready, and whether the second line is legible at this unit's screen size and UI scale.
