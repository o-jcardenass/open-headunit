# usb-version-retry — round 2 results

**Candidate:** fork `fix/usb-auto-connect` @ `24d503248`       **Baseline:** none (not needed this round, per brief)
**APK md5:** `a947693ddf8530c2ba337141eba6d2c4` (`candidate-24d50324.apk`)
**Unit:** Stage A — D-POCO (POCO X3 NFC, Android 15) as USB host over OTG, wireless adb at
`192.168.1.4:5555`, Carlinkit-class AA dongle (idle `18d1:4ee1`, accessory `18d1:2d00`), paired
phone Motorola edge 30 neo (D-MOTO).
**Date:** 2026-09-23

## Setup notes

- **D-POCO's Bluetooth state was not controlled and changed mid-round, materially affecting R1.**
  The operator manually disabled D-POCO's Bluetooth partway through R1 (not part of the brief's
  own settings table, and not requested by this session) and re-enabled it after. Plugs 1, 2 and 4
  ran with Bluetooth **on**: the dongle needed 2-3 timeout cycles (~7-10 s) before answering.
  Plugs 5-10 ran with Bluetooth **off**: the dongle answered on the very first attempt every time,
  zero timeouts. This is not a coincidence — R1's `createGroup SUCCESS`-in-window failure (see R1
  below) only ever appeared under the slow-dongle condition, i.e. with Bluetooth on. Bluetooth on
  is also the condition Native AA's own poke mechanism needs, so it is the realistic operating
  condition for this whole round's premise ("USB over the *armed* wireless stack"); the clean
  plugs 5-10 should be read as "the failure mode never got a chance to fire," not as evidence the
  fix is clean.
- **`reopen-on-reconnection` + `auto-connect-last-session` self-triggers a reconnect on a
  still-attached accessory during the 15 s wait-for-rearm window**, with no physical replug. R1's
  logged "plug 3" is actually three accessory-switch cycles: a genuine cold plug (clean, SSL in
  5.1 s), then an unprompted auto-reconnect using the same still-attached accessory (triggered
  while this session was reading source to investigate the first anomaly — real elapsed time, not
  a broadcast bug), then the operator's actual replug once asked. Neither of the extra two cycles
  showed the arbiter's own "stood the wireless stack down" line at all, going through
  `UsbLauncherManager.checkAlreadyConnected`'s "already in accessory mode" path rather than
  `UsbAttachedActivity`'s fresh-switch path — worth the author's attention separately from R1's
  main finding. Plug 3 is reported separately below and not counted in R1's 10.
- **A single `ConnectionArbiter.claim()`/`release()` pair around the initial AOA switch call is
  far shorter than any adb broadcast round-trip.** Measured release-after-claim: 18-140 ms across
  the round. Measured adb broadcast delivery (send to `AutomationReceiver.onReceive`): 89-159 ms.
  This is why R4's literal `preempts` line was never observed (see R4) — not a functional gap.
- **`ACTION_DISCONNECT` and `ACTION_CONNECT` both left the status pill's X sticky** in a way that
  blocked a later, unrelated USB attach with no visible cause, twice (once approaching R5, once
  during R6 setup). A full `force-stop` + relaunch cleared it every time; `ACTION_START_WIRELESS`
  alone did not always clear it. R6 needed two throwaway resets before a genuinely clean try.
- **D-MOTO's Wi-Fi was manually toggled by the operator during R5 setup, not self-reverting.**
  This session initially misread two early invalidated R5 attempts as an autonomous rig quirk;
  the operator clarified they were doing it by hand. Corrected here so this isn't carried forward
  as a false rig-quirk finding. The graded R5 run (3rd attempt) had D-MOTO's Wi-Fi verified off via
  `dumpsys wifi` at the start, at ~40 s, and continuously through to the end of the observed
  window — that run's data is not affected by the earlier confusion.
- Settings backed up before any write (`settings.xml.backup`, 96 keys) and restored at the end,
  confirmed by reading it back (`wifi-connection-mode=3`, `use-libusb=false`, matching the original).
