# wifi-direct-credentials — round 2 results

**Candidate:** `testing/native-aa-wireless-and-auto-start-loading` @ `b096460bf` (merge `da0bac3f3` plus
one follow-up commit, see Setup notes), `fork/fix/native-aa-wireless` @ `29ef268e` merged in on top of
parent `e013868a8` (the brief's named candidate), 2306 tests
**Baseline:** round 1's own capture (per the brief, no fresh baseline build was made)
**APK md5:** `77ab84ef7f06fcc0d5d203d8d1602e2f`
**Unit:** D-HU (UNISOC MT50, Android 14), phone D-POCO (POCO X3 NFC). No D-SAM, per the brief.
**Date:** 2026-09-22
**Evidence:** `rig-evidence-wifi-direct-credentials` (existing thread release), asset
`wifi-direct-credentials-round2-captures.zip`, sha256
`79b3ae078691552bed764f4624b23d00a359eb89af1ecc7f604e1f6a1da43958`: setup run, R2r, C1 logcat.

## Setup notes

- **As round 1, this round merged `fork/fix/native-aa-wireless` into
  `testing/native-aa-wireless-and-auto-start-loading`** (merge commit `da0bac3f3`) rather than
  checking out `29ef268e` bare, per the brief's own accommodation for that workflow.
- **The merge conflicted, and not trivially.** `29ef268e` is a *rewritten* squash of round 1's
  `0c9c26e2` plus the fix, not a fast-forward of it, so git's 3-way merge diffed it against our
  branch's already-applied `0c9c26e2` content as if they were unrelated changes. 25 files conflicted
  (`WifiDirectManager.kt` and `P2pIdentityRotationPolicy.kt`, the two files carrying the actual fix,
  merged clean; the conflicts were all in `AutomationOutputPolicy.kt`, `SettingsFragment.kt`,
  `DialogUtils.kt`, `AutomationOutputPolicyTest.kt`, and 20 locale `strings.xml` files plus the base
  one, where the fork's rewrite had also reworded some of round 1's own dialog text). `git merge -X
  theirs` was denied by the session's own sandbox classifier ("Irreversible Local Destruction");
  resolved instead by keeping every genuine conflict hunk's fork-side content (verified after: each
  touched file diffed byte-identical against `fork/fix/native-aa-wireless`'s own copy, run
  file-by-file). One reference to the now-removed `wifi_direct_new_identity_replaces_typed` string
  survived as a **silent, unflagged auto-merge** in `SettingsFragment.kt` (git's line-based 3-way
  merge did not consider it a conflict even though the fork's rewrite had restructured that same
  dialog's message-building code) and broke the Kotlin compile
  (`Unresolved reference: wifi_direct_new_identity_replaces_typed`); found by the first
  `run_unit_tests.sh` failing to compile, fixed by hand to match the fork's actual simplified
  `.setMessage(R.string.wifi_direct_new_identity_confirm)` call, then re-verified identical against
  the fork's file. That fix was committed separately (`b096460bf`) after the merge commit
  (`da0bac3f3`), since the merge had already completed when the build caught the problem.
  **Anyone merging a rewritten/squashed branch into this testing branch again should
  diff every touched file against the source branch afterward, not just check that the merge
  reported success**: a silent auto-merge is exactly the failure mode a green build can hide.
- **Build gate:** `run_unit_tests.sh` and `build_hur.sh` both ran clean once the above was fixed.
  **2306 tests, 0 failures/errors/skipped** (brief's stated 2294 for a bare `29ef268e` checkout, plus
  this branch's own existing surplus).
- **§3 shared_prefs check:** D-HU read `drwxrwx--x u0_a176 u0_a176`, correct, no root-owned blocker.
- **`wifi-direct-group-name-changes` read `0` on D-HU before the setup run** (it had been restored to
  0 at the end of round 1).
- **The live group precondition (§3) was honored exactly as written:** nothing was cleared between
  the setup run and R2r. `dumpsys wifip2p`'s group-event history confirmed `netId=22` (the setup
  run's group, SSID `DIRECT-ZZ-RigTest`) as `CURRENTLY OPEN EVENT` with 1 connected client
  immediately before R2r's settings write.
- Both automatic pokes in the setup run failed first (`read failed, socket might closed or timeout,
  read ret: -1`, to D-MOTO then D-POCO), matching round 1's pattern exactly; the connection succeeded
  on a later retry with no intervention this time (no Bluetooth toggle was used, unlike round 1).
- Settings restored to the pre-round backup at the end, verified byte-identical after CRLF
  normalization from `adb shell cat`.

## Setup run (not graded)

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=0`, `wifi-direct-stable-identity=true`,
  `log-level=2`, `wifi-direct-group-name=DIRECT-ZZ-RigTest`, `wifi-direct-group-passphrase=RigPass12345`,
  `wifi-direct-identity-user-set=true`. Deleted first: the three `wifi-direct-readback-*` keys (absent
  before launch).
- Decisive log lines, quoted with timestamps:
  - `01:52:48.409 WifiDirectManager.createQuietGroup | Attempting createGroup for Native AA (Attempt 0)...`
  - `01:52:48.459 1.onSuccess | WifiDirectManager: 5GHz createGroup SUCCESS!`
  - `01:52:48.692 WifiDirectManager: group identity ssid=DIRECT-ZZ-RigTest persistent=yes (netId 22) asked=persistent matchesRequest=yes bssid=4E:10:16:ED:EB:52 ...`
  - `01:54:31.011 WirelessServer: Incoming connection detected from /192.168.49.164`
  - `01:54:31.245 SSL handshake complete. Session id: b6igRx4C+enKTFaioYFYZ6XhCWR9lX6w69IiUULdYDs=`
- App force-stopped afterward; the group was **not** removed. `dumpsys wifip2p` confirmed `netId=22,
  ... CURRENTLY OPEN EVENT` still present right before R2r.

## R2r — a changed passphrase reaches the air even with the old group still live (the point)

**PASS**

- Settings written: only `wifi-direct-group-passphrase` changed, `RigPass12345` → `RigPass99999`; the
  name key was left untouched. Nothing else cleared or changed (per §3, the live group from the setup
  run was left standing).
- Discard-rule check: single clean run, not re-run.
- Decisive log lines, quoted with timestamps:
  - The survivor line (`a group named ... is already up ...; reading it instead of tearing it down`)
    **does not appear anywhere in this capture** (`grep -c` = 0).
  - `01:55:26.064 WifiDirectManager.createQuietGroup | Attempting createGroup for Native AA (Attempt 0)...`
  - `01:55:26.110 1.onSuccess | WifiDirectManager: 5GHz createGroup SUCCESS!`
  - `01:55:26.243 WifiDirectManager: group identity ssid=DIRECT-ZZ-RigTest persistent=yes (netId 22) asked=persistent matchesRequest=yes bssid=7A:C3:78:39:40:C7 address=generated stable=no (same name but the BSSID moved from 4E:10:16:ED:EB:52 to 7A:C3:78:39:40:C7; this unit re-addresses the group on every create)`
  - `01:55:26.258 WifiDirectManager: SUCCESS - Providing credentials to listener. SSID=DIRECT-ZZ-RigTest, IP=192.168.49.1, BSSID=7A:C3:78:39:40:C7, identity stable=no`
  - `01:55:28.839 WirelessServer: Incoming connection detected from /192.168.49.164`
  - `01:55:29.064 SSL handshake complete.`
- Measurement: with the app stopped afterward, `wifi-direct-readback-passphrase` read
  **`RigPass99999`**, the new value, not round 1's stale `RigPass12345`. `matchesRequest` read
  **`yes`**.
- Timing: relaunch (`01:55:23.659`, `MainActivity.logLaunchSource`) to `Incoming connection detected`
  (`01:55:28.839`) = **5180 ms**.
- This is round 1's R2 exactly reversed: the group was genuinely recreated (fresh BSSID
  `7A:C3:78:39:40:C7`, distinct from the survived group's `4E:10:16:ED:EB:52`) and the typed
  passphrase reached the air.

## C1 — an unchanged pair still takes the fast path (the control)

**PASS**

- Settings written: none. App was force-stopped immediately after R2r and relaunched unchanged.
- Discard-rule check: single clean run, not re-run.
- Decisive log lines, quoted with timestamps:
  - `01:55:58.904 WifiDirectManager.startNativeAaQuietHost$lambda$23 | WifiDirectManager: a group named DIRECT-ZZ-RigTest is already up from before this bring-up; reading it instead of tearing it down.`
  - `01:55:59.017 WifiDirectManager: group identity ssid=DIRECT-ZZ-RigTest persistent=yes (netId 22) asked=nothing (not this app's create) bssid=7A:C3:78:39:40:C7 address=generated stable=no (this group was already up and was read rather than created, so nothing new was measured)`
  - `01:56:03.569 WirelessServer: Incoming connection detected from /192.168.49.164`
  - `01:56:03.887 SSL handshake complete.`
  - `grep -c "Attempting createGroup"` on this capture = **0**, no `createGroup` ran.
- Timing: relaunch (`01:55:57.350`) to `Incoming connection detected` (`01:56:03.569`) = **6219 ms**.
- The survivor line **is** present and no recreate ran, as required. The fix did not buy R2r's
  correctness by disabling the survivor path outright.

## Anything the brief did not ask about

- **C1 was not faster than R2r on this unit** (6219 ms vs. 5180 ms), contrary to what the brief's
  framing anticipated ("the phone may need a moment longer than round 1 here, because the group is
  genuinely recreated rather than reused"). Breaking each run down: the WiFi Direct group itself was
  ready almost immediately in both cases (R2r: ~2.6 s from launch to credentials delivered; C1: ~1.7 s),
  so the difference between the two paths at the WiFi-Direct level is real but small (under a second).
  The bulk of both runs' total time was the phone-side reconnection after credentials were available
  (~2.6 s for R2r, ~4.5 s for C1), which this round has no lever to isolate from ordinary run-to-run
  variance in the phone's own WiFi association/DHCP timing. Two single measurements are not enough to
  call this a real reversal rather than noise; flagging the raw numbers rather than the brief's
  expected direction, per this channel's own rule against reporting a story the numbers don't
  independently support.
- **The BSSID changes on every create even though the SSID and passphrase do not** ("this unit
  re-addresses the group on every create", R2r's own log line), a detail this round surfaced in
  passing, already known behavior per the app's own comment, not new.
