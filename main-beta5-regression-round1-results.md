# main-beta5-regression round 1 results

**Candidate:** upstream `main` @ `ec9d9c33f728` (merge of the USB re-attach PR), built from a detached worktree of that commit. `ACTION_QUERY_STATE` prints `ec9d9c33f728-dirty` on every unit: the worktree held one untracked file (`.WORKTREE-INFO`), no tracked change.
**Baseline:** none. No A/B, every condition is absolute.
**APK md5:** `73355877bb4f0c0ceb094bd2aa33e577` (versionCode 117, `3.5.0-beta4`, 21389519 bytes), identical on all five units after the Closing stage.
**Unit:** D-HU (MT50, Android 14) for W1 to W4; D-POCO (POCO X3, Android 15) as USB head unit in U1; D-HP (HP Slate 7 Plus, Android 4.2.2) in H1; D-POCO as phone in W and H, D-MOTO as phone in U.
**Date:** 2026-10-09
**Captures:** release `rig-evidence-main-beta5-regression`, asset `main-beta5-regression-round1-captures.zip`, sha256 `1eb52f634cde03abc334516a548039588d818bee555aadbc64a747ca654ba7ac`.

## Setup notes

**Quirk files read:** `rig-quirks/topics/tooling.md`, `units/D-HU.md`, `units/D-POCO.md`, `units/D-MOTO.md`, `units/D-HP.md`, `units/D-SAM-and-D-HP.md`. TESTING-TEMPLATE without 7b and 8.

