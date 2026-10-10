# aa-178-protocol-levers, round 1 results

**Candidate:** `feat/aa-178-protocol-levers` @ `dd5b2a823df9b25c7ec1fae3cab4c13a0987ae4e` (tree `ca8a5828f34f22f2e9b7e9c85b369abad1252d82`)   **Baseline:** none built (`main` `ec9d9c33`)
**APK md5:** `5999e5b3927c4940be3229d96c4e99b2` (candidate, build and pulled copy identical)
**Unit:** D-HU (UNISOC MT50, Android 14, 1440x720 panel), D-POCO (POCO X3 NFC, Gearhead `17.9.664004-release`), D-MOTO (Bluetooth off, then back on)
**Date:** 2026-10-10

## Round verdicts

| Run | Verdict |
|---|---|
| R0 build gate, R5 | PASS (3120 tests, 0 failures; R5a count 0, control 2) |
| R2 positive control | FAIL by the letter (conditions 8 and 9), substantive control holds, see R2 |
| R1 point of the round | PASS |
| R4 touch | INCONCLUSIVE |
| R3 head unit server on 2.4 GHz AP | INCONCLUSIVE (addendum run; the first attempt was UNTESTABLE, see R3) |

## Setup notes

- **Quirk files read:** `rig-quirks/topics/tooling.md`, `wifi.md`, `bt.md`, `gearhead.md`, `projection.md`, `rig-quirks/units/D-HU.md`, `D-POCO.md`, `D-MOTO.md`; the template without §7b and §8.
- **Scripts used:** `wt-new.sh` (worktree), `build_hur_cool.sh`, `run_unit_tests.sh`, `rig_preflight.sh`, `rig_cleanup.sh`, `rig_thermal.sh`. New, round-only, in `rig-data/rounds/aa-178-protocol-levers-round1/scripts/`: `lib.sh` (the brief's section 5 helpers plus `hu_kv` from the forget-car brief, and a `th_gate` that uses `rig_thermal.sh wait 75` and `watch`), `run_r.sh` (one run's session script, with the pre-checks), `grade_r2.sh`, `grade_taps.sh`. Nothing added to `rig-toolkit/`.
- **Build:** worktree `worktrees/aa-178-protocol-levers__candidate__dd5b2a82__20261010`, `ls-remote` printed the brief's SHA, tree matched. `thermal_guarded` paused Gradle once (87C at 12:09:07, resumed 12:13:13 at 68C). Unit tests: 3120 run, 0 failures, 0 errors, 0 skipped, counted from the JUnit XML. Installed with `adb install -r` on D-HU only.
- **Identity gate:** `ACTION_QUERY_STATE` `commit":"dd5b2a823df9-dirty"`; `WppChannelListPolicy` 3, `VideoFallbackPolicy` 8 in the pulled APK's dex. R5a: `Ask for a longer link timeout` 0, control `Lower video on a 2.4 GHz link` 2.
- **Pre-flight.** First `rig_preflight.sh` FAILED on strays: three orphaned `tail -f` monitors from another thread's round (killed by pid) and an idle Gradle daemon (the build's own). After the build and `rig_cleanup.sh kill` it printed `PREFLIGHT OK`. Table after the operator step:

  | Role | adb | WiFi | BT | Screen | Profiles |
  |---|---|---|---|---|---|
  | D_HU | ok | 1 | 1 | Awake | HFP none, A2DP none |
  | D_POCO | ok | 1 | 1 | Awake | HFP `..:33:59`, A2DP up |
  | D_MOTO | ok | 1 | 1 | Awake | none |

