# narrow-band-and-disconnect-scope — round 1 results

**Candidate:** `fork/fix/narrow-band-and-disconnect-scope` @ `9635f8a5` (4 commits on `fork/feat/native-aa-wireless-and-bt-lifecycle` @ `a938ba91`)
**Control (R1 only):** `fork/feat/native-aa-wireless-and-bt-lifecycle` @ `a938ba91` — the candidate's own parent
**APK md5:** candidate `91d085004e8304d04cf9b23c069a3692` / control `92ec968506ce79883baaa08551b0bed5`
**Unit:** D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, 1440x720, ~3.7 GB RAM, BT `11:46:03:10:33:59`. Phone = POCO X3 NFC (`M2007J20CG` / `surya`), Android 15, Gearhead 17.5, BT `DC:B7:2E:5E:4E:59`.
**Date:** 2026-09-03

## Verdicts

| Run | Verdict | One line |
|---|---|---|
| R0 build gate | **PASS** | candidate 1259 / 0 / 0; DEX `narrow-band-profile-cap` present in candidate, absent in control |
| R1 disconnect scope (**the round**) | **PASS** | `AapTransport stopping and sending byebye` after the service destroy: candidate **1**, control **0** — exactly the brief's "Expected 1 and 0" |
| R2 verdict reaches the screen | **PASS** | banner `VIDEO_LINK_TOO_SLOW` shown on both `native-ap-transport` values, seed stamp intact after both |
| R3 a rendering session retires it | **PASS** | after ~56 s of video, `headunit://disconnect` → `connection-issue-video-starved` reads `0`; relaunch shows no banner |
| R4 hotspot bring-up vs busy radio | **INCONCLUSIVE (pre-registered)** | SoftAp config unreadable/unwritable on every attempt (SecurityException), so the literal PASS string is unreachable; an AP did come up (attempt 3 formed a full session with video), no give-up line blamed privileges — **not FAIL** |
| R5 profile cap does not fire on a 5 GHz radio | **PASS (regression-guard branch)** | rig reports `this unit's WiFi radio reports a 5 GHz band`; `linkCapped=none`, profile announced unchanged at 1080p/60 |

**Shipping read:** the disconnect-scope fix (`67f4344b`) does exactly what it claims on hardware — after the first `AapService` destroy the control never sends another byebye, the candidate does. The banner (`99214450`) reaches the screen on both transports and is retired by a session that renders. The profile cap (`8b3b576e`) correctly stays dormant on a 5 GHz radio. The hotspot-wait change (`9635f8a5`) can only be shown as far as this rig allows: the "WiFi disabled before enabling hotspot" path is dead code on API 34 (the platform ignores an app WiFi-disable), but the fix's other half — never blaming privileges on a unit with WRITE_SETTINGS — held on every attempt, and an access point did eventually come up. No blocker.

---

## Setup notes

**Rig state at round start.** D-HU's station WiFi was OFF and the app was mid-thrash on a Native AA handshake loop (`WifiDirectManager: WiFi is disabled but needed for Native AA. Attempting to enable...` → `the claimed create window is released (WiFi is off and only the user can turn it on)`, repeating). `adb shell svc wifi enable` brought the station back and it auto-re-associated to `Pegue Cdesta` at **5500 MHz** with no operator tap — done twice this session (once here, once restoring after R4), contrary to the §7a / memory note that this rig needs a hand tap to re-join. Nothing was paired at round start either; the operator paired the POCO with the head unit and let Android Auto connect once before the runs began.

**Brief erratum — the settings key is `log-level`, not `exporter-log-level`, and the rig was not already on VERBOSE.** §4's row assumes "as the rig already sets for VERBOSE". The pref is `log-level` (int), holding the **ordinal** of `LogExporter.LogLevel` (`VERBOSE=0, DEBUG=1, INFO=2, WARNING=3, ERROR=4, SILENT=5`). The rig had it at **2 (INFO)**, which suppresses every `.d`/`.v` line the round leans on. Set to `0` for every run. `AppLog`'s logcat output is gated by this same value (`isLoggable(priority) = priority >= cachedLogLevel`), so the capture is empty of the decisive lines until it is lowered.

