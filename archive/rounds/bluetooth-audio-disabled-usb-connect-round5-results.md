# bluetooth-audio-disabled-usb-connect, round 5 results

**Candidate:** `fix/bluetooth-audio-disabled-usb-connect` @ `fff96699480d878bb36bd04078d77260231b19dc` (measurement, no fix on trial)   **Baseline:** none (every comparison is between runs on the candidate)
**APK md5:** `234caaadff2d4731e006591a0c69bb2a` (candidate) / none
**Unit:** D-POCO (POCO X3 NFC, Android 15) is the USB head unit and runs the app; D-MOTO (Motorola edge 30 neo, Android 14) is the phone; D-HU (MT50) is a plain Bluetooth car unit with its app stopped. D-POCO and D-MOTO on wireless adb; D-MOTO on D-POCO's OTG port only inside a session.
**Date:** 2026-10-09
**Evidence:** release `rig-evidence-bluetooth-audio-disabled-usb-connect`, asset `bluetooth-audio-disabled-usb-connect-round5-captures.zip`, sha256 `53261d3925ed4ee2aa001d34427f2e0b91d98d658f45410918ce4c31b653d01f`

## Round verdict: INCONCLUSIVE

Neither control disabled A2DP (`C0` = `NO_DISABLE`, `C1` = `NO_DISABLE`), so the skip claim ("skip does not disable") is ungraded. The parts that need no control are graded below. Goal 1 (R2a to R3b) did not run, as the brief directs.

## Setup notes

- **Pre-flight** (`rig_preflight.sh`, first attempt): FAIL, D-MOTO Bluetooth off. The operator switched it on; second attempt `PREFLIGHT OK`:

  | Role | ADB | WiFi | BT | Screen | Profiles |
  |---|---|---|---|---|---|
  | D-HU | ok | 1 | 1 | Awake | HFP:none A2DP:none |
  | D-POCO | ok | 1 | 1 | Awake | HFP up A2DP up |
  | D-MOTO | ok | 1 | 1 | Awake | HFP:none A2DP:none |
- **Quirk files read:** `topics/tooling.md`, `units/D-HU.md`, `units/D-MOTO.md`, `units/D-POCO.md`.
- **Brief and template:** the transfer branch was force-updated mid-round ("trimmed"). The round 5 brief and the round 4 brief were byte-identical to the copies already read.
- **Build:** not built. `apks/candidate-fff96699.apk` md5 matched `WANT_MD5`. D-POCO carried another build (md5 `ed9dc2c9b9f2f17f04a6a69305c53274`); settings backed up first, then `adb install -r -d`. Installed md5 `234caaad...`. `settings.xml` identical before and after the install. `ACTION_QUERY_STATE` returned commit `fff96699480d`. D-HU's app was not installed or launched.
- **Settings delta against round 4's closing backups:** D-POCO: only `last-connection-usb-device` (Pixel 4 now, D-MOTO then) plus XML formatting. D-HU: empty-string formatting only. D-HU as found: `wifi-connection-mode` 3, `auto-start-bt-macs` empty (unchanged by the round; restored from backup).
- **`shared_prefs/` stat:** D-POCO `u0_a277:u0_a277` `drwxrwx--x`; D-HU `u0_a176:u0_a176` `drwxrwx--x`. Both correct.
- **Rig audio keys as found (D-HU backup):** `use-aac-audio=false`, `audio-latency-multiplier=8`, `audio-queue-capacity=20`. Not written. D-POCO's backup carries none.
- **Addresses:** `DHU_BT` 11:46:03:10:33:59 (Navegadortz2), `POCO_BT` DC:B7:2E:5E:4E:59, `MOTO_BT` A0:46:5A:97:E4:95, all as in round 3.
- **Stage P:** bonded list on D-MOTO lists both D-HU (Navegadortz2) and D-POCO; identical to round 4's closing `bonds-after.txt` (both copies); round 3's asset has no bonds file. Profiles were down (`A2dpService=none HeadsetService=none`). Repair: D-POCO Bluetooth off, D-HU Bluetooth cycle (no effect), then H10 by the operator: `A2dpService=Connected HeadsetService=Connected`. No re-pair (H7 did not run). Pre-read: switch for D-HU `ON`; last `Saved connectionPolicy 11:46:03:10:33:59 = 100` in the buffer was a MAP-profile line; `getProfileConnectionPolicy ... profile=9 ... = 100`.
- **Stage 0:** calibration values copied from round 4's `s0.env` (`ROUTE=resume`, `LABEL="Media audio"`, `A2DP_PLAY_RE`, `SINK_PLAY_RE`, `ROUTE_RE`, MP3 path). Not re-run. Gate met by the Stage P pre-read (a dump naming D-HU with the Media audio row `ON`). VLC and the MP3 were present on D-MOTO. Android Auto version not read, per brief.
- **Wireless adb:** D-POCO 192.168.1.8, D-MOTO 192.168.1.5, both port 5555. One `error: device offline` and one `device still authorizing` appeared in the run logs on a USB re-attach; both links recovered without a run being voided.
- **Harness:** `hur-wifi-test-scripts/bluetooth-audio-disabled-usb-connect-round5/`. Seven files: `lib1008r3.sh`, `lib1008r4.sh`, `lib1008r5.sh`, `ui1008.py`, `navxy.py`, `ohu_setkeys.py` (copied from round 4) and `lib1008-round5.sh` (from the brief). Also `hu_put.sh`, `stageP.sh` (sourcing order) and `run1.sh` (one run per script call under `flock /tmp/ohu-rig.lock`). Changes:
  1. `taps.log` renamed to `inputs.log` in the round 5 copies (as the brief says).
  2. `r5.env` carries `P2=$HU; POCO_NUM=3151710070`, because round 4's `lib1008r5.sh` treats `P2` as the second phone (D-POCO's own wireless serial here).
  3. Cue text "D-HP's OTG port" changed to "D-POCO's OTG port".
  4. `ui_open`: `am start` of `BLUETOOTH_SETTINGS` now passes `-f 0x10008000` (new task, clear task). In R0 and R0x D-MOTO's Settings was stuck on `Settings$UsbDetailsActivity` after the USB attach, so `ui_open` could not reach D-HU's screen. After the change `ui_open` worked in R1 and S1 to S4.
