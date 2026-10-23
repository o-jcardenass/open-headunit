# self-mode-vpn-release, round 2 brief

Publish name on `transfer/rig-rounds`: `self-mode-vpn-release-round2-brief.md`. Results go in
`self-mode-vpn-release-round2-results.md`. Evidence goes in the existing release
`rig-evidence-self-mode-vpn-release` as the asset `self-mode-vpn-release-round2-captures.zip`.

## 1. Build and baseline

| Arm | Ref | SHA |
|---|---|---|
| Candidate | `fork/fix/self-mode-vpn-release` | `2a91ee336f64aa37fd4f9babb35f61a842380c26` |

```bash
git fetch fork fix/self-mode-vpn-release
git checkout 2a91ee336f64aa37fd4f9babb35f61a842380c26
```

The tip is the same as in round 1. No history was rewritten. The candidate is two commits on
`origin/main` @ `145a0c762f0a87386ca63b54518ea5e861b290be`:

- `25d9985d` Self Mode: release the dummy VPN once the session is up
- `2a91ee33` Self Mode: start no dummy VPN on the head unit server route

**No baseline build this round.** Round 1's R5A already measured `main` on an online start.

### R0. Gate (identity only)

The code did not change, so the unit tests do not run again (round 1: 2744 tests, 0 failures).

1. If round 1's candidate APK is still on the host and its md5 reads
   `50f8137b85adf64b917c8e9274c70da0`, use it. If not, build `:app:assembleGithubDebug` at the SHA
   above with `--max-workers=2` after `th_wait 70`, and copy the APK out of `apks/` at once.
2. Check the DEX for the class the candidate adds:
   ```bash
   unzip -p <apk> 'classes*.dex' | strings | grep -c SelfLaunchRoutePolicy
   ```
3. After each install, send `ACTION_QUERY_STATE` and read `commit` from the `data=` line.

**PASS:** the DEX count is 1 or more, and every install reads `"commit":"2a91ee336f64"`. A failure
stops the round.

## 2. What this is and why it exists

A user runs an old phone as a head unit in Self Mode, offline by default. When the phone joins a
hotspot after Self Mode started, no app on the phone reaches the internet. The cause is our dummy
VPN: its `0.0.0.0/0` tun takes IPv4 from every app, and on `main` it stays up for the whole drive.
The ticket's `diagnosis.md` has the detail.

The candidate starts no tun on the Android Auto 17.4+ route (`127.0.0.1:5277`). On the legacy
route (below 17.4) it releases the tun in `AapService.onConnected`.

**Round 1 proved the fix** (`self-mode-vpn-release-round1-results.md`):

- R2 (5277 route, offline start, late join): no tun at any read, probe 12 against the control's 0.
- R4 (legacy route, the point of round 1): the release fired once, 512 ms before SSL. The session
  then ran 4 min 10 s with 0 `AapService: Disconnected.` and 49 render lines. H3 is closed.
- R3P and R3L (reconnect) passed on both routes.

**Round 1 graded R5P and R5L FAIL on one condition only:** `AapService: Disconnected.` had to be 0,
and each read 1. Every other R5 condition held (no tun, `P` = 12, `P2` = 12, one SSL).

**The disconnect is our own app, on both arms, and not the candidate.** The round 1 captures show
the sequence, 0.4 to 0.8 s after each `-drop` marker, in R5A (baseline), R5P and R5L:

```
AapService.maybeTearDownBeforeLinkGoes | AapService: WIFI_STATION_DISABLING with a live session ...
AapService: session state disconnected (link_lost)
AapTransport stopping and sending byebye (USER_SELECTION)
AapService: Disconnected. wasPlayingBeforeDisconnect=false
```

`svc wifi disable` (and airplane mode, which also disables WiFi) raises `WIFI_STATE_DISABLING`.
`LinkLossTeardownPolicy` then closes the session, although a Self Mode session runs over loopback
and not over WiFi. That is a separate defect on `main`. It is out of scope for this branch, and
this round does not grade it. The reporter's own drop is a hotspot that goes away, which does not
disable the WiFi radio and does not raise that trigger.

**This round re-runs R5 on the plan's own conditions:** an online start gives no VPN, a session
forms and stays up, and apps reach the internet. There is no network drop in any graded run.

## 3. What is different about this round

