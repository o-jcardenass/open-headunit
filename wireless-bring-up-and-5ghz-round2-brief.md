# wireless-bring-up-and-5ghz: round 2 brief

**Brief revision 2, 2026-09-05.** If your copy does not carry this block it is revision 1, and the
round now builds a different branch. Re-read **§1, §3, §5 and R0 and R6** rather than the whole file;
R1, R5, R7, R8, §7 and §8 are unchanged.

What changed: the round is now built from **`testing/wireless-plus-automation`**, the candidate merged
with the automation command surface, so the rig has the automation receiver and the build stamp. That
replaces R6's timed `input tap` with an exactly-timed broadcast, replaces R0's DEX grep with the
build stamp, and moves the unit gate from 1358 to **1389**. §3 is new. Revision 1 said this branch had
no automation receiver; that was true of the candidate branch alone and is no longer how the round is
built.

This file is append-only from here: revisions arrive as new commits and a `git pull` fast-forwards, so
no brief you have already read changes under you.

---

Round 1 passed all five runs and the branch's three original changes are hardware-clear. Two commits
have landed since, and this round is about them. One is a new fix that came out of a user's own
failed-connection logs, not out of round 1; the other closes the dead-log finding round 1 raised.

Four of the six runs are new. R1 and R5 keep their round-1 ids because they are re-runs of the same
runs, and R2, R3 and R4 are not re-run at all.

## 1. Build and baseline

```bash
git fetch fork
git checkout testing/wireless-plus-automation   # 5753dfd9a63c5d2b88a02e6babb60259af7f8059
```

**Build the testing branch, not the candidate branch.** It is the candidate merged with the automation
command surface, which gives the rig its instrument. The merge is clean and adds nothing to what is
under test.

| Input | SHA | Role |
|---|---|---|
| `fix/wireless-bring-up-and-5ghz-channel` | `e68636db` | **the candidate.** Five commits on `main` @ `c4dd2ba1` |
| `feat/automation-command-surface` | `41a3866b` | **the instrument, not the subject.** Two commits on the same `main` |
| `testing/wireless-plus-automation` | `5753dfd9` | the merge of those two — what you build |

The two candidate commits this round is about:

| SHA | What |
|---|---|
| `5ef1012d` | Native AA: one wake poke at a time, and say when one fails |
| `e68636db` | Wireless: say how a USB hold ended, instead of nothing |

**No history was rewritten since round 1.** Round 1's `65014ed8` is still an ancestor, so nothing has
to be re-cloned; `testing/wireless-plus-automation` is a new branch, so check it out fresh.

Unit gate on the testing branch: **1389 / 0** (`testGithubDebugUnitTest`). Round 1 read 1351, the
candidate alone reads 1358, and the automation branch's own tests take it to 1389. Any other count
means the wrong tree was built — stop and say so rather than running the round.

## 2. What this is

**`5ef1012d` — two pokes could reach the same phone at once.** `triggerPoke()` already stood aside for
a manual poke, but `manualPoke()` had no mirror of that guard: it called `pokeJob?.cancel()`, and a
cancel cannot interrupt a blocking `socket.connect()`. So a user pressing the WiFi tile while the
automatic poke was already inside `connect()` produced two concurrent RFCOMM connects to the same
phone. Measured on a user's own capture, a POCO head unit with a Motorola phone, 2026-09-04:

```
16:08:00.296  NativeAA: Attempting active poke to device: motorola edge 30 neo
16:08:00.531  MainActivity.beginAutoConnect | Auto-connect: begin (manual Native-AA poke)
16:08:00.700  NativeAA: Poke via HFP-AG ... failed: read failed, socket might closed or timeout, read ret: -1
16:08:00.728  NativeAA: Poke via HFP-AG ... failed: ... read ret: -1
16:08:00.957  NativeAA: Poke via HSP-AG ... failed: ... read ret: -1
16:08:00.986  NativeAA: Poke via HSP-AG ... failed: ... read ret: -1
```

