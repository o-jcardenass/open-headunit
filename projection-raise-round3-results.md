# projection-raise — round 3 results

**Candidate:** fix/audio-sink-and-wireless-bring-up @ c2effbca       **Baseline:** none this round
**APK md5:** ed1f09de0787dc35af921632a730aaf5
**Unit:** D-HU (UNISOC MT50, Android 14), D-SAM (Galaxy Tab 4 7.0, Android 4.4.2 / API 19), D-HP (HP Slate 7 Plus, Android 4.2.2 / API 17), D-POCO (phone, Gearhead as installed)
**Date:** 2026-09-17

## Setup notes

- Gate: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testGithubDebugUnitTest` → 2149 tests, 0 failures, matching the brief exactly. This host's default `java` is a full JDK (not the JRE-with-no-javac round 2 flagged), so `JAVA_HOME` was carried over defensively but may not be load-bearing here.
- **All five rig devices were left plugged in simultaneously for the whole round**, on the operator's instruction that they do not interfere, rather than the brief's own stage-by-stage unplug/replug sequence. D-MOTO and D-HP were plugged in partway through (see below); nothing in the results below depends on isolation between stages.
- Section 2's `shared_prefs` ownership check, done before each stage: D-HU `u0_a174:u0_a174` (771), D-SAM `u0_a154:u0_a154` (771), D-HP `u0_a71:u0_a71` (771) — all correctly owned, no `chown` needed anywhere this round.
- **W1r2's own precondition (D-POCO holding a live HFP link to D-HU) did not already exist** and had to be built: D-HU's `auto-start-bt-macs` was set to D-POCO's MAC to force the ordinary auto-poke loop to probe it, which did not by itself produce a *held* link (our own poke connects and releases). What worked was then bouncing D-HU's own Bluetooth radio (`svc bluetooth disable`, which self-reverts on this unit within ~14s) — D-POCO's own stack reconnected its previous HFP profile on its own once D-HU's radio came back, confirmed `Connected` via `dumpsys bluetooth_manager` before arming. `auto-start-bt-macs` was cleared before the graded W1r2 capture so it would not add unaccounted poke noise.
- **Disabling and re-enabling Gearhead on D-POCO (required by W1r2) was blocked by this session's own permission classifier** (`pm disable-user`/`pm enable` flagged as risky on a real device); the operator ran both commands by hand via `!`. Not a rig finding — a note for anyone continuing this exact session.
- **`native-aa-wake-damage-verdict` latches permanently once measured** ("so it will not be used again"), which is by design, but it meant N5r2 could not reach a real session against D-POCO afterward without a settings write not listed in the brief's own preconditions. Reset to `0` (unmeasured) via `set_pref.sh` between W2r2 and N5r2 to let ordinary poke/session testing continue; it re-measured itself safely once a real session connected (`"measured safe on this unit: the hands-free link came back on its own"`).
- **D-SAM's `wifi-direct-group-name-changes` read `4`** (already past the `RENAMED` threshold) before this round touched it, left over from an unreported earlier session. Reset to `0` via a local `python3` edit of `settings.xml` + `run-as $PKG cp` (D-SAM has no `sed`) for a clean B3r baseline.
- **D-SAM's clock matched the host exactly** (`date` on both agreed to the second) — the ~12h offset TESTING-TEMPLATE records through round 7 is confirmed gone.
- **D-SAM's `wifi-connection-mode` was changed `3` → `1`** for L3r and left at `1`; no further D-SAM runs this round needed it back.
- **A1 needed AA's dev "Start head unit server" on D-POCO**, which has no scriptable trigger; the operator did the one manual tap. Its own well-documented ~15–20s self-disconnect flakiness (recorded in round 2's own Setup notes) reproduced once (session dropped after ~19s, `WiFi read timeout (15000ms) - connection lost`), then held stably for the remainder without operator intervention.
- **The host's own logcat capture process died silently twice during A1's long run** (once for ~2.5 min, once for ~7 min), a tooling/USB artifact on this rig, not an app-side drop — confirmed by an unchanged app pid and no further `AapService: session state disconnected` lines either side of the gaps. A live `logcat -d` buffer dump was used to re-establish continuity each time. This cost the round the one-shot `"Audio queue capacity is 0..."` warning's own log line, which fired inside one of the gaps and had already rotated out of the device's own ring buffer by the time this was noticed.
- **Gearhead was force-stopped on D-POCO at the end of the round** (kills its dev head-unit server); a future round reusing that window needs the manual tap again.
- Scripts used: `set_pref.sh`, `set_autostart_btmac.sh` (both unmodified) for D-HU; a local `python3` edit + `adb push` + `run-as $PKG cp` for D-SAM and D-HP (neither has `sed`, matching the documented quirk). No script was added this round.

## Stage A — D-HU, D-POCO (D-MOTO present but not exercised by any run below; D-SAM/D-HP left plugged in per the note above)

### W1r2 — The escalated wake, on a budget that can now reach it

**PASS.**

- Settings written: `native-driver-selection-mode=1`, `last-connected-native-mac=DC:B7:2E:5E:4E:59`, `native-aa-wake-damage-verdict` cleared to `0` beforehand; `auto-start-bt-macs=DC:B7:2E:5E:4E:59` used transiently to build the HFP precondition, then cleared.
- Radio state: D-HU↔D-POCO HFP link independently confirmed `Connected` (`dumpsys bluetooth_manager`, masked `XX:XX:XX:XX:33:59`) immediately before arming.
- Discard-rule check: single clean run, no re-run needed.
- Decisive log lines, quoted with timestamps:
  - Arming, `21:37:14.949`: *"waking a phone over a hands-free link it holds is not yet measured on this unit, so the first one is the measurement."*
  - Refusal passes (`noteHandsFreePokeSkip`), five of them, 15.0–15.1 s apart, **each costing zero budget**: `21:37:31.241`, `21:37:46.265`, `21:38:01.289`, `21:38:16.312`, `21:38:31.334` — all: *"Not poking POCO X3 NFC (DC:B7:2E:5E:4E:59) — this head unit already holds a Bluetooth hands-free link to it, which a poke would take over and leave disconnected. Waiting for the phone to start Android Auto itself."*
  - Escalation, `21:38:46.356`, **91.4 s after arming** (brief expected "about 90 s"): *"waking POCO X3 NFC (DC:B7:2E:5E:4E:59) despite the hands-free link — it has not started Android Auto in 90s, and a link that never changes raises no event for the phone to notice. Wake 1 of 2; the link is left alone for 30s to come back."*
  - Poke sequence, both within the same second as the escalation: HFP-AG at `21:38:46.369` failed (`.653`), HSP-AG at `21:38:46.659` succeeded → `21:38:46.769` *"Successfully poked POCO X3 NFC via HSP-AG. Holding 20000ms..."*
  - `grep -c "Calling socket.connect()"` between `21:38:46.769` and the verdict: **0**.
  - Verdict, `21:39:16.370`, exactly 30.001 s after the wake: *"the hands-free link is still down 30s after the wake. On this unit a wake costs the link for good, so it will not be used again — the phone has to raise the Bluetooth event itself."*
- Measurements: arm→escalation 91.4 s; refusal cadence 15.0–15.1 s × 5; zero pokes in the 30 s post-wake window.
- Independent `dumpsys bluetooth_manager` samples (masked `XX:XX:XX:XX:33:59`): +30 s (`21:39:16`) Disconnected; +2 min (target `21:40:46`, actually sampled `21:43:07` — turn-scheduling overhead, not a rig delay) Disconnected; +5 min (target `21:43:46`, actually sampled `21:43:55`) Disconnected. Agrees with the app's own verdict at every sample.

The +2 min sample landed late relative to its own target because of scheduling overhead between conversation turns on this end, not anything on the rig; all three independent reads agree with the app's own claim regardless, so it does not change the verdict.

### W2r2 — The verdict holds across an arming

**PASS.**

- Re-arm without reboot, `21:44:31` → `21:44:33.485`: *"waking a phone over a hands-free link it holds is measured to cost this unit its hands-free link for good, so none will go out."* `settings.xml` read `native-aa-wake-damage-verdict="2"`, unchanged from W1r2.
- Reboot issued `21:45:02`; boot completed and `shared_prefs` re-confirmed owned (`u0_a174:u0_a174`, mode 771) afterward; `settings.xml` still read `value="2"`.
- Post-reboot re-arm, `21:45:53.493`, from a fully fresh process: **identical line, verbatim** — *"waking a phone over a hands-free link it holds is measured to cost this unit its hands-free link for good, so none will go out."* Proves the write survives a real reboot, not just an in-process cache.
- Discard-rule: single clean run; ownership was correct both times, so round 2's "absent key means one of two things" ambiguity does not arise here.

### N5r2 — The WiFi button inside the quiet window

Not pass/fail as a whole. Automation `no_ui` sub-item: **PASS** (both forms). UI-tap input-routing sub-item: not reached.

- Broadcast form, after a clean `headunit://disconnect`: `am broadcast -a com.andrerinas.openheadunit.ACTION_START_WIRELESS -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.automation.AutomationReceiver --ez no_ui true` → `22:09:06.172` *"AapService: Not raising the projection, a no_ui command asked for the session only"*. Round 2's erratum (`ACTION_START_WIRELESS` in `PLAIN_RELAYS`, silently dropping the extra) is fixed.
- Direct form: `am start-service -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService -a com.andrerinas.openheadunit.ACTION_START_WIRELESS --ez no_ui true` → `22:09:41.328`, same line. Still works, as round 2 found.
- Deviation: reaching a state where `no_ui` actually had something to suppress required a real session attempt against D-POCO, which `native-aa-wake-damage-verdict`'s latch from W1r2 blocked; reset to `0` first (see Setup notes).
- UI-tap sub-item (tap the WiFi tile inside the phone-quiet window right after a "Finish later" bye-bye) was **not attempted this round**. Reaching it needs a "Finish later" action on D-POCO's own Gearhead screen, which has no adb-scriptable trigger; given the time already spent reaching a live session for the automation half, this was left at the same not-reached status round 2 recorded for it.

