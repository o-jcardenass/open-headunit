# audio-sink-jitter, round 5 brief: closing what round 4 left open

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `9ecc0077` | 4 on `main` | 2011 tests, 0 failures |
| Baseline | `main` | `5ce51c5e` | | |

```bash
git fetch fork
git checkout -B audio-sink fork/fix/audio-sink-and-wireless-bring-up   # 9ecc0077
git log --oneline -4
# 9ecc0077 Misc: the overlay, the USB reconnect, and a Share button that killed the app
# d9295370 Native AA: bring-up is gated and bounded, and old Android says what it cannot do
# 8a42674c Transport: video gets its own thread, and its ack is the flow control
# e8222786 Audio: the sink is measured, sized by the dial, and keeps what it has
```

**Check the branch out fresh. Every SHA round 4's brief names is gone.** The branch still carries
four commits, but round 4's results produced two changes which were compacted into them on
2026-09-16, rewriting all four. Final-tree equality was the check, the tree is byte identical to the
six-commit version it replaces, and all four commits compile alone.

What moved into them: the comment correction T2 forced went into `8a42674c`, whose own message used
to claim the phone's window bounds the backlog, and the stale-group recovery went into `d9295370`,
which S1 grades. Round 4's verdicts on everything else still stand.

**Baseline is needed for R0 only.** Everything else is candidate only.

## 2. What this round is

**This is not a re-run of round 4.** Rounds N1, N2, N3, N5, N6, N8, N9, A1, A3, A4, A5, A6, M1, M2
and M3 are graded and closed; do not repeat them. Round 5 is six runs, and each one exists because
round 4 either measured something the brief did not ask the right question about, or graded a run
whose lever turned out not to reach the code under test.

Read `audio-sink-jitter-round4-results.md` for the rig notes in its Setup section. Every one of them
still applies, and they are not repeated here.

Same rig: **D-SAM as head unit, D-POCO as the phone**, API 19 on a 2.4 GHz-only radio. Same
constraints as round 4 section 3, now also in `TESTING-TEMPLATE.md` section 7a.

**T1r comes first and needs no hardware.** Do it before touching the rig, because if round 4's
captures survive it closes a FAIL from the desk.

## 3. Settings keys this round needs

Round 4's table still applies. These are the differences:

```xml
<int name="log-level" value="0" />                              <!-- VERBOSE, every run -->
<int name="wifi-connection-mode" value="3" />                   <!-- Native AA -->
<boolean name="native-wifi-version-exchange" value="true" />    <!-- N4r ONLY, see below -->
<int name="debug-video-feed-hold-ms" value="200" />             <!-- T2r first half, 0 for second -->
<int name="audio-latency-multiplier" value="16" />              <!-- restore after A2r -->
```

**`native-wifi-version-exchange` defaults to `false` and round 4's table did not carry it.** With it
off, `sendWifiVersionRequest` never runs and the run it gates produces nothing to grade. Round 4
found this the hard way. Turn it on for N4r only and restore it to `false` immediately after,
confirming the restore in Setup notes.

`video-profile-starvation-cap` and `playback-focus-self-defeating` are still latches. Read both back
after every run as well as before one.

## 4. Runs

### R0. Gate

**PASS:** `./gradlew :app:testGithubDebugUnitTest` reads **2011** tests, 0 failures, and the
installed APK's md5 matches the one just built. Note the figure moved from round 4's 2003: eight of
those are `ProvenGroupStalePolicyTest`, which S1 grades on hardware.

---

### T1r. The per-channel split round 4 did not record

**No hardware needed if round 4's captures still exist.** Round 4 graded T1 a FAIL on
"`blocks=0` in every window", reporting `blocks` at 1, 2, 3 and 5. That is not the field that
decides anything. The dispatch line carries a per-channel breakdown, and the whole point of giving
video its own thread is that **`video=` collapses to roughly zero**:

```
transport dispatch over 30000ms: video=Nms, audio=Nms, other=Nms, unread=N%, blocks=N, longest=Nms on X, videoQueue=N, videoShed=N
```

Re-grep round 4's captures for those lines **in full** and report every window's `video=`, `audio=`,
`other=` and `longest=... on X`.

**PASS:** `video=` is near zero in every window, and the residual `blocks` are accounted for by
`audio=` and `other=`. That says the split did its job and the brief's bar was written for faster
silicon than an API 19 tablet.

**FAIL:** `video=` carries real time. That is the park still holding the read thread, and the fix is
incomplete.

