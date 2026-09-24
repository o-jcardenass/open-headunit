# wire-corruption-escalation — round 1 brief: a truncated frame should repair in seconds, not a GOP

## 1. Build and baseline

**Candidate:** `fix/wire-corruption-escalation` @ `96eefddb` (4 commits) on the fork.
**Baseline:** `origin/main` @ `562c8dcf` (identical to `fork/main`).

**History was rewritten since this branch was first pushed.** It was rebased onto the new `main`, so
the SHAs `12598458`, `7ac88050` and `64630ce7` from any earlier note are gone and only
`96eefddb`'s ancestry is reachable. Fetch and reset; do not pull.

```bash
git fetch fork
git checkout -B wire-corruption-escalation fork/fix/wire-corruption-escalation
git log --oneline -4          # expect 96eefddb, 64630ce7, 7c896716, ca6a225a
```

| commit | what it does |
|---|---|
| `ca6a225a` | `AapVideo`'s corruption path can reach the focus cycle instead of only the inert nudge. **The subject of this round.** |
| `7c896716` | `Decrypted payload too short` now carries chan/flags/enc_len. |
| `64630ce7` | Stops dropping a video run's last fragment when its payload is under 2 bytes. **Not testable on this rig — see §3.** |
| `96eefddb` | A session banner in every exported log: build, device, settings, log level. |

## 2. What this is and why it exists

A reporter's Galaxy Tab S7 FE loses the **last fragment** of a video frame every so often. The run
never gets its flag 10, the reassembler reports `TRUNCATED_PREVIOUS`, and every frame after it
predicts from a reference that was never decoded, so the picture smears until a keyframe arrives.

AAP has no keyframe request. The only lever that produces one on demand is releasing video focus and
taking it back, which `KeyframeCycleEscalationPolicy` schedules. `AapTransport` armed `AapVideo`'s
corruption callback with `escalatable = false`, so that path sent only the gain-only nudge — measured
inert across four rounds — and then armed no clock at all.

Measured in one 2h21m reporter session, four corruption events:

| path | picture broken for |
|---|---|
| decoder shed a reference frame (was escalatable) | **2.78 s** |
| wire truncation | **27.53 s** |
| wire truncation | **11.16 s** |
| wire truncation | **19.23 s** |

`ca6a225a` gives the truncation path the same rendered-frame gate the dropped-frame path already
uses, so it reaches the same lever. This round asks whether that holds on hardware.

## 3. What is different about this round

### The rig can reproduce the reporter's exact fault, with a mode that already exists

`debug-video-fault-injection=3` (`DROP_LAST_FRAGMENT`) is **observationally identical** to the real
defect. Both discard the flag-10 message at the same point in the path:

- The real fault: `AapMessageIncoming.decrypt` returns null, so the message never reaches
  `AapVideo.process`. But `auditFragment` already counted it in the reader, so the framing audit
  stays silent.
- Mode 3: `AapVideo.process` drops the message after the reader has framed and audited it. The
  framing audit stays silent for the same reason.

Downstream of that point they are the same event: the assembler's run stays open, the next flag 9 or
11 raises `TRUNCATED_PREVIOUS`, and there is **no `AapRead:` audit line**. That absence is the
signature to check — it is what tells this fault apart from a lost middle fragment, and it is what
the reporter's log shows.

One immaterial difference, recorded so it is not mistaken for something: the real fault never reaches
`AapMessageHandlerType.handle`, so it does not stamp the link-gap monitor. Mode 3 does. Nothing in
this round reads that.

### What this round does *not* test

**`64630ce7`, the actual cause fix, is not reachable by any injector mode.** The injector drops a
whole message at the assembler; the guard dropped a message at decrypt based on its payload being
under two bytes, and no mode produces a short payload. Producing one for real needs an access unit
whose size mod 16384 is 1, which is roughly one in 16384 fragmented frames — at this rig's candidate
rate that is hours per event, so it is not worth scheduling. Its coverage is `AapMessageFramingTest`
in R0 plus the reporter's next log. **Do not read R1 as evidence for it.**

### Candidate scarcity is the binding constraint, and this thread has already lost runs to it

From `release-next` round 6, same rig, same injector, different mode:

- mode 5 at `rate=3` landed **30 faults in 114.6 s** on one screen state;
- the same setting landed **30 faults in 725.6 s** on another — 6.3x apart;
- mode 5 at `rate=300` landed **0 faults in 11.4 min** (58 candidates), even after panning the map.

So the rate cannot be chosen in advance. **R1a is a calibration run and is not optional.**

### The escalation has a 60 s cooldown, which changes how faults must be spaced

`CYCLE_COOLDOWN_MS` is 60 s and `MAX_CYCLES_PER_SESSION` is 3. Faults landing closer than 60 s apart
exercise the cooldown, not the escalation. That is why R1 uses **`budget=1`**: one fault, one
escalation, one repair, no spacing to control.

