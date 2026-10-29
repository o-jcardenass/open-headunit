# Poke hardening — results

**Candidate:** `fix/native-aa-poke-hardening` @ `203d6dc7`, based on `main` @ `e900de78`
**APK md5:** `bc4abb6642df5753791f8063d1e0920d`
**Unit:** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, phone POCO X3 NFC
**Date:** 2026-08-09

## Setup notes

- Run directly in the coordinating session rather than delegated to a background agent, per a
  standing instruction adopted after round 10 (a delegated round burned ~200k tokens re-deriving
  context and its agent died silently mid-round, leaving the rig unsupervised for ~68 minutes).
- **R2's literal recipe ("launch and watch two minutes") does not exercise the guard on this rig,
  and this is worth recording as its own finding.** The automatic retry loop's very first
  `while`-check landed on `handshake=true` before ever reaching `pokeDevice()` — the phone's own
  Android Auto reconnect (over Bluetooth RFCOMM) rides the same fast link that keeps HFP alive, so
  by the time the poke loop would try, a handshake is already in flight. A full session formed
  ~6.6 s after launch with zero pokes ever attempted. This technically satisfies R2's literal PASS
  wording (no `Calling socket.connect()`, link stays `Connected`) but for the wrong reason — the
  guard was never actually asked to do anything.

  **Used instead: the app's manual-poke entry point, scripted.** `HomeFragment`'s device picker
  sends an explicit `Intent` to `AapService` (`action = com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE`,
  extra `extra_mac`), which calls `NativeAaHandshakeManager.manualPoke()` → `pokeDevice()` directly,
  bypassing `NativeHandoffPolicy` entirely. This is fully scriptable:
  ```bash
  adb shell am start-foreground-service -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService \
    -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es extra_mac "<phone MAC>"
  ```
  Used this for R2, R5, and R6, since all three are specifically about `pokeDevice()`'s own guards,
  not about winning a race against the automatic retry loop. Noting it here as a new scriptable
  action worth adding to `hur-wifi-test-scripts/` if a future round needs it again.

- R1 needed the UI (no scriptable trigger for a Settings screen render/save round-trip, per house
  rules). Minimum taps: Search → "Wake-up" → tap the result → toggle each radio option → Save.
  Found and worth noting: **saving via the UI always writes `bluetooth-wake-mode` explicitly** (`0`
  or `1`), never leaves it absent — functionally identical to the default, but means "absent" as a
  distinct state can only be produced by deleting the key directly (`set_hu_prefs.sh del`), not by
  any UI action, including Reset.
- R6 needed the UI to unpair the phone (`Settings → Bluetooth → device → Forget`) — no adb-scriptable
  unpair command exists (`cmd bluetooth_manager` has no unbond action). The user did the physical
  re-pairing confirmation tap afterward, since Android's pairing dialog needs a tap on both devices
  and there's no adb equivalent.
