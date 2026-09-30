# Standing template — how a round is briefed, run and reported

This is the reusable half of the channel. A round's brief carries only what is specific to its
branch; everything below applies to every round and is not restated in briefs.

The rules here are not style preferences. Each one exists because ignoring it once produced a false
result that cost hours to unwind.

---

## House rules — the short version

Eight standing rules. Everything after this section is detail on how to follow them.

1. **Use the rig's existing scripts.** `hur-wifi-test-scripts/` already has `build_hur.sh`,
   `run_unit_tests.sh` and others for building, installing and driving the app. Look there first,
   use what fits, and only write a new script when nothing does — then leave it in that folder for
   the next round. §5.
2. **Every action on the app goes through `AutomationReceiver`. No `input tap` on the app, ever.**
   Connect, disconnect, exit, the pill's X, the WiFi and USB buttons, a poke, a marker: each is an
   `am broadcast` verb (§3). A brief that says "tap" for something a verb does is a brief bug; send
   the verb and note it in Setup notes. A step with no verb is UNTESTABLE unless the brief names it
   a hand step and says why no verb exists. §0, §3.
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
8. **Never run adb calls in parallel against one unit.** An unthrottled parallel
   `adb exec-out screencap` loop froze D-HU hard enough to need a physical power cycle. Sequential,
   with a `sleep 0.3` between calls, is the proven method. §7a.

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

The same applies to acting *during* a run. The app's command surface is
`com.andrerinas.openheadunit.automation.AutomationReceiver` (§3), and it is the only way a run acts
on the app. A tap is unrecorded, unrepeatable and mistimed; a verb prints
`AutomationReceiver: <action>` at INFO with a timestamp, so the log proves it landed. **Do not reach
for `uiautomator dump` plus `input tap` to drive the app**, even when a brief seems to ask for it:
check §3's verb table first. `input keyevent` for BACK and the media keys is scripted and allowed.

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

**`set_hu_prefs.sh`'s `del` is line-scoped and corrupts a multi-line `<set>` key.** It removes only
the opening `<set name="...">` line and orphans the inner `<string>` and the closing `</set>`, which
is invalid XML and can drop the whole file back to defaults on the next load. Once a `<set>` key may
hold a real value, rebuild the file on the host and `adb push` it back, restoring owner and mode,
rather than deleting in place.

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

## 3. Driving the app: `AutomationReceiver`

**Every action a run takes on the app is a broadcast to
`com.andrerinas.openheadunit.automation.AutomationReceiver`** (on `main`, source in
`app/src/main/java/com/andrerinas/openheadunit/automation/`). No root, no screen, no `input tap`.
Define the helper once per shell and use it for every step:

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }

send ACTION_QUERY_STATE                       # reply on data=, carries the build's commit
send ACTION_LOG_MARKER --es text R4-start     # AutomationMarker: R4-start at WARN
```

Both flags are required. `-n` (or `-p $PKG`) names the package, without which Android 14 skips the
broadcast at enqueue as a background execution while `am` still prints `result=0`.
`-f 0x00000020` is `FLAG_INCLUDE_STOPPED_PACKAGES`: after `am force-stop` the package is stopped and
Android drops every broadcast to it, explicit component or not. **Every verb logs
`AutomationReceiver: <action>` at INFO; a step with no such line never landed and the run is void,
not a FAIL.** The reply (`ok`, and `error` on a refusal) is on `am`'s `data=` line: record it.

### The verbs, and the UI control each one replaces

| Verb (`send ...`) | Replaces | Notes |
|---|---|---|
| `ACTION_START_WIRELESS_SCAN` | the home screen's **WiFi button** | clears the user-exit latch, lifts the pill's X (`userRequested`) |
| `ACTION_START_WIRELESS [--ez no_ui true]` | saving a wireless setting | headless arm, no `MainActivity`; lifts the X too |
| `ACTION_CANCEL_WIRELESS` | the **status pill's X** | the service reads the transport itself: USB attempt, USB session or USB stage takes the USB branch |
| `ACTION_CHECK_USB` / `ACTION_CONNECT` | the **USB button** | sends `user_requested`, so it lifts the X's USB hold |
| `ACTION_CONNECT --es ip <addr> [--ez no_ui true]` | typing an address | TCP to `:5277` |
| `ACTION_DISCONNECT` | Exit on the projection | the `isUserExit` path |
| `ACTION_EXIT` / `ACTION_STOP_SERVICE` | Exit from the home screen | stops the service |
| `ACTION_STOP_WIRELESS` | | stops the launcher, no latch |
| `ACTION_END_SESSION_STAY_ARMED` | | ends the session, keeps the network |
| `ACTION_NATIVE_AA_POKE --es extra_mac <MAC>` | the manual poke | reaches `selectDriver`, not `HomeFragment`: no pill, no `Connecting to Native-AA device:` |
| `ACTION_NATIVE_AA_CANCEL_POKE` | | |
| `ACTION_NEARBY_CONNECT --es extra_endpoint_id <id>` | picking a Nearby endpoint | |
| `ACTION_START_SELF_MODE [--ez no_ui true]` | the Self Mode button | |
| `ACTION_SET_NIGHT_MODE --es state day\|night\|auto` | | |
| `ACTION_RAISE_PROJECTION` | bringing the projection forward | |
| `ACTION_RESTART_AUDIO`, `ACTION_REFRESH_SENSORS` | | |
| `ACTION_QUERY_STATE` | | APK identity: compare `commit` to the brief's SHA |
| `ACTION_LOG_MARKER --es text <label>` | | stamp a step or a heard fault |
| `ACTION_EXPORT_LOG`, `ACTION_SET_LOG_LEVEL --es level <l>`, `ACTION_START/STOP_LOG_CAPTURE` | the log screen | gated, below |
| `ACTION_SET/GET/RESET_SETTINGS` | | gated; settings still go in `settings.xml` (§1) |

The last two rows are refused unless `allow-external-configuration` is `true`; the control verbs
are not gated. The authoritative list is `AutomationCommandPolicy.effectsFor` on the branch under
test: grep it before assuming a verb exists on an older build.

**Where a verb differs from the control it replaces, the brief says so**, and the log shows it: the
pill's X by broadcast skips `MainActivity: status pill X pressed`, and the USB verb lifts the hold
with `a USB connection was asked for` rather than `the USB button was pressed`. Grade the service's
lines, which are the same either way.

### What is not a verb, and what to use instead

- **Launch the app:** `adb shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`.
  Only when a run needs `MainActivity` in front; a headless run uses `ACTION_START_WIRELESS`.
- **Hard stop:** `adb shell am force-stop $PKG`.
- **Open the settings screen:**
  `adb shell am start -n $PKG/com.andrerinas.openheadunit.main.SettingsActivity --ei extra_destination 0`.
  **Close it:** `adb shell input keyevent 4` (BACK). A keyevent is scripted and timestamped; it is not
  a tap.
- **Deep links** (`headunit://connect|disconnect|exit|nightmode`) reach the same commands through
  `AutomationActivity`, which is an activity and may bring the app forward. Use the broadcast unless
  a run is grading the deep link itself.

**`ACTION_CONNECT` with no `ip` is the USB check, and a no-op under Native AA.** To re-arm Native AA
after an exit, send `ACTION_START_WIRELESS_SCAN`; cycling the phone's Bluetooth is only for runs that
grade the Bluetooth auto-start path itself (`MATCH! Starting AapService`).

**A tap on the app is never the fallback.** If a step genuinely has no verb (a banner's dismiss
button, a dialog), the brief must name it a hand step and say why; if it does not, the run is
UNTESTABLE and Setup notes say which control had no verb. Taps into the *projected video*, which go
to the phone over the touch channel, are the one standing exception (§7a).

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

**The captures do not come with it.** This branch is markdown only. Zip the round's screenshots, logs
and traces, upload them as an asset on the fork, and cite the release, the asset filename and a
sha256 in the results file instead of a path.

**One release per thread, one asset per round.** The release is `rig-evidence-<topic>` and it is
created once, on that thread's first round; every later round adds an asset to the same release. A
release per round grew the tag list by about three a day, and git has one flat tag namespace, so
evidence anchors pile up in the same list as the hundred-odd `v.*` version tags and make them hard to
find. Nothing is ever overwritten, because the round number is in the filename, which is the rule
that matters: an asset replaced in place with the same name cost a round once.

```bash
zip -1 -r <topic>-round<N>-captures.zip <your capture dir>
sha256sum <topic>-round<N>-captures.zip
# first round of a thread only
gh release create rig-evidence-<topic> --repo o-jcardenass/open-headunit \
  --notes "<topic> rig captures, one asset per round" <topic>-round<N>-captures.zip
# every round after that
gh release upload rig-evidence-<topic> --repo o-jcardenass/open-headunit \
  <topic>-round<N>-captures.zip
```

**When a thread closes, its release is folded into `rig-evidence-legacy` and the per-thread release
is deleted.** There is one archive release and it never gains a date suffix, so the fork's tag list
carries at most the live threads plus that one. Download each asset, verify its sha256 against what
the results file quotes, upload it under the same filename, re-download and verify again, and only
then `gh release delete <tag> --cleanup-tag --yes`. Record the move in
`archive/evidence-manifest-20260921.tsv`, which maps every swept release tag to the asset now
holding it. The 2026-09-18 sweep's own index stays at `archive/evidence-manifest-20260918.tsv`.

