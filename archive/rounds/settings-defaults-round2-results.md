# settings-defaults: round 2 results

- Candidate: C `fix/settings-defaults` `77c0b1fe` (`GIT_SHA` reads `77c0b1fe0226`), APK md5 `76a0f6e4573fc7447d4d67da3a40d620`
- Baseline: A `main` `f749b84c`, round 1's APK reused, md5 `17715974a26b7b6c5651c82eab02b383`
- Unit: D-HU (API 34) for every run, D-POCO as the phone in E2b, E2b-2 and E4
- Date: 2026-10-08

## Setup notes

1. **R0 `dirty` grep.** `grep -c dirty` on the dex strings reads 6, but they are not the build field: the six hits are `dirty`, `dirty`, `dirtyColors`, `dirtyConfig`, `dirtyFile` and `dirtyFiles`. The build field reads `77c0b1fe0226` (in `LogExporter: exportArtifact commit=77c0b1fe0226` and the session line), with no `-dirty` suffix; the worktree `git status` was clean. I treated the gate as passed on that basis.
2. **Injected taps** under the operator's standing rule (at most 5 per run). E2b, E2b-2 and E4's Exit taps: three (`60,665`, `60,665`, `240,110`), the coordinates of round 1's Setup note 11. I did not re-check them with a `uiautomator dump`; each run saved a `dash` screenshot, and `native_focus=1` in every E2b run shows the Exit tile was hit. R3b: one tap at `720,204`, aimed at our own button. No injected tap in R1b.
3. **R1b's hand tap needed two attempts.** The recorder was armed at 19:02:44. The first 180 s window recorded no touch (the operator was not looking at D-HU at that moment). The recorder stayed armed, the operator was asked again, and one `BTN_TOUCH DOWN` was recorded about 8 s into the second wait. Nothing was injected.
4. **`pseg` is CR-safe.** The phone capture has CR line endings, so the brief's `pseg` would count 0; `sd2_env.sh` overrides it to strip CRs. `phone_aa_before` values below come from that.
5. **Settings written for the E runs:** `aa-exit-action` deleted, `enable-floating-button` false, plus (as in round 1's E runs) `native-poke-all-paired` true and `native-driver-selection-mode` 0, `onboarding-version` 2. `pget aa-exit-action` read ABSENT before and after each E run. The brief says not to write `native-poke-all-paired=false` for these runs; I wrote `true`, which is the file's value in the round-start backup.
6. **Phones.** D-POCO and D-MOTO were in airplane mode with Bluetooth and WiFi explicitly off (verified with `dumpsys`) outside the E runs. D-POCO sat at 18% to 19% on the PC's charger. No session formed on its own between runs.
7. **Session formation.** `toggle=0` in E2b, E2b-2 and E4: no head unit Bluetooth toggle was needed.
8. **Backup and restore.** `round-start.settings.xml` (165 elements) is byte-identical to round 1's round-start backup; D-HU's `settings.xml` was restored from it at the end and diffed identical. The overlay permission was set to `allow` for R3b and R1b and back to `default`. `block_untrusted_touches` read null (blocking on). No logcat or `getevent` was left running.
9. **Installs.** C was installed once (`adb install -r`) before E2b; A was installed with `-r -d` before R1b. The md5 was checked with `apk_check` before every run.
10. R3's replacement and the R1 follow-up were the only changes from round 1's list; nothing from §8 (Do not re-run) was re-run.

## R0

**PASS**

Tests 2836 run, 0 failures, 0 errors (count unchanged from round 1). C's md5 `76a0f6e4573fc7447d4d67da3a40d620` differs from B's `285526785255eb362bd61968e89ba847` and from A's. `grep -c '^77c0b1fe'` on the dex strings: 1. No `-dirty` suffix on the build field (setup note 1).

## E2b: an untouched Exit setting disconnects as a user exit (C). Point of the round.

**PASS**

Valid run: `throughput_before=3`, `phone_aa_before=1758`, `native_focus=1`. After the Exit tile:

| Count | Required | Measured |
|---|---|---|
| `disconnect` | 1 or more | 1 |
| `state_user_exit` | 1 or more | 1 |
| `state_link_lost` | 0 | 0 |
| `user_exit` | 1 or more | 1 |
| `minimize` | 0 | 0 |
| `accepted_after` | 0 | 0 |
| `throughput_after` | 0 | 1 (see below) |
| `aa-exit-action` afterwards | ABSENT | ABSENT |

`throughput_after=1` is a counting artifact of the helper's window, not a frame after the Exit: the window opens at `exit-ready`, before the three taps, and the one `Throughput over` line is at 18:56:34.6, two seconds before `Video Focus NATIVE received` (18:56:36.6). After the Exit there were 0 throughput lines over the next 32 s. The sequence at the Exit: 18:56:36.578 `ExitAction: Disconnecting projection session`, 18:56:36.734 `AapService: session state disconnected (user_exit)`, 18:56:36.781 `Native AA user exit. Stopping active launcher.`; no `NativeAA: Connection accepted from` followed. `phone_byebye=4`. Front after the Exit: `MainActivity`. Round 1's E2 showed `link_lost` and a phone back about 1 s later; this run shows neither.

## E2b-2: the same again

**PASS**

Same result: `native_focus=1`, `disconnect=1`, `state_user_exit=1`, `state_link_lost=0`, `user_exit=1`, `minimize=0`, `accepted_after=0`, `phone_aa_before=1387`, `phone_byebye=4`, `aa-exit-action` ABSENT afterwards. `throughput_after=1` is the same artifact: the line is at 18:58:40.25, two seconds before `Video Focus NATIVE received` (18:58:42.3), and nothing follows the Exit.

## E4: a phone that leaves is still not a user exit (C)

**PASS**

In the `E4-drop` to `E4-dropped` window (phone put into airplane mode, 30 s): `state_other=1` (the session ended as a link loss or `phone_left`), `state_user_exit=0`, `user_exit=0`, `cooldown=0`. The last `Throughput over` was at 19:00:29.5. After the phone's radios came back, `form_session E4-back` printed `session toggle=0`: a session formed with no user action and no head unit Bluetooth toggle. In that window `NativeAA: Connection accepted from` is 0 and the cooldown line is 0; the session came back by another route than that accept line, which I did not trace.

## R3b: 5% is clickable (C)

**PASS**

Setup valid: `Added floating button overlay` 1. Window flags `NOT_FOCUSABLE LAYOUT_IN_SCREEN` (no `NOT_TOUCHABLE`), `lpalpha` empty (no alpha token, so 1.0). The injected tap at `720,204` (aimed at the button): `ohu_start=1`, `settings_start=0`, `untrusted=0`, `Removed floating button overlay` 1 after `R3b-tap-ready`. Round 1's B read `NOT_TOUCHABLE` with `alpha=0.05` at this setting.

## R1b: the defect on the merged build above 80% (A)

**PASS (shape 2)**

Setup valid: `Added floating button overlay` 1, window flags `NOT_FOCUSABLE NOT_TOUCHABLE LAYOUT_IN_SCREEN`, one real touch (`BTN_TOUCH DOWN` 1). With `floating-button-opacity-percent` 90, the `mAttrs` line reads **`alpha=0.8`**, not 0.9. The tap passed through: `settings_start=1`, `ohu_start=0`, `untrusted=0`, and the Wi-Fi settings activity was in front afterwards. So the window alpha is capped at 0.8 whatever the opacity setting, and a non-touchable overlay at 0% lets the tap through on D-HU, so the defect round 1's R1 looked for cannot occur at 0% on this unit. The brief's prediction for round 1's R1 (window alpha 1.0, tap blocked) is therefore refuted; whether the cap comes from Android or from the app was not separated here.

## R2b: the fix above 80% (C)

**UNTESTABLE** (skipped by the brief's own rule)

The brief runs R2b only if R1b is shape 1. R1b was shape 2.

## Anything the brief did not ask about

- E2b's helper window opens before the taps, so `throughput_after` will read at least 1 whenever a throughput line (every 5 s) falls in the 2 to 3 seconds between `exit-ready` and the Exit line. A fix is to start the window at `Video Focus NATIVE received`.
- E4: after the phone returned there was no `Connection accepted from` line in the window, yet a session formed. If a later round needs to know how the phone came back (RFCOMM accept, the persistent WiFi Direct group, or the phone's own reconnect), it should read the lines between `E4-back` and the first `Throughput over`.
- Evidence: release `rig-evidence-settings-defaults-round2`, asset `settings-defaults-round2-captures.zip` (7503884 bytes), sha256 `61a63041e926f17bbbd800f93e90e7f90f74c18eedde90d06346c57f32429729`. The APKs are not in the zip; their md5s are in the header.
