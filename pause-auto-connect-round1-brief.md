# pause-auto-connect: round 1 brief

> **Revised 2026-09-23, before any run.** Every action is now an `AutomationReceiver` verb (template
> §3, `send ...`); there is no `input tap` left in this brief. Re-read §3's last bullet, R1 step 1,
> R4 and R6. R4 changed most: the X and the USB button are broadcasts, the X fires from a log
> watcher instead of by eye, and its lift line is different. A copy that still says "tap" is stale.

## 1. Build and baseline

- Branch `fix/pause-auto-connect-all-modes` on the fork, tip **`1984b1ad`**, two commits on `main`
  `dd454eed`. New branch, never rewritten.
  ```bash
  git fetch fork fix/pause-auto-connect-all-modes
  git checkout -B fix/pause-auto-connect-all-modes fork/fix/pause-auto-connect-all-modes
  git rev-parse --short HEAD   # 1984b1ad
  ```
- JVM gate: **2330 tests, 0 failures** on this SHA from the author's side. Re-run it with the rig's
  `run_unit_tests.sh`.
- DEX identity: `AutoConnectHoldPolicy` must be in the APK (template §5's symbol check).

## 2. What this is and why it exists

Issue #1011: a user on a **USB wireless-AA dongle** stops the session and opens settings, and the app
reconnects straight away and pulls the home screen over settings. They asked for a pause while in
settings, or a pause button on the main screen.

3.4.0 did this for the wireless stack only (`SettingsScreenPausePolicy`, and the status pill's X
holding wireless down). No USB path read any of it. A dongle re-enumerates at the end of its own
session, which fires the attach handlers, and those call `checkAlreadyConnected(force = true)` and
`launchMainActivityIfNeeded` with no settings check. A Bluetooth arrival also launched `MainActivity`
over settings in every mode. `MainActivity` is `singleTask`, so that launch **closed** the settings
screen, including in Native mode.

What the branch does:

- **While the settings screen is visible**, every automatic USB check is held, and nothing raises
  the home screen: the USB attach trampoline, reopen-on-reconnection, the Bluetooth auto-start and
  the WiFi auto-start. A held USB check, or a held Bluetooth raise, is replayed once, **1.5 s after
  the screen closes**.
- The signal is `SettingsActivity.isVisible` (`onStart`/`onStop`), not `isForeground`. The USB attach
  trampoline is translucent, so it *pauses* settings before its own `onCreate` runs.
- **The status pill's X on a USB attempt** now latches USB auto-connect off. Every automatic USB
  check is then refused until the USB button, the USB list, `headunit://connect` or an automation USB
  verb asks for one. The X on a wireless attempt behaves exactly as on 3.4.0.

## 3. What is different about this round

- **Two stages.** The MT50 cannot host USB (template §7b), so the USB runs use the arrangement of
  `usb-aoa-handoff` round 2: **D-POCO as head unit and USB host over OTG**, on wireless adb, with the
  **Carlinkit-class AA dongle** (idle `18d1:4ee1`, accessory `18d1:2d00`) and whichever phone the
  dongle is paired with. Name that phone in Setup notes. The Bluetooth runs are Stage B on **D-HU**,
  in Native mode, with a phone as the Bluetooth peer.
- **Every Stage A command and both log readers run over wireless adb to D-POCO, never a cable.**
  D-POCO's only port carries the dongle on OTG, and a PC cable there would make D-POCO a USB
  *peripheral* rather than the host, so no run is possible while one is plugged in. Set it up while
  still cabled, then unplug the PC and plug the OTG adapter:
  ```bash
  adb -s 4f4027e9 tcpip 5555
  adb connect <D-POCO's LAN IP>:5555      # round 2 of usb-aoa-handoff used 192.168.1.10
  adb devices                             # the IP:5555 entry must say "device"
  export ANDROID_SERIAL=<D-POCO's LAN IP>:5555
  ```
  Put the address in Setup notes. Before each run, confirm `adb devices` still lists it. After each
  run, confirm both reader files grew. A reader that died mid-run voids that run: re-run it, never
  grade it from a partial capture.
- **The dongle's attach does reach `UsbAttachedActivity`** on this host. That was measured in that
  round's R11. The MTP short-circuit in template §7b applies to a *phone* plugged in, not to the dongle.
- **Two readers in Stage A, per template §7b:** `OPENHU:V '*:S'`, and `-s UsbHostManager:D`. The second one is
  what proves a USB event happened inside a window. Use that round's `capture.sh` `start`/`stop`
  pattern: an inline `adb logcat &` died over wireless adb when the dongle was replugged fast.
- **Unplugging and replugging the dongle is hand-operated.** Do it slowly, and leave at least 5 s
  between an unplug and the plug.
- **Which screen is in front** is read with
  `adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity"`. A
  translucent trampoline can sit over settings for a moment, so read it about 3 s after the event.
- **No taps.** Define template §3's `send` helper on each unit and use it for every action. The
  pill's X is `send ACTION_CANCEL_WIRELESS`, the USB button is `send ACTION_CHECK_USB`, an exit is
  `send ACTION_DISCONNECT`. The X broadcast skips `MainActivity`'s own read, which is fine: the
  service reads `isUsbSession` itself before it disconnects, and that is the branch R4 grades. A
  step whose `AutomationReceiver: <action>` line is missing never landed: the run is void, repeat it.

## 4. Settings keys

Stage A, on D-POCO. Back up `settings.xml` first (template §1), and write it with `set_prefs_runas.sh`, because
the POCO is not rooted.

| Key | Type | Value | Why |
|---|---|---|---|
| `auto-start-on-usb` | boolean | `true` | the reporter's setup; turns on the attach trampoline's launch and reopen-on-reconnection |
| `reopen-on-reconnection` | boolean | `true` | the default; this is what launches `MainActivity` on a re-enumeration |
| `auto-connect-last-session` | boolean | `true` | keeps the startup scan and the post-exit reconnect armed |
| `kill-on-disconnect` | boolean | `false` | an exit must leave the app open, or there is no settings screen to open |

**`auto-start-on-usb` is mirrored** into `shared_prefs/settings_device_protected.xml` (device
protected storage), and the attach trampoline reads **the mirror**. Writing `settings.xml` alone
leaves the mirror stale. After the write, read the mirror back. If it does not say `true`, turn the
toggle off and on once in the UI (the USB auto-start setting), and read it back again. Put
both readings in Setup notes.

Stage B, on D-HU: `wifi-connection-mode` = 3, and `auto-start-bt-macs` holding the peer phone's MAC.
That set resyncs from `settings.xml`, so write it there, not only in the mirror.

## 5. The lines that decide every run

Each of these was checked with `git grep -F` on `1984b1ad`.

| Line (substring) | Meaning |
|---|---|
| `UsbLauncher: USB auto-connect held while the settings screen is open` | a USB check was held (new) |
| `AapService: the settings screen closed with a USB auto-connect held behind it, checking USB now.` | the replay on close (new) |
| `UsbAttachedActivity: settings on show or the status pill's X holding; handing` | the trampoline stood down (new) |
| `Reopen on reconnection: not raised over settings or after the status pill's X` | the home-screen raise stood down (new) |
| `AapService: the status pill's X stopped the USB attempt.` | the X took the USB branch (new) |
| `UsbLauncher: USB auto-connect refused: the status pill's X holds it` | the latch refused a check (new) |
| `UsbLauncher: ` … `so the stop from the status pill is lifted.` | the USB latch was lifted (new; the wireless one prints `WifiLauncher:`) |
| `AapService: Bluetooth auto-start while the settings screen is on show; raising the home screen when it closes.` | the Bluetooth raise was held (new) |
| `AapService: the settings screen closed with a Bluetooth arrival held behind it, raising the home screen now.` | its replay (new) |
| `AapService: the status pill's X stopped the wireless bring-up.` | the unchanged wireless X |
| `Found device already in accessory mode` / `Switching USB device to accessory mode` | a USB connect actually started |
| `Reopen on reconnection: launching MainActivity` | the raise actually ran |
| `SSL handshake complete` | a session formed (match without the `Handshake:` prefix; see CLAUDE.md) |
| `MATCH! Starting AapService` | the Bluetooth auto-start fired |

## 6. Runs

### R0 Build gate
SHA `1984b1ad`, DEX carries `AutoConnectHoldPolicy`, JVM gate 2330/0, `adb install -r` on D-POCO and
on D-HU. PASS: all four.

### R1 A dongle event during settings is held and replayed on close (the point of the round)
Setup: Stage A, keys from §4, a dongle session live (`SSL handshake complete`).
1. `send ACTION_DISCONNECT`. Within 1 s, open settings:
   `am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0`.
2. Wait 30 s. Record whether the dongle re-enumerated by itself (`UsbHostManager` `Added device`
   lines). If it did not, unplug it, wait 5 s, and plug it back.
3. 5 s after the last `Added device`, read which activity is in front.
4. Press BACK (`input keyevent 4`). Watch 30 s.

PASS, all of:
- after the settings screen opened, at least one `Added device` (proves the event happened), and at
  least one `USB auto-connect held while the settings screen is open`;
- **zero** `Found device already in accessory mode`, `Switching USB device to accessory mode` and
  `Reopen on reconnection: launching MainActivity` between the settings screen opening and BACK;
- the front activity at step 3 is `SettingsActivity`;
- after BACK, `checking USB now.` within 1.3 to 2.5 s, then `SSL handshake complete`.

If the change did nothing, the held line would never print and the session would form with settings
still open. So a PASS with zero `Added device` in the window proves nothing: report it as
INCONCLUSIVE and repeat with a physical replug.

Also report: the timestamp gap from BACK to `checking USB now.`, and from that line to
`SSL handshake complete`. And say whether step 2 needed a hand replug; that is the reporter's own
question.

### R2 A settings visit with no USB event replays nothing
Setup: Stage A, dongle plugged in and idle, no session, settings closed for 30 s with no
`Added device`. Open settings, wait 60 s, BACK, watch 60 s.
PASS: zero `Added device` inside the settings window (otherwise this was R1; record it and repeat),
zero `checking USB now.`, and no `SSL handshake complete` in the 60 s after BACK. Pair it with
`dumpsys usb` showing the dongle still attached, so "nothing connected" is not "nothing there".

### R3 Positive control: with settings closed, nothing changed
Setup: Stage A, home screen in front, no session. Unplug the dongle, wait 5 s, and plug it back.
PASS: `Switching USB device to accessory mode` or `Found device already in accessory mode`, then
`SSL handshake complete`, and zero `held while the settings screen is open`. That proves the hold
is not blanket. Do it 3 times, and report each cycle.

### R4 The pill's X on a USB attempt latches USB off
Setup: Stage A, home screen in front, no session. Arm the X on a log watcher, then plug the dongle:
```bash
adb logcat -T 1 OPENHU:V '*:S' | grep -m1 -F 'Found device already in accessory mode' >/dev/null \
  && send ACTION_CANCEL_WIRELESS
```
Then:
1. Watch 60 s. Unplug the dongle, wait 5 s, plug it back, and watch 60 s more.
2. `send ACTION_CHECK_USB` (the USB button).

PASS, all of:
- `AutomationReceiver: com.andrerinas.openheadunit.ACTION_CANCEL_WIRELESS`, then
  `the status pill's X stopped the USB attempt.`; **not** `stopped the wireless bring-up`;
- no `SSL handshake complete` from the X to step 2;
- at least one `USB auto-connect refused: the status pill's X holds it` after the replug in step 1,
  and zero `Reopen on reconnection: launching MainActivity`;
- at step 2, `UsbLauncher: a USB connection was asked for, so the stop from the status pill is lifted.`,
  then `SSL handshake complete`.

Record whether the session had already reached `SSL handshake complete` when the X landed. **An X at
SSL or later is the case the second commit fixes**, so do a second attempt with the watcher keyed on
`SSL handshake complete` instead of the accessory line; the broadcast lands in well under a second,
before the projection comes up.

### R5 A Bluetooth arrival during settings, Native mode (Stage B)
Setup: D-HU in Native mode, keys from §4, no session, the peer phone's Bluetooth off. Open settings
on D-HU. Turn the phone's Bluetooth on.
PASS, all of:
- `MATCH! Starting AapService`, then `Bluetooth auto-start while the settings screen is on show;
  raising the home screen when it closes.`;
- `SettingsActivity` still in front 5 s after `MATCH!`. On 3.4.0 this is `MainActivity`: that is the
  regression half;
- BACK, then `raising the home screen now.` about 1.5 s later. If `Bluetooth auto-start while the
  settings screen is open; re-arming when it closes.` printed earlier, then
  `the settings screen closed with a wireless request held behind it` must follow the close too;
- a session forms (`SSL handshake complete`) within 90 s of BACK.

If no `MATCH!` line prints, the arrival never reached the app: INCONCLUSIVE, and say whether the
phone's MAC was in `auto-start-bt-macs` (§4).

### R6 Regression: the wireless X is unchanged (Stage B)
Setup: D-HU in Native mode, no session, the pill showing a wireless stage (Waiting for your phone).
`send ACTION_CANCEL_WIRELESS`, then `send ACTION_START_WIRELESS_SCAN` (the WiFi button).
PASS: `the status pill's X stopped the wireless bring-up.`, zero `stopped the USB attempt`, and the
scan lifts it (`WifiLauncher: ` … `so the stop from the status pill is lifted.`). This run checks
that the service's own USB detection does not misfire on a wireless attempt.

## 7. Do not re-run

Nothing in this thread has run yet. The AOA switch itself, the attach trampoline and the libusb route
were settled in `usb-aoa-handoff` round 2 (R10, R11, R2). Do not re-prove them here.

**Not on hardware in this round, on purpose:** the Self Mode arrival, which is the same held raise as
R5 (`handleLaunchIntent` makes the Self Mode decision and is unchanged); the WiFi auto-start receiver;
and the automation USB verbs, which `AutomationCommandPolicyTest` covers.

## 8. Report back

1. **R1:** held line count, the front activity during the window, and the two gaps (BACK to replay,
   and replay to SSL). This decides whether #1011 is fixed.
2. **R4:** the refused count after the replug, and whether an X at SSL or later took the USB branch.
3. **R5:** the front activity 5 s after `MATCH!`, and whether a session formed after BACK.

Captures: `rig-evidence-pause-auto-connect` release, asset `pause-auto-connect-round1-captures.zip`,
cited with its sha256 (template §7).
