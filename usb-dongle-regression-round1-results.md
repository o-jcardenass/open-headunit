# usb-dongle-regression, round 1 results

**Candidate:** none (release arms). A = v.3.4.0 `013b05bc`, B = v.3.5.0 `1c1b5b72`, C = B plus the beta4 keycode list (`82f148f9fe3c`, local commit, never pushed)   **Baseline:** none
**APK md5:** A `d83bf3f026dbc595adcd02798b6c8aad` / B `e65aa7351c4bb92dda460ddd824885a2` / C `097bf82547dbe46918442f6d65c0940d`. Prepare APK on both hosts `7219f83f3f82adb5ded4b5a8adf6e805`.
**Unit:** D-HP (API 17) and D-POCO (API 35) as USB host with the dongle (enumerates `Pixel 4`, idle `18d1:4ee1`, accessory `18d1:2d00`), D-MOTO as its phone (Gearhead 18.0.664204-release)
**Date:** 2026-10-23
**Evidence:** release `rig-evidence-usb-dongle-regression`, asset `usb-dongle-regression-round1-captures.zip` (6756922 bytes, sha256 `30777e9bc08247f5282cecf4ebe86a9302bc1f408a1d459079912e3df1164e75`).

**Result in one line:** the rig's dongle does not reproduce the fault on v.3.5.0 on either host (H-B 3 of 3, P-B 5 of 5 projected), so arm C cannot be read and the keycode list is neither confirmed nor ruled out. The control arm v.3.4.0 failed 5 of 5 on D-POCO.

## Setup notes

- Pre-flight, opening: D_MOTO Bluetooth was off and failed it. The operator turned it on; pre-flight then read D_HP wifi 1 bt 1, D_POCO wifi 1 bt 1, D_MOTO wifi 1 bt 1, no BT profiles. D-MOTO's Bluetooth read off again once after the operator reseated its cable and was re-enabled with `svc bluetooth enable` (brief Prepare step 5), read back `enabled: true`, `state: ON`, bonded `carplay_box_F96B`. Closing restores D-MOTO to its original state (Bluetooth off).
- Quirk files read: `topics/tooling.md`, `units/D-HP.md`, `D-POCO.md`, `D-MOTO.md`, `D-SAM-and-D-HP.md`.
- Transfer branch could not be checked out in the main checkout (held by another worktree); it was fast-forwarded in that worktree. A `git reset --hard` meant for it ran on `main` once and was undone from the reflog (`a8d8a92d8`); no data lost.
- Brief errata and local scripts: (1) `build.sh` copies from `$WT/apks`, but `build_hur_cool.sh` writes to `rig-data/apks/`; arm B's copy was done by hand and the script patched. (2) The brief's `mark` reaches only the unit; the phone never got `RIGMARK`, so `pcrit` would always print 0. `libdongle2.sh`/`libdongle3.sh` redefine `mark` to also run `log -t RIGMARK "RIGMARK <label>"` on D-MOTO (checked: the line lands). (3) D-HP (API 17) has no `adb exec-in`, so its `pocoput` inner writer was replaced with `adb push` plus `run-as sh ohu_cp.sh`; read-back `keys_ok ok` and `video-codec` H.264 count 1 verified before the stage. D-POCO kept the brief's writer. (4) `dest_id` stubbed (it needs `aapt2` and would append `DEST_UNKNOWN` to the TSV). Scripts: `scripts/build.sh`, `scripts/stageH.sh`, `scripts/stageP.sh`, `libdongle1/2/3.sh`, `ohu_cp.sh`. The brief's `stageP.sh` only echoed the skip rule; mine skips P-C for real.
- `svc power stayon true` on D-MOTO was withheld until the operator approved it; applied, closing sets it false.
- `settings.xml` delta vs last backup: D-POCO against `usb-reattach-round4`: six keys now absent (`auto-connect-last-session`, `auto-start-on-usb`, `force-software-decoding`, `kill-on-disconnect`, `reopen-on-reconnection`, `use-libusb`) and added `allow-external-configuration=true`, `debug-video-low-latency=true`, `enable-audio-sink=false`, `has-accepted-disclaimer`, `has-completed-setup-wizard`, `narrow-band-profile-cap=false`, `show-fps-counter=true`, `use-head-unit-microphone=false`, `video-profile-starvation-cap=false`, `wifi-direct-ip-repeated`. D-HP against `hus-version-retry-round1`: `last-connection-type` wifi (was usb), `last-connection-ip` 192.168.1.20, USB device empty, one extra `usb` set item. All keys of section 5 were rewritten before every cycle, so these differences do not reach the runs. Installing B left both files byte-identical to the backups.
- Identity gate (pulled APK md5 equals the build): B on D-HP and D-POCO `e65aa735…`; C on D-HP `097bf825…`; A on D-POCO `d83bf3f0…`. DEX symbols `AapTlsWriter`/`StaleAccessoryRecoveryPolicy`: A 0/0, B 12/10, C 12/10. `ACTION_QUERY_STATE` `commit`: B `1c1b5b7261eb`, C `82f148f9fe3c`, A `013b05bc0e7f`. Trees: B `c126e7a9…`, A `e86e02a1…`; C diff 1 file, 57 deletions.
- Host thermal: a 1.5 min wait (72 to 79 C) before H-B just after the builds; thermal max 69 C in Stage H and 65 C in Stage P, throttle delta 0.
- Windowing: every count is between a cycle's `-go` and `-end` markers (`dongle_cycle.py`). `MATCH! Starting AapService` 0 in all four captures. 0 voided cycles. Every cycle's `-go`/`-end` markers landed, and each capture shows 17 to 19 `AutomationReceiver:` lines.
- Closing: prior APK reinstalled and pulled on D-HP and D-POCO, md5 `7219f83f3f82adb5ded4b5a8adf6e805` on both. `settings.xml` restored: D-POCO byte-identical to its backup; D-HP identical once carriage returns are removed (its backup was taken through the API 17 shell with CRLF line ends; the restore wrote the CR-free copy). D-MOTO `stay_on_while_plugged_in` 0, Bluetooth off (its opening state). H5 done by the operator; both hosts back on USB, wireless adb disconnected. D-POCO's `ACTION_EXIT` answered `startForegroundService() not allowed` because the app was already stopped; force-stop was used. The D-HP reinstall took about 3 minutes over wireless adb and its APK pull stalled, so it was finished over USB after the operator recabled D-HP.
- USB grants after the round, against the Prepare dumps: D-HP gained two device filters for our package, vendor `0x18D1` with products `0x4EE1` and `0x2D00` (the dongle idle and accessory ids); D-POCO's dump gained extra accessory-preference entries naming our package. Both follow from allowing "Always" on the dongle dialog. Left in place.
- Closing pre-flight: D_HP wifi 1 bt 1, D_POCO wifi 1 bt 1, D_MOTO wifi 1 bt 0 (all as the opening state), no BT profiles, 0 logcat readers. Its one FAIL is the operator's `tail -n0 -F hand-steps.log` cue terminal (pid 30490), a stray by design, not killed.
- The brief asks for `marks.log`; no script wrote one (markers live in the captures and TSVs), so the zip has none.
- D-HP's host capture is tag-filtered (API 17) and its `hu_cap_alive` check held every cycle. Pre-Stage-H hand step (dongle into D-HP) was done by the operator on request, not as a cue; H2 cue at 17:42:34 is in `hand-steps.log`.

