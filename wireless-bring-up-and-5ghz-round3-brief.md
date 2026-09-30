# wireless-bring-up-and-5ghz: round 3 brief

Round 2 came back with five runs PASS and one FAIL. R6 is the whole of this round: the fix it asked
for exists now, and nothing else on the branch has changed.

This file is append-only. Corrections arrive as new commits and a `git pull` fast-forwards, so no
brief you have already read changes under you.

Read `wireless-bring-up-and-5ghz-round2-results.md` before starting. R6's setup, its watcher and its
timings all carry over, and this brief only says what is different.

---

## 1. Build and baseline

```bash
git fetch fork
git checkout testing/wireless-plus-automation   # 1f4b85ce5a5936ca6f00ff5f1f52ad82a5a5b6cf
```

| Input | SHA | Role |
|---|---|---|
| `fix/wireless-bring-up-and-5ghz-channel` | `e6ed1ad2` | **the candidate.** Six commits on `main` @ `c4dd2ba1` |
| `feat/automation-command-surface` | `41a3866b` | **the instrument, not the subject.** Two commits on the same `main` |
| `fix/usb-attach-clear-defaults` | `b236103c` | **a third input, and not a subject either.** Two commits on the same `main`; it joined the testing branch after round 2 |
| `testing/wireless-plus-automation` | `1f4b85ce` | the merge of those three, and what you build |

**No history was rewritten.** Round 2's `e68636db` is still an ancestor of the candidate, and round
2's `5753dfd9` is still an ancestor of the testing branch, so a `git pull` fast-forwards both.

The one new candidate commit:

| SHA | What |
|---|---|
| `e6ed1ad2` | Native AA: wait out a poke's whole connect, not four seconds |

Unit gate on the testing branch: **1414 / 0** (`testGithubDebugUnitTest`). Round 2 read 1389; the USB
branch's four test classes and two new `PokeOverlapPolicyTest` cases take it here. Any other count
means the wrong tree was built; stop and say so rather than running the round.

**The USB branch is in the APK and is not under test in this round.** It touches
`connection/usb/`, `UsbAttachedActivity` and the manifest, none of which any run here reaches. It has
its own round, briefed separately as `usb-aoa-handoff-round2-brief.md`; do not run its runs from here
and do not let its lines change a verdict.

## 2. What this is

**Round 2's R6 failed on a number, and `e6ed1ad2` is that number.**

`PokeOverlapPolicy.CONNECT_SETTLE_WAIT_MS` was 4000 ms, sized from a reporter's phone that was
*present and actively refusing*, where an HFP-AG refusal takes ~3.05 s. Round 2 ran R6 with the
POCO's Bluetooth genuinely **off**, and on this rig that makes `socket.connect()` run the full
Android RFCOMM connect timeout instead: **15.40-15.46 s, measured three times, per profile record.**
So the manual poke logged its waiting line correctly, polled for 4000 ms, got `PROCEED`, and opened
its own socket about 11 s before the automatic one's `finally` cleared the marker. From `r6b`:

```
21:15:51.246  NativeAA: Calling socket.connect() for POCO X3 NFC via HFP-AG   ← 1st socket
21:15:51.512  NativeAA: another poke is already connecting to POCO X3 NFC — waiting for it ...
21:15:55.603  NativeAA: Calling socket.connect() for POCO X3 NFC via HFP-AG   ← 2nd, 1st still open
21:16:06.670  NativeAA: Poke via HFP-AG to POCO X3 NFC (...) failed: ... read ret: -1   ← 1st closes
```

Two concurrent RFCOMM sockets to one phone, every record failing: the exact symptom the commit
before it set out to remove.

Two things changed. The bound is **20 s**, which outlasts a Bluetooth-off connect on this rig with
margin. And the policy gained a third answer: when the bound expires with the same phone still being
connected to, the poke now **abandons** instead of proceeding. A connect still in flight after 20 s
is stuck, and a second socket into a stuck one has never helped.

