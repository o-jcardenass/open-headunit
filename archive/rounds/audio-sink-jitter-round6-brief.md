# audio-sink-jitter, round 6 brief: grading the three fixes round 5 forced

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `78f7e70b` | 6 on `main` | 2017 tests, 0 failures |
| Baseline | `main` | `5ce51c5e` | | |

```bash
git fetch fork
git checkout -B audio-sink fork/fix/audio-sink-and-wireless-bring-up   # 78f7e70b
git log --oneline -6
# 78f7e70b Transport: the video ceiling's own numbers, measured at the ceiling
# 8441d044 Native AA: the endpoint withhold now covers the dial, and creates cannot race
# 9ecc0077 Misc: the overlay, the USB reconnect, and a Share button that killed the app
# d9295370 Native AA: bring-up is gated and bounded, and old Android says what it cannot do
# 8a42674c Transport: video gets its own thread, and its ack is the flow control
# e8222786 Audio: the sink is measured, sized by the dial, and keeps what it has
```

**The four commits round 5 graded are unchanged and still carry those SHAs.** Two new commits sit on
top. Nothing was rewritten this time, so a branch checked out for round 5 fast-forwards.

`8441d044` is what W1, N7r2 and J1 grade. `78f7e70b` is comments only and changes no behaviour.

**Baseline is needed for R0 only.** Everything else is candidate only.

## 2. What this round is

Round 5 was the best-instrumented round this thread has run, and it produced three code-provable
defects rather than one. Two of them it went looking for; the third its own numbers proved by
accident. All three are fixed on `8441d044` and **none of them is verified on hardware**. This round
is that verification, plus one cheap measurement that lets the thread stop asking about the video
ceiling.

Read `audio-sink-jitter-round5-results.md` first. Its Setup notes carry four rig facts this round
depends on and they are not repeated here.

**Closed, do not repeat:** T1r and T2r are both answered. T1r established that the video-thread split
works (`video=` averages 199ms against audio's 2598ms, `longest=` names VIDEO in 5 of 151 windows,
`videoShed=0` in all 151) and that `unread=` sitting at 7 to 12% is audio dispatch, not video.
`healthy` requires below 5% and will not read true on this unit; that is now recorded in the
instrument's own KDoc and is not a defect to chase. T2r established that the 256 ceiling is reachable
only through `debug-video-feed-hold-ms`, and that off the lever the queue sits at 2 to 5.

Same rig: **D-SAM as head unit, D-POCO as the phone**, API 19 on a 2.4 GHz-only radio.

**W1 costs the phone something, so read it before planning the order.** Arming it deliberately
re-poisons D-POCO's Gearhead the way round 5 did by accident, and clearing it again needs the same
forget-the-vehicle plus re-consent taps. Run W1 last, or accept paying that cost mid-round.

## 3. Settings keys this round needs

Round 4's table still applies. These are the differences:

```xml
<int name="log-level" value="0" />                              <!-- VERBOSE, every run -->
<int name="wifi-connection-mode" value="3" />                   <!-- Native AA -->
<boolean name="native-wifi-version-exchange" value="true" />    <!-- W1 ONLY, restore to false -->
<int name="native-ap-transport" value="1" />                    <!-- W1 arming step ONLY, see W1 -->
<int name="debug-video-feed-hold-ms" value="0" />               <!-- T3 needs this at 0 -->
```

`native-wifi-version-exchange` defaults to `false`, and with it off the WPP version exchange never
runs and W1 grades nothing. Round 4 found that the hard way and round 5 confirmed it. Turn it on for
W1 only and restore it, confirming the restore by readback.

`video-profile-starvation-cap` and `playback-focus-self-defeating` are still latches. Read both back
after every run as well as before one.

## 4. Runs

### R0. Gate

**PASS:** `./gradlew :app:testGithubDebugUnitTest` reads **2017** tests, 0 failures, and the
installed APK's md5 matches the one just built. The figure moved from round 5's 2011: six of those
are `WppTcpServePolicyTest`, which W1 grades on hardware.

---

### W1. The server now refuses the dial it would refuse to advertise

**Candidate only. This is the run the round exists for.**

