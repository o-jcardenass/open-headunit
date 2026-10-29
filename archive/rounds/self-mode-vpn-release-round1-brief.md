# self-mode-vpn-release, round 1 brief

Publish name on `transfer/rig-rounds`: `self-mode-vpn-release-round1-brief.md`. Results go in
`self-mode-vpn-release-round1-results.md`, evidence in the release `rig-evidence-self-mode-vpn-release`.

## 1. Build and baseline

| Arm | Ref | SHA |
|---|---|---|
| Candidate | `fork/fix/self-mode-vpn-release` | `2a91ee336f64aa37fd4f9babb35f61a842380c26` |
| Baseline | `origin/main` | `145a0c762f0a87386ca63b54518ea5e861b290be` |

```bash
git fetch fork fix/self-mode-vpn-release && git fetch origin main
git checkout 2a91ee336f64aa37fd4f9babb35f61a842380c26    # candidate, then build
git checkout 145a0c762f0a87386ca63b54518ea5e861b290be    # baseline, then build
```

The candidate is two commits on the baseline. This is the first round of the thread, so no history
was rewritten.

- `25d9985d` Self Mode: release the dummy VPN once the session is up
- `2a91ee33` Self Mode: start no dummy VPN on the head unit server route

Both arms build `:app:assembleGithubDebug`. Copy each APK out of `apks/` as soon as it is built
(`build_hur.sh` deletes the previous one).

### R0. Gate

1. Run `th_wait 70`, start `th_watch $OUT/R0.thermal &`, and build each arm with `--max-workers=2`
   (thermal helpers from `ptr_lib.sh`, `projection-teardown-and-relays-round1-brief.md`).
2. Run `run_unit_tests.sh` on the candidate.
3. Check the DEX of each APK for the class the candidate adds:
   ```bash
   unzip -p <apk> 'classes*.dex' | strings | grep -c SelfLaunchRoutePolicy
   ```

**PASS:** 2744 tests and 0 failures, counted from the JUnit XML, with `SelfLaunchRoutePolicyTest`
and `DummyVpnPolicyTest` present. The two md5s differ. The DEX count is 1 or more on the candidate
and 0 on the baseline. A build or test failure stops the round.

## 2. What this is and why it exists

A user runs an old phone as a head unit in Self Mode, with no SIM, offline by default. The phone
sometimes joins a hotspot to sync music and maps. When the hotspot is joined after Open Headunit
started, no app on the phone reaches the internet. When the user disconnects the VPN by hand, the
session keeps running and every app goes online at once.

The cause is in the code, and the ticket's `diagnosis.md` has the detail:

- `HomeFragment.startSelfMode` starts the dummy VPN when `activeNetwork == null`.
- `DummyVpnService` adds a `0.0.0.0/0` route with `excludeSelf=false`, so the tun takes IPv4 from
  every app on the unit.
- Android routes a VPN's apps by UID rules ahead of any network joined later, so a later network
  carries nothing.
- On `main`, owner `SELF_MODE` is released only at session end, service destroy or the 120 s
  no-phone watchdog. The tun stays up for the whole drive.

`selfmode-playstore-route-round1-results.md` measured the two launch routes:

- **Android Auto 17.4 and later (`127.0.0.1:5277`)** forms a session offline with no VPN (R2, R3).
- **Android Auto below 17.4 (our `WirelessServer` on 5288)** needs the tun to launch offline: R8
  failed with no VPN, R8c connected in 2.6 s with it.

The candidate does two things:

1. **Commit 2:** on the 5277 route, Self Mode starts no dummy VPN. `HomeFragment` asks
   `SelfLauncherManager.installedPath` first and logs
   `HomeFragment: Device is offline; Android Auto 17.4+ connects over 127.0.0.1:5277, so no dummy VPN.`
2. **Commit 1:** on the legacy route the tun still comes up for the launch. `AapService.onConnected`
   releases it with reason `SELF_MODE_SESSION_LIVE`, with no delay. `onConnected` is the TCP accept,
   which is before SSL.

