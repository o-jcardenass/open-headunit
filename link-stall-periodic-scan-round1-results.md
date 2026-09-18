# Periodic link stall — round 1 results

**Build:** `origin/main` @ `a8830caa`. No candidate branch, no A/B.
**APK md5:** `ff80b9dd8f63a67b8d0f59450ad79c26`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14. Phone: POCO X3 NFC, Android 11.
**Date:** 2026-08-13

## Setup notes

**`hur-wifi-test-scripts/` inventory used:** `build_hur.sh` (build), `set_hu_prefs.sh` (multi-key
settings write, one relaunch). No script fit the round's own measurement need, so `recv_gaps.py`
was saved into `hur-wifi-test-scripts/` exactly as the brief specified, verbatim from the brief.

**The brief's own §3/§4 are wrong about which log level carries `RECV:`, and this cost the first
attempt at R1 entirely.** `log-level=1` (DEBUG) was written per the brief's table and produced
**zero** `RECV: ` lines over a full live session with a completed SSL handshake and
`AudioDecoder.start` firing — kept as `r1-INVALID-debug-loglevel.txt` as direct proof. Reading
`AapMessageIncoming.kt:50` shows the actual guard is `if (AppLog.LOG_VERBOSE)`, not
`LOG_DEBUG` — the log *call* is `AppLog.d(...)` (DEBUG priority), but visibility requires
`log-level=0` (VERBOSE) specifically, regardless of priority. The brief conflates the two. Every
run from R1 onward used **`log-level=0`**, not the `1` the brief's §3 table states. Flag this for
whoever writes the next brief in this thread.

