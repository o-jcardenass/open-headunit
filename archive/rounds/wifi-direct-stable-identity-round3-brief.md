# wifi-direct-stable-identity, round 3: the WiFi button after a disconnect starts two group-create chains

**Candidate:** `fork/feat/wifi-direct-stable-identity` @ `94450a6d` (6 commits on `ef74866c`), `3.3.0` (versionCode 103)
**Baseline:** the same branch one commit back, `250b0123` (build it; there is no APK on record). The A/B isolates the single fix commit and also gives `250b0123` its first hardware run.

```bash
git fetch fork
git rev-parse fork/feat/wifi-direct-stable-identity   # 94450a6d...
git log --oneline ef74866c..94450a6d                  # a0940339 8dc5f92e 652ba40f f4c32678 250b0123 94450a6d
```

| SHA | What |
|---|---|
| `a0940339` `8dc5f92e` `652ba40f` `f4c32678` | rounds 1-2, verified |
| `250b0123` | keep the WiFi Direct group across a lost session; leave a live one alone when the phone arrives (untested on hardware) |
| `94450a6d` | **this round** — mark the group create in flight synchronously, before the first async hop |

Read `TESTING-TEMPLATE.md` first, and §7a before planning. The candidate APK the coding session built is md5 `6de3cf264feb48a9ba41b7330a1b0811`; rebuild both arms yourself and record both md5s.

**Current rig state to undo first:** all three devices were left with a `74d0319c` build installed (the `bt-auto-start-and-auto-disconnect` branch, which carries `94450a6d` plus two unrelated commits). That is not a clean candidate. Reinstall a clean `94450a6d` build on D-HU for this round.

---

## 1. Why this round exists

Reproduced 2026-09-01 on D-HU (MT50) + D-POCO (POCO X3 NFC, GH 17.5.663204). Captures in `evidence/wifi-direct-stable-identity-round3/`: `repro-hu.log` (full OPENHU), `repro-phone-filtered.log` (Gearhead + wifi-framework, trimmed to the window). Build under test at capture time was a pre-fix build on this branch line (>= `f4c32678`).

**Symptom:** form a Native AA session, exit it, then tap the in-app WiFi / Native-AA poke button once. The phone wakes, does the Bluetooth handshake, then sits on "Obtaining IP address" / `CONNECTING_WIFI` forever and no session forms. The system also throws several "5 GHz band" selection dialogs during the attempt. Letting the phone reconnect on its own (the automatic poke loop) instead of tapping the button connects fine.

**Mechanism, from the two logs:**

