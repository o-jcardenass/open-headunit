# native-aa-wireless round 3

**Candidate:** `fork/fix/native-aa-wireless` at `ec7c84f8`, **four commits** on `main` (`80a81099`),
**2264** JVM tests. Round 2's `3e7237d3` is the first three of those four, unchanged.
**Baseline:** `3e7237d3` for R22, and only for R22. Every other run is candidate only.
**Units:** D-SAM (SM-T230, API 19) with D-POCO as the phone for R22 and R24; **D-HU** for R23,
because it is the only rig unit above API 29 that hosts a WiFi Direct group.
**Read first:** `native-aa-wireless-round2-results.md`. This round exists because of a defect inside
its R20, which R20 did not grade.

## What this round is about

R20 PASSed on what it graded, which was the group's name. Two of its three bring-ups also recorded
this, and nobody was asked to look at it:

```
group identity ssid=DIRECT-Eo-Navegadortz3 ... stable=yes nameChanges=19/3
  (same name and same BSSID as the last group)
```

`same name and same BSSID as the last group` is the reason string of the **STABLE** arm. D-SAM
renames its group on every create and its persisted verdict at the end of that round was `RENAMED`,
so a unit that renames every create was graded stable, and the verdict was written to settings.

The cause is a race the round's own timestamps show: in both affected bring-ups the `group identity`
line lands about 340 ms **before** the `already up from before this bring-up` line, so a group-info
callback reached the assessment while the bring-up was still deciding whether to adopt. The
assessment is made once per group, so the later, correct callback never re-asked, and the surviving
group was compared to itself.

**What a wrong `STABLE` costs is the whole point.** It is the value that decides
`AccessPointType STATIC` and the WPP-over-TCP endpoint. On a unit that renames every create, a phone
given both stores a name and a BSSID that the next create replaces, and then loops `NETWORK_NOT_FOUND`
with no Bluetooth fallback. That is the failure this branch was written to stop, on the class of unit
it was written for.

The fourth commit does two things. The assessment now waits for the adopt decision, which closes the
race on every API level. And below API 29, once the rename has been measured, a repeated name and
address no longer promote the verdict: there is no API there that names a group, so nothing could
have made the name come back, and it is that group being seen again.

## Greps, in addition to round 1's §3 and round 2's

```bash
# the verdict on every bring-up, with its reason
grep -nE "group identity ssid=" log.txt
# the adopt decision, to pair with the line above by timestamp
grep -n "already up from before this bring-up" log.txt
# the endpoint, which is what a wrong verdict releases
grep -nE "WifiVersionRequest|wpp_info|endpoint" log.txt
```

```bash
# the persisted verdict and count, app stopped
adb shell run-as com.andrerinas.headunitrevived cat shared_prefs/settings.xml \
  | grep -oE 'wifi-direct-(last-identity-verdict|group-name-changes)[^/]*'
```

### R22: a group that was only read is not graded as one that came back

D-SAM, candidate, with `3e7237d3` as the baseline arm. **The point of this round.**

Start from a known state: with the app stopped, read `wifi-direct-group-name-changes` and confirm it
is at or above 3, which it will be on this unit. Do not reset it; its absolute value is not what this
grades.

1. Baseline arm, `3e7237d3`. Bring Native AA up, then force-stop and relaunch three times, no exit
   between, exactly as R20 did. Quote every `group identity` line with its timestamp, and every
   `already up from before this bring-up` line with its timestamp. Read the persisted verdict after.

- **Expected, and not a FAIL**: at least one bring-up reads `stable=yes` with the reason
  `same name and same BSSID as the last group`. This arm exists to show the defect is real on this
  unit rather than a reading of one capture, so a baseline that reproduces it is what makes the
  candidate arm mean something.
- **INCONCLUSIVE** if all three read `stable=no`: the race did not fall that way this time. Say so,
  rerun the arm twice more, and if it still will not reproduce, grade the candidate arm alone and
  record that the baseline could not be made to show it.

