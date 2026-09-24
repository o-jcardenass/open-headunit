# bring-up-status-pill-and-poke-readiness: round 4 brief

**Candidate:** `fork/feat/bring-up-status-pill-and-poke-readiness` @ `7f439d4e`
**Baseline:** none needed. See §1.
**Gate:** 1628 tests / 0 failures
**Read `TESTING-TEMPLATE.md` first.** This brief only says what is different about this round.

---

## 0. What this is and why it exists

Three commits past round 3's `fe8b91e2`, each answering one round 2 or round 3 finding, and a
rewording of two Part A conditions the rounds showed to be the brief's mistake rather than the
build's.

**P1 and P3 are decided, not fixed.** Round 3 graded `fe8b91e2`'s retreat (`WAKING_PHONE` back to
`WAITING_FOR_PHONE` after a poke pass the phone did not answer) as a P1 FAIL because the rank
decreased, and as a P3 FAIL because `ARMED` never came back. Both readings were correct against the
wording, and the wording is what changes. The poke loop never gives up, so a return to `ARMED` would
tell the user the listeners are closed when they are open; and the two labels already read "Waiting
for your phone" and "Waiting for your phone to connect". The retreat stays. P1 now allows it and P3
requires it.

**WB1 is fixed.** The WiFi button's connect with a reachable phone took the full-screen overlay,
which suppresses the pill and its `status pill step:` lines, and the overlay's own text never moves.
`6de32024`: `HomeFragment.connectToNativeDevice` uses `PILL_THEN_OVERLAY` on every path, so the pill
with its step line shows through the wake and the overlay takes over once the phone answers
(`Auto-connect: a phone is answering, taking the full screen.`). The "Connecting to <phone>" toast
that round 3's screenshots showed under the pill is gone; the pill's first line carries the name.
`fb9ac939`: the manual poke the button fires now reports `WAKING_PHONE` before each round and
retreats after one the phone did not answer, as the automatic loop does.

**FX Plus is classified.** Round 2 found the intercom (class `0x001F00`, Uncategorized, advertising
the Audio Gateway record) counted as a phone and auto-selected as the driver in every Part B run.
`7f439d4e`: a gateway record on an Uncategorized class is `UNKNOWN`, reason
`GATEWAY_BUT_CLASS_UNCATEGORIZED`. The offered tier is phones, else unknowns, so FX Plus is hidden
while D-MOTO is paired, still offered when nothing else is, and a pin lifts it.

**D3 is re-measured, not changed.** Round 3's `ownCloseMs=14803` came from the toggle landing at
SSL+8 s instead of +3 s. The constant stays at 15000; this round scripts the toggle.

## 1. Build and baseline

```bash
git fetch fork
git checkout feat/bring-up-status-pill-and-poke-readiness   # 7f439d4e, 10 commits on main f5c2e986
```

No history was rewritten since round 3; `fe8b91e2` is the seventh commit and still resolves.

**No baseline APK.** Every run is graded on lines `main` does not have. WB1 has its own control
inside the candidate: round 3's `wb1-dhu.txt` is the pre-fix capture of the same gesture on the same
rig, and its zero `status pill step:` lines are what WB1 is compared against.

Build with `hur-wifi-test-scripts/build_hur.sh` per §5. Identity: `ACTION_QUERY_STATE` returns
`commit`; it must read `7f439d4e`. Fallback DEX grep: `GATEWAY_BUT_CLASS_UNCATEGORIZED`, which no
earlier build carries.

The **same APK** is installed on D-HU (Part A, D2, D3) and on D-POCO (W2, D1).

## 2. What is different about this round

**A role swap gets `headunit://exit` first, never a bare force-stop.** Rounds 2 and 3 both lost a
run to the same thing: D-POCO ended a head-unit-role run (D1) with `am force-stop`, which skips
`WifiDirectManager.stop()`, and stayed Group Owner of its own 192.168.49.1 network. As the phone in
the next run it then completed the WPP handshake every ~3 s (`WifiConnectStatus status=SUCCESS(0)`
repeating) and never opened 5288 on D-HU, because it was already on a network of that address: its
own. Round 2 read that as an IPv6-only bind on D-HU, which its own socket tables do not support.
Before any device changes role, launch its app once and send `headunit://exit`, then confirm
`dumpsys wifip2p` on it shows no `GroupCreatedState` and `p2p0` carries no `192.168.49.1`. Record
that read in the setup notes for every swap. A run that shows the 3 s WPP loop is void, not a FAIL,
and the setup note says which device still held a group.

