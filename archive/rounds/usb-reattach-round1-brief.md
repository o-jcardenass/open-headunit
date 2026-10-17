# usb-reattach, round 1 brief

Published name on the transfer branch: `usb-reattach-round1-brief.md`. Results file: `usb-reattach-round1-results.md`. Evidence release: `rig-evidence-usb-reattach` (first round of the thread, so create it).

This is a new thread. A fix is on trial, with a baseline A/B. The round answers three questions:

1. **The trigger (the point of the round).** Does every failed USB handshake on the candidate now reach the recovery code? In the `bluetooth-audio-disabled-usb-connect` captures, only 2 of 31 failed handshakes reached the failure branch that sends the report.
2. **The ladder.** Does an AOA re-switch, and then a USB reset, make D-MOTO re-enumerate and bring Android Auto back with no hand replug?
3. **The guards.** Does the ladder stay away from a wireless dongle that answers late, and from a handshake that the user ended?

## 1. Build and baseline

- **Candidate:** branch `fix/usb-reattach` on the fork, SHA **`37bbe71e`** (`37bbe71effa84fd5a09a72b0f0f4e226d9db4f09`). Three commits on `main` `7102b428`. The branch is new on the fork, so no earlier SHA of it was briefed and no history was rewritten.
- **Baseline:** `main` at **`7102b428`** (`7102b4283666ffcc7802e49402735e3958cd51a0`, `3.5.0-beta4`). It runs R1 and R1b only.

```bash
git fetch fork fix/usb-reattach main
git worktree add ../ohu-wt-usb-reattach-37bbe71e 37bbe71effa84fd5a09a72b0f0f4e226d9db4f09
git worktree add ../ohu-wt-main-7102b428 7102b4283666ffcc7802e49402735e3958cd51a0
git -C ../ohu-wt-usb-reattach-37bbe71e rev-parse HEAD   # must print 37bbe71effa84fd5a09a72b0f0f4e226d9db4f09
git -C ../ohu-wt-main-7102b428 rev-parse HEAD           # must print 7102b4283666ffcc7802e49402735e3958cd51a0
```

- Build each with `build_hur.sh` (`HUR_DIR=<that worktree>`). `build_hur.sh` deletes the previous APK, so copy each APK out of `apks/` at once: `apks/candidate-37bbe71e.apk` and `apks/baseline-7102b428.apk`. Record both md5s as `CAND_MD5` and `BASE_MD5`. They must differ.
- Run `run_unit_tests.sh` on the candidate worktree. **Gate: 2856 tests, 0 failures.** A failure stops the round.
- **DEX identity** (template §5). The candidate must contain the class and the baseline must not:
  ```bash
  unzip -p apks/candidate-37bbe71e.apk 'classes*.dex' | strings | grep -c StaleAccessoryRecoveryPolicy   # 1 or more
  unzip -p apks/baseline-7102b428.apk 'classes*.dex' | strings | grep -c StaleAccessoryRecoveryPolicy    # 0
  ```
- `send ACTION_QUERY_STATE` must reply with a `commit` that begins `37bbe71e` on the candidate and `7102b428` on the baseline. Record each reply in the run's `.keys` file (the run driver does this).
- Install with `adb install -r -d` only, never uninstall. Back up `settings.xml` before each install and diff it after (`rig-quirks/topics/tooling.md`: an install can wipe it).

## 2. What this is and why it exists

**The fault.** A phone used as the Android Auto source over USB fails the next bring-up after a session end or a cable event. The head unit finds the phone still in accessory mode, opens it, and the handshake fails. The `bluetooth-audio-disabled-usb-connect` rounds 1 to 3 measured it on D-POCO with D-MOTO on its OTG port:

```
Found device already in accessory mode: motorola motorola edge 30 neo (VID: 18D1 PID: 2D01)
Connected have EPs
Handshake: Version request send failed (ret=-1), attempt 1   (then attempts 2 and 3, 2.35 s apart)
Handshake failed
```

Round 2 also saw a second form after a cable event: `SSLException: Unable to parse TLS packet header`. In every case only a hand replug, a USB mode change on the phone, or a Gearhead force-stop cleared it. Round 3 needed a hand re-enumeration in every one of its five pull runs.

**Why the app did nothing.** The app already had a recovery for this state (`onHandshakeFailed`, an AOA re-switch). It was wired to the `ConnectionState.Error` collector, and that collector never sees `Error` because the flow is conflated. In the old captures `Stale accessory detected` and `AOA re-enumeration` count 0 against every failure. Also, one plug-in makes exactly one handshake, so a "second failure" rule could never fire.

**What the candidate does.**

1. `AapTransport` names the failure: `TRANSPORT_ERROR` (a send or read error in the version exchange), `SSL` (the version response came back and TLS failed), `PEER_SILENT`, or `OTHER`.
2. `CommManager.startHandshake` reports each USB handshake end to `UsbLauncherManager.onUsbHandshakeEnded`. The first build put this report behind a preemption guard. The failure's own teardown sets `_connection` to null first, so the guard swallowed the report. A plan revision made the report go out from the guarded path too, unless a newer connection holds the state or the user ended the handshake. That path prints `CommManager: USB handshake failed (<KIND>) after its own teardown; reporting it to USB`.
3. A pure policy, `StaleAccessoryRecoveryPolicy`, decides a bounded ladder. It acts only on `TRANSPORT_ERROR` or `SSL`, only while a device is in accessory mode (`18D1:2D00` to `2D05`), and never while the status pill's X holds USB.
   - Step 1 is the AOA re-switch (51/52/53).
   - Step 2 is a USB reset (`libusb_reset_device` on the `UsbManager` file descriptor).
   - After a step, the app polls `UsbManager.deviceList` every 100 ms. A detach followed by a new device is a re-enumeration, and the app then calls `checkAlreadyConnected` itself. No change in 5 s means one more handshake on the same device. A detach with nothing back in 15 s means the phone left the bus.
   - After both steps, the app gives up and raises a new banner record, `STALE_USB_ACCESSORY` ("Unplug the phone, wait five seconds, and plug it back in"). A USB session that reaches SSL clears it.
   - The bound is two steps per plug-in. A ladder that gave up holds until that accessory leaves the bus.

**What nobody has measured.** A code read of AOSP says a re-switch on a phone already at `2D01` is a framework no-op. Generic Linux sources say a USB reset needs root. Neither was measured on a phone. The planning decision is to ship the ladder, the banner and the logs, and to drop any step that this round shows is dead. So R2R and R2b are measurements first. **A FAIL in R2R is a result that decides what ships, not a broken round.**

## 3. What is different about this round

**Layout.** Stage U is the same as the `bluetooth-audio-disabled-usb-connect` Stage U. Stage D is the same as `usb-version-retry` round 3 Stage A.

| Stage | Head unit | Phone or device on the OTG port | adb | Runs |
|---|---|---|---|---|
| **U** | D-POCO (`4f4027e9`), Android 15 | D-MOTO (`ZY22GC3BM4`) | both over wireless adb | R1, R1b if needed (baseline); R2, R3, R5u (candidate) |
| **D** | D-POCO, wireless adb | the Carlinkit-class dongle (idle `18d1:4ee1`, accessory `18d1:2d00`), paired to D-MOTO | D-POCO wireless, D-MOTO on its PC cable | R4p, R4w (candidate) |

