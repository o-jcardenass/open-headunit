# usb-reattach, round 3 brief

Published name on the transfer branch: `usb-reattach-round3-brief.md`. Results file: `usb-reattach-round3-results.md`. Evidence: add the asset `usb-reattach-round3-captures.zip` to the existing release `rig-evidence-usb-reattach`. Do not make a new release.

This round tests a new build of the thread. The stale-accessory ladder that rounds 1 and 2 measured is now rebuilt on top of current `main`, and four follow-up fixes ride with it. The round answers three questions:

1. **The attempt slot (the point of the round).** After a recovery step ends, is the USB attempt slot free again, so that the next cycle's USB check runs? A plain rebase onto `main` left the slot busy for the life of the service.
2. **The Save keeps its USB retry until SSL.** With the settings screen open for 35 s after a USB Save, does the dongle's session form behind the screen? `pr-1047-session-reconnect-round3` measured it held for 46.5 s and 51.3 s.
3. **The raise deadline is bounded.** On a unit where the projection screen cannot come up, does the second unprojected session in a row end with automatic reconnection held?

## 1. Build and baseline

- **Candidate C:** branch `fix/usb-reattach` on the fork, SHA **`bcf3b1a3`** (`bcf3b1a3bff1672578ca3750bf60aadef7faf503`), tree `d817c5a28247bb6d2d0f6afa78f245e06d8f5b13`. Seven commits on `main` `71375a68` (`71375a689f704df1dc844e6a0656a58e471a12aa`).
- **History was rewritten.** Rounds 1 and 2 tested `37bbe71e` on `main` `7102b428`. The branch was rebased onto `71375a68` and force-pushed, so `37bbe71e` and the later `42240750` are no longer on it.
- **Export build E (Stage D only):** C plus one manifest attribute, `android:exported="true"` on `.main.SettingsActivity`. D-POCO cannot open the settings screen from the shell without it (`pr-1047-session-reconnect-round3-brief.md` section 3.3). E is a local commit only. Never push it.
- **No baseline APK.** The control numbers come from rounds 1 and 2 and from `pr-1047-session-reconnect-round3-results.md`.
- **JVM tests:** 3084, 0 failures, our run on tree `d817c5a2`. Do not run them on the rig.

```bash
git ls-remote fork fix/usb-reattach          # MUST print bcf3b1a3bff1672578ca3750bf60aadef7faf503
git fetch fork fix/usb-reattach
git worktree add ../ohu-wt-usb-reattach-bcf3b1a3 bcf3b1a3bff1672578ca3750bf60aadef7faf503
git -C ../ohu-wt-usb-reattach-bcf3b1a3 rev-parse 'HEAD^{tree}'    # MUST print d817c5a28247bb6d2d0f6afa78f245e06d8f5b13
# E: one attribute on top of C, committed locally only
git -C ../ohu-wt-usb-reattach-bcf3b1a3 checkout -B arm-E-r3
sed -i 's|android:name=".main.SettingsActivity"|android:name=".main.SettingsActivity"\n            android:exported="true"|' ../ohu-wt-usb-reattach-bcf3b1a3/app/src/main/AndroidManifest.xml
git -C ../ohu-wt-usb-reattach-bcf3b1a3 diff --stat              # MUST print 1 file changed, 1 insertion(+)
git -C ../ohu-wt-usb-reattach-bcf3b1a3 -c user.name=rig -c user.email=rig@local commit -qam "rig: export SettingsActivity"
```

Build C first, at `bcf3b1a3` (check out the SHA, build, then make the E commit and build again). Use `build_hur.sh` with `HUR_DIR=<that worktree>`. Cool the host to 70C first and build with `--max-workers=2` (`rig-quirks/topics/tooling.md`). `build_hur.sh` deletes the previous APK, so copy each APK out of `apks/` at once: `apks/candidate-bcf3b1a3.apk` and `apks/export-bcf3b1a3.apk`. Copy `local.properties` into the worktree before the build if it has none. Kill the build's Gradle daemon before Stage U.

**Stop and escalate** if `git ls-remote` prints another SHA, or the tree differs.

### 1a. Identity gate, per APK, before any run on it

Pull the installed APK with a real `adb pull`, then:

```bash
for s in PROJECTION_UNRAISED StaleAccessoryRecoveryPolicy 'not reconnecting by itself, the projection screen could not be raised.'; do
  printf '%s\t' "$s"; unzip -p installed.apk 'classes*.dex' | strings | grep -cF "$s"; done      # each 1 or more, on C and on E
aapt2 dump xmltree --file AndroidManifest.xml installed.apk | grep -A4 'main.SettingsActivity' | grep -c 'exported(0x01010010)=true'   # E: 1. C: 0
```

`send ACTION_QUERY_STATE` must reply with a `commit` that begins `bcf3b1a3` on C. On E it must equal `git -C ../ohu-wt-usb-reattach-bcf3b1a3 rev-parse --short=12 HEAD` of the E commit. Record both md5s from `adb pull` plus a local `md5sum`. They must differ. Install with `adb install -r -d` only. Back up `settings.xml` before each install and diff it after.

## 2. What this is and why it exists

**The fault (rounds 1 and 2).** A phone used as the Android Auto source over USB fails the next bring-up after a session end. D-POCO finds D-MOTO still in accessory mode (`18D1:2D01`), opens it, and the handshake fails in one of two forms: `TRANSPORT_ERROR` (form A, after `ACTION_DISCONNECT`) or `SSL` (form K, after a force-stop and relaunch of the app). The ladder answers each failure: step 1 is an AOA re-switch, step 2 is a USB reset, and after both it gives up and raises the `STALE_USB_ACCESSORY` banner record.

**What rounds 1 and 2 measured on `37bbe71e`.** The trigger reached the ladder for every counted failure (13 of 13, `miss=0`, `extra=0`). The USB reset re-enumerated D-MOTO in 8 of 8 steps (305 to 712 ms). The re-switch re-enumerated it in 0 of 6, but a plain retry on `main` recovered 0 of 11 cycles, so the re-switch matters even with no re-enumeration. The give-up, its bound, the banner and its clearing all held (RG PASS).

**What changed, by item.** The ticket's diagnosis confirmed each item with a red JVM probe or a reading of the exact code path.

| Item | Commit | Defect | Fix |
|---|---|---|---|
| 0 | `aa5b89d0` | `main` gained a USB attempt slot (`attemptJob`, a queued `PendingCheck` and its replay). A plain rebase ran the recovery step as that slot's job but nothing freed it, so every later USB scan was queued and never replayed. | The step runs inside one `launchAttempt` job. Its completion frees the slot and replays the queued scan, as on every other path. The handshake report is posted to Main. |
| 1 | `a8e65957` | A Save-owned USB attempt that opened and then ended before SSL dropped its retry token, so the settings screen held the dongle's return (`pr-1047-session-reconnect-round3` U1 FAIL). | CommManager keeps the Save owner from the USB open until SSL. |
| 4 | `07c478b5` | A USB check held through a wireless SSL came back after a user exit. | A user exit drops the USB debt. JVM tests only. |
| 3 | `d3b99bcc` | A replayed scan could switch the device a second time after the attach screen had switched it. | A switch during the attempt makes the replay accessory-only. JVM tests only. |
| 2 | `bcf3b1a3` | The projection raise deadline ended a session that never projected, then automatic recovery started a new one, every ~20 s with no bound. | The second such end in a row is a held end (`DisconnectReason.PROJECTION_UNRAISED`): no 3 s USB check, no discovery retry, no user-exit side effects. |

The other two commits (`f040f643`, `27f7c5b1`) are the rounds 1 and 2 build, replayed onto `main`.

**What a PASS would look like if the change did nothing.**

- Item 0: c1 of R2A recovers, then the next `ACTION_CHECK_USB` prints `AutomationReceiver:` and nothing else. No `Found device already in accessory mode` follows, the cycle needs the hand ladder or ends `NO_SSL`, and `checks_ran` is below `checks`.
- Item 1: `held` 1 or more in U1 and no session before the screen closes, as in `pr-1047-session-reconnect-round3`.
- Item 2: no `the projection screen never came up again` line, and one more `the projection screen never came up` line about every 35 to 40 s on this rig.

