# usb-version-retry: round 2 brief

This round grades two changes on one branch:

- the discard fix, now bounded by time instead of a count;
- a new connection arbiter.

It also finishes pause-auto-connect's last open case (the before-SSL X).

## 1. Build

- Candidate: **`fix/usb-auto-connect`** on the fork, tip **`24d50324`**. Seven commits on `main` `dd454eed`:
  - the three round 1 tested (`001cb34c`, `1984b1ad`, `0933a348`);
  - `50f6c4c4`, the discard fix;
  - `5d42773e`, `e7b491b7`, `c9be03bc` and `24d50324`, the arbiter.
  ```bash
  git fetch fork fix/usb-auto-connect
  git checkout -B fix/usb-auto-connect fork/fix/usb-auto-connect
  git rev-parse --short HEAD   # 24d50324
  ```
- JVM gate: **2346 tests, 0 failures** on this SHA, from the author's side.
- DEX identity: `ConnectionArbiter`, `ConnectionPriorityPolicy` and `HandshakeMessagePolicy` must all be in the APK (template §5).
- **Name the APK by SHA as soon as it is built** (`apks/candidate-24d50324.apk`). In round 1, rebuilding the baseline overwrote the candidate. No baseline is needed this round.

## 2. What changed since round 1, and why

**The discard cap was wrong.** On round 1's contaminated pass, plugs 3 and 6 each logged `more late VERSION_RESPONSEs than version requests sent`.

- The captures show why. The dongle went silent for 20 to 33 s across two or three handshakes, then sent every answer at once. Handshake 2 met 5 answers for its 3 requests.
- The cap then failed it mid-TLS, and the next handshake met the phone's TLS reply: shape B.
- Our wireless stack was **not** running in either window: the pill's X had held it off since `16:20:24`. So round 1's "contention" reading is wrong for those two plugs.
- `50f6c4c4` discards late answers until the TLS deadline instead. A deadline expiry prints `SSL Handshake: still receiving late VERSION_RESPONSEs at the deadline`.

**The arbiter** is new, and the user asked for it. USB, a connection the user asked for, and the wireless stack used to run side by side, each blind to the others. Now one attempt holds the connection at a time, by tier:

| Rank | Tier | Examples |
|---|---|---|
| 1 | USER | an `ACTION_CONNECT ip=` verb, the network list, the USB button |
| 2 | USB | an attach, the AOA switch, an accessory connect |
| 3 | WIRELESS_HANDSHAKE | the Native AA handshake, a phone's socket on 5288, NSD |
| 4 | background | the armed wireless stack: pokes, group, discovery |

- A higher tier **preempts** a lower one in flight, and holds new lower ones off.
- Anything from outside the wireless stack stands the stack down.
- When the higher attempt ends with no session, whatever it took is given back after 1.5 s: the wireless stack is re-armed, or a USB check is asked again. If the attempt formed a session, the giving back waits for that session's end, unless the user ended it.
- The wired-session re-arm now goes through the existing USB deferral, so it no longer races the next USB retry.

## 3. Rig

Stage A, the same rig as round 1:

- D-POCO as head unit and USB host over OTG, on wireless adb;
- the Carlinkit-class dongle (idle `18d1:4ee1`, accessory `18d1:2d00`);
- D-MOTO paired with the dongle.

Round 1's rules on the two readers (`capture.sh`), hand replugs and `stdbuf -oL` still apply.

- **Legacy keys:** with the app stopped, run `DEL native-aa-wireless` and `DEL wifi-launcher-mode` before any `SET wifi-connection-mode`. One of those keys silently turned round 1's mode 0 back into 3. Read the mode back after every relaunch.
- **`SettingsActivity` is not exported**, so `am start` cannot open it. No run in this round needs it.
- **The pill's X is sticky.** Round 1's contaminated pass had wireless held by the X for most of its length. Before every run that needs wireless armed, send `send ACTION_START_WIRELESS` and wait for `WifiLauncher: Initializing WiFi Mode: NATIVE`.
- No taps. Every action is template §3's `send`, or a hand replug.

## 4. The lines that decide every run

Checked with `git grep -F` on `24d50324`. `<what>` is the attempt's own name, for example `USB switch of Google Pixel 4 (VID: 18D1 PID: 4EE1)` or `192.0.2.1:5277`.

