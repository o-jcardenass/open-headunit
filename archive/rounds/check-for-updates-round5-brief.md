# check-for-updates, round 5 brief: GitHub's root bundled for the old units

## 1. Build and baseline

| | Where | SHA | Gate |
|---|---|---|---|
| Candidate | fork, branch `fix/update-check-old-tls` | `2689d1a2` | compiles; 2408 JVM tests, 0 failures |
| Baseline | none built. Round 4 is the baseline (`b77bf005`, APK md5 `582e2114a1c2cfcaaeb0a1acdc2e95d7`) | | |

The candidate is round 4's commit plus one:

```bash
git fetch https://github.com/o-jcardenass/open-headunit.git fix/update-check-old-tls
git checkout 2689d1a2
git log --oneline 8f88d9a6..HEAD      # exactly 2, the top one "Update check: trust GitHub's root CA on old Android"
git status --short                    # empty
```

Build with `build_hur.sh`. Record the md5 from a real `adb pull` on every unit; it must differ from
`582e2114...`. Identity by symbol and resource, because versionName does not move:

```bash
unzip -p cand.apk 'classes*.dex' | strings | grep -cF 'BundledRootTrustManager'   # >= 1 on the candidate, 0 on round 4
unzip -l cand.apk | grep -c 'res/raw/usertrust_'                                  # 2
```

Install with `adb install -r` only, as round 4 did.

## 2. What this is and why it exists

Round 4 proved the first fix: on D-HP and D-SAM the handshake runs on the bundled Conscrypt and
GitHub accepts TLS 1.2. The check then failed one step later on both, with
`CertPathValidatorException: Trust anchor for certification path not found` from the platform
`TrustManagerImpl`.

The reason, measured from the host on 2026-09-29: `api.github.com` serves
`*.github.com <- Sectigo DV E36 <- Sectigo Root E46 <- USERTrust ECC Certification Authority`, and
USERTrust ECC is newer than the root stores of Android 4.2 and 4.4. `openssl s_client` with that one
root as its only trust file verifies the chain (`Verify return code: 0 (ok)`).

The candidate bundles USERTrust ECC and USERTrust RSA (`res/raw/usertrust_*.pem`, fingerprints
pinned by a JVM test). `BundledRootTrustManager` asks the system store first and the two bundled
roots only when the system store refuses. It is used only by the update check, and only below API 21.
Hostname checking is unchanged.

It also retitles the failure dialog "Check for updates". Round 4 showed a failure titled
"Up to Date", which reads as success.

## 3. What is different about this round

Everything in round 4's §3 still applies: one hand press per run, `stand-down-station-mode=2` on
every unit, the phone in airplane mode throughout, capture everything, no log-level change. Two
additions:

- **Grade the dialog's body, never its title.** A success body reads
  "You are already using the latest version (...)" or "A newer version (...) is available...". The
  failure body is "Failed to check for updates. Please check your internet connection." On this build
  the failure title is "Check for updates"; record the title too, since that change is under test.
- **D-HP: dump the ring buffer after every press.** Its USB re-enumerated three times in round 4 and
  killed the host capture. Right after the dialog appears, and again after the end marker:

  ```bash
  adb -s CNU350BGBJ logcat -d -v threadtime > r1-dhp-ringdump.txt
  ```

  Grade R1 on whichever of the host capture and the ring dump holds both markers, or the start marker
  and the press, and say which in Setup notes.

## 4. Settings keys this round needs

```xml
<int name="stand-down-station-mode" value="2" />   <!-- NEVER; every unit, every run -->
```

Back up `settings.xml` on each unit first and restore it at the end of the round.

## 5. Lines that decide the runs

Each checked with `grep -F` against `2689d1a2`.

```
Conscrypt security provider is active                            (App.onCreate, INFO; D-HP and D-SAM only)
UpdateChecker: Update check failed: <exception.message>          (ERROR, followed by the stack trace)
ConscryptInitializer: no bundled HTTPS socket factory: <e>       (WARN; must never appear)
StationStandDown: asked this unit to leave its WiFi network      (INFO; any hit discards the run)
```

**On a FAIL, the exception says which layer refused:**

| Exception in the failure line | Meaning |
|---|---|
| `CertPathValidatorException` / `Trust anchor ... not found` | the bundled roots were not consulted, or GitHub's chain no longer ends at a USERTrust root. Report the whole trace |
| `SSLHandshakeException` without a cert-path cause, or `sslv3 alert` | a regression below round 4. Report the first TLS frame's package |
| `no bundled HTTPS socket factory` line present | the factory failed to build; its exception is on that line |
| `UnknownHostException` / `ConnectException` | the network, not the fix: discard and re-run once |
| anything else | report class, message and the whole trace |

The capture set-up and the greps are round 4's §5, unchanged (`check-for-updates-round4-brief.md`, "Capture set-up"),
with the D-HP ring dump from §3 added.

## 6. Runs

Run order: **R1, R2, R3.** One press per run. A discarded run may be re-run once.

### R1 - D-HP (API 17, `CNU350BGBJ`), the point of the round

- Setup: `stand-down-station-mode=2`, app launched, `ping api.github.com` answered.
- **PASS**, all of:
  - the dialog body is the "latest version" or the "newer version" text (screenshot);
  - `Update check failed:` count is 0;
  - `no bundled HTTPS socket factory` count is 0;
  - `Conscrypt security provider is active` count is at least 1.
- **FAIL**: the failure body, or `Update check failed:` count above 0. Report the exception per §5.
- **Discard**: `StationStandDown: asked this unit to leave` above 0, the pre-press ping failed, or a
  session formed (the phone left airplane mode).

### R2 - D-SAM (API 19, `30041c35642d2200`), the point of the round

Same setup, PASS, FAIL and discard conditions as R1. Record the WiFi state line before the launch
and after the press; both must read connected.

### R3 - D-HU (API 34, `27870808938846`), regression guard

- Setup as R1. Confirm `ping api.github.com` answers before the launch; round 4 lost its first R3 to
  a dropped WiFi.
- **PASS**: a success body and `Update check failed:` count 0. This passes whether or not the fix
  works, because the bundled trust manager is never built above API 20.

### R4 - D-HP, failure dialog title (optional, only if R1 PASSes)

- Setup: as R1, but take the unit off the network before the press (§7a: `svc wifi` does not work on
  D-HP; pull the access point's cable or switch the router off only if that is harmless to the rest
  of the rig, otherwise mark R4 UNTESTABLE).
- **PASS**: title "Check for updates", failure body, and an `Update check failed:` line naming an
  `UnknownHostException` or `ConnectException`.

## 7. Do not re-run

- Round 4's handshake result. The bundled Conscrypt engaging is settled.
- The Native AA stand-down case from round 2.

## 8. Report back

For each run:

1. the dialog title and body;
2. the `Update check failed:` count, and on a FAIL the exception class, message and whole trace;
3. the APK md5 pulled from that unit and the two identity counts from §1.

Captures go to the existing release `rig-evidence-check-for-updates` as
`check-for-updates-round5-captures.zip`.
