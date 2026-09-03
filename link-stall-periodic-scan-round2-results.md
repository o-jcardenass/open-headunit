# Periodic link stall — round 2 results

**Candidate:** `test/p2p-force-2ghz-band` @ `fdb4df27` on `o-jcardenass/open-headunit`, plain commit
on top of `origin/main` @ `a8830caa`. Test-only, never shipped — reinstalled round 1's own
`main` @ `a8830caa` APK on the head unit at the end of the round.
**APK md5:** `249cf9cbb7d2984952e45f0b2e66e43c` (candidate) / `ff80b9dd8f63a67b8d0f59450ad79c26`
(round 1's `main` baseline, reinstalled after)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14. Phone: POCO X3 NFC, Android 11.
**Date:** 2026-08-13

## Setup notes

**`hur-wifi-test-scripts/` inventory used:** `build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh`.
`recv_gaps.py` (round 1) reused unchanged. `restore_settings.sh` (round 1's pushed-script settings
restore pattern) reused unchanged.

**No new brief defects found this round** — round 1's two corrections (log-level=0, and launching
the head unit app before the phone comes back online) both held and were followed as documented;
neither attempt needed a discard this time.

**R4's log carries a genuine double-count worth flagging so it isn't misread.** The phone's own
`WifiService: startScan uid=2000` count came back at 36 rather than 18 when checked after R4,
because the phone's logcat buffer (grown to 16M for R2, per the brief) still held R2's 18 scans
alongside R4's 18 — the phone was never `logcat -c`'d between R2 and R4. R4's own 18 `RIGMARK
COMBINED_SCAN N` markers are the reliable per-run count and match exactly (18 scan markers + 2
hotspot markers = 20 total `RIGMARK` lines in `r4.txt`); the 36 is two rounds' worth of the same
evidence, not a double-fire within one run.

**One session served R1 through R4**, per the brief's own suggestion — formed once for R1 and never
reconnected. The `p2p-wlan0-4` references present in `r1.txt`'s first second are the teardown of an
already-torn-down group from a completely separate earlier session (round 1's own leftover state,
unrelated to this round), not churn within this round; every run's own live traffic used exactly one
interface, `p2p-wlan0-5`, throughout.

## R0 — build gate

**PASS**

- Builds clean (`assembleGithubDebug`), diff against `a8830caa` confirmed as the brief's own
  19-line `WifiDirectManager.kt` change before building
- **244/244 unit tests**, 0 failures, 0 errors (`testGithubDebugUnitTest`)
- APK confirmed live via `md5sum` against the installed path: `249cf9cbb7d2984952e45f0b2e66e43c`

## R1 — does the group come up on 2.4 GHz, and does it hold? (gate + baseline)

**PASS**

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=0`, `resolutionId=2` (720p,
  confirmed camelCase-correct), `log-level=0`
- Radio state: phone airplane mode ON → head unit app launched → group settled 15 s → phone
  airplane mode OFF + `svc wifi enable`
- Discard-rule check: clean, 1 group, 1 handshake (logged twice, 1 ms apart, from two classes), 1
  interface (`p2p-wlan0-5`) used throughout
- Decisive log lines:
  `19:02:29.574 WifiDirectManager: 2.4GHz createGroup SUCCESS! [TEST BUILD]` →
  `19:02:29.693 onGroupInfoAvailable: SSID: DIRECT-ZQ-Navegadortz2, BSSID: 82:b4:8e:df:1a:d6, GO:
  true, IFACE: p2p-wlan0-5, Freq: 2462 MHz (2.4GHz, channel 11)` →
  `19:02:58.378 SSL handshake complete` →
  `19:02:59.088 [ServiceDiscovery] NegotiatedResolution is: 1280x720` →
  `19:03:35.400 AudioDecoder.start: channel=6, sampleRate=48000, ...`
- **`Freq: 2462 MHz` (2.4GHz, channel 11)** — the band request took.
  **`NegotiatedResolution: 1280x720`** — the resolution pin took.