All four records failed, no session formed, and the user's next press 42 s later worked first try.
Three things changed: a manual poke now waits, bounded at 4000 ms, for a connect already in flight to
the same phone; `pokeDevice()` checks for cancellation between its two records, so a cancelled poke
stops after one instead of spending the second as well; and the poke-failure line moved from DEBUG to
INFO. That last one matters on its own — in the same capture an earlier attempt was undiagnosable
because the log was at INFO, where the attempt printed and its outcome did not.

**`e68636db` — round 1's finding 1, closed.** `AapService: USB handoff settled after <N>ms` was
guarded on `usbDeferralJob != null`, and the recheck coroutine nulls that job before it re-enters, so
the line could never print. Confirmed against round 1's zero occurrences. It is guarded on its own
flag now. While fixing it a second gap appeared: the backstop path, where the hold simply runs out its
8 s budget, would have started claiming the handoff had "settled". A hold that reaches its budget now
says so in its own words, so R5 can be judged either way instead of coming back silent.

## 3. Driving the app

The testing branch carries the automation receiver, so this round is scripted rather than tapped.

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { adb shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit."$@"; }
```

**The package and the action prefix genuinely differ** (`headunitrevived` versus `openheadunit`), and
getting it wrong does nothing at all, silently. Paste the lines, do not retype them.

**`-f 0x00000020` is `FLAG_INCLUDE_STOPPED_PACKAGES`, and it is not optional.** `am force-stop` is
part of every clean run, and Android does not deliver broadcasts to a stopped package even to an
explicit component.

**Every `send` must produce `AutomationReceiver: <action>` at INFO.** If that line is missing the
broadcast never landed, whatever the shell printed, and the run is void rather than a FAIL.

| Command | Why it matters here |
|---|---|
| `ACTION_NATIVE_AA_POKE --es extra_mac <MAC>` | **R6's trigger.** Relays to `AapService.ACTION_NATIVE_AA_POKE`, which is `selectDriver` -> `manualPoke` — the exact path the WiFi tile takes into the code under test |
| `ACTION_LOG_MARKER --es text <label>` | Writes `AutomationMarker: <label>` at WARN. Put one before and after every step |
| `ACTION_QUERY_STATE` | Build identity in one call; the reply comes back on `data=` and carries `commit` |

`ACTION_SET_SETTINGS` and `ACTION_SET_LOG_LEVEL` are gated behind `allow-external-configuration` and
**this round does not use them** — settings still go into `settings.xml` with the app stopped, per §1
of the template and §4 below.

**What the broadcast does not do:** it goes straight to `selectDriver` and never reaches
`HomeFragment.connectToNativeDevice`, so `HomeFragment: Connecting to Native-AA device:` and
`Auto-connect: begin` do **not** appear, and there is no pill or overlay. None of R6's criteria need
them; do not read their absence as a fault.

## 4. What is different about this round

- **R6, R7 and R8 want the phone's Bluetooth OFF**, which is the opposite of every previous round on
  this thread. That is deliberate and it is what makes the race reproducible: a poke at a phone whose
  radio is off still passes the wake guard (the head unit holds no hands-free link, so it pokes), and
  its `socket.connect()` then blocks for the ~3.05 s a refusal takes. That block is the window the
  timed tap has to land in. With the phone's radio on, a poke connects in ~2.2 s and the window is
  both shorter and harder to aim at.
- **No session forms in R6, R7 or R8, and that is not a failure.** They are judged on poke lines only.
- **R6 is timed, but not tapped.** A logcat watcher fires an `ACTION_NATIVE_AA_POKE` broadcast the
  instant the automatic poke enters `connect()`, so the round stays inside §0's script-it rule and
  nothing depends on human reaction time or on tile coordinates.
- **`log-level=2` (INFO) for R6, R7 and R8.** Higher than round 1's VERBOSE, and deliberately so:
  proving the failure line is visible at the shipping default is the whole of R7. Every decisive line
  in this brief is a plain `AppLog.i` with no `LOG_VERBOSE` guard — checked in the source, not
  assumed. R1 keeps `log-level=0` so its timing is comparable with round 1's.
- **R5 runs on the POCO as head unit** per §7b, and its USB link was unstable all through round 1.
  Its INCONCLUSIVE arm is pre-registered below; do not spend the round fighting the dongle.
- The rig baseline's `native-driver-selection-mode=2` still auto-picks an absent phone, exactly as
  round 1 found. Set it to `0` for every run here and restore it after, the same as last time.

## 5. Settings keys this round needs

| Key | Element | Note |
|---|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | Native AA, every run |
| `native-driver-selection-mode` | `<int name="native-driver-selection-mode" value="0" />` | every run; restore the rig's `2` afterwards |
| `log-level` | `<int name="log-level" value="2" />` | R6, R7, R8. **R1 uses `0`** |
| `native-poke-bt-macs` | `<set name="native-poke-bt-macs"><string>DC:B7:2E:5E:4E:59</string></set>` | R6/R7/R8, so the loop pokes only the POCO |
| `wifi-5ghz-channel` | `<int name="wifi-5ghz-channel" value="0" />` | automatic, every run |

`native-poke-bt-macs` is a **StringSet**, which §1's element table does not cover. Its removal needs
its own element-scoped pattern, since the `<[a-z]+ ... />` and `<string>` forms in the write template
will not match it:

```bash
adb shell run-as $PKG sh -c '
  f=shared_prefs/settings.xml
  sed -i -E "s#<set name=\"native-poke-bt-macs\">.*</set>##g" $f
  sed -i "s|</map>|<set name=\"native-poke-bt-macs\"><string>DC:B7:2E:5E:4E:59</string></set></map>|" $f
