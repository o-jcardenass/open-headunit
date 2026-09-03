# media-gap-instrument — round 1 brief

## 1. Build and baseline

| | |
|---|---|
| Branch | `fix/media-gap-instrument-and-attribution` |
| SHA | `3398c8cc61cf1a9de2767451291d6503cd211217` |
| Base | `main` @ `e7a3b3ad` (which already contains `df3cce5c`, the video-and-wireless stack merge) |
| Commits | one |
| History rewritten? | The branch was rebased once before it was pushed, off an unrelated CI commit. It has been pushed exactly once since. Nothing here has been tested before. |

```bash
git fetch fork fix/media-gap-instrument-and-attribution
git checkout -B media-gap-round1 fork/fix/media-gap-instrument-and-attribution
git log --oneline -1     # expect 3398c8cc
```

**First-ever compile.** There is no JVM in the environment that wrote this, so nothing here has been
through a compiler or a test runner. R0 is a real gate, not a formality — if it fails, stop and
report, that is a complete and useful round.

## 2. What this is and why it exists

A reporter's head unit (MediaTek `ac8227l`, 1 GB, Android 8.1, 1024x600, Native AA) loses picture and
sound together for four to eight seconds, every ten and a half seconds. Five captures were taken.
**Three separate instruments in the app watched it happen and none of them said so**, and one of them
reacted to it by rebuilding the decoder. This branch is those three instruments.

What the captures established, so you know what the code is fighting:

- The variable is whether the head unit's own WiFi station is joined to a network. Joined: no dropout
  longer than 0.65 s in 138 s, zero audio underruns, on H.265 *and* on H.264. Unjoined: twelve
  outages every ~10.5 s and thirty underruns in two minutes, on both codecs. The codec is not the
  variable.
- **The inbound link never goes quiet.** `LinkGapMonitor` printed twice across all five logs, both
  times naming a gap of 1.6-1.8 s in a 30 s window — including windows that each contained three
  six-second blackouts. Its threshold is 1200 ms and the phone pings on CONTROL about once a second,
  so a link carrying nothing but pings scores healthy while both media channels are dead.
- Video and audio die in the same window, 12 times out of 12, while control runs on. That is what AAP
  media flow control looks like (`max_unacked` 12 on VIDEO, 30 on each audio sink), not what a dead
  radio looks like.

So: three inbound series instead of one (link, video, audio); a new `UplinkStallMonitor` timing our
own writes, because whether *our* acks stop leaving the socket is the half nothing could see; a
coexistence warning that stops telling users to undo the thing that fixes them; and a fix to the
display-stall counter that charged a whole session's long frames to one tick.

## 3. What is different about this round

**This rig cannot carry the fault.** `link-stall-periodic-scan-round5-results.md` settled that on
timescale grounds — the UNISOC MT50 on Android 14 does not produce the reporters' periodic outage at
all, on either the associated or the unassociated arm. **Do not try to reproduce it.** The question
this round answers is the other one, and it is the one that decides whether the change ships:

> Are the new lines **silent** on healthy hardware?

An instrument that speaks on a good session gets ignored on a bad one. That already happened on this
project once — the framing audit spent its whole print budget on false positives in the first 200 ms
and was switched off for the five minutes that mattered. R1 is the point of this round for that
reason, and it is a run whose PASS condition is an absence.

Pre-registered as likely INCONCLUSIVE, so they are not read as failures:

- **R3's positive half.** It depends on Android Auto actually going quiet on the audio channel when
  playback pauses. If it keeps the channel fed with silence instead, no gap appears and no line
  prints. That is a fact about the phone, not a defect. Report it as INCONCLUSIVE and move on — the
  logic itself is proven in R0, where the measured waveform is replayed with a 1 Hz ping in it.
- **R4's joined arm.** It needs the head unit joined to a WiFi network, and §7a records that this rig
  has no network to join and a `WifiScanner` that does not work by any route. Attempt it only via the
  phone's hotspot and only if that costs a couple of minutes; the unjoined arm is the one that must
  pass.
- **Any positive proof that a real display stall still trips the floor.** There is no known lever on
  this rig that freezes the display consumer while video keeps arriving — §7a records that
  `KEYCODE_HOME` does not even tear down the surface here. That coverage is routed to R0's JVM tests
  (`a collapsed consumer still fills the window`), deliberately. R5 checks the neighbouring risk
  instead: that the recovery ladder still works at all.

## 4. Settings keys this round needs

Written into `shared_prefs/settings.xml` with the app stopped, via
`hur-wifi-test-scripts/set_pref.sh <key> <type> <value>`. Never through the UI.

