# Standing template — how a round is briefed, run and reported

This is the reusable half of the channel. A round's brief carries only what is specific to its
branch; everything below applies to every round and is not restated in briefs.

The rules here are not style preferences. Each one exists because ignoring it once produced a false
result that cost hours to unwind.

---

## House rules — the short version

Six standing rules. Everything after this section is detail on how to follow them.

1. **Use the rig's existing scripts.** `hur-wifi-test-scripts/` already has `build_hur.sh`,
   `run_unit_tests.sh` and others for building, installing and driving the app. Look there first,
   use what fits, and only write a new script when nothing does — then leave it in that folder for
   the next round. §5.
2. **Script it, don't drive it.** Prefer adb and the app's automation surface — deep links, exported
   actions, media keys — over touching the screen. §0, §3.
3. **Settings go in `shared_prefs/settings.xml` with the app stopped.** Never change a setting
   through the UI, and never scroll the settings list with adb. §1, §3.
4. **Run the whole round unattended.** Changing between runs is yours. Escalate only for a failed
   build gate, a fork the brief did not cover, something destructive, or a broken rig. §3a.
5. **Capture before you launch, with `stdbuf -oL`.** §2.
6. **Report in the fixed format, and put every deviation in Setup notes.** §7.

Read **§7a, known rig quirks**, before planning a round. Several of them will change how a run has
to be set up, and two of them make the obvious method silently wrong.

---

## 0. The governing rule: script it, don't drive it

Before anything else, check whether `hur-wifi-test-scripts/` already has a script for the step in
front of you (§5). Existing script first, adb second, the app's UI never.

**Never navigate the app's UI to set up a run.** Launching the app starts `AapService`, which forms a
P2P group or starts the resolver and opens Bluetooth listeners — so the run has already begun before
you finished configuring it. Write the preferences with the app stopped and every run starts from
exactly the state you specified.

The same applies to acting *during* a run. The app exposes a full automation surface (§3); use it
rather than tapping. A tap is unrecorded, unrepeatable and mistimed. An `am start` is in the log with
a timestamp.

Concretely, and without exception: **every setting change goes into `shared_prefs/settings.xml` with
the app stopped, and the settings list is never scrolled with adb.**

Where something genuinely cannot be automated — the picture appearing, sound coming out of a
speaker, a toast being visible — say so explicitly in the results and describe what you observed.
Those are real evidence; a claim that a scripted step "should have" worked is not.

A brief should never make a **verdict** depend on a human being present. If a run's PASS condition
can only be checked by ear or by eye, the brief has a bug: say so in Setup notes and give the
verdict on the scriptable evidence, noting the sensory part separately as unconfirmed.

---

## 1. Preferences: reading and writing

Debug builds are debuggable, so `run-as` works without root. **The app must be stopped** — a running
process holds the prefs in memory and overwrites the file on exit.

```bash
PKG=com.andrerinas.headunitrevived
adb shell am force-stop $PKG

# ALWAYS back up first; recovery is then trivial
adb shell run-as $PKG cat shared_prefs/settings.xml > settings-backup.xml
```

### The write template

```bash
adb shell run-as $PKG sh -c '
  f=shared_prefs/settings.xml
  sed -i -E "s#<[a-z]+ name=\"KEY\"[^>]*/>##g" $f
  sed -i -E "s#<string name=\"KEY\">[^<]*</string>##g" $f
  sed -i "s|</map>|<int name=\"KEY\" value=\"V\" /></map>|" $f
'
adb shell run-as $PKG cat shared_prefs/settings.xml     # verify before launching, every time
```

Three things about that template are deliberate:

- **The element goes on the same line as `</map>`.** toybox `sed` is inconsistent about `\n` in a
  replacement, and SharedPreferences does not care about formatting.
- **The removal is element-scoped, never line-scoped.** Every insert lands immediately before
  `</map>`, so keys accumulate on that one physical line; a `sed -i '/name="X"/d'` would delete the
  whole line — `</map>` and every sibling key with it. That silently truncated the settings file
  during an early round.
- **Both removal forms are shown because the type varies by key.** Running the wrong one is
  harmless, so run both.

To **clear** an override rather than set it, run only the delete half. An absent key reads as its
default; a blank string does not always.

### Reading one key back

```bash
adb shell run-as $PKG cat shared_prefs/settings.xml | grep -o 'KEY[^/]*'
```

### Element types

| Type | Element |
|---|---|
| int | `<int name="KEY" value="0" />` |
| boolean | `<boolean name="KEY" value="true" />` |
| string | `<string name="KEY">VALUE</string>` |

**A log call's priority does not tell you the level it needs.** The two are set independently: the
line can be `AppLog.d(...)` and still be wrapped in `if (AppLog.LOG_VERBOSE)`, in which case only
`log-level=0` produces it. `RECV:` is exactly that — `AapMessageIncoming.kt:50` guards an `AppLog.d`
call with `LOG_VERBOSE`, and the periodic-link-stall round 1 lost a whole run to a brief that read
the `d` and wrote `log-level=1`. Before a brief quotes a level, grep for the **guard** around the
call, not the call; and check each line the round depends on separately, because siblings in one
file can differ.

