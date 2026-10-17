# Periodic link stall — round 2 brief: the same interference, on the reporter's band

**Candidate:** `test/p2p-force-2ghz-band` @ `fdb4df27` on `o-jcardenass/open-headunit`.
**Baseline:** round 1's own captures. **No baseline build** — round 1 already measured this rig
clean on 5 GHz, and that is the comparison.

```bash
git fetch fork && git checkout test/p2p-force-2ghz-band
git merge --ff-only fork/test/p2p-force-2ghz-band
git rev-parse --short HEAD          # expect fdb4df27
```

Plain commit on top of `origin/main` @ `a8830caa`, no history rewrite.

**This branch has never been compiled.** One constant, one private const and seven log strings —
but R0 is a real gate, and a failure there stops the round.

**It is a test build and must never ship.** It forces the Native AA P2P group onto 2.4 GHz. The
only reason it exists is to put this rig on the reporter's band for one round.

---

## 1. Why round 1's two FAILs do not mean what they look like

Round 1 ran the two runs it existed for and both came back FAIL: a phone scan loop (R3) and the
phone hosting its own SoftAP (R4) each left the RECV gap profile identical to a clean baseline.

**The interference and the link were not on the same band.** This rig is API 29+, so
`createQuietGroup()` requests `GROUP_OWNER_BAND_5GHZ` and round 1's capture duly says
`5GHz createGroup SUCCESS!`. The reporter's unit is API 27, where that call does not exist —
`WifiDirectManager.kt` logs `5GHz P2P group request requires Android 10+. Using standard
createGroup`, and a standard autonomous group lands on a 2.4 GHz social channel. A 2.4 GHz scan
sweep cannot stall a 5 GHz group, and R4's phone AP was very probably 2.4 GHz as well; its
frequency was not recorded.

So round 1 did not measure the mechanism and fail to find it. It never loaded the mechanism at all.
Those two runs should be read as untested.

One honesty note carried from the analysis: the reporter's own group frequency reads
`Freq: 0 MHz (unknown)` — the pre-API-29 reflection finds no field on his unit — so "his group is on
2.4 GHz" is a strong inference from the code path he takes, not a measurement.

**What is being reproduced.** `recv_gaps.py` on his capture:

```
RECV lines            42140 over 487.7s
stalls > 1.2s         41   dead time 68.7s = 14.1%
stall duration         n=41  median=1.590s mean=1.677s sd=0.373
quiet interval         n=40  median=9.986s mean=9.884s sd=0.795
period (start-start)   n=40  median=11.570s mean=11.562s sd=0.693
audio delivered       177.8 kB/s (48k/16/2 needs 192.0) = 92.6% of real time
```

Against round 1's clean 5 GHz control on this rig: **0 stalls in 432 s, 192.1 kB/s = 100.0%.**

---

## 2. What is different about this round

- **`log-level=0`, not 1.** Round 1's brief was wrong and cost a run. `RECV:` is an `AppLog.d` call
  wrapped in `if (AppLog.LOG_VERBOSE)` at `AapMessageIncoming.kt:50`, so only VERBOSE produces it.
  The standing template now carries the general rule.
- **Video is pinned to 720p for the whole round**, because 2.4 GHz will not hold a connection above
  it. This is a settings key, not a discovery — see §3. It is also the reporter's own resolution.
- **R1 is a gate as well as a baseline, and it can still legitimately fail.** Requesting 2.4 GHz is
  not the same as getting it, and even at 720p the band may not carry this rig's link. **If R1 is
  not clean, the round pivots rather than continuing** — see R1.
- **Head-unit-side scanning stays untestable.** Round 1 established that this unit's `WifiScanner`
  service is broken by both routes. There is no R for it and no substitute; do not spend time.
- **Still no run for the discovery leak**, for the same reason as round 1: arming it needs a runtime
  helper→native mode switch, and a settings write requires the app stopped, which drains the very
  Handler that would hold the leaked runnable. That coverage goes to a JVM test and a code fix.
- **Launch order matters and round 1 proved it.** Head unit app up first while the phone is down,
  group settles ~15 s, then the phone. Restoring the phone first produced two sessions.
- One session can serve R1 through R4, as round 1 did. Prefer that over reconnecting between runs.

---

