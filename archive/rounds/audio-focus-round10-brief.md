# Round 10 brief — does sustained poking break the head unit's own hands-free link?

Read `TESTING-TEMPLATE.md` §7a first. Round 9 added three things to it that this round depends on
completely, including the recipe every run here is built around.

**Read `audio-focus-round9-results.md` too.** This round exists because of one detail in it, and the
brief below assumes you know what R3 found.

---

## 1. Build

**Candidate:** `fix/744-call-audio-hfp` @ `d64d7802` — **unchanged since round 9**. No new commits, no
rebuild.

### R0 — installed-APK gate

```bash
adb shell md5sum /data/app/*/com.andrerinas.headunitrevived*/base.apk
```

**PASS:** `9e0f9dee4259ccbd150bf485cbf37316`, the md5 round 9 recorded. If it matches, skip
`build_hur.sh` and `run_unit_tests.sh` entirely — round 9 already proved this tree compiles and its
219 tests pass, and nothing has changed since.

**If it does not match**, something replaced the package between rounds. Rebuild from the branch and
say so in Setup notes; do not measure anything against an unknown APK.

---

## 2. What this is, and what round 9 left open

Round 9's R3 found that on this rig call audio comes out of **the car**, carried entirely by the head
unit's own stock `HfpClientConnectionService` — a classic-Bluetooth profile that exists whether or not
Android Auto is running, and which nothing in this app has any part in. That reframed the question and
it is the right finding.

But it did not test the mechanism the round was after, and the timestamps in your own results show
why. `NativeHandoffPolicy.shouldPoke` stops the poke loop the moment a session is connected, so
**during R3's call the poke was not running.** It is not running during the reporter's calls either.
The poke can only ever act *before* the session, in the window where the head unit's hands-free
client is trying to establish its link to the phone.

Round 9 brushed against exactly that and came out clean:

| | |
|---|---|
| 13:08:27–33 | R5 forces a poke — HFP-AG attempted and failed, then HSP-AG failed |
| 13:08:42 | session comes up |
| 13:15:40 | R3's call on that same session → audio came out of the **car** |

So **one** round of failed HFP-AG attempts did not break anything. The reporter's poke loop is
unbounded and keeps firing while his session takes far longer to form, so one round and dozens are
not the same experiment. **Round 10 is that experiment.**

### The hypothesis, stated so it can fail

> Repeated RFCOMM connect attempts to the phone's **HFP-AG** record (`0000111f`) — the same record the
> head unit's own hands-free client uses — prevent that client's link from forming, or knock it down
> once formed. The attempts do not need to *succeed*; on both this rig and the reporter's they fail.

If that is true, sustained default poking should visibly damage the hands-free link while
sustained HSP-AG-only poking does not. If both survive twenty minutes, the hypothesis is dead on this
hardware and the branch it was built for should be dropped rather than merged.

---

## 3. What is different about this round

**The whole round runs with no Android Auto session at all.** That is deliberate, not a shortcut. The
measurement is about classic Bluetooth, and §7a records that the phone's own reconnect beats the poke
in 3-6 s whenever it has recently seen the car — which is why round 9 could barely make the poke run.

**The poke-forever recipe:**

```
phone Wi-Fi   OFF      <- no session can form, so the poke loop never exits
phone Bluetooth ON     <- so the pokes actually reach a live radio
head unit     wifi-connection-mode=3, app launched
```

With no route to a session, the loop never reaches its
`Stopping poke retry loop (… session=true)` exit and keeps firing every ~15 s for as long as you let
it. **Confirm it is actually running before starting any clock** — count the
`Calling socket.connect() … via HFP-AG` lines over the first two minutes and expect roughly one round
per 15 s. If the count is zero, the recipe has not taken and nothing below means anything.

**Check the bond first, per §7a.** Round 9 lost its opening to devices that were Bluetooth-on but not
paired with each other. Confirm the bonded list on **both** sides before anything else.

**Log level: DEBUG (`log-level=1`).** The poke failure line is `AppLog.d`, and counting poke rounds is
the spine of this round.

---

## 4. Settings keys

| Key | Element | Meaning |
|---|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | Native AA. Required for the poke loop to exist. |
| `bluetooth-wake-mode` | absent, or `<int name="bluetooth-wake-mode" value="1" />` | absent = default HFP-AG then HSP-AG; **1 = HSP-AG only** |
| `log-level` | `<int name="log-level" value="1" />` | DEBUG |

`set_hu_prefs.sh` (your round 9 addition) is the right tool here — several keys per run, and no
relaunch between them.

---

## 5. The lines and commands that decide the runs

**Poke rounds — `NativeAaHandshakeManager`, all prefixed `OPENHU`:**

```
NativeAA: Calling socket.connect() for <name> via <HFP-AG|HSP-AG> (<uuid>)...
NativeAA: Poke via <HFP-AG|HSP-AG> to <name> (<addr>) failed: <reason>     <- DEBUG
NativeAA: Successfully poked <name> via <HFP-AG|HSP-AG>. Holding <n>ms...
NativeAA: Stopping poke retry loop (settling=<b>, handshake=<b>, session=<b>).
```

