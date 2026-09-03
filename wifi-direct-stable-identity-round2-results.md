# wifi-direct-stable-identity — round 2 results

**Candidate:** `fork/feat/wifi-direct-stable-identity` @ `f4c32678` (4 commits on `ef74866c`), `3.3.0`
**Baseline:** `fork/feat/native-aa-wpp-tcp-and-hfp-link` @ `ef74866c` (round 1's APK, still valid)
**APK md5:** candidate `4001b0dc60019b8dc226930a1655e889` / baseline `54584d347ffe9e4ba7d31149c541fa16`
**Unit:** D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, single BT radio. D-POCO = POCO X3 NFC (`M2007J20CG`), Android 15, Gearhead 17.5.663204 (R2 only).
**Date:** 2026-09-01

## Headline

- **R0 PASS** — candidate **1170 / 0** exact, `P2pGroupIdentityPolicyTest` **16**, other three counts unchanged (5, 10, 10); the three new DEX symbols present on candidate, 0 on baseline; candidate md5 differs from round 1's.
- **R1 PASS** — the give-up fires exactly as briefed. Bring-up 2 prints the warning line verbatim and reads `stable=no`; bring-ups 3 and 4 draw two different temporary networks (`DIRECT-BU`, `DIRECT-2I`) with `asked=temporary` / `persistent=no (temporary)` and leave the kept name+passphrase untouched; deleting the flag and bringing up a fifth time returns to `asking for the kept network DIRECT-MR`. `createGroup SUCCESS` = 1 on every one of the five.
- **R3 PASS on the fix it targets** — the join watchdog's no-band fallback now **names the group** (`standard createGroup as DIRECT-XX-Navegadortz2, band left to the platform.`) and **never resurrects the stale `DIRECT-TQ` / `DIRECT-WC` framework profile** that round 1's R4b caught it bringing up. No `refused a named group` line. The brief's literal sub-conditions (every group named `DIRECT-MR`, `asked=persistent`) do **not** hold, for a reason the brief did not model — see the deviation note under R3.
- **R4 PASS** — simulated first launch draws `DIRECT-6J-Navegadortz2`; suffix is `Navegadortz2`, not `HeadUnit`.
- **R2 FAIL / INCONCLUSIVE, stopped by operator direction.** The candidate arm formed all four sessions but the **~10 s reconnects round 1 saw are still there, unchanged** (initial 3.6 s, reconnect 1 = 10.1 s, reconnect 2 = 10.1 s, reconnect 3 = 2.0 s) even though every group is now a brand-new name the phone has never seen — so round 1's hypothesis that the kept SSID caused them is refuted, and the give-up does not buy a faster reconnect on this rig. The **baseline arm formed no session at all** across two full attempts (plus a HU+phone Bluetooth recovery between them), where round 1's R2 formed 4/4 on the same rig, phone and APK. The operator, watching the rig, called the baseline-connection failure a regression from round 1 and directed that the round be stopped and reported here rather than chased further. This report does not isolate code-regression from rig degradation — see R2.

## Shipping read

- **The give-up mechanism (commit 4) works** — R1 proves it end to end, including the app persisting `wifi-direct-group-address-moves=true` itself this round (the prefs file was app-writable, unlike round 1), which R1 bring-up 5 confirms by re-deciding after the key was hand-deleted.
- **The watchdog-fallback fix (commit 4) works** — R3/R3b: the no-band `standardCreateGroup` fallback names the group and the round-1 R4b regression (falling back to the stale `DIRECT-TQ` framework profile) does not reproduce. Zero `DIRECT-TQ` / `DIRECT-WC` in either R3 capture.
- **The reconnect-speed benefit is still not demonstrable on this rig**, and round 2 adds a data point round 1 did not have: with the kept name dropped and a never-seen SSID on every create, the ~10 s reconnects persist unchanged. The delay is the phone's, and it tracks the phone's `WIRELESS_WIFI_CACHED_CREDENTIALS_USED` → `..._INVALID` cycle (keyed to the head unit's Bluetooth identity, not the SSID — round 1's finding 2), which nothing in this branch touches. Route the reconnect-speed question to `GroupIdentityStabilityPolicyTest` / `P2pGroupIdentityPolicyTest` and a BSSID-stable unit, as round 1 already recommended.
- **The baseline-arm connection failure needs a clean-rig re-check before it is read as a parent regression.** By the time the baseline arm ran, the P2P interface index was at `p2p-wlan0-30` — about 30 group creates in one uninterrupted session (`p2p-wlan0-0` at the day's first R1 bring-up) — and §7a records this rig's P2P and HeadsetClient stacks degrading under repeated group/adapter cycling and sometimes needing more than one recovery pass. The candidate arm ran earlier in the same session (`p2p-wlan0-16..20`) and was clean. A baseline re-run after a WiFi restart on a fresh session is the way to tell a real `ef74866c` regression from stack exhaustion; that was not done because the round was stopped.

## Setup notes

### Deviations from the brief and the protocol

- **The prefs file was app-writable this round, where round 1 found it root-owned.** `set_hu_prefs.sh` chowns `settings.xml` back to the uid it stat'd at the start; this round that uid was `u0_a168` (the app), so the app's own writes of `wifi-direct-group-address-moves` and `wifi-direct-last-group-*` reached disk. The brief (written for round 1's root-owned rig) says "on this rig its write fails, so now write ... by hand"; the hand-write was still done at each briefed point, but R1 bring-up 5 shows the app would have persisted the flag itself (it was hand-deleted before b5, the warning fired, and the key was present again afterward). For R3 the file was deliberately chowned `root:root 644` to restore round 1's rig condition; it made no difference — SharedPreferences writes via temp-file + rename and the `shared_prefs/` directory is app-owned, so the flag still persisted. Both R3 captures (`hu_r3.txt` app-owned, `hu_r3b.txt` root-owned) show the identical behaviour.
- **`dumpsys wifip2p` still redacts `networkName` / `passphrase`** (round 1's note). Bring-up 1's drawn pair was read as root from `/data/vendor/wifi/wpa/p2p_supplicant.conf`: `ssid="DIRECT-MR-Navegadortz2" psk="CdKW0JXmKneU"`, seeded by hand before bring-up 2. `matchesRequest=yes` on every later read-back independently confirms the hand-written key is the one on the air.
- **R2 was not completed.** The candidate arm was run twice: the first attempt (`hu_r2_candidate.txt`) was contaminated by round 1's R4b stale WPP-over-TCP endpoint still on the POCO (`Trying to start WPP on TCP with configuration: ... ssid=DIRECT-WC-Navegadortz2, bssid=EA:BD:E2:D6:29:9C, ... port=5299` × many, `NETWORK_NOT_FOUND` loop, zero sessions). Round 1's cleanup note claimed a Gearhead `force-stop` cleared that endpoint; it did not — it survived force-stops and several days. The operator forgot the head unit in the POCO's Android Auto settings mid-round (a UI step, not scriptable without `pm clear`), after which the second candidate attempt (`hu_r2_candidate2.txt`) was clean (`No WPP on TCP configuration found in storage` × 54, `Trying to start WPP on TCP` × 0). The baseline arm was then run twice (`hu_r2_baseline.txt`, then `hu_r2_baseline2.txt` after a HU+phone Bluetooth recovery); neither formed any session. The round was stopped there by operator direction.
- **`settings.xml` restored byte-identical** to the round backup (`diff` clean), all five identity keys absent, `wifi-5ghz-channel` back to `0`, `static-bssid` `0`, `native-wifi-version-exchange` `false`. Candidate `4001b0dc...` left installed and confirmed live. Both phones' radios on. The POCO's head-unit record was forgotten by the operator during R2 (cleaner than round 1 left it — the R4b `port=5299` endpoint is gone).
- The only `settings.xml` delta the round wrote was `wifi-5ghz-channel` `0` → `36` (brief §3, same as round 1), reverted at restore.

### Scripts

- `build_hur.sh`, `run_unit_tests.sh` — unchanged, candidate build + tests.
- `wds_bringup.sh`, `wds_reconnect.sh` — round 1's scripts, unchanged, run with `OUTDIR=round-wifi-direct-stable-identity-r2`. `wds_bringup.sh` was used for R1 (×5), R3, R3b and R4 at various settle times; `wds_reconnect.sh` for R2.
- No new scripts.

## R0 — build gate and unit tests

**PASS**

- Candidate: **1170 / 0** exact. `P2pGroupIdentityPolicyTest` **16** (round 1 was 15), `NativeRefreshPolicyTest` 5, `GroupIdentityStabilityPolicyTest` 10, `WppEndpointPolicyTest` 10.
- DEX symbols on candidate: `onStandardCreateSucceeded` 3, `resetWifiDirectIdentityMeasurement` 2, `wifi-direct-group-address-moves` 1. All 0 on baseline.
- Candidate md5 `4001b0dc60019b8dc226930a1655e889` ≠ round 1's `b891061428dac2a8aa16b8cc3b80e958`. Baseline md5 `54584d347ffe9e4ba7d31149c541fa16` matches round 1's on record.

## R1 — the give-up (candidate, head unit only)

**PASS**

- Settings: `wifi-5ghz-channel=36`, all else the round baseline. Identity keys seeded by hand between bring-ups per §3.
- Radio state: POCO Bluetooth + WiFi **off** throughout. D-HU joined to `Pegue Cdesta` @ 5260 MHz.
- Discard-rule check: clean. `createGroup SUCCESS` = 1 on every bring-up; `MATCH! Starting AapService` = 0 on every bring-up. The leading `p2p-wlan0-N` on bring-ups 2–5 is the previous group torn down at launch (the §7a benign pattern).

| # | `group identity:` line | read-back `asked=` / `persistent=` | SSID | BSSID | `stable=` |
|---|---|---|---|---|---|
| 1 | `no kept network yet, so DIRECT-MR-Navegadortz2 is drawn now and kept` | persistent / `yes (netId 3)` | DIRECT-MR | `D6:DD:5E:07:5E:E6` | `unproven (first group under this name)` |
| 2 | `asking for the kept network DIRECT-MR-Navegadortz2 again` | persistent / `yes (netId 3)` | DIRECT-MR | `A6:0C:DD:AA:A2:27` | **`no`** (moved from D6:DD:…) + **warning line** |
| 3 | `a new network on every create (DIRECT-BU-Navegadortz2), because this unit gives the group a new address every time …` | **temporary** / **`no (temporary)`** | DIRECT-**BU** | `E2:53:97:E3:47:09` | `unproven (a new network is made on every create)` |
| 4 | `a new network on every create (DIRECT-2I-Navegadortz2), because this unit gives the group a new address every time …` | **temporary** / **`no (temporary)`** | DIRECT-**2I** | `96:FF:04:FF:EF:27` | `unproven (a new network is made on every create)` |
| 5 | `asking for the kept network DIRECT-MR-Navegadortz2 again` | persistent / `yes (netId 3)` | DIRECT-MR | `2E:4F:14:A1:60:63` | `no` (measurement restarted from the kept pair) |

- **Step 2 holds.** Bring-up 2, verbatim (W-level): `WifiDirectManager: this unit gives its WiFi Direct group a new address on every create, so the kept name is dropped from the next create on: a phone spends seconds trying a saved network at an address that no longer exists before it takes the new one.` Read-back `stable=no (same name but the BSSID moved from D6:DD:5E:07:5E:E6 to A6:0C:DD:AA:A2:27; this unit re-addresses the group on every create)`.
- **Step 3 holds.** Bring-ups 3 and 4 give-up reason verbatim, two **different** temporary names (`DIRECT-BU`, `DIRECT-2I`), `asked=temporary`, `persistent=no (temporary)`, `stable=unproven (a new network is made on every create)`. Stored keys read back after b3+b4: `wifi-direct-group-name`=`DIRECT-MR-Navegadortz2`, `wifi-direct-group-passphrase`=`CdKW0JXmKneU`, `wifi-direct-last-group-ssid`=`DIRECT-MR-Navegadortz2` — untouched.
- **Step 4 holds.** Flag deleted, bring-up 5: `asking for the kept network DIRECT-MR-Navegadortz2 again`.

**One thing the brief did not anticipate, worth the coding session's eye:** `wifi-direct-last-group-bssid` was updated by the app during bring-up 2 (from the hand seed `D6:DD:…` to b2's `A6:0C:…`), then **frozen** through bring-ups 3 and 4 (give-up mode does not run the stability measurement), then advanced again at bring-up 5. So the "four stored keys must be untouched" during step 3 holds in the sense the brief means it (the kept identity — name and passphrase — is pristine), but `last-group-bssid` is a measurement-tracking key the app legitimately advances on measuring bring-ups. Not a fault; noted because the brief's wording implies all four are frozen.

## R3 — the watchdog's fallback keeps the identity (candidate, head unit only)

**PASS on the fix it targets. The brief's literal sub-conditions do not hold — see the deviation.**

Ran twice: `hu_r3.txt` (prefs app-owned) and `hu_r3b.txt` (prefs chowned `root:root 644` to restore round 1's condition). Both show the identical behaviour; the description below is `r3b`.

- Settings: four identity keys seeded (`DIRECT-MR` / `CdKW0JXmKneU` / `DIRECT-MR` / last-bssid `5E:73:EE:DF:81:41`), `wifi-direct-group-address-moves` deleted. POCO Bluetooth off. 155 s launch, then force-stop.
- Discard check: clean. `MATCH! Starting AapService` = 0. `createGroup SUCCESS` = 3 (initial + the two watchdog recreates at ~60 s and ~120 s).
- `chooseNativeGroupIdentity` called **3 times** — once per create:

| create | `chooseNativeGroupIdentity` says | group | read-back |
|---|---|---|---|
| initial (20:25:10) | `asking for the kept network DIRECT-MR-Navegadortz2 again` | `DIRECT-MR-Navegadortz2` | `persistent=yes (netId 3) asked=persistent matchesRequest=yes stable=no (BSSID moved 5E:73:… → 42:67:…)` |
| watchdog recreate 1 (20:26:10) | `a new network on every create (DIRECT-34-Navegadortz2), because this unit gives the group a new address every time …` | `DIRECT-34-Navegadortz2` | `persistent=no (temporary) asked=temporary` |
| watchdog recreate 2 / no-band fallback (20:27:11) | `a new network on every create (DIRECT-WR-Navegadortz2) …` then `standard createGroup as DIRECT-WR-Navegadortz2, band left to the platform.` → `Standard createGroup SUCCESS!` | `DIRECT-WR-Navegadortz2` | `persistent=no (temporary) asked=temporary` |

- **`grep -ac "DIRECT-TQ|DIRECT-WC"` = 0** in both captures — the round-1 R4b regression (the no-band fallback bringing up the stale framework profile) **does not reproduce**.
- `refused a named group` line = 0 — this unit does take a named group with no band request (the brief's stated FAIL signal did not fire).
- `standard createGroup as DIRECT-XX-Navegadortz2, band left to the platform.` present ×1, `Standard createGroup SUCCESS!` ×1 — the fallback names the group and it succeeds.

**Deviation — why the literal PASS ("every `group identity ssid=` names `DIRECT-MR`", the fallback read-back says `asked=persistent`) does not hold.** The brief's model is that `wifi-direct-group-address-moves` only affects the *next* app launch, so the whole 150 s session stays on `DIRECT-MR`. On this build `chooseNativeGroupIdentity()` re-reads the flag on **every** create. The initial create detects `stable=no` and persists the flag, so watchdog recreate 1 reads it and switches to give-up temporary names, and recreate 2 (the no-band fallback) carries that give-up identity (`DIRECT-WR`), not `DIRECT-MR`. This is the two commit-4 fixes interacting: the give-up (R1's subject) legitimately activates mid-session, and the watchdog fallback then correctly keeps *the identity that was chosen* (a give-up temp name) rather than resurrecting a stale profile. R3's actual question — does the fallback keep the chosen identity and avoid the stale framework profile — is answered **yes**. If the intent is that the watchdog fallback should keep `DIRECT-MR` specifically even after mid-session give-up, that is a design point for the coding session, not something the current code does.

## R4 — the suffix on a first launch (candidate, head unit only)

**PASS**

- All five identity keys deleted, `force-stop`, launch, read the first `group identity:` line.
- `WifiDirectManager: group identity: no kept network yet, so DIRECT-6J-Navegadortz2 is drawn now and kept for every later create.`
- Suffix is **`Navegadortz2`** (the P2P/device name — `settings get secure bluetooth_name` = `Navegadortz2`; `settings get global device_name` = `MT50_YT610E4GFPSL_U`), not `HeadUnit`.
- `createGroup SUCCESS` = 1, `MATCH!` = 0.

## R2 — reconnect time in the degraded state, A/B (phone needed)

**FAIL / INCONCLUSIVE — round stopped by operator direction.**

### Candidate arm (`hu_r2_candidate2.txt` / `phone_r2_candidate2.txt`)

- Settings: four keys seeded (`DIRECT-MR` / `CdKW0JXmKneU` / `DIRECT-MR` / last-bssid `66:B6:51:16:31:C2`), `wifi-direct-group-address-moves=true`, `native-wifi-version-exchange=false`. POCO Bluetooth + WiFi on. Reconnect trigger: `headunit://disconnect` + a D-HU Bluetooth-adapter cycle (round 1's method).
- Phone clean: `No WPP on TCP configuration found in storage` × 54, `Trying to start WPP on TCP` × 0 (the operator had forgotten the head unit; the first attempt `hu_r2_candidate.txt` was discarded for the round-1 R4b stale-endpoint loop — see Setup notes).
- Discard check: clean. `createGroup SUCCESS` = 4 (one per session), `SSL handshake complete` = 4, `MATCH!` = 3 (the three reconnect triggers). `p2p-wlan0-16..20`.
- All four groups were give-up temporary names (`DIRECT-YT`, `DIRECT-0P`, `DIRECT-9V`, `DIRECT-8P`), `asked=temporary` — i.e. every group had a name the phone had never seen.

| connect | `[TX] Wrote TYPE 3` | `WirelessServer: Incoming connection detected` | duration |
|---|---|---|---|
| initial | 20:29:52.066 | 20:29:55.637 | **3.57 s** |
| reconnect 1 | 20:30:46.898 | 20:30:56.985 | **10.09 s** |
| reconnect 2 | 20:32:13.101 | 20:32:23.221 | **10.12 s** |
| reconnect 3 | 20:33:44.846 | 20:33:46.845 | **2.00 s** |

- Phone: `WIRELESS_WIFI_CACHED_CREDENTIALS_INVALID` = 3 (once per reconnect), each preceded by `WIRELESS_WIFI_CACHED_CREDENTIALS_USED`.
- **Reading.** These numbers are effectively identical to round 1's candidate arm (1.35 / 10.2 / 10.1 / 2.08). Round 1 attributed the two ~10 s reconnects to the phone trying a *saved network profile* pinned to the kept SSID at a now-dead BSSID. Round 2's candidate arm has **no kept SSID** — every group is a fresh `DIRECT-xx` — and the ~10 s reconnects are **still there, same pattern**. So that hypothesis is refuted: the delay is the phone burning ~8 s on its Bluetooth-identity-keyed WiFi credential cache (`CACHED_CREDENTIALS_USED` → `INVALID` → rescan) before re-associating, and the branch's give-up does not remove it. Reconnect 3 landing at 2.0 s in both rounds suggests the phone eventually stops trying the cache after two failures.

### Baseline arm (`hu_r2_baseline.txt`, then `hu_r2_baseline2.txt`)

- Baseline APK installed with `adb install -r -d`, md5 confirmed `54584d34…`. All identity keys deleted. POCO Bluetooth + WiFi on, Gearhead force-stopped.
- **First attempt:** `createGroup SUCCESS` = 5, `SSL handshake complete` = **0**. HU opens its listener (`NativeAA: ACTIVELY LISTENING on Android Auto UUID` = 1) but `NativeAA: Connection accepted from` = **0** — the phone's RFCOMM connect never reaches the socket. Phone: `WIRELESS_WIFI_PROJECTION_PROTOCOL_RFCOMM_CONNECTION_ATTEMPT_STARTING` looped ~15×, zero `RFCOMM_SOCKET_CONNECTED`, no session in ~7 min.
- **Second attempt**, after a full recovery pass (D-HU Bluetooth cycle + self-revert, POCO Bluetooth + WiFi cycle, bond confirmed both sides): identical — `createGroup SUCCESS` = 5, `SSL handshake complete` = 0, `ACTIVELY LISTENING` = 1, `Connection accepted from` = 0, `RFCOMM_CONNECTION_ATTEMPT_STARTING` × 45 with zero `RFCOMM_SOCKET_CONNECTED`. `WIRELESS_WIFI_CREDENTIALS_CACHE_NOT_FOUND` × 4 (the phone's WiFi cache had cleared this time), still no session.
- Round 1's R2 formed 4/4 sessions on `ef74866c` on this same rig, phone and APK.

### Verdict and what it does and does not establish

- **The candidate-arm measurement is valid** and answers the round's shipping question: the reconnect-speed benefit is **not present** on this rig, and round 1's kept-SSID explanation for the ~10 s reconnects is **wrong** — they persist with a never-seen SSID.
- **The baseline arm could not be run.** `ef74866c` formed no session across two attempts and a recovery pass. The operator, watching the rig, judged this a regression from round 1 and directed the round be stopped.
- **This report does not isolate a code regression from rig degradation.** By the baseline arm the P2P interface index was `p2p-wlan0-30` — ~30 group creates in one uninterrupted session (the day started at `p2p-wlan0-0`) — and §7a records this rig's P2P and HeadsetClient/RFCOMM stacks degrading under exactly that load, sometimes needing more than one recovery pass. The candidate arm ran ~10 creates earlier (`p2p-wlan0-16..20`) and was clean. A baseline re-run on a fresh session after a WiFi restart is the only way to tell an `ef74866c` regression from stack exhaustion. That was not done.

## Anything the brief did not ask about

- **The prefs file being app-writable this round changes how the give-up behaves vs round 1.** Round 1 (root-owned prefs) could only ever see the flag written by hand, so the give-up only took effect on a subsequent, deliberately-triggered bring-up. This round the app persists the flag itself the moment it reads `stable=no`, so the give-up takes effect on the *very next create* — including a watchdog recreate 60 s later inside the same session (R3). Whichever ownership the shipped rig has, the coding session should decide whether mid-session give-up (dropping a name the phone may be actively trying to join when the 60 s watchdog fires) is intended, or whether the flag should only be consulted at session start.
- **Round 1's R4b stale WPP-over-TCP endpoint was still on the POCO days later.** Round 1's cleanup claimed a Gearhead `force-stop` cleared it; it did not. It took a "forget this car" in the phone's Android Auto settings. Any future round that seeds a `port=5299` advertisement on this POCO must end with a real forget-car, not a force-stop, or contaminate the next thread.
- **`NativeAA: Not poking POCO X3 NFC — this head unit already holds a Bluetooth hands-free link**, which a poke would take over and leave disconnected` fired throughout the baseline arm while the phone could not connect — the HU believed it held an HFP link to a phone that was not projecting. Consistent with §7a's "HeadsetClient after a bad disconnect" degradation.
- First credential line of the baseline arm was `SSID=DIRECT-N5-HeadUnit` — the `HeadUnit` suffix, i.e. `AapService.wifiDirectName` had not populated on that first post-install launch (round 1's R6 saw the same). Cosmetic; the R4 check for this on the candidate passed.
