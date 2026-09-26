# hold-aa-rfcomm, round 1 addendum 3 results

**Candidate:** `fix/hold-aa-rfcomm` @ `c3c5a2d8` (unchanged since round 1)       **Baseline:** none (the hold switch is the control, per brief)
**APK md5:** `52b188106e9c495e9dbf0b29f16b20d6` (built fresh this round; matches round 1's candidate byte-for-byte)
**Unit:** Block A — D-POCO (POCO X3 NFC, Android 15) head unit; Block B — D-SAM (SM-T230, Android 4.4.2) head unit
**Date:** 2026-09-26

## Setup notes

- **Arming verb failed on D-POCO.** `send ACTION_START_WIRELESS_SCAN` (the brief's documented arm step) returned `startForegroundService() not allowed due to mAllowStartForeground false` on every run, because the app had just been force-stopped by the settings writer. Substituted a plain `am start -n .../MainActivity` for every run instead, consistent with the standing quirk that D-POCO's `wifi-connection-mode=3` arms Native AA on plain launch. All landmark lines (handshake, SSL, throughput) print identically either way.
- **A1's first attempt was contaminated by a stale logcat buffer**: the capture was started without first clearing the device's logcat, so a leftover `SSL handshake complete` line from a session that pre-dated this round's own settings write produced a false-positive landmark match. Discarded before any run data was taken; re-run cleanly with `logcat -c` first, which is now done before every subsequent run's capture in this round.
- **A1's call was very short** (telecom history: answered 12:41:32.103, ended 12:41:43.634, ~11.5 s) and ended before the step-5 audio-route check or the audible question could be asked. Those two data points are missing for A1 only.
- **The phone role for A2-A4 was swapped mid-round from D-MOTO to a Samsung Galaxy S24+ (`R5CY90XYBED`)**, a rig USB peripheral not previously used as an Android Auto client, at the operator's request (D-MOTO's line has limited call minutes). This is a deviation from the brief's stated `PH=ZY22GC3BM4` for Block A; **only A1 matches the brief's phone exactly.** R5CY90XYBED had never been Bluetooth-paired with D-POCO; it was paired by hand (Settings → Bluetooth on both devices — no adb-scriptable path for pairing exists) before A2. For A2-A4 the call was placed **from D-MOTO into R5CY90XYBED**, so R5CY90XYBED is the phone whose `dumpsys telecom` state was polled and whose Gearhead session was under test; D-MOTO served only as the outside ringing handset. D-MOTO was unplugged from adb partway through setup (operator's choice) and could not be re-verified or Bluetooth-disabled by adb afterward, though its bond with D-POCO remained and `native-poke-all-paired=true` was already in effect; in practice every A2-A4 handshake still targeted the Galaxy S24+, confirmed per-run by the `Handling handshake for` line.
- **R5CY90XYBED's logcat only supports a 5 MiB ring buffer** (`logcat -G 16M` was silently capped to 5 MiB by the device). No evidence of lost lines was observed in the short captures taken.
- **D-POCO's screen was left on a stray Settings screen** after the manual Bluetooth-pairing step, matching the documented quirk that this can silently block Gearhead from answering a poke. Corrected with `KEYCODE_HOME` before Block B; not observed to have affected any run.
- **D-SAM and D-POCO had never been Bluetooth-paired.** Paired by hand before Block B (no adb path exists for pairing).
- **B1's first attempt was contaminated**: the operator toggled D-SAM's Bluetooth off then back on while the run was in progress, producing `WifiDirectManager.recoverNativeGroup | ... the phone has not opened the Android Auto Bluetooth channel since this attempt started` and no completed handshake. Discarded (kept in the evidence zip as `B1_hu_discarded_btcontam.txt` / `B1_ph_discarded_btcontam.txt` for the record, not used for any measurement); B1 was re-run cleanly after confirming D-SAM's Bluetooth was stable.
- **On D-SAM, both B1 and B2 printed `WifiDirectManager: Standard createGroup SUCCESS!` and `NativeAA: Handling handshake for POCO X3 NFC` twice each**, with the phone's own `ConnectionStateCallback` sequence (`1→2→3→4→0`) likewise appearing twice, all in the ~15-20 s before SSL landed. The P2P interface index never bumped (`p2p-wlan0-0` throughout) and only one real SSL handshake occurred (the `AapSslContext`/`AapTransport` I/D line pair prints twice for one event, confirmed against every other run in this round). Read as an intrinsic settle-in retry on this device/build combination, not contamination, since it reproduced identically on both B1 and B2 with no interface cycling — flagged here because it technically matches the discard rule's literal wording for "a second `createGroup SUCCESS`."
- **`WifiDirectManager: operating channel ...` (the brief's G2 line) never printed on D-POCO.** `WifiDirectManager.kt:2236` gates that reflection-based request path to below API 29; D-POCO is Android 15. Group MHz for A1-A4 was read from `dumpsys wifip2p`'s own `frequency:` field instead (identical information, different source).
- **D-SAM's own P2P group frequency was not separately captured** for B1/B2; the brief states D-SAM is 2.4 GHz only regardless (no code path for band selection below API 29), so this doesn't affect any run's grading.
- **The `mark()` helper's own adb round-trip lags the real event, sometimes by several seconds to over 9 s** (e.g. A1's `A1-answered` marker printed 9.4 s after `dumpsys telecom`'s own `Enter SIM_CALL` timestamp for the same event — three sequential `adb shell` invocations, each with its own USB round-trip). Every decisive timestamp in this report is taken from `dumpsys telecom`'s `CallAudioModeStateMachine` history, not from marker print times.
- **A scripted `grep -q "state=ACTIVE"` poll for call-end is unreliable**: that substring also matches stale call-history entries from earlier calls, so it never actually detected a hangup on its own after A1. Hangup was confirmed against `dumpsys telecom`'s own `Enter TONE/HOLDING` history for every run from A2 onward.
- D-SAM's battery read 100% but on its data-only USB port (no separate charging supply was used this round); its clock read within ~10 s of the host's (no correction needed).
- D-SAM's `screen-orientation` was found at `3` (LANDSCAPE_REVERSE), which the standing template flags as visibly worse than `2` for a native-portrait mount. Left as found — this round's verdicts rest on log lines and fps counters, not the picture, and the brief did not ask for this to be changed.
- D-SAM's `logcat` binary does not support `-G` (`Unrecognized Option`) on this Android version; default ring buffer used throughout Block B.
- Recorded as found and left unchanged, both head units: `native-aa-complete-hfp-slc=true`, `stand-down-station-mode=0`. D-POCO's `native-poke-bt-macs` held `A0:46:5A:97:E4:95` (D-MOTO) at the start of the round; D-SAM's was empty. Neither was edited.
- Scripts used: `build_hur.sh` (build), `install_and_launch.sh` (install, `SKIP_BUILD=1`), `set_prefs_runas_host.py` (all settings writes on both D-POCO and D-SAM — works identically on both since it never depends on on-device `sed`). No new script was needed.

## R0 — Identity

`ACTION_QUERY_STATE` on D-POCO and D-SAM both returned `"commit":"c3c5a2d8bbbc"` before any run.

## Block A: D-POCO head unit

### A1 — 2.4 GHz, hold=true (**the point of the round**, phone = D-MOTO, matches brief exactly)

**HELD**

- Settings: `wifi-connection-mode=3`, `wifi-direct-stable-identity=false`, `wifi-direct-band=2`, `native-aa-hold-bluetooth-channel=true`, `log-level=1`.
- G1 precondition: `NativeAA: Handling handshake for motorola edge 30 neo` at 12:38:10.313; zero `WppTcpServer: connection from` before SSL. Met.
- SSL handshake complete: 12:38:18.680. Group frequency (`dumpsys wifip2p`): **2462 MHz**. Station frequency (`dumpsys wifi`): 5500 MHz (D-POCO's own AP association, unrelated to the P2P group).
- Landing line: `WiFi session landed. Holding the Bluetooth channel for the session and answering the phone's pings, as a head unit does.`
- `[HOLD]` ticks: 2 before ring (60 s, 120 s), 2 after hangup (180 s, 240 s) — continuous, 0 pings answered throughout. No `the phone closed the held Bluetooth channel` line.
- Ring (`dumpsys telecom`): RINGING 12:41:19.591 → answered (SIM_CALL) 12:41:32.103 → ended (TONE/HOLDING) 12:41:43.634. Call duration ~11.5 s.
- First `Throughput` line after ring: 29fps/30fps. Throughout ring→hangup+40s: never below 19fps, zero `AapRead: Connection closed`.
- Audio route / audible check: **not captured** — call ended before the answered+10s check could run.
- G7: `Triggering WPP restart` ×1, `Attempting to connect Bluetooth RFCOMM` ×0 (both over the whole run — this is a Bluetooth-formed session, so the single WPP-restart line is not the connection path).
- G5: `ConnectionStateCallback` — **zero hits, confirmed absent** (D-MOTO's Bluetooth stack does not print this string at all; grepped case-insensitively across the whole capture).
- G4: `HFP RX (` in ring window — **zero hits**.

### A2 — 2.4 GHz, hold=false (phone = Galaxy S24+ from here on)

**HELD**

- Settings: `native-aa-hold-bluetooth-channel=false`, band/stable-identity/log-level unchanged from A1.
- G1: `Handling handshake for Galaxy S24+` at 13:35:27.022; zero TCP-route lines before SSL. Met.
- SSL: 13:35:35.511. Group frequency: **2462 MHz**. Station: 5500 MHz.
- Landing line: `WiFi session landed. Releasing the Bluetooth channel, because holding it is turned off in Settings.` No `[HOLD]` lines (expected, hold off).
- Ring: RINGING 13:38:01.535 → answered 13:38:07.731 → ended 13:39:48.246. Call duration ~100 s.
- First `Throughput` after ring: 11fps (transient dip immediately at ring), recovering to 29-30fps within the next interval; never below 5fps at any point in the window, zero `Connection closed`.
- Audio route: `TYPE_EARPIECE`. Audible check: **not heard on head unit** (operator).
- G7: WPP-restart ×1, BT-RFCOMM-attempt ×0.
- G5/G4: not requested for A2 by §9 (verbatim only asked for A1/B1/B2).

### A3 — 5 GHz, hold=true (control: addendum-2-style Bluetooth-formed session)

**HELD**

- G1: `Handling handshake for Galaxy S24+` at 13:42:33.060; zero TCP-route lines before SSL. Met.
- SSL: 13:42:41.727. Group frequency: **5240 MHz**. Station: 5500 MHz (both 5 GHz — no band split this run, unlike A1/A2).
- Landing line: holding, as A1. `[HOLD]` ticks: 2 before ring (60 s, 120 s), 2 after hangup (180 s, 240 s), 0 pings answered throughout.
- Ring: RINGING 13:45:05.108 → answered 13:45:07.883 → ended 13:45:38.276. Call duration ~30 s.
- First `Throughput` after ring: 22fps. Minimum inside the ring→hangup+30s window: **8fps** at 13:45:28 (never below 5fps, zero `Connection closed` — HELD by the brief's rule).
- **Secondary observation, outside the graded window**: throughput continued to degrade for roughly another 40 s after the graded window closed, touching **4fps** twice (13:46:53, 13:46:58) before recovering to 10fps by 13:47:13 — still with zero `Connection closed` throughout. This falls entirely after `-hangup + 30s` so it does not change the verdict, but is a real, reproducible-looking dip worth a future round's attention.
- Audio route: `TYPE_EARPIECE`. Audible check: **not heard on head unit** ("heard on phone").
- G7: WPP-restart ×1, BT-RFCOMM-attempt ×0.

### A4 — 5 GHz, hold=false (lowest priority; run per the user's "full grid" choice)

**HELD**

- G1: `Handling handshake for Galaxy S24+`, two attempts (13:48:05.897, 13:48:07.693) before SSL; zero TCP-route lines before SSL. Met.
- SSL: 13:48:14.734. Group frequency: **5180 MHz**. Station: 5500 MHz.
- Landing line: releasing, as A2. No `[HOLD]` lines.
- Ring: RINGING 13:50:27.430 → answered 13:50:29.852 → ended 13:51:01.525. Call duration ~32 s.
- Throughput throughout ring→hangup+30s: steady 46-54fps, zero `Connection closed`.
- Audio route: `TYPE_EARPIECE`. Audible check: **not heard on head unit**.
- G7: WPP-restart ×2, BT-RFCOMM-attempt ×0.

## Block B: D-SAM head unit, D-POCO phone

### B1 — hold=true (addendum 1 reproduced, instrumented)

**HELD**

- Settings: `wifi-connection-mode=3`, `native-aa-hold-bluetooth-channel=true`, `log-level=1`. (`wifi-direct-band` not applicable — D-SAM is 2.4 GHz only.)
- G1: `Handling handshake for POCO X3 NFC`, two attempts (13:57:32.305, 13:57:46.709) before SSL; zero TCP-route lines before SSL. Met.
- SSL: 13:57:51.924.
- Landing line: `WiFi session landed. Holding the Bluetooth channel for the session and answering the phone's pings, as a head unit does.` `[HOLD]` ticks: 3 before ring (60 s/120 s/180 s), 1 after hangup (240 s). No `the phone closed the held Bluetooth channel` line.
- Ring: RINGING 14:01:30.295 → answered 14:01:34.945 → ended 14:02:25.741. Call duration ~51 s.
- First `Throughput` after ring: 29fps (from a slightly earlier 26fps sample at 14:01:29 overlapping the ring marker). Minimum in-window: **8fps** at 14:01:39 (well above the 5fps collapse line). Zero `Connection closed`.
- Audio route: `TYPE_EARPIECE`. Audible check: **not heard on head unit**.
- G7: WPP-restart ×2, BT-RFCOMM-attempt ×0.
- G5 verbatim (`ConnectionStateCallback`, SSL→end, D-POCO's own log): two identical five-line sequences, both **before SSL landed** (13:57:41-13:57:43 and 13:57:55-13:57:57), matching the double-handshake-attempt pattern above:
  ```
  ConnectionStateCallback: 1 for xx:xx:xx:xx:5e:1d
  ConnectionStateCallback: 2 for xx:xx:xx:xx:5e:1d
  ConnectionStateCallback: 3 for xx:xx:xx:xx:5e:1d
  ConnectionStateCallback: 4 for xx:xx:xx:xx:5e:1d
  ConnectionStateCallback: 0 for xx:xx:xx:xx:5e:1d
  ```
  (repeated once more, 14 s later). **No further `ConnectionStateCallback` line appears anywhere from SSL to end** — none at the ring. Last value before ringing: `0` (disconnected).
- G4 verbatim: **zero `HFP RX (` lines in the ring window** (14:01:30 → 14:02:25).
- Hands-free at the ring: **down** (last `ConnectionStateCallback` before ring was `0`; zero in-window `HFP RX`).

### B2 — hold=false (addendum 1's own "switch off" most likely ran)

**HELD**

- G1: `Handling handshake for POCO X3 NFC`, two attempts before SSL; zero TCP-route lines before SSL. Met.
- SSL: 14:04:07.070.
- Landing line: `WiFi session landed. Releasing the Bluetooth channel, because holding it is turned off in Settings.` No `[HOLD]` lines.
- Ring: RINGING 14:06:31.295 → answered 14:06:35.095 → ended 14:07:03.958. Call duration ~29 s.
- Throughput throughout ring→hangup+30s: rock steady 29-30fps, zero `Connection closed`.
- Audio route: `TYPE_EARPIECE` at answer, switching to `TYPE_SPEAKER` (phone's own loudspeaker) by 14:06:37.962 — never routed to D-SAM. Audible check: **not heard on head unit**.
- G7: WPP-restart ×2, BT-RFCOMM-attempt ×0.
- G5 verbatim: same double five-line `1→2→3→4→0` sequence as B1, both occurrences before SSL (14:03:56-14:03:57, 14:04:10-14:04:12). **Nothing at the ring**; last value before ringing: `0`.
- G4 verbatim: **zero `HFP RX (` lines in the ring window** (14:06:31 → 14:07:04).
- Hands-free at the ring: **down**.

## The grid, per §7

- **A1 HELD, A3 HELD**: the band does **not** decide the outcome on D-POCO's class — neither 2.4 GHz nor 5 GHz reproduced a collapse with the channel held.
- **A1 and A2 both HELD**: 2.4 GHz alone does not reproduce addendum 1's collapse on D-POCO. A3/A4 (5 GHz) also HELD, so the full band × hold grid on D-POCO shows no collapse in any combination.
- **B1 and B2**: both HELD, and hands-free was **down** at the ring in both (not up) — so this round, like addendum 2, did not observe a live stand-in link at a ring. The stand-in-link-at-ring hypothesis remains untested by any run to date; A1 through B2 all show the same shape (transient HFP-AG poke completes early in session setup, `mActiveDevice: null` and no further hands-free activity by the time the phone rings).
- **B2 held with the channel released**, so addendum 1's original proposal ("release on stand-in units") cannot be evaluated against a collapse here either, since nothing collapsed to prevent.
- **Net result: no run in this six-run grid collapsed**, on either head unit, either band, either hold position, with or without a live stand-in hands-free link. This directly fails to reproduce addendum 1's original finding (collapse within 7 s of ringing) under full instrumentation, on the same head unit (D-SAM) and phone (D-POCO) pairing addendum 1 used. Addendum 1's own capture was never published and its switch position, Bluetooth link state, and audio destination were never recorded, so nothing in this round's data explains what addendum 1 saw; it also cannot be ruled out that addendum 1's run differed in some uncontrolled way (a different phone Android/Gearhead build, a transient rig fault, or the TCP-route condition this round explicitly gated against in Block A). A3's post-window fps dip (secondary observation above) is the closest thing to link stress this round produced, and it never became a collapse.

## Anything the brief did not ask about

- The phone-role swap from D-MOTO to a Galaxy S24+ for A2-A4 (Setup notes) is the single largest deviation in this round and should be weighed accordingly: A1 alone is a brief-exact result; A2-A4 measure the same code path on a different, previously-unseen phone.
- A3's post-graded-window fps dip to 4fps (Setup notes / A3) recovered on its own with no session drop; worth a dedicated look if a future round wants to chase link degradation under call load specifically, separate from the collapse question this round answered.
- The double `createGroup SUCCESS` / double `Handling handshake for` / double `ConnectionStateCallback` pattern on D-SAM (both B1 and B2, identically) looks like a real, reproducible characteristic of this head unit/build combination settling into its first connection each session, not noise — worth naming explicitly if a future D-SAM round's discard-rule check needs to tell it apart from genuine contamination (as opposed to this round's B1, where an actual mid-run Bluetooth toggle produced a different, non-recovering signature: no handshake ever completed at all).
- `set_prefs_runas_host.py` worked without modification on D-SAM (Android 4.4.2, no root, no `sed`) as well as D-POCO — worth noting in the scripts inventory as a second confirmed-working device for that script, since the standing template describes D-SAM's settings-writing method as a separate, more manual recipe.

Captures: `hold-aa-rfcomm-round1-addendum3-captures.zip` on release `rig-evidence-hold-aa-rfcomm`, sha256 `efe611c105d47772972ed6525dd69cfe7956113e8b920e67cf1a4d212ef86c94`, both devices' logcats per run (including the two discarded contaminated captures from the first B1 attempt, clearly named).
