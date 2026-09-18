# wifi-direct-stable-identity — round 1 results

**Candidate:** `fork/feat/wifi-direct-stable-identity` @ `652ba40f` (3 commits on `ef74866c`)
**Baseline:** `fork/feat/native-aa-wpp-tcp-and-hfp-link` @ `ef74866c`
**APK md5:** candidate `b891061428dac2a8aa16b8cc3b80e958` / baseline `54584d347ffe9e4ba7d31149c541fa16`
**Unit:** D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, single BT radio. D-POCO = POCO X3 NFC, Android 15, Gearhead 17.5.663204 (R2/R4 only).
**Date:** 2026-09-01

## Headline

- **Question 1 (does the platform honour the persistent request on this unit): YES.** Five bring-ups,
  one persistent profile (`netId 1`), same SSID `DIRECT-WC-Navegadortz2` and same passphrase every
  time, `persistent=yes` and `matchesRequest=yes` on every read-back line.
- **Question 2 (does the BSSID repeat on this unit): NO.** All five groups came up under a different
  locally-administered BSSID. `stable=no` on bring-ups 2-5. `/data/vendor/wifi/wpa/p2p_supplicant.conf`
  has `p2p_interface_random_mac_addr=1`, so the group interface is re-addressed on every create.
- Therefore **R4 is a pass-by-refusal** and **R4b is the live run of this round** (per the brief).

## Shipping read

- **R0-R1, R1b, R6: PASS.** The name/passphrase half of the branch does exactly what it claims on
  this unit — one persistent profile, same SSID and PSK on every create, and a clean off switch.
- **R4 PASS-by-refusal, R4b validates the gate.** On a unit that re-addresses the group every create
  (this one), `WppEndpointPolicy` withholds the WPP-over-TCP endpoint and the session forms over
  Bluetooth (R4). Forcing the endpoint out anyway (R4b, via `static-bssid`) then letting the address
  move reproduced the exact brick the gate exists to prevent: the phone loops `NETWORK_NOT_FOUND`
  for minutes with no Bluetooth fallback. The gate should ship as written.
- **R2: no regression, but no measurable benefit on this rig.** The reconnect-speed win the branch
  targets needs a BSSID-stable unit; on a `stable=no` unit the stable SSID can even cost the phone a
  few seconds (it tries a saved profile pinned to a dead BSSID first). Route the reconnect-time A/B
  to a `stable=yes` unit and the JVM policy tests.
- The one thing hardware could not settle: whether a unit that *does* keep its BSSID gets the faster
  reconnect and the safe TCP endpoint. This rig re-randomises (`p2p_interface_random_mac_addr=1`),
  so that path stays on `GroupIdentityStabilityPolicyTest` (10) / `WppEndpointPolicyTest` (10) /
  `P2pGroupIdentityPolicyTest` (15) / `NativeRefreshPolicyTest` (5).

## Setup notes

### Deviations from the brief

- **`dumpsys wifip2p` redacts `networkName` and `passphrase` on this ROM** — both print empty
  (`networkName: `, `passphrase: <empty>`) on every one of the five bring-ups, and the app logs the
  SSID but never the passphrase at any level (verified in source: no `AppLog` call in
  `WifiDirectManager` / `NativeAaHandshakeManager` emits the passphrase; `NativeAA: Credentials
  updated` carries SSID/IP/BSSID only). The brief's two stated ways to learn the drawn passphrase
  (dumpsys, or a `log-level=1` `Credentials updated` line) therefore both fail here.
  **Substitute used:** the persistent-group passphrase was read as root from
  `/data/vendor/wifi/wpa/p2p_supplicant.conf` (`adb shell` is root on this rig). Bring-up 1 drew
  `ssid="DIRECT-WC-Navegadortz2" psk="ne4Pmsmk11AX"`; that pair was then written by hand into
  `wifi-direct-group-name` / `wifi-direct-group-passphrase` before bring-up 2, exactly as the brief's
  root-owned-prefs workaround intends, just with the psk sourced from the supplicant config instead
  of dumpsys. The SSID in each read-back line, and `matchesRequest=yes` on bring-ups 2-5 (which
  compares the stored psk against the live `WifiP2pGroup.passphrase`), independently confirm the psk
  written by hand is the one on the air.
