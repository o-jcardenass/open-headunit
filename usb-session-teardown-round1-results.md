# usb-session-teardown — round 1 results

**Candidate:** `0ff9e620` (pinned SHA per the brief). No baseline, no fix on trial — diagnosis round,
one APK for the whole round. Note: `origin/3.3.0-beta1`'s tip had advanced 3 commits past this SHA
by the time this round ran (to `5e981b01`, including the just-merged `fix/video-feed-backpressure`
PR #889) — checked out the exact pinned commit rather than the branch tip.
**APK md5:** `0b30d9ea8b57395800922b9797c80852`
**Unit:** Xiaomi M2007J20CG, Android 15 (API 35), qcom sm6150. Gearhead `17.5.663204-release`.
**Date:** 2026-08-25

## Setup notes

- **Flash-drive-via-USB-C-hub never registered as host-connected on this phone.** Two `dumpsys usb`
  captures with it plugged in both showed `current_mode=none` / `connected=false` at the port-status
  level — no `devices={}` block ever appeared for it, unlike every other device tested. Substituted a
  **Logitech MX Vertical mouse** (VID:PID `1133:49290` / `0x046D:0xC08A`, HID-only class, `class=3
  subclass=1 protocol=2`) as the non-audio device for R3.
- **Audio device:** TaiYiLian/UGREEN-BT701, VID:PID `2578:16391` (`0x0A12:0x4007`) — genuinely
  enumerates USB Audio Class (interface id=2 `class=1 subclass=1` Audio Control, id=3 `class=1
  subclass=2` Audio Streaming, two alt settings) plus HID. Its VID (`0x0A12`, Cambridge Silicon
  Radio) matches the reporter's device exactly.
- **`pidof <bare gearhead package name>` returns nothing on this build.** Gearhead runs as 5
  suffixed processes only (`:projection`, `:shared`, `:car`, `:watchdog`, `:provider`) with no
  bare-name process alive. Used `ps -A | grep gearhead` to collect all 5 pids instead.
- **Cold-start reliability issue, present on every fresh launch this round** (full detail under R1
  and "Anything the brief did not ask about"): every Self Mode launch attempt got stuck on "Android
  is starting" with zero video for anywhere from ~5s to ~2.5 minutes, hitting Android Auto's own
  `FIRST_ACTIVITY_LAUNCHED` 5000ms timeout (`PROJECTION_NOT_STARTED`). Recovery needed either a
  manual "Start head unit server" bounce or repeated Self Mode re-taps; neither is logged. This ate
  most of the round's time/battery budget and is why R4–R6 were not run (see "Not run").
- **An unclean local disconnect wedges the Headunit Server.** R1's first launch attempt was
  abandoned server-side by Android Auto (`PROJECTION_NOT_STARTED`) while our app still considered
  itself connected. Sending `headunit://disconnect` against that state produced `AapTransport
  quitting (clean=false)` and wedged the server for the next connection attempt (peer accepted the
  TCP connection, then sent nothing — matches `self-mode-bt-audio-round1-brief.md` §0's documented
  wedge symptom exactly). Recovered via the documented fix (toggle "Start head unit server" off/on).
- `log-source` was absent from the phone's `settings.xml` before this round (defaults to `LOGCAT` on
  this branch); written explicitly per §3 anyway. `settings.xml` already carried prior onboarding
  from an earlier round's testing — no fresh onboarding wizard triggered.
- Scripts used, unchanged: `build_hur.sh`, `run_unit_tests.sh`, `set_pref.sh`. No new script needed.
- Settings backed up to `settings-backup-usb-session-teardown-round1.xml` before any write, restored
  at the end of the round (verified: `log-level` back to `1`, `log-source` removed, matching the
  pre-round file).

## R0 — Gate

**PASS**

- Xiaomi M2007J20CG, Android 15 (API 35), qcom sm6150. Gearhead `17.5.663204-release`.
- `run-as stat shared_prefs`: `drwxrwx--x`, uid/gid `10268`.
- Both USB devices identified (see Setup notes for the flash-drive substitution).
- §5a Gearhead log-level raise verified: **7291 lines** captured by pid during a live session
  (failure threshold was zero or low single digits).
- Build clean, unit tests clean.

## R1 — Control: does a session survive on its own?

**PASS**, with a major caveat reported separately below rather than folded into the verdict.

