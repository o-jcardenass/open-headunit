# wpp-endpoint-depoison — round 5 results

**Candidate:** `fork/fix/wpp-endpoint-depoison` @ `d49ce471f` — `8e6c27db` (fork tip going into this
round) with the rig's own `6b4eb0d9d` ("Native AA: read a surviving group instead of always tearing
it down") rebased onto it and pushed at the start of this round (§0 below); `d49ce471f` is the
resulting tip and the one commit's diff, confirmed against `8e6c27dbf..d49ce471f`.
**Baseline:** none. This thread has no A/B.
**APK md5:** `cb8ac89794b49cc85c1e7209f0e0585c`
**Unit:** D-HU (UNISOC MT50, Android 14). **Phone:** D-POCO (POCO X3 NFC), Gearhead
`17.8.163804-release.daily`
**Date:** 2026-09-20
**Evidence:** `rig-evidence-wpp-endpoint-depoison-round5` (release asset on `fork`), sha256
`711f118c8fde4311613f62d4919ded846f0ff25cffc16d31b8c8198290b4055f` — `round-wpp-endpoint-depoison-r5/`
(`hu.txt`/`poco.txt`, R0a-R5) and `round-wpp-endpoint-depoison-r5-r6/` (`hu.txt`, the R6 hotspot arm)

## Setup notes

- **`6b4eb0d9d` was rebased onto `8e6c27db` and pushed to `fork/fix/wpp-endpoint-depoison` before
  R0**, per the brief's §0. Clean rebase, no conflicts; new tip `d49ce471f`. This closes out the
  brief's stated risk of that commit being lost to a `git checkout` on this checkout.
- **D-HU's WiFi radio was off at the round's start** (`settings get global wifi_on` = `0`), which the
  brief did not anticipate and which the log surfaced as `WifiDirectManager: WiFi is off and this
  Android does not let an app switch it on.` `svc wifi enable` was run to bring it up. The first
  launch attempt afterward exhausted all 5 `createQuietGroup` retries against `BUSY (System is busy,
  retry needed)` on the 5 GHz band before the radio had actually settled; a force-stop and relaunch
  ~12 seconds after enabling WiFi succeeded on the first attempt. Any round that starts from a fully
  powered-off WiFi radio on this unit should expect to eat one failed launch this way.
- **The brief names the wrong key for the passphrase half of the yardstick pair.** §2 asks for
  `wifi-direct-passphrase` to be cleared once at the start; the actual key
  (`Settings.kt:653`, `wifiDirectGroupIdentity`) is `wifi-direct-group-passphrase`. Functionally
  inert here — `wifiDirectGroupIdentity`'s getter (`Settings.kt:646-648`) already returns `null` once
  `wifi-direct-group-name` alone is cleared, since both halves are required — but the stray value
  (`hoEqpesgVzVd`, left over from a previous thread) was still sitting in `settings.xml` until caught
  and cleared with a second one-off script. A future brief for this thread should say
  `wifi-direct-group-passphrase`.
- **The named phone-side instrument for field 5 does not exist on this phone's Gearhead build.**
  `Won't persist Wifi Configuration (%s) since access point is DYNAMIC` never appears anywhere in
  `poco.txt`. What this build (`17.8.163804-release.daily`) actually logs on *every* handshake,
  DYNAMIC or STATIC alike, is the generic pair `Not persisting Wi-Fi configuration.` /
  `Wi-Fi frequency is not persisted: config is removed.` (`GH.WIRELESS.SETUP`) — confirmed present on
  both R1 (DYNAMIC) at `19:55:42.255` and R2 (STATIC) at `19:58:19.349`/`19:58:19.990`, so it does not
  discriminate field 5's value at all and cannot be used as the brief intended. The signal that
  *does* discriminate cleanly, and is what R1/R2 are graded on below, is whether
  `GH.WPP.TCP: Trying to start WPP on TCP with configuration: ...` appears on the phone's next
  handshake — absent after R1, present with this round's own SSID/BSSID after R2. Field 5 is landing;
  it just is not provable through the string the brief named.
- **R3's ten-minute wait was unnecessary and was stopped mid-wait.** The phone re-dialled and was
  refused **0.7 seconds** after the head unit relaunched (`19:59:26.642` listening →
  `19:59:27.400` rejected), not after ten minutes — the phone never lost its WiFi association across
  the force-stop, so it dialled the very first chance it had rather than on some later retry.
  Caught and the sleep aborted after the fact; the log already fully captured every line R3 grades
  on before the wait began.
