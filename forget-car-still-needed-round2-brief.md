# forget-car-still-needed, round 2 brief

> **Revised 2026-10-29: moved onto `rig-lib`.** The round now runs as one `rig_session.py` process from the
> `rig-requires` block below. The arms, the runs, the strings and the verdict rules are the same. The block
> splits each run by arm (R1C, R4C, R1F, R4F), adds a preparation run P and a cleanup run, and puts every
> rep's grading in the grade scripts. Section 3 lists what changed.

> **Revised 2026-10-26: both arms moved again.** F was rebased from `048fd16a` to `6c23c264` on `main`
> `947b3ae9`, with the same patches (clean rebase), and C moved from `e9377ff9` to `947b3ae9`. F and C
> still differ only by the fix. `main` now also carries PRs #1138 and #1139. The queue line named
> `048fd16a` while the branch sat at `842f72e4` since 2026-10-25; this note supersedes both. Rebuild both
> arms and re-read section 1. The dev host ran 4268 tests at F, 0 failures. No run, string or verdict
> rule changed. A copy that still names `048fd16a` or `842f72e4` is stale.

> **Revised 2026-10-22: both arms moved again.** F was rebased from `96304bce` to `048fd16a` on `main`
> `e9377ff9`, with the same patches, and C moved from `28af450d` to `e9377ff9`. F and C still differ
> only by the fix. Re-read section 1 and rebuild both arms. No run, string or verdict rule changed. A copy
> that still names `96304bce` is stale.

> **Revised 2026-10-17: both arms moved.** F was rebased from `25a56033` to `96304bce` on `main`
> `28af450d`, with the same patches, and C moved from `d2d0a6bf` to `28af450d` so that F and C still
> differ only by the fix. Round 1's APKs are neither arm: section 1 now builds both. Re-read section 1
> and the install blocks C and F. No run, string or verdict rule changed. A copy that still names
> `25a56033` is stale.

Published name: `forget-car-still-needed-round2-brief.md`. Results go in
`forget-car-still-needed-round2-results.md`. Evidence: upload `forget-car-still-needed-round2-captures.zip`
to the existing release `rig-evidence-forget-car-still-needed`.

Units: D-HU (head unit) and D-POCO (phone). D-MOTO keeps its Bluetooth off for the round. D-SAM and D-HP take
no part. No hand steps and no injected taps.

Queue line: `forget-car-still-needed-round2-brief.md` (F `6c23c264` on `fork/fix/forget-car-still-needed`,
C `947b3ae9` = `main`; units D-HU, D-POCO and D-MOTO; R1 is the point).

Estimated time: 25 min to build both arms, then the session: P 2 min, R1C 12 to 30 min, R4C 5 min, R1F 8 min,
R4F 8 min, cleanup 1 min. About 1 h 20 min, or 1 h 40 min if the sweep finds a leak late.

**R1 is the point of the round** (R1C is its control half, R1F its fix half). R4 is a regression guard that
round 1 left at FAIL on one rep.

## 0. What the session needs

```rig-requires
lib 19f862828
candidate F 6c23c264931712d51bff48c4d38bd99c090162aa
candidate C 947b3ae9fae9834aa8e4145ee91751ed743c2ae0
unit D-HU role=headunit wifi=on bt=on
unit D-POCO role=phone wifi=on aa=D-HU
unit D-POCO@R1C,R1F bt=off
unit D-POCO@P,R4C,R4F bt=on bond=D-HU
unit D-MOTO bt=off
confirm the host built both arms with get_apks.sh: $EV/apk/F.apk and $EV/apk/C.apk exist
run P cap=300 script=prep.sh grade=grade-prep.sh
run R1C cap=2100 script=r1c.sh grade=grade-r1c.sh point=yes
run R4C cap=1000 script=r4c.sh grade=grade-r4c.sh
run R1F cap=900 script=r1f.sh grade=grade-r1f.sh point=yes
run R4F cap=1200 script=r4f.sh grade=grade-r4f.sh
run cleanup cap=180 script=cleanup.sh always=yes
stop P FAIL,UNTESTABLE skip=all
stop R1C INCONCLUSIVE skip=R1F
```

- D-POCO's Bluetooth is off for R1 (no session, the run grades the group only) and on, bonded to D-HU, for
  P and R4. P reads the bond at the start, so a missing bond stops the session before the first rep, not
  in the middle. `check --fix` switches the radio before each run, so the order needs no operator step.
- D-MOTO's Bluetooth stays off so no poke goes to it. The round's end puts every radio back to `baseline.conf`.
- R1C's grade is PASS when the control leaked at some delay `D`, and INCONCLUSIVE when it did not. That
  INCONCLUSIVE skips R1F, as round 1's rule says: F is not run without a leak on C.

## 1. Build and baseline

Round 1's three commits, rebased onto `main` `947b3ae9` on 2026-10-26. They are the same patches, and C
moved to the new base with them.
History was rewritten three times (2026-10-17, 2026-10-22, 2026-10-26). Round 1's APKs are neither arm, so build both.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| F (fix) | `fork/fix/forget-car-still-needed` | `6c23c264931712d51bff48c4d38bd99c090162aa` | `f90896cc2b7961a41f1601e854d863948bf97259` |
| C (control) | `main` | `947b3ae9fae9834aa8e4145ee91751ed743c2ae0` | `e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165` |

The dev host ran the JVM suite at `6c23c264`: 4268 tests, 0 failures. Do not run the unit tests on the rig.

The host builds both arms in one worktree, F first, before it starts the session. Run it with `EV` set to this
round's evidence dir and `run_in_background`; a build with thermal pauses passes 10 minutes.

**get_apks.sh**

```bash
: "${EV:?set EV to the evidence dir of this round}"
mkdir -p "$EV/apk"
WT=$("$RIG_TOOLKIT/wt-new.sh" forget-car-still-needed scratch 6c23c264 | tail -1)
[ -n "$WT" ] && [ -e "$WT/.git" ] || { echo "NO_WORKTREE"; exit 1; }
git -C "$WT" fetch fork fix/forget-car-still-needed
git -C "$WT" fetch origin main
for arm in F:6c23c264931712d51bff48c4d38bd99c090162aa:f90896cc2b7961a41f1601e854d863948bf97259 \
           C:947b3ae9fae9834aa8e4145ee91751ed743c2ae0:e7112fbfdd820cf4834a8a9a5a47f5b62e3ea165; do
  IFS=: read -r name sha tree <<< "$arm"
  git -C "$WT" checkout --detach "$sha" || exit 1
  [ "$(git -C "$WT" rev-parse 'HEAD^{tree}')" = "$tree" ] || { echo "TREE_MISMATCH $name"; exit 1; }
  "$RIG_TOOLKIT/rig_thermal.sh" "wait" 70
  HUR_DIR="$WT" MAX_WORKERS=2 "$RIG_TOOLKIT/build_hur_cool.sh" || exit 1
  cp "$(ls -t "$RIG_DATA"/apks/*.apk | head -1)" "$EV/apk/$name.apk" || exit 1
done
(cd "$WT" && ./gradlew --stop)
md5sum "$EV/apk/F.apk" "$EV/apk/C.apk"
```