| Key | Type | Value | Why |
|---|---|---|---|
| `log-level` | int | `2` | INFO. Every line this round reads prints at INFO — verified, none is behind `AppLog.LOG_VERBOSE`. Do **not** raise to VERBOSE: §7a records this unit's driver stack flooding logcat and wrapping the ring buffer inside one run. |
| `wifi-connection-mode` | int | `3` | Native AA. §7a: the only usable transport on this rig. |
| `view-mode` | int | `1` | TextureView. The display-stall path under test (`maybeRecoverFromDisplayStall`) is gated on the backend reporting drawn frames, and SurfaceView returns -1, which makes the whole path unreachable. **R5 is meaningless on SurfaceView — check this key before running it.** |
| `debug-video-fault-injection` | int | `0` normally, `2` for R5 only | `2` = `DROP_MIDDLE_FRAGMENT`. Return it to `0` afterwards. |

## 5. The lines that decide every run

Verified with `grep -F` against `3398c8cc`. All at **INFO** except the two marked W.

| Grep for | Source | Means |
|---|---|---|
| `inbound link quiet ` | `LinkGapMonitor.kt:181` | the whole link went silent — every channel at once |
| `inbound video quiet ` | same | the video channel alone went silent, twice or more in a window |
| `inbound audio quiet ` | same | the three audio sinks went silent, twice or more in a window |
| `AapTransport: uplink blocked on ` | `UplinkStallMonitor.kt:102` | one of our own writes held the send thread over 250 ms |
| `This unit is connected to another WiFi network` | `StationCoexistencePolicy.kt:46,54,60,66,72` | the station is associated while the group is up |
| `Disconnecting the other network` | `StationCoexistencePolicy.kt:49` | **W** — the prescriptive half, now reachable only when both frequencies are known and differ |
| `Display stall (` | `AapProjectionActivity.kt:603,610` | **W** — the display-stall recovery fired |
| `Rebuilding projection view` | `AapProjectionActivity.kt:610` | **W** — and it rebuilt |

All three `inbound … quiet` lines are prefixed `AapTransport: ` in the log. A full example of the
shape, from the unit tests:

```
AapTransport: inbound video quiet 12 times in 31500ms: dead=17400ms (55%), longest=6460ms, period~10500ms
AapTransport: uplink blocked on 3 writes of 812 in 31500ms: blocked=17400ms (55%), longest=6460ms
```

**Grep every capture with `-a`** (§7a): `grep -ac`, `grep -a -o`. A refused count on a long line
reads exactly like "not found", and this round's central results are counts of absent patterns.

## 6. Runs

### R0 — build and unit tests. Gate.

```bash
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
```

First compile of this branch, so expect the failure mode to be a compile error rather than a test
failure. If it does not build, **report the error and stop** — that is the round.

**PASS**: builds, and all tests green. Report the total, and these four classes by name with their
counts: `LinkGapMonitorTest` (12), `UplinkStallMonitorTest` (5), `StationCoexistencePolicyTest` (7),
`ProjectionWatchdogPolicyTest` (23). That is **23 new tests** over `main`.

Two of them are the branch's whole argument and are worth naming in the results either way:

- `LinkGapMonitorTest > the ping masks a total media outage from the link series` — replays the
  reporter's waveform with a 1 Hz ping running through it. The link series must stay silent and the
  video series must report a period between 10 and 11 s. This test **fails against `main`**.
- `ProjectionWatchdogPolicyTest > a collapsed consumer still fills the window` — the regression
  guard, since the change makes the counter charge less.

**FAIL**: anything red. Copy the failure verbatim; do not fix it.

### R1 — clean session, 10 minutes. **The point of the round.**

Ordinary Native AA session, nothing injected, nothing toggled, map or music on the phone as normal.
§4's clean-run protocol; head unit up before the phone (§7a).

**PASS**, all four:

```bash
grep -ac "inbound link quiet"        r1.txt     # expect 0
grep -ac "inbound video quiet"       r1.txt     # expect 0
grep -ac "inbound audio quiet"       r1.txt     # expect 0
grep -ac "uplink blocked on"         r1.txt     # expect 0
```

**FAIL**: any of them non-zero. If one fires, quote the whole line — the numbers in it say whether
the rig genuinely stalled or the threshold is wrong, and those need different answers.

Also report, as context rather than a verdict: `grep -ac "Throughput over" r1.txt` and the fps range,
so the round records that the session was actually healthy while the instruments were silent.

### R2 — idle screen, 3 minutes. The false-positive guard.

Same session as R1 if it is still up (§7a: reuse a live link). Bring Android Auto to a screen with
**nothing animating** — a paused media player, or a stationary map with no navigation — and leave it
alone for a full three minutes. Do not touch the screen.