**Brief erratum — §5 string `HotspotManager: WiFi disabled before enabling hotspot.` never appears on this rig.** API 34 silently ignores an app's `setWifiEnabled(false)`, so `HotspotManager.setHotspotEnabled` takes the sibling branch (`HotspotManager.kt:172`): `HotspotManager: Asked to disable WiFi and the platform ignored it (expected on modern Android); the framework will take the radio itself if it needs to.` The `WiFi disabled before enabling hotspot` line (`HotspotManager.kt:170`) is only reachable where the platform honours the disable. This makes R4's literal PASS condition unreachable here — see R4.

**Brief erratum — §5 string `on this API level: <ExceptionName>: <message>` was not the line hit.** On this rig the SoftAp read/write fails earlier and more specifically: `SoftApConfigCompat: could not read the current access point configuration: SecurityException: App not allowed to read or update stored WiFi Ap config (uid = 10168)` and `SoftApConfigCompat: could not configure the access point (SecurityException: …); leaving it as the device has it and starting it anyway.` The `SoftApConfigCompat: could not set … on this API level:` line (`SoftApConfigCompat.kt:196`) was never reached.

**R5 pre-flight needed a forced fresh group.** The band-answer line lives in `WifiDirectManager.createQuietGroup()`, which does not run when a warm group is only refreshed (`refresh: the group … is up, so its credentials are read again rather than the group remade`). First R5 attempt reused the group R3 left up and emitted no band line; re-ran after `headunit://exit` + `am force-stop` + `cmd wifip2p remove-saved-groups`, which forced a real create and the line appeared.

**Bring-up order.** R1/R3/R5 followed the §7a rule: phone WiFi off, launch the head unit, wait ~12 s for the group to form, phone WiFi on. Phone Bluetooth stayed on throughout (Native AA needs it for RFCOMM). R1's second session used a phone Bluetooth off/on cycle — it produced a fresh `ACL_CONNECTED` → `AutoStartReceiver … MATCH! Starting AapService` on **both** builds, so the §7a fallback to cycling the head unit's own adapter was not needed.

**settings.xml** backed up before any change (`md5 d8ffbc01cdcf5350972444ce1b57fd21`), restored byte-identical at round end (same md5, key-set diff empty).

**Scripts used:** `hur-wifi-test-scripts/build_hur.sh`, `run_unit_tests.sh`, `set_hu_prefs.sh` (rooted multi-key set/del). No new script added.

**Candidate left installed on D-HU** (confirmed by `narrow-band-profile-cap` DEX symbol in the on-device `base.apk`). Phone radios restored (WiFi on, Bluetooth on). Head unit station restored to `Pegue Cdesta` / 5500 MHz.

---

## R0 — build gate

**PASS**

- Candidate unit tests: **1259 tests, 0 failures, 0 errors** (`app/build/test-results/testGithubDebugUnitTest/*.xml`, summed).
- md5: candidate `91d085004e8304d04cf9b23c069a3692`, control `92ec968506ce79883baaa08551b0bed5`.
- DEX symbol `narrow-band-profile-cap`: **present (1)** in the candidate APK, **absent (0)** in the control APK. Re-checked against the installed on-device `base.apk` at round end: present.

---

## R1 — the disconnect scope. The point of the round.

**PASS**

- Settings written: `wifi-connection-mode=3`, `native-ap-transport=0`, `log-level=0`. Delta from backup: exactly those two keys (`log-level` 2→0, `native-ap-transport` 1→0).
- Radio: D-HU station up (`Pegue Cdesta`, 5500 MHz) throughout; phone WiFi toggled off for each bring-up then on; phone Bluetooth cycled once to get session 2.
- Discard-rule check: **clean.** One `5GHz createGroup SUCCESS!` per session on both builds; `p2p-wlan0` interface index `-1` → `-2` monotonic (candidate); control likewise one create per session.

### Candidate (`9635f8a5`)

| Step | Line | Time |
|---|---|---|
| 1 session 1 up | `AapSslContext.performHandshake \| SSL handshake complete. Session id: +EZ+lmab…` | 21:29:57.973 |
| 2 `headunit://exit` | `AapService.onDestroy \| AapService destroying... (wakeLock held=false)` | 21:30:18.693 |
| 2 (same) | `AapTransport.stop… \| AapTransport stopping and sending byebye` (synchronous `destroy()`→`doDisconnect` path) | 21:30:18.634 |
| 3 session 2 up | `AutoStartReceiver.onReceive \| MATCH! Starting AapService via Bluetooth Auto-start...` 21:30:47.498 → `SSL handshake complete. Session id: oLsDWJUD…` | 21:31:04.682 |
| 4 `headunit://disconnect` | `AapTransport.stop… \| AapTransport stopping and sending byebye` — **after the step-2 destroy** | 21:31:26.355 |

