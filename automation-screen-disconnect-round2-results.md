# automation-screen-disconnect, round 2 results

**Candidate:** fork/feat/automation-screen-disconnect @ 8db52cebe6bfc103f645c656a201d56bd3a3a963 (tree c5c46cdaaf8c96979bbd78e745aa113d93ffad3d)       **Baseline:** none (feature absent on main)
**APK md5:** C = fc8132d85fe9e2fbf0ac4c300902c03e (`apk/C.apk`)
**Unit:** D-POCO (POCO X3 NFC, API 35, LineageOS, Gearhead 18.0.664204) as head unit, D-MOTO (Gearhead 18.0.664204) as phone; D-SAM (SM-T230, API 19) as head unit, D-POCO as phone. D-HU ran nothing; only its Bluetooth was held off per session and switched back on in X.
**Date:** 2026-10-17

## Verdicts

| Run | Verdict | Lever |
|---|---|---|
| R0 | PASS (build and identity) | n/a |
| L-MOTO | record: MOTO_FAKE=yes | fake |
| L-POCO | record: LEVER_POCO=fake | fake |
| V1 | **PASS** (rerun) | n/a |
| R3 | **PASS** | fake |
| R4 | **PASS** | fake |
| R1 | **PASS** | fake |
| R2 | **PASS** | fake |
| R6 | **PASS** | fake |
| R10 | **PASS** | fake |
| T1 | **PASS** (condition 6 check defective, see run) | cable |
| V3 | **FAIL** (condition 3) | fake |
| V4 | **FAIL** (condition 3) | fake |
| R5 | **PASS** | fake |
| R7 | **FAIL** (condition 6; 4 and 5 INCONCLUSIVE) | fake |
| L-SAM | record: LEVER_SAM=cable | fake failed |
| S1 | **PASS** | n/a |
| R1s | **UNTESTABLE** | cable (brief rule) |
| S2 | **PASS** | cable |

## Setup notes

- **Transfer branch checkout:** `git checkout transfer/rig-rounds` was refused because worktree `rig-sync__scratch__8228cb07__20261015` holds the branch (clean, at the fork tip). Brief, template and quirks were read from it; the main checkout stayed on `main`. `rig_sync.py sync`: in sync, no restart.
- **Quirk files read:** topics/tooling.md; units/D-POCO.md, D-SAM.md, D-MOTO.md, D-HU.md, D-SAM-and-D-HP.md. No topic files.
- **Opening pre-flight** (`rig_preflight.sh D_HU/D_POCO/D_MOTO/D_SAM :wifi,bt`):

  | Role | adb | WiFi | BT | Screen | Profiles |
  |---|---|---|---|---|---|
  | D_HU | ok | 1 | 1 | Awake | HFP none, A2DP none |
  | D_POCO | ok | 1 | 1 | Awake | HFP connected, A2DP up |
  | D_MOTO | ok | 1 | **0** | Awake | none |
  | D_SAM | ok | 1 | 1 | Asleep | none |

  FAIL on D_MOTO Bluetooth. The operator said "Go", read as a waiver because `phone_on` in `bringup` enables the phone's radios per run. D_MOTO ends with WiFi on, Bluetooth off, as it began.
