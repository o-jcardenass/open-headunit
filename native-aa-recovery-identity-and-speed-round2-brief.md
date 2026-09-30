# native-aa-recovery-identity-and-speed: round 2 brief

Two commits appended to the round 1 candidate. Round 1 scored the three fixes below them and found
no FAIL; what it could not reach was the group-create wedge (R7, not exercised, the rig forms a
group in about 1.2 s). Two reporter logs since then show that wedge on two phones acting as head
units, and one of them shows what round 1's candidate would have done there: cancelled the stuck
create and asked for exactly the same one again, which was accepted and hung again, forever, at a
16 s cadence, while the early wake brought the phone back every half minute into a 60 s credential
wait that could not end well.

This file is append-only. Read `TESTING-TEMPLATE.md` first; this brief only says what is specific to
this round.

---

## 1. Build and baseline

```bash
git fetch fork
git checkout fix/native-aa-recovery-identity-and-speed   # 583729a6
```

| Input | SHA | Role |
|---|---|---|
| `fix/native-aa-recovery-identity-and-speed` | `583729a6` | **the candidate.** Five commits on `main` `12706e26` |
| the same branch at | `b5617bd5` | **the baseline.** Round 1's candidate, so the diff measured is exactly the two new commits |

The two new commits, and the run that scores each:

| SHA | Subject | Scored by |
|---|---|---|
| `74eb8414` | WiFi Direct: a create accepted but never formed is asked for differently | R7 (watch), R11 (watch) |
| `583729a6` | Native AA: a handshake that found no credentials stops the early wake | R10 |

**Unit gate: 1466 / 0** on the candidate; **1450 / 0** on the baseline. The 16 new cases:

| Class | New cases |
|---|---|
| `P2pCreateWedgePolicyTest` | 6 (14 total) |
| `NativeRefreshPolicyTest` | 4 (10 total) |
| `P2pIdentityRotationPolicyTest` | 1 (11 total) |
| `EarlyWakePolicyTest` | 5 (14 total) |

Any other count means the wrong tree was built; stop and say so.

**No history was rewritten.** `b5617bd5` and everything under it are unchanged; the two commits sit on
top. Identity check, in order of strength: the build stamp (`"commit":"583729a6...."` on the
candidate, `"b5617bd5...."` on the baseline; this is the only check that discriminates the two arms,
because both carry the same six DEX symbols round 1 listed), then the APK md5s, recorded for both.

## 2. What the two commits do

### a. A create the platform accepts and never finishes is asked for differently

