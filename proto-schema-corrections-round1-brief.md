# proto-schema-corrections, round 1 brief: two protocol asks nobody has measured

## 1. Build and baseline

| | Branch | SHA | Commits | Gate |
|---|---|---|---|---|
| Candidate | `fix/audio-sink-and-wireless-bring-up` | `e9c97fd8` | 5 on `main` | 2052 tests, 0 failures |
| Baseline | same APK, both settings off | | | |

```bash
git fetch fork
git checkout -B proto-schema fork/fix/audio-sink-and-wireless-bring-up
./gradlew :app:testGithubDebugUnitTest      # 2052, 0 failures
```

**The branch was compacted to five commits on 2026-09-17**, so what is on `fork` is a rewrite of
what was there before. Reset any older local copy of it rather than pulling it.

One APK for the whole round. Both levers are settings, so no second build is needed and no arm
here rebuilds anything.

## 2. What this round is

A field-by-field audit of our `.proto` files against Google's own AAP schema now rides on this
branch, as the single `AAP:` commit. Most of it is renames that change nothing on the wire. Two
things put new bytes out, and each sits behind a setting that ships **off** because nobody has ever
seen a phone answer them:

- **`WifiVersionRequest` field 3**, the bands our access point can offer, sent when
  **Native AA wireless version exchange** is on. The audit's lead was that every phone answers the
  version response with `-8 STATUS_NO_SUPPORTED_WIFI_CHANNELS`, and field 3 is how a head unit
  states its channels.
- **`ServiceDiscoveryResponse` field 16**, a `ConnectionConfiguration` asking for a 15 s ping
  timeout instead of the protocol's 3 s and 64 KB socket buffers instead of 16 KB, sent when
  **Ask for a longer link timeout** is on. It is aimed at a radio that goes off channel for a few
  seconds mid session, which is the shape behind the periodic-outage reports.

**What this round is not.** It does not grade the hands-free wake riding in the same branch. Both
settings here are off in that round and every run there leaves them off, so the two are independent.

**Carry the tension in, do not resolve it in advance.** Nineteen of nineteen rig captures on file
show `-8` on sessions that went on to connect, so `-8` on its own is noise. P1 is what turns
"field 3 changes the answer" from a hypothesis into a finding, in either direction. **A P1 that
changes nothing is a result, not a failure**, and it retires the audit's best remaining lead.

Rig: **D-SAM as head unit, D-POCO as the phone.** Run order: **P0, P3, P1, P2.** P3 first on
purpose: the control has to hold before either lever means anything.

## 3. Settings, and where they are

Both rows are in **Settings**, no adb required.

| Lever | Row | Section | Default |
|---|---|---|---|
| field 3 | Native AA wireless version exchange | Wireless, Native AA only | off |
| `ConnectionConfiguration` | Ask for a longer link timeout | Wireless, all modes | off |

Change one at a time and Save. Saving a wireless setting restarts the launcher and re-pokes about
2 s later, so expect the projection to come up over whatever is on screen.

## 4. Runs

### P0. Gate

`./gradlew :app:testGithubDebugUnitTest` reads 2052, 0 failures. Record the APK's identity by its
symbol check before anything else, as every round here does.

**PASS** when the gate matches and the installed APK is the one just built.

### P3. Control: both off

One Native AA session, start to a live picture, then a clean exit. Both settings off.

```bash
grep -c "SSL handshake complete" log.txt
grep "Media Sink Setup Request: . on channel VIDEO" log.txt
grep "Throughput over 5000ms" log.txt | tail -5
grep "Asking for ping timeout" log.txt     # want: nothing
grep "WifiVersionRequest (Type 4)" log.txt # want: nothing
```

**PASS** when the session forms and projects, and neither new line appears. A `ConnectionConfiguration`
line here means the setting is not actually off, and the rest of the round is void until that is fixed.

### P1. Field 3: the version exchange on

Turn **Native AA wireless version exchange** on. Three bring-ups from a cold arm, each to a live
picture or to a recorded failure.

```bash
grep "NativeAA: \[TX\] Sending WifiVersionRequest (Type 4)" log.txt
grep "NativeAA: \[RX\] WifiVersionResponse" log.txt
```

Record, per bring-up, the whole `WifiVersionResponse` line. Three fields decide it:

- `status=` : is it still `NO_SUPPORTED_WIFI_CHANNELS(-8)`, or something else.
- `channelType=` : present at all, and if so which value. This is the phone saying which band it
  wants and is the only place it ever says so. **Absent on every run is itself the finding**: it
  means the phone does not answer our claim.
- `v4.2` or lower: the protocol the phone offered, which gates whether it reads field 3 at all.

**PASS** when three bring-ups all reach a picture and the response line is captured on each.
**Grade the levers separately from the session**: a bring-up that fails with the version exchange on
is a FAIL of the exchange, and it is already known that a dongle refuses it, so record which.

**What a change looks like**: `status` moving off `-8`, or `channelType` appearing where P3 had no
version exchange to compare against. Either is a finding worth a follow-up round. Neither changing
retires the lead.

### P2. `ConnectionConfiguration`: a longer ping timeout

Version exchange back **off**. Turn **Ask for a longer link timeout** on. This lever is
transport-agnostic, so run it on whatever transport last produced a periodic outage on this rig.

```bash
grep "\[ServiceDiscovery\] Asking for ping timeout" log.txt
grep "Handshake: Version response received" log.txt
grep "Handshake: the phone asks for ping timeout" log.txt
grep "station scans:" log.txt
```

**Read the ordering correctly, or this run grades nothing.** The AAP version handshake happens
*before* `ServiceDiscoveryResponse` goes out, so `Handshake: the phone asks for ping timeout` is the
**phone's own ask of us**, not its reply to ours. There is no reply. The protocol gives the phone no
way to acknowledge field 16, which is exactly why this has to be measured behaviourally.

Hold a session for **at least 15 minutes** with the map moving, and record:

- every `station scans:` line, with its cadence
- whether the session survived each scan window, and the `Throughput over 5000ms` rows either side
- the time to teardown if it ends, and what ended it

**PASS** when a session that previously ended inside a scan window now rides through one, with the
scan lines present to prove the scans still happened. **UNTESTABLE**, not FAIL, when no scan occurs
in the window: this lever cannot be graded against a link that never stalled, and the run must be
repeated with the station joined to something that makes it scan.

**FAIL** if the session ends sooner or more often than P3's control, which would mean asking for
these parameters costs something.

## 5. What this round cannot settle

- Whether the phone honours field 16 at all. Only the session's survival is observable, so a single
  clean 15 minutes is weak evidence and two rounds' worth is not much stronger. Say so in the results
  rather than calling it honoured.
- Protocol 1.6. Google gates the ack timestamps and the underflow notification on it, and announcing
  1.6 obliges us to populate them, so it is a spike and is deliberately out of this round.
- Anything about the hands-free wake. Different setting, different round, both off here.
