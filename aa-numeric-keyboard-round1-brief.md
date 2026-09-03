# aa-numeric-keyboard, round 1: does Android Auto honour `setKeyboardType(KEYBOARD_NUMBER)` on a projected head unit?

**Candidate:** none. **This round does not build or install the head unit app**, and nothing on any `pr/` branch changes.
**What is installed:** a throwaway phone-side Android Auto template app, source in `evidence/aa-numeric-keyboard-round1/aa-keyboard-probe-src.zip`, prebuilt debug APK beside it as `aa-keyboard-probe-debug.apk` (md5 `153675a0c01cd1433e4d97eb0335de84`).

Read `TESTING-TEMPLATE.md` first, and §7a before planning. This brief carries only what is specific to this round.

> **This is the one round on this branch where the head unit app is not the subject.** D-HU runs
> whatever build is already on it, in whatever wireless mode already works. The probe is a third
> party app on the phone, and the thing being measured is what Android Auto draws on the head unit
> screen. Do not reflash D-HU for this round.

---

## 1. Why this round exists

A third party Android Auto app enters numeric data (fuel volume, odometer, price) through
`SignInTemplate` with `InputSignInMethod`, and every field opens the full QWERTY keyboard. Its
developer believes the car app library gives no way to ask for a numeric pad, and that testing this
about a year ago showed the full keyboard regardless of the input type set.

The library disagrees. `InputSignInMethod.Builder.setKeyboardType(int)` has accepted
`KEYBOARD_NUMBER` since `androidx.car.app` 1.1.0-alpha01 (2021-06-16, stable in 1.1.0 on
2021-12-15). The class is `@RequiresCarApi(2)` and the setter carries no gate of its own, and Car
App API level 2 has worked on real head units since Android Auto 6.7.

A teardown of Android Auto `17.5.663204` says the host honours it. Two independent render paths
read the value. The legacy View path maps it straight onto the `EditText`:

```java
int keyboardType = inputSignInMethod.getKeyboardType();
int i = keyboardType != 2 ? keyboardType != 3 ? keyboardType != 4 ? inputSignInMethod.getInputType() == 2 ? 129 : 1 : inputSignInMethod.getInputType() == 2 ? 18 : 2 : 3 : 33;
inputSignInView.b.setInputType(i);
```

`KEYBOARD_NUMBER` (4) becomes `2`, `InputType.TYPE_CLASS_NUMBER`. `KEYBOARD_PHONE` (3) becomes `3`,
`TYPE_CLASS_PHONE`. `KEYBOARD_EMAIL` (2) becomes `33`, text plus the email variation. Anything else
becomes `1`, plain text. The newer path resolves 4 to `KeyboardType.Number`.

That is static analysis of someone else's binary. It predicts what the screen does; it does not show
it. This round shows it, or refutes it.

**The finding is the round whichever way it falls.** A numeric pad confirms the reading and gives
the developer something he can act on. A full QWERTY on all four means the host ignores the value on
this hardware, and the decompiled mapping is reached but overridden somewhere downstream, which is
worth knowing before anyone repeats the claim.

---

## 2. What is different about this round

- **No APK gate, no unit test gate, no log capture in the usual sense.** The artifact is
  screenshots. §2's `stdbuf -oL` capture rule does not apply; take a logcat anyway (§4) but it is
  secondary.
- **The probe installs on the phone (D-POCO), not on D-HU.** It is a normal debug APK.
- **Android Auto will not list an unpublished template app unless developer mode and Unknown
  sources are both on.** This is the step that most often ends such a round with "the app never
  appeared". §3 covers it.
- **Screenshots come from D-HU**, because D-HU is the head unit and the projected surface is its
  screen. `adb shell screencap` on D-HU captures exactly what the driver sees, at full resolution.
  Do not photograph the screen.
- **The probe offers four keyboard types from one build.** That is the point: the four screens
  differ only in the int passed to `setKeyboardType`, so a difference between them cannot be
  attributed to the library version, the host version, the phone, or app configuration.
