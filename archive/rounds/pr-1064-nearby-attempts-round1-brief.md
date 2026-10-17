# nearby-attempts, round 1 brief: a late Nearby tunnel or a reused endpoint id must not attach to the wrong attempt

This round tests an outside contributor's change to the Google Nearby transport of Wireless Helper mode (`wifi-connection-mode=2`, `helper-connection-strategy=2`). It needs no other round first.

## 1. Build and baseline

Two APKs. Build each with `build_hur.sh` and copy it out of `apks/` the moment it is built, because the script deletes the previous APK (template section 7a, tooling). Name each file by its SHA.

| Arm | Code | Full SHA | Gate |
|---|---|---|---|
| **B** (baseline) | the PR's merge-base on `main` | `2ca3b1f1a807faa11ba8608fd8a905acc09c65a9` | 2689 JVM tests, 0 failures |
| **C** (candidate) | outside contributor's branch `codex/nearby-tunnel-lifecycle` | `32311d316abdd63081f8629b0dbefcfbb4033f68` | 2702 JVM tests, 0 failures |

```bash
git fetch https://github.com/emotionbug/open-headunit.git codex/nearby-tunnel-lifecycle
git checkout -B arm-C 32311d316abdd63081f8629b0dbefcfbb4033f68
git log --oneline 2ca3b1f1..HEAD | wc -l        # 3
git checkout -B arm-B 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9
```

The candidate is the PR head as it is. Nothing was rewritten.

**Why the baseline is the merge-base and not `main` 145a0c76.** The PR does not merge onto `main`: it conflicts in `CommManager.connect(socket)`. A merged arm would carry a conflict resolution that we wrote, not the PR. `main` is 8 commits past the merge-base. None of them touches `NearbyManager`, `NearbySocket`, `WifiLauncherHelper`, `ConnectionArbiter` or the USB launcher. Two of them touch code the Nearby socket passes through: `a70ba7e2` changes `CommManager.connect(socket)` itself, and `4c75aba3` changes `AapService`'s start commands. With `main` as the baseline, those two commits would sit in the difference between the arms. With the merge-base, the arms differ by the PR's three commits and nothing else. So a PASS here grades the PR as written. It says nothing about the PR after a rebase onto `main`; that needs its own round.

### 1a. Identity gate, per arm, before any run on that arm

```bash
adb -s $DEV shell pm path $PKG                 # pull that apk to ./installed.apk, then:
unzip -p installed.apk 'classes*.dex' | strings | grep -cF '<symbol>'
```

| Symbol | B | C |
|---|---|---|
| `NearbyAttemptGuard` | 0 | 1 or more |
| `ConnectionAdmission` | 0 | 1 or more |
| `HeldServerSocket` | 0 | 0 |

The source trees agree: `NearbyAttemptGuard` and `ConnectionAdmission` are in 0 files on B and in 2 and 3 files on C. `HeldServerSocket` is in 0 files on both, and in 4 on `main`, so a 0 also proves that neither APK is `main`. Also send `ACTION_QUERY_STATE` and record `commit`: it must start `2ca3b1f1` on B and `32311d31` on C. Record the md5 of both APKs from a real `adb pull` and a local `md5sum` (template section 5). The two must differ.

### 1b. The Wireless Helper build on the phone

The phone side of Nearby is the Wireless Helper app. Round 1 of `pr-1047-session-reconnect` could not run its Nearby case, because D-POCO had no helper installed. This round installs one.

Build the **debug** variant of upstream `andreknieriem/wireless-helper` at `8ac36c9bcc80731b78949c04fdc63178b5904f2f`. Its package is `com.andrerinas.wirelesshelper.debug`, which is debuggable, so `run-as` can write its settings. **Do not build the fork's `main` (`97f46f6`).** It still advertises the Nearby service id `com.andrerinas.hurev`, and the head unit looks for `com.andrerinas.openhu`, so the head unit would never find it. Upstream `8ac36c9` advertises `com.andrerinas.openhu`.

```bash
# in the wireless-helper checkout that hur-wifi-test-scripts/build_wireless_helper.sh builds
git fetch https://github.com/andreknieriem/wireless-helper.git main
git checkout --detach 8ac36c9bcc80731b78949c04fdc63178b5904f2f
git rev-parse HEAD                               # must print 8ac36c9bcc80731b78949c04fdc63178b5904f2f
hur-wifi-test-scripts/build_wireless_helper.sh   # the debug APK; the istartup-binder round used this script
```

If the script cannot build a given ref, run its Gradle command by hand on that checkout and say so in Setup notes. Install with `adb -s $PH install -r <apk>`. Record its md5. Do the same on D-MOTO before Stage U.

## 2. What this is and why it exists

The PR gives each Nearby connection attempt its own owner (`NearbyAttemptGuard`) and its own Nearby callbacks. Every callback, the 800 ms tunnel delay and the hand-off to `CommManager` now check that their attempt is still the current one. A new `ConnectionAdmission` makes the hand-off and the arbiter check one step under one lock. `NearbyManager.stop()` now cancels the tunnel job and closes the pending stream and pipes.

On the baseline, three paths can attach old resources to the wrong attempt:

- **The late tunnel.** After the bandwidth reaches HIGH, the tunnel job waits 800 ms (`NearbyManager: Waiting 800ms for phone state synchronization...`). On B nothing is checked after that wait. If the phone drops in the window, B still builds the tunnel (`NearbyManager: Initiating stream tunnel to`) and hands a dead socket to `CommManager`.
- **A stop in the window.** B's `stop()` does not cancel the tunnel job, so a stop in the window is followed by the same tunnel build.
- **A reused endpoint id.** B uses one shared set of callbacks, so a callback from an old attempt can act on a new attempt with the same endpoint id.

Our review found no blocking defect. It found two should-fix gaps and four notes. The ones this round can measure:

- **Finding 1 (should fix): a refused hand-off never retires the attempt.** When the arbiter refuses the Nearby socket (`ConnectionArbiter: the socket from 127.0.0.1 (WIRELESS_HANDSHAKE) refused while ... is in flight`), the PR closes the socket but keeps `activeEndpointId` and the connected endpoint. Discovery is already stopped, so nothing retires the attempt until the phone drops it. A later connect to the same id logs `NearbyManager: Already connected to <id>, ignoring duplicate request` and does nothing. The baseline has the same gap. The precondition is a USB plug-in that has spent its 60 s budget, so the Nearby stack stays armed while USB retries hold the arbiter.
- **Note 5: a session end does not retire the attempt.** If the AAP session ends and the Nearby endpoint stays up, `activeEndpointId` keeps its value, and a connect to the same id logs the same `Already connected to` line. The baseline has the same behaviour. Also, C's `stop()` lost its only log line, so on C a stop is visible only as the verb's `AutomationReceiver:` line.
- **Finding 2 (should fix)** is a publish that disconnects a live USB session while it holds two locks. **The rig cannot reach it:** the wired-session quiesce stops the wireless stack at SSL, so no Nearby publish can arrive during a live USB session. It stays with the JVM tests and the code review.