'
adb shell run-as $PKG cat shared_prefs/settings.xml   # verify, every time
```

Two traps on this key. It **seeds itself from the auto-start list the first time it is read** and
writes that back, so read it after a launch to see what it actually holds. And never leave a
`settings.xml.bak` beside the file: SharedPreferences reads a stray `.bak` as an aborted write and
restores it over the edit.

## 6. The lines that decide every run

Verified with `grep -F` against `e68636db`; each appears exactly once in `app/src/main/java`.

```
NativeAA: another poke is already connecting to
waiting for it rather than opening a second socket.
NativeAA: Calling socket.connect() for
NativeAA: Attempting active poke to device:
NativeAA: Attempting manual poke to
NativeAA: Successfully poked
AapService: USB handoff settled after
the USB attempt did not settle within
AapService: a USB projection attempt is in flight
AutomationReceiver: 
```

The failure line is a format string, so grep the fixed part: `NativeAA: Poke via ` and, on the same
line, ` failed: `. Its full emitted shape is

```
NativeAA: Poke via HFP-AG to POCO X3 NFC (DC:B7:2E:5E:4E:59) failed: read failed, socket might closed or timeout, read ret: -1
```

**The new "another poke" line contains an em dash**, U+2014, with one space each side. Copy it, do not
retype it; a hyphen will not match. Its full shape, with only the phone's name and no address:

```
NativeAA: another poke is already connecting to POCO X3 NFC — waiting for it rather than opening a second socket.
```

Also used and already familiar: `WirelessServer: Incoming connection detected` and
`SSL handshake complete`. The group line is composed from a band label, so grep the fixed part
`createGroup SUCCESS!` — it emits as `WifiDirectManager: 5GHz createGroup SUCCESS!` on this rig.

## 7. Runs

### R0: build gate

Standard. The build stamps its own commit:

```bash
./gradlew :app:assembleGithubDebug        # prints "Building from commit: 5753dfd9a63c"
send ACTION_QUERY_STATE
# the reply JSON on data= must carry "commit":"5753dfd9a63c"
```

Anything ending in `-dirty` means the tree had uncommitted changes when it was built. Stop and clean
it: a dirty build cannot be tied to this brief.

Belt and braces, because the stamp is only as good as the tree it was built from — the candidate adds
a class that cannot exist before `5ef1012d`, and the instrument adds two of its own:

```bash
PKG=com.andrerinas.headunitrevived
adb shell pm path $PKG                       # pull that apk, then
unzip -p <apk> 'classes*.dex' | strings | grep -cF 'PokeOverlapPolicy'        # > 0, the candidate
unzip -p <apk> 'classes*.dex' | strings | grep -cF 'AutomationCommandPolicy'  # > 0, the instrument
```

Round 1's APK (md5 `f44bc175255c6983c2ee69246da59c0b`) reads **0** on all of them if it is still
around. Unit gate **1389 / 0**, candidate md5 recorded, installed with `adb install -r`.

---

### R6: a manual poke no longer races an automatic one. **The point of the round.**

Setup, in this order:

1. Settings per §5 with `log-level=2`, POCO's MAC in `native-poke-bt-macs`, app stopped.
2. **POCO's Bluetooth OFF.** Its WiFi state does not matter.
3. Launch, and **let the WiFi Direct group form first** — wait for `createGroup SUCCESS!`. This
   is load-bearing: if credentials are not ready, `manualPoke()` runs a pre-flight that can burn
   4000 ms and swallow the whole overlap window before it ever reaches the wait under test.
4. Arm the manual poke against the automatic one. The broadcast lands in single-digit milliseconds,
   so the window is the full ~3 s the automatic `connect()` blocks for:
   ```bash
   send ACTION_LOG_MARKER --es text r6-armed
   adb logcat -v time | grep -m1 --line-buffered -F "Calling socket.connect() for POCO X3 NFC" \
     && send ACTION_NATIVE_AA_POKE --es extra_mac DC:B7:2E:5E:4E:59
   send ACTION_LOG_MARKER --es text r6-fired
   ```
   `AutomationReceiver: ACTION_NATIVE_AA_POKE` must appear, or the run is void.

**PASS**, all four:

1. `NativeAA: another poke is already connecting to POCO X3 NFC — waiting for it rather than opening a second socket.`
   appears **exactly once**. This one line is the fix.
2. Between the automatic poke's `Calling socket.connect()` and its own outcome line, there is **no
   second** `Calling socket.connect()` for the same MAC. The two connects are serialised, not
   overlapped.
3. The manual poke's `NativeAA: Attempting manual poke to POCO X3 NFC...` comes **after** the
   automatic attempt's outcome, not between its two records.
4. The failure lines that do appear each name a profile and a reason.

**FAIL:** two `Calling socket.connect()` lines for the same MAC overlap in time, or the waiting line
never appears although the broadcast demonstrably landed inside the window.

Pre-registered non-failures, so neither is read as a result:

- **The poke missed the window.** If `Attempting manual poke` lands more than ~3 s after the automatic
  `Calling socket.connect()`, or before it, there was no overlap to serialise. That run is **void** —
  repeat it, do not record it as PASS or FAIL. Report how many attempts it took.
- **A handshake landed during the wait.** The manual round loop breaks and no `Attempting manual poke`
  prints at all. Also void; it should not happen with the phone's radio off, so if it does, say so.
- The broadcast bypasses the home screen entirely, so the selector dialog cannot appear and
  `native-driver-selection-mode` does not affect this run. It is still set to `0` for consistency
  with R1.

Report the delay from `AutomationReceiver: ACTION_NATIVE_AA_POKE` to `Attempting manual poke`.

---

### R7: a poke failure says why, at the default log level. **New.**

Same setup as R6, POCO's Bluetooth still off. No tap needed; just let the automatic loop run one pass.

**PASS:** with `log-level=2`, a line matching `NativeAA: Poke via ` ... ` failed: ` appears and carries
a reason, e.g. `read failed, socket might closed or timeout, read ret: -1`.

**FAIL:** `Calling socket.connect()` appears with no outcome line of any kind at this level, which is
the pre-fix behaviour.

This is a one-line check and it can be read out of R6's own capture if that run is clean; say so
rather than running it twice.

---

### R8: a cancelled poke stops after one record. **New.**

Read out of R6's capture — no separate setup.

**PASS:** the automatic poke that the manual one cancelled logs **at most one** `Calling
socket.connect()`, and no `HSP-AG` connect after its `HFP-AG` attempt. Before this change a cancelled
poke spent both records.

**This run is why R6 can pass at all**, so read them together. The manual poke's wait is bounded at
4000 ms, but two refused records take ~6.1 s — longer than the bound. It only fits because the manual
poke cancels the automatic one first, and the cancellation check then stops it after its first record
instead of letting it open a second. If R8 fails, expect R6 to fail with it, and the pair says the
cancellation check is the part that did not take.

**INCONCLUSIVE** if R6 came back void: with no cancellation there is nothing to observe. Do not
manufacture one.

---

### R1 (re-run): an ordinary Native AA session still forms. **Regression guard.**

Two commits landed on the wake path every Native AA session uses, so this is the run that protects
the branch. POCO's Bluetooth **back on**, `log-level=0`, `native-poke-bt-macs` left as set, everything
else as round 1's R1.

**PASS:** `createGroup SUCCESS!`, `WirelessServer: Incoming connection detected`,
`SSL handshake complete`, and a picture. Service start to `SSL handshake complete` is not more than
~1.5x round 1's **21.2 s**, i.e. under about 32 s.

**FAIL:** no picture, or that time is over the bound.

`MATCH! Starting AapService` = 1 is the benign poke self-wake, as in round 1; it is not churn unless a
second `createGroup SUCCESS` comes with it.

---

### R5 (re-run): the USB hold now says how it ended. **The dead-log fix.**

POCO as head unit per §7b, port free, wireless adb, a phone plugged in over USB at launch. It already
carries a debug build from round 1, so reinstall the candidate over it.

**PASS:** exactly one of these two lines appears, and wireless ends up armed either way:

- `AapService: USB handoff settled after <N>ms — arming wireless now`, N under 8000 — the USB attempt
  resolved before the budget; or
- `AapService: the USB attempt did not settle within <N>ms — arming wireless anyway`, N at or just
  over 8000 — the backstop, which is what round 1 actually hit.

Round 1 saw **neither**, because the only line that then existed was unreachable. Either one is a
PASS; say which, since it also says which path the rig took.

**FAIL:** the hold happens — `a USB projection attempt is in flight` before any `createGroup` — and
then neither line appears.

**INCONCLUSIVE** if the deferral never engages at all, which happens when nothing is on the bus at the
instant `onCreate` runs. Round 1 needed the launch timed to a device-present window to get it. Two
attempts is enough; if the dongle will not hold still, say so and move on.

## 8. Do not re-run

- **R2, R3 and R4.** Settled in round 1 and untouched by either new commit. Their errata stand: the
  5 GHz walk ladder and the regulatory dump are sub-Android-10 only and unreachable on this API-34
  rig, `StationStandDown.restore()` always logs the WARN branch here, and R4's live form cannot be
  reached because the MT50 honours pinned channels.
- Anything from the driver-selection rounds. Neither commit touches the selector, the wake picker or
  the exclusive gate.

## 9. Report back

1. **R6's outcome**, and the delay from `AutomationReceiver: ACTION_NATIVE_AA_POKE` to
   `Attempting manual poke`. If it took several attempts to land inside the window, how many.
2. **Whether R7's failure line carried a reason at `log-level=2`.** One line, quoted.
3. **R1's service-start to `SSL handshake complete` time**, against round 1's 21.2 s.
4. **Which of R5's two lines came back**, or that the deferral never engaged.

**The one thing to watch for that is nobody's criterion:** whether
`NativeAA: a chosen driver's wake poke is running — not replacing it with the multi-device loop.`
appears after R6's manual poke wins. It should, and it means the automatic loop is correctly staying
out of the way for the three manual rounds. Its absence is not a FAIL, but it is worth a line in the
results, because it is the other half of the same mutual-exclusion the round is testing.

## Evidence to capture

Into `evidence/wireless-bring-up-and-5ghz-round2/`:

- full logcat per run, gzipped: `hu_r6.txt.gz`, `hu_r7.txt.gz`, `hu_r1.txt.gz`, `poco_r5.txt.gz`
- the decisive excerpt per run as `r<N>_console.txt`
- `settings-backup.xml` from each unit before anything is written