| Line (substring) | Meaning |
|---|---|
| `ConnectionArbiter: ` … ` preempts ` | a higher attempt ended a lower one |
| `stood the wireless stack down until it ends` | a USB or user attempt stopped the armed stack |
| ` refused while ` … ` is in flight` | a lower attempt was held off |
| `ConnectionArbiter: a USB check waits for` | a USB check met another owner's connect, and will be asked again |
| `ended with no session; giving back` | what was taken comes back, 1.5 s later |
| `ConnectionArbiter: wireless bring-up refused while` | an automatic bring-up was held off by an attempt |
| `AapService: a USB projection attempt is in flight` | the existing deferral holding a re-arm while the dongle is on the bus |
| `WifiLauncher: Initializing WiFi Mode: NATIVE` | the stack came up |
| `NativeAA: Attempting active poke`, `createGroup SUCCESS` | the stack at work |
| `SSL Handshake: discarded a late VERSION_RESPONSE` | the discard (round 1) |
| `SSL Handshake: still receiving late VERSION_RESPONSEs at the deadline` | the new bound expired (should be rare) |
| `Unable to parse TLS packet header`, `SSL Handshake: Failed to read AAP header` | shapes A and B, the bug |
| `Switching USB device to accessory mode`, `Found device already in accessory mode` | a USB attempt started |
| `SSL handshake complete` | a session formed (match without the `Handshake:` prefix) |

## 5. Settings keys

On D-POCO, with `set_prefs_runas.sh`. Back up `settings.xml` first and restore it at the end.

| Key | Type | Value | Why |
|---|---|---|---|
| `native-aa-wireless`, `wifi-launcher-mode` | | `DEL` | §3 |
| `wifi-connection-mode` | int | per run | `3` for R1, R3 and R5; `0` for R2 |
| `auto-start-on-usb` | boolean | `true` | as round 1 |
| `reopen-on-reconnection` | boolean | `true` | as round 1 |
| `auto-connect-last-session` | boolean | `true` | as round 1 |
| `kill-on-disconnect` | boolean | `false` | as round 1 |
| `use-libusb` | boolean | `true`, except R2 | |

## 6. Runs

### R0 Build gate

PASS needs all four:

- SHA `24d50324`;
- DEX carries the three symbols;
- JVM gate 2346/0;
- `adb install -r` on D-POCO.

### R1 USB over the armed wireless stack (the point of the round)

**Setup.** Mode 3, libusb, home screen, no session. Send `ACTION_START_WIRELESS` and confirm `Initializing WiFi Mode: NATIVE`.

**Procedure.** Do **10 cold plugs**, each like this:
1. Unplug, wait 5 s, plug.
2. Wait for `SSL handshake complete` or 90 s.
3. Send `ACTION_DISCONNECT`.
4. Wait **15 s** so wireless re-arms (about 1.5 s, plus up to 8 s of deferral while the dongle sits on the bus).
5. Before the next unplug, confirm that `Initializing WiFi Mode: NATIVE` printed after the disconnect.

**Record for each plug:**
- whether the stand-down line printed, and whether it came before the first `Handshake: Version request sent`;
- the count of poke and `createGroup` lines between the stand-down line and `SSL handshake complete`;
- the trigger and discard counts;
- the accessory-to-SSL time.

**PASS needs all of:**
- the stand-down line on every plug where the stack was up at the plug, and before the version request;
- **zero** `Attempting active poke` and **zero** `createGroup SUCCESS` between the stand-down line and SSL;
- wireless back up after every `ACTION_DISCONNECT`;
- zero shape A, zero shape B, zero deadline line;
- SSL on the first handshake after the plug in at least **9 of 10** plugs.

If the stack was never up at a plug time, the stand-down was not exercised, so that plug grades INCONCLUSIVE.

### R2 The clean control

Mode 0 (legacy keys deleted, the mode read back), standard route (`use-libusb=false`), 10 cold plugs as in round 1's R1.

**PASS:** round 1's conditions (zero shape A and B, a discard on every retried handshake, SSL first time in at least 9 of 10) plus zero `ConnectionArbiter:` preempt or refused lines.

### R3 USB preempts a Native AA handshake

**Setup.** Mode 3, and a phone that can run a Native AA handshake with D-POCO. D-MOTO is busy with the dongle, so use another paired phone if there is one.

