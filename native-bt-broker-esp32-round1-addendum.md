# native-bt-broker-esp32, round 1 addendum: v1.0.0 control pairing

Follows `native-bt-broker-esp32-round1-results.md` (commit `354da5114`, not edited). Asked by the operator after the round: does the board pair on `v1.0.0` in the same state in which it failed to pair twice on `apply_hfp`?

**Candidate:** PR head `792989746b8f71b03562e44e067ee4dd47594c4f`, `C.apk` md5 5f5f8e4c24b08734dda392cb10cf1b1d (reinstalled on D-HU, pulled back and md5-equal, `commit` `792989746b8f`, install changed no setting, the three Bluetooth and location permissions read `granted=true`).
**Firmware:** `v1.0.0` release bins flashed back onto E-1 (4 SHA256 matched, `Hash of data verified` x4, MAC 78:1c:3c:2b:ac:ec read first, no self-check failure, no panic). Same board, same NVS, same D-HU LE bond as the failed `apply_hfp` attempts; D-POCO had no bond to the board.
**Date:** 2026-10-26. Evidence: same release, asset `native-bt-broker-esp32-round1-addendum-captures.zip` (1621228 bytes, sha256 29927e4943120f57a8a7c553d4c49a5449519ee139ec55f09c09e12e25784ff4).

## Setup notes

- Out of brief. Nothing in the pushed results was rewritten. Round scripts as in the results file (`flash.sh` patched with `3>&-` so the serial capture holds no rig lock; `pair.sh` unchanged). The first flash's `flash.txt` and `e1-mac.txt` are kept as `flash-1st.txt` and `e1-mac-1st.txt`.
- D-HU had its prior APK installed after the round; it was switched to the candidate for the control (the broker screen exists only there) and switched back afterwards.
- Injected taps (operator-approved): attempt 1 `p-scan` 09:52:27.234 and `p-pick` 09:52:30.125; attempt 2 `p-scan` 09:55:19.838, `p-pick` 09:55:22.412, `p-pair` 09:55:35.006. H4 cue 09:55:35.145.

## Control pairing

**PASS** (the board paired on `v1.0.0` in the state where it timed out on `apply_hfp`)

Attempt 1 (09:52:24) was not comparable: the board still held a classic bond to D-POCO from the first pairing (`removed bond DC:B7:2E:5E:4E:59` at 09:52:32.010), and `v1.0.0` ended pairing mode 0.25 s after opening it (`classic discoverable=0` and `pairing mode ended` at 09:52:32.259). `paired=1`. That attempt removed the stale bond.

Attempt 2 (09:55:19), the control:

| Item | `v1.0.0` control | `apply_hfp` attempt 2 (09:37:14) |
|---|---|---|
| Board before the window | `no classic bonds to clear` (09:55:35.520) | `no classic bonds to clear` (09:37:13.998) |
| Pairing window | `pairing mode for 120 s` 09:55:35.816 | `pairing mode for 120 s` 09:37:14.125 |
| Board panic | none | none (attempt 1 at 09:33:29.573 had one) |
| Phone | `BOND_NONE => BOND_BONDING` 09:55:49.446, `BOND_BONDING => BOND_BONDED` 09:55:55.718 (6.3 s) | `BONDING` 09:38:22.066 and 09:38:48.386, both `Bonding failed ... HCI_ERR_CONNECTION_TOUT` (09:38:43.628, 09:39:08.616) |
| Board | `phone paired: POCO X3 NFC` 09:55:56.470, `remembered phone DC:B7:2E:5E:4E:59` | no `phone paired` line |
| Result | `paired=0`, broker saved `78:1C:3C:2B:AC:EE`, D-POCO bonded list names `OpenHU-NativeBTBroker` | `paired=1`, no bond |

The `apply_hfp` serial also prints `HFP armed for pairing (inbound only)` in the window; the `v1.0.0` firmware does not.

**What this does and does not show.** With the board, NVS, D-HU bond, phone and cabling unchanged, `v1.0.0` bonded in 6.3 s and `apply_hfp` timed out twice, so the page timeouts follow the firmware image and not the board or the rig. It is one control run against two failed runs, and the two images differ in more than the PR's code: `apply_hfp` was built here with unpinned PlatformIO (espressif32 8.0.0, framework-arduinoespressif32 4.30312.261024) and a different bootloader, while the `v1.0.0` bins are the release build. This does not separate the PR's own change from the newer toolchain. The mechanism is not established.

## Closing

D-HU: `headunit://exit`, force-stop, broker file deleted, prior APK reinstalled (md5 edac10621db7e3a3dcd37f4b256207db), `settings.xml` restored as root, diff against the round backup empty. Serial capture stopped by pid; `rig_cleanup.sh list`: no strays, lock free. **Board E-1 is left on `v1.0.0`, paired to D-POCO** (it ran `apply_hfp` at the end of the round and a blink sketch before the round). D-POCO is still bonded to `OpenHU-NativeBTBroker` and D-HU to `OpenHU-NativeBTBroker-Setter`: both are to be forgotten by hand. D-SAM is unchanged from the round (candidate APK, settings restored).
