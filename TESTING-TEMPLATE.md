# Standing template — how a round is briefed, run and reported

This is the reusable half of the channel. A round's brief carries only what is specific to its
branch; everything below applies to every round and is not restated in briefs.

The rules here are not style preferences. Each one exists because ignoring it once produced a false
result that cost hours to unwind.

---

## House rules — the short version

Seven standing rules. Everything after this section is detail on how to follow them.

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
7. **No issue or discussion numbers anywhere on this branch.** Briefs and results describe the
   behaviour and cite the SHA, the log line or the evidence file. A number is a pointer into a
   tracker this branch has no access to, and it dates badly; write what the fault does instead.
   Rows written before this rule still carry some.

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

**Clear the buffer and trigger the action in the same command.** Two tool calls leave a gap, and
the lines that matter print in it: one round lost a relaunch's `ACTIVELY LISTENING` and
`createGroup SUCCESS` that way, and read a first event later than the app's own clock reported it.
Where the app states an interval itself, that figure is authoritative over wall-clock arithmetic
against the first captured line.

**Raise the ring buffer before relying on `logcat -d` retroactively.** `adb logcat -G 16M` first.
GPS fixes print about once a second during a session and evicted a whole handshake from the default
buffer in one round, leaving a finding with no evidence of its own start.

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

**Only five actions are activities; the rest are receiver-only.** `AutomationActivity`'s filter
carries `ACTION_SET_NIGHT_MODE`, `ACTION_CONNECT`, `ACTION_DISCONNECT`, `ACTION_START_SELF_MODE` and
`ACTION_STOP_SERVICE`, and those are the `am start` forms above. Everything else the manifest
advertises, the wireless actions included, reaches `AutomationReceiver` only, so `am start -a` on one
fails with `unable to resolve Intent` and the run reads as the app refusing the command. Use
`am broadcast -a <action> -p com.andrerinas.headunitrevived` for those, with the package flag below.
A brief that writes `am start` for a wireless action has a bug; it cost one round its first attempt
at grading the status pill's X.

**An `am broadcast -a <action>` aimed at the app's own receivers is dropped unless it names the
package.** Without `-p com.andrerinas.headunitrevived` the shell's broadcast is refused at enqueue as
a background execution, and `am` still prints `Broadcast completed: result=0`, so the run reads as
the app having ignored it. Measured on the Android 14 D-HU in round 5 of the status-pill thread:
`dumpsys activity broadcasts history` showed the one matching manifest receiver as
`SKIPPED terminal ... reason: skipped by policy at enqueue: Background execution not allowed`, with
the app alive and not force-stopped. Adding the package fixed every broadcast in that round,
including `ACTION_QUERY_STATE`. The `am start` forms above are not affected, and neither is
`am start-foreground-service`.

`headunit://disconnect` is the scripted equivalent of the user pressing Exit, which is what the
`isUserExit` code paths are gated on. Prefer it over any UI route.

**`headunit://connect` and `ACTION_CONNECT` are USB-only, and are a no-op under Native AA.** With no
`ip` query param they map to `ACTION_CHECK_USB` (`AutomationActivity.kt:53-57`), which only scans for
USB accessory devices. On a wireless-only rig they do nothing at all, silently: a
`session-vpn-lever` round 1 run sat on one for about four minutes waiting for a second session that
was never coming. There is no deep link that re-arms a mode 3 session after a user exit, because
that exit tears down the P2P group (`AapService.kt:1218-1246`) and the only thing that rebuilds it is
`ACTION_BT_AUTO_START`, which `AutoStartReceiver` fires on the phone's Bluetooth `ACL_CONNECTED`
(`AapService.kt:2157-2181`). **To reconnect on Native AA, cycle the phone's Bluetooth off and on**
and watch for `MATCH! Starting AapService`. It produces a full second session within a few seconds.

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

**Not in Self Mode, from `fix/929-self-mode-media-session` onward.** There the app holds no media
session and forwards no media key, because the player is a local app on the same device and holds a
real session of its own. These keys reach that player directly instead, so a Self Mode round that
needs a track change has to drive the player and cannot read `TX Key -> AA=` as confirmation.

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

**Verify with `dumpsys`, never with `settings get global`.** On the POCO X3,
`settings get global bluetooth_on` and `wifi_on` reported both radios on while
`dumpsys bluetooth_manager` and `dumpsys wifi` showed both actually disabled. A `session-vpn-lever`
round 1 run launched on those keys and was discarded: the head unit formed its group and retried
three times waiting for a phone whose radios were off. `svc bluetooth enable` / `svc wifi enable`
fixed it, but only the `dumpsys` read revealed there was anything to fix.

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
  rewrites resolution, DPI and video codec, three variables you would then be testing by accident.
  §7b carries the one exception, clearing a USB permission grant, and what it costs;
- **confirm which APK is actually live** before trusting a single run:

```bash
PKG=com.andrerinas.headunitrevived
adb shell md5sum $(adb shell pm path $PKG | cut -d: -f2 | tr -d '\r')
```

- **A resource or a string is not an identity check, and version name and versionCode never move
  between our candidates.** Round 5's host arrived carrying a build installed the evening before.
  Its `usb_device_filter.xml` was byte-identical to the candidate's, and its DEX already contained
  the log string the fix prints, because that line predated the fix. Both checks passed on the wrong
  APK and two attempts were spent measuring it, one of them a full lock-screen reboot cycle. When
  the md5 above cannot settle it, because the installed APK was not built here, verify against a
  **symbol the candidate introduces and the older build cannot have**:

```bash
PKG=com.andrerinas.headunitrevived
adb shell pm path $PKG                       # pull that apk, then
unzip -p <apk> 'classes*.dex' | strings | grep -F '<new class or method name>'
```

  A brief should name that symbol in its own build gate. Round 5 used `initUnlockedOnce` and
  `App$userUnlockedReceiver$1`. A crash frame naming a function the fix *deleted* does the same job
  more cheaply: there, `App.onCreate` calling `getComponent` was a one-line "this is the old build"
  tell that neither the resource nor the string could give.

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

This channel serves one rig, so these belong here rather than in each brief. A brief will still say
when a quirk changes a run. **Unless a quirk names a unit, it was measured on D-HU**, which is where
most rounds run; the per-unit sections below say which unit the rest belong to.

### Units