- `native-aa-wireless` / `wifi-launcher-mode` legacy keys were already absent on this device
  (round 1's fix held); explicit `DEL` was still sent before every `wifi-connection-mode` write
  per the brief.
- Scripts used: `build_hur.sh`, `set_prefs_runas.sh` (both pre-existing, no changes). No new
  script needed this round.
- D-POCO's wireless adb address was `192.168.1.4:5555`, same as round 1.

## R0 — Build gate

**PASS**

- SHA: `git rev-parse --short HEAD` → `24d50324`, matches the brief.
- DEX: `ConnectionArbiter`, `ConnectionPriorityPolicy` and `HandshakeMessagePolicy` all present via
  `strings` on the merged `classes*.dex`.
- JVM gate: 2346 tests, 0 failures, matches the brief exactly.
- `adb install -r` succeeded on D-POCO; installed APK md5 matches the built APK md5
  (`a947693ddf8530c2ba337141eba6d2c4`).

## R1 — USB over the armed wireless stack (the point of the round)

**FAIL.**

Per-plug table (plug 3 excluded — see Setup notes; reported separately below):

| Plug | BT state | Trigger count | Discard count | `createGroup`/poke in window | First-handshake SSL | Accessory→SSL |
|---|---|---|---|---|---|---|
| 1 | on | 6 | 0 | **1 `createGroup SUCCESS`** | no (3rd internal cycle) | 29.22 s |
| 2 | on | 3 | 2 | **1 `createGroup SUCCESS`** | no (2nd internal cycle) | 20.65 s |
| 4 | on | 3 | 2 | **1 `createGroup SUCCESS`** | no (2nd internal cycle) | 23.29 s |
| 5 | off | 0 | 0 | zero | yes | 3.08 s |
| 6 | off | 0 | 0 | zero | yes | 2.68 s |
| 7 | off | 0 | 0 | zero | yes | 2.94 s |
| 8 | off | 0 | 0 | zero | yes | 2.94 s |
| 9 | off | 0 | 0 | zero | yes | 3.06 s |
| 10 | off | 0 | 0 | zero | yes | 2.86 s |

Zero shape A, zero shape B (`Failed to read AAP header`), zero deadline-expiry line on all 9 plugs
— the `50f6c4c48` discard fix itself holds. The failure is the **new arbiter**.

**Decisive lines, plug 1 (representative — plugs 2 and 4 are the same shape):**

```
22:44:43.930  ConnectionArbiter: USB switch of Google Pixel 4 (...) (USB) stood the wireless stack down until it ends
22:44:43.933  Switching USB device to accessory mode Google Pixel 4 (...)
22:44:45.083 → 22:44:52.546  three Version-request attempts, all "No VERSION_RESPONSE", handshake gives up
22:44:54.282  WifiLauncher: Initializing WiFi Mode: NATIVE          <- wireless given back, no session had formed
22:44:54.883  WifiDirectManager: 5GHz createGroup SUCCESS!          <- full bring-up starts
22:44:58.065  ConnectionArbiter: USB switch ... stood the wireless stack down until it ends   <- USB reclaims
22:44:58.067  Switching USB device to accessory mode ...            <- 2nd internal cycle
22:45:13.156  SSL handshake complete
```

Plug 2's window additionally shows the full bring-up completing inside the gap, not just starting:
`WirelessServer: binding port 5288...`, `NativeAA: ACTIVELY LISTENING on Android Auto UUID ...
Waiting for phone to connect back!`, and `WifiDirectManager: SUCCESS - Providing credentials to
listener` all fire and then get torn down cleanly (`WirelessServer: port 5288 released`, `NativeAA:
AA Server socket closed cleanly`) before the reclaim — a ~4 s window where the wireless stack is
genuinely live, not just mid-transition.

**Root cause (as measured, not audited in source beyond confirming the log-line owners):** the
`AapTransport` handshake's own 3-attempt give-up releases `ConnectionArbiter`'s claim
(`sessionFormed = false`) when the dongle doesn't answer inside the budget. The arbiter's own
"give it back after ~1.5 s" design then lets wireless come all the way up before USB's *own*
internal retry re-attaches and reclaims. This only has room to happen when the first handshake
cycle is slow enough to exhaust its budget — which is the Bluetooth-on condition, the one this
round's whole premise depends on.

**Audio-start check (not graded):** `disabled due to previous underrun`: 0 occurrences across the
whole R1 capture. `playback started with N frames banked`: 13 occurrences (9 clean plugs + 3 for
plug 3's extra cycles + 1 initial), all `18432 frames banked (target 19200)`, 210-235 ms.

### Plug 3 — anomaly, not counted toward the 10

Three accessory-switch cycles for one operator action, none showing a `ConnectionArbiter` stand-down
line at all:

```
22:48:01.070  USB auto-start: launching app for Google Pixel 4 (...)   <- still-attached device, no physical replug
22:48:06.255  SSL handshake complete                                    <- clean, unplanned session
22:49:42.299  AapService: USB disconnect after user Exit with reopenOnReconnection enabled. Will reconnect on next USB attach.
22:49:47.069  UsbAttachedActivity.onCreate | USB auto-start: launching app for Google Pixel 4 (...)   <- 2nd auto-reconnect
22:50:02.212  SSL handshake complete                                    <- this is the operator's actual replug's result
```

Neither of the first two cycles logged a `ConnectionArbiter.claim` stand-down despite wireless
being armed and having just formed a group 7 s earlier — worth checking against
`UsbLauncherManager.checkAlreadyConnected`'s `beginAttempt` path separately from this round's main
finding.

## R2 — the clean control

**PASS.**

| Plug | Trigger count | Discard count | Accessory→SSL |
|---|---|---|---|
| 1 | 0 | 0 | 2.87 s |
| 2 | 1 | 1 | 5.56 s |
| 3 | 1 | 1 | 3.56 s |
| 4 | 0 | 0 | 2.84 s |
| 5 | 0 | 0 | 2.95 s |
| 6 | 0 | 0 | 2.73 s |
| 7 | 2 | 2 | 9.92 s |
| 8 | 0 | 0 | 3.01 s |
| 9 | 2 | 2 | 9.77 s |
| 10 | 0 | 0 | 2.88 s |

10/10 plugs: SSL on the first (only) accessory-switch cycle, zero shape A/B, zero
`ConnectionArbiter:` preempt or refused lines, a discard on every retried handshake. Zero
`disabled due to previous underrun` in the R2 capture.

## R3 — USB preempts a Native AA handshake

**NOT RUN.** No second phone is available on this rig to hold a Native AA handshake while D-MOTO
is occupied by the dongle. D-POCO's bonded-device list besides D-MOTO is "Navegadortz2",
"Navegadortz3", "Magnetic Speaker" and the retired HP Slate unit — none can stand in as a phone for
this run.

## R4 — a user's connection preempts USB

**Functional PASS on conditions 2-4, condition 1 not observed in 3/3 tries** (the brief's own
fallback for exactly this case).

| Try | Switch→`AutomationReceiver` gap | `preempts` line | `a USB check waits for` | `ended with no session; giving back USB` | Resumed without replug |
|---|---|---|---|---|---|
| 1 | 159 ms | no | yes (x3) | yes | yes, SSL 2.28 s later |
| 2 | 89 ms | no | yes (x3) | yes | yes, SSL 4.86 s later |
| 3 | 136 ms | no | yes (x3) | yes | yes, SSL 4.58 s later |

In all 3 tries the initial `UsbAttachedActivity` claim self-released (`ended with no session;
giving back wireless`) 18-140 ms after the switch line — before the fastest achievable
`ACTION_CONNECT` broadcast could land (measured 89-159 ms; see Setup notes). By the time the
user's claim was taken, the arbiter was already idle, so there was nothing left to preempt; the
user's claim then correctly held off the *next* internal USB retry (`a USB check waits for
192.0.2.1:5277 (USER)`), gave back cleanly on its own 5 s timeout, and USB resumed and completed
without a replug every time. The literal `preempts` line is not reachable via an adb-scripted
trigger on this rig at this claim's timing, not evidence the preempt path is broken — the
functional outcome (user wins, USB waits, clean handoff both ways) held 3/3.

## R5 — a failed USB attempt gives wireless back

**Ambiguous against the letter of the brief; functionally a problem.** First two attempts
invalidated by D-MOTO's Wi-Fi being turned back on mid-setup (operator, by hand — see Setup notes,
corrected from this session's earlier mischaracterization as a rig self-revert). Third attempt
verified Wi-Fi off via `dumpsys wifi` at start, ~40 s, and continuously through the end.

- Stand-down line on the first attempt: **yes**, `23:21:26.760`, before the version request.
- `Initializing WiFi Mode: NATIVE` measured from the *literal last* handshake-failure line
  (`23:24:19.930`, `Handshake: Version request/response failed after 3 attempt(s)`) to the rearm
  (`23:24:21.479`): **1.549 s — inside the 12 s bar.**
- **But wireless was unusable for 173 s (23:21:26.763 → 23:24:21.479) before that "last failure"
  line was ever reached.** `ConnectionArbiter`'s own claim released in 23 ms as usual, but a
  separate mechanism, `AapService.deferWirelessForUsbHandoff`, re-printed `"a USB projection
  attempt is in flight — holding the wireless bring-up for up to 8000ms"` roughly every 13 s,
  13 times in a row, because the underlying handshake kept internally retrying against a dongle
  that can never succeed (D-MOTO unreachable). The "up to 8000ms" cap this line describes was
  never actually enforced as a ceiling on the overall attempt — every ~8 s window renewed itself
  as long as the retry loop was still alive.
- After the failure, wireless recovered correctly: `Initializing WiFi Mode: NATIVE` at
  `23:24:21.479`, `createGroup SUCCESS` at `23:24:22.175`, stayed up until a second USB attach at
  `23:24:25.493` (a new stand-down, correctly held) began the same cycle again — still running
  when this attempt was ended for time.

Reading the brief's stated bar literally, this is a **PASS** (both named conditions hold). Reading
its intent — "the stack stays up except while a new USB attempt holds the arbiter" — it is a
**FAIL**: the arbiter itself was free for essentially the entire 173 s, yet wireless stayed down
the whole time on a second, uncapped hold. This session flags it as the more important reading
rather than picking one silently.

## R6 — the before-SSL X (pause-auto-connect R4)

**PASS**, on the third try. The first two tries were invalidated by a sticky status-pill X left
over from this session's own `ACTION_DISCONNECT`/`ACTION_CONNECT` calls during setup (see Setup
notes) — not a candidate defect; a `force-stop` + relaunch cleared it.

Clean try, `23:33:40`:

```
23:33:40.209  Switching USB device to accessory mode Google Pixel 4 (...)
23:33:40.341  AutomationReceiver: ACTION_CANCEL_WIRELESS                    (132 ms after the switch)
23:33:40.356  AapService: the status pill's X stopped the USB attempt. USB stays down until the USB button asks for it.
23:33:40.651  UsbLauncher: USB auto-connect refused: the status pill's X holds it until the USB button.
23:33:40.706  UsbLauncher: USB auto-connect refused: the status pill's X holds it until the USB button.
23:33:42.180  UsbLauncher: USB auto-connect refused: the status pill's X holds it until the USB button.
```

Zero `Handshake: Version request sent` and zero `SSL handshake complete` for at least 25 s after
the X, for the re-enumerated accessory.

## Anything the brief did not ask about

- `reopen-on-reconnection` racing a still-attached accessory during the arbiter's 15 s
  wait-for-rearm window (R1 plug 3) is worth its own brief: it can self-trigger a session with no
  physical action, and at least twice did so through a code path that never prints the arbiter's
  own stand-down line.
- The status pill's X sticking across `ACTION_DISCONNECT`/`ACTION_CONNECT` in a way only a
  `force-stop` reliably clears cost two full setup cycles this round (once near R5, once in R6).
  Worth checking whether this is the same "sticky X" mechanism documented for the wireless-arm
  case or a related but distinct latch.

Captures: release `rig-evidence-usb-version-retry`, asset `usb-version-retry-round2-captures.zip`,
sha256 `3ba24476765e307a9346b01b96b25e6b626ad3c5b2efbf471ec506d054aa7725`.
