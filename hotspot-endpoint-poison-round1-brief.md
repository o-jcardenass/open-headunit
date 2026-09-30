# hotspot-endpoint-poison: round 1 brief

> **Revised 2026-09-23, before any run.** The candidate is now **`018d5af6`** (three commits, 2336
> tests): the third teaches the app the XYAuto ACC pair and holds hotspot auto-enable while the car
> is off. §2 carries the reporter's new address readings, R1's expectation changed, and R8 is new.
> A copy that says `cf3b693c` is stale.

## 1. Build and baseline

- Branch `fix/hotspot-endpoint-poison` on the fork, tip **`018d5af6`**, three commits on `main`
  `dd454eed`. New branch, never rewritten.
  ```bash
  git fetch fork fix/hotspot-endpoint-poison
  git checkout -B fix/hotspot-endpoint-poison fork/fix/hotspot-endpoint-poison
  git rev-parse --short HEAD   # 018d5af6
  ```
- JVM gate: **2336 tests, 0 failures** on this SHA from the author's side. Re-run it with
  `run_unit_tests.sh`.
- DEX identity: `SoftApEndpointStabilityPolicy` must be in the APK (template §5's symbol check).
- **Baseline APK for P1 only:** `main` at `dd454eed`, built separately and copied out of `apks/`
  before the candidate is built (template §7a, `build_hur.sh` deletes the previous APK).

## 2. What this is and why it exists

On the Native AA **hotspot** route, the head unit tells the phone a WPP-over-TCP endpoint once the
access point has been graded `STABLE`. The phone then stores `{ssid, bssid, ip, port}` plus the
password, and from then on it joins that network and dials that IP instead of running the
Bluetooth handshake. When any of those no longer holds, it never falls back to Bluetooth, and the
user has to forget the head unit in Android Auto.

The grading compared **only the name and the BSSID**. On WiFi Direct that is enough, because the
group owner is always `192.168.49.1`. On a hotspot it is not: a reporter's access point came up at
`192.168.4.159`, which is the shape of Android's tethering address picker since 11 (a random /24
inside 192.168.0.0/16, random host part). The reporter's words were "I gotta sometimes forget the AA
when the unit is off for a bit". Their next two logs settled it: **the address moves on every AP
start, inside one boot as well as across one**, while the name and BSSID never move, so every
bring-up graded `stable=yes` and advertised a dead address.

