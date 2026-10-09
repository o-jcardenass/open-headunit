# settings-defaults: round 2 brief

## 1. Build and baseline

| Arm | Ref | SHA | Get it |
|---|---|---|---|
| A (baseline) | `main` | `f749b84c` | reuse the round 1 APK (md5 `17715974a26b7b6c5651c82eab02b383`); if you no longer have it, `git fetch fork && git checkout f749b84c` and rebuild |
| C (candidate) | `fix/settings-defaults` | `77c0b1fe` | `git fetch fork && git checkout 77c0b1fe` |

C is round 1's B (`9e32cf47`) plus two commits: `59f34df2` (the Exit fix) and `77c0b1fe` (any visible opacity stays clickable). History was not rewritten: `9ccadb7f` and `9e32cf47` are unchanged. This brief first named `59f34df2`; if you already built that, rebuild at `77c0b1fe`.

**Build gate (R0).** Build C with `build_hur.sh` and copy the APK out of `apks/` at once. Run `run_unit_tests.sh` on C.

- PASS: 2836 tests and 0 failures, counted from the JUnit XML. The count is the same as round 1: `59f34df2` adds no test, and `77c0b1fe` changes `FloatingButtonOpacityPolicyTest` (still 9) without adding one.
- PASS: C's md5 differs from round 1's B (`285526785255eb362bd61968e89ba847`) and from A.
- PASS: `unzip -p <C.apk> 'classes*.dex' | strings | grep -c '^77c0b1fe'` is 1 or more. This is the `GIT_SHA` build field. Also `grep -c 'dirty'` on the same output: if it is 1 or more, the build had local changes; stop and report.
- A build or test failure stops the round.

Install with `adb install -r <named apk>` only. Run `apk_check` before every run.

## 2. What this is and why

Round 1's E2 failed. The Exit in Android Auto disconnected the session (`ExitAction: Disconnecting projection session`), but the session ended as `link_lost`. The wireless stack stayed up, and the phone came back in about 1 s.

The cause is in `CommManager`. The Exit path sets `wasUserExit` on the transport and stops it. The transport's quit callback then set `_transport` to null **before** `transportedQuited` read `_transport?.wasUserExit`. So the read always gave false. This bug is older than this branch (since `2c7d7440`, 2026-07-17), so "Disconnect" in the Exit setting has never ended a session as a user exit. `headunit://exit` and `ACTION_EXIT` are not affected: they go through `disconnect()`, which sets the state itself. That is why round 1's end-of-run `ACTION_EXIT` did log the user-exit line.

`59f34df2` passes the quitting transport's own flag to `transportedQuited`. Prediction, **not measured**: the Exit now ends the session as `user_exit`, the Native AA launcher stops, and the phone does not reconnect.

A phone that leaves on its own still sets no flag, so that path is unchanged. E4 checks this, because a wrong flag there would hold the stack down after every dropped link.

### The 5% rule (`77c0b1fe`)

Round 1's B made the button untouchable below 10%. The slider moves in steps of 5, so that affected 0% and 5%. `77c0b1fe` goes back to the merged PR's rule: only 0% hides the button and lets taps through. At 5% the button is faint but clickable, with an opaque window and the opacity on the view, as at every other visible value. A tap that lands on our own button is not subject to Android 12's pass-through rule, so this needs no window alpha. Round 1's R3 (5% passes through) no longer describes C; R3b replaces it.

### R1 from round 1

R1 needed a window with `NOT_TOUCHABLE` and **no** `alpha=` token. A's window read `alpha=0.8` with the "Opacity" setting at 80. A's code never sets the window alpha (`LayoutParams.alpha`); it sets only the view alpha. So the 0.8 came from somewhere else. There are two explanations, and R1b separates them:

1. The 0.8 is the "Opacity" setting reaching the window in some way we have not found. Then at opacity 90 the window reads `alpha=0.9`, and Android 12+ should block the tap.
2. Android itself caps a non-touchable overlay window at 0.8, the largest alpha it lets touches pass through. Then at opacity 90 the window still reads `alpha=0.8`, and the tap passes on A too. In that case the defect R1 looked for cannot happen at 0%.