- **The point of the round is R5P2 and R5L2.** They replace round 1's R5P and R5L. R2M (a second
  5277 unit) is optional, as in round 1.
- **R5 is a regression guard, not a fix test.** `main` already starts no tun on an online start
  (round 1 R5A). So a PASS here looks the same whether or not the candidate changed anything. What
  the run proves is that the candidate's new route check and its release do not start a tun or end a
  session on an online start. The `Throughput over ` count proves that the session was alive across
  the hold, so a `Disconnected.` count of 0 means something.
- **Do not use `svc wifi disable`, `cmd connectivity airplane-mode enable`, or the `offline` helper
  between the launch and the `-end` marker of any run.** Each one ends the session through the
  link-loss teardown above.
- **In Self Mode the head unit and the phone are one device.** The unit's full `adb logcat` is also
  the phone capture. Do not tag-filter it. Each run grades at least one Gearhead line from it.
- **Use round 1's `hur-wifi-test-scripts/svr_lib.sh` as it was left.** It carries three fixes to
  round 1's helpers: `port5277` greps `:149D`, `tuns` reads `/sys/class/net` for `^tun[0-9]`, and
  `uidstate` prints `procState` and `effective` for the app's UID. Section 5 lists the functions
  this round calls.
- **The probe is valid only while the app's UID is not background-blocked.** Run it only during a
  session, with the projection in front, and record `uidstate` beside it. A probe whose `uidstate`
  does not read `effective=NONE` is INCONCLUSIVE.
- **D-HU must still run Android Auto 17.3.x** and keep WiFi auto connect on for `Pegue Cdesta`.
  Section 5's precheck reads both. If D-HU reads 17.4 or later, R5L2 is UNTESTABLE (no legacy unit).
- **D-POCO's `:5277` server must be up, and nothing may force-stop Gearhead.** The app stop between
  runs is `am force-stop $PKG` only.
- **The rig's audio settings are a deliberate worst case.** Do not change `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back and
  record them.
- **Never run adb calls in parallel against one unit.**

**Pre-registered outcomes, none of them a FAIL:**

- An `AapService: releasing the dummy VPN (owner=SELF_MODE, reason=SELF_MODE_SESSION_LIVE)` line
  followed by `VpnControl: DummyVpnService was not running` is expected on both units.
  `SelfLauncherManager.adoptDummyVpn` adopts the owner whenever the flavor has a VPN. The tun count
  is the measure.
- `SelfMode: Installed AA version: ` reads 2 per launch on the candidate (the new route check in
  `HomeFragment`, then the launch). It is not a restart.

### Stages

| Stage | Plugged in | Runs, in order |
|---|---|---|
| A | D-POCO, D-HU | precheck on both, R5P2 (D-POCO), R5L2 (D-HU) |
| B | D-MOTO | R2M (optional), only if a person is there for the hand step |

During R5P2, D-HU's app is stopped and its Bluetooth is off. During R5L2, D-POCO's app is stopped
and D-POCO is offline (`U=$POCO; offline`) with Bluetooth off. Neither unit may reach the other's
Android Auto.

## 4. Settings keys

Write with the app stopped (template §1). On D-HU, copy as root, then `chown u0_a176:u0_a176` and
`chmod 660` (`rig-quirks/units/D-HU.md`). Take a fresh backup of each unit first, and state the delta
against it in Setup notes. Read every key back before the launch. `svr_mkprefs.py` and
`svr_apply.sh` from round 1 do this; use them.

| Key | Element | Why |
|---|---|---|
| `auto-start-self-mode` | `<boolean name="auto-start-self-mode" value="true" />` | The only entry that reaches `HomeFragment.startSelfMode`, where the VPN decision is. |
| `auto-connect-delay-seconds` | `<int name="auto-connect-delay-seconds" value="0" />` | No wait before the launch. |
| `log-level` | `<int name="log-level" value="2" />` | INFO. Every app line in this round is INFO or WARN. |
| `connection-modes` | `<set name="connection-modes"><string>self</string></set>` | Self Mode only. Replace the whole set; do not use a line-scoped delete (template §1). |
| `auto-connect-last-session` | delete | Keep Self Mode the only auto-connect. |
| `auto-connect-single-usb` | delete | The same. |
| `keep-dummy-vpn-during-session` | delete (default false) | The `SESSION` owner must not put a tun up. |
| `video-profile-starvation-cap` | delete | Clear a cap left by earlier rounds. |
| `native-poke-all-paired` | `<boolean name="native-poke-all-paired" value="false" />` | D-HU only: no poke. |