- Measurements (`recv_gaps.py r1.txt`): **31480 RECV lines over 428.4 s. 0 stalls > 1.2 s. Dead
  time 0.0 s = 0.0%. Audio delivered 192.1 kB/s = 100.1% of real time.**

The 2.4 GHz band alone, at 720p, does not degrade this rig's link. This is a genuine clean control
on the reporter's own band, not a repeat of round 1's mistake — the round is valid.

## R2 — phone scanning, on the reporter's band

**FAIL**

- Settings: unchanged from R1 (same live session, reused)
- Discard-rule check: clean, no new group formed
- Decisive log lines: 18/18 `RIGMARK PHONE_SCAN N` markers in the head unit capture at ~10 s
  spacing. **This time the scans were also confirmed directly on the phone's own log** (buffer
  grown to 16M first, per the brief's own fix for round 1's soft spot): 18/18
  `WifiService: startScan uid=2000` lines recovered after the run.
- Measurements (`recv_gaps.py r2.txt`): **17502 RECV lines over 330.2 s. 0 stalls > 1.2 s. Dead
  time 0.0 s = 0.0%. Audio delivered 192.0 kB/s = 100.0% of real time.**

The loop ran, the scans are now confirmed on the phone's own log (round 1's evidentiary gap is
closed), and the profile is unchanged from R1 — the brief's own literal FAIL condition, this time
with no caveat.

## R3 — the phone hosting its own AP, on the reporter's band

**FAIL, with the AP's frequency recorded**

- Setup performed by hand on request, as the brief allows: hotspot on/off toggled by the user,
  set to 2.4 GHz explicitly in the phone's own settings
- **`SoftApInfo` frequency confirmed before the observation window: `frequency= 2462`** — same
  2.4 GHz channel (11) as our own P2P group, closing round 1's R4 gap directly
- Radio state: hotspot on for 6 m 13 s (`HOTSPOT_ON` 19:18:01 → `HOTSPOT_OFF` 19:24:14), idle,
  nothing connected to it
- Discard-rule check: clean — 1 interface (`p2p-wlan0-5`) throughout, 0 additional handshakes
- Measurements (`recv_gaps.py r3.txt`): **28066 RECV lines over 583.9 s. 0 stalls > 1.2 s. Dead
  time 0.0 s = 0.0%. Audio delivered 192.0 kB/s = 100.0% of real time.**

Even with the phone's own AP confirmed on the exact same 2.4 GHz channel as our P2P group for over
six minutes, the session did not so much as stall, let alone drop. This phone/chipset tolerates
same-channel AP-plus-P2P coexistence with no visible cost on this hardware pairing.

## R4 — both at once (bonus, run because R2 and R3 both FAILed)

**FAIL**

- Setup performed by hand: hotspot turned back on (confirmed 2462 MHz again), then the 18-step
  scan loop run inside the hotspot window
- Discard-rule check: clean — 20/20 `RIGMARK` markers (18 `COMBINED_SCAN N` + `HOTSPOT_ON_R4` +
  `HOTSPOT_OFF_R4`), 1 interface (`p2p-wlan0-5`) throughout, 0 additional handshakes
- Measurements (`recv_gaps.py r4.txt`, spanning hotspot-on through both quiet tails):
  **58534 RECV lines over 1220.3 s. 0 stalls > 1.2 s. Dead time 0.0 s = 0.0%. Audio delivered
  191.9 kB/s = 99.9% of real time.**

No threshold effect: even with the phone hosting a same-channel AP *and* scanning every 10 s at the
same time, the profile stayed flat.

## R5 (freebie, from the brief's §6) — Discovery active check on R1

**PASS** — `grep -cE "Discovery active|Discovery failed" r1.txt` → **0**, re-confirming round 1's
R5 on this round's own session.

## Anything the brief did not ask about

- The phone's SoftAP and our own P2P group landed on the *identical* channel (11, 2462 MHz) in
  both R3 and R4 without being asked to — this phone appears to pick the same social channel by
  default rather than avoiding a channel it's already using for something else. That is the
  strongest form of "same band" this round could have produced, and it still didn't move the
  needle.