- `AapTransport stopping and sending byebye` **after the service destroy: 1** (the 21:31:26.355 line).
- Total `AapTransport stopping and sending byebye`: 2. Total `AapService destroying...`: 1.

### Control (`a938ba91`)

| Step | Line | Time |
|---|---|---|
| 1 session 1 up | `SSL handshake complete. Session id: 6DssDc9s…` | 21:32:09.624 |
| 2 `headunit://exit` | `AapService.onDestroy \| AapService destroying...` 21:32:40.423; `AapTransport stopping and sending byebye` 21:32:40.344 (synchronous path) | 21:32:40 |
| 3 session 2 up | `MATCH! Starting AapService` 21:33:01.887 → `SSL handshake complete. Session id: yMCh5zRt…` | 21:33:10.917 |
| 4 `headunit://disconnect` | `AutomationActivity.onCreate \| AutomationActivity: Received intent. Action: android.intent.action.VIEW, Data: headunit://disconnect` 21:33:20.330; `AapService.onDisconnected` ran (21:33:20.419) — **no `AapTransport stopping and sending byebye`** | — |

- `AapTransport stopping and sending byebye` **after the service destroy: 0.**
- Total `AapTransport stopping and sending byebye`: 1 (only the step-2 synchronous one). Total `AapService destroying...`: 1.

**Reading:** the string is emitted from one place (`AapTransport.stop()`, reachable only via `doDisconnect(sendByeBye = true)`). On the control, after the first `AapService destroy` the `CommManager._scope` stays cancelled, so `headunit://disconnect` → `disconnect()` → `_scope.launch { doDisconnect(...) }` is a no-op and the byebye never goes out. On the candidate, `destroy()` re-arms the scope, so the later disconnect completes and the byebye is sent. Candidate 1 / control 0, as the brief predicted.

Evidence: `evidence/narrow-band-and-disconnect-scope-round1/r1_candidate.log`, `r1_control.log`.

---

## R2 — the verdict reaches the screen

**PASS**

- Settings written: `connection-issue-video-starved=1755800000000` (long), `connection-issue-dismissed-at` **deleted**, `wifi-connection-mode=3`, `log-level=0`. `native-ap-transport` = `0` for R2a, `1` for R2b.
- No session (phone WiFi off for both sub-runs).

| Sub-run | `native-ap-transport` | Line | Time |
|---|---|---|---|
| R2a | 0 | `MainActivity.updateConnectionIssueBanner \| MainActivity: showing the connection issue banner for VIDEO_LINK_TOO_SLOW` (pid 3108) | 21:34:43.011 |
| R2b | 1 | same line, after `am force-stop` + relaunch (pid 3634) | 21:35:09.736 |

- Banner text on screen (uiautomator, both sub-runs identical): *"Your phone connected and then gave up on the video before a single frame arrived, several times in a row. That is what a WiFi link too slow to carry the picture looks like. Tap to lower the FPS limit to 30, and a lower resolution and AAC audio are worth trying too. On a unit with a 5 GHz band, using it is the better fix."*
- Screenshot: `evidence/narrow-band-and-disconnect-scope-round1/r2_banner.png` (the head unit home screen is fully static, so R2a and R2b screencaps were byte-identical — the distinct pids and timestamps on the two log lines are the decisive evidence).
- Seed stamp read back as `1755800000000` after both sub-runs (no session rendered, so nothing retired it).

---

## R3 — a session that renders retires it

**PASS**

