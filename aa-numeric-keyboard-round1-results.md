# aa-numeric-keyboard — round 1 results

**Candidate:** none — no head unit build changed. Phone-side probe only.
**Probe APK:** shipped `evidence/aa-numeric-keyboard-round1/aa-keyboard-probe-debug.apk` md5 `153675a0c01cd1433e4d97eb0335de84` **did not run** (see Setup notes); the round was completed with a rebuilt probe, `evidence/aa-numeric-keyboard-round1/rebuild/aa-keyboard-probe-r1rebuild.apk` md5 `c4420d1c18e699326d54708e9c5f88eb`.
**Head unit (D-HU):** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, Open Headunit `3.3.0` versionCode 103 (the `testing/automation-plus-btautostart` @ `e9ab7f13` build already installed). Wireless mode 3 (Native AA). Screen 1440x720 landscape.
**Phone (D-POCO):** POCO X3 NFC (`M2007J20CG`), Android 15, Android Auto (Gearhead) `17.5.663204-release`.
**Date:** 2026-09-02

---

## Verdict

**FAIL.** `InputSignInMethod.setKeyboardType()` has **no effect** on this host. All four
keyboard constants — `KEYBOARD_NUMBER` (4), `KEYBOARD_DEFAULT` (1), `KEYBOARD_PHONE` (3),
`KEYBOARD_EMAIL` (2) — render the **identical full QWERTY keyboard** (persistent digit row +
full a–z + `! ?`, symbol toggle `= <`, space, `.` `-`). No numeric keypad, no dialpad, no
dedicated `@` key. A and B are indistinguishable, which the brief defines as the FAIL outcome;
C and D confirm the whole mapping is inert rather than one constant happening to collide.

Four screenshots attached: `evidence/aa-numeric-keyboard-round1/screens/kb-A.png` ..
`kb-D.png`. They are the artifact — see them, the layouts are pixel-equivalent (mean abs
pixel difference over the keyboard region: A↔B 0.74/255, A↔C 1.42/255, A↔D 0.88/255 — all
attributable to the semi-transparent keyboard letting the moving map/nav content behind bleed
through between shots).

The decompiled `17.5.663204` mapping in the brief (`KEYBOARD_NUMBER` → `TYPE_CLASS_NUMBER`,
etc.) is therefore **reached-but-overridden, or not reached, on this hardware** — cannot tell
which from the app log alone; either way the screen does not change.

---

## Setup notes

### The shipped probe never ran — it is rejected by Gearhead's validator

The prebuilt `aa-keyboard-probe-debug.apk` (and a first rebuild that only added the
`androidx.car.app.MAP_TEMPLATES` uses-permission) is **denied before app enumeration**:

```
W/CAR.VALIDATOR: Package DENIED; Uses for TEMPLATE not defined [com.example.aakbprobe]
D/CarApp.H: Settings template apps found: []
```

(`com.google.android.googlequicksearchbox` gets the identical denial — this is a standard gate.)
It never appears in the AA launcher, in projection **or** in Self Mode, and `CarApp.H`'s
"Raw list of car apps found" omits it entirely. `MAP_TEMPLATES` alone does **not** clear it
(build md5 `8b4ecc08…`, still 34 DENIED lines). Before/after log lines:
`evidence/aa-numeric-keyboard-round1/aa-kb-probe-validator-evidence.txt`.

**Root cause:** the probe declares no automotive app descriptor. Gearhead's `CAR.VALIDATOR`
"Uses for TEMPLATE" check wants `<uses name="template" />` in an
`res/xml/automotive_app_desc.xml`, referenced from the manifest with
`<meta-data android:name="com.google.android.gms.car.application" android:resource="@xml/automotive_app_desc" />`.
This is the same descriptor media apps use (`<uses name="media" />` — hence the parallel
"Uses for MEDIA not defined" denials for Bluetooth/YouTube/Brave in the same log).

**Fix applied to complete the round:** rebuilt the probe from the source zip with

1. `app/src/main/res/xml/automotive_app_desc.xml` = `<automotiveApp><uses name="template" /></automotiveApp>`
2. the `com.google.android.gms.car.application` meta-data
3. `<uses-permission android:name="androidx.car.app.MAP_TEMPLATES" />` (kept — correct for a POI
   app, though not the thing the validator was failing on)

With the descriptor present the probe is enumerated first try, Gearhead binds it,
negotiates Car App API 7, builds its icon, and it shows in the launcher as "AA KB Probe":

```
D/CarApp.H:  Raw list of car apps found: [ … com.example.aakbprobe/.ProbeCarAppService … ]
I/HostConfig: App: [com.example.aakbprobe/.ProbeCarAppService] app info: [Library version: [1.4.0] Min Car Api Level: [2] …]
D/HostConfig: App: [com.example.aakbprobe/.ProbeCarAppService], Host negotiated api: [7]
D/CarApp.H.Tem: Creating car host instance for com.example.aakbprobe/com.example.aakbprobe.ProbeCarAppService
```

Rebuilt APK + patched source: `evidence/aa-numeric-keyboard-round1/rebuild/`
(`aa-keyboard-probe-r1rebuild.apk` md5 `c4420d1c…`, `…-src.zip`). Build was
`./gradlew assembleDebug` with `-Dorg.gradle.java.home=/opt/android-studio/jbr` (the system
`/usr/lib/jvm/java-21-openjdk-amd64` is a JRE with no `javac` and Gradle's toolchain
auto-detect picks it and fails; the Android Studio JBR is the only JDK on the box).

**This should go back into the shipped probe before any re-run.**

