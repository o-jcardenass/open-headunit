# status-pill-stuck-after-disconnect — report

**Build:** `main` @ `5ce51c5e6` (versionName 3.4.0-beta3, versionCode 108)
**Units:** D-SAM (Samsung SM-T230, Android 4.4.2) as head unit; D-POCO (POCO X3 NFC) as phone, Google Android Auto (Gearhead)
**Date:** 2026-09-16

## Origin

Reported directly by the user while testing manually, not from a queued brief: after a live Android
Auto session ends, the home screen's status pill keeps showing "Android Auto is starting… / Starting
Android Auto" and nothing progresses. Opening Settings and returning to the home screen makes a
session start correctly. This file is a first-look finding for the coding session to triage, not a
round against a candidate and baseline pair, so it is not paired with a brief.

R1 below is a first pass that scripted the head unit's own Disconnect button and came back clean.
R2 is the user's own live reproduction, captured on request with both devices under logcat, and it
is a genuine FAIL with the exact mechanism traced in code (see R2). The two runs disagree because
they are not the same trigger: R1 ends the session locally (`ACTION_DISCONNECT`); R2 ends it because
the phone itself sent Android Auto's own Bye-bye. That distinction is the finding.

The user also asked directly: should the pill show at all after a disconnection? Left open below for
the coding session to decide; nothing here answers it on its own, though R2 shows that whatever the
answer, the pill must not display information from an attempt that already ended.

## Setup notes

- D-SAM's `settings.xml` at the start of this round already had `wifi-connection-mode=3` (Native AA),
  `auto-start-bt-macs` empty (BT auto-start disabled), and `native-poke-bt-macs` /
  `last-connected-native-mac` = `DC:B7:2E:5E:4E:59`, confirmed via D-SAM's own
  `dumpsys bluetooth_manager` ACL log lines to be D-POCO's real Bluetooth MAC. Nothing was changed
  in settings for this round.
- A live Native AA session between D-SAM and D-POCO was already up when this round started (found
  via `dumpsys activity services` / `dumpsys window` showing `AapProjectionActivity` focused), so R1
  reused it rather than dialing a fresh one.
- R1's disconnect was scripted with the app's own deep link (`headunit://disconnect`), the same
  action `HomeFragment`'s Disconnect button fires (`AapService.ACTION_DISCONNECT`). D-SAM's `am`
  binary (Android 4.4.2) rejects the `-p` package-restrict flag with a `NullPointerException`; drop
  it and pass the URI alone.
- R2 is a second pass, done live at the user's request: both devices were put under filtered logcat
  capture (same commands as R1) and the user reproduced the bug by hand on the actual hardware while
  the capture ran, rather than it being scripted here. This is the trigger the user actually hit, not
  a constructed one.
- Could not reproduce a live Helper-mode (mode 2) or Auto-mode (mode 1) session end-to-end in this
  round: no dedicated Wireless Helper companion app is installed on D-POCO
  (`pm list packages` shows only `com.andrerinas.headunitrevived` and an unrelated APKMirror
  updater), and there was no common WiFi network already bridging the two units to test mode 1's NSD
  path. The "Helper/Auto modes" paragraph under R2 below is a code-level generalization, not a
  hardware confirmation for those two modes specifically, and is reported as such rather than skipped
  or faked.
- Captures in `evidence/status-pill-stuck-after-disconnect/`:
  `dsam.log` / `dpoco.log` (R1), `dsam_live.log` / `dpoco_live.log` (R2) — all
  `stdbuf -oL adb logcat -v threadtime`, D-SAM filtered to `OPENHU:V AndroidRuntime:E '*:S'`, D-POCO
  at `*:I` unfiltered by tag (this Gearhead build's own log lines are obfuscated, tags like
  `aelr`/`akvb`/`GH.*`, so most of it is Bluetooth/WiFi/system context, not an AA-specific signal —
  except for R2, where the phone's own `wpa_supplicant`/`WifiNetworkFactory`/`GH.WIRELESS.SETUP` lines
  turned out to carry the other half of the story; see R2).

## R1 — Native AA (mode 3), user-initiated disconnect

**PASS** — pill hides correctly; no bug reproduced in this mode.

