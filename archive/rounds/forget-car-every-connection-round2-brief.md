# forget-car-every-connection, round 2 brief

Published name: `forget-car-every-connection-round2-brief.md`. Results go in
`forget-car-every-connection-round2-results.md`. Evidence: add the asset
`forget-car-every-connection-round2-captures.zip` to the existing release `rig-evidence-forget-car-every-connection`.

Estimated time: 15 min pre-flight and build, 30 min Stage 1 (D-HU), 10 min Stage 2 (D-POCO as head unit).

## 1. Build and baseline

- **Candidate:** `fix/forget-car-every-connection` at **`c06d35b5`**
  (`c06d35b576d869c27987415f6d2f2d9a555cdd66`), tree `5d2ba66510099f43eebce69d401d61899247240b`.
  Two commits on `main` `71375a68`: `19849d78` (group IP grade) and `c06d35b5` (stale endpoint banner).
- **History was rewritten since round 1.** Round 1 tested `93a59f7a`. Three fix rounds then went into the
  same two commits by autosquash, on the same base. A worktree from round 1 is stale; make a new one.
- **Baseline:** none built. Round 1's captures on `93a59f7a` are the comparison for H4 and H5.

```bash
git ls-remote fork fix/forget-car-every-connection   # MUST print c06d35b576d869c27987415f6d2f2d9a555cdd66
git fetch fork fix/forget-car-every-connection
git worktree add ../ohu-wt-fcec-c06d35b5 c06d35b576d869c27987415f6d2f2d9a555cdd66
git -C ../ohu-wt-fcec-c06d35b5 rev-parse 'HEAD^{tree}'   # MUST print 5d2ba66510099f43eebce69d401d61899247240b
cp <main checkout>/local.properties ../ohu-wt-fcec-c06d35b5/   # a new worktree has none
```

If `ls-remote` prints `93a59f7a...`, the candidate is not on the fork yet. Stop the round and say so.

Build with `rig-toolkit/build_hur_cool.sh` and `HUR_DIR=../ohu-wt-fcec-c06d35b5`. Copy the APK out of `apks/`
at once. Run `rig-toolkit/run_unit_tests.sh` on the same worktree: expect **3041** tests, 0 failures.
A build or test failure stops the round.

Install with `adb -s <unit> install -r` on **D-HU and D-POCO**. Identity gate on each unit, both MUST hold:

```bash
send ACTION_QUERY_STATE     # data= line MUST carry commit c06d35b5 (a "-dirty" suffix from .WORKTREE-INFO is fine)
unzip -p <pulled apk> 'classes*.dex' | strings | grep -cF raisesStaleEndpointRecord   # MUST be >= 1
```

`raisesStaleEndpointRecord` is new after round 1. `93a59f7a` does not carry it.

## 2. What this is and why

A field unit (QUALCOMM Bengal, Android 11, Native AA over WiFi Direct) needs "forget this car" in
Android Auto after every disconnect. Its group name and BSSID repeat on every create, but the group IP
moves to a new subnet each time. So each handshake gave the phone a WPP-over-TCP endpoint (field 6) and
STATIC credentials (field 5) at an IP that the next create killed. The phone then dialled a dead IP, and
only a forget cleared it.

The candidate grades the group IP too. The IP must repeat across two **creates**. A read of a surviving
group does not count, because it repeats by construction. When an advertised endpoint's IP moves, the app
logs one WARN and raises the `PHONE_HOLDS_STALE_ENDPOINT` banner once. A Bluetooth landing does not clear
that banner until a create proves the IP.

Round 1 found one defect, and the fix rounds added two more changes:

1. **A read was graded as a create** (round 1 H1-r2, H2-c1, H4). The grade ran before the "already up"
   decision flagged the group as a read. Now a group is **undecided** from the manager's start or stop
   until a bring-up chooses to adopt or create. An undecided delivery grades as a read, writes no record
   and logs only at DEBUG. So at INFO the one `group address ip=` line of a read now prints **after**
   `is already up from`.
2. **A delivery with no address no longer takes the per-group latch**, so a later delivery with an address
   still records the group.
3. **No false banner after the update.** A phone that holds a valid endpoint from an older build dials the
   group's live IP. While the group is only `unproven`, the type 10 rejection clears the phone's record,
   so nothing needs a forget. The server now logs
   `WppTcpServer: this dial reached the group's live address, so the rejection leaves nothing to forget`
   and raises no banner. A moved group (`no`), the access point, or a dial that cannot be rejected still
   raises it.

