# wpp-endpoint-depoison — round 4 results

**Candidate:** `fork/fix/wpp-endpoint-depoison` @ `0847fd38` ("only a P2P interface can answer what a
group's address is"), with the rig's own `6b4eb0d9d` ("read a surviving group instead of always
tearing it down") cherry-picked onto it as `736a0582` (the candidate tip; `6b4eb0d9d` itself is not
an ancestor since it was rebased, not merged)
**Baseline:** `main` @ `80a81099` (no baseline APK; this thread has no A/B)
**APK md5:** `34ecfa3b2dfdbeef60efe7d05450a3d0`
**Unit:** D-HU (UNISOC MT50, Android 14). **Phone:** D-POCO (POCO X3 NFC), Gearhead
`17.8.163804-release.daily`
**Date:** 2026-09-19

## Setup notes

- **The round could not get past R1, and not for a reason R0-R0a checked.** The phone came into this
  round already carrying the exact poisoned WPP-over-TCP endpoint round 3 documented
  (`ssid=DIRECT-IP-MT50YT610E4GFPSLU, bssid=4A:07:A4:F0:AC:CF, 192.168.49.1:5299`), **despite** this
  round's own step 0: `forget_car_gearhead.sh Google` confirmed `Accepted vehicles: None` before the
  reboot, and a full `adb reboot` was run and waited out to `sys.boot_completed=1`. The first
  `GH.WPP.TCP: Trying to start WPP on TCP with configuration: ...` line for that stale entry appears
  at `15:17:58.108`, twelve seconds into this round's very first head-unit launch and long before R1's
  own force-stop/relaunch cycle (`15:19:42`) had a chance to poison anything. That corrects last
  round's addendum (`21f579739`), which concluded "only forgetting the head unit clears it" from a
  force-stop test alone: **forgetting the head unit and a full reboot together did not clear it
  either.** A live re-check of Accepted vehicles at `15:29` (mid-round, after ~12 minutes of failed
  retries) still read `None`, which means the stored WPP endpoint is not carried in the "vehicle"
  record the Vehicles UI manages at all, and survives whatever a forget-and-reboot does clear. See R1
  below for the full evidence chain.
- **Corollary: R1 as scripted (poison the phone with a fresh, this-round entry, then observe it
  survive a force-stop) could not be exercised**, because the phone was already saturated with a
  stale entry that consumes every attempt at a wireless AA session before this round's own credential
  handshake ever runs. R2 through R6 either depend on R1 completing (R2, R3) or on the phone reaching
  a working session at all (R5), so those did not run. R4, which needs no phone action, ran clean and
  is reported in full below. R6 is graded from the group-identity lines R0a, R1 and R4 did produce.
- **`am force-stop com.google.android.projection.gearhead` is denied by this session's sandbox
  classifier** (`[Interfere With Workloads]`) on D-POCO — a known rig limitation, not attempted
  around. The phone was left with Gearhead's stuck session still retrying; an operator can force-stop
  or reboot it by hand. The classifier did not block `svc bluetooth disable/enable`, `pm`-free UI
  navigation via `uiautomator`, or reading `Accepted vehicles`, all of which were used instead.
- **The phone's screen locked itself partway through R1's wait** (idle timeout; not touched on
  purpose). `forget_car_gearhead.sh`'s `uiautomator dump` failed once with "could not find node"
  because the top of the tree was the lock screen, not Settings — `input keyevent KEYCODE_WAKEUP`
  plus one swipe-up cleared it (no PIN configured) and the script's normal navigation resumed from
  there. Note this for any future round that leaves the phone idle for several minutes: a
  `uiautomator` failure is worth checking against the lock screen before assuming the app moved.
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh` (`SKIP_BUILD=1` after the
  first build), `set_pref.sh` (six settings), `forget_car_gearhead.sh`. No script was added or
  changed. The five stale identity keys were cleared with a small one-off on-device `sed` script
  pushed the same way `set_pref.sh` does (not saved back into the shared directory, since it is a
  one-time-per-round action, not a repeatable lever).
- **The candidate branch needed assembling.** `fix/wpp-endpoint-depoison` (this checkout) and
  `fork/fix/wpp-endpoint-depoison` had diverged by one commit each (`6b4eb0d9d` locally,
  `0847fd38` on the fork) since both were added independently after the same parent, `8b3e15f3`. Per
  the brief's own candidate line, `6b4eb0d9d` was cherry-picked onto `fork/fix/wpp-endpoint-depoison`
  as `736a0582`, cleanly, no conflicts.
- **R0a's literal first `Initial BSSID from` line is not about this round's own group.** Read in
  strict launch order, the very first delivery on the very first launch resolves a **different,
  already-persisted P2P group** (`DIRECT-RB-Navegadortz2`, `netId 14`, `asked=nothing (not this app's
  create)`) via `NetworkInterface.hardwareAddress: 00:00:00:00:00:00`, which is masked, so it falls
  through to `source=access point setting (stand-in)`. This is a leftover from an unrelated earlier
  session (`dumpsys wifip2p` lists seven persisted groups on this unit, several named
  `...-Navegadortz2`, matching R5's hotspot SSID) and is not "up" — `stable=unproven ... belongs to
  another interface, so it says nothing about this group`. It appears exactly once (two log lines,
  each repeated once for a duplicate delivery) at `15:17:46.9xx`, and never again across the whole
  517k-line, ~13-minute capture. **This round's own group** (`DIRECT-01-MT50YT610E4GFPSLU`, `netId
  18`) resolves via `IPv6 link-local` on every single delivery, including through R1's relaunch and
  all ten of R4's cycles, with zero `static override` occurrences anywhere. R0a is graded PASS on that
  basis; see R6 for how the stand-in read is scored there.

## R0 — build gate

**PASS**

- `assembleGithubDebug` and `testGithubDebugUnitTest` both clean via `build_hur.sh` /
  `run_unit_tests.sh`.
- Total: **2192 / 0** (matches the brief's `0847fd38`-alone baseline; `6b4eb0d9d` added no new pure
  policy tests).
- `P2pInterfaceNamePolicyTest`: 5/0. `P2pBssidSourcePolicyTest`: 10/0.
  `GroupIdentityStabilityPolicyTest`: 23/0. `WppTcpServePolicyTest`: 9/0. `WppEndpointPolicyTest`:
  10/0.
- Tip: `736a0582` (`fix/wpp-endpoint-depoison` local candidate branch, contains both `0847fd38` and a
  cherry-picked `6b4eb0d9d`). APK md5 `34ecfa3b2dfdbeef60efe7d05450a3d0`.

## R0a — the address gate

**PASS**

- This round's own group (`DIRECT-01-MT50YT610E4GFPSLU`) resolves via `IPv6 link-local` on its first
  delivery:
  ```
  15:17:47.750 WifiDirectManager: BSSID read from the IPv6 link-local address of p2p-wlan0-1 (EUI-64): 26:E2:6D:4E:57:18
  15:17:47.751 WifiDirectManager: Initial BSSID from IPv6 link-local: 26:E2:6D:4E:57:18
  15:17:47.756 WifiDirectManager: group identity ssid=DIRECT-01-MT50YT610E4GFPSLU persistent=yes (netId 18) asked=persistent matchesRequest=yes bssid=26:E2:6D:4E:57:18 stable=unproven (first group under this name; the next one decides) source=IPv6 link-local
  ```
- `source=` agrees between the `onGroupInfoAvailable` line and the `group identity` line on every
  occurrence for this group, through R1 and all of R4. See Setup notes for the one, unrelated,
  first-launch-only exception (a different, non-live persisted group).

## R1 — poison the phone

**INCONCLUSIVE** — the phone never landed on this round's own credentials; the leftover *this
round's own step 1-2-3 created* was read correctly (steps 1-3 below all matched the brief exactly),
but step 4 could not be confirmed because a **different, older** poisoned entry from round 3
monopolized every wireless AA attempt for the rest of the round.

Steps 1-3, as scripted, all matched:

1. Launch. Group formed as `DIRECT-01-MT50YT610E4GFPSLU`, `stable=unproven ... first group under this
   name`, BSSID `26:E2:6D:4E:57:18` (quoted under R0a above).
2. `am force-stop com.andrerinas.headunitrevived`. Group survived:
   ```
   mWifiP2pInfo groupFormed: true isGroupOwner: true groupOwnerAddress: /192.168.49.1
   mGroup network: DIRECT-01-MT50YT610E4GFPSLU
   ```
3. Relaunch, `15:19:42.926`-`15:19:43.259`:
   ```
   15:19:43.135 WifiDirectManager: a group named DIRECT-01-MT50YT610E4GFPSLU is already up from before this bring-up; reading it instead of tearing it down.
   15:19:43.259 WifiDirectManager: group identity ssid=DIRECT-01-MT50YT610E4GFPSLU persistent=yes (netId 18) asked=nothing (not this app's create) bssid=26:E2:6D:4E:57:18 stable=yes (same name and same BSSID as the last group) source=IPv6 link-local
   ```

Step 4 (phone stores **this round's** SSID/BSSID) could not be reached. Instead, from the very first
launch onward, the phone repeatedly dialled a **stale, unrelated** entry:

```
15:17:58.108 GH.WPP.TCP: Trying to start WPP on TCP with configuration: WifiProjectionProtocolOnTcpConfiguration(wifiConfiguration=WifiConfiguration(ssid=DIRECT-IP-MT50YT610E4GFPSLU, bssid=4A:07:A4:F0:AC:CF, ...), ipAddress=192.168.49.1, port=5299)
```

This is the exact SSID/BSSID round 3's addendum documented as poisoned, on a group name
(`DIRECT-IP-...`) this round never created. It repeated 14 times in the saved capture (last at
`15:26:48.792`) with 42 `WIFI_CONNECTED_TO_WRONG_SSID` events between them, all under the **same**
Gearhead connectivity session id (`d973a4f9-af1b-4352-8d31-d5d40859bad8`) that opened before this
round did anything. A live re-check outside the saved capture, at `15:30:21` (~13.5 minutes into that
same session, event #1021), showed it still retrying the identical stale SSID/BSSID with no sign of
recovering on its own.

Two interventions were tried, neither broke it:

- `svc bluetooth disable` / `enable` on the phone (`15:23:19`) did drop the head unit's existing HFP
  client link (`HeadsetClientStateMachine curState=Disconnected` at `15:21:13.777`, read via
  `dumpsys`) and freed the head unit's poke, which fired and succeeded twice
  (`Successfully poked POCO X3 NFC via HSP-AG`, `... via HFP-AG`, `15:23:23`-`15:23:54`). The phone's
  stuck WiFi retry loop continued unaffected through and after both pokes.
- `am force-stop com.google.android.projection.gearhead` was attempted to end the loop cleanly for
  teardown and was denied by this session's sandbox classifier (`[Interfere With Workloads]`) — see
  Setup notes.

`Accepted vehicles: None` was confirmed both before the round's reboot and again at `15:29`, so the
stale endpoint is not stored in the record `forget_car_gearhead.sh` clears.

**This is the round's central finding**, and it revises round 3's addendum: on Gearhead
`17.8.163804-release.daily`, the stored WPP-over-TCP endpoint outlives a force-stop (round 3's
finding) *and* a "forget vehicle + full reboot" cycle (this round's finding) — the round 4 brief's
own prescribed recovery. Whatever store holds it is not reachable from the Vehicles UI at all. This
round did not identify what does clear it; the next thing to try is `pm clear` on Gearhead, which was
not attempted here because it would also erase the Android Auto developer "Start head unit server"
toggle another thread ([[project_dpoco_selfmode_gearhead_server]]) depends on this phone keeping —
that is worth a deliberate decision by whoever picks this up, not something to absorb into this round.

## R2, R3, R5 — not reached

R2 needs a phone that accepts this round's fresh credentials (R1's step 4); R3 is graded from R2's
own window; R5 needs the phone to open an Android Auto Bluetooth channel at all. None of those held
given R1's finding above. Not attempted, per the brief's own guidance not to force a run past a
blocking finding.

## R4 — the group reuse

**PASS**

Five `headunit://exit` + relaunch cycles, then five `am force-stop` + relaunch cycles, no phone
action in either set.

Exit cycles (new BSSID every time, `stable=no`, all `source=IPv6 link-local`):

| Cycle | BSSID | stable |
|---|---|---|
| 1 | `E6:50:68:13:92:11` | no (moved from `26:E2:6D:4E:57:18`) |
| 2 | `62:4A:08:A3:30:49` | no (moved from `E6:50:68:13:92:11`) |
| 3 | `12:24:3A:84:CC:C5` | no (moved from `62:4A:08:A3:30:49`) |
| 4 | `C6:E1:6F:A4:EA:39` | no (moved from `12:24:3A:84:CC:C5`) |
| 5 | `52:3A:28:ED:A7:E0` | no (moved from `C6:E1:6F:A4:EA:39`) |
| (extra launch before force-stop set) | `32:0D:A8:1E:4B:87` | no (moved from `52:3A:28:ED:A7:E0`) |

Force-stop cycles (same BSSID `32:0D:A8:1E:4B:87` read every time, `stable=yes`,
`asked=nothing (not this app's create)`, all `source=IPv6 link-local`):

```
15:26:07.604  group identity ssid=DIRECT-01-MT50YT610E4GFPSLU ... bssid=32:0D:A8:1E:4B:87 stable=yes (same name and same BSSID as the last group) source=IPv6 link-local
15:26:17.777  (identical, cycle 2)
15:26:28.013  (identical, cycle 3)
15:26:38.222  (identical, cycle 4)
15:26:48.385  (identical, cycle 5)
```

- `grep -cE "PROV_DISC|prov_disc|stuck retry"` over the whole capture: **0**.
- Reads happened on every force-stop cycle, creates on every exit cycle, matching the PASS criterion
  exactly.
- Phone rejoin / time-to-SSL-handshake: not gradable on any cycle. The phone was independently
  occupied with R1's stale-endpoint loop for this entire window (its own log carries 633 `GH.*` lines
  across the same ~2m20s span), never idle enough to attempt joining any of these groups, so no cycle
  here says anything about join timing.

## R6 — the setting split

**PASS**

- `source=static override`: **0** occurrences anywhere in the capture, with `static-bssid` set to
  `00:27:15:43:06:6a` throughout R0a, R1 and both halves of R4.
- `source=access point setting (stand-in)`: exactly the one first-launch occurrence documented under
  R0a and Setup notes, for a different, non-live, non-this-app's-create group. Every group-info line
  for this round's own group (`DIRECT-01-MT50YT610E4GFPSLU`) agrees on `source=IPv6 link-local`, with
  no exception, across R0a, R1's relaunch and all ten of R4's cycles.
- `nothing on this device reported the group's own address`: 2 lines, both part of the same one
  first-launch stand-in event above, not repeated.
- Read strictly, the brief's FAIL text ("the access point's address is announced for a WiFi Direct
  group") is literally true once, for a group this round never created or used. Read for what R6 is
  actually gating — whether the address *this round's own group* announces ever comes from the static
  override or the access-point stand-in rather than a real read of the group — it is PASS with no
  exception.

## Anything the brief did not ask about

- **The stale poison predates this round's first launch by design, not by accident of timing.** It
  was already being dialled 12 seconds after the very first launch on freshly-written settings and a
  freshly rebooted phone, which rules out anything this round's own R0a/R1 steps did as the source —
  it is purely a phone-side leftover from round 3, several hours earlier in the same day. Anyone
  picking up round 5 should assume this phone needs a stronger reset than reboot-and-forget before
  trusting any R1-onward result, or should switch phones for this thread.
- `dumpsys wifip2p`'s persisted-group list carries seven entries on this unit
  (`DIRECT-G3/0U/RB/RX-Navegadortz2`, `DIRECT-IP/9B/01-MT50YT610E4GFPSLU`), several visibly left over
  from the R5 hotspot arm and past `wifi-direct-stable-identity` rounds. None of this round's runs
  were affected beyond the one R0a stand-in read, but a round that ever needs a genuinely clean P2P
  group history on this unit will need to clear that list, not just this app's own five settings
  keys.

## Addendum (2026-09-20): the blocker was a stale Bluetooth pairing, not the depoison mechanism

**The R1 finding above overclaimed, and this addendum retracts the overclaimed part.** What was
actually run above was: check `Accepted vehicles` (already `None`, from some earlier point outside
this round), reboot, observe still poisoned. That never exercises forgetting a *confirmed* poisoned
state — there was nothing on the vehicle list for the forget action to act on, so "forgetting the
vehicle and a reboot together did not clear it" is not a conclusion the run above actually supports.
It was pointed out in-session and is corrected here rather than in the original text, per this
branch's append-only rule.

The real cause, found by the operator directly on the rig: **the phone's Bluetooth pairing with this
head unit was stale.** Repairing it (unpair and re-pair) on 2026-09-20 is the only change made before
re-running the round; no app settings, no code, nothing else. Every symptom above — the phone dialling
a stale WPP endpoint forever, escalated wake pokes succeeding at the socket level but never producing
an AA RFCOMM callback, `Accepted vehicles` staying empty — is consistent with a phone that could not
complete a fresh Bluetooth handshake at all, which explains why nothing on the HU side, including a
full reboot of the phone, could ever have fixed it.

**Full re-run after the repair**, same candidate (`736a0582`, rebuilt: APK md5
`0ccac17beabe90526208be7353fef842`, `testGithubDebugUnitTest` 2192/0 again), same settings procedure
as the brief. Capture: `rig-evidence-wpp-endpoint-depoison-round4-addendum`, sha256
`8857700e88d8e080ee1019c2b7420c7868e5caddb2a2854cbbb73ad7960f7dd8`.

- **R0, R0a: PASS**, as above (leftover-group stand-in read reproduced identically, one first-launch
  occurrence, unrelated to this round's own group).
- **R1: PASS.** Poke succeeded, the phone called back over the AA RFCOMM UUID
  (`NativeAA: Connection accepted from POCO X3 NFC` at `15:07:12.682`), and the full handshake ran:
  `[TX] Sending WifiVersionRequest (Type 4)` → `NativeAA: advertising WPP over TCP at
  192.168.49.1:5299` → `[RX] Received Type 5` → `[TX] Sending WifiStartRequest (Type 1)` → `[RX]
  Received Type 2` → `[TX] Sending WifiInfoResponse (Type 3)` → `onSessionEstablished`. The phone's
  own storage then read (`15:07:15.623`) `ssid=DIRECT-XB-MT50YT610E4GFPSLU, bssid=06:38:49:39:E7:0C`
  — exactly this round's own SSID and the exact BSSID from step 1, matching the brief's PASS
  criterion outright.
- **R2: INCONCLUSIVE, with the mechanism pinned down precisely.** `headunit://exit` + relaunch moved
  the group's address under the kept name as designed (`stable=no (same name but the BSSID moved from
  06:38:49:39:E7:0C to 5A:50:C5:B9:73:DD)`). Watched for the full ten minutes: the phone's own
  `WifiNetworkFactory` request stayed pinned to the *old* BSSID and never resolved it, logging
  `WIRELESS_WIFI_SCAN_RESULTS_BSSID_MISMATCH` 20 times over the window — it can see the SSID broadcast
  (it is not `NETWORK_NOT_FOUND`, the earlier round's shape) but its `WifiNetworkSpecifier` is an exact
  BSSID match, so it never routes a packet to us. `grep -c "WppTcpServer:"` on the head unit's side of
  the whole window: **0** — no dial reached the head unit at all, so this is the brief's precondition
  gap ("no dial reaches the head unit in the window"), not a rejection-path result to grade PASS/FAIL.
  One `NativeAaHandshakeManager.escalateHandsFreeWake` fired at `15:09:19.317` ("Wake 1 of 2") and the
  poke succeeded at the Bluetooth socket level (`Successfully poked ... via HFP-AG`), but no
  `Connection accepted` followed and no "Wake 2 of 2" ever fired in the remaining eight minutes —
  Gearhead's own connectivity state machine, once committed to resolving the stale-BSSID request,
  did not act on the wake by reopening AA discovery. This is a real, reproducible shape and a
  legitimate next-round target (does Gearhead ever give up that request on its own, and after how
  long?), not an artifact of the Bluetooth repair.
- **R3: not reached**, for the same reason as R2 — no dial arrived at all, so there is nothing to
  grade the stranding guard against.
- **R4: PASS**, reproduced cleanly a second time. Five `headunit://exit` cycles each produced a new
  BSSID (`stable=no`); five `am force-stop` cycles all read the same BSSID
  (`36:F8:B3:B4:DC:39`, `stable=yes`, `asked=nothing (not this app's create)`).
  `grep -cE "PROV_DISC|prov_disc|stuck retry"`: **0**.
- **R5: PASS.** Run separately from the P2P runs above, hotspot started by the operator by hand
  (`SoftApInfo{... frequency=5180, bssid=00:27:15:43:06:6a, iface=wlan2}` — 5180 MHz, matching the
  band the brief asks to confirm). The phone had a real `Accepted vehicles` entry this time (from R1's
  own session), so `forget_car_gearhead.sh` ran its full flow rather than short-circuiting on an
  already-empty list — the first time this thread has observed an actual forget action against a
  confirmed record. Connect 1: full handshake (`advertising WPP over TCP at 192.168.96.48:5299`,
  `WifiConnectStatus status=SUCCESS`, `SSL handshake complete`, `VideoDecoder.start... Codec
  initialized`). Disconnect via `headunit://disconnect`. Reconnect: `headunit://connect` alone did not
  restart the wireless bring-up (see Setup deviation below); a force-stop + relaunch did, and this
  time the phone reconnected through the WPP-over-TCP endpoint directly (`WppTcpServer: [TX]
  WifiVersionRequest` → `[RX] Type 5` → `[TX] WifiStartRequest (Type 1) -> 192.168.96.48:5288` → `[RX]
  Type 7` → `[RX] Type 6`, then `SSL handshake complete` and `Codec initialized` again) — the exact
  mechanism this thread is named for, working end to end. `grep -c "rejecting this dial"` across the
  whole capture: **0**.
- **R6: PASS**, reproduced identically: `source=static override` is 0, the one stand-in read is the
  same first-launch, unrelated-group occurrence as R0a, and this round's own group is
  `source=IPv6 link-local` on every line including R5's hotspot-transport credentials.

**Setup deviation:** `headunit://connect` (no `ip=`) after a `headunit://disconnect` logged "Received
connect intent without IP -> triggering last session auto-connect" but never restarted the wireless
bring-up (`WifiLauncherNative.start` never fired, no poke, nothing for eight minutes). A plain
force-stop + relaunch of `MainActivity` did. Not chased further since R5 only needs one clean
reconnect, but worth a look if a future round scripts reconnect via that deep link specifically.

**Net effect on this thread:** the depoison mechanism (`0847fd38` + `6b4eb0d9d`) works as designed on
both transports once the Bluetooth link is healthy — R1 and R5 are the thread's first clean PASS on
the actual poison-and-recover path. R2/R3 remain open, but as a real, narrow, well-evidenced gap
(Gearhead's own stuck-request recovery timing) rather than the broad "forgetting doesn't work" claim
above.
