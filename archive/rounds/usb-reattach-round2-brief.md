# usb-reattach, round 2 brief

Published name on the transfer branch: `usb-reattach-round2-brief.md`. Results file: `usb-reattach-round2-results.md`. Evidence: add the asset `usb-reattach-round2-captures.zip` to the existing release `rig-evidence-usb-reattach`. Do not make a new release.

This round tests the same build as round 1. It adds no new fix. It measures the three things that round 1 left open, because the next plan needs them:

1. **The control arm (the point of the round).** Does a plain second handshake, after the same pause, recover the stale phone on `main`, with no AOA re-switch before it? Round 1 cannot separate the re-switch from the retry that follows it.
2. **The USB reset.** Does the reset re-enumerate D-MOTO every time? Round 1 has one sample (313 ms).
3. **The give-up path.** Do the give-up line, the bound on the same plug-in, the `STALE_USB_ACCESSORY` banner and its clearing work on hardware? Round 1 never reached a give-up.

The whole round runs on one cable layout and needs no hand step in the normal case.

## 1. Build and baseline

- **Candidate:** branch `fix/usb-reattach` on the fork, SHA **`37bbe71e`** (`37bbe71effa84fd5a09a72b0f0f4e226d9db4f09`). This is the same SHA as round 1. No history was rewritten.
- **Baseline:** `main` at **`7102b428`** (`7102b4283666ffcc7802e49402735e3958cd51a0`). It runs RC5, RCK and RC1.

**Reuse the round 1 APKs.** Check their md5s first:

```bash
md5sum apks/candidate-37bbe71e.apk   # must print 4647bf675bf59119b34c5ee7dde72fc6
md5sum apks/baseline-7102b428.apk    # must print dd8653ee2c5397c691645eae323d28cb
```

If a file is missing or its md5 differs, build it again from section 1 of `usb-reattach-round1-brief.md` (worktrees at the two SHAs above, `build_hur.sh`), record the new md5, and run the DEX check from that section. Set `CAND_MD5` and `BASE_MD5` to the md5s you use.

Do not run the unit tests. Round 1 R0 ran them on this SHA: 2856 tests, 0 failures.

`send ACTION_QUERY_STATE` must reply with a `commit` that begins `37bbe71e` on the candidate and `7102b428` on the baseline. The run drivers record each reply in the run's `.keys` file.

Install with `adb install -r -d` only. Back up `settings.xml` before each install and diff it after.

## 2. What this is and why it exists

**The fault.** A phone used as the Android Auto source over USB fails the next bring-up after a session end or a cable event. The head unit finds the phone still in accessory mode (`18D1:2D01`), opens it, and the handshake fails with `Handshake: Version request send failed` (kind `TRANSPORT_ERROR`) or with `Unable to parse TLS packet header` (kind `SSL`). On `main` nothing tries again, so only a hand replug, a USB mode change on the phone, or a Gearhead force-stop cleared it.

**What the candidate does.** After a failed handshake on an accessory-mode device, `StaleAccessoryRecoveryPolicy` decides a bounded ladder:

- Step 1 is the AOA re-switch (51/52/53).
- Step 2 is a USB reset (`libusb_reset_device` on the `UsbManager` file descriptor).
- After each step the app polls the device list for 5 s. A re-enumeration makes it call `checkAlreadyConnected`. No change makes it try the handshake once more on the same device.
- After both steps it gives up, logs `recovery used both steps; a replug is needed` and raises the `STALE_USB_ACCESSORY` banner record. A USB handshake that reaches SSL clears the record.
- A ladder that gave up holds until that device leaves the bus.

**What round 1 measured** (`usb-reattach-round1-results.md`, `usb-reattach-round1-addendum.md`):

- Every failed USB handshake reached the ladder (R2T, 13 counted failures, `miss=0`, `extra=0`).
- The re-switch re-enumerated D-MOTO in **0 of 6** steps. In R2 c1 to c5 the app logged `no re-enumeration within ... of RESWITCH; trying the handshake once more`, and that second handshake reached SSL with no re-enumeration on the phone (`ph.enter_auto=0`).
- The USB reset re-enumerated D-MOTO in **1 of 1** step (R2 c0, 313 ms), on an unrooted D-POCO with no SELinux denial.
- Recovery took about 17.9 s by the retry and 10.6 s by the reset. A hand replug took about 0.6 s. Of the 17.9 s, the 5 s wait after the re-switch paid off 0 times.
- No cycle reached a give-up, so the bound, the banner and its clearing are unmeasured.

**Why this round.** The next plan must decide what step 1 is: a plain retry after a pause, the USB reset, or the re-switch as now. That decision needs three numbers. First, does a plain retry on `main` recover the phone as often as the candidate's retry did (RC5)? Second, does the pause matter (RC1)? Third, how reliable is the reset, and does a plain retry also clear the TLS form (R2K, RCK)? RG then measures the give-up path on hardware.

## 3. What is different about this round

**One layout, no Stage D.** D-POCO is the head unit on wireless adb, and D-MOTO is the phone on D-POCO's OTG port for the whole round. D-HU and the dongle take no part. The dongle runs (R4p, R4w) are settled.

**Two ways to make the stale state, both scripted.**

- **Form A:** `ACTION_DISCONNECT` with the cable left in, then 10 s, then `ACTION_CHECK_USB`. Round 1 R1 and R2 used it. It gives the `TRANSPORT_ERROR` form.
- **Form K (new):** with a live session, `am force-stop` the app on D-POCO, relaunch it, then `ACTION_CHECK_USB`. Every run start in round 1 did this by accident (`c0` of R1, R2, R3 and R5u), and each one failed twice with the `SSL` form. In R2 c0 and R3 c0 the candidate needed step 2 for it. So form K is the way to get USB reset samples.

**Recovery between cycles is scripted, not a cable.** Round 1 recovered every baseline failure with a hand replug. This round replaces that with three steps in order. The cable is the last one and should not be needed:

