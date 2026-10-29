# session-reconnect, round 3 results

**Candidate C:** the stack, tree 2c4b06fd2c77cc8a25bcb88f775b5d51ec12d1ad (printed, equals the expected tree), local merge commit 4f10e12578b8 (the stack round's `arm-S`, reused)   **Export build E:** C plus `android:exported="true"` on `.main.SettingsActivity`, tree d77ff36f0bb34068c6c4b9c7e2fab280e2e642da, local commit d3bf1c34f5de
**Baseline:** none built; round 2's C (`b62d86c2`) is the baseline by its measured numbers (N2 reconnect 32.5 to 34.1 s)
**APK md5:** C ed9dc2c9b9f2f17f04a6a69305c53274 / E dc35a171299592e9fc62643b51bbfcf1
**Units:** D-HU Android 14 (API 34); D-POCO Android 15 (API 35), Gearhead 17.9.664004-release; D-MOTO Android 14 (API 34), Gearhead 17.9.664004-release
**Date:** 2026-10-09
**Evidence:** release `rig-evidence-pr-1047-session-reconnect`, asset `pr-1047-session-reconnect-round3-captures.zip`, sha256 49c621e0cb29e83626e0f9641486458dfe6f40c6063f128eead738e0c28c3982

## Setup notes

**What I read.** `TESTING-TEMPLATE.md` without sections 7b and 8; quirk files `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `D-POCO.md` and `D-MOTO.md`. I did not read `topics/bt.md` or `topics/wifi.md`.

**Pre-flight** (`rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_MOTO`, before any device was touched): PREFLIGHT OK.

| Role | adb | WiFi | BT | Screen | BT profiles |
|---|---|---|---|---|---|
| D_HU | ok | 1 | 1 | Awake | HFP:none A2DP:none |
| D_POCO | ok | 1 | 1 | Awake | HFP:none A2DP:none |
| D_MOTO | ok | 1 | 1 | Asleep | HFP:none A2DP:none |

**Build and identity.**
- C was reused. The stack round's local branch `arm-S` printed tree 2c4b06fd2c77cc8a25bcb88f775b5d51ec12d1ad at HEAD 4f10e12578b8, and its kept APK has md5 ed9dc2c9b9f2f17f04a6a69305c53274, so no C build was made. `git ls-remote` printed the five expected PR heads (dbe0004d, 7cc36de8, e6b24905, abd732c3, e4116c17) at the start.
- E was built from `arm-S` in a scratch worktree (`arm-E`, one commit, `git diff --stat` printed 1 file changed, 1 insertion). `GRADLE_OPTS=-Dorg.gradle.workers.max=2`; the host started at 63C, peaked at 90C during the build with `throttle_pkg=0` throughout. The standing `~/.gradle/gradle.properties` was in effect. The build ran warm and I did not count cache restores. The merge commits and `arm-E` stay local and were not pushed. No unit tests were run, as the addendum says.
- The auto-mode classifier first refused the E build as a security weakening (an exported activity). The operator allowed it, and I retried the identical commands.
- The brief's E check, `grep -c 'exported.*0xffffffff'`, printed 0 on E. This aapt2 (build-tools 37.0.0) prints `exported(0x01010010)=true`, not `0xffffffff`. The activity block of E reads `exported=true`; C has no `exported` attribute on it. So the brief's string does not match this aapt2 and E is correct.
- Session scripts, not `rig-executor`: I ran Stage A and Stage U as two session scripts (`stageA.sh`, `stageU.sh`) in the background under `flock /tmp/ohu-rig.lock`, in the foreground host session, and graded from the host. No agent touched a device. The brief's blocks for A0 (my own wrapper), N2, ND and U1 were copied verbatim from the brief; `lib1047r3.sh` is the brief's block verbatim.
- Libraries copied from round 2 as the brief says; `ohu_setkeys.py` came from the stack round's folder, `grep -c 'if v else \[\]'` printed 1.

**Deviations and faults, in order.**
1. First Stage A launch (00:57:22) aborted at its start check before touching a device. The brief's reader check `ps aux | grep -c "[l]ogcat"` matched my own launcher shells, whose command lines contain the word, and printed 3. I replaced it with `pgrep -fc "^adb .*logcat"`, which printed 0, and relaunched at 00:57:57. The brief's check will false-positive in any host session that spells the word in its command.
2. After N2, the script stalled for about 5 minutes. A thermal watcher started inside the `run_n2 | tee` pipeline inherited the pipe, so `tee` never saw end of file. I killed that watcher by pid at 01:06:19 and N2-DONE was written. For ND I armed a one-shot helper that killed the orphan after the `ND-C-end` marker (`orphan-kill.log`). No capture or grade was affected; the two captures had already stopped (`logcat_readers(N2-C-post)=0`).
3. `rig_cleanup.sh kill`, run before the pre-flight, also ended a `tail -F` monitor on another session's file (`build-speed-round3/session.log`) and an idle Gradle daemon. That monitor was not mine.
4. ND's `t_disc` printed `NA`. The brief's block looks up the `Disconnect action received.` line immediately after `send` returns, before the line is written (it appeared 21 ms later). The later counts in that cycle used the block's default of 8000 ms for the Disconnect time. I measured the real time from the capture: Save line 01:06:53.678, Disconnect line 01:07:01.860, so 8182 ms. The `pokes_after_disc` window therefore starts at 9000 ms; the only poke line after the Save is at 5107 ms, so no count changes.
5. The A0 screenshot was not saved (`adb pull -q` is not an option on this adb). A0 is graded on the two dumped targets.
6. N2R was not needed (2 graded cycles, 0 unanswered).
7. At the close, D-HU's `settings.xml` had 57 lines of difference from the backup (the app's own writes during the runs); I restored it as root from the backup and read it back (diff 0, owner `u0_a176:u0_a176`). D-POCO's was restored with `pocoput` (diff 0), and I reinstalled stock C on D-POCO with `adb install -r -d` (md5 ed9dc2c9, settings diff 0), so E does not stay on D-POCO. D-MOTO's radios were re-enabled and read back (Bluetooth ON, WiFi enabled). D-POCO went back to USB debugging mode. D-POCO did not serve port 5277 at U0 (`port5277=0`).

**Settings.** Each write was read back before launch. D-HU `AKEYS` as in the brief (`wifi-connection-mode=3`, `native-aa-wake-damage-verdict=0`, `video-profile-starvation-cap` deleted). `connection-modes` on D-HU as found: `wifi,self`. D-POCO `U1KEYS` as in the brief (`wifi-connection-mode=3`, `connection-modes=usb,wifi`, `use-libusb=false`). Rig audio keys as found on D-HU and not changed: `use-aac-audio=false`, `audio-latency-multiplier=8`, `audio-queue-capacity=20`, `separate-audio-streams=false`, `native-poke-all-paired=true`, `native-poke-bt-macs` holds one entry (D-POCO). D-POCO had none of the audio keys set. Installs preserved `settings.xml` on both units (diff 0 after each).

**Injected taps** (`taps.log`; `open_audio` is a deep link, `am` route on both units, destination id 2131296365):

| Run | Tap | Target | Coordinates |
|---|---|---|---|
| N2-C | 1 | toggle | 1356 391 |
| N2-C | 2 | save | 1271 47 |
| N2-C | 3 | toggle | 1356 391 |
| N2-C | 4 | save | 1271 47 |
| ND-C | 1 | toggle | 1356 391 |
| ND-C | 2 | save | 1271 47 |
| U1-E | 1 | toggle | 2248 713 |
| U1-E | 2 | save | 2085 87 |
| U1-E | 3 | toggle | 2248 713 |
| U1-E | 4 | save | 2085 87 |

Tap count per run: A0 0, N2 4, ND 2, U0 0, U1 4. No target failed to resolve, so no cycle was voided.

**Hand steps** (`hand-steps.log`): 01:10:34 H1 unplug D-POCO from the PC cable (done 01:10:54); 01:11:26 H1 plug the dongle into D-POCO's OTG port (the USB session formed by 01:11:54); 01:14:40 H1 unplug the dongle and put D-POCO back on its PC cable (done by 01:15:04). No USB permission dialog appeared (no H2 cue fired), and H4 was not needed.

**Windowing.** Every count is inside the run's own capture (one capture per run, started after `logcat -c`). Thermal per run: A0 62C, N2 65C, ND 57C, U0 59C, U1 56C, `throttle_pkg` delta 0 on each. Logcat readers read 0 before and after each run (`logcat-readers.log`). The phone capture is D-POCO's for Stage A and D-MOTO's for Stage U, unfiltered.

**Grader cross-checks.** N2: SSL lines at 00:58:56.738, 00:59:32.917, 01:00:26.564 against Save lines at 00:59:26.034 and 01:00:18.131 give 6883 and 8433 ms, equal to the block. ND: counted the poke, SSL and Disconnect lines directly (see ND). U1: counted the `held while the settings screen is open` lines per Save window by hand (5 and 5), equal to the block.

## R0 Gate

**PASS**

| APK | md5 | `SettingsRestart: route=` count | commit or tree | JVM count |
|---|---|---|---|---|
| C | ed9dc2c9b9f2f17f04a6a69305c53274 | 1 | commit 4f10e12578b8, tree 2c4b06fd2c77cc8a25bcb88f775b5d51ec12d1ad | 2995, 0 failures (our run, not rerun) |
| E | dc35a171299592e9fc62643b51bbfcf1 | 1 | commit d3bf1c34f5de, tree d77ff36f0bb34068c6c4b9c7e2fab280e2e642da | not run |

Other identity strings, C and E alike: `AapMessageReassembler` 13, `ConnectionAdmissionRejectedException` 2, `AutomaticReconnect` 10, `encrypted write failed or incomplete` 1. The md5s differ. `ACTION_QUERY_STATE` `commit` equals the HEAD of the arm that built each APK. Both installs used `adb install -r -d`, no uninstall.

## A0

**PASS**

| Unit | APK | destination id | route |
|---|---|---|---|
| D-HU | C | 2131296365 | am |
| D-POCO | E | 2131296365 | am |

Both units printed two coordinate pairs: D-HU toggle 1356 391, save 1369 47; D-POCO toggle 2248 713, save 2269 87.

## N2 (the point)

**FAIL**

Literal grade: `t_poke` is above the PASS ceiling of 5000 ms on both cycles (5130 and 5116 ms). Every other PASS condition holds, and none of the brief's FAIL triggers (`t_poke` above 29500 or NA, `t_ssl2` above 15000 with no failed poke, `ssl_count` 2 or more, `fallback_run` 1) fired. The cause is a designed delay, not the hold: the unit logs `NativeAA: the session just ended, so the first poke waits 4882ms for the phone's WiFi to settle.` at 00:59:26.254, 220 ms after the Save line. So the poke lands at about 5.1 s on a healthy build, and the 5000 ms ceiling sits below that. The change the brief meant to measure (the 30 s wake hold is gone) is met: the reconnect took 6.9 s and 8.4 s, against 32.5 to 34.1 s in round 2. I leave the ruling on the ceiling to the operator.

| run | cycle | t_retry | t_poke | t_ssl2 | pokefail_before_ssl2 | poked_before_ssl2 | hold | ssl_count | t_video2 | audio | fallback_run | fallback_skip | settings_restart | link_lost | raise | bye_phone | crit | graded |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| N2-C | 1 | 101 | 5130 | 6883 | 0 | 1 | 0 | 1 | 8442 | 1 | 0 | 1 | 1 | 0 | 1 | 1 | 4 | yes |
| N2-C | 2 | 89 | 5116 | 8433 | 0 | 1 | 0 | 1 | 8412 | 1 | 0 | 1 | 1 | 0 | 1 | 1 | 5 | yes |

Also on both cycles: `user_exit` 0, `keep` 0, `stop` 0, `fatal` 0, `phone_fatal` 0. The audio precondition held (`AudioDecoder.start: channel=` 1 in the first session). Preflight: `WifiLauncher: Initializing WiFi Mode: NATIVE` 1. No cycle was `poke unanswered`.

Decisive lines, cycle 1: `00:59:26.034 CommManager: audio settings changed; reconnecting the projection session`, `00:59:26.135 SettingsRestart: route=NATIVE retry=run`, `00:59:26.254 NativeAA: the session just ended, so the first poke waits 4882ms ...`, SSL at `00:59:32.917`.

The Native Save reconnects inside 15 s: yes.

## ND

**FAIL**

One graded cycle (`pokes_before_disc` 1, so the stop rule ended ND after the first attempt). `ssl_after_disc` is 1, which the brief names as a FAIL. No new poke started after the Disconnect, but the session formed anyway, from the poke already in flight, so the Disconnect did not stop the wake.

| cycle | t_disc | pokes_before_disc | stand_down | pokes_after_disc | skip_line | ssl_after_disc | fallback_run | fallback_skip | bt12 | re_arm | t_rearm | bye_phone | graded |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ND-1 | 8182 (measured; the block printed NA, see Setup notes) | 1 | 1 | 0 | 0 | 1 | 0 | 1 | on | 1 | 4644 | 1 | yes |

Timeline, from the capture (Save line at 01:06:53.678):
- `01:06:58.785` `NativeAA: Attempting active poke to device: ...` then `01:06:58.794 NativeAA: Calling socket.connect() ... via HFP-AG`. D-POCO's Bluetooth was off from the Save (the block disables it on the Save line).
- `01:07:01.840` `ACTION_DISCONNECT` reaches the service; `01:07:01.860 Disconnect action received.`; `01:07:01.861 NativeAA: deliberate session end; reopening listeners without an automatic wake.` That is `stand_down` 1.
- D-POCO's Bluetooth was switched back on at Save + 12 s (about 01:07:05.7). The poke that was already inside `socket.connect()` then completed: `01:07:08.817 NativeAA: Successfully poked ... Holding 15000ms...`.
- `01:07:09.005 NativeAA: Connection accepted from ...`, the type 4, 1, 2 and 3 exchange, `01:07:10.044 WirelessServer: Incoming connection detected`, `01:07:10.224 SSL handshake complete`. That is 8.4 s after the Disconnect and 16.5 s after the Save.
- `01:07:23.764 SettingsRestart: route=NATIVE fallback=skip_superseded`, so the fallback found the session and did nothing.

The brief anticipated that an in-flight poke "finishes by itself" and does not count as a new poke. It did not anticipate that the finished poke would carry the phone all the way to a session with the listeners still open. So the brief's own test (Bluetooth back on 4 s after the Disconnect, inside a poke's 15 s connect) is built so that an in-flight poke can succeed. Whether the Disconnect should cancel an in-flight poke or refuse the handshake that follows it is a code question; `stand_down` held for new pokes only.

