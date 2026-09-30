# native-aa-recovery-identity-and-speed — round 1 results

**Candidate:** `fix/native-aa-recovery-identity-and-speed` @ `b5617bd5` (3 commits on `main` `12706e26`)
**Baseline:** `origin/main` @ `12706e26`
**APK md5:** candidate `be317bd5db95f4fd2a8b3d757bf295e6` / baseline `07fd71e39c3d12dc6b96059214f70e20` (both versionCode 106, versionName 3.3.2)
**Build stamp (`ACTION_QUERY_STATE`):** candidate `b5617bd5527c`, flavor `github`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, 1440x720, adb shell is uid 0. Phone: POCO X3 NFC (`4f4027e9`, BT `DC:B7:2E:5E:4E:59`), Gearhead `17.5.663214`.
**Date:** 2026-09-09

---

## Overall

| Run | Verdict | One line |
|---|---|---|
| R0 | **PASS** | candidate 1450/0, baseline 1414/0 (Δ36 = 8+10+4+5+9), md5s differ, 6 DEX symbols present on candidate / 0 on baseline, stamp `b5617bd5527c` |
| R1 | **PASS** | session on all 6 runs. `creating`→`SSL` mean: candidate 10.3 s (9.4 / 8.6 / 12.8), baseline 14.5 s (18.7 / 16.8 / 8.1) |
| R2 | **PASS** (on the wake-trigger signal) | candidate wakes the phone **before** the first credentials (−1.1 to −1.2 s, 3/3); baseline wakes **after** them (+0.007 to +0.009 s, 3/3). The literal `Calling socket.connect()` ordering is **INCONCLUSIVE** here — see Setup notes. |
| R3 | **PASS** | exactly one `wake poke starting (listeners ready after …)` per bring-up (4/4); credential re-delivery refused with `a wake poke is already running for these credentials` (2–3/bring-up) |
| R4 | **PASS** | one `WifiLauncherNative: creating the group` per bring-up, **N = 100 ms** every time, one `createGroup SUCCESS`, one `p2p-wlan0-N` |
| R5 | **PASS** | `[TX] Wrote TYPE 3` credential state SSID == most-recent `Providing credentials` SSID in every capture; no "group changed while Type 3 was pending" |
| R6 | **PASS** | `Waiting for IP on interface` = **0** on the candidate (owner branch taken). Also 0 on the baseline. |
| R7 | **not exercised** | `a group this unit accepted` = 0 and `createGroup … BUSY` = 0 across all 11 captures. Acceptable per the brief; the wedge fix stays JVM-covered. |
| R8 | **PASS** | candidate: poke → `the Native AA handshake servers are not running (this unit's Bluetooth was off or unavailable when the mode was armed) … Starting them before the poke.` → `ACTIVELY LISTENING` 22 ms later. baseline: poke → `Native AA listeners are closed, reopening them before the poke.` → nothing; `ACTIVELY LISTENING` only 10.7 s later via an unrelated `MATCH! Starting AapService`, and `Successfully poked` logged regardless. |
| R9 | **PASS** (API 29+ path); legacy path **UNTESTABLE** | `ACTION_ROTATE_WIFI_DIRECT_IDENTITY` → `WifiDirectManager: a new WiFi Direct identity was asked for now (NAMED_RECREATE); recreating the group.` SSID before `DIRECT-TX-Navegadortz2` → after `DIRECT-R9-OpenHeadunitRot`; phone formed a full session (video flowing) on the new network. `persistent profile purge` absent, as expected on Android 14. |

Ship signal: nothing in this round argues against merging. The speed fix is real and directionally large (see R1/R2), the two recovery fixes both behaved exactly as designed on the one that could be provoked (R8), and the identity rotation reaches the air (R9). The create-wedge fix (R7) was not reachable on this rig and rests on its JVM tests.

---

## Setup notes

### Scripts
`hur-wifi-test-scripts/` inventory taken at round start. Used:
- `build_hur.sh` — both APKs (candidate then baseline). **§7a hazard confirmed:** it `rm`s `apks/com.andrerinas.headunitrevived_*.apk` before each build, so each APK was copied to `round-native-aa-recovery-r1/` immediately after its build.
- `run_unit_tests.sh` — 1450/0 candidate, 1414/0 baseline (parsed from `app/build/test-results/`).
- `set_hu_prefs.sh` — multi-key settings writes and the end-of-round restore (rooted, restores file owner).
- `adb install -r -d` + per-run `md5sum $(pm path)` for every arm switch (13 installs total). `set_hu_pref.sh` deliberately **not** used to switch arms (§7a: it reinstalls newest-in-`apks/`).