The same wait was also put on the *automatic* loop, which had the identical hole.
`triggerPoke()` does `pokeJob?.cancel()` and starts a fresh loop, and a cancel cannot interrupt a
blocking `connect()` any more there than it can in `manualPoke()`. That half has never been
exercised on hardware; it is R9 below, and it is a watch item, not a criterion.

## 3. Driving the app

Unchanged from round 2 §3, and the receiver behaved exactly as briefed there. In short:

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { adb shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit."$@"; }
```

The package and the action prefix genuinely differ (`headunitrevived` versus `openheadunit`), and
`-f 0x00000020` is not optional. Every `send` must produce `AutomationReceiver: <action>` at INFO; if
that line is missing the broadcast never landed and the run is void, not a FAIL.

## 4. What is different about this round

- **The DEX grep no longer separates this APK from round 2's.** Round 2's APK
  (md5 `ba6293923a059f4503931503a47159d2`) already contains `PokeOverlapPolicy` and
  `AutomationCommandPolicy`, so both greps read non-zero on both builds. **The build stamp is the
  only identity check that discriminates here.** Round 2's brief could lean on the grep; this one
  cannot. See R0.
- **R6 now has two PASS shapes, and which one happened is a reported result.** Round 2's brief
  assumed one shape and that assumption is what its "void" conditions were written around. Both
  shapes below are a PASS and both are informative; do not treat the second as a partial one.
- **Expect R6 to take about four times as long as round 2's.** The manual poke should now wait out
  the full ~15.4 s connect rather than 4 s, so a single R6 run runs about 35-50 s from the automatic
  poke's `connect()` to `Manual poke ... finished.` Round 2's three runs are the reference.
- **R6, R7 and R8 still want the phone's Bluetooth OFF**, and still form no session. That is
  deliberate and is what makes the race reproducible.
- **The watcher must be the file poll**, not `logcat | grep -m1 ... && send`. Round 2's first R6
  attempt was void because `adb logcat` never exits, so the pipeline hangs after the match and the
  broadcast never fires. The working watcher polls the growing capture file every 200 ms for a *new*
  `Calling socket.connect() for POCO X3 NFC` and fires the broadcast the instant one appears; it
  landed 0.18-0.24 s after the automatic connect in all three valid runs.
  `hur-wifi-test-scripts/wireless_bringup_5ghz_r2_poke.sh` already does this and needs no change.
- **The rig baseline's `native-driver-selection-mode=2` still auto-picks an absent phone.** Set it to
  `0` for every run and restore the rig's `2` afterwards, as in rounds 1 and 2.
- **R1 is unchanged and is the run that protects the branch.** `e6ed1ad2` adds a wait to the
  *automatic* poke loop, which every Native AA session uses, so this regression guard now covers more
  than it did in round 2.

## 5. Settings keys this round needs

| Key | Element | Note |
|---|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | Native AA, every run |
| `native-driver-selection-mode` | `<int name="native-driver-selection-mode" value="0" />` | every run; restore the rig's `2` afterwards |
| `log-level` | `<int name="log-level" value="2" />` | R6, R7, R8, R9. **R1 uses `0`** |
| `native-poke-bt-macs` | `<set name="native-poke-bt-macs"><string>DC:B7:2E:5E:4E:59</string></set>` | R6/R7/R8/R9, so the loop pokes only the POCO |
| `wifi-5ghz-channel` | `<int name="wifi-5ghz-channel" value="0" />` | automatic, every run |

`native-poke-bt-macs` is a **StringSet**, which §1's element table does not cover, so it needs its own
element-scoped pattern:

```bash
adb shell run-as $PKG sh -c '
  f=shared_prefs/settings.xml
  sed -i -E "s#<set name=\"native-poke-bt-macs\">.*</set>##g" $f
  sed -i "s|</map>|<set name=\"native-poke-bt-macs\"><string>DC:B7:2E:5E:4E:59</string></set></map>|" $f