- R6 also produced an unplanned but useful confirmation: moments before the scripted manual-poke
  test, the **automatic** retry loop (still running from R5's session) independently noticed the
  same freshly-unpaired MAC and pruned it on its own (`Dropping Auto Start BT MAC(s) no longer
  paired: [DC:B7:2E:5E:4E:59]`) — `2a3c4602`'s fix working exactly as designed, caught incidentally
  rather than by a dedicated run. `manualPoke()` takes its target MAC from the intent extra, not the
  stored list, so this did not affect R6's own test.
- R7 could not be landed after four distinct attempts; see its own section. Auto Start MAC list and
  settings.xml backed up before R7 and restored after (see below).
- Auto Start BT device (`DC:B7:2E:5E:4E:59`, POCO X3 NFC) and `bluetooth-wake-mode` (absent) restored
  to their pre-round values at the end. Full `settings.xml` backups taken before starting and again
  before R7, both at `hur-wifi-test-scripts/round11-settings-backup.xml` and
  `round11-r7-settings-backup.xml`.
- Scripts used: `run_unit_tests.sh`, `build_hur.sh`, `install_and_launch.sh` (`SKIP_BUILD=1`),
  `set_hu_prefs.sh`. No new scripts added to the directory (the manual-poke `am` command above is
  short enough to inline; flagged in case a future round wants it as a script).

## R0 — build gate

**PASS.** First compile of this exact four-commit combination anywhere (it was rebased fresh onto
`main`). `run_unit_tests.sh`: full suite green, `BluetoothWakePolicyTest` 21/21 (9 wake-mode + 6
hands-free guard + 6 pairing, matching the brief's breakdown exactly). `build_hur.sh`: built clean.
Installed, md5 `bc4abb6642df5753791f8063d1e0920d`.

## R1 — the settings screen still works

**PASS.** Found via Settings → search "Wake-up" → "Wake-up connection" result, under Wireless
Connection. Both options present and selectable: "Hands-free first" (default, checked) and "Headset
only". Selecting "Headset only" and tapping Save wrote `bluetooth-wake-mode=1`; switching back to
"Hands-free first" and saving wrote `bluetooth-wake-mode=0`. Control renders correctly and persists
in both directions on this rebased branch.

## R2 — the guard fires when the link is up *(the point of the branch)*

**PASS**, via the manual-poke path (see Setup notes for why the literal automatic-loop recipe
doesn't reach this code on this rig). With `HeadsetClientService` at `curState=Connected` and
`bluetooth-wake-mode` absent:

```
16:21:01.242 NativeAA: Manual poke requested for POCO X3 NFC (DC:B7:2E:5E:4E:59)
16:21:01.260 NativeAaHandshakeManager.noteHandsFreePokeSkip | NativeAA: Not poking POCO X3 NFC
             (DC:B7:2E:5E:4E:59) — this head unit already holds a Bluetooth hands-free link, which
             a poke would take over and leave disconnected. That link is itself the connection a
             poke exists to create.
16:21:01.267 NativeAA: Manual poke to POCO X3 NFC finished.
```

Zero `Calling socket.connect()` lines. `HeadsetClientService` read `Connected` before and after.
18 ms from request to the guard's decision.

## R3 — Android Auto still connects with the poke suppressed

**PASS.** From the same launch used to discover R2's recipe gap: app launched 16:16:10.885,
`WirelessServer: Incoming connection detected` at 16:16:17.460 — **6.575 s**, in the same range as
round 9's 3-6 s. Zero poke attempts across the entire 2-minute observation window that followed
(`grep -c "Calling socket.connect()"` = 0). The guard being present costs nothing when the phone
reconnects on its own, which is the normal case on this rig.

## R4 — is HSP-AG destructive too? — **the answer, with one caveat on how firmly it's shown**

**Yes, on the evidence available, but the causal chain is weaker than round 10's HFP-AG finding and
deserves that qualification stated plainly rather than rounded up.**

Recipe: `bluetooth-wake-mode=1`, phone Bluetooth off → launch → wait 8 s → phone Bluetooth on (round
9's recipe, link starts `Disconnected` so the new guard doesn't suppress the poke).

- First poke round (HSP-AG) failed to connect (~26 s timeout: `read failed, socket might closed or
  timeout, read ret: -1`), 16:22:20.133 → 16:22:46.224.
- Second round **succeeded**: `Successfully poked POCO X3 NFC via HSP-AG` at 16:23:02.073.
- A full Android Auto session formed 3.85 s later (16:23:05.924, `Incoming connection detected`).
- `HeadsetClientService` sampled at +30 s, +73 s, and again at ~+3 min after the successful poke:
  **`Disconnected` every time**, with the AA session healthy and actively resumed
  (`AapProjectionActivity`) throughout.
- Recovered with a single head-unit-side Bluetooth adapter cycle immediately after — not a bond
  loss, same recovery pattern as round 10's HFP-AG damage.

**Why this isn't as clean as round 10's finding:** round 10's HFP-AG poke tore down a link that was
*already Connected*, with a 4 ms OS-level log chain proving direct causation. Here the link started
`Disconnected` by the recipe's own design (phone Bluetooth had just been cycled), so what's shown is
that it **never came up** over the following 3 minutes, not that the poke *tore anything down*. The
alternative explanation — "the AA session itself, not the poke, is what's keeping HFP from
connecting" — is not the likely one: **R2 and R3, in this same round, showed an active AA session
and a `Connected` HFP link coexisting for over 5 minutes with no interaction.** But R2/R3's HFP link
was already established before their session formed, while R4's had to establish fresh, so the two
aren't perfectly comparable, and "AA session came up 3.85 s after the poke and has held the radio's
attention ever since" cannot be fully ruled out as a contributing factor independent of the poke
itself.

**Conclusion offered at the confidence the evidence supports:** HSP-AG-only does not look like a
safe alternative to the guard on this rig — the classic hands-free link failed to establish across
a healthy AA session for 3 minutes following a successful HSP-AG poke, matching HFP-AG's practical
outcome from round 10. Both records look destructive here. The guard, not the setting, is carrying
the fix.

## R5 — the manual poke still runs when there is nothing to protect

**PASS.** Link down, `bluetooth-wake-mode` absent, manual-poke intent fired at 16:27:34.067:

```
16:27:36.798 NativeAA: Calling socket.connect() for POCO X3 NFC via HFP-AG (0000111f-...)...
```

2.7 s from request to connect attempt. (The attempt itself later failed on timeout at 16:27:52.193 —
irrelevant to this run's PASS condition, which only asks whether a poke is attempted at all.) The
escape hatch is intact.

## R6 — the manual poke refuses an unpaired device

**PASS.** Phone unpaired via Settings → Bluetooth → device → Forget (UI, no scriptable path exists).
Auto Start MAC left in place (`DC:B7:2E:5E:4E:59`) — coincidentally pruned by the *automatic* loop
moments before this run for the same underlying reason (see Setup notes), but `manualPoke()` takes
its address from the intent, not the stored list, so the test still ran as designed:

```
16:29:29.605 NativeAA: Manual poke requested for POCO X3 NFC (DC:B7:2E:5E:4E:59)
16:29:29.616 NativeAaHandshakeManager.pokeDevice | NativeAA: Not poking POCO X3 NFC
             (DC:B7:2E:5E:4E:59) — it is not currently paired with this head unit, and connecting
             to an unpaired device would ask the user to pair rather than wake anything.
16:29:29.620 NativeAA: Manual poke to POCO X3 NFC finished.
```

11 ms from request to refusal. Zero `Calling socket.connect()` lines for this device (the automatic
loop's fallback did poke a *different*, still-bonded device — Magnetic Speaker — in the background,
unrelated to this test). `uiautomator dump` immediately after showed no "pair" text anywhere on
screen, and the screenshot confirms no dialog. Re-paired afterward with the user's help (pairing
confirmation needs a tap on both screens).

## R7 — a Bluetooth-off poke round leaves the Auto Start device alone

**INCONCLUSIVE.** Four distinct attempts, none landed the specific race the run needs (the automatic
retry loop's device-selection code — `bondReadingFor()` — executing while the head unit's own
Bluetooth adapter is genuinely disabled):

1. Toggle HU Bluetooth off *after* a successful poke round: the loop settled into
   `NativeHandoffPolicy`'s post-poke "settling" state and never looped again during the observation
   window — never reached the device-selection code at all.
2. Toggle HU Bluetooth off *before* launch, repeated every 3 s for ~18 s: `NativeAaHandshakeManager.start()`
   itself requires the adapter enabled at init (`Bluetooth adapter not available or disabled`, logged
   E-level) and silently no-ops if it isn't — the whole manager never came up, so there was nothing
   to test.
3. Same as (2) with tighter 1.5 s toggling: same `start()` failure.
4. Enable Bluetooth, wait 2 s, launch, *then* toggle off every 1.2 s for ~19 s: `start()` **still**
   failed the same way, meaning the `svc bluetooth enable` shell command's completion does not
   reliably mean `BluetoothAdapter.isEnabled` is already `true` by the time `start()` checks it a
   couple of seconds later — a propagation delay narrower than what these attempts allowed for.

The Auto Start MAC list was never pruned across any of the four attempts, but this is weak evidence
at best (absence of damage when the target code path was never confirmed to run, versus proof it was
exercised and behaved correctly).

**What isn't in doubt:** direct reading of `bondReadingFor(device)` in `NativeAaHandshakeManager.kt`
shows it checks `adapter.isEnabled` as a plain boolean *before* ever calling `device.bondState` —
structurally, there is no code path by which a disabled adapter could be misread as a positive
`NOT_BONDED` answer; the two are checked in strict order and the first short-circuits the second.
Combined with the six passing `BluetoothWakePolicyTest` cases covering `BondReading.UNREADABLE`'s
handling in the pure policy, the *decision* is proven and the *static* wiring reads correctly. What
remains unconfirmed is only the live runtime path this run was built to exercise. Per the brief's own
guidance ("If the adapter self-reverts too fast to catch a poke round, say so and mark
INCONCLUSIVE — do not force it by other means"), that's where this stops rather than being forced
further with more exotic toggling.

## Anything the brief did not ask about

- **R2's literal recipe doesn't work on this rig** (see Setup notes) — worth folding into §7a so a
  future round doesn't spend time re-discovering it: the automatic retry loop essentially never gets
  a live iteration in in normal operation on this hardware, because the phone's own AA reconnect
  (over the same Bluetooth link HFP uses) is consistently faster than the loop's ~2 s startup delay.
  Anything that needs to inspect the automatic loop's live behavior should expect this and plan
  around it, e.g. via the manual-poke intent where the code path allows it.
- **The stub `0000111e` hands-free record**: `HFP connection accepted` did not appear in any capture
  this round either (five rounds now, zero accepts).
- **Poke connectivity kept varying within this single round**, not just between rounds: R4's HSP-AG
  failed once then succeeded; R5's manual HFP-AG poke connected but then timed out on read. Consistent
  with §7a's existing note — still not a fixed property of either record on this rig.