**The open risk is commit 1.** On the legacy route, Gearhead binds its socket to the Network it was
given, which can be the VPN's. Android can close a socket when its network goes away. If it does,
the release kills the session it was meant to free. Only D-HU on Android Auto 17.3 can answer this.

## 3. What is different about this round

- **The point of the round is R4** (the release on the legacy route, D-HU). **R2** (no tun on the
  5277 route, D-POCO) is the fix the user sees. R1 and R1L are the positive controls on `main`.
  R3P, R3L, R5P and R5L are regression guards. R2M is a second 5277 unit and is optional.
- **In Self Mode the head unit and the phone are one device.** Gearhead runs on the unit under
  test, so the unit's full `adb logcat` is also the phone capture. Do not tag-filter it. Each run
  grades at least one Gearhead line from it.
- **The internet probe runs as the app's own UID** through `run-as`. D-HU's adb shell is root, and
  a root shell can bypass a VPN's UID rules. The app's UID is inside the tun on `main`
  (`excludeSelf=false`), as every other app is. R0b calibrates the probe on every unit.
- **No rig unit is near API 30**, the user's level. D-POCO is API 35 and D-MOTO is API 34. Record
  the API level in each run.
- **D-HU must still run Android Auto 17.3.x.** R0b reads it. If D-HU reads 17.4 or later, mark R1L,
  R4, R3L and R5L UNTESTABLE (no legacy unit) and run the rest.
- **D-POCO's `:5277` server must be up, and nothing may force-stop Gearhead.** A force-stop drops
  the server and only a hand toggle in Android Auto's developer settings brings it back. R0b checks
  the port. The app stop between runs is `am force-stop $PKG` only.
- **D-POCO's radio levers drift between rounds.** One round found `cmd connectivity airplane-mode`
  reliable on it, a later round found it did not take the radios down. `offline` below uses
  airplane mode plus explicit `svc` disables, and every run checks `Active default network: none`
  before the launch. A WiFi self-revert after `svc wifi disable` is known on D-POCO; the check
  catches it.
- **The Gearhead teardown strings come from the 17.8 dex**, not from 17.3 or 17.5. Their absence on
  this rig is weak evidence. The session verdicts rest on our own `AapService: Disconnected.` count
  and the `Throughput over` lines. Report the Gearhead version of each unit in Setup notes.
- **The rig's audio settings are a deliberate worst case.** Do not change `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and
  record them.
- **Never run adb calls in parallel against one unit.** D-HU hard-reboots under sustained load; no
  run here adds load.

**Pre-registered outcomes, none of them a FAIL:**

- R1 or R1L with a probe count above 0 while the tun is up: the rig does not show the fault. The
  internet half of R2 (after R1) or R4 (after R1L) is then INCONCLUSIVE. Every other condition
  still grades.
- R2M is UNTESTABLE if `:5277` does not listen on D-MOTO after one hand toggle.
- R2's `AapService: releasing the dummy VPN (owner=SELF_MODE, reason=SELF_MODE_SESSION_LIVE)` line
  can print with no tun behind it. `SelfLauncherManager.adoptDummyVpn` adopts the owner whenever the
  flavor has a VPN. The next lines are then `VpnControl: Stopping DummyVpnService (GitHub Build)` and
  `VpnControl: DummyVpnService was not running`. That is not a FAIL; the tun count is the measure.

### Stages

| Stage | Plugged in | Runs, in order |
|---|---|---|
| A | D-POCO, D-HU | R0b on both, R1 and R5A (baseline), R2, R3P, R5P (candidate) |
| B | D-HU, D-POCO | R1L (baseline), R4, R3L, R5L (candidate) |
| C | D-MOTO | R2M (candidate), only if a person is there for the hand toggle |

During stage A, D-HU's app is stopped and its Bluetooth is off. During stage B, D-POCO is in
`offline` state (below) with Bluetooth off. Neither unit may reach the other's Android Auto.

## 4. Settings keys

Write with the app stopped (template §1). On D-HU, copy as root and `chown` to the app's uid
(`rig-quirks/units/D-HU.md`). Read every key back before the launch. Take a fresh backup first and
state the delta against it in Setup notes.