'
adb shell run-as $PKG cat shared_prefs/settings.xml   # verify, every time
```

Round 2 found it already held only the POCO's MAC and read back unchanged after every launch, so this
is likely a no-op; verify rather than assume. Two standing traps: it seeds itself from the auto-start
list the first time it is read and writes that back, so read it *after* a launch; and never leave a
`settings.xml.bak` beside the file, because SharedPreferences reads a stray `.bak` as an aborted write
and restores it over the edit.

## 6. The lines that decide every run

Verified with `git grep -F` against `e6ed1ad2`; each appears in exactly one file under
`app/src/main/java`.

```
NativeAA: another poke is already connecting to
NativeAA: another poke has been connecting to
NativeAA: Calling socket.connect() for
NativeAA: Attempting active poke to device:
NativeAA: Attempting manual poke to
NativeAA: Successfully poked
NativeAA: a chosen driver's wake poke is running
AutomationReceiver: 
```

The failure line is a format string, so grep the fixed part `NativeAA: Poke via ` and, on the same
line, ` failed: `. Its emitted shape, unchanged from round 2:

```
NativeAA: Poke via HFP-AG to POCO X3 NFC (DC:B7:2E:5E:4E:59) failed: read failed, socket might closed or timeout, read ret: -1
```

**Both "another poke" lines contain an em dash**, U+2014, with one space each side. Copy them, do not
retype them; a hyphen will not match. Their full shapes:

```
NativeAA: another poke is already connecting to POCO X3 NFC — waiting for it rather than opening a second socket.
NativeAA: another poke has been connecting to POCO X3 NFC for 20000ms — not opening a second socket to it.
```

The second one is new this round and is the abandon shape. Its words also occur in a source comment,
so grep the `NativeAA: ` prefix rather than the bare phrase if you are checking the tree rather than a
capture.

Also used and already familiar: `WirelessServer: Incoming connection detected`,
`SSL handshake complete`, and the group line, which is composed from a band label, so grep the fixed
part `createGroup SUCCESS!`, which emits as `WifiDirectManager: 5GHz createGroup SUCCESS!` here.

## 7. Runs

### R0: build gate

The build stamps its own commit:

```bash
./gradlew :app:assembleGithubDebug        # prints "Building from commit: 1f4b85ce5a59"
send ACTION_QUERY_STATE
# the reply JSON on data= must carry "commit":"1f4b85ce5a59"
```

Anything ending in `-dirty` means the tree had uncommitted changes when it was built. Stop and clean
it: a dirty build cannot be tied to this brief.

**The stamp is the whole identity check this round.** Round 2's APK also carries `PokeOverlapPolicy`
and `AutomationCommandPolicy`, so a DEX grep for either reads non-zero on both builds and separates
nothing. If you want a second, independent check, grep the DEX for `UsbSwitchClaim`. The USB branch
joined the testing branch after round 2, so round 2's APK reads **0** on it and this one does not.

Unit gate **1414 / 0**, candidate md5 recorded, installed with `adb install -r`.

**If R0 fails, stop and report.**

---

### R6 (re-run): a manual poke no longer races an automatic one. **The point of the round.**

Setup, in this order, identical to round 2:

1. Settings per §5 with `log-level=2`, POCO's MAC in `native-poke-bt-macs`, app stopped.
2. **POCO's Bluetooth OFF.** Its WiFi state does not matter.
3. Launch, and **let the WiFi Direct group form first**, waiting for `createGroup SUCCESS!`. This is
   load-bearing: without credentials, `manualPoke()` runs a pre-flight that can burn 4000 ms and
   swallow the window before it ever reaches the wait under test.
4. Arm the manual poke with the file-poll watcher, per §4. `AutomationReceiver: ACTION_NATIVE_AA_POKE`
   must appear, or the run is void.

**PASS** is either of two shapes. Both are a pass; **say which one happened.**

**Shape A, serialised.** The ordinary case, and the one to expect:

1. `NativeAA: another poke is already connecting to POCO X3 NFC — waiting for it rather than opening a second socket.`
   appears **exactly once**.
2. Between the automatic poke's `Calling socket.connect()` and its own outcome line, there is **no
   second** `Calling socket.connect()` for the same MAC.
3. `NativeAA: Attempting manual poke to POCO X3 NFC...` comes **after** the automatic attempt's
   outcome line, not between its records.
4. The failure lines that do appear each name a profile and a reason.

**Shape B, abandoned.** The bound ran out first:

1. The waiting line above appears exactly once, and
   `NativeAA: another poke has been connecting to POCO X3 NFC for 20000ms — not opening a second socket to it.`
   follows it.
2. No `Attempting manual poke` line at all, and no second `Calling socket.connect()` for that MAC.

**FAIL, in either shape:** two `Calling socket.connect()` lines for the same MAC overlap in time. That
is round 2's result and it is the only thing this round is trying to remove. Also a FAIL if the
waiting line never appears although the broadcast demonstrably landed inside the window.

Pre-registered non-failures, so neither is read as a result:

- **The poke missed the window.** If `AutomationReceiver: ACTION_NATIVE_AA_POKE` lands *before* the
  automatic `Calling socket.connect()`, or more than ~15 s after it, there was no overlap to
  serialise. That run is **void**: repeat it, do not record it as PASS or FAIL. Report how many
  attempts it took. Note the window is now the full ~15.4 s the automatic connect blocks for, so it
  is much easier to hit than round 2's brief assumed.
- **A handshake landed during the wait.** The manual round loop breaks and no `Attempting manual poke`
  prints. Also void; it should not happen with the phone's radio off, so if it does, say so.

**Report the delay from `AutomationReceiver: ACTION_NATIVE_AA_POKE` to whichever line ended the
wait**, which is `Attempting manual poke` in shape A and the abandon line in shape B. Round 2
measured **4.11 s, three runs out of three**, and that number is exactly what failed. About 15.5 s in shape A, or about
20 s in shape B, is what proves the new bound was actually reached rather than short-circuited by a
marker that cleared early. A delay near 4 s means the old constant is in the build, whatever the
stamp says.

Three valid runs, as in round 2.

---

### R7 (re-run): a poke failure says why, at the default log level.

Read out of R6's capture, with no separate setup. Unchanged from round 2, which passed it.

**PASS:** with `log-level=2`, a line matching `NativeAA: Poke via ` ... ` failed: ` appears and carries
a reason.

**FAIL:** `Calling socket.connect()` appears with no outcome line of any kind at this level.

`e6ed1ad2` does not touch this. It is here because it costs one grep of a capture you already have.

---

### R8 (re-run): a cancelled poke stops after one record.

Read out of R6's capture, with no separate setup. Unchanged from round 2, which passed it 3/3.

**PASS:** the automatic poke that the manual one cancelled logs **at most one**
`Calling socket.connect()`, and no `HSP-AG` connect after its `HFP-AG` attempt.

**Read this together with R6, because it is what makes shape A possible.** One refused record takes
~15.4 s here; two take ~31 s, which is past the new 20 s bound. So if the cancellation check ever
stops taking, R6 will come back as shape B rather than shape A. That pairing is diagnostic, not a
failure of either.

**INCONCLUSIVE** if R6 came back void: with no cancellation there is nothing to observe. Do not
manufacture one.

---

### R1 (re-run): an ordinary Native AA session still forms. **Regression guard.**

`e6ed1ad2` adds a wait to the automatic poke loop, which every Native AA session goes through, so this
is the run that protects the branch. POCO's Bluetooth **back on**, `log-level=0`,
`native-poke-bt-macs` left as set, everything else as round 2's R1.

**PASS:** `createGroup SUCCESS!`, `WirelessServer: Incoming connection detected`,
`SSL handshake complete`, and a picture. `AapService creating` to `SSL handshake complete` is not more
than ~1.5x round 2's **21.42 s**, i.e. under about 32 s.

**FAIL:** no picture, or that time is over the bound.

**Pair the verdict with the number**, because a pass here can mean two things. If the wait is working
as intended it costs this run nothing at all: no other poke is in flight, so the policy answers
`PROCEED` on the first poll and the timing should land near round 2's 21.42 s. A time that has grown
by ten seconds or more would mean the automatic loop is waiting on something it should not, which is
the one regression this commit could plausibly introduce. Report the seconds, not "about the same".

`MATCH! Starting AapService` = 1 is the benign poke self-wake, as in rounds 1 and 2; it is not churn
unless a second `createGroup SUCCESS` comes with it.

---

### R9 (new): the automatic poke loop waits for itself too. **Watch item, not a criterion.**

No setup of its own; read out of R6's and R1's captures.

The same wait now guards `triggerPoke()`'s device loop. It fires only when a credential *change*
restarts the poke loop while a connect is already in flight; an unchanged redelivery is deduped
before it gets that far, so on a settled group it will usually never fire at all.

**What to report:** how many `NativeAA: another poke is already connecting to` lines appear with **no**
`AutomationReceiver: ACTION_NATIVE_AA_POKE` line before them in the same run. Those are the automatic
gate. Any that follow a broadcast belong to R6 and are already counted there.

**Zero is not a FAIL** and is the likely answer. This run exists because the automatic half of the fix
has never been seen on hardware, and one sighting would be worth more than a green.

## 8. Do not re-run

- **R2, R3 and R4.** Settled in round 1 and untouched since. Their errata stand: the 5 GHz walk ladder
  and the regulatory dump are sub-Android-10 only and unreachable on this API-34 rig,
  `StationStandDown.restore()` always logs the WARN branch here, and R4's live form cannot be reached
  because the MT50 honours pinned channels.
- **R5.** It passed in round 2 on the backstop path, and `e6ed1ad2` does not touch the USB hold. Its
  subject also moved: the deferral is the wireless branch's, not the USB branch's, so nothing in the
  USB round covers it either. It stands as settled.
- Anything from the driver-selection or USB threads. `e6ed1ad2` touches neither.

## 9. Report back

1. **R6's outcome, and which shape**, serialised or abandoned. With the delay from
   `AutomationReceiver: ACTION_NATIVE_AA_POKE` to the line that ended the wait, for each of the three
   runs, against round 2's 4.11 s. If it took several attempts to land inside the window, how many.
2. **Whether any `Calling socket.connect()` pair for the same MAC overlapped in time**, in any run.
   One line, and the timestamps if the answer is yes.
3. **R1's `AapService creating` to `SSL handshake complete` time**, against round 2's 21.42 s.
4. **R9's count** of automatic-gate sightings, and zero if that is the answer.

One line each for R0, R7 and R8.

**The one thing to watch for that is nobody's criterion:** whether
`NativeAA: a chosen driver's wake poke is running — not replacing it with the multi-device loop.`
still appears after R6's manual poke wins. It fired 3 times in round 2's `r6b`, and it is the other
half of the same mutual exclusion. Its absence is not a FAIL, but it is worth a line, because the manual poke
now holds its slot about four times as long, so if that guard were going to be strained by this
change, this is the round where it would show.

## Evidence to capture

Into `evidence/wireless-bring-up-and-5ghz-round3/`:

- full logcat per run, gzipped: `hu_r6b.txt.gz`, `hu_r6c.txt.gz`, `hu_r6d.txt.gz`, `hu_r1.txt.gz`
- the decisive excerpt per run as `r<N>_console.txt`
- `wifip2p_r1.txt`, and the station state before and after R1
- `settings-backup-mt50.xml`, taken before anything is written