Neither is measured. Both outcomes are useful, so R1b has no FAIL for the build; it records which one happened.

## 3. What is different this round

- **The point of the round is E2b.** R3b and R1b follow.
- **R3b's tap is injected**, under the operator's standing rule, as round 1's R4 was. It aims at our own button, so the pass-through question does not arise.
- **Exit taps are injected**, as in round 1's E runs, under the operator's standing rule (at most 5 injected taps per run). The coordinates are round 1's Setup note 11. Check them with `uiautomator dump` or a screenshot before E2b. If they moved, use the new ones and say so in Setup notes.
- **R1b and R2b need a real hand tap.** An injected tap does not pass through `/dev/input`, and we do not know whether Android checks it against the overlay in the same way. The operator taps the **visible Wi-Fi row** of the OEM settings app, not the button, after the executor prints the ready line. The recorder is armed before that line. If the operator cannot tap, R1b and R2b are UNTESTABLE. Do not replace the hand tap with `input tap`.
- **R2b runs only if R1b reproduces the defect** (shape 1 in §7). Otherwise skip it.
- **Phones stay in airplane mode, with Bluetooth and WiFi switched off explicitly** (round 1 Setup note 5), except during the E runs.
- **A session may need one head unit Bluetooth toggle to form** (round 1 Setup note 10). This is allowed once per run and is recorded as `toggle=1`.
- **The phone pattern** is `[/ ]` before `GH.` or `CAR.` (round 1 Setup note 9).

## 4. Settings keys

Write them with the app stopped, using the template's §1 write method on D-HU. Read every key back before launch. Back up `settings.xml` with `pdump round-start` before R1b and restore it after the last run, as in round 1.

R1b and R2b use round 1's R1 keys, with one change:

| Key | Type | Value |
|---|---|---|
| `enable-floating-button` | boolean | `true` |
| `floating-button-connection-status-mode` | boolean | `true` |
| `floating-button-disconnected-opacity-percent` | int | `0` |
| `floating-button-opacity-percent` | int | **`90`** |
| `floating-button-size-dp` | int | `96` |
| `floating-button-x-percent` | int | `50` (round 1 calibration) |
| `floating-button-y-percent` | int | `23` (round 1 calibration) |
| `onboarding-version` | int | `2` |
| `native-driver-selection-mode` | int | `0` |
| `native-poke-all-paired` | boolean | `false` |

E2b and E4 use the rig's Native AA baseline (do **not** write `native-poke-all-paired=false`), plus:

| Key | Type | Value |
|---|---|---|
| `aa-exit-action` | int | delete (the template's §1 delete half) |
| `enable-floating-button` | boolean | `false` |

Before R1b and R2b, set the overlay permission and record `block_untrusted_touches`, as round 1 §4 says. Set the permission back to `default` after R2b (or after R1b if R2b is skipped).

`ACTION_LOG_MARKER` is not gated on either build, so `allow-external-configuration` is not needed.

## 5. The helper

Source `sd_lib.sh` **as it stood at the end of round 1** (with your `bring_up` that opens the OEM settings app, and `lpalpha`). Then source this file as `sd2_lib.sh`. It replaces `hand_exit` and `grade_exit` and adds two helpers. It needs `HU`, `OUT`, `RUN` and `PH` set.

```bash
# sd2_lib.sh : settings-defaults round 2 (source after sd_lib.sh)
# Injected Exit: dashboard, launcher grid, Exit tile (round 1 Setup note 11)
EXIT_TAPS="60,665 60,665 240,110"
tap_exit() { pmark "$RUN-exit-ready"; local p
  for p in $EXIT_TAPS; do adb -s "$HU" shell input tap ${p%,*} ${p#*,}; sleep 2; done
  local n=0; until sed -n "/AutomationMarker: $RUN-exit-ready\$/,\$p" "$(CAP)" | grep -aqF 'Video Focus NATIVE received'; do
    n=$((n+1)); [ $n -ge 75 ] && break; sleep 0.2; done
  sleep 30; front > "$OUT/$RUN.front-after-exit.txt"; pmark "$RUN-exit-done"; }
# form_session : wait up to 45 s for projection, then one Bluetooth toggle and 45 s more
form_session() { local from=$1 n=0; TOGGLE=0
  until seg "$from" ZZZ_NONE | grep -aqF 'Throughput over'; do n=$((n+1))
    if [ $n -eq 45 ]; then adb -s "$HU" shell svc bluetooth disable; sleep 4; adb -s "$HU" shell svc bluetooth enable; TOGGLE=1; fi
    [ $n -ge 90 ] && { echo "no_session toggle=$TOGGLE"; return 1; }; sleep 1; done; echo "session toggle=$TOGGLE"; }
grade_exit() { local s; s=$(seg "$RUN-exit-ready" "$RUN-exit-done")
  echo "throughput_before=$(seg "$RUN-start" "$RUN-exit-ready" | grep -acF 'Throughput over')"
  echo "native_focus=$(echo "$s" | grep -acF 'Video Focus NATIVE received. User clicked Exit in Android Auto.')"
  echo "minimize=$(echo "$s" | grep -acF 'ExitAction: Minimizing projection to OEM Launcher')"
  echo "disconnect=$(echo "$s" | grep -acF 'ExitAction: Disconnecting projection session')"
  echo "state_user_exit=$(echo "$s" | grep -acF 'AapService: session state disconnected (user_exit)')"
  echo "state_link_lost=$(echo "$s" | grep -acF 'AapService: session state disconnected (link_lost)')"
  echo "user_exit=$(echo "$s" | grep -acF 'AapService: Native AA user exit. Stopping active launcher.')"
  echo "accepted_after=$(echo "$s" | grep -acF 'NativeAA: Connection accepted from')"
  echo "throughput_after=$(echo "$s" | grep -acF 'Throughput over')"
  echo "phone_aa_before=$(pseg "$RUN-start" "$RUN-exit-ready" | grep -acE '[/ ](GH|CAR)\.')"
  echo "phone_byebye=$(pseg "$RUN-exit-ready" "$RUN-exit-done" | grep -aic byebye)"
  echo "front_after=$(cat "$OUT/$RUN.front-after-exit.txt")"; }
# grade_drop : counts for E4, between two markers
grade_drop() { local s; s=$(seg "$1" "$2")
  echo "state_user_exit=$(echo "$s" | grep -acF 'AapService: session state disconnected (user_exit)')"
  echo "state_other=$(echo "$s" | grep -acE 'AapService: session state disconnected \((link_lost|phone_left)\)')"
  echo "user_exit=$(echo "$s" | grep -acF 'AapService: Native AA user exit. Stopping active launcher.')"
  echo "cooldown=$(echo "$s" | grep -acF 'AapService: User exit cooldown active for')"; }
```

`hand_tap`, `grade_tap`, `win`, `lpalpha`, `pmark`, `pseg`, `pcap_start`, `pcap_stop`, `cap_start`, `cap_stop`, `apk_check`, `front`, `pget` and `pdump` come from `sd_lib.sh` unchanged. `cap_stop` can hang when the script's output is piped (round 1); do not pipe a run script's output.

## 6. The deciding lines

Every app line was checked with `grep -F` against `77c0b1fe`. The framework lines are patterns from round 1.

| What | Line or pattern | Where | Level |
|---|---|---|---|
| Exit tapped | `Video Focus NATIVE received. User clicked Exit in Android Auto.` | HU capture | INFO |
| Exit minimized | `ExitAction: Minimizing projection to OEM Launcher` | HU capture | INFO |
| Exit disconnected | `ExitAction: Disconnecting projection session` | HU capture | INFO |
| Ended as a user exit | `AapService: session state disconnected (user_exit)` | HU capture | INFO |
| Ended as a link loss | `AapService: session state disconnected (link_lost)` | HU capture | INFO |
| Ended, phone left | `AapService: session state disconnected (phone_left)` | HU capture | INFO |
| Launcher stopped | `AapService: Native AA user exit. Stopping active launcher.` | HU capture | INFO |
| Reconnect refused | `AapService: User exit cooldown active for` | HU capture | INFO |
| Phone came back | `NativeAA: Connection accepted from` | HU capture | INFO |
| Projection | `Throughput over` | HU capture | INFO |
| Marker | `AutomationMarker: <label>` | both captures | WARN |
| Phone side active | `[/ ](GH\|CAR)\.` | phone capture | framework |
| Tap reached Settings | `START u0 .*cmp=com.android.settings/` | HU capture, tap window | framework |
| Tap dropped | `untrusted touch`, case-insensitive | HU capture, tap window | framework |
| Tap reached the button | `START u0 .*cmp=com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.MainActivity` | HU capture, tap window | framework |
| Button shown | `FloatingButtonService: Added floating button overlay` | HU capture | INFO |
| Button removed | `FloatingButtonService: Removed floating button overlay` | HU capture | INFO |
| Window alpha | the `alpha=` token on the `mAttrs` line (`lpalpha`) | `$RUN.<label>.overlay.txt` | n/a |

`log-level` 2 (INFO) carries every app line above.

## 7. Runs

Units: **D-HU** (API 34) for every run, **D-POCO** as the phone in E2b and E4. For each run: `RUN=<id>`, `apk_check` with that arm's md5, force-stop, write the keys, `cap_start` (and `pcap_start` in the E runs), then the steps. `cap_stop` at the end. Reset between runs with the template's §3a commands.

### E2b: an untouched Exit setting disconnects as a user exit (C, D-HU + D-POCO). Point of the round.

1. Phone in airplane mode with Bluetooth and WiFi off. HU: force-stop, write the keys (§4), `pget aa-exit-action` must print ABSENT. `cap_start`, `pcap_start`.
2. Launch `MainActivity`, `sleep 15`, `pmark $RUN-start`.
3. Phone: airplane mode off, `svc wifi enable`, `svc bluetooth enable`, verify with `dumpsys`. Then `form_session $RUN-start`. If it prints `no_session`, the run is INCONCLUSIVE (no session).
4. `sleep 10`, `tap_exit`, `grade_exit`.
5. `send ACTION_EXIT`, `sleep 5`, `pmark $RUN-end`. Phone airplane mode on. `cap_stop`, `pcap_stop`.
6. HU force-stop, `pget aa-exit-action`.

The run is valid only if `throughput_before` ≥ 1, `phone_aa_before` ≥ 1 and `native_focus` ≥ 1. Otherwise it is INCONCLUSIVE.

- **PASS:** `disconnect` ≥ 1, `state_user_exit` ≥ 1, `state_link_lost` = 0, `user_exit` ≥ 1, `minimize` = 0, `accepted_after` = 0, `throughput_after` = 0, and `aa-exit-action` still ABSENT afterwards.
- **FAIL:** any of those not met. Quote the HU lines from `ExitAction:` to 5 s after it.
- If the change did nothing, this run looks like round 1's E2: `state_link_lost` = 1 and `accepted_after` ≥ 1 about 1 s later. `accepted_after` = 0 means nothing only if the phone had a way back, so also report `phone_byebye` and the phone's last `[/ ](GH|CAR)\.` line in the exit window with its time.

Repeat E2b once more (`RUN=E2b-2`). Both must PASS.

### E4: a phone that leaves is still not a user exit (C, D-HU + D-POCO)

This guards the path `59f34df2` must not change.

1. Steps 1 to 3 of E2b, with `RUN=E4`.
2. `sleep 10`, `pmark E4-drop`. Phone: airplane mode on. `sleep 30`, `pmark E4-dropped`, `grade_drop E4-drop E4-dropped`.
3. Phone: airplane mode off, `svc wifi enable`, `svc bluetooth enable`, verify. `pmark E4-back`. `form_session E4-back`.
4. `send ACTION_EXIT`, `sleep 5`, `pmark E4-end`. Phone airplane mode on. `cap_stop`, `pcap_stop`.

- **PASS:** in the `E4-drop` window, `state_other` ≥ 1, `state_user_exit` = 0, `user_exit` = 0, `cooldown` = 0; and `form_session E4-back` prints `session` (the phone came back with no user action). Report its `toggle=` value.
- **FAIL:** `state_user_exit` ≥ 1, or `user_exit` ≥ 1, or no session after `E4-back` while `state_user_exit` ≥ 1.
- If `state_other` = 0 and `state_user_exit` = 0, the session had not ended within 30 s: the run is INCONCLUSIVE. Report the last `Throughput over` time.
- If no session forms after `E4-back` but every row of the drop window passed, the run is INCONCLUSIVE, not a FAIL. Report `toggle=` and the last `NativeAA:` line.

### R3b: 5% is clickable (C, D-HU)

Keys: round 1's R3 keys (status mode `true`, disconnected opacity **`5`**, opacity 80, x-percent 50, y-percent 23). Steps: `bring_up`; `front` must name the OEM settings app; `win R3b`; read the window alpha with `lpalpha` on `R3b.R3b.overlay.txt`; `shot R3b`; `mark R3b-tap-ready`; `adb -s $HU shell input tap 720 204`; `sleep 3`; `mark R3b-tap-done`; `grade_tap tap` (it reads exactly these two markers).

- Setup: `Added floating button overlay` ≥ 1, the flags do **not** contain `NOT_TOUCHABLE`, and `lpalpha` shows no alpha (1.0).
- **PASS:** `ohu_start` ≥ 1, `settings_start` = 0, `untrusted` = 0, and `Removed floating button overlay` ≥ 1 after `R3b-tap-ready`.
- **FAIL:** `settings_start` ≥ 1 (the tap passed through, which is round 1's B behaviour), or the flags contain `NOT_TOUCHABLE`.
- If the change did nothing, the window reads `NOT_TOUCHABLE` with `alpha=0.05`, as round 1's R3 measured. The flag line alone separates the two.

### R1b: the defect on the merged build above 80% (A, D-HU)

Keys: §4 (opacity **90**). Steps: `bring_up`; `front` must name the OEM settings app; `win R1b`; read the window alpha with your round 1 `lpalpha` helper on `R1b.R1b.overlay.txt`; then `hand_tap tap` with the operator tapping the Wi-Fi row at `TAPX`=720, `TAPY`=204; `grade_tap tap`.

The setup is valid if `Added floating button overlay` ≥ 1 and the window flags contain `NOT_TOUCHABLE`, and `touch_downs` ≥ 1. If not, the run is INCONCLUSIVE.

Grade it as one of three shapes and name the shape:

| Shape | `lpalpha` | Tap | Meaning |
|---|---|---|---|
| 1 | `0.9` | `untrusted` ≥ 1, `settings_start` = 0 | The defect is real above 80%. **PASS (defect reproduced).** Run R2b. |
| 2 | `0.8` | `settings_start` ≥ 1, `untrusted` = 0 | Android caps the window. The defect cannot occur at 0% on D-HU. **PASS (shape 2).** Skip R2b. |
| 3 | `0.9` | `settings_start` ≥ 1, `untrusted` = 0 | The tap passes even above 0.8. **FAIL (prediction refuted).** Skip R2b. |

Any other combination: quote the overlay block and the tap window, and mark it INCONCLUSIVE.

### R2b: the fix above 80% (C, D-HU). Only if R1b is shape 1.

R1b's keys and steps on C.

- `lpalpha` must print `0.0` and the flags must contain `NOT_TOUCHABLE`.
- **PASS:** `touch_downs` ≥ 1, `settings_start` ≥ 1, `untrusted` = 0, `ohu_start` = 0.

**Stop rule:** stop after R2b (or after R1b if R2b is skipped), or after 3 runs in a row are UNTESTABLE for the same reason. After the last run, restore `round-start.settings.xml` and set the overlay permission back to `default`.

## 8. Do not re-run

R0's B gate, R2, R4 to R6, E1, E3 and S0 to S2 passed in round 1, and C changes none of their code paths (R2 is 0%, where both rules agree). R3 is superseded by R3b, not re-run. The C0 calibration stands (x-percent 50, y-percent 23, tap point 720,204).

## 9. Report back

1. E2b and E2b-2: `state_user_exit`, `user_exit` and `accepted_after`. Together they say whether the Exit now ends the session as a user exit.
2. E4: `state_user_exit` and whether a session formed after `E4-back`. Together they say whether a dropped link still recovers.
3. R3b: `ohu_start`, `settings_start` and the window flags, which say whether 5% is now clickable.
4. R1b: the shape, with the `lpalpha` value.