- Ran straight after R2 with the stamp still seeded and `connection-issue-dismissed-at` still absent. `native-ap-transport=0`.
- **Stamp before step 1:** `connection-issue-video-starved` = `1755800000000`. `connection-issue-dismissed-at`: absent (grep count 0).
- Session formed (`SSL handshake complete` 21:35:49.363), `VideoDecoder.outputThreadLoop \| First frame rendered (hardware decode)` **21:35:51.049**, sustained ~49–50 fps, `dropped=0` on every `logThroughput` line, for ~56 s.
- Note: the banner still showed once at launch (21:35:41.977, before the first frame) — expected, the stamp was live and no session had rendered yet.
- `headunit://disconnect` 21:35:47 → `AapService.onDisconnected \| AapService: Disconnected.` 21:36:47.
- **Stamp after disconnect (before force-stop):** `connection-issue-video-starved` = `0`.
- `am force-stop` + relaunch: `HomeFragment.onResume \| HomeFragment: onResume. isConnected=false` 21:37:04.959 — **no `updateConnectionIssueBanner` line, no banner.** uiautomator text shows only "Self Mode" / "Settings".
- **Stamp after step 3:** `connection-issue-video-starved` = `0`.

Screenshot: `evidence/narrow-band-and-disconnect-scope-round1/r3_no_banner.png`. Capture: `r3.log`, `r3_relaunch.log`.

---

## R4 — hotspot bring-up against a busy radio. Run last.

**INCONCLUSIVE (pre-registered)**

- Settings written: `native-ap-transport=1`, `auto-enable-hotspot=true`, `wifi-connection-mode=3`, `log-level=0`.
- Radio at start: station up (`Pegue Cdesta`), head unit hotspot off (`SoftApStateMachine` in `IdleState`).
- Three launches, ≥60 s apart: 21:40:26, 21:42:01, 21:43:10.

### Why INCONCLUSIVE and not PASS

The brief's pre-registered INCONCLUSIVE condition held **exactly**: this rig can neither read nor write its SoftAp config. Attempts 1 and 2:

```
21:40:41.069 D SoftApConfigCompat: could not read the current access point configuration:
              SecurityException: App not allowed to read or update stored WiFi Ap config (uid = 10168)
21:40:41.075 W SoftApConfigCompat: could not configure the access point (SecurityException: …);
              leaving it as the device has it and starting it anyway.
21:42:16.070 D  (same, attempt 2)
21:42:16.079 W  (same, attempt 2)
```

The literal PASS string `HotspotManager: WiFi disabled before enabling hotspot.` is **unreachable on API 34** — the platform ignores the app's WiFi-disable, so the code logs the sibling line after waiting the full radio-settle window:

```
21:40:33.019 I HotspotManager: Setting hotspot enabled=true (API 34, canWriteSettings=true)
21:40:41.059 I HotspotManager: Asked to disable WiFi and the platform ignored it (expected on
              modern Android); the framework will take the radio itself if it needs to.
```

### The facts the brief says still matter

**An access point did come up.** Attempts 1 and 2 found no AP interface (`SoftApCredentials: No interface looks like an access point … Present: dummy0 …, seth_lte0 …, wlan0 …`), saw `getWifiApState` flicker to `12` (ENABLING) then fall back, and each hit the 30 s no-AP timeout with a give-up line. But `wlan2` (an AP interface, `192.168.196.43`) appeared between attempt 2's give-up (21:42:33) and attempt 3's launch (21:43:10) — a delayed result of attempt 2's enable request. **Attempt 3 formed a full Native AA session over it:**

```
21:43:10.612 I SoftApCredentials: SUCCESS - Providing credentials from wlan2: SSID=Navegadortz2,
              IP=192.168.196.43, BSSID=00:27:15:43:06:6A
21:43:10.613 I SoftApCredentials: these credentials come from the manual override …
21:43:16.869 I WirelessServer: Incoming connection detected from /192.168.196.183
21:43:17.074 I AapSslContext.performHandshake | SSL handshake complete. Session id: Zzk/ei5R…
21:44:54.336 D Companion.decrypt | RECV: VIDEO Media Data type: 0 flags: 11 size: 2040   (video flowing)
```

**No give-up line blamed privileges.** Both give-up lines used the non-privilege arm:

```
21:40:53.187 W HotspotManager: Every start path was tried on 5 GHz and no access point came up
              within 6s each. This app is allowed to ask, so either the radio was busy or this
              unit refuses it. Connecting again is worth trying before switching the hotspot on
              in system settings.
21:42:28.192 W  (identical, attempt 2)
```

`canWriteSettings=true` on every `Setting hotspot enabled=true` line. The privilege-blaming arm (`On a non-privileged install this usually cannot be done from an app`) fired **0 times**.