| Key | Element | Why |
|---|---|---|
| `auto-start-self-mode` | `<boolean name="auto-start-self-mode" value="true" />` | The only entry that reaches `HomeFragment.startSelfMode`, where the VPN decision is. `ACTION_START_SELF_MODE` goes to the service and skips it. |
| `auto-connect-delay-seconds` | `<int name="auto-connect-delay-seconds" value="0" />` | No wait before the launch. |
| `log-level` | `<int name="log-level" value="2" />` | INFO. Every app line in this round is INFO or WARN. |
| `auto-connect-last-session` | delete | Keep Self Mode the only auto-connect. |
| `auto-connect-single-usb` | delete | The same. |
| `keep-dummy-vpn-during-session` | delete (default false) | The `SESSION` owner must not put a tun up. |
| `video-profile-starvation-cap` | delete | Clear a cap left by earlier rounds. |
| `native-poke-all-paired` | `<boolean name="native-poke-all-paired" value="false" />` | D-HU only: no poke while Bluetooth is briefly on. |

No `allow-external-configuration` is needed: the candidate's `AutomationCommandPolicy.CONFIGURING`
does not list `ACTION_LOG_MARKER`.

Before each baseline run, check the VPN consent with `adb -s $U shell appops get $PKG ACTIVATE_VPN`.
If it does not read `allow`, run `adb -s $U shell appops set $PKG ACTIVATE_VPN allow`. A consent
dialog would block the launch.

## 5. Helpers

Save as `hur-wifi-test-scripts/svr_lib.sh` and source it after `ptr_lib.sh`'s thermal functions.
Set `U` to the unit under test and `RUN` to the run id before each run.

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
POCO=4f4027e9; HU=27870808938846; MOTO=ZY22GC3BM4
send() { a=$1; shift; adb -s "$U" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; }
CAP() { echo "$OUT/$RUN.txt"; }
cap_start() { adb -s "$U" logcat -G 16M; sleep 0.3
  for t in GH.DHUService CAR.SERVICE GH.WirelessFSM GH.WSR GH.GHLifetimeManager GH.WirelessNetRequest; do
    adb -s "$U" shell setprop log.tag.$t VERBOSE; sleep 0.3; done
  adb -s "$U" logcat -c && { stdbuf -oL adb -s "$U" logcat -v time > "$(CAP)" 2>&1 & CAPPID=$!; }; sleep 1; }
cap_stop() { kill $CAPPID; }
defnet() { adb -s "$U" shell dumpsys connectivity | grep -a -m1 'Active default network'; }
tuns() { adb -s "$U" shell ip -o link show | grep -ac ': tun'; }
# probe : 12 pings as the app's UID, about 60 s; prints how many got a reply
probe() { local ok=0 i; for i in $(seq 1 12); do
  adb -s "$U" shell run-as $PKG ping -c 1 -W 3 8.8.8.8 2>&1 | grep -aq ' 0% packet loss' && ok=$((ok+1)); sleep 2; done; echo $ok; }
port5277() { adb -s "$U" shell cat /proc/net/tcp /proc/net/tcp6 | grep -acE ':1495 [0-9A-F]+:[0-9A-F]+ 0A'; }
aaver() { adb -s "$U" shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName; }
# offline / join : phones (D-POCO, D-MOTO) and D-HU use different levers
offline() { if [ "$U" = "$HU" ]; then adb -s $HU shell svc wifi disable; sleep 0.3; adb -s $HU shell svc bluetooth disable
  else adb -s "$U" shell cmd connectivity airplane-mode enable; sleep 2; adb -s "$U" shell svc wifi disable; sleep 0.3
    adb -s "$U" shell svc data disable; sleep 0.3; adb -s "$U" shell svc bluetooth disable; fi; }
join() { if [ "$U" = "$HU" ]; then adb -s $HU shell svc wifi enable
  else adb -s "$U" shell cmd connectivity airplane-mode disable; sleep 2; adb -s "$U" shell svc wifi enable; sleep 0.3
    adb -s "$U" shell svc data enable; sleep 2; adb -s "$U" shell svc bluetooth disable; fi; }
