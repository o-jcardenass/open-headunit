# bring-up-status-pill-and-poke-readiness — round 5 results

**Candidate:** `fork/testing/status-pill-plus-aac` @ `8c6d90e4`       **Baseline:** none (brief §1, no `main` behaviour to compare)
**APK md5:** `b764c4ddf2e5990572db206f293cfa01` (`com.andrerinas.headunitrevived_3.4.0-beta1_debug.apk`, versionCode 106), identical on D-HU and D-POCO
**Unit:** D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14, single BT radio, adbd root), bonded name "Navegadortz2". D-POCO = POCO X3 NFC (`M2007J20CG`, Android 11, not rooted), phone role for Parts A/B/D (AAC1/2/4), head unit role for Part C's C2 and Part D's AAC3. D-MOTO = motorola edge 30 neo (`miami`, not rooted), phone role for Part C's C2 and Part D's AAC3, and (mid-round) newly paired to D-HU to unblock C1's two-phone scenario.
**Date:** 2026-09-11

## Setup notes

### Scripts used (`hur-wifi-test-scripts/`, a sibling dir, not this repo)
`build_hur.sh`, `run_unit_tests.sh` (R0), `set_hu_settings_host.py` (D-HU, rooted), `set_hu_settings_runas.py` (D-POCO, run-as). No new script needed.

### Deviations and findings worth flagging before the run-by-run detail

1. **`adb shell am broadcast -a <action>` alone is silently dropped on this Android 14 unit.** B1's own scripted line (`am broadcast -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es extra_mac ...`) produced `Broadcast completed: result=0` with no app-side effect at all — `dumpsys activity broadcasts history` showed the one matching manifest receiver (`AutomationReceiver`) `SKIPPED terminal ... reason: skipped by policy at enqueue: Background execution not allowed`, even though the app process was alive and not force-stopped. Adding `-p com.andrerinas.headunitrevived` (making it an explicit-package broadcast) fixes delivery immediately and is confirmed working for every subsequent broadcast in this round (including `ACTION_QUERY_STATE`). This affects every `am broadcast -a ...` line in this brief and in `TESTING-TEMPLATE.md`'s own automation-surface section — worth fixing at the template level, not just noting per-round. The discarded first B1 attempt is kept as `b1-dhu.txt.gz`.
2. **D-POCO can end up hosting its own stale WiFi Direct group as Group Owner** (`DIRECT-MW-Navegadortz`, `isGO: true`) left over from an earlier round where it played head-unit role. While that group stands, a phone-role connection attempt to D-HU stalls indefinitely at `PHONE_JOINING` (the RFCOMM handshake completes, credentials are sent, but `WirelessServer: Incoming connection detected` never appears) — this cost about 5 minutes on the first AAC1 attempt before being diagnosed via `dumpsys wifip2p` and cleared with a plain `svc wifi disable`/`enable` cycle (POCO is not rooted, so `cmd wifip2p remove-group` is refused with `SecurityException`). Worth a pre-round check in future rounds: `dumpsys wifip2p | grep isGroupOwner` on every phone before starting.
3. **D-MOTO was paired to D-HU mid-round** (operator action, to unblock C1 — see C1 below) and this had two knock-on effects that cost real rig time: (a) it changed D-HU's driver-candidate pool from 1 phone to 2, and once D-MOTO's own classic-BT profile briefly went "connected" during the pairing dance, `NativeDriverSelectionPolicy`'s AUTO-mode "exactly one connected phone wins" rule silently auto-selected **D-MOTO** instead of D-POCO for what was meant to be an AAC1 (D-HU/D-POCO) run — caught only because the operator, watching the pill, flagged that it named D-MOTO. That run was discarded (`aac1-dhu.txt.gz`, kept for the record) and redone after disabling D-MOTO's Bluetooth radio to take it out of the connected-candidate pool. (b) It also left D-MOTO's own Android Auto/Gearhead bound to D-HU as its "current" wireless car, which then made **AAC3** (D-POCO head unit, D-MOTO phone) fail outright: D-POCO's poke loop successfully woke D-MOTO's Bluetooth six times (`hands-free service level connection established`) but D-MOTO's Android Auto never opened the AA channel back to D-POCO at all. The app's own diagnostic nailed it exactly: *"The phone has answered 6 wake pokes but has never opened the Android Auto channel ... Its Android Auto is most likely bound to a different Bluetooth device that also advertises the Android Auto service."* This is a real rig-identity conflict, not a build defect, and AAC3 is reported INCONCLUSIVE per the brief's own allowance for this exact failure shape. Fixing it would need touching D-MOTO's UI (forgetting one of the two cars) or removing the D-HU/D-MOTO bond, either of which was out of scope to do unattended mid-round.
4. **Google Maps navigation could not be reliably scripted on D-POCO from a cold/locked state** — `am start -a VIEW -d google.navigation:...` landed behind a secure lock screen and an "Open with" chooser that consumed several attempts and screenshots to get through even after `cmd connectivity airplane-mode disable` + `svc wifi/bluetooth enable` + waking the screen. Once the phone was already unlocked (mid-AAC1-session), the identical intent worked on the first try and Maps did start "Driving with" guidance — but no spoken maneuver was ever captured in any AAC run's log window despite this. AAC1/AAC2's navigation-prompt requirement is therefore only partially met: Maps was actively navigating during AAC2, but no `AAC Decoder started for 16000 Hz` line beyond the one produced automatically at connection setup (a system/greeting prompt, not a route maneuver) was observed. A `cmd notification post -S messaging ...` was tried as a lighter-weight substitute for both the navigation prompt and the notification requirement; it posted successfully but produced no audio-channel activity, consistent with Android Auto requiring a real `CarAppExtender`-tagged messaging notification to speak one, which a bare shell-posted notification does not carry.
5. Settings backups (`dhu-settings-backup.xml`, `dpoco-settings-backup.xml`) were taken before any write and restored at the end of the round on both units; radio states were restored to their exact values at round start (D-HU: WiFi+BT on; D-POCO: WiFi+BT off; D-MOTO: WiFi on, BT off — D-MOTO's bond to D-HU was left in place since undoing a bond is a larger, more visible action than this round's own scope covers). `native-preferred-device-mac` was left absent throughout, confirmed absent in every settings read-back.
6. **`headunit://exit` does nothing if the app has already been force-stopped** — sending it to a dead process simply cold-launches the app via `AutomationActivity`, which then runs its own normal auto-connect-on-startup logic and can form a brand-new group, the opposite of the intended "confirm no group" effect. Always send `headunit://exit` to a *running* app, then verify with `dumpsys wifip2p`.

