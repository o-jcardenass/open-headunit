# check-for-updates, round 4 brief: the update request on the bundled Conscrypt

## 1. Build and baseline

| | Where | SHA | Gate |
|---|---|---|---|
| Candidate | fork, branch `fix/update-check-old-tls` | `b77bf005` | compiles; 2404 JVM tests, 0 failures |
| Baseline | none built. Rounds 2 and 3 are the baseline (`8f88d9a6` plus the log-call change, APK md5 `3145575b01ad5dbbe6f20c6cfbc6a550`) | | |

The candidate is one commit on top of the owner's `checkforupdate` @ `8f88d9a6`. **It already
contains round 2's three-line log-call change**, so throw away the uncommitted copy of it first or
the checkout will refuse:

```bash
git checkout -- app/src/main/java/com/andrerinas/openheadunit/utils/UpdateChecker.kt
git fetch https://github.com/o-jcardenass/open-headunit.git fix/update-check-old-tls
git checkout b77bf005
git log --oneline 8f88d9a6..HEAD      # exactly 1: "Update check: use the bundled Conscrypt below API 21"
git status --short                    # empty
```

Build with `build_hur.sh`. Record the md5 from a real `adb pull` on every unit; it must differ from
`3145575b...`. Then check identity by symbol, because versionName does not move (`3.5.0-beta2`, 115):

```bash
unzip -p cand.apk 'classes*.dex' | strings | grep -cF 'no bundled HTTPS socket factory'   # >= 1 on the candidate, 0 on rounds 1-3
unzip -p cand.apk 'classes*.dex' | strings | grep -cF 'ModernTlsSocketFactory'            # >= 1
```

Install with `adb install -r` only. D-SAM: `install_and_launch.sh` with `HU=30041c35642d2200`.
D-HP and D-HU: `install_all.sh` as round 2 did, or `adb -s <serial> install -r`.

## 2. What this is and why it exists

Rounds 2 and 3 named the fault on both old units: GitHub answers the client hello with
`SSL23_GET_SERVER_HELLO:sslv3 alert handshake failure`. The stack trace showed the request going
out on the **platform** TLS stack (`org.apache.harmony.xnet` on D-HP, `com.android.org.conscrypt`
under `com.android.okhttp` on D-SAM). That stack offers no TLS 1.2 below API 21, and `api.github.com`
accepts nothing lower. The app registers the bundled Conscrypt at startup
(`Conscrypt security provider is active`), but registering a provider does not change which socket
factory `HttpsURLConnection` uses.

The candidate adds `ConscryptInitializer.httpsSocketFactory()`, a factory from the **bundled**
Conscrypt with TLS 1.2 and 1.3 enabled on every socket, and `UpdateChecker.check` sets it on the
connection. It applies below API 21 only; on API 21 and up it returns null and nothing changes.
Certificate checking is the system default, not trust-all.

**The one thing this round can newly fail on** is certificate trust. With TLS 1.2 working, a 2013
root store (D-HP, 4.2.2) may not hold the root GitHub's chain ends at. That shows as a
`CertPathValidatorException` / `Trust anchor for certification path not found`, and it is a
different fix (a bundled root). R1 is the run most likely to show it.

## 3. What is different about this round

- **The press is a hand step, as in rounds 1 to 3.** There is no `AutomationReceiver` verb for the
  update check, and on D-HP the Settings screen cannot be reached by script at all (§7a, D-HP only).
  The operator opens Settings > About and presses "Check for updates" **once** per run. No
  `input tap` from adb.
- **The WiFi has to stay up.** Round 2's D-SAM failure was the Native AA station stand-down taking
  the tablet off its network 6 s before the press. This round writes
  `stand-down-station-mode=2` (NEVER) on every unit, and the phone stays in **airplane mode for the
  whole round**, so no session forms. A run whose window holds a `StationStandDown: asked this unit
  to leave its WiFi network` line is discarded, not graded.
- **No log-level change is needed.** The failure line is `AppLog.e` and prints its stack trace at
  ERROR, so INFO carries it. Leave each unit's `log-level` as found and record the value.
- **Capture everything** (§2). Rounds 1 to 3 used a tag filter; do not, because a trust failure can
  be logged by the framework under its own tag.
- **R3 on D-HU is a regression guard, not a test of the fix.** The factory returns null on API 34,
  so D-HU passes whether or not the change works. Its only job is to show the new code path did not
  break a modern unit.

## 4. Settings keys this round needs

Write with the app stopped (§1). D-SAM: `set_prefs_runas_host.py`. D-HU: `set_hu_prefs.sh`. D-HP:
the `run-as` template in §1. Read back before every launch.

```xml
<int name="stand-down-station-mode" value="2" />   <!-- NEVER; every unit, every run -->
```