**One capture holds several cycles.** Each run is one capture. Cycle `c0` brings the first session up. Cycles `c1` to `cN` each start from a live session, make the stale state, and end at the next session. Every cycle has its own markers, `<RUN>c<n>-start` and `<RUN>c<n>-end`, and is graded on its own window.

**Three ways to make the stale state.**

- **Form A:** `ACTION_DISCONNECT` with the cable left in, then 10 s, then `ACTION_CHECK_USB`. This is the plan's cycle.
- **Form B:** form A, plus a force-stop and a relaunch of the app before `ACTION_CHECK_USB`. This is what each run start did in the `bluetooth-audio-disabled-usb-connect` rounds.
- **Form P:** a hand pull of the cable mid-session and a replug after about 5 s. Round 3 of that thread reproduced the fault after every pull.

**A re-enumeration costs a permission dialog, by design.** USB permission is keyed by the bus path (template §7b). A phone that re-enumerates is a new device, so D-POCO shows `UsbPermissionActivity` (and, after a normal-mode attach, `UsbConfirmActivity`). The scripts cue H2 for each one. H2 does not count as a hand recovery. A cable action (H1) or a Gearhead force-stop does.

**The rig audio keys are a deliberate worst case.** They live on D-HU, which this round does not use. Do not write or reset them. Record D-POCO's `use-aac-audio`, `audio-latency-multiplier` and `audio-queue-capacity` as found.

**The 5 GHz WiFi Direct stall does not apply.** `usb-version-retry` round 3 saw D-POCO stop enumerating USB while a WiFi Direct session streamed. This round runs `wifi-connection-mode=0`, so no group forms.

**Expected INCONCLUSIVE or UNTESTABLE, said up front:**

- **R5 (wireless beside a failing USB accessory): UNTESTABLE.** D-HU cannot host USB (`rig-quirks/topics/tooling.md`, "No USB accessory path"). D-POCO can host USB, but the rig's only other phone, D-MOTO, is the USB phone. The arbiter side is covered by `ConnectionArbiterTest` and `ConnectionPriorityPolicyTest`. R5u keeps the user-exit half of R5.
- **R2b: INCONCLUSIVE** if step 1 recovers every failing cycle, because then step 2 never runs.
- **R3's TLS check: INCONCLUSIVE** if no cycle fails with `Unable to parse TLS packet header`. R3's trigger grade still counts.
- **R5u: INCONCLUSIVE** if no cycle has a stale handshake in flight when the user exit lands.

## 4. Hand steps, and why no verb exists

The scripts print `OPERATOR: ...`, ring the bell and append to `$OUT/hand-steps.log` (`cue`). Keep the H7 cue terminal open, as in round 3 of the earlier thread. Every wait is on a log line, never on a clock alone.

| Id | Step | Why no verb |
|---|---|---|
| H0 | Unlock D-MOTO once in Prepare if it is behind a PIN | adb has no way past a PIN (`rig-quirks/units/D-MOTO.md`) |
| H1 | Cables: move D-POCO and D-MOTO between the PC and D-POCO's OTG port; pull and replug D-MOTO when cued; plug and unplug the dongle when cued | A cable is hardware. Do not change D-MOTO's USB mode instead: that is a different lever |
| H2 | Allow a system USB dialog on D-POCO (`UsbPermissionActivity` or `UsbConfirmActivity`). **Do not tick "Always"** | A system dialog, not our app. "Always" would change the rig for every later round (§7b) |
| H7 | Before Stage U, open a terminal on the rig PC with `tail -n0 -F <OUT>/hand-steps.log` and keep it in view | A cue must reach the operator in time |

The Gearhead force-stop in the hand ladder is scripted (`adb -s $PH shell am force-stop ...`). It is not a hand step, but it counts as a hand recovery for the grades.

## 5. Settings keys

Write them with the app stopped, after a backup (template §1). D-POCO is not rooted, so use `pocoput`. The run driver writes them at the start of every run from the Prepare backup. `log-level` 2 (INFO) carries every line this round reads: each app line in section 7 is `AppLog.i`, `AppLog.w` or `AppLog.e`, and none sits behind `LOG_VERBOSE`.

| Key | Type | D-POCO | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `0` | no wireless stack beside USB |
| `log-level` | int | `2` | INFO |
| `onboarding-version` | int | `2` | no wizard over the banner check |
| `kill-on-disconnect` | boolean | `false` | keeps `MainActivity` after `ACTION_DISCONNECT` |
| `auto-connect-last-session` | boolean | `false` | only the verbs and the ladder start a connection |
| `auto-connect-single-usb` | boolean | `false` | as above |
| `auto-start-on-usb` | boolean | `false` | as above |
| `reopen-on-reconnection` | boolean | `false` | as above |
| `use-libusb` | boolean | `false` | the re-switch takes the Java `controlTransfer` path (`Sending acc start`) |
| `connection-modes` | string set | `usb,wifi` | as in the earlier thread |
| `video-profile-starvation-cap` | | delete | a run of failed bring-ups can leave it set |
| `connection-issue-stale-usb-accessory` | | delete | the banner record under test |
| `connection-issue-dismissed-at` | | delete | a dismissal hides a later record (`rig-quirks/topics/projection.md`) |
| `native-aa-wireless`, `wifi-launcher-mode` | | delete | legacy mode keys |

```bash
KEYS="int:wifi-connection-mode=0 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:auto-connect-last-session=false bool:auto-connect-single-usb=false bool:auto-start-on-usb=false bool:reopen-on-reconnection=false bool:use-libusb=false set:connection-modes=usb,wifi del:video-profile-starvation-cap del:connection-issue-stale-usb-accessory del:connection-issue-dismissed-at del:native-aa-wireless del:wifi-launcher-mode"
```

## 6. Shell setup

Make `hur-wifi-test-scripts/usb-reattach-round1/`. Copy `lib1008r3.sh` and `ohu_setkeys.py` into it from `../bluetooth-audio-disabled-usb-connect-round3/`. If `lib1008r3.sh` is gone, cut it from section 6 of `bluetooth-audio-disabled-usb-connect-round3-brief.md` on the transfer branch. Save `libreattach1.sh` below beside them. List all three in Setup notes. Source `lib1008r3.sh` first, then `libreattach1.sh`. From `lib1008r3.sh` this round uses `send`, `mark`, `cue`, `nl`, `dlg`, `th_gate`, `th_stop`, `th_report`, `apk_check`, `cap_start`, `cap_stop`, `win` and `pocoput`. If a function does not match the real line format, fix it, say so in Setup notes, and keep going. Run each stage under `flock /tmp/ohu-rig.lock`. One adb call at a time per unit; the two log captures are the only streams beside them (house rule 8). The dialog check runs inside the same foreground loop as the waits, never as a second loop.

**`libreattach1.sh`**:

```bash
# libreattach1.sh : source after lib1008r3.sh. Needs HU, PH, OUT, BASE, KEYS, WANT_MD5.
WAIT_AUTO=${WAIT_AUTO:-90}
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity

# cyc_wait <secs> <ERE> <from-line> : 0 when the pattern is in the unit capture at or after <from-line>.
# In the same loop, every 3 s, it reads D-POCO's top activity and cues H2 for a system USB dialog.
cyc_wait() { local end=$((SECONDS+$1)) next=0 d
  while [ $SECONDS -lt $end ]; do
    tail -n +"$3" "$CAP" | grep -aqE -- "$2" && return 0
    if [ $SECONDS -ge $next ]; then next=$((SECONDS+3)); d=$(dlg)
      if [ -n "$d" ]; then echo "$(date +%T.%3N) ${RUN:-} $d" >> "$OUT/dialogs.log"
        cue "H2: allow the USB dialog on D-POCO ($d). Do not tick Always"; next=$((SECONDS+8)); fi; fi
    sleep 0.5; done; return 1; }
# since <fixed-string> <from-line> : 0 when the string is in the unit capture at or after <from-line>
since() { tail -n +"$2" "$CAP" | grep -aqF -- "$1"; }
# bkey : the banner record's stamp in settings.xml, or "absent"
bkey() { local v; v=$(adb -s "$HU" shell run-as $PKG cat shared_prefs/settings.xml | grep -aoE 'connection-issue-stale-usb-accessory" value="[0-9]+' | grep -aoE '[0-9]+$')
  echo "${v:-absent}"; }
# ph_home : record D-MOTO's focus, then press HOME on it
ph_home() { adb -s "$PH" shell dumpsys window | grep -a mCurrentFocus | sed "s/^/$(date +%T) ${RUN:-} /" >> "$OUT/dialogs.log"
  adb -s "$PH" shell input keyevent KEYCODE_HOME; }

# hand_recover <cyc> : the hand ladder after a cycle with no session. 0 when a session formed.
hand_recover() { local c=$1 L
  mark "$c-hand"; L=$(nl); ph_home
  cue "H1: unplug D-MOTO from D-POCO, wait 5 s, plug it back"
  cyc_wait 120 'USB_DEVICE_ATTACHED' "$L"; sleep 8
  since 'SSL handshake complete' "$L" || send ACTION_CHECK_USB >/dev/null
  cyc_wait 90 'SSL handshake complete' "$L" && return 0
  echo -e "$c\tG_FORCE_STOP" | tee -a "$OUT/summary.tsv"; mark "$c-g"; L=$(nl)
  adb -s "$PH" shell am force-stop $GH; sleep 3
  cue "H1: unplug D-MOTO from D-POCO, wait 5 s, plug it back"
  cyc_wait 120 'USB_DEVICE_ATTACHED' "$L"; sleep 8
  since 'SSL handshake complete' "$L" || send ACTION_CHECK_USB >/dev/null
  cyc_wait 90 'SSL handshake complete' "$L"; }

# give_up_checks <cyc> : after a give-up, one more try on the same plug-in (the bound), then the banner and its stamp
give_up_checks() { local c=$1 L
  sleep 3; mark "$c-bound"; L=$(nl); send ACTION_CHECK_USB >/dev/null
  cyc_wait 30 'recovery used both steps; a replug is needed|SSL handshake complete|UsbLauncher: stale accessory .*: handshake failed \(' "$L"; sleep 3
  adb -s "$HU" shell input keyevent KEYCODE_HOME; sleep 1
  adb -s "$HU" shell am start -n $MAIN >/dev/null; sleep 4; mark "$c-banner"
  echo -e "$c\tkey_after_giveup=$(bkey)" | tee -a "$OUT/summary.tsv"; }

# settle <cyc> <from-line> : ask for USB, wait for a session or a give-up, then the checks and the hand ladder
settle() { local c=$1 L=$2
  since 'SSL handshake complete' "$L" || send ACTION_CHECK_USB >/dev/null
  cyc_wait "$WAIT_AUTO" 'SSL handshake complete|recovery used both steps; a replug is needed' "$L"
  if since 'SSL handshake complete' "$L"; then echo -e "$c\tAUTO_SSL" | tee -a "$OUT/summary.tsv"
  else
    since 'recovery used both steps; a replug is needed' "$L" && give_up_checks "$c"
    if hand_recover "$c"; then echo -e "$c\tHAND_SSL" | tee -a "$OUT/summary.tsv"
    else echo -e "$c\tNO_SSL" | tee -a "$OUT/summary.tsv"; mark "$c-end"; return 1; fi
  fi
  sleep 3; echo -e "$c\tkey_at_end=$(bkey)" | tee -a "$OUT/summary.tsv"; mark "$c-end"; }

# make_stale <cyc> <A|B|P> <from-line> : end the live session the way the form says
make_stale() { local c=$1 f=$2 L=$3 D
  case $f in
    A|B) send ACTION_DISCONNECT >/dev/null
         cyc_wait 15 'Disconnect action received.' "$L" || echo -e "$c\tno-disconnect-line" | tee -a "$OUT/summary.tsv"
         sleep 10
         if [ "$f" = B ]; then adb -s "$HU" shell am force-stop $PKG; sleep 1
           adb -s "$HU" shell am start -n $MAIN >/dev/null; sleep 3; fi ;;
    P)   cue "H1: PULL D-MOTO's cable out of D-POCO, count to five, plug it back in"
         cyc_wait 120 'USB_DEVICE_DETACHED' "$L" || { echo -e "$c\tno-detach-in-120s" | tee -a "$OUT/summary.tsv"; return; }
         D=$(tail -n +"$L" "$CAP" | grep -anF 'USB_DEVICE_DETACHED' | head -1 | cut -d: -f1)
         cyc_wait 60 'USB_DEVICE_ATTACHED' $((L + D - 1)) || echo -e "$c\tno-attach-in-60s" | tee -a "$OUT/summary.tsv"
         sleep 8 ;;
  esac; }

# ue_fire <cyc> : ask for USB, and send the user exit while the stale handshake is still sending
ue_fire() { local c=$1 L k; mark "$c-check"; L=$(nl); send ACTION_CHECK_USB >/dev/null
  k=$((SECONDS+30)); while [ $SECONDS -lt $k ]; do since 'Handshake: Version request send failed' "$L" && break; sleep 0.05; done
  send ACTION_DISCONNECT >/dev/null; mark "$c-ue"
  since 'Handshake: Version request send failed' "$L" && echo -e "$c\tue_hit" | tee -a "$OUT/summary.tsv"
  sleep 30; }

# cycle <run> <n> <A|B|P> [ue] : one cycle, from a live session to the next session
cycle() { local c="$1c$2" L
  sleep 20; mark "$c-start"; L=$(nl)
  make_stale "$c" "$3" "$L"
  if [ "$4" = ue ]; then ue_fire "$c"; L=$(nl); fi
  settle "$c" "$L"; }

# csum <cyc> : the counts for one cycle window, head unit and phone, into cycles.tsv
csum() { local c=$1
  win "$CAP" "$c" > "$OUT/$c.hwin"; win "$PCAP" "$c" > "$OUT/$c.pwin"
  grep -aE 'UsbLauncher: |CommManager: USB handshake failed|UsbAccessoryMode: USB reset|Acc start sent|\| Handshake failed$|SSL handshake complete|Found device already in accessory mode|Unable to parse TLS packet header|USB permission granted for|requesting permission|Disconnect action received|showing the connection issue banner|AutomationMarker: ' "$OUT/$c.hwin" > "$OUT/$c.ladder"
  LC_ALL=C awk -v c="$c" '
  function ms(t,  a) { split(t, a, /[:.]/); return ((a[1] * 60 + a[2]) * 60 + a[3]) * 1000 + a[4] }
  function has(s) { return index($0, s) > 0 }
  function kind() { if (match($0, /\((TRANSPORT_ERROR|SSL|PEER_SILENT|OTHER)\)/)) return substr($0, RSTART + 1, RLENGTH - 2); return "none" }
  { t = ms($2) }
  has("AutomationMarker: " c "-hand") { hand = 1 }
  has("Connected have EPs") { inflight = 1; ue = 0; tlsnow = 0; detnow = 0 }
  has("Disconnect action received.") && inflight { ue = 1 }
  has("USB Intent: ") && has("USB_DEVICE_DETACHED") { det++; if (inflight) detnow = 1 }
  has("USB Intent: ") && has("USB_DEVICE_ATTACHED") { att++ }
  has("Unable to parse TLS packet header") { tls++; tlsnow = 1 }
  has("Handshake: Version request send failed") { vfail++ }
  has("Handshake: Drained ") { drained++ }
  has("SSL Handshake: discarded a late VERSION_RESPONSE") { late++ }
  has("Found device already in accessory mode") { found++ }
  has("/OPENHU") && /\| Handshake failed$/ {
    fails++; inflight = 0
    if (ue) { uefails++; tue = t; ue = 0; next }
    if (detnow) { exempt++; detnow = 0; next }
    if (pend) { miss++; print c " MISS " pt > "/dev/stderr" }
    pend = 1; tf = t; pt = $1 " " $2; ptls = tlsnow; tlsnow = 0
    if (!hand) { fauto++; if (t1 == "") t1 = t } }
  has("SSL handshake complete") { ssl++; inflight = 0; ue = 0; if (!hand && ts == "") ts = t }
  has("after its own teardown; reporting it to USB") { tear++; if (tue != "" && t - tue <= 2000) uerep++ }
  { step = has("UsbLauncher: stale accessory ") && has(": handshake failed (")
    gu = has("recovery used both steps; a replug is needed")
    ns = has("not a stale-accessory failure, no recovery")
    rf = has("UsbLauncher: stale accessory recovery for ") }
  step { if (/RESWITCH$/) s1++; else if (/USB_RESET$/) s2++; if (gave && !hand) sag++; if (tue != "" && t - tue <= 25000) uesteps++ }
  gu { giveup++; gave = 1 }
  ns { notstale++ }
  rf { refused++ }
  step || gu || ns || rf {
    v++; k = kind(); kinds = kinds k ","
    if (pend && t - tf <= 2000) { ok++; pend = 0
      if (ptls) { if (ns || (step && k != "SSL")) tlsbad++; else tlsok++ } }
    else { extra++; print c " EXTRA " $0 > "/dev/stderr" } }
  re = has("re-enumerated the phone in"); nc = has("no re-enumeration within") && has("; trying the handshake once more")
  re && has("RESWITCH") { re1++ }
  re && has("USB_RESET") { re2++ }
  nc && has("RESWITCH") { nc1++ }
  nc && has("USB_RESET") { nc2++ }
  has("the phone left the bus after") { left++ }
  has("Acc start sent") { accstart++ }
  has("UsbAccessoryMode: USB reset issued to ") { rstok++ }
  has("UsbAccessoryMode: USB reset not issued to ") { rstno++ }
  has("Accessory-mode device has no permission (re-enumerated); requesting permission") { permreq++ }
  has("USB permission granted for ") { permok++ }
  has("/UsbNative(") { unative++ }
  tolower($0) ~ /avc: denied/ && tolower($0) ~ /usb/ { avc++ }
  has("MainActivity: showing the connection issue banner for ") && has("STALE_USB_ACCESSORY") { banner++ }
  has("the accessory that recovery gave up on has left the bus") { leftgu++ }
  has("Handshake failed on accessory device") || has("Stale accessory detected") || has("AOA re-enumeration") { olddead++ }
  has("MATCH! Starting AapService") { match_++ }
  END { if (pend) { miss++; print c " MISS " pt > "/dev/stderr" }
    dt = (t1 != "" && ts != "") ? ts - t1 : "na"
    printf "%s fails=%d fauto=%d uefails=%d exempt=%d miss=%d extra=%d ok=%d verdicts=%d kinds=%s tear=%d step1=%d step2=%d giveup=%d steps_after_giveup=%d notstale=%d refused=%d re1=%d nc1=%d re2=%d nc2=%d left=%d accstart=%d rstok=%d rstno=%d vfail=%d tls=%d tlsok=%d tlsbad=%d drained=%d late=%d found=%d ssl=%d ssl_auto=%d dt_ms=%s permreq=%d permok=%d det=%d att=%d unative=%d avc=%d banner=%d leftgu=%d uerep=%d uesteps=%d olddead=%d match=%d\n",
      c, fails, fauto, uefails, exempt, miss, extra, ok, v, kinds, tear, s1, s2, giveup, sag, notstale, refused, re1, nc1, re2, nc2, left,
      accstart, rstok, rstno, vfail, tls, tlsok, tlsbad, drained, late, found, ssl, (ts != ""), dt, permreq, permok, det, att, unative, avc,
      banner, leftgu, uerep, uesteps, olddead, match_ }' "$OUT/$c.hwin" 2>> "$OUT/misses.log" | tee -a "$OUT/cycles.tsv"
  LC_ALL=C awk -v c="$c" '
  function has(s) { return index($0, s) > 0 }
  has("RIGMARK") && has(": " c "-hand") { hand = 1 }
  has("entering USB accessory mode") { en++; if (!hand) ena++ }
  has("exited USB accessory mode") { ex++; if (!hand) exa++ }
  has("applyOemOverride usbfunctions=accessory") { oem++ }
  has("UsbDeviceManager") { udm++ }
  has("Critical error") { crit++ }
  has("/GH.") { gh++ }
  END { printf "%s ph.enter=%d ph.enter_auto=%d ph.exit=%d ph.exit_auto=%d ph.oem=%d ph.udm=%d ph.crit=%d ph.gh=%d\n", c, en, ena, ex, exa, oem, udm, crit, gh }' "$OUT/$c.pwin" | tee -a "$OUT/cycles.tsv"; }

# nfail <run> : how many of c1..c9 had a failure before any hand step
nfail() { grep -aE "^$1c[1-9] fails=" "$OUT/cycles.tsv" | grep -acvE ' fauto=0 '; }

# urun2 <run> <A|B|P> <cycles> [ue] : one Stage U run. c0 brings a session up, then the cycles, in one capture.
urun2() { local n; RUN=$1; th_gate || return 3
  apk_check || { echo -e "$RUN\tAPK_MISMATCH" | tee -a "$OUT/summary.tsv"; return 1; }
  adb -s "$HU" shell am force-stop $PKG
  pocoput "$BASE" $KEYS > "$OUT/$RUN.keys" || { echo -e "$RUN\tpocoput-failed" | tee -a "$OUT/summary.tsv"; return 1; }
  cap_start "$RUN"; mark "$RUN-start"
  adb -s "$HU" shell am start -n $MAIN >/dev/null; sleep 3
  send ACTION_QUERY_STATE >> "$OUT/$RUN.keys"
  mark "${RUN}c0-start"
  if settle "${RUN}c0" "$(nl)"; then
    for n in $(seq 1 "$3"); do
      cycle "$RUN" "$n" "$2" "$4" || { echo -e "$RUN\tSTOPPED at c$n" | tee -a "$OUT/summary.tsv"; break; }
      [ "$4" = ue ] && [ "$(grep -ac "^${RUN}c[0-9]*.ue_hit" "$OUT/summary.tsv")" -ge 2 ] && break
    done
  fi
  mark "$RUN-end"; cap_stop; th_stop
  for n in $(seq 0 "$3"); do [ -n "$(win "$CAP" "${RUN}c$n" | head -1)" ] && csum "${RUN}c$n"; done
  th_report "$RUN" | tee -a "$OUT/summary.tsv"; }

# Stage D. dplug <run> <n> : plug the dongle, hold a session 20 s, unplug.
dplug() { local c="$1c$2" L
  mark "$c-start"; L=$(nl)
  cue "H1: plug the dongle into D-POCO's OTG port"
  cyc_wait 120 'USB_DEVICE_ATTACHED' "$L" || echo -e "$c\tno-attach-in-120s" | tee -a "$OUT/summary.tsv"
  sleep 8; since 'SSL handshake complete' "$L" || send ACTION_CHECK_USB >/dev/null
  if cyc_wait 90 'SSL handshake complete' "$L"; then echo -e "$c\tAUTO_SSL" | tee -a "$OUT/summary.tsv"; sleep 20
  else echo -e "$c\tNO_SSL" | tee -a "$OUT/summary.tsv"; fi
  L=$(nl); cue "H1: unplug the dongle from D-POCO"
  cyc_wait 60 'USB_DEVICE_DETACHED' "$L"; sleep 5; mark "$c-end"; }
# dwifi <run> <n> : the dongle stays in; D-MOTO's WiFi is off for 20 s under a live session
dwifi() { local c="$1c$2" L
  mark "$c-start"; L=$(nl)
  adb -s "$PH" shell svc wifi disable
  cyc_wait 60 'USB_DEVICE_DETACHED' "$L" || echo -e "$c\tno-detach-in-60s" | tee -a "$OUT/summary.tsv"
  sleep 20; adb -s "$PH" shell svc wifi enable; sleep 2
  adb -s "$PH" shell dumpsys wifi | grep -a -m1 'Wi-Fi is' | sed "s/^/$c /" >> "$OUT/summary.tsv"
  cyc_wait 30 'SSL handshake complete' "$L" || send ACTION_CHECK_USB >/dev/null
  if cyc_wait 120 'SSL handshake complete' "$L"; then echo -e "$c\tAUTO_SSL" | tee -a "$OUT/summary.tsv"; sleep 20
  else echo -e "$c\tNO_SSL" | tee -a "$OUT/summary.tsv"; fi
  mark "$c-end"; }
# drun <run> <plug|wifi> <cycles> : one Stage D run in one capture
drun() { local n; RUN=$1; th_gate || return 3
  apk_check || { echo -e "$RUN\tAPK_MISMATCH" | tee -a "$OUT/summary.tsv"; return 1; }
  adb -s "$HU" shell am force-stop $PKG
  pocoput "$BASE" $KEYS > "$OUT/$RUN.keys" || { echo -e "$RUN\tpocoput-failed" | tee -a "$OUT/summary.tsv"; return 1; }
  cap_start "$RUN"; mark "$RUN-start"
  adb -s "$HU" shell am start -n $MAIN >/dev/null; sleep 3
  send ACTION_QUERY_STATE >> "$OUT/$RUN.keys"
  if [ "$2" = plug ]; then
    for n in $(seq 1 "$3"); do dplug "$RUN" "$n"
      if [ "$n" -eq 3 ] && ! grep -aqE "^${RUN}c[123].AUTO_SSL" "$OUT/summary.tsv"; then
        echo -e "$RUN\tUNTESTABLE no dongle session in c1 to c3" | tee -a "$OUT/summary.tsv"; break; fi
    done
  else
    mark "${RUN}c0-start"; L=$(nl); cue "H1: plug the dongle into D-POCO's OTG port and leave it in"
    cyc_wait 120 'USB_DEVICE_ATTACHED' "$L"; sleep 8; since 'SSL handshake complete' "$L" || send ACTION_CHECK_USB >/dev/null
    if cyc_wait 90 'SSL handshake complete' "$L"; then mark "${RUN}c0-end"; sleep 20
      for n in $(seq 1 "$3"); do dwifi "$RUN" "$n"; done
    else echo -e "$RUN\tUNTESTABLE no dongle session for the WiFi drops" | tee -a "$OUT/summary.tsv"; mark "${RUN}c0-end"; fi
    cue "H1: unplug the dongle from D-POCO"; sleep 10
  fi
  mark "$RUN-end"; cap_stop; th_stop
  for n in $(seq 0 "$3"); do [ -n "$(win "$CAP" "${RUN}c$n" | head -1)" ] && csum "${RUN}c$n"; done
  th_report "$RUN" | tee -a "$OUT/summary.tsv"; }
```

