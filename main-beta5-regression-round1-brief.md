# main-beta5-regression round 1 brief

A regression round on current `main`, the 3.5.0-beta5 candidate. Every PR in it already passed its own round. This round checks the merged combination, end to end, on three transports. At the end it leaves this build installed on all five units.

## 1. Build and baseline

| | |
|---|---|
| Candidate | upstream `main` @ `ec9d9c33` (merge of the USB re-attach PR) |
| Baseline | none. No A/B. Every condition is absolute. |
| History | not rewritten. `fork/main` is behind at `71375a68`, so build from `origin`. |

```bash
git fetch origin main
git checkout --detach ec9d9c33
git log -1 --format=%H    # must print ec9d9c33...
```

Build with the rig's own scripts (`build_hur.sh`, `GRADLE_OPTS=-Dorg.gradle.workers.max=2`) after the thermal gate (rig-quirks `topics/tooling.md`, first entry). Copy the APK out of `apks/` at once into `main-beta5-round1/`. Run `run_unit_tests.sh` once.

The release commit `3bc0b957` on `release/3.5.0-beta5` changes only `CHANGELOG.md`, `README.md` and the version (118, `3.5.0-beta5`). It is not pushed. Do not build it. This round builds versionCode 117, `3.5.0-beta4`.

## 2. What this is and why

Since `v.3.5.0-beta4` (`0cbff004`), 15 PRs merged. The contributor's stack changed AAP framing, TLS, video worker ownership, session replacement, reconnect timers, Nearby tunnels and the ByeBye write. Our PRs changed Exit in Android Auto, Save-only-changes, USB Media audio, the wizard, stale USB recovery and the no-screen reconnect hold. The floating button gained a connection status mode. Each passed alone or on the stack build `2c4b06fd`. Five merged after that stack build, and nothing has run them together.

The questions are:
1. Does a Native AA session still form, hold a picture for 10 minutes and end cleanly, with zero TLS or framing faults?
2. Does Exit in Android Auto end the session as a user exit with the ByeBye sent first, and stay ended?
3. Does a USB dongle session and an API 17 Headunit Server session still form and end cleanly on the merged TLS code?

## 3. What is different this round

- **No A/B.** One APK. Every PASS is an absolute comparison.
- **The floating button is on in W1 only**, in connection status mode at 0% when disconnected. It is off in every other run, so it cannot cover an injected tap.
- **Three stages, because 5 units share 4 USB ports.** Stage W: D-HU and D-POCO cabled. Stage U: D-POCO on wireless adb with the dongle on its OTG port, D-MOTO cabled. Stage H: D-HP cabled, D-POCO on wireless adb on the house WiFi. The Closing stage plugs each unit in turn.
- **Known, not a FAIL:** the `BootCompleteReceiver` crash `ForegroundServiceStartNotAllowedException` on D-POCO after a force-stop. It is on `main`. Record the count only.
- **Phone `Critical error` lines are report-only.** Rounds of the PRs in this build showed 2 to 5 per session end on both arms, including `GH.WirelessStartup: Critical error encountered` at teardown. Quote every one with its timestamp.

## 4. Pre-flight (one batched ask to the operator, before R0)

Ask all of these in one message, then run unattended.

1. D-POCO's Bluetooth paired list holds D-HU's radio (`Navegadortz2`) and no other head unit. Check: `adb -s 4f4027e9 shell dumpsys bluetooth_manager | grep -a -A8 'Bonded devices'`. Ask the operator to forget any other head unit (rig-quirks `units/D-POCO.md`).
2. D-SAM's Bluetooth is off for the whole round.
3. The dongle is ready for Stage U on D-POCO's OTG port, and D-MOTO is bonded to it.
4. Gearhead's head unit server is on, on D-POCO, for Stage H. From the host: `nc -z -w 3 <D-POCO IP> 5277 && echo open`.
5. D-POCO and D-MOTO are above 60% battery.

## 5. Settings keys

Write with the app stopped (TESTING-TEMPLATE §1). Back up each unit's `settings.xml` first and diff it against its last backup, and put the delta in Setup notes. On D-HU, `stat` `shared_prefs/` and report it (rig-quirks `units/D-HU.md`).

**D-HU, every W run (`WKEYS`):**