Added this round, left in `hur-wifi-test-scripts/`:
- `native_aa_recovery_r1.sh` — one Native AA bring-up capture (clean protocol, landmark counts, p2p-index dump). `RECIPE=radios-on` (R1) or `bt-off-launch` (poke-forcing).
- `native_aa_recovery_r8.sh` — the BT-off-at-arm / poke-button flow.
- `native_aa_recovery_r9.sh` — the identity-rotation flow (writes a fresh minted pair, relaunches, fires the non-exported service action as root).

### settings.xml delta vs the fresh backup (`round-native-aa-recovery-r1/settings-backup.xml`)
Round wrote: `native-driver-selection-mode` 2→0, `log-level` 2→0 (VERBOSE). Already correct and untouched: `wifi-connection-mode`=3, `native-ap-transport`=0, `wifi-5ghz-channel`=0, `native-poke-bt-macs`={`DC:B7:2E:5E:4E:59`} (verified it survived every reinstall and did not need re-seeding), `wifi-direct-stable-identity` absent → default `true` (R9). R9 also rewrote `wifi-direct-group-name`/`-passphrase`. **All four restored at round end** (`native-driver-selection-mode`=2, `log-level`=2, group name/passphrase back to `DIRECT-TX-Navegadortz2` / `OQWFqrnSXsM8`); one `</map>`, no `.bak`. Candidate APK left installed.

### R2 — why the literal condition is INCONCLUSIVE on this rig
The brief asks for the first `NativeAA: Calling socket.connect() for` to appear before the first `Providing credentials`. It cannot on this rig: the P2P group forms in ~1.2 s (`AapService creating` → first credentials), which is *faster* than the poke loop's own spin-up from the early-wake trigger to its first `socket.connect()` (~1.27 s: `wake poke starting` at +0 ms, `Calling socket.connect()` at +1.27 s, measured R2_cand2). So `socket.connect()` lands ~0.13 s *after* credentials even though the wake was *triggered* ~1.1 s before them. In the `radios-on` runs it never fires at all — the phone's own AA reconnect (§7a) completes the handshake before the loop's first iteration, so `Calling socket.connect()` = 0 on all three candidate R1 runs and all three baseline runs.

The fix's actual intent — *the wake now starts while the group is still forming instead of after it* — is shown cleanly and repeatably by the **wake-trigger vs first-credentials** ordering, which is what the verdict rests on:

| Capture | wake line | first `Providing credentials` | signed gap |
|---|---|---|---|
| R1_run1 candidate | `waking the phone while the WiFi group is still forming.` 08:22:45.466 | 08:22:46.672 | **−1.206 s** |
| R1_run5 candidate | `waking the phone …` 08:27:46.674 | 08:27:47.845 | **−1.171 s** |
| R2_cand2 candidate | `waking the phone …` 08:32:34.298 | 08:32:35.437 | **−1.139 s** |
| R1_run2 baseline | `triggerPoke() delay starting (2s)…` 08:23:56.192 | 08:23:56.185 | **+0.007 s** |
| R1_run4 baseline | `triggerPoke() delay starting (2s)…` 08:26:41.204 | 08:26:41.197 | **+0.007 s** |
| R1_run6 baseline | `triggerPoke() delay starting (2s)…` 08:28:44.828 | 08:28:44.819 | **+0.009 s** |

