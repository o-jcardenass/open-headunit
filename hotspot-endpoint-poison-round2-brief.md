# hotspot-endpoint-poison: round 2 brief

## 1. Build and baseline

- Branch `fix/hotspot-endpoint-poison` on the fork, tip **`30a1f6d8`**, four commits on `main`
  `dd454eed`. Round 1's `018d5af6` plus one commit; never rewritten.
  ```bash
  git fetch fork fix/hotspot-endpoint-poison
  git checkout -B fix/hotspot-endpoint-poison fork/fix/hotspot-endpoint-poison
  git rev-parse --short HEAD   # 30a1f6d8
  ```
- JVM gate: **2339 tests, 0 failures** on this SHA from the author's side. Re-run with
  `run_unit_tests.sh`.
- DEX identity: `SoftApEndpointStabilityPolicy` and `SoftApAutoEnablePolicy` must be in the APK
  (template §5). This commit adds no log line of its own, so `ACTION_QUERY_STATE`'s commit is what
  tells it apart from round 1's APK.
- No baseline APK this round: P1 reproduced the bug in round 1.

## 2. What round 1 found, and what changed

Round 1 (`hotspot-endpoint-poison-round1-results.md`) proved the fix itself: P1 reproduced the
stale endpoint on `main`, and R2/R3 withheld it on the candidate on both an unproven and a moved
address. R5 and R8 step 2 failed, and reading their timestamps against the code found **one defect
of ours** under a rig limit:

- **The retry asked over attempt 1 and was dropped.** A `HotspotManager` start blocks for about 20 s
  on D-HU (8 s radio settle, then 6 s per start path). The handshake restarts the resolve loop every
  ~10 s, the new loop saw "not succeeded" as "finished", and fired attempt 2 while attempt 1 was
  still running. `HotspotManager` refused it as a second start, which is why `the hotspot did not
  come up on attempt 2` printed 5 to 7 ms after `(attempt 2 of 2)` in both runs. The retry now waits
  for attempt 1 to return and is spaced 10 s from that return.
- **After ACC-on the first attempt fired within 1 s**, because the wait was counted from the run's
  start before the sleep. The reporter's unit restores its own AP about 4 s after
  `xy.android.acc.on`, so an ask then races the firmware. The first attempt after a wake now waits
  15 s, and each wake gets a fresh two-attempt budget.
- **The AP not coming up from the app is D-HU, not the candidate.** `main` did the same in
  `narrow-band-and-disconnect-scope` round 1 (both asks gave up, `wlan2` appeared late). So this
  round records whether the AP comes up and does not grade it.

## 3. What is different about this round

- **Units, readers, settings keys and the bring-up recipe are round 1's §3 and §4**, with these
  corrections from its setup notes:
  - `cmd wifi get-softap-config` does not exist here: read the persisted config from
    `/data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml` as root (round 1 read
    `Navegadortz2` / `12345678`).
  - Never `am force-stop` right before an `adb reboot` that R5 needs: it leaves the app in the
    stopped state and `BOOT_COMPLETED` is not delivered. Stop it with `ACTION_STOP_SERVICE` and
    check `dumpsys package com.andrerinas.headunitrevived | grep stopped=` reads `false`.
  - The first `start-softap` after every reboot comes up degraded on D-HU; one
    `stop-softap`/`start-softap` cycle clears it (template §7a).
  - Raise the buffer with `logcat -G 16M` on both units first, and if a background capture dies,
    take `logcat -d` straight after the step rather than rerunning it.
- **Round 1's §4 key list applies**, including the clears before R2. The banner keys this round
  reads are `connection-issue-stale-endpoint` (R4) and `connection-issue-hotspot-off` (R5, R8).

## 4. The lines that decide every run

Round 1's §5 table applies. New or changed for this round, each checked with `git grep -F` on
`30a1f6d8`:

| Line (substring) | Meaning |
|---|---|
| `SoftApCredentials: No access point after` … `(attempt N of 2)` | an auto-enable attempt started |
| `the hotspot did not come up on attempt N` | **that same** attempt returned without an AP; N now names the attempt that ran |
| `HotspotManager: Every start path was tried on` | a start gave up (the end of an attempt) |
| `HotspotManager: A hotspot start is already running` | a second start asked over the first; **must be zero** this round |
| `HotspotManager: Setting hotspot enabled=true` | a start entered `HotspotManager` |
| `WakeDetect: xy.android.acc.on` | the ACC-on broadcast arrived |
| `NativeAA: the WPP endpoint advertised on the access point at` … `no longer matches (password)` | R4's banner line |

## 5. Runs

### R0 Build gate
SHA `30a1f6d8`, DEX identity per §1, JVM gate 2339/0, `adb install -r` on D-HU, `ACTION_QUERY_STATE`
reports the commit. PASS: all four.

### R2+R3 Quick recheck (no regression on the fix round 1 proved)
Keys cleared per round 1 §4, AP up from the shell. One bring-up, then `adb reboot`, the AP cycle from
§3, one bring-up. PASS: the first reads `stable=unproven` and the second `stable=no` with `its
address moved from` (or `stable=unproven` with `not yet seen across a restart` if the address held),
no `advertising WPP over TCP` on either, and both reach `SSL handshake complete`.