- **No `settings.xml` backup was taken at the round's start**, against the standing rule in
  `TESTING-TEMPLATE.md` §7a. No diff-against-a-fresh-backup was done either. The settings state this
  round left behind is recorded in the R6 and closing paragraphs below in place of a diff.
- **`headunit://connect` did nothing in R6.** After `headunit://disconnect` (which worked cleanly),
  `am start -a android.intent.action.VIEW -d "headunit://connect"` was received
  (`AutomationActivity.onCreate`) but triggered no reconnection attempt — consistent with the
  preceding disconnect's own `AapService: User exit cooldown active for 5000ms` /
  `User exit with wirelessServer active. Not restarting discovery.` lines. Reconnect was done the
  same way as every other cycle in this round instead: `am force-stop` then relaunch. A first
  `am start -n .../MainActivity` alone was also a no-op (`Warning: Activity not started, intent has
  been delivered to currently running top-most instance.`) since `MainActivity` was still on top.
- **Scripts used, unchanged:** `build_hur.sh`, `run_unit_tests.sh`, `install_and_launch.sh`
  (`SKIP_BUILD=1` after the first build), `set_pref.sh` (nine settings across the round),
  `forget_car_gearhead.sh` (twice). Two one-off on-device clear scripts (the five stale identity
  keys, and separately the mis-named stray passphrase key above) were pushed the same way
  `set_pref.sh` does and not saved to the shared directory, being one-time-per-round actions. No
  script was added to `hur-wifi-test-scripts/`.
- `native-ap-transport` was left at `0` (WiFi Direct, its R0a-R5 value) at the end of the round,
  since there was no backup to restore to and R0a-R5 is this thread's primary arm.

## R0: build gate — **PASS**

`compileGithubDebugKotlin` and `testGithubDebugUnitTest` both clean via `build_hur.sh` /
`run_unit_tests.sh`. **2206 tests, 0 failures, 0 errors, 0 skipped.** Tip `d49ce471f`, contains both
`8e6c27db` and the rebased `6b4eb0d9d`. APK md5 `cb8ac89794b49cc85c1e7209f0e0585c`.

| Suite | Expected | Measured |
|---|---|---|
| `MacAddressOriginPolicyTest` | 6 | 6 |
| `GroupIdentityStabilityPolicyTest` | 25 | 25 |
| `WppEndpointPolicyTest` | 14 | 14 |
| `WppMessagesTest` | 19 | 19 |
| `WppTcpServePolicyTest` | 9 | 9 |
| `P2pBssidSourcePolicyTest` | (none given) | 10 |
| `P2pInterfaceNamePolicyTest` | (none given) | 5 |

## R0a: the address gate — **PASS**

First successful launch (second attempt, after the WiFi-off recovery above), pid 4758:

```
19:55:32.237 WifiDirectManager: Initial BSSID from IPv6 link-local: 6A:1C:E8:84:6A:33
19:55:32.246 WifiDirectManager: group identity ssid=DIRECT-JR-MT50YT610E4GFPSLU persistent=yes
  (netId 8) asked=persistent matchesRequest=yes bssid=6A:1C:E8:84:6A:33 address=generated
  stable=no (first group under this name, and 6A:1C:E8:84:6A:33 is generated rather than this
  interface's own, so the next create moves it) source=IPv6 link-local
```

Source reads `IPv6 link-local` on both lines, and `address=generated` is present, as expected for a
first bring-up under `8e6c27db`.

## R1: the phone is told not to keep a moving network — **PASS**

Head unit, first handshake (verdict `CHANGED`, first bring-up):

```
19:55:33.302 NativeAaHandshakeManager: not advertising WPP over TCP: this unit gives its WiFi
  Direct group a new address on every create, and the phone would keep dialling the one it
  stored. Withholding one does not clear one the phone already has: ...
```

Phone: no `Trying to start WPP on TCP with configuration` line follows anywhere in the capture up to
this point (confirmed absent for the whole file). The generic `Not persisting Wi-Fi configuration.`
does appear at `19:55:42.255`, but see Setup notes — it is not the discriminating signal the brief
named, since it also appears on R2's STATIC handshake below.

## R2: the phone is told to keep one that will not move — **PASS**

`am force-stop`, group survived (`dumpsys wifip2p`: `groupFormed: true`,
`mGroup network: DIRECT-JR-MT50YT610E4GFPSLU`). Relaunch:

```
19:58:11.737 WifiDirectManager: a group named DIRECT-JR-MT50YT610E4GFPSLU is already up from
  before this bring-up; reading it instead of tearing it down.
19:58:11.840 WifiDirectManager: group identity ssid=DIRECT-JR-MT50YT610E4GFPSLU ... bssid=
  6A:1C:E8:84:6A:33 address=generated stable=yes (same name and same BSSID as the last group)
  source=IPv6 link-local
19:58:16.898 NativeAaHandshakeManager: advertising WPP over TCP at 192.168.49.1:5299
```