The brief's do-nothing shape ("`pokes_after_disc` 1 or more within about 15 s of the Bluetooth coming back, and often an SSL") does not match this either: `pokes_after_disc` is 0. This build stood down the new pokes and still formed a session, so the lever changed one thing and not the other. I did not make a second attempt: the stop rule ends ND at the first graded cycle.

Phone: `bye_phone` 1, `GAL was deliberately disconnected, so do not restart` printed 2 times in the capture.

## U0

**PASS**

Wireless adb answered; D-POCO's port table shows no 5277 listener (`port5277=0`); A0 passed on D-POCO with route `am`; with `U1KEYS`, a launch and the dongle plug formed a USB session (`usb_session=1`), and `stopping the wireless stack for the duration of it` printed 1 time after it. D-MOTO Bluetooth read enabled / ON.

## U1

**FAIL**

Both cycles hit the held path. After the Save, the dongle detached and came back in normal mode (PID 4EE1, not accessory mode); that attach scheduled a 2000 ms auto-connect check, which the open settings screen held, 5 times per cycle. No session formed until the screen closed, 46 to 51 s after the Save.

| cycle | retry | detach | reattach | held | tries_before_ssl | t_ssl2 | ssl_before_close | quiesce | arm_in_window | pokes_in_window | fallback_run | fallback_other | raise_refused | usb_retry_line | crit |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| U1-1 | 1 | 4 | 0 | 5 | 4 | 51273 | 0 | 1 | 0 | 0 | 0 | 1 | 0 | 1 | 7 |
| U1-2 | 1 | 4 | 0 | 5 | 3 | 46531 | 0 | 1 | 0 | 0 | 0 | 1 | 0 | 1 | 7 |