1. `am force-stop com.google.android.projection.gearhead` on D-MOTO, then `ACTION_CHECK_USB`. The original report names a Gearhead force-stop as a clearing action.
2. `svc usb setFunctions mtp` on D-MOTO. This re-enumerates the phone's USB gadget from the phone side, which is what a replug does.
3. Cue H1: a hand replug.

Each recovery logs which step worked (`REC=GEARHEAD_STOP`, `REC=PHONE_GADGET` or `REC=CABLE`). All three count as a hand recovery for the grades, because none is the app's own work.

**RG disables Gearhead on D-MOTO for about two minutes.** This is the only way to make both ladder steps fail on purpose: with no Android Auto app on the phone, no reader opens the accessory, and every handshake fails. `pm disable-user` is reversible with `pm enable`, and it does not clear Gearhead's data. This brief authorizes it. The closing section re-enables Gearhead whatever happens. `rig-quirks/topics/lifecycle.md` says a disable does not kill running processes and `dumpsys package` misreads the state, so RG force-stops Gearhead after the disable and checks with `pm list packages -d`. `rig-quirks/topics/gearhead.md` says a disable can be refused. If it is, RG is UNTESTABLE.

**The operator's "USB controlled by" step.** Round 1 found that D-MOTO needs "USB controlled by: This device" set by hand after each plug into D-POCO. This round plugs D-MOTO in once, in Prepare. Only a cable recovery (H1) needs it again.

**D-POCO's battery.** D-POCO powers D-MOTO over OTG and cannot charge while it does. Charge D-POCO to 90% or more before Stage U. Each run checks the level first and stops the round at 25% or below.

**The rig audio keys are a deliberate worst case.** They live on D-HU, which this round does not use. Do not write or reset them.

**Expected INCONCLUSIVE or UNTESTABLE, said up front:**

