# usb-version-retry — round 3 results

**Candidate:** fork `fix/usb-auto-connect` @ `181e71386`       **Baseline:** none (not needed this round, per brief)
**APK md5:** `488022dff8a3b7803655cd7cb4142448` (`candidate-181e7138.apk`)
**Unit:** Stage A — D-POCO (POCO X3 NFC, Android 15) as USB host over OTG, wireless adb at
`192.168.1.4:5555`, Bluetooth on for the whole round; Carlinkit-class AA dongle (idle `18d1:4ee1`,
accessory `18d1:2d00`); paired phone Motorola edge 30 neo (D-MOTO), Bluetooth on and in reach except
where R5 powers it off.
**Date:** 2026-09-24

## Setup notes

- Build ran via the approved `hur-wifi-test-scripts/build_hur.sh` and `run_unit_tests.sh` (not a raw
  `./gradlew` call). A raw `assembleGithubDebug` invocation was tried first and hit a `javac` toolchain
  error (`JAVA_COMPILER` missing under `/usr/lib/jvm/java-21-openjdk-amd64`, a JRE not a JDK); the
  approved script sets `JAVA_HOME=/opt/android-studio/jbr` and built cleanly. No new script needed.
- **D-MOTO auto-connects over wireless on its own whenever mode 3 is armed and it is in range,
  independent of USB.** Once armed, D-MOTO forms a full Native AA session in as little as ~9 s (poke
  → `createGroup SUCCESS` → SSL), and this app's own Bluetooth-auto-disconnect logic tears it back down
  roughly 15 s later if nothing keeps it alive. This closes the "armed but not yet connected" window
  fast — R1's own arming broadcast was not enough on its own; catching the real race needed a plug to
  land while a P2P group was up and poking, not merely after `ACTION_START_WIRELESS`.
- **R1 ran 12 physical plug/unplug cycles, not the nominal 10.** The first 2 landed with wireless
  already idle (torn down by the auto-disconnect above, informative but not race-exercising) before
  the plug-vs-live-wireless timing converged; the remaining 10 (renumbered 1–10 below) all correctly
  landed on a live wireless attempt. Both idle-wireless plugs are reported separately, not counted
  toward the 10.
- A stray touch fired `MainActivity.cancelBringUp | status pill X pressed` once during a physical
  unplug/replug transition (`23:25:49.832` local capture time), most likely a hand brushing the
  screen — not scripted. Recovered with `ACTION_START_WIRELESS`.
- **USB enumeration on D-POCO appears to stall while an active 5 GHz WiFi Direct + wireless AA video
  session is running.** A dongle plugged in and drawing power (its own Bluetooth link to D-MOTO came
  up) produced zero `USB_DEVICE_ATTACHED` intents and an empty `/sys/bus/usb/devices/` listing (only
  the root hubs) for several minutes while a wireless session streamed; it enumerated within seconds
  of an isolating `ACTION_DISCONNECT` with nothing else changed. Flagged in "Anything the brief did
  not ask about" — it is outside this round's fix, but affects how R1 and R5 needed to be timed.
- **R4's watcher needed two implementation passes.** A `tail -F -n0 file | grep -m1 pattern` pipe was
  measured ~23 s late firing the trigger broadcast relative to the target log line — a tooling
  artifact of this environment, not app behavior (by the time it fired, the USB session had already
  reached SSL 20+ s earlier). Replaced with a tight `sleep 0.05` polling loop against the growing
  capture file, tracking a line-count baseline before each try; this fired 150–450 ms after the target
  line, in the range round 2 measured for the fastest achievable broadcast delivery. Try 1 under the
  broken watcher is excluded from R4's data below; it was redone clean under the fixed watcher.
- **R5 step 1's dongle retries roughly once every ~60 s (each appearance ~4–8 s), not continuously**,
  when it cannot find its paired phone — see R5 below. This is a dongle hardware behavior, not
  something the app controls.
- Settings backed up to `shared_prefs/settings_backup_round3.xml` (not `.bak` — SharedPreferences
  treats an existing `.bak` as an aborted write and silently reverts to it) before any write, restored
  and read back at the end: `wifi-connection-mode=3`, `use-libusb=false`, matching round 2's own
  restored baseline.
- `native-aa-wireless` / `wifi-launcher-mode` legacy keys: explicit `DEL` sent before every
  `wifi-connection-mode` write per the brief; already absent going in.
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `set_prefs_runas.sh` (all pre-existing, no
  changes).
- D-POCO's wireless adb address was `192.168.1.4:5555`, same as rounds 1–2.

## R0 — Build gate

**PASS**