### Verify, do not assume

Read the settings the run actually ran under out of the new banner line rather than from this brief.
That is what `96eefddb` is for, and R5 checks it.

## 4. Settings keys this round needs

App stopped, written into `shared_prefs/settings.xml`, per TESTING-TEMPLATE §1. All ints.

| Key | Element |
|---|---|
| `debug-video-fault-injection` | `<int name="debug-video-fault-injection" value="3" />` |
| `debug-video-fault-rate` | `<int name="debug-video-fault-rate" value="N" />` (N from R1a) |
| `debug-video-fault-budget` | `<int name="debug-video-fault-budget" value="1" />` |
| `log-level` | `<int name="log-level" value="2" />` (INFO) |

**INFO is enough for every line this round reads, and is deliberate.** The two `LOG_VERBOSE` guards
in `AapTransport` wrap `Sent size:` and a message dump, neither of which this round uses;
`VideoDecoder`, `AapVideo`, `VideoFaultReporter` and `LogExporter` have no verbose or debug guards at
all. VERBOSE would only cost ring buffer.

**Leave `video-codec`, `view-mode`, `resolution-id` and the wireless keys as found.** Do not
normalise them. Diff `settings.xml` against a fresh backup at the start of the round and state the
delta in Setup notes even if it is zero (§7a), and quote the banner line from each capture.

To clear the injector between runs, run only the delete half of the write template for all three
`debug-video-fault-*` keys. An absent key reads as its default.

## 5. The lines that decide every run

Verified with `grep -F` against `96eefddb`. Format specifiers are as they appear in source.

```
AapRead: FAULT INJECTION IS ON
AapVideo: FAULT INJECTION IS ON
AapVideo: FAULT INJECTED (#%d of %d candidates): %s on flag %d, len=%d
AapVideo: fault injection - <mode> 1-in-<rate>, <n> candidates seen, <n> injected, budget n/n
AapVideo: fault injection budget spent after %d faults - the stream is clean from here

AapVideo: Previous frame was truncated! Resetting assembly state.
AapVideo: %s, requesting keyframe to recover stream          ← "frame truncated" fills %s
AapVideo: reassembly anomalies over %dms: truncated=%d, orphan=%d, headless=%d, overflow=%d

AapTransport: Requesting recovery keyframe (unsolicited focus gain).
AapTransport: picture unrepaired for %dms - cycling video focus (%d/3)
AapTransport: retaking video focus to complete the keyframe cycle
AapTransport: picture unrepaired for %dms, no cycle available now (%d/3 spent) - waiting for the phone's own keyframe
AapTransport: picture unrepaired for %dms but the stream is still losing frames (last %dms ago) - holding the cycle until it settles

VideoDecoder: keyframe reached the codec (%d bytes)
VideoDecoder: keyframe decoded - the picture is repaired
Throughput over %dms: rendered=... fed=... dropped=... skipped=... inputWait=...

LogExporter: session | build=... device=... video=... wifi=... logLevel=...
Decrypted payload too short: %d  chan: %d %s  flags: 0x%02x  enc_len: %d
```

The injector prints its summary every 15 s while active. **That summary is the run's own progress
meter** — if `candidates seen` is climbing and `injected` is stuck at 0, the rate is too high and the
run is heading for INCONCLUSIVE; say so rather than letting it run out.

## 6. Runs

### R0 — build and unit-test gate

Build both APKs with the rig's `build_hur.sh`. This branch has **never been compiled**.

- PASS: clean build of `96eefddb`, all unit tests green, and `AapMessageFramingTest` present and
  passing (it is new in `64630ce7`, 4 tests). Report the total count and that file's count.
- FAIL: anything red. Stop the round and escalate.

### R1a — calibration (no verdict, this is setup)

Injector mode 3, `rate=100`, `budget=0` (unlimited), INFO. Connect, leave the default post-connect
screen, capture 3 minutes.

Read the 15 s summary lines and record **flag-10 candidates per second**. Then choose R1's rate as:

```
rate ≈ 90 × candidates_per_second      (clamped to at least 2)
```

which aims one fault roughly every 90 seconds. Report the measured candidate rate and the rate you
chose. If fewer than 10 candidates appear in 3 minutes, pan the map with `input swipe` and extend to
6 minutes before choosing; if still under 10, say so and use `rate=2` for R1.

### R1 — the point of the round: one truncated frame, repaired

Mode 3, rate from R1a, **`budget=1`**, INFO. Fresh launch, capture from before launch.

Wait for `FAULT INJECTED ... on flag 10`, then keep capturing for a further 90 s.

**PASS** — all of:

- `AapVideo: Previous frame was truncated!` within ~100 ms of the injected fault;
- **no `AapRead:` framing-audit line attributable to it** (the signature that this is a last-fragment
  loss and not a middle one);
