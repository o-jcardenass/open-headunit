# wifi-direct-credentials — round 1 results

**Candidate:** `testing/native-aa-wireless-and-auto-start-loading` @ `dfecb05dd`, `fork/fix/native-aa-wireless` @ `0c9c26e2` merged in on top of parent `e013868a8` (the brief's named candidate), merge clean, no conflicts
**Baseline:** none, every run is candidate only
**APK md5:** `2f791549d0a6b6b217816899442ce5b0` (verified byte-for-byte against the copy pulled back from D-HU after install)
**Unit:** D-HU (UNISOC MT50, Android 14) for R1, R2, R4; phone D-POCO (POCO X3 NFC) for R1, R2. D-SAM (Samsung SM-T230, Android 4.4.2 / API 19) for R3.
**Date:** 2026-09-22
**Evidence:** `rig-evidence-wifi-direct-credentials` (new release on `fork`, created this round), asset `wifi-direct-credentials-round1-captures.zip`, sha256 `573e92b65bf33e790718e65fb1fd3fb768d7b5efa3fc3172911b09b59142e3a2` — R1/R2 (D-HU) and R3 (D-SAM) logcat, plus R4's `export.json`.

## Setup notes

- **The brief named a bare checkout of `0c9c26e2`; this round instead merged `fork/fix/native-aa-wireless`
  into `testing/native-aa-wireless-and-auto-start-loading`** (merge commit `dfecb05dd`), per this
  round's own instruction to test on that branch rather than the brief's candidate directly. The merge
  was clean (auto-merged strings + the four touched Kotlin files, no conflicts) and the testing branch's
  other content (the external-BT-daemon and auto-start-loading work already on it) is unrelated to this
  brief's runs.
- **Build gate:** `build_hur.sh` was denied once by this session's own sandbox/auto-mode classifier
  ("Modify Shared Resources"); the identical invocation succeeded on a bare retry seconds later. Matches
  the same-class denial noted in `native-aa-wireless-round4-results.md` Setup notes — flagging again for
  whoever tunes that classifier. `run_unit_tests.sh`: **2303 tests, 0 failures/errors/skipped** (the
  brief's stated 2291 plus this merge's own 12 test additions/changes: 10 new in
  `P2pIdentityEditPolicyTest`, 2 changed in `AutomationOutputPolicyTest`).
- **§3 shared_prefs ownership check, both units correctly app-owned, no root-owned blocker:** D-HU
  `drwxrwx--x u0_a176 u0_a176` (`stat` available). D-SAM has no `stat` in its toolbox (known quirk); `ls
  -la` on the parent dir read `drwxrwx--x u0_a154 u0_a154` for `shared_prefs`.
- **D-SAM's pre-round `wifi-direct-group-name-changes` was already 47**, confirming the brief's §3
  warning that the counter survives unreported rounds. Left untouched for R3 (the brief's R3 does not
  ask to clear it, and R3 grades the record only).
- **D-SAM has no `sed`/`busybox`/`awk`** (known quirk, `which` is also absent from its toolbox). Edited
  a locally-pulled copy of `shared_prefs/settings.xml` on the host and pushed the whole file back via a
  `run-as`-run one-line script, per the established pattern for this unit.
- **Own error, caught and corrected:** the local copy of D-SAM's pre-round `settings.xml` was edited in
  place (`log-level` 0→2) and used directly as R3's config, so it was no longer a true backup. The first
  restore attempt therefore re-applied R3's `log-level=2` instead of the original `0`. Caught by
  re-deriving the true original from the file's first-read content (only the one field had been
  touched) and pushing that; the restored file was diffed against the reconstruction and is
  byte-identical (a CRLF artifact from `adb shell cat` was the only surface difference, normalized
  before comparing).
- **D-HU's Bluetooth radio was toggled off/on during R1**, after two automatic pokes (to D-MOTO and then
  D-POCO) failed with `read failed, socket might closed or timeout, read ret: -1` — the documented
  rig lever for forcing a live HFP link (`project_mt50_hfp_link_needs_hu_bt_toggle`). The next automatic
  poke retry succeeded shortly after, but the toggle's own causal contribution to that success is not
  established (no control run against a retry with no toggle) and is not claimed as fact here.
- **D-HU's OS-level saved P2P groups were cleared before R2** per §3: eleven persistent groups
  (`networkId` 10-20, names `DIRECT-G3-...` through `DIRECT-ZZ-RigTest`) were present, all
  `cmd wifip2p delete-saved-group`d, confirmed empty by a follow-up `list-saved-groups`.
- Scripts used: `build_hur.sh`, `run_unit_tests.sh`, `install_all.sh`'s equivalent `adb install -r -d`
  (installed individually per device rather than via the all-devices script, since only two of the four
  connected devices were in scope this round). No new script was added; `set_pref.sh`'s pattern was
  hand-adapted for the multi-key R1 write and the D-SAM host-edit-and-push path, neither of which fit an
  existing script closely enough to reuse directly.
