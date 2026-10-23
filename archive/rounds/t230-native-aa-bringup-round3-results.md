# t230-native-aa-bringup — round 3 results

**Candidate:** `fix/audio-sink-and-wireless-bring-up` @ 5bf505d7a (same build as round 2, not
rebuilt this round — only the capture changed)
**Baseline:** round 2's result — `t230-native-aa-bringup-round2-results.md`
**APK md5:** de16ab3541b35257130de8c07dde67b6 (unchanged from round 2)
**Unit:** Samsung SM-T230 (head unit, same as rounds 1-2) + POCO X3 NFC (`4f4027e9`, Gearhead
17.5.663204, the paired phone) — first round to also capture the phone's own WiFi/Gearhead logs
**Date:** 2026-09-15

## Setup notes

- No rebuild this round — round 2's APK was already installed and correctly configured; only added
  a second, parallel capture on the phone.
- Enabled `adb -s <POCO> shell cmd wifi set-verbose-logging enabled` before capturing (the matching
  `get-verbose-logging` query throws `SecurityException: Uid 2000 does not have access` from the
  shell user, so the enable can't be read back, but the request completed silently and the
  subsequent capture was in fact more detailed than a baseline sample taken just before).
  Real tag names found by sampling the phone's live logcat buffer first (`WifiP2pService`,
  `WifiClientModeImpl`, `wpa_supplicant`, `WifiScoreCard`, `WifiDataStall`, `WifiStaIfaceAidlImpl`)
  plus, unexpectedly, a full family of Gearhead's own connection-setup tags (`GH.CAR`,
  `GH.ConnLoggerV2`, `GH.WifiBluetoothRcvr`, `GH.WifiPreflight`, `GH.WirelessNotify`,
  `GH.WIRELESS.SETUP`, `GH.WPP.CONN`, `CAR.SETUP.WIFI`) — these turned out to carry the decisive
  signal, not the WiFi-stack tags. Worth remembering as the standard filter for any future Gearhead
  wireless-setup capture: the WiFi-stack tags alone would have missed the actual finding.
- Two `adb logcat` processes running in parallel against two different devices, same pattern as
  every other round (`stdbuf -oL` piped to separate files, force-stopped by PID afterward — one
  `pkill -f` attempt hit the wrapper shell first and had to be redone by explicit PID, consistent
  with the standing "never `pkill -f` a pattern matching the shell wrapper" caution).
- **The two devices' clocks are not synchronized** — the head unit's log timestamps read ~12h
  "behind" the phone's (`11:53:01` HU vs `23:52:34` PHONE for events a few seconds apart), and even
  after adjusting for that the two clocks drift by tens of seconds relative to each other. Cross-
  device correlation in this file is by event *ordering and cadence*, not by matching timestamps to
  the millisecond.
- Battery was still 9% (unchanged from rounds 1-2, still not charged). See below — this round makes
  a real case that it isn't purely coincidental.

## R1 — Native AA with synchronized head-unit + phone-side capture

**FAIL**, but this round fully explains round 2's EPIPE signature from the phone's own side.

The phone's Gearhead logs name the exact thing that's failing: a TCP socket it calls the **"GAL"
socket** (the actual `192.168.49.1:5288` data connection to the head unit — the same socket the app
calls `SocketProjectionConnection`). Every one of round 2's 13 EPIPE/`link_lost` cycles has an exact
phone-side counterpart:

```
GH.ConnLoggerV2: ... GEARHEAD_PROJECTION_ENABLED           (SDP done, AAP session starting)
GH.ConnLoggerV2: ... PROJECTION_WINDOW_MANAGER_STARTING    (Gearhead about to show the UI)
GH.CAR: Stopping session: <id>
GH.WIRELESS.SETUP: Received GAL socket result, callback=N, result=FailedUnexpectedly
GH.ConnLoggerV2: ... WIRELESS_GAL_SOCKET_FAILED_UNEXPECTEDLY / _DISCONNECTED_UNEXPECTEDLY
GH.ConnLoggerV2: ... FRAMER_READ_IO_EXCEPTION
GH.ConnLoggerV2: ... FRAMER_READ_IO_EXCEPTION_SOCKET_CLOSED
GH.WIRELESS.SETUP: Attempting to stop.
```