## 3. What is different about this round

### 3.1 Stages

| Stage | Plugged into the test PC | Runs |
|---|---|---|
| **N** | D-HU (head unit), D-POCO (phone with the helper) | R0, N0, N1, N2, N3 |
| **U** | D-MOTO (phone with the helper) on a cable; D-POCO (head unit and USB host) on wireless adb with the dongle on its OTG port | U1 |

Do Stage N first: N1 is the point of the round. Stage U is the last run and needs an operator for two plugs.

### 3.2 Rig facts that change the runs

- **Both units must be on the house network** (`Pegue Cdesta`). Nearby's WiFi upgrade uses the shared network: in `release-test` round 1, D-HU logged `MEDIUM_NOT_AVAILABLE ... WITHOUT_CONNECTED_WIFI_NETWORK` while its station was down. D-HU is always joined (`rig-quirks/topics/wifi.md`). Read both with `dumpsys wifi | grep -iE "mWifiInfo|SSID"` and record the SSID and frequency. If D-POCO is on another network, the run is UNTESTABLE until an operator joins it.
- **The helper path on this phone is unproven since August.** `release-test` round 1 (2026-08-21, Gearhead 17.5, helper 1.9.3) formed three Nearby sessions. Gearhead on D-POCO read `17.9.664004-release` in `projection-teardown-and-relays` round 3. N0 is the gate: if B forms no Nearby session there, every Stage N run is INCONCLUSIVE with the reason "Nearby helper sessions do not form on this phone", and Setup notes record both versions.
- **D-POCO's own head unit app stays stopped in Stage N**: `adb -s $PH shell am force-stop com.andrerinas.headunitrevived`. If a release Wireless Helper (`com.andrerinas.wirelesshelper`) is installed, force-stop it too, so only the debug helper advertises.
- **Every watcher reads the capture file**, polling every 0.05 s. A `logcat | grep` pipe never fires on this rig (`pr-1047` round 1), and a `tail -F` pipe fired 23 s late (`usb-version-retry` round 3). The file poll fired 150 to 450 ms after its line.
- **D-POCO (API 35) refuses `ACTION_START_WIRELESS_SCAN` right after a force-stop.** In Stage U, start our app on D-POCO with `am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`.
- **No tap on our app anywhere.** The helper's Connection Mode setting is written to its own settings file through `run-as`. Section 3.3 names the only hand steps.
- **The rig audio settings are a deliberate worst case** (`use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`). Do not change them. Delete `video-profile-starvation-cap` before every run.
- **Pre-registered INCONCLUSIVE outcomes**, so none is read as a failure: Stage N when N0's B arm forms no session; N1 or N2 on an arm that gets fewer than 2 window hits in its attempt budget; U1 when Nearby cannot reach HIGH with D-MOTO's Bluetooth off, or when the dongle never spends its 60 s budget, or when no Nearby hand-off lands inside a USB retry.

### 3.3 Hand steps (the only ones in the round)

| Id | Step | Reason no verb exists |
|---|---|---|
| H1 | Move D-POCO from the PC cable to wireless adb, then plug the dongle into D-POCO's OTG port; at the end, the reverse | A cable is hardware. |
| H2 | Unplug and replug the dongle once between the B arm and the C arm of U1 | A fresh plug-in starts a fresh 60 s USB budget. |
| H3 | Unlock D-MOTO once if it shows a PIN | adb has no way past a PIN (`rig-quirks/units/D-MOTO.md`). |
| H4 | Only if `exec-in` cannot write the helper's settings: open the helper on the phone and pick "Google Nearby (Beta)" under Connection Mode | The helper is a third-party app with no settings intent; `release-test` round 1 did exactly this once. |

The cue for each is a line on the executor's console that starts `OPERATOR:`, a terminal bell, and a timestamped line in `$OUT/hand-steps.log`. The executor then waits on a log line, never on a clock.

## 4. Settings keys

### 4.1 Our app, D-HU in Stage N

Write with the app stopped, with `hu_put` (section 6), from a backup taken at the start of the round. Read back before every launch.

| Key | Type | Value |
|---|---|---|
| `wifi-connection-mode` | int | `2` (Wireless Helper) |
| `helper-connection-strategy` | int | `2` (Google Nearby) |
| `auto-connect-last-session` | boolean | `false` (every connect in this round comes from the verb) |
| `log-level` | int | `2` (INFO; every line in section 5 prints at INFO) |
| `onboarding-version` | int | `2` |
| `kill-on-disconnect` | boolean | `false` |
| `video-profile-starvation-cap`, `native-aa-wireless`, `wifi-launcher-mode` | | delete |

Record the rig audio keys as found. Do not write them.

### 4.2 Our app, D-POCO as head unit in Stage U

Same table, plus:

| Key | Type | Value |
|---|---|---|
| `auto-start-on-usb` | boolean | `true` |
| `reopen-on-reconnection` | boolean | `true` |
| `use-libusb` | boolean | `false` |

D-POCO is not rooted. Write its file with `pocoput` (section 6), which sends the edited file to `run-as` on stdin. If that does not read back, use `hur-wifi-test-scripts/set_prefs_runas.sh` as `usb-version-retry` round 3 did, and say so in Setup notes. Name the backup `settings_backup_pr1064.xml`, never `*.bak`: SharedPreferences treats a `.bak` as an aborted write and reverts to it.

### 4.3 Runtime permissions for our app (both head units, once per install)

`NearbyManager.start()` refuses with `NearbyManager: Missing required location/bluetooth permissions. Skipping start.` without them.

```bash
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION BLUETOOTH_SCAN BLUETOOTH_CONNECT BLUETOOTH_ADVERTISE NEARBY_WIFI_DEVICES; do
  adb -s $HU shell pm grant $PKG android.permission.$p; done
```

### 4.4 The helper (phone), once per install