- **`shared_prefs/` is root-owned** (`project_test_headunit_shared_prefs_root_owned`), so the app's
  own writes of the four identity keys never reach disk. All four keys were written by hand with
  `set_hu_prefs.sh` (rooted `sed`, chowns the file back) between bring-ups, per the brief §3.
  `wifi-direct-last-group-bssid` was updated to the previous group's BSSID after every bring-up so
  each comparison was against the immediately-preceding group.

### Scripts

- `build_hur.sh`, `run_unit_tests.sh` — unchanged, used for both APKs.
- **Added `wds_bringup.sh`** — one Native AA WiFi Direct bring-up on D-HU (force-stop, capture,
  launch, settle, `dumpsys wifip2p`, force-stop, print decisive lines + counts). HU-only.
- **Added `wds_reconnect.sh`** — R2/R4/R4b: launch HU, then N forced reconnects via
  `headunit://disconnect` + a head-unit BT-adapter cycle (the phone-BT-cycle route does not raise
  `ACL_CONNECTED` on this rig, TESTING-TEMPLATE §7a). `N=0` = initial connect only.

### Other deviations

- **R2 reconnect trigger:** the brief's "cycle the phone's Bluetooth" does not raise `ACL_CONNECTED`
  on this rig (§7a). First attempt (D-HU BT cycle alone, no disconnect) left the live port-5288
  session up and produced zero reconnects — discarded. Working method: `headunit://disconnect` (tears
  the session + P2P group down) then D-HU `svc bluetooth disable` (self-reverts ~14 s → `ACL_CONNECTED`
  → `MATCH! Starting AapService` → re-init).
- **R4b leaves D-POCO with a stored WPP-over-TCP endpoint** that it loops on (`NETWORK_NOT_FOUND`).
  Clearing it is "forget this head unit" in D-POCO's Android Auto settings — a UI step, left for the
  operator. Deliberate: the brief's R4b says to forget the car afterwards either way.
- **`static-bssid` override and the transmitted BSSID:** with `static-bssid` set, `onGroupInfoAvailable`
  and `NativeAA: Credentials updated` both report the static value, and the app logs `the BSSID being
  sent is the static override from Settings`. But the BSSID Gearhead ends up storing in its
  WPP-over-TCP config in R4b was a *stale real* address (`EA:BD:E2:D6:29:9C`), not the static
  `5E:7E:B1:C9:37:8B` — Gearhead appears to take the BSSID from its own scan of the live group, not
  purely from the credential payload. Noted for the coding session; does not change any verdict.

### settings.xml delta vs the round's fresh backup

One key: `wifi-5ghz-channel` `0` → `36` (brief §3: band/channel as five-ghz-channel round 1's R1,
channel 36 so the phone lists the group). Everything else the brief's "every WiFi Direct run" table
asks for was already in place from prior threads (`wifi-connection-mode=3`, `native-ap-transport=0`,
`log-level=2`, `native-wifi-version-exchange=false`, empty `hotspot-*`, `static-bssid=0`,
`wifi-direct-band=1`, `hotspot-band=1`). `wifi-direct-stable-identity` absent = default `true`.
`stand-down-station-for-wifi-direct` absent.

## R0 — build gate and unit tests

**PASS**

- Candidate: **1169 / 0** exact. `P2pGroupIdentityPolicyTest` 15, `NativeRefreshPolicyTest` 5,
  `GroupIdentityStabilityPolicyTest` 10, `WppEndpointPolicyTest` 10 — all four match the brief exactly.
- Baseline: **1135 / 0** exact.
- DEX symbols — candidate: `P2pGroupIdentityPolicy` 13, `GroupIdentityStabilityPolicy` 5,
  `NativeRefreshPolicy` 6, `WppEndpointPolicy` 3. Baseline: `P2pGroupIdentityPolicy` 0,
  `GroupIdentityStabilityPolicy` 0, `NativeRefreshPolicy` 0, `WppEndpointPolicy` 3 (predates this
  branch — the hotspot path). Matches the brief's gate.

## R1 — identity across five bring-ups (candidate, head unit only)

**PASS**

- Settings written: as above; identity keys seeded by hand between bring-ups (see Setup notes).
- Radio state: phone BT + WiFi **off** throughout (`svc bluetooth disable` / `svc wifi disable` on
  D-POCO, confirmed `state: OFF`). D-HU joined to `Pegue Cdesta` @ 5260 MHz throughout.