Round 5's N4r sub-result B: `WppTcpServer` listened on 5299 and served whatever dialled it,
independent of what that same session's `WppEndpointPolicy` had just decided. D-POCO dialled in on a
belief cached from an earlier thread, was served this group's SSID, and then looped
`NETWORK_UNAVAILABLE_NETWORK_NOT_FOUND` on that dead name for about 15 minutes with no Bluetooth
fallback. Forgetting the vehicle on the phone was the only way out.

`8441d044` makes serving the dial the same decision as advertising the endpoint: on a withhold the
socket is accepted, logged and closed, and nothing is sent. The listener deliberately stays up,
because `listeningPort` is an input to that decision and turning the server off would gate the answer
behind itself.

**Arming the state, because round 5 destroyed it.** D-POCO no longer holds an endpoint for this head
unit. The lever is the hotspot strategy, which advertises whatever the group identity says, and the
endpoint goes out in the Bluetooth version request before any WiFi join, so a full session is not
needed and the 5 GHz limit does not apply:

1. `native-ap-transport=1` plus round 5's hotspot values from `wpp-over-tcp-round5-brief.md` section 3.
2. Bring up once and confirm `[TX] Sending WifiVersionRequest (Type 4)` carries an endpoint. Quote
   the line. **If it does not, stop here and report that**, because the rest of W1 cannot run.
3. `native-ap-transport=0`, back to WiFi Direct, and restart the mode.

**Then run it.** Bring up normally and watch for the phone dialling 5299.

**PASS:**

- `WppTcpServer: connection from 192.168.49.x` appears, immediately followed by
  `WppTcpServer: not serving this dial: <reason>`, and the reason is the RENAMED text.
- **No `WppTcpServer: [TX] WifiInfoResponse (Type 3) with credentials` anywhere.** This is the
  field that decides the run: it is the message that handed the phone the doomed SSID.
- No `WppTcpServer: TLS handshake complete` for that dial.
- The session then forms over Bluetooth, and renders.

**FAIL:** any `WifiInfoResponse` on the TCP path, or the Bluetooth route never recovers.

**Then watch for ten minutes and report whether Gearhead wedges.** Grep D-POCO for
`GH.WPP.TCP: Restarting WPP over TCP` and give the count against round 5's "every 35 to 55 seconds
for about 15 minutes". **State plainly whichever way it goes.** The fix stops us handing out a new
doomed name; it cannot retire an endpoint the phone already holds, because no WPP message does. If
Gearhead still loops on the cached endpoint that is expected behaviour, not a FAIL of this run, and
the results should say so rather than grading it down.

**Restore `native-wifi-version-exchange=false` and the hotspot keys afterwards, and say so.**

---

### N7r2. The guard, with the hole closed

**Candidate only.** Round 5 staged a settings-screen close and `ACTION_START_WIRELESS_SCAN` under a
second apart, and got two concurrent walks 130ms apart, no guard line, two `createGroup SUCCESS`, and
a phone told to join a group that was replaced 891ms later. The session formed 50.7 s late.

The guard was sound and was watching the wrong side of the teardown: both triggers force a restart,
and `WifiLauncherManager.setActive` stops before it starts, and that stop clears the claim
`WifiDirectManager` holds. `8441d044` puts a second claim in the launcher manager, ahead of the
teardown, and fences the framework create callbacks so a create the platform had already taken cannot
claim a group after a stop.

**Stage round 5's exact sequence.** Close the settings screen, then fire
`ACTION_START_WIRELESS_SCAN` under a second later. Use round 5's own timings if they are recorded.

**PASS:**

- `WifiLauncher: a Native AA bring-up was re-armed Nms ago, so this one is not started on top of it.`
- Exactly **one** `createGroup SUCCESS`.
- Exactly **one** SSID delivered to the phone. Grep `SUCCESS - Providing credentials` and the
  `SSID=DIRECT-` names, and report every distinct name in the window.
- Time from the first trigger to `WirelessServer: Incoming connection detected`, against round 5's
  **50.7 s**.

**FAIL:** two creates, or two different SSIDs reaching the phone, or the refusal firing on a re-arm
that should have gone through.