## 3. What is different about this round

- **H5 is new: the false banner after the update.** It clears the IP record so that the group grades
  `unproven`, while D-POCO still holds the endpoint from H2. On `93a59f7a` this dial raised the banner.
- **H6 is new: a create releases the banner.** H4 proves a read does not clear it. H6 proves a create
  does, so the banner cannot hold forever.
- **H2 and P1 phone conditions changed.** Round 1 graded phone errors from the whole capture. The phone
  dials the stopped head unit between cycles, so errors before the relaunch are expected. This round grades
  the phone only from the lines after the launch, and grades the route, not the error count.
- **`NETWORK_NOT_FOUND` matches a Gearhead scan event** (`WIRELESS_WIFI_SCAN_RESULTS_NETWORK_NOT_FOUND`).
  The helper `pnf` excludes it.
- **The window rule changed.** The app prints read-path lines up to 300 ms before the start marker. Every
  head unit count in this round runs from the **start of the capture to the end marker** (`upto`). Each
  capture holds exactly one launch.
- **No `su` on D-HU.** adbd runs as root, so `hu_kv` runs the pushed script with `sh`.
- **Paths:** scripts are in `rig-toolkit/`. Output goes to `rig-data/rounds/forget-car-every-connection-round2/`.
  Reuse round 1's `fc_lib.sh`, `h1.sh` to `h4.sh` and `p1.sh` from
  `rig-data/rounds/forget-car-every-connection-round1/scripts/`, with the changes in sections 5 and 7.
- **Unchanged from round 1:** no rig unit moves its group IP, so H3 seeds the move. D-HU's BSSID moves on
  every create, so H2 to H5 seed `wifi-direct-last-identity-verdict` = `STABLE`. On a D-HU create the label
  reads `stable=no` even when the IP repeated; grade the IP from `settings.xml`.
- **Stat D-HU's `shared_prefs/` first** (`rig-quirks/units/D-HU.md`). The app writes the keys this round grades.
- **Role order:** D-POCO is the phone in Stage 1 and the head unit in Stage 2. End Stage 2 with
  `headunit://exit` on D-POCO, never a bare force-stop.

## 4. Settings keys

Back up each head unit's `settings.xml` before its stage and diff it against the round start. State the
delta in Setup notes. Leave the rig's audio keys as they are (deliberate worst case). No run grades
audio or video, so leave any video cap as it is too. `log-level` 2 (INFO) carries every line this round
greps. `ACTION_LOG_MARKER` is not gated on this branch.

**D-HU, Stage 1 baseline** (write once, then each run writes only what it names):

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<boolean name="wifi-direct-stable-identity" value="true" />
<string name="static-p2p-bssid">0</string>
<int name="log-level" value="2" />
<int name="onboarding-version" value="2" />
```

Keep D-HU's current `native-poke-bt-macs` (D-POCO) and `native-poke-all-paired`. Record both. Delete the
seven new keys and the stamp (`clear_new`):

| Key | Type | Meaning |
|---|---|---|
| `wifi-direct-last-ip` | string | the group IP last recorded |
| `wifi-direct-last-ip-psk-digest` | string | its passphrase digest |
| `wifi-direct-ip-repeated` | boolean | `true` only after a create saw the same IP |
| `wifi-direct-advertised-endpoint-ssid` / `-psk-digest` / `-bssid` / `-ip` | string | the endpoint last advertised |
| `connection-issue-stale-endpoint` | long | the banner stamp, epoch ms; `0` or absent means no banner |

**D-POCO as head unit, Stage 2** (write with `rig-toolkit/set_prefs_runas_host.py`):

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<boolean name="wifi-direct-stable-identity" value="true" />
<set name="native-poke-bt-macs" />
<boolean name="native-poke-all-paired" value="true" />
<int name="native-driver-selection-mode" value="0" />
<int name="onboarding-version" value="2" />
<int name="log-level" value="2" />
```

D-POCO redacts D-MOTO's address, so the wake list is empty and `native-poke-all-paired` is `true` (as in
round 1). Delete the seven new keys and `connection-issue-stale-endpoint`.

