# link-stall-periodic-scan — round 5 brief: does a WiFi scan blank the link?

**Candidate:** `fork/fix/wifi-direct-lifecycle` @ `224cae32` — **552 JVM tests**. One APK for the whole
round. It sits on top of `fix/p2p-legacy-5ghz` (`7ec828ba`), which sits on `fix/video-stack`
(`b79a2b69`), so building this branch builds all three. **Fetch and reset, do not pull** — the base
was rewritten on 2026-08-19.

**Priority: run this before round 4.** Round 4 is still queued and still valid, but its band arm is
now the weaker of the two hypotheses and it is inert on the reporters' own units (they are API 27,
where the band lever does nothing). This round tests something that has never been controlled in any
round of this thread, on either side.

---

## 1. Why this round exists

Every round so far has varied the codec, the decoder, the memory profile and the band. None of them
can make the radio leave the group's channel for seconds at a time. **A WiFi scan can**, and the
numbers come from the vendor of the reporters' own silicon.

MediaTek's Gen3 WLAN driver — which is what `ac8227l` runs — carries this in
`include/mgmt/scan.h`:

```c
#define SCN_BSS_DESC_STALE_SEC 10   /* 2.4G + 5G need 8.1s */
```

That is **MediaTek stating that a full dual-band scan takes ~8.1 s on this driver**, with results
treated as stale after 10 s. `roaming_fsm.h` adds `ROAMING_DISCOVERY_TIMEOUT_SEC 5`. A 10 s
stale-refresh with a partial sweep gives an ~11.6 s period; a truncated 8.1 s sweep gives 5-6 s gaps.
Both reporter waveforms fall inside that envelope:

| Report | Dead | Period | Dead time |
|---|---|---|---|
| #824 | 1.59 s | 11.57 s | 14.1 % over 487.7 s |
| #839 | 5.96 / 6.11 / 5.17 s | 10.05 and 11.19 s apart | 78 % |

While a scan runs, the single radio is off the group's channel and **everything stops at once** —
which is the observed signature: picture and sound together, every channel, decoder clean,
`dropped=0`, `fed == rendered`.

**The two obvious alternatives are ruled out on magnitude, not on taste.** MCC and NoA schedule
absences against the beacon interval (~100 ms), so time-slicing costs throughput and tens of ms of
jitter, never contiguous seconds. Bluetooth/WiFi coexistence arbitration (PTA) works at
sub-millisecond to ~16 ms. Neither can produce a multi-second silence. This is the first mechanism
proposed on this thread whose timescale actually matches the fault.

**And there is a confound in the reporter evidence that this round exists partly to break.** Across
#839's five captures, station association and codec co-vary *perfectly*: the clean capture had the
station associated at 5745 MHz **and** ran H.265; every sick capture had no WiFi connected **and** ran
H.264. Round 3's codec finding and this scanning theory fit exactly the same evidence. Note the
direction is counter-intuitive — an **unassociated but enabled** station scans *more* than an
associated one, so "I disconnected my WiFi" predicts worse, not better, which is what the reporter
observed when he tried it.

**This rig has never recorded its own station state in any round.** It is the same uncontrolled
variable the codec was before round 3.

---

## 2. Read this before planning the runs — what this rig can and cannot prove

**The 8.1 s constant is MediaTek's, and this rig is UNISOC on Android 14.** Its scan cadence and its
concurrency handling are its own. So:

- **A clean result here does NOT refute the theory for the reporters' units.** Say that explicitly in
  the results. If the rig shows no gaps in any arm, the correct conclusion is "this rig's radio does
  not blank its P2P group for scans", not "scanning is not the cause of #839".
- **What the rig can decide** is whether scanning perturbs a live P2P link *at all* on hardware we
  hold, and by how much. A *small but real* gap aligned with a scan validates the mechanism even at a
  fraction of the reporters' magnitude, and that is a genuine finding.
- **The rig can also test the mitigations safely**, which matters because we cannot ask two reporters
  to try settings we have never run ourselves.

**The rig's default station state is probably already the suspected sick one.** WiFi must be on for
WiFi Direct to work at all, but "on" does not mean "associated" — and an enabled, unassociated
station is the one that scans hardest. If that is how every previous round ran, then five clean
rounds are already weak evidence against the mechanism *on this hardware*. Record the state; do not
assume it.

**Do not turn WiFi off to test this.** On most devices that tears down the P2P group with it. The
station-off arm is `wifi_scan_always_available`, not `svc wifi disable`.

Standing rules that apply: `grep -a` without exception (§7a); `set_hu_prefs.sh` for multi-key writes;
**head unit up before the phone** (§4); capture unfiltered with `stdbuf -oL` (§2) — the scan lines
this round needs are framework lines, so a tag filter would throw away the entire measurement.