**The WiFi button is a graded run.** WB1 is added to Part A with two passes, one with the phone
reachable and one with its Bluetooth off, because the two produce different first lines on the pill.
The tap is `adb shell input tap 894 334` (the `wifi_button` centre from rounds 2 and 3's `uiautomator`
dumps; re-dump if the layout moved). The driver selector must not appear: with one paired phone and
`native-preferred-device-mac` deleted the button resolves the driver itself, which is what rounds 2
and 3 saw.

**The D3 toggle is scripted.** Tail the capture for `SSL handshake complete`, sleep 3, then
`cmd connectivity airplane-mode enable` on D-POCO, in one shell line, so the timing is the script's
and not the session's. Report the measured offset from the SSL line to the toggle command's own
timestamp.

**Expected INCONCLUSIVE:** P6 (no USB host on D-HU).

## 3. Settings keys this round needs

As round 1 §3, with these differences. All written with the app stopped, read back before launch.
Delete `native-preferred-device-mac` on both head units for the whole round.

| Key | Type | D-HU | D-POCO | Why |
|---|---|---|---|---|
| `wifi-connection-mode` | int | `3` | `3` | Native AA |
| `log-level` | int | `2` | `2` | INFO |
| `auto-start-bt-macs` | StringSet | empty | empty | no auto-start trigger |
| `native-poke-bt-macs` | StringSet | leave | W2: `11:46:03:10:33:59` | the seeded car-kit MAC, as round 1 W2 |
| `native-poke-all-paired` | boolean | leave | `true` | the fallback under test in W2 |
| `auto-disconnect-bt-macs` | StringSet | D2/D3: `DC:B7:2E:5E:4E:59` | D1: `A0:46:5A:97:E4:95` | the watched device |
| `auto-disconnect-bt-delay-seconds` | int | `5` | `5` | the user-visible delay |

## 4. The lines that decide every run

Round 1 §4's table still holds. New or newly load-bearing this round, verified with `grep -F`
against `7f439d4e`:

| Line | Means |
|---|---|
| `HomeFragment: Connecting to Native-AA device: ` | the WiFi button resolved a driver; carries `btConnected=true` or `false` |
| `Auto-connect: begin (Native-AA driver: ` | the connect UI was requested; must carry `mode=PILL_THEN_OVERLAY` |
| `Auto-connect: a phone is answering, taking the full screen.` | the overlay took over from the pill |
| `NativeAA: Attempting manual poke to ` | a manual wake round is about to call `pokeDevice()` |
| `MainActivity: status pill step: ` | as before; `hidden` is expected once the overlay owns the screen |
| `BluetoothHelper: driver candidates: ` | the roll-up; FX Plus must now count under `unknown` |
| `gateway but class uncategorized` | FX Plus's reason in the roll-up's `hidden:` list |

## 5. Runs

### R0: build and unit-test gate

**PASS** requires: 1628 tests, 0 failures; `ACTION_QUERY_STATE` proves `7f439d4e` on both units;
both installed md5s match the one built.

---

### Part A: the status pill, on D-HU with D-POCO as the phone

**P1: Native AA cold bring-up to projection.** As round 1 P1 with one change to the rank rule:
reading the values in order and ignoring repeats, the rank never decreases except (a) immediately
after a `startNativeAaQuietHost() requested` line and (b) from `WAKING_PHONE` to `WAITING_FOR_PHONE`,
at any time. Any other decrease is a FAIL. Report the full ordered list with timestamps and count the
(b) decreases; round 3 run 1 had one, run 2 had none, and either is PASS.

**P2: the pill does not cover the home controls.** As round 1. The pill measured 352x105 px in round
3; report the rectangle again.

**P3: an unanswered wake keeps the pill honest.** As round 1 P3, phone down for 120 s. PASS requires:
`status pill step:` 3 or more times; `WAITING_FOR_PHONE` reached; **at least two** `WAKING_PHONE` to
`WAITING_FOR_PHONE` transitions after the first `WAKING_PHONE` (round 3 measured a ~46 s cadence, so
120 s carries two); `hidden` zero times. `ARMED` is not required to return and its absence is not a
FAIL. Quote the transition timestamps.

**P4: a different mode produces a different sequence.** As round 1.

**P5: the pill renders three lines.** As round 1 P5, with the third line (`WiFi Direct on ...`)
present. Describe all three.

**P6: USB.** Expected INCONCLUSIVE.

**WB1: the WiFi button shows the pill.** From a fresh launch with no session (if one formed, send
`headunit://disconnect` and wait for `ACTIVELY LISTENING`), D-POCO reachable (both radios on, bonded,
recently seen), tap the WiFi button. Screenshot within 3 s of the tap. 60 s.

