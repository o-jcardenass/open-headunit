# driver-selection-native: round 2 results

Run 2026-09-04 on **D-HU** (MT50) as the head unit, **D-POCO** (POCO X3 NFC) and **D-MOTO**
(motorola edge 30 neo) as driver phones. One APK: `fork/testing/driver-selection-plus-automation`
@ `a333306e6882` (md5 `42cf54518aab99a7508eb5ee98609f8c`), unit gate **1309 / 0** (matches the
brief exactly), `ACTION_QUERY_STATE` echoed the same commit while the app was stopped, no `-dirty`.
Round 1's install blocker recurred from the cold start (release-signed shipped app blocks
`adb install -r`); used the pre-approved remedy (backup `settings.xml`, uninstall, install, restore,
verify byte-identical) before R0.

**Headline: 2 of 4 §8 numbers are the good outcome, 1 is a clear regression, 1 is a new finding not
in scope for round 1's four defects.** R1 (the point of the round) and R8 (regression guard) both
PASS cleanly. **R2 (the round's positive control) FAILS**: the two sub-runs measured the same delay
regardless of the timeout setting, which is exactly the brief's specified failure signature. R5 and
R7 surfaced two new, reproducible findings beyond round 1's original four defects.

Settings restored byte-identical (verified via diff against the pre-round backup) and both phones
re-bonded before closing the round. Evidence: `hur-wifi-test-scripts/round-driver-selection-native-r2/`
(9 raw logcat captures, 3 screenshots, the candidate APK).

---

## R0: build gate — PASS

- `./gradlew assembleGithubDebug` printed `Building from commit: a333306e6882`, no `-dirty`.
- `ACTION_QUERY_STATE` (app stopped, `-f 0x00000020`) replied with `"commit":"a333306e6882"`,
  confirming the stopped-package broadcast delivery works as documented.
- Unit gate: **1309 / 0**, `BUILD SUCCESSFUL`. Exactly the brief's number.
- APK md5 `42cf54518aab99a7508eb5ee98609f8c`.

---

## R1: headless bring-up — **PASS**. The point of the round.

Setup: both phones bonded, both radios off, `native-driver-selection-mode=1`, timeout `10`, both
MAC strings empty, `native-poke-bt-macs` and `auto-start-bt-macs` cleared in both `settings.xml`
and the device-protected mirror (read back after clearing — the mirror held a stale
`DC:B7:2E:5E:4E:59` entry from an earlier round and needed an explicit rewrite, exactly the trap
§3 warns about). `ACTION_START_WIRELESS` sent without ever launching `MainActivity`.

- **Zero** `Multi-driver selection is active` lines (brief wants exactly 0).
- **5 pokes in 120 s**, round-robin across both phones (`motorola edge 30 neo` then
  `POCO X3 NFC`, repeating) — matches `main`'s round-1 count of 5 exactly.
- First poke **2.20 s** after `5GHz createGroup SUCCESS!` (12:33:33.108 → 12:33:35.309), close to
  `main`'s 2.264 s.
- Read-back confirmed: `native-driver-selection-mode=1`, 2 bonded devices
  (`dumpsys bluetooth_manager`), both phone names appear in the poke lines (nothing pre-targeted).
- Context: 3 `createGroup SUCCESS` / 3 distinct `p2p-wlan0-N` occurred over the 120 s window
  (roughly one per ~30 s), consistent with this rig's known ~60 s `recoverNativeGroup` cycling
  documented from earlier rounds — not a regression, not scored by this run.

Log: `r1_candidate.txt`.

---

## R2: the round's positive control — **FAIL**

Two sub-runs, same setup as R1 except `MainActivity` launched normally (dialog appears, untouched).

- **Sub-run (a)**, `native-driver-selection-timeout=10`: `PROMPT_SHOWN` at 12:36:46.844,
  exactly one `went unanswered` at 12:37:48.384 → **61.54 s**. 3 `Multi-driver selection is
  active` lines while up, poke followed ~2 s later.
- **Sub-run (b)**, `native-driver-selection-timeout=30` (read back and confirmed in
  `settings.xml`): `PROMPT_SHOWN` at 12:38:30.559, exactly one `went unanswered` at
  12:39:32.184 → **61.625 s**. Same pattern otherwise.
- **The two delays differ by 0.085 s.** The brief is explicit that this is the FAIL condition:
  *"equal delays mean the deadline is not reading the setting, and a fixed hidden constant is not
  what was written."* Both sub-runs converge on ~61.5-61.6 s regardless of a 10 vs. 30 setting —
  the `native-driver-selection-timeout` value is read back correctly from `settings.xml`
  (confirmed both times) but the deferral deadline itself does not appear to consult it.
- Both sub-runs also ran considerably longer than the brief's own "~25 s / ~45 s" expectation
  (timeout + 15 s grace) — the fixed ~61.5 s figure doesn't match either formula, which is a
  second data point that something other than the documented timeout+grace math is driving this
  deadline.

Logs: `r2a_candidate.txt`, `r2b_candidate.txt`.

---

## R3: dialog dismissed by leaving the app — PASS (mechanism), with a caveat on timing

Setup as R2(a), timeout `10`. Launched, waited for `PROMPT_SHOWN` (12:40:18.213), sent the
`R3-home` marker (12:40:27.299), then `KEYCODE_HOME`.

- `ACTION_NATIVE_AA_PROMPT_DISMISSED received` and `the driver prompt is gone without a choice`
  fired **0.22 s** after the marker — well inside the brief's "about 2 s" bar.
- **Zero** further `Multi-driver selection is active` or `went unanswered` lines appeared after
  the dismiss — the accept gate opened immediately via the dismiss path, not the R2 deadline.
- The dismiss mechanism itself is proven correct. However, the **actual poke did not fire until
  54.47 s** after the marker (12:41:21.767) — much longer than the brief's "well before the 25 s
  deadline" expectation, and in the same ~55-60 s neighborhood as R2's broken deadline. With no
  deferral or waiting line logged in between, the gap looks bound by the poke loop's own retry
  cadence rather than anything blocking on the dismiss path — but this is inference, not a
  measured mechanism, and is reported as an open question rather than a verdict-changing finding.

Log: `r3_candidate.txt`.

---

## R4: cancel stops the poke without deafening the unit — **PASS**

Setup as R2(a), timeout `10`, D-MOTO's MAC armed in `auto-start-bt-macs` (both files).
`PROMPT_SHOWN` at 12:42:17.827; `R4-cancel` marker + `KEYCODE_BACK` at 12:42:25.

- `cancelPoke() called` fired immediately (12:42:25.840, same second as the Back press).
- Left the app running 35 s, then cycled D-MOTO's Bluetooth off/on (12:43:14 → 12:43:17).
- `a phone arrived over Bluetooth — the cancelled prompt no longer stands` fired at 12:43:22.863.
- Phone accepted cleanly: `Connection accepted from motorola edge 30 neo` (12:43:41.548),
  `SSL handshake complete` (12:43:57.749).
- **Zero** `User explicitly canceled driver selection — refusing connection` lines anywhere in
  the capture — no refusal loop at all, let alone round 1's 20-refusals-in-1.4s. The BT cycle
  landed after the deliberate 30 s refusal window had already elapsed, so a clean first-attempt
  accept is the expected outcome.

Log: `r4_candidate.txt`.

---

## R5: Switch Phone keeps the network — **PASS on network preservation, FAIL on reaching D-POCO**

Setup: `native-preferred-device-mac`=D-MOTO, timeout `10`, `kill-on-disconnect=false`, D-MOTO
radios on, D-POCO bonded. Countdown auto-resolved to D-MOTO with no touch (preferred-device
short-circuit), session formed, then `KEYCODE_BACK` → tap **Switch Phone** → pick D-POCO.

**Setup note — an execution detail worth recording for the next round:** the driver-selector
dialog shown after tapping Switch Phone auto-resolves via the ordinary round-robin poke roughly
**2-4 s** after it renders (distinct from R2's ~61 s deadline) — considerably faster than a
screenshot + `uiautomator dump` round-trip allows. The first two attempts landed the D-POCO tap
either before the selector rendered or after the round-robin had already reconnected D-MOTO; only
the third attempt (`r5c_candidate.txt`), using a single chained `adb shell` command with the tap
timed ~3 s after the Switch Phone tap, landed correctly (confirmed by
`HomeFragment: Connecting to Native-AA device: POCO X3 NFC` and `Driver selected: DC:...`
appearing in the log). D-POCO's radios also had to be turned on ahead of the switch step —
the brief's stated setup ("D-POCO bonded with radios off") cannot by itself produce the stated
PASS criterion of "a second SSL handshake complete, to D-POCO," since an unreachable phone cannot
complete a handshake.

Results, from the cleanest run (`r5c_candidate.txt`), all three earlier attempts included for the
interface-index count since none of them re-created a group:

- **Interface count: exactly 1** distinct `p2p-wlan0-N` (`p2p-wlan0-14`) across the whole
  three-attempt capture — round 1 recorded 2. **This is the brief's stated verdict criterion and
  it PASSes.**
- `createGroup SUCCESS` count: **1** — also PASS-consistent (round 1 was 2).
- `AapService: Native AA session ended; keeping the WIFI_DIRECT network up for the phone's return.`
  — present, every attempt.
- `AapService: Native AA user exit. Stopping active launcher.` — **absent**, every attempt.
- **`a second SSL handshake complete, to D-POCO` — did not happen.** On the one attempt where
  D-POCO was genuinely selected in the app's own logic (`selectDriver: DC:B7:2E:5E:4E:59`,
  `Manual poke requested for POCO X3 NFC`, `Attempting manual poke to POCO X3 NFC...`), D-MOTO's
  own reconnect (`Connection accepted from motorola edge 30 neo`, 1.05 s after the manual poke to
  D-POCO started) still won the race and completed a second handshake **to D-MOTO**, not D-POCO.
  This happened on both attempts where a poke actually landed. `NativeAaHandshakeManager.
  rearmForNextSession` explicitly reopens the AA listeners "for the phone's return," which appears
  to let the just-switched-from phone (D-MOTO) reconnect on its own faster than the newly-selected
  target (D-POCO) can respond to its manual poke.

**Verdict: FAIL** on the brief's 4-criterion bar (3 of 4 met; the "connects to the newly-selected
phone" criterion is not met). The original round-1 defect this run targets — **P2P group teardown
on Switch Phone — is clearly fixed** (1 interface, 1 createGroup, network-kept line present,
no user-exit line). The new finding is that Switch Phone's *target selection* is not reliable:
the previous phone can win the reconnection race back before the newly-chosen one connects.

Screenshot: `r5_exitdialog.png` (live D-MOTO session, exit dialog with Switch Phone visible).
Logs: `r5_candidate.txt` (attempt 1), `r5b_candidate.txt` (attempt 2), `r5c_candidate.txt`
(attempt 3, the one that reached D-POCO selection).

---

## R6: Switch Phone with "close app on disconnect" — **PASS**

R5's setup plus `kill-on-disconnect=true`. Session with D-MOTO formed, `KEYCODE_BACK`, tapped
Switch Phone.

- **Zero** `AapService destroying` and **zero** `killProcessOnDestroy is true` lines within 10 s
  of `User requested switch driver` (12:51:35.x) — checked over the whole capture, not just the
  window.
- `adb shell pidof com.andrerinas.headunitrevived` returned a live pid (31497) after the switch.
- Driver selector confirmed on screen. Screenshot: `r6_selector.png`.

Round 1's original defect (process killed 0.643 s into a switch) is fixed.

Log: `r6_candidate.txt`.

---

## R7: `Off` mode's WiFi button — **FAIL**, a new finding, different failure mode than round 1

`native-driver-selection-mode=0`, both MAC strings empty, both phones bonded and radios off.
Launched, settled, sent the `R7-tap` marker, tapped the home screen's WiFi button
(`wifi_button`, found via `uiautomator dump`, no scrolling needed).

- Settings read back and confirmed correct at tap time: `native-driver-selection-mode=0`,
  `native-preferred-device-mac`/`last-connected-native-mac` both empty.
- **No picker of any kind opened.** Instead, within **56 ms** of the marker, the app went
  straight to `HomeFragment: Connecting to Native-AA device: motorola edge 30 neo` →
  `Auto-connect: begin (Native-AA driver: motorola edge 30 neo, mode=OVERLAY)` →
  `Received manual Native-AA poke request for A0:46:5A:97:E4:95` → a manual poke, with no
  intervening UI at all (screenshot `r7_result.png`, taken ~2 s after the tap, shows only
  "Android Auto is starting…").
- This is neither of the two previously-known behaviors: not round 1's candidate result ("a
  toast and nothing else") and not `main`'s baseline ("Select Bluetooth Device" list). It's a
  third, distinct behavior — a silent, un-chosen auto-connect to one specific bonded device with
  no picker and no user input at all, despite two bonded phones and `mode=0`.

**Verdict: FAIL** against the stated PASS bar ("the driver picker opens"). This is a new,
reproducible finding, not previously described in round 1.

Log: `r7_candidate.txt`. Screenshots: `r7_result.png`.

---

## R8: single bonded phone (regression guard) — **PASS**

D-MOTO unpaired via system Bluetooth settings (Settings → gear icon on the row → Forget device;
the plain row tap tries to *connect*, not open device details — confirmed empirically this round).
Only D-POCO bonded. Mode `1`, both MAC strings empty. Clean-run protocol: head unit launched
first (`AapService creating...` at 12:57:06.885), D-POCO's radios restored 18 s later.

- **Zero** `ACTION_NATIVE_AA_PROMPT_SHOWN received`.
- **Zero** `Multi-driver selection is active`.
- **Zero** `went unanswered`.
- Launch to `SSL handshake complete`: **27.79 s** (12:57:06.885 → 12:57:34.675) — faster than
  round 1's 36.3 s baseline, well within the "5 s" tolerance (it's faster, not slower — no
  regression).

