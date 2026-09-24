# p2p-bringup-loop — round 1 results

**Candidate:** `fork/fix/907-p2p-channel-request-wedge` @ `ef5080cd`   **Baseline:** `origin/main` @ `a211dd48`
**APK md5:** `2b25adcf423d9bf6570dff63cd823781` (arm B) / `c6af71a6a663222546d9a447b5d00069` (arm A)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14 (SDK 34), 1440×720, HU Bluetooth name `Navegadortz2`. Phone: POCO X3 NFC (`M2007J20CG` / `surya`), Android 15, Gearhead `17.5.663204-release`.
**Date:** 2026-08-29

## Summary for the shipping decision

| | premise `no band request` / `no answer in` | `createGroup SUCCESS` A→B | `auto-starting Native AA quiet host` A→B | verdict |
|---|---|---|---|---|
| R1 | 0 / 0 | 1 → 1 | 0 → 0 | **PASS** both arms, counts identical |
| R2 (5 cycles) | 0 / 0 | 5 → 5 | 0 → 0 | **PASS** both arms, counts identical |
| R3 (WiFi toggle) | 0 / 0 | 3 → 3 | 1 → 1 | **PASS** both arms — latch cleared, `P2P enabled, auto-starting Native AA quiet host` fired |
| R4 (helper visibility) | 0 / 0 | — | — | **PASS** — bare-launch line 0/0 (rig behaviour, not a regression); fires on a real toggle on arm B |
| R5 (`No usable access point after`) | 0 / 0 | control | control | A = **1**, B = **3** (30s/90s/151s), banner shown on B → **PASS** (B) / control (A) |
| R6 (record retires) | 0 / 0 | — | — | **INCONCLUSIVE** as pre-registered — retire path unreachable on this rig |

1. **`createGroup SUCCESS` and `auto-starting Native AA quiet host`, arm A vs arm B, per run:** identical on every run (R1 1/0 vs 1/0; R2 5/0 vs 5/0; R3 3/1 vs 3/1). Same group count, same bring-up count. No delta anywhere. `Attempting createGroup for Native AA` also identical (1, 5, 3).
2. **R3's verdict: PASS on both arms.** A real 10 s WiFi toggle cleared the latches and started a bring-up — `P2P enabled, auto-starting Native AA quiet host` at +2 ms of `state=2`, group formed within 600 ms. No stuck latch. WiFi returned on the first `svc wifi enable` both times (no nudge needed).
3. **R5 arm A vs arm B: 1 vs 3, banner appeared on B.** `connection-issue-hotspot-off` = non-zero long on B after the run, absent on A. Exactly the brief's predicted 1 vs ≥2.
4. **The two premise-check counts: 0 and 0, in all 14 captures.** `no band request below Android 10` and `no answer in ` never appeared. The §0 framing holds: the pre-Q channel ladder is dead code on this Android-14 rig.

**Recommendation: ships.** Both live-on-Android-14 edits (`P2pStateChangePolicy`, `ConnectionIssue.HOTSPOT_NOT_RUNNING`) are regression-clean, and the new reachable behaviour (R5) works as designed.

## Build gate — PASS

- `build_hur.sh` clean on both SHAs; md5s recorded above and different.
- `run_unit_tests.sh` (arm B): **873 tests, 0 failures, 0 errors, 0 skipped.**
- `P2pStateChangePolicyTest`: **8 tests, 0 failures** (new file).
- `ConnectionIssueBannerPolicyTest`: 29 tests, 0 failures — contains both named tests verbatim: `only the hotspot route can be blocked by there being no access point`, `no setting is a remedy for there being no access point`.
- All §5 decisive strings and both §3 premise strings verified present on `ef5080cd` with `git grep -F` before running.

## Setup notes

**Pairing.** The round opened with the head unit bonded only to a `motorola edge 30 neo` that was not connected via adb; the connected phone (POCO X3 NFC) was not paired to the HU. The operator re-paired the POCO by hand (`BOND_STATE_BONDED` at 17:04). The HU's `auto-start-bt-name` / `last-nearby-device-name` still read `motorola edge 30 neo` throughout — left unchanged (R1/R2 launches were explicit `am start`, and R2 re-arm rode `AutoStartReceiver` which matched the POCO's ACL anyway).