- Settings: `wifi-connection-mode=3` (unchanged, already the unit's live config).
- Fired `am start -a android.intent.action.VIEW -d "headunit://disconnect"` on D-SAM at device-log
  time `20:15:43.147`.
- Decisive log lines (`dsam.log`):
  - `20:15:43.147 AapService.onStartCommand | Disconnect action received.`
  - `20:15:44.759 2.invokeSuspend | AapService: Native AA user exit. Stopping active launcher.`
  - `20:15:44.769 MainActivity.renderNetworkLine | MainActivity: status pill network: none`
  - `20:15:44.789 MainActivity.renderStagePill | MainActivity: status pill step: hidden`
- Screenshots: `00_pre_disconnect.png` (live session before the disconnect), `01_after_disconnect_10s.png`
  and `02_after_disconnect_30s.png` (home screen at +10s and +30s, no pill visible in either),
  `02_ui_30s.xml` (`uiautomator dump` at +30s, no pill node anywhere in the tree).
- Cycling D-POCO's Bluetooth off then on afterward (`03_after_bt_cycle.png`, `svc bluetooth
  disable`/`enable`) did not resurface a pill either: `auto-start-bt-macs` is empty on this unit, so
  the BT-auto-start path that could otherwise re-arm the wireless stack and show a pill never fires.
- Traces cleanly in code: `SessionEndGroupPolicy.decide()` returns `STOP` for a Native-mode user
  exit, which calls `WifiLauncherManager.stop()` with its default sequence `ANY`.
  `WifiLauncherStopSequence.handledAt(LAST)` returns `true` when the receiver is `ANY`, so `stop()`
  reaches its `ConnectionStageTracker.clear()` call and the pill is genuinely gone, not merely
  off-screen.

## R2 — Native AA (mode 3), phone-initiated disconnect (Bye-bye) — HARDWARE CONFIRMED

**FAIL** — this is the user's bug, reproduced live and root-caused down to the exact line.

The trigger is **not** the head unit's own Disconnect button (that is R1, and R1 is clean). It is
Android Auto ending the session **from the phone side** — Gearhead sent a Bye-bye
(`USER_SELECTION`) rather than the app's own `ACTION_DISCONNECT`. That single difference changes
which policy branch runs, and the branch that runs never clears the pill.

Screenshot: `04_live_bug_state.png` — the home screen, pill showing exactly
**"Android Auto is starting… / Starting Android Auto"**, nothing else on screen changing.
`04_ui_live.xml` is the matching `uiautomator dump` (pill node present, `Starting Android Auto`
text confirmed in the tree, unlike R1's dump which had no pill node at all).

Decisive log lines (`dsam_live.log`, device time):
- `20:28:13.389 AapControlService.byebyeRequest | !!! RECEIVED BYEBYE REQUEST FROM PHONE !!! Reason: USER_SELECTION`
- `20:28:13.980 1.emit | AapProjectionActivity: Finishing because state isUserExit=false, isClean=true, killOnDisconnect=false`
- `20:28:14.030 MainActivity.renderStagePill | MainActivity: status pill step: STARTING_PROJECTION` — the stale value re-rendered the instant `HomeFragment` regains the screen; there is no line hiding it afterward anywhere in the rest of the capture.
- `20:28:14.300 2.invokeSuspend | AapService: Native AA session ended; keeping the WIFI_DIRECT network up for the phone's return.`
- `20:28:14.310 NativeAaHandshakeManager.rearmForNextSession | NativeAA: reopening the Android Auto listeners for the phone's return.`
- Credential deliveries, a genuine new poke, a real BT handshake (Type 1/2/3) and a real WiFi join
  attempt all follow (`20:28:14.771` through `20:28:21.928`) — the reconnect machinery is actually
  running underneath the frozen pill, it just never gets shown.
- `20:28:21.928 NativeAaHandshakeManager.logReceivedDetail | NativeAA: [RX] WifiConnectStatus status=WIFI_NETWORK_UNAVAILABLE(-11)` — this particular re-arm attempt also failed to actually reconnect (see below), which is a second, separate problem from the frozen pill.

Root cause, traced to the exact lines:
- `isUserExit=false` (a phone-initiated Bye-bye is not a local user exit) with `activeModeIsNative=true`
  makes `SessionEndGroupPolicy.decide()` return `KEEP_AND_REARM`, not `STOP`. `AapService.onDisconnected()`'s
  `KEEP_AND_REARM` branch calls `launcher?.rearmAfterSessionEnd()` and nothing else — unlike `STOP`,
  it never calls `WifiLauncherManager.stop()`, so `ConnectionStageTracker.clear()` (which only has the
  two call sites already described in R1) is never reached. This is the same clearing gap R1's control
  test exposed for Helper/Auto modes, just reached from a different, and probably far more common,
  direction: **any session end that isn't a local-button user exit on Native AA also falls through it**,
  including every ordinary Bye-bye the phone itself sends when the driver ends the session from Android
  Auto's own UI.
- The stuck pill is not merely "unrefreshed" — it is actively locked. `ConnectionStagePolicy.shouldApply(current, candidate)`
  only allows a `.report()` call through when `candidate.rank >= current.rank`. `STARTING_PROJECTION`
  is rank 90, the highest rank in `ConnectionStage`. `rearmAfterSessionEnd()`'s credential/poke cycle
  reports `ConnectionStage.WAKING_PHONE` (rank 45) through the exact same `onNativeCredentials()` path
  R1 also uses — but every one of those calls is silently dropped by the rank check, because the
  tracker still reads `STARTING_PROJECTION` from the session that just ended. That is exactly why the
  capture shows real handshake progress (Type 1/2/3, a real poke, a real WiFi join attempt) with no
  matching `renderStagePill` line: the `MutableStateFlow` never changes value, so the collector never
  fires again. The only thing that can move it is `ConnectionStageTracker.beginAttempt()`, which is not
  rank-gated — and nothing on the automatic re-arm path calls it, only a fresh `setActive()` does.
- This is also the exact mechanism for "opening Settings and going back correctly starts a session":
  that round trip re-enters `MainActivity`'s launch path, which (confirmed by R2's own first segment,
  `20:27:43.180`, `MainActivity.beginAutoConnect | Auto-connect: begin (Native-AA driver: POCO X3 NFC, ...)`)
  calls `beginAutoConnect()` → `WifiLauncherManager.setActive()` → `ConnectionStageTracker.beginAttempt(ARMED)`,
  which unconditionally overwrites the stuck value. It is not that Settings does anything special; it is
  the only path in this flow that resets the floor instead of trying to report on top of it.