| Name | Hardware | Android | Serial | Role |
|---|---|---|---|---|
| `D-HU` | UNISOC MT50 (`MT50_YT610E4GFPSL_U`, `head-unit-make: Royal Enfield`) | 14 | `27870808938846` | head unit, rooted |
| `D-POCO` | POCO X3 NFC (`M2007J20CG`, `surya`, LineageOS) | 15 | `4f4027e9` | phone, also stands in as head unit |
| `D-MOTO` | Motorola edge 30 neo (`miami`) | 14 | `ZY22GC3BM4` | phone, not rooted, can be PIN-locked |
| `D-SAM` | Samsung SM-T230, Galaxy Tab 4 7.0 (`degaswifi`) | 4.4.2 (API 19) | `30041c35642d2200` | tablet, runs as head unit |
| `D-HP` | HP Slate 7 Plus (`birch`) | 4.2.2 (API 17) | `CNU350BGBJ` | tablet, head unit, Headunit Server only |

Android versions are as of 2026-09-16 and drift: D-POCO has read 11, 12, 13 and 15 across rounds.
A round's own `Unit:` header is the authority for what that round ran on, not this table.

**Five units against four USB ports on the test PC, and wireless adb does not cover the gap.**
Projection takes the head unit onto a P2P group or a shared LAN, so the unit under test is always
cabled; a phone can go wireless only in Headunit Server mode, where it stays on the house LAN. A
round that needs more than four devices is staged, each stage names what is plugged in, and the
Setup notes say what was. **D-HP came back on 2026-09-17** after being listed here as retired: it
has no WiFi Direct and no hotspot, so `wifi-connection-mode=1` is the only mode it runs, and nothing
downstream of an access point is possible on it.

Two names to keep straight. **D-SAM was called `D-T230`** in `t230-native-aa-bringup-round1` through
`-round4` and its two addenda, which are not edited; read that name as D-SAM anywhere it appears.
And the Samsung phone `R5CY90XYBED` in the `usb-device-diagnostics` rounds is a **USB test
peripheral plugged into a host**, not a rig unit and not D-SAM.

### D-SAM only

Every one of these was measured across `t230-native-aa-bringup` rounds 1 to 4.

- **No `su`, and no `sed`, `awk`, `busybox` or `toybox`** (`cp`, `cat`, `grep`, `mv` exist). Use
  `set_pref.sh`, the `run-as` script built for unrooted units, never `set_hu_pref.sh`, which assumes
  root and defaults to D-HU. `set_pref.sh`'s pushed on-device helper still fails here with
  `sed: not found`: the method that works is to edit a local copy of `shared_prefs/settings.xml`
  with `python3` on the host, push the whole file, then
  `run-as $PKG cp <pushed file> shared_prefs/settings.xml`. A `sed`-free variant of `set_pref.sh`
  was asked for in round 1 and has not been written.
- **The USB port is data-only and does not charge it.** `dumpsys battery` reads `USB powered: true,
  AC powered: false`, and the battery sat at 9% through all four rounds, so every conclusion from
  them carries that as an uncontrolled variable. Charge it from a separate supply before any round
  that measures the radio.
- **Mount or hold it in its native portrait position**, with `screen-orientation=2` (`LANDSCAPE`).
  Neither of the app's two fixed-landscape settings gives a correct display on a landscape mount;
  `3` (`LANDSCAPE_REVERSE`) is visibly worse, tested live and reverted. This is an installation
  constraint, not a preference.
- **Video no longer needs capping by hand, as of the 2026-09-16 build.** At 1080p the phone used to
  complete the handshake, open every channel and break the TCP session with `EPIPE` about 3.5 s
  later, repeating every 5 to 6 s and never rendering a frame; 60 fps was fine, so resolution was the
  binding constraint. `audio-sink-jitter-round4-results.md` N5 measured the app's own cap firing
  unasked on every session including a 40 minute one, with no `EPIPE` anywhere. Read
  `[RES_CAP] ... linkCapped=` and leave `resolutionId` alone. On a build older than that, cap it.
- **The P2P group name is random every session.** `wifi-direct-stable-identity` has no code path
  below API 29, so any run that keys off a stable SSID is untestable on this unit.
- **Build and install with** `install_and_launch.sh` and `HU=30041c35642d2200`. It wraps
  `build_hur.sh` (`assembleGithubDebug`) and installs and relaunches in one step.
- **The system clock was a fixed ~12h00m behind the host and D-POCO, and is not any more.** It was
  set by hand on 2026-09-17 and now matches the other devices, so **a round from that date on lines
  D-SAM's timestamps up against a phone's or the host's with no correction**. Confirm it anyway at
  the start of a round: `adb -s 30041c35642d2200 shell date` against the host's own `date`, one line,
  and say in Setup notes what it read. The offset was measured across `audio-sink-jitter-round4` and
  was still in force through `round7`, so **every result file up to and including round 7 carries
  offset D-SAM timestamps**: add 12h when reading one of those against a phone log. The old note that
  `date -s` silently no-ops no longer describes this unit; the method that did work is unrecorded.
- **`SettingsActivity` is not exported on this build.** `am start -n ... SettingsActivity` from a
  plain `adb shell` fails with a `SecurityException`. The substitute on an unrooted unit is
  `adb shell run-as $PKG am start --user 0 -n ...`, which launches from the app's own UID.
- **`am` rejects `-p` on 4.4.2** with a `NullPointerException` inside the `am` command itself, for
  both `am broadcast` and `am start`. Drop the flag everywhere; it is not specific to one action.
- **Never chain `svc wifi disable` and `svc wifi enable` in one `adb shell` call.** On this unit
  that produced repeated `try again in 1second` retries and then an unexpected ~18 MB
  `dumpstate`-shaped dump on stdout, probably because the WiFi service is not ready again when the
  enable fires. Issue them as separate calls with a pause between, which worked every time.
  Measured in `audio-sink-jitter-round5`.
- **It cannot host a WiFi access point, by any path found.** `ro.radio.noril=yes`: no baseband or RIL
  hardware at all, a true WiFi-only tablet. Its Settings app exposes a Tethering screen only through
  the hidden `com.android.settings/.Settings$TetherSettingsActivity` component, and that screen
  renders a header with nothing under it: no hotspot toggle of any kind. So **no hotspot-transport
  run (`native-ap-transport=1`) is possible on this unit**, and neither is anything downstream of one,
  including the WPP-over-TCP serve path. Route those to D-HU. Measured in `audio-sink-jitter-round7`,
  which lost its W1r to it after the app waited out both its budgets (30 s for the access point, 60 s
  for credentials) and failed with an explicit message, correctly.