**Procedure.**
1. Arm this watcher:
   ```bash
   stdbuf -oL adb logcat -T 1 OPENHU:V '*:S' | grep -m1 -F 'NativeAA: Handling handshake for'
   ```
2. Wake the phone with `send ACTION_NATIVE_AA_POKE --es extra_mac <phone MAC>`.
3. Plug the dongle by hand as soon as the watcher returns.

**PASS:**
- `ConnectionArbiter: USB switch of … (USB) preempts the Native AA handshake with … (WIRELESS_HANDSHAKE)`;
- zero poke or `createGroup` lines after it until SSL or the end of the USB attempt;
- after the USB attempt or session ends, wireless is re-armed (`Initializing WiFi Mode: NATIVE`).

Report whether USB reached SSL; it is not graded, since the dongle needs D-MOTO. If no second phone can be brought into a Native handshake, report R3 NOT RUN and say why.

### R4 A user's connection preempts USB

**Setup.** Mode 0, libusb, home screen. `192.0.2.1` is a documentation address that never answers, so the TCP connect times out after about 5 s.

**Procedure.** Arm the watcher, then plug the dongle:
```bash
stdbuf -oL adb logcat -T 1 OPENHU:V '*:S' | grep -m1 -F 'Switching USB device to accessory mode' >/dev/null \
  && send ACTION_CONNECT --es ip 192.0.2.1
```

**PASS, in this order:**
1. `192.0.2.1:5277 (USER) preempts USB switch of …`;
2. either `ConnectionArbiter: a USB check waits for 192.0.2.1:5277 (USER)` or `USB … (USB) refused while 192.0.2.1:5277 (USER) is in flight`;
3. `192.0.2.1:5277 (USER) ended with no session; giving back`, with USB in the list (mode 0 may also list wireless, which is harmless);
4. then, without a replug, `Found device already in accessory mode` and `SSL handshake complete`.

If the switch finishes before the preempt line, report the gap from the switch line to the verb's `AutomationReceiver` line and re-run, 3 tries at most.

### R5 A failed USB attempt gives wireless back

**Setup.** Mode 3, libusb, wireless armed. Turn D-MOTO's WiFi off so the dongle cannot reach it.

**Procedure.** Plug the dongle and watch for 3 minutes.

**PASS:**
- the stand-down line on the first attempt;
- after the last USB handshake failure, `Initializing WiFi Mode: NATIVE` within **12 s**;
- from then on, the stack stays up except while a new USB attempt holds the arbiter.

Report every `giving back` and `deferral` line with its timestamp. Turn D-MOTO's WiFi back on afterwards.

### R6 The before-SSL X (pause-auto-connect R4)

**Setup.** Mode 0, libusb, home screen. The attach screen now reports the USB stage at the switch, so an X that lands there takes the USB branch.

**Procedure.** Run the watcher and replug:
```bash
stdbuf -oL adb logcat -T 1 OPENHU:V '*:S' | grep -m1 -F 'Switching USB device to accessory mode' >/dev/null \
  && send ACTION_CANCEL_WIRELESS
```

**PASS:**
- `the status pill's X stopped the USB attempt.`;
- then `UsbLauncher: USB auto-connect refused: the status pill's X holds it` for the re-enumerated accessory;
- zero `Handshake: Version request sent` and zero `SSL handshake complete` after the X.

Three tries. If the switch line arrives after SSL every time, grade INCONCLUSIVE and give the gaps.

## 7. Do not re-run

- Round 1's R3 (the settings hold's SSL leg) PASSed on `0933a348`, and nothing in the hold changed.
- pause-auto-connect round 1's other PASSes still stand.
- Stage B is not in this round.

## 8. Report back

1. **R1 and R2:** the per-plug tables, with the counts of trigger, discard, shape A, shape B, stand-down and poke-in-window lines.
2. **R3 to R6:** the verdicts, the ordered lines with timestamps, and the gaps named above.
3. **Audio at session start:** round 1 noted a brief stutter at every dongle session start. Not graded. From each R1 session report:
   - `grep -c "disabled due to previous underrun"`;
   - the `AudioTrackWrapper: playback started with N frames banked` line.

Captures: release `rig-evidence-usb-version-retry`, new asset `usb-version-retry-round2-captures.zip`, cited with its sha256 (template §7).
