# aa-numeric-keyboard, round 2b: can anything put a numeric keypad on the head unit screen?

**Candidate:** none. **No head unit build changes.** D-HU keeps whatever is installed, in whatever wireless mode works.
**Probe:** `evidence/aa-numeric-keyboard-round2b/aa-keyboard-probe-r2b.apk` md5 `e40ec5ac2a9f9c156846f74a44dbbafe`, source `…-r2b-src.zip` md5 `e6be6d9178941bba2e41b7a40102d4f5`. Installs on **D-POCO**, not D-HU.

Read `TESTING-TEMPLATE.md` first, and §7a before planning. This brief carries only what is specific to this round.

> **This is round 2 re-run on a fixed probe. The runs are unchanged.** Round 2 could not answer
> anything because the probe crashed in its own `onGetTemplate()` before any template reached the
> host. Both crashes were mine, both are fixed, and there is now a test that fails if either comes
> back. Nothing about the questions changed.

---

## 1. Why this round exists

Round 1 measured all four keyboard constants and got one identical QWERTY. A full decompile of
`17.5.663204` since then explains it, and the explanation is what makes this round worth running.

The value is **not** dropped by the host. `SignInTemplate` has one live renderer, `kby`, which maps
the constant correctly and calls `setInputType()` on a real `EditText`; the `EditorInfo` reaching the
keyboard genuinely carries `TYPE_CLASS_NUMBER`. The keyboard is Gearhead's own input service, bound
privately over `com.google.android.gms.car.BIND_CAR_INPUT`, and **which** keyboard appears is chosen
by the head unit's input configuration:

- **touch** head units get `xdm`, whose layout is selected purely by locale. Its only use of
  `inputType` anywhere in the stack is `getCursorCapsMode`, for auto-capitalisation.
- **rotary** head units get `xdv`, which *does* branch: `(editorInfo.inputType & 15) == 3` selects a
  dialpad handler. That is `TYPE_CLASS_PHONE`, so `KEYBOARD_PHONE` would hit it and `KEYBOARD_NUMBER`
  would not.

So two things are left to try, and R1 is the one that matters.

---

## 2. What changed since round 2

Round 2's probe threw on every screen. Two separate constraint violations, both introduced by the
round-2 "Back to list" change, both now fixed:

- `SignInTemplate.Builder.addAction` requires a `ParkedOnlyOnClickListener`. The head unit reports
  parked, so the button still fires.
- `TelephoneKeypadTemplate`'s primary action is constrained to **0 custom titles** and
  **requires an icon**, so the titled "Save" action is now an icon-only action with a check mark.

Both constraints were read out of `ActionsConstraints` in the 1.8.0-beta01 sources rather than
guessed.

**The probe now has a pre-flight that catches this class of defect before the rig.** These builders
validate at runtime, so a violation compiles cleanly and only fails on the head unit. Two things
close that gap:

- A Robolectric test builds all five templates for real. It passes on this build, and it was checked
  against the negative case: reintroducing both round-2 defects fails both tests.
- **Opening the probe on the phone now prints a pre-flight list**, one line per screen, `OK` or
  `FAIL` with the exception. See §3 — it takes one tap and it is the first step of the round.

Everything else is as round 2: descriptor fix in place, rows A to D unchanged as the control,
`androidx.car.app:app:1.8.0-beta01`, app appears as **"AA KB Probe"**.

---

## 3. Setup

On **D-POCO**. This APK is signed with the same key as the round-2 one, so `-r` is enough; no
uninstall needed:

```bash
adb -s <D-POCO> install -r aa-keyboard-probe-r2b.apk
adb -s <D-POCO> shell pm list packages | grep aakbprobe
```

**Pre-flight, before touching the car.** Open **AA KB Probe** on the phone itself and read the list
at the bottom of the screen:

```
Pre-flight (every template this probe can show):

OK    A KEYBOARD_NUMBER
OK    B KEYBOARD_DEFAULT
OK    C KEYBOARD_PHONE
OK    D KEYBOARD_EMAIL
OK    E TelephoneKeypadTemplate
```

**If any line says `FAIL`, stop and report it with the exception text.** That screen is the same
template construction the head unit will do, so a `FAIL` here is a guaranteed crash there and the
round cannot run. Five `OK` lines means every screen at least builds.