Two things to state plainly whichever way it lands. Round 2 measured **132 to 145 blocks a window at
up to 233 ms** with the audio sink underrunning beside them; give round 4's numbers against that.
And round 4 called `unread=` at 8 to 9% healthy, but the instrument's own definition of healthy is
**below 5%**, so say whether it clears that bar rather than repeating the word.

If the captures are gone, say so and run one five-minute session to get them. Do not re-run all of T1.

### T2r. Where the backlog actually stops

**Candidate only.** Round 4 watched `videoQueue` climb 19 to 120 over seven minutes with
`debug-video-feed-hold-ms=200` and stopped while it was still rising. That left the one question
that matters unanswered: **whether it plateaus or diverges.**

It matters because the code announces a flow-control window of 12 messages and the backlog reached
ten times that. Either the phone does not honour the window we announce, or it counts something
other than messages. Whichever it is, the ceiling that actually bounds this is ours, at 256.

**Run it to an answer, not to a clock.** `debug-video-feed-hold-ms=200`, media playing, and watch
until either:

- `videoQueue` is flat across **three consecutive windows** (a plateau), or
- `AapTransport: the video thread is 256 messages behind, shedding` fires (the ceiling), or
- forty minutes pass.

**Report, in this order:**

1. The plateau depth, or the time to the ceiling. This is the number the round exists for.
2. The announced window, quoted from the phone's own setup: `grep "Config response:" log.txt`, which
   carries `maxUnacked=N`. State it beside the plateau.
3. `videoShed=` and whether the picture stayed whole, with a screenshot as round 4 took one.
4. The `audio sink AUDIO` line across the same windows.

**Then repeat once with `debug-video-feed-hold-ms=0`**, five minutes, and report `videoQueue` across
those windows. **This is the half round 4 never ran and it decides whether any of this matters.**
The feed hold is an artificial lever and the code's own log says "No real fault looks like this
line"; if the queue sits in the low tens with the lever off, the climb is a property of the lever.

**There is no FAIL condition on this run.** It is a measurement, not a verdict. Say what the number
was.

### N7r. The guard, with a lever that reaches it

**Candidate only.** Round 4 fired `ACTION_START_WIRELESS_SCAN` twice and saw exactly one
`createGroup SUCCESS`, but never the guard line. That reading was correct and the run proved
nothing about the guard: a force-scan runs a full stop of the WiFi stack between the two, and a stop
legitimately clears the claim, so the second bring-up is not a duplicate.

The guard is for two bring-ups arriving from **different sources** within about 1.5 s, which is what
the regression it was written for looked like. Produce that instead. In rough order of how easily
they are staged:

- Close the settings screen (which re-arms the mode) while the phone arrives on Bluetooth.
- Toggle WiFi off and on at the platform level so the P2P state receiver re-enters while a
  `ACTION_START_WIRELESS_SCAN` is in flight.

**PASS:** `still running, so this one is not started on top of it.` and exactly one
`createGroup SUCCESS`. Count with `grep -c "createGroup SUCCESS"`.

**INCONCLUSIVE, not FAIL,** if neither lever lands two bring-ups inside 1.5 s. Report the closest
gap you achieved, from the timestamps. The window is 1.5 s and a run that never got inside it has
not tested the guard.

### N4r. The second path to the endpoint

**Candidate only**, `native-wifi-version-exchange=true` for this run only.

Round 4 graded the withhold correctly but on one of the two code paths that can put the WPP TCP
endpoint on the air. The other one is the TCP server's own check, which round 4 never reached.

Bring the mode up with the version exchange on and let the handshake run. Report:

- The identity line each time: `grep "group identity ssid=" log.txt`, with `persistent=` and
  `stable=`.
- The withhold reason each time, in full.
- Whether any `WppTcpServer` line offers an endpoint.

**PASS:** the endpoint is withheld on both paths, and after enough real creates the reason names the
platform, matching `too old to name its own WiFi Direct group`. The session still forms over
Bluetooth every time.

**FAIL:** an endpoint is offered on either path.

**Budget six bring-ups, not three.** Round 4 needed six, because a delivery that reads
`asked=nothing (not this app's create)` is a stale group rather than a fresh create and **resets**
the name-change counter. Count only deliveries that are this app's own create toward the three, and
say how many of your bring-ups were which.

**The withhold is load-bearing.** Forcing the endpoint out on a unit whose address moves has been
measured to put the phone into a `NETWORK_NOT_FOUND` loop with no Bluetooth fallback.

### A2r. The dial, with somebody listening