- **Unverified here:** whether the in-app log export lands under `/storage/sdcard0` rather than
  `/storage/emulated/0`. That was measured on D-HP (Android 4.2.2) and is likely to hold on another
  Android 4.x unit, but nobody has checked it on D-SAM.

- **Its clock now matches the host.** The roughly 12 hour offset recorded through round 7 was gone
  in `projection-raise` round 3, `date` on both agreeing to the second. Check it rather than
  assuming either way, and say which in Setup notes.
- **Per-unit counters survive rounds nobody reported.** `wifi-direct-group-name-changes` read `4` at
  the start of `projection-raise` round 3, left over from an earlier session, which would have
  invalidated a run that assumed a fresh unit. Read any counter a run grades before the run and
  reset it deliberately, never assume it starts at zero.

### D-SAM and D-HP, both

- **`input tap` takes the UIAutomator logical coordinate space, not `screencap`'s physical one.**
  The physical buffer comes back portrait (800x1280) while the app forces landscape content into it
  (`rotation="1"` in a `uiautomator dump`). Tapping the screenshot's apparent pixel position fails;
  tapping the `uiautomator` bounds works. Cost real time on both units in `projection-raise` round 2
  before the right mapping was found. Dump, then tap the dump's bounds, on either tablet.
- **Device-to-device ARP resolution to a phone breaks transiently**, `Destination Host Unreachable`
  on both sides despite an ARP entry with the correct MAC, and self-resolves after a short wait with
  no adb intervention. Seen on both tablets in the same round. Wait it out before chasing it.

### D-HP only

- **`native-aa-complete-hfp-slc` is unreachable here, whatever it is set to.** The stand-in
  hands-free record lives inside `NativeAaHandshakeManager`, the Native AA and WiFi Direct path, and
  D-HP has no WiFi Direct and no hotspot, so it runs Headunit Server only. Do not brief this unit
  for that setting; D-SAM is the tablet that can reach it. Established in `projection-raise` round 2.
- **`run-as $PKG am start --user 0 -n .../SettingsActivity` segfaults this unit's `am` binary**,
  every time it was tried, without crashing the app. Try `am start` without `run-as` and without
  `--user 0`, or `monkey -p $PKG 1` to bring the app forward, before assuming the UI is unreachable.
- **Neither `svc wifi disable` nor `settings put global wifi_on 0` takes the radio down** on this
  Android 4.2.2 build: `dumpsys wifi` keeps reporting `"Wi-Fi is enabled"` with no observable state
  change. Any run needing a WiFi disconnect here needs a physical lever, or D-SAM instead.
- **The in-app log export lands at `/storage/emulated/0`**, not the legacy `/storage/sdcard0`,
  confirmed on this unit in `projection-raise` round 2 via `ACTION_EXPORT_LOG`.
- **Its USB connector is flaky.** The link vanished from `adb devices` once mid-round and came back
  on a physical replug with the app process and its session both intact, same pid either side.
- **The Settings screen cannot be reached by script, and four routes are closed.** `run-as am start`
  segfaults with and without `--user 0`; plain `am start` is refused because the activity is not
  exported; `monkey` plus `input tap` at the Settings button's dumped bounds produces no transition;
  and neither does the same mirrored for the `rotation="1"` against `mRotation=3` disagreement the
  dump shows. Touch delivery itself works, because the neighbouring WiFi button at dumped bounds
  fires `beginAutoConnect`. Any run needing this screen needs a hand. `projection-raise` round 3.
  **Round 4 did that hand-tap** (Settings, then the log Share button): both landed cleanly, produced
  a real system `ChooserActivity`, and the app stayed alive throughout — the screen and the Share
  flow both work fine once a human reaches them, the gap is purely in scripting the route there.
- **Host-side `logcat` capture has died silently here during long runs**, twice in one session, for
  two and a half and seven minutes, with the app's own pid unchanged either side. It cost that round
  a one-shot log line that had already rotated out of the device's ring buffer. On any long run,
  write the capture to a file from before the session starts and check the process is still alive at
  the end. **Round 4 hit this again** (twice in one ~19 minute run) — a 60 s-interval liveness poll
  that restarts the capture with `>>` append on death is sufficient to not lose anything; the one-shot
  line in question landed cleanly across one of the two gaps.
- **`svc wifi enable` can take ~25-30 s to actually bring the radio up on this Android 4.2.2 build.**
  `dumpsys wifi` read `"Wi-Fi is disabled"` for four consecutive 5 s checks after the command
  returned, then `"enabled"` on the fifth. Poll rather than assuming the command's own return means
  the radio is up; a run that immediately launches the app against a WiFi-dependent mode (Headunit
  Server discovery, for instance) will otherwise read as a connectivity failure that is really just
  impatience. `projection-raise` round 4.

### Everything else

