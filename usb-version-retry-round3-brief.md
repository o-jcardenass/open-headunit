# usb-version-retry: round 3 brief

This round checks the fix for round 2's R1 FAIL and the R5 hold, which are two faces of one gap. It
also retries R4 now that its preempt line can print.

## 1. Build

- Candidate: **`fix/usb-auto-connect`** on the fork, tip **`181e7138`**. That is round 2's `24d50324` plus two commits:
  - `a42fbb5d` holds USB's claim through the handshake and between retries;
  - `181e7138` stops a failed handshake from counting as a user exit.
  ```bash
  git fetch fork fix/usb-auto-connect
  git checkout -B fix/usb-auto-connect fork/fix/usb-auto-connect
  git rev-parse --short HEAD   # 181e7138
  ```
- JVM gate: **2354 tests, 0 failures** on this SHA, from the author's side.
- DEX identity: `ConnectionArbiter`, `ConnectionPriorityPolicy` and `HandshakeMessagePolicy` (template §5).
- Name the APK by SHA as soon as it is built (`apks/candidate-181e7138.apk`).

## 2. What round 2 found, read from its captures

**R1's failure was not the arbiter's own give-back.**
- Each USB claim ended the moment its step returned:
  - the attach screen's claim when the AOA switch returned, 27 ms after it began;
  - the service's claim when the accessory transport opened.
- So the version handshake, up to 10 s of it, ran with nothing held.
- When it failed, the old "wired session ended" re-arm brought wireless up, `rearmWirelessAfterWiredSession` at `22:44:52.765`.
- The dongle came back 5.5 s later as `4EE1` and took it down again.

**R5's 173 s hold was an older bug.**
- The USB deferral restarted its 8 s budget on every retry, because a transport that is open but still handshaking counted as "connected".
- A dongle with no phone therefore held wireless off for as long as it kept retrying.

**Every failed handshake was logged as a user exit** (`session state disconnected (user_exit)`, `User exit cooldown active`). After that, a re-armed Native stack refused to poke: `userExitedAA is true. Skipping auto-poke`.

**Three points in the round 2 results are corrected here.** None of them is a defect.
- **Plug 3 had no stand-down line** because someone tapped the pill's X at `22:47:55.297` (`MainActivity.cancelBringUp | status pill X pressed`). The stack was already down.
- **The X was not left behind by `ACTION_DISCONNECT` or `ACTION_CONNECT`.** The R6 watcher fired `ACTION_CANCEL_WIRELESS` twice, at `23:29:28` and `23:31:42`. An X during a USB attempt holds USB until the USB button, by design.
- **The self-reconnect after `ACTION_DISCONNECT` is reopen-on-reconnection working.** With the dongle still plugged in, it re-enumerates on its own and gets a new session.

## 3. What changed

- **The claim now lasts from the switch to the handshake's outcome.** An opened transport keeps its claim until SSL completes or fails.
- **A USB plug-in is an episode.** After a USB attempt ends with no session, wireless stays down for **8 s** so USB can try again. Round 2 measured the dongle's gap at 5.5 s and the service's re-check at 3 s. A retry inside that window carries the hold on.
- **A plug-in gets a budget of 60 s.** When it runs out, wireless comes back and the plug-in's later retries stop standing it down.
- The episode ends when:
  - a session reaches SSL;
  - USB stays quiet for 8 s;
  - the user starts wireless by hand.
- **The wired-session stand-down moved from transport open to SSL**, so a spent plug-in's retries leave wireless alone.
- **The USB deferral no longer restarts its budget while a handshake is in flight,** and it steps aside once a plug-in is spent.
- **A failed handshake disconnects as a non-user exit with no ByeBye:** no cooldown, and the next Native poke is not skipped.

## 4. Rig

Stage A, the same rig as rounds 1 and 2:

- D-POCO as head unit and USB host over OTG, on wireless adb;
- the dongle (idle `18d1:4ee1`, accessory `18d1:2d00`);
- D-MOTO paired with the dongle.

