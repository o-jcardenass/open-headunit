# Round 9 brief — the wake poke and the phone's hands-free record

Read `TESTING-TEMPLATE.md` first if you have not, and **read §7a before planning the runs** — three
of its quirks decide the shape of this round, and one of them rules out the reporter's own scenario
entirely.

> **This brief replaces an earlier round 9 brief** that named `dbdb4883` and three settings. That
> version was cut back: it tested two mechanisms at once and gated a Bluetooth listener's whole
> lifecycle for a hypothesis nobody has confirmed. Ignore it; only what is below exists on the branch.

---

## 1. Build

**Candidate:** `fix/744-call-audio-hfp` @ `d64d7802` on the `fork` remote.

One commit, stacked on `fix/audio-focus-pauses-bt-source` @ `26032e65` — the SHA round 8 measured,
now restored after a detour. So this build carries round 8's audio-focus fix *and* the new setting,
in one checkout:

```bash
git fetch fork --prune --prune-tags
git checkout -B fix/744-call-audio-hfp fork/fix/744-call-audio-hfp
git log --oneline -4
# expect: d64d7802, 26032e65, a2381b46, d2dff1df
```

**No baseline APK.** The control is a one-key pref write on the candidate.

### R0 — build gate

Rig scripts (§5): `run_unit_tests.sh`, then `build_hur.sh`.

- `PlaybackFocusPolicyTest` still **20 green** — the regression canary for round 8's work.
- **`BluetoothWakePolicyTest` present and green** (9 tests).
- Full suite green. Record the APK md5 and confirm it is the installed package (§5).

**If R0 fails, stop and report.** This branch has never been compiled anywhere — fork branch pushes
do not trigger CI (the workflow fires on `pull_request` and pushes to `main` only), so **R0 is the
first build check this code has ever had.** A compile error here is expected-ish and is not a
finding; just report it.

---

## 2. What this is

Android Auto **never carries phone calls over the projection link**. Calls go over Bluetooth
hands-free between the phone and the head unit, so the head unit's own hands-free profile has to hold
that link. Issue #744's reporter — who has now confirmed round 8's audio-focus fix works — says calls
are heard on the phone instead of through the car, that the screen says Bluetooth is not connected
while the phone says it is, and that this is intermittent run to run. They call **3.1.1 stable**.

Diffing `v.3.1.1..v.3.2.3` over the Bluetooth code finds exactly one change touching the record a
call rides on:

> **`38a82373` — "Retry the Native AA wake poke continuously and try HFP before HSP"** (2026-07-30,
> in v.3.2.0, **not** in v.3.1.1). Before it the poke made one pass and connected only to **HSP-AG**
> (`00001112`). After it, it retries while no session is up and tries **HFP-AG** (`0000111f`) first
> — the record a head unit's hands-free client must reach to carry a call, which a phone offers to
> one device at a time.

**The new setting is `bluetooth-wake-mode`.** `0` (default) is today's HFP-AG-then-HSP-AG. `1` is
HSP-AG **only**, with no fallback — literally v.3.1.1.

**Why no fallback, since it looks harsh.** `pokeDevice()` stops at the first target that *connects*.
On the reporter's unit **both fail**, every cycle:

```
16:39:34.649  Poke via 0000111f (HFP-AG) … failed: read failed, socket might closed or timeout
16:39:34.659  Poke via 00001112 (HSP-AG) … failed: read failed, socket might closed or timeout
16:39:49.672  Poke via 0000111f … failed
16:39:49.679  Poke via 00001112 … failed
```

So a mere reorder would fall straight through and keep attempting HFP-AG every ~15 s exactly as now.
Nothing is being *held* on that hardware — what 3.2.0 added is the repeated **attempt**. Only a mode
with no fallback removes it. That their own stable version had no fallback either is the evidence it
is safe for whoever selects it; everyone else keeps the default.

**This rig may behave differently from the reporter's, and that is worth measuring.** If HSP-AG
actually *connects* here, `Successfully poked … via HSP-AG` will say so, and mode 1 will look very
different from what the reporter would see. Record which target succeeded, always.