- Both units' `settings.xml` were restored to their exact pre-round state at the end of the round
  (byte-verified for D-SAM as above; D-HU's backup was never mutated so its restore needed no
  re-verification beyond the readback already shown in R1/R2).

## R1 — a typed pair reaches the air and the phone joins it (D-HU, the point of the round)

**PASS**

- Settings written: `wifi-connection-mode=3` (int), `native-ap-transport=0` (int),
  `wifi-direct-stable-identity=true` (boolean), `log-level=2` (int),
  `wifi-direct-group-name=DIRECT-ZZ-RigTest` (string), `wifi-direct-group-passphrase=RigPass12345`
  (string), `wifi-direct-identity-user-set=true` (boolean). Deleted first:
  `wifi-direct-readback-name`, `wifi-direct-readback-passphrase`, `wifi-direct-readback-bssid`,
  `wifi-direct-group-name-changes` (all absent before launch, confirmed by grep count 0).
- Radio state: D-HU Bluetooth toggled off/on mid-run (see Setup notes); WiFi Direct created fresh
  (`Attempt 0`, no prior live group to collide with, see R2 for the contrasting case).
- Discard-rule check: single clean run, not re-run.
- Decisive log lines, quoted with timestamps:
  - `01:14:50.680 WifiDirectManager: group identity: asking for DIRECT-ZZ-RigTest, the network name and password the user typed, so the phone is handed exactly those.`
  - `01:14:51.508 WifiDirectManager: 5GHz createGroup SUCCESS!`
  - `01:14:51.813 WifiDirectManager: group identity ssid=DIRECT-ZZ-RigTest persistent=yes (netId 20) asked=persistent matchesRequest=yes bssid=C2:12:DA:F0:A0:3A address=generated stable=unproven ... source=IPv6 link-local`
  - `01:14:51.831 WifiDirectManager: SUCCESS - Providing credentials to listener. SSID=DIRECT-ZZ-RigTest, IP=192.168.49.1, BSSID=C2:12:DA:F0:A0:3A, identity stable=unproven`
  - `01:15:36.908 NativeAA: Successfully poked POCO X3 NFC via HSP-AG. Holding 20000ms...`
  - `01:15:37.649 NativeAA: Connection accepted from POCO X3 NFC (DC:B7:2E:5E:4E:59) on local radio [Navegadortz2]`
  - `01:15:45.534 WirelessServer: Incoming connection detected from /192.168.49.164`
  - `01:15:45.807 AapSslContext.performHandshake | SSL handshake complete. Session id: QSEImw3uVpQ3QpcYPnOHy2Z2MzdgFJJSj2ap0Vo4q38=`
  - Session went on to render video normally (42fps steady, 0 dropped) before the app was stopped for
    the readback check.
- Measurements: the group SSID actually came up as **`DIRECT-ZZ-RigTest`**, the exact typed name, not a
  random `DIRECT-xx-...`. `matchesRequest=yes`.
- With the app stopped, `settings.xml` read: `wifi-direct-readback-name=DIRECT-ZZ-RigTest`,
  `wifi-direct-readback-passphrase=RigPass12345`, `wifi-direct-readback-bssid=C2:12:DA:F0:A0:3A`.

## R2 — the pair is genuinely what the phone is given, not a label (D-HU)

**FAIL**

- Settings written: only `wifi-direct-group-passphrase` changed, `RigPass12345` → `RigPass99999`; the
  name key (`wifi-direct-group-name=DIRECT-ZZ-RigTest`) was left as R1 set it, per the brief. All eleven
  saved P2P groups were cleared first (Setup notes).