Record both md5s in Setup notes. P grades that they differ, and that only F's DEX holds
`HotspotStartSupersededPolicy`. Every run that installs an arm proves it with `apk_identity.sh` and with the
commit in `ACTION_QUERY_STATE`'s reply (`6c23c264` on F, `947b3ae9` on C). A run whose identity fails stops at
once, and its grade's first condition says so.

## 2. What this is and why

A field unit runs Native AA. Its user switches the Native transport from the head unit's own hotspot to
WiFi Direct. After the switch, no session forms until the user closes the app. In the field log, every
WiFi Direct group create answers `BUSY` within 2 ms. The handshake waits 60 s for credentials and fails.
The next app start logs a framework `stopSoftAp` and then `createGroup SUCCESS` on the first try. So an
access point (AP) of ours was still up after the switch, and it held the radio against the P2P group.

F changes two things that the rig can see:

- **Seam 1.** A hotspot start checks for a stop before each request and on each poll. A start that a stop
  has passed sends no request, or takes its own AP down again. `disableAndAwaitDown` waits up to 1.5 s
  for a start in flight to finish.
- **Seam 2.** When a Native create gets `BUSY` while this unit's AP is up, `WifiDirectManager` takes the AP
  down once and asks for the same group again.

## 3. What round 1 found, and what is different now

Round 1: R1 INCONCLUSIVE, R2 PASS, R3 PASS, R4 FAIL on one rep.

- **R1.** The control (C) did not leak in 5 of 5 reps. Each C rep ended on `The hotspot was switched off
  while this start was still running` with `ap_end=0`. So the stop arrived on C before the AP came up, and
  D-HU dropped the enable by itself. F's guard fired in 5 of 5 reps. Round 1 timed the stop from
  `Setting hotspot enabled=true` (+1.5 s) and from `HotspotManager: Band preference is ` (+1.0 s). Both
  land before or just after the request.
- **The leak needs a later stop.** The stop must reach the framework while D-HU is already starting the AP.
  Then the framework can drop the disable and finish the enable. This round times the stop from the line
  that prints right before the request goes out, and sweeps the delay.
- **The trigger is `SoftApConfigCompat: enableHotspot called (API=`.** It prints at INFO, once per band,
  a few ms before `HotspotManager` posts the start request. Do not use
  `HotspotManager: SoftAp configuration applied; now starting the hotspot.`: D-HU refuses
  `setSoftApConfiguration()` (`rig-quirks/units/D-HU.md`), so that line never prints on D-HU. On D-HU the
  next line is `SoftApConfigCompat: could not configure the access point (` at WARN.
- **The host delay is not the device delay.** The host polls its capture file every 0.5 s (`rl_wait_line`),
  and the switch script adds a few hundred ms. So each rep reports `off_ms`: the device time from the trigger
  to the stop line `HotspotManager: Setting hotspot enabled=false`. Grade the sweep on `off_ms`, not on the
  host delay.
- **D-HU may never leak.** The app's own hotspot start rarely brings D-HU's AP up inside its window
  (`rig-quirks/units/D-HU.md`). If no C rep leaks after the sweep, R1 is INCONCLUSIVE by design. That is
  not a failure.