(R1_run3 candidate's credentials block carried the MT50 frozen-timestamp quirk — see below — so it is omitted from this table; its wake line and `wake poke starting` count are still valid.)

`Calling socket.connect()` signed gap where it did fire: candidate R2_cand2 **+0.132 s** (after credentials). Baseline: never (0 in every capture).

### R9 — deviation from the UI trigger
The real trigger is a Settings row that, in-process, does `settings.wifiDirectGroupIdentity = P2pGroupIdentityPolicy.mint(...)` **then** `startService(AapService, ACTION_ROTATE_WIFI_DIRECT_IDENTITY)`. A live external write to `settings.xml` is not seen by the running process (SharedPreferences caches in memory), so this was replicated as: form+exit a session, force-stop, write a fresh valid minted pair (`DIRECT-R9-OpenHeadunitRot` / `R9rotate12abX`) into `wifi-direct-group-name`/`-passphrase` as root (owner restored), relaunch, then fire the **non-exported** service action against the now-running foreground service with `am start-service` (works because adb shell is uid 0; `Error: app is in background uid null` if the FGS is not already up). The rotate-path log line and the on-air SSID change are both genuine; the in-app toast was not shown (would be `wifi_direct_new_identity_done` — "A new network identity will be used from the next connection." — since `appliesNow` is false while no group/session is up at tap time). Legacy `< API 29` purge path is **UNTESTABLE** (this rig is Android 14).

### Rig quirks hit
- **MT50 `5GHz createGroup SUCCESS!` frozen timestamp** (known): in R1_run3, R1_run4, R1_run6 the `1.onSuccess | … createGroup SUCCESS!` line and the credential block just after it carried timestamps 0.1–1.3 s *earlier* than the line that precedes them by 3000+ log lines. Line order is the truth; sub-second timing across that block in those runs is not. Headline endpoints (`AapService creating`, `SSL handshake complete`) are in-order and were used as-is.
- **Stale P2P interface at launch** (§7a benign): between runs the previous run's group outlived `headunit://exit` + force-stop and was torn down at the next launch, bumping `p2p-wlan0-N` *before* that run's `createGroup SUCCESS`. Seen on R1_run2/4/6 (two indices each). Not contamination — one `createGroup SUCCESS`, one SSL handshake, no second session, every time. The post-`exit` wait in `native_aa_recovery_r1.sh` was raised from 2 s to 10 s after R1_run2; R1_run3/5 then showed a single index.
- **`bt-off-launch` recipe self-wakes** (CLAUDE.md's "the app can wake itself up"): R2_cand (SETTLE_S=14) — the phone's Bluetooth coming back on mid-poke raised `ACL_CONNECTED` → `MATCH! Starting AapService` → a **second** `createGroup SUCCESS`. Discarded (`R2_cand_candidate_DISCARDED_selfwake.txt`) and re-run at SETTLE_S=6 (`R2_cand2`), which stayed at one `createGroup SUCCESS` / one session despite a lone benign `MATCH`.
- **`MATCH! Starting AapService` with no group churn is benign** (§7a): R2_cand2 and R8_candidate each carry `MATCH`=1 with `createGroup SUCCESS`=1 and one SSL handshake — the phone's own BT reconnect firing `AutoStartReceiver`, not contamination.
- All greps run with `grep -a` (§7a).

### Deviations from the brief's run plan
- R1 alternated arms C/B/C/B/C/B as written (5 reinstalls). R2–R7 were read off the candidate R1 captures plus one dedicated `bt-off-launch` candidate capture (R2_cand2); the baseline R1 captures serve as the R2/R6 controls. No dedicated separate captures were made for R3/R4/R5/R6 — they are properties of a candidate bring-up and every candidate capture agrees.

---

## R0 — build gate

**PASS**

- Candidate `testGithubDebugUnitTest`: **1450 / 0** (138 result XMLs). New classes present: `P2pCreateWedgePolicyTest`, `P2pIdentityRotationPolicyTest`, `GroupIpResolutionPolicyTest`, `StationStandDownSettlePolicyTest`, `EarlyWakePolicyTest`.
- Baseline: **1414 / 0**, none of the five classes present. Δ = 36 = 8 + 10 + 4 + 5 + 9. Reconciles exactly.
- APK md5: candidate `be317bd5…`, baseline `07fd71e3…` (different).
- DEX symbols (`unzip -p … classes*.dex | strings | grep -cF`): candidate `P2pCreateWedgePolicy` 6, `P2pIdentityRotationPolicy` 5, `P2pPersistentGroupPurge` 17, `GroupIpResolutionPolicy` 3, `StationStandDownSettlePolicy` 6, `EarlyWakePolicy` 3. Baseline: all 0.
- Build stamp: `{"commit":"b5617bd5527c","flavor":"github","wifiMode":"NATIVE", …}` via `ACTION_QUERY_STATE`.

---

## R1 — an ordinary Native AA session still forms, and how long it takes

**PASS** — a session formed on all six runs.

- Settings written: `wifi-connection-mode`=3, `native-ap-transport`=0, `native-driver-selection-mode`=0, `log-level`=0, `wifi-5ghz-channel`=0, `native-poke-bt-macs`={`DC:B7:2E:5E:4E:59`}.
- Radio state: phone Bluetooth + WiFi on and settled the whole time (`RECIPE=radios-on`); head unit force-stopped then one `am start`.
- Discard-rule check: clean on all 6. `createGroup SUCCESS` = 1 every run; one `SSL handshake complete` every run; benign stale-interface bump on runs 2/4/6 (see Setup notes).

| Run | Arm | `AapService creating` | `Incoming connection detected` | `SSL handshake complete` | **creating→SSL** | **creating→Incoming** |
|---|---|---|---|---|---|---|
| 1 | candidate | 08:22:45.018 | 08:22:54.167 | 08:22:54.435 | **9.42 s** | 9.15 s |
| 3 | candidate | 08:25:32.916 | 08:25:41.292 | 08:25:41.511 | **8.60 s** | 8.38 s |
| 5 | candidate | 08:27:46.263 | 08:27:58.813 | 08:27:59.066 | **12.80 s** | 12.55 s |
| 2 | baseline | 08:23:52.454 | 08:24:10.875 | 08:24:11.138 | **18.68 s** | 18.42 s |
| 4 | baseline | 08:26:40.936 | 08:26:57.458 | 08:26:57.688 | **16.75 s** | 16.52 s |
| 6 | baseline | 08:28:42.239 | 08:28:50.095 | 08:28:50.350 | **8.11 s** | 7.86 s |

Candidate mean 10.27 s / 10.03 s; baseline mean 14.51 s / 14.27 s. The candidate is directionally faster and its worst run (12.8 s) beats the baseline's two slow runs (16.8, 18.7 s), but baseline run 6 (8.1 s) beat two of the three candidate runs — the phone-association phase (`Providing credentials` → `Incoming connection`) is highly variable (candidate ~7.5–11 s, baseline ~5.3–16.3 s) and three runs per arm do not cleanly separate the distributions. Reported as a measurement, not a criterion; no run was retried for a better number.

---

## R2 — the wake now happens while the group forms

**PASS** on the wake-trigger-vs-credentials signal (literal `socket.connect()` ordering INCONCLUSIVE — see Setup notes).

- Candidate (4 captures: R1_run1/3/5, R2_cand2): `NativeAA: waking the phone while the WiFi group is still forming.` fires **before** the first `Providing credentials` every time — by −1.206 s, −1.171 s, −1.139 s in the three with clean timestamps. Exactly one such line per bring-up.
- Baseline (3 captures: R1_run2/4/6): the wake is `NativeAA: triggerPoke() delay starting (2s)…` and it fires **+0.007 s / +0.007 s / +0.009 s after** the first `Providing credentials` — i.e. only once the group is up and its info has come back. The `waking the phone while the WiFi group is still forming.` line does not exist on the baseline.
- `Calling socket.connect()` signed gap (only fired on the forced `bt-off-launch` candidate run): **+0.132 s** (R2_cand2). Never on the `radios-on` runs or on any baseline run.

---

## R3 — the early wake is not thrown away when credentials land

**PASS**

- `NativeAA: wake poke starting (listeners ready after Nms).` count per bring-up: **1** in R1_run1, R1_run3, R1_run5, R2_cand2 (4/4). N was `0ms` in every case.
- `NativeAA: a wake poke is already running for these credentials - not restarting it.` present at 2–3 per bring-up — the credential re-deliveries (§ "credentials re-delivered several times per group") being refused rather than restarting the loop.

---

## R4 — the stand-down still produces exactly one group

**PASS**

- `WifiLauncherNative: creating the group 100ms after the stand-down (still joined=false).` — appears **once** per bring-up, **N = 100 ms** in every candidate capture (R1_run1/3/5, R2_cand2, R9). Well under 1500.
- `StationStandDown: this unit has left its WiFi network.` present when the rig was joined at launch (R1_run3/5; absent on run1 where it was already off-network, still `joined=false`).
- `createGroup SUCCESS` = **1** per bring-up; one `p2p-wlan0-N` index per run (ignoring the benign pre-create stale-interface bump). No run showed two create chains.

---

## R5 — the credentials still name the live group

**PASS**

- R1_run5, the most-recent `Providing credentials` above `[TX] Wrote TYPE 3` (written 08:27:53.572):
  `08:27:48.701  WifiDirectManager: SUCCESS - Providing credentials to listener. SSID=DIRECT-TX-Navegadortz2, IP=192.168.49.1, BSSID=E6:2C:4E:3E:17:23`
  and at Type 3 send time `08:27:52.387  NativeAA: Phone connected. Current credentials state: SSID=DIRECT-TX-Navegadortz2` — same SSID.
- `NativeAA: the group changed while Type 3 was pending` — **absent** in all captures.
- R9, after the rotate, `[TX] Wrote TYPE 3 (size 65)` at 08:40:22.664 followed the credential line `SSID=DIRECT-R9-OpenHeadunitRot` — the renamed group, not the old one.

---

## R6 — the IP wait is no longer paid

**PASS**

- `WifiDirectManager: Waiting for IP on interface` = **0** in every candidate capture (owner branch taken — the group owner is always 192.168.49.1 and is read once).
- Baseline: also **0** in all three R1 captures. No divergence to report there.

---

## R7 — the create wedge (watch item)

**not exercised** — acceptable per the brief.

- `a group this unit accepted <N>ms ago never formed …` = **0** across all 11 captures.
- `WifiDirectManager: … createGroup … BUSY` with a ~2-minute stall = **0** on both arms.
- Consequently `the stuck group creation was cancelled` / `cancelling the stuck group creation was refused` = 0. Nothing to check the follow-up against. The fix remains covered only by `P2pCreateWedgePolicyTest` (8 cases).

---

## R8 — the poke button can start servers that never started

**PASS**

- Settings: mode 3 etc. as R1. HU Bluetooth turned **off** (`svc bluetooth disable`, confirmed `enabled: false` / `state: OFF` via `dumpsys bluetooth_manager`) *before* launch; phone Bluetooth off for the capture, then on for step 6.
- Discard-rule check: candidate clean (one `createGroup SUCCESS`, one SSL handshake, `MATCH`=1 benign). Baseline capture's step-6 `createGroup`=2 / `MATCH`=2 are from deliberately restoring the phone's Bluetooth and are outside the decisive window.

**Candidate:**
```
08:35:17.755 E NativeAaHandshakeManager.start | NativeAA: Bluetooth adapter not available or disabled
08:35:31.993 I AapService.onStartCommand | AapService: Received manual Native-AA poke request for DC:B7:2E:5E:4E:59
08:35:31.994 W AapService.onStartCommand | AapService: the Native AA handshake servers are not running (this unit's Bluetooth was off or unavailable when the mode was armed), so nothing could answer the phone. Starting them before the poke.
08:35:32.016 I NativeAA: ACTIVELY LISTENING on Android Auto UUID (…) Waiting for phone to connect back!
```
`ACTIVELY LISTENING` follows the poke by **22 ms**. The parenthesised reason names Bluetooth being off at arm time. Session then formed ~15 s after the phone's Bluetooth was restored.

**Baseline (control):**
```
08:36:29.578 E NativeAaHandshakeManager.start | NativeAA: Bluetooth adapter not available or disabled
08:36:43.525 I AapService.onStartCommand | AapService: Received manual Native-AA poke request for DC:B7:2E:5E:4E:59
08:36:43.529 I AapService.onStartCommand | AapService: Native AA listeners are closed, reopening them before the poke.
   … no ACTIVELY LISTENING …
08:36:54.229 I AutoStartReceiver.onReceive | MATCH! Starting AapService via Bluetooth Auto-start...
08:36:54.509 I NativeAA: ACTIVELY LISTENING on Android Auto UUID (…)
08:36:59.059 I NativeAaHandshakeManager.pokeDevice | NativeAA: Successfully poked POCO X3 NFC via HFP-AG. Holding 15000ms...
```
The poke's own branch (`reopening them before the poke`) produced nothing; `ACTIVELY LISTENING` appears only **10.98 s** later and is caused by `MATCH! Starting AapService` re-initialising the whole service (I turned the phone's Bluetooth on in step 6). `Successfully poked` is logged regardless — the capture reads like a healthy wake the phone ignored, which is the failure the fix removes.

---

## R9 — a new network identity reaches the air

**PASS** for the Android 14 (`NAMED_RECREATE`) path. Legacy `< API 29` purge path **UNTESTABLE** on this rig.

- SSID before (last `Providing credentials` of the pre-rotate session): **`DIRECT-TX-Navegadortz2`**. `wifi-direct-group-name` in prefs matched.
- Fresh identity written: `DIRECT-R9-OpenHeadunitRot` / `R9rotate12abX`.
- Action fired (`am start-service … ACTION_ROTATE_WIFI_DIRECT_IDENTITY`, root, against the running FGS):
```
08:39:17.870 I WifiDirectManager.rotateNativeIdentityNow | WifiDirectManager: a new WiFi Direct identity was asked for now (NAMED_RECREATE); recreating the group.
08:39:18.387 I WifiDirectManager.chooseNativeGroupIdentity | WifiDirectManager: group identity: asking for the kept network DIRECT-R9-OpenHeadunitRot again, so a phone that saved it can rejoin without being set up for a new one.
08:39:18.421 I WifiDirectManager: 5GHz createGroup SUCCESS!
08:39:18.583 I SUCCESS - Providing credentials to listener. SSID=DIRECT-R9-OpenHeadunitRot, …
```
- SSID after: **`DIRECT-R9-OpenHeadunitRot`** (≠ before).
- Phone session on the new network:
```
08:40:21.152 I NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59)
08:40:21.651 I NativeAA: [RX] Received Type 2
08:40:22.664 I NativeAA: [TX] Wrote TYPE 3 (size 65) to Bluetooth
08:40:27.669 I WirelessServer: Incoming connection detected from /192.168.49.101
08:40:27.917 I AapSslContext.performHandshake | SSL handshake complete
   … RECV: VIDEO Media Data … (projection running)
```
- `WifiDirectManager: persistent profile purge:` — **absent** (0). Correct for Android 14; it must not appear on this path.

Toast text not captured (UI path bypassed — see Setup notes); it would be `wifi_direct_new_identity_done` = "A new network identity will be used from the next connection."

---

## Anything the brief did not ask about

1. **The poke-loop spin-up is ~1.27 s on this rig** (`wake poke starting` → first `Calling socket.connect()`, R2_cand2). That is longer than the whole P2P group formation (~1.2 s), which is why the early wake's benefit shows up as the phone associating faster rather than as an earlier `socket.connect()`. If the intent is for the *socket* to be dialled before credentials, something between `wake poke starting` and `pokeDevice()` (the `NativeDriverSelectionPolicy.pokeHold` / round-robin path) is eating more time than the fix saves.

2. **`identity stable=` transitions `unproven` → `no` within one bring-up.** In R9 the first `Providing credentials` after a rename says `identity stable=unproven` and the next says `identity stable=no` (BSSID moved `22:CE:BA:D3:02:B8` → `26:1D:81:45:5F:36` across the rotate's two creates). Expected given "this unit re-addresses the group on every create", but worth noting the label is per-credential-delivery, not per-session.

3. **The phone re-associates roughly twice as fast on the candidate when it has to be woken.** Comparing the `Providing credentials` → `Incoming connection detected` phase: candidate ~7.5 s (R1_run1), baseline ~14.7 s / ~16.3 s (R1_run2/4). Baseline run 6 was an outlier at ~5.3 s. The early wake seems to let the phone's Wi-Fi radio spin up during the ~1 s the group is forming, which is exactly the mechanism the commit message claims — but the sample is small and one baseline run beat it.

4. **`native-poke-bt-macs` did not re-seed itself.** The brief warned it seeds from the auto-start list on first read and writes back; on this rig it was already populated with the same MAC as `auto-start-bt-macs`, and it survived all 13 reinstalls unchanged. No re-read surprise.

5. **`am start-service` to a non-exported FGS works from adb as uid 0** but fails `Error: app is in background uid null` if the service is not already running foreground — relevant to any future round that needs to reach `ACTION_ROTATE_WIFI_DIRECT_IDENTITY` or similar without the UI.