- **One hands-free link per device, in both directions, and it shapes any round with two phones or
  two head units.** A phone's Audio Gateway serves one hands-free device, and a head unit serves one
  too: D-HU's own `HeadsetClientService` reports `mMaxHeadsetConnections: 1` / `Max Connected
  Devices = 1` in `dumpsys bluetooth_manager`. So a second head unit cannot take a phone whose
  hands-free another head unit already holds, and a second phone cannot be given a head unit's slot
  while the first holds it. **This is not a defect and no app can fix it** - the disconnect APIs are
  unreachable at a modern target SDK - so never grade an app against beating it. What it means for
  a round is a precondition: whichever pairing a run needs, clear the other one first, by
  disconnecting it or by switching the holder's Bluetooth off. `projection-raise` round 4 hit it
  twice, once inside W3r's own setup and once as a standalone observation, and only the second was
  recognised at the time.

- **`shared_prefs/` can be root-owned, and then the app's own writes never reach disk.** On D-HU
  that directory has been `root:root` while the app runs as its own uid. Reading `settings.xml`
  needs only file permission and works; `SharedPreferences.apply()` writes a temp file and renames
  it, which needs write permission on the **directory** and has none. So every setting the app
  writes updates the in-memory copy correctly and is lost on the next start, for any key. It is
  silent in both directions: a run that reads in-process sees the right value and passes, a run that
  force-stops and reads the file sees the stale one and fails, and neither is the truth. `stat` the
  directory at the start of any round that depends on a value the **app** writes, `chown` it to the
  app's uid:gid if it is wrong, and say in Setup notes what it read first. Values the tester seeds
  are root writes and are unaffected. Measured 2026-08-21 in `connection-failure-banner` round 1,
  which lost its R2c to it; SELinux was checked and is permissive, so that is not the cause.
  **Report what the `stat` read in Setup notes even when it is correct**: `projection-raise` round 2
  did not, and two of its runs graded a value the app writes, so neither result can be read now.
- **`auto-start-bt-macs` does not scope the poke loop, and a round read it as a defect.** The wake
  list is `native-poke-bt-macs`; `auto-start-bt-macs` gates only which device auto-launches the app
  on a Bluetooth connect. An empty wake list plus `native-poke-all-paired-devices`, which defaults
  to on, is what walks every bonded phone. The two lists were deliberately split because one list
  doing both jobs wrote itself back and undid the user's clearing. To scope a poke, set
  `native-poke-bt-macs`.
- **`native-aa-wake-damage-verdict` latches once a wake has measured the unit**, by design, and a
  latched verdict stands every later poke down. `projection-raise` round 3 hit this and had to clear
  it mid-round to reach a session at all. Clear it to `0` before any run that needs a real session
  after a wake run, and say so.
- **`svc wifi disable` fails on API 19 too, and fails when issued alone.** Round 3 ran it on D-SAM
  by itself, not chained with `enable`, and got the same `"try again in 1second"` loop eight times
  over plus an unsolicited multi-megabyte `dumpstate` on stdout, with WiFi still enabled two seconds
  later. `settings put global wifi_on 0` does nothing either. Neither tablet has a working lever.
- **The host PC's own firewall can block a listener a round depends on.** `projection-raise` round 2
  needed a rule change before a bare TCP listener on the host could be reached from D-HU at all
  (ICMP worked, TCP did not, on two arbitrary ports). Check host-side reachability before reading a
  head unit's failure to connect to the host as the head unit's fault.
- **`native-aa-complete-hfp-slc` reading `false` is not a regression, and one round reported it as
  one.** The setting is for units that cannot make a hands-free profile of their own, where the app
  publishes a stand-in record and has to speak on it. A unit whose own stack already advertises
  Hands-Free never gets a stand-in published at all, so the setting is inert there whichever way it
  is set, which is why D-HU connects fine with it off. Do not "restore" it on D-HU. The units it is
  live on are the tablets standing in as head units, D-SAM and D-HP, and a phone doing the same.
- **A run that denies `SYSTEM_ALERT_WINDOW` must give it back in the same run.** Without the
  permission to draw over other apps, a session the phone starts by itself reaches the handshake and
  then hangs: the projection screen is raised by a full-screen-intent notification that Android 14
  demotes, and until somebody taps it nothing reads the socket. `appops` is not in `settings.xml`, so
  a settings restore does not undo it. One round denied it in its fourth run and read the next six
  sessions as a general defect in the candidate.
- **Projection is proved by four lines, and `TransportStarted` is not one of them.** It is a state
  name the app never logs, so a grep for it reports a perfectly healthy session as never having
  projected. Use `Service Discovery Response`, `Channel Open Response`,
  `Media Sink Setup Request: N on channel VIDEO` and `Throughput over …ms: rendered=`.
- **The status pill's own line says whether the projection was raised and by whom.**
  `status pill step: STARTING_PROJECTION` means the SSL handshake finished, nothing more. The suffix
  `(not shown, the overlay owns the screen)` means an auto-connect attempt was in flight, which
  raises the screen itself; a bare `STARTING_PROJECTION` with no `AapProjectionActivity.onCreate`
  after it is a session with nothing reading it.
- **D-POCO's WiFi adapter self-reverts back on** a few seconds after `svc wifi disable`, the same way
  the documented Bluetooth self-reverts do on other units. A single disable will not hold the phone
  off a network: use a repeat-disable loop for any run that needs the link starved rather than merely
  interrupted. Measured in `audio-sink-jitter-round4`. **Round 5 refined both halves of this.** The
  self-revert appears to follow a single disable only: after a sustained repeat-disable loop the
  radio stayed off for several minutes and needed an explicit `svc wifi enable`. And a 15 s cadence is
  too loose, because the radio comes back fast enough inside the gap to complete a whole poke,
  handshake, join and session cycle, which produces churn rather than starvation. Use 3 s.
  `s1_wifi_holddown.sh` and `s1_wifi_holddown_tight.sh` in `hur-wifi-test-scripts/` are the two
  cadences.
- **Confirm D-POCO's screen is idle or at home before trusting an automated bring-up.** A stray
  Settings screen left open from earlier diagnosis silently stopped Gearhead answering the HFP poke
  with the Android Auto channel, with nothing on the head-unit side to point at it.
  `adb shell dumpsys window | grep mCurrentFocus` is the check; `input keyevent KEYCODE_HOME` is the
  fix. Measured in `audio-sink-jitter-round5`.
- **Forgetting the head unit on D-POCO has no adb-reachable trigger, and neither does the consent
  dialog that follows.** Settings > Android Auto > Vehicles > each entry > Forget is UI only;
  `am start` into Gearhead's activities finds nothing scriptable. The next connection then raises a
  one-time "Welcome to Android Auto" dialog whose `Continue` is also UI only. Both are done with
  `uiautomator dump` plus minimum taps. This is the documented and so far only recovery from a phone
  wedged on a stale WPP-over-TCP endpoint. Measured in `audio-sink-jitter-round5`.
- **That forget is not durable, and round 5's claim that it held for a round is refuted.** Round 7
  forgot the vehicle and the very next poke cycle was answered in full: RFCOMM accepted 290 ms after
  the poke, a complete handshake, a session and video within 15 s, with no consent dialog shown.
  Gearhead keeps opening that socket whether or not the vehicle is remembered. So a forget clears a
  stale *endpoint*; it does not make the phone ignore the head unit, and any run whose lever is "the
  phone stops answering" needs a different one. Measured in `audio-sink-jitter-round7`.
- **A run that sets `native-ap-transport=1` arms the phone, permanently, and this is the rule that
  orders a multi-phone round.** On its own access point a head unit advertises a WPP-over-TCP endpoint
  unconditionally; the phone stores it for the life of its Android Auto process and dials it in
  preference to Bluetooth, and **nothing in the protocol or in the app can retract one**. Round 7 lost
  its first three bring-ups to an endpoint left over from round 6, across a round boundary, with
  nothing either round's settings did able to clear it. On WiFi Direct neither head unit can advertise
  one (D-SAM's Android cannot name its own P2P group, D-HU re-addresses its group on every create), so
  only hotspot-transport arms carry this cost. Put every such arm last, on a phone no later block
  needs clean, and forget the vehicle on it before it is used again.
- **`playback-focus-self-defeating` is a latch the app writes, and it survives everything.** Two media
  channels closing within 5 s of a focus grab set it, and once set the app never takes system audio
  focus again, on this or any later session. Only re-picking the focus mode in the settings UI clears
  it from inside the app; from adb, write `false` with the app stopped. A whole round of focus runs
  measured nothing because it was already set: every decline read `learned` whatever the mode was.
  Read it back before and after any run that is about audio focus.
- **A heard fault can be stamped into the capture.** `ACTION_LOG_MARKER --es text <label>` prints
  `AutomationMarker: <label>` at WARN, so a tester listening to a run can put the moment they heard
  something into the log beside the instruments. `-f 0x00000020` is required, as for every automation
  broadcast. Worth using for any audible or visible fault the instruments might miss entirely: the
  count of markers that fall in a window where nothing was logged is the size of that blind spot.
- **Driving VLC as the phone-side audio source takes three fixes that are not obvious.** It needs
  `appops set org.videolan.vlc MANAGE_EXTERNAL_STORAGE allow`, because that is an app-op and
  `pm grant` does not set it; a leftover playback item makes it reopen the old path whatever the new
  intent says, until `pm clear`, which then costs a first-run storage scan of about 15 s; and the
  activity to target is `org.videolan.vlc/.StartActivity`, not the player activity, which accepts the
  VIEW intent silently and never becomes the resumed activity. Launch with
  `am start -a android.intent.action.VIEW -d "file://<path>" -t audio/mpeg -n org.videolan.vlc/.StartActivity`.
  A keep-alive watchdog that restarts it on any non-PLAYING state will contaminate any run that
  deliberately pauses playback; kill the watchdog first.
- **A bonded A2DP speaker invalidates any run a person is listening to.** A paired "Magnetic
  Speaker" was holding `STREAM_MUSIC` on the head unit at the start of a listening round, so the
  audio a tester would have graded by ear was never coming out of the unit under test. Confirm
  `adb shell dumpsys audio` reports the route as `speaker(2)` before every run that uses a person as
  an instrument, and physically disconnect anything bonded that can take the stream.
- **`adb install -r` can silently wipe `settings.xml`.** Reinstalling over a live session with the
  same `versionCode` reset a head unit's settings to a handful of connection-bookkeeping keys,
  onboarding flags included, despite a `force-stop` immediately before. It happened once going
  baseline to candidate and not on the reverse or on later cycles, and the cause was not chased down.
  Reseed and read every key back before the next launch rather than assuming the install preserved
  them; a round that reads a default it never set has measured nothing.
- **Poke targets survive from earlier rounds and wake the wrong phone.** `native-poke-bt-macs` and
  `last-connected-native-mac` are per unit and persist, so a head unit left pointing at a phone from
  an unrelated round will never let the phone under test join its group, with no line saying why.
  Repoint both before the first run of any round that changes the pairing.
- **A P2P group outlives `am force-stop` on the unit that hosted it.** The previous head unit keeps
  its group up, and a phone still associated to it will not join anything else. Bouncing the phone's
  WiFi with `svc wifi disable` then `enable` clears it; stopping the old head unit's app does not.
- **What AudioFlinger thinks needs no root.** `dumpsys media.audio_flinger` and
  `dumpsys media.metrics` both work from the shell user on the rig's units, which is the only way to
  see an underrun below our own `AudioTrack`. The framework's per-session figure is the
  `MediaAnalyticsItem ... android.media.audiotrack.underrunframes=N` line at teardown: divide by the
  sample rate for seconds of real underrun, and prefer it over any counter of ours when the two
  disagree.
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
- **`ACTION_RECREATE_MAIN` cannot be fired from adb, and no `-p` fixes it.** `MainActivity`
  registers that receiver with `ContextCompat.RECEIVER_NOT_EXPORTED`, so a shell-uid broadcast is
  never dispatched to the app; `am` still answers `Broadcast completed: result=0`, and
  `dumpsys activity broadcasts history` shows it enqueued and never delivered. Round 8 lost an
  attempt to it. **To recreate an activity, rotate the unit for real:**

  ```bash
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1     # 0 restores
  ```

  `MainActivity`'s manifest `configChanges` is `keyboardHidden|uiMode` only, so a rotation genuinely
  destroys and rebuilds it: two `WindowManager: finishDrawing of relaunch` events and a second
  `MainActivity.logLaunchSource`. **Grade a recreation on those, never on the PID** - Android rebuilds
  an activity inside the same process. Note `AapProjectionActivity` carries `orientation|screenSize`
  in its own `configChanges`, so the same lever does **not** recreate the projection activity.
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
- **A run that needs D-HU on a 2.4 GHz network needs the operator's hands, twice.** D-MOTO is not
  rooted, so `cmd wifi start-softap` refuses (`SecurityException: Uid 2000 does not have access`) and
  its hotspot is hand-started; and D-HU roams straight back to its saved 5 GHz network the moment the
  app exits, so every run after the first needs the join redone. `cmd wifi connect-network` is not
  the way back: it accepts neither a bare saved network id nor this rig's spaced SSID
  (`Unknown network type Chingon`). Round 8 ran C1, C3 and C5 hours after the rest of Part C for this
  reason. **Order a brief's runs so every hand-operated-hotspot run is adjacent**, and put the
  NEVER-mode run between them where it can be, since NEVER never leaves the network.
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
- **Seeding a `connection-issue-*` stamp means clearing `connection-issue-dismissed-at` too.** The
  banner compares the two directly, and the seed constants briefs use (`1755800000000` and its
  neighbours) are *older* than any real on-device clock reading. So a dismissal left behind by an
  earlier run in the same session silently suppresses a stamp seeded afterwards, and the run reads
  as "no banner" when the banner logic is working exactly as designed. This cost a real false
  negative once. Delete the key alongside every seed unless the run is specifically about dismissal.
- **The banner is refreshed on `onResume()` and nowhere else.** Nothing re-checks while the app stays
  foregrounded, so a condition that raises mid-session does not appear on a screen that is already
  up. Any step phrased as "let the condition raise by itself and confirm the banner" therefore means
  force-stop and relaunch, whatever else it says. Two runs in one round have needed this.
- **Media keys do not open or resume an AAP audio channel here. A tap on the projected media widget
  does.** Measured across a whole round: `KEYCODE_MEDIA_PLAY_PAUSE` sent to the phone
  (`adb -s <phone> shell input keyevent`), relayed through the head unit's `CommManager`
  (`adb -s <hu> shell input keyevent 85`), and a genuinely fresh Spotify relaunch all failed to reopen
  a channel once it had closed. What works every time is tapping Android Auto's own play/pause
  control, which is drawn *into the projected video* rather than being a native view, so the tap goes
  back to the phone over the app's own touch channel: `adb -s <hu> shell input tap 272 657` on this
  rig's 1440×720 layout. Plan any run that needs music playing around the tap, not the key. One side
  effect to expect: the first Play tap of a cold session flaps `PLAYING`/`PAUSED` every 200-800 ms for
  several seconds before settling, with `MediaSession: Processing transport control action =
  KEYCODE_MEDIA_PAUSE` repeating, so take timings after it settles.
- **This rig has no speaker and no 3.5 mm output.** Nothing can be checked by ear, which suits
  §0's rule that a verdict must never depend on a human being present, but it means a brief step
  phrased "confirm audio audible" has no way to run. Every audio brief must name its scriptable proxy
  up front; the ones already used are the phone's own `dumpsys media_session` playback state and the
  app's `Media Start Request AUDIO` and `AapMediaPlayback` status lines. A round that discovers this
  mid-run loses the corroboration on every step that assumed it.
- **Cycling the *phone's* Bluetooth radio does not produce a fresh `ACL_CONNECTED` here. Cycling the
  *head unit's own* adapter does.** `svc bluetooth disable` / `enable` on the phone was tried at a 2 s
  and a 10 s window, with up to 40 s of continuous observation after each, and no
  `android.bluetooth.device.action.ACL_CONNECTED` ever reached the head unit — confirmed absent from a
  continuous capture, not a `logcat -d` ring wrap. The working substitute is `svc bluetooth disable` on
  the **head unit**, which self-reverts in ~14 s per the entry above and raises a real system-level
  `ACL_CONNECTED` every time. Corroborate it from the phone's own
  `GH.WifiBluetoothRcvr: Connection action: ... ACL_CONNECTED` rather than from our side alone, so the
  run is not resting on our own receiver to prove our own receiver fired.
- **`pm revoke android.permission.RECORD_AUDIO` does not work on this ROM**, which answers with
  `SecurityException: ... is not a changeable permission type`. `appops set <pkg> RECORD_AUDIO ignore`
  (and `allow` to restore) does work. **They are not the same test.** The app-op denial leaves the
  runtime grant at `granted=true`, so any code that reads the *permission* rather than the *op* still
  sees it held, and a run that needs a genuine revoke cannot be done here at all. Say which of the two
  a run used. The pre-round default on this rig is `foreground`, not `allow`; restore it to that.
- **`set_hu_pref.sh` cannot be used to switch arms.** It relaunches through
  `install_and_launch.sh SKIP_BUILD=1`, which installs whatever is newest in the shared `apks/`
  folder rather than the arm under test. It silently reinstalled the candidate part-way through an
  arm-A run; the md5 check caught it before any capture was taken. For an A/B, install a named APK
  with `adb install -r <specific-apk>` and verify the md5 every single time, and edit `settings.xml`
  directly rather than through a script that relaunches.
- **`KEYCODE_BACK` does not cancel an in-flight assistant session.** Sent 1.5 s into one, the session
  ran its full natural length regardless. What does cancel it is a second trigger sent shortly after
  the first. Prove the cut from the session's own uplink summary rather than from the clock: an
  interrupted session measured 1.09 s and 10 frames against a natural 3 to 10 s and 23 to 82 frames
  elsewhere in the same round.
- **One trigger can produce several assistant sessions.** Gemini is multi-turn, so a single broadcast
  was observed producing two or three `Voice Session Notification: START`/`STOP` pairs with no
  further action. Any brief that says "four assistant sessions" means four START/STOP pairs; count
  sessions, never triggers, and do not assume a 1:1 mapping holds.
- **Gearhead's phone-side strings drift between builds.** Three strings confirmed present in
  `17.5.663204` (`Using phone microphone`, `Not using phone mic`, `microphone timed out; no data
  received for`) did not appear at all on `17.3.662854`, on a run where the phone-side behaviour they
  describe demonstrably happened. Confirm any phone-side string against the build in front of you,
  and report the Gearhead version in Setup notes so a later reader can tell absence from drift.
- **`headunit://exit` does nothing to an app that is already force-stopped.** Sent to a dead process
  it cold-launches the app through `AutomationActivity`, which then runs its own auto-connect and can
  form a brand new group: the opposite of the "confirm no group" the exit was for. Send it to a
  *running* app, then confirm with `dumpsys wifip2p`.
