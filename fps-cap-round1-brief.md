# fps-cap - round 1 brief

> **Revised 2026-10-29: moved onto `rig-lib`.** The round runs as one `rig_session.py` process from the
> `rig-requires` block below. The runs, the strings and the verdict rules are the same. The helpers are
> functions in `fc_env.sh`, the preparation and the finish are runs P and cleanup, and the grade scripts do
> the grading. Section 3 lists what changed.

> **Revised 2026-10-26: the candidate is now `main` itself.** PR #1139 (`feat/overlay-device-fields`) merged and the
> branch left `fork`. C moved from `8e031b17` to `main` `947b3ae9` (tree `e7112fbf`), fetched from `origin`. The patch is the
> same, now inside `main`, which carries about 50 more commits than the old base. Rebuild C as `cand-947b3ae9.apk`. Head
> unit strings were checked again at `947b3ae9`. No run, string or verdict rule changed.

> **Revised 2026-10-22: the candidate was rebased onto `main` `e9377ff9`.** C moved from `85e69f17` to
> `8e031b17`. The commit is the same patch. Only the base and the translations main reworded moved. Rebuild
> C. No run, string or verdict rule changed. A copy that still names `85e69f17` is stale.

**Queue:** units D-POCO (head unit) and D-MOTO (phone). D-HU stays plugged in with its app stopped and its Bluetooth off. D-SAM and D-HP are not used. Two builds: the candidate `main` at `947b3ae9`, and `v.3.4.0` (`013b05bc`) for R5.
**Time:** builds 30 min, then the session: P 5 min, R1 to R4 6 min each (12 with a retry), R5 6 min if it runs, cleanup 3 min. About 70 min.
**Run names:** P, R1 to R5, cleanup. R1 and R3 are the point of the round.

## 0. What the session needs

```rig-requires
lib 2c7a270e0
candidate C 947b3ae9fae9834aa8e4145ee91751ed743c2ae0
candidate V 013b05bc0e7f5a881ea1d4363b9275df69bd5f5a
unit D-POCO role=headunit wifi=on bt=on
unit D-MOTO role=phone wifi=on aa=D-POCO
unit D-MOTO@P,R1,R2,R5 bt=on bond=D-POCO
unit D-MOTO@R1,R2 nobond=D-HU
unit D-MOTO@R3,R4 bt=off
unit D-HU bt=off
unit D-HU@R1,R2,R3,R4,R5 bt=off
confirm D-MOTO's screen lock is a swipe, not a PIN, so the round can drive it unattended
run P cap=420 script=prep.sh grade=grade-prep.sh
run R1 cap=1080 script=r1.sh grade=grade-r1.sh point=yes
run R2 cap=1080 script=r2.sh grade=grade-r2.sh hand="watch D-MOTO while R2's session forms; tap through any consent or new-car screen, and note its text and the time"
run R3 cap=900 script=r3.sh grade=grade-r3.sh point=yes
run R4 cap=900 script=r4.sh grade=grade-r4.sh
run R5 cap=1200 script=r5.sh grade=grade-r5.sh
run cleanup cap=300 script=cleanup.sh always=yes
stop P FAIL,UNTESTABLE skip=all
```

- D-MOTO must be bonded to D-POCO for the native runs. P reads the bond, so a gap stops the session at the
  start. D-POCO's bonded list redacts D-MOTO's address, so the check matches it by name.
- D-HU's Bluetooth turns itself back on. The scoped line makes `check --fix` switch it off again before each run.
- The stop rule (two runs in a row INCONCLUSIVE) cannot be a `stop` line, because a `stop` line reads one run.
  So each run script checks it first, and a run it stops grades `NOT_RUN`. R5 also grades `NOT_RUN` when its
  condition does not hold.

## 1. Build and baseline