- **The switch sends `ACTION_START_WIRELESS_SCAN` a few seconds after the launch.** §4 step 4 of the
  template forbids that for arming. Here it is the field user's Save (`setActiveFromSettings(force =
  true)`), and it is what the run measures. Keep it.
- **R4 gets a rig-fault rule.** R4-F-2 in round 1 formed no session because D-POCO's own stack logged
  `RFCOMM_CreateConnection failed` on all 4 of its attempts, and D-HU logged no `NativeAA: Connection
  accepted from`. C-2 showed the same fault (69.2 s). A rep with that pair is now void, not FAIL.
- **R4's phone window starts at the switch marker.** Round 1 started it at the phone marker, so R4-F-1 and
  R4-F-3 read `rfcomm=0` and `tcp=0` on sessions that formed. The phone dials within the 4 s after its radios
  come on, before that marker.
- **Changed by the move to `rig-lib` (2026-10-29).** The pre-flight is the block: radios, the D-POCO bond and
  a phone left as group owner are read back by `check --fix`. The rest of the old pre-flight is run P. Times
  are in ms (`succ_ms`, `ssl_ms`), because a condition compares integers. Two gaps in the old verdict rules
  are closed: R1F with 3 or 4 reached reps, all passing, is INCONCLUSIVE (the old rule named only fewer than
  3), and R4F with fewer than 3 graded reps after its extra reps is INCONCLUSIVE. The settings restore goes in
  as root, not through `restore_settings.sh`: `run-as` on D-HU cannot read `/data/local/tmp`
  (`rig-quirks/units/D-HU.md`).
- **Read `rig-quirks/topics/tooling.md`, `rig-quirks/units/D-HU.md`, `rig-quirks/units/D-POCO.md` and
  `rig-quirks/topics/wifi.md` first.** Thermal limits are the table in `tooling.md`.

## 4. Settings keys

P backs up D-HU's `settings.xml` (`$EV/settings-backup.xml`) and cleanup puts it back and compares it. State
the delta in Setup notes. P records `shared_prefs/` owner and mode. Leave the audio keys as
they are: they are a deliberate worst case. Leave `video-profile-starvation-cap` as it is and record it.
No run grades audio or video.

P records, and no run changes: `native-poke-bt-macs`, `native-poke-all-paired`, `native-driver-selection-mode`,
`native-preferred-device-mac`, `wifi-direct-stable-identity`, `hotspot-band`.

**Base** (every run). `allow-external-configuration` is needed for `ACTION_SET_SETTINGS`.

```xml
<int name="wifi-connection-mode" value="3" />
<int name="log-level" value="2" />
<boolean name="allow-external-configuration" value="true" />
<int name="onboarding-version" value="2" />
<string name="static-bssid">0</string>
<string name="static-p2p-bssid">0</string>
```

**HOT** (hotspot transport at launch; R1 and R4). Also delete `hotspot-teardown-proven-unsafe`.
`hotspot-ssid` MUST be set: with it, D-HU reaches `setSoftApConfiguration()`, refuses it, and posts the
start request.

```xml
<int name="native-ap-transport" value="1" />
<string name="hotspot-interface">wlan2</string>
<string name="hotspot-ssid">OHU-TEST</string>
<string name="hotspot-password">testtest1234</string>
<boolean name="auto-enable-hotspot" value="true" />
```

Log level 2 (INFO) carries every head unit line in section 6.

## 5. Shared commands

`rig_run.sh` sources `lib.sh` before every script and grade. It holds the serials, the verbs, the settings
writes, the rep bodies and the greps. Every adb call runs in sequence, never two at once against one unit.

**lib.sh**

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
HU=27870808938846; PH=4f4027e9
SHA_F=6c23c264; SHA_C=947b3ae9
TRIG="SoftApConfigCompat: enableHotspot called (API="
send() { local a=$1; shift; adb -s "$HU" shell am broadcast -f 0x00000020 -n "$RX" -a "com.andrerinas.openheadunit.$a" "$@"; }
pnow() { adb -s "$PH" shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
marker() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3; echo "$1 $(pnow)" >> "$EV/phone-marks.txt"; }
launch() { adb -s "$HU" shell am start -n "$PKG/com.andrerinas.openheadunit.main.MainActivity" >/dev/null; }
hustop() { adb -s "$HU" shell am force-stop "$PKG"; sleep 1; }
# huexit only while the app runs: an exit on a stopped app starts the service
huexit() { adb -s "$HU" shell am start -a android.intent.action.VIEW -d "headunit://exit" >/dev/null; sleep 5; hustop; }

# the AP on D-HU: apup prints 1 when wlan2 has an IPv4 address, else 0
apup()   { adb -s "$HU" shell ip -4 addr show wlan2 2>&1 | grep -ac 'inet '; }
ap_on()  { adb -s "$HU" shell cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5 >/dev/null 2>&1; sleep 6; apup; }
ap_off() { adb -s "$HU" shell cmd wifi stop-softap >/dev/null 2>&1; sleep 4; apup; }
apval()  { sed -n "s/^$2=//p" "$EV/$1-ap.txt" 2>/dev/null | head -1; }

# settings on D-HU, app stopped. Args: "set TYPE KEY VALUE" or "del x KEY". adbd is root.
hu_kv() {
  local IFS=' ' kv
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
  } > "$EV/fc_kv.sh"
  adb -s "$HU" push "$EV/fc_kv.sh" /data/local/tmp/fc_kv.sh >/dev/null
  adb -s "$HU" shell "sh /data/local/tmp/fc_kv.sh"
}
base() { hu_kv "set int wifi-connection-mode 3" "set int log-level 2" "set boolean allow-external-configuration true" \
  "set int onboarding-version 2" "set string static-bssid 0" "set string static-p2p-bssid 0"; }
hot()  { base; hu_kv "set int native-ap-transport 1" "set string hotspot-interface wlan2" "set string hotspot-ssid OHU-TEST" \
  "set string hotspot-password testtest1234" "set boolean auto-enable-hotspot true" "del x hotspot-teardown-proven-unsafe"; }
hu_read() { adb -s "$HU" shell run-as "$PKG" cat shared_prefs/settings.xml > "$EV/settings-$1.xml"; }

# install one arm unless it is live already, then prove it: apk_identity.sh and the commit in QUERY_STATE
install_arm() {
  local apk="$EV/apk/$1.apk" want=$SHA_C
  [ "$1" = F ] && want=$SHA_F
  rl_step "install $1"
  hustop
  "$RIG_TOOLKIT/apk_identity.sh" "$apk" "$HU" >/dev/null 2>&1 || adb -s "$HU" install -r -d "$apk"
  "$RIG_TOOLKIT/apk_identity.sh" "$apk" "$HU" > "$EV/$RUN-identity.txt" 2>&1 || return 1
  launch; rl_sleep 5
  send ACTION_QUERY_STATE > "$EV/$RUN-query.txt"
  huexit
  grep -aqF "$want" "$EV/$RUN-query.txt"
}
identity_ok() { local want=$SHA_C; [ "$1" = F ] && want=$SHA_F; num "$(grep -acF "$want" "$EV/$RUN-query.txt" 2>/dev/null)"; }

# the switch: a marker, the transport write, the force re-arm (block fc_switch.sh, run on D-HU)
push_switch() { adb -s "$HU" push "$EV/scripts/fc_switch.sh" /data/local/tmp/fc_switch.sh >/dev/null; }
doswitch() { adb -s "$HU" shell sh /data/local/tmp/fc_switch.sh "$1" > "$EV/$1-switch-reply.txt"; sleep 0.3
  echo "$1-switch $(pnow)" >> "$EV/phone-marks.txt"; }
hu_cap() { adb -s "$HU" logcat -c; adb -s "$HU" logcat -G 16M; rl_cap_start "$1-hu" "$HU"; }

# head unit greps over one rep: capture start to the marker "ID-end"
upto() { awk -v e="AutomationMarker: $1-end" '{print} index($0,e){exit}' "$EV/$1-hu.txt" 2>/dev/null; }
ln1()  { upto "$1" | grep -anF -- "$2" | head -1 | cut -d: -f1; }
lnA()  { upto "$1" | awk -v f="${2:-0}" -v s="$3" 'NR>f+0 && index($0,s){print NR; exit}'; }
cntA() { upto "$1" | awk -v f="${2:-0}" -v s="$3" 'NR>f+0 && index($0,s){n++} END{print n+0}'; }
tms()  { upto "$1" | awk -v l="$2" 'NR==l{split($2,t,":"); printf "%.3f\n", t[1]*3600+t[2]*60+t[3]; exit}'; }
gapms() { [ -n "$2" ] && [ -n "$3" ] || { echo none; return; }
  awk -v a="$(tms "$1" "$2")" -v b="$(tms "$1" "$3")" 'BEGIN{printf "%.0f\n", (b-a)*1000}'; }
END_S=("HotspotManager: Hotspot is up" "An access point came up after its band's window had expired"
  "HotspotManager: Every start path was tried on" "HotspotManager: All hotspot attempts failed."
  "The hotspot was switched off while this start was still running"
  "HotspotManager: a stop arrived before this start asked for" "HotspotManager: a stop arrived while this start was running")
lnEnd() { local m= x s; for s in "${END_S[@]}"; do x=$(lnA "$1" "$2" "$s"); [ -n "$x" ] && { [ -z "$m" ] || [ "$x" -lt "$m" ]; } && m=$x; done; echo "$m"; }
# run-time text "createGroup failed (BUSY" is source "createGroup failed (" plus "BUSY (System is busy, retry needed)"
busyA() { echo $(( $(cntA "$1" "$2" "createGroup failed (BUSY") + $(cntA "$1" "$2" "the group create got BUSY while") )); }

# phone greps over one window, on the phone's clock from phone-marks.txt
pt()   { grep -aF "$1 " "$EV/phone-marks.txt" 2>/dev/null | tail -1 | cut -d' ' -f2-; }
pwin() { awk -v a="$(pt "$2")" -v b="$(pt "$3")" 'a!="" && b!="" && substr($0,1,18)>=a && substr($0,1,18)<=b' "$EV/$1-phone.txt" 2>/dev/null; }
pc()   { pwin "$1" "$2" "$3" | grep -acF -- "$4"; }

# one row per rep, key=value; rv ROW KEY reads one value back
rv() { printf '%s\n' $1 | sed -n "s/^$2=//p" | head -1; }
num() { [[ "$1" =~ ^-?[0-9]+$ ]] && echo "$1" || echo "${2:-0}"; }

# R1: one rep, the switch DELAY_MS after the trigger first prints, AP samples at +9 s and +18 s, end at +45 s
r1_rep() {
  local ID=$1 dms=$2
  rl_step "$ID"
  hustop; ap_off > "$EV/$ID-appre.txt"
  hot; hu_read "$ID-pre"
  hu_cap "$ID"
  launch; sleep 1; marker "$ID-start"
  if rl_wait_line "$EV/$ID-hu.txt" "$TRIG" 90 >/dev/null; then
    sleep "$(awk -v m="$dms" 'BEGIN{printf "%.1f", m/1000}')"
    doswitch "$ID"; echo "$ID switched delay_ms=$dms" >> "$EV/races.log"
    rl_sleep 9; echo "ap10=$(apup)" > "$EV/$ID-ap.txt"
    rl_sleep 9; echo "ap20=$(apup)" >> "$EV/$ID-ap.txt"
  else
    echo "$ID NO_TRIGGER" >> "$EV/races.log"
  fi
  rl_sleep 25
  echo "ap_end=$(apup)" >> "$EV/$ID-ap.txt"
  marker "$ID-end"; rl_cap_stop "$ID-hu"; huexit; ap_off >/dev/null
}

r1_row() {  # r1_row ID ARM DELAY_MS
  local ID=$1 Lt Lm Lw= Le= reached=no off guard late busy creates succ cred stillup drain verbs imported
  local ap10 ap20 apend void=no leak=no fpass=no
  Lt=$(ln1 "$ID" "$TRIG"); Lm=$(ln1 "$ID" "AutomationMarker: $ID-switch")
  if [ -n "$Lt" ]; then Lw=$(lnA "$ID" "$Lt" "HotspotManager: Setting hotspot enabled=false"); Le=$(lnEnd "$ID" "$Lt"); fi
  [ -n "$Lt" ] && [ -n "$Lw" ] && { [ -z "$Le" ] || [ "$Lw" -lt "$Le" ]; } && reached=yes
  off=$(gapms "$ID" "$Lt" "$Lw")
  [ -n "$Le" ] && echo "$ID $(upto "$ID" | sed -n "${Le}p" | cut -c1-160)" >> "$EV/R1-ends.txt"
  guard=$(( $(cntA "$ID" "$Lm" "HotspotManager: a stop arrived before this start asked for") + $(cntA "$ID" "$Lm" "HotspotManager: a stop arrived while this start was running") ))
  late=$(( $(cntA "$ID" "$Lm" "HotspotManager: Hotspot is up") + $(cntA "$ID" "$Lm" "An access point came up after its band's window had expired") ))
  busy=$(busyA "$ID" "$Lm"); creates=$(cntA "$ID" "$Lm" "createGroup SUCCESS")
  succ=$(gapms "$ID" "$Lm" "$(lnA "$ID" "$Lm" "createGroup SUCCESS")")
  cred=$(cntA "$ID" 0 "Handshake failed - No WiFi credentials available after")
  stillup=$(cntA "$ID" 0 "HotspotManager: the access point was still up")
  drain=$(cntA "$ID" 0 "HotspotManager: a start was still running")
  verbs=$(( $(cntA "$ID" 0 "AutomationReceiver: com.andrerinas.openheadunit.ACTION_SET_SETTINGS") + $(cntA "$ID" 0 "AutomationReceiver: com.andrerinas.openheadunit.ACTION_START_WIRELESS_SCAN") ))
  imported=$(grep -ac 'imported":1' "$EV/$ID-switch-reply.txt" 2>/dev/null)   # not-literal
  ap10=$(apval "$ID" ap10); ap20=$(apval "$ID" ap20); apend=$(apval "$ID" ap_end)
  { grep -aqF "$ID NO_TRIGGER" "$EV/races.log" 2>/dev/null || [ "$verbs" -lt 2 ] || [ "${imported:-0}" -lt 1 ]; } && void=yes
  [ "$reached" = yes ] && { [ "$late" -ge 1 ] || [ "$ap10" = 1 ] || [ "$ap20" = 1 ] || [ "$apend" = 1 ] || [ "$busy" -ge 1 ] || [ "$cred" -ge 1 ]; } && leak=yes
  [ "$reached" = yes ] && [ "$guard" -ge 1 ] && [ "$late" -eq 0 ] && [ "$ap10" = 0 ] && [ "$ap20" = 0 ] && [ "$apend" = 0 ] \
    && [ "$cred" -eq 0 ] && [ "$(num "$succ" 99999)" -ge 0 ] && [ "$(num "$succ" 99999)" -le 30000 ] && fpass=yes
  echo "id=$ID arm=$2 d=$3 void=$void reached=$reached leak=$leak fpass=$fpass off_ms=$off guard=$guard late_up=$late busy=$busy creates=$creates succ_ms=$succ ap10=${ap10:-none} ap20=${ap20:-none} ap_end=${apend:-none} cred60=$cred stillup=$stillup drain=$drain verbs=$verbs imported=${imported:-0}"
}

r1_count() { num "$(grep -acE " arm=$1 d=$2 .*$3" "$EV/R1-reps.txt" 2>/dev/null)"; }   # r1_count ARM DELAY_MS PATTERN
r1_reps() {  # r1_reps ARM DELAY_MS TARGET MAX PATTERN: reps until TARGET rows match PATTERN, at most MAX rows at that delay
  local n
  while [ "$(r1_count "$1" "$2" "$5")" -lt "$3" ]; do
    n=$(( $(r1_count "$1" "$2" '') + 1 )); [ "$n" -gt "$4" ] && return 1
    r1_rep "R1-$1-d$2-$n" "$2"
    r1_row "R1-$1-d$2-$n" "$1" "$2" >> "$EV/R1-reps.txt"
  done
}

# R4: one rep, the AP settled by hand, the switch, then the phone's radios on and a session over WiFi Direct
r4_rep() {
  local ID=$1
  rl_step "$ID"
  hustop; ap_on > "$EV/$ID-appre.txt"
  hot; hu_read "$ID-pre"
  rl_radio "$PH" bt off
  hu_cap "$ID"; rl_cap_start "$ID-phone" "$PH" -T 1 -b main,system,crash,events
  launch; sleep 1; marker "$ID-start"
  rl_wait_line "$EV/$ID-hu.txt" "NativeAA: ACTIVELY LISTENING on Android Auto UUID" 60 >> "$EV/$ID-waits.txt"
  rl_sleep 5
  doswitch "$ID"
  rl_sleep 15; echo "ap_after_switch=$(apup)" > "$EV/$ID-ap.txt"
  rl_radio "$PH" wifi on; rl_radio "$PH" bt on
  marker "$ID-phone"
  rl_wait_line "$EV/$ID-hu.txt" "SSL handshake complete" 120 >> "$EV/$ID-waits.txt"
  rl_sleep 15; marker "$ID-end"
  rl_cap_stop "$ID-hu"; rl_cap_stop "$ID-phone"; huexit; ap_off >/dev/null
}

r4_row() {  # r4_row ID ARM
  local ID=$1 Lm Lp Lssl= ens auto_on drain busy backstop succ ssl thr=0 accepted adv imported apsw crit rfcomm tcp rfail void=no
  Lm=$(ln1 "$ID" "AutomationMarker: $ID-switch"); Lp=$(ln1 "$ID" "AutomationMarker: $ID-phone")
  [ -n "$Lp" ] && Lssl=$(lnA "$ID" "$Lp" "SSL handshake complete")
  ens=$(cntA "$ID" "$Lm" "ensuring hotspot is disabled first...")
  auto_on=$(cntA "$ID" 0 "trying to switch this device's hotspot on")
  drain=$(cntA "$ID" 0 "HotspotManager: a start was still running")
  busy=$(busyA "$ID" "$Lm"); backstop=$(cntA "$ID" "$Lm" "the group create got BUSY while")
  succ=$(gapms "$ID" "$Lm" "$(lnA "$ID" "$Lm" "createGroup SUCCESS")")
  ssl=$(gapms "$ID" "$Lp" "$Lssl")
  [ -n "$Lssl" ] && thr=$(cntA "$ID" "$Lssl" "Throughput over")
  accepted=$(cntA "$ID" "$Lm" "NativeAA: Connection accepted from")
  adv=$(cntA "$ID" 0 "NativeAA: advertising WPP over TCP at")
  imported=$(grep -ac 'imported":1' "$EV/$ID-switch-reply.txt" 2>/dev/null)   # not-literal
  apsw=$(apval "$ID" ap_after_switch)
  crit=$(pc "$ID" "$ID-switch" "$ID-end" "Critical error")                              # phone
  rfcomm=$(pc "$ID" "$ID-switch" "$ID-end" "Creating rfcomm socket for device:")        # phone
  tcp=$(pc "$ID" "$ID-switch" "$ID-end" "WPP on TCP connected to the WiFi network")      # phone
  rfail=$(pc "$ID" "$ID-switch" "$ID-end" "RFCOMM_CreateConnection failed")             # phone
  { [ "${imported:-0}" -lt 1 ] || [ "$auto_on" -ge 1 ]; } && void=yes
  [ "$void" = no ] && [ "$ssl" = none ] && [ "$rfail" -ge 1 ] && [ "$accepted" -eq 0 ] && void=rig
  echo "id=$ID arm=$2 void=$void ens=$ens auto_on=$auto_on drain=$drain busy=$busy backstop=$backstop succ_ms=$succ ssl_ms=$ssl thr=$thr accepted=$accepted adv=$adv imported=${imported:-0} ap_after_switch=${apsw:-none} crit=$crit rfcomm=$rfcomm tcp=$tcp rfail=$rfail"
}

r4_count() { num "$(grep -acE " arm=$1 .*$2" "$EV/R4-reps.txt" 2>/dev/null)"; }   # r4_count ARM PATTERN
r4_reps() {  # r4_reps ARM TARGET MAX: reps until TARGET graded (void=no) rows, at most MAX rows
  local n
  while [ "$(r4_count "$1" "void=no")" -lt "$2" ]; do
    n=$(( $(r4_count "$1" "") + 1 )); [ "$n" -gt "$3" ] && return 1
    r4_rep "R4-$1-$n"
    r4_row "R4-$1-$n" "$1" >> "$EV/R4-reps.txt"
  done
}

# R4 PASS conditions for one row: KEY OP VALUE, as rl_cond reads them
R4_CONDS=("ens ge 1" "drain eq 0" "busy eq 0" "succ_ms le 30000" "ssl_ms le 120000" "thr ge 1" "adv eq 0" "crit eq 0")
r4_meets() { local v; v=$(rv "$1" "$2"); [[ "$v" =~ ^-?[0-9]+$ ]] && [ "$v" "-$3" "$4" ]; }
```

