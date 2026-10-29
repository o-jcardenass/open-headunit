# Round 1 brief, is Android Auto's IStartup binder callable on this phone

Read `TESTING-TEMPLATE.md` first. New thread, cites no prior round.

**This round is unlike every other one in this channel.** Nothing is built for the head unit, no
head unit is involved, no projection session is formed and nothing is graded about our app. It
installs one debug APK on each of the two rig phones, fires one broadcast at each, and reads one
log line. Budget is minutes, not a session. It is here rather than done on the PC because the
answer is a property of each handset and cannot be read from any APK.

---

## 1. Build

**This is a different repository.** The candidate is the phone-side Wireless Helper, not
`open-headunit`.

**Candidate:** `probe/istartup-binder` @ `6699862` on `o-jcardenass/wireless-helper`, one commit on
that repo's `upstream/main` @ `8ac36c9`. New branch, pushed once, no history rewritten.

```bash
git clone https://github.com/o-jcardenass/wireless-helper.git   # or fetch, if already present
cd wireless-helper && git checkout -B probe/istartup-binder origin/probe/istartup-binder
echo "sdk.dir=$HOME/Android/Sdk" > local.properties              # if absent
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug
```

Output is `app/build/outputs/apk/debug/com.andrerinas.wirelesshelper_1.9.4-debug.apk`. It built
clean on the PC on 2026-09-21, so a failure here is an environment difference worth reporting
rather than working around.

**No baseline.** There is nothing to compare against: each run's verdict is one of two log lines
and neither depends on our code being right.

**No `run_unit_tests.sh` / `build_hur.sh` gate.** Those are the head unit's scripts and this round
does not build the head unit. If either is run by habit it proves nothing about this candidate.

### Install discipline

The debug APK carries `applicationIdSuffix ".debug"`, so its package is
`com.andrerinas.wirelesshelper.debug` and it installs **beside** any release Wireless Helper rather
than replacing it. Do not uninstall the release one. Uninstall the `.debug` package when the round
is done.

---

## 2. What this is

Since Android Auto 17.4 no app can trigger a wireless session: `WirelessStartupActivity` went
`exported="false"` and `WirelessStartupReceiver` ships `enabled="false"` and cannot be enabled even
from shell. That is what killed the Wireless Helper, and it is why the only Bluetooth-free way into
a session today is Android Auto's developer "Start head unit server" toggle on `:5277`, which a
human has to tap, dies on reboot and wedges after a bad connection.

One thing found in the 17.5 and 17.8 teardowns could replace that toggle.
`GearheadCarStartupService` is `exported="true"`, `enabled="true"`, `process=":car"` and carries
**no permission**. Its binder is the stub for `com.google.android.gms.car.startup.IStartup` and
reaches `ICar` then `IConnectionController.startDuplexConnection`, which is the same file-descriptor
primitive the `:5277` server uses internally. If it is callable, a phone-side app can hand Android
Auto one end of a socket pair and relay the other to a head unit over WiFi, with no Bluetooth and no
toggle.

The catch, and the whole reason for this round: `onTransact` runs a `GoogleSignatureVerifier` check
that throws `Caller is not Google-signed.`, and that check is skipped when the Phenotype flag
`GearheadCarService__startup_proxy_signature_check_enabled` is false. It reads compiled default
**false** in both builds, but Phenotype flags are pushed from Google's servers per device and per
cohort, so **the compiled default is not what a given handset is running**. Nothing in the APK
answers it. One transaction does.

**Why two phones and not one.** D-POCO runs LineageOS and D-MOTO runs stock Motorola. Flag cohorts
follow the device and its Google account, so the two can legitimately disagree, and a disagreement
is a more useful result than either answer alone. Do not stop after the first phone.

---

## 3. Runs

### P1, the probe, on D-POCO and D-MOTO

Run the identical sequence on each phone and report them as P1-POCO and P1-MOTO.