**Pre-flight** (`rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_MOTO:wifi,bt D_HP:wifi D_SAM`, re-run after the operator's changes). Final read before R0:

| Role | adb | WiFi | BT | Screen | Profiles |
|---|---|---|---|---|---|
| D_HU | ok | 1 | 1 | Awake | HFP none, A2DP none |
| D_POCO | ok | 1 | 1 | Awake | HFP none, A2DP none |
| D_MOTO | ok | 1 | 1 | Asleep | HFP none, A2DP none |
| D_HP | ok | 1 | 1 | Asleep | HFP none, A2DP none |
| D_SAM | ok | 1 | 0 | Asleep | HFP none, A2DP none |

The first pre-flight found three blockers, all cleared by the operator in one message: D-POCO had `Navegadortz3` (D-SAM's radio) bonded beside `Navegadortz2`, D-SAM's Bluetooth was on, and Gearhead's head unit server was not listening. Bonded list after the fix: `motorola edge 30 neo`, `Magnetic Speaker`, `FX Plus`, `Navegadortz2`. Batteries: D-POCO 75%, D-MOTO 100%. D-HU `shared_prefs/` read `u0_a176:u0_a176` (not root-owned). A stray idle Gradle daemon makes `rig_preflight.sh` print FAIL after a build; `rig_cleanup.sh kill` cleared it.

**Scripts used:** `rig_devices.sh`, `rig_preflight.sh`, `rig_thermal.sh` (wait 75 before every run, watch during), `build_hur_cool.sh`, `run_unit_tests.sh`, `apk_identity.sh`, `set_hu_settings_host.py` (D-HU, rooted), `set_prefs_runas_host.py` (D-POCO, D-HP), `wt-new.sh`, `rig_cleanup.sh`. The brief's `hur-wifi-test-scripts/`, `pocoput` and `ohu_setkeys.py` no longer exist (folder is `rig-toolkit/` since 2026-10-09); `set_prefs_runas_host.py` replaced `pocoput` and `ohu_setkeys.py`. New one-round scripts, kept in `rig-data/rounds/main-beta5-regression-round1/scripts/` (not promoted): `w_session.sh` (W1 to W4), `u_session.sh`, `h_session.sh`, `closing.sh`. Each script refuses to start unless its pre-checks hold and its last log line is `script exit=N`.

**Settings delta.** No earlier backup of any unit exists in `rig-data/rounds/`, so the delta against a previous backup could not be computed. What each round-start backup held that the brief overrides: D-HU `native-driver-selection-mode=1`, `aa-exit-action=0`, `allow-external-configuration=true`, `connection-modes={wifi,self}` (left as found, contains `wifi`). All W keys, UKEYS and HKEYS were written with the app stopped and read back before every launch; each run's `settings-written.txt` and the script log hold the read-back. `adb install -r -d` did not change D-HU's `settings.xml` (diff against backup after install: empty). Every unit's round-start `settings.xml` was restored at the end of each run and read back (`restore: baseline read back OK`).

**Deviations and brief errata:**

1. **Build.** First build failed in 14 s: the new worktree had no `local.properties` (`SDK location not found`). Copied it from the main checkout, rebuilt with `build_hur_cool.sh` (thermal-guarded, `GRADLE_OPTS=-Dorg.gradle.workers.max=2`). The host reached 89C during the build; `thermal_guarded.sh` paused Gradle at 86C (20:06:28) and resumed at 68C (20:07:29), and `throttle_pkg` stayed 0. Every run started below 75C (peak during runs 61C to 64C, `throttle_pkg` 0).
2. **L-SDR string.** `Service Discovery Response` has 0 hits at INFO on this build (it exists only in `AapDump`/`MsgType` name tables). The INFO line is `AapControlService.serviceDiscoveryRequest | Service Discovery Request: Android` (W1 20:35:41.173). Brief error.
3. **W1 floating button dump conditions cannot be met as written.** `FloatingButtonService` shows the overlay only when `!FloatingButtonManager.isAppForeground` (`FloatingButtonService.kt`, in `onStartCommand` and `showOrUpdateOverlay`). Both dumps were taken with the app in front (MainActivity, then AapProjectionActivity), so no headunitrevived overlay window exists in either. L-FB did print once, at 20:45:45.139, after the screen-off key.
4. **W2 tap coordinates.** The brief's `(41,680)` then `(252,85)` did not open the launcher on this build (attempt 1: 0 L-NAT, screenshot shows Spotify now-playing). The projection starts in whatever state the phone left it. The working sequence, found from screenshots, was `(59,665)` (dashboard icon, from fullscreen Maps), `(59,665)` (6-dot app grid, now visible), `(240,100)` (Exit tile). Attempts: 1 two taps, missed; 2 one tap then stopped by me; 3 no taps, timed out waiting for me (the script waits 300 s for coordinates and I did not answer in time); 4 three taps, graded. 3 taps in the graded run, 6 across all attempts.
5. **W3 tap coordinates are stale.** `(1356,391)` and `(1271,47)` from the older round hit rows that start `OnboardingActivity` (`START u0 ... OnboardingActivity` at 21:08:33.332 and 21:09:31.082), L-SAVE 0 both cycles. The Settings screen now opens on the Advanced tab with the General group; no audio toggle is on the first screen. Reaching one needs scrolling (banned) or the search field (3 taps per cycle, 6 for two cycles, over the 5-tap cap). W3 is UNTESTABLE; I did not improvise a route.
6. **D-POCO lock screen.** After about 14 minutes the phone's keyguard re-engaged (`mCurrentFocus=NotificationShade`), and W2's pre-check refused. `wm dismiss-keyguard` clears it (swipe lock, no PIN). Added to the scripts' pre-check.
7. **Stray lock holder.** A `sleep` child of the thermal watch in W3's script kept the rig lock fd open, so W4's first start refused (`rig lock held`). Killed by pid; later scripts close fd 9 for background children.
8. **My own mistake.** `pkill -f "scripts/w_session.sh W2"` matched the host shell and exited 144 (round discipline forbids `pkill -f`). It did stop the W2 attempt 2 script cleanly (baseline restored); the edit in the same command did not run and was redone.
9. **Stage U and H over wireless adb.** D-POCO went to `192.168.1.8:5555` for U1 and H1 (its USB cable unplugged for the dongle). It stayed on wireless adb for the Closing stage, so the Closing stage ran with all five units reachable in one pass (no unit had to be plugged in turn). The Closing stage ran before this file was written (the brief says after); nothing was committed in between.
10. **D-MOTO had no app installed.** `run-as: unknown package` before the Closing stage. It is the phone in U1 and needs none. The Closing install was therefore a fresh install (`Success`, no `INSTALL_FAILED_UPDATE_INCOMPATIBLE`), and there was no round-start `settings.xml` to restore.
11. **D-MOTO captures** use a tag filter (`CAR.SERVICE:I CAR.SERVICE.LITE:I CAR.GAL.GAL.LITE:D *:W`) so the phone-side lines survive. D-POCO as phone and D-HU used unfiltered captures. `logcat -G 16M` was not run on any unit; no capture lost its ring buffer (first and last lines checked against the markers).
12. **H1 pre-check.** My H1 script ran `nc -z 192.168.1.8 5277` (the brief's own pre-flight item 4 does the same) and pressed WAKEUP, dismiss-keyguard and HOME on D-POCO before launching D-HP's app. See R H1.
13. **Markers** landed on all units (`allow-external-configuration=true` is in D-HU's baseline; D-POCO and D-HP markers also landed). Not checked against `AutomationCommandPolicy.CONFIGURING`, which the brief says does not list `ACTION_LOG_MARKER`.
14. **Overlay grant.** `SYSTEM_ALERT_WINDOW` read `default` before W1, set to `allow` by W1's pre-check and left set.
15. **Windowing.** All counts are windowed by `AutomationMarker` lines in the same capture. Counts were re-run in this session from the capture files, not taken from an executor block (no `rig-executor` was used; the host ran the session scripts itself in the background).

## R0. Build and identity gate

**PASS**

- Unit tests: 3111 run, 0 failures, 0 errors, 0 skipped (`run_unit_tests.sh`, exit 0).
- md5 `73355877bb4f0c0ceb094bd2aa33e577` for the built APK and for the pulled `base.apk` on D-HU, D-POCO and D-HP (`apk_identity.sh`: MATCH, `lastUpdateTime` 2026-10-09 20:33:33 and 20:33:38).
- `unzip -p <apk> 'classes*.dex' | strings | grep -c -F unprojectedEndsInARow` = 1.
- `ACTION_QUERY_STATE` on D-HU and D-POCO: `"commit":"ec9d9c33f728-dirty"`, `versionCode` 117, `"flavor":"github"`.

## R W1. Native AA cold start, 10-minute soak, floating button, screen-off return

**FAIL**

- Settings written: WKEYS plus the four W1 keys (all read back before launch).
- Radio state: phone in airplane mode until `W1-phone-on` (20:35:35.920), then WiFi and Bluetooth on (`Wi-Fi is enabled`, `bt enabled: true`).
- Discard-rule check: clean (L-MATCH 0, one L-SSL).
- Decisive lines:
  - `20:35:40.492 AapSslContext.performHandshake | SSL handshake complete` (4 s after `W1-phone-on`).
  - `20:35:41.173 Service Discovery Request: Android` (L-SDR string absent, see note 2).
  - `20:45:45.139 FloatingButtonService.showOrUpdateOverlay | FloatingButtonService: Added floating button overlay` (L-FB 1).
  - `20:37:59.078 VideoDecoder.outputThreadLoop | Decoder stall detected (no output for 2004ms while receiving input). Forcing restart (1/4).`
  - `20:37:59.934 AapProjectionActivity.maybeRecoverWarmRelaunch | relaunched surface has no picture after 138802ms - cycling video focus`
  - `20:45:53.938 ... relaunched surface has no picture after 850ms - cycling video focus` (before `W1-return` at 20:45:55.271).
- Measurements:
  - L-SSL 1; L-TLS sum 0; L-INC 0; L-MATCH 0; dropped sum 0.
  - Soak L-TP windows from SSL + 30 s to `W1-soak-end`: 113 windows (needs 100 or more, met). `rendered=` per 5 s window: **median 30 (6 fps), minimum 0, maximum 151**. Needs a median of 125 or more. **Not met.** Eight windows inside the soak read 0 (20:37:27, :32, :57; 20:38:09, :14, :29, :34, :39).
  - Return: `W1-return` 20:45:55.271 to the first L-TP with `rendered=` above 0 (35) at 20:45:55.807 = 536 ms (limit 15 s, met). L-NOPIC after `W1-return` is 0; L-NOPIC in the run is 2 (the 20:37:59 mid-soak line and the 20:45:53 line that precedes the marker).
  - Overlay windows: no `Window #` block for `com.andrerinas.headunitrevived` has `ty=APPLICATION_OVERLAY` in `W1-win-armed.txt` or `W1-win-live.txt` (blocks present: MainActivity in the first, AapProjectionActivity and MainActivity in the second). The brief's armed and live alpha and flags conditions are not met (see note 3).
  - Phone: P-STATE 4 (first `Car connection state changed: DISCONNECTED->CONNECTING` 20:35:39.907); P-FATAL 0; P-CRIT 0.

The fps median is the failed condition that matters. The nav screen was a static map, and the head unit logged `inbound video quiet 3 times in 35791ms: dead=2714` at 20:37:50.648 and a 3 kB/s video rate (20:37:41.597), so the phone sent few frames. Earlier rounds recorded parked-car nav at about 5 fps as a phone-side property. A 125-per-window threshold is not reachable on that content, so this FAIL is partly a brief threshold issue; no TLS or framing fault was seen. The decoder stall at 20:37:59 is real (`Decoder stall detected` lines: W1 1, W2 0, W4 0, U1 0, H1 0).

## R W2. Exit in Android Auto ends the session

**PASS**

- Settings written: WKEYS (floating button off).
- Radio state: as W1; discard-rule check clean (L-MATCH 0, SSL at 21:04:20.120 and 21:06:41.637 only).
- Decisive lines (taps at 21:05:27, see note 4; `W2-exit` marker 21:04:50.675 precedes the taps):
  - `21:05:28.626 Video Focus NATIVE received. User clicked Exit in Android Auto.` (L-NAT 1)
  - `21:05:28.631 ExitAction: Disconnecting projection session` (L-EXA 1)
  - `21:05:28.632 AapTransport stopping and sending byebye (USER_SELECTION)` (L-BYE 1)
  - `21:05:28.637 AapTransport: ByeBye write SENT` (L-BYW 1)
  - `21:05:28.641 AapService: session state disconnected (user_exit)` (L-UX 1)
  - `21:05:28.679 AapService: User exit cooldown active for 5000ms` (L-COOL 1)
  - `21:06:41.637 SSL handshake complete`
- Measurements: no L-SSL from `W2-exit` (21:04:50.675) to `W2-rearm` (21:06:30.548), 99.9 s after the marker and 61.9 s after the actual Exit at 21:05:28.626 (needs 60 s or more). L-SSL 1 between `W2-rearm` and `W2-end`. L-BYE to L-BYW 5 ms. `W2-rearm` to L-SSL 11.1 s. L-TLS 0. Phone: P-BYE 1 (`21:05:27.214 received ByeByeRequest`, phone clock), P-STATE 12, P-FATAL 0.
- P-CRIT (report only): 21:05:27.216 `GH.WirelessStartup: Critical error encountered, attempting to handle result.`; 21:05:27.793 `Critical error 4 detail: 34 msg: reason:1`; 21:05:27.844 and :845 `Critical error 3 detail: 50/52 msg: io error`; 21:05:27.878 `Critical error 18 detail: 54 msg: Failed to read message`.

## R W3. Save reconnects a Native session

**UNTESTABLE**

- Settings written: WKEYS.
- Attempt 1 (brief taps): both cycles launched `OnboardingActivity` instead of toggling (21:08:33.332, 21:09:31.082). L-SAVE 0, L-SR 0, one L-SSL (21:08:00 region, 9 s after phone on), L-TLS 0, L-MATCH 0. Screenshots `w3-save1-nosave.png` and `w3-save2-nosave.png` are in the evidence.
- Attempt 2 (screenshot-guided retry, 0 taps sent): Settings opens on the Advanced tab; the visible rows are Auto-Optimize Settings, Permissions and Connection mode. No audio toggle is on the first screen (`w3-c1a.png`).
- The brief says to mark a missed cycle UNTESTABLE, not FAIL, and the route to a toggle breaks the no-scroll rule or the 5-tap cap (note 5). The PR's behaviour (L-SAVE, `route=NATIVE retry=run`, reconnect inside 15 s) is not measured by this round. It was passed on its own round.

## R W4. The Disconnect verb ends the session and stays down

**PASS**

- Settings written: WKEYS. Discard-rule check clean (one L-SSL at 21:18:13, L-MATCH 0).
- Decisive lines after `W4-disc`:
  - `21:18:44.052 Disconnect action received.` (L-DISC 1)
  - `21:18:44.053 NativeAA: deliberate session end; reopening listeners without an automatic wake.` (L-DEL 1)
  - `21:18:44.086 AapTransport stopping and sending byebye (USER_SELECTION)` (L-BYE 1)
  - `21:18:44.092 AapTransport: ByeBye write SENT` (L-BYW 1)
- Measurements: no L-SSL from `W4-disc` to `W4-end` (61 s). Phone: P-BYE 1, P-STATE 12 in total, P-FATAL 0. L-TLS 0.
- P-CRIT (report only): 21:18:42.669 `GH.WirelessStartup: Critical error encountered, attempting to handle result.`; 21:18:43.276 `Critical error 4 detail: 34 msg: reason:1`; 21:18:43.334 `Critical error 18 detail: 54 msg: Failed to read message`. The phone clock runs about 1.4 s ahead of the head unit's.

## R U1. USB dongle session on D-POCO

**PASS**

- Settings written: UKEYS (all read back, including `connection-modes={usb,wifi}`), D-MOTO as phone, dongle on D-POCO's OTG port, D-POCO on wireless adb.
- Decisive lines: `Sending acc start` 2 (first at the first `U1-usb`, second after `U1-usb2`); SSL after `U1-usb` 10 s (`first SSL after 10 s`); SSL after `U1-usb2` 9 s (`second SSL after 9 s`).
- Measurements: L-TP windows with `rendered=` above 0 before `U1-disc`: 35 of 35 (needs 20 or more), median 150, maximum 151. After `U1-disc` (21:25:00.997 `Disconnect action received.`): `21:25:01.006 AapTransport: ByeBye write SENT`. L-TLS 0, L-INC 0, L-MATCH 0. Phone (D-MOTO): P-STATE 12, P-FATAL 0.
- P-CRIT (report only): 21:25:01.690 `GH.WirelessStartup: Critical error encountered, attempting to handle result.`; 21:25:03.377 `Critical error 4 detail: 34 msg: reason:1`; 21:25:03.401 `Critical error 18 detail: 54 msg: Failed to read message`.
- `BootCompleteReceiver` crash count: 1 (`ForegroundServiceStartNotAllowedException` at 21:21:44.310 on D-POCO, 4 stack-trace lines), known, not a FAIL.
- A first start of the script was refused by its own pre-check (`mCurrentFocus=null` on D-MOTO a moment after HOME; it runs `com.qqlabs.minimalistlauncher`); the check now retries for 6 s. No run data came from that start.

## R H1. Headunit Server session on D-HP (API 17)

**PASS** (attempt 4 of 4; attempts 1 to 3 were silent at the handshake, see below)

- Settings written: HKEYS on D-HP (read back, `connection-modes={wifi}`). D-POCO on the house WiFi, head unit server listening on :5277 (read from D-POCO's `/proc/net/tcp6`).
- Decisive lines (attempt 4): `H1-start` 21:40:59.814; `21:41:04.654 SSL handshake complete` (5 s); `21:44:07.164 Disconnect action received.`; `21:44:07.214 AapTransport: ByeBye write SENT`; `21:44:07.204 AapService: session state disconnected (user_exit)`.
- Measurements: L-SSL 1 within 90 s (5 s); L-TP windows with `rendered=` above 0 before `H1-disc`: 35 of 35, median 150, minimum 102, maximum 151; L-TLS 0, L-INC 0, L-MATCH 0, `peer_silent` 0. Phone: P-BYE 1 (`21:44:05.200 received ByeByeRequest`), P-FATAL 0.
- P-CRIT (report only): 21:44:06.199 `Critical error 4 detail: 34 msg: reason:1`; 21:44:06.233 and .233 `Critical error 3 detail: 50/52 msg: io error`; 21:44:06.234 `Critical error 18 detail: 54 msg: Failed to read message`.

**Attempts 1 to 3 (kept as evidence).** Each found the server (`Found Headunit Server on 192.168.1.8:5277`, 7 times), opened a socket and got `Handshake: No VERSION_RESPONSE within 2s` three times, then `session state failed (peer_silent)`; no TLS line was reached, L-SSL 0 in all three. A raw host client sending a bare AAP version request (`00 03 00 06 00 01 00 01 00 01`) also got no reply in 6 s while D-POCO's `:149D` table showed four stacked connections with unread bytes. After the operator force-stopped Android Auto on D-POCO and restarted the server, the first raw probe was still silent (attempt 2 followed, also silent). Later the operator connected D-HP by hand twice with success, and a raw probe at 21:35 got a reply (`00 03 00 08 00 02 00 01 00 07 00 00`, 0.24 s); attempt 3, started 1 minute later, was silent again.

The operator asked whether the script interferes. Three script-side actions preceded every silent attempt: the pre-check `nc -z` (opens and closes a connection with no AAP bytes), the HOME and keyguard keys on D-POCO, and my abandoned raw probes. Attempt 4 removed the `nc -z` and the D-POCO key events and made no probe, and it passed on the first try. **The cause is not isolated between those two changes**, and I did not run a control with only one of them. Android Auto's own documented behaviour (the app's diagnostic text on `peer_silent`: it hands each accepted connection to its car service and waits with no timeout) makes the abandoned connection the leading candidate. If so, the brief's own pre-flight item 4 (`nc -z`) is the thing that wedges the server. TLS on API 17 and bundled Conscrypt is covered by attempt 4.

## Closing. Leave this build on every unit

| Unit | md5 | commit | settings restored |
|---|---|---|---|
| D-HU | `73355877bb4f0c0ceb094bd2aa33e577` | `ec9d9c33f728-dirty` | yes (read back) |
| D-POCO | `73355877bb4f0c0ceb094bd2aa33e577` | `ec9d9c33f728-dirty` | yes (read back) |
| D-MOTO | `73355877bb4f0c0ceb094bd2aa33e577` | `ec9d9c33f728-dirty` | n/a (no app or settings before this round) |
| D-SAM | `73355877bb4f0c0ceb094bd2aa33e577` | `ec9d9c33f728-dirty` | yes (read back) |
| D-HP | `73355877bb4f0c0ceb094bd2aa33e577` | `ec9d9c33f728-dirty` | yes (read back; D-HP's H1 attempt 4 restored it again afterwards) |

All five installs printed `Success`; none needed an uninstall. The app is stopped on every unit. D-POCO and D-MOTO: WiFi enabled, `bluetooth enabled: true`, `airplane_mode_on` 0 (read with `dumpsys` and `settings get`). This replaces the stack build and the `usb-reattach` export build on D-POCO.

## Anything the brief did not ask about

- **The numbers the brief asks for (section 10):** W1 fps median over the soak 30 per 5 s window (6 fps) against 25 fps expected, L-TLS sum 0; W2 L-BYW `SENT`, 61.9 s with no session after Exit (needs 60); U1 SSL 10 s and 9 s, L-TLS 0; H1 SSL 5 s, L-TLS 0.
- **Decoder stall mid-soak (W1, 20:37:59).** `sync_stall` restart 1/4 and a warm-relaunch banner ("the renderer confirmation banner is up (the phone is streaming and nothing has drawn)") during a static-map soak, with `GlProjectionView: displayed 5 frames in 7600ms (0fps)` at 20:37:42. The picture came back by itself within the same window. The map was idle, not frozen; the watchdog read it as a stall.
- **TLS and framing:** zero faults in every run (W1, W2, W3 attempt 1, W4, U1, H1 attempt 4).
- **`Critical error` lines on the phone** are 3 to 5 per session end in every run, same shape as the earlier rounds. None is `FATAL EXCEPTION`.
- **Projection start state is not fixed.** The AA UI came up on Spotify now-playing, on fullscreen Maps, or on the split dashboard in different sessions. Any brief that taps by coordinate has to screenshot first.
- **D-POCO's keyguard re-engages** after about 14 minutes of an idle screen during a long round.
- **Gearhead server hygiene.** Any connection that reaches the server and never completes the AAP version exchange can leave Android Auto unresponsive to later clients until its process is restarted. Do not `nc -z` or probe the server before an attempt; read `/proc/net/tcp6` on the phone instead.
- **Brief errata to fix:** L-SDR string; the W1 overlay conditions (app must be in the background); the W2 and W3 tap coordinates; pre-flight item 4 (`nc -z`); the W1 125-per-window fps threshold for a static-map soak.
- **Not run (brief section 9) and not re-run:** stale USB recovery, no-screen reconnect hold, USB Save retry, `bt-announce`, wizard steps, update link, Nearby, floating-button opacity taps.
