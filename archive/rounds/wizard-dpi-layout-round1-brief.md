# wizard-dpi-layout, round 1 brief: the announced density and Android Auto's layout

Publish name on `transfer/rig-rounds`: `wizard-dpi-layout-round1-brief.md`. Results go in
`wizard-dpi-layout-round1-results.md`. Evidence goes in a new release `rig-evidence-wizard-dpi-layout`
as the asset `wizard-dpi-layout-round1-captures.zip`.

One head unit (D-HU) and one phone (D-POCO). Two builds: the candidate C and `main` as A. 18 short
Native AA sessions, each with one screenshot of the projection. About 1 h 45 min, builds included.
The runs use verbs only; the operator does nothing by hand.

| Run | Build | Codec | Stored DPI | What it decides |
|---|---|---|---|---|
| R1-171 (x3) | A | 800x480 | 171 | **the point of the round**: the reporter's value gives the reduced layout |
| R1-150A (x1) | A | 800x480 | 150 | the same build, a low density: the full layout comes back |
| R2a (x3) | C | 800x480 | 171, migration not yet run | an upgrader: the migration resets 171 and C sends 150; R1's 150 row |
| R2b (x3) | C | 800x480 | 175 | a typed value: the clamp sends 170; R1's 170 row |
| R1-160 (x3) | C | 800x480 | 160 | R1's 160 row |
| R3A-720, R3A-1080 | A | 1280x720, 1920x1080 | 200, 240 | the layout on `main` at two larger codecs |
| R3C-720, R3C-1080 | C | 1280x720, 1920x1080 | 200, 240 | C sends the same density and draws the same layout |
| R4 (x1) | C | 1920x1080 on a 2.4 GHz group | 240 | the phone takes the 720p fallback, and its dp canvas equals the primary |

## 1. Build and baseline

| Arm | Ref | SHA | `commit` in `ACTION_QUERY_STATE` starts with |
|---|---|---|---|
| **C** (candidate) | `fork/fix/wizard-dpi-layout` | `71e3854dd5f70fd956ee6b84b8cac9e1a52023d0` | `71e3854d` |
| **A** (baseline) | `origin/main` | `73b52b64cbf88beb411f064b014cd4d7ae5b19db` | `73b52b64` |

C is `main` at `73b52b64` plus four commits: `13624092` (the policy and the connect-time clamp),
`cf5b43bf` (the `Density is:` log line and the fallback density), `2195466d` (the one-time migration, which skips portrait units),
`71e3854d` (the DPI settings row). This is the thread's first round. No history was rewritten.

```bash
git fetch fork fix/wizard-dpi-layout && git fetch origin main
git rev-parse fork/fix/wizard-dpi-layout origin/main     # print both full SHAs; record them
git worktree add ../wdl-C fork/fix/wizard-dpi-layout
git worktree add ../wdl-A origin/main
cp local.properties ../wdl-C/ && cp local.properties ../wdl-A/
```

If the first SHA does not start with `71e3854d` or the second with `73b52b64`, stop the round.

### R0. Build and identity gate

Make `hur-wifi-test-scripts/wizard-dpi-layout-round1/` and set `OUT` to it. Copy the helpers first
(section 5), so `th_wait` exists. Build each arm and copy its APK out at once (tooling quirk:
`build_hur.sh` deletes the previous APK). The contract:

```bash
th_wait 70
cd ../wdl-C && ./gradlew :app:assembleGithubDebug --max-workers=2 && mkdir -p $OUT/apk && cp app/build/outputs/apk/github/debug/*.apk $OUT/apk/C.apk
th_wait 70
cd ../wdl-C && ./gradlew :app:testGithubDebugUnitTest --max-workers=2
th_wait 70
cd ../wdl-A && ./gradlew :app:assembleGithubDebug --max-workers=2 && cp app/build/outputs/apk/github/debug/*.apk $OUT/apk/A.apk
md5sum $OUT/apk/C.apk $OUT/apk/A.apk
for s in AnnouncedDensityPolicy LegacyWizardDpiPolicy 'Density is: ' dpi-legacy-wizard-reset; do
  printf '%s C=%s A=%s\n' "$s" "$(unzip -p $OUT/apk/C.apk 'classes*.dex' | strings | grep -cF "$s")" "$(unzip -p $OUT/apk/A.apk 'classes*.dex' | strings | grep -cF "$s")"; done
```