- `AapTransport: picture unrepaired for 2...ms - cycling video focus (1/3)` about 2 s after;
- `retaking video focus` about 400 ms after that;
- `VideoDecoder: keyframe decoded - the picture is repaired` **within 4 s of the truncation**.

**FAIL** — a truncation with no `cycling video focus` line after it, or a repair later than 8 s.

**INCONCLUSIVE** — no fault landed. Do not lower the rate mid-run and keep the capture; report the
candidate count.

Report the interval from `Previous frame was truncated` to `picture is repaired`, in ms. **That
single number is the round.** Pair it with the `Throughput over` line covering the same window, so a
fast repair on a stream that was not actually running is not read as a pass.

### R2 — the same fault on the baseline

`origin/main` @ `562c8dcf`, same injector settings except **`budget=3`**, same screen state, fresh
launch.

The baseline has `KeyframeCycleEscalationPolicy` and the nudge; what it does not have is
`AapVideo`'s path being allowed to arm the clock. Expect the nudge and then nothing until the phone's
own keyframe.

**Three faults, not one, and this is why:** the phone's GOP is about 69 s, so an unrepaired picture
heals after a uniformly distributed 0-69 s. A single baseline sample can land at 3 s by luck and
would look like a pass. Compare **medians of three**, and if only one fault lands, the A/B is
**INCONCLUSIVE** regardless of what that one number says.

- PASS: no `cycling video focus` line attributable to any of the three truncations, and a median
  repair interval materially above R1's.
- FAIL: baseline also escalates — which would mean R1 proved nothing and the mechanism is elsewhere.

### R3 — the budget, and what a long drive looks like

Candidate, mode 3, rate from R1a, **`budget=4`**. Run until all four land or 15 minutes pass.

The reporter's session had four corruption events. Three cycles are all a session gets.

- PASS: the first three truncations each produce `cycling video focus (1/3)`, `(2/3)`, `(3/3)`, and
  the fourth produces `no cycle available now (3/3 spent)`.
- **If the faults land closer than 60 s apart, that is not a failure** — report the actual spacing
  and which lines appeared. Cooldown behaviour is a result too. Mark the budget-exhaustion question
  INCONCLUSIVE in that case rather than forcing it.

### R4 — sustained loss must still hold the cycle

Candidate, mode 3, **`rate=3`, `budget=30`**. This is the setting that lands faults quickly
(`release-next` round 6 got 30 in 114.6 s on one screen).

A cycle spent while the wire is still breaking buys a keyframe that arrives broken and stamps a 60 s
cooldown over the one that would have worked, so the policy holds it. That guard must not have
regressed.

- PASS: at least one `holding the cycle until it settles` line, and after
  `fault injection budget spent after 30 faults` a `picture is repaired` within 90 s.
- FAIL: three cycles spent inside the fault storm with none left when it stops, or no repair after
  the budget is spent.

Report the time from `budget spent` to `picture is repaired`. Also report `dropped=` totals: mode 3
at 1-in-3 is severe and the decoder may shed on its own, which would put a second, legitimate arming
path in the capture. Say so if it happens rather than attributing everything to the truncations.

### R5 — clean control, and the banner

No `debug-video-fault-*` keys at all (delete them), INFO, 10 minutes of ordinary use.

- PASS, all of: zero `Previous frame was truncated`; zero `Decrypted payload too short`; zero
  `cycling video focus`; `Configuring decoder` exactly once; `dropped=` summed over the session at or
  below 0.15 per minute.
- Pair those zeroes with the `Throughput over` fps, so a silent capture on a stalled picture is not
  read as a clean one.

Then, in the same run, check `96eefddb` two ways:

1. Export with the capture running. The banner must be in the file.
2. Stop the capture, delete the capture file, export again so the ring-buffer path is taken. The
   banner must be in that file too.

Quote the banner and confirm `build=` matches the APK you installed and `video=codec:` matches what
is actually in `settings.xml` — those two are the whole point of the line.

## 7. Do not re-run

- The `release-next` thread's mode-5 and mode-2 runs. Different fault, different code path.
- Anything about the read-desync silence: `release-next` round 6 R6 covered it across five captures.
- `AapMessageFraming` behaviour beyond R0's unit-test gate. It is pure and fully covered on the JVM.

## 8. Report back

Five numbers decide whether this ships:

1. **R1's truncation-to-repair interval in ms**, and whether a `cycling video focus` line is in it.
2. **R2's median of three on the baseline**, and whether any cycled.
3. **R4: did `holding the cycle until it settles` appear**, and the budget-spent-to-repair time.
4. **R5's five zeroes**, with the fps alongside each.
5. **R1a's candidate rate**, so the next round can skip calibration.

And the standing question from §7a: `grep -c "createGroup SUCCESS"` per run — more than 1 is a
discard.
