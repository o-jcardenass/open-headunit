# field-ride-automation-btautostart — round 2 brief

Round 1 was a field report, not a run: three faults from a real ride, two source-traced. This round
tests the fixes. It is scripted end to end through the app's automation surface; nothing here needs
the screen touched.

## 1. Build and baseline

```bash
git fetch fork testing/automation-plus-btautostart
git checkout -B testing/automation-plus-btautostart fork/testing/automation-plus-btautostart
git log --oneline -10
```

Tip `1f06d8a8`, a merge of the field-ride fixes (`f1718991`) and the automation command surface
(`f625403b`). **History was rewritten**: the branch no longer descends from the round-1 build
`e9ab7f13`, and the five fix commits were replayed after that round, so every SHA round 1 cited is
stale. Build from the tip, not from a cached checkout.

Unit tests on this tip: 1266, all passing. Run `run_unit_tests.sh` before the device work; if it
disagrees, stop and report that instead of running the rounds.

## 2. What this is and why it exists

On the ride, pressing the WiFi button in mode 3 kept re-opening Android Auto's server prompt and only
connected after many presses; the audio sink never came up though the setting was on; and the
Bluetooth auto-start entry would not clear. All three came back to a chain that starts with the app
waking itself:

1. The Native-AA wake poke connects to the phone's HFP profile. That outbound `socket.connect()`
   makes the OS raise `ACL_CONNECTED` for the phone's own address.
2. `AutoStartReceiver` cannot tell that from the phone arriving, and brings `MainActivity` forward
   with a Bluetooth launch source.
3. `MainActivity` then forced a **Self Mode** launch. In mode 3 that can never succeed, because the
   projecting phone is not running its own head unit server, so it failed on `127.0.0.1:5277`.
4. That failure stopped the wireless launcher underneath a P2P group that was still coming up
   (three groups in twenty seconds), reported itself as a *user exit* so the auto-poke was suppressed
   for the rest of the group, and left `SelfLauncherManager.isActive` set. The wireless session that
   finally connected then announced system sounds only, with no media and no speech channel.

Separately, clearing the Bluetooth auto-start list undid itself: an empty list made the poke target
every paired device, and a completed handshake wrote the phone's address straight back into it.

The fixes: a Native unit never forces Self Mode on a Bluetooth auto-start; running out of launchers
reports without claiming a user exit or a disconnect; the audio-sink gate asks the session's own
endpoint instead of a flag that outlives a launch; and the wake poke gets its own setting so the
auto-start list is only ever written by the user.

## 3. What is different about this round

- **The trigger is the head unit's own Bluetooth adapter, not the phone's.** §7a: cycling the
  phone's radio raises no `ACL_CONNECTED` here, and cycling the head unit's own does, self-reverting
  about 14 s later. Every run below that needs `AutoStartReceiver` to fire uses that lever, and each
  one carries a check that it actually fired. Let the app launch and settle **first** — §7a is
  explicit that an adapter that is off when `NativeAaHandshakeManager.start()` runs kills the manager
  for the whole session.
- **R5 cannot reproduce its fault on this rig, and that is expected.** Port 5299 only enters
  TIME_WAIT after the phone has dialled it, which needs the WPP TCP endpoint to have been advertised,
  which this rig's re-addressing P2P group prevents. R5 is a regression guard only. Do not treat its
  PASS as evidence the bind fix works.
- **`shared_prefs/` may be root-owned** (§7a). R4 depends on what the app writes to disk, so it has a
  `stat` gate and a positive control; run them as written.
- **Do not assume a poke connects.** §7a: poke connectivity varies between sessions here. No run
  below has a PASS or FAIL that depends on `Successfully poked`.
- `settings.xml` carries the previous thread's non-defaults. Back it up, diff it, and state the delta
  in Setup notes even if it is zero.

## 4. Settings keys this round needs

Written with the app stopped, per §1. `native-poke-bt-macs` and `native-poke-all-paired` are new on
this branch.

