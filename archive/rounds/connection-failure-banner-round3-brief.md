# connection-failure-banner, round 3 brief: the stamp R2b found, and two screens beside it

**Candidate:** `fix/session-lifecycle-and-diagnostics` @ `3ad29942` on `fork`
(`o-jcardenass/open-headunit`). Still the head of draft PR #867, three commits further on than the
tip round 2 measured. **No baseline.** One APK for the whole round.

```bash
git fetch fork
git checkout -B fix/session-lifecycle-and-diagnostics fork/fix/session-lifecycle-and-diagnostics
git rev-parse HEAD          # must print 3ad29942...
git log --oneline -4
# 3ad29942 Wireless settings: drop the experimental tag, trim the hotspot note, and show Static BSSID only where it is read
# 71fa808a WiFi Direct: let the user pick the band, and give Android 9 somewhere to fall back to
# e91a1527 Native AA: keep a connection-failure record until something disproves it
# bd3d7b99 Head unit hotspot: let the user pick the band, and show each lever where it is read
```

`bd3d7b99` is round 2's candidate, unchanged and still at the bottom. Nothing round 2 measured is
invalidated; R1, R2c, R3, R4 and R5 of that round are settled and are not re-run here.

---

## 0. Before anything else: the rig fix, again

Round 2 confirmed `chown -R u0_a168:u0_a168 shared_prefs` fixes the root-owned directory and that it
survives `adb install -r`. It does **not** survive an uninstall, and this round reads `settings.xml`
back in five runs, so check it before the first launch rather than assuming round 2's fix is still
in place:

```bash
adb shell stat -c '%U:%G %a' /data/data/com.andrerinas.headunitrevived/shared_prefs
adb shell run-as com.andrerinas.headunitrevived id     # use the uid this prints, not round 2's
```

If it is root-owned again, redo the `chown` and say so. If there is no root shell this time, every
"the stamp is X afterwards" check becomes INCONCLUSIVE and the log lines are the whole result.

**Round 2's own process finding applies to this round unchanged.** `assembleGithubDebug` builds the
APK into `apks/` and does not install it. Round 2 lost a pass through R1/R2a/R2b to a stale
installed build before `md5sum` caught it. Install explicitly, then verify the md5 of what is on the
device before the first run, not after a surprising result.

---

## 1. Why this round exists

Round 2 was PASS everywhere except **R2b, which was MIXED**, and its finding held up against the
source. Three commits answer it and two other asks.

**The defect R2b found.** `SoftApCredentialsProvider.publish()` reached its `SUCCESS` line and
cleared both the notification and the persisted stamp unconditionally, four lines after warning that
an empty passphrase is an open network the phone will refuse. So R2a's state (name set, password
blank) deleted the record during the very run that showed the banner, and it could never come back:
`decide()` returns `CONFIG_UNREADABLE` only when the resolved SSID is empty, so a name that is set is
a name that resolves. A user who does half the remedy loses the instruction permanently.

**Keeping the record was not enough on its own**, which is the part round 2 could not have seen.
Dismissal is per occurrence, so a record whose stamp never advances is hidden for good after one
dismissal. The publish site now **raises** rather than merely keeps when the credentials going out
are not joinable. R3 below is that path, and nothing has ever tested it.

**A workaround is not a disproof.** With both fields set the clear also fired, but the device still
will not name its own access point. The record is now retired only by the device answering for
itself; the banner is hidden by `remedyApplied()`, which is what R2b's screen half already measured
working. The same shape was latent on `BSSID_UNAVAILABLE` behind a static override and is fixed with
it, though nothing has measured that half: R4 and R5 are its first run.

**Two things beside it.** WiFi Direct gets the Auto / 5 GHz only / 2.4 GHz only selector the hotspot
route already had, and three rows on the wireless screen are corrected. R7 and R8.

---

## 2. What is different about this round

- **No baseline, no A/B.** Every run is a state change on one APK. Each suppression run flips the
  setting back in the same session, and **a suppression run without its control is not a pass**.
- **`debug-force-p2p-band-24` no longer exists.** It and `p2p-legacy-5ghz` are replaced by
  `wifi-direct-band` (0 auto, 1 five, 2 two-four). Existing values migrate on first read, so a rig
  that had the old key set will come up on `wifi-direct-band=2` without being asked. **Delete both
  old keys before R8** or the migration will decide the band for you.
- **The pre-Q operating-channel ladder cannot be exercised here.** This unit is Android 14 and takes
  the real band request, so the 5 GHz then 2.4 GHz then unrestricted ladder is unreachable on it.
  Do not try to provoke it; it is covered by `P2pOperatingChannelPolicyTest`. R8 measures the API 29+
  half only, and that limit is the honest result rather than a gap in the round.
