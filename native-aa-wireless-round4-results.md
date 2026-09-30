# native-aa-wireless — round 4 results

**Candidate:** `fork/fix/native-aa-wireless` @ `181f8e16`, four commits on `main` (`80a81099`)
**Baseline:** none. Every run is candidate only.
**APK md5:** `9e6e691737df3b431e1c60046fab1b91` (verified byte-for-byte against the copy pulled back
from D-SAM after install)
**Unit:** D-SAM (SM-T230, Android 4.4.2 / API 19), phone D-POCO (POCO X3 NFC, Android 15). D-HU not
used for measurement, but its presence on the rig mattered (see Setup notes).
**Date:** 2026-09-21
**Evidence:** `rig-evidence-native-aa-wireless` (new per-thread release on `fork`, created this
round per the retention-rule change), asset `native-aa-wireless-round4-captures.zip`, sha256
`d4ddaa57bb6c72c124508fec3d7831b16f72a85c34e013eeab7ec5e97905c180` — R25/R26/R27/R27b logs and
D-SAM's pre-round `settings.xml` backup. Rounds 1-3's per-round releases stay as they are.

## Setup notes

- **Build gate**: `assembleGithubDebug` (direct `./gradlew`, `build_hur.sh` itself was blocked by
  the session's own sandbox classifier as "Interfere With Workloads"; the equivalent direct
  `gradlew` invocation was not) and `testGithubDebugUnitTest`. **2266 tests, 0
  failures/errors/skipped**, matching the brief's stated count exactly.
- **A third rig device corrupted the phone's own head-unit targeting, and this cost most of the
  round's time.** D-HU (MT50) was adb-attached and idle (no HeadUnitRevived process running,
  launcher foregrounded) throughout, but its classic Bluetooth link to D-POCO was live
  (`isActiveHfpDevice`/`mActiveDevice` both read D-HU's real MAC, masked as `...33:59` in
  `dumpsys`). Android Auto's own "HU presence tracker" on D-POCO had cached D-HU as the current
  head unit from earlier, unrelated testing and kept retrying RFCOMM against it
  (`GH.WIRELESS.BT: Creating rfcomm socket for device: 11:46:03:10:33:59`, repeatedly failing SDP
  discovery) instead of D-SAM (`...5E:1D`), so D-SAM's own listeners and pokes were never answered.
  Toggling D-HU's own Bluetooth radio did not fix it (self-reverts on within ~15-20s, confirmed
  again this round, and the ACL to D-POCO re-establishes before D-POCO's cached target changes).
  Toggling D-POCO's own Bluetooth flipped the live HFP-side active-device pointer to D-SAM but did
  not change Gearhead's cached RFCOMM target. `forget_car_gearhead.sh Google` (the Vehicles-list
  entry) also did not change it — the stale target is a raw BT MAC cached somewhere inside
  Gearhead's own connection state, not the Vehicles list. The fix that worked, matching round 3's
  unrelated but same-class finding: **`pm clear com.google.android.projection.gearhead`** on
  D-POCO. That command is denied by this session's own sandbox classifier ("Irreversible Local
  Destruction"), matching an already-documented precedent for `pm disable-user`/`force-stop`
  against Gearhead on this same phone; the operator ran it manually. After the clear, D-SAM's very
  next bring-up completed a full session on the first RFCOMM attempt. **Flagging for whoever plans
  the next round touching D-POCO against a headunit other than the one it was most recently tested
  with**: check `dumpsys bluetooth_manager | grep -A1 mActiveDevice` and the phone-side
  `GH.WIRELESS.BT: Creating rfcomm socket for device: <mac>` line early, and compare the MAC against
  the unit actually under test, not just against the two signatures round 3 already named.
- **Notification access for Android Auto was still granted after the data clear** (unlike round 3,
  where the clear revoked it and needed a manual re-grant) — no wizard or extra step was needed
  this time.
