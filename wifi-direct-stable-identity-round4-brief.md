# wifi-direct-stable-identity, round 4: the WiFi button after an exit, and the three defects behind it

**Candidate:** `fork/feat/wifi-direct-stable-identity` @ `0a9a98d9` (8 commits on `ef74866c`), `3.3.0` (versionCode 103)
**Baseline:** `937e68b7`, the same branch with the kept group and none of the fixes. Build it; there is no APK on record, and `937e68b7` has never run on hardware either.

```bash
git fetch fork
git rev-parse fork/feat/wifi-direct-stable-identity   # 0a9a98d9...
git log --oneline 937e68b7..0a9a98d9                  # ac060b36 6a49295c 3c25972e ed03cf7d 0a9a98d9
```

| SHA | What |
|---|---|
| `a0940339` `8dc5f92e` `652ba40f` | rounds 1 and 2, verified |
| `937e68b7` | the kept group across a lost session (**the baseline; untested on hardware**) |
| `ac060b36` | round 3's candidate: claim the create window before the first async hop |
| `6a49295c` | the watchdog fallback keeps the group's identity; the unit's own device name before the P2P name arrives |
| `3c25972e` | one teardown helper that invalidates credentials, the create window claimed at the launcher, a re-check behind WAIT |
| `ed03cf7d` | **Type 3 carries the credentials that are live when it is written** |
| `0a9a98d9` | an arrival inside a create is left to answer; the wake poke waits while Settings is open |

Read `TESTING-TEMPLATE.md` first, and §7a before planning. Rebuild both arms yourself and record both md5s.

> **Round 3 was never run, and its brief is superseded by this one.** Do not use
> `wifi-direct-stable-identity-round3-brief.md`: its candidate `94450a6d` and baseline `250b0123` are
> gone from the branch, and two of its instructions are wrong. The `refresh:` lines print at **INFO**,
> not VERBOSE, and `94450a6d` is not independent of the kept group, so an A/B that isolates it alone
> answers nothing. Round 3's repro logs are still the reference for the fault:
> `evidence/wifi-direct-stable-identity-round3/`.

---

## 1. Why this round exists

Reproduced 2026-09-01 on D-HU (MT50) + D-POCO (POCO X3 NFC, GH 17.5.663204), captures in
`evidence/wifi-direct-stable-identity-round3/`. Form a Native AA session, exit it, tap the in-app
WiFi poke button once. The phone wakes, does the Bluetooth handshake, then sits on "Obtaining IP
address" and no session forms. Two or three P2P groups appear in a row. Letting the phone reconnect
on its own works fine.

Reading both logs found **two independent defects**, and the second is the one the phone actually
saw. Fixing either alone leaves a broken case, which is why this round replaces round 3.

**The double chain.** The button re-inits the mode, and `startNativeAaQuietHost()` is 570 to 617 ms
from the real `createGroup` behind an async `removeGroup` and a 500 ms post. The button then calls
the manual poke, whose pre-flight sees no credentials and asks for a refresh; the refresh sees no
group and no create in flight and starts a second chain. The two fight over BUSY.

**The stale Type 3.** The handshake read the credentials once, near the start of the exchange, and
wrote them a second later. In run 1 the live group's credentials arrived 51 ms before the write; in
run 2, 143 ms. Both times the replaced group's name went out anyway. The phone stored it, looped
`releaseRequestAsUnfulfillableByAnyFactory` and `NETWORK_NOT_FOUND` for about 36 s, and aborted with
no Bluetooth fallback. **This defect does not need two chains** - one BUSY rung on one chain
produces it, and three rungs on this branch removed a group without telling the handshake at all.

Two smaller ones, from code and not yet seen on hardware: a Bluetooth arrival landing inside a
create tore that create down and started another, and with the station stand-down enabled the whole
1.5 s wait was unclaimed, so the second chain formed *and* skipped the stand-down.

---

## 2. What is different about this round

- **The trigger must be scripted.** The button is `HomeFragment`'s Native-AA device selector. The
  scriptable equivalent, from `hfp_poke_probe.sh`'s own header:
  ```bash
  adb -s $HU shell am start-foreground-service \
    -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.aap.AapService \
    -a com.andrerinas.openheadunit.ACTION_NATIVE_AA_POKE --es extra_mac <D-POCO BT MAC>
  ```
  `AapService` is `exported=false`; D-HU has a root adb shell, so it should be accepted there.
  **Verify it in setup** (`am` prints `Error: ...` on rejection). If rejected, fall back to
  `uiautomator dump` plus one `input tap` on the poke row, and say so in Setup notes. Do not scroll.
- **One poke, not a loop.** The bug is the *first* poke after a stop, when the mode has to be
  re-inited. A second poke while the mode is already active takes a different branch and does not
  reproduce it. Each run: session, `headunit://exit`, wait for `WifiDirectManager: Stopping and
  cleaning up...`, exactly one poke, observe 90 s.
- **INFO is enough for R1 and R2.** Every line those runs score is `AppLog.i`. R2's positive
  evidence and R3 and R5 want DEBUG, which is `log-level=1`: the enum is VERBOSE 0, DEBUG 1,
  INFO 2, so `log-level=2` hides every DEBUG line. Round 1 ran everything at 1.