`log-level` enum order: **VERBOSE 0, DEBUG 1, INFO 2, WARNING 3, ERROR 4, SILENT 5.** Briefs state
which level they need. Prefer the highest level that still carries the round's lines — on units whose
driver stack floods logcat, VERBOSE costs you evidence by wrapping the ring buffer rather than buying
any.

---

## 2. Capture

```bash
adb shell am force-stop com.andrerinas.headunitrevived
adb logcat -c
stdbuf -oL adb logcat -v time > rN.txt &        # started BEFORE the launch, always
```

**`stdbuf -oL` is not optional.** Redirected to a file, the adb client fully-buffers stdout and
`SIGTERM` does not flush it; that cost two discarded runs in an early round. Verify afterwards by
comparing the capture's last timestamp against the kill wall-clock — they should agree within a
couple of seconds.

**Capture everything, not a tag filter.** The framework lines around ours are usually what decide a
verdict.

Keep the app's own exported `HUR_Log_*.txt` alongside the logcat capture when the round asks for it.

---

## 3. Driving the app: automation surface

All of these are exported and work from `adb shell` with no root and no tapping.

### Lifecycle

```bash
PKG=com.andrerinas.headunitrevived
MAIN=$PKG/com.andrerinas.openheadunit.main.MainActivity

adb shell am start -n $MAIN                                          # launch
adb shell am force-stop $PKG                                         # hard stop
```

### Connect / disconnect / exit — deep links

```bash
adb shell am start -a android.intent.action.VIEW -d "headunit://connect"           # auto (USB check)
adb shell am start -a android.intent.action.VIEW -d "headunit://connect?ip=1.2.3.4"  # direct TCP
adb shell am start -a android.intent.action.VIEW -d "headunit://disconnect"        # user disconnect
adb shell am start -a android.intent.action.VIEW -d "headunit://exit"              # stop the service
adb shell am start -a android.intent.action.VIEW -d "headunit://nightmode?state=on"
```

### The same things as explicit actions

```bash
adb shell am start -a com.andrerinas.openheadunit.ACTION_CONNECT
adb shell am start -a com.andrerinas.openheadunit.ACTION_DISCONNECT
adb shell am start -a com.andrerinas.openheadunit.ACTION_START_SELF_MODE
adb shell am start -a com.andrerinas.openheadunit.ACTION_STOP_SERVICE
adb shell am start -a com.andrerinas.openheadunit.ACTION_SET_NIGHT_MODE --es state on
```

`headunit://disconnect` is the scripted equivalent of the user pressing Exit, which is what the
`isUserExit` code paths are gated on. Prefer it over any UI route.

### Media transport — drives the *phone's* player through the head unit

```bash
adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE     # 85
adb shell input keyevent KEYCODE_MEDIA_NEXT           # 87
adb shell input keyevent KEYCODE_MEDIA_PREVIOUS       # 88
adb shell input keyevent KEYCODE_MEDIA_PAUSE          # 127
adb shell input keyevent KEYCODE_MEDIA_PLAY           # 126
```

The app holds an active `MediaSession` and relays these to the phone over AAP, so they start and skip
tracks without touching the phone. This is the way to run "play two short tracks in a row" or to end
a track at a chosen moment.

### Settings screen, deep-linked

```bash
adb shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0
```

### Settings are changed in `settings.xml`, never in the UI

**A setting is never changed by driving the app's UI, and the settings list is never scrolled with
adb.** Write the key (§1), verify it by reading the file back, and prove it took effect from the log
lines the behaviour produces. That is the whole check: a value in the file plus the behaviour it
causes is stronger evidence than a screenshot of a control, and it costs no taps, no swipes and no
guesses about where a row sits on this screen size.

Scripted scrolling in particular is banned. Row positions differ by screen size, density and which
options are visible, so a swipe that works once silently lands somewhere else next round and the
dump then "proves" a control is absent when it is merely off-screen.

If a brief needs confirmation that a control is on screen and correctly labelled — because a
regression there would be invisible in the log — it will say so and ask for a **screenshot**:

```bash
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```

Open the screen with a deep link rather than navigating to it, take one shot, and attach it. Do not
scroll to find things; if it is not on the first screen, say so and move on.

### Radios and state

```bash
# head unit
adb shell svc wifi enable | disable
adb shell svc bluetooth enable | disable
adb shell cmd wifi start-softap OHU-TEST wpa2 testtest1234 -b 5     # -b 5 is not optional
adb shell cmd wifi stop-softap
adb shell dumpsys wifi | grep -i SoftApInfo                          # the band, authoritatively
adb shell dumpsys bluetooth_manager | grep -iE "a2dp|avrcp|Profile"
adb shell ip -4 addr

# phone
adb -s <phone> shell cmd connectivity airplane-mode enable | disable
adb -s <phone> shell svc wifi enable
adb -s <phone> shell svc bluetooth enable
```

Coming out of airplane mode, **do not trust the toggle to restore the radios** — on at least one MIUI
phone `airplane-mode disable` clears the flag without bringing WiFi or Bluetooth back, producing a
run that fails for no reason visible in the head unit's log. Re-enable and verify explicitly.