| Element | Why |
|---|---|
| `<int name="wifi-connection-mode" value="3" />` | Native AA |
| `<int name="log-level" value="2" />` | INFO carries every line below |
| `<int name="onboarding-version" value="2" />` | no wizard |
| `<int name="native-driver-selection-mode" value="0" />` | no driver selector |
| `<boolean name="native-poke-all-paired" value="true" />` | wake D-POCO |
| `<int name="native-aa-wake-damage-verdict" value="0" />` | wake not latched off |
| `<boolean name="kill-on-disconnect" value="false" />` | service survives an exit |
| delete `video-profile-starvation-cap` | no silent 720p cap |
| delete `aa-exit-action` | default 2, DISCONNECT |
| `<boolean name="enable-floating-button" value="false" />` | off, except W1 |

**W1 adds:**

| Element |
|---|
| `<boolean name="enable-floating-button" value="true" />` |
| `<boolean name="floating-button-connection-status-mode" value="true" />` |
| `<int name="floating-button-disconnected-opacity-percent" value="0" />` |
| `<int name="floating-button-opacity-percent" value="80" />` |

Before W1, check the overlay grant: `adb shell appops get com.andrerinas.headunitrevived SYSTEM_ALERT_WINDOW`. If it is not `allow`, run `adb shell appops set com.andrerinas.headunitrevived SYSTEM_ALERT_WINDOW allow` and record it.

**D-POCO as USB head unit, U1 (`UKEYS`):** the `U1KEYS` of `usb-reattach-round4-brief.md` (in `archive/rounds/`), unchanged:
`int:wifi-connection-mode=3 int:log-level=2 int:onboarding-version=2 bool:kill-on-disconnect=false bool:auto-connect-last-session=true bool:auto-start-on-usb=true bool:reopen-on-reconnection=true bool:use-libusb=false set:connection-modes=usb,wifi del:video-profile-starvation-cap del:native-aa-wireless del:wifi-launcher-mode int:native-aa-wake-damage-verdict=0`, plus `bool:enable-floating-button=false`. Write it with `pocoput`, and use the fixed `ohu_setkeys.py` (rig-quirks `topics/tooling.md`, empty `set:` entry).

**D-HP, H1 (`HKEYS`):** `int:wifi-connection-mode=1 int:log-level=2 int:onboarding-version=2 set:connection-modes=wifi bool:kill-on-disconnect=false del:video-profile-starvation-cap bool:enable-floating-button=false`.

`allow-external-configuration` is not needed. `ACTION_LOG_MARKER` is not in `AutomationCommandPolicy.CONFIGURING` on this tree.

