# pr-1064-nearby-attempts - round 1 results

**Candidate C:** `codex/nearby-tunnel-lifecycle` @ `32311d316abdd63081f8629b0dbefcfbb4033f68`   **Baseline B:** merge-base @ `2ca3b1f1a807faa11ba8608fd8a905acc09c65a9`
**APK md5:** B `c9413ad01beaf74b74e4423be5a929ba` / C `084543b295c6487392c0b3e491ed8715`   **Helper:** wireless-helper debug @ `8ac36c9bcc80731b78949c04fdc63178b5904f2f`, md5 `2b7b86874f6c5d71618aafec1893ba35`
**Units:** D-HU (UNISOC MT50, Android 14, API 34), D-POCO (POCO X3, API 35, Gearhead `17.9.664004-release`), D-MOTO (API 34, Gearhead `17.9.664004-release`, Stage U only)
**Date:** 2026-10-06
**Evidence:** release `rig-evidence-pr-1064-nearby-attempts`, asset `pr-1064-nearby-attempts-round1-captures.zip`, sha256 `242310bc59d7f4cfb7f91b65e88b903b91ff500723dd4262d71985fb2b72c9bb`

**Round verdict: INCONCLUSIVE.** Nothing on the candidate was exercised. Every Nearby session on this rig stopped at a phone-side step that no build touches, so none of the four claims (late tunnel, stop in the window, repeated reconnects, refused hand-off) was measured on either arm. The grade rests on the brief's own stop rule, quoted in R0 and N0.

## Setup notes

- **Brief resolved from a commit carrying five briefs;** the operator chose all five in order, and this is the second. Quirk files read: `rig-quirks/topics/tooling.md`, `rig-quirks/topics/gearhead.md`, `rig-quirks/topics/wifi.md` (the house network lines), `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-POCO.md`, `rig-quirks/units/D-MOTO.md`.
- **Pre-flight** (`rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt`): PREFLIGHT OK. D_HU WiFi 1, BT 1, Awake, HFP none, A2DP none. D_POCO WiFi 1, BT 1, Awake, HFP `XX:XX:XX:XX:33:59`, A2DP up. A first read of D-HU's WiFi showed the radio on but `Supplicant state: DISCONNECTED` with no IP; the operator joined it by hand (the rejoin needs a tap), and the second pre-flight read plus `dumpsys wifi` showed `Pegue Cdesta`, 5745 MHz on both units (D-HU 192.168.1.4, D-POCO 192.168.1.8).
- **Builds.** B and C with `build_hur_cool.sh`, each APK copied out at once; JVM tests with `run_unit_tests.sh`; the helper by hand Gradle in a scratch worktree at `8ac36c9` (`thermal_guarded.sh ./gradlew assembleDebug`) because `build_wireless_helper.sh` hard-codes its checkout path. Build phase reached 93C once (the guard pauses Gradle at 86C, no capture was recording).
- **Worktree deviation.** The three scratch worktrees had no `local.properties`; I copied `sdk.dir=/home/oscar/Android/Sdk` into each. No source change.
- **Deviation 1, foreground app on D-HU.** The first N0 attempt (kept as `attempt1-n0-nofg/`) found no endpoint in 3 of 3: `NearbyConnections: Missing required permissions, aborting call to startDiscovery()` and `NearbyManager: [ERROR] Discovery failed: 8034: MISSING_PERMISSION_ACCESS_COARSE_LOCATION`, with every permission in 4.3 granted. `appops get` showed `COARSE_LOCATION: foreground` and a `rejectTime` seconds old, i.e. Google Play Services refuses the scan for an app that is not foreground. I redefined `n_open` (in `head.sh`) to `am start` the main activity after the settings write, once; discovery then worked at once (`Endpoint FOUND: M2007J20CG (71DL)`). The brief's `n_open` does not launch the app.
- **Deviation 2, thermal watcher leak.** `apk_check` starts a thermal watcher that holds the rig lock's file descriptor after the block ends, so the next `flock -n` launch silently did nothing. I killed it by pid and added a kill to `n_close`.
- **Deviation 3, `connection-modes` on D-POCO as head unit.** The brief's key list for Stage U omits it. D-POCO's baseline holds `{usb}`, so the app logged `WiFi is not one of the chosen connection modes. Not arming it` and discovery never started. I added `set:connection-modes=usb,wifi`. Brief erratum.
- **Deviation 4, D-MOTO's Bluetooth self-reverts.** After `svc bluetooth disable` it read `enabled:true` again within about 40 s, three times (a script pre-check refused once; two reads at step 3). I re-disabled it each time, with the reads in `bt-moto-relever.log`.
- **Helper settings write raced the install.** On D-MOTO the first `exec-in` write left no `WirelessHelperPrefs.xml`; a second identical write landed and read back `connection_mode` 4.
- **Dongle plugged early.** The operator plugged the dongle into D-POCO at 20:12:41, before the script's cue (`UsbLauncherListener.onUsbAttach | Normal USB device attached: Google Pixel 4 (VID: 18D1 PID: 4EE1)`). That arm start is voided and kept as `attempt1-u1-B/`.
- **Settings.** D-HU backup `settings-backup-DHU.xml`, written from it with `hu_put`; D-POCO backup `settings_backup_pr1064.xml`. D-HU audio keys found as `use-aac-audio` false, `audio-latency-multiplier` 8, `audio-queue-capacity` 20 and not changed. Delta after `adb install -r` of B: empty. Both units' settings restored at the end and read back equal to the backups (a first D-POCO read raced the asynchronous write and printed empty; a second read matched). D-MOTO's Bluetooth restored to on, as found.
- **Hand steps** (`hand-steps.log`): D-HU WiFi join (before the first run); H1 unplug D-POCO from the PC cable, done 20:11:11; H1 plug the dongle, cued 20:16:31 (the operator plugged it twice more, 20:17 and 20:18, see U1); the closing cable step is open at the time of writing.
- **Grading.** All counts come from my own greps over the named windows; no executor was used.