---

## 3a. Run the whole round unattended

**Do not check in between runs.** A brief specifies every run's setup as commands you can paste;
work through them in order, record each verdict, and keep going. Moving from one run to the next,
restoring state, re-writing settings, deciding that a run is INCONCLUSIVE because its gate failed —
all of that is yours to do. A round that stops after run 2 to ask what to do next has cost a day for
nothing.

Between runs, reset to a known state rather than assuming the last run left one:

```bash
PKG=com.andrerinas.headunitrevived
adb shell am start -a android.intent.action.VIEW -d "headunit://exit"   # stop the service cleanly
sleep 3
adb shell am force-stop $PKG
adb shell run-as $PKG cp settings-backup.xml shared_prefs/settings.xml  # back to the round's baseline
# then write only the keys the next run names, and re-verify
```

Escalate — stop and ask — only in these cases:

- **the build or unit-test gate fails**, and the brief says a failure there stops the round;
- **a run's result would change what the remaining runs should be**, and the brief did not say which
  way to go;
- **something looks destructive or irreversible** on the rig, or outside what the brief describes;
- **the rig is broken** — it will not boot, adb is gone, the app will not install.

Everything else is a result, not a question. A run whose setup cannot be performed as written is
**UNTESTABLE**; a run that executes but cannot produce the signal on this hardware is
**INCONCLUSIVE**. Record which, say why in Setup notes, and carry on to the next run.

If a brief gives a stop condition ("if three passes are all inconclusive, stop"), honour it and move
on to the next run — that is the brief answering the question in advance, not an invitation to ask.

---

## 4. Clean-run protocol — every run, no exceptions

1. **Phone: airplane mode ON.** Wait for its Bluetooth and WiFi to actually be down.
2. **Head unit: force-stop, clear logcat, start the capture** (§2) — before anything else.
3. **Write the run's settings** (app stopped) and **set the radio state**. Verify both.
4. **Launch and let it settle**, roughly 15-20 s.
5. **Phone: airplane mode OFF.** From here the phone drives; do not touch either device.
6. Give it **90 s** before calling a run failed.
7. Stop the capture; keep it with the exported `HUR_Log_*.txt`.

### Discard rules

A capture containing any of these is contaminated — discard and re-run:

- `MATCH! Starting AapService` — a self-inflicted wake-up
- a second `createGroup SUCCESS` where the run should have formed one group
- a bump in the P2P interface index: `grep -o "p2p-wlan0-[0-9]*" rN.txt | sort -u`
- `AapRead: Magic Garbage detected in header`
- any unintended reconnect, or a second `Handshake: SSL handshake complete`

---

## 5. Build and install — use the rig's own scripts

**`hur-wifi-test-scripts/` is the first place to look for any step, not the last.** It already holds
the scripts this rig is set up around — `build_hur` for building, and others for installing and
driving the app. They encode the JDK path, the flavour, the APK location and whatever else this
machine needs, and every round that re-derived those by hand got one of them wrong.

Start each round by taking an inventory, and record it in Setup notes:

```bash
ls -la hur-wifi-test-scripts/
head -20 hur-wifi-test-scripts/*.sh | head -100     # what each one does and what it takes
```

Then map the brief's steps onto them. A brief states what a step must *achieve* and usually shows the
raw command as the contract; if a script does that thing, run the script instead and say which one.
The raw command in a brief is there so you can tell whether a script is doing the right thing — not
because it is the preferred route.

**Only write a new script when nothing existing fits.** When you do, put it in
`hur-wifi-test-scripts/` in the same style as its neighbours, name it after what it does, and list it
in Setup notes so the next round inherits it rather than reinventing it.

Two of them are worth knowing about before you reach for either:

- **`set_hu_pref.sh` relaunches the app on every call**, so stacking it to set several keys starts
  `AapService` once per key on a partially-written settings set. Safe for one key, wrong for a run
  that needs a group of them applied together.
- **`set_hu_prefs.sh`** (round 9) is the multi-key sibling: writes or deletes several keys in one
  pass with a single relaunch at the end. Use it whenever a run sets more than one key.

The rig also carries `code-researchs/hur-wifi-test-scripts-inventory.md`, a standing writeup of that
whole directory. It lives on the rig rather than on this branch — read it there instead of
re-deriving the inventory, and update it when you add a script.

Whatever route you take, these are the invariants a build-and-install step has to satisfy:

- both APKs built from known SHAs, and their **md5s recorded and different**;
- **`adb install -r`** — never uninstall/reinstall. A fresh install re-runs the setup wizard and
  rewrites resolution, DPI and video codec, three variables you would then be testing by accident;
- **confirm which APK is actually live** before trusting a single run:

```bash
PKG=com.andrerinas.headunitrevived
adb shell md5sum $(adb shell pm path $PKG | cut -d: -f2 | tr -d '\r')
```

Only rounds with an A/B need the baseline built at all; a brief will say so. There is no `main`
behaviour to compare against for code that does not exist there.

---

## 6. Verdicts

