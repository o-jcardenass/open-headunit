# connection-failure-banner — round 2 results

**Candidate:** `fix/session-lifecycle-and-diagnostics` @ `bd3d7b99` on `fork`       **Baseline:** none (single-APK round)
**APK md5:** `e1251913b58306845e71b4604d4917d7` (`com.andrerinas.headunitrevived_3.2.6_debug.apk`)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, `head-unit-make: Royal Enfield`
**Date:** 2026-08-21

## Setup notes

- **§0 (rig fix): confirmed root shell (`adb shell` is already uid 0), `shared_prefs` was
  `root:root 775`. `chown -R u0_a168:u0_a168` fixed it, verified `u0_a168:u0_a168 775` afterward,
  and it held through the whole round including an `adb install -r`. No root-shell fallback
  needed; every disk-persistence check below is a full read, not an in-process substitute.
- **APK mismatch caught mid-round.** R0's build (`assembleGithubDebug`) only produces the APK in
  `apks/`; it does not install it. The first pass through R1/R2a/R2b ran against whatever was
  already installed on the rig (md5 `036ab497...`, `lastUpdateTime` 19:13:35 — a stale build from
  an earlier session today, not this candidate). Caught by §5's own "confirm which APK is live"
  check after R2b came back with the banner still showing even with both hotspot fields set, which
  contradicts `bd3d7b99`'s own source. `adb install -r` onto the correct freshly-built APK (md5
  `e1251913...`), re-verified, and **R1, R2a, R2b were all redone** against the correct build. Only
  the redone results are reported below; the stale-APK numbers are discarded entirely. `install -r`
  preserved `settings.xml` and the `shared_prefs` ownership fix, as the template says it should.
- **One R1 attempt discarded for early force-stop.** First redo attempt was force-stopped at ~25s
  into the run instead of the required 60s dwell (my own timing error, not a rig or candidate
  issue). The mechanism had already fired within 1s of launch, so no signal was lost, but the run
  didn't meet its own protocol — discarded, `connection-issue-hotspot-config` reset to 0, and
  rerun clean for the full 60s. Kept as `r1-attempt1-INVALID-early-stop.txt` for the record, not
  used in any verdict below.
- **SoftAP transience (§7a) confirmed again**, twice: it dropped entirely (interface gone) between
  the initial `start-softap` check and the R1 redo ~10 minutes later, and was restarted and
  re-verified (`ip addr show wlan2` showing an assigned IP) immediately before every softAP-dependent
  launch, never from a check made earlier in the round.
- `hur-wifi-test-scripts/` inventory: used `build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh`
  (multi-key writes, no intermediate relaunch), `set_hu_pref.sh` pattern not needed. No script
  added or changed this round.
- Settings restore at close-out used the pushed-script pattern (`sh /data/local/tmp/restore_settings.sh`)
  after confirming the inline `run-as sh -c 'cp ...'` form fails with `cp: Needs 1 argument`,
  exactly as the template documents — not re-litigated, just re-confirmed.
- Phone: POCO X3 NFC, already bonded to the head unit (`Navegadortz2`); Bluetooth was off at the
  start of the session and enabled for R5 only.

## R0 — build and unit gate

**PASS**

- `./gradlew :app:assembleGithubDebug` — clean.
- `./gradlew :app:testGithubDebugUnitTest` — clean.
- Suite counts: `ConnectionIssueBannerPolicyTest`=24, `ConnectionIssuesTest`=8,
  `CredentialsHandoffTest`=8, `SoftApBandPolicyTest`=10. **Total: 676**, matching the brief's
  prediction exactly.

## R1 — the hotspot condition, raised by the hardware. The point of the round.

