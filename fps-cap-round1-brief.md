# fps-cap - round 1 brief

> **Revised 2026-10-26: the candidate is now `main` itself.** PR #1139 (`feat/overlay-device-fields`) merged and the
> branch left `fork`. C moved from `8e031b17` to `main` `947b3ae9` (tree `e7112fbf`), fetched from `origin`. The patch is the
> same, now inside `main`, which carries about 50 more commits than the old base. Rebuild C as `cand-947b3ae9.apk`. Head
> unit strings were checked again at `947b3ae9`. No run, string or verdict rule changed.

> **Revised 2026-10-22: the candidate was rebased onto `main` `e9377ff9`.** C moved from `85e69f17` to
> `8e031b17`. The commit is the same patch. Only the base and the translations main reworded moved. Rebuild
> C. No run, string or verdict rule changed. A copy that still names `85e69f17` is stale.

**Queue:** units D-POCO (head unit) and D-MOTO (phone). D-HU stays plugged in with its app stopped and its Bluetooth off. D-SAM and D-HP are not used. One build: the candidate `main` at `947b3ae9`. One conditional build: `v.3.4.0` (`013b05bc`), only if a run tells you to.
**Time:** build 15 min, preparation 10 min, R1 to R4 5 min each, R5 25 min if it runs (build plus run). About 50 min, or 75 min with R5.
**Run names:** R1 to R5. R1 and R3 are the point of the round.

## 1. Build and baseline

