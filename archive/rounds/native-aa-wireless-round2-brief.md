# native-aa-wireless round 2

**Candidate:** `fork/fix/native-aa-wireless` at `3e7237d3`, still **three commits** on `main`
(`80a81099`), now **2261** JVM tests. Round 1's `4fc4753a3` no longer resolves: two later fixes were
folded into the commits they belong to and tree equality was verified (`a863b0d2` on both the
compacted tip and the pre-compaction tip `017d30ba`, preserved on `fork` as
`pre-compaction/native-aa-wireless-20260921`), so the code round 1 measured is still all here, under
different commit boundaries, plus the two fixes below.
**Units:** D-SAM (SM-T230, API 19) for R18 to R20, with D-POCO as the phone, and **D-HP** (API 17)
for R21 alone. **Not D-HU**: every run here is about a unit whose Android is too old to name its own
WiFi Direct group, or to publish a hands-free profile of its own.
**Read first:** `native-aa-wireless-round1-results.md`. Round 2 repeats none of its seventeen runs.

## What is new since round 1, and what it does to R16

Two field reports on Android 4.2.2 head units drove both fixes.

**The stand-in Hands-Free record is asked for three times rather than once**, 1.5 s apart, and a unit
that still refuses raises `HANDS_FREE_RECORD_REFUSED` on the home screen. One reporter's unit refuses
it in every capture it has ever sent, about 20 ms after the Android Auto record registered, and the
app answered that with a single error line and nothing else.

**A group already up is now read rather than recreated below API 29 too.** The reuse path round 1
graded compared the live group against the name the app would ask for, and below Q the app never gets
to ask for one, so it never matched on exactly the units where every create mints a new name.

**That supersedes R16 part 2's expectation and the results should say so.** R16 measured the counter
walking 9→10→11→12 across four force-stop bring-ups, which was the correct reading of the old
behaviour. On this candidate those bring-ups should meet the group they left up and keep its name, so
**the counter is expected to stop moving**. That is R20 passing, not R16 regressing. The verdict stays
at the platform-names-it condition either way and the endpoint stays withheld, which R16 confirmed
4/4 and which must remain true.

## Greps, in addition to round 1's §3

```bash
# the record, and the retry
grep -nE "gets the stand-in HFP record|refused the stand-in Hands-Free record|would not publish a Hands-Free record|HFP Server socket error" log.txt
# the banner
grep -n "showing the connection issue banner for HANDS_FREE_RECORD_REFUSED" log.txt
```

```bash
# the stamp, app stopped
adb shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml \
  | grep -o 'connection-issue-hands-free-record-refused[^/]*'
```

### R18: the record registers, and the banner it would raise

D-SAM.

1. Over the whole round, with the stamp absent at the start.

- **PASS**: every arming logs `gets the stand-in HFP record` and no `refused the stand-in` line, no
  `would not publish a Hands-Free record` line, and `connection-issue-hands-free-record-refused` is
  still absent or 0 at the end. The record registering first time is the expected result on both
  units; this run exists so a retry loop that fires when it should not is caught.
- **FAIL**: a refusal line on a unit that registered the record before this commit, or a stamp
  written on a unit whose record registered.

2. The banner, driven by hand because the condition cannot be produced. App stopped, write
   `connection-issue-hands-free-record-refused` to a recent epoch millisecond value, clear
   `connection-issue-dismissed`, launch the app and stay on the home screen. Native AA mode, wireless
   selected.

- **PASS**: the banner appears, its text names the hands-free record and says the app keeps asking,
  tapping it opens nothing, and the dismiss control hides it. Screenshot it.
- **FAIL**: no banner with the stamp set and the mode selected, or a tap that lands the user on a
  settings row, since no row changes this unit's Bluetooth stack's answer.

3. Then, with the stamp still set, arm Native AA once and let the record register.

- **PASS**: the stamp goes to 0 on that arming and the banner is gone on the next launch.
- **FAIL**: the stamp survives a registration that succeeded. That is the shape the stale-endpoint
  banner was found stuck in after round 5, and this is the check that it was not repeated here.

### R19: what the phone keeps, on a unit that renames its network

D-SAM and D-POCO. **The point of this run.** Below Q the platform names the group and D-SAM has been
measured taking a different name on every bring-up. On `main` the credentials always claim
`AccessPointType STATIC`, whatever the identity verdict says, which asks the phone to persist a
network name that will not exist next time; on the candidate field 5 follows the verdict, so a
`RENAMED` unit announces `DYNAMIC`.

**Read it on the phone, not in our log.** Gearhead 17.8 logs a generic non-discriminating line for
both values, so the instrument is what the phone kept:

```bash
adb -s <phone> shell cmd wifi list-networks | grep -i DIRECT
```

1. Baseline, `main` at `80a81099`. On the phone, forget the head unit in Android Auto and delete every
   saved `DIRECT-` network, then run four Native AA bring-ups on D-SAM with a handshake each, reading
   the phone's list after each one.
2. Candidate, `3e7237d3`, same preparation on the phone, same four bring-ups.

