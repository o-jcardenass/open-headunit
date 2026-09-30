# mic-uplink — round 3 results

**Candidate:** `fork/fix/mic-uplink` @ `dcef0500`       **Baseline:** none this round (see brief §0)
**APK md5:** `c4e0975860b7ceaa4895f7767c338e20`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14; phone: Motorola edge 30 neo, Android Auto (Gearhead)
**Date:** 2026-08-25

## Setup notes

- The Motorola was briefly unbonded and re-paired by the user right before this round started (an
  aside unrelated to this round's own work); confirmed bonded again (`A0:46:5A:97:E4:95`) before R0.
- `hur-wifi-test-scripts/` inventory used: `build_hur.sh` (R0 build), `run_unit_tests.sh` (R0 gate),
  `set_hu_prefs.sh` (multi-key settings writes, no relaunch until told — used instead of
  `set_hu_pref.sh` per the brief's own instruction), `restore_settings.sh` (existing, used via the
  pushed-script pattern for the final restore, since inline `run-as sh -c 'cp ...'` failed exactly as
  `TESTING-TEMPLATE.md` §7a predicts — `cp: Needs 1 argument`). No new script needed.
- `auto-start-bt-macs`/`auto-start-bt-name` were already correctly pointed at the Motorola (in the
  set, and as the name) carried over from round 2; confirmed before R1, no change needed.
- `settings.xml` delta at round start: `log-level` `2`→`1`(R1/R3/R4)/`0`(R2); `log-source` absent→`0`;
  `use-head-unit-microphone` `false`→`true`; `enable-audio-sink` `false`→`true`. `mic-sample-rate`
  (16000), `mic-input-source` (0), `wifi-connection-mode` (3), `native-ap-transport` (0) were already
  correct. `key-codes` confirmed absent throughout. Restored to the exact pre-round backup at the end,
  verified byte-identical with `diff`.
- **Brief correction, found before R1 could produce anything at all.** R1's literal recipe
  (`am force-stop` immediately before cycling Bluetooth) cannot trigger `AutoStartReceiver` on this
  rig: force-stop puts the app into Android's "stopped" component state, which blocks delivery to
  manifest-registered `BroadcastReceiver`s until the app is explicitly launched again — already
  documented in `TESTING-TEMPLATE.md` §7a, and round 1's own brief for this identical M6a scenario
  already worked around it ("launch the app explicitly, let a session form, then `headunit://exit`
  ... then cycle Bluetooth"). Confirmed directly: cycle 1 run exactly as this round's brief specifies
  produced **zero** `MATCH!` and zero of everything else over the full 90 s window, despite a
  corroborated real `ACL_CONNECTED` firing partway through (seen on this rig's own installed
  Gearhead's `GH.WifiBluetoothRcvr` — this head unit has Gearhead installed on the device itself, not
  only the phone). Discarded that capture (kept as `r1-1-discarded-forcestop-recipe.txt`) and
  substituted round 1's validated recipe for all five cycles: one explicit launch to clear the
  stopped-state, then `headunit://exit` (not force-stop) between cycles. All five cycles ran clean on
  the corrected recipe — see R1 below.
- **Rig quirk, surfaced only because R4 actually reached this code path for the first time on real
  hardware** (round 1/2's M6b never got this far — no `MicrophoneRequest` ever arrived those rounds).
  `adb shell appops set $PKG RECORD_AUDIO ignore` (the package-scoped form `TESTING-TEMPLATE.md` §7a
  documents) does **not** block capture while the service is an active foreground service: two real
  mic captures completed in full, with real non-zero audio, while `appops get` read `ignore` the whole
  time. Root cause: `appops get` also showed a separate `Uid mode: RECORD_AUDIO: foreground` line,
  and that UID-scoped mode governs while the app is genuinely foregrounded, overriding the
  package-scoped `ignore`. The working form is `appops set --uid $PKG RECORD_AUDIO ignore` in addition
  to the package-scoped set — with both applied, all four subsequent requests declined correctly with
  `code -3`. Restored both scopes to `foreground` (the documented pre-round default) at the end,
  verified via `appops get`. Worth folding into `TESTING-TEMPLATE.md` §7a for the next round that
  needs a real app-op denial against an actively-foregrounded service.
- **Tooling snag, not a rig quirk.** Per-cycle `kill %1` to stop each `logcat` capture silently did
  nothing, every time — this harness does not persist shell job-table state (`%1`) across separate
  tool invocations, so each capture process kept running and appending to its own file for the rest of
  the round. Confirmed via `ps aux`: all 8 `adb logcat` processes from the round were still alive when
  finally checked, and were killed explicitly by the PID each start step had printed. This did **not**
  corrupt any reported count below — every count was read immediately after its own cycle's window and
  before the next cycle began, so at read-time each file held only that cycle's own data — but the raw
  capture files themselves now hold trailing data from later cycles/runs past their own window. Only
  the *first* occurrence of each cycle's marker lines (timestamps given below) is that cycle's own;
  later repeats in the same file belong to whatever ran next. Future rounds: stop captures by the
  printed PID, never by `%1`.
- R2's and R4's spoken utterances ("navigate to the nearest petrol station," repeated for R4) were
  provided live by the user at the moment each assistant trigger was sent — no scriptable substitute
  for actual speech exists (`TESTING-TEMPLATE.md` §0).

## R0 — Gate

**PASS.** First-ever compile of `dcef0500`, clean. **836/836** tests, up 3 from round 2's 833 exactly
as predicted; `ForegroundServiceTypePolicyTest` = **8** (up from 5). APK md5
`c4e0975860b7ceaa4895f7767c338e20`, confirmed installed and matching.

## R1 — Five background starts (the point of the round)

**PASS, 5/5 cycles.**

Per cycle, corrected recipe (explicit launch once to clear the stopped-state, then `headunit://exit`
between cycles — not `am force-stop`; see Setup notes), then cycling the head unit's own Bluetooth
adapter:

| Cycle | MATCH! | caught in onCreate | caught in onStartCommand | Starting FGS mic | filled bt address | createGroup SUCCESS | SSL handshake | Foreground after | types |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 1 | 0 | 0 | 0 | 0 | 1 | 1 | true | `0x12` |
| 2 | 1 | 0 | 0 | 0 | 0 | 1 | 1 | true | `0x12` |
| 3 | 1 | 0 | 0 | 0 | 0 | 1 | 1 | true | `0x12` |
| 4 | 1 | 0 | 0 | 0 | 0 | 1 | 1 | true | `0x12` |
| 5 | 1 | 0 | 0 | 0 | 0 | 1 | 1 | true | `0x12` |

Zero `SecurityException` / `ForegroundServiceStartNotAllowedException` across all five cycles — round
1's M6a failure is gone. Every cycle's service reached the foreground, `types=0x12`
(`CONNECTED_DEVICE`|`MEDIA_PLAYBACK`, the base mask) — matching the fix's design: no microphone type
claimed at start. Each cycle's `ACL_CONNECTED` was corroborated on this device's own Gearhead's
`GH.WifiBluetoothRcvr` receiver, a genuine system broadcast, not app-initiated. Discard-rule check:
single `createGroup SUCCESS` and single `p2p-wlan0-N` index per cycle, clean throughout.

MATCH! → SSL handshake complete (first frame), from the first occurrence in each cycle's own capture:

| Cycle | MATCH! → SSL handshake |
|---|---|
| 1 | 6731ms |
| 2 | 6202ms |
| 3 | 6504ms |
| 4 | 10317ms |
| 5 | 10287ms |

`filled in this device's Bluetooth address` never fired in any cycle — consistent with round 1 and
round 2's finding that `BluetoothHelper.getBluetoothMacAddress()` returns empty (not masked) on this
device, the intended leave-blank branch.

**This is the result that unblocks the branch.**

## R2 — The microphone still opens on a background-started session

**PASS.**

One background-started cycle (`MATCH!`=1, `SSL handshake complete`=1, clean, discard-rule clean).
Assistant triggered via route 1 broadcast; the user spoke "navigate to the nearest petrol station."
Gemini's multi-turn behaviour produced three separate mic captures inside one
`Voice Session Notification: START`/`STOP` pair.

All three captures, in order, every time:

```
Mic request: open: true
AapService: claimed the microphone foreground-service type for this capture
AapTransport: mic uplink started (channel MIC, type 0, timestamps in microseconds, 4096B messages)
```

followed by a clean `mic uplink |` summary: 70f/286720B/101%/pk2851, 82f/335872B/101%/pk3782,
24f/98304B/105%/pk2422 — all real, non-zero signal.

The claim line landed after `Mic request: open: true` every single time, as required.

## R3 — The type is released again

**PASS** (free from R2's capture).

`could not drop the microphone foreground-service type`: **0** occurrences anywhere.
`claimed the microphone foreground-service type` count: **3**, against **1** assistant session (1
START/STOP pair) containing those 3 captures — one claim per capture, not one for the whole session,
exactly as the brief predicted. Service confirmed still foreground after the session ended,
`types=0x12` (base mask, microphone type correctly dropped again).

## R4 — The decline path

**PASS**, after a methodology correction (see Setup notes).

First attempt, package-scoped `appops set $PKG RECORD_AUDIO ignore` only: the app-op denial was **not
honoured** — two real mic captures completed in full (72f/294912B/pk2529, 46f/188416B/pk1192,
non-zero throughout) despite `appops get` reading `ignore` the whole time. Not scored as a candidate
defect; the UID-mode root cause is in Setup notes.

Corrected with `appops set --uid $PKG RECORD_AUDIO ignore` added: **4/4** subsequent requests declined
cleanly:

```
Mic request: open: true
MicRecorder: RECORD_AUDIO is granted but this ROM has revoked the microphone app-op; it has to be re-enabled in the system's own privacy settings
Mic request: capture did not start (code -3); telling the phone so rather than leaving it waiting on a stream that will never arrive
```

**Code sent: -3, not -6** — the permission/app-op check runs before the foreground claim exactly as
the brief describes; no `claimed the microphone foreground-service type` line appears on any of the
four declines. Every `Voice Session Notification: START` closed with a `STOP` within 0.7-1.1s. Service
stayed foreground throughout, `types=0x12` unchanged. App-op restored to `foreground` at both scopes
at the end, verified via `appops get`.

Whole-round discard-rule check (R2's and R4's continuous captures, including the two-part app-op
experiment inside R4): single `createGroup SUCCESS`, single `p2p-wlan0-N` index, single
`SSL handshake complete` in each — clean.

## Anything the brief did not ask about

- This rig has Android Auto's own Gearhead installed on the head unit device itself, not only the
  phone — its `GH.WifiBluetoothRcvr` receiver logs the same `ACL_CONNECTED` events on the head unit's
  own logcat, which is what let every R1 cycle's Bluetooth trigger be corroborated as a genuine system
  broadcast without needing phone-side adb access this round.
- The Motorola being briefly unbonded and re-paired right before this round started is noted only for
  continuity with round 2's setup; not a rig or code issue.
