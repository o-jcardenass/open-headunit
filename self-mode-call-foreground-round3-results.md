# self-mode-call-foreground, round 3 results: the raise works, on every call that actually covers it

**Candidate:** `fork/fix/883-self-mode-call-raise` @ `4d8679e7`       **Baseline:** `origin/main` @ `ea7aa7e0`, R4 only
**APK md5:** candidate `621b3072ec0e570be5a499b67845c742` / baseline `b120faa29ce718002e59126c84328256`
**Unit:** Redmi M2007J20CG / POCO X3 NFC (`surya_eea`), `ro.build.version.sdk=35`, `ro.build.version.release=15`. Android Auto `17.5.663204-release`.
**Date:** 2026-08-25

## Setup notes

- `hur-wifi-test-scripts/` inventory: used `build_hur.sh` (candidate build, then re-run for the R4 baseline
  build) and `run_unit_tests.sh` unchanged. Did **not** invoke the existing `set_pref.sh` by name for the
  `raise-projection-during-call` write/clear — re-derived the identical push-script-and-run-as pattern ad
  hoc instead. Functionally identical (confirmed by reading the existing script afterward), but the next
  round in this thread should just call `set_pref.sh` directly.
- **`build_hur.sh` deletes the previous APK before building**, so the candidate APK was copied out to
  `hur-wifi-test-scripts/round-self-mode-call-foreground/round3-apks/candidate_4d8679e7.apk` before building
  the R4 baseline, and reinstalled from that copy afterward. Both md5s recorded above and confirmed different.