Back up `settings.xml` first on each unit and restore it at the end of the round.

## 5. Lines that decide the runs

Each checked with `grep -F` against `b77bf005`.

```
Conscrypt security provider is active                            (App.onCreate, INFO; D-HP and D-SAM only)
UpdateChecker: Update check failed: <exception.message>          (ERROR, followed by the stack trace)
ConscryptInitializer: no bundled HTTPS socket factory: <e>       (WARN; must never appear)
StationStandDown: asked this unit to leave its WiFi network      (INFO; any hit discards the run)
```

**On a FAIL, the first stack frame's package says whether the fix engaged:**

| First TLS frame in the trace | Meaning |
|---|---|
| `org.conscrypt.` (no `com.android.` prefix) | the bundled Conscrypt ran the handshake: the fix engaged, the next cause is below it |
| `com.android.org.conscrypt.` or `org.apache.harmony.xnet.` | the platform stack again: the fix did not engage |

Capture set-up, identical for every run (`<S>` is the run's serial):

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
send() { a=$1; shift; adb -s $S shell am broadcast -f 0x00000020 -n $RX -a com.andrerinas.openheadunit.$a "$@"; }
adb -s $S logcat -G 16M
adb -s $S shell am force-stop $PKG
adb -s $S logcat -c; stdbuf -oL adb -s $S logcat -v threadtime > $RUN.txt &
adb -s $S shell am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity
sleep 20
adb -s $S shell ping -c1 api.github.com          # must answer; if not, fix the WiFi and restart the run
send ACTION_LOG_MARKER --es text $RUN-start
# hand step: Settings > About > Check for updates, once. Wait for the dialog.
adb -s $S shell screencap -p /sdcard/$RUN.png && adb -s $S pull /sdcard/$RUN.png
send ACTION_LOG_MARKER --es text $RUN-end
adb -s $S shell dumpsys wifi | grep -m1 -i "state"
kill %1
```

If `am broadcast` rejects `-f` on 4.2 or 4.4, drop the flag and note it. Never `-p` on D-SAM.

Greps, all on the lines between `$RUN-start` and `$RUN-end` in `$RUN.txt`:

```bash
W() { sed -n "/$RUN-start/,/$RUN-end/p" $RUN.txt; }
W | grep -c  'UpdateChecker: Update check failed:'
W | grep -A25 'UpdateChecker: Update check failed:'          # only if the count is > 0
W | grep -c  'ConscryptInitializer: no bundled HTTPS socket factory'
W | grep -c  'StationStandDown: asked this unit to leave its WiFi network'
grep -c 'Conscrypt security provider is active' $RUN.txt      # whole capture, from the launch
```

## 6. Runs

Run order: **R1, R2, R3.** The phone is in airplane mode throughout. One press per run. A discarded
run may be re-run once; a second discard is reported as it is.

### R1 - D-HP (API 17, `CNU350BGBJ`), the point of the round

- Setup: `stand-down-station-mode=2`, app launched, `ping api.github.com` answered.
- **PASS**, all of:
  - the dialog title is "Up to Date" or "Update Available" (screenshot);
  - `Update check failed:` count is 0;
  - `no bundled HTTPS socket factory` count is 0;
  - `Conscrypt security provider is active` count is at least 1.
- **FAIL**: the failure dialog, or `Update check failed:` count above 0. Report the exception class,
  its message, and the first TLS frame's package from the table in §5.
- **Discard**: `StationStandDown: asked this unit to leave` count above 0, or the pre-press ping
  failed.

### R2 - D-SAM (API 19, `30041c35642d2200`), the point of the round

Same setup, PASS, FAIL and discard conditions as R1. Record the WiFi state line before the launch and
after the press; both must read connected.

### R3 - D-HU (API 34, `27870808938846`), regression guard

- Setup: `stand-down-station-mode=2`, app launched, `ping api.github.com` answered.
- **PASS**: the dialog title is "Up to Date" or "Update Available", and `Update check failed:`
  count is 0. `Conscrypt security provider is active` is **not** expected here and is not graded.
- **FAIL**: the failure dialog. Report the exception as in R1.

## 7. Do not re-run

- The baseline. Rounds 2 and 3 already measured the platform-stack refusal on both old units.
- The Native AA stand-down case from round 2. It is understood and out of scope for this change.

## 8. Report back

For each run, the three things that decide the question:

1. the dialog title the operator saw;
2. the `Update check failed:` count, and on a FAIL the exception class, message and first TLS
   frame's package;
3. the APK md5 pulled from that unit, and the two symbol counts from §1.

Captures go to the existing release `rig-evidence-check-for-updates` as
`check-for-updates-round4-captures.zip` (§7).
