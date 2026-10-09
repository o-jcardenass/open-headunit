# native-aa-wireless — round 3 results

**Candidate:** `fork/fix/native-aa-wireless` @ `ec7c84f8`, four commits on `main` (`80a81099`)
**Baseline:** `3e7237d3` (round 2's candidate), R22 only
**APK md5:** candidate `fe257e6cbccdd7bf94abd0eb09c6cf5f` (verified byte-for-byte against the copy
pulled back from D-SAM after install) / baseline `e9c644fa733f6f17f3e3e16d145c6402` (round 2's build,
reused)
**Unit:** D-SAM (SM-T230, Android 4.4.2 / API 19) for R22 and R24, phone D-POCO (POCO X3 NFC,
Android 15). **D-HU** (MT50, Android 14, API 34) for R23, candidate only.
**Date:** 2026-09-21
**Evidence:** `rig-evidence-native-aa-wireless-round3` (release asset on `fork`), sha256
`11bdc28d98415c5600ac392a576e56e5fb24d65b4718871d36acdd0437c5f45e` — R22 baseline and candidate
logs, R23 log, R24 log, a D-SAM screenshot from R24's troubleshooting, and both units' pre-round
`settings.xml` backups.

## Setup notes

- **Build gate run though the brief names none.** `assembleGithubDebug` and `testGithubDebugUnitTest`
  both clean via `build_hur.sh` / `run_unit_tests.sh`. **2264 tests, 0 failures/errors/skipped**,
  summed from `testGithubDebugUnitTest`'s per-suite XML reports — matches the brief's stated count
  exactly.
- **D-SAM's persisted verdict needed a reset between R22's baseline and candidate arms, and this is
  a deviation from the brief's literal instructions.** The physical WiFi Direct group is OS-level and
  survives an `adb install -r` of a different APK; nothing in R22 tears it down between arms. The
  baseline arm is the *buggy* build by design, and it duly wrote `wifi-direct-last-identity-verdict=
  STABLE` to disk (see R22 below) — but that persisted value is shared state, not something scoped to
  the baseline APK, so the candidate's very first cycle inherited it. Because a *read* (as opposed to
  a *create*) is a deliberate passthrough of whatever verdict preceded it
  (`GroupIdentityStabilityPolicy.assess`'s `readNotCreated` branch returns `previousStability`
  unchanged), the candidate's first read faithfully reported the poisoned `STABLE` it was handed
  (`stable=yes`), which is correct behavior given a false starting condition, not a defect. This was
  caught before it was misread as a candidate FAIL by checking `GroupIdentityStabilityPolicy.kt` and
  `WifiDirectManager.kt`'s diff between `3e7237d3` and `ec7c84f8` directly. The fix: with the app
  stopped, `wifi-direct-last-identity-verdict` was written back to `RENAMED` (what the unit's own
  history already supported — `nameChanges` was 21, well past the below-Q measurement threshold)
  before the candidate's three cycles were run, restoring the condition the two arms were meant to
  share without touching the physical group. All three candidate cycles below are from that clean
  starting point.
- **R22's step 3 (handshake to a session) needed real troubleshooting before it produced a signal,
  none of it the candidate's fault.** D-POCO carried two pieces of debris from round 2's own testing:
  a Gearhead `Material3SettingsActivity` (Bluetooth device list) stuck in the foreground from round
  2's `forget_car_gearhead.sh` failure (cleared with `KEYCODE_HOME`, confirmed by a fresh
  `uiautomator dump`), and — the actual blocker — `ConnectivityService` holding a stale
  `WifiNetworkSpecifier` request for `DIRECT-Eo-Navegadortz3`, an SSID from round 2's session that no
  longer existed (the live group had moved through `DIRECT-nY-` → `DIRECT-GW-` → `DIRECT-Aw-` by
  then), confirmed via `dumpsys` logcat lines showing Gearhead releasing and re-requesting that exact
  stale specifier every few seconds. D-POCO's Android Auto vehicle list was already empty (round 2's
  own end state), so `forget_car_gearhead.sh` found nothing to forget and could not have fixed this.
  A `KEYCODE_HOME` dismiss and an `svc wifi disable`/`enable` toggle on D-POCO did not clear the stale
  request. **The operator manually cleared Android Auto (Gearhead)'s app data on D-POCO**, after which
  the next bring-up completed a full session on the first try (`SSL handshake complete` at
  `02:01:58.046`, `WirelessServer: Incoming connection detected from /192.168.49.210` at
  `02:01:57.515`). Clearing app data reset Gearhead's own pairing state; this did not surface as a
  problem for R22 (the transport-level handshake completed regardless), but resurfaced during R24 —
  see below.
