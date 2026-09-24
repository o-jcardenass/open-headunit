# hotspot-endpoint-poison — round 3 results

**Candidate:** `fix/hotspot-endpoint-poison` @ `29abed1773c0`       **Baseline:** none (per brief; round 1's P1 already reproduced the bug)
**APK md5:** `b3fc126f4a3779411f37f768be0c1c9f`
**APK sha256:** `df493dd5c4fae1df2148521e9c1a96a85df7da200dced442874349f69692f821`
**Unit:** UNISOC MT50 (D-HU, rooted), Android 14; D-MOTO (Motorola edge 30 neo) as the thread's phone
**Date:** 2026-09-23/24

## Setup notes

- Inventoried `hur-wifi-test-scripts/`; used `build_hur.sh`, `run_unit_tests.sh` and `set_hu_prefs.sh`
  as-is. No new script needed.
- `shared_prefs/` was **not** root-owned this round (`u0_a176:u0_a176`), unlike the general warning in
  template §7a; checked with `stat` before relying on anything, and no run in this round depended on an
  app-written value anyway.
- **Restoring `settings.xml` at the end via `run-as $PKG sh -c 'cat /data/local/tmp/... >
  shared_prefs/settings.xml'` failed** ("No such file or directory") — `run-as`'s sandbox cannot read
  `/data/local/tmp`. Since D-HU is rooted, pushed the backup directly to the app's `shared_prefs/`
  path as root and `chown`/`chmod 660`'d it back to `u0_a176:u0_a176` instead; that worked. Worth
  using this pattern directly next time a round needs a full-file settings restore on this rig, rather
  than the run-as+cat route.
- **R7's first attempt was discarded and re-run.** D-HU's `native-preferred-device-mac` is empty and
  `native-poke-all-paired-devices` defaults to on, so the app walks every bonded phone — and D-POCO
  (still bonded from an earlier thread, per round 1/2's own setup notes) answered the poke and never
  let the loop move past it: `NativeAA: Phone reports it is still joining — extending the settling
  window.` repeated for the whole attempt (over 4 minutes, capture `r7-hu.txt`) with no session ever
  landing. Diagnosed mid-round and fixed by `adb shell svc bluetooth disable` on **D-POCO** (not D-HU;
  per template §7a's own lever, toggling the *holder's* radio is what isolates a pairing) — the retried
  bring-up (`r7-retry-hu.txt`) then reached D-MOTO within the same run and landed normally at
  `00:00:56.250`. POCO's Bluetooth was re-enabled afterward. This is a rig pairing-state issue, not a
  candidate defect (nothing in this branch touches poke device selection), but it cost about 6 minutes
  and is worth a standing setup step for any future round on this rig that needs a specific phone:
  disable D-POCO's Bluetooth first, unless the round is specifically testing multi-phone selection.
- **Because of that same pairing issue, R4's three bring-ups (run before the diagnosis) landed against
  D-POCO, not D-MOTO**, even though §3 names D-MOTO as this round's phone. R4's own PASS conditions are
  all head-unit log lines (the banner text, the session-landed line, the settings key) with no
  phone-identity dependency, so this does not affect R4's verdict, but R4 was not actually exercised
  against the round's stated phone. Flagging per the brief's own instruction to name a phone swap;
  this one was involuntary rather than deliberate.
- **A tester-side stray-process bug, not the app's:** the background `logcat` capture for R4's
  bring-up 1 was not killed before starting bring-up 2's capture (and likewise 2→3, and R4→R7), so for
  part of the round up to 4 `logcat` client processes ran concurrently against D-HU — clearing the
  buffer does not stop an already-attached client from receiving new lines, so each earlier file kept
  accumulating output from later runs. Every decisive line quoted in this report was verified against
  its own timestamp inside the correct run's time window, so no finding here rests on contaminated
  data, but the raw `r4-bringup*.txt` files each carry trailing lines from later runs (including R7's
  discarded first attempt) past their own relevant window — noted so a later reader of the raw
  captures isn't confused by the overlap. Killed the stray processes once noticed; only one `logcat`
  client ran at a time for the remainder of the round.
- No reboot needed or performed this round, per the brief. Consequently the "first `start-softap` after
  a reboot comes up degraded" rig quirk did not apply: the shell AP for R4 came up clean on the first
  try (`192.168.221.246`, `00:27:15:43:06:6a`, `5765` MHz).
- Captures: `hotspot-endpoint-poison-round3-captures.zip`, sha256
  `c106b2a057132af96b5914d38f3615da7ddb084b72f314948b26b9d5177660f4`, uploaded to the existing
  `rig-evidence-hotspot-endpoint-poison` release (round 1 and round 2's assets stay alongside it).
  Contains the candidate APK, all five capture files (including the discarded `r7-hu.txt`), and the
  settings.xml backup taken at the round's start.

## R0 — Build gate

**PASS**

- SHA `29abed1773c0` (six commits on `main` `dd454eed1`: `fba8e7c8d`, `cf3b693c9`, `018d5af67`,
  `30a1f6d8d`, `24682388d`, `29abed177`), matches the brief exactly.
- JVM gate: 2340 tests, 0 failures, 0 errors (aggregated from `app/build/test-results/**/*.xml`),
  matches the brief's `2340/0`.
- `adb install -r`; installed APK's `pm path` md5 (`b3fc126f4a3779411f37f768be0c1c9f`) matches the
  built APK's md5.
- `ACTION_QUERY_STATE` reply: `"commit":"29abed1773c0"`.

## R4 — A moved password raises the banner, and it stays up

**PASS**

- Setup: shell AP up (`OHU-HOTSPOT`/`ohutest12345`, `192.168.221.246`, `00:27:15:43:06:6a`, 5765 MHz).
  Keys written before bring-up 1: `wifi-connection-mode=3`, `native-ap-transport=1`,
  `hotspot-interface=wlan2`, `hotspot-ssid=OHU-HOTSPOT`, `hotspot-password=ohutest12345`,
  `static-bssid=0`, `auto-enable-hotspot=false`, `allow-external-configuration=true`; all read back
  before the first bring-up.
- **Bring-up 1** (identity capture): `WifiLauncherNative: access point identity ssid=OHU-HOTSPOT
  bssid=00:27:15:43:06:6A ip=192.168.221.246 ... stable=no (the access point came back but its address
  moved from 192.168.41.88 to 192.168.221.246 ...)` at `23:51:26.511`; `SSL handshake complete` at
  `23:51:39.688`.
- Force-stopped, seeded `soft-ap-advertised-ssid=OHU-HOTSPOT`,
  `soft-ap-advertised-bssid=00:27:15:43:06:6A`, `soft-ap-advertised-ip=192.168.221.246`,
  `soft-ap-advertised-psk-digest=`(64 zeros), `connection-issue-stale-endpoint=0`; all five read back
  correctly.
- **Bring-up 2:** `NativeAA: the WPP endpoint advertised on the access point at 192.168.221.246 no
  longer matches (password); a phone holding it needs this head unit forgotten in Android Auto.` at
  `23:52:15.352` — exactly once, only `password` in the brackets. `NativeAA: WiFi session landed.` at
  `23:52:17.670`, after the banner line in the same bring-up.
- Force-stopped, read `connection-issue-stale-endpoint` = **`1790225535352`** (E), non-zero — this is
  the fix working; round 2 read `0` here for the same step.
- **Bring-up 3** (also a landing): no `no longer matches` line anywhere in the capture; `NativeAA: WiFi
  session landed.` at `23:52:43.468`.
- Force-stopped again, read `connection-issue-stale-endpoint` = **`1790225535352`**, unchanged from E.
- **Recorded, not graded:** no `MainActivity: showing the connection issue banner for
  PHONE_HOLDS_STALE_ENDPOINT` line appeared in any of the three bring-ups' captures — expected, since
  every bring-up here used `--ez no_ui true` and never raised `MainActivity` to the foreground, so
  the on-screen banner path was never exercised. No screenshot was taken for the same reason.
- Note: per Setup notes, all three bring-ups in this run landed against D-POCO rather than D-MOTO; none
  of the PASS conditions above depend on which phone connected.

If the change had done nothing, E would have read `0` after the force-stop, as it did in round 2. It
did not.

## R7 — WiFi Direct: the retire still works there

**PASS** (on the retried attempt; the first attempt is discarded, see Setup notes)

- Reverted per round 1 §4: `native-ap-transport=0`, `hotspot-ssid=""`, `hotspot-password=""`,
  `static-bssid="0"`, `hotspot-interface=""`; all read back correctly. Shell AP was stopped first
  (D-HU's WiFi HAL cannot hold a SoftAP and a WiFi Direct GO interface together).
- Seeded `connection-issue-stale-endpoint=1790000000000`, read back correctly before the bring-up.
- **First attempt (discarded):** poked D-POCO repeatedly for the full run with no session landing
  (`r7-hu.txt`) — a rig pairing-state issue, see Setup notes. Force-stopped and re-run after disabling
  D-POCO's Bluetooth.
- **Retried bring-up (`r7-retry-hu.txt`, counted):**
  - `WifiDirectManager: group identity ssid=DIRECT-QS-MT50YT610E4GFPSLU ...` at `23:59:17.276`.
  - Zero `access point identity` lines in the capture.
  - `NativeAA: Connection accepted from motorola edge 30 neo (A0:46:5A:97:E4:95) ...` at `00:00:42.695`.
  - `WirelessServer: Incoming connection detected from /192.168.49.28` at `00:00:55.984`.
  - `NativeAA: WiFi session landed.` at `00:00:56.012`.
  - `SSL handshake complete` at `00:00:56.250`.
- Force-stopped, read `connection-issue-stale-endpoint` = **`0`** afterward.

If the change had leaked into WiFi Direct, the key would have still read `1790000000000`. It read `0`.

## Anything the brief did not ask about

- **R4's root-cause fix (round 2's most consequential finding) is confirmed closed.** Round 2 showed
  `NativeAaHandshakeManager.retireStaleEndpointRecord()` clearing `connection-issue-stale-endpoint` on
  the very same bring-up that raised it, because a hotspot-route phone dialling a dead address is never
  *refused* (it just times out on its own network layer) and so never sets
  `dialRefusedSinceLastLanding`. This round's E staying non-zero and unchanged across a second landing
  is exactly the observable that finding predicted would be fixed by `24682388d`.
- **D-POCO's continued Bluetooth bonding to D-HU is now a recurring cost across this thread's rounds.**
  Round 2's setup notes flagged a stale WPP-over-TCP record from D-POCO; this round hit a second,
  independent cost from the same underlying fact (D-POCO still paired) — it absorbed the poke rotation
  and blocked D-MOTO from being reached at all until its radio was switched off by hand. Unless a round
  specifically needs D-POCO, forgetting/unpairing it from D-HU once (rather than working around it every
  round) would likely save time across this and future D-MOTO-based rounds.
- Settings.xml was restored to its exact round-start contents at the end of the round (see Setup
  notes on the restore-method correction).