## H-B

**INCONCLUSIVE**

The fault does not reproduce on D-HP: 3 of 3 cycles projected (the brief's "projects" band, 2 or more).

| Cycle | handshakes | ssl | sdr | reply | silent_detach | thr_max | projected | selected | crit | bat |
|---|---|---|---|---|---|---|---|---|---|---|
| H-B-1 | 2 | 1 | 1 | 1 | 0 | 8 | 1 | 1.7 | 0 | 100 |
| H-B-2 | 2 | 1 | 1 | 1 | 0 | 8 | 1 | 1.7 | 0 | 100 |
| H-B-3 | 2 | 1 | 1 | 1 | 0 | 8 | 1 | 1.7 | 0 | 100 |

My own count over the capture: 27 `Throughput over` lines, 0 `stale accessory` lines.

## H-C

**INCONCLUSIVE**

H-B projects, so H-C cannot be read (the brief expected this). For the record C also projected 3 of 3, same row shape: handshakes 2, ssl 1, sdr 1, reply 1, silent_detach 0, thr_max 8, projected 1, selected 1.7, crit 0, bat 100 in each of H-C-1, H-C-2, H-C-3. 27 `Throughput over` lines.

## P-B

**INCONCLUSIVE**

v.3.5.0 projects on D-POCO: 5 of 5 cycles (the brief's "does not reproduce" band is 3 or more). `silent_detach` is 0 in every cycle.

| Cycle | handshakes | ssl | sdr | reply | silent_detach | thr_max | projected | selected | crit | bat |
|---|---|---|---|---|---|---|---|---|---|---|
| P-B-1 | 1 | 1 | 1 | 1 | 0 | 8 | 1 | 1.7 | 2 | 100 |
| P-B-2 | 4 | 1 | 1 | 1 | 0 | 8 | 1 | 1.7 | 0 | 99 |
| P-B-3 | 2 | 1 | 1 | 1 | 0 | 8 | 1 | 1.7 | 0 | 98 |
| P-B-4 | 2 | 1 | 1 | 1 | 0 | 8 | 1 | 1.7 | 0 | 98 |
| P-B-5 | 2 | 1 | 1 | 1 | 0 | 8 | 1 | 1.7 | 0 | 97 |

Over the capture: 40 `Throughput over`, 5 `SSL handshake complete`, 0 `UsbLauncher: stale accessory`. `Bluetooth service announced with carAddress` not examined.

In cycles 2 to 5 the first handshake attempt of the cycle fails and a later one succeeds. Example, P-B-3:
`17:51:46.855 Handshake: Drained 2190 bytes of stale USB data before version request`
`17:51:46.856 Handshake: Version request send failed (ret=-1), attempt 1`
`17:51:46.863 USB Intent: ... USB_DEVICE_DETACHED`
`17:51:47.057 Handshake: Version request/response failed after 1 attempt(s). last ret: -1`
`17:51:50.044 USB Intent: ... USB_DEVICE_ATTACHED`, then the next attempt reaches SSL. 4 `Drained` and 4 `send failed` lines in the capture.

P-B-1 `crit=2` on a projecting cycle: the two phone lines are `CAR.SERVICE: Critical error 3 detail: 52 msg: io error` at epoch 1792795697.123 and 1792795715.876. Not analysed further; P-C was not run so the "crit 0" condition is not used.

## P-C

**INCONCLUSIVE**

Not run. P-B projected 5 of 5, so the brief skips it as unreadable. No C cycle exists on D-POCO. (C did project 3 of 3 on D-HP, which is also a non-reproducing host.) The question the round asked, whether the 98-code keycode list causes the fault, is open: the rig's dongle never failed on B.

## P-A

**FAIL**

The control arm v.3.4.0 projected 0 of 5 (pass band is 3 or more). By the brief this makes Stage P INCONCLUSIVE ("the rig or the dongle is not healthy").

| Cycle | handshakes | ssl | sdr | reply | silent_detach | thr_max | projected | selected | crit | bat |
|---|---|---|---|---|---|---|---|---|---|---|
| P-A-1 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 1.7 | 2 | 97 |
| P-A-2 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 1.7 | 2 | 96 |
| P-A-3 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 1.7 | 2 | 96 |
| P-A-4 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 1.7 | 2 | 95 |
| P-A-5 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 1.7 | 2 | 95 |

Over the capture: 0 `SSL handshake complete`, 0 `Service Discovery Request`, 0 `Throughput over`, 5 `Drained` and 5 `Version request send failed`. The failure is earlier than the one the report describes (this one never reaches SSL). P-A-2:
`17:57:50.602 Found device already in accessory mode: Google Pixel 4 (VID: 18D1 PID: 2D00)`
`17:57:51.345 Start Aap transport handshake for ...`
`17:57:51.854 Handshake: Drained 2353 bytes of stale USB data before version request`
`17:57:51.856 Handshake: Version request send failed (ret=-1), attempt 1`
`17:57:51.862 USB Intent: ... USB_DEVICE_DETACHED`
`17:57:52.057 Handshake: Version request/response failed after 1 attempt(s). last ret: -1`
Phone side, the first `Critical error` of P-A-2 with the lines before it: `1792796291.611 CAR.GAL.GAL.LITE: at jnh.run(SourceFile:103)`, `.623 GH.ConnLoggerV2: ... FRAMER_READ_IO_EXCEPTION`, `.633 CAR.STARTUP: Protocol error 3 (52): io error`, `.633 GH.CarConnSession: Tearing down`, `.634 GH.WirelessStartup: ProjectionErrorCode = 3, ProjectionErrorDetail = 52, io error`, `.634 GH.WirelessStartup: Critical error encountered, attempting to handle result.`

The same first-attempt failure (stale data, send failed, detach) also appears on B (above) but B's later attempt succeeds and A's does not. The cause of that difference was not isolated this round; B carries the version-request retry and recovery code that A lacks (A has 0 of both DEX symbols), which fits but was not tested.

## Stage E

**UNTESTABLE**

Runs only when P-C fails and P-A passes. Neither held, so b4 and b5 were not built or run.

## Anything the brief did not ask about

- Every cycle starts after the previous cycle's `ACTION_DISCONNECT`, when the dongle re-enumerates with 2 to 2.4 KB of stale USB data pending. That is a rig-side artifact of the cycle design and differs from the reporter's cold start. If the fault depends on a cold dongle, this cycle shape may hide it on B.
- A follow-up round should run cold-start cycles (unplug and replug the dongle, or a long idle, before each attempt) on B and C, since this dongle model (`Pixel 4`, not `smartBox`) does not fail on B with the warm cycle.
- Also worth one run: A with a re-attach between cycles, to tell whether A's 0 of 5 is the dongle's stale-data state or the missing retry.
- D-HP held at 100% battery through 6 cycles on OTG power (about 12 minutes), well above the 30% stop line.
- Gradle daemon left idle after the builds; stopped with `rig_cleanup.sh kill`.