- **A phone can be left hosting its own WiFi Direct group from an earlier round.** A unit that played
  head unit keeps `isGroupOwner: true` on a `DIRECT-` network, and while that stands a phone-role
  connection to the real head unit stalls forever at `PHONE_JOINING`: the RFCOMM handshake completes,
  credentials go out, and `WirelessServer: Incoming connection detected` never appears. Check every
  phone with `dumpsys wifip2p | grep isGroupOwner` before the round starts. On a rooted unit
  `cmd wifip2p remove-group` clears it; on an unrooted one that call is refused with a
  `SecurityException` and a `svc wifi disable`/`enable` cycle is the lever.
- **The external Bluetooth module route can be reached on a unit that has no module, with root.**
  `ExternalBtPolicy.detect` accepts the system property `rw.zlink.bt.type=extra` (case-insensitive)
  as evidence, alongside the `/dev/rf_serial` and `/dev/zj_bt_serial` nodes. `setprop
  rw.zlink.bt.type extra` therefore puts the app on its real detection path, which is the only way to
  see the module settings rows, the probe and the refusal dialogs on ordinary hardware.
  `BluetoothHelper.externalBtEvidence` is a `by lazy`, so the property has to be written with the app
  force-stopped and read on the next launch. Native AA is refused while it is set, so do this last in
  a round and clear it with `setprop rw.zlink.bt.type ""` plus a force-stop afterwards.