**Then check the refusal does not eat a real one.** Change a wireless setting and save it, which
sends `ACTION_START_WIRELESS`. The mode must restart and the new setting must take effect, because
the refusal is written to skip a re-arm only when the configuration is unchanged. **A refusal here is
a FAIL** even if the first half passed. The band setting is the easiest to read back.

Also grep for `the Native AA group create abandoned` and
`a later bring-up owns the group now`. Neither is expected in a clean run; if either appears, quote
it with what happened next, because they are the new fence talking.

---

### S1r. The stale-group recreate, with a lever that reaches it

**Candidate only. Round 5's S1 lever cannot grade this and must not be retried.**

Round 5 held D-POCO's WiFi down, first at 15 s and then at 3 s. Both genuinely starved the link, and
neither reached the mechanism. The counter the fix reads is incremented by a **successful poke** and
reset the moment the phone opens the Android Auto channel, and a phone with its WiFi off still
answers Bluetooth: it opened the channel every cycle and replied
`WIFI_NETWORK_UNAVAILABLE(-11)` 22 times. So the counter was reset 12 times and never approached 4.
That reading was correct. Do not spend rig time on `s1_wifi_holddown*.sh` again for this run.

**The shape that reaches it is round 4's, not round 5's:** the phone answers the HFP poke and then
never opens the Android Auto channel. Reproduce it by taking Gearhead out while leaving both radios
alone.

1. Bring up normally and let a real session render, so
   `this group has carried a session, so the join watchdog will not recreate it if the phone leaves`
   fires. **Confirm that line before going on**; without it the branch under test is not armed.
2. On D-POCO, `am force-stop com.google.android.projection.gearhead`, and keep it stopped for the
   window (`pm disable-user` if a force-stop does not hold). Leave Bluetooth and WiFi on.
3. Watch for at least six poke cycles, which is roughly five minutes.

**PASS:**

- `NativeAA: Successfully poked POCO X3 NFC via HFP-AG` each cycle, with **no**
  `NativeAA: Connection accepted from`. That pair is the state the counter counts.
- At the fourth such poke:
  `WifiDirectManager: Native AA - the phone has ignored 4 wake pokes since this group last carried a session, so the network it saved is not reaching it either; recreating the group.`
- Exactly one `createGroup SUCCESS` follows it.
- Re-enable Gearhead and the phone returns **without anyone pressing the WiFi button**. Report how
  long it took.

**FAIL:** the counter climbs past four with no recreate, or the recreate fires while a session is
live or an exchange is in flight, or it fires more than the bound allows.

**Count these across the whole window and report them as numbers.** A recreate raises group churn,
and a poke's own connect raises an `ACL_CONNECTED` the auto-start receiver reads as the phone
arriving. Round 4 measured zero in its stuck window; round 5 measured two creates from the ordinary
path:

```bash
grep -c "MATCH! Starting AapService"  log.txt   # round 4: 0
grep -c "createGroup SUCCESS"         log.txt   # round 4: 0 in the stuck window; round 5: 2
grep -c "Attempting active poke"      log.txt   # round 5: 12
grep -c "ignored .* wake pokes"       log.txt   # round 5: 0, which is why S1 was inconclusive
```

**Then do the control.** Leave Gearhead stopped and keep watching: the recreates must stay bounded,
no more than four, then `phone still not connected after N recreations`. An unbounded recreate loop
is a FAIL even if the first half passed.

---

### J1. The refusal widening, which was wired to only one route

**Candidate only. This is the defect round 5's numbers proved without looking for it.**

`JoinRefusalPolicy` widens the poke gap for a phone that answers every message and then reports it
could not join: 15 s for the first refusal, 30 s from the second, 120 s from the fifth. Its only
production caller was the external-Bluetooth-module carrier. On the ordinary radio the counter
climbed, the log said retries were slowing down, and the gap stayed flat. Round 5's S1 measured it
without meaning to: **22 refusals over 257 s at roughly 21 s apart**, where the policy asks for two
or three attempts in that window.

`8441d044` wires it into the automatic poke loop. The settings-screen deferral and the
driver-selection wake rounds are deliberately left alone.

