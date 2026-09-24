# Periodic link stall — round 1 brief: does a WiFi scan produce the reporter's waveform?

**Build:** `origin/main` @ `a8830caa`. **No candidate branch and no A/B** — this round measures the
rig's behaviour against unmodified `main`, so one APK is all that is needed.

```bash
git fetch origin && git checkout main
git merge --ff-only origin/main
git rev-parse --short HEAD          # expect a8830caa
```

Read `TESTING-TEMPLATE.md` first. This brief restates nothing from it.

---

## 1. What this is and why it exists

A reporter on a Spreadtrum `sp7731e_1h10` (Android 8.1, API 27) hears his music pause on a fixed
cadence for the whole of a drive. His VERBOSE capture says the fault is not in the audio path at all:
**the entire inbound AAP link goes silent, on every channel at once, and then delivers the backlog in
one burst.**

Measured over every `RECV:` line in his 11-minute Native AA session. This is `recv_gaps.py` (§5) run
on his capture — the same script and the same output format this round reports back in, so the
numbers are directly comparable:

```
RECV lines            42140 over 487.7s
stalls > 1.2s         41   dead time 68.7s = 14.1%
stall duration         n=41  median=1.590s mean=1.677s sd=0.373
quiet interval         n=40  median=9.986s mean=9.884s sd=0.795
period (start-start)   n=40  median=11.570s mean=11.562s sd=0.693
audio delivered       177.8 kB/s (48k/16/2 needs 192.0) = 92.6% of real time
```

The duration sd of 0.373 is carried by two double-length outliers; across the 36 cycles without one,
the stall is **1.591 s with an sd of 0.078 s** — remarkably fixed.

Inside a stall window his log has zero lines from any thread — no video, no audio, and no
`CONTROL Ping Request`, which his phone sends once a second. The process is demonstrably alive
throughout: `Throughput over Nms:` keeps printing `5002ms`, `5007ms`, `5003ms`, never long. Only
bytes stop.

**The shape is the finding.** The operation takes a very consistent 1.591 s and the gap *between*
operations is 9.99 s — the ten-second constant sits *after* the operation completes, not around it.
That is a scan loop rearmed on completion, and a 1.4-1.7 s sweep is what a 2.4 GHz channel scan
costs. It is not his phone idling and not the app: nothing in `AapService`, `AapTransport` or
`connection/` runs anywhere near that cadence, and his capture contains no `Discovery active` line.

**What this round is for.** His capture can never name the scanner. `LogExporter.kt:85` runs
`logcat -v threadtime *:V` with no PID filter, but Android hands an unprivileged app only its own
UID's entries — 151,365 of the 151,366 lines in his file are his own process. This rig has adb, so
here the scanner is visible. The question the round answers is:

> Does a periodic WiFi scan — on the head unit, on the phone, or as a side effect of the phone
> hosting its own access point — reproduce that waveform on a live Native AA session?

A positive proves the mechanism. A negative does **not** clear his unit (see §2).

---

## 2. What is different about this round

- **The rig is not his hardware.** UNISOC MT50, Android 14, versus Spreadtrum SC7731E, Android 8.1.
  Scan-versus-P2P concurrency is precisely where those two diverge most: Android 14 may defer or
  refuse connectivity scans while a P2P group is up, where 8.1 largely does not. **A null result here
  is a fact about this rig, not about his unit.** Record it as such and do not read it as refuting
  the diagnosis.
- **Every run after R0 is gated on R0.** `cmd wifip2p` is already known to be refused for uid 2000 on
  this rig (§7a), so `cmd wifi start-scan` may well be refused too. R0 establishes which levers exist
  before any measurement depends on one. The fallback lever is in R0 itself.
- **R4 needs a tap on the phone**, and that is allowed: the no-UI rule governs *this app's* settings
  list, not the phone's system settings. Say in Setup notes that it was done by hand.
- **There is no run for our own discovery leak, deliberately.** `WifiDirectManager`'s
  `discoveryRunnable` (`:70-82`) re-arms every 10 s on `!isClientConnected`, which on the Native AA
  path never becomes true — the file says so itself at `:110-111`. Nothing on that path calls
  `handler.removeCallbacks(discoveryRunnable)` or `stopPeerDiscovery()`, and `initWifiMode()` skips
  `stop()` on a helper→native switch because `WifiModePolicy.usesWifiDirect` is true for both modes.
  That is a real defect. It is **not testable here**: arming it needs a runtime mode switch, and a
  settings change requires the app stopped, which drains the very Handler that would hold the leaked
  runnable. There is no exported action that changes the wireless mode. That coverage goes to a JVM
  test and a code fix, not to this rig. R5 asks only for the cheap negative check that the analysis
  rests on.
