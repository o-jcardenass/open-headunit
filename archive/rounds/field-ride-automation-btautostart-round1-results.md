# field-ride-automation-btautostart — round 1 results

Field report from a ride on `fork/testing/automation-plus-btautostart` (`3.3.0` vc103, commit
`e9ab7f135ec7` = PR 1 + PR 2 + PR 3 merged). Not a scripted round. Three findings; two are traced to
a shared mechanism with log + source evidence, one is partly open.

Build / setup facts are in `field-ride-automation-btautostart-round1-brief.md`. In short: head unit =
POCO X3 NFC, `wifi-connection-mode = 3` (Native AA / WiFi Direct), `enable-audio-sink = true`,
`auto-start-bt-macs = {A0:46:5A:97:E4:95}` (the projected motorola edge 30 neo), Android Auto
`17.5.663204`, log level INFO.

The two exported files (`...429.txt`, `...442.txt`) are the **same session and pid** (29092),
exported ~4 s apart; `442` just carries two extra tail lines. The window is only
**18:17:51 → 18:18:42** — the tail of the "lots of presses" episode: three doomed Self Mode launches
and then the connection that held. All timestamps below are from `...429.txt`.

---

## F1 — WiFi button in mode 3 loops through doomed Self Mode launches (FAIL)

**Verdict: FAIL.** Reproduced in the captured window. Matches the operator's report 1 ("keep opening
the AA settings to activate the server", "keep showing the AA 17.4+ notif", "after a lot of presses
it worked").

### What the log shows

```
18:18:00.152  HomeFragment: Manually selected motorola edge 30 neo for Native-AA poke
18:18:00.152  MainActivity.beginAutoConnect | Auto-connect: begin (manual Native-AA poke, mode=OVERLAY)
18:18:00.184  AapService: Received manual Native-AA poke request for A0:46:5A:97:E4:95
18:18:00.733  WifiDirectManager: 5GHz createGroup SUCCESS!            (SSID DIRECT-X3-Navegadortz, 5240 MHz)
18:18:02.893  NativeAA.pokeDevice | Calling socket.connect() for motorola edge 30 neo via HFP-AG
18:18:04.166  AutoStartReceiver.onReceive | BT Device connected: motorola edge 30 neo (A0:46:5A:97:E4:95)
18:18:04.166  AutoStartReceiver.onReceive | MATCH! Starting AapService via Bluetooth Auto-start...
18:18:04.187  MainActivity.handleLaunchIntent | Bluetooth auto-start: forcing a Self Mode launch
18:18:04.226  SelfMode: Installed AA version: 17.5.663204-release
18:18:04.226  SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...
18:18:04.230  java.net.ConnectException: failed to connect to /127.0.0.1 (port 5277) ... ECONNREFUSED
18:18:04.232  AapService.emitSessionState | session state disconnected (link_lost)
18:18:04.249  AapService.scheduleReconnectIfNeeded | Self Mode disconnected. Not restarting.
18:18:04.252  SelfLauncherV17_4.run | SelfMode: Headunit Server (127.0.0.1:5277) is NOT running.
18:18:04.273  SelfMode: All launchers failed
18:18:04.303  AapService.emitSessionState | session state disconnected (user_exit)
18:18:04.325  AapService.onDisconnected | User exit cooldown active for 5000ms
```

From here every Native AA credential delivery is suppressed:

```
18:18:05.425  WifiLauncherNative.onNativeCredentials | AapService: userExitedAA is true. Skipping auto-poke.
18:18:05.458  ... Skipping auto-poke.
18:18:05.931  ... Skipping auto-poke.
18:18:09.160  ... Skipping auto-poke.   (and .169, .180, .190)
```

The forced Self Mode launch repeats:

```
18:18:19.853  Auto-connect: begin (auto-start self mode, mode=OVERLAY)   → 18:18:20.413  All launchers failed
18:18:24.412  Auto-connect: begin (Bluetooth auto-start, mode=PILL)
18:18:24.413  MainActivity: Bluetooth auto-start: forcing a Self Mode launch  → 18:18:24.479  All launchers failed
```

P2P group churn while this goes on: `5GHz createGroup SUCCESS!` at **18:18:00.733, 18:18:04.910,
18:18:20.666** (3 groups in ~20 s); group frequency bounces 5240 → 5180 MHz. `p2p-wlan0-N` interface
index is not in an INFO log so lifetime group count is not measurable here.

