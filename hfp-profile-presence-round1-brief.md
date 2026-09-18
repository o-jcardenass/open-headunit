# hfp-profile-presence, round 1 brief: make the phone count our Bluetooth as connected

**Candidate:** `fork/fix/hfp-profile-presence` @ `c5609e16`, three commits.
**Baseline:** `fork/fix/bssid-from-interface-address` @ `e6b19c3a`, the candidate's own parent. Using
the parent isolates these three commits from the sixteen beneath them.

```bash
git fetch fork
git rev-parse fork/fix/hfp-profile-presence            # c5609e16...
git rev-parse fork/fix/bssid-from-interface-address    # e6b19c3a...
```

| SHA | What |
|---|---|
| `c53d0d7e` | We open the hands-free exchange instead of only answering it, and hold it with a keepalive |
| `a7de6339` | The wake poke does the same on its own socket, behind a setting, default off. **The point of the round** |
| `c5609e16` | An audio sink record is published, so a phone reads this as an audio device |

No history was rewritten on the parent since round 1; `e6b19c3a` is the same commit that round built.

**Two builds**, candidate and baseline. R1 and R3 are a matched pair, so build both before starting.

---

## 1. Why this round exists

Round 1 established that the BSSID is right and the two-phone leg still fails, in one identifiable
place: the phone never opens the Android Auto channel at all. `Connection accepted from` was 0 across
three candidate attempts and one baseline attempt, and Gearhead logged
`WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE` seven times in a single run.

The addendum read that as the poke's hold being too short. It is not. The other receiver pokes the
same record we do, releases it *earlier* than we do on success, and in its own successful capture the
phone's hands-free profile reached connected and Gearhead dialled us **before** its poke socket was
even opened. The poke is not the variable.

What is: Android Auto gates wireless setup on the head unit's Bluetooth MAC being connected **with a
profile**, and the flag that would relax that to a bare link is off by default in this build. Our
poke opened a channel to the phone's hands-free record and then wrote nothing to it. A phone answers
an incoming connection there by moving its own hands-free profile to *connecting* and waiting for us
to speak first; a silent hold times out in that state and never becomes a connected profile. The same
gap ran the other way: we publish the hands-free record, which is the head unit's role, so the
opening exchange is ours to start whoever opened the socket, and the responder only ever answered.

The candidate opens that exchange, `AT+BRSF` then `AT+CIND=?`, `AT+CIND?` and `AT+CMER`, and once it
is up reads the indicators every two seconds to hold it there.

**R1 is the premise check and comes first.** If the phone's hands-free profile does *not* sit in
connecting during a baseline poke, the mechanism above is wrong and R3 cannot be read as evidence
either way. Say so and stop rather than pressing on.

---

## 2. What is different about this round

Four rig facts change how these runs have to be set up. Three are from the standing template's §7a
and one is new.

- **Do not judge anything by whether a poke connects.** §7a records that both poke targets fail every
  time in some sessions on this rig and succeed 13/13 in others, in the same hardware. Every run
  below measures poke connectivity rather than assuming it, and a run where no poke connected at all
  is INCONCLUSIVE, not FAIL.
- **The automatic poke loop often never reaches `pokeDevice()`.** §7a: the first `while` check
  routinely lands on `handshake=true` because the phone's own reconnect rides the same link, so a
  session can form with zero pokes attempted. Every run that is about the poke uses the manual poke
  broadcast instead of launch-and-watch. Note it holds for **20 s**, not 15.
- **Bonding is checked by hand, on both sides.** Round 1 opened with the two phones not mutually
  bonded and was held until an operator re-paired them. Do that check first, per §7a.
- **The head unit's own Bluetooth must be up before the app launches**, or `start()` silently no-ops
  and no listener, decoy or poke exists at all. Never toggle-then-launch.

**One instrument in this brief is not verified from here.** R1 and R3 read the phone's own profile
state through `dumpsys bluetooth_manager`, and the exact section name and field wording vary by
Android version. Capture the whole hands-free section rather than grepping for one string, and if
nothing resembling a per-device state appears, say so in Setup notes and fall back on the Gearhead
counter in §5. A missing string here is a tooling gap, not a result.

---

## 3. Devices and roles

| Role | Device | What it runs |
|---|---|---|
| **Head unit under test** | **D-MOTO** | the OHU build, wireless mode 3 |
| **Projecting phone** | **D-POCO** | Android Auto 17.5, nothing of ours |
| **Regression head unit** | **D-HU** | the OHU build, wireless mode 3 |

Same assignment as round 1, for the same reason: D-POCO's Gearhead is the one every recent round has
driven. Swap and say so if D-MOTO cannot create a P2P group.