Same BSSID as R1. Phone, `19:58:19.986`:

```
GH.WPP.TCP: Trying to start WPP on TCP with configuration: WifiProjectionProtocolOnTcpConfiguration
  (wifiConfiguration=WifiConfiguration(ssid=DIRECT-JR-MT50YT610E4GFPSLU, bssid=6A:1C:E8:84:6A:33,
  securityMode=WPA2_PERSONAL, ...), ipAddress=192.168.49.1, port=5299)
```

Carries this round's SSID and BSSID, as required. Session landed
(`SSL handshake complete` `19:58:18.013`).

## R3: the measurement — **PASS**

Cleared only `wifi-direct-last-group-ssid` / `wifi-direct-last-group-bssid`, left the group and
every other setting alone. `am force-stop` (group confirmed still up), relaunch:

```
19:59:26.746 WifiDirectManager: a group named DIRECT-JR-MT50YT610E4GFPSLU is already up from
  before this bring-up; reading it instead of tearing it down.
19:59:26.868 WifiDirectManager: group identity ... bssid=6A:1C:E8:84:6A:33 address=generated
  stable=no (first group under this name, ...) source=IPv6 link-local
19:59:28.212 NativeAaHandshakeManager: not advertising WPP over TCP: ...
```

Same BSSID as R2, group read not recreated, but with no yardstick the verdict is `CHANGED` and the
endpoint is withheld, exactly as predicted. The phone still holds the R2 endpoint and dialled almost
immediately (see Setup notes on the ten-minute wait):

```
19:59:27.327 WppTcpServer: connection from 192.168.49.249
19:59:27.397 WppTcpServer: TLS handshake complete with 192.168.49.249 ...
19:59:27.400 WppTcpServer.refuse: rejecting this dial so the phone drops our endpoint and goes
  back to Bluetooth: this unit gives its WiFi Direct group a new address on every create, ...
19:59:27.424 WppTcpServer.send: [TX] wrote type 10 (2 bytes)
```

Phone:

```
19:59:29.126 GH.WIRELESS.SETUP: Handling WifiConnectionRejection with reason:
  CONNECTION_REJECTION_REASON_INVALID_SETUP_TOKEN
19:59:41.290 GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit,
  will not start WPP on TCP.
```

The "no configuration found" line repeats roughly every 5.5s from `19:59:41` through at least
`20:00:30` (ten occurrences grepped) — the phone dropped the stored config and stopped trying WPP.
An ordinary Bluetooth-carried Android Auto session ran throughout this window (`QC2Buf`/
`PipelineWatcher` decode activity from `19:59:41` on), so the repair works on 17.8: this is the run
this thread has been trying to reach for four rounds, and it landed clean, not `PARTIAL`.

## R4: the stranding guard — **Not reached**

Only one dial reached the head unit's `WppTcpServer` in the entire round (R3's, above), and it
arrived with the listener open — no `not serving this dial` line appears anywhere in the capture.
R3 is the run that matters, per the brief's own guidance for this case.

## R5: the group reuse, with the phone in it — **PASS**

Five `headunit://exit` + relaunch cycles, then five `am force-stop` + relaunch cycles, phone left to
reconnect on its own each time (not touched otherwise).

**Exit cycles** (each one tears the group down and creates a fresh one — expected):

| Cycle | New BSSID | Read or created | Launch→SSL handshake |
|---|---|---|---|
| 1 | `C6:30:A1:7C:A3:80` | created, `stable=no` | 9.52s |
| 2 | `CE:A5:DB:7F:99:71` | created, `stable=no` | 8.91s |
| 3 | `CA:9A:A0:79:9A:DC` | created, `stable=no` | 8.89s |
| 4 | `C2:2C:FF:4C:99:2F` | created, `stable=no` | 9.14s |
| 5 | `3A:EC:E2:B1:C6:5D` | created, `stable=no` | 9.37s |

**Force-stop cycles** (each one reads the existing group without recreating it — expected):

| Cycle | BSSID | Read or created | Launch→SSL handshake |
|---|---|---|---|
| 1 | `3A:EC:E2:B1:C6:5D` | read, `stable=yes` | 6.30s |
| 2 | `3A:EC:E2:B1:C6:5D` | read, `stable=yes` | 1.12s |
| 3 | `3A:EC:E2:B1:C6:5D` | read, `stable=yes` | 1.13s |
| 4 | `3A:EC:E2:B1:C6:5D` | read, `stable=yes` | 1.11s |
| 5 | `3A:EC:E2:B1:C6:5D` | read, `stable=yes` | 1.13s |