- **The settings-screen module probe and the real module route log under different prefixes, and
  never both.** `ZbtProbe` is the diagnostic behind the "Test the head unit's Bluetooth module" row
  and every line it writes is `ZbtProbe:`-prefixed; on an unreachable daemon it writes the raw
  exception (`ZbtProbe: ConnectException: ...`) and the row itself reads "Nothing is listening on
  port 3152. This unit has no vendor Bluetooth daemon to talk to." The `NativeAA: [ZBT]` lines,
  including `nothing is listening on 127.0.0.1:3152`, come from `ZbtDaemonReachability` and
  `ZbtAaCarrier`, which run only during a real Native AA handshake attempt. A brief that quotes a
  `NativeAA: [ZBT]` line for a probe run is asking for a line that cannot appear.
- **`enable-audio-sink` gates the media and speech channels, and a `false` left by an earlier round
  looks like a broken build.** With it off the app logs "Audio sink is off in Settings. Skipping the
  media and speech audio channels - the phone will not send audio and this is not a fault", and the
  only channel set up is the always-on Audio2 (System Sounds) one, so no `AudioDecoder.start:` ever
  appears for music. Read it back `true` before any run that grades an audio channel.
- **D-MOTO is not rooted, so its hotspot cannot be scripted.** `cmd wifi start-softap` and
  `cmd wifi get-softap-config` both refuse with `SecurityException: Uid 2000 does not have access`,
  and this build has no non-root tethering shell command. A run that needs D-MOTO to host a network
  is hand-operated: the operator sets the band, reads off the SSID and password, and says so in
  Setup notes.
