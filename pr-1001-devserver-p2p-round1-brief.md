# pr-1001-devserver-p2p, round 1 brief: automatic WiFi Direct for Headunit Server mode

Publish name on `transfer/rig-rounds`: `pr-1001-devserver-p2p-round1-brief.md`. Results go in
`pr-1001-devserver-p2p-round1-results.md`. Evidence goes in a new release
`rig-evidence-pr-1001-devserver-p2p` as the asset `pr-1001-devserver-p2p-round1-captures.zip`.

An outside contributor's change adds an opt-in setting to Headunit Server mode (mode 1). With it on,
the head unit creates a WiFi Direct group named "OpenHU", waits for a phone to join it, sweeps the
group's subnet for port 5277 and dials the phone's developer head unit server. Three builds, two
units, two stages. About 3 hours plus three builds.

**The point of the round is R3**: a Self Mode session beside an armed WiFi Direct launcher, with
station WiFi switched off. The other runs check that the setting-off paths did not move (R1, R2) and
measure the setting-on bring-up (R4, R5, R6).

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` |
|---|---|---|---|
| **B** (base control) | upstream `main` | `0cbff00432d13c164799fe2d20e79444be2c0983` | `0cbff00432d1` |
| **P** (change as pushed) | `fork/review/pr-1001` | `7f5bc6e926fbbdf167951281ca54de59f44c0559` | `7f5bc6e926fb` |
| **F** (change plus our fix) | `fork/review/pr-1001-loopback-fix` | `ce5e61cb55d7a19b26312e460ce86b760f9c61f5` | `ce5e61cb55d7` |

P is two commits on B. F is P plus one commit, `ce5e61cb`: a one-line change to
`LinkLossTeardownPolicy` and one JVM test. No history was rewritten. This is the thread's first round.

```bash
git fetch https://github.com/andreknieriem/open-headunit.git main
git fetch https://github.com/o-jcardenass/open-headunit.git review/pr-1001 review/pr-1001-loopback-fix
git cat-file -e 0cbff00432d13c164799fe2d20e79444be2c0983 && echo B-ok
git cat-file -e 7f5bc6e926fbbdf167951281ca54de59f44c0559 && echo P-ok
git cat-file -e ce5e61cb55d7a19b26312e460ce86b760f9c61f5 && echo F-ok
git worktree add ../p1001-B 0cbff00432d13c164799fe2d20e79444be2c0983
git worktree add ../p1001-P 7f5bc6e926fbbdf167951281ca54de59f44c0559
git worktree add ../p1001-F ce5e61cb55d7a19b26312e460ce86b760f9c61f5
for a in B P F; do cp local.properties ../p1001-$a/; done
```

If any of the three `echo` lines does not print, stop the round.

### R0. Build and identity gate

Make `hur-wifi-test-scripts/pr-1001-devserver-p2p-round1/` and set `OUT` to it. For each arm, wait
for the host to cool (`th_wait 70`), build, and copy the APK out at once (tooling quirk:
`build_hur.sh` deletes the previous APK). `build_hur_cool.sh` or `build_hur.sh` is fine if it builds
the worktree you name; the contract is this:

```bash
cd ../p1001-B && ./gradlew :app:assembleGithubDebug --max-workers=2 && cp app/build/outputs/apk/github/debug/*.apk $OUT/apk/B.apk
cd ../p1001-P && ./gradlew :app:assembleGithubDebug --max-workers=2 && cp app/build/outputs/apk/github/debug/*.apk $OUT/apk/P.apk
cd ../p1001-F && ./gradlew :app:assembleGithubDebug --max-workers=2 && cp app/build/outputs/apk/github/debug/*.apk $OUT/apk/F.apk
cd ../p1001-F && ./gradlew :app:testGithubDebugUnitTest --max-workers=2
```

Then the identity checks:

```bash
md5sum $OUT/apk/{B,P,F}.apk                                   # three different values
for a in B P F; do echo "$a $(unzip -p $OUT/apk/$a.apk 'classes*.dex' | strings | grep -cF '; proceeding with group creation')"; done
grep -c 'a self mode loopback session survives wifi off beside a P2P launcher' \
  ../p1001-F/app/build/test-results/testGithubDebugUnitTest/TEST-com.andrerinas.openheadunit.connection.wifi.LinkLossTeardownPolicyTest.xml
```

**PASS when all hold:** three builds succeed; F's unit tests report 0 failures and 0 errors; the
three md5s differ; the DEX count reads B 0, P 1 or more, F 1 or more; the test-name grep reads 1.
P and F carry the same strings, so the DEX count cannot tell them apart. The `commit` field can:
after every install, `send ACTION_QUERY_STATE` and compare `commit` with the table above. A wrong
commit voids that arm's runs on that unit until a reinstall reads right. **A failure in R0 stops
the round.**

## 2. What this is and why

The change is opt-in: `headunit-server-wifi-direct`, read only in mode 1. With the setting off,
everything must behave as on B. The ticket's `diagnosis.md` ranks five hypotheses. This round tests
three of them on hardware:

- **H1 (confirmed in a JVM test).** P adds an arm to `LinkLossTeardownPolicy` for
  `WIFI_STATION_DISABLING`: close any session whose peer is a head unit server. Self Mode on Android
  Auto 17.4 or later dials `127.0.0.1:5277`, so a Self Mode session counts as such a peer. With a
  WiFi Direct launcher armed (Native AA, or Helper strategy 1), B keeps the Self Mode session when
  station WiFi goes off. P closes it, with the setting off. F gates the new arm on a server-P2P
  launcher, which returns every other route to B's answer. R3 measures this.
- **H2 (code reading).** A stopped `WifiDirectManager` whose `createGroup` succeeds late removes the
  group. Its guard asks the stopped instance, so it never sees the new manager's group. Prediction:
  a WiFi button press during a create leaves an outage of 10 to 20 s and a second group create. R5B.
- **H3 (code reading).** A create that keeps failing is retried every 20 s with no backoff. R5A
  counts creates over five idle minutes. It shows only if a create fails on this unit.

What happens on B when there is no launcher at all is already measured: `self-mode-vpn-release`
round 1 (R5A) saw B close a Self Mode session on `svc wifi disable` with
`WIFI_STATION_DISABLING with a live session`. So the close itself is old behaviour. H1 is only
about the case where a WiFi Direct launcher is armed, and R3 builds exactly that case.

## 3. What is different about this round

- **Two stages.** Stage 1: D-POCO is the head unit (Self Mode), R3 on all three arms. D-HU's app is
  stopped and its Bluetooth is off. Stage 2: D-HU is the head unit and D-POCO is the phone, R1, R5,
  R4, R6, R2 in that order. Both units stay cabled for the whole round.
- **D-POCO changes role between stages.** End every stage 1 run with `clean_hu`, which sends
  `headunit://exit` while our app runs and then force-stops it. Never use a bare force-stop on a unit
  that hosted a group (rig quirk: the group outlives it). Before stage 2, `p2p $POCO` must read
  `isGroupOwner: false`. If it reads `true`, run `adb -s $POCO shell svc wifi disable`, wait 5 s,
  run `svc wifi enable`, and read it again.
- **Never force-stop Android Auto (Gearhead) on D-POCO.** It drops the developer head unit server on
  `:5277`, which then needs hand step HS1 again.
- **R3 is armed with the WiFi button verb, on purpose.** R3 writes `connection-modes` as `self` only,
  so the service's own start refuses to arm wireless
  (`WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen`). The run then sends
  `ACTION_START_WIRELESS_SCAN`, which is user-requested and arms the stored Native mode. That gives
  one known arm point and keeps Self Mode the only auto-connect.
- **R3's B arm is also the proof that the setup works.** With no WiFi Direct launcher, B closes the
  session (above). So B printing `leaving it alone` proves the launcher was armed as a WiFi Direct
  route. If B closes the session instead, the setup failed: see R3's setup-failure rule.
- **P is expected to FAIL R3.** That FAIL is the hardware proof of H1. It is pre-registered, not a
  surprise. A P run that keeps the session is the unexpected result.
- **In Self Mode the unit's own capture is also the phone capture.** Do not tag-filter it. Each R3 arm
  grades Gearhead lines from it.
- **D-POCO's WiFi self-reverts on** a few seconds after one `svc wifi disable` (D-POCO quirk). R3 needs
  only the one `WIFI_STATE_DISABLING` broadcast, so this does not matter. Read `wifi_on` after and
  turn it on again if it is still off.
- **D-HU's WiFi may not come back on the first `svc wifi enable`** (wifi quirk). R6 sends a second
  `svc wifi enable` when `wifi_on` still reads disabled after 10 s. Never read that stall as a fault
  of the candidate.
- **The setting-on runs (R4, R5, R6) run on F only.** P and F differ only in the WiFi-off decision.
  R6 grades that decision on the server-P2P route, where both P and F close the session.
- **The setting-off runs (R1, R2) run on B and P.** F changes nothing they reach.
- **Pre-registered INCONCLUSIVE outcomes:** R5B when no press lands inside a create in 10 attempts;
  H3 in R5A when every create succeeds; R4 and R6 when the operator misses hand step HS2.
- **The rig's audio settings are a deliberate worst case.** Never write `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back on D-HU and
  record them.
- **The rename to "OpenHU" changes D-HU's WiFi Direct device name and nothing here restores it.**
  Record `adb -s $DHU shell dumpsys wifip2p | grep -a -m2 -i devicename` before stage 2 and after R6,
  and put both in Setup notes.
- **Never run adb calls in parallel against one unit.** The background logcat captures are the only
  exception, as in every round.

### Hand steps

Each one is a system screen with no verb in our app. `cue` prints `OPERATOR_STEP` and logs it in
`$OUT/hand-steps.log`.

| Id | When | What the operator does | Why there is no verb |
|---|---|---|---|
| HS1 | `port5277` reads 0 before a stage, or a run's wedge check says so | On D-POCO: Android Auto > developer settings > "Start head unit server" | The toggle is in Gearhead's UI; no adb route reaches it |
| HS2 | R4, and R6 if the phone does not rejoin by itself | On D-POCO: Settings > Wi-Fi > Wi-Fi Direct, tap "OpenHU" | The phone-side app that joins by itself is the Wireless Helper root dev-server build, and D-POCO has no app-level root. This is the phone's system UI, not our app |
| HS3 | Only if a "Wi-Fi Direct" connection prompt appears on D-HU or D-POCO during HS2 | Accept it | A system dialog. Record whether it appeared, and on which unit |
| HS4 | Only if R1's precheck shows D-HU or D-POCO off `Pegue Cdesta` | Join that unit to `Pegue Cdesta` in its WiFi settings | D-HU's build has no adb rejoin for a spaced SSID with autojoin off (tooling round notes) |

## 4. Settings keys

Write with the app stopped. Take a fresh backup of each unit first (`prefs_cat`), and state the
delta against it in Setup notes. Write keys with `hu_put` on D-HU (root, then
`chown u0_a176:u0_a176`, `chmod 660`) and `tab_put` on D-POCO (`run-as`), from `ohu_lib.sh`, in the
key syntax `pr-1066-byebye-priority` round 1 used. Read every key back before the launch. On D-HU,
record `ls -ld /data/data/com.andrerinas.headunitrevived/shared_prefs` once (`stat -c` is not
supported there). Restore both units from their backups at the end and diff each against its backup.

| Key | R3 (D-POCO) | R1, R4, R5, R6 (D-HU) | R2 (D-HU) |
|---|---|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | `<int name="wifi-connection-mode" value="1" />` | `<int name="wifi-connection-mode" value="3" />` |
| `connection-modes` | `<set name="connection-modes"><string>self</string></set>` | `<set name="connection-modes"><string>wifi</string></set>` | `<set name="connection-modes"><string>wifi</string></set>` |
| `headunit-server-wifi-direct` | delete | R1: `<boolean name="headunit-server-wifi-direct" value="false" />`; R4, R5, R6: `value="true"` | delete |
| `native-ap-transport` | `<int name="native-ap-transport" value="0" />` (WiFi Direct) | | `<int name="native-ap-transport" value="0" />` |
| `native-poke-all-paired` | `<boolean name="native-poke-all-paired" value="false" />` | | |
| `native-driver-selection-mode` | `<int name="native-driver-selection-mode" value="0" />` | | `<int name="native-driver-selection-mode" value="0" />` |
| `auto-start-self-mode` | `<boolean name="auto-start-self-mode" value="true" />` | `<boolean name="auto-start-self-mode" value="false" />` | `<boolean name="auto-start-self-mode" value="false" />` |
| `auto-connect-delay-seconds` | `<int name="auto-connect-delay-seconds" value="0" />` | | |
| `auto-connect-last-session` | `<boolean name="auto-connect-last-session" value="false" />` | `<boolean name="auto-connect-last-session" value="false" />` | |
| `kill-on-disconnect` | | `<boolean name="kill-on-disconnect" value="false" />` | `<boolean name="kill-on-disconnect" value="false" />` |
| `log-level` | `<int name="log-level" value="2" />` | `<int name="log-level" value="2" />` | `<int name="log-level" value="2" />` |
| `onboarding-version` | `<int name="onboarding-version" value="2" />` | `<int name="onboarding-version" value="2" />` | `<int name="onboarding-version" value="2" />` |
| `keep-dummy-vpn-during-session` | delete | | |
| `video-profile-starvation-cap` | delete | delete | delete |

The same keys in `hu_put` / `tab_put` syntax:

```bash
KEYS_R3="int:wifi-connection-mode=3 set:connection-modes=self del:headunit-server-wifi-direct int:native-ap-transport=0 bool:native-poke-all-paired=false int:native-driver-selection-mode=0 bool:auto-start-self-mode=true int:auto-connect-delay-seconds=0 bool:auto-connect-last-session=false int:log-level=2 int:onboarding-version=2 del:keep-dummy-vpn-during-session del:video-profile-starvation-cap"
KEYS_R1="int:wifi-connection-mode=1 set:connection-modes=wifi bool:headunit-server-wifi-direct=false bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:kill-on-disconnect=false int:log-level=2 int:onboarding-version=2 del:video-profile-starvation-cap"
KEYS_ON="int:wifi-connection-mode=1 set:connection-modes=wifi bool:headunit-server-wifi-direct=true bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:kill-on-disconnect=false int:log-level=2 int:onboarding-version=2 del:video-profile-starvation-cap"
KEYS_R2="int:wifi-connection-mode=3 set:connection-modes=wifi del:headunit-server-wifi-direct int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:auto-start-self-mode=false bool:kill-on-disconnect=false int:log-level=2 int:onboarding-version=2 del:video-profile-starvation-cap"
```

B does not know `headunit-server-wifi-direct`, so writing it `false` on B changes nothing.
`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on any arm, so no
`allow-external-configuration` is needed. Every line this round grades is INFO or WARN, so
`log-level` 2 carries all of them.

## 5. Helpers and verbs

Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into `$OUT` from
`hur-wifi-test-scripts/projection-teardown-and-relays-round3/`, as `pr-1066-byebye-priority` round 1
did. From them use only `prefs_cat`, `hu_put`, `tab_put`, `apk_check`, `th_wait`, `th_gate` and
`th_report`. Save this file as `$OUT/p1001_lib.sh` and source it last, so its functions win:

```bash
# p1001_lib.sh : source after ohu_lib.sh and ptr_lib.sh. Needs OUT, HU, PH, RUN.
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity
DHU=27870808938846; POCO=4f4027e9
# HU = the unit that runs the app under test. PH = the phone. In Self Mode PH=HU.
send() { local a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; sleep 0.3; }
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; if [ "$PH" != "$HU" ]; then adb -s "$PH" shell log -t OHURIG "$1"; sleep 0.3; fi; }
HF() { echo "$OUT/$RUN.hu.txt"; }
PF() { if [ "$PH" = "$HU" ]; then echo "$OUT/$RUN.hu.txt"; else echo "$OUT/$RUN.ph.txt"; fi; }
cap_start() {
  for t in CAR.GAL CAR.GAL.GAL.LITE CAR.GAL.SECURITY.LITE; do adb -s "$PH" shell setprop log.tag.$t VERBOSE; done
  adb -s "$HU" logcat -G 16M; adb -s "$HU" logcat -c
  stdbuf -oL adb -s "$HU" logcat -v time > "$(HF)" & echo $! > "$OUT/$RUN.hu.pid"
  if [ "$PH" != "$HU" ]; then adb -s "$PH" logcat -G 16M; adb -s "$PH" logcat -c
    stdbuf -oL adb -s "$PH" logcat -v time > "$(PF)" & echo $! > "$OUT/$RUN.ph.pid"; fi
  sleep 1; }
cap_stop() { sleep 1; for f in "$OUT/$RUN.hu.pid" "$OUT/$RUN.ph.pid"; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null; rm -f "$f"; done; }
# hwin A B : head unit capture from marker A to marker B. pwin A B : the same on the phone capture.
_win() { awk -v a="$1$2" -v b="$1$3" 'function at(s){i=index($0,s); return i>0 && substr($0,i+length(s))~/^[[:space:]]*$/} at(a){on=1} on{print} on&&at(b){exit}' "$4"; }
hwin() { _win "AutomationMarker: " "$1" "$2" "$(HF)"; }
pwin() { if [ "$PH" = "$HU" ]; then hwin "$1" "$2"; else _win "OHURIG: " "$1" "$2" "$(PF)"; fi; }
c() { grep -acF -- "$1"; }
rend() { grep -acE 'Throughput over [0-9]+ms: rendered=[1-9]'; }
lines() { wc -l < "${1:-$(HF)}"; }
# waitline SECS STRING [FILE] [FROM_LINE] : 0 when STRING appears after FROM_LINE, 1 on timeout
waitline() { local s=$1 str=$2 f=${3:-$(HF)} from=${4:-0} i
  for i in $(seq 1 "$s"); do tail -n +"$((from+1))" "$f" | grep -aqF -- "$str" && return 0; sleep 1; done; return 1; }
port5277() { adb -s "$POCO" shell cat /proc/net/tcp /proc/net/tcp6 | awk '$2 ~ /:149D$/ && $4 == "0A"' | wc -l; }
wifi_on() { adb -s "$1" shell dumpsys wifi | grep -a -m1 -E 'Wi-Fi is (enabled|disabled)'; }
p2p() { adb -s "$1" shell dumpsys wifip2p | grep -aE 'isGroupOwner|groupFormed|mNetworkName|interface' | head -8; }
ssid() { adb -s "$1" shell dumpsys wifi | grep -a -m1 'mWifiInfo'; }
state() { send ACTION_QUERY_STATE | grep -a 'data='; }
cue() { echo "$(date +%T) OPERATOR_STEP $1"; printf '\a'; echo "$(date +%T) $1" >> "$OUT/hand-steps.log"; }
clean_hu() { adb -s "$HU" shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 3; adb -s "$HU" shell am force-stop $PKG; }
# ms from the first line holding A to the first later line holding B, on stdin
gapms() { awk -v A="$1" -v B="$2" 'function ms(s,t){split(s,t,/[:.]/);return ((t[1]*60+t[2])*60+t[3])*1000+t[4]} index($0,A)&&!a{a=ms($2)} a&&!b&&index($0,B){b=ms($2)} END{if(a!=""&&b!="")print b-a; else print "NA"}'; }
```

`clean_hu` is only for a unit whose app is running: `headunit://exit` on a stopped app starts the
service, and its `onCreate` arms the stack.

Install every arm with `adb -s <unit> install -r -d $OUT/apk/<arm>.apk`, set `WANT_MD5`, run
`apk_check`, then `state`. Never install through a script that picks the newest APK.

**Verbs used, and nothing else acts on the app:**

| Action | Command |
|---|---|
| arm the stored wireless mode, as the WiFi button | `send ACTION_START_WIRELESS_SCAN` |
| arm headless, as saving a wireless setting | `send ACTION_START_WIRELESS --ez no_ui true` |
| user exit from the projection | `send ACTION_DISCONNECT` |
| connect to the phone's server by address | `send ACTION_CONNECT --es ip $PHIP` |
| marker | `mark <label>` |
| build identity | `state` |
| launch the app (Self Mode auto-start, R4's natural start) | `adb -s $HU shell am start -n $MAIN` |

Every verb must print `AutomationReceiver: ` followed by the full action in the head unit capture. A
step with no such line never landed: that step is void, not a FAIL.

### Stage prechecks

Run once before each stage, apps stopped, and record the output in Setup notes:

```bash
adb -s $POCO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName   # 17.4 or later
port5277                                    # 1 or more; if 0, cue HS1 and read again
adb -s $POCO shell dumpsys window | grep -a mCurrentFocus    # the launcher; if not: adb -s $POCO shell input keyevent KEYCODE_HOME
p2p $POCO; p2p $DHU                         # isGroupOwner false on the phone-role unit
wifi_on $POCO; wifi_on $DHU; ssid $POCO; ssid $DHU
adb -s $DHU shell dumpsys bluetooth_manager | grep -a -m3 -iE 'enabled|state'
adb -s $POCO shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'
```

A second `port5277` of 0 after HS1 makes every run that needs the server UNTESTABLE (R1, R3, R4, R6).

## 6. The deciding lines

Every app string below matched `git grep -F` in `app/src` at `ce5e61cb`, and the B and P columns say
where it exists on the other arms. Two of our lines are built from a variable, so the runs grep two
literal fragments of each:

- `Auto/P2P: rename <reason>; proceeding with group creation`: grep the fragment
  `; proceeding with group creation`. The reason is `confirmed`, `timed out`, `refused (N)` or
  `unavailable`. Record it.
- `WifiDirectManager: <what> abandoned - the manager was stopped while it was in flight.`: grep lines
  holding ` abandoned - the manager was stopped while it was in flight.`, then split them by `<what>`:
  `the Native AA group create` (the H2 case; the label says Native AA on the server route too),
  `group inspection`, `group creation`, `group recreate`.
- `AapService: <trigger> with a live session ... ` and
  `AapService: <trigger>, but this session does not ride that link; leaving it alone`: grep the
  fragments `with a live session` and `but this session does not ride that link; leaving it alone`.

| Line (grep -F) | Source | B | P | F | Used for |
|---|---|---|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | yes | yes | yes | a verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` | yes | yes | yes | markers |
| `WifiLauncher: Initializing WiFi Mode: ` | `WifiLauncherManager` | yes | yes | yes | arm; the line ends `NATIVE` or `AUTO` |
| `WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen` | `WifiLauncherManager` | yes | yes | yes | R3: the service's own start refused |
| `WifiDirectManager: Stopping and cleaning up...` | `WifiDirectManager` | yes | yes | yes | R3: launcher stopped; must be 0 after `-armed` |
| `Auto start selfmode` | `HomeFragment` | yes | yes | yes | R3: Self Mode auto-start |
| `SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...` | `SelfLauncherManager` | yes | yes | yes | R3: the 5277 route |
| `SSL handshake complete` | `AapSslContext` (INFO) | yes | yes | yes | a session; one per session at INFO |
| `Throughput over ` | `VideoDecoder` | yes | yes | yes | picture; regex `Throughput over [0-9]+ms: rendered=[1-9]` |
| `but this session does not ride that link; leaving it alone` | `AapService.maybeTearDownBeforeLinkGoes` | yes | yes | yes | WiFi off, session kept |
| `with a live session` | `AapService.maybeTearDownBeforeLinkGoes` | yes | yes | yes | WiFi off, session closed |
| `AapService: link-loss teardown finished in ` | `AapService` | yes | yes | yes | the close finished |
| `AapService: Disconnected.` | `AapService` | yes | yes | yes | session end |
| `Disconnect action received.` | `AapService` | yes | yes | yes | the user exit landed |
| `AapService: Native AA user exit. Stopping active launcher.` | `AapService` | yes | yes | yes | R2 exit |
| `AapService: Force-starting WIFI-Scan from UI` | `AapService` | yes | yes | yes | the WiFi button verb landed |
| `NetworkDiscovery: Found Headunit Server on ` | `NetworkDiscovery` | yes | yes | yes | the sweep found 5277 |
| `Auto-connecting to Headunit Server at ` | `WifiLauncherSharedServices` | yes | yes | yes | discovery dialled it |
| `(reusing socket)` | `WifiLauncherSharedServices` | yes | yes | yes | on the line above |
| `createGroup SUCCESS` | `WifiDirectManager` | yes | yes | yes | R2 Native group count |
| `MATCH! Starting AapService` | `AutoStartReceiver` | yes | yes | yes | discard check |
| `Auto/P2P` | P's new lines | no | yes | yes | R1, R2 on P: must be 0 (setting off) |
| `; proceeding with group creation` | `WifiDirectManager` | no | yes | yes | server group bring-up |
| `WifiDirectManager: P2P Group created (fresh this session).` | `WifiDirectManager` | yes | yes | yes | a server group create succeeded |
| `No existing P2P group, creating new one` | `WifiDirectManager` | yes | yes | yes | a create is about to go out; R5B trigger |
| `Existing P2P group found` | `WifiDirectManager` | yes | yes | yes | the remove-and-recreate path; record |
| `WifiDirectManager: createGroup failed: ` | `WifiDirectManager` | yes | yes | yes | H3 |
| `WifiDirectManager: Chip is BUSY, retrying in 2s...` | `WifiDirectManager` | yes | yes | yes | record |
| `Auto/P2P badge: group=` | `WifiDirectManager` | no | yes | yes | the group up (`group=true`) or gone (`group=false`) |
| ` abandoned - the manager was stopped while it was in flight.` | `WifiDirectManager.supersededByStop` | yes | yes | yes | H2, with the label below |
| `the Native AA group create` | `WifiDirectManager.createAbandonedAfterStop` | yes | yes | yes | H2 label |
| `WifiDirectManager: the group that create made was removed with it.` | `WifiDirectManager` | yes | yes | yes | H2: the late create removed a group |
| `WifiDirectManager: a later bring-up owns the group now, so the abandoned create leaves it alone.` | `WifiDirectManager` | yes | yes | yes | H2 guard working; expected 0 |
| `WifiDirectManager: P2P enabled, auto-starting WiFi Direct visibility` | `WifiDirectManager` | yes | yes | yes | R6: P2P back after WiFi on |
| `NetworkDiscovery: scanning P2P ` | `NetworkDiscovery` | no | yes | yes | a client joined; the sweep runs |
| `Auto/P2P: transport failure, keeping wireless recovery armed` | `CommManager` | no | yes | yes | record |

Gearhead and framework lines. They are not in our source, so `grep -F` against the branch cannot
check them. All three Gearhead lines were seen on D-POCO's 17.9 in `self-mode-vpn-release` round 1
and `pr-1066-byebye-priority` round 1.

| Line | Where | Used for |
|---|---|---|
| `Head unit connected` | phone capture | the 5277 server accepted us |
| `received ByeByeRequest` | phone capture (`CAR.GAL.GAL.LITE`, set VERBOSE by `cap_start`) | our close reached the phone |
| `Head unit disconnected` | phone capture | record |
| `setWifiEnabled` | the unit whose WiFi goes off | record: the `svc wifi disable` landed |

Do not grade `Critical error` on the phone at a session end we cause. `pr-1066-byebye-priority`
round 1 measured 2 to 4 of them at every such end on B as well.

## 7. Runs

Every run: `th_gate` first, the stage precheck where the stage starts, then the steps in order.
Report `th_report $RUN` in each section. Discard rule: a second `SSL handshake complete` inside a
window that should hold one session. A discarded run is re-run once.

### Stage 1. D-POCO as head unit, D-HU silent

```bash
adb -s $DHU shell am force-stop $PKG; adb -s $DHU shell svc bluetooth disable
adb -s $DHU shell dumpsys bluetooth_manager | grep -a -m2 -iE 'enabled|state'    # off
HU=$POCO; PH=$POCO
prefs_cat > $OUT/settings-backup-POCO.xml; BASEXML=$OUT/settings-backup-POCO.xml
adb -s $POCO shell appops get $PKG ACTIVATE_VPN       # record; do not change
```

### R3. Self Mode beside an armed Native launcher, station WiFi off. Point of the round.

Three arms, in the order B, P, F. Run ids `R3-B`, `R3-P`, `R3-F`. Replace `X` below with the arm
letter.

```bash
RUN=R3-X; WANT_MD5=<md5 of X.apk>
adb -s $POCO install -r -d $OUT/apk/X.apk; apk_check; state     # commit must match section 1
adb -s $POCO shell am force-stop $PKG
tab_put "$BASEXML" $KEYS_R3                                      # read back
port5277                                                         # 1 or more, else HS1
th_gate
cap_start; mark R3-X-start
send ACTION_START_WIRELESS_SCAN > $OUT/$RUN.arm.reply
waitline 20 'WifiLauncher: Initializing WiFi Mode: '; echo "arm_wait=$?"
sleep 20; p2p $POCO > $OUT/$RUN.p2p-armed
mark R3-X-armed
adb -s $POCO shell am start -n $MAIN
waitline 60 'SSL handshake complete'; echo "ssl_wait=$?"
waitline 30 'Throughput over '; sleep 12
mark R3-X-off
adb -s $POCO shell svc wifi disable
sleep 30
mark R3-X-hold-end
wifi_on $POCO; adb -s $POCO shell svc wifi enable; sleep 5; wifi_on $POCO
mark R3-X-end
clean_hu; cap_stop; th_report $RUN
```

If `ssl_wait` is 1, the session did not form: the run is a setup failure. Record the last 30 app
lines and re-run once.

**Extract, per arm** (all from `$(HF)`):

```bash
A=$(hwin R3-X-start R3-X-off); M=$(hwin R3-X-armed R3-X-off); H=$(hwin R3-X-off R3-X-hold-end)
echo "R3-X refused=$(echo "$A"|c 'WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen') \
native=$(echo "$A"|grep -aF 'WifiLauncher: Initializing WiFi Mode: '|grep -ac 'NATIVE') \
last_init=$(echo "$A"|grep -aF 'WifiLauncher: Initializing WiFi Mode: '|tail -1|grep -ac 'NATIVE') \
stops_after_last_init=$(echo "$A"|awk '/WifiLauncher: Initializing WiFi Mode: /{n=0} /WifiDirectManager: Stopping and cleaning up\.\.\./{n++} END{print n+0}') \
inits_after_arm=$(echo "$M"|c 'WifiLauncher: Initializing WiFi Mode: ') \
route=$(echo "$A"|c 'SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...') \
ssl=$(echo "$A"|c 'SSL handshake complete') rend_pre=$(echo "$M"|rend) \
leave=$(echo "$H"|c 'but this session does not ride that link; leaving it alone') \
close=$(echo "$H"|c 'with a live session') fin=$(echo "$H"|c 'AapService: link-loss teardown finished in ') \
disc=$(echo "$H"|c 'AapService: Disconnected.') rend_hold=$(echo "$H"|rend) \
bye=$(echo "$H"|c 'received ByeByeRequest') hud=$(echo "$H"|c 'Head unit disconnected') \
wifi_cmd=$(echo "$H"|c 'setWifiEnabled')"
```

**Setup conditions, every arm.** `native` 1 or more, `last_init` 1 (the launcher in force at the
trigger is Native), `stops_after_last_init` 0 (nothing stopped it after that), `route` 1, `ssl` 1,
`rend_pre` 1 or more, and `leave` + `close` 1 or more (the trigger reached a live session). A miss on
any of these is a setup failure: re-run once, then UNTESTABLE. Record `refused` and
`inits_after_arm`; they are not graded.

**Expected, by arm:**

| Count in `R3-X-off` to `R3-X-hold-end` | B | P | F |
|---|---|---|---|
| `leave` | 1 or more | 0 | 1 or more |
| `close` | 0 | 1 | 0 |
| `fin` | 0 | 1 | 0 |
| `disc` | 0 | 1 or more | 0 |
| `rend_hold` | 3 or more | not graded | 3 or more |
| `bye` (Gearhead) | 0 | 1 or more | 0 |

- **R3-B PASS** when the B column holds. B is the positive control: it proves the launcher was a WiFi
  Direct route. If B shows `close` 1 instead, the launcher was not armed as one: it is a setup
  failure, not a result. Re-run once; a second miss makes all of R3 UNTESTABLE.
- **R3-P is graded FAIL when the P column holds.** That FAIL is the expected outcome and confirms H1
  on hardware. If P shows the B column instead, grade it PASS and say in the section that H1 did
  not reproduce.
- **R3-F PASS** when the F column holds. FAIL otherwise.
- **If B and F both keep the decision (`leave` 1 or more, `close` 0) but `disc` reads 1 or more,** the
  session ended by another path. Grade that arm FAIL, and quote the 20 head unit lines before the
  first `AapService: Disconnected.` in the hold. If B and F show the same lines, say so: the fix then
  still matches the base.

Record for each arm: the `p2p-armed` read (`isGroupOwner`, `groupFormed`), the first
`setWifiEnabled` line, and both `wifi_on` reads after `-hold-end`.

End of stage 1: `p2p $POCO` must read `isGroupOwner: false` (section 3). Restore D-POCO's
`settings.xml` from `$BASEXML` after R3-F. Turn D-HU's Bluetooth back on with
`adb -s $DHU shell svc bluetooth enable`. Check `wifi_on $POCO` and `ssid $POCO` read enabled and
`Pegue Cdesta`.

### Stage 2. D-HU as head unit, D-POCO as phone

```bash
HU=$DHU; PH=$POCO
adb -s $POCO shell am force-stop $PKG                 # our app only; never Gearhead
prefs_cat > $OUT/settings-backup-DHU.xml; BASEXML=$OUT/settings-backup-DHU.xml
PHIP=$(adb -s $POCO shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2); echo "PHIP=$PHIP"
```

Run the stage precheck (section 5). Both units must read `Pegue Cdesta` for R1; if either does not,
cue HS4.

### R1. Setting off, Headunit Server over the house network, B then P

Run ids `R1-B` and `R1-P`. Replace `X` with the arm letter.

```bash
RUN=R1-X; WANT_MD5=<md5 of X.apk>
adb -s $DHU install -r -d $OUT/apk/X.apk; apk_check; state
adb -s $DHU shell am force-stop $PKG; hu_put "$BASEXML" $KEYS_R1      # read back
port5277; th_gate
cap_start; mark R1-X-start
L=$(lines); adb -s $DHU shell am start -n $MAIN
waitline 60 'SSL handshake complete' "$(HF)" $L; echo "first=$?"
# only if first=1: mark R1-X-fallback; L=$(lines); send ACTION_CONNECT --es ip $PHIP; waitline 60 'SSL handshake complete' "$(HF)" $L; echo "fallback=$?"
waitline 30 'Throughput over '; sleep 10
for n in 1 2 3; do
  mark R1-X-x$n-go; send ACTION_DISCONNECT > $OUT/$RUN-x$n.reply; sleep 8; mark R1-X-x$n-done; sleep 2
  mark R1-X-r$n-go; L=$(lines); send ACTION_CONNECT --es ip $PHIP > $OUT/$RUN-r$n.reply
  waitline 60 'SSL handshake complete' "$(HF)" $L; echo "r$n=$?" | tee -a $OUT/$RUN.summary
  waitline 20 'Throughput over ' "$(HF)" $L; mark R1-X-r$n-done
  if grep -q "r$n=1" $OUT/$RUN.summary; then echo "listening=$(port5277)" | tee -a $OUT/$RUN.summary; break; fi
done
mark R1-X-end; clean_hu; cap_stop; th_report $RUN
```

A failed reconnect ends the arm. **Wedge** for that reconnect: `listening` 1 or more and
`pwin R1-X-rN-go R1-X-rN-done | c 'Head unit connected'` is 0. After a wedge, cue HS1 before the next
arm and record it.

**PASS (each arm), all of:**

1. `first` is 0, and the window `R1-X-start` to `R1-X-x1-go` holds
   `NetworkDiscovery: Found Headunit Server on ` 1 or more and `Auto-connecting to Headunit Server at `
   1 or more, each naming `$PHIP:5277`. A session that needed the fallback fails this condition.
2. `R1-X-start` to `R1-X-x1-go`: `SSL handshake complete` 1, `rend` 1 or more.
3. Each exit window `R1-X-xN-go` to `R1-X-xN-done`: `Disconnect action received.` 1 and
   `AapService: Disconnected.` 1 or more. Phone `pwin` of the same window: `received ByeByeRequest`
   1 or more.
4. Reconnects 3 of 3. Each `R1-X-rN-go` to `R1-X-rN-done`: `SSL handshake complete` 1, `rend` 1 or
   more, and phone `Head unit connected` 1 or more. Wedges 0.
5. Whole capture: `Auto/P2P` 0, `NetworkDiscovery: scanning P2P ` 0,
   `WifiDirectManager: P2P Group created (fresh this session).` 0. The setting is off.

**R1-P PASS** also needs every count in conditions 1 to 4 to match R1-B's. **If R1-B fails, R1-P is
INCONCLUSIVE**: the positive control did not work on this rig, so nothing can be read from P.

### R5. Setting on, F: idle churn (R5A) and a WiFi button press during a create (R5B)

D-POCO must never have joined "OpenHU" yet. That is why R5 runs before R4. One capture for R5A and
R5B.

```bash
RUN=R5; WANT_MD5=<md5 of F.apk>
adb -s $DHU install -r -d $OUT/apk/F.apk; apk_check; state
adb -s $DHU shell am force-stop $PKG; hu_put "$BASEXML" $KEYS_ON       # read back
adb -s $DHU shell dumpsys wifip2p | grep -a -m2 -i devicename           # record before the rename
th_gate
cap_start; mark R5-start
mark R5A-go; send ACTION_START_WIRELESS --ez no_ui true
waitline 30 'WifiDirectManager: P2P Group created (fresh this session).'; echo "grp_wait=$?"
sleep 30; p2p $DHU > $OUT/R5A.p2p-30s
sleep 270; p2p $DHU > $OUT/R5A.p2p-300s
mark R5A-done
```

**R5A extract and PASS.** Over `hwin R5A-go R5A-done`:

- `WifiLauncher: Initializing WiFi Mode: ` 1, and the line ends `AUTO`.
- `; proceeding with group creation` 1. Record the reason it names.
- `WifiDirectManager: P2P Group created (fresh this session).` 1.
- `WifiDirectManager: createGroup failed: ` 0.
- `Auto/P2P badge: group=` lines holding `group=true`: 1 or more.
- The group name and interface in `R5A.p2p-30s` and `R5A.p2p-300s` are the same.
- Premise: `NetworkDiscovery: scanning P2P ` 0. If it is 1 or more, a client joined and R5A is
  void: re-run once.

**H3, only if `createGroup failed: ` is 1 or more:** list the timestamp of every
`No existing P2P group, creating new one` in the window, and the gaps between them. H3 is reproduced
when 10 or more creates go out at about 20 s apart. If every create succeeded, H3 is INCONCLUSIVE.

**R5B.** Each attempt sends the WiFi button twice: press A starts a new manager and a new create,
and press B lands while A's create is in flight. Run attempts `k` = 1 to 10. Stop after the 5th hit.

```bash
for k in $(seq 1 10); do
  mark R5B-$k-go; L=$(lines)
  send ACTION_START_WIRELESS_SCAN > $OUT/R5B-$k.pressA
  timeout 20 tail -n +$((L+1)) -F "$(HF)" | grep -a -m1 -F 'No existing P2P group, creating new one' >/dev/null \
    && send ACTION_START_WIRELESS_SCAN > $OUT/R5B-$k.pressB
  sleep 45; mark R5B-$k-done
  W=$(hwin R5B-$k-go R5B-$k-done)
  hit=$(echo "$W" | grep -aF 'the Native AA group create' | c ' abandoned - the manager was stopped while it was in flight.')
  echo "R5B-$k presses=$(echo "$W"|c 'AapService: Force-starting WIFI-Scan from UI') hit=$hit \
removed=$(echo "$W"|c 'WifiDirectManager: the group that create made was removed with it.') \
leftalone=$(echo "$W"|c 'WifiDirectManager: a later bring-up owns the group now, so the abandoned create leaves it alone.') \
creates=$(echo "$W"|c 'WifiDirectManager: P2P Group created (fresh this session).') \
other_abandon=$(echo "$W"|c ' abandoned - the manager was stopped while it was in flight.') \
outage_ms=$(echo "$W"|grep -aE 'the group that create made was removed with it|Auto/P2P badge: group=true'|gapms 'removed with it' 'group=true')" | tee -a $OUT/R5B.summary
  [ "$(grep -c ' hit=[1-9]' $OUT/R5B.summary)" -ge 5 ] && break
done
mark R5-end; clean_hu; cap_stop; th_report R5
```

`presses` must read 2. An attempt with 1 (no trigger line within 20 s) is void and does not count.

**R5B grading.** An attempt is a **hit** when `hit` is 1 or more. In a hit, **H2 is reproduced**
when `removed` is 1 or more and `outage_ms` is `NA` or 10000 or more. Report: attempts run, hits,
hits that reproduced H2, and the `outage_ms` and `creates` of each hit.

- **R5B FAIL** when 1 or more hits reproduce H2. That confirms H2 on hardware; it is a finding for
  the review, not a broken rig.
- **R5B PASS** when there are 1 or more hits and none reproduces H2.
- **R5B INCONCLUSIVE** when there are 0 hits in 10 attempts. The press cannot land inside a create on
  this unit from the host, and H2 stays at code reading.

What a PASS looks like if the press never mattered: `hit` 0 everywhere. That is INCONCLUSIVE, not a
PASS, which is why the grade needs hits.

### R4. Setting on, F: bring-up to a session over the "OpenHU" group

One capture holds R4 and R6 (`RUN=R46`). D-POCO's dev server must listen and our app on D-POCO must
be stopped.

```bash
RUN=R46
adb -s $DHU shell am force-stop $PKG; hu_put "$BASEXML" $KEYS_ON       # read back; F is installed
port5277; p2p $POCO; th_gate
cap_start; mark R4-start
adb -s $DHU shell am start -n $MAIN
waitline 60 'Auto/P2P badge: group=true'; echo "grp_wait=$?"
mark R4-join-cue
cue "R4 HS2: on D-POCO open Settings > Wi-Fi > Wi-Fi Direct and tap OpenHU. HS3: accept a Wi-Fi Direct prompt on either unit if one appears."
waitline 180 'NetworkDiscovery: scanning P2P '; echo "join_wait=$?"
waitline 60 'SSL handshake complete'; echo "ssl_wait=$?"
waitline 30 'Throughput over '; sleep 10
p2p $POCO > $OUT/R4.poco-p2p; adb -s $DHU shell dumpsys window | grep -a mCurrentFocus
mark R4-end
```

Do not exit: R6 starts from this session. If `join_wait` is 1, the operator missed HS2: R4 is
INCONCLUSIVE and R6 is UNTESTABLE.

**R4 PASS, all of, over `hwin R4-start R4-end`:**

1. `WifiLauncher: Initializing WiFi Mode: ` 1 or more, ending `AUTO`.
2. `; proceeding with group creation` 1 or more. Record the reason.
3. `WifiDirectManager: P2P Group created (fresh this session).` 1 or more, and an
   `Auto/P2P badge: group=` line holding `group=true`.
4. `NetworkDiscovery: scanning P2P ` 1 or more. Record the interface and address it names.
5. `NetworkDiscovery: Found Headunit Server on ` 1 or more, naming a `192.168.49.` address and `:5277`.
6. `Auto-connecting to Headunit Server at ` 1 or more, naming the same address, with
   `(reusing socket)`.
7. `SSL handshake complete` 1, and `rend` 2 or more after it.
8. Time from the first `NetworkDiscovery: scanning P2P ` to `SSL handshake complete`, by
   `gapms 'NetworkDiscovery: scanning P2P ' 'SSL handshake complete'` on the window: 30000 or less.
9. Lines 3, 4, 5, 6 and 7 appear in that order by timestamp.
10. Phone, `pwin R4-join-cue R4-end`: `Head unit connected` 1 or more.
11. `R4.poco-p2p` shows D-POCO in a formed group as a client (`groupFormed: true`,
    `isGroupOwner: false`).

Record whether HS3 was needed, and on which unit.

### R6. Setting on, F: station WiFi off on D-HU with a live P2P session, then back

Continues R4's capture and session.

```bash
mark R6-go; echo "listening_before=$(port5277)"
adb -s $DHU shell svc wifi disable
sleep 15; wifi_on $DHU
mark R6-off-done
adb -s $DHU shell svc wifi enable; sleep 10; wifi_on $DHU
# only if it still reads disabled: adb -s $DHU shell svc wifi enable; sleep 10; wifi_on $DHU
mark R6-on; L=$(lines)
waitline 90 'WifiDirectManager: P2P Group created (fresh this session).' "$(HF)" $L; echo "grp2=$?"
waitline 60 'NetworkDiscovery: scanning P2P ' "$(HF)" $L; echo "rejoin_self=$?"
# only if rejoin_self=1:
#   mark R6-rejoin-cue; cue "R6 HS2: on D-POCO connect to OpenHU again in Settings > Wi-Fi > Wi-Fi Direct"
#   waitline 180 'NetworkDiscovery: scanning P2P ' "$(HF)" $L; echo "rejoin_hand=$?"
waitline 60 'SSL handshake complete' "$(HF)" $L; echo "ssl2=$?"
waitline 30 'Throughput over ' "$(HF)" $L; sleep 10
echo "listening_after=$(port5277)"
mark R6-end; clean_hu; cap_stop; th_report R46
adb -s $DHU shell dumpsys wifip2p | grep -a -m2 -i devicename           # record after
p2p $POCO                                                               # must not still be a client of OpenHU before R2
```

If `rejoin_hand` is 1, the operator missed HS2 a second time: grade the WiFi-off half and mark the
return half INCONCLUSIVE.

**R6 PASS, all of:**

1. `hwin R6-go R6-off-done`: `with a live session` 1,
   `but this session does not ride that link; leaving it alone` 0,
   `AapService: link-loss teardown finished in ` 1, `AapService: Disconnected.` 1 or more.
2. Phone, `pwin R6-go R6-off-done`: `received ByeByeRequest` 1 or more. Our close reached the phone
   before the link went.
3. `hwin R6-on R6-end`: `WifiDirectManager: P2P Group created (fresh this session).` 1 or more,
   `NetworkDiscovery: scanning P2P ` 1 or more, `SSL handshake complete` 1, and `rend` 1 or more.
4. Time from the first `NetworkDiscovery: scanning P2P ` to `SSL handshake complete` in that window:
   60000 or less.
5. Phone, `pwin R6-on R6-end`: `Head unit connected` 1 or more. `listening_after` 1 or more.
6. `$OUT/hand-steps.log` holds no HS1 between `R6-go` and `R6-end`: the phone's server needed no
   restart.

**Wedge:** `ssl2` is 1, `listening_after` is 1 or more, and phone `Head unit connected` after `R6-on`
is 0. That is a FAIL: quote the last 20 phone lines of the window.

Record: whether the phone rejoined by itself (`rejoin_self` 0) or needed HS2 again, whether
`WifiDirectManager: P2P enabled, auto-starting WiFi Direct visibility` appeared after `R6-on`, and
`ssid $DHU` at the end.

### R2. Setting off, Native AA, five sessions, B then P

Run ids `R2-B` and `R2-P`. Before the first arm: D-POCO's screen is idle (stage precheck), D-POCO is
not a client of any group (`p2p $POCO`), and D-SAM's Bluetooth is off or D-SAM is unplugged
(D-POCO quirk: a stale bond can stop the wake).

```bash
RUN=R2-X; WANT_MD5=<md5 of X.apk>
adb -s $DHU install -r -d $OUT/apk/X.apk; apk_check; state
adb -s $DHU shell am force-stop $PKG; hu_put "$BASEXML" $KEYS_R2       # read back
th_gate
arm() { local r; r=$(send ACTION_START_WIRELESS_SCAN | tr -d '\r'); echo "$r" | grep -q 'not allowed\|Exception' && adb -s $DHU shell am start -n $MAIN >/dev/null; }
cap_start; mark R2-X-start
mark R2-X-s1-go; L=$(lines); arm
waitline 120 'SSL handshake complete' "$(HF)" $L; echo "s1=$?" | tee -a $OUT/$RUN.summary
waitline 30 'Throughput over ' "$(HF)" $L; mark R2-X-s1-done
for n in 1 2 3 4 5; do
  sleep 5; mark R2-X-x$n-go; send ACTION_DISCONNECT > $OUT/$RUN-x$n.reply; sleep 8; mark R2-X-x$n-done; sleep 2
  [ $n -eq 5 ] && break
  m=$((n+1)); mark R2-X-s$m-go; L=$(lines); arm
  waitline 120 'SSL handshake complete' "$(HF)" $L; echo "s$m=$?" | tee -a $OUT/$RUN.summary
  waitline 30 'Throughput over ' "$(HF)" $L; mark R2-X-s$m-done
  grep -q "s$m=1" $OUT/$RUN.summary && break
done
mark R2-X-end; clean_hu; cap_stop; th_report $RUN
```

A session that does not form ends the arm.

**PASS (each arm), all of:**

1. Sessions 5 of 5. Each `R2-X-sN-go` to `R2-X-sN-done`: `SSL handshake complete` 1 and `rend` 1 or
   more.
2. Each exit `R2-X-xN-go` to `R2-X-xN-done`: `Disconnect action received.` 1 and
   `AapService: Native AA user exit. Stopping active launcher.` 1. Phone `pwin` of the same window:
   `received ByeByeRequest` 1 or more.
3. Whole capture on P: `Auto/P2P` 0 and `NetworkDiscovery: scanning P2P ` 0.
4. Record `createGroup SUCCESS` and `MATCH! Starting AapService` over the whole capture.

**R2-P PASS** also needs sessions equal to R2-B's and a `createGroup SUCCESS` count no more than
R2-B's plus 1. **If R2-B does not reach 5 of 5, R2-P is INCONCLUSIVE.**

End of stage 2: restore D-HU's `settings.xml` from `$BASEXML` as root (D-HU quirk), `chown` and
`chmod` it, and diff it against the backup.

### Stop rule

- A setup failure (wrong commit, a missing `AutomationReceiver: ` line, a setup condition missed) is
  re-run once. A second setup failure makes that run UNTESTABLE.
- Every run runs at most 2 times, and the second time only after a setup failure or a discard.
- R1: 3 exits per arm, and the arm ends at the first failed reconnect. A second wedge in the round
  stops R1 and R6.
- R2: 5 sessions per arm, and the arm ends at the first session that does not form.
- R5B: at most 10 attempts, and it stops after the 5th hit.
- No run is repeated to turn an INCONCLUSIVE into a PASS.

## 8. Do not re-run

- B closing a Self Mode session on `svc wifi disable` with no wireless launcher armed.
  `self-mode-vpn-release` round 1 (R5A, R5P, R5L) measured it on D-POCO and D-HU.
- The policy decision for H1 in isolation. The ticket's JVM test is red on P and green on B and F.
- The change's own unit tests: 81 tests in 9 classes passed on the review host. R0 runs F's whole
  suite, which covers them.

## 9. Report back

1. **R3, the point of the round:** `leave`, `close` and `disc` for each of B, P and F. B 1/0/0, P
   0/1/1 or more and F 1/0/0 is the expected answer: H1 is real, and F removes it.
2. **R5B:** attempts, hits, and hits that reproduced H2 with their `outage_ms`. Also R5A's count of
   `WifiDirectManager: P2P Group created (fresh this session).` in 300 s.
3. **R1 and R2:** sessions formed out of sessions tried, per arm, and `createGroup SUCCESS` on R2-B
   against R2-P.

Also give R4's and R6's verdicts with the scan-to-SSL times, every hand step taken (from
`hand-steps.log`), D-POCO's Gearhead `versionName`, the three APK md5s, and D-HU's WiFi Direct device
name before stage 2 and after R6.

## 10. Decisive strings

Every log string the runs grep, one per line. Lines 1 to 38 are ours, or fragments of ours, and each
matches `git grep -F` in `app/src` at `ce5e61cb`. Line 39, `group=true`, is the value our badge line
prints and is not a literal in the source. Lines 40 to 42 are Gearhead lines and line 43 is the
framework's `WifiService` line; the runs grep them on the device capture, not against our source.

```decisive-strings
AutomationReceiver: 
AutomationMarker: 
WifiLauncher: Initializing WiFi Mode: 
WifiLauncher: wireless bring-up requested, but WiFi is not one of the chosen
WifiDirectManager: Stopping and cleaning up...
Auto start selfmode
SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...
SSL handshake complete
Throughput over 
but this session does not ride that link; leaving it alone
with a live session
AapService: link-loss teardown finished in 
AapService: Disconnected.
Disconnect action received.
AapService: Native AA user exit. Stopping active launcher.
AapService: Force-starting WIFI-Scan from UI
NetworkDiscovery: Found Headunit Server on 
Auto-connecting to Headunit Server at 
(reusing socket)
createGroup SUCCESS
MATCH! Starting AapService
Auto/P2P
; proceeding with group creation
WifiDirectManager: P2P Group created (fresh this session).
No existing P2P group, creating new one
Existing P2P group found
WifiDirectManager: createGroup failed: 
WifiDirectManager: Chip is BUSY, retrying in 2s...
Auto/P2P badge: group=
 abandoned - the manager was stopped while it was in flight.
the Native AA group create
WifiDirectManager: the group that create made was removed with it.
WifiDirectManager: a later bring-up owns the group now, so the abandoned create leaves it alone.
WifiDirectManager: P2P enabled, auto-starting WiFi Direct visibility
NetworkDiscovery: scanning P2P 
Auto/P2P: transport failure, keeping wireless recovery armed
removed with it
NATIVE
group=true
Head unit connected
received ByeByeRequest
Head unit disconnected
setWifiEnabled
```