**PASS when all hold:** both builds succeed; the unit tests report 0 failures and 0 errors (record
the count; the review host ran 3209); the two md5s differ; each of the four symbols counts 1 or more
in C and 0 in A. After every install, `state` must show the `commit` of the arm the table names.
**A failure in R0 stops the round.**

## 2. What this is and why

A reporter projects at 800x480 (480p) and gets a reduced Android Auto layout: a vertical rail on the
left, the map, and no media card. The same phone on another head unit app shows the full layout.
The setup wizard had stored a DPI of 171.

- **The cause, in code.** The wizard computes the DPI from the panel diagonal. At 800x480 with the
  6 inch size it gives `sqrt(800^2 + 480^2) / 6.0 * 1.1 = 171`. The app sends the stored DPI to the
  phone unchanged. Android Auto works in dp (`px * 160 / dpi`), so 171 gives a canvas of 748.5x449.1
  dp. Google's head unit guide says the canvas MUST be at least 748x450 dp. A JVM test reproduces the
  171 from the real wizard code.
- **What the phone does with it, from the decompile only.** Gearhead 17.8 keys its system layout on
  dp. It picks the rail shape on a 450 dp height breakpoint, and it shows the map and the dashboard
  cards side by side only when the map region is wider than 710 dp. With an 80 dp rail, an 800 px
  canvas needs a DPI of 162 or less for that. Nobody has measured this on a phone. **R1 measures it.**
- **The fix.** C clamps the announced density at connect so the canvas stays at or above 748x450
  dp, whatever the stored value (`AnnouncedDensityPolicy`, read by `ServiceDiscoveryResponse`).
  Automatic (stored 0) and the wizard now aim at an 853 dp wide canvas, which is 150 at 800 px and
  240 at 1280 px. A one-time migration resets a stored value that the old wizard wrote and that is
  above the floor's maximum (in practice 171 and 186 at 800x480) to Automatic. The 720p fallback
  configuration behind 1080p now scales its density so its dp canvas equals the primary's. A new
  INFO line reports the decision.
- **An earlier operator investigation already saw density move the layout.**
  `dsam-widget-layout-dpi-round1-results.md` (root of this branch): on D-SAM at 1280x720, 175 DPI
  showed an extra weather panel and 198 did not, with nothing else changed. That is a different
  breakpoint at a larger canvas. It shows the phone reads the density; it does not measure 480p.

## 3. What is different about this round

- **The verdicts on layout are read from screenshots by the grading host.** No log line on either
  side reliably names Android Auto's layout. The executor saves two PNGs per session; the Sonnet host
  opens them itself and classifies each one (section 6). The executor does not classify.
- **R1-171 is a reproduction and can fail to reproduce.** If any R1-171 session shows the full
  layout, this phone and Gearhead build do not reproduce the reporter's layout at 171. R1 is then a
  FAIL of the hypothesis. Run every other run anyway: their log conditions still grade, and their
  layout readings become record-only.
- **The pixel shape is forced square in every run** (`pixel-aspect-ratio-e4` 10001; exactly 10000
  means "unset", and 10001 is the measured way to force square). D-HU's panel is wider than 5:3, so
  under FILL the app would otherwise announce a non-square pixel ratio, and the phone honours it.
  Square pixels keep the dp canvas equal to `px * 160 / dpi`, which is what the fix computes.
- **`narrow-band-profile-cap` is off in every run**, so the stored resolution goes on the wire on
  any band. `video-profile-starvation-cap` is deleted in every run.
- **R4 forces a 2.4 GHz group** (`wifi-direct-band` 2) so the phone refuses 1080p by itself and takes
  the fallback. Gearhead logs that refusal. If the phone takes 1080p anyway, R4 is INCONCLUSIVE (not
  exercised).
- **R3 asks for a 5 GHz group** (`wifi-direct-band` 1). If D-HU lands on 2.4 GHz and the phone takes
  the fallback in R3A-1080 or R3C-1080, that run's primary reading is INCONCLUSIVE. In R3C-1080 it
  then also counts as a second R4 reading.