- **Stream the capture, do not dump it.** §7a warns the driver stack wraps the ring buffer inside a
  run; these runs are 5-6 minutes each. Streaming with `stdbuf -oL` (§2) sidesteps that entirely.
  Grow the buffer anyway as insurance: `adb logcat -G 16M`.
- **No spin loops.** Every loop in this brief is `sleep`-driven and runs on the PC, not the rig.

---

## 3. Settings keys this round needs

Same for every run. Write them with the app stopped (§1 of the template) and read the file back.

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA; §7a says it is the only usable transport here |
| `native-ap-transport` | int | `0` | WiFi Direct P2P, the reporter's configuration |
| `log-level` | int | `1` | DEBUG. `RECV:` is emitted at DEBUG (`AapMessageIncoming.kt:51`); VERBOSE only adds the ack lines and floods the buffer |

`log-level` enum order is in the template: VERBOSE 0, **DEBUG 1**, INFO 2, WARNING 3, ERROR 4,
SILENT 5.

---

## 4. The lines that decide every run

Verified with `grep -F` against `a8830caa`.

| Line | Source | Means |
|---|---|---|
| `RECV: ` | `aap/AapMessageIncoming.kt:51` | one inbound AAP message. **The measurement is built entirely on these.** |
| `Standard createGroup SUCCESS` | `connection/WifiDirectManager.kt` | the P2P group is up |
| `WirelessServer: Incoming connection detected` | `aap/AapService.kt` | the phone reached us over TCP |
| `SSL handshake complete` | `aap/AapSslContext.kt` | session live |
| `AudioDecoder.start:` | `aap/AapAudio.kt` | audio channel open; carries sampleRate and latencyMultiplier |
| `Throughput over` | `decoder/VideoDecoder.kt` | 5 s video window; its *interval* proves the process was scheduling normally through a stall |
| `Discovery active` / `Discovery failed` | `connection/WifiDirectManager.kt` | **must not appear** in a mode-3 session (R5) |
| `This unit is connected to another WiFi network while hosting the WiFi Direct group` | `connection/WifiDirectManager.kt` | station/P2P coexistence; sampled only at group-info time |

---

## 5. The measurement

One script does every run's numbers. It has to be written — nothing in `hur-wifi-test-scripts/`
computes this. Save it as `hur-wifi-test-scripts/recv_gaps.py`, list it in Setup notes, and it is
there for the next round. It parses both `-v time` and `-v threadtime`, so it works on the rig
capture and on an exported `HUR_Log_*.txt` alike.

```python
#!/usr/bin/env python3
"""recv_gaps.py <capture.txt> [stall_threshold_s]

Inbound-stall profile of an AAP session, from the timestamps of "RECV:" lines.
Reports stall count, stall duration, the quiet interval between stalls, dead time,
and the delivered audio bitrate. Aligns each stall with the nearest RIGMARK line.
"""
import sys, re, statistics

path = sys.argv[1]
thr = float(sys.argv[2]) if len(sys.argv) > 2 else 1.2

def secs(tok):
    h, m, rest = tok.split(':')
    return int(h) * 3600 + int(m) * 60 + float(rest)

recv, audio, marks = [], [], []
for line in open(path, errors='ignore'):
    parts = line.split()
    if len(parts) < 2:
        continue
    try:
        t = secs(parts[1])
    except (ValueError, IndexError):
        continue
    if 'RIGMARK' in line:
        marks.append((t, line.strip()[-40:]))
    if 'RECV: ' not in line:
        continue
    recv.append(t)
    if 'RECV: AUDIO Media Data' in line:
        m = re.search(r'size: (\d+)', line)
        if m:
            audio.append((t, int(m.group(1)) - 10))

if len(recv) < 50:
    sys.exit('only %d RECV lines — no session in this capture' % len(recv))
recv.sort()
span = recv[-1] - recv[0]

stalls = [(recv[i], recv[i + 1]) for i in range(len(recv) - 1)
          if recv[i + 1] - recv[i] > thr]
dur = [b - a for a, b in stalls]
quiet = [stalls[i + 1][0] - stalls[i][1] for i in range(len(stalls) - 1)]

def stat(name, v, unit='s'):
    if not v:
        print('%-22s none' % name)
        return
    print('%-22s n=%-3d median=%.3f%s mean=%.3f%s sd=%.3f' %
          (name, len(v), statistics.median(v), unit, statistics.mean(v), unit,
           statistics.pstdev(v)))

print('RECV lines            %d over %.1fs' % (len(recv), span))
print('stalls > %.1fs         %d   dead time %.1fs = %.1f%%' %
      (thr, len(stalls), sum(dur), 100 * sum(dur) / span if span else 0))
stat('stall duration', dur)
stat('quiet interval', quiet)
stat('period (start-start)', [stalls[i + 1][0] - stalls[i][0]
                              for i in range(len(stalls) - 1)])

if audio:
    tot = sum(s for _, s in audio)
    aspan = audio[-1][0] - audio[0][0]
    if aspan > 0:
        print('audio delivered       %.1f kB/s (48k/16/2 needs 192.0) '
              '= %.1f%% of real time' % (tot / aspan / 1000, 100 * tot / 192000 / aspan))

if marks:
    print('\nstall vs nearest RIGMARK (negative = stall began before the mark):')
    for a, b in stalls:
        near = min(marks, key=lambda m: abs(m[0] - a))
        print('  stall %8.3f dur %.3f   mark %+.3fs  %s' % (a, b - a, a - near[0], near[1]))
```