| When (reporter, api 30, #1010 test build) | AP address | Name / BSSID |
|---|---|---|
| 22 Sep | `192.168.4.159` | `AndroidAP_7935` / `56:A1:4C:D3:A0:F2` |
| 23 Sep, before the car was switched off | `192.168.157.153` | same |
| the app's restart attempt during ACC-off | `192.168.206.8` (tethering failed, `No such device`) | same |
| after the wake, `stable=yes`, advertised | `192.168.246.199` | same |
| after a full reset, `stable=yes` | `192.168.0.154` | same |

The same logs show the second complaint ("the hotspot needs to be manually turned on") is the car's
power cycle. On `xy.android.acc.off` the unit takes the AP and Bluetooth down, and the app's
auto-enable fired into that teardown. The unit then killed the app during the sleep, and
`xy.android.acc.on` is sent without the background flag, so no manifest receiver can hear it.

What the branch does:

- **The address and the password now count** (`SoftApEndpointStabilityPolicy`). A different IP or
  password than last time grades `CHANGED` (endpoint withheld, credentials sent `DYNAMIC`). The same
  IP must also have been seen in **two different boots** (`Settings.Global.BOOT_COUNT`) before the
  verdict may stay `STABLE`; until then it is `UNPROVEN`. It only ever demotes the name and BSSID
  verdict.
- **An advertised access point that later moved raises the stale-endpoint banner.** What was
  advertised is remembered; a bring-up that differs logs what moved and raises
  `PHONE_HOLDS_STALE_ENDPOINT`. A rejection cannot retire it on this route: the phone dials an
  address nobody holds, and no app API can pin the tethering address or rewrite the AP config.
- **Auto-enable** (second commit): the log header carries `autoHotspot:on|off`; a failed first
  attempt is retried once, at least 10 s later, capped at two per run; and the no-AP line says so
  when the setting is off.
- **XYAuto ACC** (third commit): `xy.android.acc.off` joins the ACC-off actions (session torn down,
  and no auto-enable attempt while the car is off); `xy.android.acc.on` joins the runtime wake
  filter, the manifest and the boot receiver.

## 3. What is different about this round

- **Units:** D-HU as head unit (rooted, `adb root` first), **D-MOTO** as the phone, both cabled.
  D-MOTO is preferred over D-POCO because D-POCO's Gearhead has carried stale records from other
  threads (`native-aa-wireless` round 4). Name the phone in Setup notes if you have to swap.
- **Two readers:** D-HU `OPENHU:V '*:S'` plus `-s WifiService:I SoftApManager:D hostapd:I`, and a
  full `logcat -v threadtime` on the phone. The phone's lines are what prove whether it holds an
  endpoint.
- **The hotspot is started from the shell for R1 to R4**, with `auto-enable-hotspot=false` so the
  app does not race it:
  ```bash
  adb -s 27870808938846 shell cmd wifi start-softap OHU-HOTSPOT wpa2 ohutest12345 -b 5
  adb -s 27870808938846 shell cmd wifi stop-softap
  ```
  D-HU cannot read its own AP config (memory: the rig refuses both halves of the reflection), so the
  app gets the name and password from the manual override keys in §4, and **they must match what
  the shell started**. R5/R6 let the app start it and use the persisted config (§4).
- **`adb reboot` is fine here**, even though it skips `ShutdownThread` (template §7a): this round
  needs a new boot, not a clean shutdown. After each reboot, re-run `adb root`, restart both readers
  and confirm D-HU is back on `Pegue Cdesta` (`dumpsys wifi | grep -iE "mWifiInfo|SSID"`).
- **A "bring-up"** in this brief is always:
  ```bash
  adb shell am force-stop $PKG
  send ACTION_START_WIRELESS --ez no_ui true
  ```
  followed by up to 90 s for `SSL handshake complete`. On the hotspot route a force-stop orphans
  nothing, because the AP belongs to the shell.
- **Forgetting the head unit on the phone is a hand step** (Android Auto settings on D-MOTO,
  Previously connected cars, forget this unit). There is no verb for it: it is the phone's own UI.
- **R4 depends on R3's outcome.** If D-HU's address moves on every reboot, nothing is ever
  advertised on the candidate, and R4 is expected **UNTESTABLE** on this rig. Its logic is covered by
  `SoftApEndpointStabilityPolicyTest`. Say which branch R3 took and move on.
- **P1 depends on R1.** It only runs if R1 shows the address moving on a reboot.

## 4. Settings keys

Back up `settings.xml` first (template §1). D-HU's `shared_prefs/` has been root-owned: use §7a's
host-side `python3` edit, push, `run-as cp` method, and read the file back.

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `native-ap-transport` | int | `1` | the hotspot route |
| `hotspot-interface` | string | `wlan2` | D-HU's access-point interface |
| `hotspot-ssid` | string | `OHU-HOTSPOT` | must match the shell's `start-softap` |
| `hotspot-password` | string | `ohutest12345` | must match the shell's `start-softap` |
| `static-bssid` | string | `0` | no typed address; the BSSID must be read |
| `auto-enable-hotspot` | boolean | `false` | R1 to R4; R5 sets it `true` |
| `allow-external-configuration` | boolean | `true` | so `ACTION_EXPORT_LOG` works for the header |

**Delete** (both removal forms, template §1) at the start of R2 and again before P1's candidate
half, so the measurement starts from nothing: `soft-ap-last-group-ssid`, `soft-ap-last-group-bssid`,
`soft-ap-last-identity-verdict`, `soft-ap-last-ip`, `soft-ap-last-psk-digest`, `soft-ap-ip-boot`,
`soft-ap-ip-spanned-boot`, `soft-ap-advertised-ssid`, `soft-ap-advertised-psk-digest`,
`soft-ap-advertised-bssid`, `soft-ap-advertised-ip`, `connection-issue-stale-endpoint`.

For R5/R6, read the persisted AP config as root (`cmd wifi get-softap-config`) and put its name and
password into `hotspot-ssid` / `hotspot-password`. Also set `auto-start-on-boot` = `true`, which is
mirrored into `settings_device_protected.xml`: read the mirror back too.

**Revert at the end** (R7 and after): `native-ap-transport=0`, and the four hotspot keys cleared
together (`hotspot-ssid` `""`, `hotspot-password` `""`, `static-bssid` `"0"`, `hotspot-interface`
`""`), per §7a's four-key rule.

## 5. The lines that decide every run

Each checked with `git grep -F` on `018d5af6`, except the phone's, which are Gearhead's own.

| Line (substring) | Meaning |
|---|---|
| `WifiLauncherNative: access point identity ssid=` … `ip=` … `stable=` | one per bring-up: the verdict, with its reason in brackets |
| `first reading of the access point's address at` | first bring-up since the keys were cleared (`stable=unproven`) |
| `not yet seen across a restart` | same address, same boot (`stable=unproven`) (new) |
| `its address moved from` | the address moved (`stable=no`) (new) |
| `its password changed` | the password moved (`stable=no`) (new) |
| `NativeAA: advertising WPP over TCP at` | an endpoint went out |
| `NativeAA: not advertising WPP over TCP: ` | withheld, with its reason |
| `NativeAA: the WPP endpoint advertised on the access point at` | the banner was raised (new) |
| `SoftApCredentials: No access point after` … `(attempt N of 2)` | an auto-enable attempt (new wording) |
| `the hotspot did not come up on attempt` | that attempt failed (new) |
| `is off, so the app did not try to switch it on` | the no-AP line with the setting off (new) |
| `LogExporter: session` … `autoHotspot:` | the header field (new); emitted on `ACTION_EXPORT_LOG` |
| `WirelessServer: Incoming connection detected from` | the phone reached us on the network |
| `SSL handshake complete` | a session formed (match without the `Handshake:` prefix) |
| phone: `No WPP on TCP configuration found in storage for the head unit` | the phone holds no endpoint and uses Bluetooth |
| phone: `Trying to start WPP on TCP with configuration` | the phone holds one and is dialling it |
| phone: `NETWORK_NOT_FOUND` / `CONNECTED_TO_WRONG_SSID` | the phone is stuck on a stored record |

## 6. Runs

### R0 Build gate
SHA `018d5af6`, DEX carries `SoftApEndpointStabilityPolicy`, JVM gate 2336/0, `adb install -r` on
D-HU. `send ACTION_QUERY_STATE` reports the commit. PASS: all four.

### R1 Does D-HU's tethering address move? (measurement, decides P1 and R4)
App stopped. For each of the four points below, record `settings get global boot_count` and
`ip -4 addr show wlan2`:
1. `start-softap` (as in §3). 2. `stop-softap`, wait 5 s, `start-softap`. 3. `adb reboot`, then
`start-softap`. 4. `stop-softap`, `start-softap`.

No PASS/FAIL: report the four addresses and boot counts. There is no expectation to meet: the
reporter's unit moved on every start (§2), and D-HU may hold its address, move it per start, or move
it per boot. R3 and R4 are graded on whichever it does.
Also record the BSSID (`dumpsys wifi | grep -i SoftApInfo` or the `hostapd` lines) at each point.

### P1 Positive control on the baseline (only if R1 shows the address moving on a reboot)
Install `dd454eed`. Keys from §4, cleared. AP up from the shell.
1. Two bring-ups in one boot. Expect the second to print `advertising WPP over TCP at <ip>:5299`.
2. `adb reboot`, `start-softap`, one bring-up. Watch 3 minutes.

**Reproduced** if, after the reboot, the phone logs `Trying to start WPP on TCP with configuration`
for the old IP and no `SSL handshake complete` arrives in 3 minutes. Then do the hand forget on
D-MOTO, and install the candidate with `adb install -r`. If it does not reproduce, say what the phone
did instead. The candidate's runs still go ahead.

### R2 Same boot: no endpoint on an address not yet seen across a restart
Candidate, keys from §4, cleared. AP up from the shell. Phone forgotten if P1 ran. Two bring-ups.

PASS, all of:
- bring-up 1: `stable=unproven` with `first reading of the access point's address at`, and
  `not advertising WPP over TCP`;
- bring-up 2: `stable=unproven` with `not yet seen across a restart`, and `not advertising WPP over TCP`;
- both reach `SSL handshake complete`, and the phone logs `No WPP on TCP configuration found`
  at least once.

If the change did nothing, bring-up 2 would read `stable=yes` and advertise.

### R3 Across a reboot (the point of the round)
Straight after R2. `adb reboot`, `adb root`, readers back, `start-softap`, one bring-up. Then a
second bring-up without rebooting.

- **If the address moved** (compare with R2's `ip=`): PASS is `stable=no` with
  `its address moved from <R2 ip> to <new ip>`, `not advertising WPP over TCP`, and
  `SSL handshake complete` over Bluetooth **with no forget**. The second bring-up reads
  `stable=unproven` (`not yet seen across a restart`).
- **If the address held**: PASS is `stable=yes` and `advertising WPP over TCP at <ip>:5299` on the
  first bring-up, then on the second a reconnect with `WirelessServer: Incoming connection detected`
  and the phone logging `Trying to start WPP on TCP with configuration`.

FAIL: any `advertising WPP over TCP` on a bring-up whose `ip=` differs from the one before it.

### R4 A moved password raises the banner (only if R3 advertised)
With an endpoint advertised in R3: `stop-softap`, `start-softap OHU-HOTSPOT wpa2 ohutest67890 -b 5`,
force-stop, write `hotspot-password` = `ohutest67890`, one bring-up.

PASS, all of:
- `stable=no` with `its password changed`;
- `NativeAA: the WPP endpoint advertised on the access point at <ip> no longer matches (password)`;
- `connection-issue-stale-endpoint` non-zero in `settings.xml` after a force-stop.

The phone is expected to be stuck after this: that is the failure the banner exists to name.
Record what it logs for 2 minutes, then do the hand forget and confirm a Bluetooth session forms.
If R3 took the "moved" branch, report R4 as UNTESTABLE (§3).

### R5 The app switches the hotspot on after a boot (Part B)
`stop-softap`. Keys per §4's R5/R6 paragraph, `auto-enable-hotspot` = `true`,
`auto-start-on-boot` = `true` (mirror read back). `adb reboot`. Nothing by hand. Readers back
once adb returns, and watch for 3 minutes from boot.

PASS, all of:
- `(attempt 1 of 2)`. If it is followed by `the hotspot did not come up on attempt 1`, then
  `(attempt 2 of 2)` at least 10 s later;
- the AP is up (`SoftApInfo`), then `SoftApCredentials: SUCCESS`, then `SSL handshake complete`;
- `send ACTION_EXPORT_LOG` afterwards, and the `LogExporter: session` line carries `autoHotspot:on`.

Report the gap between the last `WifiService`/`SoftApManager` radio event before each attempt and
the attempt itself. That is the number memory ties to the attempt failing.

### R6 Setting off: the log says so
As R5 with `auto-enable-hotspot` = `false`. PASS: no `(attempt` line; after about 30 s,
`SoftApCredentials: No usable access point after` ending with
`'Auto-Enable Hotspot' is off, so the app did not try to switch it on.`; the header carries
`autoHotspot:off`.

### R8 The car switched off and on (XYAuto broadcasts)
Hotspot route, `auto-enable-hotspot` = `true`, AP up from the shell, a session live. The ACC
broadcasts are implicit and reach the app's runtime receiver, so no root is needed for them:
1. `adb shell am broadcast -a xy.android.acc.off`, then `cmd wifi stop-softap` within 2 s. Watch 60 s.
2. `adb shell am broadcast -a xy.android.acc.on`. Watch 60 s.

PASS, all of:
- step 1: `WakeDetect: ACC off (xy.android.acc.off)`, then `SoftApCredentials: the car is off
  (xy.android.acc.off), not switching the hotspot on.`, and **zero** `(attempt` lines in the 60 s;
  `connection-issue-hotspot-off` unchanged from before the step. Record which teardown line
  followed the ACC line (`closing it now` or `does not ride that link`); it is not graded;
- step 2: `WakeDetect: xy.android.acc.on`, then `(attempt 1 of 2)` within 60 s, and the AP up.

If the change did nothing, step 1 would print an `(attempt` line straight after the AP went down.

### R7 WiFi Direct, no regression
Revert per §4. One bring-up. PASS: `WifiDirectManager: group identity ssid=` present, no
`access point identity` line, and `SSL handshake complete`.

## 7. Do not re-run

The WiFi Direct endpoint retirement (rename, toggle and New identity arms) passed on PR #1010's
branch and is untouched here. R7 is only a guard.

## 8. Report back

1. R1's four addresses and boot counts: whether this unit's tethering address moves on a reboot.
2. R3's verdict line and whether the phone reconnected with no forget.
3. R5's attempt count, and the timing gap for each attempt.
4. R8 step 1's attempt count in the 60 s (the answer is zero).