```bash
HPKG=com.andrerinas.wirelesshelper.debug
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION BLUETOOTH_CONNECT BLUETOOTH_ADVERTISE NEARBY_WIFI_DEVICES POST_NOTIFICATIONS; do
  adb -s $PH shell pm grant $HPKG android.permission.$p; done
adb -s $PH shell appops set $HPKG SYSTEM_ALERT_WINDOW allow
adb -s $PH shell am force-stop $HPKG
printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" '<map><int name="connection_mode" value="4" /></map>' > hprefs.xml
adb -s $PH exec-in "run-as $HPKG sh -c 'mkdir -p shared_prefs; cat > shared_prefs/WirelessHelperPrefs.xml'" < hprefs.xml
adb -s $PH shell run-as $HPKG cat shared_prefs/WirelessHelperPrefs.xml   # must show connection_mode value="4"
```

`connection_mode` 4 is Nearby in the helper's own code (`WirelessHelperService`: 0 NSD, 1 phone hotspot, 2 tablet hotspot, 3 WiFi Direct, 4 Nearby). If the read-back does not show 4, use H4 and read back again.

## 5. The lines that decide the runs

Each line was checked with `grep -rF` against `app/src/main` of both arms. All print at INFO. `AapService: session state <state>` is composed at run time; this brief greps the prefix `AapService: session state connect`, which matches `connecting` and `connected`.

| Line (fixed substring) | File | Meaning | B | C |
|---|---|---|---|---|
| `AutomationReceiver: ` | unit | a verb landed; the action follows in full, e.g. `com.andrerinas.openheadunit.ACTION_STOP_WIRELESS` | yes | yes |
| `AutomationMarker: ` | unit | run and step brackets | yes | yes |
| `NearbyManager: Starting Nearby (Discoverer only)...` | unit | discovery started | yes | yes |
| `NearbyManager: Missing required location/bluetooth permissions. Skipping start.` | unit | the permissions in 4.3 are missing; the run is void | yes | yes |
| `NearbyManager: Endpoint FOUND: ` | unit | `<model> (<id>)`; the id for the connect verb | yes | yes |
| `NearbyManager: Auto-connect check: Enabled=` | unit | ends with `activeEndpointId=<id or null>`; a non-null id after a session end is note 5's stale attempt | yes | yes |
| `AapService: Connecting to Nearby endpoint ` | unit | the connect verb reached the service | yes | yes |
| `NearbyManager: Requesting connection to endpoint: ` | unit | an attempt began | yes | yes |
| `NearbyManager: Already connected to ` with `, ignoring duplicate request` | unit | a connect met a stale attempt with the same id | yes | yes |
| `NearbyManager: Connection RESULT for ` | unit | Nearby answered the request | yes | yes |
| `NearbyManager: Connected successfully!` | unit | the endpoint is connected | yes | yes |
| `NearbyManager: Wi-Fi Bandwidth Upgrade successful (Quality: HIGH). Initiating stream tunnel...` | unit | HIGH reached; the tunnel job starts | yes | yes |
| `NearbyManager: Waiting 800ms for phone state synchronization...` | unit | **the window opens** | yes | yes |
| `NearbyManager: Initiating stream tunnel to ` | unit | **the window closed and the tunnel was built** | yes | yes |
| `NearbyManager: Starting AA handshake now. Input will block until stream arrives.` | unit | the socket goes to `CommManager` | yes | yes |
| `NearbySocket: Blocking read until InputStream is AVAILABLE via Nearby Payload` | unit | the AAP handshake waits on the tunnel | yes | yes |
| `NearbyManager: DISCONNECTED from ` | unit | the endpoint dropped (C prints it only for the current attempt) | yes | yes |
| `NearbyManager: Stopping discovery and disconnecting from any active endpoint...` | unit | B's `stop()`; **C prints nothing** | yes | **no** |
| `NearbyManager: Failed to hand over stream tunnel` | unit | C's hand-off threw | **no** | yes |
| `NearbyManager: Bandwidth upgrade timed out after 10s` | unit | HIGH never came | yes | yes |
| `ConnectionArbiter: ` with `the socket from 127.0.0.1` and ` refused while ` | unit | finding 1's refusal | yes | yes |
| `USB has tried for ` with `with no session; wireless comes back beside it` | unit | the USB plug-in spent its 60 s budget | yes | yes |
| `stood the wireless stack down until it ends` | unit | a USB claim stopped the wireless stack | yes | yes |
| `Found device already in accessory mode`, `Switching USB device to accessory mode` | unit | a USB attempt began | yes | yes |
| `AapService: session state ` | unit | then `connecting`, `connected` or `disconnected (...)`; `AapService: session state connect` matches the first two | yes | yes |
| `AapService: ACTION_END_SESSION_STAY_ARMED received` | unit | N3's session end landed | yes | yes |
| `AapService: Force-starting WIFI-Scan from UI` | unit | the WiFi-button verb landed | yes | yes |
| `SSL handshake complete` | unit | a session formed (INFO form; never prefix `Handshake:`) | yes | yes |
| `Throughput over ` with `rendered=` | unit | video reached the decoder | yes | yes |
| `MATCH! Starting AapService` | unit | discard rule | yes | yes |
| `NearbyStrategy: Advertising as` | phone | the helper is advertising | helper `8ac36c9` | |
| `NearbyStrategy: Connected to` | phone | the phone's side of the endpoint is up | helper | |
| `NearbyStrategy: Disconnected from` | phone | the phone saw the endpoint drop | helper | |
| `NearbyStrategy: Received incoming STREAM payload. Tunnel is B-DIR now.` | phone | the phone got our half of the tunnel | helper | |
| `AA is now flowing through proxy` | phone | Android Auto on the phone is carrying the session | helper | |
| `AA proxy connection lost` | phone | the phone's session ended | helper | |
| `FATAL EXCEPTION` | both | a crash (system line) | | |

The phone lines were checked with `git grep -F` against the helper at `8ac36c9`. Each appears once in its source.

## 6. Shell setup and every action as a verb

Make the folder `hur-wifi-test-scripts/pr-1064-nearby-attempts-round1/`. Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into it from `hur-wifi-test-scripts/projection-teardown-and-relays-round3/`, then save `lib1064.sh` below beside them. Source them in that order. From those files this brief uses `send`, `mark`, `nl`, `waitfor`, `hu_put` (`ohu_lib.sh`) and `apk_check`, `th_gate`, `th_report`, `cap_start`, `cap_stop`, `pcap_start`, `pcap_stop`, `clockcheck` (`ptr_lib.sh`). The thermal rules in `rig-quirks/topics/tooling.md` apply: `apk_check` calls `th_gate` before every run. Run each stage under `flock /tmp/ohu-rig.lock`, as `projection-teardown-and-relays` round 3 did.