Round 3's discard-rule finding stands: one `MATCH! Starting AapService` per capture and two
`p2p-wlan0-N` values in a capture spanning a previous teardown are **benign**, as long as
`AapService.onCreate` and `createGroup SUCCESS!` each verify to exactly one occurrence.

---

## 3. The measurement

`recv_gaps.py` and `wire_bitrate.py` are in `hur-wifi-test-scripts/` from rounds 3-4. Run both on
every capture. Carry forward round 4's three reporting rules: **AUDIO coverage** per run
(`wire_bitrate` AUDIO kB/s ÷ `recv_gaps` kB/s — round 3's R1 was ~78 % and nobody noticed), Spotify
confirmed `state=PLAYING` **before** each capture starts, and the `inbound link quiet` count from
`LinkGapMonitor` beside the script's answer.

**The new instrument is a grep, not a script.** Because §2 already requires an unfiltered capture,
every scan the framework runs is already in the logs we take. What is new is looking for it:

```bash
grep -anE "startScan|WifiScanRequestProxy|WifiScanningService|SCAN_RESULTS|scan results|ScanRequestProxy|WifiNative.*[Ss]can" rN.txt | head -60
```

The exact tags vary by vendor and Android version — **record verbatim whichever ones this unit
actually prints**, and put the list in Setup notes so the next round can grep for the right thing
instead of guessing. Then extract just the timestamps of the scan markers you settled on and compare
them against `recv_gaps.py`'s stall list.

**Add one script:** `hur-wifi-test-scripts/scan_vs_gaps.py`, saved for reuse.

```python
#!/usr/bin/env python3
"""scan_vs_gaps.py <capture.txt> <scan_regex> [stall_threshold_s]

Aligns inbound RECV stalls against WiFi scan markers in the same capture.
Prints each stall with the offset to the nearest scan marker; a mechanism that
blanks the radio shows a tight cluster of small positive offsets.
"""
import sys, re, statistics

path = sys.argv[1]
scan_re = re.compile(sys.argv[2])
thr = float(sys.argv[3]) if len(sys.argv) > 3 else 1.2

def secs(tok):
    h, m, rest = tok.split(':')
    return int(h) * 3600 + int(m) * 60 + float(rest)

recv, scans = [], []
for line in open(path, errors='ignore'):
    parts = line.split()
    if len(parts) < 2:
        continue
    try:
        t = secs(parts[1])
    except (ValueError, IndexError):
        continue
    if scan_re.search(line):
        scans.append((t, line.strip()[:90]))
    if 'RECV: ' in line:
        recv.append(t)

if not recv:
    sys.exit('no RECV lines — was log-level=0 set?')
recv.sort()
stalls = [(recv[i], recv[i + 1]) for i in range(len(recv) - 1)
          if recv[i + 1] - recv[i] > thr]

print('scan markers %d, stalls > %.1fs %d' % (len(scans), thr, len(stalls)))
if scans:
    gaps = [scans[i + 1][0] - scans[i][0] for i in range(len(scans) - 1)]
    if gaps:
        print('scan interval  median=%.1fs mean=%.1fs min=%.1fs max=%.1fs'
              % (statistics.median(gaps), statistics.mean(gaps), min(gaps), max(gaps)))
if not scans or not stalls:
    sys.exit(0)

offsets = []
print('\nstall start -> nearest scan marker (negative = stall began before the marker):')
for a, b in stalls:
    near = min(scans, key=lambda s: abs(s[0] - a))
    off = a - near[0]
    offsets.append(off)
    print('  stall %8.3f dur %5.3f   scan %+7.3fs  %s' % (a, b - a, off, near[1]))

close = [o for o in offsets if abs(o) <= 2.0]
print('\nstalls within 2s of a scan marker: %d/%d (%.0f%%)'
      % (len(close), len(offsets), 100 * len(close) / len(offsets)))
```

---

## 4. Runs

Common to every measured run: `log-level=0` (VERBOSE), `wifi-connection-mode=3`, `view-mode=0`,
`fps-limit=60`, `force-software-decoding=false`, `video-codec=H.264`, no fault-injection keys, no
`debug-force-p2p-band-24`, no `debug-force-memory-profile`. Spotify playing throughout, AA surface
swiped every 20-30 s with the count recorded, **ten minutes each**.

**Record the station state for every run, from the log, not from intent:**

```bash
adb shell dumpsys wifi | grep -aiE "mWifiInfo|SSID|Supplicant state|Frequency" | head
adb shell cmd wifi status
adb shell settings get global wifi_scan_always_available
```

### R0 — gate and preconditions

**PASS / FAIL — a FAIL stops the round.**

- `build_hur.sh`, `run_unit_tests.sh`. **Expect 552.** Record the APK md5.
- Back up `settings.xml`. Restore and diff at the end.
- **Regression check on the API 29+ band path**, which this stack must leave alone: a short launch
  must still log `Requesting Native AA P2P group on 5GHz band.` followed by `5GHz createGroup
  SUCCESS!`, and **no** line mentioning an operating channel. The new pre-Q code is unreachable on
  Android 14; if any of it fires here, stop and report it.
- Confirm the scan grep finds *something* in a short capture, and record which tags. If nothing in
  §3's pattern ever appears on this unit, say so — the round becomes UNTESTABLE for R1-R3 and only
  R4 can run.

### R1 — station enabled, not associated

The suspected sick configuration, and probably what every previous round ran without recording it.
WiFi on, **not** joined to any network. Verify with `cmd wifi status` before starting.

Report the full instrument set plus `scan_vs_gaps.py`.

### R2 — station associated to a 5 GHz network

Same, but joined to a 5 GHz AP before launching the head unit app. This is the configuration the one
**clean** reporter capture was taken in.

The comparison against R1 is the round's primary result: **scan-marker interval, stall count, dead
time, and the fraction of stalls within 2 s of a scan.**

### R3 — scanning suppressed

R1 again with scanning turned down:

```bash
adb shell settings put global wifi_scan_always_available 0
```

Verify it took (`settings get`), and **restore it at the end of the round** — record that you did.
If the unit ignores the setting (scan markers keep appearing at the same cadence), that is the
result: report it and do not spend time forcing it.

### R4 — the lifecycle fixes, which only this rig can test

Not part of the scanning question; it is here because the candidate carries `fix/wifi-direct-lifecycle`
and these paths need a live P2P stack. Two checks, no measurement:

1. **Re-arm after a user exit.** Connect a session, exit AA through the app (not force-stop), let the
   phone reconnect. Expect the second session to work, and expect `WIFI_P2P_CONNECTION_CHANGED`
   handling to still be alive — the phone joining must be seen. Before this branch the receiver never
   re-registered, so the join watchdog tore down a group the phone had joined. Report whether a
   second session forms and whether any `Native AA join watchdog` teardown fires against a joined
   group.
2. **The manual poke button after a completed session.** With a session established and then ended
   without a clean disconnect, press poke. Expect `Native AA listeners are closed — re-arming before
   the poke.` followed by listeners reopening. Before this branch the button did nothing at all.

**PASS** = both behave as described. A FAIL here is more important than anything in R1-R3.

---

## 5. Pre-registered outcomes — decided now

**The mechanism is present on this rig if**, in R1: stalls > 1.2 s exist **and** ≥ 60 % of them start
within 2 s of a scan marker **and** the scan-marker interval is close to the stall period. All three,
not any one — a coincidental alignment on a handful of stalls is not evidence.

**The mechanism is absent on this rig if** R1 has zero stalls > 1.2 s, or stalls that show no
alignment with scans (< 30 % within 2 s). **This is the expected outcome**, given five clean rounds,
and it is a PASS of R1, not a FAIL. Report it as a number and move on.

**R2 is the interesting comparison either way.** If associating the station measurably reduces scan
markers *and* R1 had any stalls, that reproduces the reporter's own clean-vs-sick split on hardware
we control, and it is the strongest result this round can produce.

**If R3 suppresses scans and R1 had stalls that then disappear**, we have a mitigation we can hand to
two reporters with a straight face. If R3 suppresses scans and nothing changes because there was
nothing to change, say exactly that.

**What no result here can do:** refute the theory for `ac8227l` on Android 8.1. The 8.1 s constant is
MediaTek's and this rig is not that silicon. A clean round narrows the claim to "not universal", and
that is all.

---

## 6. Report back

1. R0's regression check on the API 29+ band path, quoted verbatim, and the scan tags this unit
   actually prints.
2. Per run: `recv_gaps.py`, `wire_bitrate.py`, AUDIO coverage, underruns and rate/min, `inbound link
   quiet` count, swipes issued, throughput windows, **and the station state as read from the device**.
3. `scan_vs_gaps.py` output for R1, R2 and R3, with the scan-marker interval.
4. The three §5 criteria, answered one by one.
5. R4's two checks, PASS/FAIL each, with the log lines.
6. Whether `wifi_scan_always_available` took, and confirmation it was restored.
7. The discard-rule counts for every capture.
8. Anything this brief did not ask about.