- **A spaced SSID needs its quotes escaped so they survive adb's argv join.** `adb -s $HU shell cmd
  wifi connect-network \"SSID With Spaces\" wpa2 psk` works; a plain locally-quoted `"SSID With
  Spaces"` is re-split on the far side and fails with `Unknown network type <second word>`. Round 7
  found even the escaped form unreliable from some shells and added
  `hur-wifi-test-scripts/connect_hotspot.sh`, pushed to `/data/local/tmp` and run on the unit, which
  sidesteps the join entirely. Prefer the script for any run that joins a named hotspot.
- **There is no non-destructive way to make D-HU leave a network it prefers.** Its `cmd wifi` build
  has `forget-network` but no `disable-network`, and its home network is both stronger and
  higher-priority than any temporary hotspot, so it wins a reconnect race. Round 7 joined the
  hotspot and launched the app back-to-back, faster than the roam-back; that worked every time but
  it is a race, so a run that depends on the station's network should read the frequency back rather
  than assume it.
- **D-MOTO can be behind a PIN rather than a swipe, and adb has no way past one.** Round 7 met both
  states in the same round with no adb action in between, so it is session-dependent (a trust-agent
  grace window). A run that needs D-MOTO's screen unattended should confirm the lock state first,
  or be scheduled where a person can unlock it once.
- **Mocking GPS does not reach Android Auto's own navigation.** `cmd location providers
  set-test-provider-location` against the raw `gps` provider drives Google Maps on the phone itself
  (round 7 watched it compute a route and count an ETA down), but the projected session still
  reported `0 km/h` and never advanced past the first maneuver across ten minutes of fixes. Android
  Auto's nav rendering consumes Play Services' fused location, which this method does not feed. Do
  not brief a run that grades projected turn-by-turn against this lever.
- **The device-protected auto-start mirror is `shared_prefs/settings_device_protected.xml`**, under
  `/data/user_de/0/com.andrerinas.headunitrevived/`, and it exists only once the app has written it
  at least once. Check both it and the host `settings.xml` whenever a run grades an auto-start key.
- **Clearing the auto-start device and testing a Bluetooth arrival are mutually exclusive on one
  unit.** A run that proves `auto-start-bt-macs` is cleared when two phones are paired removes the
  MAC a real arrival would have to match, so any run downstream of it that needs `ACTION_BT_AUTO_START`
  from a real arrival has nothing left to arrive against. Put the two on different units, or fire the
  action at the service component directly and say in the results that the receiver-side arrival was
  not the trigger.
- **`headunit://disconnect` stops the Native AA launcher, and a Bluetooth arrival is not the only way
  back, but on a build before the round 8 candidate `ACTION_START_WIRELESS` is not the lever.**
  `wifiLauncherManager.stop()` leaves the stopped launcher in place, so `setActive` answers "WiFi
  Mode NATIVE.mode with same start-configuration is already initialized" and arms nothing. Measured
  in round 7's D2, where it produced no session at all, and the refusal was at DEBUG so an INFO
  capture showed nothing whatever. `ACTION_NATIVE_AA_POKE` on a stopped manager does start it and
  logs why it had not started, but it costs a full RFCOMM handshake, and a `headunit://disconnect`
  issued just before will tear the reopened listeners down about 1.3 s later. From the round 8
  candidate on, the guard asks whether the launcher is still running and `ACTION_START_WIRELESS`
  re-arms; the refusal is at INFO, so a brief can grade its absence.
- **A hotspot-arm-then-revert recipe has to clear four keys, not flip one.** Setting
  `native-ap-transport=0` while `hotspot-ssid`, `hotspot-password`, `static-bssid` and
  `hotspot-interface` still carry the hotspot arm's values makes the next WiFi Direct group form on
  the hotspot's own static BSSID and read `identity stable=yes`, which is a false reading and voids
  anything graded on it. Clear all four (`""`, `""`, `"0"`, `""`) and read them back. Caught in
  `wpp-over-tcp` round 4 and again in `audio-sink-jitter` round 6, on a different thread's brief.
- **D-POCO's `dumpsys wifip2p` reads `P2pDisabledState` for minutes after a rapid WiFi bounce while
  the radio is fine.** Seen twice in `audio-sink-jitter` round 6 after `svc wifi disable`/`enable`
  cycling, with station WiFi itself reading `Wi-Fi is enabled` and a real P2P join completing
  minutes later. Treat it as a stale read, not a disabled radio, and do not gate a run on it.
- **`adb shell cat <apk> | md5sum` does not agree with a real `pull` plus `md5sum`.** Round 6 got
  two different hashes for a byte-identical 23419189-byte file. The pipe is a transport artefact;
  pull the file and hash it locally whenever a run grades APK identity.
- **Gearhead cannot be held down with `force-stop`.** Its own Bluetooth-triggered receiver restarts
  it and it answers the very next poke, confirmed in round 6 with the force-stop reissued every
  ~15 s. `pm disable-user` and a background force-stop loop may both be refused by the session's
  permission scope. The lever that holds is **forgetting the vehicle** in Android Auto's settings:
  one set of `uiautomator` taps, durable for the round, and the phone's Bluetooth stack still
  answers the HFP poke, which is what an unanswered-poke counter measures.