- **R2a seeds the upgrader state directly** (stored 171, `dpi-legacy-wizard-reset` absent) rather
  than installing C over a configured A. The migration reads only the stored keys, so the result is
  the same, and every session starts from the same file.
- **D-HU's `shared_prefs/` can be root-owned** (D-HU quirk). R2a reads back a value the app writes.
  Record `ls -ld /data/data/com.andrerinas.headunitrevived/shared_prefs` once, `chown` it to the
  app's uid:gid if it is wrong, and say what it read first in Setup notes. R2a's verdict rests on the
  log line, not on the file.
- **The rig's audio settings are a deliberate worst case.** Never write `use-aac-audio`,
  `audio-latency-multiplier`, `audio-queue-capacity` or `enable-audio-sink`. Read them back once and
  record them.
- **D-HU is not rotated** (projection quirk). Nothing here needs it.
- **Phone `Critical error` lines at a verb-driven exit are normal.** Count them only between the arm
  and exit markers.
- **Never run adb calls in parallel against one unit.** The background logcat captures are the only
  exception.

## 4. Settings keys

Write with the app stopped, with `hu_put` (root, then `chown` and `chmod 660`) from `ohu_lib.sh`, in
the key syntax below. Take a fresh backup first and state the delta against it in Setup notes.
Restore D-HU from the backup at the end and diff it against the backup.

```bash
adb -s $DHU shell am force-stop $PKG
prefs_cat > $OUT/settings-backup-DHU.xml; BASEXML=$OUT/settings-backup-DHU.xml
```

`$MAC` is D-POCO's Bluetooth address. Take it from D-HU's `dumpsys bluetooth_manager` "Bonded
devices" list and record it.

```bash
BASE="int:wifi-connection-mode=3 int:native-ap-transport=0 int:native-driver-selection-mode=0 bool:native-poke-all-paired=false set:native-poke-bt-macs=$MAC str:last-connected-native-mac=$MAC str:native-preferred-device-mac=$MAC bool:auto-start-self-mode=false bool:auto-connect-last-session=false bool:kill-on-disconnect=false int:onboarding-version=2 del:native-aa-wake-damage-verdict del:video-profile-starvation-cap int:log-level=2 int:video-fit-mode=0 int:pixel-aspect-ratio-e4=10001 str:display-size-preset=PHONE_4_6 bool:narrow-band-profile-cap=false"
K480="$BASE int:resolutionId=1 int:wifi-direct-band=0"
KEYS_R1_171="$K480 int:dpi-pixel-density=171 del:dpi-legacy-wizard-reset"
KEYS_R1_150A="$K480 int:dpi-pixel-density=150 del:dpi-legacy-wizard-reset"
KEYS_R2A="$K480 int:dpi-pixel-density=171 del:dpi-legacy-wizard-reset"
KEYS_R2B="$K480 int:dpi-pixel-density=175 bool:dpi-legacy-wizard-reset=true"
KEYS_R1_160="$K480 int:dpi-pixel-density=160 bool:dpi-legacy-wizard-reset=true"
KEYS_R3_720="$BASE int:resolutionId=2 int:dpi-pixel-density=200 int:wifi-direct-band=1 bool:dpi-legacy-wizard-reset=true"
KEYS_R3_1080="$BASE int:resolutionId=3 int:dpi-pixel-density=240 int:wifi-direct-band=1 bool:dpi-legacy-wizard-reset=true"
KEYS_R4="$BASE int:resolutionId=3 int:dpi-pixel-density=240 int:wifi-direct-band=2 bool:dpi-legacy-wizard-reset=true"
```

What the keys mean:

- `resolutionId`: 1 is 800x480, 2 is 1280x720, 3 is 1920x1080.
- `dpi-pixel-density`: the stored DPI. 0 is Automatic.
- `display-size-preset` `PHONE_4_6` is the 6 inch size, the reporter's case. Only Automatic reads it.
- `dpi-legacy-wizard-reset`: the migration's done flag on C. Absent means the migration runs on the
  first read. A has no such key and ignores it.
- `video-fit-mode` 0 is FILL. With it, D-HU announces margins of 0x0 at every codec in this round.
- `wifi-direct-band`: 0 automatic, 1 ask for 5 GHz, 2 force 2.4 GHz.

`ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` at C or A, so no
`allow-external-configuration` is needed. Every app line this round greps is INFO or WARN, so
`log-level` 2 carries all of them.

## 5. Helpers and verbs

Copy `ohu_lib.sh`, `ohu_setkeys.py` and `ptr_lib.sh` into `$OUT` from
`hur-wifi-test-scripts/projection-teardown-and-relays-round3/` (with the empty-set fix the tooling
quirk asks for), and `p1042_lib.sh` and `flags.py` from
`hur-wifi-test-scripts/pr-1042-disabled-home-buttons-round1/`. From the first three use only
`prefs_cat`, `hu_put`, `apk_check`, `th_wait`, `th_gate` and `th_report`. Source `p1042_lib.sh`, then
save and source `$OUT/wdl_lib.sh` last:

```bash
# wdl_lib.sh : source after ohu_lib.sh, ptr_lib.sh and p1042_lib.sh. Needs OUT and BASEXML.
WANT='name="(dpi-pixel-density|resolutionId|display-size-preset|dpi-legacy-wizard-reset|pixel-aspect-ratio-e4|video-fit-mode|wifi-direct-band|narrow-band-profile-cap|video-profile-starvation-cap|log-level)"'
# sess RUN KEYS : one Native AA session from a cold start, two screenshots, a user exit
sess() { RUN=$1; local K=$2 L
  adb -s $POCO shell cmd connectivity airplane-mode enable; sleep 5
  adb -s $DHU shell am force-stop $PKG; sleep 0.3
  hu_put "$BASEXML" $K
  prefs_cat | grep -aoE "$WANT[^>]*>" > $OUT/$RUN.keys-before
  th_gate; PHCAP=1; cap_start; mark $RUN-start
  adb -s $DHU shell am start -n $MAIN; sleep 15
  mark $RUN-arm; L=$(lines); send ACTION_START_WIRELESS_SCAN > $OUT/$RUN.arm.reply
  adb -s $POCO shell cmd connectivity airplane-mode disable; sleep 5
  adb -s $POCO shell svc wifi enable; sleep 0.3; adb -s $POCO shell svc bluetooth enable; sleep 3
  adb -s $POCO shell input keyevent KEYCODE_HOME; sleep 0.3
  waitline 120 'Throughput over ' "$(HF)" $L; echo "video=$?" | tee $OUT/$RUN.summary
  sleep 20; mark $RUN-shot1; shot layout1; sleep 10; mark $RUN-shot2; shot layout2
  mark $RUN-x-go; send ACTION_DISCONNECT > $OUT/$RUN.x.reply; sleep 8; mark $RUN-x-done
  mark $RUN-end; clean_hu; cap_stop; th_report $RUN; PHCAP=
  prefs_cat | grep -aoE "$WANT[^>]*>" > $OUT/$RUN.keys-after
}
# readout RUN : the numbers each run is graded on, one per line
readout() { RUN=$1; local w="hwin $RUN-arm $RUN-x-go"
  echo "rendered_windows=$($w | rend)"
  echo "ssl=$($w | c 'SSL handshake complete')"
  echo "self_wake=$($w | c 'MATCH! Starting AapService')"
  echo "disconnected_in_session=$($w | c 'AapService: Disconnected.')"
  echo "mode=$($w | grep -a -m1 -oF 'Initializing WiFi Mode: NATIVE')"
  echo "video_sink=$($w | grep -acE 'Media Sink Setup Request: [0-9]+ on channel VIDEO')"
  echo "rescap=$($w | grep -a -m1 -o '\[RES_CAP\] resolutionId=.*')"
  echo "freq=$($w | grep -a -m1 -o 'onGroupInfoAvailable: SSID: .*' | grep -ao 'Freq: [0-9]* MHz')"
  $w | grep -aF -e '[ServiceDiscovery] NegotiatedResolution is: ' -e '[ServiceDiscovery] Margins are: ' \
    -e '[ServiceDiscovery] PixelAspectRatioE4 is: ' -e '[ServiceDiscovery] Density is: ' \
    -e '[ServiceDiscovery] Offering a ' -e 'HeadUnitScreenConfig: Video: the phone chose the ' | sed 's/^/hu: /'
  local p="pwin $RUN-arm $RUN-x-go"
  echo "ph_codec=$($p | c 'The videoCodecType to be used is:')"
  echo "ph_critical=$($p | c 'Critical error')"
  echo "ph_refused_freq=$($p | c 'is not allowed due wireless frequency')"
  echo "ph_bye=$(pwin $RUN-x-go $RUN-x-done | c 'received ByeByeRequest')"
  $p | grep -aF -e 'Will use layout: ' -e 'displayDimensions ' -e 'is not allowed due wireless frequency' | head -6 | sed 's/^/ph: /'
}
```

