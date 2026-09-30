# bt-auto-start-disconnect, round 1: does a chosen Bluetooth device now start and end the session, in every mode but USB?

**Candidate:** `fork/feat/bt-auto-start-and-auto-disconnect` @ `f0c20b8a` (2 commits on `0a9a98d9`), `3.3.0`
**Baseline:** none built. The only behaviour the branch changes that already existed is the Native AA re-arm on `ACL_CONNECTED`, and the parent's version of that is on record in `wifi-direct-stable-identity-round1-results.md` R2 (the `headunit://disconnect` + HU adapter cycle recipe). R3 below repeats it on the candidate; that is the regression check.

```bash
git fetch fork
git rev-parse fork/feat/bt-auto-start-and-auto-disconnect    # f0c20b8a...
git rev-parse fork/feat/wifi-direct-stable-identity           # 0a9a98d9...  the parent, rebuilt 2026-09-02
```

| SHA | What |
|---|---|
| `ae35e6a5` | "Auto-start on Bluetooth" is no longer WiFi-only: the row shows whenever WiFi or Self Mode is a selected transport, the service arms a non-Native wireless launcher when nothing is armed, and `MainActivity` forces a Self Mode launch for the Bluetooth launch source. Native's forced re-arm is unchanged. USB is untouched on purpose |
| `f0c20b8a` | "Auto-disconnect on Bluetooth": a second device list, a grace delay, a runtime `ACL_DISCONNECTED` / `ACL_CONNECTED` receiver in `AapService`, and a session end that behaves like the Exit button and then keeps the wireless bring-up down |

Read `TESTING-TEMPLATE.md` first, and §7a before planning. This brief carries only what is specific to the branch.

> **The parent branch was rebuilt on 2026-09-02 and this branch was rebased onto it**, so both SHAs
> here are new and no APK on record matches them. The parent now carries four commits this branch
> has never been tested with, including one that changes the very `ACTION_BT_AUTO_START` handler
> commit 1 rewrites: a Bluetooth arrival landing while the WiFi Direct group is still being created
> now does nothing at all rather than re-arming the mode. `BtAutoStartRearmPolicy.actionsFor` gained
> a `networkComingUp` question for it. R3 is where that meets hardware; if R3 shows a re-arm that
> should have happened and did not, the new line
> `AapService: Bluetooth auto-start: the Native AA group is still being created, so it is left to answer.`
> is the one to grep for. **If rig time is short, run `wifi-direct-stable-identity-round4-brief.md`
> first** - it tests the parent directly, and a failure there invalidates this round's base.

---

## 1. Why this round exists

Two reporters asked for the same automation. One runs a head unit with its own battery: parked next to the car, it never ends its session because nothing tells it the drive is over, and a charger-based trigger flaps when the battery is full. The other drives a motorbike and wrote two automation macros by hand: start Android Auto's head unit server and open this app when the bike's Bluetooth connects, stop it and go home when it disconnects. A commercial wireless dongle ships the same thing as a "Start/Stop" setting keyed on the car's Bluetooth device.

The start half already existed, but it was WiFi-only twice over: the settings row was hidden unless WiFi was a selected transport, and the service did nothing with the trigger in any wireless mode but Native AA. The disconnect half did not exist. Both are now general, meaning the wireless modes and Self Mode. USB stays out because it has its own attach and detach.

Two facts about Native AA shaped the disconnect rule, and both are things this rig can show:

- **The app's own Bluetooth work raises the events it now watches.** The wake poke opens a socket to the phone, holds it 15 s and closes it, every cycle while no session is up. The handshake socket closes seconds after the handoff. Both are real `ACL_CONNECTED` / `ACL_DISCONNECTED` broadcasts for the phone's address. So the auto-disconnect **does nothing without a session, and nothing within 60 s of one starting**. Those two rules are the branch; R4 and R5 are where they meet hardware.
- **`userExitedAA` is cleared on USB attach and detach**, so "stay down" is not that flag. After the session ends the wireless launcher is stopped outright, in every mode, and only a Bluetooth auto-start, the Home screen or a service restart brings it back.

