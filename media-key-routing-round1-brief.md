# Round 1 brief — media-key routing and the key de-duplication rewrite

Read `TESTING-TEMPLATE.md` §7a first. Two entries there decide half of this round: the A2DP link
comes and goes on its own, and the head unit's own Bluetooth cannot be switched off.

This round is unusual in one way worth stating up front: **the defect itself cannot be reproduced on
this rig.** It needs a steering wheel whose OEM key handler fans one press out to two consumers, and
this unit has neither. Everything the branch actually *does* about it is testable, and that is what
the runs below measure. R10 is the only run that reaches for the user-visible symptom, and it is a
stand-in, labelled as one.

---

## 1. Build

**Candidate:** `fix/803-media-key-double-skip` @ `f9b1ca73` on `fork`.
**Baseline (R9 only):** `origin/main` @ `64f07228`.

**History was rewritten today.** The branch used to reach `main` by merge commits and was rebased
onto `64f07228`; it is now a single commit. The tree is byte-identical to the pre-rebase head, but
`git pull` will not fast-forward — use `-B`:

```bash
git fetch fork --prune --prune-tags
git checkout -B fix/803-media-key-double-skip fork/fix/803-media-key-double-skip
git log --oneline -2
# expect: f9b1ca73  Input: let the Bluetooth side keep the media buttons, and stop mangling clicks
#         64f07228  Merge pull request #814 …
```

### R0 — build gate

`run_unit_tests.sh`, then `build_hur.sh`.

- **`KeyDebouncePolicyTest` must report 13 tests, all green** — the whole transition table. If this
  class is missing, the wrong commit is checked out.
- **`MediaKeyRoutingPolicyTest` 6/6.**
- Full suite green. Record the APK md5 and confirm it is live (§5).

GitHub CI already builds this SHA and runs the suite green, so a failure here is a rig or toolchain
difference and is worth reporting as such rather than as a branch defect.

**If R0 fails, stop and report.**

---

## 2. What this is and why it exists

A user reports that one press of the steering-wheel **Next** skips **two** tracks — but only while
Android Auto is projecting through OHU *and* the phone holds a Bluetooth media (A2DP) link to the
head unit. Their own logs settle what it is not: across five captures there is exactly **one**
`AA=87` press/release pair per press, in both Bluetooth states. The second skip never travelled over
Android Auto, so no debounce change could have stopped it.

Two consumers act on one button. The OEM key handler delivers the press to us, and the unit's
Bluetooth stack independently acts on it: AOSP's `BluetoothMediaBrowserService` publishes a media
session whose skip-to-next issues an AVRCP passthrough FORWARD **to the source device — the same
phone we are projecting**. Both arrive at the same media app.

```
steering-wheel Next
        │
   OEM key handler on the head unit
        ├────────────► head unit's Bluetooth media session ──AVRCP FORWARD──┐
        │              (exists only while A2DP is up)                       │
        └──key broadcast──► OHU ──AAP input channel───────────────────────  ┤
                                                                            ▼
                                                                   phone's media app
                                                                     = 2 track skips
```

The reporter confirmed it by toggling **Media audio** for the phone inside a single live projection
session, both directions: A2DP on → two skips, A2DP off → one. Play/pause never doubles for them,
which corroborates the mechanism — passthrough PLAY/PAUSE are state-specific and converge, FORWARD
accumulates. A fault inside this app would have doubled both.

Nothing in the platform arbitrates the two: `setConnectionPolicy` and `setActiveDevice` are
`BLUETOOTH_PRIVILEGED`, `abortBroadcast()` cannot reach the framework's media-button route because
it picks one target rather than fanning out, and taking audio focus to deactivate the sink's session
is exactly what the audio-focus rounds removed. So the lever is **which consumer we leave the key
to**, and that is a user setting.

### What the branch adds

**`MediaKeyRoutingPolicy`** — a three-way setting, `media-key-routing`:

| Value | Mode | Behaviour |
|---|---|---|
| `0` | ALWAYS | forward media keys, as today. The stored zero value, so an unset preference changes nothing for existing installs. |
| `1` | AUTO | hold media keys back while this head unit has a Bluetooth media (A2DP) link — that link is very likely the phone we project, already doing the action. |
| `2` | NEVER | never forward them. For the case AUTO cannot see: a second user on a BMW F30 whose phone is paired to the **car's** system, so our adapter reports nothing while the factory radio acts on every press. |