- Settings written: `log-level=0`, `log-source=0`, `wifi-connection-mode=1`.
- No USB device near the phone for the entire run.
- Discard-rule check: not applicable (Self Mode; per `self-mode-bt-audio-round1-brief.md` §0 the
  discard rules are inert on loopback).
- **Three consecutive cold-start attempts got stuck** on "Android is starting" with zero video, each
  hitting Android Auto's own timeout:
  `08-25 00:23:21.838 CAR.SERVICE.FCD.LITE: timed out at stage FIRST_ACTIVITY_LAUNCHED after 5000
  milliseconds, publishing PROJECTION_NOT_STARTED`
- Once established, measured for a genuinely untouched **195s window** (00:34:30–00:37:52, confirmed
  still live and healthy through 00:38:00): steady 50–52fps, `dropped=0`, `concealed=0`, zero USB
  events, no disconnect.
  `08-25 00:37:50.751 VideoDecoder.logThroughput | Throughput over 5006ms: rendered=262 (52fps)...
  dropped=0, skipped=0, concealed=0`
- The question R1 actually asks is answered cleanly: **an already-streaming session does not die on
  its own.** The correlation the round rests on is not an artifact of general instability. The
  cold-start problem is a distinct, likely more fundamental defect, reported under "Anything the
  brief did not ask about" rather than gating this verdict.

## R2 — Detach a USB audio device mid-session

**FAIL** (reproduction). Full capture: `round-usb-session-teardown/r2.txt` (10.9 MB),
Gearhead pid slice: `round-usb-session-teardown/r2-gearhead.txt`.

- Settings: unchanged from R1.
- Audio dongle (UGREEN-BT701) plugged in before launch. Video confirmed flowing at 00:41:17 after
  this run's own cold-start delay (required 3 manual Self Mode re-taps — see Setup notes). Settle:
  66s untouched, 00:41:17–00:42:23. Physically unplugged at **00:42:36.446**.
- Decisive log lines, in order:
  ```
  00:42:31.396  CAR.SERVICE.USBMON.LITE: Port status changed for UsbPort{id=otg_default...
                UsbPortStatus{connected=false...}}
  00:42:31.403  GH.ConnLoggerV2: ... USB_PORT_DISCONNECTED
  00:42:31.880  UsbReceiver.onReceive | USB Intent: ... USB_DEVICE_DETACHED   (first of two)
  00:42:32.411  Companion.decrypt | RECV: VIDEO Media Data ...                (last RECV)
  00:42:32.415  AapReadSingleMessage.doRead | AapRead: Connection closed (EOF). Disconnecting.
  00:42:32.415  GH.DHUService: java.io.IOException: write failed: EPIPE (Broken pipe)
  ```
- Measurements:
  - T minus USB broadcast: **535 ms** (00:42:32.415 − 00:42:31.880).
  - T minus last RECV: **4 ms** — cut mid-stream, not quiet first.
  - `USB_ACCESSORY_DETACHED` count: **0**.
  - Gearhead hit in `[T-2s,T]`: yes (USBMON port-status + `GH.DHUService` EPIPE, same millisecond as
    our EOF). `ConnectivityService`/`NetworkAgent`/`destroySockets`/`onLost` hits: **0**.
- Attribution for this run: **H1.**

## R3 — Detach the non-audio device mid-session

**FAIL** (reproduction). **This is the round's discriminator, and it did not discriminate.**
Full capture: `round-usb-session-teardown/r3.txt` (8.2 MB), Gearhead pid slice:
`round-usb-session-teardown/r3-gearhead.txt`.

- Settings: unchanged.
- Substituted device: Logitech MX Vertical mouse (see Setup notes). Plugged in before launch; video
  flowing immediately this run, no cold-start delay (707 packets at the 15s check). Settle: ≥60s
  untouched (video-healthy check at 00:47:33.372 showed 4077 packets cumulative). Physically
  unplugged at **00:47:42.783**.
- Decisive log lines, in order:
  ```
  00:47:39.392  UsbReceiver.onReceive | USB Intent: ... USB_DEVICE_DETACHED
  00:47:39.533  CAR.SERVICE.USBMON.LITE: Port status changed for UsbPort{id=otg_default...
                UsbPortStatus{connected=false...}}
  00:47:39.538  GH.ConnLoggerV2: ... USB_PORT_DISCONNECTED
  00:47:40.547  Companion.decrypt | RECV: VIDEO Media Data ...                (last RECV)
  00:47:40.554  AapReadSingleMessage.doRead | AapRead: Connection closed (EOF). Disconnecting.
  00:47:40.554  GH.DHUService: java.net.SocketException: Socket closed
  ```