Round 2's §3 rules still apply: `DEL` both legacy keys, read the mode back after every relaunch, `stdbuf -oL`, and no taps.

**New rules:**

- **D-POCO's Bluetooth is ON for the whole round, and it goes in the setup table.** Round 2's clean R1 plugs were the ones run with it off. Only with it on did the dongle run slow enough to exercise the fix.
- **D-MOTO's WiFi cannot be held off.** Android Auto turns it back on by itself so that it can connect. Do not use it as a lever. It is also what invalidated round 2's first two R5 attempts. To make the dongle's phone unreachable, **power D-MOTO off**.
- **Never touch the pill.** Kill every `logcat | grep -m1 … && send …` watcher as soon as its run ends. A leftover watcher is what fired the X in round 2.
- **If the X is holding USB** (`UsbLauncher: USB auto-connect refused: the status pill's X holds it`), `send ACTION_CONNECT` with no `ip` lifts it. `send ACTION_START_WIRELESS` lifts the wireless X. Do not force-stop unless both fail.

## 5. The lines that decide every run

Checked with `git grep -F` on `181e7138`.

| Line (substring) | Meaning |
|---|---|
| `stood the wireless stack down until it ends` | a USB or user attempt stopped the armed stack |
| `ended with no session; USB has 8000ms to try again` | a USB try failed; wireless waits out the quiet window (new) |
| `ConnectionArbiter: wireless bring-up refused while` | an automatic re-arm held off, either `… is in flight` or `USB is between tries` (new) |
| `ended with no session; giving back` | what was taken comes back (1.5 s later) |
| `USB has tried for 60 s with no session; wireless comes back beside it` | the plug-in spent its budget (new) |
| ` preempts ` | a higher attempt ended a lower one |
| `ConnectionArbiter: a USB check waits for` | a USB check met a user's connect in flight |
| `stopping the wireless stack for the duration of it` | the wired-session stand-down, now at SSL |
| `WifiLauncher: Initializing WiFi Mode: NATIVE`, `createGroup SUCCESS`, `NativeAA: Attempting active poke` | the stack at work |
| `userExitedAA is true. Skipping auto-poke`, `User exit cooldown active` | the old user-exit reading; after a failed handshake these must be gone |
| `Unable to parse TLS packet header`, `SSL Handshake: Failed to read AAP header`, `still receiving late VERSION_RESPONSEs at the deadline` | shapes A and B, and the deadline |
| `Switching USB device to accessory mode`, `Found device already in accessory mode` | a USB attempt started |
| `SSL handshake complete` | a session formed (match without the `Handshake:` prefix) |

## 6. Settings keys

The same as round 2 §5, with a back-up first and a restore at the end.

| Run | `wifi-connection-mode` | `use-libusb` |
|---|---|---|
| R1 | `3` | `true` |
| R2 | `0` | `false` |
| R4 | `0` | `true` |
| R5 | `3` | `true` |

## 7. Runs

### R0 Build gate

PASS needs all four:
- SHA `181e7138`;
- the DEX carries the three symbols;
- JVM gate 2354/0;
- `adb install -r` on D-POCO.

### R1 USB over the armed wireless stack, Bluetooth on (the point of the round)

**Setup.** Mode 3, libusb, home screen, no session. D-POCO's Bluetooth is on, and D-MOTO is on and in reach. Send `ACTION_START_WIRELESS` and confirm `Initializing WiFi Mode: NATIVE`.

**Procedure.** Do **10 cold plugs**, each like this:
1. Plug the dongle.
2. Wait for `SSL handshake complete`, or 120 s.
3. **Unplug** to end the session. Do not use `ACTION_DISCONNECT`, because with the dongle still attached it reconnects by itself (see §2).
4. Wait 15 s, and confirm `Initializing WiFi Mode: NATIVE` printed after the unplug.

**Record for each plug:**
- the stand-down line;
- the number of `USB has 8000ms to try again` lines;
- the number of `wireless bring-up refused while` lines;
- the trigger and discard counts;
- the plug-to-SSL time;
- the counts of `Initializing WiFi Mode`, `createGroup SUCCESS` and `Attempting active poke` between the stand-down and SSL.