- **The hotspot disproof arm is UNTESTABLE on this rig, permanently.** Rounds 1 and 2 both measured
  that this device refuses `getSoftApConfiguration()`, so it can never name its own access point and
  can never produce the one state that legitimately clears the hotspot record. Say so; do not invent
  a run for it. Its coverage is `SoftApCredentialsPolicyTest`.
- **Nothing re-resolves when the hotspot override settings change.** `hotspot-ssid` and
  `hotspot-password` are not in `requiresRestart` and `AapService`'s preference listener ignores
  them, so a banner will not clear because the settings were saved. Every step in R1, R2 and R3 is
  force-stop, write, relaunch. Never edit and wait.
- The phone is needed for R5 and R8 only. Everything else is head-unit-side.

---

## 3. Settings keys this round needs

| Key | Type | Element | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `<int name="wifi-connection-mode" value="3" />` | Native AA. R7 walks other values. |
| `native-ap-transport` | int | `<int name="native-ap-transport" value="1" />` | `1` = hotspot, `0` = WiFi Direct. Set it explicitly every time. |
| `helper-connection-strategy` | int | `<int name="helper-connection-strategy" value="1" />` | R7 only, so mode 2 is a real WiFi Direct helper configuration. |
| `hotspot-ssid` | string | `<string name="hotspot-ssid">OHU-TEST</string>` | Deleted for R3's first launch. Set in R1 and R2. |
| `hotspot-password` | string | `<string name="hotspot-password">testtest1234</string>` | R1 leaves it deleted. R2 sets it. |
| `static-bssid` | string | `<string name="static-bssid">AA:BB:CC:DD:EE:FF</string>` | R4 only. Delete it for R5. |
| `wifi-direct-band` | int | `<int name="wifi-direct-band" value="0" />` | R8. **New.** Delete `debug-force-p2p-band-24` and `p2p-legacy-5ghz` alongside it. |
| `log-level` | int | `<int name="log-level" value="1" />` | DEBUG, for the same reason as round 2: none of these files has a `LOG_VERBOSE` guard, and VERBOSE brings this unit's driver flood closer to wrapping the ring buffer. |
| `connection-issue-hotspot-config` | long | `<long name="connection-issue-hotspot-config" value="1755800000000" />` | Seeded record. |
| `connection-issue-bssid` | long | `<long name="connection-issue-bssid" value="1755800000000" />` | Seeded record. |
| `connection-issue-dismissed-at` | long | `<long name="connection-issue-dismissed-at" value="0" />` | R3 sets this. Delete the key to reset. |

---

## 4. The lines that decide every run

All copied from `3ad29942`. Use `grep -a`, always. The three record lines are new this round and are
mutually exclusive: exactly one of them prints per successful publish.

| Grep string | Level | Means |
|---|---|---|
| `SoftApCredentials: SUCCESS - Providing credentials from` | i | unchanged to the byte from round 2, and no new line contains it, so its count is comparable across both rounds. |
| `named by this device rather than by the manual override` | i | the hotspot record was **retired**. Unreachable on this rig, see §2. |
| `these credentials carry no passphrase` | w | the hotspot record was **raised**. R1 and R3 turn on this line. |
| `these credentials come from the manual override` | i | the hotspot record was **kept**. R2's line. |
| `this unit read its own WiFi address` | i | the BSSID record was retired. R5's line. |
| `the BSSID being sent is the static override` | i | the BSSID record was kept. R4's line. |
| `this route cannot raise the missing-BSSID condition` | i | the hotspot transport reached the BSSID clear site and declined it. |
| `SoftApCredentials: The access point on` | e | the hotspot condition raising by itself, as in round 2's R1. |
| `MainActivity: showing the connection issue banner for` | i | the banner went up, and the suffix names the condition. |
| `MainActivity: could not read the connection issue record` | w | the read threw. Should never appear. |
| `ConnectionIssues: settings unavailable, not recording` | d | preferences unreachable. Should never appear. |
| `WifiDirectManager: Band preference is` | i | **new**, prints on every WiFi Direct bring-up. R8's line. |
| `Requesting Native AA P2P group on` | i | the band actually asked for. |
| `Handshake: SSL handshake complete` | d | a session went live. |

---

## 5. Runs

### R0 - build and unit gate

```bash
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
```

**PASS:** clean build, suite green, with these counts:

| Suite | Test methods |
|---|---|
| `ConnectionIssueBannerPolicyTest` | 27 (24 at `bd3d7b99`) |
| `SoftApCredentialsPolicyTest` | 15 (8) |
| `SoftApBssidPolicyTest` | 13 (8) |
| `NativeGroupBandPolicyTest` | 14 (8) |
| `P2pOperatingChannelPolicyTest` | 14 (11) |
| `ConnectionIssuesTest` | 8 (unchanged) |