Take D-POCO's and D-MOTO's Bluetooth MACs at the start and quote both in the results; every run below
needs D-POCO's for the poke broadcast and D-MOTO's for reading the phone's view of it.

---

## 4. Settings

Written to `shared_prefs/settings.xml` with the app stopped, per the template.

| Key | Type | Value | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `3` | Native AA |
| `native-ap-transport` | int | `0` | WiFi Direct, not the hotspot arm |
| `static-bssid` | string | `0` | The unset sentinel, so the round 1 route is what supplies the BSSID |
| `log-level` | int | `1` | DEBUG. The decisive line is a warning, but the exchange itself is DEBUG |
| `native-wifi-version-exchange` | bool | `false` | Default, keeps the TCP endpoint out of this round |
| `insecure-aa-rfcomm-listener` | bool | `false` | Default |
| `native-aa-complete-hfp-slc` | bool | **per run** | `false` in R2, `true` in R3. The one variable |

`native-aa-complete-hfp-slc` is new in this branch and absent from the baseline. Read it back after
writing and quote the value in each run's report; it is the only thing separating R2 from R3.

---

## 5. The lines that decide every run

All verified against `c5609e16` with `grep -F`.

On the head unit, ours:

```
NativeAA: HFP responder active for                              a channel is being served
NativeAA: HFP TX (                                              what we sent, DEBUG
NativeAA: HFP RX (                                              what came back, DEBUG
NativeAA: hands-free service level connection established (     THE line, WARN
NativeAA: HFP connection accepted from                          the phone dialled our record
NativeAA: audio sink decoy accepted                             the phone dialled the decoy
already advertises an audio sink                                the decoy was skipped
already advertises Hands-Free                                   the stand-in record was skipped
NativeAA: Successfully poked                                    a poke connected
NativeAA: Connection accepted from                              the phone opened the AA channel
WirelessServer: Incoming connection detected                    the phone got onto the network
```

On the phone, Gearhead's:

```
WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE          the failure this round targets
GH.WIRELESS.BT: Creating rfcomm socket for device               Gearhead dialling us
```

Capture D-POCO **unfiltered**, as round 1 did. The `ConnLoggerV2` line above is the decisive one and
any tag filter hides it.

---

## 6. Runs

### R0: build gate

Both builds, `assembleGithubDebug` and `testGithubDebugUnitTest`.

- Candidate **1069 tests, 0 failures**. Baseline **1049, 0 failures**, so the delta is **+20**. Both
  measured here, not computed.
- One class is new in the candidate and absent from the baseline: `HfpSlcInitiatorTest` (15). Two
  grow: `BluetoothWakePolicyTest` 15 to 16, `HfpServiceRecordPolicyTest` 5 to 9.
- Record both APK md5s.

A failed build gate is an escalation.

### R1: the premise, on the baseline

**Baseline APK.** The control for the whole round, and the only run that can invalidate it.

Bring the app up in mode 3 with the group formed and the listeners open, confirm mutual bonding, then
fire one manual poke at D-POCO and watch the phone's own profile state across it:

```bash
adb -s <D-MOTO> shell am broadcast \
  -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es extra_mac "<D-POCO MAC>"
# then, on the phone, repeatedly across the next ~30 s:
adb -s <D-POCO> shell dumpsys bluetooth_manager | grep -iB5 -A30 "Headset"
```

Sample it at least three times: just before the poke, ~3 s after `Successfully poked`, and ~25 s
after, which is past the 20 s manual hold.

- **PASS (premise holds):** the phone's hands-free profile for D-MOTO's MAC enters a connecting state
  during the hold and never reaches connected, and Gearhead logs `NO_HFP_FROM_HU_PRESENCE`.
- **INCONCLUSIVE:** no poke connected (`Successfully poked` absent), or the dump carries no per-device
  profile state. Retry up to three times, then report as INCONCLUSIVE and continue to R2 anyway.
- **FAIL (premise refuted):** the profile reaches connected on the baseline. Report it and **stop the
  round**. The fix is aimed at something that is not happening, and R3 would prove nothing.

Quote the profile state verbatim at each of the three samples. This is the run the rest is read
against.

### R2: candidate, setting off

**Candidate APK, `native-aa-complete-hfp-slc=false`.** Two purposes: it is the regression arm for the
two commits that ship on by default, and it isolates what the audio sink decoy does on its own.

Normal two-phone attempt, per §7a's "one poke round, then a normal session" recipe. Run it twice.

