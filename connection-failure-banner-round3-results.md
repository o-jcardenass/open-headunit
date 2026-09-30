# connection-failure-banner - round 3 results

**Candidate:** `fix/session-lifecycle-and-diagnostics` @ `3ad29942` on `fork`       **Baseline:** none (single-APK round)
**APK md5:** `24727928963c470164ef992d59eb4a31` (`com.andrerinas.headunitrevived_3.2.6_debug.apk`)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, `head-unit-make: Royal Enfield`
**Date:** 2026-08-21

## Setup notes

- **§0 (rig fix): confirmed.** `shared_prefs` was already `u0_a168:u0_a168` (the app's own uid) at
  round start, so round 2's `chown` held through everything since, including this round's own
  `adb install -r`. No re-chown needed. Verified again after install: unchanged.
- **APK installed and verified before R1**, per §0's warning about round 2's stale-build loss.
  `install_and_launch.sh SKIP_BUILD=1` installed `com.andrerinas.headunitrevived_3.2.6_debug.apk`;
  local and on-device md5 both `24727928963c470164ef992d59eb4a31`, confirmed match before any run.
- **`hur-wifi-test-scripts/` inventory**: used `build_hur.sh`, `run_unit_tests.sh`,
  `install_and_launch.sh`, `set_hu_prefs.sh` (multi-key writes throughout, no intermediate
  relaunch). No script added or changed this round.
- **`connection-issue-dismissed-at` leftover caused a real false-negative in R7e's first attempt.**
  R3 left a real-clock dismissal timestamp (`1787372294474`) in `settings.xml`. R7e then seeded
  `connection-issue-bssid` with the brief's own constant (`1755800000000`), which is older than
  that leftover dismissal, so `ConnectionIssueBannerPolicy.bannerFor()`'s
  `newest.raisedAtEpochMs <= dismissedAtEpochMs` check correctly suppressed the banner as "already
  dismissed", and the first R7e attempt landed on a live Android Auto session instead (see below).
  Not a candidate defect, the suppression logic is working exactly as designed in
  `ConnectionIssueBannerPolicy.kt`. It is a testing-methodology gap worth flagging for future
  briefs that reuse the constant seed values across a multi-run session: any run seeding a
  `connection-issue-*` stamp should also clear `connection-issue-dismissed-at` unless the run is
  specifically about dismissal, since the two are compared directly and the seed constants are
  older than any real on-device clock reading. Fixed by deleting the leftover key and re-seeding;
  R7e's real result is from the clean redo (`r7e-final.txt`).
- **R7e's first redo (`r7e-redo.txt`) was discarded for a different reason.** The phone's own
  Bluetooth reconnect (left on from R5/R8's earlier runs) beat the banner tap and formed a live
  session, which auto-brought `AapProjectionActivity` to the foreground between the screenshot and
  the tap landing, so the tap that was aimed at the banner landed on the live Google Maps surface
  instead. Turning the phone's Bluetooth off before relaunching (a pattern §7a already documents)
  fixed it on the next attempt, kept as `r7e-final.txt`.
- **A screenshot taken immediately after a tap can be stale.** R7e's post-tap screenshot showed the
  home screen unchanged, but a `uiautomator dump` taken moments later showed Settings had in fact
  opened with the right content: the activity transition simply hadn't painted yet when the first
  `screencap` ran. Re-screenshotted after the dump to get a definitive shot; no defect, just a
  timing note for the next round doing UI verification this way.
- **`updateConnectionIssueBanner()` only runs on `onResume()`**, not periodically. R1 and R3 step 1
  both needed an explicit force-stop-then-relaunch cycle to see the banner after the condition
  raised mid-session, because the app was already resumed when the condition fired and nothing
  re-triggers the check while it stays foregrounded. Not a defect, this matches round 2's own R1
  methodology, but worth stating plainly since the brief's wording ("let the condition raise by
  itself, confirm the banner") could be read as not needing the relaunch.
- Phone: POCO X3 NFC (`4f4027e9`), bonded throughout (head unit's own BT name reads
  `Navegadortz2` from the phone's side, confirmed via `dumpsys bluetooth_manager`). Bluetooth
  cycled off/on repeatedly through the round as the lever for triggering/suppressing sessions per
  §7a; each cycle verified with `dumpsys bluetooth_manager`, never assumed.
- Settings restored to the pre-round backup via the pushed-script pattern at close-out; `shared_prefs`
  ownership re-applied in the same script. Softap stopped (`cmd wifi stop-softap`) at close-out.

## R0 - build and unit gate

**PASS**

- `./gradlew :app:assembleGithubDebug`, clean.
- `./gradlew :app:testGithubDebugUnitTest`, clean.
- Suite counts: `ConnectionIssueBannerPolicyTest`=27, `SoftApCredentialsPolicyTest`=15,
  `SoftApBssidPolicyTest`=13, `NativeGroupBandPolicyTest`=14, `P2pOperatingChannelPolicyTest`=14,
  `ConnectionIssuesTest`=8. **Total: 698**, matching the brief's prediction exactly, against round
  2's 676.

## R1 - the name alone is still not the remedy, and now it says so twice

**PASS**, all four conditions.

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=1`, `hotspot-ssid=OHU-TEST`,
  `log-level=1`; `hotspot-password` and all four `connection-issue-*` keys deleted.
- Radio state: `cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5`, confirmed via
  `ip -4 addr show wlan2` giving `192.168.231.139/24`, 5745 MHz.
- Discard-rule check: clean (`createGroup SUCCESS`=0, `MATCH! Starting AapService`=0, expected
  since this transport doesn't form a P2P group).
- Decisive log line: `SoftApCredentials: these credentials carry no passphrase, so the phone will
  refuse them and the hotspot-configuration record stays up.` (count 1, at `23:13:51.581`).
- `connection-issue-hotspot-config` read back **non-zero** (`1787372031581`) after the 60s dwell
  (round 2 read `0` here on the same state, the defect this fix addresses).
- Relaunch: exactly one `MainActivity: showing the connection issue banner for
  HOTSPOT_CONFIG_UNREADABLE`, screenshot confirms banner text on screen.
- `named by this device rather than by the manual override` and `these credentials come from the
  manual override` both **0**.

## R2 - the pair is the remedy, and the record survives it. The headline result.

**PASS**, all three conditions.

- Settings written: `hotspot-password=testtest1234` (added to R1's `hotspot-ssid=OHU-TEST`),
  `connection-issue-hotspot-config` seeded to `1755800000000`.
- No `showing the connection issue banner` line fired at all after relaunch.
- **`connection-issue-hotspot-config` read back as `1755800000000`, unchanged from the seed.**
  Round 2 read `0` here on the identical state; this is the one number the round exists to
  measure, and it now holds.
- `SoftApCredentials: these credentials come from the manual override, which is a way round this
  device not naming its own access point rather than proof that it can, so the hotspot-configuration
  record stays as it is.` (count 1). `carry no passphrase` (count 0).

## R3 - a dismissal does not outlive the next attempt. Never tested before this round.

**PASS**, both conditions.

- Step 1: force-stop, `hotspot-ssid`/`hotspot-password` and all four `connection-issue-*` keys
  deleted, softap left up. Condition raised by the hardware itself
  (`SoftApCredentials: The access point on wlan2 is up, but this device will not let apps read its
  name...`) at `23:16:32.440`. Relaunch showed the banner; `connection-issue-hotspot-config` read
  `1787372192456` (later refreshed to `1787372269332` by the relaunch's own re-check before the
  dismiss tap).
- Step 2: **tapped the Dismiss (X) button**, bounds located via `uiautomator dump`
  (`connection_issue_dismiss`, `[1347,99][1395,147]`), tapped at its center (1371,123), confirmed
  via screenshot that the banner disappeared and Settings did **not** open (proving the tap hit
  Dismiss, not the body). `connection-issue-dismissed-at` read back `1787372294474`, later than
  `connection-issue-hotspot-config`'s `1787372269332`.
- Step 3: `hotspot-ssid=OHU-TEST` only (password stays deleted), relaunched, 60s dwell, force-stop.
  `connection-issue-hotspot-config` read back `1787372330709`, **greater than**
  `connection-issue-dismissed-at` (`1787372294474`), confirming the re-raise.
- Final relaunch: **the banner is back**, second `showing the connection issue banner for
  HOTSPOT_CONFIG_UNREADABLE` line at `23:19:09.664`, confirmed via screenshot. Before this change
  this path wiped the stamp to 0 at step 3 and the banner never returned; that did not happen
  here.

## R4 - a static BSSID keeps the record it works around

**PASS**, all three conditions. First hardware run of the BSSID half of this fix.

- Settings written: `native-ap-transport=0`, `static-bssid=AA:BB:CC:DD:EE:FF`,
  `connection-issue-bssid=1755800000000`, hotspot overrides deleted.
- Real Native AA attempt via phone-Bluetooth toggle (off before HU launch, on after listeners
  opened) reached Type 3 cleanly: `Connection accepted from POCO X3 NFC` then `[TX] Wrote TYPE 3`
  in under 2s, `createGroup SUCCESS`=1 (no churn).
- `NativeAA: the BSSID being sent is the static override from Settings, which is a way round this
  unit not reading its own WiFi address rather than proof that it can, so the missing-BSSID record
  stays as it is.` (count 1).
- `connection-issue-bssid` read back **unchanged** at `1755800000000` after force-stop.
- `this unit read its own WiFi address` (count 0).
- As expected, the phone did not complete a full session on the bogus static BSSID; not the
  measurement per the brief.

## R5 - and a real address still retires it. R4's control. The regression guard.

**PASS**, all three conditions.

- Settings written: `static-bssid` deleted, `connection-issue-bssid` re-seeded to
  `1755800000000`, `native-ap-transport=0` unchanged.
- Real Native AA session via the same phone-Bluetooth toggle: `NativeAA: this unit read its own
  WiFi address, so the missing-BSSID record is retired.` at `23:22:02.092`,
  `Handshake: SSL handshake complete` at `23:22:13.795`.
- `connection-issue-bssid` read back **`0`** after force-stop, the retirement held.
- Discard-rule check: `createGroup SUCCESS`=1 for this run's own group; one `p2p-wlan0-N` index
  bump (`p2p-wlan0-0` to `p2p-wlan0-1`) confirmed to be R4's stale group being torn down *before*
  this run's `createGroup SUCCESS` fired (the benign pattern per `TESTING-TEMPLATE.md` §7a, not a
  discard). One `MATCH! Starting AapService` from the deliberate phone-BT toggle, zero group churn
  attached to it.

## R6 - round-wide invariant: nothing raises by itself

**PASS**, all zero, across `r2.txt`, `r4.txt`, `r5.txt` (R1's and R3's captures excluded per the
brief, since the condition raising is their own point).

- `the phone connected over Bluetooth and answered nothing we sent`: 0/0/0
- `MainActivity: could not read the connection issue record`: 0/0/0
- `ConnectionIssues: settings unavailable, not recording`: 0/0/0

## R7 - the wireless screen says what it should, where it should

All five parts **PASS**.

- **R7a**: `wifi-connection-mode=3`, `native-ap-transport=1`. Found via in-app search ("Hotspot").
  Note reads exactly `The phone joins this device's own hotspot instead of a WiFi Direct group.
  Switch it on before connecting.`, a verbatim match. "Experimental" does not appear anywhere on
  screen. Fits without pushing "Auto-Enable Hotspot" off screen (both visible together).
- **R7b**: toggled Auto-Enable Hotspot on. The "Experimental Feature" dialog still appears
  ("This feature uses reflection and might not work on all devices..."), confirmed by screenshot,
  the deliberate, separate feature, not a leftover. Cancelled and reverted the toggle;
  `auto-enable-hotspot` confirmed still `false` in the live prefs afterward, no stray persistence.
- **R7c**: `native-ap-transport=0`. Search "WiFi Direct band" surfaces a three-way **Auto / 5 GHz
  only / 2.4 GHz only** selector in **Basic**, with its explanatory note. Old toggles ("Force WiFi
  Direct onto 2.4 GHz", "Ask for 5 GHz on Android 9 and older") not found anywhere in search.
  Picking **2.4 GHz only** made the coupled "Use the upper 5 GHz range" toggle disappear (confirmed
  via a separate "upper" search returning no match, only a stale unrelated "Use AAC Audio" hit on
  one incremental-search attempt, corrected with the full search string); picking **Auto** brought
  it back (confirmed present again, toggle off, description intact). Back-navigated without saving;
  confirmed `wifi-direct-band` and `native-ap-transport` unchanged in the persisted file afterward.
- **R7d**: Advanced tab, search "Static BSSID" for each of the four states:

  | Setting | Static BSSID row |
  |---|---|
  | `wifi-connection-mode=1` | **absent** (confirmed) |
  | `wifi-connection-mode=2`, `helper-connection-strategy=2` | **absent** (confirmed) |
  | `wifi-connection-mode=2`, `helper-connection-strategy=1` | **present**, labelled "Static BSSID (MAC Address)" |
  | `wifi-connection-mode=3`, `native-ap-transport=0` | **present**, same label |
  | `wifi-connection-mode=3`, `native-ap-transport=1` | **present**, same label |

- **R7e**: `wifi-connection-mode=3`, `native-ap-transport=0`, `connection-issue-bssid` seeded,
  `static-bssid` deleted (and, per the Setup notes finding above, `connection-issue-dismissed-at`
  cleared). Phone Bluetooth held off for this run specifically to prevent a live session from
  stealing foreground before the tap (see Setup notes). Launched, waited, banner confirmed on
  screen ("This unit could not read its own WiFi MAC address (BSSID)..."), tapped the body. Settings
  opened with the search box pre-filled to exactly **"Static BSSID (MAC Address)"** and the row on
  screen, confirmed by screenshot and `uiautomator dump`.

## R8 - the band selector reaches the group

All three parts **PASS**.

- **R8a (Auto)**: `wifi-direct-band=0`, old keys already absent. `WifiDirectManager: Band preference
  is automatic (5 GHz, then whatever this unit will host).` then `Requesting Native AA P2P group on
  5GHz band.` then `createGroup SUCCESS`=1 then a real session completed
  (`Handshake: SSL handshake complete`). Matches round 2's R5 behaviour.
- **R8b (2.4 GHz only)**: `wifi-direct-band=2`. `Band preference is 2.4 GHz only, set by the user.`
  then `Requesting Native AA P2P group on 2.4GHz band. Chosen by the user.` then a group formed at
  **2412 MHz** (`onGroupInfoAvailable`, channel 1), well under the 3000 MHz threshold.
  `band-mismatch`=0, `Retrying 5GHz`=0, `createGroup SUCCESS`=1 for this group. Observation only,
  not a PASS condition: re-enabling the phone's Bluetooth did produce a full session on this 2.4
  GHz group (`Handshake: SSL handshake complete`), including through a second, phone-BT-toggle-
  induced group re-init that stayed on 2.4 GHz both times.
- **R8c (migration)**: deleted `wifi-direct-band`, wrote `debug-force-p2p-band-24=true`, launched,
  force-stopped. `wifi-direct-band` read back as **`2`**, written by the app itself; the old flag
  was not silently lost.

## Anything the brief did not ask about

- The false-negative in R7e's first attempt (leftover `connection-issue-dismissed-at` predating the
  brief's fixed seed constants) is worth a note for future briefs in this thread: any run that
  seeds a `connection-issue-*` timestamp from a constant should also clear
  `connection-issue-dismissed-at`, since `bannerFor()` compares them directly and a leftover
  real-clock value from an earlier run in the same session will always be newer than the constant.
- `updateConnectionIssueBanner()` firing only on `onResume()` (not periodically, not on any
  broadcast) is worth documenting in the standing template for this thread specifically, since two
  separate runs in this round (R1, R3 step 1) needed an explicit relaunch to observe a
  condition that had raised mid-session.
