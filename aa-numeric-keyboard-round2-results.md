# aa-numeric-keyboard — round 2 results

**Candidate:** none — no head unit build changed.
**Probe APK:** `evidence/aa-numeric-keyboard-round2/aa-keyboard-probe-r2.apk` md5 `1a9086a52565f603dfaf38a7299e40fe` (matches brief). Installed and enumerated; **crashed on every screen it was asked to open** (see below).
**Head unit (D-HU):** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14, Open Headunit `3.3.0` versionCode 103 (`testing/automation-plus-btautostart` @ `e9ab7f13`, already installed). Wireless mode 3 (Native AA). Screen 1440x720 landscape, split layout forced to fullscreen by the operator's DPI bump.
**Phone (D-POCO):** POCO X3 NFC (`M2007J20CG`), Android 15, Android Auto (Gearhead) `17.5.663204-release`.
**Session:** live projected Native AA session, `AapProjectionActivity` focused, FPS 8–32 throughout.
**Date:** 2026-09-02

---

## Verdict

**R1 (the run that matters): UNTESTABLE — the round-2 probe is broken and no screen can be
opened.** R2 and R3 UNTESTABLE for the same reason.

The probe enumerates and binds correctly — round 1's descriptor blocker is fixed (no
`Package DENIED` for `com.example.aakbprobe`, it is in `Raw list of car apps found`, host
negotiates Car App API, `Binding to: com.example.aakbprobe` succeeds, launcher icon built).
But **the r2 "Back to list" / template-body-action change that the brief §2 introduced throws
an uncaught exception in the probe's own `onGetTemplate()` on every screen**, before any
template reaches the host. Two distinct defects, one per template type:

| Screen | Rows | Crash site | Exception |
|---|---|---|---|
| `KeyboardScreen` | A, B, C, D | `KeyboardScreen.java:50` — `SignInTemplate.Builder.addAction(...)` | `java.lang.IllegalArgumentException: The action must use a ParkedOnlyOnClickListener` (`SignInTemplate.java:296`) |
| `KeypadScreen` | E | `KeypadScreen.java:35` — `new TelephoneKeypadTemplate.Builder(primary, …)` | `java.lang.IllegalArgumentException: Action list exceeded max number of 0 actions with custom titles` (`ActionsConstraints.java:402` → `TelephoneKeypadTemplate.java:268`) |

Each tap on a row pushed the screen, the probe threw in `onGetTemplate`, the host showed
its "AA KB Probe has encountered an unexpected error / Exit" panel (or "Waiting…" during the
BIND ANR that preceded the second crash), and `ActivityManager` killed the probe process.
The host never received a `TelephoneKeypadTemplate` (or any keyboard screen), so **the R1
question — does AA 17.5 render `TelephoneKeypadTemplate` for a non-`CALLING` POI app — is not
answered.** It needs a rebuilt probe.

**This is a brief/artifact defect, not a rig or host finding.** Escalating per
`TESTING-TEMPLATE.md` §3a/§8.

---

## Setup notes

### ESCALATION — the probe needs a rebuild before this round can run