## 3. Settings keys this round needs

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `native-ap-transport` | int | `0` | WiFi Direct P2P, the reporter's configuration |
| `resolutionId` | int | `2` | **720p.** Anything higher will not hold a connection on 2.4 GHz — see below. Also what the reporter runs |
| `log-level` | int | `0` | **VERBOSE.** `RECV:` needs it — see §2 |

**`resolutionId` is camelCase**, unlike every other key in this file. The §1 write template's
element-scoped `sed` patterns take a `KEY` literally, so they work — but do not "correct" it to
`resolution-id` on the way past. `Settings.Resolution` ids are AUTO 0, 480p 1, **720p 2**, 1080p 3,
1440p 4, 2160p 5 (`Settings.kt:713-719`).

**Why 720p is set up front rather than discovered.** A 2.4 GHz link will not carry this app's higher
resolutions — the hotspot route on this class of hardware completes its handshake at 1080p/60 and
then dies within seconds, while 800x480/30 holds for minutes. WiFi Direct is a different transport
but the same band and the same ceiling, so pinning 720p removes a known failure that would otherwise
be misread as the round's own result. It also matches the reporter's unit exactly, which is what the
round is trying to reproduce.

If R1 still fails on throughput at 720p, the pivot run additionally sets `fps-limit` to `30` (int).

---

## 4. The lines that decide every run

Verified with `grep -F` against `fdb4df27`.

| Line | Source | Means |
|---|---|---|
| `2.4GHz createGroup SUCCESS! [TEST BUILD]` | `WifiDirectManager.kt` | the candidate's path ran — **if this is absent, you are running the wrong APK** |
| `Requesting Native AA P2P group on 2.4GHz band. [TEST BUILD]` | `WifiDirectManager.kt` | the band was asked for |
| `onGroupInfoAvailable: … Freq: N MHz` | `WifiDirectManager.kt` | **the band actually obtained.** This is the number the round turns on |
| `RECV: ` | `AapMessageIncoming.kt:50` | the measurement, VERBOSE only |
| `[ServiceDiscovery] NegotiatedResolution is: WxH` | `protocol/messages/ServiceDiscoveryResponse.kt:103` | what the link is being asked to carry |
| `Throughput over` | `decoder/VideoDecoder.kt` | rendered/fed/dropped per 5 s |
| `WirelessServer: Incoming connection detected` / `SSL handshake complete` / `AudioDecoder.start:` | — | session is live |
| `Discovery active` / `Discovery failed` | `WifiDirectManager.kt` | must stay at zero |

`recv_gaps.py` is already in `hur-wifi-test-scripts/` from round 1. Unchanged; use it as is.

---

## 5. Runs

### R0 — build gate

Build the candidate with `build_hur.sh`, run `run_unit_tests.sh`, `adb install -r -d`, and confirm
the live APK's md5 (§5 of the template). Record both md5 and the unit-test count.

- **PASS** — compiles, unit tests green, APK confirmed live.
- **FAIL** — **stop the round** and report the compiler output. This branch has never been compiled;
  a build failure here is the brief's fault, not the rig's.

### R1 — does the group actually come up on 2.4 GHz, and does it hold? (gate + baseline)

Bring up a session per the launch order in §2. Then leave both devices **completely alone for 6
minutes** — no commands, no taps.

First, before any measurement, read the band off the log:

```bash
grep -E "createGroup SUCCESS|onGroupInfoAvailable" r1.txt | head
grep "NegotiatedResolution" r1.txt
```

- **PASS** — `Freq:` reports a 2.4 GHz frequency (2412-2484 MHz), the session stays up for the full
  6 minutes, and `recv_gaps.py` reports fewer than 3 stalls over 1.2 s with no repeating rhythm.
- **FAIL, band** — `Freq:` reports 5 GHz or 0. The request did not take, and R2/R3 would repeat
  round 1's mistake. Report the frequency and **stop the round**; the branch needs a different
  approach.
- **FAIL, throughput** — the group is 2.4 GHz but the session drops, or the profile shows stalls
  with nothing interfering, **at 720p**. **This is a result, not a wasted round** — it would mean the
  band alone degrades the link on this hardware, which is closer to the reporter's situation than
  anything measured so far. Report the full numbers, then **pivot**: set `fps-limit=30`, re-run R1
  once, and if that is clean carry on to R2 with it. Say in Setup notes that R2/R3 ran at 30 fps.