waitoff() { local i; for i in $(seq 1 10); do defnet | grep -aq none && return 0; sleep 2; done; return 1; }   # 20 s
waitnet() { local i; for i in $(seq 1 30); do defnet | grep -aq none || return 0; sleep 2; done; return 1; }   # 60 s
waitline() { local i; for i in $(seq 1 "$1"); do grep -aqF -- "$2" "$(CAP)" && return 0; sleep 1; done; return 1; }
cnt() { grep -acF -- "$1" "$(CAP)"; }
ts() { grep -aF -- "$1" "$(CAP)" | head -1 | awk '{print $2}'; }
```

`join` on D-HU relies on the station rejoining its saved network. If `waitnet` returns 1 there, run
`hur-wifi-test-scripts/connect_hotspot.sh` for the station network, then `waitnet` again. A second
failure makes the run's internet half UNTESTABLE; record it.

### Clean start for every run

```bash
send ACTION_EXIT; sleep 3; adb -s "$U" shell am force-stop $PKG    # our app only, never Gearhead
# write the run's settings (section 4) and read them back
offline; waitoff; defnet; tuns; [ "$U" != "$HU" ] && port5277
send ACTION_QUERY_STATE     # record data=; commit must match the arm
```

The run starts only when `defnet` reads `none` and `tuns` reads 0. If `waitoff` fails, run `offline`
again once. A second failure is a setup failure: record it and re-run the run once.

## 6. The deciding lines

All app strings were checked with `grep -F -r` against `app/src` at `2a91ee33`. Every grep runs on
the run's own capture, `$OUT/$RUN.txt`, which is the unit's full logcat.

| Line | Source | Used for |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | every verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` | step markers |
| `Device is offline. Preparing Dummy VPN for Self Mode.` | `HomeFragment` | the VPN path was taken |
| `HomeFragment: Device is offline; Android Auto 17.4+ connects over 127.0.0.1:5277, so no dummy VPN.` | `HomeFragment` (candidate only) | commit 2 |
| `VPN permission already granted. Starting VPN service.` | `HomeFragment` | no consent dialog |
| `DummyVpnService: tun established` | `DummyVpnService` | a tun came up |
| `SelfMode: Installed AA version: ` | `SelfLauncherManager` | the route read |
| `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...` | `SelfLauncherManager` | 5277 route |
| `SelfMode: AA < 17.4 detected. Starting WirelessServer on 5288 and running legacy triggers...` | `SelfLauncherManager` | legacy route |
| `SelfMode: Launching AA Wireless Startup via Activity...` | `SelfLauncherLegacy` | legacy launch |
| `WirelessServer: Incoming connection detected from` | `WirelessServer` | legacy accept |
| `SSL handshake complete` | `AapSslContext` (INFO) | session up; one per session at INFO |
| `Throughput over ` | `VideoDecoder` (INFO, every 15 to 40 s) | session alive; read `rendered=N` |
| `AapService: releasing the dummy VPN (owner=` | `AapService.stopDummyVpn` | a release; the same line must carry `SELF_MODE_SESSION_LIVE` |
| `SELF_MODE_SESSION_LIVE` | `DummyVpnPolicy` | the new reason |
| `VpnControl: Stopping DummyVpnService (GitHub Build)` | `VpnControl` | the stop ran |
| `Dummy VPN stopped` | `DummyVpnService` | a real tun closed |
| `VpnControl: DummyVpnService was not running` | `VpnControl` | the stop found no tun |
| `AapService: Disconnected.` | `AapService` | session end; must be 0 in every hold |
| `SelfMode: nothing connected within` | `SelfLauncherManager` | launch deadline missed |
| `MainActivity finishing, resetting auto-start flag.` | `MainActivity` | R3's branch |

The release line renders as
`AapService: releasing the dummy VPN (owner=SELF_MODE, reason=SELF_MODE_SESSION_LIVE)`. Count it
with both literals:

```bash
grep -aF 'AapService: releasing the dummy VPN (owner=' "$(CAP)" | grep -acF 'SELF_MODE_SESSION_LIVE'
```

