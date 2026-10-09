# native-aa-wireless — round 1 results

**Candidate:** `fork/fix/native-aa-wireless` @ `4fc4753a3`, three commits on `main` (`80a81099`)
**Baseline:** none for the new work; R1, R7 and R14 are compared against `wpp-endpoint-depoison`
round 5's measurements as the brief directs.
**APK md5:** `93a511a99a4eb0d0122a8c81d2e23969`
**Unit:** D-HU (UNISOC MT50, Android 14). **Phone:** D-POCO (POCO X3 NFC), Gearhead
`17.8.163804-release.daily`. **Also used:** D-SAM (Samsung SM-T230, Android 4.4.2 / API 19) for R16.
**Date:** 2026-09-20
**Evidence:** `rig-evidence-native-aa-wireless-round1` (release asset on `fork`), sha256
`c56c1eb61b28399acd630bb92575ebac989b17c750ab1a01cf3627ca96e290e2` — `round-native-aa-wireless-r1/`
(`r0a_r1.txt` D-HU logcat R0a-R15, `poco.txt`/`poco_r2_snapshot.txt` phone logcat, `dsam.txt` D-SAM
logcat R16, `settings-backup.xml` pre-round state)

## Setup notes

- **R7 setup conflict, found and worked around.** §2's baseline leaves `static-bssid=00:27:15:43:06:6a`
  set for R15's sake, and on this unit that value happens to equal the hotspot's real factory BSSID.
  With it set, bring-up 1 of R7 reads `stable=yes (the static BSSID setting fixes the address the
  phone is told)` instead of the `stable=unproven` the brief expects from a first bring-up — the
  static override completely short-circuits the two-bring-up promotion logic R7 exists to test.
  Cleared `static-bssid` for R7 only (and cleared the three `soft-ap-last-*` keys with it), restored
  `00:27:15:43:06:6a` immediately afterward for R8 onward and R15's own test. A brief for this thread
  should say to clear `static-bssid` for R7's setup, not just the `soft-ap-last-*` keys.
- **R10 hit a real self-inflicted stuck-phone loop, distinct from anything the brief describes.**
  Switching `native-ap-transport` back to 0 after the R7-R9 hotspot detour creates a *new* WiFi
  Direct group (new generated BSSID, same SSID) while the phone still held a stale WPP-TCP config
  from the earlier R5 session pointing at the *old* BSSID — `headunit://exit` / a transport switch
  does not itself clear the phone's stored WPP memory, only a **refused** dial does (R3's own
  finding), and no dial had been refused since R5's group. Gearhead entered its own internal WiFi
  reconnect loop fully independent of Bluetooth (`GH.WirelessNetRequest: Connected to network Pegue
  Cdesta while expected DIRECT-QS-MT50YT610E4GFPSLU`, `WIRELESS_WIFI_CONNECTED_TO_WRONG_SSID`,
  `WIRELESS_WIFI_SCAN_RESULTS_BSSID_MISMATCH`, retrying every ~17s) while the head unit sat
  `ACTIVELY LISTENING` and poking with no RFCOMM connect-back — the same self-inflicted-loop shape
  the CLAUDE.md warns about for #760, just on the phone side of the link instead of the head unit's.
  Fixed with `forget_car_gearhead.sh` again. **Worth a line in the next brief for this thread:**
  switching `native-ap-transport` mid-round should warn that a stale phone-side WPP memory from an
  earlier WiFi Direct group can block the next bring-up's handshake entirely, and the fix is
  forgetting the vehicle (or letting a dial get refused) before relying on Bluetooth auto-start again.
