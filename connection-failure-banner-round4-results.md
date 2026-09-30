# connection-failure-banner — round 4 results

**Candidate:** `fix/session-lifecycle-and-diagnostics` @ `7db4e0c65082430ebe4c13d14f7881e8f40718ee` on `fork`
**Baseline:** none (no A/B this round)
**APK md5:** `b94791f786f0169e9bf57bcb1d0aeac4` (one APK for the whole round, confirmed live via `pm path` + `md5sum` before R1 and again after R4)
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, 800x480-class panel (screenshots captured at the panel's native 1440x810 framebuffer)
**Date:** 2026-08-22

## Setup notes

- SHA confirmed exact match to the brief (`git rev-parse HEAD` printed `7db4e0c6...`) after `checkout -B` per §0's rewritten-history warning. `git fetch fork --tags` hit one harmless pre-existing tag clash (`v.3.2.0-beta3`, "would clobber existing tag") unrelated to this branch; ignored.
- Verified all five of the brief's §4 grep strings and the two §5 hint strings against the source tree before running anything (`grep -rF`), all matched verbatim. `headunit_setup_needed` is genuinely absent from source (the channel constant itself was removed, not just its use).
- `hur-wifi-test-scripts/build_hur.sh` and `run_unit_tests.sh` used for R0 as-is, no changes needed. `install_and_launch.sh SKIP_BUILD=1` for install. `set_hu_prefs.sh` (existing multi-key script) used for every settings write this round; nothing new needed.
- Phone: POCO X3 NFC (`4f4027e9`), bonded throughout.
- **R1's condition needs the system hotspot actually brought up** (`cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5`), not just the app's stored ssid/password deleted — the brief's §7a cross-reference to "confirm it immediately before launching" only makes sense once the AP is actually up. Confirmed via `dumpsys wifi | grep SoftApInfo` before every launch.
- **R2's "Location services off" is the head unit's own location, not the phone's.** Traced to source: `WifiDirectManager.kt:443-445` and `NativeCredentialsPreflight.kt:226-228` both call `context.getSystemService(LOCATION_SERVICE)` on the app's own `Context`, which runs on the head unit. Toggled the phone's location off by mistake first, caught it before capturing, reverted the phone and toggled the head unit's instead. Worth fixing in the next brief that touches this — "the address unreadable" reads ambiguously between the two devices.
- **First R2 attempt discarded per the standard rule**: `createGroup SUCCESS`=2, `p2p-wlan0-0`→`p2p-wlan0-1`, one `MATCH! Starting AapService` with group churn attached (the poke's own `socket.connect()` briefly ACL-connecting and self-waking `AutoStartReceiver`, exactly the loop `CLAUDE.md` documents). Re-ran using the phone's Bluetooth as the settle lever (§7a's "one poke round, then a normal session" pattern): phone BT off before launch, wait ~18s for the group+listeners to settle with location off, phone BT back on, wait for the phone's own reconnect. Redo capture was clean: `createGroup SUCCESS`=1, the one `MATCH!` had zero group churn attached (the phone's own reconnect, the documented non-contamination exception).
- **The Basic-tab band selectors don't surface their own hint under a search for the row's own title** — `filterSettings()`/`searchableText()` matches each item independently (no "show the adjacent item too" grouping beyond category headers), so searching "Hotspot band" or "WiFi Direct band" shows only the selector row, not the `InfoBanner` hint below it, because the hint's own searchable text (the hint sentence itself) doesn't contain the row's title. To read a hint verbatim, search a substring **of the hint text itself** ("reliable up to 720p" / "asks for a 5 GHz channel first"), not the row's label — this got both R3 screenshots on-screen in full with no scrolling. Worth a note in `TESTING-TEMPLATE.md` for whoever writes the next brief touching these hints.
- **The in-app search field does not clear itself between activity relaunches if the same `SettingsActivity` instance is reused.** `am start` on an already-foregrounded `SettingsActivity` logs "Warning: Activity not started, intent has been delivered to currently running top-most instance" and a second `input text` call appends to the existing query rather than replacing it. Fixed by explicitly clearing the field (`input keyevent KEYCODE_DEL` x40) before typing, or by `force-stop`-ing between searches.
- R4's Advanced-tab ordering check needed a bounded scroll (not a search) since it's an adjacency/ordering question across ~7 rows spanning two categories at the top of the settings list — this is the documented exception in `TESTING-TEMPLATE.md` §7a ("swipe, `uiautomator dump`, grep for the label, repeat up to a stated maximum"), not the banned "blind swipe and assume" pattern: every intermediate screen was screenshotted and read in sequence.
- Settings restored to the pre-round backup via the pushed-script pattern (`adb push` + on-device `cp`, `chown`/`chmod` re-applied) and verified with a byte-for-byte `diff` against the backup — identical. SoftAP stopped, head-unit and phone location both re-enabled, phone Bluetooth confirmed still on, at close-out. No stray `logcat` processes found running (`ps aux | grep logcat` empty) before or after — each capture's own PID was killed explicitly right after its run.

## R0 — build and unit gate

**PASS**

- Clean `assembleGithubDebug`, no errors.
- `testGithubDebugUnitTest`: **698/698**, `BUILD SUCCESSFUL`. Exact match to the brief's expected count, unchanged from round 3.

## R1 — the hotspot condition raises with no notification and no toast

**PASS**, all five conditions.

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=1`, `log-level=1`, `show-toast-messages=true`; `hotspot-ssid`, `hotspot-password`, and all four `connection-issue-*` keys deleted.
- Radio state: head unit's own hotspot brought up via `cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5`, confirmed via `dumpsys wifi | grep SoftApInfo` immediately before launch (`wlan2`, 5805 MHz, `SAP is enabled successfully`).
- Discard-rule check: clean. `MATCH! Starting AapService`=0, `createGroup SUCCESS`=0 (expected — hotspot transport doesn't form a P2P group), no `p2p-wlan0-N` lines.
- Decisive log lines:
  - `08-22 00:07:xx SoftApCredentials: The access point on ... is up, but this ...` — 2 hits (`grep -ac`).
  - `08-22 00:08:42.687 I/OPENHU MainActivity: showing the connection issue banner for HOTSPOT_CONFIG_UNREADABLE` — exactly 1, after the force-stop/relaunch.
  - `CredentialsNotice` — 0 hits, confirming the deleted class never appears (right APK).
  - `these credentials carry no passphrase` — 0 hits, correctly absent since this scenario is "no name on file," not "empty passphrase" (that's round 3's R1 shape, not this one).
- Notification check: `dumpsys notification --noredact | grep -i headunitrevived` showed exactly one `NotificationRecord` for the whole 60s window, `channel=headunit_service_v2` (the ongoing foreground-service notification, id=1) — nothing on `headunit_setup_needed` or any other channel. Cross-checked visually: `cmd statusbar expand-notifications` screenshot at the 60s mark shows only "Open Headunit · Open Headunit is running" under Silent, alongside Android System's own unrelated USB-debugging notification.
- Toast check: screenshot at the 15s mark shows a clean home screen, no toast overlay.
- `connection-issue-hotspot-config` read back **`1787375323408`** (non-zero) after the run.
- Screenshots: shade at 60s (`r1_shade.png`), banner after relaunch (`r1_relaunch.png`), clean screen at 15s (`r1_15s_check.png`).

## R2 — the BSSID condition raises with no notification

**INCONCLUSIVE**, per the brief's own pre-registered fallback ("the address reads fine anyway").

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=0`, `log-level=1`; `static-bssid` and all four `connection-issue-*` keys deleted.
- Radio state: **head unit's own location services** disabled (`cmd location set-location-enabled false`, verified via `dumpsys location | grep "Location Setting"` → `false`) — this is the device that reads its own BSSID (`WifiDirectManager.kt:443-445`, `NativeCredentialsPreflight.kt:226-228`), not the phone's, despite the brief's ambiguous wording. Phone location left on throughout the counted (redo) run.
- Discard-rule check: **first attempt discarded** (`createGroup SUCCESS`=2, interface index bumped `p2p-wlan0-0`→`p2p-wlan0-1`, one `MATCH!` with group churn attached — the poke's own self-wake loop). **Redo clean**: `createGroup SUCCESS`=1, the one `MATCH!` (the phone's own Bluetooth reconnect, arranged deliberately by toggling its adapter as the settle lever) had zero group churn attached, which `TESTING-TEMPLATE.md` §7a's discard rule explicitly treats as non-contamination.
- Decisive log lines (redo capture):
  - `WifiDirectManager: WARNING - Location Services are DISABLED. BSSID will likely be masked (00:00...)!` and `BSSID is masked. Starting fallbacks...` — both fired, confirming the head-unit-side location toggle was read and acted on.
  - `NativeAA: Connection accepted from POCO X3 NFC ... ` → `[TX] Sending WifiStartRequest (Type 1)` → `[RX] Received Type 2` → `[TX] Wrote TYPE 3` → `WirelessServer: Incoming connection detected` → `AapSslContext.performHandshake | SSL handshake complete` — the full, ordinary handshake sequence, twice (once in the discarded attempt, once in the clean redo).
  - `NativeAA: BSSID is still masked/empty` — **0 hits in either attempt.** The masking warning fired and the fallback chain ran, but resolved a real, non-masked address (`c6:5f:f1:5f:9c:82` in the first attempt) before the Type 3 check, so the abort this run is built to exercise never triggered.
- `connection-issue-bssid` never got a non-zero write (condition never raised); not applicable to report as a number.
- The redo's live session was torn down explicitly (`headunit://disconnect`) rather than left running, and head-unit location restored to on immediately after.
- Matches this repo's own `CLAUDE.md` note on the six-deep BSSID fallback chain (NetworkInterface → last known → local device → group owner → sysfs/`ip link` → `Settings.Secure` → reflection): on this rig, at least one fallback below the location-gated API resolves a usable address regardless of the location toggle, so `BSSID_UNAVAILABLE` cannot be raised here by this lever. R1 already carries the removal's regression coverage on its own, per the brief.

## R3 — the two hint texts, on screen

**PASS**, both parts, both verbatim.

- **R3a (hotspot band, `native-ap-transport=1`):** in-app search for a substring of the hint itself ("reliable up to 720p") surfaced it fully on screen, one line wrapped to two, nothing pushed off screen:

  > Auto asks for 5 GHz and falls back to 2.4 GHz. 2.4 GHz is reliable up to 720p, so pick 5 GHz only above that.

  Exact match to the brief. Screenshot: `r3a_hint_search2.png`.

- **R3b (WiFi Direct band, `native-ap-transport=0`):** same method, searched "asks for a 5 GHz channel first":

  > Auto asks for 5 GHz and falls back to 2.4 GHz, and on Android 9 and older asks for a 5 GHz channel first. 2.4 GHz is reliable up to 720p, so pick 5 GHz only above that.

  Exact match to the brief. Screenshot: `r3b_wifidirect_band_hint.png`.

## R4 — the WiFi Direct band selector is the first row under the transport selector

**PASS**, all parts.

- Advanced tab, `wifi-connection-mode=3` (Native), `native-ap-transport=0` (WiFi Direct). Bounded scroll from the top of the Advanced list (§7a's documented exception for order/adjacency checks), screenshotting every intermediate screen:

  **Wireless Mode** (Wireless Helper / **Native** / Headunit Server — the brief's "Android Auto mode") → **Network transport** (**WiFi Direct** / Hotspot) → **WiFi Direct band** (**Auto** / 5 GHz only / 2.4 GHz only) → its note (verbatim match to R3b's text) → **Use the upper 5 GHz range** → **Bluetooth Adapter** → **Manual Secondary Radio** → **Modern handshake (version exchange)**.

  Every adjacency in that chain was confirmed directly (no gap screenshotted-over): transport selector sits immediately above WiFi Direct band with nothing between (`r4g_transport_to_band.png`), and the three items the brief says "used to come before" (Bluetooth adapter, secondary Bluetooth service = "Manual Secondary Radio", the version-exchange toggle = "Modern handshake (version exchange)") all appear strictly after "Use the upper 5 GHz range" (`r4h_after_upper5ghz.png`, `r4i_version_exchange.png`). Screenshot showing transport selector and band row together: `r4g_transport_to_band.png`.

- Basic tab: **WiFi Direct band** confirmed present with a search on its own label (`r4j_basic_tab_present.png`), and its note confirmed present with a search on the note's own text (`r4k_basic_band_and_note.png`).
- Set band to **2.4 GHz only**: `Use the upper 5 GHz range` search returned nothing but the row's absence (`r4n_upper5_absent.png`) — matches round 3's R7c.
- Set back to **Auto**: `Use the upper 5 GHz range` returned, description text intact, Save button reverted to its inactive/no-pending-change color (`r4p_upper5_returns.png`).
- Backed out via the top-left back arrow, **not Save**. `wifi-direct-band` read back **`0`** afterward — unchanged from the value on disk before any of this run's taps.

## Anything the brief did not ask about

- The search-field append bug (Setup notes, above) is a testing-methodology footgun, not an app defect — the app's own search field is a normal `EditText` that behaves exactly as expected when the activity is actually freshly launched (confirmed: `force-stop` + relaunch always started with an empty field).
- `SoftApCredentialsProvider`'s masked-BSSID fallback chain is evidently robust enough on this specific rig/session that the head-unit-side location toggle alone can't produce `BSSID_UNAVAILABLE` — consistent with, and a mild strengthening of, the six-deep fallback chain this repo's own `CLAUDE.md` already documents. Nothing here suggests the removal (this round's actual subject) regressed; R1 already carries that proof on its own, as the brief anticipated.

## Report back (the brief's four numbers)

1. **R0's test count:** 698, exact match.
2. **R1's notification count:** zero from this app beyond the one ongoing foreground-service notification, confirmed two ways over the full 60s window.
3. **The banner still works:** yes, both R1 and R2 — R1 showed the banner cleanly after the removal; R2's premise didn't raise on this rig (INCONCLUSIVE), so it couldn't test the banner's response, but nothing in either R2 attempt suggests a regression in the banner path itself.
4. **The two hint texts:** both verbatim matches.