- **P3, a blocker cleared by the operator.** D-POCO was bonded to `Navegadortz3` beside `Navegadortz2`, which the brief forbids. The operator unpaired it; the list afterwards was `motorola edge 30 neo`, `Magnetic Speaker`, `FX Plus`, `Navegadortz2`. D-HU's bonded list holds `POCO X3 NFC` (`DC:B7:2E:5E:4E:59`), and `native-poke-bt-macs` holds exactly that address.
- **P2:** D-HU `shared_prefs/` read `u0_a176:u0_a176 771`, `settings.xml` `u0_a176:u0_a176`. Not root-owned.
- **P4:** D-HU station `Pegue Cdesta` at **5745 MHz**, not the 5500 MHz the brief expects. D-POCO on the same network at 5745 MHz, `groupFormed: false`. P5: `:149D` count 1 on D-POCO.
- **Gearhead version:** D-POCO runs **17.9.664004-release**. The brief's decompiles are 17.8 and 17.5. See the phone-string drift below.
- **Settings delta.** The round-start `settings.xml` (backup `settings-backup-roundstart.xml`) was left by an earlier thread. The baseline write changed: `narrow-band-profile-cap` true to false, `native-poke-all-paired` true to false, `log-level` 2 to 0, `resolutionId` 2 to 3; `wifi-connection-mode` 3, `native-ap-transport` 0, `native-aa-wake-damage-verdict` 0, `announce-connection-configuration` true, `onboarding-version` 2 were already as required; `video-profile-starvation-cap` was absent. Found and left alone: `video-codec` H.264, `fps-limit` 60, `video-fit-mode` 0, `fullscreen-mode` 1, `native-driver-selection-mode` 1 (AUTO) until R2b. Written with the pushed-script `hu_kv` as root, each result read back, XML parse checked.
- **Driver selection deviation (operator-approved), and a finding for the selector logic.** The brief says to leave `native-driver-selection-mode` as found, which was **1 (AUTO)**. In R2 the selector chose D-MOTO (`A0:46:5A:97:E4:95`) although D-MOTO's Bluetooth was off and `native-poke-all-paired` was false:
  `12:25:05.429 NativeAaHandshakeManager.refuseAtGate | NativeAA: the driver chose A0:46:5A:97:E4:95, so DC:B7:2E:5E:4E:59 waits until that phone has had its turn.`
  The app then poked D-MOTO via HFP-AG and HSP-AG (12:25:19 and 12:25:39, 12:26:15 and 12:26:35, each `read failed ... ret: -1`, about 20 s per pair) and held D-POCO back. D-POCO's phone logged 544 `Retrying connection attempt on all channels` between 12:25 and 12:26. The session only formed at 12:27:04 (`Incoming connection detected`), about 2 min after the group came up. The wake list held only D-POCO, so the selector overrode it. On the operator's instruction (the selector logic may need revising) R2 was repeated as **R2b** and R1 ran with `native-driver-selection-mode=0` (DISABLED). R3 was not reached. The backup restore at the end returned the key to its round-start value.
- **Clean-run protocol:** phone `cmd connectivity airplane-mode enable`, then app force-stop, capture, launch, 15 s, `ph_down` reversed by `ph_up` (airplane off, WiFi on, Bluetooth on, each read back with `dumpsys`). Phone on its launcher before every run. All markers landed (`AutomationMarker:` lines present). Phone clock was read with `date` on D-POCO before each launch (`PHT`).
- **R2 first attempt kept as `R2-*` files**, the repeat is `R2b-*`. The brief allows one repeat for a setup failure; I treated the selector choosing a Bluetooth-off phone as one. Both are graded below.
- **Windowed greps:** every head unit count is `upto <id>-end` over a capture that holds one launch; phone counts start at the printed `PHT`. `LC_ALL=C` was needed because the driver spam trips awk's multibyte warnings.
- **Brief defects found.**
  1. `Checking video config at index <i> codec:<c> fps:<f>. isAllowed: <bool>` appears **0 times in both phone captures of every run** on Gearhead 17.9 (R2, R2b, R1). The only video lines are `GH.CAR.VIDEO: The videoCodecType to be used is: 3` and, in R1, `VideoCodecResolutionType 3 (3) is not allowed due wireless frequency.` So R2 condition 9 and R1 condition 6b are unobservable on this build. I excluded 6b from the R1 verdict (the brief says a 0 on another version can mean a renamed string) and recorded R2 condition 9 as not met in the table, but I do not rest the R2 reading on it.
  2. R2 condition 8 counts `Retrying connection attempt on all channels` from `PHT` with no end bound. In R2b it reads 3: one at 12:31:35.588 before `FOUND_COMPATIBLE_WIFI_NETWORK` (12:31:51.965; the version request went out at 12:31:51.815), and two at 12:34:17.892 and .894, after `R2b-end` (12:34:16.416), i.e. teardown after `huexit`. No retry occurred between the status 0 reply and the exit. A window `PHT` to `R2-end` would read 1.
  3. The bottom-bar taps (`59,665`, `400,665`, `652,665`) assume the bar at the bottom. On the 1920x1080 session (R2b) the phone laid out `GhFacetBar` as a **125 x 1080 rail** (`CAR.WM.CW: GhFacetBar.onWindowSurfaceAvailable width: 125 height: 1080`), so the R2 control taps landed in the map, not the bar. In R1 the bar was `width: 1440 height: 109` at the bottom, as the brief assumes.
  4. P4 expected 5500 MHz, the station reads 5745 MHz.