Counts across the ~80s capture: `WIRELESS_GAL_SOCKET_FAILED_UNEXPECTEDLY` x13,
`WIRELESS_GAL_SOCKET_DISCONNECTED_UNEXPECTEDLY` x12, `FRAMER_READ_IO_EXCEPTION` x22,
`GEARHEAD_PROJECTION_ENABLED` x13, `PROJECTION_WINDOW_MANAGER_STARTING` x11, `Stopping session` x13
— matching the head unit's own 12 `session state disconnected (link_lost)` and 26
`SSL handshake complete` in the same window (one extra HU handshake at the very start/end of the
capture window, off-by-one from where each side's capture was cut).

**Neither side's own error is more specific than "the socket read/wrote and got a low-level I/O
failure."** The head unit's break this round showed as an EOF on read (`AapReadSingleMessage.doRead
| Connection closed (EOF)`, not the write-side EPIPE round 2 hit — same underlying event, observed
from whichever side's I/O call happened to notice first) — confirming it's one shared TCP-connection
death, not two independent bugs on each side. Neither app logs an errno or a TCP-level reason (RST
vs. a stalled/timed-out read); that would need a packet capture, not app logs, and is out of scope
for this round.

**Gearhead does not retry the TCP socket directly — it restarts the whole Bluetooth/WPP handshake
from scratch** each time (`WIRELESS.SETUP: State changed to RFCOMM_READ_WRITE_FAILURE` /
`"Retrying connection attempt on all channels by restarting WPP"`), which is why the visible cycle
on the head-unit side looks like a full poke→handshake→negotiate→break loop rather than a quiet TCP
reconnect: it *is* a full restart, driven by the phone.

**Two supporting signals point toward radio degradation, not protocol logic, as the actual cause:**
- `WifiDataStall`'s `tx tput in kbps` reading declines steadily across the capture: 47025 → 32175 →
  19800 → 15840 → 9405, alongside `TxTrafficHigh: true, RxTrafficHigh: true` right at each failure —
  real payload traffic was flowing, not an idle link timing out.
- `WifiScoreCard`'s `RSSI` on the same association degrades over the same window: -24 → -25 → -26 →
  -31 → -32. Still a technically strong signal throughout (close-range test), but a consistent
  downward trend across a single unbroken WiFi association (`Supplicant state: COMPLETED` the whole
  time, no drop to `DISCONNECTED` in `WifiScoreCard`) is unusual for a stationary short-range test
  and is consistent with — though not proof of — a weakening transmit signal from the tablet's own
  radio, which the tablet's 9% and non-charging battery is a plausible cause of.

The frequency stayed 2412MHz (2.4GHz, channel 1) the whole round — round 1's DFS/5GHz hypothesis is
now dead; this device's driver picked 2.4GHz both times it's been observed, and the failure happens
on 2.4GHz regardless.

## Anything the brief did not ask about

- This closes the "what does the phone see" question from round 2's own recommendation. The answer
  is: the phone's TCP-layer read simply fails with no more specific reason logged at the app level,
  restarting the whole BT handshake each time rather than just reopening the socket — which is itself
  worth knowing (it means even a one-off transient drop costs a full ~5-6s handshake round trip, not
  a quick reconnect).
- The declining RSSI/throughput trend across one continuous, never-disconnected WiFi association is
  the most actionable new lead: it points at signal degradation over the test, not a one-shot
  protocol bug. Re-running charged, and ideally with a wired power supply so the radio isn't under
  any battery-management influence, is now the most likely single change to either fix this or rule
  it out for good.
- If a charged re-run still shows the same ~5-6s EPIPE cadence, the next lever is a `tcpdump` on the
  phone (`adb shell tcpdump` if available, or `nethogs`-style capture) during one cycle, since neither
  app's own logs name a TCP-level reason for the break.