## 3. What is different about this round

**Two cable layouts, in this order.**

| Stage | Head unit | On D-POCO's OTG port | adb | Runs | Arm |
|---|---|---|---|---|---|
| **U** | D-POCO (`4f4027e9`) | D-MOTO (`ZY22GC3BM4`) | both over wireless adb | R2A, R2K, P1; R2T is graded over R2A and R2K | C |
| **D** | D-POCO, wireless adb | the dongle (idle `18d1:4ee1`, accessory `18d1:2d00`), paired to D-MOTO | D-POCO wireless, D-MOTO on its PC cable | U0, U1 | E |

**The failure line changed, so round 1's `csum` cannot be used as it is.** On `main` `71375a68` the app no longer prints a plain `Handshake failed` line. `csum` anchored every failure on that line, so it would count 0 failures and call every ladder line `extra`. A failed handshake now ends with exactly one of five `AapTransport` lines at ERROR (section 7). `libreattach3.sh` (section 6) replaces `csum` with one that counts those. The guarded report line `after its own teardown; reporting it to USB` is also gone (the rework removed that path), so `tear` is not counted.

**`service scan (force=` is not an instrument.** It prints at VERBOSE when the bus is unchanged. Item 0 is graded on `Found device already in accessory mode` after each USB check verb instead.

**D-POCO is Android 15 (API 35), not 34 as the plan said.** P1 needs API 29 or later and the overlay permission off. Read the level in Prepare.

**P1 needs the app in the background with the overlay off.** `ActivityLaunchPolicy` raises the projection directly while any activity of ours is started (`App.hasStartedActivity`). So P1 revokes the overlay app-op, presses HOME, and only then asks for USB. A raise by notification on an awake, unlocked screen does not open the activity, so the raise fails as the fix needs. P1 restores the app-op and the screen timeout at its end.

**Two P1 risks, pre-registered.** A USB re-enumeration during the ladder can start our `UsbAttachedActivity`, which makes the app foreground for a moment, so a raise can go DIRECT and project. And `ACTION_CHECK_USB` reaches a running foreground service from the background; if Android refuses it, no scan follows. Both make P1 INCONCLUSIVE, not FAIL (section 8, P1).

**The dongle returns in normal mode after every session end** (`pr-1047-session-reconnect-round3-results.md`, U1): it detaches about 1.7 s after the Save's first retry opens it, and re-attaches as `18D1:4EE1`. That is the path item 1 changes.

**Logcat readers.** `cap_stop` in `lib1008r3.sh` counts readers with `ps aux | grep -c "[l]ogcat"`, which matches the host's own shells. `libreattach3.sh` overrides `cap_stop` to use `pgrep -fc "^adb .*logcat"`. Run that check before and after every run in both stages. It must print 0.

**D-POCO's battery.** It powers D-MOTO and then the dongle over OTG and cannot charge. Charge it to 80% or more before Stage U. `bat_gate` stops the round at 25%.

**The rig audio keys are a deliberate worst case.** They live on D-HU, which this round does not use. Do not write or reset them.

**Expected INCONCLUSIVE, said up front:**

- **R2A:** fewer than 2 cycles in c1 to c5 with a failure before any hand step.
- **R2K, reset part:** no cycle reaches step 2.
- **P1:** any of the cases in section 8, P1 (no notification raise, a picture formed, no second session, or the USB verb ignored from the background).
- **U1:** U0 fails.

## 4. Hand steps

There is one batched request before the first run (Prepare step 9) and one cable change between the stages (cue H1b). After that, a cue appears only if a scripted recovery fails or a system USB dialog appears. Keep the cue terminal open.

| Id | Step | When | Why no verb |
|---|---|---|---|
| H0 | Unlock D-MOTO if it is behind a PIN | Prepare only | adb has no way past a PIN (`rig-quirks/units/D-MOTO.md`) |
| H1 | Plug D-MOTO into D-POCO's OTG port, then set "USB controlled by" to "This device" on D-MOTO | Prepare, and only as the last recovery step in Stage U | A cable is hardware. The role setting is a system screen on the phone |
| H1b | Unplug D-MOTO from D-POCO and put it on its PC cable. Plug the dongle into D-POCO's OTG port | Once, at the start of Stage D | A cable is hardware |
| H1c | Unplug the dongle. Put D-POCO back on its PC cable | Closing | A cable is hardware |
| H2 | Allow a system USB dialog on D-POCO (`UsbPermissionActivity` or `UsbConfirmActivity`). **For D-MOTO, never tick "Always".** For the dongle, "Always" is allowed, as in `pr-1047-session-reconnect-round3` | Fallback only | A system dialog, not our app. "Always" for D-MOTO changes the rig for later rounds (template §7b) |
| H7 | Open a terminal on the rig PC with `tail -n0 -F <OUT>/hand-steps.log` and keep it in view | Prepare | A cue must reach the operator in time |

**Injected taps.** U1 makes each Save with two injected taps on dumped targets (`tap_save` in `lib1047r3.sh`): the "Separate Audio Streams" toggle by `:id/settingSwitch`, then the Save button by `:id/save_button_widget` with the text `Save (Reconnect needed)`. No verb reaches `applyAudioSettings()`. Budget: 4 taps in U1, at most 5 per run, enforced by `tap_xy`. A target that does not resolve voids the cycle. Never guess a coordinate. List every tap from `taps.log` in Setup notes.

## 5. Settings keys

Write them with the app stopped, after a backup (template §1). D-POCO is not rooted, so use `pocoput`. Every run writes its keys from the Prepare backup. `log-level` 2 (INFO) carries every app line this round reads: each one in section 7 is `AppLog.i`, `AppLog.w` or `AppLog.e`, and none sits behind `LOG_VERBOSE`. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on C, so `allow-external-configuration` is not needed.

| Key | Type | R2A, R2K | P1 | U0, U1 | Why |
|---|---|---|---|---|---|
| `wifi-connection-mode` | int | `0` | `0` | `3` | Stage U: no wireless stack beside USB. U1: an armed Native stack prints `WifiLauncher: Initializing WiFi Mode: NATIVE`, which U1 counts |
| `log-level` | int | `2` | `2` | `2` | INFO |
| `onboarding-version` | int | `2` | `2` | `2` | no wizard |
| `kill-on-disconnect` | boolean | `false` | `false` | `false` | keeps the service after `ACTION_DISCONNECT` |
| `auto-connect-last-session` | boolean | `false` | `false` | `true` | Stage U: only the verbs and the ladder start a connection |
| `auto-connect-single-usb` | boolean | `false` | **`true`** | (not written) | P1: the 3 s USB check after a recovered end needs it (`scheduleReconnectIfNeeded`), else nothing reconnects and the bound is never reached |
| `auto-start-on-usb` | boolean | `false` | `false` | `true` | |
| `reopen-on-reconnection` | boolean | `false` | `false` | `true` | |
| `use-libusb` | boolean | `false` | `false` | `false` | the re-switch takes the Java path (`Sending acc start`) |
| `connection-modes` | string set | `usb,wifi` | `usb,wifi` | `usb,wifi` | |
| `native-aa-wake-damage-verdict` | int | | | `0` | as in `pr-1047-session-reconnect-round3` |
| `video-profile-starvation-cap` | | delete | delete | delete | a run of failed bring-ups can leave it set |
| `connection-issue-stale-usb-accessory`, `connection-issue-dismissed-at` | | delete | delete | | the banner record and a dismissal |
| `native-aa-wireless`, `wifi-launcher-mode` | | delete | delete | delete | legacy mode keys |

```bash
KEYS="int:wifi-connection-mode=0 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:auto-connect-last-session=false bool:auto-connect-single-usb=false bool:auto-start-on-usb=false bool:reopen-on-reconnection=false bool:use-libusb=false set:connection-modes=usb,wifi del:video-profile-starvation-cap del:connection-issue-stale-usb-accessory del:connection-issue-dismissed-at del:native-aa-wireless del:wifi-launcher-mode"
P1KEYS="${KEYS/bool:auto-connect-single-usb=false/bool:auto-connect-single-usb=true}"
U2KEYS="int:wifi-connection-mode=1 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:auto-connect-last-session=true bool:auto-start-on-usb=true bool:reopen-on-reconnection=true bool:use-libusb=false set:connection-modes=usb,wifi del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode"
U1KEYS="${U2KEYS/wifi-connection-mode=1/wifi-connection-mode=3} int:native-aa-wake-damage-verdict=0"
```