No `allow-external-configuration` is needed: the candidate's `AutomationCommandPolicy.CONFIGURING`
does not list `ACTION_LOG_MARKER`.

Read `adb -s $U shell appops get $PKG ACTIVATE_VPN` on each unit and record it. Do not change it.
The candidate starts no VPN on an online start, so a consent dialog in a run is itself a finding.

## 5. Helpers

Source `hur-wifi-test-scripts/svr_lib.sh` (round 1) after `ptr_lib.sh`'s thermal functions. Set `U`
to the unit under test and `RUN` to the run id before each run. The file defines `PKG`, `RX`,
`MAIN`, `POCO`, `HU` and `MOTO` as in round 1's brief. This round calls these functions, and nothing
else from the file:

| Function | Does |
|---|---|
| `send <ACTION> [extras]` | `am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.<ACTION>` on `$U` |
| `mark <label>` | `send ACTION_LOG_MARKER --es text <label>`, then `sleep 0.3` |
| `cap_start`, `cap_stop` | full logcat of `$U` into `$OUT/$RUN.txt`, with `logcat -G 16M` and `logcat -c` first |
| `defnet` | the `Active default network` line |
| `tuns` | count of `tun[0-9]` in `/sys/class/net` |
| `uidstate` | `uid=<uid> procState=<state> effective=<rules>` for `$PKG` |
| `probe` | 12 pings to `8.8.8.8` as the app's UID over about 60 s; prints the reply count |
| `port5277` | listeners on `:149D` |
| `aaver` | Gearhead `versionName` |
| `offline`, `join`, `waitoff`, `waitnet` | radio levers and waits (D-HU and the phones use different levers) |
| `waitline <s> <string>` | wait up to `<s>` seconds for `<string>` in the capture |
| `cnt <string>` | `grep -acF` of `<string>` in the capture |

Add this window helper if `svr_lib.sh` does not have it:

```bash
win() { sed -n "/AutomationMarker: $1\$/,/AutomationMarker: $2\$/p" "$OUT/$RUN.txt"; }
wcnt() { win "$1" "$2" | grep -acF -- "$3"; }
```

### Precheck (once per unit, app stopped)

```bash
U=$POCO; aaver; adb -s $U shell getprop ro.build.version.sdk; port5277
U=$HU;   aaver; adb -s $U shell getprop ro.build.version.sdk
```

D-POCO needs `versionName` 17.4 or later and `port5277` 1 or more. If `port5277` reads 0, ask a
person to toggle "Start head unit server" in Android Auto's developer settings (hand step: no adb
route reaches it), then read it again. A second 0 makes R5P2 UNTESTABLE. D-HU needs `versionName`
17.3.x.

### Clean start for every run (online)

```bash
send ACTION_EXIT; sleep 3; adb -s "$U" shell am force-stop $PKG    # our app only, never Gearhead
# write the run's settings (section 4) and read them back
join; waitnet; defnet; tuns; [ "$U" = "$POCO" ] && port5277
adb -s "$U" shell dumpsys window | grep -a mCurrentFocus             # expect the launcher
send ACTION_QUERY_STATE     # record data=; commit must read 2a91ee336f64
```

The run starts only when `defnet` reads a network (not `none`) and `tuns` reads 0. If `waitnet`
returns 1 on D-HU, run `hur-wifi-test-scripts/connect_hotspot.sh` for the station network and then
`waitnet` again. A second failure is a setup failure.

## 6. The deciding lines

Every app string below matched `grep -F -r` in `app/src` at `2a91ee33`. Every grep runs on the run's
own capture, `$OUT/$RUN.txt`, inside the window `AutomationMarker: <RUN>-start` to
`AutomationMarker: <RUN>-end` unless a condition names another window.