- Session stability across the whole round is itself worth noting: one Native AA session, formed
  once, survived R1 through R4 (over 25 minutes of wall-clock time including two hotspot cycles and
  36 combined scan commands) without a single reconnect, restart, or forbidden line.

## A refined hypothesis, checked against this round's own data

Raised after the round: does the reporter's head unit have a *saved* WiFi network (a phone hotspot
from earlier setup, a home network) that's in range during his drives? If so, Android's own
`WifiConnectivityManager` can run periodic background scans looking for it while the station WiFi
interface sits disconnected — which it does for the whole of a P2P-only session (R5 confirmed
`Supplicant state: DISCONNECTED` throughout). That would be a periodic scan the app never asks for
and that no `adb`-driven test in either round exercised: R0/R2's `cmd wifi start-scan` is an
explicit, foreground-triggered request through a different API path than the framework's own
autonomous scan-for-a-known-network logic.

This rig already had the precondition the hypothesis needs: `cmd wifi list-networks` on the head
unit lists three saved networks, one of them plausibly a former hotspot config
(`Hotspotcito Chingon`, `Navegadortz`, `Pegue Cdesta`). Yet **R1's clean 6+ minute baseline in both
rounds ran with those saved networks already present** and showed 0 stalls each time — if
`WifiConnectivityManager`'s periodic scan-for-saved-network logic were live during a P2P session on
this rig, R1 should have shown it, and it didn't. That connects to R0's own finding from round 1:
this rig's entire `WifiScanner` service returns `Failed to retrieve wifiscanner` for *any* scan
request the instant a P2P group is up — plausibly the same underlying Android 14 behavior (the
scanning subsystem going unavailable while a P2P group owns the radio) that would suppress the
framework's own autonomous scanning too, not just explicit calls. That is a specific, coherent
reason this rig cannot produce the signal even with the right saved-network precondition in place,
consistent with — and now more specific than — both briefs' standing caveat that Android 14 and the
reporter's Android 8.1 diverge exactly on scan-versus-P2P concurrency.

**Not a round for this rig.** The useful next step is on the reporter's side: whether his unit has
a saved network in range during the affected drives, and whether his Android 8.1 build's
`WifiConnectivityManager`/PNO logs show periodic scan activity while his P2P group is up — 8.1 may
not suppress station-side scanning during P2P the way this rig's 14 build appears to.

## Bottom line

**This round closes the "wrong band" gap round 1 left open, and the result is the same: neither
lever reproduces the reporter's waveform, now confirmed on his own band.** R1 proves the round was
actually valid this time — `Freq: 2462 MHz`, `NegotiatedResolution: 1280x720`, a clean 6+ minute
control with 0 stalls. R2 (phone scan, scans now confirmed on the phone's own log) and R3 (phone
SoftAP, band now confirmed at 2462 MHz, same channel as our group) both FAIL outright, not
INCONCLUSIVE — the mechanism was genuinely tested and did not fire. R4, run because both singly
failed, found no combined threshold effect either. Round 1's honest caveat still applies: a null
result here is a fact about this Android 14 / UNISOC rig and this specific phone, not proof against
the reporter's diagnosis on his Android 8.1 / Spreadtrum unit and a possibly different phone
chipset's WiFi/BT coexistence behavior. Between round 1 and round 2, this rig has now ruled out
band mismatch, phone-scan interference and same-channel AP coexistence as reproducible mechanisms,
on both 5 GHz and 2.4 GHz, singly and combined. What remains untested and unruled-out is the
head-unit-side scan lever (this unit's own `WifiScanner` is broken, both routes, confirmed twice
across two rounds) and anything specific to the reporter's own Spreadtrum/API-27 WiFi/P2P stack
that this Android 14 rig cannot emulate by settings alone — including the refined hypothesis above
(a saved network driving the OS's own periodic background scanning during a P2P-only session),
which this rig has the precondition for but structurally cannot exercise, for the same reason R0's
explicit scans failed. Settling this further needs a capture on the reporter's own hardware, not a
round 3 on this rig.
