# Round 1 brief — does the head unit's own GPS actually reach the phone?

First round of a new thread. Read `TESTING-TEMPLATE.md` first; everything standing lives there.

---

## 1. Build

**Candidate:** `fix/native-gps-transport-race` @ `9e7cf95a` — three commits on main's `a8830caa`.
**History has been rewritten several times**; if the rig has any earlier copy of this branch, discard
it:

```bash
git fetch fork --prune --prune-tags
git checkout -B fix/native-gps-transport-race fork/fix/native-gps-transport-race
git log --oneline -4
# expect: 9e7cf95a, cf9e50d1, 2de11a95, a8830caa
```

**Baseline: none.** No A/B in this round; nothing here compares against main.

### R0 — build gate

`run_unit_tests.sh`, then `build_hur.sh`. Record the APK md5 and confirm it is live (§5).

**If R0 fails, stop and report — this branch has never been compiled anywhere.** There is no CI
run behind it; this build is its first.

---

## 2. What this is and why it exists

A reporter with a working in-dash GPS antenna found Android Auto following the phone's position:
with the "Gps for navigation" toggle on, spoofing the phone's location moved the map. The trace
found the toggle's pipeline real but broken twice on the way to the wire:

1. **A fix was sent at the wrong moment.** A LOCATION SensorEvent only leaves once the phone's own
   `SENSOR_STARTREQUEST` for LOCATION has been processed, which lands ~1–1.7 s *after*
   TransportStarted. Everything sent before that was silently discarded, with no queue behind it.
   The fix: `sensorStartRequest` now sends the freshest head-unit GPS fix immediately after
   answering the phone's request — the earliest point a send can succeed.
2. **A parked head unit produced one fix, ever.** The subscription asked for 400 ms AND 5 m;
   Android ANDs the two, so without movement nothing further arrived, and Android Auto fell back to
   the phone. The fix: subscribe on time alone at 1 Hz, plus a bounded 3 s resend backstop for
   chips that only emit on change (it stands in only for a missed beat, and gives up when the last
   real fix is over 60 s old so a dead antenna lets the phone take over again).

Everything the phone receives as the car's position is now sourced from the GPS provider only —
never network/passive fallbacks, which on a wireless head unit are often derived from the very
hotspot or P2P group the app itself created.

This round is the first hardware check of the send path. The author's own unit verified an earlier
build's end-to-end behaviour (phone spoofed elsewhere, AA stayed on the head unit's position); what
has never run anywhere is *this* build.

---

## 3. What is different about this round

**The rig's GPS situation is unknown, so the round carries its own probe.** Indoors, a real GPS
lock is unlikely even if the hardware exists. The round therefore leans on **shell-injected test
provider fixes** (Android 12+: `cmd location providers add-test-provider`), which need no GPS
hardware at all. R1 probes what exists; R3 establishes whether injection works on this rig. If
injection turns out impossible, R3–R5 are **INCONCLUSIVE** (rig fact, not a branch defect) — say
so and finish the rest; there is no JVM fallback for these, hardware is the only check.

**Run order is load-bearing once:** R2 must run **before any fix is ever injected** — it needs "no
recent GPS fix" to be true, and an injected fix stays fresh for 10 minutes.

**Log level 0 (VERBOSE) throughout.** One decisive line (`GpsLocation: fix received`) is `AppLog.v`.
This unit's driver stack floods logcat at VERBOSE (§7a), so keep runs short, and pull the app's
exported `HUR_Log_*.txt` alongside each capture — the app's own lines survive there when the
logcat ring has wrapped.

**Location plumbing setup, once at the start** (survives force-stop; redo after any reinstall):

```bash
PKG=com.andrerinas.headunitrevived
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell cmd location set-location-enabled true
```

**Mock injection contract** (used from R3 on; exact flag spelling may differ on this build — run
`adb shell cmd location help` first and adapt, recording the working form in Setup notes):

```bash
adb shell cmd appops set --uid 2000 android:mock_location allow \
  || adb shell appops set com.android.shell android:mock_location allow
adb shell cmd location providers add-test-provider gps
adb shell cmd location providers set-test-provider-enabled gps true
adb shell cmd location providers set-test-provider-location gps --location 48.8584,2.2945
```

The coordinates are the Eiffel Tower — arbitrary on purpose; any fixed pair will do, just keep the
same pair all round.

---

## 4. Settings keys

| Key | Element | Meaning |
|---|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | Native AA. Required throughout. |
| `log-level` | `<int name="log-level" value="0" />` | VERBOSE. Required throughout. |
| `gps-navigation` | absent (defaults true), or `<boolean name="gps-navigation" value="false" />` | the toggle under test; R6 sets false |

`set_hu_prefs.sh` for anything setting more than one key.

---

## 5. The lines that decide the runs

Verified with `grep -F` against `9e7cf95a`. All prefixed `OPENHU`. Interpolated values shown as
`<...>`.