Report `NegotiatedResolution` (expect `1280x720`), the `Freq:` value, and the full `recv_gaps.py`
block either way. If `NegotiatedResolution` is not 720p, the `resolutionId` write did not take —
check the key's camelCase spelling before anything else.

### R2 — phone scanning, on the reporter's band

**The point of the round.** Round 1's R3, repeated where it can actually bite.

Session up and music playing. Then, from the PC:

```bash
i=0
while [ $i -lt 18 ]; do
  adb shell log -p i -t RIGMARK "PHONE_SCAN $i"      # marker on the HEAD UNIT's clock
  adb -s <phone> shell cmd wifi start-scan
  i=$((i + 1))
  sleep 10
done
sleep 120        # quiet tail, still capturing
```

Round 1 confirmed this command works cleanly on this phone. Grow the **phone's** buffer this time
(`adb -s <phone> shell logcat -G 16M`) so the per-run confirmation does not wrap before it is read —
that was round 1's one soft spot.

- **PASS** — stalls appear during the loop, stop in the quiet tail, and `recv_gaps.py` puts each
  stall within 1 s of a RIGMARK.
- **FAIL** — the loop runs, the scans are confirmed on the phone's own log this time, and the
  profile is unchanged from R1.

Report median stall duration and median quiet interval against his 1.590 s / 9.986 s.

### R3 — the phone hosting its own AP, on the reporter's band

Round 1's R4, with the one thing it was missing.

Session up. **Record the phone's AP band before anything else** — if it comes up on 5 GHz this run
tests nothing, exactly as round 1 did:

```bash
adb -s <phone> shell dumpsys wifi | grep -i SoftApInfo
```

Turn the phone's hotspot on by hand (allowed — the no-UI rule governs *this app's* settings list,
not the phone's system settings), **set to 2.4 GHz in its own settings if the phone offers the
choice**, leave it idle with nothing connected for 5 minutes, then off, then 2 quiet minutes. Mark
both transitions:

```bash
adb shell log -p i -t RIGMARK "HOTSPOT_ON"
adb shell log -p i -t RIGMARK "HOTSPOT_OFF"
```

- **PASS** — a repeating stall pattern between the markers, absent outside them.
- **FAIL** — no change across either marker, **with the AP's frequency recorded**. Without that
  number the run is INCONCLUSIVE, not FAIL.
- **PASS with a note** — the session drops entirely when the AP comes up. Same mechanism, larger
  amplitude.

### R4 — both at once (bonus, only if R2 and R3 both came back FAIL)

If neither lever alone moves the profile, run them together: hotspot on **and** the 18-step scan
loop inside the hotspot window. Cheap, and it is the only way this round can find a threshold effect
that neither one alone reaches. Skip it entirely if R2 or R3 already reproduced.

- **PASS** — stalls appear only in the combined window.
- **FAIL** — profile unchanged with both levers active.

---

## 6. Do not re-run

- **Round 1's R1, R3 and R4 on 5 GHz.** Settled; they are this round's baseline.
- **Head-unit-side scanning.** `WifiScanner` is broken on this unit by both routes, reproduced twice.
- **`Discovery active` on the Native AA path.** Round 1's R5 confirmed zero. Just re-check it once
  on R1's capture as a freebie and move on.
- **The audio path as a cause.** Byte-identical across v.3.2.2 / v.3.2.3 / v.3.2.4; the reporter's
  `queueCapacity=0` means the 3.2.4 default change never reached him; his 8x latency is already the
  maximum and cannot cover a 1.6 s outage of which ~0.8 s of music is never sent.

---

## 7. Report back

`link-stall-periodic-scan-round2-results.md`, in §7's format. The numbers that decide whether this
becomes a code change, a reply to the reporter, or a dead end:

1. **R1's `Freq:` value and `NegotiatedResolution`** — whether the round was valid at all.
2. **R1's full `recv_gaps.py` block** — a clean 2.4 GHz control, or the band-alone finding.
3. **R2 and R3's median stall duration and median quiet interval**, against 1.590 s / 9.986 s.
4. **R3's `SoftApInfo` frequency.** Round 1's R4 is unusable without it; do not repeat that.

If R1 fails on band, stop there and say so — everything downstream is meaningless and the branch
needs rethinking rather than more device time.
