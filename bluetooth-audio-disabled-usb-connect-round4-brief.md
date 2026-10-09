# bluetooth-audio-disabled-usb-connect, round 4 brief

Published name on the transfer branch: `bluetooth-audio-disabled-usb-connect-round4-brief.md`.

This is a **measurement round** on the same probe build as rounds 1 to 3. No fix is on trial. Rounds 1 to 3 measured the phone's stored A2DP policy. They did not measure what the reporter describes. The reporter says the phone's "Media audio" switch for the head unit is **greyed out**, not only off. This round has two goals:

1. **The greyed switch (Stage R, real address).** Find when D-MOTO greys the switch, how long the grey state lasts, and what clears it. The round reads the switch from D-MOTO's screen with `uiautomator dump`. It also asks the operator to turn the switch on once at chosen points (the hold test), because another ROM may show "greyed" as a switch that snaps back to off.
2. **The skip value end to end (Stage S, `bt-announce=skip`).** Show that the switch stays checked and enabled, that media plays over the cable with no second copy over Bluetooth, what a phone call does, and that the key goes back to `real` cleanly.

**The point of the round is S1 to S3** (the skip arm), with R1 and S4 as the real-address control on each side of it. Goal 1 is a measurement, not a PASS or FAIL on the app.

## 1. Build and baseline

- Candidate: branch `fix/bluetooth-audio-disabled-usb-connect` on the fork, SHA **`fff96699`** (`fff96699480d878bb36bd04078d77260231b19dc`). This is the SHA of rounds 1 to 3. History was not rewritten, and no commit was added.
  ```bash
  git fetch fork fix/bluetooth-audio-disabled-usb-connect
  git -C ../ohu-wt-bad-fff96699 rev-parse HEAD     # must print fff96699480d878bb36bd04078d77260231b19dc
  ```
- **Reuse the round 3 APK.** If `apks/candidate-fff96699.apk` exists and its md5 is `234caaadff2d4731e006591a0c69bb2a`, set `WANT_MD5=234caaadff2d4731e006591a0c69bb2a` and do not build. If it is gone, build it from `../ohu-wt-bad-fff96699` with `build_hur.sh`, run `run_unit_tests.sh` (gate: **2747 tests, 0 failures**; a failure stops the round), copy the APK to `apks/candidate-fff96699.apk` at once, and record its md5 as `WANT_MD5`.
- **Only D-POCO runs the app this round.** If `apk_check` on D-POCO does not match, back up its `settings.xml` first, then `adb -s 4f4027e9 install -r apks/candidate-fff96699.apk`, then diff `settings.xml` against the backup. Do not install on D-HU: its app stays stopped for the whole round.
- `send ACTION_QUERY_STATE` on D-POCO must reply with a `commit` that begins `fff96699`.
- DEX symbol for identity (template §5): `BluetoothAnnouncePolicy`. Check it only if you built a new APK.
- **No baseline APK.** Every comparison is between runs on the candidate.

## 2. What this is and why it exists

**The report.** A user's phone is paired with the head unit over Bluetooth. When Android Auto connects by cable, the phone's Bluetooth settings switch off the head unit's "Media audio" toggle by themselves. The reporter says the toggle is greyed out.

**What rounds 1 to 3 measured** on D-MOTO (Android 14, Android Auto 17.9.664004), from D-MOTO's own Bluetooth log:

| End of the session | Stored A2DP policy for the announced address | Where measured |
|---|---|---|
| Session starts (any transport) | `gearhead:car` sets `0` about 0.7 to 1.0 s after SSL | every run, rounds 1 to 3 |
| Clean end | `gearhead:car` sets `100` about 1.4 to 1.9 s later | USB and Native AA |
| Cable pulled out | `gearhead:car` sets `100` within 460 ms of the disconnect | round 2 U5a2, round 3 U5a4 to U5a9 |
| Android Auto force-stopped | stays `0` | USB and Native AA |
| Next clean session after a force-stop | `gearhead:car` sets `100` at its end | USB and Native AA |
| `bt-announce=blank` or `skip` | no `setConnectionPolicy(` for the address | round 1 U2, U3 |

**What is missing.** Every reading so far is the stored value, plus an operator who said "on" or "off" by eye. No reading recorded whether the switch could be changed. So this round reads the switch itself.

**Why D-POCO announces D-HU's address.** D-POCO is the only USB head unit on the rig (D-HU cannot host USB). D-POCO is a phone, so D-MOTO most likely shows no "Media audio" switch for it. D-HU is a real head unit with an A2DP sink and a hands-free client, and it is bonded to D-MOTO. Android Auto keys the disable on the announced address (round 1 U1 and U4). So D-POCO announces D-HU's address over USB, while D-MOTO stays connected to D-HU over Bluetooth. That is the reporter's setup: a head unit on Bluetooth, then Android Auto by cable. The engineer chose this layout at the plan gate. It is a rig arrangement only, and the closing state puts D-POCO's own key back.

**The skip value.** `SKIP_THIS_BLUETOOTH` is a value that Android Auto knows and handles in its own branch. Round 1 U3 showed that it stops the policy change. If this round shows that the switch also stays usable and calls still work, a user setting that announces the skip value is the candidate fix.

## 3. What is different about this round

**Layout.** One stage layout for the whole round. All three units stay in place.

| Unit | Serial | Role | adb | State for the round |
|---|---|---|---|---|
| D-POCO | `4f4027e9` | USB head unit, the app under test | wireless | `bt-address` = D-HU's address, **Bluetooth off** |
| D-MOTO | `ZY22GC3BM4` | the phone | wireless | Bluetooth on, A2DP and hands-free connected to D-HU; plugged into D-POCO's OTG port only during a session |
| D-HU | `27870808938846` | a plain Bluetooth car unit | PC cable | its app stopped, `wifi-connection-mode=0` |
| other phone | the operator's | rings D-MOTO (H4) | none | |

**Why D-POCO's Bluetooth is off.** D-HU serves one hands-free link at a time (`rig-quirks/units/D-HU.md`). D-POCO is bonded to D-HU, and round 3's preflight showed D-POCO with hands-free and A2DP up. If D-POCO holds D-HU's slot, D-MOTO cannot connect. `pre_rig` checks this before every run.

**Why D-HU's app is stopped.** In Native mode, D-HU's app would poke D-MOTO, raise `ACL_CONNECTED` and start a wireless session in the middle of a reading. Prepare exits the app with `headunit://exit` (never a bare force-stop first: that orphans a P2P group), force-stops it, and writes `wifi-connection-mode=0`. A force-stopped package receives no broadcast. Every run captures D-HU's logcat and checks `dh.wake=0`.

**The cable is out at the start and after `t0` in every run.** The `pre` reading is taken with D-MOTO unplugged. `orun` cues the plug-in before the session. After `t0` the cable is out for the whole read window: a clean end is `ACTION_DISCONNECT` and then the unplug cue, a pull is the pull, and a force-stop is followed by `ACTION_EXIT` and the unplug cue.

**New instruments.**

1. **The switch reading.** `uiautomator dump` of D-MOTO's Settings screen for D-HU, parsed by `ui1008.py` into one line of `toggle.tsv`. The script never touches the screen. Each reading reopens the screen (`ui_open`) so it is not stale, except inside a poll window and a hold test, where the screen stays in front.
2. **The hold test (H9).** The operator turns the switch on once. The script dumps the screen until 15 s after the switch first reads checked, and reads the policy lines. It runs only where the switch reads off.
3. **Media.** VLC plays a file on D-MOTO, started by an intent. D-POCO must log `Media Start Request AUDIO:`. `av` reads whether A2DP is playing to D-HU at the same time (the second copy).
4. **Calls.** The operator rings D-MOTO from another phone (H4). The script answers with `KEYCODE_CALL` and records the call audio route.
5. **A third capture.** D-HU's logcat, for wake lines and its own Bluetooth state.

**Harness fixes from round 3.**

- Verb counts are over the whole run capture, not the marker window. Round 3's window count was one short.
- `usb_up2` searches from a given line, so the second session inside R3b is found.
- The round 3 `cue` with `notify-send` and `spd-say` stays. Every cue reaches the operator directly.

**Expected INCONCLUSIVE, and that is not a failure:**

- The call half of every run, if Stage 0 cannot get a ring to D-MOTO. Then say so once and skip `call`.
- The second-copy check, if Stage 0 finds no dump field that changes when A2DP plays.
- A point read `UNREAD` twice. The run still counts for its other points.

**A finding, not a failure:** D-MOTO never shows a grey state, by look or by hold test, in any R run. Report it with the dumps. It means D-MOTO does not reproduce the reporter's grey state.

## 4. Hand steps, and why no verb exists

The executor prints a line that starts `OPERATOR:`, fires the round 3 `cue` (bell, `notify-send`, `spd-say`), and appends a line to `$OUT/hand-steps.log`. It waits on a log line or a dump where one exists, never on a clock alone. The only touch on any phone screen is H0, H3, H4 (answer, fallback only), H6, H7, H8 and H9. None of them touches the app under test.