| Line | Level | Meaning |
|---|---|---|
| `Sensor Start Request sensor: LOCATION, minUpdatePeriod: <N>` | INFO | the phone asked for car GPS |
| `LOCATION sensor requested. Sending current fix immediately. sentOnWire=true` | INFO | **the point of the round** — a fix went on the wire at the priming moment |
| `LOCATION sensor requested. No recent GPS fix to prime with.` | INFO | priming ran, nothing to send |
| `GpsLocation: not requesting updates, ACCESS_FINE_LOCATION granted=<b> GPS_PROVIDER enabled=<b>` | INFO | the subscription gate refused, and which half |
| `GpsLocation: first fix after <N>s` | INFO | the subscription produced its first fix |
| `GpsLocation: fix received` | **VERBOSE** | one line per real fix — the cadence counter |
| `GpsLocation: no GPS fix for <N>s, stopping resend so the phone can fall back to its own location` | INFO | the resend backstop gave up (N should be 60–66) |
| `AapTransport: dropping sensor events, isAlive=<b> startedSensors=<set> droppedByType=<map>` | INFO | should **not** normally appear; if it does, quote it in full |

---

## 6. Runs

Standard clean-run protocol (§4) for every session run. Force-stop between runs — it also empties
the app's in-memory location cache, which several runs rely on.

### R1 — location stack probe (read-only, no app, no phone)

```bash
adb shell dumpsys location > r1-dumpsys-location.txt
adb shell cmd location help > r1-cmd-location-help.txt 2>&1
```

Record: which providers exist, whether a real `gps` provider is present, any last-known locations
and their ages, and whether `cmd location` offers the test-provider subcommands. No PASS/FAIL —
this run is input to the rest.

### R2 — priming with no fix available (**before any injection, ever**)

Settings: defaults plus the two required keys. No test provider. Session up per protocol.

- **PASS:** `Sensor Start Request sensor: LOCATION` appears, followed by
  `LOCATION sensor requested. No recent GPS fix to prime with.`, and the session stays healthy.
- **FAIL:** the priming line claims `sentOnWire=true` (where did that fix come from? quote it), or
  the session dies around the priming moment.
- If R1 showed a real, fresh (<10 min) GPS last-known fix on this unit, this run's expectation
  flips to R3's — note it and carry on.
- Record which subscription-gate line printed (`Request location updates` alone, or
  `not requesting updates ... enabled=false`) — either is fine here, it documents the rig.

### R3 — mock feasibility + primed send (**the point of the round**)

App stopped. Set up the mock contract (§3), inject **one** fix, verify it took:

```bash
adb shell dumpsys location | grep -iA2 "last location"
```

Then session up per protocol. After the session forms, inject one more fix.

- **PASS:** `LOCATION sensor requested. Sending current fix immediately. sentOnWire=true`, and no
  `AapTransport: dropping sensor events` for LOCATION anywhere in the capture.
- **FAIL:** `sentOnWire=false`, or the drop line names LOCATION — attach the full capture.
- **INCONCLUSIVE:** injection cannot be made to work on this rig (document every command tried).
  R4 and R5 are then also INCONCLUSIVE; skip to R6.

### R4 — cadence, and no duplication

Session still up from R3 (or fresh). Inject 30 fixes at ~1/s:

```bash
for i in $(seq 1 30); do
  adb shell cmd location providers set-test-provider-location gps --location 48.8584,2.2945
  sleep 1
done
```

- **PASS:** ≥ 24 `GpsLocation: fix received` lines during the window (the OS may throttle a few;
  count from `HUR_Log_*.txt` if logcat wrapped). Exactly one `first fix after <N>s` per app
  launch.
- **Measure:** the count, and the `<N>` from the first-fix line.

### R5 — the backstop gives up, and re-arms

Directly after R4, with the session live: stop injecting and wait **75 s**, then inject one fix,
then stop again and wait another **75 s**.

- **PASS:** exactly one `no GPS fix for <N>s, stopping resend` line per starvation window — two in
  total, each with N in 60–66 — and a `fix received` between them (the re-arming fix).
- **FAIL:** zero stale lines (the bound never fired), more than one per window (the once-latch is
  broken), or N far outside 60–66.
- **Measure:** both N values.

### R6 — toggle off

`gps-navigation=false`. Force-stop, fresh session per protocol. Inject two fixes during the
session (keeps the test provider active as a control).

- **PASS:** the whole capture contains **no** `Sensor Start Request sensor: LOCATION` and **no**
  `LOCATION sensor requested.` lines, and the session is otherwise healthy (`Handshake: SSL
  handshake complete`, video up).
- **FAIL:** either line appears with the toggle off.

### R7 — the gate refuses, and says which half

Remove the test provider and disable location; restore `gps-navigation` to absent:

```bash
adb shell cmd location providers remove-test-provider gps
adb shell cmd location set-location-enabled false
```

Fresh session per protocol (the GPS service only starts once a connection is up).

- **PASS:** `GpsLocation: not requesting updates, ACCESS_FINE_LOCATION granted=true
  GPS_PROVIDER enabled=false` appears, and priming reports
  `No recent GPS fix to prime with.` — nothing goes on the wire.
- **FAIL:** a `sentOnWire=true` with location disabled (quote where the fix came from).

Afterwards, restore: `cmd location set-location-enabled true`.

---

## 7. Do not re-run

Nothing — this is the thread's first round. The phone-spoof end-to-end check (mock app on the
phone, AA stays on the head unit's position) was already done on the author's unit against an
earlier build; do not spend rig time reproducing it.

---

## 8. Report back

The numbers that decide the shipping question:

1. **R3's verdict** — `sentOnWire=true` on the first try, or not. This is the round.
2. **R4's count** — fixes received out of 30 injected.
3. **R5's two N values** — both in 60–66, exactly one line each.
4. Whether `AapTransport: dropping sensor events` appeared **anywhere**, and if so its full text.

Results file: `native-gps-forwarding-round1-results.md`, format per template §7.