| Key | Element |
|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` |
| `connection-modes` | `<set name="connection-modes"><string>wifi</string><string>self</string></set>` |
| `enable-audio-sink` | `<boolean name="enable-audio-sink" value="true" />` |
| `auto-start-bt-macs` | `<set name="auto-start-bt-macs"><string>PHONE_MAC</string></set>` |
| `native-poke-bt-macs` | `<set name="native-poke-bt-macs"><string>PHONE_MAC</string></set>` |
| `native-poke-all-paired` | `<boolean name="native-poke-all-paired" value="true" />` |
| `allow-external-configuration` | `<boolean name="allow-external-configuration" value="true" />` |
| `log-level` | `<int name="log-level" value="2" />` |

**INFO (2) is enough.** Every line this round decides on is an `AppLog.i`, `.w` or `.e`, and none is
behind a `LOG_VERBOSE` guard; VERBOSE only costs ring buffer on a unit whose driver stack floods it.

`connection-modes` must contain `self`. It is what makes the Self Mode half of a Bluetooth auto-start
eligible at all, so without it R1 passes for the wrong reason and R2 cannot run.

## 5. Using the automation surface

Everything below goes to one receiver, so no run needs the UI. Verified against this branch.

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
A=com.andrerinas.openheadunit

adb shell am broadcast -n $RX -a $A.ACTION_LOG_MARKER --es text "R1 start"   # ungated
adb shell am broadcast -n $RX -a $A.ACTION_QUERY_STATE                        # JSON on data=
adb shell am broadcast -n $RX -a $A.ACTION_NATIVE_AA_POKE --es extra_mac "PHONE_MAC"
adb shell am broadcast -n $RX -a $A.ACTION_DISCONNECT
adb shell am broadcast -n $RX -a $A.ACTION_STOP_SERVICE
adb shell am broadcast -n $RX -a $A.ACTION_START_WIRELESS
adb shell am broadcast -n $RX -a $A.ACTION_EXPORT_LOG --es path /sdcard/Download/rN-hur.txt
```

- **Mark every run.** `ACTION_LOG_MARKER` prints `AutomationMarker: <text>` at WARN and needs no
  config switch, so one capture can carry the whole round and the results can quote boundaries.
- **`ACTION_QUERY_STATE` replies on `data=`** because `am broadcast` sends ordered. Take one before
  and after each run; it is cheaper than reading state out of the log.
- **Session transitions also print** as `AapService: session state <state>`, with the reason in
  brackets. That line is the fastest way to see a wrong `user_exit`.
- `ACTION_EXPORT_LOG`, `ACTION_SET_SETTINGS` and `ACTION_GET_SETTINGS` need
  `allow-external-configuration`, which §4 seeds.
- **`ACTION_GET_SETTINGS` cannot answer R4.** It withholds `auto-start-bt-macs`,
  `auto-start-bt-name` and `native-poke-bt-macs` from any export, so R4 reads `settings.xml`
  through `run-as` and nothing else.

## 6. The lines that decide every run

Verified with `grep -F` against `1f06d8a8`.

| Line | Where it comes from |
|---|---|
| `AutoStartReceiver.onReceive \| MATCH! Starting AapService via Bluetooth Auto-start...` | the trigger fired |
| `MainActivity: Bluetooth auto-start: leaving Self Mode alone` | the fix took the decision |
| `MainActivity: Bluetooth auto-start: forcing a Self Mode launch` | the pre-fix behaviour |
| `SelfMode: All launchers failed` | a doomed Self Mode launch ran |
| `AapService: userExitedAA is true. Skipping auto-poke.` | the poke suppressed by a false user exit |
| `AapService: session state disconnected (user_exit)` | the false user exit, as automation sees it |
| `AapService: Self Mode disconnected. Not restarting` | the wireless launcher teardown path |
| `; the launch is still in flight, so the wireless launcher stays` | the teardown declined |
| `WifiDirectManager: <band> createGroup SUCCESS!` | one group per bring-up, not three |
| `Media Sink Setup Request: 1 on channel AUDIO2` | system sounds, always announced |
| `Media Sink Setup Request: 1 on channel AUDIO1` | speech; absent before the fix |
| `Media Sink Setup Request: 1 on channel AUDIO` | media; absent before the fix |
| `NativeAA: Saving <mac> (<name>) as the wake poke device.` | the poke target adopted a phone |
| `NativeAA: No wake poke device selected, so nothing is poked.` | empty target, widening off |
| `WppTcpServer: listening for Android Auto on TCP 5299` | the bind succeeded |
| `WppTcpServer: could not listen on 5299` | the bind failed |
| `WirelessServer: Incoming connection detected from /192.168.49.` | the phone reached the session |

Channel names come from `Channel.name()`: media is `AUDIO`, speech is `AUDIO1`, system is `AUDIO2`.
The first number is the request type and varies; match on the channel name.

Two of these are built from interpolated strings, so grep the stable part: `createGroup SUCCESS!`
(the band label or `Standard` comes first) and `on channel AUDIO1` (the type number comes first).

## 7. Runs

Standing setup for every run: settings written per §4 with the app stopped, capture started with
`stdbuf -oL` before the launch, head unit brought up before the phone and given ~15 s to settle.

### R1 — a Bluetooth auto-start in Native mode leaves Self Mode alone (**the point of the round**)

1. Settings per §4, app stopped. Phone on and paired.
2. Launch `MainActivity`, wait for `createGroup SUCCESS!` and the RFCOMM listeners, ~15 s.
3. Marker `R1 trigger`. `adb shell svc bluetooth disable` **on the head unit**. Wait 40 s for the
   self-revert and the `ACL_CONNECTED` it raises.