- **This rig makes it worse, not milder.** D-HU re-addresses every group, so each create draws an
  address the phone has never seen and there is nothing cached to fall back to.
- The 5 GHz band dialogs during the fault are one per `createGroup` attempt and are not scored.

---

## 3. Preparation

Settings as round 2's table: `wifi-connection-mode=3`, `wifi-5ghz-channel=36`, `static-bssid=0`,
`native-wifi-version-exchange=false`, `stand-down-station-for-wifi-direct` **absent** (R6 is the one
run that sets it), `wifi-direct-band` as round 2. `log-level=1` (DEBUG) throughout: it carries every
INFO line the runs score as well as the DEBUG ones.

D-POCO paired to D-HU, its head-unit record clean (no stored `port=5299` endpoint from a prior
WPP-TCP round). Phone Bluetooth on for every run.

**Current rig state to undo first:** the devices were last left with a `74d0319c` build, which is
not on any branch any more. Reinstall from this round's builds.

### Build gate (R0)

Two builds, both md5s recorded, and they must differ.

```bash
# candidate only, all three must be > 0
unzip -p <candidate.apk> 'classes*.dex' | strings | grep -cF 'CredentialFreshnessPolicy'
unzip -p <candidate.apk> 'classes*.dex' | strings | grep -cF 'loopStep'
unzip -p <candidate.apk> 'classes*.dex' | strings | grep -cF 'networkComingUp'
# baseline: all three must be 0
```

Unit tests: candidate **1193 / 0**, baseline **1184 / 0**. Named classes on the candidate:
`CredentialFreshnessPolicyTest` 4 (new), `NativeRefreshPolicyTest` 6, `NativeHandoffPolicyTest` 28,
`BtAutoStartRearmPolicyTest` 13, `P2pGroupIdentityPolicyTest` 15. A failure stops the round.

---

## 4. The lines that decide every run

Verified with `grep -F` against `0a9a98d9`.

**New on the candidate, INFO:**

```
WifiDirectManager: a Native AA group create is claimed (<why>); a refresh in the next 15s waits for it.
WifiDirectManager: the group is being removed (<why>), so its credentials are no longer handed out.
WifiDirectManager: refresh: asking again in <n>ms, once that create's grace is up.
AapService: Bluetooth auto-start: the Native AA group is still being created, so it is left to answer.
AapService: manual Native-AA poke ignored: the wireless mode is not Native AA.
NativeAA: the settings screen is open, so the wake poke waits until it closes.
NativeAA: the group changed while Type 3 was pending (was <ssid>/<bssid>, now <ssid>/<bssid>); sending the live credentials instead.
NativeAA: the network these credentials name was taken down while Type 3 was pending, so nothing is sent; the phone retries once a group is up.
```

**New on the candidate, DEBUG (needs `log-level=1`):**

```
WifiDirectManager: the claimed create window is released (<why>).
NativeAA: the live credentials still match the ones this handshake captured.
```

**Already there, on both arms, INFO:**

```
WifiDirectManager: startNativeAaQuietHost() requested.        one per bring-up; two is the bug
WifiDirectManager: refresh: no group is up, so one is created.     the RECREATE that starts chain 2
WifiDirectManager: refresh: a group was asked for <n>ms ago and has not answered yet, ...   the WAIT
WifiDirectManager: refresh: the group <name> is up, so its credentials are read again ...   REDELIVER
WifiDirectManager: <band> createGroup SUCCESS!
WifiDirectManager: <band> createGroup failed (BUSY ...), removing group and retrying in 2s (retry N/4)...
WifiDirectManager: onGroupInfoAvailable: SSID: DIRECT-.., BSSID: .., IFACE: p2p-wlan0-N
NativeAA: [TX] Wrote TYPE 3 (size ..) to Bluetooth
WirelessServer: Incoming connection detected from /192.168.49.x      the line that matters
Handshake: SSL handshake complete
```

**The one comparison that decides R2**, and it is worth scripting: the SSID inside the last
`SUCCESS - Providing credentials` before `[TX] Wrote TYPE 3` must be the SSID the phone reports
receiving in `GH.WIRELESS.SETUP: Info response received`. Round 3's captures show them differing.

---

## 5. Runs

### R1: one poke, one group (both arms, three times each)

`log-level=1`. Per arm, three times:

1. Bring up a full Native AA session, phone connecting on its own. Wait for `SSL handshake complete`
   and video.
2. `adb shell am start -a android.intent.action.VIEW -d 'headunit://exit' <pkg>`. Wait for
   `WifiDirectManager: Stopping and cleaning up...`.
3. Fire **one** poke, per §2.
4. Capture 90 s.

**Baseline `937e68b7`, expect the fault on at least 2 of 3:**
- `refresh: no group is up, so one is created.` present after the poke, and a second
  `startNativeAaQuietHost() requested` within ~200 ms of the first.