**fc_switch.sh** (runs on D-HU; `doswitch` passes the rep ID)

```bash
#!/system/bin/sh
R=com.andrerinas.headunitrevived/com.andrerinas.openheadunit.automation.AutomationReceiver
A=com.andrerinas.openheadunit
am broadcast -f 0x00000020 -n $R -a $A.ACTION_LOG_MARKER --es text "$1-switch"
am broadcast -f 0x00000020 -n $R -a $A.ACTION_SET_SETTINGS --es json '{"format":"open-headunit-settings","version":1,"settings":{"native-ap-transport":0}}'
am broadcast -f 0x00000020 -n $R -a $A.ACTION_START_WIRELESS_SCAN
```

## 6. The deciding lines

Each head unit string was checked with `grep -F -r` on `app/src/main` at `6c23c264`, and at `947b3ae9`
where the table says C. All print at INFO or WARN. Lines with a run-time value are listed by their
run-time text here, with the source fragment in the Meaning column. Rechecked on 2026-10-29 against both
candidates with `git grep -F`.

**Head unit capture** (`$EV/<ID>-hu.txt`, capture start to `<ID>-end`):

| Grep (run-time text) | Arms | Meaning |
|---|---|---|
| `SoftApConfigCompat: enableHotspot called (API=` | F, C | R1 trigger: the start is about to post its request |
| `SoftApConfigCompat: could not configure the access point (` | F, C | D-HU refused the configuration; the request follows (record) |
| `Setting hotspot enabled=true` / `Setting hotspot enabled=false` | F, C | a start, and a stop (source `HotspotManager: Setting hotspot enabled=`) |
| `trying to switch this device's hotspot on` | F, C | the hotspot route asks for its own AP |
| `ensuring hotspot is disabled first...` | F, C | the WiFi Direct bring-up stops the hotspot |
| `HotspotManager: Hotspot is up` / `An access point came up after its band's window had expired` | F, C | the start reports an AP up |
| `HotspotManager: Every start path was tried on` / `HotspotManager: All hotspot attempts failed.` | F, C | the start ends with no AP |
| `The hotspot was switched off while this start was still running` | C only | the old stop check |
| `HotspotManager: a stop arrived before this start asked for` | F only | the start sends no request |
| `HotspotManager: a stop arrived while this start was running` | F only | the start takes its own AP down again |
| `HotspotManager: a start was still running` | F only | the 1.5 s drain expired (WARN) |
| `HotspotManager: the access point was still up` | F, C | the stop did not bring the AP down in 8 s (WARN) |
| `WifiDirectManager: Attempting createGroup for Native AA` | F, C | a Native create starts |
| `createGroup SUCCESS` | F, C | the group formed |
| `createGroup failed (BUSY` | F, C | a create got `BUSY` (source `createGroup failed (` plus `BUSY (System is busy, retry needed)`) |
| `the group create got BUSY while` | F only | the backstop takes the AP down |
| `NativeAA: ACTIVELY LISTENING on Android Auto UUID` | F, C | the Bluetooth listeners are open (R4 settle line) |
| `NativeAA: advertising WPP over TCP at` | F, C | an endpoint went out (MUST be 0) |
| `NativeAA: Connection accepted from` | F, C | the phone came back over Bluetooth |
| `SSL handshake complete` | F, C | a session formed (INFO form) |
| `Throughput over` | F, C | video decoded |
| `Handshake failed - No WiFi credentials available after` | F, C | the field symptom's end line |
| `MATCH! Starting AapService` | F, C | a self wake (record) |
| `AutomationReceiver: ` / `AutomationMarker: ` | F, C | a verb or a marker landed; a missing line voids the step |

