# aa-numeric-keyboard — round 2b results

**Candidate:** none — no head unit build changed.
**Probe APK:** `evidence/aa-numeric-keyboard-round2b/aa-keyboard-probe-r2b.apk` md5 `e40ec5ac2a9f9c156846f74a44dbbafe` (matches brief). Installed on D-POCO with `install -r` (same signing key as r2, no uninstall); installed `base.apk` md5 verified `e40ec5ac2a9f9c156846f74a44dbbafe`.
**Head unit (D-HU):** UNISOC MT50 (`MT50_YT610E4GFPSL_U`), Android 14. Open Headunit `3.3.0` versionCode 103, installed APK md5 `0abee20c530f234033508c8d63c7769d` (= `fork/testing/automation-plus-btautostart` @ `e9ab7f13`, the build pr-readiness round 1 tested). Wireless mode 3 (Native AA). `view-mode=2` (TEXTURE). Screen 1440x720 landscape.
**Phone (D-POCO):** POCO X3 NFC (`M2007J20CG`), Android 15, Android Auto (Gearhead) `17.5.663204-release`.
**Session:** one live projected Native AA session per phase, fullscreen, `AapProjectionActivity` focused, 10–57 fps. Session survived the whole round.
**Date:** 2026-09-02

---

## Verdict

**R1 (the run that matters): PASS.** Android Auto `17.5.663204` renders `TelephoneKeypadTemplate`
for a non-`CALLING` POI app. The probe is a `template` app with no `CALLING` category — the host's
`Raw list of template calling apps found: []` is empty every enumeration — and the host still
accepted and drew the template. Digits reach the number field; `*` and `#` are accepted as literal
characters; the check-mark action fires and receives the full entered string.

**There is no decimal key.** The `*` key (labelled `dec?` in the probe for exactly this question)
produces a literal `*`, not `.`. A fuel-amount entry that needs `12.5` cannot be done with this
template as-is — the app would have to accept `*` or `#` as a decimal separator and rewrite it.

**R2 (control): PASS.** Rows A (`KEYBOARD_NUMBER`) and D (`KEYBOARD_EMAIL`) both produce the
identical full QWERTY keyboard, matching round 1. Build is not suspect; R1 can be trusted.

**R3 (rotary branch): FAIL, as predicted — does not affect the verdict.** With `enable-rotary=true`
the head unit still announces `hasTouchScreen=true` (alongside `hasRotaryController=true`,
`hasDpad=true`), so Gearhead keeps the touch keyboard. Row C (`KEYBOARD_PHONE`) rendered the same
full QWERTY as A and D — no dialpad.

**Shipping read:** `TelephoneKeypadTemplate` is a working lever for putting a numeric keypad on the
head unit screen for a POI-category template app on AA 17.5. It draws its own keypad, so it does not
depend on `setKeyboardType()` (which round 1 proved inert) or on the head unit's input
configuration. The one gap for a fuel-amount use case is the missing decimal key.

---

## Setup notes

### Pre-flight (brief §3) — all five OK

Ran on D-POCO before touching the car, `evidence/aa-numeric-keyboard-round2b/preflight-dpoco.png`:

```
OK    A KEYBOARD_NUMBER
OK    B KEYBOARD_DEFAULT
OK    C KEYBOARD_PHONE
OK    D KEYBOARD_EMAIL
OK    E TelephoneKeypadTemplate
```

No `FAIL`, no crash. The round-2 `onGetTemplate()` crashes (`ParkedOnlyOnClickListener`,
`0 custom-title actions`) are fixed — every screen built and rendered on the head unit this round,
and the r2b "Back to list" body action works (used it to move between rows A → D without a
force-stop).

### Deviations from the brief / protocol

- **D-POCO logcat was not `-c`-cleared at round start.** Only the head unit was cleared (§2). The
  phone capture (`poco-session.txt`, this session, first line ~16:55) therefore still contains
  round 2's `FATAL EXCEPTION` / `IllegalArgumentException` lines at `16:27`–`16:31`. Every
  `aakbprobe` crash line in the capture predates `16:55`; there are **zero** after the session
  started. Evidence file `dpoco-carapp.txt` is filtered to the CarApp template lines and is clean.