Only the media transport keys can be held back — `MEDIA_NEXT`, `MEDIA_PREVIOUS`, `MEDIA_PLAY_PAUSE`,
`MEDIA_PLAY`, `MEDIA_PAUSE`, `MEDIA_STOP`, `MEDIA_FAST_FORWARD`, `MEDIA_REWIND`. Rotary controllers,
D-pad, Back and Enter are forwarded in every mode. That is not incidental: the BMW user's request was
explicitly to keep the iDrive controller and lose only the media buttons, so **R6 is as much the
point of this round as R4 is.**

The A2DP probe is `BluetoothHelper.a2dpMediaLinkState()`, which now returns `null` for "the adapter
would not say" instead of guessing. Key routing resolves `null` as *forward* (buttons that quietly do
nothing read as a broken app); audio focus resolves it the other way and is unchanged. The probe is
cached **2 s** — short enough that toggling media audio takes effect without reconnecting, which is
how the reporter tested it, but it means **leave ≥3 s between changing the Bluetooth state and the
next press.**

**`KeyDebouncePolicy`** — the de-duplication in `CommManager.sendKey` rewritten as a pure object.
This is not part of the reported defect; it is three pre-existing bugs found while reading that code,
and it is the larger regression surface of the two changes:

- **The old code latched the key before the debounce returned**, so a dropped press still marked the
  key down and its release was transmitted — `DOWN, UP, UP` on the wire, present in the reporter's own
  logs. A press and its release are now dropped as a unit.
- **A press whose release never arrived latched the key forever**: every later press matched the held
  state and returned in silence until the next disconnect. That is the shape of the long-standing
  "steering keys stop working until I restart" reports. A press held longer than 2 s is now closed out
  with a release and the new press goes through.
- **Two deliberate presses inside 600 ms used to merge into one.** De-duplication is now on
  `KeyEvent.getDownTime()` where the caller has a real event, which is exact and needs no tuning. The
  600 ms / 300 ms windows survive only for the OEM broadcasts that carry no `KeyEvent`.

`sendKey` now also takes a source label, and every call site names itself, so the log finally shows
how many distinct physical presses there were rather than how many deliveries.

---

## 3. What is different about this round

**Every setting this round needs is an int in `settings.xml`.** No run requires the UI, and the new
control sits inside the one long settings list, which §7a records is not deep-linkable — so do not
try to reach it. R11 checks the *behaviour* of the setting instead, which is the stronger evidence
anyway.

**This round needs VERBOSE, and §7a warns this unit's driver stack floods logcat.** The two decisive
lines — the routing drop and the debounce drop — are `AppLog.v`; at the default INFO a suppressed key
is indistinguishable from a key that never arrived. Two mitigations, use both:

```bash
adb logcat -G 16M                    # before the capture, once per boot
```

and enable the app's **own** file log, which is written directly by the app and is immune to the
logcat ring buffer:

| Key | Type | Value |
|---|---|---|
| `log-level` | int | `0` (VERBOSE) |
| `log-source` | int | `1` (APPLOG_FILE) |
| `log-capture-enabled` | boolean | `true` |
| `log-location` | int | `0` (default) |

The file lands at `/sdcard/Android/data/com.andrerinas.headunitrevived/files/HUR_Log_<ts>.txt`, one
per `AppLog.init`. Treat it as the authoritative source for `OPENHU` lines and keep the logcat
capture for framework context. Note that `log-source=1` means those lines **stop appearing in
logcat** — if you would rather grep one stream, set `log-source=0` and accept the ring-buffer risk,
but then say which you chose in Setup notes.

**Runs are short.** Nothing here needs a 90 s settle beyond getting a session up; once projection is
live, each run is a handful of broadcasts and a few seconds.

**Three runs are gated on the A2DP link**, which §7a says cannot be forced. Check it immediately
before each one and mark the run INCONCLUSIVE if it is down — that is rig flakiness, not a finding:

```bash
adb shell dumpsys bluetooth_manager | grep -iE "a2dp|avrcp|Connected|Active Device"
```

**R10 may be UNTESTABLE and that is an acceptable outcome.** It depends on this unit publishing a
Bluetooth media session of its own, which R1 establishes.

---

## 4. Settings keys this round needs

| Key | Type | Values |
|---|---|---|
| `media-key-routing` | int | `0` ALWAYS · `1` AUTO · `2` NEVER · absent = ALWAYS |
| `log-level` | int | `0` VERBOSE |
| `log-source` | int | `1` APPLOG_FILE |
| `log-capture-enabled` | boolean | `true` |

Use `hur-wifi-test-scripts/set_pref.sh` / `set_hu_prefs.sh` per §5 — the log keys are set once for the
whole round, only `media-key-routing` changes between runs. Remember §5's warning that `set_hu_pref.sh`
relaunches the app per call.

