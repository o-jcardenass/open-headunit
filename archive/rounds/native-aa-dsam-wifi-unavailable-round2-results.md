# native-aa-dsam-wifi-unavailable — round 2 results

**Candidate:** `fix/native-aa-withdrawn-network` @ `5846b249aa93574bfe6e6a23bfe07a59a0b713fa`
**Baseline:** not re-run — round 1's captures on `main` are the baseline (`native-aa-dsam-wifi-unavailable-round1-results.md`)
**APK md5:** `737761639426375c173da986a5221538` (candidate, `com.andrerinas.headunitrevived_3.5.0-alpha_debug.apk`, versionCode 113)
**Unit:** D-SAM (Samsung SM-T230, Android 4.4.2 / API 19, 2.4GHz-only WiFi Direct band, not rooted) as head unit; D-POCO (POCO X3 NFC, Android 15, Gearhead `17.5.663204`) as phone
**Date:** 2026-09-24

## Setup notes

- **A1 partly blocked by local tooling policy, not the rig.** Pulling `/system/etc/wifi/` and reading
  `/data/misc/wifi/p2p_supplicant.conf` from D-SAM were refused by this session's own sandbox
  classifier (flagged as credential-adjacent access) and had to be run by the operator instead, from
  a script handed over verbatim. Same for the `baksmali-2.5.2.jar` download (flagged as fetching
  external code) and the `assembleGithubDebug`/`testGithubDebugUnitTest` build (flagged as modifying
  shared resources). None of this reflects a rig limitation; once the operator ran the handed-over
  commands, everything proceeded normally and every file landed in the same `~/rig-private/...` path
  on the same machine, so nothing needed to be handed back except a go-ahead.
- **`p2p_supplicant.conf` read:** `run-as` not available (device not rooted); direct `shell cat` was
  refused with `Permission denied`, exactly the answer the brief called "expected."
- **`/system/etc/wifi/` contents:** `p2p_supplicant_overlay.conf`, `wpa_supplicant.conf`,
  `wpa_supplicant_overlay.conf` — 3 small files, no credential material, only P2P channel/vendor
  defaults and `autoscan`/`fast_reauth` settings. None contain `persistent_reconnect`,
  `p2p_no_group_iface`, or `p2p_go_` (see A3f).
- **A race between two concurrent runs of `partB_cycles.sh` corrupted the first cycle 1/2 attempt**
  (the operator had a second terminal open running the same script while I was independently running
  commands). Caught before any grading: both sets of orphaned `logcat`/`tail` processes were killed,
  the tainted `dsam_c1/c2.logcat` + `dpoco_c1/c2.logcat` files were deleted, and cycles 1 onward were
  re-run solo, sequentially, by this session alone. No graded data in this report comes from the
  raced files.
- **D-SAM's `logcat -c` does not reliably clear its ring buffer** (old toolbox logcat on Android
  4.4.2). Every "fresh" capture in this round contained a handful of stale lines from earlier in the
  session (including two phantom `Client list empty` hits and a duplicate `C1-end` marker in cycle
  1's capture, both timestamped *before* that cycle's own `C1-start`). Every cycle's analysis in this
  report is anchored to that cycle's own marker timestamps, never to a raw whole-file grep. `-G` (log
  buffer resize) is also unsupported on D-SAM's toolbox logcat; only D-POCO's `-G 16M` call succeeded
  and D-SAM's default buffer size was used instead.
- **D-SAM's clock runs ~8.52s behind D-POCO's / the host's.** Measured at round start
  (`host: 15:21:54 -05`, `dsam: 15:21:45 COT`, `dpoco: 15:21:53 -05`) and confirmed per-cycle by
  matching a `[TX] Wrote TYPE 3` (D-SAM) against the phone's `Info response received` for the same
  credentials (e.g. cycle 5: D-SAM `15:36:53.693` vs phone `15:37:02.213` → 8.520s). Every
  cross-device timing comparison in the per-hit grading below adds this offset to D-SAM's clock
  before comparing to D-POCO's.