Exactly four, and the last two are results, not failures to apologise for:

| Verdict | Meaning |
|---|---|
| **PASS** | Every condition the brief listed for that run was met. |
| **FAIL** | A stated condition was not met. Keep the full capture, not an excerpt. |
| **INCONCLUSIVE** | The run executed but this hardware cannot produce the signal — the code path was never reached. |
| **UNTESTABLE** | The run cannot be set up on this rig at all, and no amount of retrying will change that. |

Do not invent a substitute run to turn an INCONCLUSIVE into something. An honest INCONCLUSIVE moves
the coverage question onto the JVM tests, which is where a brief will usually already have put it.

If a brief gives a stop condition ("if three passes are all inconclusive, stop"), honour it.

---

## 7. Reporting back

One file, `<topic>-round<N>-results.md`, committed to this branch alongside the brief.

```markdown
# <topic> — round <N> results

**Candidate:** <branch> @ <sha>       **Baseline:** origin/main @ <sha>
**APK md5:** <candidate> / <baseline>
**Unit:** <chipset, Android version, screen, anything load-bearing>
**Date:** <yyyy-mm-dd>

## Setup notes

Every deviation from the brief and the protocol, and every error found in either. Wrong settings
key, log string that does not match, adb command that does not exist on this device, step that could
not be performed as written.

Also: which `hur-wifi-test-scripts/` scripts were used for which step, and any script added or
changed this round.

## R<id> — <name>

**PASS** | **FAIL** | **INCONCLUSIVE** | **UNTESTABLE**

- Settings written: <the keys and values>
- Radio state and how it was set:
- Discard-rule check: clean / re-run N times
- Decisive log lines, quoted with timestamps:
- Measurements the brief asked for, as numbers:

<one short paragraph on anything the verdict does not capture>

## Anything the brief did not ask about

Things noticed in passing. This section has produced more real findings than some rounds' runs.
```

**The Setup notes section is not politeness.** The `stdbuf -oL` rule, the `log-level` correction and
several corrected log strings in this document all came from a previous round's setup notes, and each
would otherwise have cost the next round the same hours.

For any FAIL: attach the full capture, not an excerpt. For any measurement: give the number, not an
adjective — "5180 MHz", not "5 GHz"; "waited 3400 ms", not "quickly".

---

## 7a. Known rig quirks

Measured on the UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14, `head-unit-make: Royal Enfield`).
This channel serves one rig, so these belong here rather than in each brief. A brief will still say
when a quirk changes a run.

- **The head unit's Bluetooth re-enables itself.** `adb shell svc bluetooth disable` is silently
  reverted about 14 s later — `AdapterState` shows `OffState → … → OnState` via `USER_TURN_ON`,
  reproduced twice with no other adb activity in between. It is not the app: the only
  `ACTION_REQUEST_ENABLE` in the source is behind the Bluetooth device-picker in AutoStart settings,
  needs a tap on the system consent dialog, and cannot fire during a run. Treat head-unit Bluetooth
  as **not switchable off** on this rig.
- **To drop the A2DP link, switch off the *phone's* Bluetooth instead.** It takes
  `A2dpSinkStateMachine` to `STATE_DISCONNECTED` while leaving an already-established Native AA TCP
  session on port 5288 untouched — the handshake's own RFCOMM socket is already closed by the time
  the WiFi handoff completes. Verify with `netstat` that the session survived.
- **The A2DP link comes and goes on its own schedule, and nothing visible controls it.** Three rounds,
  three different behaviours: round 6 it reconnected the instant the phone's Bluetooth came back;
  round 7 two cycles including a full 8 s off never returned `A2dpSinkStateMachine` to `Connected`;
  round 8 it refused to come up **at all** for ~15 minutes across the prescribed method and several
  substitutes (adapter enable, full disable/enable cycle, forcing playback to provoke an on-demand
  profile connect), with `A2dpSinkService` stuck at `Active Device = null` — then came up
  unprompted during unrelated setup, with no new technique applied.

  So: **confirm the link immediately before every link-dependent run** and never infer it from the
  last one. If it is down, do not spend the round trying to force it — no technique has ever been
  shown to work. Run the link-free runs, then re-check. Runs that never got a link are
  **INCONCLUSIVE**: rig flakiness, not a finding about the branch.

  ```bash
  adb shell dumpsys bluetooth_manager | grep -iE "a2dp|avrcp|Connected|Active Device"
  ```
- **A live link survives head-unit-side restarts, so reuse one once you have it.** Round 8 ran four
  consecutive `force-stop` + settings-write + relaunch cycles on one underlying Bluetooth connection
  with A2DP intact throughout, verified before and after each. The risk to the link is touching the
  **phone's** radios, not the head unit's app. Once a link-dependent session is live, sequence every
  run that needs it back-to-back with head-unit-only resets and leave the phone alone.
- **A force-stopped app's manifest receivers do not fire until it is explicitly relaunched.** §1
  requires the app stopped to write settings; after that, waiting for the phone's Bluetooth to
  reconnect and trigger `AutoStartReceiver` does **nothing**. Measured in the periodic-link-stall
  round 1: two full minutes with the phone's Bluetooth confirmed `state: ON` and idle, and
  `dumpsys activity services` showing no `AapService` the whole time; the session formed
  immediately once `MainActivity` was launched explicitly. Never plan a run that skips the explicit
  launch.