Also: `settings_restart` 1 and `fatal` 0 on both cycles; D-MOTO `phone_fatal` 0. Preflight: `WifiLauncher: Initializing WiFi Mode: NATIVE` 0 and `stopping the wireless stack ...` 1, so the run's USB path was proven.

FAIL triggers hit, both cycles: `held` 1 or more, and `ssl_before_close` 0 with the dongle back on the bus (the `reattach` column counts only the accessory-mode line `USB accessory device attached, connecting.`, which did not print inside the 35 s window; the dongle did return twice in that window as `USB_DEVICE_ATTACHED`, in normal mode). Held passes: `arm_in_window` 0, `pokes_in_window` 0 (wireless never armed under the plugged dongle), `fallback_run` 0 (the fallback printed `fallback=skip_superseded` once at Save + 30 s).

Timeline, cycle 1 (Save at 01:12:31.012):
- `01:12:31.050 SettingsRestart: route=USB retry=run`; `01:12:31.058 Found device already in accessory mode`; `01:12:31.068 session state connected`.
- `01:12:32.705` `USB_DEVICE_DETACHED`; `01:12:32.718 session state disconnected (link_lost)`; `01:12:32.733 USB disconnect. Scheduling reconnect check in 3000ms...`.
- `01:12:35.411 UsbLauncherListener.onUsbAttach | Normal USB device attached: ... PID 4EE1. Will check auto-connect in 2000ms...`, then `01:12:35.443`, `35.738`, `37.426` `UsbLauncher: USB auto-connect held while the settings screen is open; checking again when it closes.`, and again at `01:12:54.654`, `54.637` after the second detach and attach.
- `01:13:09.514` the screen closes (`U1-E-closed`); `01:13:10.048 Single USB auto-connect: connecting to ...`; SSL at `01:13:22.285`.