- **Operator approvals:** `screen_off_timeout` writes (D-SAM 600000 to 1800000 in P; D-POCO 15000 during T1). X restored D-POCO 1800000 and D-SAM 600000 (read back).
- **R0:** md5 above. Symbol counts in the DEX: ExternalPowerDisconnectPolicy 3, ScreenOffAdminReceiver 4, ScreenOnRaisePolicy 3, `external-power disconnect` 13. `install -r` on D-POCO and D-SAM; `settings.xml` pre and post identical on both. `ACTION_QUERY_STATE` on both: commit `8db52cebe6bf`, versionCode 118. Build: `build_hur_cool.sh`, `MAX_WORKERS=2`, host 58C before.
- **P:** SECURE_POCO=no (`Lock credential verified successfully`). TIMEOUT_POCO=1800000, TIMEOUT_SAM=600000. `stay_on_while_plugged_in` 0. TURN_SCREEN_ON appop default. admin 0 and 0. Bonds present: D-MOTO (MOTO_MAC=A0:46:5A:97:E4:95 from D-MOTO's own `bluetooth_address`; the bond list masks it), Navegadortz3. POCO_MAC=DC:B7:2E:5E:4E:59. D-HU battery all false level 0. D-POCO and D-SAM read AC true, USB false, level 100. D-SAM clock 20 s behind the host. Audio keys only in D-SAM's backup (enable-audio-sink true, use-aac-audio false, queue 50, latency 16).
- **Scripts:** brief helpers extracted to `ascd2_lib.sh` and `sx.py` unchanged; `ptr_lib.sh` copied from round 1. One-round runner `scripts/runlocked.sh` and per-run scripts in `scripts/`. The host ran every script itself under `flock /tmp/ohu-rig.lock`; no `rig-executor` was used.
- **Lock trap (brief helper defect):** `th_gate` starts a `th_watch` subshell that outlives the script and keeps the lock fd, so the next `flock -n` failed (V1, R3 each lost one start with exit 1 and no device action). Fixed in `runlocked.sh` (kill the watcher and its `sleep`, `flock -w 40`).
- **V1 rerun:** attempt 1 (kept in `attempt1/`) read `Dozing` before the refused verb with no `SCREEN_OFF` event in the window, so condition 1 was unassessable. Rerun added `panel_on; wake` after the launch.
- **V4 rerun:** attempt 1 (kept in `attempt1/`) was void: after V3's `lockNow()` the keyguard stayed up and the session formed under it (focus NotificationShade, `rend` 0 before the cut). Rerun, and R5/R7, added `panel_on; wm dismiss-keyguard` (no credential on the unit) before `bringup`. This is not a brief step.
- **HS2:** first launch lost the `$` of `Settings$DeviceAdminSettingsActivity` on the device shell and opened plain Settings; admin stayed 1 after 120 s. Relaunched with `\$`; the operator deactivated the admin, `admin_after=0`. A first monitor of mine re-emitted the cue many times (filter bug), harmless.
- **Thermal:** peaks 78C (V4 attempt 1), 75C (R5), 73C (L-SAM), 69C or lower elsewhere; `throttle_delta=0` in every run.
- **T1 timing:** cue 18:36:21, the operator pulled at 18:40:47 (the 120 s delay counts from the actual loss), plugged at 18:43:33.

## L-MOTO. The fake lever on D-MOTO

**RECORD: MOTO_FAKE=yes**

`d0=1 d1=6 c0=6 c1=13` (POWER_DISCONNECTED and POWER_CONNECTED entries in `dumpsys activity broadcasts history`). After `battery reset`: AC powered true, USB powered false, as in P.

## L-POCO

**RECORD: LEVER_POCO=fake**

`FAKE_OK`: 1 `WakeDetect: POWER_DISCONNECTED` (18:12:43.081) and 1 `WakeDetect: POWER_CONNECTED` (18:12:48.468). Wakefulness: `w0` Dozing, `w-unplug` Awake, `w1` Dozing, `w-plug` Awake, so `ROM_WAKES_ON_UNPLUG=yes`, `ROM_WAKES_ON_PLUG=yes`. `external-power disconnect` count 0.

## V1. The screen verbs before the grant

**PASS** (rerun)

- Settings: `$BASE $A0 $EP_OFF` for V1a, `$A1` for V1b. Fake lever, battery plugged.
- V1a-off reply: `"error":"external configuration is off; turn on \"Allow external configuration\" in Settings","ok":false`; `w-pre` Awake, `w-off` Awake. `w-dark` Dozing.
- V1a-on: one line `AapService: turning the screen on (automation) by wake lock` (18:16:33); gap to `WakeDetect: SCREEN_ON (screen was off for 5s)` 58 ms; `w-on` Awake. `PowerManagerService: Allowing device wake-up without android.permission.TURN_SCREEN_ON for com.andrerinas.headunitrevived`. `V1a.focus` NotificationShade, `V1a.kg` isKeyguardShowing=true.
- V1b-off reply: `no device admin grant; allow it under Settings > Auto-start`, `"ok":false`; `w-off` Awake. `screen-on launch failed` 0 in both.

## R3. Power lost with no session

**PASS**

Fake lever. `POWER_DISCONNECTED` 1, `POWER_CONNECTED` 1 in W2. A3 line `power lost, but nothing is projecting; leaving the connection stack alone.` 2 ms after `POWER_DISCONNECTED`. A1, A6 and `keeping the wireless bring-up down` 0 in W1. W2: `external-power disconnect` 0, screen-on 0. Pokes: 3 before the cut, 2 in W1. `MATCH! Starting AapService` 0.

## R4. Switch off

**PASS**

Fake lever. W (cut to `R4-x-go`): `POWER_DISCONNECTED` 1, `POWER_CONNECTED` 1, `external-power disconnect` 0, `since connection is disconnected` 0, `rend` 7 in cut to back. Phone `received ByeByeRequest` 0 in the window (1 in the reference exit window, so the phone capture works). Validity: `rend` 6, MATCH 0, `wrapped=no`, `ssl=0`.

## R1. The point of the round

**PASS**

Fake lever, one chain with R2 and R10 (one capture, `wrapped=no`, `ssl=0`, `ssl2=0`).

- A1 at 18:24:10.610, `power lost; ending the session in 20000ms unless it comes back.`, 1 ms after `POWER_DISCONNECTED`.
- A1 to A6 gap **20009 ms**. A6 to ByeBye gap **22 ms**, reason `USER_SELECTION` on both the line and the reference exit.
- `since connection is disconnected`: 0 before A6, 1 after. Stand-down `keeping the wireless bring-up down until something re-arms it.` 1; `power came back while the session was ending` 0.
- Quiet window (A6 to `POWER_CONNECTED`) **140333 ms**; in `W2` (15 s after A6 onward): `createGroup SUCCESS` 0, `Attempting active poke` 0, `MATCH! Starting AapService` 0, `SSL handshake complete` 0.
- Phone `received ByeByeRequest` 1. Screen untouched: `: screen off ` 0, `screen not turned off` 0, `R1.wake` mWakefulness=Awake.
- Return: `POWER_CONNECTED` to `power is back; re-arming the wireless bring-up.` 0 ms; screen-on, A2 and `checking USB again` 0. New session: `ssl2=0`, `rend` 3, POWER_CONNECTED to SSL **17087 ms**. `the session end never reached the stand-down` 0.

## R2. Power back inside the delay, and a flap

**PASS**

Fake lever, chain. Cancel: A1 1, A2 1 (2 ms after `POWER_CONNECTED`), loss to back 8342 ms (limit 18000), A6 0, `not ending` 0, `rend` 6, phone ByeBye 0. Flap: A1 2, A2 1, A6 1, `not ending` 0, last A1 to A6 **20007 ms**. Return: re-arm 1, `ssl2=0`, `rend` 2.

## R6. Screen row on, no grant

**PASS**

Fake lever. A1 1, `screen not turned off, no device admin grant` 1, A6 1 (gap 0 ms), `keeping the wireless bring-up down` 1, `: screen off ` 0, `WakeDetect: SCREEN_OFF` 0, `R6.wake` Awake, phone ByeBye 1; return: re-arm 1, screen-on 0, `ssl2=0`, `rend` 2.

## R10. The service stops inside the delay

**PASS**

Fake lever, chain. A1 1 before the exit, `AapService destroying...` 1 between exit and re-arm, A6 0, `not ending` 0, `external-power disconnect` 0 after the exit. `ssl2=0`, `rend` 8, no disconnect after the new SSL. `R10-cut` to `R10-hold` **71.6 s**.

## T1. The timer while the unit sleeps

**PASS** (condition 6 check defective)

Cable lever. `T1.battery0` AC true. Validity: `POWER_DISCONNECTED` 1 (18:40:47.662) and `WakeDetect: SCREEN_OFF` 1 (18:41:03.448), **15786 ms** apart. A1 delay **120000 ms**. A1 to A6 **120012 ms** (A6 at 18:42:47.676). `could not hold the CPU awake` 0, `never reached the stand-down` 0, `power lost beside a screen-off` 0. A6 present, phone `received ByeByeRequest` 1. `T1.wake` Awake.

- `suspended=unknown`: `/sys/power/suspend_stats/success` is Permission denied from the shell on both reads.
- **Condition 6 as written fails, and the check is defective.** `T1.lock` read 6 because `dumpsys power | grep -c ExternalPowerDisconnect` counts wake-lock history lines (`ACQ`/`REL` events of R6 and T1), which can never be 0 after any use. The lock was released at 18:42:47.687, 11 ms after the A6 line, and the live "Wake Locks" section lists no OpenHeadunit lock. Graded on that; the operator may regrade it FAIL on the literal text.

## V3. The screen verbs with the grant, no session

**FAIL**

Fake lever, grant held (`admin` 1). Conditions 1, 2, 4 met: reply `"screenOff":true`,`"ok":true`; `w-off` Dozing; 1 `WakeDetect: SCREEN_OFF`; `power lost beside a screen-off` 0; `screen-on launch failed` 0; `w-on` Awake. **Condition 3 not met**: the screen-on line is `AapService: turning the screen on (automation) by wake lock` with no `and the home screen`; gap to `SCREEN_ON` 70 ms. `V3.kg-off` and `kg-on` both isKeyguardShowing=true, `V3.focus` NotificationShade. `PowerManagerService: Allowing device wake-up without android.permission.TURN_SCREEN_ON`.

Cause, read in `AapService.turnScreenOn` at this SHA: `ScreenPower.wakeBriefly` runs before `ScreenOnRaisePolicy.decide`. Here the wake lock alone lights the panel (`Waking up from Dozing ... reason=WAKE_REASON_APPLICATION, details=HeadunitRevived::ExternalPowerWake`), `isInteractive` is then true, and the policy returns `NONE`. The raise path was never reached on this ROM, so INCONCLUSIVE is arguable; graded FAIL because a stated condition failed.

## V4. The screen verbs during a session

**FAIL**

Fake lever, grant held, rerun (see Setup notes). Valid: `rend` 6, MATCH 0, `wrapped=no`, `ssl=0`, `V4.kg-pre` isKeyguardShowing=false. Condition 1 met (`"screenOff":true`, `w-off` Dozing, `w-on` Awake), condition 2 met (0). The session survived the dark panel (0 disconnects between off and on), so condition 3 applies and is **not met**: the line is `(automation) by wake lock` with no `the projection`, `V4.focus` is NotificationShade not `AapProjectionActivity`, and `rend` after screen-on is 0. Phone ByeBye 0. Same cause as V3, plus the keyguard left by `lockNow()` covers the projection.

## R5. Screen-off after the end, at the minimum delay

**PASS**

Fake lever. `R5.kg-pre` isKeyguardShowing=false. A1 delay **5000 ms** (the 2 clamped), A1 to A6 **5007 ms**, `keeping the wireless bring-up down` 1, `: screen off ... done.` 1, `refused by the system.` 0. `WakeDetect: SCREEN_OFF` 1, **5881 ms** after `POWER_DISCONNECTED`. `power lost beside a screen-off` 0. `R5.wake` Dozing, `R5.kg` isKeyguardShowing=true. Phone ByeBye 1. Validity: `rend` 6, MATCH 0.

## R7. Lit again on power back

**FAIL**

- Met: `POWER_CONNECTED` 1; one screen-on line `(external power back) by wake lock`, **15 ms** after `POWER_CONNECTED`; re-arm 1, **1 ms** after the screen-on line; `screen-on launch failed` 0; `ssl2=0`.
- **Condition 6 not met**: `rend` is 0 after power back (0 `Throughput over` lines in the window). `R7.focus` NotificationShade, `R7.kg` isKeyguardShowing=true, `R7.wake` Awake.
- Conditions 4 (SCREEN_ON line 196 ms after the screen-on line) and 5 (`R7.wake` Awake) are **INCONCLUSIVE**: `ROM_WAKES_ON_PLUG=yes`. POWER_CONNECTED to `SCREEN_ON` 211 ms.
- `re-arming the wireless bring-up held while the unit was asleep` 0, `hibernate wake detected` 0. `device wake-up` line: `Allowing device wake-up without android.permission.TURN_SCREEN_ON for com.andrerinas.headunitrevived`.

A new session forms but nothing renders until the user clears the lock screen, because the screen-on never raised the projection.

## L-SAM

**RECORD: LEVER_SAM=cable**

`FAKE_OK` not met: `WakeDetect: POWER_DISCONNECTED` 0 and `POWER_CONNECTED` 0, although the app was running (markers `L-SAM-cut`, `back`, `panel`, `end` logged; wake receiver registered with 33 actions; `SCREEN_OFF`, `USER_PRESENT`, `SCREEN_ON` seen). All four wake readings Asleep, so the ROM readings are `unknown`. `external-power disconnect` count 0.

## S1. Screen-on below API 33

**PASS**

`w-dark` Asleep after 8 s. One line `AapService: turning the screen on (automation) by wake lock` (no `) by wake lock and `). `w-on` Awake. `screen-on launch failed` 0.

## R1s. R1 on API 19

**UNTESTABLE**

`LEVER_SAM=cable`; the brief rules R1s UNTESTABLE on the cable lever.

## S2. A real cable pull with no session

**PASS**

Cable lever on D-SAM. `POWER_DISCONNECTED` 19:02:25.137 and `POWER_CONNECTED` 19:03:43.864, 78727 ms apart. A3 line 10 ms after the loss. `external-power disconnect` count exactly 1. `S2.battery` AC true, USB false, same as P. `wrapped=no`. The app saw the loss 17 s before adb reported the unit gone (19:02:42).

## X. Clean-up

Batteries reset (all three AC true, USB false). Timeouts: D-POCO 1800000, D-SAM 600000. `admin` 0 and 0. D-HU Bluetooth enabled (read back `enabled: true`). `settings.xml` restored by `put` with no keys: D-POCO identical to its backup; D-SAM identical apart from line endings (API 19 `run-as cat` returns `\r\r\n`, 438 CR against 146 in the backup). D-MOTO airplane off, WiFi on, Bluetooth off (opening state).

## Anything the brief did not ask about

- **The screen-on raise never fires where the wake lock can light the panel.** On D-POCO (API 35, LineageOS) `turnScreenOn` wakes the panel first, so `ScreenOnRaisePolicy` returns `NONE`. Three runs (V3, V4, R7) depend on the raise and fail on it. After the feature's own `lockNow()` the keyguard stays up and covers the projection; R7 shows a session forming with zero frames rendered. Worth a code-side look at the order of `wakeBriefly` and `decide`.
- **Brief defects:** T1 condition 6 counts wake-lock history; the HS2 activity name needs `\$` on the device shell; `th_gate` leaves a watcher that holds the rig lock; V1 and V4/R5/R7 need a lit, unlocked panel at the start, which the scripts do not guarantee (D-POCO dozes between runs).
- **D-POCO dozes by itself between runs** with `screen_off_timeout` 1800000 (the keyguard uses a shorter timeout); `am start` does not wake it.
- **D-SAM:** the fake battery lever sends no `POWER_*` broadcast on API 19, while a real pull does and the app logs it before adb notices.
- **Gearhead 18.0.664204** on both phones; the brief names 17.5 and 17.8 for `received ByeByeRequest`. The string was present in every window where a ByeBye was expected (R1, R5, R6, T1), so it holds on 18.0.
- Quirk files to update at publish: `rig-quirks/units/D-POCO.md` (fake lever works, ROM wakes on both plug changes, `suspend_stats` unreadable from the shell, keyguard behaviour after `lockNow`) and `rig-quirks/units/D-SAM.md` (fake lever sends nothing, real pull does, `run-as cat` CR doubling).
- Evidence: `rig-evidence-automation-screen-disconnect`, asset `automation-screen-disconnect-round2-captures.zip`, sha256 `10b4ea16dd76612574e7f781c99ba7d6c1cc702c5645f454ba0f162caa71ff7a` (16572981 bytes).