- Radio state: app was force-stopped between R1 and R2 but the **live** WiFi Direct group from R1 was
  never torn down at the OS level (`dumpsys wifip2p` history shows no `REMOVE_GROUP`/`CREATE_GROUP`
  pair around the R2 relaunch timestamp, and the group's `dumpsys` record shows `netId=20,
  startTime=01:14:51.661` — R1's start time — still `CURRENTLY OPEN` after R2). Clearing the *saved*
  profiles (§3) did not affect this *live* group, which is a separate OS-level object.
- Discard-rule check: single clean run, not re-run.
- Decisive log lines, quoted with timestamps:
  - `01:16:46.113 WifiDirectManager: startNativeAaQuietHost() requested. Removing old group if any...`
  - `01:16:46.123 WifiDirectManager: group identity: asking for DIRECT-ZZ-RigTest, the network name and password the user typed, so the phone is handed exactly those.`
  - `01:16:46.350 WifiDirectManager: a group named DIRECT-ZZ-RigTest is already up from before this bring-up; reading it instead of tearing it down.`
  - `01:16:46.387 WifiDirectManager: refresh: the group DIRECT-ZZ-RigTest is up, so its credentials are read again rather than the group remade.`
  - `01:16:46.459 WifiDirectManager: group identity ssid=DIRECT-ZZ-RigTest persistent=yes (netId 20) asked=nothing (not this app's create) bssid=C2:12:DA:F0:A0:3A address=generated stable=unproven (this group was already up and was read rather than created, so nothing new was measured) source=IPv6 link-local`
  - `01:16:51.036 WirelessServer: Incoming connection detected from /192.168.49.164`
  - `01:16:51.375 SSL handshake complete. Session id: Qe6l3UOq40X24VJbYA5M6SPC8qxOFw7cn/q4p9q0TR0=`
- Measurement: with the app stopped after this run, `wifi-direct-readback-passphrase` read
  **`RigPass12345`** — R1's old value, not the `RigPass99999` just written to `settings.xml` and asked
  for at `01:16:46.123`. This is the brief's stated FAIL condition exactly.
- **Mechanism, not just the symptom:** the phone did join, and the session did come up, but
  `matchesRequest` was never evaluated at all (`asked=nothing (not this app's create)` in place of
  `yes`/`no`) because `WifiDirectManager` treated the still-live R1 group as a survivor of the same
  identity and read it rather than recreating it — a policy this testing branch already carries
  (`181f8e169`, "WiFi Direct: do not grade a surviving group as one that came back"). That policy keys
  off the group *name* alone; since R2 changed only the passphrase and deliberately left the name
  unchanged (as the brief specifies, to isolate the passphrase), the survivor check matched and the
  typed passphrase never reached the air. The brief's own framing (this is "the one arrangement the
  redraw rule exists to prevent, produced deliberately by writing the key directly, which the UI would
  not let a user do") holds: going through the UI's own edit flow would presumably trigger the redraw
  logic against this exact case, but the redraw is evidently not otherwise invoked by a bring-up that
  finds a live, same-named group already up — only a raw settings write plus a relaunch reaches this
  path, which is exactly the tool §3 says to use.

## R3 — the record works below the naming API (D-SAM, API 19)

**PASS**

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=0` (both already at these values),
  `log-level=0→2`. Pair keys deliberately not written, per the brief.
- Radio state: fresh group create (`Attempt 0`, `Standard createGroup SUCCESS!`).
- Discard-rule check: single clean run, not re-run.
- Decisive log lines, quoted with timestamps:
  - `01:18:38.838 WifiDirectManager: Standard createGroup SUCCESS!`
  - `01:18:39.128 WifiDirectManager: onGroupInfoAvailable: SSID: DIRECT-Rh-Navegadortz3, BSSID: E6:58:E7:0E:DE:1E (source=IPv6 link-local), GO: true, IFACE: p2p-wlan0-0, Freq: 0 MHz (unknown)`
  - `01:18:39.138 WifiDirectManager: group identity ssid=DIRECT-Rh-Navegadortz3 persistent=no (temporary) asked=framework profile bssid=E6:58:E7:0E:DE:1E address=generated stable=no (the platform names it) nameChanges=48/3 ...`
- Measurement: with the app stopped, `settings.xml` read `wifi-direct-readback-name=DIRECT-Rh-Navegadortz3`,
  `wifi-direct-readback-passphrase=KP9GEFnW`, `wifi-direct-readback-bssid=E6:58:E7:0E:DE:1E` — name and
  BSSID match the `onGroupInfoAvailable` line exactly, in the same capture.
- No phone connection was attempted for this run (the brief grades the record only); D-POCO was not
  moved to D-SAM.

## R4 — the passphrase is withheld from an export (D-HU, desk check)

**PASS**

- Settings state: `allow-external-configuration=true` already set on D-HU.
- Broadcast: `am broadcast -a com.andrerinas.openheadunit.ACTION_GET_SETTINGS -p <pkg> --es path
  /sdcard/Download/export.json` replied
  `{"action":"...ACTION_GET_SETTINGS","file":"/sdcard/Download/export.json","withheld":11,"ok":true}` —
  non-zero `withheld` count.
- Measurement: pulled `export.json` (2674 bytes, 97 keys). Neither `wifi-direct-group-passphrase` nor
  `wifi-direct-readback-passphrase` appears as a key, and the substring `RigPass` (both passphrases
  written earlier this round) does not appear anywhere in the file.

## Anything the brief did not ask about

- **R4's export withholds more than the passphrase.** `wifi-direct-group-name`,
  `wifi-direct-readback-name`, `wifi-direct-readback-bssid` and `wifi-direct-group-name-changes` are
  also absent from `export.json` — not flagged as sensitive individually, but because the exportable
  settings set is a fixed, curated list of "portable" configuration that never included the per-session
  identity/read-back fields at all. Only `wifi-direct-band` and the `wifi-direct-identity-user-set`
  boolean marker are present from this feature's keys. This is stronger than the brief's PASS bar asks
  for, but it also means the export/import round-trip cannot carry over a typed group *name*, only the
  fact that one was typed — worth knowing if export/import is ever pitched as a way to hand a
  pre-configured identity to a second head unit.
- **R2's failure is plausibly a direct interaction with `181f8e169`**, already merged onto this testing
  branch before this round started (see gitStatus: it is the branch's 5th-most-recent commit). This
  round did not bisect to confirm that commit specifically causes the survivor-read behavior (it very
  likely does, by title and by the log line's own wording), only that the two features collide on
  hardware. Whoever picks up the R2 fix should start there rather than re-deriving the mechanism.
