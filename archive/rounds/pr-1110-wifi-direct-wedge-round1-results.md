# pr-1110-wifi-direct-wedge, round 1 results

**Candidate:** `origin` `pull/1110/head` @ `58828f812c87e66713548f7ae11c6b4f65d83050` (tree `fd9f2168f12b91e9662ed52bc8926baa732c8696`)       **Baseline:** none (the opt-in setting is the control)
**APK md5:** C `bbcf22fed7f82d13e43f8cdcb7b17d8c` (versionName 3.5.0-beta4, versionCode 117, `ACTION_QUERY_STATE` commit `58828f812c87`)
**Unit:** D-HU (UNISOC MT50, Android 14, serial from `rig_devices.sh`), D-POCO (POCO X3 NFC, Android 15, Gearhead 18.0.664204-release). D-MOTO Bluetooth off, D-SAM and D-HP unused.
**Date:** 2026-10-17
**Brief:** `pr-1110-wifi-direct-wedge-round1-brief.md` (transfer commit `51691448e`)
**Evidence:** release `rig-evidence-pr-1110-wifi-direct-wedge`, asset `pr-1110-wifi-direct-wedge-round1-captures.zip`, sha256 `9575c9f34d0c8cea5cb07c8961e43114724c55f127b68b1167e81abf9d6ae1c2`

## Round verdicts

| Run | Verdict |
|---|---|
| R1 | **PASS** |
| R2 | **PASS** |
| R3 | **INCONCLUSIVE** |
| R4 | not run (R3 never reached `sig=1`) |
| R5 | **INCONCLUSIVE** |

## Setup notes

Everything below is a deviation, a brief defect or a rig observation. Nothing was graded on a number the host did not recompute.

**Environment and process**

- The remote `transfer/rig-rounds` had been re-rooted since the local checkout; the clean scratch worktree holding the branch was reset to the fetched tip `51691448e`. `rig_sync.py sync` reported in sync, no restart, `CLAUDE.md` unchanged.
- The Queue lists this brief fifth; it ran first because the operator named its SHA.
- Quirk files read: `tooling`, `wifi`, `lifecycle`, `D-HU`, `D-POCO`. Template read without 7b and 8. TOOLS.md "Pick a tool" table read. Tools used: `rig_preflight.sh`, `rig_cleanup.sh`, `rig_thermal.sh`, `build_hur_cool.sh`, `wt-new.sh`, `rig_devices.sh`. One-round scripts (the brief's blocks, `run_wrap.sh`, `r2_all.sh`, `r2_rest.sh`, `r3_full.sh`, `r3_loop.sh`) live in `rig-data/rounds/pr-1110-wifi-direct-wedge-round1/scripts/`. `extract_brief_blocks.py` matched nothing (it expects backticked names; this brief's are bare `**name.sh**`), so the blocks were extracted with an inline regex.
- The round ran in the host session without a `rig-executor` agent, under `flock` on `/tmp/ohu-rig.lock` through `run_wrap.sh`. Each script's log ends `script exit=143`: that is `th_end`'s `wait` on the thermal watcher it killed, the last command of every brief script, not a failure.
- Host slips, all without effect on a device: (1) a stray `bash b_route.sh &` was started and killed before Stage B; port 5555 was verified still closed. (2) A pointless `cat >> rig-toolkit/TOOLS.md.check` hung a shell and left an empty file in `rig-toolkit/`; it was removed. (3) Stage B's first attempt ran without `lib.sh` sourced, so every adb call was malformed and nothing reached the device; it was re-run correctly.
- R2 reps a-2, b-1 and b-2 were first launched about 1 s after a-1 ended and exited 1 at `flock -n` with no device action and no capture. The thermal watcher's `sleep 20`, orphaned by `th_end`'s kill, held the lock for up to 20 s. `run_wrap.sh` now uses `flock -w 40`; the three reps were re-run and are the ones graded.
- Taps: 0. R4 did not run. HS1 (the "Allow USB debugging?" dialog) was staffed by the operator, who confirmed they would stand by, but no restart ran in R3, so the app never opened a local ADB connection and the dialog never appeared.
- Thermal: R1, R2 and R3 peaks 65 C, 65 C and 65 C (package), throttle counter 0 in every watch log.