Cycle 2 has the same shape (Save `01:13:43.705`, held lines at `01:13:48.383`, `48.466`, `50.367`, `01:14:07.614`, `09.599`, screen closed `01:14:22.227`, SSL `01:14:30.236`).

So the Save's retry did not carry through the dongle's return. The attach that the code path meets in this rig is the normal-mode one, and it is held like any plain attach. That is the shape the brief gives as "what a PASS would look like if the change did nothing" for `held`, but with `arm_in_window` 0 and `fallback_run` 0, which round 2's old head did not show.

## Verdict table

| Run | C | E | Notes |
|---|---|---|---|
| R0 | PASS | PASS | brief's `0xffffffff` string does not match this aapt2; E reads `exported=true` |
| A0 | PASS | PASS | |
| N2 | FAIL | | reconnect 6.9 and 8.4 s; `t_poke` 5130 and 5116 ms against a 5000 ms ceiling; designed 4882 ms settle delay |
| ND | FAIL | | `ssl_after_disc` 1 from the poke already in flight at the Disconnect; no new poke |
| U0 | | PASS | |
| U1 | | FAIL | `held` 5 and `ssl_before_close` 0 on both cycles; dongle returns in normal mode |

## Anything the brief did not ask about

- **N2 `t_poke` ceiling.** The brief's 5000 ms is 118 ms under what the build does by design (a 4882 ms settle delay after the session ends, then a poke about 230 ms later). Either the ceiling should read about 6000 ms or the delay is the thing under review. Round 2's old head had `t_poke` near 30000.
- **ND test design.** The brief switches D-POCO's Bluetooth back on 4 s after the Disconnect, while a poke started at Save + 5 s is still blocked in `socket.connect()` (about 15 s). That poke completes once the radio returns, so the design guarantees an in-flight success. A follow-up that keeps the radio off until the in-flight poke has timed out would separate "the Disconnect stopped new pokes" (true here) from "the Disconnect stopped the session" (false here).
- **U1 first retry.** The Save's immediate USB retry connected to a device already in accessory mode, and the session was lost 1.7 s later when the dongle detached on its own (`Handshake: Version request/response failed after 1 attempt(s)`). That matches the dongle quirk (it detaches about 1.6 s after the session ends), so the first retry cannot win on this rig.
- **Phone `Critical error` lines** (reported, not graded): N2 4 and 5, ND 14 for the whole run, U1 7 and 7. `FATAL EXCEPTION`: 0 on every unit and phone capture.
- **Thermal.** The E build reached 90C with the standing Gradle setting, `throttle_pkg` 0. The unit runs themselves stayed under 66C.
- **Not run, as the brief says:** the Helper fix, the Self Mode deadline, N1, U2, Headunit Server, N3 and D-SAM.