- **Bring the head unit up before the phone, always.** Restoring the phone's radios first lets its
  own Bluetooth reconnect race an explicit `am start` a few seconds later, and the result is two
  sessions, two SSL handshakes and two `p2p-wlan0-N` interfaces — a genuine discard-rule hit. §4's
  order already says this; the periodic-link-stall round 1 lost a capture proving it. Launch the head
  unit app while the phone is still down, let the group settle ~15 s, then bring the phone back.
- **This head unit's `WifiScanner` service does not work, by any route.** `cmd wifi start-scan`
  returns exit=0 and `list-scan-results` says "No scan results", with
  `E/WifiScanRequestProxy: Failed to retrieve wifiscanner` right after `I/WifiService: startScan`;
  the `WIFI_SETTINGS` screen hits the same broken backend. Reproduced twice. Any run needing a
  head-unit-side scan is UNTESTABLE here — do not spend time on it. The phone's own
  `cmd wifi start-scan` works cleanly and returns real results.
- **`cmd connectivity airplane-mode enable|disable` *does* work on this phone**, unlike the
  `am broadcast` route in the bullet below. It reliably drops Bluetooth and usually WiFi, but coming
  back it restores **neither** radio reliably. `svc wifi enable` was already documented here; the
  media-gap round 2 found the same on the other radio, where `disable` brought WiFi back and left
  Bluetooth off, needing an explicit `svc bluetooth enable`. Run both nudges after every
  `airplane-mode disable` and verify both, rather than treating either as automatic. Native AA needs
  Bluetooth for the handshake, so a missed nudge here costs the whole session, not one run.
- **Airplane mode cannot be toggled from adb on this phone.**
  `am broadcast -a android.intent.action.AIRPLANE_MODE` is refused with
  `SecurityException: Permission Denial: not allowed to send broadcast … uid=2000`, and
  `settings put global airplane_mode_on` alone changes the flag without moving the radios. §4's
  clean-run protocol therefore cannot be followed literally here — use the phone's Bluetooth adapter
  (`svc bluetooth enable|disable`) as the lever for link-state changes, and say so in Setup notes.
- **The phone's Bluetooth self-reverts too, sometimes.** Once in round 8, `svc bluetooth disable` on
  the phone was back to `state: ON` about 45 s later with no adb command in between; a second attempt
  minutes later held. Distinct from the head unit's ~14 s revert above and not yet characterised.
  Verify a radio is actually off rather than assuming the command took.
- **Native AA cannot connect with the phone's Bluetooth off.** The handshake itself needs RFCOMM, so
  a run that requires "no Bluetooth link *at connect time*" is impossible on this rig — round 8
  confirmed no session after 50 s versus 10-50 s on every successful attempt. Dropping Bluetooth
  after the session is established gets `bluetoothMedia=false`, but the deep links
  (`headunit://disconnect` then `headunit://connect`) do **not** produce a fresh SSL handshake, so
  connect-time decisions do not re-run. Briefs should route that coverage to a JVM test instead of
  asking for it on hardware.
- **No USB accessory path.** `dumpsys usb` reports device mode only (`host_connected=false`); the
  port is the adb link to the PC. There is also no shared regular WiFi both devices can join. Native
  AA wireless (`wifi-connection-mode=3`) is the only usable transport, so treat any brief that says
  "connect over USB" as needing a substitute.
- **Media keys alone do not open a fresh audio channel.** Focus is re-evaluated when the channel
  opens, not per track, so a run needing a fresh decision must restart the media app on the phone:
  ```bash
  adb -s <phone> shell am force-stop com.spotify.music
  adb -s <phone> shell monkey -p com.spotify.music -c android.intent.category.LAUNCHER 1
  ```
- **Always grep a capture with `-a`.** Logs come back long enough that `file(1)` calls them "ASCII
  text, with very long lines", and `grep` then auto-detects one as **binary**: `grep -c` prints
  *nothing at all* and exits 1, rather than printing `0`. Every count of an absent pattern and every
  count of a present one look identical from the shell — a refused count reads as "pattern not
  found". Round 2 of the video-pipeline stack lost real time to this on one capture before noticing
  and redoing the round's greps. So `grep -ac`, `grep -a -o`, `grep -aP`, without exception, and if a
  count comes back empty rather than `0`, that is the bug and not the answer.

- **Inline `sh -c` over adb is unreliable, for `sed` and `cp` alike** — the quoting does not survive.
  Confirmed again in round 8: `run-as $PKG sh -c 'cp …'` fails with `cp: Needs 1 argument`, twice out
  of two attempts, while the pushed-script form worked first time, twice out of two. Push a small
  script and run it on-device, always:
  ```bash
  adb push set_pref.sh /data/local/tmp/ && adb shell run-as $PKG sh /data/local/tmp/set_pref.sh
  ```
  `hur-wifi-test-scripts/` has a generalised `set_pref.sh <key> <type> <value>` from round 8 —
  use it rather than writing a new one-key script per run.