Gearhead lines (phone side, same capture). These are not in our source.

| Line | Seen before on | Used for |
|---|---|---|
| `Head unit connected` | 17.5 (`selfmode-playstore-route` R1 to R7) | 5277 accept |
| `startDuplexConnection` | 17.5 | 5277 duplex up |
| `Launch projection 127.0.0.1 5288` | 17.3 (R8, R8c) | legacy launch aimed at us |
| `Critical error`, `hard loss of network`, `WSEM#onProjectionNetworkLost`, `Lost network. lostNetwork=`, `WiFi network is lost`, `Socket failed due to VPN connection`, `Head unit disconnected`, `WIRELESS_WIFI_NETWORK_STATE_CHANGED_DISCONNECTED_NO_SOCKET_DISCONNECT` | 17.8 dex only | Gearhead teardown; each must be 0 inside a hold window |

Count the teardown set inside a window with:

```bash
win() { sed -n "/AutomationMarker: $1\$/,/AutomationMarker: $2\$/p" "$(CAP)"; }
for s in 'Critical error' 'hard loss of network' 'WSEM#onProjectionNetworkLost' 'Lost network. lostNetwork=' \
  'WiFi network is lost' 'Socket failed due to VPN connection' 'Head unit disconnected' \
  'WIRELESS_WIFI_NETWORK_STATE_CHANGED_DISCONNECTED_NO_SOCKET_DISCONNECT'; do
  echo "$s: $(win "$RUN-start" "$RUN-end" | grep -acF -- "$s")"; done
```

## 7. Runs

Every run: `th_gate` first, then the clean start (section 5), then the steps. Report `th_report $RUN`
in each section. Discard rule for this round: a second `SSL handshake complete` in a run that should
have one session. R3P and R3L have two by design.

### R0b. Unit calibration (no app session)

On each unit of the stage (`U=$POCO`, then `U=$HU`), with the app stopped:

1. Record `aaver`, `adb -s $U shell getprop ro.build.version.sdk`, and on D-POCO `port5277`.
2. Run `join; waitnet; defnet; probe`. Record the count as `P_on`.
3. Run `offline; waitoff; defnet; probe`. Record the count as `P_off`.

**PASS per unit:** `P_on` is 10 or more and `P_off` is 0. D-POCO also needs `versionName` 17.4 or
later and `port5277` 1 or more. D-HU needs `versionName` 17.3.x.

If `P_on` is below 10 on a unit, the probe or the join lever does not work there. Mark the internet
condition of every run on that unit UNTESTABLE and grade the rest. If D-POCO's `port5277` reads 0,
stop and ask a person to toggle "Start head unit server" in Android Auto's developer settings (hand
step: no adb route reaches that setting). If it still reads 0, stage A is UNTESTABLE.

### R1. Positive control, baseline, 5277 route (D-POCO)

`U=$POCO; RUN=R1`. Install the baseline (`adb install -r`), md5 check, `ACTION_QUERY_STATE` commit
`145a0c76`.

```bash
cap_start; mark R1-start
adb -s $U shell am start -n $MAIN
waitline 60 'SSL handshake complete'; sleep 5
mark R1-ssl; echo "tun_at_ssl=$(tuns)"
mark R1-join; join; waitnet; defnet; sleep 10
mark R1-probe; echo "P=$(probe)"; echo "tun_at_end=$(tuns)"
sleep 60; mark R1-end; cap_stop
```

**Control reproduces the fault when:** `DummyVpnService: tun established` = 1 and its timestamp is
before `SelfMode: AA 17.4+ detected...`; `tun_at_ssl` = 1; `tun_at_end` = 1; `P` = 0;
`SSL handshake complete` = 1; `AapService: Disconnected.` = 0; `Head unit connected` 1 or more
(phone side). If `P` is above 0 with `tun_at_end` = 1, R2's internet condition is INCONCLUSIVE.

### R5A. Baseline, online start, then the network drops and returns, 5277 route (D-POCO)