**Releases from before 2026-09-21 were one per round** (`rig-evidence-<topic>-round<N>`). None
survive: a results file that cites one is answered by the manifest above, by asset filename.

A capture staged on a `transfer/` branch is refused by the commit guard. So anything a verdict rests
on belongs in the results file as a quoted line with its timestamp: the file has to stand on its own,
and the asset is only for re-examining something later.

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

The quirks live in `rig-quirks/`, one file per unit and one per topic, so a round reads only what it
touches instead of the whole list. **Unless a quirk names a unit, it was measured on D-HU.** A brief
will still say when a quirk changes a run. To plan a round, read:

1. `rig-quirks/topics/tooling.md`, always;
2. `rig-quirks/units/<unit>.md` for every unit the brief names (D-HU is in nearly every round), plus
   `rig-quirks/units/D-SAM-and-D-HP.md` for either tablet;
3. each topic file whose area a run touches. When unsure, `grep -il <term> rig-quirks/topics/*.md`.

| File | Scope |
|---|---|
| `rig-quirks/topics/audio.md` | Focus latches, VLC, AudioFlinger, media keys, the audio sink, Gemini sessions. |
| `rig-quirks/topics/bt.md` | Hands-free and A2DP links, poke targets and verdicts, adapter self-reverts, the external module route. |
| `rig-quirks/topics/gearhead.md` | Its dialogs, cached records, data clears, string drift, GPS. |
| `rig-quirks/topics/lifecycle.md` | Force-stop and receivers, exit and disconnect intents, auto-start mirrors, reboot and ACC wake. |
| `rig-quirks/topics/projection.md` | Proving projection, the status pill, rotation and night mode, fault injection, the connection-issue banner. |
| `rig-quirks/topics/tooling.md` | Read for every round: adb and shell traps, build and install, settings.xml, markers, grep, fresh installs. |
| `rig-quirks/topics/wifi.md` | Station radio, P2P groups, the hotspot transport, WPP status lines, airplane mode. |
| `rig-quirks/units/D-HP.md` | The HP Slate 7 Plus (API 17). |
| `rig-quirks/units/D-HU.md` | The MT50 head unit. Read for every round that uses it, which is nearly all of them. |
| `rig-quirks/units/D-MOTO.md` | The Motorola phone. |
| `rig-quirks/units/D-POCO.md` | The POCO phone, and when it stands in as the head unit. |
| `rig-quirks/units/D-SAM-and-D-HP.md` | Read with either tablet. |
| `rig-quirks/units/D-SAM.md` | The SM-T230 tablet (API 19). |

A new quirk goes in the file it belongs to, never back into this section.

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
   If any run stamps `ACTION_LOG_MARKER` and the candidate still lists it in
   `AutomationCommandPolicy.CONFIGURING`, add `allow-external-configuration` = `true` to this table
   (current `main` does not gate it; grep the candidate).
5. **Every action as an `AutomationReceiver` verb** (§3), written as the `send ...` line to paste.
   Never write "tap" or `input tap` for anything on the app. If a step has no verb, either add one
   on the branch under test or name the step a hand step with the reason; a brief that leaves the
   tester to improvise a tap is not ready to push.
6. **The lines that decide every run** — copied verbatim from the source, not from memory. Verify
   them with `grep -F` against the branch before committing the brief.
7. **Runs**, each with an id, the exact setup, and explicit PASS / FAIL conditions. Mark which one is
   the point of the round.
8. **Do not re-run** — settled runs, so the round is not spent re-proving them.
9. **Report back** — the two or three numbers that actually decide the shipping question.

**Written for a Haiku executor (2026-09-25).** On the rig a Haiku agent runs each run and a
Sonnet host grades it from what Haiku returns (`CLAUDE.md`, Subagent model routing). A brief works
for that split when:

- every step of a run is a paste-ready command (`send ...`, a script with its arguments, a
  `sleep`), in order, with nothing left to infer; a step that needs judgement mid-run ("if the
  session looks stuck, ...") names the exact observable and the exact command for each branch;
- every run names the markers that bound it and the greps that decide it, each with the file it
  runs on (part 6 already asks for the lines verbatim; add the file and the marker window);
- every PASS / FAIL condition is a comparison over those counts and timestamps ("`Client list
  empty` to the candidate's line within 5 s, for every hit"), never a reading of the log as a
  whole, so Sonnet can grade from the Haiku extract alone;
- the round's stop rule is a count too ("stop after the 3rd cycle that hits the trigger").

A condition that can only be judged by reading the capture end to end is still allowed, but say
so in the run: the host then reads the named window itself rather than trusting an extract.

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