`sess` follows the clean-run protocol: the phone is in airplane mode while D-HU starts, and it comes
back after the arm. `readout` prints the counts and the decisive lines from the two captures, bounded
by the markers. Save its output as `$OUT/$RUN.readout` and quote it in the results.

**Verbs and commands used, and nothing else acts on the app:**

| Action | Command |
|---|---|
| arm Native AA, as the WiFi button | `send ACTION_START_WIRELESS_SCAN` |
| user exit from the projection | `send ACTION_DISCONNECT` |
| marker | `mark <label>` (also writes `OHURIG` on the phone while `PHCAP=1`) |
| build identity | `state` |
| launch | `adb -s $DHU shell am start -n $MAIN` |
| stop the app | `clean_hu` (only on a running app) |

Every verb must print `AutomationReceiver: ` in the head unit capture. A step with no such line never
landed: that session is void, not a FAIL.

### Precheck, once

Apps stopped. Record every output in Setup notes.

```bash
adb -s $POCO shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
adb -s $DHU shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'       # D-POCO listed; take $MAC
adb -s $POCO shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'      # no other head unit's radio
adb -s $POCO shell dumpsys wifip2p | grep -a -m2 -E 'isGroupOwner|groupFormed'   # isGroupOwner false
adb -s $POCO shell dumpsys window | grep -a -m1 mCurrentFocus                    # launcher, not Settings
adb -s $DHU shell ls -ld /data/data/com.andrerinas.headunitrevived/shared_prefs
adb -s $DHU shell wm size; adb -s $DHU shell wm density                          # D-HU's own panel; record only
prefs_cat | grep -aoE 'name="(use-aac-audio|audio-latency-multiplier|audio-queue-capacity|enable-audio-sink)"[^>]*>'
```

If D-POCO lists another head unit's radio as bonded, ask the operator to forget it on the phone
before the first session (D-POCO quirk). That is the round's only possible operator ask; make it
once, before R1.

## 6. The deciding lines and the layout classes

Every app string below matched `git grep -F` in `app/src/main` at `71e3854d`. All but `Density is:`
also matched at `73b52b64`.

| Line (grep -F) | Source | Level | Used for |
|---|---|---|---|
| `AutomationReceiver: ` | `AutomationReceiver` | INFO | a verb landed |
| `AutomationMarker: ` | `AutomationEffectRunner` | WARN | markers |
| `Initializing WiFi Mode: ` | `WifiLauncherManager` | INFO | preflight: must read `NATIVE` |
| `WifiDirectManager: onGroupInfoAvailable: SSID: ` | `WifiDirectManager` | INFO | the group's `Freq: N MHz` |
| `SSL handshake complete` | `AapSslContext` | INFO | a session formed |
| `[RES_CAP] resolutionId=` | `HeadUnitScreenConfig` | INFO | preflight: `capped=` is the run's codec and `linkCapped=none` |
| `[ServiceDiscovery] NegotiatedResolution is: ` | `ServiceDiscoveryResponse` | INFO | preflight: the run's codec |
| `[ServiceDiscovery] Margins are: ` | `ServiceDiscoveryResponse` | INFO | preflight: `0x0` |
| `[ServiceDiscovery] PixelAspectRatioE4 is: ` | `ServiceDiscoveryResponse` | INFO | preflight: `10001` |
| `[ServiceDiscovery] Density is: ` | `ServiceDiscoveryResponse` | INFO | C only: the announced density and why |
| `[ServiceDiscovery] Offering a ` | `ServiceDiscoveryResponse` | INFO | the fallback and, on C, its density |
| `HeadUnitScreenConfig: Video: the phone chose the ` | `HeadUnitScreenConfig.adoptFallback` | INFO | the phone took the fallback |
| `Media Sink Setup Request: ` | `AapControl` | INFO | regex `Media Sink Setup Request: [0-9]+ on channel VIDEO` |
| `Throughput over ` | `VideoDecoder` | INFO | picture; regex `Throughput over [0-9]+ms: rendered=[1-9]` |
| `MATCH! Starting AapService` | `AutoStartReceiver` | INFO | discard check |
| `AapService: Disconnected.` | `AapService` | INFO | must be 0 inside the session window |