`KEYS` is round 2's string. `U2KEYS` and `U1KEYS` are `pr-1047-session-reconnect-round3`'s strings; `U2KEYS` is only the base of `U1KEYS`. Check `ohu_setkeys.py` first: `grep -c 'if v else \[\]' ohu_setkeys.py` must print 1.

## 6. Shell setup

### Stage U

Make `hur-wifi-test-scripts/usb-reattach-round3/`. Copy `lib1008r3.sh`, `ohu_setkeys.py`, `libreattach1.sh` and `libreattach2.sh` into it from `../usb-reattach-round2/`. If one is gone, cut it from section 6 of `usb-reattach-round1-brief.md` (`libreattach1.sh`), `usb-reattach-round2-brief.md` (`libreattach2.sh`) or `bluetooth-audio-disabled-usb-connect-round3-brief.md` (`lib1008r3.sh`). Save `libreattach3.sh` below beside them. Source them in this order: `lib1008r3.sh`, `libreattach1.sh`, `libreattach2.sh`, `libreattach3.sh`. Run the stage under `flock /tmp/ohu-rig.lock`. One adb call at a time per unit; the two log captures are the only streams beside them (house rule 8).

`libreattach3.sh` replaces `csum` and `cap_stop`, and adds `readers`, `p1run` and `p1sum`. Everything else is rounds 1 and 2's, unchanged: `send`, `mark`, `cue`, `nl`, `dlg`, `th_gate`, `th_stop`, `th_report`, `apk_check`, `cap_start`, `win`, `pocoput`, `cyc_wait`, `since`, `bkey`, `ph_home`, `give_up_checks`, `settle`, `cycle`, `nfail`, `urun2`, `make_stale` (forms A and K), `hand_recover` (the scripted gadget recovery first), `bat_gate` and `pcount`.

**`libreattach3.sh`**:

```bash
# libreattach3.sh : source after lib1008r3.sh, libreattach1.sh and libreattach2.sh. Needs HU, PH, OUT, BASE, KEYS, P1KEYS, WANT_MD5.

# readers : logcat readers on the host; must print 0 before and after every run
readers() { pgrep -fc "^adb .*logcat"; }

# cap_stop : as lib1008r3.sh, with the reader count that does not match the host's own shells
cap_stop() { local p; for p in $CAPLOOP $PCAPLOOP; do pkill -P $p 2>/dev/null; kill $p 2>/dev/null; done
  adb -s "$PH" logcat -d -v time > "$PCAP.dump"
  for f in "$CAP" "$PCAP"; do awk '!seen[$0]++' "$f" | tr -d '\r' > "$f.tmp" && mv "$f.tmp" "$f"; done
  echo -e "${RUN:-}\treaders_after=$(readers)" | tee -a "$OUT/summary.tsv"; }

# csum <cyc> : the counts for one cycle window, head unit and phone, into cycles.tsv.
# A failed handshake is one of the five AapTransport terminal lines. A verdict must follow it within 3 s.
csum() { local c=$1
  win "$CAP" "$c" > "$OUT/$c.hwin"; win "$PCAP" "$c" > "$OUT/$c.pwin"
  grep -aE 'UsbLauncher: |UsbAccessoryMode: USB reset|Acc start sent|Handshake: Version exchange timed out after|Handshake: Version request/response failed after|Handshake: SSL performHandshake failed\.|Handshake: AuthComplete write incomplete or transport retired|Handshake failed with exception|SSL handshake complete|Found device already in accessory mode|Unable to parse TLS packet header|USB permission granted for|requesting permission|Disconnect action received|AapService: session state |AutomationReceiver: |AutomationMarker: ' "$OUT/$c.hwin" > "$OUT/$c.ladder"
  LC_ALL=C awk -v c="$c" '
  function ms(t,  a) { split(t, a, /[:.]/); return ((a[1] * 60 + a[2]) * 60 + a[3]) * 1000 + a[4] }
  function has(s) { return index($0, s) > 0 }
  function kind() { if (match($0, /\((TRANSPORT_ERROR|SSL|PEER_SILENT|OTHER)\)/)) return substr($0, RSTART + 1, RLENGTH - 2); return "none" }
  function isfail() { return has("/OPENHU") && (has("Handshake: Version exchange timed out after") || has("Handshake: Version request/response failed after") || has("Handshake: SSL performHandshake failed.") || has("Handshake: AuthComplete write incomplete or transport retired") || has("Handshake failed with exception")) }
  { t = ms($2) }
  has("AutomationMarker: " c "-hand") { hand = 1 }
  has("AutomationReceiver: ") && has("ACTION_CHECK_USB") { checks++; ck[checks] = t; next }
  has("Found device already in accessory mode") || has("Switching USB device to accessory mode") || has("SSL handshake complete") {
    for (i = 1; i <= checks; i++) if (!ran[i] && t >= ck[i] && t - ck[i] <= 15000) { ran[i] = 1; cran++ } }
  has("Connected have EPs") { inflight = 1; detnow = 0 }
  has("USB Intent: ") && has("USB_DEVICE_DETACHED") { det++; if (inflight) detnow = 1 }
  has("USB Intent: ") && has("USB_DEVICE_ATTACHED") { att++ }
  has("Unable to parse TLS packet header") { tls++ }
  has("Handshake: Version request send failed") { vfail++ }
  has("Found device already in accessory mode") { found++ }
  has("AapService: session state failed (") { sfail++ }
  has("UsbLauncher: replaying the USB scan queued during the attempt") { replay++ }
  isfail() { fails++; inflight = 0
    if (detnow) { exempt++; detnow = 0; next }
    if (pend) { miss++; print c " MISS " pt > "/dev/stderr" }
    pend = 1; tf = t; pt = $1 " " $2
    if (!hand) { fauto++; if (t1 == "") t1 = t }
    next }
  has("SSL handshake complete") { ssl++; inflight = 0; if (!hand && ts == "") ts = t }
  { step = has("UsbLauncher: stale accessory ") && has(": handshake failed (")
    gu = has("recovery used both steps; a replug is needed")
    ns = has("not a stale-accessory failure, no recovery")
    rf = has("UsbLauncher: stale accessory recovery for ") }
  step { if (/RESWITCH$/) s1++; else if (/USB_RESET$/) s2++; if (gave && !hand) sag++ }
  gu { giveup++; gave = 1 }
  ns { notstale++ }
  rf { refused++; if (has("the USB attempt slot is busy")) busy++; if (has("another attempt holds the arbiter")) arb++ }
  step || gu || ns || rf {
    v++; k = kind(); kinds = kinds k ","
    if (pend && t - tf <= 3000) { ok++; pend = 0 }
    else { extra++; print c " EXTRA " $0 > "/dev/stderr" } }
  { re = has("re-enumerated the phone in"); nc = has("no re-enumeration within") && has("; trying the handshake once more") }
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
  has("the accessory that recovery gave up on has left the bus") { leftgu++ }
  has("MATCH! Starting AapService") { match_++ }
  END { if (pend) { miss++; print c " MISS " pt > "/dev/stderr" }
    dt = (t1 != "" && ts != "") ? ts - t1 : "na"
    printf "%s fails=%d fauto=%d exempt=%d miss=%d extra=%d ok=%d verdicts=%d kinds=%s step1=%d step2=%d giveup=%d steps_after_giveup=%d notstale=%d refused=%d busy=%d arb=%d re1=%d nc1=%d re2=%d nc2=%d left=%d accstart=%d rstok=%d rstno=%d vfail=%d tls=%d found=%d ssl=%d ssl_auto=%d dt_ms=%s checks=%d checks_ran=%d replay=%d sfail=%d permreq=%d permok=%d det=%d att=%d unative=%d avc=%d leftgu=%d match=%d\n",
      c, fails, fauto, exempt, miss, extra, ok, v, kinds, s1, s2, giveup, sag, notstale, refused, busy, arb, re1, nc1, re2, nc2, left,
      accstart, rstok, rstno, vfail, tls, found, ssl, (ts != ""), dt, checks, cran, replay, sfail, permreq, permok, det, att, unative, avc, leftgu, match_ }' "$OUT/$c.hwin" 2>> "$OUT/misses.log" | tee -a "$OUT/cycles.tsv"
  LC_ALL=C awk -v c="$c" '
  function has(s) { return index($0, s) > 0 }
  has("RIGMARK") && has(": " c "-hand") { hand = 1 }
  has("entering USB accessory mode") { en++; if (!hand) ena++ }
  has("exited USB accessory mode") { ex++; if (!hand) exa++ }
  has("Critical error") { crit++ }
  has("/GH.") { gh++ }
  END { printf "%s ph.enter=%d ph.enter_auto=%d ph.exit=%d ph.exit_auto=%d ph.crit=%d ph.gh=%d\n", c, en, ena, ex, exa, crit, gh }' "$OUT/$c.pwin" | tee -a "$OUT/cycles.tsv"; }

# p1run : P1 on C. Overlay off, app in the background, then a USB session that cannot raise its screen.
p1run() { local L T R ov to c=P1c1; RUN=P1; th_gate || return 3; bat_gate || return 4
  apk_check || { echo -e "$RUN\tAPK_MISMATCH" | tee -a "$OUT/summary.tsv"; return 1; }
  adb -s "$HU" shell am force-stop $PKG
  pocoput "$BASE" $P1KEYS > "$OUT/$RUN.keys" || { echo -e "$RUN\tpocoput-failed" | tee -a "$OUT/summary.tsv"; return 1; }
  echo -e "$RUN\tsdk=$(adb -s "$HU" shell getprop ro.build.version.sdk | tr -d '\r')" | tee -a "$OUT/summary.tsv"
  ov=$(adb -s "$HU" shell appops get $PKG SYSTEM_ALERT_WINDOW | tr -d '\r' | grep -aoE 'SYSTEM_ALERT_WINDOW: [a-z]+' | awk '{print $2}')
  echo "${ov:-default}" > "$OUT/P1.overlay_before"
  adb -s "$HU" shell appops set $PKG SYSTEM_ALERT_WINDOW deny
  echo -e "$RUN\toverlay_before=${ov:-default} overlay_now=$(adb -s "$HU" shell appops get $PKG SYSTEM_ALERT_WINDOW | tr -d '\r' | grep -aoE 'SYSTEM_ALERT_WINDOW: [a-z]+')" | tee -a "$OUT/summary.tsv"
  to=$(adb -s "$HU" shell settings get system screen_off_timeout | tr -d '\r'); echo "$to" > "$OUT/P1.screen_off_timeout"
  adb -s "$HU" shell settings put system screen_off_timeout 1800000
  adb -s "$HU" shell input keyevent KEYCODE_WAKEUP
  cap_start "$RUN"; mark "$RUN-start"
  adb -s "$HU" shell am start -n $MAIN >/dev/null; sleep 3
  send ACTION_QUERY_STATE >> "$OUT/$RUN.keys"
  mark "${RUN}c0-start"
  if settle "${RUN}c0" "$(nl)"; then
    sleep 10; mark "$c-start"; L=$(nl)
    send ACTION_DISCONNECT >/dev/null
    cyc_wait 15 'Disconnect action received.' "$L" || echo -e "$c\tno-disconnect-line" | tee -a "$OUT/summary.tsv"
    sleep 10
    adb -s "$HU" shell input keyevent KEYCODE_HOME; sleep 3
    R=$(adb -s "$HU" shell dumpsys activity activities | grep -a -m1 -E 'topResumedActivity|mResumedActivity' | tr -d '\r')
    echo -e "$c\tfg_ours=$(echo "$R" | grep -acF "$PKG") wake=$(adb -s "$HU" shell dumpsys power | grep -a -m1 -oE 'mWakefulness=[A-Za-z]+')" | tee -a "$OUT/summary.tsv"
    mark "$c-trigger"; T=$(nl); send ACTION_CHECK_USB >/dev/null
    cyc_wait 15 'Found device already in accessory mode' "$T" || echo -e "$c\tno-scan-in-15s" | tee -a "$OUT/summary.tsv"
    if cyc_wait 150 'the projection screen never came up again' "$T"; then mark "$c-held"; sleep 60
    else echo -e "$c\tno-hold-in-150s" | tee -a "$OUT/summary.tsv"; fi
    echo -e "$c\twake_end=$(adb -s "$HU" shell dumpsys power | grep -a -m1 -oE 'mWakefulness=[A-Za-z]+')" | tee -a "$OUT/summary.tsv"
    mark "$c-end"
  fi
  mark "$RUN-end"; cap_stop; th_stop
  adb -s "$HU" shell appops set $PKG SYSTEM_ALERT_WINDOW "$(cat "$OUT/P1.overlay_before")"
  adb -s "$HU" shell settings put system screen_off_timeout "$(cat "$OUT/P1.screen_off_timeout")"
  echo -e "$RUN\toverlay_restored=$(adb -s "$HU" shell appops get $PKG SYSTEM_ALERT_WINDOW | tr -d '\r' | grep -aoE 'SYSTEM_ALERT_WINDOW: [a-z]+') timeout_restored=$(adb -s "$HU" shell settings get system screen_off_timeout | tr -d '\r')" | tee -a "$OUT/summary.tsv"
  [ -n "$(win "$CAP" "${RUN}c0" | head -1)" ] && csum "${RUN}c0"
  p1sum; th_report "$RUN" | tee -a "$OUT/summary.tsv"; }

# p1sum : P1's counts. Phase 1 runs from P1c1-trigger to the held end, phase 2 from the held end to
# the first USB attach after it or to P1c1-end. Phase 3 is after that attach: report only.
p1sum() {
  LC_ALL=C awk '/AutomationMarker: P1c1-trigger/ { on = 1 } on { print } /AutomationMarker: P1c1-end/ { exit }' "$CAP" > "$OUT/P1.twin"
  LC_ALL=C awk '
  function ms(t,  a) { split(t, a, /[:.]/); return ((a[1] * 60 + a[2]) * 60 + a[3]) * 1000 + a[4] }
  function has(s) { return index($0, s) > 0 }
  BEGIN { ph = 0 }
  { t = ms($2); if (t0 == "") t0 = t; tl = t }
  has("AapService: raising the projection by ") { if (has("by NOTIFICATION")) rn[ph]++; else if (has("by DIRECT")) rd[ph]++; else if (has("by OVERLAY")) ro[ph]++ }
  has("Throughput over ") { thr[ph]++ }
  has("SSL handshake complete") { ssl[ph]++ }
  has("Found device already in accessory mode") { found[ph]++ }
  has("AapService: USB disconnect. Scheduling reconnect check in ") { sched[ph]++; if (pendrec && t - trec <= 10000) { recsched = 1; pendrec = 0 } }
  has("Single USB auto-connect: connecting to") { single[ph]++ }
  has("the projection screen never came up") {
    ends++
    if (has("never came up again")) { holds++; if (ph == 0) { ph = 1; th = t } }
    else { recov++; trec = t; pendrec = 1 } }
  has("not reconnecting by itself, the projection screen could not be raised.") { notrec++; if (th != "" && t - th <= 2000) notrec_ok++ }
  ph == 1 && t > th && has("USB Intent: ") && has("USB_DEVICE_ATTACHED") { ph = 2; ta = t }
  END {
    span = (th == "") ? "na" : ((ta != "") ? ta - th : tl - th)
    printf "P1 ends=%d recovered_ends=%d held_ends=%d notrec=%d notrec_within_2s=%d recovered_end_rescheduled=%d raise_notif=%d raise_direct=%d raise_overlay=%d throughput=%d ssl_before_hold=%d ssl_after_hold=%d found_after_hold=%d sched_after_hold=%d single_after_hold=%d hold_at_ms=%s quiet_span_ms=%s attach_after_hold=%d ssl_after_attach=%d\n",
      ends, recov, holds, notrec, notrec_ok, recsched, rn[0] + rn[1] + rn[2], rd[0] + rd[1] + rd[2], ro[0] + ro[1] + ro[2], thr[0] + thr[1] + thr[2],
      ssl[0], ssl[1], found[1], sched[1], single[1], (th == "" ? "na" : th - t0), span, (ta != ""), ssl[2] }' "$OUT/P1.twin" | tee -a "$OUT/cycles.tsv"
  echo "P1 ph.gh_before_hold=$(pcount P1c1-trigger P1c1-held '/GH.') ph.gh_after_hold=$(pcount P1c1-held P1c1-end '/GH.') ph.BYEBYE=$(pcount P1c1-trigger P1c1-end 'BYEBYE') ph.ByeBye=$(pcount P1c1-trigger P1c1-end 'ByeBye') ph.gal_deliberate=$(pcount P1c1-trigger P1c1-end 'GAL was deliberately disconnected') ph.enter_after_hold=$(pcount P1c1-held P1c1-end 'entering USB accessory mode') ph.crit_after_hold=$(pcount P1c1-held P1c1-end 'Critical error')" | tee -a "$OUT/cycles.tsv"
  grep -aE 'RIGMARK|/GH\.|BYEBYE|ByeBye|GAL was deliberately|Critical error|entering USB accessory mode|exited USB accessory mode' "$PCAP" | LC_ALL=C awk '/: P1c1-held/ { on = 1 } on { print } /: P1c1-end/ { exit }' | head -200 > "$OUT/P1.phone_after_hold"; }
```