- **R0's install found a discard-rule-adjacent contamination early in the round, unrelated to the candidate
  code**: the first two attempts to bring up a clean Self Mode session both failed for platform reasons
  described in round 1 §0 — first the head-unit-server toggle was off (`SelfMode: Headunit Server ... is NOT
  running.`), then, after a redundant manual `ACTION_START_SELF_MODE` intent tore down a session that had
  just formed, Android Auto's local server wedged (`... has accepted 3 connections in a row without
  answering any of them`). Both needed the phone's Android Auto developer-settings toggle cycled off/on by
  hand (the "one setup step that cannot be scripted", per round 1 §0). Discarded captures kept alongside
  `r3.txt` as `r3-discarded-selfmode-relaunch.txt` and `r3-discarded-wedged-server.txt`. Third attempt was
  clean, single handshake, and is what `r3.txt` starts from.
- **R1's calls were not scripted one at a time as the brief's runs enumerate them** — the operator (with a
  second handset) ended up placing several calls back-to-back while this session was mid-analysis, so R1
  ended up with **four** independent, fully-corroborating incoming-call raise cycles rather than one, plus
  one call that never covered the projection at all (see R1 below). All four are reported since they're
  clean and consistent; none needed to be discarded.
- **`dumpsys activity activities | grep topResumedActivity` was not caught at the exact millisecond during
  R1's covered window** — the polling loop happened to run in a quiet gap before the operator's call
  actually landed. R1's "topResumedActivity is us during the call" claim is instead proven from the
  `wm_on_top_resumed_lost_called` (Dialer) / `wm_on_top_resumed_gained_called` (us) EventLog pair, which
  `dumpsys` itself reads from and which carries exact timestamps — used instead, and is at least as strong
  a proof. R4's control run **did** catch a live `topResumedActivity` snapshot mid-call, confirming it
  stayed on Dialer's `InCallActivity` throughout.
- **R5 was not run.** Its own step 3 needs the candidate's process to switch out of Self Mode into an
  ordinary session *without restarting it* — meaning the standard settings.xml-with-app-stopped write path
  can't be used, and the session would need the UNISOC head-unit rig's own Android Auto to reconnect to this
  phone as the AA phone side. Operator confirmed this is arrangeable but, given R1-R4 already answered the
  round's actual question, it was deferred by mutual agreement rather than attempted under time pressure.
  Reported as **not run**, not as UNTESTABLE — nothing was attempted that failed.
- Notification-tap investigation (operator-initiated, not in the brief): the operator noticed that tapping
  the phone's own system call notification versus Android Auto's own in-car call notification produced
  different behavior. One call cycle (the fourth, 20:31:54-20:32:14) never triggered Dialer's
  `InCallActivity` at all — the projection was never covered, so neither tap could have "gotten them out of"
  anything that call. A second, deliberate repeat (20:35:05 cycle) showed the ordinary mechanism clearly:
  Dialer's screen appears, and OHU raises itself back within ~1-2s, which the operator described directly
  ("The notification was up just by 1-2 seconds and then OHU put itself on top") and which matches the
  logged raise timing exactly. See the R1 write-up for the numbers.

## R0 — gate

**PASS**

- Build: `assembleGithubDebug` clean for `4d8679e7`.
- Unit tests: **784/784**, 0 failures, 0 ignored — exactly matching the brief's predicted 765 + 13
  (`SelfModeCallRaisePolicyTest`) + 6 (`CallStateTest`). Both new suites individually 13/13 and 6/6.
- Installed APK md5 matches the built APK exactly (`621b3072ec0e570be5a499b67845c742`).
- `settings.xml` before/after install: **zero delta** (diff empty).
- `cmd telecom get-default-dialer` → `com.android.dialer` (matches rounds 1-2; still not the reporters'
  `com.google.android.dialer`).
- `ro.build.version.sdk` = `35`, `ro.build.version.release` = `15` (matches round 2's OTA reading).
- Gearhead versionName = `17.5.663204-release`.
- `appops get com.andrerinas.headunitrevived SYSTEM_ALERT_WINDOW` → `allow` — overlay permission already
  granted; the "no overlay permission" failure path was never exercised this round.

## R1 — incoming call answered — the point of the round

**PASS.** Four independent, fully successful raise cycles captured (not the one the brief's script
enumerates — see Setup notes), plus one call that never covered the projection at all.

| # | Time | Dialer `START u0` → our `onResume` | Attempts | Overlay trampoline | `dropped` around it |
|---|---|---|---|---|---|
| 1 | 20:26:21 | 2124 ms | 1 of 3 | succeeded, uid 10268 confirmed on our own `START u0` | 0 in all 5 windows either side |
| 2 | 20:28:40 | 2092 ms | 1 of 3 | succeeded | 0 |
| 3 | 20:31:20 | 2038 ms | 1 of 3 | succeeded | 0 |
| 4 | 20:35:05 | 2050 ms | 1 of 3 | succeeded | 0 |

Average gap across the four: **2076 ms**. **The retry ladder never needed a second attempt** — every
raise landed on attempt 1, well inside the first 600 ms + 1.2 s window.

Decisive lines, cycle 1 (representative):

```
20:26:21.885534  ActivityTaskManager: START u0 {...cmp=com.android.dialer/com.android.incallui.InCallActivity...} from uid 10126
20:26:21.893357  AapProjectionActivity: onPause
20:26:22.606510  AapProjectionActivity: covered during a call, will raise the projection (0 attempts already spent)
20:26:23.109339  ActivityTaskManager: Displayed com.android.dialer/com.android.incallui.InCallActivity: +1s207ms
20:26:23.414918  AapProjectionActivity: raising the projection - attempt 1 of 3 during the call
20:26:23.953282  ActivityTaskManager: START u0 {...cmp=com.andrerinas.headunitrevived/...AapProjectionActivity...} from uid 10268 (BAL_ALLOW_SAW_PERMISSION)
20:26:23.955095  Overlay trampoline: startActivity succeeded
20:26:24.009339  AapProjectionActivity: onResume
```

`wm_on_top_resumed_lost_called` for Dialer's `InCallActivity` (20:26:23.950617) immediately followed by
`wm_on_top_resumed_gained_called` for our `AapProjectionActivity` (20:26:24.024814, cycle 4's equivalent
shown; cycle 1's pair lands the same way) is the independent EventLog confirmation that focus genuinely
changed hands, not just that our `startActivity` call returned success.

**Android 15 genuinely honoured the trampoline for us, all four times** — this is not inferred from
`Overlay trampoline: startActivity succeeded` alone (the brief's own warning about a silently-blocked
`startActivity`): every cycle also shows our own `ActivityTaskManager: START u0` with **uid 10268** (our
own uid, not the Dialer's), immediately followed by a genuine `onResume`.

`Throughput over 5000ms:` windows bracketing every raise stayed at `dropped=0` throughout (50-56 fps), and
`Configuring decoder:` appears exactly once in the whole capture, at session start (20:23:32) — never again
near any of the four raises. The reorder cost no picture.

**One call (20:31:54 - 20:32:14) never covered the projection at all.** `GH.ICarCall: onCallAdded` fired,
the call went `RINGING → ACTIVE` and ran ~18s, but **zero** `ActivityTaskManager: START u0` for Dialer's
`InCallActivity` appeared and **zero** `AapProjectionActivity: onPause` fired — the projection simply never
left the foreground for that call. Not a code defect: whatever path answered that particular call never
handed Dialer's own service the full-screen intent, so there was nothing for the raise logic to react to.
Worth noting as call-answer-path variability on this platform, separate from anything the candidate does.

**Finding not asked for by the brief: the `call raise finished` log line never appears on any of the four
successful cycles.** `AapProjectionActivity.onResume()` (`AapProjectionActivity.kt:1001-1005`) calls
`closeCallRaiseEpisode()` unconditionally, and that function (`:1519-1522`) clears the episode and cancels
the pending tick **with no log line**. The `"call raise finished - $reason"` line only prints from inside
`tickCallRaise()`'s `DONE` branch (`:1538-1543`) — which never gets a chance to run when the very next
`onResume` (from the successful raise itself) tears the pending tick down first. So on the observed
first-attempt-always-succeeds path, brief §5's fourth PASS-condition line is structurally unreachable; it
would only print on a slower convergence (attempt 2 or 3, or the post-call grace attempt). Items 1-3 of R1's
PASS conditions are otherwise unambiguous across all four cycles.

## R2 — outgoing call

**PASS.** Two cover/raise cycles inside one outgoing call (the operator's phone screen was tapped again
mid-call, re-showing the Dialer a second time):

| # | Dialer `START u0` → our `onResume` | Attempts |
|---|---|---|
| 1 | 1927 ms | 1 of 3 |
| 2 | 2094 ms | 1 of 3 |

Same mechanism as R1, confirming direction-independence. `dropped=0` in every `Throughput` window from the
call through 9 more windows after hangup (20:37:43 - 20:38:43); no `Configuring decoder:` line reappeared.

## R3 — the user's own exit is not argued with

**PASS**, with one caveat on the first half.

- **No call, Home press**: zero `covered during a call` / `raising the projection` lines — but
  `KEYCODE_HOME` **did not actually pause `AapProjectionActivity`** on this phone; video kept rendering
  uninterrupted at 50-52 fps straight through the press and the 10 s hold, and the only `onPause`/`onResume`
  pair in that window came from the return `am start` command itself (a `LAUNCH_SINGLE_TASK` re-deliver
  flicker), not from Home backgrounding it. So this half's zero is real but doesn't exercise the covered
  path — it would look identical if the feature did nothing.
- **Home press during a live call**: this time Home genuinely paused the activity
  (`onUserLeaveHint` → `onPause` at 20:40:34.907, confirmed via `mCallState=2` immediately beforehand) and
  **zero** `covered`/`raising` lines followed for the rest of the observed window, despite the call still
  being active. This is the meaningful half of R3, and it passed cleanly: a deliberate exit is not fought
  even mid-call.

## R4 — the setting off, control

**PASS.**

- Settings written: `raise-projection-during-call=false` (boolean), app stopped, readback confirmed.
- Baseline `ea7aa7e0` installed (md5 `b120faa29ce718002e59126c84328256`, confirmed different from candidate).
- Self Mode session formed cleanly (single `SSL handshake complete`, no discard-rule hits).
- Real call: `AapProjectionActivity: onPause` fired (covered, same as always), and **zero**
  `covered during a call` / `raising the projection` lines for the whole call — the setting genuinely gates
  the entire path off.
- `dumpsys activity activities | grep topResumedActivity`, taken live mid-call: **stayed on**
  `com.android.dialer/com.android.incallui.InCallActivity` the whole time — confirmed the projection never
  came back while the call was up.
- At hangup: `AapProjectionActivity: onResume` fired **2158 ms** after `Telecom: InCallController:
  onCallRemoved`, with **no** `ActivityTaskManager: START u0` for our package anywhere in that window — the
  activity resurfaced on its own, exactly the mechanism round 1 described. **Numeric caveat**: round 1
  measured this same self-return gap at ~17 ms; this run measured 2158 ms. The qualitative mechanism
  (self-return, no explicit launch for us) matches round 1 exactly; the magnitude does not, and is reported
  as-is rather than rounded to match.

## R5 — not run

Deferred by mutual agreement (see Setup notes) after R1-R4 already answered the round's central question.
Not attempted, so reported as **not run** rather than UNTESTABLE or INCONCLUSIVE.

## Anything the brief did not ask about

- The operator's own notification-tap observation (Setup notes) lines up with the measured raise timing:
  Android Auto's own in-car call notification appears able to answer a call without Dialer's
  `InCallActivity` ever taking the full screen (the fourth call in R1's table never covered the projection at
  all), while the phone's system call notification/screen does trigger the ordinary cover-then-raise cycle
  timed at ~2.0-2.1 s across every measured instance.
- `bt-address` was not specifically checked this round (out of scope for this thread); no action needed.
- Four separate live-call arrangements were needed across R1/R2/R3/R4 since each brief run's call had to be
  placed fresh from the second handset; all were coordinated live with the operator rather than scripted,
  consistent with the brief's own §0 statement that this round is "not fully unattended."