- **R3, two attempts, and what changed the AP.** First attempt (before the operator's change): `start-softap -b 2` reported 2472 then 2427 MHz in the callback, but D-POCO joined `OHU-TEST` at `Frequency: 5745MHz` (the AP followed the station's channel) and R3 was UNTESTABLE. After the operator set the preferred hotspot band to 2.4 GHz, `start-softap -b 2` took D-HU's own station down (`Wi-Fi is disabled`, `Supplicant state: DISCONNECTED`, no `wlan0` address); one start printed `Soft AP failed to start`, a `stop-softap` then `start-softap` cycle came up at 2432 MHz and D-POCO joined at 2432MHz. D-HU's station was therefore off the network for the whole of the R3 addendum. `svc wifi enable` at the end rejoined `Pegue Cdesta` 5745 MHz within 30 s with no tap.
- **Void R3 attempt, a restore trap.** The first R3 run after the closing steps ran with `resolutionId` 2 (`[RES_CAP] resolutionId=2 ... capped=_1280x720`) and `log-level` 2, because the closing restore had written back the round-start `settings.xml`, which carries the earlier thread's values. No fallback was offered, so the run was not graded (setup failure). Its files are in `void-R3-attempt1/`. The baseline was re-written and `run_r3.sh` now fails unless `resolutionId`, `log-level`, `native-driver-selection-mode`, `narrow-band-profile-cap` and `wifi-connection-mode` read back as required. A brief that restores between runs and then runs again has to re-seed.
- **Closing (run twice, after R1 and after the R3 addendum):** `settings.xml` restored as root from the round-start backup, owner `u0_a176:u0_a176`, `diff` against the backup is empty both times. D-POCO `OHU-TEST` forgotten and D-POCO back on `Pegue Cdesta` 5745MHz. D-HU station `Pegue Cdesta` 5745 MHz at the end (after `svc wifi enable`, see the R3 note), and read after R2b and after R1 by the script. Candidate APK left installed. No logcat processes left (`rig_cleanup.sh list`: no strays, lock free). Host thermal: R2 max 63C, R2b max 63C, R1 max 64C, `throttle_pkg=0` in all three.

## R2: positive control on a 5 GHz group

**FAIL**