### Also in scope, for one line only

The app publishes its own stub Hands-Free service record (`0000111e`, "Hands-Free Unit") next to
whatever the unit's Bluetooth stack publishes, behind a responder that answers `OK` to everything and
negotiates neither a codec nor a SCO link. A phone that attaches its hands-free link there has none
with the head unit. **Whether that ever happens is unknown** — the line has appeared in no reporter
log — so nothing was built to control it. R1 exists purely to find out, costs no call, and decides
whether that work is ever worth doing.

---

## 3. What is different about this round

**Two of the five runs need a real phone call, and no round has ever placed one.** Round 8 was asked
to and declined — correctly — as too risky to script blind on a device that can dial a real number.

**You choose the number, and you judge what is safe to dial.** A second line you own, a carrier's free
automated service, anything you are comfortable calling repeatedly. **If nothing is safe, mark R3 and
R4 UNTESTABLE and say so.** R0, R1, R2 and R5 still return a decisive round without a single call —
that is why they are ordered first.

**Do not attempt USB.** §7a records this rig has no USB accessory path, so the reporter's "it happens
on USB too" cannot be reproduced here. Native AA (`wifi-connection-mode=3`) is the only transport, and
it is enough for everything below.

**Link ordering, as round 8 (§7a).** Bring the phone's Bluetooth up once, confirm the link, then leave
the phone alone and reset only on the head-unit side between runs:

```bash
PKG=com.andrerinas.headunitrevived
adb shell am start -a android.intent.action.VIEW -d "headunit://exit"
sleep 3
adb shell am force-stop $PKG
# write the next run's keys, verify, relaunch — phone's radios never touched
```

Confirm the link before each run that needs one, and say so in the results:

```bash
adb shell dumpsys bluetooth_manager | grep -iE "a2dp|avrcp|Connected|Active Device"
```

**Log level: DEBUG (`log-level=1`).** The poke *failure* line is `AppLog.d` and is decisive for R2 —
at INFO you would see only successes and could not tell a suppressed HFP-AG attempt from one that
merely failed quietly.

---

## 4. Settings keys

Written into `shared_prefs/settings.xml` with the app stopped (§1), via the pushed-script method
(§7a). Ints; absent reads as 0.

| Key | Element | Meaning |
|---|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | Native AA. Required throughout. |
| `bluetooth-wake-mode` | `<int name="bluetooth-wake-mode" value="0" />` | **0 = HFP-AG then HSP-AG (today), 1 = HSP-AG only (v.3.1.1)** |
| `log-level` | `<int name="log-level" value="1" />` | DEBUG |

---

## 5. The lines that decide the runs

Verified with `grep -n` against `d64d7802`. All prefixed `OPENHU`.

**The poke — `NativeAaHandshakeManager`:**

```
NativeAA: Calling socket.connect() for <name> via <HFP-AG|HSP-AG> (<uuid>)...
NativeAA: Successfully poked <name> via <HFP-AG|HSP-AG>. Holding <n>ms...
NativeAA: Poke via <HFP-AG|HSP-AG> to <name> (<addr>) failed: <reason>     <- DEBUG
NativeAA: Stopping poke retry loop (settling=<b>, handshake=<b>, session=<b>).
NativeAA: Attempting active poke to device: <name> (<addr>)...
```

**The stub hands-free record — `NativeAaHandshakeManager`:**

```
NativeAA: HFP connection accepted from <name> (<addr>) on radio [<radio>] — the phone's hands-free link now terminates in this app, which cannot carry call audio. If calls are not heard on this unit, look here first.
NativeAA: HFP responder active for <name>
NativeAA: HFP RX: <cmd>                      <- DEBUG, the raw AT traffic
```

**Round 8's audio-focus lines are unchanged** and still decide R5.

---

## 6. Runs

### Call-free — these carry the round

#### R1 — does the phone attach its hands-free link to us at all?

**The cheapest thing here and the one with the longest reach.** Defaults: `bluetooth-wake-mode=0`.

