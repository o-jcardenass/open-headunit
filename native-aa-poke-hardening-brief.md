# Poke hardening — one branch, three guards

**This supersedes `audio-focus-round11-brief.md`.** That brief pointed at
`fix/744-call-audio-hfp`, which was stacked on the audio-focus work and carried two of the three
changes below. Everything has been rebuilt onto `main` as one branch, and the third change — the
unbonded-device fix, which had been sitting on its own branch — is now in it too. Run this instead;
round 11's R1-R4 are folded in as R2-R5 here.

Read `TESTING-TEMPLATE.md` §7a first. Rounds 9 and 10 added five entries and four of them shape this
round.

---

## 1. Build

**Candidate:** `fix/native-aa-poke-hardening` @ `203d6dc7`, based on `main` @ `e900de78`.

**Not stacked on anything.** Unlike every audio-focus round, this branch does not carry
`fix/audio-focus-pauses-bt-source` — nothing here needs it, and none of the runs below involve
Android Auto audio. Do not expect round 8's focus lines in these captures.

```bash
git fetch fork --prune --prune-tags
git checkout -B fix/native-aa-poke-hardening fork/fix/native-aa-poke-hardening
git log --oneline -5
# expect: 203d6dc7, 57bd5334, c0f8a63c, 2a3c4602, e900de78
```

### R0 — build gate

`run_unit_tests.sh`, then `build_hur.sh`.

- **`BluetoothWakePolicyTest`: 21 tests, all green.** Nine cover the wake-mode setting, six the
  hands-free guard, six the pairing rules.
- Full suite green. Record the APK md5 and confirm it is live (§5).

**If R0 fails, stop and report.** This combination has never been compiled anywhere: two of its three
commits were only ever built as part of a different branch, and the third has never been built at all.
Expect the rebase to be where a problem shows up, if there is one.

---

## 2. What is on the branch

Three changes, all to the Native AA wake poke, listed oldest first.

**`2a3c4602` — don't poke a phone that's no longer paired.** `triggerPoke()` read the stored Auto
Start BT MAC list and connected to it unconditionally. Against a device the user has since unpaired,
`connect()` makes Android start a *new* pairing negotiation as a side effect, which the user sees as
the head unit repeatedly asking to pair. Now any MAC that is not `BOND_BONDED` is skipped and pruned
from the list, and the pruned list is synced to device-protected storage so `AutoStartReceiver`'s
copy stays consistent.

**`c0f8a63c` — a setting for which record the poke may touch.** `bluetooth-wake-mode`: `0` (default)
is today's HFP-AG then HSP-AG; `1` is HSP-AG only, with no fallback, which is what v.3.1.1 did.

**`57bd5334` — don't poke over a live hands-free link.** Your round 10 R2 showed a poke that
*connects* to HFP-AG takes the phone's one hands-free slot and the head unit's own
`HfpClientConnectionService` is dropped 4 ms later, permanently. The poke now stands down when a
hands-free link exists — which is also when it is pointless, a live hands-free link being the ACL
connection the poke exists to create. The check is in `pokeDevice()`, so both the retry loop and the
manual poke go through it.

**`203d6dc7` — the pairing check moved to `pokeDevice()`, and the prune made conservative.** See
below.

### The fourth commit, and what it changed about this round

`203d6dc7` fixes two problems that only appeared once the branches were combined. Both were found by
reading the combined code, and both are now decided by pure functions with tests — so R6 and R7 below
**verify a fix** rather than probe for a defect, and both should pass.

- **The pairing check was in `triggerPoke()`'s device selection; the hands-free guard is in
  `pokeDevice()`.** Both poke entry points reach `pokeDevice()`, only one reaches the selection
  block — so a manual poke skipped the pairing check and could still solicit pairing at an unpaired
  device, the exact thing that check exists to stop. The pairing check moved down next to the guard.
- **The prune deleted anything that was not a positive `BOND_BONDED`** — including the `catch` path
  and a `getBondState()` taken while the adapter was off, which AOSP answers `BOND_NONE` rather than
  admitting it does not know. `triggerPoke()` never checked `isEnabled`, this rig's Bluetooth is
  documented switching itself off and back on (§7a), and the deletion goes straight through to
  device-protected storage. One poke round in that window would have taken away the user's Auto
  Start device silently. Poking and forgetting are now separate rules: strict about poking (only a
  confirmed pairing), lenient about forgetting (only a positive not-paired answer from a working
  adapter, or an address that was never a Bluetooth address).

