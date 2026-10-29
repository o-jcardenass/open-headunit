# wifi-direct-stable-identity round 4 + bt-auto-start-disconnect round 1 — combined results

Run as one round against a single build, per request. Two briefs
(`wifi-direct-stable-identity-round4-brief.md`, `bt-auto-start-disconnect-round1-brief.md`).

**Candidate:** `fork/feat/bt-auto-start-and-auto-disconnect` @ `f0c20b8a` — 2 commits (`ae35e6a5`,
`f0c20b8a`) on top of `fork/feat/wifi-direct-stable-identity` @ `0a9a98d9`. So this one APK carries
the whole WiFi-Direct-round-4 fix stack **plus** the two bt-auto-start commits.
**Baseline (brief 1 R1/R2 A/B only):** `937e68b7` — the WiFi-Direct branch with the kept group and
none of the round-4 fixes.
**APK md5:** candidate `190488f91a34fcbeaf2d653496e7bd4c` / baseline `3e5cb0079ae86361137ceda7336b99b3`
**Unit:** D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14, `Navegadortz2`, BT `11:46:03:10:33:59`).
D-POCO = POCO X3 NFC (`M2007J20CG`, Android 15, Gearhead 17.5.663204, BT `DC:B7:2E:5E:4E:59`).
**Date:** 2026-09-02

---

## Setup notes

### Deviations from the briefs