The `Density is:` line is `[ServiceDiscovery] Density is: <announced> (dp canvas <W>x<H>, requested
<stored or automatic>, <reason>)`. Two of the exact texts below are pinned by
`AnnouncedDensityPolicyTest`; the others follow from the same format. Compare the text after
`Density is: ` exactly:

| Run | Expected text after `Density is: ` |
|---|---|
| R2a | `150 (dp canvas 853x512, requested automatic, codec-based)` |
| R2b | `170 (dp canvas 752x451, requested 175, clamped to the 450 dp height floor)` |
| R1-160 | `160 (dp canvas 800x480, requested 160, within the HUIG floor)` |
| R3C-720 | `200 (dp canvas 1024x576, requested 200, within the HUIG floor)` |
| R3C-1080, R4 | `240 (dp canvas 1280x720, requested 240, within the HUIG floor)` |

Gearhead lines, graded on D-POCO's capture. They are not in our source.

| Line (grep -F) | Used for |
|---|---|
| `The videoCodecType to be used is:` | the phone set up video for this session; 1 or more |
| `Critical error` | 0 between the arm and exit markers |
| `received ByeByeRequest` | the session was live on the phone until our exit (tag set VERBOSE by `cap_start`) |
| `is not allowed due wireless frequency` | the phone refused 1080p on a 2.4 GHz link (R4) |
| `Will use layout: ` | record only: the layout resource Gearhead chose, with the dp size and dpi; it may not print on 17.9 |
| `displayDimensions ` | record only: the display size and dpi Gearhead read |

### Layout classes (the grading host reads the PNGs)

The host opens `$OUT/$RUN.layout1.png` and `$OUT/$RUN.layout2.png` and gives each one a class and a
rail position. The class of the session is the class of `layout2`; record both.

| Class | What the projection shows |
|---|---|
| `DASH` | the map plus one or more cards (media, weather, suggestion) in a column or a row beside it |
| `MAP` | the map fills all the space beside the rail; no card |
| `OTHER` | anything else: a dialog, a black or frozen picture, the launcher, an app other than the map |

Rail position: `LEFT`, `RIGHT` or `BOTTOM`. The reporter's photo is `MAP` with the rail `LEFT`.
A session whose `layout2` is `OTHER` is re-run once; a second `OTHER` makes that session
INCONCLUSIVE.

## 7. Runs

Order: precheck, R0, install A, R1-171-1 to -3, R1-150A, R3A-720, R3A-1080, install C, R2a-1 to -3,
R2b-1 to -3, R1-160-1 to -3, R3C-720, R3C-1080, R4. That keeps the installs to two.

Install A: `adb -s $DHU install -r -d $OUT/apk/A.apk`, set `WANT_MD5` to A's md5, `apk_check`,
`state`. Install C the same way with `install -r`. An install can wipe `settings.xml` (tooling
quirk); `sess` rebuilds it from `BASEXML` every time, so nothing is lost.

Each session is `sess <RUN> "$KEYS_..."`, then `readout <RUN> | tee $OUT/<RUN>.readout`. About 3 min
each.

**Every session is valid only when all hold**, read from the readout. Report all of them:

1. `rendered_windows` is 1 or more, and `video_sink` is 1 or more.
2. `ssl` is 1. A 0 is a setup failure. A 2 or more is a discard.
3. `self_wake` is 0 and `disconnected_in_session` is 0. Otherwise discard.
4. `mode` reads `Initializing WiFi Mode: NATIVE`.
5. `rescap` shows `capped=` with the run's codec (`_800x480`, `_1280x720` or `_1920x1080`) and
   `linkCapped=none`. The `NegotiatedResolution is:` line shows the same size. `Margins are: 0x0`.
   `PixelAspectRatioE4 is: 10001`.