| Id | Step | Why no verb |
|---|---|---|
| H0 | Unlock D-MOTO once in Prepare if it is behind a PIN | adb has no way past a PIN (`rig-quirks/units/D-MOTO.md`) |
| H1 | Plug D-MOTO into D-POCO's OTG port at the plug-in cue; unplug it at the unplug cue and leave it out; **re-enumerate** (unplug, 5 s, plug back) at a recovery cue. Each recovery is counted. Two recoveries without a session make the run VOID | A cable is hardware. The re-attach fault (`Found device already in accessory mode`, `Version request send failed`, `Unable to parse TLS packet header`) belongs to another thread and does not void a run |
| H2 | Allow a system USB dialog on D-POCO (`UsbPermissionActivity` or `UsbConfirmActivity`). Do not tick "Always" | A system dialog, not our app |
| H3 | Clear any Android Auto or system screen on D-MOTO that blocks the session, and say what it said | Android Auto's screens have no adb lever |
| H4 | Ring D-MOTO from another phone at the cue. Hang up from **the other phone** 20 s after D-MOTO answers. If the answer cue comes, answer on D-MOTO's screen | A call needs a second phone. The script answers with `KEYCODE_CALL`; the screen answer is the fallback |
| H5 | **Pull D-MOTO's cable out of D-POCO** at the pull cue, and leave it out until the next plug-in cue | It must be a physical pull |
| H6 | Turn Media audio for D-HU back on in D-MOTO's Settings. Only at the H6 cue, which comes only from `prechk` or the close, never inside a read window | No public API sets another app's A2DP policy |
| H7 | Pair D-MOTO and D-HU, only if Prepare finds them not bonded | Pairing needs a confirmation on both screens |
| H8 | Open D-MOTO's Settings **from its launcher icon**, then Connected devices, then D-HU's entry. Then do not touch the phone | Only if no scripted route opens that screen (Stage 0) |
| H9 | **The hold test.** Turn D-MOTO's Media audio switch for D-HU on, once. Then do not touch the phone. Say what you saw: it moved, it flicked back, or it did not move | This is the measurement. It is on D-MOTO's Settings, not on the app |

Before Stage R, the host tells the operator in one message: "Cues arrive as a popup and a voice. A PULL cue means pull the cable from D-POCO and leave it out. A HOLD cue means turn Media audio on for Navegadortz2 once and then do not touch the phone. Never touch D-MOTO's Settings without a cue." Round 3 lost N4a2 to an uncued touch.

## 5. Settings keys

Write the keys with the app stopped, after a backup (template §1). D-POCO is not rooted: use `pocoput`. D-HU: use `hu_put` (`adb shell` is root there). Read every key back before each launch. Record the rig audio keys (`use-aac-audio`, `audio-latency-multiplier`, `audio-queue-capacity`) from both backups as found. Do not write them. They are a deliberate worst case.