- `createGroup failed (BUSY` at least once; two or more `onGroupInfoAvailable` with **different**
  SSID and BSSID inside the one poke; `p2p-wlan0-N` jumping by 2 or more.
- No `WirelessServer: Incoming connection detected`, no session inside the 90 s.

**Candidate `0a9a98d9`, expect all three clean:**
- Exactly **one** `startNativeAaQuietHost() requested` per poke, and exactly one
  `createGroup SUCCESS!`; `p2p-wlan0-N` up by at most 1.
- The poke's pre-flight refresh answered by `refresh: a group was asked for ...` (WAIT) or
  `refresh: the group ... is up` (REDELIVER), and **never** `refresh: no group is up`.
- `a Native AA group create is claimed (bringing the Native AA group up)` present.
- `Incoming connection detected from /192.168.49.x`, then `SSL handshake complete`, then video.

**PASS** when the baseline faults on at least 2 of 3 and the candidate forms a session on all 3 with
no second create chain. Report per arm: sessions formed / 3, `startNativeAaQuietHost() requested`
per poke, `createGroup SUCCESS` per poke, `createGroup failed (BUSY` per poke, and poke to
`Incoming connection detected` seconds for each candidate success.

If the baseline does not fault on any of the three, R1 is **INCONCLUSIVE** for the A/B. Still report
the candidate side, and say the baseline did not reproduce.

### R2: Type 3 names the group that exists (both arms, from R1's captures)

No new runs. For every poke in R1, on both arms, extract:

- the SSID and BSSID of the last `SUCCESS - Providing credentials` before `[TX] Wrote TYPE 3`;
- the SSID and BSSID in the phone's `GH.WIRELESS.SETUP: Info response received`;
- whether the phone then reached `CONNECTED_WIFI` or looped `NETWORK_NOT_FOUND`.

**PASS** when they match on every candidate poke. On the baseline they are expected to differ
wherever R1 faulted; that difference is the defect and is worth quoting verbatim in the results.

On the candidate the group should not churn at all, so `the live credentials still match the ones
this handshake captured` (DEBUG, R3's capture) is the positive evidence that the re-read runs. The
two warn/error lines firing on a clean run would be a **finding**, not a pass.

### R3: the wake poke waits while Settings is open (candidate only)

`log-level=1`. With Native AA armed and the group up, and **D-POCO's Bluetooth off** so no session
can form:

1. `adb shell am start -n com.andrerinas.headunitrevived/com.andrerinas.openheadunit.main.SettingsActivity`
2. Wait 60 s.
3. `adb shell input keyevent KEYCODE_BACK`, and wait 60 s more.

**PASS** when `NativeAA: the settings screen is open, so the wake poke waits until it closes.` appears
exactly once in step 2, `NativeAA: Attempting active poke to device` appears **zero** times in step 2,
and at least one appears within 20 s of step 3. A `Stopping poke retry loop` line during step 2 is a
**FAIL**: the loop must defer, not end.

### R4: the automatic reconnect still works (candidate only, regression guard)

`log-level=1`. Form a session, `headunit://exit`, then do not poke: let the phone come back on its
own, using round 2's reconnect trigger (`wds_reconnect.sh`, the head unit `svc bluetooth` cycle).
Once, 90 s.

**PASS** when a session forms, `createGroup SUCCESS` is 1 for the reconnect, and any `refresh:` line
is REDELIVER or WAIT and never `no group is up`.

### R5: five-minute clean control (candidate only)

`log-level=1`. One Native AA session, left untouched five minutes, then exit.

**PASS** when `createGroup SUCCESS` stays at 1, one `p2p-wlan0-N` throughout, throughput windows all
`dropped=0`, zero `refresh: no group is up` once the session is established, and zero
`the group is being removed` while the session is up.

### R6: the stand-down window is claimed too (candidate only)

`log-level=1`, `stand-down-station-for-wifi-direct=true`, D-HU joined to its own WiFi network.
Repeat R1's candidate procedure **once**.

**PASS** when `a Native AA group create is claimed (waiting for this unit to leave its own network)`
appears, exactly one `startNativeAaQuietHost() requested` follows it, and the session forms. This is
a code-level defect that has never been seen on hardware, so a clean run here is confirmation rather
than a fix being proven. Restore the setting to absent afterwards.

---

## 6. Stop conditions and restore

- R0 fails on either arm: stop.
- The poke cannot be triggered on D-HU by either method: stop, report **UNTESTABLE**, and quote what
  `am` printed.
- R1's baseline does not fault: R1 is INCONCLUSIVE; run R2 through R6 anyway.

Leave the candidate installed on D-HU, `settings.xml` restored to the round's baseline and verified
byte-identical, radios on, D-POCO's head-unit record clean.

---

## 7. Report back

Four numbers decide it: baseline sessions formed / 3, candidate sessions formed / 3, whether any
candidate poke produced a second `startNativeAaQuietHost() requested`, and whether Type 3's SSID
matched the live group on every candidate poke. R3's three counts settle the settings pause.

If R1 and R2 both pass, this branch is ready for its PR and
`bt-auto-start-disconnect-round1-brief.md` becomes runnable on top of it.