- **Only nav-graph fragments are deep-linkable; settings *categories* are not.**
  `SettingsActivity`'s `extra_destination` calls `navController.navigate(id)`, so it can only open a
  whole sub-screen (dark mode, keymap, and so on). Audio, Graphics, Input and the rest are categories
  inside the one long `settingsFragment` list, several screens below where any deep link lands.

  This makes "deep-link and screenshot without scrolling" impossible for most controls — round 8's R9
  was UNTESTABLE for exactly that reason, and the fault was the brief's. If a control's on-screen
  presence genuinely has to be checked, use a **bounded search**: swipe, `uiautomator dump`, grep for
  the label, repeat up to a stated maximum, and fail if it is not found by the end. That is not what
  the no-scroll rule bans — the ban is on a fixed number of blind swipes followed by an assumption.
  Better still, verify list membership and ordering from `SettingsFragment.kt` and spend the run
  elsewhere.
- **"Bluetooth is on" and "the two devices are paired" are different facts, and only the second one
  matters.** Round 9 opened with the phone and head unit completely unbonded — each side's
  bonded-device list held only an unrelated speaker — traced to a bond removal and a failed re-pair
  the previous day (`bond_state_changed BOND_STATE_BONDING → BOND_STATE_NONE` within 1 s). Native AA
  can do nothing without the bond, so the whole round was blocked until it was re-paired **by hand**:
  pairing needs a UI confirmation and adb-only tooling cannot restore it. Check both sides before
  assuming the "bring the phone's Bluetooth up once" step above is enough:
  ```bash
  adb -s <hu>    shell dumpsys bluetooth_manager | grep -iA 20 "Bonded devices"
  adb -s <phone> shell dumpsys bluetooth_manager | grep -iA 20 "Bonded devices"
  ```
- **The phone's own reconnect beats our poke, so the poke is normally never exercised.** Whenever the
  phone has recently seen the car it completes a full SSL handshake 3-6 s after the head unit's
  listeners open, while `NativeAaHandshakeManager` logs "handshake already in flight" and never calls
  `socket.connect()` at all. Round 9 ruled out the obvious levers: force-stopping
  `com.google.android.projection.gearhead` and deleting the head unit's persistent group
  (`cmd wifip2p delete-saved-group`) changed nothing, and the phone's own P2P cache cannot be
  inspected without root (`cmd wifip2p` → `SecurityException: Uid 2000 does not have access`).

  Two recipes, depending on what you need:

  - **One poke round, then a normal session** — phone Bluetooth **off**, launch the app so its RFCOMM
    listeners and P2P group come up while the phone is unreachable, wait ~8 s, phone Bluetooth back
    on. Round 9 used this for R2 and R5.
  - **The poke running indefinitely** — phone **Wi-Fi off**, phone **Bluetooth on**. No session can
    form, so the loop never reaches its `Stopping poke retry loop (… session=true)` exit and fires
    every ~15 s for as long as you leave it. Use this whenever a run needs many poke rounds.

  A brief that wants to observe poke behaviour must build one of these into its setup; "force-stop
  and relaunch" will not do it.
- **A third option, when the run is actually about `pokeDevice()`'s own guards rather than the
  automatic retry loop's timing: trigger a poke directly, scripted.** The app's manual-poke UI
  (device picker in `HomeFragment`) sends an explicit `Intent` that reaches `pokeDevice()` straight
  away, bypassing `NativeHandoffPolicy` (settling/handshake/session) entirely:
  ```bash
  adb shell am start-foreground-service -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService \
    -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es extra_mac "<device MAC>"
  ```
  Round 11 needed this because on this rig the automatic loop's very first `while`-check routinely
  lands on `handshake=true` before ever reaching `pokeDevice()` — the phone's own AA reconnect (over
  Bluetooth RFCOMM) rides the same fast link that keeps HFP alive, so "launch and watch" alone proves
  nothing about `pokeDevice()`'s guards; a session forms with zero pokes ever attempted. Use this
  whenever a run is specifically about what `pokeDevice()` decides (a guard, a pairing check), not
  about the retry loop's cadence or whether it starts at all.
- **`NativeAaHandshakeManager.start()` requires the head unit's own Bluetooth adapter to be enabled
  at the moment it runs, and silently no-ops (`Bluetooth adapter not available or disabled`, E-level)
  if it isn't** — toggling the adapter off *before* launching the app, even briefly, can prevent the
  whole manager from ever coming up, not just delay it. A run that needs the adapter off must let
  `start()` succeed first (app already running, listeners already open) and only then toggle it,
  never toggle-then-launch. Also: `svc bluetooth enable` completing does not mean
  `BluetoothAdapter.isEnabled` is already `true` a couple of seconds later when `start()` checks it —
  round 11 saw `start()` fail this way even after an explicit `enable` and a 2 s wait before launch.