- SHA: `git rev-parse --short HEAD` → `181e7138`, matches the brief.
- DEX: `ConnectionArbiter`, `ConnectionPriorityPolicy` and `HandshakeMessagePolicy` all present via
  `strings` on the merged `classes*.dex`.
- JVM gate: 2354 tests, 0 failures — exact match to the brief.
- `adb install -r` succeeded on D-POCO; installed APK md5 (`488022dff8a3b7803655cd7cb4142448`)
  matches the built APK md5 exactly.

## R1 — USB over the armed wireless stack, Bluetooth on (the point of the round)

**PASS.**

The 10 plugs that landed on a live wireless attempt (renumbered from the 12 physical cycles — see
Setup notes):

| Plug | Stand-down | SSL | Claim→SSL | Trigger (`8000ms`) | Discard |
|---|---|---|---|---|---|
| 1 | `00:34:02.471` | `00:34:08.362` | 5.89 s | 1 | 2 |
| 2 | `00:35:16.747` | `00:35:19.505` | 2.76 s | 1 | 0 |
| 3 | `00:36:05.639` | `00:36:15.259` | 9.62 s | 1 | 2 |
| 4 | `00:37:23.455` | `00:37:26.867` | 3.41 s | 0 | 1 |
| 5 | `00:37:56.570` | `00:38:05.964` | 9.39 s | 1 | 2 |
| 6 | `00:39:23.669` | `00:39:26.674` | 3.01 s | 1 | 0 |
| 7 | `00:40:09.741` | `00:40:12.435` | 2.69 s | 1 | 0 |
| 8 | `00:40:39.393` | `00:40:41.949` | 2.56 s | 1 | 0 |
| 9 | `00:41:08.856` | `00:41:18.473` | 9.62 s | 1 | 2 |
| 10 | `00:41:44.377` | `00:41:53.885` | 9.51 s | 1 | 2 |

Stand-down line on 10/10. Five plugs (1, 3, 5, 9, 10) hit the slow-dongle
3-attempt case (two `No VERSION_RESPONSE within 2s` timeouts, succeeding on attempt 3, two discarded
late responses each); the rest completed in a single attempt. **Zero** `Initializing WiFi Mode`,
`createGroup SUCCESS`, `Attempting active poke` or `wireless bring-up refused while` lines between any
plug's stand-down and its SSL — the fix held wireless down through every internal USB retry, including
the four slow ones. SSL on 10/10, no replug needed. Zero shape A, zero shape B
(`Failed to read AAP header`), zero `still receiving late VERSION_RESPONSEs at the deadline` lines
across the whole capture.

Wireless back within 15 s of every unplug, all 12 measured (including the 2 idle-wireless plugs
below): 1.537 s–1.570 s, remarkably consistent — matches the fix's `delay(1500)` settle plus overhead.