| Line | Source | Used for |
|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | every verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` | step markers |
| `Device is offline. Preparing Dummy VPN for Self Mode.` | `HomeFragment` | VPN path taken; must be 0 |
| `HomeFragment: Device is offline; Android Auto 17.4+ connects over 127.0.0.1:5277, so no dummy VPN.` | `HomeFragment` | offline branch taken; must be 0 on an online start |
| `VpnControl: Starting DummyVpnService (GitHub Build` | `VpnControl` (github flavor) | a VPN start; must be 0 |
| `DummyVpnService: tun established` | `DummyVpnService` (github flavor) | a tun came up; must be 0 |
| `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...` | `SelfLauncherManager` | 5277 route |
| `SelfMode: AA < 17.4 detected. Starting WirelessServer on 5288 and running legacy triggers...` | `SelfLauncherManager` | legacy route |
| `SelfMode: Launching AA Wireless Startup via Activity...` | `SelfLauncherLegacy` | legacy launch |
| `WirelessServer: Incoming connection detected from` | `WirelessServer` | legacy accept |
| `SSL handshake complete` | `AapSslContext` (INFO) | session up; one per session at INFO |
| `Throughput over ` | `VideoDecoder` (INFO) | session alive; read `rendered=N` |
| `AapService: releasing the dummy VPN (owner=` | `AapService.stopDummyVpn` | the release call |
| `SELF_MODE_SESSION_LIVE` | `DummyVpnPolicy` | the new reason |
| `VpnControl: DummyVpnService was not running` | `VpnControl` (github flavor) | the release found no tun |
| `with a live session` | `AapService.maybeTearDownBeforeLinkGoes` | link-loss teardown; must be 0 |
| `AapService: Disconnected.` | `AapService` | session end; must be 0 inside the hold |
| `SelfMode: nothing connected within` | `SelfLauncherManager` | launch deadline missed; must be 0 |

Gearhead lines (phone side, same capture). They are not in our source.

| Line | Seen before on | Used for |
|---|---|---|
| `Head unit connected` | 17.9 on D-POCO (round 1 R2) | 5277 accept |
| `Launch projection 127.0.0.1 5288` | 17.3 on D-HU (round 1 R4, R5L) | legacy launch aimed at us |
| `received ByeByeRequest` | 17.9 on D-POCO (round 1 R5A, R5P) | a session close from our side; must be 0 inside the hold on D-POCO. Not graded on D-HU: 17.3 logged none in round 1 R5L although our ByeBye went out, so a 0 there proves nothing |
| `Critical error`, `hard loss of network`, `WSEM#onProjectionNetworkLost`, `Lost network. lostNetwork=`, `WiFi network is lost`, `Socket failed due to VPN connection`, `Head unit disconnected` | 17.8 dex | Gearhead teardown; each must be 0 inside the hold |

`WIRELESS_WIFI_NETWORK_STATE_CHANGED_DISCONNECTED_NO_SOCKET_DISCONNECT` is not graded. Round 1 found
it at launch on the legacy route in both arms (R1L, R4). Record its count only.

## 7. Runs

Every run: `th_gate` first, then the clean start (section 5), then the steps. Report `th_report $RUN`
in each section. Discard rule: a second `SSL handshake complete` in the run. A discarded run is
re-run once.

### R5P2. Fix, online start, 5277 route (D-POCO). Point of the round.

`U=$POCO; RUN=R5P2`. Install the candidate with `adb install -r`, check the md5, then the clean
start.

```bash
cap_start; mark R5P2-start
adb -s $U shell am start -n $MAIN
waitline 60 'SSL handshake complete'; echo "ssl_wait=$?"; sleep 5
mark R5P2-ssl; echo "tun_at_ssl=$(tuns)"
sleep 20
mark R5P2-probe; uidstate; echo "P=$(probe)"; echo "tun_at_mid=$(tuns)"
sleep 40
mark R5P2-end; echo "tun_at_end=$(tuns)"; defnet; cap_stop
send ACTION_EXIT; sleep 3
```

`R5P2-ssl` to `R5P2-end` is about 130 s. If `ssl_wait` is 1, the session did not form: grade the
run FAIL and record the last 30 app lines.

**PASS when all hold:**

- `Device is offline. Preparing Dummy VPN for Self Mode.` = 0, and
  `HomeFragment: Device is offline; Android Auto 17.4+ connects over 127.0.0.1:5277, so no dummy VPN.` = 0.
- `VpnControl: Starting DummyVpnService (GitHub Build` = 0 and `DummyVpnService: tun established` = 0.
- `tun_at_ssl` = 0, `tun_at_mid` = 0, `tun_at_end` = 0.
- `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...` = 1.
- `SSL handshake complete` = 1.
- Between `R5P2-ssl` and `R5P2-end`: `AapService: Disconnected.` = 0 and `with a live session` = 0.
- Between `R5P2-ssl` and `R5P2-end`: 3 or more `Throughput over ` lines, each with `rendered` above 0.
- `P` is 6 or more, with `uidstate` reading `effective=NONE`.
- `defnet` at the end reads a network, not `none`.
- Phone side: `Head unit connected` 1 or more. Between `R5P2-ssl` and `R5P2-end`,
  `received ByeByeRequest` = 0 and every Gearhead teardown string = 0.
- `SelfMode: nothing connected within` = 0.

**FAIL** on any condition above that is not met. A `tun established` line or a tun at any read is
a FAIL of commit 2. A `Disconnected.` inside the hold with fewer than 3 `Throughput over ` lines
before it is a FAIL; record the 20 capture lines before it. `P` from 1 to 5 with `effective=NONE` is
INCONCLUSIVE for the internet half only.

Record: the count of `AapService: releasing the dummy VPN (owner=` lines that also carry
`SELF_MODE_SESSION_LIVE`, and the count of `VpnControl: DummyVpnService was not running`.

```bash
grep -aF 'AapService: releasing the dummy VPN (owner=' "$OUT/$RUN.txt" | grep -acF 'SELF_MODE_SESSION_LIVE'
```

End of R5P2: `adb -s $POCO shell am force-stop $PKG; U=$POCO; offline; waitoff`. Bluetooth on
D-POCO stays off.

### R5L2. Fix, online start, legacy route (D-HU). Point of the round.

`U=$HU; RUN=R5L2`. Install the candidate, check the md5, then the clean start. D-HU's Bluetooth
stays off: run `adb -s $HU shell svc bluetooth disable` after the clean start and check
`dumpsys bluetooth_manager` reads it off.

```bash
cap_start; mark R5L2-start
adb -s $U shell am start -n $MAIN
waitline 60 'SSL handshake complete'; echo "ssl_wait=$?"; sleep 5
mark R5L2-ssl; echo "tun_at_ssl=$(tuns)"
sleep 20
mark R5L2-probe; uidstate; echo "P=$(probe)"; echo "tun_at_mid=$(tuns)"
sleep 40
mark R5L2-end; echo "tun_at_end=$(tuns)"; defnet; cap_stop
send ACTION_EXIT; sleep 3
```

**PASS when all hold:**

- `Device is offline. Preparing Dummy VPN for Self Mode.` = 0, and the `HomeFragment: Device is offline; ...` line = 0.
- `VpnControl: Starting DummyVpnService (GitHub Build` = 0 and `DummyVpnService: tun established` = 0.
- `tun_at_ssl` = 0, `tun_at_mid` = 0, `tun_at_end` = 0.
- `SelfMode: AA < 17.4 detected. Starting WirelessServer on 5288 and running legacy triggers...` = 1,
  and `SelfMode: Launching AA Wireless Startup via Activity...` 1 or more.
- `WirelessServer: Incoming connection detected from` = 1, and `SSL handshake complete` = 1.
- Between `R5L2-ssl` and `R5L2-end`: `AapService: Disconnected.` = 0 and `with a live session` = 0.
- Between `R5L2-ssl` and `R5L2-end`: 3 or more `Throughput over ` lines, each with `rendered` above 0.
- `P` is 6 or more, with `uidstate` reading `effective=NONE`.
- `defnet` at the end reads a network, not `none`.
- Phone side: `Launch projection 127.0.0.1 5288` 1 or more. Between `R5L2-ssl` and `R5L2-end`,
  every Gearhead teardown string = 0. Record the `received ByeByeRequest` count; it is not graded
  on D-HU (section 6).
- `SelfMode: nothing connected within` = 0.

**FAIL** on any condition above that is not met, with the same notes as R5P2. A release line here
is the adopt-with-no-tun case and is not a FAIL. Record the same two release counts as R5P2.

End of stage A: `U=$HU; send ACTION_EXIT; sleep 3; adb -s $HU shell am force-stop $PKG`. Turn
Bluetooth back on with `svc bluetooth enable` on D-HU and on D-POCO, and run `join` on D-POCO.
Restore `settings.xml` from the backup on both units, and diff each against its backup.

### R2M. Fix, offline start, 5277 route, second unit (D-MOTO). Optional.

Run it only when a person can do the hand step. `U=$MOTO; RUN=R2M`. Candidate. It repeats round 1's
R2 on a second unit at API 34. No rig unit is near API 30, the user's level.

1. Hand step: toggle "Start head unit server" in Android Auto's developer settings on D-MOTO. No adb
   route reaches it.
2. `port5277` must read 1 or more. If not, R2M is UNTESTABLE.
3. Confirm D-MOTO is unlocked (`dumpsys window | grep -a mCurrentFocus` shows no keyguard). It can
   be behind a PIN.
4. Write section 4's keys (no `native-poke-all-paired`), then run an **offline** clean start:
   `send ACTION_EXIT; sleep 3; adb -s $U shell am force-stop $PKG; offline; waitoff; defnet; tuns; port5277; send ACTION_QUERY_STATE`.
   The run starts only when `defnet` reads `none` and `tuns` reads 0.

```bash
cap_start; mark R2M-start
adb -s $U shell am start -n $MAIN
waitline 60 'SSL handshake complete'; echo "ssl_wait=$?"; sleep 5
mark R2M-ssl; echo "tun_at_ssl=$(tuns)"
mark R2M-join; join; waitnet; defnet; sleep 10
mark R2M-probe; uidstate; echo "P=$(probe)"; echo "tun_at_end=$(tuns)"
sleep 60; mark R2M-end; cap_stop
send ACTION_EXIT; sleep 3
```

Do not use `offline` after the launch. `join` turns the radios on and does not raise the
link-loss trigger.

**PASS when all hold:** `HomeFragment: Device is offline; Android Auto 17.4+ connects over 127.0.0.1:5277, so no dummy VPN.` = 1;
`Device is offline. Preparing Dummy VPN for Self Mode.` = 0; `DummyVpnService: tun established` = 0;
`tun_at_ssl` = 0 and `tun_at_end` = 0; `SSL handshake complete` = 1; `AapService: Disconnected.` = 0
between `R2M-ssl` and `R2M-end`; 2 or more `Throughput over ` lines between `R2M-join` and
`R2M-end`, each with `rendered` above 0; `Head unit connected` 1 or more; every Gearhead teardown
string 0 between `R2M-ssl` and `R2M-end`; `P` 6 or more with `effective=NONE`.

There is no baseline arm on D-MOTO. A `P` of 0 with no tun is INCONCLUSIVE for the internet half,
not a FAIL. Restore D-MOTO's `settings.xml` from its backup at the end.

### Stop rule

- A run with a setup failure (wrong commit, `defnet` reads `none` at an online launch, a missing
  `AutomationReceiver:` line, a consent dialog) is re-run once. A second setup failure makes it
  UNTESTABLE.
- R5P2 and R5L2 run at most 2 times each, and the second time only after a setup failure or a discard.
- No run is repeated to turn an INCONCLUSIVE into a PASS.

## 8. Do not re-run

- Round 1 R1, R1L, R2, R4, R3P, R3L: the fault on `main`, the fix on both routes, and the reconnect.
  All PASS.
- Round 1 R5A: `main` starts no tun on an online start.
- The network drop in R5A, R5P and R5L. The round 1 captures already show that our own link-loss
  teardown ends a Self Mode session on `WIFI_STATE_DISABLING`, in both arms.
- `selfmode-playstore-route` round 1, R2, R3, R8 and R8c: the two launch routes.

## 9. Report back

1. **R5P2 and R5L2:** `tun_at_ssl`, `tun_at_mid`, `tun_at_end`, `P` with its `uidstate`, the
   `AapService: Disconnected.` count in the hold, and the `Throughput over ` count in the hold.
2. **R5P2 and R5L2:** the `VpnControl: Starting DummyVpnService (GitHub Build` count and the
   `DummyVpnService: tun established` count.
3. **R2M**, if run: `tun_at_end` and `P`.

Also give, in Setup notes: each unit's API level, Gearhead `versionName`, the `ACTIVATE_VPN` appop
on each unit, the APK md5, and whether the APK was round 1's or a rebuild.