Android Auto sends no video while nothing animates, so this produces one long video silence. The
media series require two gaps in a window before they print, precisely so this does not read as a
fault; the earlier version of this reasoning is why a "connection lost" overlay used to cover the
projection every 15-30 s.

**PASS**: `grep -ac "inbound video quiet" r2.txt` is `0`, and the session is still live at the end.

**FAIL**: it printed. Quote the line and the `gaps=` count.

### R3 — audio pause/play cycling. Positive control, best effort.

With music playing over the head unit, run this and capture throughout:

```bash
for i in 1 2 3 4 5 6; do
  adb shell input keyevent KEYCODE_MEDIA_PAUSE ; sleep 4
  adb shell input keyevent KEYCODE_MEDIA_PLAY  ; sleep 6
done
```

Six cycles at ~10 s each is 60 s, which spans two 30 s windows and puts three pauses in each — two
more than the floor.

**PASS**: at least one `inbound audio quiet` line, with `gaps` of 2 or more, and
`grep -ac "inbound link quiet" r3.txt` still `0`. That second half is the real point: the link stayed
up while a media channel went quiet, which is the distinction the whole change exists to make.

**INCONCLUSIVE** (expected, see §3): no audio line at all, because Android Auto kept the channel fed
through the pause. Say so and report `grep -ac "Media Sink Stop Request: AUDIO"` as the evidence for
which of the two happened.

**FAIL**: an `inbound link quiet` line appears. That would mean pausing music silences the whole link,
which contradicts the model this change is built on and is worth stopping for.

### R4 — the coexistence line.

**Unjoined arm (required).** The rig has no station association. Over any session from R1-R3:

```bash
grep -ac "This unit is connected to another WiFi network" r1.txt    # expect 0
grep -ac "Disconnecting the other network"                r1.txt    # expect 0
```

**PASS**: both `0`. The line is gated on `supplicantState == COMPLETED` and must not fire otherwise.

**Joined arm (optional, timeboxed to ~5 minutes).** If the phone can host a hotspot the head unit
joins while still serving as the P2P client — discussion #665 describes a unit doing exactly this —
form a session that way and grep again.

**PASS**: `This unit is connected to another WiFi network` appears **at I level**, and
`Disconnecting the other network` does **not** appear. This rig is Android 14, so
`WifiP2pGroup.getFrequency()` exists and both frequencies may be readable; if they are and they
differ, a W-level line with `Disconnecting` in it is also correct. Quote the whole line either way —
which of the five branches fired is the finding.

**UNTESTABLE**: no hotspot, no join. Say so and move on; the unjoined arm carries the round.

### R5 — the recovery ladder still works.

`view-mode` must be `1` (TextureView). Set `debug-video-fault-injection` to `2` with the app stopped,
run a session for 5 minutes, then set it back to `0`.

This is a regression probe, not a new claim. The display-stall counter now charges less than it did,
and the risk is that something downstream of it stopped recovering.

**PASS**: the decoder recovers the way it does on `main` — `cycling video focus` followed by
`keyframe reached the codec` and `keyframe decoded`, with `rendered=` never pinned at 0 across
consecutive `Throughput over` windows. Report the injected fault count alongside the recovery count; a run
with zero faults is INCONCLUSIVE, not PASS. The injector announces itself at W level with
`FAULT INJECTION IS ON` and prints `FAULT INJECTED (#N of M candidates)` per fault, so
`grep -ac "FAULT INJECTED"` is the count. If `FAULT INJECTION IS ON` is absent, the setting did not
take and the run is void rather than clean.

**FAIL**: a wedge — `rendered=0` across several consecutive windows with input still flowing.

Also report `grep -ac "Display stall (" r5.txt`. Either answer is acceptable here and neither decides
the run; it is recorded because it is the first measurement of that counter after the change.

## 7. Do not re-run

- Anything from `link-stall-periodic-scan` round 5. The scan question is answered on this rig and the
  fault is not reproducible here. This round does not revisit it.
- The video keyframe/wedge validation from `video-pipeline-stack` round 2 and `release-next`. R5
  touches that ladder only as a regression probe and is not re-proving it.
- Codec A/B. The reporter's own 2x2 settled that the codec is not the variable; nothing on this rig
  adds to it.

## 8. Report back

Three numbers decide whether this ships:

1. **R0**: does it compile, and are all tests green — with the total and the four class counts.
2. **R1**: the four zeroes. Non-zero anywhere is the finding, whatever else happened.
3. **R2**: `inbound video quiet` count on a genuinely idle screen. Must be `0`.

Everything else is context. R3 and R4's joined arm are bonuses; R5 is insurance.
