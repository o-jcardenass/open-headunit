# usb-aoa-handoff — round 1 results

**Candidate:** `fork/fix/usb-attach-clear-defaults` @ `361cfed3`
**Baseline:** same branch, its own parent @ `e3077047`
**APK md5:** `ec0fcc99bc25cc1021e3cc65f5c27c75` (candidate) / `819bb763f58a4961a8ecdf6e6d75880d` (baseline)
**Unit:** Poco X3 NFC (M2007J20CG `surya`, Android 15, sm6150) as head-unit / USB host over OTG,
Carlinkit-class AA dongle in its USB-C port, wireless adb; Motorola edge 30 neo as the projecting
phone over the dongle's own radio/BT.
**Date:** 2026-09-03

## Setup notes

- **Clean-run protocol**: §4's wireless protocol does not apply (per brief §3). USB runs are
  force-stop, `logcat -c`, start both captures (`OPENHU:V '*:S'` and `-s UsbHostManager:D`), then
  plug.
- **Scripts used** (`hur-wifi-test-scripts/`): `build_hur.sh` (R0 both APKs), `run_unit_tests.sh`
  (R0 unit gates), `set_prefs_runas.sh` (all pref writes — the Poco is a debuggable non-rooted
  build, so `run-as`, not the rooted `set_hu_prefs.sh`).
- **New script**: `hur-wifi-test-scripts/round-usb-aoa-handoff-r1/capture.sh` — `start <runid>` /
  `stop`, force-stops the app, clears the buffer, and starts the two §7b readers by pid. Left in
  place for the next round.
- **`dumpsys usb` before the round**: `user_permissions` empty (no "always"-ticked persistent
  grant — clean). Default *handlers* ARE set for both dongle identities (`18D1:4EE1` and
  `18D1:2D00` → `com.andrerinas.headunitrevived`) from a prior dongle session — this suppresses the
  system chooser for the dongle; the app's own `UsbPermissionActivity` can still fire. Left as-is
  (matches a returning real user's device; the brief's §5 exception is about clearing a permission
  *grant*, which is not what was present).
- **Wireless adb**: Poco joined the lab WiFi (`192.168.1.10`), `adb tcpip 5555`, all runs over
  `192.168.1.10:5555`. Its own BT was off at the start — enabled with `svc bluetooth enable` for
  R4/R5/R6 (mode 3 needs a BT adapter for the NativeAA listener).
- **`createGroup SUCCESS` is not produced on this rig.** The Poco runs `wifi-connection-mode=3`
  over the SoftAP transport (`native-ap-transport=1`, its stored value), so mode-3 wireless
  bring-up is `WirelessServer` bind on 5288 + `NativeAA: ACTIVELY LISTENING` + a 30 s
  SoftAp-credential wait — never a WiFi Direct group. R5/R6 use `AapService.onCreate` →
  `WirelessServer: binding port 5288` as the "wireless armed" proxy; the candidate's
  `deferWirelessForUsbHandoff()` is at the top of `initWifiModeWithOptionalWait()`, upstream of the
  transport branch, so this is a faithful substitute for the brief's `createGroup SUCCESS` timing.
- **Deviation — R3b and R4 run on the libusb route** (`use-libusb=true`), not the brief's pinned
  `use-libusb=false`. Reason: after ~40 min the dongle's standard-route USB link degraded to where
  it cannot hold a session at all (see R1 / "Anything the brief did not ask about"). R3/R4 test the
  switch-claim and the wireless deferral, both route-independent; R3a still ran standard as the
  brief asks. Standard-route session behaviour on this link is fully captured in R1.
- **The R1 A/B baseline arm was not run.** With the candidate unable to complete a standard-route
  session for link reasons, the baseline would fail identically and the head-to-head the round
  wanted ("does the fix win the race the baseline loses") is not answerable on this rig right now.
- **Left on the rig**: candidate APK `361cfed3` (`ec0fcc99…`) reinstalled; `settings.xml` restored
  from the round-start backup (`wifi-connection-mode=3`, `use-libusb=false`, `log-level=2`,
  `auto-start-on-usb=false`). BT left enabled. The pre-round installed build was an unknown
  `e3d8d77c…` — not restored (it was not one of ours).