2. Candidate arm, `ec7c84f8`. Same three force-stop relaunches.

- **PASS**: every `group identity` line reads `stable=no`, and `wifi-direct-last-identity-verdict`
  is `RENAMED` at the end. The reason on a bring-up that adopted will be either
  `this group was already up and was read rather than created` or
  `same name and same BSSID, but this platform has named the group itself on N creates`; both are
  correct and which one appears depends on which callback arrives first, so do not grade on it.
- **FAIL**: any `stable=yes`, or a persisted verdict of `STABLE`, on a unit whose count is at or
  above 3.

3. Then, on the candidate, let a handshake run to a session on one of those adopted groups. D-SAM
   auto-connects unattended, so this needs no action beyond leaving it alone.

- **PASS**: no endpoint goes out. The handshake logs a withholding whose reason names the platform
  naming the group, and `Providing credentials to listener` reads `identity stable=no`.
- **FAIL**: an advertised endpoint, or credentials delivered with a stable identity, on this unit.
  This is the check that matters most in the round: it is the phone-side damage the verdict exists
  to prevent.

### R23: the same question above the naming API

D-HU, candidate only. The race is not below-Q specific; only the second half of the fix is. Above Q
the app names the group, so a bring-up that meets a group under the name it was about to ask for
adopts it, and the same stray callback can grade it stable against itself.

1. Bring Native AA up and let a group form. Note its name and BSSID. Then force-stop and relaunch
   three times with no exit between, quoting each `group identity` line and each
   `already up from before this bring-up` line with timestamps.

- **PASS**: no bring-up that adopted a group logs a verdict it did not already hold. An adopted
  bring-up reads the `already up and was read rather than created` reason and carries the verdict
  forward unchanged, whatever that verdict is on this unit.
- **FAIL**: a bring-up that adopted a group logs `stable=yes` with the reason
  `same name and same BSSID as the last group`, which is the self-comparison this fix removes.
- **INCONCLUSIVE**: D-HU's verdict was already `STABLE` before the round, in which case a `stable=yes`
  says nothing. Read the persisted verdict before starting and say what it was; if it is `STABLE`,
  the run cannot separate the two and the arm should be recorded as not measurable on this unit
  rather than passed.

### R24: what a deep-link exit does with no session live

D-SAM, candidate. Round 2's R20 part 2 found `headunit://exit` stopping the service without the
group going away, where round 1's R13 on D-HU saw it removed within 3 s. The explanation to test is
that the teardown is owed only when a session is live: `ServiceStopWaitPolicy.waitsForWirelessTeardown`
is `sessionConnected && wirelessLauncherActive`, so an exit with nothing connected waits for no
teardown and removes no network.

1. Bring Native AA up and let the group form, but do not let a session form. Keep the phone away, or
   turn its Bluetooth off before arming. Confirm no `SSL handshake complete` in the capture. Then
   send `headunit://exit` and poll `dumpsys wifip2p` for 10 s.

- **Expected**: the group stays up. Quote the poll.

2. Then bring it up again, let a session form this time, confirm `SSL handshake complete`, and send
   the same deep link. Poll the same way.

- **Expected**: the group goes away, as R13 measured.

- **PASS** is either result, as long as both halves are quoted: this run is here to name the
  condition rather than to grade a fix, and whichever way it falls is written up as the answer to a
  divergence that is currently unexplained. **INCONCLUSIVE** only if a session forms in step 1
  despite the precaution, which makes the two steps the same test.

## A note for whoever maintains the rig scripts

Round 2 found `forget_car_gearhead.sh` failing with `could not find node with text="Additional
settings in the app"` because the Gearhead app-info intent was delivered to a stuck
`Settings$ConnectedDeviceDashboardActivity` task from an earlier round. `KEYCODE_HOME` did not clear
it; `am force-stop com.android.settings` did. Doing that before the first dump would make the script
survive that state. Round 2 also found two "Google" vehicle entries rather than one, so a script that
forgets the first and stops leaves the other behind.