The reporter's Situation A: the unit is online when Self Mode starts, and the reporter sees a VPN.
After the hotspot drops and returns, no app goes online. The code starts no tun on an online start,
so this run records what `main` does. It has no PASS or FAIL. Run it straight after R1, on the baseline.

`U=$POCO; RUN=R5A`. Baseline, md5 check, commit `145a0c76`. After the clean start, run
`join; waitnet; defnet` before the capture.

```bash
cap_start; mark R5A-start
adb -s $U shell am start -n $MAIN
waitline 60 'SSL handshake complete'; sleep 5
mark R5A-ssl; echo "tun_at_ssl=$(tuns)"; echo "P1=$(probe)"
mark R5A-drop; offline; waitoff; defnet; sleep 20
mark R5A-rejoin; join; waitnet; defnet; sleep 10
mark R5A-probe; echo "P2=$(probe)"; echo "tun_at_end=$(tuns)"
sleep 30; mark R5A-end; cap_stop
```

**Record:** `tun_at_ssl`, `P1`, `tun_at_end`, `P2`; the count and the timestamp of each of
`Device is offline. Preparing Dummy VPN for Self Mode.`, `VpnControl: Starting DummyVpnService (GitHub Build`
and `DummyVpnService: tun established`; `SSL handshake complete`; `AapService: Disconnected.`; and the
number of `SelfMode: Installed AA version: ` lines (more than 1 means Self Mode started again).

**If any `tun established` line appears, or either tun read is 1:** an unknown start path exists. Paste
the 20 capture lines before the first `VpnControl: Starting DummyVpnService (GitHub Build` line into the
results. Do not stop the round.

### R2. Fix, 5277 route (D-POCO). Point of the round, with R4.

`U=$POCO; RUN=R2`. Install the candidate, md5 check, commit `2a91ee33`.

```bash
cap_start; mark R2-start
adb -s $U shell am start -n $MAIN
waitline 60 'SSL handshake complete'; sleep 5
mark R2-ssl; echo "tun_at_ssl=$(tuns)"
mark R2-join; join; waitnet; defnet; sleep 10
mark R2-probe; echo "P=$(probe)"; echo "tun_at_end=$(tuns)"
sleep 60; mark R2-end; cap_stop
```

**PASS when all hold:**

- `HomeFragment: Device is offline; Android Auto 17.4+ connects over 127.0.0.1:5277, so no dummy VPN.` = 1.
- `Device is offline. Preparing Dummy VPN for Self Mode.` = 0 and `DummyVpnService: tun established` = 0.
- `tun_at_ssl` = 0 and `tun_at_end` = 0.
- `SSL handshake complete` = 1 and `AapService: Disconnected.` = 0.
- `Throughput over ` lines between `R2-join` and `R2-end`: 2 or more, each with `rendered` above 0.
- Phone side: `Head unit connected` 1 or more; every Gearhead teardown string 0 between `R2-start` and `R2-end`.
- `P` is 6 or more.

**FAIL** on any `tun established` line, any tun at a read, or `P` = 0 while R1's control reproduced.
`P` from 1 to 5 is INCONCLUSIVE for the internet half; record it.

### R3P. Fix, reconnect, 5277 route (D-POCO)

`U=$POCO; RUN=R3P`. Candidate.

```bash
cap_start; mark R3P-start
adb -s $U shell am start -n $MAIN
waitline 60 'SSL handshake complete'; sleep 20
mark R3P-exit; send ACTION_EXIT; sleep 5
offline; waitoff; defnet
F=$(cnt 'MainActivity finishing, resetting auto-start flag.'); echo "finish_lines=$F"
[ "$F" -ge 1 ] || { adb -s $U shell am force-stop $PKG; echo branch=force-stop; }
mark R3P-relaunch; adb -s $U shell am start -n $MAIN
sleep 30; echo "tun_at_end=$(tuns)"; mark R3P-end; cap_stop
```

Record which branch ran (same process, or `force-stop`).

**PASS:** `SSL handshake complete` = 2, the second after `R3P-relaunch`; `Throughput over ` with
`rendered` above 0 after `R3P-relaunch`; `DummyVpnService: tun established` = 0; `tun_at_end` = 0;
`SelfMode: nothing connected within` = 0; `Head unit connected` 2 or more.