**The lever is a phone that reaches us over Bluetooth and cannot join the network we name.** Round
5's tight WiFi hold-down produces exactly that and is the cheapest way there; the hotspot-on-the-phone
shape the policy's own KDoc names works too. Either is fine, but say which.

**Measure the gaps, not the count.** Report the interval between consecutive
`NativeAA: Attempting active poke` lines, in order, for at least seven refusals.

**PASS:**

- Refusal 1: about 15 s to the next poke.
- Refusals 2 to 4: at least 30 s.
- Refusal 5 onward: at least 120 s.
- `NativeAA: the phone has refused this network 2 times in a row, so retries are slowing down` fires
  once, and the gap after it actually is wider.

**FAIL:** a flat 15 s cadence, which is the pre-fix behaviour, or a widening that never comes back
down. Release the lever at the end and confirm the next successful handshake returns the cadence to
normal, because the counter is reset by the phone getting on.

---

### T3. One last question about the video ceiling

**Candidate only, and cheap.** Round 5 showed the 256 ceiling is a property of
`debug-video-feed-hold-ms=200` and that the same route with the hold off sits at 2 to 5. That was
three windows. Give it one full session so the thread can stop asking.

`debug-video-feed-hold-ms=0`, the branch's own settings otherwise, one mock-drive route with media
playing, at least fifteen minutes. Report the **high-water `videoQueue` across every window**, the
`videoShed` total, and whether `AapTransport: the video thread is 256 messages behind, shedding` ever
fires.

**There is no FAIL condition.** If the high-water stays in single or low double digits, the ceiling is
unreachable in normal use and the question is closed. If it climbs, that is a new finding and worth
the round on its own.

Round 5 also measured that shedding costs whole fragment runs: 30
`AapVideo: fragment run lost bytes, requesting keyframe to recover stream` in 2.5 minutes at the
ceiling, with visible tiling that never cleared while load stayed high. That is the consequence of
losing reference frames, not a defect in the shed path, and it is only a concern if the ceiling turns
out to be reachable. T3 is what decides that.

---

## 5. Pre-registered UNTESTABLE on this rig

Unchanged from round 5 section 5, and all still apply: anything needing 5 GHz or a cross-band split,
the positive `wifi-direct-stable-identity` case, `deletePersistentGroup`, the USB bus-composition
rework, round 3's Part P, and whether the phone counts the flow-control window in messages or frames.

Added this round:

- **Whether refusing the dial makes an already-poisoned Gearhead fall back to Bluetooth.** W1 can
  observe it but cannot make it happen: no WPP message retires a stored endpoint, so the phone's own
  behaviour decides, and one observation on one phone is not a verdict.
- **The `WppTcpServePolicy` advertise case.** D-SAM is below API 29 and cannot keep a group name, so
  the serve-the-dial half of the policy has no path on this unit. Only the refusal is gradable here.

## 6. Report back

`audio-sink-jitter-round6-results.md`, format in `TESTING-TEMPLATE.md` section 7, one section per run
id above.

Four things this round wants named explicitly whatever the verdicts are:

1. **Whether any `WifiInfoResponse` went out on the TCP path in W1.** That single field is the whole
   fix.
2. **The distinct SSIDs delivered to the phone in N7r2**, as names, not a count.
3. **The poke-to-poke intervals in J1**, in order, as seconds.
4. **The `videoQueue` high-water in T3** across a full session, against round 5's 2 to 5 over three
   windows.

And carry four rig facts round 5 established into `TESTING-TEMPLATE.md` section 7a if they are not
there yet: D-SAM's `svc wifi disable; svc wifi enable` chained in one shell call producing a
`dumpstate`-shaped dump, so issue them separately with a pause; D-POCO's WiFi self-reverting only
after a single disable and not after a sustained hold; D-POCO's screen needing to be at home before
an automated bring-up is trusted, with `dumpsys window | grep mCurrentFocus` as the check; and that
forgetting the vehicle on the phone and the "Welcome to Android Auto" re-consent both need
`uiautomator dump` plus taps, with no adb-reachable trigger.