**Notes on the instruments.**

- `csum` fields are round 1's, with these changes. `fails` counts the five terminal lines. `ok` and `extra` use a 3 s window, not 2 s, because the report now crosses to Main before the verdict prints. `tear`, `uefails`, `uerep`, `uesteps` and `banner` are gone. New: `checks` (USB check verbs that landed), `checks_ran` (of those, how many were followed within 15 s by `Found device already in accessory mode`, `Switching USB device to accessory mode` or `SSL handshake complete`), `busy` and `arb` (the two refusal forms), `replay` (queued scans replayed) and `sfail` (the service's `session state failed (` line, report only).
- `p1sum` puts its counts on one `P1 ...` line in `cycles.tsv`, and the phone counts on a second. `P1.phone_after_hold` holds D-MOTO's lines from the hold to the run end.
- The two clocks differ. Compare only counts across devices.

### Stage D

Run it in a **new shell**, because the `lib1047` family defines functions with the same names as the Stage U libraries. Make `hur-wifi-test-scripts/usb-reattach-round3/stageD/`. Copy `ohu_lib.sh`, `ohu_setkeys.py`, `ptr_lib.sh`, `lib1047.sh` and `lib1047r3.sh` into it from `../../pr-1047-session-reconnect-round3/`. If `lib1047r3.sh` is gone, cut it from section 6 of `archive/rounds/pr-1047-session-reconnect-round3-brief.md`. Source them in that order. Replace every `ps aux | grep -c "[l]ogcat"` check with `pgrep -fc "^adb .*logcat"` (`pr-1047-session-reconnect-round3-results.md`, Setup notes item 1).

```bash
POCO_IP=<D-POCO wlan0 address, from Stage U>; HU=$POCO_IP:5555; MOTO=ZY22GC3BM4; PH=$MOTO
OUT=~/hur-wifi-test-scripts/usb-reattach-round3/stageD; mkdir -p $OUT; cd $OUT
PUT=pocoput; BASEXML=$OUT/settings_backup_poco.xml; UNIT=D-POCO
cp ../settings_backup_poco.xml $BASEXML
source ./ohu_lib.sh; source ./ptr_lib.sh; source ./lib1047.sh; source ./lib1047r3.sh
ARM=E; WANT_MD5=<E md5>
```

## 7. The lines that decide the runs

App lines were checked with `git grep -F` against `app/src/main` and `contract/src` at `bcf3b1a3`. All print at INFO or above. Every count is inside a marker window: `<RUN>c<n>-start` to `<RUN>c<n>-end` (`AutomationMarker:` in `$OUT/<RUN>.hu.logcat`, `RIGMARK` in `$OUT/<RUN>.phone.logcat`) through `csum`; `P1c1-trigger` to `P1c1-end` through `p1sum`; and the Save line plus a stated number of ms through `cntw` in U1.

