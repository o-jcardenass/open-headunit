# driver-selection-native — round 1 brief

Rig: **D-HU** (MT50) as the head unit, **D-POCO** and **D-MOTO** both bonded to it as driver phones.
Both phones must stay bonded for R1 to R6; R7 unpairs one on purpose and re-pairs afterwards.

This round is about a feature that is driven from a dialog, so read **§3 of `TESTING-TEMPLATE.md`**
and then **§3 of this brief**, which says exactly where the "script it, don't drive it" rule is
suspended and why.

---

## 1. Build and baseline

| | Branch | SHA |
|---|---|---|
| **Candidate** | `origin/driver-change-native-mode` | `d103ce7a` |
| **Baseline** | `origin/main` | `ce2897c4` |

```bash
git fetch origin
git log --oneline ce2897c4..origin/driver-change-native-mode   # expect exactly three commits
git checkout d103ce7a                                          # detached, for the candidate build
git checkout ce2897c4                                          # detached, for the baseline build
```

The baseline is the candidate's own merge base, so the A/B isolates exactly those three commits. No
history was rewritten. The middle commit is a translation pass and changes no behaviour.

**Identity check** — the candidate carries one class the baseline cannot have:

```bash
unzip -p <apk> 'classes*.dex' | strings | grep -c -F NativeDriverSelectionPolicy
# candidate: non-zero    baseline: 0
```

Unit gate, both measured off-rig: candidate **1276 / 0**, baseline **1259 / 0**. The 17 are the new
policy's own tests and nothing else, so any other number on the rig means the wrong tree was built.

`:app:compileGithubDebugKotlin` succeeds on the candidate off-rig, so a build failure on the rig is a
toolchain problem, not the branch.

---

## 2. What this is and why it exists

The candidate adds driver selection to Native AA wireless: a dialog on the home screen that lists
bonded phones, an auto-connect countdown, a preferred-device setting, and a "Switch Driver" entry in
the projection exit dialog. It also adds a per-connection accept gate in the Bluetooth accept loop
and a new early return in the wake-poke loop.

Three things in it were read as regressions on paper and this round exists to settle them on
hardware. All three are about what the unit does when **nobody taps anything**.

**The wake poke now defers on any unit with two bonded phones.** `triggerPoke()` returns early
whenever the selection policy says a dialog would be appropriate and no driver has been chosen yet.
That predicate is computed from stored settings plus the bond list, with no dialog involved, and the
default mode (`Auto`) answers yes at two bonded phones unless exactly one of them is currently
connected over Bluetooth. On paper this means a unit that starts with nobody watching never wakes
the phone at all, where today it pokes every 30 s.

**The prompt flag latches.** It is set when the dialog is shown and cleared only when a driver is
picked or Cancel is pressed. Backgrounding the app dismisses the dialog through a path that clears
neither, and the accept gate then refuses every incoming Bluetooth connection with only a log line.

**Cancel is permanent.** The cancel flag is cleared only by picking a driver. Nothing in `start()`,
`stop()` or the session-end re-arm resets it, and the Bluetooth auto-start path does not know about
it, so on paper the phone arriving over Bluetooth can no longer bring the unit back.

