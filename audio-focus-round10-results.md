# Audio focus — round 10 results

**Candidate:** `fix/744-call-audio-hfp` @ `d64d7802`, stacked on `fix/audio-focus-pauses-bt-source`
@ `26032e65`       **Baseline:** none (control is a one-key pref write on the candidate, per brief)
**APK md5:** `9e0f9dee4259ccbd150bf485cbf37316`, confirmed identical to round 9 (R0 md5 gate PASS,
no rebuild)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, phone POCO X3 NFC
**Date:** 2026-08-09

## Setup notes

- The round was started via a background subagent, which correctly identified and refused to work
  around a Claude Code permission-classifier block on placing an actual phone call (a self-call
  attempted as a low-risk substitute for a human dialing in). That block worked as intended — no
  call was ever placed, no real number was reached. However the subagent's process then died
  silently partway through R3's setup (its transcript stopped updating at 13:52:44 with no error,
  and it dropped off the live-agent list) while a background wait was still pending. Nothing
  supervised the rig for the following ~68 minutes. R2 is unaffected: it started and concluded
  cleanly (13:43:27–13:49:38) well before the subagent died. R3 was not so lucky — see below. Per
  a standing instruction from this point on, hardware rounds with a fully-scripted brief are run
  directly by the coordinating session rather than delegated to a background agent.

- **R3's core premise does not hold on this rig, and this cost the round two wasted attempts before
  the reason became clear.** The brief's "poke forever" recipe assumes phone Wi-Fi off prevents any
  Android Auto session from forming. On this phone it does not: `svc wifi disable` correctly
  disables the STA interface (confirmed via `dumpsys wifi` before each attempt), but Wi-Fi Direct/
  P2P is not gated by it, and a full session forms anyway.
  - **Attempt 1** (via the now-dead subagent): Wi-Fi confirmed off at setup, session had fully
    formed and was streaming video by the time it was discovered ~68 minutes later, with the
    phone's Wi-Fi later found back in the *enabled* state (state observed after the fact, so
    whether it flipped back before or after the session formed is not established).
  - **Attempt 2** (this session, direct adb `svc wifi disable`): confirmed off at launch (15:04:24);
    by t=35s a real session had already formed (`AapProjectionActivity` resumed) and the phone's
    Wi-Fi was back to *enabled*.
  - **Attempt 3** (after the user manually toggled Wi-Fi off via the phone's own UI, confirmed off
    via adb): still formed a full session within 35s, `AapProjectionActivity` resumed, all while
    `dumpsys wifi` continued reporting Wi-Fi disabled throughout. This rules out "the toggle didn't
    actually take" as the explanation — the STA radio was genuinely off and a session formed
    anyway.
  - Conclusion: on this phone, disabling Wi-Fi from either adb or the UI does not reliably prevent
    Wi-Fi Direct/P2P from completing a join. **No method available to this round could hold the
    "no session can ever form" precondition for 20 minutes.** R3 is UNTESTABLE on this rig as
    designed; see §7a addition below. R2, which needs no such precondition (it runs against a live
    HFP-AG poke loop with the *lack* of a session only used to keep the poke retrying — the
    causal evidence R2 found does not depend on the loop running any particular length of time),
    is unaffected by this limitation.

- Attempt 1's 68-minute unsupervised session left a 812 MB logcat capture (`r3-round10.txt`) and
  pushed the head unit's `/proc/loadavg` to ~15 from sustained video-decode load — high but the
  device did not hard-reboot (CPU was mostly idle by the time it was checked, the load average was
  a decaying artifact of the finished decode work, not an active spin loop). Deleted from the
  results directory after this write-up; not committed.

- Scripts used: `set_hu_prefs.sh` (round 9) for all multi-key settings writes, no new scripts
  needed. A small one-off sampling script (`monitor_r3.sh`) was used for the direct-session R3
  retries; not kept, since R3 could not be completed as designed and the script has no reuse value
  without a working "session-proof" recipe.

- The recovery method that worked for R2's aftermath (cycle the head unit's own Bluetooth adapter
  off/on) did not reliably recover the link after R3's contamination episodes — it took a *second*
  cycle plus a phone-side Bluetooth off/on before `HeadsetClientService` came back to `Connected`.
  Noted in §7a.

## R0 — installed-APK gate

**PASS.** `md5sum` on the installed APK returned `9e0f9dee4259ccbd150bf485cbf37316`, identical to
round 9's build. No rebuild, no unit test re-run, per the brief.

## R1 — baseline: calls work here with no HUR at all

**PASS.** App confirmed not running (`am force-stop`, no `headunit` process in `ps -A`). With the
app fully stopped, `HeadsetClientService` showed `curState=Connected` — the head unit's own
hands-free link to the phone is up on its own, nothing from this app involved. (The brief's
"place a call" sub-step needs a live human and was not run — see below; the link-up baseline alone
is what R2 and R3 needed to be meaningfully comparable against, and that part is established.)

## R2 — sustained poking at the defaults — **the headline result of this investigation**

**FAIL — hypothesis confirmed.** Sustained default-mode poking (HFP-AG, `bluetooth-wake-mode`
absent) breaks the head unit's own hands-free link to the phone.

- Settings written: `wifi-connection-mode=3`, `bluetooth-wake-mode` absent (default), `log-level=1`
- Radio state: phone Wi-Fi off (`svc wifi disable`, confirmed via `dumpsys wifi`), phone Bluetooth
  on. App launched at 13:43:27.
- Poke loop confirmed firing within the first 35 s (2 rounds by t=35s in the equivalent recipe
  check; steady state ~30-31 s cadence, not the brief's rougher "~15 s" estimate — 15 s hold +
  ~15-16 s gap between rounds).
