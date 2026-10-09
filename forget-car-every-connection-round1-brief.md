# forget-car-every-connection, round 1 brief

Published name: `forget-car-every-connection-round1-brief.md`. Results go in
`forget-car-every-connection-round1-results.md`. Evidence: release `rig-evidence-forget-car-every-connection`,
asset `forget-car-every-connection-round1-captures.zip` (first round of the thread, so create the release).

Estimated time: 15 min pre-flight, 20 min Stage 1 (D-HU), 15 min Stage 2 (D-POCO as head unit).

## 1. Build and baseline

- **Candidate:** `fix/forget-car-every-connection` at **`93a59f7a`**
  (`93a59f7a2f722391e8b3579eecfc7b53b7214699`), tree `ce1cd02f5d7804cf5cbc7fc4de5f78cc8c438677`.
  Two commits on `main` `71375a68`. New branch, so no history was rewritten.
- **Baseline:** none built. The `main` comparison for Stage 2 is the `samsung-driver-native` round 1
  addendum, which ran `main` `71375a68` on D-POCO as head unit (section 3).

```bash
git ls-remote fork fix/forget-car-every-connection   # MUST print 93a59f7a2f722391e8b3579eecfc7b53b7214699
git fetch fork fix/forget-car-every-connection
git worktree add ../ohu-wt-fcec-93a59f7a 93a59f7a2f722391e8b3579eecfc7b53b7214699
git -C ../ohu-wt-fcec-93a59f7a rev-parse 'HEAD^{tree}'   # MUST print ce1cd02f5d7804cf5cbc7fc4de5f78cc8c438677
```

Build with `build_hur_cool.sh` (or `build_hur.sh`) and `HUR_DIR=../ohu-wt-fcec-93a59f7a`. Copy the APK
out of `apks/` at once. Run `run_unit_tests.sh` on the same worktree: expect **3035** tests, 0 failures.
A build or test failure stops the round.

Install with `adb -s <unit> install -r` on **D-HU and D-POCO**. Identity gate on each unit, both MUST hold:

```bash
send ACTION_QUERY_STATE                          # data= line MUST carry commit 93a59f7a
unzip -p <pulled apk> 'classes*.dex' | strings | grep -cF noteAdvertisedGroupAddressMoved   # MUST be >= 1
```

The symbol is new in this branch. `main` cannot carry it.

## 2. What this is and why

A field unit (QUALCOMM Bengal, Android 11, Native AA over WiFi Direct) needs "forget this car" in
Android Auto after every disconnect. Its log shows the cause:

- The group name and the group BSSID repeat on every create, so the identity verdict reads `stable=yes`.
- The group IP moves on every create (five creates, five different `192.168.x.y` subnets).
- So each handshake advertises a WPP-over-TCP endpoint (field 6) and STATIC credentials (field 5) at
  an IP that the next create kills. The phone then joins the same name and BSSID and dials a dead IP.
  No dial reaches us, so the type 10 rejection cannot clear it. Only a forget clears it.

The candidate grades the group IP too, with the access point's grader
(`SoftApEndpointStabilityPolicy.grade`):

1. **Commit 1.** `WifiDirectManager` grades the IP the group's interface answered, once per group.
   The IP must repeat across two **creates**. A read of a surviving group does not count, because it
   repeats by construction. The combined verdict feeds fields 5 and 6, the TCP serve decision and the
   type 3. It prints one new line per group: `WifiDirectManager: group address ip=<ip> stable=<label> (<reason>)`.
2. **Commit 2.** The handshake stores the endpoint it advertised. When a later group on the same name
   and passphrase comes up at another IP, the app logs one WARN line and raises the
   `PHONE_HOLDS_STALE_ENDPOINT` banner once. A Bluetooth landing no longer clears that banner until the
   IP has been proven to repeat.

Three new `settings.xml` keys hold the IP record: `wifi-direct-last-ip`, `wifi-direct-last-ip-psk-digest`,
`wifi-direct-ip-repeated`. Four hold the advertised endpoint: `wifi-direct-advertised-endpoint-ssid`,
`-psk-digest`, `-bssid`, `-ip`. The banner stamp is `connection-issue-stale-endpoint` (a long, epoch ms).