The phone rejoined and reached `SSL handshake complete` on all 10 cycles (times above are from the
log's own `createQuietGroup`/`already up from before this bring-up` line to
`AapSslContext.performHandshake: SSL handshake complete`, not from the adb command issue time).
Force-stop cycle 1's 6.30s is an outlier against cycles 2-5's ~1.1s; not investigated further since
every cycle still connected well inside the round's own pacing.

`grep -cE "PROV_DISC|prov_disc|stuck retry"` = **0**, both for the R5 window alone and for the whole
capture.

**A real bug surfaced by this run, outside anything the brief asked to check** — see its own section
below.

## R6: the hotspot arm — **PASS** (no-regression only, per the brief's own caveat)

`native-ap-transport=1`, `hotspot-ssid=Navegadortz2`, `hotspot-password=12345678`,
`hotspot-interface=wlan2`, `auto-enable-hotspot=false`. Vehicle forgotten first. AP started by hand:

```
$ adb shell cmd wifi start-softap Navegadortz2 wpa2 12345678 -b 5
SoftApInfo{bandwidth=4, frequency=5220, bssid=00:27:15:43:06:6a, ...}
```

5220 MHz on `wlan2`, BSSID matches `static-bssid` and is globally unique.

```
20:09:45.506 WifiLauncherNative: access point BSSID 00:27:15:43:06:6A is factory
```

First connect: `SSL handshake complete` `20:09:56.524`, `First frame rendered (hardware decode)`
`20:09:58.257` — a picture. Disconnected (`headunit://disconnect`, clean), reconnected via
force-stop + relaunch (see Setup notes on `headunit://connect`): second connect
`SSL handshake complete` `20:11:56.260`, `First frame rendered` `20:11:57.964` — a picture again,
and `access point BSSID 00:27:15:43:06:6A is factory` logged again on this second bring-up too.
`grep -c "rejecting this dial"` = **0** across the whole R6 capture.

As the brief notes, D-HU's own AP address is globally unique, so this only confirms the changed
identity-check path does not regress the old hotspot behaviour — it is not evidence for the
generated-AP case the change exists for.

## R7: the setting split — **PASS**

Across the whole R0a-R5 capture: `source=static override` = 0, `source=access point setting
(stand-in)` = 0, `nothing on this device reported the group's own address` = 0. Every `group
identity` line's `source=` was either `IPv6 link-local` or `lastKnownBssid cache`, and every
announced BSSID under a given source was internally consistent with the group it described.
`static-bssid=00:27:15:43:06:6a` was confirmed still set at the end of R5, unchanged since it was
written before R0a.

## A bug the brief did not ask about: the stale-endpoint banner never clears once R3's fix works

`ConnectionIssue.PHONE_HOLDS_STALE_ENDPOINT` (`utils/ConnectionIssues.kt:113`) is the standing-issue
record behind the head unit's home-screen banner: *"Your phone keeps trying to reach this unit at a
network address it was given earlier..."*. It is raised once by `WppTcpServer.refuse()`
(`WppTcpServer.kt:254`, the same call R3 above exercises) and is cleared in exactly one place:
`WppTcpServer.kt:215`, inside `handleConnection`'s success branch — reached only when a **served**
WPP-over-TCP dial arrives.

R3's rejection does its job perfectly: the phone drops the stored WPP config and, from then on,
reconnects over ordinary Bluetooth (as R5's ten clean cycles above show). But that is exactly the
condition under which `WppTcpServer.handleConnection` is never entered again — the phone has nothing
left to dial. So the one code path that clears the banner can never run again after the fix has
actually worked, and the banner is stuck permanently "on".

Confirmed live, not just from the code: after R5's five `headunit://exit` and five `am force-stop`
cycles all reconnected cleanly, a `uiautomator dump` of D-HU's home screen still showed the banner
text verbatim, and `shared_prefs/settings.xml` still carried
`connection-issue-stale-endpoint value="1789952796287"` (R3's own rejection timestamp,
`19:59:27.4` on this unit's clock, unchanged since). Nothing in the ten R5 cycles that followed moved
it.

This is a correctness bug in the banner's own lifecycle, separate from anything R0-R7 above grade,
and worth its own fix: either clear it on a successful ordinary-Bluetooth session landing (which is
the actual proof the phone let go), or on a time-based expiry, rather than only on a WPP dial that
the fix's own success makes unreachable.

## Anything else the brief did not ask about

Nothing else observed beyond the sections above.