It finally connects when a poke lands and holds long enough for the phone to come back:

```
18:18:24.500  NativeAA.pokeDevice | Successfully poked motorola edge 30 neo via HFP-AG. Holding 15000ms...
18:18:24.732  NativeAA | hands-free service level connection established (HFP-AG poke to A0:46:5A:97:E4:95)
18:18:25.238  WppTcpServer: connection from 192.168.49.62
18:18:25.312  WppTcpServer: TLS handshake complete with 192.168.49.62
18:18:25.439  WirelessServer: Incoming connection detected from /192.168.49.62      ← the line that matters
18:18:25.445  AapService.emitSessionState | session state connected
18:18:25.454  NativeAaHandshakeManager.onSessionEstablished | session is up — cancelling the poke retry loop
18:18:26.940  VideoDecoder.outputThreadLoop | First frame rendered (hardware decode)
```

After that the session was healthy (throughput windows 28 / 53 / 38 fps, `dropped=0`).

### Root cause (source-traced)

`AutoStartReceiver.onReceive` (`app/.../app/AutoStartReceiver.kt`, main) fires on any
`ACTION_ACL_CONNECTED` for a MAC in `auto-start-bt-macs`, with **no guard** for:
- the Native-AA poke's own outbound HFP `socket.connect()` (CLAUDE.md already documents that
  `triggerPoke()`'s `socket.connect()` raises an OS `ACL_CONNECTED` that `AutoStartReceiver` cannot
  tell from the user's phone arriving);
- `wifiConnectionMode == 3`.