**PASS** requires all of:

1. `HomeFragment: Connecting to Native-AA device: POCO X3 NFC` with `btConnected=true`, then
   `Auto-connect: begin (Native-AA driver: POCO X3 NFC, mode=PILL_THEN_OVERLAY)`.
2. Between that line and `CONNECTING`, `status pill step:` appears with `WAKING_PHONE` or
   `PHONE_ANSWERED` at least once. Round 3's `wb1-dhu.txt` has zero such lines in the same window;
   that is the comparison.
3. `Auto-connect: a phone is answering, taking the full screen.` appears, and `status pill step:
   hidden` follows it. `hidden` **before** that line is a FAIL.
4. `SSL handshake complete`.
5. The screenshot shows the pill with `Starting Android Auto with POCO X3 NFC` as its first line and
   **no toast** anywhere on the screen. If a toast is visible, quote its text; that is the round's
   most valuable finding.

**WB1b: the same with the phone away.** D-POCO Bluetooth off (verify with `dumpsys`), tap the WiFi
button, screenshot within 3 s, then D-POCO Bluetooth on after 20 s. 90 s.

**PASS** requires: `btConnected=false` on the `Connecting to Native-AA device` line; `mode=PILL_THEN_OVERLAY`;
`NativeAA: Attempting manual poke to POCO X3 NFC` followed by `status pill step: WAKING_PHONE`; a
`status pill step: WAITING_FOR_PHONE` after the first manual round ends unanswered (`Manual poke to
POCO X3 NFC finished.`) **if** the phone was still off at that point, otherwise `PHONE_ANSWERED`;
the screenshot's first pill line reads `POCO X3 NFC is disconnected, waking it...`; no toast. Whether
the session forms after the radio returns is corroboration, not a condition: the manual wake has two
rounds and the phone may need a third from the automatic loop.

---

### Part B: only W2, on D-POCO with D-MOTO as the phone

Setup as round 1 Part B (app on D-HU force-stopped, link precondition read and recorded, D-MOTO
Bluetooth off at launch, on 8 s after `createGroup SUCCESS`). FX Plus (`D0:D9:4F:C2:C7:1E`) must be
bonded to D-POCO and powered, as it was in round 2; say so in the setup notes.

**W2: the seeded car-kit MAC is not poked, the phone is, and the intercom is not a candidate.**
Round 1 W2's five conditions unchanged, plus:

6. The `driver candidates:` roll-up counts FX Plus under `unknown`, never under `phone`, and its
   `hidden:` list names it with `gateway but class uncategorized`. Quote the line.
7. No `Attempting active poke to device: FX Plus`, no `Manual poke requested for FX Plus`, and no
   `Unambiguous driver (FX Plus` anywhere in the run.

If FX Plus is absent from the roll-up entirely, report its class and UUIDs from D-POCO's
`dumpsys bluetooth_manager`; the classifier was given something other than `0x1F00`.

---

### Part C: D1, D2, D3 as round 1, with the D3 toggle scripted

**D1** as round 1. **D2** as round 3 ran it, with `cmd connectivity airplane-mode enable` as the
toggle from the start (`svc bluetooth disable` self-reverts on D-POCO). **D3** as round 1 with the
toggle scripted per §2: PASS is `went away`, then `not ending the session for ... ownCloseMs=<n>`
with `n` below 15000, and the session surviving to the `link_lost` that airplane mode brings. Report
the SSL-to-toggle offset and the `ownCloseMs`; the point of the run is the number at +3 s, which no
round has measured yet.

D1 must be ended with `headunit://exit` on D-POCO before D2 starts (§2).

## 6. Do not re-run

W1, W1b, W2b and W3 passed twice on `ef66abbf` and nothing after it touches the wake decision or the
manual poke's guard. The pill's rank and retreat rules are JVM-tested; the classifier's new rule has
three cases under R0. Do not construct a rewind or a class by hand.

## 7. Report back

Beyond §7 of the template:

1. **WB1's screenshot and its `status pill step:` lines between `begin` and `CONNECTING`**, against
   round 3's zero. Everything else is secondary.
2. **P1's ordered list** with the count of `WAKING_PHONE` to `WAITING_FOR_PHONE` decreases, and
   **P3's transition timestamps**.
3. **W2's `driver candidates:` line**, verbatim, with FX Plus's tier.
4. **D3's SSL-to-toggle offset and `ownCloseMs`.**
5. For every role swap, the `dumpsys wifip2p` read that shows the device held no group.