Graded on R2b (R2's first attempt is in the table). Condition 8 and condition 9 as written are not met; conditions 1 to 7 are. The substantive control (status 0, the phone pinging, the hold answered) holds in both attempts. If the operator reads condition 8 with an end bound and condition 9 as unobservable on 17.9, R2 is a PASS; see Setup notes, brief defects 1 and 2.

- Settings written: baseline plus `wifi-direct-band` 1; R2: `native-driver-selection-mode` 1 (as found); R2b: 0.
- Radio state: phone airplane mode on, then off with WiFi and Bluetooth enabled and read back; D-HU station on `Pegue Cdesta`, `groupFormed: false` before each launch.
- Discard-rule check: `createGroup SUCCESS` 1, `Magic Garbage` 0, `MATCH! Starting AapService` 0 in both attempts. `p2p-wlan0-1` in R2b appears at 12:30:58.021, before its first `createGroup SUCCESS`, a stale-group index bump (benign).
- Pre-check: `Initializing WiFi Mode: NATIVE` 1; `[RES_CAP] ... capped=_1920x1080 ... linkCapped=none`.

| Condition | R2 (driver AUTO) | R2b (driver 0) |
|---|---|---|
| Group `Freq:` | 5180 MHz (5GHz) | 5785 MHz (5GHz) |
| 1 TX field 4 | `channels=[5180]` met | `channels=[5785]` met |
| 2 RX status | `SUCCESS(0)` met | `SUCCESS(0)` met |
| 3 fallback offered, `configuration_indices: 1` | 1 and 1 met | 1 and 1 met |
| 4 `config_index`, `the phone chose the` | 0, 0 met | 0, 0 met |
| 5 last `[HOLD]` | `120s, 120 pings answered` met | `120s, 121 pings answered` met |
| 6 session alive | sslc 1, holds 2, closed 0, early end 0 met | same, met |
| 7 `Asking for ping timeout` | 0 met | 0 met |
| 8 phone: FOUND_COMPATIBLE 2 (>= 1), channels not supported 0, timed out on pings 0, retries | retries **548** NOT met | retries **3** NOT met |
| 9 `Checking video config at index 0 ... isAllowed: true` | 0, string absent NOT met | 0, string absent NOT met |

Decisive lines (R2b): `12:31:57.885 AapSslContext.performHandshake | SSL handshake complete`, `12:31:51.815 NativeAA: [TX] version request channel type=CHANNELS_DUAL_BAND channels=[5785]`, `NativeAA: [RX] WifiVersionResponse v4.2 status=SUCCESS(0) channelType=0 ...`, `NativeAA: [HOLD] Bluetooth channel held 120s, 121 pings answered.`, phone `12:31:51.965 GH.WIRELESS.SETUP: State changed to FOUND_COMPATIBLE_WIFI_NETWORK`.

Recorded, not graded: `WifiStartRequest from the phone, ignored on purpose` 0 in both; `the phone selected 1.7 (we asked for 1.2), status STATUS_SUCCESS`; phone `has not received the ping response for last: ` 143 (R2) and 144 (R2b), all of the form `last: 1 ping requests`; `Throughput over` windows 26 (R2) and 27 (R2b) with median `rendered=150` in each; `StationStandDown: ` 1 line in R2b (`this unit's own WiFi network is on 5745 MHz and the group is asking for 5 GHz as well, which is the state measured to run clean`); station read back `Pegue Cdesta` 5745 MHz.

The R2 retry count of 548 comes from the selector gate described in Setup notes (544 retries at 12:25 and 12:26, before the 12:27:04 connection). In R2b the three retries sit outside the status 0 to exit span, as described in brief defect 2.

## R1: 2.4 GHz group, 1080p, the phone picks the fallback

**PASS**

- Settings written: `wifi-direct-band` 2; `native-driver-selection-mode` 0; baseline as in Setup notes.
- Radio state: as R2.
- Discard-rule check: `createGroup SUCCESS` 1, `Magic Garbage` 0, `MATCH! Starting AapService` 0, `p2p-wlan0-2` (one index bump at launch, before the group). No repeat needed.
- Pre-check: `Initializing WiFi Mode: NATIVE` 1; group `Freq: 2412 MHz (2.4GHz, channel 1)`; `[RES_CAP] resolutionId=3 ... capped=_1920x1080 ... linkCapped=none`; `NegotiatedResolution is: 1920x1080` 1; `createGroup SUCCESS` 1.
- **Gate:** `is not allowed due wireless frequency` = **1**. `12:35:57.093 GH.CAR.VIDEO: VideoCodecResolutionType 3 (3) is not allowed due wireless frequency.` The phone read the link as 2.4 GHz.

| Condition | Measured | Result |
|---|---|---|
| 1 TX field 4, RX status | `channels=[2412]` (equals `Freq:`); `WifiVersionResponse v4.2 status=NO_SUPPORTED_WIFI_CHANNELS(-8)` | met |
| 2 fallback offered, index list | `Offering a _1280x720 fallback, margins 0x0` 1; `configuration_indices: 1` 1 | met |
| 3 phone's pick | `Media Start Request VIDEO: session=0, config_index=1` | met |
| 4 app adopted it | `HeadUnitScreenConfig.adoptFallback | HeadUnitScreenConfig: Video: the phone chose the _1280x720 fallback (index 1), margins 0x0` count 1; margins equal the offer's `0x0` | met |
| 5 frames after the choice | `Throughput over 5003ms: rendered=56 (11fps)`, then 143 (28fps), 148 (29fps); 17 windows from SSL to `R1-end`, median `rendered=150` | met |
| 6 phone | `No working configuration` 0 met; `Checking video config at index 1 ... isAllowed: true` 0, string absent on 17.9 | first met; second unobservable, excluded (defect 1) |
| 7 phone and HU | `WiFi channels not supported` 1 (>= 1); `has not received the ping response for last: ` 0; last `[HOLD] Bluetooth channel held 60s, 0 pings answered.` | met |
| 8 `sslc` | 1 | met |

Recorded, not graded: `StationStandDown: ` 11 lines (`standDown ... mode=AUTO, station on 5745MHz, 5GHz=true`, `this unit has left its WiFi network (config=enabled)`, then `the platform rejoined this unit's WiFi network 4s into the stand-down (config=current, WifiLock not held); leaving it a...`); `WifiStartRequest from the phone, ignored on purpose` 0; station read after `huexit`: `Pegue Cdesta` 5745 MHz, no WiFi cycle needed. `R1-pre.png` shows the projected map filling the 1440x720 panel with the bottom bar (media controls and clock) intact; judged by eye only.

The refusal at 12:35:57.093 and `Media Start Request ... config_index=1` together show the fallback worked end to end on this Gearhead build: the phone refused index 0 at 2.4 GHz and took index 1, and frames rendered at 28 to 29 fps.

## R4: touch during the fallback session

**INCONCLUSIVE**

The head unit side holds; the phone side cannot be read. The phone logged **no `injectMotionEvent(event:` line** in `R1-phone.txt` (0) nor in `R2b-phone.txt` (0), so there is nothing to compare against the mapped X. By the brief's rule that is INCONCLUSIVE, not FAIL (the FAIL case needs R2 control lines and no R1 lines).

- Condition 1 (head unit): all 6 `Touch map:` lines in R1 after the `chose the` line carry `video=1280x720 margin=0x0`, equal to the `chose the` line's margins. 0 `Touch map:` lines before it. Met.
- Per tap, from `Touch map` (X, Y in video coordinates) and the phone:

  | Tap | raw | R1 `video=` X,Y | R1 phone window, x[0], y[0] | R2b `video=` X,Y | R2b phone window, x[0], y[0] |
  |---|---|---|---|---|---|
  | t1 | 59,665 | 52,665 | no line | 79,998 | no line |
  | t2 | 400,665 | 356,665 | no line | 533,998 | no line |
  | t3 | 652,665 | 580,665 | no line | 869,998 | no line |

  The ratio `x[0] / X` cannot be computed. On R1 the mapping is consistent with the brief's 1280x720 reading (X = raw x * 1280/1440, Y = raw y); on R2b it is raw x * 1920/1440 and Y = raw y * 1080/720.
- The phone has the window `GhFacetBar` in both sessions, but its geometry differs (R1: 1440 x 109 at the bottom; R2b: 125 x 1080 at the left). This only confirms the phone laid the two sessions out differently.

## R3: head unit server on a 2.4 GHz access point

**INCONCLUSIVE**

Run as an addendum after the operator set D-HU's preferred hotspot band to 2.4 GHz. The link was 2.4 GHz and 1080p was announced, but the phone never logged the wireless-frequency refusal, so the gate fails and nothing is graded. This supersedes the first attempt's UNTESTABLE (the AP had followed the station to 5745 MHz).

- Settings written: baseline re-written (see Setup notes, the restore trap), `wifi-connection-mode` 0, `native-driver-selection-mode` 0, `log-level` 0, `resolutionId` 3.
- Link: `start-softap OHU-TEST wpa2 testtest1234 -b 2` came up at `frequency= 2432` on `wlan2` (`SoftApInfo{bandwidth= 2, frequency= 2432`) and D-POCO's `mWifiInfo` read `SSID: "OHU-TEST"`, `Frequency: 2432MHz`, IP `192.168.232.248`. `PIP=192.168.232.248` was the `ACTION_CONNECT --es ip` target.
- Pre-check met: `Initializing WiFi Mode: MANUAL` 1; `[RES_CAP] resolutionId=3 ... chosen=_1920x1080 capped=_1920x1080 changed=false linkCapped=none`; `NegotiatedResolution is: 1920x1080`; `AutomationReceiver: ... ACTION_CONNECT` and `ACTION_DISCONNECT` landed.
- Discard-rule check: `createGroup SUCCESS` 0, `Magic Garbage` 0, `MATCH! Starting AapService` 0; WPP lines 0, as the brief says for this mode.
- **Gate:** `pcnt P "$PHT" "is not allowed due wireless frequency"` = **0**. The phone only logged `12:49:44.446 GH.CAR.VIDEO: The videoCodecType to be used is: 3`. So the phone did not treat this link as 2.4 GHz wireless, and the brief rules the run INCONCLUSIVE.
- Recorded: `Offering a _1280x720 fallback, margins 0x0` 1 and `configuration_indices: 1` 1 (the head unit offered it); `Media Start Request VIDEO: session=0, config_index=0` (phone took index 0); `the phone chose the` 0; `No working configuration` 0; `Checking video config at index 1` 0 (string absent on 17.9); `SSL handshake complete` 1 at 12:49:44.474 (the session formed within 10 s of the connect); `Throughput over` 7 windows, median `rendered=149`, so 1080p video ran on the 2.4 GHz AP link without refusal.
- Reading: Gearhead's frequency refusal applies to its wireless projection transport, not to a head unit server TCP session, even when the TCP link rides a 2.4 GHz access point. The fallback offer is harmless there, and the phone never needs it. Whether a head unit server session can hit the refusal at all is therefore not settled by this rig; it did not here.

## Anything the brief did not ask about

- **The driver selector chose a Bluetooth-off phone over the wake list.** With `native-driver-selection-mode=1` and `native-poke-all-paired=false`, D-HU picked D-MOTO (powered off, not in `native-poke-bt-macs`) and made D-POCO wait for its turn: about 2 min lost before the session formed, 4 failed pokes to a phone that cannot answer, 544 WPP restarts on the phone. The refusal line is `NativeAaHandshakeManager.refuseAtGate` at `12:25:05.429`. Evidence for any revision of the selector: `R2-hu.txt` 12:25:05 to 12:27:04 (the capture is in the round asset).
- **Station stand-down at R1 behaves as the brief predicts, with one extra beat:** the platform rejoined the station 4 s into the stand-down and the app left it again (`StationStandDown.onStationJoined`). 11 lines, no WiFi cycle needed to recover.
- **R2's group frequency varied between attempts:** 5180 MHz then 5785 MHz, both 5 GHz, field 4 followed each.
- **WPP ping works end to end** on Gearhead 17.9: 1 Hz pings, 120 to 121 answered in 120 s, and 143 to 144 phone-side `has not received the ping response for last: 1 ping requests` lines that never progressed past 1 (no `timed out on pings`).
- **Phone string drift to 17.9:** `Checking video config at index ...`, `isAllowed:` and `injectMotionEvent(event:` do not print at all; `is not allowed due wireless frequency`, `WiFi channels not supported`, `State changed to FOUND_COMPATIBLE_WIFI_NETWORK`, `has not received the ping response for last: ` and `Retrying connection attempt on all channels` do.
- **R3 on a head unit server link:** the phone's 2.4 GHz refusal did not fire over a TCP head unit server session on a 2.4 GHz AP (gate 0, frame rate 149 per 5 s window). Together with R1 this puts the refusal on the wireless projection path only, which is what the fallback is for.
- **Evidence:** release `rig-evidence-aa-178-protocol-levers`, asset `aa-178-protocol-levers-round1-captures.zip`, sha256 `3c272d99d8fefcf30aee60169919d53d8a42e4b88f558c2985a90600bbe5e857`.