- **Candidate:** `main` at `947b3ae9fae9834aa8e4145ee91751ed743c2ae0` (tree `e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165`). It holds the overlay device fields (PR #1139, was `8e031b17`). First round of this thread.
- **Build:** `git -C <wt> fetch origin main && git -C <wt> checkout --detach 947b3ae9 && git -C <wt> rev-parse --short HEAD` must print `947b3ae9`. `git -C <wt> rev-parse HEAD^{tree}` must print `e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165`. Then `$RIG_TOOLKIT/rig_thermal.sh wait 70` and `HUR_DIR=<wt> MAX_WORKERS=2 $RIG_TOOLKIT/build_hur_cool.sh`. Copy the APK out of `apks/` to `$EV/apk/cand-947b3ae9.apk` at once.
- **Conditional baseline (R5 only):** in the same worktree, `git -C <wt> checkout --detach 013b05bc` (tag `v.3.4.0`), build the same way, copy to `$EV/apk/v340-013b05bc.apk`. Build it only when R5's condition holds. No capture may record during that build.
- **Save the installed APK first** (P1), so F2 can put it back.
- **Install:** `adb -s $HU shell am force-stop $PKG`, then `adb -s $HU install -r -d <apk>`. Run `$RIG_TOOLKIT/apk_identity.sh` and record the md5. Then read every §4 key back (an install can wipe `settings.xml`, `rig-quirks/topics/tooling.md`).
- **Identity:** `send ACTION_QUERY_STATE` must carry `"commit":"947b3ae9`, then the md5.
- **What the candidate changes:** it is `main`. The thread's own change is three new lines on the performance overlay (battery, free memory, WiFi signal). It touches nothing on the video path.
- **Unit tests:** the candidate is upstream `main`, and its own CI covers it. The rig runs no unit tests.

## 2. What this is and why

**The symptom.** On a car trip with D-POCO as the head unit and D-MOTO as the phone (wireless Native AA, 5 GHz group, H.264, 1080p, `fps-limit` 60), the overlay showed 29 to 30 fps for the whole trip. D-POCO's vehicle information was display name `Open Headunit`, make `Google`, model `Desktop Head Unit`, head unit make `Google`.

**A second observation, by the operator on 2026-10-20.** A Self Mode session on D-POCO (D-POCO is phone and head unit at once) also stayed at 30 fps. It stayed at 30 at 720p too, and a hard swipe of the map did not raise it. D-POCO has the capacity and the memory for 60 fps.

**The question.** Did our changes break 60 fps, and if not, what holds the picture at 30?

**What the code says on our side.** One function decides the frame rate we announce, `ServiceDiscoveryResponse.announcedFrameRate`. Its only caps are the 2.4 GHz cap and the starvation cap. Both were off. It has no Self Mode branch. No commit since `v.3.4.0` changes the announced rate, the decoder's drop rule or what the overlay counts. None of this is measured on the device yet.

**What the code says on the phone's side (Gearhead 18.0, decompiled; 17.5 and 17.8 carry the same lists).** Android Auto sets the projected display's maximum rate from the config it accepts (`configureForDisplay`). It then applies the lowest of its active limiters:

| Limiter (as the dump names it) | When it gives 30 | Does a touch lift it? |
|---|---|---|
| `PowerBasedLimiter{lowPower=..., interactive=...}` | car make on `cap_fps_when_non_interactive_cars` (APK default holds `google`) and no input for about 2 s; or `lowPower=true` and no input | yes, for about 2 s |
| `RestrictionLevelLimiter{level=...}` | `MEDIUM` gives 30, `HIGH` gives 15; set by two or more displays or by thermal state | no |
| `FrameRateLimitManagerStaticImpl` | car on `disable_dynamic_frame_rate_list` (APK default: Chevrolet myLink, VW, Seat and Skoda TechniSat units; not Google) | no, fixed at 30 |

The Self Mode result does not fit the first row: touch should have lifted it. So the round no longer assumes a mechanism. **Android Auto's own service dump names the limit in force and the maximum it configured.** That dump is the round's main instrument, beside our own `RECV:` and `Throughput over` counts.

The make `Google` also gates sideloaded apps (`CAR.VALIDATOR` needs manufacturer exactly `Google` and model exactly `Desktop Head Unit`), so R2's make change is a measurement only, not a proposed fix.

This round asks four questions:

1. **R1: in the trip's setup, what does D-MOTO's dump say while idle and while the map is panned, and how many frames reach D-POCO and get drawn?** The point of the round.
2. **R2: does the dump change with make `Mazda`?** A control, one setting changed.
3. **R3: what does D-POCO's own dump say in Self Mode, at 1080p?** Repeats the operator's test with the instrument. The other point of the round.
4. **R4: the same at 720p.** **R5** runs only if frames arrive at about 60 and we draw about 30.

## 3. What is different this round

- **Two setups.** R1 and R2: D-POCO is the head unit and D-MOTO is the phone, as in `forget-car-every-connection` round 1 Stage 2. R3 and R4: D-POCO alone in Self Mode. D-POCO's bonded list redacts D-MOTO's address, so R1 and R2 use an empty wake list and `native-poke-all-paired` `true`.
- **Self Mode needs D-POCO's Android Auto head unit server on `:5277`.** It can be down after any force-stop of Android Auto (`rig-quirks/topics/gearhead.md`). P7 checks it, and turning it on is a hand step. **Never force-stop `com.google.android.projection.gearhead` in this round.**
- **D-POCO runs at VERBOSE** (`log-level` 0) for the `RECV:` lines (`AapMessageIncoming` guards them with `LOG_VERBOSE`). Expect large captures.
- **Swipes into the projected video are this round's input.** They go to the phone over the touch channel, the template's standing exception to the no-tap rule, and are not limited to 5 per run.
- **The phone dump is unproven as a route.** No round has read Android Auto's frame-rate dump yet. `fc_dump.sh` dumps every running service of the package and saves the whole output, so the host can search it if the expected lines are absent. If no file holds `FrameRateLimitManager`, the dump conditions are unobservable; say so and grade on the frame counts.
- **Changing `vehicle-make` makes the phone see a new car.** D-MOTO can show a one-time consent or "new car" screen on R2's first session. Hand step.
- **Idle frame counts do not decide anything.** A parked map sends few frames whatever the limit is. Idle windows are graded on the dump only.
- **The map cannot move by itself on the bench** (`rig-quirks/topics/gearhead.md`: mocked GPS does not reach Android Auto's navigation). The pan makes the picture change.

## 4. Settings keys

Written by `fc_keys.sh` with the app stopped, then read back. `MAKE`, `RES` and `MODE` come from the run (§7).

| Element | Why |
|---|---|
| `<int name="wifi-connection-mode" value="3" />` | Native AA (R1, R2); Self Mode is started by its verb |
| `<int name="native-ap-transport" value="0" />` | WiFi Direct |
| `<int name="wifi-direct-band" value="0" />` | automatic, which asks for 5 GHz |
| `<boolean name="wifi-direct-stable-identity" value="true" />` | as the earlier D-POCO stage |
| `<set name="native-poke-bt-macs" />` | empty wake list |
| `<boolean name="native-poke-all-paired" value="MODE" />` | `true` in R1, R2 and R5 (wakes D-MOTO); `false` in R3 and R4 |
| `<int name="native-driver-selection-mode" value="0" />` | no driver selector |
| `<int name="onboarding-version" value="2" />` | no wizard |
| `<int name="log-level" value="0" />` | VERBOSE, for `RECV:` |
| `<int name="fps-limit" value="60" />` | the trip's value |
| `<int name="resolutionId" value="RES" />` | 3 (1080p), except R4 (2, 720p) |
| `<string name="video-codec">H.264</string>` | the trip's codec |
| `<boolean name="narrow-band-profile-cap" value="true" />` | the default; the run proves it does not fire |
| `<boolean name="video-profile-starvation-cap" value="false" />` | off |
| `<boolean name="force-software-decoding" value="false" />` | hardware decoder |
| `<boolean name="kill-on-disconnect" value="false" />` | |
| `<string name="vehicle-make">MAKE</string>` | `Google`, except R2 (`Mazda`) |
| `<string name="head-unit-make">Google</string>` | the trip's value |

Before the first write, back up `settings.xml` (P2). Diff it against the last backup you hold for D-POCO and state the delta in Setup notes.

## 5. Shared commands

Source the toolkit's `rig_devices.sh` before `fc_env.sh`, so `$RIG_TOOLKIT` is set.

**fc_env.sh**

```bash
export PKG=com.andrerinas.headunitrevived
export GH=com.google.android.projection.gearhead
export HU=4f4027e9 PH=ZY22GC3BM4 DHU=27870808938846
export EV=$HOME/Coding/StudioProjects/rig-data/rounds/fps-cap-round1
mkdir -p $EV/apk $EV/scripts $EV/dumps
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { adb -s $HU shell log -t RIGMARK "$1"; adb -s $PH shell log -t RIGMARK "$1"; echo "$(date +%T.%3N) $1" >> $EV/marks.txt; }
waitline() { for i in $(seq 1 "$3"); do grep -aqF "$2" "$1" && return 0; sleep 1; done; return 1; }  # file, fixed string, cap s
huexit() { adb -s $HU shell am start -a android.intent.action.VIEW -d "headunit://exit"; sleep 3; adb -s $HU shell am force-stop $PKG; }
launch() { adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity; }
```

**fc_keys.sh**

```bash
#!/bin/bash
# usage: MAKE=<Google|Mazda> RES=<3|2> MODE=<true|false> fc_keys.sh <out-file>
source "$(dirname "$0")/fc_env.sh"
: "${MAKE:?}" "${RES:?}" "${MODE:?}"
adb -s $HU shell am force-stop $PKG
python3 -I $RIG_TOOLKIT/set_prefs_runas_host.py $HU scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:int:wifi-direct-band:0 scalar:boolean:wifi-direct-stable-identity:true setclear:native-poke-bt-macs scalar:boolean:native-poke-all-paired:$MODE scalar:int:native-driver-selection-mode:0 scalar:int:onboarding-version:2 scalar:int:log-level:0 scalar:int:fps-limit:60 scalar:int:resolutionId:$RES scalar:string:video-codec:H.264 scalar:boolean:narrow-band-profile-cap:true scalar:boolean:video-profile-starvation-cap:false scalar:boolean:force-software-decoding:false scalar:boolean:kill-on-disconnect:false scalar:string:vehicle-make:$MAKE scalar:string:head-unit-make:Google >/dev/null
adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > "$1"
for k in '<int name="log-level" value="0" />' '<int name="fps-limit" value="60" />' "<int name=\"resolutionId\" value=\"$RES\" />" '<string name="video-codec">H.264</string>' '<boolean name="video-profile-starvation-cap" value="false" />' '<boolean name="force-software-decoding" value="false" />' "<string name=\"vehicle-make\">$MAKE</string>" '<string name="head-unit-make">Google</string>' "<boolean name=\"native-poke-all-paired\" value=\"$MODE\" />"; do
  grep -aqF "$k" "$1" || { echo "missing: $k"; exit 8; }; done
exit 0
```

If `set_prefs_runas_host.py` takes its key specs in another form than `set_hu_settings_host.py`, use its own form for the same keys, and say so in Setup notes. The read-back loop is the check either way.

**fc_dump.sh** dumps Android Auto's services on one device and pulls out the frame-rate section:

```bash
#!/bin/bash
# usage: fc_dump.sh <serial> <out-prefix>; writes <out-prefix>-full.txt and <out-prefix>-fr.txt
source "$(dirname "$0")/fc_env.sh"
S=$1; O=$2
adb -s $S shell dumpsys activity service $GH > "$O-full.txt" 2>&1
grep -aE 'FrameRateLimitManager|Fixed frame rate|Current restrictions|no restrictions applied| fps: |powerState:0x' "$O-full.txt" > "$O-fr.txt"
grep -aqF 'FrameRateLimitManager' "$O-fr.txt" && exit 0
# fallback: dump each running service of the package by name
adb -s $S shell dumpsys activity services $GH | grep -aoE "ServiceRecord\{[^}]* $GH/[^ }]+" | awk '{print $NF}' | sort -u > "$O-services.txt"
while read -r c; do echo "=== $c"; adb -s $S shell dumpsys activity service "$c"; sleep 0.3; done < "$O-services.txt" >> "$O-full.txt" 2>&1
grep -aE 'FrameRateLimitManager|Fixed frame rate|Current restrictions|no restrictions applied| fps: |powerState:0x' "$O-full.txt" > "$O-fr.txt"
grep -aqF 'FrameRateLimitManager' "$O-fr.txt" && exit 0 || exit 1
```

**fc_pan.sh**

```bash
#!/bin/bash
# usage: fc_pan.sh <seconds>; swipes the middle of the projection left and right, about one per second
source "$(dirname "$0")/fc_env.sh"
S=$(adb -s $HU shell dumpsys window displays | grep -ao 'cur=[0-9]*x[0-9]*' | head -1 | tr -dc '0-9x')
W=${S%x*}; H=${S#*x}
X1=$((W*40/100)); X2=$((W*60/100)); Y=$((H*50/100))
echo "display ${W}x${H} swipe ${X1}<->${X2} at y=${Y}" >> $EV/swipes.txt
END=$(( $(date +%s) + $1 ))
n=0
while [ "$(date +%s)" -lt "$END" ]; do
  if [ $((n % 2)) -eq 0 ]; then adb -s $HU shell input swipe $X1 $Y $X2 $Y 300; else adb -s $HU shell input swipe $X2 $Y $X1 $Y 300; fi
  n=$((n+1)); sleep 0.4
done
echo "swipes=$n" >> $EV/swipes.txt
```

**fc_state.sh** records one device's power and thermal state:

```bash
#!/bin/bash
# usage: fc_state.sh <serial> <out-file>
source "$(dirname "$0")/fc_env.sh"
S=$1
{ echo "low_power=$(adb -s $S shell settings get global low_power | tr -d '\r')"
  adb -s $S shell dumpsys battery | grep -aE '^ *(level|AC powered|USB powered|temperature):'
  adb -s $S shell dumpsys thermalservice | grep -a -m1 'Thermal Status'
  adb -s $S shell dumpsys package $GH | grep -a -m1 versionName
} > "$2"
```

**fc_extract.sh** is the extract Haiku returns. It prints only numbers and quoted lines.

```bash
#!/bin/bash
# usage: fc_extract.sh <hu-capture> <run>   (windows: <run>-idle-start..-idle-end, <run>-pan-start..-pan-end)
HUF=$1; R=$2
win() { awk -v a="$2" -v b="$3" 'index($0,"RIGMARK") && index($0,a){f=1;next} index($0,"RIGMARK") && index($0,b){f=0} f' "$1"; }
med() { sort -n | awk '{a[NR]=$1} END{if(NR==0){print "none"} else {print a[int((NR+1)/2)]}}'; }
starts() { grep -aP 'RECV: VIDEO.*flags: (11|9) ' | awk '{print substr($2,1,8)}' | uniq -c | awk '{a[NR]=$1} END{for(i=2;i<NR;i++) print a[i]}' | med; }  # drops the two partial seconds
for w in idle pan; do
  echo "$R $w frame_starts_per_s_median=$(win $HUF $R-$w-start $R-$w-end | starts)"
  echo "$R $w rendered_fps_median=$(win $HUF $R-$w-start $R-$w-end | grep -aF 'Throughput over' | grep -aoP 'rendered=\d+ \(\K\d+' | med) fed_fps_median=$(win $HUF $R-$w-start $R-$w-end | grep -aF 'Throughput over' | grep -aoP 'fed=\d+ \(\K\d+' | med)"
  echo "$R $w skipped_sum=$(win $HUF $R-$w-start $R-$w-end | grep -aF 'Throughput over' | grep -aoP 'skipped=\K\d+' | awk '{s+=$1} END{print s+0}')"
  echo "$R $w phone_rate_changes=$(win $HUF $R-$w-start $R-$w-end | grep -acF 'Frame rate change.')"
done
echo "$R announced|$(win $HUF $R-launch $R-end | grep -aF '[ServiceDiscovery]' | grep -aoE 'target=[0-9]+x[0-9]+@[0-9]+' | head -1)"
echo "$R narrowband_lines=$(win $HUF $R-launch $R-end | grep -acF 'AAC audio announced by the 2.4 GHz cap')"
echo "$R sink|$(win $HUF $R-launch $R-end | grep -aoE 'Media Sink Setup Request: [0-9]+ on channel VIDEO' | head -1)"
echo "$R start|$(win $HUF $R-launch $R-end | grep -aoE 'Media Start Request VIDEO: session=[0-9]+, config_index=[0-9]+' | head -1)"
echo "$R group|$(win $HUF $R-launch $R-end | grep -aoE 'Freq: [0-9]+ MHz' | sort -u | tr '\n' ' ')"
echo "$R ssl=$(win $HUF $R-launch $R-end | grep -acF 'SSL handshake complete. Session id')"
echo "$R throughput_windows=$(win $HUF $R-launch $R-end | grep -acF 'Throughput over')"
for d in idle pan; do f=$EV/dumps/$R-$d-fr.txt; echo "$R dump_$d|$( [ -s $f ] && tr '\n' '|' < $f | cut -c1-600 || echo absent)"; done
```

In R3 and R4 the phone's lines are in D-POCO's own capture, so `phone_rate_changes` counts them there. In R1 and R2, count them in D-MOTO's capture: `grep -acF 'Frame rate change.'` over the same marker windows of `$EV/<run>-ph.txt`, and add the two counts to the extract by hand.

- **The launch** is `launch`. **Do not send `ACTION_START_WIRELESS_SCAN` after it** (template §4 step 4).
- **The phone** leaves the Bluetooth link only by `svc`, never by airplane mode.

## 6. The deciding lines

Head unit strings were checked with `grep -F` against `947b3ae9`. Android Auto strings were read in Gearhead 18.0 (decompiled); none has been seen on the rig yet.

Head unit capture, `$EV/<run>-hu.txt`:

| Line | Level | Meaning |
|---|---|---|
| `RECV: VIDEO ... flags: 11 ` / `flags: 9 ` | VERBOSE only | one frame start each; 8 and 10 continue a frame |
| `Throughput over <n>ms: rendered=<n> (<n>fps), fed=<n> (<n>fps), dropped=<n>, skipped=<n>, ...` | INFO | `fed` is what reached the decoder, `rendered` what it drew |
| `[ServiceDiscovery] Negotiating a profile ... target=<w>x<h>@60` | INFO (WARN if not adequate) | what we announced |
| `[ServiceDiscovery] AAC audio announced by the 2.4 GHz cap` | INFO | the 2.4 GHz cap fired; must be 0 |
| `Media Sink Setup Request: 3 on channel VIDEO` | INFO | 3 is H.264, 7 is H.265 |
| `Media Start Request VIDEO: session=<n>, config_index=<n>` | INFO | 1 means the phone took the 720p fallback |
| `WifiDirectManager: onGroupInfoAvailable: ... Freq: <n> MHz` | INFO | the group's band (R1, R2) |
| `SSL handshake complete. Session id` | INFO | the session formed |

Android Auto's dump, `$EV/dumps/<run>-<idle|pan>-fr.txt`:

| Line | Meaning |
|---|---|
| `FrameRateLimitManagerImpl (limit: <n> fps, max: <m> fps)` | `max` is what the display was configured for; `limit` is what is in force |
| `  Fixed frame rate: <n> fps` | a fixed rate overrides every limiter |
| `    -- no restrictions applied --` | no limiter active |
| `    <n> fps: PowerBasedLimiter{lowPower=<b>, interactive=<b>}` | the make or power limiter, and its inputs |
| `    <n> fps: RestrictionLevelLimiter{level=<L>}` | the display-count or thermal limiter |
| `FrameRateLimitManagerStaticImpl: current frame rate: <n>, fixed frame rate:<m>` | the static path, fixed at 30 |
| `powerState:0x<h>,thermalStatus:<n>,...,isPowerSaveModeOn:<b>` | Android Auto's power view |

Android Auto's log, phone capture (D-MOTO in R1 and R2, D-POCO in R3 and R4):

| Line | Meaning |
|---|---|
| `CAR.WM.FrameRateLimit` ... `Frame rate change. <a> fps -> <b> fps` | the limit changed; 0 means no limit |

## 7. Runs

### Preparation (once, before R1)

- **P0.** On D-HU: `adb -s $DHU shell am force-stop $PKG`, then `adb -s $DHU shell svc bluetooth disable`. It reverts on by itself; check it before each run and disable it again if needed. Say so in Setup notes.
- **P1.** Save D-POCO's installed APK: `P=$(adb -s $HU shell pm path $PKG | head -1 | sed 's/^package://' | tr -d '\r')`, then `adb -s $HU pull "$P" $EV/apk/installed-before.apk`. Record its md5 and `send ACTION_QUERY_STATE`'s commit.
- **P2.** `adb -s $HU shell am force-stop $PKG`, then `adb -s $HU shell run-as $PKG cat shared_prefs/settings.xml > $EV/settings-before.xml`. Record these values verbatim before any write: `vehicle-make`, `head-unit-make`, `vehicle-model`, `fps-limit`, `resolutionId`, `video-codec`, `video-profile-starvation-cap`, `narrow-band-profile-cap`.
- **P3.** Bonds: `adb -s $PH shell dumpsys bluetooth_manager | grep -a -A12 'Bonded devices'` must list `POCO X3 NFC`. If not, stop and ask the operator to pair them.
- **P4.** D-MOTO unlocked and on its launcher: `adb -s $PH shell dumpsys window | grep -a mCurrentFocus`. If it shows a keyguard or PIN screen, ask the operator to unlock it once (hand step, D-MOTO quirk). Then `adb -s $PH shell svc power stayon true`.
- **P5.** D-POCO idle at home: `adb -s $HU shell dumpsys window | grep -a mCurrentFocus`; if not the launcher, `adb -s $HU shell input keyevent KEYCODE_HOME`. Then `adb -s $HU shell svc power stayon true`.
- **P6.** Install the candidate (§1). Then `fc_state.sh $PH $EV/p6-moto-state.txt` and `fc_state.sh $HU $EV/p6-poco-state.txt`.
- **P7.** D-POCO's head unit server: `adb -s $HU shell cat /proc/net/tcp6 /proc/net/tcp | grep -ci ':149D'`. Record the count. If it is 0, ask the operator to turn on "Start head unit server" in Android Auto's developer settings on D-POCO (hand step), then read the count again. It must be 1 or more before R3.
- **P8.** Dump route check, no session needed: `fc_dump.sh $PH $EV/dumps/p8-moto`. Record the exit code and the line count of `p8-moto-full.txt`. Exit 1 with no session is expected; the check is that the command runs and writes a file.

### The native session (R1, R2, R5)

**fc_run.sh**

```bash
#!/bin/bash
# usage: R=<run> MAKE=<m> RES=<3|2> fc_run.sh ; exit 0 ok, 8 keys, 9 no session, 10 no video, 12 host hot
source "$(dirname "$0")/fc_env.sh"
: "${R:?}" "${MAKE:?}" "${RES:?}"
$RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || exit 12
$RIG_TOOLKIT/rig_thermal.sh watch $EV/thermal-$R.log & TH=$!
# 1. phone off the Bluetooth link
adb -s $PH shell svc bluetooth disable; sleep 3
# 2. keys with the app stopped, then captures
MODE=true "$(dirname "$0")/fc_keys.sh" $EV/$R-keys.txt || { kill $TH; exit 8; }
"$(dirname "$0")/fc_state.sh" $PH $EV/$R-moto-state-before.txt
adb -s $HU logcat -G 16M; adb -s $PH logcat -G 16M
adb -s $HU logcat -c; stdbuf -oL adb -s $HU logcat -v time > $EV/$R-hu.txt & HC=$!
adb -s $PH logcat -c; stdbuf -oL adb -s $PH logcat -v time > $EV/$R-ph.txt & PC=$!
sleep 2; mark $R-launch
# 3. launch, then phone Bluetooth on
launch; sleep 15
adb -s $PH shell svc bluetooth enable
# 4. session and video, capped waits
waitline $EV/$R-hu.txt 'SSL handshake complete. Session id' 120 || { mark $R-end; kill $HC $PC $TH; huexit; exit 9; }
waitline $EV/$R-hu.txt 'Throughput over' 60 || { mark $R-end; kill $HC $PC $TH; huexit; exit 10; }
# 5. navigation full screen on the projection
adb -s $PH shell am start -n com.google.android.apps.maps/com.google.android.maps.MapsActivity -a android.intent.action.VIEW -d google.navigation:q=6.1738,-75.3364
sleep 20
adb -s $HU shell screencap -p /sdcard/fc.png; adb -s $HU pull /sdcard/fc.png $EV/$R-screen.png
# 6. idle window, no input; dump at its end
mark $R-idle-start; sleep 50
"$(dirname "$0")/fc_dump.sh" $PH $EV/dumps/$R-idle; mark $R-idle-end
# 7. pan window; dump in the middle, while swipes run
mark $R-pan-start
"$(dirname "$0")/fc_pan.sh" 60 & PAN=$!
sleep 30; "$(dirname "$0")/fc_dump.sh" $PH $EV/dumps/$R-pan
wait $PAN; mark $R-pan-end
# 8. end
sleep 15; mark $R-end
"$(dirname "$0")/fc_state.sh" $PH $EV/$R-moto-state-after.txt
huexit; sleep 2; kill $HC $PC $TH
exit 0
```

Step 5's screenshot is for the host. If it does not show a full-screen map, the pan still runs; record that in Setup notes. If the intent opens a chooser on D-MOTO, pick Maps, "Just once", and say so (one hand step).

### The Self Mode session (R3, R4)

**fc_self.sh**

```bash
#!/bin/bash
# usage: R=<run> RES=<3|2> fc_self.sh ; exit 0 ok, 8 keys, 9 no session, 10 no video, 12 host hot, 13 no :5277
source "$(dirname "$0")/fc_env.sh"
: "${R:?}" "${RES:?}"
[ "$(adb -s $HU shell cat /proc/net/tcp6 /proc/net/tcp | grep -ci ':149D')" -ge 1 ] || exit 13
$RIG_TOOLKIT/rig_thermal.sh wait 75 || $RIG_TOOLKIT/rig_thermal.sh wait 75 || exit 12
$RIG_TOOLKIT/rig_thermal.sh watch $EV/thermal-$R.log & TH=$!
MAKE=Google MODE=false "$(dirname "$0")/fc_keys.sh" $EV/$R-keys.txt || { kill $TH; exit 8; }
"$(dirname "$0")/fc_state.sh" $HU $EV/$R-poco-state-before.txt
adb -s $HU logcat -G 16M
adb -s $HU logcat -c; stdbuf -oL adb -s $HU logcat -v time > $EV/$R-hu.txt & HC=$!
sleep 2; adb -s $HU shell log -t RIGMARK $R-launch
send ACTION_START_SELF_MODE
waitline $EV/$R-hu.txt 'SSL handshake complete. Session id' 60 || { adb -s $HU shell log -t RIGMARK $R-end; kill $HC $TH; huexit; exit 9; }
waitline $EV/$R-hu.txt 'Throughput over' 60 || { adb -s $HU shell log -t RIGMARK $R-end; kill $HC $TH; huexit; exit 10; }
sleep 20
adb -s $HU shell screencap -p /sdcard/fc.png; adb -s $HU pull /sdcard/fc.png $EV/$R-screen.png
adb -s $HU shell log -t RIGMARK $R-idle-start; sleep 50
"$(dirname "$0")/fc_dump.sh" $HU $EV/dumps/$R-idle; adb -s $HU shell log -t RIGMARK $R-idle-end
adb -s $HU shell log -t RIGMARK $R-pan-start
"$(dirname "$0")/fc_pan.sh" 60 & PAN=$!
sleep 30; "$(dirname "$0")/fc_dump.sh" $HU $EV/dumps/$R-pan
wait $PAN; adb -s $HU shell log -t RIGMARK $R-pan-end
sleep 15; adb -s $HU shell log -t RIGMARK $R-end
"$(dirname "$0")/fc_state.sh" $HU $EV/$R-poco-state-after.txt
huexit; sleep 2; kill $HC $TH
exit 0
```

Self Mode opens Android Auto's own home screen. If the screenshot shows no map, the pan still runs on whatever is in the middle; record what the screenshot shows.

### After every session

Run `fc_extract.sh $EV/<run>-hu.txt <run> > $EV/<run>-extract.txt`. For R1 and R2, add D-MOTO's `Frame rate change.` counts as §5 says. Haiku returns the extract, the exit code and the two `-fr.txt` files whole. Sonnet grades from them.

Discard and re-run a session once if the head unit capture has `MATCH! Starting AapService`, a second `SSL handshake complete. Session id`, or `AapRead: Magic Garbage detected in header`. A second discard makes the run INCONCLUSIVE.

### Gates (every run; a failed gate makes the run INCONCLUSIVE, with the reason)

- exit code 0;
- `announced|target=<w>x<h>@60`, with the run's resolution, and `narrowband_lines=0`;
- `sink|Media Sink Setup Request: 3 on channel VIDEO`;
- R1, R2 and R5 only: `group|` shows only frequencies above 4000 MHz.

### R1: trip setup, make Google (**a point of the round**)

`R=R1 MAKE=Google RES=3 fc_run.sh`

PASS, every condition:

1. **Dump, both windows:** `R1-idle-fr.txt` and `R1-pan-fr.txt` each hold a `FrameRateLimitManager` line.
2. **Head unit, pan:** `pan frame_starts_per_s_median` is a number (the pan window carried video).

R1 is a measurement, so its PASS only says the instruments worked. Record and report, verbatim:

- each dump's `limit:` and `max:` values, every `fps:` line under `Current restrictions`, any `Fixed frame rate` line, any `StaticImpl` line, and the `powerState` line;
- `idle` and `pan`: `frame_starts_per_s_median`, `fed_fps_median`, `rendered_fps_median`, `skipped_sum`;
- D-MOTO's `Frame rate change.` lines in both windows, the first three verbatim.

### R2: trip setup, make Mazda (the control)

`R=R2 MAKE=Mazda RES=3 fc_run.sh`

**Hand step, first session only.** The operator watches D-MOTO while R2's session forms. If it shows a consent or "new car" screen, the operator taps it through. Say what the screen said, and the time, in Setup notes. If the session ends because of it, run `fc_run.sh` again with the same variables.

Same PASS conditions and the same records as R1.

### R3: Self Mode on D-POCO, 1080p (**a point of the round**)

`R=R3 RES=3 fc_self.sh`. Exit 13 means `:5277` is down: do P7's hand step, then run it again.

Same PASS conditions and the same records as R1, with D-POCO's dumps and D-POCO's own `Frame rate change.` count from the extract. Also record both `poco-state` files.

### R4: Self Mode on D-POCO, 720p

`R=R4 RES=2 fc_self.sh`. Same conditions and records as R3.

### R5: v.3.4.0 on R1's setup (conditional)

Run R5 only if R1 or R3 shows `pan fed_fps_median` of 50 or more and `pan rendered_fps_median` below 45. That is: frames reach us at about 60 and we draw fewer. Build `v.3.4.0` (§1), install it with `adb -s $HU install -r -d $EV/apk/v340-013b05bc.apk`, run `apk_identity.sh`, read every §4 key back, then repeat the run that met the condition with `R=R5`. `ACTION_QUERY_STATE`'s commit must start `013b05bc`. Same records. Reinstall the candidate before F1.

### Stop rule

Run R1 to R4 in order, then R5 if its condition holds. A run that ends in exit 9 or 10 twice in a row is INCONCLUSIVE; move to the next run. Stop the round after 2 runs in a row are INCONCLUSIVE.

### Finish (once, after the last run)

- **F1.** `adb -s $HU shell am force-stop $PKG`, then put `$EV/settings-before.xml` back with `$RIG_TOOLKIT/restore_settings.sh` and compare it with `cmp`.
- **F2.** Install `$EV/apk/installed-before.apk` with `adb -s $HU install -r -d`, then compare the md5 with P1. Read `settings.xml` back once more and `cmp` it with `settings-before.xml`.
- **F3.** `adb -s $PH shell svc power stayon false` and `adb -s $HU shell svc power stayon false`. D-HU Bluetooth on: `adb -s $DHU shell svc bluetooth enable`.
- **F4.** If R2 ran, D-MOTO now has a `Mazda` car record. Leave it, and say so in Setup notes.

## 8. Do not re-run

Nothing. This is the thread's first round.

## 9. Report back

1. For R1 and R3: each dump's `limit:` and `max:`, and every limiter line under `Current restrictions`, idle and pan. These say what holds 30.
2. For every run: `pan fed_fps_median` and `pan rendered_fps_median`. These say whether frames arrive at 60 and whether we draw them.
3. R2's dumps beside R1's: whether the make changes any limiter.