The JVM tests cover the grade. Three things have no unit test and only hardware shows them: the
per-group latch over the three or four credential deliveries, the read-or-create flag, and the use of
the read IP rather than the `192.168.49.1` fallback. This round tests those, and checks that a unit
with a fixed IP still advertises.

## 3. What is different about this round

- **No rig unit moves its group IP.** D-HU and D-POCO both hold `192.168.49.1` (D-POCO measured in the
  `samsung-driver-native` addendum A0). So the field fault is reproduced by **seeding** a moved IP into
  `settings.xml` with the app stopped (H3). That is the positive control and the point of the round.
- **D-HU never advertises on its own.** Its BSSID moves on every create (`rig-quirks/topics/wifi.md`,
  and `wpp-endpoint-depoison` rounds 4 and 5), and a read hands back the stored verdict of the last
  create. So on D-HU the name-and-BSSID verdict is `CHANGED` after any create, on `main` too. H2 and H3
  seed `wifi-direct-last-identity-verdict` = `STABLE` so that the IP layer alone decides. Restore the
  backup after Stage 1.
- **On D-HU, a create prints `stable=no` on the `group address` line even when the IP repeated.** The
  label is the combined verdict, and the BSSID half is `no`. The reason in brackets is the IP half
  only. So H1 grades the IP record from `settings.xml`, never from the label. `(name, BSSID and address repeat)`
  beside `stable=no` is expected on D-HU.
- **D-POCO as head unit is the real fixed-IP, fixed-BSSID unit.** On `main` (addendum A1) it advertised
  `192.168.49.1:5299` in cycle 1, and both reconnects came back by WPP over TCP. On the candidate,
  cycle 1 must withhold (no IP record yet) and cycle 2 must advertise. Stage 2 (P1) measures that.
- **The app writes the keys this round grades.** Stat D-HU's `shared_prefs/` first (`rig-quirks/units/D-HU.md`).
- **Phone captures:** D-POCO in Stage 1, D-MOTO in Stage 2. H1 needs no phone.
- **Role order:** D-POCO is the phone in Stage 1 and the head unit in Stage 2. End Stage 2 with
  `headunit://exit` on D-POCO, never a bare force-stop.

## 4. Settings keys

Back up each head unit's `settings.xml` before its stage and diff it against the round start. State the
delta in Setup notes. Leave the rig's audio keys as they are (deliberate worst case). `log-level` 2
(INFO) carries every line this round greps. `ACTION_LOG_MARKER` is not gated on this branch, so
`allow-external-configuration` is not needed.

**D-HU, Stage 1 baseline** (write once, then each run writes only what it names):

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<boolean name="wifi-direct-stable-identity" value="true" />
<string name="static-p2p-bssid">0</string>
<int name="log-level" value="2" />
<int name="onboarding-version" value="2" />
```

Keep D-HU's current wake list and `native-poke-all-paired`, which reach D-POCO today. Record both.
Delete the seven new keys listed in section 2 (the state just after the update).

**D-POCO as head unit, Stage 2** (write with `set_prefs_runas_host.py`, the rig's D-POCO method):

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<boolean name="wifi-direct-stable-identity" value="true" />
<set name="native-poke-bt-macs"><string>MOTO_MAC</string></set>
<boolean name="native-poke-all-paired" value="false" />
<int name="native-driver-selection-mode" value="0" />
<int name="onboarding-version" value="2" />
<int name="log-level" value="2" />
```

`MOTO_MAC` comes from pre-flight P3. If D-POCO's bonded list redacts it, write an empty set and
`native-poke-all-paired` `true`, and say so. Delete the seven new keys and `connection-issue-stale-endpoint`.