## 6. Shell helpers

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s $HU shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
mark() { send ACTION_LOG_MARKER --es text "$1"; adb -s $PH shell log -t RIGMARK "$1"; }
HUCAP() { adb -s $HU logcat -c; stdbuf -oL adb -s $HU logcat -v time > "$1-hu.txt" & }
PHCAP() { adb -s $PH logcat -c; stdbuf -oL adb -s $PH logcat -v time > "$1-phone.txt" & }
```

Stage W: `HU=27870808938846 PH=4f4027e9`. Stage U: `HU=<D-POCO IP>:5555 PH=ZY22GC3BM4`. Stage H: `HU=CNU350BGBJ PH=<D-POCO IP>:5555`. Run `adb logcat -G 16M` on every unit once. Grep every capture with `grep -a`. Never run two adb calls at once on one unit. Use `sleep 0.3` between calls.

Every run starts with the clean-run protocol (TESTING-TEMPLATE §4) and the thermal gate, and ends with `send ACTION_EXIT`, `sleep 3`, `adb -s $HU shell am force-stop $PKG`, then a restore of that unit's round backup.

## 7. The deciding lines

All are `AppLog.i` unless marked, and none has a `LOG_VERBOSE` guard. Each one was checked with `git grep -F` on `ec9d9c33`. Grep them in `<run>-hu.txt`, between the run's start and end markers, unless marked as phone lines.

| Id | String (head unit unless marked) | Note |
|---|---|---|
| L-SSL | `SSL handshake complete` | session formed. At INFO, one per session. |
| L-SDR | `Service Discovery Response` | projection started |
| L-TP | `Throughput over ` | one per 5 s window. Read `rendered=` and `dropped=` |
| L-TLS | `AapRead: invalid framing or TLS session`, `AapRead: TLS state cannot continue`, `SSL Decrypt failed`, `AapTransport: encrypted write failed or incomplete`, `AapRead: Fatal read error` | fault set. Count the sum. |
| L-INC | `send incomplete (ret=` | `AppLog.w` |
| L-FB | `FloatingButtonService: Added floating button overlay` | W1 only |
| L-NAT | `Video Focus NATIVE received. User clicked Exit in Android Auto.` | Exit tapped |
| L-EXA | `ExitAction: Disconnecting projection session` | |
| L-UX | `AapService: session state disconnected (user_exit)` | built from `session state $state ($reason)`. The full text is what prints. |
| L-COOL | `AapService: User exit cooldown active for` | |
| L-BYE | `AapTransport stopping and sending byebye (` | |
| L-BYW | `AapTransport: ByeBye write ` | then `SENT`, `FAILED`, `REJECTED`, `TIMED_OUT` or `INTERRUPTED` |
| L-SAVE | `CommManager: audio settings changed; reconnecting the projection session` | |
| L-SR | `SettingsRestart: route=` | expect `route=NATIVE retry=run` |
| L-DISC | `Disconnect action received.` | |
| L-DEL | `deliberate session end; reopening listeners without an automatic wake.` | |
| L-RET | `returned to a kept surface with a stopped decoder; re-arming keyframe recovery` | screen-off return |
| L-NOPIC | `relaunched surface has no picture after ` | must stay 0 |
| L-ACC | `Sending acc start` | USB |
| L-MATCH | `MATCH! Starting AapService` | discard rule |
| P-CRIT | phone: `Critical error` | report only |
| P-FATAL | phone: `FATAL EXCEPTION` | Gearhead process only |
| P-BYE | phone: `received ByeByeRequest` | |
| P-STATE | phone: `Car connection state changed` | phone saw the session |

## 8. Runs

**W1 is the point of the round.** Run W1, W2, W3, W4, U1, H1 in that order, then the Closing.

### R0. Build and identity gate

1. Build and test (section 1). Record the test count and failures.
2. `md5sum` the APK. Install it with `adb install -r -d` on D-HU and D-POCO only.
3. Pull each installed APK and `md5sum` it (`pm path`, then `adb pull`). It must match step 2.
4. `unzip -p <apk> 'classes*.dex' | strings | grep -c -F unprojectedEndsInARow` must be 1 or more. The symbol comes from the last commit in this build.
5. On each unit: `send ACTION_QUERY_STATE`. The `commit` on the `data=` line must start with `ec9d9c33`.

PASS: 0 test failures, md5s match, symbol found, both commits match. **A failure here stops the round.**

### W1. Native AA cold start, 10-minute soak, floating button, screen-off return (the point)

Setup: `WKEYS` plus the W1 keys, on D-HU. Phone in airplane mode. `adb -s $PH shell input keyevent KEYCODE_HOME`, then `dumpsys window | grep mCurrentFocus` must show the launcher.

1. `HUCAP W1; PHCAP W1; mark W1-start`
2. `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, then `sleep 20`.
3. `adb -s $HU shell dumpsys window windows > W1-win-armed.txt`
4. `adb -s $PH shell cmd connectivity airplane-mode disable`, then `adb -s $PH shell svc wifi enable` and `adb -s $PH shell svc bluetooth enable`. `mark W1-phone-on`
5. Wait up to 90 s for L-SSL. If it does not appear, `mark W1-nossl` and go to step 11.
6. `sleep 60`, then `adb -s $HU shell dumpsys window windows > W1-win-live.txt`
7. Soak 10 minutes from L-SSL. Every 40 s, swipe the map on the head unit: `adb -s $HU shell input swipe 900 360 500 360 300`. Every 120 s, skip a track: `adb -s $HU shell input keyevent 87`. These swipes go into the projected video, which is the standing exception (TESTING-TEMPLATE §3). `mark W1-soak-end`
8. Screen-off return: `adb -s $HU shell input keyevent 223`, `sleep 8`, `adb -s $HU shell input keyevent 224`, `sleep 2`, `send ACTION_RAISE_PROJECTION`, `mark W1-return`, `sleep 20`.
9. `mark W1-end`
10. Stop both captures.
11. End-of-run reset (section 6).

Floating button windows: in each `W1-win-*.txt`, find the `Window #` block whose window line names `com.andrerinas.headunitrevived` and whose `mAttrs` has `ty=APPLICATION_OVERLAY`. Record its `alpha=` and `fl=`. D-HU's own assist ball is a different package. Sonnet reads these two blocks itself.

PASS, all of these:
- L-SSL is 1 between `W1-phone-on` and `W1-end`, within 90 s of `W1-phone-on`.
- L-SDR is 1 or more.
- L-TLS sum is 0, and L-INC is 0.
- Soak windows (L-TP lines from L-SSL + 30 s to `W1-soak-end`): 100 or more lines. The median `rendered=` is 125 or more per window, which is 25 fps.
- L-FB is 1. In `W1-win-armed.txt` the overlay has `alpha=0.0` and `NOT_TOUCHABLE` in `fl=`. In `W1-win-live.txt` it has an alpha above 0 (1.0, or 0.8 from Android's own cap) and no `NOT_TOUCHABLE`.
- Return: L-NOPIC is 0 after `W1-return`. The first L-TP after `W1-return` with `rendered=` above 0 comes within 15 s.
- L-MATCH is 0. A second L-SSL is a discard (TESTING-TEMPLATE §4).
- Phone: P-STATE is 1 or more after `W1-phone-on`. P-FATAL for Gearhead is 0.

Report: the fps median, minimum and maximum. The total `dropped=`. The return time in ms. Every P-CRIT line.

If this run made no change, it would still pass. That would show the merge did no harm, which is the question. The fps median and L-TLS sum are the numbers that show how much load it carried.

Stop rule: if L-SSL is missing, re-run W1 once. Two misses is a FAIL. Then continue with W2.

### W2. Exit in Android Auto ends the session

Setup: `WKEYS` on D-HU (floating button off). Run steps 1 to 5 of W1 with label `W2`, then `sleep 30`.

1. `mark W2-exit`. Tap the Android Auto launcher at `adb -s $HU shell input tap 41 680`, `sleep 2`, then the Exit tile at `adb -s $HU shell input tap 252 85`. These are the working taps from the stack round's XS rerun.
2. If L-NAT does not appear within 5 s, take `adb -s $HU shell screencap -p /sdcard/w2.png` and pull it. Find the Exit tile and tap it. Use 5 taps at most, step 1 included. List every tap in Setup notes.
3. `sleep 60`, `mark W2-held`
4. `send ACTION_START_WIRELESS_SCAN`, `mark W2-rearm`. Wait up to 90 s for L-SSL.
5. `mark W2-end`, stop the captures, reset.

PASS, all of these:
- After `W2-exit`: L-NAT 1, L-EXA 1, L-UX 1, L-COOL 1, and L-BYW 1 reading `SENT`.
- No L-SSL from `W2-exit` to `W2-rearm`, which is 60 s or more.
- L-SSL is 1 between `W2-rearm` and `W2-end`.
- Phone: P-BYE is 1 or more between `W2-exit` and `W2-held`.

Report: the ms from L-BYE to L-BYW. The ms from `W2-rearm` to L-SSL.

### W3. Save reconnects a Native session

Setup: `WKEYS` on D-HU. Run steps 1 to 5 of W1 with label `W3`, then `sleep 30`. Do two cycles. The second Save toggles the setting back.

Per cycle k (1, 2):
1. `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0`, `sleep 3`. D-HU's shell is root, so a non-exported activity opens.
2. `mark W3-save$k`. Tap the toggle at `adb -s $HU shell input tap 1356 391`, `sleep 1`, then Save at `adb -s $HU shell input tap 1271 47`. These are the taps from `pr-1047-session-reconnect` round 3.
3. `sleep 20`, `adb -s $HU shell input keyevent 4`, `mark W3-close$k`, `sleep 30`.

Then `mark W3-end`, stop the captures, reset.

PASS, for each cycle:
- After `W3-save$k`: L-SAVE 1, L-SR 1 reading `route=NATIVE retry=run`.
- A new L-SSL within 15 s of L-SAVE. Round 3 measured 6.9 and 8.4 s.
- After `W3-close$k`: an L-TP with `rendered=` above 0 within 30 s.
- L-TLS sum 0 for the whole run.
- Phone: P-STATE is 1 or more after each `W3-save$k`.

If L-SAVE is missing, the taps missed. Take one screenshot, record it, and mark that cycle UNTESTABLE, not FAIL.

Report: L-SAVE to L-SSL in ms, per cycle.

### W4. The Disconnect verb ends the session and stays down

Setup: `WKEYS` on D-HU. Run steps 1 to 5 of W1 with label `W4`, then `sleep 30`.

1. `mark W4-disc`, `send ACTION_DISCONNECT`
2. `sleep 60`, `mark W4-end`. Stop the captures, reset.

PASS: after `W4-disc`, L-DISC 1, L-BYE 1, L-BYW 1 reading `SENT`, L-DEL 1, and no L-SSL until `W4-end`. Phone: P-BYE 1 or more.

Do not cycle D-POCO's Bluetooth in this run. Round 3's ND result (a poke already inside `socket.connect()` finishes) is known and not under test.

### U1. USB dongle session on D-POCO

Stage U. Setup: `UKEYS` on D-POCO. D-MOTO is the phone, and its radios are on. The dongle is on D-POCO's OTG port.

1. `HUCAP U1; PHCAP U1; mark U1-start`
2. `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`, `sleep 5`
3. `send ACTION_CHECK_USB`, `mark U1-usb`. Wait up to 60 s for L-SSL.
4. `sleep 180`, `mark U1-disc`, `send ACTION_DISCONNECT`, `sleep 20`
5. `send ACTION_CHECK_USB`, `mark U1-usb2`. Wait up to 60 s for L-SSL.
6. `sleep 30`, `mark U1-end`. Stop the captures, reset.

If the dongle never enumerates (no L-ACC within 30 s of a CHECK_USB), ask the operator to reseat it once. This is a known rig fault from the stack round. A second failure is UNTESTABLE.

PASS, all of these:
- L-ACC 1 or more and L-SSL 1 after `U1-usb`, within 60 s.
- L-TP lines with `rendered=` above 0 in 20 or more windows before `U1-disc`.
- After `U1-disc`: L-BYW 1 reading `SENT`.
- L-SSL 1 after `U1-usb2`, within 60 s.
- L-TLS sum 0, L-INC 0.
- Phone (D-MOTO): P-STATE 1 or more after `U1-usb`, P-FATAL 0.

Report: both SSL times. The `BootCompleteReceiver` crash count.

### H1. Headunit Server session on D-HP (API 17)

Stage H. This is the merged TLS code on bundled Conscrypt. Setup: `HKEYS` on D-HP. D-POCO is on the house WiFi with its head unit server open (pre-flight item 4).

1. `HUCAP H1; PHCAP H1; mark H1-start`
2. `adb -s $HU shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`. Wait up to 90 s for L-SSL.
3. `sleep 180`, `mark H1-disc`, `send ACTION_DISCONNECT`, `sleep 20`
4. `mark H1-end`. Stop the captures, reset.

PASS: L-SSL 1 within 90 s. L-TP with `rendered=` above 0 in 20 or more windows before `H1-disc`. L-TLS sum 0. After `H1-disc`: L-BYW 1 reading `SENT`. Phone: P-BYE 1 or more after `H1-disc`.

Install the APK on D-HP before H1 (R0 steps 2, 3 and 5 for this unit).

### Closing. Leave this build on every unit

**The operator asked for `main` to stay installed on all five units.** Do this last, after the results file is written.

For each of D-HU, D-POCO, D-MOTO, D-SAM and D-HP, plugged in turn:
1. Back up `settings.xml` if the app is installed (TESTING-TEMPLATE §1).
2. `adb -s <serial> install -r -d main-beta5-round1/<apk>`. If it fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, do not uninstall. Record it and move on.
3. Pull the installed APK, `md5sum` it, and compare it with R0.
4. `send ACTION_QUERY_STATE`. The commit must start with `ec9d9c33`.
5. Restore the unit's round-start `settings.xml` and read it back.
6. Leave the app stopped: `headunit://exit`, `sleep 3`, `force-stop`.
7. Leave the phones with airplane mode off, and WiFi and Bluetooth on. Check with `dumpsys`.

Report a table: unit, md5, commit, settings restored yes or no. This replaces the stack APK and the `usb-reattach` export build still on D-POCO.

## 9. Do not re-run

These passed on hardware on the same code, or cannot be built here:
- The stale USB accessory recovery and the no-screen reconnect hold (`5398058c`). The first needs a phone that fails its handshake in accessory mode. The second needs a projection screen that never comes up. Both are in the JVM tests.
- The USB Save retry behind the open settings screen. `usb-reattach` round 4 passed it. Main's `SettingsActivity` is not exported, and D-POCO's shell is not root, so it cannot be opened from adb.
- `bt-announce=skip`. `bluetooth-audio-disabled-usb-connect` round 5 passed the announce part.
- The wizard size and vehicle steps. `wizard-display-and-vehicle` round 1 passed them, and they need hand scrolling.
- The update link below Android 5.0. It needs a build with a lowered version name. `update-direct-apk-link-round1-brief.md` covers it and is still unrun.
- Nearby (Gearhead 17.9 refuses the helper) and the 3 s USB recheck (the dongle re-enumerates and masks it).
- Floating button opacity tap tests. `settings-defaults` round 2 passed them.

## 10. Report back

The numbers that decide whether beta5 ships:
1. W1: the fps median over the soak and the L-TLS sum. Expect 25 or more, and 0.
2. W2: L-BYW `SENT`, and no session for 60 s after Exit.
3. U1 and H1: SSL reached, and the L-TLS sum, per transport.

Results go in `main-beta5-regression-round1-results.md`. Captures go to the release `rig-evidence-main-beta5-regression` as `main-beta5-regression-round1-captures.zip` (TESTING-TEMPLATE §7).
