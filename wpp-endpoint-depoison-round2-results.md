# wpp-endpoint-depoison — round 2 results

**Candidate:** `fork/fix/wpp-endpoint-depoison` @ `8b3e15f3`       **Base:** `main` @ `80a81099` (no baseline APK; this round has no A/B)
**APK md5:** `371d1cfee61a5f4e6246388dceea90e3`
**Unit:** D-HU (UNISOC MT50), Android 14, hotspot interface `wlan2`; phone D-POCO (POCO X3 NFC), Gearhead `17.8.163804-release.daily`
**Date:** 2026-09-19

**Evidence:** `rig-evidence-wpp-endpoint-depoison-round2` (`wpp-endpoint-depoison-round2-captures.zip`, sha256
`76e04ad321c44b0d225af128e7ab230bb08438c12e616bcf2974b13d4d30acea`) — continuous logcat from both devices across
the whole round (R1 through R3), plus the settings.xml backup taken before the round.

## Setup notes

- **Phone runs Gearhead 17.8.163804**, same build round 1 used. No 17.5 device is on this rig for this thread.
- **`build_hur.sh` and `run_unit_tests.sh`** built and tested R0 at `8b3e15f3`, checked out via `git checkout
  fork/fix/wpp-endpoint-depoison` (detached). `install_and_launch.sh` installed it (`adb install -r`).
- **`install_and_launch.sh SKIP_BUILD=1` did not skip the build** the first time it was invoked in this round —
  `SKIP_BUILD=1` was passed as a second positional argument instead of a leading env var assignment, so the
  script rebuilt from source. The rebuild produced the identical APK (md5 matched the direct
  `assembleGithubDebug` build), so this cost time but did not affect the round.
- **The phone's own Bluetooth adapter was off at the very start of the round**, unrelated to any HU-side
  BT-disable action — flagged by the user mid-round, confirmed with `dumpsys bluetooth_manager` (`state: OFF`),
  fixed with `svc bluetooth enable`. Before that, the head unit's pokes were failing outright
  (`Poke via HFP-AG ... failed: read failed, socket might closed or timeout, read ret: -1`) because there was no
  radio on the other end to answer them, not a rig BT-stack fault.
- **Forgetting the head unit's vehicle entry on D-POCO** used the same UI path round 1 used (Settings → Apps →
  Android Auto → Additional settings in the app → Vehicles → Google → Forget, via `uiautomator dump` + `input
  tap`, minimum taps each time). Done twice: before R1 and between R2 and R3, per §3.
- **R1 PASS, unchanged from round 1's method.** The reconnect after `headunit://exit` + force-stop + relaunch
  landed on the stored WPP endpoint directly — no RFCOMM/poke reopened for it
  (`NativeAA: Not poking POCO X3 NFC ... this head unit already holds a Bluetooth hands-free link to it`), then
  straight to `WppTcpServer: connection from 192.168.143.183` with no `NativeAA: Connection accepted from`
  in between. Phone confirms: `GH.WPP.TCP: Trying to start WPP on TCP with configuration: ...
  ipAddress=192.168.143.137, port=5299` logged repeatedly through the whole gap before the head unit came back up.
- **R2 is INCONCLUSIVE, and the mechanism is a rig/hardware limitation, not the app.** The brief's own §3
  anticipates this exact failure and says to stop: "if the switch takes it down on this ROM, say so and stop,
  because the run cannot be scored without it." That is what happened here. Full mechanism below (its own
  paragraph, since it is the round's central finding). R4 and R5 both depend on this same precondition and are
  reported as not reached rather than run against a broken setup.
- **R6 is fully gradable from the R2 attempt's own capture**, independent of whether the AP survived, and is a
  clean PASS — see its own section.
- **R3 PASS**, run after re-forgetting the head unit on the phone and reverting to R1's hotspot settings
  (`native-ap-transport=1`), with the access point manually restarted (`cmd wifi start-softap Navegadortz2 wpa2
  12345678 -b 5`, confirmed on 5745 MHz via `SoftApInfo` before launch). Zero `rejecting this dial` or
  `session error:` lines across the entire R3 window (first connect + exit/reconnect), matching PASS exactly.