- **R0 was VOID** (`hu.ssl` = 2): the operator's unplug came about 40 s after the cue, the phone re-attached and formed a second session (08:53:24). Re-run as R0x. R0 data is kept below for the record.
- **Hand steps** (`hand-steps.log`): H10 once in Stage P; the H1 plug and unplug cue pairs for R0, R0x, R1, S1, S2, S3, S4; one H5 PULL cue (S2). No H11 (no pairing dialog appeared in any run). No H1 recovery in any run. The operator authorised starting at D-POCO battery 61 % instead of 80 %; it ended at 46 %.
- **Injected inputs** (`inputs.log`, 23 lines in all, at most 4 for any run): gear of Navegadortz2 for the screen route (H12) and one SIM chooser tap at 540,1384 (SIM 1) in S1.
- **Windowing:** phone counts are windowed by `<run>-start` to `<run>-end`; `hu.*` counts take the run's whole capture. `ph.restarts` of 2 to 5 is the number of phone logcat reconnects in a run, from `<run>.phone.logcat.restarts`.
- **Host thermal:** `thermal_max` 58 to 65 C, `throttle_delta` 0 in every run.
- **Close:** `lay1`, switch for D-HU read `ON` at close (09:18:53). D-POCO `settings.xml` restored from `settings_backup_poco.xml`: `bt-address`, `bt-announce`, `head-unit-make`, `head-unit-model` absent in both, as in the backup. D-HU `settings.xml` restored, `wifi-connection-mode` 3 and `auto-start-bt-macs` empty as found. D-POCO Bluetooth back on. `bonds-after.txt` lists both D-HU and D-POCO; bonded list unchanged. No logcat left running.

## Stage P, in a row

| Bond diff vs round 4 close | Profiles at start | Repair | Re-pair | Switch at `pre` | Last stored policy |
|---|---|---|---|---|---|
| none | A2DP none, HFP none | D-HU cycle, then H10 | no | `ON` | `100` (MAP-profile line), A2DP entry unset in the buffer |

## Controls

| Run | Layout | `ph.disabler` | `p.disabled_by_gh_before_stop` | `ph.route_off` | `ph.pairreq` | `live` switch |
|---|---|---|---|---|---|---|
| R0 (VOID, `hu.ssl` = 2) | 0 | 0 | 0 | 0 | 2 | `ON` (`UNREAD` after the second session) |
| R0x | 0 | 0 | 0 | 0 | 1 | `UNREAD` (screen not reachable) |
| R1 | 1 | 0 | 0 | 0 | 1 | `ON`, `policy=none` |

**`C0` = `NO_DISABLE`, `C1` = `NO_DISABLE`.** By the brief's table: D-MOTO changed since round 3, not the layout. R0's announced address was D-POCO's own with its Bluetooth on, as in round 3, and still no disabler ran; D-MOTO opened a pairing flow instead (`Bluetooth pairing method chosen: 2`, `: 4`, then `Sending a pairing request`).

## R0

**INCONCLUSIVE**

Not PASS or FAIL by the brief. Result `C0` = `NO_DISABLE` (from R0x; R0 itself was VOID).