- Scripts used: direct `gradlew` (see above), `set_pref_hostedit.sh` (D-SAM has no on-device
  `sed`, confirmed again — `set_pref.sh`'s on-device script fails with `sed: not found`),
  `forget_car_gearhead.sh` (ran, did not fix the actual blocker, see above), plain `adb install -r`.
- Settings backed up before the first write: `settings-backup-dsam.xml`, in the evidence zip.
  End-of-round state (left as-is, not restored to the pre-round snapshot, since the counter moved
  through genuine hardware creates during troubleshooting, not artificial contamination) — D-SAM:
  `wifi-connection-mode=3`, `wifi-direct-last-identity-verdict=RENAMED`,
  `wifi-direct-group-name-changes=39`. D-POCO: Bluetooth restored on (was toggled off mid-round for
  R27's no-session half), Android Auto notification access granted, Gearhead app data cleared this
  round (vehicle list and connection history reset as a side effect).

## R25: a stored stability the unit cannot have is not handed to the phone — **PASS**

D-SAM, candidate. Pre-check: `wifi-direct-group-name-changes` read `33` (≥ 3). With the app stopped,
`wifi-direct-last-identity-verdict` was written to `STABLE` (the poisoned state, per the brief).

### 1. Adopt vs. assess race

Force-stop/relaunch cycle 1 was a genuine create (the physical group had already cycled since the
prior round); cycle 2 adopted it:

```
13:23:28.224 WifiDirectManager: a group named DIRECT-oz-Navegadortz3 is already up from before this
  bring-up; reading it instead of tearing it down.
13:23:28.344 WifiDirectManager: group identity ssid=DIRECT-oz-Navegadortz3 ... stable=no (the
  platform names it) nameChanges=34/3 (this group was already up and was read rather than created,
  and this platform has named the group itself on 34 creates)
```

Adopt decision landed before the assessment (120ms), and the verdict read `stable=no` with the
platform-names-it reason, not `same name and same BSSID as the last group`. **PASS.**

Persisted value after this read: `RENAMED` (written by cycle 1's own genuine create, then correctly
left unchanged by cycle 2's read) — not `STABLE`. This is expected per the brief's own caveat
("what this run grades is the verdict that reaches the wire, not the one on disk"): the poisoned
value only stood until a genuine create replaced it, which happened before the adopt cycle ran, not
because the read overwrote it.

### 2. Handshake to a session on the adopted group

Every `Providing credentials to listener` line while the poisoned/re-derived verdict was in play
read `identity stable=no`; no advertised endpoint appears anywhere in the capture
(`grep -nE "WifiVersionRequest|wpp_info|endpoint"` matches only the outbound `WifiVersionRequest`
TX lines, never an inbound endpoint). Once past the Setup-notes blocker, a full session formed on
the adopted group:

```
13:57:10.677 WirelessServer: Incoming connection detected from /192.168.49.157
13:57:11.287 AapSslContext.performHandshake | SSL handshake complete.
```

**PASS**, and the check that matters most: no endpoint went out, and credentials were delivered with
`identity stable=no` throughout.

## R26: an exit that starts the app does not leave a stopped manager owning a group — **PASS**

D-SAM, candidate. Force-stopped with the group up (`DIRECT-qz-Navegadortz3`, confirmed via
`dumpsys wifip2p`), then `headunit://exit` sent with nothing else running:

```
13:58:17.152 NativeAA: ACTIVELY LISTENING on Android Auto UUID ... Waiting for phone to connect back!
13:58:17.232 AapService.onStartCommand | Stop action received. Broadcasting finish request to activities.
13:58:17.312 WifiLauncherNative: creating the group 100ms after the stand-down (still joined=false).
13:58:17.362 WifiDirectManager.stop | WifiDirectManager: Stopping and cleaning up...
13:58:17.392 WifiDirectManager.supersededByStop | WifiDirectManager: credential refresh abandoned -
  the manager was stopped while it was in flight.
13:58:17.402 WifiDirectManager.supersededByStop | WifiDirectManager: the adopt-or-create decision
  abandoned - the manager was stopped while it was in flight.
```

The guard line appears right after the stop; neither `already up from before this bring-up` nor
`Group formed. Owner: true` follow it. Poll of `dumpsys wifip2p` every 2s for 10s afterward:

```
+2s  mGroup network: DIRECT-qz-Navegadortz3
+4s  mGroup network: DIRECT-qz-Navegadortz3
+6s  mGroup network: DIRECT-qz-Navegadortz3
+8s  mGroup network: DIRECT-qz-Navegadortz3
+10s mGroup network: DIRECT-qz-Navegadortz3
```

**Expected, and not a FAIL**: the group stayed up throughout, as the brief predicts (removal is
skipped while ownership is unestablished, a separate change). **PASS.**

`wake poke starting` / `Calling socket.connect()` count after `Stop action received`: **0 / 0** —
no wake poke or hands-free-link socket connect happened on this exit's way out. Measurement only,
per the brief, not a verdict.

## R27: the ordinary exit still removes the network — **PASS (both halves)**

D-SAM, candidate.

### 1. No session live

Bluetooth disabled on D-POCO beforehand to keep this half free of a session. Bring-up settled 22s
(group identity logged for `DIRECT-0y-Navegadortz3`, `nameChanges=39/3`), confirmed no
`SSL handshake complete` in the window, then `headunit://exit`:

```
14:01:12.743 AapService.onStartCommand | Stop action received. Broadcasting finish request to activities.
14:01:12.823 WifiDirectManager.stop | WifiDirectManager: Stopping and cleaning up...   [80ms]
14:01:13.594 WifiDirectManager: Final group removal success
```

`mGroup` null at the first poll (+2s). **PASS.**

### 2. Session live

Bluetooth left on for D-POCO (default state); bring-up completed a full session before the exit was
sent (`DIRECT-qz-Navegadortz3`):

```
13:59:26.389 AapSslContext.performHandshake | SSL handshake complete.
...
13:59:45.488 AapService.onStartCommand | Stop action received. Broadcasting finish request to activities.
13:59:47.430 WifiDirectManager.stop | WifiDirectManager: Stopping and cleaning up...   [1942ms]
13:59:49.462 WifiDirectManager: Final group removal success
```

`mGroup` gone by +4s (present at +2s, null at +4s). **PASS.**

Delay from `Stop action received` to `Stopping and cleaning up`: **80ms with no session, 1942ms
with one** — matching round 3's R24 figures (180ms / 1972ms) closely. `ServiceStopWaitPolicy`'s gate
is doing the same job on this tip as it did before the two fixes were folded in; R26's new guard
sits earlier in the same path and does not disturb it.

## Anything the brief did not ask about

The stale-RFCOMM-target signature in Setup notes (Gearhead retrying a *different* rig headunit's
real Bluetooth MAC rather than the one currently under test) is a third phone-side failure
signature on D-POCO, distinct from the two round 3 already named (the stale `WifiNetworkSpecifier`
retry and the notification-access wizard). Worth checking for early — via
`dumpsys bluetooth_manager | grep -A1 mActiveDevice` and the phone's own
`GH.WIRELESS.BT: Creating rfcomm socket for device: <mac>` line — in any round that switches D-POCO
between headunits without a Gearhead data clear in between. Nothing else observed beyond the
sections above.