- Discard-rule check: clean. `createGroup SUCCESS` = 1 per bring-up; `MATCH! Starting AapService` = 0;
  the second `p2p-wlan0-N` each bring-up is the *previous* group being torn down at launch
  (`P2P-GROUP-REMOVED … reason=REQUESTED`, before the create), the §7a benign pattern.

| # | `group identity:` line | read-back | BSSID | `stable=` |
|---|---|---|---|---|
| 1 | `no kept network yet, so DIRECT-WC-Navegadortz2 is drawn now and kept` | `persistent=yes (netId 1) asked=persistent matchesRequest=yes` | `A6:6D:8C:4C:EA:50` | `unproven (first group under this name)` |
| 2 | `asking for the kept network DIRECT-WC-Navegadortz2 again` | `persistent=yes (netId 1) asked=persistent matchesRequest=yes` | `2A:4F:03:25:F1:40` | **`no`** (moved from A6:6D:…) |
| 3 | `asking for the kept network …` | `persistent=yes (netId 1) matchesRequest=yes` | `D2:D7:14:1D:41:6B` | **`no`** (moved from 2A:4F:…) |
| 4 | `asking for the kept network …` | `persistent=yes (netId 1) matchesRequest=yes` | `9E:0B:23:8B:2F:23` | **`no`** (moved from D2:D7:…) |
| 5 | `asking for the kept network …` | `persistent=yes (netId 1) matchesRequest=yes` | `36:27:74:D6:2F:99` | **`no`** (moved from 9E:0B:…) |

- Five `group identity ssid=` lines all name `DIRECT-WC-Navegadortz2`; bring-ups 2-5 all say
  `asking for the kept network`. ✓
- `persistent=yes (netId 1)` and `matchesRequest=yes` on every line. ✓
- `/data/vendor/wifi/wpa/p2p_supplicant.conf` after all five: one `network={ ssid="DIRECT-WC-Navegadortz2"
  psk="ne4Pmsmk11AX" … disabled=2 }` block (plus an unrelated older `DIRECT-TQ-…` from a prior
  thread). Same name + psk throughout. Freq 5180 MHz (ch 36) every bring-up. ✓
- `createGroup SUCCESS` = 1 per bring-up. ✓

**Second answer (decides R4):** `stable=no` on every measurable bring-up. This unit keeps the group
*name and passphrase* but gives the group a **new BSSID on every create**
(`p2p_interface_random_mac_addr=1` in the supplicant config). Five distinct BSSIDs, all with the
locally-administered bit set.

## R1b — the baseline, two bring-ups

**PASS** (matches the documented baseline behaviour)

- Bring-up 1: `SSID=DIRECT-F1-Navegadortz2, BSSID=46:6B:6E:C1:80:3D`
- Bring-up 2: `SSID=DIRECT-5Y-Navegadortz2, BSSID=AE:5A:12:7C:A8:E8`
- Two different `DIRECT-xx-` names (F1, 5Y). Zero `group identity` lines — the read-back line does
  not exist on the baseline (`SUCCESS - Providing credentials` is the pre-branch `$lambda$5` form
  with no `identity stable=` suffix; no `chooseNativeGroupIdentity` call). `createGroup SUCCESS` = 1
  each. The baseline mints a fresh random name *and* a fresh BSSID on every create.

## R3 — no churn while nobody joins, A/B (head unit only)

**PASS** on the churn question; the `refresh: the group … is up` sub-condition is unreachable on this
rig HU-only (see below).

- Settings: candidate had the identity keys seeded (name `DIRECT-WC-…`, psk `ne4Pmsmk11AX`); baseline
  none. Phone BT off. 80 s bring-up, then force-stop.
- Discard check: clean, 1 create in the first 60 s on both arms.

| | candidate | baseline |
|---|---|---|
| `createGroup SUCCESS` in first 60 s | 1 (at +6 s) | 1 (at +6 s) |
| `createGroup SUCCESS` total in 80 s | 2 | 2 |
| second create | +61 s (the 60 s join watchdog) | +61 s (the 60 s join watchdog) |
| `WiFi Direct credential refresh requested` | 0 | 0 |
| `refresh: the group … is up` | 0 | 0 |
| `refresh: a group was asked for … ago` | 0 | 0 |
| distinct `p2p-wlan0-N` (incl. the launch teardown of the prior group) | 3 | 3 |