- **`pm disable-user` does not kill a package's already-running processes, and `dumpsys package`'s
  `enabled=0` does not mean disabled.** The mirror image of the entry above: disabling Gearhead
  while its `:projection`/`:shared`/`:car` processes are already alive from earlier testing leaves
  it fully functional until those processes are separately killed (`am force-stop` or a natural
  exit) — a session can still complete normally after a "successful" disable. Verify with
  `ps -A | grep <pkg>`, not just the disable command's own exit status. Separately, the per-user
  `enabled=` field in `dumpsys package <pkg>` reads `0` for the **default** (enabled) state, not
  disabled — `pm list packages -d | grep <pkg>` (present = disabled) is the only reliable check.
  Misreading `enabled=0` as "disabled" cost `projection-raise` round 4 two discarded captures.
- **A persistent WiFi Direct group lets an unintended paired phone rejoin in a few seconds,
  independent of Bluetooth state or the app's own poke logic.** With `wifi-direct-stable-identity=
  true`, a phone that has joined the group before can reconnect via the OS-level P2P framework
  alone — no poke, no Bluetooth activity, nothing in the app's own log until the session is already
  forming. Distinct from the "phone's own reconnect beats our poke" entry below, which is about
  Bluetooth/RFCOMM racing the poke; this one is pure WiFi-Direct-framework rejoin. A run that arms
  one specific phone on D-HU while a second paired phone is present should clear
  `wifi-direct-stable-identity` (to `false`), the `wifi-direct-group-name` / `wifi-direct-last-
  group-ssid` / `wifi-direct-last-group-bssid` / `wifi-direct-group-passphrase` keys, and D-HU's
  OS-level saved P2P groups (`cmd wifip2p init`, `list-saved-groups`, `delete-saved-group <id>` for
  each) first. `projection-raise` round 4 lost two W3r captures to this before finding it.

---

## 7b. Known quirks when a phone is the USB host

The MT50 rig cannot host USB (`host_connected=false`), so every USB round so far has run on a
host-capable phone over wireless adb with the port free. These are that arrangement's quirks, not
the rig's.

- **Two different dialogs look almost identical, and they mean opposite things.** "Open Open
  Headunit to handle <device>?" is the *system chooser*, `systemui.usb.UsbConfirmActivity` or
  `UsbResolverActivity`, and it is the manifest filter's doing. "Allow Open Headunit to access the
  USB device?" is `systemui.usb.UsbPermissionActivity`, raised by the app's own
  `usbManager.requestPermission()`. A brief that says "no dialog" without naming which one has
  measured nothing. Grep for the activity name, never the wording.

- **Two permission prompts per connect are by design; a third is a finding.** USB permission is
  keyed by the *bus path* (`/dev/bus/usb/002/003`), so any re-enumeration drops it. The AOA switch
  re-enumerates the phone from its normal identity to `18D1:2D0x` at a new path, which is a
  different `UsbDevice` with no permission. So: one prompt pre-switch, one post-switch. Anything
  beyond that is either an extra re-enumeration cycle or two of the app's four `requestPermission`
  call sites racing.

  **Counting them costs one grep.** Every call logs at INFO, and the name carries VID:PID:

  ```bash
  adb logcat -v time OPENHU:V '*:S' | grep -E "Requesting USB permission|Usb in accessory mode but no permission"
  ```

  Three *distinct* identities is structural. The same identity twice is a defect. Report the
  identities, not the count.

- **Ticking "always" is sticky per identity, and it will silence a later run.** That writes a
  persistent grant keyed by VID, PID, device class, manufacturer, product and serial, checked
  before the per-bus-path one, so it survives re-enumeration and unplugging. It does **not** carry
  across the AOA switch, because `18D1:4EE1` and `18D1:2D01` are different keys. A round that ticks
  it has changed the rig for every round after it.

- **Resetting it needs an uninstall, which §5 otherwise forbids.** "Clear defaults" in Settings
  clears the default *handler*, not the permission grant, and `pm clear` does not touch it either:
  it lives in system data keyed by uid. Only changing the uid orphans it, and only an uninstall does
  that.

  This is the one sanctioned exception to §5's `adb install -r`, and §5's reason still applies in
  full — a fresh install re-runs the setup wizard and rewrites resolution, DPI and video codec. So
  take it **only** when a round's verdict turns on a clean permission state, never as part of an
  ordinary A/B, and restore afterwards:

  ```bash
  PKG=com.andrerinas.headunitrevived
  adb shell dumpsys usb | grep -i -A4 headunitrevived     # before
  adb shell run-as $PKG cat shared_prefs/settings.xml > settings-backup.xml
  adb uninstall $PKG && adb install <apk>
  adb shell dumpsys usb | grep -i -A4 headunitrevived     # expect empty
  # onboarding has now rewritten resolution, DPI and codec: push settings-backup.xml
  # back and diff it, then re-check those three before any measurement
  ```

  If a round only needs to know **whether** a grant exists rather than to clear one, read it and
  leave it alone. That costs nothing and breaks nothing.

  **Read `dumpsys usb` before any round that judges a dialog**, and put what it said in Setup notes.
  A stale grant or a stale default handler makes "no dialog" mean nothing.

- **A phone host suppresses the chooser for a file-transfer phone, and a head unit does not.**
  AOSP's `UsbProfileGroupSettingsManager.resolveActivity` short-circuits to an MTP notification when
  the device presents a `06/01/01` interface, or `FF/FF/00` named `MTP`, and no default handler is
  set. No chooser appears whatever the manifest filter says. That short-circuit is disabled by
  `FEATURE_AUTOMOTIVE`, so a real head unit takes the chooser path instead. **Any verdict about
  which devices Android offers the app for is host-specific**; say which host it was measured on and
  do not carry it over to the rig.

- **A phone with USB debugging on is the wrong instrument for this.** It exposes an extra `FF/42/01`
  interface and stops being an MTP device by the test above, so it takes the chooser path where a
  normal phone does not. Round 2 read that difference as a regression. Sweep modes on a phone with
  debugging **off**, and capture the descriptor.

- **Capture the descriptor, always.** `adb logcat -v time -s UsbHostManager:D` on the host prints
  the whole `Added device UsbDevice[...]` block. Every USB conclusion so far that turned out wrong
  was one that skipped this. Note that it needs a *second* reader: the OHU capture on a Motorola
  host has to be tag-filtered (`OPENHU:V '*:S'`) or the ROM's own spam rolls the buffer.

- **A USB peripheral can present more than one enumeration and alternate between them.** The dock's
  ethernet controller was measured switching form inside a single replug: composite (`mClass=0`,
  seven interfaces) removed after 2 s and re-added vendor-only (`mClass=255`, one interface). One
  plug proves nothing about a peripheral. Replug until the descriptor stops changing, and report
  every form seen.

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