It sends `AapService.ACTION_BT_AUTO_START` and launches `MainActivity` with
`EXTRA_LAUNCH_SOURCE = "Bluetooth auto-start"`. On this testing branch that source makes
`MainActivity.handleLaunchIntent` **force a Self Mode launch** ("Bluetooth auto-start: forcing a Self
Mode launch" — a branch-only string, not on `main`). In mode 3 the projecting phone has not started
its 17.4+ head-unit server, so `SelfLauncherV17_4` always fails against `127.0.0.1:5277`. That failure:

1. emits `session state disconnected (user_exit)` → `AapService.onDisconnected` sets
   `userExitedAA = true` (`AapService.kt:1476`, the `state.isUserExit` branch) →
   `WifiLauncherNative.onNativeCredentials` takes the `else` at `WifiLauncherNative.kt:166`
   ("userExitedAA is true. Skipping auto-poke.") for the rest of that group's credential deliveries;
2. re-runs the 17.4+ / AA-server-activation UI on every forced launch (the operator's "keep opening
   the AA settings to activate the server" / "AA 17.4+ notif" — `aa174_notice_shown` is already
   `true` in prefs, so this is the SelfLauncher path's messaging, not `Aa174Notice`);
3. churns the P2P group (`startNativeAaQuietHost` re-entered on each bring-up).

This is the CLAUDE.md "the app can wake itself up" loop, made worse by PR 2: it forces **Self Mode**
(unwinnable in mode 3) rather than `initWifiMode`.

### Also seen on this path

- **IntentReceiver leak.** `18:18:17.046  E ActivityThread: Service ... has leaked IntentReceiver
  ...WifiDirectManager$receiver$1 ... at WifiDirectManager.registerReceiverIfNeeded(WifiDirectManager.kt:643)
  ... startNativeAaQuietHost(WifiDirectManager.kt:1470) ... refreshNativeCredentials$lambda$19(WifiDirectManager.kt:1595)`.
  Two leaked receivers logged back to back. Matches the CLAUDE.md note that `isReceiverRegistered` is
  never reset in `stop()` (the `fix/connection-lifecycle-hardening` branch).
- **Port not released on churn.** `18:18:00.232  E WppTcpServer: could not listen on 5299: bind
  failed: EADDRINUSE`. A prior `WppTcpServer` was still bound at the next bring-up. Self-corrected on
  the successful run (`18:18:19.982  WppTcpServer: listening for Android Auto on TCP 5299`).
- **WPP control channel closed after 0 pings.** `18:18:35.460  WppTcpServer: the phone closed the
  control channel after 0 pings` — consistent with the `wpp-over-tcp` round-5 finding (Gearhead
  17.5.663204 drops the WPP control channel ~10 s in; harmless).

### Suggested direction (design is the coding session's)

- Make `AutoStartReceiver` / the BT-auto-start path inert while a Native-AA handshake or poke is in
  flight (`isHandshakeInFlight()` exists; `fix/760…` adds `isHandoffSettling()`), and/or
- do not force **Self Mode** when `wifiConnectionMode == 3` — in mode 3 a BT connect from the
  projecting phone should drive `initWifiMode` / the Native AA poke, not `SelfLauncherManager`.
- Filter the poke socket's own self-connect from `AutoStartReceiver` matching.

### Still unverified

The full "lots of presses" count and the group-churn total are not in an INFO log. A **VERBOSE**
capture of one WiFi-button press in mode 3 with `auto-start-bt-macs` set would pin the cadence.

---

## F2 — audio sink never set up on the POCO though `enable-audio-sink = true` (FAIL)

**Verdict: FAIL.** Matches report 2. The friend's motorola-as-head-unit kept the audio sink;
the POCO did not.

### What the log shows

Settings on the POCO after the ride (`evidence/.../poco-settings.xml`):

```xml
<boolean name="enable-audio-sink" value="true" />
```

In the session's ServiceDiscovery only the SYSTEM audio channel is announced — no MEDIA, no SPEECH:

```
18:18:25.737  Companion.makeProto | [ServiceDiscovery] NegotiatedResolution is: 1920x1080
18:18:25.741  Companion.makeProto | BT MAC Address is empty, so no Bluetooth service is announced. ...
18:18:26.010  AapControlMedia.mediaSinkSetupRequest | Media Sink Setup Request: 1 on channel AUDIO2   ← SYSTEM (Channel.ID_AU2)
18:18:26.012  AapControlMedia.mediaSinkSetupRequest | Media Sink Setup Request: 3 on channel VIDEO
```

There is **no** `Media Sink Setup Request` for the MEDIA channel (`Channel.ID_AUD`) or the SPEECH
channel (`Channel.ID_AU1`), and no music audio-focus / render activity for the rest of the session.
The `AppLog.i("Audio sink is off in Settings. Skipping the media and speech audio channels ...")`
line is **absent**, so the code did **not** take the `else` of `if (settings.enableAudioSink)` — it
entered the `if` branch and still skipped the two channels.

### Root cause (source-traced)

`ServiceDiscoveryResponse.makeProto` (`app/.../aap/protocol/messages/ServiceDiscoveryResponse.kt`,
main, ~lines 154–199):

```kotlin
services.add(audio2)                       // SYSTEM — always

if (settings.enableAudioSink) {
    val isSelfMode = AapService.instance?.isSelfModeActive() ?: false
    if (!isSelfMode) { services.add(audio1) }   // SPEECH
    if (!isSelfMode) { services.add(audio0) }   // MEDIA
} else {
    AppLog.i("Audio sink is off in Settings. Skipping the media and speech audio channels ...")
}
```

With `enableAudioSink == true` **and** `isSelfModeActive() == true`, MEDIA and SPEECH are dropped and
only SYSTEM (AUDIO2) is announced — exactly what the log shows.

`isSelfModeActive()` is `selfLauncherManager.isActive` (`AapService.kt:286`).
`SelfLauncherManager.isActive` is set `true` in `start()` (`SelfLauncherManager.kt:111`) and cleared
in only two places:
- `AapService.scheduleReconnectIfNeeded` on a `Disconnected` transition
  (`AapService.kt:~1503`, "Self Mode disconnected. Not restarting." then `isActive = false`);
- the launch-timeout watchdog (`SelfLauncherManager.kt:194`), which is **only scheduled when at least
  one launcher succeeds** (`anySucceeded`, `SelfLauncherManager.kt:~180`).

The last forced Self Mode launch (`18:18:24.479  All launchers failed`) had **no live session**, so
`emitError` produced **no `Disconnected` transition** — there is no "session state disconnected" line
between `18:18:24.479` and the `18:18:25.445  session state connected` of the Native AA session — and
`anySucceeded` was false so the watchdog was never scheduled. `isActive` stayed `true`. The Native AA
WiFi Direct session then connected and `ServiceDiscoveryResponse.makeProto` ran at `18:18:25.737` with
`isSelfModeActive() == true`.

`SelfLauncherManager.kt:191-194` already documents this exact failure:

> *"The report is deliberately not a disconnect, so no Disconnected transition arrives to clear this.
> Left true, it poisons the next session in this process: ServiceDiscoveryResponse drops the media and
> speech audio sinks."*

The existing `isActive = false` there covers only the *timeout* path, not the
all-launchers-failed-immediately-with-no-session path that F1 produces.

### Independence from F1

Any failed Self Mode launch followed by a wireless connect in the same process reproduces F2 — it does
not need F1's specific trigger. F1 is just the reason a failed Self Mode launch happened at all on
this ride.

### Suggested direction

Clear `SelfLauncherManager.isActive` on the all-launchers-failed branch
(`SelfLauncherManager.kt:~163-173`), not only in the watchdog; or gate the ServiceDiscovery
MEDIA/SPEECH drop on a signal that self mode is actually the transport for *this* session rather than
the sticky `isActive` flag.

---

## F3 — BT auto-start entry could not be cleared (FAIL, partly open)

**Verdict: FAIL** on the outcome (the entry survived the operator's attempts). Mechanism not fully
reproduced — the ride log window does not cover the settings UI.

### What is certain

After the ride, on the POCO, both stores still hold the entry:

`shared_prefs/settings.xml`:
```xml
<set name="auto-start-bt-macs">
    <string>A0:46:5A:97:E4:95</string>
</set>
<string name="auto-start-bt-name">motorola edge 30 neo</string>
```
`shared_prefs/settings_device_protected.xml` (read via `run-as`, `/data/user_de/0/.../shared_prefs/`):
```xml
<set name="auto-start-bt-macs">
    <string>A0:46:5A:97:E4:95</string>
</set>
```

- Overlay permission: `appops get ... SYSTEM_ALERT_WINDOW` = `allow`.
- `BLUETOOTH_CONNECT`: `granted=true`.

So in the current state neither the overlay-permission dialog (`AutoStartFragment.kt:~209`) nor the
`BLUETOOTH_CONNECT` gate (`showBluetoothDeviceSelector`, `AutoStartFragment.kt:441`) is what blocks
the clear.

### Candidate mechanisms (for the coding session to repro on a MIUI device)

1. **Staged removal that is never persisted.** `showBluetoothDeviceSelector()`
   (`AutoStartFragment.kt:~440-505`): unchecking the device, or the "Remove" neutral button
   (`:494`), updates only `pendingAutoStartBtMacs`. Nothing is written until the toolbar **Save**
   runs `saveSettings()` (`:172` — `settings.autoStartBluetoothDeviceMacs = pendingAutoStartBtMacs`
   plus `syncAutoStartBtMacsToDeviceStorage`). If the user removes the device and then leaves the
   screen (system back gesture included) without Save, both stores keep the entry and it is back on
   next open. Worth checking whether the fragment warns on unsaved removal, and whether "Remove"
   should write through immediately (it is unambiguous and destructive).
2. **A MIUI system consent dialog, not OHU UI.** "tapped deny a lot" fits a MIUI
   background-Activity-start / autostart permission prompt (`AutoStartReceiver` does
   `context.startActivity(...)` from the background on every BT connect) rather than any OHU screen —
   OHU's clear path has no "deny" button. If that is it, the entry was never the blocker; the operator
   was fighting a different dialog.

### Suggested direction

Repro on a MIUI/HyperOS device: confirm "Remove" + Save clears **both** stores; consider making
"Remove" write-through; check whether a MIUI dialog is intercepting the flow the operator described.

---

## One-line summary

| Finding | Verdict | Traced |
|---|---|---|
| F1 — WiFi button in mode 3 loops through doomed Self Mode launches (poke self-wake → forced Self Mode → `userExitedAA` + AA-server UI + group churn) | FAIL | yes — `AutoStartReceiver` no guard for poke / mode 3; branch forces Self Mode |
| F2 — audio sink dropped though `enable-audio-sink=true` (poisoned `SelfLauncherManager.isActive` → `ServiceDiscoveryResponse` skips MEDIA+SPEECH) | FAIL | yes — `isActive` only cleared on Disconnected or success-only watchdog |
| F3 — BT auto-start entry would not clear | FAIL (open) | partly — state confirmed in both prefs stores; UI mechanism needs a MIUI repro |

Secondary: `WifiDirectManager$receiver$1` IntentReceiver leak on the churn/teardown path
(`WifiDirectManager.kt:643` / `:1470`); `WppTcpServer` 5299 `EADDRINUSE` at bring-up on the churn
path.
