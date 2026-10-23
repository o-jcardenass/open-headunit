# native-aa-wireless round 4

**Candidate:** `fork/fix/native-aa-wireless` at `181f8e16`, **four commits** on `main` (`80a81099`),
**2266** JVM tests. The first three are round 3's, unchanged; `181f8e16` is round 3's `ec7c84f8` with
the two fixes below folded into it, same tree as the six-commit tip it was compacted from. Round 3's
`ec7c84f8` is on no ref any more, so install from `181f8e16` and record its own md5; the three
verdicts round 3 reached are unchanged by the fold, which only adds.
**Baseline:** none. Every run below is candidate only.
**Unit:** D-SAM (SM-T230, API 19) with D-POCO as the phone, for all three runs. Nothing here is
measurable above the naming API, so D-HU is not needed.
**Read first:** `native-aa-wireless-round3-results.md`. This round grades the two commits its
evidence produced, and re-checks the one behaviour they could have broken.

## What this round is about

Round 3's own captures answered the question R24 was written to ask, and turned up a second defect
on the way. Both fixes are in `181f8e16` and neither has run on hardware.

**The identity half.** A stored verdict is handed straight back on every bring-up that
adopts a surviving group, and nothing re-measures it until a genuine create. Round 3's operator had
to hand-write `wifi-direct-last-identity-verdict=RENAMED` between the two arms for exactly this
reason. In `r22_candidate_capture.txt` the poisoned window produced **18** credential deliveries
reading `identity stable=yes`, and what kept them off the wire was that no phone happened to run a
handshake in those two minutes: the only `[TX] Sending WifiVersionRequest` in the whole capture is
after the reset. Below the naming API, a name and address that both repeat is now `RENAMED` on the
read path as well as the create path, because no API there could have made the name come back.

**The exit half, and it is what R20 actually was.** Round 2's two failed deep-link
exits are the same shape to the millisecond:

```
01:09:17.522 setActive | Initializing WiFi Mode: NATIVE        the deep link started the service
01:09:18.033 NativeAA: wake poke starting                      and it poked the phone
01:09:18.043 Stop action received
01:09:18.143 AapService destroying...
01:09:18.163 WifiDirectManager: Stopping and cleaning up...    isGroupOwner false, so no removeGroup
01:09:18.193 supersededByStop | credential refresh abandoned
01:09:18.193 a group named DIRECT-Eo... reading it instead of tearing it down.
01:09:18.203 onConnectionInfoAvailable | Group formed. Owner: true
```

`headunit://exit` on a stopped app starts the service, whose `onCreate` arms the whole stack, so the
stop lands half a second into a bring-up. `stop()` runs before ownership is established and skips its
removal, and the in-flight `requestGroupInfo` callback then adopts the group on a manager that has
already stopped. The third exit in that same capture, where the bring-up had settled 8 s earlier,
logged `Final group removal success` and the group went. No session was live at either failed exit,
so `ServiceStopWaitPolicy` was answering correctly throughout and R24's refutation of the brief's
paper explanation was right.

It gives that callback the same `supersededByStop(gen, ...)` guard the other thirteen
`requestGroupInfo` continuations in the file already have. **It does not make the group go away**,
and R26 below exists to record that rather than to grade it: the removal is skipped because
`isGroupOwner` is still false, which is a separate change and wants its own round.

## Greps, in addition to round 3's

```bash
# the verdict on every bring-up, with its reason
grep -nE "group identity ssid=" log.txt
# the adopt decision, and the new refusal that replaces it after a stop
grep -nE "already up from before this bring-up|adopt-or-create decision abandoned" log.txt
# the teardown, and its outcome. AppLog.d, so capture at DEBUG or above or this is invisible
grep -nE "Stop action received|Stopping and cleaning up|Final group removal" log.txt
# an exit that pokes the phone on its way out
grep -nE "wake poke starting|Calling socket.connect\(\)" log.txt
# the endpoint, which is what a wrong verdict releases
grep -nE "WifiVersionRequest|wpp_info|not advertising WPP over TCP" log.txt
```

```bash
# the persisted verdict and count, app stopped
adb shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml \
  | grep -oE 'wifi-direct-(last-identity-verdict|group-name-changes)[^/]*'
```

**This round's captures go to a new per-thread release**, `rig-evidence-native-aa-wireless`, as
`native-aa-wireless-round4-captures.zip`. Rounds 1 to 3 have a release each and stay as they are;
`TESTING-TEMPLATE.md` §7 carries the rule and the commands. Cite the release, the asset filename and
the sha256.