Total: expect **698**, against round 2's 676. Report the number either way.

### R1 - the name alone is still not the remedy, and now it says so twice

Round 2's R2a re-run, with the two halves it did not check. Force-stop, `wifi-connection-mode=3`,
`native-ap-transport=1`, `hotspot-ssid=OHU-TEST`, **`hotspot-password` deleted**, all four
`connection-issue-*` keys deleted. Bring the access point up and confirm it immediately before
launching, per §7a. Launch, leave it 60 s, force-stop, read `settings.xml`.

**PASS, all four:**

- `these credentials carry no passphrase` at least 1;
- `connection-issue-hotspot-config` is **non-zero** afterwards. Round 2 saw `0` here on the same
  state, which is the defect;
- relaunching shows the banner, exactly one
  `showing the connection issue banner for HOTSPOT_CONFIG_UNREADABLE`;
- `named by this device` and `come from the manual override` are both **0**.

Note the stamp value. R3 needs it to have moved.

### R2 - the pair is the remedy, and the record survives it

Round 2's R2b, the run that has to turn. Force-stop, also set `hotspot-password=testtest1234`, seed
`connection-issue-hotspot-config=1755800000000`, relaunch.

**PASS, all three:**

- no banner, and no new `showing the connection issue banner` line;
- `connection-issue-hotspot-config` is **still 1755800000000**. Round 2 read `0`. This one number is
  the round;
- `these credentials come from the manual override` at least 1, and `carry no passphrase` is 0.

### R3 - a dismissal does not outlive the next attempt. **Never tested.**

The half of the defect round 2 could not see: keeping the record is useless if its stamp never moves,
because dismissal is judged per occurrence.

1. Force-stop. `hotspot-ssid` and `hotspot-password` **both deleted**, all four `connection-issue-*`
   keys deleted. Launch, let the condition raise by itself, confirm the banner.
2. **Tap the banner's Dismiss button** (not the body). Confirm it goes. Force-stop and read
   `connection-issue-dismissed-at`: it must now be non-zero and later than
   `connection-issue-hotspot-config`.
3. Force-stop, set `hotspot-ssid=OHU-TEST` only, leave the password deleted, relaunch and leave it
   60 s. Force-stop, relaunch.

**PASS, both:**

- `connection-issue-hotspot-config` after step 3 is **greater** than
  `connection-issue-dismissed-at` from step 2, which is the re-raise;
- **the banner is back** on the final relaunch, with a second
  `showing the connection issue banner for HOTSPOT_CONFIG_UNREADABLE`.

Before this change step 3 wiped the stamp to 0 and the banner never returned. A banner that does not
come back here is the defect, not a pass.

### R4 - a static BSSID keeps the record it works around

The BSSID half, on hardware for the first time. Force-stop, `native-ap-transport=0` (WiFi Direct),
`static-bssid=AA:BB:CC:DD:EE:FF`, `connection-issue-bssid=1755800000000`, hotspot overrides deleted.
Run a real Native AA attempt with the phone and let it reach Type 3. The phone will refuse those
credentials, which is expected and is not the measurement.

**PASS, all three:**

- `the BSSID being sent is the static override` at least 1;
- `connection-issue-bssid` is **still 1755800000000** afterwards;
- `this unit read its own WiFi address` is 0.

If the handshake never reaches Type 3, this is **INCONCLUSIVE**, not a fail. Say which happened.

### R5 - and a real address still retires it. R4's control.

Force-stop, **delete `static-bssid`**, seed `connection-issue-bssid=1755800000000`,
`native-ap-transport=0`. One ordinary Native AA session, as round 2's R5 ran.

**PASS, all three:**

- `Handshake: SSL handshake complete`;
- `this unit read its own WiFi address` at least 1;
- `connection-issue-bssid` is **0** afterwards.

This is the regression guard for the whole change. A rule that stopped the clears running at all
would pass R1 through R4 and only show up here.

### R6 - round-wide invariant: nothing raises by itself

Over every capture except R1's and R3's, where the hotspot condition raising is the result:

```bash
grep -ac "the phone connected over Bluetooth and answered nothing we sent" *.txt
grep -ac "MainActivity: could not read the connection issue record" *.txt
grep -ac "ConnectionIssues: settings unavailable, not recording" *.txt
```

**PASS:** all zero. Unchanged from round 2 except that `BSSID is still masked/empty` is no longer in
the list, because R4 and R5 are about that condition.

### R7 - the wireless screen says what it should, where it should

