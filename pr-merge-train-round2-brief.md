# pr-merge-train, round 2 brief: wireless lifecycle and Bluetooth routes (stage B)

Published name: `pr-merge-train-round2-brief.md`. Results go in `pr-merge-train-round2-results.md`.
Evidence: add `pr-merge-train-round2-captures.zip` to the release `rig-evidence-pr-merge-train`.

Units: D-HU (head unit), D-POCO (phone). D-MOTO, D-SAM and D-HP take no part; D-SAM's Bluetooth stays off.

Queue line: `pr-merge-train-round2-brief.md` (S `1b716181` on `fork/test/pr-merge-train`, B `f9a72d48` = `main`,
reuse round 1's B APK; units D-HU and D-POCO; X1 is the point).

Pairing: any round that does not use D-HU or D-POCO (§3b). Hand steps: none. Injected taps: none.

**Time budget.** Build 25 min, runs 20 min, thermal waits about 5 min. **About 50 min.** Build S in the same batch as
round 1's builds.

| Run | Arm | What it proves | Minutes |
|---|---|---|---|
| X0 | B, `main` | the control: Exit and an immediate Start, three times | 9 |
| **X1** | S | **the point: the same three cycles, and the old Exit's cleanup never stops the new start** | 9 |

## 1. Build and baseline

Stage B is round 1's stage A plus four PRs, merged in this order: cleanup tied to its arm, ZBT link state before a
reconnect, the XYAuto media policy, and WPP decisions in one snapshot. Two of them conflicted only in the host test
list `app/src/test/host-connection/run.py`; both entries are kept.

| Arm | Ref | Commit | Tree |
|---|---|---|---|
| S (candidate) | `fork/test/pr-merge-train` | `1b71618176668fe6ab01bcee7a80677a5ab0b34f` | `10b0734592f9f9b6a33e57388f61a9408cf62fd9` |
| B (control) | `main` | `f9a72d480d51105870f62df6f0a40a1276e98a34` | round 1's `apk-B.apk` |

The dev host ran the JVM suite at S: 4217 tests, 0 failures, 0 skipped. Do not run the unit tests on the rig. Build S as round 1's
`get_apk.sh` does, with this commit and tree, into `rig-data/rounds/pr-merge-train-round2/apk-S.apk`.

Identity on D-HU: `send ACTION_QUERY_STATE` carries the arm's commit, and for S,
`unzip -p live.apk 'classes*.dex' | strings | grep -acE 'WirelessCleanupGeneration|XyAutoMediaSession'` is 2 or more.
For B it is 0.

## 2. What this is and why

**The cleanup PR (X1).** A Start that comes while the previous Exit is still tearing down can be undone by that
teardown: on `main` the old cleanup can stop the newly armed launcher, remove its network or put the exit cooldown
back. A user on an HCT unit had to reconnect Bluetooth by hand after every Exit and Start. The PR ties each cleanup
to the arm it belongs to, so a newer arm makes the older cleanup a no-op. X1 sends Start 0.3 s after Exit, three times.

**The other three PRs** act only on units the rig does not have (a ZBT daemon, an XYAuto Bluetooth stack) or on a
group that changes during a handshake. On D-HU they must stay silent and change nothing; X1's capture grades that.

## 3. What is different about this round

- **X0 can pass.** The race depends on how long the old teardown takes. Each cycle records the gap between `<c>-start`
  and the old Exit's teardown line, so the results say whether the race was reached. If no X0 cycle reaches it, X0 is
  INCONCLUSIVE as a control and X1 still grades.
- `ACTION_START_WIRELESS_SCAN` here follows an Exit, not a launch, so §4 step 4 does not apply.
- Read `rig-quirks/topics/lifecycle.md` and `rig-quirks/topics/wifi.md` beside round 1's list.

## 4. Settings keys

Round 1's R0 keys, unchanged. Back up and diff `settings.xml` as round 1 does.

## 5. Helpers

**lib2.sh**

```bash
OUT=rig-data/rounds/pr-merge-train-round2; . ./lib.sh      # lib.sh from round 1
# cycle <id>: from a live session, Exit, then Start 0.3 s later; wait for the next session
cycle() { local C=$1
  mark $C-start; send ACTION_DISCONNECT; sleep 0.3; mark $C-go; send ACTION_START_WIRELESS_SCAN
  waitn $C-go "SSL handshake complete" 1 120 || echo "$C NO_SESSION" >> $OUT/marks.txt
  waitn $C-go "Throughput over" 1 30; sleep 20; mark $C-end; }
# run <R>: one launch, a first session, three cycles, one clean exit
run() { local R=$1
  th_gate $R || return 1; ph_off; adb -s $D shell am force-stop $PKG
  launch; sleep 2; mark $R-start; ph_on
  waitn $R-start "SSL handshake complete" 1 120 || { echo "$R NO_SESSION" >> $OUT/marks.txt; mark $R-end; th_end; appexit; return 1; }
  waitn $R-start "Throughput over" 1 30; sleep 15
  for i in 1 2 3; do cycle $R-c$i; done
  mark $R-end; send ACTION_DISCONNECT; sleep 5; th_end; appexit; }
```

Start the two captures as round 1's `capture.sh` does, with this round's `OUT`.

## 6. The deciding lines

All verified with `grep -F -r` in `app/src/main`, at `1b716181` and at `main`, except L-WD, L-EP and `XYAuto`,
which exist only at `1b716181`. Grep with `-a`. A cycle's window is `<c>-start` to `<c>-end`.

| Id | Line | Read |
|---|---|---|
| L-EXIT | `AapService: Native AA user exit. Stopping active launcher.` | the old Exit's cleanup |
| L-GRP | `AapService: CommManager teardown complete. Stopping WiFi Direct group.` | the old Exit's group removal |
| L-ARM | `WifiLauncher: Initializing WiFi Mode:` | the new Start armed |
| L-SSL | `SSL handshake complete` | a session formed |
| L-TP | `Throughput over` | `rendered=N` |
| L-T3 | `NativeAA: [TX] Sending WifiInfoResponse (Type 3)` | credentials sent |
| L-CS | `NativeAA: [RX] WifiConnectStatus status=` | the phone joined: read `SUCCESS(0)` |
| L-WD | `WiFi credentials were withdrawn, incomplete, moved` | snapshot PR: an exchange ended |
| L-EP | `the WPP endpoint went out for` | snapshot PR: an exchange ended |
| L-ZX | `[ZBT]` and `XYAuto` | the module PRs acted |

FAIL lines in any window: `FATAL EXCEPTION` in a block naming `com.andrerinas`. Phone, between RIGMARK lines:
`Critical error` MUST be 0.

## 7. Runs

Install B, `run X0`. Install S (identity check), `run X1`. Stop both captures; restore `settings.xml`.

Stop rule: retry a run once if its first session never forms. Stop the round after 2 runs with no first session.

For every cycle `c1` to `c3`, record: t(`<c>-go`) to the first L-ARM after it, to the first L-SSL after it, and the
time of every L-EXIT and L-GRP in the window **relative to that L-ARM** (negative is before it).

- **The race was reached** in a cycle when its first L-EXIT or L-GRP comes less than 2 s before that L-ARM, or after it.
- **X0:** record, per cycle, whether the race was reached and whether a session formed. A cycle with an L-EXIT or
  L-GRP after the L-ARM, or with no L-SSL, is the defect shown on `main`.
- **X1 PASS** when all three cycles have: an L-ARM within 5 s of `<c>-go`; no L-EXIT or L-GRP after that L-ARM; an
  L-SSL within 120 s of `<c>-go`; a last L-TP with `rendered=` > 0. And, over the whole `X1` window: every L-T3 is
  followed by an L-CS reading `SUCCESS(0)` before the next L-T3, and L-WD, L-EP and L-ZX are each 0.

## 8. Do not re-run

Round 1's audio and decoder runs.

## 9. Report back

1. Per cycle, X0 and X1: race reached (yes or no), L-EXIT or L-GRP after L-ARM (count), and t(`<c>-go`) to L-SSL.
2. X1's L-WD, L-EP and L-ZX counts.