```bash
HU=27870808938846; PH=4f4027e9; MOTO=ZY22GC3BM4
OUT=~/hur-wifi-test-scripts/pr-1064-nearby-attempts-round1; mkdir -p $OUT; cd $OUT
PUT=hu_put; BASEXML=$OUT/settings-backup-DHU.xml
source ./ohu_lib.sh; source ./ptr_lib.sh; source ./lib1064.sh
adb -s $HU shell am force-stop $PKG; adb -s $HU shell cat /data/data/$PKG/shared_prefs/settings.xml > $BASEXML   # D-HU's adb shell is root
ARM=B          # set to C after the candidate is installed; every run file carries it
MODEL=$(adb -s $PH shell getprop ro.product.model | tr -d '\r')    # the helper advertises as Build.MODEL
NKEYS="int:wifi-connection-mode=2 int:helper-connection-strategy=2 bool:auto-connect-last-session=false int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
```

**`lib1064.sh`**:

```bash
# lib1064.sh : source after ohu_lib.sh and ptr_lib.sh. Needs HU, PH, OUT, PUT, BASEXML, MODEL, NKEYS.
HPKG=com.andrerinas.wirelesshelper.debug
HACT=$HPKG/com.andrerinas.wirelesshelper.MainActivity
# ms <HH:MM:SS.mmm> : milliseconds since midnight; NA stays NA
ms() { [ "$1" = NA ] && { echo NA; return; }; echo "$1" | awk -F'[:.]' '{print (($1*60+$2)*60+$3)*1000+$4}'; }
# lno <from-line> <fixed-string> : absolute line number of the first match at or after <from-line>; 0 if none
lno() { local n; n=$(tail -n +"$1" "$CAP" | grep -anF -m1 -- "$2" | cut -d: -f1); [ -n "$n" ] && echo $(( $1 + n - 1 )) || echo 0; }
# tat <line> : the HH:MM:SS.mmm field of a capture line; NA for line 0
tat() { if [ "$1" -gt 0 ]; then sed -n "$1p" "$CAP" | awk '{print $2}'; else echo NA; fi; }
# cnt <from> <to> <fixed-string> : matches in capture lines from..to ($ for the end)
cnt() { sed -n "$1,$2p" "$CAP" | grep -acF -- "$3"; }
# pcnt <fixed-string> : matches in the phone capture so far
pcnt() { grep -acF -- "$1" "$PCAP"; }
# fire_on <from-line> <ERE> <delay-ms> <cmd...> : background. Polls the capture every 0.05 s from <from-line>; on the
# first match waits <delay-ms>, runs <cmd...> once, logs host epoch ms to $OUT/fire.log. Gives up after 90 s.
fire_on() { local from=$1 re=$2 d=$3; shift 3
  ( end=$((SECONDS+90)); while [ $SECONDS -lt $end ]; do
      if tail -n +"$from" "$CAP" | grep -aqE -- "$re"; then sleep "$(awk -v d="$d" 'BEGIN{print d/1000}')"
        echo "$(date +%s%3N) fire [$re] +${d}ms" >> "$OUT/fire.log"; "$@" >/dev/null 2>&1; exit 0; fi
      sleep 0.05; done; echo "$(date +%s%3N) nofire [$re]" >> "$OUT/fire.log" ) & FIREPID=$!; }
cue() { printf '\a'; echo "OPERATOR: $1 NOW"; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
# pocoput <base.xml> <spec...> : D-POCO as head unit (not rooted). App stopped. Host-side edit, written through run-as on stdin, read back.
pocoput() { python3 ohu_setkeys.py "$1" new.xml "${@:2}" || return 1
  adb -s "$HU" exec-in "run-as $PKG sh -c 'cat > shared_prefs/settings.xml'" < new.xml
  adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE '(wifi-connection-mode|helper-connection-strategy|log-level|auto-start-on-usb|reopen-on-reconnection|use-libusb|video-profile-starvation-cap)[^/]*'; }   # the starvation key must NOT print
helper_kill() { adb -s "$PH" shell am force-stop $HPKG; }
# helper_up : starts the helper; 0 when a new 'NearbyStrategy: Advertising as' reaches the phone capture within 30 s
helper_up() { local c0 i; c0=$(pcnt 'NearbyStrategy: Advertising as')
  adb -s "$PH" shell am start -a android.intent.action.VIEW -d wirelesshelper://start -n $HACT >/dev/null 2>&1
  for i in $(seq 1 30); do [ "$(pcnt 'NearbyStrategy: Advertising as')" -gt "$c0" ] && return 0; sleep 1; done; return 1; }
# ep_id : the newest endpoint id the unit found for $MODEL
ep_id() { grep -aF "NearbyManager: Endpoint FOUND: $MODEL (" "$CAP" | tail -n 1 | tr -d '\r' | sed -E 's/.*\(([^()]*)\)[[:space:]]*$/\1/'; }
# arm : the WiFi-button verb; 0 when this arming's discovery start is in the capture within 20 s
arm() { local L; L=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null
  waitfor 20 'NearbyManager: (Starting Nearby \(Discoverer only\)|Already running discovery)' "$L"; }
# prep <label> : a fresh helper and a fresh discovery; 0 when the phone's endpoint is found
prep() { local L; L=$(nl); mark "$1-prep"; send ACTION_STOP_WIRELESS >/dev/null; helper_kill; sleep 2
  helper_up || { echo "HELPER_DOWN $1"; return 1; }
  arm || { echo "ARM_FAIL $1"; return 1; }
  waitfor 40 "NearbyManager: Endpoint FOUND: $MODEL \\(" "$L" || { echo "NO_ENDPOINT $1"; return 1; }; }
# connect_phone : the Nearby connect verb for the newest id; prints the id
connect_phone() { local id; id=$(ep_id); [ -n "$id" ] || return 1; send ACTION_NEARBY_CONNECT --es extra_endpoint_id "$id" >/dev/null; echo "$id"; }
# session <from-line> : 0 when an SSL and a rendered window follow <from-line>, 60 s each
session() { waitfor 60 'SSL handshake complete' "$1" && waitfor 60 'Throughput over [0-9]+ms: rendered=[1-9]' "$1"; }
# recover <label> : after an attempt, a full session must form. Appends "<label> next_ok=<0|1> proxy=<n>" to $OUT/$RUN.recover
recover() { local L p0 ok=0; send ACTION_DISCONNECT >/dev/null; sleep 6
  prep "$1-rec" && { L=$(nl); p0=$(pcnt 'AA is now flowing through proxy'); connect_phone >/dev/null && session "$L" && ok=1; }
  echo "$1 next_ok=$ok proxy=$(( $(pcnt 'AA is now flowing through proxy') - ${p0:-0} ))" | tee -a "$OUT/$RUN.recover"
  send ACTION_DISCONNECT >/dev/null; sleep 6; }
# n_open <RUN> / n_close <RUN> : one unit capture and one phone capture around a whole run, D-HU in Stage N
n_open() { RUN=$1; CAP=$OUT/$RUN.logcat; apk_check || return 1; clockcheck "$RUN-start"
  adb -s "$HU" shell am force-stop $PKG; adb -s "$PH" shell am force-stop $PKG; adb -s "$PH" shell am force-stop com.andrerinas.wirelesshelper
  helper_kill; pcap_start "$RUN"; cap_start; sleep 1; $PUT "$BASEXML" $NKEYS || return 1
  mark "$RUN-start"; }
n_close() { mark "$1-end"; sleep 1; send ACTION_EXIT >/dev/null; sleep 3; adb -s "$HU" shell am force-stop $PKG; helper_kill
  pcap_stop; cap_stop; clockcheck "$1-end"; th_report "$1"; }
```