**Clean-run protocol deviation (§4 step 1).** `cmd connectivity airplane-mode enable` does not hold on this phone — the flag self-reverted to 0 within ~15 s and both radios stayed up. Toggling the phone's **WiFi** off/on during setup also caused repeated P2P rejoin and a double session (R1-A attempt 1, discarded: 2 `createGroup SUCCESS`, 4 `SSL handshake complete`). Substituted **`svc bluetooth disable` on the phone** as the sole HU-first lever (Native AA cannot handshake without RFCOMM, §7a), re-enabling after the HU group settled. All Native-AA runs used this. Documented rather than absorbed because it changes §4.

**`wifi-direct-band` 1 → 0.** Current `settings.xml` (from the `intercom-native-mode` / `headunit-info` thread) had `wifi-direct-band=1`. Set to `0` (Auto) per the brief's §4 table, applied identically to both arms. Groups formed on 5 GHz (5180 MHz) throughout on both arms regardless.

**Extra key deleted each run:** `connection-issue-hotspot-config` (stale `HOTSPOT_CONFIG_UNREADABLE` stamp `1787376606616` left by the previous thread) — deleted alongside the two keys the brief names, so it could not shadow the R5/R6 banner check. Not in the brief's table.

**`settings.xml` delta at round start** (vs. defaults, from the previous thread): `wifi-connection-mode=3`, `native-ap-transport=0`, `helper-connection-strategy=2`, `wifi-direct-band=1`, `auto-enable-hotspot=true`, `log-level=2`, `view-mode=2` (GLES), `video-codec=H.265`, `software-video-decoder=1`, `debug-video-low-latency=true`, `fps-limit=60`, plus `connection-issue-hotspot-config=1787376606616`. All round keys overwritten per §4; `settings.xml` restored **byte-identical** to the backup at the end (`diff` clean).

**R2 cycle mechanism.** `headunit://disconnect` in mode 3 on this build tears everything down and does **not** re-arm — `AapService: User exit with wirelessServer active. Not restarting discovery.` A bare phone-BT off/on did not reliably raise `ACL_CONNECTED` on the HU (no `MATCH!` for 48 s in one attempt). The working cycle, used for all of R2: `headunit://disconnect` → wait 8 s → **`svc bluetooth disable` on the head unit** (self-reverts in ~14 s, §7a, raising a real `ACL_CONNECTED` → `MATCH! Starting AapService` → `initWifiMode` in the **same process**). No force-stop between cycles, so `lastP2pRequestAtMs` is preserved across all five and the 2 s gate is genuinely exercised.

**R1-B attempt 1 discarded.** Phone reconnect took ~55 s (vs ~36 s in R1-A), long enough that the no-session 60 s group-recreate watchdog fired before the phone joined → 2 `createGroup SUCCESS`. Re-ran with the phone's BT left **on** through the whole run; phone joined in ~10 s, one clean group. R1-A (BT-toggle method) was already clean; the four §5 counts are identical between the two.

**R3 third group.** On both arms a 3rd `createGroup SUCCESS` appears ~60 s after the post-toggle group. This is `WifiDirectManager`'s no-client join watchdog recreating the group (phone BT was off for R3, nothing ever joined). Present on both arms, unrelated to commit 907.