- **PASS**: the baseline's list grows with a new `DIRECT-` entry per differently named group, and the
  candidate's does not grow at all. Quote both lists in full after each bring-up, and quote the
  `group identity` line beside each so the verdict and the phone's answer line up.
- **FAIL**: the candidate persists a network on a bring-up whose verdict was not `STABLE`.
- **INCONCLUSIVE**, and say which: the phone refuses `cmd wifi list-networks`, or D-SAM happens to
  repeat its group name across all four bring-ups, which would leave the verdict `STABLE` and the run
  measuring nothing.

Whatever this measures, it is the first direct read of what field 5 does to a phone, and a field
report on a renaming Android 4.2.2 unit is waiting on it.

### R20: a surviving group is read rather than recreated, below Q

D-SAM, which renames its group on every bring-up exactly as the field unit does.

**Why.** The reuse path added earlier on this branch compared the live group's name against the name
the app would ask for, and below API 29 the app never gets to ask for one: the platform names the
group. So on the one class of unit that cannot afford a recreate, the check never matched and every
bring-up minted a new name the phone had to be told about over Bluetooth again. It now reads any
group this unit owns when it cannot name one itself.

**This does not make such a unit `STABLE`**, and the run should not be graded as though it does: the
branch's own `readNotCreated` arm deliberately refuses to promote a verdict from a group it only
read. What is being graded is how often the name moves.

1. Bring Native AA up, note the group name, then force-stop the app and bring it up again, three
   times. Do not exit through the app, since a user exit removes the network by design.

- **PASS**: the second and third bring-ups log `a group named DIRECT-... is already up from before
  this bring-up; reading it instead of tearing it down` and carry **the same SSID** as the first,
  and `nameChanges` does not move across them. Quote all three `group identity` lines.
- **FAIL**: a new SSID on a bring-up that met a group already up, which is the behaviour this run
  exists to remove.

2. Then exit through the app and bring it up once more.

- **PASS**: the network is gone and a new group is created, with a new name. Removing it on a user
  exit is deliberate and this run must not read that as a regression.


### R21: does a second Android 4.2.2 tablet publish the stand-in record at all

**D-HP** (HP Slate 7 Plus, 4.2.2, API 17), phone D-POCO. The one run in this round that is not on
D-SAM, and the only one that can measure the thing R18 cannot.

**Why this unit, which has no WiFi Direct.** The refusal the retry exists for has been seen on
exactly one unit, a reporter's MTK board, and nothing in the rig reproduces it. D-HP is a second
Android 4.2.2 tablet with no hands-free profile of its own, so it is the closest thing available to
that reporter's hardware. Whichever way it answers is worth having: if it publishes the record
cleanly, the refusal is that vendor's Bluetooth stack rather than something common to 4.2.2, and the
retry is aimed at a rare quirk; if it refuses too, the retry and the banner matter a great deal more
than currently assumed.

**This contradicts a standing note in `TESTING-TEMPLATE.md` §7a**, which says the stand-in record is
unreachable on D-HP. That note's reason is that D-HP is run in Headunit Server mode, not that the
code gates it: in `WifiLauncherNative.start()` the `handshakeManager?.start()` call sits outside the
transport branch, and `WifiDirectManager` is constructed whether or not the device reports
`FEATURE_WIFI_DIRECT`. **Step 1 tests that claim**, and if it turns out to be right the run is
INCONCLUSIVE rather than a FAIL, and the note stands and should be given its reason.

**Nothing else about this unit is being graded.** It has no WiFi Direct and no hotspot, so there are
no credentials to hand over and no session can form. The bring-up is expected to get as far as the
Bluetooth listeners and stop. Do not read that as a failure, and do not leave this unit in Native
mode afterwards.

1. With the app stopped, set `wifi-connection-mode` to `3` and bring Native AA up. Read the startup
   lines.

- **The run is live** if the log carries `NativeAA: Starting Bluetooth Handshake Servers` and
  `NativeAA: ACTIVELY LISTENING on Android Auto UUID`.
- **INCONCLUSIVE** if neither appears: the note in the template was right for a reason nobody had
  written down, and what is needed is the line that shows where the bring-up stopped instead.
- **INCONCLUSIVE, differently**, if the log reads `radio already advertises Hands-Free — stand-in
  HFP not registered`: this unit has its own profile, so the stand-in never applies and it cannot
  stand in for the reporter's. Quote the line either way.

2. With `gets the stand-in HFP record` in the log, read what followed.

- **PASS, and the more likely answer**: no refusal line, and
  `connection-issue-hands-free-record-refused` absent from `settings.xml`. Record that a second
  4.2.2 tablet publishes the record first time.
- **PASS, and the answer worth having**: `refused the stand-in Hands-Free record ...; asking again in
  1500ms` appears, one or two of them, and then either the record registers on a later attempt or
  `would not publish a Hands-Free record in 3 attempts` is logged and the banner appears on the home
  screen by itself. Screenshot it. **This is the only end-to-end test of the new code that exists**,
  and it replaces R18's hand-driven stamp if it happens.
- **FAIL**: a refusal with no retry line after it, or three refusals with no banner.

3. Restore `wifi-connection-mode` to what this unit had before, app stopped.