R7 is still worth running on hardware even though the rule is unit-tested, because the unit test
proves the *decision* and the run proves the **wiring** — that the adapter-off case really does reach
the policy as `UNREADABLE` rather than arriving pre-flattened to `BOND_NONE` somewhere earlier.

---

## 3. What is different about this round

**Everything is short.** No twenty-minute runs, no "no session can ever form" precondition — that
lever does not work on this phone (§7a) and nothing here needs it.

**Poke connectivity varies between sessions on this rig** — 0/13 in round 9, 13/13 in round 10 (§7a).
Several runs below need a poke to actually connect. Each says so, and a run that needed one and saw
only failures is **INCONCLUSIVE**, not a pass. Retry once after a head-unit Bluetooth adapter cycle;
if pokes still will not connect, report that and stop — the round cannot be finished today and that
is a rig fact.

**Re-establish the hands-free link before every run.** Runs that damage it leave it down, and §7a
records recovery is not always one adapter cycle. Before each run:

```bash
adb shell dumpsys bluetooth_manager | grep -iE "HeadsetClient|curState"
```

must read `Connected`. If it does not, recover it first — a run started with the link already down
measures nothing.

**Back up the Auto Start setting before R7, and check it after every run.** R7 deliberately probes a
path that can delete it, and if the defect is real, earlier runs may have deleted it already.

```bash
adb shell run-as $PKG cat shared_prefs/settings.xml | grep -i "auto-start-bt"
```

**Log level: DEBUG (`log-level=1`).** The poke failure line and the repeated guard line are both
`AppLog.d`.

---

## 4. Settings keys

| Key | Element | Meaning |
|---|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | Native AA. Required throughout. |
| `bluetooth-wake-mode` | absent, or `value="1"` | absent = HFP-AG then HSP-AG; **1 = HSP-AG only** |
| `log-level` | `<int name="log-level" value="1" />` | DEBUG |

Plus the Auto Start BT device list, which R6 and R7 manipulate — note its exact value before you
start. `set_hu_prefs.sh` for anything setting more than one key.

---

## 5. The lines that decide the runs

Verified against `57bd5334`. All prefixed `OPENHU`.

```
NativeAA: Not poking <name> (<addr>) — this head unit already holds a Bluetooth hands-free link, which a poke would take over and leave disconnected. That link is itself the connection a poke exists to create.
NativeAA: Dropping Auto Start BT MAC(s) no longer paired: [<macs>]
NativeAA: Calling socket.connect() for <name> via <HFP-AG|HSP-AG> (<uuid>)...
NativeAA: Successfully poked <name> via <HFP-AG|HSP-AG>. Holding <n>ms...
NativeAA: Poke via <HFP-AG|HSP-AG> to <name> (<addr>) failed: <reason>     <- DEBUG
```

The damage, from the OS rather than from us — the chain your round 10 R2 quoted:

```
D/BluetoothHeadsetClientServiceJni: connection_state_cb: state 0 ...
D/HfpClientConnService: Disconnecting from <addr>
E/HeadsetClientStateMachine: Bad currentState: ...
```

---

## 6. Runs

### R1 — the settings screen still works

The wake-mode control is new UI on a branch that has been rebased across a different base. Open
Settings → the Android Auto connection-mode block and confirm **"Wake-up connection"** appears with
two options, and that picking each one and saving writes `bluetooth-wake-mode` as expected. One
screenshot.

- **PASS:** control present, both options selectable, the value lands in `settings.xml`.
- **FAIL:** missing, crashes, or the value does not persist.

### R2 — the guard fires when the link is up *(the point of the branch)*

Link `Connected`, `bluetooth-wake-mode` absent. Launch in Native AA, watch two minutes.

- **PASS:** the `Not poking …` line appears, **no** `Calling socket.connect()` line appears at all,
  and the link still reads `Connected` at the end.
- **FAIL:** any poke is attempted, or the link drops.

Quote the guard line with its timestamp and the profile state before and after.

### R3 — Android Auto still connects with the poke suppressed

The guard's own risk. Continue from R2 with the link up.

- **PASS:** a full session forms — `Incoming connection detected`, `SSL handshake complete`,
  projection — with no poke attempted. Record how long, to compare with round 9's 3-6 s and round
  10's ~35 s.
- **FAIL:** no session inside 90 s. That is the most important negative available and would mean the
  guard is too aggressive. Report it prominently.