- **13 total poke rounds** over ~6 minutes (13:43:29.893 → 13:49:38.327), **all 13 succeeded**
  connecting to HFP-AG (0 failures) — notably different from round 9, where every HFP-AG and
  HSP-AG poke attempt failed to connect at all.
- **Decisive log lines, quoted with timestamps** — the very first poke round:
  ```
  08-09 13:43:29.893 NativeAA: Calling socket.connect() for POCO X3 NFC via HFP-AG (0000111f-...)...
  08-09 13:43:30.181 D/BluetoothHeadsetClientServiceJni: connection_state_cb: state 0 peer_feat 0 chld_feat 0
  08-09 13:43:30.184 V/BroadcastQueue: Enqueuing BroadcastRecord{... android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED ...}
  08-09 13:43:30.185 NativeAA: Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms...
  08-09 13:43:30.186 D/HfpClientConnService: Disconnecting from XX:XX:XX:XX:4E:59
  08-09 13:43:30.186 D/HfpClientDeviceBlock.XX:XX:XX:XX:4E:59: Resetting state for device XX:XX:XX:XX:4E:59
  08-09 13:43:30.188 I/Telecom: CallsManager: Sending phone-account ... com.android.bluetooth.hfpclient.HfpClientConnectionService ... unregistered intent ...
  08-09 13:43:30.205 E/HeadsetClientStateMachine: Bad currentState: ...HeadsetClientStateMachine$Disconnected@47c2b2
  ```
  This is not a timing correlation — it is the OS's own stock `HfpClientConnectionService` logging
  its *own* disconnect from the same peer device, 4 ms after our poke's `socket.connect()` call
  succeeds against that peer's HFP-AG record, 1 ms before our own "Successfully poked" line is
  even written. The head unit's own hands-free client tears itself down because the poke opened a
  second, competing RFCOMM connection to the same SDP record on the same device.
- The link **did not self-recover**: still `curState=Disconnected` when checked at 13:50:42 (60 s
  after the app was force-stopped and poking ended) and again at 13:51:32 (~8 minutes after the
  drop). Bond remained intact throughout (`POCO X3 NFC` still listed under Bonded devices on the
  head unit).
- Recovery required manual intervention: cycling the head unit's own Bluetooth adapter off/on
  (`svc bluetooth disable`, ~14 s self-revert to on per the known rig quirk, then a further ~5-10 s)
  brought `HeadsetClientService` back to `curState=Connected` with a fresh state-machine instance
  (`total records=37`, vs. hundreds accumulated pre-drop) — no bond loss, no UI pairing step
  needed, unlike round 9's unrelated bond-loss finding.
- The call-audio-routing sub-check (place a call while the poke loop is still running, see where
  audio comes out) needs a live human and was not run this round — R2's headline result (the link
  breaking) does not depend on it, and is conclusive on its own.

R2 is stopped early relative to the brief's "20 minutes or drop, whichever comes first" — the drop
happened in the first poke round, so continuing to poke a link that was already down for another
14 minutes would have added confirmation samples, not new information. 13 rounds against 1 drop
event is already an unambiguous result.

## R3 — the same, HSP-AG only

**UNTESTABLE.** Cannot be run as designed on this rig: see Setup notes. The precondition ("no
session can ever form so the poke retries indefinitely") could not be held for more than ~35
seconds across three independent attempts (adb-only twice, adb after a manual UI Wi-Fi toggle
once), because Wi-Fi Direct/P2P on this phone is not gated by the Wi-Fi station-mode toggle. Since
R2 already showed damage, R3 was meant to run as the causal control — that control cannot currently
be built on this hardware.

One observation from the aborted attempts, offered without weight since the session contaminated
the run: in the one retry that got as far as three poke rounds before a session formed, all three
HSP-AG pokes *succeeded* connecting (`Successfully poked POCO X3 NFC via HSP-AG`) — unlike round 9,
where HSP-AG failed to connect every time. Poke connectivity on this rig appears to vary between
sessions rather than being a fixed characteristic of the SDP record, which is worth keeping in mind
if a future round revisits either poke target's reliability.

## R4 — the stub hands-free record, over a long window

**PASS (no accept).** `HFP connection accepted` does not appear in any capture from this round: not
in R2's ~6-minute window, and not in any of the three R3 attempts (~35 s to ~68 minutes each). No
phone ever attached to HUR's own stub hands-free record across any of this round's sessions.

## Anything the brief did not ask about

- **The `NativeHandoffPolicy.shouldPoke` gap that motivated this round is real and matters, but
  round 9's R3 was still directionally right — R2 here shows the poke damages the link even in the
  *narrower* window round 9 accidentally tested (one round, session forming 12 s later). Round 9's
  R5 found one round of failed HFP-AG attempts did not visibly break anything in the following
  session; round 10's R2 shows repeated *successful* HFP-AG connects will. The difference was not
  "one round vs. many" as much as "attempts failing vs. attempts succeeding" — this round's pokes
  connected every time (0/13 failures) where round 9's failed every time. That may itself be the
  more important variable: a poke that fails to connect cannot preempt the OS's own HFP client
  connection, only one that succeeds can.
- **Wi-Fi Direct/P2P is not gated by the phone's Wi-Fi toggle on this phone/Android version.** This
  is a rig/phone-level finding, not an app finding, and it invalidates any future brief that assumes
  "phone Wi-Fi off" is sufficient to suppress session formation for an extended, unsupervised
  window. Folded into `TESTING-TEMPLATE.md` §7a below.
- **Link recovery after a bad disconnect is not always a single Bluetooth-adapter cycle.** R2's drop
  recovered on the first head-unit-side cycle; the R3 contamination episodes needed a second
  head-unit cycle plus a phone-side Bluetooth off/on before `HeadsetClientService` returned to
  `Connected`. Also folded into §7a.