## R0 — build and unit-test gate

**PASS**

- Build: `build_hur.sh` → `BUILD SUCCESSFUL`, apk md5 `b764c4ddf2e5990572db206f293cfa01`.
- Unit tests: `run_unit_tests.sh` → `BUILD SUCCESSFUL`; test-result XMLs sum to **1643 tests, 0 failures, 0 errors, 0 skipped**, matching the brief's gate exactly.
- Install: `adb install -r` on both D-HU and D-POCO; live `md5sum` of each `pm path` base.apk matches the built apk and each other.
- Identity: DEX grep `AacDecoderRecoveryPolicy` → 3 hits, `AutoStartOfferPolicy` → 11 hits (both present, as the brief predicts). `ACTION_QUERY_STATE` (sent with `-p`, see Setup note 1) → `"commit":"8c6d90e4b14e"`.

---

## Part A — bring-up, D-HU head unit, D-POCO phone

### P1 — Native AA cold bring-up to projection

**PASS**

- Clean-run protocol: D-POCO airplane mode on at capture start, D-HU force-stopped/cleared/launched, D-POCO radios restored 18s after launch (both confirmed on).
- Discard-rule check: `MATCH!`=0, `createGroup SUCCESS`=1, `Magic Garbage`=0, one `SSL handshake complete`.
- **Ordered `status pill step:` list** (file order, which is the true emission order for this single-coroutine sequence; a handful of these lines print with an out-of-order *timestamp* under this device's heavy WindowManager/Layer log volume — noted, not treated as a defect):

  | # | time | value |
  |---|---|---|
  | 1 | 07:08:38.287 | ARMED |
  | 2 | 07:08:38.399 | PREPARING_NETWORK |
  | 3 | 07:08:38.788 | WAITING_FOR_PHONE |
  | — | 07:08:38.802 | `startNativeAaQuietHost() requested` |
  | 4 | 07:08:38.803 | PREPARING_NETWORK *(rank decrease, immediately after the line above — allowed, same pattern round 4 recorded)* |
  | 5 | (07:08:37.931 printed) | CREATING_NETWORK |
  | 6 | (07:08:38.285 printed) | WAKING_PHONE |
  | 7 | 07:09:09.136 | WAITING_FOR_PHONE *(rank decrease, WAKING_PHONE→WAITING_FOR_PHONE — allowed; count = 1)* |
  | 8 | 07:09:24.155 | WAKING_PHONE |
  | 9 | 07:09:25.537 | PHONE_ANSWERED |
  | 10 | 07:09:26.123 | SENDING_CREDENTIALS |
  | 11 | 07:09:27.145 | PHONE_JOINING |
  | 12 | 07:09:28.144 | CONNECTING |
  | 13 | 07:09:28.275 | SECURING |
  | 14 | 07:09:28.413 | STARTING_PROJECTION |

  14 lines (≥6 required), first ARMED, WAITING_FOR_PHONE before WAKING_PHONE, last STARTING_PROJECTION, exactly one WAKING_PHONE→WAITING_FOR_PHONE decrease plus the one allowed decrease right after the create-request line — same shape as round 4's own P1. `SSL handshake complete` at 07:09:28.407.
  Wall clock, first `status pill step:` to `STARTING_PROJECTION`: **50.13s.**

### A1 — a bring-up with WiFi off does not wedge the mode

**Mixed: conditions 1, 2 and 4 PASS; condition 3 did not produce the expected line, for a reason distinct from the fix under test.** Also surfaced a separate, more serious defect (below).

Priming launch (auto-start-bt-macs written, one launch, `BT Device connected:`/`MATCH!` mechanism confirmed working via the device-protected mirror directly: `cat /data/user_de/0/.../settings_device_protected.xml` showed the MAC present after one launch).

Run proper: `svc wifi disable` confirmed off, launched, left 60s, then `svc bluetooth disable` on D-HU (self-reverts ~14s, produces a real `ACL_CONNECTED`), waited for `MATCH!`, then `svc wifi enable`, left 60s+ more.

1. **PASS.** `WifiDirectManager: WiFi is off and this Android does not let an app switch it on.` appears **exactly once** in the whole capture (07:13:47.221), not once per 10s poll.
2. **PASS.** `WifiDirectManager: a Native AA group create is claimed` never appears before 07:19:48 (when WiFi was re-enabled) — zero occurrences while WiFi was off.
3. **Not met as literally stated, but for a documented reason.** `MATCH! Starting AapService` fired at 07:15:07.973, 14.3s after the BT toggle (matches the self-revert quirk exactly). Neither the "left to answer" line nor a `BtAutoStartActions(...)` rearm line appeared — `AapService`'s `ACTION_BT_AUTO_START` handler ran (confirmed: nothing in its four-branch if/else chain fired, and none of the four possible log lines printed at all), because `BtAutoStartRearmPolicy.actionsFor()`'s very first guard, `attemptInFlight == true`, was true: a **stale, stuck handshake attempt** from an earlier POCO reconnect (POCO reconnects to the AA RFCOMM UUID every ~12s on its own initiative while a hands-free profile link is up, regardless of app action, and each one starts a 60s "waiting for credentials" window that only WiFi coming back can ever satisfy) was still in flight at the exact moment `MATCH!` landed. `AapService`'s own final fallback branch (`attemptInFlight != true`) is *also* gated on the same flag, so the same stuck-handshake state silently suppressed it too. The brief's own §4 line list assumes `networkComingUp` is the relevant veto in this scenario; it is not reached here because a different, independently-correct veto (`attemptInFlight`) fires first. This is not evidence the fix is broken — conditions 1/2/4 directly answer the brief's actual question (does WiFi-off wedge the mode) with "no" — but the literal line the brief asks to quote was never produced, so this condition is reported as not-met rather than forced into a PASS.
4. **PASS.** After `svc wifi enable` at 07:19:47, recovery was fully automatic: `WIFI_P2P_STATE_CHANGED_ACTION state=2` → `P2P enabled, auto-starting Native AA quiet host` → `a Native AA group create is claimed` → `startNativeAaQuietHost() requested` → `5GHz createGroup SUCCESS!`, all within 1.1s, no further gesture.

**Separate finding, more serious than anything A1 asked about.** The `svc bluetooth disable` toggle that condition 3 needs killed the live RFCOMM accept loop with a real exception (`NativeAA: AA Server socket error: read failed... at BluetoothServerSocket.accept`), and **the accept loop never reopened for the rest of the process's life**, even though the WiFi group itself recovered cleanly at 07:19:48 per condition 4. With no listener to accept the phone's return connection, the group recovery watchdog cycled through all 4 recreate attempts (`recreate attempt 1/4` … `4/4`, one per minute) and then gave up outright: `WifiDirectManager: Native AA — phone still not connected after 4 recreations ... giving up until the next start.` The mode was fully wedged from that point, requiring a fresh app launch to recover — this on the exact rig-realistic scenario (the head unit's own Bluetooth bouncing) that A1's own test script uses to simulate a real arrival, and that CLAUDE.md's own "two feedback loops" section already documents as routine on this hardware. This looks like the gap the *unpushed* `fix/760-poke-during-wifi-association` branch's "`start()` reopens closed AA listeners" line item was written to close (see the repo's Open-defects section) — it is not part of this candidate.

### A2 — the group waits for this unit's own access point to go down

**INCONCLUSIVE (2/2 attempts).** Raised a 5GHz soft AP with `cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5`, confirmed up via `dumpsys wifi`, then launched. `WifiLauncherNative: waiting for this unit's hotspot to go down before creating the group.` never appeared in either attempt — `createWhenRadioIsFree()`'s own `teardown.isCompleted` check found the app's own `HotspotManager.setHotspotEnabled(false)` teardown already finished (well under 500ms) both times, including a second attempt with the AP raised and the launch chained immediately (`start-softap && am start`, no confirmation step in between) to close the "seconds-later" gap the brief itself calls out. Group formation succeeded cleanly both times (`5GHz createGroup SUCCESS!` ~0.4–1.1s after the request), and neither `hotspot had not gone down after 10s` nor `HotspotManager: the access point was still up` appeared — so nothing here suggests a regression, only that this unit's own soft-AP teardown is consistently too fast to exercise the wait branch. Per the brief's own instruction this is reported as INCONCLUSIVE rather than a no-op PASS; a third attempt is not expected to change the outcome given the second attempt already minimised the timing gap as far as adb scripting allows.

---

## Part B — a wake that does nothing says why, on D-HU

### B1 — the wake line

**PASS**, after correcting for Setup note 1 (the `-p` package flag).

Scripted per the brief exactly, with `-p com.andrerinas.headunitrevived` added to the broadcast: `am force-stop` → `svc bluetooth disable` → launch → `sleep 4` → poke broadcast (landed 4s after disable, well inside the ~14s self-revert window — confirmed still off via `NativeAA: Bluetooth adapter not available or disabled` printed at the same moment).

- `AapService: the Native AA handshake servers are not running (this unit's Bluetooth was off or unavailable when the mode was armed), so nothing could answer the phone. Starting them before the poke.` — 07:31:56.790.
- `NativeAA: not waking null — the handshake servers are not running (this unit's Bluetooth was off or unavailable when the mode was armed)` — 07:31:56.806, once, separator is an em dash (—), matching the brief's warning that it's easy to mistype. The device name prints as `null` here because the manual-poke target device object could not be resolved while the adapter was disabled — consistent with the state being reported, not a second defect.

The first attempt (`b1-dhu.txt.gz`, discarded) used the brief's un-modified broadcast command and produced neither line — `dumpsys activity broadcasts history` traced this to the background-execution skip in Setup note 1, not to the app.

---

## Part C — the auto-start offer

### C1 — two phones clear the stored trigger, on D-HU

**FAIL**, on a second attempt after D-MOTO was paired to D-HU mid-round to make the two-phone scenario reachable at all (see below).

First attempt: only D-POCO was bonded to D-HU (`dumpsys bluetooth_manager`'s `Bonded devices` list had one entry), so `driver candidates: 1 phone` — this is the brief's own pre-registered INCONCLUSIVE condition ("if it reports 1, the run never reached the two-phone arm"), reported as such and not counted as a run.

Second attempt, after the operator paired D-MOTO to D-HU: `driver candidates: 2 phone, 0 unknown, 0 not a phone` confirmed. Settings as specified (`auto-start-bt-macs=DC:B7:2E:5E:4E:59`, `auto-start-offer-answered-macs` deleted, both read back correctly before launch). No dialog appeared (screenshot at +5s, `c1b-screenshot.png`), matching condition 3.

1. **FAIL.** `HomeFragment: more than one phone is paired, so the Bluetooth auto-start device is cleared.` never appeared anywhere in the capture.
2. **FAIL, consequently.** `auto-start-bt-macs` still read back as `DC:B7:2E:5E:4E:59` after the run (not cleared), on both the host settings file and (not separately re-checked on the mirror, since the host value alone already fails the condition).
4. Not gated by 3/2, but for the record: `driver candidates: 2 phone` (condition 4's own check) was satisfied.

`AutoStartOfferPolicy.decide()` is pure and unit-tested, and by its own logic (`phonesPaired >= 2` and `autoStartConfigured=true` from the pre-set `auto-start-bt-macs`) should return `Action.RESET`, which unconditionally prints the line and clears the setting — that branch demonstrably did not run. Root cause is not settled from the device log alone: `checkAutoStartOffer()`'s own `BluetoothHelper.driverCandidates()` call is one of five call sites in the codebase producing an identical log line, so the single "driver candidates: 2 phone" line seen in this capture cannot be attributed to a specific caller by content alone, and neither `checkNativeDriverSelectionOnStartup`'s two logging branches (selector shown / unambiguous auto-connect) printed either, which is consistent with it returning `false` via its own silent bottom fall-through and *should* have let `checkAutoStartOffer()` run next. This needs a source-level review pass rather than more rig time — the two most likely candidates are (a) a stale in-memory `answeredMacs` or `autoStartBluetoothDeviceMacs` read racing the on-disk settings write from immediately before launch, or (b) `checkAutoStartOffer()` itself hitting its own early return (`adapter == null || !adapter.isEnabled`) for a reason not visible from this log.

### C2 — one phone is asked, once, on D-POCO as head unit

**FAIL**, and a genuine, reproducible defect.

First attempt (`native-driver-selection-mode` left at its standing value, `1`/AUTO): the offer path is structurally unreachable by design — `HomeFragment.onResume`'s own gate is `if (!checkNativeDriverSelectionOnStartup()) checkAutoStartOffer()`, and with exactly one paired phone in AUTO mode, `checkNativeDriverSelectionOnStartup()` always auto-connects and returns `true` (`HomeFragment: Unambiguous driver (motorola edge 30 neo) - auto-connecting directly without prompt`), so `checkAutoStartOffer()` is never called. This matches round 4's own documented standing behaviour for this exact device/mode combination and is intentional per the code's own comment ("the selector owns this moment when it wants it") — but it means C2's own scenario, as scripted (a single paired phone, mode left at the standing AUTO value), can never reach the offer on this unit. This is a brief/settings-table gap, not a build defect: the brief's §3 table does not mention `native-driver-selection-mode` for D-POCO at all.

Second attempt, with `native-driver-selection-mode` set to `0` (DISABLED) so `checkNativeDriverSelectionOnStartup()` returns `false` immediately and `checkAutoStartOffer()` runs: the offer **is** raised — proven by `auto-start-offer-answered-macs` containing `A0:46:5A:97:E4:95` after the run (written the instant the dialog is put up, per the code's own comment) — but it is never usably shown. `E/WindowManager: android.view.WindowLeaked: Activity ... MainActivity has leaked window ... DecorView ... [MainActivity]` fires at 07:37:28.575, between two `HomeFragment.onResume` calls 573ms apart (07:37:28.264 and 07:37:28.837) — the same stored-orientation relaunch the brief's own condition 2 warns about for the driver selector (D-POCO launches portrait, `screen-orientation=2` forces a rotation-driven Activity recreation about half a second to a second later) destroys the auto-start-offer dialog's window before the user can ever see or answer it. The screenshot at +5s (`c2b-screenshot.png`) shows no dialog, only the ordinary connection pill, confirming the user-visible effect.

This is worse than a simple UI glitch: because the "answered" mark is written on **put**, not on **answer**, the dialog is now permanently suppressed for that phone (`answeredMacs.any {...}` will match on every future launch) — the user is never given a chance to say yes, and the app will never ask again. Given round 4 already fixed an equivalent stored-orientation-relaunch crash for the driver selector via an `isAdded` guard on the *callback*, this is the same hazard reaching a different dialog's `.show()` call itself, one call site the earlier fix did not cover.

- Condition 2 (no `FATAL EXCEPTION`, same PID before/after) is technically met — `WindowLeaked` is a warning, not a crash, and the PID (19023) is unchanged throughout, confirmed via two `pidof` reads. This is reported as a pass on the letter of the condition while flagging that the underlying window-leak is arguably a worse outcome than the crash condition 2 was written to catch.
- Conditions 1, 3 and 4 are moot: the dialog was never in a state a user could act on.

Restored `auto-start-bt-macs` to empty and `native-driver-selection-mode` to its original value (`1`) on D-POCO at the end of Part C.

---

## Part D — AAC audio

Both rig units create 5GHz-capable groups in every capture, so the 2.4GHz-only cap never announces here (brief §2); `use-aac-audio=true` was used throughout to force the announcement via the other arm of `useAac = userChoice || caps(...)`, exactly as the brief specifies.

**No spoken guidance or notification audio was captured in any run** (Setup note 4) — Maps was confirmed actively navigating during AAC2 (`Driving with Google Maps` in the notification shade) but produced no additional `AAC Decoder started for 16000 Hz` beyond the one instance already logged at connection setup, and a raw shell-posted notification produced no audio-channel activity at all. Every AAC run below is therefore full coverage for the **media** channel and condition 1's codec-agreement table, but only partial coverage for condition 2's "twice" requirement on the 16kHz guidance channel.

### AAC1 — D-HU / D-POCO, `use-aac-audio=true`, `static-audio-focus=false`, `log-level=0`

**PASS**, on the corrected run (`aac1c-dhu.txt.gz`). A first attempt (`aac1-dhu.txt.gz`) connected to **D-MOTO instead of D-POCO** — caught by the operator watching the pill — because D-MOTO's Bluetooth profile had just gone "connected" from its fresh pairing to D-HU, and `NativeDriverSelectionPolicy`'s AUTO-mode "exactly one connected phone wins" rule picked it over the poke target. Discarded and redone after disabling D-MOTO's Bluetooth radio; `Driver selected: DC:B7:2E:5E:4E:59` confirmed before connecting.

1. **PASS.** Channel table:

   | Channel | `Media Sink Setup Request` type | `AudioDecoder.start: isAac=` | source |
   |---|---|---|---|
   | AUDIO (media) | 2 | true | setup |
   | AUDIO1 | 2 (announced only, no traffic — see above) | — | — |
   | AUDIO2 | 2 (announced only, no traffic — see above) | — | — |

   Every announced sink read `source=setup`; zero `source=setting`.
2. `AAC Decoder started for 48000 Hz, 2 channels` — once, as required. `for 16000 Hz, 1 channels` — zero times in this run (no guidance/notification traffic reached D-HU during this specific session).
3. Zero of every listed failure string (`AAC Input Buffer timeout`, `AAC Codec Error`, `Failed to init AAC decoder`, `rebuilding the AAC decoder`, `giving up on this sink`, `no working AAC decoder`, `AAC decoder output is`, `AapAudio: sink setup type`).
4. `AudioTrackWrapper thread finished.` — no drop count appended.
5. Zero underruns; `inbound rate over ...` steady at **audio=62–63 kB/s** across the full ~3-minute window (07:55:21 to 07:58:21).

No sink ever fell back to type 1 mid-session on any channel.

### AAC2 — D-HU / D-POCO, `use-aac-audio=true`, `static-audio-focus=true` (the mixer), `log-level=2`

**PASS.**

1. Channel table:

   | Channel | Setup Request type | `isAac=` | source | sampleRate/ch |
   |---|---|---|---|---|
   | AUDIO2 (guidance) | 2 | true | setup | 16000 Hz, 1 ch |
   | AUDIO1 | 2 (announced, no traffic) | — | — | — |
   | AUDIO (media) | 2 | true | setup | 48000 Hz, 2 ch |

   The 16kHz guidance decoder started automatically at connection setup (an initial system prompt, not a route maneuver — see the Part D preamble) at 08:09:52.889/970, giving one clean media+guidance decode pair running concurrently through the shared mixer.
2. `AAC Decoder started for 48000 Hz, 2 channels` once; `for 16000 Hz, 1 channels` **once**, not twice — the brief's second instance depends on a genuine navigation maneuver or a proper Android-Auto-recognised notification, neither of which this round could reliably script (Setup note 4).
3. Zero of every listed failure string.
4. Two `AudioTrackWrapper thread finished.` lines (one per decoder), neither carrying a drop count.
5. Zero underruns; `inbound rate over ...` steady at audio=62–63 kB/s across the session, matching AAC1 despite the extra concurrent 16kHz stream (consistent with the guidance stream's low bitrate).

### AAC3 — D-POCO / D-MOTO, `use-aac-audio=true`, `static-audio-focus=false`

**INCONCLUSIVE**, per the brief's own allowance for exactly this failure shape ("if music cannot be started ... within a few attempts, AAC3 is INCONCLUSIVE").

D-POCO (head unit) successfully created its group and woke D-MOTO's Bluetooth six separate times (`hands-free service level connection established`), but D-MOTO's own Android Auto never opened the AA channel back — the app's own diagnostic explains why: *"The phone has answered 6 wake pokes but has never opened the Android Auto channel on radio [POCO X3 NFC]. Its Android Auto is most likely bound to a different Bluetooth device that also advertises the Android Auto service."* This is a direct, well-evidenced consequence of Setup note 3 (D-MOTO pairing to D-HU mid-round), not a decoder or codec defect — no `Media Sink Setup Request` or `AudioDecoder.start` line was ever reached, so this run carries no evidence for or against the "second decoder vendor" question condition 1 asks about. AAC1/AAC2/AAC4 stand on their own per the brief.

### AAC4 — D-HU / D-POCO, `use-aac-audio` deleted (PCM baseline), `static-audio-focus=false`

**No verdict (baseline), as specified.**

- Sinks: `Media Sink Setup Request: 1` on all three audio channels (PCM, as expected with the setting absent and this unit's 5GHz-capable radio never tripping the narrow-band cap).
- `AudioDecoder.start: ... isAac=false, source=setup` for the media channel; no AAC decoder lines anywhere, zero errors.
- `inbound rate over ...`: steady **audio=187 kB/s** across the full window (08:01:25 to 08:03:56), roughly **3× AAC1's 62–63 kB/s** for the same music track and duration.

---

## 6. Do not re-run

Per the brief's §6, none of P2/P3/P4/P5/W2/WB1/WB1b/D1/D2/D3/P6 were re-run — all settled on `7f439d4e` in round 4 and untouched by the three commits since.

## Anything the brief did not ask about

- **The `am broadcast -a` background-execution gap (Setup note 1)** is worth fixing in `TESTING-TEMPLATE.md` itself, not just noted per-round: every future brief's automation-surface commands need the `-p` flag on this Android version, and the template's own §3 examples currently omit it.
- **The RFCOMM-accept-loop-never-reopens defect found under A1** (a real bring-up wedge on a routine, rig-documented BT bounce) is a more serious finding than A1's own stated question, and looks like exactly what the unpushed `fix/760-poke-during-wifi-association` branch's listener-reopen fix was written to address. Worth prioritising that branch, or a narrower fix, ahead of the AAC "(Experimental)" label decision.
- **The auto-start-offer window-leak under C2** is a genuine, reproducible bug with a bad failure mode (silent, permanent, one-way suppression of a feature the user never got to answer) — distinct from, but structurally identical to, the driver-selector crash round 4 already fixed. The same `isAdded`-guard pattern likely needs to move earlier, to guard the `.show()` call itself against a relaunch that is already in flight, not just the dialog's button callback.
- **C1's root cause is unresolved** and would benefit from a source-level pass rather than more rig time: `AutoStartOfferPolicy.decide()`'s own logic clearly calls for `Action.RESET` given the settings measured at launch, and that branch demonstrably did not run.
- D-MOTO remains bonded to D-HU at the end of this round (an artifact of unblocking C1 mid-round) — this is now the rig's standing state and should be accounted for by whoever plans the next round touching driver selection, Part C, or Part D.