Then: Android Auto developer mode on (tap the version in About ten times), **Developer settings ->
Unknown sources** enabled, bring up a normal projected session, open the phone's app launcher on the
head unit, start **AA KB Probe**.

Confirm enumeration before doing anything else:

```bash
adb -s <D-POCO> logcat -d | grep -E "CAR.VALIDATOR|Raw list of car apps found" | grep -i aakbprobe
```

**If a screen turns out to be a dead end** (the "Back to list" button is not drawn, or is behind the
keyboard), this resets the probe to its list without disturbing the AAP session:

```bash
adb -s <D-POCO> shell am force-stop com.example.aakbprobe
```

Then start it again from the head unit launcher. Round 1 found header actions and `ActionStrip` are
not drawn on this host and the projection swallows hardware BACK, so this is the reliable way out.

---

## 4. Runs

**Run R1 first.** It is the only one that can produce a new finding; R2 and R3 are confirmation.

### R1 — `TelephoneKeypadTemplate` from a POI app (the run that matters)

Row **E**. This template draws its **own** keypad as part of the template, so it never goes through
the car keyboard. It is public API (`androidx.car.app.dialer`, added 1.8.0, `@ExperimentalCarApi`) and
AA 17.5 registers a presenter for it and lists it alongside `ListTemplate` and `GridTemplate`.

The open question is whether the host renders it for a **POI** app or refuses it because the app is
not a `CALLING` app.

1. Tap row E.
2. `adb -s <D-HU> shell screencap -p /sdcard/kb-E.png` and pull it. Name it `kb-E.png`.
3. If a keypad renders: tap several digits and confirm they appear in the number field, then tap the
   check-mark action and record whether the toast shows the digits. Also tap `*` and `#` and note
   whether they are accepted, since there is **no decimal key** and a fuel amount needs one. `*`
   carries the label `dec?` for exactly this question.
4. If nothing renders, or the screen is blank or errors, capture the log:

```bash
adb -s <D-POCO> logcat -d > r1-keypad-template.txt
```

### R2 — control, rows A and D

Re-run row **A** (`KEYBOARD_NUMBER`) and row **D** (`KEYBOARD_EMAIL`) on this build exactly as in
round 1. Screencap both as `kb-A-r2b.png` and `kb-D-r2b.png`. They must still look identical to each
other and to round 1. If they now differ, the build is suspect and R1 cannot be trusted either.

### R3 — the rotary branch, cheap check

**This one is predicted to fail, and that is fine.** The decode says the rotary keyboard is selected
only when the head unit reports `hasRotaryController` **and no touchscreen**. Open Headunit announces
a touchscreen unconditionally, and `enable-rotary` only adds a *touchpad*, which is a different flag.
So the touch keyboard should stay. Run it because it is one key and one session. Skip it without
penalty if the session is expensive to rebuild.

1. App stopped, set `enable-rotary` to `true` in `shared_prefs/settings.xml` using
   `hur-wifi-test-scripts/set_hu_prefs.sh`.
2. Bring the session back up, open row **C** (`KEYBOARD_PHONE`).
3. Screencap as `kb-C-rotary.png`.
4. **Restore `enable-rotary` to its original value** and verify.

Report the log line `[ServiceDiscovery] Announcing Rotary/Touchpad support` as proof the setting took
effect, and whether the keyboard changed.

---

## 5. Verdicts

- **PASS** if R1 renders a numeric keypad. That is a usable lever and the round's finding. Say
  whether digits reach the field and what `*` and `#` do.
- **PARTIAL** if R1 renders but is unusable (no digits captured, no way to confirm, keypad drawn but
  dead).
- **FAIL** if R1 is refused or draws nothing. Attach the log showing why; the refusal reason is the
  result. This is a real answer, not a wasted round: it closes the last open lever.
- **BLOCKED** if the phone pre-flight shows a `FAIL`, or the probe crashes again. Report the
  exception; do not spend a session on it.
- **INCONCLUSIVE** if R2 does not reproduce round 1.

R3 is reported alongside but does not set the verdict either way.

Record in Setup notes: the pre-flight output, the Android Auto version on D-POCO, the head unit build
on D-HU, the wireless mode, and whether the session was split or fullscreen.