- **R24's step 2 hit the same reset's second-order effect: Gearhead's own first-run wizard.** With a
  session expected to form, the WiFi Direct group instead churned every ~15s under a new SSID (`Mg` →
  `WC` → …) with the Bluetooth handshake completing each time but no `WirelessServer` accept ever
  following — matching the operator's direct observation of no projection on D-SAM's screen.
  `dumpsys activity activities` found D-POCO's foreground activity was Gearhead's own
  `TapHeadUnitActivity` (a first-run pairing wizard screen reading "To continue, select Android Auto
  on your vehicle screen"), and a screenshot of D-SAM (`r24_dsam_screen.png`, in the evidence zip)
  confirmed the *other* side of that same wizard was live and correctly rendered there: "Android Auto
  needs you to turn on notification access from your phone." The notification-access permission had
  been revoked by the app-data clear; `adb shell am start -a
  android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` on D-POCO opened directly to that one
  bounded settings screen (no scrolling needed) and found Android Auto already listed under
  "Allowed" by the time it was checked, at which point the operator confirmed the session connected.
  **Net effect for the round:** none of this is a candidate defect — it is accumulated phone-side
  state from testing round 2 and this round back-to-back against the same physical phone without a
  full reset in between, compounded by the data clear needed to get past the first blocker. Flagging
  for whoever plans the next round touching D-POCO: a stale `WifiNetworkSpecifier` retry loop and a
  Gearhead first-run wizard are both now known failure signatures worth checking for early rather
  than assumed to be the candidate.
- **D-HU dropped off `adb` mid-round** (unrelated to any of the above — a physical USB disconnect,
  confirmed by `adb devices` losing it and an `adb kill-server`/`start-server` cycle not bringing it
  back). R24 was run before R23 to use the wait productively; D-HU came back once the operator
  power-cycled it, and R23 ran normally afterward with no lingering effect.
- **Scripts used:** `build_hur.sh`, `run_unit_tests.sh`, plain `adb install -r` (candidate and
  round 2's baseline APK, reused byte-for-byte), `forget_car_gearhead.sh` (found nothing to do, see
  above), `set_prefs_runas_host.py`.
- Settings backed up before the first write: `round-native-aa-wireless-r3/settings-backup-dsam.xml`
  (D-SAM) and `.../settings-backup-dhu.xml` (D-HU), both in the evidence zip.
  End-of-round state — D-SAM: `wifi-connection-mode=3`, `wifi-direct-last-identity-verdict=RENAMED`,
  `wifi-direct-group-name-changes=33`. D-HU: `wifi-connection-mode=3`,
  `wifi-direct-last-identity-verdict=CHANGED`, `wifi-direct-group-name-changes=0` (unchanged from
  pre-round — this unit names its own group, so the below-Q counter never moves on it).
  D-POCO: Bluetooth restored on (was toggled off for R24 step 1), Android Auto vehicle list empty
  (as round 2 left it), notification access granted to Android Auto.

## R22: a group that was only read is not graded as one that came back — **PASS**

D-SAM, phone D-POCO.

Pre-check: with the app stopped, `wifi-direct-group-name-changes` read `20` (≥ 3, the brief's stated
precondition).

### 1. Baseline arm, `3e7237d3` — reproduces the defect, as expected (not a FAIL)

Three force-stop relaunches, no exit between:

```
01:45:45.237 group identity ssid=DIRECT-nY-Navegadortz3 ... stable=no (the platform names it)
  nameChanges=21/3 (this platform names the group itself and has picked a different name on 21
  creates, ...)                                                          [cycle 1 — genuine create]

01:45:56.137 group identity ssid=DIRECT-nY-Navegadortz3 ... stable=yes nameChanges=21/3 (same name
  and same BSSID as the last group)
01:45:56.478 a group named DIRECT-nY-Navegadortz3 is already up from before this bring-up; reading
  it instead of tearing it down.                    [cycle 2 — assessment landed 341ms BEFORE the
                                                       adopt decision: the race]

01:46:24.125 group identity ssid=DIRECT-nY-Navegadortz3 ... stable=yes nameChanges=21/3 (same name
  and same BSSID as the last group)
01:46:24.425 a group named DIRECT-nY-Navegadortz3 is already up from before this bring-up; reading
  it instead of tearing it down.                                          [cycle 3 — same race]
```

Persisted after: `wifi-direct-last-identity-verdict=STABLE`, `wifi-direct-group-name-changes=21`
(unchanged by the STABLE arm, as expected). This is the exact defect the round exists to fix: a unit
whose own history says `RENAMED` was graded `STABLE` from comparing a surviving group to itself.

### 2. Candidate arm, `ec7c84f8` — closes the race

Persisted verdict reset to `RENAMED` first (see Setup notes — undoing the baseline's own
contamination of state the two arms share, not touching the physical group). Three force-stop
relaunches:

```
01:49:59.054 a group named DIRECT-nY-Navegadortz3 is already up from before this bring-up; reading
  it instead of tearing it down.
01:49:59.344 group identity ssid=DIRECT-nY-Navegadortz3 ... stable=no (the platform names it)
  nameChanges=21/3 (this group was already up and was read rather than created, so nothing new was
  measured)                                    [cycle 1 — adopt decision landed 290ms BEFORE the
                                                 assessment: race closed]

01:50:27.632 a group named DIRECT-nY-Navegadortz3 is already up from before this bring-up; reading
  it instead of tearing it down.
01:50:27.902 group identity ssid=DIRECT-nY-Navegadortz3 ... stable=no (the platform names it)
  nameChanges=21/3 (this group was already up and was read rather than created, ...)   [cycle 2]

01:50:55.259 group identity ssid=DIRECT-GW-Navegadortz3 ... stable=no (the platform names it)
  nameChanges=22/3 (this platform names the group itself and has picked a different name on 22
  creates, ...)                          [cycle 3 — the group itself cycled to a new SSID/BSSID
                                           between cycles 2 and 3; a genuine create, correctly
                                           re-measured as RENAMED rather than compared stale]
```

Every `group identity` line reads `stable=no`. Persisted after:
`wifi-direct-last-identity-verdict=RENAMED`, `wifi-direct-group-name-changes=22`. **PASS.**

### 3. Handshake to a session on the candidate's adopted group — **PASS, and the check that matters most**

D-SAM auto-connects unattended; see Setup notes for the troubleshooting this needed before it
produced a signal. Once through:

```
02:01:51.359 [TX] Sending WifiVersionRequest (Type 4) v4.2
02:01:51.359 NativeAA: not advertising WPP over TCP: this unit's Android is too old to name its own
  WiFi Direct group, and the platform has picked a new name every create, so there is nothing for
  the phone to remember. Withholding one does not clear one the phone already has: ...
02:01:54.472 SUCCESS - Providing credentials to listener. SSID=DIRECT-3P-Navegadortz3,
  IP=192.168.49.1, BSSID=E6:58:E7:0E:DE:1E, identity stable=no (the platform names it)
02:01:57.515 WirelessServer: Incoming connection detected from /192.168.49.210
02:01:58.046 AapSslContext.performHandshake | SSL handshake complete.
```

`grep -nE "WifiVersionRequest|wpp_info|endpoint" r22_candidate_capture.txt` matches only the one
withholding line above — no endpoint was ever advertised, and credentials were delivered with
`identity stable=no`. This is the phone-side damage R22 exists to prevent, and it did not happen.

## R23: the same question above the naming API — **PASS**

D-HU, candidate only. Pre-check: `wifi-direct-last-identity-verdict` read `CHANGED` before the round
(not `STABLE`), so the run is measurable per the brief's own INCONCLUSIVE condition.

Initial bring-up (a genuine create — D-HU names its own group above Q):

```
10:42:35.293 group identity ssid=DIRECT-QS-MT50YT610E4GFPSLU persistent=yes (netId 9) asked=persistent
  matchesRequest=yes bssid=32:2E:CB:83:2A:36 stable=no (same name but the BSSID moved from
  5A:F2:2E:67:49:71 to 32:2E:CB:83:2A:36; this unit re-addresses the group on every create)
```

Three force-stop relaunches, no exit between — every one adopted (read) the same surviving group:

```
10:42:45.615 a group named DIRECT-QS-MT50YT610E4GFPSLU is already up from before this bring-up;
  reading it instead of tearing it down.
10:42:45.732 group identity ssid=DIRECT-QS-MT50YT610E4GFPSLU ... stable=no (this group was already
  up and was read rather than created, so nothing new was measured)   [117ms: decision before
                                                                         assessment]

10:43:09.975 a group named DIRECT-QS-MT50YT610E4GFPSLU is already up from before this bring-up;
  reading it instead of tearing it down.
10:43:10.106 group identity ssid=DIRECT-QS-MT50YT610E4GFPSLU ... stable=no (this group was already
  up and was read rather than created, ...)                            [131ms]

10:43:33.494 a group named DIRECT-QS-MT50YT610E4GFPSLU is already up from before this bring-up;
  reading it instead of tearing it down.
10:43:33.601 group identity ssid=DIRECT-QS-MT50YT610E4GFPSLU ... stable=no (this group was already
  up and was read rather than created, ...)                            [107ms]
```

No bring-up that adopted a group logged a verdict it did not already hold — every read carried
`CHANGED` (labeled `stable=no`) forward unchanged. Persisted after: still `CHANGED`,
`wifi-direct-group-name-changes=0` (unmoved, as expected above Q). **PASS.**

## R24: what a deep-link exit does with no session live — **PASS (names the condition; diverges from both the brief's prediction and round 2's R20)**

D-SAM, candidate.

### 1. Exit with no session live

Phone Bluetooth disabled on D-POCO before arming (confirmed `bluetooth_on=0`). Bring-up created a
fresh group (`DIRECT-ep-Navegadortz3`, nameChanges 26→27). Confirmed no session: zero
`SSL handshake complete` lines in the capture from the bring-up onward. `headunit://exit` sent, then
`dumpsys wifip2p` polled every 2s for 10s:

```
02:04:57.401 AutomationActivity: Received intent. Action: VIEW, Data: headunit://exit
02:04:57.431 Stop action received. Broadcasting finish request to activities.
02:04:57.581 WifiDirectManager: Stopping and cleaning up...
02:04:58.302 WifiDirectManager: Final group removal success

poll @+3s  mGroup null
poll @+5s  mGroup null
poll @+8s  mGroup null
poll @+10s mGroup null
poll @+12s mGroup null
```

**The group went away**, with no re-arm racing into the 10s window (no `createGroup` line appears
between the removal and the last poll) — the opposite of the brief's stated "Expected: the group
stays up" for this half, and the opposite of round 2's own R20 finding on this same unit and this
same deep link (there, exit re-armed instantly and the surviving group was never observed torn
down). No re-run was needed to confirm this: the poll window is clean throughout.

### 2. Exit with a session live

Bluetooth re-enabled on D-POCO. Bring-up completed a full session:

```
02:09:31.388 WirelessServer: Incoming connection detected from /192.168.49.156
02:09:32.059 AapSslContext.performHandshake | SSL handshake complete.
```

(Followed by live VIDEO media data in the capture, confirming the session was genuinely running, not
just transport-level.) `headunit://exit` sent against the live session (group `DIRECT-Cj-Navegadortz3`):

```
02:10:38.073 AutomationActivity: Received intent. Action: VIEW, Data: headunit://exit
02:10:38.093 Stop action received. Broadcasting finish request to activities.
02:10:40.045 WifiDirectManager: Stopping and cleaning up...
02:10:40.996 WifiDirectManager: Final group removal success   [2.9s after the exit intent]

poll @+3s  mGroup network: DIRECT-Cj-Navegadortz3
poll @+5s  mGroup null
poll @+7s  mGroup null
poll @+9s  mGroup null
poll @+11s mGroup null
```

The group went away within ~3-5s, matching round 1's R13 figure (~3s) closely. **PASS** per the
brief's own rule ("PASS is either result, as long as both halves are quoted"), but **the round's
premise is not confirmed**: both halves produced the same outcome (group removed) on this build and
unit, which does not support `ServiceStopWaitPolicy.waitsForWirelessTeardown`'s
`sessionConnected && wirelessLauncherActive` gate as the explanation for round 2's R20 divergence —
if that gate controlled whether teardown happens at all (rather than, say, only how long the app
waits before finishing), step 1 (no session) should have left the group up and it did not. Whoever
picks this up next should treat "why did R20 see a re-arm and neither of this round's runs did" as
still open, not answered by this round's evidence in the direction the brief expected.

## Anything the brief did not ask about

The stale `WifiNetworkSpecifier` retry loop and the Gearhead first-run wizard found while
troubleshooting R22/R24 (see Setup notes) are both now-known failure signatures on D-POCO worth
checking for early in the next round that touches this phone, rather than assumed to be a candidate
regression. Nothing else observed beyond the sections above.