- **R4 was not attempted this round.** Its precondition needs the transport switched *live*, inside the same app
  process that already holds a completed Bluetooth handshake (`canRunRfcomm()`'s in-memory
  `aaListenersClosedForSession` flag must survive the switch), which rules out `set_pref.sh` — it force-stops the
  app before editing `shared_prefs/settings.xml`, which would reset exactly the flag R4 needs to still be true.
  Doing this live would need the in-app Settings screen (`nativeApTransport`'s
  `SegmentedButtonSettingEntry` in `SettingsFragment.kt:1120`, a staged/pending setting that only applies on
  save), an automation surface this round had not used before, and — per R2's finding just above — flipping
  that setting live would trigger the same platform-level AP teardown, so it also risks losing whatever radio
  state R4 needs mid-attempt. The brief's own R4 section pre-authorizes exactly this call: "Desk check instead:
  if that state cannot be reached in one attempt, say so and stop. The decision itself is covered by
  `WppTcpServePolicyTest`, and burning the round on the setup costs more than it proves." R0 confirms that suite
  at 9/9 passing on this candidate. Taking the desk-check exit rather than attempting the live toggle blind.
- **R5 was not reached**, since it is graded "after R2" and R2 never reached its measurement window.
- Diffed `settings.xml` against a fresh backup taken at the very start of the round (in the evidence zip) and
  restored it byte-for-byte at the end (`diff` confirmed clean, no output). Delta during the round:
  `native-ap-transport` (1 for R1/R3, 0 for R2), `native-wifi-version-exchange`, `auto-enable-hotspot`,
  `hotspot-ssid`, `hotspot-password`, `static-bssid`, `hotspot-interface` (all per §3); the five WiFi-Direct
  identity keys (`wifi-direct-last-group-ssid`, `wifi-direct-last-group-bssid`, `wifi-direct-group-name`,
  `wifi-direct-group-passphrase`, `wifi-direct-group-name-changes`) were already absent/zero on this device
  before the round started (leftover from round 1's own run), so no write was needed for them; `static-p2p-bssid`
  was left unset (`0`) throughout, as instructed.
- `log-level=2` (INFO) throughout, as the brief says is enough.
- D-HU's Bluetooth adapter was toggled off/on once during R1 setup (`svc bluetooth disable; sleep 5; svc
  bluetooth enable`) to force the HFP-AG poke socket to find a live link on the other end, per
  `project_mt50_hfp_link_needs_hu_bt_toggle` — left on at the end, matching the round's normal working state.
  D-POCO's Bluetooth adapter was left on at the end (it was off only because of the pre-round state noted above).
- The SoftAP that was manually started for R1/R3 was manually stopped at the end of the round
  (`cmd wifi stop-softap`), confirmed with an empty `SoftApInfo` map afterward.
- Both logcat captures (`stdbuf -oL adb logcat -v time`) ran continuously from before R1 through the end of R3
  and were killed by pid at the end of the round; confirmed no stray `adb logcat` processes remained.

### The R2 mechanism, in full

Settings were correct and the access point was confirmed up (`SoftApInfo` showing `wlan2` at 5805 MHz) both
immediately before and 2 seconds after the head unit was relaunched with `native-ap-transport=0`. About 8 seconds
into that relaunch — once `AapService`/`WifiDirectManager` began creating the WiFi Direct group — the *platform's*
own WiFi HAL logged `HalDevMgr: bestIfaceCreationProposal is null, requestIface=P2P, existingIface=[name=wlan2
type=AP, name=wlan0 type=STA]`, immediately followed by `WifiService: stopSoftAp uid=1073` (uid 1073 is
`com.android.networkstack.tethering`, not this app) and then `hostapd: wlan2: AP-DISABLED`. This is the chip's
own interface-combination arbitration concluding it cannot hold an AP interface and create a P2P group
interface at the same time, and preempting the AP — nothing in `com.andrerinas.headunitrevived`'s own code
called `stopSoftAp`. The phone's radio (`wpa2_supplicant`) logged `CTRL-EVENT-DISCONNECTED bssid=00:27:15:43:06:6a
reason=1` essentially simultaneously (11:50:40.885, versus the head unit's `stopSoftAp` at 11:50:41.134).

Because the AP came down, every dial the phone made to the stale `192.168.143.137:5299` endpoint during the
window either predated `WppTcpServer` binding (`ECONNREFUSED`, nothing listening yet on a fresh app launch) or
postdated the phone's own move onto the WiFi Direct subnet (`SocketTimeoutException`, the address is on a subnet
the phone's P2P-only route table can no longer reach at all). `WppTcpServer` never logged a `connection from`
line for a stale dial during the entire window, so `rejecting this dial` was never exercised — the app-level
mechanism this run exists to measure was never reached, through no fault of the app.

Separately: the phone *did* fall back to a fresh RFCOMM/Bluetooth handshake (Connection accepted → Type 1–7
exchange) once its WPP-TCP retries had exhausted enough attempts, and on that handshake the head unit correctly
withheld the new WiFi Direct endpoint (`NativeAA: not advertising WPP over TCP: this unit gives its WiFi Direct
group a new address on every create...`, since the freshly-cleared identity read `stable=no`), handing over
group credentials the old way instead. The phone associated to the resulting WiFi Direct group successfully
(traffic later confirmed from `192.168.49.178`) — notable on its own since round 1 could not get the phone onto
this unit's WiFi Direct group at all on three attempts; see R6.

## R0 — build gate

**PASS**

- `compileGithubDebugKotlin` clean, `testGithubDebugUnitTest`: **2180 / 0** (all suites), JDK 17.
- `P2pBssidSourcePolicyTest` **7** (new with `8b3e15f3`), `SoftApBssidPolicyTest` **20**.
- `WppMessagesTest` **17**, `WppTcpServePolicyTest` **9**, `WppEndpointPolicyTest` **10**,
  `WppHandshakeSessionTest` **30** — all match the brief exactly.

## R1 — poison the phone deliberately

**PASS**

- Settings written: `wifi-connection-mode=3` (unchanged), `native-ap-transport=1`,
  `native-wifi-version-exchange=true`, `hotspot-band=1` (unchanged), `auto-enable-hotspot=false`,
  `hotspot-ssid=Navegadortz2`, `hotspot-password=12345678`, `static-bssid=00:27:15:43:06:6a`,
  `hotspot-interface=wlan2`.
- Radio state: SoftAP started manually on 5 GHz (`cmd wifi start-softap Navegadortz2 wpa2 12345678 -b 5`),
  confirmed at 5805 MHz via `SoftApInfo`; station radio confirmed `Wi-Fi is enabled` beforehand.
- Discard-rule check: clean, no re-run needed.
- Decisive log lines, quoted with timestamps:
  ```
  11:47:20.914 NativeAA: advertising WPP over TCP at 192.168.143.137:5299
  11:47:29.380 WirelessServer: Incoming connection detected from /192.168.143.183
  11:47:29.822 WppTcpServer: TLS handshake complete with 192.168.143.183 ...
  11:49:20.543 NativeAA: Not poking POCO X3 NFC ... this head unit already holds a Bluetooth hands-free link
  11:49:20.704 WppTcpServer: connection from 192.168.143.183          <- reconnect, no RFCOMM accept before it
  11:49:11.115 GH.WPP.TCP: Trying to start WPP on TCP with configuration: ... ipAddress=192.168.143.137, port=5299
  ```
- No measurements beyond the above; matches brief's PASS wording exactly.

## R2 — the measurement

**INCONCLUSIVE**

- Settings written: `native-ap-transport=0`; the five identity keys were already clear (see Setup notes).
- Radio state: AP confirmed up (`SoftApInfo` @ 5805 MHz) immediately before and ~2s after the head unit relaunch;
  torn down by the platform ~8s further in — see the mechanism paragraph above.
- Discard-rule check: not re-run — the brief's own §3 says to stop rather than re-attempt when the platform
  takes the AP down on this ROM.
- Decisive log lines, quoted with timestamps:
  ```
  11:50:41.104 HalDevMgr: bestIfaceCreationProposal is null, requestIface=P2P, existingIface=[name=wlan2 type=AP, name=wlan0 type=STA]
  11:50:41.134 WifiService: stopSoftAp uid=1073
  11:50:41.150 hostapd: wlan2: AP-DISABLED
  11:50:40.885 wpa_supplicant (phone): CTRL-EVENT-DISCONNECTED bssid=00:27:15:43:06:6a reason=1
  ```
  No `WppTcpServer: connection from`, no `rejecting this dial`, no `session error:` at any point in the window —
  the code path this run exists to exercise was never reached.
- No measurement produced; the brief's own R2 fallback ("if the switch takes it down on this ROM, say so and
  stop") is the applicable verdict path, which maps to INCONCLUSIVE per house rule 3 (a rig/hardware limitation,
  not a code problem).

This is a genuine rig capability limit, not a re-run candidate: the same chip-level interface arbitration would
fire again on any repeat attempt with the AP and WiFi Direct group both wanted at once, on this ROM.

## R3 — the regression that matters

**PASS**

- Settings written: reverted to R1's set (`native-ap-transport=1`), AP manually restarted at 5745 MHz.
- Radio state: `SoftApInfo` confirmed up before launch; phone's Android Auto vehicle entry re-forgotten first
  (`Accepted vehicles: None` confirmed via `uiautomator dump`) so this run started from neither R1's nor R2's
  record.
- Discard-rule check: clean.
- Decisive log lines, quoted with timestamps:
  ```
  11:58:xx  WirelessServer: Incoming connection detected / SSL handshake complete   <- ordinary first connect, projecting
  11:58:31.966 WppTcpServer: connection from 192.168.143.183                        <- reconnect after exit/relaunch
  11:58:32.033 WppTcpServer: handshake complete; projection session is up
  ```
- Measurement: `rejecting this dial` / `session error:` count across the entire R3 window (first connect through
  reconnect) = **0**.

## R4 — the stranding guard

**Desk check — not attempted.** See Setup notes for why (live in-app setting toggle needed, not reachable with
this round's tooling without risking the very in-memory state the run depends on). `WppTcpServePolicyTest`
(9/9, confirmed in R0 at this candidate SHA) covers the decision this run would exercise on-device.

## R5 — the banner

**Not reached.** Graded "after R2"; R2 never reached the state that would produce the banner.

## R6 — the setting that cost round 1

**PASS**

- Graded on the R2 bring-up's own WiFi Direct group creation (the attempt above), with `static-bssid` still set
  to the access point's address (`00:27:15:43:06:6a`) throughout, per §3.
- Every `onGroupInfoAvailable` line in the window names a detected source, never a static one:
  ```
  11:50:42.234 WifiDirectManager: onGroupInfoAvailable: SSID: DIRECT-RB-Navegadortz2, BSSID: 16:AC:69:D7:78:CD (source=IPv6 link-local), GO: true, IFACE: p2p-wlan0-0, Freq: 5220 MHz (5GHz)
  11:50:42.238 WifiDirectManager: group identity ssid=DIRECT-RB-Navegadortz2 persistent=yes (netId 5) asked=persistent matchesRequest=yes bssid=16:AC:69:D7:78:CD stable=no (same name but the BSSID moved from 02:F3:F4:1C:58:FB to 16:AC:69:D7:78:CD; this unit re-addresses the group on every create) source=IPv6 link-local
  ```
  (repeated identically on every subsequent `onGroupInfoAvailable` callback for this group; all say
  `source=IPv6 link-local`.)
- Neither `source=WiFi Direct setting` nor `source=access point setting (stand-in)` appears anywhere in the
  capture (`grep -c` = 0), and the stand-in text `nothing on this device reported the group's own address` also
  never appears.
- The `group identity` line reads `stable=no (...)`, never `stable=yes (the static BSSID setting fixes the
  address the phone is told)` — confirming the access point's `static-bssid` no longer bleeds into the WiFi
  Direct group's stability computation.
- The BSSID source dump for this bring-up:
  ```
  WifiDirectManager: == BSSID source dump (iface=p2p-wlan0-0) ==
  WifiDirectManager:   WiFi Direct override (Settings)  = 0
  WifiDirectManager:   access point override (Settings) = 00:27:15:43:06:6a
  WifiDirectManager:   getGroupOwnerBssid()             = null
  WifiDirectManager:   BSSID read from the IPv6 link-local address of p2p-wlan0-0 (EUI-64): 16:AC:69:D7:78:CD
  WifiDirectManager: == end BSSID source dump ==
  ```
  The two settings are named and reported separately (`WiFi Direct override` vs `access point override`), and
  the announced BSSID (`16:AC:69:D7:78:CD`) matches the `IPv6 link-local` row exactly.
- **The phone associated to the WiFi Direct group on this build**, unlike round 1 where it failed to join on
  three separate attempts. This run's group formed and the phone was confirmed on the resulting subnet
  (`192.168.49.178`) — round 1's join failure does not reproduce here.

## Anything the brief did not ask about

- `install_and_launch.sh`'s `SKIP_BUILD=1` only works as a leading environment-variable assignment
  (`SKIP_BUILD=1 ./install_and_launch.sh`), not as a trailing positional argument — passing it as
  `./install_and_launch.sh SKIP_BUILD=1` silently rebuilds instead. Worth a one-line note in the script's own
  header comment for the next round that reaches for it.
- The chip-level AP/P2P interface-combination limit found in R2 is worth flagging as a standing rig fact
  alongside the existing `TESTING-TEMPLATE.md` §7a entries: **this unit's WiFi HAL cannot hold a SoftAP and a
  WiFi Direct group owner interface at the same time**; requesting the second always tears down the first
  (`HalDevMgr: bestIfaceCreationProposal is null` → `WifiService: stopSoftAp uid=1073` from
  `com.android.networkstack.tethering`, not the app). Any future brief that wants both up at once on this rig
  needs a different unit or a different design, not a retry.

## Addendum: what the captures say about the phone's retry loop

Read back from `wpp-endpoint-depoison-round2-captures.zip` after the round was filed. Three findings,
and one sentence above withdrawn.

**Withdrawn.** Setup notes say the phone "did fall back to a fresh RFCOMM/Bluetooth handshake ... once
its WPP-TCP retries had exhausted enough attempts". The captures do not support that. The RFCOMM cycle
at `11:51:22` was concurrent with the TCP loop, not consequent on it, and the TCP loop never ended.

**1. The phone never gave up on the stale endpoint.** It re-dialled `192.168.143.137:5299` from
`11:50:30.061` to `11:59:54.103`, which is the end of the capture: 2438 `GH.WPP.TCP` lines overall, 14
three-attempt cycles inside the R2 window alone, and no give-up, fallback or attempt-ceiling line
anywhere in either capture. 17 of the 42 attempts failed `ECONNREFUSED` from `192.168.143.183`, the
other 25 `SocketTimeoutException` from `192.168.49.178` once the phone had moved onto the WiFi Direct
subnet.

**2. Gearhead runs the Bluetooth handshake and the TCP dial loop at the same time, and the Bluetooth
half can still complete.** `GH.WIRELESS.SETUP: State changed to CONNECTING_RFCOMM` repeats every ~32 s
straight through the failing window, and at `11:51:22.147` to `11:51:26.223` it ran a full cycle
(`CONNECTED_RFCOMM`, `VERSION_CHECK_COMPLETE`, `WIFI_PROJECTION_START_REQUESTED`, `CONNECTING_WIFI`,
`CONNECTED_WIFI`, `PROJECTION_INITIATED`) before dropping to `RFCOMM_READ_WRITE_FAILURE`. A second full
cycle ran at `11:57:52.688`. So a stale endpoint is a permanent parallel failure loop rather than an
absolute block on this build.

**3. Withholding does not retract an endpoint the phone already holds.** On the `11:51:23` handshake the
head unit withheld the endpoint, correctly, because the freshly cleared identity read `stable=no`. The
phone went on dialling the same dead `192.168.143.137:5299` afterwards, and it alternated **two** stored
configurations at it: `Navegadortz2` / bssid `00:27:15:43:06:6A` (the access point, 7 dials) and
`DIRECT-RB-Navegadortz2` / bssid `16:AC:69:D7:78:CD` (the **new WiFi Direct group**, 9 dials). The type-3
credentials updated the record's network while the withheld `wpp_info` left its address untouched. That
is a direct measurement of why withholding is not a cure, and it is the case the rejection answers.

**4. The station was not the reason the access point died.** D-HU's `wlan0` carries no
`CTRL-EVENT-CONNECTED` anywhere in the capture, so it was never associated to anything; the HAL counted
the interface, not the association. Standing the station down would not obviously buy the slot, and the
R2 limit is not worked around that way.

**R6's PASS re-checked line by line.** `source=static override`, `source=WiFi Direct setting` and
`source=access point setting` are each **0** across the whole capture; every `onGroupInfoAvailable`
summary reads `source=IPv6 link-local`; the single `group identity ssid=` line reads `stable=no`. The
source dump lists `WiFi Direct override (Settings) = 0` beside `access point override (Settings) =
00:27:15:43:06:6a`, and the announced `16:AC:69:D7:78:CD` matches the `IPv6 link-local` rung exactly.