Run it on every capture:

```bash
python3 hur-wifi-test-scripts/recv_gaps.py rN.txt
```

**The three numbers that decide the round** are stall count, median stall duration and median quiet
interval, per run. Report them as numbers, never as adjectives (§7).

---

## 6. Runs

Clean-run protocol per §4, with §7a's substitution: airplane mode is not usable on this phone, so use
the phone's Bluetooth adapter as the lever. Check the bond on both sides before starting the round —
§7a, round 9's whole round was lost to an unbonded pair.

A session is up when the capture shows `Standard createGroup SUCCESS`,
`WirelessServer: Incoming connection detected`, `SSL handshake complete`, then `AudioDecoder.start:`.
Start music with a media key once it is (§3), and confirm from the `RECV: AUDIO Media Data` rate that
it is actually playing before starting any interference.

### R0 — which scan levers exist here (gate)

Not a measurement. Establishes what R2 and R3 can use.

```bash
# head unit
adb shell cmd wifi start-scan ; echo "exit=$?"
adb shell cmd wifi list-scan-results | head -5

# phone
adb -s <phone> shell cmd wifi start-scan ; echo "exit=$?"
adb -s <phone> shell cmd wifi list-scan-results | head -5
```

Record for each device whether the command is accepted, refused (`SecurityException`, uid 2000), or
accepted-but-inert. Prove a scan actually ran rather than trusting the exit code:

```bash
adb logcat -d | grep -iE "ScanRequestProxy|WifiScanningService|startScan" | tail -20
```

**Fallback if `start-scan` is refused on a device:** its WiFi picker scans roughly every 10 s while
visible, which is the same shape by a different route.

```bash
adb shell am start -a android.settings.WIFI_SETTINGS      # or -s <phone>
```

On the **phone** this is clean and does not touch the head unit. On the **head unit** it backgrounds
the projection, which perturbs the video path — use it there only if `start-scan` is refused, and say
so in Setup notes.

**Verdict:** PASS if at least one lever works on at least one device; UNTESTABLE only if neither
device can be made to scan by either route. Carry on to R1 regardless.

### R1 — baseline: what does a clean session look like on this rig

Bring up a Native AA session, start music, then **leave both devices completely alone for 6 minutes.**
No commands, no taps, no adb beyond the capture.

- **PASS** — fewer than 3 stalls over 1.2 s across the whole 6 minutes, and no repeating rhythm.
- **FAIL** — a repeating stall pattern with nothing interfering. That is a finding in its own right,
  and it makes this rig a reproduction of the reporter's unit rather than a control. Report the
  numbers and keep the capture; the rest of the round is then measured against this profile instead
  of against zero.

### R2 — head unit scanning during a live session

This is one of the two runs the round exists for.

Session up and music playing as R1. Then, from the PC:

```bash
i=0
while [ $i -lt 18 ]; do
  adb shell log -p i -t RIGMARK "HU_SCAN $i"
  adb shell cmd wifi start-scan
  i=$((i + 1))
  sleep 10
done
sleep 120        # two quiet minutes at the end, still capturing
```

The `log -t RIGMARK` line puts a marker in the same capture on the same clock, so alignment needs no
guessing. Keep the capture running through the quiet tail — the tail is half the evidence.

- **PASS** — stalls appear during the loop and stop in the quiet tail, and `recv_gaps.py` reports
  each stall beginning within 1 s of a RIGMARK.