**PASS**, all four conditions.

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=1`, `log-level=1`;
  `hotspot-ssid`/`hotspot-password` and all four `connection-issue-*` keys deleted.
- Radio state: `cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5`, confirmed via
  `ip -4 addr show wlan2` → `192.168.231.139/24`, frequency 5745 MHz (5GHz band honored).
- Discard-rule check: clean (`createGroup SUCCESS`=0, `MATCH! Starting AapService`=0 — expected,
  this transport doesn't form a P2P group).
- Decisive log lines:
  - `08-21 21:20:58.692 E/OPENHU SoftApCredentials: The access point on wlan2 is up, but this
    device will not let apps read its name, so there is nothing to hand the phone. ...` — count 1.
  - `SoftApCredentials: SUCCESS - Providing credentials from` — count **0**, across both the
    60s-dwell capture and the relaunch capture.
  - Relaunch: exactly one `MainActivity: showing the connection issue banner for
    HOTSPOT_CONFIG_UNREADABLE`.
  - `connection-issue-hotspot-config` read back **non-zero** (`1787365258711`) after the 60s dwell.
- AP was still up at the end of the dwell (`wlan2` still had its IP).
- Screenshot confirms exact banner text: "This unit will not tell the app its hotspot name and
  password, so your phone had nothing to join. Tap to enter them by hand."

## R2 — the remedy retires the banner, and keeps the record

**R2a — PASS.** Name only, no password. Banner still showed
(`showing the connection issue banner for HOTSPOT_CONFIG_UNREADABLE`), confirmed by screenshot.

*Side observation, not a brief requirement:* the stamp was cleared to 0 by this same run anyway —
see R2b's finding below, same mechanism, same root cause.

**R2b — MIXED, one half PASS and one half FAIL.**

- Banner-hiding half: **PASS**. With both `hotspot-ssid=OHU-TEST` and `hotspot-password=testtest1234`
  set, no `showing the connection issue banner` line fired at all, and the screenshot confirms no
  banner on screen.
- Stamp-survival half: **FAIL**. `connection-issue-hotspot-config` was seeded to `1755800000000`
  immediately before the run and read back as `0` afterward.
- **Root cause, verified against source, not a rig or candidate defect in the four fixes under
  test:** `SoftApCredentialsProvider.kt:429-430` calls `ConnectionIssues.clear(context,
  HOTSPOT_CONFIG_UNREADABLE)` unconditionally whenever `publish()` reaches its SUCCESS branch —
  including R2a's blank-password case, which logs `SoftApCredentials: SUCCESS - Providing
  credentials from ...` right alongside its own `No passphrase for 'OHU-TEST'` warning. This code
  path is untouched by `bd3d7b99`'s four listed fixes (`ConnectionIssueBannerPolicy.kt`'s
  `remedyApplied()` only controls what the *banner* shows, not whether the stamp is cleared). So
  the brief's stated design goal ("the record is kept, not cleared") holds for the screen but not
  for the persisted stamp: the very first time both fields are set and a connection is attempted,
  the stamp will be wiped by this pre-existing success path, same as R2a's degraded case. Confirmed
  reproducible: this happened identically on both the stale-APK first pass and the corrected-APK
  redo, so it is not an artifact of the APK mixup either.

**R2c — PASS**, all parts including the negative control.

- First half (`native-ap-transport=0`, no `static-bssid` override, `connection-issue-bssid`
  seeded): banner named `BSSID_UNAVAILABLE`; confirmed exact wording "This unit could not read its
  own WiFi MAC address (BSSID), so your phone was never told which network to join." (design gap 3
  fix, confirmed on screen). `connection-issue-bssid` unchanged at `1755800000000`.
- Second half (`static-bssid=AA:BB:CC:DD:EE:FF`): no banner line, screenshot confirms absent.
  `connection-issue-bssid` still unchanged at `1755800000000` — unlike the hotspot case, the BSSID
  stamp genuinely survives, because there is no equivalent unconditional-clear call on this path
  within the short window tested.
  `createGroup SUCCESS`=1 in the first half (WiFi Direct actually forms a group here — expected,
  not contamination).
- Control (`static-bssid=0`, relaunch): banner is back, `showing the connection issue banner for
  BSSID_UNAVAILABLE`.

## R3 — the tap reaches both fields

**PASS**, all four conditions. Screenshot confirms: Settings opened in Basic mode; search box read
exactly "Hotspot name and password"; both "Hotspot name (manual)" and "Hotspot password (manual)"
rows visible with no scrolling; no keyboard open over the list.

## R4 — a record only shows on a route that can produce it

Stamp order used throughout: `connection-issue-hotspot-config`=1755800002000 (newest),
`connection-issue-bssid`=1755800001000 (1s older), `connection-issue-bt-silent` cleared for R4a/R4b,
then seeded to 1755800003000 (newest of all three) for R4c.

**R4a — PASS, both halves.**
- `native-ap-transport=0` (WiFi Direct): banner named `BSSID_UNAVAILABLE`, **not**
  `HOTSPOT_CONFIG_UNREADABLE` despite the latter being the newest stamp overall — the
  route-relevance filter, not raw recency, decided it.
- Flipped to `native-ap-transport=1`, same stamps, no re-seed: banner named
  `HOTSPOT_CONFIG_UNREADABLE`.

**R4b — PASS**, answered directly by R4a's own two captures (the brief calls it "the same two
launches read the other way round"): under `native-ap-transport=1`, `BSSID_UNAVAILABLE` never
appeared even though its stamp was still non-zero and older only by 1s.

**R4c — PASS, both parts.** `wifi-connection-mode=2`, `helper-connection-strategy=2`, all three
stamps seeded (bt-silent now newest at 1755800003000): **no banner at all**, no
`showing the connection issue banner` line, screenshot confirms nothing on screen. Control
(`wifi-connection-mode=3`, same stamps, relaunch): banner is back, naming `BLUETOOTH_SENT_NO_DATA`
(correctly the newest of the two conditions relevant to Native AA hotspot transport). Screen reads
fine both ways; no legibility concern to flag from watching it happen.

## R5 — nothing above broke a real session

**PASS.** `native-ap-transport=0`, all four `connection-issue-*` keys deleted first, real Native AA
session against the paired POCO X3 NFC phone.

- Clean-run order followed: head unit launched with phone Bluetooth off, ~15s settle, phone
  Bluetooth re-enabled, then hands off.
- `Handshake: SSL handshake complete` at `21:31:38.453`, ~7.4s after the phone's Bluetooth came
  back up.
- Discard-rule check: `createGroup SUCCESS`=1 (single group, `p2p-wlan0-4`); the one
  `MATCH! Starting AapService` line (21:31:34.575) is the phone's own reconnect I deliberately
  triggered by re-enabling its Bluetooth, with zero group churn attached — not contamination per
  the template's refined rule. A `p2p-wlan0-3` teardown appears *before* the first `createGroup
  SUCCESS`, which is the documented benign case (a stale prior-round group being torn down at
  launch), not a second group.
- After a clean `headunit://disconnect`-equivalent exit and force-stop, all four
  `connection-issue-*` keys were **absent** from `settings.xml` — the grep for them returned
  nothing at all, not even zeros.