- Measurements:
  - T minus USB broadcast: **1162 ms** (00:47:40.554 − 00:47:39.392).
  - T minus last RECV: **7 ms** — cut mid-stream, not quiet first.
  - `USB_ACCESSORY_DETACHED` count: **0**.
  - Gearhead hit in `[T-2s,T]`: yes (USBMON port-status + `GH.DHUService` SocketException, same
    millisecond as our EOF). `ConnectivityService` lines present but explicitly `NetReassign [no
    changes]` — no actual loss. `NetworkAgent`/`destroySockets`/`onLost` hits: **0**.
- **A HID-only, non-audio, non-Bluetooth device kills the session on detach exactly like the audio
  dongle did**, with the identical Gearhead-side signature (port-status change → `GH.DHUService`
  socket death, simultaneous with our own EOF). The trigger is not an audio-route change; it is
  Android Auto's own monitoring of the physical USB port itself.

Both runs: `Bound socket to network: 178` confirmed present (H2's precondition — the loopback socket
is pinned to a WiFi `Network` it never uses), but no loss event was ever seen against that network in
either detach window.

## Attribution: H1

Both R2 and R3 name the same author with the same signature: **Android Auto's own Headunit Server**
(`GH.DHUService`, driven by `CarService`'s `UsbPort` monitor, `CAR.SERVICE.USBMON.LITE`) closes the
Self Mode loopback session whenever the phone's **physical USB port itself** reports a state change —
regardless of what device is attached or detached, audio class or not.

- **H3 is ruled out.** `ACTION_USB_ACCESSORY_DETACHED` fired zero times in either run; that action is
  peripheral-mode-specific and was never expected to fire while the phone hosts a device (matches
  what R6 would have probed, though R6 itself was not separately run — see "Not run").
- **H2 is ruled out.** No `ConnectivityService`/`NetworkAgent` loss, no `destroySockets`, no `onLost`
  in either detach window. The only `ConnectivityService` activity present (R3) was routine
  `NetReassign [no changes]` housekeeping, not a loss event.
- **H1 is confirmed, and refined**: it is not "Android Auto tears down its own projection on an audio
  focus change" — R3's non-audio mouse rules that framing out. It is specifically Android Auto's
  Headunit Server watching the phone's own USB host port and closing the loopback connection on any
  port state change.

This is Android Auto's own behavior on the reporter's exact configuration (Self Mode, loopback
Headunit Server), not a bug in Open Headunit. The reporter is also on Self Mode, so this fully
explains the original report with no fix on our side — the outcome the brief flagged as possible for
H1 in §1.

## Not run

R4 (attach mid-session), R5 (Wireless Helper transport), and R6 (dedicated `ACCESSORY_DETACHED` probe
with the phone as a USB peripheral) were not run this round. Stopped once R2 and R3 gave a clean,
doubly-confirmed, high-confidence attribution, and the test phone's battery was running low.

R6's specific question is answered incidentally by R2/R3 (`ACCESSORY_DETACHED` fired 0 times while
device-hosting in both runs), but the dedicated peripheral-mode probe (phone plugged into a host) is
untested. A follow-up round could confirm whether the mechanism holds over the `WirelessServer`
transport (R5) as well, since `GH.DHUService` is specific to the local Self Mode / loopback path —
the reporter's third recollection ("once on a Wireless Helper session") is not explained by this
round's findings and remains open.

## Anything the brief did not ask about

- **The cold-start reliability issue is likely a significant, separate defect.** Three consecutive
  fresh launches on this phone this round got stuck on "Android is starting" for anywhere from ~5s to
  ~2.5 minutes, or required a manual server bounce, before ever reaching a stable session — hitting
  Android Auto's own 5-second `FIRST_ACTIVITY_LAUNCHED` timeout every time. Once a session did
  establish, it was rock solid (R1's 195s clean window; R2/R3 healthy up to the USB event under
  test). Worth its own round if it reproduces on a phone that hasn't been through several other
  rounds' accumulated state — this one had.
- **An unclean local disconnect after Android Auto has already silently abandoned a session wedges
  the Headunit Server for the next attempt.** A user tapping "disconnect" on a session that looks
  connected app-side but already died server-side can itself break the *next* connection, not just
  end the current one. Not something this round can fix, but worth knowing when reading future
  reporter logs that show a "stuck" pattern after a manual disconnect.