**Not FAIL:** FAIL requires every attempt to fail to bring an AP up **and** the give-up line to blame privileges while `canWriteSettings=true`. Neither holds.

### Per-attempt measurements

| Attempt | Launch | `Setting hotspot enabled=true` → `enableHotspot called` (settle-wait executed) | AP up within its window? |
|---|---|---|---|
| 1 | 21:40:26 | 21:40:33.019 → 21:40:41.067 = **8.05 s** (disable ignored; app waited the full window then proceeded) | No |
| 2 | 21:42:01 | 21:42:08.021 → 21:42:16.066 = **8.04 s** | No (AP `wlan2` appeared afterward) |
| 3 | 21:43:10 | no `setHotspotEnabled` call — `wlan2` already present at launch | Yes — session + video formed over it |

### Radio restore

On R4 the framework took the station radio for the AP (`Wi-Fi is disabled`, `wlan2` up). Restored by hand at round end: `cmd wifi stop-softap` (`Soft AP stopped successfully`) + `svc wifi enable` → auto-re-associated to `Pegue Cdesta` at 5500 MHz, `wlan2` gone. No operator tap needed.

Capture: `evidence/narrow-band-and-disconnect-scope-round1/r4.log` (full).

---

## R5 — the profile cap does not fire on a 5 GHz radio

**PASS** (regression-guard branch — §3 predicted this)

- Settings written: `fps-limit=60`, `resolutionId=3`, `narrow-band-profile-cap` = default (key absent = `true`), `native-ap-transport=0`, `wifi-connection-mode=3`, `log-level=0`.
- **Rig's own band answer, verbatim:** `WifiDirectManager: this unit's WiFi radio reports a 5 GHz band.` (21:39:21.759, from a forced fresh `createQuietGroup` — see setup notes).
- `HeadUnitScreenConfig.recalculate \| [RES_CAP] resolutionId=3 realScreen=1440x720 usable=1440x720 portrait=false locked=true chosen=_1920x1080 capped=_1920x1080 changed=false linkCapped=none` — **`linkCapped=none`**.
- `This unit has no 5 GHz band`: **0** occurrences. No `[ServiceDiscovery] This unit has no 5 GHz band` line. No `asked for at most 720p and 30 fps` line.
- Announced profile unchanged from settings: `[ServiceDiscovery] NegotiatedResolution is: 1920x1080`, `[ServiceDiscovery] Negotiating a profile this device claims to carry: … target=1920x1080@60`, `H.265 SPS parsed: 1920x1080 (negotiated 1920x1080)`. Sustained ~50 fps on the wire (`logThroughput`: `rendered=254 (50fps)` … `dropped=0`) — the phone caps a few fps below 60 by its own rate adaptation, not the OHU cap; the announced target is 1920x1080@60.
- Group formed on 5745 MHz (5 GHz).

Capture: `evidence/narrow-band-and-disconnect-scope-round1/r5.log`.

---

## Anything the brief did not ask about

- **MT50 station recovered from OFF by plain `svc wifi enable` twice** this session and auto-rejoined `Pegue Cdesta` both times, plus once more via `cmd wifi stop-softap` + `svc wifi enable` after R4. The §7a / memory note that this rig needs an operator tap to re-join did not bite today. Worth re-checking before relying on it either way — this rig's WiFi recovery behaviour is not stable across sessions.
- **Poke correctly suppressed on a healthy Native session:** `NativeAaHandshakeManager.noteHandsFreePokeSkip \| NativeAA: Not poking POCO X3 NFC … this head unit already holds a Bluetooth hands-free link, which a poke would take over and leave disconnected.` seen on every session bring-up.
- **The hotspot transport does work end-to-end on this rig** once an AP is actually up and the manual-override SSID (`Navegadortz2`) matches — R4 attempt 3 proved it with a full session and video. This is consistent with the standing "hotspot config unreadable" finding; what R4 adds is that the app's own enable request is what eventually brought the AP up, just outside the 30 s per-attempt budget the credentials provider allows.
- **`AapService destroying...` fires exactly once** on a `headunit://exit` on both builds — the exit path itself is not the source of the control's missing byebye; the missing byebye is specifically on the *subsequent* `headunit://disconnect`, which is what R1 isolates.