- **One build for both briefs.** Brief 1's candidate arm was run on `f0c20b8a`, not `0a9a98d9`. The
  two extra commits touch `AapService.kt` (auto-disconnect receiver + BT-arrival handler),
  `BtAutoStartRearmPolicy.kt`, and UI/Settings — **not** `WifiDirectManager.kt`,
  `NativeAaHandshakeManager.kt`, `WifiLauncherNative.kt`, or the credential/create/Type-3 logic every
  brief-1 run scores (`git diff --stat 0a9a98d9..f0c20b8a` confirms). The auto-disconnect receiver is
  inert in brief 1 (`auto-disconnect-bt-macs` empty). Brief 1's R0 unit-test count is therefore
  **1208 / 0** (brief 2's number), not brief 1's stated 1193 / 0, and `BtAutoStartRearmPolicyTest`
  is 18 not 13 — both are the post-rebase counts.

- **`log-level` for brief 1: used ordinal `1` (DEBUG) throughout, not `2`.** Brief 1 §3/§4 says
  "`log-level=2` … for the DEBUG lines", but the enum is `VERBOSE 0, DEBUG 1, INFO 2 …`
  (`Settings.kt:203`, `AppLog.LOG_DEBUG = LOG_LEVEL <= Log.DEBUG`), so the DEBUG-guarded line
  `NativeAA: the live credentials still match the ones this handshake captured` (R2's positive
  evidence, `AppLog.d`) needs ordinal `1`, and `log-level=2` would have hidden it. Ran every brief-1
  run at ordinal 1 (captures all the INFO lines the runs score, plus the one DEBUG line). Brief 2
  ran at ordinal 2 as written (all its lines are `AppLog.i`).

- **`set_hu_prefs.sh` cannot write a `<set>` element** (its string matcher is `<string name=…>`, not
  `<set name=…>`). Added `set_hu_settings_host.py` (host-side: pull `settings.xml`, edit with
  python — scalars + `<set>` + delete — push back via `adb cp`/`chown`/`chmod` as separate calls).
  Inline `adb shell sh -c 'cp … && chown …'` fails on this rig (§7a) — separate `adb shell cp`,
  `adb shell chown`, `adb shell chmod` calls work. The MT50's adb shell is **root directly, no `su`**
  (first script version used `su 0` and silently truncated `settings.xml` to one key; recovered from
  the round-start backup, no lost run).

- **Brief 1 R6 verdict is FAIL** (see R6). Brief 1 R3's "exactly one" defer-line expectation became
  "twice" — a cadence assumption, substance holds (see R3).

- **Brief 2 R1/R1b/R1retry/R2/R3 are INCONCLUSIVE by a rig limit** — the projecting phone drops its
  Bluetooth link to this HU within seconds of every WiFi handoff and does not restore it during the
  session (this is R4's finding). The deliberate "watched device leaves after 60 s" lever has no ACL
  to drop. R6/R7 (Self Mode on D-POCO, where the D-HU↔D-POCO A2DP link is not torn by a WiFi
  session) are the runs that exercise the auto-disconnect end to end, and both PASS.

- **Brief 2 R3** was not run as a separate run: its lever and starting condition are identical to
  R1b's, and the session-end it needs to observe the re-arm cannot be produced (same reason as R1).
  R1b's capture covers it: HU-bt cycle during a live Native AA session → no auto-disconnect line,
  session stays up, no spurious re-arm (`MATCH` suppressed while a session is live).

- **Brief 2 R6/R7 needed the operator** to enable D-POCO's AA-Developer "Start head unit server"
  (`:5277` was down). Enabled mid-session on request; both runs then completed.

- **Brief 2 R6/R7 rig prep:** on plain `MainActivity` launch, D-POCO with `wifi-connection-mode=3`
  armed the Native AA host (`setActiveFromSettings` reads `wifiConnectionMode` directly and does
  **not** consult `connectionModes`/`showsWifi()`), so `connection-modes={self}` alone did not keep
  it idle. Set D-POCO `wifi-connection-mode=1` for R6/R7 (restored to 3 afterward). And D-HU↔D-POCO
  do **not** auto-reconnect their BT link on this rig once it has dropped (§7a: "A2DP … can drop and
  never reconnect"); a `svc bluetooth disable/enable` cycle on **D-POCO** forces it, and the runner
  does that before each Self-Mode run.

- Pokes connected far more often than §7a predicts this round: `NativeAA: Successfully poked … via
  HSP-AG`/`via HFP-AG` in 6 of 7 D-HU captures.

### Scripts

`hur-wifi-test-scripts/`: added `wds_r4_poke.sh` (brief-1 R1: session → exit → one
`ACTION_NATIVE_AA_POKE` → 90 s), `wds_r4_settings_pause.sh` (brief-1 R3), `wds_r4_autoreconnect.sh`
(brief-1 R4/R5), `bt_autodisc_run.sh` (brief-2 D-HU Native runs), `bt_autodisc_selfmode.sh`
(brief-2 R6/R7), `set_hu_settings_host.py` (multi-type incl. `<set>` settings writer, host-side).
Used `build_hur.sh` and `run_unit_tests.sh` unchanged for R0.

### State restored

Both `settings.xml` restored **byte-identical** to their round-start backups (`diff` clean on both
devices). Candidate `f0c20b8a` left installed on D-HU **and** D-POCO (D-POCO was on a stale
`74d0319c` build before). Both radios on, D-HU joined to `Pegue Cdesta` (5260 MHz), D-HU app
relaunched. `:5277` left listening on D-POCO. Gearhead never force-stopped. No background `logcat`
left running.

---

# Brief 1 — wifi-direct-stable-identity round 4

## R0 — build gate and unit tests — **PASS**

- Candidate `f0c20b8a`: md5 `190488f9…`, **1208 / 0**. `CredentialFreshnessPolicyTest` 4,
  `NativeRefreshPolicyTest` 6, `NativeHandoffPolicyTest` 28, `P2pGroupIdentityPolicyTest` 15,
  `BtAutoStartRearmPolicyTest` 18. DEX symbols `CredentialFreshnessPolicy` 5, `loopStep` 1,
  `networkComingUp` 1 — all present.
- Baseline `937e68b7`: md5 `3e5cb007…`, **1184 / 0**. All three DEX symbols 0.
- md5s differ.

## R1 — one poke, one group — **PASS**

`log-level=1`. Per arm: full Native AA session → `headunit://exit` → wait
`WifiDirectManager: Stopping and cleaning up…` → exactly one
`am start-foreground-service … ACTION_NATIVE_AA_POKE --es extra_mac DC:B7:2E:5E:4E:59` → 90 s.
The poke intent is accepted on D-HU (`ActivityManager: Background started FGS: Allowed …
ACTION_NATIVE_AA_POKE`), no fallback needed.

| | baseline `937e68b7` | candidate `f0c20b8a` |
|---|---|---|
| sessions formed after the poke | **3 / 3** (all recovered, but faulted first) | **3 / 3** clean |
| `startNativeAaQuietHost() requested` per poke | **2** (i1 +166 ms, i2 +204 ms, i3 +220 ms apart) | **1** |
| `createGroup SUCCESS` per poke | 2 | 1 |
| `createGroup failed (BUSY` per poke | **2** each | **0** |
| poke's pre-flight refresh | `refresh: no group is up, so one is created.` — the recreate that starts chain 2 | `refresh: a group was asked for …ms ago and has not answered yet` (WAIT) every time — **never** `no group is up` |
| distinct group BSSIDs the phone saw in the poke window | **3** (e.g. i1 `2E:8C…` → `22:8A…` → `72:7C…`) | 1 |
| `p2p-wlan0-N` jump per poke | **+2** (7→9, 10→12, 13→15) | +1 |
| phone `NETWORK_NOT_FOUND` | **2** per iteration | **0** |
| poke → `Incoming connection detected` | recovered at +49 s / +100 s / +79 s | +6.7 s / +4.3 s / +15.3 s |

Baseline decisive lines, i1 (`evidence/…/b1_r1_hu_base_i1.txt`):
```
10:08:28.377  startNativeAaQuietHost() requested. Removing old group if any...
10:08:28.541  refresh: no group is up, so one is created.
10:08:28.543  startNativeAaQuietHost() requested. Removing old group if any...      <- second chain, +166ms
10:08:29.080  5GHz … createGroup failed (BUSY …), removing group and retrying in 2s (retry 1/4)...
10:08:31.093  5GHz … createGroup failed (BUSY …) … (retry 2/4)...
   phone: Info response received bssid=22:8A:94:AC:64:FA … NETWORK_NOT_FOUND ×2 … then bssid=72:7C:29:8E:F1:B9 → CONNECTED_WIFI at 10:09:17
```
Candidate decisive lines, i1 (`b1_r1_hu_cand_i1.txt`):
```
09:58:34.690  a Native AA group create is claimed (bringing the Native AA group up); a refresh in the next 15s waits for it.
09:58:34.692  startNativeAaQuietHost() requested. Removing old group if any...
09:58:35.035  refresh: a group was asked for 340ms ago and has not answered yet, so nothing is remade underneath it.
09:58:35.566  5GHz … createGroup SUCCESS!
09:58:39.732  NativeAA: the live credentials still match the ones this handshake captured.
09:58:41.421  WirelessServer: Incoming connection detected from /192.168.49.54
```
Baseline faulted (double chain + BUSY + 3 groups + phone `NETWORK_NOT_FOUND`) on **3 / 3**;
candidate formed a session with **one** chain on **3 / 3**. The baseline sessions did eventually
recover (this rig's phone retries aggressively), but the reported mechanism — 2-3 P2P groups in a
row, phone stuck on `NETWORK_NOT_FOUND` — reproduced every time and the candidate removed it.

Benign `MATCH! Starting AapService` (1-2 per run) appeared during bring-up in both arms — the
phone's own BT reconnect; no extra `createGroup SUCCESS` attached (§7a).

## R2 — Type 3 names the group that exists — **PASS**

From R1's captures, both arms. For every Type 3 send: the SSID/BSSID of the last
`SUCCESS - Providing credentials` before `[TX] Wrote TYPE 3` vs the phone's
`GH.WIRELESS.SETUP: Info response received`.

- **Candidate: matched on every send (6/6).** i1 `02:36:DA…`→`02:36:DA…` & `9E:30:EC…`→`9E:30:EC…`;
  i2 `3E:8D…`/`96:0A…`; i3 `C2:D3…`/`1A:A1…`. `NativeAA: the live credentials still match the ones
  this handshake captured` (DEBUG) present at every send — the re-read runs. `the group changed
  while Type 3 was pending` warn/error lines: **0** (their firing on a clean run would be a finding
  per the brief — they did not fire). Phone `NETWORK_NOT_FOUND` = 0, `CONNECTED_WIFI` every session.
- **Baseline: differed exactly where R1 faulted.** The phone received an intermediate group name
  that had already been torn down (e.g. i1 `22:8A:94:AC:64:FA`), looped `NETWORK_NOT_FOUND` ×2, then
  eventually got the live one. `NETWORK_NOT_FOUND` = 2 in all three iterations.

## R3 — the wake poke waits while Settings is open — **PASS**

`log-level=1`, D-POCO Bluetooth off. Poke loop live before the test
(`NativeAA: Attempting active poke to device` at 10:17:43, before opening Settings).
`SettingsActivity` opened 10:17:48.7 (`Displayed … +1s90ms`), 60 s window, `KEYCODE_BACK` 10:18:49.

- `the settings screen is open, so the wake poke waits until it closes.` — **2** in the 60 s window
  (10:18:29.6 and 10:18:44.6). Brief said "exactly once"; the poke loop's cycle is ~15 s so it hits
  the poke-decision point ~2× in 60 s and defers on each — the loop **defers repeatedly**, which is
  the intended behaviour, not a fault.
- `NativeAA: Attempting active poke to device` in the 60 s window — **0**.
- `Stopping poke retry loop` in the 60 s window — **0** (this would be a FAIL — the loop must defer,
  not end).
- After BACK: `Attempting active poke` at 10:18:59.6 — within ~10 s (want ≤20 s), then again at
  10:19:44.

The three conditions that matter (0 pokes while open, loop not stopped, poke resumes ≤20 s after
close) all hold.

## R4 — the automatic reconnect still works — **PASS**

`log-level=1`. Session → `headunit://exit` → wait `Stopping and cleaning up…` → HU
`svc bluetooth disable` (self-reverts, raises `ACL_CONNECTED`) → 90 s. No poke.

- bt disable 10:21:26 → `MATCH! Starting AapService` 10:21:39.1 (~13 s) → `Initializing WiFi Mode:
  NATIVE` → `startNativeAaQuietHost() requested` (one) → `createGroup SUCCESS` (one, the 2nd of the
  run) → `Incoming connection detected` 10:21:47.9 → `SSL handshake complete` (session in ~22 s from
  the disable).
- `refresh: no group is up` = **0** for the reconnect (no `refresh:` line at all — the MATCH-driven
  re-init claims the create window directly; no manual poke, so no pre-flight refresh). `createGroup
  SUCCESS` = 1 for the reconnect. `p2p-wlan0-19` → `p2p-wlan0-20`. Throughput `dropped=0` at 49-50 fps.

## R5 — five-minute clean control — **PASS**

`log-level=1`. One session, untouched 5 min, then exit.

- `createGroup SUCCESS` stays at **1**, one `p2p-wlan0-21` throughout.
- `refresh: no group is up` once the session is established — **0**.
- `the group is being removed` while the session is up — **0** (the 2 instances are at launch
  bring-up, before the session, and one post-exit).
- Throughput `dropped=0` in every sampled window (fps 11-17 — this rig collapses projected fps on a
  parked car, phone-side adaptive rate, per `project_test_headunit_gearhead_and_nav_fps`; `dropped`
  is the metric that matters).
- Two benign `MATCH!` at launch (phone BT reconnect), no extra group.

## R6 — the stand-down window is claimed too — **FAIL (benign)**

`log-level=1`, `stand-down-station-for-wifi-direct=true`, D-HU joined to `Pegue Cdesta`. R1's
candidate procedure once.

- `a Native AA group create is claimed (waiting for this unit to leave its own network)` **appears**
  (10:29:53.603, 1.5 s before `StationStandDown.standDown$lambda$0: this unit has left its WiFi
  network` at 10:29:55.040). The station did disconnect (`wpa_supplicant: CTRL-EVENT-DISCONNECTED
  bssid=f4:52:46:60:8d:4e reason=3 locally_generated=1`).
- **But two `startNativeAaQuietHost() requested` chains at bring-up, not one:**
  - 10:29:53.823 — from `WIFI_P2P_STATE_CHANGED state=2 → P2P enabled, auto-starting Native AA quiet
    host` (this receiver path fires only when the stand-down cycles the P2P stack; it is absent in
    R1/R4/R5 where there is exactly one chain at launch)
  - 10:29:55.042 — from the `StationStandDown` completion callback

  Both chains claim the window and both `createGroup SUCCESS` land first-try (no BUSY), so the phone
  gets a valid group and **sessions form cleanly** (session 1 at 10:30:22, then a second after the
  poke at 10:30:59, both with `NETWORK_NOT_FOUND` = 0). The failure is narrow: the brief's PASS
  requires "exactly one `startNativeAaQuietHost() requested` follows [the claim line]", and the
  second chain the brief said had been fixed still forms during the stand-down wait — it just does
  not break on this rig because both creates succeed. `0a9a98d9` does not close this path; the
  `P2P enabled` auto-start still races the stand-down. `evidence/…/b1_r6_hu.txt`.

Also: `StationStandDown.restore` logged "the platform refused to re-enable this unit's WiFi
network" after the session, but the station was back on `Pegue Cdesta` (COMPLETED) minutes later on
its own — no operator repair needed.

## Brief 1 shipping read

**R1 and R2 both PASS → the WiFi-Direct-round-4 fix stack is PR-ready.** The double-chain and the
stale Type 3 are both fixed and verified on hardware, on 3/3 iterations, against a baseline that
faulted 3/3. R3/R4/R5 clean. **R6 is a FAIL:** the station stand-down still spawns a second
bring-up chain (`P2P enabled` auto-start + stand-down completion callback), benign on this rig but
the defect the brief wanted confirmed-gone is still there — worth a look before relying on
`stand-down-station-for-wifi-direct`.

---

# Brief 2 — bt-auto-start-disconnect round 1

## R0 — build gate and unit tests — **PASS**

Candidate `f0c20b8a`, **1208 / 0**. `BtAutoStartRearmPolicyTest` 18, `BtAutoDisconnectPolicyTest`
10. DEX symbols `BtAutoDisconnectPolicy` 3, `BluetoothDevicePicker` 16 — both present.

## R1 / R1b / R1retry — a watched device leaves (D-HU, Native) — **INCONCLUSIVE ×3**

Keys as §3, `auto-disconnect-bt-macs={DC:B7:2E:5E:4E:59}`, delay absent (5 s). Settled session
aged ≥80 s. Levers: R1 phone BT off, R1b/R1retry HU BT disable.

**Step 1 (the phone's link on the HU) — down in 3 of 4 runs.** At session +80-83 s:
`HeadsetClientStateMachine` / `A2DPSinkStateMachine` = `Connected` **0** in R1, R1b, R1retry
(R9 was the one run where the phone held the link — see R9). The projecting phone drops its BT ACL
to this HU **~0-5 s after every WiFi handoff** and does not restore it during the session
(`GH.WifiBluetoothRcvr: … BTIF_AV_ACL_DISCONNECTED` and `HUREV_AUTOSTART: Broadcast received:
ACL_DISCONNECTED` ~4 s after SSL; nothing after). It reconnects only once the session ends.

**Consequence:** the deliberate "watched device leaves" lever has no ACL to drop. In all three runs
the lever produced **no new `Bluetooth auto-disconnect:` line** and the session stayed up
(`netstat` 5288 `ESTABLISHED` through the watch).

**What did fire — on the natural drop — is exactly the feature working:**
```
R1     10:38:38.907  Bluetooth auto-disconnect: DC:B7:2E:5E:4E:59 went away; ending the session in 5000ms unless it comes back.
       10:38:43.914  Bluetooth auto-disconnect: not ending the session for DC:B7:2E:5E:4E:59 (up=true, age=9425ms).
R1b    10:43:23.069 / 10:43:28.072   same, age=9479ms
R1retry 10:57:17.930 / 10:57:22.940  same, age=5724ms
```
The auto-disconnect **armed** on the real `ACL_DISCONNECTED` and the 60 s-since-connected guard
**correctly held** every time (`age` well under 60000). That is the R2 cancel/guard branch and R4's
outcome, observed for free.

D-HU is bonded to nothing else that can be switched off on command (§brief), so a watched device
that keeps a BT profile through the session cannot be provided here. **R1's core assertion is
untestable by a deliberate lever on this rig** — but see R6.

## R2 — a blip does not end the session (D-HU, Native) — **INCONCLUSIVE** (delay + guard confirmed)

`auto-disconnect-bt-delay-seconds=30`. Lever HU BT disable.

- `went away; ending the session in **30000ms** unless it comes back.` (10:48:43.2) — the 30 s delay
  is honoured (vs R1's 5000 ms).
- `not ending the session for DC:B7:2E:5E:4E:59 (up=true, age=**30720ms**).` (10:49:13.6) — the delay
  expired and the 60 s-since-connected guard fired; session survived (`netstat` ESTABLISHED).
- `went away, but nothing is projecting; leaving the connection stack alone.` ×2 (10:48:32-33) —
  the poke socket's ACL cycling before the session was up; nothing torn down (this is R5).
- The `is back; the pending disconnect is cancelled` branch **cannot be produced** — the phone does
  not "come back within the delay" because its BT link does not persist during a session. That
  branch's coverage stays on `BtAutoDisconnectPolicyTest`.

## R3 — device comes back, Native re-arms (regression check) — **INCONCLUSIVE**

Not run separately: lever and starting condition identical to R1b, and the session-end it needs to
observe cannot be produced (R1). From R1b's capture: HU-bt cycle during a live Native AA session →
0 `Bluetooth auto-disconnect:` lines, session stays up (`netstat` ESTABLISHED), `MATCH` suppressed
while the session is live (count stays at the bring-up value, no re-init, no teardown). No
regression seen; the `networkComingUp` guard could not be exercised (needs an arrival landing while
a group is still being created — not reproducible on demand here).

## R4 — the first minute of a session (D-HU, Native) — **PASS — outcome B**

The finding of the round. From every D-HU Native session's first 60 s (R1, R1b, R1retry, R2):

- **Outcome B every time:** `not ending the session for <D-POCO> (up=true, age=Nms)` with N ∈
  {5724, 9425, 9479, 30720} ms — always < 60000. The phone's link **drops right after the handoff**
  (`went away; ending the session in Nms` fires at SSL + ~0.2-5 s), the 60 s-since-connected guard
  holds, the session survives.
- `dumpsys bluetooth_manager` on D-HU at session +80-83 s: phone `HeadsetClient` / `A2DPSink`
  **not connected** (0/0) in R1, R1b, R1retry. Connected in R9 (variable — §7a: "the phone's
  Bluetooth self-reverts too, sometimes"; "A2DP … unreliable in both directions").

**Reporter-facing sentence for the settings hint:** on this head unit the projecting phone drops
its Bluetooth link within seconds of the WiFi handoff and does not restore it while projecting, so
watching **the projecting phone** for auto-disconnect will not fire here. The watched device must be
one that keeps a Bluetooth profile with the head unit through the session — a helmet intercom, or a
second phone kept on HFP — not the phone being projected.

## R5 — the poke does not stand anything down — **PASS**

Opportunistic, across all D-HU captures. Pokes connected this round
(`NativeAA: Successfully poked … via HSP-AG` / `via HFP-AG`, 6/7 captures). For a poke with no
session up (R2, 10:48:32-33): `Bluetooth auto-disconnect: DC:B7:2E:5E:4E:59 went away, but nothing
is projecting; leaving the connection stack alone.` ×2, and the poke loop kept printing
`Attempting active poke` after. Nothing torn down.

## R6 — the whole cycle on Self Mode (D-POCO) — **PASS**

D-POCO keys as §3 (`connection-modes={self}`, `auto-start-self-mode=false`, `kill-on-disconnect=false`,
`auto-start-bt-macs`/`auto-disconnect-bt-macs`=`{11:46:03:10:33:59}`, `wifi-connection-mode=1`),
overlay op `allow`, `:5277` listening. D-HU runs no app; D-POCO↔D-HU BT link forced up by a D-POCO
radio cycle. D-HU's own adapter is the lever (self-reverts ~13 s).

`am start MainActivity` → **no session in 15 s** (`SSL handshake complete` = 0). Then:

**Step 1 — device leaves, then returns, Self Mode auto-starts:**
```
11:25:39      D-HU svc bluetooth disable
11:25:40.514  Bluetooth auto-disconnect: 11:46:03:10:33:59 went away, but nothing is projecting; leaving the connection stack alone.
11:25:52.462  (phone) GH.WifiBluetoothRcvr: … ACL_CONNECTED, device …33:59            <- HU adapter reverted
11:25:52.488  AutoStartReceiver: BT Device connected: Navegadortz2 (11:46:03:10:33:59)
11:25:52.488  MATCH! Starting AapService via Bluetooth Auto-start...
11:25:52.525  AapService: Bluetooth auto-start: BtAutoStartActions(clearUserExit=true, forceRearmWireless=false, armWirelessIfIdle=false)
11:25:52.529  Auto-connect: begin (Bluetooth auto-start, mode=PILL)
11:25:52.540  MainActivity: Bluetooth auto-start: forcing a Self Mode launch
11:25:52.632  SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...
11:25:53.345  SSL handshake complete                                                   <- session 1, by the trigger
```

**Step 2 — device leaves during the settled session (aged 80 s), session ends, re-arms:**
```
11:27:14      D-HU svc bluetooth disable
11:27:16.795  Bluetooth auto-disconnect: 11:46:03:10:33:59 went away; ending the session in 5000ms unless it comes back.
11:27:21.800  Bluetooth auto-disconnect: 11:46:03:10:33:59 stayed away; ending the session the way the Exit button does.
11:27:21.803  Hiding reconnecting overlay - the session ended
11:27:21.924  User exit cooldown active for 5000ms
11:27:21.925  Self Mode disconnected. Not restarting.
11:27:23.001  Bluetooth auto-disconnect: keeping the wireless bring-up down until something re-arms it.
              — 2.9 s of nothing —
11:27:25.909  BT Device connected … MATCH! … forcing a Self Mode launch … SelfMode: AA 17.4+ detected
11:27:26.415  SSL handshake complete                                                   <- session 2, by the trigger
```

**PASS:** 2 sessions started by the Bluetooth trigger (both `forcing a Self Mode launch` → SSL),
1 session ended by the trigger (full end sequence in order), no relaunch between the end and the
re-arm. Actions line `forceRearmWireless=false, armWirelessIfIdle=false` both times. Both `MATCH`es
corroborated by phone-side `ACL_CONNECTED`. Gap `stayed away` → next `MATCH` = 4.1 s (brief expected
~9 s; shorter because the HU adapter reverted faster this run). `evidence/…/b2_r6_phone.txt`.

## R7 — "Close app on disconnect" completes the macro (D-POCO) — **PASS**

R6 step 2 with `kill-on-disconnect=true`.

```
11:32:15.210  Bluetooth auto-disconnect: … stayed away; ending the session the way the Exit button does.  (OHU pid 10534)
11:32:15.329  Self Mode disconnected. Not restarting.                                                     (pid 10534)
              — app killed —
11:32:22.404  AutoStartReceiver: BT Device connected … MATCH! Starting AapService                          (OHU pid 11106, fresh process)
11:32:23.345  App launched via: Bluetooth auto-start
11:32:23.457  MainActivity: Bluetooth auto-start: forcing a Self Mode launch
11:32:24.749  SelfMode: AA 17.4+ detected …
11:32:25.565  SSL handshake complete                                                                       (pid 11106)
```

The OHU process id went **10534 → 11106** between the session-end and the re-arm — the app was
killed by `kill-on-disconnect` and **cold-started** by the manifest `AutoStartReceiver` on the
adapter-revert `ACL_CONNECTED`, producing a fresh Self Mode session. `App launched via: Bluetooth
auto-start` (the cold-start marker, absent in R6's warm re-arm) confirms it. That is the motorbike
reporter's two macros from one setting plus the existing "close app on disconnect" toggle.

(`forcing a Self Mode launch` count 3: the receiver delivered the launch intent twice on the
re-arm, `handleLaunchIntent` de-duped to one session — benign.)

## R8 — a healthy Helper group is not torn down to re-arm it (D-HU, Helper) — **PASS**

`wifi-connection-mode=2`, `helper-connection-strategy=1` (WiFi Direct), phone BT on, nothing joins.

```
11:02:11.147  WifiLauncher: Initializing WiFi Mode: HELPER
11:02:11.577  WifiDirectManager: P2P Group created (fresh this session).
              — HU svc bluetooth disable, ~14 s revert —
11:02:47.230  AutoStartReceiver: BT Device connected: POCO X3 NFC (DC:B7:2E:5E:4E:59)
11:02:47.231  MATCH! Starting AapService via Bluetooth Auto-start...
11:02:47.313  AapService: Bluetooth auto-start: BtAutoStartActions(clearUserExit=true, forceRearmWireless=false, armWirelessIfIdle=false)
```
After the re-arm: `Initializing WiFi Mode` still **1**, `P2P Group created (fresh` still **1**,
`createGroup SUCCESS` 0. **The group was not torn down.** `forceRearmWireless=false` (the non-Native
value) confirms `ae35e6a5`: auto-start on Bluetooth now fires for a non-Native wireless mode and
does it without churning the group. (First attempt had D-POCO's BT off — no `MATCH` — re-run with it
on.)

## R9 — the empty list is the off switch (D-HU, Native) — **PASS**

`auto-disconnect-bt-macs` deleted. Settled session, HU BT disable, 45 s.
`grep -ac "Bluetooth auto-disconnect:"` = **0** for the whole capture, session survived the cycle
(`netstat` 5288 ESTABLISHED). Notably the phone's HFP+A2DP link to the HU **was** up at session +83 s
this run (0/0 → 1/1), so the zero auto-disconnect lines are because the feature is off, not because
there was nothing to react to.

## Brief 2 shipping read

**The auto-disconnect + generalised auto-start feature works end to end** — proven in Self Mode
(R6/R7): the Bluetooth trigger starts a session, a watched device leaving ends it cleanly (Exit-button
path, then wireless bring-up held down), and the device returning re-arms it — with `kill-on-disconnect`
it kills and cold-starts the app. Helper-mode auto-start fires without group churn (R8). The empty
list is the off switch (R9).

**The Native-AA-on-D-HU side is INCONCLUSIVE for the disconnect lever** because this HU's projecting
phone drops its Bluetooth link seconds into every session (R4). That is a rig property, not a branch
defect — and it is itself the round's finding: **the settings hint must tell users the watched
device has to keep a Bluetooth profile with the head unit through the session; the phone being
projected does not qualify on this hardware.** Coverage for the Native-AA disconnect path,
the "device returns within the delay → cancel" branch, and the `networkComingUp` guard stays on
`BtAutoDisconnectPolicyTest` / `BtAutoStartRearmPolicyTest`.

Branch is PR-ready pending: (a) the settings-hint wording above, (b) confirmation this branch is
rebased on a WiFi-Direct branch that has itself addressed R6 of brief 1.

---

## Anything the briefs did not ask about

- **The phone's BT-link-during-session is bimodal on this rig**: dropped for good ~seconds after the
  handoff in R1/R1b/R1retry/R2, but held to +83 s in R9 — same build, same procedure, ~15 min apart.
  Whatever governs it is not in the app.
- **`AapProjectionActivity: call raise finished - the projection is back in front`** logs twice at
  the start of every Self Mode session on D-POCO (R6/R7) with no call involved — cosmetic, the
  call-raise episode is opened and closed on plain resume.
- On the software-decode-free Native runs the poke connected almost every time (`via HSP-AG` and
  `via HFP-AG` both seen), which is the opposite of the §7a "pokes rarely connect" note — poke
  connectivity on this rig is per-session, not a fixed property.