Every action in this round:

| Action | Command | Notes |
|---|---|---|
| The WiFi button (arm, lift the user-exit latch) | `send ACTION_START_WIRELESS_SCAN` | logs `AapService: Force-starting WIFI-Scan from UI` |
| Pick the phone in the Nearby list | `send ACTION_NEARBY_CONNECT --es extra_endpoint_id <id>` | logs `AapService: Connecting to Nearby endpoint <id>` |
| Stop the wireless stack, no latch | `send ACTION_STOP_WIRELESS` | B also logs `NearbyManager: Stopping discovery ...`; C logs only the verb line |
| End the session, keep the stack | `send ACTION_END_SESSION_STAY_ARMED` | not a user exit |
| Exit the projection | `send ACTION_DISCONNECT` | the user-exit path |
| Stop the service | `send ACTION_EXIT` | only to a running app |
| Mark a step | `mark <label>` | `AutomationMarker: <label>` at WARN |
| Read the build | `send ACTION_QUERY_STATE` | `commit` on the `data=` line |
| Start or stop the helper | `helper_up`, `helper_kill` | the helper's own deep link and `am force-stop`; not our app |
| Arm a watcher | `fire_on <line> <ERE> <ms> <cmd...>` | file poll, 0.05 s |

Every `send` must print `AutomationReceiver: <action>` in the capture. A step with no such line never landed, and its attempt is void, not a FAIL.

## 7. Runs

**The point of the round is N1.** N2 is the second half of the same claim. N3 and U1 measure the two gaps the PR leaves, and both are expected to look the same on both arms.

**Per-run rules.** Run each run on B, then install C with `adb install -r` and run it again. Before each arm: the identity gate (1a), the permission grants (4.3), and a read-back of the settings. The discard rules of template section 4 apply: a capture with `MATCH! Starting AapService` or `AapRead: Magic Garbage detected in header` in an attempt voids that attempt. Every count is over the attempt's own lines: from the line number read just before the attempt's first action to the line read at its end marker. Never count over the whole capture.

### R0 Gate (once, both arms)

PASS needs: both APK md5s recorded and different; the 1a table matching; `commit` as stated; `run_unit_tests.sh` on each arm with the counts in section 1 and 0 failures from the JUnit XML (a failure stops the round for that arm); `adb install -r` succeeding (never an uninstall); the settings backup diffed against the file after each install, with the delta stated; the helper's md5 and `git rev-parse HEAD` recorded; D-POCO's and D-HU's SSID and frequency recorded; the Gearhead versionName on D-POCO and D-MOTO (`dumpsys package com.google.android.projection.gearhead | grep versionName`).

### N0 Nearby sessions form at all (3 per arm, the stage gate)

```bash
n_open N0-$ARM || exit
for k in 1 2 3; do
  prep N0-$k || { echo "N0-$k fail prep"; continue; }
  L=$(nl); p0=$(pcnt 'AA is now flowing through proxy'); mark N0-$k-go
  id=$(connect_phone); if session "$L"; then s=1; else s=0; fi
  echo "N0-$k id=$id session=$s proxy=$(( $(pcnt 'AA is now flowing through proxy') - p0 ))" | tee -a $OUT/N0-$ARM.tsv
  send ACTION_DISCONNECT >/dev/null; sleep 6
done
n_close N0-$ARM
```

**PASS (per arm):** at least 2 of 3 attempts with `session=1` and `proxy` of 1 or more (the phone capture shows Android Auto carrying each session). **If B scores 0 of 3, stop Stage N:** N1, N2 and N3 are INCONCLUSIVE ("Nearby helper sessions do not form on this phone"), and Setup notes give the last 30 `HUREV_` lines from the phone capture and the unit's last `NearbyManager:` line. If C scores lower than B by 2 or more, that is a FAIL.

### N1 The phone drops inside the 800 ms window (the point of the round)

The lever is `am force-stop` of the helper, fired by a watcher on `NearbyManager: Connected successfully!`. On this rig HIGH arrived 7 to 8 ms after `Connected successfully!` in `release-test` round 1, so the window opens almost at once and closes about 800 ms later. The kill takes 150 to 450 ms to fire, plus the time Nearby needs to report the drop. So the attempts step the delay through 0, 150 and 300 ms, and each attempt is classified from its own timestamps.

```bash
n_open N1-$ARM || exit
hits=0
for k in $(seq 1 12); do
  d=$(( (k-1) % 3 * 150 ))
  prep N1-$k || continue
  L=$(nl); mark N1-$k-go
  fire_on "$L" 'NearbyManager: Connected successfully!' "$d" helper_kill
  id=$(connect_phone) || { kill $FIREPID 2>/dev/null; echo "N1-$k no id"; continue; }
  sleep 20; kill $FIREPID 2>/dev/null; mark N1-$k-end; E=$(nl)
  lw=$(lno "$L" 'NearbyManager: Waiting 800ms for phone state synchronization...')
  ld=$(lno "$L" "NearbyManager: DISCONNECTED from $id")
  tw=$(ms "$(tat $lw)"); td=$(ms "$(tat $ld)")
  hit=0; [ "$tw" != NA ] && [ "$td" != NA ] && [ "$td" -gt "$tw" ] && [ "$td" -lt $((tw+800)) ] && hit=1
  F=$ld; [ "$F" -eq 0 ] && F=$E
  echo -e "$k\t$d\t$id\t$hit\t$(tat $lw)\t$(tat $ld)\t$(cnt $F $E 'NearbyManager: Initiating stream tunnel to')\t$(cnt $F $E 'NearbyManager: Starting AA handshake now.')\t$(cnt $F $E 'NearbySocket: Blocking read until InputStream is AVAILABLE')\t$(cnt $F $E 'AapService: session state connect')\t$(cnt $L $E 'FATAL EXCEPTION')" | tee -a $OUT/N1-$ARM.tsv
  hits=$((hits+hit))
  recover N1-$k
  [ $hits -ge 3 ] && break
done
n_close N1-$ARM
```