- **PASS:** no crash, no new error line, the group forms, and the run is no worse than round 1's
  equivalent. Report `Connection accepted from` and `NO_HFP_FROM_HU_PRESENCE` counts.
- Report separately, as observations rather than verdicts, whether the decoy was published or skipped
  (`audio sink decoy accepted` / `already advertises an audio sink`), and whether
  `NativeAA: HFP connection accepted from` appears at all. **That last count is the decoy's own
  question:** it has been 0 across five previous rounds, so any non-zero value is the finding, and
  zero is not a failure of this run.
- **FAIL:** a session that formed in round 1 no longer forms, or any new error-level line.

### R3: candidate, setting on. The point of the round

**Candidate APK, `native-aa-complete-hfp-slc=true`.** Everything else exactly as R2.

Same manual-poke procedure and the same three profile-state samples as R1, then let the run continue
into a normal session attempt. Run it three times.

- **PASS:** `hands-free service level connection established` appears, **and** the phone's hands-free
  profile for D-MOTO reaches connected during the hold, **and** `NO_HFP_FROM_HU_PRESENCE` stops.
  `Connection accepted from` becoming non-zero, and `WirelessServer: Incoming connection detected`
  after it, is the full result and what the round is for.
- **PARTIAL:** the link is established and the profile reaches connected, but the phone still does not
  open the AA channel. That is a real and useful result: it says the gate was cleared and something
  else is behind it. Capture the phone side in full.
- **INCONCLUSIVE:** no poke connected in any of the three attempts, per §2.
- **FAIL:** the exchange starts and stalls, meaning `HFP TX` appears with no `HFP RX` answering it, or
  `hands-free service level connection established` never appears while pokes are connecting.

**What a PASS would look like if the change did nothing.** A session can form here with no poke ever
attempted, because the phone's own reconnect races ahead (§7a). So "a session formed" on its own is
not a pass for this round. The pass needs the established line and the phone-side profile state
beside it; if a session forms without them, record it as a session that formed **without** exercising
the change, and re-run.

### R4: the positive control

Immediately after a passing R3, on the same session state, flip `native-aa-complete-hfp-slc` back to
`false` and repeat R3's procedure once.

- **The control passes** if the failure comes back: no established line, the profile does not reach
  connected, `NO_HFP_FROM_HU_PRESENCE` returns.
- If the two-phone link keeps working with the setting off, then something other than this change
  fixed it, and R3's pass does not belong to this branch. Say so plainly.

This is a settings change on the candidate and needs no third build.

### R5: real head unit regression

**Candidate APK on D-HU, `native-aa-complete-hfp-slc=false`.** One ordinary Native AA session with
its usual phone.

The reason this run exists: the audio sink record is published on every device, not only a phone
standing in as head unit, and D-HU has a real Bluetooth stack.

- **PASS:** full session as round 1's R4 saw it, `Incoming connection detected`, SSL, video running
  around 50 fps with `dropped=0`.
- Report which branch the two record decisions took on D-HU: `already advertises Hands-Free` and
  `already advertises an audio sink` are both expected there, and if either is absent say so, because
  it means a decoy was published beside a real stack.
- **FAIL:** the session does not form, or calls stop working on that unit.

### R6: harvest

Grep every capture from the round for the lines in §5 and report the counts. In particular the full
`HFP TX` / `HFP RX` sequence from one R3 attempt, verbatim and in order: it is the first time this
exchange has been observed on hardware, and the reply to `AT+BRSF` tells us what the phone's own
feature set is.

---

## 7. Do not re-run

Settled by round 1 and not worth the time:

- Whether the IPv6 route resolves a BSSID on this hardware. It does, on both devices, and the other
  receiver independently sent the identical address.
- Whether the abort at `BSSID is still masked/empty` still fires on the candidate. It does not.
- The poke's hold duration, cadence and target record. The decompile settled that they are not the
  variable, and this branch does not change any of them.
- The AT responder's answering half. Unchanged in this branch and covered by JVM tests.

---

## 8. Report back

Three numbers decide the shipping question:

1. **R1's phone-side profile state.** Does the hands-free profile sit in connecting and time out on
   the baseline? Everything else is read against this.
2. **R3's `hands-free service level connection established` count, beside the phone-side profile
   state and the `Connection accepted from` count.** The three together, never one alone.
3. **R5's session on D-HU.** Formed or not, and which branch the two record decisions took.

Plus R4's one-line answer: did the failure come back when the setting went off.

The settings surface is deliberately not in this round. `native-aa-complete-hfp-slc` has no UI row
yet and is set through `settings.xml`; where it belongs in the settings screen is being decided
separately and does not block this.