**Notes on the instruments.**

- `cap_stop` strips `\r`, so `csum` runs only after it. `csum` anchors the app's `Handshake failed` line at the line end: a `W/resolv` line ("TLS Handshake failed") and `Handshake failed with exception` do not match.
- A **verdict line** is one of four UsbLauncher lines: a step (`UsbLauncher: stale accessory <name>: handshake failed (<KIND>), step <n> of 2: <STEP>`), a give-up, `not a stale-accessory failure, no recovery`, or `UsbLauncher: stale accessory recovery for <name> refused: ...`. The observation lines (`re-enumerated`, `no re-enumeration`, `left the bus`) are not verdicts.
- `miss` is a failure with no verdict line within 2 s. `extra` is a verdict line with no failure 2 s before it. Each one prints with its time to `$OUT/misses.log`.
- A failure is **exempt** from the trigger grade in two cases. `uefails`: an `ACTION_DISCONNECT` landed while that handshake ran (R5u). `exempt`: the device detached while that handshake ran, so no accessory is left to recover.
- `kinds` lists the failure kind of every verdict line in order (`none` for a give-up or a refusal).
- The phone counts come from D-MOTO's capture inside the same cycle markers (`RIGMARK`). The two clocks differ, so only counts are compared across devices.

## 7. The lines that decide the runs