To test the **absent-key default** in R11, run only the delete half; a blank value is not the same as
an absent key.

---

## 5. Driving a key without a steering wheel

`CommManager.sendKey` is the single entry point for every key in the app, and three exported,
permissionless components feed it. All of these need the transport **started** — `sendKey` returns
silently otherwise, with no log line at all.

```bash
PKG=com.andrerinas.headunitrevived
CARKEY=$PKG/com.andrerinas.openheadunit.connection.CarKeyReceiver
REMOTE=$PKG/com.andrerinas.openheadunit.app.RemoteControlReceiver
```

**The Microntek pair is the primary injector for this round.** It is the only adb-reachable path with
*separate DOWN and UP*, which R7 needs to forge a stuck key, and it reproduces the same **double
delivery** a real OEM key produces: `CarKeyReceiver.handleKey` both rebroadcasts a `KeyIntent` — which
the projection activity picks up as `src=key-broadcast` — and calls `sendKey` directly as
`src=carkey`.

```bash
adb shell am broadcast -n $CARKEY -a com.microntek.irkeyDown --ei keyCode 87   # press
adb shell am broadcast -n $CARKEY -a com.microntek.irkeyUp   --ei keyCode 87   # release
```

`-n` targets the component explicitly, so the Android 8+ implicit-broadcast restriction does not
apply. Landing is confirmed by `CarKeyReceiver: Handling intent action: com.microntek.irkeyDown`
(INFO), which appears whether or not a session is up — use it to tell "the broadcast never arrived"
apart from "we are not projecting".

**The second delivery requires `AapProjectionActivity` to be resumed.** Its receiver is registered in
`onResume` and unregistered in `onPause`, so if projection is not in the foreground you get one
delivery, not two, and R7 will look like it passed for the wrong reason. Confirm two `sendKey`
deliveries per press before trusting R7.

The other two injectors, each exercising a different call site:

| Command | `source` in the log | Event identity |
|---|---|---|
| `am broadcast -n $REMOTE -a com.android.music.musicservicecommand --es command next` | `remote-command` | none |
| `input keyevent 87` | `mediasession` | real `downTime` |

`input keyevent` goes through the framework's media-button routing to whichever media session wins —
which is exactly the stage a real steering-wheel press reaches on the reporter's unit, and why R10
uses it.

Key codes: **87** MEDIA_NEXT, **88** MEDIA_PREVIOUS, **85** MEDIA_PLAY_PAUSE. Non-media for R6:
**19/20/21/22** D-pad, **66** Enter (remapped to DPAD_CENTER, so it logs as `AA=23`), **4** Back.

**Check the keymap is empty before the round.** `sendKey`'s first step is a physical→logical remap
from the `key-codes` set in `settings.xml`; if a previous round learned anything there, an injected
code can arrive as a different logical code and every count below goes wrong. It should be absent:

```bash
adb shell run-as $PKG cat shared_prefs/settings.xml | grep -o 'key-codes[^/]*'
```

If it is present, record its contents in Setup notes and read the `AA=` values in the log rather than
assuming they match what was injected.

---

## 6. The lines that decide every run

Copied verbatim from `CommManager.kt` on `f9b1ca73` and verified with `grep -F`.

| Meaning | Level | Line |
|---|---|---|
| forwarded to Android Auto | I | `CommManager: TX Key -> AA=87 (isPress=true) src=carkey` |
| held back by the routing gate | **V** | `CommManager: Not sending media key 87 to Android Auto (routing=AUTO, src=carkey)` |
| dropped by de-duplication | **V** | `CommManager: Dropping key 87 (isPress=true, src=key-broadcast) - duplicate 12ms after the last press, within 600ms` |
| stuck press closed out | I | `CommManager: Key 87 was still held from an earlier press with no release - releasing it first` |
| what the A2DP probe read | I | `CommManager: Bluetooth media link state for key routing: true` |
| broadcast landed | I | `CarKeyReceiver: Handling intent action: com.microntek.irkeyDown` |

`routing=` prints the enum name: `ALWAYS`, `AUTO` or `NEVER`. The probe prints `true`, `false` or
`null`. Other `dropReason` texts you may see, all after `Dropping key … - `:

- `another delivery of the same key event`
- `the key is already held down`
- `its press was dropped`
- `no press is outstanding`

Standing counts for every run:

```bash
grep -c 'TX Key ->.*isPress=true'  rN.txt
grep -c 'TX Key ->.*isPress=false' rN.txt      # must equal the above
grep -c 'Not sending media key'    rN.txt
grep -c 'Dropping key'             rN.txt
grep -n  'Bluetooth media link state' rN.txt
```