- **Both poke targets fail on this rig, exactly as on the reporter's — usually, but not always.**
  Round 9 saw HSP-AG and HFP-AG both fail to connect every time (`read failed, socket might closed
  or timeout, read ret: -1`, ~6 s timeout). Round 10 saw the opposite in the same session: 13/13
  HFP-AG pokes connected, and a separate short run saw 3/3 HSP-AG pokes connect. Poke connectivity
  varies between sessions on this rig rather than being a fixed property of either SDP record — do
  not write a run whose PASS *or* FAIL depends on assuming a poke will or won't connect; measure it
  each time.
- **Phone Wi-Fi off does not prevent a session from forming, because Wi-Fi Direct/P2P is not gated
  by the Wi-Fi station-mode toggle on this phone.** Round 10 needed a "no session can ever form"
  precondition to run the poke loop indefinitely and could not get one: across three attempts (adb
  `svc wifi disable` twice, the phone's own UI once), `dumpsys wifi` confirmed Wi-Fi disabled
  throughout, yet a full Android Auto session formed within 35 s every time. One of those attempts
  ran unsupervised for ~68 minutes as a result, undetected until someone checked. A brief that needs
  to suppress session formation for an extended window cannot rely on this lever on this phone; no
  working alternative is known yet.
- **Recovering `HeadsetClientService` after a bad disconnect is not always a single Bluetooth-adapter
  cycle.** Round 9/10's documented recovery (cycle the head unit's own Bluetooth adapter off/on,
  ~14 s self-revert) worked on the first try after round 10's R2 finding. It did not work after R3's
  session-contamination episodes — that needed a *second* head-unit-side cycle plus a phone-side
  Bluetooth off/on before `curState` returned to `Connected`. If one cycle doesn't recover the link,
  try a second, then the phone's own adapter, before concluding something is actually broken.
- **`svc wifi enable` does not reliably bring the station radio back after `svc wifi disable`.** Hit
  in rounds 3 and 4; in round 4 all three runs that toggled WiFi needed help, one of them still
  reading `wifi_on=0` five and a half minutes later, and the app's own internal re-enable attempt
  (for P2P group creation) did not flip it either. The nudges that work are
  `cmd wifi connect-network "<ssid>" wpa2 "<psk>"` or a second manual `svc wifi enable`. Budget for
  it in any run that disables WiFi, verify `settings get global wifi_on` rather than assuming the
  command took, and **never read the resulting stall as a candidate defect** — round 4's R9 lost 5.5
  minutes to it on a path that has no nudge script of its own.
- **`adb reboot` is not an Android shutdown.** `adbd` sets the `sys.powerctl` property and `init`
  reboots directly; `ActivityManager` is never involved, so `ShutdownThread` never runs and
  `ACTION_SHUTDOWN` is never broadcast. Anything testing shutdown behaviour needs
  `svc power reboot` / `svc power shutdown`, which go through `IPowerManager`. Round 4's R8 was
  written with `adb reboot` and could not have worked; the brief was wrong, not the unit.
- It **hard-reboots under sustained multi-core spin load**. No spin loops, no CPU stress, ever.
- **Its driver stack floods logcat**, so the ring buffer wraps past several minutes inside one run.
  Prefer the highest log level that still carries the round's lines.
- It **refuses `setSoftApConfiguration()`** but **can read `getSoftApConfiguration()`**; `wlan2` is
  the working access-point interface, `seth_lte0` the modem bridge. Relevant only to hotspot work.
- **`build_hur.sh` deletes the previous APK before it builds.** Its own
  `rm -f com.andrerinas.headunitrevived_*.apk` clears `apks/` first, so a two-build round that builds
  A then B is left holding only B. **Copy each APK out of `apks/` into a round-specific folder as soon
  as it is built**, before starting the next one. Found in the video-black round 1, which A/B'd two
  tags.
- **Release tags are not monotonic in versionCode, so an A/B across tags can be a downgrade.**
  `v.3.2.4` is versionCode 97 and `v.3.2.3` is 96, so installing the older tag second fails with
  `INSTALL_FAILED_VERSION_DOWNGRADE`. Use **`adb install -r -d`** (`-d` = allow downgrade), which is
  safe between two debuggable builds and preserves `settings.xml` exactly as `-r` alone does — verify
  with `run-as cat` before and after, as the video-black round 1 did. Do **not** reach for
  uninstall/reinstall: §5's reason still holds, a fresh install re-runs the setup wizard and rewrites
  resolution, DPI and video codec.
- **A projection surface is not torn down by `KEYCODE_HOME` on this unit.** Twelve scripted cycles in
  the video-black round 1 — two builds, TEXTURE and GLES, holds from 3 s to 120 s — never produced a
  single `Decoder stopped: surfaceDestroyed`, and video throughput ran uninterrupted at 29-50 fps
  straight through a 120 s hold. Any run whose subject is the surface lifecycle, decoder restart or
  activity backgrounding must **verify the teardown actually happened** before measuring anything
  downstream of it, and must not assume a Home press provides one. Note also that
  `AapBroadcastReceiver` relaunches `AapProjectionActivity` with `FLAG_ACTIVITY_NEW_TASK` when the
  phone re-runs media-sink setup on the video channel, so **the app can return itself to the
  foreground** with no command from the rig.