6. `keys-before` shows the run's `dpi-pixel-density` and `resolutionId`.
7. Phone: `ph_codec` is 1 or more, and `ph_critical` is 0.

A failed condition 4, 5 or 6 is a setup failure: fix the keys and re-run once. A `ph_critical` of 1
or more is not a setup failure: keep the session, quote the line, and the run it belongs to FAILs.

### R1. The point of the round: does 171 give the reduced layout, and where does it change?

**R1-171-1, -2, -3 (A).** `sess R1-171-1 "$KEYS_R1_171"`, then `-2` and `-3` the same.
**R1-150A (A), the same-build control.** `sess R1-150A "$KEYS_R1_150A"`.

A has no `Density is:` line. On A the stored value goes out unchanged, so `keys-before` is the
density on the wire.

The other rows of R1 come from C's sessions: 150 is R2a, 170 is R2b, 160 is R1-160. Their wire value
is proved by C's `Density is:` line.

**R1 PASS when all hold:**

1. All three R1-171 sessions are `MAP`.
2. R1-150A is `DASH`.
3. All three R2a sessions are `DASH`.

**R1 FAIL** when any R1-171 session is `DASH` (the rig does not reproduce the layout), or when
R1-150A or any R2a session is `MAP` (a low density does not bring the full layout back).

**Record, not graded:** the class and rail position of every R1-160 and R2b session, and of each
R1-171 session. The diagnosis predicts `DASH` at 160 and `MAP` at 170 (the 710 dp map width needs
162 or less at 800 px). Report the lowest density that gave `MAP` and the highest that gave `DASH`.
Quote every `Will use layout: ` and `displayDimensions ` line the phone printed.

**What a PASS would look like if density did nothing:** R1-171 would be `DASH` like R1-150A. The
contrast between the two A runs is the proof; R2a's three sessions add the candidate's own reading.

### R2. The fix on 800x480

**R1-160-1, -2, -3 (C).** `sess R1-160-1 "$KEYS_R1_160"`, then `-2` and `-3`. PASS when each
session's `Density is:` text matches the table in section 6 exactly. Its layout feeds R1.

**R2a-1, -2, -3 (C), an upgrader with the old wizard's 171.** `sess R2a-1 "$KEYS_R2A"`, then `-2`
and `-3`. PASS when all hold, in all three sessions:

1. The `Density is:` text is exactly `150 (dp canvas 853x512, requested automatic, codec-based)`.
   The text `170 (dp canvas 752x451, requested 171, clamped to the 450 dp height floor)` instead
   means the migration did not run and the clamp did the work: that is a FAIL of the migration.
2. The layout is `DASH`.

Record `keys-after` for each session: whether `dpi-pixel-density` reads 0 and
`dpi-legacy-wizard-reset` reads true on disk after the app ran. A file that still reads 171 with no
flag means the app's write did not reach disk (D-HU quirk). That is record only.

**R2b-1, -2, -3 (C), a typed 175.** `sess R2b-1 "$KEYS_R2B"`, then `-2` and `-3`. 175 is not a value
the old wizard wrote, so the migration leaves it and the clamp acts. PASS when, in all three
sessions, the `Density is:` text is exactly
`170 (dp canvas 752x451, requested 175, clamped to the 450 dp height floor)`. Its layout feeds R1.

### R3. No change on larger codecs

**R3A-720 and R3A-1080 (A).** `sess R3A-720 "$KEYS_R3_720"` and `sess R3A-1080 "$KEYS_R3_1080"`.
**R3C-720 and R3C-1080 (C).** The same keys, after C is installed.

PASS when all hold:

1. R3C-720's `Density is:` text is exactly `200 (dp canvas 1024x576, requested 200, within the HUIG floor)`.
2. R3C-1080's `Density is:` text is exactly `240 (dp canvas 1280x720, requested 240, within the HUIG floor)`.
3. R3C-720 has the same class and rail position as R3A-720, and R3C-1080 the same as R3A-1080.