**Reading.** The 10-second credential-refresh loop is driven by an *active handshake wait*, which
never starts with no phone present, so on this rig a HU-only idle bring-up produces zero refresh
lines on **either** arm — the brief's expectation that the baseline recreates "one per ten seconds"
while idle does not hold for this parent build (`ef74866c` already only recreates on the 60 s join
watchdog when nobody joins). The point of R3 — "no churn while nobody joins" — is met: the candidate
churns **identically** to the baseline (1 group + 1 watchdog recreate in 80 s), i.e. the three
commits add no idle churn.

Verification that the refresh no longer *recreates* during a handshake is carried by R2: across the
candidate's 4 sessions (initial + 3 reconnects), each with a ~2-10 s gap between `TYPE 3` and
`Incoming connection detected`, `createGroup SUCCESS` totalled **exactly 4** — one per session, none
mid-handshake — so the refresh that fires during those waits read the live group rather than
remaking it. `NativeRefreshPolicyTest` (5) covers the branch points.

## R2 — reconnect time, A/B (phone needed)

**PASS** on "candidate forms every reconnect / no churn / no regression". The stated
`CACHED_CREDENTIALS_INVALID`-only-once sub-condition is **not measurable on this rig** (see below),
and the reconnect-speedup the change targets does not appear on a `stable=no` unit.

- Settings: candidate had identity keys seeded (`DIRECT-WC-Navegadortz2` / `ne4Pmsmk11AX`);
  `native-wifi-version-exchange=false`. Baseline arm: identity keys cleared. D-POCO BT + WiFi on.
- **Reconnect trigger on this rig:** `headunit://disconnect` on D-HU (tears the session + P2P group
  down) followed by a D-HU BT-adapter cycle (`svc bluetooth disable`, ~14 s self-revert →
  `ACL_CONNECTED` → `MATCH! Starting AapService` → re-init). The phone-BT-cycle route in the brief
  does not raise `ACL_CONNECTED` on this rig (§7a); a first attempt cycling D-HU BT *without* the
  disconnect left the port-5288 session alive and produced no reconnect at all (discarded).
- Discard check: clean on both arms. `createGroup SUCCESS` = 4 (1 initial + 3 reconnects), one per
  session, no extra churn; `p2p-wlan0-N` climbs by exactly one per session.

| | candidate | baseline |
|---|---|---|
| sessions formed (initial + 3 reconnects) | 4 / 4 | 4 / 4 |
| `createGroup SUCCESS` | 4 | 4 |
| `TYPE 3` → `Incoming connection detected`, initial | 1.35 s | 2.08 s |
| … reconnect 1 | 10.2 s | 1.93 s |
| … reconnect 2 | 10.1 s | 1.98 s |
| … reconnect 3 | 2.08 s | 2.17 s |
| `WIRELESS_WIFI_CACHED_CREDENTIALS_USED` (phone) | every connect | every connect |
| `WIRELESS_WIFI_CACHED_CREDENTIALS_INVALID` (phone) | 4 (every connect) | 4 (every connect) |
| `No WPP on TCP configuration found in storage` (phone) | every connect | every connect |

**Readings:**

1. **The candidate forms a session on every reconnect** — the point that matters for shipping. Every
   read-back was `persistent=yes (netId 1) matchesRequest=yes`, `stable=no` (BSSID moved each time).
2. **`CACHED_CREDENTIALS_INVALID` fires on every connect on both arms**, so the brief's PASS
   sub-condition ("first candidate connect at most") cannot be evaluated here. On Gearhead 17.5 the
   WiFi credential cache is keyed to the head unit's Bluetooth identity, not to the SSID: the phone
   tries its cached association on every reconnect and it is invalidated every time — on the baseline
   because the SSID is new, on the candidate because the **BSSID** is new. Same line, same count,
   both arms.