- **The projected AA UI is a single opaque surface.** `uiautomator dump` on the head unit returns
  one `android.view.View [0,0][1440,720]` (the video) — the AA launcher, probe list and templates
  are all phone-rendered pixels. All navigation was `adb -s <D-HU> shell input tap <x> <y>` by
  coordinate read off a `screencap`, the same method rounds 1 and 2 used. Taps land 1:1 in the
  1440×720 layout. Two coordinate quirks: a tap above y≈110 near the top opens OHU's own
  brightness/volume overlay (top-edge gesture); the AA app-grid icon is reached by tapping the
  bottom-left nav-rail icon **twice** (first tap opens a split view, second opens all-apps).
- **One list swipe on the head unit** to bring row E fully on screen (`input swipe 700 550 700 300`),
  screencapped to confirm before tapping — same single swipe round 2 did. The probe list is a
  5-item `ListTemplate`, fully enumerable; this is not a blind settings-list scroll.
- **R3 needed a settings write + app relaunch + session rebuild.** `enable-rotary` was written with
  `hur-wifi-test-scripts/set_hu_prefs.sh` (rooted head unit, `set boolean enable-rotary true`),
  then OHU relaunched and D-POCO Bluetooth cycled to bring the session back. Restored to `false`
  the same way and verified. Full `settings.xml` diff against the start-of-round backup: only
  `wifi-direct-last-group-bssid` changed (the app writing its own runtime P2P group BSSID), on
  every capture. No other delta; `exporterLogLevel`, `view-mode`, `wifi-connection-mode` untouched.
- **Session bring-up** was `svc bluetooth disable/enable` on D-POCO (head-unit Bluetooth is not
  switchable on this rig, §7a) with OHU already running — the phone's own reconnect formed the
  session 3–5 s after, `SSL handshake complete` at 16:55:27 (R1/R2 phase) and 17:02:14 (R3 phase).

### Discard-rule check

Per phase, exactly one intended session:

| Capture | `createGroup SUCCESS` | `SSL handshake complete` | `MATCH! Starting AapService` | `p2p-wlan0-N` |
|---|---|---|---|---|
| `hu-session.txt` (R1+R2 phase, then R3 relaunch) | 2 | 2 | 2 | -0, -1 |
| `hu-r3.txt` (R3 phase only) | 1 | 1 | 1 | -0, -1 |

The two events in `hu-session.txt` are the two deliberate D-POCO Bluetooth cycles (one to bring up
the R1/R2 session, one to bring up the R3 session), ~7 min apart, each producing exactly one group
and one handshake. No second `createGroup` inside a single phase, no unintended reconnect. Clean.

### Scripts

No new script. `hur-wifi-test-scripts/set_hu_prefs.sh` used for the R3 `enable-rotary` write and
restore.

---

## R1 — `TelephoneKeypadTemplate` from a POI app

**PASS**

- Settings written: none.
- Radio state: D-POCO Bluetooth cycled to form the session; head unit left as-is.
- Discard-rule check: clean (see table).
- Decisive log lines (D-POCO, `poco-session.txt`):
  ```
  16:55:28.192 D/CarApp.H : Raw list of template calling apps found: []
  16:57:21.240 I/CarApp.H.Tem : Host received new template (type: androidx.car.app.model.ListTemplate)      <- probe MenuScreen
  16:58:08.189 I/CarApp.H.Tem : Host received new template (type: androidx.car.app.dialer.TelephoneKeypadTemplate)
  ```
  No `CarApp.H.Tem: Error`, no `FATAL EXCEPTION`, no "not a calling app" / category refusal after
  the template was sent. `Raw list of template calling apps found: []` is empty on every
  enumeration (16:55, 16:56, 17:02, 17:02) — the probe is not a `CALLING` app and the template
  rendered anyway.
- Screencaps:
  - `kb-E.png` — the rendered keypad on the head unit: digits 1–9, `*` (labelled `dec?`), `0`, `#`,
    a backspace (top right), and a check-mark end action (bottom right). Empty number field on the
    right half.
  - `kb-E-digits.png` — after tapping 1,2,3,4,5: field shows `12345`.
  - `kb-E-starhash.png` — after tapping `*` then `#`: field shows `12345*#`. Both accepted as
    literal characters.
  - `kb-E-checkmark.png` — after tapping the check-mark action: a `GhToast`
    (`16:59:03.003 CAR.PROJECTION.PRES … GhToast … show()`) reading **`entered: 12345*#`**. The
    action receives the full string including `*` and `#`.