4. Marker `R1 end`. `ACTION_QUERY_STATE`.

**Reachability gate, check first:** `MATCH! Starting AapService via Bluetooth Auto-start...` must
appear after the `R1 trigger` marker. Without it the trigger never fired and R1 is INCONCLUSIVE, not
a PASS — every condition below is satisfied by an auto-start that simply never happened.

**PASS**, all of:
- `Bluetooth auto-start: leaving Self Mode alone` present after the marker.
- `forcing a Self Mode launch` absent.
- `SelfMode: All launchers failed` absent.
- `userExitedAA is true. Skipping auto-poke.` absent.
- `session state disconnected (user_exit)` absent.
- At most one `createGroup SUCCESS!` after the marker.

**FAIL** if a forced Self Mode launch appears, or if the group is recreated more than once.

Report the count of each of those six, not just the verdict.

### R2 — the same trigger in Helper mode still launches Self Mode (positive control)

Settings-only change on the same build: `wifi-connection-mode` to `2`. Everything else as R1, same
trigger.

**PASS**: `forcing a Self Mode launch` present, `leaving Self Mode alone` absent. The launch is then
expected to fail and to log `All launchers failed`; that is the control working, not a fault.

This is what proves R1 narrowed the behaviour rather than deleting it. If R2 also says
`leaving Self Mode alone`, R1's PASS is worthless and the veto is too wide.

Restore `wifi-connection-mode` to `3` afterwards.

### R3 — the media and speech sinks are announced on a wireless session

Needs a real Native AA session. Settings per §4.

1. Bring the session up by whatever route works on the day. Confirm
   `WirelessServer: Incoming connection detected from /192.168.49.` and `session state projecting`.
2. Marker `R3 session up`.

**PASS**: all three of `on channel AUDIO2`, `on channel AUDIO1` and `on channel AUDIO` present for
this session. Before the fix only `AUDIO2` appeared.

**Also record**, because it is what makes the PASS mean something: whether
`Self Mode is projecting this device to itself` or `Audio sink is off in Settings` appears. Either
means the announcement was skipped for a stated reason and the run did not exercise the fix.

If no session forms, R3 is INCONCLUSIVE. Do not substitute a Self Mode session for it: Self Mode is
the one case that still drops those two channels on purpose.

### R4 — clearing the Bluetooth auto-start entry sticks

**Gate first**, per §7a:

```bash
adb shell run-as com.andrerinas.headunitrevived stat -c '%U:%G %a' shared_prefs
```

If that is not the app's own uid, `chown` it before continuing. If it cannot be fixed, R4 is
INCONCLUSIVE and says so: on a root-owned prefs directory the app's writes never land, so "the
auto-start list stayed empty" is true whatever the code does.

1. App stopped. Write `auto-start-bt-macs` and `native-poke-bt-macs` as **empty sets**, everything
   else per §4. Back up `settings.xml`.
2. Launch, let a full handshake complete with the phone.
3. Marker `R4 handshake done`. Force-stop, then read both keys back through `run-as`.

**PASS**, both:
- `native-poke-bt-macs` now contains the phone's address, and
  `NativeAA: Saving <mac> (<name>) as the wake poke device.` is in the log.
- `auto-start-bt-macs` is still absent or empty.

The first half is the positive control: it is the same file and the same code path, so a write that
lands there proves the second half is a real negative and not a read-only prefs directory.

**FAIL** if `auto-start-bt-macs` gained the address. **INCONCLUSIVE** if neither key was written.

### R5 — port 5299 still binds every bring-up (regression guard only)

1. From a running app: `ACTION_STOP_SERVICE`, wait 5 s, launch `MainActivity` again. Repeat three
   times, marking each cycle.
2. **PASS**: `WppTcpServer: listening for Android Auto on TCP 5299` once per bring-up, and
   `could not listen on 5299` absent throughout.

§3 already says this cannot reproduce the original `EADDRINUSE` here, because nothing dials 5299 on
this rig. Report it as a guard.

## 8. Do not re-run

- Anything from round 1. It was a field report on a build that no longer exists.
- The keyboard, video and audio-focus threads. Nothing here touches them.

## 9. Report back

Three numbers decide whether this ships:

1. **R1**: the count of `forcing a Self Mode launch` and of `createGroup SUCCESS!` after the trigger.
   Both should be zero and one.
2. **R2**: whether the control reproduced the old behaviour. A PASS on R1 with a PASS on R2 is the
   result; a PASS on both halves in the same direction is not.
3. **R3**: which of the three audio channels were announced.

Then R4's two key values, quoted from `settings.xml`, and R5's bind counts.

If a run is INCONCLUSIVE, say which gate it failed rather than giving a verdict on the branch.
