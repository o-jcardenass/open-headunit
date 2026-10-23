# aa-numeric-keyboard, round 2: can anything put a numeric keypad on the head unit screen?

**Candidate:** none. **No head unit build changes.** D-HU keeps whatever is installed, in whatever wireless mode works.
**Probe:** `evidence/aa-numeric-keyboard-round2/aa-keyboard-probe-r2.apk` md5 `1a9086a52565f603dfaf38a7299e40fe`, source `…-r2-src.zip`. Installs on **D-POCO**, not D-HU.

Read `TESTING-TEMPLATE.md` first, and §7a before planning. This brief carries only what is specific to this round.

> **Round 1 settled that `setKeyboardType()` does nothing, so this round does not re-ask that.** It
> tests the two things that could still work. Round 1's probe was denied by Gearhead's validator and
> the operator had to rebuild it; that fix is folded in here, so this APK should enumerate first try.

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

## 2. What is different about this round

- **The artifact is screenshots**, as in round 1. `stdbuf -oL` capture still applies for the logcat,
  which is the artifact if R1 is refused rather than rendered.
- **The probe now carries `res/xml/automotive_app_desc.xml`** with `<uses name="template" />` and the
  `com.google.android.gms.car.application` meta-data. That was round 1's blocker. If
  `Package DENIED; Uses for TEMPLATE not defined` appears again, stop and report it: the fix did not
  take, and nothing else in the round can run.
- **Every screen now has a "Back to list" action in the template body.** Round 1 found header actions
  and `ActionStrip` are not drawn, and the projection swallows hardware BACK, so screens were a
  one-way trip. Use that button, not BACK.
- **Rows A to D are unchanged from round 1** and act as the control.
- The probe is built against `androidx.car.app:app:1.8.0-beta01`, up from 1.4.0, because
  `TelephoneKeypadTemplate` was added in 1.8.0.
- App still appears as **"AA KB Probe"**.

---

## 3. Setup

As round 1. On **D-POCO**:

```bash
adb -s <D-POCO> install -r aa-keyboard-probe-r2.apk
adb -s <D-POCO> shell pm list packages | grep aakbprobe
```

Android Auto developer mode on (tap the version in About ten times), then **Developer settings ->
Unknown sources** enabled, or the app is not listed. Bring up a normal projected session, open the
phone's app launcher on the head unit, start **AA KB Probe**.

Confirm enumeration before doing anything else:

```bash
adb -s <D-POCO> logcat -d | grep -E "CAR.VALIDATOR|Raw list of car apps found" | grep -i aakbprobe
```

---

## 4. Runs

### R1 — `TelephoneKeypadTemplate` from a POI app (the run that matters)

Row **E**. This template draws its **own** keypad as part of the template, so it never goes through
the car keyboard. It is public API (`androidx.car.app.dialer`, added 1.8.0, `@ExperimentalCarApi`) and
AA 17.5 registers a presenter for it and lists it alongside `ListTemplate` and `GridTemplate`.

The open question is whether the host renders it for a **POI** app or refuses it because the app is
not a `CALLING` app.

1. Tap row E.
2. `adb -s <D-HU> shell screencap -p /sdcard/kb-E.png` and pull it. Name it `kb-E.png`.
3. If a keypad renders: tap several digits and confirm they appear in the number field, then tap
   **Save** and record whether the toast shows the digits. Also tap `*` and `#` and note whether they
   are accepted, since there is **no decimal key** and a fuel amount needs one.
4. If nothing renders, or the screen is blank or errors, capture the log:

```bash
adb -s <D-POCO> logcat -d > r1-keypad-template.txt
```

### R2 — control, rows A and D

Re-run row **A** (`KEYBOARD_NUMBER`) and row **D** (`KEYBOARD_EMAIL`) on this build exactly as in
round 1. Screencap both as `kb-A-r2.png` and `kb-D-r2.png`. They must still look identical to each
other and to round 1. If they now differ, the build is suspect and R1 cannot be trusted either.

### R3 — the rotary branch, cheap check

**This one is predicted to fail, and that is fine.** The decode says the rotary keyboard is selected
only when the head unit reports `hasRotaryController` **and no touchscreen**. Open Headunit announces
a touchscreen unconditionally, and `enable-rotary` only adds a *touchpad*, which is a different flag.
So the touch keyboard should stay. Run it because it is one key and one session.

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
  result.
- **INCONCLUSIVE** if R2 does not reproduce round 1.

R3 is reported alongside but does not set the verdict either way.

Record in Setup notes: the Android Auto version on D-POCO, the head unit build on D-HU, the wireless
mode, and whether the session was split or fullscreen.