- Settings: layout 0 keys, `bt-address=DC:B7:2E:5E:4E:59`, `bt-announce=real`.
- Announce, R0x: `1 carAddress=DC:B7:2E:5E:4E:59 (bt-announce=real)`; `hu.ssl` 1; `hu.media_audio` 1.
- `ph.disabler` 0, `p.disabled_by_gh_before_stop` 0, `p.P0` none, `p.reading` `OTHER` (no `setConnectionPolicy(` for the address), `ph.route_off` 0, `ph.pairreq` 1.
- Quoted, R0x: `08:56:40.091 D/CAR.BT.SVC.LITE: Bluetooth pairing method chosen: 4`, then `Sending a pairing request`.
- R0x switch readings `pre`, `live`, `end+5`, `end+60` all `UNREAD` (D-MOTO on the USB details screen); counted, not graded for R0.
- R0, VOID: `hu.ssl` 2 at 08:52:11.091 and 08:53:24.982; `ph.pairreq` 2.

## R1

**INCONCLUSIVE**

Control run. `C1` = `NO_DISABLE`.

- Announce: `1 carAddress=11:46:03:10:33:59 (bt-announce=real)`; `hu.ssl` 1; `hu.media_audio` 2.
- `ph.disabler` 0, `p.disabled_by_gh_before_stop` 0, `p.P0` `100` at 09:01:07.825, `ph.route_off` 0, `ph.pairreq` 1, `ph.unbond` 0.
- `live` (09:01:20.554): `state=ON checked=true sw_enabled=true row_enabled=true policy=none`. Readings `pre`, `live`, `end+5`, `end+60` all `ON`.
- Hold test: `SKIPPED_SWITCH_ON` (no tap, the switch is not off).
- `av live`: `moto_a2dp_play=0 dhu_sink_play=1 dhu_standby_no=0 music_dev=Devices: bt_a2dp(80)`; `av after-hold1`: `0 / 0 / 0`.
- Call route: `Entering state ActiveBluetoothRoute`, `ActiveEarpieceRoute`, `ActiveBluetoothRoute`, `ActiveEarpieceRoute`; `noroute` 0.

**R0 against R1:** `NO_DISABLE` / `NO_DISABLE`: D-MOTO changed since round 3. Not graded.

## Stage R (R2a, R2b, R3a, R3b)

**UNTESTABLE**

Skipped by the brief's rule: `C1` is not `DISABLES_SWITCH_OFF`, so there is no grey state to read. Not run.

## S1

**INCONCLUSIVE**

Announce, media and back-to-real parts graded below; the skip claim is ungraded.

- Announce: `1 carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)`; `hu.ssl` 1; `hu.media_audio` 2.
- `ph.disabler` 0, no `setConnectionPolicy(` for the address, `ph.route_off` 1, `ph.pairreq` 0, `p.P0` `100`.
- Readings: `pre` `ON`, `live` `ON` (09:05:48.334), `end+5` `ON`, 10 of 10 polls `ON`, `end+60` `ON`; `UNREAD` 0. All `policy=none`.
- `av live`: `moto_a2dp_play=0 dhu_sink_play=1 dhu_standby_no=0 music_dev=Devices: bt_a2dp(80)`.
- Call route: `ActiveBluetoothRoute` x3, `ActiveEarpieceRoute` x1; `noroute` 0.

## S2

**INCONCLUSIVE**

- Announce `1 carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)`; `hu.ssl` 1; `hu.live_at_stop` 1; `-gone` 09:09:49.384.
- `ph.disabler` 0, `ph.route_off` 1, `ph.pairreq` 0. Readings `pre`, `live`, `end+5`, 10 of 10 polls, `end+60` all `ON`.

## S3

**INCONCLUSIVE**

- Announce `1 carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)`; `hu.ssl` 1.
- `ph.disabler` 0, `ph.route_off` 1, `ph.pairreq` 0. Readings `pre`, `live`, `end+5`, 10 of 10 polls, `end+60` all `ON`.

## Stage S parts that need no control

| Part | Runs | Result | Evidence |
|---|---|---|---|
| Announce | S1, S2, S3 | **PASS** | each `1 carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)`, `getk` shows `"bt-announce":"skip"`, `Google`, `Desktop Head Unit` |
| Media over the cable | S1 | **PASS** | `hu.media_audio` 2 |
| No second copy | S1 at `live` | **INCONCLUSIVE** | `moto_a2dp_play=0`, `dhu_sink_play=1`, `dhu_standby_no=0`, `music_dev=Devices: bt_a2dp(80)`: the three fields disagree. The same `dhu_sink_play=1` with `moto_a2dp_play=0` reads in R1, S2, S3 and S4 (real and skip alike) |
| Back to real | S4 | **PASS** | `getk` `"bt-announce":"real"`, `1 carAddress=11:46:03:10:33:59 (bt-announce=real)` |
| Skip claim | S1, S2, S3 | **ungraded** | neither `C0` nor `C1` is `DISABLES*`; `ph.disabler` 0 in all three, same as the controls |