**Phone capture** (`$EV/<ID>-phone.txt`, phone clock window from `$EV/phone-marks.txt`). P records the
Gearhead version. Round 1 read `18.0.164204-release.daily`. A 0 can mean a renamed string. Then mark the condition "string
not seen", not FAIL.

| Grep | Meaning |
|---|---|
| `Critical error` | Android Auto ended a session on a protocol fault (MUST be 0 in R4) |
| `Creating rfcomm socket for device:` | the phone ran the Bluetooth handshake |
| `WPP on TCP connected to the WiFi network` | the phone reached a stored TCP endpoint |
| `RFCOMM_CreateConnection failed` | the phone's own stack could not open RFCOMM (rig fault, see R4) |

**AP state on D-HU** is `apup` (1 or 0), read at fixed points. It is not a log line.

## 7. Runs

Each run writes its reps as rows (`key=value`) to `$EV/R1-reps.txt` or `$EV/R4-reps.txt`, and the R1 end
lines to `$EV/R1-ends.txt`. The grade scripts read only those rows. A void rep does not count: the run
re-runs it, at most 2 extra reps per arm and delay.

### P. Preparation (no session)

Records D-HU's station, `WRITE_SETTINGS` app-op, `shared_prefs/` owner and mode, the Gearhead version, and
D-POCO's bonded list. Sets D-HU `stayon`. Cycles D-HU's AP off, on, off. Backs up `settings.xml`. Reads the
two APKs' md5s and DEX. Round 1 read the station as `Pegue Cdesta` at 5745 MHz and Gearhead as
`18.0.164204-release.daily`.