Both crash sites are caused by the r2-only change described in brief §2 ("Every screen now has
a 'Back to list' action in the template body"). The androidx `car.app` action constraints
forbid exactly what the probe added:

1. **`KeyboardScreen.java:46-54`** — `SignInTemplate` body actions must carry a
   `ParkedOnlyOnClickListener`, not a plain `OnClickListener`:
   ```java
   .addAction(new Action.Builder()
           .setTitle("Back to list")
           .setOnClickListener(ParkedOnlyOnClickListener.create(
                   () -> getScreenManager().pop()))
           .build())
   ```
   (`import androidx.car.app.model.ParkedOnlyOnClickListener;`) — the head unit reports parked,
   so the listener still fires. Alternatively drop `.addAction(...)` entirely and rely on the
   `Action.BACK` header action, which round 1 already reported *this host does not draw* — so
   the `ParkedOnlyOnClickListener` route is the one to take.

2. **`KeypadScreen.java:26-35`** — `TelephoneKeypadTemplate`'s primary/end `Action` is
   constrained to **zero custom-title actions**; the probe passes an `Action` with
   `.setTitle("Save")`. It must be an icon-only action:
   ```java
   Action primary = new Action.Builder()
           .setIcon(new CarIcon.Builder(
                   IconCompat.createWithResource(getCarContext(), R.drawable.ic_save)).build())
           .setOnClickListener(() -> CarToast.makeText(
                   getCarContext(), "entered: " + mDigits, CarToast.LENGTH_LONG).show())
           .build();
   ```
   (any small vector drawable will do). The constraint is checked in the `Builder` constructor,
   so there is no way to keep a titled action here.

3. The `KeypadScreen` "Back to list" body action the brief mentions is **not present in the
   shipped `KeypadScreen.java`** (it uses `Action.BACK` as `setStartHeaderAction` on a
   `Header`, plus `setHeader(...)`); only `KeyboardScreen` got the `addAction` change. If the
   intent was for row E to also have a body "Back to list", it will hit the same
   zero-custom-title constraint and must be icon-only too.

Suggested for the rebuild: also remove `.setHeaderAction(Action.BACK)` from `KeyboardScreen`
if it is dead on this host (round 1 said it draws nothing) — harmless but noise.

### What did work

- **Descriptor fix confirmed.** `res/xml/automotive_app_desc.xml` in the APK decodes to
  `<automotiveApp><uses name="template"/></automotiveApp>`; `com.example.aakbprobe` never
  appears in a `CAR.VALIDATOR ... DENIED` line (0 hits), and appears in every
  `CarApp.H: Raw list of car apps found` from `16:25:58` onward. `Host negotiated api`, icon
  build (`GH.AppIconFactory ... com.example.aakbprobe`), and
  `CarApp.H.Tem: Binding to: com.example.aakbprobe` all present. Round 1's blocker is closed.
- **`TelephoneKeypadTemplate` classes are in the APK** (`androidx/car/app/dialer/
  TelephoneKeypadTemplate`, `$Builder`, `$PhoneNumberChangeListener`), built against
  `androidx.car.app:app:1.8.0-beta01` per the source zip. The template code shipped; the probe
  just can't construct it.
- The probe MenuScreen (`ListTemplate`) renders fine — rows A–E visible and scrollable on the
  head unit (`kb-list.png`, `kb-list2.png`).

### Rig deviations

- **Probe re-install required a signature-mismatch uninstall.** The round-1 rebuild
  (`com.example.aakbprobe`, md5 `c4420d1c…`) was still on D-POCO and is signed with a
  different key than the r2 APK, so `adb install -r` failed
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Uninstalled and clean-installed r2; installed base.apk
  md5 verified `1a9086a52565f603dfaf38a7299e40fe`. (The probe is not the OHU package, so §5's
  uninstall caveat about onboarding does not apply.)
- **`show-fps-counter` left `true`** this round (round 1 turned it off). The session was
  already live when the round started, so disabling it would have meant a force-stop and a
  fresh operator session bring-up. The HUD sits top-left over the AA header only; every row
  tap (A at y≈180, E at y≈624) landed well clear of it and every tap registered (each one
  pushed a screen and produced a crash). Not a factor in the result.
- **D-HU `settings.xml` not touched.** Diff against the start-of-round backup shows one line
  changed — `wifi-direct-last-group-bssid` (`12:65:15:30:77:94` → `B6:D3:F7:93:3D:10`) — which
  is the app writing its own runtime P2P group BSSID, not a setting write. No restore needed.
- **R3 not attempted.** It uses the same `KeyboardScreen` (row C) that crashes for A/B/D, so
  it would crash identically; it also needs an `enable-rotary` write + a fresh operator
  session, not worth spending for a guaranteed crash. Route stays on
  `WifiP2pOperatingChannelPolicyTest` / the rotary-announce path — R3 was a predicted FAIL
  anyway per the brief.

### Scripts / captures

No new script. Captures were `stdbuf -oL adb -s <serial> logcat -v time` to file
(`round-aa-numeric-keyboard-r2/dpoco-round2.txt`, `dhu-round2.txt`); D-POCO capture last
timestamp `16:32:55`, killed `16:32:57`. Filtered D-POCO CarApp/crash lines committed as
`evidence/aa-numeric-keyboard-round2/dpoco-carapp-filtered.txt`.

---

## R1 — `TelephoneKeypadTemplate` from a POI app

**UNTESTABLE**

- Session live, fullscreen, probe enumerated and bound.
- Scrolled the probe list (one swipe) to reveal row E; screencaps `kb-list.png`,
  `kb-list2.png` (row E = "E  TelephoneKeypadTemplate — a keypad drawn by the template, not
  the keyboard").
- Tapped row E (700, 624). Probe pushed `KeypadScreen`, then:
  ```
  16:27:15.445 E/AndroidRuntime( 9101): FATAL EXCEPTION: main
  16:27:15.445 E/AndroidRuntime( 9101): Process: com.example.aakbprobe, PID: 9101
  16:27:15.445 E/AndroidRuntime( 9101): java.lang.RuntimeException: java.lang.IllegalArgumentException: Action list exceeded max number of 0 actions with custom titles
  16:27:15.445 E/AndroidRuntime( 9101):   at androidx.car.app.model.constraints.ActionsConstraints.validateOrThrow(ActionsConstraints.java:402)
  16:27:15.445 E/AndroidRuntime( 9101):   at androidx.car.app.dialer.TelephoneKeypadTemplate$Builder.<init>(TelephoneKeypadTemplate.java:268)
  16:27:15.445 E/AndroidRuntime( 9101):   at com.example.aakbprobe.KeypadScreen.onGetTemplate(KeypadScreen.java:35)
  16:27:15.456 E/CarApp.H.Tem( 1513): Error: [type: null, cause: null, debug msg: java.lang.IllegalArgumentException: Action list exceeded max number of 0 actions with custom titles …]
  ```
- Host showed "AA KB Probe has encountered an unexpected error / Exit" (`kb-E.png` was
  captured just before the error panel and shows the list, from the crash-restart).
- **No `TelephoneKeypadTemplate` ever reached the host.** `Raw list of template calling apps
  found: []` is present but that is the host's normal enumeration of `CALLING`-category apps
  and is not the signal — the signal (host renders / refuses the template for a POI app) was
  never produced.

## R2 — control, rows A and D

**UNTESTABLE**

- After an operator relaunch, tapped row A (`KEYBOARD_NUMBER`) twice (700,180 then 650,155).
  Both taps pushed `KeyboardScreen` and crashed it:
  ```
  16:31:52.238 E/AndroidRuntime(10246): java.lang.IllegalArgumentException: The action must use a ParkedOnlyOnClickListener
  16:31:52.238 E/AndroidRuntime(10246):   at androidx.car.app.model.signin.SignInTemplate$Builder.addAction(SignInTemplate.java:296)
  16:31:52.238 E/AndroidRuntime(10246):   at com.example.aakbprobe.KeyboardScreen.onGetTemplate(KeyboardScreen.java:50)
  …
  16:31:59.672 E/CarApp.H.Tem( 1513): Error: [type: ANR_TIMEOUT, cause: null, debug msg: ANR API: BIND]
  16:32:16.891 E/AndroidRuntime(10307): java.lang.IllegalArgumentException: The action must use a ParkedOnlyOnClickListener   (same stack)
  16:32:16.935 I/ActivityManager( 1422): Process com.example.aakbprobe (pid 10307) has died: vis BTOP
  ```
- Row D not attempted — same `KeyboardScreen`, identical crash guaranteed.
- Screencaps `kb-A-r2-nokbd.png` (list, tap not yet processed), `kb-A-r2-try2.png` (host
  "Waiting…" panel during the BIND ANR).

## R3 — the rotary branch

**UNTESTABLE** (not attempted — see Setup notes; same `KeyboardScreen` crash).

---

## Anything the brief did not ask about

- **The probe binds and the host accepts it as a template app fully** — this round did clear
  round 1's open "is it host-version-specific or descriptor-specific" ambiguity about
  *enumeration*: the descriptor is all that was missing, and with it the probe is a
  first-class Car App on Gearhead 17.5.663204 both in projection and (round 1) in Self Mode.
- **`TelephoneKeypadTemplate`'s primary action constraint is `0` custom-title actions on
  1.8.0-beta01.** Whatever the shipped probe wanted to show as "Save", the template only
  takes an icon action there. Worth knowing for whoever writes the app-developer answer: even
  if the host renders the keypad, the template's own API gives the app very little chrome
  around it (no titled action, and — per the constraint name — a capped action list).
- The host's crash-recovery panel text differs by state: **"…has encountered an unexpected
  error"** after a `FATAL EXCEPTION`, **"Waiting…"** while a re-bind is mid-ANR. Both have an
  "Exit" button that returns to the AA launcher; neither returns to the probe's MenuScreen.
- One benign `MATCH! Starting AapService` equivalent was **not** seen — the D-HU capture shows
  a single stable session for the whole round (no second `createGroup SUCCESS`, no second SSL
  handshake). The repeated probe crashes did not disturb the AAP session.