**Pre-flight (first, FAIL; second, after the operator enabled D-POCO Bluetooth)**

| Role | ADB | WiFi | BT | Screen | Profiles |
|---|---|---|---|---|---|
| D_HU | ok | 1 | 1 | Awake | HFP:none A2DP:none |
| D_POCO (first) | ok | 1 | 0 | Dozing | none |
| D_POCO (second) | ok | 1 | 1 | Awake | HFP XX:XX:XX:XX:33:59, A2DP up |

D-MOTO Bluetooth read back `state: OFF` after `svc bluetooth disable`. Station: `"Pegue Cdesta"` (associated). Route facts: `id -u` 0, `which su` rc 1 (no su), `ro.adb.secure` 1, `service.adb.tcp.port` empty, `PORT5555_AT_P=0`, `wpa_supplicant` pid 1659, no `wpa_cli`, `android.hardware.wifi@1.0-service` pid 547, `wificond` pid 736, no p2p interface in `/sys/class/net` at rest. D-HU idle baseline already read `curState=P2pDisabledState` with the app stopped and WiFi enabled.

D-POCO was found with `isGroupOwner: true` and the keyguard up (focus `NotificationShade`); `svc wifi disable`/`enable` cleared the group, and `HOME` alone did not clear the keyguard, so `wm dismiss-keyguard` was used (focus then `launcher3`). Bonded list included Navegadortz2 (D-HU, `33:59`), Navegadortz3 (D-SAM, `5E:1D`) and the Motorola. The phone's `Creating rfcomm socket for device: 11:46:03:10:33:59` matches D-HU's radio.

**Settings**

- D-HU `settings.xml` backed up to `settings-backup.xml`, restored in step 6 (diff reported there). `shared_prefs/` owner `u0_a176:u0_a176` mode 771, file 660, app uid 10176: correct, no `chown` needed.
- Recorded, not changed: `native-poke-all-paired=true`, `native-driver-selection-mode=1`, `wifi-direct-stable-identity=true`, `native-poke-bt-macs` and `native-preferred-device-mac` empty, `video-profile-starvation-cap` absent. `connection-modes` = {`wifi`, `self`}. Not named in the brief; left as found.
- Written per brief: base block (`wifi-connection-mode` 3, `native-ap-transport` 0, `log-level` 2, `onboarding-version` 2, `static-bssid` 0, `static-p2p-bssid` 0), ON block for R1-R3. The install did not wipe `settings.xml` (`hu_read P0` showed the keys).
- Identity: `install -r -d` OK; pulled APK md5 equals the built one; 13 `WifiStackRecovery` strings in the dex.

**Stage B**: `adb tcpip 5555` dropped D-HU from `adb devices` for under 60 s; `wait-for-device` returned early and the script printed a spurious `HU_LOST`. Read back after: `id -u` 0, `:5555` listener count 1, `service.adb.tcp.port` 5555. `ROUTE=ADB`. Reverting with `adb usb` is part of the end of the round.

**Brief metrics that were window-defective, recomputed by the host (no candidate effect)**

- R1 `groups>=1`: after a force-stop the P2P group survives (`GroupCreatedState`, `groupFormed: true`, owner true at every `-afterkill` dump), so the relaunch logs `a group named DIRECT-QS-MT50YT610E4GFPSLU is already up from before this bring-up; reading it instead` (1 per rep) and never prints `createGroup SUCCESS`. Graded on that line, `ssl`, and the session time.
- R1 `crit` window starts at the kill; the phone's four `Critical error` lines in every rep fall at the kill instant (R1-1: 14:35:26.420 `GH.WirelessStartup`, 14:35:27.047 and .093 `CAR.SERVICE ... io error`), which is the phone's session dying with the app. Relaunch to end: 0 in all five reps.
- R2 `group_s` counts from `-back`, but the group re-forms inside the toggle phase (0.6 s after each `P2P enabled, auto-starting`), so it printed `none` four times. Used last `P2P enabled` to `createGroup SUCCESS` instead.
- R2 `nosess_ssl` and `rf` use the `-phone` marker, which `ph_on` stamps 4 to 9 s after the Bluetooth enable; the session starts before it. R2-b-1 printed `nosess_ssl=1` for its intended session (phone rfcomm socket 14:54:14.240, `NativeAA: Connection accepted` 14:54:15.084, SSL 14:54:17.536, marker 14:54:18.566). SSL before the first `Connection accepted` is 0 in all four reps, and `MATCH! Starting AapService` is 0, so b-1 was not voided. `rf` counted from `-back` is 1 in all four reps.
- `WPP on TCP connected to the WiFi network` was 0 everywhere; `rf+tcp>=1` was met by `rf`.
- R3 fresh-launch check after a `sig=0` attempt was done as launch, 45 s, `dumpsys wifip2p` `groupFormed: true` (no new capture), a small deviation from "wait 60 s for `createGroup SUCCESS`".

