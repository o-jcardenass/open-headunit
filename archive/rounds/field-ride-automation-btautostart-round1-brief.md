# field-ride-automation-btautostart — round 1 (field report, not a scripted round)

This is not a brief the hardware session runs. It is a **field report from a real ride**, moved here
so the coding session can act on it. The operator rode with `fork/testing/automation-plus-btautostart`
(PR 1 + PR 2 + PR 3 merged, `3.3.0` vc103, commit `e9ab7f135ec7`) as the daily driver and hit three
bugs. The analysis, log evidence, and code root-cause are in
`field-ride-automation-btautostart-round1-results.md`. Findings 1 and 2 are traced to a specific
mechanism; finding 3 is partly open.

Evidence in `evidence/field-ride-automation-btautostart/`:
- `poco-HUR_Log_20260902_181842_429.txt` and `poco-HUR_Log_20260902_181846_442.txt` — the same
  ~51-second session on the head-unit phone, exported twice (INFO level). This is the **tail** of the
  episode: the last three doomed Self Mode launches and then the connection that finally held.
- `poco-settings.xml` — the head-unit phone's `shared_prefs/settings.xml` read after the ride.

## Verbatim operator prompt (kept in full, as requested)

> I did a lot of runs of fork/testing/automation-plus-btautostart while riding and I found 2 bugs.
> 1. When I pressed the wifi button on my poco (headunit) to connect to my moto, it keep openning
>    the AA settings to activate the server, it also keep showing the AA 17.4+ notif, after a lot of
>    presses the Native Wifi Direct connection worked.
> 2. Poco never had the audio sink even with the option on, A friend connected to my motorola (as
>    headunit) and it keept the audio sink.
> 3. I was unable to clear BT auto start with my poco I tapped deny a lot, but it never dissapeared.
> All logs are in the exported log path of OHU. Move those findings to the transfer branch, also in
> that transfer keep this prompt verbose so the builder agent can also read it. All phones+headunit
> connected via adb.

(The message says "2 bugs" in the first line then lists three; all three are treated as findings.)

## Setup as it actually was during the ride

| | |
|---|---|
| Head unit | POCO X3 NFC (Xiaomi `M2007J20CG`, `sm6150`, API 35), view mode GLES, H.264 |
| Head-unit build | `3.3.0` (103) `github/debug`, commit `e9ab7f135ec7` = `fork/testing/automation-plus-btautostart` |
| Projected phone | motorola edge 30 neo, BT MAC `A0:46:5A:97:E4:95`, Android Auto `17.5.663204-release` |
| Connection mode | `wifi-connection-mode = 3` (Native AA), `strategy = WIFI_DIRECT` |
| Relevant settings | `enable-audio-sink = true`, `auto-start-bt-macs = {A0:46:5A:97:E4:95}`, `auto-start-bt-name = "motorola edge 30 neo"`, `auto-start-self-mode = false`, overlay permission = allow, `BLUETOOTH_CONNECT` = granted |
| Log level | INFO (no verbose poke / group-info detail) |
| Not captured | the friend's "motorola-as-head-unit" session (that phone was not on adb); the MT50's only logs are from 2026-08-27 and unrelated |

## What the coding session needs to decide

Findings 1 and 2 share one trigger: **the Native-AA poke's own outbound HFP `socket.connect()` sets
off `AutoStartReceiver` (PR 2), which forces a Self Mode launch that cannot win in mode 3** (the phone
is not running its 17.4+ head-unit server yet). That failure then (a) latches `userExitedAA` and
suppresses the Native AA auto-poke, (b) re-shows the AA-server-activation / 17.4 messaging, (c) churns
the P2P group, and (d) poisons `SelfLauncherManager.isActive`, which makes `ServiceDiscoveryResponse`
drop the media and speech audio sinks for the wireless session that eventually connects.

Design calls that belong to the coding session:
- Whether `AutoStartReceiver` / the BT-auto-start path should be inert while a Native-AA
  handshake/poke is in flight, and/or should not force **Self Mode** when `wifiConnectionMode == 3`.
- Whether `SelfLauncherManager.isActive` should be cleared on the all-launchers-failed-with-no-session
  path (today it is only cleared by a `Disconnected` transition or the success-only timeout watchdog).
- Whether `ServiceDiscoveryResponse`'s "drop MEDIA+SPEECH in self mode" should key off a real
  "self-mode is the transport" signal instead of the sticky `isActive` flag.

Finding 3 (BT auto-start entry would not clear) needs a MIUI repro; see the results file.

If a hardware round is wanted after the fix, the natural shape is: mode 3, `auto-start-bt-macs` set to
the projecting phone, press the WiFi button, and check (a) no forced Self Mode launch, (b) no
`Skipping auto-poke`, (c) `Media Sink Setup Request` for MEDIA and SPEECH channels, not only AUDIO2.
Ask the operator for a **VERBOSE** capture that time.