Three questions, in order of value:

1. **Does a watched device leaving end a settled session, and does the head unit then stay quiet?** R1.
2. **Does the phone's own Bluetooth link to this unit survive the session at all?** If the phone's ACL drops after the handshake socket closes and never returns, watching the phone can never fire on this unit, and the settings hint should say so. R4 measures it. It is the finding of the round whichever way it goes.
3. **Does the whole cycle close on Self Mode, which is the motorbike reporter's case?** R6.

---

## 2. What is different about this round

- **`MATCH! Starting AapService` is the subject, not a contamination.** Every run that cycles the head unit's adapter is meant to produce one. The discard rule for this round is the narrow one from §7a: a second `createGroup SUCCESS` where one session was expected. Report the `MATCH!` count anyway.
- **One lever raises both events on this rig: the head unit's own adapter.** `svc bluetooth disable` on D-HU raises `ACL_DISCONNECTED` for the phone at once and, when the adapter self-reverts about 14 s later, a real `ACL_CONNECTED` (§7a). Cycling the phone's radio raises the disconnect but, on this rig, never the reconnect. So: the phone's radio is the "device left" lever (R1), the head unit's adapter is the blip lever (R2) and the "device came back" lever (R3, R6).
- **The watched device on D-HU can only be the phone.** D-HU is bonded to nothing else that can be switched off on command. That makes R4's question live in every D-HU run: the phone's ACL must still be up when the trigger fires, or nothing fires.
- **The 14 s self-revert is a clock.** With the default 5 s delay an adapter cycle ends the session at +5 s and re-arms it at about +14 s. With a 30 s delay the same cycle is a blip that cancels. R2 and R3 are the same command with a different delay key.
- **INFO is enough.** Every line this round reads is `AppLog.i`. `log-level=2`.
- **`shared_prefs/` is root-owned on D-HU** and `auto-start-bt-macs` is read by the manifest receiver from a *second* file, the device-protected mirror. §3 says how to check both.
- **D-POCO's head unit server on `:5277` is down.** The previous round force-stopped Gearhead on D-POCO during its cleanup, and only the AA Developer-settings "Start head unit server" toggle brings it back. R6 and R7 need it. Ask the operator for that one toggle **before** the round starts, as earlier Self Mode rounds did, and check it with the command in §3 first.
- **Three new settings keys.** `auto-start-bt-macs` already exists and already holds D-POCO's address on D-HU (that is how the previous round's reconnects worked); verify rather than assume.

| Key | Type | Meaning |
|---|---|---|
| `auto-disconnect-bt-macs` | string set, default empty | The devices to watch. Empty is the off switch, exactly like the auto-start list |
| `auto-disconnect-bt-delay-seconds` | int, default 5 (absent = 5) | Grace period. 0 ends the session at once; clamped to 3600 |
| `auto-start-bt-macs` | string set | Unchanged key, now general. Also the Native poke target list, as before |

A string set in `settings.xml` looks like this, on the `</map>` line as §1 of the template requires:

```xml
<set name="auto-disconnect-bt-macs"><string>AA:BB:CC:DD:EE:FF</string></set>
```

The address is the one `BT Device connected: <name> (<address>)` prints, upper case, colon separated. Check `set_hu_prefs.sh` handles a `set` element before relying on it; the template's element-scoped delete does not match one (`<set ...>` spans to `</set>`). If it cannot, pull the file, edit on the PC, push back as root and `chown` it as the script does.

---

## 3. Preparation

### Step 0: identities and preconditions