- **RC5, RCK, RC1: INCONCLUSIVE** if fewer than 3 cycles fail before the retry. Round 1 R1 failed in 4 of 5 cycles, so this is not expected.
- **R2K, reset part: INCONCLUSIVE** if no cycle reaches step 2 (step 1's retry recovered every cycle).
- **RG: UNTESTABLE** if `pm disable-user` is refused. **INCONCLUSIVE** if the failures with Gearhead down are `PEER_SILENT`, because the ladder does not act on that kind.
- **R5 (wireless beside a failing USB accessory):** still UNTESTABLE, for the reason in round 1. Not run.

## 4. Hand steps

There is one batched request before the first run (Prepare step 8). After it, the round needs no hand step unless a fallback below fires. Keep the cue terminal open: the scripts print `OPERATOR: ...`, ring the bell and append to `$OUT/hand-steps.log`.

| Id | Step | When | Why no verb |
|---|---|---|---|
| H0 | Unlock D-MOTO if it is behind a PIN | Prepare only | adb has no way past a PIN (`rig-quirks/units/D-MOTO.md`) |
| H1 | Plug D-MOTO into D-POCO's OTG port, then set "USB controlled by" to "This device" on D-MOTO | Prepare, and only as the last recovery step | A cable is hardware. The role setting is a system screen on the phone |
| H2 | Allow a system USB dialog on D-POCO (`UsbPermissionActivity` or `UsbConfirmActivity`). **Do not tick "Always"** | Fallback only. Round 1 needed none | A system dialog, not our app. "Always" changes the rig for later rounds (template §7b) |
| H3 | Allow a dialog on D-MOTO that asks to open Android Auto for the USB accessory | Fallback only, in RG after Gearhead is enabled again | A system dialog on the phone |
| H7 | Open a terminal on the rig PC with `tail -n0 -F <OUT>/hand-steps.log` and keep it in view | Prepare | A cue must reach the operator in time |

## 5. Settings keys

The same keys as round 1. Write them with the app stopped, after a backup (template §1). D-POCO is not rooted, so use `pocoput`. The run drivers write them at the start of every run from the Prepare backup. `log-level` 2 (INFO) carries every app line this round reads: each one is `AppLog.i`, `AppLog.w` or `AppLog.e`, and none sits behind `LOG_VERBOSE`. `ACTION_LOG_MARKER` is not gated on either build.

| Key | Type | D-POCO | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `0` | no wireless stack beside USB |
| `log-level` | int | `2` | INFO |
| `onboarding-version` | int | `2` | no wizard over the banner check |
| `kill-on-disconnect` | boolean | `false` | keeps `MainActivity` after `ACTION_DISCONNECT` |
| `auto-connect-last-session` | boolean | `false` | only the verbs and the ladder start a connection; on, it adds a reconnect 3 s after each failure and spoils the control arm |
| `auto-connect-single-usb` | boolean | `false` | as above |
| `auto-start-on-usb` | boolean | `false` | as above |
| `reopen-on-reconnection` | boolean | `false` | as above |
| `use-libusb` | boolean | `false` | the re-switch takes the Java `controlTransfer` path (`Sending acc start`) |
| `connection-modes` | string set | `usb,wifi` | as in round 1 |
| `video-profile-starvation-cap` | | delete | a run of failed bring-ups can leave it set |
| `connection-issue-stale-usb-accessory` | | delete | the banner record under test |
| `connection-issue-dismissed-at` | | delete | a dismissal hides a later record (`rig-quirks/topics/projection.md`) |
| `native-aa-wireless`, `wifi-launcher-mode` | | delete | legacy mode keys |

```bash
KEYS="int:wifi-connection-mode=0 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:auto-connect-last-session=false bool:auto-connect-single-usb=false bool:auto-start-on-usb=false bool:reopen-on-reconnection=false bool:use-libusb=false set:connection-modes=usb,wifi del:video-profile-starvation-cap del:connection-issue-stale-usb-accessory del:connection-issue-dismissed-at del:native-aa-wireless del:wifi-launcher-mode"
```

## 6. Shell setup

Make `hur-wifi-test-scripts/usb-reattach-round2/`. Copy `lib1008r3.sh`, `ohu_setkeys.py` and `libreattach1.sh` into it from `../usb-reattach-round1/`. If `libreattach1.sh` is gone, cut it from section 6 of `usb-reattach-round1-brief.md` on the transfer branch. Save `libreattach2.sh` below beside them, and list all four in Setup notes.

Source them in this order: `lib1008r3.sh`, then `libreattach1.sh`, then `libreattach2.sh`. `libreattach2.sh` replaces two round 1 functions (`hand_recover`, `make_stale`) and adds the control-arm and RG drivers. Everything else is round 1's, unchanged: `send`, `mark`, `cue`, `nl`, `dlg`, `th_gate`, `th_stop`, `th_report`, `apk_check`, `cap_start`, `cap_stop`, `win`, `pocoput`, `cyc_wait`, `since`, `bkey`, `ph_home`, `give_up_checks`, `settle`, `cycle`, `csum`, `nfail` and `urun2`.

Run the stage under `flock /tmp/ohu-rig.lock`. One adb call at a time per unit. The two log captures are the only streams beside them (house rule 8).

**`libreattach2.sh`**:

```bash
# libreattach2.sh : source after lib1008r3.sh and libreattach1.sh. Needs HU, PH, OUT, BASE, KEYS, WANT_MD5.
GH=${GH:-com.google.android.projection.gearhead}

# keep round 1's make_stale as make_stale_r1, then add form K
eval "$(declare -f make_stale | sed '1s/^make_stale/make_stale_r1/')"
make_stale() { local c=$1 f=$2 L=$3
  if [ "$f" = K ]; then
    adb -s "$HU" shell am force-stop $PKG; sleep 1
    adb -s "$HU" shell am start -n $MAIN >/dev/null; sleep 3
  else make_stale_r1 "$c" "$f" "$L"; fi; }

# bat_gate : D-POCO's battery level into summary.tsv; false at 25% or below
bat_gate() { local b; b=$(adb -s "$HU" shell dumpsys battery | grep -a -m1 'level:' | grep -aoE '[0-9]+')
  echo -e "${RUN:-}\tbattery=${b:-unknown}" | tee -a "$OUT/summary.tsv"; [ "${b:-0}" -gt 25 ]; }

# hand_recover <cyc> : scripted recovery first, the cable last. 0 when a session formed.
hand_recover() { local c=$1 L
  mark "$c-hand"; L=$(nl)
  adb -s "$PH" shell am force-stop $GH; sleep 3
  send ACTION_CHECK_USB >/dev/null
  if cyc_wait 45 'SSL handshake complete' "$L"; then echo -e "$c\tREC=GEARHEAD_STOP" | tee -a "$OUT/summary.tsv"; return 0; fi
  mark "$c-gadget"; L=$(nl)
  adb -s "$PH" shell svc usb setFunctions mtp
  cyc_wait 20 'USB_DEVICE_ATTACHED' "$L"; sleep 8
  since 'SSL handshake complete' "$L" || send ACTION_CHECK_USB >/dev/null
  if cyc_wait 60 'SSL handshake complete' "$L"; then echo -e "$c\tREC=PHONE_GADGET" | tee -a "$OUT/summary.tsv"; return 0; fi
  mark "$c-cable"; L=$(nl); ph_home
  cue "H1: unplug D-MOTO from D-POCO, wait 5 s, plug it back, then set USB controlled by to This device on D-MOTO"
  cyc_wait 180 'USB_DEVICE_ATTACHED' "$L"; sleep 8
  since 'SSL handshake complete' "$L" || send ACTION_CHECK_USB >/dev/null
  if cyc_wait 90 'SSL handshake complete' "$L"; then echo -e "$c\tREC=CABLE" | tee -a "$OUT/summary.tsv"; return 0; fi
  return 1; }

# fcount <from-line> : failed handshakes in the unit capture at or after <from-line>
fcount() { tail -n +"$1" "$CAP" | grep -acP 'OPENHU.*\| Handshake failed\r?$'; }

# ctl_settle <cyc> <from-line> <pause-s> <tries> : ask for USB; after each failure wait <pause-s>, then ask again, up to <tries> times
ctl_settle() { local c=$1 L=$2 p=$3 n=$4 i k F=0
  since 'SSL handshake complete' "$L" || send ACTION_CHECK_USB >/dev/null
  for i in $(seq 1 "$n"); do
    k=$((SECONDS+60))
    while [ $SECONDS -lt $k ]; do
      since 'SSL handshake complete' "$L" && break 2
      F=$(fcount "$L"); [ "$F" -ge "$i" ] && break
      sleep 0.2; done
    [ "$F" -ge "$i" ] || break
    sleep "$p"; mark "$c-retry$i"; send ACTION_CHECK_USB >/dev/null
  done
  cyc_wait 30 'SSL handshake complete' "$L"
  if since 'SSL handshake complete' "$L"; then echo -e "$c\tAUTO_SSL" | tee -a "$OUT/summary.tsv"
  elif hand_recover "$c"; then echo -e "$c\tHAND_SSL" | tee -a "$OUT/summary.tsv"
  else echo -e "$c\tNO_SSL" | tee -a "$OUT/summary.tsv"; mark "$c-end"; return 1; fi
  sleep 3; mark "$c-end"; }

# rsum <cyc> : the retries of one control cycle, from the window csum wrote
rsum() { local c=$1
  LC_ALL=C awk -v c="$c" '
  function ms(t,  a) { split(t, a, /[:.]/); return ((a[1] * 60 + a[2]) * 60 + a[3]) * 1000 + a[4] }
  function has(s) { return index($0, s) > 0 }
  { t = ms($2) }
  has("AutomationMarker: " c "-hand") { hand = 1 }
  has("/OPENHU") && /\| Handshake failed$/ { tf = t; if (nr) after++ }
  has("AutomationMarker: " c "-retry") { armed = 1; next }
  armed && has("AutomationReceiver: ") && has("ACTION_CHECK_USB") { armed = 0; nr++; tr = t; pz = pz (t - tf) ","; next }
  nr && !ranx[nr] && has("Found device already in accessory mode") && t - tr <= 3000 { ranx[nr] = 1; ran++ }
  nr && !hand && !got && has("SSL handshake complete") { got = 1; rs = t - tr }
  END { printf "%s retries=%d retries_ran=%d fails_after_retry=%d retry_ssl=%d retry_to_ssl_ms=%s pause_ms=%s\n",
    c, nr, ran, after, got, (got ? rs : "na"), pz }' "$OUT/$c.hwin" | tee -a "$OUT/cycles.tsv"; }

# crun <run> <A|K> <cycles> <pause-s> <tries> : one control run on the baseline, in one capture
crun() { local n c L; RUN=$1; th_gate || return 3; bat_gate || return 4
  apk_check || { echo -e "$RUN\tAPK_MISMATCH" | tee -a "$OUT/summary.tsv"; return 1; }
  adb -s "$HU" shell am force-stop $PKG
  pocoput "$BASE" $KEYS > "$OUT/$RUN.keys" || { echo -e "$RUN\tpocoput-failed" | tee -a "$OUT/summary.tsv"; return 1; }
  cap_start "$RUN"; mark "$RUN-start"
  adb -s "$HU" shell am start -n $MAIN >/dev/null; sleep 3
  send ACTION_QUERY_STATE >> "$OUT/$RUN.keys"
  mark "${RUN}c0-start"
  if settle "${RUN}c0" "$(nl)"; then
    for n in $(seq 1 "$3"); do c="${RUN}c$n"
      sleep 20; mark "$c-start"; L=$(nl)
      make_stale "$c" "$2" "$L"; L=$(nl)
      ctl_settle "$c" "$L" "$4" "$5" || { echo -e "$RUN\tSTOPPED at c$n" | tee -a "$OUT/summary.tsv"; break; }
    done
  fi
  mark "$RUN-end"; cap_stop; th_stop
  for n in $(seq 0 "$3"); do [ -n "$(win "$CAP" "${RUN}c$n" | head -1)" ] && { csum "${RUN}c$n"; rsum "${RUN}c$n"; }; done
  th_report "$RUN" | tee -a "$OUT/summary.tsv"; }

# pcount <from-label> <to-label> <fixed> : lines with <fixed> in the phone capture between two RIGMARK labels
pcount() { LC_ALL=C awk -v a=": $1" -v b=": $2" -v s="$3" '
  index($0, "RIGMARK") && index($0, a) { on = 1; next }
  index($0, "RIGMARK") && index($0, b) { on = 0 }
  on && index($0, s) { n++ } END { print n + 0 }' "$PCAP"; }

# rgrun : RG on the candidate. Gearhead down on D-MOTO, the ladder runs out, then the bound, the banner and the clearing.
rgrun() { local L R c=RGc1; RUN=RG; th_gate || return 3; bat_gate || return 4
  apk_check || { echo -e "$RUN\tAPK_MISMATCH" | tee -a "$OUT/summary.tsv"; return 1; }
  adb -s "$HU" shell am force-stop $PKG
  pocoput "$BASE" $KEYS > "$OUT/$RUN.keys" || { echo -e "$RUN\tpocoput-failed" | tee -a "$OUT/summary.tsv"; return 1; }
  cap_start "$RUN"; mark "$RUN-start"
  adb -s "$HU" shell am start -n $MAIN >/dev/null; sleep 3
  send ACTION_QUERY_STATE >> "$OUT/$RUN.keys"
  mark "${RUN}c0-start"
  if settle "${RUN}c0" "$(nl)"; then
    sleep 20; mark "$c-start"; L=$(nl)
    send ACTION_DISCONNECT >/dev/null
    cyc_wait 15 'Disconnect action received.' "$L" || echo -e "$c\tno-disconnect-line" | tee -a "$OUT/summary.tsv"
    sleep 3; mark "$c-dis"
    R=$(adb -s "$PH" shell pm disable-user --user 0 $GH 2>&1 | tr -d '\r' | tr '\n' ' ')
    echo -e "$c\tdisable: $R" | tee -a "$OUT/summary.tsv"
    adb -s "$PH" shell am force-stop $GH; sleep 3
    echo -e "$c\tgh_disabled=$(adb -s "$PH" shell pm list packages -d | grep -acF "$GH") gh_procs=$(adb -s "$PH" shell ps -A | grep -acF "$GH")" | tee -a "$OUT/summary.tsv"
    if [ "$(adb -s "$PH" shell pm list packages -d | grep -acF "$GH")" -ge 1 ]; then
      L=$(nl); send ACTION_CHECK_USB >/dev/null
      cyc_wait 120 'recovery used both steps; a replug is needed|SSL handshake complete' "$L"
      if since 'recovery used both steps; a replug is needed' "$L"; then give_up_checks "$c"
      else echo -e "$c\tno-giveup-in-120s" | tee -a "$OUT/summary.tsv"; fi
    else echo -e "$c\tUNTESTABLE gearhead not disabled" | tee -a "$OUT/summary.tsv"; fi
    mark "$c-restore"
    adb -s "$PH" shell pm enable $GH | tr -d '\r' | sed "s/^/$c\t/" | tee -a "$OUT/summary.tsv"
    echo -e "$c\tgh_disabled_after=$(adb -s "$PH" shell pm list packages -d | grep -acF "$GH")" | tee -a "$OUT/summary.tsv"
    if hand_recover "$c"; then echo -e "$c\tHAND_SSL" | tee -a "$OUT/summary.tsv"
    else ph_home; cue "H3: if D-MOTO shows a dialog about Android Auto and the USB accessory, allow it"
      L=$(nl); sleep 20; send ACTION_CHECK_USB >/dev/null
      if cyc_wait 60 'SSL handshake complete' "$L"; then echo -e "$c\tHAND_SSL_H3" | tee -a "$OUT/summary.tsv"
      else echo -e "$c\tNO_SSL" | tee -a "$OUT/summary.tsv"; fi; fi
    sleep 3; echo -e "$c\tkey_at_end=$(bkey)" | tee -a "$OUT/summary.tsv"; mark "$c-end"
  fi
  mark "$RUN-end"; cap_stop; th_stop
  for n in 0 1; do [ -n "$(win "$CAP" "${RUN}c$n" | head -1)" ] && csum "${RUN}c$n"; done
  echo -e "$c\tph_gh_while_down=$(pcount "$c-dis" "$c-restore" '/GH.')" | tee -a "$OUT/summary.tsv"
  th_report "$RUN" | tee -a "$OUT/summary.tsv"; }
```

**Notes on the instruments.**

- `csum` (round 1) gives every per-cycle count this brief names: `fails`, `fauto`, `miss`, `extra`, `kinds`, `step1`, `step2`, `re1`, `nc1`, `re2`, `nc2`, `left`, `rstok`, `rstno`, `giveup`, `steps_after_giveup`, `banner`, `leftgu`, `ssl_auto`, `dt_ms`, `avc`, `unative`, `match`, and on the phone `ph.enter`, `ph.enter_auto`, `ph.exit`, `ph.gh`. Section 6 of the round 1 brief defines each one.
- `rsum` adds the control-arm counts. `retries` is the number of retry verbs that landed. `retries_ran` is how many of them produced `Found device already in accessory mode` within 3 s, which proves that the retry started a handshake. `retry_ssl=1` means a session formed after a retry and before any recovery step. `pause_ms` is the time from the failure before each retry to that retry's `AutomationReceiver: ` line, so it is the real pause, not the `sleep`.
- The `-hand` marker starts every recovery. All counts with "auto" in the name stop at it, so a recovery never counts as the app's own work.
- `fcount` reads the live capture, which still carries `\r`. That is why it uses `grep -P` with `\r?$`. `csum` and `rsum` read the window files after `cap_stop` stripped `\r`.
- The phone counts come from D-MOTO's capture inside the same markers (`RIGMARK`). The two clocks differ, so compare only counts across devices.

## 7. The lines that decide the runs

App lines were checked with `git grep -F` against `app/src` at `37bbe71e`, and the "main too" lines also at `7102b428`. All print at INFO or above. The phone and framework lines were checked in the round 1 captures. Every count is inside one cycle window, `<RUN>c<n>-start` to `<RUN>c<n>-end` (`AutomationMarker:` in `$OUT/<RUN>.hu.logcat`, `RIGMARK` in `$OUT/<RUN>.phone.logcat`), through `csum` and `rsum`. RG's `ph_gh_while_down` uses the window `RGc1-dis` to `RGc1-restore` in the phone capture.

| Line (fixed substring) | File | Source | Meaning |
|---|---|---|---|
| `AutomationReceiver: ` | unit | app, main too | a verb landed; a missing one voids the step |
| `ACTION_CHECK_USB` | unit | app, main too (the action name, on the receiver line) | the USB check verb |
| `AutomationMarker: ` | unit | app, main too | a marker |
| `Disconnect action received.` | unit | app, main too | `ACTION_DISCONNECT` reached the service |
| `Found device already in accessory mode` | unit | app, main too | the phone is still at `18D1:2D0x`; a handshake starts on it |
| `Connected have EPs` | unit | app, main too | the endpoints opened |
| `Handshake: Version request send failed` | unit | app, main too | the `TRANSPORT_ERROR` form |
| `Unable to parse TLS packet header` | unit | `SSLException` text, not the app's | the `SSL` form |
| `Handshake failed` at the line end, tag `OPENHU` | unit | app, main too (`AapTransport`) | one failed handshake |
| `Handshake failed with exception` | unit | app, main too | precedes the line above on the `SSL` form |
| `SSL handshake complete` | unit | app, main too | a session formed (never prefix `Handshake:`) |
| `USB Intent: ` with `USB_DEVICE_DETACHED` or `USB_DEVICE_ATTACHED` | unit | app, main too | a detach or an attach |
| `Accessory-mode device has no permission (re-enumerated); requesting permission` | unit | app, main too | a re-enumerated phone asks for permission (H2) |
| `Acc start sent` | unit | app, main too | the re-switch went out |
| `UsbLauncher: stale accessory ` with `: handshake failed (` | unit | app, candidate | a ladder step started; the line ends `RESWITCH` or `USB_RESET` |
| `recovery used both steps; a replug is needed` | unit | app, candidate | give-up |
| `not a stale-accessory failure, no recovery` | unit | app, candidate | the ladder does not act on this failure |
| `re-enumerated the phone in` | unit | app, candidate | a step re-enumerated the phone |
| `no re-enumeration within` with `; trying the handshake once more` | unit | app, candidate | no change in 5 s; one more handshake follows |
| `UsbAccessoryMode: USB reset issued to `, `UsbAccessoryMode: USB reset not issued to ` | unit | app, candidate | step 2 went out, or why not |
| `the accessory that recovery gave up on has left the bus` | unit | app, candidate | the latch ended |
| `MainActivity: showing the connection issue banner for ` with `STALE_USB_ACCESSORY` | unit | app (the enum name is candidate only) | the banner is on screen |
| `connection-issue-stale-usb-accessory` | `settings.xml` | app, candidate | the banner record's stamp (`bkey`) |
| `MATCH! Starting AapService` | unit | app, main too | discard rule |
| `RIGMARK` | phone | the shell `log` tag | phone markers |
| `entering USB accessory mode` | phone | Android | the phone's gadget entered accessory mode (`ph.enter`) |
| `/GH.` | phone | Gearhead | any Android Auto line (`ph.gh`, `ph_gh_while_down`) |

**Composed strings.** The step and observation lines join a literal and an enum name at run time (`$step re-enumerated the phone in`, `of $step; trying the handshake once more`, `... banner for $issue`). The scripts match the literal part and the enum name apart, and only the literal parts are in the `decisive-strings` block.

**The string blocks at the end.** `decisive-strings` holds only strings the app prints, each a literal in `app/src` at `37bbe71e`. `decisive-strings-baseline` holds the old dead-recovery strings that `csum` counts as `olddead`. They are literals only at `7102b428`, so check them against the baseline, not the candidate. `decisive-strings-external` holds strings the app does not print: the `SSLException` text, the logcat tags, the kernel audit text and the phone's lines. Check those against the round 1 captures, not the source.

## 8. Runs

Estimated time: Prepare 20 min, RC5 12 min, RCK 10 min, RC1 8 min, R2K 10 min, RG 8 min, Closing 5 min. **Round total: about 75 min**, plus D-POCO's charge before Prepare.

### Prepare (P), on cables

1. **APKs** (section 1). Record `CAND_MD5` and `BASE_MD5`.
2. **D-POCO battery:** `adb -s 4f4027e9 shell dumpsys battery | grep -a -m1 level`. Charge to 90% or more on the PC cable before step 9. Record the level.
3. **Back up D-POCO's settings** with the app stopped: `adb -s 4f4027e9 shell am force-stop com.andrerinas.headunitrevived; adb -s 4f4027e9 shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/settings_backup_poco.xml`. Diff it against round 1's `settings_backup_poco.xml` and state the delta.
4. **USB grants:** `adb -s 4f4027e9 shell dumpsys usb | grep -i -A4 headunitrevived > $OUT/dumpsys-usb-grants-before.txt`. Report any "Always" grant.
5. **Install the baseline on D-POCO:** `adb -s 4f4027e9 install -r -d apks/baseline-7102b428.apk`. Diff `settings.xml` against the backup. Launch, `send ACTION_QUERY_STATE`, check `commit`, force-stop.
6. **D-MOTO:**
   ```bash
   adb -s ZY22GC3BM4 shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
   adb -s ZY22GC3BM4 shell pm list packages -d | grep -acF com.google.android.projection.gearhead   # must print 0
   adb -s ZY22GC3BM4 shell svc usb getFunctions
   adb -s ZY22GC3BM4 shell svc power stayon true
   adb -s ZY22GC3BM4 shell dumpsys window | grep -a -E "mShowingLockscreen|mDreamingLockscreen"
   adb -s ZY22GC3BM4 shell input keyevent KEYCODE_HOME
   ```
   Record the Android Auto version (round 1 ran `17.9.664004-release`) and the `svc usb getFunctions` reply. If `svc usb` is not found, say so: the recovery's second step will then fail and fall through to the cable.
7. **D-HU:** `adb -s 27870808938846 shell am force-stop com.andrerinas.headunitrevived`. It takes no part.
8. **The one batched request to the operator.** Send it once, then wait for the operator's answer:
   > "Round 2 needs you once now and probably not again. 1) If D-MOTO is behind a PIN, unlock it. 2) Open the cue terminal: `tail -n0 -F <OUT>/hand-steps.log`. 3) When I say 'plug', unplug D-POCO from the PC, unplug D-MOTO from the PC, plug D-MOTO into D-POCO's OTG port, and set 'USB controlled by' to 'This device' on D-MOTO. After that the round runs about 60 minutes on its own. A cue appears only if a scripted recovery fails (a cable replug), or if a system USB dialog appears on D-POCO or D-MOTO. Never tick 'Always'."
9. **Switch to wireless adb**, as in round 1:
   ```bash
   adb -s 4f4027e9 tcpip 5555; adb -s ZY22GC3BM4 tcpip 5555; sleep 3
   POCO_IP=$(adb -s 4f4027e9 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
   MOTO_IP=$(adb -s ZY22GC3BM4 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
   adb connect $POCO_IP:5555; adb connect $MOTO_IP:5555
   ```
   Then say "plug" (step 8, part 3). Check that `adb -s $POCO_IP:5555 shell getprop ro.product.model` and `adb -s $MOTO_IP:5555 shell getprop ro.product.model` both answer.
10. **Shell:**
    ```bash
    HU=$POCO_IP:5555; PH=$MOTO_IP:5555; BASE=$OUT/settings_backup_poco.xml
    KEYS="(section 5)"
    source ./lib1008r3.sh; source ./libreattach1.sh; source ./libreattach2.sh
    WANT_MD5=$BASE_MD5; WAIT_AUTO=30
    ```

**Valid cycle** (all runs). A cycle counts when all of these hold. Name the failed item for any cycle that does not count.

1. Its `-start` and `-end` markers are in both captures.
2. Each verb in it has its `AutomationReceiver: ` line.
3. `match=0`.
4. For form A, `Disconnect action received.` is in the window. For form K, the window holds at least one `Found device already in accessory mode` after the relaunch.

**Preflight in every run.** The run's `.keys` file must show the keys of section 5 read back and the `commit` of the right build. The capture must hold `Found device already in accessory mode` at least once and `MATCH! Starting AapService` zero times. If a check fails, the run is a setup failure: fix the cause, re-run it once, and do not grade the failed attempt.

### RC5. Control: a plain retry on `main`, form A, pause about 5.6 s (the point of the round)

```bash
crun RC5 A 5 5.3 1
nfail RC5
```

Each cycle makes the stale state with form A. After the first failed handshake, the driver waits 5.3 s plus its poll and adb time (about 5.6 s in total, the same as the candidate's pause in round 1), then sends one `ACTION_CHECK_USB`. Nothing else acts on the phone until the recovery.

- **Counted cycle:** a valid cycle with `fauto` of 1 or more and `retries_ran=1`.
- **PASS** (the control measured something): 3 or more counted cycles in c1 to c5, each with a phone capture in its window.
- **INCONCLUSIVE:** fewer than 3 counted cycles. Say how many cycles had no failure and how many had `retries_ran=0`.
- There is no FAIL. This run measures `main`; it does not grade the candidate.
- **The result:** counted cycles with `retry_ssl=1`, out of counted cycles. For each counted cycle, also report `pause_ms`, `retry_to_ssl_ms`, `fails_after_retry`, the recovery step from `summary.tsv` (`REC=...`) if one ran, and on the phone `ph.enter_auto` and `ph.gh`.
- **Phone condition:** in each counted cycle with `retry_ssl=1`, report `ph.enter_auto`. A value of 0 means the phone did not re-enter accessory mode before the session, so the retry alone formed it. A value of 1 or more means the phone moved by itself (as R1 c1 did in round 1), and that cycle does not count as a retry success. Count it apart.
- **What this decides.** Round 1's candidate recovered 5 of 5 form A cycles with the retry after a re-switch that did nothing visible. If RC5 recovers 4 or more of 5 with `ph.enter_auto=0`, the re-switch adds nothing on this phone. If it recovers 1 or fewer, something in the re-switch matters even with no re-enumeration.

### RCK. Control: plain retries on `main`, form K, up to two retries

```bash
crun RCK K 3 5.3 2
nfail RCK
```

Form K gives the `SSL` form. After each failure the driver waits about 5.6 s and sends `ACTION_CHECK_USB`, up to two times. This is the same number of tries as the candidate's ladder, with no re-switch and no reset.

- **Counted cycle:** a valid cycle with `fauto` of 1 or more and `retries_ran` of 1 or more.
- **PASS** (measured): 2 or more counted cycles in c1 to c3. **INCONCLUSIVE:** fewer.
- **The result:** counted cycles with `retry_ssl=1`, and for each one which retry worked (`fails_after_retry` 0 means the first). Report the failure form (`tls` and `vfail`), `pause_ms`, `ph.enter_auto` and the `REC=...` step for any cycle that needed one.
- **What this decides.** If plain retries clear the `SSL` form, the reset is not needed for it. If they clear none, the reset is the only lever the app has for that form.

### RC1. Control: a plain retry on `main`, form A, pause about 1.3 s

Run it only if D-POCO's battery is at 60% or more after RCK. Otherwise skip it and say so.

```bash
crun RC1 A 3 1 1
nfail RC1
```

- Counted cycle, PASS and INCONCLUSIVE as RC5, with 2 or more counted cycles in c1 to c3.
- **The result:** counted cycles with `retry_ssl=1` and `ph.enter_auto=0`, out of counted cycles, with `pause_ms` and `retries_ran`. A retry that the app ignored (`retries_ran=0`) is not a failed retry: report it apart.
- **What this decides.** Whether the pause matters. Compare it with RC5.

### Switch to the candidate

```bash
adb -s $HU shell am force-stop com.andrerinas.headunitrevived
adb -s $HU shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/settings_before_candidate.xml
adb -s $HU install -r -d apks/candidate-37bbe71e.apk
adb -s $HU shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml | diff - $OUT/settings_before_candidate.xml
WANT_MD5=$CAND_MD5; WAIT_AUTO=90
```

### R2K. The candidate, form K: USB reset samples

```bash
RUN=R2K; bat_gate && urun2 R2K K 5
nfail R2K
```

`urun2` is round 1's driver. With form K it force-stops and relaunches the app in each cycle, and the ladder acts on its own. `c0` is a form K cycle too (the run start force-stops the app over the last RC session), so grade the reset over `c0` to `c5`.

- **PASS:** all of these hold.
  1. `nfail R2K` is 3 or more.
  2. Every failing cycle ends `AUTO_SSL` in `summary.tsv` (no `REC=...` line, no `HAND_SSL`).
  3. `miss=0` and `extra=0` in every cycle.
  4. Each `USB_RESET` step line is followed within 3 s by `UsbAccessoryMode: USB reset issued to ` or `... not issued to `, and within 20 s by exactly one observation line for `USB_RESET`. Read the times in the cycle's `.ladder` file. The counts agree: `rstok + rstno` equals `step2`, and `re1 + nc1 + re2 + nc2 + left` equals `step1 + step2`.
  5. On the phone, `ph.enter_auto` is 1 or more in every cycle with `re2=1`. This proves from the phone's own log that the reset re-enumerated its gadget.
- **FAIL:** an item does not hold. Quote the cycle's `.ladder` lines.
- **INCONCLUSIVE (reset part):** `step2=0` in every cycle from `c0` to `c5`. Items 1 to 3 are still graded.
- **The result:** USB reset re-enumerations out of USB reset steps (`re2` out of `step2`, summed over `c0` to `c5`), each with its `re-enumerated the phone in` time; re-switch re-enumerations out of re-switch steps (`re1` out of `step1`); `dt_ms` per failing cycle; `avc` and `unative` in every cycle with `step2` of 1 or more, with each line quoted; `kinds` per cycle.
- **What a PASS would look like if the reset did nothing.** Every `USB_RESET` step would log `no re-enumeration within ... of USB_RESET`, `re2` would be 0 and `ph.enter_auto` would be 0, and a session could still form from the retry after it. So item 5 and the `re2` count are the measurement, not the `AUTO_SSL` outcome.

### RG. The candidate: give-up, bound, banner and clearing

```bash
rgrun
```

`c0` brings a session up (a form K start, so it may add one more reset sample: report it with R2K's counts). In `c1` the driver ends the session, disables Gearhead on D-MOTO, force-stops it, and asks for USB. With no Android Auto app on the phone, the ladder should run both steps and give up. `give_up_checks` (round 1) then asks for USB once more on the same plug-in to test the bound, brings `MainActivity` forward, and reads the banner record. The driver then enables Gearhead and runs the scripted recovery, so a USB session must form and clear the record.

- **PASS:** all of these hold.
  1. `gh_disabled=1` and `gh_procs=0` in `summary.tsv`, and `ph_gh_while_down=0` (phone condition: Gearhead logged nothing while it was down).
  2. In `RGc1`, before the `RGc1-hand` marker (read the `.ladder` file): one `RESWITCH` step line, one `USB_RESET` step line, and then a give-up line.
  3. The bound held: `giveup` is 2 or more in `RGc1` (the bound try added one) and `steps_after_giveup=0`.
  4. `key_after_giveup` in `summary.tsv` is a number, not `absent`, and `banner` is 1 or more in `RGc1`.
  5. The recovery after Gearhead came back formed a session (`HAND_SSL` or `HAND_SSL_H3`), and `key_at_end` is `absent` or `0`: the record cleared on its own SSL.
  6. `gh_disabled_after=0`.
- **FAIL:** item 2, 3, 4 or 5 does not hold while item 1 holds. Quote the `.ladder` lines of `RGc1`.
- **UNTESTABLE:** `gh_disabled=0`, or the `disable:` line in `summary.tsv` shows an exception. Run the closing anyway.
- **INCONCLUSIVE:** item 1 holds but no give-up came (`no-giveup-in-120s`), and `kinds` shows `PEER_SILENT` or `none` for the failures. Report `kinds`, `step1`, `step2` and the failure lines.
- **Setup failure (re-run once):** item 1 fails because `gh_procs` is above 0 or `ph_gh_while_down` is above 0. Gearhead was still alive, so the ladder was not tested.
- **Report:** `kinds`, `step1`, `re1`, `nc1`, `step2`, `re2`, `nc2`, `left`, `giveup`, `steps_after_giveup`, `banner`, `leftgu`, `key_after_giveup`, `key_at_end`, the `REC=...` step, any H2 or H3 cue, and the time from the first `Handshake failed` to the first give-up line.

### Closing

Run all of it, whatever happened before. If the round stopped early, start here.

1. `adb -s $PH shell pm enable com.google.android.projection.gearhead`, then `adb -s $PH shell pm list packages -d | grep -acF com.google.android.projection.gearhead`. It must print 0. Report it.
2. `send ACTION_EXIT`. Then `adb -s $HU shell am start -a android.intent.action.VIEW -d headunit://exit`, `sleep 3`, and `adb -s $HU shell am force-stop com.andrerinas.headunitrevived`.
3. Restore D-POCO's backup with `pocoput $BASE` (no spec) and read it back.
4. `adb -s $HU shell dumpsys usb | grep -i -A4 headunitrevived > $OUT/dumpsys-usb-grants-after.txt`. Diff it against the Prepare copy and report any new grant.
5. `adb -s $PH shell svc power stayon false`.
6. When the operator is next at the rig (not a cue): unplug D-MOTO from D-POCO, put both back on their PC cables, and run `adb -s $POCO_IP:5555 usb` and `adb -s $MOTO_IP:5555 usb`.
7. `ps aux | grep -c "[l]ogcat"` must print 0.

### Stop rules

- `crun` and `urun2` stop a run at the first cycle that ends `NO_SSL` (`STOPPED at c<n>`). Go on to the next run.
- If two runs in a row stop at `c0` with `NO_SSL`, the rig cannot form a USB session. Stop, run the closing, and mark the rest UNTESTABLE.
- `bat_gate` false (D-POCO at 25% or below): stop, run the closing, and mark the rest UNTESTABLE (battery).
- If 2 cycles in the round end `REC=CABLE`, the scripted recovery does not work on this rig. Finish the run in progress, then skip RC1.
- The host thermal rules of `rig-quirks/topics/tooling.md` apply. `crun`, `urun2` and `rgrun` gate on them.

## 9. Do not re-run

- **R0** (unit tests on `37bbe71e`: 2856, 0 failures) and the DEX identity check, unless an APK was rebuilt.
- **R2T, the trigger:** 13 counted failures, `miss=0`, `extra=0` in round 1. R2K re-checks `miss` and `extra` on its own cycles; there is no separate trigger run.
- **R1, the baseline reproduction:** round 1 reproduced the fault and the dead report in R1 c2, c3 and c5.
- **R3, the cable form:** it needs a hand pull per cycle. Round 1 passed it.
- **R5u, the user exit:** passed in round 1.
- **R4p and R4w, the dongle:** passed in round 1 (11 sessions, no ladder step).
- Two permission prompts per connect: structural (template §7b). Report any dialog; do not grade the count.

## 10. Report back

The numbers that decide the next plan:

1. **The control arm:** RC5's plain-retry recoveries with `ph.enter_auto=0`, out of counted cycles, against round 1's 5 of 5 on the candidate. Add RC1's figure if it ran.
2. **The reset:** USB reset re-enumerations out of USB reset steps over R2K and `RGc0`, with each time and any `avc` or `UsbNative` line. Add RCK's plain-retry recoveries of the `SSL` form, out of counted cycles.
3. **The give-up path:** RG items 2 to 5, each as held or not held, with `key_after_giveup` and `key_at_end`.

Also report every `REC=...` line and every cue, because the round is meant to run with none.

Put the captures in `usb-reattach-round2-captures.zip` on the release `rig-evidence-usb-reattach`, and quote its size and sha256 in the results file. Include `cycles.tsv`, `summary.tsv`, `misses.log`, `dialogs.log`, `hand-steps.log`, every `.keys` file and every `.ladder` file.

```decisive-strings-baseline
Handshake failed on accessory device
Stale accessory detected
AOA re-enumeration
```

```decisive-strings-external
Unable to parse TLS packet header
/OPENHU
OPENHU
/UsbNative(
avc: denied
RIGMARK
entering USB accessory mode
exited USB accessory mode
applyOemOverride usbfunctions=accessory
UsbDeviceManager
Critical error
/GH.
```