- **This rig is permanently joined to a WiFi network** (`Pegue Cdesta`, 5500 MHz), and has been for
  every round on record. Any run whose premise is an *unjoined* head unit is **UNTESTABLE** here, and
  authorization to change the rig's own network association has never been given. The media-gap round
  1 lost a run to a brief that asserted the opposite without checking. **Verify a rig-state premise
  with a command before writing it into a brief**, not from memory of an earlier round:

  ```bash
  adb shell dumpsys wifi | grep -iE "mWifiInfo|SSID|Frequency" | head
  ```
- **`settings.xml` survives between rounds and carries the previous thread's non-defaults.** This
  cuts both ways. Media-gap round 2 needed no settings writes at all because round 1 had left the
  file exactly right, which saved a `force-stop` cycle; the same property silently imports another
  thread's log level, view mode or codec into a round that never asked for it. **Diff against a fresh
  backup at the start of every round** and state the delta (even if zero) in Setup notes, as round 2
  did. Note also that a test-APK install re-runs onboarding on a fresh install and rewrites
  resolution, DPI and codec — see §5.
- **Video fault injection does nothing at its default rate.** `debug-video-fault-injection` selects
  the mode, but `debug-video-fault-rate` defaults to 300 (one in three hundred candidate fragments),
  and at 720p a five-minute capture offers only about thirty candidates for a mode like
  `DROP_MIDDLE_FRAGMENT`. The media-gap round 1's injection run came back INCONCLUSIVE having injected
  nothing at all. A brief that asks for injection **must set the rate explicitly and state the
  expected number of injections**; if that number is not comfortably above one, the run is not worth
  scheduling. Candidate scarcity, not the rate alone, is the binding constraint.
- **The discard rule is "a *second* `createGroup SUCCESS`", not any sign of churn.** Two benign
  patterns keep tripping the broader reading, and both have now been seen in two independent threads:
  a `p2p-wlan0-N` index bump that happens **before** the first `createGroup SUCCESS` is a stale group
  from a previous round being torn down at launch, and a lone `MATCH! Starting AapService` with **zero
  group churn attached** is the phone's own Bluetooth reconnect. Neither is contamination. Count the
  thing that actually matters:

  ```bash
  grep -c "createGroup SUCCESS" capture.txt   # more than 1 in one run is the discard
  ```

  A second SSL handshake in one run is the corroborating signal. Report the counts either way, so a
  clean run is on the record as clean rather than merely unremarked.

---

## 8. Writing a brief (for whoever prepares the next round)

A brief that gets a useful round back has these parts, in this order:

1. **Build and baseline** — branch, exact SHA, the `git` command that gets there, and whether history
   was rewritten since last time.
2. **What this is and why it exists** — the defect, with the evidence that identified it. The tester
   makes better judgement calls when they know what the code is supposed to be fighting.
3. **What is different about this round** — rig-specific facts that change the runs, and any run that
   is expected to be INCONCLUSIVE, said up front so it is not treated as a failure.
4. **Settings keys this round needs** — as a table of elements, ready to paste.
5. **The lines that decide every run** — copied verbatim from the source, not from memory. Verify
   them with `grep -F` against the branch before committing the brief.
6. **Runs**, each with an id, the exact setup, and explicit PASS / FAIL conditions. Mark which one is
   the point of the round.
7. **Do not re-run** — settled runs, so the round is not spent re-proving them.
8. **Report back** — the two or three numbers that actually decide the shipping question.

Prefer a positive control wherever one exists: a setting that makes the defect *reappear* proves the
fix addresses the real mechanism, and is worth more than any number of passes. A control that is a
**settings change on the candidate** beats one that needs a second build: round 8 ran its whole
positive-control set off `playback-focus-mode=1` and needed no baseline APK at all.

**Check each run is physically possible on this rig before writing it.** Two of round 8's ten were
dead on arrival for reasons §7a already implied — one asked for a connect with Bluetooth off, which
Native AA cannot do, and one asked for a deep link to a settings category, which the navigation graph
cannot do. Both cost the tester real time to prove impossible. If a run's setup depends on something
§7a does not confirm works, either verify it first or route that coverage to a JVM test and say so.

**Verify a rig-state premise, never assert one.** Distinct from the bullet above: the run is possible,
but the brief states something about the rig that is simply not true. The media-gap round 1 asserted
the rig had no WiFi station association; it has had one throughout, which made an arm of that round
untestable and was caught only because the *other* arm printed something the brief said could not
appear. Rig state drifts between threads and none of it is yours. One `adb` command in the brief's
own preparation is the whole cost.

**Say what a PASS would look like if the change did nothing.** A PASS condition satisfied by two
different states, only one of which exercises the change, records a green that proves nothing. The
media-gap round 2 asked for zero instrument lines on an idle screen; it got zero, but because the
picture ran at 45 fps the entire window, so the ceiling under test was short-circuited and never
evaluated. The run was still worth having as a regression guard, and the brief should have said which
of the two it would be — and asked for the number that distinguishes them, here the throughput
alongside the count. **Pair every count with the measurement that proves the condition was reachable.**