---

## 7. Runs

Setup common to R2–R11: session up (Native AA, the rig's only transport), projection in the
foreground, log keys from §3 written, app relaunched, capture started before the launch.

### R1 — what this rig can actually see

No verdict on the branch. This establishes which later runs are possible; report the four answers.

```bash
adb shell dumpsys bluetooth_manager | grep -iE "a2dp|avrcp|Connected|Active Device"
adb shell dumpsys media_session | grep -iE "package|active|Bluetooth"
adb shell cmd media_session dispatch next            # does the command exist on Android 14 here?
adb shell am broadcast -n $CARKEY -a com.microntek.irkeyDown --ei keyCode 87
```

Report: (a) is the A2DP sink link up; (b) **does this unit publish a Bluetooth media session of its
own** — the second consumer, and R10's precondition; (c) does `cmd media_session dispatch` exist;
(d) does the Microntek broadcast produce `CarKeyReceiver: Handling intent action`.

### R2 — ALWAYS forwards, which is also the no-change check

`media-key-routing=0`. Press 87, 88 and 85 once each, ~1 s apart.

**PASS:** each of the three produces `TX Key -> AA=<code> (isPress=true) … src=carkey` and a matching
`isPress=false`. Zero `Not sending media key` lines.
**FAIL:** any `Not sending media key`, or presses ≠ releases.

This is the run that proves existing installs are unaffected.

### R3 — NEVER holds all three back

`media-key-routing=2`. Same three presses.

**PASS:** three `Not sending media key <code> to Android Auto (routing=NEVER, src=…)` lines and
**zero** `TX Key -> AA=87/88/85`.
**FAIL:** any media key forwarded.

Note `routing=NEVER` needs no Bluetooth state at all, so this run is not gated.

### R4 — AUTO with an A2DP link up · **the point of the round**

Gated: confirm the link immediately before, per §3. `media-key-routing=1`. Same three presses.

**PASS:** `Bluetooth media link state for key routing: true`, then three
`Not sending media key … (routing=AUTO, …)` and zero `TX Key` for 87/88/85.
**FAIL:** the probe reads `true` and a media key is still forwarded.
**INCONCLUSIVE:** the link is down — the code path was never reached.

If the probe reads `null`, record it and treat the run as **FAIL of the probe, not of the policy**:
`null` deliberately forwards, so the keys going through is correct behaviour for that reading, but it
means AUTO cannot work on this hardware and that is the finding.

### R5 — AUTO with no A2DP link forwards

`media-key-routing=1`, session already established, then drop the link by switching the **phone's**
Bluetooth off (§7a — the head unit's own adapter is not switchable and self-reverts). Wait for
`A2dpSinkStateMachine` to reach `STATE_DISCONNECTED`, then ≥3 s more for the probe cache, then press.

**PASS:** `Bluetooth media link state for key routing: false` and all three keys forwarded.
**FAIL:** keys held back with the probe reading `false`.

This is the positive control for R4 and it needs no second build: the same APK, one setting
unchanged, and only the Bluetooth state differs.

### R6 — non-media keys are never held back · **as important as R4**

For each of `media-key-routing` = 0, 1, 2: press 19, 20, 21, 22, 66, 4, ~1 s apart. Six presses,
three modes, eighteen in all.

**PASS:** eighteen forwarded presses with matching releases, and **zero** `Not sending media key`
lines in any mode. Note that 66 arrives as `AA=23`.
**FAIL:** any non-media key held back in any mode.

Run the AUTO third with the link up if you have one; with it down the run still has value but proves
less.

### R7 — the de-duplication rewrite

`media-key-routing=0` throughout, so nothing else can suppress. Four parts, one capture each is fine.

**R7a — one press, two deliveries.** One `irkeyDown 87` + `irkeyUp 87`.
**PASS:** two `sendKey` deliveries visible (`src=carkey` and `src=key-broadcast`), of which exactly
one press is forwarded and one is dropped with `duplicate <N>ms after the last press, within 600ms`;
presses == releases. If only one delivery appears, projection was not resumed (§5) — fix and re-run.

**R7b — no unmatched release.** Over the whole R7 capture, `grep 'TX Key -> AA=87'` must alternate
`isPress=true` / `isPress=false` strictly.
**FAIL:** any `false` not preceded by a `true`. This is the exact defect R9 shows on the baseline.

**R7c — stuck-key recovery.** `irkeyDown 87` and **no** `irkeyUp`. Wait 3 s (the threshold is 2 s).
Then `irkeyDown 87` followed by `irkeyUp 87`.
**PASS:** `Key 87 was still held from an earlier press with no release - releasing it first`, then a
forwarded press. **FAIL:** the second press is dropped with `the key is already held down`.

**R7d — press and hold still works.** `irkeyDown 87`, wait 1 s, `irkeyUp 87`.
**PASS:** exactly one forwarded press and one release, and **no** `releasing it first` line.

### R8 — two deliberate presses inside 600 ms

`media-key-routing=0`. `input keyevent 87` twice, back to back with no sleep.

**PASS:** two forwarded press/release pairs. These arrive through the MediaSession with real, distinct
`downTime`s, so identity — not the window — decides, and both must get through.
**FAIL:** the second dropped with `duplicate <N>ms after the last press`. That is the old behaviour.

If the rig's shell round-trip makes the two presses land more than 600 ms apart the run proves
nothing either way; report the measured gap and mark it INCONCLUSIVE.

### R9 — baseline A/B for the unmatched release · optional

**Only run this if a baseline APK is cheap to produce.** Build `origin/main` @ `64f07228`, install
with `adb install -r`, confirm the md5 that is live, and repeat **R7a** exactly.

**Expected on the baseline:** `DOWN, UP, UP` for one press — a release on the wire with no press. That
is the defect, on this hardware, and it makes R7b a demonstration rather than an assertion. Restore
the candidate APK afterwards and re-confirm the md5.

### R10 — the double skip, as far as this rig allows

Gated on R1(b): if this unit publishes no Bluetooth media session, mark **UNTESTABLE** and move on.

This is **not** a reproduction. There is no OEM key handler here fanning one press out, so the fan-out
is forged by firing both consumers from one shell line. Start playback on the phone first, and capture
the phone's current track before and after each press:

```bash
adb -s <phone> shell dumpsys media_session | grep -iE "metadata|description|title" | head -5
```

For each of `media-key-routing` = 0, 1 (link up), 2 — one press per mode, both consumers:

```bash
adb shell am broadcast -n $CARKEY -a com.microntek.irkeyDown --ei keyCode 87 &
adb shell cmd media_session dispatch next
wait
```

**Measurement, not a pass/fail on the branch:** how many tracks the phone advanced in each mode.
Expect 2 with ALWAYS and 1 with AUTO/NEVER. Give the track titles, not an adjective.

Also worth one line on its own, with the link up and mode ALWAYS: `adb shell input keyevent 87`. If
the phone skips a track and there is **no `TX Key` line at all**, the unit's own Bluetooth media
session took the key — that is the second consumer, made visible, and it is the single most useful
observation this round can produce.

Per §0: the track count is observational. Report it as a measurement and do not let any verdict rest
on it.

### R11 — the setting itself

Three cheap checks, no session needed for the first two.

- **Absent key reads ALWAYS.** Delete `media-key-routing` entirely, relaunch, press 87 with a session
  up. **PASS:** forwarded, no `Not sending`.
- **Persists across a force-stop.** Write `2`, relaunch, read the file back, press 87.
  **PASS:** the key still reads `2` and the press is held back with `routing=NEVER`.
- **Out-of-range value falls back.** Write `7`, relaunch, press 87. **PASS:** forwarded — `fromInt`
  maps anything unknown to ALWAYS. **FAIL:** a crash, or the key held back.

---

## 8. Do not re-run

- Anything about the poke, the P2P group, audio focus or the hotspot. Nothing on this branch touches
  them; if a capture shows a change there, it is a rig variable, and it belongs in "Anything the brief
  did not ask about" rather than in a verdict.
- The pure-policy transition tables. `KeyDebouncePolicyTest` (13) and `MediaKeyRoutingPolicyTest` (6)
  cover them on the JVM, and R0 runs them. In particular, **the same `KeyEvent` arriving twice with an
  identical `downTime` cannot be forged from adb** — the OEM broadcasts carry no `KeyEvent` at all.
  Do not invent a substitute run for it; it is covered where it can be.

---

## 9. Report back

Three numbers decide whether this ships:

1. **R6** — the count of `Not sending media key` lines for non-media keys across all three modes. It
   must be **0**. Anything else and the setting is disabling controls it was promised not to touch.
2. **R4/R5** — what `Bluetooth media link state for key routing:` actually read on this hardware, in
   both link states. If it never reads anything but `null`, AUTO is decorative on units like this one
   and NEVER is the only working answer, which changes what we tell users.
3. **R7b** — presses versus releases forwarded, as two counts. They must be equal.

And, if R10 ran: the number of tracks the phone advanced per mode.