## R0 Gate

**PASS**

Every item the round reached; the Stage N stop (N0) kept the C install items from being run, listed below.

| Arm | md5 | NearbyAttemptGuard / ConnectionAdmission / HeldServerSocket | commit | JVM tests |
|---|---|---|---|---|
| B | `c9413ad01beaf74b74e4423be5a929ba` | 0 / 0 / 0 (pulled APK from D-HU) | `2ca3b1f1a807` | 2689, 0 failures, 0 errors |
| C | `084543b295c6487392c0b3e491ed8715` | 10 / 21 / 0 (the built APK, never installed) | not run | 2702, 0 failures, 0 errors |

The two md5s differ. Not run: C's install on a unit, its `ACTION_QUERY_STATE` commit and its install-time settings delta, because Stage N stopped after B's N0. Helper `git rev-parse HEAD` printed `8ac36c9bcc80731b78949c04fdc63178b5904f2f`. Network: both units `Pegue Cdesta`, 5745 MHz. Gearhead `17.9.664004-release` on D-POCO and on D-MOTO. The brief's stop rule, quoted: "If B scores 0 of 3, stop Stage N: N1, N2 and N3 are INCONCLUSIVE ('Nearby helper sessions do not form on this phone')."

## N0

**INCONCLUSIVE**

B formed 0 of 3 sessions. C was not run, per the stop rule.

| Arm | attempt | endpoint id | HIGH reached | session | proxy |
|---|---|---|---|---|---|
| B | 1 | 71DL | yes | 0 | 0 |
| B | 2 | RTOD | yes | 0 | 0 |
| B | 3 | 1Q06 | yes | 0 | 0 |

Unit side, attempt 1 (`N0-B.logcat`): `Connected successfully!` 20:01:30.896, `Wi-Fi Bandwidth Upgrade successful (Quality: HIGH)` 20:01:31.441, `Waiting 800ms for phone state synchronization...` 20:01:31.449, `Initiating stream tunnel to 71DL...` 20:01:32.251, then `NearbySocket: phone never sent its half of the stream tunnel within 8000ms` 20:01:40.475 and `Handshake failed`. Attempts 2 and 3 are identical in shape (HIGH 756 ms and 747 ms after `Connected`).

Phone side (`N0-B.phone.logcat`), the cause. The helper does reach HIGH and sends its half, then cannot start Android Auto:

```
20:01:30.992 HUREV_TRIGGER: Activity launch failed (Permission Denial: starting Intent { flg=0x34000000
  cmp=com.google.android.projection.gearhead/com.google.android.apps.auto.wireless.setup.service.impl.WirelessStartupActivity ... }
  from ProcessRecord{...:com.andrerinas.wirelesshelper.debug/u0a278} ... not exported from uid 10193). Attempting Broadcast fallback...
HUREV_TRIGGER: Broadcast fallback 1 (WirelessStartupReceiver) sent.
HUREV_TRIGGER: Broadcast fallback 2 (WifiBluetoothReceiver START_WIRELESS_PROJECTION ...) sent.
HUREV_NEARBY: Launch timed out without connection.
```