## 5. Helpers

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846            # Stage 1 D-HU; Stage 2 D-POCO 4f4027e9
PH=4f4027e9                  # Stage 1 D-POCO; Stage 2 D-MOTO ZY22GC3BM4
OUT=~/rig-private/forget-car-every-connection-r1; mkdir -p $OUT
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1"; }
launch() { adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity; }
huexit() { adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit"; sleep 5; }

# captures: start before the launch; the phone stream does not clear the phone's buffer
cap_on() { adb -s $HU logcat -c; stdbuf -oL adb -s $HU logcat -v threadtime > $OUT/$1-hu.txt & HUPID=$!
  stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash,events > $OUT/$1-phone.txt & PHPID=$!; sleep 2; }
cap_off() { sleep 2; kill $HUPID $PHPID 2>/dev/null; }

# waitfor FILE STRING MAXSEC : poll every 5 s; prints the elapsed seconds or TIMEOUT
waitfor() { local n=0; while [ $n -lt $3 ]; do grep -aqF "$2" "$1" && { echo $n; return 0; }; sleep 5; n=$((n+5)); done; echo TIMEOUT; return 1; }

# settings: read, and one root pass on D-HU (app stopped). Each arg is "set TYPE KEY VALUE" or "del TYPE KEY".
hu_read() { adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings-$1.xml
  grep -aoE '(wifi-direct-(last-ip|last-ip-psk-digest|ip-repeated|advertised-endpoint-[a-z-]+|last-identity-verdict|last-group-ssid|last-group-bssid)|connection-issue-stale-endpoint)"[^/]*' $OUT/settings-$1.xml; }
hu_kv() {
  local IFS=' '
  { echo 'F=/data/data/com.andrerinas.headunitrevived/shared_prefs/settings.xml'
    for kv in "$@"; do set -- $kv
      echo "sed -i -E 's#<[a-z]+ name=\"$3\"[^>]*/>##g' \$F"
      echo "sed -i -E 's#<string name=\"$3\">[^<]*</string>##g' \$F"
      if [ "$1" = set ]; then
        if [ "$2" = string ]; then echo "sed -i 's|</map>|<string name=\"$3\">$4</string></map>|' \$F"
        else echo "sed -i 's|</map>|<$2 name=\"$3\" value=\"$4\" /></map>|' \$F"; fi
      fi
    done
    echo 'chown $(stat -c %u:%g /data/data/com.andrerinas.headunitrevived) $F; chmod 660 $F'
  } > $OUT/fc_kv.sh
  adb -s $HU push $OUT/fc_kv.sh /data/local/tmp/fc_kv.sh >/dev/null
  adb -s $HU shell "su -c 'sh /data/local/tmp/fc_kv.sh'"
}
NEWKEYS="del string wifi-direct-last-ip|del string wifi-direct-last-ip-psk-digest|del boolean wifi-direct-ip-repeated|del string wifi-direct-advertised-endpoint-ssid|del string wifi-direct-advertised-endpoint-psk-digest|del string wifi-direct-advertised-endpoint-bssid|del string wifi-direct-advertised-endpoint-ip"
clear_new() { local IFS='|'; hu_kv $NEWKEYS "del long connection-issue-stale-endpoint"; }

# phone radios back after airplane mode: always both nudges, then verify with dumpsys
ph_up() { adb -s $PH shell cmd connectivity airplane-mode disable; sleep 2
  adb -s $PH shell svc wifi enable; sleep 0.3; adb -s $PH shell svc bluetooth enable; sleep 5
  adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_down() { adb -s $PH shell cmd connectivity airplane-mode enable; sleep 5; }
```

Copy `th_pkg`, `th_thr`, `th_wait`, `th_watch`, `th_gate` and `th_report` from
`archive/rounds/projection-teardown-and-relays-round1-brief.md` (its `ptr_lib.sh` block) and call
`RUN=<id> th_gate` before every run. Report `th_report <id>` in each run's section. Count verdicts stand
whatever the temperature. Never run two adb calls against one unit at the same time.

**Window rule for every grep:** count only the lines between `AutomationMarker: <id>-start` and
`AutomationMarker: <id>-end` in the named file. Use `grep -a` always.

```bash
win() { awk -v s="AutomationMarker: $2-start" -v e="AutomationMarker: $2-end" 'index($0,s){f=1} f{print} index($0,e){f=0}' "$1"; }
cnt() { win "$1" "$2" | grep -acF "$3"; }     # cnt FILE ID STRING
```

## 6. The deciding lines

Each string below was checked with `grep -F -r` on `app/src` at `93a59f7a`. All are INFO or WARN. Two are
built at run time, so `grep -F` on `app/src` cannot find their full text. The source is
`"first reading of the $network's address at $ip; ..."` and `"the $network came back but $moved, ..."` in
`SoftApEndpointStabilityPolicy`, with `network = "WiFi Direct group"` passed by `WifiDirectManager` and
`moved = "its address moved from ${previous.ip} to $ip"`. The runs grep the full substituted line in the log
(a run-time match). The `decisive-strings` block lists only the verbatim source fragments
(`first reading of the `, `'s address at `, ` came back but `, `its address moved from `), so each line in it
re-greps with `grep -F` on `app/src`. The Gearhead strings graded from the phone capture are in a
separate `phone-strings` block. Conditions that add `ip=192.168.49.1 stable=yes` to a line are the
same line with its runtime values.

**Head unit capture** (`$OUT/<id>-hu.txt`, inside the window):

| Line | What it means |
|---|---|
| `WifiDirectManager: group address ip=` | new: the IP grade for this group, once per group |
| `first reading of the WiFi Direct group's address at` (run-time match) | no IP record yet; grade `unproven` |
| `the WiFi Direct group came back but its address moved from` (run-time match) | the IP moved; grade `no` |
| `but not yet seen across a restart` | same IP, not yet proven by a create |
| `name, BSSID and address repeat` | the IP half passed (the label still carries the BSSID half) |
| `SUCCESS - Providing credentials to listener` | one per credential delivery; carries `identity stable=<label>`, the combined verdict |
| `the WPP endpoint advertised on the WiFi Direct group at` | new WARN: a phone may hold a dead endpoint (full text ends `needs this head unit forgotten in Android Auto.`) |
| `needs this head unit forgotten` | the same WARN, short form for counts |
| `NativeAA: advertising WPP over TCP at` | field 6 sent |
| `not advertising WPP over TCP:` | field 6 withheld (NativeAA or WppTcpServer prefix) |
| `WppTcpServer: connection from` | the phone dialled the endpoint |
| `WppTcpServer: rejecting this dial so the phone drops our endpoint and goes back to Bluetooth:` | type 10 sent |
| `is already up from` | the group was read, not created (full: `... reading it instead of tearing it down.`) |
| `createGroup SUCCESS` | the group was created |
| `AapService: Native AA user exit. Stopping active launcher.` | the user exit removed the group |
| `NativeAA: Connection accepted from` | the phone came back over Bluetooth |
| `SSL handshake complete` | session formed (INFO form, from `AapSslContext`) |
| `Service Discovery Request: ` | the phone asked for services; with SSL, the session is live |
| `never opened the Android Auto channel on radio [` | the phone answers the poke but never opens AA (bond capture) |
| `MATCH! Starting AapService` | self wake (discard rule only if it comes with group churn) |
| `AutomationReceiver: ` | a verb landed; a missing line voids the step |

**Phone capture** (`$OUT/<id>-phone.txt`, the whole file: it spans one run, and the markers print only on the head unit). Gearhead strings drift; a 0 means
absent or renamed. Report the Gearhead version (`dumpsys package com.google.android.projection.gearhead | grep versionName`).

| Line | What it means |
|---|---|
| `No WPP on TCP configuration found in storage` | the phone holds no endpoint |
| `Trying to start WPP on TCP with configuration` | the phone dials a stored endpoint (record the `ipAddress=`) |
| `NETWORK_NOT_FOUND` / `TCP_SOCKET_CONNECTION_FAILED` / `BSSID_MISMATCH` | the stored endpoint failed |
| `Restarting WPP over TCP` | Gearhead retries the TCP route |
| `THROTTLE_LIMIT_EXCEEDED` | Gearhead gave up retrying |

## 7. Runs

### Pre-flight (one batch, before Stage 1)

- **P1, adb, read only.** `adb devices -l`: D-HU, D-POCO, D-MOTO present. D-POCO and D-MOTO screens at
  home (`dumpsys window | grep mCurrentFocus`; fix with `input keyevent KEYCODE_HOME`). D-POCO:
  `dumpsys wifip2p | grep isGroupOwner` MUST be false (else `svc wifi disable`, `svc wifi enable`).
- **P2, adb.** D-HU: `adb -s $HU shell su -c 'stat -c "%U:%G %a" /data/data/com.andrerinas.headunitrevived/shared_prefs'`.
  Record it. If it is not the app's uid, `chown` it to the app's uid:gid and record both reads.
- **P3, adb, read only.** Bonds: `adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -A12 "Bonded devices"`
  and the same on `ZY22GC3BM4`. Record: D-POCO bonded to D-HU's radio (`Navegadortz2`) yes or no;
  D-MOTO bonded to D-POCO (`POCO X3 NFC`) yes or no; `MOTO_MAC` from D-POCO's list, or "redacted".
- **P4, one message to the operator**, only for what P3 found missing: pair D-MOTO with `POCO X3 NFC`;
  keep D-MOTO unlocked on its home screen during Stage 2. If nothing is missing, send nothing.

No forget in Android Auto is needed on either phone. H3 and H4 clear D-POCO's endpoint with a type 10,
and P1 measures what D-MOTO holds.

### Stage 1: D-HU head unit, D-POCO phone

Backup, then write the Stage 1 baseline and `clear_new`, app stopped. `hu_read s1-start` and record it.
D-MOTO Bluetooth off (`adb -s ZY22GC3BM4 shell svc bluetooth disable`) for the whole stage.

**H1. The IP record: two creates prove it, two reads do not. (6 min, no phone)**

D-POCO in airplane mode (`ph_down`) for all of H1.

1. Clean start: `adb -s $HU shell am force-stop $PKG`, `launch`, `sleep 30`, `huexit`,
   `adb -s $HU shell am force-stop $PKG`. This removes any group left from before. Then `clear_new`.
2. **H1-c1 (create).** `cap_on H1-c1`, `launch`, `sleep 3`, `mark H1-c1-start`, `sleep 35`,
   `mark H1-c1-end`, `huexit`, `cap_off`. `hu_read H1-c1`.
3. **H1-c2 (create).** `adb -s $HU shell am force-stop $PKG`, `cap_on H1-c2`, `launch`, `sleep 3`,
   `mark H1-c2-start`, `sleep 35`, `mark H1-c2-end`, `cap_off`, `adb -s $HU shell am force-stop $PKG`
   (force-stop, not exit: the group must survive for the reads). `hu_read H1-c2`.
4. Remove the IP record only: `hu_kv "del string wifi-direct-last-ip" "del string wifi-direct-last-ip-psk-digest" "del boolean wifi-direct-ip-repeated"`.
5. **H1-r1 (read).** `cap_on H1-r1`, `launch`, `sleep 3`, `mark H1-r1-start`, `sleep 35`, `mark H1-r1-end`,
   `cap_off`, `adb -s $HU shell am force-stop $PKG`. `hu_read H1-r1`.
6. **H1-r2 (read).** Same as step 5 with id `H1-r2`.

PASS, every condition:

- c1 and c2: `createGroup SUCCESS` = 1 and `is already up from` = 0 in each window. (A read in c1 is
  allowed: c1 needs no create. Record which.)
- c1: `group address ip=192.168.49.1` = 1, with `first reading of the WiFi Direct group's address at` = 1.
- c2: `group address ip=192.168.49.1` = 1, with `name, BSSID and address repeat` on that line.
  `settings-H1-c2.xml`: `wifi-direct-last-ip` = `192.168.49.1` and `wifi-direct-ip-repeated` = `true`.
- r1 and r2: `is already up from` = 1 and `createGroup SUCCESS` = 0 in each window. r1 line carries
  `first reading`. After r2, `wifi-direct-ip-repeated` is `false` or absent.
- In all four: `group address ip=` = 1 per window, though `SUCCESS - Providing credentials to listener`
  is >= 2. Record both counts. `group address ip=unread` = 0.

FAIL: any condition above not met. If `group address ip=unread` appears, or an IP other than
`192.168.49.1`, say so first: the interface read failed and the fallback was not graded.

**H2. A proven fixed IP still advertises, and the phone reconnects by WPP over TCP. (6 min)**

State from H1: the group from c2 is up (force-stopped app), and the IP record from r1 and r2 holds
`192.168.49.1` with its passphrase digest.

1. Seed, app stopped: `hu_kv "set boolean wifi-direct-ip-repeated true" "set string wifi-direct-last-identity-verdict STABLE"`.
   `hu_read H2-seed`: `wifi-direct-last-ip` = `192.168.49.1`, a 64-hex digest, repeated `true`, verdict `STABLE`.
   Also `adb -s $HU shell dumpsys wifip2p | grep -E "groupFormed|isGroupOwner"`: both `true`. If not,
   the group did not survive: H2, H3 and H4 are UNTESTABLE; say so and go to Stage 2.
2. `ph_up`. **H2-c1:** `cap_on H2-c1`, `launch`, `sleep 3`, `mark H2-c1-start`. `waitfor $OUT/H2-c1-hu.txt "SSL handshake complete" 150`.
   Then `sleep 20`, `mark H2-c1-end`, `cap_off`, `adb -s $HU shell am force-stop $PKG`.
3. **H2-c2** and **H2-c3**: the same as step 2 with those ids. No seeding between them.

PASS, every condition:

- c1: `is already up from` = 1; `group address ip=192.168.49.1 stable=yes` = 1; every
  `SUCCESS - Providing credentials to listener` line carries `identity stable=yes`;
  `NativeAA: advertising WPP over TCP at 192.168.49.1:5299` >= 1; `SSL handshake complete` >= 1 within 150 s.
- c1, after the window: `hu_read H2-c1` shows all four `wifi-direct-advertised-endpoint-*` keys, `-ip` = `192.168.49.1`.
- c2 and c3: `WppTcpServer: connection from` >= 1, then `SSL handshake complete` within 150 s of the start marker.
  Phone: `Trying to start WPP on TCP with configuration` >= 1 with `ipAddress=192.168.49.1`;
  `NETWORK_NOT_FOUND`, `TCP_SOCKET_CONNECTION_FAILED`, `BSSID_MISMATCH` all 0.
- All three: `needs this head unit forgotten` = 0; `connection-issue-stale-endpoint` absent or `0` after c3.

Record for each cycle the seconds from the start marker to `SSL handshake complete`, and the route
(`WppTcpServer: connection from` or `NativeAA: Connection accepted from`).

FAIL: c1 does not advertise, or a reconnect needs a forget. A c2 or c3 that lands over Bluetooth with
no TCP dial and no phone error is not a FAIL; record it as "Bluetooth route" and keep going.

**H3. The point of the round: a moved group IP withholds, warns once and holds the banner. (4 min)**

State from H2: the group is up, D-POCO holds the endpoint `192.168.49.1:5299`, the app is force-stopped.

1. Seed the field fault, app stopped. The IP record says the group was at another subnet, and the
   endpoint went out there:
   `hu_kv "set string wifi-direct-last-ip 192.168.77.1" "set string wifi-direct-advertised-endpoint-ip 192.168.77.1"`.
   `hu_read H3-seed`: last IP `192.168.77.1`, repeated `true`, verdict `STABLE`, all four endpoint keys
   present with `-ip` `192.168.77.1`. If the endpoint keys are absent (H2 did not advertise), copy
   `-ssid` from `wifi-direct-last-group-ssid`, `-bssid` from `wifi-direct-last-group-bssid` and
   `-psk-digest` from `wifi-direct-last-ip-psk-digest`, and say so.
2. **H3:** `cap_on H3`, `launch`, `sleep 3`, `mark H3-start`. `waitfor $OUT/H3-hu.txt "SSL handshake complete" 150`.
   Then `sleep 20`, `mark H3-end`, `cap_off`, `adb -s $HU shell am force-stop $PKG`. `hu_read H3`.

PASS, every condition:

- `is already up from` = 1 (a read: the name-and-BSSID half is the seeded `STABLE`).
- `the WiFi Direct group came back but its address moved from 192.168.77.1 to 192.168.49.1` = 1, on the
  `group address ip=192.168.49.1 stable=no` line.
- `SUCCESS - Providing credentials to listener` >= 2, and none carries `identity stable=yes`.
- `the WPP endpoint advertised on the WiFi Direct group at 192.168.77.1` = **exactly 1**, though the
  deliveries are >= 2. Record both counts.
- `NativeAA: advertising WPP over TCP at` = 0; `not advertising WPP over TCP:` >= 1.
- `SSL handshake complete` >= 1 within 150 s (the session still forms, over Bluetooth).
- `settings-H3.xml`: `connection-issue-stale-endpoint` > 0 (the landing did not clear it);
  `wifi-direct-last-ip` = `192.168.49.1`; `wifi-direct-ip-repeated` `false` or absent; no
  `wifi-direct-advertised-endpoint-*` key.

Expected, recorded but not graded: D-POCO still holds H2's endpoint, so expect
`WppTcpServer: connection from` >= 1 followed by `WppTcpServer: rejecting this dial so the phone drops our endpoint and goes back to Bluetooth:`
>= 1, and on the phone `Trying to start WPP on TCP with configuration` >= 1. A dial that is served
(`connection from` followed by `SSL handshake complete` within 3 s and no `rejecting`) is a **FAIL**.

What a PASS looks like if the change did nothing: the `group address` line would not exist,
`advertising WPP over TCP at` would be >= 1, and the WARN count 0. So H3 cannot pass by accident.

**H4. The phone is clean after the type 10, and the banner still holds. (3 min)**

1. **H4:** no seeding. `cap_on H4`, `launch`, `sleep 3`, `mark H4-start`.
   `waitfor $OUT/H4-hu.txt "SSL handshake complete" 150`. Then `sleep 20`, `mark H4-end`, `cap_off`,
   `huexit`, `adb -s $HU shell am force-stop $PKG`. `hu_read H4`.

PASS: `but not yet seen across a restart` = 1 on the `group address` line (same IP, a read proves
nothing); `advertising WPP over TCP at` = 0; `WppTcpServer: connection from` = 0; `SSL handshake complete`
>= 1. Phone: `No WPP on TCP configuration found in storage` >= 1 and `Trying to start WPP on TCP with configuration` = 0.
`connection-issue-stale-endpoint` > 0 still.

If H3 logged no `rejecting this dial` line, grade H4's phone conditions as INCONCLUSIVE and say why.

**End of Stage 1.** Restore D-HU's backup as root (`rig-quirks/units/D-HU.md`), `chown` and `chmod 660`,
read it back, and diff against the round-start backup: MUST be empty. D-MOTO Bluetooth on again.

### Stage 2: D-POCO head unit, D-MOTO phone

Set `HU=4f4027e9` and `PH=ZY22GC3BM4`. D-HU: `huexit` against D-HU's serial, force-stop, then
`adb -s 27870808938846 shell svc bluetooth disable` and verify with `dumpsys bluetooth_manager`. Restore
D-HU's Bluetooth at the end of the stage. Back up D-POCO's `settings.xml`, then write the Stage 2 keys
with `set_prefs_runas_host.py` and read them back.

**P1. A real fixed-IP unit: withhold once after the update, then advertise and reconnect by TCP. (12 min)**

The addendum's A1 cycle on the candidate, four cycles. Reuse `scripts/a1.sh` from the addendum's data
folder with D-MOTO as the phone. Each cycle `P1-c` (c = 1 to 4):

1. `adb -s $HU shell am force-stop $PKG`, `cap_on P1-c`, `launch`, `sleep 3`, `mark P1-c-start`.
2. `waitfor $OUT/P1-c-hu.txt "SSL handshake complete" 150`. On TIMEOUT, mark the cycle `NO-SESSION` and go to step 5.
3. `sleep 20`.
4. `send ACTION_DISCONNECT` (the user exit, which removes the group).
5. `sleep 10`, `mark P1-c-end`, `cap_off`.

Stop rule: if c1 logs `never opened the Android Auto channel on radio [` >= 1, stop Stage 2 and mark P1
INCONCLUSIVE (the phone is bound to another head unit). If two cycles in a row are `NO-SESSION`, stop.

PASS, every condition:

- c1: `group address ip=192.168.49.1` = 1 with `first reading of the WiFi Direct group's address at`;
  `NativeAA: advertising WPP over TCP at` = 0; `not advertising WPP over TCP:` >= 1; session formed.
- c2: `createGroup SUCCESS` >= 1 and `is already up from` = 0; `group address ip=192.168.49.1 stable=yes` = 1;
  `NativeAA: advertising WPP over TCP at 192.168.49.1:5299` >= 1; session formed.
- c3 and c4: `WppTcpServer: connection from` >= 1, then `SSL handshake complete` within 150 s. Phone:
  `Trying to start WPP on TCP with configuration` >= 1; `NETWORK_NOT_FOUND`, `TCP_SOCKET_CONNECTION_FAILED`,
  `BSSID_MISMATCH` all 0.
- All four: `needs this head unit forgotten` = 0; `group address ip=unread` = 0.

Record per cycle the seconds from start marker to SSL and the route. Compare with the addendum on `main`:
SSL in 10.3 s, 8.7 s, 8.0 s, and the endpoint advertised in cycle 1.

If c1 shows the phone dialling a stored endpoint from an earlier round (phone `Trying to start WPP on TCP`
>= 1 in c1), expect a `rejecting this dial` line in c1. Record it; it does not change the grade.

FAIL: c2 does not advertise, or c3 or c4 does not reconnect by TCP with a phone-side error present.

**End of Stage 2.** `huexit` on D-POCO (never a bare force-stop), force-stop, restore D-POCO's backup
and diff it: MUST be empty. D-HU Bluetooth on. D-POCO `dumpsys wifip2p | grep isGroupOwner`: false.

## 8. Do not re-run

- D-POCO's group shape on `main` (addendum A0: IP holds, BSSID holds) and its `main` reconnects (A1 PASS).
- D-HU's BSSID move on a create and the BSSID repeat on a read (`wpp-endpoint-depoison` rounds 4 and 5).
- The type 10 retraction itself (measured before this thread). H3 records it, it does not grade it.
- The JVM grade cases (3035 tests in the build gate).

## 9. Report back

1. H3: the WARN count against the delivery count, the `group address` line, the endpoint count
   (advertised or withheld), and the banner stamp after the landing.
2. P1: which cycle first advertised, and whether c3 and c4 reconnected by WPP over TCP (seconds to SSL).
3. H1: `wifi-direct-ip-repeated` after the two creates and after the two reads, and any
   `group address ip=` value other than `192.168.49.1`.

The block below holds one string per line. Each line from `WifiDirectManager: group address ip=` to
`AutomationMarker: ` re-greps with `grep -F -r` on `app/src` at `93a59f7a`. The four fragments are the
verbatim parts of the two run-time lines in section 6. The `phone-strings` block after it holds the seven
Gearhead strings that the runs grep in the phone capture. They are not in `app/src`, so do not re-grep them there.

```phone-strings
No WPP on TCP configuration found in storage
Trying to start WPP on TCP with configuration
NETWORK_NOT_FOUND
TCP_SOCKET_CONNECTION_FAILED
BSSID_MISMATCH
Restarting WPP over TCP
THROTTLE_LIMIT_EXCEEDED
```