### R5P. Fix, online start, 5277 route (D-POCO)

`U=$POCO; RUN=R5P`. Candidate. After the clean start, run `join; waitnet; defnet` before the
capture.

```bash
cap_start; mark R5P-start
adb -s $U shell am start -n $MAIN
waitline 60 'SSL handshake complete'; sleep 10
mark R5P-probe; echo "P=$(probe)"; echo "tun_at_mid=$(tuns)"
mark R5P-drop; offline; waitoff; defnet; sleep 20
mark R5P-rejoin; join; waitnet; defnet; sleep 10
mark R5P-probe2; echo "P2=$(probe)"; echo "tun_at_end=$(tuns)"
sleep 30; mark R5P-end; cap_stop
```

**PASS:** both `Device is offline` lines = 0 (the `Preparing Dummy VPN` one and the `HomeFragment`
one); `DummyVpnService: tun established` = 0; `tun_at_mid` = 0 and `tun_at_end` = 0;
`SSL handshake complete` = 1; `AapService: Disconnected.` = 0; `P` 6 or more and `P2` 6 or more;
`Head unit connected` 1 or more. The drop and rejoin is the reporter's Situation A, compared with R5A.

End of stage A: `U=$POCO; send ACTION_EXIT; sleep 3; adb -s $POCO shell am force-stop $PKG; offline`.

### R1L. Positive control, baseline, legacy route (D-HU)

`U=$HU; RUN=R1L`. Install the baseline, md5 check, commit `145a0c76`. Steps as R1, with `R1L-`
markers.

**Control reproduces the fault when:** `DummyVpnService: tun established` = 1, before
`SelfMode: Launching AA Wireless Startup via Activity...`; `WirelessServer: Incoming connection
detected from` 1; `SSL handshake complete` = 1; `tun_at_ssl` = 1; `tun_at_end` = 1; `P` = 0;
`AapService: Disconnected.` = 0; `Launch projection 127.0.0.1 5288` 1 or more (phone side). If `P`
is above 0 with `tun_at_end` = 1, R4's internet condition is INCONCLUSIVE.

### R4. Fix, release at connect, legacy route (D-HU). Point of the round.

`U=$HU; RUN=R4`. Install the candidate, md5 check, commit `2a91ee33`.

```bash
cap_start; mark R4-start
adb -s $U shell am start -n $MAIN
waitline 60 'SSL handshake complete'; sleep 5
mark R4-ssl; echo "tun_at_ssl=$(tuns)"
sleep 55
mark R4-join; join; waitnet; defnet; sleep 10
mark R4-probe; echo "P=$(probe)"; echo "tun_at_probe=$(tuns)"
sleep 150; mark R4-end; cap_stop      # R4-ssl to R4-end is about 300 s
```

Record these timestamps: `tun established`, `SelfMode: Launching AA Wireless Startup via Activity...`,
`WirelessServer: Incoming connection detected from`, the release line, `Dummy VPN stopped`,
`SSL handshake complete`. Report `D_rel_ssl` = SSL minus release, in ms (negative if SSL came first).

**PASS when all hold:**

- `DummyVpnService: tun established` = 1, before `SelfMode: Launching AA Wireless Startup via Activity...`.
- The release count (section 6) = 1, after `WirelessServer: Incoming connection detected from`. Exactly 1: the second call site must log nothing once the first has released. A count of 2 is a FAIL.
- `Dummy VPN stopped` = 1, after the release line.
- `tun_at_ssl` = 0 and `tun_at_probe` = 0.
- `SSL handshake complete` = 1 and `AapService: Disconnected.` = 0 from `R4-start` to `R4-end`.
- `Throughput over ` lines between `R4-ssl` and `R4-end`: 6 or more, and 5 or more with `rendered` above 0.
- Phone side: `Launch projection 127.0.0.1 5288` 1 or more; every Gearhead teardown string 0 between `R4-start` and `R4-end`.
- `SelfMode: nothing connected within` = 0.
- `P` is 6 or more.