The rest of the round covers the `Off` mode's home-screen button, whether "Switch Driver" keeps the
Wi-Fi Direct group (the branch's own description says it does), and one regression guard for the
majority case of a single bonded phone.

---

## 3. What is different about this round

- **House rule 2 is suspended for three runs, and only those.** R4, R5 and R6 need a real touch,
  because the thing under test is a dialog and this branch adds no automation action for it. R1, R2,
  R3, R7 and R8 need no touch at all and must be run with none. Where a touch is needed, prefer
  keyevents over coordinates:

  ```bash
  adb shell input keyevent KEYCODE_DPAD_DOWN     # move down the dialog's list
  adb shell input keyevent KEYCODE_DPAD_CENTER   # pick the focused row
  adb shell input keyevent KEYCODE_BACK          # cancels the dialog (this is R3's cancel)
  adb shell input keyevent KEYCODE_HOME          # backgrounds the app (this is R2's dismiss)
  ```

  A coordinate tap is the fallback; if you use one, put the coordinates in Setup notes so the next
  round can repeat it.

- **The dialog can be made to auto-connect with no touch at all**, and R4 uses that. With
  `native-preferred-device-mac` set, the countdown resolves a target and connects when it expires.
  With every history key empty, no target resolves, the countdown is hidden and the dialog sits open
  indefinitely, which is what R1, R2 and R3 need. The keys in §4 are what switches between those two
  states.

- **`auto-start-bt-macs` is read from a second file, not from `settings.xml`.** The Bluetooth
  auto-start receiver reads the device-protected mirror at
  `/data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml`, which is written only when the
  Auto-start screen is saved. Writing the key into `shared_prefs/settings.xml` does **not** arm it.
  R3 depends on auto-start firing, so verify it before the run:

  ```bash
  PKG=com.andrerinas.headunitrevived
  adb shell run-as $PKG cat /data/user_de/0/$PKG/shared_prefs/settings_device_protected.xml
  ```

  If D-MOTO's MAC is not in there, cycle the phone's Bluetooth once and check for
  `MATCH! Starting AapService`. If that line does not appear, set it once through the Auto-start
  screen (a sanctioned exception, noted in Setup notes) or record R3 as UNTESTABLE.

- **The same key is also read as connect history by the candidate**, so it changes what the dialog
  does. That is a finding this round should confirm, not a rig artifact: with a MAC in
  `auto-start-bt-macs` and `last-connected-native-mac` empty, the countdown still resolves a target.

- **§4's clean-run protocol applies as written** for every run except R4 and R5, where the second
  phone must be present and awake for the switch. Say so in Setup notes.

- **Read back every key you write** (§1). Several of this round's verdicts are "the app did nothing",
  which is also what a settings write that never landed looks like.

- **R7 changes rig state.** It unpairs D-MOTO from D-HU. Run it last and re-pair before closing the
  round.

---

## 4. Settings keys

Written with the app stopped, per §1. All four new keys live in `shared_prefs/settings.xml`.

| Key | Element | Meaning |
|---|---|---|
| `native-driver-selection-mode` | `<int name="native-driver-selection-mode" value="1" />` | 0 = Off, 1 = Auto (the default), 2 = Always |
| `native-driver-selection-timeout` | `<int name="native-driver-selection-timeout" value="30" />` | countdown seconds, clamped to 3..30 |
| `native-preferred-device-mac` | `<string name="native-preferred-device-mac"></string>` | empty = no preferred phone |
| `last-connected-native-mac` | `<string name="last-connected-native-mac"></string>` | written by the app after a handshake |
| `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` | Native AA |
| `native-poke-all-paired` | `<boolean name="native-poke-all-paired" value="true" />` | leave at the default |
| `native-poke-bt-macs` | `<set name="native-poke-bt-macs"></set>` | empty for this round |
| `kill-on-disconnect` | `<boolean name="kill-on-disconnect" value="false" />` | R5 sets this true |
| `log-level` | `<int name="log-level" value="0" />` | context only |

**Every line this round's verdicts turn on is INFO**, so a level-2 capture decides all of them.
Level 0 is asked for so the poke and group-info lines are there when a run goes an unexpected way.

The two string keys are **cleared by writing an empty element, not by deleting the line**: the app
reads them with a `""` default either way, but an absent key and an empty one look different in a
read-back and the whole point is to be able to prove what was set.

---

## 5. The lines that decide the runs

All verified with `grep -F` against `d103ce7a`.

**Candidate only, and each one is a verdict:**

```
NativeAA: Multi-driver selection is active and awaiting user choice — deferring automated multi-device poke loop.
NativeAA: Selection prompt active (target=<mac-or-null>) — refusing connection from <mac>
NativeAA: User explicitly canceled driver selection — refusing connection from <mac>
NativeAA: Driver selection was explicitly canceled by user — skipping automated poke.
NativeAA: cancelPoke() called — user explicitly canceled driver selection.
NativeAA: Cancelling background multi-device poke loop because selection prompt is active.
NativeAA: Driver selected: <mac>
AapService: ACTION_NATIVE_AA_PROMPT_SHOWN received
AapService: ACTION_NATIVE_AA_CANCEL_POKE received — user explicitly canceled driver selection
AapService: ACTION_NATIVE_AA_SWITCH_DEVICE received (targetMac=<mac>)
AapProjectionActivity: User requested switch driver
AapTransport stopping and sending byebye (DEVICE_SWITCH)
HomeFragment: Connecting to Native-AA device: <name> (<mac>)
HomeFragment: Unambiguous driver (<name>) - auto-connecting directly without prompt
```

**On both builds:**

```
NativeAA: Attempting active poke to device
NativeAA: Successfully poked
NativeAA: ACTIVELY LISTENING on Android Auto UUID
NativeAA: Connection accepted from
WifiDirectManager: Standard createGroup SUCCESS!
WirelessServer: Incoming connection detected
Handshake: SSL handshake complete
MATCH! Starting AapService
```

Counting commands:

```bash
grep -c "Attempting active poke to device"                rN.txt   # did the unit ever try to wake a phone
grep -c "deferring automated multi-device poke loop"      rN.txt   # the new early return firing
grep -c "refusing connection from"                        rN.txt   # the new accept gate firing
grep -c "createGroup SUCCESS"                             rN.txt   # group churn across a switch
grep -c "Incoming connection detected"                    rN.txt   # the phone actually got on the network
grep -o "p2p-wlan0-[0-9]*"                                rN.txt | sort -u
```

---

## 6. Runs

### R0 — build gate

Both APKs built, md5s recorded and different, the DEX symbol check above passes, unit gate 1276 / 0 on the
candidate and 1259 / 0 on the baseline. `adb install -r` only.

### R1 — two bonded phones, nobody touches the screen — **this is the point of the round**

Candidate and baseline. Both phones bonded to D-HU, **both in airplane mode** so neither is connected
over Bluetooth. Settings: mode `1`, timeout `30`, both MAC strings empty,
`native-poke-bt-macs` empty. Start the capture, launch the app, and then touch nothing for 120 s.

- **PASS (candidate):** `Attempting active poke to device` appears, and its first occurrence is no
  later than the baseline's in the same setup.
- **FAIL:** `deferring automated multi-device poke loop` appears at all, or the poke count is 0 while
  the baseline's is not.

Report, for both builds: the poke count, the seconds from `createGroup SUCCESS` to the first poke,
and whether the dialog was on screen (a screenshot is fine here, it is not a verdict).

**What a PASS would look like if the change did nothing:** a poke on both builds is the honest pass.
But a poke on the candidate *plus* a `Driver selected:` line means something answered the dialog, and
the run is void. Confirm no `Driver selected:` and no `ACTION_NATIVE_AA_POKE` in the candidate
capture before recording a pass.

### R2 — the dialog is dismissed by backgrounding the app

Candidate only. Same settings as R1. Launch, wait for the dialog, then
`input keyevent KEYCODE_HOME`. Take D-POCO out of airplane mode and start Android Auto on it, so the
phone dials the head unit itself. 120 s.

- **PASS:** the session reaches `Handshake: SSL handshake complete`.
- **FAIL:** `Selection prompt active (target=null) — refusing connection from` appears, and no
  session forms.

Then, without restarting the app, bring the app back to the foreground and repeat the phone-side
attempt. Report whether it recovers, because that decides whether this is a nuisance or a dead unit.

### R3 — the dialog is cancelled with Back

Candidate only. Settings as R1, except `auto-start-bt-macs` must contain D-MOTO's MAC in the
device-protected file (see §3). Launch, wait for the dialog, `input keyevent KEYCODE_BACK` within
10 s. Then cycle D-MOTO's Bluetooth off and on, which is the rig's only re-arm for Native AA (§3 of
the template).

- **PASS:** `MATCH! Starting AapService` appears and a session follows.
- **FAIL:** `User explicitly canceled driver selection — refusing connection from` appears, or no
  poke follows the auto-start.

Then press the home screen's WiFi button once and report whether that recovers the unit, and which
of `Driver selected:` / `createGroup SUCCESS` shows up when it does. That answers "is there any way
back short of a restart".

### R4 — happy path, then Switch Driver

Candidate only. `native-preferred-device-mac` = D-MOTO's MAC, timeout `10`, D-MOTO out of airplane
mode. Launch and let the countdown expire on its own, no touch.

- **PASS (first half):** `Connecting to Native-AA device` then a session with D-MOTO, with **no**
  touch anywhere in the capture.

Then, in the projection, open the exit dialog and choose Switch Driver, and pick D-POCO in the
selector that follows. This half needs touches; note them.

- **PASS (second half):** `User requested switch driver`, `stopping and sending byebye
  (DEVICE_SWITCH)`, the selector appears, and a session forms with D-POCO.

Report: `createGroup SUCCESS` count across the whole capture, the `p2p-wlan0-N` values seen, and the
seconds from `User requested switch driver` to the second `SSL handshake complete`. The branch's
description says the group and credentials survive a switch; two `createGroup SUCCESS` lines say they
do not, and that number is the finding either way.

### R5 — Switch Driver with "Close app on disconnect" on

Candidate only. R4's setup plus `kill-on-disconnect=true`. Get a session, then Switch Driver.

- **PASS:** the driver selector appears.
- **FAIL:** the app closes, or the capture ends at `AapService destroying` with no selector.

### R6 — mode `Off`, two bonded phones, the WiFi button — **regression guard, do not skip**

Candidate and baseline. `native-driver-selection-mode=0`, both MAC strings empty, both phones bonded
and in airplane mode. Launch, then press the home screen's WiFi button once.

- **PASS:** `Attempting active poke to device` follows the press within 10 s, on both builds.
- **FAIL (candidate):** nothing follows the press but a toast.

**What a PASS would look like if the change did nothing:** the baseline opens a device list here
rather than poking, so the two builds are not expected to look the same. What must not happen is the
candidate doing *nothing at all*. Say which of the two the candidate did, and quote the last three
lines after the press.

### R7 — one bonded phone only — **regression guard, do not skip. Run this last.**

Candidate and baseline. Unpair D-MOTO from D-HU so exactly one phone is bonded. Mode `1`, MAC strings
empty. Ordinary cold connect per §4.

- **PASS:** no dialog, no `deferring` line, and the time from launch to `SSL handshake complete` is
  within a couple of seconds of the baseline's.

This is the majority configuration in the field. If it regresses, nothing else in the round matters.
Re-pair D-MOTO afterwards and say so in Setup notes.

### R8 — is a bonded phone ever seen as connected?

Candidate only. Both phones bonded. Take **D-POCO only** out of airplane mode and let its Bluetooth
connect to D-HU before launching the app. Mode `1`, MAC strings empty.

- **PASS:** no dialog, and `Unambiguous driver (<name>) - auto-connecting directly without prompt`
  appears.
- **FAIL:** the dialog appears anyway.

This decides whether the `Auto` mode ever differs from `Always` on this hardware. The app reads the
per-device connected state through a hidden method; if that read returns nothing here, every user
with two bonded phones sees the dialog on every start. Also grep the capture for
`Accessing hidden method` and quote any line naming `isConnected`.

---

## 7. Do not re-run

- Anything about the Wi-Fi Direct group identity, the 5288 rebind or the Bluetooth lifecycle. Those
  are the already-merged commits underneath this branch and both arms carry them.
- The translation commit in the middle of the branch. It was checked off-rig: all 21 locale files
  carry the same 983 strings, no duplicates, and the countdown format arguments match in every
  locale.

---

## 8. Report back

`driver-selection-native-round1-results.md`, in §7's format. The numbers that decide whether this
ships:

1. **R1, both builds:** poke count in 120 s, and whether the deferring line fired on the candidate.
   This alone can block the branch.
2. **R2 and R3:** whether the unit ever accepted a phone again without a touch, and which lever
   brought it back if one did.
3. **R4:** `createGroup SUCCESS` count across the switch, and the switch duration in seconds.
4. **R7:** launch to `SSL handshake complete` on both builds, as two numbers.