**Closing (step 6)**

- D-HU `settings.xml` restored as root from `settings-backup.xml` (owner `u0_a176:u0_a176`, mode 660); `diff` against the backup is empty. `adb usb` run, `:5555` listener count 0 and `service.adb.tcp.port` 0 afterwards. `svc power stayon false`. D-HU WiFi on (`enable_tries=1`); its `dumpsys wifip2p` reads `P2pDisabledState` at rest, as it did at pre-flight.
- D-POCO: the `sig=0` cleanup path never runs `ph_on`, so its WiFi and Bluetooth were still off after R3; both were switched back on to the opening table's state and read back (`Wi-Fi is enabled`, `state: ON`, `isGroupOwner: false`).
- D-MOTO Bluetooth left OFF, its state when the round began (the brief's end-of-round line says to enable it; the host followed the opening state).
- Idle Gradle daemon left by the host's own build killed with `rig_cleanup.sh kill`. Closing pre-flight: `D_HU ok 1 1 Awake`, `D_POCO ok 1 1 Awake HFP:XX:XX:XX:XX:33:59 A2DP:up`, `PREFLIGHT OK`; `rig_cleanup.sh list`: no strays, lock free. No logcat process left.
- Build worktree removed; APK kept at `rig-data/rounds/pr-1110-wifi-direct-wedge-round1/apk-C.apk`.

## R1

**PASS**

- Settings written: ON block (`auto-restart-wifi-p2p-stuck` true, `wifi-restart-root-granted` true) plus base block; stuck/dismissed keys deleted before the run.
- Radio state: D-POCO WiFi and Bluetooth off at launch (`Wi-Fi is disabled`, `state: OFF`), on 20 s after launch; D-HU WiFi on throughout.
- Discard-rule check: clean. `MATCH! Starting AapService` 0, one `createGroup SUCCESS` in the capture (the first bring-up, 14:33:51.439), interface `p2p-wlan0-0` only.
- Chain: 5 reps, no void, no `TIMEOUT`, `R1-diag` not needed.

| Rep | wedged | restart | cannot | held | stuck_pref | edges | fails | busyrm | sup | halrm | sess (s) | ssl | rf | crit (kill-end / relaunch-end) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| R1-1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 12.5 | 1 | 7 | 4 / 0 |
| R1-2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 13.6 | 1 | 8 | 4 / 0 |
| R1-3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 12.9 | 1 | 8 | 4 / 0 |
| R1-4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 11.9 | 1 | 8 | 4 / 0 |
| R1-5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 10.3 | 1 | 7 | 4 / 0 |

Session waits from the script log: 15, 15, 15, 15, 10 (5 s poll steps). Whole-capture counts: wedge line 0, `WifiStackRecovery` 0, `Failed to setup P2p iface in supplicant` 0, `AutomationReceiver` 17, `AutomationMarker` 17.

Reach: the largest `edges` and `fails` in any rep are both 0, so **D-HU's force-stop shape reaches neither policy input**. A PASS here would look identical if the policy did nothing; what it shows is that the shape that wedged the contributor's phone does not produce even one disabled broadcast or one counted create failure on D-HU. Framework side: `sup=0`, `halrm=0`, and every `p2pdump` reads `GroupCreatedState`, `groupFormed: true`, owner true, WiFi enabled (the `disabledState` grep counts 14 at the start of the chain, 5 from R1-3, 0 at R1-5-end; these count old `P2pDisabledState` entries in the dump's history, not the live state).

The brief's `groups>=1` and `crit=0` as literally written are not met; see the Setup notes for why the host substituted the measurements above. Stating it plainly: if the brief's literal conditions are held to, R1 reads as "not graded PASS or FAIL by the letter" and the host's PASS rests on those two substitutions.

## R2

**PASS**

- Settings written: ON block plus base block, keys deleted before each rep.
- Radio state: D-POCO off until the toggles and the 90 s hold ended (`ph_off` read back), then on. D-HU WiFi toggled by `svc wifi disable` then `hu_wifi_on`.
- Discard-rule check: single-group rule does not apply (re-arm after each toggle is the behaviour under test). `MATCH! Starting AapService` 0, SSL before first `Connection accepted` 0, `crit=0` in all four reps. `p2p-wlan0-1` appears in a-2, b-1 and b-2 (stale group torn down at launch).
- Variants: a is OFF 8 s, ON 12 s, 2 cycles; b is OFF 3 s, ON 5 s, 3 cycles. Four graded reps, no extra reps needed.

| Rep | toggle edges | toggle fails | wedged | restart | cannot | held | stuck_pref | enable_tries | P2P enabled to group (s) | busyrm | sess (s) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| R2-a-1 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 1, 1 | 0.616, 0.637 | 2 | 11.9 |
| R2-a-2 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 1, 1 | 0.632, 0.644 | 2 | 14.8 |
| R2-b-1 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 1, 1, 1 | 0.631, 0.635, 0.629 | 3 | 5.9 |
| R2-b-2 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 1, 1, 1 | 0.616, 0.635 | 2 | 8.5 |

Decisive lines, R2-a-1: `14:44:23.755 WIFI_P2P_STATE_CHANGED_ACTION state=1`, `14:44:32.185 P2P enabled, auto-starting Native AA quiet host`, `14:44:32.208 Native AA removeGroup before recreate failed (reason=BUSY`, `14:44:32.801 5GHz createGroup SUCCESS!`; the same again at 14:44:49.359, 14:44:56.750, 14:44:57.387.

Reach: the policy needs `edges>=2` **and** `fails>=1`. Two reps had `edges` 2, two had 3, and `fails` was 0 in all of them: the user's WiFi toggle produces the disabled-broadcast input but never the create-failure input, because the group re-forms 0.6 s after WiFi comes back. The concern ("a user toggle trips the policy") is therefore not reachable on D-HU. As in R1, a PASS would look the same if the policy did nothing; the reach figures are the evidence.

## R3

**INCONCLUSIVE**

Six attempts, two per method, none reached `sig=1`, so the follow-up (restart route, held-back check, session back) never ran. No part of the restart path was exercised on hardware.

- Settings written: ON block plus base block per attempt.
- Radio state: D-POCO off for every attempt.
- Discard-rule check: n/a (induction runs).

| Attempt | Induction | sig | `sup` | `wifi_on_30s` | edges | fails | busyrm | wedged | p2p state at 30 s and 120 s | stack check |
|---|---|---|---|---|---|---|---|---|---|---|
| R3-M1-1 | kill `wpa_supplicant`, group up | 0 | 0 | 1 | 1 | 0 | 0 | 0 | `P2pDisabledState`, WiFi enabled | cycled once |
| R3-M1-2 | same | 0 | 0 | 1 | 1 | 0 | 0 | 0 | `P2pDisabledState`, WiFi enabled | cycled once |
| R3-M2-1 | force-stop, kill supplicant, relaunch | 0 | 0 | 0 | 1 | 0 | 0 | 0 | `P2pDisabledState`, WiFi disabled | cycled once |
| R3-M2-2 | same | 0 | 0 | 0 | 1 | 0 | 0 | 0 | `P2pDisabledState`, WiFi disabled | cycled once |
| R3-M3-1 | kill `android.hardware.wifi` (pid 547) | 0 | 0 | 0 | 1 | 4 | 1 | 0 | `P2pDisabledState`, WiFi disabled | cycled once |
| R3-M3-2 | same | 0 | 0 | 0 | 1 | 4 | 1 | 0 | `P2pDisabledState`, WiFi disabled | cycled once |

What the inductions did (R3-M1-1, 15:12:29): `SupplicantStaIfaceHalAidlImpl: Handle supplicant death`, `WifiP2pService: Wifi enabled=false`, P2P interface torn down, app logged `startNativeAaQuietHost ... WiFi is off and this Android does not let an app switch it on.`; `wpa_supplicant` restarted 0.5 s later (new pid 9911) but the P2P framework stayed in `P2pDisabledState` with no group and no create attempt. In M2 and M3 the framework went further and turned WiFi itself off. R3-M3-1, 15:27:56: `WifiHalHidlImpl: IWifi HAL service died!`, then `WifiController: Recovery has been throttled, disable wifi` at 15:27:56.574, `STA disabled, return to DisabledState` at 15:27:56.660. The app's four `5GHz createGroup failed (BUSY (System is busy, retry needed))` lines (retries 1/4 to 4/4, 2 s apart, 15:27:56 to 15:28:02) therefore happened with WiFi off.

Why the policy stayed quiet in each case: M1 left WiFi on but produced `fails=0` (the app never tried a create), M2 and M3 produced the failures but WiFi was off. The policy needs WiFi on, at least 2 disabled broadcasts, and at least 3 failed creates inside 60 s; the rig never produced all three together. The framework signature `Failed to setup P2p iface in supplicant` and `removeIfaceInternal: iface(name)=` appeared 0 times in all captures, so D-HU did not show the contributor's state either. After every attempt the stack needed one `svc wifi disable`/`enable` cycle to bring a group back; none needed a second.

Tool-limit note: killing the supplicant or the HAL as root does not reproduce a mid-session force-stop wedge on this MT50 / Android 14 stack. An INCONCLUSIVE here moves the coverage onto the JVM tests (`P2pStackWedgePolicyTest`), as the brief expected.

## R4

Not run. R3 never reached `sig=1`, and the brief runs R4 only after it. Marking it UNTESTABLE or INCONCLUSIVE would invent a verdict; it is reported as "did not run". No taps were used.

## R5

**INCONCLUSIVE**

Command: `r5.sh` over all 11 head-unit captures (R1, R2-a-1, R2-a-2, R2-b-1, R2-b-2, R3-M1-1 to R3-M3-2).

- `restarts=0` and `inside_session=0` for every capture. Total restarts this round: **0**. No capture contains a `WifiStackRecovery` line, a wedge line, an `AdbManager: Command '` line or a banner line.
- No restart ran, so the live-session guard was never tested on hardware.

## Anything the brief did not ask about

- **A user's WiFi toggle and a force-stop both leave the P2P stack fine on D-HU.** The three inputs the policy needs were never present together at any point in 11 captures. The two situations the contributor's PR worries about for false positives (R1 and R2) produced 0 disabled broadcasts and 0 create failures (force-stop) and 2 or 3 broadcasts with 0 failures (toggle).
- **Killing the WiFi HAL makes Android turn WiFi off by itself** (`Recovery has been throttled, disable wifi`). Anything that wants to provoke "WiFi on, P2P disabled, creates failing" needs a lever that keeps STA up; killing `wpa_supplicant` keeps WiFi reading enabled but does not make the app attempt creates. A third lever (for example holding the supplicant's P2P interface busy) has not been tried and is outside this brief.
- **A supplicant kill leaves D-HU's P2P stack disabled until WiFi is cycled.** `wpa_supplicant` is restarted by init within 0.5 s, `dumpsys wifi` reads enabled, and `dumpsys wifip2p` stays `P2pDisabledState` for at least 120 s with no group. This is a state the app does not detect on D-HU today: it logs `WiFi is off and this Android does not let an app switch it on`, which is not a counted create failure. Worth a look by whoever owns the policy, but it is a different signature from the contributor's.
- **After a force-stop the group outlives the app on D-HU** and the relaunch adopts it (`already up from before this bring-up`). This is the "orphaned P2P group" shape the brief named; it is cheap and clean (a session in 10 to 14 s).
- **Brief fixes for next time:** (1) R1 `groups>=1` should accept the `already up from before this bring-up` line; (2) R1 `crit` should be counted from the relaunch marker; (3) R2 `group_s` should count from the last `P2P enabled` line, and `nosess_ssl`/`rf` should end/start on the Bluetooth enable, not the marker; (4) `extract_brief_blocks.py` could accept bare bold names; (5) `th_end` should kill the watcher's child `sleep`, or scripts hand the lock to a successor with `flock -w`.
- **D-HU left in TCP mode** until the end-of-round `adb usb` (step 6 restores it, together with settings and the radios).