- **FAIL** — the loop runs, the scans demonstrably happen (R0's log check), and the RECV gap profile
  is unchanged from R1.
- **INCONCLUSIVE** — R0 found no working lever for the head unit.

Report the median stall duration. If it lands near 1.5 s, that is the reporter's number and it is the
strongest single result available from this rig.

### R3 — phone scanning during a live session

The other run the round exists for, and the one that matches the leading hypothesis for his unit: his
phone is a P2P client of our group *and* the source of his head unit's internet.

Identical to R2, driven at the phone:

```bash
i=0
while [ $i -lt 18 ]; do
  adb shell log -p i -t RIGMARK "PHONE_SCAN $i"      # marker on the HEAD UNIT's clock
  adb -s <phone> shell cmd wifi start-scan
  i=$((i + 1))
  sleep 10
done
sleep 120
```

Note the marker is written to the head unit deliberately, so it shares a clock with the `RECV:` lines.

Same PASS / FAIL / INCONCLUSIVE conditions as R2.

### R4 — the phone hosting its own access point while it is our P2P client

His configuration, as closely as this rig can get to it. A phone serving a SoftAP on one channel and
sitting in our P2P group on another has one radio doing two jobs.

Bring the session up and confirm music is flowing. **Then turn the phone's mobile hotspot on by
hand** (Settings → Connections → Mobile Hotspot, or the quick-settings tile) and leave everything
alone for 5 minutes. Do not connect anything to the hotspot — an idle AP is the test. Then turn it
off by hand and capture 2 more quiet minutes.

Mark both transitions so they are in the capture:

```bash
adb shell log -p i -t RIGMARK "HOTSPOT_ON"     # immediately before you tap
adb shell log -p i -t RIGMARK "HOTSPOT_OFF"
```

- **PASS** — a repeating stall pattern appears between the two markers and is absent outside them.
- **FAIL** — no change in the gap profile across either marker.
- **UNTESTABLE** — this phone will not host an AP while joined to a P2P group (some refuse outright).
  That refusal is itself worth reporting, with whatever the phone says.

If the session drops entirely when the hotspot comes up, that is a **PASS with a note**, not a
failure: it is the same mechanism at a larger amplitude.

### R5 — confirm the app is not the one scanning (cheap, no interference)

Reuse R1's capture; no new run needed.

```bash
grep -cE "Discovery active|Discovery failed" r1.txt      # expect 0
grep -c "This unit is connected to another WiFi network while hosting" r1.txt
grep -o "p2p-wlan0-[0-9]*" r1.txt | sort -u              # expect exactly one
```

- **PASS** — zero `Discovery active` and zero `Discovery failed` across a full mode-3 session.
- **FAIL** — either appears. That would mean `discoveryRunnable` is live on the Native AA path on
  this rig, which is the leak described in §2 reproducing without being provoked. Say so loudly; it
  changes the fix.

Also report whether the coexistence line appeared, and what `dumpsys wifi | grep -iE "mWifiInfo|SSID|Supplicant"` says about the head unit's station state during the session — his was not associated,
and it matters whether this rig's is.

---

## 7. Do not re-run

Settled already; no device time on any of it.

- **The audio path is not the cause.** `AudioDecoder.kt`, `AudioMixer.kt`, `AudioTrackWrapper.kt` and
  `SocketAccessoryConnection.kt` are byte-identical across `v.3.2.2`, `v.3.2.3` and `v.3.2.4`.
- **The 3.2.4 `audioQueueCapacity` default is not the cause for him.** `ee2de94b` moved it 0 → 50, but
  his log reads `queueCapacity=0` — his stored pref is still unbounded and the change never reached
  him. No `Audio queue is full` line appears anywhere in his capture.
- **Raising the audio latency multiplier does not help.** He is already at 8x, which is 0.70 s of
  buffer against a 1.6 s outage, and ~0.8 s of music per cycle is never sent at all.
- **It is not a 3.2.3 regression.** He described the same 10-15 s cadence on v3.2, before any of the
  commits he blames.

---

## 8. Report back

`link-stall-periodic-scan-round1-results.md` on this branch, in §7's format. The numbers that decide
whether this becomes a code change or a reply to the reporter:

1. **R1's stall count and dead-time %** — whether this rig has a clean baseline at all.
2. **R2, R3 and R4's median stall duration and median quiet interval**, against his 1.590 s / 9.986 s.
3. **Which scan levers worked** (R0), so the next round does not re-derive it.
4. **R5's `Discovery active` count** — zero is the expected answer and the one the analysis needs.

If R2, R3 and R4 are all INCONCLUSIVE or UNTESTABLE, stop there. That is the round answering the
question: this rig cannot produce the signal, and the diagnosis has to be settled on the reporter's
own hardware instead.