**A force-stopped app's manifest broadcast receivers do not fire, at all, until the app is
explicitly relaunched** — not previously documented in the template. After writing settings the
app is force-stopped (§1's own instruction); an attempt to skip the explicit relaunch and let the
phone's Bluetooth reconnect trigger `AutoStartReceiver` on its own produced nothing for two full
minutes with the phone's Bluetooth confirmed `state: ON` and idle the whole time — `dumpsys
activity services` showed no `AapService` running throughout. The moment `MainActivity` was
launched explicitly, the session formed normally. Any future round that considers skipping the
explicit launch step should expect this.

**`cmd connectivity airplane-mode enable|disable` works on this phone**, unlike the
`am broadcast -a android.intent.action.AIRPLANE_MODE` route the template's §7a already documented
as refused. It reliably drops Bluetooth and (usually) WiFi; re-enabling did not reliably bring the
station WiFi radio back on its own, so `svc wifi enable` was run explicitly after every
airplane-mode-off in this round, matching the disable→re-enable nudge already in §7a for a
different trigger.

**R1 needed two discarded attempts before a clean capture**, both kept for the record:

- `r1-INVALID-debug-loglevel.txt` — the log-level bug above.
- `r1-INVALID-groupchurn.txt` — bringing the phone's radios back up *before* launching the head
  unit app let the phone's own Bluetooth reconnect (which matches `AutoStartReceiver`) race an
  explicit `am start` a few seconds later, producing two `createGroup SUCCESS`-adjacent sessions
  (2 SSL handshakes, two different `p2p-wlan0-N` interfaces) — a genuine discard-rule hit, not a
  borderline call. Fixed by reverting to the order the clean-run protocol already specifies:
  launch the head unit app first, while the phone is still in airplane mode, let the group settle
  15 s, **then** bring the phone back. Every run from the corrected R1 onward reused that same one
  session without a fresh connect, so this ordering issue only had to be solved once.

The final R1 capture does contain one `p2p-wlan0-1` reference and one `MATCH! Starting AapService`
line; both were traced by timestamp to the *teardown of the discarded prior attempt* landing in the
first second of the new capture, not to this run's own session — the live session used exactly one
interface (`p2p-wlan0-2`) throughout, confirmed by `grep -n` across the whole file. Reported
precisely rather than silently discarding a third time on a false positive.

**R3's phone-side scan-firing evidence went stale before it was checked.** R0 already confirmed
`cmd wifi start-scan` on this phone produces a clean `WifiService: startScan uid=2000` with real
scan results and no error. R3 issued the same command 18 times (all exit=0, all 18 `RIGMARK`
markers landed in the head unit's capture), but a direct phone-side logcat re-check of R3's own
window came back empty — the phone's own ring buffer (not grown to 16M the way the head unit's
was) had already wrapped past that window by the time it was checked. The FAIL verdict below rests
on R0's already-established reliability of the command plus the 18/18 issued markers, not on a
fresh per-run log check.

## R0 — which scan levers exist here

**PASS** (per the brief's own condition: at least one lever works on at least one device).

- **Phone: works cleanly.** `cmd wifi start-scan` → exit=0, `list-scan-results` returned real
  APs (4+ SSIDs with RSSI/frequency), confirmed by `I/WifiService: startScan uid=2000` with no
  error line.
- **Head unit: accepted but inert**, reproduced twice. `cmd wifi start-scan` → exit=0,
  `list-scan-results` → "No scan results" both times. Decisive line both times:
  `E/WifiScanRequestProxy: Failed to retrieve wifiscanner` immediately after
  `I/WifiService: startScan uid=0`. The fallback lever (`WIFI_SETTINGS` screen) hits the same
  broken backend: opening it produced `W/WifiService: Attempt to retrieve WifiConfiguration with
  invalid scanResult List` and two sibling warnings (passpoint, OsuProviders), zero actual scan
  results, over an 8 s window while the screen had focus. This head unit's `WifiScanner` service
  itself does not function, by either route — likely a consequence of the radio already being
  committed to hosting the Native AA P2P group, though that wasn't isolated further this round.

Carried forward: **R2 has no working lever on this rig and is INCONCLUSIVE by the brief's own
stop condition**; R3's phone lever is confirmed reliable.

## R1 — baseline: clean session, 6 minutes untouched

**PASS**

- Settings written: `wifi-connection-mode=3` (int), `native-ap-transport=0` (int),
  `log-level=0` (int, corrected — see Setup notes)
- Radio state: phone airplane mode ON → head unit launched, group settled 15 s → phone airplane
  mode OFF + `svc wifi enable`
- Discard-rule check: clean after the ordering fix (see Setup notes) — 1 group, 1 real handshake
  (logged twice, 1 ms apart, from two classes — not two handshakes), 1 interface used
  (`p2p-wlan0-2`) throughout the live session
- Decisive log lines: `17:38:44.898 WifiDirectManager: 5GHz createGroup SUCCESS!` →
  `17:39:15.826 WirelessServer: Incoming connection detected from /192.168.49.88` →
  `17:39:16.022 AapSslContext.performHandshake: SSL handshake complete` →
  `17:39:59.219 AudioDecoder.start: channel=6, sampleRate=48000, ...`
- Measurements (`recv_gaps.py r1.txt`): **32309 RECV lines over 432.1 s. 0 stalls > 1.2 s. Dead
  time 0.0 s = 0.0%. Audio delivered 192.1 kB/s = 100.0% of real time.**

This rig has a clean control baseline: no repeating stall pattern with nothing interfering, unlike
the reporter's own unit. Every subsequent run in this round is measured against this profile.

## R2 — head unit scanning during live session

**INCONCLUSIVE** — per the brief's own stated condition, since R0 found no working scan lever for
the head unit (both `start-scan` and its `WIFI_SETTINGS` fallback are inert on this device, same
root cause). No device time spent trying to force a nonexistent lever, per the brief's own
instruction not to invent a substitute.

## R3 — phone scanning during live session

**FAIL**

- Settings: unchanged from R1 (same live session, reused)
- Discard-rule check: clean, same session as R1, no new group formed
- Decisive log lines: 18/18 `RIGMARK PHONE_SCAN N` markers landed in the head unit capture at
  ~10 s spacing; `cmd wifi start-scan` returned exit=0 on the phone all 18 times
- Measurements (`recv_gaps.py r3.txt`): **20585 RECV lines over 333.7 s. 0 stalls > 1.2 s. Dead
  time 0.0 s = 0.0%. Audio delivered 192.0 kB/s = 100.0% of real time.**

The loop ran (18/18 markers, all commands accepted, R0 already established this exact command
produces real scans on this phone) and the RECV gap profile is byte-for-byte unchanged from R1 —
this is the brief's own literal FAIL condition. A phone-initiated WiFi scan does not perturb this
rig's inbound AAP link at all.

## R4 — phone hosting its own access point while it is our P2P client

**FAIL**

- Setup performed by hand, as the brief explicitly allows: the phone's mobile hotspot was turned
  on and off by the user on request, nothing scripted or scrolled via adb for this step
- Settings: unchanged from R1 (same live session, reused)
- Radio state: phone mobile hotspot on for 5 m 39 s (`HOTSPOT_ON` 17:54:02.529 →
  `HOTSPOT_OFF` 17:59:41.901), idle, nothing connected to it
- Discard-rule check: clean — 1 interface (`p2p-wlan0-2`) throughout, 0 additional handshakes,
  0 `MATCH! Starting AapService`
- Measurements (`recv_gaps.py r4.txt`): **24362 RECV lines over 503.3 s. 0 stalls > 1.2 s. Dead
  time 0.0 s = 0.0%. Audio delivered 192.0 kB/s = 100.0% of real time.**

The session survived the entire hotspot-on window without so much as a stall, let alone the
session dropping (which the brief said would count as a PASS-with-a-note). This phone tolerates
running its own SoftAP while joined to our P2P group with no visible effect on the link — the
opposite of the reporter's configuration on this specific hardware pairing.

## R5 — confirm the app is not the one scanning

**PASS**

- `grep -cE "Discovery active|Discovery failed" r1.txt` → **0**
- `grep -c "This unit is connected to another WiFi network while hosting" r1.txt` → **0**
- `grep -o "p2p-wlan0-[0-9]*" r1.txt | sort -u` → **`p2p-wlan0-1`, `p2p-wlan0-2`** — but
  `p2p-wlan0-1` is exclusively the teardown of the prior discarded attempt landing in the first
  second of this capture (see Setup notes); the live session used exactly one interface
  (`p2p-wlan0-2`), confirmed by direct inspection of every occurrence
- `dumpsys wifi | grep -iE "mWifiInfo|SSID|Supplicant"`: `Supplicant state: DISCONNECTED`,
  `SSID: <unknown ssid>` — the head unit's own station radio is not associated during the session,
  matching what the reporter's own unit showed

`WifiDirectManager.discoveryRunnable` did not fire on this rig's Native AA session, consistent
with the code reading in the brief's §2 — this is the cheap negative check the analysis rests on,
and it holds.

## Anything the brief did not ask about

- The two brief defects found in Setup notes (the `log-level` guard mismatch, and the
  force-stopped-app-blocks-receivers behavior) are both worth folding into the standing template
  for future rounds, not just this thread.
- R0's finding that this head unit's own `WifiScanner` service is broken (not merely "no
  privilege") is itself possibly worth a one-line note somewhere if a future round ever needs a
  head-unit-side scan for an unrelated investigation — it will not work here by any route tried.

## Bottom line

**R1's own baseline is clean** (0 stalls in 432 s of real audio traffic), so this rig is a valid
control, not itself a silent reproduction of the reporter's fault. **Neither of the two runs the
round exists for reproduced his waveform**: a phone-initiated scan (R3) and the phone hosting its
own SoftAP while joined to the P2P group (R4) both left the RECV gap profile identical to the
clean baseline — zero stalls, 100% real-time audio delivery, across a combined ~14 minutes of
active interference. R0 confirms the phone-side scan lever is real and working (so R3's negative
is not an artifact of a broken command), and R5 confirms the app itself is not the source of any
periodic behavior on this rig's Native AA path. Per the brief's own §2 framing, **this is a fact
about this rig, not about the reporter's unit** — Android 14 here may simply defer, refuse, or
otherwise decouple connectivity scanning from an active P2P group in a way his Android 8.1 does
not, and the phone-hosting-a-SoftAP configuration (R4) is a different phone/chipset entirely.
**Only R2 came back INCONCLUSIVE**; R3 and R4, the two runs the round exists for, both came back
FAIL rather than INCONCLUSIVE — the mechanism was actually tested here and did not reproduce, not
merely untestable. The diagnosis is not refuted (per the brief's own caveat, a null result here
doesn't clear his unit), but this rig cannot supply positive evidence for it either; settling it
now needs a capture on the reporter's own hardware, or a rig running a WiFi/P2P stack closer to
Android 8.1.