- **Candidate:** `main` at `947b3ae9fae9834aa8e4145ee91751ed743c2ae0` (tree `e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165`). It holds the overlay device fields (PR #1139, was `8e031b17`). First round of this thread.
- **Baseline for R5:** `v.3.4.0` at `013b05bc0e7f5a881ea1d4363b9275df69bd5f5a`. The session cannot stop for a build, so the host builds it with the candidate, before the session. R5 installs it only if its condition holds.
- **What the candidate changes:** it is `main`. The thread's own change is three new lines on the performance overlay (battery, free memory, WiFi signal). It touches nothing on the video path.
- **Unit tests:** the candidate is upstream `main`, and its own CI covers it. The rig runs no unit tests.
- **Install and identity** are in the runs: P saves D-POCO's installed APK, installs the candidate and proves it with `apk_identity.sh` and `"commit":"947b3ae9` in `ACTION_QUERY_STATE`'s reply. R5 does the same for `013b05bc`. Cleanup puts the saved APK back.

The host runs this with `EV` set to the round's evidence dir and `run_in_background`.

**get_apks.sh**

```bash
: "${EV:?set EV to the evidence dir of this round}"
mkdir -p "$EV/apk"
WT=$("$RIG_TOOLKIT/wt-new.sh" fps-cap scratch 947b3ae9 | tail -1)
[ -n "$WT" ] && [ -e "$WT/.git" ] || { echo "NO_WORKTREE"; exit 1; }
git -C "$WT" fetch origin main
for arm in cand-947b3ae9:947b3ae9fae9834aa8e4145ee91751ed743c2ae0 v340-013b05bc:013b05bc0e7f5a881ea1d4363b9275df69bd5f5a; do
  name=${arm%%:*}; sha=${arm#*:}
  git -C "$WT" checkout --detach "$sha" || exit 1
  [ "$(git -C "$WT" rev-parse HEAD)" = "$sha" ] || { echo "HEAD_MISMATCH $name"; exit 1; }
  "$RIG_TOOLKIT/rig_thermal.sh" "wait" 70
  HUR_DIR="$WT" MAX_WORKERS=2 "$RIG_TOOLKIT/build_hur_cool.sh" || exit 1
  cp "$(ls -t "$RIG_DATA"/apks/*.apk | head -1)" "$EV/apk/$name.apk" || exit 1
done
[ "$(git -C "$WT" rev-parse '947b3ae9fae9834aa8e4145ee91751ed743c2ae0^{tree}')" = e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165 ] || echo "TREE_MISMATCH candidate"
(cd "$WT" && ./gradlew --stop)
md5sum "$EV"/apk/*.apk
```

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
- **Self Mode needs D-POCO's Android Auto head unit server on `:5277`.** It can be down after any force-stop of Android Auto (`rig-quirks/topics/gearhead.md`). P records it. R3 and R4 read it first; if it is down, the run prints `OPERATOR NOW` (turn on "Start head unit server" in Android Auto's developer settings on D-POCO) and polls for 5 min. **Never force-stop `com.google.android.projection.gearhead` in this round.**
- **D-POCO runs at VERBOSE** (`log-level` 0) for the `RECV:` lines (`AapMessageIncoming` guards them with `LOG_VERBOSE`). Expect large captures. The run script writes the extract at its end, so the grade reads a small file.
- **Swipes into the projected video are this round's input.** They go to the phone over the touch channel, the template's standing exception to the no-tap rule, and are not limited to 5 per run.
- **The phone dump is unproven as a route.** No round has read Android Auto's frame-rate dump yet. `fc_dump` dumps every running service of the package and saves the whole output, so the host can search it if the expected lines are absent. If no dump file of a run holds `FrameRateLimitManager`, the dump condition is unobservable: the grade says so and grades on the frame counts.
- **Changing `vehicle-make` makes the phone see a new car.** D-MOTO can show a one-time consent or "new car" screen on R2's first session. Hand step (R2's `hand=`). If the session ends because of it, R2's script tries once more by itself.
- **Idle frame counts do not decide anything.** A parked map sends few frames whatever the limit is. Idle windows are graded on the dump only.
- **The map cannot move by itself on the bench** (`rig-quirks/topics/gearhead.md`: mocked GPS does not reach Android Auto's navigation). The pan makes the picture change.
- **Changed by the move to `rig-lib` (2026-10-29).** Markers are `log -t AutomationMarker <tag>` on each device (they were `RIGMARK`), so `rl_win` and `rl_cnt` read the windows. D-MOTO runs no app, so one marker form serves both captures. Captures are `threadtime` (they were `time`). The pan is bounded by a swipe count as well as by time. v.3.4.0 is built before the session, because the session cannot pause for a build. The finish installs the saved APK first and then restores `settings.xml`, because an install can wipe it.

## 4. Settings keys

Written by `fc_keys` with the app stopped, then read back. `MAKE`, `RES` and `MODE` come from the run (§7).

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

P backs up `settings.xml` before the first write (`$EV/settings-before.xml`). Diff it against the last backup you hold for D-POCO and state the delta in Setup notes.

## 5. Shared commands

`rig_run.sh` sources `fc_env.sh` before every script and grade.

**fc_env.sh**

```bash
PKG=com.andrerinas.headunitrevived
GH=com.google.android.projection.gearhead
HU=4f4027e9; PH=ZY22GC3BM4; DHU=27870808938846
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
RUNS_ORDER="R1 R2 R3 R4 R5"
mkdir -p "$EV/dumps"
send() { local a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
mark() { adb -s "$HU" shell log -t AutomationMarker "$1"; adb -s "$PH" shell log -t AutomationMarker "$1"; echo "$(date +%T.%3N) $1" >> "$EV/marks.txt"; }
huexit() { adb -s "$HU" shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 3; adb -s "$HU" shell am force-stop "$PKG"; }
launch() { adb -s "$HU" shell am start -n "$PKG/com.andrerinas.openheadunit.main.MainActivity" >/dev/null; }
num() { [[ "$1" =~ ^-?[0-9]+$ ]] && echo "$1" || echo "${2:-0}"; }
xv() { sed -n "s/^$2=//p" "$1" 2>/dev/null | head -1; }   # xv EXTRACT KEY

# install one APK on D-POCO and prove it: apk_identity.sh, then the commit in QUERY_STATE
install_apk() {
  rl_step "install $1"
  adb -s "$HU" shell am force-stop "$PKG"
  adb -s "$HU" install -r -d "$EV/apk/$1.apk"
  "$RIG_TOOLKIT/apk_identity.sh" "$EV/apk/$1.apk" "$HU" > "$EV/$RUN-identity.txt" 2>&1 || return 1
  send ACTION_QUERY_STATE > "$EV/$RUN-query.txt"
  grep -aqF "\"commit\":\"$2" "$EV/$RUN-query.txt"
}

# the keys of section 4 with the app stopped, then read back
fc_keys() {  # fc_keys MAKE RES MODE OUT
  local k
  adb -s "$HU" shell am force-stop "$PKG"
  python3 -I "$RIG_TOOLKIT/set_prefs_runas_host.py" "$HU" scalar:int:wifi-connection-mode:3 scalar:int:native-ap-transport:0 scalar:int:wifi-direct-band:0 scalar:boolean:wifi-direct-stable-identity:true setclear:native-poke-bt-macs scalar:boolean:native-poke-all-paired:"$3" scalar:int:native-driver-selection-mode:0 scalar:int:onboarding-version:2 scalar:int:log-level:0 scalar:int:fps-limit:60 scalar:int:resolutionId:"$2" scalar:string:video-codec:H.264 scalar:boolean:narrow-band-profile-cap:true scalar:boolean:video-profile-starvation-cap:false scalar:boolean:force-software-decoding:false scalar:boolean:kill-on-disconnect:false scalar:string:vehicle-make:"$1" scalar:string:head-unit-make:Google > "$4.log" 2>&1 || return 8
  adb -s "$HU" shell run-as "$PKG" cat shared_prefs/settings.xml > "$4"
  for k in '<int name="log-level" value="0" />' '<int name="fps-limit" value="60" />' "<int name=\"resolutionId\" value=\"$2\" />" '<string name="video-codec">H.264</string>' '<boolean name="video-profile-starvation-cap" value="false" />' '<boolean name="force-software-decoding" value="false" />' "<string name=\"vehicle-make\">$1</string>" '<string name="head-unit-make">Google</string>' "<boolean name=\"native-poke-all-paired\" value=\"$3\" />"; do
    grep -aqF "$k" "$4" || { echo "missing: $k"; return 8; }
  done
}

# Android Auto's services on one device, and the frame-rate section of them
fc_dump() {  # fc_dump SERIAL OUT_PREFIX: writes PREFIX-full.txt and PREFIX-fr.txt; returns 1 if no FrameRateLimitManager
  local c
  rl_step "dump $2"
  adb -s "$1" shell dumpsys activity service "$GH" > "$2-full.txt" 2>&1
  grep -aE 'FrameRateLimitManager|Fixed frame rate|Current restrictions|no restrictions applied| fps: |powerState:0x' "$2-full.txt" > "$2-fr.txt"   # system
  grep -aqF 'FrameRateLimitManager' "$2-fr.txt" && return 0   # system
  adb -s "$1" shell dumpsys activity services "$GH" | grep -aoE "ServiceRecord\{[^}]* $GH/[^ }]+" | awk '{print $NF}' | sort -u > "$2-services.txt"
  while read -r c; do echo "=== $c"; adb -s "$1" shell dumpsys activity service "$c"; sleep 0.3; done < "$2-services.txt" >> "$2-full.txt" 2>&1
  grep -aE 'FrameRateLimitManager|Fixed frame rate|Current restrictions|no restrictions applied| fps: |powerState:0x' "$2-full.txt" > "$2-fr.txt"   # system
  grep -aqF 'FrameRateLimitManager' "$2-fr.txt"   # system
}

# swipes across the middle of the projection, left and right, about one a second
fc_pan() {  # fc_pan SECONDS
  local S W H X1 X2 Y n=0 end
  S=$(adb -s "$HU" shell dumpsys window displays | grep -ao 'cur=[0-9]*x[0-9]*' | head -1 | tr -dc '0-9x')
  W=${S%x*}; H=${S#*x}; W=$(num "$W" 1080); H=$(num "$H" 2400)
  X1=$((W * 40 / 100)); X2=$((W * 60 / 100)); Y=$((H * 50 / 100))
  echo "display ${W}x${H} swipe ${X1}<->${X2} at y=${Y}" >> "$EV/swipes.txt"
  end=$(( $(date +%s) + $1 ))
  while [ "$n" -lt "$1" ] && [ "$(date +%s)" -lt "$end" ]; do
    if [ $((n % 2)) -eq 0 ]; then adb -s "$HU" shell input swipe "$X1" "$Y" "$X2" "$Y" 300; else adb -s "$HU" shell input swipe "$X2" "$Y" "$X1" "$Y" 300; fi
    n=$((n + 1)); sleep 0.4
  done
  echo "swipes=$n" >> "$EV/swipes.txt"
}

# one device's power and thermal state
fc_state() {  # fc_state SERIAL OUT
  { echo "low_power=$(adb -s "$1" shell settings get global low_power | tr -d '\r')"
    adb -s "$1" shell dumpsys battery | grep -aE '^ *(level|AC powered|USB powered|temperature):'
    adb -s "$1" shell dumpsys thermalservice | grep -a -m1 'Thermal Status'   # system
    adb -s "$1" shell dumpsys package "$GH" | grep -a -m1 versionName
  } > "$2"
}

port_up() { num "$(adb -s "$HU" shell cat /proc/net/tcp6 /proc/net/tcp 2>/dev/null | grep -ci ':149D')"; }
shot() { adb -s "$HU" shell screencap -p /sdcard/fc.png; adb -s "$HU" pull /sdcard/fc.png "$EV/$1-screen.png" >/dev/null; }

# the stop rule: a run after two INCONCLUSIVE runs in a row does not run, and neither does any run after it
stopped_by_rule() {
  local prev=() r
  [ -s "$EV/stop-rule.txt" ] && { echo "$RUN" >> "$EV/stop-rule.txt"; return 0; }
  for r in $RUNS_ORDER; do [ "$r" = "$RUN" ] && break; prev+=("$r"); done
  [ "${#prev[@]}" -ge 2 ] || return 1
  for r in "${prev[@]: -2}"; do grep -aq '"verdict": "INCONCLUSIVE"' "$EV/$r.grade.json" 2>/dev/null || return 1; done   # not-literal
  echo "$RUN" >> "$EV/stop-rule.txt"
}

# the idle window (50 s, dump at its end), the pan window (60 s, dump at 30 s), a 15 s tail
windows() {  # windows TAG DUMP_SERIAL
  local pan
  mark "$1-idle-start"; rl_sleep 50
  fc_dump "$2" "$EV/dumps/$1-idle"; mark "$1-idle-end"
  mark "$1-pan-start"
  rl_bg "pan:$1" fc_pan 60; pan=$RL_PID
  rl_sleep 30; fc_dump "$2" "$EV/dumps/$1-pan"
  rl_step "pan $1 ends"; wait "$pan"; rl_stop "pan:$1"; mark "$1-pan-end"
  rl_sleep 15; mark "$1-end"
}

native_session() {  # native_session TAG MAKE RES: 0 ok, 8 keys, 9 no session, 10 no video
  local T=$1
  rl_step "$T keys"
  rl_radio "$PH" bt off
  fc_keys "$2" "$3" true "$EV/$T-keys.txt" || return 8
  fc_state "$PH" "$EV/$T-moto-state-before.txt"
  adb -s "$HU" logcat -G 16M; adb -s "$PH" logcat -G 16M
  adb -s "$HU" logcat -c; adb -s "$PH" logcat -c
  rl_cap_start "$T-hu" "$HU"; rl_cap_start "$T-ph" "$PH"
  mark "$T-launch"; launch; rl_sleep 15
  rl_radio "$PH" bt on
  rl_wait_line "$EV/$T-hu.txt" 'SSL handshake complete. Session id' 120 || { mark "$T-end"; session_end "$T"; return 9; }
  rl_wait_line "$EV/$T-hu.txt" 'Throughput over' 60 || { mark "$T-end"; session_end "$T"; return 10; }
  adb -s "$PH" shell am start -n com.google.android.apps.maps/com.google.android.maps.MapsActivity -a android.intent.action.VIEW -d google.navigation:q=6.1738,-75.3364 >/dev/null
  rl_sleep 20; shot "$T"
  windows "$T" "$PH"
  fc_state "$PH" "$EV/$T-moto-state-after.txt"
  session_end "$T"
}

self_session() {  # self_session TAG RES: 0 ok, 8 keys, 9 no session, 10 no video, 13 no :5277
  local T=$1 i
  if [ "$(port_up)" -lt 1 ]; then
    echo "OPERATOR NOW: on D-POCO, turn on Start head unit server in Android Auto's developer settings"
    for i in $(seq 1 60); do rl_sleep 5; [ "$(port_up)" -ge 1 ] && break; done
    [ "$(port_up)" -ge 1 ] || return 13
  fi
  rl_step "$T keys"
  fc_keys Google "$2" false "$EV/$T-keys.txt" || return 8
  fc_state "$HU" "$EV/$T-poco-state-before.txt"
  adb -s "$HU" logcat -G 16M; adb -s "$HU" logcat -c
  rl_cap_start "$T-hu" "$HU"
  mark "$T-launch"
  send ACTION_START_SELF_MODE >/dev/null
  rl_wait_line "$EV/$T-hu.txt" 'SSL handshake complete. Session id' 60 || { mark "$T-end"; session_end "$T"; return 9; }
  rl_wait_line "$EV/$T-hu.txt" 'Throughput over' 60 || { mark "$T-end"; session_end "$T"; return 10; }
  rl_sleep 20; shot "$T"
  windows "$T" "$HU"
  fc_state "$HU" "$EV/$T-poco-state-after.txt"
  session_end "$T"
}

session_end() { rl_cap_stop "$1-hu"; rl_cap_stop "$1-ph"; huexit; sleep 2; }

# a session is discarded (and tried once more) on a self wake, a second session or a garbage header
discard_reason() {
  local f="$EV/$1-hu.txt" r=""
  [ "$(num "$(grep -acF 'MATCH! Starting AapService' "$f" 2>/dev/null)")" -ge 1 ] && r="${r}self-wake "
  [ "$(num "$(grep -acF 'SSL handshake complete. Session id' "$f" 2>/dev/null)")" -ge 2 ] && r="${r}second-session "
  [ "$(num "$(grep -acF 'AapRead: Magic Garbage detected in header' "$f" 2>/dev/null)")" -ge 1 ] && r="${r}garbage "
  echo "$r"
}

# a frame start is flags 11 or 9; the two partial seconds at the window edges are dropped
med() { sort -n | awk '{a[NR]=$1} END{if(NR==0){print "none"} else {print a[int((NR+1)/2)]}}'; }
starts() { grep -aP 'RECV: VIDEO.*flags: (11|9) ' | awk '{print substr($2,1,8)}' | uniq -c | awk '{a[NR]=$1} END{for(i=2;i<NR;i++) print a[i]}' | med; }   # not-literal

# the extract: one key=value per line, from the capture of the attempt that counts
fc_extract() {  # fc_extract TAG
  local T=$1 w f="$EV/$1-hu.txt"
  for w in idle pan; do
    rl_step "extract $T $w"
    rl_win "$f" "$T-$w-start" "$T-$w-end" > "$EV/$T-$w.win"
    echo "${w}_starts_median=$(starts < "$EV/$T-$w.win")"
    echo "${w}_rendered_median=$(grep -aF 'Throughput over' "$EV/$T-$w.win" | grep -aoP 'rendered=\d+ \(\K\d+' | med)"   # not-literal
    echo "${w}_fed_median=$(grep -aF 'Throughput over' "$EV/$T-$w.win" | grep -aoP 'fed=\d+ \(\K\d+' | med)"   # not-literal
    echo "${w}_skipped_sum=$(grep -aF 'Throughput over' "$EV/$T-$w.win" | grep -aoP 'skipped=\K\d+' | awk '{s+=$1} END{print s+0}')"   # not-literal
    echo "${w}_rate_changes_hu=$(grep -acF 'Frame rate change.' "$EV/$T-$w.win")"   # phone
    [ -f "$EV/$T-ph.txt" ] && echo "${w}_rate_changes_ph=$(rl_cnt "$EV/$T-ph.txt" "$T-$w-start" "$T-$w-end" 'Frame rate change.')"   # phone
    [ -f "$EV/$T-ph.txt" ] && rl_win "$EV/$T-ph.txt" "$T-$w-start" "$T-$w-end" | grep -aF 'Frame rate change.' | head -3 | sed "s/^/${w}_rate_change_line=/"   # phone
    rm -f "$EV/$T-$w.win"
  done
  rl_step "extract $T session"
  rl_win "$f" "$T-launch" "$T-end" > "$EV/$T-all.win"
  echo "announced=$(grep -aF '[ServiceDiscovery]' "$EV/$T-all.win" | grep -aoE 'target=[0-9]+x[0-9]+@[0-9]+' | head -1)"   # not-literal
  echo "narrowband_lines=$(grep -acF 'AAC audio announced by the 2.4 GHz cap' "$EV/$T-all.win")"
  echo "sink=$(grep -aoE 'Media Sink Setup Request: [0-9]+ on channel VIDEO' "$EV/$T-all.win" | head -1)"
  echo "start=$(grep -aoE 'Media Start Request VIDEO: session=[0-9]+, config_index=[0-9]+' "$EV/$T-all.win" | head -1)"   # not-literal
  echo "group=$(grep -aoE 'Freq: [0-9]+ MHz' "$EV/$T-all.win" | sort -u | tr '\n' ' ')"
  echo "ssl=$(grep -acF 'SSL handshake complete. Session id' "$EV/$T-all.win")"
  echo "throughput_windows=$(grep -acF 'Throughput over' "$EV/$T-all.win")"
  rm -f "$EV/$T-all.win"
  for w in idle pan; do echo "dump_$w=$( [ -s "$EV/dumps/$T-$w-fr.txt" ] && tr '\n' '|' < "$EV/dumps/$T-$w-fr.txt" | cut -c1-600 || echo absent)"; done
}

# one run: a second try after exit 9 or 10 or a discard, then the extract of the try that counts
run_session() {  # run_session native|self MAKE RES
  local T rc=0 why
  : > "$EV/$RUN-attempts.txt"
  for T in "$RUN" "$RUN-b"; do
    if [ "$1" = native ]; then native_session "$T" "$2" "$3"; else self_session "$T" "$3"; fi
    rc=$?; why=$(discard_reason "$T")
    echo "$T exit=$rc discard=${why:-none}" >> "$EV/$RUN-attempts.txt"
    case $rc in
      0) [ -z "$why" ] && break ;;
      9|10) ;;
      *) break ;;
    esac
  done
  echo "$T" > "$EV/$RUN.final"; echo "$rc" > "$EV/$RUN.rc"; echo "${why:-}" > "$EV/$RUN.discard"
  [ -f "$EV/$T-hu.txt" ] && fc_extract "$T" > "$EV/$RUN-extract.txt"
  return 0
}

# the grade of R1 to R5: gates make the run INCONCLUSIVE; two conditions decide PASS
grade_session() {  # grade_session native|self RES
  local x="$EV/$RUN-extract.txt" T rc why gates="" want f any=0 fr
  [ -s "$EV/stop-rule.txt" ] && grep -aqx "$RUN" "$EV/stop-rule.txt" && { rl_verdict NOT_RUN "stop rule: two runs in a row INCONCLUSIVE"; return; }
  T=$(cat "$EV/$RUN.final" 2>/dev/null); rc=$(cat "$EV/$RUN.rc" 2>/dev/null); why=$(cat "$EV/$RUN.discard" 2>/dev/null)
  want=1920x1080@60; [ "$2" = 2 ] && want=1280x720@60
  [ "$rc" = 0 ] || gates="${gates}exit=${rc:-none} "
  [ -z "$why" ] || gates="${gates}discarded twice ($why) "
  [ "$(xv "$x" announced)" = "target=$want" ] || gates="${gates}announced=$(xv "$x" announced) "
  [ "$(xv "$x" narrowband_lines)" = 0 ] || gates="${gates}narrowband_lines=$(xv "$x" narrowband_lines) "
  [ "$(xv "$x" sink)" = "Media Sink Setup Request: 3 on channel VIDEO" ] || gates="${gates}sink=$(xv "$x" sink) "
  if [ "$1" = native ]; then
    fr=$(xv "$x" group)
    { [ -n "$fr" ] && ! echo "$fr" | grep -qE 'Freq: ([0-3][0-9]{3}|[0-9]{1,3}) MHz'; } || gates="${gates}group=$fr "
  fi
  for f in idle pan; do grep -aqF 'FrameRateLimitManager' "$EV/dumps/$T-$f-fr.txt" 2>/dev/null && any=1; done   # system
  if [ "$any" = 1 ]; then
    for f in idle pan; do
      rl_cond "dump-$f" eq "$(grep -aqF 'FrameRateLimitManager' "$EV/dumps/$T-$f-fr.txt" 2>/dev/null && echo 1 || echo 0)" 1 "the $f dump holds FrameRateLimitManager"   # system
    done
  else
    echo "no dump of $T holds FrameRateLimitManager: the dump condition is unobservable, graded on frame counts"
  fi
  rl_cond pan-frames ge "$(num "$(xv "$x" pan_starts_median)" -1)" 0 "pan frame_starts_per_s_median is a number"
  [ -z "$gates" ] || rl_verdict INCONCLUSIVE "gate: $gates"
}
```

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

Every run of R1 to R5 does one session, and one more try when the first ends in exit 9 or 10 or is discarded
(`MATCH! Starting AapService`, a second `SSL handshake complete. Session id`, or `AapRead: Magic Garbage
detected in header` in the head unit capture). It then writes `$EV/<run>-extract.txt` from the try that
counts. Exit codes: 0 ok, 8 keys, 9 no session, 10 no video, 12 host hot, 13 no `:5277`.

**Gates** (every run; a failed gate makes the run INCONCLUSIVE, with the reason):

- exit code 0, and the try that counts was not discarded;
- `announced=target=<w>x<h>@60`, with the run's resolution, and `narrowband_lines=0`;
- `sink=Media Sink Setup Request: 3 on channel VIDEO`;
- R1, R2 and R5 (native) only: `group=` shows only frequencies above 4000 MHz.

**PASS, every condition** (the same for R1 to R5):

1. **Dump, both windows:** `<run>-idle-fr.txt` and `<run>-pan-fr.txt` each hold a `FrameRateLimitManager` line.
   If neither does, the condition is unobservable and the grade says so.
2. **Head unit, pan:** `pan_starts_median` is a number (the pan window carried video).

A run is a measurement, so its PASS only says the instruments worked. The host reports from the extract and
the dumps, verbatim:

- each dump's `limit:` and `max:` values, every `fps:` line under `Current restrictions`, any `Fixed frame rate` line, any `StaticImpl` line, and the `powerState` line;
- `idle` and `pan`: `starts_median`, `fed_median`, `rendered_median`, `skipped_sum`;
- the `Frame rate change.` counts (`_rate_changes_ph` from D-MOTO in R1 and R2, `_rate_changes_hu` from D-POCO in R3 and R4) and the first three lines.

### P. Preparation (once, before R1)

Stops D-HU's app. Saves D-POCO's installed APK and its commit, backs up `settings.xml` and records the values
§4 overwrites. Asks the operator to unlock D-MOTO if it shows a keyguard. Sets `stayon` on both phones, sends
D-POCO home, installs the candidate, records both devices' state and the `:5277` count, and tries the dump
route on D-MOTO with no session (exit 1 is expected there; the check is that it writes a file).

**prep.sh**

```bash
adb -s "$DHU" shell am force-stop "$PKG"
rl_step "save the installed APK"
P=$(adb -s "$HU" shell pm path "$PKG" | head -1 | sed 's/^package://' | tr -d '\r')
adb -s "$HU" pull "$P" "$EV/apk/installed-before.apk" >/dev/null
md5sum "$EV/apk/installed-before.apk" > "$EV/P-installed-before.md5"
send ACTION_QUERY_STATE > "$EV/P-installed-before-query.txt"
adb -s "$HU" shell am force-stop "$PKG"
adb -s "$HU" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-before.xml"
grep -aoE '(vehicle-make|head-unit-make|vehicle-model|fps-limit|resolutionId|video-codec|video-profile-starvation-cap|narrow-band-profile-cap)"[^/]*' \
  "$EV/settings-before.xml" > "$EV/P-recorded-keys.txt"
rl_step "D-MOTO screen"
adb -s "$PH" shell dumpsys window | grep -a mCurrentFocus > "$EV/P-moto-focus.txt"
if grep -aqiE 'Keyguard|Bouncer|NotificationShade' "$EV/P-moto-focus.txt"; then   # system
  echo "OPERATOR NOW: unlock D-MOTO once and leave it on its launcher"
  for i in $(seq 1 60); do
    rl_sleep 5
    adb -s "$PH" shell dumpsys window | grep -a mCurrentFocus > "$EV/P-moto-focus.txt"
    grep -aqiE 'Keyguard|Bouncer|NotificationShade' "$EV/P-moto-focus.txt" || break   # system
  done
fi
adb -s "$PH" shell svc power stayon true
adb -s "$HU" shell input keyevent KEYCODE_HOME
adb -s "$HU" shell svc power stayon true
install_apk cand-947b3ae9 947b3ae9 || exit 1
fc_state "$PH" "$EV/p6-moto-state.txt"; fc_state "$HU" "$EV/p6-poco-state.txt"
echo "port_5277=$(port_up)" > "$EV/P-port.txt"
fc_dump "$PH" "$EV/dumps/p8-moto"; echo "exit=$? lines=$(wc -l < "$EV/dumps/p8-moto-full.txt")" > "$EV/P-dump-route.txt"
exit 0
```

**grade-prep.sh**

```bash
rl_cond 1 ge "$(num "$(stat -c %s "$EV/apk/installed-before.apk" 2>/dev/null)")" 1 "the installed APK was saved"
rl_cond 2 ge "$(num "$(grep -ac '</map>' "$EV/settings-before.xml" 2>/dev/null)")" 1 "settings.xml backed up"
rl_cond 3 eq "$(num "$(grep -aciE 'Keyguard|Bouncer|NotificationShade' "$EV/P-moto-focus.txt" 2>/dev/null)" 1)" 0 "D-MOTO is unlocked"   # system
rl_cond 4 ge "$(num "$(grep -acF '"commit":"947b3ae9' "$EV/P-query.txt" 2>/dev/null)")" 1 "the candidate is installed"   # not-literal
rl_cond 5 ge "$(num "$(sed -n 's/.* lines=//p' "$EV/P-dump-route.txt" 2>/dev/null)")" 1 "the dump route wrote a file"
```

### R1: trip setup, make Google (**a point of the round**)

Native session: D-POCO head unit, D-MOTO phone, 1080p, `native-poke-all-paired` `true`. D-MOTO leaves the
Bluetooth link with `svc` before the launch and comes back 15 s after it. **No `ACTION_START_WIRELESS_SCAN`
after the launch** (template §4 step 4). The script starts navigation on D-MOTO and takes a screenshot for the
host: if it shows no full-screen map, the pan still runs. If the Maps intent opens a chooser on D-MOTO, the
operator picks Maps, "Just once", and says so.

**r1.sh**

```bash
stopped_by_rule && exit 0
rl_thermal_gate || exit 12
run_session native Google 3
```

**grade-r1.sh**

```bash
grade_session native 3
```

### R2: trip setup, make Mazda (the control)

The same as R1 with `vehicle-make` `Mazda`. **Hand step:** the operator watches D-MOTO while R2's session
forms. If it shows a consent or "new car" screen, the operator taps it through. Say what the screen said, and
the time, in Setup notes. If the session ends because of it, the script's second try covers it.

**r2.sh**

```bash
stopped_by_rule && exit 0
rl_thermal_gate || exit 12
run_session native Mazda 3
```

**grade-r2.sh**

```bash
grade_session native 3
```

### R3: Self Mode on D-POCO, 1080p (**a point of the round**)

Self Mode on D-POCO, started by `ACTION_START_SELF_MODE`, `native-poke-all-paired` `false`. Self Mode opens
Android Auto's own home screen. If the screenshot shows no map, the pan still runs on whatever is in the
middle; record what the screenshot shows. Also record both `poco-state` files.

**r3.sh**

```bash
stopped_by_rule && exit 0
rl_thermal_gate || exit 12
run_session self Google 3
```

**grade-r3.sh**

```bash
grade_session self 3
```

### R4: Self Mode on D-POCO, 720p

**r4.sh**

```bash
stopped_by_rule && exit 0
rl_thermal_gate || exit 12
run_session self Google 2
```

**grade-r4.sh**

```bash
grade_session self 2
```

### R5: v.3.4.0 on R1's or R3's setup (conditional)

Runs only if R1 or R3 shows `pan_fed_median` of 50 or more and `pan_rendered_median` below 45. That is: frames
reach us at about 60 and we draw fewer. It installs `v340-013b05bc.apk` (commit `013b05bc`) and repeats the
first run that met the condition, with the same records. If no run met it, R5 grades `NOT_RUN`.

**r5.sh**

```bash
stopped_by_rule && exit 0
src=""
for r in R1 R3; do
  fed=$(num "$(xv "$EV/$r-extract.txt" pan_fed_median)" -1); ren=$(num "$(xv "$EV/$r-extract.txt" pan_rendered_median)" 999)
  if [ "$fed" -ge 50 ] && [ "$ren" -lt 45 ]; then src=$r; break; fi
done
[ -n "$src" ] || { echo "no run met the condition" > "$EV/R5.notneeded"; exit 0; }
echo "$src" > "$EV/R5.source"
rl_thermal_gate || exit 12
install_apk v340-013b05bc 013b05bc || exit 1
if [ "$src" = R1 ]; then run_session native Google 3; else run_session self Google 3; fi
```

**grade-r5.sh**

```bash
if [ -f "$EV/R5.notneeded" ]; then
  rl_verdict NOT_RUN "condition not met: neither R1 nor R3 had pan fed >= 50 with rendered < 45"
else
  ok=$(num "$(grep -acF '"commit":"013b05bc' "$EV/R5-query.txt" 2>/dev/null)")   # not-literal
  rl_cond identity ge "$ok" 1 "v.3.4.0 is installed"
  [ "$ok" -ge 1 ] || exit 0
  if [ "$(cat "$EV/R5.source" 2>/dev/null)" = R1 ]; then grade_session native 3; else grade_session self 3; fi
fi
```

### cleanup. Finish (once, after the last run)

Installs the saved APK and checks its md5 against P. Then puts `settings-before.xml` back with the toolkit's
`restore_settings.sh` and compares it with `cmp`. Clears `stayon` on both phones and switches D-HU's
Bluetooth back on. If R2 ran, D-MOTO now has a `Mazda` car record: leave it, and say so in Setup notes.

**cleanup.sh**

```bash
adb -s "$HU" shell am force-stop "$PKG"
[ -s "$EV/apk/installed-before.apk" ] && adb -s "$HU" install -r -d "$EV/apk/installed-before.apk"
"$RIG_TOOLKIT/apk_identity.sh" "$EV/apk/installed-before.apk" "$HU" > "$EV/cleanup-identity.txt" 2>&1; ident=$?
[ -s "$EV/settings-before.xml" ] || { echo "NO_BACKUP: settings.xml not restored"; exit 1; }
adb -s "$HU" shell am force-stop "$PKG"
adb -s "$HU" push "$EV/settings-before.xml" /data/local/tmp/settings-restore.xml >/dev/null
adb -s "$HU" push "$RIG_TOOLKIT/restore_settings.sh" /data/local/tmp/restore_settings.sh >/dev/null
adb -s "$HU" shell run-as "$PKG" sh /data/local/tmp/restore_settings.sh
adb -s "$HU" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-after.xml"
adb -s "$PH" shell svc power stayon false
adb -s "$HU" shell svc power stayon false
rl_radio "$DHU" bt on
cmp "$EV/settings-before.xml" "$EV/settings-after.xml" && [ "$ident" -eq 0 ]
```

## 8. Do not re-run

Nothing. This is the thread's first round.

## 9. Report back

1. For R1 and R3: each dump's `limit:` and `max:`, and every limiter line under `Current restrictions`, idle and pan. These say what holds 30.
2. For every run: `pan_fed_median` and `pan_rendered_median`. These say whether frames arrive at 60 and whether we draw them.
3. R2's dumps beside R1's: whether the make changes any limiter.