**PASS needs all of:**
- the stand-down line on every plug;
- **zero** `Initializing WiFi Mode`, `createGroup SUCCESS` or `Attempting active poke` between a plug's stand-down and its SSL, across every internal retry;
- SSL on **10 of 10** plugs, within the plug, with no replug;
- wireless back within 15 s of every unplug;
- zero shape A, zero shape B, zero deadline lines.

**Coverage.** Round 2 needed its slow plugs to exercise anything. If fewer than 3 plugs print a `USB has 8000ms to try again` line, the retry path was not exercised enough. In that case add plugs, up to 20, and report how many did.

### R2 The clean control

Mode 0, standard route, **5 cold plugs**, ending each session by unplugging.

**PASS:**
- zero shape A and zero shape B;
- a discard on every retried handshake;
- SSL on 5 of 5;
- zero `ConnectionArbiter:` preempt or refused lines.

### R4 A user's connection preempts USB

**Setup.** Mode 0, libusb, home screen, D-MOTO on and in reach.

**Procedure.** The claim now lives through the handshake, so the watcher keys on the accessory line. Arm it, then plug:
```bash
stdbuf -oL adb logcat -T 1 OPENHU:V '*:S' | grep -m1 -F 'Found device already in accessory mode' >/dev/null \
  && send ACTION_CONNECT --es ip 192.0.2.1
```

**PASS, in this order:**
1. `192.0.2.1:5277 (USER) preempts USB …`;
2. zero `SSL handshake complete` for that USB attempt;
3. `192.0.2.1:5277 (USER) ended with no session; giving back`, with USB in the list;
4. then, without a replug, `Found device already in accessory mode` and `SSL handshake complete`.

Three tries, and kill the watcher after each. If SSL beats the verb on all three, grade INCONCLUSIVE and give the gap from the accessory line to the verb's `AutomationReceiver` line.

### R5 A USB plug-in that can never connect

**Setup.** Mode 3, libusb. Send `ACTION_START_WIRELESS` and confirm `Initializing WiFi Mode: NATIVE`. **Power D-MOTO off** (see §4).

**Procedure.**
1. Plug the dongle and leave it plugged in for 3 minutes.
2. Power D-MOTO on, and wait until it is fully booted with Bluetooth and WiFi up.
3. Unplug the dongle, wait 15 s, plug it again, and wait up to 120 s for SSL.

**PASS needs all of:**
- **Step 1:**
  - the stand-down line on the first attempt;
  - `USB has tried for 60 s with no session; wireless comes back beside it` 55 to 70 s after that stand-down;
  - `Initializing WiFi Mode: NATIVE` within 12 s after that line;
  - `createGroup SUCCESS` after that;
  - **zero** `stood the wireless stack down` lines for the rest of step 1, while the dongle keeps retrying;
  - zero `userExitedAA is true. Skipping auto-poke` and zero `User exit cooldown active` lines after any failed handshake.
- **Step 3:**
  - a **new** stand-down line on the replug (the episode ended while the dongle was unplugged);
  - SSL.

Report every arbiter line and every `Initializing WiFi Mode` line with its timestamp. Report `Attempting active poke` lines too, ungraded.

## 8. Do not re-run

- **R6** (the before-SSL X) PASSed in round 2 on `24d50324`. The X's path did not change.
- **R3** stays open until a second phone can hold a Native handshake with D-POCO. Run it only if one is available, exactly as round 2 §6 R3 describes.
- Stage B is not in this round.

## 9. Report back

1. **R1 and R2:** the per-plug tables, with D-POCO's Bluetooth state in the setup notes.
2. **R4 and R5:** the verdicts, the ordered lines with timestamps, and the gaps named above.
3. **Audio (ungraded):** as round 2 §8.3, for the R1 sessions.

Captures: release `rig-evidence-usb-version-retry`, new asset `usb-version-retry-round3-captures.zip`, cited with its sha256 (template §7).