### `setShowKeyboardByDefault(true)` is also not honoured

On every one of the four screens the SignInTemplate opened with the input field but **no
keyboard**, fullscreen or split. The keyboard only appears after a tap on the field
(`evidence/…/screens/A-signin-no-keyboard-autoshow.png` shows row A sitting with no keyboard).
So every capture required one tap on the "Liters" field — a deviation from the brief's
"no tap, nothing but the constant can be blamed". It does not affect the result: the field's
`InputType` is fixed by `setKeyboardType` regardless of how the keyboard is summoned, and B
(the control) got the identical treatment and the identical keyboard.

### SignInTemplate on this host renders no header action and no ActionStrip

`setHeaderAction(Action.BACK)` draws nothing. A rebuild that also added an `ActionStrip` with a
"MENU" action (`getScreenManager().pop()`) — that ActionStrip is not drawn either. Combined
with OHU's projection swallowing the hardware BACK (it raises AA's own "Exit Android Auto"
dialog, or via OHU's overlay Back/Home button exits the projection entirely), **a Screen pushed
onto the probe's MenuScreen is a one-way trip on this rig**. Each of C and D therefore needed
the probe relaunched from the AA launcher by the operator. Worth folding into the shipped
probe: give each keyboard screen its own visible pop control **in the template body** (the one
place this host does render actions) rather than relying on header/strip/BACK.

### Rig deviations

- **Fullscreen AA is not natively available on this head unit** — it defaults to a split
  layout with the MT50's own map pane. The operator forced fullscreen by raising the head
  unit's display DPI. A/B were captured both split and fullscreen (identical result); C/D
  fullscreen only.
- OHU's on-screen FPS/stats HUD (top-left) sits over the AA header and eats taps there.
  `show-fps-counter` was set `false` via `hur-wifi-test-scripts/set_hu_prefs.sh` (app
  force-stopped) for the fullscreen A/B/C/D pass and **restored to `true`** at round end
  (verified). `set_hu_prefs.sh` rewrites the whole `settings.xml`, so key ordering/formatting
  may differ from the pre-round file though every value matches; no other key was touched.
- D-HU's AA session dropped several times during setup (known MT50 flakiness); every capture
  in the results was taken from a live session (`AapProjectionActivity` focused, FPS > 0).
- Gearhead on D-POCO was force-stopped twice during setup to force app re-enumeration. That
  kills D-POCO's Self Mode dev head-unit server on `:5277` — the operator must re-toggle
  "Start head unit server" in AA Developer settings before the next Self Mode round on that phone.

### Scripts

No new script. `hur-wifi-test-scripts/set_hu_prefs.sh` used for the HUD toggle and restore.
Captures were `stdbuf -oL adb … logcat -v time` to file.

---

## R1 — KEYBOARD_NUMBER (constant 4)

**FAIL**

- Probe row A, `setKeyboardType(4)`. Fullscreen, session live.
- Keyboard did not auto-show; raised by one tap on the "Liters" field.
- Result: **full QWERTY** — top row `1 2 3 4 5 6 7 8 9 0`, then `q w e r t y u i o p` /
  `a s d f g h j k l` / `⇧ z x c v b n m ! ?` / `⌨ =< , [space] . - ✓`. No numeric keypad.
- `evidence/aa-numeric-keyboard-round1/screens/kb-A.png`

## R2 — KEYBOARD_DEFAULT (constant 1)

**FAIL** (this is the control; A == B is the round's result)

- Probe row B, `setKeyboardType(1)`. Same conditions, same one field tap.
- Result: **full QWERTY, identical to R1** — same keys, same positions.
- `evidence/aa-numeric-keyboard-round1/screens/kb-B.png`

## R3 — KEYBOARD_PHONE (constant 3)

**FAIL** (predicted a dialpad with `+ * #`)

- Probe row C, `setKeyboardType(3)`. Same conditions.
- Result: **full QWERTY, identical to R1/R2**. No dialpad.
- `evidence/aa-numeric-keyboard-round1/screens/kb-C.png`

## R4 — KEYBOARD_EMAIL (constant 2)

**FAIL** (predicted QWERTY with a visible `@` key)

- Probe row D, `setKeyboardType(2)`. Same conditions.
- Result: **full QWERTY, identical to R1/R2/R3**. No dedicated `@` on the primary layer
  (it is on the `= <` symbols layer, same as the other three).
- `evidence/aa-numeric-keyboard-round1/screens/kb-D.png`

---

## Anything the brief did not ask about

- **The finding for the app developer who raised this:** on Android Auto `17.5.663204` +
  this head unit, there is no app-side lever for a numeric pad in `SignInTemplate` —
  `setKeyboardType` is silently ignored for every value. His year-old observation still holds
  on current Gearhead. Whether it is host-version-specific or universal needs a second head
  unit / Gearhead build to say; the decompiled mapping the brief quotes exists in the binary
  but does not reach the screen here.
- **`SignInTemplate` is threadbare on this host**: no header action, no action strip, no
  `showKeyboardByDefault`. Only the title, the instructions line, the input field, and
  (after a tap) the keyboard render. An app relying on any of those SignInTemplate features
  for its sign-in flow would have a worse problem than the keyboard type.
- The probe's `MainActivity` is a bare splash that finishes immediately — fine, but it means
  "launch it once on the phone to clear the stopped-state flag" is the only thing it is good
  for; there is no phone-side UI to confirm anything.
- Two independent Gearhead instances (projection to D-HU, and D-POCO's own Self Mode) both
  rejected the pre-descriptor probe identically — so the descriptor requirement is in the
  Car App host validator, not in anything projection-specific.
