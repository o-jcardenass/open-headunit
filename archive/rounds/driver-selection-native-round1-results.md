# driver-selection-native — round 1 results

**Candidate:** `origin/driver-change-native-mode` @ `d103ce7a`   **Baseline:** `origin/main` @ `ce2897c4`
**APK md5:** candidate `1e847fba6a7269acd9f57bba083e53fd` / baseline `03f3ae8a7a40bb82d32d691bd418c449`
**Unit:** D-HU (MT50, `MT50_YT610E4GFPSL_U`), Android 14, 1440×720, D-POCO (POCO X3 NFC) + D-MOTO (motorola edge 30 neo) both bonded for R1–R6/R8, D-MOTO unpaired for R7 and re-paired after.
**Date:** 2026-09-04

## Setup notes

- **R0**: both APKs built and unit-tested on-rig (not just off-rig). Candidate **1276/0**, baseline **1259/0** — matches the brief exactly. DEX symbol check: `NativeDriverSelectionPolicy` count candidate **24**, baseline **0**. md5s differ. All PASS.
- **Install blocker, escalated and resolved with operator approval**: the rig's installed build was signed with the release key (`CN=André Rinas, ...`), not the debug keystore these `assembleGithubDebug` builds carry, so `adb install -r` refused with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. This is outside R0's stated "adb install -r only", so it was escalated rather than resolved unilaterally. With operator go-ahead: `adb uninstall` + fresh install, `settings.xml` restored from a pre-uninstall backup (verified byte-identical after), and the original release-signed APK was reinstalled at the end of the round with the same restore-and-verify step. No round content depends on this deviation — it only affected getting the candidate/baseline debug builds onto the rig.
- **`hur-wifi-test-scripts/` inventory**: used `build_hur.sh` + `run_unit_tests.sh` for R0, `set_hu_settings_host.py` (scalar/set/delete edits) and `set_hu_prefs.sh` (scalar-only, single force-stop) for every settings write. No new script needed.
- **Real errata found, not in the brief**: §3's `auto-start-bt-macs` trap is bigger than described. The brief says the key is read from the device-protected mirror as connect history; what actually happened is the **candidate's own `settings.xml` copy of `auto-start-bt-macs`** (a leftover from an earlier, unrelated round — `DC:B7:2E:5E:4E:59` / D-POCO) is what the app resyncs into the device-protected mirror **on every launch**. Clearing only the mirror (as §3 describes) is not durable: the very next launch silently repopulates it from `settings.xml`. This cost two fully-run, fully-contaminated R1 attempts (both auto-connected to D-POCO at the configured timeout via `resolveAutoConnectTarget`'s history branch, with a real `Driver selected:` line each time) before the mismatch was found. **Any future round on this thread must clear `auto-start-bt-macs` in `settings.xml` itself, not just the mirror**, whenever a run needs "no history."
- **R2 and R8 both hit the same wall**: on this rig, once a phone's radios are brought back from off, it does not reliably re-establish a Bluetooth profile connection to this head unit specifically (it will happily reconnect to some other bonded accessory instead, e.g. D-POCO reconnected to a paired speaker in the R2 attempt). Three different scripted levers were tried across the two runs — plain radio enable, a full phone-BT off/on cycle, and explicitly launching Gearhead's `CAR_PROJECTION` `SetupActivity` — and none produced a BT profile connection to the head unit (`mCurrentDevice: null` throughout, both sides, every attempt). Recorded INCONCLUSIVE on both runs rather than invented.
- **R7's unpair/re-pair used the system Bluetooth Settings UI** (Saved devices → gear → Forget, then Pair new device), the one sanctioned touch-driven exception — there is no scriptable unpair on this rig. Re-pairing needed the operator's involvement both times (a numeric-comparison-style confirmation that adb cannot drive blind); the round was paused twice to ask for it, once at the very start (D-MOTO was never bonded to D-HU before this round) and once at the end to restore D-MOTO's bond.
- Every settings write was read back before use; the two string keys were always cleared to an empty element, never deleted, per §1/§4.

## R0 — build gate

**PASS**

- md5s differ (above); unit gate candidate 1276/0, baseline 1259/0 (both measured on-rig, matching the brief's off-rig numbers exactly).
- `NativeDriverSelectionPolicy` DEX-symbol count: candidate 24, baseline 0.

## R1 — two bonded phones, nobody touches the screen — the point of the round

**FAIL (candidate)**

- Settings: mode `1`, timeout `30`, both MAC strings empty, `native-poke-bt-macs` empty, `auto-start-bt-macs` empty in **both** `settings.xml` and the device-protected mirror (see Setup notes — this took two discarded attempts to get right).
- Radio state: D-POCO and D-MOTO both `svc bluetooth disable` + `svc wifi disable`, verified `state: OFF` / `Wi-Fi is disabled` on both immediately before launch.
- Discard-rule check: this run clean (3 `createGroup SUCCESS` across the 120 s — group refresh churn while idle, not a second session; no second SSL handshake, no `MATCH!`, no Magic Garbage). Two earlier attempts discarded for the `auto-start-bt-macs` contamination in Setup notes.
- Decisive log lines (candidate, `evidence/driver-selection-native-round1/r1_candidate_decisive.txt`):
  ```
  NativeAA: Multi-driver selection is active and awaiting user choice — deferring automated multi-device poke loop.   (×9 over 120 s)
  ```
  No `Attempting active poke to device`, no `Driver selected:`, no `ACTION_NATIVE_AA_POKE` anywhere in the capture.
- Screenshot at t+120 s (`r1_candidate_final_dialog_no_resolve.png`): dialog still open, both phones listed as "Paired" (not connected), **no countdown text or progress bar** — exactly "no target resolves, the countdown is hidden" as §3 describes for this setup.
- Baseline, same setup (`r1_baseline_decisive.txt`): `Attempting active poke to device` × **5** over 120 s, first at **2.264 s** after `createGroup SUCCESS` (10:48:59.100 → 10:49:01.364), alternating D-MOTO/D-POCO.

**Numbers for §8: candidate poke count = 0, baseline poke count = 5. The deferring line fired 9 times on the candidate. Candidate poke count is 0 while baseline's is not — an unambiguous FAIL by the brief's own condition.** A unit that starts with nobody watching never wakes either phone at all, where `main` pokes within 3 seconds and keeps going.

## R2 — dialog dismissed by backgrounding the app

**INCONCLUSIVE**

- Candidate, same settings as R1. Dialog confirmed shown (`ACTION_NATIVE_AA_PROMPT_SHOWN` present), then `KEYCODE_HOME`. D-POCO's radios restored and Gearhead's `SetupActivity` (`CAR_PROJECTION` category) explicitly launched to force an attempt, since plain radio-enable did not by itself make it try the head unit (see Setup notes).
- Over roughly 7 minutes and three different scripted triggers, D-POCO never opened an RFCOMM connection to D-HU's still-listening Native AA UUID at all: no `Selection prompt active (target=null) — refusing connection from` (the candidate-specific refusal line), and no `SSL handshake complete`. The credentials-update / deferring cycle on the head unit side ran the whole time, confirming the listeners were live and simply never dialed.
- Not recorded as FAIL because the brief's FAIL condition (the refusal line appearing) never had a chance to fire — the phone-side half of the setup could not be produced on this rig. Evidence: `evidence/driver-selection-native-round1/r2_candidate_decisive.txt`.

## R3 — dialog cancelled with Back

**FAIL (candidate)**

- Settings as R1 except `auto-start-bt-macs` (device-protected mirror) = D-MOTO's MAC, written directly per §3.
- Dialog shown, `KEYCODE_BACK` pressed ~6 s in (well inside the 10 s window); `cancelPoke()` / `ACTION_NATIVE_AA_CANCEL_POKE` logged immediately.
- D-MOTO's Bluetooth cycled off/on. The app was never force-stopped between cancel and the cycle, so its RFCOMM listeners stayed open the whole time — D-MOTO reconnected directly to them and was refused **20 times** in **1.4 seconds** (11:01:16.238 → 11:01:23.043):
  ```
  NativeAA: User explicitly canceled driver selection — refusing connection from A0:46:5A:97:E4:95
  ```
  `MATCH! Starting AapService` = 0 (expected — the service was never killed, so `AutoStartReceiver`'s cold-start path was never exercised; this is a genuine live-reconnect refusal, not a missing cold-start line). No poke followed the auto-start reconnect at all — the phone reached us and was turned away every time.
- **Recovery**: pressing the home screen's WiFi button (a real tap — no scriptable trigger exists for it) recovered the unit immediately: `HomeFragment.connectToNativeDevice` → `Driver selected: A0:46:5A:97:E4:95` → `SSL handshake complete` at 11:02:14.000, **14 seconds** after the tap. `Driver selected:` is what showed, **not** a fresh `createGroup SUCCESS` — the WiFi Direct group survived the whole cancel-and-recover cycle.

**Numbers for §8: the unit never accepted D-MOTO again without a touch — Cancel is exactly as sticky as the brief predicted, confirmed by 20 consecutive live refusals of a phone that actually reached the listener. The only lever that brought it back was the WiFi button (a touch), which is scriptable only as a tap/coordinate, not as a deep link or action.**

## R4 — happy path, then Switch Driver

**PASS** (both halves)

- `native-preferred-device-mac` = D-MOTO's MAC, timeout `10`, D-MOTO's radios on. Launch, no touch.
- First half — **PASS, no touch anywhere in the capture**: `Connecting to Native-AA device: motorola edge 30 neo` → `Driver selected` → `createGroup SUCCESS` → `SSL handshake complete` at 11:03:32.231, all inside the 10 s window with no input sent.
- Second half: `KEYCODE_BACK` opens the exit dialog (scriptable — `onBackPressedDispatcher`, no coordinate tap needed), row is labelled **"Switch Phone"** in this build (not "Switch Driver" — string differs from the brief's shorthand, same feature). Tapped it, then tapped D-POCO in the resulting "Select Driver Phone" selector (D-MOTO shown as "★ Preferred" / "🟢 In vehicle / Connected", confirming the connected-state read works correctly for a genuinely live session).
- **PASS**: `User requested switch driver` (11:04:41.449) → `AapTransport stopping and sending byebye (DEVICE_SWITCH)` → selector appeared → `Driver selected: DC:B7:2E:5E:4E:59` → `SSL handshake complete` (11:05:28.347).

**Numbers for §8: `createGroup SUCCESS` count across the whole capture = 2, on two different P2P interface indices (`p2p-wlan0-27`, `p2p-wlan0-28`). Switch duration (`User requested switch driver` → the second `SSL handshake complete`) = 46.9 seconds.** The branch's own description says the group and credentials survive a switch — on this hardware they do **not**: the group was torn down and a new one created, not preserved.

## R5 — Switch Driver with "Close app on disconnect" on

**FAIL**

- R4's setup plus `kill-on-disconnect=true`. Got a live session with D-MOTO (`Driver selected` 11:06:17.113, `SSL handshake complete` 11:06:26.846), then `KEYCODE_BACK` → tapped "Switch Phone" again.
- `User requested switch driver` (11:07:11.102) → `AapService.onDestroy | AapService destroying...` **0.643 seconds later** (11:07:11.745), with **no selector** shown and `pidof com.andrerinas.headunitrevived` returning nothing afterward — the whole process is gone. Screenshot (`r5_app_closed_launcher.png`) shows the bare OS launcher with an "Open Headunit" relaunch tile, confirming the app, not just the projection Activity, exited.

Matches the brief's FAIL condition exactly: the app closes instead of presenting a driver selector.

## R6 — mode `Off`, two bonded phones, the WiFi button — regression guard

**PASS (candidate did not do nothing)**

- `native-driver-selection-mode=0`, both MAC strings empty, both phones bonded and radios off. Launched, then the WiFi button tapped once (no scriptable trigger for it — the `headunit://connect` deep link is USB-only and a no-op under Native AA, confirmed in `TESTING-TEMPLATE.md`).
- Method limitation, noted honestly: the pre-existing, mode-independent automatic poke loop (unaffected by `Off` — it only checks `mode != DISABLED` in the *new* early-return, not in the old loop at all) was already mid-cycle at the moment of the tap on both the candidate and baseline runs, so a line freshly and cleanly attributable to the tap alone (vs. the loop's own ~30 s cadence) could not be isolated with the timing used. This is flagged rather than glossed over.
- What **was** cleanly observed: a Toast appeared immediately after the tap (the `DISABLED`-mode fallback path — `searching_phone` + an `ACTION_NATIVE_AA_POKE` intent with no specific MAC since two candidates exist), and active poke attempts (`Attempting active poke to device`, alternating D-MOTO/D-POCO, HFP then HSP) continued uninterrupted through and after the press. The candidate never went dead silent.
- Baseline, same setup: the WiFi button opened the **old "Select Bluetooth Device" plain list** (D-MOTO / D-POCO, `r6_baseline_bt_device_list.png`) — structurally different from the candidate's Toast-and-poke behaviour, exactly the asymmetry the brief predicted ("the baseline opens a device list here rather than poking").

**What must not happen** (per the brief) is the candidate doing nothing at all. It did not: it showed a Toast and kept poking. Recorded PASS on that basis, with the tap-attribution caveat above.

## R7 — one bonded phone only — regression guard, run last

**PASS**

- D-MOTO unpaired via the system Bluetooth Settings UI (Forget device — the one sanctioned touch-driven step, no scriptable unpair exists). Confirmed only D-POCO bonded afterward.
- Mode `1`, both MAC strings empty. Ordinary clean-run protocol (§4): head unit launched first, D-POCO's radios restored 18 s later, up to 90 s allowed.
- Candidate: no dialog, `deferring` count = **0**. Launch 11:15:19 → `SSL handshake complete` 11:15:55.283 = **36.3 s**.
- Baseline: no dialog (not applicable to baseline), no `deferring` line (candidate-only string, confirmed absent). Launch 11:17:32 → `SSL handshake complete` 11:18:08.193 = **36.2 s**.
- D-MOTO re-paired afterward via the same UI, with the operator's help for the confirmation step; confirmed both phones bonded again before closing the round.

**Numbers for §8: launch-to-`SSL handshake complete` = candidate 36.3 s, baseline 36.2 s — within 0.1 s of each other.** This is the majority configuration in the field, and it is unaffected by the branch.

## R8 — is a bonded phone ever seen as connected?

**INCONCLUSIVE**

- Both phones bonded, mode `1`, both MAC strings empty. Attempted to get D-POCO Bluetooth-connected to D-HU before launching the app, per the brief.
- Same wall as R2 (see Setup notes): plain radio enable, cycling the head unit's own Bluetooth adapter (the documented working lever for other purposes on this rig), and explicitly bringing Gearhead's `SetupActivity` to the foreground were all tried, none produced a BT profile connection between D-POCO and D-HU (`mCurrentDevice: null` on both sides throughout, every attempt) — so the app was never launched into the state R8 needs, and neither `Unambiguous driver ... auto-connecting directly without prompt` nor the dialog could be meaningfully observed as an answer to this run's question.
- This is not a null result for the underlying question, though: R4's screenshot (`r4_switch_selector_moto_connected.png`) independently shows the "🟢 In vehicle / Connected" badge rendering correctly for D-MOTO during a *genuinely live* session, so the connected-state read itself works when there is a real connection to read — the open question this run leaves is specifically whether a bonded phone can be gotten into that state via Bluetooth alone (without an active Native AA session) on this hardware, and that remains unanswered.

## Anything the brief did not ask about

- The `auto-start-bt-macs` settings.xml-vs-mirror resync (Setup notes) is worth fixing or at least documenting for every future round on this rig, not just this thread — it will bite the next round that assumes clearing the device-protected mirror alone is sufficient.
- R4's screenshot is the first hardware confirmation seen on this rig that the "🟢 In vehicle / Connected" driver-dialog badge reflects a real, live BT+session state correctly, not just settings-derived history — useful context given R1/R2/R8's difficulties producing that state from Bluetooth alone.
- The rig's installed app was release-signed at the start of this round (see Setup notes) — worth a note for whoever runs the next round on this rig, since it means `adb install -r` will fail again from a cold start unless a debug build is already installed.
