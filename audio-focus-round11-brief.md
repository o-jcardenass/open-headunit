# Round 11 brief — does the guard hold, and is HSP-AG any safer?

Read `TESTING-TEMPLATE.md` §7a first — round 10 added three entries to it and two of them change how
this round is set up.

Your round 10 R2 is the finding this whole investigation was for, and the quoted 4 ms chain is what
made it a demonstration rather than a correlation. This round checks the fix that came out of it, and
answers the one question R2 left open.

---

## 1. Build

**Candidate:** `fix/744-call-audio-hfp` @ `da3da45c` — **one new commit** on top of round 10's
`d64d7802`. A rebuild is needed this time.

```bash
git fetch fork --prune --prune-tags
git checkout -B fix/744-call-audio-hfp fork/fix/744-call-audio-hfp
git log --oneline -5
# expect: da3da45c, d64d7802, 26032e65, a2381b46, d2dff1df
```

### R0 — build gate

`run_unit_tests.sh`, then `build_hur.sh`.

- **`BluetoothWakePolicyTest` must now report 15 tests, all green** (9 from round 9 plus 6 for the
  guard). If it still says 9, the wrong commit is checked out.
- `PlaybackFocusPolicyTest` still 20/20 — the standing canary.
- Full suite green. Record the new APK md5 and confirm it is live (§5).

**If R0 fails, stop and report.** This commit has never been compiled anywhere.

---

## 2. What changed, and why

Round 10 R2 showed that a poke which **connects** to the phone's HFP-AG record takes the phone's one
hands-free slot, and the head unit's own `HfpClientConnectionService` is dropped 4 ms later and does
not come back without a Bluetooth adapter cycle. Round 9 saw no damage only because every poke there
failed to connect — a poke that fails cannot take a slot. That difference, not "one round versus
dozens", is the variable.

**The fix does not try to pick a safer record.** It stands the poke down when there is a hands-free
link to destroy — which is also exactly when the poke is pointless. The poke exists to raise an ACL
connection so the phone notices a wireless-capable head unit; a live hands-free link *is* an ACL
connection. Units where the poke is load-bearing are the ones whose Bluetooth profiles do not
auto-connect, and those have no hands-free link to read, so the guard never fires for them.

`BluetoothHelper.handsFreeLinkState()` reads `HEADSET_CLIENT` and `HEADSET` via
`getProfileConnectionState`. Three answers, and `BluetoothWakePolicy.shouldPoke` maps them:

| Reading | Poke? |
|---|---|
| a hands-free link is connected | **no** — skip, and say so |
| no hands-free link | yes, as before |
| the adapter will not say | yes, as before |

The check sits inside `pokeDevice()`, so the retry loop and the manual poke are both covered.

### The question still open

Whether an **HSP-AG** poke does the same damage. AOSP's `HeadsetClientService` covers both the
hands-free and headset roles, so it may well. Round 10's R3 was meant to answer it and came back
UNTESTABLE because "phone Wi-Fi off" could not hold a session off for twenty minutes.

**That premise was wrong and is dropped.** Your R2 showed the link dies on the *first* poke round, so
the control needs one successful poke and one profile read — about a minute — not a session-proof
window. A session forming 35 s later does not matter, because the measurement is already over.

---

## 3. What is different about this round

**No twenty-minute runs, and no "no session can ever form" requirement anywhere.** Every run below is
over within a couple of minutes of the app launching.

**The whole round hinges on pokes actually connecting**, and §7a now records that connectivity varies
between sessions on this rig — round 9 got 0/13, round 10 got 13/13. So each run states what it needs
and what to do when it does not get it:

- A run that needs a **successful** poke and sees only failures is **INCONCLUSIVE**, not a pass. Say
  which it was. Retry once or twice — a fresh Bluetooth adapter cycle on the head unit sometimes
  changes it — and if pokes still will not connect, stop and report that; the round cannot be
  completed today and that is a rig fact, not a branch defect.

**Re-establish the hands-free link between every run.** Each run that damages it leaves it down, and
§7a records the recovery is not always a single adapter cycle. Before each run below, confirm:

```bash
adb shell dumpsys bluetooth_manager | grep -iE "HeadsetClient|curState"
```