## R6 — round-wide invariant: nothing raises by itself

**PASS.** Across all 11 non-R1 captures (`r2a`, `r2b`, `r2c_1/2/3`, `r3`, `r4a_1/2`, `r4c_1/2`,
`r5`):

```
grep -ac "the phone connected over Bluetooth and answered nothing we sent" *.txt   → 0 everywhere
grep -ac "BSSID is still masked/empty" *.txt                                       → 0 everywhere
grep -ac "MainActivity: could not read the connection issue record" *.txt          → 0 everywhere
grep -ac "ConnectionIssues: settings unavailable, not recording" *.txt             → 0 everywhere
```

## Anything the brief did not ask about

- **The R2b finding above is the headline result of this round**, more than any single PASS: three
  of the four design-gap fixes are confirmed working exactly as designed (R2a screen text, R2c
  BSSID wording and filtering, R4's relevance filtering), but the fourth ("the record is kept, not
  cleared") is only half-true. The banner correctly stops showing once the user has done the work
  it asked for, but the underlying audit trail (`connection-issue-hotspot-config`) still gets wiped
  the moment that fix works — by a different, older code path (`SoftApCredentialsProvider.kt:430`)
  that this candidate never touched. Anyone relying on that stamp for anything beyond "should I
  show the banner right now" (support diagnostics, a future settings-export audit, etc.) will find
  it looks like the condition never happened at all, 100% of the time, as soon as the user fixes
  it. Whether that's acceptable depends on what the stamp is *for* — worth a decision, not
  necessarily a code change, before this ships.
- The APK-mismatch catch cost real time this round; worth calling out as a reminder that R0's build
  step and the actual on-device install are two separate steps, and the second one is easy to skip
  when a device already has *some* build of the app installed and launches without complaint.