| Line (fixed substring) | File | Meaning |
|---|---|---|
| `AutomationReceiver: ` | unit | a verb landed; a missing one voids the step |
| `AutomationMarker: ` | unit | a marker |
| `Disconnect action received.` | unit | `ACTION_DISCONNECT` reached the service |
| `Found device already in accessory mode` | unit | a USB scan ran and found the phone at `18D1:2D0x` |
| `Connected have EPs` | unit | the endpoints opened; a handshake starts |
| `Handshake: Version request send failed` | unit | form A (report) |
| `Handshake: Version exchange timed out after` | unit | **terminal failure line 1** |
| `Handshake: Version request/response failed after` | unit | **terminal failure line 2** |
| `Handshake: SSL performHandshake failed.` | unit | **terminal failure line 3** |
| `Handshake: AuthComplete write incomplete or transport retired` | unit | **terminal failure line 4** |
| `Handshake failed with exception` | unit | **terminal failure line 5** (form K) |
| `Unable to parse TLS packet header` | unit | `SSLException` text, not the app's (form K) |
| `SSL handshake complete` | unit | a session formed (never prefix `Handshake:`) |
| `AapService: session state ` with `failed (` | unit | the service's failure report (report only) |
| `USB Intent: ` with `USB_DEVICE_DETACHED` or `USB_DEVICE_ATTACHED` | unit | a detach or an attach |
| `UsbLauncher: stale accessory ` with `: handshake failed (` | unit | a ladder step; the line ends `RESWITCH` or `USB_RESET` |
| `recovery used both steps; a replug is needed` | unit | give-up |
| `not a stale-accessory failure, no recovery` | unit | the ladder does not act on this failure |
| `UsbLauncher: stale accessory recovery for ` with `the USB attempt slot is busy` or `another attempt holds the arbiter` | unit | a refused step |
| `re-enumerated the phone in`, `no re-enumeration within` with `; trying the handshake once more`, `the phone left the bus after` | unit | a step's observation |
| `UsbAccessoryMode: USB reset issued to `, `UsbAccessoryMode: USB reset not issued to ` | unit | step 2 went out, or why not |
| `UsbLauncher: replaying the USB scan queued during the attempt` | unit | **item 0:** the slot's completion replayed a queued scan |
| `the accessory that recovery gave up on has left the bus` | unit | the give-up latch ended |
| `Acc start sent`, `Sending acc start` | unit | the re-switch went out |
| `Accessory-mode device has no permission (re-enumerated); requesting permission`, `USB permission granted for ` | unit | a permission request and its answer |
| `AapService: raising the projection by ` then `NOTIFICATION`, `DIRECT` or `OVERLAY` | unit | **P1:** the raise route |
| `the projection screen never came up` | unit | **P1:** a raise-deadline end; it is a prefix of the next line too |
| `the projection screen never came up again` | unit | **P1:** the held end |
| `not reconnecting by itself, the projection screen could not be raised.` | unit | **P1:** the held end skipped every automatic reconnect |
| `AapService: USB disconnect. Scheduling reconnect check in ` | unit | the 3 s USB check |
| `Single USB auto-connect: connecting to` | unit | a single-device auto-connect |
| `Throughput over ` | unit | video reached the decoder: a picture formed |
| `CommManager: audio settings changed; reconnecting the projection session` | unit | **U1:** the Save (T0) |
| `SettingsRestart: route=` with `USB retry=run`; `fallback=run`, `fallback=skip_superseded`, `fallback=cancel_superseded` | unit | **U1:** the Save's retry and its fallback |
| `AapService: session state ` with `(settings_restart)` | unit | **U1:** the Save's end |
| `UsbLauncher: USB auto-connect held while the settings screen is open; ` | unit | **U1:** a USB check held by the screen (item 1's failure) |
| `USB accessory device attached, connecting.`, `Switching USB device to accessory mode` | unit | **U1:** the dongle's return |
| `stopping the wireless stack for the duration of it` | unit | **U1:** the wired-session quiesce at SSL |
| `WifiLauncher: Initializing WiFi Mode: ` then `NATIVE` | unit | **U1:** wireless armed |
| `NativeAA: Attempting active poke to device` | unit | **U1:** a wake poke |
| `AapService: Not raising the projection, the settings screen is open` | unit | **U1:** the raise refused behind the screen |
| `MATCH! Starting AapService` | unit | discard rule |
| `RIGMARK` | phone | phone markers |
| `entering USB accessory mode`, `exited USB accessory mode` | phone | Android: the phone's gadget changed state |
| `/GH.` | phone | any Android Auto line |
| `BYEBYE`, `ByeBye`, `GAL was deliberately disconnected` | phone | Android Auto's reading of our ByeBye (P1, report) |
| `Critical error` | phone | report only |
| `FATAL EXCEPTION` | unit, phone | a crash (system line) |

**Composed strings.** Several lines join a literal and a value at run time: `$step re-enumerated the phone in`, `of $step; trying the handshake once more`, `raising the projection by $strategy`, `session state $state ($reason)`, `SettingsRestart: route=$route retry=` and `fallback=` with `run` or `skip_superseded`. The scripts match the literal and the value apart. Only the literal parts are in the `decisive-strings` block. `decisive-strings-external` holds strings the app does not print; check those against the round 2 and `pr-1047-session-reconnect-round3` captures, not the source.

## 8. Runs

Estimated time: Prepare and two builds 30 min, R2A 12 min, R2K 10 min, P1 7 min, the cable change 5 min, U0 8 min, U1 7 min, Closing 5 min. **Round total: about 85 min**, plus D-POCO's charge before Prepare. Write the results file after every run, as the thermal rule asks.

**Preflight in every run.** `readers` prints 0 before the run starts. The run's `.keys` file shows its keys read back and the right `commit`. Stage U captures hold `Found device already in accessory mode` at least once and `MATCH! Starting AapService` zero times. U1 holds `stopping the wireless stack for the duration of it` after its first session. If a check fails, the run is a setup failure: fix the cause, re-run it once, and do not grade the failed attempt.

**Valid cycle** (R2A, R2K). As round 2: both captures carry its `-start` and `-end` markers; each verb has its `AutomationReceiver: ` line; `match=0`; form A has `Disconnect action received.` in the window, form K has at least one `Found device already in accessory mode` after the relaunch. Name the failed item for any cycle that does not count.

### Prepare (P), on cables

1. **Builds and identity** (section 1). Record both md5s.
2. **D-POCO battery:** `adb -s 4f4027e9 shell dumpsys battery | grep -a -m1 level`. 80% or more. Record it.
3. **D-POCO API level:** `adb -s 4f4027e9 shell getprop ro.build.version.sdk`. Record it. Below 29 makes P1 UNTESTABLE.
4. **Back up D-POCO's settings** with the app stopped: `adb -s 4f4027e9 shell am force-stop com.andrerinas.headunitrevived; adb -s 4f4027e9 shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml > $OUT/settings_backup_poco.xml`. Diff it against round 2's `settings_backup_poco.xml` and state the delta.
5. **USB grants:** `adb -s 4f4027e9 shell dumpsys usb | grep -i -A4 headunitrevived > $OUT/dumpsys-usb-grants-before.txt`. Report any "Always" grant.
6. **Install C on D-POCO:** `adb -s 4f4027e9 install -r -d apks/candidate-bcf3b1a3.apk`. Diff `settings.xml` against the backup. Run the 1a gate on C.
7. **D-MOTO:**
   ```bash
   adb -s ZY22GC3BM4 shell dumpsys package com.google.android.projection.gearhead | grep -a -m1 versionName
   adb -s ZY22GC3BM4 shell pm list packages -d | grep -acF com.google.android.projection.gearhead   # must print 0
   adb -s ZY22GC3BM4 shell svc power stayon true
   adb -s ZY22GC3BM4 shell dumpsys window | grep -a -E "mShowingLockscreen|mDreamingLockscreen"
   adb -s ZY22GC3BM4 shell input keyevent KEYCODE_HOME
   adb -s ZY22GC3BM4 shell dumpsys bluetooth_manager | grep -a -i -A30 "Bonded devices" > $OUT/moto-bonds.txt   # the dongle carplay_box_F96B must be listed
   ```
   Record the Android Auto version (round 2 ran `17.9.664004-release`).
8. **D-HU:** `adb -s 27870808938846 shell am force-stop com.andrerinas.headunitrevived`. It takes no part.
9. **The one batched request to the operator.** Send it once, then wait for the answer:
   > "Round 3 needs you now, once between its two stages, and at the end. 1) If D-MOTO is behind a PIN, unlock it. 2) Open the cue terminal: `tail -n0 -F <OUT>/hand-steps.log`. 3) When I say 'plug', unplug D-POCO from the PC, unplug D-MOTO from the PC, plug D-MOTO into D-POCO's OTG port, and set 'USB controlled by' to 'This device' on D-MOTO. 4) After about 35 minutes a cue asks you to move D-MOTO back to its PC cable and plug the dongle into D-POCO's OTG port. 5) At the end a cue asks you to unplug the dongle and put D-POCO back on its PC cable. Other cues appear only if a scripted recovery fails or a system USB dialog appears. Never tick 'Always' for D-MOTO."
10. **Switch to wireless adb**, as in round 2:
    ```bash
    adb -s 4f4027e9 tcpip 5555; adb -s ZY22GC3BM4 tcpip 5555; sleep 3
    POCO_IP=$(adb -s 4f4027e9 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
    MOTO_IP=$(adb -s ZY22GC3BM4 shell ip -4 addr show wlan0 | grep -o 'inet [0-9.]*' | cut -d' ' -f2)
    adb connect $POCO_IP:5555; adb connect $MOTO_IP:5555
    ```
    Then say "plug" (step 9, part 3). Check that `adb -s $POCO_IP:5555 shell getprop ro.product.model` and `adb -s $MOTO_IP:5555 shell getprop ro.product.model` both answer.
11. **Shell:**
    ```bash
    HU=$POCO_IP:5555; PH=$MOTO_IP:5555; BASE=$OUT/settings_backup_poco.xml
    KEYS="(section 5)"; P1KEYS="(section 5)"
    source ./lib1008r3.sh; source ./libreattach1.sh; source ./libreattach2.sh; source ./libreattach3.sh
    WANT_MD5=<C md5>; WAIT_AUTO=90
    readers          # must print 0
    ```

### Stage U, on C

#### R2A. Form A on the candidate: the slot is free after every step (the point of the round)

```bash
RUN=R2A; bat_gate && urun2 R2A A 5
nfail R2A
```

Round 1's R2, unchanged: each cycle ends the live session with `ACTION_DISCONNECT`, waits 10 s and asks for USB. In round 1 every such cycle failed once (`TRANSPORT_ERROR`), took step 1 (`RESWITCH`, no re-enumeration), retried, and formed a session. Here the cycle after that is the test: its `ACTION_CHECK_USB` only runs if the previous step freed the slot.

- **PASS:** all of these hold.
  1. `nfail R2A` is 2 or more.
  2. Every failing cycle in c1 to c5 ends `AUTO_SSL` in `summary.tsv` (no `REC=...`, no `HAND_SSL`).
  3. `checks_ran` equals `checks` in every cycle from c0 to c5, and every cycle reached its `-end` marker (no `STOPPED at`).
  4. `miss=0` and `extra=0` in every cycle (R2T, below).
  5. Phone: `ph.gh` is 1 or more in every cycle with `ssl_auto=1`.
- **FAIL:** any item does not hold while item 1 holds. For item 3, quote the cycle's `.ladder` lines from its first `AutomationReceiver: ` line with `ACTION_CHECK_USB` to its `-end` marker.
- **INCONCLUSIVE:** `nfail R2A` is below 2.
- **Report** per cycle: `fails`, `kinds`, `step1`, `re1`, `nc1`, `step2`, `re2`, `checks`, `checks_ran`, `replay`, `busy`, `arb`, `sfail`, `dt_ms`, the outcome, `ph.enter_auto`, `ph.gh`.

#### R2K. Form K on the candidate: USB reset samples

```bash
RUN=R2K; bat_gate && urun2 R2K K 5
nfail R2K
```

Round 2's R2K, unchanged. Each cycle force-stops and relaunches the app, so the ladder starts from a fresh service; this run re-proves the reset on the rebuilt step, not the slot across cycles. `c0` is a form K cycle too, so grade over `c0` to `c5`.

- **PASS:** all of these hold.
  1. `nfail R2K` is 3 or more.
  2. Every failing cycle ends `AUTO_SSL`.
  3. `miss=0` and `extra=0` in every cycle, and `checks_ran` equals `checks`.
  4. Each `USB_RESET` step line is followed within 3 s by `UsbAccessoryMode: USB reset issued to ` or `... not issued to `, and within 20 s by exactly one observation line for `USB_RESET`. Read the times in the cycle's `.ladder` file. `rstok + rstno` equals `step2`, and `re1 + nc1 + re2 + nc2 + left` equals `step1 + step2`.
  5. Phone: `ph.enter_auto` is 1 or more in every cycle with `re2=1`.
- **FAIL:** an item does not hold. Quote the cycle's `.ladder` lines.
- **INCONCLUSIVE (reset part):** `step2=0` in every cycle. Items 1 to 3 are still graded.
- **Report:** `re2` out of `step2` with each `re-enumerated the phone in` time, `re1` out of `step1`, `dt_ms`, `avc`, `unative`, `kinds`, `replay`, `busy`.
- **What a PASS would look like if the reset did nothing:** every `USB_RESET` step logs `no re-enumeration within ... of USB_RESET`, `re2=0` and `ph.enter_auto=0`. So item 5 and the `re2` count are the measurement.

#### R2T. The trigger, graded over R2A and R2K

No run of its own. Graded over every counted cycle of R2A and R2K, `c0` included.

- **PASS:** `miss=0` and `extra=0` in every cycle, and the sum of `fails - exempt` is **3 or more**.
- **FAIL:** any `miss` or `extra`. Quote each line from `misses.log` with the 10 lines before it from the cycle's `.ladder` file.
- **INCONCLUSIVE:** fewer than 3 counted failures.
- **Report:** the sums of `fails`, `exempt`, `ok` and `sfail`, and `kinds` per cycle. `sfail` below `fails - exempt` is a finding to report, not a FAIL: the service's line sits behind the same guard as the report.

#### P1. The raise deadline holds after the second unprojected session

```bash
p1run
```

`c0` brings a session up with `MainActivity` in front, so its raise goes DIRECT and it projects. In `c1` the driver ends it with `ACTION_DISCONNECT`, presses HOME, and asks for USB from the background with the overlay off. Expected: the session forms (through the ladder if the phone is stale), the raise goes by NOTIFICATION twice, the deadline ends it (`END_AND_RECOVER`), the 3 s USB check forms a second session, that one also cannot raise, and the deadline ends it as a held end. Then nothing reconnects.

**Setup failure (re-run P1 once, do not grade):** `fg_ours` is above 0, or `wake` or `wake_end` is not `mWakefulness=Awake`, or `overlay_now` is not `deny`.

Grade from the two `P1 ...` lines in `cycles.tsv`.

- **INCONCLUSIVE** (the condition under test was not reached; name which):
  - `raise_notif` is below 2, or `raise_direct + raise_overlay` is above 0, or `throughput` is above 0 (a raise worked);
  - `ssl_before_hold` is below 2 and `held_ends` is 0 (no second session formed);
  - `no-scan-in-15s` in `summary.tsv` (the USB verb did not reach the service from the background);
  - `quiet_span_ms` is below 60000 because `attach_after_hold=1`. Grade items 1 to 3 below anyway, and report the attach and `ssl_after_attach`.
- **PASS:** all of these hold.
  1. `ends` is 2, `recovered_ends` is 1 and `held_ends` is 1.
  2. `recovered_end_rescheduled` is 1: the recovered end still scheduled the 3 s USB check, so the bound is what stopped the loop.
  3. `notrec` is 1 and `notrec_within_2s` is 1.
  4. After the hold, until `P1c1-end` or the first attach: `ssl_after_hold`, `found_after_hold`, `sched_after_hold` and `single_after_hold` are all 0, over a `quiet_span_ms` of 60000 or more.
  5. Phone: `ph.gh_before_hold` is 1 or more (Android Auto took part in the sessions).
- **FAIL:** `no-hold-in-150s` with `ends` of 3 or more (the loop is still unbounded), or any count in item 4 above 0, or `ends` above 2.
- **Report:** `hold_at_ms` (from the trigger), every `raising the projection by` line with its `(overlay=..., foreground=...)` tail, `ph.BYEBYE`, `ph.ByeBye`, `ph.gal_deliberate`, `ph.enter_after_hold`, `ph.crit_after_hold`, and in one short paragraph what `P1.phone_after_hold` shows Android Auto did in the 60 s after the second ByeBye: a reconnect attempt, an error screen, or nothing. The ByeBye reason stays `USER_SELECTION`; no source says how Android Auto treats it over USB.

#### Close Stage U

1. `send ACTION_EXIT`. Then `adb -s $HU shell am start -a android.intent.action.VIEW -d headunit://exit`, `sleep 3`, `adb -s $HU shell am force-stop com.andrerinas.headunitrevived`.
2. Restore D-POCO's backup with `pocoput $BASE` (no spec) and read it back.
3. Read `appops get com.andrerinas.headunitrevived SYSTEM_ALERT_WINDOW` and `settings get system screen_off_timeout` on D-POCO. Each must equal the value `p1run` saved. If not, set it again.
4. `readers` must print 0.
5. `cue "H1b: unplug D-MOTO from D-POCO and put it on its PC cable, then plug the dongle into D-POCO's OTG port"`. Wait for the operator's answer.
6. `adb -s $MOTO_IP:5555 usb`. Check that `adb -s ZY22GC3BM4 shell getprop ro.product.model` answers. Then `adb -s ZY22GC3BM4 shell svc wifi enable; adb -s ZY22GC3BM4 shell svc bluetooth enable; sleep 3` and read both back with `dumpsys`.

### Stage D, on E (new shell, section 6)

#### U0. Stage D setup and gate (once)

```bash
adb -s $HU shell getprop ro.product.model                                   # must answer over wireless adb
adb -s $HU shell ls /sys/bus/usb/devices                                     # must show more than the two root hubs; else cue the operator to reseat the dongle, once
adb -s $HU shell cat /proc/net/tcp /proc/net/tcp6 | grep -aiE ':149D [0-9A-F]+:0000 0A' && echo "D-POCO SERVES 5277"   # must print nothing
adb -s $MOTO shell dumpsys bluetooth_manager | grep -a -m2 -iE '^ *(enabled|state):'   # must read enabled / ON
adb -s $HU shell am force-stop $PKG
adb -s $HU install -r -d <path>/apks/export-bcf3b1a3.apk; ARM=E; WANT_MD5=<E md5>
```

Then: the 1a gate on E; settings diff after the install; then the screen check: `dest_id` prints a non-zero id, `open_audio` returns 0 with route `am`, `ui_dump && target settingSwitch && target save_button_widget` prints two coordinate pairs, then `close_settings`. No tap in U0. Then write `U1KEYS`, launch, and form the first session: `$PUT $BASEXML $U1KEYS; L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null; usb_live $L`.

- **PASS:** wireless adb answers; no 5277 listener; the 1a gate and the screen check pass; a USB session forms and `stopping the wireless stack for the duration of it` follows it.
- A failed U0 makes U1 UNTESTABLE. Say why.

#### U1. A USB Save with the settings screen open for 35 s (E, 2 cycles, 4 taps)

The `pr-1047-session-reconnect-round3` block, with the run named `U1-E`:

```bash
r_open U1-E $U1KEYS; L=$(nl); adb -s $HU shell am start -n $MAIN >/dev/null; usb_live $L || echo "U1 no USB session at start"
echo "U1 preflight native=$(cnt $L '$' 'WifiLauncher: Initializing WiFi Mode: NATIVE') quiesce=$(cnt $L '$' 'stopping the wireless stack for the duration of it')" | tee -a $OUT/U1-E.tsv   # quiesce 1 or more
done=0
for k in 1 2; do
  S=$(nl); p0=$(pcnt 'Critical error'); f0=$(pcnt 'FATAL EXCEPTION'); mark U1-$k-go
  tap_save U1-$k || continue
  hold_until 35000; C0=$(nl); close_settings; waitfor 60 'SSL handshake complete' $C0; E=$(nl)
  FS=$(lno $SAVE_LN 'SSL handshake complete')
  R="U1-$k\tretry=$(cntw 0 5000 'SettingsRestart: route=USB retry=run')\tdetach=$(cntw 0 35000 'USB_DEVICE_DETACHED')\tattach=$(cntw 0 35000 'USB_DEVICE_ATTACHED')\treattach=$(cntw 0 35000 'USB accessory device attached, connecting.')"
  R="$R\theld=$(cntw 0 35000 'UsbLauncher: USB auto-connect held while the settings screen is open; ')\ttries_before_ssl=$( [ $FS -gt 0 ] && usb_tries $SAVE_LN $FS || echo NA )\tt_ssl2=$(ms_of $FS)"
  R="$R\tssl_before_close=$(cnt $SAVE_LN $C0 'SSL handshake complete')\tquiesce=$( [ $FS -gt 0 ] && cnt $FS $E 'stopping the wireless stack for the duration of it' || echo NA )\tarm_in_window=$(cntw 0 35000 'WifiLauncher: Initializing WiFi Mode: NATIVE')\tpokes_in_window=$(cntw 0 35000 "$POKE")"
  R="$R\tfallback_run=$(cntw 0 60000 'fallback=run')\tfallback_other=$(( $(cntw 0 60000 'fallback=skip_superseded') + $(cntw 0 60000 'fallback=cancel_superseded') ))\traise_refused=$(cnt $SAVE_LN $C0 'AapService: Not raising the projection, the settings screen is open')"
  R="$R\tsettings_restart=$(cntw 0 3000 '(settings_restart)')\tusb_retry_line=$(cnt $SAVE_LN $E 'AapService: USB disconnect. Scheduling reconnect check in ')\taccstart=$(cntw 0 35000 'Sending acc start')\tstale_steps=$(cntw 0 60000 'UsbLauncher: stale accessory ')\treplay=$(cntw 0 60000 'replaying the USB scan queued during the attempt')"
  R="$R\tcrit=$(( $(pcnt 'Critical error') - p0 ))\tfatal=$(cnt $S $E 'FATAL EXCEPTION')\tphone_fatal=$(( $(pcnt 'FATAL EXCEPTION') - f0 ))"
  echo -e "$R" | tee -a $OUT/U1-E.tsv
  done=$((done+1)); [ $done -ge 2 ] && break; sleep 10
done
r_close U1-E
```

- **PASS, on every completed cycle:** `retry` 1; `ssl_before_close` 1 or more with `t_ssl2` 35000 ms or less (the session formed behind the open screen); `tries_before_ssl` 1 or more; `quiesce` 1 or more; `held` 0; `arm_in_window` 0 and `pokes_in_window` 0; `fallback_run` 0; `raise_refused` 1 or more; `settings_restart` 1; `fatal` 0. Phone (D-MOTO, graded): `phone_fatal` 0.
- **FAIL:** `held` 1 or more, or `ssl_before_close` 0 with the dongle back on the bus (`attach` or `reattach` 1 or more), or `arm_in_window` 1 or more, or `fallback_run` 1 with a session formed before 30 s.
- **Report:** `detach`, `attach`, `reattach`, `tries_before_ssl`, `fallback_other`, `usb_retry_line`, `crit`, and for item 3 `accstart` per cycle (more than one `Sending acc start` for one return of the dongle is a finding to quote), `stale_steps` (any ladder line on the dongle, quoted) and `replay`.
- **What a PASS would look like if the change did nothing:** `pr-1047-session-reconnect-round3`'s shape: `held` 5 per cycle, `ssl_before_close` 0, and SSL 46 to 51 s after the Save.
- **Stop rule:** 2 completed cycles, or 2 attempts. A cycle voided by `tap_save` is not completed. Fewer than 1 completed cycle makes U1 INCONCLUSIVE.

### Closing

Run all of it, whatever happened before. If the round stopped early, start here.

1. `send ACTION_EXIT`. Then `adb -s $HU shell am start -a android.intent.action.VIEW -d headunit://exit`, `sleep 3`, `adb -s $HU shell am force-stop com.andrerinas.headunitrevived`.
2. **Reinstall C on D-POCO** so that E does not stay: `adb -s $HU install -r -d <path>/apks/candidate-bcf3b1a3.apk`, then check the md5 and diff `settings.xml`.
3. Restore D-POCO's backup with `pocoput` and read it back. The diff must be empty.
4. Read the overlay app-op and `screen_off_timeout` on D-POCO once more against the values `p1run` saved.
5. `adb -s $HU shell dumpsys usb | grep -i -A4 headunitrevived > $OUT/dumpsys-usb-grants-after.txt`. Diff it against the Prepare copy and report any new grant.
6. `adb -s ZY22GC3BM4 shell svc power stayon false`.
7. `cue "H1c: unplug the dongle from D-POCO, and put D-POCO back on its PC cable"`. Then `adb -s $POCO_IP:5555 usb`.
8. `pgrep -fc "^adb .*logcat"` must print 0.
9. The local `arm-E-r3` commit stays local. Do not push it.

### Stop rules

- `urun2` stops a run at the first cycle that ends `NO_SSL` (`STOPPED at c<n>`). Go on to the next run. In R2A that stop is itself graded (item 3).
- If R2A and R2K both stop at `c0` with `NO_SSL`, the rig cannot form a USB session. Skip P1, run the stage close, and go on to Stage D.
- `bat_gate` false (D-POCO at 25% or below): stop, run the closing, and mark the rest UNTESTABLE (battery).
- If 2 cycles in Stage U end `REC=CABLE`, finish the run in progress, then go to P1.
- The host thermal rules of `rig-quirks/topics/tooling.md` apply. `urun2` and `p1run` gate on them; `r_open` does in Stage D.

## 9. Do not re-run

- **RC5, RCK, RC1** (plain retries on `main`): round 2, 0 of 11 recovered. The new commits do not touch `main`'s plain path.
- **RG** (give-up, bound, banner and clearing): round 2 PASS. The `GIVE_UP` branch and the banner record were replayed with conflict resolution only; `ConnectionIssueBannerPolicyTest` and `StaleAccessoryRecoveryPolicyTest` cover them.
- **R3** (the cable form), **R5u** (a user exit during a stale handshake), **R4p and R4w** (the dongle as a negative control): round 1 PASS. U1 reports any ladder line on the dongle.
- **Items 3 and 4** have no cheap rig trigger (a slow bus during a launcher retry; a USB attach inside a wireless SSL). Their JVM tests are the proof. U1 reports `accstart` for item 3.
- **N2 and ND** of `pr-1047-session-reconnect-round3`: out of scope for this branch.
- Two permission prompts per connect: structural (template §7b). Report any dialog; do not grade the count.

## 10. Report back

The numbers that decide shipping:

1. **R2A, the slot:** failing cycles that ended `AUTO_SSL`, out of failing cycles in c1 to c5, and `checks_ran` against `checks` summed over c0 to c5. Add R2K's `re2` out of `step2` and R2T's `miss` and `extra` sums.
2. **U1, the Save:** per cycle, `held`, `ssl_before_close`, `t_ssl2` and `arm_in_window`.
3. **P1, the bound:** `ends`, `held_ends`, `ssl_after_hold` with `quiet_span_ms`, and what Android Auto did after the second ByeBye.

Also report every `REC=...` line, every cue and every injected tap.

Put the captures in `usb-reattach-round3-captures.zip` on the release `rig-evidence-usb-reattach`, and quote its size and sha256 in the results file. Include `cycles.tsv`, `summary.tsv`, `misses.log`, `dialogs.log`, `hand-steps.log`, `taps.log`, `marks.log`, every `.keys`, `.ladder` and `.tsv` file, `P1.twin` and `P1.phone_after_hold`.