1. `headunit://exit` stops the mode: `WifiDirectManager: Stopping and cleaning up...`.
2. The button fires `ACTION_NATIVE_AA_POKE`. Because the mode is stopped, `AapService` re-inits it: `AapService: Received manual Native-AA poke request` -> `AapService: Initializing Native AA mode before poke...` -> `WifiLauncher: Initializing WiFi Mode: NATIVE` -> `startNativeAaQuietHost()` (**group-create chain #1**).
3. `AapService` then immediately calls `manualPoke()`. Its pre-flight sees credentials not ready and asks for a refresh: `NativeAA: WiFi credentials not ready before manual poke. Requesting WiFi refresh...`.
4. `recreateNativeGroup()` reaches its real `createGroup()` call several async hops later (an async `removeGroup()`, then a 500 ms-posted `delayedCreateQuietGroup()`), and until then `nativeCreateRequestedAtMs` is `0`. The refresh lands in that gap, `NativeRefreshPolicy.decide` sees no group and no create in flight, and returns RECREATE: `WifiDirectManager: refresh: no group is up, so one is created.` -> a second `startNativeAaQuietHost()` (**chain #2**).
5. The two chains fight over `createGroup failed (BUSY ...), removing group and retrying in 2s (retry N/4)` and produce three or four P2P groups in a row. On this rig every create draws a fresh SSID (round 2's give-up), so each group is a name the phone has never seen.
6. The Bluetooth handshake, already in flight, hands the phone `WifiInfoResponse` (Type 3) built from the **first** group's SSID/BSSID. By the time the phone reads it, that group has been torn down and replaced. Phone side: `GH.WIRELESS.SETUP: Info response received. Received credentials=WifiConfiguration(ssid=DIRECT-VQ-Navegadortz2, bssid=7A:3E:20:5A:12:2F ...)` -> `State changed to CONNECTING_WIFI` -> `WifiNetworkFactory ... releaseRequestAsUnfulfillableByAnyFactory` and `GH.WirelessNetRequest: Supplicant state: DISCONNECTED` on a loop, never `CONNECTED_WIFI`. In the capture it stayed stuck ~2.5 minutes until later pokes happened to line a group up.

**The fix (`94450a6d`, one file):** `WifiDirectManager.recreateNativeGroup()` sets `nativeCreateRequestedAtMs` synchronously at its top, before the async `removeGroup()`, so a refresh landing in the gap gets a WAIT decision instead of RECREATE. `stop()` clears the marker alongside the other create guards it already resets. `createQuietGroup()` / `standardCreateGroup()` still refresh the marker to an accurate value once they issue the request.

**Why the automatic poke does not hit it:** the standalone poke loop does not re-init the mode and does not fire the extra pre-flight refresh; by the time it pokes, one group is long up and the refresh (if any) returns REDELIVER.

---

## 2. What is different about this round

- **The trigger must be scripted.** The button is `HomeFragment`'s Native-AA device selector. The scriptable equivalent, from `hfp_poke_probe.sh`'s own header:
  ```bash
  adb -s $HU shell am start-foreground-service \
    -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService \
    -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es extra_mac <D-POCO BT MAC>
  ```
  `AapService` is `exported=false`. `hfp_poke_probe.sh` records this being **rejected on non-rooted D-MOTO**; D-HU has a root adb shell (`set_hu_pref.sh` relies on it) so it should be accepted there. **Verify it in setup** (`am` prints `Error: ...` on rejection). If it is rejected on D-HU too, fall back to the in-app selector: `uiautomator dump` to find the poke row for D-POCO, one `input tap`, and say so in Setup notes. Do not scroll a list.
- **One poke, not a loop.** The bug is the *first* poke after a stop, when the mode has to be re-inited. A second poke while the mode is already active takes a different branch and does not reproduce it. Each run: session -> `headunit://exit` -> wait for `WifiDirectManager: Stopping and cleaning up...` -> exactly one poke -> observe 90 s.
- **This rig makes it worse, not milder.** Round 2 established D-HU re-addresses every group, so `chooseNativeGroupIdentity()` draws a fresh SSID per create and the phone has nothing cached to fall back to. A rig that kept one SSID might mask the stuck-join. Expect the full stuck-join here.
- **The 5 GHz dialogs are a side effect of the same churn** (one per `createGroup` attempt) and are not separately scored.
- INFO is not enough for the refresh lines — use `log-level=2` (VERBOSE). `WifiDirectManager: refresh: ...` and the poke pre-flight lines are DEBUG/VERBOSE.

---

## 3. Preparation

Settings as round 2's table: `wifi-connection-mode=3`, `wifi-5ghz-channel=36`, `static-bssid=0`, `native-wifi-version-exchange=false`, `stand-down-station-for-wifi-direct` absent, `wifi-direct-band` as round 2. `log-level=2`. Identity keys: leave them as they land (the give-up will re-arm `wifi-direct-group-address-moves` on its own within a bring-up or two on this rig, which is the state we want).

D-POCO paired to D-HU, its head-unit record clean (no stored `port=5299` endpoint from a prior WPP-TCP round; `wpp-over-tcp` round 5 notes how to check). Phone Bluetooth on for every run in this round.

### Build gate (R0)

Two builds. Record both md5s; they must differ. Symbol check on the candidate:

```bash
unzip -p <candidate.apk> 'classes*.dex' | strings | grep -cF 'Claim the create window now'   # 0 -- comment, not in DEX; use the two below
```

The fix is one assignment and a comment, so there is no new symbol. Confirm the candidate instead by:

```bash
git show 94450a6d --stat        # exactly 1 file, WifiDirectManager.kt, +15
git log --oneline 250b0123..94450a6d   # exactly 94450a6d
```

Unit tests: candidate and baseline both **the same count** (the fix adds no test). Round 2's candidate was **1170 / 0** with `P2pGroupIdentityPolicyTest` 16; `250b0123` and `94450a6d` should both still read 1170 / 0 or higher with 0 failures. A failure stops the round.

---

## 4. Runs

### R1: the double chain, A/B (the point of the round)

Both arms, phone Bluetooth on, `log-level=2`.

Per arm, three times:
1. Bring up a full Native AA session (phone connects on its own; `SSL handshake complete`, video).
2. `adb shell am start -a android.intent.action.VIEW -d 'headunit://exit' <pkg>` (or the exit deep link the rig uses). Wait for `WifiDirectManager: Stopping and cleaning up...`.
3. Fire **one** poke (the `am start-foreground-service` line in §2).
4. Capture 90 s.

**Baseline `250b0123` — expect the bug, all three:**
- `WifiDirectManager: refresh: no group is up, so one is created.` present (>= 1) after the poke.
- `createGroup failed (BUSY` >= 1, `removing group and retrying in 2s (retry` >= 1.
- Two or more `onGroupInfoAvailable: SSID: DIRECT-` lines with **different** SSID **and** BSSID within the one poke; `p2p-wlan0-N` interface index jumps by 2 or more.
- **No** `WirelessServer: Incoming connection detected from` -> no `SSL handshake complete` -> no session.
- Phone: `State changed to CONNECTING_WIFI` then a loop of `releaseRequestAsUnfulfillableByAnyFactory` / `Supplicant state: DISCONNECTED`, never `CONNECTED_WIFI`.

**Candidate `94450a6d` — expect the fix, all three:**
- `WifiDirectManager: refresh: a group was asked for <N>ms ago and has not answered yet, so nothing is remade underneath it.` present (>= 1) — the WAIT that suppresses chain #2. (`refresh: no group is up` may still appear **once** for chain #1's own first pass before the marker is set; what must not appear is a second `startNativeAaQuietHost` / a second independent create chain.)
- Exactly **one** `createGroup SUCCESS!` for the poke, one SSID, `p2p-wlan0-N` steady (index +1 at most).
- `WirelessServer: Incoming connection detected from /192.168.49.x` -> `SSL handshake complete` -> session, video.
- Phone: `State changed to CONNECTED_WIFI` -> `PROJECTION_INITIATED`.

**PASS** when the baseline shows the stuck join on at least 2 of 3 and the candidate forms a session on all 3, with the `refresh: a group was asked for ...` WAIT line present on the candidate and the second create chain absent. Report, per arm: sessions formed / 3, `createGroup SUCCESS` count per poke, `createGroup failed (BUSY` count per poke, and poke -> `Incoming connection detected` seconds for each candidate success.

If the baseline does **not** reproduce the stuck join (the rig lined a group up in time on every attempt), R1 is INCONCLUSIVE for the A/B — still report the candidate side, and note the baseline did not fault.

### R2: the automatic poke still works (candidate only, regression guard)

Candidate. Form a session, `headunit://exit`, then **do not** poke — let the automatic poke loop / the phone's own reconnect bring it back (round 2's reconnect trigger: a head-unit `svc bluetooth disable` self-revert, per `wds_reconnect.sh`). Once. 90 s.

**PASS** when a session forms, one `createGroup SUCCESS` for the reconnect, and `refresh: ` (if it appears) is REDELIVER (`the group ... is up, so its credentials are read again`) or WAIT, never a second create chain.

### R3: 5-minute clean control (candidate only)

Candidate. One Native AA session, left untouched 5 minutes, then exit.

**PASS** when `createGroup SUCCESS` stays at 1, `p2p-wlan0-N` stays at one interface, throughput windows all `dropped=0`, and zero `refresh: no group is up` after the session is established.

### R4 (optional, only if rig time): round 2's still-open baseline reconnect re-run

Round 2's R2 was stopped by the operator with a queued follow-up: a baseline `ef74866c` R2-shape reconnect re-run on a **fresh session** (WiFi restarted, `p2p-wlan0` index reset) to tell an `ef74866c` regression from rig P2P-stack exhaustion. If there is rig time after R1-R3, run `wifi-direct-stable-identity-round2-brief.md` R2 baseline arm only, once, right after a WiFi off/on cycle, and report the six `[TX] Wrote TYPE 3` -> `Incoming connection detected` numbers. Skip without penalty.

---

## 5. Stop conditions and restore

- R0 fails on either arm: stop.
- The poke cannot be triggered on D-HU by either method: stop and report UNTESTABLE with what `am` printed.
- R1 baseline does not fault: R1 is INCONCLUSIVE, run R2 and R3 anyway.

Leave the candidate `94450a6d` installed on D-HU, `settings.xml` restored to the round's baseline (verify byte-identical), phones' radios on, D-POCO's head-unit record clean.

---

## 6. Report back

Three numbers decide it: baseline sessions-formed / 3, candidate sessions-formed / 3, and whether the candidate's `refresh: a group was asked for ...` WAIT line is present with no second create chain. If the baseline reproduces and the candidate is 3/3 clean, the fix ships and `250b0123` clears its first hardware run with it.