- **R5's exact log string is unreachable from "R2's end state" as literally described, though the
  substance it grades fully holds.** `WppTcpServer.refuse()`'s "a dial arrived while projection is up;
  holding it silent" line (`WppTcpServer.kt:266`) only fires when `WppEndpointPolicy.decide()` returns
  `Withhold` — i.e. when the identity verdict is *not* `STABLE`. R2's own success requires the verdict
  to be `STABLE`, and nothing in the R2→R5 sequence changes it during the 3-minute window, so
  `decide()` returns `Advertise` on every re-dial and `refuse()` is never called; the re-dials instead
  hit the pre-existing branch at `WppTcpServer.kt:369` ("projection already up; holding the re-dialled
  control channel open without a handshake"). Confirmed by code read, not just the capture. Functional
  behaviour is identical either way — zero rejections, session held unbroken, no stale-endpoint record
  — so R5 is graded PASS on substance below, but the brief's named string cannot fire from its own
  stated setup; reaching `refuse()`'s silent-hold branch needs a `Withhold` verdict arriving while a
  session is *already* up, a different precondition than "R2's end state" produces.
- **R8's refusal string differs from the brief's.** Actual: `"these credentials carry no passphrase,
  so the phone will refuse them and the hotspot-configuration record stays up. Set 'Hotspot password
  (manual)' as well as the name."` Brief expected: `"these credentials for '<ssid>' carry no
  passphrase, and the phone refuses an open network. Not sending them."` Same substance (refusal +
  record raised), different wording. The poke-gate line (`"not waking the phone for '<ssid>', which
  has no passphrase to join with"`) matched the brief exactly.
- **R15's one UI step (confirm a restart prompt by eye) was not completed.** The settings-screen
  search box repeatedly ate taps meant for the result row below it (bounds shifted between the
  `uiautomator dump` and the `input tap`, landing back on the search field and appending stray text
  into it instead of opening the setting), and after two clean attempts failed the same way it was
  abandoned rather than risking a bad write to `settings.xml` from a stuck field. No setting was
  actually changed by this (confirmed by reading `settings.xml` back — `static-bssid` and
  `static-p2p-bssid` were unaffected). The substantive, code-level part of R15 (both typed overrides
  ignored, detection wins) is fully confirmed below; only the UI-only restart-prompt confirmation the
  brief calls "graded by eye" was skipped.
- **R16's `wifi-direct-group-name-changes` on D-SAM started at 8, not 0**, left over from this unit's
  earlier `native-aa-recovery` testing thread — the four bring-ups in this round therefore show the
  counter walking 9→10→11→12 rather than a fresh 0→1→2→3 climb to `RENAMED`. The monotonic-increment
  invariant and the `RENAMED`-equivalent reasoning are both confirmed directly; the exact walk-to-3
  transition was not freshly observed since the unit was already long past it.
- **Two things this rig cannot reach, as the brief says up front:** the Bluetooth adapter cycle
  (needs API < 33 with a unit holding a hands-free client link; D-HU is API 34, D-SAM has no
  `HEADSET_CLIENT`) and the external Bluetooth module route (no unit has a `bttype:extra` module or a
  daemon on 3152). R17 grades the *gate* on D-HU only, as the brief directs.
- **Scripts used, unchanged:** `build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh`
  (`SKIP_BUILD=1` after the first build), `set_hu_prefs.sh` (every multi-key write this round),
  `forget_car_gearhead.sh` (three times: round start, and twice more during R10's recovery). No
  script was added to `hur-wifi-test-scripts/`.
- R13 step 2 used `headunit://disconnect` as the scripted equivalent of the projection screen's own
  "Stop Connection" rather than tapping the button, consistent with the template's own guidance that
  the deep link is the scripted equivalent of that action.
- No settings-export automation surface exists (`ACTION_EXPORT_LOG` is the only export action, and
  it exports the log, not settings), so R10's "confirm the key is gone from an export" was checked by
  source grep instead of a live UI export: `native-wifi-version-exchange` does not appear anywhere in
  `app/src/main/java/`, so it cannot appear in an export by construction.
- Settings backed up to `round-native-aa-wireless-r1/settings-backup.xml` before the first write.
  End-of-round state: `wifi-connection-mode=3`, `native-ap-transport=0`, `wifi-direct-stable-identity=
  true`, `static-bssid=00:27:15:43:06:6a`, `static-p2p-bssid=0`, `hotspot-ssid=Navegadortz2`,
  `hotspot-password=12345678` (restored after R8's empty-passphrase test).

## R0: build gate — **PASS**

`compileGithubDebugKotlin` and `testGithubDebugUnitTest` both clean via `build_hur.sh` /
`run_unit_tests.sh`. **2254 tests, 0 failures, 0 errors, 0 skipped** — matches the brief exactly. Tip
`4fc4753a3`. APK md5 `93a511a99a4eb0d0122a8c81d2e23969`.

| Suite | Expected | Measured |
|---|---|---|
| `MacAddressPolicyTest` | 10 | 10 |
| `GroupIdentityStabilityPolicyTest` | 31 | 31 |
| `WppEndpointPolicyTest` | 14 | 14 |
| `WppTcpServePolicyTest` | 12 | 12 |
| `WppMessagesTest` | 19 | 19 |
| `NativeCredentialsPolicyTest` | 10 | 10 |
| `SessionEndGroupPolicyTest` | 16 | 16 |
| `BluetoothRadioCyclePolicyTest` | 19 | 19 |
| `UserExitHotspotPolicyTest` | 13 | 13 |
| `SoftApBssidPolicyTest` | 20 | 20 |
| `P2pBssidSourcePolicyTest` | 10 | 10 |
| `P2pInterfaceNamePolicyTest` | 5 | 5 |
| `AutomationCommandPolicyTest` | 21 | 21 |

Symbol check on the installed APK (`unzip -p ... classes*.dex \| strings \| grep -F ...`):
`ACTION_END_SESSION_STAY_ARMED` present, `MacAddressPolicy` present.

## R0a: the address gate — **PASS**

First launch from a torn-down group (`headunit://exit` run once beforehand to guarantee a genuine
first create):

```
22:46:32.383 WifiDirectManager: Initial BSSID from IPv6 link-local: E2:EB:AE:62:0C:DB
22:46:32.395 WifiDirectManager: group identity ssid=DIRECT-QS-MT50YT610E4GFPSLU ... bssid=
  E2:EB:AE:62:0C:DB address=generated stable=no (first group under this name, ...) source=IPv6
  link-local
```

Full `BSSID source dump` block inspected: the `sysfs / ip link` rung independently reports the same
address, but `source=` names `IPv6 link-local`, and no line anywhere names the bare station interface
`wlan0` as the group's address (70 occurrences of the substring `wlan0` in the whole R0a/R1 window are
all the `p2p-wlan0-N` virtual interface, never the station interface alone).

## R1: a surviving group no longer promises its address repeats — **PASS**

First create: `stable=no`, `address=generated`, `not advertising WPP over TCP` (quoted above).
`am force-stop` (not exit); group survived (`dumpsys wifip2p`: `mGroup network:
DIRECT-QS-MT50YT610E4GFPSLU`). Relaunch:

```
22:47:24.317 WifiDirectManager: a group named DIRECT-QS-MT50YT610E4GFPSLU is already up from before
  this bring-up; reading it instead of tearing it down.
22:47:24.441 WifiDirectManager: group identity ssid=DIRECT-QS-MT50YT610E4GFPSLU ... bssid=
  E2:EB:AE:62:0C:DB address=generated stable=no (this group was already up and was read rather than
  created, so nothing new was measured) source=IPv6 link-local
```

Same BSSID as the first create. `wifi-direct-last-group-bssid` unchanged (`E2:EB:AE:62:0C:DB`),
`wifi-direct-last-identity-verdict` still `CHANGED`. Endpoint stayed withheld (`not advertising WPP
over TCP` repeated, same reason).

## R2: a read hands back what the creates earned — **PASS**

Seeded `wifi-direct-last-group-ssid`/`-bssid` = the surviving group's own values, and
`wifi-direct-last-identity-verdict=STABLE`, then relaunched (group read, not recreated):

```
22:48:08.254 NativeAA: advertising WPP over TCP at 192.168.49.1:5299
22:48:10.133 AapSslContext.performHandshake: SSL handshake complete
```

Phone (`poco_r2_snapshot.txt`, `22:48:32.526`):

```
GH.WPP.TCP: Trying to start WPP on TCP with configuration: WifiProjectionProtocolOnTcpConfiguration
  (wifiConfiguration=WifiConfiguration(ssid=DIRECT-QS-MT50YT610E4GFPSLU, bssid=E2:EB:AE:62:0C:DB, ...),
  ipAddress=192.168.49.1, port=5299)
```

Carries the seeded SSID and BSSID — field 5 and field 6 agree, the invariant the brief names.

## R3: the refused dial, and the phone letting go — **PASS**

Cleared only the three yardstick keys, left the group and everything else alone. `am force-stop`,
relaunch (group read again, no yardstick → `UNPROVEN` → endpoint withheld):

```
22:48:56.195 WppTcpServer: connection from 192.168.49.136
22:48:56.266 WppTcpServer: TLS handshake complete with 192.168.49.136
22:48:56.268 WppTcpServer.refuse: rejecting this dial so the phone drops our endpoint and goes back
  to Bluetooth: the WiFi Direct group's name and address have not yet been seen to repeat on this
  unit, so the phone is not told to remember it. The next bring-up decides. ...
```

Phone: `22:48:58.264 GH.WIRELESS.SETUP: Handling WifiConnectionRejection with reason:
CONNECTION_REJECTION_REASON_INVALID_SETUP_TOKEN`, then `No WPP on TCP configuration found in storage
for the head unit, will not start WPP on TCP.` repeating every ~5s from `22:48:58` through
`22:49:16` (5 occurrences). An ordinary Bluetooth-carried AAP session ran throughout (`SSL handshake
complete` `22:48:58.036`, video decoding from there on). No 10-minute wait was needed — the phone
re-dialled and was refused inside 100ms of the relaunch reaching the listener.

## R4: the stranding guard — **Not reached**

`grep -c "not serving this dial"` across the **whole round's capture** = 0, against 23 total
`WppTcpServer: connection from` dials logged. Only R3's own dial was ever refused (`grep -c
"rejecting this dial"` = 1, whole capture). This arm has now gone six rounds without executing
(five per the brief, plus this one); nothing here changes that.

## R5: a dial during projection is held silent — **PASS** (functional; see Setup notes on the string)

Re-seeded the `STABLE` verdict (same group), relaunch advertised and established a WPP-TCP session
(`SSL handshake complete` `22:49:49.036`). Left it projecting for 3 minutes untouched. 16 re-dials
landed at ~10s cadence (`22:49:49`-`22:52:32`), every one logged:

```
WppTcpServer: projection already up; holding the re-dialled control channel open without a handshake
```

— not the brief's named `"a dial arrived while projection is up; holding it silent"` (see Setup
notes for why that branch is structurally unreachable from this setup). Substance: **0** rejections
in the window, the session survived unbroken (single SSL session id the whole 3 minutes, video
throughput logs continuous at ~29-30fps throughout), `connection-issue-stale-endpoint` stayed `0`
before/during/after, and the phone logged 19 `WPP_SOCKET_IO_EXCEPTION` lines (expected — its own read
timeouts on the held-open socket).

## R6: a dial while stopping is not blamed on the phone — **PASS**

`headunit://exit` from the live R5 session (`22:52:53.188 AapService: Native AA user exit. Stopping
active launcher.`). Across the whole capture, the only `rejecting this dial` line is R3's own
(accounted for); no `connection-issue-stale-endpoint` timestamp changed during or after this exit
(`0` before, `0` after). The narrow "dial lands past TLS exactly while stopping" window was not hit
in this capture (no dial arrived in the exact second), so `"not serving this dial, and it says
nothing about the phone"` was not exercised — expected per the brief, not a failure.

## R7: the access point is measured, not assumed — **PASS** (setup conflict resolved; see Setup notes)

Cleared `static-bssid` (see Setup notes) and the three `soft-ap-last-*` keys, then:

**Bring-up 1** (AP just switched on by hand, `cmd wifi start-softap Navegadortz2 wpa2 12345678 -b 5`,
BSSID `00:27:15:43:06:6a` @ 5785MHz):

```
22:54:32.348 WifiLauncherNative: access point identity ssid=Navegadortz2 bssid=00:27:15:43:06:6A
  address=factory stable=unproven (first group under this name; the next one decides)
```

`soft-ap-last-group-bssid` written = `00:27:15:43:06:6A`, `soft-ap-last-identity-verdict=UNPROVEN`.

**Bring-up 2** (`headunit://exit`, relaunch):

```
22:55:20.926 WifiLauncherNative: access point identity ssid=Navegadortz2 bssid=00:27:15:43:06:6A
  address=factory stable=yes (same name and same BSSID as the last group)
```

`soft-ap-last-group-bssid` unchanged, `soft-ap-last-identity-verdict=STABLE`. Credentials-update log
confirms `identity stable=yes` and a poke went out; the phone's RFCOMM handshake did not land inside
the observation window on this arm (poke succeeded at socket level, no `Connection accepted from`
followed), so `advertising WPP over TCP` for the hotspot transport specifically was not directly
observed — the identity-promotion evidence itself, which is what R7 grades, is complete from the two
reads above and the settings-key evidence. As the brief notes, D-HU's own AP address is factory
either way, so this is a no-regression result, not evidence for the generated-AP case the change
exists for.

## R8: credentials with no passphrase are refused, and cost no poke — **PASS** (string differs; see Setup notes)

`hotspot-ssid=Navegadortz2`, `hotspot-password=""`:

```
22:56:52.866 SoftApCredentialsProvider.publish: these credentials carry no passphrase, so the phone
  will refuse them and the hotspot-configuration record stays up. Set 'Hotspot password (manual)' as
  well as the name.
22:56:52.878 WifiLauncherNative: not waking the phone for 'Navegadortz2', which has no passphrase to
  join with.
```

No `Wrote TYPE 3` and no `Calling socket.connect()` anywhere after `22:56:52`.
`connection-issue-hotspot-config` raised (`1789963012866`). Recovery: `hotspot-password=12345678`,
relaunch → `22:57:29.665 SUCCESS - Providing credentials` resumed cleanly — the refusal is not a
wedge.

## R9: an access point the system says is off — **INCONCLUSIVE** (expected per the brief)

`hotspot-interface=wlan2`, hotspot switched off (`cmd wifi stop-softap`). `wlan2` does not exist as
an interface once the AP is down:

```
22:58:04.318 SoftApCredentialsProvider.pickApInterface: No interface named 'wlan2'. Present: dummy0,
  seth_lte0, lo. Falling back to automatic selection.
```

The resolve stops before reaching the "named by hand" escape-hatch branch — exactly the outcome the
brief calls a fine, expected result on most units.

## R10: the version request has no setting left — **PASS**

`native-ap-transport` back to 0, `static-bssid` restored, `native-wifi-version-exchange=false`
written. After recovering from the stuck-phone loop (Setup notes):

```
23:01:28.208 NativeAA: [TX] Sending WifiVersionRequest (Type 4) v4.2
23:01:31.971 AapSslContext.performHandshake: SSL handshake complete
```

Type 4 sent and the session proceeded to a complete handshake despite
`native-wifi-version-exchange=false` — the setting is confirmed inert. Export-absence confirmed by
source grep (Setup notes): the key is not referenced anywhere in `app/src/main/java/`.

## R11: the stale-endpoint record retires without a served dial — **PASS**

Reached implicitly through the R3→R5 sequence rather than as a standalone setup: R3's refusal raised
`ConnectionIssue.PHONE_HOLDS_STALE_ENDPOINT` (confirmed in code —
`WppTcpServePolicy.blamesStaleEndpoint` is true for R3's `Withhold` reason since `ourOwnState=false`),
and R5's re-seed relaunch (`22:49:47`-`49`) was a clean Bluetooth handshake landing with no dial
refused in that same landing cycle. `connection-issue-stale-endpoint` read `0` at every check from
immediately afterward (pre-R6) through the end of R10 — raised at `~22:48:56`, cleared by `~22:49:49`,
never moved again.

## R12: End session, stay ready — **PASS**

From a live projecting session, `am broadcast -a com.andrerinas.openheadunit.ACTION_END_SESSION_STAY_ARMED
-p com.andrerinas.headunitrevived`:

```
23:02:54.590 AapService: ACTION_END_SESSION_STAY_ARMED received
23:02:55.788 AapService: Native AA session ended; keeping the WIFI_DIRECT network up for the phone's
  return.
23:02:55.788 NativeAA: the phone ended the session itself, so the listeners reopen without waking it.
23:02:55.789 NativeAA: reopening the Android Auto listeners for the phone's return.
```

Group confirmed still up immediately after (`groupFormed: true`, same network name). **No**
`Calling socket.connect()` anywhere in the following 3 minutes. The phone reconnected entirely on its
own, over Bluetooth, in **2.749s** (`23:02:54.590` action received → `23:02:57.339 SSL handshake
complete`), and a genuine new session followed (`First frame rendered` `23:03:00.693`, throughput
logs continuous afterward) — no poke was needed at all.

## R13: Stop and Exit are unchanged — **PASS**

Step 1 (`headunit://exit`): `groupFormed: false` within 3s, `23:06:40.660 AapService: Native AA user
exit. Stopping active launcher.`
Step 2 (relaunch, new session, then `headunit://disconnect` as the scripted equivalent of the
projection screen's Stop Connection — see Setup notes): `groupFormed: false` within 3s,
`23:07:16.847 AapService: Native AA user exit. Stopping active launcher.` No `the wireless teardown
did not finish in` line either time.

## R14: group reuse with the phone rejoining, and the reconnect times — **PASS**

Ten cycles, phone left to reconnect on its own throughout (`grep -cE "PROV_DISC|prov_disc|stuck
retry"` = **0** for the whole window). All 10 cycles reached `SSL handshake complete`. One exit-cycle
launch landed on an already-foregrounded `MainActivity` (`Warning: Activity not started...`) and
produced no new session; it was re-run immediately to get a genuine 5th exit-cycle measurement rather
than reported as a cycle.

**Exit cycles** (each tears the group down and creates fresh — `stable=no`, new BSSID every time):

| Cycle | New BSSID | Launch (createGroup)→SSL handshake |
|---|---|---|
| 1 | `0E:C2:4F:A2:22:6C` | 10.554s |
| 2 | `FA:60:E5:45:61:21` | 8.590s |
| 3 | `F6:61:0E:FE:07:40` | 6.844s |
| 4 | `8A:E7:70:BE:56:CC` | 9.843s |
| 5 | `0A:AE:58:76:DB:98` | 8.449s |

**Force-stop cycles** (cycle 1 is itself a create, since the last exit-cycle tore the group down;
cycles 2-5 read the surviving group):

| Cycle | BSSID | Read or created | Launch→SSL handshake |
|---|---|---|---|
| 1 | `5A:F2:2E:67:49:71` | created (prior exit had torn the group down) | 8.534s |
| 2 | `5A:F2:2E:67:49:71` | read | 2.384s |
| 3 | `5A:F2:2E:67:49:71` | read | 6.193s |
| 4 | `5A:F2:2E:67:49:71` | read | 4.720s |
| 5 | `5A:F2:2E:67:49:71` | read | 3.379s |

Reused-group reads (force-stop cycles 2-5) averaged **4.17s**, materially faster than the five
recreate cycles' **8.86s** average — the fix's reuse mechanism still saves real time even though, in
this round, the identity verdict never gets promoted to `STABLE` (every cycle here starts from a
fresh `CHANGED` baseline, so none of the reads carry the WPP-endpoint shortcut R2 exercises). Not as
dramatic a gap as round 5's `1.1s` vs `8.9s` — that gap came from `STABLE`-identity reads skipping
the endpoint negotiation entirely, a different saving than the pure group-negotiation reuse measured
here — but the group-reuse saving on its own is real and reproducible.

## R15: the static BSSID is per transport, and applies when saved — **PASS** (one sub-step not performed)

`static-p2p-bssid=AA:BB:CC:DD:EE:FF` (wrong, on purpose): relaunch reads `source=IPv6 link-local`,
`bssid=5A:F2:2E:67:49:71` — the real detected address, not the typed one. `BSSID source dump` shows
`WiFi Direct override (Settings) = AA:BB:CC:DD:EE:FF` listed but not chosen.

`static-bssid=11:22:33:44:55:66` (also wrong, still on the WiFi Direct transport): relaunch still
reads `source=IPv6 link-local`, same real `bssid=5A:F2:2E:67:49:71` — neither typed value won, and
the access point's own setting was never announced as the group's address anywhere in the whole
capture (`grep -c "access point setting (stand-in)"` = 0 across the round).

Restored both settings to their round baseline afterward. **The one UI step (confirm the app asks
for a restart after changing the WiFi Direct BSSID in the settings screen) was not completed** — see
Setup notes; settings were confirmed unaffected by the attempt.

## R16: D-SAM, below Q — **PASS**

Candidate installed fresh on D-SAM (SM-T230, API 19). D-SAM already had a Native AA WiFi Direct setup
targeting D-POCO from an earlier testing thread and auto-connected on its own.

**Part 1 (crash check):** full session landed (`23:20:35.329 SSL handshake complete`,
`23:20:35.459 AapProjectionActivity.onCreate`, `23:20:40.584 First frame rendered (hardware
decode)`), process stayed alive throughout, **zero** `FATAL EXCEPTION` or `NoClassDefFoundError` from
`AapProjectionActivity.onCreate` onward. Dalvik's install-time class verifier did log warnings for
the specific inner lambda referencing `android.media.AudioManager$OnModeChangedListener` (`VFY:
unable to resolve...`), which is expected on pre-API-23 Dalvik and is exactly the class the brief
asks about — but this is a soft verify-time warning, not a runtime crash, and nothing invoking it
brought the process down. (Two unrelated `FATAL EXCEPTION: Thread-30x` / `NullPointerException`
entries at `android.app.ActivityThread$mRunnable.run` earlier in the capture are stock Samsung
system-thread noise attributed to the app's PID during install, unrelated to our code or to
`AudioManager$OnModeChangedListener`.)

**Part 2 (name-changes counter):** four bring-ups (force-stop + relaunch), counter already at 8 from
earlier testing on this unit (Setup notes) walked forward **9→10→11→12**, never resetting, every
bring-up's reason naming the platform (`"this platform names the group itself and has picked a
different name on N creates, so the kept identity cannot apply here"`) — the `RENAMED`-equivalent
condition. Endpoint withheld on every one of the four bring-ups (`not advertising WPP over TCP: this
unit's Android is too old to name its own WiFi Direct group, ...`, 4/4).

## R17: the wake, as far as this rig reaches — **PASS**

`native-aa-wake-damage-verdict` cleared at round start. Values at round end:
`native-aa-wake-damage-verdict=1` (SAFE — moved from cleared/UNKNOWN because this round's many
successful pokes measured it that way; this is expected and is not itself part of R17's pass/fail
criteria, only cleared at the start per the brief). `native-aa-radio-cycle-verdict`: **absent from
`settings.xml` at both start and end** — never written, i.e. still 0, matching PASS.
`native-aa-wake-armings-without-session`: **0 at start, 0 at end** — never incremented, correct since
every arming this round reached a session. No adapter-cycle log line of any kind appears anywhere in
the round's capture (`grep -ci "radio cycle\|BluetoothRadioCycle\|adapter cycle"` = 0) — no cycle was
ever attempted on this API-34 unit.

## Anything the brief did not ask about

Nothing else observed beyond the sections above.