**prep.sh**

```bash
rl_step "record state"
adb -s "$HU" shell dumpsys wifi | grep -aiE "mWifiInfo|SSID|Frequency" | head > "$EV/P-station.txt"   # system
adb -s "$HU" shell appops get "$PKG" WRITE_SETTINGS > "$EV/P-appops.txt"
adb -s "$HU" shell svc power stayon true
adb -s "$PH" shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName > "$EV/P-gearhead.txt"
adb -s "$PH" shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices' > "$EV/P-poco-bonds.txt"
hustop
adb -s "$HU" shell stat -c '%U:%G %a' "/data/data/$PKG/shared_prefs" > "$EV/P-prefs-stat.txt"
hu_read backup
grep -aoE '(native-poke-bt-macs|native-poke-all-paired|native-driver-selection-mode|native-preferred-device-mac|wifi-direct-stable-identity|hotspot-band|video-profile-starvation-cap)"[^/]*' \
  "$EV/settings-backup.xml" > "$EV/P-recorded-keys.txt"
rl_step "AP cycle"
{ echo "off1=$(ap_off)"; echo "on=$(ap_on)"; echo "off2=$(ap_off)"; } > "$EV/P-ap.txt"
for a in F C; do md5sum "$EV/apk/$a.apk"; done > "$EV/P-md5.txt" 2>&1
for a in F C; do echo "$a $(unzip -p "$EV/apk/$a.apk" 'classes*.dex' 2>/dev/null | strings | grep -c 'HotspotStartSupersededPolicy')"; done > "$EV/P-dex.txt"
```

**grade-prep.sh**

