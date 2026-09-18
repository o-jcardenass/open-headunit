# native-aa-recovery-identity-and-speed - round 2 results

**Candidate:** `fix/native-aa-recovery-identity-and-speed` @ `7d1f93f5` (the brief names `583729a6`;
the branch head is one commit further on, `7d1f93f5` "Settings: every switch waits for Save", a
settings-UI-only change - see Setup notes). 6 commits on `main` `12706e26`.
**Baseline:** the same branch at `b5617bd5` (round 1's candidate; round 2's brief measures exactly
the two commits `74eb8414` + `583729a6`, plus the cosmetic `7d1f93f5`).
**APK md5:** candidate `96c326cc64be6c3fcf3b35d743694cb5` / baseline `be317bd5db95f4fd2a8b3d757bf295e6`
(both versionCode 106, versionName 3.3.2).
**Build stamp (`git rev-parse --short=12 HEAD`, embedded, verified in DEX):** candidate
`7d1f93f544f0`, baseline `b5617bd5527c`. No `-dirty` on either.
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, 1440x720, adb shell uid 0. Phone for
R1-R10: POCO X3 NFC (`4f4027e9`, BT `DC:B7:2E:5E:4E:59`), Gearhead `17.5.663214`.
R11 pairing: POCO X3 NFC as head unit / Moto edge 30 neo (`ZY22GC3BM4`, BT `A0:46:5A:97:E4:95`) as phone.
**Date:** 2026-09-09

---

## Overall