Coverage: `USB has 8000ms to try again` fired on 9/10 plugs (well past the brief's "at least 3" bar);
5 of those hit the full 3-attempt slow-dongle case.

**Two additional plugs, not counted toward the 10** (idle wireless at claim time, so no stand-down
line — correct per `standDownWireless`'s own contract, "false when it was not running", not a defect):

- `00:21:51.032` → SSL `00:21:54.659` (3.63 s), clean single cycle.
- `00:31:31.207` → SSL `00:31:34.331` (3.12 s), clean single cycle, plugged during the USB-enumeration
  investigation (see Setup notes) and registered once the live wireless session was disconnected.

## R2 — the clean control

**PASS.**

| Plug | Claim | SSL | Claim→SSL | Discard |
|---|---|---|---|---|
| 1 | `00:46:00.185` | `00:46:06.607` | 6.42 s | 2 |
| 2 | `00:46:31.186` | `00:46:33.828` | 2.64 s | 0 |
| 3 | `00:46:56.707` | `00:46:59.406` | 2.70 s | 0 |
| 4 | `00:47:20.869` | `00:47:26.814` | 5.94 s | 2 |
| 5 | `00:47:49.209` | `00:47:51.807` | 2.60 s | 0 |

5/5 plugs SSL, zero shape A/B, a discard on every retried handshake (4 retries, 4 discards), zero
`ConnectionArbiter:` preempt or refused lines.

## R3 — USB preempts a Native AA handshake

**NOT RUN**, per the brief's §8: no second phone is available on this rig to hold a Native AA
handshake while D-MOTO is occupied by the dongle.

## R4 — a user's connection preempts USB

**PASS, 3/3, and the literal `preempts` line fired every time** (round 2 never observed it in 3/3
tries; the difference is watcher speed, not app behavior — see Setup notes).

| Try | Accessory line | `preempts` line | Gap | SSL for preempted USB | Giveback | Resumed w/o replug |
|---|---|---|---|---|---|---|
| 1 (redo) | `00:51:28.407` | `00:51:28.577` | 170 ms | zero | `giving back wireless and USB` `00:51:33.604` | yes, SSL `00:51:35.874` (2.27 s later) |
| 2 | `00:52:54.120` | `00:52:54.293` | 173 ms | zero | `giving back USB` `00:52:59.318` | yes, SSL `00:53:01.523` (671 ms later) |
| 3 | `00:53:35.018` | `00:53:35.184` | 166 ms | zero | `giving back USB` `00:53:40.207` | yes, SSL `00:53:42.395` (656 ms later) |

Try 1's first attempt (under the broken `tail -F` watcher, broadcast landing 23 s late) produced no
`preempts` line because the USB session had already reached SSL by the time the broadcast landed —
excluded from this table as invalid data, not as a candidate finding; redone clean above.

## R5 — a USB plug-in that can never connect

**Step 1: UNTESTABLE for the "60 s budget" line; every individual retry handled correctly.**
**Step 3: PASS.**

D-MOTO was powered off and confirmed unreachable (poke failures logged: `Poke via HFP-AG ... failed:
read failed, socket might closed or timeout, read ret: -1`) before the dongle was plugged.

- **The dongle itself retries roughly once every ~60 s when it cannot find its paired phone, each
  appearance lasting only ~4–8 s, not continuously.** Three separate accessory-mode appearances were
  observed: claims at `01:04:13.522`, `01:05:15.109`, `01:06:18.420` — each ~62–63 s apart. Because the
  arbiter's `usbEpisode` budget only accumulates across retries that land inside the running 8 s quiet
  window, and these arrivals are ~60 s apart (well outside that window), each is treated as a fresh,
  separate episode rather than one continuous 60 s episode. The `USB has tried for 60 s with no
  session; wireless comes back beside it` line therefore cannot fire with this dongle's actual retry
  cadence — a hardware/dongle behavior mismatch with the brief's assumption, not a code defect.
- **Every individual retry the dongle did make was handled correctly:** claim → `... ended with no
  session; USB has 8000ms to try again` → clean giveback of wireless 8–17 s later, every time. Zero
  `userExitedAA is true. Skipping auto-poke` lines anywhere in the capture. One
  `User exit cooldown active for 5000ms` line appears (`01:06:57.049`), traced to an unrelated
  spontaneous wireless session's own user-exit teardown, not to any dongle handshake failure — the
  brief's specific bar (zero cooldown lines *after a failed handshake*) holds.
- A dongle plugged while D-MOTO was briefly power-cycled back on never got a clean enumeration window
  either — the ~8 s live-accessory windows above were the only USB activity across roughly 7 minutes
  of D-MOTO being off, on, then off again.

**Step 3** (D-MOTO powered back on, dongle unplugged, waited 15 s, replugged): clean single-cycle
attach at `01:07:24.298` → accessory at `01:07:24.831` → SSL at `01:07:27.348` (3.05 s), no replug
needed. The brief's literal "a new stand-down line on the replug" was not observed, because wireless
had gone idle again by replug time (the same explainable, non-defect pattern as R1's first two plugs —
`standDownWireless` correctly returns false when nothing is running); the practical signal the step
asks for — a clean, un-stuck reconnect after the dead-end episode — held.

## Anything the brief did not ask about

- **USB enumeration on D-POCO appears to stall entirely while an active 5 GHz WiFi Direct + wireless
  AA video session is live.** A powered, BT-paired dongle produced zero kernel-level USB events
  (`/sys/bus/usb/devices/` showed only root hubs) for several minutes during a live session, then
  enumerated within seconds of an isolating `ACTION_DISCONNECT` with nothing else changed. This sits
  outside `ConnectionArbiter`'s own accounting (which only sees intents Android actually delivers) and
  is worth the author's attention as a separate investigation — it could affect any USB-attach attempt
  on this chipset while wireless is actively streaming, not just this fix's own scenarios.
- The dongle's ~60 s-apart retry cadence (see R5) is worth knowing for anyone designing a future
  USB-retry-budget test on this rig: reaching the 60 s exhaustion line needs either a different dongle
  or a lever to force faster retries, since this one's own hardware timer does not cooperate.

Captures: release `rig-evidence-usb-version-retry`, asset `usb-version-retry-round3-captures.zip`,
sha256 `fd6d5672ec64621ce6395d8fd6e189415156b9edeae2ce409871940ad75de3e6`.