- **The app appears as "AA KB Probe"** with a generic pencil icon, under the phone's app list on the
  head unit.

---

## 3. Setup

The probe is unpublished, so Android Auto hides it by default. On **D-POCO**:

```bash
adb -s <D-POCO> install -r aa-keyboard-probe-debug.apk
```

Then, on the phone itself (this part is UI, there is no adb lever for it):

1. Android Auto settings, scroll to the bottom, tap **About**, tap the version line ten times until
   the developer toggle appears.
2. Overflow menu, **Developer settings**.
3. Enable **Unknown sources**.

Verify the app is installed and that Android Auto can see a template service:

```bash
adb -s <D-POCO> shell pm list packages | grep aakbprobe
adb -s <D-POCO> shell dumpsys package com.example.aakbprobe | grep -A3 "androidx.car.app.CarAppService"
```

If the round has to build rather than use the prebuilt APK, the source zip is a standalone Gradle
project (AGP 8.13.2, Gradle 8.13, `compileSdk 36`, one dependency, `androidx.car.app:app:1.4.0`, no
NDK and no CMake):

```bash
unzip aa-keyboard-probe-src.zip && cd aa-keyboard-probe
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

Bring up a normal projected session between D-POCO and D-HU in whatever mode currently works. Then
on the head unit open the phone's app launcher and start **AA KB Probe**.

---

## 4. Runs

One run per keyboard type, four in all. Each is the same three steps.

For each of rows **A** (`KEYBOARD_NUMBER`), **B** (`KEYBOARD_DEFAULT`), **C** (`KEYBOARD_PHONE`) and
**D** (`KEYBOARD_EMAIL`):

1. From the probe's list screen, tap the row. The sign-in screen opens and the keyboard appears on
   its own, because the probe sets `setShowKeyboardByDefault(true)`. No tap on the field is needed,
   and none should be made, so nothing but the constant can be blamed for the result.
2. Capture the head unit screen:

```bash
adb -s <D-HU> shell screencap -p /sdcard/kb-A.png
adb -s <D-HU> pull /sdcard/kb-A.png
```

3. Press Back to return to the list.

Name them `kb-A.png` through `kb-D.png` and attach all four.

Take one logcat over the whole round from the phone, as a secondary record:

```bash
adb -s <D-POCO> logcat -d > aa-kb-probe-logcat.txt
```

---

## 5. What each result means

| Row | Constant | Predicted keyboard | If it matches | If it does not |
|---|---|---|---|---|
| A | `KEYBOARD_NUMBER` (4) | numeric keypad, digits only | the host honours it, and the claim is settled | the mapping is reached but overridden downstream; A vs B is then the whole finding |
| B | `KEYBOARD_DEFAULT` (1) | full QWERTY | the control behaved | if B is *also* numeric, the probe is wrong, not the host |
| C | `KEYBOARD_PHONE` (3) | dialpad, with `+` `*` `#` | mapping to `TYPE_CLASS_PHONE` confirmed | narrows where the value is lost |
| D | `KEYBOARD_EMAIL` (2) | QWERTY with a visible `@` key | mapping to the email variation confirmed | as above |

**A and B differing is the result.** C and D are there to show the whole mapping moves together
rather than one constant happening to work.

Record, in Setup notes: the Android Auto version on D-POCO
(`adb -s <D-POCO> shell dumpsys package com.google.android.projection.gearhead | grep versionName`),
the head unit build on D-HU, and the wireless mode the session used.

---

## 6. Verdicts

- **PASS** if A shows a numeric keypad and B shows the full QWERTY, with C and D as predicted.
- **PARTIAL** if A differs from B but C or D does not match its prediction. Say which.
- **FAIL** if A and B look the same. Attach both screenshots; that outcome is more interesting than
  a pass and must not be summarised in words alone.
- **BLOCKED** if the probe never appears on the head unit. The usual cause is Unknown sources being
  off, so state which of the three setup steps was reached.