```bash
rl_cond 1 eq "$(sed -n 's/^off1=//p' "$EV/P-ap.txt")" 0 "D-HU ap_off reads 0"
rl_cond 2 eq "$(sed -n 's/^on=//p' "$EV/P-ap.txt")" 1 "D-HU ap_on reads 1"
rl_cond 3 eq "$(sed -n 's/^off2=//p' "$EV/P-ap.txt")" 0 "D-HU ap_off reads 0 again"
rl_cond 4 ge "$(grep -ac '</map>' "$EV/settings-backup.xml" 2>/dev/null)" 1 "settings.xml backed up"
rl_cond 5 ge "$(sed -n 's/^F //p' "$EV/P-dex.txt")" 1 "F's DEX holds HotspotStartSupersededPolicy"
rl_cond 6 eq "$(sed -n 's/^C //p' "$EV/P-dex.txt")" 0 "C's DEX does not"
rl_cond 7 eq "$(grep -ac '\.apk$' "$EV/P-md5.txt" 2>/dev/null)" 2 "both APKs present"
rl_cond 8 eq "$(awk '{print $1}' "$EV/P-md5.txt" 2>/dev/null | sort -u | wc -l)" 2 "the two md5s differ"
```

### R1C and R1F. The switch lands while D-HU is starting the AP (THE POINT)

Phone capture: no. D-POCO's Bluetooth stays off (the block), so the run grades the group only.

Rep IDs are `R1-<arm>-d<ms>-<n>`, for example `R1-C-d300-2`. Host delays: 0, 300, 600 and 900 ms.

**Stop rule, as the scripts run it.**

1. R1C runs C at d0, d300, d600, d900, until each delay has 2 non-void reps (at most 4 reps there).
2. At the first delay with a C rep that leaked, the sweep stops. That delay is `D` (`$EV/R1-D.txt`).
3. R1C then runs C at `D` until it has 5 reached reps there in all (at most 7 reps at `D`).
4. If no C rep leaks in the sweep, R1C writes `D=none` and grades INCONCLUSIVE. The block's stop rule then
   skips R1F.
5. R1F runs F at `D` until it has 5 reached reps (at most 7 reps).

Per rep:

- **Void:** `NO_TRIGGER` in `races.log` (no trigger in 90 s), or `verbs` below 2, or `imported` = 0.
- **Not reached:** `reached=no`. The start ended (for example `Hotspot is up`) before the stop line. It is
  recorded and does not count toward the 5.
- Do not discard a rep for a second `createGroup SUCCESS`. A late AP that breaks the group is the defect.
- **C rep, leaked** (reached): `late_up` >= 1, or `ap10=1`, or `ap20=1`, or `ap_end=1`, or `busy` >= 1, or
  `cred60` >= 1. The group forms about 1.6 s after the switch, so an AP up 10 s after the switch came up after
  the stop.
- **F rep passes** (reached): `guard` >= 1, `late_up` = 0, `ap10=0`, `ap20=0`, `ap_end=0`, `cred60` = 0, and
  `succ_ms` <= 30000. Any miss is a FAIL of that rep.

**R1 verdict.** R1C PASS means the control leaked at `D`. R1F carries R1's verdict:

- PASS: C leaked in at least 1 reached rep at `D`, F has 5 reached reps at `D`, and every reached F rep passes.
- FAIL: any reached F rep fails.
- INCONCLUSIVE: no C rep leaked in the sweep (R1C). The reason reads "the leak does not reproduce on D-HU at
  `off_ms` from N to M ms; F not run", where N and M are the smallest and largest `off_ms` of the reached C
  reps. Also INCONCLUSIVE: F has fewer than 5 reached reps after its extra reps, with none failing.

What a PASS would look like if F did nothing: `ap_end=0` and a fast `succ_ms` can both come from D-HU's
platform stopping the AP for P2P. So `guard` >= 1 is required, and C's leak at the same delay is what proves
the window was the field one.

**r1c.sh**

```bash
rl_thermal_gate || exit 12
install_arm C || exit 1
push_switch
: > "$EV/R1-reps.txt"; : > "$EV/R1-ends.txt"; : > "$EV/races.log"
D=none
for d in 0 300 600 900; do
  r1_reps C "$d" 2 4 'void=no'
  if [ "$(r1_count C "$d" 'void=no reached=yes leak=yes')" -ge 1 ]; then D=$d; break; fi
done
echo "$D" > "$EV/R1-D.txt"
[ "$D" = none ] || r1_reps C "$D" 5 7 'void=no reached=yes'
exit 0
```

**grade-r1c.sh**

```bash
rl_cond 1 ge "$(identity_ok C)" 1 "C is the installed build"
[ "$(identity_ok C)" -ge 1 ] || exit 0
D=$(cat "$EV/R1-D.txt" 2>/dev/null)
if [ "${D:-none}" = none ]; then
  grep -aE ' arm=C .*void=no reached=yes' "$EV/R1-reps.txt" > "$EV/R1-C-reached.txt" 2>/dev/null   # not-literal
  rng=$(sed -n 's/.* off_ms=\(-\{0,1\}[0-9]*\) .*/\1/p' "$EV/R1-C-reached.txt" | sort -n | awk 'NR==1{a=$1} {b=$1} END{print (NR ? a " to " b : "none")}')
  rl_verdict INCONCLUSIVE "the leak does not reproduce on D-HU at off_ms from $rng ms; F not run ($(num "$(grep -acE ' arm=C .*void=no' "$EV/R1-reps.txt" 2>/dev/null)") non-void C reps)"
else
  rl_cond 2 ge "$(r1_count C "$D" 'void=no reached=yes leak=yes')" 1 "C leaked at D=$D ms"
fi
```

**r1f.sh**

```bash
rl_thermal_gate || exit 12
D=$(cat "$EV/R1-D.txt" 2>/dev/null)
[ -n "$D" ] && [ "$D" != none ] || { echo "NO_D: R1C found no leak"; exit 1; }
install_arm F || exit 1
push_switch
r1_reps F "$D" 5 7 'void=no reached=yes'
exit 0
```

**grade-r1f.sh**

```bash
rl_cond 1 ge "$(identity_ok F)" 1 "F is the installed build"
[ "$(identity_ok F)" -ge 1 ] || exit 0
D=$(cat "$EV/R1-D.txt" 2>/dev/null); D=${D:-none}
rl_cond 2 ge "$(r1_count C "$D" 'void=no reached=yes leak=yes')" 1 "C leaked at D=$D ms"
reached=$(r1_count F "$D" 'void=no reached=yes')
failed=$(r1_count F "$D" 'void=no reached=yes leak=[a-z]+ fpass=no')
i=3
while read -r row; do
  rl_cond "$i" eq "$([ "$(rv "$row" fpass)" = yes ] && echo 1 || echo 0)" 1 "$(rv "$row" id) passes (guard>=1, late_up=0, ap10/ap20/ap_end=0, cred60=0, succ_ms<=30000)"
  i=$((i + 1))
done < <(grep -aE " arm=F d=$D .*void=no reached=yes" "$EV/R1-reps.txt" 2>/dev/null)
[ "$failed" -eq 0 ] && [ "$reached" -lt 5 ] && rl_verdict INCONCLUSIVE "F has $reached reached reps at D=$D ms, 5 needed"
exit 0
```