3. **The candidate is not faster** — on this unit it was *slower* on 2 of 3 reconnects (10.2 / 10.1
   vs the baseline's steady ~2 s). The likely cause: with the SSID kept stable, the phone holds a
   *saved network profile* pinned to a now-dead BSSID and spends ~8 s trying it before scanning and
   re-associating on the new address; the baseline's always-new SSID skips straight to a fresh
   association. Per the brief this is "a finding, not a FAIL, because the association time is the
   phone's" — and it is a direct consequence of `stable=no`. The reconnect-speed benefit the three
   commits target is only realisable on a `stable=yes` unit, which this rig is not; that coverage
   sits on `GroupIdentityStabilityPolicyTest` / `P2pGroupIdentityPolicyTest` and needs a
   BSSID-stable unit to confirm on hardware.

## R4 — WPP over TCP on WiFi Direct (candidate, phone needed)

**PASS by refusal** (the brief's path when R1 = `stable=no`)

- Settings: candidate, identity keys seeded, `native-wifi-version-exchange=true`, `static-bssid=0`.
  D-POCO BT + WiFi on.
- One connect. Decisive lines (with timestamps):
  - `18:54:18.693  … group identity ssid=DIRECT-WC-Navegadortz2 persistent=yes (netId 1) … stable=no
    (…BSSID moved from 66:21:FB:C5:E7:8D to EA:BD:E2:D6:29:9C…)`
  - `18:54:22.180  NativeAA: not advertising WPP over TCP: this unit gives its WiFi Direct group a new
    address on every create, and the phone would keep dialling the one it stored. …forget this head
    unit on the phone` ← the brief's stable=no refusal line, verbatim
  - `18:54:22.347  [RX] Received Type 5 … WifiVersionResponse v4.2 status=NO_SUPPORTED_WIFI_CHANNELS(-8)`
    — the phone rejects the version exchange's channel list (Gearhead 17.5 behaviour, unrelated to
    this branch)
  - `18:54:23.424  [TX] Wrote TYPE 3` → `18:54:25.463  WirelessServer: Incoming connection detected
    from /192.168.49.25` → `18:54:25.701  SSL handshake complete`
- The endpoint is withheld and the session still forms over Bluetooth. ✓

## R4b — does the phone pin the BSSID at all? (candidate, phone needed)

**The gate is right as written** (the brief's `NETWORK_NOT_FOUND`-loop outcome). The phone pins both
SSID and BSSID and has no Bluetooth fallback.

- Settings: candidate, identity keys seeded, `native-wifi-version-exchange=true`,
  **`static-bssid=5E:7E:B1:C9:37:8B`** (the address the R4b probe group had). D-POCO BT + WiFi on.
- First connect (18:56):
  - `onGroupInfoAvailable: SSID: DIRECT-WC-Navegadortz2, BSSID: 5E:7E:B1:C9:37:8B (source=static override)`
  - `group identity ssid=DIRECT-WC-Navegadortz2 persistent=yes (netId 1) … stable=yes (the static
    BSSID setting fixes the address the phone is told) source=static override`
  - `NativeAA: Credentials updated. SSID=DIRECT-WC-Navegadortz2, IP=192.168.49.1,
    BSSID=5E:7E:B1:C9:37:8B, identity stable=yes` — the credential payload carries the static address
  - `NativeAA: the BSSID being sent is the static override from Settings, which is a way round this
    unit not reading its own WiFi address rather than proof that it can…`
  - `NativeAA: advertising WPP over TCP at 192.168.49.1:5299` ← endpoint goes out, as the gate intends
    once `stable=yes`
  - `[TX] Wrote TYPE 3` at 18:56:34
- **The phone then never forms a session.** `SSL handshake complete` = **0** for the whole run
  (18:56 → 19:02). Instead the phone stores the endpoint and loops:
  - `GH.WPP.TCP: Trying to start WPP on TCP with configuration: WifiProjectionProtocolOnTcpConfiguration(
    wifiConfiguration=WifiConfiguration(ssid=DIRECT-WC-Navegadortz2, bssid=EA:BD:E2:D6:29:9C, …),
    ipAddress=192.168.49.1, port=5299)` — **× 8**
  - `WIRELESS_WIFI_SCAN_RESULTS_NETWORK_NOT_FOUND` / `Triggering WPP restart.
    Reason=NETWORK_UNAVAILABLE_NETWORK_NOT_FOUND` / `Restarting WPP over TCP` — **`NETWORK_NOT_FOUND`
    × 23** over ~6 minutes, still going when the capture stopped.
  - No `RFCOMM` / Bluetooth-handshake fallback is ever attempted for that session — the phone is
    committed to the dead TCP endpoint.
- Meanwhile D-HU churned five groups trying to get a join (`createGroup SUCCESS` = 5, including the
  60-second join watchdog falling back to `standardCreateGroup` and bringing up the stale
  `DIRECT-TQ-Navegadortz2` framework profile).
- The BSSID the phone loops on (`EA:BD:E2:D6:29:9C`) is a **stale** address — not the `5E:7E:…`
  static override we sent on this connect, and not any group live during R4b. Gearhead recorded a
  BSSID once and will not re-resolve it against a fresh scan.

**Conclusion for the branch:** `WppEndpointPolicy` withholding the endpoint on `stable != STABLE` is
correct and load-bearing. The A/B is R4 vs R4b on the same rig: R4 (withheld, `stable=no`) formed a
Bluetooth session in ~7 s; R4b (forced out via `static-bssid`, then the real group address moved)
left the phone unable to connect for the entire run with no fallback. The gate does **not** need to
be relaxed to name-only — Gearhead 17.5 pins the BSSID and will loop `NETWORK_NOT_FOUND`
indefinitely when it is wrong.

**Cleanup:** the R4b endpoint was held in D-POCO's Gearhead *process memory* (session-scoped, not
persisted). A `am force-stop com.google.android.projection.gearhead` cleared it — 0 new
`Trying to start WPP on TCP` in the 20 s after — so no "forget this head unit" UI step was needed
after all. (If D-POCO is next used for a Self Mode round, its dev head-unit server on `:5277` will
need the AA-Developer-settings "Start head unit server" toggle, per the standing note.) HU
`settings.xml` restored byte-identical to the round backup (`diff` clean); `static-bssid` back to
`0`; `native-wifi-version-exchange` back to `false`; all four identity keys deleted; candidate APK
left installed; both phones' radios on.

## R6 — the off switch (candidate, head unit only)

**PASS**

- Settings: `wifi-direct-stable-identity=false`, all four identity keys deleted. Phone BT off.
- Bring-up 1: `group identity: a new network on every create (DIRECT-PB-HeadUnit)` →
  `group identity ssid=DIRECT-PB-HeadUnit persistent=no (temporary) asked=temporary matchesRequest=yes
  bssid=76:0E:97:53:71:A8 stable=unproven (a new network is made on every create)`.
- Bring-up 2: `group identity: a new network on every create (DIRECT-8H-Navegadortz2)` →
  `… ssid=DIRECT-8H-Navegadortz2 persistent=no (temporary) asked=temporary matchesRequest=yes …`.
- Both `group identity:` lines say "a new network on every create"; the two SSIDs differ
  (`DIRECT-PB-…` vs `DIRECT-8H-…`); the read-back says `asked=temporary` and `persistent=no
  (temporary)` on both. `createGroup SUCCESS` = 1 per bring-up. ✓
- (Bring-up 1's suffix was the `HeadUnit` default rather than `Navegadortz2` — `AapService.wifiDirectName`
  had not populated yet on the first launch after the APK install; irrelevant to the two-names-differ
  check.)
- Key restored (`wifi-direct-stable-identity` deleted = default `true`) before R4.

## Anything the brief did not ask about

- The persistent group's BSSID in `p2p_supplicant.conf` is written as the fixed
  `p2p_device_persistent_mac_addr` (`42:60:10:2e:b9:3c`), but the group interface that actually comes
  up carries a *different*, freshly-randomised address every time (resolved by the app via the IPv6
  link-local rung). So the stored profile and the on-air BSSID disagree by design on this unit — which
  is exactly the hazard `GroupIdentityStabilityPolicy` exists to catch, and it catches it.
- **The 60-second join watchdog falls back to `standardCreateGroup` (framework profile), which brings
  up whatever the platform's stored profile is** — in R4b that was the *older* `DIRECT-TQ-Navegadortz2`
  (`netId 0`), not the branch's `DIRECT-WC` (`netId 1`). So when a phone never joins, the kept-identity
  guarantee is silently dropped by the watchdog's fallback path. Two consecutive `DIRECT-TQ` groups
  appeared in R4b's churn. Probably worth having the watchdog re-use `chooseNativeGroupIdentity()`
  rather than the two-arg `createGroup`.
- **STA Rx rate collapses to 6 Mbit/s while a P2P group is up** (`Rx Link speed: 6Mbps` in every
  station read during a group, vs `433Mbps` at rest) — same single-radio MCC cost the `five-ghz-channel`
  round 2 recorded; the association never drops.
- R4's `WifiVersionResponse … status=NO_SUPPORTED_WIFI_CHANNELS(-8)` — Gearhead 17.5 rejects the
  version-exchange channel list on the WiFi Direct path; consistent with the `wpp-over-tcp` rounds'
  finding that the WPP control channel is effectively inert on this Gearhead build. It does not stop
  the endpoint being *stored* (R4b), only *pinged*.