Record each session's `Freq:`. If R3A-1080 or R3C-1080 has a `the phone chose the ` line, condition 3
for 1080p is INCONCLUSIVE for that pair (the phone drew the fallback, and A and C send different
fallback densities by design). Grade R3C-1080 under R4's conditions as well.

### R4. The fallback keeps the dp canvas

**R4 (C).** `sess R4 "$KEYS_R4"`. A forced 2.4 GHz group with 1080p asked for: Gearhead refuses
1080p on its own 2.4 GHz link and takes the 720p fallback.

Expected lines:

- `freq` below 2500 MHz.
- `[ServiceDiscovery] Offering a _1280x720 fallback, margins 0x0, density 160`
- `HeadUnitScreenConfig: Video: the phone chose the _1280x720 fallback (index 1), margins 0x0`

PASS when all hold:

1. The `Density is:` text is exactly `240 (dp canvas 1280x720, requested 240, within the HUIG floor)`.
2. The `Offering a ` line ends in `density 160`. That gives `1280 * 160 / 160 = 1280` dp wide, equal
   to the primary's 1280 dp.
3. The `the phone chose the _1280x720 fallback` line is present, and `ph_refused_freq` is 1 or more.
4. The layout is `DASH`.

**R4 is INCONCLUSIVE (not exercised)** when the phone takes 1080p: no `the phone chose the ` line and
`ph_refused_freq` 0. Do not change anything to force it. If `freq` is 5000 MHz or more, the band key
did not take: that is a setup failure, re-run once.

A 2.4 GHz session on D-HU has dropped about 10 s after SSL before. If `disconnected_in_session` is 1
or more, re-run once. A second drop makes R4 INCONCLUSIVE; quote the drop line.

### Stop rule

- A setup failure (wrong commit, a missing `AutomationReceiver: ` line, a key that did not read back,
  a failed preflight condition) is re-run once. A second setup failure makes that session UNTESTABLE.
- A discarded session is re-run once. A second discard makes it UNTESTABLE.
- Every session runs at most 2 times. No session is repeated to turn a FAIL into a PASS.
- Stop the round after 26 sessions in total, re-runs included. Mark every session not yet run
  UNTESTABLE (budget).
- Host thermal: the template's rule. If the host stays hot for 30 min, stop and mark the rest
  UNTESTABLE (host thermal).

End of round: restore D-HU's `settings.xml` from the backup as root, diff it against the backup, leave
C installed, and put D-POCO back in airplane mode.

## 8. Do not re-run

- The wizard's numbers for every size and codec, the clamp's arithmetic, the migration's value
  table and the fallback density: the JVM tests on C cover them (`AnnouncedDensityPolicyTest`,
  `OnboardingDisplayPolicyTest`, `LegacyWizardDpiPolicyTest`). R0 runs the suite as the gate.
- The DPI settings row and its clamp note (commit `71e3854d`). No verb opens that screen, and its
  values come from the same `decide` the tests cover. Not in this round.
- D-SAM's 1280x720 widget panel at 175 and 198: `dsam-widget-layout-dpi-round1-results.md` measured
  it.
- Portrait canvases. The fix does not clamp them, and D-HU is not rotated.

## 9. Report back

1. **R1:** the class and rail position of each R1-171 session, R1-150A, and each R2a session. This
   decides whether the density is the cause on hardware.
2. **Where the layout changes:** the class of each R2b (170) and R1-160 (160) session, and the
   lowest density that gave `MAP`.
3. **The fix's wire values:** for R2a, R2b, R1-160, R3C-720 and R3C-1080, how many sessions printed
   the exact expected `Density is:` text; for R4, the `Offering a ` line, the `the phone chose the `
   line and `ph_refused_freq`.

Also give D-POCO's Gearhead `versionName`, both APK md5s, the unit test count, the `shared_prefs`
`ls -ld` line, D-HU's `wm size` and `wm density`, each session's `Freq:`, and the seven validity
readings for every session.

## 10. Decisive strings

Every log string the runs grep, one per line. Lines 1 to 16 are ours and each matched `git grep -F`
in `app/src/main` at `71e3854d`. Lines 17 to 22 are Gearhead's.