### R4 — is HSP-AG destructive too? *(still open in the code)*

The guard suppresses the poke when a link is up, so this needs the link **down** at launch, which is
the one state where the poke still runs.

- Bring `HeadsetClientService` to `Disconnected` (phone Bluetooth off, launch, phone Bluetooth on —
  the round 9 recipe in §7a).
- `bluetooth-wake-mode=1`, so only HSP-AG is ever touched.
- Watch until a `Successfully poked … via HSP-AG` appears, or pokes are seen failing.

- **Poke succeeded →** sample `HeadsetClientService` immediately and again 30 s later.
  - Link comes up and stays up → HSP-AG is safe, and the setting is a real alternative to the guard.
  - Link drops or never establishes → both records are destructive and the guard is the only fix.
    Equally valuable; say so plainly.
- **Poke only ever failed →** INCONCLUSIVE per §3.

### R5 — the manual poke still runs when there is nothing to protect

Link **down** (as R4 leaves it), `bluetooth-wake-mode` absent. Trigger a manual poke from the app's
own UI.

- **PASS:** a `Calling socket.connect()` line appears. The guard has not disabled the user's escape
  hatch.
- **FAIL:** suppressed even with the link down.

### R6 — the manual poke refuses an unpaired device

The gap `203d6dc7` closed. Set the Auto Start BT device to a MAC that is **not** currently bonded —
simplest is to note the phone's MAC, unpair the phone from the head unit, and leave the stored MAC in
place. Then trigger a manual poke at it from the UI.

- **PASS:** the log says
  `Not poking … it is not currently paired with this head unit`, **no** `Calling socket.connect()`
  line appears, and **no pairing request appears on either device**.
- **FAIL:** a pairing request appears, or a connect is attempted at the unpaired device.

Watch both screens for the pairing dialog — it is the user-visible symptom and it will not be in the
log.

Re-pair afterwards and restore the Auto Start setting.

### R7 — a Bluetooth-off poke round leaves the Auto Start device alone

The second thing `203d6dc7` fixed, and the one worth doing on hardware even though the rule is
unit-tested: the test proves the decision, this run proves the wiring — that an adapter-off read
really does reach the policy as "cannot tell" rather than arriving already flattened to "not paired".

**Back up `settings.xml` first**, and note the Auto Start MAC list exactly.

- Set a valid, **bonded** Auto Start BT device. Confirm it is in `settings.xml`.
- Get the poke loop running (Native AA, hands-free link down so the guard does not suppress it).
- With the loop running, turn the **head unit's own** Bluetooth off and leave it off across at least
  one poke round (§7a: it self-reverts in ~14 s, so watch for the window rather than assuming it).
- Read the MAC list back.

- **PASS:** the list is unchanged, and **no** `Dropping Auto Start BT MAC(s)` line appears for a
  device that is still paired.
- **FAIL:** the line appears and the list is now empty. Quote it with the before/after values — it
  would mean the adapter-off case is reaching the policy as a positive not-paired answer, which is
  the one thing the fix is supposed to prevent.

If the adapter self-reverts too fast to catch a poke round, say so and mark INCONCLUSIVE — do not
force it by other means. Restore the setting afterwards either way.

---

## 7. Do not re-run

- Round 10's R2. The damage is demonstrated; this round is about the fix.
- Round 8's audio-focus work — not on this branch at all.
- The stub `0000111e` record. Three rounds have looked for `HFP connection accepted` across windows
  from 60 s to 68 minutes and never seen it. Note it if it appears; do not spend a run on it.

---

## 8. Report back

`native-aa-poke-hardening-results.md` on this branch, in §7's format. Six answers:

1. **R2** — does the guard fire, and does the hands-free link survive?
2. **R3** — does Android Auto still connect with the poke suppressed, and how fast?
3. **R4** — is HSP-AG destructive too? This is the only run whose answer is genuinely unknown, and
   either way it settles a design question that is open in the code right now.
4. **R5 and R6** — is the manual poke still useful when there is nothing to protect, and does it now
   refuse an unpaired device without a pairing dialog appearing?
5. **R7** — does the Auto Start device survive a poke round taken with Bluetooth off?
6. Whether pokes connected at all this session — several runs depend on it, and §7a says it varies.

R6 and R7 verify fixes rather than hunt for defects, so a plain PASS on both is the expected and
useful outcome. R4 is the one to spend extra care on.