- Start the app in Native AA and let its listeners open.
- Cycle **the phone's** Bluetooth off and on (§7a's lever) and watch for 60 s.
- **Record: does `HFP connection accepted` appear?** Quote it with the peer address, or say plainly
  that it did not appear in the window. If it does, also quote any `HFP RX:` lines that follow.

No PASS/FAIL — it is a measurement, and both answers are useful. An accept means the stub record is
live on this rig and is worth controlling. No accept across a clean Bluetooth-connect window is
evidence it is not, and closes that question for now.

#### R2 — the setting keeps the poke off the hands-free record

`bluetooth-wake-mode=1`. Force a fresh connect so the poke loop actually runs.

- **PASS:** across the whole run, **no poke line of any kind names HFP-AG or `0000111f`** — every
  attempt, success and failure names HSP-AG — and Android Auto still connects.
- **FAIL:** any HFP-AG poke line appears, or the session no longer forms.
- **Record either way: did the HSP-AG poke succeed or fail?** `Successfully poked … via HSP-AG` versus
  the DEBUG failure line. This is the difference between this rig and the reporter's, and it decides
  how much their result can be predicted from this one.
- Record how long the connect took, to compare against R5's.

**A session that stops forming is the most important negative result this round can produce.** It
would mean HFP-AG is load-bearing for the wake on this hardware, and that the default must never
change. Report it prominently rather than as a failure.

### Call-dependent — only if §3's condition is met

Same number for every run. Record where the audio came out in one word: **car** or **phone**.

#### R3 — baseline call at the defaults

`bluetooth-wake-mode=0`, AA session live and projecting. Place the call, answer, hold ~15 s, hang up.

- **Record:** where the audio came out; whether `HFP connection accepted` appeared; whether any poke
  was in flight in the minute before the call.
- **If the audio came out of the car at the defaults, this rig does not reproduce the fault.** Say so
  plainly and mark R4 INCONCLUSIVE rather than running it — that is a real result, and it means the
  mechanism is specific to the reporter's hardware.

#### R4 — the same call with the setting on

Only if R3's audio came out of the **phone**. Repeat R3 exactly with `bluetooth-wake-mode=1`.

- **Did the audio move to the car?** That is the round's headline if it ran.
- If it did, run R3 once more afterwards to confirm the fault still reproduces at the defaults rather
  than having drifted away on its own.

### Regression gate — run this whatever happened above

#### R5 — the default is a no-op

`bluetooth-wake-mode` **absent from `settings.xml` entirely** (delete the element rather than writing
0, so this also covers the absent-reads-as-default path).

- **PASS:** poke lines name **HFP-AG first**, and round 8's R1 still holds —
  `AapAudio: AA audio started (AUDIO) - leaving system audio focus alone (mode=AUTO,
  bluetoothMedia=true)`, no AVRCP PAUSE following, media playing for 60 s.
- **FAIL:** anything differs from round 8's R1, or the poke order is not the default.

---

## 7. Do not re-run

Settled in rounds 6-8 and untouched by this branch:

- The `PlaybackFocusPolicy` matrix beyond R5's single regression check. R5-of-round-8's one-pause
  measurement in particular is done.
- `AppLog.Logger.File` throughput and the `LogExporter` 16 MB cap.
- The settings backup/restore round-trip.

---

## 8. Report back

`audio-focus-round9-results.md` on this branch, in §7's format. What decides it:

1. **R2's verdict, plus which target succeeded.** Does the setting keep every poke off HFP-AG, and
   does Android Auto still connect without it?
2. **R1's answer.** Did the phone ever attach its hands-free link to our stub? Yes or no closes a
   question either way.
3. **R4's headline, if it ran.** Did turning the setting on move call audio to the car?
4. **R5's verdict.** The default is unchanged behaviour.

Say UNTESTABLE plainly for anything §3's call condition rules out. A round that returns R0, R1, R2 and
R5 honestly and places no calls at all is a good round; a round that guesses at a call result is not.