The columns are: attempt, delay ms, endpoint id, hit, window open time, drop time, then the counts **after the drop line**: `init`, `hand`, `block`, `sconn`, and `fatal` over the whole attempt. `sconn` counts `AapService: session state connect`, which matches both `connecting` (B's hand-off emits it) and `connected` (C's admitted hand-off publishes straight to it).

**A hit** is an attempt whose `DISCONNECTED from <id>` line falls after the window opens and less than 800 ms later. Only hits are graded. An attempt with no window line (the drop came before HIGH) or a drop after the window is a miss, and it is reported but not graded.

**Stop rule:** stop an arm at its 3rd hit or after 12 attempts. Fewer than 2 hits on an arm makes N1 INCONCLUSIVE for that arm.

**PASS (C), over C's hits:**
- `init`, `hand`, `block` and `sconn` are all 0 on every hit: nothing was built or handed to `CommManager` for the dropped attempt;
- `recover` printed `next_ok=1` and `proxy` of 1 or more after every hit: the next attempt formed a session, and the phone's Android Auto carried it;
- `fatal` is 0 on every attempt.

**The positive control is B.** On a B hit, `init` of 1 or more and `hand` of 1 or more after the drop is the late tunnel the PR removes. Record each B hit's counts. **What a PASS would look like if the change did nothing:** C would print `init` 1 after the drop, as B does. A C "PASS" from misses only proves nothing, which is why only hits are graded. If B gets 2 or more hits and none shows `init` of 1 or more, the window is not where this brief says: grade C anyway, and write in Setup notes that the positive control did not show.

### N2 The head unit stops inside the 800 ms window

The same claim from our side: `stop()` must cancel the tunnel job. The lever is `send ACTION_STOP_WIRELESS`, fired on the window line itself, at a delay of 0 or 200 ms.

```bash
n_open N2-$ARM || exit
hits=0
for k in $(seq 1 8); do
  d=$(( (k-1) % 2 * 200 ))
  prep N2-$k || continue
  L=$(nl); mark N2-$k-go; pd0=$(pcnt 'NearbyStrategy: Disconnected from')
  fire_on "$L" 'NearbyManager: Waiting 800ms for phone state synchronization\.\.\.' "$d" send ACTION_STOP_WIRELESS
  id=$(connect_phone) || { kill $FIREPID 2>/dev/null; echo "N2-$k no id"; continue; }
  sleep 15; kill $FIREPID 2>/dev/null; mark N2-$k-end; E=$(nl)
  lw=$(lno "$L" 'NearbyManager: Waiting 800ms for phone state synchronization...')
  ls=$(lno "$L" 'AutomationReceiver: com.andrerinas.openheadunit.ACTION_STOP_WIRELESS')
  tw=$(ms "$(tat $lw)"); ts=$(ms "$(tat $ls)")
  hit=0; [ "$tw" != NA ] && [ "$ts" != NA ] && [ "$ts" -gt "$tw" ] && [ "$ts" -lt $((tw+800)) ] && hit=1
  F=$ls; [ "$F" -eq 0 ] && F=$E
  echo -e "$k\t$d\t$id\t$hit\t$(tat $lw)\t$(tat $ls)\t$(cnt $F $E 'NearbyManager: Initiating stream tunnel to')\t$(cnt $F $E 'NearbyManager: Starting AA handshake now.')\t$(cnt $F $E 'AapService: session state connect')\t$(cnt $F $E 'SSL handshake complete')\t$(( $(pcnt 'NearbyStrategy: Disconnected from') - pd0 ))\t$(cnt $L $E 'FATAL EXCEPTION')" | tee -a $OUT/N2-$ARM.tsv
  hits=$((hits+hit))
  recover N2-$k
  [ $hits -ge 3 ] && break
done
n_close N2-$ARM
```

The columns are: attempt, delay, id, hit, window open, stop time, then after the stop's `AutomationReceiver:` line: `init`, `hand`, `sconn` (as in N1), `ssl`, then the phone's new `NearbyStrategy: Disconnected from` lines in the attempt (`pdrop`), and `fatal`.

**A hit** is an attempt whose stop verb line falls after the window opens and less than 800 ms later. **Stop rule:** stop at the 3rd hit or after 8 attempts; fewer than 2 hits is INCONCLUSIVE for that arm.

**PASS (C), over C's hits:** `init`, `hand`, `sconn` and `ssl` all 0; `pdrop` of 1 or more (the phone saw the stop end the endpoint, graded from the phone capture); `recover` printed `next_ok=1` after every hit (the stop left nothing stuck); `fatal` 0 on every attempt. **Positive control (B):** a hit with `init` of 1 or more is the defect the PR removes. Note in the results that C prints no `NearbyManager:` line for the stop itself (review note 5); that is expected, not a FAIL.

### N3 Ten reconnect cycles after a session end, counting the stale attempt

Note 5 says a session end keeps `activeEndpointId`. This run measures what that costs a user who ends a session and comes back with the WiFi button and the Nearby list. Both arms are expected to behave the same. The run is a regression gate for C and a count for the review.

```bash
n_open N3-$ARM || exit
prep N3-0 && { L=$(nl); connect_phone >/dev/null; session "$L" || echo "N3 first session failed"; }
for c in $(seq 1 10); do
  S=$(nl); mark N3-$c-start; pd0=$(pcnt 'NearbyStrategy: Disconnected from'); px0=$(pcnt 'AA is now flowing through proxy')
  send ACTION_END_SESSION_STAY_ARMED >/dev/null; sleep 10
  A=$(nl); send ACTION_START_WIRELESS_SCAN >/dev/null
  if waitfor 40 "NearbyManager: Endpoint FOUND: $MODEL \\(" "$A"; then found=1; else found=0; fi
  id=$(connect_phone); C1=$(nl)
  if waitfor 60 'SSL handshake complete' "$A"; then ok=1; else ok=0; fi
  mark N3-$c-end; E=$(nl)
  aid=$(sed -n "$A,${E}p" "$CAP" | grep -aF 'NearbyManager: Auto-connect check: Enabled=' | head -1 | tr -d '\r' | sed 's/.*activeEndpointId=//')
  echo -e "$c\t$found\t$id\t${aid:-none}\t$(cnt $A $E 'NearbyManager: Already connected to')\t$(cnt $A $E 'NearbyManager: Requesting connection to endpoint: ')\t$ok\t$(( $(pcnt 'NearbyStrategy: Disconnected from') - pd0 ))\t$(( $(pcnt 'AA is now flowing through proxy') - px0 ))\t$(cnt $S $E 'FATAL EXCEPTION')" | tee -a $OUT/N3-$ARM.tsv
  [ $ok = 1 ] || { prep N3-$c-rec && { L=$(nl); connect_phone >/dev/null; session "$L" && echo "N3-$c rec_ok=1" || echo "N3-$c rec_ok=0"; } | tee -a $OUT/N3-$ARM.tsv; }
done
n_close N3-$ARM
```