```bash
HU=<D-HU serial>; PH=<D-POCO serial>; PKG=com.andrerinas.headunitrevived

adb -s $HU shell dumpsys bluetooth_manager | grep -iA 20 "Bonded devices"     # D-POCO's address
adb -s $PH shell dumpsys bluetooth_manager | grep -iA 20 "Bonded devices"     # D-HU's address
adb -s $PH shell netstat -tln | grep 5277                                     # R6/R7 gate
adb -s $PH shell appops get $PKG SYSTEM_ALERT_WINDOW                          # must be allow for R6
```

If `:5277` is not listening, R6 and R7 are UNTESTABLE until the operator toggles it; run everything else first. If the overlay op on D-POCO is not `allow`, set it: `adb -s $PH shell appops set $PKG SYSTEM_ALERT_WINDOW allow`. Without it Android 15 blocks the receiver's activity launch and the Self Mode half of an auto-start never runs; that is the app's existing limit, not the branch's.

### The device-protected mirror

`AutoStartReceiver` reads the auto-start list from `/data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml`, not from `settings.xml`. The app copies the list there on every start after unlock (`App.initUnlockedOnce`). So after writing `auto-start-bt-macs` into `settings.xml`, launch the app once and confirm the mirror carries the address before relying on a cold-start `MATCH!`:

```bash
adb -s $HU shell cat /data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml | grep -A2 auto-start-bt-macs
```