### R4 A moved password raises the banner (made testable)
Round 1 could not reach this because nothing was ever advertised. Seed the advertised record by hand
instead:
1. AP up from the shell (`OHU-HOTSPOT` / `ohutest12345`), one bring-up to a session. Note the
   identity line's `ssid=`, `bssid=` and `ip=`.
2. `am force-stop` (the verdict is latched per process, so the check only runs in a fresh one), then
   write four string keys: `soft-ap-advertised-ssid` = the `ssid=`,
   `soft-ap-advertised-bssid` = the `bssid=`, `soft-ap-advertised-ip` = the `ip=`, and
   `soft-ap-advertised-psk-digest` = `0000000000000000000000000000000000000000000000000000000000000000`
   (64 zeros: a password digest that cannot match). Set `connection-issue-stale-endpoint` = `0`. Read
   all five back.
3. One bring-up.

PASS, all of:
- `NativeAA: the WPP endpoint advertised on the access point at <ip> no longer matches (password);`
  once, with **only** `password` in the brackets;
- `connection-issue-stale-endpoint` non-zero, and the four `soft-ap-advertised-*` keys gone, read
  after a force-stop;
- a second bring-up prints no `no longer matches` line.

If the change did nothing, there would be no `no longer matches` line and the counter would stay 0.
The phone holds no endpoint here, so it is expected to connect normally over Bluetooth; that is not
graded.

### R5 The app switches the hotspot on after a boot
As round 1's R5 (keys `Navegadortz2` / `12345678`, `auto-enable-hotspot=true`,
`auto-start-on-boot=true`, stopped state checked per §3), `stop-softap`, `adb reboot`, nothing by
hand, watch 3 minutes from boot. If the capture fails, a manual re-arm (`ACTION_STOP_SERVICE`, then
`ACTION_START_WIRELESS --ez no_ui true`) runs the same code and is acceptable; say which was used.

PASS, all of:
- `(attempt 1 of 2)`, then **either** `SoftApCredentials: SUCCESS` **or**, in this order,
  a `HotspotManager` give-up (`Every start path was tried on` or `All hotspot attempts failed`),
  `the hotspot did not come up on attempt 1`, and
  `(attempt 2 of 2)` **at least 10 s after that give-up line**;
- **zero** `A hotspot start is already running` lines;
- exactly one `HotspotManager: Setting hotspot enabled=true` per `(attempt` line, plus one per
  `Re-enabling the hotspot we started` line if the AP came up and dropped;
- `ACTION_EXPORT_LOG` afterwards carries `autoHotspot:on`.

Record but do not grade whether the AP came up (`wlan2` present, `SoftApInfo`) and when, including a
late appearance after attempt 2 gave up. Report each attempt's start, its give-up line and the gap.

If the change did nothing, `(attempt 2 of 2)` would land about 12 s after attempt 1, before its
give-up line, and be followed within 10 ms by `did not come up on attempt 2` and an
`already running` line.

### R8 The car switched off and on (XYAuto broadcasts)
Setup as round 1's R8: hotspot route, `auto-enable-hotspot=true`, AP up from the shell, a session
live.
1. `am broadcast -a xy.android.acc.off`, then `cmd wifi stop-softap` within 2 s. Watch 60 s.
2. `am broadcast -a xy.android.acc.on`. Watch **60 s**.
3. Repeat step 1, then `am broadcast -a xy.android.acc.on` and **within 5 s** `cmd wifi
   start-softap OHU-HOTSPOT wpa2 ohutest12345 -b 5` (the firmware restoring its own AP). Watch 60 s.

PASS, all of:
- step 1: `WakeDetect: ACC off (xy.android.acc.off)`, zero `(attempt` lines, and
  `connection-issue-hotspot-off` unchanged (the same as round 1, which passed);
- step 2: `WakeDetect: xy.android.acc.on`, **no** `(attempt` line in the first 15 s after it, then
  `(attempt 1 of 2)`; if it fails, attempt 2 follows the R5 rule. Whether the AP comes up is recorded,
  not graded;
- step 3: **zero** `(attempt` lines, and `SoftApCredentials: SUCCESS` on the shell's AP.

If the change did nothing, step 2 would print `(attempt 1 of 2)` within 2 s of the ACC-on line (as
round 1 measured at 16:02:46), and step 3 would ask for the AP over the one the shell just started.

### R7 WiFi Direct, no regression
Revert per round 1 §4. One bring-up. PASS: `WifiDirectManager: group identity ssid=` present, no
`access point identity` line, `SSL handshake complete`.

## 6. Do not re-run

R1 (the address measurement), P1 (the baseline reproduction) and R6 (setting off) passed or were
measured in round 1 and are untouched by this commit.

## 7. Report back

1. R5: each attempt's start, give-up line and the gap between them, and the count of
   `already running` lines (the answer is zero).
2. R8 step 2: the seconds from `WakeDetect: xy.android.acc.on` to `(attempt 1 of 2)`.
3. R8 step 3: the attempt count (the answer is zero).
4. R4: the `no longer matches` line verbatim.