Round 1's candidate cancelled a stuck create and retried the same rung with the same request. On the
reporter's unit the banded create (a named, persistent group on the 5 GHz band) was accepted four
times in thirty minutes and never once produced a group, so that was a faster loop around the same
failure. Now each cancel drops something the stuck create asked for: the band first, then the name
(the two-argument create, the platform's own stored profile), then it stops and says so honestly.
Three cancels per bring-up at most.

Three more changes in the same commit, all measured on that log:

- The stall is read off the group-info reads themselves. Twenty empty reads on an accepted create is
  the stall, and the cancel is spent there; it used to need a BUSY from a colliding refresh to fire.
- The refresh waits an accepted create out for the whole 21 s read window instead of remaking the
  group underneath it at 15 s.
- The stamp that ages the accepted create is on `uptimeMillis`, the framework's own clock for its
  two-minute timeout. The reporter's screen went off mid-capture and every timer stalled 8 to 18 s;
  on `elapsedRealtime` our clock would have run ahead and skipped the cancel.

Also: a rotation of the network identity now waits while a create is outstanding, and two lines a
reporter log needs at INFO were DEBUG (`removeGroup before recreate failed` and `the claimed create
window is released`).

### b. A handshake that found no credentials stops the early wake

The early wake (round 1's b) starts the poke loop as soon as the listeners are open. On a unit whose
group never forms, that loop woke the phone every half minute, the phone dialled back, waited 60 s
for credentials that never came, and failed, indefinitely. Now a handshake that ends for lack of
credentials stops a loop that started without them, and a second empty wake is refused until the
credentials exist. The credential delivery restarts the wake as before.

## 3. Driving the app

As round 1. R11 needs a different head unit; see the run.

## 4. Settings keys

As round 1, every run: `wifi-connection-mode` 3, `native-ap-transport` 0,
`native-driver-selection-mode` 0, `log-level` 0 (VERBOSE), `native-poke-bt-macs` the phone's MAC
alone, `wifi-5ghz-channel` 0. Restore the rig's values afterwards, as round 1 did.

## 5. The lines that decide every run

New on the candidate, absent from the baseline. Verified with `git grep -F` against `583729a6`.

```
WifiDirectManager: the stuck group creation was cancelled; asking for a group differently.
WifiDirectManager: cancelling the stuck group creation went unanswered
WifiDirectManager: the create that never formed asked for a band
WifiDirectManager: the create that never formed named the group
WifiDirectManager: this unit accepts a group request and never forms the group
WifiDirectManager: refresh: a group was accepted
NativeAA: the early wake brought the phone back to no network; not waking it again until the WiFi group exists.
NativeAA: not waking the phone again before the WiFi group exists
AapService: the new WiFi Direct identity waits for the next create: a group is still being asked for
```

Round 1's discard exception still greps `the stuck group creation was cancelled`; the prefix is
unchanged and only its tail moved.

Present on both arms and used for counts: everything in round 1's section 5, plus

```
NativeAA: Handshake failed - No WiFi credentials available after
NativeAA: Connection accepted from
WifiDirectManager: WiFi is disabled but needed for Native AA
```

## 6. The runs

### R0 - build gate

Test count both arms, both APK md5s, both build stamps. **Stop the round if the candidate is not
1466 / 0.**

### R1 to R4 - regression guards

Candidate only, on the MT50, exactly as round 1 defined them: R1 three sessions with the two
timings each, R2 the wake trigger before the first credentials, R3 exactly one
`wake poke starting` per bring-up, R4 one `creating the group` and one create chain. Round 1's
baseline numbers stand as the control; do not re-run the baseline for these. The new commits must
not move any of them: commit b acts only after a handshake fails for lack of credentials, which a
healthy bring-up never does, and commit a acts only after twenty empty group reads.

**Also count, per capture, and expect 0 of each:** `the stuck group creation was cancelled`,
`the create that never formed`, `this unit accepts a group request and never forms`,
`refresh: a group was accepted`, `the early wake brought the phone back to no network`,
`not waking the phone again`. A non-zero count on a healthy bring-up is a FAIL.

### R7 - the create wedge, still a watch item

As round 1: not provoked, count across every capture on both arms, "not exercised" is acceptable.
If it does fire on the candidate, the cancel line must be followed by one of the two
`the create that never formed` lines or by `this unit accepts a group request and never forms`,
and the create that follows must be a different one from the one cancelled (`5GHz createGroup`
then `standard createGroup as`, or `standard createGroup as` then the two-argument
`Standard createGroup`). Quote the sequence.

### R10 - the early wake stops when the phone comes back to nothing

**Both arms.** This is the round's point, and the only half of the reporter's failure the rig can
provoke.

1. Force-stop the app. Settings as section 4.
2. **Turn the head unit's WiFi off** (`svc wifi disable`; confirm with `dumpsys wifi | grep -m1
   "Wi-Fi is"`). Bluetooth stays on. Phone radios on and settled.
3. Launch the app. On Android 10 and above the app does not turn WiFi on itself:
   `WifiDirectManager: WiFi is disabled but needed for Native AA` and a toast, and no group is
   asked for. The listeners open regardless, and the early wake starts.
4. Capture for **four minutes** without touching anything.
5. Turn WiFi back on (`svc wifi enable`). Capture until a session forms or three more minutes pass.

**Candidate PASS**, in this order:

- `waking the phone while the WiFi group is still forming.` once, then `Calling socket.connect()`,
  then `Connection accepted from`, then 60 s later
  `Handshake failed - No WiFi credentials available after 60s wait.`
- Immediately after it, `the early wake brought the phone back to no network; not waking it again
  until the WiFi group exists.`
- From that line to step 5: **zero** further `Calling socket.connect()` and zero further
  `Connection accepted from`. Any `not waking the phone again before the WiFi group exists` line
  in that window is the refusal working, not a failure; report its count.
- After step 5: a group, `Providing credentials`, then one `wake poke starting` (or the phone
  connecting on its own), and a session. The wake restarting after credentials is the criterion;
  a session is the informative part.

**Baseline control** (`b5617bd5`): the same first four lines, then the loop carries on. Expect a
second `Calling socket.connect()` within about 30 s of the failure, a second `Connection accepted`
and a second 60 s failure inside the four minutes. Report the count of `Connection accepted from`
and of `Handshake failed - No WiFi credentials` in the window on each arm.

Report also how long each 60 s wait actually took on the wall clock (the `after Ns` in the
`Still waiting for credentials` lines): with the head unit's screen on it should be within a second
of 60.

**Discard-rule note:** `MATCH! Starting AapService` may fire when the phone's Bluetooth link to the
head unit drops and returns after the failed handshake. With WiFi off it cannot create a group, so
it is benign here; count it and say so rather than discarding.

### R11 - phone to phone

Both reporter units are phones acting as head units; round 1 measured only the MT50. **Candidate
only. D-POCO is the head unit, D-MOTO is the phone.** Settings as section 4 on D-POCO, with
`native-poke-bt-macs` set to D-MOTO's MAC alone. One clean bring-up from a force-stop, phone radios
on and settled, captured until a session forms or five minutes pass.

**PASS:** `WirelessServer: Incoming connection detected from` then `Handshake: SSL handshake
complete`, with one `createGroup SUCCESS` and one create chain. **Video is not a criterion**: a
phone in portrait cannot hold the projection activity foregrounded, so the phone side withholds
video and only the audio channels run; that is a known property of a phone head unit, not a
defect, and a capture with no video and a healthy SSL handshake is a PASS.

Report separately for this capture: the R1 timings, the R7 watch counts, every `BUSY`, every
`Group info was null` run and its length, and every `p2p-wlan0-N` index. A phone's P2P stack is the
population the wedge was seen on, so anything it does that the MT50 did not is worth a line even
when the session forms.

D-POCO's own Android Auto head unit server being down does not matter here: that is the Self Mode
path, and this run is Native AA.

## 7. Discard rules

Round 1's, including its exception for a second create that follows
`the stuck group creation was cancelled`. R10 deliberately forms no session for its first four
minutes; that is not a discard.

## 8. Numbers to report

1. R0: both counts, both md5s, both stamps.
2. R1 to R4: as round 1, candidate only, plus the six zero-counts.
3. R7: the count of `a group this unit accepted` across the round, both arms.
4. R10: per arm, the count of `Connection accepted from` and of `Handshake failed - No WiFi
   credentials` in the four-minute window; the time from the failure line to the next
   `Calling socket.connect()` (candidate: none before step 5); whether a session formed after
   step 5 and how long after.
5. R11: the R1 timings, the R7 watch counts, the BUSY and null-group-info counts, the interface
   indices.

## 9. Anything the brief did not ask about

As always. In particular anything D-POCO's P2P stack says that the MT50's never did.