D-MOTO re-paired afterward (required an operator unlock — see Setup notes) and both phones
confirmed bonded before continuing to R9.

Log: `r8_candidate.txt`.

---

## R9: a chosen driver's poke is not trampled (regression guard) — **PASS**

Setup as R1. After the group came up, sent `ACTION_NATIVE_AA_POKE --es extra_mac
A0:46:5A:97:E4:95` (D-MOTO), watched 60 s.

- `Attempting manual poke to motorola edge 30 neo...` followed the `R9-poke` marker immediately
  (same timestamp, 13:02:31.736).
- `a chosen driver's wake poke is running` appeared **3 times** during the 60 s watch.
- **Zero** `Attempting active poke to device: POCO X3 NFC` in that window (the one
  `Attempting active poke to device: motorola...` line in the capture is from *before* the
  marker, part of R1-style round-robin cadence prior to the manual poke — not a violation).

The guard holds: the round-robin does not trample a chosen driver's in-flight manual poke.

Log: `r9_candidate.txt`.

---

## Setup notes

- Install blocker recurred as predicted (release-signed shipped app vs. debug build); used the
  pre-approved uninstall/install/restore remedy before R0, verified `settings.xml` byte-identical
  afterward via `diff`.
- The `auto-start-bt-macs` device-protected-mirror trap (§3) is real and bit this round once: the
  mirror held a stale MAC from an earlier round even after the main `settings.xml` set was
  cleared. Cleared explicitly (rooted `adb shell`, direct file rewrite) and re-read every time a
  run needed "no history."
