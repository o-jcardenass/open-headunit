# usb-aoa-handoff — round 2 results

**Candidate:** `fix/usb-attach-clear-defaults` @ `b236103c` (`ee16f522` + `b236103c` on `main` @
`c4dd2ba1`), merged into `testing/wireless-plus-automation` @ `1f4b85ce`
(`1f4b85ce5a593e45e0e2a0ae5155347c9c727c93`) with `feat/automation-command-surface` @ `41a3866b`
(instrument) and `fix/wireless-bring-up-and-5ghz-channel` @ `e6ed1ad2` (third input).
**Baseline:** none (round 1's baseline was the branch's own parent, which no longer exists; the one
run needing a control, R8, is UNTESTABLE here — see below).
**Built tree:** `testing/wireless-plus-automation`, build stamp `1f4b85ce5a59` (not dirty)
**APK md5:** `b892fcf6cc3740bee42e254ba8277559`
**Unit gate:** 1414 / 0 (`testGithubDebugUnitTest`)
**Unit:** POCO X3 NFC (`M2007J20CG` `surya`, Android 13, sm6150) as head-unit / USB host over OTG,
wireless adb `192.168.1.10:5555`. Attached devices: Motorola edge 30 neo (`22b8:2e82`, USB
debugging OFF) for R7/R8; Carlinkit-class AA dongle (idle `18d1:4ee1` PTP, accessory `18d1:2d00`)
for R10/R11/R3/R2.
**Date:** 2026-09-05

## Verdicts

| Run | Verdict | One line |
|---|---|---|
| R0 build gate | **PASS** | stamp `1f4b85ce5a59`, DEX carries `UsbSwitchClaim` + `UsbEndpointSelectionPolicy` + `UsbAccessoryHandoffPolicy` (10 hits), gate 1414/0, md5 `b892fcf6…`, `QUERY_STATE` commit matches |
| R7 non-Pixel phone gets an AOA switch — **the point of the round** | **UNTESTABLE** | on a phone host, a phone in MTP mode (`FF/FF/00` named MTP) with no default handler is short-circuited by AOSP's `resolveActivity` (TESTING-TEMPLATE §7b); the `USB_DEVICE_ATTACHED` intent never reaches `UsbAttachedActivity`, so `ee16f522`'s change cannot be exercised by plugging a phone here. Coverage stays on `UsbAttachPolicyTest`. |
| R8 configured allow-list still refuses + hands off — **the control** | **UNTESTABLE** | same mechanism as R7 — needs the attach activity to run |
| R10 the AOA control transfer no longer gives up early | **PASS** (regression guard) | `Success controlTransfer len: 2  acc_ver: 2` at **+14 ms** after `Switching USB device to accessory mode`; **0** `Error controlTransfer len: -1`; full SSL session + video followed |
| R11 the attach trampoline does not linger | **PASS** | `UsbAttachedActivity` absent from `dumpsys activity recents` and `…activities`; **0** `Usb re-attached in normal mode`; every attach hits a fresh `onCreate`, never `onNewIntent` |
| R3 replug ×5 — regression guard for the narrowed switch claim | **INCONCLUSIVE** (degraded link, pre-registered §3) | `Sending acc start` = **5** (one switch per cycle, no double-switch); `didn't handle` = **2/5** — but both are `ee16f522`'s service hand-off after a slow accessory-permission re-request, **not** the narrowed `UsbSwitchClaim` "leaving to it" path (which fired 0 times). 3/5 cycles reached SSL. |
| R2 cold plug, libusb route | **PASS** | one `Performing AOA switch` (native, ~22 ms), **0** `didn't handle`, `Successfully connected via JNI Libusb`, **0** `Unable to find an endpoint pair on the accessory interface`, `SSL handshake complete`, ~55 s of video `dropped=0 skipped=0 concealed=0` (HEVC HW), on the exact link R3's standard route only made 3/5 |

---

## Setup notes

### Deviations / conditions

1. **Clean-run protocol §4 not used** (per brief §3). USB runs were force-stop, `logcat -c`, start
   both readers (`OPENHU:V '*:S'` and `-s UsbHostManager:D`), then plug.