The columns are: cycle, endpoint found after the WiFi button, id used, `activeEndpointId` on the first auto-connect check, `already` (count of `Already connected to`), `req` (new requests), `ok` (an SSL within 60 s), `pdrop` (the phone saw its endpoint drop after the session end), `proxy` (the phone carried the new session), `fatal`.

**Stop rule:** 10 cycles. A cycle whose `send` lines are missing is void and is not replaced.

**PASS (C):** `ok` on 8 or more of 10 cycles; C's `ok` count is at least B's minus 1; C's total `already` is no more than B's; `fatal` 0. Phone: on every cycle with `ok=1`, `proxy` is 1 or more. **Record for the review:** the cycles where `already` is 1 or more and `ok` is 0 (note 5's shape), the cycles where `pdrop` is 0 (the endpoint outlived the session end), and every non-null `activeEndpointId` read on an auto-connect check after a session end. Those numbers say how often a user meets note 5. They are a characterisation, not a pass condition.

### U1 A Nearby hand-off refused by a USB retry (finding 1, Stage U)

**Run it last. It needs the operator for H1 and H2.** The precondition is a USB plug-in that has spent its 60 s budget and keeps retrying, while the Nearby stack stays armed. The dongle proxies D-MOTO and answers AAP only while a phone is associated over Bluetooth (`usb-aoa-dongle-findings`). With D-MOTO's Bluetooth off, the dongle has no phone, so its USB attempts fail and retry, about once a minute for 4 to 8 s each (`usb-version-retry` round 3, R5). D-MOTO is also the only free phone for the helper, so it carries the helper with its Bluetooth off. The refusal happens at the arbiter claim, before any AAP byte, so the run needs Nearby to reach HIGH and nothing from Android Auto.

**Setup (once):**

```bash
adb -s $PH tcpip 5555; sleep 3; POCO_IP=$(adb -s $PH shell ip -4 addr show wlan0 | awk '/inet /{print $2}' | cut -d/ -f1)
adb connect $POCO_IP:5555; adb -s $POCO_IP:5555 shell getprop ro.product.model     # must answer
cue "H1: unplug D-POCO from the PC; leave the dongle unplugged for now"
HU=$POCO_IP:5555; PH=$MOTO; PUT=pocoput; BASEXML=$OUT/settings-backup-DPOCO.xml
adb -s $HU shell am force-stop $PKG; adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $BASEXML
NKEYS="$NKEYS bool:auto-start-on-usb=true bool:reopen-on-reconnection=true bool:use-libusb=false"
MODEL=$(adb -s $PH shell getprop ro.product.model | tr -d '\r')
adb -s $PH shell svc bluetooth disable; sleep 3
adb -s $PH shell dumpsys bluetooth_manager | grep -a -m2 -iE '^ *(enabled|state):'     # must read disabled / OFF
adb -s $PH shell dumpsys wifi | grep -iE 'mWifiInfo' | head -2                          # must name the house network
```

Install the helper on D-MOTO and apply 4.4 there (H3 if it shows a PIN). Install the arm's APK on D-POCO with `adb -s $HU install -r`, apply 4.2 and 4.3, and run the identity gate.

**Per arm:**

1. `RUN=U1-$ARM; CAP=$OUT/$RUN.logcat; apk_check; adb -s $HU shell am force-stop $PKG; $PUT $BASEXML $NKEYS; pcap_start $RUN; cap_start`. Start our app: `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`. Wait for `NearbyManager: Starting Nearby (Discoverer only)...` (30 s).
2. **Gate U-G:** `helper_up`; `L=$(nl)`; wait 40 s for `NearbyManager: Endpoint FOUND: $MODEL (`; `connect_phone`; wait 30 s for `NearbyManager: Wi-Fi Bandwidth Upgrade successful (Quality: HIGH)` after `L`. No HIGH: U1 is INCONCLUSIVE for the arm ("Nearby cannot reach HIGH with the phone's Bluetooth off"); skip to step 7. Then `send ACTION_DISCONNECT; sleep 6`.
3. `cue "H1: plug the dongle into D-POCO's OTG port"`. Wait up to 240 s for `with no session; wireless comes back beside it`. None: INCONCLUSIVE ("the dongle did not spend its USB budget"); go to step 7. Record how many USB attempt lines came before it.
4. `G=$(nl)`. The spent line gave wireless back with no verb from us. **Do not send `ACTION_START_WIRELESS_SCAN` from here to the end of the arm:** the WiFi button takes the arbiter for the user and resets the USB budget. If no `NearbyManager: Endpoint FOUND: $MODEL (` follows `G` within 60 s, run `helper_kill; helper_up` once and wait 60 s more.
5. Up to 4 tries; stop at the first hit:
   ```bash
   T=$(nl); mark U1-try
   fire_on "$T" 'Found device already in accessory mode|Switching USB device to accessory mode' 0 send ACTION_NEARBY_CONNECT --es extra_endpoint_id "$(ep_id)"
   waitfor 90 'the socket from 127\.0\.0\.1 \(WIRELESS_HANDSHAKE\) refused while' "$T" && hit=1 || hit=0
   ```
   The watcher waits for the next USB retry and sends the connect at once, so the tunnel is ready while USB holds the arbiter. Without a hit, `kill $FIREPID`, `send ACTION_DISCONNECT`, `sleep 6`, and try again.
6. On a hit: `R=$(lno $T 'refused while'); pd0=$(pcnt 'NearbyStrategy: Disconnected from'); sleep 60`. Then `send ACTION_NEARBY_CONNECT --es extra_endpoint_id <the same id>; sleep 5; E=$(nl)`. Record from line `R` to `E`: `req` = `NearbyManager: Requesting connection to endpoint: `, `ssl` = `SSL handshake complete`, `already` = `NearbyManager: Already connected to`, `found` = `NearbyManager: Endpoint FOUND: `, `fatal` = `FATAL EXCEPTION`; and from the phone, `pdrop` = new `NearbyStrategy: Disconnected from` lines.
7. `send ACTION_EXIT; sleep 3; adb -s $HU shell am force-stop $PKG; pcap_stop; cap_stop`. Before the next arm: `cue "H2: unplug the dongle"`, install the next APK, and repeat from step 1.

**Grading (characterisation; the gap is the same on both arms by code reading):** write `finding 1 reproduced` for an arm when its hit shows `req` 0, `ssl` 0, `found` 0 and `pdrop` 0 for the 60 s, and the re-sent connect shows `already` 1: the attempt stayed connected and blocked the next connect, and the phone kept its endpoint. **PASS (C)** when `fatal` is 0 and C is not worse than B: a C arm stuck where the B arm recovered (B `req` or `ssl` of 1 or more, C both 0) is a FAIL. **Closing Stage U:** `adb -s $MOTO shell svc bluetooth enable` and confirm with `dumpsys`; `send ACTION_EXIT` to D-POCO's app and force-stop it; `cue "H1: unplug the dongle and put D-POCO back on its PC cable"`; `adb -s $POCO_IP:5555 usb`. D-POCO goes back to being a phone, so it gets `headunit://exit` before any later round uses it as one (`rig-quirks/topics/wifi.md`).

### Closing step

Restore each unit's round backup of `settings.xml`, read it back, and record the diff (it must be empty). Leave the debug helper installed on D-POCO and D-MOTO and say so in Setup notes; a later round may need it. Kill every capture and watcher: `ps aux | grep -c "[l]ogcat"` must print 0.

## 8. Do not re-run

Nothing from this thread has run before. Settled elsewhere and not repeated: the Nearby happy path and a failed request not poisoning the next one (`release-test` round 1, B4 and B5); the arbiter tiers and the USB budget (`usb-version-retry` rounds 1 to 3). Finding 2 is not reachable on the rig (section 2).

## 9. Report back

1. **N1 and N2 on C's hits:** `init`, `hand` and `sconn` after the drop or the stop, and `next_ok`. All zero with `next_ok=1` is the PR's claim holding on hardware. Give B's hit counts beside them as the positive control.
2. **N3:** `ok` per arm out of 10, total `already` per arm, and the count of note-5 cycles (`already` 1 or more with `ok` 0).
3. **U1:** `finding 1 reproduced` yes or no, per arm, with the six numbers from step 6.

### Results skeleton (copy into `pr-1064-nearby-attempts-round1-results.md`)

```markdown
# nearby-attempts, round 1 results

**Candidate C:** codex/nearby-tunnel-lifecycle @ 32311d316abdd63081f8629b0dbefcfbb4033f68   **Baseline B:** merge-base @ 2ca3b1f1a807faa11ba8608fd8a905acc09c65a9
**APK md5:** B <md5> / C <md5>   **Helper:** wireless-helper debug @ 8ac36c9bcc80731b78949c04fdc63178b5904f2f, md5 <md5>
**Units:** D-HU <API>, D-POCO <API, Gearhead version>, D-MOTO <API, Gearhead version> (Stage U only)
**Date:** <yyyy-mm-dd>
**Evidence:** release `rig-evidence-pr-1064-nearby-attempts`, asset `pr-1064-nearby-attempts-round1-captures.zip`, sha256 <hash>

## Setup notes
Deviations; scripts used and added; hand steps with times; the helper's settings route (exec-in or H4); both SSIDs and frequencies; void attempts and why; any string that did not match.

## R0 Gate
| Arm | md5 | NearbyAttemptGuard / ConnectionAdmission / HeldServerSocket | commit | JVM count |
|---|---|---|---|---|

## N0
| Arm | sessions / 3 | proxy lines |
|---|---|---|

## N1 (the point)
| Arm | attempt | delay ms | hit | window open | drop | init | hand | block | sconn | next_ok | fatal |
|---|---|---|---|---|---|---|---|---|---|---|---|

## N2
| Arm | attempt | delay ms | hit | window open | stop | init | hand | sconn | ssl | pdrop | next_ok | fatal |
|---|---|---|---|---|---|---|---|---|---|---|---|---|

## N3
| Arm | cycle | found | activeEndpointId | already | req | ok | pdrop | proxy |
|---|---|---|---|---|---|---|---|---|

## U1
| Arm | gate HIGH | spent line | tries | hit | req | ssl | found | already | pdrop | fatal | finding 1 reproduced |
|---|---|---|---|---|---|---|---|---|---|---|---|

## Verdict table
| Run | B | C | Notes |
|---|---|---|---|

## Anything the brief did not ask about
```

Evidence goes to a release asset (template section 7): zip the round folder, `sha256sum` it, and create the release `rig-evidence-pr-1064-nearby-attempts` with that asset (this is the thread's first round).

## Decisive strings

Unit lines are from `app/src/main` of B and C, except where section 5 marks one arm. Phone lines are from the helper at `8ac36c9`. `FATAL EXCEPTION` is a system line.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
AapService: Force-starting WIFI-Scan from UI
AapService: Connecting to Nearby endpoint 
AapService: ACTION_END_SESSION_STAY_ARMED received
AapService: session state 
NearbyManager: Starting Nearby (Discoverer only)...
NearbyManager: Missing required location/bluetooth permissions. Skipping start.
NearbyManager: Endpoint FOUND: 
NearbyManager: Auto-connect check: Enabled=
NearbyManager: Requesting connection to endpoint: 
NearbyManager: Already connected to 
, ignoring duplicate request
NearbyManager: Connection RESULT for 
NearbyManager: Connected successfully!
NearbyManager: Wi-Fi Bandwidth Upgrade successful (Quality: HIGH). Initiating stream tunnel...
NearbyManager: Waiting 800ms for phone state synchronization...
NearbyManager: Initiating stream tunnel to 
NearbyManager: Starting AA handshake now. Input will block until stream arrives.
NearbyManager: DISCONNECTED from 
NearbyManager: Stopping discovery and disconnecting from any active endpoint...
NearbyManager: Failed to hand over stream tunnel
NearbyManager: Bandwidth upgrade timed out after 10s
NearbySocket: Blocking read until InputStream is AVAILABLE via Nearby Payload
ConnectionArbiter: 
the socket from 
 refused while 
USB has tried for 
with no session; wireless comes back beside it
stood the wireless stack down until it ends
Found device already in accessory mode
Switching USB device to accessory mode
SSL handshake complete
Throughput over 
MATCH! Starting AapService
NearbyStrategy: Advertising as
NearbyStrategy: Connected to
NearbyStrategy: Disconnected from
NearbyStrategy: Received incoming STREAM payload. Tunnel is B-DIR now.
AA is now flowing through proxy
AA proxy connection lost
FATAL EXCEPTION
```