must read `Connected`. If it does not, recover it (head-unit adapter cycle, a second cycle, then the
phone's adapter) before starting — a run begun with the link already down measures nothing.

**Log level: DEBUG (`log-level=1`).** The poke failure line and the repeat guard line are both
`AppLog.d`.

---

## 4. Settings keys

| Key | Element | Meaning |
|---|---|---|
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | Native AA. Required throughout. |
| `bluetooth-wake-mode` | absent, or `value="1"` | absent = HFP-AG then HSP-AG; **1 = HSP-AG only** |
| `log-level` | `<int name="log-level" value="1" />` | DEBUG |

`set_hu_prefs.sh` for anything setting more than one key.

---

## 5. The lines that decide the runs

Verified against `da3da45c`. All prefixed `OPENHU`.

**The guard — new this round:**

```
NativeAA: Not poking <name> (<addr>) — this head unit already holds a Bluetooth hands-free link, which a poke would take over and leave disconnected. That link is itself the connection a poke exists to create.
```

Info the first time in a run of skips, debug on repeats.

**The poke:**

```
NativeAA: Calling socket.connect() for <name> via <HFP-AG|HSP-AG> (<uuid>)...
NativeAA: Successfully poked <name> via <HFP-AG|HSP-AG>. Holding <n>ms...
NativeAA: Poke via <HFP-AG|HSP-AG> to <name> (<addr>) failed: <reason>     <- DEBUG
```

**The damage, from the OS rather than from us** — the chain your R2 quoted:

```
D/BluetoothHeadsetClientServiceJni: connection_state_cb: state 0 ...
D/HfpClientConnService: Disconnecting from <addr>
E/HeadsetClientStateMachine: Bad currentState: ...
```

**The measurement:**

```bash
adb shell dumpsys bluetooth_manager | grep -iE "HeadsetClient|curState"
```

---

## 6. Runs

### R1 — the guard fires when the link is up *(the point of the round)*

Confirm `HeadsetClientService` is `Connected`. `bluetooth-wake-mode` absent (default). Launch the app
in Native AA and watch for two minutes.

- **PASS:** the `Not poking …` line appears; **no** `Calling socket.connect()` line appears at all;
  `HeadsetClientService` still reads `Connected` at the end of the window.
- **FAIL:** any poke attempt is made, or the link drops.

This is the run the fix exists for. Quote the guard line with its timestamp, and the profile state
before and after.

### R2 — Android Auto still connects with the guard in place

The guard's risk is that it stands the poke down on a unit that needs it. This rig connects via the
phone's own reconnect rather than the poke (§7a), so it cannot prove the poke is dispensable
everywhere — but it can show the guard does not itself break connecting.

Continue from R1 or start fresh, with the link up and the guard therefore active.

- **PASS:** a full session forms — `Incoming connection detected`, `SSL handshake complete`,
  projection running — with no poke having been attempted. Record how long it took, to compare with
  round 9's 3-6 s and round 10's ~35 s.
- **FAIL:** no session inside 90 s. That would be the most important negative result available and
  would mean the guard is too aggressive; report it prominently rather than as a failed run.

### R3 — is HSP-AG any safer? *(the question round 10 left open)*

The guard would suppress the poke entirely here, so this run needs the hands-free link **down** at
launch, which is the one state where the poke still runs.

- Bring `HeadsetClientService` to `Disconnected` — the phone's Bluetooth off, launch, then phone
  Bluetooth on is the round 9 recipe (§7a) and gives a window where the link is re-establishing.
- Set `bluetooth-wake-mode=1`, so only HSP-AG is ever touched.
- Watch until either a `Successfully poked … via HSP-AG` line appears, or pokes are seen failing.

- **If the poke succeeded:** sample `HeadsetClientService` immediately, then again 30 s later.
  - **Link comes up and stays up →** HSP-AG is safe, and the `bluetooth-wake-mode=1` escape is a real
    alternative to the guard for anyone the guard misreads.
  - **Link drops or never establishes →** HSP-AG is *not* safe, both records are destructive, and the
    guard is the only fix. Equally valuable; say so plainly.
- **If the poke only ever failed:** INCONCLUSIVE per §3. Retry once, then report.

### R4 — the guard does not stop the manual poke being useful when it should run

With the link **down** (as R3 left it) and `bluetooth-wake-mode` absent, trigger a manual poke from
the app's own UI.

- **PASS:** the poke runs — a `Calling socket.connect()` line appears — because there is no
  hands-free link to protect. Confirms the guard has not disabled the user's own escape hatch.
- **FAIL:** the guard suppresses it even with the link down.

---

## 7. Do not re-run

- Round 10's R2 itself. The damage is demonstrated; this round is about the fix.
- `bluetooth-wake-mode`'s own mechanics — rounds 9 and 10 settled that it suppresses HFP-AG and that
  the default is unchanged.
- Round 8's audio-focus work.
- The stub `0000111e` record. Three rounds have now looked for `HFP connection accepted` across
  windows from 60 s to 68 minutes and never seen it. Note it if it ever appears, but do not spend a
  run on it.

---

## 8. Report back

`audio-focus-round11-results.md`, in §7's format. Four answers:

1. **R1** — does the guard fire, and does the link survive?
2. **R2** — does Android Auto still connect with the poke suppressed, and how fast?
3. **R3** — is HSP-AG destructive too? Either answer settles a design question that is currently open
   in the code.
4. **R4** — is the manual poke still available when there is nothing to protect?

And, as always, whether pokes connected at all this session — every run above depends on it, and
§7a says it varies.