### R4C and R4F. Regression: a switch with the AP settled, then a session over WiFi Direct

Phone capture: yes. Rep IDs are `R4-<arm>-<n>`. C: 2 graded reps (at most 4). F: 3 graded reps (at most 5).
The phone goes to its home screen first. If the listener line times out, the rep goes on: the switch still runs.

- **Void:** `imported` = 0, or `auto_on` >= 1 (the shell AP was not up, so the AP was not settled).
- **Void, rig fault** (`void=rig`): `ssl_ms` is `none`, `rfail` >= 1 and `accepted` = 0. The phone's stack
  could not open RFCOMM and the head unit never saw a dial.
- **PASS** (F, every graded rep): `ens` >= 1, `drain` = 0, `busy` = 0, `succ_ms` <= 30000, `ssl_ms` <= 120000,
  `thr` >= 1, `adv` = 0, `crit` = 0, and `rfcomm` + `tcp` >= 1. `busy` >= 1 with `backstop` = 0 and
  `ap_after_switch=1` on F is a FAIL of seam 2.
- **FAIL** (F): any PASS condition missed in a graded rep. When a graded C rep misses the same condition, the
  label says `(C misses it too)`.
- If `rfcomm` + `tcp` = 0 on a rep with a session, the string may have moved. The grade then prints
  "string not seen" for it and grades the rest.
- R4C is the control. Its grade asks only for 2 graded reps; its rows are the comparison for R4F.

**r4c.sh**

```bash
rl_thermal_gate || exit 12
install_arm C || exit 1
push_switch
adb -s "$PH" shell input keyevent KEYCODE_HOME
r4_reps C 2 4
exit 0
```

**grade-r4c.sh**

```bash
rl_cond 1 ge "$(identity_ok C)" 1 "C is the installed build"
rl_cond 2 ge "$(r4_count C 'void=no')" 2 "C graded reps"
```

**r4f.sh**

```bash
rl_thermal_gate || exit 12
install_arm F || exit 1
push_switch
adb -s "$PH" shell input keyevent KEYCODE_HOME
r4_reps F 3 5
exit 0
```

**grade-r4f.sh**

```bash
rl_cond 1 ge "$(identity_ok F)" 1 "F is the installed build"
[ "$(identity_ok F)" -ge 1 ] || exit 0
crows=$(grep -a ' arm=C void=no' "$EV/R4-reps.txt" 2>/dev/null)   # not-literal
graded=0; failed=0
while read -r row; do
  id=$(rv "$row" id); graded=$((graded + 1))
  for c in "${R4_CONDS[@]}"; do
    set -- $c; also=""
    while read -r cr; do [ -n "$cr" ] && ! r4_meets "$cr" "$1" "$2" "$3" && also=" (C misses it too)"; done <<< "$crows"
    r4_meets "$row" "$1" "$2" "$3" || failed=$((failed + 1))
    rl_cond "$id-$1" "$2" "$(rv "$row" "$1")" "$3" "$id $1$also"
  done
  rt=$(( $(num "$(rv "$row" rfcomm)") + $(num "$(rv "$row" tcp)") ))
  if [ "$rt" -eq 0 ] && [ "$(rv "$row" ssl_ms)" != none ]; then echo "$id rfcomm+tcp: string not seen"
  else rl_cond "$id-dial" ge "$rt" 1 "$id rfcomm+tcp"; [ "$rt" -ge 1 ] || failed=$((failed + 1)); fi
done < <(grep -a ' arm=F void=no' "$EV/R4-reps.txt" 2>/dev/null)   # not-literal
grep -a ' arm=F void=rig' "$EV/R4-reps.txt" 2>/dev/null | sed 's/^/rig-void: /'   # not-literal
[ "$failed" -eq 0 ] && [ "$graded" -lt 3 ] && rl_verdict INCONCLUSIVE "F has $graded graded reps, 3 needed"
exit 0
```

### cleanup. End of round

Stops the app, takes the AP down, puts `settings-backup.xml` back as root, reads it back (MUST match), and
clears `stayon`. It refuses to restore an empty backup. Radios go back to `baseline.conf` after the session.

**cleanup.sh**

```bash
hustop
echo "ap_end=$(ap_off)" > "$EV/cleanup-ap.txt"
[ -s "$EV/settings-backup.xml" ] || { echo "NO_BACKUP: settings.xml not restored"; exit 1; }
PREFS=/data/data/$PKG/shared_prefs/settings.xml
adb -s "$HU" push "$EV/settings-backup.xml" /data/local/tmp/fc-restore.xml >/dev/null
adb -s "$HU" shell "cp /data/local/tmp/fc-restore.xml $PREFS && chown \$(stat -c %u:%g /data/data/$PKG) $PREFS && chmod 660 $PREFS"
hu_read end
adb -s "$HU" shell svc power stayon false
cmp "$EV/settings-backup.xml" "$EV/settings-end.xml"
```

## 8. Do not re-run

- R2, the BUSY backstop: PASS in round 1 (variant B, 3 of 3, `createGroup SUCCESS` 0.2 to 0.3 s after the
  backstop line).
- R3, a user exit from a hotspot session: PASS in round 1. `hotspot-teardown-proven-unsafe` is written on
  both arms because D-HU's restart never comes back.
- R1 with the stop before the request: F's guard fired 5 of 5 in round 1, on both early windows.
- The status pill on the external module route. No rig unit has the module. Unit tests decide it.
- Whether D-POCO dials a stored WPP-over-TCP endpoint after a transport switch. It is parked.

## 9. Report back

1. R1: one row per rep from `$EV/R1-reps.txt` (`off_ms`, `reached`, `guard`, `late_up`, `ap10`, `ap20`,
   `ap_end`, `busy`, `succ_ms`) with its line from `$EV/R1-ends.txt`. Then `D`, the count of C reps that
   leaked, and the count of F reps that passed. If no C rep leaked, the `off_ms` range of the reached C reps.
2. R4: `ssl_ms` per graded rep on both arms, and every void rep with its `rfail` and `accepted` counts.
3. Any rep on either arm with `cred60` >= 1 or `busy` >= 1: its ID and the line numbers of the first `BUSY`
   and of `createGroup SUCCESS`.