That is 3 `Activity launch failed` and 3 `Launch timed out without connection` over the three attempts, and `AA is now flowing through proxy` 0 times. This is the shape of the known Gearhead regression where the wireless broadcast fallback no longer starts Android Auto. It is not a build difference: B and C were never compared. `release-test` round 1 formed three sessions here with Gearhead 17.5 and helper 1.9.3; this round is Gearhead 17.9 and helper 1.9.4.

The 30 last `HUREV_` lines are in `N0-B.last30-HUREV.txt` and the unit's last `NearbyManager:` line is in `N0-B.last-NearbyManager.txt`, both in the evidence asset.

## N1 (the point of the round)

**INCONCLUSIVE**

Not run. The stop rule after N0 on B applies: Nearby helper sessions do not form on this phone. No hits, no `init`, `hand`, `block` or `sconn` counts were taken on either arm.

## N2

**INCONCLUSIVE**

Not run, same stop rule.

## N3

**INCONCLUSIVE**

Not run, same stop rule.

## U1

**INCONCLUSIVE**

Arm B ran; arm C was not run (see below). The brief lists this outcome in advance: "U1 when ... the dongle never spends its 60 s budget".

| Arm | gate HIGH | spent line | tries | hit | req | ssl | found | already | pdrop | fatal | finding reproduced |
|---|---|---|---|---|---|---|---|---|---|---|---|
| B | yes (`gate id=UBAP`, `UG=HIGH`) | no (`SPENT=no`, 240 s) | 0 | no | - | - | - | - | - | - | not measured |

The gate passed: D-MOTO's helper and D-POCO's head unit reached HIGH with D-MOTO's Bluetooth off. The dongle then never attached. At 20:17 and again at 20:18 the operator plugged it in and the capture showed only port role changes (`UsbPortManager: USB port changed: port=UsbPort{id=otg_default ...}` at 20:17:44 and 20:18:15, `data_role` host then device, `power_role=sink`, `Charging connected device via USB`), with no `USB_DEVICE_ATTACHED`, and `/sys/bus/usb/devices` listed only `1-0:1.0 2-0:1.0 usb1 usb2`. `bt_moto at step3: enabled:true` was logged just before I re-disabled it. The script closed with `SPENT=no` and the TSV line `B INCONCLUSIVE dongle never spent its budget`.

The operator reported two facts about this dongle: it enumerates only on a Bluetooth connection, and it stays enumerated after Bluetooth goes off only while D-MOTO is joined to the dongle's own WiFi Direct network. Together with the earlier attach at 20:12:41, when D-MOTO's Bluetooth had self-reverted on, this means the brief's premise (a dongle with no phone, retrying on USB) cannot be produced: Bluetooth off removes the dongle from the bus, Bluetooth on gives it a phone, and the WiFi Direct variant takes D-MOTO off the house network that Nearby's upgrade needs. Arm C was not run because that failure does not depend on the build, and a C arm would stop at the same step.

## Anything the brief did not ask about

- **The 800 ms window is real and measurable on B.** From `Connected successfully!` to HIGH took 545 to 756 ms here, then 802 to 804 ms to `Initiating stream tunnel`. The brief's note that HIGH arrived 7 to 8 ms after `Connected` (a different round, different Gearhead) does not hold on this rig now, which makes the N1 lever's delay steps (0, 150, 300 ms) land well before the window opens. A rerun of N1 should anchor on `Wi-Fi Bandwidth Upgrade successful`, not on `Connected successfully!`.
- **A Nearby-only gate would have been runnable** if N0 had graded on the phone's `Payload RECEIVED ... Tunnel is B-DIR now` and the unit's `Initiating stream tunnel`, both of which printed on B. The brief grades N0 on a full session, so I followed it.
- **Brief fixes worth making:** `n_open` must foreground the app on API 34 (or the brief must name the location op); the Stage U key list needs `connection-modes`; and U1 needs a dongle that spends its budget without a phone, or a different lever.
- **Open at the time of writing:** D-POCO is off adb (its adbd is in USB mode, the dongle is in its only port); the closing cable step from the brief is waiting for the operator.
- **Evidence:** `pr-1064-nearby-attempts-round1-captures.zip`, asset of release `rig-evidence-pr-1064-nearby-attempts`.