App lines were checked with `git grep -F` against `app/src/main` at `37bbe71e`. The lines marked "main too" are also on `7102b428`; the others exist only on the candidate. All print at INFO or above. Phone lines and framework lines were checked in the `bluetooth-audio-disabled-usb-connect` round 1 and round 2 captures. Every count is inside one cycle window, `<RUN>c<n>-start` to `<RUN>c<n>-end` (`AutomationMarker:` in `$OUT/<RUN>.hu.logcat`, `RIGMARK` in `$OUT/<RUN>.phone.logcat`), through `csum`.

| Line (fixed substring) | File | Source | Meaning |
|---|---|---|---|
| `AutomationReceiver: ` | unit | app, main too | a verb landed; a missing one voids the step |
| `AutomationMarker: ` | unit | app, main too | a marker |
| `Disconnect action received.` | unit | app, main too | `ACTION_DISCONNECT` reached the service |
| `Found device already in accessory mode` | unit | app, main too | the phone is still at `18D1:2D0x` |
| `Connected have EPs` | unit | app, main too | the endpoints opened; a handshake starts |
| `Handshake: Version request send failed` | unit | app, main too | the stale form 1 |
| `Unable to parse TLS packet header` | unit | `SSLException` text, not the app's | the stale form 2 |
| `Handshake failed` at the line end | unit | app, main too (`AapTransport.startHandshake`) | one failed handshake |
| `Handshake failed with exception` | unit | app, main too | precedes the line above on the TLS form |
| `Handshake: Drained ` | unit | app, main too | stale bytes before the version request |
| `SSL Handshake: discarded a late VERSION_RESPONSE` | unit | app, main too | the late-answer fix; expected on the dongle |
| `SSL handshake complete` | unit | app, main too | a session formed (never prefix `Handshake:`) |
| `USB Intent: ` with `USB_DEVICE_DETACHED` or `USB_DEVICE_ATTACHED` | unit | app, main too (the action name is Android's) | a detach or an attach; each prints twice |
| `Accessory-mode device has no permission (re-enumerated); requesting permission` | unit | app, main too | a re-enumerated phone asks for permission (H2) |
| `USB permission granted for ` | unit | app, main too | H2 allowed |
| `Sending acc start`, `Acc start sent` | unit | app, main too | the re-switch went out |
| `CommManager: USB handshake failed (` ... `after its own teardown; reporting it to USB` | unit | app, candidate | the report from the guarded path |
| `UsbLauncher: stale accessory ` with `: handshake failed (` | unit | app, candidate | a step started; the line ends `RESWITCH` or `USB_RESET` |
| `recovery used both steps; a replug is needed` | unit | app, candidate | give-up |
| `not a stale-accessory failure, no recovery` | unit | app, candidate | a failure the ladder does not act on |
| `UsbLauncher: stale accessory recovery for ` | unit | app, candidate | a refusal (the X, or the arbiter) |
| `re-enumerated the phone in` | unit | app, candidate | a step worked |
| `no re-enumeration within` with `; trying the handshake once more` | unit | app, candidate | no change in 5 s; one more handshake follows |
| `the phone left the bus after` | unit | app, candidate | a detach with no return in 15 s |
| `UsbAccessoryMode: USB reset issued to `, `UsbAccessoryMode: USB reset not issued to ` | unit | app, candidate | step 2 went out, or why not |
| `the accessory that recovery gave up on has left the bus` | unit | app, candidate | a new plug-in after a give-up |
| `session ended while stale accessory recovery runs` | unit | app, candidate | informational |
| `MainActivity: showing the connection issue banner for ` | unit | app, main too (`STALE_USB_ACCESSORY` is candidate only) | the banner is on screen |
| `connection-issue-stale-usb-accessory` | `settings.xml` | app, candidate | the banner record's stamp (`bkey`) |
| `Handshake failed on accessory device`, `Stale accessory detected`, `AOA re-enumeration` | unit | app, main only (`7102b428`) | the old dead recovery; expect 0 on main and 0 on the candidate |
| `MATCH! Starting AapService` | unit | app, main too | discard rule |
| `/UsbNative(`, `avc: denied` | unit | native tag, kernel audit | the reset's own error, or an SELinux denial |
| `RIGMARK` | phone | the shell `log` tag | phone markers |
| `entering USB accessory mode`, `exited USB accessory mode` | phone | Android | the phone's gadget changed state |
| `applyOemOverride usbfunctions=accessory` | phone | Android | the phone set the accessory function |
| `UsbDeviceManager` | phone | Android | any gadget change |
| `Critical error` | phone | Gearhead | session end; informational |
| `/GH.` | phone | Gearhead | any Android Auto line |

**Composed strings.** Three kinds of line join a literal and an enum name at run time, so the runs match them as two literals on one line, never as one string. The step lines are `RESWITCH re-enumerated the phone in` and `USB_RESET re-enumerated the phone in` (source `$step re-enumerated the phone in`), and `no re-enumeration within ... of RESWITCH; trying the handshake once more` with its `USB_RESET` twin (source `of $step; trying the handshake once more`). The banner line is `MainActivity: showing the connection issue banner for STALE_USB_ACCESSORY` (source `... banner for $issue`). `csum` matches the literal part and the enum name apart, and only the literal parts are in the `decisive-strings` block. `connection-issue-stale-usb-accessory` is a `settings.xml` key and also a literal in the source.

**The three string blocks at the end.** `decisive-strings` holds only strings the app prints, each a literal in `app/src` at `37bbe71e`. `decisive-strings-baseline` holds the old dead-recovery strings, which are literals only at `7102b428`: check them against the `main` worktree, not the candidate. `decisive-strings-external` holds strings the app does not print: the `SSLException` text, the logcat tags `/OPENHU`, `/UsbNative(`, `/GH.` and `RIGMARK`, the kernel audit text, and the phone's Android and Gearhead lines. Check those against the earlier thread's captures, not the source. `/UsbNative(` and `avc: denied` appear in none of the earlier thread's captures, because no reset ran there; a count of 0 for them is the expected reading, not a broken grep.

**The trigger grade (decides the defect the plan revision fixed).** For each cycle: `miss=0` and `extra=0`. Report `tear` against `fails - uefails - exempt`: it says how many reports came through the guarded path.

**What a PASS would look like if the change did nothing.** On the old wiring, every stale failure is a `miss` and `verdicts=0`. R1 on `main` must show exactly that (`miss` equal to the counted failures), which proves that the trigger grade can see a dead report. A candidate cycle with `fails=0` proves nothing about the trigger, so the trigger grade needs at least 3 counted failures across the round.

## 8. Runs

### Prepare (P), on cables

1. **Build and identity** (section 1). Record `CAND_MD5`, `BASE_MD5`, both DEX counts and the unit test count.
2. **Back up D-POCO's settings** with the app stopped: `adb -s 4f4027e9 shell am force-stop com.andrerinas.headunitrevived; adb -s 4f4027e9 shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/settings_backup_poco.xml`. Diff it against round 3's closing backup of the earlier thread if you still have it, and state the delta. Record D-POCO's three audio keys as found.
3. **USB grants:** `adb -s 4f4027e9 shell dumpsys usb | grep -i -A4 headunitrevived > $OUT/dumpsys-usb-grants-before.txt`. Report whether an "Always" grant exists for `18D1:2D01` or for the dongle. If one exists, H2 dialogs will not appear for it; say so and go on.
4. **Kernel log:** `adb -s 4f4027e9 shell dmesg | tail -3`. Record whether it is readable. If it is, the run close saves `dmesg | grep -i usb | tail -80` for each run.
5. **Install the baseline on D-POCO:** `adb -s 4f4027e9 install -r -d apks/baseline-7102b428.apk`. Diff `settings.xml` against the backup. Launch, `send ACTION_QUERY_STATE`, check `commit`, force-stop.
6. **D-MOTO:**
   ```bash
   adb -s ZY22GC3BM4 shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
   adb -s ZY22GC3BM4 shell svc power stayon true
   adb -s ZY22GC3BM4 shell dumpsys window | grep -a -E "mShowingLockscreen|mDreamingLockscreen"   # H0 if behind a PIN
   adb -s ZY22GC3BM4 shell input keyevent KEYCODE_HOME
   ```
   Record the Android Auto version (the earlier thread ran `17.9.664004`).
7. **Bonds for Stage D:** `adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -i -A30 "Bonded devices" > $OUT/moto-bonds.txt`. Record whether the dongle is in the list.
8. **D-HU:** `adb -s 27870808938846 shell am force-stop com.andrerinas.headunitrevived`. It takes no part.
9. **H7.** Ask the operator to open the cue terminal with `$OUT` written as a full path. Do not start Stage U until the operator confirms it.

### Stage U: D-POCO as head unit over USB, D-MOTO as the phone

**Switch to wireless adb**, as in the earlier thread:

```bash
adb -s 4f4027e9 tcpip 5555; adb -s ZY22GC3BM4 tcpip 5555; sleep 3
POCO_IP=$(adb -s 4f4027e9 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
MOTO_IP=$(adb -s ZY22GC3BM4 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
adb connect $POCO_IP:5555; adb connect $MOTO_IP:5555
```

Then H1: unplug D-POCO from the PC; unplug D-MOTO from the PC and plug it into D-POCO's OTG port. Check that `adb -s $POCO_IP:5555 shell getprop ro.product.model` and `adb -s $MOTO_IP:5555 shell getprop ro.product.model` both answer.

```bash
HU=$POCO_IP:5555; PH=$MOTO_IP:5555; BASE=$OUT/settings_backup_poco.xml
KEYS="(section 5)"
source ./lib1008r3.sh; source ./libreattach1.sh
```

**Valid cycle.** A cycle counts when all of these hold. A cycle that misses one is not counted; name the item in the results.

1. Its `-start` and `-end` markers are in both captures.
2. Each verb in it has its `AutomationReceiver: ` line.
3. `match=0`.
4. For forms A and B, `Disconnect action received.` is in the window. For form P, `det` is 2 or more.

#### R1. Baseline reproduction, form A, on `main` `7102b428`

```bash
WANT_MD5=$BASE_MD5; WAIT_AUTO=60
urun2 R1 A 5
nfail R1          # failing cycles among c1..c5
```

- **PASS** (the fault reproduces on `main`): `nfail R1` is **2 or more**, and in every failing cycle `ssl_auto=0`, `verdicts=0`, `tear=0`, `olddead=0` and `miss` equals `fails - uefails - exempt`. On the phone, `ph.enter_auto=0` in every failing cycle.
- **If `nfail R1` is below 2, run R1b** (form B), then grade R1 on whichever of the two reached 2:
  ```bash
  urun2 R1b B 5; nfail R1b
  ```
- **INCONCLUSIVE:** both are below 2. Then set `FORM=A` and go on: R3 makes the stale state with a cable, and R2 becomes a regression check (`extra=0`).
- Set `FORM` for R2 and R5u: `A` if R1 reached 2, else `B` if R1b reached 2, else `A`.
- **Report** per cycle: `fails`, `vfail`, `tls`, `found`, `ssl_auto`, `miss`, the cycle's `summary.tsv` outcome (`AUTO_SSL`, `HAND_SSL`, `NO_SSL`, `G_FORCE_STOP`), and `ph.enter`, `ph.exit`.

**Switch to the candidate.**

```bash
adb -s $HU shell am force-stop com.andrerinas.headunitrevived
adb -s $HU shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/settings_before_candidate.xml
adb -s $HU install -r -d apks/candidate-37bbe71e.apk
adb -s $HU shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml | diff - $OUT/settings_before_candidate.xml
WANT_MD5=$CAND_MD5; WAIT_AUTO=90
```

#### R2. The candidate, the same cycles (the point of the round)

```bash
urun2 R2 $FORM 5
```

R2 is graded three ways from the same capture.

**R2T, the trigger.** Graded over every counted cycle of R2, R3 and R5u, `c0` included.

- **PASS:** `miss=0` and `extra=0` in every cycle, and the sum of `fails - uefails - exempt` is **3 or more**.
- **FAIL:** any `miss` or `extra`. Quote each line from `misses.log` with the 10 lines before it from the cycle's `.ladder` file.
- **INCONCLUSIVE:** fewer than 3 counted failures in the round.
- **Report:** the sums of `fails`, `uefails`, `exempt`, `ok`, `tear`, and `kinds` per cycle.

**R2R, the recovery.** Graded over R2's cycles `c1` to `c5`.

- **PASS:** `nfail R2` is 2 or more. Every failing cycle has `ssl_auto=1`, its `summary.tsv` outcome is `AUTO_SSL`, `ph.enter_auto` is 1 or more, and `key_at_end` is `absent` or `0`.
- **FAIL:** a failing cycle needed the hand ladder (`HAND_SSL`, `G_FORCE_STOP` or `NO_SSL`).
- **INCONCLUSIVE:** `nfail R2` is below 2.
- **Report** per failing cycle: `step1`, `re1`, `nc1`, `step2`, `re2`, `nc2`, `left`, `accstart`, `rstok`, `rstno`, `permreq`, `permok`, `dt_ms`, `ph.exit_auto` and `ph.enter_auto`. The two numbers that matter: **re-switch re-enumerations out of `step1`**, and **USB reset re-enumerations out of `step2`**.

**R2b, the second step, the give-up and the banner.** Graded over every cycle of R2, R3 and R5u that has `step2` of 1 or more, or `giveup` of 1 or more.

- **PASS:** all of these hold.
  1. Each `USB_RESET` step line is followed within 3 s by `UsbAccessoryMode: USB reset issued to ` or `... not issued to `, and within 20 s by exactly one observation line for `USB_RESET`. Read the times in the cycle's `.ladder` file. The counts must agree: `rstok + rstno` equals `step2`, and `re2 + nc2` plus any `left` after `USB_RESET` equals `step2`.
  2. Each cycle with `giveup` of 1 or more has `steps_after_giveup=0`, and its `-bound` try added a second give-up line (`giveup` of 2 or more), so the bound held on the same plug-in.
  3. `key_after_giveup` is a number, not `absent`. In the first give-up cycle of each run, `banner` is 1 or more.
  4. The hand recovery that follows reached SSL, and `key_at_end` is then `absent` or `0`: the record cleared on its own SSL.
- **FAIL:** an item does not hold. Quote the cycle's `.ladder` lines.
- **INCONCLUSIVE:** no cycle in the round has `step2` or `giveup` above 0 (step 1 recovered every failure).
- **Report:** `avc` and `unative` for every cycle with `step2` above 0, with each line quoted. Such a line in the second after `USB reset issued to` answers the root question on this hardware. Also report the `dmesg` lines for the reset if Prepare step 4 found the kernel log readable.

#### R3. The cable form and the TLS failure, form P

```bash
urun2 R3 P 5
```

Before the run, tell the operator: "At each PULL cue, pull the cable from D-POCO, count to five, and plug it back in."

- **PASS:** every counted cycle meets R2T's per-cycle condition. Every TLS-form failure was answered as a stale failure (`tlsbad=0`). No cycle needed `G_FORCE_STOP`. And every failing cycle ends in `AUTO_SSL`, or in a give-up that R2b grades.
- **FAIL:** `tlsbad` above 0, or `G_FORCE_STOP` in a cycle.
- **INCONCLUSIVE (TLS part only):** `tls=0` in every cycle. Say so, and still report the trigger counts.
- **Report** per cycle: `tls`, `tlsok`, `tlsbad`, `drained`, `kinds`, `step1`, `re1`, `step2`, `re2`, the outcome, `ph.exit`, `ph.enter`.

#### R5u. A user exit during a stale handshake

```bash
urun2 R5u $FORM 4 ue        # stops after the 2nd cycle with ue_hit
```

In each cycle, `ue_fire` sends `ACTION_CHECK_USB`. It waits for the first `Handshake: Version request send failed` of that attempt, then sends `ACTION_DISCONNECT` at once, then waits 30 s. The stale handshake still has about 4.7 s of sends left at that point. The cycle then settles as in R2.

- **PASS:** at least 2 cycles have `ue_hit` in `summary.tsv` and `uefails` of 1 or more. In each of them, `uerep=0` (no `after its own teardown` line within 2 s of that failure) and `uesteps=0` (no step line within 25 s of it).
- **FAIL:** `uerep` or `uesteps` above 0 in a cycle with `uefails` of 1 or more.
- **INCONCLUSIVE:** fewer than 2 cycles with `uefails` of 1 or more.
- **Report** per cycle: the times of `Version request send failed`, `Disconnect action received.` and the `Handshake failed` that followed, from the `.ladder` file.

**What a PASS would look like if the change did nothing.** Without the user-exit rule, the failure that the exit causes would print `after its own teardown` and start a step within 2 s. So `uerep=0` with `uefails` of 1 or more is the measurement, and `uefails=0` is no measurement at all.

#### R5. Wireless beside a failing USB accessory

**UNTESTABLE**, pre-registered (section 3). Do not run it.

#### Closing Stage U

1. `send ACTION_EXIT`.
2. `adb -s $HU shell am start -a android.intent.action.VIEW -d headunit://exit`, then `sleep 3`, then `adb -s $HU shell am force-stop com.andrerinas.headunitrevived`.
3. Restore D-POCO's backup with `pocoput $BASE` (no spec) and read it back.
4. `adb -s $HU shell dumpsys usb | grep -i -A4 headunitrevived > $OUT/dumpsys-usb-grants-after.txt`. Diff it against the Prepare copy and report any new grant.
5. H1: unplug D-MOTO from D-POCO and put D-MOTO back on its PC cable. Leave D-POCO on wireless adb for Stage D.
6. `adb -s $MOTO_IP:5555 usb`. Check `adb -s ZY22GC3BM4 shell getprop ro.product.model` answers.

### Stage D: the dongle on D-POCO, D-MOTO as its phone

```bash
PH=ZY22GC3BM4
adb -s $PH shell svc wifi enable; adb -s $PH shell svc bluetooth enable; sleep 3
adb -s $PH shell dumpsys wifi | grep -a -m1 'Wi-Fi is'
```

#### R4p and R4w. Negative control: the dongle

```bash
drun R4p plug 10
drun R4w wifi 5
```

R4p plugs the dongle ten times with a 20 s session each time. R4w plugs it once, then turns D-MOTO's WiFi off for 20 s under a live session, five times, so that the dongle re-enumerates by itself.

- **PASS:** all of these hold.
  1. R2T's per-cycle condition (`miss=0`, `extra=0`) in every cycle.
  2. `step1=0` and `step2=0` in every cycle with `fails=0`.
  3. Every verdict line with the kind `PEER_SILENT` is a `not a stale-accessory failure, no recovery` line. Read it in the cycle's `.ladder` file.
  4. On the phone, `ph.gh` is 1 or more in every cycle that reached SSL.
- **FAIL:** an item does not hold.
- **UNTESTABLE:** the driver logged `no dongle session in c1 to c3`, or no dongle session for the WiFi drops.
- **Report** per cycle: the outcome, `fails`, `kinds`, `step1`, `late`, `found`, `ssl`. Also report the SSL count out of 10 for R4p. `usb-version-retry` round 3 had the same dongle reach SSL on 10 of 10 plugs. A step on a dongle failure of kind `TRANSPORT_ERROR` or `SSL` is allowed by design, but quote it.

#### Closing Stage D

1. `send ACTION_EXIT`, then `headunit://exit`, `sleep 3`, force-stop, as for Stage U.
2. Restore D-POCO's backup with `pocoput $BASE` and read it back.
3. H1: unplug the dongle. Put D-POCO back on its PC cable and run `adb -s $POCO_IP:5555 usb`.
4. `adb -s ZY22GC3BM4 shell svc power stayon false`.
5. `ps aux | grep -c "[l]ogcat"` must print 0.

### Stop rules

- Each run has a cycle count, and `urun2` stops a run at the first cycle that ends `NO_SSL` (`STOPPED at c<n>`). Go on to the next run.
- If two runs in a row stop at `c0` with `NO_SSL`, the rig cannot form a USB session. Stop Stage U and mark the remaining Stage U runs UNTESTABLE.
- The host thermal rules of `rig-quirks/topics/tooling.md` apply; `urun2` and `drun` gate on them.

## 9. Do not re-run

- What Android Auto does to the phone's A2DP setting over USB and Native AA: settled in `bluetooth-audio-disabled-usb-connect` rounds 1 to 3. This round does not touch it.
- The arbiter's USB-over-wireless preemption and the late `VERSION_RESPONSE` discard: settled in `usb-version-retry` round 3.
- Two permission prompts per connect: structural (template §7b). Report the dialogs; do not grade their count.

## 10. Report back

The three numbers that decide what ships:

1. **The trigger:** verdict lines out of counted failed USB handshakes, over R2, R3 and R5u, with how many came through `after its own teardown`. The plan needs every one.
2. **The ladder:** re-switch re-enumerations out of re-switch steps, and USB reset re-enumerations out of USB reset steps, each with the `avc` and `UsbNative` lines. A step with no re-enumeration in any attempt is a candidate to drop.
3. **The guards:** steps fired on dongle cycles with no failure (must be 0), and steps fired after a user exit (must be 0).

Put the captures in `usb-reattach-round1-captures.zip` on the release `rig-evidence-usb-reattach`, and quote its sha256 in the results file. Include `cycles.tsv`, `summary.tsv`, `misses.log`, `dialogs.log`, `hand-steps.log` and every `.ladder` file.