Screen-reading only, no connection. One screenshot per part. Stay in **Basic** unless a part says
otherwise, and do not scroll with adb.

**R7a, the hotspot note.** `wifi-connection-mode=3`, `native-ap-transport=1`. **PASS:** the note
under the transport buttons reads exactly

> The phone joins this device's own hotspot instead of a WiFi Direct group. Switch it on before
> connecting.

and the word "Experimental" does not appear on it. Note whether it fits without pushing the rows
below off screen, which is the thing the trim was for.

**R7b, the Auto-Enable Hotspot warning still warns.** Same screen, toggle Auto-Enable Hotspot on.
**PASS:** the dialog still titled "Experimental Feature" appears. That one is deliberate and is a
different feature; a round that reports it as a leftover has read this wrong.

**R7c, the WiFi Direct band selector is reachable without Advanced.** `native-ap-transport=0`.
**PASS:** a three-way "WiFi Direct band" control reading Auto / 5 GHz only / 2.4 GHz only is on
screen **in Basic**, with its explanatory note under it, and the two old toggles ("Force WiFi Direct
onto 2.4 GHz", "Ask for 5 GHz on Android 9 and older") are gone. Pick 2.4 GHz only and confirm "Use
the upper 5 GHz range" disappears; pick Auto and confirm it returns.

**R7d, Static BSSID only where it is read.** Advanced tab for this part.

| Setting | Static BSSID row |
|---|---|
| `wifi-connection-mode=1` | **absent** |
| `wifi-connection-mode=2`, `helper-connection-strategy=2` | **absent** |
| `wifi-connection-mode=2`, `helper-connection-strategy=1` | present |
| `wifi-connection-mode=3`, either transport | present |

**PASS:** all four, and where present the row is labelled **Static BSSID (MAC Address)**.

**R7e, the banner's remedy still lands.** `wifi-connection-mode=3`, `native-ap-transport=0`, seed
`connection-issue-bssid` only, `static-bssid` deleted. Launch, wait 4 s, tap the banner **body**.
**PASS:** Settings opens with the search box pre-filled and the Static BSSID (MAC Address) row on
screen. This is the coupling the new visibility gate could have broken.

### R8 - the band selector reaches the group

`wifi-connection-mode=3`, `native-ap-transport=0`, **`debug-force-p2p-band-24` and `p2p-legacy-5ghz`
deleted**, one real session per arm.

**R8a, Auto.** `wifi-direct-band=0`. **PASS:** `Band preference is automatic`, `Requesting Native AA
P2P group on 5GHz band`, and a session. This must behave exactly as round 2's R5 did.

**R8b, 2.4 GHz only.** `wifi-direct-band=2`. **PASS:** `Band preference is 2.4 GHz only, set by the
user`, `Requesting Native AA P2P group on 2.4GHz band. Chosen by the user.`, and **zero**
`band-mismatch` or `Retrying 5GHz` lines, which is the coupling that would tear down every group it
made. Report the group's frequency from `onGroupInfoAvailable`; under 3000 MHz is the answer.

Whether the phone then holds a session on 2.4 GHz is a separate question and not a PASS condition.
Report it as an observation.

**R8c, the migration.** Force-stop, delete `wifi-direct-band`, write
`<boolean name="debug-force-p2p-band-24" value="true" />`, launch, force-stop, read `settings.xml`.
**PASS:** `wifi-direct-band` now reads `2`, written by the app itself. Nobody who had set the old
flag should silently lose it.

---

## 6. Do not re-run

Settled and untouched by these three commits:

- **Round 2's R1** (the hotspot condition raised by the hardware), **R2c** (the BSSID banner wording
  and its control), **R3** (the remedy tap reaching both hotspot rows), **R4a/R4b/R4c** (the
  route-relevance filter) and **R5** (a real session unbroken).
- **The hotspot disproof arm.** Permanently untestable here, see §2.
- **The pre-Q channel ladder.** Unreachable on Android 14, see §2.
- **Portrait layout**, still UNTESTABLE on this panel.
- **The dismissal rules in general.** R3 tests one specific sequence, not the rules round 2 passed.

---

## 7. Report back

Five numbers decide whether this ships:

1. **§0**: whether `shared_prefs` was still owned by the app, and the md5 of the APK actually
   installed before R1.
2. **R0**: the total against 698, and the six suite counts.
3. **R2**: whether `connection-issue-hotspot-config` survived. Round 2 read `0` here. That one number
   is the whole reason for the round.
4. **R3**: whether the banner came back after the dismissal. Nothing has ever measured this path.
5. **R5**: whether `connection-issue-bssid` went to `0` after a real session. If it did not, the new
   rule is too strict and R4's pass means nothing.