| Run | Verdict | One line |
|---|---|---|
| R0 | **PASS** | candidate 1466/0, baseline 1450/0 (round 1). New cases reconcile: P2pCreateWedgePolicyTest 14, NativeRefreshPolicyTest 10, P2pIdentityRotationPolicyTest 11, EarlyWakePolicyTest 14. md5s + stamps differ. |
| R1 | **PASS** | 3/4 sessions (c2/c3/c4 clean); the miss (c1, first launch after the phone's Wi-Fi was switched on) was a phone-side P2P-association failure with every head-unit step correct and all 6 new zero-count lines at 0. `creating`→`SSL`: 9.0 / 9.7 / 10.7 s. |
| R2 | **PASS** | wake trigger fires **before** the first credentials on every clean run (-0.13 / -1.22 / -0.21 s). Round 1 baseline control: +0.007 s (after). |
| R3 | **PASS** | exactly one `wake poke starting` per clean bring-up (3/3); `a wake poke is already running for these credentials` 2-3×. |
| R4 | **PASS** | one `creating the group 100ms after the stand-down`, N = 100 ms; one `createGroup SUCCESS`; one `p2p-wlan0-N` per run. |
| R7 | **not exercised** | `a group this unit accepted` / all wedge lines = 0 across all 6 captures; no `createGroup … BUSY` stall. Acceptable per brief; `74eb8414` stays JVM-covered (P2pCreateWedgePolicyTest 14, NativeRefreshPolicyTest 10). |
| R10 | **PASS (candidate)** / baseline half **INCONCLUSIVE** | Candidate: one early wake → one credential-less `Handshake failed` at 60.8 s → `the early wake brought the phone back to no network …` 1 ms later → **zero** further pokes for the whole ~3-min Wi-Fi-off window → session ~10 s after Wi-Fi on. The baseline did **not** run away either (loop already self-limits on this rig via `97430ddf`), so the divergence the brief predicts is not measurable here. |
| R11 | **PASS** | POCO-head-unit / Moto-phone: `5GHz createGroup SUCCESS!` (one chain), `Incoming connection detected` → `SSL handshake complete` at ~12 s. `BUSY`=0, one 0.13 s `Group info was null`, no `p2p-wlan0-N` lines from POCO's ROM. |

---

## Setup notes

### Brief targets `583729a6`; the branch head is `7d1f93f5`
Per the transfer-branch note ("brief to test head of the branch is `7d1f93f5` with only a cosmetic
fix"), the candidate built and tested is `7d1f93f5` - one commit past the brief's `583729a6`. That
commit ("Settings: every switch waits for Save") only changes the in-app settings UI (switches wait
for a Save press). This round writes `settings.xml` directly with the app force-stopped, so the
commit does not touch any tested path. Build stamp reads `7d1f93f544f0`, not `583729a6…`.

### Scripts
`hur-wifi-test-scripts/` inventory taken at round start. Used:
- `build_hur.sh` - candidate APK only. Baseline (`b5617bd5`) was **not rebuilt**: round 1's
  `round-native-aa-recovery-r1/candidate-b5617bd5.apk` is byte-identical (md5
  `be317bd5db95f4fd2a8b3d757bf295e6`, matches round 1's recorded value), so round 1's 1450/0 count
  carries over. Both APKs copied into `round-native-aa-recovery-r2/` immediately (build_hur.sh
  `rm`s the previous one).
- `run_unit_tests.sh` - candidate 1466/0 (parsed from `app/build/test-results/`).
- `set_hu_settings_host.py` - the two settings writes on the MT50 (rooted; restores owner/mode).
- `native_aa_recovery_r1.sh` (round 1's) - R1-R4/R7 bring-ups, `RECIPE=radios-on`. Its post-`exit`
  settle was bumped 4 s → 10 s (round 1's own note said it should be; the file still had 4).
- `native_aa_recovery_r10.sh` - **added this round**, left in `hur-wifi-test-scripts/`. The R10
  Wi-Fi-off flow: force-stop, HU `svc wifi disable`, launch, 4-min untouched capture, `svc wifi
  enable` (+ up to 3 nudges per §7a), wait ≤3 min for SSL.
- `adb install -r -d` + `md5sum $(pm path)` on every arm switch.

### settings.xml delta vs the fresh backup (`round-native-aa-recovery-r2/settings-backup-HU.xml`)
Round wrote on the MT50: `native-driver-selection-mode` 2→0, `log-level` 2→0 (VERBOSE). Already
correct and untouched: `wifi-connection-mode`=3, `native-ap-transport`=0, `wifi-5ghz-channel`=0,
`native-poke-bt-macs`={`DC:B7:2E:5E:4E:59`}. `wifi-direct-stable-identity` absent → default `true`
(not used this round). `wifi-direct-group-name`/`-passphrase` left at round 1's restored values
(`DIRECT-TX-Navegadortz2` / `OQWFqrnSXsM8`).

### R1c1 - the one non-session
First bring-up after the phone's Wi-Fi was enabled from a disabled state (POCO's Wi-Fi was off at
round start). The head unit did everything right: `5GHz createGroup SUCCESS!`, `Group owner address:
192.168.49.1 at p2p-wlan0-17`, `Phone ready for WiFi association. Delivering credentials...`, and
the handoff-settling guard correctly held the poke back (`Handoff still settling - not starting a
poke that would compete with the phone's WiFi association`). The phone ACKed Type 3 over Bluetooth
twice but never associated onto the P2P group - no `WirelessServer: Incoming connection detected`,
the "stuck obtaining IP address" phone-side failure. c2/c3/c4 (same phone, Wi-Fi already warm) all
formed a session cleanly. All 6 new zero-count lines were 0 even on this degraded run. Counted as a
phone/rig flake, not a candidate regression.

---

## R0 - build gate

**PASS**

- Candidate `testGithubDebugUnitTest`: **1466 / 0**. New/changed case counts:
  `P2pCreateWedgePolicyTest` 14, `NativeRefreshPolicyTest` 10, `P2pIdentityRotationPolicyTest` 11,
  `EarlyWakePolicyTest` 14 - matches the brief's "14 / 10 / 11 / 14 total" exactly.
- Baseline `b5617bd5`: **1450 / 0** (round 1; APK byte-identical by md5, not rebuilt).
- APK md5: candidate `96c326cc64be6c3fcf3b35d743694cb5`, baseline `be317bd5db95f4fd2a8b3d757bf295e6`.
- Build stamp (DEX string): candidate `7d1f93f544f0`, baseline `b5617bd5527c`.
- DEX symbol counts (`unzip -p classes*.dex | strings | grep -cE`): `P2pCreateWedgePolicy`
  candidate 11 / baseline 7 (the `74eb8414` change); the other five policy classes identical
  between arms (`NativeRefreshPolicy` 7/7, `EarlyWakePolicy` 3/3, `P2pIdentityRotationPolicy` 6/6,
  `GroupIpResolutionPolicy` 3/3, `StationStandDownSettlePolicy` 7/7, `P2pPersistentGroupPurge`
  22/22). The build stamp is the load-bearing discriminator, as the brief says.

---

## R1 - an ordinary Native AA session still forms, and how long it takes

**PASS** - a session formed on 3 of 4 candidate bring-ups; the miss is a phone-side association
failure (see Setup notes), not a head-unit regression.

- Settings: `wifi-connection-mode`=3, `native-ap-transport`=0, `native-driver-selection-mode`=0,
  `log-level`=0, `wifi-5ghz-channel`=0, `native-poke-bt-macs`={`DC:B7:2E:5E:4E:59`}.
- Radio state: `RECIPE=radios-on` - phone BT+Wi-Fi enabled by the script each run; HU force-stopped
  then one `am start`.
- Discard-rule check: clean on all 4. `createGroup SUCCESS` = 1 every run; `SSL handshake complete`
  = 1 on c2/c3/c4, 0 on c1; one benign `MATCH! Starting AapService` per run (phone's own BT
  reconnect, zero group churn attached).

| Run | `AapService creating` | `Incoming connection detected` | `SSL handshake complete` | **creating→SSL** | **creating→Incoming** |
|---|---|---|---|---|---|
| c1 | 10:33:17.772 | - (none) | - (none) | **FAIL** | - |
| c2 | 10:35:55.397 | 10:36:04.165 | 10:36:04.413 | **9.02 s** | 8.77 s |
| c3 | 10:36:37.719 | 10:36:47.180 | 10:36:47.400 | **9.68 s** | 9.46 s |
| c4 | 10:37:53.758 | 10:38:04.231 | 10:38:04.455 | **10.70 s** | 10.47 s |

Candidate mean (c2-c4) 9.80 s / 9.57 s. Round 1's `main` (`12706e26`) control stands: 18.68 /
16.75 / 8.11 s, mean 14.5 s - the candidate is directionally faster, as in round 1. Reported as a
measurement, not a criterion; no run retried for a better number (c1 was followed by an extra run,
c4, because it produced no session at all; c4 also passed).

**Per-capture zero-counts (brief §6 R1-R4): all six = 0 on every candidate capture incl. c1** -
`the stuck group creation was cancelled`, `the create that never formed`, `this unit accepts a
group request and never forms`, `refresh: a group was accepted`, `the early wake brought the phone
back to no network`, `not waking the phone again`.

---

## R2 - the wake happens while the group forms (regression guard)

**PASS**

`NativeAA: waking the phone while the WiFi group is still forming.` - exactly one per bring-up, and
it precedes the first `SUCCESS - Providing credentials to listener.` every clean run:

| Run | wake line | first `Providing credentials` | signed gap |
|---|---|---|---|
| c2 | 10:35:55.799 | 10:35:55.930 | **-0.131 s** |
| c3 | 10:36:38.125 | 10:36:39.347 | **-1.222 s** |
| c4 | 10:37:54.157 | 10:37:54.369 | **-0.212 s** |

Round 1's baseline control: the wake is `triggerPoke() delay starting (2s)…` and fires **+0.007 s
after** the first credentials. The candidate line does not exist on the baseline.

---

## R3 - the early wake is not thrown away when credentials land

**PASS**

`NativeAA: wake poke starting (listeners ready after Nms).` = **1** per clean bring-up (c2/c3/c4),
N = 0 ms each. `NativeAA: a wake poke is already running for these credentials` = 2 (c2, c3), 3
(c4) - the credential re-deliveries being refused, not restarting the loop.

(c1, the non-session, showed 3 `wake poke starting` - the poke loop correctly re-arming after each
hold+gap because no session ever formed. That is the loop working as designed on a stuck bring-up,
not the R3 violation, which is about a single healthy bring-up.)

---

## R4 - the stand-down still produces exactly one group

**PASS**

- `WifiLauncherNative: creating the group 100ms after the stand-down (still joined=…)` - once per
  bring-up, **N = 100 ms** every run.
- `StationStandDown: this unit has left its WiFi network.` present every run.
- `createGroup SUCCESS` = **1** per bring-up; one `p2p-wlan0-N` index per run (17 / 18 / 19 / 20,
  monotonic across the four runs - no in-run double).
- No run showed two create chains.

---

## R7 - the create wedge (watch item)

**not exercised** - acceptable per the brief.

Across all six R2 captures (R1c1-c4 candidate, R10c candidate, R10b baseline):

| Line | count, every capture |
|---|---|
| `a group this unit accepted` | 0 |
| `this unit accepts a group request and never forms the group` | 0 |
| `the create that never formed asked for a band` / `named the group` | 0 |
| `refresh: a group was accepted` | 0 |
| `the stuck group creation was cancelled` | 0 |
| `createGroup … BUSY` with a ~2-minute stall | 0 |

The only BUSY seen is `Native AA removeGroup before recreate failed (reason=BUSY…)` at Wi-Fi
re-enable in R10 - the expected startup BUSY when no group exists (CLAUDE.md), a group formed
~0.7 s later. Nothing to check the cancel/retry-differently sequence against; the `74eb8414`
wedge-handling change stays covered by `P2pCreateWedgePolicyTest` (14 cases) and
`NativeRefreshPolicyTest` (10).

---

## R10 - the early wake stops when the phone comes back to nothing

**Candidate: PASS.** The baseline arm did **not** reproduce the runaway re-poke on this rig - see
the note at the end of this section - so the differential the brief predicts is not measurable here;
the candidate's own PASS criteria are all met.

- Settings: as R1. HU Wi-Fi turned **off** (`svc wifi disable`, confirmed `Wi-Fi is disabled`);
  HU Bluetooth stayed on (`enabled: true`). Phone BT+Wi-Fi on and settled.
- Flow: launch at 10:39:21 with Wi-Fi off → 240 s untouched → `svc wifi enable` at 10:43:21 → wait.
- Discard-rule check: `createGroup SUCCESS` = 1 (only after Wi-Fi on); `MATCH! Starting AapService`
  = 2, both benign (one at 10:39:28.653 during the credential-less handshake, when Wi-Fi was off so
  no group could be created - exactly the case the brief's discard-note calls out; one at
  10:43:24.311 after Wi-Fi returned).

**Decisive sequence (candidate), quoted:**
```
10:39:21.???  (app launched, HU Wi-Fi off)
10:39:23.956  NativeAA: waking the phone while the WiFi group is still forming.        (×1)
10:39:28.043  NativeAA: Calling socket.connect() for POCO X3 NFC via HFP-AG …
10:39:28.860  NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) …
10:39:39.449  NativeAA: Still waiting for credentials after 10s. Requesting WiFi refresh...
   … 20s / 30s / 40s / 50s …
10:40:29.663  NativeAA: Handshake failed - No WiFi credentials available after 60s wait.
10:40:29.664  NativeAA: the early wake brought the phone back to no network; not waking it
              again until the WiFi group exists.
   -- 10:40:29.664 → 10:43:21 (Wi-Fi still off): ZERO further Calling socket.connect(),
      ZERO further Connection accepted from --
10:43:21       svc wifi enable
10:43:22.763  WifiDirectManager: 5GHz createGroup SUCCESS!
10:43:22.929  WifiDirectManager: SUCCESS - Providing credentials to listener. SSID=DIRECT-TX-Navegadortz2
10:43:22.947  NativeAA: Calling socket.connect() for …            (wake restarted after credentials)
10:43:24.940  NativeAA: Connection accepted from …
10:43:31.364  WirelessServer: Incoming connection detected from /192.168.49.50
10:43:31.581  Handshake: SSL handshake complete
```

**Numbers (candidate):**
- Wi-Fi-off window (launch → 10:43:21): `Connection accepted from` = **1**, `Handshake failed - No
  WiFi credentials` = **1**.
- 60 s wait wall-clock: `Connection accepted` 10:39:28.860 → `Handshake failed` 10:40:29.663 =
  **60.80 s** (HU screen on; within a second of 60, as the brief expects). The five `Still waiting
  for credentials after Ns` lines land at +10.6 / +20.6 / +30.7 / +40.7 / +50.8 s.
- Time from the failure line to the next `Calling socket.connect()`: **none before step 5.** First
  post-failure `socket.connect()` is 10:43:22.947, i.e. 1.9 s *after* `svc wifi enable` and *after*
  `createGroup SUCCESS` + `Providing credentials`.
- `not waking the phone again before the WiFi group exists` = **0** (nothing attempted a re-wake in
  the window, so the explicit refusal path was never needed - the loop simply stopped).
- After step 5: a group, `Providing credentials`, one `wake poke starting` + one `Calling
  socket.connect()` (the wake restarting on credentials, which is the criterion), then a full
  session - `SSL handshake complete` **10.2 s** after `svc wifi enable`.
- `wake poke starting (listeners ready after` = **2** total (one WiFi-off early wake, one post-credentials).

**Baseline (`b5617bd5`) - the runaway loop did not reproduce on this rig.**

The brief predicted the baseline would keep the loop running: "a second `Calling socket.connect()`
within about 30 s of the failure, a second `Connection accepted` and a second 60 s failure inside
the four minutes." It did not happen. Run: launch 10:45:04 (Wi-Fi off), `svc wifi enable` 10:49:05.

```
10:45:05.178  NativeAA: waking the phone while the WiFi group is still forming.        (×1)
10:45:09.258  NativeAA: Calling socket.connect() for …
10:45:11.382  NativeAA: Connection accepted from POCO X3 NFC …
10:45:21.968  Still waiting for credentials after 10s …  (→ 20/30/40/50s)
10:46:12.165  NativeAA: Handshake failed - No WiFi credentials available after 60s wait.
   -- 10:46:12.165 → 10:49:03 (2 min 51 s, Wi-Fi still off): ZERO NativeAA poke/wake/connect
      activity of any kind. No re-poke, no retry-scheduling line, nothing. --
10:49:03.242  onWifiEnabled()  (svc wifi enable)
10:49:04.026  NativeAA: Calling socket.connect() for …   (first activity since the failure)
10:49:05.095  NativeAA: Connection accepted from …
   … group, credentials, Incoming connection detected, SSL handshake complete - a session.
```

In the four-minute Wi-Fi-off window the baseline produced **exactly** what the candidate did: one
`waking the phone`, one `Calling socket.connect()`, one `Connection accepted from`, one
`Handshake failed - No WiFi credentials` (60.0 s wall: 10:45:11.382 → 10:46:12.165), then silence.
`Connection accepted from` = 1 and `Handshake failed` = 1 in the window on **both** arms (the
count-of-2 in the full-capture totals is the second, post-Wi-Fi-on connection on each arm).

The only arm difference observed:
- **candidate** emits `NativeAA: the early wake brought the phone back to no network; not waking it
  again until the WiFi group exists.` 1 ms after the failure, then `Handshake stage
  AWAIT_CREDENTIALS -> FAILED`. The **baseline** has no such line - it goes straight to `Handshake
  failed - no WiFi credentials to hand the phone.`
- `not waking the phone again before the WiFi group exists` (the explicit refusal of a second empty
  wake) = **0 on both arms** - nothing on this rig tried a second empty wake, so the guard was
  never exercised.

Why: `b5617bd5` already carries commit `97430ddf` ("recover a wedged create and a **stopped
poke**"), which bounds the early-wake poke loop. On this rig that existing bound already stops the
loop after the first failed handshake, so `583729a6`'s dedicated "stop the early wake after a
credential-less handshake" is belt-and-braces here rather than the thing that stops the loop. The
reporter's KitKat/Broadcom stack, where the loop ran every 30 s indefinitely, is the population the
new commit targets and this rig is not it. **R10's candidate half is a clean PASS; the
baseline-divergence half is INCONCLUSIVE on this rig** (the rig cannot produce the baseline's bad
behaviour - the same class of limitation round 1 hit on R7).

**Log-level corroboration (brief §2a):** the two lines the brief says were promoted DEBUG→INFO
show that promotion on hardware. `WifiDirectManager.releaseNativeCreateWindow` ("the claimed create
window is released" family) and `Native AA removeGroup before recreate failed (reason=BUSY…)` log
at **`I/`** on the candidate and **`D/`** on the baseline (both captures were VERBOSE so both are
visible; the level moved).

---

## R11 - phone to phone

**PASS**

- Head unit: POCO X3 NFC (`4f4027e9`), candidate APK `96c326cc…`, settings already at the round's
  values (`wifi-connection-mode`=3, `native-ap-transport`=0, `native-driver-selection-mode`=0,
  `log-level`=0, `wifi-5ghz-channel`=0, `native-poke-bt-macs`={`A0:46:5A:97:E4:95`} - all survived
  the `-r -d` install, nothing written this round).
- Phone: Moto edge 30 neo (`ZY22GC3BM4`), BT+Wi-Fi enabled by the script, radios settled. The
  POCO↔Moto bond is `BOND_TYPE_PERSISTENT` on both sides (earlier `PAGE_TIMEOUT` ACL failures were
  only because the Moto's BT was off before the round).
- One clean bring-up from force-stop; captured 300 s (session formed at ~12 s).
- Discard-rule check: clean. `createGroup SUCCESS` = 1, one create chain (`Attempt 0`), one
  `SSL handshake complete`, `MATCH! Starting AapService` = 0.

**Decisive sequence, quoted:**
```
10:52:28.936  AapService creating...
10:52:29.068  WifiDirectManager: a Native AA group create is claimed (recreating the group)
10:52:29.101  NativeAA: ACTIVELY LISTENING on Android Auto UUID (4de17a00-…) on radio [POCO X3 NFC]
10:52:30.093  WifiDirectManager: Attempting createGroup for Native AA (Attempt 0)...
10:52:30.113  WifiDirectManager: 5GHz createGroup SUCCESS!
10:52:30.124  WifiDirectManager: Group info was null! Retrying in 1s (Attempt 1/20)...
10:52:30.258  WifiDirectManager: SUCCESS - Providing credentials to listener. SSID=DIRECT-MW-Navegadortz
10:52:30.342  NativeAA: Calling socket.connect() for motorola edge 30 neo via HFP-AG …
10:52:32.397  NativeAA: Connection accepted from motorola edge 30 neo (A0:46:5A:97:E4:95) …
10:52:40.333  WirelessServer: Incoming connection detected from /192.168.49.48
10:52:40.644  Handshake: SSL handshake complete
```

**Numbers the brief asked for (this capture):**
- R1 timings: `AapService creating` → `SSL handshake complete` = **11.71 s**; → `Incoming
  connection detected` = **11.40 s**.
- R2: `waking the phone while the WiFi group is still forming.` fires ~1.2 s **before** the first
  `Providing credentials` (10:52:29.1 vs 10:52:30.258). R3: `wake poke starting` = 1, `a wake poke
  is already running` = 2. Same clean shape as the MT50.
- R7 watch: `a group this unit accepted` / `the stuck group creation was cancelled` / `the create
  that never formed` / all wedge lines = **0**.
- `BUSY` = **0** (none, not even the startup `removeGroup` BUSY the MT50 shows - POCO's WiFi was
  not joined to a station network at launch).
- `Group info was null` runs = **1**, length ~0.13 s (10:52:30.124 → 10:52:30.258, one
  `Attempt 1/20` then credentials). No 20-attempt spiral.
- `p2p-wlan0-N` indices: **none in the capture** - POCO's ROM does not log the P2P group interface
  by that name at this level. Not a defect; just no interface-index signal from this device. The
  group is real (`5GHz createGroup SUCCESS!`, credentials with `IP=192.168.49.1`, phone joined on
  `192.168.49.48`).

**What the POCO P2P stack did that the MT50's did not** (brief §9 for R11):
- No `StationStandDown` / `WifiLauncherNative: creating the group` step - POCO was off-network at
  launch, so it went straight to `a Native AA group create is claimed (recreating the group)` and
  `Attempting createGroup … (Attempt 0)`. The MT50 always stands down from `Pegue Cdesta` first.
- `MATCH! Starting AapService` = 0 - POCO's `AutoStartReceiver` did not fire off the phone's BT
  reconnect the way the MT50's does every run.
- SSID scheme `DIRECT-MW-Navegadortz` (vs the MT50's `DIRECT-TX-Navegadortz2`).
- Session formed in ~12 s, comparable to the MT50 candidate runs (~9-11 s).

Video: not a criterion (brief). A Moto in portrait cannot foreground the projection activity so
the phone side withholds video; the SSL handshake is healthy and the audio channels run. PASS.

---

## Anything the brief did not ask about

1. **`native-ap-transport=0` on the POCO head unit really does take the WiFi-Direct path.** An
   older note in this channel (usb-aoa findings) had "POCO mode-3 = SoftAP, no `createGroup`". With
   `native-ap-transport` explicitly 0 the POCO ran a normal `5GHz createGroup SUCCESS!` /
   `p2p`-owner-`192.168.49.1` group in R11 - no SoftAP, no 5288-bind proxy. The earlier
   observation was probably a different `native-ap-transport` value.

2. **The DEBUG→INFO promotion in `74eb8414` is confirmed on hardware.**
   `WifiDirectManager.releaseNativeCreateWindow` and `Native AA removeGroup before recreate failed
   (reason=BUSY…)` log at `I/` on the candidate, `D/` on `b5617bd5`. (Brief §2a predicted it; both
   captures were VERBOSE so both show the lines, but the level moved.)

3. **The early-wake poke loop already self-limits on this rig without `583729a6`.** See the R10
   baseline note. `b5617bd5` (which carries `97430ddf`'s "stopped poke" recovery) goes silent for
   the full ~3-minute dead window after one credential-less handshake failure. The reporter's
   "poke every 30 s forever" is not reproducible on the MT50 + POCO pairing; the rig's poke loop
   stops after the first failed handshake regardless of arm.

4. **R1c1's phone-side stall (no P2P association despite credentials delivered) recurred exactly
   as CLAUDE.md describes it** - `Phone ready for WiFi association. Delivering credentials…`, the
   handoff-settling guard correctly holding the poke, Type 3 ACKed over Bluetooth twice, and no
   `Incoming connection detected` ever. It cleared on the next attempt with no code or config
   change. Worth remembering that "credentials delivered + Type 3 ACKed" is not "session forming".

5. **`Still waiting for credentials after Ns` fires on a strict 10 s cadence** (measured +10.6 /
   +20.6 / +30.7 / +40.7 / +50.8 s from `Connection accepted` in R10), and `Handshake failed` at
   +60.8 s - so the "60 s wait" is 60 s of 10 s polls plus one poll interval, wall-accurate to
   within a second with the screen on, on both arms.