## 5. Helpers

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846            # Stage 1 D-HU; Stage 2 D-POCO 4f4027e9
PH=4f4027e9                  # Stage 1 D-POCO; Stage 2 D-MOTO ZY22GC3BM4
OUT=rig-data/rounds/forget-car-every-connection-round2; mkdir -p $OUT
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1"; }
huexit() { adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit"; sleep 5; }

# launch: records the phone's clock first, so phone greps can start at the launch
launch() { PHT=$(adb -s $PH shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'); echo "PHT=$PHT"; sleep 0.3
  adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity; }

# captures: start before the launch; the phone stream does not clear the phone's buffer
cap_on() { adb -s $HU logcat -c; stdbuf -oL adb -s $HU logcat -v threadtime > $OUT/$1-hu.txt & HUPID=$!
  stdbuf -oL adb -s $PH logcat -v threadtime -T 1 -b main,system,crash,events > $OUT/$1-phone.txt & PHPID=$!; sleep 2; }
cap_off() { sleep 2; kill $HUPID $PHPID 2>/dev/null; }

waitfor() { local n=0; while [ $n -lt $3 ]; do grep -aqF "$2" "$1" && { echo $n; return 0; }; sleep 5; n=$((n+5)); done; echo TIMEOUT; return 1; }

# head unit greps: from the start of the capture to "<id>-end"
upto()  { awk -v e="AutomationMarker: $2-end" '{print} index($0,e){exit}' "$1"; }
cnt()   { upto "$1" "$2" | grep -acF -- "$3"; }                              # cnt FILE ID STRING
lno()   { upto "$1" "$2" | grep -anF -- "$3" | head -1 | cut -d: -f1; }      # first line number, empty if none
line()  { upto "$1" "$2" | grep -aF -- "$3" | head -1; }                     # first matching line

# phone greps: only lines at or after the launch ($PHT, phone clock; threadtime starts "MM-DD HH:MM:SS.mmm")
ph_after() { awk -v t="$2" 'substr($0,1,18) >= t' "$1"; }
pcnt() { ph_after "$1" "$2" | grep -acF -- "$3"; }                           # pcnt FILE PHT STRING
pnf()  { ph_after "$1" "$2" | grep -aF NETWORK_NOT_FOUND | grep -avcF SCAN_RESULTS_NETWORK_NOT_FOUND; }

# settings on D-HU (app stopped). Each arg is "set TYPE KEY VALUE" or "del TYPE KEY". adbd is root: no su.
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
  adb -s $HU shell "sh /data/local/tmp/fc_kv.sh"
}
IPKEYS="del string wifi-direct-last-ip|del string wifi-direct-last-ip-psk-digest|del boolean wifi-direct-ip-repeated"
EPKEYS="del string wifi-direct-advertised-endpoint-ssid|del string wifi-direct-advertised-endpoint-psk-digest|del string wifi-direct-advertised-endpoint-bssid|del string wifi-direct-advertised-endpoint-ip"
clear_new() { local IFS='|'; hu_kv $IPKEYS $EPKEYS "del long connection-issue-stale-endpoint"; }
clear_ip()  { local IFS='|'; hu_kv $IPKEYS "del long connection-issue-stale-endpoint"; }
# the stamp, as a number: 0 when absent
stamp() { grep -aoE 'connection-issue-stale-endpoint" value="[0-9]+' $OUT/settings-$1.xml | grep -oE '[0-9]+$' || echo 0; }

ph_up() { adb -s $PH shell cmd connectivity airplane-mode disable; sleep 2
  adb -s $PH shell svc wifi enable; sleep 0.3; adb -s $PH shell svc bluetooth enable; sleep 5
  adb -s $PH shell dumpsys bluetooth_manager | grep -m1 -E "^ *state:"; adb -s $PH shell dumpsys wifi | grep -m1 "Wi-Fi is"; }
ph_down() { adb -s $PH shell cmd connectivity airplane-mode enable; sleep 5; }
```

Copy `th_gate`, `th_watch` and `th_report` from `archive/rounds/projection-teardown-and-relays-round1-brief.md`
(`ptr_lib.sh`) and call `RUN=<id> th_gate` before every run. Report `th_report <id>` in each run's section.
Count verdicts stand whatever the temperature. Never run two adb calls against one unit at the same time.

## 6. The deciding lines

Each string was checked with `grep -F -r` on `app/src` at `c06d35b5`. All are INFO or WARN. Two lines are
built at run time; the runs grep their full text in the log, and the `decisive-strings` block lists only
their verbatim source fragments. The phone strings are Gearhead's and are in the `phone-strings` block.

**Head unit capture** (`$OUT/<id>-hu.txt`, `upto` the end marker):

| Line | What it means |
|---|---|
| `WifiDirectManager: group address ip=` | the IP grade for this group, once per group at INFO |
| `first reading of the WiFi Direct group's address at` (run time) | no IP record yet; grade `unproven` |
| `the WiFi Direct group came back but its address moved from` (run time) | the IP moved; grade `no` |
| `but not yet seen across a restart` | same IP, not yet proven by a create |
| `name, BSSID and address repeat` | the IP half passed (the label still carries the BSSID half) |
| `SUCCESS - Providing credentials to listener` | one per delivery; `identity stable=<label>` is the verdict sent |
| `the WPP endpoint advertised on the WiFi Direct group at` | WARN: the advertised IP moved |
| `needs this head unit forgotten` | the same WARN, short form for counts |
| `this dial reached the group's live address, so the rejection leaves nothing to forget` | new: a refused dial raised no banner |
| `not withdrawing the endpoint because the` | a refused dial with no Bluetooth route; this one still raises the banner |
| `showing the connection issue banner for PHONE_HOLDS_STALE_ENDPOINT` | the banner is on screen |
| `NativeAA: advertising WPP over TCP at` | field 6 sent |
| `not advertising WPP over TCP:` | field 6 withheld |
| `WppTcpServer: connection from` | the phone dialled the endpoint |
| `WppTcpServer: rejecting this dial so the phone drops our endpoint and goes back to Bluetooth:` | type 10 sent |
| `is already up from` | the group was read, not created |
| `createGroup SUCCESS` | the group was created |
| `NativeAA: Connection accepted from` | the phone came back over Bluetooth |
| `SSL handshake complete` | session formed (INFO form) |
| `StationStandDown: ` | the station stand-down ran (recorded only) |
| `never opened the Android Auto channel on radio [` | the phone answers the poke but never opens AA |
| `MATCH! Starting AapService` | self wake (discard only with group churn) |
| `AutomationReceiver: ` / `AutomationMarker: ` | a verb or a marker landed; a missing line voids the step |

**Phone capture** (`$OUT/<id>-phone.txt`, only lines at or after `$PHT` of that run). Report the Gearhead
version (`dumpsys package com.google.android.projection.gearhead | grep versionName`). A 0 can mean a renamed string.

| Line | What it means |
|---|---|
| `WPP on TCP connected to the WiFi network` | the phone reached the endpoint (seen in round 1 P1-c3) |
| `Trying to start WPP on TCP with configuration` | the phone dials a stored endpoint (did not print in every TCP cycle) |
| `No WPP on TCP configuration found in storage` | the phone holds no endpoint |
| `TCP_SOCKET_CONNECTION_FAILED` / `BSSID_MISMATCH` / `pnf` | a stored endpoint failed (recorded only) |

## 7. Runs

### Pre-flight (one batch, before Stage 1)

- **P1, read only.** `rig-toolkit/rig_preflight.sh D_HU:wifi,bt D_POCO:wifi,bt D_MOTO:wifi,bt`. D-POCO and
  D-MOTO at home (`dumpsys window | grep mCurrentFocus`; fix with `input keyevent KEYCODE_HOME`). D-POCO:
  `dumpsys wifip2p | grep isGroupOwner` MUST be false (else `svc wifi disable`, `svc wifi enable`).
- **P2.** D-HU: `adb -s 27870808938846 shell 'stat -c "%U:%G %a" /data/data/com.andrerinas.headunitrevived/shared_prefs'`.
  Record it. If the owner is not the app's uid, `chown` it to the app's uid:gid and record both reads.
- **P3, read only.** `adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -A12 "Bonded devices"` and the
  same on `ZY22GC3BM4`. Record: D-POCO bonded to `Navegadortz2` yes or no; D-MOTO bonded to `POCO X3 NFC` yes or no.
- **P4, one message to the operator**, only for what P3 found missing: pair D-MOTO with `POCO X3 NFC`, and
  keep D-MOTO unlocked on its home screen during Stage 2. If nothing is missing, send nothing.

No forget in Android Auto is needed on either phone.

### Stage 1: D-HU head unit, D-POCO phone

Backup, write the Stage 1 baseline and `clear_new`, app stopped. `hu_read s1-start` and record it.
D-MOTO Bluetooth off (`adb -s ZY22GC3BM4 shell svc bluetooth disable`) for the whole stage.

**H1. The IP record: two creates prove it, two reads do not. (6 min, no phone)**

D-POCO in airplane mode (`ph_down`) for all of H1. The phone helpers still read D-POCO's clock.

1. Clean start: `adb -s $HU shell am force-stop $PKG`, `launch`, `sleep 30`, `huexit`,
   `adb -s $HU shell am force-stop $PKG`, `clear_new`.
2. **H1-c1 (create).** `cap_on H1-c1`, `launch`, `sleep 3`, `mark H1-c1-start`, `sleep 35`,
   `mark H1-c1-end`, `huexit`, `cap_off`. `hu_read H1-c1`.
3. **H1-c2 (create).** `adb -s $HU shell am force-stop $PKG`, `cap_on H1-c2`, `launch`, `sleep 3`,
   `mark H1-c2-start`, `sleep 35`, `mark H1-c2-end`, `cap_off`, `adb -s $HU shell am force-stop $PKG`
   (force-stop, not exit: the group must survive for the reads). `hu_read H1-c2`.
4. Remove the IP record only: `(IFS='|'; hu_kv $IPKEYS)`.
5. **H1-r1 (read).** `cap_on H1-r1`, `launch`, `sleep 3`, `mark H1-r1-start`, `sleep 35`, `mark H1-r1-end`,
   `cap_off`, `adb -s $HU shell am force-stop $PKG`. `hu_read H1-r1`.
6. **H1-r2 (read).** Same as step 5 with id `H1-r2`.

PASS, every condition (`F=$OUT/<id>-hu.txt`):

- c1 and c2: `cnt $F <id> "createGroup SUCCESS"` = 1 and `cnt $F <id> "is already up from"` = 0.
- c1: `cnt $F H1-c1 "group address ip=192.168.49.1"` = 1, and that line carries
  `first reading of the WiFi Direct group's address at 192.168.49.1`.
- c2: the `group address ip=192.168.49.1` line carries `name, BSSID and address repeat`.
  `settings-H1-c2.xml`: `wifi-direct-last-ip` = `192.168.49.1` and `wifi-direct-ip-repeated` = `true`.
- r1 and r2: `is already up from` = 1 and `createGroup SUCCESS` = 0. `lno $F <id> "group address ip="` is
  **greater than** `lno $F <id> "is already up from"` (the INFO grade follows the read decision).
- r1: the `group address` line carries `first reading`. `settings-H1-r1.xml`: `wifi-direct-ip-repeated`
  `false` or absent.
- r2: the `group address` line does **not** carry `stable=yes`. `settings-H1-r2.xml`:
  `wifi-direct-ip-repeated` **`false` or absent** (round 1 failed here with `true`).
- All four: `group address ip=` = 1 per capture, and `SUCCESS - Providing credentials to listener` >= 2.
  Record both counts. Every `group address ip=` line names `192.168.49.1`.

FAIL: any condition not met. A capture with deliveries >= 2 and no `group address ip=` line is a FAIL: the
group was never recorded. Record for r1 and r2, not graded: `cnt $F <id> "StationStandDown: "`.

**H2. A proven fixed IP still advertises, and the phone reconnects by WPP over TCP. (6 min)**

State from H1: the group from c2 is up (force-stopped app). The IP record from r1 holds `192.168.49.1`.

1. Seed, app stopped: `hu_kv "set boolean wifi-direct-ip-repeated true" "set string wifi-direct-last-identity-verdict STABLE"`.
   `hu_read H2-seed`: last IP `192.168.49.1`, a 64-hex digest, repeated `true`, verdict `STABLE`.
   `adb -s $HU shell dumpsys wifip2p | grep -E "groupFormed|isGroupOwner"`: both `true`. If not, the
   group did not survive: H2 to H6 are UNTESTABLE; say so and go to Stage 2.
2. `ph_up`. **H2-c1:** `cap_on H2-c1`, `launch`, `sleep 3`, `mark H2-c1-start`.
   `waitfor $OUT/H2-c1-hu.txt "SSL handshake complete" 150`. Then `sleep 20`, `mark H2-c1-end`, `cap_off`,
   `adb -s $HU shell am force-stop $PKG`. `hu_read H2-c1`.
3. **H2-c2** and **H2-c3**: the same as step 2 with those ids. No seeding between them.

PASS, every condition:

- c1: `is already up from` = 1; `cnt $F H2-c1 "group address ip=192.168.49.1 stable=yes"` = 1 (round 1 read
  `unproven` here); the **last** `SUCCESS - Providing credentials to listener` line carries `identity stable=yes`;
  `NativeAA: advertising WPP over TCP at 192.168.49.1:5299` >= 1; `SSL handshake complete` >= 1.
- c1: `settings-H2-c1.xml` holds all four `wifi-direct-advertised-endpoint-*` keys, `-ip` = `192.168.49.1`.
- c2 and c3: `WppTcpServer: connection from` >= 1 and `SSL handshake complete` >= 1 within 150 s of the
  start marker. Phone: `pcnt $OUT/<id>-phone.txt "$PHT" "WPP on TCP connected to the WiFi network"` +
  `pcnt ... "Trying to start WPP on TCP with configuration"` >= 1.
- All three: `needs this head unit forgotten` = 0; `stamp H2-c3` = 0.

Record per cycle: seconds from the start marker to `SSL handshake complete`, the route (`WppTcpServer:
connection from` or `NativeAA: Connection accepted from`), the credential lines by label, and the phone's
`TCP_SOCKET_CONNECTION_FAILED`, `BSSID_MISMATCH` and `pnf` counts after `$PHT` (not graded: the phone can
dial in the 1 to 3 s before the app listens).

FAIL: c1 does not advertise or reads `unproven`, or c2 or c3 needs a forget. A c2 or c3 that lands over
Bluetooth with no TCP dial is not a FAIL; record it as "Bluetooth route".

**H5. The point of the round, part 1: no false banner after the update. (3 min)**

State from H2: the group is up, the app is force-stopped, D-POCO holds the endpoint `192.168.49.1:5299`.
This is a unit just updated from an older build: the IP record is gone, the phone's endpoint is valid.

1. Seed, app stopped: `clear_ip` (the three IP keys and the stamp; the endpoint keys stay, as H3 needs them).
   `hu_read H5-seed`: no `wifi-direct-last-ip`, no stamp, verdict `STABLE`, the four endpoint keys present.
2. **H5:** `cap_on H5`, `launch`, `sleep 3`, `mark H5-start`. `waitfor $OUT/H5-hu.txt "SSL handshake complete" 150`.
   Then `sleep 20`, `mark H5-end`, `cap_off`, `adb -s $HU shell am force-stop $PKG`. `hu_read H5`.

Gate: `cnt $F H5 "WppTcpServer: connection from"` >= 1. If it is 0, the phone did not dial: H5 is
INCONCLUSIVE; record it and go on. If `not withdrawing the endpoint because the` >= 1, the Bluetooth
listeners were not open at the dial, which still raises the banner by design: H5 is INCONCLUSIVE; record it.

PASS, every condition:

- `is already up from` = 1; the `group address ip=192.168.49.1` line carries `stable=unproven` and
  `first reading of the WiFi Direct group's address at 192.168.49.1`.
- `this dial reached the group's live address, so the rejection leaves nothing to forget` >= 1, and
  `WppTcpServer: rejecting this dial so the phone drops our endpoint and goes back to Bluetooth:` >= 1.
- `needs this head unit forgotten` = 0; `showing the connection issue banner for PHONE_HOLDS_STALE_ENDPOINT` = 0.
- `NativeAA: advertising WPP over TCP at` = 0; `not advertising WPP over TCP:` >= 1.
- `SSL handshake complete` >= 1 within 150 s (over Bluetooth).
- `stamp H5` = 0.
- Phone: `pcnt $OUT/H5-phone.txt "$PHT" "Trying to start WPP on TCP with configuration"` +
  `pcnt ... "WPP on TCP connected to the WiFi network"` >= 1 (the phone dialled).

What a PASS looks like if the change did nothing: on `93a59f7a` the refused dial raised the banner, so
`stamp H5` would be > 0 and the `this dial reached` line would not exist. H5 cannot pass by accident.

**H3. A moved group IP withholds, warns once and holds the banner. (3 min, regression guard)**

State from H5: the group is up, the app is force-stopped, the IP record holds `192.168.49.1`, and D-POCO's
endpoint is gone (H5's type 10).

1. Seed the field fault, app stopped:
   `hu_kv "set string wifi-direct-last-ip 192.168.77.1" "set string wifi-direct-advertised-endpoint-ip 192.168.77.1"`.
   `hu_read H3-seed`: last IP `192.168.77.1`, verdict `STABLE`, all four endpoint keys present with `-ip`
   `192.168.77.1`. If the endpoint keys are absent, copy `-ssid` from `wifi-direct-last-group-ssid`, `-bssid`
   from `wifi-direct-last-group-bssid` and `-psk-digest` from `wifi-direct-last-ip-psk-digest`, and say so.
2. **H3:** `cap_on H3`, `launch`, `sleep 3`, `mark H3-start`. `waitfor $OUT/H3-hu.txt "SSL handshake complete" 150`.
   Then `sleep 20`, `mark H3-end`, `cap_off`, `adb -s $HU shell am force-stop $PKG`. `hu_read H3`.

PASS, every condition:

- `is already up from` = 1; `lno ... "group address ip="` > `lno ... "is already up from"`.
- `the WiFi Direct group came back but its address moved from 192.168.77.1 to 192.168.49.1` = 1, on the
  `group address ip=192.168.49.1 stable=no` line.
- `SUCCESS - Providing credentials to listener` >= 2, and `identity stable=yes` = 0.
- `the WPP endpoint advertised on the WiFi Direct group at 192.168.77.1` = **exactly 1**. Record the
  delivery count beside it.
- `NativeAA: advertising WPP over TCP at` = 0; `not advertising WPP over TCP:` >= 1.
- `SSL handshake complete` >= 1 within 150 s.
- `settings-H3.xml`: `stamp H3` > 0; `wifi-direct-last-ip` = `192.168.49.1`; `wifi-direct-ip-repeated`
  `false` or absent; no `wifi-direct-advertised-endpoint-*` key.

Record, not graded: `WppTcpServer: connection from` (expected 0, since H5 cleared the phone's endpoint).

**H4. The point of the round, part 2: a read does not clear the banner. (3 min)**

1. **H4:** no seeding. `cap_on H4`, `launch`, `sleep 3`, `mark H4-start`.
   `waitfor $OUT/H4-hu.txt "SSL handshake complete" 150`. Then `sleep 20`, `mark H4-end`, `cap_off`,
   `huexit` (this removes the group for H6), `adb -s $HU shell am force-stop $PKG`. `hu_read H4`.

PASS, every condition:

- `is already up from` = 1 and `createGroup SUCCESS` = 0; `lno ... "group address ip="` > `lno ... "is already up from"`.
- The `group address ip=192.168.49.1` line carries `stable=unproven` and `but not yet seen across a restart`.
- `NativeAA: advertising WPP over TCP at` = 0; `WppTcpServer: connection from` = 0; `SSL handshake complete` >= 1.
- `stamp H4` = `stamp H3` (the landing did not clear it; round 1 read 0 here).
- `settings-H4.xml`: `wifi-direct-ip-repeated` `false` or absent (round 1 read `true` here).
- Phone: `pcnt $OUT/H4-phone.txt "$PHT" "No WPP on TCP configuration found in storage"` >= 1 and
  `pcnt ... "WPP on TCP connected to the WiFi network"` = 0.

**H6. A create proves the IP and releases the banner. (3 min)**

State from H4: no group (the exit removed it), app stopped, the stamp from H3 still set.

1. **H6:** `cap_on H6`, `launch`, `sleep 3`, `mark H6-start`. `waitfor $OUT/H6-hu.txt "SSL handshake complete" 150`.
   Then `sleep 20`, `mark H6-end`, `cap_off`, `huexit`, `adb -s $HU shell am force-stop $PKG`. `hu_read H6`.

PASS, every condition:

- `createGroup SUCCESS` = 1 and `is already up from` = 0.
- The `group address ip=192.168.49.1` line carries `name, BSSID and address repeat` (`stable=no` is
  expected: D-HU's BSSID moved).
- `SSL handshake complete` >= 1 within 150 s; `WppTcpServer: connection from` = 0.
- `settings-H6.xml`: `wifi-direct-ip-repeated` = `true` and `stamp H6` = 0.

What a PASS looks like if the change did nothing: H6 alone would pass on `93a59f7a` too. H4 is the run that
separates the builds; H6 shows the banner does not stay forever. Grade them together in the summary.

**End of Stage 1.** Restore D-HU's backup as root (`rig-quirks/units/D-HU.md`), `chown` and `chmod 660`,
read it back, and diff against the round-start backup: MUST be empty. D-MOTO Bluetooth on again.

### Stage 2: D-POCO head unit, D-MOTO phone

Set `HU=4f4027e9` and `PH=ZY22GC3BM4`. D-HU: `huexit` against D-HU's serial, force-stop, then
`adb -s 27870808938846 shell svc bluetooth disable` and verify with `dumpsys bluetooth_manager`. D-HU's
radio reverted on before every cycle in round 1: re-disable it at each cycle start and record the reads.
Back up D-POCO's `settings.xml`, write the Stage 2 keys with `set_prefs_runas_host.py`, and read them back.

**P1. A real fixed-IP unit: withhold once after the update, then advertise and reconnect by TCP. (9 min)**

Three cycles. Adapt round 1's `p1.sh`. Each cycle has the id `P1-<c>` (c = 1 to 3, so `P1-1`, `P1-2`, `P1-3`):

1. `adb -s $HU shell am force-stop $PKG`, `cap_on P1-<c>`, `launch`, `sleep 3`, `mark P1-<c>-start`.
2. `waitfor $OUT/P1-<c>-hu.txt "SSL handshake complete" 150`. On TIMEOUT, mark the cycle `NO-SESSION` and go to step 5.
3. `sleep 20`.
4. `send ACTION_DISCONNECT` (the user exit, which removes the group).
5. `sleep 10`, `mark P1-<c>-end`, `cap_off`, `hu_read P1-<c>` (read with `run-as`; the app may run).

Stop rule: if c1 logs `never opened the Android Auto channel on radio [` >= 1, stop Stage 2 and mark P1
INCONCLUSIVE. If two cycles in a row are `NO-SESSION`, stop.

PASS, every condition:

- c1: `createGroup SUCCESS` >= 1; the `group address ip=192.168.49.1` line carries
  `first reading of the WiFi Direct group's address at 192.168.49.1`; `NativeAA: advertising WPP over TCP at` = 0;
  `not advertising WPP over TCP:` >= 1; `SSL handshake complete` >= 1.
- c1, only if `WppTcpServer: connection from` >= 1 (D-MOTO may still hold round 1's endpoint):
  `this dial reached the group's live address, so the rejection leaves nothing to forget` >= 1 and
  `stamp P1-1` = 0. If the dial count is 0, record "no dial" for this condition.
- c2: `createGroup SUCCESS` >= 1 and `is already up from` = 0; `group address ip=192.168.49.1 stable=yes` = 1;
  `NativeAA: advertising WPP over TCP at 192.168.49.1:5299` >= 1; `SSL handshake complete` >= 1.
- c3: `WppTcpServer: connection from` >= 1, then `SSL handshake complete` within 150 s of the start marker.
  Phone: `pcnt $OUT/P1-3-phone.txt "$PHT" "WPP on TCP connected to the WiFi network"` +
  `pcnt ... "Trying to start WPP on TCP with configuration"` >= 1.
- All three: `needs this head unit forgotten` = 0.

Record per cycle: seconds from the start marker to SSL, the route, and the phone's
`TCP_SOCKET_CONNECTION_FAILED`, `BSSID_MISMATCH` and `pnf` counts after `$PHT` (not graded). Compare with
round 1: c1 13.5 s, c2 10.4 s, c3 5.1 s.

FAIL: c1 advertises, c2 does not advertise, or c3 does not reconnect by TCP.

**End of Stage 2.** `huexit` on D-POCO (never a bare force-stop), force-stop, restore D-POCO's backup with
`rig-toolkit/restore_settings.sh` and diff it: MUST be empty. D-HU Bluetooth on. D-POCO
`dumpsys wifip2p | grep isGroupOwner`: false.

## 8. Do not re-run

- D-POCO's group shape on `main` (IP holds, BSSID holds) and its `main` reconnects (`samsung-driver-native` addendum).
- D-HU's BSSID move on a create and the BSSID repeat on a read (`wpp-endpoint-depoison` rounds 4 and 5).
- The type 10 retraction itself. H3 and H5 record it; they do not grade it as new.
- A second P1 TCP cycle (round 1 c4, 7.9 s). Three cycles decide.
- The JVM grade cases (3041 tests in the build gate).

## 9. Report back

1. H4 and H6: the stamp after H3, after H4 and after H6, and `wifi-direct-ip-repeated` after each.
2. H5: the `this dial reached` count, the stamp after the run, and the banner line count.
3. H1-r2 and H2-c1: the `group address` label and line order against `is already up from`, and
   `wifi-direct-ip-repeated` after r2.
4. P1: c1 withheld and c2 advertised, c3 route and seconds to SSL, and whether D-MOTO dialled in c1.