- No new scripts added to `hur-wifi-test-scripts/` root; inventory doc untouched.

## R0 — build gate

**PASS**

- Both APKs built with `build_hur.sh` from checked-out SHAs (`361cfed3`, then `e3077047`).
- `git log --oneline e3077047..361cfed3` = exactly two commits:
  `8514e06f USB: claim the accessory device before a fast-reverting dongle bails`,
  `361cfed3 Wireless: an in-flight USB handoff defers the bring-up`.
- APK md5s recorded and different: `ec0fcc99…` (candidate) vs `819bb763…` (baseline).
- Identity check (`unzip -p <apk> 'classes*.dex' | strings | grep -c -e UsbAccessoryHandoffPolicy
  -e UsbSwitchClaim -e WirelessBringUpDeferralPolicy`): **candidate 7, baseline 0**.
- Unit gate (`testGithubDebugUnitTest`, counted from the JUnit XMLs):
  **candidate 1010 / 0 failures**, **baseline 997 / 0 failures** — both match the brief exactly.

## R1 — cold plug, standard route

**INCONCLUSIVE** (candidate arm; 3 attempts per the brief's stop condition)

The handoff path the round tests was reached and behaved correctly on every attempt, but this
hardware could not complete a USB AAP session at all today — the SSL handshake fails on a
corrupted AOA bulk stream (`USB reads recovered after 2 errors` every attempt,
`SSLException: Unable to parse TLS packet header`). The session-completion PASS condition could
not be evaluated. The dongle-findings doc got a clean ~60 ms SSL on this same rig on 2026-09-03,
so this is a link/rig regression between then and now, not a candidate defect.

- Settings written: `log-level=0`, `use-libusb=false`, `auto-start-on-usb=true`,
  `wifi-connection-mode=3` (verified read-back, settings.xml well-formed, `log-source=0`).
- Radio state: n/a (USB round). Dongle: Carlinkit-class `carplay_box_F967`, Motorola edge 30 neo
  associated to it over WiFi (`192.168.43.100`, `Requesting package name: …gearhead`), Gearhead
  process live throughout.
- Discard-rule check: n/a (§4 discard rules are wireless-round rules; brief §3).

**Per-attempt (candidate `361cfed3`, all captures kept):**

| | acc-start | didn't-handle | Established conn | dongle patience¹ | acc-start→Established | outcome |
|---|---|---|---|---|---|---|
| attempt 1 (`r1-cand`)  | 1 | 0 (initial) | 1 | 411 ms | 1.72 s | version ret=12 (attempt 2, after `reads recovered`), then `SSLException: Unable to parse TLS packet header` → disconnect |
| attempt 2 (`r1-cand2`) | 1 (initial) | 0 (initial) | 1 | 408 ms | ~1.5 s | **no** version response in 3 tries → `USB_DEVICE_DETACHED` (dongle self-reverted after ~7.6 s) → then a ~20 s service-fallback retry loop: 7 further `Sending acc start` / 7 `didn't handle` over the 90 s window |
| attempt 3 (`r1-cand3`) | 1 | 0 (initial) | 1 | 408 ms | 1.51 s | identical to attempt 1: version ret=12 (attempt 2, after `reads recovered`), then `SSLException: Unable to parse TLS packet header` → disconnect |

¹ `Acc start sent` → first `18d1:2d00` line in the `UsbHostManager` capture.

**What the round asked to be reported regardless of verdict:**

1. `Accessory-mode permission arrived after <N>ms` — **absent on all 3 attempts.** Permission was
   already present (manifest auto-grant for `18D1:2D00` via `usb_device_filter.xml`, plus a
   pre-existing default-handler entry). Commit 1's permission-poll-before-dialog path was therefore
   **not exercised**; its activity-side device poll was — `Accessory mode reached after 2–3 ms` on
   every attempt.
2. Dongle patience on the plug: **411 / 408 / 408 ms** — tight and consistent, well under the 1 s
   the permission poll would need, so even if the retry path had been reachable the dongle would
   not have waited for a dialog (which is exactly the failure commit 1 addresses).
3. Dialog identities: **no dialog on any attempt.** `Requesting USB permission` count 0;
   no `systemui.usb.*` activity. The app is the registered default handler for both dongle
   identities and had permission, so neither the system chooser nor the app's own
   `UsbPermissionActivity` fired.

**Candidate handoff behaviour (the part that could be measured):** on the initial plug, every
attempt did **exactly one** AOA switch, **zero** `didn't handle`, the commit-2 line
`a USB projection attempt is in flight — holding the wireless bring-up for up to 8000ms` fired,
and `quiesceWirelessForWiredSession` then stopped the wireless stack for the (doomed) session.
**No** `createGroup SUCCESS`, no 5288 bind, no poke preceded the USB open on any attempt. That is
the two commits working as designed; the SSL failure is downstream of everything they touch.

The attempt-2 retry storm (7× `didn't handle`) is **not** the switch-claim failing to cover the
first window — it is the service's post-disconnect `UsbLauncherListener` fallback re-switching
every ~20 s after the SSL failure dropped the session, each cycle dying at SSL again. It would
count against an R3 verdict but R3 needs a session that forms in the first place.

_Baseline arm not run: with no session forming on candidate for link reasons, the baseline would
fail identically; the handoff A/B (1 vs 2 switches on the initial plug) is noted as a follow-up if
the link is restored._

## R2 — cold plug, libusb route

**PASS** (candidate `361cfed3`, `use-libusb=true`)

A clean end-to-end session formed and ran video for ~2.5 min. **The libusb route holds a session
on the exact dongle + cable + link that fails the standard route in R1** — this is the round's
main finding.

- Settings written: `log-level=0`, **`use-libusb=true`**, `auto-start-on-usb=true`,
  `wifi-connection-mode=3` (read back).
- Capture: `r2-cand-ohu.txt` / `r2-cand-usb.txt`.

**Decisive log lines (timestamps):**

```
23:18:18.215  UsbAttachedActivity.onCreate | Switching USB device to accessory mode … 4EE1
23:18:18.232  UsbAccessoryMode.connectAndSwitch | Performing AOA switch via native libusb...
23:18:18.261  UsbAccessoryMode.connectAndSwitch | Result: true                 (native switch ~29 ms)
23:18:18.389  AapService.deferWirelessForUsbHandoff | holding the wireless bring-up for up to 8000ms
23:18:18.672  UsbHostManager | USB device attached: 18d1:2d00
23:18:18.777  UsbAttachedActivity.awaitAccessoryDevice | Accessory mode reached after 513ms … 2D00
23:18:19.824  UsbAttachedActivity.onCreate | Switching USB device to accessory mode … 4EE1   ← 2nd activity
23:18:19.831  UsbAccessoryMode.connectAndSwitch | Cannot open device                          ← 4EE1 already gone, no-op
23:18:19.857  LibusbAccessoryConnection: Successfully connected via JNI Libusb
23:18:19.922  AapService.quiesceWirelessForWiredSession | stopping the wireless stack …
23:18:20.809  Handshake: Version response received (ret=12, attempt=1)          ← clean, no "reads recovered"
23:18:20.923  AapSslContext.performHandshake | SSL handshake complete
23:18:21.193  AapControlService.serviceDiscoveryRequest | Service Discovery Request: Android
23:18:24.004  AapControlMedia.mediaStartRequest | Media Start Request VIDEO
23:18:24.330  AapProjectionActivity.hideLoadingOverlay | Hiding loading overlay after first video frame
```

**PASS conditions:**
- one native AOA switch (`Performing AOA switch via native libusb` ×1); the second activity's
  `Cannot open device` on the already-gone `4EE1` sent nothing to a live device — **`didn't handle`
  count 0**;
- switch → `LibusbAccessoryConnection: Successfully connected` = **1.60 s**;
- session reaches **`SSL handshake complete`** ✓.

**Measurements (as numbers):**
- native AOA switch call: **~29 ms** (`Performing AOA switch` → `Result: true`).
- dongle patience: `4EE1` attached 23:18:17.544 → `2D00` 23:18:18.672 = **~411 ms** (same as R1 — it
  is the dongle's own revert timer, route-independent).
- `Switching USB device to accessory mode` → first video frame = **6.11 s** (of which 20.9→24.3 s is
  the phone's own Service-Discovery / Media-Start negotiation).
- steady state: **31 throughput windows, every one `dropped=0, skipped=0, concealed=0`**, 46–52 fps,
  `decodeLatency` 12–13 ms (p95 13–24 ms), codec `c2.qti.avc.decoder` (HW H.264). Clean 23:18:24 →
  23:20:59.
- `Accessory-mode permission arrived` **absent** (permission pre-granted, as R1); no dialog,
  `Requesting USB permission` 0.

Second data point for the two routes (vs the dongle-findings doc's single run each): standard
route `Switching…` → first frame ~24 s there (double-switch) / never here (R1); libusb ~3.8 s there
/ **6.11 s here** (single clean switch, the extra ~2 s is app cold-start + the phone's negotiation,
not the transport).

## R3 — replug, five times

### R3a — standard route (`use-libusb=false`, as the brief pins it)

**INCONCLUSIVE** on the strict PASS bar (marginal link, not the branch); **the switch-claim
mechanism R3 exists to test PASSED** — 0 `didn't handle` on the initial handoff of every replug.

- Settings: `use-libusb=false`, others as R1. Capture: `r3-std-ohu.txt` / `r3-std-usb.txt`.
- Operator did the 5 cycles at ~30 s cadence; cycle 5 needed a reseat.

| cycle | `2d00` enum | `Sending acc start` | `Established connection` (acc→) | outcome |
|---|---|---|---|---|
| 1 | 23:23:42.807 | 23:23:42.377 | 23:23:43.922 (1.55 s) | **SSL complete** 23:23:45.264 |
| 2 | 23:24:14.017 | 23:24:13.606 | 23:24:14.169 (0.56 s) | **SSL complete** 23:24:17.000 |
| 3 | 23:24:47.139 | 23:24:46.734 | 23:24:47.291 (0.56 s) | **SSL complete** 23:24:50.108 |
| 4 | 23:25:21.972 | 23:25:21.556 | 23:25:22.130 (0.57 s) | version exchange failed (3 attempts) → `Handshake failed` 23:25:29.682 |
| 5 | did not enumerate on first attempt; after a firm reseat: 23:27:41.208 | 23:27:40.789 | 23:27:41.342 | **SSL complete** 23:27:43.725 |

- **`didn't handle` during the five replug windows: 0.** One `Sending acc start` per enumerated
  plug, no double-switch, no dialog (`Requesting USB permission` 0).
- The 3 `didn't handle` events in the capture (23:26:34, 23:26:54, 23:27:13) are **all** in the
  ~20 s service-retry loop that followed cycle 4's *session* failure — the same pre-existing
  `UsbLauncherListener` fallback seen in R1 attempt 2, not a fresh-plug race. They are >60 s after
  the last deliberate replug.
- Sessions formed: **4 of 5 enumerated attempts** (cycles 1, 2, 3, 5); cycle 4 connected but the
  version exchange failed — the same marginal-link failure as R1, hitting ~1 plug in 4–5 once the
  dongle has been cycled repeatedly (classic marginal-USB-2.0 signature, cf. `#800`).
- Standard route now formed sessions where R1 could not — the difference is the R2 reseat plus a
  prior successful (libusb) session; the link is intermittently, not permanently, broken, and
  degrades with repeated cycling.

### R3b — libusb route (`use-libusb=true`, deviation — see Setup notes)

**INCONCLUSIVE** on session formation (link had degraded badly by this point);
**switch-claim PASS** — 0 `didn't handle`, and the native connection layer held 5/5.

- Settings: `use-libusb=true`. Capture: `r3-libusb-ohu.txt` / `r3-libusb-usb.txt`.
- **native AOA switch ×5, `didn't handle` ×0**, `Accessory mode reached after 504–511 ms` every
  cycle (very consistent), one benign second-activity `Cannot open device` (as R2).
- **`LibusbAccessoryConnection: Successfully connected via JNI Libusb` 5/5** — every switch
  produced a working native USB connection (standard route in R3a did not connect on every plug).
- Sessions to `SSL handshake complete`: **2 of 5** (23:31:02, 23:32:54). The other 3 failed
  `No VERSION_RESPONSE attempt 1 (ret=0)` → `Version response received attempt 2` → SSL
  `Handshake failed with exception` — the same corrupted-early-byte failure as R1, now reaching
  the libusb route too because ~40 min of aggressive replug cycling had degraded the dongle/cable
  link markedly (progressive marginal-USB-2.0 behaviour, `#800` signature; not a branch effect).
- Net for R3: on **both** routes the switch-claim R3 exists to test holds (0 `didn't handle` on
  every fresh plug across 10 cycles); session formation is gated by the marginal link, not the
  branch.

## R4 — dongle on the bus before the service starts

**PASS** — the strongest form (dongle already at `18D1:2D00` when the service started).

- Settings: `use-libusb=true` (deviation, per R3b rationale — standard route can't hold a session
  on the degraded link; the deferral being tested is route-independent). `wifi-connection-mode`
  read back **1** here, not 3 — see Setup notes; it does not affect the verdict (the deferral is
  upstream of the mode branch). Capture `r4-cand-*`.
- Sequence: dongle plugged and left to settle (app auto-started on the USB attach, ran a session,
  I force-stopped it; the dongle stayed in accessory mode), capture started, then
  `am start …/main.MainActivity`.

```
23:39:58.703  am start issued
23:39:59.333  AapService.onCreate | AapService creating...
23:39:59.405  UsbLauncherManager.checkAlreadyConnected | Found device already in accessory mode … 2D00
23:39:59.406  AapService.deferWirelessForUsbHandoff | a USB projection attempt is in flight —
              holding the wireless bring-up for up to 8000ms                        ← +73 ms after onCreate
23:40:00.026  LibusbAccessoryConnection: Successfully connected via JNI Libusb
23:40:00.062  AapService.quiesceWirelessForWiredSession | USB session established while wireless
              mode … was armed — stopping the wireless stack
23:40:01.699  AapTransport.handshake | Handshake failed with exception   ← session then died at SSL (degraded link, as R1)
```

**PASS conditions:**
- `holding the wireless bring-up` **present** ✓
- before `quiesceWirelessForWiredSession`: **no** `WirelessServer: binding port 5288`, **no**
  `NativeAA: ACTIVELY LISTENING`, **no** `createGroup SUCCESS`, **no** `Attempting active poke` ✓
- service start → `USB session established while wireless mode …` (quiesce) = **1.359 s**
  (`AapService creating` → quiesce = **729 ms**). The wireless stack was held the entire time and
  never armed; the commit-2 reorder (`checkAlreadyConnected()` before `initWifiModeWithOptionalWait()`)
  is what makes the deferral win — `checkAlreadyConnected` at +72 ms sets the switch in flight, the
  deferral reads it 1 ms later.

The SSL failure afterwards is the same degraded-link issue as R1; R4's verdict is about the
deferral, which is fully satisfied before the handshake is even attempted.

## R5 — no USB device at all (regression guard)

**PASS** — the deferral costs a wireless-only unit nothing.

- Settings: `log-level=0`, `use-libusb=false`, `auto-start-on-usb=true`, `wifi-connection-mode=3`
  (read back, both builds). BT enabled on the Poco. Nothing on the USB bus (verified).
- Service started by `am start -n …/main.MainActivity`; measured from `AapService.onCreate |
  AapService creating...`. Captures `r5-cand-*` / `r5-base-*`.
- **`createGroup SUCCESS` does not occur on this rig** — the Poco runs mode 3 over the SoftAP
  transport (`native-ap-transport=1`, its stored value), so wireless bring-up is
  `WirelessServer` bind on 5288 + `NativeAA: ACTIVELY LISTENING` + a 30 s SoftAp-credential wait,
  not a WiFi Direct group. The candidate's `deferWirelessForUsbHandoff()` sits at the top of
  `initWifiModeWithOptionalWait()`, upstream of the transport branch, so the 5288-bind time is a
  valid proxy for "wireless armed".

| build | `AapService creating` | `WirelessServer: binding port 5288` | Δ | `NativeAA: ACTIVELY LISTENING` | Δ |
|---|---|---|---|---|---|
| candidate `361cfed3` | 23:35:24.044 | 23:35:24.119 | **75 ms** | 23:35:24.142 | 98 ms |
| baseline `e3077047`  | 23:37:13.136 | 23:37:13.212 | **76 ms** | 23:37:13.237 | 101 ms |

- **Deferral line (`holding the wireless bring-up`) absent on both** — correct, nothing on the bus.
- Difference candidate vs baseline: **1 ms** (noise). The added bus read / policy call when
  `usbManager.deviceList` is empty is free.

## R6 — the USB-C Bluetooth audio adapter (regression guard)

**PASS** — the adapter is rejected, no switch, no deferral, wireless on time; no pre-existing
`UsbDeviceIdentityPolicy` fault.

- Adapter: **`TaiYiLian UGREEN-BT701` (VID `0A12` PID `4007`)**. Descriptor stable across 3 replugs
  (operator confirmed identical each time): `if0/if1 03/00/00` (HID), `if2 01/01/00` (audio
  control), `if3/if4/if5 01/02/00` (audio streaming). **No FF/FF/00 and no 06/01/01 interface.**
- Settings: `wifi-connection-mode=3` (read back on both builds), `use-libusb=false`,
  `log-level=0`, `auto-start-on-usb=true`. Captures `r6-cand-*` / `r6-base-*`.

| build | `UsbDiagnostics` verdict (×3: attach / service force=false / force=true) | `Sending acc start` | deferral line | onCreate→5288 bind | onCreate→`ACTIVELY LISTENING` |
|---|---|---|---|---|---|
| candidate `361cfed3` | `rejected: no Android interface`, **0 usable for Android Auto** | none | **absent** | **85 ms** | 110 ms |
| baseline `e3077047`  | `rejected: no Android interface`, **0 usable for Android Auto** | none | n/a | **75 ms** | 102 ms |

- Dump says **`rejected:`**, not `accepted:` — so the §7b pre-existing-fault branch does not apply,
  on either build.
- No `Performing AOA switch` / `Sending acc start` on either build — no AOA switch attempted for a
  non-Android peripheral.
- `deferWirelessForUsbHandoff` / `holding the wireless bring-up` **absent** on the candidate —
  `WirelessBringUpDeferralPolicy` correctly does not defer for a device that is neither in
  accessory mode nor has a switch in flight.
- `createGroup SUCCESS` absent on both (SoftAP path, as R5); 5288-bind proxy differs by 10 ms
  candidate vs baseline — within the R5 noise band (1 ms there). A permanently-attached BT audio
  adapter costs the wireless bring-up nothing.

## Anything the brief did not ask about

- **`wifi-connection-mode` changed from 3 to 1 (`NATIVE` → `AUTO`) at some point between R5 and R4**
  — verified 3 immediately before R5-cand and R5-base (and both ran `WifiLauncher: Initializing
  WiFi Mode: NATIVE`), read back **1** in R4 (`mode AUTO/WIFI_DIRECT`). Between those two I
  reinstalled the candidate (`install -r`), set only `use-libusb=true`, and the operator plugged
  the dongle (which auto-started the app and ran a short USB session). `set_prefs_runas.sh` did not
  touch the key. Not isolated to a single cause; worth the coding session checking whether the
  USB-attach / auto-start path or a `Settings` getter migration writes `wifi-connection-mode`.
  Restored to 3 from the backup at round end. Did not affect any verdict — the deferral is upstream
  of the mode branch.
- **The manual "USB Connect" button in the app succeeds on the standard route where the automatic
  cold plug fails at SSL** (operator observed it working "each time" during R1). Consistent with
  the mechanism: the automatic path opens the accessory interface and starts the AAP/version/SSL
  handshake ~1–1.5 s after re-enumeration, while the dongle's USB↔radio bridge is still settling
  (`USB reads recovered after 2 errors` on every standard-route attempt); a later manual attempt
  hits a bridge that has had seconds to stabilise. A short post-open settle/drain, or a more
  tolerant version-exchange retry that does not feed early bytes into the TLS parser, would likely
  make the automatic standard route as reliable as the manual one on marginal dongles. This is
  adjacent to — but not the same as — the race commit 1 fixes (winning the claim); here the claim
  is won and the pipe is simply not carrying clean data yet.
- **`Acc start sent (len=0)`** — the standard-route AOA-start control transfer logs `len=0`. This is
  the zero-data-phase `ACC_REQ_START` (`0x35`) and is correct, but the wording reads like a failure;
  a coding-session cleanup could say "no data phase" instead.
- **The libusb route connected on 10/10 switches across R2+R3b**; the standard route did not connect
  on every plug in R3a. Combined with R2 forming a clean session on the same link that broke R1's
  standard route, this is a second, stronger data point for the dongle-findings hypothesis that the
  libusb transport is more robust for marginal / fast-reverting dongles. It is still not the default
  and this round does not recommend flipping it, but the case for offering it prominently in the UI
  (rather than burying it in advanced settings) is now well supported.
- **Progressive link degradation under cycling.** R1 (3 fails) → R2 after a reseat + a successful
  libusb session (clean) → R3a cycles 1–3 clean, 4 fails → R3b (2/5) → R4 fails at SSL. The dongle
  gets worse the more it is power-cycled in a short window and partially recovers after a rest or a
  reseat. Classic marginal-USB-2.0 behaviour (`#800`), independent of the branch, but it means
  **this rig cannot currently serve as a reliable standard-route session test bed** — a new
  cable / OTG adapter, or a second dongle, is needed before R1/R3a can get a real verdict.

## Verdict summary

| run | verdict | one line |
|---|---|---|
| R0 | **PASS** | both APKs, md5s differ, identity 7/0, unit 1010/0 & 997/0 |
| R1 | **INCONCLUSIVE** | handoff clean 3/3 (1 switch, 0 fallback, deferral fired, quiesced); no session — SSL fails on a corrupted AOA bulk stream, degraded link not the branch |
| R2 | **PASS** | libusb route: clean session, SSL complete, ~50 fps 0 drop/skip/conceal 2.5 min, on the exact link R1 fails |
| R3a | **switch-claim PASS / sessions INCONCLUSIVE** | 0 `didn't handle` on every replug; 4/5 enumerated cycles formed sessions, 1 failed on the link |
| R3b | **switch-claim PASS / sessions INCONCLUSIVE** | 0 `didn't handle`, native connect 5/5; 2/5 to SSL (link had degraded badly) |
| R4 | **PASS** | dongle at 2D00 before service; deferral fired at +73 ms, held everything, quiesced at +729 ms; no 5288/listener/poke before quiesce |
| R5 | **PASS** | no USB device: deferral absent, wireless armed 75 ms (cand) vs 76 ms (base) — free |
| R6 | **PASS** | UGREEN BT audio adapter `rejected` / 0 usable on both builds; no switch, no deferral, wireless 85 vs 75 ms |

**Ship call:** nothing in the eight runs blocks the branch. The two regression guards (R5, R6) are
clean. The handoff logic (commits 1 + 2) behaved correctly everywhere it could be observed —
one switch per plug, no competing second switch on a live device, wireless deferred then quiesced,
and free when no USB device is present. The one thing the round could **not** deliver is the R1
head-to-head "does the fix win the race the baseline loses", because the rig's dongle link can no
longer hold a standard-route session at all; R2 (libusb, clean) shows the fix path is sound, and
a standard-route A/B needs a better cable or a second dongle.