- Measurements the brief asked for:
  - Digits reach the field: **yes**, verbatim.
  - `*` and `#`: **accepted**, appended as literal `*` / `#`.
  - Decimal key: **none.** `*` produces `*`, not `.`. No key on the template emits a decimal
    separator.

The template draws its own keypad and never invokes the car keyboard, so it is not affected by
round 1's finding that `setKeyboardType()` is inert on this host. It is `@ExperimentalCarApi`
(`androidx.car.app.dialer`, added 1.8.0) and AA 17.5 has a working presenter for it even for a
non-`CALLING` app.

## R2 — control, rows A and D

**PASS**

- Settings written: none.
- Discard-rule check: clean.
- Decisive log lines (D-POCO):
  ```
  16:59:41.787 I/CarApp.H.Tem : Host received new template (type: androidx.car.app.model.signin.SignInTemplate)   <- row A
  17:00:51.184 I/CarApp.H.Tem : Host received new template (type: androidx.car.app.model.signin.SignInTemplate)   <- row D
  ```
  No crash on either — the r2b `ParkedOnlyOnClickListener` fix holds.
- Screencaps:
  - `kb-A-r2b.png` — row A (`KEYBOARD_NUMBER`, title `setKeyboardType(4)`): full QWERTY with a
    number row on top, `!`, `?`, `,`, `.`, `-`, space. No dedicated numeric pad.
  - `kb-D-r2b.png` — row D (`KEYBOARD_EMAIL`, title `setKeyboardType(2)`): **byte-for-layout
    identical** to row A — same number row, same QWERTY, same `.` / `-`, no visible `@` key.
- The two are identical to each other and to round 1's `kb-A.png` / `kb-D.png`. Build is sound.

## R3 — the rotary branch

**FAIL (predicted)** — reported alongside, does not set the verdict.

- Settings written: `enable-rotary` → `true` (restored to `false` after).
- Decisive log lines:
  ```
  D-HU  17:02:15.464 I/OPENHU : [ServiceDiscovery] Announcing Rotary/Touchpad support
  D-POCO 17:02:15.563 I/CAR.INPUT : Discovered input for display 0 … CarUiInfo (hasRotaryController: true, touchscreenType: 1, hasDpad: true, hasTouchpadForUiNavigation: false …)
  D-POCO 17:02:17.364 D/CAR.PROJECTION.PRES : GhFacetBar updateConfiguration(Config{hasTouchScreen=true, hasRotaryController=true, hasTouchpadForNavigation=false …})
  D-POCO 17:05:09.169 I/CarApp.H.Tem : Host received new template (type: androidx.car.app.model.signin.SignInTemplate)   <- row C
  ```
  `Announcing Rotary/Touchpad support` confirms the setting took effect. But `touchscreenType: 1`
  and `hasTouchScreen=true` are still announced — `enable-rotary` adds rotary/D-pad, it does not
  remove the touchscreen. The brief's decode says the rotary keyboard (`xdv`, with the
  `(inputType & 15) == 3` dialpad branch) is selected only when the head unit reports a rotary
  controller **and no touchscreen**.
- Screencap `kb-C-rotary.png` — row C (`KEYBOARD_PHONE`, `setKeyboardType(3)`): the same full
  QWERTY as A and D. No dialpad. The touch keyboard (`xdm`) stayed.

---

## Anything the brief did not ask about

- **The probe is a fully first-class template app on this host now.** Enumerates
  (`Raw list of car apps found` includes `com.example.aakbprobe` every scan), binds
  (`CarApp.H.Tem: Binding to: com.example.aakbprobe`), gets a launcher icon, and every one of its
  five screens renders. Round 1's descriptor blocker and round 2's builder-constraint crashes are
  both closed.
- **`TelephoneKeypadTemplate`'s chrome is minimal.** The end action is icon-only (the
  0-custom-title constraint), and the template gives the app the number field + keypad and little
  else. An app using it for fuel entry gets the keypad but cannot label the confirm action or add
  much around it.
- **The `dec?` key behaves as a normal `*`.** It is not a special key the app can repurpose from
  the template side — the character it emits is `*`. Any decimal handling would be the app parsing
  `12*5` as `12.5` itself.
- **The session was completely undisturbed by the probe** across ~10 min, three template loads,
  two keyboard loads and an `enable-rotary` toggle + relaunch — one stable AAP session per phase,
  50 fps on the final screencap.