**The stub hands-free record:**

```
NativeAA: HFP connection accepted from <name> (<addr>) on radio [<radio>] — ...
```

**The head unit's own hands-free client link — this is the measurement.** Round 9 proved this
tooling works; reuse it rather than inventing another:

```bash
# link state, sampled on an interval
adb shell dumpsys bluetooth_manager | grep -iE "headsetclient|hfpclient|profile|connected"

# during a call: where the audio actually is
adb shell dumpsys audio | grep -A 4 "Audio mode"
#   expect: Mode: MODE_IN_CALL
#           Active communication device: ... type:speaker ... name:MT50_...   <- the car
# a phone-side route shows a different device, or no MODE_IN_CALL on the head unit at all

# call lifecycle
adb shell dumpsys telecom | grep -iE "hfpclient|setCallState"
```

Sample the link state **every 30 s** through R2 and R3 and keep the series — "it was up at the start
and down at the end" is much weaker than "it dropped at minute 7, after 28 poke rounds".

---

## 6. Runs

Three states, each differing from the next by exactly one thing. That is what makes the result
attributable:

| Run | App | Poke targets | Isolates |
|---|---|---|---|
| R1 | not running | none | the baseline |
| R3 | running, `bluetooth-wake-mode=1` | HSP-AG only | R1→R3: the stub record + the HSP-AG poke |
| R2 | running, key absent | HFP-AG then HSP-AG | R3→R2: **the HFP-AG poke, alone** |

### R1 — baseline: calls work here with no HUR at all

```bash
adb shell am force-stop com.andrerinas.headunitrevived
```

Confirm the app is really gone (no `AapService`, no listeners), then confirm the head unit's
hands-free client link to the phone is up, place a call, answer, hold ~30 s, hang up.

- **PASS:** the link is up and the call audio comes out of the **car**.
- **FAIL / INCONCLUSIVE:** if the link will not come up, or the call does not route to the car with
  the app not even running, **stop the round here and report that**. It would mean this rig has
  stopped being able to measure the thing R2 and R3 are about, and every run below would be noise.
  Round 9's unpaired-devices escape is the precedent.

### R2 — sustained poking at the defaults *(the point of the round)*

`bluetooth-wake-mode` absent. Apply the §3 recipe and confirm the loop is running.

Then let it run **until the hands-free client link drops, or 20 minutes pass, whichever comes
first**, sampling every 30 s.

- Record **the poke round count** at the end, and how many named HFP-AG.
- Record **the link-state series** — and if it dropped, the minute and the round number it dropped at.
- Then, **with the poke loop still running**, place a call. Answer, hold ~30 s, hang up. Record where
  the audio came out.

- **PASS (hypothesis refuted):** link up throughout, call audio out of the car. Say how many poke
  rounds it survived — that number is the result, not just "PASS".
- **FAIL (hypothesis confirmed, and the finding of this whole investigation):** the link drops, or
  the call routes to the phone. Quote the drop with its timestamp and the poke round that preceded
  it.

### R3 — the same, HSP-AG only

`bluetooth-wake-mode=1`. Identical recipe, identical duration, identical call.

**Only run this if R2 showed damage.** If R2 came back clean there is nothing for R3 to be a control
for — mark it INCONCLUSIVE, say that R2's clean result is why, and do not spend twenty minutes
proving a negative twice.

If it does run: R3 clean while R2 was damaged is a direct causal demonstration, and the setting
ships on the strength of it.

### R4 — the stub hands-free record, over a long window

Free — the captures from R2 and R3 already contain it. Round 9 answered "no accept" across one ~60 s
window; these give twenty minutes each with the listener up.

- Report simply whether `HFP connection accepted` appears at all, and quote it if it does.

---

## 7. Do not re-run

- The build and unit tests. R0 is an md5 check; round 9 did the rest.
- `bluetooth-wake-mode`'s own behaviour — round 9's R2 and R5 established that it suppresses HFP-AG
  and that the default is unchanged. This round uses the setting, it does not test it.
- Round 8's audio-focus work. No Android Auto session is involved here at all.

---

## 8. Report back

`audio-focus-round10-results.md` on this branch, in §7's format. Four numbers decide it:

1. **R2's poke round count**, and whether the hands-free link survived it. This is the headline
   whichever way it goes.
2. **Where R2's call audio came out.**
3. **R1's baseline** — without it, 1 and 2 mean nothing.
4. **R3's comparison**, if R2 gave it something to be a control for.

A clean R2 across twenty minutes and forty-odd poke rounds is a real, useful result: it refutes the
mechanism on this hardware and tells us to stop building for it. Say so plainly rather than softening
it — a negative that is clearly stated is what lets the branch be dropped instead of merged on a
theory.