**Capture at DEBUG or above for every run.** `Final group removal success` and
`Final group removal failed: N` are both `AppLog.d`. Round 2's R20 could not see either, which is
why it read a skipped removal as a re-arm and cost this thread two rounds.

### R25: a stored stability the unit cannot have is not handed to the phone

D-SAM, candidate. **The point of this round.**

Start from a known state. With the app stopped, read `wifi-direct-group-name-changes` and confirm it
is at or above 3, which it will be on this unit. Then, still with the app stopped, write
`wifi-direct-last-identity-verdict=STABLE`. That is the poisoned state a unit carries after running
any build of this branch before `ec7c84f8`, reproduced deliberately.

1. Launch, and force-stop and relaunch until a bring-up logs `already up from before this bring-up;
   reading it instead of tearing it down`. Quote that line and the `group identity` line that follows
   it, with timestamps.

- **PASS**: the `group identity` line reads `stable=no (the platform names it)` and its reason names
  the platform having picked the name on N creates. The adopt line comes first, as round 3 measured.
- **FAIL**: `stable=yes`, or a reason reading `same name and same BSSID as the last group`.
- **Not a FAIL, and do not grade it as one**: `wifi-direct-last-identity-verdict` still reads
  `STABLE` in `settings.xml` afterwards. A read deliberately writes nothing back, so only a genuine
  create replaces the stored value. What this run grades is the verdict that reaches the wire, not
  the one on disk. Record both.

2. Then let a handshake run to a session on that adopted group. D-SAM auto-connects unattended.

- **PASS**: no endpoint goes out, the handshake logs a withholding, and
  `Providing credentials to listener` reads `identity stable=no`.
- **FAIL**: an advertised endpoint, or credentials delivered with a stable identity. This is the
  phone-side damage the whole verdict exists to prevent and it is the check that matters most.

### R26: an exit that starts the app does not leave a stopped manager owning a group

D-SAM, candidate. This reproduces round 2's R20 part 2 exactly, on the fix.

1. Force-stop the app, leaving the WiFi Direct group up (confirm with `dumpsys wifip2p` that
   `mGroup network:` is present). Then send `headunit://exit` with nothing else running, so the deep
   link itself starts the service. Poll `dumpsys wifip2p` every 2 s for 10 s afterwards.

- **PASS**: `the adopt-or-create decision abandoned - the manager was stopped while it was in
  flight.` appears after `Stopping and cleaning up...`, and **no** `already up from before this
  bring-up` and **no** `Group formed. Owner: true` follow the stop.
- **FAIL**: either of those two lines lands after the stop, which is the callback still adopting on a
  stopped manager.
- **Expected, and not a FAIL**: the group is still up in the poll. The removal is skipped while
  ownership is unestablished, which this commit does not address. Quote the poll either way. This run
  is the baseline the next change is measured against.
- Also count `wake poke starting` and `Calling socket.connect()` after the `Stop action received`
  line and report the number. An exit that takes the phone's hands-free link on its way out is the
  other half of the same race and is likewise untouched here. It is a measurement, not a verdict.

### R27: the ordinary exit still removes the network

D-SAM, candidate. The regression check, because R26's guard sits on the path R24 walked.

1. Launch and let a bring-up settle for at least 20 s, with or without a session. Confirm a
   `group identity` line has been logged for it. Then send `headunit://exit` and poll the same way.

- **PASS**: `Final group removal success` appears, and `mGroup` is gone within 5 s. This is round 3's
  R24 repeated on the new tip.
- **FAIL**: no removal line, or the group still present at the end of the poll. That would mean the
  guard swallowed a legitimate adopt and cost the exit its removal.

2. Repeat once with a session live (let the phone connect, confirm `SSL handshake complete`, then
   exit). Quote the delay from `Stop action received` to `Stopping and cleaning up...` in both
   halves: round 3 measured 180 ms with no session and 1972 ms with one, and that gap is
   `ServiceStopWaitPolicy` doing its only job.

## A note for whoever runs this on D-POCO

Two failure signatures found while troubleshooting round 3, both phone-side and neither a candidate
defect. Check for them early rather than reading them as the build. A stale `WifiNetworkSpecifier`
retry loop: `ConnectivityService` keeps requesting an SSID from an earlier round that no longer
exists, visible in `dumpsys` and in Gearhead releasing and re-requesting it every few seconds, and
neither `KEYCODE_HOME` nor a WiFi toggle clears it. Clearing Android Auto's app data does, and then
raises the second one: Gearhead's own first-run wizard (`TapHeadUnitActivity`, "To continue, select
Android Auto on your vehicle screen") waiting on notification access, which the data clear revoked.
`adb shell am start -a android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` opens straight to it.