- **R5's timing gotcha** (documented in the R5 section above) cost two discarded attempts. Worth
  carrying forward to the next round: the post-Switch-Phone selector auto-resolves in ~2-4 s via
  the ordinary poke loop, not the ~61 s R2 deadline. A `uiautomator dump` + tap round-trip does
  not fit; a single chained `adb shell "cmd1; sleep N; cmd2"` with the delay tuned to land inside
  that window is what worked.
- **R8's unpair step**: tapping a paired-but-disconnected device's row in stock Android Bluetooth
  settings tries to **connect** it, not open its detail page — the separate `settings_button`
  (gear icon) on the right edge of the row is what opens Forget/detail. Cost several retries
  before finding it (operator correction mid-round).
- **Re-pairing D-MOTO after R8** needed an operator unlock: the phone was asleep/locked, which
  silently prevents the Bluetooth pairing-code confirmation dialog from being interactable on
  that side (the dialog on the head unit side resets back to the scan list with no error). Waking
  the screen alone was not enough; the keyguard itself had to be cleared. Escalated to the user
  per the "stop and ask" rule for anything needing device credentials I don't have; the user
  unlocked it and pairing completed on the very next attempt.
- Both phones' radios and `settings.xml` restored to their pre-round state at the end (verified
  byte-identical via `diff`); both phones confirmed bonded.