## S4

**PASS** (back to real); disabler not graded.

- Announce `1 carAddress=11:46:03:10:33:59 (bt-announce=real)`; `hu.ssl` 1; `hu.media_audio` 2.
- `ph.disabler` 0, `p.disabled_by_gh_before_stop` 0 (recorded, not graded, since `C1` is not `DISABLES*`). `ph.route_off` 1, `ph.pairreq` 1.
- Against R1: `live` state `ON` in both with `policy=none`; hold `SKIPPED_SWITCH_ON` in both; call route `ActiveEarpieceRoute`/`ActiveBluetoothRoute` in both with `noroute` 0; `ph.pairreq` 1 in both. Differences: `ph.route_off` 0 in R1, 1 in S4.

## Calls and bonds

| Run | Call route | `ph.pairreq` | `ph.unbond` | `ph.route_off` | Pairing dialog (H11) |
|---|---|---|---|---|---|
| R0 / R0x | none | 2 / 1 | 0 / 0 | 0 / 0 | none |
| R1 | Bluetooth/Earpiece alternating | 1 | 0 | 0 | none |
| S1 | Bluetooth x3 then Earpiece | 0 | 0 | 1 | none |
| S2 | none | 0 | 0 | 1 | none |
| S3 | none | 0 | 0 | 1 | none |
| S4 | Earpiece/Bluetooth alternating | 1 | 0 | 1 | none |

`ph.unbond_poco` and `ph.wrongdev` were 0 in every run. `ph.fatal_processes` empty and `hu.fatal` 0 in every run. `dh.wake` 0.

## Tables

`table.tsv` (`run layout hu.announce hu.ssl hu.media_audio ph.disabler ph.route_off ph.pairreq ph.wrongdev ph.unbond ph.unbond_poco h1`):

```
R0	0	2 carAddress=DC:B7:2E:5E:4E:59 (bt-announce=real)	2	2	0	0	2	0	0	0	0
R0x	0	1 carAddress=DC:B7:2E:5E:4E:59 (bt-announce=real)	1	1	0	0	1	0	0	0	0
R1	1	1 carAddress=11:46:03:10:33:59 (bt-announce=real)	1	2	0	0	1	0	0	0	0
S1	1	1 carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)	1	2	0	1	0	0	0	0	0
S2	1	1 carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)	1	1	0	1	0	0	0	0	0
S3	1	1 carAddress=SKIP_THIS_BLUETOOTH (bt-announce=skip)	1	1	0	1	0	0	0	0	0
S4	1	1 carAddress=11:46:03:10:33:59 (bt-announce=real)	1	2	0	1	1	0	0	0	0
```

`controls.tsv`: R0 `VOID`, R0x `NO_DISABLE`, R1 `NO_DISABLE`, S1 to S4 `NO_DISABLE`. `av.tsv` and `calls.tsv` lines are quoted per run above. No `toggle.tsv` Stage R lines (Stage R did not run). `hold.tsv`: R1 and S4 `H1 SKIPPED_SWITCH_ON`. `links.tsv`: `A2dpService=Connected HeadsetService=Connected` at every `pre` from R0 on.

## Closing: D-POCO's entry on D-MOTO

R0x `p.reading` `OTHER`, `p.END_at_end` `none`: the run produced no `setConnectionPolicy(` or `Saved connectionPolicy` line for D-POCO's address, so D-POCO's stored value is unread (not `100` confirmed). A clean session in layout 0 repaired it in round 2; that is a run for a later round.

## Anything the brief did not ask about

- **The pairing flow tracks the announced address, not the layout.** `ph.pairreq` was 1 or 2 in R0, R0x, R1 and S4 (real addresses) and 0 in S1, S2, S3 (skip). No profile disabler ran in any of the seven runs.
- **`disabling A2dp route while in projection`** (`ph.route_off`) read 0 in R0, R0x and R1 and 1 in S1 to S4, skip and real alike, so it does not follow the announce value. D-MOTO's Android Auto process id changed between S3 and S4 (12306, then 2498): S3 force-stopped it.
- **A switch that reads `ON` with `policy=none`** was constant in every readable reading; no `STALE_OR_ROM` case.
- **`dhu_sink_play=1` with `moto_a2dp_play=0` and `dhu_standby_no=0`** read in R1, S1, S2, S3 and S4, so the second-copy instrument cannot separate skip from real here. `after-hold1` in R1 and S4 read `0 / 0 / 0`.
- **D-MOTO's USB details screen** (`Settings$UsbDetailsActivity`) takes the foreground on a USB attach and blocked the first `ui_open` attempts in R0 and R0x (clear-task fix above).
- Android Auto's version on D-MOTO was not read this round (brief).