2. **Two capture readers per §7b**, via `round-usb-aoa-handoff-r2/capture.sh` (a `sed`-renamed copy
   of round 1's). The first R3 attempt (`r3-ohu.txt`, 4 lines) **died mid-run** — `adb logcat` over
   wireless adb dropped while the dongle was being replugged fast; that attempt is void and R3 was
   re-run as `r3b` at a slower cadence. An inline `stdbuf … adb logcat &` from within a single Bash
   call does not survive the call; `capture.sh`'s separate `start`/`stop` invocations do.

3. **R7/R8 UNTESTABLE — the host, not the branch.** The Motorola enumerated as pure MTP:
   `22b8:2e82`, one config `mtp`, one interface `mId=0 mName=MTP mClass=255 mSubclass=255
   mProtocol=0` (`FF/FF/00`), **no ADB interface** (debugging confirmed off), no audio/HID/storage.
   With OHU force-stopped and no default handler for `22b8:2e82` (the one `device_preferences`
   entry in `dumpsys usb` is for `18d1:4ee1` "Pixel 4" serial `99021FFAZ006X3` — the dongle, not
   the phone), AOSP's `UsbProfileGroupSettingsManager.resolveActivity` short-circuits an
   `FF/FF/00`-named-MTP device to an MTP notification and launches no activity (TESTING-TEMPLATE
   §7b). There is **no manifest `BroadcastReceiver` for `USB_DEVICE_ATTACHED`** — only
   `UsbAttachedActivity` — and its `UsbReceiver` is runtime-registered (needs the app already
   running). Result: `r7-ohu.txt` is **empty**; a confirmatory full-logcat replug
   (`r7-confirm-fulllog.txt`) shows the system doing nothing with the attach and OHU never
   starting. `ee16f522` removes the `0x18D1` gate in `UsbAttachedActivity` and the service
   fallback, but on this phone host the attach never reaches either. A real `FEATURE_AUTOMOTIVE`
   head unit skips the short-circuit; the MT50 cannot host USB. Coverage is `UsbAttachPolicyTest`
   (94 lines in `ee16f522`). This is the §8 "check each run is physically possible on this rig"
   trap — the brief referenced §7b but R7's setup still assumes the attach is delivered.

4. **R10/R11/R3/R2 substitute the dongle for the phone.** The dongle enumerates idle as
   `18d1:4ee1` with a PTP interface (`06/01/01`), which is *not* MTP-short-circuited and *is*
   matched by `usb_device_filter.xml`, so it reaches `UsbAttachedActivity` and OHU switches it
   `4EE1→2D00`. R10 (control-transfer timing) and R11 (trampoline) are about the switch/activity
   mechanics, not the phone-identity gate, so the dongle exercises them. Noted rather than absorbed.

5. **The dongle link is degraded, progressively, exactly as round 1 recorded.** `num_connects` in
   `dumpsys usb` ran **211 → 275** over the round (the dongle self-reverts `4EE1↔2D00` every
   ~400 ms whenever nothing claims it, and the R3 replug cycles added ~30 more). Standard-route
   sessions: R10 formed, R3 made 3/5, R2's *libusb* route formed clean. `USB reads recovered after
   N errors` appears on every standard-route open. Round 1's queued item stands: a fresh cable, a
   new OTG adapter, or a second dongle is needed before the standard route can get a clean verdict.
   R3 was pre-registered INCONCLUSIVE on these terms (§3).

6. **`dumpsys usb` before:** `user_permissions` empty (no "always"-ticked persistent grant). One
   `device_preferences` default handler, for `18d1:4ee1` "Pixel 4" (the dongle) → OHU, left from a
   prior session — matches a returning device, not touched. **After:** `user_permissions` now
   carries a `device_permissions` block (transient per-bus-path grants from the runs'
   `usbManager.requestPermission()` auto-grants; these drop on re-enumeration, no "always" ticked).
   No dialog was ticked at any point.

7. **No `systemui.usb.*` activity in any capture** — no system chooser and no visible
   `UsbPermissionActivity`. The dongle's `2D00` is manifest-matched, so `requestPermission()`
   auto-grants silently (R10: 1 call, R3b: 4, R2: 0). The "third dialog identity" watch item (§8)
   is clean: zero dialogs of any kind.

8. **Settings** written with `set_prefs_runas.sh` (POCO not rooted). R7/R8: `allow-devices`
   removed, `auto-start-on-usb=false`, `use-libusb=false`, `log-level=0`. R10/R11/R3:
   `auto-start-on-usb=true`, `use-libusb=false`. R2: `use-libusb=true`. Restored at round end to
   the backup (`use-libusb=false`, `log-level=0`, `auto-start-on-usb` removed); the only residual
   delta vs the backup is two app-written `last-loc-latitude`/`last-loc-longitude` keys from the
   projected sessions. Candidate APK left installed.

### Scripts

- Used: `build_hur.sh`, `run_unit_tests.sh`, `set_prefs_runas.sh`.
- New: `hur-wifi-test-scripts/round-usb-aoa-handoff-r2/capture.sh` (round 1's, `sed`-renamed).

---

## R0 — build gate

**PASS**

- `./gradlew :app:assembleGithubDebug` → `Building from commit: 1f4b85ce5a59` (no `-dirty`).
- `unzip -p <apk> classes*.dex | strings | grep -c -e UsbSwitchClaim -e UsbEndpointSelectionPolicy
  -e UsbAccessoryHandoffPolicy` → **10** (non-zero; 0 on any `main` build).
- Unit gate **1414 / 0**.
- On-device md5 `b892fcf6cc3740bee42e254ba8277559` matches host; `adb install -r`.
- `send ACTION_QUERY_STATE` → `"commit":"1f4b85ce5a59"`, `versionCode 105`.

---

## R7 / R8 — non-Pixel phone attach

**UNTESTABLE** on this phone host (TESTING-TEMPLATE §7b MTP short-circuit).

- Motorola descriptor (`r7-usb.txt`): `vidpid 22b8:2e82`, `motorola/motorola edge 30 neo/5.04`,
  `hasAudio/HID/Storage: false/false/false`; single config `mtp`, single interface
  `FF/FF/00` named `MTP`. It re-enumerated once (`001/002` → `001/003`), both times identical MTP.
- `r7-ohu.txt`: **empty** — OHU never started, no `UsbAttachedActivity`, no
  `UsbDeviceDiagnostics.logDeviceList`, no switch, no `letting the service decide`.
- Confirmatory full-logcat replug (`r7-confirm-fulllog.txt`): no `resolveActivity` /
  `UsbProfileGroupSettingsManager` chooser, no OHU line of any kind.
- The one default handler present is for `18d1:4ee1` (the dongle), keyed by serial — it does not
  match `22b8:2e82`, so the short-circuit applies and no activity is launched.

`main`'s refusal string (`Skipping device … (not allowed and USB auto-start disabled)`) does not
exist on this branch, so its absence proves nothing, and the positive line
(`Switching USB device to accessory mode`) never had a chance to appear. R8's control
(`Not switching … letting the service decide` → `Fallback: force=true …`) is blocked identically.
`ee16f522` is covered by `UsbAttachPolicyTest`.

---

## R10 — the AOA control transfer no longer gives up early

**PASS** (regression guard)

Own plug of the dongle, `use-libusb=false`. Decisive lines (`r10-ohu.txt`):

```
23:27:56.539  UsbAttachedActivity.onCreate | Switching USB device to accessory mode Google Pixel 4 (VID: 18D1 PID: 4EE1)
23:27:56.553  UsbAccessoryMode.switch | Success controlTransfer len: 2  acc_ver: 2
23:27:56.555–.563  initStringControlTransfer | Success controlTransfer len: 7/12/12/5/45/12  (all six strings)
23:27:56.563  UsbAccessoryMode.switch | Sending acc start
23:27:57.420  UsbLauncherManager.checkAlreadyConnected | Found device already in accessory mode: … 2D00
23:28:01.369  StandardUsbProjectionConnection.usbOpen | Established connection
23:28:02.172  AapSslContext.performHandshake | SSL handshake complete
```

- `Switching USB device to accessory mode` → first `Success controlTransfer` = **14 ms**.
- `Error controlTransfer len: -1` — **0**. `didn't handle` — 0.
- A full session formed (SSL complete, 1010 `RECV: VIDEO Media Data` lines followed).

**What a PASS means here:** 14 ms is well under `main`'s 100 ms constant, so `main` would also pass
— this dongle answers the first control transfer fast, so `USB_TIMEOUT_IN_MS = 1000` is not
*exercised* (the 100–1000 ms band that only the new constant tolerates was never entered). It is a
clean **regression guard**: no early give-up, no `Error controlTransfer len: -1`. The demonstration
of the 1000 ms value mattering needs a genuinely slow device, which the brief routed to R7 (the
Motorola) — and R7 is UNTESTABLE here.

---

## R11 — the attach trampoline does not linger

**PASS** (both checks)

- **Check 1:** `dumpsys activity recents | grep -c UsbAttachedActivity` = **0**, and
  `dumpsys activity activities | grep -c UsbAttachedActivity` = **0**, immediately after an attach.
  `noHistory` + `excludeFromRecents` + `taskAffinity=""` are in effect. (Recent #0 is the app's own
  `MainActivity`/`AapProjectionActivity` task — expected, not the trampoline.)
- **Check 2:** unplug → 10 s → replug. `Usb re-attached in normal mode; asking the service to check
  it` — **absent**. Both attach events hit `UsbAttachedActivity.onCreate` (the second on a **new
  process**, PID 2916); `onNewIntent` fired **0** times. No stale instance survived the first
  attach, so the `onNewIntent` path is now unreachable on this manifest config — which is what the
  run confirms.

---

## R3 — replug ×5 (regression guard for the narrowed switch claim)

**INCONCLUSIVE** — standard-route link degraded (pre-registered §3). First attempt void (capture
died). Re-run as `r3b`, 5 cycles at ~15 s unplugged / ~15 s plugged.

| cycle | `Switching…4EE1` | `Sending acc start` | `Established` (acc→) | SSL | `didn't handle` |
|---|---|---|---|---|---|
| 1 | 23:35:56.892 | 23:35:56.914 | 23:35:59.254 (2.34 s) | 23:36:00.081 **OK** | 0 |
| 2 | 23:36:51.745 | 23:36:51.767 | 23:36:53.718 (1.95 s) | 23:36:54.576 **OK** | **1** @ 23:36:53.702 |
| 3 | 23:37:49.278 | 23:37:49.290 | 23:37:51.213 (1.92 s) | 23:37:52.166 **OK** | 0 |
| 4 | 23:38:47.240 | 23:38:47.246 | 23:38:48.876 (1.63 s) | 23:38:49.919 OK, then dropped → retry SSL-fail | 0 |
| 5 | 23:39:11.274 | 23:39:11.282 | 23:39:13.558 (2.28 s) | **FAIL** (`SSL performHandshake failed`) | **1** @ 23:39:13.252 |

- **`Sending acc start` = 5** — exactly one switch per cycle, no double-switch, no dialog.
- **`didn't handle` = 2** (cycles 2 and 5). The brief's PASS bar is 0, but in both cases the
  sequence is: switch → `acc start` → `Found already in accessory 2D00` →
  `Accessory-mode device has no permission (re-enumerated); requesting permission` → ~0.3–0.5 s
  later `UsbAttachedActivity didn't handle … 4EE1. Trying from service…` → service
  `checkAlreadyConnected` → `Established`. In cycles 1/3/4 the accessory permission arrived faster
  (`Accessory-mode permission arrived after 438 ms` in C1) and the activity handled it directly.
- **The narrowed `UsbSwitchClaim` read was never the deciding factor.** Its line
  (`A USB accessory switch is already in flight; leaving <device> to it`) fired **0 times** in the
  whole capture — no *second* attach ever raced the switch; the dongle transitions as a single
  device (`4EE1` → gone → `2D00`). The `didn't handle` events are `ee16f522`'s intentional new
  hand-off (activity → service, instead of `finish()`) triggered by accessory-permission
  re-request latency, and the session still formed when the link allowed (cycle 2). Round 1's
  0/10 was on a dongle that kept its permission / re-enumerated faster.
- Sessions: 3/5 to SSL (cycles 1, 2, 3). Cycles 4 and 5 failed at SSL on the degraded link
  (`USB reads recovered`, `SSL performHandshake failed`) — the `#800` marginal-USB-2.0 signature,
  not the branch.

**Net:** the regression-guard question R3 exists to answer (does the narrower `UsbSwitchClaim` read
still cover a *second racing attach*?) was **not exercised** — no second attach raced on this
single-device dongle. What the run did surface is that `didn't handle` now fires on
accessory-permission re-request latency; the code side should decide whether that hand-off is
acceptable noise or wants tightening. A clean verdict needs a non-degraded dongle link.

---

## R2 — cold plug, libusb route

**PASS**

`use-libusb=true`, one plug. Decisive lines (`r2-ohu.txt`):

```
23:43:08.988  Switching USB device to accessory mode … 4EE1
23:43:09.000  UsbAccessoryMode.connectAndSwitch | Performing AOA switch via native libusb...
23:43:09.022  UsbAccessoryMode.connectAndSwitch | Result: true              (~22 ms)
23:43:09.941  UsbLauncherManager.checkAlreadyConnected | Found device already in accessory mode: … 2D00
23:43:10.591  LibusbAccessoryConnection: Successfully connected via JNI Libusb
23:43:11.468  Handshake: Version response received (ret=12, attempt=1)       (clean, no "reads recovered")
23:43:11.522  AapSslContext.performHandshake | SSL handshake complete
23:43:14.849  VideoDecoder.outputThreadLoop | First frame rendered (hardware decode)
```

- one `Performing AOA switch` (native, ~22 ms); **0** `Sending acc start` (libusb route, as round 1);
  **0** `didn't handle`.
- **`Unable to find an endpoint pair on the accessory interface` — 0.** This is the run's point:
  `UsbEndpointSelectionPolicy` now decides `LibusbProjectionConnection`'s endpoints too, and the
  libusb route connected without an endpoint error.
- `Switching…` → `SSL handshake complete` = **2.53 s**; → first frame = **5.86 s** (round 1: 6.11 s).
- Steady state: ~55 s, `c2.qti.hevc.decoder`, 46/45/45/45/39/21 fps, **`dropped=0 skipped=0
  concealed=0`** every window, `decodeLatency` 18–19 ms p95 21–23 ms. (fps tapers toward the end —
  the phone's idle-screen adaptive rate, as round 1 noted, not a decoder issue since
  dropped/skipped/concealed stay 0.)
- Formed a clean session on the exact link where R3's standard route managed 3/5 — consistent with
  round 1's libusb-is-more-robust finding.

---

## Answers to the brief's §8

1. **R7 and R8: neither ran — both UNTESTABLE.** A phone in MTP mode on this phone host is
   short-circuited by AOSP before the attach reaches `UsbAttachedActivity` (§7b), so `ee16f522`'s
   removal of the `0x18D1` gate cannot be exercised by plugging a phone here. The Motorola's
   `uniqueName` as printed: `motorola edge 30 neo` / serial `ZY22GC3BM4` /
   `UsbDevice[mName=/dev/bus/usb/001/00x, mVendorId=8888, mProductId=11906, mClass=0, …]`. Full
   descriptor in `descriptors.txt`. No dialog identities (nothing was offered).
2. **R10 timing:** `Switching USB device to accessory mode` → first `Success controlTransfer` =
   **14 ms**; `Error controlTransfer len: -1` — **0**. Regression guard only (14 ms < `main`'s 100 ms).
3. **R3 `didn't handle` count: 2 / 5 cycles** (`Sending acc start` = 5). Both are `ee16f522`'s
   activity→service hand-off on slow accessory-permission re-request, not the `UsbSwitchClaim`
   "leaving to it" path (0 occurrences). Per-cycle `acc start → Established`: 2.34 / 1.95 / 1.92 /
   1.63 / 2.28 s. Sessions 3/5 to SSL. INCONCLUSIVE (degraded link).
4. **R11:** recents/activities clear (0); `Usb re-attached in normal mode` absent (0); `onNewIntent`
   0, `onCreate` per attach. PASS.
5. **R2:** libusb route forms a full clean session (SSL + video, 0 dropped/skipped/concealed);
   `Unable to find an endpoint pair on the accessory interface` — **0**. PASS.

**Third-dialog watch item:** no `systemui.usb.*` activity in any capture — no chooser, no visible
`UsbPermissionActivity`. The dongle's `2D00` auto-grants silently. Zero dialog identities, so the
"third identity" question does not arise on this rig.

## Shipping question

- **`ee16f522` (non-Pixel attach) is unverified on hardware** — this phone host cannot deliver the
  attach to the code path. Its coverage is `UsbAttachPolicyTest`. A `FEATURE_AUTOMOTIVE` USB host
  would be needed for a real hardware check.
- **`b236103c` (accessory race):** R10 shows the control-transfer path with no early give-up and no
  `Error controlTransfer len: -1`; R2 shows the shared `UsbEndpointSelectionPolicy` picking the
  libusb route's endpoints with no endpoint-pair error; R11 shows the manifest trampoline does not
  linger. The one open item is R3's `didn't handle` on accessory-permission re-request latency
  (2/5 cycles) — not a `UsbSwitchClaim` failure (that path never fired), but worth a code-side look
  at whether the activity should wait out a re-request rather than hand off. The standard-route
  link on this rig's dongle is too degraded (`num_connects` 211→275, 2/5 SSL failures) for a clean
  R3 verdict; a fresh cable / OTG adapter / second dongle is still the blocker round 1 named.

## Evidence

`evidence/usb-aoa-handoff-round2/`:

- `r7-ohu.txt.gz` (empty), `r7-usb.txt.gz`, `r7-confirm-fulllog.txt.gz`,
  `r10-ohu.txt.gz` / `r10-usb.txt.gz`, `r11-ohu.txt.gz` / `r11-usb.txt.gz`,
  `r3b-ohu.txt.gz` / `r3b-usb.txt.gz`, `r2-ohu.txt.gz` / `r2-usb.txt.gz`
- `r10_console.txt`, `r11_console.txt`, `r3b_console.txt`, `r2_console.txt`
- `dumpsys-usb-before.txt`, `dumpsys-usb-after.txt`
- `descriptors.txt` (Motorola MTP; dongle idle `4EE1` PTP; dongle accessory `2D00`)
- `settings-backup-poco.xml`