- `R.string.stage_starting_projection` = **"Starting Android Auto"**, and the pill's fixed title line
  is `R.string.android_auto_starting` = **"Android Auto is starting…"** — the exact text on screen in
  `04_live_bug_state.png` and the exact text the user described originally.

**Secondary finding, same capture, different bug:** the automatic re-arm's own reconnect attempt did
not succeed here either. D-POCO's log at the matching moment
(`dpoco_live.log:10983`, `wpa_supplicant: wlan0: CTRL-EVENT-ASSOC-REJECT bssid=e6:58:e7:0e:de:1e
status_code=1 qca_driver_reason=no_bss_found`, followed by `GH.WIRELESS.SETUP: Send WifiConnectStatus,
status=STATUS_WIFI_NETWORK_UNAVAILABLE`) shows the phone rejected its own association attempt because
it could not see the target BSSID in a scan — matching the head unit's `WIFI_NETWORK_UNAVAILABLE(-11)`
exactly. Whether the automatic poke is firing before the phone has had a chance to actually scan for
the (unchanged) P2P group is a separate question from the pill bug and is left for the coding session;
noted here only because it happened in the same capture and might otherwise be mistaken for a symptom
of the pill issue rather than a second, independent fault.

**Generalization, not independently hardware-tested:** the same clearing gap applies verbatim to a
plain Helper (mode 2) or Auto (mode 1) disconnect, where `SessionEndGroupPolicy.decide()` returns
`NONE` and nothing touches the tracker at all — a strictly simpler case than R2 (no re-arm even
attempts to run). Not reproduced live this round; see Setup notes for why.

## Anything the brief did not ask about

- D-SAM's own Bluetooth stack (`BleAutoConnectService`, an OEM component, not app code) was
  independently cycling ACL connect/disconnect with D-POCO's MAC every 15-30s in the hour before this
  round, entirely outside the app (`dumpsys bluetooth_manager`, repeated
  `ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED` for `DC:B7:2E:5E:4E:59`). Harmless on this unit
  since `auto-start-bt-macs` is empty, but on a unit where that setting is populated this looks like
  a second, independent path to the same symptom: `MainActivity.kt` around line 1043 shows the pill
  optimistically (`beginAutoConnect(LAUNCH_SOURCE_BLUETOOTH, ConnectionUiMode.PILL)`) on the
  assumption that "the service has already been told to arm wireless," without confirming the
  service-side arm actually went through (it can be refused by the user-exit cooldown or a MAC not
  matching). Worth the coding session's attention alongside R2, since it would produce the same
  stuck-pill symptom through a different mechanism and might need a different fix.