---

## §8 report-back, in the brief's own terms

1. **R1**: poke count in 120 s = **5** (round 1: 0), `Multi-driver selection is active` count =
   **0**. Matches the "first non-zero, second exactly 0" bar. **PASS.**
2. **R2**: two measured delays = **61.54 s** (timeout 10) and **61.625 s** (timeout 30). They
   must differ by about 20 s; they differ by **0.085 s**. **FAIL** — this is the brief's own
   stated failure signature for "the deadline is not reading the setting."
3. **R5**: `createGroup SUCCESS` count = **1**, distinct `p2p-wlan0-N` count = **1** (round 1: 2
   and 2; this round needed 1 and 1 — **met**). The network-preservation half of R5 **PASSes**;
   the "second handshake goes to D-POCO" half does not (see R5 section) — **overall FAIL** on the
   brief's 4-criterion bar, but the specific numeric criterion asked for here is met.
4. **R8**: launch to `SSL handshake complete` = **27.79 s**, against round 1's 36.3 s — faster,
   no regression. **PASS.**

Plus the three one-liners: **R3** recovered without waiting out the deadline (dismiss fired the
gate open 0.22 s after the marker; zero deferral/deadline lines followed), though the eventual
poke itself still took 54.47 s, close to R2's broken figure, rather than being fast — reported as
an open question, not folded into R3's verdict. **R4**: yes, the phone was accepted again, lever
was `a phone arrived over Bluetooth`. **R6**: yes, the app stayed alive (pid confirmed) through
the switch.

---

## Ship/no-ship read

Two of the round's three original blockers (dialog-latch accept gate, cancel-permanence) are
cleanly fixed (R3, R4). Two of the five originally-measured defects (Switch-Phone group teardown,
kill-on-disconnect app-kill) are cleanly fixed (R5's network half, R6). The headless wake-poke fix
that is the entire point of this round works (R1), and the two regression guards hold (R8, R9).

Against that: **the round's own positive control fails** (R2 — the unanswered-prompt deadline
does not read `native-driver-selection-timeout`), and **two new, reproducible defects** surfaced
that round 1 did not cover: Switch Phone's target selection can silently reconnect the wrong
phone (R5), and the `Off`-mode WiFi button silently auto-connects to one bonded device with no
picker at all instead of offering a choice (R7). Not PR-ready as-is; R2, R5's target-selection
half, and R7 need fixes and a re-run before this ships.