## Stage B — D-SAM, D-POCO

### B3r — The rename counter, and which of two causes round 2 actually hit

**FAIL.**

- Settings written: `wifi-direct-group-name-changes` reset `4→0` before bring-up 1 (see Setup notes).
- Four full bring-ups (force-stop + relaunch between each), read-back line and `settings.xml` both, every time:

  | Bring-up | First (ephemeral) callback | Settled SSID / line | Log `nameChanges` | `settings.xml` |
  |---|---|---|---|---|
  | 1 | — (fresh baseline) | `22:11:27.803` DIRECT-LR-Navegadortz3 | `1/3` | `1` |
  | 2 | `22:12:04.159` DIRECT-LR (CHANGED, same name, BSSID moved) `0/3` | `22:12:05.170` DIRECT-oX-Navegadortz3 | `1/3` | `1` |
  | 3 | `22:13:49.562` DIRECT-oX (CHANGED) `0/3` | `22:13:50.613` DIRECT-bA-Navegadortz3 | `1/3` | `1` |
  | 4 | `22:14:16.828` DIRECT-bA (CHANGED) `0/3` | `22:14:17.919` DIRECT-e6-Navegadortz3 | `1/3` | `1` |

- Every bring-up produced a genuinely different SSID (matching D-SAM's known "re-addresses every group" behaviour), but `nameChanges` never exceeded `1/3` in either the log's own read-back line or `settings.xml`, across all four bring-ups. `RENAMED` was never reached; the WPP-over-TCP endpoint was never withheld.
- **Root cause, read from source, not just inferred:** `GroupIdentityStabilityPolicy.assess()`'s `CHANGED` branch (`GroupIdentityStabilityPolicy.kt:109–112`, same name/different BSSID) and its `STABLE` branch (`:105–108`) still construct their `Verdict` without passing `nameChanges = nameChangesSoFar`, so it defaults to `0` (the data class default, `:49`), and `WifiDirectManager.kt:1212`'s `appSettings.wifiDirectGroupNameChanges = verdict.nameChanges` overwrites the persisted counter with that `0` on every bring-up's first, ephemeral `onGroupInfoAvailable` callback — before the second, settled callback's own `nameChangedVerdict()` path (which *does* carry `nameChangesSoFar` forward correctly, `:135`) ever gets to accumulate past `1`. Only the early-return/`UNPROVEN` branches (`:72–90`) and the rename-accumulation path (`:135`) were fixed for this; the `CHANGED`/`STABLE` branches were not.
- This is not a rig or ownership artifact: `shared_prefs` was confirmed correctly owned before the round, and the log's own printed value (independent of any disk read) shows the identical non-accumulating pattern every time.

This reproduces round 2's real-world consequence (RENAMED unreachable, endpoint never withheld on a unit that needs it withheld) through a closely related but distinct code path from round 2's own read of the mechanism — the fix is measurably incomplete rather than untested.

### B2r — Read the phone while the tablet pokes it

Not pass/fail, as specified. The report is the result.

D-SAM's poke of D-POCO's stand-in HFP record accepted again (`AT+BRSF`→`AT+CIND=?`→`AT+CIND?`→`AT+CMER`→`+BSIR: 0`, then *"hands-free service level connection established"* at `22:15:11.982`) — the same shape as round 2's first-ever accept, this time read more deeply:

1. **AG-side state machine**: `dumpsys bluetooth_manager` on D-POCO, `Profile: HeadsetService`, `StateMachine for XX:XX:XX:XX:5E:1D` (D-SAM's masked MAC): `mCurrentState: Connected`, sampled during the hold. Round 2 could not reach this read at all.
2. **Gearhead's own verdict**, from D-POCO's own logcat: `WIRELESS_SETUP_SHARED_HFP_CONNECTING` (`22:15:15.291`) then `WIRELESS_SETUP_SHARED_HFP_CONNECTED` (`22:15:15.928`) — Gearhead's own connection logger independently confirms the accept. Immediately and repeatedly after: `GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit, will not start WPP on TCP.` (every ~5 s, unchanged for the rest of the window).
3. **Whether D-SAM is a car the phone knows**: not a plain yes/no. Gearhead's `CAR.BTCapsStore`: *"Checking device with address E4:58:E7:0E:5E:1D"* / *"This device previously has known UUID."*, and `GH.WifiBluetoothRcvr`: *"Bluetooth device XX:XX:XX:XX:5E:1D is previously known to have Android Auto UUID"* — Gearhead recognises D-SAM's Bluetooth capability, but has no stored WPP-over-TCP wireless-projection configuration entry for it. That missing entry, not the HFP profile, is what actually blocks wireless setup from continuing.

This round's read is strictly more informative than round 2's: the phone's own stack (both the AG state machine and Gearhead's own connection logger) now confirms the accept as a real hands-free connection, and the specific reason wireless setup does not continue past that point is now named precisely rather than left as "unknown."

### B1r and B4r

Not repeated; both PASSed in round 2.

## Stage C — D-HP, D-POCO (Headunit Server only)

### A1 — The unbounded audio queue, reported as a delay

**PASS**, on the brief's own stated PASS condition, with one discrepancy flagged.

- Settings written: `audio-queue-capacity=0` on D-HP for the first half, restored to `50` afterward and left there.
- A live Headunit Server session with D-POCO held for 12+ minutes total (one ~19 s drop from the AA dev server tool's own known self-disconnect flakiness — `WiFi read timeout (15000ms) - connection lost` at `22:21:50.590`, matching round 2's own Setup note about this exact tool — then stable for the remainder). Spotify played throughout, restarted with `input keyevent 126` whenever `dumpsys media_session` read anything other than `PLAYING`.
- `audio sink AUDIO over Nms: ... queued=N (Xms) ...` grew from `3 (21ms)` at `22:24:24` to `1103 (7721ms)` by `22:35:55` and `2512 (17584ms)` by `22:39:25`, underruns appearing (4, then 51) once the backlog passed several seconds. **The duration is directly readable from the log line itself**, with no chunk-count arithmetic needed — the brief's stated PASS condition.
- The one-shot warning line itself (`"Audio queue capacity is 0, so nothing sheds..."`) was not caught on this run: the host-side capture died silently during the window it must have fired in (see Setup notes), and the device's own ring buffer had already rotated past it. The threshold it fires at (`SinkQueueOverflowPolicy.UNBOUNDED_BACKLOG_WARN_MS = 2_000L`) was measurably crossed several times over, so the condition for it to have fired was clearly met; this is a capture gap, not a claim the line didn't print.
- With capacity restored to `50`: no `"Audio queue capacity is 0..."` line appears (correct — the code's own guard is `queueCapacityChunks > 0`), and the runaway growth stops. **But the queue settles at a persistent plateau of `142–143 (994–1001ms)`, not the "single digits" the brief's own text describes**, with active shedding (`dropped` climbing 0→3→598→802 over the observed minutes) and underruns still occasionally appearing (2, then 10 in one 30 s sample) — a genuine discrepancy from the brief's stated expectation, flagged here rather than assumed away.

The brief's stated PASS condition (delay readable from the log alone) is unambiguously met. Its separate claim that `capacity=50` settles to single digits is not what was measured here; worth a source check by whoever picks this up next, rather than assuming the setting's pre-regression behaviour still holds unchanged.

### L3r — WiFi comes back and discovery does not wait it out

**UNTESTABLE** — closed, not carried a third time, per the brief's own instruction.

- Run on D-SAM (API 19) per the brief's redirect, D-HP's Android 4.2.2 already ruled out in round 2.
- `svc wifi disable`, issued alone (not chained with `enable`): `dumpsys wifi` read *"Wi-Fi is enabled"* both immediately before and 2 s after. Reproduced the TESTING-TEMPLATE-documented `"try again in 1second"` ×8 followed by an unsolicited ~8 MB `dumpstate`-shaped dump on stdout — **worth noting this specific failure mode is not limited to chaining `disable`+`enable` in one call, as §7a currently reads**: only `disable` was issued here.
- `settings put global wifi_on 0`: WiFi still read *"enabled"* 2 s later.
- Neither lever takes either tablet's radio down. Per the brief, this run closes as unreachable.

### L4r — The Share button below Android 10

**UNTESTABLE** — Settings unreachable by any method tried, matching round 2's own conclusion.

- `run-as $PKG am start --user 0 -n .../SettingsActivity`: segfaults `am`, reproducing round 2 exactly.
- Plain `am start -n .../SettingsActivity` (no `run-as`): rejected, `SecurityException: ... not exported from uid 10071`.
- `run-as $PKG am start -n .../SettingsActivity` (no `--user 0`): same segfault as the first attempt.
- `monkey -p $PKG 1` (per the brief) brought the app to the foreground; a `uiautomator dump` located the Settings nav button at `[1003,259][1206,463]`. Tapping its centre, and separately the label's own text bounds, produced **no transition** (confirmed by a fresh `dumpsys activity activities` and a fresh dump: identical screen, identical bounds, both times).
- **New lead, not previously recorded**: the dump's own `<hierarchy rotation="1">` disagrees with `dumpsys window`'s live `mRotation=3` — a 180° mismatch. The corresponding mirrored coordinates for Settings were tried too; still no transition.
- This mismatch does **not** explain the failure by itself: the identical direct-bounds-from-dump method, at `[693,259][896,463]` (the WiFi button), correctly triggered `MainActivity.beginAutoConnect | Auto-connect: begin (manual WiFi headunit server scan, mode=OVERLAY)` — proving touch delivery and this coordinate space both work on this unit, and narrowing the miss to something specific to the Settings button rather than a systemic input problem.
- Given touch delivery is confirmed working and two independent coordinate hypotheses for Settings both failed, this is reported as unreachable rather than pursued further by script. No operator hand was available mid-round for the brief's by-hand exception; a single physical tap on the Settings icon, then Share, would settle this in one step for whoever can do it next.

### L1r, L2, L5r

Not repeated. L1 and L5 PASSed in round 2; L2 was dropped by the brief as structurally unreachable on D-HP.

## Anything the brief did not ask about

- D-HU's generic auto-poke loop pokes every bonded phone (D-MOTO and D-POCO both) regardless of `auto-start-bt-macs`, which appears to gate something else (most likely which MAC auto-launches the app on `ACL_CONNECTED`) rather than which devices the generic poke loop itself walks. Worth a source check if a future round relies on `auto-start-bt-macs` to scope the poke loop.
- D-HP's host-side logcat capture died silently twice during otherwise-uneventful long runs (A1). Worth remembering as a known capture risk on this rig going forward, distinct from the app-side session itself, which stayed up through both gaps (confirmed by an unchanged pid and no further disconnect lines).
- D-SAM's `wifi-direct-group-name-changes` read `4` before this round touched it, left over from an earlier, unreported session on this unit.