**FAIL (H3, fails commit 1):** an `AapService: Disconnected.` line, or any Gearhead teardown string,
within 30 s after the release line. Report the first such line with its timestamp and `D_rel_ssl`.
Do not repeat R4 after an H3 FAIL; run R3L and R5L anyway.

**FAIL (other):** any other condition above that is not met. `P` from 1 to 5 is INCONCLUSIVE for
the internet half.

### R3L. Fix, reconnect, legacy route (D-HU)

`U=$HU; RUN=R3L`. Candidate. Steps as R3P, with `R3L-` markers.

**PASS:** `DummyVpnService: tun established` = 2, the second after `R3L-relaunch`; the release
count = 2, the second after `R3L-relaunch`; `SSL handshake complete` = 2; `Throughput over ` with
`rendered` above 0 after `R3L-relaunch`; `AapService: Disconnected.` 1 or more between `R3L-exit`
and `R3L-relaunch` (the exit) and 0 after `R3L-relaunch`; `tun_at_end` = 0; `Launch projection 127.0.0.1 5288` 2 or more.

### R5L. Fix, online start, legacy route (D-HU)

`U=$HU; RUN=R5L`. Candidate. Steps as R5P, with `R5L-` markers.

**PASS:** both `Device is offline` lines = 0; `DummyVpnService: tun established` = 0; `tun_at_end`
= 0; `SSL handshake complete` = 1; `AapService: Disconnected.` = 0; `P` 6 or more;
`Launch projection 127.0.0.1 5288` 1 or more. A release line here is the adopt-with-no-tun case and
is not a FAIL.

End of stage B: `U=$HU; send ACTION_EXIT; sleep 3; adb -s $HU shell am force-stop $PKG`, then
`svc wifi enable` and `svc bluetooth enable` on D-HU, and restore `settings.xml` from the backup
on both units. Diff each against its backup.

### R2M. Fix, 5277 route, second unit (D-MOTO). Optional.

Run it only when a person can do the hand step. `U=$MOTO; RUN=R2M`. Candidate.

1. Hand step: toggle "Start head unit server" in Android Auto's developer settings on D-MOTO. No adb
   route reaches it, and it does not survive a forgotten car or a Gearhead restart.
2. `port5277` must read 1 or more. If not, R2M is UNTESTABLE.
3. Confirm D-MOTO is unlocked (it can be behind a PIN).
4. Steps and PASS as R2, with `R2M-` markers. There is no baseline arm on D-MOTO, so a `P` of 0 is
   graded against R1 only.

### Stop rule

- A run with a setup failure (wrong commit, `defnet` not `none` at the launch, a missing
  `AutomationReceiver:` line) is re-run once. A second setup failure makes it UNTESTABLE.
- R4 runs at most twice, and only the second attempt after a setup failure.
- No run is repeated to turn an INCONCLUSIVE into a PASS.
- R5A is a recording run. A tun found there never stops the round; report it and go on.

## 8. Do not re-run

- `selfmode-playstore-route` round 1, R2 and R3: the 5277 route forms a session offline with no VPN.
- The same round, R8 and R8c: the legacy route does not launch offline without the tun, and does
  with it. No run in this round asks for a legacy launch without the tun.

## 9. Report back

1. **R4:** the release count, `D_rel_ssl` in ms, `AapService: Disconnected.` count over the
   300 s hold, the `Throughput over ` count, and `P`.
2. **R2 against R1:** `tun_at_ssl`, `tun_at_end` and `P` for each.
3. **R4 against R1L:** `tun_at_probe` (or `tun_at_end`) and `P` for each.
4. **R5A against R5P:** `tun_at_ssl` (R5A) and `tun_at_mid` (R5P), `tun_at_end`, `P1`/`P`, `P2`, and the
   count of `VpnControl: Starting DummyVpnService (GitHub Build` in each. If R5A found a tun, the 20 lines.

Also give, in Setup notes: each unit's API level, Gearhead `versionName`, R0b's `P_on` and `P_off`,
and the R3 branch taken.