**R4 — the brief's bare-launch expectation is wrong for this rig.** On a bare helper/WiFi-Direct launch, `WifiLauncherHelper.start()` brings the P2P group up via the direct path and the receiver's `makeVisible()` branch is gated by `!isGroupCreatingOrCreated` — **identical on both arms** (verified against `a211dd48`'s receiver: same condition, arm B only adds the 2 s window on top). So `auto-starting WiFi Direct visibility` = 0 on a bare launch on **both** arms (R4-A and R4-B), with one `state=2` present. This is not the FAIL the brief's literal wording describes — it is how this rig has always behaved, exactly as mode 3's `auto-starting Native AA quiet host` = 0 on a bare launch (R1). Added a **toggle variant**: with helper/WiFi-Direct armed, `svc wifi disable; sleep 10; svc wifi enable` → arm B logs `WifiDirectManager: P2P enabled, auto-starting WiFi Direct visibility` (17:42:14.187) and rebuilds the group + discovery. The policy does not wrongly gate `makeVisible()`.

**R6 — the rig CAN raise a SoftAP** (contra the brief's §6 "cannot raise a usable SoftAP"). `cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5` brought `wlan2` up at 5745 MHz with `192.168.121.249/24`. But `SoftApCredentialsProvider` can't read its name (`getSoftApConfiguration` masked for apps, no manual override set): `SoftApCredentials: The access point on wlan2 is up, but this device will not let apps read its name, so there is nothing to hand the phone.` `ConnectionIssues.clear(HOTSPOT_NOT_RUNNING)` fires only on a nameable/joinable AP by design (`SoftApCredentialsProvider.kt` ~line 440 comment: *"a device that shows an interface but never yields joinable credentials keeps the record it earned"*), so the retire path was still unreachable → INCONCLUSIVE, as the brief pre-registered. SoftAP stopped afterwards.

**Rig-state divergence from §3.** The head unit is **not** currently joined to `Pegue Cdesta` or any WiFi station network (`Supplicant state: DISCONNECTED`, `Wifi is not connected`; `wlan0` is `ROLE_CLIENT_PRIMARY` but unassociated). Almost certainly dropped by the R3 `svc wifi disable/enable` cycles — this rig does not reliably re-associate its saved network after `svc wifi enable` (§7a). No run depended on the association: R5's "no AP interface" finding (`Present: dummy0, seth_lte0`) holds regardless, and R6's SoftAP came up on `wlan2`. WiFi radio left **enabled**; the next round should re-verify and re-join before any run that needs the station.

**Scripts.** `hur-wifi-test-scripts/build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh` (multi-key, no relaunch). No new scripts. Both APKs copied into `hur-wifi-test-scripts/round-p2p-bringup-loop/` (`armA_a211dd48.apk`, `armB_ef5080cd.apk`) immediately after each build, before the second `build_hur.sh` wiped `apks/` (§7a). Candidate (`ef5080cd`) left installed; `settings.xml` restored byte-identical; phone + HU radios re-enabled.

---

## R1 — Native AA over WiFi Direct still connects

**PASS** (arm A and arm B)

- Settings written (both arms): `wifi-connection-mode=3`, `native-ap-transport=0`, `helper-connection-strategy=1`, `wifi-direct-band=0`, `auto-enable-hotspot=false`, `log-level=0`; `connection-issue-hotspot-off` / `-dismissed-at` / `-hotspot-config` deleted.
- Radio state: HU WiFi + BT on. Phone BT off during HU launch + settle, then on (arm A: also off before; arm B attempt 2: phone BT left on).
- Discard-rule check: arm A clean (BT-toggle method); arm B attempt 1 discarded (2 `createGroup SUCCESS` — slow-reconnect + 60 s watchdog), attempt 2 clean.
- Decisive lines:
  - arm B: `17:30:22.093 … 5GHz createGroup SUCCESS!` (×1) · `17:30:29.716 … WirelessServer: Incoming connection detected from /192.168.49.63` · `17:30:29.919 … SSL handshake complete` (×1) · steady 51–52 fps `c2.unisoc.hevc.decoder`, `dropped=0`.
  - arm A: `17:11:00.834 … 5GHz createGroup SUCCESS!` (×1) · `17:11:55.215 … Incoming connection detected from /192.168.49.153` · projection 49–58 fps, `dropped=0`.

| count | arm A | arm B |
|---|---|---|
| `createGroup SUCCESS` | 1 | 1 |
| `Attempting createGroup for Native AA` | 1 | 1 |
| `auto-starting Native AA quiet host` | 0 | 0 |
| `WIFI_P2P_STATE_CHANGED_ACTION state=2` | 1 | 1 |
| `Incoming connection detected` | 1 | 1 |
| `SSL handshake complete` (log lines; = 1 handshake) | 2 | 2 |
| premise `no band request` / `no answer in` | 0 / 0 | 0 / 0 |

`MATCH! Starting AapService` — arm A 2, arm B 1: the phone's own Bluetooth reconnect after the BT toggle, with one `createGroup SUCCESS` and one handshake attached, i.e. not contamination per the brief's §3 rule. Per-run delta in `auto-starting Native AA quiet host` and `Attempting createGroup`: **0**.

## R2 — repeated bring-ups are not swallowed by the 2 s gate

**PASS** (arm A and arm B)

- Settings: as R1.
- Radio state: phone BT on throughout; each cycle re-armed by an HU-side `svc bluetooth disable` self-revert (see Setup notes).
- Discard-rule check: clean on both — exactly 5 `createGroup SUCCESS`, 5 distinct P2P interfaces, no `Magic Garbage`.
- 5 cycles → 5 groups → 5 sessions on both arms. Every `state=2` broadcast that arrived with no group up was followed by `Attempting createGroup for Native AA` within ~0.5 s (arm B, verbatim): `17:33:27.477 state=2` → `17:33:27.981 Attempting createGroup` → `17:33:28.028 createGroup SUCCESS` (and the same shape ×5).

| count | arm A | arm B |
|---|---|---|
| `createGroup SUCCESS` | 5 | 5 |
| `Attempting createGroup for Native AA` | 5 | 5 |
| `auto-starting Native AA quiet host` | 0 | 0 |
| `WIFI_P2P_STATE_CHANGED_ACTION state=2` | 5 | 5 |
| `Incoming connection detected` | 5 | 5 |
| distinct `p2p-wlan0-N` | 3,4,5,6,7 | 3,4,5,6,7 |
| premise | 0 / 0 | 0 / 0 |

No cycle where the phone was present, no group existed, and no bring-up was attempted. The 2 s self-inflicted window never refused a legitimate cycle — same group count on arm B as arm A with the same (equal) `Attempting createGroup` count, which the brief calls a PASS.

## R3 — a real WiFi toggle still clears the latches

**PASS** (arm A and arm B) — the highest-value run.

- Settings: as R1. App armed (group up, listeners open), phone BT off so no session.
- Action: `adb -s <hu> shell svc wifi disable ; sleep 10 ; adb -s <hu> shell svc wifi enable`.
- WiFi returned on the first `svc wifi enable` both times (`wifi_on=1`, "Wi-Fi is enabled") — no nudge, run fully valid (not the INCONCLUSIVE the brief allowed for).
- Decisive lines, arm B verbatim:
  ```
  17:37:33.088  WifiDirectManager: WIFI_P2P_STATE_CHANGED_ACTION state=1
  17:37:43.447  WifiDirectManager: WIFI_P2P_STATE_CHANGED_ACTION state=2
  17:37:43.449  WifiDirectManager: P2P enabled, auto-starting Native AA quiet host
  17:37:43.958  WifiDirectManager: Attempting createGroup for Native AA (Attempt 0)...
  17:37:44.038  WifiDirectManager: 5GHz createGroup SUCCESS!
  ```
  arm A identical shape: `17:21:55.420 state=2` → `17:21:55.422 P2P enabled, auto-starting Native AA quiet host` → `17:21:56.022 createGroup SUCCESS`.
- The 10 s off-window is far outside the 2 s `SELF_INFLICTED_WINDOW_MS`, so `shouldStartBringUp` returned true and `shouldResetOnDisable` cleared the latches on `state=1`. No latch stuck true; no 30 s dead window.

| count | arm A | arm B |
|---|---|---|
| `createGroup SUCCESS` (launch + post-toggle + 60 s no-client watchdog) | 3 | 3 |
| `auto-starting Native AA quiet host` | 1 | 1 |
| `P2P enabled, auto-starting Native AA quiet host` | 1 | 1 |
| `state=1` / `state=2` | 1 / 2 | 1 / 2 |
| premise | 0 / 0 | 0 / 0 |

## R4 — helper mode WiFi Direct is still made visible

**PASS** (arm B; arm A run for comparison)

- Settings: `wifi-connection-mode=2`, `helper-connection-strategy=1`. No phone.
- Bare launch, 60 s: `auto-starting WiFi Direct visibility` = **0** on **both** arms, with `WIFI_P2P_STATE_CHANGED_ACTION state=2` = 1. On both arms the group came up via the direct `WifiLauncherHelper.start()` path (`WifiLauncher: Using strategy WIFI_DIRECT.` → `P2P Group created (fresh this session).` → `Discovery active - peer search running`) and the receiver's `makeVisible()` branch was gated by `!isGroupCreatingOrCreated` — identical condition on both arms. This is the same structural behaviour as mode 3's bare-launch `auto-starting Native AA quiet host` = 0 (R1), not a regression.
- Toggle variant (arm B, added): helper/WiFi-Direct armed, `svc wifi disable; sleep 10; svc wifi enable` →
  ```
  17:42:03.843  WIFI_P2P_STATE_CHANGED_ACTION state=1
  17:42:14.186  WIFI_P2P_STATE_CHANGED_ACTION state=2
  17:42:14.187  WifiDirectManager: P2P enabled, auto-starting WiFi Direct visibility
  17:42:14.276  WifiDirectManager: P2P Group created (fresh this session).
  17:42:24.304  WifiDirectManager: Discovery active - peer search running
  ```
  `P2pStateChangePolicy.shouldStartBringUp` did not wrongly gate `makeVisible()` after a real P2P transition.
- premise: 0 / 0 (bare) and 0 / 0 (toggle).

Verdict PASS: helper-mode visibility works on arm B and behaves identically to arm A; the one line the brief keyed on is a rig artifact of the bare-launch path, corrected in Setup notes.

## R5 — the hotspot record is raised, repeated, and shown

**PASS** (arm B) · **control** (arm A)

- Settings: `wifi-connection-mode=3`, `native-ap-transport=1`, `auto-enable-hotspot=false`; `connection-issue-hotspot-off` / `-dismissed-at` / `-hotspot-config` deleted. HU hotspot off (`Wifi Soft AP state is: false`). Phone BT on (handshake keeps asking). 3-minute window, then force-stop + relaunch.
- Path-reachable proof (both arms): `SoftApCredentials: No interface looks like an access point; waiting for one. Present: dummy0 (no private address), seth_lte0 (10.246.161.59)` — 19× (A), 15× (B).

| | arm A | arm B |
|---|---|---|
| `No usable access point after` in the 3-min window | **1** (`reportBudgetExhaustedOnce`, "after 30s") | **3** (`reportNoAccessPoint`, "after 30s / 90s / 151s" — 60 s cooldown) |
| `connection-issue-hotspot-off` in `settings.xml` after | absent | `value="1788043548768"` (non-zero long) |
| banner on relaunch | none | `MainActivity: showing the connection issue banner for HOTSPOT_NOT_RUNNING` (17:46:16.998) |
| banner on screen | n/a | **yes** — `id/connection_issue_banner`, text "This unit's hotspot is off, so there was no network to send your phone to. …" (screenshot `r5b_banner.png`) |
| premise | 0 / 0 | 0 / 0 |

Arm A matches the brief's "Expected on arm A" exactly (line once, no key, no banner). Arm B meets all three PASS conditions.

## R6 — the record retires when an access point comes up

**INCONCLUSIVE** (arm B) — as the brief pre-registered.

- Continued from R5-B (`connection-issue-hotspot-off` = `1788043608385`).
- SoftAP raised: `cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5` → `wlan2` @ 5745 MHz (5 GHz ch 149), `inet 192.168.121.249/24`, `Wifi Soft AP state is: true`.
- Relaunch (mode 3, transport 1, phone BT on):
  ```
  17:47:42.873  MainActivity: showing the connection issue banner for HOTSPOT_NOT_RUNNING
  17:47:43.528  AapService: Native AA on the head unit hotspot — resolving access point credentials.
  17:47:43.584  SoftApCredentials: The access point on wlan2 is up, but this device will not let
                apps read its name, so there is nothing to hand the phone. … Set 'Hotspot name
                (manual)' and 'Hotspot password (manual)' in Settings …
  ```
- `connection-issue-hotspot-off` did **not** return to 0; banner still shows `HOTSPOT_NOT_RUNNING`.
- Cause: `ConnectionIssues.clear(HOTSPOT_NOT_RUNNING)` fires only when the AP yields a nameable, joinable set of credentials (by design). This rig masks `getSoftApConfiguration()` for apps and no manual `hotspot-ssid`/`hotspot-password` override is set, so the clear site is unreachable. Not a code fault; the retire path is covered by `ConnectionIssueBannerPolicyTest`. One attempt, marked, moved on per the brief. SoftAP stopped.

## Anything the brief did not ask about

- **`auto-starting Native AA quiet host` is 0 on every clean Native-AA launch on this rig** (R1, R2). The line only appears when P2P actually transitions DISABLED→ENABLED (R3). On a normal launch the group is created by the direct `startNativeAaQuietHost()` call from `AapService.initWifiMode()`, and the sticky `state=2` broadcast that arrives on receiver registration finds `isGroupCreatingOrCreated` already true (or is inside the 2 s window on arm B). Worth knowing for any future brief that keys a PASS on that line at launch — it will read as a false FAIL, the same way R4's did here.
- **The no-client 60 s group-recreate watchdog** fires on both arms whenever a group sits with nothing joined (seen in R1-B attempt 1, R3 both arms). It bumps `createGroup SUCCESS` without any `state=2` or `auto-starting` line before it — distinguishable from a real bring-up by exactly that absence.
- **`5GHz createGroup SUCCESS!` vs `Standard createGroup SUCCESS!`** — every group this round logged the `5GHz` variant; `Standard createGroup SUCCESS!` (a §5 line) appeared 0 times. `grep -ac "createGroup SUCCESS"` catches both, so the discard rule is unaffected, but a brief greping the literal `Standard createGroup SUCCESS!` would miss every group on this rig.
- **Head unit dropped its WiFi station association** during the round (R3's `svc wifi` cycles) and had not re-joined `Pegue Cdesta` by the end. Flagged above; the next round on this channel should re-verify.