**Candidate only.** Round 4's A2 table is clean and monotone and needs no repeating. What it could
not grade is the half of the PASS that needs ears: no person was present, and no underruns fired at
any multiplier, so "fewer breaks at 16 than at 2" was not distinguishable.

**If a listener can be present:** two sessions, `audio-latency-multiplier` 2 then 16, media playing,
about three minutes each. Mark what you hear with
`ACTION_LOG_MARKER --es text <label>` (`-f 0x00000020`) at the moment of each break, so the marks
land in the capture beside the instruments. Report the marker count per multiplier against
`underruns=` on the sink line for the same windows.

**PASS:** fewer audible breaks at 16 than at 2, or none at either with `underruns=0` at both, which
is a quiet link rather than a working dial and should be said that way.

**If no listener can be present, skip this run and say so.** Do not grade it from the instrument
alone. Round 4 already has that half, and a second instrument-only PASS adds nothing.

### S1. The group the phone stopped coming back to

**Candidate only, and this is the run `d9295370`'s new half exists for.**

Round 4's unbriefed 40-minute session caught a real unprompted disconnect. The phone's own stack left
the WiFi Direct group during a background scan (`wpa_supplicant ... reason=3 locally_generated=1`),
could no longer find the group by scan, and **nothing on the app side ever recovered**: the Bluetooth
poke loop ran for nine minutes with no group recreate and no give-up, and only the WiFi button fixed
it.

The cause on our side is that a group which has carried a session was kept forever, on the reasoning
that recreating it moves its address out from under the profile the phone saved. That holds until the
phone stops coming back, when the saved profile is not working either. `d9295370` recreates such a
group after **four unanswered wake pokes**, bounded by the existing recreate budget.

**Do not wait forty minutes for a scan.** Stage the state directly: bring a session up, let it
project, then drop D-POCO off the group in a way that sends no Bye-bye and leaves the head unit's
group formed. Round 4's own lever works: toggle D-POCO's WiFi off and hold it down with a
repeat-disable loop every 15 s, because its adapter self-reverts on (round 4 Setup notes). Hold it
down for the whole observation.

**PASS:**

- The poke loop runs, and after about four pokes:
  `the phone has ignored N wake pokes since this group last carried a session` fires.
- Exactly one `createGroup SUCCESS` follows it.
- With D-POCO's WiFi released, the phone rejoins **without anyone pressing the WiFi button**.

**FAIL:** the stuck state of round 4 reproduces unchanged (pokes forever, no recreate), or the
recreate fires while a session is live or an exchange is in flight.

**Count these across the whole window and report them as numbers**, because they are the hazard this
change has to be read against:

```bash
grep -c "MATCH! Starting AapService"  log.txt   # self-inflicted wake-ups; round 4 measured 0
grep -c "createGroup SUCCESS"         log.txt   # round 4 measured 0 in the stuck window
grep -c "Attempting active poke"      log.txt
```

A recreate raises group churn, and a poke's own connect raises an `ACL_CONNECTED` that the auto-start
receiver treats as the phone arriving. Round 4 measured **zero** of those in the stuck window, so any
number above zero here is new and belongs in the results whether or not the run passes.

**Then do the control.** Repeat with the phone simply switched off rather than held off the group,
and confirm the recreate is still bounded: no more than four, then
`phone still not connected after N recreations`. An unbounded recreate loop is a FAIL even if the
first half passed.

---

## 5. Pre-registered UNTESTABLE on this rig

Unchanged from round 4 section 7, and all still apply: anything needing 5 GHz or a cross-band split,
the positive `wifi-direct-stable-identity` case, `deletePersistentGroup`, the USB bus-composition
rework, and round 3's Part P.

Added this round:

- **Whether the phone counts the flow-control window in messages or frames.** T2r measures the
  consequence, not the cause. Reading the cause needs the phone's side of the protocol and is not a
  rig question.

## 6. Report back

`audio-sink-jitter-round5-results.md`, format in `TESTING-TEMPLATE.md` section 7, one section per run
id above.

Four things this round wants named explicitly whatever the verdicts are:

1. **T1r's `video=` figures.** They decide whether round 4's T1 FAIL was a defect or a bar written
   for faster hardware.
2. **Where `videoQueue` stops**, and the `maxUnacked=` the phone was told, side by side. Plus the
   `debug-video-feed-hold-ms=0` windows, which say whether this matters off the test lever.
3. **Whether the guard line appeared in N7r**, and if not, the closest gap between two bring-ups you
   managed, in milliseconds.
4. **Whether S1 recovered without the WiFi button**, and the three counts above.