```bash
S=<serial>
adb -s $S install -r app/build/outputs/apk/debug/com.andrerinas.wirelesshelper_1.9.4-debug.apk
adb -s $S shell dumpsys package com.google.android.projection.gearhead | grep -m1 versionName
adb -s $S logcat -c
adb -s $S shell am broadcast \
  -a com.andrerinas.wirelesshelper.PROBE_ISTARTUP \
  -n com.andrerinas.wirelesshelper.debug/com.andrerinas.wirelesshelper.probe.IStartupProbeReceiver
sleep 3
adb -s $S logcat -d -s HUREV_PROBE
```

Record the Gearhead `versionName` with each verdict; it is part of the answer.

| Log line | Verdict |
|---|---|
| `VERDICT: OPEN. descriptor=com.google.android.gms.car.startup.IStartup` | **PASS.** The flag is off on this handset and the route is open. |
| `VERDICT: CLOSED. Caller is not Google-signed.` | **PASS.** The flag is on here. A clean, conclusive negative, not a FAIL. |
| `VERDICT: OPEN, but the descriptor moved. …` | **PASS**, and quote the actual descriptor verbatim. |
| `VERDICT: could not bind …` | **INCONCLUSIVE.** Android Auto missing, the component gone, or package visibility refused. Says nothing about the flag. |
| `VERDICT: bind never connected within 10000ms` | Retry once. If it repeats, **INCONCLUSIVE**. |
| `VERDICT: inconclusive, …` | **INCONCLUSIVE.** Quote the exception class and message verbatim. |
| no `HUREV_PROBE` line at all | **INCONCLUSIVE.** Confirm the package installed and the broadcast was accepted, then report. |

**Both OPEN and CLOSED are a PASS**, because the round's job is to get an answer, not a particular
one. Only a run that fails to produce either is a problem, and it is INCONCLUSIVE rather than FAIL.

### P2, the deep arm, D-POCO only, and only if P1-POCO read OPEN

Adds `--ez deep true` to the same broadcast. It dispatches one real transaction instead of only the
descriptor query, which confirms the gate covers the dispatch and not just the descriptor.

```bash
adb -s 4f4027e9 logcat -c
adb -s 4f4027e9 shell am broadcast --ez deep true \
  -a com.andrerinas.wirelesshelper.PROBE_ISTARTUP \
  -n com.andrerinas.wirelesshelper.debug/com.andrerinas.wirelesshelper.probe.IStartupProbeReceiver
sleep 3
adb -s 4f4027e9 logcat -d -s HUREV_PROBE
```

Report the `deep:` line verbatim. Any of the three is a **PASS**; the arm exists to be informative,
not to be green. **Skip it entirely on D-MOTO**: that transaction's meaning is not known, D-MOTO is
the less expendable phone, and the descriptor answer is the one the decision needs.

If Android Auto misbehaves on D-POCO afterwards, clear its data
(`adb -s 4f4027e9 shell pm clear com.google.android.projection.gearhead`) and note it. That is a
known recovery on this phone from the `native-aa-wireless` round 4 finding, not a new fault.

---

## 4. What this round does not measure

- **Nothing about the head unit.** No session, no video, no head-unit build. A green here is not a
  statement that anything projects.
- **Nothing durable.** A flag Google pushes can change next week. The result dates itself, and that
  is expected; it is a snapshot used to decide whether to build, not a guarantee.
- **The `:5277` route.** Unaffected either way and already known to work with the toggle on.

## 5. What it decides

OPEN on either phone unlocks the next stage: the Wireless Helper gains a session source that tries
the file-descriptor handoff first, `127.0.0.1:5277` second, and today's `WirelessStartupActivity`
intent last, giving a phone-hotspot topology with no Bluetooth anywhere on either side. CLOSED on
both leaves `:5277` as the only Bluetooth-free route, which still works but keeps the manual toggle,
and the next stage ships the fallback chain without its first link.

## 6. Reporting

`TESTING-TEMPLATE.md` §7 format, file `istartup-binder-probe-round1-results.md`. Quote every
`HUREV_PROBE` line verbatim rather than summarising it, and give each phone's Gearhead `versionName`
and Android version in the header. No captures to upload: the evidence is a handful of log lines and
belongs inline in the results file.