| Key | Type | D-POCO | D-HU |
|---|---|---|---|
| `wifi-connection-mode` | int | `0` | `0` |
| `log-level` | int | `2` (INFO) | as found, do not write |
| `onboarding-version` | int | `2` | |
| `allow-external-configuration` | boolean | `true` | |
| `enable-audio-sink` | boolean | `true` | |
| `kill-on-disconnect` | boolean | `false` | |
| `auto-connect-last-session` | boolean | `false` | |
| `auto-connect-single-usb` | boolean | `false` | |
| `auto-start-on-usb` | boolean | `false` | |
| `reopen-on-reconnection` | boolean | `false` | |
| `use-libusb` | boolean | `false` | |
| `connection-modes` | string set | `usb,wifi` | |
| `bt-address` | string | **`$DHU_BT`** (D-HU's address) | as found, do not write |
| `auto-start-bt-macs` | string set | | as found, read back and record |
| `bt-announce` | | delete | as found, do not write |
| `head-unit-make`, `head-unit-model` | | delete | as found, do not write |
| `video-profile-starvation-cap`, `native-aa-wake-damage-verdict` | | delete | |
| `native-aa-wireless`, `wifi-launcher-mode` | | delete | |

On D-POCO, `orun` sets `bt-announce` (`real` or `skip`), `head-unit-make` `Google` and `head-unit-model` `Desktop Head Unit` per run through `ACTION_SET_SETTINGS`, and reads them back with `ACTION_GET_SETTINGS`. The announce line on the wire is the proof of the value in force. `bt-address` stays `$DHU_BT` in both arms. With `skip` it is not on the wire.

## 6. Shell setup

Make `hur-wifi-test-scripts/bluetooth-audio-disabled-usb-connect-round4/`. Copy `ohu_setkeys.py` and `lib1008r3.sh` into it from `../bluetooth-audio-disabled-usb-connect-round3/`, as the rig ran them in round 3 (with its `cue`). Save `ui1008.py` and `lib1008r4.sh` below beside them. List all four in Setup notes. If a function does not match the real line or dump format, fix it, say so in Setup notes, and keep going. Run each run as one script call under `flock /tmp/ohu-rig.lock`. One adb call at a time per unit; the three log captures are the only streams that run beside them (house rule 8).

Before the round, check for a leftover thermal watcher or capture and kill it by pid: `ps aux | grep -E "[r]ig_thermal.sh watch|[s]leep 20|[l]ogcat"`.

Every shell sources, in this order:

```bash
cd hur-wifi-test-scripts/bluetooth-audio-disabled-usb-connect-round4
source ./lib1008r3.sh; source ./lib1008r4.sh; source "$OUT/r4.env"
```

`$OUT/r4.env` holds the values that Prepare and Stage 0 find (section 8). From round 3's lib this round uses `send`, `cue`, `nl`, `pnl`, `waitfor`, `th_*`, `apk_check`, `loopcap`, `win`, `wc_`, `setj`, `getk`, `pocoput`, `hu_put`, `ghsnap`, `dlg`, `void_` and `psum`. `lib1008r4.sh` replaces `mark`, `cap_start` and `cap_stop`.

**`ui1008.py`** (run it with `python3 -I`):

```python
#!/usr/bin/env python3
# ui1008.py <dump.xml> <label> <device name> [settings package]
# Prints, tab separated: state pkg named checked switch_enabled row_enabled rows
# state: ON, OFF, GREY_ON, GREY_OFF, NOROW (the screen names the device and has no <label> row), UNREAD.
import sys
import xml.etree.ElementTree as ET

path, label, dev = sys.argv[1], sys.argv[2], sys.argv[3]
want = sys.argv[4] if len(sys.argv) > 4 else "com.android.settings"


def out(state, pkg="-", named="-", c="-", se="-", re_="-", rows="-"):
    print("\t".join([state, pkg, named, c, se, re_, rows]))
    sys.exit(0)


try:
    root = ET.parse(path).getroot()
except Exception:
    out("UNREAD")
parent = {ch: p for p in root.iter() for ch in p}
nodes = list(root.iter("node"))
pkgs = [n.get("package") for n in nodes if n.get("package")]
pkg = pkgs[0] if pkgs else "-"
named = any(dev in (n.get("text") or "") for n in nodes)


def switch_of(title):
    p = parent.get(title)
    for _ in range(3):
        if p is None:
            return None, None
        sws = [n for n in p.iter("node") if n.get("checkable") == "true"]
        if len(sws) > 1:
            return None, None
        if len(sws) == 1:
            row = p
            while row is not None and row.get("clickable") != "true":
                row = parent.get(row)
            return sws[0], (row if row is not None else p)
        p = parent.get(p)
    return None, None


rows, hit = [], None
for n in nodes:
    t = (n.get("text") or "").replace("\t", " ").replace(";", ",")
    if not t or n.get("checkable") == "true":
        continue
    sw, row = switch_of(n)
    if sw is None:
        continue
    rows.append("%s=%s/%s/%s" % (t, sw.get("checked"), sw.get("enabled"), row.get("enabled")))
    if t == label and hit is None:
        hit = (sw, row)
if pkg != want or not named:
    out("UNREAD", pkg, str(named).lower())
rs = ";".join(rows) or "-"
if hit is None:
    out("NOROW", pkg, "true", rows=rs)
sw, row = hit
c, se, re_ = sw.get("checked"), sw.get("enabled"), row.get("enabled")
state = ("GREY_" if (se != "true" or re_ != "true") else "") + ("ON" if c == "true" else "OFF")
out(state, pkg, "true", c, se, re_, rs)
```

The `rows` column lists every switch row on the screen as `title=checked/switch_enabled/row_enabled`, so the calls and contacts rows are recorded too.

**`lib1008r4.sh`**:

```bash
# lib1008r4.sh : source AFTER ../bluetooth-audio-disabled-usb-connect-round3/lib1008r3.sh (as the rig ran it, with its notify-send cue).
# Set first: HU (D-POCO), PH (D-MOTO), DH (D-HU), OUT, BASE, KEYS, WANT_MD5, DHU_BT, DEVNAME, LABEL, ROUTE, DACTION, DEXTRA,
# SETTINGS_CMP, MEDIA, A2DP_PLAY_RE, SINK_PLAY_RE, ROUTE_RE, BTCYC. ADDR=$DHU_BT and SUF=${DHU_BT: -5} in every run.
VLC=org.videolan.vlc
at() { while [ $SECONDS -lt $1 ]; do sleep 0.2; done; }
home() { adb -s "$PH" shell input keyevent KEYCODE_HOME; sleep 0.3; }

# Markers go to all three units: the verb on D-POCO, the RIGMARK tag on D-MOTO and D-HU.
mark() { send ACTION_LOG_MARKER --es text "$1" >/dev/null; sleep 0.3
  adb -s "$PH" shell log -t RIGMARK "$1"; sleep 0.3; adb -s "$DH" shell log -t RIGMARK "$1"
  echo "$(date +%T.%3N) $1" >> "$OUT/marks.log"; }

# Three captures per run: D-POCO ($CAP), D-MOTO ($PCAP), D-HU ($DCAP).
cap_start() { CAP=$OUT/$1.hu.logcat; PCAP=$OUT/$1.phone.logcat; DCAP=$OUT/$1.dhu.logcat; : > "$CAP"; : > "$PCAP"; : > "$DCAP"
  adb -s "$HU" logcat -G 16M; adb -s "$HU" logcat -c; CAPLOOP=$(loopcap "$HU" "$CAP")
  adb -s "$PH" logcat -G 16M; adb -s "$PH" logcat -c; PCAPLOOP=$(loopcap "$PH" "$PCAP")
  adb -s "$DH" logcat -G 16M; adb -s "$DH" logcat -c; DCAPLOOP=$(loopcap "$DH" "$DCAP"); sleep 1; }
cap_stop() { local p; for p in $CAPLOOP $PCAPLOOP $DCAPLOOP; do pkill -P $p 2>/dev/null; kill $p 2>/dev/null; done
  adb -s "$PH" logcat -d -v time > "$PCAP.dump"
  for f in "$CAP" "$PCAP" "$DCAP"; do awk '!seen[$0]++' "$f" | tr -d '\r' > "$f.tmp" && mv "$f.tmp" "$f"; done
  ps aux | grep -c "[l]ogcat"; }          # must print 0; else kill the leftover by its pid

# pocobt : D-POCO's adapter state, must print enabled:false for the whole round
pocobt() { adb -s "$HU" shell dumpsys bluetooth_manager | grep -a -m1 -E '^ *enabled: ' | tr -d '\r '; }
# linkst : D-MOTO's A2DP and hands-free state for D-HU, e.g. "A2dpService=Connected HeadsetService=Connected"
linkst() { adb -s "$PH" shell dumpsys bluetooth_manager | tr -d '\r' > "$OUT/linkst.txt"
  local s v o=; for s in A2dpService HeadsetService; do
    v=$(LC_ALL=C awk -v s="Profile: $s" -v a="${DHU_BT: -5}" '
      index($0, "Profile: ") { f = (index($0, s) > 0); g = 0 }
      f && index($0, a) { g = 1 }
      f && g && match($0, /(curState=|mCurrentState: )[A-Za-z]+/) { x = substr($0, RSTART, RLENGTH); sub(/.*[=:] ?/, "", x); print x; exit }' "$OUT/linkst.txt")
    o="$o$s=${v:-none} "; done; echo "$o"; }

# uidump <file> : one uiautomator dump of D-MOTO's screen, one retry
uidump() { local i; for i in 1 2; do
  adb -s "$PH" shell uiautomator dump /sdcard/ui1008.xml >/dev/null 2>&1; sleep 0.3
  adb -s "$PH" exec-out cat /sdcard/ui1008.xml > "$1"; sleep 0.3
  grep -aq '<hierarchy' "$1" && return 0; sleep 1; done; return 1; }
# rd <point> : one reading of the switch, one line in toggle.tsv. LAST is its state.
# toggle.tsv columns: run point host_time phone_time state pkg named checked switch_enabled row_enabled rows
rd() { local f pt o; mkdir -p "$OUT/ui"; f="$OUT/ui/$RUN.$1.$(date +%H%M%S%3N).xml"
  uidump "$f"; pt=$(adb -s "$PH" shell date +%T.%3N | tr -d '\r'); sleep 0.3
  o=$(python3 -I ui1008.py "$f" "$LABEL" "$DEVNAME"); LAST=$(echo "$o" | cut -f1)
  printf '%s\t%s\t%s\t%s\t%s\n' "$RUN" "$1" "$(date +%T.%3N)" "$pt" "$o" | tee -a "$OUT/toggle.tsv"; }
# ui_open : bring D-HU's details screen to the front on D-MOTO; H8 when the scripted route does not get there
ui_open() { local e
  if [ "$ROUTE" = intent ]; then adb -s "$PH" shell am start -a "$DACTION" $DEXTRA >/dev/null
  else home; adb -s "$PH" shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$SETTINGS_CMP" >/dev/null; fi
  sleep 2; uidump "$OUT/open.xml" && [ "$(python3 -I ui1008.py "$OUT/open.xml" "$LABEL" "$DEVNAME" | cut -f1)" != UNREAD ] && return 0
  cue "H8: on D-MOTO open Settings from its launcher icon, then Connected devices, then $DEVNAME. Then do not touch the phone"
  echo "$(date +%T) ${RUN:-} H8" >> "$OUT/hand-steps.log"
  e=$((SECONDS+120)); while [ $SECONDS -lt $e ]; do sleep 3
    uidump "$OUT/open.xml" && [ "$(python3 -I ui1008.py "$OUT/open.xml" "$LABEL" "$DEVNAME" | cut -f1)" != UNREAD ] && return 0; done
  echo -e "${RUN:-}\tui_open-failed" | tee -a "$OUT/summary.tsv"; return 1; }
# poll <T0> : one reading every 5 s from T0+5 to T0+55, screen kept in front; the first one is end+5
poll() { local t0=$1 n=1; ui_open
  while [ $((t0+5*n)) -lt $((t0+60)) ]; do at $((t0+5*n)); if [ $n = 1 ]; then rd end+5; else rd poll; fi; n=$((n+1)); done; }

# hold <k> : the hold test (H9). Reads until 15 s after the switch first reads checked or the policy line of the H9 switch-on, at most 60 s.
hold() { local k=$1 e d=0 PL seq ON BACK LS T TF; mark "$RUN-H$k-start"; PL=$(pnl)
  cue "H9: on D-MOTO turn Media audio for $DEVNAME ON, once. Then do not touch the phone. Say what you saw"
  e=$((SECONDS+60)); while [ $SECONDS -lt $e ]; do rd "hold$k"
    if [ $d = 0 ] && { [ "$(tail -1 "$OUT/toggle.tsv" | cut -f8)" = true ] || tail -n +"$PL" "$PCAP" | grep -aqF "setConnectionPolicy($ADDR, 100)"; }; then
      d=1; e=$((SECONDS+15)); fi; done
  mark "$RUN-H$k-end"; win "$PCAP" "$RUN-H$k" > "$OUT/$RUN.H$k.pwin"
  LC_ALL=C awk -v A="$ADDR" -v pf="$OUT/$RUN.pids" '
  function ms(t,  a) { split(t, a, /[:.]/); return ((a[1] * 60 + a[2]) * 60 + a[3]) * 1000 + a[4] }
  BEGIN { while ((getline l < pf) > 0) { n = split(l, f, " "); if (n >= 2 && f[1] ~ /^[0-9]+$/) own[f[1]] = f[2] } }
  /Start proc [0-9]+:/ { if (match($0, /Start proc [0-9]+:[^ \/]+/)) { s = substr($0, RSTART + 11, RLENGTH - 11); c = index(s, ":"); own[substr(s, 1, c - 1)] = substr(s, c + 1) } }
  index($0, "/BluetoothA2dp(") && index($0, "setConnectionPolicy(" A ", ") {
    match($0, /BluetoothA2dp\( *[0-9]+\)/); pid = substr($0, RSTART + 14, RLENGTH - 14); gsub(/[^0-9]/, "", pid)
    match($0, /, -?[0-9]+\)/); v = substr($0, RSTART + 2, RLENGTH - 3); o = (pid in own) ? own[pid] : "unknown"
    print "h.set " $2 " pid=" pid " owner=" o " value=" v
    if (v == "100" && on == "") { on = $2; onown = o } else if (v == "0" && on != "" && back == "") { back = $2; bpid = pid; bown = o } }
  index($0, "/A2dpService(") && index($0, "Saved connectionPolicy " A " = ") { last = $NF }
  END { print "h.on=" (on == "" ? "none" : on " owner=" onown)
    print "h.back=" (back == "" ? "none" : back " pid=" bpid " owner=" bown " snap_ms=" ms(back) - ms(on))
    print "h.last_saved=" (last == "" ? "none" : last) }' "$OUT/$RUN.H$k.pwin" > "$OUT/$RUN.H$k.h"
  seq=$(awk -F'\t' -v r="$RUN" -v p="hold$k" '$1 == r && $2 == p { printf "%s%s", (n++ ? "," : ""), $8 }' "$OUT/toggle.tsv")
  ON=$(grep -a '^h.on=' "$OUT/$RUN.H$k.h" | cut -d= -f2); BACK=$(grep -a '^h.back=' "$OUT/$RUN.H$k.h" | cut -d= -f2)
  LS=$(grep -a '^h.last_saved=' "$OUT/$RUN.H$k.h" | cut -d= -f2)
  T=$(echo ",$seq," | grep -c ',true,'); TF=$(echo "$seq" | grep -cE 'true.*,false')
  if [ "$ON" = none ] && [ "$T" = 0 ]; then HRES=REFUSED
  elif [ "$TF" = 1 ] || [ "$BACK" != none ]; then HRES=SNAPPED_BACK
  elif [ "$T" = 1 ] && [ "$LS" = 100 ]; then HRES=HELD
  else HRES=OTHER; fi
  echo -e "$RUN\tH$k\t$HRES\tdumps=$seq\t$(tr '\n' '\t' < "$OUT/$RUN.H$k.h")" | tee -a "$OUT/hold.tsv"; }

# av <point> : is A2DP playing to D-HU, and where STREAM_MUSIC goes on D-MOTO. The two regexes come from Stage 0.
av() { local d="$OUT/av"; mkdir -p "$d"
  adb -s "$PH" shell dumpsys audio > "$d/$RUN.$1.moto-audio.txt"; sleep 0.3
  adb -s "$PH" shell dumpsys bluetooth_manager > "$d/$RUN.$1.moto-bt.txt"; sleep 0.3
  adb -s "$PH" shell dumpsys media_session > "$d/$RUN.$1.moto-ms.txt"
  adb -s "$DH" shell dumpsys bluetooth_manager > "$d/$RUN.$1.dhu-bt.txt"; sleep 0.3
  adb -s "$DH" shell dumpsys media.audio_flinger > "$d/$RUN.$1.dhu-af.txt"
  echo -e "$RUN\t$1\tmoto_a2dp_play=$(grep -acE "$A2DP_PLAY_RE" "$d/$RUN.$1.moto-bt.txt")\tdhu_sink_play=$(grep -acE "$SINK_PLAY_RE" "$d/$RUN.$1.dhu-bt.txt")\tmusic_dev=$(grep -a -A4 -- '- STREAM_MUSIC:' "$d/$RUN.$1.moto-audio.txt" | grep -a -m1 'Devices:' | tr -d '\r' | sed 's/^ *//')" | tee -a "$OUT/av.tsv"; }
media() { adb -s "$PH" shell am start -a android.intent.action.VIEW -d "file://$MEDIA" -t audio/mpeg -n $VLC/.StartActivity >/dev/null; sleep 10; home; }
mstop() { adb -s "$PH" shell am force-stop $VLC; }

# call : the call check (H4). telst prints D-MOTO's call state: 0 idle, 1 ringing, 2 off hook.
telst() { adb -s "$PH" shell dumpsys telephony.registry | grep -aoE 'mCallState=[0-9]' | cut -d= -f2 | sort -n | tail -1 | tr -d '\r'; }
call() { local e d="$OUT/call"; mkdir -p "$d"; mark "$RUN-call-start"
  cue "H4: ring D-MOTO from the other phone now. Hang up from the other phone 20 s after D-MOTO answers"
  e=$((SECONDS+90)); while [ $SECONDS -lt $e ] && [ "$(telst)" != 1 ]; do sleep 2; done
  if [ "$(telst)" != 1 ]; then echo -e "$RUN\tno-ring-in-90s" | tee -a "$OUT/calls.tsv"; mark "$RUN-call-end"; return 1; fi
  mark "$RUN-ringing"; sleep 5; adb -s "$PH" shell input keyevent KEYCODE_CALL; sleep 4
  if [ "$(telst)" != 2 ]; then cue "H4: answer the call on D-MOTO's screen"; echo "$(date +%T) $RUN H4-answer-by-hand" >> "$OUT/hand-steps.log"
    e=$((SECONDS+30)); while [ $SECONDS -lt $e ] && [ "$(telst)" != 2 ]; do sleep 2; done; fi
  mark "$RUN-answered"; sleep 5
  adb -s "$PH" shell dumpsys telecom > "$d/$RUN.moto-telecom.txt"; sleep 0.3
  adb -s "$PH" shell dumpsys bluetooth_manager > "$d/$RUN.moto-bt.txt"; sleep 0.3
  adb -s "$DH" shell dumpsys bluetooth_manager > "$d/$RUN.dhu-bt.txt"
  e=$((SECONDS+60)); while [ $SECONDS -lt $e ] && [ "$(telst)" != 0 ]; do sleep 2; done
  mark "$RUN-call-end"
  echo -e "$RUN\troute=$(grep -aoE "$ROUTE_RE" "$d/$RUN.moto-telecom.txt" | head -3 | tr '\n' ' ')\tnoroute=$(wc_ "$PCAP" "$RUN" 'No high priority audio routes available')" | tee -a "$OUT/calls.tsv"; }

# btcycle : D-MOTO's Bluetooth off for 5 s and on, then up to 60 s for the A2DP link to D-HU
btcycle() { local e; mark "$RUN-bt-off"
  if [ "$BTCYC" = cmd ]; then adb -s "$PH" shell cmd bluetooth_manager disable; sleep 5; adb -s "$PH" shell cmd bluetooth_manager enable
  else adb -s "$PH" shell cmd connectivity airplane-mode enable; sleep 5; adb -s "$PH" shell cmd connectivity airplane-mode disable; sleep 2
    adb -s "$PH" shell svc wifi enable; adb -s "$PH" shell svc bluetooth enable; fi
  mark "$RUN-bt-on"; e=$((SECONDS+60))
  while [ $SECONDS -lt $e ]; do sleep 10; linkst | sed "s/^/$RUN after-btcycle /" | tee -a "$OUT/links.tsv" | grep -q 'A2dpService=Connected' && break; done; }

# USB bring-up from line <L> of $CAP, so a second session in one run is found too
usb_up2() { local L=$1 end d foc=; end=$((SECONDS+120)); send ACTION_CHECK_USB >/dev/null
  while [ $SECONDS -lt $end ]; do
    tail -n +"$L" "$CAP" | grep -aqF 'SSL handshake complete' && return 0
    d=$(dlg); [ -n "$d" ] && { echo "$(date +%T) $RUN $d" >> "$OUT/dialogs.log"; cue "H2: allow the USB dialog on D-POCO ($d). Do not tick Always"; sleep 8; }
    if [ -z "$foc" ] && [ $SECONDS -ge $((end-60)) ]; then foc=1
      adb -s "$PH" shell dumpsys window | grep -a mCurrentFocus | tee -a "$OUT/dialogs.log"
      cue "H3: clear any Android Auto or system screen on D-MOTO and say what it said"; fi
    sleep 2; done; return 1; }
# up <L> : a session within at most two H1 recoveries, else 1 (the run is then VOID)
up() { local i; usb_up2 "$1" && return 0
  for i in 1 2; do echo -e "$RUN\tH1-recovery-$i" | tee -a "$OUT/summary.tsv"
    cue "H1: re-enumerate D-MOTO: unplug it from D-POCO, wait 5 s, plug it back"; sleep 12
    usb_up2 "$1" && return 0; done; return 1; }
cue_pull5() { local t0=$SECONDS
  while [ $((SECONDS-t0)) -lt 600 ]; do
    cue "H5: PULL D-MOTO's cable out of D-POCO now. Leave it out until the plug-in cue"
    waitfor 30 'AapService: session state disconnected' "$1" && return 0; done; return 1; }

# pre_rig : D-POCO's Bluetooth off, D-MOTO's A2DP and hands-free on D-HU
pre_rig() { local b l
  b=$(pocobt); [ "$b" = enabled:false ] || { adb -s "$HU" shell svc bluetooth disable; sleep 5; b=$(pocobt); }
  echo -e "$RUN\tpoco_bt=$b" | tee -a "$OUT/summary.tsv"
  [ "$b" = enabled:false ] || { echo -e "$RUN\tVOID D-POCO Bluetooth does not stay off" | tee -a "$OUT/summary.tsv"; return 1; }
  l=$(linkst); echo -e "$RUN\tpre\t$l" | tee -a "$OUT/links.tsv"
  case "$l" in *A2dpService=Connected*HeadsetService=Connected*) return 0 ;; esac
  adb -s "$DH" shell svc bluetooth disable; sleep 20; adb -s "$DH" shell svc bluetooth enable; sleep 40
  l=$(linkst); echo -e "$RUN\tpre-after-dhu-cycle\t$l" | tee -a "$OUT/links.tsv"; return 0; }

# prechk <run> : the switch must read ON before <run>. Repair: one clean real session (at most 2 in the round), then H6.
prechk() { local id=$1 e; RUN=$id-pre; ui_open; rd pre-check; home; [ "$LAST" = ON ] && return 0
  REP=$((REP+1)); if [ $REP -le 2 ]; then orun "Rrep$REP" real clean ""; RUN=$id-pre; ui_open; rd pre-check; home; [ "$LAST" = ON ] && return 0; fi
  ui_open; cue "H6: on D-MOTO turn Media audio for $DEVNAME on. Then touch nothing else"
  echo "$(date +%T) $RUN H6" >> "$OUT/hand-steps.log"
  e=$((SECONDS+180)); while [ $SECONDS -lt $e ]; do sleep 3; rd pre-check-h6; [ "$LAST" = ON ] && { home; return 0; }; done
  home; echo -e "$id\tVOID the switch is not ON before the run" | tee -a "$OUT/summary.tsv"; return 1; }

# usum4 <run> : unit-side counts. Verb counts are over the whole run capture (round 3's window count was one short).
usum4() { local c=$CAP r=$1
  { echo "== $r"
    echo "hu.verbs_whole=$(grep -acF 'AutomationReceiver: ' $c) hu.check_usb=$(grep -aF 'AutomationReceiver: ' $c | grep -acF ACTION_CHECK_USB) hu.set=$(grep -aF 'AutomationReceiver: ' $c | grep -acF ACTION_SET_SETTINGS) hu.get=$(grep -aF 'AutomationReceiver: ' $c | grep -acF ACTION_GET_SETTINGS)"
    echo "hu.announce=$(grep -aoE 'Bluetooth service announced with carAddress=[^ ]+ \(bt-announce=[a-z]+\)' $c | sort | uniq -c | tr '\n' ' ')"
    echo "hu.announce_empty=$(grep -acF 'BT MAC Address is empty, so no Bluetooth service is announced' $c)"
    echo "hu.ssl=$(grep -acF 'SSL handshake complete' $c) hu.media_audio=$(grep -acF 'Media Start Request AUDIO:' $c)"
    echo "hu.live_at_stop=$(win $c $r | LC_ALL=C awk -v m="AutomationMarker: $r-stop" '
      index($0, "SSL handshake complete") { s = 1; d = 0 } index($0, "AapService: session state disconnected") { d = 1 }
      index($0, m) { print (s && !d) ? 1 : 0; x = 1; exit } END { if (!x) print 0 }')"
    echo "hu.disconnected:"; grep -aF 'AapService: session state disconnected' $c | head -3
    echo "hu.acc_mode=$(grep -acF 'Found device already in accessory mode' $c) hu.vfail=$(grep -acF 'Version request send failed' $c) hu.tls_parse=$(grep -acF 'Unable to parse TLS packet header' $c) hu.usb_perm=$(grep -acF 'Requesting USB permission for' $c)"
    echo "hu.fatal=$(grep -acF 'FATAL EXCEPTION' $c)"
    echo "dh.wake=$(grep -acE 'MATCH! Starting AapService|Attempting active poke to device' $DCAP) dh.openhu=$(grep -acE '/OPENHU *\(' $DCAP)"
    echo "ph.disabler=$(wc_ $PCAP $r 'disabling A2dp via profile disabler') ph.noroute=$(wc_ $PCAP $r 'No high priority audio routes available')"
    echo "ph.abort=$(win $PCAP $r | grep -acE 'Wrong device is being paired|Sending a pairing request|Device was unbonded at some point') ph.bond_none=$(win $PCAP $r | grep -aF "$SUF" | grep -acF 'BOND_NONE')"
    echo "ph.fatal_processes:"; win $PCAP $r | grep -a -A1 'FATAL EXCEPTION' | grep -aF 'Process: ' | head -3
    echo "ph.restarts=$(cat "$PCAP.restarts" 2>/dev/null | wc -l)"
  } | tee -a "$OUT/summary.tsv"; }

# tsum <run> : every reading of the run beside the last stored policy at that phone time, then the grey polls
tsum() { local r=$1
  LC_ALL=C awk -v r="$r" -v A="$ADDR" '
  FNR == NR { if (index($0, "/A2dpService(") && index($0, "Saved connectionPolicy " A " = ")) { n++; tt[n] = $2; vv[n] = $NF }; next }
  { split($0, f, "\t"); if (f[1] != r) next; pol = "none"; for (i = 1; i <= n; i++) if (tt[i] <= f[4]) pol = vv[i]
    print r " t." f[2] " phone=" f[4] " state=" f[5] " checked=" f[8] " sw_enabled=" f[9] " row_enabled=" f[10] " policy=" pol }' "$PCAP" "$OUT/toggle.tsv" | tee -a "$OUT/summary.tsv"
  awk -F'\t' -v r="$r" '$1 == r && ($2 == "end+5" || $2 == "poll" || $2 == "end+60") && $5 ~ /^GREY/ { if (a == "") a = $4; b = $4; c++ }
    END { print r " t.grey_polls=" c + 0 " first=" (a == "" ? "none" : a) " last=" (b == "" ? "none" : b) }' "$OUT/toggle.tsv" | tee -a "$OUT/summary.tsv"; }

# orun <run> <real|skip> <clean|pull|ghkill> "<options>" ; options: call hold-live hold-60 clear-time clear-session
orun() { RUN=$1; local arm=$2 how=$3 opt=" $4 " L L2 LP T0 T1 S2 SSLT
  th_gate || return 3; apk_check || { echo -e "$RUN\tAPK_MISMATCH" | tee -a "$OUT/summary.tsv"; return 1; }
  pre_rig || return 1
  adb -s "$HU" shell am force-stop $PKG
  pocoput "$BASE" $KEYS > "$OUT/$RUN.keys" || { echo -e "$RUN\tpocoput-failed" | tee -a "$OUT/summary.tsv"; return 1; }
  cap_start "$RUN"; mark "$RUN-start"; ghsnap
  ui_open; rd pre; home
  adb -s "$HU" shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity >/dev/null; sleep 3
  setj "\"bt-announce\":\"$arm\",\"head-unit-make\":\"Google\",\"head-unit-model\":\"Desktop Head Unit\"" >> "$OUT/$RUN.keys"; getk >> "$OUT/$RUN.keys"
  cue "H1: plug D-MOTO into D-POCO's OTG port"; sleep 8
  L=$(nl); up "$L" || { void_ "no session after two H1 recoveries"; return 2; }
  SSLT=$SECONDS; sleep 3; ghsnap
  media; waitfor 15 'Media Start Request AUDIO:' "$L" || echo -e "$RUN\tno-media-start-in-15s" | tee -a "$OUT/summary.tsv"
  at $((SSLT+20)); ui_open; rd live; home; av live
  case "$opt" in *" call "*) call ;; esac
  case "$opt" in *" hold-live "*) ui_open; hold 1; home; av after-hold1 ;; esac
  mstop; sleep 3
  mark "$RUN-stop"; LP=$(nl)
  case $how in
    clean)  send ACTION_DISCONNECT >/dev/null; T0=$SECONDS; cue "H1: unplug D-MOTO from D-POCO and leave it out" ;;
    pull)   cue_pull5 "$LP" || echo -e "$RUN\tno-disconnect-line-in-600s" | tee -a "$OUT/summary.tsv"
            T0=$SECONDS; mark "$RUN-gone" ;;
    ghkill) adb -s "$PH" shell am force-stop $GH; T0=$SECONDS; sleep 2; send ACTION_EXIT >/dev/null
            cue "H1: unplug D-MOTO from D-POCO and leave it out" ;;
  esac
  poll $T0
  at $((T0+60)); ui_open; rd end+60; ghsnap; mark "$RUN-read"
  case "$opt" in *" hold-60 "*) case $LAST in OFF|GREY_OFF) hold 2 ;; esac ;; esac
  home
  if [ "$LAST" != ON ]; then
    case "$opt" in *" clear-time "*)
      at $((T0+180)); ui_open; rd clr3m; home
      at $((T0+300)); ui_open; rd clr5m; HRES=none
      case $LAST in OFF|GREY_OFF) hold 2 ;; esac; home
      if [ "$HRES" != HELD ]; then btcycle; ui_open; rd clrbt; case $LAST in OFF|GREY_OFF) hold 3 ;; esac; home; fi ;;
    esac
    case "$opt" in *" clear-session "*)
      cue "H1: plug D-MOTO into D-POCO's OTG port"; sleep 8; L2=$(nl)
      if up "$L2"; then S2=$SECONDS; at $((S2+20)); ui_open; rd clr-live; home
        mark "$RUN-stop2"; send ACTION_DISCONNECT >/dev/null; T1=$SECONDS; cue "H1: unplug D-MOTO from D-POCO and leave it out"
        at $((T1+5)); ui_open; rd clr-end+5; at $((T1+60)); ui_open; rd clr-end+60; home
      else echo -e "$RUN\tclear-session: no session after two H1 recoveries" | tee -a "$OUT/summary.tsv"; fi ;;
    esac
  fi
  mark "$RUN-end"; ghsnap; cap_stop; th_stop
  usum4 "$RUN"; psum "$RUN"; tsum "$RUN"; th_report "$RUN" | tee -a "$OUT/summary.tsv"; }
```

**Read points, as `orun` takes them.** `t0` is the event that ends the session: `ACTION_DISCONNECT` (clean), the head unit's `session state disconnected` line after the pull cue (pull, marker `-gone`), or `am force-stop` of Android Auto (ghkill).

| Point in `toggle.tsv` | When | Screen |
|---|---|---|
| `pre` | before the plug-in cue, cable out | reopened |
| `live` | 20 s after SSL, media playing | reopened |
| `hold1` | R1 and S4 only, after `live` and the call | kept in front |
| `end+5`, `poll` | every 5 s from `t0+5` to `t0+55` | opened once at `t0`, kept in front |
| `end+60` | `t0+60` | reopened |
| `hold2` | R2a, R2b at `end+60`; R3a at `t0+5 min`; only where the switch reads `OFF` or `GREY_OFF` | kept in front |
| `clr3m`, `clr5m` | R3a, `t0+3 min` and `t0+5 min` (clear by time) | reopened |
| `clrbt`, `hold3` | R3a, after D-MOTO's Bluetooth off and on, only if `hold2` did not read `HELD` | reopened |
| `clr-live`, `clr-end+5`, `clr-end+60` | R3b, the next clean session (clear by the next session) | reopened |

The clearing chain runs only when `end+60` does not read `ON`.

## 7. The lines that decide the runs

App lines were checked with `grep -rF` against `app/src/main` on `fff96699`. All print at INFO or above. The phone's Bluetooth lines are Android's, checked against D-MOTO's captures in the round 1 to 3 assets. Every count is inside the run's window, from `<run>-start` to `<run>-end` (`AutomationMarker:` in `$CAP`, `RIGMARK` in `$PCAP` and `$DCAP`, function `win`), except the `hu.*` counts of `usum4`, which take the whole run capture.

| Line (fixed substring) | File | Meaning |
|---|---|---|
| `Bluetooth service announced with carAddress=` | `$CAP` | the address on the wire, then ` (bt-announce=real)` or ` (bt-announce=skip)` |
| `BT MAC Address is empty, so no Bluetooth service is announced` | `$CAP` | an empty `bt-address`: the run is VOID |
| `SSL handshake complete` | `$CAP` | a session formed (never prefix `Handshake:`) |
| `Media Start Request AUDIO:` | `$CAP` | the media channel opened over the cable (composed from `Media Start Request %s: session=`) |
| `AapService: session state disconnected` | `$CAP` | the session ended (composed from `AapService: session state `) |
| `AutomationReceiver: ` with `ACTION_CHECK_USB`, `ACTION_SET_SETTINGS`, `ACTION_GET_SETTINGS` | `$CAP` | a verb landed (the line carries the full action name) |
| `AutomationMarker: ` | `$CAP` | a run marker |
| `Found device already in accessory mode`, `Version request send failed`, `Requesting USB permission for` | `$CAP` | the USB bring-up and the re-attach fault |
| `Unable to parse TLS packet header` | `$CAP` | the second re-attach fault (an `SSLException` message) |
| `MATCH! Starting AapService`, `Attempting active poke to device` | `$DCAP` | D-HU's app woke up: the run is VOID |
| `/OPENHU (` | `$DCAP` | any line of D-HU's app; expected 0 (informational) |
| `FATAL EXCEPTION`, then `Process: ` | `$CAP`, `$PCAP` | a crash, and whose |
| `RIGMARK` | `$PCAP`, `$DCAP` | the run markers |
| `getProfileConnectionPolicy: XX:XX:XX:XX:` | `$PCAP` | a read of the stored policy; the first one for `$SUF` in a window is **P0** |
| `setConnectionPolicy(` | `$PCAP` | a request to change it, under tag `BluetoothA2dp(<pid of the asker>)` |
| `Saved connectionPolicy ` | `$PCAP` | the stored value, under tag `A2dpService(...)`: `100` on, `0` off |
| `Start proc ` | `$PCAP` | maps a pid to a process name |
| `disabling A2dp via profile disabler` | `$PCAP` | Android Auto's disabler ran |
| `No high priority audio routes available` | `$PCAP` | Android Auto found no call route (tag `GH.CallManager`) |
| `Wrong device is being paired`, `Sending a pairing request`, `Device was unbonded at some point` | `$PCAP` | **abort lines.** Android Auto acts on the borrowed address. Read from the 17.5 and 17.8 dex, not seen on 17.9, so a zero proves little |
| `BOND_NONE` with `$SUF` on the same line | `$PCAP` | **abort line.** D-MOTO dropped its bond to D-HU |

**Reading a run's policy (`psum`, unchanged from round 3).** Phase 0 is before `<run>-stop`, phase 1 is from `<run>-stop` to `<run>-read` (`t0+60`), phase 2 is after `<run>-read`. `p.P0`, `p.disabled_by_gh_before_stop`, `p.restore`, `p.reading` (`RESTORED`, `NOT_RESTORED`, `OTHER`) mean what they meant in round 3. **A `com.android.settings` setter inside an `-H<k>` window is the hold test, not a fault.** Subtract those from `p.non_gh_setters_before_read` and report the rest.

**Reading a switch (`ui1008.py`).** `GREY_ON` or `GREY_OFF` is any of `switch_enabled=false` or `row_enabled=false`. `OFF` is `checked=false` with both enabled. `NOROW` is a Settings screen that names D-HU and has no media row. `UNREAD` is a dump whose package is not `com.android.settings` or that does not name D-HU. **A hold test that reads `SNAPPED_BACK` or `REFUSED` is a grey state too**, whatever the look.

**Reading a hold test (`hold.tsv`).** `HELD`: the switch reads checked in every dump after the first checked one, no `setConnectionPolicy($ADDR, 0)` follows the H9 switch-on, and the last stored value is `100`. `SNAPPED_BACK`: it reads checked and then unchecked, or a `setConnectionPolicy($ADDR, 0)` follows the H9 switch-on. Report `snap_ms` and the owner of that line. `REFUSED`: no `setConnectionPolicy($ADDR, 100)` and no checked dump. `OTHER`: anything else. The operator's words go in the notes; the dumps and the log decide.

## 8. Runs

### Prepare (P), on cables

1. **Build and identity** (section 1). Record `WANT_MD5`, the `commit` reply, and whether you built or installed.
2. **Back up both settings files** with each app stopped: `adb -s 4f4027e9 shell run-as $PKG cat shared_prefs/settings.xml > $OUT/settings_backup_poco.xml` and `adb -s 27870808938846 shell cat /data/data/$PKG/shared_prefs/settings.xml > $OUT/settings_backup_dhu.xml`. Diff each against round 3's closing backup and state the delta, even if zero. Record the rig audio keys from both.
3. **`stat` both `shared_prefs/` directories** and record owner, group and mode.
4. **Addresses.** `adb -s 27870808938846 shell settings get secure bluetooth_address` is `DHU_BT` (round 3: `11:46:03:10:33:59`, the address of every D-HU policy line). `adb -s ZY22GC3BM4 shell settings get secure bluetooth_address` is `MOTO_BT` (round 3: `A0:46:5A:97:E4:95`). `POCO_BT` from `4f4027e9` (round 3: `DC:B7:2E:5E:4E:59`). If a value differs, use the new one and say so.
5. **Bonds.** `adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -i -A30 "Bonded devices" > $OUT/bonds-before.txt`. It must list `DHU_BT`. Its name there is `DEVNAME` (round 3: `Navegadortz2`). If D-HU is missing, H7, then check again. Still missing: the round is UNTESTABLE.
6. **D-HU quiet** (on its PC cable):
   ```bash
   adb -s 27870808938846 shell am start -a android.intent.action.VIEW -d "headunit://exit"; sleep 3
   adb -s 27870808938846 shell am force-stop com.andrerinas.headunitrevived
   ( HU=27870808938846; hu_put $OUT/settings_backup_dhu.xml int:wifi-connection-mode=0 )
   adb -s 27870808938846 shell cat /data/data/com.andrerinas.headunitrevived/shared_prefs/settings.xml | grep -aoE '(wifi-connection-mode|auto-start-bt-macs)[^/]*'
   ```
   Record `auto-start-bt-macs` as found. Do not launch D-HU's app again in this round.
7. **D-MOTO.** Run `rig_preflight.sh`; Bluetooth must be on. Then:
   ```bash
   adb -s ZY22GC3BM4 shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
   for t in CAR.BT.SVC CAR.BT.SVC.LITE CAR.BT.A2DP CAR.BT.A2dpDisabler CAR.BT.LITE; do adb -s ZY22GC3BM4 shell setprop log.tag.$t VERBOSE; done
   adb -s ZY22GC3BM4 shell settings get global zen_mode          # must be 0; else: cmd notification set_dnd off
   adb -s ZY22GC3BM4 shell svc power stayon true
   adb -s ZY22GC3BM4 shell dumpsys window | grep -a -E "mShowingLockscreen|mDreamingLockscreen"   # H0 if locked behind a PIN
   adb -s ZY22GC3BM4 shell dumpsys battery | grep -a level; adb -s 4f4027e9 shell dumpsys battery | grep -a level   # both 50 or more
   adb -s ZY22GC3BM4 shell input keyevent KEYCODE_HOME
   ```
   Record the Android Auto version. If it is not `17.9.664004`, say so and go on.
8. **D-POCO's Bluetooth off:** `adb -s 4f4027e9 shell svc bluetooth disable`, wait 30 s, then `adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -m1 "enabled:"` must read `enabled: false`. If it came back on by itself, disable it again and record it. Do not use airplane mode on D-POCO: it would drop wireless adb.
9. **Wireless adb**, as in round 3, while both phones are still on PC cables:
   ```bash
   adb -s 4f4027e9 tcpip 5555; adb -s ZY22GC3BM4 tcpip 5555; sleep 3
   POCO_IP=$(adb -s 4f4027e9 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
   MOTO_IP=$(adb -s ZY22GC3BM4 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
   adb connect $POCO_IP:5555; adb connect $MOTO_IP:5555
   adb -s $POCO_IP:5555 shell dumpsys usb > $OUT/dumpsys-usb-before.txt
   ```
   Then H1: unplug D-POCO and D-MOTO from the PC. **Leave D-MOTO unplugged.** Check that `getprop ro.product.model` answers on both wireless serials.
10. **Write `$OUT/r4.env`** with these lines, and add the Stage 0 values to it as Stage 0 finds them:
    ```bash
    HU=$POCO_IP:5555; PH=$MOTO_IP:5555; DH=27870808938846; BASE=$OUT/settings_backup_poco.xml; WANT_MD5=...
    DHU_BT=...; ADDR=$DHU_BT; SUF=${DHU_BT: -5}; POCO_BT=...; MOTO_BT=...; DEVNAME=...
    KEYS="int:wifi-connection-mode=0 int:log-level=2 int:onboarding-version=2 bool:allow-external-configuration=true bool:enable-audio-sink=true bool:kill-on-disconnect=false bool:auto-connect-last-session=false bool:auto-connect-single-usb=false bool:auto-start-on-usb=false bool:reopen-on-reconnection=false bool:use-libusb=false set:connection-modes=usb,wifi str:bt-address=$DHU_BT del:bt-announce del:head-unit-make del:head-unit-model del:video-profile-starvation-cap del:native-aa-wake-damage-verdict del:native-aa-wireless del:wifi-launcher-mode"
    REP=0
    ```
11. **The operator message** of section 4.

### Stage 0: calibration (no session)

One capture, `S0`. Steps marked **host step** need the Sonnet host to read a file and choose a value. Write every chosen value into `$OUT/r4.env` and quote it in Setup notes.

```bash
RUN=S0; cap_start S0; mark S0-start; ghsnap
```

1. **The link.** `linkst` must print `A2dpService=Connected HeadsetService=Connected`. If it prints `none`, read `$OUT/linkst.txt` and fix the awk (**host step**). If the link is genuinely down, run `pre_rig` once. If A2DP to D-HU is still not connected, stop and escalate: the reporter's layout cannot be built.
2. **The screen route.** Try a scripted route first:
   ```bash
   adb -s $PH shell dumpsys package com.android.settings | grep -a -iE "BLUETOOTH_DEVICE_DETAIL|DeviceDetail" > $OUT/s0-settings-actions.txt
   SETTINGS_CMP=$(adb -s $PH shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER com.android.settings | tail -1 | tr -d '\r')
   for A in android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS com.android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS $(grep -aoE '[a-z.]+\.[A-Z_]*BLUETOOTH_DEVICE_DETAIL[A-Z_]*' $OUT/s0-settings-actions.txt | sort -u); do
     adb -s $PH shell am start -a $A --es device_address $DHU_BT; sleep 3; uidump $OUT/s0-$A.xml
     echo "$A $(python3 -I ui1008.py $OUT/s0-$A.xml - "$DEVNAME")"; home; sleep 1; done
   ```
   - An action whose line starts `NOROW com.android.settings true` opened D-HU's screen. Set `ROUTE=intent`, `DACTION=<that action>`, `DEXTRA="--es device_address $DHU_BT"`.
   - None did: cue H8 once, then test the resume route. Set `ROUTE=resume`, and run `home; adb -s $PH shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$SETTINGS_CMP"; sleep 2; uidump $OUT/s0-resume.xml; python3 -I ui1008.py $OUT/s0-resume.xml - "$DEVNAME"`. A line that starts `NOROW com.android.settings true` means the route works. If it does not, `ui_open` cues H8 at each reading. Say so in Setup notes, because each reading then costs the operator a step.
3. **The label (host step).** The `rows` column of the dump from step 2 lists every switch row. `LABEL` is the row title that contains `ultimedia` or `edia` (round 1 to 3 called it "Media audio"; D-MOTO's UI is Spanish). If none or more than one match, choose by reading the dump and say why. Then `ui_open; rd s0-dhu; home` must print `ON`. Record the full `rows` value: the calls and contacts rows are evidence too.
4. **D-POCO's screen, for the record.** Open D-POCO's entry on D-MOTO (the intent route with `--es device_address $POCO_BT`, or H8 with D-POCO's name from `bonds-before.txt`), dump it to `$OUT/s0-poco.xml`, and record `python3 -I ui1008.py $OUT/s0-poco.xml "$LABEL" "<D-POCO's name>"`. A `NOROW` here confirms the plan's reason for the borrowed address. Then bring D-HU's screen back (`ui_open`, H8 if asked), so the resume route lands on D-HU, and press HOME.
5. **The Bluetooth cycle lever.** `adb -s $PH shell cmd bluetooth_manager help | grep -a -E "enable|disable"`. Both listed: `BTCYC=cmd`. Otherwise `BTCYC=airplane`, and say so.
6. **Media (host step for the two regexes).**
   ```bash
   adb -s $PH shell pm list packages org.videolan.vlc          # must print package:org.videolan.vlc
   adb -s $PH shell appops set org.videolan.vlc MANAGE_EXTERNAL_STORAGE allow
   adb -s $PH shell find /sdcard/Music /sdcard/Download -maxdepth 2 -iname '*.mp3' | head -1   # MEDIA; it must last 3 min or more
   ```
   If no MP3 is on D-MOTO, push one from the rig PC to `/sdcard/Music/ohu1008.mp3` and use that path. If VLC is not installed, no other player starts without a hand step, so stop and ask the engineer. Then calibrate the second-copy instrument with no session, while D-MOTO plays to D-HU over A2DP:
   ```bash
   A2DP_PLAY_RE='mIsPlaying: true'; SINK_PLAY_RE='mIsPlaying: true|isAudioPlaying: true|STATE_PLAYING|AUDIO_STATE_STARTED'
   media; sleep 5; av s0-play; mstop; sleep 5; av s0-stop
   ```
   **Host step:** compare `$OUT/av/S0.s0-play.*` with `$OUT/av/S0.s0-stop.*`. Choose `A2DP_PLAY_RE` (D-MOTO's A2DP section) and `SINK_PLAY_RE` (D-HU's A2DP sink section) as the regexes that match at least once in `s0-play` and never in `s0-stop`. If no field changes on one side, the second-copy check rests on the other side only. If neither changes, the second-copy check is INCONCLUSIVE for the round. Say so up front in the results. Record the `music_dev` value of both lines.
7. **Calls (host step for `ROUTE_RE`).** Set `ROUTE_RE='Active[A-Za-z]*Route|CallAudioState\{[^}]*\}'`, then run `call`. With no session, the call audio should go to D-HU over hands-free. **Host step:** read `$OUT/call/S0.moto-telecom.txt` and choose a `ROUTE_RE` that names the route (earpiece, speaker, Bluetooth, and which device). If `calls.tsv` reads `no-ring-in-90s` twice (one retry), the call half is UNTESTABLE for the round: leave `call` out of every `orun` below and say so.
8. **Flags (informational).** `adb -s $PH shell dumpsys activity service com.google.android.projection.gearhead/com.google.android.gearhead.service.SharedService > $OUT/s0-flags.txt`, then `grep -a -c -E "a2dp_fix_car_list|enable_a2dp_at_projection_end|BluetoothPairing__disable_a2dp" $OUT/s0-flags.txt`. Record the count. Round 2 found none.
9. `mark S0-end; cap_stop`. Check `$OUT/S0.dhu.logcat` for `dh.wake`: `grep -acE 'MATCH! Starting AapService|Attempting active poke to device'` must be 0.

**Stage 0 gate.** The round is UNTESTABLE before any session if no route (scripted or H8) gives a dump that names D-HU and has a `LABEL` row reading `ON`.

### Validity of a run

A run counts when all of these hold. Read each from `summary.tsv`, `policy.tsv` and `$OUT/<run>.keys`:

1. `hu.announce` is exactly one line per session, with the arm's value: `carAddress=<DHU_BT> (bt-announce=real)` or `carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)`. `hu.announce_empty` = 0.
2. `hu.ssl` = 1. R3b: 2, when its clearing session formed.
3. `getk` in `$OUT/<run>.keys` shows the arm's `bt-announce`, `Google` and `Desktop Head Unit`.
4. `hu.check_usb`, `hu.set` and `hu.get` are each 1 or more.
5. The phone window carries the `-start`, `-stop`, `-read` and `-end` markers (`p.stop` and `p.read` not empty). A pull run also needs `-gone` and `hu.live_at_stop` = 1.
6. `dh.wake` = 0.
7. `hu.fatal` = 0, and `ph.fatal_processes` names no `com.google.android.projection.gearhead` process. A `com.android.settings` crash: re-run once under `<id>x`.
8. The `pre`, `live`, `end+5` and `end+60` readings are not `UNREAD`.
9. **Abort.** `ph.abort` = 0 and `ph.bond_none` = 0. Otherwise stop the stage at once, go to the close, and report the lines.

A run that misses item 1 to 7 is VOID: say which item, re-run it once under `<id>x`, then go on. `hu.media_audio` of 0 does not void a run. It makes that run's second-copy check INCONCLUSIVE.

### Stage R: the real address

The cable is out. Run these in order. Each `prechk` must return 0 before its run; if it returns 1, the run is VOID with the reason "switch not ON before the run".

```bash
prechk R1  && orun R1  real clean  "call hold-live"
prechk R2a && orun R2a real pull   "hold-60"
prechk R2b && orun R2b real pull   "hold-60"
prechk R3a && orun R3a real ghkill "clear-time"
prechk R3b && orun R3b real ghkill "clear-session"
```

#### R1. Clean end, call, hold test during the session (the real control)

- **Control valid** when the run is valid, `ph.disabler` is 1 or more and `p.disabled_by_gh_before_stop` is 1 or more. This shows that D-HU's address, announced by D-POCO, reaches the disabler.
- **If R1 is not a valid control**, run `prechk R1x && orun R1x real clean "call hold-live"`. If R1x is not a valid control either, stop the round: it is INCONCLUSIVE, because Stage S has no control. Go to the close.
- **Measure:** the `live` state and its `policy=` column in `tsum`; the `hold1` outcome with `snap_ms` and the owner of the snap-back line (can a user turn the switch on while Android Auto runs?); `av live` and `av after-hold1`; the call route.
- **A switch that reads `ON` while `policy=0`** at the same reading is a finding. Mark it `STALE_OR_ROM` in the results and quote both.

#### R2a, R2b. Cable pulled out mid-session

Before each, remind the operator: "Wait for the PULL cue. Pull the cable from D-POCO. Leave it out until the next plug-in cue."

- **Measure:** every `end+5` and `poll` state, `t.grey_polls` with its first and last phone time, `end+60`, and `hold2` if it ran. Round 3 measured that a pull restores the policy within 460 ms of the disconnect. This run reads whether the switch goes grey on the way back.

#### R3a. Android Auto force-stopped, then cleared by time, by hand, by Bluetooth

- **Measure:** `end+60`, `clr3m`, `clr5m` (does time alone clear it?), `hold2` at `t0+5 min` (can the user turn it back on by hand? This is the reporter's case), then `clrbt` and `hold3` if `hold2` did not read `HELD`.
- The policy reading `p.reading` should be `NOT_RESTORED`, as in rounds 2 and 3. Report it.

#### R3b. Android Auto force-stopped, then cleared by the next session

- **Measure:** `end+60`, then `clr-live`, `clr-end+5` and `clr-end+60` of the next clean session. No hold test runs before that session, so the clearer is measured alone.

**Goal 1 is a measurement.** For every R run, report per point: `state`, `checked`, `sw_enabled`, `row_enabled` and `policy`. Report the first and last poll that read grey, and the time between them. Report which clearer first gave a reading of `ON`. Report every hold outcome. If no R run shows `GREY_*`, `NOROW`, `SNAPPED_BACK` or `REFUSED`, say so in one line: D-MOTO does not reproduce a grey state.

### Stage S: the skip value (the point of the round)

```bash
prechk S1 && orun S1 skip clean  "call"
prechk S2 && orun S2 skip pull   ""
prechk S3 && orun S3 skip ghkill ""
prechk S4 && orun S4 real clean  "call hold-live"
```

S1 to S3 run no hold test: with `skip` the switch should never read off, and a reading of off is already a FAIL.

#### S1, S2, S3 (per run)

- **PASS** when the run is valid and all of these hold:
  - every reading of the run at `pre`, `live`, `end+5`, `poll` and `end+60` is `ON` (`UNREAD` readings are left out; count them);
  - `ph.disabler` = 0;
  - no `p.set` line with `value=0` in any phase;
  - S1 only, with `hu.media_audio` of 1 or more: `av live` reads `moto_a2dp_play=0` and `dhu_sink_play=0`.
- **FAIL** when any of those readings is `OFF`, `GREY_ON`, `GREY_OFF` or `NOROW`, or `ph.disabler` is 1 or more, or a `p.set` line has `value=0`, or S1's `av live` shows A2DP playing to D-HU while `hu.media_audio` is 1 or more. Quote the readings and lines.
- **What a PASS would look like if the skip value did nothing:** the switch would read `OFF` at `live` and `policy=0`, as in R1 and S4. A PASS is credible only next to a valid R1 and a valid S4 with `ph.disabler` of 1 or more. A reading of `ON` with `policy=none` (no stored line at all) is also expected under `skip`, because nothing changes the policy; P0 says what it was.
- **Record, not graded:** S1's call route, `ph.noroute`, and `music_dev` at `live`.

#### S4. Back to `real`, the second half of the control

- **PASS** when the run is valid, `getk` shows `"bt-announce":"real"`, the announce line is `carAddress=<DHU_BT> (bt-announce=real)`, and `ph.disabler` and `p.disabled_by_gh_before_stop` are both 1 or more.
- **FAIL** otherwise, with the item that missed.
- **Compare with R1:** the `live` state, `hold1` outcome and call route. If they differ, report both. A drift on D-MOTO across the round shows up here.

### Closing the round

Do the steps in this order. Step 1 can run a repair session, which writes the run keys again, so it comes before any restore.

1. **D-MOTO's switch for D-HU must read `ON`:** `RUN=END; ui_open; rd close; home`. If it does not, run `prechk END` (one clean session, then H6). Never leave the rig with that switch off.
2. `send ACTION_EXIT` on D-POCO, then `adb -s $HU shell am start -a android.intent.action.VIEW -d headunit://exit`, `sleep 3`, `adb -s $HU shell am force-stop $PKG`, `mstop`.
3. Restore D-POCO's backup with `pocoput $BASE` (no spec). Read back `bt-address`, `bt-announce`, `head-unit-make` and `head-unit-model`; they must match `settings_backup_poco.xml`. **A `bt-address` left at D-HU's address would announce the wrong head unit in every later round.**
4. `adb -s $HU shell svc bluetooth enable`, and confirm `enabled: true` with `dumpsys`.
5. Restore D-HU's backup: `( HU=27870808938846; hu_put $OUT/settings_backup_dhu.xml )` (no spec). Read back `wifi-connection-mode` and `auto-start-bt-macs`; they must match the backup. Do not launch D-HU's app.
6. **Bonds:** `adb -s $PH shell dumpsys bluetooth_manager | grep -a -i -A30 "Bonded devices" > $OUT/bonds-after.txt`. It must still list `DHU_BT` and `POCO_BT`. Diff it against `bonds-before.txt`.
7. `adb -s $HU shell dumpsys usb > $OUT/dumpsys-usb-after.txt`. H1: put D-MOTO and D-POCO back on PC cables. `adb -s $POCO_IP:5555 usb` and `adb -s $MOTO_IP:5555 usb`.
8. On D-MOTO: `svc power stayon false`. `ps aux | grep -c "[l]ogcat"` must print 0.
9. Put every `toggle.tsv` line of the R and S runs, every `hold.tsv` line, `av.tsv`, `calls.tsv` and the `p.reading`, `p.P0` and `p.restore` lines in the results file. The dumps and captures go in the asset.

**Stop rule.** At most 15 runs: R1, R1x, R2a, R2b, R3a, R3b, S1, S2, S3, S4, at most one `x` re-run for any of them except R1x, and at most two `Rrep` repair runs. Stop at once on an abort line (validity item 9). Stop after R1x if it is not a valid control. If the host stays above 75C for 30 min, stop and mark the remaining runs UNTESTABLE (host thermal).

## 9. Do not re-run

These are settled. Delays are on D-MOTO's clock.

| Run | P0 | After the end | Delay |
|---|---|---|---|
| Round 1 U1a, U1b, U1c, U4 (clean, USB) | `-1`, `100` | Gearhead set `100` | 1811 to 1853 ms from the stop |
| Round 1 U2 (`blank`), U3 (`skip`) | none | no `setConnectionPolicy(` for the address | |
| Round 1 N1, N2a, N2b; round 3 N0p (clean, Native AA) | `100` | Gearhead set `100` | 1361 to 1448 ms from the stop |
| Round 2 U5a2; round 3 U5a4 to U5a9 (cable pull, USB) | `100` | Gearhead set `100` (RESTORED) | -202 to 460 ms from the disconnect |
| Round 2 U5b1, U5b2; round 3 N4a1, N4a2x (Android Auto force-stop) | `100` | NOT_RESTORED, stored `0` | |
| Round 2 U6a, U6b, U6r2v; round 3 N4b1, N4b2 (clean, from `0`) | `0` | Gearhead set `100` | 880 to 1864 ms from the stop |

- **The stored policy is settled for every end.** This round reads the switch, not the policy. R2 and R3 repeat ends only to read the switch.
- **`blank` stops the disable.** It is not re-run: Android Auto handles `blank` and `skip` in the same branch, and only `skip` is a candidate.
- **A restore lasts only until the next session** (round 2, U6r1b). Not a question for this round.

## 10. Report back

1. **Stage S:** the verdict of S1, S2 and S3, with the count of `ON` readings against all readings per run, `ph.disabler`, and S1's `av live` values. With them, the R1 and S4 control verdicts. This decides whether a skip setting is the fix.
2. **The grey state on D-MOTO:** for R1 to R3b, any `GREY_*` or `NOROW` reading (first and last phone time), every hold outcome with `snap_ms` and the snap-back owner, and the clearer that first gave `ON` in R3a and R3b. If nothing grey appeared, say so.
3. **Calls:** the route in R1, S1 and S4 (head unit over hands-free, phone earpiece or speaker, or the no-route line), and any abort line or bond change.

The decisive strings below are the app's own, one per line, for a mechanical `grep -F -r` against `app/src/main` on `fff96699`. Two of them are the fixed parts of composed lines. `AapService: session state ` is the fixed part of `AapService: session state disconnected`. `Media Start Request %s: session=` is the format string of `Media Start Request AUDIO:`. The action names are grepped inside the `AutomationReceiver: ` line, which prints the full action.