If it does not (the same root-ownership that blocks the app's other writes may block this one), write the mirror by hand the same way. Say in Setup notes which it was. The auto-disconnect list has no mirror; the service reads `settings.xml` directly.

### Settings

Written with the app stopped, read back before the first run counts.

**D-HU, every run (R1 to R5, R8, R9):**

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<int name="log-level" value="2" />
<boolean name="native-wifi-version-exchange" value="false" />
<boolean name="kill-on-disconnect" value="false" />
<set name="auto-start-bt-macs"><string>D-POCO</string></set>
<set name="auto-disconnect-bt-macs"><string>D-POCO</string></set>
```

Leave the WiFi Direct identity, band and channel keys as the previous round left them (`wifi-5ghz-channel=36`, `stand-down-station-for-wifi-direct` absent). `auto-disconnect-bt-delay-seconds` absent for R1 and R3 (default 5), `30` for R2, absent again after. R8 changes the mode; R9 deletes the disconnect list. Each run says.

**D-POCO, R6 and R7:**

```xml
<int name="log-level" value="2" />
<set name="connection-modes"><string>self</string></set>
<boolean name="auto-start-self-mode" value="false" />
<boolean name="kill-on-disconnect" value="false" />
<set name="auto-start-bt-macs"><string>D-HU</string></set>
<set name="auto-disconnect-bt-macs"><string>D-HU</string></set>
```

`connection-modes` is set to Self only so the Bluetooth auto-start does not also arm D-POCO's mode 1 discovery (the actions line must say `armWirelessIfIdle=false`); `auto-start-self-mode` is off so the only thing that can launch Self Mode is the trigger under test. D-POCO's prefs are not root-owned; `set_pref.sh` (the phone-side writer) or a pushed script both work. Back the file up first and restore it byte-identical afterwards, including `connection-modes`.

Diff `settings.xml` against a fresh backup on both devices at the start and state the delta even if zero.

### Build gate

One APK, md5 recorded, identity checked by symbol:

```bash
unzip -p <candidate.apk> 'classes*.dex' | strings | grep -cF 'BtAutoDisconnectPolicy'   # > 0
unzip -p <candidate.apk> 'classes*.dex' | strings | grep -cF 'BluetoothDevicePicker'    # > 0
```

---

## 4. The lines that decide every run

Verified with `grep -F` against `f0c20b8a`. All INFO. Every auto-disconnect line starts `AapService: Bluetooth auto-disconnect:`, so `grep -ac "Bluetooth auto-disconnect:"` is the count of everything the feature said.

**The receiver, in the service:**
```
Registered Bluetooth auto-disconnect receiver
```

**A watched device leaving (the `$mac` is the watched address):**
```
AapService: Bluetooth auto-disconnect: AA:BB:CC:DD:EE:FF went away; ending the session in 5000ms unless it comes back.
AapService: Bluetooth auto-disconnect: AA:BB:CC:DD:EE:FF went away, but nothing is projecting; leaving the connection stack alone.
```

**The device coming back inside the delay:**
```
AapService: Bluetooth auto-disconnect: AA:BB:CC:DD:EE:FF is back; the pending disconnect is cancelled.
```

**The delay running out. One of these, always:**
```
AapService: Bluetooth auto-disconnect: AA:BB:CC:DD:EE:FF stayed away; ending the session the way the Exit button does.
AapService: Bluetooth auto-disconnect: not ending the session for AA:BB:CC:DD:EE:FF (up=true, age=12345ms).
```
`age=` is milliseconds since the session reached Connected; under `60000` the session is left alone by design.

**After the session ends, in order:**
```
AapService: Native AA user exit. Stopping active launcher.          (Native only)
AapService: Self Mode disconnected. Not restarting.                 (Self Mode only)
AapService: User exit cooldown active for 5000ms
Hiding reconnecting overlay - the session ended                     (the projection screen closing)
AapService: Bluetooth auto-disconnect: keeping the wireless bring-up down until something re-arms it.
```

**The auto-start, per mode. The receiver first, then the service's decision as a record:**
```
BT Device connected: <name> (AA:BB:CC:DD:EE:FF)
MATCH! Starting AapService via Bluetooth Auto-start...
AapService: Bluetooth auto-start: BtAutoStartActions(clearUserExit=true, forceRearmWireless=true, armWirelessIfIdle=false)
WifiLauncher: Initializing WiFi Mode: NATIVE
```
`forceRearmWireless=true` only ever appears for Native. In Helper mode with a healthy group it reads `forceRearmWireless=false, armWirelessIfIdle=false`, and in Self Mode `(clearUserExit=true, forceRearmWireless=false, armWirelessIfIdle=false)`. When a session is up the actions line is **absent**: the record is not printed when it does nothing.

**The activity's half (D-POCO, Self Mode):**
```
App launched via: Bluetooth auto-start
Auto-connect: begin (Bluetooth auto-start, mode=PILL)
MainActivity: Bluetooth auto-start: forcing a Self Mode launch
SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...
```

**Unchanged lines the counts use:** `createGroup SUCCESS`, `WirelessServer: Incoming connection detected`, `SSL handshake complete` (the INFO one, from `AapSslContext`), `NativeAA: Attempting active poke`, `NativeAA: Successfully poked`, `NativeAA: session is up — cancelling the poke retry loop`, `AapService: Disconnected. Restarting discovery loop in 2s...`, `p2p-wlan0-N`.

**Phone side (R6):** `GH.WifiBluetoothRcvr: Connection action: ... ACL_CONNECTED` on D-POCO corroborates that the head unit's adapter revert reached the phone as an ACL event, so the run does not rest on our own receiver to prove our own receiver fired.

**Silence, the thing R1 is really about.** After the stand-down line, in a 60 s window: `createGroup SUCCESS` does not increase, no new `Initializing WiFi Mode`, no `Attempting active poke`, no `Restarting discovery loop`, and `netstat -tn | grep 5288` shows no `ESTABLISHED`.

---

## 5. Runs

### R0: build gate and unit tests

Candidate **1208 / 0**. Named classes: `BtAutoStartRearmPolicyTest` (18), `BtAutoDisconnectPolicyTest` (10). Both DEX symbols above present. A failure here stops the round.

### R1: a watched device leaves and the session ends (D-HU, Native, the point of the round)

Keys as §3, delay absent (5 s). Clean-run protocol: head unit first, phone's Bluetooth back on, session forms. **Wait until `SSL handshake complete` is at least 75 s old**, so the 60 s rule cannot be the reason nothing happens. Then, in this order:

1. Confirm the phone's link is still up on D-HU: `dumpsys bluetooth_manager | grep -iE "Connected|Active Device"`. Record it. If the phone is **not** connected at the Bluetooth level here, note it and see R4: the trigger below cannot fire, and R1 becomes R1b.
2. `adb -s $PH shell svc bluetooth disable`, and confirm it took (`dumpsys`, not `settings get`).
3. Watch 30 s, then leave the head unit alone for a further 60 s.

PASS when all of:
- `went away; ending the session in 5000ms` within 10 s of step 2, naming D-POCO's address;
- `stayed away; ending the session the way the Exit button does` about 5 s later;
- then `Native AA user exit. Stopping active launcher.`, `User exit cooldown active`, `Hiding reconnecting overlay - the session ended`, `keeping the wireless bring-up down until something re-arms it`, in that order;
- the 60 s silence set from §4 holds, and `createGroup SUCCESS` for the run is exactly 1.

FAIL if the session is still up 30 s after step 2 with the arm line present, or if any re-arm line appears in the silence window. If the arm line never appears and step 1 showed the link already down, this lever cannot reach the code on this unit: mark R1 INCONCLUSIVE by this lever and run **R1b**.

**R1b (only if R1's lever never fired):** same keys, same settled session, but the lever is `adb -s $HU shell svc bluetooth disable`. Expect the same line sequence; the difference is that about 14 s after the disable the adapter self-reverts, raises `ACL_CONNECTED`, and R3 happens on its own. Score R1b on the same conditions with the silence window cut at the `MATCH!` line, and record where in the sequence the `MATCH!` landed.

Bring the phone's Bluetooth back on before R2 and verify it (§7a: the toggle does not restore radios reliably).

### R2: a blip does not end the session (D-HU, Native)

`auto-disconnect-bt-delay-seconds=30`. Settled session as R1 (older than 75 s). Lever: `adb -s $HU shell svc bluetooth disable`. Watch 45 s.

PASS when: `went away; ending the session in 30000ms`, then within about 20 s `is back; the pending disconnect is cancelled`, then `MATCH! Starting AapService` with **no** `Bluetooth auto-start: BtAutoStartActions` line after it (a live session prints nothing), and the session survives: no `stayed away`, no `Hiding reconnecting overlay`, `netstat` still `ESTABLISHED` on 5288, throughput windows continuing across the whole 45 s. `createGroup SUCCESS` = 1.

If the adapter revert raises no `ACL_CONNECTED` this time (the §7a entry says it does, every time so far), the run is INCONCLUSIVE and the cancel path stays on `BtAutoDisconnectPolicyTest`; say so. Restore the delay key (delete it) afterwards.

### R3: the device comes back and Native re-arms (D-HU, Native, the regression check)

Delay absent (5 s). Settled session. Lever: `adb -s $HU shell svc bluetooth disable`. Watch 60 s.

This is R1's ending followed by the previous round's reconnect recipe, from one command. PASS when: the R1 line sequence through `keeping the wireless bring-up down`, then `MATCH!`, then `Bluetooth auto-start: BtAutoStartActions(clearUserExit=true, forceRearmWireless=true, armWirelessIfIdle=false)`, then `Initializing WiFi Mode: NATIVE`, then a second session (`Incoming connection detected`, `SSL handshake complete`). Report the time from `MATCH!` to the second `SSL handshake complete`; the previous round measured this recipe at about 2 s (baseline) and 10 s (candidate, `stable=no` costs the phone a saved-profile retry), so anything in that band is the parent's behaviour. `createGroup SUCCESS` = 2, one per session.

Between the fire line and the `MATCH!` there must be no bring-up of any kind. That gap is the stand-down working; measure it (expect about 9 s).

### R4: the first minute of a session (D-HU, Native, the finding of the round)

No lever. Every D-HU session in this round starts with 60 s that answer this; evaluate R1's and R3's first sessions and report both.

From `SSL handshake complete` to +60 s, exactly one of these happens:
- **A:** no `Bluetooth auto-disconnect:` line at all. The phone's link to this unit survived the handshake socket closing.
- **B:** `not ending the session for <D-POCO> (up=true, age=Nms)` with N below 60000, and the session survives. The link dropped after the handoff and the 60 s rule held.

Either is a PASS. **FAIL** is a `stayed away; ending the session` line inside the first 60 s. Then at +30 s and at +75 s, `dumpsys bluetooth_manager` on D-HU: is the phone connected (which profiles)? If B, and the phone is still not connected at +75 s, then R1's lever cannot work on this unit and that is the reporter-facing finding: **watching the phone only works where the unit keeps a profile with it through the session.** Report which of A/B, the `age=` value, and the two dumpsys reads.

### R5: the poke does not stand anything down (D-HU, Native, opportunistic)

No dedicated run. In every D-HU capture, for each `NativeAA: Successfully poked` with no session up at the time, expect a `went away, but nothing is projecting; leaving the connection stack alone` about 15 s later (the hold ends, the socket closes) and the poke loop still printing `Attempting active poke` afterwards. `grep -ac "nothing is projecting"` per capture, beside the `Successfully poked` count.

The §7a note says pokes rarely connect on this rig because the phone's own reconnect wins. If you want one on purpose, the §7a recipe applies: phone Bluetooth off, launch D-HU, wait 8 s, phone Bluetooth on. If no poke ever connected across the round, R5 is INCONCLUSIVE and that branch stays on `BtAutoDisconnectPolicyTest`; say so rather than forcing it.

### R6: the whole cycle on Self Mode (D-POCO, needs `:5277`)

D-POCO keys as §3. D-HU: `headunit://exit` then `force-stop` so it runs no app; its own Bluetooth stack still connects to D-POCO by itself. Confirm on D-POCO that D-HU is bonded and connected (`dumpsys bluetooth_manager`). Start the capture on D-POCO, `am start` its `MainActivity`, wait 15 s: **no session** must form (`auto-start-self-mode` is off). Then:

1. `adb -s $HU shell svc bluetooth disable`. D-POCO gets an `ACL_DISCONNECTED` for D-HU with nothing projecting: expect `went away, but nothing is projecting`. About 14 s later the adapter reverts: expect `BT Device connected: ... (<D-HU>)`, `MATCH!`, `App launched via: Bluetooth auto-start`, `Auto-connect: begin (Bluetooth auto-start, mode=PILL)`, `MainActivity: Bluetooth auto-start: forcing a Self Mode launch`, `SelfMode: AA 17.4+ detected`, `SSL handshake complete`. The service's actions line must read `forceRearmWireless=false, armWirelessIfIdle=false`.
2. Wait until that session is 75 s old. `adb -s $HU shell svc bluetooth disable` again. Expect `went away; ending the session in 5000ms`, `stayed away; ending the session the way the Exit button does`, `Self Mode disconnected. Not restarting.`, `Hiding reconnecting overlay - the session ended`, `keeping the wireless bring-up down`; then about 9 s of nothing; then the revert, `MATCH!`, `forcing a Self Mode launch`, and a third session.

PASS when both steps hold: the first session is launched by the trigger and nothing else, the second ends on the trigger, and the third forms on the trigger with no relaunch in between. Corroborate each `MATCH!` from the phone side (`GH.WifiBluetoothRcvr ... ACL_CONNECTED`). If step 1's `MATCH!` never comes because D-HU's revert raised no ACL event on D-POCO, the run is INCONCLUSIVE at that step and its Self-Mode half stays on `BtAutoStartRearmPolicyTest`; step 2's disconnect half can still be run by launching Self Mode with `ACTION_START_SELF_MODE` by hand.

Two things to know: Gearhead on D-POCO may also try a wireless session toward D-HU when D-HU's Bluetooth comes back (D-HU is a known car to it); with no app listening on D-HU that attempt fails harmlessly and is not a discard. And the previous round's cleanup left D-POCO's Gearhead freshly force-stopped, so its first launch here may run the head unit server's own first-connection setup; give step 1 a full 90 s.

### R7: "Close app on disconnect" completes the macro (D-POCO, needs `:5277`)

R6 step 2 with `kill-on-disconnect=true`. After the `stayed away` line: `pidof $PKG` empty within 5 s, `dumpsys activity activities | grep mResumedActivity` naming the launcher (not our package), and then the adapter revert cold-starts the app again through the manifest receiver: `MATCH!` from a fresh pid, `forcing a Self Mode launch`, a new session. That is the motorbike reporter's two macros from one setting plus one existing toggle. PASS on all three. Restore `kill-on-disconnect=false`.

### R8: a healthy Helper group is not torn down to re-arm it (D-HU, Helper mode, head unit only)

`wifi-connection-mode=2`, `helper-connection-strategy=1` (WiFi Direct), phone's Bluetooth on but nothing will join (no helper app on this rig). Launch, wait 20 s: `Initializing WiFi Mode: HELPER` = 1, one `createGroup SUCCESS`. Lever: `adb -s $HU shell svc bluetooth disable`. Watch 45 s.

PASS when: `MATCH!`, then `Bluetooth auto-start: BtAutoStartActions(clearUserExit=true, forceRearmWireless=false, armWirelessIfIdle=false)`, and afterwards `Initializing WiFi Mode` is **still 1** and `createGroup SUCCESS` still 1. A second `Initializing WiFi Mode` here is a FAIL: the group was torn down to re-arm it.

The other half, `armWirelessIfIdle=true`, needs a Helper session to auto-disconnect first and no helper can connect on this rig; it stays on `BtAutoStartRearmPolicyTest` (`the other wireless modes arm only when nothing is armed`). Restore `wifi-connection-mode=3` afterwards.

### R9: the empty list is the off switch (D-HU, Native)

Delete `auto-disconnect-bt-macs`. Settled session. Lever: `adb -s $HU shell svc bluetooth disable`. Watch 45 s. PASS when `grep -ac "Bluetooth auto-disconnect:"` is **0** for the capture and the session survives the cycle (as in R2). Restore the key.

### Not runnable here

- **Helper mode after a user exit arms when idle** (`armWirelessIfIdle=true`): needs a Helper session; see R8.
- **Backup round trip of the two new keys:** the export and import are UI-only; coverage by code review of `SettingsBackupManager`'s whitelist.
- **The settings rows:** hidden or shown by `connection-modes` is a `filterNot` on stable ids, covered by reading `AutoStartFragment.kt`. If a screenshot is cheap, open `SettingsActivity` with `--es extra_search_query "Auto-disconnect on Bluetooth"`: PASS if the "Auto-start settings" row is the search result, which proves the keyword is wired. No scrolling.

---

## 6. Stop conditions

- R0 fails: stop.
- R1 and R1b both never print an arm line while step 1 showed the link up: stop after R4's dumpsys reads and report; something other than the link is wrong and it needs a look before more runs.
- R6 step 1 INCONCLUSIVE: run its step 2 by hand-launching Self Mode, then R7 the same way.

Leave the candidate installed, `settings.xml` restored byte-identical on both devices (`connection-modes` on D-POCO included), the delay key absent, `kill-on-disconnect=false`, D-HU on `wifi-connection-mode=3`, D-HU's app running with the phone connected, and both phones' radios on. Do not force-stop Gearhead on D-POCO.

---

## 7. Report back

The three things that decide the shipping question:

1. **R1's line sequence and the silence window**, with the time from `svc bluetooth disable` to `stayed away` and the `createGroup SUCCESS` count.
2. **R4's answer, A or B, with the `age=` value and the two dumpsys reads.** This is the sentence that goes into the settings hint.
3. **R6: three sessions, two started by the trigger, one ended by it**, with the gap between the fire line and the next `MATCH!`.