- Settings recorded, unchanged (per brief §4, no settings were written this round):
  `wifi-connection-mode=3` (Native AA), `wifi-direct-band=2` (2.4GHz only). `native-poke-bt-macs` is
  not present in `settings.xml` at all (default/empty), not merely blank.
- Unit test suite on the candidate SHA: 2376 tests, 0 failures, 0 errors — matches the brief exactly.
- Stopped after cycle 7 (3rd cycle to hit the trigger, per the brief's own stopping rule), not the
  full 10.

## Part A — Samsung's group-removal rule

### A3a. The branch that logs "Client list empty, remove non-persistent p2p group"

File: `smali-framework/android/net/wifi/p2p/WifiP2pService$P2pStateMachine$GroupCreatedState.smali`
(this build's `WifiP2pService` lives in `framework.jar`/`framework.odex`, not `services.jar` — normal
for API 19, before services split out into its own jar).

The only condition tested is `WifiP2pGroup.isClientListEmpty()`. There is **no** check of
`mAutonomousGroup`, `mPersistentGroup`, or any other field before the removal:

```smali
invoke-virtual {v1, v11}, Landroid/net/wifi/p2p/WifiP2pGroup;->removeClient(Ljava/lang/String;)Z
move-result v1
if-eqz v1, :cond_451

# "Removed client <addr>" logd, then:
invoke-virtual {v1}, Landroid/net/wifi/p2p/WifiP2pGroup;->isClientListEmpty()Z
move-result v1
if-eqz v1, :cond_449

const-string v2, "Client list empty, remove non-persistent p2p group"
invoke-virtual {v1, v2}, ...->logd(Ljava/lang/String;)V
invoke-static {v1}, ...->access$1000(...)Landroid/net/wifi/WifiNative;   # mWifiNative
...
invoke-virtual {v1, v2}, Landroid/net/wifi/WifiNative;->p2pGroupRemove(Ljava/lang/String;)Z
```

Nothing else gates it. The log message's own name ("non-persistent") is aspirational, not enforced —
the code removes an emptied group unconditionally regardless of whether it actually is persistent.

### A3b. Every write to fields the condition tests

The condition tests only a method call (`isClientListEmpty()`), not a field — there is no
`mAutonomousGroup`/`mPersistentGroup` read anywhere in this branch. For completeness, both fields do
exist in `WifiP2pService.smali` and are used elsewhere:

- **`mAutonomousGroup`** (`WifiP2pService.smali:126`, accessors `access$7800`/`access$7802`):
  - Read once, in `GroupCreatedState.enter()` (line 235), to decide whether to send a
    `P2P_CONNECTION_CHANGED` broadcast — unrelated to group removal.
  - Written (`access$7802`) in `InactiveState` (5 sites) and `NfcProvisionState` (3 sites) — always as
    part of arming or leaving a group, never read back by the removal branch.
  - Set unconditionally to `true` (`const/4 v2, 0x1`) inside the `CREATE_GROUP` handler itself (see
    A3c) — every group this service creates via that message is autonomous, but that fact plays no
    role in whether it gets removed.
- **`mPersistentGroup`** (`WifiP2pService.smali:202`, accessors `access$12100`/`access$12102`):
  - Read once, in `GroupCreatedState.enter()` (line 214-219), to decide whether to arm the idle-group
    timer (see A3e).
  - **The setter (`access$12102`) has zero callers anywhere in the decompiled tree** (`framework.jar`
    and every other odex swept). The field can never become `true`; it is always its Java default
    (`false`). So the idle-timer path in A3e is unconditionally armed for every group this service
    creates — there is no way for the app to avoid it, and no live code path that ever tries.

### A3c. `CREATE_GROUP` (`0x2200d` / 139277) handling

Handled in `InactiveState` at `sswitch_6a9` (`WifiP2pService$P2pStateMachine$InactiveState.smali:2065`):

```smali
:sswitch_6a9
# (Samsung MDM restriction-policy check, unrelated)
const/4 v2, 0x1
invoke-static {v1, v2}, ...->access$7802(...)Z   # mAutonomousGroup = true, unconditionally

iget v15, v0, Landroid/os/Message;->arg1:I        # v15 = "netId" = the message's arg1
.local v15, "netId":I
...
const/4 v2, 0x0
invoke-virtual {v1, v2}, Landroid/net/wifi/WifiNative;->p2pGroupAdd(Z)Z   # p2pGroupAdd(false)
```

**`arg1` ("netId") is read into a local and never used again anywhere in the method** (confirmed:
grepping the disassembled method body for `v15` between its declaration and its `.end local` finds
only the read at line 2114 and the scope-close at 2189 — no branch, no comparison, no further use).
The boolean passed to `p2pGroupAdd()` is a hardcoded `const/4 v2, 0x0` (`false`), completely
independent of what `arg1` carried. So whatever value `WifiP2pManager.createGroup()` sends, this
build's service ignores it and always requests a non-persistent group.

### A3d. `WifiP2pManager.createGroup(Channel, ActionListener)` and every `Group`/`Persistent`/`Autonomous` method

```smali
.method public createGroup(Landroid/net/wifi/p2p/WifiP2pManager$Channel;Landroid/net/wifi/p2p/WifiP2pManager$ActionListener;)V
    ...
    const v1, 0x2200d          # CREATE_GROUP
    const/4 v2, -0x2           # arg1 = -2 (this is the "netId" A3c reads and ignores)
    invoke-static {p1, p2}, ...->access$600(...)I   # putListener
    move-result v3
    invoke-virtual {v0, v1, v2, v3}, Lcom/android/internal/util/AsyncChannel;->sendMessage(III)V
.end method
```

Every public method in `WifiP2pManager.smali` whose name contains `Group`, `Persistent` or
`Autonomous` (grepped for `^\.method` across the whole file):

```
createGroup(Channel, ActionListener)V
deletePersistentGroup(Channel, I, ActionListener)V
removeGroup(Channel, ActionListener)V
requestGroupInfo(Channel, GroupInfoListener)V
requestPersistentGroupInfo(Channel, PersistentGroupInfoListener)V
```

**There is no `createGroup` overload that takes a persistence flag or a specific `netId`.** The only
group-creation entry point this build's `WifiP2pManager` exposes always sends `arg1 = -2`, and A3c
shows the service ignores that value outright. Nothing the app calls, and nothing this framework
exposes to call, can ask for a persistent group.

### A3e. Anything that removes a group on a timer or on idle

Besides the client-list-empty path (A3a) there is exactly one timer-driven removal, plus three
error-path cleanup calls with no timer involved:

- **`P2P_GROUP_STARTED_TIMED_OUT`** (`0x23034`, `WifiP2pService.smali:90`). Armed in
  `GroupCreatedState.enter()` only when `!mPersistentGroup` (A3b: always true, so always armed).
  Its handler (`GroupCreatedState.smali:4287`, `sswitch_c90`) re-checks `isClientListEmpty()` and,
  if still empty, self-sends `REMOVE_GROUP` (`0x22010`), whose handler (`sswitch_5bf`, line 2285)
  calls `mWifiNative.p2pGroupRemove()` unconditionally. This round never observed this path fire —
  every hit was caught by the immediate `AP_STA_DISCONNECTED` → client-list-empty path first — but it
  exists as a second, independent removal trigger with no app-controllable disarm.
- Three more `p2pGroupRemove()` call sites (`GroupCreatedState.smali:2279`, `DefaultState.smali:1322`,
  `InactiveState.smali:2351`) are all reached immediately after a `loge(...)` call — error-path
  cleanup (e.g. failed DHCP/interface setup), not timers.

### A3f. `getprop` and `/system/etc/wifi/` lines

Every `getprop` line matching `wifi|p2p|wlan` (18 total; DHCP lines are a stale lease from a prior
real WiFi association, not from this round):

```
[dhcp.wlan0.dns1]: [200.21.200.10]  [dhcp.wlan0.dns2]: [200.21.200.80]  [dhcp.wlan0.dns3]: []
[dhcp.wlan0.dns4]: []  [dhcp.wlan0.domain]: []  [dhcp.wlan0.gateway]: [192.168.1.1]
[dhcp.wlan0.ipaddress]: [192.168.1.2]  [dhcp.wlan0.leasetime]: [86400]  [dhcp.wlan0.mask]: [255.255.255.0]
[dhcp.wlan0.mtu]: []  [dhcp.wlan0.pid]: [12453]  [dhcp.wlan0.reason]: [ROUTERADVERT]
[dhcp.wlan0.result]: [failed]  [dhcp.wlan0.roaming]: [0]  [dhcp.wlan0.server]: [192.168.1.1]
[dhcp.wlan0.vendorInfo]: []  [init.svc.dhcpcd_wlan0]: [stopped]  [init.svc.iprenew_wlan0]: [stopped]
[init.svc.p2p_supplicant]: [running]  [net.tcp.buffersize.wifi]: [524288,1048576,2097152,262144,524288,1048576]
[ro.build.description]: [degaswifixx-user 4.4.2 KOT49H T230XXU0ANJ4 release-keys]
[ro.build.fingerprint]: [samsung/degaswifixx/degaswifi:4.4.2/KOT49H/T230XXU0ANJ4:user/release-keys]
[ro.build.product]: [degaswifi]  [ro.carrier]: [wifi-only]  [ro.product.device]: [degaswifi]
[ro.product.name]: [degaswifixx]  [ro.wifi.active_roaming.enable]: [true]  [ro.wifi.channels]: []
[wifi.interface]: [wlan0]  [wlan.driver.status]: [ok]
```

`/system/etc/wifi/` (full contents, 3 files, quoted in Setup notes above): no `persistent_reconnect`,
`p2p_no_group_iface`, or `p2p_go_` line in any of them.

**Bottom line for Part A:** nothing the app controls — no setting, no API call, no config file this
service reads — keeps a Native AA group alive past its first empty client list on this platform. The
candidate correctly does not try to stop step 3; A3a-A3e confirm there is no lever to stop it with.

## Part B — the candidate on D-SAM with D-POCO

B0 identity check: `ACTION_QUERY_STATE` → `"commit":"5846b249aa93-dirty"` (the `-dirty` suffix is only
the handed-over helper scripts sitting untracked in the repo root during the build, not a source
change — confirmed with `git status --short`/`git diff --stat`, both clean of tracked changes, moved
out of the repo before the round continued).

### Per-cycle summary

| Cycle | Hit? | Session in 240s? | Seconds `C<N>-start`→`SSL handshake complete` | `-11` count |
|---|---|---|---|---|
| C1 | no | yes | 23.03s | 0 |
| C2 | **yes (1)** | no (240s timeout) | — | 4 |
| C3 | no | yes | 22.01s | 0 |
| C4 | no | yes | 23.61s | 0 |
| C5 | **yes (1)** | yes | 38.48s | 0 |
| C6 | no | yes | 29.94s | 0 |
| C7 | **yes (4)** | yes | 77.61s | 0 |

Stopped after C7 (3rd cycle to hit the trigger). 6 individual hits graded across 3 cycles.

### Per-hit grading

All timestamps below are D-SAM clock unless marked "phone" (D-POCO clock, already skew-corrected by
adding 8.52s where compared against a D-SAM timestamp).

| Hit | Drop (`Client list empty`) | Candidate's line | Δ (≤5s) | Next `Connection accepted` | Δ (≤20s) | Phone stops hunting old SSID | Δ (≤5s, skew-corrected) | Verdict |
|---|---|---|---|---|---|---|---|---|
| C2 h1 | 15:27:04.658 | **never appeared** | — | 15:27:49.112 (45.1s later, old path) | — | phone exhausted its own 3-attempt ladder (7s+12s+17s) to `ABORTED_WIFI` at 15:27:42.112 | — | **FAIL** |
| C5 h1 | 15:36:47.397 | 15:36:50.590 | 3.19s | 15:36:52.241 | 4.84s | last `DIRECT-v4` attempt 15:36:59.131 (phone) | 3.21s | **PASS** |
| C7 h1 | 15:42:01.623 | 15:42:05.327 | 3.70s | 15:42:10.232 | 8.61s | last `DIRECT-Xc` attempt 15:42:13.507 (phone) | 3.36s | **PASS** |
| C7 h2 | 15:42:18.760 | 15:42:21.913 | 3.15s | 15:42:27.368 | 8.61s | last `DIRECT-4F` attempt 15:42:30.631 (phone) | 3.35s | **PASS** |
| C7 h3 | 15:42:35.897 | 15:42:39.100 | 3.20s | 15:42:44.595 | 8.70s | last `DIRECT-5Q` attempt 15:42:47.764 (phone) | 3.34s | **PASS** |
| C7 h4 | 15:42:53.144 | 15:42:56.237 | 3.09s | 15:42:58.108 | 4.96s | last `DIRECT-Wz` attempt 15:43:04.778 (phone) | 3.12s | **PASS** |

**5 of 6 hits PASS, one FAILs. Per the brief's rule ("PASS only if every graded hit PASSes"), the
round's verdict is FAIL.**

### Why C2 h1 failed while every other hit passed

The candidate's `credentialsWithdrawals` counter is only ever incremented by
`WifiDirectManager.invalidateNativeGroupCredentials()`, and every call site logging "recreating the
group" that actually fired during a hit this round traces back to `refreshNativeCredentials()`
finding "no group is up" — **not** to the `WIFI_P2P_CONNECTION_CHANGED_ACTION` broadcast that fires
the instant D-SAM's platform removes the group. Read in source
(`WifiDirectManager.kt`, the `else` branch of that broadcast handler, ~line 803): it resets
`isConnected`, `isClientConnected`, `lastNativeGroupStatusMessage`, `ConnectionStageTracker`,
`isGroupCreatingOrCreated`, and cancels the join watchdog — but never calls
`invalidateNativeGroupCredentials()`. So the one event this candidate exists to react to never
directly arms it; detection only happens when some other, unrelated `refreshNativeCredentials()` call
(scheduled elsewhere, on its own cadence — not on every poke, and not on the disconnect broadcast)
happens to land in the few seconds after the drop.

In the 5 passing hits that incidental refresh landed reliably, every single time, 3.09-3.70s after
the drop. In C2 h1 it did not land at all before the phone exhausted its own retry ladder 37s later —
confirmed by grepping the full cycle window: no `WifiDirectManager.refreshNativeCredentials` or
`invalidateNativeGroupCredentials` log line of any kind appears between the drop (15:27:04.658) and
the phone's `WIFI_NETWORK_UNAVAILABLE(-11)` report (15:27:34.077). The candidate's own WARN/Fail lines
never appear anywhere in C2's whole capture. **The candidate works when something else happens to
notice the group is gone in time; it has no reliable trigger of its own tied to the actual removal
event.**

Also notable, only in C2: because nothing ever recreated the group, the app spent the rest of the
240s window re-poking the phone and **resending the same already-dead SSID/credentials** three more
times (4 `[TX] Wrote TYPE 3` total, only 1 `createGroup SUCCESS` in the whole cycle), each producing
another `WIFI_NETWORK_UNAVAILABLE(-11)` with an escalating backoff (15s → 30s → 30s → 30s) — the exact
"45s lost, 2-4 occurrences per connect" pattern round 1 measured on `main`, undiminished.

### First hit's phone-side lines (C2 h1, 5s before the skew-corrected drop through the reconnect)

Drop, skew-corrected to phone clock: 15:27:04.658 + 8.52s ≈ **15:27:13.18**.

```
15:27:06.056  GH.WIRELESS.SETUP: Info response received. Received credentials=WifiConfiguration(ssid=DIRECT-HU-Navegadortz3, ...)
15:27:06.057  GH.WIRELESS.SETUP: State changed to CONNECTING_WIFI
15:27:06.058  GH.WirelessNetRequest: MD supports WPA3-Personal SAE security mode.
15:27:06.065  GH.WirelessNetRequest: HU is using WPA2 security mode.
15:27:13.074  GH.WirelessNetRequest: Failed to find network within PT7S
15:27:13.076  GH.WirelessNetRequest: Retry to connect to the same network. Force: false
15:27:20.094  GH.WirelessNetRequest: Supplicant state: SCANNING
15:27:25.086  GH.WirelessNetRequest: Failed to find network within PT12S
15:27:25.089  GH.WirelessNetRequest: Retry to connect to the same network. Force: false
15:27:32.108  GH.WirelessNetRequest: Supplicant state: DISCONNECTED
15:27:39.130  GH.WirelessNetRequest: Supplicant state: DISCONNECTED
15:27:42.099  GH.WirelessNetRequest: Failed to find network within PT17S
15:27:42.102  GH.WirelessNetRequest: Do not retry wifi connection. Num attempts used: 3/3, expired: false
15:27:42.112  GH.WIRELESS.SETUP: State changed to ABORTED_WIFI
15:27:42.112  GH.WIRELESS.SETUP: Send WifiConnectStatus, status=STATUS_WIFI_NETWORK_UNAVAILABLE, errorMessage=Optional.empty
15:27:42.114  GH.WIRELESS.SETUP: Network unavailable. Info response state=RECEIVED
15:27:42.116  GH.WIRELESS.SETUP: Triggering WPP restart. Reason=NETWORK_UNAVAILABLE_NETWORK_NOT_FOUND
15:27:47.130  GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit, will not start WPP on TCP.
15:27:47.140  GH.WIRELESS.SETUP: State changed to SHUTDOWN
15:27:47.153  GH.WIRELESS.SETUP: Wireless setup is stopped.
```

This answers the round's open question directly: with the link left open (candidate inert), Android
Auto runs its **full 3-attempt escalating ladder** (7s, 12s, 17s ≈ 36s total) against the dead network
before giving up on its own, then restarts WPP. It does not abandon the old join early; the app has to
close the Bluetooth link itself to make that happen faster, which is exactly what the candidate does
in the 5 passing hits (compare the `RFCOMM_READ_WRITE_FAILURE`/`WPP_SOCKET_IO_EXCEPTION` pattern
visible in C5/C7's captures, where the closed link cuts the ladder off after the first ~7s attempt).

## Anything the brief did not ask about

- The mechanism that actually arms the candidate's detection (`refreshNativeCredentials()` finding
  "no group is up") is not documented anywhere as a Native-AA-recovery signal — it reads as a general
  credential-refresh safety net that happens to also catch this case, on its own schedule, when its
  timing lines up. If a future patch wants deterministic coverage, wiring
  `invalidateNativeGroupCredentials()` directly into `WifiDirectManager`'s `WIFI_P2P_CONNECTION_CHANGED_ACTION`
  disconnected branch looks like the direct fix — that branch already resets every other piece of
  state for this exact event and only omits this one call.
- Cycle 7's four consecutive hits (all against fresh recreated groups) is itself a data point: D-SAM's
  platform-driven removal is not a one-off flake on this connect — once it starts happening in a
  session it can repeat immediately against every freshly recreated group in a row, until either the
  phone's join finally lands in time or (per this round) the candidate isn't there to catch it.
